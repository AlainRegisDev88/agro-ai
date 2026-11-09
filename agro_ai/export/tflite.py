"""Convert a trained expert to FP16 and full-INT8 TFLite, evaluate both, update the manifest.

    python -m agro_ai.export.tflite --run models/cassava/v1 --data .../processed/cassava

INT8 (uint8 input, float32 probabilities out) is the default download for
budget Android phones. If it loses more than --max-int8-drop accuracy points
against the Keras model on the test split, or cannot run at all, FP16 becomes
the recommended file and a warning is printed; both files are always written.
"""

from __future__ import annotations

import argparse
import json
import random
import shutil
import sys
import tempfile
from pathlib import Path

import numpy as np

from ..train.dataset import Item, load_uint8, read_manifests, split
from ..train.metrics import classification_report
from .manifest import INPUT_SPEC, display_name, file_entry, update_manifest

REPRESENTATIVE_SAMPLES = 300


def _saved_model(model, out: Path) -> Path:
    if hasattr(model, "export"):   # Keras 3
        try:
            model.export(str(out), verbose=False)  # otherwise prints hundreds of captured-variable lines
        except TypeError:                          # Keras versions without the verbose argument
            model.export(str(out))
    else:
        import tensorflow as tf
        tf.saved_model.save(model, str(out))
    return out


def convert(model, rep_items: list[Item], out_dir: Path, name: str) -> dict[str, Path]:
    import tensorflow as tf

    tmp = Path(tempfile.mkdtemp(prefix="agro_sm_"))
    try:
        sm = _saved_model(model, tmp / "saved_model")

        fp16 = tf.lite.TFLiteConverter.from_saved_model(str(sm))
        fp16.optimizations = [tf.lite.Optimize.DEFAULT]
        fp16.target_spec.supported_types = [tf.float16]
        fp16_path = out_dir / f"{name}_fp16.tflite"
        fp16_path.write_bytes(fp16.convert())

        def representative():
            for item in rep_items:
                yield [load_uint8(item.path).astype(np.float32)[None]]

        int8 = tf.lite.TFLiteConverter.from_saved_model(str(sm))
        int8.optimizations = [tf.lite.Optimize.DEFAULT]
        int8.representative_dataset = representative
        int8.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        int8.inference_input_type = tf.uint8
        int8_path = out_dir / f"{name}_int8.tflite"
        int8_path.write_bytes(int8.convert())
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return {"fp16": fp16_path, "int8": int8_path}


def _input_dtype(path: Path) -> str:
    import tensorflow as tf
    interp = tf.lite.Interpreter(model_path=str(path))
    return np.dtype(interp.get_input_details()[0]["dtype"]).name


def open_interpreter(path: Path, threads: int = 4):
    """Interpreter with the default XNNPACK CPU delegate, or without it if XNNPACK
    rejects the model (seen with full-INT8 MobileNetV3). Returns (interpreter, xnnpack_ok).
    The Android app needs the same fallback, so xnnpack_ok is recorded in the manifest."""
    import tensorflow as tf

    try:
        interp = tf.lite.Interpreter(model_path=str(path), num_threads=threads)
        interp.allocate_tensors()
        return interp, True
    except RuntimeError as exc:
        print(f"         XNNPACK rejected {path.name} ({str(exc).splitlines()[0][:120]}); "
              f"retrying with the plain TFLite kernels", flush=True)
    interp = tf.lite.Interpreter(
        model_path=str(path), num_threads=threads,
        experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_WITHOUT_DEFAULT_DELEGATES)
    interp.allocate_tensors()
    return interp, False


def evaluate_tflite(path: Path, items: list[Item], labels: list[str], threads: int = 4) -> dict:
    interp, xnnpack_ok = open_interpreter(path, threads)
    inp = interp.get_input_details()[0]
    out = interp.get_output_details()[0]
    scale, zero = inp["quantization"]
    index = {l: i for i, l in enumerate(labels)}
    y_true, y_pred = [], []
    for item in items:
        x = load_uint8(item.path).astype(np.float32)[None]
        if inp["dtype"] != np.float32:
            info = np.iinfo(inp["dtype"])
            x = np.clip(np.round(x / scale + zero), info.min, info.max)
        interp.set_tensor(inp["index"], x.astype(inp["dtype"]))
        interp.invoke()
        y = interp.get_tensor(out["index"])[0]
        y_true.append(index[item.label])
        y_pred.append(int(np.argmax(y)))
    return {**classification_report(y_true, y_pred, labels), "xnnpack": xnnpack_ok}


def export_run(run_dir: Path, data_dirs: list[Path], max_int8_drop: float = 2.0, model=None,
               eval_limit: int | None = None, seed: int = 42) -> dict:
    import keras

    from ..train import model as _register_custom_layers  # noqa: F401  (CoordinateAttention)

    run_dir = Path(run_dir)
    name, version = run_dir.parent.name, run_dir.name
    models_root = run_dir.parent.parent
    labels = json.loads((run_dir / "labels.json").read_text(encoding="utf-8"))
    items, data_labels = read_manifests(data_dirs)
    if data_labels != labels:
        raise SystemExit(f"labels in {run_dir} do not match the data manifests")
    if model is None:
        model = keras.models.load_model(run_dir / "model.keras", compile=False)

    rng = random.Random(seed)
    pool = split(items, "train") + split(items, "val")
    rep_items = rng.sample(pool, min(REPRESENTATIVE_SAMPLES, len(pool)))
    eval_items = split(items, "test") or split(items, "val")
    eval_split = "test" if split(items, "test") else "val"
    if eval_limit and len(eval_items) > eval_limit:
        eval_items = rng.sample(eval_items, eval_limit)

    print(f"[export] converting {name} {version} (representative set: {len(rep_items)})")
    files = convert(model, rep_items, run_dir, name)
    (run_dir / f"{name}_labels.txt").write_text("\n".join(labels) + "\n", encoding="utf-8")

    metrics_path = run_dir / "metrics.json"
    metrics = json.loads(metrics_path.read_text(encoding="utf-8")) if metrics_path.exists() else {}
    keras_acc = (metrics.get("keras", {}).get(eval_split) or {}).get("accuracy")
    tflite_metrics: dict[str, dict] = {}
    for variant, path in files.items():
        print(f"[export] evaluating {path.name} on {len(eval_items)} {eval_split} images")
        try:
            m = evaluate_tflite(path, eval_items, labels)
        except Exception as exc:  # one broken variant must not throw away the training run
            if variant == "fp16":
                raise
            print(f"!!! {path.name} could not be evaluated: {exc}", flush=True)
            tflite_metrics[variant] = {"error": str(exc)}
            continue
        tflite_metrics[variant] = m
        print(f"         accuracy={m['accuracy']:.4f} macro_f1={m['macro_f1']:.4f} "
              f"size={path.stat().st_size / 1e6:.1f} MB xnnpack={'yes' if m['xnnpack'] else 'no'}")
    metrics["tflite"] = {"split": eval_split, **tflite_metrics}

    recommended = "int8"
    int8_acc = tflite_metrics["int8"].get("accuracy")
    if int8_acc is None:
        recommended = "fp16"
        print(f"!!! WARNING: INT8 model unusable; recommending FP16 for {name} {version}.", flush=True)
    elif keras_acc is not None:
        drop = (keras_acc - int8_acc) * 100
        metrics["tflite"]["int8_drop_points"] = round(drop, 2)
        if drop > max_int8_drop:
            recommended = "fp16"
            print(f"!!! WARNING: INT8 loses {drop:.2f} accuracy points (limit {max_int8_drop}); "
                  f"recommending FP16 for {name} {version}. Consider more representative samples "
                  f"or quantization-aware training.", flush=True)
    metrics_path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")

    file_entries = []
    for v in ("int8", "fp16"):
        entry_file = file_entry(files[v], v, _input_dtype(files[v]), models_root)
        entry_file["xnnpack"] = tflite_metrics[v].get("xnnpack")  # false: app must disable XNNPACK for it
        entry_file["accuracy"] = tflite_metrics[v].get("accuracy")
        file_entries.append(entry_file)

    test_domains = sorted({i.domain for i in eval_items})
    entry = {
        "name": name,
        "version": int(version.lstrip("v")),
        "crop": name,
        "labels": labels,
        "display_names": {l: display_name(l) for l in labels},
        "input": INPUT_SPEC,
        "output": {"type": "float32", "shape": [1, len(labels)], "meaning": "softmax probabilities in label order"},
        "files": file_entries,
        "recommended": recommended,
        "metrics": {
            "eval_split": eval_split,
            "eval_domains": test_domains,
            "keras_accuracy": keras_acc,
            "int8_accuracy": int8_acc,
            "fp16_accuracy": tflite_metrics["fp16"]["accuracy"],
            "int8_macro_f1": tflite_metrics["int8"].get("macro_f1"),
            "fp16_macro_f1": tflite_metrics["fp16"]["macro_f1"],
        },
    }
    manifest = update_manifest(models_root, entry)
    print(f"[export] done -> {run_dir}; manifest {manifest}")
    return entry


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--run", required=True, help="models/<name>/v<N> directory produced by train.py")
    p.add_argument("--data", nargs="+", required=True, help="the processed crop dir(s) it was trained on")
    p.add_argument("--max-int8-drop", type=float, default=2.0)
    p.add_argument("--eval-limit", type=int, help="evaluate on at most this many images")
    args = p.parse_args(argv)
    export_run(Path(args.run), [Path(d) for d in args.data], args.max_int8_drop, eval_limit=args.eval_limit)
    return 0


if __name__ == "__main__":
    sys.exit(main())

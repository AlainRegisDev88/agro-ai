"""Train one crop expert and export it to TFLite.

    python -m agro_ai.train.train --data /kaggle/working/agro-data/processed/cassava

    # PlantVillage-style pretraining on several crops, then field fine-tuning:
    python -m agro_ai.train.train --data .../processed/tomato .../processed/potato --name pv_pretrain --no-export
    python -m agro_ai.train.train --data .../processed/maize --init-from models/pv_pretrain/v1/model.keras

Writes models/<name>/v<N>/: model.keras, labels.json, history.json, metrics.json,
and (unless --no-export) the quantized .tflite files; then updates models/manifest.json.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path

import numpy as np

from ..paths import models_dir
from .dataset import class_weights, make_dataset, read_manifests, split
from .metrics import classification_report


def next_version_dir(root: Path, name: str) -> Path:
    base = root / name
    versions = [int(m.group(1)) for p in base.glob("v*") if (m := re.fullmatch(r"v(\d+)", p.name))]
    out = base / f"v{max(versions, default=0) + 1}"
    out.mkdir(parents=True)
    return out


def evaluate_keras(model, items, labels, batch: int) -> dict:
    if not items:
        return {}
    ds = make_dataset(items, labels, batch, training=False)
    probs = model.predict(ds, verbose=0)
    index = {l: i for i, l in enumerate(labels)}
    y_true = [index[i.label] for i in items]
    return classification_report(y_true, np.argmax(probs, axis=1), labels)


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--data", nargs="+", required=True, help="processed crop dir(s) from dataset_factory")
    p.add_argument("--name", help="expert name (default: the single crop dir's name)")
    p.add_argument("--models-dir", help="defaults to $AGRO_MODELS_DIR, /kaggle/working/models, or ./models")
    p.add_argument("--epochs-head", type=int, default=10)
    p.add_argument("--epochs-finetune", type=int, default=15)
    p.add_argument("--batch", type=int, default=64)
    p.add_argument("--lr-head", type=float, default=1e-3)
    p.add_argument("--lr-finetune", type=float, default=1e-4)
    p.add_argument("--unfreeze", type=int, default=60, help="backbone layers to fine-tune in phase 2")
    p.add_argument("--label-smoothing", type=float, default=0.1)
    p.add_argument("--coord-attention", action="store_true", help="add a Coordinate Attention block")
    p.add_argument("--init-from", help="model.keras whose backbone weights initialise this one")
    p.add_argument("--no-class-weights", action="store_true")
    p.add_argument("--mixed-precision", action="store_true", help="float16 training on GPU")
    p.add_argument("--no-export", action="store_true")
    p.add_argument("--max-int8-drop", type=float, default=2.0, help="accuracy points INT8 may lose vs Keras")
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args(argv)

    import keras
    import tensorflow as tf

    from .model import build_model, find_backbone, unfreeze_top

    keras.utils.set_random_seed(args.seed)
    if args.mixed_precision:
        keras.mixed_precision.set_global_policy("mixed_float16")

    data_dirs = [Path(d) for d in args.data]
    name = args.name or (data_dirs[0].name if len(data_dirs) == 1 else None)
    if not name:
        p.error("--name is required when training on several data dirs")
    items, labels = read_manifests(data_dirs)
    train_items, val_items, test_items = split(items, "train"), split(items, "val"), split(items, "test")
    print(f"[data] {name}: {len(labels)} classes, train={len(train_items)} val={len(val_items)} "
          f"test={len(test_items)} (test field images: {sum(i.domain == 'field' for i in test_items)})")
    if not train_items or not val_items:
        raise SystemExit("need non-empty train and val splits")

    run_dir = next_version_dir(models_dir(args.models_dir), name)
    print(f"[run] {run_dir}")
    train_ds = make_dataset(train_items, labels, args.batch, training=True,
                            label_smoothing=args.label_smoothing, seed=args.seed)
    val_ds = make_dataset(val_items, labels, args.batch, training=False)
    weights = None if args.no_class_weights else class_weights(train_items, labels)

    model = build_model(len(labels), coord_attention=args.coord_attention)
    if args.init_from:
        source = keras.models.load_model(args.init_from, compile=False)
        find_backbone(model).set_weights(find_backbone(source).get_weights())
        print(f"[init] backbone weights from {args.init_from}")

    ckpt = str(run_dir / "model.keras")

    def callbacks(patience: int):
        return [
            keras.callbacks.ModelCheckpoint(ckpt, monitor="val_accuracy", save_best_only=True),
            keras.callbacks.EarlyStopping(monitor="val_accuracy", patience=patience, restore_best_weights=True),
            keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.3, patience=2, min_lr=1e-6),
        ]

    loss = keras.losses.CategoricalCrossentropy()
    history: dict[str, list] = {}
    started = time.time()

    model.compile(optimizer=keras.optimizers.Adam(args.lr_head), loss=loss, metrics=["accuracy"])
    h1 = model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_head,
                   class_weight=weights, callbacks=callbacks(3), verbose=2)
    history["head"] = {k: [float(v) for v in vs] for k, vs in h1.history.items()}

    if args.epochs_finetune > 0:
        unfreeze_top(model, args.unfreeze)
        model.compile(optimizer=keras.optimizers.Adam(args.lr_finetune), loss=loss, metrics=["accuracy"])
        h2 = model.fit(train_ds, validation_data=val_ds, epochs=args.epochs_finetune,
                       class_weight=weights, callbacks=callbacks(4), verbose=2)
        history["finetune"] = {k: [float(v) for v in vs] for k, vs in h2.history.items()}

    # The checkpoint holds the best fine-tuning epoch. Evaluate and export a float32 copy of it.
    best = keras.models.load_model(ckpt, compile=False)
    if args.mixed_precision:
        keras.mixed_precision.set_global_policy("float32")
        weights_fp16 = best.get_weights()
        best = build_model(len(labels), coord_attention=args.coord_attention, weights=None)
        best.set_weights(weights_fp16)
        best.save(ckpt)

    metrics = {
        "keras": {"val": evaluate_keras(best, val_items, labels, args.batch),
                  "test": evaluate_keras(best, test_items, labels, args.batch)},
        "train_seconds": round(time.time() - started),
        "tensorflow": tf.__version__,
    }
    config = vars(args)
    (run_dir / "labels.json").write_text(json.dumps(labels, indent=2), encoding="utf-8")
    (run_dir / "history.json").write_text(json.dumps(history, indent=2), encoding="utf-8")
    (run_dir / "train_config.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
    (run_dir / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    test = metrics["keras"]["test"] or metrics["keras"]["val"]
    print(f"[keras] test accuracy={test['accuracy']:.4f} macro_f1={test['macro_f1']:.4f} (n={test['n']})")

    if not args.no_export:
        from ..export.tflite import export_run
        export_run(run_dir, data_dirs, max_int8_drop=args.max_int8_drop, model=best)
    return 0


if __name__ == "__main__":
    sys.exit(main())

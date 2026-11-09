import json

import pytest

from agro_ai.export.manifest import file_entry, update_manifest
from agro_ai.export.publish import gh_command, main, publish


def fake_model(root, name, version, payload=b"model"):
    run = root / name / f"v{version}"
    run.mkdir(parents=True)
    files = []
    for variant, dtype in (("int8", "uint8"), ("fp16", "float32")):
        f = run / f"{name}_{variant}.tflite"
        f.write_bytes(payload + variant.encode())
        files.append(file_entry(f, variant, dtype, root))
    (run / f"{name}_labels.txt").write_text(f"{name}__healthy\n", encoding="utf-8")
    entry = {"name": name, "version": version, "crop": name, "files": files, "recommended": "int8"}
    update_manifest(root, entry)
    return entry


def test_publish_flat_folder(tmp_path):
    models, out = tmp_path / "models", tmp_path / "dist/release"
    fake_model(models, "cassava", 1, b"old")
    fake_model(models, "cassava", 2)
    fake_model(models, "maize", 1)
    written = publish(models, out)
    assert sorted(p.name for p in out.iterdir()) == sorted(p.name for p in written) == [
        "cassava_fp16.tflite", "cassava_int8.tflite", "cassava_labels.txt",
        "maize_fp16.tflite", "maize_int8.tflite", "maize_labels.txt", "manifest.json"]
    assert (out / "cassava_int8.tflite").read_bytes() == b"modelint8"

    source = json.loads((models / "manifest.json").read_text())
    released = json.loads((out / "manifest.json").read_text())
    assert released["schema"] == 1
    assert released["models"] == [m for m in source["models"] if (m["name"], m["version"]) != ("cassava", 1)]
    assert released["models"][0]["files"][0]["path"] == "cassava/v2/cassava_int8.tflite"


def test_publish_sha_mismatch(tmp_path):
    fake_model(tmp_path, "cassava", 1)
    (tmp_path / "cassava/v1/cassava_fp16.tflite").write_bytes(b"tampered")
    with pytest.raises(SystemExit, match="sha256 mismatch"):
        publish(tmp_path, tmp_path / "out")
    assert not (tmp_path / "out").exists()


def test_publish_duplicate_basename(tmp_path):
    entry = fake_model(tmp_path, "cassava", 1)
    other = tmp_path / "other/v1"
    other.mkdir(parents=True)
    dup = other / "cassava_int8.tflite"
    dup.write_bytes(b"x")
    (other / "other_labels.txt").write_text("other__healthy\n", encoding="utf-8")
    update_manifest(tmp_path, {**entry, "name": "other", "files": [file_entry(dup, "int8", "uint8", tmp_path)]})
    with pytest.raises(SystemExit, match="share the basename 'cassava_int8.tflite'"):
        publish(tmp_path, tmp_path / "out")


def test_publish_missing_labels(tmp_path):
    fake_model(tmp_path, "cassava", 1)
    (tmp_path / "cassava/v1/cassava_labels.txt").unlink()
    with pytest.raises(SystemExit, match="missing file"):
        publish(tmp_path, tmp_path / "out")


def test_main_prints_gh_command(tmp_path, capsys):
    fake_model(tmp_path / "models", "cassava", 1)
    out = tmp_path / "release"
    assert main(["--models", str(tmp_path / "models"), "--out", str(out), "--tag", "models-test"]) == 0
    printed = capsys.readouterr().out
    assert "gh release create models-test" in printed
    assert f'"{(out / "manifest.json").as_posix()}"' in printed
    assert gh_command([out / "a.tflite"], "t").startswith("gh release create t ")

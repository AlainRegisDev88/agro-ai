import json

from agro_ai.export.manifest import display_name, file_entry, sha256, update_manifest
from agro_ai.train.dataset import class_weights, read_manifests
from agro_ai.train.metrics import classification_report
from agro_ai.train.train import next_version_dir


def test_classification_report():
    r = classification_report([0, 0, 1, 1, 2], [0, 1, 1, 1, 0], ["a", "b", "c"])
    assert r["accuracy"] == 0.6
    assert r["per_class"]["b"] == {"precision": 0.6667, "recall": 1.0, "f1": 0.8, "support": 2}
    assert r["per_class"]["c"]["recall"] == 0.0
    assert r["confusion_matrix"][0] == [1, 1, 0]


def test_manifest_replaces_same_version(tmp_path):
    f = tmp_path / "cassava/v1/cassava_int8.tflite"
    f.parent.mkdir(parents=True)
    f.write_bytes(b"model")
    entry = {"name": "cassava", "version": 1, "files": [file_entry(f, "int8", "uint8", tmp_path)]}
    update_manifest(tmp_path, entry)
    update_manifest(tmp_path, {**entry, "recommended": "fp16"})
    update_manifest(tmp_path, {"name": "cassava", "version": 2, "files": []})
    data = json.loads((tmp_path / "manifest.json").read_text())
    assert [(m["name"], m["version"]) for m in data["models"]] == [("cassava", 1), ("cassava", 2)]
    assert data["models"][0]["recommended"] == "fp16"
    assert data["models"][0]["files"][0]["path"] == "cassava/v1/cassava_int8.tflite"
    assert data["models"][0]["files"][0]["sha256"] == sha256(f)


def test_display_name():
    assert display_name("cassava__brown_streak_disease") == "Brown streak disease"


def test_next_version_dir(tmp_path):
    (tmp_path / "maize/v1").mkdir(parents=True)
    (tmp_path / "maize/v3").mkdir()
    (tmp_path / "maize/vx").mkdir()
    assert next_version_dir(tmp_path, "maize").name == "v4"
    assert next_version_dir(tmp_path, "tea").name == "v1"


def test_read_manifests_merges_dirs(tmp_path):
    for crop, label in (("tomato", "tomato__healthy"), ("potato", "potato__late_blight")):
        d = tmp_path / crop
        d.mkdir()
        (d / "manifest.csv").write_text(
            "path,label,source,domain,split,phash,original\n"
            f"images/x/1.jpg,{label},s,lab,train,00,o\n"
            f"images/x/2.jpg,{label},s,lab,train,01,o\n", encoding="utf-8")
    items, labels = read_manifests([tmp_path / "tomato", tmp_path / "potato"])
    assert labels == ["potato__late_blight", "tomato__healthy"]
    assert len(items) == 4
    assert class_weights(items, labels) == {0: 1.0, 1: 1.0}

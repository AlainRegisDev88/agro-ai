import csv
import json
import shutil
from collections import Counter

import yaml

from agro_ai.data.factory import Record, assign_splits, dedupe, run


def _rec(label, domain, i, phash=None, priority=50):
    return Record(f"images/{label}/{i}.jpg", label, "s", domain, priority, phash or f"{i:016x}", "")


def test_dedupe_prefers_field_then_priority():
    lab = _rec("maize__rust", "lab", 1, phash="aa", priority=10)
    field = _rec("maize__rust", "field", 2, phash="aa", priority=90)
    other = _rec("maize__rust", "lab", 3, phash="bb")
    kept, dupes, conflicts = dedupe([lab, field, other])
    assert dupes == 1 and conflicts == 0
    assert field in kept and lab not in kept


def test_dedupe_counts_label_conflicts():
    kept, dupes, conflicts = dedupe([_rec("a__x", "lab", 1, phash="aa"), _rec("a__y", "lab", 2, phash="aa")])
    assert (len(kept), dupes, conflicts) == (1, 1, 1)


def test_splits_are_stratified_and_test_is_field_only():
    records = [_rec("maize__rust", "lab", i) for i in range(100)]
    records += [_rec("maize__rust", "field", 100 + i) for i in range(100)]
    records += [_rec("maize__healthy", "lab", 300 + i) for i in range(50)]
    test_domain = assign_splits(records, val_frac=0.1, test_frac=0.1, seed=0)

    split = Counter((r.label, r.split) for r in records)
    assert split[("maize__rust", "test")] == 20
    assert split[("maize__rust", "val")] == 20
    assert all(r.domain == "field" for r in records if r.split == "test" and r.label == "maize__rust")
    assert test_domain == {"maize__rust": "field", "maize__healthy": "lab"}
    assert split[("maize__healthy", "test")] == 5


def test_splits_are_deterministic():
    a = [_rec("t__x", "lab", i) for i in range(40)]
    b = [_rec("t__x", "lab", i) for i in range(40)]
    assign_splits(a, 0.1, 0.1, seed=7)
    assign_splits(b, 0.1, 0.1, seed=7)
    assert [r.split for r in a] == [r.split for r in b]


def test_end_to_end_with_local_sources(tmp_path, image_factory):
    # lab source: PlantVillage-style folders; field source: plain folders, sharing one duplicate image
    for i in range(12):
        image_factory(f"lab/Corn_(maize)___Common_rust_/{i}.jpg", seed=i)
        image_factory(f"lab/Corn_(maize)___healthy/{i}.jpg", seed=100 + i)
        image_factory(f"field/Common_Rust/{i}.jpg", seed=200 + i)
    image_factory("field/Mystery_Spot/0.jpg", seed=999)
    shutil.copy(tmp_path / "lab/Corn_(maize)___Common_rust_/0.jpg", tmp_path / "field/Common_Rust/dup.jpg")
    (tmp_path / "field/Common_Rust/broken.jpg").write_bytes(b"garbage")

    sources = {"sources": {
        "lab_src": {"kind": "local", "ref": str(tmp_path / "lab"), "adapter": "folder_per_class",
                    "label_set": "plantvillage", "domain": "lab", "priority": 50},
        "field_src": {"kind": "local", "ref": str(tmp_path / "field"), "adapter": "folder_per_class",
                      "label_set": "maize_field", "domain": "field", "priority": 20},
    }}
    sources_path = tmp_path / "sources.yaml"
    sources_path.write_text(yaml.safe_dump(sources))

    out = tmp_path / "data"
    reports = run(["maize"], data_root=out, workers=1, sources_path=sources_path)

    crop_dir = out / "processed" / "maize"
    rows = list(csv.DictReader((crop_dir / "manifest.csv").open(encoding="utf-8")))
    assert {r["label"] for r in rows} == {"maize__common_rust", "maize__healthy"}
    assert all((crop_dir / r["path"]).exists() for r in rows)
    assert len(rows) == 36  # 12 + 12 + 13 field (incl. dup) - 1 duplicate; broken file rejected
    assert all(r["domain"] == "field" for r in rows if r["split"] == "test" and r["label"] == "maize__common_rust")

    report = json.loads((crop_dir / "report.json").read_text())
    assert report == reports["maize"]
    assert report["duplicates_removed"] == 1
    assert report["rejected"] == {"corrupt": 1}
    assert report["sources"]["field_src"]["unmapped"] == {"Mystery_Spot": 1}
    assert report["test_domain"]["maize__healthy"] == "lab"
    assert (crop_dir / "labels.txt").read_text().split() == ["maize__common_rust", "maize__healthy"]

    # second run reuses processed images and gives identical output
    run(["maize"], data_root=out, workers=1, sources_path=sources_path)
    assert list(csv.DictReader((crop_dir / "manifest.csv").open(encoding="utf-8"))) == rows

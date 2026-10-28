import json

from agro_ai.data.adapters import bbox_crops, cassava_csv, folder_per_class, rocole
from agro_ai.data.labels import ClassMap, normalize
from agro_ai.data.sources import load_sources

CFG = {"name": "src", "domain": "field"}


def test_normalize_plantvillage_folder_names():
    assert normalize("Corn_(maize)___Common_rust_") == "corn_maize_common_rust"
    assert normalize("Pepper,_bell___Bacterial_spot") == "pepper_bell_bacterial_spot"
    assert normalize("Tomato___Spider_mites Two-spotted_spider_mite") == "tomato_spider_mites_two_spotted_spider_mite"


def test_shipped_configs_are_consistent():
    cmap = ClassMap.load()
    for name, cfg in load_sources().items():
        assert cfg["label_set"] in cmap.tables, f"{name} points at missing label_set {cfg['label_set']}"
    assert cmap.lookup("plantvillage", "Corn_(maize)___Common_rust_") == ("maize__common_rust", True)
    assert cmap.lookup("plantvillage", "test") == (None, True)
    assert cmap.lookup("plantvillage", "Banana___Sigatoka") == (None, False)
    assert cmap.lookup("cassava_competition", "Cassava Mosaic Disease (CMD)")[0] == "cassava__mosaic_disease"


def test_folder_per_class_uses_parent_dir(tmp_path, image_factory):
    image_factory("ds/train/Healthy/1.jpg", 1)
    image_factory("ds/valid/Common_Rust/2.png", 2)
    (tmp_path / "ds/train/Healthy/readme.txt").write_text("x")
    samples = list(folder_per_class(tmp_path / "ds", CFG))
    assert sorted(s.raw_label for s in samples) == ["Common_Rust", "Healthy"]


def test_folder_per_class_exclude_dirs(tmp_path, image_factory):
    image_factory("ds/keep/A/1.jpg", 1)
    image_factory("ds/Skip/A/2.jpg", 2)
    samples = list(folder_per_class(tmp_path / "ds", {**CFG, "exclude_dirs": ["skip"]}))
    assert len(samples) == 1


def test_cassava_csv(tmp_path, image_factory):
    root = tmp_path / "cassava"
    image_factory("cassava/train_images/100.jpg", 1)
    (root / "train.csv").write_text("image_id,label\n100.jpg,3\n", encoding="utf-8")
    (root / "label_num_to_disease_map.json").write_text(json.dumps({"3": "Cassava Mosaic Disease (CMD)"}))
    [s] = list(cassava_csv(root, CFG))
    assert s.raw_label == "Cassava Mosaic Disease (CMD)"
    assert s.path.exists()


def test_rocole_csv(tmp_path, image_factory):
    root = tmp_path / "rocole"
    image_factory("rocole/Photos/C1P1H1.jpg", 1)
    (root / "Annotations").mkdir()
    (root / "Annotations/RoCoLe-csv.csv").write_text(
        "ID,File,Binary.Label,Multiclass.Label\n1,C1P1H1.jpg,unhealthy,rust_level_2\n", encoding="utf-8")
    [s] = list(rocole(root, CFG))
    assert s.raw_label == "rust_level_2"


def test_bbox_crops_yolo(tmp_path, image_factory):
    root = tmp_path / "fp"
    image_factory("fp/train/images/leaf1.jpg", 1)
    (root / "train/labels").mkdir(parents=True)
    (root / "train/labels/leaf1.txt").write_text("1 0.5 0.5 0.4 0.4\n0 0.2 0.2 0.1 0.1\n")
    (root / "data.yaml").write_text("names: ['Corn Blight', 'Cassava Mosaic']\n")
    samples = list(bbox_crops(root, CFG))
    assert [s.raw_label for s in samples] == ["Cassava Mosaic", "Corn Blight"]
    assert samples[0].box.normalized


def test_bbox_crops_coco(tmp_path, image_factory):
    root = tmp_path / "coco"
    image_factory("coco/train/a.jpg", 1)
    (root / "train/_annotations.coco.json").write_text(json.dumps({
        "images": [{"id": 1, "file_name": "a.jpg"}],
        "annotations": [{"image_id": 1, "category_id": 7, "bbox": [10, 10, 120, 120]}],
        "categories": [{"id": 7, "name": "Tomato leaf late blight"}],
    }))
    [s] = list(bbox_crops(root, CFG))
    assert s.raw_label == "Tomato leaf late blight"
    assert (s.box.x1, s.box.y1) == (130, 130)

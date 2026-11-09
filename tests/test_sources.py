import pytest
import yaml

from agro_ai.data import sources
from agro_ai.data.factory import run
from agro_ai.data.sources import _kaggle_mounts

DATASET = {"kind": "kaggle_dataset", "ref": "shashwatwork/identifying-disease-in-tea-leafs"}
COMPETITION = {"kind": "kaggle_competition", "ref": "cassava-leaf-disease-classification"}


@pytest.mark.parametrize("layout", [
    "identifying-disease-in-tea-leafs",
    "datasets/shashwatwork/identifying-disease-in-tea-leafs",
    "some-folder/identifying-disease-in-tea-leafs",
])
def test_dataset_mount_layouts(tmp_path, layout):
    (tmp_path / layout).mkdir(parents=True)
    assert _kaggle_mounts(DATASET, tmp_path)[0] == tmp_path / layout


@pytest.mark.parametrize("layout", ["cassava-leaf-disease-classification",
                                    "competitions/cassava-leaf-disease-classification"])
def test_competition_mount_layouts(tmp_path, layout):
    (tmp_path / layout).mkdir(parents=True)
    assert _kaggle_mounts(COMPETITION, tmp_path) == [tmp_path / layout]


def test_missing_mount(tmp_path):
    (tmp_path / "other-dataset").mkdir()
    assert _kaggle_mounts(DATASET, tmp_path) == []


def test_unattached_sources_are_skipped_on_kaggle(tmp_path, image_factory, monkeypatch):
    for i in range(6):
        image_factory(f"input/corn-or-maize-leaf-disease-dataset/data/Healthy/{i}.jpg", seed=i)
        image_factory(f"input/corn-or-maize-leaf-disease-dataset/data/Common_Rust/{i}.jpg", seed=50 + i)
    monkeypatch.setattr(sources, "KAGGLE_INPUT", tmp_path / "input")
    monkeypatch.setattr(sources, "on_kaggle", lambda: True)

    cfg = {"sources": {
        "maize_field": {"kind": "kaggle_dataset", "ref": "smaranjitghose/corn-or-maize-leaf-disease-dataset",
                        "adapter": "folder_per_class", "label_set": "maize_field", "domain": "field"},
        "new_plant_diseases": {"kind": "kaggle_dataset", "ref": "vipoooool/new-plant-diseases-dataset",
                               "adapter": "folder_per_class", "label_set": "plantvillage", "domain": "lab"},
    }}
    path = tmp_path / "sources.yaml"
    path.write_text(yaml.safe_dump(cfg))

    report = run(["maize"], data_root=tmp_path / "out", workers=1, download=False, sources_path=path)["maize"]
    assert report["total"] == 12
    assert list(report["sources_skipped"]) == ["new_plant_diseases"]

    with pytest.raises(FileNotFoundError, match="Add Input"):
        run(["maize"], ["new_plant_diseases"], data_root=tmp_path / "out", workers=1, download=False,
            sources_path=path)

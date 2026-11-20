"""Turn each dataset's on-disk layout into a stream of labeled samples.

Every adapter has the signature ``adapter(root, cfg) -> Iterator[Sample]`` where
``root`` is the extracted dataset directory and ``cfg`` its entry in sources.yaml.
Adapters only read file names and annotation files; images are opened later.
"""

from __future__ import annotations

import csv
import json
import os
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterator

import yaml

from .images import Box, is_image_file


@dataclass(frozen=True)
class Sample:
    path: Path
    raw_label: str
    source: str
    domain: str
    box: Box | None = None


Adapter = Callable[[Path, dict], Iterator[Sample]]


def _walk(root: Path, exclude: list[str] | None = None) -> Iterator[Path]:
    """All files under root in a stable order, skipping hidden and excluded dirs."""
    excluded = {e.strip("/\\").replace("\\", "/").lower() for e in (exclude or [])}
    for dirpath, dirnames, filenames in os.walk(root):
        rel = Path(dirpath).relative_to(root).as_posix().lower()
        dirnames[:] = sorted(
            d for d in dirnames
            if not d.startswith((".", "__"))
            and (f"{rel}/{d.lower()}" if rel != "." else d.lower()) not in excluded
        )
        for name in sorted(filenames):
            yield Path(dirpath) / name


def _index_images(root: Path) -> dict[str, Path]:
    """Map lowercased file name and stem to path, for annotation files that only store names."""
    index: dict[str, Path] = {}
    for p in _walk(root):
        if is_image_file(p):
            index.setdefault(p.name.lower(), p)
            index.setdefault(p.stem.lower(), p)
    return index


def folder_per_class(root: Path, cfg: dict) -> Iterator[Sample]:
    """Label = name of the directory that directly contains the image."""
    for p in _walk(root, cfg.get("exclude_dirs")):
        if is_image_file(p) and p.parent != root:
            yield Sample(p, p.parent.name, cfg["name"], cfg["domain"])


def cassava_csv(root: Path, cfg: dict) -> Iterator[Sample]:
    """Kaggle cassava competition: train.csv (image_id,label) + train_images/."""
    csvs = sorted(root.rglob("train.csv"))
    if not csvs:
        raise FileNotFoundError(f"train.csv not found under {root}")
    table = csvs[0]
    names: dict[str, str] = {}
    mapping = table.parent / "label_num_to_disease_map.json"
    if mapping.exists():
        names = {str(k): v for k, v in json.loads(mapping.read_text(encoding="utf-8")).items()}
    image_dir = table.parent / "train_images"
    with table.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            label = str(row["label"]).strip()
            yield Sample(image_dir / row["image_id"], names.get(label, label), cfg["name"], cfg["domain"])


def _find_column(header: list[str], *candidates: str) -> str | None:
    norm = {h.lower().replace(".", "_").replace(" ", "_"): h for h in header}
    for c in candidates:
        if c in norm:
            return norm[c]
    return None


def rocole(root: Path, cfg: dict) -> Iterator[Sample]:
    """RoCoLe: a CSV with a file column and a multiclass label column.

    Falls back to folder_per_class when the Kaggle copy is already sorted into folders.
    """
    index: dict[str, Path] | None = None
    for table in sorted(root.rglob("*.csv")):
        with table.open(newline="", encoding="utf-8-sig") as fh:
            reader = csv.DictReader(fh)
            header = reader.fieldnames or []
            file_col = _find_column(header, "file", "filename", "file_name", "image", "image_id")
            label_col = _find_column(header, "multiclass_label", "multiclass", "label", "class")
            if not (file_col and label_col):
                continue
            if index is None:
                index = _index_images(root)
            found = False
            for row in reader:
                name = Path(row[file_col].strip()).name.lower()
                path = index.get(name) or index.get(Path(name).stem)
                if path is not None:
                    found = True
                    yield Sample(path, row[label_col].strip(), cfg["name"], cfg["domain"])
            if found:
                return
    yield from folder_per_class(root, cfg)


def _yolo_names(root: Path) -> list[str]:
    for p in sorted(root.rglob("*.yaml")) + sorted(root.rglob("*.yml")):
        data = yaml.safe_load(p.read_text(encoding="utf-8")) or {}
        names = data.get("names") if isinstance(data, dict) else None
        if isinstance(names, dict):
            return [names[k] for k in sorted(names, key=int)]
        if isinstance(names, list):
            return [str(n) for n in names]
    for p in sorted(root.rglob("classes.txt")):
        return [line.strip() for line in p.read_text(encoding="utf-8").splitlines() if line.strip()]
    return []


def _coco(root: Path, cfg: dict, index: dict[str, Path]) -> Iterator[Sample]:
    for ann_file in sorted(root.rglob("*.json")):
        try:
            data = json.loads(ann_file.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if not (isinstance(data, dict) and {"images", "annotations", "categories"} <= data.keys()):
            continue
        cats = {c["id"]: c["name"] for c in data["categories"]}
        images = {}
        for im in data["images"]:
            local = ann_file.parent / im["file_name"]
            images[im["id"]] = local if local.exists() else index.get(Path(im["file_name"]).name.lower())
        for ann in data["annotations"]:
            path = images.get(ann["image_id"])
            if path is None or "bbox" not in ann:
                continue
            x, y, w, h = ann["bbox"]
            yield Sample(path, cats.get(ann["category_id"], str(ann["category_id"])), cfg["name"], cfg["domain"],
                         Box(x, y, x + w, y + h))


def _voc(root: Path, cfg: dict, index: dict[str, Path]) -> Iterator[Sample]:
    for xml_file in sorted(root.rglob("*.xml")):
        try:
            tree = ET.parse(xml_file)
        except ET.ParseError:
            continue
        node = tree.getroot()
        if node.tag != "annotation":
            continue
        filename = (node.findtext("filename") or xml_file.stem).strip()
        path = index.get(Path(filename).name.lower()) or index.get(xml_file.stem.lower())
        if path is None:
            continue
        for obj in node.iter("object"):
            bb = obj.find("bndbox")
            if bb is None:
                continue
            coords = [float(bb.findtext(k, "0")) for k in ("xmin", "ymin", "xmax", "ymax")]
            yield Sample(path, (obj.findtext("name") or "").strip(), cfg["name"], cfg["domain"], Box(*coords))


def _yolo(root: Path, cfg: dict, index: dict[str, Path]) -> Iterator[Sample]:
    names = _yolo_names(root)
    for txt in sorted(root.rglob("*.txt")):
        if txt.name == "classes.txt" or "labels" not in {p.lower() for p in txt.parts}:
            continue
        path = index.get(txt.stem.lower())
        if path is None:
            continue
        for line in txt.read_text(encoding="utf-8").splitlines():
            parts = line.split()
            if len(parts) < 5:
                continue
            cls = int(float(parts[0]))
            cx, cy, w, h = map(float, parts[1:5])
            label = names[cls] if cls < len(names) else str(cls)
            yield Sample(path, label, cfg["name"], cfg["domain"],
                         Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, normalized=True))


def bbox_crops(root: Path, cfg: dict) -> Iterator[Sample]:
    """Detection datasets (COCO json, Pascal VOC xml, or YOLO txt): one sample per box."""
    index = _index_images(root)
    for reader in (_coco, _voc, _yolo):
        found = False
        for sample in reader(root, cfg, index):
            found = True
            yield sample
        if found:
            return


ADAPTERS: dict[str, Adapter] = {
    "folder_per_class": folder_per_class,
    "cassava_csv": cassava_csv,
    "rocole": rocole,
    "bbox_crops": bbox_crops,
}

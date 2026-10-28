"""models/manifest.json: the index the Android app reads to offer and download experts.

One entry per (expert, version). The app picks the newest version of each
expert and downloads the file named in "recommended", checking its sha256.
"""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1

INPUT_SPEC = {
    "height": 224,
    "width": 224,
    "channels": 3,
    "color": "RGB",
    "range": "0-255",
    "preprocess": "center-crop to square, resize to 224x224; no normalization (done inside the model)",
}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def display_name(label: str) -> str:
    condition = label.split("__", 1)[-1]
    return condition.replace("_", " ").capitalize()


def file_entry(path: Path, variant: str, input_dtype: str, models_root: Path) -> dict:
    return {
        "variant": variant,
        "path": path.relative_to(models_root).as_posix(),
        "bytes": path.stat().st_size,
        "sha256": sha256(path),
        "input_dtype": input_dtype,
    }


def update_manifest(models_root: Path, entry: dict) -> Path:
    path = models_root / "manifest.json"
    data = {"schema": SCHEMA_VERSION, "models": []}
    if path.exists():
        data = json.loads(path.read_text(encoding="utf-8"))
    models = [m for m in data.get("models", [])
              if not (m["name"] == entry["name"] and m["version"] == entry["version"])]
    models.append({**entry, "created": datetime.now(timezone.utc).isoformat(timespec="seconds")})
    models.sort(key=lambda m: (m["name"], m["version"]))
    data = {"schema": SCHEMA_VERSION, "models": models}
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    return path

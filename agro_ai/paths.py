"""Where data and models live.

The repository sits inside OneDrive, so tens of GB of images must never be
written under it. Everything heavy goes to AGRO_DATA_DIR instead.
"""

from __future__ import annotations

import os
from pathlib import Path

CONFIG_DIR = Path(__file__).resolve().parent / "config"
REPO_ROOT = Path(__file__).resolve().parent.parent


def on_kaggle() -> bool:
    return Path("/kaggle/input").is_dir()


def data_dir(override: str | os.PathLike | None = None) -> Path:
    if override:
        return Path(override)
    env = os.environ.get("AGRO_DATA_DIR")
    if env:
        return Path(env)
    if on_kaggle():
        return Path("/kaggle/working/agro-data")
    if os.name == "nt":
        return Path("C:/agro-data")
    return Path.home() / "agro-data"


def raw_dir(root: Path) -> Path:
    return root / "raw"


def processed_dir(root: Path) -> Path:
    return root / "processed"


def models_dir(override: str | os.PathLike | None = None) -> Path:
    if override:
        return Path(override)
    env = os.environ.get("AGRO_MODELS_DIR")
    if env:
        return Path(env)
    if on_kaggle():
        return Path("/kaggle/working/models")
    return REPO_ROOT / "models"

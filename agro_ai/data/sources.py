"""Locate or download each dataset listed in sources.yaml.

On Kaggle, attached datasets are already mounted under /kaggle/input and are
used in place. Elsewhere, files are downloaded with the Kaggle API (needs
~/.kaggle/kaggle.json) or git, into <data_dir>/raw/<source name>/.
"""

from __future__ import annotations

import subprocess
import zipfile
from pathlib import Path

import yaml

from ..paths import CONFIG_DIR, on_kaggle, raw_dir

DONE_MARKER = ".agro_complete"


def load_sources(path: Path | None = None) -> dict[str, dict]:
    path = path or CONFIG_DIR / "sources.yaml"
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    sources = {}
    for name, cfg in (data.get("sources") or {}).items():
        cfg = dict(cfg)
        cfg["name"] = name
        cfg.setdefault("enabled", True)
        cfg.setdefault("priority", 100)
        cfg.setdefault("label_set", name)
        missing = {"kind", "ref", "adapter", "domain"} - cfg.keys()
        if missing:
            raise ValueError(f"source {name!r} is missing {sorted(missing)}")
        if cfg["domain"] not in {"lab", "field"}:
            raise ValueError(f"source {name!r}: domain must be 'lab' or 'field'")
        sources[name] = cfg
    return sources


KAGGLE_INPUT = Path("/kaggle/input")


def _kaggle_mounts(cfg: dict, base: Path | None = None) -> list[Path]:
    """Where Kaggle may have mounted an attached input: the known layouts first,
    then any folder named after the dataset up to three levels down."""
    base = base or KAGGLE_INPUT
    ref = cfg["ref"]
    if cfg["kind"] == "kaggle_competition":
        name = ref
        known = [base / ref, base / "competitions" / ref]
    else:
        owner, _, name = ref.partition("/")
        known = [base / name, base / "datasets" / owner / name, base / ref]
    found = [m for m in known if m.is_dir()]
    for depth in range(1, 4):
        found += sorted(p for p in base.glob("/".join(["*"] * (depth - 1) + [name])) if p.is_dir())
    return list(dict.fromkeys(found))


def _kaggle_api():
    try:
        from kaggle.api.kaggle_api_extended import KaggleApi
    except ImportError as exc:
        raise RuntimeError("Kaggle downloads need `pip install kaggle`") from exc
    api = KaggleApi()
    api.authenticate()
    return api


def _unzip_all(dest: Path) -> None:
    for archive in list(dest.glob("*.zip")):
        with zipfile.ZipFile(archive) as zf:
            root = dest.resolve()
            for member in zf.namelist():
                target = (dest / member).resolve()
                if not target.is_relative_to(root):
                    raise RuntimeError(f"unsafe path {member!r} in {archive.name}")
            zf.extractall(dest)
        archive.unlink()


def resolve(cfg: dict, data_root: Path, download: bool = True) -> Path:
    """Return the directory holding this source's files, downloading if needed."""
    kind = cfg["kind"]
    sub = cfg.get("subdir")

    if kind == "local":
        path = Path(cfg["ref"])
        if not path.is_dir():
            raise FileNotFoundError(f"local source {cfg['name']!r}: {path} does not exist")
        return path / sub if sub else path

    if on_kaggle() and kind.startswith("kaggle"):
        mounts = _kaggle_mounts(cfg)
        if mounts:
            return mounts[0] / sub if sub else mounts[0]

    dest = raw_dir(data_root) / cfg["name"]
    if (dest / DONE_MARKER).exists():
        return dest / sub if sub else dest
    if not download:
        if on_kaggle():
            raise FileNotFoundError(f"{cfg['name']!r} ({cfg['ref']}) is not attached: add it with 'Add Input' "
                                    f"or leave it out with --sources")
        raise FileNotFoundError(f"{cfg['name']!r} not downloaded yet ({dest}); run without --no-download")

    dest.mkdir(parents=True, exist_ok=True)
    print(f"[download] {cfg['name']} <- {cfg['ref']}", flush=True)
    if kind == "kaggle_dataset":
        _kaggle_api().dataset_download_files(cfg["ref"], path=str(dest), unzip=True, quiet=False)
    elif kind == "kaggle_competition":
        _kaggle_api().competition_download_files(cfg["ref"], path=str(dest), quiet=False)
        _unzip_all(dest)
    elif kind == "github":
        if not any(dest.iterdir()):
            subprocess.run(["git", "clone", "--depth", "1", cfg["ref"], str(dest)], check=True)
    else:
        raise ValueError(f"unknown source kind {kind!r}")
    (dest / DONE_MARKER).write_text("ok", encoding="utf-8")
    return dest / sub if sub else dest

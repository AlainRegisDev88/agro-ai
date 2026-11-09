"""Prepare a flat GitHub release folder from models/ for the Android app.

The app downloads https://github.com/<owner>/<repo>/releases/latest/download/<basename>:
manifest.json first, then each files[].path reduced to its basename, checking sha256.
Release assets cannot contain "/", so every file is copied flat under its basename.

Only the newest version of each model is published (that is the one the app picks,
and older versions share the same basenames). Their manifest entries are written
unchanged; older entries are left out.

    python -m agro_ai.export.publish --models models --out dist/release

Prints the `gh release create` command; it does not run it.
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

from .manifest import SCHEMA_VERSION, sha256


def newest_entries(models: list[dict]) -> list[dict]:
    newest: dict[str, dict] = {}
    for m in models:
        if m["name"] not in newest or m["version"] > newest[m["name"]]["version"]:
            newest[m["name"]] = m
    return [newest[n] for n in sorted(newest)]


def release_files(models_root: Path, entries: list[dict]) -> dict[str, Path]:
    """basename -> source path for every file the release needs; checks names and hashes."""
    out: dict[str, Path] = {}

    def add(rel: str, expected_sha: str | None) -> None:
        name = PurePosixPath(rel).name
        src = models_root / rel
        if name in out:
            raise SystemExit(f"two release files share the basename {name!r}: {out[name]} and {src}")
        if not src.is_file():
            raise SystemExit(f"missing file listed in manifest: {src}")
        if expected_sha is not None and sha256(src) != expected_sha:
            raise SystemExit(f"sha256 mismatch for {src}: manifest has {expected_sha}, file is {sha256(src)}")
        out[name] = src

    for m in entries:
        for f in m["files"]:
            add(f["path"], f["sha256"])
        model_dir = PurePosixPath(m["files"][0]["path"]).parent
        add((model_dir / f"{m['name']}_labels.txt").as_posix(), None)
    return out


def publish(models_root: Path, out_dir: Path) -> list[Path]:
    models_root, out_dir = Path(models_root), Path(out_dir)
    data = json.loads((models_root / "manifest.json").read_text(encoding="utf-8"))
    if data.get("schema") != SCHEMA_VERSION:
        raise SystemExit(f"unsupported manifest schema {data.get('schema')!r} (expected {SCHEMA_VERSION})")
    entries = newest_entries(data["models"])
    if not entries:
        raise SystemExit(f"no models in {models_root / 'manifest.json'}")
    files = release_files(models_root, entries)
    if "manifest.json" in files:
        raise SystemExit("a model file is named manifest.json")

    out_dir.mkdir(parents=True, exist_ok=True)
    written = []
    for name, src in files.items():
        shutil.copy2(src, out_dir / name)
        written.append(out_dir / name)
    manifest = out_dir / "manifest.json"
    manifest.write_text(json.dumps({"schema": data["schema"], "models": entries}, indent=2), encoding="utf-8")
    written.append(manifest)
    return written


def gh_command(files: list[Path], tag: str) -> str:
    assets = " ".join(f'"{p.as_posix()}"' for p in files)
    return f'gh release create {tag} --title "Models {tag}" --notes "Model release {tag}" {assets}'


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--models", default="models", help="models root containing manifest.json")
    p.add_argument("--out", default="dist/release", help="flat output folder for the release assets")
    p.add_argument("--tag", default=f"models-{datetime.now(timezone.utc):%Y%m%d}", help="release tag")
    args = p.parse_args(argv)
    written = publish(Path(args.models), Path(args.out))
    for path in written:
        print(f"[publish] {path}")
    print("\nTo publish, run:\n")
    print(gh_command(written, args.tag))
    return 0


if __name__ == "__main__":
    sys.exit(main())

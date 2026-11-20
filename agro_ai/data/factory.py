"""Build per-crop training sets from every configured dataset.

    python dataset_factory.py --crops cassava,maize
    python dataset_factory.py --inspect fieldplant
    python dataset_factory.py --list-sources

For each requested crop this writes, under <data_dir>/processed/<crop>/:

    images/<condition>/<id>.jpg   224x224 RGB, center-cropped square
    manifest.csv                  path,label,source,domain,split,phash,original
    labels.txt                    one canonical label per line, sorted
    report.json                   counts, rejects, duplicates, unmapped labels, warnings

Every crop also gets a "<crop>__unknown" class: images from sources with a
"negatives" cap in sources.yaml that are NOT that crop (other crops' leaves,
weeds, scenes, objects). Turn it off with --no-negatives.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import random
import sys
from collections import Counter, defaultdict
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from ..paths import data_dir, processed_dir
from .adapters import ADAPTERS, Sample
from .images import RejectedImage, phash, prepare, save_jpeg
from .labels import SEP, ClassMap, crop_of, normalize
from .sources import load_sources, resolve

MIN_IMAGES_PER_CLASS = 150
NONE_CROP = "none"          # class_map crop for negatives-only images; never an expert
UNKNOWN = "unknown"         # condition name of every crop's "not my crop's leaf" class
MANIFEST_FIELDS = ["path", "label", "source", "domain", "split", "phash", "original"]


@dataclass
class Record:
    path: str          # relative to the crop directory
    label: str
    source: str
    domain: str
    priority: int
    phash: str
    original: str
    split: str = ""


def _sample_id(sample: Sample) -> str:
    key = f"{sample.source}|{sample.path.as_posix()}|{sample.box}"
    return hashlib.sha1(key.encode("utf-8")).hexdigest()[:20]


def _process_one(task: tuple[Sample, str, str, int]) -> tuple[Record | None, str | None]:
    sample, label, crop_dir, priority = task
    rel = f"images/{label.split(SEP, 1)[1]}/{_sample_id(sample)}.jpg"
    out = Path(crop_dir) / rel
    try:
        if out.exists():
            with Image.open(out) as img:
                h = phash(img)
        else:
            img = prepare(sample.path, sample.box)
            h = phash(img)
            save_jpeg(img, out)
    except RejectedImage as exc:
        return None, exc.reason
    except OSError:
        return None, "corrupt"
    return Record(rel, label, sample.source, sample.domain, priority, h, str(sample.path)), None


def dedupe(records: list[Record]) -> tuple[list[Record], int, int]:
    """Keep one record per perceptual hash: field over lab, then lower priority number.

    Returns (kept, duplicates_removed, label_conflicts). A conflict is the same
    image carrying two different labels; the preferred record wins.
    """
    ordered = sorted(records, key=lambda r: (r.domain != "field", r.priority, r.source, r.path))
    seen: dict[str, Record] = {}
    dupes = conflicts = 0
    for r in ordered:
        kept = seen.get(r.phash)
        if kept is None:
            seen[r.phash] = r
            continue
        dupes += 1
        if kept.label != r.label:
            conflicts += 1
    return list(seen.values()), dupes, conflicts


def cap_per_class(records: list[Record], cap: int, seed: int) -> list[Record]:
    rng = random.Random(seed)
    by_label: dict[str, list[Record]] = defaultdict(list)
    for r in records:
        by_label[r.label].append(r)
    out = []
    for label in sorted(by_label):
        items = sorted(by_label[label], key=lambda r: r.path)
        rng.shuffle(items)
        items.sort(key=lambda r: r.domain != "field")  # stable: field first, shuffled within domain
        out.extend(items[:cap])
    return out


def assign_splits(records: list[Record], val_frac: float, test_frac: float, seed: int) -> dict[str, str]:
    """Stratified split per label. When the crop has any field images, the test
    split is drawn from field images only (at most half of each label's field
    images), so the reported accuracy reflects real-world photos.

    Returns {label: domain the test split for that label came from}.
    """
    rng = random.Random(seed)
    crop_has_field = any(r.domain == "field" for r in records)
    by_label: dict[str, list[Record]] = defaultdict(list)
    for r in records:
        by_label[r.label].append(r)

    test_domain: dict[str, str] = {}
    for label in sorted(by_label):
        items = sorted(by_label[label], key=lambda r: r.path)
        rng.shuffle(items)
        n = len(items)
        n_test = round(n * test_frac)
        n_val = round(n * val_frac)
        if n >= 3:
            n_test, n_val = max(1, n_test), max(1, n_val)

        field = [r for r in items if r.domain == "field"]
        if crop_has_field and field:
            n_test = min(n_test, len(field) // 2) if len(field) > 1 else 0
            test = field[:n_test]
            test_domain[label] = "field"
        else:
            test = items[:n_test]
            test_domain[label] = "lab"
        test_ids = {id(r) for r in test}
        rest = [r for r in items if id(r) not in test_ids]
        for r in test:
            r.split = "test"
        for i, r in enumerate(rest):
            r.split = "val" if i < n_val else "train"
    return test_domain


def write_outputs(crop: str, crop_dir: Path, records: list[Record], report: dict) -> None:
    crop_dir.mkdir(parents=True, exist_ok=True)
    records = sorted(records, key=lambda r: (r.split, r.label, r.path))
    with (crop_dir / "manifest.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=MANIFEST_FIELDS)
        w.writeheader()
        for r in records:
            w.writerow({k: getattr(r, k) for k in MANIFEST_FIELDS})
    labels = sorted({r.label for r in records})
    (crop_dir / "labels.txt").write_text("\n".join(labels) + "\n", encoding="utf-8")
    (crop_dir / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True), encoding="utf-8")


def _crops_of_label_set(cmap: ClassMap, label_set: str) -> set[str]:
    return {crop_of(v) for v in cmap.tables.get(label_set, {}).values() if v}


def select_sources(sources: dict[str, dict], cmap: ClassMap, crops: set[str] | None,
                   names: list[str] | None) -> list[dict]:
    if names:
        unknown = set(names) - sources.keys()
        if unknown:
            raise SystemExit(f"unknown sources: {sorted(unknown)}; see --list-sources")
        chosen = [sources[n] for n in names]
    else:
        chosen = [s for s in sources.values() if s["enabled"]]
    if crops is not None:
        chosen = [s for s in chosen if _crops_of_label_set(cmap, s["label_set"]) & crops]
    return chosen


def select_negative_sources(sources: dict[str, dict], chosen: list[dict], names: list[str] | None) -> list[dict]:
    """Enabled sources with a negatives cap that aren't already chosen for their own crops.
    With --sources, only the named ones are considered."""
    taken = {s["name"] for s in chosen}
    pool = [sources[n] for n in names] if names else [s for s in sources.values() if s["enabled"]]
    return [s for s in pool if s.get("negatives") and s["name"] not in taken]


def sample_negatives(candidates: list[tuple[Sample, str]], n: int, rng: random.Random) -> list[Sample]:
    """Up to n samples spread evenly over the candidates' canonical labels (round robin)."""
    by_label: dict[str, list[Sample]] = defaultdict(list)
    for sample, label in candidates:
        by_label[label].append(sample)
    queues = []
    for label in sorted(by_label):
        items = sorted(by_label[label], key=lambda smp: smp.path.as_posix())
        rng.shuffle(items)
        queues.append(items)
    out: list[Sample] = []
    while len(out) < n and queues:
        for q in list(queues):
            if len(out) >= n:
                break
            out.append(q.pop())
            if not q:
                queues.remove(q)
    return out


def run(crops: list[str] | None, source_names: list[str] | None = None, data_root: Path | None = None,
        workers: int | None = None, limit: int | None = None, max_per_class: int | None = None,
        val_frac: float = 0.1, test_frac: float = 0.1, seed: int = 42, download: bool = True,
        sources_path: Path | None = None, class_map_path: Path | None = None,
        negatives: bool = True) -> dict[str, dict]:
    data_root = data_root or data_dir()
    sources = load_sources(sources_path)
    cmap = ClassMap.load(class_map_path)
    crop_set = set(crops) if crops else None
    if crop_set and NONE_CROP in crop_set:
        raise SystemExit(f"'{NONE_CROP}' is reserved for negatives-only images, not a crop")
    chosen = select_sources(sources, cmap, crop_set, source_names)
    if not chosen:
        raise SystemExit("no source produces the requested crops; check class_map.yaml")
    negatives_only = select_negative_sources(sources, chosen, source_names) if negatives else []

    tasks: list[tuple[Sample, str, str, int]] = []
    per_source: dict[str, dict] = {}
    skipped: dict[str, str] = {}
    # source name -> (cfg, every mapped sample of any crop) for building "<crop>__unknown"
    negative_pools: dict[str, tuple[dict, list[tuple[Sample, str]]]] = {}
    out_root = processed_dir(data_root)
    for cfg in chosen + negatives_only:
        for_negatives_only = cfg in negatives_only
        try:
            # A negatives-only source is a bonus: use it if present, never download it.
            root = resolve(cfg, data_root, download=download and not for_negatives_only)
        except FileNotFoundError as exc:
            # With --no-download (e.g. on Kaggle) use whatever is attached; a source the
            # user named explicitly must exist.
            if not for_negatives_only and (download or source_names):
                raise
            skipped[cfg["name"]] = str(exc)
            note = " (optional, only used for the unknown class)" if for_negatives_only else ""
            print(f"[skip] {exc}{note}", flush=True)
            continue
        adapter = ADAPTERS[cfg["adapter"]]
        unmapped: Counter = Counter()
        dropped: Counter = Counter()
        mapped: list[tuple[Sample, str]] = []
        everything: list[tuple[Sample, str]] = []
        for sample in adapter(root, cfg):
            label, known = cmap.lookup(cfg["label_set"], sample.raw_label)
            if not known:
                unmapped[sample.raw_label] += 1
            elif label is None:
                dropped[sample.raw_label] += 1
            else:
                everything.append((sample, label))
                crop = crop_of(label)
                if crop != NONE_CROP and (crop_set is None or crop in crop_set):
                    mapped.append((sample, label))
        if limit and len(mapped) > limit:
            random.Random(seed).shuffle(mapped)
            mapped = mapped[:limit]
        if negatives and cfg.get("negatives") and everything:
            negative_pools[cfg["name"]] = (cfg, everything)
        per_source[cfg["name"]] = {"mapped": len(mapped), "unmapped": dict(unmapped), "dropped": dict(dropped)}
        role = " (negatives only)" if for_negatives_only else ""
        print(f"[source] {cfg['name']}{role}: {len(mapped)} samples, {sum(unmapped.values())} unmapped", flush=True)
        if unmapped:
            print(f"         unmapped labels: {', '.join(sorted(unmapped))[:300]}", flush=True)
        for sample, label in mapped:
            tasks.append((sample, label, str(out_root / crop_of(label)), int(cfg["priority"])))

    if not tasks:
        raise SystemExit("none of the sources for these crops is available:\n  " + "\n  ".join(skipped.values()))

    targets = sorted(crop_set if crop_set is not None else {crop_of(t[1]) for t in tasks})
    for name, (cfg, everything) in sorted(negative_pools.items()):
        given = {}
        for crop in targets:
            cap = int(cfg["negatives"]) if not limit else min(int(cfg["negatives"]), limit)
            candidates = [(smp, lbl) for smp, lbl in everything if crop_of(lbl) != crop]
            rng = random.Random(f"{seed}|{crop}|{name}")
            picked = sample_negatives(candidates, cap, rng)
            for sample in picked:
                tasks.append((sample, f"{crop}{SEP}{UNKNOWN}", str(out_root / crop), int(cfg["priority"])))
            if picked:
                given[crop] = len(picked)
        per_source[name]["negatives"] = given
        if given:
            print(f"[negatives] {name} -> " + ", ".join(f"{c}__{UNKNOWN}: {n}" for c, n in given.items()), flush=True)
    print(f"[process] {len(tasks)} images with {workers or os.cpu_count()} workers", flush=True)
    records_by_crop: dict[str, list[Record]] = defaultdict(list)
    rejects: dict[str, Counter] = defaultdict(Counter)
    if workers == 1:
        results = map(_process_one, tasks)
    else:
        pool = ProcessPoolExecutor(max_workers=workers)
        results = pool.map(_process_one, tasks, chunksize=64)
    try:
        for i, (task, (record, reason)) in enumerate(zip(tasks, results), 1):
            crop = crop_of(task[1])
            if record is None:
                rejects[crop][reason] += 1
            else:
                records_by_crop[crop].append(record)
            if i % 5000 == 0:
                print(f"          {i}/{len(tasks)}", flush=True)
    finally:
        if workers != 1:
            pool.shutdown()

    reports = {}
    for crop in sorted(records_by_crop):
        records, dupes, conflicts = dedupe(records_by_crop[crop])
        if max_per_class:
            records = cap_per_class(records, max_per_class, seed)
        test_domain = assign_splits(records, val_frac, test_frac, seed)
        counts: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
        for r in records:
            counts[r.label][r.split] += 1
            counts[r.label][f"domain_{r.domain}"] += 1
        warnings = []
        for label, c in sorted(counts.items()):
            total = c["train"] + c["val"] + c["test"]
            if total < MIN_IMAGES_PER_CLASS:
                warnings.append(f"{label}: only {total} images (< {MIN_IMAGES_PER_CLASS})")
            if test_domain.get(label) == "lab":
                warnings.append(f"{label}: no field images, test accuracy is lab-only")
        report = {
            "crop": crop,
            "total": len(records),
            "classes": {k: dict(v) for k, v in counts.items()},
            "test_domain": test_domain,
            "rejected": dict(rejects[crop]),
            "duplicates_removed": dupes,
            "label_conflicts": conflicts,
            "sources": {name: info for name, info in per_source.items()},
            "sources_skipped": skipped,
            "warnings": warnings,
            "settings": {"val_frac": val_frac, "test_frac": test_frac, "seed": seed,
                         "limit": limit, "max_per_class": max_per_class},
        }
        write_outputs(crop, processed_dir(data_root) / crop, records, report)
        reports[crop] = report
        print(f"[crop] {crop}: {len(records)} images, {len(counts)} classes, "
              f"{dupes} duplicates removed -> {processed_dir(data_root) / crop}", flush=True)
        for w in warnings:
            print(f"       warning: {w}", flush=True)
    return reports


def inspect(source_name: str, data_root: Path | None = None, download: bool = True,
            sources_path: Path | None = None, class_map_path: Path | None = None) -> Counter:
    sources = load_sources(sources_path)
    if source_name not in sources:
        raise SystemExit(f"unknown source {source_name!r}; see --list-sources")
    cfg = sources[source_name]
    cmap = ClassMap.load(class_map_path)
    root = resolve(cfg, data_root or data_dir(), download=download)
    counts: Counter = Counter(s.raw_label for s in ADAPTERS[cfg["adapter"]](root, cfg))
    print(f"{source_name} ({cfg['adapter']}, label_set={cfg['label_set']}) at {root}")
    print(f"{'count':>7}  {'raw label':<45} {'normalized key':<45} canonical")
    for raw, n in sorted(counts.items()):
        label, known = cmap.lookup(cfg["label_set"], raw)
        canon = label if known and label else ("(dropped)" if known else "** UNMAPPED **")
        print(f"{n:>7}  {raw[:45]:<45} {normalize(raw)[:45]:<45} {canon}")
    return counts


def list_sources() -> None:
    cmap = ClassMap.load()
    for name, cfg in load_sources().items():
        crops = ", ".join(sorted(_crops_of_label_set(cmap, cfg["label_set"]) - {NONE_CROP}))
        crops = crops or ("(unknown class only)" if cfg.get("negatives") else "(no mapped labels yet)")
        flag = "" if cfg["enabled"] else "  [disabled]"
        if cfg.get("negatives"):
            flag += f"  [unknown: {cfg['negatives']}/crop]"
        print(f"{name:<26} {cfg['domain']:<6} {cfg['kind']:<19} {crops}{flag}")


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--crops", help="comma-separated crops (e.g. cassava,maize) or 'all'")
    p.add_argument("--sources", help="comma-separated source names (default: all enabled sources)")
    p.add_argument("--data-dir", help="defaults to $AGRO_DATA_DIR, /kaggle/working/agro-data, or ../agro-data beside the repo")
    p.add_argument("--workers", type=int, default=None, help="processes for image preprocessing (1 = serial)")
    p.add_argument("--limit", type=int, help="max samples per source, for smoke tests")
    p.add_argument("--max-per-class", type=int, help="cap each class after dedupe (field images kept first)")
    p.add_argument("--val-frac", type=float, default=0.1)
    p.add_argument("--test-frac", type=float, default=0.1)
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--no-download", action="store_true", help="fail instead of downloading missing sources")
    p.add_argument("--no-negatives", action="store_true", help="don't add the <crop>__unknown class")
    p.add_argument("--inspect", metavar="SOURCE", help="print raw labels of one source and how they map")
    p.add_argument("--list-sources", action="store_true")
    args = p.parse_args(argv)

    if args.list_sources:
        list_sources()
        return 0
    root = Path(args.data_dir) if args.data_dir else None
    if args.inspect:
        inspect(args.inspect, root, download=not args.no_download)
        return 0
    if not args.crops:
        p.error("--crops is required (or use --inspect / --list-sources)")
    crops = None if args.crops.strip().lower() == "all" else [c.strip() for c in args.crops.split(",") if c.strip()]
    sources = [s.strip() for s in args.sources.split(",")] if args.sources else None
    run(crops, sources, root, workers=args.workers, limit=args.limit, max_per_class=args.max_per_class,
        val_frac=args.val_frac, test_frac=args.test_frac, seed=args.seed, download=not args.no_download,
        negatives=not args.no_negatives)
    return 0


if __name__ == "__main__":
    sys.exit(main())

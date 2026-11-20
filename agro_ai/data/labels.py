"""Raw dataset labels -> canonical "<crop>__<condition>" labels."""

from __future__ import annotations

import re
from pathlib import Path

import yaml

from ..paths import CONFIG_DIR

_NON_ALNUM = re.compile(r"[^a-z0-9]+")
SEP = "__"


def normalize(raw: str) -> str:
    return _NON_ALNUM.sub("_", raw.lower()).strip("_")


def crop_of(canonical: str) -> str:
    return canonical.split(SEP, 1)[0]


class ClassMap:
    def __init__(self, tables: dict[str, dict[str, str | None]]):
        self.tables = {name: {normalize(str(k)): v for k, v in (table or {}).items()}
                       for name, table in tables.items()}
        for name, table in self.tables.items():
            for raw, canon in table.items():
                if canon is not None and SEP not in canon:
                    raise ValueError(f"class_map[{name}][{raw}] = {canon!r} must look like 'crop__condition'")

    @classmethod
    def load(cls, path: Path | None = None) -> "ClassMap":
        path = path or CONFIG_DIR / "class_map.yaml"
        return cls(yaml.safe_load(path.read_text(encoding="utf-8")) or {})

    def lookup(self, label_set: str, raw: str) -> tuple[str | None, bool]:
        """Return (canonical or None, known). known=False means the label is missing from the table."""
        table = self.tables.get(label_set, {})
        key = normalize(raw)
        if key in table:
            return table[key], True
        return None, False

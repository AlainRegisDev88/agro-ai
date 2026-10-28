"""Read factory manifests and turn them into tf.data pipelines."""

from __future__ import annotations

import csv
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image


@dataclass(frozen=True)
class Item:
    path: str
    label: str
    split: str
    domain: str


def read_manifests(dirs: list[Path]) -> tuple[list[Item], list[str]]:
    """Items from one or more processed crop directories, and the sorted label union."""
    items: list[Item] = []
    for d in dirs:
        d = Path(d)
        with (d / "manifest.csv").open(newline="", encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                items.append(Item(str(d / row["path"]), row["label"], row["split"], row["domain"]))
    labels = sorted({i.label for i in items})
    return items, labels


def split(items: list[Item], name: str) -> list[Item]:
    return [i for i in items if i.split == name]


def load_uint8(path: str, size: int = 224) -> np.ndarray:
    with Image.open(path) as img:
        img = img.convert("RGB")
        if img.size != (size, size):
            img = img.resize((size, size), Image.Resampling.BILINEAR)
        return np.asarray(img, dtype=np.uint8)


def make_dataset(items: list[Item], labels: list[str], batch: int, training: bool,
                 label_smoothing: float = 0.0, seed: int = 42):
    import tensorflow as tf

    from .augment import augment

    index = {l: i for i, l in enumerate(labels)}
    paths = tf.constant([i.path for i in items])
    ys = tf.constant([index[i.label] for i in items], dtype=tf.int32)
    k = len(labels)

    def load(path, y):
        img = tf.io.decode_jpeg(tf.io.read_file(path), channels=3)
        img = tf.image.resize(img, [224, 224])
        img.set_shape([224, 224, 3])
        return tf.cast(img, tf.float32), y

    def one_hot(img, y):
        target = tf.one_hot(y, k)
        if label_smoothing:
            target = target * (1.0 - label_smoothing) + label_smoothing / k
        return img, target

    ds = tf.data.Dataset.from_tensor_slices((paths, ys))
    if training:
        ds = ds.shuffle(len(items), seed=seed, reshuffle_each_iteration=True)
    ds = ds.map(load, num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        ds = ds.map(augment, num_parallel_calls=tf.data.AUTOTUNE)
    ds = ds.map(one_hot, num_parallel_calls=tf.data.AUTOTUNE)
    return ds.batch(batch).prefetch(tf.data.AUTOTUNE)


def class_weights(items: list[Item], labels: list[str]) -> dict[int, float]:
    counts = np.array([sum(1 for i in items if i.label == l) for l in labels], dtype=float)
    counts = np.maximum(counts, 1.0)
    weights = counts.sum() / (len(labels) * counts)
    return {i: float(w) for i, w in enumerate(weights)}

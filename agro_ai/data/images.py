"""Image validation, square center-crop, resize, and perceptual hashing.

Kept free of TensorFlow so it runs on any Python that has Pillow and NumPy.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps, UnidentifiedImageError

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}
TARGET_SIZE = 224
MIN_SIDE = 96
JPEG_QUALITY = 95
BOX_PAD = 0.10

# Large field photos are legitimate; this only guards against decompression bombs.
Image.MAX_IMAGE_PIXELS = 200_000_000


class RejectedImage(Exception):
    """Raised with a short machine-readable reason (bad_ext, corrupt, too_small, bad_box)."""

    def __init__(self, reason: str, detail: str = ""):
        super().__init__(f"{reason}: {detail}" if detail else reason)
        self.reason = reason


@dataclass(frozen=True)
class Box:
    """Bounding box in the original image, (x0, y0) top-left, (x1, y1) bottom-right.

    Pixel coordinates by default; with normalized=True they are fractions of the
    image size (YOLO labels), resolved once the image is opened.
    """

    x0: float
    y0: float
    x1: float
    y1: float
    normalized: bool = False

    def to_pixels(self, width: int, height: int) -> "Box":
        if not self.normalized:
            return self
        return Box(self.x0 * width, self.y0 * height, self.x1 * width, self.y1 * height)


def is_image_file(path: Path) -> bool:
    return path.suffix.lower() in IMAGE_EXTS


def load_rgb(path: Path) -> Image.Image:
    """Open, verify, apply EXIF rotation, and convert to RGB."""
    if not is_image_file(path):
        raise RejectedImage("bad_ext", path.suffix)
    try:
        with Image.open(path) as probe:
            probe.verify()
        with Image.open(path) as img:
            img = ImageOps.exif_transpose(img)
            return img.convert("RGB")
    except (UnidentifiedImageError, OSError, SyntaxError, ValueError, Image.DecompressionBombError) as exc:
        raise RejectedImage("corrupt", str(exc)) from exc


def crop_box(img: Image.Image, box: Box, pad: float = BOX_PAD) -> Image.Image:
    w, h = img.size
    box = box.to_pixels(w, h)
    bw, bh = box.x1 - box.x0, box.y1 - box.y0
    if bw <= 0 or bh <= 0:
        raise RejectedImage("bad_box", f"{box}")
    x0 = max(0, int(round(box.x0 - bw * pad)))
    y0 = max(0, int(round(box.y0 - bh * pad)))
    x1 = min(w, int(round(box.x1 + bw * pad)))
    y1 = min(h, int(round(box.y1 + bh * pad)))
    if x1 <= x0 or y1 <= y0:
        raise RejectedImage("bad_box", f"{box} outside {w}x{h}")
    return img.crop((x0, y0, x1, y1))


def center_crop_square(img: Image.Image) -> Image.Image:
    w, h = img.size
    side = min(w, h)
    left = (w - side) // 2
    top = (h - side) // 2
    return img.crop((left, top, left + side, top + side))


def prepare(path: Path, box: Box | None = None, size: int = TARGET_SIZE, min_side: int = MIN_SIDE) -> Image.Image:
    """Full preprocessing for one sample: load, optional box crop, square crop, resize."""
    img = load_rgb(path)
    if box is not None:
        img = crop_box(img, box)
    if min(img.size) < min_side:
        raise RejectedImage("too_small", f"{img.size[0]}x{img.size[1]}")
    img = center_crop_square(img)
    if img.size[0] != size:
        img = img.resize((size, size), Image.Resampling.LANCZOS)
    return img


def save_jpeg(img: Image.Image, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp")
    img.save(tmp, format="JPEG", quality=JPEG_QUALITY)
    tmp.replace(out)


def _dct_matrix(n: int) -> np.ndarray:
    k = np.arange(n)[:, None]
    i = np.arange(n)[None, :]
    m = np.cos(np.pi * (2 * i + 1) * k / (2 * n)) * np.sqrt(2.0 / n)
    m[0, :] = np.sqrt(1.0 / n)
    return m


_DCT32 = _dct_matrix(32)


def phash(img: Image.Image) -> str:
    """64-bit DCT perceptual hash as 16 hex chars (same idea as imagehash.phash)."""
    gray = img.convert("L").resize((32, 32), Image.Resampling.LANCZOS)
    pixels = np.asarray(gray, dtype=np.float64)
    dct = _DCT32 @ pixels @ _DCT32.T
    low = dct[:8, :8]
    bits = (low > np.median(low)).flatten()
    value = 0
    for bit in bits:
        value = (value << 1) | int(bit)
    return f"{value:016x}"


def hamming(a: str, b: str) -> int:
    return bin(int(a, 16) ^ int(b, 16)).count("1")

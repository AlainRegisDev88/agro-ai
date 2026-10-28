from pathlib import Path

import numpy as np
import pytest
from PIL import Image


def make_image(path: Path, seed: int, size: tuple[int, int] = (300, 260)) -> Path:
    """Random-texture RGB image; different seeds give clearly different perceptual hashes."""
    rng = np.random.default_rng(seed)
    small = rng.integers(0, 256, size=(size[1] // 20 + 1, size[0] // 20 + 1, 3), dtype=np.uint8)
    img = Image.fromarray(small).resize(size, Image.Resampling.BICUBIC)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    return path


@pytest.fixture
def image_factory(tmp_path):
    def _make(rel: str, seed: int, size=(300, 260)) -> Path:
        return make_image(tmp_path / rel, seed, size)
    return _make

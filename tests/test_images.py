import pytest
from PIL import Image

from agro_ai.data.images import Box, RejectedImage, center_crop_square, crop_box, hamming, phash, prepare


def test_prepare_outputs_square_224(image_factory):
    img = prepare(image_factory("a.jpg", 1, size=(640, 360)))
    assert img.size == (224, 224)
    assert img.mode == "RGB"


def test_center_crop_keeps_middle():
    img = Image.new("RGB", (300, 100))
    img.paste((255, 0, 0), (100, 0, 200, 100))  # red middle third
    out = center_crop_square(img)
    assert out.size == (100, 100)
    assert out.getpixel((50, 50)) == (255, 0, 0)
    assert out.getpixel((0, 0)) == (255, 0, 0)


def test_rejects_wrong_extension(tmp_path):
    p = tmp_path / "notes.txt"
    p.write_text("hello")
    with pytest.raises(RejectedImage) as exc:
        prepare(p)
    assert exc.value.reason == "bad_ext"


def test_rejects_corrupt_file(tmp_path):
    p = tmp_path / "broken.jpg"
    p.write_bytes(b"\xff\xd8\xff\xe0 not really a jpeg")
    with pytest.raises(RejectedImage) as exc:
        prepare(p)
    assert exc.value.reason == "corrupt"


def test_rejects_tiny_image(image_factory):
    with pytest.raises(RejectedImage) as exc:
        prepare(image_factory("tiny.png", 2, size=(80, 200)))
    assert exc.value.reason == "too_small"


def test_normalized_box_crop():
    img = Image.new("RGB", (400, 200))
    out = crop_box(img, Box(0.25, 0.25, 0.75, 0.75, normalized=True), pad=0)
    assert out.size == (200, 100)


def test_box_outside_image_rejected():
    img = Image.new("RGB", (100, 100))
    with pytest.raises(RejectedImage):
        crop_box(img, Box(150, 150, 120, 180))


def test_phash_stable_under_resize_and_distinct_across_images(image_factory):
    a = Image.open(image_factory("a.png", 10))
    b = Image.open(image_factory("b.png", 11))
    assert hamming(phash(a), phash(a.resize((224, 224)))) <= 4
    assert hamming(phash(a), phash(b)) > 10

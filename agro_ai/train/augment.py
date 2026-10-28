"""Field-style augmentation in tf.data (kept out of the model so exports stay clean).

Lab images (flat background, even light) are pushed toward what a phone sees
outdoors: uneven light, cast shadows, slight blur, off-center framing.
Images are float32 in [0, 255].
"""

from __future__ import annotations

import tensorflow as tf


def _random_shadow(img: tf.Tensor) -> tf.Tensor:
    """Darken one side of a random line across the image, with a soft edge."""
    size = tf.cast(tf.shape(img)[0], tf.float32)
    coords = tf.range(size) / size
    yy, xx = tf.meshgrid(coords, coords, indexing="ij")
    angle = tf.random.uniform([], 0.0, 6.2832)
    offset = tf.random.uniform([], -0.3, 0.3)
    dist = (xx - 0.5) * tf.cos(angle) + (yy - 0.5) * tf.sin(angle) - offset
    mask = tf.sigmoid(dist * 20.0)
    strength = tf.random.uniform([], 0.3, 0.6)
    return img * (1.0 - strength * mask)[..., None]


def _blur(img: tf.Tensor) -> tf.Tensor:
    return tf.nn.avg_pool2d(img[None], ksize=3, strides=1, padding="SAME")[0]


def augment(img: tf.Tensor, label: tf.Tensor):
    size = tf.shape(img)[0]
    # framing: random crop 75-100% of the side, then back to full size
    scale = tf.random.uniform([], 0.75, 1.0)
    crop = tf.cast(tf.cast(size, tf.float32) * scale, tf.int32)
    img = tf.image.random_crop(img, tf.stack([crop, crop, 3]))
    img = tf.image.resize(img, tf.stack([size, size]))

    img = tf.image.random_flip_left_right(img)
    img = tf.image.random_flip_up_down(img)
    img = tf.image.rot90(img, k=tf.random.uniform([], 0, 4, dtype=tf.int32))

    img = tf.image.random_brightness(img, 40.0)
    img = tf.image.random_contrast(img, 0.7, 1.3)
    img = tf.image.random_saturation(img, 0.75, 1.25)
    img = tf.image.random_hue(img, 0.03)

    img = tf.cond(tf.random.uniform([]) < 0.3, lambda: _random_shadow(img), lambda: img)
    img = tf.cond(tf.random.uniform([]) < 0.2, lambda: _blur(img), lambda: img)
    return tf.clip_by_value(img, 0.0, 255.0), label

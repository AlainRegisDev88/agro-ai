"""MobileNetV3-Large expert classifier with optional Coordinate Attention.

Input: float32 RGB images in [0, 255], 224x224. Rescaling is built into the
backbone (include_preprocessing=True), so the Android side feeds raw pixels.
"""

from __future__ import annotations

import tensorflow as tf
from tensorflow import keras

IMG_SIZE = 224


@keras.saving.register_keras_serializable(package="agro_ai")
class CoordinateAttention(keras.layers.Layer):
    """Coordinate Attention (Hou et al., CVPR 2021).

    Pools along height and width separately so the attention keeps position
    information, which helps the model focus on lesions instead of background.
    """

    def __init__(self, reduction: int = 32, **kwargs):
        super().__init__(**kwargs)
        self.reduction = reduction

    def build(self, input_shape):
        channels = int(input_shape[-1])
        mid = max(8, channels // self.reduction)
        self.conv_shared = keras.layers.Conv2D(mid, 1, use_bias=False, name="ca_shared")
        self.bn = keras.layers.BatchNormalization(name="ca_bn")
        self.conv_h = keras.layers.Conv2D(channels, 1, activation="sigmoid", name="ca_h")
        self.conv_w = keras.layers.Conv2D(channels, 1, activation="sigmoid", name="ca_w")
        h, w = int(input_shape[1]), int(input_shape[2])
        self._hw = (h, w)  # static sizes keep the TFLite graph free of dynamic shape ops
        self.conv_shared.build((None, h + w, 1, channels))
        self.bn.build((None, h + w, 1, mid))
        self.conv_h.build((None, h, 1, mid))
        self.conv_w.build((None, w, 1, mid))
        super().build(input_shape)

    def call(self, x, training=None):
        h, w = self._hw
        pool_h = tf.reduce_mean(x, axis=2, keepdims=True)                              # B,H,1,C
        pool_w = tf.transpose(tf.reduce_mean(x, axis=1, keepdims=True), [0, 2, 1, 3])  # B,W,1,C
        y = tf.concat([pool_h, pool_w], axis=1)
        y = self.bn(self.conv_shared(y), training=training)
        y = y * tf.nn.relu6(y + 3.0) / 6.0                                             # hard-swish
        y_h, y_w = tf.split(y, [h, w], axis=1)
        a_h = self.conv_h(y_h)                                                         # B,H,1,C
        a_w = tf.transpose(self.conv_w(y_w), [0, 2, 1, 3])                             # B,1,W,C
        return x * a_h * a_w

    def get_config(self):
        return {**super().get_config(), "reduction": self.reduction}


def find_backbone(model: keras.Model) -> keras.Model:
    for layer in model.layers:
        if isinstance(layer, keras.Model):
            return layer
    raise ValueError("model has no nested backbone")


def build_model(num_classes: int, coord_attention: bool = False, dropout: float = 0.2,
                weights: str | None = "imagenet") -> keras.Model:
    inputs = keras.Input((IMG_SIZE, IMG_SIZE, 3), name="image")
    backbone = keras.applications.MobileNetV3Large(
        input_shape=(IMG_SIZE, IMG_SIZE, 3), include_top=False, weights=weights,
        include_preprocessing=True, pooling=None)
    backbone.trainable = False
    x = backbone(inputs, training=False)
    if coord_attention:
        x = CoordinateAttention(name="coord_attention")(x)
    x = keras.layers.GlobalAveragePooling2D(name="gap")(x)
    x = keras.layers.Dropout(dropout, name="dropout")(x)
    # float32 output even under mixed precision, for stable softmax and clean TFLite export
    outputs = keras.layers.Dense(num_classes, activation="softmax", dtype="float32", name="probs")(x)
    return keras.Model(inputs, outputs, name="agro_expert")


def unfreeze_top(model: keras.Model, n_layers: int) -> None:
    """Make the last n backbone layers trainable; BatchNorm stays frozen for small-batch fine-tuning."""
    backbone = find_backbone(model)
    backbone.trainable = True
    cutoff = len(backbone.layers) - n_layers
    for i, layer in enumerate(backbone.layers):
        layer.trainable = i >= cutoff and not isinstance(layer, keras.layers.BatchNormalization)

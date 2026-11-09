package com.agroai.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure geometry and quantization helpers; unit-tested without Android. */
object ImageMath {
    const val INPUT_SIZE = 224

    /** Square crop centred in a w×h image: (left, top, side). Matches agro_ai/data/images.py. */
    fun centerSquare(width: Int, height: Int): Triple<Int, Int, Int> {
        val side = min(width, height)
        return Triple((width - side) / 2, (height - side) / 2, side)
    }

    /** Largest power-of-two subsample that keeps the short side at least [minSide]. */
    fun sampleSize(width: Int, height: Int, minSide: Int): Int {
        var sample = 1
        while (min(width, height) / (sample * 2) >= minSide) sample *= 2
        return sample
    }

    /** Real value -> quantized integer, as TFLite does: q = round(v / scale) + zeroPoint. */
    fun quantize(value: Float, scale: Float, zeroPoint: Int, qMin: Int, qMax: Int): Int {
        if (scale == 0f) return value.roundToInt().coerceIn(qMin, qMax)
        return ((value / scale).roundToInt() + zeroPoint).coerceIn(qMin, qMax)
    }
}

/**
 * Turns a photo into what the experts were trained on: EXIF-upright, centre-cropped
 * square, resized to 224×224 RGB with values 0-255 (normalization is inside the model).
 */
object Preprocessor {
    /** Decodes [uri] upright and centre-cropped to a square of at least 448px where possible. */
    fun loadSquare(context: Context, uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not an image" }

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageMath.sampleSize(bounds.outWidth, bounds.outHeight, ImageMath.INPUT_SIZE * 2)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Could not decode image")
        val orientation = resolver.openInputStream(uri)!!.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val upright = applyOrientation(decoded, orientation)
        val (left, top, side) = ImageMath.centerSquare(upright.width, upright.height)
        return Bitmap.createBitmap(upright, left, top, side, side)
    }

    /** Square -> 224×224 by repeated halving, then one bilinear step, to avoid aliasing. */
    fun toModelInput(square: Bitmap): Bitmap = downscale(square, ImageMath.INPUT_SIZE)

    fun saveJpeg(square: Bitmap, file: File, maxSide: Int = 720) {
        file.parentFile?.mkdirs()
        val out = if (square.width > maxSide) downscale(square, maxSide) else square
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    private fun downscale(src: Bitmap, target: Int): Bitmap {
        var current = src
        while (current.width / 2 >= target) {
            current = Bitmap.createScaledBitmap(current, current.width / 2, current.height / 2, true)
        }
        return if (current.width == target) current else Bitmap.createScaledBitmap(current, target, target, true)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

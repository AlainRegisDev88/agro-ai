package com.agroai.app.ml

import android.graphics.Bitmap
import com.agroai.app.BuildConfig
import com.agroai.app.data.content.ContentRepository
import com.agroai.app.data.history.Scan
import com.agroai.app.data.models.InstalledModel
import com.agroai.app.data.models.ModelRepository
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

interface Classifier : Closeable {
    val labels: List<String>

    /** [input] is 224×224. Returns probabilities in label order. */
    fun classify(input: Bitmap): FloatArray
}

/**
 * Runs one crop expert. Reads the input tensor's dtype and quantization from the model,
 * so both the INT8 (uint8 in) and FP16 (float32 in) exports work.
 */
class TfliteClassifier(file: File, override val labels: List<String>) : Classifier {
    private val interpreter: Interpreter

    init {
        val buffer = FileInputStream(file).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
        interpreter = Interpreter(buffer, Interpreter.Options().setNumThreads(4))
        val outputs = interpreter.getOutputTensor(0).shape().last()
        require(outputs == labels.size) { "Model has $outputs outputs but ${labels.size} labels" }
    }

    @Synchronized
    override fun classify(input: Bitmap): FloatArray {
        val size = ImageMath.INPUT_SIZE
        require(input.width == size && input.height == size)
        val pixels = IntArray(size * size).also { input.getPixels(it, 0, size, 0, 0, size, size) }

        val inTensor = interpreter.getInputTensor(0)
        val inType = inTensor.dataType()
        val q = inTensor.quantizationParams()
        val buffer = ByteBuffer.allocateDirect(size * size * 3 * inType.byteSize()).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            for (channel in intArrayOf((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) {
                val v = channel.toFloat()
                when (inType) {
                    DataType.FLOAT32 -> buffer.putFloat(v)
                    DataType.UINT8 -> buffer.put(ImageMath.quantize(v, q.scale, q.zeroPoint, 0, 255).toByte())
                    DataType.INT8 -> buffer.put(ImageMath.quantize(v, q.scale, q.zeroPoint, -128, 127).toByte())
                    else -> error("Unsupported input type $inType")
                }
            }
        }
        buffer.rewind()

        val outTensor = interpreter.getOutputTensor(0)
        val n = labels.size
        return when (outTensor.dataType()) {
            DataType.FLOAT32 -> {
                val out = Array(1) { FloatArray(n) }
                interpreter.run(buffer, out)
                out[0]
            }
            DataType.UINT8, DataType.INT8 -> {
                val signed = outTensor.dataType() == DataType.INT8
                val raw = ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder())
                interpreter.run(buffer, raw)
                raw.rewind()
                val oq = outTensor.quantizationParams()
                FloatArray(n) {
                    val b = raw.get().toInt()
                    val value = if (signed) b else b and 0xFF
                    (value - oq.zeroPoint) * oq.scale
                }
            }
            else -> error("Unsupported output type ${outTensor.dataType()}")
        }
    }

    override fun close() = interpreter.close()
}

/**
 * Stand-in used in debug builds when no expert is installed, so every screen can be tried
 * before a model is trained. Deterministic per photo; results are marked as demo.
 */
class FakeClassifier(override val labels: List<String>) : Classifier {
    override fun classify(input: Bitmap): FloatArray {
        var seed = 17L
        for (y in 0 until input.height step 16) for (x in 0 until input.width step 16) {
            seed = seed * 31 + input.getPixel(x, y)
        }
        val random = java.util.Random(seed)
        val logits = FloatArray(labels.size) { random.nextFloat() * 2f }
        logits[random.nextInt(labels.size)] += 3f + random.nextFloat() * 2f
        val exps = logits.map { exp(it.toDouble()) }
        val sum = exps.sum()
        return FloatArray(labels.size) { (exps[it] / sum).toFloat() }
    }

    override fun close() = Unit
}

/** Loads and caches one classifier per crop. */
class ClassifierProvider(private val models: ModelRepository, private val content: ContentRepository) {
    data class Loaded(val classifier: Classifier, val version: String)

    private var cached: Pair<InstalledModel, Loaded>? = null

    /** The installed expert, or a demo classifier in debug builds; null if neither. */
    @Synchronized
    fun get(crop: String): Loaded? {
        val installed = models.installed.value[crop]
        if (installed != null) {
            cached?.let { (meta, loaded) -> if (meta == installed) return loaded }
            cached?.second?.classifier?.close()
            val loaded = Loaded(TfliteClassifier(models.modelFile(crop), installed.labels), installed.versionLabel)
            cached = installed to loaded
            return loaded
        }
        if (BuildConfig.DEBUG) {
            val labels = content.crop(crop)?.labels ?: return null
            return Loaded(FakeClassifier(labels), Scan.DEMO)
        }
        return null
    }

    fun isAvailable(crop: String): Boolean = models.installed.value.containsKey(crop) || BuildConfig.DEBUG
}

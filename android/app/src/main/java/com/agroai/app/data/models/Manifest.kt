package com.agroai.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest

/** models/manifest.json as written by agro_ai/export/manifest.py (schema 1). */
@Serializable
data class ModelManifest(val schema: Int, val models: List<ManifestModel> = emptyList())

@Serializable
data class ManifestModel(
    val name: String,
    val version: Int,
    val crop: String,
    val labels: List<String>,
    @SerialName("display_names") val displayNames: Map<String, String> = emptyMap(),
    val files: List<ManifestFile>,
    val recommended: String,
    val metrics: ManifestMetrics = ManifestMetrics(),
    val created: String = "",
)

@Serializable
data class ManifestFile(
    val variant: String,
    val path: String,
    val bytes: Long,
    val sha256: String,
    @SerialName("input_dtype") val inputDtype: String = "",
)

@Serializable
data class ManifestMetrics(
    @SerialName("eval_split") val evalSplit: String? = null,
    @SerialName("keras_accuracy") val kerasAccuracy: Double? = null,
    @SerialName("int8_accuracy") val int8Accuracy: Double? = null,
    @SerialName("fp16_accuracy") val fp16Accuracy: Double? = null,
    @SerialName("int8_macro_f1") val int8MacroF1: Double? = null,
)

object Manifests {
    const val SCHEMA = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ModelManifest {
        val manifest = json.decodeFromString<ModelManifest>(text)
        require(manifest.schema == SCHEMA) { "Unsupported manifest schema ${manifest.schema}" }
        return manifest
    }

    /** Newest version of each crop's expert. */
    fun latest(manifest: ModelManifest, crop: String): ManifestModel? =
        manifest.models.filter { it.crop == crop }.maxByOrNull { it.version }

    fun recommendedFile(model: ManifestModel): ManifestFile =
        model.files.firstOrNull { it.variant == model.recommended } ?: model.files.first()

    /** GitHub release assets are flat, so "cassava/v1/cassava_int8.tflite" is served as "cassava_int8.tflite". */
    fun assetName(path: String): String = path.substringAfterLast('/')

    fun accuracy(model: ManifestModel, variant: String): Double? = when (variant) {
        "int8" -> model.metrics.int8Accuracy
        "fp16" -> model.metrics.fp16Accuracy
        else -> null
    } ?: model.metrics.kerasAccuracy
}

fun sha256Hex(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1 shl 16)
    while (true) {
        val n = input.read(buffer)
        if (n < 0) break
        digest.update(buffer, 0, n)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

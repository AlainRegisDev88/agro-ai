package com.agroai.app.data.content

import android.content.Context
import android.util.Log
import com.agroai.app.R
import com.agroai.app.ml.Diagnosis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Crop(
    val id: String,
    val name: String,
    val emoji: String,
    val group: String,
    val labels: List<String>,
)

@Serializable
data class Treatment(
    val cultural: List<String> = emptyList(),
    val organic: List<String> = emptyList(),
    val chemical: List<String> = emptyList(),
) {
    val isEmpty get() = cultural.isEmpty() && organic.isEmpty() && chemical.isEmpty()

    /** The first few steps across categories, for the result screen's "What to do" card. */
    fun highlights(max: Int = 3): List<String> = (cultural + organic + chemical).take(max)
}

@Serializable
data class DiseaseInfo(
    val crop: String,
    val name: String,
    val healthy: Boolean = false,
    val summary: String = "",
    val symptoms: List<String> = emptyList(),
    val cause: String = "",
    val treatment: Treatment = Treatment(),
    val prevention: List<String> = emptyList(),
    val getHelp: String = "",
    val reviewed: Boolean = false,
)

/** Offline crop catalogue and disease guidance bundled in assets/content. */
class ContentRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val crops: List<Crop> by lazy {
        json.decodeFromString<List<Crop>>(readAsset("content/crops.json") ?: "[]")
    }

    private val diseases: Map<String, DiseaseInfo> by lazy {
        val text = readAsset("content/diseases.json") ?: return@lazy emptyMap()
        runCatching { json.decodeFromString<Map<String, DiseaseInfo>>(text) }
            .onFailure { Log.e(TAG, "diseases.json is invalid", it) }
            .getOrDefault(emptyMap())
    }

    fun crop(id: String): Crop? = crops.firstOrNull { it.id == id }

    fun cropName(id: String): String = crop(id)?.name ?: id.replaceFirstChar { it.uppercase() }

    /** Guidance for a label; labels without written content still get a usable name. */
    fun disease(label: String): DiseaseInfo = diseases[label] ?: DiseaseInfo(
        crop = Diagnosis.cropOf(label),
        name = if (Diagnosis.isUnknown(label)) context.getString(R.string.label_not_recognized) else Diagnosis.displayName(label),
        healthy = Diagnosis.isHealthy(label),
    )

    fun name(label: String): String = disease(label).name

    private fun readAsset(path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private companion object {
        const val TAG = "ContentRepository"
    }
}

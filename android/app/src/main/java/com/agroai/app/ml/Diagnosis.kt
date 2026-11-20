package com.agroai.app.ml

import kotlinx.serialization.Serializable

enum class DiagnosisStatus { Healthy, Disease, Uncertain }

@Serializable
data class LabelScore(val label: String, val score: Float)

object Diagnosis {
    /**
     * Below this top probability we ask the farmer to retake the photo. Training uses
     * label smoothing 0.1, so even a sure answer tops out around 0.9; 0.70 still lets
     * clear photos through while turning shaky guesses into "Not sure".
     */
    const val MIN_CONFIDENCE = 0.70f

    /**
     * A near tie between the top two is just as unreliable as a low top score.
     * With MIN_CONFIDENCE at 0.70 the runner-up is at most 0.30, so this only bites
     * if MIN_CONFIDENCE is ever lowered below 0.625; it is kept as that safeguard.
     */
    const val MIN_MARGIN = 0.25f

    fun isHealthy(label: String): Boolean = label.endsWith("__healthy")

    /** The model's "this isn't my crop's leaf" class (models from v2 on). */
    fun isUnknown(label: String): Boolean = label.endsWith("__unknown")

    /** True when the best answer is "not this crop's leaf": the photo, not the plant, is the problem. */
    fun notRecognized(ranked: List<LabelScore>): Boolean = ranked.firstOrNull()?.let { isUnknown(it.label) } == true

    fun cropOf(label: String): String = label.substringBefore("__")

    /** Same rule as agro_ai.export.manifest.display_name. */
    fun displayName(label: String): String =
        label.substringAfter("__").replace('_', ' ').replaceFirstChar { it.uppercase() }

    fun rank(labels: List<String>, probs: FloatArray): List<LabelScore> {
        require(labels.size == probs.size) { "labels (${labels.size}) and outputs (${probs.size}) differ" }
        return labels.indices.map { LabelScore(labels[it], probs[it]) }.sortedByDescending { it.score }
    }

    fun status(ranked: List<LabelScore>): DiagnosisStatus {
        val top = ranked.firstOrNull() ?: return DiagnosisStatus.Uncertain
        if (isUnknown(top.label)) return DiagnosisStatus.Uncertain
        val second = ranked.getOrNull(1)?.score ?: 0f
        if (top.score < MIN_CONFIDENCE || top.score - second < MIN_MARGIN) return DiagnosisStatus.Uncertain
        return if (isHealthy(top.label)) DiagnosisStatus.Healthy else DiagnosisStatus.Disease
    }
}

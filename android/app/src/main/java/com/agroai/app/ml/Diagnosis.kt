package com.agroai.app.ml

import kotlinx.serialization.Serializable

enum class DiagnosisStatus { Healthy, Disease, Uncertain }

@Serializable
data class LabelScore(val label: String, val score: Float)

object Diagnosis {
    /** Below this top probability we ask the farmer to retake the photo. */
    const val MIN_CONFIDENCE = 0.55f

    /** A near tie between the top two is just as unreliable as a low top score. */
    const val MIN_MARGIN = 0.15f

    fun isHealthy(label: String): Boolean = label.endsWith("__healthy")

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
        val second = ranked.getOrNull(1)?.score ?: 0f
        if (top.score < MIN_CONFIDENCE || top.score - second < MIN_MARGIN) return DiagnosisStatus.Uncertain
        return if (isHealthy(top.label)) DiagnosisStatus.Healthy else DiagnosisStatus.Disease
    }
}

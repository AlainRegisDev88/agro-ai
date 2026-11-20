package com.agroai.app

import com.agroai.app.ml.Diagnosis
import com.agroai.app.ml.DiagnosisStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosisTest {
    private val labels = listOf("cassava__mosaic_disease", "cassava__healthy", "cassava__green_mottle")

    @Test
    fun ranksBestFirst() {
        val ranked = Diagnosis.rank(labels, floatArrayOf(0.2f, 0.7f, 0.1f))
        assertEquals(listOf("cassava__healthy", "cassava__mosaic_disease", "cassava__green_mottle"), ranked.map { it.label })
    }

    @Test
    fun confidentHealthy() {
        assertEquals(DiagnosisStatus.Healthy, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.1f, 0.85f, 0.05f))))
    }

    @Test
    fun confidentDisease() {
        assertEquals(DiagnosisStatus.Disease, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.9f, 0.05f, 0.05f))))
    }

    @Test
    fun lowTopScoreIsUncertain() {
        assertEquals(DiagnosisStatus.Uncertain, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.5f, 0.3f, 0.2f))))
    }

    @Test
    fun nearTieIsUncertain() {
        // Above MIN_CONFIDENCE but the margin over the runner-up is too small.
        assertEquals(DiagnosisStatus.Uncertain, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.56f, 0.44f, 0f))))
    }

    @Test
    fun shakyGuessIsNowUncertain() {
        // Was accepted under the old 0.55 threshold; too weak to show a diagnosis.
        assertEquals(DiagnosisStatus.Uncertain, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.62f, 0.2f, 0.18f))))
    }

    @Test
    fun clearWinnerBelowOldMarginIsUncertain() {
        assertEquals(DiagnosisStatus.Uncertain, Diagnosis.status(Diagnosis.rank(labels, floatArrayOf(0.72f, 0.0f, 0.28f))))
    }

    @Test
    fun unknownTopIsNotRecognized() {
        val withUnknown = labels + "cassava__unknown"
        val ranked = Diagnosis.rank(withUnknown, floatArrayOf(0.05f, 0.03f, 0.02f, 0.9f))
        assertEquals(DiagnosisStatus.Uncertain, Diagnosis.status(ranked))
        assertTrue(Diagnosis.notRecognized(ranked))
        assertFalse(Diagnosis.notRecognized(Diagnosis.rank(labels, floatArrayOf(0.9f, 0.05f, 0.05f))))
    }

    @Test
    fun displayNameMatchesPython() {
        assertEquals("Mosaic disease", Diagnosis.displayName("cassava__mosaic_disease"))
        assertEquals("cassava", Diagnosis.cropOf("cassava__mosaic_disease"))
    }
}

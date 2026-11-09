package com.agroai.app.ml

import android.content.Context
import android.net.Uri
import com.agroai.app.data.history.HistoryRepository
import com.agroai.app.data.history.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class NoModelException(val crop: String) : Exception("No model installed for $crop")

/** Photo -> preprocess -> expert -> saved Scan. */
class ScanService(
    private val context: Context,
    private val classifiers: ClassifierProvider,
    private val history: HistoryRepository,
) {
    /** Returns the id of the saved scan. */
    suspend fun analyze(crop: String, photo: Uri): Long = withContext(Dispatchers.Default) {
        val loaded = classifiers.get(crop) ?: throw NoModelException(crop)
        val square = Preprocessor.loadSquare(context, photo)
        val probs = loaded.classifier.classify(Preprocessor.toModelInput(square))
        val ranked = Diagnosis.rank(loaded.classifier.labels, probs)

        val file = File(history.scansDir, "${UUID.randomUUID()}.jpg")
        Preprocessor.saveJpeg(square, file)

        history.add(
            Scan(
                crop = crop,
                label = ranked.first().label,
                confidence = ranked.first().score,
                topJson = Scan.encodeTop(ranked.take(3)),
                imagePath = file.absolutePath,
                modelVersion = loaded.version,
            ),
        )
    }
}

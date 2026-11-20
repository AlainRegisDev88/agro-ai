package com.agroai.app.data.models

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.agroai.app.AgroApp
import java.io.IOException

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val crop = inputData.getString(KEY_CROP) ?: return Result.failure()
        val models = (applicationContext as AgroApp).container.models
        return try {
            setProgress(workDataOf(KEY_PROGRESS to 0f))
            models.download(crop) { setProgress(workDataOf(KEY_PROGRESS to it)) }
            Result.success()
        } catch (e: IOException) {
            // Network drops and checksum mismatches are worth another try.
            Log.w(TAG, "download of $crop failed (attempt $runAttemptCount)", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "download of $crop failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val MAX_ATTEMPTS = 3
        const val KEY_CROP = "crop"
        const val KEY_PROGRESS = "progress"
    }
}

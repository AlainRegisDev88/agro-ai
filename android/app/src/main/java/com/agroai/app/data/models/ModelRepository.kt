package com.agroai.app.data.models

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.agroai.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What is on disk at filesDir/models/<crop>/ next to model.tflite. */
@Serializable
data class InstalledModel(
    val crop: String,
    val version: Int,
    val variant: String,
    val bytes: Long,
    val labels: List<String>,
    val accuracy: Double? = null,
    /** "release" when downloaded, "local" when imported from a file in a debug build. */
    val source: String = "release",
    val installedAt: Long = System.currentTimeMillis(),
) {
    val versionLabel: String get() = if (source == "local") "local" else "v$version $variant"
}

sealed interface DownloadState {
    data object Idle : DownloadState
    data object Queued : DownloadState
    data class Running(val fraction: Float) : DownloadState
    data object Done : DownloadState
    data object Failed : DownloadState
}

class ChecksumMismatch(message: String) : IOException(message)

class ModelRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val root = File(context.filesDir, "models").apply { mkdirs() }
    private val manifestCache = File(root, "manifest.json")
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val workManager get() = WorkManager.getInstance(context)

    private val _manifest = MutableStateFlow(loadCachedManifest())
    val manifest: StateFlow<ModelManifest?> = _manifest.asStateFlow()

    private val _installed = MutableStateFlow(scanInstalled())
    val installed: StateFlow<Map<String, InstalledModel>> = _installed.asStateFlow()

    fun modelFile(crop: String) = File(File(root, crop), MODEL_FILE)

    fun latest(crop: String): ManifestModel? = _manifest.value?.let { Manifests.latest(it, crop) }

    fun downloadBytes(crop: String): Long? = latest(crop)?.let { Manifests.recommendedFile(it).bytes }

    fun updateAvailable(crop: String): Boolean {
        val have = _installed.value[crop] ?: return false
        val newest = latest(crop) ?: return false
        return have.source == "release" && newest.version > have.version
    }

    /** Fetches manifest.json from the release and caches it for offline use. */
    suspend fun refreshManifest(): Result<ModelManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val text = get(BuildConfig.MODELS_BASE_URL + "manifest.json").use { it.body!!.string() }
            val parsed = Manifests.parse(text)
            manifestCache.writeText(text)
            _manifest.value = parsed
            parsed
        }.onFailure { Log.w(TAG, "manifest refresh failed", it) }
    }

    fun enqueueDownload(crop: String, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_CROP to crop))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .addTag(TAG_DOWNLOAD)
            .build()
        workManager.enqueueUniqueWork(workName(crop), ExistingWorkPolicy.KEEP, request)
    }

    fun cancelDownload(crop: String) {
        workManager.cancelUniqueWork(workName(crop))
    }

    fun downloadState(crop: String): Flow<DownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(crop)).map { infos ->
            val info = infos.lastOrNull() ?: return@map DownloadState.Idle
            when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadState.Queued
                WorkInfo.State.RUNNING -> DownloadState.Running(info.progress.getFloat(ModelDownloadWorker.KEY_PROGRESS, 0f))
                WorkInfo.State.SUCCEEDED -> DownloadState.Done
                WorkInfo.State.FAILED -> DownloadState.Failed
                WorkInfo.State.CANCELLED -> DownloadState.Idle
            }
        }

    /**
     * Downloads the recommended file for [crop], verifies its sha256 and installs it.
     * Called from [ModelDownloadWorker]; throws on failure so the worker can retry.
     */
    suspend fun download(crop: String, onProgress: suspend (Float) -> Unit) = withContext(Dispatchers.IO) {
        val manifest = _manifest.value ?: refreshManifest().getOrThrow()
        val model = Manifests.latest(manifest, crop) ?: error("No model for $crop in manifest")
        val file = Manifests.recommendedFile(model)
        val dir = File(root, crop).apply { mkdirs() }
        val part = File(dir, "$MODEL_FILE.part")

        get(BuildConfig.MODELS_BASE_URL + Manifests.assetName(file.path)).use { response ->
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 } ?: file.bytes
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var read = 0L
                    var lastReported = 0f
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val fraction = (read.toFloat() / total).coerceAtMost(1f)
                        if (fraction - lastReported >= 0.02f) {
                            lastReported = fraction
                            onProgress(fraction)
                        }
                    }
                }
            }
        }

        val actual = part.inputStream().use(::sha256Hex)
        if (!actual.equals(file.sha256, ignoreCase = true)) {
            part.delete()
            throw ChecksumMismatch("sha256 mismatch for ${file.path}")
        }
        install(
            part,
            InstalledModel(
                crop = crop,
                version = model.version,
                variant = file.variant,
                bytes = part.length(),
                labels = model.labels,
                accuracy = Manifests.accuracy(model, file.variant),
            ),
        )
    }

    /** Debug builds: install a .tflite and labels.txt picked from the phone's storage. */
    suspend fun importLocal(crop: String, modelUri: Uri, labelsUri: Uri) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val labels = resolver.openInputStream(labelsUri)!!.bufferedReader().readLines()
            .map { it.trim() }.filter { it.isNotEmpty() }
        require(labels.isNotEmpty()) { "labels file is empty" }
        val dir = File(root, crop).apply { mkdirs() }
        val part = File(dir, "$MODEL_FILE.part")
        resolver.openInputStream(modelUri)!!.use { input -> part.outputStream().use { input.copyTo(it) } }
        install(part, InstalledModel(crop, 0, "local", part.length(), labels, source = "local"))
    }

    fun remove(crop: String) {
        cancelDownload(crop)
        File(root, crop).deleteRecursively()
        _installed.value = scanInstalled()
    }

    fun bytesUsed(): Long = root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun install(part: File, meta: InstalledModel) {
        val dir = part.parentFile!!
        File(dir, META_FILE).writeText(json.encodeToString(meta))
        val target = File(dir, MODEL_FILE)
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        _installed.value = scanInstalled()
    }

    private fun get(url: String) = http.newCall(Request.Builder().url(url).build()).execute().also {
        if (!it.isSuccessful) {
            it.close()
            throw IOException("HTTP ${it.code} for $url")
        }
    }

    private fun loadCachedManifest(): ModelManifest? =
        runCatching { Manifests.parse(manifestCache.readText()) }.getOrNull()

    private fun scanInstalled(): Map<String, InstalledModel> =
        root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
            val meta = File(dir, META_FILE)
            if (!meta.exists() || !File(dir, MODEL_FILE).exists()) return@mapNotNull null
            runCatching { json.decodeFromString<InstalledModel>(meta.readText()) }.getOrNull()
        }.associateBy { it.crop }

    companion object {
        private const val TAG = "ModelRepository"
        const val TAG_DOWNLOAD = "model-download"
        private const val MODEL_FILE = "model.tflite"
        private const val META_FILE = "meta.json"
        fun workName(crop: String) = "download-$crop"
    }
}

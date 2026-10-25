package com.slothshot.app.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.slothshot.app.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class QwenModelState {
    object NotDownloaded : QwenModelState()
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Long,
        val totalMb: Long,
        val currentFileName: String
    ) : QwenModelState()
    data class Ready(val modelPath: String, val sizeBytes: Long) : QwenModelState()
    data class Error(val message: String) : QwenModelState()
}

class QwenModelManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null

    private val modelsDir = File(appContext.filesDir, "models")
    val modelFile = File(modelsDir, "qwen2.5-vl-3b-instruct-q4_k_m.gguf")
    val mmprojFile = File(modelsDir, "mmproj-qwen2.5-vl-3b-f16.gguf")

    private val _stateFlow = MutableStateFlow<QwenModelState>(QwenModelState.NotDownloaded)
    val stateFlow: StateFlow<QwenModelState> = _stateFlow.asStateFlow()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    init {
        modelsDir.mkdirs()
        checkExistingModel()
    }

    companion object {
        @Volatile
        private var instance: QwenModelManager? = null

        fun getInstance(context: Context): QwenModelManager {
            return instance ?: synchronized(this) {
                instance ?: QwenModelManager(context).also { instance = it }
            }
        }

        // Direct download mirror URLs for Qwen2.5-VL 3B GGUF
        const val MODEL_URL = "https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/qwen2.5-vl-3b-instruct-q4_k_m.gguf"
        const val MMPROJ_URL = "https://huggingface.co/Qwen/Qwen2.5-VL-3B-Instruct-GGUF/resolve/main/mmproj-qwen2.5-vl-3b-instruct-f16.gguf"
        const val ESTIMATED_TOTAL_MB = 1850L
    }

    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 50_000_000L
    }

    fun checkExistingModel() {
        if (isModelReady()) {
            val totalSize = modelFile.length() + if (mmprojFile.exists()) mmprojFile.length() else 0L
            _stateFlow.value = QwenModelState.Ready(modelFile.absolutePath, totalSize)
        } else {
            _stateFlow.value = QwenModelState.NotDownloaded
        }
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return

        downloadJob = managerScope.launch {
            try {
                createDownloadNotificationChannel()
                _stateFlow.value = QwenModelState.Downloading(0, 0, ESTIMATED_TOTAL_MB, "qwen2.5-vl-3b-instruct-q4_k_m.gguf")

                // Step 1: Download Main GGUF weights
                downloadFileWithProgress(MODEL_URL, modelFile, "qwen2.5-vl-3b-instruct-q4_k_m.gguf", 0, 85)

                // Step 2: Download Visual Projector
                downloadFileWithProgress(MMPROJ_URL, mmprojFile, "mmproj-qwen2.5-vl-3b-f16.gguf", 85, 100)

                val totalSize = modelFile.length() + mmprojFile.length()
                _stateFlow.value = QwenModelState.Ready(modelFile.absolutePath, totalSize)
                showDownloadCompleteNotification()
            } catch (e: CancellationException) {
                _stateFlow.value = QwenModelState.NotDownloaded
            } catch (e: Exception) {
                e.printStackTrace()
                _stateFlow.value = QwenModelState.Error("Ошибка загрузки: ${e.localizedMessage ?: "Сбой соединения"}")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        // Delete incomplete files
        if (modelFile.exists() && !isModelReady()) {
            modelFile.delete()
        }
        _stateFlow.value = QwenModelState.NotDownloaded
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(2002)
    }

    fun deleteModel() {
        cancelDownload()
        if (modelFile.exists()) modelFile.delete()
        if (mmprojFile.exists()) mmprojFile.delete()
        _stateFlow.value = QwenModelState.NotDownloaded
    }

    private suspend fun downloadFileWithProgress(
        url: String,
        destFile: File,
        fileName: String,
        rangeStartPercent: Int,
        rangeEndPercent: Int
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} при скачивании $fileName")
            }
            val body = response.body ?: throw Exception("Пустой ответ сервера")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            FileOutputStream(destFile).use { out ->
                val buffer = ByteArray(64 * 1024)
                val input = body.byteStream()
                var read: Int
                var lastUpdate = System.currentTimeMillis()

                while (input.read(buffer).also { read = it } != -1) {
                    ensureActive()
                    out.write(buffer, 0, read)
                    downloadedBytes += read

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 300) {
                        lastUpdate = now
                        val fraction = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                        val mappedPercent = rangeStartPercent + (fraction * (rangeEndPercent - rangeStartPercent)).toInt()
                        val currentDownloadedMb = (destFile.length() / (1024 * 1024))

                        _stateFlow.value = QwenModelState.Downloading(
                            progressPercent = mappedPercent.coerceIn(0, 100),
                            downloadedMb = currentDownloadedMb,
                            totalMb = ESTIMATED_TOTAL_MB,
                            currentFileName = fileName
                        )
                        updateDownloadNotification(mappedPercent, fileName)
                    }
                }
            }
        }
    }

    private fun createDownloadNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                "qwen_download_channel",
                "Qwen VLM Model Downloader",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Показывает прогресс загрузки локальной модели Qwen2.5-VL"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateDownloadNotification(progress: Int, fileName: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(appContext, "qwen_download_channel")
            .setContentTitle("Загрузка Alibaba Qwen2.5-VL 3B 🦥")
            .setContentText("$fileName ($progress%)")
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        nm.notify(2002, notif)
    }

    private fun showDownloadCompleteNotification() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(2002)
        val notif = NotificationCompat.Builder(appContext, "qwen_download_channel")
            .setContentTitle("Qwen2.5-VL 3B готова к работе 🟢")
            .setContentText("100% оффлайн-зрение для скриншотов активировано!")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        nm.notify(2003, notif)
    }
}

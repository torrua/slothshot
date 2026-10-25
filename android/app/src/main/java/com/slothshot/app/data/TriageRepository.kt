package com.slothshot.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.ocr.MlKitOcrEngine
import com.slothshot.app.ocr.TesseractOcrEngine
import com.slothshot.app.triage.GeminiTriageEngine
import com.slothshot.app.triage.HeuristicTriageEngine
import com.slothshot.app.triage.OnDeviceTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TriageMetrics(
    val totalScanned: Int,
    val ephemeralJunkCount: Int,
    val purgedCount: Int,
    val reclaimedBytes: Long,
    val timeSavedMinutes: Int
)

class TriageRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("slothshot_prefs", Context.MODE_PRIVATE)
    private val storageFile = File(appContext.filesDir, "triage_items.json")

    private val _itemsFlow = MutableStateFlow<List<TriageItem>>(emptyList())
    val itemsFlow: StateFlow<List<TriageItem>> = _itemsFlow.asStateFlow()

    private val tesseractOcr = TesseractOcrEngine()
    private val mlKitOcr = MlKitOcrEngine()
    private val onDeviceTranslator = OnDeviceTranslator()
    private val heuristicTriage = HeuristicTriageEngine()
    private val geminiTriage = GeminiTriageEngine(apiKeyProvider = { getApiKey() })

    init {
        loadPersistedItems()
    }

    companion object {
        @Volatile
        private var instance: TriageRepository? = null

        fun getInstance(context: Context): TriageRepository {
            return instance ?: synchronized(this) {
                instance ?: TriageRepository(context).also { instance = it }
            }
        }
    }

    fun getApiKey(): String = prefs.getString("gemini_api_key", "").orEmpty()

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun isAutoCopyOtpEnabled(): Boolean = prefs.getBoolean("auto_copy_otp", true)

    fun setAutoCopyOtpEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_copy_otp", enabled).apply()
    }

    fun isMonitoringActive(): Boolean = prefs.getBoolean("monitoring_active", true)

    fun setMonitoringActive(active: Boolean) {
        prefs.edit().putBoolean("monitoring_active", active).apply()
    }

    fun getMetrics(): TriageMetrics {
        val items = _itemsFlow.value
        val purged = items.filter { it.isPurged }
        val active = items.filter { !it.isPurged }
        val ephemeral = active.filter { it.retention == RetentionClass.EPHEMERAL_PURGE }

        val reclaimedBytes = purged.sumOf {
            if (it.fileSizeBytes > 0) it.fileSizeBytes else 2_500_000L
        }

        return TriageMetrics(
            totalScanned = items.size,
            ephemeralJunkCount = ephemeral.size,
            purgedCount = purged.size,
            reclaimedBytes = reclaimedBytes,
            timeSavedMinutes = (items.size * 1.5).toInt()
        )
    }

    suspend fun triageImageUri(uri: Uri, fileName: String = "Screenshot", sizeBytes: Long = 0L): TriageItem = withContext(Dispatchers.IO) {
        val existing = _itemsFlow.value.find { it.uriString == uri.toString() }
        if (existing != null) return@withContext existing

        // Path 1: Cloud Multimodal Vision (if Gemini key configured)
        if (getApiKey().isNotBlank()) {
            val cloudResult = geminiTriage.triage("", fileName, appContext, uri)
            val item = TriageItem(
                id = UUID.randomUUID().toString(),
                uriString = uri.toString(),
                fileName = fileName,
                timestamp = System.currentTimeMillis(),
                fileSizeBytes = sizeBytes,
                category = cloudResult.category,
                retention = cloudResult.retention,
                title = cloudResult.title,
                summary = cloudResult.summary,
                keyValues = cloudResult.keyValues,
                tags = cloudResult.tags,
                primaryActionType = cloudResult.actionType,
                primaryActionLabel = cloudResult.actionLabel,
                primaryActionPayload = cloudResult.actionPayload,
                rawContent = cloudResult.fullRawContent.orEmpty(),
                isPurged = false
            )
            val updated = listOf(item) + _itemsFlow.value
            _itemsFlow.value = updated
            persistItems(updated)
            return@withContext item
        }

        // Path 2: 100% On-Device Offline Engine (Tesseract Cyrillic + ML Kit Translator)
        var rawText = tesseractOcr.recognizeText(appContext, uri)
        if (rawText.isBlank()) {
            // Fallback to ML Kit if Tesseract couldn't extract
            rawText = mlKitOcr.recognizeText(appContext, uri)
        }

        // Translate to Russian if screenshot is in English/foreign language
        val translatedRussianText = onDeviceTranslator.translateToRussianIfNeeded(rawText)

        // Run Russian heuristic triage
        val takeaway = heuristicTriage.triage(translatedRussianText.ifBlank { rawText }, fileName)

        val fullDisplayContent = if (translatedRussianText.isNotBlank() && translatedRussianText != rawText) {
            "🇷🇺 Перевод на русский:\n$translatedRussianText\n\n📄 Оригинал OCR:\n$rawText"
        } else {
            rawText
        }

        val item = TriageItem(
            id = UUID.randomUUID().toString(),
            uriString = uri.toString(),
            fileName = fileName,
            timestamp = System.currentTimeMillis(),
            fileSizeBytes = sizeBytes,
            category = takeaway.category,
            retention = takeaway.retention,
            title = takeaway.title,
            summary = takeaway.summary,
            keyValues = takeaway.keyValues,
            tags = takeaway.tags,
            primaryActionType = takeaway.actionType,
            primaryActionLabel = takeaway.actionLabel,
            primaryActionPayload = takeaway.actionPayload,
            rawContent = fullDisplayContent,
            isPurged = false
        )

        val updated = listOf(item) + _itemsFlow.value
        _itemsFlow.value = updated
        persistItems(updated)
        item
    }

    fun markItemAsPurged(id: String) {
        val current = _itemsFlow.value
        val updated = current.map {
            if (it.id == id) it.copy(isPurged = true) else it
        }
        _itemsFlow.value = updated
        persistItems(updated)
    }

    fun markAllEphemeralAsPurged(ids: Set<String>) {
        val current = _itemsFlow.value
        val updated = current.map {
            if (it.id in ids) it.copy(isPurged = true) else it
        }
        _itemsFlow.value = updated
        persistItems(updated)
    }

    fun clearAllItems() {
        _itemsFlow.value = emptyList()
        if (storageFile.exists()) {
            storageFile.delete()
        }
    }

    suspend fun scanGalleryScreenshots(limit: Int = 30): List<TriageItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TriageItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%Screenshot%", "%screenshot%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            appContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Screenshot.png"
                    val size = cursor.getLong(sizeCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    // Skip if already triaged
                    if (_itemsFlow.value.none { it.uriString == contentUri.toString() }) {
                        val item = triageImageUri(contentUri, name, size)
                        results.add(item)
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        results
    }

    private fun loadPersistedItems() {
        if (!storageFile.exists()) return
        try {
            val jsonStr = storageFile.readText()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<TriageItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val kvObj = obj.optJSONObject("keyValues")
                val kv = mutableMapOf<String, String>()
                kvObj?.keys()?.forEach { k -> kv[k] = kvObj.optString(k) }

                val tagsArr = obj.optJSONArray("tags")
                val tags = mutableListOf<String>()
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        tags.add(tagsArr.getString(t))
                    }
                }

                list.add(
                    TriageItem(
                        id = obj.getString("id"),
                        uriString = obj.getString("uriString"),
                        fileName = obj.optString("fileName", "Screenshot"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                        category = runCatching { ContentCategory.valueOf(obj.getString("category")) }.getOrDefault(ContentCategory.UNKNOWN),
                        retention = runCatching { RetentionClass.valueOf(obj.getString("retention")) }.getOrDefault(RetentionClass.KEEP_ARCHIVE),
                        title = obj.getString("title"),
                        summary = obj.getString("summary"),
                        keyValues = kv,
                        tags = tags,
                        primaryActionType = runCatching { com.slothshot.app.model.ActionType.valueOf(obj.getString("primaryActionType")) }.getOrDefault(com.slothshot.app.model.ActionType.COPY),
                        primaryActionLabel = obj.optString("primaryActionLabel", "Copy"),
                        primaryActionPayload = obj.optString("primaryActionPayload", ""),
                        rawContent = obj.optString("rawContent", ""),
                        isPurged = obj.optBoolean("isPurged", false)
                    )
                )
            }
            _itemsFlow.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun persistItems(items: List<TriageItem>) {
        try {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("uriString", item.uriString)
                    put("fileName", item.fileName)
                    put("timestamp", item.timestamp)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("category", item.category.name)
                    put("retention", item.retention.name)
                    put("title", item.title)
                    put("summary", item.summary)
                    put("keyValues", JSONObject(item.keyValues))
                    put("tags", JSONArray(item.tags))
                    put("primaryActionType", item.primaryActionType.name)
                    put("primaryActionLabel", item.primaryActionLabel)
                    put("primaryActionPayload", item.primaryActionPayload)
                    put("rawContent", item.rawContent)
                    put("isPurged", item.isPurged)
                }
                arr.put(obj)
            }
            storageFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

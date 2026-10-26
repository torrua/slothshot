package com.slothshot.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slothshot.app.ai.QwenModelState
import com.slothshot.app.data.TriageMetrics
import com.slothshot.app.data.TriageRepository
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.service.ScreenshotMonitorService
import com.slothshot.app.ui.components.FilterType
import com.slothshot.app.ui.components.HubStats
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TriageRepository.getInstance(application)
    private val qwenModelManager = repository.qwenModelManager

    val items = repository.itemsFlow
    val filter = MutableStateFlow<FilterType>(FilterType.All)
    val isScanning = MutableStateFlow(false)
    val rawContentItem = MutableStateFlow<TriageItem?>(null)
    val fullScreenImageItem = MutableStateFlow<TriageItem?>(null)
    val showSettings = MutableStateFlow(false)

    val qwenModelState: StateFlow<QwenModelState> = qwenModelManager.stateFlow

    val metrics = items.map { repository.getMetrics() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            repository.getMetrics()
        )

    val filteredItems: StateFlow<List<TriageItem>> = combine(items, filter) { list, f ->
        when (f) {
            is FilterType.All -> list.filter { !it.isPurged }
            is FilterType.Receipts -> list.filter {
                !it.isPurged && (
                    it.category == ContentCategory.TRANSACTIONAL_RECEIPT ||
                    it.tags.any { t -> t in listOf("чек", "receipt", "оплата", "расходы", "заказ", "додо", "dodo") }
                )
            }
            is FilterType.Shopping -> list.filter {
                !it.isPurged && (
                    it.tags.any { t -> t in listOf("product", "shopping", "товар", "покупки", "вещи", "магазин", "скидка", "market", "salewa", "одежда", "wishlist", "купить") } ||
                    it.category == ContentCategory.ENTERTAINMENT_MEDIA
                )
            }
            is FilterType.Knowledge -> list.filter {
                !it.isPurged && (
                    it.category == ContentCategory.KNOWLEDGE_REFERENCE ||
                    it.category == ContentCategory.PERSONAL_CHAT ||
                    it.tags.any { t -> t in listOf("заметка", "книга", "статья", "код", "note", "book", "wiki") }
                )
            }
            is FilterType.EphemeralOnly -> list.filter {
                !it.isPurged && (
                    it.retention == RetentionClass.EPHEMERAL_PURGE ||
                    it.category == ContentCategory.TEMPORARY_UTILITY
                )
            }
            is FilterType.ByCategory -> list.filter { it.category == f.category && !it.isPurged }
            is FilterType.PurgedOnly -> list.filter { it.isPurged }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hubStats: StateFlow<HubStats> = items.map { list ->
        val active = list.filter { !it.isPurged }
        val archived = list.filter { it.isPurged }

        val receipts = active.filter {
            it.category == ContentCategory.TRANSACTIONAL_RECEIPT ||
            it.tags.any { t -> t in listOf("чек", "receipt", "оплата", "расходы", "заказ", "додо", "dodo") }
        }

        var totalReceiptsAmount = 0.0
        for (item in receipts) {
            val amount = extractAmountFromItem(item)
            if (amount != null && amount > 0) {
                totalReceiptsAmount += amount
            }
        }

        val shopping = active.filter {
            it.tags.any { t -> t in listOf("product", "shopping", "товар", "покупки", "вещи", "магазин", "скидка", "market", "salewa", "одежда", "wishlist", "купить") } ||
            it.category == ContentCategory.ENTERTAINMENT_MEDIA
        }

        val knowledge = active.filter {
            it.category == ContentCategory.KNOWLEDGE_REFERENCE ||
            it.category == ContentCategory.PERSONAL_CHAT ||
            it.tags.any { t -> t in listOf("заметка", "книга", "статья", "код", "note", "book", "wiki") }
        }

        val ephemeral = active.filter {
            it.retention == RetentionClass.EPHEMERAL_PURGE ||
            it.category == ContentCategory.TEMPORARY_UTILITY
        }

        val ephemeralBytes = ephemeral.sumOf { if (it.fileSizeBytes > 0) it.fileSizeBytes else 2_500_000L }

        HubStats(
            totalCount = active.size,
            receiptsCount = receipts.size,
            receiptsTotalAmount = totalReceiptsAmount,
            shoppingCount = shopping.size,
            knowledgeCount = knowledge.size,
            ephemeralCount = ephemeral.size,
            ephemeralBytes = ephemeralBytes,
            archivedCount = archived.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HubStats())

    val urgentItems: StateFlow<List<TriageItem>> = items.map { list ->
        list.filter { it.retention == RetentionClass.ACTION_REQUIRED && !it.isPurged }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scanGallery() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            repository.scanGalleryScreenshots(limit = 40)
            isScanning.value = false
        }
    }

    fun clearAndRescan() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            repository.clearAllItems()
            repository.scanGalleryScreenshots(limit = 40)
            isScanning.value = false
        }
    }

    fun markActionDone(id: String) {
        repository.markActionDone(id)
    }

    fun markItemPurged(id: String) {
        repository.markItemAsPurged(id)
    }

    fun restoreItem(id: String) {
        repository.restoreItem(id)
    }

    fun markEphemeralPurged(ids: Set<String>) {
        repository.markAllEphemeralAsPurged(ids)
    }

    fun getAllEphemeralItems(): List<TriageItem> {
        return items.value.filter { it.retention == RetentionClass.EPHEMERAL_PURGE && !it.isPurged }
    }

    fun getApiKey(): String = repository.getApiKey()
    fun isAutoCopyOtpEnabled(): Boolean = repository.isAutoCopyOtpEnabled()
    fun isMonitoringActive(): Boolean = repository.isMonitoringActive()

    fun startQwenDownload() {
        qwenModelManager.startDownload()
    }

    fun cancelQwenDownload() {
        qwenModelManager.cancelDownload()
    }

    fun deleteQwenModel() {
        qwenModelManager.deleteModel()
    }

    fun updateSettings(apiKey: String, autoCopy: Boolean, monitoring: Boolean) {
        repository.setApiKey(apiKey)
        repository.setAutoCopyOtpEnabled(autoCopy)
        val wasActive = repository.isMonitoringActive()
        repository.setMonitoringActive(monitoring)

        if (monitoring && !wasActive) {
            ScreenshotMonitorService.start(getApplication())
        } else if (!monitoring && wasActive) {
            ScreenshotMonitorService.stop(getApplication())
        }
    }

    private fun extractAmountFromItem(item: TriageItem): Double? {
        val amountKeys = listOf("сумма", "итого", "total", "к оплате", "цена", "стоимость", "price", "amount")
        for ((k, v) in item.keyValues) {
            val lowerK = k.lowercase()
            if (amountKeys.any { lowerK.contains(it) }) {
                val parsed = extractNumber(v)
                if (parsed != null && parsed > 0) return parsed
            }
        }
        val titleParsed = extractNumber(item.title)
        if (titleParsed != null && titleParsed > 0) return titleParsed

        val summaryParsed = extractNumber(item.summary)
        if (summaryParsed != null && summaryParsed > 0) return summaryParsed

        return null
    }

    private fun extractNumber(text: String): Double? {
        val clean = text.replace(" ", "")
            .replace("\u00A0", "")
            .replace("₽", "")
            .replace("$", "")
            .replace("€", "")
            .replace("руб", "", ignoreCase = true)
            .trim()
        val pattern = java.util.regex.Pattern.compile("([0-9]+(?:[.,][0-9]{1,2})?)")
        val matcher = pattern.matcher(clean)
        if (matcher.find()) {
            val numStr = matcher.group(1)?.replace(",", ".") ?: return null
            return numStr.toDoubleOrNull()
        }
        return null
    }
}

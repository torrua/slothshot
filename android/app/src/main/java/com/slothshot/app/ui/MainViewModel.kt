package com.slothshot.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.slothshot.app.ai.QwenModelState
import com.slothshot.app.data.TriageMetrics
import com.slothshot.app.data.TriageRepository
import com.slothshot.app.model.RetentionClass
import com.slothshot.app.model.TriageItem
import com.slothshot.app.service.ScreenshotMonitorService
import com.slothshot.app.ui.components.FilterType
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
            is FilterType.All -> list
            is FilterType.EphemeralOnly -> list.filter { it.retention == RetentionClass.EPHEMERAL_PURGE && !it.isPurged }
            is FilterType.ByCategory -> list.filter { it.category == f.category && !it.isPurged }
            is FilterType.PurgedOnly -> list.filter { it.isPurged }
        }
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

    fun markItemPurged(id: String) {
        repository.markItemAsPurged(id)
    }

    fun markEphemeralPurged(ids: Set<String>) {
        repository.markAllEphemeralAsPurged(ids)
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
}

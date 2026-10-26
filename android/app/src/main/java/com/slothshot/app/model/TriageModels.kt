package com.slothshot.app.model

enum class ContentCategory(val displayName: String, val emoji: String) {
    TEMPORARY_UTILITY("Мусор / 2FA", "⚡"),
    ENTERTAINMENT_MEDIA("Кино и книги", "🎬"),
    TRANSACTIONAL_RECEIPT("Чеки и заказы", "🧾"),
    KNOWLEDGE_REFERENCE("Заметки", "📚"),
    PERSONAL_CHAT("Чаты", "💬"),
    UNKNOWN("Прочее", "📁")
}

enum class RetentionClass(val label: String, val badgeColorHex: String) {
    EPHEMERAL_PURGE("Мусор", "#EF4444"),
    ACTION_REQUIRED("Нужно действие", "#F59E0B"),
    KEEP_ARCHIVE("Сохранить", "#10B981")
}

enum class ActionType {
    COPY,
    OPEN_URL,
    SEARCH_WEB,
    SHARE,
    PURGE
}

data class TriageItem(
    val id: String,
    val uriString: String,
    val fileName: String,
    val timestamp: Long,
    val fileSizeBytes: Long,
    val category: ContentCategory,
    val retention: RetentionClass,
    val title: String,
    val summary: String,
    val keyValues: Map<String, String>,
    val tags: List<String>,
    val primaryActionType: ActionType,
    val primaryActionLabel: String,
    val primaryActionPayload: String,
    val rawContent: String,
    val isPurged: Boolean = false
)

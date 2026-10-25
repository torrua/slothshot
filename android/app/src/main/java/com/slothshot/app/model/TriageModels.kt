package com.slothshot.app.model

enum class ContentCategory(val displayName: String, val emoji: String) {
    TEMPORARY_UTILITY("Temporary Utility", "⚡"),
    ENTERTAINMENT_MEDIA("Movies & Books", "🎬"),
    TRANSACTIONAL_RECEIPT("Receipts & Orders", "🧾"),
    KNOWLEDGE_REFERENCE("Knowledge & Notes", "📚"),
    PERSONAL_CHAT("Chats & Social", "💬"),
    UNKNOWN("Other", "📁")
}

enum class RetentionClass(val label: String, val badgeColorHex: String) {
    EPHEMERAL_PURGE("Purge in 24h", "#EF4444"),
    ACTION_REQUIRED("Action Pending", "#F59E0B"),
    KEEP_ARCHIVE("Keep Archive", "#10B981")
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

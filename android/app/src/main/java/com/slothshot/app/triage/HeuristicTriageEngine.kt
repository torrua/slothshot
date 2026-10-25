package com.slothshot.app.triage

import com.slothshot.app.model.ActionType
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import java.util.regex.Pattern

data class ParsedTakeaway(
    val category: ContentCategory,
    val retention: RetentionClass,
    val title: String,
    val summary: String,
    val keyValues: Map<String, String>,
    val tags: List<String>,
    val actionType: ActionType,
    val actionLabel: String,
    val actionPayload: String,
    val fullRawContent: String? = null
)

class HeuristicTriageEngine {

    fun triage(ocrText: String, fileName: String = ""): ParsedTakeaway {
        val cleanedText = CyrillicDemangler.clean(ocrText.trim())
        val text = cleanedText
        val lower = text.lowercase()

        // 1. Check for OTP / 2FA code
        val otpPattern = Pattern.compile("(?i)(?:code|verification|otp|2fa|код|kog|пароль|napoл|security code)[^0-9a-zA-Z]{0,10}([0-9]{4,8}|[0-9]{3}[-\\s][0-9]{3})")
        val otpMatcher = otpPattern.matcher(text)
        if (otpMatcher.find()) {
            val code = otpMatcher.group(1)?.replace(" ", "")?.replace("-", "") ?: ""
            if (code.isNotEmpty()) {
                val kv = mutableMapOf("Код подтверждения" to code)
                if (lower.contains("minute") || lower.contains("мин")) {
                    kv["Срок действия"] = "5-10 минут"
                }
                return ParsedTakeaway(
                    category = ContentCategory.TEMPORARY_UTILITY,
                    retention = RetentionClass.EPHEMERAL_PURGE,
                    title = "Код подтверждения 2FA",
                    summary = "Одноразовый проверочный код ($code). Временные данные — безопасно удалить после ввода.",
                    keyValues = kv,
                    tags = listOf("код", "безопасность", "2fa", "удалить"),
                    actionType = ActionType.COPY,
                    actionLabel = "Скопировать код",
                    actionPayload = code,
                    fullRawContent = text
                )
            }
        }

        // Standalone 6-digit code if text is very short (classic SMS / authenticator screenshot)
        if (text.lines().size <= 6) {
            val standaloneCodePattern = Pattern.compile("\\b([0-9]{6}|[0-9]{3}[-\\s][0-9]{3})\\b")
            val scMatcher = standaloneCodePattern.matcher(text)
            if (scMatcher.find()) {
                val code = scMatcher.group(1)?.replace(" ", "")?.replace("-", "") ?: ""
                return ParsedTakeaway(
                    category = ContentCategory.TEMPORARY_UTILITY,
                    retention = RetentionClass.EPHEMERAL_PURGE,
                    title = "Код безопасности ($code)",
                    summary = "Одноразовый код подтверждения ($code). Можно удалить после использования.",
                    keyValues = mapOf("Код" to code),
                    tags = listOf("код", "безопасность", "удалить"),
                    actionType = ActionType.COPY,
                    actionLabel = "Скопировать код",
                    actionPayload = code,
                    fullRawContent = text
                )
            }
        }

        // 2. Wi-Fi credentials
        val wifiQrPattern = Pattern.compile("(?i)WIFI:S:(.+?);(?:T:.+?;)?P:(.+?);;")
        val wifiQrMatcher = wifiQrPattern.matcher(text)
        if (wifiQrMatcher.find()) {
            val ssid = wifiQrMatcher.group(1) ?: "Сеть"
            val pass = wifiQrMatcher.group(2) ?: ""
            return ParsedTakeaway(
                category = ContentCategory.TEMPORARY_UTILITY,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Wi-Fi: $ssid",
                summary = "Пароль и параметры сети '$ssid'. Скопируйте пароль для входа.",
                keyValues = mapOf("Сеть" to ssid, "Пароль" to pass),
                tags = listOf("wifi", "сеть", "пароль"),
                actionType = ActionType.COPY,
                actionLabel = "Скопировать пароль",
                actionPayload = pass,
                fullRawContent = text
            )
        }

        if ((lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("пароль") || lower.contains("сеть")) &&
            (lower.contains("password") || lower.contains("пароль") || lower.contains("pass:"))) {
            val passPattern = Pattern.compile("(?i)(?:password|pass|пароль)[^0-9a-zA-Z]{0,5}([a-zA-Z0-9!@#$%^&*()_+=-]{5,30})")
            val passMatcher = passPattern.matcher(text)
            val pass = if (passMatcher.find()) passMatcher.group(1) ?: "" else ""
            return ParsedTakeaway(
                category = ContentCategory.TEMPORARY_UTILITY,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Пароль от Wi-Fi",
                summary = "Данные для доступа к сети Wi-Fi.",
                keyValues = if (pass.isNotEmpty()) mapOf("Пароль" to pass) else emptyMap(),
                tags = listOf("wifi", "сеть", "пароль"),
                actionType = ActionType.COPY,
                actionLabel = "Скопировать пароль",
                actionPayload = pass.ifEmpty { text },
                fullRawContent = text
            )
        }

        // 3. URLs
        val urlPattern = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\.&]+")
        val urlMatcher = urlPattern.matcher(text)
        if (urlMatcher.find()) {
            val url = urlMatcher.group(0) ?: ""
            return ParsedTakeaway(
                category = ContentCategory.KNOWLEDGE_REFERENCE,
                retention = RetentionClass.KEEP_ARCHIVE,
                title = "Ссылка: ${url.take(30)}...",
                summary = "Сохраненная веб-ссылка из скриншота.",
                keyValues = mapOf("URL" to url),
                tags = listOf("ссылка", "веб", "закладка"),
                actionType = ActionType.OPEN_URL,
                actionLabel = "Открыть ссылку",
                actionPayload = url,
                fullRawContent = text
            )
        }

        // 4. Movies, Books, Media
        if (lower.contains("imdb") || lower.contains("rotten tomatoes") || lower.contains("netflix") ||
            lower.contains("кинопоиск") || lower.contains("сезон") || lower.contains("season") ||
            lower.contains("directed by") || lower.contains("режиссер") || lower.contains("rating:")) {
            val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: "Рекомендация фильма"
            return ParsedTakeaway(
                category = ContentCategory.ENTERTAINMENT_MEDIA,
                retention = RetentionClass.KEEP_ARCHIVE,
                title = firstLine.take(45),
                summary = "Фильм или сериал для просмотра: $firstLine.",
                keyValues = mapOf("Название" to firstLine),
                tags = listOf("фильм", "сериал", "кино"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Искать в Google",
                actionPayload = firstLine,
                fullRawContent = text
            )
        }

        // 5. Tracking & Shipping
        val trackingPattern = Pattern.compile("(?i)(?:tracking|трек|трек[- ]номер|order|заказ)(?:\\s+(?:number|no|code|id|#))?[:\\s]*([A-Z0-9]{8,35})")
        val trackMatcher = trackingPattern.matcher(text)
        if (trackMatcher.find()) {
            val trackingCode = trackMatcher.group(1) ?: ""
            return ParsedTakeaway(
                category = ContentCategory.TRANSACTIONAL_RECEIPT,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Трек посылки: $trackingCode",
                summary = "Отслеживание доставки заказа #$trackingCode.",
                keyValues = mapOf("Трек-номер" to trackingCode),
                tags = listOf("доставка", "посылка", "трекинг"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Отследить посылку",
                actionPayload = trackingCode,
                fullRawContent = text
            )
        } else if (lower.contains("tracking") || lower.contains("in transit") || lower.contains("доставка") || lower.contains("посылка")) {
            val codePat = Pattern.compile("\\b([A-Z0-9]{8,30})\\b")
            val cpMatcher = codePat.matcher(text)
            val code = if (cpMatcher.find()) cpMatcher.group(1) ?: "Shipment" else "Shipment"
            return ParsedTakeaway(
                category = ContentCategory.TRANSACTIONAL_RECEIPT,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Доставка посылки",
                summary = "Посылка в пути ($code).",
                keyValues = mapOf("Трек-номер" to code),
                tags = listOf("доставка", "посылка", "заказ"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Отследить заказ",
                actionPayload = code,
                fullRawContent = text
            )
        }

        // 6. Receipts & Payment Totals
        val totalPattern = Pattern.compile("(?i)(?:total|итого|mroro|hroro|к оплате|сумма|cymma)[^0-9]{0,5}([0-9]+[.,][0-9]{2}|[0-9]+)\\s*(?:rub|usd|eur|\\$|€|₽|руб)?")
        val totalMatcher = totalPattern.matcher(text)
        if (totalMatcher.find()) {
            val total = totalMatcher.group(0)?.trim() ?: ""
            return ParsedTakeaway(
                category = ContentCategory.TRANSACTIONAL_RECEIPT,
                retention = RetentionClass.KEEP_ARCHIVE,
                title = "Чек: $total",
                summary = "Финансовая операция или чек на сумму $total.",
                keyValues = mapOf("Сумма" to total),
                tags = listOf("чек", "оплата", "расходы"),
                actionType = ActionType.COPY,
                actionLabel = "Скопировать сумму",
                actionPayload = total,
                fullRawContent = text
            )
        }

        // 7. General Note
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.take(40) ?: "Скриншот из галереи"
        val snippet = text.lines().take(3).joinToString(" ").take(100)
        return ParsedTakeaway(
            category = ContentCategory.KNOWLEDGE_REFERENCE,
            retention = RetentionClass.KEEP_ARCHIVE,
            title = firstLine,
            summary = snippet.ifEmpty { "Визуальная заметка." },
            keyValues = mapOf("Строк" to "${text.lines().size}"),
            tags = listOf("заметка", "скриншот"),
            actionType = ActionType.COPY,
            actionLabel = "Скопировать текст",
            actionPayload = text,
            fullRawContent = text
        )
    }
}

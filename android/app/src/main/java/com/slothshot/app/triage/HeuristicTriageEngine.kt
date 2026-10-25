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
    val actionPayload: String
)

class HeuristicTriageEngine {

    fun triage(ocrText: String, fileName: String = ""): ParsedTakeaway {
        val text = ocrText.trim()
        val lower = text.lowercase()

        // 1. Check for OTP / 2FA code
        val otpPattern = Pattern.compile("(?i)(?:code|verification|otp|2fa|код|пароль|security code)[^0-9a-zA-Z]{0,10}([0-9]{4,8}|[0-9]{3}[-\\s][0-9]{3})")
        val otpMatcher = otpPattern.matcher(text)
        if (otpMatcher.find()) {
            val code = otpMatcher.group(1)?.replace(" ", "")?.replace("-", "") ?: ""
            if (code.isNotEmpty()) {
                val kv = mutableMapOf("Verification Code" to code)
                if (lower.contains("minute") || lower.contains("мин")) {
                    kv["Expires"] = "Short-lived (5-10m)"
                }
                return ParsedTakeaway(
                    category = ContentCategory.TEMPORARY_UTILITY,
                    retention = RetentionClass.EPHEMERAL_PURGE,
                    title = "2FA Verification Code",
                    summary = "Temporary one-time passcode ($code). Ephemeral data - safe to purge immediately.",
                    keyValues = kv,
                    tags = listOf("otp", "security", "ephemeral", "2fa"),
                    actionType = ActionType.COPY,
                    actionLabel = "Copy Code",
                    actionPayload = code
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
                    title = "Security Code Detected",
                    summary = "Single-use verification code ($code). Safe to delete once entered.",
                    keyValues = mapOf("Code" to code),
                    tags = listOf("otp", "ephemeral"),
                    actionType = ActionType.COPY,
                    actionLabel = "Copy Code",
                    actionPayload = code
                )
            }
        }

        // 2. Wi-Fi credentials
        val wifiQrPattern = Pattern.compile("(?i)WIFI:S:(.+?);(?:T:.+?;)?P:(.+?);;")
        val wifiQrMatcher = wifiQrPattern.matcher(text)
        if (wifiQrMatcher.find()) {
            val ssid = wifiQrMatcher.group(1) ?: "Network"
            val pass = wifiQrMatcher.group(2) ?: ""
            return ParsedTakeaway(
                category = ContentCategory.TEMPORARY_UTILITY,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Wi-Fi: $ssid",
                summary = "Network credentials for SSID '$ssid'. Copy password to connect.",
                keyValues = mapOf("SSID" to ssid, "Password" to pass),
                tags = listOf("wifi", "network", "credentials"),
                actionType = ActionType.COPY,
                actionLabel = "Copy Password",
                actionPayload = pass
            )
        }

        if ((lower.contains("wi-fi") || lower.contains("wifi") || lower.contains("пароль от wi-fi") || lower.contains("пароль wi-fi")) &&
            (lower.contains("password") || lower.contains("пароль") || lower.contains("pass:"))) {
            val passPattern = Pattern.compile("(?i)(?:password|pass|пароль)[^0-9a-zA-Z]{0,5}([a-zA-Z0-9!@#$%^&*()_+=-]{5,30})")
            val passMatcher = passPattern.matcher(text)
            val pass = if (passMatcher.find()) passMatcher.group(1) ?: "" else ""
            return ParsedTakeaway(
                category = ContentCategory.TEMPORARY_UTILITY,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Wi-Fi Network Credentials",
                summary = "Wi-Fi access details detected.",
                keyValues = if (pass.isNotEmpty()) mapOf("Password" to pass) else emptyMap(),
                tags = listOf("wifi", "network"),
                actionType = ActionType.COPY,
                actionLabel = "Copy Password",
                actionPayload = pass.ifEmpty { text }
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
                title = "Web Link: ${url.take(30)}...",
                summary = "Saved web link from screenshot.",
                keyValues = mapOf("URL" to url),
                tags = listOf("link", "web", "bookmark"),
                actionType = ActionType.OPEN_URL,
                actionLabel = "Open Link",
                actionPayload = url
            )
        }

        // 4. Movies, Books, Media
        if (lower.contains("imdb") || lower.contains("rotten tomatoes") || lower.contains("netflix") ||
            lower.contains("кинопоиск") || lower.contains("сезон") || lower.contains("season") ||
            lower.contains("directed by") || lower.contains("режиссер") || lower.contains("rating:")) {
            val firstLine = text.lines().firstOrNull { it.isNotBlank() } ?: "Media Recommendation"
            return ParsedTakeaway(
                category = ContentCategory.ENTERTAINMENT_MEDIA,
                retention = RetentionClass.KEEP_ARCHIVE,
                title = firstLine.take(45),
                summary = "Media recommendation: $firstLine. Stored for your watchlist.",
                keyValues = mapOf("Title" to firstLine),
                tags = listOf("movie", "series", "watchlist"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Search on Web",
                actionPayload = firstLine
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
                title = "Delivery Tracking: $trackingCode",
                summary = "Tracking shipment #$trackingCode.",
                keyValues = mapOf("Tracking #" to trackingCode),
                tags = listOf("tracking", "delivery", "order"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Track Shipment",
                actionPayload = trackingCode
            )
        } else if (lower.contains("tracking") || lower.contains("in transit") || lower.contains("доставка") || lower.contains("посылка")) {
            val codePat = Pattern.compile("\\b([A-Z0-9]{8,30})\\b")
            val cpMatcher = codePat.matcher(text)
            val code = if (cpMatcher.find()) cpMatcher.group(1) ?: "Shipment" else "Shipment"
            return ParsedTakeaway(
                category = ContentCategory.TRANSACTIONAL_RECEIPT,
                retention = RetentionClass.ACTION_REQUIRED,
                title = "Delivery Tracking",
                summary = "Shipment in transit ($code).",
                keyValues = mapOf("Tracking #" to code),
                tags = listOf("tracking", "delivery", "order"),
                actionType = ActionType.SEARCH_WEB,
                actionLabel = "Track Shipment",
                actionPayload = code
            )
        }

        // 6. Receipts & Payment Totals
        val totalPattern = Pattern.compile("(?i)(?:total|итого|к оплате|сумма)[^0-9]{0,5}([0-9]+[.,][0-9]{2}|[0-9]+)\\s*(?:rub|usd|eur|\\$|€|₽)?")
        val totalMatcher = totalPattern.matcher(text)
        if (totalMatcher.find()) {
            val total = totalMatcher.group(0)?.trim() ?: ""
            return ParsedTakeaway(
                category = ContentCategory.TRANSACTIONAL_RECEIPT,
                retention = RetentionClass.KEEP_ARCHIVE,
                title = "Receipt: $total",
                summary = "Transaction or receipt document ($total).",
                keyValues = mapOf("Amount" to total),
                tags = listOf("receipt", "financial", "payment"),
                actionType = ActionType.COPY,
                actionLabel = "Copy Amount",
                actionPayload = total
            )
        }

        // 7. General Knowledge / Note
        val firstLine = text.lines().firstOrNull { it.isNotBlank() }?.take(40) ?: "Quick Screenshot Note"
        val snippet = text.lines().take(3).joinToString(" ").take(100)
        return ParsedTakeaway(
            category = ContentCategory.KNOWLEDGE_REFERENCE,
            retention = RetentionClass.KEEP_ARCHIVE,
            title = firstLine,
            summary = snippet.ifEmpty { "Saved visual note." },
            keyValues = mapOf("Lines" to "${text.lines().size}"),
            tags = listOf("note", "reference"),
            actionType = ActionType.COPY,
            actionLabel = "Copy Note",
            actionPayload = text
        )
    }
}

package com.slothshot.app.triage

object CyrillicDemangler {

    // Common mangled words generated when ML Kit Latin OCR processes Russian Cyrillic
    private val replacements = listOf(
        // Security & OTP
        Regex("(?i)\\bKog\\b") to "Код",
        Regex("(?i)\\bnogTBep[\\w>|<]+[eR]\\b") to "подтверждения",
        Regex("(?i)\\bnapo[лl][\\w]*\\b") to "пароль",
        Regex("(?i)\\bBxo[aд]\\b") to "Вход",
        Regex("(?i)\\bCoobwe[\\w]+\\b") to "Сообщение",
        Regex("(?i)\\bHe coo6wa[\\w]+\\b") to "Не сообщайте",
        Regex("(?i)\\bHukomy\\b") to "Никому",

        // Commerce & Receipts
        Regex("(?i)\\b[MH]roro\\b") to "Итого",
        Regex("(?i)\\bCymma\\b") to "Сумма",
        Regex("(?i)\\b3aka3\\b") to "Заказ",
        Regex("(?i)\\b[Aa]oc[tт]a[bв]ka\\b") to "Доставка",
        Regex("(?i)\\bTpek\\b") to "Трек",
        Regex("(?i)\\bon[nl]a[tт]a\\b") to "Оплата",
        Regex("(?i)\\b[4ч]ek\\b") to "Чек",
        Regex("(?i)\\bpy6\\b") to "руб.",
        Regex("(?i)\\bpy6[лl][\\w]*\\b") to "рублей",
        Regex("(?i)\\bC6ep\\b") to "Сбер",
        Regex("(?i)\\bTuH[\\w]+qq\\b") to "Т-Банк",

        // Wi-Fi & Network
        Regex("(?i)\\bCe[tт]b\\b") to "Сеть",
        Regex("(?i)\\bBaudau\\b") to "Вайфай",

        // Media & Entertainment
        Regex("(?i)\\bKuHono[\\w]+\\b") to "Кинопоиск",
        Regex("(?i)\\bCe3oH\\b") to "Сезон",
        Regex("(?i)\\bCepu[rR]\\b") to "Серия",
        Regex("(?i)\\b6une[tт]\\b") to "Билет",
        Regex("(?i)\\bPe[\\w]*uccep\\b") to "Режиссер",
        Regex("(?i)\\b[Pp]poc[Mm]o[tт]p\\b") to "просмотр"
    )

    fun clean(mangledText: String): String {
        var result = mangledText
        for ((pattern, replacement) in replacements) {
            result = pattern.replace(result, replacement)
        }
        return result
    }
}

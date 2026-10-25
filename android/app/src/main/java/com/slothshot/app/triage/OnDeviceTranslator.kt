package com.slothshot.app.triage

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

class OnDeviceTranslator {
    private val languageIdentifier = LanguageIdentification.getClient()

    suspend fun translateToRussianIfNeeded(text: String): String {
        if (text.isBlank()) return text
        return try {
            val langCode = languageIdentifier.identifyLanguage(text.take(250)).await()
            if (langCode == "ru" || containsCyrillic(text)) {
                return text
            }

            val sourceLang = when (langCode) {
                "en" -> TranslateLanguage.ENGLISH
                "de" -> TranslateLanguage.GERMAN
                "fr" -> TranslateLanguage.FRENCH
                "es" -> TranslateLanguage.SPANISH
                "it" -> TranslateLanguage.ITALIAN
                "zh" -> TranslateLanguage.CHINESE
                else -> TranslateLanguage.ENGLISH
            }

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLang)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build()

            val translator = Translation.getClient(options)
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()

            val translated = translator.translate(text).await()
            translator.close()
            translated
        } catch (e: Exception) {
            text
        }
    }

    private fun containsCyrillic(text: String): Boolean {
        for (char in text) {
            if (char in '\u0400'..'\u04FF') return true
        }
        return false
    }
}

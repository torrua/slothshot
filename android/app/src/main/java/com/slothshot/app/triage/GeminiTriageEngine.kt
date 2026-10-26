package com.slothshot.app.triage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.slothshot.app.model.ActionType
import com.slothshot.app.model.ContentCategory
import com.slothshot.app.model.RetentionClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiTriageEngine(
    private val apiKeyProvider: () -> String,
    private val heuristicFallback: HeuristicTriageEngine = HeuristicTriageEngine()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun triage(
        ocrText: String,
        fileName: String = "",
        context: Context? = null,
        imageUri: Uri? = null
    ): ParsedTakeaway = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isEmpty()) {
            // No API key configured: fallback to heuristic without generating fake Latin gibberish
            val heuristicResult = heuristicFallback.triage(ocrText, fileName)
            return@withContext heuristicResult.copy(
                summary = "${heuristicResult.summary}\n(Для 100% распознавания кириллицы и перевода на русский укажите ключ Gemini в Настройках)"
            )
        }

        try {
            val systemPrompt = """
                Ты — SlothShot AI, интеллектуальный ассистент для анализа и триажа скриншотов смартфона.
                Твоя главная задача — ПОНЯТЬ НАСТОЯЩИЙ ИНТЕНТ (РАДИ ЧЕГО ПОЛЬЗОВАТЕЛЬ СДЕЛАЛ ЭТОТ СКРИНШОТ) и выдать максимально осмысленный, предметный результат СТРОГО БЕЗ ВОДЫ НА РУССКОМ ЯЗЫКЕ.

                ПРАВИЛА ИНТЕНТ-АНАЛИЗА (БЕЗ ШАБЛОНОВ И ВОДЫ):
                1. ПОЙМИ СКРЫТУЮ ЦЕЛЬ ЗАХВАТА ЭКРАНА:
                   Пользователь нажал кнопку скриншота в конкретный момент, чтобы зафиксировать что-то ценное или важное. Выясни что:
                   - Сохранить промокод, скидку или цену товара, чтобы купить позже или сравнить цены?
                   - Зафиксировать ошибку в коде/приложении/системе, номер сбоя, чтобы устранить или отправить в поддержку?
                   - Сохранить одноразовый 2FA код, смс-код или временный пин, который нужен прямо сейчас и больше не потребуется?
                   - Сохранить чек, номер транзакции, квитанцию об оплате или реквизиты перевода как доказательство?
                   - Сохранить билет, бронь отеля, рейс, посадочный талон или адрес места встречи?
                   - Запомнить рекомендацию (фильм, книгу, статью, подкаст, рецепт, цитату)?
                   - Сохранить фрагмент переписки, смешной мем или твит, чтобы переслать друзьям?

                2. СТРОГО БЕЗ ВОДЫ И КЛИШЕ:
                   - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНЫ общие пустые фразы: "На скриншоте изображен...", "Скриншот показывает...", "Пользователь сделал скриншот, чтобы...", "Данное изображение представляет собой...".
                   - Сразу переходи к фактам: конкретные имена, названия, числа, артикулы, условия, выводы.

                3. ЯЗЫК:
                   - Весь вывод СТРОГО на грамотном русском языке. Если скриншот на английском или другом языке — осмысленно переведи суть, термины и факты на русский.

                4. ФОРМАТ ОТВЕТА — СТРОГО ВАЛИДНЫЙ JSON:
                   - "title": Конкретный ёмкий заголовок (3-6 слов) с главным объектом или смыслом (например: "Одноразовый код 7392 для Госуслуг", "Чек 1 450 ₽ из Додо Пицца", "Авиабилет Москва — Сочи SU-102", "Скидка на наушники Sony XM5", "Ошибка 502 Bad Gateway в Nginx", "Фильм 'Оппенгеймер' (рекомендация)").
                   - "summary": 1-2 концентрированных предложения без капли воды. Раскрой цель скриншота и ключевые условия/факты. Не описывай цвет кнопок или дизайн — пиши только предметную суть.
                   - "category": ОДНА из ["TEMPORARY_UTILITY", "TRANSACTIONAL_RECEIPT", "ENTERTAINMENT_MEDIA", "KNOWLEDGE_REFERENCE", "PERSONAL_CHAT", "UNKNOWN"].
                   - "retention": ОДНА из:
                     * "EPHEMERAL_PURGE" (для временных 2FA-кодов, одноразовых статусов и мусора, который надо удалить после извлечения).
                     * "KEEP_ARCHIVE" (для чеков, билетов, важных документов, броней, полезных заметок).
                     * "ACTION_REQUIRED" (требует конкретного действия: оплатить счет, ответить на сообщение, применить промокод до дедлайна, забрать посылку).
                   - "key_values": Словарь из 2-5 важнейших извлеченных параметров (например: {"Сумма": "1 450 ₽", "Заказ": "#4821"}, {"Код": "739211"}, {"Рейс": "SU-102", "Вылет": "15:40"}, {"Промокод": "AUTUMN20", "Скидка": "20%"}, {"Ошибка": "502 Bad Gateway"}).
                   - "tags": 2-5 точных русских тегов в нижнем регистре (например: ["госуслуги", "код", "2fa"] или ["чек", "еда", "додо"] или ["авиа", "билет", "сочи"]).
                   - "action_type": ОДНО из ["COPY", "OPEN_URL", "SEARCH_WEB", "SHARE", "PURGE"].
                   - "action_label": Название целевого действия (например: "Скопировать код 7392", "Скопировать номер заказа", "Искать фильм на Кинопоиске", "Открыть ссылку", "Удалить скриншот").
                   - "action_payload": Точное значение для действия (сам код, номер, название фильма, ссылка или текст ошибки).
                   - "raw_content": Полный распознанный текст со скриншота (если иностранный — оригинальный текст и следом его перевод на русский язык).

                Верни ТОЛЬКО валидный JSON без markdown-оберток.
            """.trimIndent()

            val partsArray = JSONArray()

            // Encode screenshot image to JPEG base64
            var hasImage = false
            if (context != null && imageUri != null) {
                val base64Image = encodeImageToBase64(context, imageUri)
                if (!base64Image.isNullOrBlank()) {
                    partsArray.put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                    hasImage = true
                }
            }

            partsArray.put(JSONObject().apply {
                put("text", "$systemPrompt\n\nИмя файла скриншота: $fileName" + (if (!hasImage && ocrText.isNotBlank()) "\nТекст: $ocrText" else ""))
            })

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.1)
                })
            }

            // Support candidate models with automatic fallback: optimal Flash-Lite first (fastest, lowest token usage), then standard Flash
            val candidateModels = listOf("gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.8-flash")
            var lastErrorCode: String? = null

            for (model in candidateModels) {
                try {
                    val request = Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                        .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    response.use { resp ->
                        if (!resp.isSuccessful) {
                            lastErrorCode = "HTTP ${resp.code}"
                            return@use
                        }

                        val respStr = resp.body?.string().orEmpty()
                        val jsonResp = JSONObject(respStr)
                        val candidates = jsonResp.optJSONArray("candidates")
                        if (candidates == null || candidates.length() == 0) {
                            lastErrorCode = "Пустой ответ модели"
                            return@use
                        }

                        val textContent = candidates.getJSONObject(0)
                            .getJSONObject("content")
                            .getJSONArray("parts")
                            .getJSONObject(0)
                            .getString("text")

                        val cleanJson = textContent.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val parsed = JSONObject(cleanJson)

                        val catStr = parsed.optString("category", "KNOWLEDGE_REFERENCE")
                        val category = runCatching { ContentCategory.valueOf(catStr) }.getOrDefault(ContentCategory.KNOWLEDGE_REFERENCE)

                        val retStr = parsed.optString("retention", "KEEP_ARCHIVE")
                        val retention = runCatching { RetentionClass.valueOf(retStr) }.getOrDefault(RetentionClass.KEEP_ARCHIVE)

                        val actStr = parsed.optString("action_type", "COPY")
                        val actionType = runCatching { ActionType.valueOf(actStr) }.getOrDefault(ActionType.COPY)

                        val kvObj = parsed.optJSONObject("key_values")
                        val kvMap = mutableMapOf<String, String>()
                        kvObj?.keys()?.forEach { k ->
                            val v = kvObj.opt(k)?.toString() ?: kvObj.optString(k)
                            if (v.isNotBlank()) {
                                kvMap[k] = v
                            }
                        }

                        val tagsArr = parsed.optJSONArray("tags")
                        val tagsList = mutableListOf<String>()
                        if (tagsArr != null) {
                            for (i in 0 until tagsArr.length()) {
                                val tag = tagsArr.optString(i).trim().removePrefix("#")
                                if (tag.isNotBlank()) {
                                    tagsList.add(tag)
                                }
                            }
                        }

                        val rawContentResult = parsed.optString("raw_content", "").ifBlank { ocrText }

                        return@withContext ParsedTakeaway(
                            category = category,
                            retention = retention,
                            title = parsed.optString("title", "Скриншот"),
                            summary = parsed.optString("summary", "Разобранный скриншот"),
                            keyValues = kvMap,
                            tags = tagsList,
                            actionType = actionType,
                            actionLabel = parsed.optString("action_label", "Скопировать"),
                            actionPayload = parsed.optString("action_payload", rawContentResult),
                            fullRawContent = rawContentResult
                        )
                    }
                } catch (e: Exception) {
                    lastErrorCode = e.localizedMessage ?: e.message
                }
            }

            // All models in fallback chain failed
            val fallback = heuristicFallback.triage(ocrText, fileName)
            return@withContext fallback.copy(
                summary = "${fallback.summary}\n(Ошибка вызова Gemini: ${lastErrorCode ?: "неизвестно"})"
            )
        } catch (e: Exception) {
            val fallback = heuristicFallback.triage(ocrText, fileName)
            fallback.copy(
                summary = "${fallback.summary}\n(Сбой сети: ${e.localizedMessage ?: "timeout"})"
            )
        }
    }

    private fun encodeImageToBase64(context: Context, uri: Uri, maxDimension: Int = 1024): String? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            var sampleSize = 1
            var w = boundsOptions.outWidth
            var h = boundsOptions.outHeight
            while (w > maxDimension || h > maxDimension) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}

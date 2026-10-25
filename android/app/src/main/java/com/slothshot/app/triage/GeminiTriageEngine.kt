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
                summary = "${heuristicResult.summary}\n(💡 Для 100% распознавания кириллицы и перевода на русский укажите ключ Gemini в Настройках)"
            )
        }

        try {
            val systemPrompt = """
                Ты — SlothShot, интеллектуальный органайзер скриншотов.
                Твоя главная задача — выдать результат НА ЧИСТОМ, ГРАМОТНОМ РУССКОМ ЯЗЫКЕ.

                Инструкции:
                1. Внимательно посмотри на скриншот (изображение).
                2. Если на скриншоте русский текст — распознай его с идеальной точностью (никакой ломаной латиницы!).
                3. Если на скриншоте иностранный язык (английский и др.) — ОБЯЗАТЕЛЬНО ПЕРЕВЕДИ весь смысл и ключевые факты НА РУССКИЙ ЯЗЫК.
                4. Сформируй ответ СТРОГО в формате JSON со следующими полями:
                   - "title": краткий емкий заголовок НА РУССКОМ ЯЗЫКЕ (до 5 слов, например: "Код подтверждения Сбер", "Рекомендация фильма Дюна", "Чек из кофейни", "Пароль от Wi-Fi").
                   - "summary": 1-2 понятных предложения НА РУССКОМ ЯЗЫКЕ, объясняющие, зачем был сделан скриншот и что в нем главного. Если скриншот иностранный — дай краткий перевод его сути.
                   - "category": одна из категорий ["TEMPORARY_UTILITY", "ENTERTAINMENT_MEDIA", "TRANSACTIONAL_RECEIPT", "KNOWLEDGE_REFERENCE", "PERSONAL_CHAT", "UNKNOWN"]
                   - "retention": одна из ["EPHEMERAL_PURGE", "KEEP_ARCHIVE", "ACTION_REQUIRED"] (для одноразовых кодов/паролей выбирай EPHEMERAL_PURGE).
                   - "key_values": объект с ключевыми параметрами НА РУССКОМ ЯЗЫКЕ (например {"Код": "849211"} или {"Сеть": "Guest", "Пароль": "secret"} или {"Сумма": "450 ₽"}).
                   - "tags": список тегов НА РУССКОМ ЯЗЫКЕ (например ["код", "безопасность", "удалить"]).
                   - "action_label": понятное действие на русском (например "Скопировать код", "Искать фильм", "Открыть ссылку").
                   - "action_payload": значение для копирования или поиска (код, пароль, ссылка или название).
                   - "action_type": одно из ["COPY", "OPEN_URL", "SEARCH_WEB", "SHARE", "PURGE"]
                   - "raw_content": полный текст со скриншота на русском языке (если скриншот на иностранном — приведи оригинальный текст и следом его полный перевод на русский язык).

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

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val fallback = heuristicFallback.triage(ocrText, fileName)
                    return@withContext fallback.copy(
                        summary = "${fallback.summary}\n(Ошибка вызова Gemini: HTTP ${response.code})"
                    )
                }

                val respStr = response.body?.string().orEmpty()
                val jsonResp = JSONObject(respStr)
                val candidates = jsonResp.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext heuristicFallback.triage(ocrText, fileName)
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
                    kvMap[k] = kvObj.optString(k)
                }

                val tagsArr = parsed.optJSONArray("tags")
                val tagsList = mutableListOf<String>()
                if (tagsArr != null) {
                    for (i in 0 until tagsArr.length()) {
                        tagsList.add(tagsArr.getString(i))
                    }
                }

                val rawContentResult = parsed.optString("raw_content", "").ifBlank { ocrText }

                ParsedTakeaway(
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

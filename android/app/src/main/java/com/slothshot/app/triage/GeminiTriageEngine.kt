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
            return@withContext heuristicFallback.triage(ocrText, fileName)
        }

        try {
            val systemPrompt = """
                Ты — аналитический модуль SlothShot.
                ТВОЯ ЕДИНСТВЕННАЯ ЗАДАЧА: извлечь сухие факты и полезную информацию из скриншота и структурировать в JSON.

                ЖЕСТКИЕ ПРАВИЛА (СТРОГО БЕЗ ВОДЫ):
                1. КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНЫ любые вводные и описательные клише:
                   - "На снимке изображено..."
                   - "На скриншоте видно..."
                   - "Скриншот показывает..."
                   - "Пользователь сделал скриншот..."
                   - "Данное изображение..."
                   - "Это скриншот..."
                   - "В кадре находится..."
                   Нарушение этого правила считается ошибкой. Сразу пиши конкретные факты и суть!

                2. ЗАПРЕЩЕНО описывать оформление и системный интерфейс: цвет фона/кнопок, статус-бар (батарея, Wi-Fi, время), геометрию экрана.

                3. АНАЛИЗ ИНТЕНТА:
                   Пойми, ради чего сделан скриншот (сохранить чек/квитанцию, код 2FA, промокод, билет/бронь, ошибку/баг, товар, фильм/книгу). Извлекай только то, что имеет прикладную ценность.

                4. СТРОГИЙ ФОРМАТ SUMMARY:
                   Формула: [Объект / суть] + [Ключевые параметры, числа, имена] + [Целевое действие / статус].
                   Максимум 1-2 сжатых предложения. Никаких общих рассуждений.

                5. СТРУКТУРА JSON:
                {
                  "title": "Конкретное название объекта (3-5 слов)",
                  "summary": "Сжатая суть и факты по формуле без капли воды",
                  "category": "TEMPORARY_UTILITY" | "TRANSACTIONAL_RECEIPT" | "ENTERTAINMENT_MEDIA" | "KNOWLEDGE_REFERENCE" | "PERSONAL_CHAT" | "UNKNOWN",
                  "retention": "EPHEMERAL_PURGE" | "KEEP_ARCHIVE" | "ACTION_REQUIRED",
                  "key_values": {
                    "Параметр": "Точное значение"
                  },
                  "tags": ["тег1", "тег2"],
                  "action_type": "COPY" | "OPEN_URL" | "SEARCH_WEB" | "SHARE" | "PURGE",
                  "action_label": "Глагол + объект (например: Скопировать код, Искать товар)",
                  "action_payload": "Точное значение для действия",
                  "raw_content": "Полный распознанный текст со скриншота на русском (при иностранном оригинале — точный русский перевод)"
                }

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
                val userPrompt = buildString {
                    append("Проанализируй скриншот, извлеки факты и полезную информацию, структурируй в JSON.")
                    if (fileName.isNotBlank()) append("\nИмя файла: $fileName")
                    if (!hasImage && ocrText.isNotBlank()) append("\nТекст OCR: $ocrText")
                }
                put("text", userPrompt)
            })

            val requestBodyJson = JSONObject().apply {
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemPrompt)
                        })
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.0)
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

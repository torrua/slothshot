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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
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
                You are SlothShot, an autonomous on-device triage agent for Android screenshots.
                Analyze the screenshot image and/or text.
                Preserve Russian, Cyrillic, or English text accurately in their original script.
                Extract the core universal takeaway in JSON format with these exact fields:
                - title: brief descriptive headline (max 5 words, in the language of the screenshot)
                - summary: clear 1-sentence explanation of why the user took this screenshot
                - category: one of ["TEMPORARY_UTILITY", "ENTERTAINMENT_MEDIA", "TRANSACTIONAL_RECEIPT", "KNOWLEDGE_REFERENCE", "PERSONAL_CHAT", "UNKNOWN"]
                - retention: one of ["EPHEMERAL_PURGE", "KEEP_ARCHIVE", "ACTION_REQUIRED"]
                - key_values: object with 1-4 key-value pairs (e.g. {"Код": "849211"} or {"Password": "secret"})
                - tags: list of 2-4 lowercase strings
                - action_label: short action label (e.g. "Скопировать код", "Search IMDb", "Track Package")
                - action_payload: string payload for the action
                - action_type: one of ["COPY", "OPEN_URL", "SEARCH_WEB", "SHARE", "PURGE"]
                - raw_content: the complete unabbreviated text read from the image in its original language (Russian, English, etc.)

                Return ONLY valid JSON.
            """.trimIndent()

            val partsArray = JSONArray()

            // If image is available, include base64 jpeg for multimodal OCR
            if (context != null && imageUri != null) {
                val base64Image = encodeImageToBase64(context, imageUri)
                if (base64Image != null) {
                    partsArray.put(JSONObject().apply {
                        put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        })
                    })
                }
            }

            partsArray.put(JSONObject().apply {
                put("text", "$systemPrompt\n\nFallback OCR Text from screenshot '$fileName':\n$ocrText")
            })

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("response_mime_type", "application/json")
                    put("temperature", 0.2)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext heuristicFallback.triage(ocrText, fileName)
                }
                val respStr = response.body?.string().orEmpty()
                val jsonResp = JSONObject(respStr)
                val candidates = jsonResp.optJSONArray("candidates") ?: return@withContext heuristicFallback.triage(ocrText, fileName)
                if (candidates.length() == 0) return@withContext heuristicFallback.triage(ocrText, fileName)
                val textContent = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val parsed = JSONObject(textContent.trim().removeSurrounding("```json", "```").trim())

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

                val fullRaw = parsed.optString("raw_content", "").ifBlank { ocrText }

                ParsedTakeaway(
                    category = category,
                    retention = retention,
                    title = parsed.optString("title", "Screenshot Takeaway"),
                    summary = parsed.optString("summary", "Processed screenshot takeaway."),
                    keyValues = kvMap,
                    tags = tagsList,
                    actionType = actionType,
                    actionLabel = parsed.optString("action_label", "Copy"),
                    actionPayload = parsed.optString("action_payload", ocrText),
                    fullRawContent = fullRaw
                )
            }
        } catch (e: Exception) {
            heuristicFallback.triage(ocrText, fileName)
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

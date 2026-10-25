package com.slothshot.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.slothshot.app.triage.CyrillicDemangler
import kotlinx.coroutines.tasks.await

class MlKitOcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(context: Context, uri: Uri): String {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val visionText = recognizer.process(image).await()
            val raw = visionText.text.trim()
            CyrillicDemangler.clean(raw)
        } catch (e: Exception) {
            "OCR extraction failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }
}

package com.slothshot.app.ocr

import android.content.Context
import android.graphics.*
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import com.slothshot.app.triage.CyrillicDemangler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TesseractOcrEngine {

    private fun ensureTessData(context: Context): String {
        val baseDir = File(context.filesDir, "tesseract")
        val tessDataDir = File(baseDir, "tessdata")
        tessDataDir.mkdirs()

        val models = listOf("rus.traineddata", "eng.traineddata")
        for (model in models) {
            val destFile = File(tessDataDir, model)
            if (!destFile.exists() || destFile.length() < 100_000) {
                try {
                    context.assets.open("tessdata/$model").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return baseDir.absolutePath
    }

    suspend fun recognizeText(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        try {
            val dataPath = ensureTessData(context)
            val originalBitmap = loadOptimizedBitmap(context, uri, maxDimension = 2560)
                ?: return@withContext ""

            val isDarkMode = isDarkThemeBitmap(originalBitmap)
            val primaryBitmap = if (isDarkMode) invertBitmap(originalBitmap) else originalBitmap

            val tess = TessBaseAPI()
            val initSuccess = tess.init(dataPath, "rus+eng")
            if (!initSuccess) {
                tess.recycle()
                if (primaryBitmap != originalBitmap) primaryBitmap.recycle()
                originalBitmap.recycle()
                return@withContext ""
            }

            tess.setVariable("preserve_interword_spaces", "1")
            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(primaryBitmap)
            var text = tess.utF8Text.orEmpty().trim()

            // If dark mode inversion gave sparse text (< 20 chars), try the original bitmap as fallback
            if (text.length < 20 && isDarkMode) {
                tess.setImage(originalBitmap)
                val fallbackText = tess.utF8Text.orEmpty().trim()
                if (fallbackText.length > text.length) {
                    text = fallbackText
                }
            }

            tess.recycle()
            if (primaryBitmap != originalBitmap) primaryBitmap.recycle()
            originalBitmap.recycle()

            CyrillicDemangler.clean(text)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun isDarkThemeBitmap(bitmap: Bitmap): Boolean {
        // Fast sample 64 grid points to determine average luminance
        var totalLuma = 0L
        var count = 0
        val stepX = (bitmap.width / 8).coerceAtLeast(1)
        val stepY = (bitmap.height / 8).coerceAtLeast(1)

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Standard Rec. 601 luma formula
                val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalLuma += luma
                count++
            }
        }
        val avgLuma = if (count > 0) totalLuma / count else 255
        return avgLuma < 128
    }

    private fun invertBitmap(source: Bitmap): Bitmap {
        val inverted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inverted)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            )))
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return inverted
    }

    private fun loadOptimizedBitmap(context: Context, uri: Uri, maxDimension: Int = 2560): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            var sampleSize = 1
            var w = options.outWidth
            var h = options.outHeight
            while (w > maxDimension || h > maxDimension) {
                sampleSize *= 2
                w /= 2
                h /= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }
}

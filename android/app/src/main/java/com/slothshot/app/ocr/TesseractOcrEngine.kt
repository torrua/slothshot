package com.slothshot.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TesseractOcrEngine {

    private var isInitialized = false
    private val initLock = Any()

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
            val bitmap = loadOptimizedBitmap(context, uri) ?: return@withContext ""

            val tess = TessBaseAPI()
            val initSuccess = tess.init(dataPath, "rus+eng")
            if (!initSuccess) {
                tess.recycle()
                return@withContext ""
            }

            tess.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            tess.setImage(bitmap)
            val text = tess.utF8Text.orEmpty().trim()
            tess.recycle()
            bitmap.recycle()

            text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun loadOptimizedBitmap(context: Context, uri: Uri, maxDimension: Int = 1800): Bitmap? {
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

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }
}

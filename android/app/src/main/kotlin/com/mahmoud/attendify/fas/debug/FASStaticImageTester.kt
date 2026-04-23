package com.mahmoud.attendify.fas.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mahmoud.attendify.fas.models.BaseFASModel
import com.mahmoud.attendify.fas.core.FASResult
import java.io.InputStream

/**
 * FASStaticImageTester
 *
 * Arabic:
 * أداة اختبار static لنماذج Anti‑Spoofing
 * - بدون كاميرا
 * - بدون Flutter
 * - متوافقة تمامًا مع FASModel و FASResult
 */
object FASStaticImageTester {

    private const val TAG = "FAS_TEST"

    fun runTest(
        context: Context,
        model: BaseFASModel,
        assetImagePath: String,
        useGpu: Boolean
    ) {
        Log.d(TAG, "--------------------------------------------------")
        Log.d(
            TAG,
            "Model=${model.id} | GPU=$useGpu | Image=$assetImagePath"
        )

        val bitmap = loadBitmapFromAssets(context, assetImagePath)
        if (bitmap == null) {
            Log.e(TAG, "❌ Failed to load image: $assetImagePath")
            Log.d(TAG, "--------------------------------------------------")
            return
        }

        try {
            // ✅ تهيئة النموذج
            model.prepare(useGpu)

            // ✅ تحليل الصورة
            val result = model.analyze(bitmap)

            // ✅ طباعة النتيجة
            logResult(result)

        } catch (e: Exception) {
            Log.e(
                TAG,
                "❌ Exception while running model ${model.id}",
                e
            )
        }

        Log.d(TAG, "--------------------------------------------------")
    }

    private fun loadBitmapFromAssets(
        context: Context,
        path: String
    ): Bitmap? {
        return try {
            val inputStream: InputStream = context.assets.open(path)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load asset image: $path", e)
            null
        }
    }

    /**
     * تفسير نتيجة الـ FAS بشكل صحيح باستخدام sealed class
     */
    private fun logResult(result: FASResult) {
        when (result) {
            is FASResult.Real -> {
                Log.d(
                    TAG,
                    "✅ REAL detected | confidence=${result.confidence}"
                )
            }
            is FASResult.Spoof -> {
                Log.d(
                    TAG,
                    "❌ SPOOF detected | confidence=${result.confidence}"
                )
            }
            is FASResult.Inconclusive -> {
                Log.w(
                    TAG,
                    "⚠️ INCONCLUSIVE | reason=${result.reason}"
                )
            }
        }
    }
}

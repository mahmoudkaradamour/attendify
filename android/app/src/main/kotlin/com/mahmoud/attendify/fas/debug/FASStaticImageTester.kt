package com.mahmoud.attendify.fas.debug

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import java.io.InputStream

/**
 * FASStaticImageTester
 *
 * ✅ اختبار نماذج FAS باستخدام صور ثابتة من assets
 * ✅ مفصول تمامًا عن الكاميرا وFlutter
 */
object FASStaticImageTester {

    fun runTest(
        context: Context,
        model: FASModel,
        assetImagePath: String,
        useGpu: Boolean
    ) {
        Log.d(
            "FAS_TEST",
            "--------------------------------------------------"
        )
        Log.d(
            "FAS_TEST",
            "Model=${model.id} | GPU=$useGpu | Image=$assetImagePath"
        )

        val bitmap = loadBitmapFromAssets(context, assetImagePath)
            ?: run {
                Log.e("FAS_TEST", "❌ Failed to load image")
                return
            }

        // 1️⃣ Prepare model (CPU / GPU)
        model.prepare(useGpu)

        // 2️⃣ Run inference
        val result = model.analyze(bitmap)

        when (result) {
            is FASResult.Real -> {
                Log.d(
                    "FAS_TEST",
                    "✅ REAL detected | confidence=${result.confidence}"
                )
            }
            is FASResult.Spoof -> {
                Log.d(
                    "FAS_TEST",
                    "❌ SPOOF detected | confidence=${result.confidence}"
                )
            }
            is FASResult.Inconclusive -> {
                Log.d(
                    "FAS_TEST",
                    "⚠️ INCONCLUSIVE | reason=${result.reason}"
                )
            }
        }
    }

    private fun loadBitmapFromAssets(
        context: Context,
        path: String
    ): Bitmap? {
        return try {
            val inputStream: InputStream =
                context.assets.open(path)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
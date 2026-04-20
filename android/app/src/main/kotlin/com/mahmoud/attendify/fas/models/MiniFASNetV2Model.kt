package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.metrics.FasRuntimeMetrics
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * MiniFASNetV2Model
 *
 * ⚠️ نسخة اختبار (DEBUG SAFE)
 * ✅ تمنع الانهيار
 * ✅ تقيس زمن inference
 * ❌ ليست التنفيذ النهائي للعقد
 */
class MiniFASNetV2Model(
    context: Context
) : BaseFASModel(context), FASModel {

    override val id: String = "minifasnet_v2_80x80_default"
    override val inputSize: Int = 80
    override val defaultThreshold: Float = 0.85f
    override val supportsGpu: Boolean = false

    init {
        loadModel("models/fas/minifasnet_v2_80x80_default.tflite")
    }

    override fun analyze(faceBitmap: Bitmap): FASResult {

        /* =========================================================
         * 1️⃣ Resize
         * ========================================================= */
        val resized = resizeBitmap(faceBitmap, inputSize)

        /* =========================================================
         * 2️⃣ Create input buffer
         * ========================================================= */
        val inputBuffer = ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(
            pixels,
            0,
            inputSize,
            0,
            0,
            inputSize,
            inputSize
        )

        // Normalization بسيط (مؤقت – للاختبار فقط)
        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        /* =========================================================
         * 3️⃣ Run inference (Safe Output Handling)
         * ========================================================= */
        val startNs = System.nanoTime()

        return try {
            // ✅ قراءة حجم الإخراج ديناميكيًا
            val outputShape = interpreter.getOutputTensor(0).shape()
            val outputSize = outputShape.last()

            val output = Array(1) { FloatArray(outputSize) }
            interpreter.run(inputBuffer, output)

            val inferenceMs =
                (System.nanoTime() - startNs) / 1_000_000

            FasRuntimeMetrics.log(
                modelId = id,
                useGpu = false,
                stage = "inference",
                durationMs = inferenceMs
            )

            /* =====================================================
             * 4️⃣ تفسير بسيط لمنع الانهيار (DEBUG ONLY)
             * ===================================================== */
            val spoofLogit = output[0][0]
            val realLogit =
                if (outputSize > 1) output[0][1] else output[0][0]

            val expSpoof = exp(spoofLogit)
            val expReal = exp(realLogit)
            val sum = expSpoof + expReal

            if (sum.isNaN() || sum == 0.0f) {
                FASResult.Inconclusive(
                    "Invalid softmax output"
                )
            } else {
                val confidence = expReal / sum
                if (confidence >= defaultThreshold) {
                    FASResult.Real(confidence)
                } else {
                    FASResult.Spoof(confidence)
                }
            }

        } catch (e: Exception) {
            Log.e(
                "MiniFASNetV2",
                "Safe failure during inference",
                e
            )
            FASResult.Inconclusive(
                "MiniFASNetV2 inference failed: ${e.message}"
            )
        }
    }
}
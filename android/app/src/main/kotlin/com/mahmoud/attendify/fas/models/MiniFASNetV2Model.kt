package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.metrics.FasRuntimeMetrics
import kotlin.math.exp

/**
 * MiniFASNetV2Model
 *
 * Default Secure Passive Face Anti‑Spoofing model.
 *
 * Architecture:
 * - MiniFASNet V2 (Mobile‑optimized)
 *
 * Input:
 * - [1, 80, 80, 3] RGB
 * - Float32
 * - Normalization: [-1, 1]
 *
 * Output:
 * - [1, 2] logits
 *   [0] = Spoof
 *   [1] = Real
 *
 * Role:
 * - Secure‑by‑default model
 * - Balanced accuracy vs latency
 */
class MiniFASNetV2Model(
    context: Context
) : BaseFASModel(context), FASModel {

    override val id: String =
        "minifasnet_v2_80x80_default"

    override val inputSize: Int = 80

    /**
     * Default threshold for production usage.
     * Can be overridden via policy.
     */
    override val defaultThreshold: Float = 0.85f

    /**
     * MiniFASNetV2 does NOT significantly benefit from GPU.
     */
    override val supportsGpu: Boolean = false

    init {
        // ✅ تحميل النموذج مرة واحدة
        loadModel("models/fas/minifasnet_v2_80x80_default.tflite")
    }

    override fun analyze(faceBitmap: Bitmap): FASResult {

        // --------------------------------------------------
        // 1️⃣ Resize face
        // --------------------------------------------------
        val resized = resizeBitmap(faceBitmap, inputSize)
        val inputBuffer = createInputBuffer(inputSize)

        // --------------------------------------------------
        // 2️⃣ Normalization [-1, 1]
        // --------------------------------------------------
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

        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8  and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel        and 0xFF) - 127.5f) / 127.5f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        // --------------------------------------------------
        // 3️⃣ Inference
        // --------------------------------------------------
        val output = Array(1) { FloatArray(2) }

        val startInferenceNs = System.nanoTime()

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            return FASResult.Inconclusive(
                reason = "MiniFASNetV2 inference failed: ${e.message}"
            )
        }

        val inferenceMs =
            (System.nanoTime() - startInferenceNs) / 1_000_000

        // ✅ تسجيل زمن inference
        FasRuntimeMetrics.log(
            modelId = id,
            useGpu = false,
            stage = "inference",
            durationMs = inferenceMs
        )

        // --------------------------------------------------
        // 4️⃣ Softmax (logits → probability)
        // --------------------------------------------------
        val spoofLogit = output[0][0]
        val realLogit  = output[0][1]

        val expSpoof = exp(spoofLogit)
        val expReal  = exp(realLogit)
        val sum = expSpoof + expReal

        if (sum.isNaN() || sum == 0.0f) {
            return FASResult.Inconclusive(
                reason = "Invalid softmax output"
            )
        }

        val realProbability = expReal / sum

        // --------------------------------------------------
        // 5️⃣ Decision
        // --------------------------------------------------
        return if (realProbability >= defaultThreshold) {
            FASResult.Real(confidence = realProbability)
        } else {
            FASResult.Spoof(confidence = realProbability)
        }
    }
}
package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import com.mahmoud.attendify.metrics.FasRuntimeMetrics
import kotlin.math.exp

/**
 * MiniFASNetV1SEModel
 *
 * Lightweight Passive Face Anti‑Spoofing model.
 *
 * Architecture:
 * - MiniFASNet V1 + Squeeze‑and‑Excitation
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
 * Usage:
 * - Low‑end devices
 * - Factories / high throughput scenarios
 */
class MiniFASNetV1SEModel(
    context: Context
) : BaseFASModel(context), FASModel {

    override val id: String =
        "minifasnet_v1se_80x80_light"

    override val inputSize: Int = 80

    /**
     * Lower threshold compared to V2.
     * Optimized for speed over maximum security.
     */
    override val defaultThreshold: Float = 0.80f

    /**
     * GPU is not required and not recommended
     * for this lightweight model.
     */
    override val supportsGpu: Boolean = false



    init {
        loadModel("models/fas/minifasnet_v1se_80x80_light.tflite")
    }


    override fun analyze(faceBitmap: Bitmap): FASResult {

        val resized = resizeBitmap(faceBitmap, inputSize)
        val inputBuffer = createInputBuffer(inputSize)

        /**
         * MiniFASNet normalization: [-1, 1]
         */
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

        val output = Array(1) { FloatArray(2) }
        val startInferenceNs = System.nanoTime()
        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            return FASResult.Inconclusive(
                reason = "MiniFASNetV1SE inference failed: ${e.message}"
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
        val spoofLogit = output[0][0]
        val realLogit  = output[0][1]

        /**
         * Softmax
         */
        val expSpoof = exp(spoofLogit)
        val expReal  = exp(realLogit)
        val sum = expSpoof + expReal

        if (sum.isNaN() || sum == 0.0f) {
            return FASResult.Inconclusive(
                reason = "Invalid softmax output"
            )
        }

        val realProbability = expReal / sum

        return if (realProbability >= defaultThreshold) {
            FASResult.Real(confidence = realProbability)
        } else {
            FASResult.Spoof(confidence = realProbability)
        }
    }
}
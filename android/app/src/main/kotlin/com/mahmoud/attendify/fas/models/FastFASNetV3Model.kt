package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import kotlin.math.exp

/**
 * FastFASNetV3Model
 *
 * High‑Security Passive Face Anti‑Spoofing model.
 *
 * Architecture:
 * - MobileNetV3‑Large backbone
 * - Optimized for ARM (Hard‑Swish)
 *
 * Input:
 * - [1, 128, 128, 3] RGB
 * - Float32
 * - ImageNet normalization (PyTorch)
 *
 * Output:
 * - [1, 2] logits
 *   [0] = Spoof
 *   [1] = Real
 */
class FastFASNetV3Model(
    context: Context
) : BaseFASModel(context), FASModel {

    override val id: String =
        "fastfasnet_v3_128x128_highsec"

    override val inputSize: Int = 128

    /**
     * High‑security default threshold.
     * Can be overridden by policy.
     */
    override val defaultThreshold: Float = 0.90f

    /**
     * This model benefits significantly from GPU / NNAPI,
     * but does not require it.
     */
    override val supportsGpu: Boolean = true

    init {
        loadModel("models/fas/fastfasnet_v3_128x128_highsec.tflite")
    }

    override fun analyze(faceBitmap: Bitmap): FASResult {

        val resized = resizeBitmap(faceBitmap, inputSize)
        val inputBuffer = createInputBuffer(inputSize)

        /**
         * PyTorch ImageNet normalization
         */
        val IMAGE_MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        val IMAGE_STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

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
            val r = ((pixel shr 16 and 0xFF) / 255.0f - IMAGE_MEAN[0]) / IMAGE_STD[0]
            val g = ((pixel shr 8  and 0xFF) / 255.0f - IMAGE_MEAN[1]) / IMAGE_STD[1]
            val b = ((pixel        and 0xFF) / 255.0f - IMAGE_MEAN[2]) / IMAGE_STD[2]

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        val output = Array(1) { FloatArray(2) }

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            return FASResult.Inconclusive(
                reason = "FastFASNet inference failed: ${e.message}"
            )
        }

        val spoofLogit = output[0][0]
        val realLogit  = output[0][1]

        /**
         * Softmax (logits → probability)
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
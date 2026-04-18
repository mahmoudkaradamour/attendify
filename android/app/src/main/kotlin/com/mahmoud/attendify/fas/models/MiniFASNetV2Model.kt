package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.fas.core.FASResult
import kotlin.math.exp

class MiniFASNetV2Model(
    context: Context
) : BaseFASModel(context), FASModel {

    override val id: String =
        "minifasnet_v2_80x80_default"

    override val inputSize: Int = 80

    override val defaultThreshold: Float = 0.85f

    override val supportsGpu: Boolean = false



    init {
        loadModel("models/fas/minifasnet_v2_80x80_default.tflite")
    }


    override fun analyze(faceBitmap: Bitmap): FASResult {

        val resized = resizeBitmap(faceBitmap, inputSize)
        val inputBuffer = createInputBuffer(inputSize)

        // ✅ MiniFASNet normalization: [-1, 1]
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(
            pixels, 0, inputSize, 0, 0, inputSize, inputSize
        )

        for (pixel in pixels) {
            val r = ((pixel shr 16 and 0xFF) - 127.5f) / 127.5f
            val g = ((pixel shr 8 and 0xFF) - 127.5f) / 127.5f
            val b = ((pixel and 0xFF) - 127.5f) / 127.5f

            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        val output = Array(1) { FloatArray(2) }

        try {
            interpreter.run(inputBuffer, output)
        } catch (e: Exception) {
            return FASResult.Inconclusive(
                reason = "Inference failed: ${e.message}"
            )
        }

        val spoofLogit = output[0][0]
        val realLogit = output[0][1]

        // ✅ Softmax
        val eSpoof = exp(spoofLogit)
        val eReal = exp(realLogit)
        val sum = eSpoof + eReal

        val realScore = (eReal / sum)

        return if (realScore >= defaultThreshold) {
            FASResult.Real(confidence = realScore)
        } else {
            FASResult.Spoof(confidence = realScore)
        }
    }
}

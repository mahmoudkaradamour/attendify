package com.mahmoud.attendify.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * MobileFaceNet (INT8)
 *
 * Arabic:
 * - يولّد Face Embedding باستخدام نموذج INT8
 * - يتعامل مع output بالشكل الصحيح [1,128]
 * - يُجري Dequantization إلى Float
 *
 * English:
 * - Generates face embeddings using INT8 model
 * - Correctly handles [1,128] output tensor
 */
class MobileFaceNet(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 112
    private val embeddingSize = 128

    init {
        val model = loadModel(context)

        val options = Interpreter.Options().apply {
            setNumThreads(
                Runtime.getRuntime()
                    .availableProcessors()
                    .coerceAtMost(4)
            )
        }

        interpreter = Interpreter(model, options)
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val fileDescriptor =
            context.assets.openFd("models/mobilefacenet_int8.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val channel = inputStream.channel

        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Generate face embedding (FloatArray[128])
     */
    fun getEmbedding(face: Bitmap): FloatArray {

        // ---------------- Input (INT8) ----------------

        val resized =
            Bitmap.createScaledBitmap(face, inputSize, inputSize, true)

        val inputBuffer =
            ByteBuffer.allocateDirect(inputSize * inputSize * 3)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)

                inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
                inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
                inputBuffer.put((pixel and 0xFF).toByte())
            }
        }

        // ---------------- Output (INT8) ----------------
        // ✅ الشكل الصحيح: [1,128]
        val outputBuffer =
            Array(1) { ByteArray(embeddingSize) }

        interpreter.run(inputBuffer, outputBuffer)

        // ---------------- Dequantization ----------------

        val outputTensor =
            interpreter.getOutputTensor(0)
        val quantParams =
            outputTensor.quantizationParams()

        val scale = quantParams.scale
        val zeroPoint = quantParams.zeroPoint

        val embedding = FloatArray(embeddingSize)

        for (i in 0 until embeddingSize) {
            embedding[i] =
                (outputBuffer[0][i] - zeroPoint) * scale
        }

        return embedding
    }
}
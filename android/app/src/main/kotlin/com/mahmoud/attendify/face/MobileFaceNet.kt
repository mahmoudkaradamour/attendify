package com.mahmoud.attendify.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

import android.graphics.Canvas
import android.graphics.Matrix

/**
 * MobileFaceNet
 *
 * يولّد Face Embedding (128D) بأداء عالي:
 * - Zero Allocation أثناء التشغيل
 * - بدون ضغط GC
 * - جاهز لإضافة GPU Delegate لاحقًا
 */
class MobileFaceNet(context: Context) {

    private val inputSize = 112
    private val embeddingSize = 128

    private val interpreter: Interpreter

    /* ===== Buffers مُعاد استخدامها ===== */

    private val intValues = IntArray(inputSize * inputSize)

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())

    private val outputBuffer =
        Array(1) { ByteArray(embeddingSize) }

    init {
        val model = loadModel(context, "models/mobilefacenet_int8.tflite")
        interpreter = Interpreter(model)
    }

    private val resizedBitmap =
        Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)

    private val canvas = Canvas(resizedBitmap)
    private val matrix = Matrix()

    /**
     * يولّد embedding للوجه
     */
    fun getEmbedding(face: Bitmap): FloatArray {

        val resized = scaleBitmapFast(face)
        val buffer = convertBitmapToBuffer(resized)
        interpreter.run(buffer, outputBuffer)

        return dequantize(outputBuffer[0])
    }

    /* ===== Helpers ===== */

    private fun convertBitmapToBuffer(bitmap: Bitmap): ByteBuffer {

        inputBuffer.rewind()

        bitmap.getPixels(
            intValues,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )

        for (pixel in intValues) {
            inputBuffer.put(((pixel shr 16) and 0xFF).toByte())
            inputBuffer.put(((pixel shr 8) and 0xFF).toByte())
            inputBuffer.put((pixel and 0xFF).toByte())
        }

        return inputBuffer
    }

    private fun dequantize(bytes: ByteArray): FloatArray {

        val params = interpreter.getOutputTensor(0).quantizationParams()
        val scale = params.scale
        val zeroPoint = params.zeroPoint

        val embedding = FloatArray(embeddingSize)

        for (i in 0 until embeddingSize) {
            embedding[i] = (bytes[i] - zeroPoint) * scale
        }

        return embedding
    }

    private fun loadModel(
        context: Context,
        filename: String
    ): MappedByteBuffer {

        val fd = context.assets.openFd(filename)
        val stream = FileInputStream(fd.fileDescriptor)
        val channel = stream.channel

        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }


    /**
     * scaleBitmapFast
     *
     * ✅ إعادة استخدام Bitmap واحد فقط
     * ✅ بدون أي Memory Allocation
     */
    private fun scaleBitmapFast(source: Bitmap): Bitmap {

        matrix.reset()

        val scaleX = inputSize.toFloat() / source.width
        val scaleY = inputSize.toFloat() / source.height

        matrix.setScale(scaleX, scaleY)

        canvas.drawBitmap(source, matrix, null)

        return resizedBitmap
    }
}

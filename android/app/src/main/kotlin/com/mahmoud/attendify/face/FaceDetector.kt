package com.mahmoud.attendify.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * FaceDetector (BlazeFace)
 *
 * Arabic:
 * كاشف وجه خفيف (BlazeFace)
 * - مهيأ لاختبارات الصور الثابتة
 * - Threshold منخفض لتقبّل الصور المطبوعة / الشاشة
 *
 * English:
 * Lightweight BlazeFace detector
 * - Tuned for static image testing
 * - Lower threshold for printed/screen faces
 */
class FaceDetector(context: Context) {

    companion object {
        private const val TAG = "FACE_DETECT"
    }

    private val interpreter: Interpreter

    /** BlazeFace input size */
    private val inputSize = 128

    /**
     * Arabic:
     * Threshold منخفض جدًا للاختبارات الثابتة
     *
     * English:
     * Low threshold for static image detection
     */
    private val scoreThreshold = 0.30f

    init {
        val model = loadModel(context)
        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
        }
        interpreter = Interpreter(model, options)
    }

    private fun loadModel(context: Context): MappedByteBuffer {
        val fd = context.assets.openFd("models/face_detection.tflite")
        val inputStream = fd.createInputStream()
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    /**
     * Normalize image to [-1,1] Float32
     */
    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = resized.getPixel(x, y)
                buffer.putFloat(((pixel shr 16 and 0xFF) - 128) / 128f)
                buffer.putFloat(((pixel shr 8 and 0xFF) - 128) / 128f)
                buffer.putFloat(((pixel and 0xFF) - 128) / 128f)
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * Detect the best face and return pixel bounding box
     */
    fun detectBestFace(bitmap: Bitmap): FaceDetection? {

        val inputBuffer = prepareInputBuffer(bitmap)

        val locations = Array(1) { Array(896) { FloatArray(16) } }
        val scores = Array(1) { Array(896) { FloatArray(1) } }

        interpreter.runForMultipleInputsOutputs(
            arrayOf(inputBuffer),
            mapOf(0 to locations, 1 to scores)
        )

        var bestIndex = -1
        var bestScore = 0f

        for (i in scores[0].indices) {
            val score = scores[0][i][0]
            if (score > scoreThreshold && score > bestScore) {
                bestScore = score
                bestIndex = i
            }
        }

        if (bestIndex == -1) {
            Log.w(TAG, "No face passed threshold=$scoreThreshold")
            return null
        }

        val raw = locations[0][bestIndex]

        // BlazeFace format: center_x, center_y, width, height (normalized)
        val cx = raw[0] * bitmap.width
        val cy = raw[1] * bitmap.height
        val w = raw[2] * bitmap.width
        val h = raw[3] * bitmap.height

        val left = (cx - w / 2).toInt().coerceAtLeast(0)
        val top = (cy - h / 2).toInt().coerceAtLeast(0)
        val right = (cx + w / 2).toInt().coerceAtMost(bitmap.width)
        val bottom = (cy + h / 2).toInt().coerceAtMost(bitmap.height)

        val box = Rect(left, top, right, bottom)

        Log.d(TAG, "Face detected: score=$bestScore box=$box")

        return FaceDetection(
            score = bestScore,
            box = box
        )
    }
}

data class FaceDetection(
    val score: Float,
    val box: Rect
)

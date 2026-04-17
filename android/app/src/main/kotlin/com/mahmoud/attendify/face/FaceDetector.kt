package com.mahmoud.attendify.face
//
import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * FaceDetector (BlazeFace)
 *
 * يكشف الوجوه ويُرجع أفضل وجه (الأقرب للكاميرا)
 * مع أداء عالي وبدون ضغط GC
 */
class FaceDetector(context: Context) {

    private val interpreter: Interpreter

    private val inputSize = 128
    private val minConfidence = 0.6f

    /* ===== Buffers مُعاد استخدامها ===== */

    private val intValues = IntArray(inputSize * inputSize)

    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

    init {
        val model = loadModel(context, "models/face_detection.tflite")
        interpreter = Interpreter(model)
    }

    /**
     * يرجع أفضل وجه (أكبر مساحة مع ثقة كافية)
     */
    fun detectBestFace(bitmap: Bitmap): FaceDetection? {

        val resized =
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val buffer = prepareInputBuffer(resized)

        val locations =
            Array(1) { Array(896) { FloatArray(16) } }
        val scores =
            Array(1) { Array(896) { FloatArray(1) } }

        interpreter.runForMultipleInputsOutputs(
            arrayOf(buffer),
            mapOf(
                0 to locations,
                1 to scores
            )
        )

        var best: FaceDetection? = null
        var bestArea = 0f

        for (i in scores[0].indices) {

            val score = scores[0][i][0]
            if (score < minConfidence) continue

            val box = locations[0][i]
            val area = box[2] * box[3]

            if (area > bestArea) {
                bestArea = area
                best = FaceDetection(score, box.copyOfRange(0, 4))
            }
        }

        return best
    }

    /* ===== Helpers ===== */

    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {

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
            inputBuffer.putFloat((((pixel shr 16) and 0xFF) - 128f) / 128f)
            inputBuffer.putFloat((((pixel shr 8) and 0xFF) - 128f) / 128f)
            inputBuffer.putFloat(((pixel and 0xFF) - 128f) / 128f)
        }

        return inputBuffer
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
}

/**
 * FaceDetection
 *
 * ليست data class عمداً
 */
class FaceDetection(
    val score: Float,
    val box: FloatArray // [x, y, w, h]
)
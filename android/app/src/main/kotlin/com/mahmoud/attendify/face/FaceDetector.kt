package com.mahmoud.attendify.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * FaceDetector (BlazeFace)
 *
 * Arabic:
 * كلاس مسؤول عن:
 * - تحميل نموذج BlazeFace
 * - تحويل صورة الكاميرا إلى input مناسب للنموذج
 * - تشغيل Face Detection
 *
 * English:
 * Responsible for:
 * - Loading BlazeFace model
 * - Preparing input tensor
 * - Running face detection inference
 */
class FaceDetector(context: Context) {

    /**
     * TensorFlow Lite interpreter
     *
     * Arabic:
     * المشغّل الذي ينفّذ النموذج العصبي
     *
     * English:
     * Executes the neural network model
     */
    private val interpreter: Interpreter

    /**
     * inputSize
     *
     * Arabic:
     * حجم الصورة الذي يتطلبه نموذج BlazeFace
     * النموذج لا يهتم بدقة الكاميرا الأصلية
     *
     * English:
     * Required input resolution for BlazeFace model
     */
    private val inputSize = 128

    /**
     * scoreThreshold
     *
     * Arabic:
     * حدّ الثقة (Confidence Threshold)
     * أي كشف أقل من هذه القيمة سيُهمل
     *
     * English:
     * Confidence threshold for detections
     */
    private val scoreThreshold = 0.85f

    /**
     * maxFaces
     *
     * Arabic:
     * حد أقصى نظري للوجوه التي نسمح بإحصائها
     * (للأمان ومنع الضوضاء)
     *
     * English:
     * Maximum allowed detected faces
     */
    private val maxFaces = 1

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

    /**
     * loadModel
     *
     * Arabic:
     * تحميل ملف النموذج من assets
     *
     * English:
     * Load TFLite model from assets
     */
    private fun loadModel(context: Context): MappedByteBuffer {
        val fileDescriptor =
            context.assets.openFd("models/face_detection.tflite")
        val inputStream = fileDescriptor.createInputStream()
        val channel = inputStream.channel

        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * prepareInputBuffer
     *
     * Arabic:
     * تحويل Bitmap إلى Float32 tensor
     * مع Normalization مناسب لنموذج BlazeFace
     *
     * English:
     * Convert bitmap into Float32 input tensor
     */
    private fun prepareInputBuffer(bitmap: Bitmap): ByteBuffer {

        val resized =
            Bitmap.createScaledBitmap(
                bitmap,
                inputSize,
                inputSize,
                true
            )

        val buffer = ByteBuffer.allocateDirect(
            inputSize * inputSize * 3 * 4
        )
        buffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {

                val pixel = resized.getPixel(x, y)

                buffer.putFloat(
                    ((pixel shr 16 and 0xFF) - 128) / 128f
                )
                buffer.putFloat(
                    ((pixel shr 8 and 0xFF) - 128) / 128f
                )
                buffer.putFloat(
                    ((pixel and 0xFF) - 128) / 128f
                )
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * detectFaces
     *
     * Arabic:
     * تشغيل النموذج وإرجاع:
     * 0 = لا يوجد وجه
     * 1 = يوجد وجه واحد مستقر
     *
     * English:
     * Run inference and return:
     * 0 = no face
     * 1 = face detected
     */
    fun detectBestFace(bitmap: Bitmap): FaceDetection? {

        val inputBuffer = prepareInputBuffer(bitmap)

        val locations =
            Array(1) { Array(896) { FloatArray(16) } }

        val scores =
            Array(1) { Array(896) { FloatArray(1) } }

        val outputMap = mapOf(
            0 to locations,   // regressors
            1 to scores       // classifiers
        )

        interpreter.runForMultipleInputsOutputs(
            arrayOf(inputBuffer),
            outputMap
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

        if (bestIndex == -1) return null

        // أول 4 قيم تمثل Bounding Box
        val rawBox = locations[0][bestIndex].copyOfRange(0, 4)

        return FaceDetection(
            score = bestScore,
            box = rawBox
        )
    }
}

data class FaceDetection(
    val score: Float,
    val box: FloatArray   // [x, y, w, h]
)

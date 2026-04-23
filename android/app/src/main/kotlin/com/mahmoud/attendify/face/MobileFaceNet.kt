package com.mahmoud.attendify.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * MobileFaceNet
 *
 * يولّد Face Embedding (Dimension يتم اكتشافه من Tensor).
 *
 * ✅ Zero Allocation أثناء التشغيل (Buffers + Bitmap مُعاد استخدامها)
 * ✅ Dequantization صحيح لـ INT8 و UINT8
 * ✅ L2 Normalization إلزامية
 * ✅ Contract logging مرة واحدة فقط
 *
 * ⚠️ هذا الكلاس يفترض أن النموذج Quantized (INT8 / UINT8)
 */
class MobileFaceNet(context: Context) {

    companion object {
        private const val TAG = "MobileFaceNet"
        private const val MODEL_PATH = "models/mobilefacenet_int8.tflite"

        @Volatile
        private var contractLogged = false
    }

    /** MobileFaceNet input size ثابت */
    private val inputSize = 112

    private val interpreter: Interpreter

    /** embedding size يتم اكتشافه ديناميكيًا */
    private val embeddingSize: Int

    /* ===== Buffers مُعاد استخدامها (No GC Pressure) ===== */

    private val intValues = IntArray(inputSize * inputSize)

    // input tensor: UINT8 / INT8 => 1 byte لكل قناة
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(inputSize * inputSize * 3)
            .order(ByteOrder.nativeOrder())

    // output tensor: quantized embeddings
    private val outputBuffer: Array<ByteArray>

    // Bitmap + Canvas + Matrix لإعادة الاستخدام
    private val resizedBitmap =
        Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)

    private val canvas = Canvas(resizedBitmap)
    private val matrix = Matrix()

    init {
        val model = loadModel(context, MODEL_PATH)
        interpreter = Interpreter(model)

        // اكتشاف حجم الإخراج الحقيقي
        val outShape = interpreter.getOutputTensor(0).shape()
        embeddingSize = outShape.last()

        outputBuffer = Array(1) { ByteArray(embeddingSize) }

        // طباعة العقد مرة واحدة فقط
        logContractOnce()
    }

    /**
     * يولّد embedding للوجه
     *
     * ⚠️ الصورة يجب أن تكون Face Crop بالفعل
     */
    fun getEmbedding(face: Bitmap): FloatArray {
        val resized = scaleBitmapFast(face)
        val input = convertBitmapToBuffer(resized)
        interpreter.run(input, outputBuffer)
        return dequantizeAndNormalize(outputBuffer[0])
    }

    /* ====================================================================== */
    /* =============================== Helpers =============================== */
    /* ====================================================================== */

    /**
     * تحويل Bitmap إلى ByteBuffer مطابق لنموذج INT8 / UINT8
     * (RGB bytes مباشرة 0..255)
     */
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

    /**
     * Dequantization + L2 Normalization
     *
     * ✅ يدعم INT8 و UINT8
     * ✅ يمنع نتائج مطابقة وهمية
     */
    private fun dequantizeAndNormalize(bytes: ByteArray): FloatArray {
        val outTensor = interpreter.getOutputTensor(0)
        val params = outTensor.quantizationParams()
        val scale = params.scale
        val zeroPoint = params.zeroPoint
        val dtype = outTensor.dataType()

        val embedding = FloatArray(embeddingSize)

        for (i in 0 until embeddingSize) {
            val rawInt = when (dtype) {
                DataType.UINT8 -> bytes[i].toInt() and 0xFF   // 0..255
                DataType.INT8 -> bytes[i].toInt()             // -128..127
                else -> bytes[i].toInt()                      // fallback آمن
            }

            embedding[i] = (rawInt - zeroPoint) * scale
        }

        return l2Normalize(embedding)
    }

    /**
     * L2 Normalization (إلزامية قبل أي Similarity)
     */
    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        val norm = sqrt(sum)

        if (norm > 0f) {
            for (i in vec.indices) {
                vec[i] /= norm
            }
        }
        return vec
    }

    /**
     * تحميل نموذج TFLite من assets
     */
    private fun loadModel(
        context: Context,
        filename: String
    ): MappedByteBuffer {
        val fd = context.assets.openFd(filename)
        FileInputStream(fd.fileDescriptor).use { stream ->
            val channel = stream.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    /**
     * scaleBitmapFast
     *
     * ✅ إعادة استخدام Bitmap واحد
     * ✅ بدون أي Memory Allocation إضافي
     */
    private fun scaleBitmapFast(source: Bitmap): Bitmap {
        matrix.reset()
        val scaleX = inputSize.toFloat() / source.width
        val scaleY = inputSize.toFloat() / source.height
        matrix.setScale(scaleX, scaleY)

        canvas.drawBitmap(source, matrix, null)
        return resizedBitmap
    }

    /**
     * طباعة عقد النموذج مرة واحدة فقط للـ debugging
     */
    private fun logContractOnce() {
        if (contractLogged) return

        synchronized(MobileFaceNet::class.java) {
            if (contractLogged) return

            val inTensor = interpreter.getInputTensor(0)
            val outTensor = interpreter.getOutputTensor(0)
            val qp = outTensor.quantizationParams()

            Log.d(TAG, "==== MobileFaceNet CONTRACT ====")
            Log.d(TAG, "modelPath=$MODEL_PATH")
            Log.d(TAG, "inputShape=${inTensor.shape().contentToString()} inputType=${inTensor.dataType()}")
            Log.d(TAG, "outputShape=${outTensor.shape().contentToString()} outputType=${outTensor.dataType()}")
            Log.d(TAG, "quantization scale=${qp.scale} zeroPoint=${qp.zeroPoint}")
            Log.d(TAG, "embeddingSize=$embeddingSize")
            Log.d(TAG, "================================")

            contractLogged = true
        }
    }
}

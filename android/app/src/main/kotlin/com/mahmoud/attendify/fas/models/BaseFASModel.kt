package com.mahmoud.attendify.fas.models

import android.content.Context
import android.graphics.Bitmap
import com.mahmoud.attendify.fas.core.FASModel
import com.mahmoud.attendify.ml.InterpreterFactory
import com.mahmoud.attendify.ml.GpuPolicy
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * BaseFASModel
 *
 * تحميل نموذج FAS مع دعم GPU / NNAPI لكل نموذج بشكل مستقل
 */
abstract class BaseFASModel(
    protected val context: Context
) : FASModel {

    protected lateinit var interpreter: Interpreter
    private lateinit var modelBuffer: ByteBuffer

    private val interpreterFactory = InterpreterFactory()

    /**
     * تحميل النموذج إلى الذاكرة (مرة واحدة)
     */
    protected fun loadModel(assetPath: String) {
        val fd = context.assets.openFd(assetPath)
        val inputStream = FileInputStream(fd.fileDescriptor)
        val channel = inputStream.channel

        modelBuffer = channel.map(
            FileChannel.MapMode.READ_ONLY,
            fd.startOffset,
            fd.declaredLength
        )
    }

    /**
     * تهيئة الـ Interpreter حسب سياسة GPU
     * تُستدعى مرة واحدة عند اختيار النموذج
     */
    override fun prepare(useGpu: Boolean) {

        val policy = if (useGpu && supportsGpu)
            GpuPolicy.FORCED_ON
        else
            GpuPolicy.FORCED_OFF

        interpreter =
            interpreterFactory.createInterpreter(
                model = modelBuffer,
                policy = policy,
                userPrefersGpu = useGpu
            )
    }

    protected fun createInputBuffer(size: Int): ByteBuffer =
        ByteBuffer.allocateDirect(1 * size * size * 3 * 4)
            .order(ByteOrder.nativeOrder())

    protected fun resizeBitmap(
        bitmap: Bitmap,
        size: Int
    ): Bitmap =
        Bitmap.createScaledBitmap(bitmap, size, size, true)
}
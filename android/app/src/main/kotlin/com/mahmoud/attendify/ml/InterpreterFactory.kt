package com.mahmoud.attendify.ml
//final1
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer

/**
 * InterpreterFactory
 *
 * ✅ GPU اختياري
 * ✅ CPU fallback آمن
 * ✅ بدون أي Crash
 * ✅ متوافق 100% مع Flutter build system
 */
class InterpreterFactory {

    var lastGpuStatus: GpuStatus = GpuStatus.DISABLED
        private set

    fun createInterpreter(
        model: ByteBuffer,
        policy: GpuPolicy,
        userPrefersGpu: Boolean
    ): Interpreter {

        val compatList = CompatibilityList()

        val shouldTryGpu =
            policy == GpuPolicy.FORCED_ON ||
                    (
                            policy == GpuPolicy.USER_CHOICE &&
                                    userPrefersGpu &&
                                    compatList.isDelegateSupportedOnThisDevice
                            )

        if (shouldTryGpu) {
            try {
                val options = Interpreter.Options()

                // ✅ نستخدم GPU delegate بدون Options مخصصة
                val gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)

                lastGpuStatus = GpuStatus.ENABLED
                return Interpreter(model, options)

            } catch (_: Exception) {
                // ✅ GPU فشل – ننتقل للـ CPU بأمان
                lastGpuStatus = GpuStatus.FAILED_FALLBACK_CPU
            }
        }

        // ✅ CPU fallback
        val cpuOptions = Interpreter.Options()
        cpuOptions.setNumThreads(
            Runtime.getRuntime()
                .availableProcessors()
                .coerceAtMost(4)
        )

        lastGpuStatus = GpuStatus.DISABLED
        return Interpreter(model, cpuOptions)
    }
}
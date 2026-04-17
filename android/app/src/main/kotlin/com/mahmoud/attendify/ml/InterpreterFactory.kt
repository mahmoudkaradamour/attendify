package com.mahmoud.attendify.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate

class InterpreterFactory(
    private val context: Context,
    private val userPrefersGpu: Boolean
) {

    fun create(model: ByteBuffer): Interpreter {

        val options = Interpreter.Options()
        val compatList = CompatibilityList()

        val useGpu =
            userPrefersGpu &&
                    compatList.isDelegateSupportedOnThisDevice

        if (useGpu) {
            val delegateOptions =
                compatList.bestOptionsForThisDevice
            options.addDelegate(GpuDelegate(delegateOptions))
        } else {
            options.setNumThreads(
                Runtime.getRuntime()
                    .availableProcessors()
                    .coerceAtMost(4)
            )
        }

        return Interpreter(model, options)
    }
}
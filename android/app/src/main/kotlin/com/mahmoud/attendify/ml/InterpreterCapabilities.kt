package com.mahmoud.attendify.ml

import org.tensorflow.lite.gpu.CompatibilityList

/**
 * InterpreterCapabilities
 *
 * مسؤول عن معرفة هل الجهاز يدعم GPU Delegate فعليًا أم لا.
 */
object InterpreterCapabilities {

    fun isGpuSupported(): Boolean {
        val compatList = CompatibilityList()
        return compatList.isDelegateSupportedOnThisDevice
    }
}
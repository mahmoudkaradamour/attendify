package com.mahmoud.attendify.fas.core

import android.graphics.Bitmap

interface FASModel {

    val id: String
    val inputSize: Int
    val defaultThreshold: Float
    val supportsGpu: Boolean

    /**
     * تهيئة الـ Interpreter حسب سياسة GPU
     */
    fun prepare(useGpu: Boolean)

    fun analyze(faceBitmap: Bitmap): FASResult
}
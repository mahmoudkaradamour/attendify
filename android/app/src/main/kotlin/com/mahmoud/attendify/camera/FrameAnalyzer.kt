package com.mahmoud.attendify.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * FrameAnalyzer
 *
 * ملاحظة:
 * لا نغلق ImageProxy هنا لتجنب double-close.
 * الإغلاق يتم دائمًا في المكان الذي يملك try/finally (MainActivity).
 */
class FrameAnalyzer(
    private val onFrameAvailable: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        onFrameAvailable(image)

    }
}
package com.mahmoud.attendify.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class FrameAnalyzer(
    private val onFrameAvailable: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        onFrameAvailable(image)
        image.close()
    }
}
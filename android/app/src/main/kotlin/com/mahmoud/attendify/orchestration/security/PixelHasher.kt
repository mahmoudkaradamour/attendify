package com.mahmoud.attendify.orchestration.security

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.security.MessageDigest

object PixelHasher {

    fun hash(bitmap: Bitmap): ByteArray {

        val buffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)

        val bytes = buffer.array()

        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
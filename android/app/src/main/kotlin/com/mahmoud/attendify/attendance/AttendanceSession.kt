package com.mahmoud.attendify.attendance

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicReference

/**
 * AttendanceSession
 *
 * يخزن آخر Face Bitmap صالح
 * آمن Thread‑safe
 * يُستخدم مرة واحدة لكل محاولة حضور
 */
object AttendanceSession {

    private val latestFaceBitmap =
        AtomicReference<Bitmap?>()

    fun updateFace(bitmap: Bitmap) {
        latestFaceBitmap.set(bitmap)
    }

    fun consumeFace(): Bitmap? {
        return latestFaceBitmap.getAndSet(null)
    }
}

package com.mahmoud.attendify.camera

import android.os.Handler
import android.os.Looper

/**
 * SystemStatusReporter
 *
 * Arabic:
 * مسؤول عن نقل أي حالة (خطأ / تحذير / نجاح)
 * من Native إلى Flutter
 *
 * English:
 * Reports system state changes to Flutter
 */
class SystemStatusReporter(
    private val callback: (SystemStatus) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun report(status: SystemStatus) {
        // Always post on main thread
        mainHandler.post {
            callback(status)
        }
    }
}
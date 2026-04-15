package com.mahmoud.attendify.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mahmoud.attendify.R

/**
 * =========================================
 * عربي:
 * خدمة Foreground مسؤولة عن إبقاء التطبيق
 * يعمل في الخلفية بدون أن يقوم النظام
 * بإيقافه، خاصة على الأجهزة الضعيفة.
 *
 * English:
 * Foreground service responsible for keeping
 * the app alive and running in the background,
 * preventing the system from killing it.
 * =========================================
 */
class AttendanceForegroundService : Service() {

    companion object {
        /**
         * عربي:
         * معرف قناة الإشعارات الخاصة بالخدمة الخلفية.
         *
         * English:
         * Notification channel ID for the foreground service.
         */
        const val CHANNEL_ID = "attendance_foreground_channel"

        /**
         * عربي:
         * رقم ثابت لتمييز الإشعار الدائم.
         *
         * English:
         * Constant ID for the persistent notification.
         */
        const val NOTIFICATION_ID = 1001
    }

    /**
     * عربي:
     * هذه الدالة تُستدعى عندما يتم ربط خدمة
     * بمكون آخر (غير مستخدم هنا).
     *
     * English:
     * Called when binding to the service.
     * (Not used in this service).
     */
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * عربي:
     * تُستدعى عند تشغيل الخدمة.
     * هنا نقوم بإنشاء قناة الإشعارات
     * وتشغيل الخدمة في الـ Foreground.
     *
     * English:
     * Called when the service is started.
     * Creates notification channel and
     * starts the service in foreground mode.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // إنشاء قناة الإشعارات (مطلوبة Android 8+)
        createNotificationChannel()

        // إنشاء الإشعار الدائم
        val notification: Notification = buildNotification()

        // تشغيل الخدمة في المقدمة (Foreground)
        startForeground(NOTIFICATION_ID, notification)

        /**
         * عربي:
         * START_STICKY:
         * في حال قام النظام بإيقاف الخدمة،
         * فسيحاول إعادة تشغيلها تلقائيًا.
         *
         * English:
         * START_STICKY:
         * If the system kills the service,
         * it will try to restart it automatically.
         */
        return START_STICKY
    }

    /**
     * عربي:
     * إنشاء الإشعار الذي يظهر بشكل دائم
     * طالما الخدمة تعمل.
     *
     * English:
     * Builds the persistent notification
     * shown while the service is running.
     */
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Attendify is running")
            .setContentText("Employee attendance service is active")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true) // لا يمكن سحبه من المستخدم
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    /**
     * عربي:
     * إنشاء قناة الإشعارات الخاصة
     * بالخدمة الخلفية.
     *
     * English:
     * Creates the notification channel
     * required for foreground services.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Attendance Background Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Keeps Attendify running in background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager =
                getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * عربي:
     * تُستدعى عند إيقاف الخدمة.
     *
     * English:
     * Called when the service is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
    }
}
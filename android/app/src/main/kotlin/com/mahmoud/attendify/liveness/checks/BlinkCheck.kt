package com.mahmoud.attendify.liveness.checks

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame

/**
 * BlinkCheck
 *
 * Arabic:
 * فحص رمش العين الحقيقي (Right / Left) اعتمادًا على Eye Aspect Ratio (EAR).
 *
 * هذا الكلاس:
 * ✅ لا يستخدم أي ML Liveness classifier
 * ✅ يعتمد على:
 *    - هندسة الوجه (EAR)
 *    - الزمن
 *    - تسلسل الإطارات
 *
 * ✅ يدعم:
 *    - الرمش بالعين اليمنى فقط
 *    - الرمش بالعين اليسرى فقط
 *    - الرمش المتزامن (أو رفضه حسب السياسة)
 *
 * ✅ يسجل:
 *    - كل رمشة حقيقية
 *    - توقيتها
 *    - مدتها
 *    - عمق الرمش (أدنى EAR)
 *
 * ❌ لا يقرر قبول/رفض نهائي
 * → القرار النهائي يكون في LivenessOrchestrator
 */
class BlinkCheck(

    /* =========================================================
     * Policy parameters (من الإدارة)
     * ========================================================= */

    /**
     * عدد الرمشات المطلوب لتحقيق النجاح
     */
    private val requiredBlinkCount: Int,

    /**
     * هل يُسمح بالرمش المتزامن للعينين؟
     * false = الرمش المتزامن لا يُحسب
     */
    private val allowSimultaneousBlink: Boolean,

    /**
     * عتبة EAR أسفلها تُعتبر العين مغلقة
     * (تأتي من الإعدادات – يمكن تعديلها لاحقًا)
     */
    private val eyeClosedThreshold: Double,

    /**
     * الحد الأدنى لمدة الإغلاق (ms) ليُعتبر رمشًا حقيقيًا
     */
    private val minBlinkDurationMs: Long = 80,

    /**
     * الحد الأقصى لمدة الإغلاق (ms)
     * (لمنع احتساب إغلاق طويل غير طبيعي)
     */
    private val maxBlinkDurationMs: Long = 400
) {

    /* =========================================================
     * Internal state – Right Eye
     * ========================================================= */

    private var rightEyeClosed = false
    private var rightEyeCloseStartMs: Long = 0
    private var rightEyeMinEAR: Double = Double.MAX_VALUE

    /* =========================================================
     * Internal state – Left Eye
     * ========================================================= */

    private var leftEyeClosed = false
    private var leftEyeCloseStartMs: Long = 0
    private var leftEyeMinEAR: Double = Double.MAX_VALUE

    /* =========================================================
     * Blink statistics (for audit/debug)
     * ========================================================= */

    private var totalBlinkCount = 0

    /**
     * سجل تفصيلي لكل رمشة حقيقية
     */
    data class BlinkEvent(
        val timestampMs: Long,
        val durationMs: Long,
        val eye: EyeSide,
        val minEAR: Double
    )

    enum class EyeSide {
        RIGHT,
        LEFT,
        BOTH
    }

    val blinkEvents: MutableList<BlinkEvent> = mutableListOf()

    /* =========================================================
     * Main entry per frame
     * ========================================================= */

    /**
     * يجب استدعاؤه مرة لكل frame
     */
    fun onFrame(frame: FacialMetricsFrame) {

        val now = frame.timestampMs

        // -------- Right Eye --------
        processEye(
            eyeEAR = frame.rightEyeEAR,
            isClosedFlag = frame.isRightEyeClosed,
            isCurrentlyClosed = rightEyeClosed,
            closeStartMs = rightEyeCloseStartMs,
            minEAR = rightEyeMinEAR,
            now = now,
            onUpdate = { closed, start, minEar ->
                rightEyeClosed = closed
                rightEyeCloseStartMs = start
                rightEyeMinEAR = minEar
            },
            onBlinkDetected = { duration, minEar ->
                registerBlink(
                    timestamp = now,
                    duration = duration,
                    minEAR = minEar,
                    eyeSide = EyeSide.RIGHT
                )
            }
        )

        // -------- Left Eye --------
        processEye(
            eyeEAR = frame.leftEyeEAR,
            isClosedFlag = frame.isLeftEyeClosed,
            isCurrentlyClosed = leftEyeClosed,
            closeStartMs = leftEyeCloseStartMs,
            minEAR = leftEyeMinEAR,
            now = now,
            onUpdate = { closed, start, minEar ->
                leftEyeClosed = closed
                leftEyeCloseStartMs = start
                leftEyeMinEAR = minEar
            },
            onBlinkDetected = { duration, minEar ->
                registerBlink(
                    timestamp = now,
                    duration = duration,
                    minEAR = minEar,
                    eyeSide = EyeSide.LEFT
                )
            }
        )

        // -------- Simultaneous blink (both eyes) --------
        if (!allowSimultaneousBlink &&
            frame.isRightEyeClosed &&
            frame.isLeftEyeClosed
        ) {
            // تجاهل الرمش المتزامن إن كان غير مسموح
            // لا نسجّل أي شيء هنا
        }
    }

    /* =========================================================
     * Core eye processing logic
     * ========================================================= */

    private fun processEye(
        eyeEAR: Double,
        isClosedFlag: Boolean,
        isCurrentlyClosed: Boolean,
        closeStartMs: Long,
        minEAR: Double,
        now: Long,
        onUpdate: (Boolean, Long, Double) -> Unit,
        onBlinkDetected: (Long, Double) -> Unit
    ) {

        if (isClosedFlag && !isCurrentlyClosed) {
            // بداية إغلاق العين
            onUpdate(true, now, eyeEAR)
            return
        }

        if (isClosedFlag && isCurrentlyClosed) {
            // العين ما زالت مغلقة – نحدّث أدنى EAR
            onUpdate(true, closeStartMs, minOf(minEAR, eyeEAR))
            return
        }

        if (!isClosedFlag && isCurrentlyClosed) {
            // نهاية إغلاق العين → تحقق من مدة الرمش
            val duration = now - closeStartMs

            if (duration in minBlinkDurationMs..maxBlinkDurationMs) {
                onBlinkDetected(duration, minEAR)
            }

            onUpdate(false, 0L, Double.MAX_VALUE)
        }
    }

    /* =========================================================
     * Blink registration
     * ========================================================= */

    private fun registerBlink(
        timestamp: Long,
        duration: Long,
        minEAR: Double,
        eyeSide: EyeSide
    ) {
        totalBlinkCount++

        blinkEvents.add(
            BlinkEvent(
                timestampMs = timestamp,
                durationMs = duration,
                eye = eyeSide,
                minEAR = minEAR
            )
        )
    }

    /* =========================================================
     * Public evaluation API
     * ========================================================= */

    /**
     * هل حقق المستخدم عدد الرمشات المطلوب؟
     */
    fun isPassed(): Boolean =
        totalBlinkCount >= requiredBlinkCount

    /**
     * عدد الرمشات المكتشفة
     */
    fun getBlinkCount(): Int = totalBlinkCount

    /**
     * إعادة تهيئة الفحص (عند بدء جلسة جديدة)
     */
    fun reset() {
        rightEyeClosed = false
        leftEyeClosed = false
        rightEyeCloseStartMs = 0
        leftEyeCloseStartMs = 0
        rightEyeMinEAR = Double.MAX_VALUE
        leftEyeMinEAR = Double.MAX_VALUE
        totalBlinkCount = 0
        blinkEvents.clear()
    }
}
package com.mahmoud.attendify.liveness.engine

import com.mahmoud.attendify.liveness.data.FacialMetricsFrame

/**
 * MetricsSessionLog
 *
 * Arabic:
 * يمثل سجلًا كاملًا لكل القياسات التي حدثت
 * خلال محاولة حضور واحدة.
 *
 * يستخدم لـ:
 * - العرض اللحظي
 * - التخزين
 * - التدقيق لاحقًا
 */
class MetricsSessionLog {

    val frames: MutableList<FacialMetricsFrame> = mutableListOf()

    fun add(frame: FacialMetricsFrame) {
        frames.add(frame)
    }

    fun clear() {
        frames.clear()
    }
}
package com.mahmoud.attendify.system.time

import java.time.Instant

/**
 * Converts a TimeSnapshot to java.time.Instant
 *
 * This is the ONLY place that knows how to
 * interpret TimeSnapshot's internal representation.
 */
fun TimeSnapshot.toInstant(): Instant =
    Instant.ofEpochMilli(this.wallClockMillis)
package com.mahmoud.attendify.attendance.domain

/**
 * Justification
 *
 * Represents a mandatory or optional explanation
 * provided by the employee for a high‑risk attendance context.
 *
 * IMPORTANT:
 * - This object is immutable.
 * - It is attached to attendance decisions.
 * - It is for audit and review, not validation.
 */
data class Justification(

    /** Free‑text justification written by the employee */
    val text: String,

    /** Timestamp when justification was provided */
    val timestampMillis: Long
)
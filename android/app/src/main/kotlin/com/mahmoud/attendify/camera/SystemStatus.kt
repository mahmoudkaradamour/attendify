package com.mahmoud.attendify.camera

/**
 * SystemStatus
 *
 * ============================================================================
 * ROLE:
 * ============================================================================
 * ✅ Canonical enumeration of NON‑AUTHORITATIVE system states
 * ✅ Emitted from Native → Flutter for UI guidance ONLY
 *
 * SECURITY CONTRACT (Phase 3.2 – UI Guidance Isolation):
 * ============================================================================
 * ❌ Not a decision
 * ❌ Not an attendance result
 * ❌ Not evidence
 * ❌ Must NEVER be used to infer acceptance or rejection
 *
 * Zero‑Trust Principle:
 * ---------------------
 * Flutter MUST treat these values as:
 *  - Informational hints
 *  - UX guidance
 *  - Non‑binding signals
 *
 * Any attempt to:
 *  - Map SystemStatus → AttendanceResult
 *  - Derive policy conclusions
 *  - Reconstruct rejection reasons
 *
 * ❌ INVALIDATES forensic integrity.
 */
enum class SystemStatus {

    /**
     * OK
     *
     * The system is operating within acceptable parameters.
     *
     * NOTES:
     * ------
     * - Does NOT imply success
     * - Does NOT imply acceptance
     * - Merely indicates no immediate blocking UX issue
     */
    OK,

    // ------------------------------------------------------------------------
    // Image quality guidance (UI ONLY)
    // ------------------------------------------------------------------------

    /**
     * IMAGE_TOO_DARK
     *
     * Captured frame is under‑exposed.
     *
     * UI may suggest better lighting.
     * No policy decision may be inferred.
     */
    IMAGE_TOO_DARK,

    /**
     * IMAGE_TOO_BRIGHT
     *
     * Captured frame is over‑exposed.
     */
    IMAGE_TOO_BRIGHT,

    /**
     * IMAGE_LOW_CONTRAST
     *
     * Visual contrast is insufficient for reliable processing.
     */
    IMAGE_LOW_CONTRAST,

    /**
     * IMAGE_BLURRY
     *
     * Motion blur or focus issue detected.
     */
    IMAGE_BLURRY,

    /**
     * FRAME_CORRUPTED
     *
     * Captured frame is invalid or corrupted.
     *
     * Could be caused by:
     * - Driver issues
     * - Resource contention
     */
    FRAME_CORRUPTED,

    /**
     * FACE_TOO_FAR
     *
     * Detected face occupies insufficient frame area.
     */
    FACE_TOO_FAR,

    /**
     * FACE_TOO_CLOSE
     *
     * Detected face is closer than acceptable bounds.
     */
    FACE_TOO_CLOSE,

    /**
     * MULTIPLE_FACES_DETECTED
     *
     * More than one face detected in the frame.
     *
     * UI usage:
     * ---------
     * - Prompt user to ensure only one subject is visible
     *
     * SECURITY:
     * ---------
     * - Does NOT expose anti‑spoof logic
     */
    MULTIPLE_FACES_DETECTED,

    // ------------------------------------------------------------------------
    // Camera / permission state guidance
    // ------------------------------------------------------------------------

    /**
     * CAMERA_BUSY
     *
     * Camera resource is currently unavailable.
     *
     * Example causes:
     * - Another process using camera
     * - Session initialization in progress
     */
    CAMERA_BUSY,

    /**
     * CAMERA_CLOSED
     *
     * Camera session is not active.
     *
     * UI may retry initialization.
     */
    CAMERA_CLOSED,

    /**
     * CAMERA_PERMISSION_REVOKED_BY_SYSTEM
     *
     * Camera permission was revoked externally (system / admin).
     */
    CAMERA_PERMISSION_REVOKED_BY_SYSTEM,

    /**
     * NO_CAMERA_PERMISSION
     *
     * Application does not currently hold camera permission.
     */
    NO_CAMERA_PERMISSION,

    // ------------------------------------------------------------------------
    // System / security guidance (NON‑DIAGNOSTIC)
    // ------------------------------------------------------------------------

    /**
     * LOW_MEMORY
     *
     * System memory pressure detected.
     *
     * UI guidance only.
     */
    LOW_MEMORY,

    /**
     * SPOOF_DETECTED
     *
     * A generic anti‑spoofing anomaly signal.
     *
     * IMPORTANT:
     * ----------
     * - This value is intentionally NON‑DESCRIPTIVE
     * - No reason, vector, or technique is exposed
     * - UI must NOT explain or expand on this
     */
    SPOOF_DETECTED,

    /**
     * INTERNAL_ERROR
     *
     * An unexpected internal failure occurred.
     *
     * SECURITY:
     * ---------
     * - No stack trace
     * - No diagnostic leakage
     */
    INTERNAL_ERROR
}
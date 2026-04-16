package com.mahmoud.attendify.camera

/**
 * SystemStatus
 *
 * يمثل كل الحالات الممكنة التي يرسلها النظام
 * من Native → Flutter
 */
enum class SystemStatus {

    // ✅ Normal
    OK,

    // ✅ Image quality
    IMAGE_TOO_DARK,
    IMAGE_TOO_BRIGHT,
    IMAGE_LOW_CONTRAST,
    IMAGE_BLURRY,
    FRAME_CORRUPTED,
    FACE_TOO_FAR,
    FACE_TOO_CLOSE,

    // ✅ Camera / Permission
    CAMERA_BUSY,
    CAMERA_CLOSED,
    CAMERA_PERMISSION_REVOKED_BY_SYSTEM,
    NO_CAMERA_PERMISSION,

    // ✅ System / Security
    LOW_MEMORY,
    SPOOF_DETECTED,
    INTERNAL_ERROR
}
package com.mahmoud.attendify.errors

sealed class AttendanceError {

    data class DeviceError(val message: String) : AttendanceError()
    data class CameraError(val message: String) : AttendanceError()
    data class LightingError(val message: String) : AttendanceError()
    data class ModelError(val message: String) : AttendanceError()
    data class RuntimeError(val message: String) : AttendanceError()
}
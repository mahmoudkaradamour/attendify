import 'package:flutter/services.dart';

/// =====================================================
/// عربي:
/// قناة الربط مع Android للتحكم بالخدمة الخلفية.
///
/// English:
/// MethodChannel used to control the foreground service.
/// =====================================================
class AttendanceServiceController {
  static const MethodChannel _channel =
  MethodChannel('attendify/system');

  /// تشغيل الخدمة
  static Future<void> startService() async {
    await _channel.invokeMethod('startAttendanceService');
  }

  /// إيقاف الخدمة
  static Future<void> stopService() async {
    await _channel.invokeMethod('stopAttendanceService');
  }

  /// فتح إعدادات تحسين البطارية
  static Future<void> openBatterySettings() async {
    await _channel.invokeMethod('openBatteryOptimizationSettings');
  }
}
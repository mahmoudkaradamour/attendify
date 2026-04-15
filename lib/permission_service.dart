import 'package:permission_handler/permission_handler.dart';

class PermissionService {
  /// ============================
  /// LOCATION
  /// ============================

  static Future<LocationPermissionState> checkLocation() async {
    final precise = await Permission.locationWhenInUse.status;
    final always = await Permission.locationAlways.status;

    return LocationPermissionState(
      precise: precise,
      background: always,
    );
  }

  static Future<void> requestLocation() async {
    await Permission.locationWhenInUse.request();
  }

  static Future<void> requestBackgroundLocation() async {
    await Permission.locationAlways.request();
  }

  /// ============================
  /// CAMERA
  /// ============================

  static Future<PermissionStatus> cameraStatus() {
    return Permission.camera.status;
  }

  static Future<void> requestCamera() async {
    await Permission.camera.request();
  }

  /// ============================
  /// NOTIFICATIONS
  /// ============================

  static Future<PermissionStatus> notificationsStatus() {
    return Permission.notification.status;
  }

  static Future<void> requestNotifications() async {
    await Permission.notification.request();
  }

  /// ============================
  /// BATTERY OPTIMIZATION
  /// ============================

  static Future<bool> isIgnoringBatteryOptimizations() {
    return Permission.ignoreBatteryOptimizations.isGranted;
  }

  static Future<void> requestIgnoreBatteryOptimizations() async {
    await Permission.ignoreBatteryOptimizations.request();
  }
}

/// Helper model
class LocationPermissionState {
  final PermissionStatus precise;
  final PermissionStatus background;

  LocationPermissionState({
    required this.precise,
    required this.background,
  });
}

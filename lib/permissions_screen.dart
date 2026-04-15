import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'permission_service.dart';

class PermissionsScreen extends StatefulWidget {
  const PermissionsScreen({super.key});

  @override
  State<PermissionsScreen> createState() => _PermissionsScreenState();
}

class _PermissionsScreenState extends State<PermissionsScreen>
    with WidgetsBindingObserver {
  LocationPermissionState? locationState;
  PermissionStatus? camera;
  PermissionStatus? notifications;
  bool batteryIgnored = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  /// Detects when user returns from settings
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _refresh();
    }
  }

  Future<void> _refresh() async {
    locationState = await PermissionService.checkLocation();
    camera = await PermissionService.cameraStatus();
    notifications = await PermissionService.notificationsStatus();
    batteryIgnored =
    await PermissionService.isIgnoringBatteryOptimizations();

    setState(() {});
  }

  Widget _statusChip(String label, bool ok) {
    return Chip(
      label: Text(label),
      backgroundColor: ok ? Colors.green : Colors.red,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Permissions Status')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [

          /// LOCATION
          const Text('Location', style: TextStyle(fontSize: 18)),
          Row(
            children: [
              _statusChip(
                'Precise',
                locationState?.precise.isGranted ?? false,
              ),
              const SizedBox(width: 8),
              _statusChip(
                'Background',
                locationState?.background.isGranted ?? false,
              ),
            ],
          ),
          ElevatedButton(
            onPressed: PermissionService.requestLocation,
            child: const Text('Grant Location'),
          ),
          ElevatedButton(
            onPressed: PermissionService.requestBackgroundLocation,
            child: const Text('Grant Background Location'),
          ),

          const Divider(),

          /// CAMERA
          const Text('Camera', style: TextStyle(fontSize: 18)),
          _statusChip(
            camera.toString(),
            camera?.isGranted ?? false,
          ),
          ElevatedButton(
            onPressed: PermissionService.requestCamera,
            child: const Text('Grant Camera'),
          ),

          const Divider(),

          /// NOTIFICATIONS
          const Text('Notifications', style: TextStyle(fontSize: 18)),
          _statusChip(
            notifications.toString(),
            notifications?.isGranted ?? false,
          ),
          ElevatedButton(
            onPressed: PermissionService.requestNotifications,
            child: const Text('Grant Notifications'),
          ),

          const Divider(),

          /// BATTERY
          const Text('Battery Optimization', style: TextStyle(fontSize: 18)),
          _statusChip(
            batteryIgnored ? 'Ignored' : 'Not Ignored',
            batteryIgnored,
          ),
          ElevatedButton(
            onPressed:
            PermissionService.requestIgnoreBatteryOptimizations,
            child: const Text('Ignore Battery Optimizations'),
          ),
        ],
      ),
    );
  }
}

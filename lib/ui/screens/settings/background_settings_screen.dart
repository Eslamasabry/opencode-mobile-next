part of '../settings_screen.dart';

/// Notifications & background category: the live background connection and
/// its battery guidance.
class BackgroundSettingsScreen extends StatefulWidget {
  final ConnectionController controller;
  const BackgroundSettingsScreen({super.key, required this.controller});

  @override
  State<BackgroundSettingsScreen> createState() =>
      _BackgroundSettingsScreenState();
}

class _BackgroundSettingsScreenState extends State<BackgroundSettingsScreen>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    widget.controller.addListener(_changed);
    widget.controller.backgroundLive.addListener(_changed);
    widget.controller.backgroundLive.refreshStatus();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      widget.controller.backgroundLive.refreshStatus();
    }
  }

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    return Scaffold(
      appBar: AppBar(title: const Text('Notifications & background')),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          SwitchListTile(
            secondary: const Icon(Icons.sync_lock_rounded),
            title: const Text('Keep coding session live'),
            subtitle: const Text(
              'Keeps server events and terminals connected while this app is in the background. '
              'Uses more battery and shows a persistent Android notification.',
            ),
            value: controller.keepLiveInBackground,
            onChanged: controller.backgroundLive.busy
                ? null
                : (value) async {
                    final enabled = await controller.setKeepLiveInBackground(
                      value,
                    );
                    if (!context.mounted) return;
                    final error = controller.backgroundLive.lastError;
                    if (error != null || enabled != value) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            error ?? 'Android did not enable background mode.',
                          ),
                        ),
                      );
                    }
                  },
          ),
          if (controller.keepLiveInBackground)
            ListTile(
              leading: Icon(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? Icons.battery_charging_full_rounded
                    : Icons.battery_alert_outlined,
              ),
              title: Text(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? 'Unrestricted battery access allowed'
                    : 'Allow unrestricted battery access',
              ),
              subtitle: Text(
                controller.backgroundLive.batteryOptimizationIgnored
                    ? 'Android may still apply its foreground-service time limit.'
                    : 'Optional. Helps preserve the live connection during Doze. '
                          'Android 15+ limits data-sync background work to six hours per 24 hours.',
              ),
              trailing: controller.backgroundLive.batteryOptimizationIgnored
                  ? const Icon(Icons.check_rounded)
                  : const Icon(Icons.open_in_new_rounded),
              onTap: controller.backgroundLive.batteryOptimizationIgnored
                  ? null
                  : () async {
                      await controller.backgroundLive
                          .requestBatteryOptimizationExemption();
                    },
            ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    widget.controller.removeListener(_changed);
    widget.controller.backgroundLive.removeListener(_changed);
    super.dispose();
  }
}

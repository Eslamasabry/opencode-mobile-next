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
    // After the frame, not during it: refreshStatus marks itself busy and
    // notifies synchronously, and a notification mid-build is a setState
    // during build for every listener above this screen.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) widget.controller.backgroundLive.refreshStatus();
    });
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
          // Android 15 stops the service on its own once the daily budget is
          // spent, and the switch flips itself off when it does. Say so where
          // the switch is, not only behind the battery row: a limit the user
          // only meets by losing a connection is a limit stated too late.
          if (controller.backgroundLive.stoppedByAndroidTimeout)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
              child: Card(
                key: const ValueKey('background-timeout-notice'),
                color: Theme.of(context).colorScheme.errorContainer,
                child: ListTile(
                  leading: const Icon(Icons.timer_off_outlined),
                  title: const Text('Android stopped the live connection'),
                  subtitle: const Text(
                    'Its daily limit for background data-sync work is spent, '
                    'so live mode turned itself off. Turn it back on to '
                    'reconnect; the limit resets within 24 hours.',
                  ),
                ),
              ),
            ),
          SwitchListTile(
            secondary: const Icon(Icons.sync_lock_rounded),
            title: const Text('Keep coding session live'),
            subtitle: const Text(
              'Keeps server events and terminals connected while this app is in the background. '
              'Uses more battery and shows a persistent Android notification. '
              'Android 15+ allows six hours of this per 24 hours and then '
              'stops it; the app turns the switch off and says so when that '
              'happens.',
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

import 'dart:math';

import 'package:shared_preferences/shared_preferences.dart';
import 'package:package_info_plus/package_info_plus.dart';

import '../host/byo_host_bundle_pins.dart';

import '../domain/byo_host.dart';
import '../host/byo_host_ssh_runner.dart';
import 'byo_host_controller.dart';
import 'byo_host_store.dart';

/// Composition for Claude's frontend. One shared runner/store, one controller
/// per phone profile. Disposing one controller never closes other machines.
class ByoHostService {
  ByoHostService({
    required this.store,
    required this.runner,
    this.bundle,
    this.clearLocalData,
    this.enabled = byoHostBuildEnabled,
  });

  static Future<ByoHostService> builtin({
    required SharedPreferences prefs,
    required Future<void> Function(String profileId) clearLocalData,
    bool enabled = byoHostBuildEnabled,
  }) async {
    final app = await PackageInfo.fromPlatform();
    return ByoHostService(
      store: PersistentByoHostStore(prefs: prefs),
      runner: BuiltinByoHostSshRunner(),
      bundle: byoHostBundleForAppVersion('${app.version}+${app.buildNumber}'),
      clearLocalData: clearLocalData,
      enabled: enabled,
    );
  }

  final ByoHostStore store;
  final ByoHostSshRunner runner;
  final ByoHostBundle? bundle;
  final Future<void> Function(String profileId)? clearLocalData;
  final bool enabled;
  final Map<String, ByoHostController> _controllers = {};
  bool _disposed = false;

  Future<bool> available() async =>
      enabled && !_disposed && bundle != null && await runner.available();
  Future<List<ByoHostRecord>> machines() async {
    _check();
    return store.listRecords();
  }

  void _check() {
    if (!enabled || _disposed) {
      throw const ByoHostFailure(ByoHostFailureCode.disabled);
    }
  }

  ByoHostController newMachine() {
    _check();
    final random = Random.secure();
    final id =
        'host-${List.generate(16, (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0')).join()}';
    return machine(id);
  }

  ByoHostController machine(String profileId) {
    _check();
    return _controllers.putIfAbsent(
      profileId,
      () => ByoHostController(
        profileId: profileId,
        runner: runner,
        store: store,
        bundle: bundle,
        clearLocalData: clearLocalData,
        enabled: enabled,
      ),
    );
  }

  /// Call only after the controller has completed removal and the existing
  /// ConnectionController has disposed its gateway and swept shared local data.
  Future<void> release(String profileId) async {
    final controller = _controllers[profileId];
    if (controller == null) return;
    await controller.dispose();
    _controllers.remove(profileId);
  }

  Future<void> dispose() async {
    for (final controller in _controllers.values) {
      await controller.dispose();
    }
    _controllers.clear();
    await runner.dispose();
    _disposed = true;
  }
}

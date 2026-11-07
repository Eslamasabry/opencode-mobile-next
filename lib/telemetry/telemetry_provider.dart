import 'package:flutter/widgets.dart' show BuildContext;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'telemetry.dart';

/// Consent is stored once for the app, not per server profile.
const telemetryConsentKey = 'oc.telemetry.crashReports';

class PreferencesTelemetryConsentStore implements TelemetryConsentStore {
  PreferencesTelemetryConsentStore(this._prefs);
  final SharedPreferences _prefs;
  @override
  bool read() => _prefs.getBool(telemetryConsentKey) ?? false;
  @override
  Future<void> write(bool value) => _prefs.setBool(telemetryConsentKey, value);
}

/// The process-wide reporter for code that has no `ref` (start-up error
/// hooks, the setup engine, the voice model manager). Off and inert until
/// [AppTelemetry.install] runs; the provider below hands out the same object.
abstract final class AppTelemetry {
  static Telemetry _current = TelemetryService(
    adapter: const NoOpTelemetryAdapter(),
  );

  static Telemetry get current => _current;

  static void install(Telemetry telemetry) => _current = telemetry;

  /// Test hook: back to the inert default.
  static void reset() =>
      _current = TelemetryService(adapter: const NoOpTelemetryAdapter());

  static void failure(String name, [Map<String, Object?> keys = const {}]) =>
      _current.recordFailure(name, keys);
}

/// Code with a `ref` depends only on this. Tests override it with a fake; a
/// vendor swap is the one adapter passed to [TelemetryStartup.start].
final telemetryProvider = Provider<Telemetry>((ref) => AppTelemetry.current);

/// The reporter for a widget that is a plain `State`: the provider's value
/// when a scope exists above it, else the process-wide one.
Telemetry telemetryOf(BuildContext context) {
  try {
    return ProviderScope.containerOf(
      context,
      listen: false,
    ).read(telemetryProvider);
  } catch (_) {
    return AppTelemetry.current;
  }
}

/// Start-up: loads the remembered choice, asks the adapter whether the
/// vendor is built in, and installs the reporter. Never blocks the first
/// frame and never throws.
abstract final class TelemetryStartup {
  static Future<void> start(TelemetryAdapter adapter) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final service = TelemetryService(
        adapter: adapter,
        store: PreferencesTelemetryConsentStore(prefs),
      );
      await service.start();
      AppTelemetry.install(service);
    } catch (_) {}
  }
}

import 'package:flutter/widgets.dart'
    show
        AppLifecycleState,
        BuildContext,
        WidgetsBinding,
        WidgetsBindingObserver;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'analytics_events.dart';
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

  /// A product analytics event; dropped unless the person agreed.
  static void track(
    AnalyticsEvent event, [
    Map<String, Object?> props = const {},
  ]) => _current.track(event, props);
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
      await AnalyticsLifecycle.start(prefs);
    } catch (_) {}
  }
}

/// The app version last seen at start-up, so a newer one counts as an
/// installed update. App-wide, not per server profile.
const analyticsLastVersionKey = 'oc.telemetry.lastVersion';

/// `app_open` (cold once per process, warm after each return from the
/// background) and `update_installed`. Inert while consent is off, like
/// every other event.
class AnalyticsLifecycle with WidgetsBindingObserver {
  AnalyticsLifecycle._();

  static AnalyticsLifecycle? _installed;
  bool _wasAway = false;

  static Future<void> start(
    SharedPreferences prefs, {
    Future<String> Function()? version,
  }) async {
    AppTelemetry.track(AnalyticsEvent.appOpen, {'kind': 'cold'});
    try {
      final current = await (version ?? _appVersion)();
      final previous = prefs.getString(analyticsLastVersionKey);
      if (previous != current) {
        if (previous != null) {
          AppTelemetry.track(AnalyticsEvent.updateInstalled);
        }
        await prefs.setString(analyticsLastVersionKey, current);
      }
    } catch (_) {}
    if (_installed == null) {
      final observer = _installed = AnalyticsLifecycle._();
      WidgetsBinding.instance.addObserver(observer);
    }
  }

  static Future<String> _appVersion() async {
    final info = await PackageInfo.fromPlatform();
    return '${info.version}+${info.buildNumber}';
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.paused || AppLifecycleState.hidden:
        _wasAway = true;
      case AppLifecycleState.resumed when _wasAway:
        _wasAway = false;
        AppTelemetry.track(AnalyticsEvent.appOpen, {'kind': 'warm'});
      default:
        break;
    }
  }
}

import 'dart:async';

import 'package:flutter/widgets.dart'
    show
        AppLifecycleState,
        BuildContext,
        NavigatorObserver,
        PageRoute,
        Route,
        WidgetsBinding,
        WidgetsBindingObserver;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../diagnostics/perf_trace.dart' show PerfTraceNavigatorObserver;
import '../feedback/crash_offer.dart' show CrashReportOffer;
import 'analytics_events.dart';
export 'flow_events.dart' show HealthFlow, failKindOf;
import 'flow_events.dart';
import 'health_probe.dart';
import 'health_schema.dart' show BreadcrumbWords;
import 'telemetry.dart';

/// The single switch of 2.1.5 before the split into [telemetryUsageKey] and
/// [telemetryCrashesKey]. Read once to carry an old answer over; kept, never
/// written again. Consent is stored once for the app, not per server profile.
const telemetryConsentKey = 'oc.telemetry.crashReports';

/// `usage`: events, performance and device context (section B, C, D).
const telemetryUsageKey = 'oc.telemetry.usage';

/// `crashes`: crash reports (section A).
const telemetryCrashesKey = 'oc.telemetry.crashes';

/// The report counter and the install code kept on this phone for the
/// problem report's code.
const telemetryReportCounterKey = 'oc.telemetry.reportCounter';
const telemetryInstallCodeKey = 'oc.telemetry.installCode';

class PreferencesTelemetryConsentStore implements TelemetryConsentStore {
  PreferencesTelemetryConsentStore(this._prefs);
  final SharedPreferences _prefs;

  /// The two answers: the saved ones, or, when neither was ever saved, those
  /// carried over from 2.1.5's single answer so nobody is asked twice.
  ///
  /// - The old single "send reports" yes meant crash reports and usage, so it
  ///   turns `usage` on, and `crashes` on too unless the person explicitly
  ///   turned the crash offer off (the old crash-offer answer).
  /// - No old yes leaves both off.
  static TelemetryConsent carriedOver(SharedPreferences prefs) {
    final usage = prefs.getBool(telemetryUsageKey);
    final crashes = prefs.getBool(telemetryCrashesKey);
    if (usage != null || crashes != null) {
      return (usage: usage ?? false, crashes: crashes ?? false);
    }
    final oldYes = prefs.getBool(telemetryConsentKey) ?? false;
    final offerRefused = prefs.getBool(CrashReportOffer.enabledKey) == false;
    return (usage: oldYes, crashes: oldYes && !offerRefused);
  }

  /// Saves the carried-over answers under the new keys, and marks the
  /// one-time question as asked when an old answer exists. Once.
  static Future<void> migrate(SharedPreferences prefs) async {
    if (prefs.containsKey(telemetryUsageKey) ||
        prefs.containsKey(telemetryCrashesKey)) {
      return;
    }
    final answer = carriedOver(prefs);
    final answered =
        prefs.containsKey(telemetryConsentKey) ||
        prefs.containsKey(CrashReportOffer.enabledKey);
    if (!answered) return;
    await prefs.setBool(telemetryUsageKey, answer.usage);
    await prefs.setBool(telemetryCrashesKey, answer.crashes);
    await prefs.setBool(telemetryConsentAskedKey, true);
  }

  @override
  TelemetryConsent read() => carriedOver(_prefs);

  @override
  Future<void> write({required bool usage, required bool crashes}) async {
    await _prefs.setBool(telemetryUsageKey, usage);
    await _prefs.setBool(telemetryCrashesKey, crashes);
  }
}

class PreferencesTelemetryReportStore implements TelemetryReportStore {
  PreferencesTelemetryReportStore(this._prefs);
  final SharedPreferences _prefs;
  @override
  int readCounter() => _prefs.getInt(telemetryReportCounterKey) ?? 0;
  @override
  Future<void> writeCounter(int value) =>
      _prefs.setInt(telemetryReportCounterKey, value);
  @override
  String? readLocalId() => _prefs.getString(telemetryInstallCodeKey);
  @override
  Future<void> writeLocalId(String value) =>
      _prefs.setString(telemetryInstallCodeKey, value);
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

  /// `feature_used` for one use of [feature].
  static void feature(AnalyticsFeature feature) =>
      _current.track(AnalyticsEvent.featureUsed, {'feature': feature});

  /// A breadcrumb (`area.action`, optionally `:word`); dropped unless it is
  /// listed in [BreadcrumbWords].
  static void crumb(String name) => _current.breadcrumb(name);

  /// The health and diagnostics wiring, once [TelemetryStartup] made it.
  static HealthTelemetry? health;
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
  static Completer<void> _ready = Completer<void>();

  /// Completes once [start] has installed the reporter (or failed), so the
  /// consent question knows whether a vendor is built in.
  static Future<void> get ready => _ready.future;

  /// Test hook: a fresh, not yet completed [ready].
  static void resetForTest() => _ready = Completer<void>();

  /// Test hook: [ready] completes now.
  static void markReadyForTest() {
    if (!_ready.isCompleted) _ready.complete();
  }

  static Future<void> start(TelemetryAdapter adapter) async {
    try {
      await _start(adapter);
    } finally {
      if (!_ready.isCompleted) _ready.complete();
    }
  }

  static Future<void> _start(TelemetryAdapter adapter) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await PreferencesTelemetryConsentStore.migrate(prefs);
      final service = TelemetryService(
        adapter: adapter,
        store: PreferencesTelemetryConsentStore(prefs),
        reports: PreferencesTelemetryReportStore(prefs),
      );
      await service.start();
      AppTelemetry.install(service);
      AppTelemetry.crumb('app.start');
      AppTelemetry.health = HealthTelemetry(telemetry: service, prefs: prefs)
        ..start();
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
  DateTime _since = DateTime.now();

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
          HealthFlow.updateResult(kind: 'in_app', result: 'installed');
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
      case AppLifecycleState.paused || AppLifecycleState.hidden when !_wasAway:
        _wasAway = true;
        AppTelemetry.crumb('app.background');
        AppTelemetry.health?.jank.endSession();
        AppTelemetry.track(AnalyticsEvent.sessionLength, {
          'minutes': AnalyticsBuckets.sessionMinutesOf(
            DateTime.now().difference(_since),
          ),
        });
      case AppLifecycleState.resumed when _wasAway:
        _wasAway = false;
        _since = DateTime.now();
        AppTelemetry.crumb('app.foreground');
        AppTelemetry.health?.warmStart();
        AppTelemetry.track(AnalyticsEvent.appOpen, {'kind': 'warm'});
      default:
        break;
    }
  }
}

/// `screen_view` for each page that comes into view, named by kind from
/// [AnalyticsScreen] (never its content). Sheets and dialogs are not pages.
class AnalyticsNavigatorObserver extends NavigatorObserver {
  void _viewed(Route<dynamic>? route) {
    if (route is! PageRoute) return;
    try {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!route.isCurrent) return;
        final screen = AnalyticsScreen.of(
          PerfTraceNavigatorObserver.routeName(route),
        );
        AppTelemetry.crumb('nav.open:${screen.wire}');
        AppTelemetry.health?.jank.screen(screen);
        AppTelemetry.track(AnalyticsEvent.screenView, {'screen': screen});
      });
    } catch (_) {}
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _viewed(route);

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) =>
      _viewed(newRoute);

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) =>
      _viewed(previousRoute);
}

/// Whether the one-time "Help improve OpenCode Mobile" question was asked.
/// App-wide; set before the question shows, so it is never asked twice.
const telemetryConsentAskedKey = 'oc.telemetryConsentAsked';

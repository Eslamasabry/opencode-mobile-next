import 'dart:async';
import 'dart:ui' show FrameTiming, PlatformDispatcher;

import 'package:flutter/scheduler.dart' show SchedulerBinding;
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart' show WidgetsBinding;
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../platform/app_exit.dart' show AndroidExitReason, AppLifecycleBridge;
import '../platform/platform_capabilities.dart';
import 'analytics_events.dart';
import 'health_schema.dart';
import 'perf_traces.dart';
import 'telemetry.dart';

/// What the phone says about itself for the device context (section D of
/// docs/telemetry/health-and-diagnostics.md): numbers and fixed words only.
class HealthDeviceContext {
  const HealthDeviceContext({
    this.ramBytes = 0,
    this.freeBytes = -1,
    this.abi,
    this.webviewMajor,
    this.notificationsAllowed,
    this.processUptimeMs = 0,
    this.fromStore = false,
  });

  factory HealthDeviceContext.fromMap(Object? raw) {
    if (raw is! Map) return const HealthDeviceContext();
    int? asInt(Object? v) => v is num ? v.toInt() : null;
    final abi = raw['abi'];
    return HealthDeviceContext(
      ramBytes: asInt(raw['ramBytes']) ?? 0,
      freeBytes: asInt(raw['freeBytes']) ?? -1,
      abi: abi is String && HealthWords.abis.contains(abi) ? abi : null,
      webviewMajor: asInt(raw['webviewMajor']),
      notificationsAllowed: raw['notificationsAllowed'] is bool
          ? raw['notificationsAllowed'] as bool
          : null,
      processUptimeMs: asInt(raw['processUptimeMs']) ?? 0,
      fromStore: raw['fromStore'] == true,
    );
  }

  final int ramBytes;
  final int freeBytes;
  final String? abi;
  final int? webviewMajor;
  final bool? notificationsAllowed;
  final int processUptimeMs;
  final bool fromStore;

  static const _gb = 1024 * 1024 * 1024;

  /// `ram_range` word, or null when unknown.
  String? get ramRange =>
      ramBytes > 0 ? HealthRanges.ramOf(ramBytes / _gb) : null;

  /// `free_storage_range` word, or null when unknown.
  String? get freeStorageRange =>
      freeBytes >= 0 ? HealthRanges.storageOf(freeBytes / _gb) : null;

  /// The user properties this context gives (section D, without the server
  /// ones).
  Map<String, Object?> get properties => {
    'ram_range': ?ramRange,
    'free_storage_range': ?freeStorageRange,
    'abi': ?abi,
    'webview_major': ?webviewMajor,
    'notif_allowed': ?switch (notificationsAllowed) {
      true => 'yes',
      false => 'no',
      null => null,
    },
  };
}

/// The Dart half of `oc/health` (HealthChannel.kt). Every call answers
/// something safe where the channel is absent (desktop, tests).
class HealthBridge {
  HealthBridge({MethodChannel? channel})
    : _channel = channel ?? const MethodChannel(channelName);

  static const channelName = 'oc/health';
  final MethodChannel _channel;

  Future<HealthDeviceContext?> deviceContext() async {
    if (!platformCapabilities.isAndroid) return null;
    try {
      return HealthDeviceContext.fromMap(
        await _channel.invokeMethod<Object?>('deviceContext'),
      );
    } on MissingPluginException {
      return null;
    } on PlatformException {
      return null;
    }
  }

  /// Android's trim level words (`moderate`, `low`, `critical`).
  void listenMemoryPressure(void Function(String level) onLevel) {
    _channel.setMethodCallHandler((call) async {
      if (call.method != 'memoryPressure') return;
      final args = call.arguments;
      final level = args is Map ? args['level'] : null;
      if (level is String) onLevel(level);
    });
  }
}

/// Process start to first frame, kept as one number until the person's
/// consent is known. Costs a stopwatch and two reads.
abstract final class AppStartClock {
  static final Stopwatch _watch = Stopwatch();
  static Duration? _firstFrame;

  /// First line of `main()`.
  static void begin() {
    if (!_watch.isRunning) _watch.start();
  }

  /// The first frame was drawn.
  static void firstFrameDone() => _firstFrame ??= _watch.elapsed;

  /// `main()` to the first frame; null before it.
  static Duration? get toFirstFrame => _firstFrame;

  /// Time since the first frame was drawn.
  static Duration get sinceFirstFrame =>
      _firstFrame == null ? Duration.zero : _watch.elapsed - _firstFrame!;

  static void resetForTest() {
    _watch
      ..stop()
      ..reset();
    _firstFrame = null;
  }
}

/// Slow frames per kind of page, once per session per page kind.
///
/// Runs only while `usage` is on: [start] registers the framework's frame
/// timing callback, [stop] removes it, so with the switch off nothing is
/// measured and nothing is kept.
class JankMeter {
  JankMeter({
    required this.report,
    double Function()? refreshRate,
    this.minFrames = 30,
  }) : _refreshRate = refreshRate ?? _displayRate;

  final void Function(AnalyticsScreen screen, String pctRange, String hz)
  report;
  final double Function() _refreshRate;

  /// A page with fewer frames than this is not worth a number.
  final int minFrames;

  bool _running = false;
  AnalyticsScreen _screen = AnalyticsScreen.other;
  final Map<AnalyticsScreen, int> _frames = {};
  final Map<AnalyticsScreen, int> _janky = {};
  final Set<AnalyticsScreen> _sent = {};

  bool get running => _running;

  static double _displayRate() {
    try {
      return PlatformDispatcher.instance.views.first.display.refreshRate;
    } catch (_) {
      return 60;
    }
  }

  void start() {
    if (_running) return;
    _running = true;
    SchedulerBinding.instance.addTimingsCallback(onTimings);
  }

  void stop() {
    if (!_running) return;
    _running = false;
    SchedulerBinding.instance.removeTimingsCallback(onTimings);
    _frames.clear();
    _janky.clear();
  }

  /// A page of this kind came into view.
  void screen(AnalyticsScreen next) {
    if (!_running || next == _screen) {
      _screen = next;
      return;
    }
    _flush(_screen);
    _screen = next;
  }

  /// The app went to the background: report what was measured, and let the
  /// next time in front count as a new session.
  void endSession() {
    if (!_running) return;
    _flush(_screen);
    for (final screen in {..._frames.keys}) {
      _flush(screen);
    }
    _sent.clear();
    _frames.clear();
    _janky.clear();
  }

  /// Frame timings, newest batch. Public for tests.
  void onTimings(List<FrameTiming> timings) {
    if (!_running) return;
    final rate = _refreshRate();
    final budgetMicros = 1000000 / (rate > 0 ? rate : 60);
    var janky = 0;
    for (final timing in timings) {
      if (timing.totalSpan.inMicroseconds > budgetMicros) janky++;
    }
    _frames[_screen] = (_frames[_screen] ?? 0) + timings.length;
    _janky[_screen] = (_janky[_screen] ?? 0) + janky;
  }

  void _flush(AnalyticsScreen screen) {
    final frames = _frames[screen] ?? 0;
    if (frames < minFrames || _sent.contains(screen)) return;
    _sent.add(screen);
    final pct = (_janky[screen] ?? 0) * 100 / frames;
    report(
      screen,
      HealthRanges.jankPctOf(pct),
      HealthRanges.refreshHzOf(_refreshRate()),
    );
  }
}

/// The word for an Android exit reason, or null for a normal end (the app
/// quit itself, or was updated) which is not worth a report.
String? exitReasonWord(int reason) => switch (reason) {
  AndroidExitReason.exitSelf || AndroidExitReason.packageUpdated => null,
  AndroidExitReason.crash => 'crash',
  AndroidExitReason.crashNative => 'crash_native',
  AndroidExitReason.anr => 'anr',
  AndroidExitReason.lowMemory => 'low_memory',
  AndroidExitReason.excessiveResourceUsage => 'excessive_resource',
  AndroidExitReason.signaled => 'signaled',
  AndroidExitReason.userRequested => 'user_requested',
  AndroidExitReason.dependencyDied => 'dependency_died',
  _ => 'other',
};

/// The major.minor of a server version string (`2.0.28` becomes `2.0`), or
/// null when it does not start that way.
String? majorMinorOf(String? version) {
  if (version == null) return null;
  final m = RegExp(r'^v?(\d{1,2})\.(\d{1,2})').firstMatch(version.trim());
  return m == null ? null : '${m.group(1)}.${m.group(2)}';
}

/// The preference key of the newest process end already reported.
const healthLastExitKey = 'oc.telemetry.lastExitAt';

/// Wires the health and diagnostics telemetry together once the reporter
/// exists: device context, app start, jank, memory pressure, the custom
/// keys, and the last process end. Every part waits for its own consent
/// and does nothing while that is off.
class HealthTelemetry {
  HealthTelemetry({
    required this.telemetry,
    required this.prefs,
    HealthBridge? bridge,
    AppLifecycleBridge? lifecycle,
    Future<String> Function()? appVersion,
    JankMeter? jank,
    DateTime Function()? now,
  }) : bridge = bridge ?? HealthBridge(),
       lifecycle = lifecycle ?? AppLifecycleBridge(),
       _appVersion = appVersion ?? _packageVersion,
       _now = now ?? DateTime.now {
    this.jank =
        jank ??
        JankMeter(
          report: (screen, pct, hz) => telemetry.track(AnalyticsEvent.jank, {
            'screen': screen,
            'janky_pct_range': pct,
            'refresh_hz': hz,
          }),
        );
  }

  final Telemetry telemetry;
  final SharedPreferences prefs;
  final HealthBridge bridge;
  final AppLifecycleBridge lifecycle;
  final Future<String> Function() _appVersion;
  final DateTime Function() _now;
  late final JankMeter jank;

  HealthDeviceContext? _context;
  bool _coldReported = false;
  bool _exitRead = false;
  bool _started = false;
  final Map<String, DateTime> _memorySent = {};
  final Map<String, Object?> _liveProperties = {};
  final Map<String, Object?> _liveKeys = {};

  static Future<String> _packageVersion() async =>
      (await PackageInfo.fromPlatform()).version;

  /// Starts listening. Idempotent.
  void start() {
    if (_started) return;
    _started = true;
    bridge.listenMemoryPressure(_onMemoryPressure);
    telemetry.consentChanges.addListener(apply);
    apply();
  }

  void dispose() {
    telemetry.consentChanges.removeListener(apply);
    jank.stop();
  }

  /// Brings what runs in line with the two switches.
  void apply() {
    if (telemetry.usageConsented && telemetry.available) {
      jank.start();
      unawaited(_applyUsage());
    } else {
      jank.stop();
    }
    if (telemetry.crashesConsented && telemetry.available) {
      unawaited(_applyCrashes());
    }
  }

  /// Free storage in gigabytes as last read, or null before it was (the
  /// context is read only while `usage` is on).
  double? get freeGigabytes {
    final free = _context?.freeBytes ?? -1;
    return free < 0 ? null : free / (1024 * 1024 * 1024);
  }

  /// The device context for a problem report the person asked for: read
  /// again here whatever the switches say, because the report is theirs to
  /// approve in the preview.
  Future<HealthDeviceContext?> deviceContextForReport() async =>
      _context ??= await bridge.deviceContext();

  Future<void> _applyUsage() async {
    final context = _context ??= await bridge.deviceContext();
    if (!telemetry.usageConsented) return;
    telemetry.setUserProperties({...?context?.properties, ..._liveProperties});
    if (!_coldReported) {
      _coldReported = true;
      final cold = _coldStart(context);
      if (cold != null) {
        telemetry.track(AnalyticsEvent.appStart, {
          'kind': 'cold',
          'ms_range': HealthRanges.appStartMsOf(cold),
        });
        telemetry.performanceTrace(PerfSchema.appColdStart, cold);
      }
    }
  }

  /// Process start to first frame: the process's age less the time since the
  /// first frame when Android said it, else `main()` to the first frame.
  Duration? _coldStart(HealthDeviceContext? context) {
    final firstFrame = AppStartClock.toFirstFrame;
    if (firstFrame == null) return null;
    final uptime = context?.processUptimeMs ?? 0;
    if (uptime > 0) {
      final cold =
          Duration(milliseconds: uptime) - AppStartClock.sinceFirstFrame;
      if (!cold.isNegative && cold >= firstFrame) return cold;
    }
    return firstFrame;
  }

  Future<void> _applyCrashes() async {
    final version = await _safeVersion();
    final context = _context ??= await bridge.deviceContext();
    if (!telemetry.crashesConsented) return;
    telemetry.setHealthKeys({
      'app_version': ?version,
      'build_kind': _buildKind(context),
      ..._liveKeys,
    });
    await _reportLastExit();
  }

  Future<String?> _safeVersion() async {
    try {
      return await _appVersion();
    } catch (_) {
      return null;
    }
  }

  static const _declaredKind = String.fromEnvironment('OC_BUILD_KIND');

  String _buildKind(HealthDeviceContext? context) {
    if (HealthWords.buildKinds.contains(_declaredKind)) return _declaredKind;
    return context?.fromStore == true ? 'store' : 'sideload';
  }

  /// Records Android's note on how the previous process ended, once, as a
  /// non-fatal carrying only the `exit_reason` word.
  Future<void> _reportLastExit() async {
    if (_exitRead) return;
    _exitRead = true;
    try {
      final history = await lifecycle.exitHistory(limit: 3);
      if (!history.supported || history.entries.isEmpty) return;
      final seen = prefs.getInt(healthLastExitKey) ?? 0;
      final newest = history.entries.reduce(
        (a, b) => a.at.isAfter(b.at) ? a : b,
      );
      final at = newest.at.millisecondsSinceEpoch;
      if (at <= seen) return;
      await prefs.setInt(healthLastExitKey, at);
      final word = exitReasonWord(newest.reason);
      if (word == null) return;
      telemetry.recordFailure('process_exit', {'exit_reason': word});
    } catch (_) {}
  }

  /// The facts that change with the connection: server kind, engine state,
  /// number of saved connections, OpenCode version. Called by whoever
  /// watches the connection; cheap when nothing changed.
  void updateConnection({
    required String serverKind,
    required String engineState,
    required int connections,
    String? userServerKind,
    String? serverVersion,
  }) {
    final oc = majorMinorOf(serverVersion);
    final keys = <String, Object?>{
      'server_kind': serverKind,
      'engine_state': engineState,
      'connections': HealthRanges.connectionsOf(connections),
      'oc_server_version': ?oc,
    };
    final properties = <String, Object?>{
      'server_kind': ?userServerKind,
      'oc_server_version': ?oc,
    };
    final changedKeys = !_sameMap(keys, _liveKeys);
    final changedProps = !_sameMap(properties, _liveProperties);
    _liveKeys
      ..clear()
      ..addAll(keys);
    _liveProperties
      ..clear()
      ..addAll(properties);
    if (changedKeys) telemetry.setHealthKeys(keys);
    if (changedProps) {
      telemetry.setUserProperties({...?_context?.properties, ...properties});
    }
  }

  static bool _sameMap(Map<String, Object?> a, Map<String, Object?> b) =>
      a.length == b.length && a.entries.every((e) => b[e.key] == e.value);

  /// A warm start: the app came back to the front. Measured to the first
  /// frame, and only while `usage` is on.
  void warmStart() {
    if (!telemetry.usageConsented || !telemetry.available) return;
    final watch = Stopwatch()..start();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!telemetry.usageConsented) return;
      telemetry.track(AnalyticsEvent.appStart, {
        'kind': 'warm',
        'ms_range': HealthRanges.appStartMsOf(watch.elapsed),
      });
    });
  }

  void _onMemoryPressure(String level) {
    if (!telemetry.usageConsented ||
        !HealthWords.memoryLevels.contains(level)) {
      return;
    }
    final now = _now();
    final last = _memorySent[level];
    if (last != null && now.difference(last) < const Duration(minutes: 5)) {
      return;
    }
    _memorySent[level] = now;
    telemetry.track(AnalyticsEvent.memoryPressure, {'level': level});
  }

  /// For tests.
  void memoryPressureForTest(String level) => _onMemoryPressure(level);
}

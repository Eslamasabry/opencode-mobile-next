import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/foundation.dart' show ChangeNotifier, Listenable;

import '../ui/kit/kit_redact.dart';
import 'analytics_events.dart';
import 'app_log_ring.dart';
import 'health_schema.dart';

/// The app's own crash and error reporting layer. Vendor-neutral: app code
/// depends only on [Telemetry]; a vendor is one swappable [TelemetryAdapter]
/// (see `firebase_crashlytics_adapter.dart`). Replacing the vendor means
/// writing another adapter and changing one provider override.
///
/// Rules every report obeys, enforced here and not left to each call site:
/// - nothing reaches an adapter while consent is off (the default);
/// - report names and key names are fixed identifiers (`setup_step_failed`,
///   `step`), never prose;
/// - key values are short tokens or numbers; anything else is dropped, never
///   cleaned up: no messages, paths, URLs, host names or tokens;
/// - stack frames keep only the code location (`package:` or `dart:` library,
///   line, column and a symbol name), and the whole text goes through
///   [KitRedact] first.
abstract interface class Telemetry {
  /// Whether the person turned on usage and app health (`usage`): the
  /// events, performance numbers and device context. Off until they do.
  bool get usageConsented;

  /// Whether the person turned on crash reports (`crashes`). Off until they
  /// do.
  bool get crashesConsented;

  /// Whether either switch is on.
  bool get consented;

  /// Whether a vendor is built into this build at all. When false the
  /// Settings switches are not offered.
  bool get available;

  /// Changes when either switch does, so a listener can apply what depends
  /// on it (the device context is set when usage turns on).
  Listenable get consentChanges;

  /// Turns `usage` on or off. Off also asks the adapter to forget the
  /// analytics data.
  Future<void> setUsageConsent(bool value);

  /// Turns `crashes` on or off. Off also asks the adapter to forget unsent
  /// crash reports.
  Future<void> setCrashesConsent(bool value);

  /// Both switches at once ("Share both" and the old single switch).
  Future<void> setConsent(bool value);

  /// An uncaught error: only its category name and cleaned stack are kept.
  /// Needs `crashes`.
  void recordCrash(String category, StackTrace? stack, {bool fatal = false});

  /// A failure the app handled but wants counted, such as
  /// `setup_step_failed` with `{step: 'node', exit: 127}`. Needs `crashes`.
  void recordFailure(String name, [Map<String, Object?> keys = const {}]);

  /// A step shown before a crash: only a word listed in
  /// [BreadcrumbWords] (`conn.connect`, `nav.open:chat`). Always kept in the
  /// app's own log ring; reaches the vendor only with `crashes`.
  void breadcrumb(String name);

  /// The Crashlytics custom keys (section A of the contract): only listed
  /// keys with listed values. Needs `crashes`.
  void setHealthKeys(Map<String, Object?> keys);

  /// The device context user properties (section D). Needs `usage`.
  void setUserProperties(Map<String, Object?> properties);

  /// A product analytics event (`setup_step_reached`, `{step: 'node'}`).
  /// Needs `usage`. Only events and values listed in [analyticsSchema] are
  /// sent; anything else is dropped.
  void track(AnalyticsEvent event, [Map<String, Object?> props = const {}]);

  /// Sends the person's problem report: [redactedText] is exactly what they
  /// saw in the preview and agreed to send. Needs no switch (it is asked
  /// every time) but a vendor. Returns the report code, or null when it
  /// could not be sent.
  Future<String?> sendProblemReport(String redactedText);

  /// The code to quote for this install, or null when no vendor is built in.
  /// "Report code" in Settings › About.
  Future<String?> reportCode();
}

/// The vendor side. Receives only text that [TelemetryService] already
/// cleaned, and only while the matching consent is on, except
/// [setCollection] and [problemReport].
abstract interface class TelemetryAdapter {
  /// Whether this build contains the vendor's configuration. Checked once.
  Future<bool> probe();

  /// Starts or stops vendor collection: analytics follows [usage], crash
  /// reports (including native crashes) follow [crashes]. Turning one off
  /// also asks the vendor to forget what it has not sent.
  Future<void> setCollection({required bool usage, required bool crashes});

  Future<void> crash(String category, String stack, {required bool fatal});

  Future<void> failure(String name, Map<String, String> keys);

  Future<void> breadcrumb(String name);

  /// One analytics event whose name and values [TelemetryService] already
  /// checked against [analyticsSchema].
  Future<void> event(String name, Map<String, String> props);

  /// Crash report custom keys, already checked against [healthKeyWords].
  Future<void> setKeys(Map<String, String> keys);

  /// Analytics user properties, already checked against
  /// [healthPropertyWords].
  Future<void> setUserProperties(Map<String, String> properties);

  /// One non-fatal report with [text] (already redacted, shown to and
  /// accepted by the person) attached as logs. [collecting] says whether
  /// crash collection is on; when it is off the adapter sends this one report
  /// and nothing else.
  Future<void> problemReport(String text, {required bool collecting});

  /// The vendor's id for this install, or null when it gives none (analytics
  /// off): only its first 8 characters are ever used.
  Future<String?> appInstanceId();
}

/// The default adapter: does nothing, keeps nothing.
class NoOpTelemetryAdapter implements TelemetryAdapter {
  const NoOpTelemetryAdapter();

  @override
  Future<bool> probe() async => false;
  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {}
  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async {}
  @override
  Future<void> failure(String name, Map<String, String> keys) async {}
  @override
  Future<void> breadcrumb(String name) async {}
  @override
  Future<void> event(String name, Map<String, String> props) async {}
  @override
  Future<void> setKeys(Map<String, String> keys) async {}
  @override
  Future<void> setUserProperties(Map<String, String> properties) async {}
  @override
  Future<void> problemReport(String text, {required bool collecting}) async {}
  @override
  Future<String?> appInstanceId() async => null;
}

/// Several vendors as one adapter (crash reports and analytics). Available
/// when any of them is; each call reaches only the ones that are.
class CombinedTelemetryAdapter implements TelemetryAdapter {
  CombinedTelemetryAdapter(this.adapters);
  final List<TelemetryAdapter> adapters;
  final _present = <TelemetryAdapter>[];

  @override
  Future<bool> probe() async {
    _present.clear();
    for (final adapter in adapters) {
      try {
        if (await adapter.probe()) _present.add(adapter);
      } catch (_) {}
    }
    return _present.isNotEmpty;
  }

  Future<void> _each(Future<void> Function(TelemetryAdapter) call) async {
    for (final adapter in _present) {
      try {
        await call(adapter);
      } catch (_) {}
    }
  }

  @override
  Future<void> setCollection({required bool usage, required bool crashes}) =>
      _each((a) => a.setCollection(usage: usage, crashes: crashes));
  @override
  Future<void> crash(String category, String stack, {required bool fatal}) =>
      _each((a) => a.crash(category, stack, fatal: fatal));
  @override
  Future<void> failure(String name, Map<String, String> keys) =>
      _each((a) => a.failure(name, keys));
  @override
  Future<void> breadcrumb(String name) => _each((a) => a.breadcrumb(name));
  @override
  Future<void> event(String name, Map<String, String> props) =>
      _each((a) => a.event(name, props));
  @override
  Future<void> setKeys(Map<String, String> keys) =>
      _each((a) => a.setKeys(keys));
  @override
  Future<void> setUserProperties(Map<String, String> properties) =>
      _each((a) => a.setUserProperties(properties));
  @override
  Future<void> problemReport(String text, {required bool collecting}) =>
      _each((a) => a.problemReport(text, collecting: collecting));

  @override
  Future<String?> appInstanceId() async {
    for (final adapter in _present) {
      try {
        final id = await adapter.appInstanceId();
        if (id != null && id.isNotEmpty) return id;
      } catch (_) {}
    }
    return null;
  }
}

/// The two consent answers.
typedef TelemetryConsent = ({bool usage, bool crashes});

/// Where the consent choices are kept.
abstract interface class TelemetryConsentStore {
  TelemetryConsent read();
  Future<void> write({required bool usage, required bool crashes});
}

/// Consent held in memory only (tests, and before preferences load).
class MemoryTelemetryConsentStore implements TelemetryConsentStore {
  /// [both] sets the two switches alike.
  MemoryTelemetryConsentStore([bool both = false])
    : _usage = both,
      _crashes = both;
  MemoryTelemetryConsentStore.split({bool usage = false, bool crashes = false})
    : _usage = usage,
      _crashes = crashes;
  bool _usage;
  bool _crashes;
  @override
  TelemetryConsent read() => (usage: _usage, crashes: _crashes);
  @override
  Future<void> write({required bool usage, required bool crashes}) async {
    _usage = usage;
    _crashes = crashes;
  }
}

/// Where the report counter and the local fallback id are kept.
abstract interface class TelemetryReportStore {
  int readCounter();
  Future<void> writeCounter(int value);
  String? readLocalId();
  Future<void> writeLocalId(String value);
}

class MemoryTelemetryReportStore implements TelemetryReportStore {
  int _counter = 0;
  String? _id;
  @override
  int readCounter() => _counter;
  @override
  Future<void> writeCounter(int value) async => _counter = value;
  @override
  String? readLocalId() => _id;
  @override
  Future<void> writeLocalId(String value) async => _id = value;
}

/// [Telemetry] over an adapter: consent gates, cleaning, never throws.
class TelemetryService extends ChangeNotifier implements Telemetry {
  TelemetryService({
    required TelemetryAdapter adapter,
    TelemetryConsentStore? store,
    TelemetryReportStore? reports,
    AppLogRing? ring,
  }) : _adapter = adapter,
       _store = store ?? MemoryTelemetryConsentStore(),
       _reports = reports ?? MemoryTelemetryReportStore(),
       _ring = ring ?? AppLogRing.instance {
    final saved = _store.read();
    _usage = saved.usage;
    _crashes = saved.crashes;
  }

  final TelemetryAdapter _adapter;
  final TelemetryConsentStore _store;
  final TelemetryReportStore _reports;
  final AppLogRing _ring;
  late bool _usage;
  late bool _crashes;
  bool _available = false;

  /// Resolves availability and applies the remembered choices to the vendor.
  /// Call once at start-up; failures leave reporting off.
  Future<void> start() async {
    try {
      _available = await _adapter.probe();
      if (_available) {
        await _adapter.setCollection(usage: _usage, crashes: _crashes);
      }
    } catch (_) {
      _available = false;
    }
    notifyListeners();
  }

  @override
  bool get usageConsented => _usage;

  @override
  bool get crashesConsented => _crashes;

  @override
  bool get consented => _usage || _crashes;

  @override
  bool get available => _available;

  @override
  Listenable get consentChanges => this;

  @override
  Future<void> setUsageConsent(bool value) =>
      _setConsent(usage: value, crashes: _crashes);

  @override
  Future<void> setCrashesConsent(bool value) =>
      _setConsent(usage: _usage, crashes: value);

  @override
  Future<void> setConsent(bool value) =>
      _setConsent(usage: value, crashes: value);

  Future<void> _setConsent({required bool usage, required bool crashes}) async {
    if (usage == _usage && crashes == _crashes) return;
    _usage = usage;
    _crashes = crashes;
    await _store.write(usage: usage, crashes: crashes);
    notifyListeners();
    if (!_available) return;
    try {
      await _adapter.setCollection(usage: usage, crashes: crashes);
    } catch (_) {}
  }

  bool get _crashLive => _crashes && _available;
  bool get _usageLive => _usage && _available;

  @override
  void recordCrash(String category, StackTrace? stack, {bool fatal = false}) {
    if (!_crashLive) return;
    final name = cleanName(category) ?? 'application_error';
    final frames = cleanStack(stack);
    _send(() => _adapter.crash(name, frames, fatal: fatal));
  }

  @override
  void recordFailure(String name, [Map<String, Object?> keys = const {}]) {
    if (!_crashLive) return;
    final clean = cleanName(name);
    if (clean == null) return;
    final safe = cleanKeys(keys);
    _send(() => _adapter.failure(clean, safe));
  }

  @override
  void breadcrumb(String name) {
    if (!BreadcrumbWords.isListed(name)) return;
    // The app's own log keeps the trail on this phone whatever was agreed;
    // it leaves only inside a problem report the person approved.
    _ring.add('crumb $name');
    if (!_crashLive) return;
    _send(() => _adapter.breadcrumb(name));
  }

  @override
  void setHealthKeys(Map<String, Object?> keys) {
    final safe = cleanListed(keys, healthKeyWords, healthKeyPatterns);
    if (safe.isEmpty) return;
    _keys.addAll(safe);
    if (!_crashLive) return;
    _send(() => _adapter.setKeys(safe));
  }

  /// The custom keys last set, for the problem report's header.
  final Map<String, String> _keys = {};
  Map<String, String> get healthKeys => Map.unmodifiable(_keys);

  @override
  void setUserProperties(Map<String, Object?> properties) {
    if (!_usageLive) return;
    final safe = cleanListed(
      properties,
      healthPropertyWords,
      healthPropertyPatterns,
    );
    if (safe.isEmpty) return;
    _send(() => _adapter.setUserProperties(safe));
  }

  @override
  void track(AnalyticsEvent event, [Map<String, Object?> props = const {}]) {
    if (!_usageLive) return;
    final safe = cleanProps(event, props);
    if (safe == null) return;
    _ring.add(
      'event ${event.wire}'
      '${safe.entries.map((e) => ' ${e.key}=${e.value}').join()}',
    );
    _send(() => _adapter.event(event.wire, safe));
  }

  @override
  Future<String?> sendProblemReport(String redactedText) async {
    if (!_available) return null;
    try {
      final code = await _nextReportCode();
      if (code == null) return null;
      _keys['report_code'] = code;
      if (_crashLive) await _adapter.setKeys({'report_code': code});
      await _adapter.problemReport(redactedText, collecting: _crashes);
      return code;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<String?> reportCode() async {
    if (!_available) return null;
    try {
      final prefix = await _installPrefix();
      return prefix == null ? null : '$prefix-${_reports.readCounter()}';
    } catch (_) {
      return null;
    }
  }

  Future<String?> _nextReportCode() async {
    final prefix = await _installPrefix();
    if (prefix == null) return null;
    final next = _reports.readCounter() + 1;
    await _reports.writeCounter(next);
    return '$prefix-$next';
  }

  /// The first 8 characters of the vendor's install id; when the vendor
  /// gives none (analytics is off), 8 random hex characters kept on this
  /// phone.
  Future<String?> _installPrefix() async {
    final fromVendor = await _adapter.appInstanceId();
    final hex = RegExp(r'^[0-9a-f]{8}');
    final match = fromVendor == null
        ? null
        : hex.firstMatch(fromVendor.toLowerCase());
    if (match != null) return match.group(0);
    var local = _reports.readLocalId();
    if (local == null || !hex.hasMatch(local)) {
      local = _randomHex8();
      await _reports.writeLocalId(local);
    }
    return local;
  }

  static String _randomHex8() {
    final random = math.Random.secure();
    return List.generate(8, (_) => random.nextInt(16).toRadixString(16)).join();
  }

  /// [props] reduced to what [analyticsSchema] allows for [event]: listed
  /// keys with listed values (bools as `true`/`false`, enums by name) that
  /// also pass [analyticsValueIsSafe]. Null when the event is not listed.
  static Map<String, String>? cleanProps(
    AnalyticsEvent event,
    Map<String, Object?> props,
  ) {
    final allowed = analyticsSchema[event.wire];
    if (allowed == null || !analyticsValueIsSafe(event.wire)) return null;
    final out = <String, String>{};
    for (final entry in props.entries) {
      final values = allowed[entry.key];
      if (values == null) continue;
      final value = switch (entry.value) {
        final bool v => '$v',
        final AnalyticsNodeVariant v => v.wire,
        final AnalyticsScreen v => v.wire,
        final AnalyticsFeature v => v.wire,
        final Enum v => v.name,
        final String v => v,
        final int v => '$v',
        _ => null,
      };
      if (value == null || !values.contains(value)) continue;
      if (!analyticsValueIsSafe(value)) continue;
      out[entry.key] = value;
    }
    return out;
  }

  /// [raw] reduced to the keys of [words] or [patterns] whose value is
  /// listed or matches. The rest is dropped, never cleaned up.
  static Map<String, String> cleanListed(
    Map<String, Object?> raw,
    Map<String, Set<String>> words,
    Map<String, RegExp> patterns,
  ) {
    final out = <String, String>{};
    for (final entry in raw.entries) {
      final value = switch (entry.value) {
        final String v => v,
        final int v => '$v',
        final bool v => v ? 'yes' : 'no',
        _ => null,
      };
      if (value == null || value.length > 40) continue;
      final listed = words[entry.key];
      final pattern = patterns[entry.key];
      if (listed != null && listed.contains(value)) {
        out[entry.key] = value;
      } else if (pattern != null && pattern.hasMatch(value)) {
        out[entry.key] = value;
      }
    }
    return out;
  }

  void _send(Future<void> Function() call) {
    try {
      unawaited(call().catchError((Object _) {}));
    } catch (_) {}
  }

  static final RegExp _name = RegExp(r'^[a-z][a-z0-9_]{0,39}$');
  static final RegExp _token = RegExp(r'^[A-Za-z0-9_.\-]{1,40}$');
  static final RegExp _frame = RegExp(
    r'^#\d+\s+([A-Za-z0-9_.<>$ ]{0,80}?)\s*\(((?:package:|dart:)[A-Za-z0-9_./\-]+(?::\d+){0,2})\)\s*$',
  );

  /// A report or key name, or null when it is not a plain identifier.
  static String? cleanName(String raw) {
    final name = raw.trim();
    if (_name.hasMatch(name)) return name;
    // Exception categories are sentences ("Invalid state"): identifiers only.
    if (!RegExp(r'^[A-Za-z ]{1,40}$').hasMatch(name)) return null;
    final snake = name.toLowerCase().replaceAll(RegExp(r'[^a-z0-9]+'), '_');
    final trimmed = snake.replaceAll(RegExp(r'^_+|_+$'), '');
    return _name.hasMatch(trimmed) ? trimmed : null;
  }

  /// Keys whose name is an identifier and whose value is a number, a bool or
  /// a short token that [KitRedact] leaves unchanged. The rest are dropped.
  /// A key the contract lists (`exit_reason`, ...) must hold a listed value.
  static Map<String, String> cleanKeys(Map<String, Object?> keys) {
    final out = <String, String>{};
    for (final entry in keys.entries.take(12)) {
      final key = _name.hasMatch(entry.key) ? entry.key : null;
      final value = switch (entry.value) {
        final int v => '$v',
        final bool v => '$v',
        final String v => v,
        _ => null,
      };
      if (key == null || value == null || !_token.hasMatch(value)) continue;
      if (KitRedact.text(value) != value) continue;
      if (healthKeyWords.containsKey(key) ||
          healthKeyPatterns.containsKey(key)) {
        if (cleanListed(
          {key: value},
          healthKeyWords,
          healthKeyPatterns,
        ).isEmpty) {
          continue;
        }
      }
      out[key] = value;
    }
    return out;
  }

  /// `#0 Symbol (package:lib/file.dart:12:3)` lines only, redacted.
  static String cleanStack(StackTrace? stack) {
    if (stack == null) return '';
    final lines = <String>[];
    for (final line in stack.toString().split('\n')) {
      final m = _frame.firstMatch(line.trim());
      if (m == null) continue;
      lines.add('#${lines.length} ${m.group(1)} (${m.group(2)})');
      if (lines.length >= 30) break;
    }
    return KitRedact.text(lines.join('\n'));
  }
}

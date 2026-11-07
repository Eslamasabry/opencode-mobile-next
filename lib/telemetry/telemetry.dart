import 'dart:async';

import '../ui/kit/kit_redact.dart';

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
  /// Whether the person turned reporting on. Off until they do.
  bool get consented;

  /// Whether a vendor is built into this build at all. When false the
  /// Settings switch is not offered.
  bool get available;

  /// Turns reporting on or off and tells the adapter to start or stop
  /// collecting. Off also asks the adapter to forget anything unsent.
  Future<void> setConsent(bool value);

  /// An uncaught error: only its category name and cleaned stack are kept.
  void recordCrash(String category, StackTrace? stack, {bool fatal = false});

  /// A failure the app handled but wants counted, such as
  /// `setup_step_failed` with `{step: 'node', exit: 127}`.
  void recordFailure(String name, [Map<String, Object?> keys = const {}]);

  /// A short trail entry shown before a crash, such as `open_settings`.
  void breadcrumb(String name);
}

/// The vendor side. Receives only text that [TelemetryService] already
/// cleaned, and only while consent is on, except [setCollectionEnabled].
abstract interface class TelemetryAdapter {
  /// Whether this build contains the vendor's configuration. Checked once.
  Future<bool> probe();

  /// Starts or stops vendor collection (including native crashes).
  Future<void> setCollectionEnabled(bool enabled);

  Future<void> crash(String category, String stack, {required bool fatal});

  Future<void> failure(String name, Map<String, String> keys);

  Future<void> breadcrumb(String name);
}

/// The default adapter: does nothing, keeps nothing.
class NoOpTelemetryAdapter implements TelemetryAdapter {
  const NoOpTelemetryAdapter();

  @override
  Future<bool> probe() async => false;
  @override
  Future<void> setCollectionEnabled(bool enabled) async {}
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
}

/// Where the consent choice is kept.
abstract interface class TelemetryConsentStore {
  bool read();
  Future<void> write(bool value);
}

/// Consent held in memory only (tests, and before preferences load).
class MemoryTelemetryConsentStore implements TelemetryConsentStore {
  MemoryTelemetryConsentStore([this._value = false]);
  bool _value;
  @override
  bool read() => _value;
  @override
  Future<void> write(bool value) async => _value = value;
}

/// [Telemetry] over an adapter: consent gate, cleaning, never throws.
class TelemetryService implements Telemetry {
  TelemetryService({
    required TelemetryAdapter adapter,
    TelemetryConsentStore? store,
  }) : _adapter = adapter,
       _store = store ?? MemoryTelemetryConsentStore(),
       _consented = (store ?? MemoryTelemetryConsentStore()).read();

  final TelemetryAdapter _adapter;
  final TelemetryConsentStore _store;
  bool _consented;
  bool _available = false;

  /// Resolves availability and applies the remembered choice to the vendor.
  /// Call once at start-up; failures leave reporting off.
  Future<void> start() async {
    try {
      _available = await _adapter.probe();
      if (_available) await _adapter.setCollectionEnabled(_consented);
    } catch (_) {
      _available = false;
    }
  }

  @override
  bool get consented => _consented;

  @override
  bool get available => _available;

  @override
  Future<void> setConsent(bool value) async {
    if (value == _consented) return;
    _consented = value;
    await _store.write(value);
    if (!_available) return;
    try {
      await _adapter.setCollectionEnabled(value);
    } catch (_) {}
  }

  bool get _live => _consented && _available;

  @override
  void recordCrash(String category, StackTrace? stack, {bool fatal = false}) {
    if (!_live) return;
    final name = cleanName(category) ?? 'application_error';
    final frames = cleanStack(stack);
    _send(() => _adapter.crash(name, frames, fatal: fatal));
  }

  @override
  void recordFailure(String name, [Map<String, Object?> keys = const {}]) {
    if (!_live) return;
    final clean = cleanName(name);
    if (clean == null) return;
    final safe = cleanKeys(keys);
    _send(() => _adapter.failure(clean, safe));
  }

  @override
  void breadcrumb(String name) {
    if (!_live) return;
    final clean = cleanName(name);
    if (clean == null) return;
    _send(() => _adapter.breadcrumb(clean));
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

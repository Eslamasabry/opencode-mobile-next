import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';

import '../platform/app_exit.dart';
import '../platform/thermal.dart';
import '../ui/kit/kit_redact.dart';
import 'perf_trace.dart';

enum ProblemEventKind { error, timing, androidExit, thermal }

/// A redacted, immutable event. Events are ordered by capture, not wall time.
@immutable
class ProblemEvent {
  const ProblemEvent._({
    required this.kind,
    required this.timestamp,
    required this.source,
    required this.message,
    required this.stack,
  });

  final ProblemEventKind kind;
  final DateTime timestamp;
  final String source;
  final String message;
  final String stack;

  Map<String, Object?> toJson() => {
    'kind': kind.name,
    'time': timestamp.toUtc().toIso8601String(),
    'source': source,
    'message': message,
    if (stack.isNotEmpty) 'stack': stack,
  };

  ProblemEvent _redacted() => ProblemEvent._(
    kind: kind,
    timestamp: timestamp.toUtc(),
    source: ReportProblem._text(source, 128),
    message: ReportProblem._text(message, 2048),
    stack: ReportProblem._text(stack, 8192),
  );

  static ProblemEvent _decode(Object? value) {
    if (value is! Map<String, dynamic>) throw const FormatException();
    final kind = ProblemEventKind.values.where((k) => k.name == value['kind']);
    if (kind.isEmpty ||
        value['time'] is! String ||
        value['source'] is! String ||
        value['message'] is! String ||
        (value.containsKey('stack') && value['stack'] is! String)) {
      throw const FormatException();
    }
    return ProblemEvent._(
      kind: kind.single,
      timestamp: DateTime.parse(value['time'] as String),
      source: value['source'] as String,
      message: value['message'] as String,
      stack: value['stack'] as String? ?? '',
    )._redacted();
  }
}

/// App-local diagnostics, never uploaded, logged or notified automatically.
///
/// Open once per app process and share that instance with callers. Register
/// loaded credentials with [KitRedact] before capture. Every write redacts
/// before truncation and commits a flushed snapshot using an atomic rename.
/// A returned record call survives process death without a dispose/flush step.
/// An interrupted replacement leaves the previous committed snapshot intact.
/// This is process-crash durability, not a guarantee against power/disk loss.
///
/// Record/clear calls use bounded synchronous IO deliberately: fatal error
/// handlers cannot rely on an async queue draining before Android exits.
/// Each snapshot and temporary file is at most [maxBytes] UTF-8 bytes; at most
/// two such files coexist during replacement. Oldest events are evicted first.
/// A single event too large for the chosen budget is dropped.
///
/// Observe [storageFailed] to avoid reporting a save/clear as successful. IO
/// errors throw a fixed [StateError], never file paths or raw exception text.
/// [dispose] only releases listeners; [clear] erases the on-device report.
class ReportProblem extends ChangeNotifier {
  ReportProblem._(this._directory, this.maxEntries, this.maxBytes);

  final Directory _directory;
  final int maxEntries;
  final int maxBytes;
  List<ProblemEvent> _entries = const [];
  bool _storageFailed = false;

  File get _file => File('${_directory.path}/report_problem.json');
  File get _pending => File('${_directory.path}/report_problem.pending');

  static bool _exists(File file) =>
      FileSystemEntity.typeSync(file.path, followLinks: false) !=
      FileSystemEntityType.notFound;

  /// Without [directory], uses the app-private application support directory.
  /// The optional directory is dedicated to diagnostics; only our two files
  /// are touched. Unsupported/corrupt/oversized snapshots are discarded.
  static Future<ReportProblem> open({
    Directory? directory,
    int maxEntries = 128,
    int maxBytes = 262144,
  }) async {
    if (maxEntries < 1) throw ArgumentError.value(maxEntries, 'maxEntries');
    if (maxBytes < 1024) throw ArgumentError.value(maxBytes, 'maxBytes');
    try {
      final target =
          directory ??
          Directory(
            '${(await getApplicationSupportDirectory()).path}/diagnostics',
          );
      target.createSync(recursive: true);
      final report = ReportProblem._(target, maxEntries, maxBytes);
      report._restore();
      return report;
    } catch (_) {
      throw StateError('Could not open the diagnostic store.');
    }
  }

  List<ProblemEvent> get entries => List.unmodifiable(_entries);
  bool get storageFailed => _storageFailed;

  void recordError(
    Object error,
    StackTrace? stack, {
    String source = 'app',
    DateTime? at,
  }) => _record(
    ProblemEventKind.error,
    at ?? DateTime.now(),
    source,
    error.toString(),
    stack?.toString() ?? '',
  );

  void recordTiming(PerfSpan span) => _record(
    ProblemEventKind.timing,
    span.wallStart,
    'OCTRACE',
    span.toLogLine(),
    '',
  );

  /// Imports already-buffered timings with one synchronous durable commit.
  /// Live timings still use [recordTiming], preserving same-turn crash evidence.
  /// Order, redaction, eviction and byte limits are identical to individual
  /// records; listeners observe the final imported snapshot once.
  void recordTimings(Iterable<PerfSpan> spans) {
    final next = [..._entries];
    var added = false;
    for (final span in spans) {
      added = true;
      next.add(
        ProblemEvent._(
          kind: ProblemEventKind.timing,
          timestamp: span.wallStart,
          source: 'OCTRACE',
          message: span.toLogLine(),
          stack: '',
        ),
      );
      if (next.length > maxEntries) next.removeAt(0);
    }
    if (!added) return;
    _commit(next);
    notifyListeners();
  }

  void recordAndroidExit(AppExitRecord record) => _record(
    ProblemEventKind.androidExit,
    record.timestamp,
    'android.exit',
    '${record.kind.name} reason=${record.reason} subReason=${record.subReason} '
        'status=${record.status} importance=${record.importance}\n'
        '${record.description}',
    '',
  );

  void recordThermal(ThermalReading reading, {DateTime? at}) => _record(
    ProblemEventKind.thermal,
    at ?? DateTime.now(),
    'android.thermal',
    'status=${reading.status.name} '
        'headroom=${reading.headroom?.isFinite == true ? reading.headroom : 'unknown'}',
    '',
  );

  /// Use at the diagnostic notification boundary; this does not post anything.
  static String notificationText(String text) => _text(text, 300);

  /// Use at the diagnostic log boundary; this does not print anything.
  static String logText(String text) => _text(text, 2048);

  static String _text(String text, int limit) {
    final safe = KitRedact.opaqueTokens(KitRedact.text(text));
    // Rune boundaries avoid leaving a half-surrogate at the truncation point.
    return String.fromCharCodes(safe.runes.take(limit));
  }

  void _record(
    ProblemEventKind kind,
    DateTime timestamp,
    String source,
    String message,
    String stack,
  ) {
    final next = [
      ..._entries,
      ProblemEvent._(
        kind: kind,
        timestamp: timestamp,
        source: source,
        message: message,
        stack: stack,
      ),
    ];
    _commit(next);
    notifyListeners();
  }

  Map<String, Object?> _snapshot(List<ProblemEvent> entries) => {
    'version': 1,
    'entries': [for (final entry in entries) entry.toJson()],
  };

  void _commit(List<ProblemEvent> candidates) {
    final next = candidates
        .skip(
          candidates.length > maxEntries ? candidates.length - maxEntries : 0,
        )
        .map((event) => event._redacted())
        .toList();
    var bytes = utf8.encode(jsonEncode(_snapshot(next)));
    while (bytes.length > maxBytes && next.isNotEmpty) {
      next.removeAt(0);
      bytes = utf8.encode(jsonEncode(_snapshot(next)));
    }
    try {
      _pending.writeAsBytesSync(bytes, flush: true);
      _pending.renameSync(_file.path);
    } catch (_) {
      _storageFailed = true;
      notifyListeners();
      throw StateError('Could not save the diagnostic store.');
    }
    _entries = List.unmodifiable(next);
    _storageFailed = false;
  }

  void _restore() {
    // Never replay an uncommitted write or resurrect it after clear.
    if (_exists(_pending)) _pending.deleteSync();
    if (!_exists(_file)) return;
    if (_file.lengthSync() > maxBytes) {
      _file.deleteSync();
      return;
    }
    List<ProblemEvent> restored;
    try {
      final data = jsonDecode(_file.readAsStringSync());
      if (data is! Map<String, dynamic> ||
          data['version'] != 1 ||
          data['entries'] is! List) {
        throw const FormatException();
      }
      restored = (data['entries'] as List).map(ProblemEvent._decode).toList();
    } on FormatException {
      _file.deleteSync();
      return;
    }
    // Reapply today's redaction and limits before exposing restored data.
    _commit(restored);
  }

  /// Deletes committed and interrupted snapshots. No queued work can recreate
  /// them. On failure, keeps the in-memory history and reports failure.
  void clear() {
    try {
      if (_exists(_pending)) _pending.deleteSync();
      if (_exists(_file)) _file.deleteSync();
    } catch (_) {
      _storageFailed = true;
      notifyListeners();
      throw StateError('Could not clear the diagnostic store.');
    }
    _entries = const [];
    _storageFailed = false;
    notifyListeners();
  }

  /// This exact snapshot can be previewed before Copy/Share. It has no network
  /// destination; callers must never send it without the person's action.
  Map<String, Object?> reportJson() => {
    'app': 'opencode-mobile',
    'privacy': 'redacted-app-local',
    'entryCount': _entries.length,
    'entries': [for (final event in _entries) event._redacted().toJson()],
  };

  String reportText() =>
      const JsonEncoder.withIndent('  ').convert(reportJson());
}

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';

import 'app_diagnostics.dart';

/// A validated capture record; arbitrary error values and stacks have no slot.
@immutable
final class CrashDiagnosticRecord {
  const CrashDiagnosticRecord._({
    required this.source,
    required this.category,
    required this.at,
  });

  final String source;
  final String category;
  final DateTime at;
}

/// An in-memory view of capture consent and validated evidence only.
@immutable
final class CrashDiagnosticsSnapshot {
  CrashDiagnosticsSnapshot._({
    required this.available,
    required this.enabled,
    required this.storageFailed,
    required this.consentEpoch,
    required this.revision,
    required List<CrashDiagnosticRecord> records,
  }) : records = List.unmodifiable(records);

  final bool available;
  final bool enabled;
  final bool storageFailed;
  final int consentEpoch;
  final int revision;
  final List<CrashDiagnosticRecord> records;
}

/// Explicit opt-in, app-private evidence for global errors and Android ANRs.
/// Only fixed categories and timestamps are saved. No throwable value or stack
/// is serialized, even if a credential has not been registered for redaction.
class CrashDiagnosticsController extends ChangeNotifier {
  CrashDiagnosticsController._(this._directory, this._diagnostics);

  static const maxEntries = 20;
  static const maxBytes = 8192;
  static const consentFileName = 'crash-diagnostics-consent';
  static const _recordFileName = 'crash-diagnostics.json';
  static const _pendingFileName = 'crash-diagnostics.pending';
  static const _nativeFileName = 'native-last-crash.properties';
  static const _categories = {
    'Invalid state',
    'Invalid argument',
    'Missing value',
    'Unsupported operation',
    'Application error',
    'Native application error',
    'Permission denied',
    'Input/output failure',
    'Interrupted operation',
    'Android reported that the app stopped responding',
  };
  static const _sources = {'flutter', 'platform', 'widget', 'native', 'anr'};

  final Directory _directory;
  final AppDiagnosticsController _diagnostics;
  List<Map<String, Object>> _records = [];
  int _enabledSince = 0;
  bool _storageFailed = false;
  bool _closed = false;
  bool _clearing = false;
  int _consentEpoch = 0;
  int _evidenceRevision = 0;

  bool get enabled => _enabledSince > 0;
  bool get storageFailed => _storageFailed;
  int get savedCount => _records.length;

  /// The saved records, newest first: fixed source, category and time only.
  /// A read-only view for the consent UI; nothing here touches storage.
  List<CrashRecord> get records => [
    for (final entry in _records.reversed)
      CrashRecord(
        source: entry['source']! as String,
        category: entry['category']! as String,
        time: DateTime.fromMillisecondsSinceEpoch(entry['time']! as int),
      ),
  ];

  /// Does not read or write storage and cannot expose paths or raw errors.
  CrashDiagnosticsSnapshot get snapshot {
    final now = DateTime.now().millisecondsSinceEpoch;
    final records = <CrashDiagnosticRecord>[];
    if (enabled && !_closed && !_storageFailed) {
      for (final entry in _records) {
        final source = entry['source'];
        final category = entry['category'];
        final time = entry['time'];
        if (source is! String ||
            category is! String ||
            time is! int ||
            !_sources.contains(source) ||
            !_categories.contains(category) ||
            time <= 0 ||
            time < _enabledSince ||
            time > now) {
          continue;
        }
        records.add(
          CrashDiagnosticRecord._(
            source: source,
            category: category,
            at: DateTime.fromMillisecondsSinceEpoch(time, isUtc: true),
          ),
        );
      }
    }
    return CrashDiagnosticsSnapshot._(
      available: !_closed,
      enabled: enabled,
      storageFailed: _storageFailed,
      consentEpoch: _consentEpoch,
      revision: _evidenceRevision,
      records: records,
    );
  }

  File _file(String name) {
    final file = File('${_directory.path}/$name');
    if (FileSystemEntity.typeSync(file.path, followLinks: false) ==
        FileSystemEntityType.link) {
      throw const FileSystemException('Diagnostic storage is unavailable.');
    }
    return file;
  }

  /// [directory] must be dedicated app-private storage; injection is for tests.
  static CrashDiagnosticsController open({
    required Directory directory,
    required AppDiagnosticsController diagnostics,
  }) {
    final controller = CrashDiagnosticsController._(directory, diagnostics);
    try {
      directory.createSync(recursive: true);
      controller._enabledSince = controller._readConsent();
      if (controller.enabled) {
        controller._restore();
      } else {
        controller._delete(consentFileName);
        controller._eraseEvidence();
      }
    } catch (_) {
      controller._enabledSince = 0;
      controller._storageFailed = true;
    }
    diagnostics.addListener(controller._onDiagnostics);
    return controller;
  }

  // Bootstrap disk reads run outside the UI isolate so a stuck filesystem
  // cannot starve the launch timeout or freeze the opening frame.
  static Future<CrashDiagnosticsController> _openInBackground({
    required Directory directory,
    required AppDiagnosticsController diagnostics,
    required bool Function() canOpen,
    Object? nativeCrash,
    Object? anrTimestamp,
  }) async {
    final snapshot = await compute(
      _readStartupSnapshot,
      _StartupInput(directory.path, nativeCrash, anrTimestamp),
    );
    final controller = CrashDiagnosticsController._(directory, diagnostics);
    if (!canOpen()) return controller;
    controller._enabledSince = snapshot.enabledSince;
    controller._storageFailed = snapshot.storageFailed;
    controller._records = snapshot.records;
    diagnostics.addListener(controller._onDiagnostics);
    return controller;
  }

  int _readConsent() {
    final consent = _file(consentFileName);
    if (!consent.existsSync()) return 0;
    if (consent.lengthSync() > 32) return 0;
    final value = int.tryParse(consent.readAsStringSync()) ?? 0;
    return value > 0 && value <= DateTime.now().millisecondsSinceEpoch
        ? value
        : 0;
  }

  /// Returns false if storage cannot honor the choice. No raw error is exposed.
  /// A successful disable removes both Flutter and native evidence immediately.
  bool setEnabled(bool value) {
    if (_closed) return false;
    if (value && enabled && !_storageFailed) return true;
    return _replaceConsent(value);
  }

  bool _replaceConsent(bool value) {
    _consentEpoch++;
    _clearing = true;
    try {
      _enabledSince = 0;
      _delete(consentFileName);
      _eraseEvidence();
      _diagnostics.clear();
      if (value) {
        final now = DateTime.now().millisecondsSinceEpoch;
        _atomicWrite(consentFileName, '$now');
        _enabledSince = now;
      }
      _storageFailed = false;
      _notify();
      return true;
    } catch (_) {
      _enabledSince = 0;
      _storageFailed = true;
      _notify();
      return false;
    } finally {
      _clearing = false;
    }
  }

  /// Erases evidence but retains consent. The new consent epoch prevents
  /// Android's historical ANR from reappearing after clear and restart.
  bool clear() {
    if (_closed || _clearing) return false;
    return _replaceConsent(enabled);
  }

  /// Safe in global error handlers: never reads error.toString() or stack text.
  void capture(Object error, StackTrace? stack, String source) {
    if (!enabled || _closed) return;
    _save(source, appErrorCategory(error), DateTime.now());
  }

  /// Imports only numeric evidence supplied by the private native channel.
  void importAndroidAnr(Object? millis) {
    if (millis is! int || millis < _enabledSince || !enabled) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    if (millis <= 0 || millis > now) return;
    _save(
      'anr',
      'Android reported that the app stopped responding',
      DateTime.fromMillisecondsSinceEpoch(millis),
    );
  }

  void importNativeCrash(Object? raw) {
    if (raw is! Map || !enabled) return;
    final millis = raw['timestamp'];
    if (millis is! int ||
        millis < _enabledSince ||
        millis <= 0 ||
        millis > DateTime.now().millisecondsSinceEpoch) {
      return;
    }
    _save(
      'native',
      'Native application error',
      DateTime.fromMillisecondsSinceEpoch(millis),
    );
  }

  void _save(String source, String category, DateTime timestamp) {
    if (!_sources.contains(source) || !_categories.contains(category)) return;
    try {
      final millis = timestamp.millisecondsSinceEpoch;
      if (_records.any(
        (entry) => entry['source'] == source && entry['time'] == millis,
      )) {
        return;
      }
      final next = [
        ..._records,
        <String, Object>{
          'source': source,
          'category': category,
          'time': millis,
        },
      ];
      while (next.length > maxEntries ||
          utf8.encode(jsonEncode(next)).length > maxBytes) {
        next.removeAt(0);
      }
      _atomicWrite(_recordFileName, jsonEncode(next));
      _records = next;
      _evidenceRevision++;
      _storageFailed = false;
      _diagnostics.record(
        category,
        null,
        source: 'crash.$source',
        at: timestamp,
      );
      _notify();
    } catch (_) {
      _storageFailed = true;
      _notify();
    }
  }

  void _restore() {
    _delete(_pendingFileName);
    final file = _file(_recordFileName);
    if (!file.existsSync()) return;
    if (file.lengthSync() > maxBytes) {
      _delete(_recordFileName);
      return;
    }
    try {
      final raw = jsonDecode(file.readAsStringSync());
      if (raw is! List || raw.length > maxEntries) {
        throw const FormatException();
      }
      final now = DateTime.now().millisecondsSinceEpoch;
      for (final entry in raw) {
        if (entry is! Map ||
            entry.length != 3 ||
            !_sources.contains(entry['source']) ||
            !_categories.contains(entry['category']) ||
            entry['time'] is! int ||
            (entry['time'] as int) < _enabledSince ||
            (entry['time'] as int) > now) {
          throw const FormatException();
        }
      }
      _records = [
        for (final entry in raw) Map<String, Object>.from(entry as Map),
      ];
      for (final entry in _records) {
        _diagnostics.record(
          entry['category']!,
          null,
          source: 'crash.${entry['source']}',
          at: DateTime.fromMillisecondsSinceEpoch(entry['time']! as int),
        );
      }
    } catch (_) {
      _records = [];
      _delete(_recordFileName);
    }
  }

  void _atomicWrite(String name, String text) {
    if (utf8.encode(text).length > maxBytes) throw const FormatException();
    final pending = _file(_pendingFileName);
    pending.writeAsStringSync(text, flush: true);
    pending.renameSync(_file(name).path);
  }

  void _delete(String name) {
    final file = _file(name);
    if (file.existsSync()) file.deleteSync();
  }

  void _eraseEvidence() {
    _records = [];
    _evidenceRevision++;
    for (final name in [
      _recordFileName,
      _pendingFileName,
      _nativeFileName,
      '$_nativeFileName.tmp',
    ]) {
      _delete(name);
    }
  }

  void _onDiagnostics() {
    if (_diagnostics.isEmpty && !_clearing) clear();
  }

  void _notify() {
    if (_closed) return;
    try {
      notifyListeners();
    } catch (_) {}
  }

  @override
  void dispose() {
    _closed = true;
    _diagnostics.removeListener(_onDiagnostics);
    super.dispose();
  }
}

/// One saved crash record as the UI reads it. [source] is one of `flutter`,
/// `platform`, `widget`, `native` or `anr`; [category] is a fixed English
/// category, never an error message. Both belong under Details.
@immutable
class CrashRecord {
  const CrashRecord({
    required this.source,
    required this.category,
    required this.time,
  });

  final String source;
  final String category;
  final DateTime time;

  @override
  bool operator ==(Object other) =>
      other is CrashRecord &&
      other.source == source &&
      other.category == category &&
      other.time == time;

  @override
  int get hashCode => Object.hash(source, category, time);
}

/// Tests control elapsed time and deadline delivery independently of host load.
@visibleForTesting
abstract interface class CrashDiagnosticsStartupTiming {
  Duration get elapsed;

  Future<CrashDiagnosticsController?> timeout(
    Future<CrashDiagnosticsController?> pending,
    Duration budget, {
    required CrashDiagnosticsController? Function() onTimeout,
  });
}

class _MonotonicStartupTiming implements CrashDiagnosticsStartupTiming {
  final _stopwatch = Stopwatch()..start();

  @override
  Duration get elapsed => _stopwatch.elapsed;

  @override
  Future<CrashDiagnosticsController?> timeout(
    Future<CrashDiagnosticsController?> pending,
    Duration budget, {
    required CrashDiagnosticsController? Function() onTimeout,
  }) => pending.timeout(budget, onTimeout: onTimeout);
}

class CrashDiagnosticsStartup {
  static const launchBudget = Duration(milliseconds: 300);
  static const _channel = MethodChannel('oc/crash_diagnostics');
  static CrashDiagnosticsController? current;
  static Future<CrashDiagnosticsController?>? _opening;
  static int _generation = 0;
  static Completer<CrashDiagnosticsController?> _readiness = Completer();

  /// Actual store readiness, independent of the bounded launch wait.
  /// A slow open keeps this pending; failure or reset resolves to null.
  static Future<CrashDiagnosticsController?> get ready => _readiness.future;

  static void capture(
    AppDiagnosticsController diagnostics,
    Object error,
    StackTrace? stack,
    String source,
  ) {
    final controller = current;
    if (controller == null) {
      diagnostics.record(appErrorCategory(error), null, source: source);
    } else {
      controller.capture(error, stack, source);
    }
  }

  static Future<CrashDiagnosticsController?> start(
    AppDiagnosticsController diagnostics, {
    @visibleForTesting MethodChannel? nativeChannel,
    @visibleForTesting CrashDiagnosticsStartupTiming? timing,
  }) {
    if (_opening != null) return _opening!;
    return _opening = _startBounded(
      diagnostics,
      nativeChannel,
      timing,
      _readiness,
    );
  }

  static Future<CrashDiagnosticsController?> _startBounded(
    AppDiagnosticsController diagnostics,
    MethodChannel? nativeChannel,
    CrashDiagnosticsStartupTiming? timing,
    Completer<CrashDiagnosticsController?> readiness,
  ) {
    final generation = _generation;
    final clock = timing ?? _MonotonicStartupTiming();
    // The budget bounds only the launch caller. Opening stays off the UI
    // isolate and may publish later, unless this generation was reset.
    bool canOpen() => generation == _generation;
    final pending = _open(diagnostics, nativeChannel, canOpen).then((
      controller,
    ) {
      if (!canOpen()) {
        controller?.dispose();
        return null;
      }
      final restored = controller == null
          ? <Map<String, Object>>[]
          : List<Map<String, Object>>.of(controller._records);
      current = controller;
      if (!readiness.isCompleted) readiness.complete(controller);
      // Settle readiness before notifying legacy synchronous report writers.
      Timer.run(() {
        if (!canOpen() || controller == null || controller._closed) return;
        for (final entry in restored) {
          // A synchronous diagnostic listener may reset startup mid-replay.
          if (!canOpen() || controller._closed) break;
          if (!controller._records.contains(entry)) continue;
          diagnostics.record(
            entry['category']!,
            null,
            source: 'crash.${entry['source']}',
            at: DateTime.fromMillisecondsSinceEpoch(entry['time']! as int),
          );
        }
      });
      return controller;
    });
    return clock.timeout(pending, launchBudget, onTimeout: () => null);
  }

  @visibleForTesting
  static void resetForTesting() {
    _generation++;
    current?.dispose();
    current = null;
    _opening = null;
    if (!_readiness.isCompleted) _readiness.complete(null);
    _readiness = Completer();
  }

  static Future<CrashDiagnosticsController?> _open(
    AppDiagnosticsController diagnostics,
    MethodChannel? nativeChannel,
    bool Function() canOpen,
  ) async {
    try {
      Map<Object?, Object?>? native;
      final channel =
          nativeChannel ?? (!kIsWeb && Platform.isAndroid ? _channel : null);
      if (channel != null) {
        native = await channel.invokeMapMethod<Object?, Object?>('open');
      }
      // A reply from a reset generation must not attach or touch evidence.
      if (!canOpen()) return null;
      final path = native?['directory'];
      final directory = path is String
          ? Directory(path)
          : Directory(
              '${(await getApplicationSupportDirectory()).path}/crash-diagnostics',
            );
      if (!canOpen()) return null;
      final controller = await CrashDiagnosticsController._openInBackground(
        directory: directory,
        diagnostics: diagnostics,
        canOpen: canOpen,
        nativeCrash: native?['nativeCrash'],
        anrTimestamp: native?['anrTimestamp'],
      );
      if (!canOpen()) {
        controller.dispose();
        return null;
      }
      return controller;
    } catch (_) {
      return null;
    }
  }
}

class _StartupSnapshot {
  const _StartupSnapshot(this.enabledSince, this.storageFailed, this.records);
  final int enabledSince;
  final bool storageFailed;
  final List<Map<String, Object>> records;
}

class _StartupInput {
  const _StartupInput(this.path, this.nativeCrash, this.anrTimestamp);
  final String path;
  final Object? nativeCrash;
  final Object? anrTimestamp;
}

_StartupSnapshot _readStartupSnapshot(_StartupInput input) {
  final diagnostics = AppDiagnosticsController();
  final controller = CrashDiagnosticsController.open(
    directory: Directory(input.path),
    diagnostics: diagnostics,
  );
  try {
    controller.importNativeCrash(input.nativeCrash);
    controller.importAndroidAnr(input.anrTimestamp);
    return _StartupSnapshot(
      controller._enabledSince,
      controller._storageFailed,
      controller._records,
    );
  } finally {
    controller.dispose();
    diagnostics.dispose();
  }
}

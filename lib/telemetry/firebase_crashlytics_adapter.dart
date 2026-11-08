import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_crashlytics/firebase_crashlytics.dart';

import 'telemetry.dart';

/// Firebase Crashlytics as one [TelemetryAdapter]. Only `firebase_core` and
/// `firebase_crashlytics`; analytics events go to
/// `firebase_analytics_adapter.dart`. No performance or remote config.
///
/// Automatic collection is off in the manifest; this adapter turns it on only
/// when [setCollectionEnabled] says the person agreed. When the build has no
/// Firebase configuration (public builds without the private file)
/// [probe] is false and nothing here ever runs.
class FirebaseCrashlyticsAdapter implements TelemetryAdapter {
  FirebaseCrashlytics? _crashlytics;

  @override
  Future<bool> probe() async {
    try {
      if (Firebase.apps.isEmpty) await Firebase.initializeApp();
      _crashlytics = FirebaseCrashlytics.instance;
      return true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<void> setCollection({
    required bool usage,
    required bool crashes,
  }) async {
    final crashlytics = _crashlytics;
    if (crashlytics == null) return;
    await crashlytics.setCrashlyticsCollectionEnabled(crashes);
    if (!crashes) await crashlytics.deleteUnsentReports();
  }

  @override
  Future<void> crash(
    String category,
    String stack, {
    required bool fatal,
  }) async {
    await _crashlytics?.recordError(
      _Named(category),
      StackTrace.fromString(stack),
      fatal: fatal,
    );
  }

  @override
  Future<void> failure(String name, Map<String, String> keys) async {
    final crashlytics = _crashlytics;
    if (crashlytics == null) return;
    for (final entry in keys.entries) {
      await crashlytics.setCustomKey(entry.key, entry.value);
    }
    await crashlytics.recordError(
      _Named(name),
      StackTrace.fromString(''),
      reason: name,
    );
  }

  @override
  Future<void> breadcrumb(String name) async => _crashlytics?.log(name);

  @override
  Future<void> setKeys(Map<String, String> keys) async {
    final crashlytics = _crashlytics;
    if (crashlytics == null) return;
    for (final entry in keys.entries) {
      await crashlytics.setCustomKey(entry.key, entry.value);
    }
  }

  /// The person's reviewed problem report, as one non-fatal with the text
  /// attached as Crashlytics logs. Crashlytics keeps the newest 64 KB of
  /// logs, so the text is cut from its start to fit. With crash reports off,
  /// anything unsent is deleted first and only this report is sent.
  @override
  Future<void> problemReport(String text, {required bool collecting}) async {
    final crashlytics = _crashlytics;
    if (crashlytics == null) return;
    if (!collecting) await crashlytics.deleteUnsentReports();
    for (final chunk in logChunks(text)) {
      crashlytics.log(chunk);
    }
    await crashlytics.recordError(
      _Named('problem_report'),
      StackTrace.fromString(''),
      reason: 'problem_report',
    );
    if (!collecting) await crashlytics.sendUnsentReports();
  }

  /// [text] as log lines of at most 1 KB each, newest kept, within 60 KB.
  static List<String> logChunks(String text, {int budget = 60 * 1024}) {
    final lines = text.split('\n');
    final kept = <String>[];
    var used = 0;
    for (final line in lines.reversed) {
      var piece = line;
      if (piece.length > 1000) piece = piece.substring(0, 1000);
      used += piece.length + 1;
      if (used > budget) break;
      kept.add(piece);
    }
    return kept.reversed.toList();
  }

  /// Analytics is not this vendor's.
  @override
  Future<void> event(String name, Map<String, String> props) async {}
  @override
  Future<void> setUserProperties(Map<String, String> properties) async {}
  @override
  Future<String?> appInstanceId() async => null;
}

/// An error whose entire text is a fixed identifier.
class _Named implements Exception {
  _Named(this.name);
  final String name;
  @override
  String toString() => name;
}

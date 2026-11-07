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
  Future<void> setCollectionEnabled(bool enabled) async {
    final crashlytics = _crashlytics;
    if (crashlytics == null) return;
    await crashlytics.setCrashlyticsCollectionEnabled(enabled);
    if (!enabled) await crashlytics.deleteUnsentReports();
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

  /// Analytics events are not crash reports.
  @override
  Future<void> event(String name, Map<String, String> props) async {}
}

/// An error whose entire text is a fixed identifier.
class _Named implements Exception {
  _Named(this.name);
  final String name;
  @override
  String toString() => name;
}

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_performance/firebase_performance.dart';

import 'perf_traces.dart';
import 'telemetry.dart';

/// Firebase Performance Monitoring as one [TelemetryAdapter], for the fixed
/// traces in `perf_traces.dart` only.
///
/// Collection is off in the manifest (`firebase_performance_collection_deactivated`
/// true) and turned on only while `usage` is on. The Performance Gradle plugin
/// is never applied, so no HTTP request is traced and no URL is recorded.
/// Without Firebase configuration [probe] is false and nothing here runs.
class FirebasePerformanceAdapter
    implements TelemetryAdapter, PerformanceAdapter {
  FirebasePerformance? _performance;
  bool _enabled = false;

  @override
  Future<bool> probe() async {
    try {
      if (Firebase.apps.isEmpty) await Firebase.initializeApp();
      _performance = FirebasePerformance.instance;
      return true;
    } catch (_) {
      return false;
    }
  }

  @override
  Future<void> setCollection({required bool usage, required bool crashes}) =>
      setPerformanceCollection(usage);

  @override
  Future<void> setPerformanceCollection(bool enabled) async {
    final performance = _performance;
    if (performance == null) return;
    _enabled = enabled;
    await performance.setPerformanceCollectionEnabled(enabled);
  }

  @override
  Future<void> perfTrace(
    String name,
    Map<String, String> attributes,
    int durationMs,
  ) async {
    final performance = _performance;
    if (performance == null || !_enabled) return;
    final trace = performance.newTrace(name);
    await trace.start();
    for (final entry in attributes.entries) {
      trace.putAttribute(entry.key, entry.value);
    }
    trace.setMetric('duration_ms', durationMs);
    await trace.stop();
  }

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

import 'health_schema.dart';

/// Firebase Performance Monitoring, the fixed part (section C2 of
/// docs/telemetry/health-and-diagnostics.md).
///
/// The vendor side is a [PerformanceAdapter]. Only the traces listed in
/// [PerfSchema] ever reach it, with attributes whose values are listed words;
/// anything else is dropped, never cleaned up. There are no automatic network
/// traces: the Performance Gradle plugin, which would record request URLs, is
/// not applied (`test/perf_privacy_build_test.dart` checks that).
abstract interface class PerformanceAdapter {
  /// Starts or stops all Performance collection. Follows `usage` exactly.
  Future<void> setPerformanceCollection(bool enabled);

  /// One finished custom trace whose [name] and [attributes] were already
  /// checked by [PerfSchema.clean]. [durationMs] is the measured time, kept
  /// as the numeric metric `duration_ms`.
  Future<void> perfTrace(
    String name,
    Map<String, String> attributes,
    int durationMs,
  );
}

/// A cleaned trace.
typedef PerfTraceRecord = ({String name, Map<String, String> attributes});

abstract final class PerfSchema {
  static const connConnect = 'conn_connect';
  static const sendFirstReply = 'send_first_reply';
  static const agentStart = 'agent_start';
  static const appColdStart = 'app_cold_start';
  static const linuxSetupPrefix = 'linux_setup_';

  /// `linux_setup_<step>` for one setup step word.
  static String linuxSetup(String step) => '$linuxSetupPrefix$step';

  /// Every trace name that may be sent.
  static final Set<String> names = {
    connConnect,
    sendFirstReply,
    agentStart,
    appColdStart,
    for (final step in HealthWords.linuxSteps) linuxSetup(step),
  };

  /// The attributes each trace may carry and their listed values.
  static final Map<String, Set<String>> _linuxAttrs = {
    'step': HealthWords.linuxSteps,
    'result': HealthWords.okFail,
  };

  static Map<String, Set<String>>? _allowed(String name) {
    if (name == connConnect) {
      return {
        'server_kind': HealthWords.serverKinds,
        'result': HealthWords.okFail,
      };
    }
    if (name == sendFirstReply) return {'server_kind': HealthWords.serverKinds};
    if (name == agentStart) {
      return {'agent': HealthWords.agents, 'result': HealthWords.okFail};
    }
    if (name == appColdStart) return const {};
    if (names.contains(name)) return _linuxAttrs;
    return null;
  }

  /// [name] and the listed [attributes] of it, or null when the trace is not
  /// listed. Unlisted attribute names and values are dropped.
  static PerfTraceRecord? clean(String name, Map<String, Object?> attributes) {
    final allowed = _allowed(name);
    if (allowed == null) return null;
    final out = <String, String>{};
    for (final entry in attributes.entries) {
      final values = allowed[entry.key];
      final value = entry.value;
      if (values == null || value is! String || !values.contains(value)) {
        continue;
      }
      out[entry.key] = value;
    }
    return (name: name, attributes: out);
  }
}

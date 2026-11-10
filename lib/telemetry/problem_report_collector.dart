import '../domain/app_exit_history.dart' show AppExitEntry;
import '../platform/app_exit.dart' show AppLifecycleBridge;
import 'app_log_ring.dart';
import 'health_probe.dart';
import 'report_redaction.dart';
import 'telemetry.dart';

/// Builds the text of "Send a problem report"
/// (docs/telemetry/health-and-diagnostics.md section E): the last 500 lines of
/// the app's own log, the custom keys, the recent process ends and the device
/// context, all of them words, numbers and ranges. No description field: free
/// text is the one thing this report does not take.
///
/// [build] returns the text already passed through [ReportRedaction], which is
/// what the person previews and what [Telemetry.sendProblemReport] sends.
class ProblemReportCollector {
  ProblemReportCollector({
    required this.telemetry,
    AppLogRing? ring,
    this.health,
    AppLifecycleBridge? lifecycle,
    this.keys,
    DateTime Function()? now,
  }) : ring = ring ?? AppLogRing.instance,
       lifecycle = lifecycle ?? AppLifecycleBridge(),
       _now = now ?? DateTime.now;

  final Telemetry telemetry;
  final AppLogRing ring;
  final HealthTelemetry? health;
  final AppLifecycleBridge lifecycle;

  /// The custom keys to show; the service's own by default.
  final Map<String, String> Function()? keys;
  final DateTime Function() _now;

  Future<String> build() async {
    final out = StringBuffer()
      ..writeln('OpenCode Mobile problem report')
      ..writeln('Made: ${_utc(_now())}')
      ..writeln();

    out.writeln('Keys');
    final shown = keys?.call() ?? _keysOf(telemetry);
    if (shown.isEmpty) {
      out.writeln('  none');
    } else {
      for (final entry in shown.entries) {
        out.writeln('  ${entry.key}=${entry.value}');
      }
    }
    out.writeln();

    out.writeln('Device');
    final device = await health?.deviceContextForReport();
    final properties = device?.properties ?? const <String, Object?>{};
    if (properties.isEmpty) {
      out.writeln('  none');
    } else {
      for (final entry in properties.entries) {
        out.writeln('  ${entry.key}=${entry.value}');
      }
    }
    out.writeln();

    out.writeln('Process ends');
    final exits = await _exits();
    if (exits.isEmpty) {
      out.writeln('  none');
    } else {
      for (final exit in exits) {
        out.writeln(
          '  ${_utc(exit.at)} ${exitReasonWord(exit.reason) ?? 'normal'}',
        );
      }
    }
    out.writeln();

    final lines = ring.lines;
    final recent = lines.length > 500
        ? lines.sublist(lines.length - 500)
        : lines;
    out.writeln('Log (last ${recent.length} lines)');
    if (recent.isEmpty) out.writeln('  empty');
    for (final line in recent) {
      out.writeln('  $line');
    }
    return ReportRedaction.redact(out.toString());
  }

  Future<List<AppExitEntry>> _exits() async {
    try {
      final history = await lifecycle.exitHistory(limit: 5);
      return history.supported ? history.entries : const [];
    } catch (_) {
      return const [];
    }
  }

  static Map<String, String> _keysOf(Telemetry telemetry) =>
      telemetry is TelemetryService ? telemetry.healthKeys : const {};

  static String _utc(DateTime time) {
    final t = time.toUtc();
    String two(int v) => v.toString().padLeft(2, '0');
    return '${t.year}-${two(t.month)}-${two(t.day)} '
        '${two(t.hour)}:${two(t.minute)}:${two(t.second)} UTC';
  }
}

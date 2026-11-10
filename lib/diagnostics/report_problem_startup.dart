import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../platform/app_exit.dart';
import '../platform/thermal.dart';
import 'app_diagnostics.dart';
import 'report_problem.dart';
import 'report_problem_capture.dart';

/// The app's one persisted problem report, opened once per process by
/// `main()` after the first frame. In-memory error capture is installed before
/// `runApp`, so opening storage can import early failures without blocking paint.
///
/// It keeps what a crash would otherwise take with it: errors (through the
/// shared [AppDiagnosticsController]), finished OCTRACE timings, the typed
/// Android exit record the next start learns about, and thermal status
/// changes. Everything is redacted by [ReportProblem] before it reaches disk.
///
/// Opening is asynchronous (the app-support directory); errors and timings
/// from before it finishes are still in the controller and `PerfTrace`
/// buffers and are imported on attach. Callers that start earlier use
/// [ready]; a failed open leaves [current] null and records one fixed line
/// in the diagnostics controller, never the raw filesystem error.
///
/// Clearing the diagnostics controller (the diagnostics screen's Clear
/// action) erases the persisted report as well; see [ReportProblemCapture].
class ReportProblemStartup {
  ReportProblemStartup._(this.report, this.capture);

  /// The fixed line recorded when the store cannot be opened.
  static const openFailedMessage =
      'The saved problem report is unavailable on this device.';

  /// The diagnostics source of [openFailedMessage].
  static const source = 'report-problem';

  final ReportProblem report;
  final ReportProblemCapture capture;
  ThermalStatus? _lastThermal;
  bool _closed = false;

  static Future<ReportProblemStartup?>? _opening;
  static ReportProblemStartup? _current;

  /// The open report, or null before [start] finished or after it failed.
  static ReportProblemStartup? get current => _current;

  /// The app's in-memory diagnostics given to [start], for a page opened
  /// without a connection (Report a problem); null before [start].
  static AppDiagnosticsController? get diagnostics => _diagnostics;
  static AppDiagnosticsController? _diagnostics;

  /// Completes with [current] once [start] has run; null when it never ran.
  static Future<ReportProblemStartup?> get ready =>
      _opening ?? Future<ReportProblemStartup?>.value();

  /// Opens the report and attaches it to [diagnostics]. Later calls return
  /// the first call's result. [directory] is for tests.
  static Future<ReportProblemStartup?> start(
    AppDiagnosticsController diagnostics, {
    Directory? directory,
    int maxEntries = 128,
    int maxBytes = 262144,
  }) {
    _diagnostics ??= diagnostics;
    return _opening ??= _open(diagnostics, directory, maxEntries, maxBytes);
  }

  static Future<ReportProblemStartup?> _open(
    AppDiagnosticsController diagnostics,
    Directory? directory,
    int maxEntries,
    int maxBytes,
  ) async {
    try {
      final report = await ReportProblem.open(
        directory: directory,
        maxEntries: maxEntries,
        maxBytes: maxBytes,
      );
      final capture = ReportProblemCapture(
        report: report,
        diagnostics: diagnostics,
        typedAndroidExits: true,
      );
      return _current = ReportProblemStartup._(report, capture);
    } catch (_) {
      diagnostics.record(openFailedMessage, null, source: source);
      return null;
    }
  }

  /// Keeps Android's record of why the previous process ended. The native
  /// side reports each exit once; a record already kept is not added again.
  void recordAndroidExit(AppExitRecord record) {
    if (_closed) return;
    final at = record.timestamp.toUtc();
    final kept = report.entries.any(
      (entry) =>
          entry.kind == ProblemEventKind.androidExit && entry.timestamp == at,
    );
    if (kept) return;
    _safely(() => report.recordAndroidExit(record));
  }

  /// Keeps a thermal status change. Repeated readings of the same status
  /// and unknown readings (no platform data) add nothing.
  void recordThermal(ThermalReading reading, {DateTime? at}) {
    if (_closed || reading.status == ThermalStatus.unknown) return;
    if (reading.status == _lastThermal) return;
    _lastThermal = reading.status;
    _safely(() => report.recordThermal(reading, at: at));
  }

  // ReportProblem exposes storageFailed; a failing store must never throw
  // into the start or the thermal guard it is diagnosing.
  void _safely(void Function() action) {
    try {
      action();
    } catch (_) {}
  }

  /// Detaches capture. Keeps the persisted history.
  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    await capture.close();
    report.dispose();
  }

  /// Closes the open report and forgets it, so a test can start again.
  @visibleForTesting
  static Future<void> resetForTesting() async {
    final opening = _opening;
    _opening = null;
    _current = null;
    _diagnostics = null;
    await (await opening)?.close();
  }
}

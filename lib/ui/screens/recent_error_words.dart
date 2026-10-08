import '../../diagnostics/report_problem.dart' show ProblemEventKind;
import '../../feedback/problem_report.dart';
import '../../l10n/app_localizations.dart';

/// The diagnostics sources the opt-in crash store writes
/// (`CrashDiagnosticsController`): listed by CrashReportsSection, so the
/// recent errors leave them out. `crash.last` (the native summary
/// `AppExitRecovery` records) is not one of them and stays in the list.
const crashStoreSources = {
  'crash.flutter',
  'crash.platform',
  'crash.widget',
  'crash.native',
  'crash.anr',
};

/// The plain title of a recent error on Report a problem (no-raw-errors
/// rule): what the person noticed, chosen by where the error came from.
/// The redacted message and the source id stay under the row's Details;
/// the problem report itself still carries them unchanged.
String recentErrorTitle(AppLocalizations copy, ProblemReportEvent event) {
  if (event.kind == ProblemEventKind.androidExit) {
    return copy.recentErrorAndroidExit;
  }
  final source = event.source;
  if (source == 'sse' || source.startsWith('sse.') || source == 'events') {
    return copy.recentErrorConnection;
  }
  if (source == 'thermal' ||
      source.startsWith('thermal.') ||
      source == 'android.thermal') {
    return copy.recentErrorTemperature;
  }
  return switch (source) {
    'flutter' || 'widget' || 'crash.widget' => copy.recentErrorScreen,
    'crash.last' || 'crash.native' => copy.crashKindClosed,
    'crash.anr' => copy.crashKindNotResponding,
    'android.exit' => copy.recentErrorAndroidExit,
    'bootstrap' ||
    'bootstrap-reset' ||
    'notify-migration' => copy.recentErrorStartup,
    'report-problem' => copy.recentErrorReportStore,
    _ => copy.recentErrorGeneric,
  };
}

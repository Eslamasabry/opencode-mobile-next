import 'package:flutter/foundation.dart';

import 'app_exit_history.dart';
import 'background_pause.dart';
import 'crash_report.dart';

export 'app_exit_history.dart';
export 'background_pause.dart';
export 'crash_report.dart';
export 'diagnostics_error.dart';

/// Device-local facts and explicit actions, independent of server protocol.
abstract interface class AppDiagnosticsGateway implements Listenable {
  Future<AppExitHistory> exitHistory({int limit = 10});
  BackgroundPauseState get backgroundPause;
  Future<BackgroundPauseState> refreshBackgroundPause();
  Future<BackgroundResumeResult> resumeBackground();
  bool get shareSupported;
  Future<CrashReportResult> previewCrashReport();
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview);
}

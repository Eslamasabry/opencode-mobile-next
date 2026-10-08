import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:opencode_mobile/domain/app_diagnostics_gateway.dart';

/// The device diagnostics gateway as the notice sees it: the pause state and
/// a Resume that answers when the test says so.
class FakePauseGateway extends ChangeNotifier implements AppDiagnosticsGateway {
  FakePauseGateway(this.pause);

  BackgroundPauseState pause;
  int resumeCalls = 0;
  int refreshCalls = 0;
  Completer<BackgroundResumeResult>? gate;
  BackgroundResumeResult Function()? answer;

  void setPause(BackgroundPauseState value) {
    pause = value;
    notifyListeners();
  }

  @override
  BackgroundPauseState get backgroundPause => pause;

  @override
  Future<BackgroundPauseState> refreshBackgroundPause() async {
    refreshCalls++;
    return pause;
  }

  @override
  Future<BackgroundResumeResult> resumeBackground() async {
    resumeCalls++;
    final result = await (gate?.future ?? Future.value(answer!()));
    setPause(result.state);
    return result;
  }

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) =>
      throw UnimplementedError();

  @override
  bool get shareSupported => false;

  @override
  Future<CrashReportResult> previewCrashReport() => throw UnimplementedError();

  @override
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview) =>
      throw UnimplementedError();
}

BackgroundPauseState pausedFor(
  BackgroundPauseReason reason, {
  DateTime? at,
  bool canResume = true,
}) => BackgroundPauseState(
  supported: true,
  paused: true,
  reason: reason,
  at: (at ?? DateTime.now()).toUtc(),
  canResume: canResume,
);

const running = BackgroundPauseState(supported: true, active: true);

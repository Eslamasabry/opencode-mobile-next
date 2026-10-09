import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../background/live_background.dart';
import '../builtin/app_exit_recovery.dart';
import '../builtin/agent_helper_diagnostics.dart';
import '../builtin/builtin_linux.dart';
import '../domain/app_diagnostics_gateway.dart';
import '../platform/app_exit.dart';
import '../state/connection.dart';
import 'crash_diagnostics.dart';
import 'crash_report.dart';

/// Uses the app's existing lifecycle and background owners. Changing selected
/// servers does not create a second service controller or diagnostics store.
final deviceDiagnosticsGatewayProvider = Provider<AppDiagnosticsGateway>((ref) {
  final gateway = DeviceDiagnosticsGateway(
    lifecycle: ref.watch(appLifecycleBridgeProvider),
    background: ref.watch(connProvider).backgroundLive,
    reports: CrashReportBuilder(
      loadController: () => CrashDiagnosticsStartup.ready,
    ),
  );
  ref.onDispose(gateway.dispose);
  return gateway;
});

class DeviceDiagnosticsGateway extends ChangeNotifier
    implements AppDiagnosticsGateway {
  DeviceDiagnosticsGateway({
    required AppLifecycleBridge lifecycle,
    required BackgroundLiveController background,
    required CrashReportBuilder reports,
    Future<BuiltinPerformance> Function()? readPerformance,
  }) : _lifecycle = lifecycle,
       _background = background,
       _reports = reports,
       _readPerformance = readPerformance ?? BuiltinLinux().performance {
    _background.addListener(_backgroundChanged);
  }

  final AppLifecycleBridge _lifecycle;
  final BackgroundLiveController _background;
  final CrashReportBuilder _reports;
  final Future<BuiltinPerformance> Function() _readPerformance;

  @override
  Future<AppExitHistory> exitHistory({int limit = 10}) async {
    final history = await _lifecycle.exitHistory(limit: limit);
    try {
      return withAgentHelperExits(
        history,
        await _readPerformance(),
        limit: limit,
      );
    } catch (_) {
      // A missing/older runtime must not hide Android's app exit history.
      return history;
    }
  }

  @override
  BackgroundPauseState get backgroundPause => _background.backgroundPause;

  @override
  Future<BackgroundPauseState> refreshBackgroundPause() =>
      _background.refreshBackgroundPause();

  @override
  Future<BackgroundResumeResult> resumeBackground() =>
      _background.resumeBackground();

  @override
  bool get shareSupported => _reports.shareSupported;

  @override
  Future<CrashReportResult> previewCrashReport() => _reports.preview();

  @override
  Future<CrashShareResult> shareCrashReport(CrashReportPreview preview) =>
      _reports.share(preview);

  void _backgroundChanged() => notifyListeners();

  @override
  void dispose() {
    _background.removeListener(_backgroundChanged);
    super.dispose();
  }
}

import 'app_update_service.dart';

/// Stands in for shorebird_updater_real.dart in the F-Droid build
/// (`--dart-define=OC_FDROID=true`): F-Droid ships updates itself and
/// forbids apps from updating their own code, so nothing here can reach a
/// code-push server. It never imports package:shorebird_code_push.
class ShorebirdAppUpdateService implements AppUpdateService {
  /// The real service takes a `ShorebirdUpdater`; nothing here accepts one.
  ShorebirdAppUpdateService();

  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

import 'package:shorebird_code_push/shorebird_code_push.dart';

import 'app_update_service.dart';

/// The only file in lib/ that imports package:shorebird_code_push. The
/// F-Droid build swaps it for shorebird_updater_stub.dart through a
/// conditional export (shorebird_update_notice.dart), so the package is not
/// compiled into that build at all.
class ShorebirdAppUpdateService implements AppUpdateService {
  ShorebirdAppUpdateService({ShorebirdUpdater? updater})
    : _updater = updater ?? ShorebirdUpdater();

  final ShorebirdUpdater _updater;

  @override
  bool get isAvailable => _updater.isAvailable;

  @override
  Future<AppUpdateState> checkForUpdate() async {
    return switch (await _updater.checkForUpdate()) {
      UpdateStatus.upToDate => AppUpdateState.current,
      UpdateStatus.outdated => AppUpdateState.available,
      UpdateStatus.restartRequired => AppUpdateState.restartRequired,
      UpdateStatus.unavailable => AppUpdateState.unavailable,
    };
  }

  @override
  Future<void> downloadUpdate() => _updater.update();
}

// The update-service seam. Free of any code-push package, so every build
// (including the F-Droid one, docs/release/fdroid-2026-10-31.md) can use it.

enum AppUpdateState { current, available, restartRequired, unavailable }

abstract interface class AppUpdateService {
  bool get isAvailable;

  Future<AppUpdateState> checkForUpdate();

  Future<void> downloadUpdate();
}

/// The update service for a build Shorebird does not patch.
///
/// Desktop bundles are released as GitHub artifacts, not Shorebird patches,
/// so constructing a `ShorebirdUpdater` there only produces an updater that
/// reports itself unavailable after doing FFI work. This says so up front,
/// and keeps the notice from ever entering its check loop.
class UnavailableAppUpdateService implements AppUpdateService {
  const UnavailableAppUpdateService();

  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

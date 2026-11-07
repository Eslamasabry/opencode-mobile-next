import '../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../domain/shared_storage_path.dart';
import '../platform/storage_access.dart';
import 'profiles.dart';

/// What stops a project in shared storage from showing its files.
enum SharedStorageBlock {
  /// Nothing: the folder is not in shared storage, the server is not on this
  /// phone, or access is already on.
  none,

  /// OpenCode inside this app reads the folder as this app, and Android's
  /// "All files access" is off for it.
  appAccess,
}

/// Decides which host's storage access a folder in shared storage needs.
/// Asked when a project is opened and when its Files listing shows only
/// hidden entries. Unknown never blocks: a failed check says [none].
class SharedStorageGate {
  SharedStorageGate._();

  static Future<SharedStorageBlock> blockFor(
    ServerProfile? profile,
    String? directory,
  ) async {
    if (profile == null || !isSharedStoragePath(directory)) {
      return SharedStorageBlock.none;
    }
    if (looksLikeInAppServer(profile)) {
      return await StorageAccessBridge.status() == StorageAccess.notGranted
          ? SharedStorageBlock.appAccess
          : SharedStorageBlock.none;
    }
    return SharedStorageBlock.none;
  }
}

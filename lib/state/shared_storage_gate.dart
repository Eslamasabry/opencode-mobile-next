import 'package:flutter/foundation.dart';

import '../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../domain/shared_storage_path.dart';
import '../platform/storage_access.dart';
import '../termux/bridge.dart';
import 'profiles.dart';

/// What stops a project in shared storage from showing its files.
enum SharedStorageBlock {
  /// Nothing: the folder is not in shared storage, the server is not on this
  /// phone, or access is already on.
  none,

  /// OpenCode inside this app reads the folder as this app, and Android's
  /// "All files access" is off for it.
  appAccess,

  /// The server runs in Termux, which has no storage access of its own yet
  /// (`termux-setup-storage`). This app's own access does not help there.
  termuxAccess,
}

/// Decides which host's storage access a folder in shared storage needs.
/// Asked when a project is opened and when its Files listing shows only
/// hidden entries. Unknown never blocks: a failed check says [none].
class SharedStorageGate {
  SharedStorageGate._();

  /// Widget tests answer for Termux without its bridge.
  @visibleForTesting
  static Future<bool> Function()? termuxStorageOverride;

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
    if (profile.backend == ServerBackend.openCode &&
        TermuxBridge.managesServerUrl(profile.baseUrl) &&
        (termuxStorageOverride != null || TermuxBridge.supported)) {
      return await _termuxCanReadStorage()
          ? SharedStorageBlock.none
          : SharedStorageBlock.termuxAccess;
    }
    return SharedStorageBlock.none;
  }

  /// Termux without storage access cannot even list the top of the shared
  /// storage ("Permission denied"); with it the listing works.
  static Future<bool> _termuxCanReadStorage() async {
    final override = termuxStorageOverride;
    if (override != null) return override();
    try {
      final result = await TermuxBridge.run(
        'ls -A /storage/emulated/0 >/dev/null 2>&1 && echo oc-storage-ok '
        '|| echo oc-storage-denied',
        timeout: const Duration(seconds: 12),
      );
      return !result.stdout.contains('oc-storage-denied');
    } catch (_) {
      return true;
    }
  }
}

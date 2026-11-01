import 'dart:convert';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import '../builtin/migration/migration_archive_store.dart';
import '../builtin/migration/migration_profile.dart';
import '../builtin/migration/termux_migration_controller.dart';
import '../builtin/migration/termux_migration_transport.dart';
import '../state/connection.dart';
import '../state/profiles.dart';
import '../state/termux_migration_offer.dart';
import '../termux/bridge.dart';
import '../voice/device.dart';
import 'termux_migration.dart';

export '../builtin/migration/termux_migration_controller.dart'
    show TermuxMigrationController;
export 'termux_migration.dart';
export '../builtin/migration/migration_space.dart' show TermuxMigrationSpace;

/// UI composition entry point; no screen imports a bridge or filesystem API.
/// Create once above routes, explicitly check/start, cancel on foreground loss,
/// and dispose only after the pending operation has settled.
abstract final class TermuxMigrationService {
  static bool eligible(ServerProfile profile) =>
      profile.backend == ServerBackend.openCode &&
      profile.baseUrl == TermuxBridge.managedServerUrl;

  /// A one-time offer per source profile. Dismissal is part of the standard
  /// profile-scoped deletion sweep; never persist an unscoped shared flag.
  static bool shouldOffer(ProfileStore store, String profileId) =>
      store.profiles.any((p) => p.id == profileId && eligible(p)) &&
      store.prefs.getBool('oc.termuxMigrationOffer.$profileId') != true &&
      !store.prefs.containsKey(
        PreferencesTermuxMigrationJournal.key(profileId),
      );

  static Future<void> dismissOffer(ProfileStore store, String profileId) =>
      TermuxMigrationOfferStore.dismiss(
        store.prefs,
        profileId,
        mayWrite: () => store.profiles.any(
          (profile) => profile.id == profileId && eligible(profile),
        ),
      );

  static Future<TermuxMigrationController> create({
    required ConnectionController connection,
    required String builtinProfileName,
  }) async {
    try {
      final support = await getApplicationSupportDirectory();
      final archiveStore = TermuxMigrationArchiveStore(support: support);
      await _cleanOrphanedCache(connection.store, archiveStore, support);
      bool available(String id) =>
          connection.isProfileReadable(id) &&
          connection.store.profiles.any((p) => p.id == id && eligible(p));
      final profileSwitcher = TermuxMigrationProfileSwitcher(
        connection: connection,
        builtinProfileName: builtinProfileName,
      );
      return TermuxMigrationController(
        transport: BridgeTermuxMigrationTransport(),
        archives: archiveStore,
        journal: PreferencesTermuxMigrationJournal(
          connection.store.prefs,
          mayWrite: available,
        ),
        availableBytes: () async =>
            (await voiceDevicePlatform.getDeviceInfo()).availableStorageBytes,
        switchProfile: profileSwitcher.switchProfile,
        sourceProfileExists: available,
      );
    } catch (_) {
      throw const TermuxMigrationException(TermuxMigrationFailure.unavailable);
    }
  }

  // Profile deletion removes its scoped journal immediately. On the next
  // composition, remove only orphaned transfer caches, never committed files.
  static Future<void> _cleanOrphanedCache(
    ProfileStore store,
    TermuxMigrationArchiveStore archives,
    Directory support,
  ) async {
    final jobs = <String>{};
    for (final key in store.prefs.getKeys().where(
      (k) => k.startsWith('oc.termuxMigration.'),
    )) {
      try {
        final value = jsonDecode(store.prefs.getString(key)!) as Map;
        if (value['job'] is String) jobs.add(value['job'] as String);
      } catch (_) {
        return;
      } // Preserve caches when ownership is uncertain.
    }
    final root = Directory('${support.path}/migrations');
    if (await FileSystemEntity.type(root.path, followLinks: false) !=
        FileSystemEntityType.directory) {
      return;
    }
    await for (final entry in root.list(followLinks: false)) {
      final id = entry.path.split('/').last;
      if (RegExp(r'^[a-f0-9]{32}$').hasMatch(id) && !jobs.contains(id)) {
        await archives.cleanupPartial(id);
      }
    }
  }
}

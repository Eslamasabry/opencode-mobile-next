import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../l10n/app_localizations.dart';
import '../../builtin/builtin_linux.dart';
import '../../builtin/builtin_server.dart'
    show looksLikeInAppServer, startBuiltinServer;
import '../../domain/shared_storage_path.dart';
import '../../platform/storage_access.dart';
import '../../state/shared_project_roots.dart';
import '../../state/profiles.dart';
import '../../state/shared_storage_gate.dart';
import '../../termux/bridge.dart';
import '../app_iconography.dart';
import '../kit/kit.dart';

/// How the shared-storage question ended.
enum SharedStorageOutcome {
  /// Nothing was in the way, or access is on now: go on and open the folder.
  proceed,

  /// The person said not now, or came back without turning access on. The
  /// folder is not opened: its files could not be shown, and a listing with
  /// only hidden entries must never pass for an empty project.
  declined,

  /// The person chose the app's own project space instead.
  useAppSpace,
}

/// Asks, in plain words, before a project in the phone's shared storage is
/// opened without the access it needs. Never runs at start-up and never for
/// the app's own project space (those paths are not shared storage).
class SharedStorageAccessFlow {
  SharedStorageAccessFlow._();

  /// The question for the folder [path] on [profile]'s server.
  /// [offerAppSpace] adds "Use the app's project space" (a server this phone
  /// runs has one).
  static Future<SharedStorageOutcome> ensure(
    BuildContext context,
    ServerProfile? profile,
    String path, {
    bool offerAppSpace = false,
    bool workRunning = false,
  }) async {
    final block = await SharedStorageGate.blockFor(profile, path);
    var outcome = SharedStorageOutcome.proceed;
    if (block != SharedStorageBlock.none && context.mounted) {
      outcome = await resolve(context, block, offerAppSpace: offerAppSpace);
    }
    if (outcome != SharedStorageOutcome.proceed || !context.mounted) {
      return outcome;
    }
    if (profile != null && await _needsRestart(profile, path)) {
      if (!context.mounted) return SharedStorageOutcome.declined;
      return _restartThenOpen(context, profile, path, workRunning);
    }
    unawaited(_remember(profile, path));
    return SharedStorageOutcome.proceed;
  }

  /// Tests answer for the native side: is the in-app server running with AI
  /// Team's confinement on?
  @visibleForTesting
  static Future<bool> Function(ServerProfile profile)? confinedRunningOverride;

  /// Tests stand in for the real restart (true when the server came back).
  @visibleForTesting
  static Future<bool> Function(ServerProfile profile)? restartOverride;

  /// A folder not opened before, on the in-app server that is running with
  /// AI Team's confinement: its binds are fixed at start, so the server has
  /// to restart before the folder shows anything.
  static Future<bool> _needsRestart(ServerProfile profile, String path) async {
    if (!looksLikeInAppServer(profile) || !isSharedStoragePath(path)) {
      return false;
    }
    if (sharedProjectRoot(path) == null) return false;
    if (await SharedProjectRoots.isKnown(profile.id, path)) return false;
    final override = confinedRunningOverride;
    if (override != null) return override(profile);
    try {
      final linux = BuiltinLinux();
      final engine = await linux.phoneEngineStatus(profile.id);
      if (!engine.protectionRequired) return false;
      return (await linux.status()).serverRunning;
    } catch (_) {
      return false;
    }
  }

  static Future<SharedStorageOutcome> _restartThenOpen(
    BuildContext context,
    ServerProfile profile,
    String path,
    bool workRunning,
  ) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final restart = await showKitConfirm(
      context,
      title: l10n.storageRestartTitle,
      body: l10n.storageRestartBody,
      consequences: [
        workRunning ? l10n.storageRestartBusy : l10n.storageRestartPause,
      ],
      confirmLabel: l10n.storageRestartConfirm,
      cancelLabel: l10n.storageAccessNotNow,
      icon: AppIconography.folderOpen,
      confirmKey: const ValueKey('storage-restart-confirm'),
    );
    if (!restart || !context.mounted) return SharedStorageOutcome.declined;
    // Saved first: the restart rebuilds the binds from it.
    await SharedProjectRoots.remember(profile.id, path);
    final bool back;
    final override = restartOverride;
    if (override != null) {
      back = await override(profile);
    } else {
      try {
        back =
            await startBuiltinServer(linux: BuiltinLinux(), profile: profile) ==
            null;
      } catch (_) {
        return context.mounted
            ? _restartFailed(context, l10n)
            : SharedStorageOutcome.declined;
      }
    }
    if (back) return SharedStorageOutcome.proceed;
    return context.mounted
        ? _restartFailed(context, l10n)
        : SharedStorageOutcome.declined;
  }

  static Future<SharedStorageOutcome> _restartFailed(
    BuildContext context,
    AppLocalizations l10n,
  ) async {
    await showKitAlert(
      context,
      title: l10n.storageRestartFailedTitle,
      body: l10n.storageRestartFailedBody,
      icon: AppIconography.folderOpen,
      alertKey: const ValueKey('storage-restart-failed'),
    );
    return SharedStorageOutcome.declined;
  }

  /// Once AI Team is on, the in-app Linux sees shared storage only through the
  /// folders opened here: keep this one for the next start.
  static Future<void> _remember(ServerProfile? profile, String path) async {
    if (profile == null || !looksLikeInAppServer(profile)) return;
    if (!isSharedStoragePath(path)) return;
    await SharedProjectRoots.remember(profile.id, path);
  }

  /// Explains [block], takes the person to the right place, and checks again
  /// when they return.
  static Future<SharedStorageOutcome> resolve(
    BuildContext context,
    SharedStorageBlock block, {
    bool offerAppSpace = false,
  }) async {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    var appSpace = false;
    final KitAction? alternative = offerAppSpace
        ? KitAction(
            key: const ValueKey('storage-access-app-space'),
            label: l10n.storageAccessUseAppSpace,
            onPressed: () => appSpace = true,
          )
        : null;
    switch (block) {
      case SharedStorageBlock.none:
        return SharedStorageOutcome.proceed;
      case SharedStorageBlock.appAccess:
        final allow = await showKitConfirm(
          context,
          title: l10n.storageAccessTitle,
          body: l10n.storageAccessBody,
          consequences: [
            l10n.storageAccessWhyScope,
            l10n.storageAccessWhyAgent,
            l10n.storageAccessWhyOff,
          ],
          confirmLabel: l10n.storageAccessAllow,
          cancelLabel: l10n.storageAccessNotNow,
          icon: AppIconography.folderOpen,
          alternative: alternative,
          confirmKey: const ValueKey('storage-access-allow'),
        );
        if (appSpace) return SharedStorageOutcome.useAppSpace;
        if (!allow || !context.mounted) return SharedStorageOutcome.declined;
        await StorageAccessBridge.request();
        final status = await StorageAccessBridge.status();
        if (status != StorageAccess.notGranted) {
          return SharedStorageOutcome.proceed;
        }
        if (context.mounted) {
          await showKitAlert(
            context,
            title: l10n.storageAccessRefusedTitle,
            body: l10n.storageAccessRefusedBody,
            icon: AppIconography.folderOpen,
            alertKey: const ValueKey('storage-access-refused'),
          );
        }
        return SharedStorageOutcome.declined;
      case SharedStorageBlock.termuxAccess:
        final open = await showKitConfirm(
          context,
          title: l10n.storageTermuxTitle,
          body: l10n.storageTermuxBody,
          confirmLabel: l10n.storageTermuxAllow,
          cancelLabel: l10n.storageAccessNotNow,
          icon: AppIconography.folderOpen,
          alternative: alternative,
          confirmKey: const ValueKey('storage-termux-allow'),
        );
        if (appSpace) return SharedStorageOutcome.useAppSpace;
        if (!open || !context.mounted) return SharedStorageOutcome.declined;
        try {
          await TermuxBridge.openTerminalSession('termux-setup-storage');
        } on TermuxBridgeException {
          // The still-missing answer below says what to do by hand.
        }
        // The person answers Android's question inside Termux; the folder is
        // opened again from the Projects screen once they are back.
        if (context.mounted) {
          await showKitAlert(
            context,
            title: l10n.storageTermuxStillTitle,
            body: l10n.storageTermuxStillBody,
            icon: AppIconography.folderOpen,
            alertKey: const ValueKey('storage-termux-still'),
          );
        }
        return SharedStorageOutcome.declined;
    }
  }
}

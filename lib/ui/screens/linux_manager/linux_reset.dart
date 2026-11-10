/// Reset Linux: erase the app's Ubuntu and set it up again from scratch.
///
/// The question says what is lost and what stays, and stays disabled until
/// the word is typed. Projects live outside Ubuntu (`BuiltinLinux.remove`
/// keeps them), saved prompts are kept by the same cleanup the Remove flow
/// uses. The reset is one act ([LinuxResetFlow]): either Linux is erased and
/// phone setup starts at once, its progress shown, or the phone refused
/// before erasing anything and that is said. A reset the app could not
/// finish (killed part way) is finished on the next start.
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_server.dart'
    show builtinLinuxProvider, looksLikeInAppServer;
import '../../../builtin/linux_manager/linux_manager.dart';
import '../../../builtin/linux_manager/linux_reset_flow.dart';
import '../../../builtin/setup/phone_setup.dart';
import '../../../builtin/setup/setup_contract.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../../widgets/phone_server_card.dart'
    show PhoneServerCardRoutes, forgetInAppServerProfiles;
import '../phone_setup/phone_setup_selection.dart';
import 'linux_manager_common.dart';

/// What a fresh Linux gets: the same selection as the first setup.
Set<String> linuxResetSelection(SetupEngine engine) =>
    defaultSetupSelection(installableComponents(engine.registry));

/// Asks, resets, and shows setup running. Returns true when Linux was
/// erased (setup is then running or finished).
Future<bool> resetLinux(BuildContext context, WidgetRef ref) async {
  final l10n = AppLocalizations.of(context);
  final manager = ref.read(linuxManagerProvider);
  try {
    await manager.requireQuiet();
  } on LinuxManagerException catch (error) {
    if (!context.mounted) return false;
    await showKitAlert(
      context,
      title: l10n.linuxMgrResetTitle,
      body: linuxProblemText(l10n, error),
      icon: AppIconography.warning,
      alertKey: const ValueKey('linux-reset-blocked'),
    );
    return false;
  }
  if (!context.mounted) return false;
  final connection = ref.read(connProvider);
  final linux = ref.read(builtinLinuxProvider);
  final engine = PhoneSetup.engine;
  LinuxResetOutcome? outcome;
  final yes = await showKitConfirm(
    context,
    title: l10n.linuxMgrResetTitle,
    body: l10n.linuxMgrResetBody,
    confirmLabel: l10n.linuxMgrReset,
    kind: KitConfirmKind.destructive,
    icon: AppIconography.restart,
    typedName: l10n.linuxMgrResetWord,
    consequenceItems: [
      KitConsequence(
        l10n.linuxMgrResetLostTools,
        mark: KitConsequenceMark.lost,
      ),
      KitConsequence(
        l10n.linuxMgrResetLostSignIns,
        mark: KitConsequenceMark.lost,
      ),
      KitConsequence(
        l10n.linuxMgrResetLostChats,
        mark: KitConsequenceMark.lost,
      ),
      KitConsequence(
        l10n.linuxMgrResetKept,
        mark: KitConsequenceMark.kept,
        key: const ValueKey('linux-reset-kept'),
      ),
    ],
    action: () async {
      outcome = await LinuxResetFlow(
        linux: linux,
        engine: engine,
        prefs: connection.store.prefs,
        selection: linuxResetSelection(engine),
        // Leave the server before it disappears, as Remove does.
        beforeErase: () async {
          if (connection.api != null &&
              looksLikeInAppServer(connection.profile)) {
            await connection.disconnect(keepActive: true);
          }
        },
        afterErase: () => forgetInAppServerProfiles(connection, l10n),
      ).run();
    },
    sheetKey: const ValueKey('linux-reset-sheet'),
    confirmKey: const ValueKey('linux-reset-confirm'),
  );
  final done = outcome;
  if (!yes || done == null || !context.mounted) return false;
  if (done.result == LinuxResetResult.notErased) {
    await showKitAlert(
      context,
      title: l10n.linuxMgrResetNotErasedTitle,
      body: l10n.linuxMgrResetNotErasedBody,
      details: [
        if (done.details case final details?)
          KitTechnicalValue(l10n.linuxMgrResetDetailsLabel, details),
      ],
      icon: AppIconography.warning,
      alertKey: const ValueKey('linux-reset-not-erased'),
    );
    return false;
  }
  if (done.problems.isNotEmpty) {
    await showKitAlert(
      context,
      title: l10n.linuxMgrResetCleanupTitle,
      body: done.problems.join('\n'),
      icon: AppIconography.info,
      alertKey: const ValueKey('linux-reset-partial'),
    );
    if (!context.mounted) return true;
  }
  await PhoneServerCardRoutes.openProgress(context);
  return true;
}

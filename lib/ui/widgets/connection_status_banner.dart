import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/connection_status.dart';
import '../../domain/server_probe.dart' show openCode2UpgradeCommand;
import '../../state/connection.dart';
import '../../state/profiles.dart' show ServerProfile;
import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import '../kit/kit.dart';
import 'connection_failure.dart';
import 'phone_server_card.dart' show serverDisplayName;
import 'runtime_switch_status.dart';
import 'work_status_line.dart' show confirmPhoneServerRestart;

/// The single presentation of the controller's connection snapshot. The
/// controller owns escalation; this adapter never starts a clock.
KitStatus? connectionKitStatus(
  BuildContext context,
  ConnectionController controller, {
  bool showChangeServer = true,
  String? note,
  bool serverOnThisPhone = false,
  bool phoneSetupRunning = false,
  Future<void> Function()? onRestartServer,
  VoidCallback? onRecoverOwnership,
  BuildContext? Function()? actionContext,
  ServerProfile? switchingTo,
}) {
  final snapshot = controller.connectionStatus;
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  // A deliberate switch between this phone's OpenCode versions
  // ([phoneRuntimeSwitchTarget]): the server being left stops, refuses its
  // old password and stops answering on purpose. One calm progress line
  // names where the switch goes, whatever phase the old connection is in.
  if (switchingTo != null) {
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: AppIconography.sync,
      tone: AppStatusTone.progress,
      message: l10n.connectionSwitchingTo(phoneRuntimeName(l10n, switchingTo)),
      supporting: note,
    );
  }
  if (!snapshot.visible) return null;
  // A changed certificate is its own state: the connection screen and the
  // server editor say so and offer Re-check and trust. "Isn't answering"
  // on top of that would contradict them, so no connection line is shown.
  if ([
    controller.connectionError,
    controller.lastError,
    controller.lastFailure?.toString(),
  ].any(
    (text) => text?.toLowerCase().contains('certificate changed') == true,
  )) {
    return null;
  }
  // Setting this phone's Linux up again (a reset): the server is not meant
  // to answer yet, so "isn't answering" and Restart would be untrue.
  if (serverOnThisPhone && phoneSetupRunning) {
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: AppIconography.sync,
      tone: AppStatusTone.progress,
      message: l10n.phoneSetupProgressTitle,
      supporting: note,
    );
  }
  if (onRecoverOwnership != null) {
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: AppIconography.warning,
      tone: AppStatusTone.failure,
      message: l10n.inAppServerOwnershipRecoveryBody,
      action: KitAction(
        key: const ValueKey('connection-banner-recover-ownership'),
        label: l10n.e7SetupOpenAppSettings,
        onPressed: onRecoverOwnership,
      ),
    );
  }
  BuildContext? target() => actionContext == null ? context : actionContext();
  void editServer() {
    final current = target();
    if (current != null && current.mounted) {
      unawaited(
        Navigator.of(current).pushNamed('/servers', arguments: 'edit-active'),
      );
    }
  }

  // Named as the server switcher names it ("In-app Ubuntu", "Termux").
  ServerProfile? owner;
  for (final profile in controller.store.profiles) {
    if (profile.id == snapshot.profileId) owner = profile;
  }
  final server = owner != null
      ? serverDisplayName(owner, l10n, among: controller.store.profiles)
      : snapshot.serverName.isEmpty
      ? 'OpenCode'
      : snapshot.serverName;
  if (snapshot.phase == ConnectionStatusPhase.credentialsUnreadable) {
    // The saved secret could not be read back on this phone: nothing was
    // tried and Reconnect cannot help. The way forward names its target;
    // Details says why in plain words (never the platform's exception).
    final token = snapshot.usesToken;
    final message = token
        ? l10n.connectionTokenUnreadable(server)
        : l10n.connectionPasswordUnreadable(server);
    final enter = KitAction(
      key: const ValueKey('banner-enter-saved-secret'),
      label: token ? l10n.connectionEnterToken : l10n.connectionEnterPassword,
      onPressed: editServer,
    );
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: AppIconography.locked,
      tone: AppStatusTone.failure,
      message: message,
      supporting: note,
      action: enter,
      more: [
        KitAction(
          key: const ValueKey('connection-banner-details'),
          label: l10n.e7BannerDetails,
          onPressed: () {
            final current = target();
            if (current == null || !current.mounted) return;
            final navigator = Navigator.of(current);
            unawaited(
              showKitSheet<void>(
                current,
                title: message,
                icon: AppIconography.locked,
                primary: KitAction(
                  label: enter.label,
                  onPressed: () {
                    navigator.pop();
                    editServer();
                  },
                ),
                body: (_) => KitText(
                  token
                      ? l10n.connectionTokenUnreadableDetails
                      : l10n.connectionPasswordUnreadableDetails,
                  key: const ValueKey('connection-details-explanation'),
                  tone: KitTextTone.secondary,
                ),
              ),
            );
          },
        ),
      ],
    );
  }
  if (snapshot.phase == ConnectionStatusPhase.credentialsRequired) {
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: AppIconography.locked,
      tone: AppStatusTone.failure,
      // Names the server, as every other line here does: with several
      // saved, "the password changed" alone leaves the person guessing.
      message: snapshot.usesToken
          ? l10n.connectionTokenRejectedFor(server)
          : l10n.connectionPasswordRejectedFor(server),
      supporting: note,
      action: KitAction(
        key: ValueKey(
          snapshot.usesToken ? 'banner-update-token' : 'banner-update-password',
        ),
        label: snapshot.usesToken
            ? l10n.updateConnectionToken
            : l10n.e7BannerUpdatePassword,
        onPressed: editServer,
      ),
    );
  }
  if (controller.connectionError?.contains('This server runs OpenCode 1') ==
      true) {
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      tone: AppStatusTone.failure,
      icon: AppIconography.warning,
      message: l10n.openCode1Unsupported,
      action: KitAction(
        label: l10n.handoffCopyCommand,
        onPressed: () =>
            unawaited(KitCopy.copy(context, openCode2UpgradeCommand)),
      ),
    );
  }
  // A conversation with an agent on this phone (Claude Code) runs on that
  // agent's own connection, not OpenCode's: name it.
  final agentName = controller.isAgentBackend ? controller.profile?.name : null;
  // Already connecting: say it once. The server is named only when more than
  // one is saved, so the person can tell which one this is.
  final several = controller.store.profiles.length > 1;
  final message = switch (snapshot.phase) {
    ConnectionStatusPhase.serverBusy => l10n.workServerBusyPhone,
    ConnectionStatusPhase.connecting || ConnectionStatusPhase.reconnecting =>
      several
          ? l10n.e7BannerReconnectingServer(server)
          : l10n.connectionReconnectingQuiet,
    _ when agentName != null => l10n.agentNotAnsweringPhone(agentName),
    _ =>
      serverOnThisPhone
          ? l10n.workServerNotAnsweringPhone
          : l10n.workServerNotAnswering(server),
  };
  final busy = snapshot.phase == ConnectionStatusPhase.serverBusy;
  // While it is already reconnecting the way out is waiting; "Try now" sits
  // in the menu, and the action never repeats the title's words.
  final retry = snapshot.retrying || busy
      ? null
      : KitAction(
          key: const ValueKey('connection-banner-retry'),
          label: snapshot.waiting
              ? l10n.connectionTryNow
              : l10n.connectionReconnectTo(
                  serverDisplayName(
                    controller.profile,
                    l10n,
                    among: controller.store.profiles,
                  ),
                ),
          onPressed: () => unawaited(controller.retryConnection()),
        );
  final agentRecover = controller.recoverAgentBackend;
  final agentRoute = agentRecover != null;
  final restart = agentRoute
      // Restarting OpenCode doesn't help a phone agent: start its helper.
      ? agentRecover
      : serverOnThisPhone
      ? onRestartServer
      : null;
  // Named for what it restarts: a bare "Restart" beside "Reconnect to …"
  // didn't say, and for an agent it starts the agent's helper.
  final restartLabel = agentRoute
      ? l10n.connectionRestartAgent
      : l10n.connectionRestartPhoneServer;
  final restartAction =
      restart == null || snapshot.retrying || snapshot.waiting || busy
      ? null
      : agentRoute
      ? KitAction(
          key: const ValueKey('connection-banner-restart'),
          label: restartLabel,
          onPressed: () => unawaited(restart()),
        )
      : KitAction(
          key: const ValueKey('connection-banner-restart'),
          label: restartLabel,
          onPressed: () => unawaited(() async {
            final current = target();
            if (current == null ||
                !current.mounted ||
                !await confirmPhoneServerRestart(
                  current,
                  commandRunning: controller.hasRunningTool,
                )) {
              return;
            }
            await restart();
          }()),
        );
  // No standing promise about drafts here (owner, 2026-10-27): the line
  // is about the link. A draft stays in its composer, which says what
  // happens to it when Send is pressed while the link is down.
  final supporting = [if (note != null && note.isNotEmpty) note];
  // The link retries by itself on a real timer (vision 2026-10-20, "Both,
  // toned down"): say when, offer Try now, and stay muted, since nobody has
  // to act. Restart and the rest wait in More. Without a schedule (the
  // person turned automatic reconnects off, a first connection failed)
  // nothing promises a retry.
  final next = snapshot.nextAttemptAt;
  if (next != null && !snapshot.retrying && !busy) {
    Future<void> restartNow() async {
      if (restart == null) return;
      if (!agentRoute) {
        final current = target();
        if (current == null ||
            !current.mounted ||
            !await confirmPhoneServerRestart(
              current,
              commandRunning: controller.hasRunningTool,
            )) {
          return;
        }
      }
      await restart();
    }

    // One quiet caption line, like every other note (owner, 2026-10-20):
    // "Laptop isn't answering · trying again in 12 s · Try now". No More and
    // no divider: the words open Details (Try again, Switch server and, for
    // this phone's server or an agent's helper, Restart); the server sheet
    // keeps the rest.
    return KitStatus(
      kind: KitStatusKind.connection,
      id: 'connection:${snapshot.profileId}',
      key: const ValueKey('connection-status-banner'),
      icon: snapshot.waiting ? AppIconography.sync : AppIconography.cloudOff,
      message: message,
      quiet: true,
      semanticsLabel: message,
      countdownTo: next,
      countdownLine: (left) {
        final seconds = (left.inMilliseconds / 1000).ceil();
        return [
          seconds <= 0
              ? l10n.connectionTryingAgainNow
              : l10n.connectionTryingAgainIn(seconds),
          ...supporting,
        ].join(' ');
      },
      action: KitAction(
        key: const ValueKey('connection-banner-retry'),
        label: l10n.connectionTryNow,
        onPressed: () => unawaited(controller.retryConnection()),
      ),
      onOpen: () {
        final current = target();
        if (current != null && current.mounted) {
          unawaited(
            showConnectionDetailsSheet(
              current,
              controller,
              showChangeServer: showChangeServer,
              onRestart: restart == null ? null : restartNow,
              restartLabel: restartLabel,
            ),
          );
        }
      },
    );
  }
  return KitStatus(
    kind: KitStatusKind.connection,
    id: 'connection:${snapshot.profileId}',
    key: const ValueKey('connection-status-banner'),
    icon: snapshot.waiting || busy
        ? AppIconography.sync
        : AppIconography.cloudOff,
    tone: snapshot.waiting || busy
        ? AppStatusTone.progress
        : AppStatusTone.failure,
    message: message,
    supporting: supporting.isEmpty ? null : supporting.join(' '),
    action: snapshot.waiting ? null : restartAction ?? retry,
    more: [
      if (snapshot.waiting && retry != null) retry,
      if (!snapshot.waiting && restartAction != null && retry != null) retry,
      KitAction(
        key: const ValueKey('connection-banner-details'),
        label: l10n.e7BannerDetails,
        onPressed: () {
          final current = target();
          if (current != null && current.mounted) {
            unawaited(
              showConnectionDetailsSheet(
                current,
                controller,
                showChangeServer: showChangeServer,
              ),
            );
          }
        },
      ),
      if (showChangeServer)
        KitAction(
          key: const ValueKey('connection-banner-change-server'),
          label: l10n.productStatesSwitchServer,
          onPressed: () {
            final current = target();
            if (current != null && current.mounted) {
              unawaited(Navigator.of(current).pushNamed('/servers'));
            }
          },
        ),
    ],
  );
}

/// Compatibility host for embedded consumers. In a KitScreen it contributes
/// to the existing slot and never adds another row.
class ConnectionStatusBanner extends StatelessWidget {
  const ConnectionStatusBanner({
    super.key,
    required this.controller,
    this.showChangeServer = true,
    this.note,
    this.serverOnThisPhone = false,
    this.onRestartServer,
  });
  final ConnectionController controller;
  final bool showChangeServer;
  final String? note;
  final bool serverOnThisPhone;
  final Future<void> Function()? onRestartServer;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
    listenable: controller,
    builder: (context, _) {
      final status = connectionKitStatus(
        context,
        controller,
        showChangeServer: showChangeServer,
        note: note,
        serverOnThisPhone: serverOnThisPhone,
        onRestartServer: onRestartServer,
      );
      if (KitStatusLineSlot.existsAbove(context)) {
        return KitStatusContribution(
          status: status,
          child: const SizedBox.shrink(),
        );
      }
      return status == null
          ? const SizedBox.shrink()
          : KitStatusLine.of(status);
    },
  );
}

/// The connection's details: what is going on, what to check, the raw
/// error under Details, Try again and (optionally) Switch server. Shared by
/// every status line (the shell, a chat, the Work tab).
///
/// A failed connection is diagnosed the way root-connecting's card
/// diagnoses it ([ConnectionFailure.diagnose]): the same title, the same
/// explanation and the same list of what to check, so the line's Details
/// and the page the app opens on never tell two stories (P4.4). The phase
/// comes from the controller's one snapshot; the raw error stays in a
/// [KitDetailsFold] (KIT-33), folded and copyable here.
Future<void> showConnectionDetailsSheet(
  BuildContext context,
  ConnectionController controller, {
  bool showChangeServer = true,
  Future<void> Function()? onRestart,
  String? restartLabel,
}) async {
  final revision = controller.connectionRevision;
  final helper = await controller.readConnectionHelperExit();
  if (!context.mounted || revision != controller.connectionRevision) return;
  final snapshot = controller.connectionStatus;
  final manualRetry = snapshot.retrying;
  final reconnecting = snapshot.waiting;
  final error = controller.connectionError?.trim();
  // A conversation with an agent on this phone (Claude Code) runs on that
  // agent's own link: the words name it, and Details says whether its
  // helper answers and why the link last failed, not only OpenCode's error.
  final agent = controller.isAgentBackend ? controller.profile?.name : null;
  final helperAnswers = agent == null ? null : controller.agentDaemonAnswers;
  final linkFailure = agent == null ? null : controller.agentLinkFailureDetails;
  final details = [
    if (error != null && error.isNotEmpty) error,
    if (helperAnswers != null)
      'helper.health: ${helperAnswers ? "answers" : "not answering"}',
    if (linkFailure != null) 'link.lastFailure: $linkFailure',
    if (helper?.lastExitAt != null) ...[
      'helper.lastExitAt: ${helper!.lastExitAt!.toUtc().toIso8601String()}',
      'helper.lastExitCode: ${helper.lastExitCode ?? "unknown"}',
      'helper.possibleCause: ${helper.possibleResourceKill ? "memory_or_phantom_kill (possible)" : "unknown"}',
    ],
  ].join('\n');
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final navigator = Navigator.of(context);
  final profile = controller.profile;
  // The OpenCode server diagnosis (addresses, Termux, tunnels) is not an
  // agent link's: its reason stays in Details.
  final failure =
      reconnecting || error == null || error.isEmpty || agent != null
      ? null
      : ConnectionFailure.diagnose(
          l10n: l10n,
          error: error,
          baseUrl: profile?.baseUrl ?? '',
          supportsTermux: false,
          usesConnectionToken: snapshot.usesToken,
        );
  return showKitSheet<void>(
    context,
    title: reconnecting
        ? l10n.mcpReconnecting
        : failure?.title ?? l10n.e7BannerLost,
    icon: reconnecting ? AppIconography.sync : AppIconography.cloudOff,
    // While a Try again is in flight nothing here would help: the words
    // say it is reconnecting.
    primary: manualRetry
        ? null
        : KitAction(
            label: l10n.isolatedTaskRetryOpen,
            onPressed: () {
              navigator.pop();
              unawaited(controller.retryConnection());
            },
          ),
    tertiary: [
      if (onRestart != null && !manualRetry)
        KitAction(
          key: const ValueKey('connection-details-restart'),
          label: restartLabel ?? l10n.connectionRestartPhoneServer,
          onPressed: () {
            navigator.pop();
            unawaited(onRestart());
          },
        ),
      if (showChangeServer && !manualRetry)
        KitAction(
          label: l10n.productStatesSwitchServer,
          onPressed: () {
            navigator.pop();
            navigator.pushNamed('/servers');
          },
        ),
    ],
    body: (sheetContext) {
      final gap = SizedBox(height: KitTokens.of(sheetContext).space3);
      return Column(
        key: const ValueKey('connection-banner-details-sheet'),
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitText(
            reconnecting
                ? (agent != null
                      ? l10n.e7BannerCheckingExplanationAgent(agent)
                      : l10n.e7BannerCheckingExplanation)
                : failure?.explanation ??
                      (agent != null
                          ? l10n.e7BannerStaleExplanationAgent(agent)
                          : l10n.e7BannerStaleExplanation),
            key: const ValueKey('connection-details-explanation'),
            tone: KitTextTone.secondary,
          ),
          if (failure != null && failure.checks.isNotEmpty) ...[
            gap,
            KitText(l10n.e7SetupWhatToCheck),
            for (final check in failure.checks)
              KitText('\u2022 $check', tone: KitTextTone.secondary),
          ],
          if (details.isNotEmpty) ...[
            gap,
            // The plain diagnosis above is read first; the raw error waits
            // folded, one tap away, with its copy action (tinkerer).
            KitDetailsFold(text: details),
          ],
        ],
      );
    },
  );
}

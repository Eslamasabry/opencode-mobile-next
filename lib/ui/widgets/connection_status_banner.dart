import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/connection_status.dart';
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
  Future<void> Function()? onRestartServer,
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
      message: snapshot.usesToken
          ? l10n.connectionTokenRejected
          : l10n.e7BannerReconnectPassword,
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
  // A conversation with an agent on this phone (Claude Code) runs on that
  // agent's own connection, not OpenCode's: name it.
  final agentName = controller.isAgentBackend ? controller.profile?.name : null;
  final message = switch (snapshot.phase) {
    ConnectionStatusPhase.connecting => l10n.e7SetupConnectingProfile(server),
    ConnectionStatusPhase.reconnecting => l10n.e7BannerReconnectingServer(
      server,
    ),
    _ when agentName != null => l10n.agentNotAnsweringPhone(agentName),
    _ =>
      serverOnThisPhone
          ? l10n.workServerNotAnsweringPhone
          : l10n.workServerNotAnswering(server),
  };
  final retry = snapshot.retrying
      ? null
      : KitAction(
          key: const ValueKey('connection-banner-retry'),
          label: l10n.connectionReconnectTo(
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
  final restartAction = restart == null || snapshot.retrying || snapshot.waiting
      ? null
      : agentRoute
      ? KitAction(
          key: const ValueKey('connection-banner-restart'),
          label: l10n.workServerRestart,
          onPressed: () => unawaited(restart()),
        )
      : KitAction(
          key: const ValueKey('connection-banner-restart'),
          label: l10n.workServerRestart,
          onPressed: () => unawaited(() async {
            final current = target();
            if (current == null ||
                !current.mounted ||
                !await confirmPhoneServerRestart(current)) {
              return;
            }
            await restart();
          }()),
        );
  final supporting = [
    if (snapshot.usesToken) l10n.codexDraftReconnectNotice,
    if (note != null && note.isNotEmpty) note,
  ];
  return KitStatus(
    kind: KitStatusKind.connection,
    id: 'connection:${snapshot.profileId}',
    key: const ValueKey('connection-status-banner'),
    icon: snapshot.waiting ? AppIconography.sync : AppIconography.cloudOff,
    tone: snapshot.waiting ? AppStatusTone.progress : AppStatusTone.failure,
    message: message,
    supporting: supporting.isEmpty ? null : supporting.join(' '),
    action: restartAction ?? retry,
    more: [
      if (restartAction != null && retry != null) retry,
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
}) async {
  final revision = controller.connectionRevision;
  final helper = await controller.readConnectionHelperExit();
  if (!context.mounted || revision != controller.connectionRevision) return;
  final snapshot = controller.connectionStatus;
  final manualRetry = snapshot.retrying;
  final reconnecting = snapshot.waiting;
  final error = controller.connectionError?.trim();
  final details = [
    if (error != null && error.isNotEmpty) error,
    if (helper?.lastExitAt != null) ...[
      'helper.lastExitAt: ${helper!.lastExitAt!.toUtc().toIso8601String()}',
      'helper.lastExitCode: ${helper.lastExitCode ?? "unknown"}',
      'helper.possibleCause: ${helper.possibleResourceKill ? "memory_or_phantom_kill (possible)" : "unknown"}',
    ],
  ].join('\n');
  final l10n = lookupAppLocalizations(Localizations.localeOf(context));
  final navigator = Navigator.of(context);
  final profile = controller.profile;
  final failure = reconnecting || error == null || error.isEmpty
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
                ? l10n.e7BannerCheckingExplanation
                : failure?.explanation ?? l10n.e7BannerStaleExplanation,
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

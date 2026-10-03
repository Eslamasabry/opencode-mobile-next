import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agent_sheet.dart';
import 'agents_text.dart';

/// The quiet lines on Conversations about the agents on this phone: a plan
/// limit, a signed-out agent, a stopped host, and the one restart offer.
/// Each carries its one action, named for its target. Nothing is drawn when
/// there is nothing to say, or when the connection has no phone agents.
List<Widget> agentNoticeWidgets(BuildContext context, ChatsHost host) {
  final agents = host.agents;
  if (agents == null || !agents.phoneAgentsAvailable) return const [];
  final l10n = AppLocalizations.of(context);
  final tokens = KitTokens.of(context);
  Widget line(Key key, String message, {List<KitAction> actions = const []}) =>
      Padding(
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space1,
          tokens.gutter,
          tokens.space1,
        ),
        child: KitNotice(key: key, message: message, actions: actions),
      );
  return [
    if (agents.phoneAgentsNeedRestart)
      line(
        const ValueKey('agents-restart'),
        l10n.agentsRestartLine,
        actions: [
          KitAction(
            key: const ValueKey('agents-restart-action'),
            label: l10n.agentsRestartAction,
            onPressed: host.closeApp,
          ),
        ],
      ),
    for (final status in agents.agentStatusLines)
      line(
        ValueKey('agents-status-${status.agentId}-${status.kind.name}'),
        agentStatusLineText(context, l10n, status),
        actions: switch (status.kind) {
          PhoneAgentStatusLineKind.limitReached => const [],
          PhoneAgentStatusLineKind.signedOut => [
            KitAction(
              key: ValueKey('agents-status-sign-in-${status.agentId}'),
              label: l10n.agentsSignInAction(KitBidi.auto(status.agentName)),
              onPressed: () => unawaited(
                showAgentSheet(
                  context,
                  agentId: status.agentId,
                  step: AgentSheetStep.signIn,
                ),
              ),
            ),
          ],
          PhoneAgentStatusLineKind.stopped => [
            KitAction(
              key: ValueKey('agents-status-resume-${status.agentId}'),
              label: l10n.agentsResumeAction(KitBidi.auto(status.agentName)),
              onPressed: () => unawaited(_resume(agents)),
            ),
          ],
        },
      ),
  ];
}

Future<void> _resume(PhoneAgentsSource agents) async {
  try {
    await agents.resumeAgentHost();
  } catch (_) {
    // The status line stays; the next refresh says what still holds it up.
  }
}

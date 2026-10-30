import 'dart:async';

import 'package:flutter/material.dart';

import '../../../domain/agent_tools/agent_certification.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agent_error_notice.dart';
import 'agent_sheet.dart';
import 'agents_text.dart';

/// The quiet lines on Conversations about the agents on this phone: a plan
/// limit, a signed-out agent, a stopped host, and the one restart offer.
/// Each carries its one action, named for its target. Nothing is drawn when
/// there is nothing to say, or when the connection has no phone agents.
class AgentStatusNotices extends StatefulWidget {
  const AgentStatusNotices({super.key, required this.host});

  final ChatsHost host;

  @override
  State<AgentStatusNotices> createState() => _AgentStatusNoticesState();
}

class _AgentStatusNoticesState extends State<AgentStatusNotices> {
  AgentFailure? _failure;

  Future<void> _resume(PhoneAgentsSource agents, String agentName) async {
    setState(() => _failure = null);
    try {
      await agents.resumeAgentHost();
    } catch (error) {
      if (!mounted) return;
      setState(
        () => _failure = agentResumeFailure(
          AppLocalizations.of(context),
          error,
          KitBidi.auto(agentName),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final lines = _lines(context, widget.host);
    if (lines.isEmpty && _failure == null) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    return Padding(
      // The list's first section label ("Today") sits a section's gap under
      // the notices, not closer to them than to its own rows (audit #14).
      padding: EdgeInsetsDirectional.only(
        bottom: tokens.sectionGap - tokens.space1,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ...lines,
          if (_failure != null)
            Padding(
              padding: EdgeInsetsDirectional.symmetric(
                horizontal: tokens.gutter,
                vertical: tokens.space1,
              ),
              child: AgentErrorNotice(failure: _failure!),
            ),
        ],
      ),
    );
  }

  /// A sign-in or plan line only for an agent New conversation offers:
  /// signing in to one it does not would lead nowhere.
  static bool _worthSaying(PhoneAgentStatusLine status) =>
      status.kind == PhoneAgentStatusLineKind.stopped ||
      AgentCertificationMatrix.bundled.certifiedForChat(status.agentId);

  List<Widget> _lines(BuildContext context, ChatsHost host) {
    final agents = host.agents;
    if (agents == null || !agents.phoneAgentsAvailable) return const [];
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    Widget line(
      Key key,
      String message, {
      List<KitAction> actions = const [],
    }) => Padding(
      padding: EdgeInsetsDirectional.fromSTEB(
        tokens.gutter,
        tokens.space1,
        tokens.gutter,
        tokens.space1,
      ),
      child: KitNotice(key: key, message: message, actions: actions),
    );
    // Nothing to do about it: one quiet caption line.
    Widget quietLine(Key key, String message) => KitStatusLine(
      key: key,
      icon: AppIconography.info,
      message: message,
      quiet: true,
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
      for (final status in agents.agentStatusLines.where(_worthSaying))
        if (status.kind == PhoneAgentStatusLineKind.limitReached)
          quietLine(
            ValueKey('agents-status-${status.agentId}-${status.kind.name}'),
            agentStatusLineText(context, l10n, status),
          )
        else
          line(
            ValueKey('agents-status-${status.agentId}-${status.kind.name}'),
            agentStatusLineText(context, l10n, status),
            actions: switch (status.kind) {
              PhoneAgentStatusLineKind.limitReached => const [],
              PhoneAgentStatusLineKind.signedOut => [
                KitAction(
                  key: ValueKey('agents-status-sign-in-${status.agentId}'),
                  label: l10n.agentsSignInAction(
                    KitBidi.auto(status.agentName),
                  ),
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
                  label: l10n.agentsResumeAction(
                    KitBidi.auto(status.agentName),
                  ),
                  onPressed: () => unawaited(_resume(agents, status.agentName)),
                ),
              ],
            },
          ),
    ];
  }
}

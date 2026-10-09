import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/agent_auth_probe.dart';
import '../../../domain/phone_agents.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../l10n/app_localizations.dart';
import '../../app_iconography.dart';
import '../../kit/kit.dart';
import '../chats/chats_host.dart';
import 'agent_error_notice.dart';
import 'agent_sheet.dart';
import 'agents_text.dart';
import 'phone_check_view.dart';

final _never = ValueNotifier<int>(0);

/// Settings › This phone › Agents: every agent this phone can run with where
/// it stands and the one act it needs (install, sign in, run the phone
/// check, resume), and "Check this phone". Draws nothing when the
/// connection has no phone agents.
class AgentsSection extends ConsumerStatefulWidget {
  const AgentsSection({super.key});

  @override
  ConsumerState<AgentsSection> createState() => _AgentsSectionState();
}

class _AgentsSectionState extends ConsumerState<AgentsSection> {
  bool _checking = false;
  String? _running;
  AgentFailure? _failure;

  /// Agents that were checked in this view, in order, with the name to show.
  final _checked = <String, String>{};

  @override
  void initState() {
    super.initState();
    // The inventory is empty until the first read: ask for it now.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        unawaited(ref.read(chatsHostProvider).agents?.refreshAgentRows());
      }
    });
  }

  bool _installed(AgentRow row) =>
      row.status != PhoneAgentStatus.needsInstall &&
      row.hiddenReason != PhoneAgentHiddenReason.catalogUnavailable &&
      row.hiddenReason != PhoneAgentHiddenReason.runtimeUnknown;

  Future<void> _checkAll(List<AgentRow> rows) async {
    final agents = ref.read(chatsHostProvider).agents;
    if (agents == null || _checking) return;
    setState(() {
      _checking = true;
      _checked.clear();
    });
    for (final row in rows.where(_installed)) {
      setState(() {
        _checked[row.id] = row.name;
        _running = row.id;
      });
      try {
        await agents.runAgentPhoneCheck(row.id);
      } catch (error) {
        // A step's failure is in the result; anything else is said here.
        if (mounted) {
          setState(
            () => _failure = agentFailure(AppLocalizations.of(context), error),
          );
        }
      }
      if (!mounted) return;
    }
    setState(() {
      _checking = false;
      _running = null;
    });
  }

  Future<void> _fix(AgentRow row) async {
    final action = row.fixAction;
    if (action == null) return;
    final host = ref.read(chatsHostProvider);
    if (action == PhoneAgentFixAction.resume) {
      try {
        await host.agents?.resumeAgentHost();
      } catch (error) {
        if (mounted) {
          setState(
            () => _failure = agentFailure(AppLocalizations.of(context), error),
          );
        }
      }
      return;
    }
    await showAgentSheet(
      context,
      agentId: row.id,
      step: switch (action) {
        PhoneAgentFixAction.install => AgentSheetStep.setup,
        PhoneAgentFixAction.signIn => AgentSheetStep.signIn,
        _ => AgentSheetStep.check,
      },
    );
  }

  /// A signed-in agent opens its sheet: who it is signed in as, Sign in
  /// again, Sign out and Remove. That includes one at its plan limit, and one
  /// that only offers Resume (stopped in the background): the chip resumes,
  /// the row opens the sheet.
  bool _opensSheet(AgentRow row) =>
      (row.fixAction == null &&
          (row.chatSelectable ||
              row.status == PhoneAgentStatus.limitReached)) ||
      row.fixAction == PhoneAgentFixAction.resume;

  Widget _agentRow(
    BuildContext context,
    AppLocalizations l10n,
    AgentRow row, {
    required AgentAuthProbeResult? account,
    required bool removing,
  }) {
    final opens = !removing && _opensSheet(row);
    final line = removing
        ? l10n.agentsRemoving(KitBidi.auto(row.name))
        : agentRowLine(l10n, row, account: account, context: context);
    return KitRow(
      key: ValueKey('agents-row-${row.id}'),
      title: KitBidi.auto(row.name),
      leading: KitRow.icon(context, agentIcon(row.iconKey)),
      supporting: TextSpan(text: line),
      supportingMaxLines: 3,
      // The title names the agent: the chip says only the act, and every
      // chip starts at one edge (owner, 2026-10-08).
      chip: removing
          ? null
          : agentFixChip(
              l10n,
              row,
              key: ValueKey('agents-fix-${row.id}'),
              onPressed: () => unawaited(_fix(row)),
            ),
      // A row with a chip has no trailing slot for a chevron.
      trailing: opens && row.fixAction == null ? const KitChevron() : null,
      onTap: opens
          ? () => unawaited(
              showAgentSheet(
                context,
                agentId: row.id,
                step: AgentSheetStep.signIn,
              ),
            )
          : null,
    );
  }

  @override
  Widget build(BuildContext context) {
    final host = ref.watch(chatsHostProvider);
    return ListenableBuilder(
      listenable: host.listenable ?? _never,
      builder: (context, _) {
        final agents = host.agents;
        if (agents == null || !agents.phoneAgentsAvailable) {
          return const SizedBox.shrink();
        }
        final l10n = AppLocalizations.of(context);
        final tokens = KitTokens.of(context);
        final rows = [
          for (final row in agents.agentRows)
            if (row.setupVisible) row,
        ];
        // Only a source with qualified status checks can say who is signed
        // in; any other keeps the plain wording.
        final accounts = agents is PhoneAgentAccountSource
            ? agents as PhoneAgentAccountSource
            : null;
        // The one agent being removed: its row says so and offers no act
        // until the host is done.
        final removing = agents is PhoneAgentRemovalSource
            ? (agents as PhoneAgentRemovalSource).removingAgentId
            : null;
        if (rows.isEmpty) {
          return Padding(
            padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
            child: KitText(
              l10n.agentsChecking,
              key: const ValueKey('agents-checking'),
              tone: KitTextTone.secondary,
            ),
          );
        }
        return Column(
          key: const ValueKey('agents-section'),
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitRowGroup(
              label: l10n.agentsSectionTitle,
              children: [
                for (final row in rows)
                  _agentRow(
                    context,
                    l10n,
                    row,
                    account: accounts?.agentAccount(row.id),
                    removing: removing == row.id,
                  ),
                if (rows.any(_installed))
                  KitRow(
                    key: const ValueKey('agents-check-phone'),
                    title: l10n.agentsCheckTitle,
                    leading: KitRow.icon(context, AppIconography.phone),
                    trailing: _checking ? null : const KitChevron(),
                    enabled: !_checking,
                    disabledReason: l10n.agentsCheckTitle,
                    onTap: () => unawaited(_checkAll(rows)),
                  ),
              ],
            ),
            if (_failure != null)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.gutter,
                  top: tokens.space3,
                  end: tokens.gutter,
                ),
                child: AgentErrorNotice(failure: _failure!),
              ),
            for (final entry in _checked.entries)
              Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.gutter,
                  top: tokens.space3,
                  end: tokens.gutter,
                ),
                child: AgentPhoneCheckView(
                  key: ValueKey('agents-check-result-${entry.key}'),
                  agent: entry.value,
                  result: entry.key == _running
                      ? null
                      : agents.agentPhoneCheck(entry.key),
                ),
              ),
          ],
        );
      },
    );
  }
}

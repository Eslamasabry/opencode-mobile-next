/// One agent as a list row, shared by the AI Team's agents list and a
/// task's Agents tab (02-ux §5.1), in the person's words
/// (docs/design/aiteam-redesign-2026-09-24.md):
///
/// ```
/// [glyph] Worker
///         Working · Sync engine · 1m ago
/// ```
///
/// The agent is named by its role ([teamAgentRole]); its Gas City name,
/// pool, provider, model and context use are on its own screen. The state
/// word leads the line and carries its glyph, so status is never
/// colour-only (§11).
library;

import 'package:flutter/material.dart';

import '../../domain/orchestration_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/team_conversation.dart' show teamSessionState;
import '../kit/kit.dart';
import 'relative_time.dart';
import 'team_vocabulary.dart';

AppLocalizations _copy(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// "ctx 63%" in the tone of [teamContextTone], for the agent's header:
/// text2 when fine, text1 once it runs high, tabular figures, never
/// colour-only (the number is the state).
class TeamContextNumber extends StatelessWidget {
  const TeamContextNumber({
    super.key,
    required this.percent,
    this.style,
    this.label,
    this.role,
  });

  final int percent;

  /// Retired by shared-team-1: pass [role]. A non-null style reads as the
  /// body role, the size its callers passed.
  final TextStyle? style;

  /// The type role; null is [KitTextRole.secondary] (or body with [style]).
  final KitTextRole? role;

  /// Longer wording ("Context use 63%") for the detail header; the short
  /// form otherwise.
  final String? label;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    final tone = teamContextTone(percent);
    return Semantics(
      label: l10n.teamUiAgentContextSemantics(percent),
      excludeSemantics: true,
      child: KitText(
        label ?? l10n.teamUiAgentContextShort(percent),
        role:
            role ?? (style == null ? KitTextRole.secondary : KitTextRole.body),
        tone: tone,
        tabular: true,
      ),
    );
  }
}

/// "Working · Sync engine · 1m ago": the state word, the step the agent
/// works on, and when it last did something.
String teamAgentLine(
  AppLocalizations l10n,
  OrchestrationAgent agent,
  WorkItem? work,
  DateTime now,
) {
  final activity = agent.lastActivity == null
      ? null
      : relativeTimeLabel(
          agent.lastActivity!.millisecondsSinceEpoch,
          now: now,
          l10n: l10n,
        );
  final unavailable = teamAgentUnavailableWords(l10n, agent);
  if (unavailable != null) {
    return [
      l10n.teamAgentUnavailableState,
      unavailable,
    ].join(teamUsageSeparator);
  }
  return [
    teamAgentStateWord(l10n, teamSessionState(agent)),
    ?work?.title,
    ?activity,
  ].join(teamUsageSeparator);
}

/// One agent of the team. [keyPrefix] names the row and its line
/// (`<prefix>-<id>`, `<prefix>-state-<id>`) so each list keeps its keys.
class TeamAgentRow extends StatelessWidget {
  const TeamAgentRow({
    super.key,
    required this.agent,
    required this.work,
    required this.now,
    required this.onTap,
    this.keyPrefix = 'team-agent',
  });

  final OrchestrationAgent agent;
  final WorkItem? work;
  final DateTime now;
  final VoidCallback? onTap;
  final String keyPrefix;

  @override
  Widget build(BuildContext context) {
    final l10n = _copy(context);
    return KitRow(
      key: ValueKey('$keyPrefix-${agent.id}'),
      leading: teamAgentMark(teamSessionState(agent)).leading(context),
      title: teamAgentRoleWord(l10n, teamAgentRole(agent)),
      supporting: TextSpan(text: teamAgentLine(l10n, agent, work, now)),
      supportingKey: ValueKey('$keyPrefix-state-${agent.id}'),
      // The step's title is the person's own words: two lines before it
      // ends, so the age is not cut off.
      supportingMaxLines: 2,
      onTap: onTap,
    );
  }
}

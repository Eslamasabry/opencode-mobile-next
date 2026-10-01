part of '../chat_screen.dart';

// A team agent's own conversation: finding it, opening it in watching mode,
// the watch banner, and its transcript inside the team conversation.

// ---------------------------------------------------------------------------
// A worker's own conversation (watching mode)
// ---------------------------------------------------------------------------

/// Why a worker's conversation could not be opened, for Live output's line.
enum TeamAgentConversationMiss {
  /// The connected server cannot list conversations across projects, or the
  /// agent names no folder.
  unreadable,

  /// No conversation of the agent's (since it started) on the connected
  /// server: it has not started yet, or the team runs on another machine.
  notFound,
}

/// Where "Open conversation" leads for an agent: its OpenCode session, or
/// why there is none ([miss]).
@immutable
class TeamAgentConversationLookup {
  const TeamAgentConversationLookup.found(String this.sessionId) : miss = null;
  const TeamAgentConversationLookup.missing(TeamAgentConversationMiss this.miss)
    : sessionId = null;

  final String? sessionId;
  final TeamAgentConversationMiss? miss;
}

/// Looks for [agent]'s own OpenCode session on the connected server
/// ([findTeamAgentSession]): in its work folder, newest since its session
/// began. Unreadable when there is no connected server that can list
/// conversations across projects (or none above [context] at all), or the
/// agent names no folder.
Future<TeamAgentConversationLookup> lookupTeamAgentConversation(
  BuildContext context,
  OrchestrationAgent agent,
) async {
  final ConnectionController conn;
  try {
    conn = ProviderScope.containerOf(context, listen: false).read(connProvider);
  } catch (_) {
    return const TeamAgentConversationLookup.missing(
      TeamAgentConversationMiss.unreadable,
    );
  }
  final repository = conn.repository;
  if (repository == null ||
      !conn.capabilities.globalSessionSearch ||
      (agent.workDir?.trim().isEmpty ?? true)) {
    return const TeamAgentConversationLookup.missing(
      TeamAgentConversationMiss.unreadable,
    );
  }
  final sessionId = await findTeamAgentSession(
    repository,
    workDir: agent.workDir,
    startedAt: agent.sessionStartedAt,
  );
  return sessionId == null
      ? const TeamAgentConversationLookup.missing(
          TeamAgentConversationMiss.notFound,
        )
      : TeamAgentConversationLookup.found(sessionId);
}

/// Live output's words for why the agent's conversation could not open.
String teamAgentConversationMissNote(
  BuildContext context,
  TeamAgentConversationMiss miss,
) => switch (miss) {
  TeamAgentConversationMiss.unreadable => _chatL10n(
    context,
  ).teamWatchFallbackUnreadable,
  TeamAgentConversationMiss.notFound => _chatL10n(
    context,
  ).teamWatchFallbackNotFound,
};

/// Opens [agent]'s conversation: its own OpenCode session on the chat page
/// in watching mode (the session in its work folder, newest since the
/// agent's session began, [findTeamAgentSession]). When there is none on
/// the connected server (a team on a computer, a worker still starting),
/// the same watching page opens drawn from the team's live output
/// ([TeamWatchLiveScreen]), with a line saying why.
///
/// [details]: the page offers the worker's own page (its state and
/// controls); false when it was opened from there, so Back returns to it.
Future<void> openTeamAgentConversation(
  BuildContext context,
  OrchestrationAgent agent, {
  OrchestrationController? team,
  TeamAgentConversationLookup? lookup,
  bool details = true,
}) async {
  final navigator = Navigator.of(context);
  final found = lookup ?? await lookupTeamAgentConversation(context, agent);
  final sessionId = found.sessionId;
  final miss = found.miss;
  if (!context.mounted) return;
  final controller = team ?? _teamOf(context);
  if (sessionId case final id?) {
    await navigator.push(
      KitPageRoute<void>(
        builder: (context) => ChatScreen(
          sessionID: id,
          watch: teamAgentWatch(
            context,
            agent,
            team: controller,
            details: details,
          ),
        ),
      ),
    );
    return;
  }
  if (controller == null) return;
  await navigator.push(
    KitPageRoute<void>(
      builder: (context) => TeamWatchLiveScreen(
        team: controller,
        agentId: agent.id,
        details: details,
        note: teamAgentConversationMissNote(
          context,
          miss ?? TeamAgentConversationMiss.notFound,
        ),
      ),
    ),
  );
}

/// Opens the conversation of the agent the team calls [agentId] (a gate's
/// logs, the cycle strip, the planner's card): as
/// [openTeamAgentConversation] while the team lists it, else straight to
/// its live output.
Future<void> openTeamAgentConversationById(
  BuildContext context,
  OrchestrationController team,
  String agentId,
) {
  if (_teamAgentById(team, agentId) case final agent?) {
    return openTeamAgentConversation(context, agent, team: team);
  }
  return Navigator.of(context).push(
    KitPageRoute<void>(
      builder: (_) => TeamWatchLiveScreen(team: team, agentId: agentId),
    ),
  );
}

/// The agent the team lists as [id], by its id or its session's.
OrchestrationAgent? _teamAgentById(OrchestrationController team, String id) {
  for (final agent in team.snapshot.agents) {
    if (agent.id == id || agent.sessionId == id) return agent;
  }
  return null;
}

/// The controller a team screen above [context] was built with, when it
/// shared one ([TeamControllerScope]).
OrchestrationController? _teamOf(BuildContext context) =>
    context.dependOnInheritedWidgetOfExactType<TeamControllerScope>()?.team;

/// Hands the team's controller to [openTeamAgentConversation] below it.
class TeamControllerScope extends InheritedWidget {
  const TeamControllerScope({
    super.key,
    required this.team,
    required super.child,
  });

  final OrchestrationController team;

  @override
  bool updateShouldNotify(TeamControllerScope oldWidget) =>
      !identical(team, oldWidget.team);
}

/// The watching-mode setup for [agent]: the status line from its session,
/// the composer addressed to it, whose words go through the team's message
/// control ([OrchestrationController.messageAgent]) with their receipt,
/// and the worker's own page as the top bar's action.
ChatWatch teamAgentWatch(
  BuildContext context,
  OrchestrationAgent agent, {
  OrchestrationController? team,
  bool details = true,
}) => _teamWatch(
  context,
  agentId: agent.id,
  agent: agent,
  team: team,
  details: details,
);

ChatWatch _teamWatch(
  BuildContext context, {
  required String agentId,
  OrchestrationAgent? agent,
  OrchestrationController? team,
  bool details = true,
}) {
  final l10n = _chatL10n(context);
  // The agent as the team lists it now: its session moves on.
  OrchestrationAgent? current() =>
      (team == null ? null : _teamAgentById(team, agentId)) ?? agent;
  final first = current();
  final role = first == null ? null : teamAgentRole(first);
  final canMessage = team != null && team.capabilities.controlMessage;
  // Receipts of what was sent from this page, not an older message's.
  final sentHere = <String>{};
  // Says a message was just sent, before the team's own next word.
  final sent = ValueNotifier<int>(0);
  // The roles load once per server; the page reads them again when they land.
  final rolesLoaded = ValueNotifier<int>(0);
  // The watched session's own title, for the agent's page.
  final sessionTitle = ValueNotifier<String?>(null);
  if (team != null && !_watchRoles.containsKey(team.profileId)) {
    unawaited(
      loadTeamRoles(team.profileId)
          .then((roles) {
            _watchRoles[team.profileId] = roles;
            rolesLoaded.value++;
          })
          .catchError((_) {
            // Without roles the worker is "Worker".
          }),
    );
  }
  // The work item the agent is on, as the team lists it now.
  WorkItem? task() {
    final now = current();
    if (team == null || now == null) return null;
    final work = team.snapshot.work;
    final id = now.currentWorkId;
    if (id != null) {
      for (final item in work) {
        if (item.id == id) return item;
      }
    }
    final session = now.sessionId;
    if (session != null && session.isNotEmpty) {
      for (final item in work) {
        if (item.sessionId == session) return item;
      }
    }
    // The reviewer (refinery) works on a task's merge request: the task of
    // its project that waits in review.
    if (teamAgentRole(now) == TeamAgentRole.reviewer) {
      final name = now.name;
      final rig = name.contains('/') ? name.split('/').first : null;
      WorkItem? inReview;
      for (final item in work) {
        if (item.state != WorkState.review) continue;
        if (rig != null && item.projectId != null && item.projectId != rig) {
          continue;
        }
        final at = item.updatedAt;
        final best = inReview?.updatedAt;
        if (inReview == null ||
            (at != null && (best == null || at.isAfter(best)))) {
          inReview = item;
        }
      }
      return inReview;
    }
    return null;
  }

  // The role the person gave the task ("Frontend"), else the agent's kind
  // ("Worker"): the generated name lives on the agent's own page.
  String who() {
    final now = current();
    final roles = team == null ? null : _watchRoles[team.profileId];
    final item = task();
    if (roles != null && item != null) {
      final id =
          roles.roleOfTask(item.id) ??
          (item.runId == null ? null : roles.roleOfTask(item.runId!));
      final known = id == null ? null : roles.byId(id);
      if (known != null) return teamRoleName(l10n, known);
    }
    return teamAgentRoleWord(
      l10n,
      now == null ? (role ?? TeamAgentRole.worker) : teamAgentRole(now),
    );
  }

  return ChatWatch(
    banner: () => _teamWatchBanner(l10n, current(), who()),
    hint: l10n.teamWatchComposerHint(who()),
    hintOf: () => l10n.teamWatchComposerHint(who()),
    // The task it is on; without one, who it is (never the session's own
    // "New conversation").
    title: () {
      final text = task()?.title.trim();
      return text == null || text.isEmpty ? who() : text;
    },
    sessionTitle: sessionTitle,
    empty: () {
      final now = current();
      final text = task()?.title.trim();
      final named = text == null || text.isEmpty ? null : text;
      final idle =
          now != null &&
          (teamSessionState(now) == AgentState.idle ||
              teamSessionState(now) == AgentState.stopped);
      if (idle) {
        return (l10n.chatWatchEmptyIdleTitle, l10n.chatWatchEmptyIdleBody);
      }
      final reviewer =
          now != null && teamAgentRole(now) == TeamAgentRole.reviewer;
      return (
        l10n.chatWatchEmptyStartingTitle,
        named == null
            ? l10n.chatWatchEmptyStartingBody
            : reviewer
            ? l10n.chatWatchEmptyReviewing(named)
            : l10n.chatWatchEmptyWorkingOn(named),
      );
    },
    readOnlyReason: l10n.teamChatComposerCannot,
    onSend: !canMessage
        ? null
        : (text) async {
            final record = await team.messageAgent(agentId, text);
            sentHere.add(record.key);
            sent.value++;
            return record.status != MutationStatus.rejected;
          },
    draftId: agentId,
    receipt: team == null
        ? null
        : (context) {
            final record = _newestSent(team, sentHere);
            if (record == null) return null;
            return _teamReceipt(
              context,
              record,
              key: const ValueKey('chat-watching-message-receipt'),
              onRetry: () => team.retryMutation(record.key),
            );
          },
    changes: team == null ? null : Listenable.merge([team, sent, rolesLoaded]),
    detailsLabel: !details || team == null || first == null
        ? null
        : l10n.teamWatchAboutRole(who()),
    onDetails: !details || team == null || first == null
        ? null
        : (context) => unawaited(
            pushKitPage<void>(
              context,
              (_) => AgentScreen(
                controller: team,
                agentId: agentId,
                sessionTitle: sessionTitle.value,
              ),
            ),
          ),
  );
}

/// Roles already loaded, by server profile, so a rebuilt page names the
/// worker at once.
final _watchRoles = <String, TeamRolesController>{};

/// "Watching the Frontend · Working": who ([who]: the task's role, else the
/// agent's kind), and its state as its session tells it ([teamSessionState],
/// never the agents list alone).
(String, AppStatusTone) _teamWatchBanner(
  AppLocalizations l10n,
  OrchestrationAgent? agent,
  String who,
) {
  if (agent == null) {
    return (l10n.teamUiAgentOutputLive, AppStatusTone.progress);
  }
  final state = teamSessionState(agent);
  final word = teamAgentStateWord(l10n, state);
  final tone = switch (state) {
    AgentState.working => AppStatusTone.progress,
    AgentState.crashed => AppStatusTone.failure,
    // What it waits on is the conversation's own (a request card); the
    // line stays plain (LOOK-4).
    AgentState.waiting ||
    AgentState.blocked ||
    AgentState.idle ||
    AgentState.stopped ||
    AgentState.unknown => AppStatusTone.neutral,
  };
  return (l10n.teamWatchBannerRole(who, word), tone);
}

/// The newest of the team's records among [keys].
MutationRecord? _newestSent(OrchestrationController team, Set<String> keys) {
  MutationRecord? best;
  for (final record in team.mutations) {
    if (!keys.contains(record.key)) continue;
    if (best == null || record.createdAt.isAfter(best.createdAt)) {
      best = record;
    }
  }
  return best;
}

/// "Open conversation": the hook on the agent screen and the task
/// Overview. One row; opens the agent's own conversation (watching), or
/// Live output when there is none.
class TeamOpenConversationRow extends StatefulWidget {
  const TeamOpenConversationRow({
    super.key,
    required this.team,
    required this.agent,
    this.onOpen,
  });

  final OrchestrationController team;
  final OrchestrationAgent agent;

  /// Tests inject the opener; [openTeamAgentConversation] otherwise.
  final Future<void> Function(BuildContext context, OrchestrationAgent agent)?
  onOpen;

  @override
  State<TeamOpenConversationRow> createState() =>
      _TeamOpenConversationRowState();
}

class _TeamOpenConversationRowState extends State<TeamOpenConversationRow> {
  bool _opening = false;

  Future<void> _open() async {
    if (_opening) return;
    setState(() => _opening = true);
    try {
      final open = widget.onOpen;
      if (open != null) {
        await open(context, widget.agent);
      } else {
        await openTeamAgentConversation(
          context,
          widget.agent,
          team: widget.team,
        );
      }
    } finally {
      if (mounted) setState(() => _opening = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = _chatL10n(context);
    final agent = widget.agent;
    final who =
        teamAgentShortName(agent) ??
        teamAgentRoleWord(l10n, teamAgentRole(agent));
    return KitRow(
      key: ValueKey('team-open-conversation-${agent.id}'),
      leading: KitRow.icon(context, AppIconography.chat),
      title: l10n.teamOpenConversation,
      supporting: TextSpan(
        text: _opening
            ? l10n.teamOpenConversationFinding
            : l10n.teamOpenConversationHint(who),
      ),
      trailing: const KitChevron(),
      onTap: _opening ? null : () => unawaited(_open()),
    );
  }
}

// ---------------------------------------------------------------------------
// An agent's live output, drawn as the chat draws a reply
// ---------------------------------------------------------------------------

/// Live output carries no tool output files.
Future<FilePreviewData> _noFilePreview(ToolOutputFile file) async =>
    FilePreviewData(name: file.displayName, text: '');

Future<void> _noFileAction(ToolOutputFile file, FilePreviewData data) async {}

/// An AI Team agent's live output (Live output, the fallback when its
/// OpenCode session cannot be read) drawn with the chat's own parts: what
/// the agent wrote is the reply's prose, and its `[tool: …]` calls are the
/// chat's tool lines, a run of them folded under one line. There is no
/// second renderer of work: the transcript becomes the chat's message
/// shape and the chat's message view draws it.
class TeamAgentTranscript extends StatefulWidget {
  const TeamAgentTranscript({
    super.key,
    required this.text,
    this.maxBlocks = 40,
  });

  /// The transcript as the host streams it.
  final String text;

  /// Blocks (what it said, or a run of calls) drawn; older ones are left
  /// out, as a long reply's beginning scrolls away.
  final int maxBlocks;

  @override
  State<TeamAgentTranscript> createState() => _TeamAgentTranscriptState();
}

class _TeamAgentTranscriptState extends State<TeamAgentTranscript> {
  final _expansion = <String, bool>{};

  @override
  Widget build(BuildContext context) {
    final blocks = parseAgentTranscript(widget.text);
    final first = math.max(0, blocks.length - widget.maxBlocks);
    final parts = <Part>[];
    for (var i = first; i < blocks.length; i++) {
      switch (blocks[i]) {
        case AgentProse(:final text):
          parts.add(
            Part(
              id: 'agent-output-$i',
              messageID: 'agent-output',
              type: 'text',
              text: text,
            ),
          );
        case AgentStepGroup(:final steps):
          for (var j = 0; j < steps.length; j++) {
            parts.add(_agentStepPart(steps[j], 'agent-output-$i-$j'));
          }
      }
    }
    final message = MessageWithParts(
      info: MessageInfo(
        id: 'agent-output',
        sessionID: 'agent-output',
        role: 'assistant',
        // Drawn finished: no tint on the newest words (the status line
        // above says whether it is live).
        time: MsgTime(completed: 1),
      ),
      parts: parts,
    );
    return _MessageView(
      key: const ValueKey('team-agent-transcript'),
      m: message,
      meta: const _MessageMeta(),
      parts: parts,
      reasoningExpanded: false,
      expansionStore: _expansion,
      showTimestamp: false,
      showActions: false,
      filePreviewLoader: _noFilePreview,
      onAttachFile: null,
      onDownloadFile: _noFileAction,
    );
  }
}

/// One `[tool: …]` call as the chat's tool part: a command is a shell
/// call, a file read or edit names its file, a search its pattern; its
/// output is what the call printed.
Part _agentStepPart(AgentStep step, String id) {
  final tool = step.tool.trim().toLowerCase();
  final (String name, Map<String, dynamic> input) = switch (step.kind) {
    AgentStepKind.command ||
    AgentStepKind.test => ('bash', {'command': step.command}),
    AgentStepKind.read => ('read', {'filePath': step.command}),
    AgentStepKind.edit => (
      const {
            'edit',
            'write',
            'patch',
            'apply_patch',
            'multiedit',
          }.contains(tool)
          ? tool
          : 'edit',
      {'filePath': step.command},
    ),
    AgentStepKind.search => switch (tool) {
      'list' || 'ls' => ('list', {'path': step.command}),
      'glob' => ('glob', {'pattern': step.command}),
      _ => ('grep', {'pattern': step.command}),
    },
    AgentStepKind.other => (tool, {'command': step.command}),
  };
  return Part(
    id: id,
    messageID: 'agent-output',
    type: 'tool',
    callID: id,
    toolName: name,
    toolState: ToolState(
      status: 'completed',
      title: step.command,
      input: input,
      output: step.output.isEmpty ? null : step.output,
    ),
  );
}

/// The task's own words: a task given as a role travels as "Role: …",
/// the role's instructions, a rule and then the person's words
/// ([describeTaskForRole]); the conversation shows only the person's words.
String? _withoutRolePreamble(String? details) {
  if (details == null || !details.startsWith('Role: ')) return details;
  const rule = '\n\n---\n\n';
  final at = details.indexOf(rule);
  return at < 0 ? details : details.substring(at + rule.length);
}

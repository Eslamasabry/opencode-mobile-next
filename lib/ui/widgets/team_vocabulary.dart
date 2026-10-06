/// The AI Team plugin's shared run vocabulary: the order runs appear in,
/// which runs have something waiting on the person, the state words and
/// glyphs (02-ux §11: never colour-only), a run's progress and the honest
/// error copy of 03-onboarding §5. The Workspace card (TEAM-107) and the
/// AI Team home (TEAM-108) both read from here so a run never sorts or
/// reads differently between the two.
///
/// The person's words (docs/design/aiteam-redesign-2026-09-24.md): a run
/// is a "task" with "steps", agents are named by role ([teamAgentRole]),
/// the host is one short phrase ([teamHostPhrase]) and a task moves through
/// four stages ([TeamStage]). Gas City's own words stay under Technical
/// details.
library;

import 'package:flutter/material.dart';

import '../../domain/orchestration_gateway.dart';
export '../../domain/team_glance.dart'
    show teamCompareRuns, teamRunRank, teamVisibleRuns;
export '../../state/team_glance.dart' show teamGatedRuns;
import '../../l10n/app_localizations.dart';
import '../../state/orchestration.dart';
import '../../state/team_conversation.dart' show teamSessionState;
import '../app_theme.dart';
import '../kit/kit_needs_you.dart';
import '../kit/kit_row.dart';
import '../kit/kit_task_mark.dart';
import '../kit/kit_text.dart';
import '../kit/kit_tokens.dart';
import 'relative_time.dart';
import 'team_now.dart';

/// How a team state leads its row (docs/design/visual-language-2026-09-26.md,
/// LOOK-4, LOOK-24): amber means "needs you" and nothing else, so a state
/// that waits on the person is the kit's one needs-you mark
/// ([KitNeedsYou.mark]), and every other state is its glyph in its tone. A
/// held-up, stale or degraded state is neutral, never amber; red is kept
/// for a failure.
@immutable
final class TeamMark {
  /// A state that does not wait on the person: [icon] in [tone].
  const TeamMark(this.icon, AppStatusTone this.tone) : needsYou = false;

  /// A state that waits on the person. [icon] is the kind's own glyph, for a
  /// place that names the kind (a request card's header); a row shows the
  /// needs-you mark instead.
  const TeamMark.needsYou(this.icon) : tone = null, needsYou = true;

  final IconData icon;

  /// The glyph's tone; null for [needsYou], whose colour is the kit's.
  final AppStatusTone? tone;

  final bool needsYou;

  /// The row's leading slot: [KitNeedsYou.mark], or [icon] in its tile
  /// tinted by [tone].
  Widget leading(BuildContext context) {
    final tone = this.tone;
    if (needsYou || tone == null) return KitNeedsYou.mark();
    return KitRow.icon(
      context,
      icon,
      color: KitTokens.toneColor(KitTokens.of(context).roles, tone),
    );
  }
}

// ---------------------------------------------------------------------------
// The person's words: host phrase, task line, stages, roles
// ---------------------------------------------------------------------------

/// The computer's name from the team URL ("dev-pc"), or null when the URL
/// names no host or only an address (an IP or `localhost`): an address is
/// not a name a person would call their computer.
String? teamComputerName(OrchestrationController controller) {
  final name =
      teamHostName(controller.host?.url) ?? teamHostName(controller.config.url);
  if (name == null) return null;
  final lower = name.toLowerCase();
  if (lower == 'localhost' || lower.endsWith('.localhost')) return null;
  final ipv4 = RegExp(r'^\d{1,3}(\.\d{1,3}){3}$');
  if (ipv4.hasMatch(name) || name.contains(':')) return null;
  return name;
}

/// How long after its last good read a team that is starting a worker may
/// answer late before it is called "Not answering". Starting OpenCode under
/// proot saturates the phone, so the app's own reads (30 s) and its live
/// stream (90 s) time out while the team is fine.
const teamStartReadGrace = Duration(minutes: 5);

/// True while a worker is starting and the only trouble is lateness: the
/// team was read within [teamStartReadGrace] and its stream or a read has
/// merely timed out. A team that failed its probe (not reachable) or has
/// not been read for longer is not busy, it is not answering.
bool teamHostBusyStarting(
  OrchestrationController controller, {
  required bool startingWorker,
}) {
  if (!startingWorker || controller.phase != OrchestrationPhase.ready) {
    return false;
  }
  final late =
      controller.isStale ||
      controller.lastError?.kind == OrchestrationErrorKind.readFailed;
  if (!late) return false;
  final at = controller.lastRefreshedAt;
  return at != null && controller.now().difference(at) <= teamStartReadGrace;
}

/// "Paused" when the agents were switched off on purpose (suspended) and
/// none is live, "Not answering" when the shown data is old or the host
/// could not be reached; null otherwise. Agents that are only asleep (they
/// wake when there is work) are not a pause ([teamRest]). [working] is the
/// caller's own evidence that the team is at work (a task's worker starting
/// or working): then it is never "Paused", whatever the agents list says.
String? teamHostCondition(
  AppLocalizations l10n,
  OrchestrationController controller, {
  bool working = false,
  bool startingWorker = false,
  bool teamStarting = false,
}) {
  // The team on this phone is coming up (its own steps are on the page):
  // it is starting, not silent.
  if (teamStarting &&
      (controller.isStale ||
          controller.phase == OrchestrationPhase.failed ||
          controller.lastError?.kind == OrchestrationErrorKind.readFailed)) {
    return l10n.teamUiHostPhraseStarting;
  }
  if (teamHostBusyStarting(controller, startingWorker: startingWorker)) {
    return l10n.teamUiHostPhraseBusyStartingWorker;
  }
  if (controller.isStale ||
      (controller.phase == OrchestrationPhase.ready &&
          controller.lastError?.kind == OrchestrationErrorKind.readFailed)) {
    return l10n.teamUiHostPhraseNotAnswering;
  }
  if (controller.phase == OrchestrationPhase.failed) {
    return switch (controller.lastError?.kind) {
      OrchestrationErrorKind.unreachable ||
      OrchestrationErrorKind.readFailed ||
      null => l10n.teamUiHostPhraseNotAnswering,
      OrchestrationErrorKind.notGasCity ||
      OrchestrationErrorKind.cityNotRunning ||
      OrchestrationErrorKind.plainHttpRefused => null,
    };
  }
  final snapshot = controller.snapshot;
  if (!working &&
      controller.phase == OrchestrationPhase.ready &&
      snapshot.hasData &&
      teamRest(snapshot.agents, config: controller.config) == TeamRest.paused) {
    return l10n.teamUiHostPhrasePaused;
  }
  return null;
}

/// Where the team runs, as one short phrase: "On this phone", "On dev-pc"
/// or "On your computer", then " · Paused" or " · Not answering" when true.
/// Never an address, a version or the engine's name: those are under the
/// info button's Technical details. [working]: see [teamHostCondition].
String teamHostPhrase(
  AppLocalizations l10n,
  OrchestrationController controller, {
  bool working = false,
  bool startingWorker = false,
  bool teamStarting = false,
}) {
  final place = teamHostPlace(l10n, controller);
  final condition = teamHostCondition(
    l10n,
    controller,
    working: working,
    startingWorker: startingWorker,
    teamStarting: teamStarting,
  );
  return condition == null ? place : '$place$teamUsageSeparator$condition';
}

/// Where the team runs, alone: "On this phone", "On dev-pc" or "On your
/// computer" ([teamHostPhrase] without its condition).
String teamHostPlace(
  AppLocalizations l10n,
  OrchestrationController controller,
) {
  final hostMode = controller.host?.hostMode ?? controller.config.hostMode;
  return switch (hostMode) {
    OrchestrationHostMode.phone => l10n.teamUiHostPhrasePhone,
    OrchestrationHostMode.computer => switch (teamComputerName(controller)) {
      final name? => l10n.teamUiHostPhraseComputerNamed(name),
      null => l10n.teamUiHostPhraseComputer,
    },
  };
}

/// The four stages a task moves through, in order, each named by its
/// outcome: Planned, Working, In review, Merged. At most these show, on
/// the task's Overview only.
enum TeamStage { waiting, working, reviewing, done }

/// The stage [run] is at: done (Merged) only once it is completed and every
/// step of it is closed, reviewing while its work is in the merge agent's
/// hands ([teamRunAwaitsMerge]) or a completed task still has a step open,
/// working while work moves (held-up work included), waiting before that.
/// Null for a failed or cancelled task: it left the stages, and its status
/// line says so.
TeamStage? teamRunStage(
  OrchestrationRun run,
  List<WorkItem> work, {
  DispatchCycle? Function(String workId)? cycleOf,
}) {
  switch (run.state) {
    case RunState.completed:
      // Merged means every step landed: a step still open (in review,
      // say) keeps the task in review.
      final open = work.any(
        (item) => item.runId == run.id && teamWorkIsOpen(item.state),
      );
      return open ? TeamStage.reviewing : TeamStage.done;
    case RunState.failed || RunState.cancelled:
      return null;
    case RunState.working ||
        RunState.blocked ||
        RunState.waiting ||
        RunState.planning ||
        RunState.unknown:
      break;
  }
  if (teamRunAwaitsMerge(run, work, cycleOf: cycleOf)) {
    return TeamStage.reviewing;
  }
  return switch (run.state) {
    RunState.working || RunState.blocked => TeamStage.working,
    _ => TeamStage.waiting,
  };
}

/// The one word for a stage.
String teamStageWord(AppLocalizations l10n, TeamStage stage) => switch (stage) {
  TeamStage.waiting => l10n.teamUiRunStageWaiting,
  TeamStage.working => l10n.teamUiRunStageWorking,
  TeamStage.reviewing => l10n.teamUiRunStageReviewing,
  TeamStage.done => l10n.teamUiRunStageDone,
};

/// A task's leading mark: needs you, then failed, done, stopped
/// (cancelled), working (planning and the merge wait included) and
/// waiting (held up or not started).
///
/// With [work], a completed task with a step still open is in review, not
/// done ([teamRunStage]), and its mark says so.
KitTaskState teamRunMark(
  OrchestrationRun run, {
  required bool needsYou,
  List<WorkItem>? work,
}) {
  if (needsYou) return KitTaskState.needsYou;
  return switch (run.state) {
    RunState.failed => KitTaskState.failed,
    RunState.completed
        when work != null && teamRunStage(run, work) == TeamStage.reviewing =>
      KitTaskState.working,
    RunState.completed => KitTaskState.done,
    RunState.cancelled => KitTaskState.stopped,
    RunState.working || RunState.planning => KitTaskState.working,
    RunState.waiting ||
    RunState.blocked ||
    RunState.unknown => KitTaskState.waiting,
  };
}

/// "3 of 5 steps done", or null for a task of one step or none (its state
/// says it all).
String? teamTaskSteps(AppLocalizations l10n, TeamRunProgress progress) =>
    progress.total <= 1
    ? null
    : l10n.teamUiTaskSteps(progress.done, progress.total);

/// A task's one supporting line: "Working · 3 of 5 steps done", "Needs
/// you · 1 of 5 steps done", "Waiting for a worker", "Reviewing", "Done ·
/// merged 5h ago".
String teamTaskLine(
  AppLocalizations l10n,
  OrchestrationRun run,
  List<WorkItem> work, {
  required bool needsYou,
  required DateTime now,
  DispatchCycle? Function(String workId)? cycleOf,
  bool explainWait = false,
  Duration? checkEvery,
  bool paused = false,
  bool showWaitAge = true,
}) {
  final finishedAt = run.finishedAt;
  if (finishedAt != null &&
      (run.state == RunState.completed || run.state == RunState.cancelled)) {
    final when = relativeTimeLabel(
      finishedAt.millisecondsSinceEpoch,
      now: now,
      l10n: l10n,
    );
    return switch (run.state) {
      RunState.cancelled => l10n.teamUiTaskCancelledAgo(when),
      _ when run.merged => l10n.teamUiTaskMergedAgo(when),
      _ => l10n.teamUiTaskDoneAgo(when),
    };
  }
  // A task waiting for a worker says for how long and when one starts
  // (teamWaitLine); the home and the Work tab card ask for it.
  if (explainWait && !needsYou) {
    final wait = teamWaitLine(
      l10n,
      run,
      work,
      now: now,
      every: checkEvery,
      paused: paused,
      showAge: showWaitAge,
      cycleOf: cycleOf,
    );
    if (wait != null) return wait;
  }
  final word = needsYou
      ? l10n.teamUiHomeRunNeedsYou
      : teamRunStateWordFor(l10n, run, work, cycleOf: cycleOf);
  final open =
      run.state != RunState.completed && run.state != RunState.cancelled;
  final steps = open
      ? teamTaskSteps(l10n, TeamRunProgress.of(run, work))
      : null;
  return [word, ?steps].join(teamUsageSeparator);
}

/// What an agent does for the team, in plain words. Gas City's own names
/// (polecat, refinery, mayor…) stay on the agent's Technical details.
enum TeamAgentRole { worker, reviewer, planner, supervisor, helper, other }

/// The role of [agent], read from its pool (or, for a named agent, its
/// name): the last part after "/" and ".", without an instance number.
TeamAgentRole teamAgentRole(OrchestrationAgent agent) {
  final source = (agent.pool ?? agent.name).toLowerCase();
  final tail = source
      .split('/')
      .last
      .split('.')
      .last
      .replaceFirst(RegExp(r'-\d+$'), '');
  return switch (tail) {
    'polecat' || 'polecats' => TeamAgentRole.worker,
    'refinery' => TeamAgentRole.reviewer,
    'mayor' => TeamAgentRole.planner,
    'witness' || 'deacon' || 'boot' => TeamAgentRole.supervisor,
    'dog' || 'dogs' => TeamAgentRole.helper,
    _ => TeamAgentRole.other,
  };
}

/// The one word for a role.
String teamAgentRoleWord(AppLocalizations l10n, TeamAgentRole role) =>
    switch (role) {
      TeamAgentRole.worker => l10n.teamUiAgentRoleWorker,
      TeamAgentRole.reviewer => l10n.teamUiAgentRoleReviewer,
      TeamAgentRole.planner => l10n.teamUiAgentRolePlanner,
      TeamAgentRole.supervisor => l10n.teamUiAgentRoleSupervisor,
      TeamAgentRole.helper => l10n.teamUiAgentRoleHelper,
      TeamAgentRole.other => l10n.teamUiAgentRoleOther,
    };

/// How much of a run is done, working and blocked, from its work items
/// when the snapshot has them and from its step counts otherwise.
class TeamRunProgress {
  const TeamRunProgress({
    required this.done,
    required this.working,
    required this.blocked,
    required this.total,
  });

  final int done;
  final int working;
  final int blocked;
  final int total;

  /// Whole percent done, or null when the host counted nothing.
  int? get percent =>
      total <= 0 ? null : (done * 100 / total).floor().clamp(0, 100);

  static TeamRunProgress of(OrchestrationRun run, List<WorkItem> work) {
    final items = [
      for (final item in work)
        if (item.runId == run.id) item,
    ];
    if (items.isNotEmpty) {
      var done = 0, working = 0, blocked = 0;
      for (final item in items) {
        // Handed to the merge agent (TEAM-117): nobody works it.
        if (workHandedToMerge(item)) continue;
        switch (item.state) {
          case WorkState.completed:
            done += 1;
          case WorkState.working:
          case WorkState.review:
            working += 1;
          case WorkState.blocked:
          case WorkState.needsInput:
          case WorkState.failed:
            blocked += 1;
          case WorkState.queued:
          case WorkState.ready:
          case WorkState.waiting:
          case WorkState.cancelled:
          case WorkState.unknown:
            break;
        }
      }
      return TeamRunProgress(
        done: done,
        working: working,
        blocked: blocked,
        total: items.length,
      );
    }
    final total = run.stepCount ?? 0;
    final done = (run.completedSteps ?? 0).clamp(0, total);
    final open = total - done;
    return TeamRunProgress(
      done: done,
      working: run.state == RunState.working && open > 0 ? 1 : 0,
      blocked: run.state == RunState.blocked && open > 0 ? 1 : 0,
      total: total,
    );
  }
}

// ---------------------------------------------------------------------------
// What the person sees (TEAM-115): the host's upkeep and empty slots hidden
// ---------------------------------------------------------------------------

/// The host's upkeep runs (patrols, chores), for the "Show team upkeep"
/// reveal.
List<OrchestrationRun> teamUpkeepRuns(Iterable<OrchestrationRun> runs) => [
  for (final run in runs)
    if (run.isUpkeep) run,
];

/// Whether an agent is live: anything but stopped or suspended, with its
/// session's word first ([teamSessionState], ledger row 21): a running
/// session is live even on an agent the list calls stopped, and a session
/// the host reports stopped is not live whatever the list says. An asleep
/// session leaves the list's word standing. Only live agents are dots on
/// the card and counted as the team.
bool teamAgentIsLive(OrchestrationAgent agent) {
  final state = teamSessionState(agent);
  if (state == AgentState.working) return true;
  if (state == AgentState.stopped) return false;
  return agent.state != AgentState.stopped && !agent.suspended;
}

/// The live agents, in the given order.
List<OrchestrationAgent> teamLiveAgents(Iterable<OrchestrationAgent> agents) =>
    [
      for (final agent in agents)
        if (teamAgentIsLive(agent)) agent,
    ];

/// The agents switched off on the host (suspended or stopped): listed
/// under a collapsed group on the home, never on the card.
List<OrchestrationAgent> teamOffAgents(Iterable<OrchestrationAgent> agents) => [
  for (final agent in agents)
    if (!teamAgentIsLive(agent)) agent,
];

/// The host's name from the orchestration URL: `dev-pc` for
/// `https://dev-pc:7000`, `100.101.102.103` for an address, IPv6 without its
/// brackets. Null when the URL names no host (a fixture path or a
/// `fixture://` URL). Never the connected OpenCode profile's name: the
/// team runs on the host, which is not necessarily the OpenCode server.
String? teamHostName(String? url) {
  if (url == null || url.trim().isEmpty) return null;
  final uri = Uri.tryParse(url.trim());
  if (uri?.scheme == 'fixture') return null;
  var host = uri?.host ?? '';
  if (host.isEmpty) {
    final match = RegExp(
      r'^[A-Za-z][A-Za-z0-9+.-]*://([^/?#]+)',
    ).firstMatch(url.trim());
    host = match?.group(1) ?? '';
    final at = host.lastIndexOf('@');
    if (at >= 0) host = host.substring(at + 1);
    if (!host.startsWith('[')) {
      final colon = host.indexOf(':');
      if (colon >= 0) host = host.substring(0, colon);
    }
  }
  if (host.startsWith('[') && host.endsWith(']')) {
    host = host.substring(1, host.length - 1);
  }
  return host.isEmpty ? null : host;
}

/// The host's name for a controller: the probe's URL, else the config's,
/// else the provider's name (a fixture path names no host).
String teamHostNameOf(OrchestrationController controller) =>
    teamHostName(controller.host?.url) ??
    teamHostName(controller.config.url) ??
    controller.host?.provider ??
    controller.config.provider.name;

/// HH:MM of [at] in the device's zone, for "Showing data from 09:41".
String teamClockLabel(BuildContext context, DateTime at) =>
    MaterialLocalizations.of(context).formatTimeOfDay(
      TimeOfDay.fromDateTime(at.toLocal()),
      alwaysUse24HourFormat: true,
    );

/// The one word for a run state.
String teamRunStateWord(AppLocalizations l10n, RunState state) =>
    switch (state) {
      RunState.planning => l10n.teamUiCardRunStatePlanning,
      RunState.working => l10n.teamUiCardRunStateWorking,
      RunState.waiting => l10n.teamUiCardRunStateWaiting,
      RunState.blocked => l10n.teamUiCardRunStateBlocked,
      RunState.failed => l10n.teamUiCardRunStateFailed,
      RunState.completed => l10n.teamUiCardRunStateCompleted,
      RunState.cancelled => l10n.teamUiCardRunStateCancelled,
      RunState.unknown => l10n.teamUiCardRunStateUnknown,
    };

/// The host's upkeep in words, one phrase per kind with duplicates
/// collapsed: "Patrol ×4 · planning; Chore · working". Patrols are the
/// host checking on its agents; everything else is a chore. Each kind says
/// its most active state. Never the engine's formula names.
String teamUpkeepLine(
  AppLocalizations l10n,
  Iterable<OrchestrationRun> upkeep,
) {
  bool patrol(OrchestrationRun run) => [
    run.title,
    run.formula,
  ].any((name) => name != null && name.toLowerCase().contains('patrol'));
  const activity = [
    RunState.working,
    RunState.planning,
    RunState.waiting,
    RunState.blocked,
    RunState.failed,
    RunState.unknown,
    RunState.completed,
    RunState.cancelled,
  ];
  String group(String kind, List<OrchestrationRun> runs) {
    final state = activity.firstWhere(
      (state) => runs.any((run) => run.state == state),
      orElse: () => RunState.unknown,
    );
    return l10n.teamHomeUpkeepGroup(
      runs.length,
      kind,
      teamRunStateWord(l10n, state).toLowerCase(),
    );
  }

  final patrols = [
    for (final run in upkeep)
      if (patrol(run)) run,
  ];
  final chores = [
    for (final run in upkeep)
      if (!patrol(run)) run,
  ];
  return [
    if (patrols.isNotEmpty) group(l10n.teamHomeUpkeepPatrol, patrols),
    if (chores.isNotEmpty) group(l10n.teamHomeUpkeepChore, chores),
  ].join('; ');
}

/// The run's state word with the merge named: "Waiting for merge" when
/// [teamRunAwaitsMerge] (TEAM-117), "Done · merged" for a completed run
/// whose work landed ([OrchestrationRun.merged]), else [teamRunStateWord].
String teamRunStateWordFor(
  AppLocalizations l10n,
  OrchestrationRun run,
  List<WorkItem> work, {
  DispatchCycle? Function(String workId)? cycleOf,
}) => teamRunAwaitsMerge(run, work, cycleOf: cycleOf)
    ? l10n.teamUiCardRunStateWaitingMerge
    // Done only once every step landed: a completed task with a step still
    // open is in review, as its stage line says.
    : teamRunStage(run, work, cycleOf: cycleOf) == TeamStage.reviewing &&
          run.state == RunState.completed
    ? l10n.teamUiCardRunStateWaitingMerge
    : run.state == RunState.completed && run.merged
    ? l10n.teamUiCardRunStateMerged
    : teamRunStateWord(l10n, run.state);

/// True when [item] is open and waits for the merge agent rather than
/// for work (TEAM-117): the mapper saw it handed to the rig's refinery,
/// or its dispatch [cycle] (when the caller has one) is past the hand-off
/// and not merged — a polecat that drained after pushing, before the
/// bead was reassigned.
bool teamWorkAwaitsMerge(WorkItem item, {DispatchCycle? cycle}) {
  if (workHandedToMerge(item)) return true;
  if (cycle == null || cycle.isTerminal) return false;
  if (!cycle.isDone(DispatchStep.handedToMerge)) return false;
  return switch (item.state) {
    WorkState.queued ||
    WorkState.ready ||
    WorkState.working ||
    WorkState.waiting ||
    WorkState.review ||
    WorkState.unknown => true,
    WorkState.blocked ||
    WorkState.needsInput ||
    WorkState.failed ||
    WorkState.completed ||
    WorkState.cancelled => false,
  };
}

/// True when the open [run] tracks work and every open item of it awaits
/// the merge agent ([teamWorkAwaitsMerge]): the batch is waiting for the
/// merge, not for an agent and not working.
bool teamRunAwaitsMerge(
  OrchestrationRun run,
  List<WorkItem> work, {
  DispatchCycle? Function(String workId)? cycleOf,
}) {
  if (run.state != RunState.waiting && run.state != RunState.working) {
    return false;
  }
  var any = false;
  for (final item in work) {
    if (item.runId != run.id || !teamWorkIsOpen(item.state)) continue;
    if (!teamWorkAwaitsMerge(item, cycle: cycleOf?.call(item.id))) {
      return false;
    }
    any = true;
  }
  return any;
}

/// When the [run]'s last hand-off to the merge agent happened, for "20 h
/// since hand-off": the latest hand-off among its items awaiting merge —
/// the cycle's hand-off time, else the bead's `updated_at`, else its
/// `created_at`. Null when no item of the run awaits merge or none
/// carries a time.
DateTime? teamRunHandoffAt(
  OrchestrationRun run,
  List<WorkItem> work, {
  DispatchCycle? Function(String workId)? cycleOf,
}) {
  DateTime? latest;
  for (final item in work) {
    if (item.runId != run.id || !teamWorkIsOpen(item.state)) continue;
    final cycle = cycleOf?.call(item.id);
    if (!teamWorkAwaitsMerge(item, cycle: cycle)) continue;
    final at =
        cycle?.reachedAt[DispatchStep.handedToMerge] ??
        item.updatedAt ??
        item.createdAt;
    if (at != null && (latest == null || at.isAfter(latest))) latest = at;
  }
  return latest;
}

/// Glyph and tone per run state: status is never colour-only (§11). A
/// held-up run is neutral: amber means "needs you" only, and the person is
/// asked through the run's gates, which carry the needs-you mark.
(IconData, AppStatusTone) teamRunGlyph(RunState state) => switch (state) {
  RunState.planning => (AppIconography.clock, AppStatusTone.neutral),
  RunState.working => (AppIconography.play, AppStatusTone.progress),
  RunState.waiting => (AppIconography.waiting, AppStatusTone.neutral),
  RunState.blocked => (AppIconography.blocked, AppStatusTone.neutral),
  RunState.failed => (AppIconography.error, AppStatusTone.failure),
  RunState.completed => (AppIconography.check, AppStatusTone.ok),
  RunState.cancelled => (AppIconography.close, AppStatusTone.neutral),
  RunState.unknown => (AppIconography.question, AppStatusTone.neutral),
};

/// The one word for a work state (BRD §14).
String teamWorkStateWord(AppLocalizations l10n, WorkState state) =>
    switch (state) {
      WorkState.queued => l10n.teamUiWorkStateQueued,
      WorkState.ready => l10n.teamUiWorkStateReady,
      WorkState.working => l10n.teamUiWorkStateWorking,
      WorkState.waiting => l10n.teamUiWorkStateWaiting,
      WorkState.blocked => l10n.teamUiWorkStateBlocked,
      WorkState.needsInput => l10n.teamUiWorkStateNeedsInput,
      WorkState.review => l10n.teamUiWorkStateReview,
      WorkState.failed => l10n.teamUiWorkStateFailed,
      WorkState.completed => l10n.teamUiWorkStateCompleted,
      WorkState.cancelled => l10n.teamUiWorkStateCancelled,
      WorkState.unknown => l10n.teamUiWorkStateUnknown,
    };

/// The mark per work state: status is never colour-only (§11). Only an
/// item that waits on the person takes the needs-you mark; a blocked one
/// is held up by other work, not by the person, so it is neutral.
TeamMark teamWorkMark(WorkState state) => switch (state) {
  WorkState.queued => const TeamMark(
    AppIconography.radioEmpty,
    AppStatusTone.neutral,
  ),
  WorkState.ready => const TeamMark(
    AppIconography.playCircle,
    AppStatusTone.neutral,
  ),
  WorkState.working => const TeamMark(
    AppIconography.play,
    AppStatusTone.progress,
  ),
  WorkState.waiting => const TeamMark(
    AppIconography.waiting,
    AppStatusTone.neutral,
  ),
  WorkState.blocked => const TeamMark(
    AppIconography.blocked,
    AppStatusTone.neutral,
  ),
  WorkState.needsInput => const TeamMark.needsYou(AppIconography.question),
  WorkState.review => const TeamMark(
    AppIconography.review,
    AppStatusTone.progress,
  ),
  WorkState.failed => const TeamMark(
    AppIconography.error,
    AppStatusTone.failure,
  ),
  WorkState.completed => const TeamMark(AppIconography.check, AppStatusTone.ok),
  WorkState.cancelled => const TeamMark(
    AppIconography.close,
    AppStatusTone.neutral,
  ),
  WorkState.unknown => const TeamMark(
    AppIconography.question,
    AppStatusTone.neutral,
  ),
};

/// The Work tab's group order (02-ux §4.2): what needs the person first,
/// then what is held up, then what moves, then the rest; the two states
/// outside the BRD list (Waiting, Unknown) sit beside their nearest kin.
const teamWorkStateOrder = [
  WorkState.needsInput,
  WorkState.blocked,
  WorkState.waiting,
  WorkState.working,
  WorkState.ready,
  WorkState.queued,
  WorkState.review,
  WorkState.completed,
  WorkState.failed,
  WorkState.cancelled,
  WorkState.unknown,
];

/// Position of [state] in [teamWorkStateOrder].
int teamWorkStateRank(WorkState state) => teamWorkStateOrder.indexOf(state);

/// Whether an item in [state] still holds up what depends on it.
bool teamWorkIsOpen(WorkState state) =>
    state != WorkState.completed && state != WorkState.cancelled;

/// The dependencies of [item] that still hold it up: the ones [work] lists
/// as open. A dependency the host no longer lists is not counted (the board
/// counts the same way), so a finished step never reads as a wait.
List<WorkItem> teamOpenDependencies(WorkItem item, List<WorkItem> work) {
  if (item.dependsOn.isEmpty) return const [];
  final ids = item.dependsOn.toSet();
  return [
    for (final other in work)
      if (ids.contains(other.id) && teamWorkIsOpen(other.state)) other,
  ];
}

/// Whether an item in [state] is stuck: blocked, waiting on the person or
/// failed. These start the graph's highlighted blocked chain.
bool teamWorkIsStuck(WorkState state) =>
    state == WorkState.blocked ||
    state == WorkState.needsInput ||
    state == WorkState.failed;

/// Honest one-line copy for a failed probe or read (03-onboarding §5).
String teamErrorCopy(AppLocalizations l10n, OrchestrationErrorKind? kind) =>
    switch (kind) {
      OrchestrationErrorKind.notGasCity => l10n.teamUiCardErrorNotGasCity,
      OrchestrationErrorKind.cityNotRunning =>
        l10n.teamUiCardErrorCityNotRunning,
      OrchestrationErrorKind.plainHttpRefused => l10n.teamUiCardErrorPlainHttp,
      OrchestrationErrorKind.unreachable ||
      OrchestrationErrorKind.readFailed ||
      null => l10n.teamUiCardErrorUnreachable,
    };

/// The BRD §47 order of what needs the person: decision, run failed,
/// review ready, gate bead. Shared by the home's Needs you segment and the
/// run's inline card so the same gate is "most urgent" on both.
int teamGateRank(GateKind kind) => switch (kind) {
  GateKind.choice ||
  GateKind.confirmation ||
  GateKind.freeText ||
  GateKind.unknown => 0,
  GateKind.runFailed => 1,
  GateKind.reviewReady => 2,
  GateKind.gateBead => 3,
};

/// The one word for a gate kind.
String teamGateKindWord(AppLocalizations l10n, GateKind kind) => switch (kind) {
  GateKind.choice => l10n.teamUiHomeGateKindChoice,
  GateKind.confirmation => l10n.teamUiHomeGateKindConfirmation,
  GateKind.freeText => l10n.teamUiHomeGateKindFreeText,
  GateKind.gateBead => l10n.teamUiHomeGateKindGateBead,
  GateKind.runFailed => l10n.teamUiHomeGateKindRunFailed,
  GateKind.reviewReady => l10n.teamUiHomeGateKindReviewReady,
  GateKind.unknown => l10n.teamUiHomeGateKindUnknown,
};

/// The mark per gate kind. Every question, confirmation, text answer and
/// gate the person closes takes the kit's one needs-you mark in a row; the
/// kind's own glyph stays for a place that names the kind (the request
/// card's header). A failed run keeps the failure glyph, a review that is
/// ready is neutral.
TeamMark teamGateMark(GateKind kind) => switch (kind) {
  GateKind.choice => const TeamMark.needsYou(AppIconography.question),
  GateKind.confirmation => const TeamMark.needsYou(AppIconography.checkCircle),
  GateKind.freeText => const TeamMark.needsYou(AppIconography.editNote),
  GateKind.gateBead => const TeamMark.needsYou(AppIconography.blocked),
  GateKind.runFailed => const TeamMark(
    AppIconography.error,
    AppStatusTone.failure,
  ),
  GateKind.reviewReady => const TeamMark(
    AppIconography.review,
    AppStatusTone.neutral,
  ),
  GateKind.unknown => const TeamMark.needsYou(AppIconography.warning),
};

/// The run a gate belongs to: named directly, else through its work item,
/// else through the work item of the agent it names. Null when none.
String? teamGateRunId(OrchestrationSnapshot snapshot, OrchestrationGate gate) {
  if (gate.runId case final id?) return id;
  String? runOfWork(String? workId) {
    if (workId == null) return null;
    for (final item in snapshot.work) {
      if (item.id == workId) return item.runId;
    }
    return null;
  }

  if (runOfWork(gate.workId) case final id?) return id;
  for (final agent in snapshot.agents) {
    if (agent.id == gate.agentId || agent.sessionId == gate.agentId) {
      return runOfWork(agent.currentWorkId);
    }
  }
  return null;
}

/// What the gate belongs to, as one line: the run, else the work item,
/// else the agent. Server titles are shown verbatim.
String? teamGateLink(
  AppLocalizations l10n,
  OrchestrationSnapshot snapshot,
  OrchestrationGate gate,
) {
  for (final run in snapshot.runs) {
    if (run.id == gate.runId) return l10n.teamUiHomeGateLinkRun(run.title);
  }
  for (final item in snapshot.work) {
    if (item.id == gate.workId) return l10n.teamUiHomeGateLinkWork(item.title);
  }
  for (final agent in snapshot.agents) {
    if (agent.id == gate.agentId || agent.sessionId == gate.agentId) {
      return l10n.teamUiHomeGateLinkAgent(agent.name);
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Activity (02-ux §6, BRD §47)
// ---------------------------------------------------------------------------

/// The Activity inbox's rank of a gate in the BRD §47 order: decision
/// requested (0), run failed (1), then — after the app's own permissions,
/// questions and forms at [teamActivityPermissionRank] — review ready (3)
/// and gate beads (4); blocked agents follow at
/// [teamActivityAgentBlockedRank].
int teamActivityGateRank(GateKind kind) => switch (kind) {
  GateKind.choice ||
  GateKind.confirmation ||
  GateKind.freeText ||
  GateKind.unknown => 0,
  GateKind.runFailed => 1,
  GateKind.reviewReady => 3,
  GateKind.gateBead => 4,
};

/// Where the app's own permissions, questions and forms sit in the §47
/// order.
const teamActivityPermissionRank = 2;

/// Where a blocked agent sits in the §47 order.
const teamActivityAgentBlockedRank = 5;

/// Whether a confirmation would destroy something, from the host's
/// `destructive` flag when it sends one and from the prompt's words
/// otherwise; the sheet marks these in the error tone.
bool teamGateIsDestructive(OrchestrationGate gate) {
  final raw = gate.raw;
  final flagged = raw['destructive'] ?? raw['is_destructive'];
  if (flagged is bool) return flagged;
  if (raw['metadata'] case final Map<Object?, Object?> meta) {
    final flag = meta['destructive'] ?? meta['is_destructive'];
    if (flag is bool) return flag;
  }
  final text = '${gate.title} ${gate.prompt ?? ''}';
  return _destructiveWords.hasMatch(text);
}

final _destructiveWords = RegExp(
  r'\b(delete|remove|drop|discard|overwrite|reset|wipe|destroy|purge|erase|'
  r'force[- ]push|rm -rf|revert|uninstall)\b',
  caseSensitive: false,
);

/// How a failed run failed (02-ux §6), read from the host's error text
/// with [teamClassifyFailure]; [unknown] when the words match nothing.
enum TeamFailureClass {
  agent,
  execution,
  test,
  mergeConflict,
  infrastructure,
  dependency,
  authentication,
  context,
  unknown,
}

/// Classifies a run's `last_error` text by its words. The order matters:
/// the more specific families (merge conflict, authentication, context,
/// dependency) are tried before the generic ones (test, infrastructure,
/// execution, agent) so "tests failed after a merge conflict" is a merge
/// conflict. Null or empty text is [TeamFailureClass.unknown].
TeamFailureClass teamClassifyFailure(String? text) {
  if (text == null || text.trim().isEmpty) return TeamFailureClass.unknown;
  for (final (family, pattern) in _failureFamilies) {
    if (pattern.hasMatch(text)) return family;
  }
  return TeamFailureClass.unknown;
}

final _failureFamilies = <(TeamFailureClass, RegExp)>[
  (
    TeamFailureClass.mergeConflict,
    RegExp(
      r'merge conflict|\bconflict(s|ed|ing)?\b|unmerged|rebase',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.authentication,
    RegExp(
      r'unauthori[sz]ed|unauthenticated|authentication|\bauth\b|'
      r'\b40[13]\b|api[ _-]?key|credential|token (expired|invalid|missing)|'
      r'invalid token|login|forbidden',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.context,
    RegExp(
      r'context (window|length|limit|size)|maximum context|'
      r'too many tokens|token limit|max_tokens|prompt (is )?too long',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.dependency,
    RegExp(
      r'dependenc|module ?not ?found|cannot find (module|package)|'
      r'no such module|unresolved import|import ?error|'
      r'version solving|pub get|npm install|package .* not found|'
      r'missing (package|library|module)|not installed',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.test,
    RegExp(
      r'\btests?\b|assert|expectation|pytest|jest|spec failed|'
      r'\d+ (failed|failing)',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.infrastructure,
    RegExp(
      r'time[d ]?out|connection (refused|reset)|econnrefused|network|'
      r'\bdns\b|unreachable|disk|out of memory|\boom\b|\b50[023]\b|'
      r'\b429\b|rate limit|server error|unavailable|no space left',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.execution,
    RegExp(
      r'exit(ed)? (code|with|status)|non-?zero|command failed|build failed|'
      r'compil|syntax error|exception|traceback|panic|segfault|'
      r'permission denied|no such file',
      caseSensitive: false,
    ),
  ),
  (
    TeamFailureClass.agent,
    RegExp(
      r'\bagent\b|crash|session (died|ended|lost)|harness|polecat|'
      r'no response|stalled|stuck|nudge|gave up|abandoned',
      caseSensitive: false,
    ),
  ),
];

/// The one word for a failure class.
String teamFailureClassWord(
  AppLocalizations l10n,
  TeamFailureClass cls,
) => switch (cls) {
  TeamFailureClass.agent => l10n.teamUiGateFailureClassAgent,
  TeamFailureClass.execution => l10n.teamUiGateFailureClassExecution,
  TeamFailureClass.test => l10n.teamUiGateFailureClassTest,
  TeamFailureClass.mergeConflict => l10n.teamUiGateFailureClassMergeConflict,
  TeamFailureClass.infrastructure => l10n.teamUiGateFailureClassInfrastructure,
  TeamFailureClass.dependency => l10n.teamUiGateFailureClassDependency,
  TeamFailureClass.authentication => l10n.teamUiGateFailureClassAuthentication,
  TeamFailureClass.context => l10n.teamUiGateFailureClassContext,
  TeamFailureClass.unknown => l10n.teamUiGateFailureClassUnknown,
};

/// Whether a retry from the host can recover a failure of this class
/// without a person changing something first; null when the class is
/// unknown, so the sheet says so rather than guessing.
bool? teamFailureRecoverable(TeamFailureClass cls) => switch (cls) {
  TeamFailureClass.agent ||
  TeamFailureClass.execution ||
  TeamFailureClass.infrastructure ||
  TeamFailureClass.context => true,
  TeamFailureClass.test ||
  TeamFailureClass.mergeConflict ||
  TeamFailureClass.dependency ||
  TeamFailureClass.authentication => false,
  TeamFailureClass.unknown => null,
};

/// The recommended action for a failure class, as text: the buttons that
/// perform it are Sprint B.
String teamFailureAction(
  AppLocalizations l10n,
  TeamFailureClass cls,
) => switch (cls) {
  TeamFailureClass.agent => l10n.teamUiGateFailureActionAgent,
  TeamFailureClass.execution => l10n.teamUiGateFailureActionExecution,
  TeamFailureClass.test => l10n.teamUiGateFailureActionTest,
  TeamFailureClass.mergeConflict => l10n.teamUiGateFailureActionMergeConflict,
  TeamFailureClass.infrastructure => l10n.teamUiGateFailureActionInfrastructure,
  TeamFailureClass.dependency => l10n.teamUiGateFailureActionDependency,
  TeamFailureClass.authentication => l10n.teamUiGateFailureActionAuthentication,
  TeamFailureClass.context => l10n.teamUiGateFailureActionContext,
  TeamFailureClass.unknown => l10n.teamUiGateFailureActionUnknown,
};

// ---------------------------------------------------------------------------
// Agents (02-ux §5.1)
// ---------------------------------------------------------------------------

/// Context use from which the number reads in the primary text tone.
const teamContextHighPercent = 75;

/// Context use from which the agent detail says "Recycling soon" (the
/// host's recycle policy threshold).
const teamContextRecyclePercent = 90;

/// Tone of a context-use number: secondary text while it is fine, primary
/// text from [teamContextHighPercent]. Never amber (that means "needs you")
/// and never red: a full context is the host's to recycle, not a failure.
KitTextTone teamContextTone(int percent) => percent >= teamContextHighPercent
    ? KitTextTone.primary
    : KitTextTone.secondary;

/// Sort of §5.1: needs-you first, then working, idle, stopped; a crashed
/// agent sits with the exceptions, right after the ones waiting.
int teamAgentRank(AgentState state) => switch (state) {
  AgentState.waiting || AgentState.blocked => 0,
  AgentState.crashed => 1,
  AgentState.working => 2,
  AgentState.idle => 3,
  AgentState.stopped => 4,
  AgentState.unknown => 5,
};

/// [teamAgentRank] first, then by name so the order is stable.
int teamCompareAgents(OrchestrationAgent a, OrchestrationAgent b) {
  final rank = teamAgentRank(a.state).compareTo(teamAgentRank(b.state));
  return rank != 0 ? rank : a.name.compareTo(b.name);
}

/// The one word for an agent state.
String teamAgentStateWord(AppLocalizations l10n, AgentState state) =>
    switch (state) {
      AgentState.working => l10n.teamUiHomeAgentStateWorking,
      AgentState.idle => l10n.teamUiHomeAgentStateIdle,
      AgentState.waiting => l10n.teamUiHomeAgentStateWaiting,
      AgentState.blocked => l10n.teamUiHomeAgentStateBlocked,
      AgentState.stopped => l10n.teamUiHomeAgentStateStopped,
      AgentState.crashed => l10n.teamUiHomeAgentStateCrashed,
      AgentState.unknown => l10n.teamUiHomeAgentStateUnknown,
    };

/// The mark per agent state: status is never colour-only (§11). An agent
/// that waits on the person takes the needs-you mark; a blocked one is
/// held up elsewhere and stays neutral.
TeamMark teamAgentMark(AgentState state) => switch (state) {
  AgentState.working => const TeamMark(
    AppIconography.play,
    AppStatusTone.progress,
  ),
  AgentState.idle => const TeamMark(
    AppIconography.statusDot,
    AppStatusTone.neutral,
  ),
  AgentState.waiting => const TeamMark.needsYou(AppIconography.question),
  AgentState.blocked => const TeamMark(
    AppIconography.blocked,
    AppStatusTone.neutral,
  ),
  AgentState.stopped => const TeamMark(
    AppIconography.stopCircle,
    AppStatusTone.neutral,
  ),
  AgentState.crashed => const TeamMark(
    AppIconography.error,
    AppStatusTone.failure,
  ),
  AgentState.unknown => const TeamMark(
    AppIconography.question,
    AppStatusTone.neutral,
  ),
};

/// "12m", "3h 14m", "2d": an elapsed span in the run's short form.
String teamElapsedLabel(AppLocalizations l10n, Duration elapsed) {
  if (elapsed.inHours < 1) {
    return l10n.teamUiRunElapsedMinutes(elapsed.inMinutes);
  }
  if (elapsed.inDays < 1) {
    return l10n.teamUiRunElapsedHours(
      elapsed.inHours,
      elapsed.inMinutes - elapsed.inHours * 60,
    );
  }
  return l10n.teamUiRunElapsedDays(elapsed.inDays);
}

// ---------------------------------------------------------------------------
// Usage (05-beads TEAM-113)
// ---------------------------------------------------------------------------

/// Parts joined on one line ("$0.42 est. · 12.4k tokens"); no letters, so
/// it needs no translation.
const teamUsageSeparator = ' · ';

/// "980", "12.4k", "1.2M": a token count in the chat's compact form.
String teamCompactCount(int count) {
  if (count >= 1000000) return '${(count / 1000000).toStringAsFixed(1)}M';
  if (count >= 1000) return '${(count / 1000).toStringAsFixed(1)}k';
  return '$count';
}

/// "$0.42": the app's session-row currency form (cents; three decimals
/// only when the amount would otherwise round to nothing).
String teamCurrencyLabel(double usd) => usd > 0 && usd < 0.01
    ? '\$${usd.toStringAsFixed(3)}'
    : '\$${usd.toStringAsFixed(2)}';

/// Input plus output tokens as far as the host reported either, or null.
int? teamUsageTokens(OrchestrationUsage? usage) {
  if (usage == null) return null;
  final input = usage.inputTokens, output = usage.outputTokens;
  if (input == null && output == null) return null;
  return (input ?? 0) + (output ?? 0);
}

/// "12.4k tokens", or null when the host reported no token count.
String? teamUsageTokensLabel(AppLocalizations l10n, OrchestrationUsage? usage) {
  final tokens = teamUsageTokens(usage);
  return tokens == null
      ? null
      : l10n.teamUiUsageTokens(teamCompactCount(tokens));
}

/// "$0.42 est.": the cost with the estimate suffix — always, since every
/// figure Gas City's `/usage` reports is its local estimate — or null when
/// the host reported no cost.
String? teamUsageCostLabel(AppLocalizations l10n, OrchestrationUsage? usage) {
  final cost = usage?.costUsd;
  return cost == null
      ? null
      : l10n.teamUiUsageCostEstimated(teamCurrencyLabel(cost));
}

/// "$0.42 est. · 12.4k tokens", whichever of the two the host reported,
/// or null when it reported neither: the surfaces that show it then stay
/// absent rather than show a placeholder.
String? teamUsageLabel(AppLocalizations l10n, OrchestrationUsage? usage) {
  final parts = [
    ?teamUsageCostLabel(l10n, usage),
    ?teamUsageTokensLabel(l10n, usage),
  ];
  return parts.isEmpty ? null : parts.join(teamUsageSeparator);
}

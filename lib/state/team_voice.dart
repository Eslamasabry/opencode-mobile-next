/// Role voices for an AI Team project task (docs/design/role-cycle-2026-11-01,
/// slice S1): who said each line of a task's conversation, and who is doing
/// what now.
///
/// Pure: no widgets and no words. The phone engine writes canned English
/// timeline lines ("Work is checking acceptance criteria."); this maps each
/// one to the role that speaks it and to a [TeamVoiceSays] code the UI turns
/// into the person's language. The manager speaks only facts the engine
/// recorded. A line this file does not know stays the manager's, with the
/// engine's own words.
library;

import '../domain/team_project.dart';

/// The roles that speak in a task conversation.
enum TeamVoice {
  /// Turns the idea into the spec (slice S2); no engine line yet.
  productOwner,

  /// The engine's planner: plans the approved spec.
  architect,

  /// The engine's own facts: queues, lanes, waits, merges.
  manager,

  /// A worker building the task.
  developer,

  /// The checker, checking the task against its criteria.
  reviewer,
}

/// What a voiced line says. Localise these; never show the enum name.
enum TeamVoiceSays {
  planning,
  planReady,
  starting,
  working,
  checking,
  foundProblems,
  merging,
  merged,
  queued,
  waitingForLane,
  waitingForTask,
  waitingForChat,
  waitingForCheck,
  paused,
  stopped,
  interrupted,
  resuming,

  /// Not a line this file knows: show the engine's words as they are.
  other,
}

/// One voiced line: who says what, and the engine's own text for [other].
class TeamVoiceLine {
  const TeamVoiceLine({
    required this.voice,
    required this.says,
    required this.planner,
    this.text = '',
  });

  final TeamVoice voice;
  final TeamVoiceSays says;

  /// True when the line is about the plan, not a task.
  final bool planner;

  /// The engine's line, kept for [TeamVoiceSays.other].
  final String text;
}

/// The engine's canned suffixes (engine/phone/src/store.rs
/// `append_job_timeline`) and what each one says. Longest first, so a
/// suffix that contains a shorter one wins.
const _suffixes = <(String, TeamVoiceSays)>[
  ('finished. The plan is ready to review.', TeamVoiceSays.planReady),
  (
    'was interrupted when the app stopped. Resume to check its existing '
        'session.',
    TeamVoiceSays.interrupted,
  ),
  (
    'was interrupted and needs review before resuming.',
    TeamVoiceSays.interrupted,
  ),
  ('is checking its existing session before resuming.', TeamVoiceSays.resuming),
  ('is paused. Resume to check its existing session.', TeamVoiceSays.paused),
  ('has checker findings that need review.', TeamVoiceSays.foundProblems),
  ('is checking acceptance criteria.', TeamVoiceSays.checking),
  ('is preparing the dev merge.', TeamVoiceSays.merging),
  ('is waiting for your chat reply to finish.', TeamVoiceSays.waitingForChat),
  ('is waiting for chat status to be checked.', TeamVoiceSays.waitingForChat),
  (
    'is waiting for protected execution to be verified.',
    TeamVoiceSays.waitingForCheck,
  ),
  (
    'is waiting for the phone server to be checked.',
    TeamVoiceSays.waitingForCheck,
  ),
  ('is waiting for an execution check.', TeamVoiceSays.waitingForCheck),
  ('is waiting for a free lane.', TeamVoiceSays.waitingForLane),
  ('is waiting for another task to finish.', TeamVoiceSays.waitingForTask),
  ('is starting.', TeamVoiceSays.starting),
  ('is running.', TeamVoiceSays.working),
  ('is queued.', TeamVoiceSays.queued),
  ('is paused.', TeamVoiceSays.paused),
  ('is stopped.', TeamVoiceSays.stopped),
  ('finished.', TeamVoiceSays.merged),
];

/// The voiced line for one engine timeline [event].
TeamVoiceLine teamVoiceOf(TeamTimelineEvent event) {
  final text = event.text.trim();
  final planner = event.kind == 'planner' || text.startsWith('Planning ');
  TeamVoiceSays says = TeamVoiceSays.other;
  for (final (suffix, code) in _suffixes) {
    if (text.endsWith(suffix)) {
      says = code;
      break;
    }
  }
  if (planner &&
      (says == TeamVoiceSays.working || says == TeamVoiceSays.starting)) {
    says = TeamVoiceSays.planning;
  }
  return TeamVoiceLine(
    voice: _voiceFor(says),
    says: says,
    planner: planner,
    text: text,
  );
}

TeamVoice _voiceFor(TeamVoiceSays says) => switch (says) {
  TeamVoiceSays.planning || TeamVoiceSays.planReady => TeamVoice.architect,
  TeamVoiceSays.starting || TeamVoiceSays.working => TeamVoice.developer,
  TeamVoiceSays.checking || TeamVoiceSays.foundProblems => TeamVoice.reviewer,
  _ => TeamVoice.manager,
};

/// The role a task's worker wears, for "Developer · Frontend": the task's
/// role name, or null for the plain worker role (and unknown roles).
String? teamDeveloperPersona(TeamTask task, List<TeamProjectRole> roles) {
  final role = roles.where((r) => r.id == task.roleId).firstOrNull;
  if (role == null) return null;
  if (const {'worker', 'planner', 'checker'}.contains(role.id)) return null;
  final name = role.name.trim();
  return name.isEmpty ? null : name;
}

/// Who is doing what now for [task]: its newest timeline line, or null when
/// the engine has written none for it yet.
///
/// The task's own status wins over an older line: a running task is the
/// developer's and a task in review is the reviewer's, even when the newest
/// line the engine kept says something else (a restarted task whose last
/// line is still "finished").
TeamVoiceLine? teamVoiceNow(TeamProject project, TeamTask task) {
  final lines = project.timeline.where((e) => e.taskId == task.id).toList();
  final newest = lines.isEmpty ? null : teamVoiceOf(lines.last);
  final live = switch (task.status) {
    'running' => TeamVoice.developer,
    'review' => TeamVoice.reviewer,
    _ => null,
  };
  if (live == null || newest?.voice == live) return newest;
  return TeamVoiceLine(
    voice: live,
    says: live == TeamVoice.developer
        ? TeamVoiceSays.working
        : TeamVoiceSays.checking,
    planner: false,
  );
}

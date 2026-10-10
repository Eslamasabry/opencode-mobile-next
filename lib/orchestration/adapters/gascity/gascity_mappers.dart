/// Pure functions from the Gas City DTOs to the product models.
///
/// Nothing here does I/O or throws on provider data: unknown strings land
/// on the `unknown` enum members with the raw string kept, and lists that
/// the host reports as `partial` come back as [GcMapped] with the flag and
/// the host's reasons instead of an exception.
///
/// The state tables (04-plugin-architecture §4) are documented on each
/// mapper: [mapBead] (work), [mapConvoy] and [mapRun] (runs), [mapAgent]
/// and [mapSession] (agents), [mapGates] (gates), [mapEvent] (events).
library;

import '../../../domain/orchestration_gateway.dart';
import '../../dispatch.dart' show isRefineryName, workHandedToMerge;
import 'dto/dto.dart';

/// A mapped list plus the host's partial-result flag. `partial` means one
/// or more backends failed and [items] may be incomplete; the UI shows the
/// items it has with a notice rather than an error.
class GcMapped<T> {
  const GcMapped(
    this.items, {
    this.partial = false,
    this.partialErrors = const [],
  });

  final List<T> items;
  final bool partial;

  /// Human-readable reasons from the host (`run projection is warming`).
  final List<String> partialErrors;

  bool get isEmpty => items.isEmpty;
  int get length => items.length;
}

// ---------------------------------------------------------------------------
// Work items
// ---------------------------------------------------------------------------

/// Cross-resource facts a bead's product state depends on but the bead
/// itself does not carry: the ready set, sessions parked in `/waits`,
/// sessions with a pending interaction, run-step errors and the convoy
/// tracking each bead. Build one per snapshot with [GcWorkContext.from].
class GcWorkContext {
  const GcWorkContext({
    this.readyIds = const {},
    this.waitingSessions = const {},
    this.needsInputSessions = const {},
    this.runErrors = const {},
    this.convoyByBead = const {},
  });

  /// Derives the context from the raw lists a snapshot fetch returns.
  factory GcWorkContext.from({
    Iterable<GcBead> ready = const [],
    Iterable<GcWait> waits = const [],
    Iterable<GcPendingInteraction> pending = const [],
    Iterable<GcConvoy> convoys = const [],
    Map<String, String> runErrors = const {},
  }) {
    final waiting = <String>{};
    for (final wait in waits) {
      if (!wait.isActive) continue;
      if (wait.sessionId != null) waiting.add(wait.sessionId!);
      if (wait.sessionName != null) waiting.add(wait.sessionName!);
    }
    final needsInput = <String>{
      for (final p in pending)
        if (p.sessionId != null) p.sessionId!,
    };
    final convoyByBead = <String, String>{};
    for (final convoy in convoys) {
      for (final id in convoy.trackedIds) {
        convoyByBead.putIfAbsent(id, () => convoy.id);
      }
    }
    return GcWorkContext(
      readyIds: {for (final b in ready) b.id},
      waitingSessions: waiting,
      needsInputSessions: needsInput,
      runErrors: runErrors,
      convoyByBead: convoyByBead,
    );
  }

  /// Bead ids the host lists under `GET /beads?ready=true`.
  final Set<String> readyIds;

  /// Session ids and names parked in `GET /waits`.
  final Set<String> waitingSessions;

  /// Session ids with an entry in `GET /pending`.
  final Set<String> needsInputSessions;

  /// Bead id → `last_error` text from a failed run step.
  final Map<String, String> runErrors;

  /// Bead id → convoy id tracking it.
  final Map<String, String> convoyByBead;

  bool _sessionIn(Set<String> set, GcBead bead) {
    if (set.isEmpty) return false;
    for (final key in [
      bead.sessionId,
      bead.sessionName,
      bead.meta('session_id'),
    ]) {
      if (key != null && set.contains(key)) return true;
    }
    return false;
  }
}

/// Bead → [WorkItem].
///
/// State table (04 §4, `WorkState.derive`), highest precedence first:
///
/// | Product state | Gas City source |
/// |---|---|
/// | Cancelled | `status=closed` and `closed_reason`/`close_reason` says `cancelled` |
/// | Completed | `status=closed` otherwise |
/// | Failed | non-empty `last_error` (bead metadata or run-step error for this bead) |
/// | Blocked | `is_blocked=true` |
/// | Needs input | the bead's session has a pending interaction |
/// | Waiting | the bead's session is parked in `/waits` |
/// | Review | label `needs-review`, or the `assignee` is a refinery (`…refinery`): handed to the merge agent (TEAM-117) |
/// | Working | `status=in_progress` |
/// | Ready | `status=open` and the bead is in the ready set |
/// | Queued | `status=open` otherwise |
/// | Unknown | any other `status` (raw kept in `rawState`) |
///
/// Other fields: `assignee` is the bead `assignee`, else `gc.routed_to`;
/// `sessionId` is `gc.session_id`; `parentId` is `parent`; `dependsOn`
/// lists the bead's dependency edges; `runId` is the tracking convoy from
/// [GcWorkContext.convoyByBead]. Branch, work dir and merge target stay in
/// `raw['metadata']` and read through [WorkItemGasCity].
WorkItem mapBead(GcBead bead, {GcWorkContext context = const GcWorkContext()}) {
  final lastError = bead.lastError ?? context.runErrors[bead.id];
  final state = WorkState.derive(
    status: bead.status,
    isBlocked: bead.isBlocked,
    isReady: context.readyIds.contains(bead.id),
    sessionWaiting: context._sessionIn(context.waitingSessions, bead),
    needsInput: context._sessionIn(context.needsInputSessions, bead),
    needsReview: bead.needsReview || _handedToRefinery(bead),
    lastError: lastError,
    closedReason: bead.closedReason,
  );
  return WorkItem(
    id: bead.id,
    title: bead.title.isEmpty ? bead.id : bead.title,
    state: state,
    rawState: bead.status,
    projectId: bead.meta('rig') ?? _rigOf(bead.routedTo ?? bead.assignee),
    runId: context.convoyByBead[bead.id],
    parentId: bead.parent,
    assignee: bead.assignee ?? bead.routedTo,
    sessionId: bead.sessionId,
    isBlocked: bead.isBlocked,
    labels: bead.labels,
    dependsOn: bead.dependsOn,
    closedReason: bead.closedReason,
    createdAt: bead.createdAt,
    updatedAt: bead.updatedAt,
    raw: bead.raw,
  );
}

/// True when the bead's own `assignee` (not the pool it was routed to)
/// is a rig's refinery: the polecat pushed and the merge agent owns it.
bool _handedToRefinery(GcBead bead) {
  final assignee = bead.assignee;
  return assignee != null && isRefineryName(assignee);
}

/// Every bead through [mapBead]. With [includeInternal] false (default)
/// the host's own bookkeeping beads are dropped: session beads, convoys,
/// mail messages, molecules and nudge chores.
List<WorkItem> mapBeads(
  Iterable<GcBead> beads, {
  GcWorkContext context = const GcWorkContext(),
  bool includeInternal = false,
}) => [
  for (final bead in beads)
    if (includeInternal || !isInternalBead(bead))
      mapBead(bead, context: context),
];

/// A [GcList] of beads through [mapBeads], keeping the partial flag.
GcMapped<WorkItem> mapBeadList(
  GcList<GcBead> list, {
  GcWorkContext context = const GcWorkContext(),
  bool includeInternal = false,
}) => GcMapped(
  mapBeads(list.items, context: context, includeInternal: includeInternal),
  partial: list.partial,
  partialErrors: list.partialErrors,
);

/// True for beads Gas City creates for itself (sessions, convoys, mail,
/// molecules, nudges) rather than work a person slung.
bool isInternalBead(GcBead bead) {
  switch (bead.issueType) {
    case 'session':
    case 'convoy':
    case 'message':
    case 'molecule':
      return true;
  }
  return bead.hasLabel('gc:session') || bead.hasLabel('gc:nudge');
}

/// Gas City-specific work fields the product model keeps in `raw`.
extension WorkItemGasCity on WorkItem {
  Map<String, Object?> get _metadata => readMapField(raw, 'metadata');

  String? _meta(String key) => readText(_metadata, key);

  /// `metadata.branch`: the polecat branch carrying the change.
  String? get branch => _meta('branch');

  /// `metadata.target`: the merge target branch.
  String? get target => _meta('target');
  String? get mergeStrategy => _meta('merge_strategy');

  /// `metadata.gc.work_dir`, else `metadata.work_dir`.
  String? get workDir => _meta('gc.work_dir') ?? _meta('work_dir');

  /// `metadata.gc.session_name`.
  String? get sessionName => _meta('gc.session_name');

  /// `metadata.gc.routed_to`: the pool or agent the bead was slung at.
  String? get routedTo => _meta('gc.routed_to');
  String? get issueType => readText(raw, 'issue_type');

  /// `metadata.merge_result`: what the refinery did with the branch
  /// (`merged` once it landed on [target]).
  String? get mergeResult => _meta('merge_result');

  /// `metadata.merged_sha`: the commit the merge produced.
  String? get mergedSha => _meta('merged_sha');
}

// ---------------------------------------------------------------------------
// Runs
// ---------------------------------------------------------------------------

/// Convoy → [OrchestrationRun] of kind [RunKind.batch].
///
/// State table, derived from the tracked beads ([work] by id; embedded
/// `children` are used when the id is not in [work]):
///
/// | Product state | Gas City source |
/// |---|---|
/// | Cancelled | convoy `status=closed` with a cancelled close reason, or every tracked item cancelled |
/// | Completed | convoy `status=closed`, or every tracked item completed |
/// | Failed | any tracked item failed |
/// | Blocked | any tracked item blocked or needing input |
/// | Working | any tracked item working, in review (`needs-review`), parked, or open with an `assignee`/`gc.session_id` (an agent has it) |
/// | Waiting | nothing started: every tracked item queued/ready with no agent (waiting for an agent), or no tracked item at all; or (TEAM-117) every open item is handed to the refinery and waits for the merge |
///
/// `planning` is never a batch state; it is reserved for formula runs the
/// host reports `pending`.
///
/// The title is the work's: a convoy titled `sling-<bead>` (or untitled)
/// takes its single tracked item's title, or `<first title> + N more`
/// with several; the raw convoy title stays in `raw['title']`.
/// `stepCount` is the number of tracked beads, `completedSteps` how many
/// are closed (`progress.closed`/`progress.total` when the host sends them).
///
/// A closed convoy is a finished run: `finishedAt` is [closedAt] (the
/// `convoy.closed` event time, see [convoyClosedTimes]), else the bead's
/// `updated_at`; an open convoy has none. `merged` is true when the run
/// is completed and every tracked item reports `merge_result=merged`.
OrchestrationRun mapConvoy(
  GcConvoy convoy, {
  Map<String, WorkItem> work = const {},
  GcWorkContext context = const GcWorkContext(),
  DateTime? closedAt,
}) {
  final bead = convoy.bead;
  final tracked = <WorkItem>[];
  final children = {for (final c in convoy.children) c.id: c};
  for (final id in convoy.trackedIds) {
    final item = work[id];
    if (item != null) {
      tracked.add(item);
    } else if (children.containsKey(id)) {
      tracked.add(mapBead(children[id]!, context: context));
    }
  }
  if (tracked.isEmpty) {
    for (final child in convoy.children) {
      tracked.add(mapBead(child, context: context));
    }
  }

  final completed = tracked.where((w) => w.state == WorkState.completed).length;
  final RunState state;
  if (bead.isClosed) {
    state = _isCancelledReason(bead.closedReason)
        ? RunState.cancelled
        : RunState.completed;
  } else {
    state = _runStateFromWork(tracked);
  }
  final failure = tracked.where((w) => w.state == WorkState.failed).firstOrNull;
  final finishedAt = bead.isClosed ? closedAt ?? bead.updatedAt : null;
  final merged =
      state == RunState.completed &&
      tracked.isNotEmpty &&
      tracked.every((w) => w.mergeResult == 'merged');
  return OrchestrationRun(
    id: bead.id,
    title: batchTitle(bead.title, tracked) ?? bead.id,
    state: state,
    rawState: bead.status,
    kind: RunKind.batch,
    projectId: bead.meta('rig'),
    stepCount: convoy.progressTotal ?? tracked.length,
    completedSteps: convoy.progressClosed ?? completed,
    lastError: failure == null ? null : _workError(failure),
    startedAt: bead.createdAt,
    updatedAt: bead.updatedAt ?? finishedAt,
    finishedAt: finishedAt,
    merged: merged,
    raw: bead.raw,
  );
}

/// Every convoy through [mapConvoy]; [work] is looked up by bead id and
/// [closedAt] (convoy id → close time) dates the finished ones.
List<OrchestrationRun> mapConvoys(
  Iterable<GcConvoy> convoys, {
  Iterable<WorkItem> work = const [],
  GcWorkContext context = const GcWorkContext(),
  Map<String, DateTime> closedAt = const {},
}) {
  final byId = {for (final w in work) w.id: w};
  return [
    for (final c in convoys)
      mapConvoy(c, work: byId, context: context, closedAt: closedAt[c.id]),
  ];
}

/// Convoy id → when it closed, from `GET /events?type=convoy.closed`
/// (the event `subject` is the convoy id, `ts` the time). The newest
/// event wins when a convoy closed more than once.
Map<String, DateTime> convoyClosedTimes(Iterable<GcEvent> events) {
  final times = <String, DateTime>{};
  for (final event in events) {
    final id = event.subject;
    final ts = event.ts;
    if (event.type != 'convoy.closed' || id == null || ts == null) continue;
    final seen = times[id];
    if (seen == null || ts.isAfter(seen)) times[id] = ts;
  }
  return times;
}

/// The finished convoys worth showing, newest first: closed ones (from
/// `GET /beads?status=closed&type=convoy`) that are not also open in
/// [openIds] (a reopened convoy is shown open), that finished within
/// [window] of [now] — by [closedAt], else `updated_at`, else
/// `created_at` — at most [limit] of them. Gas City's `/convoys` lists
/// open convoys only, so this is what keeps a run visible once it is done.
List<GcBead> selectFinishedConvoys(
  Iterable<GcBead> closed, {
  required DateTime now,
  required Duration window,
  required int limit,
  Map<String, DateTime> closedAt = const {},
  Set<String> openIds = const {},
}) {
  final since = now.subtract(window);
  DateTime? when(GcBead bead) =>
      closedAt[bead.id] ?? bead.updatedAt ?? bead.createdAt;
  final picked = [
    for (final bead in closed)
      if (bead.isConvoy &&
          bead.isClosed &&
          bead.id.isNotEmpty &&
          !openIds.contains(bead.id) &&
          !(when(bead)?.isBefore(since) ?? true))
        bead,
  ];
  picked.sort((a, b) => when(b)!.compareTo(when(a)!));
  return picked.take(limit < 0 ? 0 : limit).toList();
}

/// The product title of a batch: the tracked work's title when the convoy
/// is untitled or carries the host's automatic `sling-<bead>` name, else
/// the convoy's own title. Null when neither exists.
String? batchTitle(String convoyTitle, List<WorkItem> tracked) {
  final own = convoyTitle.trim();
  final auto = own.isEmpty || _autoConvoyTitle.hasMatch(own);
  if (!auto) return own;
  final titles = [
    for (final item in tracked)
      if (item.title.trim().isNotEmpty) item.title.trim(),
  ];
  if (titles.isEmpty) return own.isEmpty ? null : own;
  if (titles.length == 1) return titles.single;
  return '${titles.first} + ${titles.length - 1} more';
}

final _autoConvoyTitle = RegExp(r'^sling-[A-Za-z0-9_.-]+$');

/// Whether an agent has picked the item up: the bead names an `assignee`
/// (not just the pool it was routed to) or carries a live `gc.session_id`.
bool _workStarted(WorkItem item) {
  final assignee = readText(item.raw, 'assignee');
  return (assignee != null && assignee.isNotEmpty) ||
      (item.sessionId != null && item.sessionId!.isNotEmpty);
}

RunState _runStateFromWork(List<WorkItem> tracked) {
  if (tracked.isEmpty) return RunState.waiting;
  var anyBlocked = false, anyWorking = false;
  var allCompleted = true, allCancelled = true, allDone = true;
  for (final w in tracked) {
    // Handed to the refinery (TEAM-117): the merge is what is awaited,
    // no agent works it. It neither counts as working nor as done.
    if (workHandedToMerge(w)) {
      allCompleted = allCancelled = allDone = false;
      continue;
    }
    switch (w.state) {
      case WorkState.failed:
        return RunState.failed;
      case WorkState.blocked:
      case WorkState.needsInput:
        anyBlocked = true;
      case WorkState.working:
      case WorkState.review:
      case WorkState.waiting:
        anyWorking = true;
      case WorkState.queued:
      case WorkState.ready:
      case WorkState.unknown:
        if (_workStarted(w)) anyWorking = true;
      case WorkState.completed:
      case WorkState.cancelled:
        break;
    }
    if (w.state != WorkState.completed) allCompleted = false;
    if (w.state != WorkState.cancelled) allCancelled = false;
    if (w.state != WorkState.completed && w.state != WorkState.cancelled) {
      allDone = false;
    }
  }
  if (anyBlocked) return RunState.blocked;
  if (anyWorking) return RunState.working;
  if (allCompleted) return RunState.completed;
  if (allCancelled) return RunState.cancelled;
  if (allDone) return RunState.completed;
  return RunState.waiting;
}

/// Formula run status → [RunState].
///
/// | Gas City `status` | Product state |
/// |---|---|
/// | `pending` | Planning |
/// | `active` | Working |
/// | `waiting` | Waiting |
/// | `failed` | Failed |
/// | `completed` | Completed |
/// | `canceling`, `canceled`, `skipped` | Cancelled |
/// | anything else | `RunState.fromProvider`, else Unknown |
RunState runStateFromGc(String? status) {
  switch (status?.trim().toLowerCase()) {
    case 'canceling':
    case 'cancelling':
    case 'skipped':
      return RunState.cancelled;
    default:
      return RunState.fromProvider(status);
  }
}

/// Formula run → [OrchestrationRun] of kind [RunKind.formula]; state via
/// [runStateFromGc]. A run with an unknown status but a `last_error` is
/// reported as Failed. `projectId` is `scope.ref` when the scope is a rig.
/// `isUpkeep` is [isUpkeepRun].
OrchestrationRun mapRun(GcRun run) {
  var state = runStateFromGc(run.status);
  final error = run.lastError?.text;
  if (state == RunState.unknown && error != null) state = RunState.failed;
  return OrchestrationRun(
    id: run.runId,
    title: run.title.isEmpty ? (run.formula ?? run.runId) : run.title,
    state: state,
    rawState: run.status,
    kind: RunKind.formula,
    projectId: run.scopeKind == 'rig' ? run.scopeRef : null,
    formula: run.formula,
    lastError: error,
    startedAt: run.startedAt,
    updatedAt: run.updatedAt,
    isUpkeep: isUpkeepRun(run),
    raw: run.raw,
  );
}

/// True for the host's own housekeeping runs, which the product hides by
/// default: a title or formula matching the pack's patrol and chore
/// formulas (`mol-*-patrol`, `mol-shutdown-dance`, `mol-digest-generate`),
/// an `order:`/`nudge:` chore, or a `target=workflow` wisp with no scope
/// and no tracked work (the shape every pack patrol has on `/runs`).
bool isUpkeepRun(GcRun run) {
  for (final name in [run.title, run.formula]) {
    if (name == null) continue;
    final n = name.trim().toLowerCase();
    if (n.isEmpty) continue;
    if (_upkeepFormula.hasMatch(n)) return true;
    if (n.startsWith('order:') || n.startsWith('nudge:')) return true;
  }
  if (run.target?.trim().toLowerCase() == 'workflow') {
    final scoped =
        (run.scopeKind != null && run.scopeKind!.isNotEmpty) ||
        (run.scopeRef != null && run.scopeRef!.isNotEmpty);
    if (!scoped && !_runTracksWork(run.raw)) return true;
  }
  return false;
}

final _upkeepFormula = RegExp(
  r'^mol-([a-z0-9_.-]+-)?patrol$|^mol-shutdown-dance$|^mol-digest-generate$',
);

/// Whether a run payload names work it tracks (`bead`, `bead_id`,
/// `work_id`, `tracks`, `beads`); a scope-less workflow wisp names none.
bool _runTracksWork(Map<String, Object?> raw) {
  for (final key in const ['bead', 'bead_id', 'work_id', 'tracks', 'beads']) {
    final value = raw[key];
    if (value == null) continue;
    if (value is String && value.trim().isNotEmpty) return true;
    if (value is Iterable && value.isNotEmpty) return true;
    if (value is Map && value.isNotEmpty) return true;
  }
  return false;
}

/// `GET /runs` → runs plus the host's partial flag. A warming projection
/// (`partial: true`, no runs) yields an empty list with `partial` set, not
/// an error.
GcMapped<OrchestrationRun> mapRunsList(GcRunsList list) => GcMapped(
  [for (final run in list.runs) mapRun(run)],
  partial: list.partial,
  partialErrors: list.partialErrors,
);

// ---------------------------------------------------------------------------
// Agents
// ---------------------------------------------------------------------------

/// Cross-resource facts for agent states: sessions with a pending
/// interaction and sessions parked in `/waits`.
class GcAgentContext {
  const GcAgentContext({
    this.needsInputSessions = const {},
    this.waitingSessions = const {},
  });

  factory GcAgentContext.from({
    Iterable<GcPendingInteraction> pending = const [],
    Iterable<GcWait> waits = const [],
  }) {
    final work = GcWorkContext.from(pending: pending, waits: waits);
    return GcAgentContext(
      needsInputSessions: work.needsInputSessions,
      waitingSessions: work.waitingSessions,
    );
  }

  /// Session ids with an entry in `GET /pending`.
  final Set<String> needsInputSessions;

  /// Session ids and names parked in `GET /waits`.
  final Set<String> waitingSessions;

  bool _matches(Set<String> set, Iterable<String?> keys) {
    if (set.isEmpty) return false;
    for (final key in keys) {
      if (key != null && set.contains(key)) return true;
    }
    return false;
  }
}

/// Agent (+ its session when known) → [OrchestrationAgent].
///
/// State table, highest precedence first:
///
/// | Product state | Gas City source |
/// |---|---|
/// | Crashed | agent `state` or session `state` in `crashed`/`error`/`failed`, or agent `unavailable_reason` set with `available=false` |
/// | Stopped | agent `suspended=true`, `state=stopped`/`suspended`, or `running=false` without a running session |
/// | Waiting | the session has a pending interaction (needs the person) |
/// | Blocked | the session is parked in `/waits` |
/// | Idle | agent `state=idle` |
/// | Working | agent `state` in `active`/`running`/`working`/`busy`, or `running=true` with a session in `active` |
/// | Unknown | any other `state` (raw kept in `rawState`) |
///
/// Other fields: `sessionName` is `session.name`, `lastActivity` is
/// `session.last_activity` (else the session's `last_active`), `pool` is
/// the pool template, `currentWorkId` is `active_bead`.
OrchestrationAgent mapAgent(
  GcAgent agent, {
  GcSession? session,
  GcAgentContext context = const GcAgentContext(),
}) {
  final rawState = agent.state ?? session?.state;
  final sessionKeys = [
    session?.id,
    session?.sessionName,
    agent.session?.name,
    agent.name,
  ];
  final state = _agentState(
    agentState: agent.state,
    sessionState: session?.state,
    running: agent.running || (session?.running ?? false),
    suspended: agent.suspended,
    unavailable: !agent.available && agent.unavailableReason != null,
    needsInput: context._matches(context.needsInputSessions, sessionKeys),
    waiting: context._matches(context.waitingSessions, sessionKeys),
  );
  return OrchestrationAgent(
    id: agent.name,
    name: agent.name,
    state: state,
    rawState: rawState,
    sessionId: session?.id,
    sessionName: agent.session?.name ?? session?.sessionName,
    pool:
        agent.pool ??
        (session?.isPoolInstance == true ? session?.template : null),
    pack: agent.pack,
    provider: agent.provider ?? session?.provider,
    currentWorkId: agent.activeBead ?? session?.activeBead,
    lastActivity: agent.session?.lastActivity ?? session?.lastActive,
    harness: agent.displayName ?? session?.displayName,
    model: agent.model ?? session?.model,
    contextPercent: _percent(agent.contextPct ?? session?.contextPct),
    workDir: session?.workDir,
    branch: _branchOf(session?.metadata),
    sessionStartedAt: session?.createdAt,
    suspended: isSuspendedAgent(agent),
    unavailableReason: agent.available ? null : agent.unavailableReason,
    raw: agent.raw,
    sessionState: session?.state,
    sessionRunning: session?.running,
  );
}

/// Whether the host reports the agent switched off: `suspended=true` or
/// `state=suspended`.
bool isSuspendedAgent(GcAgent agent) =>
    agent.suspended || agent.state?.trim().toLowerCase() == 'suspended';

/// Whether an `/agents` entry is an agent at all, as the product sees it.
/// False for the host's empty slots and idle helpers:
///
/// * a pool member (`pool` set) with no session that is stopped and not
///   running: an unspawned slot, not an agent;
/// * the `core` and `bd` pack helpers (`control-dispatcher`, `bd.dog-*`)
///   unless they are running.
///
/// Suspended named agents stay (they are shown, collapsed, as switched
/// off), as does anything running, crashed or with a session.
bool isAgentEntry(GcAgent agent, {GcSession? session}) {
  final state = agent.state?.trim().toLowerCase();
  final hasSession = agent.session != null || session != null;
  final running = agent.running || (session?.running ?? false);
  final helperPack = agent.pack == 'core' || agent.pack == 'bd';
  final helperName =
      agent.name.contains('control-dispatcher') ||
      _dogSlot.hasMatch(agent.name);
  if ((helperPack || helperName) && !running) return false;
  if (agent.pool != null && !hasSession && !running) {
    if (state == null || state == 'stopped' || state == 'suspended') {
      return false;
    }
  }
  return true;
}

final _dogSlot = RegExp(r'(^|[./])bd\.dog-\d+$');

/// A reported context percentage clamped to 0–100; null stays null.
int? _percent(int? value) => value?.clamp(0, 100);

/// The branch a session's metadata names (`branch`, `gc.branch`), if any.
String? _branchOf(Map<String, String>? metadata) {
  if (metadata == null) return null;
  for (final key in const ['branch', 'gc.branch', 'worktree_branch']) {
    final value = metadata[key]?.trim();
    if (value != null && value.isNotEmpty) return value;
  }
  return null;
}

/// A session without an agent entry (pool instances such as polecats) →
/// [OrchestrationAgent]. Same table as [mapAgent] with the session's own
/// `state` and `running` flag; the id is the session id and the name its
/// alias (else title, else template).
OrchestrationAgent mapSession(
  GcSession session, {
  GcAgentContext context = const GcAgentContext(),
}) {
  final keys = [session.id, session.sessionName, session.alias];
  final state = _agentState(
    agentState: null,
    sessionState: session.state,
    running: session.running,
    suspended: false,
    unavailable: false,
    needsInput: context._matches(context.needsInputSessions, keys),
    waiting: context._matches(context.waitingSessions, keys),
  );
  return OrchestrationAgent(
    id: session.id,
    name: session.alias ?? session.title ?? session.template ?? session.id,
    state: state,
    rawState: session.state,
    sessionId: session.id,
    sessionName: session.sessionName,
    pool: session.isPoolInstance ? session.template : null,
    provider: session.provider,
    currentWorkId: session.activeBead,
    lastActivity: session.lastActive,
    harness: session.displayName,
    model: session.model,
    contextPercent: _percent(session.contextPct),
    workDir: session.workDir,
    branch: _branchOf(session.metadata),
    sessionStartedAt: session.createdAt,
    raw: session.raw,
    sessionState: session.state,
    sessionRunning: session.running,
  );
}

/// Joins `/agents` with `/sessions` (by `session.name` == `session_name`,
/// else by agent name == session `alias`/`template`) and maps every agent
/// that [isAgentEntry] (every entry with [includeSlots]); sessions no
/// agent claimed are mapped with [mapSession] so pool instances appear
/// too.
List<OrchestrationAgent> mapAgents(
  Iterable<GcAgent> agents,
  Iterable<GcSession> sessions, {
  GcAgentContext context = const GcAgentContext(),
  bool includeSlots = false,
}) {
  final byName = <String, GcSession>{};
  final byAlias = <String, GcSession>{};
  for (final s in sessions) {
    if (s.sessionName != null) byName.putIfAbsent(s.sessionName!, () => s);
    if (s.alias != null) byAlias.putIfAbsent(s.alias!, () => s);
  }
  final claimed = <String>{};
  final out = <OrchestrationAgent>[];
  for (final agent in agents) {
    GcSession? session;
    final name = agent.session?.name;
    if (name != null) session = byName[name];
    session ??= byAlias[agent.name];
    if (session != null) claimed.add(session.id);
    if (!includeSlots && !isAgentEntry(agent, session: session)) continue;
    out.add(mapAgent(agent, session: session, context: context));
  }
  for (final s in sessions) {
    if (claimed.contains(s.id)) continue;
    out.add(mapSession(s, context: context));
  }
  return out;
}

AgentState _agentState({
  required String? agentState,
  required String? sessionState,
  required bool running,
  required bool suspended,
  required bool unavailable,
  required bool needsInput,
  required bool waiting,
}) {
  final a = AgentState.fromProvider(agentState);
  final s = AgentState.fromProvider(sessionState);
  if (a == AgentState.crashed || s == AgentState.crashed || unavailable) {
    return AgentState.crashed;
  }
  if (suspended || s == AgentState.stopped) return AgentState.stopped;
  // The agent list can say "stopped" while the session list shows the
  // same agent's session active and running: Gas City derives the agent
  // state through its tmux backend, which a phone (no tmux server) never
  // has. A running session is the fact; the agent word yields to it.
  if (a == AgentState.stopped && !(running && s == AgentState.working)) {
    return AgentState.stopped;
  }
  if (!running && agentState != null && a != AgentState.working) {
    return AgentState.stopped;
  }
  if (!running && agentState == null && sessionState == null) {
    return AgentState.stopped;
  }
  if (needsInput) return AgentState.waiting;
  if (waiting) return AgentState.blocked;
  if (a == AgentState.idle) return AgentState.idle;
  if (a == AgentState.working) return AgentState.working;
  if (a == AgentState.waiting || a == AgentState.blocked) return a;
  if (s == AgentState.working && running) return AgentState.working;
  if (s != AgentState.unknown) return s;
  if (a != AgentState.unknown) return a;
  // No state string at all: the running flag is all there is.
  if (agentState == null && sessionState == null && running) {
    return AgentState.working;
  }
  return AgentState.unknown;
}

// ---------------------------------------------------------------------------
// Gates
// ---------------------------------------------------------------------------

/// Pending interaction → [OrchestrationGate].
///
/// | Product kind | Gas City `kind` |
/// |---|---|
/// | Choice | `choice`, `select`, `option` |
/// | Confirmation | `confirm`, `confirmation`, `yes_no`, `approval` |
/// | Free text | `text`, `free_text`, `input` |
/// | Unknown | anything else (raw kept in `rawKind`) |
///
/// The gate id is the `request_id`; `agentId` is the `session_id`.
OrchestrationGate mapPendingInteraction(GcPendingInteraction p) {
  final kind = GateKind.fromProvider(p.kind);
  return OrchestrationGate(
    id: p.requestId,
    kind: kind,
    rawKind: p.kind,
    title: p.prompt ?? _gateTitle(kind, p.kind),
    prompt: p.prompt,
    agentId: p.sessionId,
    choices: p.options,
    createdAt: p.createdAt,
    raw: p.raw,
  );
}

/// Bead → gate, or null when the bead needs nothing from the person.
///
/// | Product kind | Gas City source |
/// |---|---|
/// | Gate bead | open bead with `issue_type=gate`, `gc.kind=gate` or a `gate`/`gc:gate` label |
/// | Review ready | open bead with the `needs-review` label |
OrchestrationGate? gateFromBead(GcBead bead) {
  if (bead.isClosed) return null;
  if (bead.isGate) {
    return OrchestrationGate(
      id: 'bead:${bead.id}',
      kind: GateKind.gateBead,
      rawKind: bead.issueType,
      title: bead.title.isEmpty ? bead.id : bead.title,
      prompt: bead.description,
      workId: bead.id,
      agentId: bead.assignee ?? bead.routedTo,
      createdAt: bead.createdAt,
      raw: bead.raw,
    );
  }
  if (bead.needsReview) {
    return OrchestrationGate(
      id: 'review:${bead.id}',
      kind: GateKind.reviewReady,
      rawKind: 'needs-review',
      title: bead.title.isEmpty ? bead.id : bead.title,
      prompt: bead.description,
      workId: bead.id,
      agentId: bead.assignee ?? bead.routedTo,
      createdAt: bead.updatedAt ?? bead.createdAt,
      raw: bead.raw,
    );
  }
  return null;
}

/// Run → gate of kind [GateKind.runFailed] when the run failed, else null.
/// A failed upkeep run is the host's own business and raises no gate.
OrchestrationGate? gateFromRun(OrchestrationRun run) {
  if (run.state != RunState.failed || run.isUpkeep) return null;
  return OrchestrationGate(
    id: 'run:${run.id}',
    kind: GateKind.runFailed,
    rawKind: run.rawState,
    title: run.title,
    prompt: run.lastError,
    runId: run.id,
    createdAt: run.updatedAt ?? run.startedAt,
    raw: run.raw,
  );
}

/// Every gate in a snapshot: pending interactions, gate and review beads,
/// failed runs. Order: interactions, beads, runs.
List<OrchestrationGate> mapGates({
  Iterable<GcPendingInteraction> pending = const [],
  Iterable<GcBead> beads = const [],
  Iterable<OrchestrationRun> runs = const [],
}) => [
  for (final p in pending) mapPendingInteraction(p),
  for (final b in beads) ?gateFromBead(b),
  for (final r in runs) ?gateFromRun(r),
];

String _gateTitle(GateKind kind, String? raw) => switch (kind) {
  GateKind.choice => 'Choose an option',
  GateKind.confirmation => 'Confirm to continue',
  GateKind.freeText => 'Input needed',
  GateKind.gateBead => 'Gate',
  GateKind.runFailed => 'Run failed',
  GateKind.reviewReady => 'Ready for review',
  GateKind.unknown => raw ?? 'Needs attention',
};

// ---------------------------------------------------------------------------
// Usage, status, host
// ---------------------------------------------------------------------------

/// `GET /usage` (+ `/status` counts when given) → [OrchestrationUsage].
/// Tokens and cost come from `today`; every figure is the host's local
/// estimate, which [OrchestrationUsageGasCity.isEstimated] reports.
OrchestrationUsage mapUsage(GcUsage usage, {GcStatus? status}) {
  final available = usage.available && usage.source != 'unavailable';
  final today = available ? _mapUsageTotals(usage.today) : null;
  final windowSecs = usage.recentWindowSecs;
  final counts = status == null ? null : mapStatusCounts(status);
  return OrchestrationUsage(
    capturedAt: usage.updatedAt,
    activeAgents: counts?.activeAgents,
    runsInProgress: counts?.runsInProgress,
    workOpen: counts?.workOpen,
    workReady: counts?.workReady,
    workInProgress: counts?.workInProgress,
    inputTokens: today?.inputTokens,
    outputTokens: today?.outputTokens,
    costUsd: today?.costUsdEstimate,
    evidence: OrchestrationUsageEvidence(
      available: available,
      recording: usage.recording,
      // Gas City has no billing source. Unknown future source values must
      // not promote these estimates to authoritative charges.
      isEstimated: true,
      partial: usage.partial,
      partialReasons: List.unmodifiable(usage.partialReasons),
      observedFrom: usage.observedFrom,
      updatedAt: usage.updatedAt,
      today: today,
      recent: available ? _mapUsageTotals(usage.recent) : null,
      recentWindow: windowSecs != null && windowSecs > 0
          ? Duration(seconds: windowSecs)
          : null,
      recentBySession: available && usage.recentBySession != null
          ? List.unmodifiable([
              for (final session in usage.recentBySession!)
                if (session.session.isNotEmpty)
                  OrchestrationSessionUsage(
                    workerName: session.session,
                    sessionId: session.sessionId,
                    totals: _mapUsageTotals(session.totals)!,
                  ),
            ])
          : null,
    ),
    raw: usage.raw,
  );
}

OrchestrationUsageTotals? _mapUsageTotals(GcUsageTotals? totals) {
  if (totals == null) return null;
  int? nonnegative(int? value) => value != null && value >= 0 ? value : null;
  final cost = totals.costUsdEstimate;
  return OrchestrationUsageTotals(
    inputTokens: nonnegative(totals.inputTokens),
    outputTokens: nonnegative(totals.outputTokens),
    cacheReadTokens: nonnegative(totals.cacheReadTokens),
    cacheCreationTokens: nonnegative(totals.cacheCreationTokens),
    costUsdEstimate: cost != null && cost.isFinite && cost >= 0 ? cost : null,
    unpriced: nonnegative(totals.unpriced),
  );
}

/// `GET /status` counts → [OrchestrationUsage]: `agents.running`,
/// `work.{open,ready,in_progress}`. Tokens stay unset.
OrchestrationUsage mapStatusCounts(GcStatus status) => OrchestrationUsage(
  activeAgents: status.agents.running ?? status.running,
  runsInProgress: status.work.inProgress,
  workOpen: status.work.open,
  workReady: status.work.ready,
  workInProgress: status.work.inProgress,
  raw: status.raw,
);

/// Whether a usage figure is an estimate. Gas City's `/usage` reports
/// `source: local_estimate`; anything but an explicit billing source is
/// treated as estimated so the UI labels it.
extension OrchestrationUsageGasCity on OrchestrationUsage {
  bool get isEstimated {
    final reported = evidence?.isEstimated;
    if (reported != null) return reported;
    final source = readText(raw, 'source');
    return source == null ||
        source == 'local_estimate' ||
        source == 'unavailable';
  }

  /// `source` as reported (`local_estimate`, `unavailable`), or null.
  String? get source => readText(raw, 'source');
}

/// `/health` and `/status` → [OrchestrationHostIdentity]. `version` and
/// `city` prefer `/health`, falling back to `/status` `version`/`name`.
OrchestrationHostIdentity mapHostIdentity({
  required String url,
  required OrchestrationHostMode hostMode,
  GcHealth? health,
  GcStatus? status,
  String provider = 'gascity',
}) => OrchestrationHostIdentity(
  provider: provider,
  url: url,
  hostMode: hostMode,
  version: health?.version ?? status?.version,
  city: health?.city ?? status?.name,
);

/// `/status` `rig_details[]` → [OrchestrationProject]s (one per rig).
List<OrchestrationProject> mapRigs(GcStatus status) => [
  for (final rig in status.rigDetails)
    if (rig.name.isNotEmpty)
      OrchestrationProject(
        id: rig.name,
        name: rig.name,
        directory: rig.path,
        rig: rig.name,
        raw: rig.raw,
      ),
];

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

/// Event families the app knows are Gas City activity (from the spec's
/// `TypedEventStreamEnvelope*` list). Anything else is
/// [UnknownOrchestrationEvent].
const _activityFamilies = {
  'bead',
  'beads',
  'session',
  'convoy',
  'order',
  'mail',
  'controller',
  'project',
  'request',
  'supervisor',
  'city',
  'rig',
  'gc',
  'molecule',
  'provider',
  'webhook',
  'emergency',
  'events',
  'worker',
  'extmsg',
  'custom',
  'pg',
  'run',
  'workflow',
};

/// City event → [OrchestrationEvent].
///
/// | Gas City `type` | Product event |
/// |---|---|
/// | `bead.created` | [BeadChanged] (created); bead id from `subject`, else `payload.bead.id` |
/// | `bead.updated` | [BeadChanged] (updated) |
/// | `bead.closed`, `bead.deleted` | [BeadChanged] (closed) |
/// | `session.woke` | [SessionChanged] (woke); session id from `session_id`, else `payload.session_id`; `agentId` is `subject` |
/// | `session.stopped`, `session.crashed`, `session.*_killed`, `session.quarantined`, `session.suspended` | [SessionChanged] (stopped) |
/// | `convoy.created` | [RunChanged] (planning) |
/// | `convoy.closed` | [RunChanged] (completed) |
/// | `run.*` with a run id | [RunChanged] with `payload.status` through [runStateFromGc] |
/// | `request.result.*` with a `payload.request_id` | [RequestResult] (ok) with the type's tail as `operation` |
/// | `request.failed` with a `payload.request_id` | [RequestResult] (failed) with `payload.operation`, `error_code`, `error_message` |
/// | `order.*`, `mail.*`, `controller.*`, `project.*`, other `request.*`, other `bead.*`/`session.*` and the remaining spec families | [ActivityAppended] |
/// | `heartbeat` | [StreamHeartbeat] |
/// | anything else | [UnknownOrchestrationEvent] (type and raw kept) |
OrchestrationEvent mapEvent(GcEvent event) {
  final type = event.type;
  final seq = event.seq;
  final raw = event.raw;
  if (type == 'heartbeat') {
    return StreamHeartbeat(timestamp: event.ts, seq: seq, raw: raw);
  }
  final payloadBead = event.payloadBead;
  final beadId =
      event.subject ??
      (payloadBead == null ? null : readText(payloadBead, 'id'));
  switch (type) {
    case 'bead.created':
      if (beadId != null) {
        return BeadChanged(
          beadId: beadId,
          change: BeadChange.created,
          seq: seq,
          raw: raw,
        );
      }
    case 'bead.updated':
      if (beadId != null) {
        return BeadChanged(
          beadId: beadId,
          change: BeadChange.updated,
          seq: seq,
          raw: raw,
        );
      }
    case 'bead.closed':
    case 'bead.deleted':
      if (beadId != null) {
        return BeadChanged(
          beadId: beadId,
          change: BeadChange.closed,
          seq: seq,
          raw: raw,
        );
      }
    case 'session.woke':
      final id = _eventSessionId(event);
      if (id != null) {
        return SessionChanged(
          sessionId: id,
          change: SessionChange.woke,
          agentId: event.subject,
          seq: seq,
          raw: raw,
        );
      }
    case 'session.stopped':
    case 'session.crashed':
    case 'session.idle_killed':
    case 'session.max_age_killed':
    case 'session.quarantined':
    case 'session.suspended':
      final id = _eventSessionId(event);
      if (id != null) {
        return SessionChanged(
          sessionId: id,
          change: SessionChange.stopped,
          agentId: event.subject,
          seq: seq,
          raw: raw,
        );
      }
    case 'convoy.created':
      final id = event.runId ?? event.subject;
      if (id != null) {
        return RunChanged(
          runId: id,
          state: RunState.planning,
          rawState: 'open',
          seq: seq,
          raw: raw,
        );
      }
    case 'convoy.closed':
      final id = event.runId ?? event.subject;
      if (id != null) {
        return RunChanged(
          runId: id,
          state: RunState.completed,
          rawState: 'closed',
          seq: seq,
          raw: raw,
        );
      }
  }
  if (type == 'request.failed' || type.startsWith('request.result')) {
    final requestId = readText(event.payload, 'request_id');
    if (requestId != null) {
      final failed = type == 'request.failed';
      return RequestResult(
        requestId: requestId,
        ok: !failed,
        operation: failed
            ? readText(event.payload, 'operation')
            : type.length > 'request.result.'.length
            ? type.substring('request.result.'.length)
            : null,
        errorCode: failed ? readText(event.payload, 'error_code') : null,
        errorMessage: failed
            ? readText(event.payload, 'error_message') ?? event.message
            : null,
        payload: event.payload,
        seq: seq,
        raw: raw,
      );
    }
  }
  if (event.family == 'run') {
    final id = event.runId ?? event.subject;
    if (id != null) {
      final rawState = readText(event.payload, 'status');
      return RunChanged(
        runId: id,
        state: runStateFromGc(rawState),
        rawState: rawState,
        seq: seq,
        raw: raw,
      );
    }
  }
  if (_activityFamilies.contains(event.family)) {
    return ActivityAppended(event: mapActivity(event), seq: seq, raw: raw);
  }
  return UnknownOrchestrationEvent(type: type, seq: seq, raw: raw);
}

String? _eventSessionId(GcEvent event) =>
    event.sessionId ?? readText(event.payload, 'session_id') ?? event.subject;

/// City event → timeline line. `summary` is a short human sentence built
/// from the type, subject and message (`order gate-sweep completed`).
ActivityEvent mapActivity(GcEvent event) => ActivityEvent(
  type: event.type,
  seq: event.seq,
  id: event.seq?.toString(),
  timestamp: event.ts,
  actor: event.actor,
  subject: event.subject,
  summary: summarizeEvent(event),
  payload: event.payload,
  raw: event.raw,
);

/// Short human line for an event, or null when only the type is known.
String? summarizeEvent(GcEvent event) {
  final family = event.family;
  final dot = event.type.indexOf('.');
  final verb = dot < 0
      ? null
      : event.type.substring(dot + 1).replaceAll('_', ' ');
  final subject = event.subject;
  final message = event.message ?? readText(event.payload, 'reason');
  if (verb == null && subject == null) return message;
  final buffer = StringBuffer();
  buffer.write(family);
  if (subject != null) buffer.write(' $subject');
  if (verb != null) buffer.write(' $verb');
  if (message != null) buffer.write(': $message');
  return buffer.toString();
}

/// Decoded SSE frame → [OrchestrationEvent].
///
/// | Frame `event` | Product event |
/// |---|---|
/// | `heartbeat` (or a nameless frame whose data is only `timestamp`) | [StreamHeartbeat] |
/// | `event`, `tagged_event` | [mapEvent] of `data` |
/// | `pending` | [GateChanged] (opened) with the `request_id` |
/// | `pending_cleared` | [GateChanged] (resolved) |
/// | `turn`, `activity`, `message` (session streams) | [ActivityAppended] with the frame as payload |
/// | anything else | [UnknownOrchestrationEvent] |
/// A session stream `turn` frame → the text it carries (every turn's
/// text, in order); null for any other frame or an empty transcript.
AgentOutputText? mapTurnFrame(GcStreamFrame frame) {
  if (!frame.isTurn) return null;
  final turn = GcSessionTurn.fromJson(frame.data);
  final text = [
    for (final t in turn.turns)
      if (t.text.isNotEmpty) t.text,
  ].join('\n');
  if (text.isEmpty) return null;
  return AgentOutputText(text, cursor: frame.id);
}

OrchestrationEvent mapStreamFrame(GcStreamFrame frame) {
  final seq = frame.seq;
  if (frame.isHeartbeat) {
    return StreamHeartbeat(
      timestamp: frame.toHeartbeat().timestamp,
      seq: seq,
      raw: frame.raw,
    );
  }
  if (frame.isCityEvent) return mapEvent(frame.toEvent());
  switch (frame.event) {
    case 'pending':
      final p = GcPendingInteraction.fromJson(frame.data);
      return GateChanged(gateId: p.requestId, seq: seq, raw: frame.raw);
    case 'pending_cleared':
      final id =
          readText(frame.data, 'request_id') ??
          readText(frame.data, 'id') ??
          '';
      return GateChanged(gateId: id, resolved: true, seq: seq, raw: frame.raw);
    case 'turn':
    case 'activity':
    case 'message':
      return ActivityAppended(
        event: ActivityEvent(
          type: 'session.${frame.event}',
          seq: seq,
          id: frame.id,
          subject: readText(frame.data, 'id'),
          timestamp: toDateTime(frame.data['timestamp']),
          summary: frame.event == 'turn'
              ? GcSessionTurn.fromJson(frame.data).latestText?.trim()
              : readText(frame.data, 'summary'),
          payload: frame.data,
          raw: frame.raw,
        ),
        seq: seq,
        raw: frame.raw,
      );
  }
  return UnknownOrchestrationEvent(type: frame.event, seq: seq, raw: frame.raw);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

bool _isCancelledReason(String? reason) {
  if (reason == null) return false;
  final r = reason.toLowerCase();
  return r.contains('cancel');
}

String? _rigOf(String? identity) {
  if (identity == null) return null;
  final slash = identity.indexOf('/');
  return slash <= 0 ? null : identity.substring(0, slash);
}

String? _workError(WorkItem item) =>
    readText(readMapField(item.raw, 'metadata'), 'last_error') ??
    'Work item ${item.id} failed';

/// The tracked items of finished convoys as work of those runs, leaving out
/// ids already [listed] (the open list) and, with [projectId], other
/// projects' items. A closed convoy's items are closed beads `/beads` no
/// longer lists, so without these a finished run shows no work.
List<WorkItem> finishedConvoyWork(
  Iterable<GcConvoy> finished, {
  Set<String> listed = const {},
  String? projectId,
}) {
  final context = GcWorkContext(
    convoyByBead: {
      for (final convoy in finished)
        for (final child in convoy.children) child.id: convoy.id,
    },
  );
  final seen = {...listed};
  return [
    for (final convoy in finished)
      for (final child in convoy.children)
        if (seen.add(child.id)) mapBead(child, context: context),
  ].where((item) => projectId == null || item.projectId == projectId).toList();
}

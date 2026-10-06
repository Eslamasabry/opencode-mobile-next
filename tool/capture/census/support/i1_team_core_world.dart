// The AI Team world the census scenes of `i1-team-core` and `i2-team-sheets`
// render from: the recorded Gas City fixture (tool/qa/gascity_fixture) with
// its runs, work, agents and questions replaced by a small believable team
// (the scenes of test/support/team_golden_fixture.dart, with more detail),
// every control and the merge roles on, and a clock a scene can move.
// ignore_for_file: invalid_use_of_visible_for_testing_member
import 'dart:async';
import 'dart:io';

import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/widgets/team_moments.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The team's clock: 11 Sep 2026, 09:41 UTC unless a scene moves it.
DateTime teamClock = DateTime.utc(2026, 9, 11, 9, 41);

DateTime teamNow() => teamClock;

DateTime _ago(Duration d) => teamClock.subtract(d);

const teamRunId = 'oc-xru';
const teamFormulaRunId = 'mol-upgrade';
const teamMergedRunId = 'ma-lqw';
const teamReviewRunId = 'oc-set';

Directory _fixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

/// The fixture gateway over lists a scene edits, with merge roles.
class CensusTeamGateway extends FixtureOrchestrationGateway
    implements OrchestrationMergeGateway {
  CensusTeamGateway({
    required super.fixturePath,
    super.hostMode,
    super.url,
    this.city,
  });

  final String? city;

  List<OrchestrationRun> runList = teamRuns();
  List<WorkItem> workList = teamWork();
  List<OrchestrationAgent> agentList = teamAgents();
  List<OrchestrationGate> gateList = teamGates();
  final Map<String, MergeReadiness> readiness = {
    teamMergedRunId: const MergeReadiness(
      runId: teamMergedRunId,
      ready: true,
      rig: 'shopfront',
      targetBranch: 'main',
      files: 1,
      additions: 3,
      mergeCommit: 'e4c1a9f',
    ),
  };
  final Map<String, List<AgentOutputEvent>> outputs = {
    'bl-5qc': teamFoxTranscript(),
  };
  OrchestrationCapabilities? capabilitiesOverride;

  /// What every control answers.
  MutationReceiptStatus controlStatus = MutationReceiptStatus.accepted;
  String? controlMessage;

  @override
  OrchestrationCapabilities get capabilities =>
      capabilitiesOverride ?? super.capabilities;

  @override
  OrchestrationHostIdentity? get host {
    final recorded = super.host;
    final name = city;
    if (recorded == null || name == null) return recorded;
    return OrchestrationHostIdentity(
      provider: recorded.provider,
      version: recorded.version,
      city: name,
      url: recorded.url,
      hostMode: recorded.hostMode,
    );
  }

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => runList;

  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final run in runList) {
      if (run.id == id) return run;
    }
    return null;
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => [
    for (final item in workList)
      if (item.state == WorkState.ready) item,
  ];

  @override
  Future<WorkItem?> workItem(String id) async {
    for (final item in workList) {
      if (item.id == id) return item;
    }
    return null;
  }

  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;

  @override
  Future<OrchestrationAgent?> agent(String id) async {
    for (final agent in agentList) {
      if (agent.id == id) return agent;
    }
    return null;
  }

  @override
  Future<List<OrchestrationGate>> gates() async => gateList;

  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) {
    final scripted = outputs[sessionId];
    if (scripted == null) return super.agentOutput(sessionId);
    late StreamController<AgentOutputEvent> controller;
    controller = StreamController<AgentOutputEvent>(
      onListen: () => scripted.forEach(controller.add),
    );
    return controller.stream;
  }

  MutationReceipt _answer(String requestId) => MutationReceipt(
    id: requestId,
    status: controlStatus,
    message: controlMessage,
    retryable: controlStatus != MutationReceiptStatus.accepted,
    upstreamStatus: switch (controlStatus) {
      MutationReceiptStatus.accepted => 200,
      MutationReceiptStatus.rejected => 409,
      _ => 502,
    },
  );

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MergeReadiness?> mergeReadiness(String runId) async =>
      readiness[runId];

  @override
  Future<MutationReceipt> approveMerge(
    String mergeRequestId, {
    required String requestId,
  }) async => _answer(requestId);

  @override
  Future<MutationReceipt> merge(
    String runId, {
    required String requestId,
  }) async => _answer(requestId);
}

// ---------------------------------------------------------------------------
// The team
// ---------------------------------------------------------------------------

/// fox's session output (invented; the recorded fixture transcript names
/// a real machine's paths).
List<AgentOutputEvent> teamFoxTranscript() => const [
  AgentOutputText(
    'Picking up w-sync: queue writes while offline and replay them in '
    'order once the server answers again.\n'
    '[tool: read]\n'
    '[tool: read lib/storage/draft_store.dart]\n'
    '[tool: read lib/storage/session_store.dart]\n'
    '[tool: bash]\n'
    '[tool: flutter test test/storage]\n'
    '00:04 +18: All tests passed!\n',
  ),
  AgentOutputText(
    'The store already keeps drafts, so the queue can live beside it and '
    'reuse its transaction.\n'
    '[tool: edit]\n'
    '[tool: edit lib/sync/write_queue.dart]\n'
    '[tool: edit lib/sync/replay.dart]\n'
    '[tool: bash]\n'
    '[tool: flutter test test/sync/write_queue_test.dart]\n'
    '00:02 +6 -1: replays in order after reconnect [E]\n'
    '  Expected: [a, b, c]\n'
    '    Actual: [a, c, b]\n',
  ),
  AgentOutputText(
    'Replay sorted by insert time, but two writes share a millisecond. '
    'Using the sequence column instead.\n'
    '[tool: edit lib/sync/replay.dart]\n'
    '[tool: bash]\n'
    '[tool: flutter test test/sync]\n'
    '00:03 +9: All tests passed!\n'
    'Queue works offline and replays in order. Next: the retry banner '
    'while writes are pending.\n'
    '[tool: read lib/ui/session_list.dart]\n',
  ),
  AgentOutputText(
    'The banner reads the queue length and hides at zero.\n'
    '[tool: edit lib/ui/offline_banner.dart]\n'
    '[tool: edit lib/ui/session_list.dart]\n'
    '[tool: bash]\n'
    '[tool: flutter analyze lib/sync lib/ui]\n'
    'Analyzing 2 items...\n'
    'No issues found! (ran in 3.1s)\n'
    '[tool: bash]\n'
    '[tool: flutter test test/ui/offline_banner_test.dart]\n'
    '00:01 +1: shows the pending count\n'
    '00:01 +2: hides when the queue is empty\n'
    '00:02 +3: All tests passed!\n'
    '[tool: bash]\n'
    '[tool: git add -A && git commit -m "Offline write queue with ordered '
    'replay"]\n'
    '[polecat/sync-engine 7d3e2a1] Offline write queue with ordered replay\n'
    ' 5 files changed, 214 insertions(+), 12 deletions(-)\n'
    'Waiting for the schema decision before touching the drafts table.\n',
  ),
];

List<OrchestrationRun> teamRuns() => [
  OrchestrationRun(
    id: teamRunId,
    title: 'Offline-first sessions',
    state: RunState.working,
    rawState: 'open',
    kind: RunKind.batch,
    projectId: 'shopfront',
    stepCount: 5,
    completedSteps: 1,
    startedAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(minutes: 4)),
    raw: const {
      'id': teamRunId,
      'issue_type': 'convoy',
      'status': 'open',
      'title': 'Offline-first sessions',
      'rig': 'shopfront',
    },
  ),
  OrchestrationRun(
    id: teamFormulaRunId,
    title: 'Upgrade the HTTP client and fix what breaks',
    state: RunState.working,
    kind: RunKind.formula,
    projectId: 'shopfront',
    formula: 'mol-upgrade',
    stepCount: 4,
    completedSteps: 2,
    startedAt: _ago(const Duration(minutes: 48)),
    updatedAt: _ago(const Duration(minutes: 2)),
    raw: const {'id': teamFormulaRunId, 'formula': 'mol-upgrade'},
  ),
  OrchestrationRun(
    id: teamMergedRunId,
    title: 'Create hello.py that prints Hello from the AI Team',
    state: RunState.completed,
    kind: RunKind.batch,
    stepCount: 1,
    completedSteps: 1,
    merged: true,
    startedAt: _ago(const Duration(hours: 5, minutes: 20)),
    updatedAt: _ago(const Duration(hours: 5)),
    finishedAt: _ago(const Duration(hours: 5)),
  ),
];

/// A run whose every step is done or ready for review: the Overview ends
/// with the Merge section.
OrchestrationRun teamReviewRun() => OrchestrationRun(
  id: teamReviewRunId,
  title: 'Settings screen refresh',
  state: RunState.completed,
  rawState: 'open',
  kind: RunKind.batch,
  projectId: 'shopfront',
  stepCount: 2,
  completedSteps: 2,
  startedAt: _ago(const Duration(hours: 2, minutes: 40)),
  updatedAt: _ago(const Duration(minutes: 6)),
  raw: const {'id': teamReviewRunId, 'issue_type': 'convoy'},
);

List<WorkItem> teamReviewWork() => [
  WorkItem(
    id: 'oc-w1',
    title: 'Group the settings into sections',
    state: WorkState.completed,
    runId: teamReviewRunId,
    assignee: 'fox',
    createdAt: _ago(const Duration(hours: 2, minutes: 40)),
    updatedAt: _ago(const Duration(hours: 1)),
  ),
  WorkItem(
    id: 'oc-w2',
    title: 'Dark mode toggle',
    state: WorkState.review,
    runId: teamReviewRunId,
    assignee: 'wolf',
    dependsOn: const ['oc-w1'],
    createdAt: _ago(const Duration(hours: 2, minutes: 40)),
    updatedAt: _ago(const Duration(minutes: 20)),
    raw: const {
      'description': 'Add a Dark mode switch under Appearance.',
      'metadata': {'branch': 'polecat/dark-mode'},
    },
  ),
];

MergeReadiness teamReadiness({bool ready = true}) => MergeReadiness(
  runId: teamReviewRunId,
  ready: ready,
  rig: 'shopfront',
  targetBranch: 'main',
  lines: [
    const MergeReadinessLine(key: 'work', ok: true, detail: '2/2 work items'),
    MergeReadinessLine(
      key: 'tests',
      ok: ready,
      detail: ready ? 'passed' : '2 failing in settings_test',
    ),
    const MergeReadinessLine(key: 'build', ok: true, detail: 'passed'),
    MergeReadinessLine(
      key: 'review',
      ok: ready,
      detail: ready ? 'approved' : 'waiting for approval',
    ),
    const MergeReadinessLine(
      key: 'conflicts',
      ok: true,
      detail: 'merges cleanly',
    ),
  ],
  files: 6,
  additions: 312,
  deletions: 87,
  changes: const [
    MergeChange(
      path: 'lib/settings/settings_screen.dart',
      additions: 164,
      deletions: 52,
    ),
    MergeChange(
      path: 'lib/settings/appearance_section.dart',
      additions: 88,
      deletions: 0,
    ),
    MergeChange(path: 'lib/theme/theme_mode.dart', additions: 21, deletions: 9),
    MergeChange(path: 'lib/l10n/app_en.arb', additions: 14, deletions: 2),
    MergeChange(
      path: 'test/settings_screen_test.dart',
      additions: 23,
      deletions: 24,
    ),
    MergeChange(path: 'CHANGELOG.md', additions: 2, deletions: 0),
  ],
  mergeRequest: const MergeRequestInfo(
    id: 'gc-mr-14',
    title: 'Settings screen refresh',
  ),
  branches: const ['polecat/dark-mode'],
);

List<WorkItem> teamWork() => [
  WorkItem(
    id: 'w-storage',
    title: 'Storage layer',
    state: WorkState.completed,
    rawState: 'closed',
    projectId: 'shopfront',
    runId: teamRunId,
    assignee: 'fox',
    createdAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(hours: 2)),
    raw: const {
      'description':
          'Keep sessions and drafts in a local store the app can read '
          'without the server.',
      'metadata': {'branch': 'polecat/storage-layer'},
    },
  ),
  WorkItem(
    id: 'w-sync',
    title: 'Sync engine',
    state: WorkState.working,
    rawState: 'in_progress',
    projectId: 'shopfront',
    runId: teamRunId,
    assignee: 'fox',
    sessionId: 'bl-5qc',
    dependsOn: const ['w-storage'],
    createdAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(minutes: 5)),
    raw: const {
      'id': 'w-sync',
      'status': 'in_progress',
      'description':
          'Queue writes while offline and replay them in order once the '
          'server answers again.',
      'metadata': {
        'gc.routed_to': 'shopfront/gastown.polecat',
        'gc.session_id': 'bl-5qc',
        'branch': 'polecat/sync-engine',
        'gc.work_dir': '/home/dev/shopfront/.gc/worktrees/polecat-fox',
      },
    },
  ),
  WorkItem(
    id: 'w-conflict',
    title: 'Conflict policy',
    state: WorkState.blocked,
    projectId: 'shopfront',
    runId: teamRunId,
    isBlocked: true,
    dependsOn: const ['w-sync'],
    createdAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(hours: 1)),
  ),
  WorkItem(
    id: 'w-schema',
    title: 'Schema for offline drafts',
    state: WorkState.needsInput,
    projectId: 'shopfront',
    runId: teamRunId,
    assignee: 'wolf',
    createdAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(minutes: 12)),
  ),
  WorkItem(
    id: 'w-tests',
    title: 'Tests for reconnect',
    state: WorkState.queued,
    projectId: 'shopfront',
    runId: teamRunId,
    dependsOn: const ['w-sync'],
    createdAt: _ago(const Duration(hours: 3, minutes: 12)),
    updatedAt: _ago(const Duration(hours: 3)),
  ),
  WorkItem(
    id: 'w-ready-1',
    title: 'Retry banner copy',
    state: WorkState.ready,
    projectId: 'shopfront',
    createdAt: _ago(const Duration(minutes: 30)),
  ),
  WorkItem(
    id: 'w-ready-2',
    title: 'Empty state for the drafts list',
    state: WorkState.ready,
    projectId: 'shopfront',
    createdAt: _ago(const Duration(minutes: 25)),
  ),
  WorkItem(
    id: 'w-hello',
    title: 'Create hello.py that prints Hello from the AI Team',
    state: WorkState.completed,
    runId: teamMergedRunId,
    assignee: 'mole',
    updatedAt: _ago(const Duration(hours: 5)),
  ),
];

List<OrchestrationAgent> teamAgents() => [
  OrchestrationAgent(
    id: 'gastown.mayor',
    name: 'mayor',
    state: AgentState.idle,
    sessionId: 'ma-1',
    pool: 'gastown.mayor',
    provider: 'opencode',
    model: 'anthropic/claude-sonnet',
    sessionStartedAt: _ago(const Duration(hours: 6)),
  ),
  OrchestrationAgent(
    id: 'fox',
    name: 'fox',
    state: AgentState.working,
    rawState: 'active',
    sessionId: 'bl-5qc',
    sessionName: 'shopfront--polecat--fox',
    pool: 'gastown.polecat',
    provider: 'opencode',
    model: 'openai/gpt-x',
    harness: 'OpenCode',
    currentWorkId: 'w-sync',
    contextPercent: 63,
    workDir: '/home/dev/shopfront/.gc/worktrees/polecat-fox',
    branch: 'polecat/sync-engine',
    lastActivity: _ago(const Duration(minutes: 1)),
    sessionStartedAt: _ago(const Duration(hours: 3)),
    raw: const {
      'name': 'fox',
      'pool': 'gastown.polecat',
      'state': 'active',
      'session': 'bl-5qc',
    },
  ),
  OrchestrationAgent(
    id: 'wolf',
    name: 'wolf',
    state: AgentState.waiting,
    sessionId: 'bl-7wr',
    pool: 'gastown.polecat',
    provider: 'opencode',
    model: 'openai/gpt-x',
    currentWorkId: 'w-schema',
    contextPercent: 41,
    lastActivity: _ago(const Duration(minutes: 12)),
    sessionStartedAt: _ago(const Duration(hours: 1)),
  ),
  OrchestrationAgent(
    id: 'gastown.refinery',
    name: 'refinery',
    state: AgentState.idle,
    pool: 'gastown.refinery',
    lastActivity: _ago(const Duration(minutes: 40)),
  ),
  const OrchestrationAgent(
    id: 'gastown.deacon',
    name: 'deacon',
    state: AgentState.stopped,
    pool: 'gastown.deacon',
    suspended: true,
  ),
];

OrchestrationGate teamChoiceGate() => OrchestrationGate(
  id: 'req-schema-1',
  kind: GateKind.choice,
  rawKind: 'choice',
  title: 'Keep drafts in SQLite or in plain files?',
  prompt:
      'Drafts must survive a restart. SQLite is safer; plain files are '
      'easier to inspect.',
  workId: 'w-schema',
  runId: teamRunId,
  agentId: 'bl-7wr',
  choices: const ['SQLite', 'Plain files'],
  createdAt: _ago(const Duration(minutes: 12)),
  raw: const {'request_id': 'req-schema-1', 'session_id': 'bl-7wr'},
);

OrchestrationGate teamConfirmGate() => OrchestrationGate(
  id: 'req-migrate',
  kind: GateKind.confirmation,
  rawKind: 'confirmation',
  title: 'Run the database migration on the staging copy?',
  prompt: 'It rewrites the drafts table; the staging copy is backed up first.',
  workId: 'w-sync',
  runId: teamRunId,
  agentId: 'bl-5qc',
  createdAt: _ago(const Duration(minutes: 3)),
  raw: const {'request_id': 'req-migrate', 'session_id': 'bl-5qc'},
);

OrchestrationGate teamTextGate() => OrchestrationGate(
  id: 'req-name',
  kind: GateKind.freeText,
  rawKind: 'text',
  title: 'What should the offline banner say?',
  prompt: 'One short line shown while the phone has no connection.',
  workId: 'w-sync',
  runId: teamRunId,
  agentId: 'bl-5qc',
  createdAt: _ago(const Duration(minutes: 7)),
  raw: const {'request_id': 'req-name', 'session_id': 'bl-5qc'},
);

OrchestrationGate teamFailedGate() => OrchestrationGate(
  id: 'run-failed-upgrade',
  kind: GateKind.runFailed,
  rawKind: 'run_failed',
  title: 'Upgrade the HTTP client and fix what breaks',
  prompt: 'The agent stopped: 3 tests fail after the client upgrade.',
  workId: 'w-sync',
  runId: teamFormulaRunId,
  agentId: 'fox',
  createdAt: _ago(const Duration(minutes: 20)),
);

List<OrchestrationGate> teamGates() => [teamChoiceGate()];

// ---------------------------------------------------------------------------
// The controller
// ---------------------------------------------------------------------------

/// A started controller over a [CensusTeamGateway] that [configure] edits
/// before the first read. [onPhone] hosts the team on this phone.
Future<(OrchestrationController, CensusTeamGateway)> teamController({
  void Function(CensusTeamGateway gateway)? configure,
  bool onPhone = false,
  Future<ProbeVerdict> Function(OrchestrationConfig config)? probe,
  bool start = true,
  SharedPreferences? prefs,
  OrchestrationConfig? config,
  String profileId = 'laptop',
}) async {
  TeamCelebrations.forgetSession();
  TeamNeedsYouLabel.forgetSession();
  if (prefs == null) {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
  }
  final path = _fixtureRoot().path;
  config ??= onPhone
      ? OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'http://127.0.0.1:8472',
          city: 'phone',
          hostMode: OrchestrationHostMode.phone,
          hostKind: OrchestrationHostKind.phone,
          enabledAt: DateTime.utc(2026, 9, 10),
        )
      : OrchestrationConfig(
          provider: OrchestrationProvider.fixture,
          url: 'http://dev-pc:7000',
          city: 'bright-lights',
          enabledAt: DateTime.utc(2026, 9, 10),
        );
  final gateway = CensusTeamGateway(
    fixturePath: path,
    hostMode: onPhone
        ? OrchestrationHostMode.phone
        : OrchestrationHostMode.computer,
    url: config.url,
    city: onPhone ? config.city : 'bright-lights',
  );
  configure?.call(gateway);
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: profileId,
      name: onPhone ? 'This phone' : 'Development PC',
      baseUrl: onPhone ? 'http://127.0.0.1:4097' : 'http://192.168.1.20:4096',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => gateway,
    probe: probe,
    now: teamNow,
  );
  if (start) {
    await controller.start();
  } else {
    unawaited(controller.start());
  }
  return (controller, gateway);
}

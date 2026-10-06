// screen-team-3 fixtures: a small believable team over the recorded Gas
// City fixture (tool/qa/gascity_fixture), with the agents and the work
// replaced so every agent standing and a rich work item are on screen, and
// a pinned clock.
import 'dart:io';

import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The scenes' clock.
final team3Clock = DateTime.utc(2026, 9, 11, 12, 30);

/// Every standing the agents list orders: needs you, crashed, working (two
/// workers told apart by name), idle, asleep, paused.
List<OrchestrationAgent> team3Agents() => [
  OrchestrationAgent(
    id: 'dog-1',
    name: 'gastown.dog-1',
    pool: 'gastown.dog',
    state: AgentState.stopped,
    lastActivity: team3Clock.subtract(const Duration(hours: 3)),
  ),
  OrchestrationAgent(
    id: 'slit',
    name: 'ocproof/gastown.slit',
    pool: 'ocproof/gastown.polecat',
    state: AgentState.idle,
    suspended: true,
    rawState: 'suspended',
  ),
  OrchestrationAgent(
    id: 'refinery',
    name: 'ocproof/gastown.refinery',
    state: AgentState.idle,
    lastActivity: team3Clock.subtract(const Duration(minutes: 20)),
  ),
  OrchestrationAgent(
    id: 'furiosa',
    name: 'ocproof/gastown.furiosa',
    pool: 'ocproof/gastown.polecat',
    state: AgentState.working,
    currentWorkId: 'oc-loy',
    sessionId: 'bl-48k',
    lastActivity: team3Clock.subtract(const Duration(minutes: 1)),
  ),
  OrchestrationAgent(
    id: 'mayor',
    name: 'gastown.mayor',
    state: AgentState.crashed,
    lastActivity: team3Clock.subtract(const Duration(minutes: 7)),
  ),
  OrchestrationAgent(
    id: 'nux',
    name: 'ocproof/gastown.nux',
    pool: 'ocproof/gastown.polecat',
    state: AgentState.waiting,
    currentWorkId: 'oc-dep',
    lastActivity: team3Clock.subtract(const Duration(minutes: 2)),
  ),
];

/// A work item with everything the sheet shows: a dependency, one item
/// waiting on it, a description, branch and worktree, output, validation.
List<WorkItem> team3Work() => [
  WorkItem(
    id: 'oc-loy',
    title: 'Add subtract function to calc.py',
    state: WorkState.working,
    rawState: 'in_progress',
    runId: 'oc-xru',
    assignee: 'ocproof/gastown.furiosa',
    sessionId: 'bl-48k',
    dependsOn: const ['oc-dep'],
    createdAt: DateTime.utc(2026, 9, 10, 18, 44),
    updatedAt: team3Clock.subtract(const Duration(minutes: 3)),
    raw: const {
      'id': 'oc-loy',
      'title': 'Add subtract function to calc.py',
      'status': 'in_progress',
      'description':
          'Add `subtract(a, b)` next to `add` in **calc.py** and keep '
          'the module docstring current.',
      'priority': 2,
      'metadata': {
        'branch': 'polecat/oc-loy',
        'target': 'master',
        'gc.work_dir':
            '/home/eslam/city2/.gc/worktrees/ocproof/polecats/'
            'gastown.furiosa',
        'last_output': 'PASS test_calc.py::test_add\n1 passed in 0.02s',
        'validation': {'status': 'passed', 'summary': '1 of 1 tests'},
      },
    },
  ),
  WorkItem(
    id: 'oc-dep',
    title: 'Agree the calc.py API',
    state: WorkState.needsInput,
    rawState: 'blocked',
    raw: const {'id': 'oc-dep', 'title': 'Agree the calc.py API'},
  ),
  WorkItem(
    id: 'gc-2',
    title: 'Write tests for calc.py',
    state: WorkState.queued,
    rawState: 'open',
    dependsOn: const ['oc-loy'],
    raw: const {'id': 'gc-2', 'title': 'Write tests for calc.py'},
  ),
];

class Team3Gateway extends FixtureOrchestrationGateway {
  Team3Gateway({
    required super.fixturePath,
    required this.agentList,
    required this.workList,
    this.caps,
    super.url,
  });

  List<OrchestrationAgent> agentList;
  List<WorkItem> workList;

  /// Replaces the fixture's capabilities (every control on) when set.
  final OrchestrationCapabilities? caps;

  @override
  OrchestrationCapabilities get capabilities => caps ?? super.capabilities;

  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;

  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;

  @override
  Future<WorkItem?> workItem(String id) async {
    for (final item in workList) {
      if (item.id == id) return item;
    }
    return null;
  }
}

Directory _fixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

/// A started controller over [Team3Gateway]; the gateway is returned too so
/// a test can read its control calls. Dispose the controller after use.
Future<(OrchestrationController, Team3Gateway)> team3Controller({
  List<OrchestrationAgent>? agents,
  List<WorkItem>? work,
  OrchestrationCapabilities? caps,
}) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: 'http://dev-pc:7000',
    city: 'bright-lights',
    enabledAt: DateTime.utc(2026, 9, 10),
  );
  late Team3Gateway gateway;
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'team3',
      name: 'Development PC',
      baseUrl: 'https://server.example',
      orchestration: config,
    ),
    config: config,
    store: OrchestrationStore(prefs),
    gatewayFactory: (_, _) => gateway = Team3Gateway(
      fixturePath: _fixtureRoot().path,
      agentList: agents ?? team3Agents(),
      workList: work ?? team3Work(),
      caps: caps,
      url: config.url,
    ),
    now: () => team3Clock,
  );
  await controller.start();
  return (controller, gateway);
}

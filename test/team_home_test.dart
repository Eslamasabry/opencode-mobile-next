// TEAM-108: the AI Team home over the fixture gateway. Run ordering and
// the collapsed completed group, filter chips, title search, the host
// chip and its Technical details, the fleet rows, Needs you with the
// read-only gate sheet, stale, loading, error, pull-to-refresh and the
// Workspace card wiring.

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_mappers.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/kit/kit_top_bar.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/gascity_recorded_city.dart';
import 'team_open_settings.dart';

Directory _findFixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError(
    'tool/qa/gascity_fixture not found from ${Directory.current}',
  );
}

/// The `blocked` scenario's derived `/pending` entry (as in
/// team_on_work_test).
const _blockedPending = <String, Object?>{
  'session_id': 'bl-polecat-1',
  'request_id': 'req-fixture-choice-1',
  'kind': 'choice',
  'prompt': 'calc.py already defines subtract(). Replace it, keep it, or stop?',
  'options': ['replace', 'keep', 'stop'],
  'metadata': {'bead': 'oc-loy', 'fixture.scenario': 'blocked'},
};

/// Real sockets inside a widget test (the binding's default answers 400).
class _RealHttp extends HttpOverrides {}

/// The fixture with per-scope overrides, a read counter and an owned event
/// stream so a test can drop the connection.
class _Gateway implements OrchestrationGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final calls = <String, int>{};
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  List<OrchestrationGate>? gatesOverride;
  OrchestrationCapabilities? capabilitiesOverride;

  int count(String name) => calls[name] ?? 0;
  void _hit(String name) => calls[name] = count(name) + 1;

  @override
  OrchestrationCapabilities get capabilities =>
      capabilitiesOverride ?? inner.capabilities;
  @override
  OrchestrationHostIdentity? get host => inner.host;
  @override
  bool get isClosed => inner.isClosed;
  @override
  Future<void> close() async {
    await stream.close();
    await inner.close();
  }

  @override
  Future<List<OrchestrationProject>> projects() {
    _hit('projects');
    return inner.projects();
  }

  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async {
    _hit('runs');
    return runsOverride ?? await inner.runs(projectId: projectId);
  }

  @override
  Future<OrchestrationRun?> run(String id) => inner.run(id);

  @override
  Future<List<WorkItem>> work({String? projectId}) async {
    _hit('work');
    return workOverride ?? await inner.work(projectId: projectId);
  }

  @override
  Future<List<WorkItem>> readyWork({String? projectId}) =>
      inner.readyWork(projectId: projectId);

  @override
  Future<WorkItem?> workItem(String id) => inner.workItem(id);

  @override
  Future<List<OrchestrationAgent>> agents() async {
    _hit('agents');
    return agentsOverride ?? await inner.agents();
  }

  @override
  Future<OrchestrationAgent?> agent(String id) => inner.agent(id);

  @override
  Future<List<OrchestrationGate>> gates() async {
    _hit('gates');
    return gatesOverride ?? await inner.gates();
  }

  @override
  Future<OrchestrationUsage?> usage() {
    _hit('usage');
    return inner.usage();
  }

  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) {
    _hit('activity');
    return inner.activity(afterSeq: afterSeq, limit: limit);
  }

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => inner.respond(gateId, response, requestId: requestId);

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => inner.message(agentId, text, requestId: requestId);

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => inner.controlAgent(agentId, action, requestId: requestId);

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => inner.cancelRun(runId, requestId: requestId);

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => inner.assign(workId, agentId: agentId, requestId: requestId);

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => inner.createWork(
    title: title,
    description: description,
    projectId: projectId,
    requestId: requestId,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late String fixturePath;
  late SharedPreferences prefs;
  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs);
    clock = DateTime.utc(2026, 9, 11, 12, 30);
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          null,
        );
  });

  OrchestrationConfig config({
    OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
    String? url,
  }) => OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: url ?? fixturePath,
    city: 'bright-lights',
    hostMode: hostMode,
    enabledAt: DateTime.utc(2026, 9, 10),
  );

  ServerProfile profile({OrchestrationConfig? config}) => ServerProfile(
    id: 'srv-1',
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
    orchestration: config,
  );

  Future<(OrchestrationController, _Gateway)> boot({
    bool started = true,
    OrchestrationProbe? probe,
    void Function(_Gateway gateway)? configure,
    OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
    String? url,
  }) async {
    final gateway = _Gateway(
      FixtureOrchestrationGateway(fixturePath: fixturePath, hostMode: hostMode),
    );
    configure?.call(gateway);
    final cfg = config(hostMode: hostMode, url: url);
    final controller = OrchestrationController(
      profile: profile(config: cfg),
      config: cfg,
      store: store,
      gatewayFactory: (_, _) => gateway,
      probe: probe,
      now: () => clock,
    );
    addTearDown(controller.dispose);
    if (started) await controller.start();
    return (controller, gateway);
  }

  Widget app(Widget home, {Locale locale = const Locale('en')}) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: home,
  );

  Future<void> pumpHome(
    WidgetTester tester,
    OrchestrationController controller, {
    ValueChanged<OrchestrationRun>? onOpenRun,
    ValueChanged<OrchestrationAgent>? onOpenAgent,
  }) async {
    // Tall enough that every row of the shapes below is built.
    tester.view.physicalSize = const Size(800, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      app(
        TeamHomeScreen(
          controller: controller,
          onOpenRun: onOpenRun,
          onOpenAgent: onOpenAgent,
          now: () => clock,
        ),
      ),
    );
    await tester.pump();
  }

  Finder runRow(String id) => find.byKey(ValueKey('team-home-run-$id'));

  double top(WidgetTester tester, Finder finder) =>
      tester.getTopLeft(finder).dy;

  OrchestrationRun run(
    String id,
    RunState state,
    int minutesAgo, {
    RunKind kind = RunKind.formula,
  }) => OrchestrationRun(
    id: id,
    title: 'Run $id',
    state: state,
    kind: kind,
    formula: kind == RunKind.formula ? 'ship' : null,
    stepCount: 4,
    completedSteps: state == RunState.completed ? 4 : 1,
    updatedAt: clock.subtract(Duration(minutes: minutesAgo)),
  );

  /// Seven runs across every group; the failed one ranks with needs-you.
  void mixedShape(_Gateway gateway) {
    gateway
      ..workOverride = const []
      ..gatesOverride = const []
      ..runsOverride = [
        run('done-1', RunState.completed, 1),
        run('wait-1', RunState.waiting, 2),
        run('work-old', RunState.working, 30),
        run('work-new', RunState.working, 3),
        run('plan-1', RunState.planning, 4),
        run('done-2', RunState.completed, 5),
        run('blocked-1', RunState.blocked, 6),
        run('failed-1', RunState.failed, 7, kind: RunKind.batch),
      ];
  }

  /// TEAM-117: the fixture convoy over the recorded `oc-loy` as the host
  /// last showed it — pushed and in the refinery's hands, no live agent,
  /// never closed.
  void handedToRefineryShape(_Gateway gateway) {
    final convoys = GcList<GcConvoy>.fromJson(
      readMap(
        jsonDecode(
          File('$fixturePath/recordings/convoys.json').readAsStringSync(),
        ),
      ),
      GcConvoy.fromJson,
    ).items;
    final bead = GcBead.fromJson(
      readMap(
        jsonDecode(
          File(
            '$fixturePath/recordings/bead_handed_to_refinery.json',
          ).readAsStringSync(),
        ),
      ),
    );
    final context = GcWorkContext.from(convoys: convoys);
    final work = mapBeads([bead], context: context);
    gateway
      ..workOverride = work
      ..runsOverride = mapConvoys(convoys, work: work, context: context)
      ..gatesOverride = const [];
  }

  /// The `blocked` shape of the old team card test: the fixture convoy over a
  /// blocked `oc-loy` and a closed sibling, plus the pending choice.
  void blockedShape(_Gateway gateway) {
    final convoys = GcList<GcConvoy>.fromJson(
      readMap(
        jsonDecode(
          File('$fixturePath/recordings/convoys.json').readAsStringSync(),
        ),
      ),
      GcConvoy.fromJson,
    ).items;
    final beads = [
      GcBead.fromJson(const {
        'id': 'oc-loy',
        'title': 'Add subtract function to calc.py',
        'status': 'open',
        'issue_type': 'task',
        'is_blocked': true,
      }),
      GcBead.fromJson(const {
        'id': 'gc-2',
        'title': 'Write tests for calc.py',
        'status': 'closed',
        'issue_type': 'task',
      }),
    ];
    final context = GcWorkContext.from(convoys: convoys);
    final work = [
      for (final item in mapBeads(beads, context: context))
        WorkItem(
          id: item.id,
          title: item.title,
          state: item.state,
          runId: 'oc-xru',
          isBlocked: item.isBlocked,
          raw: item.raw,
        ),
    ];
    final runs = mapConvoys(convoys, work: work, context: context);
    gateway
      ..workOverride = work
      ..runsOverride = runs
      ..gatesOverride = [
        for (final gate in mapGates(
          pending: [GcPendingInteraction.fromJson(_blockedPending)],
          beads: beads,
          runs: runs,
        ))
          OrchestrationGate(
            id: gate.id,
            kind: gate.kind,
            title: gate.title,
            prompt: gate.prompt,
            workId: 'oc-loy',
            agentId: gate.agentId,
            choices: gate.choices,
            createdAt: clock.subtract(const Duration(minutes: 2)),
          ),
      ];
  }

  /// [mixedShape] and one more working task: past the eight a home shows
  /// without search, so the search icon and the filter menu appear.
  void manyShape(_Gateway gateway) {
    mixedShape(gateway);
    gateway.runsOverride = [
      ...?gateway.runsOverride,
      run('extra-1', RunState.working, 8),
    ];
  }

  String? lineOf(WidgetTester tester, String id) {
    final line = find.descendant(
      of: find.byKey(ValueKey('team-home-run-state-$id')),
      matching: find.byType(RichText),
    );
    return line.evaluate().isEmpty
        ? null
        : (tester.widget<RichText>(line.first).text.toPlainText());
  }

  group('runs', () {
    testWidgets('the fixture task lists with one plain status line', (
      tester,
    ) async {
      OrchestrationRun? opened;
      final (controller, _) = await boot();
      await pumpHome(tester, controller, onOpenRun: (run) => opened = run);
      expect(find.byKey(const ValueKey('team-home-data')), findsOneWidget);
      expect(find.text('AI Team'), findsOneWidget);
      // One list, no tabs or counts to choose between first.
      expect(find.text('Runs (1)'), findsNothing);
      expect(find.byKey(const ValueKey('team-home-tasks')), findsOneWidget);
      // Nothing waits on the person: no Needs you section at all.
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(runRow('oc-xru'), findsOneWidget);
      // The task is named by its work and honest about nobody having it,
      // in the person's words: no "Batch · convoy".
      expect(find.text('Add subtract function to calc.py'), findsOneWidget);
      expect(find.text('sling-oc-loy'), findsNothing);
      // One step is not worth counting ("0 of 1"): the state says it all,
      // then how long and whether a worker started (the recorded task has
      // waited far past the team's checks; team-discover-2026-09-25).
      expect(
        lineOf(tester, 'oc-xru'),
        'Waiting for a worker · 17 h 45 min · no worker has started',
      );
      expect(find.textContaining('convoy'), findsNothing);
      expect(find.text('Planning'), findsNothing);
      await tester.tap(runRow('oc-xru'));
      expect(opened?.id, 'oc-xru');
      await tester.pumpAndSettle();
      // Five live agents; the dog slots and the core helper are not agents.
      await openTeamSettingsFromHome(tester);
      expect(find.textContaining('5 roles'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('team-home-upkeep-row')),
        findsNothing,
        reason: 'no upkeep in the recording: no upkeep line',
      );
      expect(find.text('Done today'), findsNothing);
      expect(find.byKey(const ValueKey('team-home-stale')), findsNothing);

      expect(tester.takeException(), isNull);
    });

    testWidgets('ordered needs-you → active → waiting/blocked, then done', (
      tester,
    ) async {
      final (controller, _) = await boot(configure: mixedShape);
      await pumpHome(tester, controller);

      final order = [
        'failed-1',
        'work-new',
        'work-old',
        'plan-1',
        'blocked-1',
        'wait-1',
      ];
      for (final id in order) {
        expect(runRow(id), findsOneWidget, reason: id);
      }
      for (var i = 1; i < order.length; i++) {
        expect(
          top(tester, runRow(order[i - 1])),
          lessThan(top(tester, runRow(order[i]))),
          reason: '${order[i - 1]} above ${order[i]}',
        );
      }
      // What finished follows in the same list, no heading of its own, up
      // to three shown.
      expect(
        top(tester, runRow('done-1')),
        greaterThan(top(tester, runRow('wait-1'))),
      );
      await tester.dragUntilVisible(
        runRow('done-2'),
        find.byKey(const ValueKey('team-home-runs')),
        const Offset(0, -200),
      );
      expect(runRow('done-1'), findsOneWidget);
      expect(runRow('done-2'), findsOneWidget);
      expect(
        top(tester, runRow('done-1')),
        lessThan(top(tester, runRow('done-2'))),
      );
      // Rows say where a task is in plain words; the engine's words stay
      // out of the list.
      expect(find.textContaining('formula'), findsNothing);
      expect(find.textContaining('convoy'), findsNothing);
      expect(
        lineOf(tester, 'work-new'),
        'Working · 1 of 4 steps done · a reviewer checks it next',
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('one list under one heading: needs you, then working, then '
        'done; no state sections', (tester) async {
      final (controller, _) = await boot(
        configure: (g) {
          mixedShape(g);
          // Two questions: each is its task's row, first; no panel of
          // questions above the list.
          g.gatesOverride = const [
            OrchestrationGate(
              id: 'q-wait',
              kind: GateKind.freeText,
              title: 'Which branch?',
              runId: 'wait-1',
            ),
            OrchestrationGate(
              id: 'q-blocked',
              kind: GateKind.freeText,
              title: 'Which port?',
              runId: 'blocked-1',
            ),
          ];
        },
      );
      await pumpHome(tester, controller);
      // One panel of every task, with no heading; none of the old section
      // labels.
      final tasks = find.byKey(const ValueKey('team-home-tasks'));
      expect(tasks, findsOneWidget);
      expect(find.text('Tasks'), findsNothing);
      expect(find.text('Done today'), findsNothing);
      expect(find.text('Needs you'), findsNothing);
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(
        find.byKey(const ValueKey('team-home-completed-group')),
        findsNothing,
      );
      final order = ['wait-1', 'work-new', 'done-1'];
      for (final id in order) {
        expect(
          find.descendant(of: tasks, matching: runRow(id)),
          findsOneWidget,
          reason: '$id in the one list',
        );
      }
      for (var i = 1; i < order.length; i++) {
        expect(
          top(tester, runRow(order[i - 1])),
          lessThan(top(tester, runRow(order[i]))),
          reason: '${order[i - 1]} above ${order[i]}',
        );
      }
      // The row's own words carry the state: a question's task row is the
      // question, shown once.
      expect(lineOf(tester, 'wait-1'), 'Needs you · Which branch?');
      expect(lineOf(tester, 'blocked-1'), 'Needs you · Which port?');
      expect(find.byKey(const ValueKey('team-home-gate-q-wait')), findsNothing);
      expect(find.text('Which branch?'), findsNothing);
      expect(
        lineOf(tester, 'work-new'),
        'Working · 1 of 4 steps done · a reviewer checks it next',
      );
      expect(lineOf(tester, 'done-1'), startsWith('Done'));
      expect(tester.takeException(), isNull);
    });

    testWidgets('eight tasks show no search; past eight, search and the '
        'filter menu reduce the rows', (tester) async {
      final (few, _) = await boot(configure: mixedShape);
      await pumpHome(tester, few);
      expect(find.byKey(const ValueKey('team-home-search-open')), findsNothing);
      expect(find.byKey(const ValueKey('team-home-filter-menu')), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      few.dispose();

      final (controller, _) = await boot(configure: manyShape);
      await pumpHome(tester, controller);
      await tester.tap(find.byKey(const ValueKey('team-home-search-open')));
      await tester.pumpAndSettle();

      Future<void> choose(String filter) async {
        await tester.tap(find.byKey(const ValueKey('team-home-filter-menu')));
        await tester.pumpAndSettle();
        await tester.tap(find.byKey(ValueKey('team-home-filter-$filter')));
        await tester.pumpAndSettle();
      }

      await choose('active');
      expect(runRow('work-new'), findsOneWidget);
      expect(runRow('work-old'), findsOneWidget);
      expect(runRow('plan-1'), findsOneWidget);
      expect(runRow('blocked-1'), findsNothing);
      expect(runRow('wait-1'), findsNothing);
      expect(runRow('failed-1'), findsNothing);

      await choose('blocked');
      expect(runRow('failed-1'), findsOneWidget);
      expect(runRow('blocked-1'), findsOneWidget);
      expect(runRow('wait-1'), findsOneWidget);
      expect(runRow('work-new'), findsNothing);

      await choose('completed');
      expect(runRow('done-1'), findsOneWidget);
      expect(runRow('done-2'), findsOneWidget);
      expect(runRow('work-new'), findsNothing);

      await choose('all');
      expect(runRow('work-new'), findsOneWidget);
      expect(runRow('extra-1'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('search filters by title and clears', (tester) async {
      final (controller, _) = await boot(configure: manyShape);
      await pumpHome(tester, controller);
      await tester.tap(find.byKey(const ValueKey('team-home-search-open')));
      await tester.pumpAndSettle();

      await tester.enterText(
        find.byKey(const ValueKey('team-home-search')),
        'WORK-NEW',
      );
      await tester.pump(KitMotion.typingSettle);
      await tester.pumpAndSettle();
      expect(runRow('work-new'), findsOneWidget);
      expect(runRow('work-old'), findsNothing);
      expect(runRow('failed-1'), findsNothing);

      await tester.enterText(
        find.byKey(const ValueKey('team-home-search')),
        'nothing like this',
      );
      await tester.pump(KitMotion.typingSettle);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('team-home-runs-empty-filtered')),
        findsOneWidget,
      );
      expect(find.text('No tasks match.'), findsOneWidget);

      await tester.tap(find.byKey(const ValueKey('team-home-search-clear')));
      await tester.pumpAndSettle();
      expect(runRow('work-new'), findsOneWidget);
      expect(runRow('work-old'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('team-home-runs-empty-filtered')),
        findsNothing,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('TEAM-117: work in the refinery\'s hands reads Reviewing', (
      tester,
    ) async {
      final (controller, _) = await boot(configure: handedToRefineryShape);
      await pumpHome(tester, controller);
      expect(lineOf(tester, 'oc-xru'), 'Reviewing');
      expect(find.textContaining('Waiting for a worker'), findsNothing);
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a gated task with its one question: the question is its '
        'row, shown once', (tester) async {
      final (controller, _) = await boot(configure: blockedShape);
      await pumpHome(tester, controller);
      final gate = controller.snapshot.gates.single;
      // One row: the task, its question inline and its step count; no
      // separate block and no second copy of the task.
      expect(runRow('oc-xru'), findsOneWidget);
      expect(find.byKey(ValueKey('team-home-gate-${gate.id}')), findsNothing);
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(
        find.textContaining('Add subtract function to calc.py'),
        findsOneWidget,
      );
      final line = lineOf(tester, 'oc-xru');
      expect(line, startsWith('Needs you · '));
      expect(line, contains(gate.title));
      // It opens the question (the Gate sheet).
      await tester.tap(runRow('oc-xru'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-gate-sheet')), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('no tasks at all: the empty state teaches once', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) => g.runsOverride = const [],
      );
      await pumpHome(tester, controller);
      expect(
        find.byKey(const ValueKey('team-home-runs-empty')),
        findsOneWidget,
      );
      // Honest: a host lists finished tasks for a bounded time only.
      expect(find.text('No recent tasks'), findsOneWidget);
      expect(find.text('No runs yet.'), findsNothing);
      expect(
        find.text(
          'Say what you need, and the team splits it into steps and shows '
          'its progress here.',
        ),
        findsOneWidget,
      );
      // The pinned button is the action; the state does not repeat it.
      expect(find.text('Give the team a task'), findsOneWidget);
    });

    testWidgets('a task that finished and merged stays under Done: read by '
        'the Gas City gateway from the recorded city', (tester) async {
      // The live emulator city after the hello.py task merged: /convoys is
      // empty, the convoy is closed. Read through the real gateway.
      clock = recordedConvoyClosedAt.add(const Duration(hours: 5));
      // Widget tests answer every HttpClient request with 400; this read
      // goes over a real loopback socket.
      final recorded = await tester.runAsync(
        () => HttpOverrides.runWithHttpOverrides(() async {
          final city = await RecordedCity.start();
          final gateway = GasCityGateway(
            url: city.url,
            city: recordedCity,
            clock: () => clock,
          );
          try {
            return await gateway.runs();
          } finally {
            await gateway.close();
            await city.close();
          }
        }, _RealHttp()),
      );
      final (controller, _) = await boot(
        configure: (g) => g.runsOverride = recorded,
      );
      await pumpHome(tester, controller);

      expect(find.byKey(const ValueKey('team-home-runs-empty')), findsNothing);
      expect(find.text('No recent tasks'), findsNothing);
      expect(find.byKey(const ValueKey('team-home-tasks')), findsOneWidget);
      expect(
        find.byKey(const ValueKey('team-home-run-ma-lqw')),
        findsOneWidget,
      );
      expect(
        find.text('Create hello.py that prints Hello from the AI Team'),
        findsOneWidget,
      );
      expect(lineOf(tester, 'ma-lqw'), 'Done · merged 5h ago');
    });

    testWidgets('no tasks, and this phone cannot start one: no button', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) => g
          ..runsOverride = const []
          ..capabilitiesOverride = const OrchestrationCapabilities(
            runs: true,
            agents: true,
          ),
      );
      await pumpHome(tester, controller);
      final empty = find.byKey(const ValueKey('team-home-runs-empty'));
      expect(empty, findsOneWidget);
      expect(find.text('Start tasks on the computer for now.'), findsOneWidget);
      // Hide, don't disable: no button the host could not honour.
      expect(find.text('Give the team a task'), findsNothing);
      expect(
        find.descendant(of: empty, matching: find.byType(TextButton)),
        findsNothing,
      );
    });
  });

  String hostPhrase(WidgetTester tester) {
    final bar = find.descendant(
      of: find.byType(TeamHomeScreen),
      matching: find.byType(KitTopBar),
    );
    final phrase = tester.widget<KitTopBar>(bar).subtitle!;
    expect(
      find.descendant(of: bar, matching: find.text(phrase)),
      findsOneWidget,
    );
    return phrase;
  }

  // Technical details open from the page's "how it runs" row (P3.4).
  Future<void> openDetails(WidgetTester tester) async {
    await openTeamSettingsFromHome(tester);
    await tester.tap(find.byKey(const ValueKey('team-home-host-row')));
    await tester.pumpAndSettle();
  }

  Future<void> openAgents(WidgetTester tester) async {
    await openTeamSettingsFromHome(tester);
    await tester.tap(find.byKey(const ValueKey('team-home-agents-row')));
    await tester.pumpAndSettle();
  }

  group('host', () {
    testWidgets('one phrase under the title; the address, version and '
        'engine are behind Technical details', (tester) async {
      // The read path alone: no control capability, so read-only.
      final (controller, _) = await boot(
        configure: (g) =>
            g.capabilitiesOverride = OrchestrationCapabilities.gascityRead,
      );
      await pumpHome(tester, controller);
      final phrase = hostPhrase(tester);
      expect(phrase, startsWith('On '));
      for (final word in ['Gas City', '1.4.1', 'city', 'read-only']) {
        expect(phrase, isNot(contains(word)), reason: word);
      }
      expect(find.textContaining('Workstation'), findsNothing);

      await openDetails(tester);
      final sheet = find.byKey(const ValueKey('team-home-host-sheet'));
      expect(sheet, findsOneWidget);
      expect(
        find.descendant(of: sheet, matching: find.text('Technical details')),
        findsOneWidget,
      );
      // The sheet still names the host by the team URL, never the profile.
      expect(
        find.descendant(of: sheet, matching: find.text('Workstation')),
        findsNothing,
      );
      expect(
        find.descendant(of: sheet, matching: find.text('1.4.1')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: sheet, matching: find.textContaining('bright')),
        findsWidgets,
      );
      expect(
        find.byKey(const ValueKey('team-home-host-read-only')),
        findsOneWidget,
      );
      expect(
        find.descendant(of: sheet, matching: find.textContaining('polecat')),
        findsWidgets,
        reason: 'engine names live here, never on the list',
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets('a named computer is named; its controls are in details', (
      tester,
    ) async {
      // The fixture switches every capability on, controls included.
      final (controller, _) = await boot(url: 'https://dev-pc:7000');
      await pumpHome(tester, controller);
      expect(hostPhrase(tester), 'On dev-pc');
      await openDetails(tester);
      expect(find.text('Decisions and controls'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('team-home-host-read-only')),
        findsNothing,
      );
    });

    testWidgets('an address is not a name: "On your computer" (TEAM-115)', (
      tester,
    ) async {
      final (controller, _) = await boot(url: 'http://100.101.102.103:7000');
      await pumpHome(tester, controller);
      expect(hostPhrase(tester), 'On your computer');
      expect(find.textContaining('100.101.102.103'), findsNothing);
      expect(find.textContaining('Workstation'), findsNothing);
    });

    testWidgets('a phone host says so (TEAM-115)', (tester) async {
      final (controller, _) = await boot(
        hostMode: OrchestrationHostMode.phone,
        url: 'http://localhost:7000',
      );
      await pumpHome(tester, controller);
      expect(hostPhrase(tester), 'On this phone');
      expect(find.textContaining('localhost'), findsNothing);
    });
  });

  group('agents', () {});

  group('needs you', () {
    testWidgets('one question is its task row, and it opens the read-only '
        'sheet', (tester) async {
      // Without `controlRespond` the sheet stays Sprint A read-only; the
      // actions are TEAM-203's and tested in team_gate_answer_test.
      final (controller, _) = await boot(
        configure: (g) {
          blockedShape(g);
          g.capabilitiesOverride = OrchestrationCapabilities.gascityRead;
        },
      );
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(runRow('oc-xru'), findsOneWidget);
      await tester.tap(runRow('oc-xru'));
      await tester.pumpAndSettle();
      final sheet = find.byKey(const ValueKey('team-gate-sheet'));
      expect(sheet, findsOneWidget);
      expect(
        find.descendant(
          of: sheet,
          matching: find.text(
            'calc.py already defines subtract(). Replace it, keep it, or stop?',
          ),
        ),
        findsOneWidget,
      );
      for (final choice in ['replace', 'keep', 'stop']) {
        expect(
          find.descendant(of: sheet, matching: find.text(choice)),
          findsOneWidget,
        );
      }
      // Read-only: nothing to send.
      expect(find.byType(Radio<String>), findsNothing);
      expect(find.byType(TextField), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a phone host is named in the sheet', (tester) async {
      final (controller, _) = await boot(
        configure: (g) {
          blockedShape(g);
          g.capabilitiesOverride = OrchestrationCapabilities.gascityRead;
        },
        hostMode: OrchestrationHostMode.phone,
      );
      await pumpHome(tester, controller);
      await tester.tap(runRow('oc-xru'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('team-gate-sheet')),
          matching: find.text(
            'Answer this in the host on this phone. The app can only watch '
            'for now.',
          ),
        ),
        findsOneWidget,
      );
    });

    testWidgets('several questions are no section: a task\'s question is '
        'its row, the rest head the one list (owner rule 2026-09-27)', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) => g.gatesOverride = const [
          OrchestrationGate(
            id: 'gate',
            kind: GateKind.gateBead,
            title: 'Gate bead',
          ),
          OrchestrationGate(
            id: 'review',
            kind: GateKind.reviewReady,
            title: 'Review me',
          ),
          OrchestrationGate(
            id: 'failed',
            kind: GateKind.runFailed,
            title: 'Run failed',
            runId: 'oc-xru',
          ),
          OrchestrationGate(
            id: 'ask',
            kind: GateKind.freeText,
            title: 'Which branch?',
            prompt: 'main is frozen; dev has the fix.',
          ),
        ],
      );
      await pumpHome(tester, controller);
      // The questions head the page without a section label, and no
      // separate panel of questions sits above the list.
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      Finder row(String id) => find.byKey(ValueKey('team-home-gate-$id'));
      final tasks = find.byKey(const ValueKey('team-home-tasks'));
      // The failed task's question is its own row, once: the mark and
      // "Needs you · <question> · age", never a second row above.
      expect(row('failed'), findsNothing);
      expect(runRow('oc-xru'), findsOneWidget);
      expect(lineOf(tester, 'oc-xru'), startsWith('Needs you · Run failed'));
      // Questions with no task listed are rows of the same list, first,
      // most urgent first.
      final order = ['ask', 'review', 'gate'];
      for (final id in order) {
        expect(
          find.descendant(of: tasks, matching: row(id)),
          findsOneWidget,
          reason: '$id is a row of the one list',
        );
      }
      for (var i = 1; i < order.length; i++) {
        expect(
          top(tester, row(order[i - 1])),
          lessThan(top(tester, row(order[i]))),
          reason: '${order[i - 1]} above ${order[i]}',
        );
      }
      expect(top(tester, row('gate')), lessThan(top(tester, runRow('oc-xru'))));
      // The task's row opens the question, not the conversation.
      await tester.tap(runRow('oc-xru'));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-gate-sheet')), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
      await tester.pumpAndSettle();
      // A prompt that differs from the title is shown in the sheet.
      await tester.tap(row('ask'));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('team-gate-prompt')),
          matching: find.text('main is frozen; dev has the fix.'),
        ),
        findsOneWidget,
      );
    });

    testWidgets('nothing waiting: no section at all', (tester) async {
      final (controller, _) = await boot();
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-needs-you')), findsNothing);
      expect(find.text('Nothing needs you right now.'), findsNothing);
    });
  });

  group('stale, refresh, loading, error', () {
    testWidgets('stale: one status line with the time; Try again clears it', (
      tester,
    ) async {
      final (controller, gateway) = await boot();
      final refreshedAt = controller.lastRefreshedAt!;
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-stale')), findsNothing);

      clock = clock.add(const Duration(seconds: 61));
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-stale')), findsOneWidget);
      final time =
          MaterialLocalizations.of(
            tester.element(find.byType(TeamHomeScreen)),
          ).formatTimeOfDay(
            TimeOfDay.fromDateTime(refreshedAt.toLocal()),
            alwaysUse24HourFormat: true,
          );
      expect(find.textContaining(time), findsWidgets);
      // The host phrase says it too.
      expect(hostPhrase(tester), endsWith(' · Not answering'));

      final before = gateway.count('runs');
      await tester.tap(find.byKey(const ValueKey('team-home-status-retry')));
      await tester.pumpAndSettle();
      expect(gateway.count('runs'), before + 1);
      expect(find.byKey(const ValueKey('team-home-stale')), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('pull-to-refresh calls refresh once, on the home and on the '
        'agents list', (tester) async {
      final (controller, gateway) = await boot();
      await pumpHome(tester, controller);
      final before = gateway.count('runs');
      // The indicator arms at a quarter of the (tall) viewport.
      await tester.drag(
        find.byKey(const ValueKey('team-home-runs')),
        const Offset(0, 900),
      );
      await tester.pump();
      // The indicator's arm, settle and hide animations.
      await tester.pump(const Duration(seconds: 1));
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();
      expect(gateway.count('runs'), before + 1);
      await openAgents(tester);
      await tester.drag(
        find.byKey(const ValueKey('team-home-agents')),
        const Offset(0, 900),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();
      expect(gateway.count('runs'), before + 2);
      expect(tester.takeException(), isNull);
    });

    testWidgets('before the probe answers: loading', (tester) async {
      final (controller, _) = await boot(started: false);
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-loading')), findsOneWidget);
      expect(find.byKey(const ValueKey('team-home-data')), findsNothing);
      expect(find.text('AI Team'), findsOneWidget);
    });

    testWidgets('a failed probe shows honest copy and Try again recovers', (
      tester,
    ) async {
      ProbeVerdict verdict = const ProbeUnreachable(error: 'refused');
      final (controller, gateway) = await boot(probe: (_) async => verdict);
      expect(controller.phase, OrchestrationPhase.failed);
      await pumpHome(tester, controller);
      expect(find.byKey(const ValueKey('team-home-error')), findsOneWidget);
      // A host that could not be read is not an empty team: the failure
      // keeps "Try again" and never claims there are no tasks.
      expect(find.text('Try again'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-home-runs-empty')), findsNothing);
      expect(find.text('No recent tasks'), findsNothing);
      verdict = ProbeFound(host: gateway.host!, city: 'bright-lights');
      await tester.tap(find.byKey(const ValueKey('team-home-retry')));
      await tester.pumpAndSettle();
      expect(controller.phase, OrchestrationPhase.ready);
      expect(find.byKey(const ValueKey('team-home-data')), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });

  // TEAM-115: the host's internals stay out of the counts and lists.
  group('upkeep and suspended (TEAM-115)', () {
    OrchestrationRun upkeep(String name, RunState state, int minutesAgo) =>
        OrchestrationRun(
          id: 'oc-wisp-$name',
          title: 'mol-$name-patrol',
          state: state,
          kind: RunKind.formula,
          isUpkeep: true,
          updatedAt: clock.subtract(Duration(minutes: minutesAgo)),
        );

    testWidgets('upkeep runs are one line, never task rows', (tester) async {
      OrchestrationRun? opened;
      final (controller, _) = await boot(
        configure: (g) => g
          ..workOverride = const []
          ..gatesOverride = const []
          ..runsOverride = [
            upkeep('refinery', RunState.planning, 1),
            upkeep('deacon', RunState.planning, 2),
            upkeep('witness', RunState.completed, 3),
            run('mine', RunState.working, 4),
            run('done', RunState.completed, 5),
          ],
      );
      await pumpHome(tester, controller, onOpenRun: (r) => opened = r);
      expect(runRow('mine'), findsOneWidget);
      expect(find.textContaining('mol-'), findsNothing);
      for (final name in ['refinery', 'deacon', 'witness']) {
        expect(runRow('oc-wisp-$name'), findsNothing);
      }
      // What finished lists the person's task only.
      expect(runRow('done'), findsOneWidget);

      // Upkeep is one line in words in the team's own panel (owner, build
      // 2055): no switch, no rows of engine names, duplicates collapsed.
      expect(
        find.byKey(const ValueKey('team-home-upkeep-line')),
        findsNothing,
        reason: 'the work page carries no upkeep',
      );
      await openTeamSettingsFromHome(tester);
      final line = find.byKey(const ValueKey('team-home-upkeep-line'));
      expect(line, findsOneWidget);
      expect(find.byType(SwitchListTile), findsNothing);
      expect(
        find.text('Patrol ×3 · planning', findRichText: true),
        findsOneWidget,
      );
      expect(opened, isNull);
      expect(tester.takeException(), isNull);
    });

    testWidgets('only upkeep: the empty state, and the upkeep line', (
      tester,
    ) async {
      final (controller, _) = await boot(
        configure: (g) => g
          ..workOverride = const []
          ..gatesOverride = const []
          ..runsOverride = [upkeep('refinery', RunState.planning, 1)],
      );
      await pumpHome(tester, controller);
      expect(
        find.byKey(const ValueKey('team-home-runs-empty')),
        findsOneWidget,
      );
      expect(runRow('oc-wisp-refinery'), findsNothing);
      await openTeamSettingsFromHome(tester);
      expect(
        find.text('Patrol · planning', findRichText: true),
        findsOneWidget,
      );
    });
  });

  group('Arabic', () {
    testWidgets('the home reads in Arabic, engine words left out', (
      tester,
    ) async {
      final (controller, _) = await boot(configure: blockedShape);
      await tester.pumpWidget(
        app(
          TeamHomeScreen(controller: controller, now: () => clock),
          locale: const Locale('ar'),
        ),
      );
      await tester.pump();
      expect(find.text('فريق الذكاء الاصطناعي'), findsOneWidget);
      // The question first, then the tasks, in the person's words: no
      // engine names or versions on the home in any language.
      // The one-list redesign removed both section headings in every locale.
      expect(find.text('يحتاجك'), findsNothing);
      expect(find.text('المهام'), findsNothing);
      expect(runRow('oc-xru'), findsOneWidget);
      expect(hostPhrase(tester), startsWith('على '));
      for (final word in ['convoy', 'Gas City', '1.4.1', 'bright-lights']) {
        expect(find.textContaining(word), findsNothing, reason: word);
      }
      expect(tester.takeException(), isNull);
    });
  });
}

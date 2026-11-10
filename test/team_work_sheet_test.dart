// TEAM-110: the Work sheet over the fixture's `oc-loy` bead (the recorded
// state after the refinery took it) through the real mappers: title and
// term, state and owner, the description as markdown, dependency and
// blocking rows that open the other item's sheet in its place, no "Open
// this step's conversation" for Gas City (and one only with the capability, a
// link and a handler), timestamps, output excerpt and validation when
// present, the Technical details fold holding branch, worktree and every
// raw field in LTR mono (KIT-33), and the designed state for an item the
// host no longer lists (screen-team-3: the sheet in the kit sheet frame).

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_mappers.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';
import 'package:opencode_mobile/ui/screens/team/work_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

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

/// The fixture with a work override, optional capabilities and an owned
/// event stream.
class _Gateway implements OrchestrationGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  OrchestrationCapabilities? capabilitiesOverride;

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
  Future<List<OrchestrationProject>> projects() => inner.projects();
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) =>
      inner.runs(projectId: projectId);
  @override
  Future<OrchestrationRun?> run(String id) => inner.run(id);
  @override
  Future<List<WorkItem>> work({String? projectId}) async =>
      workOverride ?? await inner.work(projectId: projectId);
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) =>
      inner.readyWork(projectId: projectId);
  @override
  Future<WorkItem?> workItem(String id) => inner.workItem(id);
  @override
  Future<List<OrchestrationAgent>> agents() async =>
      agentsOverride ?? await inner.agents();
  @override
  Future<OrchestrationAgent?> agent(String id) => inner.agent(id);
  @override
  Future<List<OrchestrationGate>> gates() async => const [];
  @override
  Future<OrchestrationUsage?> usage() => inner.usage();
  @override
  Future<List<ActivityEvent>> activity({int? afterSeq, int limit = 100}) =>
      inner.activity(afterSeq: afterSeq, limit: limit);
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
  late OrchestrationStore store;
  late DateTime clock;

  setUp(() async {
    fixturePath = _findFixtureRoot().path;
    SharedPreferences.setMockInitialValues({});
    store = OrchestrationStore(await SharedPreferences.getInstance());
    clock = DateTime.utc(2026, 9, 11, 12, 30);
  });

  Map<String, Object?> recording(String name) => readMap(
    jsonDecode(File('$fixturePath/recordings/$name.json').readAsStringSync()),
  );

  /// The recorded `oc-loy` after the refinery took it (branch, worktree,
  /// session, merge target), blocked behind `oc-dep`, with `gc-2` waiting
  /// on it; every item under the fixture convoy `oc-xru`.
  List<WorkItem> fixtureWork({Map<String, Object?> extra = const {}}) {
    final loy = {
      ...recording('bead_handed_to_refinery'),
      'is_blocked': true,
      'dependencies': [
        {'issue_id': 'oc-loy', 'depends_on_id': 'oc-dep', 'type': 'blocks'},
      ],
      ...extra,
    };
    final beads = [
      GcBead.fromJson(loy),
      GcBead.fromJson(const {
        'id': 'oc-dep',
        'title': 'Agree the calc.py API',
        'status': 'open',
        'issue_type': 'task',
        'description': 'Settle the names before the code lands.',
      }),
      GcBead.fromJson(const {
        'id': 'gc-2',
        'title': 'Write tests for calc.py',
        'status': 'open',
        'issue_type': 'task',
        'dependencies': [
          {'issue_id': 'gc-2', 'depends_on_id': 'oc-loy', 'type': 'blocks'},
        ],
      }),
    ];
    return [
      for (final item in mapBeads(beads))
        WorkItem(
          id: item.id,
          title: item.title,
          state: item.state,
          rawState: item.rawState,
          runId: 'oc-xru',
          assignee: item.assignee,
          sessionId: item.sessionId,
          isBlocked: item.isBlocked,
          labels: item.labels,
          dependsOn: item.dependsOn,
          createdAt: item.createdAt,
          updatedAt: item.updatedAt,
          raw: item.raw,
        ),
    ];
  }

  Future<(OrchestrationController, _Gateway)> boot({
    void Function(_Gateway gateway)? configure,
  }) async {
    final gateway = _Gateway(
      FixtureOrchestrationGateway(fixturePath: fixturePath),
    );
    gateway.workOverride = fixtureWork();
    configure?.call(gateway);
    final config = OrchestrationConfig(
      provider: OrchestrationProvider.fixture,
      url: fixturePath,
      city: 'bright-lights',
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final controller = OrchestrationController(
      profile: ServerProfile(
        id: 'srv-1',
        name: 'Workstation',
        baseUrl: 'https://server.example:4096',
        orchestration: config,
      ),
      config: config,
      store: store,
      gatewayFactory: (_, _) => gateway,
      now: () => clock,
    );
    addTearDown(controller.dispose);
    await controller.start();
    return (controller, gateway);
  }

  /// A page with one button that opens the sheet for [workId].
  Future<void> pumpHost(
    WidgetTester tester,
    OrchestrationController controller,
    String workId, {
    ValueChanged<String>? onOpenSession,
    Size size = const Size(800, 1400),
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: Scaffold(
          body: Builder(
            builder: (context) => Center(
              child: FilledButton(
                key: const ValueKey('open'),
                onPressed: () => showWorkSheet(
                  context,
                  controller,
                  workId,
                  now: () => clock,
                  onOpenSession: onOpenSession,
                ),
                child: const Text('open'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('open')));
    await tester.pumpAndSettle();
  }

  Finder key(String name) => find.byKey(ValueKey(name));

  Future<void> closeSheet(WidgetTester tester) async {
    tester.state<NavigatorState>(find.byType(Navigator)).pop();
    await tester.pumpAndSettle();
    expect(key('team-work-sheet'), findsNothing);
  }

  Finder inSheet(Finder matching) =>
      find.descendant(of: key('team-work-sheet'), matching: matching);

  Future<void> reveal(WidgetTester tester, Finder target) async {
    await tester.scrollUntilVisible(
      target,
      160,
      scrollable: find
          .descendant(
            of: key('team-work-sheet'),
            matching: find.byType(Scrollable),
          )
          .first,
    );
    await tester.pumpAndSettle();
  }

  /// The words of the kit text keyed [name].
  String textOf(WidgetTester tester, String name) =>
      tester.widget<KitText>(key(name)).text;

  /// Opens the Technical details fold (it starts folded, KIT-33).
  Future<void> openDetails(WidgetTester tester) async {
    final fold = key('team-work-sheet-technical');
    await reveal(tester, fold);
    await tester.tap(fold);
    await tester.pumpAndSettle();
  }

  testWidgets('oc-loy: title, term, state, owner, description, code', (
    tester,
  ) async {
    final (controller, _) = await boot();
    await pumpHost(tester, controller, 'oc-loy');
    expect(key('team-work-sheet-oc-loy'), findsOneWidget);
    // The sheet's own title; the host's term and id wait under Technical
    // details, never in the subtitle.
    expect(
      inSheet(find.text('Add subtract function to calc.py')),
      findsOneWidget,
    );
    expect(
      inSheet(find.text('Work · bead oc-loy', findRichText: true)),
      findsNothing,
    );
    // The cycle strip carries the state; no second state line.
    expect(key('team-work-sheet-state'), findsNothing);
    // The owner by the agent's own name, not the host's handle.
    final owner = tester.widget<KitText>(key('team-work-sheet-owner'));
    expect(owner.text, 'refinery');

    // The description renders as markdown.
    expect(key('team-work-sheet-description'), findsOneWidget);
    expect(
      inSheet(find.textContaining('Add subtract(a, b)', findRichText: true)),
      findsWidgets,
    );

    // Branch, worktree and merge target live in Technical details, mono
    // and left to right (KIT-33): nothing technical above the fold.
    expect(key('team-work-sheet-branch'), findsNothing);
    await openDetails(tester);
    for (final (name, value) in [
      ('team-work-sheet-branch', 'polecat/oc-loy'),
      (
        'team-work-sheet-worktree',
        '/home/eslam/Storage/Code/gascity-spike/city2/.gc/worktrees/ocproof/'
            'polecats/gastown.furiosa',
      ),
      ('team-work-sheet-target', 'master'),
    ]) {
      await reveal(tester, key(name));
      final text = tester.widget<KitText>(key(name));
      expect(text.text, value);
      expect(text.role, KitTextRole.mono);
    }

    // Gas City links no OpenCode session: nothing is offered.
    expect(key('team-work-sheet-open-session'), findsNothing);
    expect(find.text("Open this step's conversation"), findsNothing);

    // Timestamps: created 18:44Z the day before the 12:30Z clock.
    await reveal(tester, key('team-work-sheet-created'));
    expect(
      find.descendant(
        of: key('team-work-sheet-created'),
        matching: find.textContaining('17h ago'),
      ),
      findsOneWidget,
    );
    expect(key('team-work-sheet-updated'), findsNothing);
    expect(key('team-work-sheet-output'), findsNothing);
    expect(key('team-work-sheet-validation'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('dependency and blocking rows open the other sheet', (
    tester,
  ) async {
    final (controller, _) = await boot();
    await pumpHost(tester, controller, 'oc-loy');
    final dependency = key('team-work-dependency-oc-dep');
    await reveal(tester, dependency);
    expect(
      find.descendant(
        of: dependency,
        matching: find.text('Agree the calc.py API'),
      ),
      findsOneWidget,
    );
    // The row says where the other item stands, in words.
    expect(
      find.descendant(
        of: dependency,
        matching: find.text('Queued', findRichText: true),
      ),
      findsOneWidget,
    );
    final blocking = key('team-work-blocking-gc-2');
    await reveal(tester, blocking);
    expect(
      find.descendant(
        of: blocking,
        matching: find.text('Write tests for calc.py'),
      ),
      findsOneWidget,
    );
    expect(key('team-work-dependency-gc-2'), findsNothing);

    await reveal(tester, dependency);
    await tester.tap(dependency);
    await tester.pumpAndSettle();
    expect(key('team-work-sheet-oc-loy'), findsNothing);
    expect(key('team-work-sheet-oc-dep'), findsOneWidget);
    expect(inSheet(find.text('Agree the calc.py API')), findsOneWidget);
    expect(textOf(tester, 'team-work-sheet-owner'), 'Unassigned');
    // oc-dep has no dependencies; oc-loy waits on it.
    expect(key('team-work-dependency-oc-loy'), findsNothing);
    await reveal(tester, key('team-work-blocking-oc-loy'));
    await tester.tap(key('team-work-blocking-oc-loy'));
    await tester.pumpAndSettle();
    expect(key('team-work-sheet-oc-loy'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('Technical details lists the raw fields with the ids LTR', (
    tester,
  ) async {
    final (controller, _) = await boot();
    await pumpHost(tester, controller, 'oc-loy');
    expect(find.text('Technical details'), findsOneWidget);
    expect(find.text('priority'), findsNothing);
    await openDetails(tester);
    // Each value once, under the first label that carries it (KIT-33):
    // the raw metadata.gc.session_id repeats the Session id and is not
    // listed twice.
    for (final label in const [
      'Work id',
      'Provider status',
      'Type',
      'Run id',
      'Assignee',
      'Session id',
      'Session name',
      'Depends on (ids)',
      'metadata.merge_strategy',
      'priority',
    ]) {
      await reveal(tester, inSheet(find.text(label)));
    }
    final session = find.byWidgetPredicate(
      (w) => w is KitText && w.text == 'bl-48k',
    );
    await reveal(tester, session.first);
    expect(tester.widget<KitText>(session.first).role, KitTextRole.mono);
    expect(
      find.byWidgetPredicate(
        (w) => w is KitText && w.text == 'gastown__polecat-bl-48k',
      ),
      findsWidgets,
    );
    expect(
      find.byWidgetPredicate((w) => w is KitText && w.text == 'oc-dep'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('Open session needs the capability, a link and a handler', (
    tester,
  ) async {
    final opened = <String>[];
    var (controller, gateway) = await boot(
      configure: (g) => g.workOverride = fixtureWork(
        extra: {
          'metadata': {
            ...readMap(recording('bead_handed_to_refinery')['metadata']),
            'opencode_session_id': 'ses_42',
          },
        },
      ),
    );
    await pumpHost(tester, controller, 'oc-loy', onOpenSession: opened.add);
    final button = key('team-work-sheet-open-session');
    await reveal(tester, button);
    // The action names what it opens.
    expect(find.text("Open this step's conversation"), findsOneWidget);
    await tester.tap(button);
    await tester.pump();
    expect(opened, ['ses_42']);

    // The same item without a handler, or from an adapter that cannot
    // link sessions, offers nothing.
    await closeSheet(tester);
    await pumpHost(tester, controller, 'oc-loy');
    expect(button, findsNothing);
    await closeSheet(tester);
    (controller, gateway) = await boot(
      configure: (g) => g
        ..capabilitiesOverride = const OrchestrationCapabilities(
          runs: true,
          workGraph: true,
        )
        ..workOverride = fixtureWork(
          extra: {
            'metadata': {'opencode_session_id': 'ses_42'},
          },
        ),
    );
    expect(gateway.capabilities.sessionLink, isFalse);
    await pumpHost(tester, controller, 'oc-loy', onOpenSession: opened.add);
    expect(button, findsNothing);
    expect(opened, ['ses_42']);
    expect(tester.takeException(), isNull);
  });

  testWidgets('output excerpt, validation and a closed stamp render', (
    tester,
  ) async {
    final (controller, _) = await boot(
      configure: (g) => g.workOverride = fixtureWork(
        extra: {
          'status': 'closed',
          'closed_at': '2026-09-11T11:30:00Z',
          'updated_at': '2026-09-11T11:30:00Z',
          'metadata': {
            'branch': 'polecat/oc-loy',
            'last_output': 'PASS test_calc.py::test_subtract\n2 passed',
            'validation': {'status': 'passed', 'summary': '2 of 2 tests'},
          },
        },
      ),
    );
    await pumpHost(tester, controller, 'oc-loy');
    final output = key('team-work-sheet-output');
    await reveal(tester, output);
    // Output is a kit code block: mono, left to right, copyable.
    expect(
      find.descendant(
        of: output,
        matching: find.textContaining('2 passed', findRichText: true),
      ),
      findsWidgets,
    );
    await reveal(tester, key('team-work-sheet-validation'));
    expect(inSheet(find.text('Passed')), findsOneWidget);
    expect(inSheet(find.text('2 of 2 tests')), findsOneWidget);
    await reveal(tester, key('team-work-sheet-updated'));
    expect(inSheet(find.text('Closed')), findsOneWidget);
    expect(inSheet(find.textContaining('1h ago')), findsNWidgets(2));
    expect(tester.takeException(), isNull);
  });

  testWidgets('an item the host no longer lists says so', (tester) async {
    final (controller, _) = await boot();
    await pumpHost(tester, controller, 'gone');
    expect(key('team-work-sheet-missing'), findsOneWidget);
    expect(find.text('Work item gone'), findsOneWidget);
    expect(
      find.text('This work item is no longer on the host.'),
      findsOneWidget,
    );
    expect(key('team-work-sheet-technical'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('owner comes from the agent on the item when there is one', (
    tester,
  ) async {
    final (controller, _) = await boot(
      configure: (g) => g.agentsOverride = const [
        OrchestrationAgent(
          id: 'wolf',
          name: 'wolf',
          state: AgentState.working,
          sessionId: 'bl-48k',
          currentWorkId: 'oc-loy',
        ),
      ],
    );
    await pumpHost(tester, controller, 'oc-loy');
    expect(textOf(tester, 'team-work-sheet-owner'), 'wolf');
    expect(tester.takeException(), isNull);
  });
}

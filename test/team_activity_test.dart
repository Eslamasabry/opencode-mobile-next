// TEAM-112: AI Team items in Activity and the read-only Gate sheet. The
// BRD §47 order with one item of each kind mixed with the app's own
// permission and question, per-server scoping (disconnecting removes the
// rows), every sheet variant with its "Answer this on the host" line and
// [How], the failed-run sheet over the `failed` fixture shape, the
// failure classifier, and the sheets at 320dp × 2.5x, LTR and RTL,
// English and Arabic.

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/dto/dto.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_mappers.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/widgets/team_vocabulary.dart';
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

/// The `failed` scenario's derived `/runs` entry (as in
/// team_controller_test).
const _failedRun = <String, Object?>{
  'run_id': 'oc-loy',
  'title': 'Add subtract() to calc.py',
  'status': 'failed',
  'scope': {'kind': 'rig', 'ref': 'ocproof'},
  'target': 'ocproof/polecats',
  'started_at': '2026-09-10T10:00:00Z',
  'updated_at': '2026-09-10T10:05:00Z',
  'last_error': {'code': 'fail', 'message': 'tests failed'},
};

/// The fixture with per-scope overrides and an owned event stream.
class _Gateway implements OrchestrationGateway {
  _Gateway(this.inner);

  final FixtureOrchestrationGateway inner;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  List<OrchestrationGate>? gatesOverride;
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
  Future<List<OrchestrationRun>> runs({String? projectId}) async =>
      runsOverride ?? await inner.runs(projectId: projectId);

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
  Future<List<OrchestrationGate>> gates() async =>
      gatesOverride ?? await inner.gates();

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

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// A connection whose plugin controller a test can set and clear, as the
/// real one does on connect and disconnect.
class _Connection extends ConnectionController {
  _Connection(super.store);

  OrchestrationController? team;
  ServerProfile? connected;

  @override
  OrchestrationController? get orchestration => team;

  @override
  ServerProfile? get profile => connected;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(projectManagement: false);

  @override
  bool get isConnected => status == StreamStatus.connected;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      repository;

  @override
  Future<void> refreshSessions() async {}
  @override
  Future<void> refreshPendingPermissions() async {}
  @override
  Future<void> refreshPendingQuestions() async {}
  @override
  Future<void> refreshPendingForms() async {}

  /// Rebuilds screens on the plugin's changes, as the real controller
  /// does through its listener.
  void attach(OrchestrationController? next) {
    team?.removeListener(notifyListeners);
    team = next?..addListener(notifyListeners);
  }

  void dropTeam() {
    attach(null);
    status = StreamStatus.disconnected;
    notifyListeners();
  }

  @override
  void dispose() {
    attach(null);
    super.dispose();
  }
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

  final profile = ServerProfile(
    id: 'srv-1',
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
  );

  /// The sheet tests here cover the Sprint A read-only variants, so the
  /// gateway advertises no `control*`; the actions (TEAM-203) are tested
  /// in team_gate_answer_test over the full fixture capabilities.
  Future<(OrchestrationController, _Gateway)> boot({
    void Function(_Gateway gateway)? configure,
    OrchestrationHostMode hostMode = OrchestrationHostMode.computer,
    bool readOnly = true,
  }) async {
    final gateway = _Gateway(
      FixtureOrchestrationGateway(fixturePath: fixturePath, hostMode: hostMode),
    );
    if (readOnly) {
      gateway.capabilitiesOverride = OrchestrationCapabilities.gascityRead;
    }
    configure?.call(gateway);
    final cfg = OrchestrationConfig(
      provider: OrchestrationProvider.fixture,
      url: fixturePath,
      city: 'bright-lights',
      hostMode: hostMode,
      enabledAt: DateTime.utc(2026, 9, 10),
    );
    final controller = OrchestrationController(
      profile: profile,
      config: cfg,
      store: store,
      gatewayFactory: (_, _) => gateway,
      now: () => clock,
    );
    addTearDown(controller.dispose);
    await controller.start();
    return (controller, gateway);
  }

  Future<_Connection> connect(OrchestrationController? team) async {
    final connection = _Connection(ProfileStore(prefs: prefs))
      ..repository = _Repository()
      ..status = StreamStatus.connected
      ..connected = profile
      ..attach(team);
    addTearDown(connection.dispose);
    connection.permissions = {
      'perm-1': PermissionRequest(
        id: 'perm-1',
        sessionID: 'ses_run',
        permission: 'edit',
        patterns: const ['lib/main.dart'],
      ),
    };
    connection.questions = {
      'q-1': const PendingQuestion(
        id: 'q-1',
        sessionID: 'ses_run',
        prompts: [
          QuestionPrompt(
            title: 'Direction',
            question: 'Proceed?',
            multiple: false,
            custom: true,
            choices: [],
          ),
        ],
      ),
    };
    return connection;
  }

  // ---------------------------------------------------------------------
  // Shapes
  // ---------------------------------------------------------------------

  /// One item of every kind: a decision (choice), a failed run, a review,
  /// a gate bead, a blocked agent; two work items of the failed run, one
  /// of which waits on the gate bead.
  void everyKind(_Gateway gateway) {
    final failed = mapRunsList(
      GcRunsList.fromJson({
        'runs': [_failedRun],
      }),
    ).items;
    final work = [
      const WorkItem(
        id: 'w-gate',
        title: 'Release approval',
        state: WorkState.ready,
        runId: 'oc-loy',
      ),
      const WorkItem(
        id: 'w-tests',
        title: 'Write tests for calc.py',
        state: WorkState.failed,
        runId: 'oc-loy',
        dependsOn: ['w-gate'],
      ),
      const WorkItem(
        id: 'w-done',
        title: 'Scaffold calc.py',
        state: WorkState.completed,
        runId: 'oc-loy',
      ),
    ];
    gateway
      ..runsOverride = failed
      ..workOverride = work
      ..agentsOverride = [
        OrchestrationAgent(
          id: 'a-bear',
          name: 'Bear',
          state: AgentState.blocked,
          currentWorkId: 'w-tests',
          lastActivity: clock.subtract(const Duration(minutes: 9)),
        ),
        OrchestrationAgent(
          id: 'a-wolf',
          name: 'Wolf',
          state: AgentState.waiting,
          currentWorkId: 'w-tests',
          lastActivity: clock.subtract(const Duration(minutes: 2)),
        ),
      ]
      ..gatesOverride = [
        OrchestrationGate(
          id: 'review:w-done',
          kind: GateKind.reviewReady,
          rawKind: 'needs-review',
          title: 'Scaffold calc.py',
          prompt: 'Diff of 2 files.',
          workId: 'w-done',
          createdAt: clock.subtract(const Duration(hours: 1)),
        ),
        OrchestrationGate(
          id: 'bead:w-gate',
          kind: GateKind.gateBead,
          rawKind: 'gate',
          title: 'Release approval',
          prompt: 'Someone signs off the **release** before tests run.',
          workId: 'w-gate',
          createdAt: clock.subtract(const Duration(minutes: 30)),
        ),
        for (final run in failed) ?gateFromRun(run),
        OrchestrationGate(
          id: 'req-1',
          kind: GateKind.choice,
          rawKind: 'choice',
          title: 'Which persistence strategy?',
          prompt: 'Pick one and the agent continues.',
          agentId: 'a-wolf',
          choices: const ['SQLite', 'Filesystem'],
          createdAt: clock.subtract(const Duration(minutes: 2)),
          raw: const {
            'request_id': 'req-1',
            'session_id': 'a-wolf',
            'kind': 'choice',
            'metadata': {'bead': 'w-tests'},
          },
        ),
      ];
  }

  /// Every interaction variant, for the sheet tests.
  void variants(_Gateway gateway) {
    gateway
      ..runsOverride = const []
      ..workOverride = const []
      ..agentsOverride = const []
      ..gatesOverride = [
        OrchestrationGate(
          id: 'choice',
          kind: GateKind.choice,
          title: 'Which persistence strategy?',
          prompt: 'Pick one and the agent continues.',
          choices: const ['SQLite', 'Filesystem', 'Server-only'],
          createdAt: clock.subtract(const Duration(minutes: 2)),
        ),
        OrchestrationGate(
          id: 'confirm',
          kind: GateKind.confirmation,
          title: 'Delete the old migrations?',
          prompt: 'This removes db/migrations/* for good.',
          createdAt: clock.subtract(const Duration(minutes: 3)),
        ),
        OrchestrationGate(
          id: 'confirm-safe',
          kind: GateKind.confirmation,
          title: 'Continue with the plan?',
          prompt: 'Three steps remain.',
          createdAt: clock.subtract(const Duration(minutes: 4)),
        ),
        OrchestrationGate(
          id: 'text',
          kind: GateKind.freeText,
          title: 'Which branch?',
          prompt: 'main is frozen; dev has the fix.',
          createdAt: clock.subtract(const Duration(minutes: 5)),
        ),
      ];
  }

  Widget app(
    Widget home, {
    Locale locale = const Locale('en'),
    TextDirection? direction,
    double textScale = 1,
  }) => MaterialApp(
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) {
      final media = MediaQuery(
        data: MediaQuery.of(context).copyWith(
          disableAnimations: true,
          textScaler: TextScaler.linear(textScale),
        ),
        child: child!,
      );
      return direction == null
          ? media
          : Directionality(textDirection: direction, child: media);
    },
    home: home,
  );

  // The Inbox that listed gates is gone; the sheet is still opened from the
  // conversation and Team cards. This host stands in for them: one button
  // per gate the snapshot holds, opening the sheet as those cards do.
  Future<void> pumpActivity(WidgetTester tester, _Connection connection) async {
    tester.view.physicalSize = const Size(800, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final team = connection.team!;
    await tester.pumpWidget(
      app(
        Scaffold(
          body: ListenableBuilder(
            listenable: team,
            builder: (context, _) => ListView(
              children: [
                for (final gate in team.snapshot.gates)
                  TextButton(
                    key: ValueKey('activity-team-gate-${gate.id}'),
                    onPressed: () =>
                        showGateSheet(context, team, gate.id, now: () => clock),
                    child: const Text('open gate'),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pump();
  }

  Finder gateRow(String id) => find.byKey(ValueKey('activity-team-gate-$id'));
  double top(WidgetTester tester, Finder finder) =>
      tester.getTopLeft(finder).dy;

  final sheet = find.byKey(const ValueKey('team-gate-sheet'));

  Future<void> open(WidgetTester tester, Finder row) async {
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(sheet, findsOneWidget);
  }

  // ---------------------------------------------------------------------
  // Gate sheet variants
  // ---------------------------------------------------------------------

  group('gate sheet', () {
    final answer = find.text(
      'Answer this on the host. The phone can only watch for now.',
    );

    testWidgets('choice: the prompt and a static option list', (tester) async {
      final (team, _) = await boot(configure: variants);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('choice'));
      expect(
        find.descendant(
          of: sheet,
          matching: find.text('Which persistence strategy?'),
        ),
        findsOneWidget,
      );
      expect(
        tester
            .widget<KitText>(find.byKey(const ValueKey('team-gate-prompt')))
            .text,
        'Pick one and the agent continues.',
      );
      for (final choice in ['SQLite', 'Filesystem', 'Server-only']) {
        expect(
          find.descendant(of: sheet, matching: find.text(choice)),
          findsOneWidget,
        );
      }
      expect(find.byKey(const ValueKey('team-gate-option-2')), findsOneWidget);
      expect(answer, findsOneWidget);
      // Read-only: no radios, no composer, no send.
      expect(find.byType(Radio<String>), findsNothing);
      expect(find.byType(TextField), findsNothing);
      expect(find.text('Send'), findsNothing);
      expect(find.text('Approve'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('[How] opens the host guide', (tester) async {
      final (team, _) = await boot(configure: variants);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('text'));
      await tester.tap(find.byKey(const ValueKey('team-gate-how')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('team-host-guide')), findsOneWidget);
    });

    testWidgets('confirmation: a destructive prompt takes the error tone', (
      tester,
    ) async {
      final (team, _) = await boot(configure: variants);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('confirm'));
      expect(
        find.byKey(const ValueKey('team-gate-destructive')),
        findsOneWidget,
      );
      // The warning is its own failure-toned notice; the prompt reads plain.
      final prompt = tester.widget<KitText>(
        find.byKey(const ValueKey('team-gate-prompt')),
      );
      expect(prompt.text, 'This removes db/migrations/* for good.');
      expect(answer, findsOneWidget);
      expect(find.text('Approve'), findsNothing);
      expect(find.text('Cancel'), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);

      // A plain confirmation is not marked.
      await open(tester, gateRow('confirm-safe'));
      expect(find.byKey(const ValueKey('team-gate-destructive')), findsNothing);
      expect(find.byKey(const ValueKey('team-gate-prompt')), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('free text: the prompt and no composer', (tester) async {
      final (team, _) = await boot(configure: variants);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('text'));
      expect(
        find.descendant(of: sheet, matching: find.text('Which branch?')),
        findsOneWidget,
      );
      expect(
        tester
            .widget<KitText>(find.byKey(const ValueKey('team-gate-prompt')))
            .text,
        'main is frozen; dev has the fix.',
      );
      expect(find.byType(TextField), findsNothing);
      expect(answer, findsOneWidget);
      // Technical details: the request id; a session id only when the gate
      // names one (this one does not).
      await tester.tap(find.byKey(const ValueKey('team-gate-technical')));
      await tester.pumpAndSettle();
      expect(find.text('Request id'), findsOneWidget);
      expect(find.text('Session id'), findsNothing);
      expect(find.text('text'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('gate bead: description, what it unblocks, close on host', (
      tester,
    ) async {
      final (team, _) = await boot(configure: everyKind);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('bead:w-gate'));
      expect(
        find.byKey(const ValueKey('team-gate-description')),
        findsOneWidget,
      );
      expect(find.textContaining('signs off the'), findsOneWidget);
      final chip = find.byKey(const ValueKey('team-gate-unblocks-w-tests'));
      expect(chip, findsOneWidget);
      expect(
        find.text('Close this on the host. The phone can only watch for now.'),
        findsOneWidget,
      );
      expect(find.text('Mark done'), findsNothing);

      // The chip closes this sheet and opens the Work sheet.
      await tester.tap(chip);
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      expect(
        find.byKey(const ValueKey('team-work-sheet-w-tests')),
        findsOneWidget,
      );
      expect(tester.takeException(), isNull);
    });

    testWidgets(
      'run failed: classification, affected work, recoverable, action',
      (tester) async {
        final (team, _) = await boot(configure: everyKind);
        final connection = await connect(team);
        await pumpActivity(tester, connection);
        await open(tester, gateRow('run:oc-loy'));
        // Titled after the task it stopped; the failure is said once.
        expect(find.text('Add subtract() to calc.py stopped'), findsOneWidget);
        expect(
          find.descendant(
            of: find.byKey(const ValueKey('team-gate-classification')),
            matching: find.text('Test'),
          ),
          findsOneWidget,
        );
        // Open items of the run, stuck ones first; the completed one is
        // not affected.
        expect(
          find.byKey(const ValueKey('team-gate-affected-w-tests')),
          findsOneWidget,
        );
        expect(
          find.byKey(const ValueKey('team-gate-affected-w-gate')),
          findsOneWidget,
        );
        expect(
          find.byKey(const ValueKey('team-gate-affected-w-done')),
          findsNothing,
        );
        expect(
          top(tester, find.byKey(const ValueKey('team-gate-affected-w-tests'))),
          lessThanOrEqualTo(
            top(
              tester,
              find.byKey(const ValueKey('team-gate-affected-w-gate')),
            ),
          ),
        );
        // A failure a retry cannot recover offers no retry, and no row
        // says so a second time.
        expect(
          find.byKey(const ValueKey('team-gate-recoverable')),
          findsNothing,
        );
        expect(find.byKey(const ValueKey('team-gate-run-retry')), findsNothing);
        expect(
          find.descendant(
            of: find.byKey(const ValueKey('team-gate-action')),
            matching: find.text(
              'Fix the failing tests on the host, then retry the run.',
              findRichText: true,
            ),
          ),
          findsOneWidget,
        );
        expect(answer, findsOneWidget);
        for (final button in ['Retry', 'Restart agent', 'Reassign']) {
          expect(find.text(button), findsNothing);
        }
        expect(tester.takeException(), isNull);
      },
    );

    testWidgets('a phone host is named in the sheet', (tester) async {
      final (team, _) = await boot(
        configure: variants,
        hostMode: OrchestrationHostMode.phone,
      );
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('choice'));
      expect(
        find.text(
          'Answer this in the host on this phone. The app can only watch '
          'for now.',
        ),
        findsOneWidget,
      );
    });

    testWidgets('a gate answered on the host meanwhile says so', (
      tester,
    ) async {
      final (team, gateway) = await boot(configure: variants);
      final connection = await connect(team);
      await pumpActivity(tester, connection);
      await open(tester, gateRow('choice'));
      gateway.gatesOverride = const [];
      await team.refresh();
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('team-gate-sheet-missing')),
        findsOneWidget,
      );
      expect(gateRow('choice'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Classifier
  // ---------------------------------------------------------------------

  group('failure classification', () {
    test('reads the family from the words', () {
      expect(teamClassifyFailure('tests failed'), TeamFailureClass.test);
      expect(
        teamClassifyFailure('CONFLICT (content): merge conflict in a.py'),
        TeamFailureClass.mergeConflict,
      );
      expect(
        teamClassifyFailure('401 Unauthorized: invalid API key'),
        TeamFailureClass.authentication,
      );
      expect(
        teamClassifyFailure('prompt exceeds the context window'),
        TeamFailureClass.context,
      );
      expect(
        teamClassifyFailure("ModuleNotFoundError: No module named 'yaml'"),
        TeamFailureClass.dependency,
      );
      expect(
        teamClassifyFailure('connection refused by 127.0.0.1:8372'),
        TeamFailureClass.infrastructure,
      );
      expect(
        teamClassifyFailure('command exited with code 2'),
        TeamFailureClass.execution,
      );
      expect(
        teamClassifyFailure('agent session died without output'),
        TeamFailureClass.agent,
      );
      expect(teamClassifyFailure(null), TeamFailureClass.unknown);
      expect(teamClassifyFailure('  '), TeamFailureClass.unknown);
      expect(teamClassifyFailure('fail'), TeamFailureClass.unknown);
    });

    test('the more specific family wins', () {
      expect(
        teamClassifyFailure('tests failed after a merge conflict'),
        TeamFailureClass.mergeConflict,
      );
      expect(teamClassifyFailure('tests timed out'), TeamFailureClass.test);
    });

    test('recoverable and action follow the class', () {
      expect(teamFailureRecoverable(TeamFailureClass.infrastructure), isTrue);
      expect(teamFailureRecoverable(TeamFailureClass.mergeConflict), isFalse);
      expect(teamFailureRecoverable(TeamFailureClass.unknown), isNull);
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(
        teamFailureAction(l10n, TeamFailureClass.unknown),
        contains('cannot act on it yet'),
      );
      expect(teamFailureClassWord(l10n, TeamFailureClass.context), 'Context');
    });

    test('destructive confirmations are read from the flag or the words', () {
      expect(
        teamGateIsDestructive(
          const OrchestrationGate(
            id: 'a',
            kind: GateKind.confirmation,
            title: 'Overwrite config.yaml?',
          ),
        ),
        isTrue,
      );
      expect(
        teamGateIsDestructive(
          const OrchestrationGate(
            id: 'b',
            kind: GateKind.confirmation,
            title: 'Continue?',
            raw: {'destructive': true},
          ),
        ),
        isTrue,
      );
      expect(
        teamGateIsDestructive(
          const OrchestrationGate(
            id: 'c',
            kind: GateKind.confirmation,
            title: 'Delete the cache?',
            raw: {'destructive': false},
          ),
        ),
        isFalse,
        reason: 'the host\'s flag wins over the words',
      );
      expect(
        teamGateIsDestructive(
          const OrchestrationGate(
            id: 'd',
            kind: GateKind.confirmation,
            title: 'Continue with the plan?',
          ),
        ),
        isFalse,
      );
    });
  });

  // ---------------------------------------------------------------------
  // Layout: 320dp × 2.5x, LTR and RTL, English and Arabic
  // ---------------------------------------------------------------------

  group('layout', () {
    Future<void> pumpSheet(
      WidgetTester tester,
      OrchestrationController team,
      String gateId,
      TextDirection direction,
      Locale locale,
    ) async {
      tester.view.physicalSize = const Size(320, 740);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        app(
          Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: TextButton(
                  key: const ValueKey('open'),
                  onPressed: () =>
                      showGateSheet(context, team, gateId, now: () => clock),
                  child: const Text('open'),
                ),
              ),
            ),
          ),
          locale: locale,
          direction: direction,
          textScale: 2.5,
        ),
      );
      await tester.pump();
      await tester.tap(find.byKey(const ValueKey('open')));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(sheet, findsOneWidget);
      expect(tester.getSize(sheet).width, 320);
    }

    /// Scrolls body content or the sheet's scrolling header into view.
    Future<void> reveal(WidgetTester tester, Finder target) async {
      final scrollable = find
          .descendant(of: sheet, matching: find.byType(Scrollable))
          .first;
      await tester.scrollUntilVisible(target, 150, scrollable: scrollable);
      await tester.pumpAndSettle();
      // KitSheet keeps its header outside the scrollable render subtree,
      // moving it with the body's offset at large text. ensureVisible alone
      // cannot bring that header back: scroll the body toward the top.
      for (var i = 0; i < 30 && target.hitTestable().evaluate().isEmpty; i++) {
        await tester.drag(scrollable, const Offset(0, 250));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      expect(target, findsOneWidget);
      final rect = tester.getRect(target);
      expect(rect.left, greaterThanOrEqualTo(0));
      expect(rect.right, lessThanOrEqualTo(320));
      expect(target.hitTestable(), findsOneWidget);
    }

    for (final direction in TextDirection.values) {
      for (final locale in const [Locale('en'), Locale('ar')]) {
        final tag = '${direction.name} ${locale.languageCode}';

        testWidgets('320dp 2.5x $tag: choice, confirmation and free text fit', (
          tester,
        ) async {
          final (team, _) = await boot(configure: variants);
          for (final id in ['choice', 'confirm', 'text']) {
            await pumpSheet(tester, team, id, direction, locale);
            if (id == 'choice') {
              await reveal(
                tester,
                find.byKey(const ValueKey('team-gate-option-2')),
              );
            }
            if (id == 'confirm') {
              await reveal(
                tester,
                find.byKey(const ValueKey('team-gate-destructive')),
              );
            }
            await reveal(
              tester,
              find.byKey(const ValueKey('team-gate-answer-on-host')),
            );
            final how = find.byKey(const ValueKey('team-gate-how'));
            await reveal(tester, how);
            expect(tester.getSize(how).height, greaterThanOrEqualTo(48));
            final close = find.byKey(const ValueKey('kit-sheet-close'));
            await reveal(tester, close);
            expect(tester.getSize(close).height, greaterThanOrEqualTo(48));
            await tester.tap(close);
            await tester.pumpAndSettle();
            expect(sheet, findsNothing);
            expect(tester.takeException(), isNull);
          }
        });

        testWidgets('320dp 2.5x $tag: gate bead and run failed fit', (
          tester,
        ) async {
          final (team, _) = await boot(configure: everyKind);
          await pumpSheet(tester, team, 'bead:w-gate', direction, locale);
          final unblocks = find.byKey(
            const ValueKey('team-gate-unblocks-w-tests'),
          );
          await reveal(tester, unblocks);
          // Within the sheet's 16 dp rails (design standard §1; was 20).
          expect(tester.getSize(unblocks).width, lessThanOrEqualTo(288));
          await reveal(
            tester,
            find.byKey(const ValueKey('team-gate-answer-on-host')),
          );
          await reveal(tester, find.byKey(const ValueKey('kit-sheet-close')));
          await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
          await tester.pumpAndSettle();
          expect(sheet, findsNothing);

          await pumpSheet(tester, team, 'run:oc-loy', direction, locale);
          for (final key in [
            'team-gate-classification',
            'team-gate-affected-w-tests',
            'team-gate-action',
            'team-gate-answer-on-host',
          ]) {
            await reveal(tester, find.byKey(ValueKey(key)));
          }
          // Technical details expand and their ids stay LTR.
          final technical = find.byKey(const ValueKey('team-gate-technical'));
          await reveal(tester, technical);
          await tester.tap(technical);
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          // The raw error sits in Technical details and fits the sheet.
          await reveal(tester, find.byKey(const ValueKey('team-gate-error')));
          await reveal(tester, find.byKey(const ValueKey('kit-sheet-close')));
          expect(tester.takeException(), isNull);
        });
      }
    }
  });
}

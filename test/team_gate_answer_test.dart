// TEAM-203: answering decisions and closing gates from Activity, with
// receipts, plus the four notification kinds. The Gate sheet runs over a
// scripted gateway (every control on, no network): a choice routes to
// exactly the gate's request id, free text keeps the `text` action, a
// confirmation's deny and a destructive approve are two-step in red, a
// gate bead marks done, a failed run retries (re-sling) or cancels (two-
// step). Receipts: sent (action off), confirmed (row leaves, sheet closes
// after a beat), unconfirmed (+Retry, a new key, the old record marked
// retriedBy), rejected (host message + Try again). Controls are absent
// without capabilities. Notifications post once per gate with ids only,
// respect the background toggle, and a tap opens the exact sheet without
// sending. Layout at 320dp × 2.5x, LTR and RTL, English and Arabic.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/background/live_background.dart';
import 'package:opencode_mobile/background/team_alerts.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/team_link.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/orchestration/adapters/gascity/gascity_control.dart';
import 'package:opencode_mobile/platform/session_link.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitReceipt;
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:opencode_mobile/ui/screens/team/team_home_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen, TeamWatchLiveScreen;
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';

const _profileId = 'srv-1';

/// One recorded control call.
class _Call {
  const _Call(this.verb, this.target, this.requestId, {this.arg});

  final String verb;
  final String target;
  final String requestId;
  final Object? arg;

  @override
  String toString() => '$verb $target $requestId ${arg ?? ''}';
}

/// A gateway with configurable capabilities, list-backed reads, a pushable
/// event stream and a scripted answer per control call.
class _Gateway implements OrchestrationGateway {
  _Gateway({this.capabilities = OrchestrationCapabilities.fixture});

  @override
  final OrchestrationCapabilities capabilities;
  final gateList = <OrchestrationGate>[];
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final agentList = <OrchestrationAgent>[];
  final calls = <_Call>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  Future<MutationReceipt> Function(_Call call)? answer;
  bool _closed = false;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'fixture',
    url: 'fixture://scripted',
    hostMode: OrchestrationHostMode.computer,
  );

  @override
  bool get isClosed => _closed;

  @override
  Future<void> close() async {
    _closed = true;
    await stream.close();
  }

  void push(OrchestrationEvent event) => stream.add(event);

  Future<MutationReceipt> _call(_Call call) {
    calls.add(call);
    final script = answer;
    if (script == null) {
      return Future.value(
        MutationReceipt(
          id: call.requestId,
          status: MutationReceiptStatus.accepted,
          upstreamStatus: 202,
        ),
      );
    }
    return script(call);
  }

  @override
  Future<List<OrchestrationProject>> projects() async => const [];
  @override
  Future<List<OrchestrationRun>> runs({String? projectId}) async => runList;
  @override
  Future<OrchestrationRun?> run(String id) async {
    for (final r in runList) {
      if (r.id == id) return r;
    }
    return null;
  }

  @override
  Future<List<WorkItem>> work({String? projectId}) async => workList;
  @override
  Future<List<WorkItem>> readyWork({String? projectId}) async => const [];
  @override
  Future<WorkItem?> workItem(String id) async {
    for (final w in workList) {
      if (w.id == id) return w;
    }
    return null;
  }

  @override
  Future<List<OrchestrationAgent>> agents() async => agentList;
  @override
  Future<OrchestrationAgent?> agent(String id) async {
    for (final a in agentList) {
      if (a.id == id) return a;
    }
    return null;
  }

  @override
  Future<List<OrchestrationGate>> gates() async => gateList;
  @override
  Future<OrchestrationUsage?> usage() async => null;
  @override
  Future<List<ActivityEvent>> activity({
    int? afterSeq,
    int limit = 100,
  }) async => const [];

  @override
  Stream<OrchestrationEvent> events({
    EventCursor resumeFrom = EventCursor.none,
  }) => stream.stream;

  @override
  Future<MutationReceipt> respond(
    String gateId,
    GateResponse response, {
    required String requestId,
  }) => _call(_Call('respond', gateId, requestId, arg: response));

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => _call(_Call('message', agentId, requestId, arg: text));

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _call(_Call('controlAgent', agentId, requestId, arg: action));

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _call(_Call('cancelRun', runId, requestId));

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _call(_Call('assign', workId, requestId, arg: agentId));

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _call(_Call('createWork', title, requestId, arg: projectId));
}

class _Repository implements ProductRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost:4096');

  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
}

class _MemoryProfileStore extends ProfileStore {
  _MemoryProfileStore({required super.prefs, required this.saved});

  final ServerProfile saved;
  String? selectedId;

  @override
  List<ServerProfile> get profiles => [saved];

  @override
  String? get activeId => selectedId;

  @override
  Future<void> setActiveId(String? id) async {
    selectedId = id;
  }
}

/// A connection whose plugin controller a test sets, as the real one does
/// on connect.
class _Connection extends ConnectionController {
  _Connection(super.store, {super.backgroundLive});

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

  void attach(OrchestrationController? next) {
    team?.removeListener(notifyListeners);
    team = next?..addListener(notifyListeners);
  }

  @override
  void dispose() {
    attach(null);
    super.dispose();
  }
}

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;
  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;
  @override
  Future<void> downloadUpdate() async {}
}

typedef _NativeCall = ({String method, Map<String, dynamic>? arguments});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late SharedPreferences prefs;
  late OrchestrationStore store;
  late DateTime clock;
  var nextKey = 0;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = await SharedPreferences.getInstance();
    store = OrchestrationStore(prefs);
    clock = DateTime.utc(2026, 9, 11, 12, 30);
    nextKey = 0;
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
    id: _profileId,
    name: 'Workstation',
    baseUrl: 'https://server.example:4096',
  );

  // ---------------------------------------------------------------------
  // Shapes
  // ---------------------------------------------------------------------

  OrchestrationGate choiceGate() => OrchestrationGate(
    id: 'req-1',
    kind: GateKind.choice,
    rawKind: 'choice',
    title: 'Which persistence strategy?',
    prompt: 'Pick one and the agent continues.',
    agentId: 'a-wolf',
    choices: const ['SQLite', 'Filesystem'],
    createdAt: clock.subtract(const Duration(minutes: 2)),
    raw: const {'request_id': 'req-1', 'session_id': 'a-wolf'},
  );

  OrchestrationGate textGate() => OrchestrationGate(
    id: 'req-text',
    kind: GateKind.freeText,
    title: 'Which branch?',
    prompt: 'main is frozen; dev has the fix.',
    createdAt: clock.subtract(const Duration(minutes: 5)),
  );

  OrchestrationGate confirmGate({bool destructive = false}) =>
      OrchestrationGate(
        id: destructive ? 'req-destroy' : 'req-safe',
        kind: GateKind.confirmation,
        title: destructive
            ? 'Delete the old migrations?'
            : 'Continue with the plan?',
        prompt: destructive
            ? 'This removes db/migrations/* for good.'
            : 'Three steps remain.',
        createdAt: clock.subtract(const Duration(minutes: 3)),
      );

  OrchestrationGate beadGate() => OrchestrationGate(
    id: 'bead:w-gate',
    kind: GateKind.gateBead,
    rawKind: 'gate',
    title: 'Release approval',
    prompt: 'Someone signs off the release.',
    workId: 'w-gate',
    createdAt: clock.subtract(const Duration(minutes: 30)),
  );

  OrchestrationRun failedRun() => OrchestrationRun(
    id: 'oc-loy',
    title: 'Add subtract() to calc.py',
    state: RunState.failed,
    lastError: 'tests failed',
    updatedAt: clock.subtract(const Duration(minutes: 5)),
    raw: const {'run_id': 'oc-loy', 'target': 'ocproof/polecats'},
  );

  OrchestrationGate runGate({String prompt = 'tests failed'}) =>
      OrchestrationGate(
        id: 'run:oc-loy',
        kind: GateKind.runFailed,
        rawKind: 'failed',
        title: 'Add subtract() to calc.py',
        prompt: prompt,
        runId: 'oc-loy',
        createdAt: clock.subtract(const Duration(minutes: 5)),
        raw: const {'run_id': 'oc-loy', 'target': 'ocproof/polecats'},
      );

  void runShape(_Gateway gateway, {String prompt = 'tests failed'}) {
    gateway.runList.add(failedRun());
    gateway.workList.addAll(const [
      WorkItem(
        id: 'w-tests',
        title: 'Write tests for calc.py',
        state: WorkState.failed,
        runId: 'oc-loy',
        assignee: 'a-wolf',
      ),
      WorkItem(
        id: 'w-done',
        title: 'Scaffold calc.py',
        state: WorkState.completed,
        runId: 'oc-loy',
      ),
    ]);
    gateway.agentList.add(
      OrchestrationAgent(
        id: 'a-wolf',
        name: 'Wolf',
        state: AgentState.waiting,
        sessionId: 'ses-wolf',
        currentWorkId: 'w-tests',
        lastActivity: clock.subtract(const Duration(minutes: 2)),
      ),
    );
    gateway.gateList.add(runGate(prompt: prompt));
  }

  Future<(OrchestrationController, _Gateway)> boot({
    void Function(_Gateway gateway)? configure,
    OrchestrationCapabilities capabilities = OrchestrationCapabilities.fixture,
    Duration mutationTimeout = const Duration(seconds: 60),
    bool start = true,
  }) async {
    final gateway = _Gateway(capabilities: capabilities);
    configure?.call(gateway);
    final controller = OrchestrationController(
      profile: profile,
      config: const OrchestrationConfig(
        provider: OrchestrationProvider.fixture,
        url: 'fixture://scripted',
        city: 'bright-lights',
      ),
      store: store,
      gatewayFactory: (_, _) => gateway,
      now: () => clock,
      mintKey: () => 'key-${++nextKey}',
      mutationTimeout: mutationTimeout,
    );
    addTearDown(controller.dispose);
    if (start) await controller.start();
    return (controller, gateway);
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

  final sheet = find.byKey(const ValueKey('team-gate-sheet'));
  final send = find.byKey(const ValueKey('team-gate-send'));
  final receiptLine = find.byType(KitReceipt);
  final retry = find.byKey(const ValueKey('team-gate-retry'));
  final confirmSheet = find.byKey(const ValueKey('team-gate-confirm'));
  final confirmYes = find.byKey(const ValueKey('team-gate-confirm-yes'));

  /// Pumps a page with a button that opens the sheet for [gateId].
  Future<void> pumpSheet(
    WidgetTester tester,
    OrchestrationController team,
    String gateId, {
    Size size = const Size(800, 1600),
    Locale locale = const Locale('en'),
    TextDirection? direction,
    double textScale = 1,
  }) async {
    tester.view.physicalSize = size;
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
        textScale: textScale,
      ),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('open')));
    await tester.pumpAndSettle();
    expect(sheet, findsOneWidget);
  }

  // The keys sit on the kit's buttons (design standard §2); the Material
  // button inside carries the enabled state.
  bool enabled(WidgetTester tester, Finder finder) =>
      tester
          .widget<ButtonStyleButton>(
            find.descendant(
              of: finder,
              matching: find.bySubtype<ButtonStyleButton>(),
            ),
          )
          .onPressed !=
      null;

  // The sheet keys its receipt by the answer's status; the receipt's
  // words also follow the wall clock (a slow wait reads "Not confirmed
  // yet"), so tests read the status.
  Finder receiptOf(String status) =>
      find.byKey(ValueKey('team-gate-receipt-$status'));

  // ---------------------------------------------------------------------
  // Choice
  // ---------------------------------------------------------------------

  group('choice', () {
    testWidgets('Send routes the option to exactly the gate\'s request id', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
      );
      await pumpSheet(tester, team, 'req-1');
      expect(
        find.byKey(const ValueKey('team-gate-answer-on-host')),
        findsNothing,
      );
      // One choice acts on tap (KIT-25): no separate Send.
      expect(send, findsNothing);

      await tester.tap(find.byKey(const ValueKey('team-gate-option-1')));
      await tester.pump();

      // Exactly one send, to the gate's own id, with the chosen option.
      expect(gateway.calls, hasLength(1));
      final call = gateway.calls.single;
      expect(call.verb, 'respond');
      expect(call.target, 'req-1');
      expect((call.arg as GateResponse).choice, 'Filesystem');
      expect(call.requestId, 'key-1');
      final record = team.mutationFor('req-1')!;
      expect(record.key, 'key-1');
      expect(record.request.targetId, 'req-1');
      expect(record.request.kind, MutationKind.respond);
      expect(record.status, MutationStatus.sent);

      // The receipt shows inline while sent.
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('a confirmed answer says Answered and closes the sheet', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);

      gateway.push(const GateChanged(gateId: 'req-1', resolved: true));
      await tester.pump();
      await tester.pump();
      expect(team.mutationFor('req-1')!.status, MutationStatus.confirmed);
      expect(receiptOf('confirmed'), findsOneWidget);
      expect(sheet, findsOneWidget);
      await tester.pump(gateSheetAnsweredBeat);
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Free text
  // ---------------------------------------------------------------------

  group('free text', () {
    testWidgets('the composer sends the text under action "text"', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(textGate()),
      );
      await pumpSheet(tester, team, 'req-text');
      final composer = find.byKey(const ValueKey('team-gate-composer'));
      expect(composer, findsOneWidget);
      expect(enabled(tester, send), isFalse);
      await tester.enterText(composer, '  dev, the fix is there  ');
      await tester.pump();
      expect(enabled(tester, send), isTrue);
      await tester.tap(send);
      await tester.pump();
      final call = gateway.calls.single;
      expect(call.target, 'req-text');
      final response = call.arg as GateResponse;
      expect(response.text, 'dev, the fix is there');
      expect(response.choice, isNull);
      expect(respondAction(response), 'text');
      expect(
        team.mutationFor('req-text')!.request.text,
        'dev, the fix is there',
      );
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Confirmation
  // ---------------------------------------------------------------------

  group('confirmation', () {
    testWidgets('Approve on a safe prompt is one tap; so is Deny', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(confirmGate()),
      );
      await pumpSheet(tester, team, 'req-safe');
      final approve = find.byKey(const ValueKey('team-gate-approve'));
      final deny = find.byKey(const ValueKey('team-gate-deny'));
      final error = AppTheme.dark().colorScheme.error;
      // The one red fill is dangerFill (visual language §5).
      final fill = AppTheme.rolesOf(AppTheme.dark()).dangerFill;
      // The safe approve is the plain primary; deny takes the error tone.
      // Design standard §2: approve is the kit's primary, deny its
      // secondary (tonal, error-coloured), no longer an outlined button.
      final approveStyle = tester
          .widget<FilledButton>(
            find.descendant(of: approve, matching: find.byType(FilledButton)),
          )
          .style!;
      expect(approveStyle.backgroundColor?.resolve({}), isNot(fill));
      final denyStyle = tester
          .widget<FilledButton>(
            find.descendant(of: deny, matching: find.byType(FilledButton)),
          )
          .style!;
      // Denying is not destructive: the neutral tone, no red.
      expect(denyStyle.foregroundColor?.resolve({}), isNot(error));

      // Deny sends at once, as on the card (slice-P4.1c): saying no loses
      // nothing, so no second step.
      await tester.tap(deny);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsNothing);
      expect(gateway.calls, hasLength(1));
      expect(gateway.calls.single.target, 'req-safe');
      expect((gateway.calls.single.arg as GateResponse).confirmed, isFalse);
      expect(respondAction(gateway.calls.single.arg as GateResponse), 'deny');
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('a destructive approve is two-step in the error tone', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(confirmGate(destructive: true)),
      );
      await pumpSheet(tester, team, 'req-destroy');
      final approve = find.byKey(const ValueKey('team-gate-approve'));
      // The one red fill is dangerFill (visual language §5).
      final fill = AppTheme.rolesOf(AppTheme.dark()).dangerFill;
      expect(
        tester
            .widget<FilledButton>(
              find.descendant(of: approve, matching: find.byType(FilledButton)),
            )
            .style
            ?.backgroundColor
            ?.resolve({}),
        fill,
      );
      expect(
        find.byKey(const ValueKey('team-gate-destructive')),
        findsOneWidget,
      );

      await tester.tap(approve);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsOneWidget);
      expect(find.text('Approve this destructive action?'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(confirmYes);
      await tester.pumpAndSettle();
      expect(gateway.calls, hasLength(1));
      expect((gateway.calls.single.arg as GateResponse).confirmed, isTrue);
      expect(respondAction(gateway.calls.single.arg as GateResponse), 'allow');
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Gate bead and run failed
  // ---------------------------------------------------------------------

  group('gate bead', () {
    testWidgets('Mark done answers the bead gate', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(beadGate()),
      );
      await pumpSheet(tester, team, 'bead:w-gate');
      expect(
        find.text('Close this on the host. The phone can only watch for now.'),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('team-gate-mark-done')));
      await tester.pump();
      expect(gateway.calls.single.verb, 'respond');
      expect(gateway.calls.single.target, 'bead:w-gate');
      expect((gateway.calls.single.arg as GateResponse).confirmed, isTrue);
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  group('run failed', () {
    testWidgets('a failure a retry cannot fix offers no retry; the title '
        'names the task once', (tester) async {
      final (team, _) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // "tests failed": something needs changing first, so no Send again.
      expect(find.byKey(const ValueKey('team-gate-run-retry')), findsNothing);
      expect(find.byKey(const ValueKey('team-gate-recoverable')), findsNothing);
      expect(find.text('What went wrong'), findsOneWidget);
      // The sheet is titled after the task; no second heading says it.
      expect(find.text('Add subtract() to calc.py stopped'), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-title')), findsNothing);
      // Every action names what it acts on.
      expect(find.text("Open Wolf's page"), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Retry re-slings the stuck work to its agent', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => runShape(g, prompt: 'connection reset'),
      );
      await pumpSheet(tester, team, 'run:oc-loy');
      // The button names what it sends and to whom; no note repeats it.
      expect(
        find.text('Send Write tests for calc.py to Wolf again'),
        findsOneWidget,
      );
      expect(
        find.text('Sends Write tests for calc.py to Wolf again.'),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('team-gate-run-retry')));
      await tester.pump();
      final call = gateway.calls.single;
      expect(call.verb, 'assign');
      expect(call.target, 'w-tests');
      expect(call.arg, 'a-wolf');
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Cancel work is two-step in red and cancels the run', (
      tester,
    ) async {
      final (team, gateway) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // Design standard §2: at most two tertiary actions show (the agent's
      // page, Watch the agent); Cancel work is a rare destructive path and sits
      // under More, still two-step. No Close repeats the sheet's X.
      expect(find.byKey(const ValueKey('team-gate-close')), findsNothing);
      final cancel = find.byKey(const ValueKey('team-gate-run-cancel'));
      expect(cancel, findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      await tester.tap(cancel);
      await tester.pumpAndSettle();
      expect(confirmSheet, findsOneWidget);
      expect(find.text('Stop this work?'), findsOneWidget);
      expect(gateway.calls, isEmpty);
      await tester.tap(confirmYes);
      await tester.pumpAndSettle();
      expect(gateway.calls.single.verb, 'cancelRun');
      expect(gateway.calls.single.target, 'oc-loy');
      expect(tester.takeException(), isNull);
      await team.stop();
    });

    testWidgets('Watch the agent and Restart or reassign open the agent\'s '
        'conversation and page', (tester) async {
      final (team, gateway) = await boot(configure: runShape);
      await pumpSheet(tester, team, 'run:oc-loy');
      // Watch the agent is the second tertiary (design standard §2: two
      // shown); here its conversation is the live output.
      await tester.tap(find.byKey(const ValueKey('team-gate-run-logs')));
      await tester.pumpAndSettle();
      expect(sheet, findsNothing);
      final logs = tester.widget<TeamWatchLiveScreen>(
        find.byType(TeamWatchLiveScreen),
      );
      expect(logs.agentId, 'a-wolf');
      await tester.pageBack();
      await tester.pumpAndSettle();

      await tester.tap(find.byKey(const ValueKey('open')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('team-gate-run-agent')));
      await tester.pumpAndSettle();
      expect(
        tester.widget<AgentScreen>(find.byType(AgentScreen)).agentId,
        'a-wolf',
      );
      // Navigation only: nothing was sent.
      expect(gateway.calls, isEmpty);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Receipts
  // ---------------------------------------------------------------------

  group('receipts', () {
    testWidgets('unconfirmed: the copy, Retry under a new key, old marked', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async => MutationReceipt(
            id: call.requestId,
            status: MutationReceiptStatus.pending,
            message: 'connection reset',
          ),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('unconfirmed'), findsOneWidget);
      expect(retry, findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
      expect(gateway.calls, hasLength(1));

      // Nothing re-sends on its own.
      await tester.pump(const Duration(minutes: 2));
      expect(gateway.calls, hasLength(1));

      await tester.tap(retry);
      await tester.pump();
      await tester.pump();
      expect(gateway.calls, hasLength(2));
      expect(gateway.calls[0].requestId, 'key-1');
      expect(gateway.calls[1].requestId, 'key-2');
      expect(gateway.calls[1].target, 'req-1');
      expect((gateway.calls[1].arg as GateResponse).choice, 'SQLite');
      final old = team.mutation('key-1')!;
      expect(old.retriedBy, 'key-2');
      expect(old.canRetry, isFalse);
      final next = team.mutation('key-2')!;
      expect(next.retryOf, 'key-1');
      expect(next.status, MutationStatus.unconfirmed);
      expect(team.mutationFor('req-1')!.key, 'key-2');
      // The sheet follows the retry.
      expect(retry, findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('rejected: the host\'s message and Try again', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async => MutationReceipt.rejected(
            call.requestId,
            'no pending interaction',
          ),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('rejected'), findsOneWidget);
      expect(find.textContaining('no pending interaction'), findsWidgets);
      // A refused answer was not stored: no Try again; the options stay
      // answerable, and a tap sends the new answer.
      expect(retry, findsNothing);
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(gateway.calls, hasLength(2));
      expect(tester.takeException(), isNull);
    });

    testWidgets('a sent answer with no result becomes unconfirmed', (
      tester,
    ) async {
      final (team, gateway) = await boot(
        configure: (g) => g.gateList.add(choiceGate()),
        mutationTimeout: const Duration(seconds: 5),
      );
      await pumpSheet(tester, team, 'req-1');
      // One choice acts on tap (KIT-25): no separate Send.
      await tester.tap(find.byKey(const ValueKey('team-gate-option-0')));
      await tester.pump();
      await tester.pump();
      expect(receiptOf('sent'), findsOneWidget);
      await tester.pump(const Duration(seconds: 6));
      expect(team.mutationFor('req-1')!.status, MutationStatus.unconfirmed);
      expect(receiptOf('unconfirmed'), findsOneWidget);
      expect(gateway.calls, hasLength(1));
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Rows
  // ---------------------------------------------------------------------

  group('rows', () {
    testWidgets('home Needs you: the chip and the row removal', (tester) async {
      final (team, gateway) = await boot(
        configure: (g) => g
          ..gateList.add(choiceGate())
          ..answer = (call) async =>
              MutationReceipt.rejected(call.requestId, 'refused'),
      );
      tester.view.physicalSize = const Size(800, 1600);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        app(TeamHomeScreen(controller: team, now: () => clock)),
      );
      await tester.pumpAndSettle();
      // One question: shown first on the home, in full, no tab to open.
      final row = find.byKey(const ValueKey('team-home-gate-req-1'));
      final chip = find.byKey(const ValueKey('team-home-gate-req-1-receipt'));
      expect(row, findsOneWidget);
      // The row's line carries no receipt word until an answer is sent.
      expect(
        find.descendant(
          of: chip,
          matching: find.textContaining('Not', findRichText: true),
        ),
        findsNothing,
      );
      await team.answerGate('req-1', const GateResponse.choice('SQLite'));
      await tester.pump();
      await tester.pump();
      expect(chip, findsOneWidget);
      // The card's refused receipt says why, above the answers again.
      expect(
        find.descendant(
          of: chip,
          matching: find.textContaining('Not accepted', findRichText: true),
        ),
        findsOneWidget,
      );
      // Rejected never removes the row.
      expect(row, findsOneWidget);
      // Only a confirmation does.
      gateway.push(const GateChanged(gateId: 'req-1', resolved: true));
      await tester.pump();
      await tester.pump();
      expect(team.mutationFor('req-1')!.status, MutationStatus.rejected);
      expect(row, findsOneWidget);
      expect(tester.takeException(), isNull);
      await team.stop();
    });
  });

  // ---------------------------------------------------------------------
  // Capabilities
  // ---------------------------------------------------------------------

  group('capabilities', () {
    testWidgets('no control capability: actions absent, host line stays', (
      tester,
    ) async {
      final (team, _) = await boot(
        capabilities: OrchestrationCapabilities.gascityRead,
        configure: (g) {
          g.gateList.addAll([
            choiceGate(),
            textGate(),
            confirmGate(destructive: true),
            beadGate(),
          ]);
          runShape(g);
        },
      );
      for (final id in ['req-1', 'req-text', 'req-destroy', 'bead:w-gate']) {
        await pumpSheet(tester, team, id);
        expect(send, findsNothing);
        expect(find.byKey(const ValueKey('team-gate-approve')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-deny')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-mark-done')), findsNothing);
        expect(find.byKey(const ValueKey('team-gate-composer')), findsNothing);
        expect(
          find.byKey(const ValueKey('team-gate-answer-on-host')),
          findsOneWidget,
        );
        await tester.tap(find.byKey(const ValueKey('kit-sheet-close')));
        await tester.pumpAndSettle();
      }
      await pumpSheet(tester, team, 'run:oc-loy');
      expect(find.byKey(const ValueKey('team-gate-run-retry')), findsNothing);
      // Reads are on: the agent screens still open, the two tertiary
      // actions shown (design standard §2), no Close. Report this failure
      // (P8.4) never answers the gate, so it stays too, under More.
      expect(find.byKey(const ValueKey('team-gate-run-agent')), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-run-logs')), findsOneWidget);
      expect(find.byKey(const ValueKey('team-gate-run-cancel')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('team-gate-run-report')),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('team-gate-run-cancel')), findsNothing);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Notifications
  // ---------------------------------------------------------------------

  group('notifications', () {
    OrchestrationSnapshot snap({
      List<OrchestrationGate> gates = const [],
      List<OrchestrationRun> runs = const [],
    }) => OrchestrationSnapshot(gates: gates, runs: runs, refreshedAt: clock);

    test('tracker: baseline, one alert per id, kinds, settled', () {
      final tracker = TeamAlertTracker(profileId: _profileId);
      expect(tracker.observe(const OrchestrationSnapshot()).isEmpty, isTrue);
      expect(tracker.primed, isFalse);
      // The first snapshot with data announces nothing.
      expect(tracker.observe(snap(gates: [choiceGate()])).isEmpty, isTrue);
      expect(tracker.primed, isTrue);

      final diff = tracker.observe(
        snap(gates: [choiceGate(), textGate(), beadGate(), runGate()]),
      );
      expect(diff.alerts, [
        const TeamAlert(
          kind: CodingAlertKind.teamDecision,
          id: 'req-text',
          key: 'team:srv-1:gate:req-text',
        ),
        const TeamAlert(
          kind: CodingAlertKind.teamRunFailed,
          id: 'run:oc-loy',
          key: 'team:srv-1:gate:run:oc-loy',
        ),
      ]);
      // Same gates again: nothing.
      expect(
        tracker
            .observe(
              snap(gates: [choiceGate(), textGate(), beadGate(), runGate()]),
            )
            .isEmpty,
        isTrue,
      );
      // Leaving settles its alert (the failed run's too).
      final settled = tracker.observe(snap(gates: [choiceGate()]));
      expect(settled.settled, [
        'team:srv-1:gate:req-text',
        'team:srv-1:gate:run:oc-loy',
      ]);
      expect(settled.alerts, isEmpty);
      // A gate that left and came back is not announced twice.
      final again = tracker.observe(snap(gates: [choiceGate(), textGate()]));
      expect(again.isEmpty, isTrue);
      expect(tracker.observe(snap(gates: [choiceGate()])).isEmpty, isTrue);

      // Review ready pushes; a run completing pushes once.
      final review = OrchestrationGate(
        id: 'review:w-done',
        kind: GateKind.reviewReady,
        title: 'Scaffold calc.py',
      );
      final working = OrchestrationRun(
        id: 'oc-run',
        title: 'Run',
        state: RunState.working,
      );
      var d = tracker.observe(snap(gates: [review], runs: [working]));
      expect(d.alerts.single.kind, CodingAlertKind.teamReview);
      final done = OrchestrationRun(
        id: 'oc-run',
        title: 'Run',
        state: RunState.completed,
      );
      d = tracker.observe(snap(gates: [review], runs: [done]));
      expect(d.alerts, [
        const TeamAlert(
          kind: CodingAlertKind.teamCompleted,
          id: 'oc-run',
          key: 'team:srv-1:run:oc-run',
        ),
      ]);
      expect(
        tracker.observe(snap(gates: [review], runs: [done])).isEmpty,
        isTrue,
      );
      // A run first seen already completed is history, not news.
      final old = OrchestrationRun(
        id: 'oc-old',
        title: 'Old',
        state: RunState.completed,
      );
      expect(
        tracker.observe(snap(gates: [review], runs: [done, old])).isEmpty,
        isTrue,
      );
    });

    Future<({ConnectionController controller, List<_NativeCall> calls})>
    background({bool backgrounded = true}) async {
      final calls = <_NativeCall>[];
      final live = BackgroundLiveController(
        preferences: prefs,
        liveStatusDebounce: Duration.zero,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          if (method == 'updateLiveStatus') return const {'updated': true};
          calls.add((method: method, arguments: arguments));
          if (method == 'showCodingAlert') return const {'shown': true};
          if (method == 'dismissCodingAlert') return const {'dismissed': true};
          return const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      await prefs.setBool(BackgroundLiveController.preferenceKey, true);
      await live.restore();
      calls.clear();
      final controller = _Connection(
        ProfileStore(prefs: prefs),
        backgroundLive: live,
      )..connected = profile;
      addTearDown(controller.dispose);
      if (backgrounded) controller.suspendForLifecycle();
      return (controller: controller, calls: calls);
    }

    test(
      'one alert per gate, ids and fixed copy only, dismissed on settle',
      () async {
        SharedPreferences.setMockInitialValues({
          BackgroundLiveController.preferenceKey: true,
        });
        prefs = await SharedPreferences.getInstance();
        store = OrchestrationStore(prefs);
        final harness = await background();
        final (team, gateway) = await boot(start: false);
        harness.controller.adoptOrchestrationForTesting(team);
        await team.start();
        await Future<void>.delayed(Duration.zero);
        // The first snapshot is the baseline: nothing to announce.
        expect(harness.calls, isEmpty);

        gateway.gateList.add(choiceGate());
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        final shown = harness.calls
            .where((c) => c.method == 'showCodingAlert')
            .toList();
        expect(shown, hasLength(1));
        expect(shown.single.arguments, {
          'kind': 'team_decision',
          'sessionID': 'req-1',
          'key': 'team:srv-1:gate:req-1',
          'quickReply': false,
          'requestID': '',
          'profileID': 'srv-1',
          'allowActions': false,
          'subtext': 'Workstation',
        });
        // No title, prompt or option travels.
        final payload = shown.single.arguments.toString();
        expect(payload, isNot(contains('persistence')));
        expect(payload, isNot(contains('SQLite')));

        // A refetch with the same gate: still one.
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        expect(
          harness.calls.where((c) => c.method == 'showCodingAlert'),
          hasLength(1),
        );

        // The gate leaves: its alert is dismissed by key.
        gateway.gateList.clear();
        await team.refresh();
        await Future<void>.delayed(Duration.zero);
        final dismissed = harness.calls
            .where((c) => c.method == 'dismissCodingAlert')
            .toList();
        expect(dismissed, hasLength(1));
        expect(dismissed.single.arguments, {'key': 'team:srv-1:gate:req-1'});
        // Nothing was ever sent by alerting.
        expect(gateway.calls, isEmpty);
      },
    );

    test('in the foreground nothing is posted', () async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      prefs = await SharedPreferences.getInstance();
      store = OrchestrationStore(prefs);
      final harness = await background(backgrounded: false);
      final (team, gateway) = await boot(start: false);
      harness.controller.adoptOrchestrationForTesting(team);
      await team.start();
      gateway.gateList.add(choiceGate());
      gateway.runList.add(failedRun());
      await team.refresh();
      await Future<void>.delayed(Duration.zero);
      expect(
        harness.calls.where((c) => c.method == 'showCodingAlert'),
        isEmpty,
      );
    });

    test('TeamLink: ids only, both shapes, strict parse', () {
      final gate = TeamLink.tryCreate(
        kind: TeamLinkKind.gate,
        profileId: 'srv-1',
        id: 'bead:w-gate',
      )!;
      expect(gate.toString(), 'opencode-mobile://team/gate/srv-1/bead:w-gate');
      expect(TeamLink.parse(gate.toString()), gate);
      final run = TeamLink.tryCreate(
        kind: TeamLinkKind.run,
        profileId: 'srv-1',
        id: 'oc-loy',
      )!;
      expect(run.toString(), 'opencode-mobile://team/run/srv-1/oc-loy');
      expect(TeamLink.parse(run.toString()), run);
      for (final bad in [
        'opencode-mobile://session?profile=srv-1&session=s',
        'opencode-mobile://team/gate/srv-1',
        'opencode-mobile://team/gate/srv-1/req-1/extra',
        'opencode-mobile://team/other/srv-1/req-1',
        'opencode-mobile://team/gate/srv-1/req-1?x=1',
        'opencode-mobile://team/gate/srv-1/req-1#f',
        'https://team/gate/srv-1/req-1',
        'opencode-mobile://team/gate/../req-1',
        'opencode-mobile://team/gate/srv-1/-bad',
        '',
        42,
      ]) {
        expect(TeamLink.parse(bad), isNull, reason: '$bad');
      }
      expect(
        TeamLink.tryCreate(kind: TeamLinkKind.gate, profileId: 'a b', id: 'x'),
        isNull,
      );
    });

    test(
      'SessionLinkIntent accepts a team link beside session links',
      () async {
        final intent = SessionLinkIntent(
          channel: const MethodChannel('oc/link-test'),
        );
        addTearDown(intent.dispose);
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(
              const MethodChannel('oc/link-test'),
              (call) async => call.method == 'consumeSessionLink'
                  ? 'opencode-mobile://team/gate/srv-1/req-1'
                  : null,
            );
        addTearDown(
          () => TestDefaultBinaryMessengerBinding
              .instance
              .defaultBinaryMessenger
              .setMockMethodCallHandler(
                const MethodChannel('oc/link-test'),
                null,
              ),
        );
        if (!SessionLinkIntent.supported) return;
        await intent.start();
        expect(intent.pending.value, isNull);
        expect(
          intent.pendingTeam.value,
          const TeamLink(
            kind: TeamLinkKind.gate,
            profileId: 'srv-1',
            id: 'req-1',
          ),
        );
        expect(intent.takeTeam(), isNotNull);
        expect(intent.pendingTeam.value, isNull);
      },
    );

    Future<_Connection> shell({
      required CodingAlertKind kind,
      required String id,
      required OrchestrationController team,
    }) async {
      final profileStore = _MemoryProfileStore(prefs: prefs, saved: profile);
      await profileStore.setActiveId(profile.id);
      var consumed = false;
      final live = BackgroundLiveController(
        preferences: prefs,
        liveStatusDebounce: Duration.zero,
        invoke: (method, [arguments]) async {
          if (method == 'getBackgroundPause') {
            return const {
              'supported': true,
              'active': false,
              'paused': false,
              'reason': 'none',
              'at': null,
              'canResume': false,
            };
          }
          if (method == 'consumeCodingAlertOpen' && !consumed) {
            consumed = true;
            return {
              'kind': kind.wireValue,
              'sessionID': id,
              'profileID': profile.id,
            };
          }
          return const {
            'enabled': true,
            'active': true,
            'notificationGranted': true,
            'batteryOptimizationIgnored': false,
          };
        },
      );
      final connection = _Connection(profileStore, backgroundLive: live)
        ..api = _Api()
        ..repository = _Repository()
        ..version = '1.18.23'
        ..status = StreamStatus.connected
        ..connected = profile
        ..attach(team);
      addTearDown(connection.dispose);
      return connection;
    }

    Widget shellApp(ConnectionController controller) => ProviderScope(
      overrides: [
        bootstrapProvider.overrideWithValue(AppBootstrap(controller.store)),
        connProvider.overrideWithValue(controller),
      ],
      child: OcApp(updateService: _NoUpdateService()),
    );

    testWidgets('a completed-run notification tap opens the task\'s '
        'conversation (P0.3), not the run page', (tester) async {
      SharedPreferences.setMockInitialValues({
        BackgroundLiveController.preferenceKey: true,
      });
      prefs = await SharedPreferences.getInstance();
      store = OrchestrationStore(prefs);
      final (team, gateway) = await boot(
        configure: (g) => g.runList.add(
          OrchestrationRun(
            id: 'oc-done',
            title: 'Ship it',
            state: RunState.completed,
            updatedAt: clock,
          ),
        ),
      );
      final connection = await shell(
        kind: CodingAlertKind.teamCompleted,
        id: 'oc-done',
        team: team,
      );
      await tester.pumpWidget(shellApp(connection));
      await tester.pump();
      await tester.pump();
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TeamConversationScreen>(find.byType(TeamConversationScreen))
            .runId,
        'oc-done',
      );
      expect(find.byKey(const ValueKey('team-run')), findsNothing);
      expect(gateway.calls, isEmpty);
      expect(tester.takeException(), isNull);
    });
  });

  // ---------------------------------------------------------------------
  // Layout
  // ---------------------------------------------------------------------

  group('layout', () {
    Future<void> reveal(WidgetTester tester, Finder target) async {
      await tester.scrollUntilVisible(
        target,
        150,
        scrollable: find
            .descendant(of: sheet, matching: find.byType(Scrollable))
            .first,
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(target, findsOneWidget);
      final rect = tester.getRect(target);
      expect(rect.left, greaterThanOrEqualTo(0));
      expect(rect.right, lessThanOrEqualTo(320));
    }

    Future<void> revealButton(WidgetTester tester, Finder target) async {
      await reveal(tester, target);
      expect(tester.getSize(target).height, greaterThanOrEqualTo(48));
    }

    for (final direction in TextDirection.values) {
      for (final locale in const [Locale('en'), Locale('ar')]) {
        final tag = '${direction.name} ${locale.languageCode}';

        testWidgets('320dp 2.5x $tag: every variant with actions fits', (
          tester,
        ) async {
          final (team, _) = await boot(
            configure: (g) {
              g.gateList.addAll([
                choiceGate(),
                confirmGate(destructive: true),
                textGate(),
                beadGate(),
              ]);
              runShape(g);
            },
          );
          Future<void> open(String id) => pumpSheet(
            tester,
            team,
            id,
            size: const Size(320, 740),
            locale: locale,
            direction: direction,
            textScale: 2.5,
          );
          Future<void> close() async {
            // At 250 % text the header scrolls away with the body (the kit
            // frame's header spacer, 2d0ac9fe): scroll the body back to the
            // top and the close button is there again.
            final close = find.byKey(const ValueKey('kit-sheet-close'));
            final body = tester.state<ScrollableState>(
              find
                  .descendant(of: sheet, matching: find.byType(Scrollable))
                  .first,
            );
            body.position.jumpTo(0);
            await tester.pumpAndSettle();
            final rect = tester.getRect(close);
            expect(rect.top, greaterThanOrEqualTo(tester.getRect(sheet).top));
            expect(tester.getSize(close).height, greaterThanOrEqualTo(48));
            await tester.tap(close);
            await tester.pumpAndSettle();
            expect(sheet, findsNothing);
          }

          await open('req-1');
          expect(tester.getSize(sheet).width, 320);
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-option-1')),
          );
          await close();

          await open('req-destroy');
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-approve')),
          );
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-deny')),
          );
          await close();

          await open('req-text');
          await reveal(
            tester,
            find.byKey(const ValueKey('team-gate-composer')),
          );
          await revealButton(tester, send);
          await close();

          await open('bead:w-gate');
          await revealButton(
            tester,
            find.byKey(const ValueKey('team-gate-mark-done')),
          );
          await close();

          await open('run:oc-loy');
          for (final key in [
            'team-gate-run-agent',
            'team-gate-run-logs',
            // Cancel work is under More (standard §2).
            'kit-actions-more',
          ]) {
            await revealButton(tester, find.byKey(ValueKey(key)));
          }
          await tester.tap(find.byKey(const ValueKey('kit-actions-more')));
          await tester.pumpAndSettle();
          expect(
            find.byKey(const ValueKey('team-gate-run-cancel')),
            findsOneWidget,
          );
          // The last variant: the menu is the last thing to fit.
          await tester.tapAt(Offset.zero);
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
        });

        testWidgets('320dp 2.5x $tag: the receipt with Retry fits', (
          tester,
        ) async {
          final (team, _) = await boot(
            configure: (g) => g
              ..gateList.add(choiceGate())
              ..answer = (call) async => MutationReceipt(
                id: call.requestId,
                status: MutationReceiptStatus.pending,
              ),
          );
          await team.answerGate('req-1', const GateResponse.choice('SQLite'));
          await pumpSheet(
            tester,
            team,
            'req-1',
            size: const Size(320, 740),
            locale: locale,
            direction: direction,
            textScale: 2.5,
          );
          await reveal(tester, receiptLine);
          await revealButton(tester, retry);
          expect(tester.takeException(), isNull);
        });
      }
    }
  });
}

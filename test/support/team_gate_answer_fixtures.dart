import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitReceipt;
import 'package:opencode_mobile/ui/screens/team/gate_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

const gateProfileId = 'srv-1';

/// One recorded control call.
class Call {
  const Call(this.verb, this.target, this.requestId, {this.arg});

  final String verb;
  final String target;
  final String requestId;
  final Object? arg;

  @override
  String toString() => '$verb $target $requestId ${arg ?? ''}';
}

/// A gateway with configurable capabilities, list-backed reads, a pushable
/// event stream and a scripted answer per control call.
class Gateway implements OrchestrationGateway {
  Gateway({this.capabilities = OrchestrationCapabilities.fixture});

  @override
  final OrchestrationCapabilities capabilities;
  final gateList = <OrchestrationGate>[];
  final runList = <OrchestrationRun>[];
  final workList = <WorkItem>[];
  final agentList = <OrchestrationAgent>[];
  final calls = <Call>[];
  final stream = StreamController<OrchestrationEvent>.broadcast();
  Future<MutationReceipt> Function(Call call)? answer;
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

  Future<MutationReceipt> _call(Call call) {
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
  }) => _call(Call('respond', gateId, requestId, arg: response));

  @override
  Future<MutationReceipt> message(
    String agentId,
    String text, {
    required String requestId,
  }) => _call(Call('message', agentId, requestId, arg: text));

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) => _call(Call('controlAgent', agentId, requestId, arg: action));

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) => _call(Call('cancelRun', runId, requestId));

  @override
  Future<MutationReceipt> assign(
    String workId, {
    required String agentId,
    required String requestId,
  }) => _call(Call('assign', workId, requestId, arg: agentId));

  @override
  Future<MutationReceipt> createWork({
    required String title,
    String? description,
    String? projectId,
    required String requestId,
  }) => _call(Call('createWork', title, requestId, arg: projectId));
}

late SharedPreferences prefs;

late OrchestrationStore store;

late DateTime clock;

var nextKey = 0;

final profile = ServerProfile(
  id: gateProfileId,
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

OrchestrationGate confirmGate({bool destructive = false}) => OrchestrationGate(
  id: destructive ? 'req-destroy' : 'req-safe',
  kind: GateKind.confirmation,
  title: destructive ? 'Delete the old migrations?' : 'Continue with the plan?',
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

void runShape(Gateway gateway, {String prompt = 'tests failed'}) {
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

Future<(OrchestrationController, Gateway)> boot({
  void Function(Gateway gateway)? configure,
  OrchestrationCapabilities capabilities = OrchestrationCapabilities.fixture,
  Duration mutationTimeout = const Duration(seconds: 60),
  bool start = true,
}) async {
  final gateway = Gateway(capabilities: capabilities);
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

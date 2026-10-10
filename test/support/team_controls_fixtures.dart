import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/screens/team/agent_screen.dart';
import 'package:opencode_mobile/ui/screens/team_conversation/team_conversation.dart'
    show TeamConversationScreen;

/// One recorded control call.
class Call {
  const Call(this.verb, this.target, this.requestId, {this.arg});

  final String verb;
  final String target;
  final String requestId;
  final Object? arg;

  @override
  String toString() => '$verb $target ${arg ?? ''}';
}

/// An in-memory host: lists the tests set, every control recorded and
/// answered as accepted with a correlation id, an owned event stream.
class Gateway implements OrchestrationGateway, OrchestrationAgentOutputGateway {
  Gateway({this.capabilities = OrchestrationCapabilities.gascityFront});

  @override
  final OrchestrationCapabilities capabilities;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  final calls = <Call>[];
  List<OrchestrationProject> projectList = const [];
  List<OrchestrationRun> runList = const [];
  List<WorkItem> workList = const [];
  List<OrchestrationAgent> agentList = const [];
  Future<MutationReceipt> Function(Call call)? answer;
  bool _closed = false;

  @override
  OrchestrationHostIdentity? get host => const OrchestrationHostIdentity(
    provider: 'gascity',
    url: 'http://127.0.0.1:8373',
    city: 'bright-lights',
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
    if (script != null) return script(call);
    return Future.value(
      MutationReceipt(
        id: call.requestId,
        status: MutationReceiptStatus.accepted,
        correlationId: 'corr-${call.requestId}',
        upstreamStatus: 202,
      ),
    );
  }

  @override
  Future<List<OrchestrationProject>> projects() async => projectList;
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
  Future<List<WorkItem>> readyWork({String? projectId}) async => [
    for (final w in workList)
      if (w.state == WorkState.ready) w,
  ];
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
  Future<List<OrchestrationGate>> gates() async => const [];
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
  Stream<AgentOutputEvent> agentOutput(String sessionId) =>
      const Stream.empty();

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

late OrchestrationStore store;

late DateTime clock;

var nextKey = 0;

OrchestrationAgent fox({AgentState state = AgentState.working}) =>
    OrchestrationAgent(
      id: 'fox',
      name: 'fox',
      state: state,
      rawState: state == AgentState.stopped ? 'stopped' : 'active',
      sessionId: 'bl-5qc',
      pool: 'gastown.polecat',
      provider: 'opencode',
      model: 'openai/gpt-x',
      harness: 'OpenCode',
      currentWorkId: 'w2',
      contextPercent: 63,
      workDir: '/home/eslam/city/.gc/worktrees/ocproof/polecats/fox',
      branch: 'polecat/oc-cq6',
      sessionStartedAt: clock.subtract(const Duration(hours: 3)),
    );

OrchestrationAgent mayor({bool suspended = false, bool session = true}) =>
    OrchestrationAgent(
      id: 'gastown.mayor',
      name: 'gastown.mayor',
      state: suspended ? AgentState.stopped : AgentState.idle,
      rawState: suspended ? 'suspended' : 'idle',
      sessionId: session ? 'bl-8jc' : null,
      pack: 'gastown',
      raw: {'name': 'gastown.mayor', 'suspended': suspended},
    );

OrchestrationRun run({
  String id = 'oc-xru',
  String title = 'Offline-first sessions',
  RunKind kind = RunKind.formula,
  RunState state = RunState.working,
  DateTime? startedAt,
}) => OrchestrationRun(
  id: id,
  title: title,
  state: state,
  kind: kind,
  stepCount: 3,
  completedSteps: 1,
  startedAt: startedAt ?? clock.subtract(const Duration(hours: 1)),
  raw: {'id': id},
);

void shape(Gateway gateway, {List<OrchestrationAgent>? agents}) {
  gateway
    ..projectList = const [
      OrchestrationProject(id: 'ocproof', name: 'ocproof', rig: 'ocproof'),
    ]
    ..runList = [run()]
    ..workList = const [
      WorkItem(
        id: 'w2',
        title: 'Sync engine',
        state: WorkState.working,
        runId: 'oc-xru',
      ),
      WorkItem(
        id: 'w4',
        title: 'Conflict policy',
        state: WorkState.ready,
        runId: 'oc-xru',
      ),
      WorkItem(
        id: 'w5',
        title: 'Storage layer',
        state: WorkState.queued,
        runId: 'oc-xru',
      ),
    ]
    ..agentList = agents ?? [fox(), mayor()];
}

Future<(OrchestrationController, Gateway)> boot({
  OrchestrationCapabilities capabilities =
      OrchestrationCapabilities.gascityFront,
  void Function(Gateway gateway)? configure,
  Duration timeout = const Duration(seconds: 30),
}) async {
  final gateway = Gateway(capabilities: capabilities);
  shape(gateway);
  configure?.call(gateway);
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.gascity,
    url: 'http://127.0.0.1:8373',
    city: 'bright-lights',
    front: true,
    enabledAt: DateTime.utc(2026, 9, 10),
  );
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'srv-1',
      name: 'Development PC',
      baseUrl: 'https://server.example:4096',
      orchestration: config,
    ),
    config: config,
    store: store,
    probe: (_) async => ProbeFound(
      host: gateway.host!,
      city: 'bright-lights',
      front: true,
      identityAllowed: true,
      capabilities: gateway.capabilities,
    ),
    gatewayFactory: (_, _) => gateway,
    now: () => clock,
    mintKey: () => 'key-${++nextKey}',
    refreshDebounce: const Duration(milliseconds: 10),
    mutationTimeout: timeout,
  );
  addTearDown(controller.dispose);
  await controller.start();
  expect(controller.phase, OrchestrationPhase.ready);
  return (controller, gateway);
}

Widget app(
  Widget home, {
  Locale locale = const Locale('en'),
  TextDirection? direction,
  double scale = 1,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: AppTheme.dark(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(scale), disableAnimations: true),
    child: direction == null
        ? child!
        : Directionality(textDirection: direction, child: child!),
  ),
  home: home,
);

Future<void> size(WidgetTester tester, Size size) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Finder key(String value) => find.byKey(ValueKey(value));

Future<void> pumpAgent(
  WidgetTester tester,
  OrchestrationController controller, {
  Locale locale = const Locale('en'),
  TextDirection? direction,
  double scale = 1,
}) async {
  await tester.pumpWidget(
    app(
      AgentScreen(controller: controller, agentId: 'fox', now: () => clock),
      locale: locale,
      direction: direction,
      scale: scale,
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 50));
}

/// Controls moved into the top bar in screen-team-1 (2026-09-27).
Future<void> openMore(WidgetTester tester) async {
  await tester.tap(key('team-agent-more'));
  await tester.pumpAndSettle();
}

Future<void> tapVisible(WidgetTester tester, Finder target) async {
  // Let a newly focused field finish its caret reveal before scrolling
  // to the next action; otherwise it scrolls back after ensureVisible.
  await tester.pumpAndSettle();
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  expect(target.hitTestable(), findsOneWidget);
  await tester.tap(target);
}

/// The task's conversation, where the run page's controls moved.
Widget conversation(OrchestrationController controller, String runId) =>
    TeamConversationScreen(
      key: ValueKey('conversation-$runId'),
      team: controller,
      runId: runId,
      now: () => clock,
    );

Future<void> settle(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 30));
}

/// Lets the receipt window of every sent record elapse so no timer is
/// left pending when the tree is torn down.
Future<void> drain(WidgetTester tester) async {
  await tester.pump(const Duration(seconds: 31));
  await tester.pump();
}

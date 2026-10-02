// The AI Team on this phone as the owner saw it on 2026-09-25: one task in
// the project my-app, the worker furiosa (a polecat running `opencode acp`
// in /root/aiteam/city/.gc/worktrees/my-app/polecats/gastown.furiosa) and
// the refinery, whose sessions land in the phone's own OpenCode store.
// Shared by the watching-mode and team-conversation tests.

import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/orchestration_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/orchestration/adapters/fixture/fixture_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/orchestration.dart';
import 'package:opencode_mobile/state/orchestration_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'complete_message_history.dart';

const teamRig = '/root/projects/my-app';
const teamPolecatDir =
    '/root/aiteam/city/.gc/worktrees/my-app/polecats/gastown.furiosa';
const teamRefineryDir = '/root/aiteam/city/.gc/worktrees/my-app/refinery';

/// The team's clock: 2026-09-25 19:55 UTC, four minutes after furiosa's
/// session was created (the phone's cold start).
final teamClock = DateTime.utc(2026, 9, 25, 19, 55);

Directory _fixtureRoot() {
  var dir = Directory.current;
  for (var i = 0; i < 5; i++) {
    final candidate = Directory('${dir.path}/tool/qa/gascity_fixture');
    if (candidate.existsSync()) return candidate;
    dir = dir.parent;
  }
  throw StateError('tool/qa/gascity_fixture not found');
}

/// The fixture host with the task, steps and agents of a scenario, and a
/// record of every message the team's control was asked to send.
class TeamChatGateway
    implements OrchestrationGateway, OrchestrationAgentOutputGateway {
  TeamChatGateway(this.inner, {required this.capabilities});

  final FixtureOrchestrationGateway inner;
  @override
  final OrchestrationCapabilities capabilities;
  final stream = StreamController<OrchestrationEvent>.broadcast();
  List<OrchestrationRun>? runsOverride;
  List<WorkItem>? workOverride;
  List<OrchestrationAgent>? agentsOverride;
  List<OrchestrationGate>? gatesOverride;
  final messages = <(String, String)>[];

  /// Agent controls sent (agent id, action), in order.
  final controls = <(String, AgentControlAction)>[];

  /// Work items created (title, project id), in order.
  final created = <(String, String?)>[];

  /// The host's answer to a cancel, when a test scripts one (a refusal).
  Future<MutationReceipt> Function(String runId, String requestId)?
  cancelRunAnswer;

  /// The in-app team: Gas City on this phone.
  @override
  OrchestrationHostIdentity? get host => OrchestrationHostIdentity(
    provider: inner.host?.provider ?? 'fixture',
    url: 'http://127.0.0.1:8472',
    hostMode: OrchestrationHostMode.phone,
    city: 'aiteam',
  );
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

  /// A scripted live output (an ended session), in place of the fixture's.
  Stream<AgentOutputEvent> Function(String sessionId)? outputOverride;

  @override
  Stream<AgentOutputEvent> agentOutput(String sessionId) =>
      outputOverride?.call(sessionId) ?? inner.agentOutput(sessionId);
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
  }) {
    messages.add((agentId, text));
    return inner.message(agentId, text, requestId: requestId);
  }

  @override
  Future<MutationReceipt> controlAgent(
    String agentId,
    AgentControlAction action, {
    required String requestId,
  }) {
    controls.add((agentId, action));
    return inner.controlAgent(agentId, action, requestId: requestId);
  }

  @override
  Future<MutationReceipt> cancelRun(
    String runId, {
    required String requestId,
  }) =>
      cancelRunAnswer?.call(runId, requestId) ??
      inner.cancelRun(runId, requestId: requestId);
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
  }) {
    created.add((title, projectId));
    return inner.createWork(
      title: title,
      description: description,
      projectId: projectId,
      requestId: requestId,
    );
  }
}

/// furiosa as Gas City reported it on the phone: `/agents` said stopped,
/// `/sessions` said active and running (trust the session).
OrchestrationAgent teamFuriosa({
  String? workDir = teamPolecatDir,
  bool? sessionRunning = true,
  String? sessionState = 'active',
  String? currentWorkId = 'ma-1',
}) => OrchestrationAgent(
  id: 'my-app/gastown.furiosa',
  name: 'my-app/gastown.furiosa',
  state: AgentState.stopped,
  rawState: 'stopped',
  sessionId: 'ma-wisp-7',
  sessionName: 'my-app--gastown__polecat-ma-wisp-7',
  pool: 'my-app/gastown.polecat',
  provider: 'opencode',
  harness: 'OpenCode',
  currentWorkId: currentWorkId,
  workDir: workDir,
  sessionStartedAt: DateTime.utc(2026, 9, 25, 19, 51, 5),
  sessionState: sessionState,
  sessionRunning: sessionRunning,
);

final teamTask = OrchestrationRun(
  id: 'ma-convoy-1',
  title: 'Add a dark mode toggle',
  state: RunState.working,
  rawState: 'open',
  kind: RunKind.batch,
  projectId: 'my-app',
  stepCount: 2,
  completedSteps: 0,
  startedAt: DateTime.utc(2026, 9, 25, 19, 49, 3),
  updatedAt: DateTime.utc(2026, 9, 25, 19, 51, 5),
  raw: const {'id': 'ma-convoy-1', 'issue_type': 'convoy'},
);

const teamSteps = [
  WorkItem(
    id: 'ma-1',
    title: 'Add the toggle to Settings',
    state: WorkState.working,
    runId: 'ma-convoy-1',
    projectId: 'my-app',
    assignee: 'my-app/gastown.furiosa',
  ),
  WorkItem(
    id: 'ma-2',
    title: 'Remember the choice',
    state: WorkState.queued,
    runId: 'ma-convoy-1',
    projectId: 'my-app',
  ),
];

/// Boots an orchestration controller on the fixture host.
Future<(OrchestrationController, TeamChatGateway)> bootTeam({
  OrchestrationCapabilities capabilities =
      OrchestrationCapabilities.gascityLoopback,
  List<OrchestrationRun>? runs,
  List<WorkItem>? work,
  List<OrchestrationAgent>? agents,
  List<OrchestrationGate> gates = const [],
}) async {
  SharedPreferences.setMockInitialValues({});
  final store = OrchestrationStore(await SharedPreferences.getInstance());
  final fixturePath = _fixtureRoot().path;
  final gateway =
      TeamChatGateway(
          FixtureOrchestrationGateway(fixturePath: fixturePath),
          capabilities: capabilities,
        )
        ..runsOverride = runs ?? [teamTask]
        ..workOverride = work ?? teamSteps
        ..agentsOverride = agents ?? [teamFuriosa()]
        ..gatesOverride = gates;
  final config = OrchestrationConfig(
    provider: OrchestrationProvider.fixture,
    url: fixturePath,
    city: 'aiteam',
    hostMode: OrchestrationHostMode.phone,
    enabledAt: DateTime.utc(2026, 9, 25),
  );
  final controller = OrchestrationController(
    profile: ServerProfile(
      id: 'phone',
      name: 'This phone',
      baseUrl: 'http://127.0.0.1:4096',
      orchestration: config,
    ),
    config: config,
    store: store,
    gatewayFactory: (_, _) => gateway,
    now: () => teamClock,
  );
  addTearDown(controller.dispose);
  await controller.start();
  return (controller, gateway);
}

MessageWithParts teamChatMessage(
  String id,
  String role,
  String text, {
  String sessionID = 'ses_furiosa',
  int minute = 52,
}) {
  final at = DateTime.utc(2026, 9, 25, 19, minute).millisecondsSinceEpoch;
  return MessageWithParts(
    info: MessageInfo(
      id: id,
      sessionID: sessionID,
      role: role,
      time: MsgTime(created: at, completed: at),
    ),
    parts: [Part(id: '$id-p', messageID: id, type: 'text', text: text)],
  );
}

/// The phone's OpenCode server: the worker's transcript (it can grow
/// between reads), and a record of every prompt anything sent.
class TeamChatApi extends OpenCodeApi with CompleteMessageHistory {
  TeamChatApi(this.transcript) : super(baseUrl: 'http://localhost');

  final Map<String, List<MessageWithParts>> transcript;
  final Map<String, Session> known = {};
  final prompts = <String>[];
  var reads = 0;

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    reads += 1;
    return List.of(transcript[id] ?? const []);
  }

  @override
  Future<Session> session(String id) async =>
      known[id] ?? Session(id: id, directory: teamPolecatDir);

  /// The current project's own list (the rig): the person's conversation.
  @override
  Future<List<Session>> sessions() async => [
    Session(
      id: 'ses_mine',
      directory: teamRig,
      title: 'Add a dark mode toggle',
    ),
  ];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async => prompts.add('$sessionID: $text');

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeSend?.call();
    prompts.add('$sessionID: $text');
  }
}

/// The server's all-projects list, newest first.
class TeamChatRepository implements ServerOperationsGateway {
  TeamChatRepository(this.results);

  final List<GlobalSessionResult> results;
  var lists = 0;

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async {
    lists += 1;
    return ServerPage(items: cursor == null ? results : const []);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

GlobalSessionResult teamSession(
  String id,
  String directory, {
  required DateTime updated,
  String? title,
  String? parentID,
}) => GlobalSessionResult(
  session: Session(
    id: id,
    title: title,
    directory: directory,
    parentID: parentID,
    time: SessionTime(
      created: updated
          .subtract(const Duration(minutes: 1))
          .millisecondsSinceEpoch,
      updated: updated.millisecondsSinceEpoch,
    ),
  ),
  projectDirectory: teamRig,
);

Future<ConnectionController> teamConnection({
  required TeamChatApi api,
  required TeamChatRepository repository,
  String directory = teamRig,
}) async {
  final prefs = await SharedPreferences.getInstance();
  final controller = ConnectionController(ProfileStore(prefs: prefs))
    ..api = api
    ..repository = repository
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  return controller;
}

/// The app shell of the tests: the connection above everything, dark theme.
Widget teamChatApp(
  ConnectionController connection,
  Widget home, {
  Locale locale = const Locale('en'),
}) => ProviderScope(
  overrides: [connProvider.overrideWithValue(connection)],
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: AppTheme.dark(),
    locale: locale,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    supportedLocales: AppLocalizations.supportedLocales,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(context).copyWith(disableAnimations: true),
      child: child!,
    ),
    home: home,
  ),
);

/// 412 × 915, the owner's phone in the renders.
void phoneViewport(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart'
    show
        MaterialApp,
        Scaffold,
        Builder,
        SingleChildScrollView,
        Key,
        ValueKey,
        SizedBox;
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_tools/browser_claude_launch.dart';
import 'package:opencode_mobile/domain/agent_auth_probe.dart';
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_install.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_agent_host_port.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

part 'support/phone_agents_genui_tests.dart';
part 'support/phone_agents_native_question_tests.dart';
part 'support/phone_agents_list_question_tests.dart';
part 'support/phone_agents_list_permission_tests.dart';
part 'support/phone_agents_reopen_tests.dart';
part 'support/phone_agents_photo_card_tests.dart';
part 'support/phone_agents_stall_tests.dart';

const _project = '/root/projects/app';
const _stamp = '2026-10-03T08:00:00Z';

class _RealHttpOverrides extends HttpOverrides {}

// ---- OpenCode side -----------------------------------------------------------

/// An in-memory Keystore: the platform channel is not what Linux tests use.
class _FakeSecure implements FlutterSecureStorage {
  _FakeSecure(this.map);
  final Map<String, String> map;

  @override
  dynamic noSuchMethod(Invocation invocation) {
    final key = invocation.namedArguments[#key] as String?;
    switch (invocation.memberName) {
      case #read:
        return Future<String?>.value(map[key]);
      case #write:
        map[key!] = invocation.namedArguments[#value] as String;
        return Future<void>.value();
      case #delete:
        map.remove(key);
        return Future<void>.value();
      case #readAll:
        return Future<Map<String, String>>.value(Map.of(map));
      case #containsKey:
        return Future<bool>.value(map.containsKey(key));
      default:
        return Future<void>.value();
    }
  }
}

class _NoEngineBridge implements PhoneProjectEngineBridge {
  @override
  Future<void> start(
    String profileId, {
    int port = 4098,
    String? notice,
  }) async {}
  @override
  Future<({String baseUrl, String bearerToken})> credentials(
    String profileId,
  ) => Future.error(StateError('no engine'));
  @override
  Future<void> delete(String profileId) async {}
  @override
  Future<void> stop(String profileId) async {}
}

class _OcScript {
  List<GlobalSessionResult> global = [];
  final created = <String?>[];
  final questionAnswers = <(String, String, List<List<String>>)>[];
}

class _OcApi extends OpenCodeApi {
  _OcApi(this.script, {this.gated = false})
    : super(baseUrl: 'http://127.0.0.1:4097');
  final _OcScript script;
  final bool gated;
  final health_ = Completer<Health>();
  String? located;

  @override
  void setLocation({String? directory, String? workspace}) {
    located = directory;
    super.setLocation(directory: directory, workspace: workspace);
  }

  @override
  Future<Health> health() => gated
      ? health_.future
      : Future.value(Health(healthy: true, version: '1'));
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Session> session(String id) async =>
      Session(id: id, directory: _project);
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<Session> createSession() async {
    script.created.add(located);
    return Session(id: 'oc-new-${script.created.length}', directory: located);
  }

  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    script.questionAnswers.add((sessionID, requestID, answers));
  }

  @override
  Future<List<FileNode>> listFiles(String path) async => const [];
}

class _Stream extends EventStream {
  _Stream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });
  @override
  void start() => onStatus(StreamStatus.connecting);
  @override
  Future<void> dispose() async {}
}

class _OcRepo extends SdkProductRepository {
  _OcRepo(OpenCodeApi api, this.script) : super(api.sdkClient);
  final _OcScript script;
  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<CatalogSnapshot> loadCatalog() async =>
      const CatalogSnapshot(providers: [], models: [], agents: []);
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<WorkspaceProject>> listProjects() async => const [];
  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];
  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async => ServerPage(items: script.global);
}

GlobalSessionResult _ocRow(String id, String directory, {int updated = 5}) =>
    GlobalSessionResult(
      session: Session(
        id: id,
        title: 'OC $id',
        directory: directory,
        time: SessionTime(created: 0, updated: updated),
      ),
      projectDirectory: directory,
    );

// ---- phone agent host side ---------------------------------------------------

class _Events {
  final log = <String>[];
}

class _BrowserPort implements BrowserClaudeLaunchPort {
  List<String> log = [];
  final targets = <BrowserEnrollmentTarget>[];
  @override
  Future<BrowserLaunchReservation?> beforeBrowserClaudeLaunch({
    required String profileId,
    required String sourceId,
    required String sessionId,
    required String daemonAgentId,
  }) async {
    log.add('browser.reserve');
    final target = BrowserEnrollmentTarget(
      profileId: profileId,
      sourceId: sourceId,
      sessionId: sessionId,
      daemonAgentId: daemonAgentId,
    );
    targets.add(target);
    return BrowserLaunchReservation(
      id: 'grant-${targets.length}',
      launchId: targets.length.toRadixString(16).padLeft(32, '0'),
      target: target,
    );
  }

  @override
  Future<void> revokeBrowserClaudeLaunch(
    BrowserLaunchReservation reservation,
  ) async {
    log.add('browser.revoke');
  }
}

class _HostState {
  Future<AgentAuthProbeResult> Function(String)? probeHandler;
  final auth = <String, AgentAuthProbeResult>{};
  AgentAuthProbeResult logoutResult = const AgentAuthProbeResult(
    state: AgentAuthProbeState.signedOut,
  );
  PaseoGateway Function(PaseoTransport, String)? gatewayFactory;
  void Function(FakePaseoSocket)? configureSocket;
  bool freshPrivateSockets = false;
  final inspectedCapabilities = <String, AgentCapabilities>{};
  Map<String, PhoneAgentRuntime> runtimes = {};
  List<Map<String, dynamic>> agents = [];
  var newAgentSeq = 0;
  AgentPhoneCheckResult? check;

  /// What the helper's runtime snapshot lists.
  List<Map<String, dynamic>> providers = [_provider('claude')];

  /// When set, the helper refuses to resume old conversations.
  bool resumeFails = false;
  final resumed = <String>[];

  /// How many next connects fail like a helper Android stopped.
  int failOpens = 0;
}

class _FakeHost implements PhoneAgentHostPort, PhoneAgentAuthPort {
  _FakeHost(this.events, this.profileId, this.state);
  final _Events events;
  final String profileId;
  final _HostState state;
  @override
  Future<AgentAuthProbeResult> probeSignIn(String id) async =>
      await state.probeHandler?.call(id) ??
      state.auth[id] ??
      ({
            AgentSignInPhase.signedIn,
            AgentSignInPhase.limitReached,
          }.contains(state.runtimes[id]?.signInPhase)
          ? const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn)
          : const AgentAuthProbeResult.failed(
              AgentAuthProbeError.invalidResponse,
            ));
  @override
  bool supportsSignOut(String id) => id == 'claude' || id == 'fx';
  @override
  Future<AgentAuthProbeResult> signOut(String id) async {
    events.log.add('logout.$id');
    state.auth[id] = state.logoutResult;
    return state.logoutResult;
  }

  final sockets = <FakePaseoSocket>[];
  final gateways = <PaseoGateway>[];
  final setup = StreamController<AgentSetupProgress>.broadcast(sync: true);
  Map<String, PhoneAgentRuntime> get runtimes => state.runtimes;
  List<Map<String, dynamic>> get agents => state.agents;
  set agents(List<Map<String, dynamic>> value) => state.agents = value;
  AgentPhoneCheckResult? get check => state.check;

  @override
  Stream<AgentSetupProgress> get setupChanges => setup.stream;
  @override
  AgentSetupProgress get setupProgress =>
      const AgentSetupProgress(agentId: '', phase: AgentSetupPhase.idle);
  @override
  Future<void> install(String agentId) async => events.log.add('install');
  @override
  Future<void> restoreInstall() async {}
  @override
  Future<void> cancelInstall() async => events.log.add('host.cancelInstall');
  @override
  Future<void> start() async => events.log.add('host.start');
  @override
  Future<void> stop() async => events.log.add('host.stop');
  @override
  Future<AgentPhoneCheckResult> selfTest(String agentId) async =>
      check ??
      AgentPhoneCheckResult(
        agentId: agentId,
        architecture: AgentArchitecture.arm64,
        passed: true,
        completed: AgentPhoneCheckStep.values,
      );
  @override
  Future<AgentArchitecture?> architecture() async => AgentArchitecture.arm64;
  @override
  Future<PhoneAgentRuntime> inspect(
    String agentId, {
    AgentSignInState? signIn,
    AgentCapabilities capabilities = const AgentCapabilities(),
  }) async {
    state.inspectedCapabilities[agentId] = capabilities;
    final base = runtimes[agentId] ?? PhoneAgentRuntime(agentId: agentId);
    return PhoneAgentRuntime(
      agentId: agentId,
      installed: base.installed,
      hostAvailable: base.hostAvailable,
      architectureQualified: base.architectureQualified,
      capabilities: base.capabilities,
      signInPhase:
          base.signInPhase ??
          (signIn?.inspected == true ? signIn!.phase : null),
      resetAt: base.resetAt,
      stoppedInBackground: base.stoppedInBackground,
    );
  }

  int get failOpens => state.failOpens;
  set failOpens(int value) => state.failOpens = value;

  @override
  Future<PaseoGateway> openGateway(String directory) async {
    if (failOpens > 0) {
      failOpens--;
      throw const AgentHostException(AgentHostFailure.hello);
    }
    return newGatewaySync(directory);
  }

  @override
  PaseoGateway newGatewaySync(String directory) {
    final socket = FakePaseoSocket();
    sockets.add(socket);
    socket.handlers['get_providers_snapshot_request'] = (request) => (
      'get_providers_snapshot_response',
      {
        'cwd': request['cwd'],
        'generatedAt': _stamp,
        'entries': state.providers,
      },
    );
    socket.handlers['refresh_providers_snapshot_request'] = (request) => (
      'refresh_providers_snapshot_response',
      {'cwd': request['cwd'], 'acknowledged': true},
    );
    socket.handlers['fetch_agents_request'] = (_) => (
      'fetch_agents_response',
      {
        'entries': [
          for (final agent in agents)
            if (agent['cwd'] == directory) {'agent': agent},
        ],
        'pageInfo': {'hasMore': false, 'nextCursor': null, 'prevCursor': null},
      },
    );
    socket.handlers['fetch_agent_request'] = (request) {
      final id = request['agentId'];
      final agent = agents.firstWhere((a) => a['id'] == id);
      return ('fetch_agent_response', {'agent': agent});
    };
    socket.handlers['resume_agent_request'] = (request) {
      final handle = request['handle'] as Map;
      state.resumed.add(handle['sessionId'] as String);
      if (state.resumeFails) {
        return (
          'rpc_error',
          {'error': 'resume refused', 'requestType': 'resume_agent_request'},
        );
      }
      final old = agents.firstWhere(
        (a) => (a['persistence'] as Map?)?['sessionId'] == handle['sessionId'],
      );
      final agent = {...old, 'status': 'idle'};
      agents = [for (final a in agents) a['id'] == old['id'] ? agent : a];
      return ('status', {'status': 'agent_resumed', 'agent': agent});
    };
    socket.handlers['create_agent_request'] = (request) {
      final agent = _agent('claude-new-${++state.newAgentSeq}', directory);
      agents = [...agents, agent];
      return ('create_agent_response', {'agent': agent});
    };
    state.configureSocket?.call(socket);
    var opened = false;
    final transport = PaseoTransport(
      endpoint: 'ws://127.0.0.1:4099',
      socketFactory: (_, _) async {
        if (!opened || !state.freshPrivateSockets) {
          opened = true;
          return socket;
        }
        // Bounded history owns and closes a separate transport connection.
        return FakePaseoSocket()..handlers.addAll(socket.handlers);
      },
    );
    final gateway =
        state.gatewayFactory?.call(transport, directory) ??
        PaseoGateway(directory: directory, transport: transport);
    gateways.add(gateway);
    return gateway;
  }

  @override
  Future<void> dispose() async => events.log.add('host.dispose');
}

Map<String, dynamic> _provider(String id) => {
  'provider': id,
  'status': 'ready',
  'enabled': true,
  'source': 'builtin',
  'label': '$id host label',
  'models': [
    {
      'provider': id,
      'id': '$id-model',
      'label': '$id model',
      'isDefault': true,
    },
  ],
  'modes': [
    {'id': 'default', 'label': 'Default'},
  ],
  'defaultModeId': 'default',
  'fetchedAt': _stamp,
};

Map<String, dynamic> _agent(
  String id,
  String cwd, {
  String provider = 'claude',
  int minute = 30,
  String status = 'idle',
  bool persistence = true,
}) => {
  'id': id,
  'provider': provider,
  'cwd': cwd,
  'model': '$provider-model',
  'title': 'Claude $id',
  'status': status,
  'createdAt': _stamp,
  'updatedAt': '2026-10-03T09:$minute:00Z',
  'lastUserMessageAt': _stamp,
  'capabilities': {
    'supportsStreaming': true,
    'supportsSessionPersistence': true,
    'supportsSessionListing': true,
    'supportsDynamicModes': true,
    'supportsMcpServers': true,
    'supportsReasoningStream': true,
    'supportsToolInvocations': true,
  },
  'currentModeId': 'default',
  'availableModes': [
    {'id': 'default', 'label': 'Default'},
  ],
  'pendingPermissions': <Object>[],
  'persistence': persistence
      ? {'provider': provider, 'sessionId': 'native-$id'}
      : null,
  'labels': <String, String>{},
  'archivedAt': null,
};

PhoneAgentRuntime _ready(String id) => PhoneAgentRuntime(
  agentId: id,
  installed: true,
  hostAvailable: true,
  architectureQualified: true,
  signInPhase: AgentSignInPhase.signedIn,
);

class _FakeSignInHost implements AgentSignInHost {
  _FakeSignInHost(this.events);
  final _Events events;
  AgentSignInPhase phase = AgentSignInPhase.signedOut;
  final submitted = <String>[];

  /// Like the real host: the page is printed first (urlReady) and the code
  /// prompt is only readable once [promptPrinted].
  bool urlFirst = false;
  bool promptPrinted = true;

  AgentSignInHostUpdate _update(AgentSignInRun run, {bool url = false}) =>
      AgentSignInHostUpdate(
        runId: run.runId,
        phase: phase,
        authorizationUrl: url
            ? AgentAuthorizationUrl.validate(
                'claude',
                'https://claude.com/cai/oauth/authorize?code=true',
              )
            : null,
      );

  @override
  Future<AgentSignInHostUpdate> status(AgentSignInRun run) async =>
      _update(run, url: phase == AgentSignInPhase.awaitingCode);
  @override
  Future<AgentSignInHostUpdate> start(AgentSignInRun run) async {
    phase = urlFirst
        ? AgentSignInPhase.urlReady
        : AgentSignInPhase.awaitingCode;
    return _update(run, url: true);
  }

  @override
  Future<AgentSignInHostUpdate> readChallenge(AgentSignInRun run) async {
    if (phase == AgentSignInPhase.urlReady && promptPrinted) {
      phase = AgentSignInPhase.awaitingCode;
    }
    return _update(run, url: true);
  }

  @override
  Future<AgentSignInHostUpdate> submitCode(
    AgentSignInRun run,
    AgentSignInCode code,
  ) async {
    submitted.add(code.consume());
    phase = AgentSignInPhase.signedIn;
    return _update(run);
  }

  @override
  Future<void> cancelAndDrain(AgentSignInRun run) async =>
      events.log.add('auth.cancelAndDrain');
}

// ---- harness -----------------------------------------------------------------

class _CardInstaller implements GenUiInstaller {
  @override
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  }) async => enabled
      ? GenUiSetupOn(agents: [GenUiAgent.claude])
      : const GenUiSetupOff();
}

class _World {
  _World(
    this.controller,
    this.hosts,
    this.state,
    this.signIn,
    this.events,
    this.oc,
  );
  final ConnectionController controller;
  final List<_FakeHost> hosts;
  final _HostState state;
  final _FakeSignInHost signIn;
  final _Events events;
  final _OcScript oc;
  _FakeHost get host => hosts.last;
}

Future<_World> _world(
  WidgetTester? tester, {
  bool phone = true,
  Map<String, Object> prefsExtra = const {},
  FlutterSecureStorage? secure,
  GenUiInstaller? genUiInstaller,
  BrowserClaudeLaunchRegistry? browserClaudeLaunchRegistry,
}) async {
  final profileJson = {
    'id': 'local',
    'name': 'Phone server',
    'baseUrl': phone ? 'http://127.0.0.1:4097' : 'http://127.0.0.1:1',
    'username': '',
  };
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([profileJson]),
    ...prefsExtra,
  });
  final prefs = await SharedPreferences.getInstance();
  final events = _Events();
  final store = ProfileStore(
    prefs: prefs,
    secure: secure,
    agentHostCleanup: (id) async => events.log.add('profileStore.cleanup'),
  );
  await store.load();
  final script = _OcScript();
  final apis = <_OcApi>[];
  final hosts = <_FakeHost>[];
  final state = _HostState();
  final signIn = _FakeSignInHost(events);
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _OcApi(script, gated: tester != null && apis.isEmpty);
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _OcRepo(api, script),
    v2GatewayFactory: (_) {
      final api = _OcApi(script);
      return (gateway: api, operations: _OcRepo(api, script));
    },
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _Stream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
    localWakeLockEnsurer: () async {},
    phoneEngineBridge: _NoEngineBridge(),
    phoneAgentHostFactory: (profile) {
      final host = _FakeHost(events, profile.id, state);
      hosts.add(host);
      return host;
    },
    agentSignInHostFactory: () => signIn,
    genUiInstaller: genUiInstaller,
    browserClaudeLaunchRegistry: browserClaudeLaunchRegistry,
  );
  final connect = controller.connect(store.profiles.single);
  await tester?.pump();
  apis.first.health_.complete(Health(healthy: true, version: '1'));
  await connect;
  await tester?.pump();
  return _World(controller, hosts, state, signIn, events, script);
}

void main() {
  final binding = TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(() => debugPlatformCapabilities = null);
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);
  setUp(() {
    debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
    binding.defaultBinaryMessenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
  });

  _genUiFeedRefreshTests();
  _nativeQuestionControllerTests();
  _listQuestionTests();
  _listPermissionTests();
  _reopenAgentTests();
  _photoCardAliasTests();
  _turnStallControllerTests();

  const dir = _project;

  Future<_World> ready(
    WidgetTester? tester, {
    Map<String, Object>? prefs,
    FlutterSecureStorage? secure,
    GenUiInstaller? genUiInstaller,
  }) async {
    final w = await _world(
      tester,
      prefsExtra: prefs ?? const {},
      secure: secure,
      genUiInstaller: genUiInstaller,
    );
    w.state.runtimes = {'claude': _ready('claude')};
    // Left over from an earlier host run: listed, not loaded, and without
    // its runtime's session handle (nothing to resume it by).
    w.state.agents = [_agent('c1', dir, status: 'closed', persistence: false)];
    w.oc.global = [_ocRow('c1', dir)];
    await w.controller.rememberLastUsedProject(dir);
    await w.controller.refreshAgentRows();
    await w.controller.refreshChatFeed();
    return w;
  }

  test(
    'BA9 uses host owner scope and revokes before native host stop',
    () async {
      final port = _BrowserPort();
      final registry = BrowserClaudeLaunchRegistry(port: port);
      final w = await _world(null, browserClaudeLaunchRegistry: registry);
      addTearDown(w.controller.dispose);
      addTearDown(registry.close);
      port.log = w.events.log;
      w.state.runtimes = {'claude': _ready('claude')};
      w.state.agents = [_agent('browser-chat', dir)];
      w.state.configureSocket = (socket) {
        socket.handlers['set_agent_model_request'] = (_) =>
            ('set_agent_model_response', {});
      };
      await w.controller.rememberLastUsedProject(dir);
      await w.controller.refreshAgentRows();
      await w.controller.refreshChatFeed();
      final row = w.controller.chatFeed().items.singleWhere(
        (row) =>
            row.sourceId == 'paseo:$dir' && row.sessionID == 'browser-chat',
      );
      final route = await w.controller.openChatFeedItem(row);
      final backend = w.controller.backendForConversation(route.sessionID)!;
      await backend.setAgentBrowserRequestedForSession(
        route.sessionID,
        requested: true,
      );
      await (backend.api as PaseoGateway).setSessionModel(
        route.sessionID,
        ModelRef(providerID: 'claude', modelID: 'opus'),
        '',
      );
      expect(port.targets.single.profileId, 'local');
      expect(port.targets.single.sourceId, 'paseo:$dir');
      expect(port.targets.single.sessionId, 'browser-chat');
      await w.controller.closePhoneAgentsForSignInReset();
      expect(w.events.log.indexOf('browser.revoke'), greaterThanOrEqualTo(0));
      expect(
        w.events.log.indexOf('browser.revoke'),
        lessThan(w.events.log.indexOf('host.stop')),
      );
    },
  );

  test('BA4 does not infer Claude resume from its provider name', () async {
    final w = await ready(null);
    addTearDown(w.controller.dispose);
    final proof = w.state.inspectedCapabilities['claude']!;
    expect(proof.resumeVerified, isFalse);
    expect(proof.modelList, isFalse);
    expect(proof.permissions, isFalse);
    expect(proof.images, isFalse);
    expect(proof.cancel, isFalse);
  });

  test(
    'qualified cards skip asks in feed and active chat, disable revokes',
    () async {
      final w = await ready(null, genUiInstaller: _CardInstaller());
      final c = w.controller;
      addTearDown(c.dispose);
      await c.setGenUiEnabled(true);
      Future<void> settle() async {
        for (var i = 0; i < 12; i++) {
          await Future<void>.delayed(Duration.zero);
        }
      }

      void ask(
        FakePaseoSocket socket,
        String id, {
        String name = 'mcp__oc-ui__show',
      }) {
        socket.push('agent_permission_request', {
          'agentId': 'c1',
          'request': {
            'id': id,
            'name': name,
            'kind': 'tool',
            'provider': 'claude',
            'input': {},
            'suggestions': [
              {'type': 'addRules'},
            ],
          },
        });
      }

      final feed = w.host.sockets.first;
      ask(feed, 'feed-card');
      await settle();
      expect(feed.of('agent_permission_response').single['response'], {
        'behavior': 'allow',
      });
      expect(c.autoApprovalFor('c1').automatic, isFalse);

      final row = c.chatFeed().items.firstWhere(
        (r) => r.sourceId == 'paseo:$dir',
      );
      await c.openChatFeedItem(row);
      final backend = c.backendForConversation('c1')!;
      final chat =
          w.host.sockets[w.host.gateways.indexOf(backend.api as PaseoGateway)];
      await settle();
      expect(backend.isConnected, isTrue);
      expect(backend.genUiEnabled, isTrue);
      expect(backend.genUiStatus.agents, contains(GenUiAgent.claude));
      expect(backend.capabilities.genUi, isTrue);
      expect(identical(chat, feed), isFalse);
      ask(chat, 'chat-card');
      await settle();
      expect(chat.of('agent_permission_response').single['response'], {
        'behavior': 'allow',
      });
      expect(backend.permissionsForSession('c1'), isEmpty);
      expect(backend.autoApprovalFor('c1').automatic, isFalse);

      ask(chat, 'other-tool', name: 'mcp__oc-ui__delete');
      await settle();
      expect(chat.of('agent_permission_response'), hasLength(1));
      expect(backend.permissionsForSession('c1').single.id, 'other-tool');

      await c.setGenUiEnabled(false);
      ask(feed, 'disabled-feed');
      ask(chat, 'disabled-chat');
      await settle();
      expect(feed.of('agent_permission_response'), hasLength(1));
      expect(chat.of('agent_permission_response'), hasLength(1));
      expect(
        backend.permissionsForSession('c1').map((p) => p.id),
        contains('disabled-chat'),
      );
    },
  );

  testWidgets('off the phone profile there is no agent surface', (
    tester,
  ) async {
    final w = await _world(tester, phone: false);
    expect(w.controller.phoneAgentsAvailable, isFalse);
    await w.controller.refreshAgentRows();
    expect(w.controller.agentRows, isEmpty);
    expect(w.controller.chatAgentChoices.map((c) => c.agentId), ['opencode']);
    await expectLater(
      w.controller.startAgentChatIn(dir, agentId: 'claude'),
      throwsA(isA<ProductException>()),
    );
    w.controller.dispose();
  });

  testWidgets('rows, chip choices and a persisted, revalidated selection', (
    tester,
  ) async {
    final w = await ready(tester);
    final c = w.controller;
    expect(c.phoneAgentsAvailable, isTrue);
    expect(c.chatAgentChoices.map((x) => x.agentId), ['opencode', 'claude']);
    final claude = c.agentRows.firstWhere((r) => r.id == 'claude');
    expect(claude.chatSelectable, isTrue);
    expect(claude.resumeLabel, "Can't reopen old chats");
    expect(claude.resumeNote, 'Starts a new chat');
    expect(
      c.agentRows.any((r) => r.fixAction == PhoneAgentFixAction.install),
      isTrue,
    );

    expect(c.selectedChatAgentId, 'opencode');
    await c.selectChatAgent('claude');
    expect(c.selectedChatAgentId, 'claude');
    expect(c.store.prefs.getString('oc.chatAgent.local'), 'claude');
    await expectLater(
      c.selectChatAgent('gemini'),
      throwsA(isA<ProductException>()),
    );

    // The host loses the agent: the stored choice falls back to OpenCode.
    w.state.runtimes = {};
    await c.refreshAgentRows();
    expect(c.selectedChatAgentId, 'opencode');
    c.dispose();
  });

  testWidgets('merged feed keeps both sources; rows open by identity', (
    tester,
  ) async {
    final w = await ready(tester);
    final c = w.controller;
    final all = c.chatFeed().items;
    expect(all.where((i) => i.sessionID == 'c1'), hasLength(2));
    final oc = all.firstWhere((i) => i.sourceId == 'opencode');
    final paseo = all.firstWhere((i) => i.sourceId == 'paseo:$dir');
    expect(oc.identity, isNot(paseo.identity));
    expect(paseo.agentId, 'claude');
    expect(
      c.chatFeed(const ChatFeedFilter(agentId: 'claude')).items.single.identity,
      paseo.identity,
    );
    expect(c.chatFeedAcrossProjects, isTrue);

    final openCodeApi = c.api;
    final revision = c.connectionRevision;
    final route = await c.openChatFeedItem(paseo);
    expect(route.sourceId, 'paseo:$dir');
    // The Claude conversation opens on its own backend: this connection
    // stays OpenCode's and is not reconnected.
    final claude = c.backendForConversation(paseo.sessionID)!;
    expect(claude.isAgentBackend, isTrue);
    expect(claude.api, isA<PaseoGateway>());
    expect(claude.directory, dir);
    expect(claude.profile?.name, 'Claude Code');
    expect(identical(c.api, openCodeApi), isTrue);
    expect(c.connectionRevision, revision);
    // Never saved, never the active server.
    expect(
      c.store.profiles.map((p) => p.id),
      isNot(contains(claude.profile?.id)),
    );
    expect(c.store.activeId, c.profile?.id);
    final routed = c.chatFeed().items;
    expect(routed.where((i) => i.sourceId == 'opencode'), hasLength(1));

    final back = await c.openChatFeedItem(oc);
    expect(back.sourceId, 'opencode');
    expect(c.backendForConversation(oc.sessionID), isNull);
    expect(c.api, isNot(isA<PaseoGateway>()));
    expect(c.directory, dir);

    final stale = ChatFeedItem(
      sessionID: 'gone',
      title: 'x',
      directory: dir,
      projectName: 'app',
      isGit: false,
      status: ChatStatus.idle,
      lastActivity: DateTime(2026),
      sourceId: 'paseo:$dir',
      agentId: 'claude',
    );
    await expectLater(
      c.openChatFeedItem(stale),
      throwsA(isA<ProductException>()),
    );
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('old phone-agent chats need an explicit new chat', (
    tester,
  ) async {
    final w = await ready(tester);
    final c = w.controller;
    final items = c.chatFeed().items;
    final oc = items.firstWhere((i) => i.sourceId == 'opencode');
    final paseo = items.firstWhere((i) => i.sourceId == 'paseo:$dir');
    expect(c.agentResumeNotice(oc).canReopen, isTrue);
    final notice = c.agentResumeNotice(paseo);
    expect(notice.canReopen, isFalse);
    expect(notice.requiresAcknowledgement, isTrue);
    expect(notice.label, "Can't reopen old chats");
    expect(notice.note, 'Starts a new chat');

    int creates() => [
      for (final s in w.host.sockets) ...s.of('create_agent_request'),
    ].length;
    await expectLater(
      c.startNewChatReplacing(paseo, newChatAcknowledged: false),
      throwsA(isA<ProductException>()),
    );
    expect(creates(), 0);

    final id = await c.startNewChatReplacing(paseo, newChatAcknowledged: true);
    // A fresh draft with its own id; the old row is untouched and the host
    // creates the agent only when the first prompt is sent.
    expect(id, isNotEmpty);
    expect(id, isNot('c1'));
    expect(c.backendForConversation(id)?.api, isA<PaseoGateway>());
    expect(c.api, isNot(isA<PaseoGateway>()));
    await c.refreshChatFeed();
    expect(
      c.chatFeed().items.where((i) => i.sessionID == 'c1').length,
      2,
      reason: 'the old rows remain',
    );
    // The new chat is live on this connection, so it reopens in place.
    final fresh = ChatFeedItem(
      sessionID: id,
      title: 'New chat',
      directory: dir,
      projectName: 'app',
      isGit: false,
      status: ChatStatus.idle,
      lastActivity: DateTime(2026),
      sourceId: 'paseo:$dir',
      agentId: 'claude',
    );
    expect(c.agentResumeNotice(fresh).canReopen, isTrue);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('the list paints once when agents had conversations before', (
    tester,
  ) async {
    final w = await _world(
      tester,
      prefsExtra: {'oc.phoneAgentsUsed.local': true},
    );
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('c1', dir)];
    w.oc.global = [_ocRow('o1', dir)];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    // OpenCode has answered, but the agents haven't: no first wave.
    expect(c.chatFeed().items, isEmpty);
    expect(c.chatFeed().loading, isTrue);
    await c.refreshAgentRows();
    await tester.pump(const Duration(milliseconds: 100));
    final rows = c.chatFeed().items.map((i) => i.sourceId).toSet();
    expect(rows, {'opencode', 'paseo:$dir'});
    await tester.pump(const Duration(seconds: 5));
    c.dispose();
  });

  testWidgets('saved agent rows paint at once and give way to live ones', (
    tester,
  ) async {
    final w = await _world(
      tester,
      prefsExtra: {
        'oc.phoneAgentsUsed.local': true,
        'oc.agentFeed.local': jsonEncode([
          {
            'sourceId': 'paseo:$dir',
            'sessionID': 'saved1',
            'title': 'Saved chat',
            'directory': dir,
            'projectName': 'app',
            'isGit': false,
            'at': DateTime(2026, 10, 3).millisecondsSinceEpoch,
            'agentId': 'claude',
            'agentLabel': 'Claude Code',
            'canReopen': true,
          },
        ]),
      },
    );
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('c1', dir)];
    w.oc.global = [_ocRow('o1', dir)];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    // Before the helper is read: OpenCode's rows and the saved ones, at once.
    final first = c.chatFeed();
    expect(first.loading, isFalse);
    expect(first.items.map((i) => i.sessionID).toSet(), {'o1', 'saved1'});
    final saved = first.items.firstWhere((i) => i.sessionID == 'saved1');
    expect(saved.status, ChatStatus.idle);
    expect(c.agentResumeNotice(saved).canReopen, isTrue);
    // The list says Claude Code's conversations are still on the way.
    expect(first.stillLoading, ['Claude Code']);
    // It opens on the agent's own backend.
    await c.openChatFeedItem(saved);
    expect(c.backendForConversation('saved1')?.isAgentBackend, isTrue);

    await c.refreshAgentRows();
    await tester.pump(const Duration(milliseconds: 100));
    // The folder has been read live: the host's rows replace the saved ones.
    final ids = c.chatFeed().items.map((i) => i.sessionID).toSet();
    expect(ids, {'o1', 'c1'});
    expect(c.chatFeed().stillLoading, isEmpty);
    expect(
      c.store.prefs.getString('oc.agentFeed.local'),
      allOf(contains('"c1"'), isNot(contains('saved1'))),
    );
    await tester.pump(const Duration(seconds: 5));
    c.dispose();
  });

  Map<String, Object> savedFeed() => {
    'oc.phoneAgentsUsed.local': true,
    'oc.agentFeed.local': jsonEncode([
      {
        'sourceId': 'paseo:$dir',
        'sessionID': 'saved1',
        'title': 'Saved chat',
        'directory': dir,
        'projectName': 'app',
        'isGit': false,
        'at': DateTime(2026, 10, 3).millisecondsSinceEpoch,
        'agentId': 'claude',
        'agentLabel': 'Claude Code',
        'canReopen': true,
      },
    ]),
  };

  testWidgets('a helper still starting is read again once it answers', (
    tester,
  ) async {
    final w = await _world(tester, prefsExtra: savedFeed());
    // The helper isn't listening yet when the app starts.
    w.state.runtimes = {
      'claude': const PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        architectureQualified: true,
      ),
    };
    w.state.agents = [_agent('c1', dir)];
    w.state.failOpens = 1000;
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    await c.refreshAgentRows();
    await tester.pump(const Duration(milliseconds: 100));
    expect(c.chatFeed().items.map((i) => i.sessionID), contains('saved1'));
    expect(c.chatFeed().stillLoading, ['Claude Code']);
    // It answers a moment later: no tap needed for its rows to arrive.
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.failOpens = 0;
    await tester.pump(const Duration(seconds: 6));
    await tester.pump(const Duration(milliseconds: 100));
    expect(c.chatFeed().items.map((i) => i.sessionID), contains('c1'));
    expect(c.chatFeed().stillLoading, isEmpty);
    await tester.pump(const Duration(seconds: 5));
    c.dispose();
  });

  testWidgets('a helper slower than half a minute still gets its rows read', (
    tester,
  ) async {
    final w = await _world(tester, prefsExtra: savedFeed());
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('c1', dir)];
    w.state.failOpens = 1000;
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    await c.refreshAgentRows();
    for (var i = 0; i < 12; i++) {
      await tester.pump(const Duration(seconds: 5));
    }
    // It answers after a minute: the list catches up on its own.
    w.state.failOpens = 0;
    await tester.pump(const Duration(seconds: 16));
    await tester.pump(const Duration(milliseconds: 100));
    expect(c.chatFeed().items.map((i) => i.sessionID), contains('c1'));
    expect(
      c.chatFeed().items.map((i) => i.sessionID),
      isNot(contains('saved1')),
    );
    c.dispose();
  });

  testWidgets('a pull to refresh reaches a helper the retries gave up on', (
    tester,
  ) async {
    final w = await _world(tester, prefsExtra: savedFeed());
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('c1', dir)];
    w.state.failOpens = 1000;
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    await c.refreshAgentRows();
    // The list repaints meanwhile; its first read settles after 30 s.
    for (var i = 0; i < 40; i++) {
      c.chatFeed();
      await tester.pump(const Duration(seconds: 5));
    }
    expect(c.chatFeed().stillLoading, isEmpty);
    expect(c.chatFeed().items.map((i) => i.sessionID), isNot(contains('c1')));
    w.state.failOpens = 0;
    await c.refreshChatFeed();
    await tester.pump(const Duration(milliseconds: 100));
    expect(c.chatFeed().items.map((i) => i.sessionID), contains('c1'));
    c.dispose();
  });

  testWidgets('a helper that never answers stops being called loading', (
    tester,
  ) async {
    final w = await _world(tester, prefsExtra: savedFeed());
    w.state.runtimes = {
      'claude': const PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        architectureQualified: true,
      ),
    };
    w.state.failOpens = 1000;
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    expect(c.chatFeed().stillLoading, ['Claude Code']);
    for (var i = 0; i < 7; i++) {
      await tester.pump(const Duration(seconds: 5));
    }
    final feed = c.chatFeed();
    expect(feed.stillLoading, isEmpty);
    // The saved rows stay.
    expect(feed.items.map((i) => i.sessionID), contains('saved1'));
    c.dispose();
  });

  testWidgets('approval choices made before carry over to the agent backend', (
    tester,
  ) async {
    const auto = {'mode': 'autoOnce', 'inheritToChildren': false};
    final w = await ready(
      tester,
      prefs: {
        'oc.autoApprove.local': jsonEncode({'c1': auto, '*': auto}),
      },
    );
    final c = w.controller;
    final paseo = c.chatFeed().items.firstWhere(
      (i) => i.sourceId == 'paseo:$dir',
    );
    await c.openChatFeedItem(paseo);
    final claude = c.backendForConversation('c1')!;
    expect(claude.autoApprovalFor('c1').automatic, isTrue);
    // "Approve everything" was the phone's OpenCode server's choice.
    expect(claude.autoApprovalFor('other').automatic, isFalse);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets(
    'the agent backend follows the app into the background and back',
    (tester) async {
      final w = await ready(tester);
      final c = w.controller;
      final paseo = c.chatFeed().items.firstWhere(
        (i) => i.sourceId == 'paseo:$dir',
      );
      await c.openChatFeedItem(paseo);
      final claude = c.backendForConversation('c1')!;
      await tester.pump(const Duration(milliseconds: 100));
      expect(claude.isConnected, isTrue);
      // One background mode and one notification owner for both.
      expect(identical(claude.backgroundLive, c.backgroundLive), isTrue);
      expect(claude.keepLiveInBackground, c.keepLiveInBackground);
      c.suspendForLifecycle();
      expect(claude.isConnected, isFalse);
      await c.resumeFromLifecycle();
      await tester.pump(const Duration(milliseconds: 100));
      expect(claude.isConnected, isTrue);
      await tester.pump(const Duration(seconds: 3));
      c.dispose();
    },
  );

  testWidgets('an old conversation with its session handle reopens by '
      'resuming it on the helper', (tester) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('old', dir, status: 'closed')];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final row = c.chatFeed().items.firstWhere((i) => i.sessionID == 'old');
    expect(c.agentResumeNotice(row).canReopen, isTrue);
    final route = await c.openChatFeedItem(row);
    expect(w.state.resumed, ['native-old']);
    expect(c.backendForConversation(route.sessionID)?.isAgentBackend, isTrue);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('a refused resume says so, and the next tap offers a new '
      'conversation', (tester) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [_agent('old', dir, status: 'closed')];
    w.state.resumeFails = true;
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final row = c.chatFeed().items.firstWhere((i) => i.sessionID == 'old');
    await expectLater(
      c.openChatFeedItem(row),
      throwsA(
        isA<ProductException>().having(
          (e) => e.message,
          'message',
          contains('could not reopen this conversation'),
        ),
      ),
    );
    final notice = c.agentResumeNotice(row);
    expect(notice.canReopen, isFalse);
    expect(notice.requiresAcknowledgement, isTrue);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('BA1 agent sign-in comes from CLI even when helper disagrees', (
    tester,
  ) async {
    final w = await _world(tester);
    w.state.runtimes = {
      'claude': _ready('claude'),
      'codex': const PhoneAgentRuntime(
        agentId: 'codex',
        installed: true,
        hostAvailable: true,
        architectureQualified: true,
      ),
    };
    w.state.providers = [
      _provider('claude'),
      {
        ..._provider('codex'),
        'status': 'error',
        'error': 'AuthRequired: run codex login',
      },
    ];
    w.state.auth['codex'] = const AgentAuthProbeResult(
      state: AgentAuthProbeState.signedOut,
    );
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshAgentRows();
    await c.refreshAgentRows();
    AgentRow codex() => c.agentRows.firstWhere((row) => row.id == 'codex');
    expect(codex().status, PhoneAgentStatus.signedOut);
    expect(codex().fixAction, PhoneAgentFixAction.signIn);
    // Only explicit account proof changes the row, not helper readiness.
    w.state.providers = [_provider('claude'), _provider('codex')];
    await c.recheckAgentSignIn('codex');
    expect(codex().status, PhoneAgentStatus.signedOut);
    w.state.auth['codex'] = const AgentAuthProbeResult(
      state: AgentAuthProbeState.signedIn,
    );
    await c.recheckAgentSignIn('codex');
    expect(codex().chatSelectable, isTrue);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  test(
    'BA3 terminal completion requires signed-in probe and BA8 verifies logout',
    () async {
      final w = await ready(null);
      final c = w.controller;
      w.state.auth['claude'] = const AgentAuthProbeResult(
        state: AgentAuthProbeState.signedOut,
      );
      expect(await c.confirmAgentSignIn('claude'), isFalse);
      w.state.auth['claude'] = const AgentAuthProbeResult.failed(
        AgentAuthProbeError.invalidResponse,
      );
      expect(await c.confirmAgentSignIn('claude'), isFalse);
      w.state.auth['claude'] = const AgentAuthProbeResult(
        state: AgentAuthProbeState.signedIn,
        accountDisplayName: 'Example account',
      );
      expect(await c.confirmAgentSignIn('claude'), isTrue);
      expect(c.agentAccount('claude')?.accountDisplayName, 'Example account');
      w.state.logoutResult = const AgentAuthProbeResult(
        state: AgentAuthProbeState.signedIn,
      );
      await expectLater(
        c.signOutAgent('claude'),
        throwsA(isA<ProductException>()),
      );
      expect(c.agentAccount('claude')?.state, AgentAuthProbeState.signedIn);
      w.state.logoutResult = const AgentAuthProbeResult(
        state: AgentAuthProbeState.signedOut,
      );
      await c.signOutAgent('claude');
      expect(c.agentAccount('claude')?.state, AgentAuthProbeState.signedOut);
      expect(c.agentAccount('claude')?.accountDisplayName, isNull);
      expect(w.events.log.where((e) => e == 'logout.claude').length, 2);
      c.dispose();
    },
  );

  test(
    'BA3 a late older probe cannot complete a newer signed-out attempt',
    () async {
      final w = await ready(null);
      final c = w.controller;
      final first = Completer<AgentAuthProbeResult>();
      final last = Completer<AgentAuthProbeResult>();
      var count = 0;
      w.state.probeHandler = (_) => ++count == 1
          ? first.future
          : count >= 4
          ? last.future
          : Future.value(
              const AgentAuthProbeResult(state: AgentAuthProbeState.signedOut),
            );
      final oldAttempt = c.recheckAgentSignIn('claude');
      await Future<void>.delayed(Duration.zero);
      expect(await c.confirmAgentSignIn('claude'), isFalse);
      first.complete(
        const AgentAuthProbeResult(state: AgentAuthProbeState.signedIn),
      );
      await Future<void>.delayed(Duration.zero);
      final whileFinalReadPending = c.agentAccount('claude')?.state;
      last.complete(
        const AgentAuthProbeResult(state: AgentAuthProbeState.signedOut),
      );
      await oldAttempt;
      expect(whileFinalReadPending, AgentAuthProbeState.signedOut);
      expect(c.agentAccount('claude')?.state, AgentAuthProbeState.signedOut);
      expect(c.agentSignInState('claude')?.phase, AgentSignInPhase.signedOut);
      c.dispose();
    },
  );

  test(
    'BA1 missing multi-agent bridge preserves direct Claude status only',
    () async {
      final w = await ready(null);
      final c = w.controller;
      w.signIn.phase = AgentSignInPhase.signedIn;
      w.state.probeHandler = (_) async => const AgentAuthProbeResult.failed(
        AgentAuthProbeError.probeUnsupported,
      );
      await c.recheckAgentSignIn('claude');
      expect(c.agentAccount('claude')?.state, AgentAuthProbeState.signedIn);
      expect(c.agentAccount('claude')?.accountDisplayName, isNull);
      c.dispose();
    },
  );

  group('issue #95: a sign-in check always ends', () {
    const codexInstalled = PhoneAgentRuntime(
      agentId: 'codex',
      installed: true,
      hostAvailable: true,
      architectureQualified: true,
    );

    testWidgets('a provider the helper does not list says it could not '
        'check, on the first read', (tester) async {
      final w = await _world(tester);
      w.state.runtimes = {'claude': _ready('claude'), 'codex': codexInstalled};
      w.state.providers = [_provider('claude')];
      final c = w.controller;
      await c.rememberLastUsedProject(dir);
      await c.refreshAgentRows();
      final codex = c.agentRows.firstWhere((row) => row.id == 'codex');
      expect(codex.hiddenReason, isNot(PhoneAgentHiddenReason.runtimeUnknown));
      expect(codex.statusMessage, contains('could not be checked'));
      expect(codex.fixAction, PhoneAgentFixAction.signIn);
      // The sign-in sheet says the same, instead of "Checking sign-in…".
      final state = c.agentSignInState('codex')!;
      expect(state.inspected, isTrue);
      expect(state.phase, AgentSignInPhase.failed);
      await tester.pump(const Duration(seconds: 6));
      c.dispose();
    });
  });

  testWidgets('a resumed session is one row, with the title it had', (
    tester,
  ) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [
      {
        ..._agent('old', dir, status: 'closed', minute: 10),
        'title': 'Fix the login page',
        'persistence': {'provider': 'claude', 'sessionId': 'native-x'},
      },
      {
        ..._agent('new', dir, minute: 40),
        'title': '',
        'persistence': {'provider': 'claude', 'sessionId': 'native-x'},
      },
    ];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    final rows = c.chatFeed().items.where((i) => i.sourceId == 'paseo:$dir');
    expect(rows.map((i) => i.sessionID), ['new']);
    expect(rows.single.title, 'Fix the login page');
    // Opened, the conversation's own header says what its row says.
    await c.openChatFeedItem(rows.single);
    final backend = c.backendForConversation('new')!;
    await backend.ensureSession('new');
    expect(backend.sessionsById['new']?.title, 'Fix the login page');
    // Nothing is written to the helper (that would move the row to the top).
    final renames = [
      for (final socket in w.host.sockets)
        for (final request in socket.sent)
          if (request['type'] == 'update_agent_request') request,
    ];
    expect(renames, isEmpty);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('at app start the helper is checked beside the server, only '
      'where agents had conversations', (tester) async {
    // The app restarts on the phone profile it was on.
    final fresh = await _world(
      tester,
      prefsExtra: {'oc.activeProfile': 'local'},
    );
    expect(fresh.controller.agentRows, isEmpty);
    fresh.controller.dispose();
    final used = await _world(
      tester,
      prefsExtra: {
        'oc.activeProfile': 'local',
        'oc.phoneAgentsUsed.local': true,
      },
    );
    // Read without anyone opening an agent screen.
    expect(used.controller.agentRows, isNotEmpty);
    await tester.pump(const Duration(seconds: 5));
    used.controller.dispose();
  });

  testWidgets('without earlier agent conversations the list never waits', (
    tester,
  ) async {
    final w = await _world(tester);
    w.oc.global = [_ocRow('o1', dir)];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshChatFeed();
    expect(c.chatFeed().items.map((i) => i.sessionID), ['o1']);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('a chat the host still runs reopens after an app restart', (
    tester,
  ) async {
    final w = await _world(tester);
    w.state.runtimes = {'claude': _ready('claude')};
    w.state.agents = [
      _agent('live', dir),
      _agent('busy', dir, status: 'running'),
      _agent('old', dir, status: 'closed', persistence: false),
    ];
    final c = w.controller;
    await c.rememberLastUsedProject(dir);
    await c.refreshAgentRows();
    await c.refreshChatFeed();
    bool reopens(String id) => c
        .agentResumeNotice(
          c.chatFeed().items.firstWhere(
            (i) => i.sessionID == id && i.sourceId == 'paseo:$dir',
          ),
        )
        .canReopen;
    expect(reopens('live'), isTrue);
    expect(reopens('busy'), isTrue);
    expect(reopens('old'), isFalse);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('starting chats routes to OpenCode or the phone host', (
    tester,
  ) async {
    final w = await ready(tester);
    final c = w.controller;
    expect(await c.startAgentChatIn(dir, agentId: 'opencode'), 'oc-new-1');
    expect(w.oc.created, [dir]);
    expect(c.api, isNot(isA<PaseoGateway>()));

    await c.selectChatAgent('claude');
    final id = await c.startChatIn(dir);
    expect(id, isNotEmpty);
    expect(id, isNot('c1'));
    expect(c.backendForConversation(id)?.api, isA<PaseoGateway>());
    expect(c.api, isNot(isA<PaseoGateway>()));
    expect(c.lastUsedProjectDirectory, dir);

    await expectLater(
      c.startAgentChatIn(dir, agentId: 'gemini'),
      throwsA(isA<ProductException>()),
    );
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('a stopped helper is started again when a chat needs it', (
    tester,
  ) async {
    final w = await ready(tester);
    final c = w.controller;
    const other = '/root/projects/other';
    w.host.failOpens = 1;
    final before = w.events.log.where((e) => e == 'host.start').length;
    final id = await c.startAgentChatIn(other, agentId: 'claude');
    expect(id, isNotEmpty);
    expect(c.backendForConversation(id)?.api, isA<PaseoGateway>());
    expect(w.events.log.where((e) => e == 'host.start').length, before + 1);
    final models = await c.agentModels('claude');
    expect(
      models.firstWhere((m) => m.name == 'claude model').isDefault,
      isTrue,
    );
    await c.selectAgentModel('claude', 'claude-model');
    expect(c.selectedAgentModel('claude'), 'claude-model');
    await expectLater(
      c.startAgentChatIn('/storage/emulated/0/x', agentId: 'claude'),
      throwsA(
        isA<ProductException>().having(
          (e) => e.message,
          'message',
          contains('/root/projects'),
        ),
      ),
    );
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('limit and stopped rows surface as status lines', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    final reset = DateTime.utc(2026, 10, 4, 9);
    w.state.runtimes = {
      'claude': PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        hostAvailable: true,
        architectureQualified: true,
        signInPhase: AgentSignInPhase.limitReached,
        resetAt: reset,
      ),
    };
    await c.refreshAgentRows();
    final line = c.agentStatusLines.single;
    expect(line.kind, PhoneAgentStatusLineKind.limitReached);
    expect(line.resetAt, reset);
    w.state.runtimes = {
      'claude': PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        hostAvailable: false,
        architectureQualified: true,
        stoppedInBackground: true,
      ),
    };
    await c.refreshAgentRows();
    // Opening an agent screen reads the rows; a stopped helper is started
    // again without a Resume tap, at most once a minute. "Stopped" isn't
    // said while that runs.
    expect(c.agentStatusLines, isEmpty);
    await tester.pump();
    expect(w.events.log.where((e) => e == 'host.start'), hasLength(1));
    // Still stopped afterwards: now it is said, with its Resume.
    await tester.pump(const Duration(milliseconds: 50));
    expect(c.agentStatusLines.single.kind, PhoneAgentStatusLineKind.stopped);
    await c.refreshAgentRows();
    await tester.pump();
    expect(w.events.log.where((e) => e == 'host.start'), hasLength(1));
    c.dispose();
  });

  test(
    'BA5 protocol switch keeps host account and conversation rows',
    () async {
      final w = await ready(null);
      final c = w.controller;
      w.signIn.phase = AgentSignInPhase.signedIn;
      await c.recheckAgentSignIn('claude');
      final original = c.store.profiles.single;
      final two = ServerProfile(
        id: 'two',
        name: 'OpenCode 2',
        baseUrl: original.baseUrl,
        flavor: ServerFlavor.v2,
      );
      await c.store.upsert(two);
      final owner = c.agentSignInProfileId;
      final hosts = w.hosts.length;
      await c.connect(two);
      await c.refreshAgentRows();
      expect(c.agentSignInProfileId, owner);
      expect(c.agentSignInState('claude')?.phase, AgentSignInPhase.signedIn);
      expect(w.hosts.length, hosts);
      expect(
        c.chatFeed().items.any(
          (row) => row.sourceId?.startsWith('paseo:') == true,
        ),
        isTrue,
      );
      await c.connect(original);
      await c.refreshAgentRows();
      expect(c.agentSignInProfileId, owner);
      expect(w.hosts.length, hosts);
      expect(
        c.agentRows.firstWhere((row) => row.id == 'claude').chatSelectable,
        isTrue,
      );
      c.dispose();
    },
  );

  test(
    'deletion closes auth, setup, host and feeds before ProfileStore',
    () async {
      final w = await ready(
        null,
        secure: _FakeSecure({'oc.agentHostSecret.local': 'x' * 64}),
      );
      final c = w.controller;
      w.signIn.phase = AgentSignInPhase.signedIn;
      await c.recheckAgentSignIn('claude');
      expect(c.agentSignInState('claude'), isNotNull);
      expect(w.host.gateways, isNotEmpty);
      w.events.log.clear();

      final result = await c.deleteProfileAndLocalData('local');
      expect(result.removedProfile, isTrue);
      expect(w.events.log, [
        'host.cancelInstall',
        'host.stop',
        'host.dispose',
        'profileStore.cleanup',
      ]);
      expect(w.host.gateways.every((g) => g.isClosed), isTrue);
      expect(c.agentRows, isEmpty);
      c.dispose();
    },
  );

  test(
    'clearing saved sign-ins closes clients and asks for a restart',
    () async {
      final w = await ready(null);
      final c = w.controller;
      expect(c.phoneAgentsNeedRestart, isFalse);
      await c.closePhoneAgentsForSignInReset();
      expect(c.phoneAgentsNeedRestart, isTrue);
      expect(c.phoneAgentsAvailable, isFalse);
      expect(c.agentRows, isEmpty);
      expect(
        w.events.log,
        containsAllInOrder(['host.cancelInstall', 'host.stop', 'host.dispose']),
      );
      expect(w.host.gateways.every((g) => g.isClosed), isTrue);
      await expectLater(
        c.startAgentChatIn(dir, agentId: 'claude'),
        throwsA(isA<ProductException>()),
      );
      c.dispose();
    },
  );
}

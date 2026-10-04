import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:opencode_mobile/domain/phone_agents.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_agent_host_port.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

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

class _HostState {
  Map<String, PhoneAgentRuntime> runtimes = {};
  List<Map<String, dynamic>> agents = [];
  var newAgentSeq = 0;
  AgentPhoneCheckResult? check;
}

class _FakeHost implements PhoneAgentHostPort {
  _FakeHost(this.events, this.profileId, this.state);
  final _Events events;
  final String profileId;
  final _HostState state;
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

  /// How many next connects fail like a helper Android stopped.
  int failOpens = 0;

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
        'entries': [_provider('claude')],
      },
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
    socket.handlers['create_agent_request'] = (request) {
      final agent = _agent('claude-new-${++state.newAgentSeq}', directory);
      agents = [...agents, agent];
      return ('create_agent_response', {'agent': agent});
    };
    final gateway = PaseoGateway(
      directory: directory,
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:4099',
        socketFactory: (_, _) async => socket,
      ),
    );
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
  'persistence': {'provider': provider, 'sessionId': 'native-$id'},
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

  const dir = _project;

  Future<_World> ready(
    WidgetTester? tester, {
    Map<String, Object>? prefs,
    FlutterSecureStorage? secure,
  }) async {
    final w = await _world(
      tester,
      prefsExtra: prefs ?? const {},
      secure: secure,
    );
    w.state.runtimes = {'claude': _ready('claude')};
    // Left over from an earlier host run: listed, but not loaded.
    w.state.agents = [_agent('c1', dir, status: 'closed')];
    w.oc.global = [_ocRow('c1', dir)];
    await w.controller.rememberLastUsedProject(dir);
    await w.controller.refreshAgentRows();
    await w.controller.refreshChatFeed();
    return w;
  }

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
    // It opens on the agent's own backend.
    await c.openChatFeedItem(saved);
    expect(c.backendForConversation('saved1')?.isAgentBackend, isTrue);

    await c.refreshAgentRows();
    await tester.pump(const Duration(milliseconds: 100));
    // The folder has been read live: the host's rows replace the saved ones.
    final ids = c.chatFeed().items.map((i) => i.sessionID).toSet();
    expect(ids, {'o1', 'c1'});
    expect(
      c.store.prefs.getString('oc.agentFeed.local'),
      allOf(contains('"c1"'), isNot(contains('saved1'))),
    );
    await tester.pump(const Duration(seconds: 5));
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
      _agent('old', dir, status: 'closed'),
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

  testWidgets('a sign-in that prints its page first still reaches the code', (
    tester,
  ) async {
    final w = await _world(tester);
    final c = w.controller;
    w.signIn
      ..urlFirst = true
      ..promptPrinted = false;
    w.state.runtimes = {
      'claude': PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        hostAvailable: true,
        architectureQualified: true,
      ),
    };
    await c.refreshAgentRows();
    await c.startAgentSignIn('claude');
    // The page is ready; the code prompt is not printed yet.
    expect(c.agentSignInState('claude')?.phase, AgentSignInPhase.urlReady);
    expect(c.agentSignInUrl('claude')?.host, 'claude.com');
    // The person logs in and comes back: sending the code reads the prompt
    // first, so the code is accepted.
    w.signIn.promptPrinted = true;
    final code = AgentSignInCode('abc#def');
    await c.submitAgentSignInCode('claude', code);
    expect(w.signIn.submitted, ['abc#def']);
    await tester.pump(const Duration(seconds: 3));
    c.dispose();
  });

  testWidgets('sign-in state, URL as data, one-shot code, cancel drain', (
    tester,
  ) async {
    final w = await _world(tester);
    final c = w.controller;
    w.state.runtimes = {
      'claude': PhoneAgentRuntime(
        agentId: 'claude',
        installed: true,
        hostAvailable: true,
        architectureQualified: true,
      ),
    };
    await c.refreshAgentRows();
    expect(c.agentSignInState('claude')?.phase, AgentSignInPhase.signedOut);
    expect(c.agentSignInState('claude')?.inspected, isTrue);
    expect(c.agentStatusLines.single.kind, PhoneAgentStatusLineKind.signedOut);
    expect(c.agentSignInUrl('claude'), isNull);

    await c.startAgentSignIn('claude');
    expect(c.agentSignInState('claude')?.phase, AgentSignInPhase.awaitingCode);
    expect(c.agentSignInUrl('claude')?.host, 'claude.com');

    final code = AgentSignInCode('abc#def');
    await c.submitAgentSignInCode('claude', code);
    expect(code.consumed, isTrue);
    expect(w.signIn.submitted, ['abc#def']);
    await tester.pump();
    w.state.runtimes = {'claude': _ready('claude')};
    await c.refreshAgentRows();
    expect(
      c.agentRows.firstWhere((r) => r.id == 'claude').chatSelectable,
      isTrue,
    );
    expect(c.agentSignInUrl('claude'), isNull);

    await tester.runAsync(() => c.cancelAgentSignIn('claude'));
    expect(w.events.log, contains('auth.cancelAndDrain'));
    expect(c.agentSignInState('claude'), isNull);
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
    expect(c.agentStatusLines.single.kind, PhoneAgentStatusLineKind.stopped);
    // Opening an agent screen reads the rows; a stopped helper is started
    // again without a Resume tap, at most once a minute.
    await tester.pump();
    expect(w.events.log.where((e) => e == 'host.start'), hasLength(1));
    await c.refreshAgentRows();
    await tester.pump();
    expect(w.events.log.where((e) => e == 'host.start'), hasLength(1));
    c.dispose();
  });

  test(
    'deletion closes auth, setup, host and feeds before ProfileStore',
    () async {
      final w = await ready(
        null,
        secure: _FakeSecure({'oc.agentHostSecret.local': 'x' * 64}),
      );
      final c = w.controller;
      w.signIn.phase = AgentSignInPhase.signedIn;
      await c.startAgentSignIn('claude');
      expect(c.agentSignInState('claude'), isNotNull);
      expect(w.host.gateways, isNotEmpty);
      w.events.log.clear();

      final result = await c.deleteProfileAndLocalData('local');
      expect(result.removedProfile, isTrue);
      expect(w.events.log, [
        'auth.cancelAndDrain',
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

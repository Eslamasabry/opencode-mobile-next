import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_agent_host_port.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart' show TermuxBridge;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';
import 'package:flutter/material.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'package:shared_preferences/shared_preferences.dart';

// The one Conversations list over every connection on this phone (see
// docs/design/all-connections-one-list-2026-10-06.md): the in-app Ubuntu as
// the main connection, Termux beside it, a server elsewhere only when shown.

const _ubuntu = 'http://127.0.0.1:4097';
final _termux = 'http://127.0.0.1:${TermuxBridge.managedServerPort}';
const _vps = 'https://vps.example.com';

class _RealHttpOverrides extends HttpOverrides {}

/// One server's sessions, by base URL.
class _Server {
  List<GlobalSessionResult> global = [];
  Completer<Health>? gate;

  /// Not answering (Termux stopped).
  bool down = false;

  /// Permission requests waiting, and the replies the server received.
  List<PermissionRequest> permissions = [];
  final replies = <(String, String)>[];
}

class _Api extends OpenCodeApi {
  _Api(this.server, String baseUrl) : super(baseUrl: baseUrl);
  final _Server server;

  @override
  Future<Health> health() => server.down
      ? Future.error(ApiException('Connection refused'))
      : server.gate?.future ??
            Future.value(Health(healthy: true, version: '1'));
  @override
  Future<List<Session>> sessions() async => const [];
  @override
  Future<Session> session(String id) async => server.global
      .map((row) => row.session)
      .firstWhere((session) => session.id == id);
  @override
  Future<Map<String, String>> sessionStatuses() async => const {};
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<ProvidersResponse> configuredProviders() async =>
      ProvidersResponse(providers: const []);
  @override
  Future<List<AgentInfo>> agents() async => const [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async =>
      server.permissions;
  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    server.replies.add((requestID, reply));
    server.permissions = [
      for (final p in server.permissions)
        if (p.id != requestID) p,
    ];
  }

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() =>
      Future.error(ApiException('V2 unavailable', statusCode: 404));
  @override
  Future<List<FileNode>> listFiles(String path) async => const [];
}

class _Repo extends SdkProductRepository {
  _Repo(this.api) : super(api.sdkClient);
  final _Api api;
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
  }) async => ServerPage(items: api.server.global);
}

class _Stream extends EventStream {
  _Stream({
    required super.api,
    required super.onEvent,
    required super.onStatus,
    super.onError,
  });
  @override
  void start() => onStatus(StreamStatus.connected);
  @override
  Future<void> dispose() async {}
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

GlobalSessionResult _row(String id, String directory, {int updated = 5}) =>
    GlobalSessionResult(
      session: Session(
        id: id,
        title: 'Chat $id',
        directory: directory,
        time: SessionTime(created: 0, updated: updated),
      ),
      projectDirectory: directory,
    );

class _World {
  _World(this.controller, this.servers);
  final ConnectionController controller;
  final Map<String, _Server> servers;
}

Future<_World> _world(
  WidgetTester tester, {
  Map<String, Object> prefsExtra = const {},
  String main = 'ubuntu',
  PhoneAgentHostPort Function(ServerProfile profile)? agents,
  void Function(Map<String, _Server> servers)? before,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'ubuntu', 'name': 'This phone', 'baseUrl': _ubuntu},
      {'id': 'termux', 'name': 'Termux', 'baseUrl': _termux},
      {'id': 'vps', 'name': 'VPS', 'baseUrl': _vps},
    ]),
    ...prefsExtra,
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final servers = {_ubuntu: _Server(), _termux: _Server(), _vps: _Server()};
  servers[_ubuntu]!.global = [_row('u1', '/root/projects/app', updated: 9)];
  servers[_termux]!.global = [_row('t1', '/data/home/site', updated: 7)];
  servers[_vps]!.global = [_row('v1', '/srv/api', updated: 3)];
  before?.call(servers);
  final controller = ConnectionController(
    store,
    apiFactory: (profile) => _Api(servers[profile.baseUrl]!, profile.baseUrl),
    repositoryFactory: (api) => _Repo(api as _Api),
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
    phoneAgentHostFactory: agents,
  );
  await controller.connect(
    store.profiles.firstWhere((profile) => profile.id == main),
  );
  await tester.pump(const Duration(milliseconds: 50));
  await controller.refreshChatFeed();
  await tester.pump(const Duration(milliseconds: 50));
  return _World(controller, servers);
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

  testWidgets('Termux joins the list beside the in-app Ubuntu; a server '
      'elsewhere only when shown', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    final rows = c.chatFeed().items;
    expect(rows.map((item) => item.sessionID), ['u1', 't1']);
    final termux = rows.firstWhere((item) => item.sessionID == 't1');
    expect(termux.sourceId, 'profile:termux');
    expect(termux.sourceLabel, 'Termux');
    // Beside another server, this one's rows name it too.
    expect(rows.first.sourceLabel, 'This phone');

    final sources = {for (final s in c.chatListSources) s.id: s};
    expect(sources['ubuntu']!.main, isTrue);
    expect(sources['termux']!.shown, isTrue);
    expect(sources['termux']!.onThisPhone, isTrue);
    expect(sources['vps']!.shown, isFalse);
    expect(sources['vps']!.onThisPhone, isFalse);

    await c.setChatListSourceShown('vps', true);
    await tester.pump(const Duration(milliseconds: 50));
    await c.refreshChatFeed();
    await tester.pump(const Duration(milliseconds: 50));
    expect(c.chatFeed().items.map((item) => item.sessionID), [
      'u1',
      't1',
      'v1',
    ]);
    c.dispose();
  });

  testWidgets('a Termux row opens on Termux\'s own connection', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    final row = c.chatFeed().items.firstWhere((item) => item.sessionID == 't1');
    final route = await c.openChatFeedItem(row);
    expect(route.sessionID, 't1');
    final backend = c.backendForConversation('t1')!;
    expect(backend.isSideBackend, isTrue);
    expect(backend.profile?.id, 'termux');
    expect(backend.directory, '/data/home/site');
    // The app stays on the in-app Ubuntu.
    expect(c.profile?.id, 'ubuntu');
    expect(c.backendForConversation('u1'), isNull);
    c.dispose();
  });

  testWidgets('a hidden source leaves the list and its connection closes', (
    tester,
  ) async {
    final w = await _world(tester);
    final c = w.controller;
    final row = c.chatFeed().items.firstWhere((item) => item.sessionID == 't1');
    await c.openChatFeedItem(row);
    final side = c.backendForConversation('t1')!;
    await c.setChatListSourceShown('termux', false);
    await tester.pump(const Duration(milliseconds: 50));
    expect(c.chatFeed().items.map((item) => item.sessionID), ['u1']);
    expect(c.backendForConversation('t1'), isNull);
    expect(side.isConnected, isFalse);
    // The main connection can't be hidden.
    await c.setChatListSourceShown('ubuntu', false);
    expect(
      c.chatListSources.firstWhere((source) => source.id == 'ubuntu').shown,
      isTrue,
    );
    c.dispose();
  });

  testWidgets('the list says Termux is still loading while it connects', (
    tester,
  ) async {
    final gate = Completer<Health>();
    final w = await _world(
      tester,
      before: (servers) => servers[_termux]!.gate = gate,
    );
    final c = w.controller;
    final first = c.chatFeed();
    expect(first.items.map((item) => item.sessionID), contains('u1'));
    expect(first.stillLoadingServers, ['termux']);
    gate.complete(Health(healthy: true, version: '1'));
    await tester.pump(const Duration(milliseconds: 50));
    await c.refreshChatFeed();
    await tester.pump(const Duration(milliseconds: 50));
    final next = c.chatFeed();
    expect(next.items.map((item) => item.sessionID), ['u1', 't1']);
    expect(next.stillLoadingServers, isEmpty);
    c.dispose();
  });

  testWidgets('on Termux, the agents of the in-app Ubuntu beside it still '
      'list', (tester) async {
    final w = await _world(
      tester,
      main: 'termux',
      agents: (_) => throw UnimplementedError('not reached'),
    );
    final c = w.controller;
    expect(c.chatFeed().items.map((item) => item.sessionID), ['u1', 't1']);
    expect(c.phoneAgentsAvailable, isTrue);
    final agents = c.chatListSources.where(
      (source) => source.kind == ChatListSourceKind.agents,
    );
    expect(agents.single.id, 'ubuntu.agents');
    expect(agents.single.shown, isTrue);
    c.dispose();
  });

  testWidgets('a Termux that isn\'t answering is named, and Try again '
      'reaches it once it is back', (tester) async {
    final w = await _world(
      tester,
      before: (servers) => servers[_termux]!.down = true,
    );
    final c = w.controller;
    await tester.pump(const Duration(milliseconds: 100));
    expect(c.chatFeed().unreachableServers, ['termux']);
    expect(
      c.chatListSources.firstWhere((s) => s.id == 'termux').unreachable,
      isTrue,
    );
    w.servers[_termux]!.down = false;
    await c.retryChatListSource('termux');
    await tester.pump(const Duration(milliseconds: 50));
    await c.refreshChatFeed();
    await tester.pump(const Duration(milliseconds: 50));
    expect(c.chatFeed().unreachableServers, isEmpty);
    expect(c.chatFeed().items.map((item) => item.sessionID), contains('t1'));
    c.dispose();
  });

  testWidgets('after a restart, Termux\'s rows from last time show at once '
      'and open on Termux while it connects', (tester) async {
    final first = await _world(tester);
    await tester.pump(const Duration(milliseconds: 400));
    expect(
      first.controller.store.prefs.getString('oc.sideFeed.termux'),
      contains('"t1"'),
    );
    final saved = first.controller.store.prefs.getString('oc.sideFeed.termux')!;
    first.controller.dispose();

    final gate = Completer<Health>();
    final w = await _world(
      tester,
      prefsExtra: {'oc.sideFeed.termux': saved},
      // Termux hasn't answered yet: nothing of its own is listed live.
      before: (servers) => servers[_termux]!
        ..gate = gate
        ..global = [],
    );
    final c = w.controller;
    final row = c.chatFeed().items.firstWhere((item) => item.sessionID == 't1');
    expect(row.sourceId, 'profile:termux');
    await c.openChatFeedItem(row);
    expect(c.backendForConversation('t1')?.profile?.id, 'termux');
    gate.complete(Health(healthy: true, version: '1'));
    await tester.pump(const Duration(milliseconds: 100));
    c.dispose();
  });

  testWidgets('a model\'s name is remembered across restarts, per server', (
    tester,
  ) async {
    final first = await _world(tester);
    first.controller.rememberModelName('claude', 'claude-sonnet-5', 'Sonnet 5');
    await tester.pump();
    final saved = first.controller.store.prefs.getString(
      'oc.modelNames.ubuntu',
    )!;
    expect(
      first.controller.store.profileScopedPreferenceKeys('ubuntu'),
      contains('oc.modelNames.ubuntu'),
    );
    first.controller.dispose();
    final w = await _world(tester, prefsExtra: {'oc.modelNames.ubuntu': saved});
    expect(
      w.controller.knownModelName('claude', 'claude-sonnet-5'),
      'Sonnet 5',
    );
    w.controller.dispose();
  });

  testWidgets('each row answers on its own connection', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    final rows = {for (final item in c.chatFeed().items) item.sessionID: item};
    expect(identical(c.connectionForRow(rows['u1']!), c), isTrue);
    expect(c.connectionForRow(rows['t1']!)?.profile?.id, 'termux');
    c.dispose();
  });

  testWidgets('a Termux request is answered from the list, on Termux', (
    tester,
  ) async {
    final w = await _world(
      tester,
      before: (servers) => servers[_termux]!.permissions = [
        PermissionRequest(id: 'p1', sessionID: 't1', permission: 'bash'),
      ],
    );
    final c = w.controller;
    final side = c.connectionForRow(
      c.chatFeed().items.firstWhere((item) => item.sessionID == 't1'),
    )!;
    await side.refreshPendingPermissions();
    await tester.pump(const Duration(milliseconds: 50));
    final row = c.chatFeed().items.firstWhere((item) => item.sessionID == 't1');
    final host = ConnectionChatsHost(c);
    await tester.pumpWidget(
      MaterialApp(
        theme: captureTheme(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: Scaffold(
          body: Builder(
            builder: (context) =>
                host.listRequest(context, row) ?? const SizedBox(),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('Run a shell command'), findsOneWidget);
    // Allow, then Undo inside the window: nothing is sent.
    await tester.tap(find.byKey(const Key('permission-card-allow')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('permission-card-undo')));
    await tester.pump(const Duration(seconds: 4));
    expect(w.servers[_termux]!.replies, isEmpty);
    await tester.tap(find.byKey(const Key('permission-card-allow')));
    // Held for its Undo window, then sent to Termux, not this server.
    await tester.pump(const Duration(seconds: 4));
    expect(w.servers[_termux]!.replies, [('p1', 'once')]);
    expect(w.servers[_ubuntu]!.replies, isEmpty);
    await tester.pumpWidget(const SizedBox());
    c.dispose();
  });

  testWidgets('a run that ends while you are away says Done until you open '
      'it, on any server', (tester) async {
    final w = await _world(tester);
    final c = w.controller;
    bool done() => c
        .chatFeed()
        .items
        .firstWhere((item) => item.sessionID == 'u1')
        .finishedUnseen;
    expect(done(), isFalse);
    c.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: const {
          'sessionID': 'u1',
          'status': {'type': 'busy'},
        },
      ),
    );
    c.handleEventForTesting(
      EventEnvelope(
        type: 'session.status',
        properties: const {
          'sessionID': 'u1',
          'status': {'type': 'idle'},
        },
      ),
    );
    await tester.pump();
    expect(done(), isTrue);
    // Opening it clears Done.
    await c.ensureSession('u1');
    expect(done(), isFalse);
    c.dispose();
  });

  testWidgets('a source the person hid stays hidden after a restart', (
    tester,
  ) async {
    final w = await _world(
      tester,
      prefsExtra: {'oc.chatListShown.termux': false},
    );
    expect(w.controller.chatFeed().items.map((item) => item.sessionID), ['u1']);
    // The choice is the profile's: deleting Termux sweeps it.
    expect(
      w.controller.store.profileScopedPreferenceKeys('termux'),
      contains('oc.chatListShown.termux'),
    );
    w.controller.dispose();
  });
}

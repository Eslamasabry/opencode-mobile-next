import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _RealHttpOverrides extends HttpOverrides {}

class _Script {
  Map<String, String> statuses = {};
  List<Session> local = [];
  List<GlobalSessionResult> global = [];
  List<WorkspaceProject> projects = [];
  bool acrossProjects = true;
  bool projectsAreGit = true;
  int globalCalls = 0;
  Completer<ServerPage<GlobalSessionResult>>? heldGlobal;
}

class _Api extends OpenCodeApi {
  _Api(this.script) : super(baseUrl: 'http://127.0.0.1:1');
  final _Script script;
  final health_ = Completer<Health>();
  String? located;

  @override
  ServerCapabilities get capabilities => !script.projectsAreGit
      ? const ServerCapabilities(projectsAreGitRepositories: false)
      : script.acrossProjects
      ? ServerCapabilities.allV1
      : const ServerCapabilities(globalSessionSearch: false);

  @override
  void setLocation({String? directory, String? workspace}) {
    located = directory;
    super.setLocation(directory: directory, workspace: workspace);
  }

  @override
  Future<Health> health() => health_.future;

  @override
  Future<List<Session>> sessions() async => [
    for (final s in script.local)
      if (s.directory == located || located == null) s,
  ];

  @override
  Future<Map<String, String>> sessionStatuses() async => script.statuses;

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

class _Repo extends SdkProductRepository {
  _Repo(OpenCodeApi api, this.script) : super(api.sdkClient);
  final _Script script;

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
  Future<List<WorkspaceProject>> listProjects() async => script.projects;

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async => const [];

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) async {
    script.globalCalls += 1;
    return script.heldGlobal?.future ?? ServerPage(items: script.global);
  }
}

GlobalSessionResult _global(String id, String directory) => GlobalSessionResult(
  session: Session(id: id, title: id, directory: directory),
  projectDirectory: directory,
);

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

class _V2Gateway implements ServerGateway {
  _V2Gateway(this.api, this.channels);
  final _Api api;
  final List<_Stream> channels;
  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;
  @override
  Future<Health> health() => api.health();
  @override
  String? get directory => api.directory;
  @override
  String? get workspace => api.workspace;
  @override
  bool get isClosed => api.isClosed;
  @override
  void setLocation({String? directory, String? workspace}) =>
      api.setLocation(directory: directory, workspace: workspace);
  @override
  void close() => api.close();
  @override
  Future<List<Session>> sessions() => api.sessions();
  @override
  Future<Map<String, String>> sessionStatuses() => api.sessionStatuses();
  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) {
    final stream = _Stream(
      api: api,
      onEvent: onEvent,
      onStatus: onStatus,
      onError: onError,
    );
    channels.add(stream);
    return stream;
  }

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) =>
      openEventChannel(onEvent: onEvent, onStatus: onStatus, onError: onError);
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw UnimplementedError('unused fake gateway method');
}

class _Controller extends ConnectionController {
  _Controller(
    super.store, {
    super.apiFactory,
    super.repositoryFactory,
    super.v2GatewayFactory,
    super.eventStreamFactory,
    super.globalEventStreamFactory,
    super.localWakeLockEnsurer,
  });

  bool holdMergedRefresh = false;
  final mergedRefresh = Completer<void>();
  @override
  Future<void> refreshChatFeed() =>
      holdMergedRefresh ? mergedRefresh.future : super.refreshChatFeed();
}

class _Harness {
  _Harness(ProfileStore store) {
    controller = _Controller(
      store,
      apiFactory: (_) => makeApi(),
      repositoryFactory: (api) => _Repo(api, script),
      v2GatewayFactory: (_) {
        final api = makeApi();
        return (
          gateway: _V2Gateway(api, channels),
          operations: _Repo(api, script),
        );
      },
      eventStreamFactory: stream,
      globalEventStreamFactory: stream,
      localWakeLockEnsurer: () async {},
    );
  }
  _Script script = _Script();
  final apis = <_Api>[];
  final channels = <_Stream>[];
  late final _Controller controller;
  _Api makeApi() {
    final api = _Api(script);
    api.health_.complete(Health(healthy: true, version: 'fixture'));
    apis.add(api);
    return api;
  }

  EventStream stream({
    required OpenCodeApi api,
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) {
    final channel = _Stream(
      api: api,
      onEvent: onEvent,
      onStatus: onStatus,
      onError: onError,
    );
    channels.add(channel);
    return channel;
  }

  Future<void> connect({bool v2 = false, String? id}) => controller.connect(
    ServerProfile(
      id: id ?? (v2 ? 'v2' : 'v1'),
      name: 'fixture',
      baseUrl: 'http://127.0.0.1:1',
      flavor: v2 ? ServerFlavor.v2 : ServerFlavor.v1,
    ),
  );
}

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  for (final v2 in [false, true]) {
    testWidgets('cold ${v2 ? 'v2' : 'v1'} loads before any feed reader', (
      tester,
    ) async {
      final h = _Harness(await _store());
      try {
        h.script.global = [_global('elsewhere', '/work/other')];
        await h.connect(v2: v2);
        for (final channel in h.channels) {
          channel.onStatus(StreamStatus.connected);
        }
        await tester.pump(const Duration(milliseconds: 1999));
        expect(h.script.globalCalls, 0);
        await tester.pump(const Duration(milliseconds: 1));
        expect(h.script.globalCalls, 1);
        expect(h.controller.chatFeed().items.single.sessionID, 'elsewhere');
        for (var i = 0; i < 10; i++) {
          h.controller.chatFeed();
        }
        await tester.pump(const Duration(seconds: 4));
        expect(h.script.globalCalls, 1);
      } finally {
        h.controller.dispose();
      }
    });

    testWidgets('${v2 ? 'v2' : 'v1'} reconnect and event burst coalesce', (
      tester,
    ) async {
      final h = _Harness(await _store());
      try {
        await h.connect(v2: v2);
        for (final channel in h.channels) {
          channel.onStatus(StreamStatus.connected);
        }
        await tester.pump(const Duration(seconds: 2));
        expect(h.script.globalCalls, 1);
        h.script.global = [_global('after-reconnect', '/work/other')];
        h.channels.first.onStatus(StreamStatus.reconnecting);
        h.channels.first.onStatus(StreamStatus.connected);
        await tester.pump(const Duration(seconds: 2));
        expect(h.script.globalCalls, 2);
        expect(
          h.controller.chatFeed().items.single.sessionID,
          'after-reconnect',
        );
        for (var i = 0; i < 10; i++) {
          h.channels.last.onEvent(
            EventEnvelope(
              type: 'session.updated',
              directory: '/work/other',
              properties: {},
            ),
          );
        }
        await tester.pump(const Duration(milliseconds: 1999));
        expect(h.script.globalCalls, 2);
        await tester.pump(const Duration(milliseconds: 1));
        expect(h.script.globalCalls, 3);
        expect(
          h.controller.chatFeed().items.single.sessionID,
          'after-reconnect',
        );
        await tester.pump(const Duration(seconds: 4));
        expect(h.script.globalCalls, 3);
      } finally {
        h.controller.dispose();
      }
    });
  }

  testWidgets('runtime switches do not join a retired slow fetch', (
    tester,
  ) async {
    final h = _Harness(await _store());
    try {
      final retired = h.script;
      retired.heldGlobal = Completer<ServerPage<GlobalSessionResult>>();
      await h.connect();
      await tester.pump(const Duration(seconds: 2));
      expect(retired.globalCalls, 1);
      h.script = _Script()..global = [_global('v2-chat', '/work/v2')];
      final next = h.script;
      await h.connect(v2: true);
      await tester.pump(const Duration(seconds: 2));
      expect(next.globalCalls, 1);
      expect(h.controller.chatFeed().items.single.sessionID, 'v2-chat');
      retired.heldGlobal!.complete(
        ServerPage(items: [_global('retired', '/work/v1')]),
      );
      await tester.pump();
      expect(h.controller.chatFeed().items.single.sessionID, 'v2-chat');
      h.script = _Script()..global = [_global('v1-chat', '/work/v1')];
      await h.connect();
      await tester.pump(const Duration(seconds: 2));
      expect(h.script.globalCalls, 1);
      expect(h.controller.chatFeed().items.single.sessionID, 'v1-chat');
    } finally {
      h.controller.dispose();
    }
  });

  testWidgets('event during slow inventory read gets one trailing fetch', (
    tester,
  ) async {
    final h = _Harness(await _store());
    try {
      await h.connect();
      final held = Completer<ServerPage<GlobalSessionResult>>();
      h.script.heldGlobal = held;
      await tester.pump(const Duration(seconds: 2));
      expect(h.script.globalCalls, 1);
      h.channels.last.onEvent(
        EventEnvelope(
          type: 'session.updated',
          directory: '/work/other',
          properties: {},
        ),
      );
      await tester.pump(const Duration(seconds: 2));
      expect(h.script.globalCalls, 1);
      h.script.heldGlobal = null;
      h.script.global = [_global('newer', '/work/other')];
      held.complete(const ServerPage(items: []));
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      expect(h.script.globalCalls, 2);
      expect(h.controller.chatFeed().items.single.sessionID, 'newer');
      await tester.pump(const Duration(seconds: 4));
      expect(h.script.globalCalls, 2);
    } finally {
      h.controller.dispose();
    }
  });

  testWidgets(
    'explicit refresh consumes startup debounce and disconnect cancels it',
    (tester) async {
      final h = _Harness(await _store());
      try {
        await h.connect();
        await h.controller.refreshChatFeed();
        await tester.pump(const Duration(seconds: 2));
        expect(h.script.globalCalls, 1);
        await h.connect(v2: true);
        await h.controller.disconnect();
        await tester.pump(const Duration(seconds: 4));
        expect(h.script.globalCalls, 1);
      } finally {
        h.controller.dispose();
      }
    },
  );
  testWidgets(
    'Home can read before connect without missing or duplicating load',
    (tester) async {
      final h = _Harness(await _store());
      try {
        expect(h.controller.chatFeed().items, isEmpty);
        h.script.global = [_global('first', '/work/other')];
        await h.connect();
        for (var i = 0; i < 10; i++) {
          h.controller.chatFeed();
        }
        await tester.pump(const Duration(seconds: 2));
        expect(h.script.globalCalls, 1);
        expect(h.controller.chatFeed().items.single.sessionID, 'first');
      } finally {
        h.controller.dispose();
      }
    },
  );

  testWidgets('retired completion cannot clear a newer in-flight fetch', (
    tester,
  ) async {
    final h = _Harness(await _store());
    try {
      final retired = h.script;
      final oldRead = Completer<ServerPage<GlobalSessionResult>>();
      retired.heldGlobal = oldRead;
      await h.connect();
      await tester.pump(const Duration(seconds: 2));
      final newRead = Completer<ServerPage<GlobalSessionResult>>();
      h.script = _Script()..heldGlobal = newRead;
      await h.connect(v2: true);
      await tester.pump(const Duration(seconds: 2));
      expect(h.script.globalCalls, 1);
      oldRead.complete(ServerPage(items: [_global('old', '/work/old')]));
      await tester.pump();
      final join = h.controller.refreshChatFeed();
      expect(h.script.globalCalls, 1);
      newRead.complete(ServerPage(items: [_global('new', '/work/new')]));
      await join;
      await tester.pump();
      expect(h.controller.chatFeed().items.single.sessionID, 'new');
    } finally {
      h.controller.dispose();
    }
  });

  testWidgets('disposed controller never fetches its pending startup trigger', (
    tester,
  ) async {
    final h = _Harness(await _store());
    await h.connect();
    h.controller.dispose();
    await tester.pump(const Duration(seconds: 2));
    expect(h.script.globalCalls, 0);
  });
  testWidgets(
    'automatic OpenCode load does not wait for merged agent refresh',
    (tester) async {
      final h = _Harness(await _store());
      try {
        h.controller.holdMergedRefresh = true;
        h.script.global = [_global('opencode', '/work/other')];
        await h.connect();
        await tester.pump(const Duration(seconds: 2));
        expect(h.script.globalCalls, 1);
        expect(h.controller.chatFeed().items.single.sessionID, 'opencode');
      } finally {
        h.controller.mergedRefresh.complete();
        h.controller.dispose();
      }
    },
  );
  for (final v2 in [false, true]) {
    testWidgets(
      '${v2 ? 'v2' : 'v1'} startup and reconnect fetch despite sustained events',
      (tester) async {
        final h = _Harness(await _store());
        try {
          await h.connect(v2: v2);
          for (final channel in h.channels) {
            channel.onStatus(StreamStatus.connected);
          }
          await tester.pump(const Duration(seconds: 1));
          h.channels.last.onEvent(
            EventEnvelope(
              type: 'session.updated',
              directory: '/work/other',
              properties: {},
            ),
          );
          await tester.pump(const Duration(seconds: 1));
          expect(h.script.globalCalls, 1);
          h.channels.first.onStatus(StreamStatus.reconnecting);
          h.channels.first.onStatus(StreamStatus.connected);
          await tester.pump(const Duration(seconds: 1));
          h.channels.last.onEvent(
            EventEnvelope(
              type: 'session.updated',
              directory: '/work/other',
              properties: {},
            ),
          );
          await tester.pump(const Duration(seconds: 1));
          expect(h.script.globalCalls, 2);
          await tester.pump(const Duration(seconds: 2));
          expect(h.script.globalCalls, 2);
        } finally {
          h.controller.dispose();
        }
      },
    );
  }
}

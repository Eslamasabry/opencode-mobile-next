import 'dart:async';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
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
  final prompts = <(String, String)>[];
  final createdIn = <String?>[];
  int globalCalls = 0;
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
  Future<Session> createSession() async {
    script.createdIn.add(located);
    return Session(
      id: 'new-${script.createdIn.length}',
      directory: located,
      time: SessionTime(created: 1, updated: 1),
    );
  }

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
  }) async => script.prompts.add((sessionID, text));

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
    return ServerPage(items: script.global);
  }
}

Session _session(
  String id,
  String directory, {
  int updated = 0,
  String? parent,
  String? title,
  int? archived,
}) => Session(
  id: id,
  title: title ?? id,
  directory: directory,
  parentID: parent,
  time: SessionTime(created: 0, updated: updated, archived: archived),
);

GlobalSessionResult _global(
  String id,
  String directory, {
  int updated = 0,
  String? parent,
  String? name,
}) => GlobalSessionResult(
  session: _session(id, directory, updated: updated, parent: parent),
  projectDirectory: directory,
  projectName: name,
);

WorkspaceProject _project(String id, String directory, {String? name}) =>
    WorkspaceProject(
      id: id,
      name: name ?? id,
      directory: directory,
      worktrees: const [],
      updatedAt: 1,
    );

Future<ProfileStore> _store() async {
  SharedPreferences.setMockInitialValues({});
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

ServerProfile _profile() =>
    ServerProfile(id: 'server', name: 'server', baseUrl: 'http://127.0.0.1:1');

Future<ConnectionController> _connect(
  WidgetTester tester,
  ProfileStore store,
  _Script script,
) async {
  final apis = <_Api>[];
  final controller = ConnectionController(
    store,
    apiFactory: (_) {
      final api = _Api(script);
      apis.add(api);
      return api;
    },
    repositoryFactory: (api) => _Repo(api, script),
    eventStreamFactory:
        ({required api, required onEvent, required onStatus, onError}) =>
            _Stream(
              api: api,
              onEvent: onEvent,
              onStatus: onStatus,
              onError: onError,
            ),
  );
  final connect = controller.connect(_profile());
  await tester.pump();
  apis.first.health_.complete(Health(healthy: true, version: '1'));
  await connect;
  await tester.pump();
  return controller;
}

EventEnvelope _event(
  String type,
  String directory,
  Map<String, dynamic> properties,
) => EventEnvelope(type: type, directory: directory, properties: properties);

void main() {
  setUpAll(() => HttpOverrides.global = _RealHttpOverrides());
  tearDownAll(() => HttpOverrides.global = null);

  group('temporary folders', () {
    test('are never projects', () {
      for (final dir in [
        '/tmp',
        '/tmp/',
        'tmp',
        '/tmp/scratch',
        '/var/tmp/x',
        '/private/tmp',
        '/var/folders/ab/cd',
        '/data/local/tmp',
        'C:\\Windows\\Temp',
        'C:/Users/me/AppData/Local/Temp/x',
        '/',
        'global',
        '',
        null,
      ]) {
        expect(isTemporaryProjectDirectory(dir), isTrue, reason: '$dir');
      }
      for (final dir in ['/work/app', '/home/me/tmpl', '/root/projects/tmp2']) {
        expect(isTemporaryProjectDirectory(dir), isFalse, reason: dir);
      }
    });
  });

  testWidgets('a server that gives every folder a project shows no Git '
      'badge', (tester) async {
    final script = _Script()
      ..projectsAreGit = false
      ..global = [_global('a', '/work/api', updated: 5)]
      ..projects = [_project('p1', '/work/api', name: 'API')];
    final controller = await _connect(tester, await _store(), script);
    await controller.refreshChatFeed();

    final feed = chatFeedSourceOf(controller).chatFeed();
    expect(feed.items.single.projectName, 'API');
    expect(feed.items.single.isGit, isFalse);
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });

  testWidgets('feed: needs-you first, then running, then by time; filters', (
    tester,
  ) async {
    final script = _Script()
      ..global = [
        _global('old', '/work/site', updated: 10),
        _global('new', '/work/site', updated: 50, name: 'Site'),
        _global('run', '/work/api', updated: 5),
        _global('wait', '/work/api', updated: 1),
        _global('child', '/work/api', updated: 99, parent: 'run'),
      ]
      ..projects = [_project('p1', '/work/api', name: 'API')];
    final controller = await _connect(tester, await _store(), script);
    controller.elsewhereAttention.handle(
      _event('permission.asked', '/work/api', {
        'id': 'perm1',
        'sessionID': 'wait',
      }),
    );
    controller.elsewhereAttention.handle(
      _event('session.status', '/work/api', {
        'sessionID': 'run',
        'status': {'type': 'busy'},
      }),
    );
    await controller.refreshChatFeed();

    final feed = chatFeedSourceOf(controller).chatFeed();
    expect(feed.acrossProjects, isTrue);
    expect(feed.items.map((i) => i.sessionID), ['wait', 'run', 'new', 'old']);
    expect(feed.items[0].status, ChatStatus.needsYou);
    expect(feed.items[1].status, ChatStatus.running);
    expect(feed.items[2].projectName, 'Site');
    expect(feed.items[0].projectName, 'API');
    expect(feed.items[0].isGit, isTrue);
    expect(feed.items[2].isGit, isFalse);

    expect(
      controller
          .chatFeed(const ChatFeedFilter(needsYou: true))
          .items
          .map((i) => i.sessionID),
      ['wait'],
    );
    expect(
      controller
          .chatFeed(const ChatFeedFilter(running: true))
          .items
          .map((i) => i.sessionID),
      ['run'],
    );
    expect(
      controller
          .chatFeed(const ChatFeedFilter(projectDirectory: '/work/site/'))
          .items
          .map((i) => i.sessionID),
      ['new', 'old'],
    );
    expect(
      controller
          .chatFeed(const ChatFeedFilter(includeSubagents: true))
          .items
          .map((i) => i.sessionID),
      contains('child'),
    );

    expect(feed.items.every((i) => i.agentId == 'opencode'), isTrue);
    expect(feed.items.first.agentLabel, isNull);
    expect(
      controller.chatFeed(const ChatFeedFilter(agentId: 'claude')).items,
      isEmpty,
    );
    expect(
      controller
          .chatFeed(const ChatFeedFilter(agentId: 'opencode'))
          .items
          .length,
      4,
    );
    expect(
      const ChatFeedFilter(agentId: 'x') == const ChatFeedFilter(agentId: 'x'),
      isTrue,
    );
    expect(const ChatFeedFilter(agentId: 'x') == ChatFeedFilter.all, isFalse);

    final summaries = controller.projectSummaries;
    final api = summaries.firstWhere((p) => p.directory == '/work/api');
    expect(api.chatCount, 2);
    expect(api.runningCount, 1);
    expect(api.needsYouCount, 1);
    expect(summaries.map((p) => p.directory), ['/work/site', '/work/api']);
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });

  testWidgets(
    'feed keeps chats from temporary folders, leaves out archived ones',
    (tester) async {
      final script = _Script()
        ..global = [
          _global('keep', '/work/app', updated: 3),
          _global('tmp1', '/tmp/scratch', updated: 9),
          _global('root', '/', updated: 8),
          GlobalSessionResult(
            session: _session('arch', '/work/app', archived: 5),
            projectDirectory: '/work/app',
          ),
        ]
        ..projects = [_project('g', '/'), _project('t', '/tmp')];
      final controller = await _connect(tester, await _store(), script);
      await controller.refreshChatFeed();

      // Conversations are never hidden for their folder; archived ones are.
      expect(controller.chatFeed().items.map((i) => i.sessionID), [
        'tmp1',
        'root',
        'keep',
      ]);
      expect(controller.projectSummaries.map((p) => p.directory), [
        '/work/app',
      ]);
      expect(controller.isTemporaryProject('/tmp/x'), isTrue);
      controller.dispose();
    },
  );

  testWidgets('without cross-project listing the feed is the current project', (
    tester,
  ) async {
    final script = _Script()
      ..acrossProjects = false
      ..local = [_session('mine', '/work/app', updated: 4)]
      ..global = [_global('other', '/work/other', updated: 9)];
    final store = await _store();
    await store.setLocation('server', directory: '/work/app');
    final controller = await _connect(tester, store, script);
    await controller.refreshChatFeed();

    expect(controller.chatFeedAcrossProjects, isFalse);
    final feed = controller.chatFeed();
    expect(feed.acrossProjects, isFalse);
    expect(feed.items.map((i) => i.sessionID), ['mine']);
    expect(script.globalCalls, 0);
    controller.dispose();
  });

  testWidgets('last used project persists per profile and is swept on delete', (
    tester,
  ) async {
    final script = _Script();
    final store = await _store();
    final controller = await _connect(tester, store, script);

    expect(controller.lastUsedProjectDirectory, isNull);
    await controller.rememberLastUsedProject('/tmp/x');
    expect(controller.lastUsedProjectDirectory, isNull);
    await controller.rememberLastUsedProject('/work/app/');
    expect(controller.lastUsedProjectDirectory, '/work/app');
    expect(store.prefs.getString('oc.lastProject.server'), '/work/app');
    expect(
      store.profileScopedPreferenceKeys('server'),
      contains('oc.lastProject.server'),
    );

    // Selecting a project (opening a chat there) records it too.
    await controller.selectLocation(directory: '/work/other');
    expect(controller.lastUsedProjectDirectory, '/work/other');
    controller.dispose();
  });

  testWidgets('startChatIn opens the project, creates the chat, sends the '
      'first prompt and remembers the project', (tester) async {
    final script = _Script();
    final controller = await _connect(tester, await _store(), script);

    final id = await controller.startChatIn(
      '/work/app',
      firstPrompt: '  hello there ',
    );
    expect(id, 'new-1');
    expect(controller.directory, '/work/app');
    expect(script.createdIn, ['/work/app']);
    expect(script.prompts, [('new-1', 'hello there')]);
    expect(controller.lastUsedProjectDirectory, '/work/app');

    final bare = await controller.startChatIn('/work/app');
    expect(bare, 'new-2');
    expect(script.prompts, hasLength(1));

    await expectLater(
      controller.startChatIn('/tmp/scratch'),
      throwsA(isA<ProductException>()),
    );
    expect(script.createdIn, hasLength(2));
    controller.dispose();
  });

  testWidgets('a refetch picks up chats created elsewhere', (tester) async {
    final script = _Script()..global = [_global('a', '/work/app', updated: 1)];
    final controller = await _connect(tester, await _store(), script);
    await controller.refreshChatFeed();
    expect(controller.chatFeed().items, hasLength(1));

    script.global = [
      _global('a', '/work/app', updated: 1),
      _global('b', '/work/app', updated: 2),
    ];
    await controller.refreshChatFeed();
    expect(controller.chatFeed().items.map((i) => i.sessionID), ['b', 'a']);
    controller.dispose();
  });

  testWidgets(
    'conversations in temp, home and root folders are listed, never projects, '
    'and reachable through Other folders',
    (tester) async {
      final script = _Script()
        ..global = [
          _global('fish', '/tmp', updated: 9, name: 'tmp'),
          _global('rel', 'tmp', updated: 8),
          _global('home', '/root', updated: 7),
          _global('rootchat', '/', updated: 6),
          _global('real', '/work/app', updated: 5),
        ]
        ..projects = [_project('p', '/work/app', name: 'App')];
      final controller = await _connect(tester, await _store(), script);
      await controller.refreshChatFeed();

      final all = controller.chatFeed().items;
      expect(all.map((i) => i.sessionID), [
        'fish',
        'rel',
        'home',
        'rootchat',
        'real',
      ]);
      String label(String id) =>
          all.firstWhere((i) => i.sessionID == id).projectName;
      expect(label('fish'), 'tmp');
      expect(label('rel'), 'tmp');
      expect(label('home'), 'Home');
      expect(label('rootchat'), '/');
      expect(label('real'), 'App');

      expect(controller.projectSummaries.map((p) => p.directory), [
        '/work/app',
      ]);
      expect(controller.lastUsedProjectDirectory, isNull);
      await controller.rememberLastUsedProject('/tmp');
      expect(controller.lastUsedProjectDirectory, isNull);

      final other = controller.chatFeed(
        const ChatFeedFilter(otherFolders: true),
      );
      expect(other.items.map((i) => i.sessionID), [
        'fish',
        'rel',
        'home',
        'rootchat',
      ]);
      expect(
        controller
            .chatFeed(const ChatFeedFilter(projectDirectory: '/work/app'))
            .items
            .map((i) => i.sessionID),
        ['real'],
      );
      expect(
        const ChatFeedFilter(otherFolders: true) == ChatFeedFilter.all,
        isFalse,
      );
      expect(otherFolderLabel('/home/user'), 'Home');
      await tester.pump();
      controller.dispose();
    },
  );
}

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:clock/clock.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_install.dart';
import 'package:opencode_mobile/builtin/agents/gen_ui_search_publish.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_history.dart';
import 'package:opencode_mobile/domain/mcp_catalog.dart';
import 'package:opencode_mobile/domain/mcp_chat.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/domain/setup_registry.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_project_engine.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Installer implements GenUiInstaller {
  @override
  Future<GenUiSetupStatus> setEnabled({
    required String profileId,
    required Set<GenUiAgent> agents,
    required bool enabled,
  }) async => enabled
      ? GenUiSetupOn(agents: [GenUiAgent.openCode1])
      : const GenUiSetupOff();
}

MessageWithParts _message({
  String reason = 'Find references.',
}) => MessageWithParts(
  info: MessageInfo(id: 'assistant', sessionID: 'session', role: 'assistant'),
  parts: [
    Part(
      id: 'part',
      type: 'tool',
      messageID: 'assistant',
      callID: 'call',
      toolName: 'oc-ui_show',
      toolState: ToolState(
        status: 'completed',
        executed: true,
        input: {
          'v': 1,
          'id': 'suggest-design',
          'title': 'Suggested connector',
          'body': <Object>[],
          'connector': {'catalogId': 'com.example/design', 'reason': reason},
        },
      ),
    ),
  ],
);

class _Api extends OpenCodeApi {
  _Api() : super(baseUrl: 'http://127.0.0.1:4097');
  List<MessageWithParts> history = [_message()];
  Completer<void>? historyReached;
  Completer<void>? resumeHistory;

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    genUi: true,
    serverCatalog: true,
    mcpRuntimeAdds: true,
    mcpChatConnect: true,
    mcpChatToolRefresh: true,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String sessionID, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: history);

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) async {
    final snapshot = history;
    if (historyReached?.isCompleted == false) historyReached!.complete();
    await resumeHistory?.future;
    return GenUiHistoryPage(items: snapshot, hasMore: false);
  }

  @override
  Future<bool> genUiSessionIdle(String sessionID) async => true;
}

class _Repository implements ProductRepository {
  int lists = 0, adds = 0;
  Completer<void>? listStarted, listHold;
  List<McpServerInfo> inventory = [];

  @override
  Future<List<McpServerInfo>> listMcpServers() async {
    lists++;
    if (listStarted?.isCompleted == false) listStarted!.complete();
    await listHold?.future;
    return inventory;
  }

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) async {
    expect(scope, McpConfigScope.runtimeLocation);
    adds++;
    inventory = [McpServerInfo(name: draft.name, status: 'connected')];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _EngineBridge implements PhoneProjectEngineBridge {
  @override
  Future<void> start(
    String profileId, {
    int port = 4098,
    String? notice,
  }) async {}
  @override
  Future<({String baseUrl, String bearerToken})> credentials(
    String profileId,
  ) async => (baseUrl: '', bearerToken: '');
  @override
  Future<void> stop(String profileId) async {}
  @override
  Future<void> delete(String profileId) async {}
}

McpCatalogItem _item([String name = 'com.example/design']) =>
    McpCatalogItem.from(
      RegistryEntry.fromJson({
        'name': name,
        'version': '1.0.0',
        'remotes': [
          {'type': 'streamable-http', 'url': 'https://example.com/mcp'},
        ],
      })!,
    );

Future<({ConnectionController connection, _Api api, _Repository repository})>
_harness({bool observe = true, GenUiSearchPublisher? searchPublisher}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'phone', 'name': 'Phone', 'baseUrl': 'http://127.0.0.1:4097'},
      {'id': 'other', 'name': 'Other', 'baseUrl': 'https://other.invalid'},
    ]),
    'oc.activeProfile': 'phone',
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  final api = _Api();
  final repository = _Repository();
  final connection =
      ConnectionController(
          store,
          isIsolated: true,
          genUiInstaller: _Installer(),
          genUiSearchPublisher: searchPublisher,
          phoneEngineBridge: _EngineBridge(),
        )
        ..api = api
        ..repository = repository
        ..directory = '/work/project'
        ..status = StreamStatus.connected;
  connection.adoptConnectedProfileForTesting(store.profiles.first);
  addTearDown(connection.dispose);
  await connection.setGenUiEnabled(true);
  if (observe) await connection.loadSessionTail('session');
  return (connection: connection, api: api, repository: repository);
}

GenUiCard _card(ConnectionController connection, _Api api) =>
    (connection.genUiCardForPart(
              'session',
              'assistant',
              api.history.single.parts.single,
            )
            as GenUiParsed)
        .card;

class _SearchPublisher implements GenUiSearchPublisher {
  final ready = Completer<void>();
  int calls = 0;
  bool succeeds = true;
  Uri? endpoint;
  String? bearer;
  @override
  Future<bool> publish({
    required String profileId,
    required GenUiAgent agent,
    required Uri endpoint,
    required String bearer,
  }) async {
    calls++;
    expect(profileId, 'phone');
    expect(agent, GenUiAgent.openCode1);
    this.endpoint = endpoint;
    this.bearer = bearer;
    if (!ready.isCompleted) ready.complete();
    return succeeds;
  }

  Future<({int status, Map<String, dynamic> body})> search({
    String directory = '/work/project',
  }) async {
    await ready.future.timeout(const Duration(seconds: 5));
    final client = HttpClient();
    try {
      final request = await client.postUrl(endpoint!);
      request.headers.set('Authorization', 'Bearer $bearer');
      request.write(
        jsonEncode({
          'arguments': {'query': 'design'},
          'directory': directory,
        }),
      );
      final response = await request.close();
      return (
        status: response.statusCode,
        body:
            jsonDecode(await utf8.decoder.bind(response).join())
                as Map<String, dynamic>,
      );
    } finally {
      client.close(force: true);
    }
  }
}

Future<void> _cache(ConnectionController connection) => connection.store.prefs
    .setString(
      'oc.setupRegistry.phone',
      jsonEncode({
        'version': 1,
        'optedIn': true,
        'cachedAt': '2026-10-09T00:00:00Z',
        'entries': [
          {
            'name': 'com.example/design',
            'version': '1',
            'title': 'Design',
            'description': 'Design reference',
            'remotes': [
              {'type': 'streamable-http', 'url': 'https://example.com/mcp'},
            ],
          },
        ],
      }),
    )
    .then((_) {});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  setUp(() {
    HttpOverrides.global = null;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, (_) async => null);
  });
  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(secure, null);
  });

  test(
    'agent search uses current cached Tools catalogue and exact inventory',
    () async {
      final publisher = _SearchPublisher();
      final h = await _harness(searchPublisher: publisher);
      await _cache(h.connection);
      h.repository.inventory = [
        const McpServerInfo(name: 'design', status: 'connected'),
      ];
      final result = await publisher.search();
      expect(result.status, 200);
      final match = (result.body['matches'] as List).single as Map;
      expect(match['catalogId'], 'com.example/design');
      expect(match['connected'], isTrue);
      expect(match['needsSignIn'], 'unknown');
      expect(h.repository.lists, 1);
      expect(h.repository.adds, 0);
    },
  );

  test('agent search never applies another project inventory', () async {
    final publisher = _SearchPublisher();
    final h = await _harness(searchPublisher: publisher);
    await _cache(h.connection);
    final result = await publisher.search(directory: '/different/project');
    expect(result.status, 200);
    expect(
      ((result.body['matches'] as List).single as Map)['connected'],
      isNull,
    );
    expect(h.repository.lists, 0);
  });

  test(
    'agent search reports missing cache without inventory request',
    () async {
      final publisher = _SearchPublisher();
      final h = await _harness(searchPublisher: publisher);
      final result = await publisher.search();
      expect(result.body['status'], 'catalogue_not_loaded');
      expect(h.repository.lists, 0);
    },
  );

  test('agent search rejects source changes during inventory read', () async {
    final publisher = _SearchPublisher();
    final h = await _harness(searchPublisher: publisher);
    await _cache(h.connection);
    h.repository.listStarted = Completer<void>();
    h.repository.listHold = Completer<void>();
    final request = publisher.search();
    await h.repository.listStarted!.future.timeout(const Duration(seconds: 5));
    h.connection.directory = '/changed';
    h.repository.listHold!.complete();
    expect((await request).status, 403);
  });

  test('old search endpoint cannot serve after cards are disabled', () async {
    final publisher = _SearchPublisher();
    final h = await _harness(searchPublisher: publisher);
    await _cache(h.connection);
    await publisher.search();
    await h.connection.setGenUiEnabled(false);
    await expectLater(publisher.search(), throwsA(isA<SocketException>()));
  });

  test('search observes cache changes without a helper restart', () async {
    final publisher = _SearchPublisher();
    final h = await _harness(searchPublisher: publisher);
    expect((await publisher.search()).body['status'], 'catalogue_not_loaded');
    await _cache(h.connection);
    expect((await publisher.search()).body['status'], 'ok');
    await h.connection.store.prefs.remove('oc.setupRegistry.phone');
    expect((await publisher.search()).body['status'], 'catalogue_not_loaded');
  });

  test('failed descriptor publishing can retry after cooldown', () async {
    var now = DateTime.utc(2026, 10, 9);
    await withClock(Clock(() => now), () async {
      final publisher = _SearchPublisher()..succeeds = false;
      final h = await _harness(searchPublisher: publisher);
      await publisher.ready.future.timeout(const Duration(seconds: 5));
      await Future<void>.delayed(Duration.zero);
      expect(publisher.calls, 1);
      now = now.add(const Duration(seconds: 6));
      publisher.succeeds = true;
      await h.connection.setTranscriptTimestampsVisible(true);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      expect(publisher.calls, 2);
      expect((await publisher.search()).status, 200);
    });
  });

  test('authoritative connector admission coalesces the same card', () async {
    final h = await _harness();
    final card = _card(h.connection, h.api);
    expect(h.connection.genUiStateForCard(card), GenUiCardState.report);
    final first = h.connection.createMcpChatController(card, _item());
    expect(h.connection.createMcpChatController(card, _item()), same(first));
    await first.connect('design');
    expect(first.snapshot.phase, McpChatPhase.toolsReady);
    expect(h.repository.adds, 1);
  });

  test(
    'unobserved and mismatched catalog suggestions cannot be admitted',
    () async {
      final h = await _harness(observe: false);
      final card =
          (genUiFromPart(
                    h.api.history.single.parts.single,
                    scope: const GenUiScope(
                      profileID: 'phone',
                      sourceId: 'opencode',
                      directory: '/work/project',
                    ),
                    sessionID: 'session',
                    messageID: 'assistant',
                  )
                  as GenUiParsed)
              .card;
      expect(
        () => h.connection.createMcpChatController(card, _item()),
        throwsA(isA<ProductException>()),
      );
      await h.connection.loadSessionTail('session');
      expect(
        () => h.connection.createMcpChatController(
          card,
          _item('com.example/other'),
        ),
        throwsA(isA<ProductException>()),
      );
      expect(h.repository.adds, 0);
    },
  );

  test(
    'changed authoritative transcript rejects the earlier card controller',
    () async {
      final h = await _harness();
      final card = _card(h.connection, h.api);
      final connector = h.connection.createMcpChatController(card, _item());
      h.api.history = [_message(reason: 'The recommendation changed.')];
      await h.connection.loadSessionTail('session');
      expect(
        () => h.connection.createMcpChatController(card, _item()),
        throwsA(isA<ProductException>()),
      );
      await connector.connect('design');
      expect(connector.snapshot.phase, isNot(McpChatPhase.toolsReady));
      expect(h.repository.adds, 0);
      expect(h.repository.lists, 0);
    },
  );

  test(
    'changed project invalidates an admitted connector before dispatch',
    () async {
      final h = await _harness();
      final connector = h.connection.createMcpChatController(
        _card(h.connection, h.api),
        _item(),
      );
      h.connection.directory = '/work/other';
      h.connection.locationRevision++;
      await connector.connect('design');
      expect(connector.snapshot.failure, McpChatFailure.sourceChanged);
      expect(h.repository.adds + h.repository.lists, 0);
    },
  );

  test('changed active profile invalidates an admitted connector', () async {
    final h = await _harness();
    final connector = h.connection.createMcpChatController(
      _card(h.connection, h.api),
      _item(),
    );
    await h.connection.store.setActiveId('other');
    await connector.connect('design');
    expect(connector.snapshot.failure, McpChatFailure.sourceChanged);
    expect(h.repository.adds + h.repository.lists, 0);
  });

  test(
    'same-profile replacement retains the card and uses its fresh repository',
    () async {
      final h = await _harness();
      final card = _card(h.connection, h.api);
      final connector = h.connection.createMcpChatController(card, _item());
      final replacementApi = _Api();
      final replacementRepository = _Repository();
      h.connection
        ..api = replacementApi
        ..repository = replacementRepository;
      await h.connection.loadSessionTail('session');
      expect(
        h.connection.createMcpChatController(
          _card(h.connection, replacementApi),
          _item(),
        ),
        same(connector),
      );
      await connector.connect('design');
      expect(connector.snapshot.phase, McpChatPhase.toolsReady);
      expect(replacementRepository.adds, 1);
      expect(replacementRepository.lists, greaterThan(0));
      expect(h.repository.adds + h.repository.lists, 0);
    },
  );

  test(
    'disabling Agent cards invalidates retained connector actions',
    () async {
      final h = await _harness();
      final connector = h.connection.createMcpChatController(
        _card(h.connection, h.api),
        _item(),
      );
      await h.connection.setGenUiEnabled(false);
      await connector.connect('design');
      expect(connector.snapshot.failure, McpChatFailure.sourceChanged);
      expect(h.repository.adds + h.repository.lists, 0);
    },
  );

  test(
    'disposed card controllers do not exhaust the pending card budget',
    () async {
      final h = await _harness();
      final card = _card(h.connection, h.api);
      for (var i = 0; i < 40; i++) {
        h.connection.createMcpChatController(card, _item()).dispose();
      }
      final connector = h.connection.createMcpChatController(card, _item());
      await connector.connect('design');
      expect(connector.snapshot.phase, McpChatPhase.toolsReady);
      expect(h.repository.adds, 1);
    },
  );

  test(
    'source change during history recovery never calls the stale repository',
    () async {
      final h = await _harness();
      final connector = h.connection.createMcpChatController(
        _card(h.connection, h.api),
        _item(),
      );
      h.api.historyReached = Completer<void>();
      h.api.resumeHistory = Completer<void>();
      final work = connector.connect('design');
      await h.api.historyReached!.future.timeout(const Duration(seconds: 3));
      h.connection.directory = '/work/other';
      h.connection.locationRevision++;
      h.api.resumeHistory!.complete();
      await work;
      expect(connector.snapshot.failure, McpChatFailure.sourceChanged);
      expect(h.repository.adds + h.repository.lists, 0);
    },
  );
}

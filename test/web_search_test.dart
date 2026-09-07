import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/events.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/web_sources_overview.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/web_sources_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api2_client_test.dart' show withServer, writeJson;
import 'support/complete_message_history.dart';

class _Store extends ProfileStore {
  _Store(SharedPreferences prefs) : super(prefs: prefs);
  final saved = ServerProfile(
    id: 'search',
    name: 'Search',
    baseUrl: 'http://localhost',
  );
  String currentID = 'search';
  @override
  List<ServerProfile> get profiles => [
    saved,
    ServerProfile(
      id: 'other',
      name: 'Other account',
      baseUrl: 'http://localhost',
    ),
  ];
  @override
  String get activeId => currentID;
}

class _SearchApi extends OpenCodeApi
    with CompleteMessageHistory
    implements WebSearchGateway {
  _SearchApi() : super(baseUrl: 'http://localhost');
  bool searchEnabled = true;
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(webSearch: searchEnabled);
  int searches = 0;
  int discoveries = 0;
  final List<String> prompts = [];
  @override
  Future<List<MessageWithParts>> messages(String id) async => [];
  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
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
  }) async => prompts.add(text);
  List<WebSearchProvider> available = const [
    WebSearchProvider(id: 'one', name: 'One'),
  ];
  Future<WebSearchResponse> Function()? answer;
  @override
  Future<List<WebSearchProvider>> webSearchProviders() async {
    discoveries++;
    return available;
  }

  @override
  Future<WebSearchResponse> searchWeb(
    String query, {
    required String providerID,
  }) async {
    searches++;
    return answer == null
        ? const WebSearchResponse(
            providerID: 'one',
            results: [
              WebSearchResult(
                url: 'https://example.com/doc',
                title: 'Documentation',
                content: 'Untrusted excerpt',
              ),
              WebSearchResult(url: 'https://localhost/private'),
            ],
          )
        : await answer!();
  }
}

Future<void> _chatFrames(WidgetTester tester) async {
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _openSearchFromChat(
  WidgetTester tester,
  ConnectionController controller,
) async {
  controller.status = StreamStatus.connected;
  controller.sessionsById['session-1'] = Session(id: 'session-1');
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await _chatFrames(tester);
  await tester.enterText(
    find.byKey(const Key('chat-composer-field')),
    'Check these sources',
  );
  await _chatFrames(tester);
  await tester.tap(find.byKey(const Key('composer-tools-button')));
  await _chatFrames(tester);
  final entry = find.widgetWithText(ListTile, 'Add web source');
  await tester.ensureVisible(entry);
  await tester.pump();
  await tester.tap(entry);
  await tester.pumpAndSettle();
  expect(find.byType(WebSourcesScreen), findsOneWidget);
}

Future<void> _searchAndReview(WidgetTester tester, _SearchApi api) async {
  final search = find.widgetWithText(FilledButton, 'Search');
  expect(tester.widget<FilledButton>(search).onPressed, isNull);
  expect(api.searches, 0);
  await tester.enterText(
    find.byKey(const ValueKey('web-search-query')),
    'Flutter documentation',
  );
  await tester.pump();
  expect(tester.widget<FilledButton>(search).onPressed, isNotNull);
  await tester.tap(search);
  await tester.pumpAndSettle();
  expect(api.searches, 1);
  expect(api.prompts, isEmpty);
  expect(find.text('Documentation'), findsOneWidget);
  final add = find.widgetWithText(TextButton, 'Add to review');
  await tester.ensureVisible(add);
  await tester.pump();
  await tester.tap(add);
  await tester.pumpAndSettle();
  await tester.scrollUntilVisible(
    find.byKey(const ValueKey('web-sources-confirm')),
    300,
    scrollable: find
        .descendant(
          of: find.byType(WebSourcesScreen),
          matching: find.byType(Scrollable),
        )
        .first,
  );
  // ensureVisible updates the scroll position; render that frame before tapping.
  await tester.pumpAndSettle();
  expect(
    find.byKey(const ValueKey('web-sources-confirm')).hitTestable(),
    findsOneWidget,
  );
  expect(find.text('Use selected sources (1)'), findsOneWidget);
  expect(find.byKey(const ValueKey('web-source-url')), findsNothing);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'explicit search uses scoped Basic transport and no history mutation or cursor',
    () async {
      await withServer(
        (server, requests) async {
          final gateway = Api2Gateway.connect(
            baseUrl: 'http://127.0.0.1:${server.port}',
            password: 'fixture',
            directory: '/project',
            workspace: 'workspace',
          );
          addTearDown(gateway.close);
          expect((await gateway.webSearchProviders()).single.id, 'one');
          final response = await gateway.searchWeb(' docs ', providerID: 'one');
          expect(response.results.single.url, 'https://example.com/doc');
          expect(requests.map((r) => r.uri.path), [
            '/api/websearch/provider',
            '/api/websearch',
          ]);
          expect(requests.last.body, {'query': 'docs', 'providerID': 'one'});
          for (final request in requests) {
            expect(request.uri.queryParameters, {
              'location[directory]': '/project',
              'location[workspace]': 'workspace',
            });
          }
        },
        handler: (request) async {
          expect(request.headers.value('authorization'), startsWith('Basic '));
          await writeJson(request, {
            'location': {'directory': '/project', 'workspaceID': 'workspace'},
            'data': request.method == 'GET'
                ? [
                    {'id': 'one', 'name': 'One'},
                  ]
                : {
                    'providerID': 'one',
                    'results': [
                      {'url': 'https://example.com/doc', 'time': {}},
                    ],
                  },
          });
        },
      );
    },
  );

  for (final path in ['provider', 'search']) {
    test('$path rejects another response location', () async {
      await withServer(
        (server, requests) async {
          final gateway = Api2Gateway.connect(
            baseUrl: 'http://127.0.0.1:${server.port}',
            password: 'fixture',
            directory: '/project',
          );
          addTearDown(gateway.close);
          await expectLater(
            path == 'provider'
                ? gateway.webSearchProviders()
                : gateway.searchWeb('docs', providerID: 'one'),
            throwsA(
              isA<WebSearchFailure>().having(
                (e) => e.kind,
                'kind',
                WebSearchFailureKind.invalidResponse,
              ),
            ),
          );
        },
        handler: (request) => writeJson(request, {
          'location': {'directory': '/other'},
          'data': path == 'provider'
              ? [
                  {'id': 'one', 'name': 'One'},
                ]
              : {'providerID': 'one', 'results': []},
        }),
      );
    });
  }

  test('503 discards remote error copy and marks search unavailable', () async {
    await withServer(
      (server, requests) async {
        final gateway = Api2Gateway.connect(
          baseUrl: 'http://127.0.0.1:${server.port}',
          password: 'fixture',
        );
        addTearDown(gateway.close);
        await expectLater(
          gateway.searchWeb('docs', providerID: 'one'),
          throwsA(
            isA<WebSearchFailure>().having(
              (e) => e.kind,
              'kind',
              WebSearchFailureKind.unavailable,
            ),
          ),
        );
      },
      handler: (request) => writeJson(request, {
        '_tag': 'ServiceUnavailableError',
        'message': 'private remote body',
      }, status: 503),
    );
  });

  test(
    'malformed provider catalog and wrong provider response fail closed',
    () async {
      var catalog = true;
      await withServer(
        (server, requests) async {
          final gateway = Api2Gateway.connect(
            baseUrl: 'http://127.0.0.1:${server.port}',
            password: 'fixture',
            directory: '/project',
          );
          addTearDown(gateway.close);
          await expectLater(
            gateway.webSearchProviders(),
            throwsA(isA<WebSearchFailure>()),
          );
          catalog = false;
          await expectLater(
            gateway.searchWeb('docs', providerID: 'one'),
            throwsA(isA<WebSearchFailure>()),
          );
        },
        handler: (request) => writeJson(request, {
          'location': {'directory': '/project'},
          'data': catalog
              ? [
                  {'id': 'one', 'name': 'One'},
                  {'id': 'one', 'name': 'Duplicate'},
                ]
              : {'providerID': 'silently-switched', 'results': []},
        }),
      );
    },
  );

  test('empty and excessive queries never reach the server', () async {
    await withServer((server, requests) async {
      final gateway = Api2Gateway.connect(
        baseUrl: 'http://127.0.0.1:${server.port}',
        password: 'fixture',
      );
      addTearDown(gateway.close);
      for (final query in ['', List.filled(1001, 'a').join()]) {
        await expectLater(
          gateway.searchWeb(query, providerID: 'one'),
          throwsA(isA<WebSearchFailure>()),
        );
      }
      expect(requests, isEmpty);
    }, handler: (request) => writeJson(request, {}));
  });

  late _SearchApi api;
  late ConnectionController controller;
  late WebSourcesOverview overview;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    api = _SearchApi();
    controller = ConnectionController(_Store(prefs))..api = api;
    overview = WebSourcesOverview(controller: controller);
  });
  tearDown(() {
    overview.dispose();
    controller.dispose();
  });

  testWidgets('a server without search keeps manual source entry available', (
    tester,
  ) async {
    api.searchEnabled = false;
    await tester.pumpWidget(
      MaterialApp(home: WebSourcesScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('web-search-query')), findsNothing);
    await tester.enterText(
      find.byKey(const ValueKey('web-source-url')),
      'https://example.com/manual',
    );
    await tester.enterText(
      find.byKey(const ValueKey('web-source-title')),
      'Pasted source',
    );
    final add = find.byKey(const ValueKey('web-source-add'));
    await tester.ensureVisible(add);
    await tester.pump();
    await tester.tap(add);
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('web-sources-confirm')),
      300,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Use selected sources (1)'), findsOneWidget);
    expect(find.text('Pasted source'), findsOneWidget);
    expect(api.searches, 0);
    expect(api.prompts, isEmpty);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'search review returns to the real editable composer without sending',
    (tester) async {
      await _openSearchFromChat(tester, controller);
      await _searchAndReview(tester, api);
      await tester.tap(
        find.byKey(const ValueKey('web-sources-confirm')).hitTestable(),
      );
      await _chatFrames(tester);
      expect(find.byType(WebSourcesScreen), findsNothing);
      final composer = find.byKey(const Key('chat-composer-field'));
      final text = tester.widget<TextField>(composer).controller!.text;
      expect(text, startsWith('Check these sources\n\n'));
      expect(text, contains('https://example.com/doc'));
      expect(text, contains('Untrusted excerpt'));
      expect(text, contains('unverified'));
      expect(api.prompts, isEmpty);
      await tester.enterText(composer, 'Edited after review');
      await tester.pump();
      expect(
        tester.widget<TextField>(composer).controller!.text,
        'Edited after review',
      );
      expect(api.searches, 1);
      expect(api.prompts, isEmpty);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  testWidgets(
    'switching account before confirmation blocks the reviewed context',
    (tester) async {
      await _openSearchFromChat(tester, controller);
      await _searchAndReview(tester, api);
      // Change the selected account without a controller notification. The
      // confirmation boundary itself must revalidate, not trust stale widgets.
      (controller.store as _Store).currentID = 'other';
      await tester.tap(
        find.byKey(const ValueKey('web-sources-confirm')).hitTestable(),
      );
      await tester.pumpAndSettle();
      expect(find.byType(WebSourcesScreen), findsOneWidget);
      expect(
        find.text('Connection changed. Close and reopen Add web source.'),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('web-sources-confirm')), findsNothing);
      expect(api.prompts, isEmpty);
      expect(api.searches, 1);
      expect(tester.takeException(), isNull);
      await tester.pumpWidget(const SizedBox());
    },
  );

  test(
    'discovery never searches; results require review and explicit confirmation',
    () async {
      await overview.discoverProviders();
      expect(api.searches, 0);
      expect(overview.providerID, 'one');
      await overview.search('documentation');
      expect(api.searches, 1);
      expect(overview.results, hasLength(1));
      expect(overview.omittedResults, 1);
      expect(overview.reviewedSelection(), isEmpty);
      overview.add(overview.results.single);
      expect(overview.reviewedSelection()!.single.excerpt, 'Untrusted excerpt');
      overview.select(overview.sources.single, false);
      expect(overview.reviewedSelection(), isEmpty);
    },
  );

  test(
    'source staging exposes immutable snapshots and rejects unsafe overflow',
    () async {
      await overview.discoverProviders();
      expect(
        () => overview.providers.add(
          const WebSearchProvider(id: 'two', name: 'Two'),
        ),
        throwsUnsupportedError,
      );

      await overview.search('documentation');
      expect(overview.results, hasLength(1));
      expect(() => overview.results.clear(), throwsUnsupportedError);
      final result = overview.results.single;
      expect(overview.add(result), isNull);
      expect(
        overview.add(
          WebSourceSelection(
            title: 'Duplicate',
            url: '  https://example.com/doc  ',
          ),
        ),
        'This URL is already in your review list.',
      );
      expect(
        () => WebSourceSelection(
          title: 'a' * (WebSourceSelection.maxTitleLength + 1),
          url: 'https://example.com/title',
        ),
        throwsFormatException,
      );
      expect(
        () => WebSourceSelection(
          title: 'Excerpt',
          url: 'https://example.com/excerpt',
          excerpt: 'visible${String.fromCharCode(0x1b)}hidden',
        ),
        throwsFormatException,
      );
    },
  );

  test(
    'multiple providers require choice; refresh never submits a query',
    () async {
      api.available = const [
        WebSearchProvider(id: 'one', name: 'One'),
        WebSearchProvider(id: 'two', name: 'Two'),
      ];
      await overview.discoverProviders();
      expect(overview.providerID, isNull);
      await overview.search('docs');
      expect(api.searches, 0);
      overview.chooseProvider('one');
      await overview.search('docs');
      overview.add(overview.results.single);
      api.available = const [];
      await overview.discoverProviders();
      expect(overview.providerID, isNull);
      expect(overview.sources, hasLength(1));
      expect(api.searches, 1);
    },
  );

  test(
    'scope switch discards pending search results and local review',
    () async {
      await overview.discoverProviders();
      final pending = Completer<WebSearchResponse>();
      api.answer = () => pending.future;
      final request = overview.search('docs');
      controller.api = OpenCodeApi(baseUrl: 'http://localhost');
      pending.complete(
        const WebSearchResponse(
          providerID: 'one',
          results: [WebSearchResult(url: 'https://example.com')],
        ),
      );
      await request;
      expect(overview.scopeChanged, isTrue);
      expect(overview.results, isEmpty);
      expect(overview.reviewedSelection(), isNull);
    },
  );

  test(
    'provider invalidation supersedes an in-flight search without another query',
    () async {
      await overview.discoverProviders();
      final pending = Completer<WebSearchResponse>();
      api.answer = () => pending.future;
      final request = overview.search('docs');
      controller.handleEventForTesting(
        EventEnvelope(type: 'websearch.updated'),
      );
      await Future<void>.delayed(Duration.zero);
      pending.complete(
        const WebSearchResponse(
          providerID: 'one',
          results: [WebSearchResult(url: 'https://example.com')],
        ),
      );
      await request;
      expect(overview.results, isEmpty);
      expect(api.discoveries, 2);
      expect(api.searches, 1);
    },
  );

  test('websearch.updated becomes a payload-free domain freshness event', () {
    final events = Api2EventAdapter().adapt(
      Api2EventEnvelope.fromJson({
        'id': 'evt_search',
        'created': 1,
        'type': 'websearch.updated',
        'data': {'private': 'ignored'},
        'location': {'directory': '/project'},
      }),
    );
    expect(events.single.type, 'websearch.updated');
    expect(events.single.properties, isEmpty);
  });
}

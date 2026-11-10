import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/product_repository.dart';
import 'support/fake_event_channel.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/session_context_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ContextApi extends FakeServerGateway with CompleteMessageHistory {
  _ContextApi() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  Object? messagesError;
  int messagesCalls = 0;
  Future<ServerPage<MessageWithParts>> Function(String? cursor)? pageHandler;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) =>
      pageHandler?.call(cursor) ??
      super.messagePage(id, cursor: cursor, limit: limit);

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    messagesCalls += 1;
    if (messagesError case final error?) throw error;
    return List.of(messagesResult);
  }
}

class _CompactRepository implements ServerOperationsGateway {
  final compacted = <String>[];
  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) async => compacted.add('$id:$providerID/$modelID');
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw StateError('Unexpected ${invocation.memberName}');
}

class _ContextController extends ConnectionController {
  _ContextController(super.store, this.actionApi) {
    api = actionApi;
    status = StreamStatus.connected;
  }

  final _ContextApi actionApi;
  int prepareCalls = 0;
  _CompactRepository? compactRepository;

  @override
  Future<ServerOperationsGateway?> prepareActionRepository() async =>
      compactRepository;

  @override
  Future<FakeServerGateway?> prepareActionTransport() async {
    prepareCalls += 1;
    return actionApi;
  }
}

MessageWithParts _message(
  String id,
  String role,
  List<Part> parts, {
  int created = 1,
  String? providerID,
  String? modelID,
  Tokens? tokens,
  double cost = 0,
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: role,
    providerID: providerID,
    modelID: modelID,
    tokens: tokens,
    cost: cost,
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: parts,
);

String _repeat(String value, int count) => List.filled(count, value).join();

List<MessageWithParts> _messages() => [
  _message('user-1', 'user', [Part(type: 'text', text: _repeat('u', 40))]),
  _message(
    'assistant-1',
    'assistant',
    [
      Part(type: 'text', text: _repeat('a', 20)),
      Part(
        type: 'tool',
        toolName: 'bash',
        toolState: ToolState(
          status: 'completed',
          inputJson: _repeat('i', 20),
          output: _repeat('o', 20),
        ),
      ),
    ],
    created: 2,
    providerID: 'openai',
    modelID: 'gpt-context',
    tokens: Tokens(
      input: 100,
      output: 20,
      reasoning: 10,
      cacheRead: 50,
      cacheWrite: 20,
    ),
    cost: .0123,
  ),
];

Future<_ContextController> _controller(_ContextApi api) async {
  SharedPreferences.setMockInitialValues({});
  final controller = _ContextController(
    ProfileStore(prefs: await SharedPreferences.getInstance()),
    api,
  );
  controller.catalog = const CatalogSnapshot(
    providers: [],
    models: [
      CatalogModel(
        id: 'gpt-context',
        providerID: 'openai',
        name: 'GPT Context',
        enabled: true,
        status: 'active',
        contextLimit: 1000,
        outputLimit: 200,
        reasoning: true,
        attachments: true,
        tools: true,
        variants: [],
      ),
    ],
    agents: [],
  );
  return controller;
}

Widget _app(Widget home, {double textScale = 1}) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  builder: (context, child) => MediaQuery(
    data: MediaQuery.of(
      context,
    ).copyWith(textScaler: TextScaler.linear(textScale)),
    child: child!,
  ),
  home: home,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'changing location clears context instead of loading the old session elsewhere',
    (tester) async {
      final api = _ContextApi()..messagesResult = _messages();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          SessionContextScreen(
            controller: controller,
            sessionID: 'session-1',
            initialMessages: _messages(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final calls = api.messagesCalls;
      controller.locationRevision++;
      controller.notifyListeners();
      await tester.pumpAndSettle();
      expect(find.textContaining('Reopen this inspector'), findsOneWidget);
      expect(api.messagesCalls, calls);
      // Refresh leaves the bar: it waits in the overflow with its reason.
      expect(find.byKey(const Key('session-context-refresh')), findsNothing);
    },
  );

  test('token parsing retains cache activity in the OpenCode total', () {
    final tokens = Tokens.fromJson({
      'input': 100,
      'output': 20,
      'reasoning': 10,
      'cache': {'read': 50, 'write': 20},
    });

    expect(tokens.cacheRead, 50);
    expect(tokens.cacheWrite, 20);
    expect(tokens.cache, 70);
    expect(tokens.total, 200);
  });

  test('context metrics use latest assistant truth and label estimates', () {
    final metrics = calculateSessionContextMetrics(
      _messages(),
      const CatalogSnapshot(
        providers: [],
        models: [
          CatalogModel(
            id: 'gpt-context',
            providerID: 'openai',
            name: 'GPT Context',
            enabled: true,
            status: 'active',
            contextLimit: 1000,
            outputLimit: 200,
            reasoning: true,
            attachments: true,
            tools: true,
            variants: [],
          ),
        ],
        agents: [],
      ),
    );

    expect(metrics.contextTokens, 200);
    expect(metrics.contextLimit, 1000);
    expect(metrics.usage, .2);
    expect(metrics.userMessages, 1);
    expect(metrics.assistantMessages, 1);
    expect(metrics.sessionCost, closeTo(.0123, .000001));
    expect(
      metrics.breakdown.fold<int>(0, (sum, segment) => sum + segment.tokens),
      100,
    );
    expect(
      metrics.breakdown
          .firstWhere(
            (segment) => segment.kind == SessionContextBreakdownKind.other,
          )
          .tokens,
      75,
    );
  });

  testWidgets('context surface stays flat and fits compact large text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final api = _ContextApi()..messagesResult = _messages();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: _messages(),
        ),
        textScale: 2,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('GPT Context'), findsOneWidget);
    expect(find.text('200 of 1,000 tokens'), findsOneWidget);
    // The verdict in plain words comes first (map infoMissing).
    expect(find.text('20% used · plenty left'), findsOneWidget);
    expect(find.byType(Card), findsNothing);
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('session-context-breakdown')),
      180,
      scrollable: find.descendant(
        of: find.byKey(const ValueKey('session-context-list')),
        matching: find.byType(Scrollable),
      ),
    );
    expect(
      find.byKey(const ValueKey('session-context-breakdown')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);

    await tester.scrollUntilVisible(
      find.text('Accumulated cost'),
      240,
      scrollable: find.descendant(
        of: find.byKey(const ValueKey('session-context-list')),
        matching: find.byType(Scrollable),
      ),
    );
    expect(find.text(r'$0.0123'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('failed refresh keeps cached context and exposes retry', (
    tester,
  ) async {
    final api = _ContextApi()..messagesError = StateError('network moved');
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: _messages(),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const ValueKey('session-context-inline-error')),
      findsOneWidget,
    );
    expect(find.text('200 of 1,000 tokens'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
  });

  testWidgets('foreground data revision refreshes the retained context view', (
    tester,
  ) async {
    final api = _ContextApi()..messagesResult = _messages();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: _messages(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('200 of 1,000 tokens'), findsOneWidget);

    api.messagesResult = [
      _message('user-1', 'user', [Part(type: 'text', text: 'Continue')]),
      _message(
        'assistant-2',
        'assistant',
        [Part(type: 'text', text: 'Updated context')],
        providerID: 'openai',
        modelID: 'gpt-context',
        tokens: Tokens(input: 210, output: 25, cacheRead: 65),
      ),
    ];
    controller.signalDataRefreshForTesting();
    await tester.pumpAndSettle();

    expect(find.text('300 of 1,000 tokens'), findsOneWidget);
    expect(api.messagesCalls, greaterThanOrEqualTo(2));
    expect(controller.prepareCalls, greaterThanOrEqualTo(2));
  });

  testWidgets(
    'expired context cursor reloads recent history and retains usage',
    (tester) async {
      final cursors = <String?>[];
      final api = _ContextApi()
        ..pageHandler = (cursor) async {
          cursors.add(cursor);
          if (cursor != null) {
            throw ApiException('Cursor expired', statusCode: 410);
          }
          return ServerPage(items: _messages(), nextCursor: 'older');
        };
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await tester.pumpWidget(
        _app(
          SessionContextScreen(controller: controller, sessionID: 'session-1'),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Load older messages'));
      await tester.pumpAndSettle();
      expect(find.text('200 of 1,000 tokens'), findsOneWidget);
      await tester.tap(find.text('Refresh recent history'));
      await tester.pumpAndSettle();
      expect(cursors, [null, 'older', null]);
      expect(
        find.byKey(const ValueKey('session-context-inline-error')),
        findsNothing,
      );
    },
  );

  testWidgets('empty session explains how context becomes available', (
    tester,
  ) async {
    final api = _ContextApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SessionContextScreen(controller: controller, sessionID: 'session-1'),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No context usage yet'), findsOneWidget);
    expect(find.text('Refresh'), findsOneWidget);
  });

  testWidgets('near the limit, the page says so and offers to compact', (
    tester,
  ) async {
    final near = [
      _messages().first,
      _message(
        'assistant-1',
        'assistant',
        [Part(type: 'text', text: 'Long answer')],
        created: 2,
        providerID: 'openai',
        modelID: 'gpt-context',
        tokens: Tokens(input: 800, output: 50),
      ),
    ];
    final api = _ContextApi()..messagesResult = near;
    final controller = await _controller(api);
    controller
      ..compactRepository = _CompactRepository()
      ..selectedModel = ModelRef(providerID: 'openai', modelID: 'gpt-context');
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: near,
        ),
      ),
    );
    await tester.pumpAndSettle();

    // Said once in the verdict and once in the notice: the verdict is the
    // number, the gauge carries no "Near limit" word of its own, and the
    // bar's menu does not offer Compact beside the notice's own.
    expect(find.text('85% used'), findsOneWidget);
    expect(find.textContaining('near the limit'), findsNothing);
    expect(find.textContaining('Near limit'), findsNothing);
    expect(
      find.byKey(const ValueKey('session-context-near-limit')),
      findsOneWidget,
    );
    List<Key?> menuKeys() => [
      for (final item in tester.widget<KitTopBar>(find.byType(KitTopBar)).menu)
        item.key,
    ];
    expect(
      menuKeys(),
      isNot(contains(const Key('session-context-compact-menu'))),
    );
    await tester.tap(find.byKey(const Key('session-context-compact')));
    await tester.pumpAndSettle();
    // Asked first, with what is kept.
    expect(find.text('Compact this conversation?'), findsOneWidget);
    expect(find.text('Every message stays in the history.'), findsOneWidget);
    expect(controller.compactRepository!.compacted, isEmpty);
    await tester.tap(find.byKey(const Key('session-context-compact-confirm')));
    await tester.pumpAndSettle();
    expect(controller.compactRepository!.compacted, [
      'session-1:openai/gpt-context',
    ]);
    // Said in place, not in a snackbar.
    expect(
      find.byKey(const ValueKey('session-context-compact-started')),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('session-context-near-limit')),
      findsNothing,
    );
    // With the notice gone, the menu holds Compact again (disabled, with
    // its reason).
    expect(menuKeys(), contains(const Key('session-context-compact-menu')));
  });

  testWidgets('the totals say the message count once, with who wrote them', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final api = _ContextApi()..messagesResult = _messages();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: _messages(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.byKey(const ValueKey('session-context-messages')),
      180,
      scrollable: find.descendant(
        of: find.byKey(const ValueKey('session-context-list')),
        matching: find.byType(Scrollable),
      ),
    );
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('session-context-messages')),
        matching: find.text('2 (1 yours, 1 agent)'),
      ),
      findsOneWidget,
    );
    expect(find.text('User / assistant'), findsNothing);
  });

  testWidgets('under half the limit there is no near-limit notice', (
    tester,
  ) async {
    final api = _ContextApi()..messagesResult = _messages();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      _app(
        SessionContextScreen(
          controller: controller,
          sessionID: 'session-1',
          initialMessages: _messages(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('session-context-near-limit')),
      findsNothing,
    );
  });

  // The Work row's details sheet merged into Conversation context
  // (slice-P3.11a): the folder and shared link sit under its Details,
  // copyable, whether or not the conversation has used context yet.
  for (final withMessages in [false, true]) {
    testWidgets('the folder and shared link are under Details '
        '${withMessages ? 'beside the figures' : 'before any reply'}', (
      tester,
    ) async {
      final api = _ContextApi()
        ..messagesResult = withMessages ? _messages() : const [];
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      controller.sessionsById['session-1'] = Session(
        id: 'session-1',
        title: 'Fix login',
        directory: '/work/shopfront',
        shareUrl: 'https://opncd.ai/share/k3v9Qd2m',
      );
      await tester.pumpWidget(
        _app(
          SessionContextScreen(controller: controller, sessionID: 'session-1'),
        ),
      );
      await tester.pumpAndSettle();
      final details = find.byKey(const ValueKey('session-context-details'));
      await tester.scrollUntilVisible(
        details,
        200,
        scrollable: find.byType(Scrollable).first,
      );
      final toggle = find.descendant(
        of: details,
        matching: find.byKey(const ValueKey('kit-details-toggle')),
      );
      await tester.ensureVisible(toggle);
      await tester.pumpAndSettle();
      await tester.tap(toggle);
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('session-context-folder')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('session-context-share')),
        findsOneWidget,
      );
      expect(find.textContaining('/work/shopfront'), findsWidgets);
      expect(find.textContaining('opncd.ai/share/k3v9Qd2m'), findsWidgets);
      expect(tester.takeException(), isNull);
    });
  }
}

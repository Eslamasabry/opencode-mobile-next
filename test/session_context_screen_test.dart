import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/session_context_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _ContextApi extends OpenCodeApi with CompleteMessageHistory {
  _ContextApi() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  Object? messagesError;
  int messagesCalls = 0;

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    messagesCalls += 1;
    if (messagesError case final error?) throw error;
    return List.of(messagesResult);
  }
}

class _ContextController extends ConnectionController {
  _ContextController(super.store, this.actionApi) {
    api = actionApi;
    status = StreamStatus.connected;
  }

  final _ContextApi actionApi;
  int prepareCalls = 0;

  @override
  Future<OpenCodeApi?> prepareActionTransport() async {
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
    expect(find.text('20%'), findsOneWidget);
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
}

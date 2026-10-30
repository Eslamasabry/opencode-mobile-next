// OpenCode 2 turns between and after steps.
//
// Recorded on a live 2.0.28 server with MiniMax M3.1 Flash Free (2026-10-19):
// a step ends once its tools settle (`session.step.ended`, finish
// `tool-calls`), and the next `session.step.started` comes one to several
// seconds later. The run itself ends only with `session.execution.*`. The
// server sends no `session.status` in between, and `/api/session/active` keeps
// the session listed the whole time.
//
// The event stream is volatile: a tool's result can be lost while the turn
// goes on. Once the server has moved past the tool it is settled and the chat
// reads the transcript again (reconcile by refetch).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/gateway_events.dart';
import 'package:opencode_mobile/api2/gateway_mappers.dart'
    show api2ServerCapabilities;
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/complete_message_history.dart';
import 'support/fake_server_gateway.dart';

const _session = 'session-1';

/// A gateway with the real OpenCode 2 capabilities and a transcript of one
/// stopped turn: the reply the person stopped, then (once [nextTurn]) their
/// next prompt and its first, finished step.
class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  bool nextTurn = false;

  /// Replaces the transcript a history read returns; counts the reads.
  List<MessageWithParts> Function()? transcript;
  int reads = 0;

  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  @override
  Future<List<Session>> sessions() async => const [];

  // The server lists the run as active the whole time it works.
  @override
  Future<Map<String, String>> sessionStatuses() async => const {
    _session: 'busy',
  };

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    reads++;
    return transcript?.call() ?? _stoppedTurn();
  }

  List<MessageWithParts> _stoppedTurn() => [
    MessageWithParts(
      info: MessageInfo(
        id: 'u1',
        sessionID: _session,
        role: 'user',
        time: MsgTime(created: 1, completed: 2),
      ),
      parts: [Part(id: 'u1-text', messageID: 'u1', type: 'text', text: 'Look')],
    ),
    MessageWithParts(
      info: MessageInfo(
        id: 'a1',
        sessionID: _session,
        role: 'assistant',
        errorText: 'Step interrupted',
        errorKind: MessageErrorKind.aborted,
        time: MsgTime(created: 3, completed: 4),
      ),
      parts: [Part(id: 'a1-text', messageID: 'a1', type: 'text', text: 'Ok')],
    ),
    if (nextTurn) ...[
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_u2',
          sessionID: _session,
          role: 'user',
          time: MsgTime(created: 5),
        ),
        parts: [
          Part(
            id: 'text-0',
            messageID: 'msg_u2',
            type: 'text',
            text: 'Idk what happened',
          ),
        ],
      ),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_step1',
          sessionID: _session,
          role: 'assistant',
          finish: 'tool-calls',
          time: MsgTime(created: 6, completed: 7),
        ),
        parts: [
          Part(
            id: 'text-0',
            messageID: 'msg_step1',
            type: 'text',
            text: 'A Flutter project. Let me get the context.',
          ),
        ],
      ),
    ],
  ];

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
}

/// The v2 wire events of the person's next prompt up to the gap between its
/// first and second step, through the real v2 adapter.
List<EventEnvelope> _promptThenFirstStep(Api2EventAdapter adapter) {
  List<EventEnvelope> v2(String type, Map<String, dynamic> data) =>
      adaptApi2EventJson(adapter, {
        'type': type,
        'data': {'sessionID': _session, ...data},
      });
  const step = 'msg_step1';
  return [
    ...v2('session.inbox.enqueued', {
      'inboxID': 'msg_u2',
      'item': {
        'id': 'msg_u2',
        'type': 'user',
        'payload': {'text': 'Idk what happened'},
        'delivery': 'steer',
      },
    }),
    ...v2('session.execution.started', const {}),
    ...v2('session.inbox.delivered', {'inboxID': 'msg_u2'}),
    ...v2('session.step.started', {
      'assistantMessageID': step,
      'agent': 'build',
      'model': {'id': 'minimax-m3.1-flash-free', 'providerID': 'opencode'},
    }),
    ...v2('session.text.started', {'assistantMessageID': step, 'ordinal': 0}),
    ...v2('session.text.ended', {
      'assistantMessageID': step,
      'ordinal': 0,
      'text': 'A Flutter project. Let me get the context.',
    }),
    ...v2('session.step.ended', {
      'assistantMessageID': step,
      'finish': 'tool-calls',
      'cost': 0,
      'tokens': {
        'input': 10,
        'output': 5,
        'reasoning': 0,
        'cache': {'read': 0, 'write': 0},
      },
    }),
  ];
}

/// The real chat screen for [_session] over [api], on a phone-size screen.
Future<ConnectionController> _pumpChat(WidgetTester tester, _Api api) async {
  tester.view.physicalSize = const Size(1080, 2400);
  tester.view.devicePixelRatio = 2.6;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.upsert(
    ServerProfile(id: 'p1', name: 'Test', baseUrl: 'http://localhost:4096'),
  );
  await store.setActiveId('p1');
  final controller = ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(
          sessionID: _session,
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  await tester.pumpAndSettle();
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test('a finished step is not a finished OpenCode 2 run; the execution '
      'end is', () async {
    SharedPreferences.setMockInitialValues({});
    final controller = ConnectionController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    )..api = _Api();
    addTearDown(controller.dispose);
    final adapter = Api2EventAdapter();

    _promptThenFirstStep(adapter).forEach(controller.handleEventForTesting);
    expect(controller.busySessions, contains(_session));

    adaptApi2EventJson(adapter, {
      'type': 'session.execution.succeeded',
      'data': {'sessionID': _session},
    }).forEach(controller.handleEventForTesting);
    expect(controller.busySessions, isNot(contains(_session)));
  });

  testWidgets('after a Stop, the next turn still reads as running between '
      'two steps', (tester) async {
    final api = _Api();
    final controller = await _pumpChat(tester, api);
    // The stopped reply: no Stop, and the stopped moment's Continue chip.
    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
    expect(find.byKey(const ValueKey('phrase-chip-Continue')), findsOneWidget);

    // The server keeps what it streams: a history read now returns it.
    api.nextTurn = true;
    final adapter = Api2EventAdapter();
    _promptThenFirstStep(adapter).forEach(controller.handleEventForTesting);
    await tester.pumpAndSettle();

    // Its first step is done; the next has not streamed yet. The agent is
    // still working: Stop, and no phrase row over the glowing composer
    // (concept C), so no Continue either.
    expect(find.text('Idk what happened', findRichText: true), findsOneWidget);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    expect(find.byKey(const ValueKey('phrase-chip-Continue')), findsNothing);
    expect(
      find.byKey(const ValueKey("phrase-chip-What's the update?")),
      findsNothing,
    );

    adaptApi2EventJson(adapter, {
      'type': 'session.execution.succeeded',
      'data': {'sessionID': _session},
    }).forEach(controller.handleEventForTesting);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('chat-stop-button')), findsNothing);
  });

  testWidgets('tools whose results were lost settle once the next step '
      'starts, and the chat rereads the transcript', (tester) async {
    MessageWithParts user() => MessageWithParts(
      info: MessageInfo(
        id: 'msg_u2',
        sessionID: _session,
        role: 'user',
        time: MsgTime(created: 5),
      ),
      parts: [
        Part(
          id: 'text-0',
          messageID: 'msg_u2',
          type: 'text',
          text: 'Find the focus tokens',
        ),
      ],
    );
    Part search(String id, String pattern, String status) => Part(
      id: id,
      callID: id,
      messageID: 'msg_a1',
      type: 'tool',
      toolName: 'grep',
      toolState: ToolState(
        status: status,
        input: {'pattern': pattern},
        output: status == 'completed' ? 'Found 1 matches' : '',
      ),
    );
    // What the server holds: both searches finished, the next step running.
    List<MessageWithParts> server() => [
      user(),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_a1',
          sessionID: _session,
          role: 'assistant',
          finish: 'tool-calls',
          time: MsgTime(created: 6, completed: 7),
        ),
        parts: [
          search('call_1', 'Released|released', 'completed'),
          search('call_2', 'focusFill|focusRing|focusSurface', 'completed'),
        ],
      ),
      MessageWithParts(
        info: MessageInfo(
          id: 'msg_a2',
          sessionID: _session,
          role: 'assistant',
          time: MsgTime(created: 8),
        ),
      ),
    ];
    final api = _Api()..transcript = () => [user()];
    final controller = await _pumpChat(tester, api);
    final adapter = Api2EventAdapter();
    List<EventEnvelope> v2(String type, Map<String, dynamic> data) =>
        adaptApi2EventJson(adapter, {
          'type': type,
          'data': {'sessionID': _session, ...data},
        });
    // While a tool runs the turn's one live line names it; the folded work
    // line waits until the turn is done.
    bool searchRunning() => find
        .descendant(
          of: find.byKey(const ValueKey('kit-turn-live-line')),
          matching: find.textContaining('Searching for'),
        )
        .evaluate()
        .isNotEmpty;

    [
      ...v2('session.execution.started', const {}),
      ...v2('session.step.started', {
        'assistantMessageID': 'msg_a1',
        'agent': 'build',
      }),
      for (final (id, pattern) in [
        ('call_1', 'Released|released'),
        ('call_2', 'focusFill|focusRing|focusSurface'),
      ]) ...[
        ...v2('session.tool.input.started', {
          'assistantMessageID': 'msg_a1',
          'id': id,
          'name': 'grep',
        }),
        ...v2('session.tool.called', {
          'assistantMessageID': 'msg_a1',
          'id': id,
          'input': {'pattern': pattern},
          'executed': false,
        }),
      ],
    ].forEach(controller.handleEventForTesting);
    await tester.pump();
    await tester.pump();
    expect(searchRunning(), isTrue);

    // Both results and the step's end were lost; the next step starts.
    api.transcript = server;
    final readsBefore = api.reads;
    v2('session.step.started', {
      'assistantMessageID': 'msg_a2',
      'agent': 'build',
    }).forEach(controller.handleEventForTesting);
    await tester.pump(const Duration(seconds: 2));
    await tester.pump(const Duration(seconds: 2));

    expect(api.reads, greaterThan(readsBefore));
    expect(searchRunning(), isFalse);
    expect(controller.busySessions, contains(_session));
  });
}

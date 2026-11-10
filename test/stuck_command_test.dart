// A command that runs far past what agents allow (owner, 2026-10-29: "Running
// cd /root/projects/IPTV_King; git add -A… · 149 min 24 s" under a banner
// "Your message wasn't sent", and a typed message waiting behind a reply that
// never came). The live line says the step may be stuck, with Stop; a message
// typed meanwhile becomes "Stop and send"; a failed send reads the turn again.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show
        MidTurnPrompt,
        MidTurnPromptGateway,
        PromptDelivery,
        ServerCapabilities;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show SeededProfileStore;
import 'support/complete_message_history.dart';
import 'support/fake_event_channel.dart';
import 'support/fake_server_gateway.dart';

class _Api extends FakeServerGateway
    with CompleteMessageHistory
    implements MidTurnPromptGateway {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  final calls = <String>[];
  int messageReads = 0;
  bool failSends = false;

  // An agent backend (Paseo): sends are not held in the phone's queue.
  bool steering = false;
  MidTurnPrompt? midTurn;

  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(offlinePromptQueue: false, promptSteering: steering);

  @override
  MidTurnPrompt? midTurnPrompt(String sessionID) => midTurn;

  @override
  Future<List<Session>> sessions() async => const [];
  Map<String, String> statuses = const {};

  @override
  Future<Map<String, String>> sessionStatuses() async => statuses;
  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};
  @override
  Future<List<MessageWithParts>> messages(String id) async {
    messageReads += 1;
    return List.of(messagesResult);
  }

  @override
  Future<Session> session(String id) async => Session(id: id);
  @override
  Future<List<FileDiff>> diff(String id) async => const [];
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];
  @override
  Future<void> abort(String sessionID) async => calls.add('abort');

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
  }) async {
    calls.add('prompt:$text');
    if (failSends) {
      throw ApiException('The request could not finish.', statusCode: 500);
    }
  }
}

class _Controller extends ConnectionController {
  _Controller(super.store);
  @override
  Future<void> refreshCatalog() async {}
}

MessageWithParts _user() => MessageWithParts(
  info: MessageInfo(
    id: 'u1',
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [Part(id: 'u1-t', messageID: 'u1', type: 'text', text: 'Go')],
);

MessageWithParts _command({
  required Duration age,
  String status = 'running',
  Map<String, dynamic> extra = const {},
}) => MessageWithParts(
  info: MessageInfo(
    id: 'a1',
    sessionID: 'session-1',
    role: 'assistant',
    finish: 'tool-calls',
    time: MsgTime(created: 10),
  ),
  parts: [
    Part(
      id: 'a1-bash',
      callID: 'a1-bash',
      messageID: 'a1',
      type: 'tool',
      toolName: 'bash',
      toolState: ToolState(
        status: status,
        input: {'command': 'cd /root/projects/IPTV_King; git add -A', ...extra},
        startedAt: DateTime.now().subtract(age),
      ),
    ),
  ],
);

Future<ConnectionController> _pump(WidgetTester tester, _Api api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller =
      _Controller(
          SeededProfileStore(
            prefs: prefs,
            seeded: [
              ServerProfile(
                id: 'laptop',
                name: 'Laptop',
                baseUrl: 'http://192.168.1.20:4096',
              ),
            ],
          ),
        )
        ..api = api
        ..status = StreamStatus.connected;
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: MaterialApp(
        home: ChatScreen(
          sessionID: 'session-1',
          handoffStore: ReviewHandoffStore(),
        ),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.status',
      properties: {
        'sessionID': 'session-1',
        'status': {'type': 'busy'},
      },
    ),
  );
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
  return controller;
}

const _stuckWords = '2 h 29 min · may be stuck';

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(find.byKey(const Key('chat-composer-field')), text);
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  List<MessageWithParts> stuckTurn() => [
    _user(),
    _command(age: const Duration(hours: 2, minutes: 29)),
  ];

  bool has(String text) =>
      find.textContaining(text, findRichText: true).evaluate().isNotEmpty;

  testWidgets('a foreground command far past the limit says what is stuck, '
      'for how long, with the cd dropped', (tester) async {
    final api = _Api()..messagesResult = stuckTurn();
    await _pump(tester, api);
    expect(has(_stuckWords), isTrue);
    expect(has('git add -A'), isTrue);
    expect(has('cd /root'), isFalse);
  });

  testWidgets('Stop is in the chips row, in the attention tone, and stops '
      'the turn', (tester) async {
    final semantics = tester.ensureSemantics();
    final api = _Api()..messagesResult = stuckTurn();
    await _pump(tester, api);
    final stop = find.byKey(const Key('chat-stop-button'));
    expect(stop, findsOneWidget);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('kit-composer-status-strip-row')),
        matching: stop,
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('live-stop')), findsNothing);
    expect(find.text('Stop'), findsOneWidget);
    expect(find.bySemanticsLabel(RegExp(r'^Stop .*OpenCode')), findsWidgets);
    await tester.tap(stop);
    await tester.pump();
    await tester.pump();
    expect(api.calls, ['abort']);
    semantics.dispose();
  });

  testWidgets('a command inside the limit, or one sent to the background, is '
      'not stuck', (tester) async {
    final api = _Api()
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 9))];
    await _pump(tester, api);
    expect(has('may be stuck'), isFalse);
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);

    final background = _Api()
      ..messagesResult = [
        _user(),
        _command(
          age: const Duration(hours: 3),
          extra: {'run_in_background': true},
        ),
      ];
    await _pump(tester, background);
    expect(has('may be stuck'), isFalse);
  });

  testWidgets('Stop stays in the chips row with an empty field and with '
      'text; the field has no Stop and there is no live-line Stop', (
    tester,
  ) async {
    final api = _Api()
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    void expectOneStop() {
      expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
      expect(find.byKey(const Key('live-stop')), findsNothing);
      expect(find.byKey(const ValueKey('kit-turn-live-stop')), findsNothing);
      expect(find.byKey(const ValueKey('kit-composer-stop')), findsNothing);
    }

    expectOneStop();
    expect(find.byKey(const ValueKey('kit-composer-mic')), findsOneWidget);

    await _type(tester, 'Keep working');
    await tester.pump(const Duration(milliseconds: 400));
    expectOneStop();
    expect(find.byKey(const ValueKey('kit-composer-send')), findsOneWidget);

    await _type(tester, '');
    await tester.pump(const Duration(milliseconds: 400));
    expectOneStop();
    expect(
      tester.getSize(find.byKey(const Key('chat-stop-button'))).height,
      greaterThanOrEqualTo(48),
    );
  });

  testWidgets('an agent that steers keeps its Send under a stuck command: '
      'it adds, and the note says what it waits for', (tester) async {
    final api = _Api()
      ..steering = true
      ..messagesResult = stuckTurn();
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    expect(
      find.text('Waits for git add -A to finish. Stop it to get through now.'),
      findsOneWidget,
    );
    expect(
      find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
      findsNothing,
    );
    await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    expect(api.calls, ['prompt:Keep working'], reason: 'no stop first');
  });

  testWidgets('a steering agent that is not stuck says "Adds to this reply"', (
    tester,
  ) async {
    final api = _Api()
      ..steering = true
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    expect(find.text('Adds to this reply'), findsOneWidget);
  });

  testWidgets('an agent that cannot take a message mid-reply: Send is the '
      '"Stop and send" pill and the note names the agent', (tester) async {
    final api = _Api()
      ..midTurn = MidTurnPrompt.restartsTurn
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    expect(
      find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
      findsOneWidget,
    );
    expect(find.text('Stop and send'), findsOneWidget);
    expect(
      find.text("OpenCode can't take a message mid-reply."),
      findsOneWidget,
    );
  });

  testWidgets('OpenCode 1 under a stuck command: the Send pill is Stop and '
      'send, the turn stops, then the words go', (tester) async {
    final api = _Api()..messagesResult = stuckTurn();
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    expect(
      find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
      findsOneWidget,
    );
    expect(find.textContaining('may never end'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    expect(api.calls, ['abort', 'prompt:Keep working']);
  });

  testWidgets('OpenCode 1, healthy command: "Sends after this reply" and a '
      'plain Send', (tester) async {
    final api = _Api()
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    expect(find.textContaining('Sends after this reply'), findsOneWidget);
    expect(
      find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
      findsNothing,
    );
  });

  testWidgets('a failed send reads the turn again, and the message offers '
      'Stop and send', (tester) async {
    final api = _Api()
      ..failSends = true
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    await _type(tester, 'Keep working');
    final before = api.messageReads;
    // The step ended while the stream was away.
    api.messagesResult = [
      _user(),
      _command(age: const Duration(minutes: 2), status: 'completed'),
    ];
    await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    expect(api.messageReads, greaterThan(before));
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('kit-turn-live-line')),
        matching: find.textContaining('git add -A', findRichText: true),
      ),
      findsNothing,
      reason: 'the finished step must not keep showing as running',
    );
  });

  testWidgets('a send that failed while the turn still runs offers Stop and '
      'send', (tester) async {
    final api = _Api()
      ..failSends = true
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    final controller = await _pump(tester, api);
    await _type(tester, 'Keep working');
    // The server still says it works on the turn.
    api.statuses = {'session-1': 'busy'};
    await tester.tap(find.byKey(const ValueKey('kit-composer-send')));
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 300));
    }
    expect(controller.busySessions, contains('session-1'));
    expect(
      find.byKey(const ValueKey('kit-composer-circle-stopAndSend')),
      findsOneWidget,
    );
    expect(find.textContaining('Sends after this reply'), findsNothing);
  });

  testWidgets('the live line sits right above the chips row with Stop', (
    tester,
  ) async {
    final api = _Api()
      ..messagesResult = [_user(), _command(age: const Duration(minutes: 2))];
    await _pump(tester, api);
    final line = tester.getRect(
      find.byKey(const ValueKey('kit-turn-live-line')),
    );
    final stop = tester.getRect(find.byKey(const Key('chat-stop-button')));
    // The pill is 32 dp in its 48 dp target: the words are within a short
    // step of it (no empty band), and the two targets do not overlap.
    expect(stop.top - line.bottom, lessThanOrEqualTo(32));
    final tap = tester.getRect(find.byKey(const ValueKey('kit-turn-live-tap')));
    expect(tap.bottom, lessThanOrEqualTo(stop.top + 0.5));
  });
}

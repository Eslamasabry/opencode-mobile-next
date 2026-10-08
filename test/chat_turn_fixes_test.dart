// Three chat transcript defects (2026-10-08): a turn the person stopped ends
// on its "You stopped this reply." line even when the server reports no
// "aborted" error (Claude Code via Paseo, OpenCode 2's interrupt); the agent
// card tool reads in words, never `oc-ui_show`; a stalled turn says why in
// one plain line with Stop and Details (FC3, over the BA7 stall watchdog).
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show PromptDelivery;
import 'package:opencode_mobile/domain/turn_stall.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  int abortCalls = 0;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      List.of(messagesResult);

  @override
  Future<Session> session(String id) async => Session(id: id);

  @override
  Future<List<FileDiff>> diff(String id) async => const [];

  @override
  Future<List<Todo>> todos(String id) async => const [];

  @override
  Future<List<FileNode>> listFiles([String path = '']) async => const [];

  @override
  Future<void> abort(String sessionID) async {
    abortCalls += 1;
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
  }) async {}
}

/// A connection whose stall watchdog reports [stall] for every session.
class _Controller extends ConnectionController {
  _Controller(super.store);

  TurnStallDiagnosis? stall;

  @override
  Future<void> refreshCatalog() async {}

  @override
  TurnStallDiagnosis? turnStallFor(String sessionId) => stall;
}

MessageWithParts _user(String id) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [
    Part(id: '$id-text', messageID: id, type: 'text', text: 'Fix the build'),
  ],
);

MessageWithParts _assistant(String id, List<Part> parts) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    time: MsgTime(created: 10, completed: 11),
  ),
  parts: parts,
);

Part _text(String messageID, String text) =>
    Part(id: '$messageID-text', messageID: messageID, type: 'text', text: text);

Future<_Controller> _pumpChat(WidgetTester tester, _Api api) async {
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = _Controller(ProfileStore(prefs: prefs))
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
  return controller;
}

void _status(ConnectionController controller, Object status) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.status',
      properties: {'sessionID': 'session-1', 'status': status},
    ),
  );
}

Future<void> _stopThenIdle(
  WidgetTester tester,
  _Api api,
  ConnectionController controller,
) async {
  await tester.tap(find.byKey(const Key('chat-stop-button')));
  await tester.pump();
  await tester.pump();
  expect(api.abortCalls, 1);
  _status(controller, 'idle');
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 400));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('a turn the person stopped', () {
    testWidgets('ends on the stopped line without a server "aborted" error', (
      tester,
    ) async {
      // Paseo (Claude Code) and OpenCode 2 end a cancelled turn quietly: the
      // last step is just done, with no error on it.
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant('a1', [_text('a1', 'Looking at the build log now.')]),
        ];
      final controller = await _pumpChat(tester, api);
      _status(controller, {'type': 'busy'});
      await tester.pump();
      await tester.pump();
      expect(find.text('You stopped this reply.'), findsNothing);

      await _stopThenIdle(tester, api, controller);

      expect(find.text('You stopped this reply.'), findsOneWidget);
      expect(find.byKey(const Key('message-stopped')), findsOneWidget);
      // Stopped on purpose is not "No reply came back".
      expect(find.byKey(const Key('no-reply-send-again')), findsNothing);
    });

    testWidgets('says so on the prompt when nothing came back yet', (
      tester,
    ) async {
      final api = _Api()..messagesResult = [_user('u1')];
      final controller = await _pumpChat(tester, api);
      _status(controller, {'type': 'busy'});
      await tester.pump();
      await tester.pump();

      await _stopThenIdle(tester, api, controller);

      expect(find.text('You stopped this reply.'), findsOneWidget);
      expect(find.byKey(const Key('no-reply-send-again')), findsNothing);
    });

    testWidgets('a turn that finished on its own says nothing of a stop', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = [
          _user('u1'),
          _assistant('a1', [_text('a1', 'Done: the build passes.')]),
        ];
      await _pumpChat(tester, api);
      await tester.pumpAndSettle();
      expect(find.text('You stopped this reply.'), findsNothing);
    });
  });

  group('the agent card tool', () {
    for (final name in ['oc-ui_show', 'mcp__oc-ui__show']) {
      testWidgets('reads in words in the transcript, never "$name"', (
        tester,
      ) async {
        final api = _Api()
          ..messagesResult = [
            _user('u1'),
            _assistant('a1', [
              Part(
                id: 'a1-tool',
                messageID: 'a1',
                callID: 'call-1',
                type: 'tool',
                toolName: name,
                toolState: ToolState.fromJson({
                  'status': 'running',
                  'title': name,
                  'input': {'id': 'plan', 'title': 'Pick a plan'},
                }, toolName: name),
              ),
            ]),
          ];
        await _pumpChat(tester, api);
        // A running call keeps its progress moving: no pumpAndSettle.
        await tester.pump(const Duration(milliseconds: 300));
        expect(
          find.textContaining('Show card', findRichText: true),
          findsWidgets,
        );
        expect(find.textContaining('oc-ui', findRichText: true), findsNothing);
      });
    }
  });

  group('a stalled turn', () {
    const slow = TurnStallDiagnosis(
      kind: TurnStallKind.modelSlow,
      evidence: TurnStallEvidence(
        transportConnected: true,
        endpointReachable: true,
      ),
      silentFor: Duration(seconds: 52),
    );

    testWidgets('says why in one plain line, with Stop and Details', (
      tester,
    ) async {
      final api = _Api()..messagesResult = [_user('u1')];
      final controller = await _pumpChat(tester, api);
      _status(controller, {'type': 'busy'});
      await tester.pump();
      expect(find.byKey(const ValueKey('turn-stall')), findsNothing);

      controller.stall = slow;
      _status(controller, {'type': 'busy'});
      await tester.pump();
      await tester.pump();

      expect(find.byKey(const ValueKey('turn-stall')), findsOneWidget);
      expect(
        find.text(
          'The model is taking longer than usual. Wait, or stop the reply '
          'and try again.',
        ),
        findsOneWidget,
      );
      // The domain's contract words and its evidence stay out of the line.
      expect(find.textContaining('modelSlow'), findsNothing);

      // The turn's clock keeps ticking while it runs: no pumpAndSettle.
      await tester.tap(find.byKey(const Key('turn-stall-details')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      expect(
        find.textContaining('silent_for: 52s', findRichText: true),
        findsOneWidget,
      );
      Navigator.of(tester.element(find.byType(ChatScreen))).pop();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));

      await tester.tap(find.byKey(const Key('turn-stall-stop')));
      await tester.pump();
      await tester.pump();
      expect(api.abortCalls, 1);
    });

    for (final (kind, evidence, words) in [
      (
        TurnStallKind.helperDown,
        const TurnStallEvidence(transportConnected: true, helperRunning: false),
        'The agent\'s helper on this phone stopped. Stop the reply and try '
            'again.',
      ),
      (
        TurnStallKind.network,
        const TurnStallEvidence(transportConnected: false),
        'The connection to the agent was lost. Stop the reply and try again '
            'once it is back.',
      ),
      (
        TurnStallKind.network,
        const TurnStallEvidence(transportConnected: true),
        'Nothing has come back for a while and the connection could not be '
            'checked. Wait, or stop the reply and try again.',
      ),
    ]) {
      testWidgets(
        'names the cause: ${kind.name} ${evidence.transportConnected}',
        (tester) async {
          final api = _Api()..messagesResult = [_user('u1')];
          final controller = await _pumpChat(tester, api);
          controller.stall = TurnStallDiagnosis(
            kind: kind,
            evidence: evidence,
            silentFor: const Duration(seconds: 50),
          );
          _status(controller, {'type': 'busy'});
          await tester.pump();
          await tester.pump();
          expect(find.text(words), findsOneWidget);
        },
      );
    }

    testWidgets('goes when the turn is no longer running', (tester) async {
      final api = _Api()..messagesResult = [_user('u1')];
      final controller = await _pumpChat(tester, api)
        ..stall = slow;
      _status(controller, {'type': 'busy'});
      await tester.pump();
      await tester.pump();
      expect(find.byKey(const ValueKey('turn-stall')), findsOneWidget);
      _status(controller, 'idle');
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
      expect(find.byKey(const ValueKey('turn-stall')), findsNothing);
    });
  });
}

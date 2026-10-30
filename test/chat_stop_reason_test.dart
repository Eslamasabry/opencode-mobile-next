// Honest stop lines and no false "connection dropped" (owner's phone,
// 2026-10-24): a turn the server interrupted says who or what stopped it
// (OpenCode 2's interrupt reason), "You stopped this reply." stays for a
// Stop pressed in this app, and a reply that looks cut off is first checked
// with the server, which may still be running it.
import 'support/complete_message_history.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show PromptDelivery, ServerCapabilities, StreamStatus;
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/review_handoff.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  List<MessageWithParts> messagesResult = const [];
  bool inbox = false;

  @override
  ServerCapabilities get capabilities => inbox
      ? const ServerCapabilities(forms: true, inbox: true, promptSteering: true)
      : super.capabilities;
  Map<String, String> statuses = const {};
  int statusReads = 0;
  int abortCalls = 0;
  final prompts = <String>[];

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async {
    statusReads += 1;
    return statuses;
  }

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
  }) async {
    prompts.add(text);
  }
}

class _Controller extends ConnectionController {
  _Controller(super.store);

  @override
  Future<void> refreshCatalog() async {}
}

MessageWithParts _user(String id, {int created = 1}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: created, completed: created + 1),
  ),
  parts: [
    Part(id: '$id-text', messageID: id, type: 'text', text: 'Build the APK'),
  ],
);

MessageWithParts _assistant(
  String id, {
  int created = 10,
  int? completed = 11,
  String text = 'Running the build now.',
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: 'session-1',
    role: 'assistant',
    time: MsgTime(created: created, completed: completed),
  ),
  parts: [Part(id: '$id-text', messageID: id, type: 'text', text: text)],
);

Future<_Controller> _pumpChat(WidgetTester tester, _Api api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles':
        '[{"id":"profile","name":"Test","baseUrl":"http://localhost"}]',
    'oc.activeProfile': 'profile',
  });
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(secure, (_) async => null);
  addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = _Controller(store)
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

void _event(
  ConnectionController controller,
  String type,
  Map<String, dynamic> props,
) {
  controller.handleEventForTesting(
    EventEnvelope(type: type, properties: {'sessionID': 'session-1', ...props}),
  );
}

Future<void> _frames(WidgetTester tester) async {
  for (var i = 0; i < 4; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// A running turn the server then interrupts for [reason].
Future<(_Api, _Controller)> _interrupted(
  WidgetTester tester,
  String reason, {
  bool withReply = true,
}) async {
  final api = _Api()
    ..messagesResult = [_user('u1'), if (withReply) _assistant('a1')];
  final controller = await _pumpChat(tester, api);
  _event(controller, 'session.status', {
    'status': {'type': 'busy'},
  });
  await tester.pump();
  _event(controller, 'session.idle', {'interruptReason': reason});
  await _frames(tester);
  return (api, controller);
}

const _youStopped = 'You stopped this reply.';
const _elsewhere = 'This reply was stopped from another app or device.';
const _inactive =
    'The server paused this reply after an hour with no new work.';
const _restart = 'The server restarted, so this reply stopped.';
const _dropped = 'The connection dropped before this reply finished.';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('a turn the server interrupted', () {
    testWidgets('after an hour with no activity says the server paused it, '
        'with Continue', (tester) async {
      final (api, _) = await _interrupted(tester, 'inactivity');
      expect(find.text(_inactive), findsOneWidget);
      expect(find.text(_youStopped), findsNothing);
      await tester.tap(find.byKey(const Key('stopped-continue')));
      await _frames(tester);
      expect(api.prompts, ['Continue']);
    });

    testWidgets('by a Stop elsewhere does not claim the person stopped it', (
      tester,
    ) async {
      await _interrupted(tester, 'user');
      expect(find.text(_elsewhere), findsOneWidget);
      expect(find.text(_youStopped), findsNothing);
    });

    testWidgets('by a server restart says so', (tester) async {
      await _interrupted(tester, 'shutdown');
      expect(find.text(_restart), findsOneWidget);
      expect(find.byKey(const Key('stopped-continue')), findsOneWidget);
    });

    testWidgets('before any reply says so on the prompt', (tester) async {
      await _interrupted(tester, 'inactivity', withReply: false);
      expect(find.text(_inactive), findsOneWidget);
      expect(find.byKey(const Key('no-reply-send-again')), findsNothing);
    });

    testWidgets('a reply never finished reads as stopped, not dropped', (
      tester,
    ) async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api()
        ..messagesResult = [
          _user('u1', created: now - 60000),
          _assistant('a1', created: now - 50000, completed: null),
        ];
      final controller = await _pumpChat(tester, api);
      _event(controller, 'session.status', {
        'status': {'type': 'busy'},
      });
      await tester.pump();
      _event(controller, 'session.idle', {'interruptReason': 'user'});
      await _frames(tester);
      expect(find.text(_elsewhere), findsOneWidget);
      expect(find.text(_dropped), findsNothing);
    });

    testWidgets('a Stop pressed here still reads "You stopped this reply."', (
      tester,
    ) async {
      final api = _Api()..messagesResult = [_user('u1'), _assistant('a1')];
      final controller = await _pumpChat(tester, api);
      _event(controller, 'session.status', {
        'status': {'type': 'busy'},
      });
      await tester.pump();
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-stop-button')));
      await tester.pump();
      await tester.pump();
      expect(api.abortCalls, 1);
      _event(controller, 'session.idle', {'interruptReason': 'user'});
      await _frames(tester);
      expect(find.text(_youStopped), findsOneWidget);
      expect(find.text(_elsewhere), findsNothing);
    });

    testWidgets('the reason goes when the conversation runs again', (
      tester,
    ) async {
      final (_, controller) = await _interrupted(tester, 'inactivity');
      _event(controller, 'session.status', {
        'status': {'type': 'busy'},
      });
      await tester.pump();
      expect(controller.turnInterruptReason('session-1'), isNull);
      _event(controller, 'session.idle', {});
      await _frames(tester);
      expect(find.text(_inactive), findsNothing);
    });
  });

  group('a reply that looks cut off', () {
    List<MessageWithParts> unfinished() {
      final now = DateTime.now().millisecondsSinceEpoch;
      return [
        _user('u1', created: now - 60000),
        // Never completed, and this phone no longer thinks it is busy.
        _assistant('a1', created: now - 50000, completed: null),
      ];
    }

    testWidgets('keeps running while the server says it is still working', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = unfinished()
        ..statuses = {'session-1': 'busy'};
      final controller = await _pumpChat(tester, api);
      await _frames(tester);
      expect(api.statusReads, greaterThan(0));
      expect(find.text(_dropped), findsNothing);
      // The server's word makes the conversation busy again here.
      expect(controller.busySessions, contains('session-1'));
      expect(find.byKey(const Key('chat-stop-button')), findsWidgets);
    });

    testWidgets('also while the event stream is down but the server answers', (
      tester,
    ) async {
      final api = _Api()
        ..messagesResult = unfinished()
        ..statuses = {'session-1': 'busy'};
      final controller = await _pumpChat(tester, api);
      controller.status = StreamStatus.reconnecting;
      controller.notifyListeners();
      // Past the quiet reconnect's grace, when a dropped reply would show.
      await tester.pump(const Duration(seconds: 16));
      await _frames(tester);
      expect(find.text(_dropped), findsNothing);
      expect(find.textContaining('Connection lost'), findsNothing);
      // Back to normal, so the reconnect timers end with the test.
      controller.status = StreamStatus.connected;
      controller.notifyListeners();
      await _frames(tester);
    });

    testWidgets('a steer still waiting for the agent does not cut the turn '
        'it joins', (tester) async {
      // The owner's phone: a long build ran, a steer waited in the inbox
      // ("Waiting to send · 1 — Adds to this turn"), and the reply above it
      // read "The connection dropped" while the server kept building.
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api()
        ..inbox = true
        ..statuses = {'session-1': 'busy'}
        ..messagesResult = [
          ...unfinished(),
          MessageWithParts(
            info: MessageInfo(
              id: 'msg_steer',
              sessionID: 'session-1',
              role: 'user',
              time: MsgTime(created: now - 1000),
            ),
            parts: [
              Part(
                id: 'steer-text',
                messageID: 'msg_steer',
                type: 'text',
                text: 'Now finish building first',
              ),
            ],
          ),
        ];
      final controller = await _pumpChat(tester, api);
      _event(controller, 'session.status', {
        'status': {'type': 'busy'},
      });
      _event(controller, 'session.inbox.enqueued', {
        'inboxID': 'msg_steer',
        'item': {
          'type': 'user',
          'payload': {'text': 'Now finish building first'},
          'delivery': 'steer',
        },
      });
      await _frames(tester);
      expect(find.text('Now finish building first'), findsWidgets);
      expect(find.text(_dropped), findsNothing);
    });

    testWidgets('says the connection dropped once the server says it ended', (
      tester,
    ) async {
      final api = _Api()..messagesResult = unfinished();
      await _pumpChat(tester, api);
      await _frames(tester);
      expect(api.statusReads, greaterThan(0));
      expect(find.text(_dropped), findsOneWidget);
    });
  });
}

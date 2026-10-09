// Issue #9: reconnect and offline hardening. A mid-stream drop keeps the
// partial answer and a refetch completes it without duplicate text; a
// network switch or a long sleep never sends a prompt twice.
import 'support/complete_message_history.dart';
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show PromptDelivery;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');

  /// What the server holds right now; tests swap it to model a reply that
  /// kept running while the app was disconnected.
  List<MessageWithParts> transcript = const [];
  int messageLoads = 0;
  final prompts = <String>[];
  Completer<void>? promptGate;

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    messageLoads += 1;
    return transcript;
  }

  @override
  Future<Session> session(String id) async => Session(id: id);

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
    await promptGate?.future;
    prompts.add(text);
  }
}

MessageWithParts _prompt() => MessageWithParts(
  info: MessageInfo(
    id: 'u1',
    sessionID: 'session-1',
    role: 'user',
    time: MsgTime(created: 1, completed: 2),
  ),
  parts: [Part(id: 'u1-text', messageID: 'u1', type: 'text', text: 'Ask')],
);

MessageWithParts _answer(String text) => MessageWithParts(
  info: MessageInfo(
    id: 'a1',
    sessionID: 'session-1',
    role: 'assistant',
    time: MsgTime(created: 3, completed: 4),
  ),
  parts: [Part(id: 'a1-text', messageID: 'a1', type: 'text', text: text)],
);

EventEnvelope _delta(String text) => EventEnvelope(
  type: 'message.part.delta',
  properties: {
    'sessionID': 'session-1',
    'messageID': 'a1',
    'partID': 'a1-text',
    'field': 'text',
    'delta': text,
  },
);

Future<_Controller> _controller(_Api api) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  return _Controller(store)
    ..api = api
    ..status = StreamStatus.connected;
}

class _Controller extends ConnectionController {
  _Controller(super.store);
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController c) async {
  addTearDown(c.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(c)],
      child: const MaterialApp(
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        home: ChatScreen(sessionID: 'session-1'),
      ),
    ),
  );
  await tester.pump();
  await tester.pump();
}

QueuedPrompt _entry(String id, String text) => QueuedPrompt(
  id: id,
  profileID: 'profile-1',
  sessionID: 'session-1',
  text: text,
  attachments: const [],
  mentions: const [],
  createdAt: 1,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    // ProfileStore.load hangs without this answer inside testWidgets.
    messenger.setMockMethodCallHandler(
      const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
      (_) async => null,
    );
    messenger.setMockMethodCallHandler(
      const MethodChannel('oc/background'),
      (_) async => null,
    );
  });

  group('mid-stream drop', () {
    testWidgets('the partial answer stays on screen while the stream is down '
        'and the refetch completes it once', (tester) async {
      final api = _Api()..transcript = [_prompt(), _answer('The answer is')];
      final controller = await _controller(api);
      await _pumpChat(tester, controller);
      expect(find.text('The answer is'), findsOneWidget);

      controller.handleEventForTesting(_delta(' forty'));
      await tester.pump();
      await tester.pump();
      expect(find.text('The answer is forty'), findsOneWidget);

      // The server drops the stream: the partial text must not vanish, even
      // when a refresh runs while the stream is still down.
      api.transcript = [_prompt(), _answer('The answer is forty')];
      controller
        ..status = StreamStatus.reconnecting
        ..signalDataRefreshForTesting();
      controller.status = StreamStatus.disconnected;
      await tester.pump();
      await tester.pump();
      expect(find.text('The answer is forty'), findsOneWidget);

      // The reply finished while the app was away. Reconnect refetches the
      // canonical text; it replaces the partial and never appends to it.
      api.transcript = [_prompt(), _answer('The answer is forty-two.')];
      controller.status = StreamStatus.connected;
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();

      expect(find.text('The answer is forty-two.'), findsOneWidget);
      expect(find.text('The answer is forty'), findsNothing);
      expect(
        find.textContaining('forty The answer'),
        findsNothing,
        reason: 'no duplicated text after the refetch',
      );
      expect(find.textContaining('The answer is'), findsOneWidget);
      // Cancel controller clocks before the widget timer invariant runs.
      controller.dispose();
    });

    testWidgets('a delta arriving after the refetch extends the refetched '
        'text exactly once', (tester) async {
      final api = _Api()..transcript = [_prompt(), _answer('Part one')];
      final controller = await _controller(api);
      await _pumpChat(tester, controller);

      controller.status = StreamStatus.disconnected;
      controller.signalDataRefreshForTesting();
      await tester.pump();
      controller.status = StreamStatus.connected;
      api.transcript = [_prompt(), _answer('Part one, part two')];
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();

      controller.handleEventForTesting(_delta(', part three'));
      await tester.pump();
      await tester.pump();
      expect(find.text('Part one, part two, part three'), findsOneWidget);
      expect(find.textContaining('Part one'), findsOneWidget);
      // Cancel controller clocks before the widget timer invariant runs.
      controller.dispose();
    });
  });

  group('long sleep and resume', () {
    testWidgets('resuming after a sleep reconciles to the server transcript '
        'and shows the finished answer once', (tester) async {
      final api = _Api()..transcript = [_prompt(), _answer('Working on it')];
      final controller = await _controller(api);
      await _pumpChat(tester, controller);
      final loadsBefore = api.messageLoads;

      // Ten minutes asleep: the run finished on the server meanwhile.
      controller.status = StreamStatus.disconnected;
      api.transcript = [_prompt(), _answer('Done: all 12 files updated.')];
      controller.status = StreamStatus.connected;
      controller.signalDataRefreshForTesting();
      await tester.pumpAndSettle();

      expect(api.messageLoads, greaterThan(loadsBefore));
      expect(find.text('Done: all 12 files updated.'), findsOneWidget);
      expect(find.text('Working on it'), findsNothing);
      expect(find.text('Ask'), findsOneWidget);
      // Cancel controller clocks before the widget timer invariant runs.
      controller.dispose();
    });
  });

  group('network switch', () {
    test('a Wi-Fi to LTE flap with a prompt on the wire sends nothing '
        'twice', () async {
      final api = _Api()..promptGate = Completer<void>();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', 'ship it'));

      // First flush is in flight when the network changes and the stream
      // reports reconnect twice; each reconnect triggers another flush.
      final first = controller.flushOfflineQueue();
      await Future<void>.delayed(Duration.zero);
      controller.status = StreamStatus.reconnecting;
      final second = controller.flushOfflineQueue();
      controller.status = StreamStatus.connected;
      final third = controller.flushOfflineQueue();
      api.promptGate!.complete();
      await Future.wait([first, second, third]);

      expect(api.prompts, ['ship it']);
      expect(controller.queuedPromptCount, 0);
    });

    test(
      'the queue flushes once after the network returns, in order',
      () async {
        final api = _Api();
        final controller = await _controller(api);
        addTearDown(controller.dispose);
        controller.status = StreamStatus.disconnected;
        await controller.queuePrompt(_entry('q1', 'one'));
        await controller.queuePrompt(_entry('q2', 'two'));
        controller.status = StreamStatus.connected;

        await Future.wait([
          controller.flushOfflineQueue(),
          controller.flushOfflineQueue(),
        ]);
        await controller.flushOfflineQueue();

        expect(api.prompts, ['one', 'two']);
        expect(controller.queuedPromptCount, 0);
      },
    );

    test('a prompt whose send was cut by the switch is held for review, '
        'not resent on the new network', () async {
      final api = _CutApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(_entry('q1', 'maybe sent'));

      await controller.flushOfflineQueue();
      expect(api.attempts, 1);
      controller.status = StreamStatus.connected;
      await controller.flushOfflineQueue();
      await controller.flushOfflineQueue();

      expect(api.attempts, 1, reason: 'unconfirmed send is never auto-resent');
      expect(
        controller.queuedPromptsFor('session-1').single.dispatchedAt,
        isNotNull,
      );
    });
  });
}

class _CutApi extends _Api {
  int attempts = 0;

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
    attempts += 1;
    throw ApiException('connection reset by peer');
  }
}

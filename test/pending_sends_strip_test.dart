import 'support/complete_message_history.dart';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// A v2-flavored fake: forms/inbox capabilities on, inbox mutations
/// recorded, prompts recording their delivery mode.
class _V2ChatApi extends OpenCodeApi with CompleteMessageHistory {
  _V2ChatApi() : super(baseUrl: 'http://localhost');

  final prompts = <({String text, PromptDelivery? delivery})>[];
  final inboxCancels = <(String, String)>[];
  final inboxSteers = <(String, String)>[];
  final inboxQueues = <(String, String)>[];
  Object? inboxError;

  @override
  ServerCapabilities get capabilities =>
      const ServerCapabilities(forms: true, inbox: true);

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => const [];

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
    prompts.add((text: text, delivery: delivery));
  }

  @override
  Future<void> cancelInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxCancels.add((sessionID, inboxID));
  }

  @override
  Future<void> steerInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxSteers.add((sessionID, inboxID));
  }

  @override
  Future<void> queueInboxItem(String sessionID, String inboxID) async {
    if (inboxError case final error?) throw error;
    inboxQueues.add((sessionID, inboxID));
  }
}

/// A v1 fake: no inbox capability, so the composer keeps its lone Stop.
class _V1ChatApi extends OpenCodeApi with CompleteMessageHistory {
  _V1ChatApi() : super(baseUrl: 'http://localhost');

  final prompts = <({String text, PromptDelivery? delivery})>[];
  final seed = <MessageWithParts>[];

  @override
  Future<List<MessageWithParts>> messages(String id) async => seed;

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
    prompts.add((text: text, delivery: delivery));
  }
}

Future<ConnectionController> _controller(OpenCodeApi api) async {
  // Seed the profile through SharedPreferences rather than
  // ProfileStore.upsert: upsert writes the password through
  // flutter_secure_storage, whose platform channel never answers inside
  // testWidgets (see the mock installed in setUp).
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
  final preferences = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: preferences);
  await store.load();
  return ConnectionController(store)
    ..api = api
    ..status = StreamStatus.connected;
}

void _enqueue(
  ConnectionController controller, {
  String inboxID = 'msg_1',
  String text = 'pending server send',
  String delivery = 'queue',
  String type = 'user',
}) {
  controller.handleEventForTesting(
    EventEnvelope(
      type: 'session.inbox.enqueued',
      properties: {
        'sessionID': 'session-1',
        'inboxID': inboxID,
        'item': {
          'type': type,
          'payload': {'text': text},
          'delivery': delivery,
        },
      },
    ),
  );
}

/// Bounded pump: the chat screen keeps looping indicators alive in several
/// states, so pumpAndSettle can never settle here.
Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _pumpChat(
  WidgetTester tester,
  ConnectionController controller,
) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(controller)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await _settle(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // flutter_secure_storage's unmocked channel never answers inside
    // testWidgets; answer reads with null so profile loading cannot hang.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  testWidgets('the strip shows offline drafts and server inbox items in one '
      'list', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await controller.queuePrompt(
      QueuedPrompt(
        id: 'queued-1',
        profileID: 'profile-1',
        sessionID: 'session-1',
        text: 'offline draft',
        createdAt: 1,
      ),
    );
    await _pumpChat(tester, controller);
    _enqueue(controller, inboxID: 'msg_1', delivery: 'queue');
    _enqueue(
      controller,
      inboxID: 'msg_2',
      text: 'steering send',
      delivery: 'steer',
    );
    await _settle(tester);

    // One bubble, "Waiting to send · 3", both kinds oldest first, each with
    // its own state words (P4.3, KitQueuedMessage).
    expect(find.text('Waiting to send · 3'), findsOneWidget);
    expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_1')), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_2')), findsOneWidget);
    expect(find.text('offline draft'), findsOneWidget);
    expect(find.text('pending server send'), findsOneWidget);
    expect(find.text('Waiting to send'), findsOneWidget);
    expect(find.text('Sends after this reply'), findsOneWidget);
    expect(find.text('Adds to this turn'), findsOneWidget);
  });

  group('a queued message the server refused', () {
    const raw =
        'APIError: 529 {"type":"error","error":{"type":"overloaded_error",'
        '"message":"Overloaded"}}';

    Future<(_V1ChatApi, ConnectionController)> pump(
      WidgetTester tester, {
      bool connected = true,
    }) async {
      final api = _V1ChatApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await controller.queuePrompt(
        QueuedPrompt(
          id: 'queued-1',
          profileID: 'profile-1',
          sessionID: 'session-1',
          text: 'run the migration',
          createdAt: 1,
          error: raw,
        ),
      );
      if (!connected) controller.status = StreamStatus.disconnected;
      await _pumpChat(tester, controller);
      return (api, controller);
    }

    testWidgets(
      'says why in plain words and offers Try again, which sends it',
      (tester) async {
        final (api, controller) = await pump(tester);
        expect(find.text('run the migration'), findsOneWidget);
        // The reason in words; the server's text never shows as copy.
        expect(
          find.textContaining('The model provider is overloaded right now.'),
          findsOneWidget,
        );
        expect(find.textContaining('overloaded_error'), findsNothing);
        // The bubble's one call to action is Retry.
        final retry = find.byKey(const ValueKey('queued-bubble-retry'));
        expect(retry, findsOneWidget);
        expect(
          find.descendant(of: retry, matching: find.text('Try again')),
          findsOneWidget,
        );
        await tester.tap(retry);
        await _settle(tester);
        expect(api.prompts.map((prompt) => prompt.text), ['run the migration']);
        expect(controller.queuedPromptsFor('session-1'), isEmpty);
      },
    );

    testWidgets('its menu has Try again first, then Edit and Discard', (
      tester,
    ) async {
      final (api, _) = await pump(tester);
      await tester.tap(find.text('run the migration'));
      await _settle(tester);
      final retry = find.byKey(const ValueKey('queued-action-retry'));
      expect(retry, findsOneWidget);
      expect(
        tester.getTopLeft(retry).dy,
        lessThan(
          tester
              .getTopLeft(find.byKey(const ValueKey('queued-action-edit')))
              .dy,
        ),
      );
      await tester.tap(retry);
      await _settle(tester);
      expect(api.prompts, hasLength(1));
    });

    testWidgets('offline there is no Try again: it waits for the reconnect', (
      tester,
    ) async {
      final (_, controller) = await pump(tester, connected: false);
      expect(find.text('run the migration'), findsOneWidget);
      expect(find.byKey(const ValueKey('queued-bubble-retry')), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
    });
  });

  testWidgets('cancelling an inbox item returns its text to the draft, and '
      'Undo sends it again the same way', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    _enqueue(controller, inboxID: 'msg_1', text: 'bring me back');
    await _settle(tester);

    // The item's own menu holds its actions. Words only: Undo puts it back,
    // so nothing asks first (kit-v2 §4.1).
    await tester.tap(find.byKey(const ValueKey('pending-send-msg_1')));
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('inbox-action-cancel')));
    await _settle(tester);

    expect(api.inboxCancels.single, ('session-1', 'msg_1'));
    expect(find.byKey(const ValueKey('pending-send-msg_1')), findsNothing);
    String composerText() => tester
        .widget<EditableText>(
          find.descendant(
            of: find.byKey(const Key('chat-composer-field')),
            matching: find.byType(EditableText),
          ),
        )
        .controller
        .text;
    expect(composerText(), 'bring me back');
    expect(find.text('Returned to your draft'), findsOneWidget);

    await tester.tap(find.text('Undo'));
    await _settle(tester);
    expect(composerText(), isEmpty);
    expect(api.prompts.single.text, 'bring me back');
    expect(api.prompts.single.delivery, PromptDelivery.queue);
  });

  testWidgets('the bubble offers only the inline flip that changes the mode', (
    tester,
  ) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    _enqueue(controller, inboxID: 'msg_1', delivery: 'queue');
    await _settle(tester);

    await tester.tap(find.byKey(const ValueKey('pending-send-msg_1')));
    await _settle(tester);
    expect(find.byKey(const ValueKey('inbox-action-steer')), findsOneWidget);
    expect(find.byKey(const ValueKey('inbox-action-queue')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('inbox-action-steer')));
    await _settle(tester);

    expect(api.inboxSteers.single, ('session-1', 'msg_1'));
    expect(find.text('Adds to this turn'), findsOneWidget);

    // Now the opposite flip is the one on offer.
    await tester.tap(find.byKey(const ValueKey('pending-send-msg_1')));
    await _settle(tester);
    expect(find.byKey(const ValueKey('inbox-action-queue')), findsOneWidget);
    expect(find.byKey(const ValueKey('inbox-action-steer')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('inbox-action-queue')));
    await _settle(tester);
    expect(api.inboxQueues.single, ('session-1', 'msg_1'));
  });

  testWidgets('a 409 flip toasts Already delivered and drops the bubble', (
    tester,
  ) async {
    final api = _V2ChatApi()
      ..inboxError = ApiException(
        'delivered',
        statusCode: 409,
        errorTag: 'ConflictError',
      );
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    _enqueue(controller, inboxID: 'msg_1', delivery: 'queue');
    await _settle(tester);

    await tester.tap(find.byKey(const ValueKey('pending-send-msg_1')));
    await _settle(tester);
    await tester.tap(find.byKey(const ValueKey('inbox-action-steer')));
    await _settle(tester);

    expect(find.text('Already delivered'), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_1')), findsNothing);
  });

  testWidgets('non-user inbox items are informational only', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    _enqueue(controller, inboxID: 'msg_ctx', type: 'synthetic');
    await _settle(tester);

    expect(find.text('Context pending'), findsOneWidget);
    // A standing fact, so a chip in the strip above the composer, not a
    // bubble of its own among the things you sent.
    expect(find.byKey(const Key('pending-context-chip')), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_ctx')), findsNothing);
    _enqueue(controller, inboxID: 'msg_ctx2', type: 'synthetic');
    await _settle(tester);
    expect(find.text('Context pending · 2'), findsOneWidget);
    expect(find.byKey(const ValueKey('inbox-action-cancel')), findsNothing);
    expect(find.byKey(const ValueKey('inbox-action-steer')), findsNothing);
    expect(find.byKey(const ValueKey('inbox-action-queue')), findsNothing);
  });

  // The busy chat runs a looping typing indicator, so these use pump() with
  // explicit frames rather than pumpAndSettle (which would never settle).
  testWidgets('a prompt waiting in the inbox is shown once, not twice', (
    tester,
  ) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Why vertical offers?',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    // Sent: its optimistic copy stands in the transcript.
    expect(find.text('Why vertical offers?'), findsOneWidget);

    // The server holds it for the next step. It is now the waiting bubble,
    // with its flip and cancel; the transcript does not repeat it.
    _enqueue(
      controller,
      inboxID: 'msg_wait',
      text: 'Why vertical offers?',
      delivery: 'steer',
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
    expect(find.text('Why vertical offers?'), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_wait')), findsOneWidget);
  });

  testWidgets('the server\'s own copy of a waiting prompt is not repeated '
      'either', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Sections are near the background colours',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    // What a live server does: it admits the prompt under the id it will
    // keep, and the app swaps its optimistic copy for that message.
    void event(String type, Map<String, dynamic> properties) =>
        controller.handleEventForTesting(
          EventEnvelope(type: type, properties: properties),
        );
    event('message.updated', {
      'info': {
        'id': 'msg_wait',
        'sessionID': 'session-1',
        'role': 'user',
        'time': {'created': DateTime.now().millisecondsSinceEpoch},
      },
    });
    event('message.part.updated', {
      'sessionID': 'session-1',
      'part': {
        'id': 'p1',
        'sessionID': 'session-1',
        'messageID': 'msg_wait',
        'type': 'text',
        'text': 'Sections are near the background colours',
      },
    });
    _enqueue(
      controller,
      inboxID: 'msg_wait',
      text: 'Sections are near the background colours',
      delivery: 'steer',
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));

    expect(find.byKey(const ValueKey('pending-send-msg_wait')), findsOneWidget);
    expect(
      find.text('Sections are near the background colours'),
      findsOneWidget,
    );
  });

  group('steering several times in a row', () {
    Future<(_V2ChatApi, ConnectionController)> busyChat(
      WidgetTester tester,
    ) async {
      final api = _V2ChatApi();
      final controller = await _controller(api);
      addTearDown(controller.dispose);
      await _pumpChat(tester, controller);
      controller.busySessions.add('session-1');
      controller.notifyListeners();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      return (api, controller);
    }

    Future<void> send(
      WidgetTester tester,
      String text, {
      bool steer = false,
    }) async {
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        text,
      );
      await tester.pump();
      if (steer) {
        // "Send after this reply" is the default (P6.6); adding to the
        // running turn is the person's choice.
        await tester.tap(find.text('Add to this turn').last);
        await tester.pump();
      }
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    }

    testWidgets('waiting steers are taken back and sent as one message', (
      tester,
    ) async {
      final (api, controller) = await busyChat(tester);
      _enqueue(
        controller,
        inboxID: 'm1',
        text: 'Use a carousel',
        delivery: 'steer',
      );
      _enqueue(
        controller,
        inboxID: 'm2',
        text: 'and keep it compact',
        delivery: 'steer',
      );
      // Queued for after the run is a different intent: it is left alone.
      _enqueue(
        controller,
        inboxID: 'm3',
        text: 'then write tests',
        delivery: 'queue',
      );
      await tester.pump();

      await send(tester, 'with a detail sheet', steer: true);

      expect(api.inboxCancels.map((c) => c.$2), ['m1', 'm2']);
      expect(api.prompts.single.delivery, PromptDelivery.steer);
      expect(
        api.prompts.single.text,
        'Use a carousel\n\nand keep it compact\n\nwith a detail sheet',
      );
    });

    testWidgets('one the agent already took goes out on its own', (
      tester,
    ) async {
      final (api, controller) = await busyChat(tester);
      _enqueue(controller, inboxID: 'm1', text: 'Too late', delivery: 'steer');
      await tester.pump();
      api.inboxError = ApiException('delivered', statusCode: 409);

      await send(tester, 'next thought');

      expect(api.prompts.single.text, 'next thought');
    });

    testWidgets('a message for after the run merges nothing', (tester) async {
      final (api, controller) = await busyChat(tester);
      _enqueue(controller, inboxID: 'm1', text: 'Steer me', delivery: 'steer');
      await tester.pump();
      await tester.enterText(
        find.byKey(const Key('chat-composer-field')),
        'later please',
      );
      await tester.pump();
      await tester.tap(find.text('Send after'));
      await tester.pump();
      await tester.tap(find.byKey(const Key('chat-send-button')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(api.inboxCancels, isEmpty);
      expect(api.prompts.single.text, 'later please');
      expect(api.prompts.single.delivery, PromptDelivery.queue);
    });
  });

  testWidgets('while busy on v2 Send is Stop and the mic stays; the toggle '
      'queues', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    // The composer's Send is Stop (the only one); the mic, where the
    // platform has one, stays beside it.
    expect(find.byKey(const ValueKey('kit-composer-stop')), findsOneWidget);
    expect(find.byKey(const Key('chat-send-button')), findsNothing);
    // Watching a run with nothing typed shows no delivery strip.
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'interject now',
    );
    await tester.pump();
    // UX-P0-04: once there is something to send, the choice is stated in
    // words while the run is active.
    expect(find.byKey(const Key('composer-delivery-control')), findsOneWidget);
    expect(find.text('Add to this turn'), findsOneWidget);
    expect(find.text('Send after'), findsOneWidget);

    expect(
      tester
          .widget<KitSegmented<KitComposerDelivery>>(
            find.byKey(const Key('composer-delivery-control')),
          )
          .selected,
      KitComposerDelivery.afterThisReply,
    );
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    // Select the mid-turn choice; the kit defaults to sending after the reply.
    await tester.tap(find.text('Add to this turn'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(api.prompts.single.text, 'interject now');
    expect(api.prompts.single.delivery, PromptDelivery.steer);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'after this run',
    );
    await tester.pump();
    await tester.tap(find.text('Send after'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(api.prompts.last.text, 'after this run');
    expect(api.prompts.last.delivery, PromptDelivery.queue);
    // No hidden gesture: a long press on Send opens nothing.
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'one more',
    );
    await tester.pump();
    await tester.longPress(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.byKey(const Key('send-delivery-menu')), findsNothing);
  });

  testWidgets('v1 keeps Send live while busy and queues after the run', (
    tester,
  ) async {
    final api = _V1ChatApi()
      ..seed.addAll([
        MessageWithParts(
          info: MessageInfo(
            id: 'u1',
            sessionID: 'session-1',
            role: 'user',
            time: MsgTime(created: 1),
          ),
          parts: [Part(type: 'text', text: 'first ask')],
        ),
        MessageWithParts(
          info: MessageInfo(
            id: 'a1',
            sessionID: 'session-1',
            role: 'assistant',
            time: MsgTime(created: 2),
          ),
          parts: [Part(type: 'text', text: 'working on it')],
        ),
      ]);
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    // OpenCode 1 accepts a prompt mid-turn and runs it afterwards: Send is
    // Stop while nothing is typed, and the composer says what Send will do
    // once something is typed.
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    expect(find.byKey(const ValueKey('kit-composer-stop')), findsOneWidget);
    // Nothing typed yet: no hint competes with the running reply.
    expect(find.text('Sends after this reply'), findsNothing);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'after this run',
    );
    await tester.pump();
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    expect(find.text('Sends after this reply'), findsOneWidget);
    // v1 has no inbox, so there is nothing to choose between.
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(api.prompts.single.text, 'after this run');
    expect(api.prompts.single.delivery, isNull);
    // The optimistic bubble says it is waiting for the current turn.
    expect(
      find.textContaining('Queued · runs after this turn'),
      findsOneWidget,
    );

    controller.busySessions.remove('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.textContaining('Queued · runs after this turn'), findsNothing);
    expect(find.text('Sends after this reply'), findsNothing);
  });

  testWidgets('the delivery control is absent until a run is active and '
      'something is typed', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);

    // Idle composer: nothing to deliver into, so no extra density.
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'typed while idle',
    );
    await tester.pump();
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);

    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byKey(const Key('composer-delivery-control')), findsOneWidget);

    // Clearing the field while the run continues removes the strip again.
    await tester.enterText(find.byKey(const Key('chat-composer-field')), '');
    await tester.pump();
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'typed while busy',
    );
    await tester.pump();
    expect(find.byKey(const Key('composer-delivery-control')), findsOneWidget);

    controller.busySessions.remove('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);
  });

  testWidgets('choosing Queue in the visible control sends with queue '
      'delivery', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'after this run',
    );
    await tester.pump();
    await tester.tap(find.text('Send after'));
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(api.prompts.single.text, 'after this run');
    expect(api.prompts.single.delivery, PromptDelivery.queue);

    // The choice is remembered, and the label keeps showing it, so the next
    // send does not silently revert to steering.
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'and this one too',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    expect(api.prompts.last.delivery, PromptDelivery.queue);
  });

  testWidgets('the busy v2 composer with the delivery control survives 2.5x '
      'text on a 360dp phone', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(360, 740);
    addTearDown(tester.view.reset);
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(
          size: Size(360, 740),
          textScaler: TextScaler.linear(2.5),
        ),
        child: ProviderScope(
          overrides: [connProvider.overrideWithValue(controller)],
          child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
        ),
      ),
    );
    await _settle(tester);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await _settle(tester);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'large text send',
    );
    await _settle(tester);

    expect(find.byKey(const Key('composer-delivery-control')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('the toggle is the only control and remembers its choice', (
    tester,
  ) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'queued through the toggle',
    );
    await tester.pump();
    await tester.tap(find.text('Send after'));
    await tester.pump();
    expect(find.byTooltip('Send after this reply'), findsOneWidget);
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(api.prompts.single.delivery, PromptDelivery.queue);
    // The send cleared the field, so the strip is gone; typing the next
    // prompt brings it back still set to Queue.
    expect(find.byType(KitSegmented<KitComposerDelivery>), findsNothing);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'next one',
    );
    await tester.pump();
    final toggle = tester.widget<KitSegmented<KitComposerDelivery>>(
      find.byType(KitSegmented<KitComposerDelivery>),
    );
    expect(toggle.selected, KitComposerDelivery.afterThisReply);
  });
}

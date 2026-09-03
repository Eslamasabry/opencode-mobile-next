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
import 'package:shared_preferences/shared_preferences.dart';

/// A v2-flavored fake: forms/inbox capabilities on, inbox mutations
/// recorded, prompts recording their delivery mode.
class _V2ChatApi extends OpenCodeApi {
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
class _V1ChatApi extends OpenCodeApi {
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

    // One strip, both kinds, differentiated only by icon + status line.
    expect(find.byKey(const ValueKey('queued-send-0')), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_1')), findsOneWidget);
    expect(find.byKey(const ValueKey('pending-send-msg_2')), findsOneWidget);
    expect(find.text('offline draft'), findsOneWidget);
    expect(find.text('pending server send'), findsOneWidget);
    expect(find.text('Queued — will send when reconnected'), findsOneWidget);
    expect(find.text('Waiting for this run to finish'), findsOneWidget);
    expect(find.text('Steering at the next step'), findsOneWidget);
  });

  testWidgets('cancelling an inbox item returns its text to the composer', (
    tester,
  ) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    _enqueue(controller, inboxID: 'msg_1', text: 'bring me back');
    await _settle(tester);

    await tester.tap(find.byKey(const ValueKey('inbox-action-cancel')));
    await _settle(tester);
    // Confirm sheet: cancel-back-to-composer is destructive-confirmed.
    await tester.tap(find.text('Cancel message'));
    await _settle(tester);

    expect(api.inboxCancels.single, ('session-1', 'msg_1'));
    expect(find.byKey(const ValueKey('pending-send-msg_1')), findsNothing);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-composer-field')))
          .controller
          ?.text,
      'bring me back',
    );
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

    expect(find.byKey(const ValueKey('inbox-action-steer')), findsOneWidget);
    expect(find.byKey(const ValueKey('inbox-action-queue')), findsNothing);
    await tester.tap(find.byKey(const ValueKey('inbox-action-steer')));
    await _settle(tester);

    expect(api.inboxSteers.single, ('session-1', 'msg_1'));
    expect(find.text('Steering at the next step'), findsOneWidget);

    // Now the opposite flip is the one on offer.
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

    expect(find.text('Context update pending'), findsOneWidget);
    expect(find.byKey(const ValueKey('inbox-action-cancel')), findsNothing);
    expect(find.byKey(const ValueKey('inbox-action-steer')), findsNothing);
    expect(find.byKey(const ValueKey('inbox-action-queue')), findsNothing);
  });

  // The busy chat runs a looping typing indicator, so these use pump() with
  // explicit frames rather than pumpAndSettle (which would never settle).
  testWidgets('while busy on v2 Stop and Send sit side by side; the toggle '
      'queues', (tester) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);
    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    // UX-P0-04: the choice is stated in words while the run is active.
    expect(find.byKey(const Key('composer-delivery-control')), findsOneWidget);
    expect(find.text('Steer'), findsOneWidget);
    expect(find.text('Queue'), findsOneWidget);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'interject now',
    );
    await tester.pump();

    // Steer is the selected default, and now rides explicitly so the sent
    // delivery always matches the label the user can see.
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
    await tester.tap(find.byKey(const Key('composer-delivery-queue')));
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(api.prompts.last.text, 'after this run');
    expect(api.prompts.last.delivery, PromptDelivery.queue);
    // No hidden gesture: a long press on Send opens nothing.
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

    // OpenCode 1 accepts a prompt mid-turn and runs it afterwards, so Stop
    // and Send sit side by side and the composer says what Send will do.
    expect(find.byKey(const Key('chat-stop-button')), findsOneWidget);
    expect(find.byKey(const Key('chat-send-button')), findsOneWidget);
    expect(find.byKey(const Key('composer-queue-hint')), findsOneWidget);
    // v1 has no inbox, so there is nothing to choose between.
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'after this run',
    );
    await tester.pump();
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
    expect(find.byKey(const Key('composer-queue-hint')), findsNothing);
  });

  testWidgets('the delivery control is absent until a run is active', (
    tester,
  ) async {
    final api = _V2ChatApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);

    // Idle composer: nothing to deliver into, so no extra density.
    expect(find.byKey(const Key('composer-delivery-control')), findsNothing);

    controller.busySessions.add('session-1');
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
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

    await tester.tap(find.byKey(const Key('composer-delivery-queue')));
    await tester.pump();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'after this run',
    );
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

    await tester.tap(find.byKey(const Key('composer-delivery-queue')));
    await tester.pump();
    expect(find.text('Send waits for this run to finish'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'queued through the toggle',
    );
    await tester.pump();
    await tester.tap(find.byKey(const Key('chat-send-button')));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(api.prompts.single.delivery, PromptDelivery.queue);
    final toggle = tester.widget<SegmentedButton<PromptDelivery>>(
      find.byType(SegmentedButton<PromptDelivery>),
    );
    expect(toggle.selected, {PromptDelivery.queue});
  });
}

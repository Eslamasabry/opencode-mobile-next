// The chat's states and banners on the design kit
// (docs/design/design-standard.md §9 step 5; docs/qa/design-standard-chat-
// 2026-09-24/README.md): what the person sees while a conversation loads,
// when it cannot load, when a message was not sent, while the connection is
// away, and when the agent asks for permission.
//
// Only public ChatScreen behaviour and widget keys are used, so the same
// file shows the old code failing (see the QA record).
//
// ignore_for_file: invalid_use_of_protected_member
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _Api extends CaptureApi {
  Object? sendError;
  int sendAttempts = 0;

  // Receipt-bearing delivery is exercised by command_receipts_queue_test
  // and the real OpenCode 2 gateway. This fixture stands in for a server
  // without receipts, so the explicit resend goes straight to the prompt
  // call the assertions below count.
  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    phoneTeamExecution: true,
    clientPromptMessageID: true,
    setupConfigRead: true,
    setupMcpInventory: true,
    mcpRuntimeAdds: true,
    mcpChatConnect: true,
    mcpChatOAuth: true,
    mcpChatToolRefresh: true,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);

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
    sendAttempts += 1;
    if (sendError case final error?) throw error;
    prompts.add(text);
  }

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeSend?.call();
    await promptAsync(
      sessionID,
      text: text,
      model: model,
      agent: agent,
      variant: variant,
      attachments: attachments,
      agentMentions: agentMentions,
      delivery: delivery,
    );
  }
}

class _AgentController extends CaptureController {
  _AgentController(super.store);

  @override
  bool get isAgentBackend => true;

  // Capture fixtures inject their gateway without connect(), so no private
  // connected profile exists for the secondary-backend profile getter.
  @override
  ServerProfile get profile => store.profiles.single;
}

List<MessageWithParts> _turn() {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now - 9000),
      parts: [
        Part(
          id: 'part_user',
          messageID: 'msg_user',
          type: 'text',
          text: userPrompt,
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo(
        'msg_assistant',
        'assistant',
        created: now - 8000,
        completed: now - 1000,
      ),
      parts: [textPart('part_intro', 'The checkout test is fixed.')],
    ),
  ];
}

Future<void> _frames(WidgetTester tester, [int count = 6]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<CaptureController> _chat(
  WidgetTester tester,
  _Api api, {
  void Function(CaptureController controller)? setUp,
  bool agentBackend = false,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  final controller = agentBackend
      ? (_AgentController(
            SeededProfileStore(
              prefs: prefs,
              seeded: [
                ServerProfile(
                  id: 'claude-backend',
                  name: 'Claude Code',
                  baseUrl: 'ws://127.0.0.1:4099',
                  backend: ServerBackend.paseo,
                ),
              ],
            ),
          )
          ..api = api
          ..repository = CaptureRepository()
          ..status = StreamStatus.connected
          ..directory = projectDirectory
          ..sessionsById = Map.of(api.sessionsById)
          ..busySessions = Set.of(api.busy))
      : await captureController(prefs: prefs, api: api);
  if (!agentBackend) {
    (controller.store as SeededProfileStore).seeded[0] = ServerProfile(
      id: 'laptop',
      name: 'Laptop',
      baseUrl: api.baseUrl,
    );
  }
  // Receipt recovery verifies that the saved profile still exists. The
  // capture store exposes profiles in memory; mirror the real saved state
  // and the injected transport's endpoint so the identity fence can pass.
  await prefs.setString(
    'oc.profiles',
    jsonEncode([
      for (final profile in controller.store.profiles) profile.toJson(),
    ]),
  );
  setUp?.call(controller);
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });
  final navigatorKey = GlobalKey<NavigatorState>();
  await tester.pumpWidget(
    captureApp(
      home: Builder(
        builder: (context) => ListenableBuilder(
          listenable: controller,
          builder: (context, _) => AppConditionsScope(
            conditions: [
              connectionKitStatus(
                context,
                controller,
                actionContext: () =>
                    navigatorKey.currentState?.overlay?.context,
              ),
            ],
            child: const ChatScreen(sessionID: checkoutSessionID),
          ),
        ),
      ),
      boundaryKey: GlobalKey(),
      navigatorKey: navigatorKey,
      controller: controller,
    ),
  );
  await _frames(tester);
  return controller;
}

final _bar = find.byKey(const ValueKey('kit-loading-bar'));
final _connectionLine = find.byKey(const ValueKey('connection-status-banner'));

void main() {
  testWidgets('first load: one loading bar and placeholder turns, no text', (
    tester,
  ) async {
    final hold = Completer<List<MessageWithParts>>();
    await _chat(
      tester,
      _Api()
        ..busy = {}
        ..messagesHandler = (_) => hold.future,
    );

    expect(_bar, findsOneWidget);
    expect(find.byType(LinearProgressIndicator), findsOneWidget);
    expect(find.byKey(const ValueKey('chat-loading')), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    hold.complete(_turn());
    await _frames(tester);
    expect(_bar, findsNothing);
    expect(find.byKey(const ValueKey('chat-loading')), findsNothing);
    expect(find.text('The checkout test is fixed.'), findsOneWidget);
  });

  testWidgets('a conversation that could not load says so plainly, with Try '
      'again, and the raw error only under Details', (tester) async {
    var attempts = 0;
    final api = _Api()..busy = {};
    api.messagesHandler = (_) async {
      attempts += 1;
      if (attempts == 1) {
        throw ApiException('Cannot reach http://192.168.1.20:4096: refused');
      }
      return _turn();
    };
    await _chat(tester, api);

    expect(find.text("Couldn't open this conversation"), findsOneWidget);
    expect(
      find.text('Nothing is lost. Try again when OpenCode answers.'),
      findsOneWidget,
    );
    expect(find.textContaining('192.168.1.20'), findsNothing);
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await _frames(tester);
    expect(find.textContaining('192.168.1.20'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('chat-load-retry')));
    await _frames(tester);
    expect(find.text("Couldn't open this conversation"), findsNothing);
    expect(find.text('The checkout test is fixed.'), findsOneWidget);
  });

  testWidgets('a Claude history failure names its helper and can retry', (
    tester,
  ) async {
    var attempts = 0;
    final api = _Api()..busy = {};
    api.messagesHandler = (_) async {
      if (++attempts == 1) {
        throw const ProductException('Synthetic helper history failure');
      }
      return _turn();
    };
    await _chat(tester, api, agentBackend: true);

    final error = find.byKey(const ValueKey('chat-load-error'));
    expect(error, findsOneWidget);
    expect(
      find.descendant(
        of: error,
        matching: find.textContaining("on this phone isn't answering"),
      ),
      findsOneWidget,
    );
    expect(
      find.descendant(of: error, matching: find.textContaining('Claude Code')),
      findsOneWidget,
    );
    expect(find.textContaining('when OpenCode answers'), findsNothing);
    expect(
      find.textContaining('Synthetic helper history failure'),
      findsNothing,
    );

    await tester.tap(find.byKey(const ValueKey('chat-load-retry')));
    await _frames(tester);
    expect(error, findsNothing);
    expect(find.text('The checkout test is fixed.'), findsOneWidget);
  });

  Future<void> agentOpenFails(
    WidgetTester tester,
    PaseoFailure failure,
    String words,
  ) async {
    final api = _Api()..busy = {};
    api.messagesHandler = (_) async => throw failure;
    await _chat(tester, api, agentBackend: true);
    final error = find.byKey(const ValueKey('chat-load-error'));
    expect(error, findsOneWidget);
    // The agent's name sits in bidi isolates (KitBidi.auto).
    expect(
      find.descendant(of: error, matching: find.textContaining(words)),
      findsOneWidget,
    );
    expect(
      find.descendant(of: error, matching: find.textContaining('Claude Code')),
      findsOneWidget,
    );
    expect(find.textContaining("isn't answering"), findsNothing);
    await tester.tap(find.byKey(const ValueKey('kit-state-details')));
    await _frames(tester);
    expect(find.textContaining(failure.reason!), findsOneWidget);
  }

  testWidgets('a Claude reply the app cannot read says it answered, and '
      'Details name the reply', (tester) async {
    await agentOpenFails(
      tester,
      PaseoFailure(
        PaseoFailureKind.invalidResponse,
        reason: 'fetch_agent_timeline_response',
      ),
      " on this phone answered, but this app couldn't read the reply. Try "
      'again, or report a problem.',
    );
  });

  testWidgets('a Claude conversation the helper does not have says so', (
    tester,
  ) async {
    await agentOpenFails(
      tester,
      PaseoFailure(
        PaseoFailureKind.notFound,
        reason: 'fetch_agent_request: no such agent',
      ),
      " on this phone doesn't have this conversation. Go back and open it "
      'from your conversations.',
    );
  });

  testWidgets('an uncertain send stays in review until an explicit resend', (
    tester,
  ) async {
    final api = _Api()
      ..busy = {}
      ..sendError = ApiException(
        'Provider is overloaded. Please retry.',
        statusCode: 503,
      )
      ..messagesHandler = (_) async => _turn();
    final controller = await _chat(tester, api);
    final field = find.byKey(const Key('chat-composer-field'));

    await tester.enterText(field, 'Run the full test suite');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await _frames(tester);

    expect(find.textContaining('Not confirmed yet'), findsOneWidget);
    expect(find.text("Your message wasn't sent"), findsNothing);
    expect(find.byType(SnackBar), findsNothing);
    final composer = tester.widget<EditableText>(
      find.descendant(of: field, matching: find.byType(EditableText)),
    );
    expect(composer.controller.text, isEmpty);
    await tester.pump(const Duration(seconds: 10));
    await controller.flushOfflineQueue();
    await _frames(tester);
    expect(find.textContaining('Not confirmed yet'), findsOneWidget);
    expect(api.sendAttempts, 1);

    await tester.tap(find.byKey(const ValueKey('queued-bubble-resend')));
    await _frames(tester);
    expect(find.text('Send this draft again?'), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-confirm-cancel')));
    await _frames(tester);
    expect(find.textContaining('Not confirmed yet'), findsOneWidget);
    expect(api.sendAttempts, 1);

    api.sendError = null;
    await tester.tap(find.byKey(const ValueKey('queued-bubble-resend')));
    await _frames(tester);
    await tester.tap(find.widgetWithText(KitButton, 'Send again').last);
    for (var i = 0; i < 50 && controller.queuedPromptCount != 0; i++) {
      await tester.pump();
    }
    await _frames(tester);
    expect(
      controller.queuedPromptCount,
      0,
      reason: controller
          .queuedPromptsFor(checkoutSessionID)
          .map((entry) => entry.error)
          .join('; '),
    );
    expect(find.textContaining('Not confirmed yet'), findsNothing);
    expect(api.sendAttempts, 2);
    expect(api.prompts, ['Run the full test suite']);
  });

  testWidgets('reconnecting: quiet for 15 s, then a recovery action, '
      'and no stale progress once connected', (tester) async {
    final controller = await _chat(
      tester,
      _Api()
        ..busy = {}
        ..messagesHandler = (_) async => _turn(),
    );
    controller.status = StreamStatus.reconnecting;
    controller.notifyListeners();
    await tester.pump();
    expect(_bar, findsNothing);
    expect(_connectionLine, findsNothing);
    expect(controller.isConnected, isFalse);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    await tester.pump(const Duration(seconds: 14));
    expect(_connectionLine, findsNothing);
    expect(find.text("Laptop isn't answering"), findsNothing);
    await tester.pump(const Duration(seconds: 2));
    expect(_connectionLine, findsOneWidget);
    expect(_bar, findsNothing);
    expect(find.text("Laptop isn't answering"), findsOneWidget);
    expect(find.text('Reconnect to Laptop'), findsOneWidget);

    controller.status = StreamStatus.connected;
    controller.notifyListeners();
    await _frames(tester);
    expect(_bar, findsNothing);
    expect(_connectionLine, findsNothing);
  });

  testWidgets('one status line: a lost connection outranks a prompt error, '
      'which comes back when the connection does', (tester) async {
    final controller = await _chat(
      tester,
      _Api()
        ..busy = {}
        ..messagesHandler = (_) async => [_turn().first],
    );
    controller.handleEventForTesting(
      captureEvent('session.error', {
        'sessionID': checkoutSessionID,
        'error': {
          'name': 'ProviderModelNotFoundError',
          'data': {'message': 'Model not found: openai/gpt-5.6.'},
        },
      }),
    );
    await _frames(tester, 2);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);

    controller
      ..status = StreamStatus.disconnected
      ..lastError = 'Cannot reach http://192.168.1.20:4096';
    controller.notifyListeners();
    await tester.pump();
    // The existing prompt error stays visible during a short link drop.
    expect(_connectionLine, findsNothing);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
    await tester.pump(const Duration(seconds: 16));
    expect(find.byType(KitStatusLine), findsOneWidget);
    expect(_connectionLine, findsOneWidget);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsNothing);

    controller.status = StreamStatus.connected;
    controller.notifyListeners();
    await tester.pump();
    expect(find.byType(KitStatusLine), findsOneWidget);
    expect(find.byKey(const ValueKey('prompt-error-banner')), findsOneWidget);
  });

  testWidgets(
    'a permission request: Allow once is the one primary, answered in place',
    (tester) async {
      await _chat(
        tester,
        _Api()..messagesHandler = (_) async => _turn(),
        setUp: (controller) => controller.permissions = {
          samplePermission().id: samplePermission(),
        },
      );
      final card = find.byKey(const ValueKey('kit-request-card'));
      final allow = find.byKey(const Key('permission-card-allow'));
      final reject = find.byKey(const Key('permission-card-reject'));
      final details = find.byKey(const Key('permission-card-review'));
      expect(card, findsOneWidget);
      // Allow once and Reject answer in place; Details opens the sheet.
      expect(find.descendant(of: card, matching: allow), findsOneWidget);
      expect(find.descendant(of: card, matching: reject), findsOneWidget);
      expect(find.descendant(of: card, matching: details), findsOneWidget);
      // Inside the card's 16 dp padding, side by side or stacked.
      for (final button in [allow, reject]) {
        expect(
          tester.getSize(button).width,
          lessThanOrEqualTo(tester.getSize(card).width - 32 + 1),
        );
      }
      // Allow once is the one primary on the card.
      final primary = find.descendant(
        of: card,
        matching: find.byWidgetPredicate(
          (widget) =>
              widget is KitButton && widget.role == KitButtonRole.primary,
        ),
      );
      expect(primary, findsOneWidget);
      expect(tester.widget(primary).key, const Key('permission-card-allow'));
    },
  );

  testWidgets('landscape: the connection line and the request card each keep '
      'their own room', (tester) async {
    final controller = await _chat(
      tester,
      _Api()
        ..busy = {}
        ..messagesHandler = (_) async => _turn(),
      agentBackend: true,
    );
    // A phone on its side.
    tester.view.physicalSize = const Size(915, 412);
    controller.handleEventForTesting(
      EventEnvelope(
        type: 'permission.asked',
        properties: {
          'id': 'request-1',
          'sessionID': checkoutSessionID,
          'permission': 'edit',
          'patterns': ['/root/projects/tip-calculator/index.html'],
          'metadata': <String, Object?>{},
          'always': <String>[],
        },
      ),
    );
    controller.status = StreamStatus.reconnecting;
    controller.notifyListeners();
    await tester.pump();
    await tester.pump(const Duration(seconds: 16));
    await _frames(tester);
    tester.takeException();

    expect(_connectionLine, findsOneWidget);
    final line = tester.getRect(_connectionLine);
    final allow = find.byKey(const Key('permission-card-allow'));
    expect(allow, findsOneWidget);
    // The room the request card is shown in (it scrolls inside it).
    final room = tester.getRect(
      find.ancestor(of: allow, matching: find.byType(Scrollable)).first,
    );
    // The line sits above that room, never over it...
    expect(line.bottom, lessThanOrEqualTo(room.top));
    expect(room.bottom, lessThanOrEqualTo(412));
    // ...is one short row instead of a block that eats the window, and
    // leaves the request enough room to be read and answered.
    expect(line.height, lessThan(70));
    expect(room.height, greaterThanOrEqualTo(72));
  });
}

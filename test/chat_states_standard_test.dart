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

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

class _Api extends CaptureApi {
  Object? sendError;

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
    if (sendError case final error?) throw error;
    prompts.add(text);
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

  testWidgets('a message that was not sent stays on the status line, with its '
      'text back in the box, until dismissed or sent again', (tester) async {
    final api = _Api()
      ..busy = {}
      ..sendError = ApiException(
        'Provider is overloaded. Please retry.',
        statusCode: 503,
      )
      ..messagesHandler = (_) async => _turn();
    await _chat(tester, api);
    final field = find.byKey(const Key('chat-composer-field'));

    await tester.enterText(field, 'Run the full test suite');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await _frames(tester);

    expect(find.text("Your message wasn't sent"), findsOneWidget);
    expect(find.byType(SnackBar), findsNothing);
    final composer = tester.widget<EditableText>(
      find.descendant(of: field, matching: find.byType(EditableText)),
    );
    expect(composer.controller.text, contains('suite'));
    // It does not leave on its own while the person reads it.
    await tester.pump(const Duration(seconds: 10));
    expect(find.text("Your message wasn't sent"), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('kit-status-dismiss')));
    // The line folds away (design standard §10), then it is gone.
    await _frames(tester, 4);
    expect(find.text("Your message wasn't sent"), findsNothing);

    // Sent again, and this time it goes: nothing is left on the line.
    await tester.tap(find.byTooltip('Send'));
    await _frames(tester);
    expect(find.text("Your message wasn't sent"), findsOneWidget);
    api.sendError = null;
    await tester.enterText(field, 'Run the full test suite');
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await _frames(tester);
    expect(find.text("Your message wasn't sent"), findsNothing);
    expect(api.prompts, ['Run the full test suite']);
  });

  testWidgets('reconnecting: progress at once, "isn\'t answering" after 8 s, '
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
    expect(_bar, findsOneWidget);
    expect(_connectionLine, findsOneWidget);
    expect(find.text('Reconnecting to Laptop…'), findsOneWidget);
    expect(find.text("Laptop isn't answering"), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);

    await tester.pump(const Duration(seconds: 7));
    expect(find.text('Reconnecting to Laptop…'), findsOneWidget);
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
}

// Golden renders of the chat's states and banners on the design kit
// (docs/design/design-standard.md §8, §9 step 5): the real ChatScreen at
// 412x915, dark and light, with the app's real fonts and no server.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/chat_states_golden_test.dart
// and look at every changed image before committing it.
//
// ignore_for_file: invalid_use_of_protected_member
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/first_run.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/widgets/app_connection_status.dart';
import 'package:opencode_mobile/ui/widgets/connection_status_banner.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

/// One page of history and a send that can fail.
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
  }
}

/// A finished turn at fixed offsets from now, so nothing on screen depends
/// on the wall clock (the transcript shows no times by default).
List<MessageWithParts> _turn({bool reply = true}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo(
        'msg_user',
        'user',
        created: now - 95 * 1000,
        completed: now - 95 * 1000,
      ),
      parts: [
        Part(
          id: 'part_user',
          messageID: 'msg_user',
          type: 'text',
          text: userPrompt,
        ),
      ],
    ),
    if (reply)
      MessageWithParts(
        info: messageInfo(
          'msg_assistant',
          'assistant',
          created: now - 80 * 1000,
          completed: now - 4 * 1000,
        ),
        parts: [textPart('part_intro', answerIntro)],
      ),
  ];
}

Future<void> _frames(WidgetTester tester, [int count = 8]) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required _Api api,
  String sessionID = checkoutSessionID,
  Map<String, Object> prefs = const {},
  void Function(CaptureController controller)? setUp,
  Future<void> Function(CaptureController controller)? before,
  Size size = const Size(412, 915),
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues(prefs);
  final controller = await captureController(
    prefs: await SharedPreferences.getInstance(),
    api: api,
  );
  setUp?.call(controller);
  final boundary = GlobalKey();
  final navigatorKey = GlobalKey<NavigatorState>();
  try {
    // Since 3d64653c (one controller-owned connection status) the chat no
    // longer draws its own connection line: the app's status slot above the
    // navigator does, so the harness hosts it the way main.dart does.
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
              child: ChatScreen(sessionID: sessionID),
            ),
          ),
        ),
        boundaryKey: boundary,
        navigatorKey: navigatorKey,
        controller: controller,
        light: light,
      ),
    );
    await _frames(tester);
    await before?.call(controller);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('chat · empty · $mode', (tester) async {
      await _golden(
        tester,
        'chat_empty',
        light: light,
        sessionID: darkModeSessionID,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async => [],
        // The caret rests after its blinks, so the frame is the same every
        // run.
        before: (_) => tester.pump(const Duration(seconds: 10)),
      );
    });

    testWidgets('chat · loading · $mode', (tester) async {
      final hold = Completer<List<MessageWithParts>>();
      addTearDown(() => hold.complete(const []));
      await _golden(
        tester,
        'chat_loading',
        light: light,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) => hold.future,
      );
    });

    testWidgets('chat · could not load · $mode', (tester) async {
      await _golden(
        tester,
        'chat_load_error',
        light: light,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async => throw ApiException(
            'Cannot reach http://192.168.1.20:4096: Connection refused',
          ),
      );
    });

    testWidgets('chat · send error · $mode', (tester) async {
      await _golden(
        tester,
        'chat_send_error',
        light: light,
        api: _Api()
          ..busy = {}
          ..sendError = ApiException(
            'Provider is overloaded. Please retry.',
            statusCode: 503,
          )
          ..messagesHandler = (_) async => _turn(),
        before: (_) async {
          await tester.enterText(
            find.byKey(const Key('chat-composer-field')),
            'Run the full test suite',
          );
          await tester.pump();
          await tester.tap(find.byTooltip('Send'));
          await _frames(tester);
          expect(find.text("Your message wasn't sent"), findsOneWidget);
        },
      );
    });

    testWidgets('chat · model error in a reply · $mode', (tester) async {
      await _golden(
        tester,
        'chat_model_error',
        light: light,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async {
            final now = DateTime.now().millisecondsSinceEpoch;
            return [
              _turn(reply: false).single,
              MessageWithParts(
                info: MessageInfo(
                  id: 'msg_assistant',
                  sessionID: checkoutSessionID,
                  role: 'assistant',
                  providerID: 'openai',
                  modelID: 'gpt-5.6',
                  time: MsgTime(
                    created: now - 80 * 1000,
                    completed: now - 79 * 1000,
                  ),
                  errorText:
                      'ProviderModelNotFoundError: Model not found: '
                      'openai/gpt-5.6. Did you mean: gpt-5.6-pro?\n'
                      '    at <anonymous> (/\$bunfs/root/chunk.js:439:1)',
                  errorKind: MessageErrorKind.modelNotFound,
                ),
                parts: const [],
              ),
            ];
          },
      );
    });

    // chat-1: a finished turn as the kit draws it: the prompt bubble, the
    // work folded under one line, the reply, and the one footer.
    for (final wide in [false, true]) {
      testWidgets('chat · transcript${wide ? ' · 1280x800' : ''} · $mode', (
        tester,
      ) async {
        await _golden(
          tester,
          wide ? 'chat_transcript_turn_1280x800' : 'chat_transcript_turn',
          light: light,
          size: wide ? const Size(1280, 800) : const Size(412, 915),
          api: _Api()
            ..busy = {}
            ..messagesHandler = (_) async {
              final now = DateTime.now().millisecondsSinceEpoch;
              Part tool(String id, String name, Map<String, Object> input) =>
                  Part(
                    id: id,
                    messageID: 'msg_assistant',
                    type: 'tool',
                    callID: 'call_$id',
                    toolName: name,
                    toolState: ToolState.fromJson({
                      'status': 'completed',
                      'input': input,
                      'output': 'ok',
                    }, toolName: name),
                  );
              return [
                _turn(reply: false).single,
                MessageWithParts(
                  info: messageInfo(
                    'msg_assistant',
                    'assistant',
                    created: now - 80 * 1000,
                    completed: now - 4 * 1000,
                  ),
                  parts: [
                    tool('t1', 'read', {'filePath': 'test/checkout_test.dart'}),
                    tool('t2', 'read', {'filePath': 'lib/checkout_bloc.dart'}),
                    tool('t3', 'read', {'filePath': 'lib/coupon.dart'}),
                    tool('t4', 'edit', {'filePath': 'test/checkout_test.dart'}),
                    textPart('part_intro', answerIntro),
                  ],
                ),
              ];
            },
        );
      });
    }

    testWidgets('chat · permission request · $mode', (tester) async {
      await _golden(
        tester,
        'chat_permission',
        light: light,
        api: _Api()..messagesHandler = (_) async => _turn(),
        setUp: (controller) => controller.permissions = {
          samplePermission().id: samplePermission(),
        },
      );
    });

    testWidgets('chat · permission sheet · $mode', (tester) async {
      await _golden(
        tester,
        'chat_permission_sheet',
        light: light,
        api: _Api()..messagesHandler = (_) async => _turn(),
        setUp: (controller) => controller.permissions = {
          samplePermission().id: samplePermission(),
        },
        before: (_) async {
          await tester.tap(find.byKey(const Key('permission-card-review')));
          await _frames(tester);
        },
      );
    });

    testWidgets('chat · disconnected · $mode', (tester) async {
      await _golden(
        tester,
        'chat_disconnected',
        light: light,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async => _turn(),
        before: (controller) async {
          controller
            ..status = StreamStatus.disconnected
            ..lastError =
                'Cannot reach http://192.168.1.20:4096: Connection refused';
          controller.notifyListeners();
          // This golden depicts a sustained outage, after quiet recovery.
          await tester.pump(const Duration(seconds: 16));
          await _frames(tester, 4);
          expect(find.text("Laptop isn't answering"), findsOneWidget);
        },
      );
    });

    // chat-9: find sits over the composer, by the keyboard it is typed with.
    testWidgets('chat · find · $mode', (tester) async {
      await _golden(
        tester,
        'chat_find_open',
        light: light,
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async => _turn(),
        before: (_) async {
          await tester.tap(
            find.byKey(const ValueKey('session-actions-button')),
          );
          await _frames(tester);
          await tester.tap(find.byKey(const ValueKey('session-menu-find')));
          await _frames(tester);
          await tester.enterText(
            find.byKey(const ValueKey('transcript-find-input')),
            'total',
          );
          await _frames(tester);
          expect(
            find.byKey(const ValueKey('transcript-find-bar')),
            findsOneWidget,
          );
        },
      );
    });

    testWidgets('chat · notify me · $mode', (tester) async {
      await _golden(
        tester,
        'chat_notify',
        light: light,
        prefs: {FirstRun.stateKey: 'done', FirstRun.notifyAskKey: 'pending'},
        api: _Api()
          ..busy = {}
          ..messagesHandler = (_) async => _turn(),
      );
    });
  }
}

// Regenerate deliberately:
//   flutter test --update-goldens test/chat_cold_open_test.dart
// and look at every changed image before committing it.
//
// A chat opened cold: the saved excerpt is shown at normal contrast while
// the live history loads, and the composer's chips never overlap.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_inventory_cache.dart';
import 'package:opencode_mobile/state/session_tail_cache.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart';

const _reply = 'The checkout test is fixed.';

/// History held until the test answers it, counting every read.
class _Api extends CaptureApi {
  _Api() {
    busy = {};
  }

  int reads = 0;
  Completer<List<MessageWithParts>> hold = Completer();
  final sendHold = Completer<void>();

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
    await sendHold.future;
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    reads += 1;
    return ServerPage(items: cursor == null ? await hold.future : const []);
  }
}

List<MessageWithParts> _turn() {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now - 9000),
      parts: [textPart('part_user', userPrompt)],
    ),
    MessageWithParts(
      info: messageInfo(
        'msg_assistant',
        'assistant',
        created: now - 8000,
        completed: now - 1000,
      ),
      parts: [textPart('part_reply', _reply)],
    ),
  ];
}

/// Saves the excerpt the last successful open of [session] left behind.
Future<void> _saveExcerpt(
  SharedPreferences prefs, {
  String session = checkoutSessionID,
}) => SessionTailCache(prefs).save(
  'laptop',
  SessionInventoryCache.scopeFor(
    ServerProfile(
      id: 'laptop',
      name: 'Laptop',
      baseUrl: 'http://192.168.1.20:4096',
    ),
    projectDirectory,
    null,
  ),
  session,
  [
    for (final message in _turn())
      MessageWithParts(
        info: MessageInfo(
          id: message.info.id,
          sessionID: session,
          role: message.info.role,
        ),
        parts: message.parts,
      ),
  ],
  isCurrent: () => true,
);

Future<CaptureController> _open(
  WidgetTester tester,
  _Api api, {
  bool saved = true,
  bool prefetch = false,
  GlobalKey? boundaryKey,
}) async {
  final boundary = boundaryKey ?? GlobalKey();
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues({});
  final prefs = await SharedPreferences.getInstance();
  if (saved) await _saveExcerpt(prefs);
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(() async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    controller.dispose();
  });
  // The tap that opens a chat warms its history (Work, Inbox, Sessions).
  if (prefetch) unawaited(controller.prefetchSessionTail(checkoutSessionID));
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: boundary,
      controller: controller,
    ),
  );
  return controller;
}

void main() {
  setUpAll(loadCaptureFonts);

  testWidgets('a refreshing saved excerpt reads at full contrast', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final key = GlobalKey();
    await _open(tester, _Api(), boundaryKey: key);
    await tester.pump(const Duration(milliseconds: 500));

    final roles = KitTokens.of(
      tester.element(find.byKey(const ValueKey('chat-opening-excerpt'))),
    ).roles;
    // The saved words are text1, as the live transcript's are.
    for (final words in [_reply, userPrompt]) {
      final rich = tester.widget<RichText>(
        find.descendant(of: find.text(words), matching: find.byType(RichText)),
      );
      expect(rich.text.style?.color, roles.text1, reason: words);
    }
    // Nothing between the page and the words fades, tints or veils them
    // (the page's own settled transition is fully opaque); only the small
    // status line says it is refreshing.
    final above = find.ancestor(
      of: find.text(_reply),
      matching: find.byWidgetPredicate(
        (w) =>
            (w is Opacity && w.opacity < 1) ||
            w is AnimatedOpacity ||
            (w is FadeTransition && w.opacity.value < 1) ||
            w is ColorFiltered ||
            w is BackdropFilter ||
            w is ModalBarrier,
      ),
    );
    expect(above, findsNothing);
    expect(find.textContaining('Refreshing'), findsOneWidget);

    await expectLater(
      find.byKey(key),
      matchesGoldenFile('goldens/chat_cold_open_refreshing_dark.png'),
    );
  });
}

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
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/phrase_chips.dart';
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

  // Jitter J9 (2026-10-28): the saved excerpt had no phrase row, so the
  // live history brought one and the whole conversation rose by a row.
  testWidgets('the phrase row the history brings is there from the excerpt', (
    tester,
  ) async {
    final api = _Api();
    final controller = await _open(tester, api);
    final phrases = PhraseChipStore.forProfile(
      controller.store.prefs,
      'laptop',
    );
    await phrases.record('Run the tests', PhraseMoment.idle);
    await phrases.record('Run the tests', PhraseMoment.idle);
    await tester.pump(const Duration(milliseconds: 500));
    expect(find.byKey(const ValueKey('chat-opening-excerpt')), findsOneWidget);
    final chip = find.byKey(const ValueKey('phrase-chip-Run the tests'));
    expect(chip, findsOneWidget);
    final before = tester.getTopLeft(chip).dy;
    api.hold.complete(_turn());
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    expect(find.byKey(const ValueKey('chat-opening-excerpt')), findsNothing);
    expect(tester.getTopLeft(chip).dy, before);
  });

  testWidgets('a refreshing saved excerpt reads at full contrast', (
    tester,
  ) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
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
    debugDefaultTargetPlatformOverride = null;
  });

  // Owner phone, 2026-10-29: the excerpt was saved only when the opening read
  // finished, so a cold open showed a chat as it was hours before, without
  // what arrived live afterwards.
  testWidgets('leaving the chat saves what live events added since the read', (
    tester,
  ) async {
    final api = _Api();
    final controller = await _open(tester, api, saved: false);
    api.hold.complete(_turn());
    await tester.pump();
    await tester.pump(const Duration(seconds: 1));
    final now = DateTime.now().millisecondsSinceEpoch;
    controller
      ..handleEventForTesting(
        captureEvent('message.updated', {
          'info': {
            'id': 'msg_live',
            'sessionID': checkoutSessionID,
            'role': 'assistant',
            'time': {'created': now, 'completed': now},
          },
        }),
      )
      ..handleEventForTesting(
        captureEvent('message.part.updated', {
          'sessionID': checkoutSessionID,
          'part': partJson(
            id: 'part_live',
            messageID: 'msg_live',
            type: 'text',
            text: 'Arrived after the read.',
          ),
        }),
      );
    await tester.pump(const Duration(seconds: 1));
    final owner = controller.profile!;
    final scope = SessionInventoryCache.scopeFor(owner, projectDirectory, null);
    final cache = SessionTailCache(controller.store.prefs);
    // Nothing yet: the read saved the history before the live message.
    final before = cache.read(owner.id, scope, checkoutSessionID);
    expect(before?.messages.map((m) => m.id), isNot(contains('msg_live')));

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    await tester.runAsync(() => Future<void>.delayed(Duration.zero));
    final after = cache.read(owner.id, scope, checkoutSessionID)!;
    expect(after.messages.last.id, 'msg_live');
    expect(after.messages.last.text, 'Arrived after the read.');
  });
}

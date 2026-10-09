import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitBidi, KitImage;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

/// A reply that holds `![Image](/path)` shows each picture as a tile, never
/// as `!` and a link: a file the server confirms reads through the app's own
/// file transport into a thumbnail that opens the file viewer; one it does
/// not list stays a named chip.

/// A 1×1 PNG.
const _png =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

class _Api extends CaptureApi {
  final List<String> read = [];

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);

  /// /tmp/shots holds home.png only.
  @override
  Future<List<FileNode>> listFiles([String path = '']) async =>
      path == '/tmp/shots'
      ? [FileNode(name: 'home.png', path: '/tmp/shots/home.png', isDir: false)]
      : const [];

  @override
  Future<FileContent> fileContent(String path) async {
    read.add(path);
    return const FileContent(
      _png,
      type: 'binary',
      encoding: 'base64',
      mimeType: 'image/png',
    );
  }
}

List<MessageWithParts> _reply(String text) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo(
        'msg_user',
        'user',
        created: now - 5000,
        completed: now - 5000,
      ),
      parts: [
        Part(
          id: 'part_ask',
          messageID: 'msg_user',
          type: 'text',
          text: 'Show me the screens',
        ),
      ],
    ),
    MessageWithParts(
      info: messageInfo(
        'msg_assistant',
        'assistant',
        created: now - 4000,
        completed: now - 1000,
      ),
      parts: [
        Part(
          id: 'part_reply',
          messageID: 'msg_assistant',
          type: 'text',
          text: text,
        ),
      ],
    ),
  ];
}

Future<_Api> _pump(WidgetTester tester, String text) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  final api = _Api()
    ..busy = {}
    ..messagesHandler = (_) async => _reply(text);
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  await _settle(tester);
  return api;
}

Future<void> _settle(WidgetTester tester) async {
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  testWidgets('a reply with pictures shows tiles, a thumbnail for a file', (
    tester,
  ) async {
    final api = await _pump(
      tester,
      'Here are the screens:\n'
      '![Image](/tmp/shots/home.png)\n'
      '![Image](/tmp/shots/settings.png)\n'
      '![Image](https://example.com/chat.png)',
    );
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    await _settle(tester);

    // Never syntax, never a link called Image with a bang before it.
    for (final fragment in ['![', '](', '!Image']) {
      expect(
        find.textContaining(fragment, findRichText: true),
        findsNothing,
        reason: 'raw "$fragment" shown',
      );
    }

    // home.png is listed by the server: its thumbnail, read through the app.
    final home = find.descendant(
      of: find.byKey(const ValueKey('kit-md-image-0')),
      matching: find.byType(KitImage),
    );
    expect(home, findsOneWidget);
    expect(api.read, ['/tmp/shots/home.png']);
    // settings.png is not listed: a chip with its name, and the web picture
    // a chip with its host. Neither is fetched.
    expect(find.text('Image · ${KitBidi.ltr('settings.png')}'), findsOneWidget);
    expect(find.text('Image · ${KitBidi.ltr('example.com')}'), findsOneWidget);
    expect(find.byType(KitImage), findsOneWidget);

    // A tap on the thumbnail opens the file viewer.
    await tester.tap(home);
    await _settle(tester);
    expect(find.byKey(const ValueKey('file-preview-sheet')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

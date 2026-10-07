import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitImage;
import 'package:opencode_mobile/ui/screens/chat_screen.dart';

import '../tool/capture/fixtures.dart';
import 'support/setup_capture_preferences.dart';

/// FC4: a photo sent with a prompt shows as a thumbnail under the prompt,
/// whichever form the server hands it back in, and opens the full view.

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

List<MessageWithParts> _prompt(List<Part> files) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return [
    MessageWithParts(
      info: messageInfo('msg_user', 'user', created: now, completed: now),
      parts: [
        Part(
          id: 'part_text',
          messageID: 'msg_user',
          type: 'text',
          text: 'What is wrong with this screen?',
        ),
        ...files,
      ],
    ),
  ];
}

Part _file(String id, {required String? mime, required String url}) => Part(
  id: id,
  messageID: 'msg_user',
  type: 'file',
  mime: mime,
  filename: '$id.png',
  url: url,
);

Future<_Api> _pump(WidgetTester tester, List<Part> files) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  final api = _Api()
    ..busy = {}
    ..messagesHandler = (_) async => _prompt(files);
  final controller = await captureController(prefs: prefs, api: api);
  addTearDown(controller.dispose);
  await tester.pumpWidget(
    captureApp(
      home: const ChatScreen(sessionID: checkoutSessionID),
      boundaryKey: GlobalKey(),
      controller: controller,
    ),
  );
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
  return api;
}

Future<void> _settle(WidgetTester tester) async {
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

void main() {
  testWidgets('a photo sent inline shows as a thumbnail that opens', (
    tester,
  ) async {
    await _pump(tester, [
      _file('photo', mime: 'image/png', url: 'data:image/png;base64,$_png'),
    ]);
    final thumb = find.byKey(const ValueKey('sent-photo-photo'));
    expect(thumb, findsOneWidget);
    expect(tester.widget(thumb), isA<KitImage>());
    // A thumbnail, not a file name.
    expect(find.text('photo.png'), findsNothing);

    await tester.tap(find.byKey(const ValueKey('sent-photo-open-photo')));
    await _settle(tester);
    expect(find.byKey(const ValueKey('file-preview-sheet')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a re-encoded photo with no type of its own still shows', (
    tester,
  ) async {
    // OpenCode 2 stores its own re-encoded copy; the type may only be in
    // the data URI.
    await _pump(tester, [
      _file('webp', mime: null, url: 'data:image/png;base64,$_png'),
    ]);
    expect(find.byKey(const ValueKey('sent-photo-webp')), findsOneWidget);
    expect(find.text('webp.png'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('a photo the server keeps as a file is read through the app', (
    tester,
  ) async {
    final api = await _pump(tester, [
      _file('kept', mime: 'image/png', url: 'file:///srv/uploads/kept.png'),
    ]);
    expect(find.byKey(const ValueKey('sent-photo-kept')), findsOneWidget);
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 50)),
    );
    await _settle(tester);
    expect(api.read, contains('/srv/uploads/kept.png'));

    await tester.tap(find.byKey(const ValueKey('sent-photo-open-kept')));
    await _settle(tester);
    expect(find.byKey(const ValueKey('file-preview-sheet')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('other files and web pictures stay named chips', (tester) async {
    await _pump(tester, [
      Part(
        id: 'notes',
        messageID: 'msg_user',
        type: 'file',
        mime: 'application/pdf',
        filename: 'notes.pdf',
        url: 'data:application/pdf;base64,JVBERi0=',
      ),
      _file('web', mime: 'image/png', url: 'https://example.com/web.png'),
    ]);
    expect(find.text('notes.pdf'), findsOneWidget);
    expect(find.text('web.png'), findsOneWidget);
    expect(find.byKey(const ValueKey('sent-photo-web')), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}

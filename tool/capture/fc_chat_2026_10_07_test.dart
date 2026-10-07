// Before/after captures for the chat backlog FC (2026-10-07): the real
// ChatScreen at 412x915 dp, dark theme, real fonts, no server calls.
//
//   flutter test --concurrency=1 --dart-define=FC_CAPTURE=before \
//     tool/capture/fc_chat_2026_10_07_test.dart   # on the old code
//   flutter test --concurrency=1 tool/capture/fc_chat_2026_10_07_test.dart
//
// Output: docs/qa/<item>-2026-10-07/<before|after>-<state>.png
//
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _prefix = String.fromEnvironment('FC_CAPTURE', defaultValue: 'after');
const _only = String.fromEnvironment('FC_ONLY');
const _size = Size(412, 915);

class _Api extends CaptureApi {
  _Api({this.caps});

  final ServerCapabilities? caps;

  @override
  ServerCapabilities get capabilities => caps ?? super.capabilities;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

Future<void> _settle(WidgetTester tester, [int frames = 8]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _shot(
  WidgetTester tester,
  GlobalKey key,
  String item,
  String state,
) async {
  expect(tester.takeException(), isNull);
  await writePng(
    'docs/qa/$item-2026-10-07/$_prefix-$state.png',
    await capturePng(tester, key, pixelRatio: 1),
  );
}

Future<(CaptureController, GlobalKey)> _chat(
  WidgetTester tester,
  _Api api, {
  String sessionID = checkoutSessionID,
  void Function(CaptureController controller)? before,
}) async {
  tester.view.physicalSize = _size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  // An empty in-memory store through the public platform interface
  // (setMockInitialValues is test-only API and this is tool/).
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  final controller = await captureController(prefs: prefs, api: api);
  before?.call(controller);
  addTearDown(controller.dispose);
  final key = GlobalKey();
  await tester.pumpWidget(
    captureApp(
      home: ChatScreen(sessionID: sessionID),
      boundaryKey: key,
      controller: controller,
    ),
  );
  await _settle(tester);
  return (controller, key);
}

Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 1));
}

bool _wanted(String item) => _only.isEmpty || _only == item;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  testWidgets('FC5 model chip while the models load', (tester) async {
    if (!_wanted('FC5')) return;
    final api = _Api()
      ..busy = {}
      ..messagesHandler = (_) async => [];
    final (_, key) = await _chat(
      tester,
      api,
      sessionID: darkModeSessionID,
      before: (controller) => controller
        ..catalog = null
        ..selectedModel = ModelRef(
          providerID: 'anthropic',
          modelID: 'claude-sonnet-5',
        ),
    );
    await _shot(tester, key, 'FC5', 'model-chip-loading');
    await _unmount(tester);
  });

  testWidgets('FC1 approval chip on runtimes that ask and that never ask', (
    tester,
  ) async {
    if (!_wanted('FC1')) return;
    for (final (name, caps) in [
      ('asks', null),
      ('never-asks', const ServerCapabilities(permissionRequests: false)),
    ]) {
      final api = _Api(caps: caps)
        ..busy = {}
        ..messagesHandler = (_) async => [];
      final (_, key) = await _chat(tester, api, sessionID: darkModeSessionID);
      await _shot(tester, key, 'FC1', 'composer-$name');
      await _unmount(tester);
    }
  });

  testWidgets('FC4 photos sent with a prompt', (tester) async {
    if (!_wanted('FC4')) return;
    String jpeg(String path) =>
        'data:image/jpeg;base64,${base64Encode(File(path).readAsBytesSync())}';
    final now = DateTime.now().millisecondsSinceEpoch;
    Part file(String id, String name, String mime, String url) => Part(
      id: id,
      messageID: 'msg_user',
      type: 'file',
      mime: mime,
      filename: name,
      url: url,
    );
    final transcript = [
      MessageWithParts(
        info: messageInfo('msg_user', 'user', created: now, completed: now),
        parts: [
          Part(
            id: 'part_text',
            messageID: 'msg_user',
            type: 'text',
            text: 'Why does the chip look different on these two screens?',
          ),
          file(
            'p1',
            'IMG_2041.jpg',
            'image/jpeg',
            jpeg('docs/qa/FC4-2026-10-07/sample-photo-1.jpg'),
          ),
          file(
            'p2',
            'IMG_2042.jpg',
            'image/jpeg',
            jpeg('docs/qa/FC4-2026-10-07/sample-photo-2.jpg'),
          ),
          file(
            'd1',
            'notes.pdf',
            'application/pdf',
            'data:application/pdf;base64,JVBERi0=',
          ),
        ],
      ),
    ];
    final api = _Api()
      ..busy = {}
      ..messagesHandler = (_) async => transcript;
    final (_, key) = await _chat(tester, api);
    for (var i = 0; i < 4; i++) {
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await _settle(tester, 2);
    }
    await _shot(tester, key, 'FC4', 'sent-photos');
    await _unmount(tester);
  });
}

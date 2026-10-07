// Before/after captures for the chat backlog FC (2026-10-07): the real
// ChatScreen at 412x915 dp, dark theme, real fonts, no server calls.
//
//   flutter test --concurrency=1 --dart-define=FC_CAPTURE=before \
//     tool/capture/fc_chat_2026_10_07_test.dart   # on the old code
//   flutter test --concurrency=1 tool/capture/fc_chat_2026_10_07_test.dart
//
// Output: docs/qa/<item>-2026-10-07/<before|after>-<state>.png
//
// ignore_for_file: invalid_use_of_visible_for_testing_member, invalid_use_of_protected_member
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fixtures.dart';

const _prefix = String.fromEnvironment('FC_CAPTURE', defaultValue: 'after');
const _only = String.fromEnvironment('FC_ONLY');
const _size = Size(412, 915);

class _Api extends CaptureApi {
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
  SharedPreferences.setMockInitialValues(const {});
  final controller = await captureController(
    prefs: await SharedPreferences.getInstance(),
    api: api,
  );
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
}

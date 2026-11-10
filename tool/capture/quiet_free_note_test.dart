// Before/after captures of the chat's free-model note (quiet-note slice,
// 2026-10-20): the real ChatScreen at 412x915 dp, dark, real fonts.
//
//   flutter test --concurrency=1 --dart-define=NOTE_PHASE=before \
//     tool/capture/quiet_free_note_test.dart
//   flutter test --concurrency=1 tool/capture/quiet_free_note_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/quiet-free-note/<phase>-dark.png
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _phase = String.fromEnvironment('NOTE_PHASE', defaultValue: 'after');
const _dir = '/home/eslam/Storage2/launch-footage/quiet-free-note';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  testWidgets('free-model note $_phase', (tester) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    SharedPreferencesStorePlatform.instance =
        InMemorySharedPreferencesStore.empty();
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    final now = DateTime.now().millisecondsSinceEpoch;
    final api = CaptureApi()
      ..messagesHandler = (_) async => [
        MessageWithParts(
          info: messageInfo('msg_user', 'user', created: now - 20000),
          parts: [
            Part(
              id: 'part_prompt',
              messageID: 'msg_user',
              type: 'text',
              text: 'Build me a small landing page for the bakery.',
            ),
          ],
        ),
        MessageWithParts(
          info: messageInfo('msg_reply', 'assistant', created: now - 15000),
          parts: [
            Part(
              id: 'part_words',
              messageID: 'msg_reply',
              type: 'text',
              text:
                  'Here is a first version of the page with a hero, the '
                  'menu and opening hours.',
            ),
          ],
        ),
      ];
    final controller = CaptureController(
      SeededProfileStore(
        prefs: prefs,
        seeded: [
          ServerProfile(
            id: 'laptop',
            name: 'Laptop',
            baseUrl: 'http://192.168.1.20:4096',
          ),
        ],
      ),
    );
    controller
      ..api = api
      ..repository = CaptureRepository()
      ..status = StreamStatus.connected
      ..directory = projectDirectory
      ..providers = ProvidersResponse(providers: const [])
      ..catalog = CatalogSnapshot(
        providers: const [],
        models: [
          CatalogModel(
            id: 'big-pickle',
            providerID: 'opencode',
            name: 'big-pickle',
            enabled: true,
            status: 'active',
            contextLimit: 200000,
            outputLimit: 32000,
            reasoning: false,
            attachments: false,
            tools: true,
            variants: const [],
            cost: const ModelCost(inputPerMillion: 0, outputPerMillion: 0),
          ),
        ],
        agents: const [],
      )
      ..selectedModel = ModelRef(providerID: 'opencode', modelID: 'big-pickle')
      ..sessionsById = Map.of(api.sessionsById);
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: const ChatScreen(sessionID: checkoutSessionID),
        boundaryKey: key,
        controller: controller,
      ),
    );
    for (var i = 0; i < 10; i++) {
      await tester.pump(const Duration(milliseconds: 120));
    }
    expect(tester.takeException(), isNull);
    await writePng(
      '$_dir/$_phase-dark.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
  });
}

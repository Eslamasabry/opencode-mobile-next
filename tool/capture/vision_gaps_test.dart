// Captures of the vision gaps (2026-10-21): long-press Stop's "Stop after
// this step" and the changes screen (one sentence first, files folded with
// the first open). The real screens at 412x915 dp, 2x pixels, dark, real
// fonts, blurred shadows, no server. One full phone screen per image. The
// Preview, Inbox and Conversations renders come from vision_preview_test
// and vision_inbox_test (VISION_HOME=true).
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/vision_gaps_test.dart
//
// Output: /home/eslam/vision-assets/out/gaps/renders/<scene>.png
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/review_workspace.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/gaps/renders',
);

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

const _dir = '/root/projects/settings-demo';
const _sid = 'ses_tea';

bool _wanted(String scene) => _only.isEmpty || _only.split(',').contains(scene);

class _Api extends CaptureApi {
  _Api();

  // What OpenCode 2 advertises: it asks before acting and keeps "always"
  // grants; it steers a running turn.
  @override
  ServerCapabilities get capabilities => ServerCapabilities(
    permissionRequests: true,
    persistentPermissionGrants: true,
    promptSteering: true,
  );

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

MessageWithParts _prompt(int now, String text) => MessageWithParts(
  info: messageInfo('msg_user', 'user', created: now - 40000),
  parts: [
    Part(id: 'part_prompt', messageID: 'msg_user', type: 'text', text: text),
  ],
);

Future<void> _frames(WidgetTester tester, [int n = 12]) async {
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 200)),
  );
  for (var i = 0; i < n; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<SharedPreferences> _prefs() async {
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  return prefs;
}

/// The chat's controller on the app's own server on this phone.
CaptureController _phoneController(SharedPreferences prefs, _Api api) {
  final controller = CaptureController(
    SeededProfileStore(
      prefs: prefs,
      seeded: [
        ServerProfile(
          id: 'phone',
          name: 'On this phone',
          baseUrl: BuiltinLinux.serverUrl,
        ),
      ],
    ),
  );
  controller
    ..api = api
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected
    ..directory = _dir
    ..sessionsById = Map.of(api.sessionsById)
    ..busySessions = Set.of(api.busy);
  return controller;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
    KitMotion.loops = true;
  });

  void phone(WidgetTester tester) {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    debugDisableShadows = false;
    tester.view.physicalSize = const Size(412, 915) * 2;
    tester.view.devicePixelRatio = 2;
    tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
    addTearDown(tester.view.reset);
    KitLiveBackground.frozenSeconds = 21;
  }

  Future<void> done(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 1));
    debugDisableShadows = true;
    debugDefaultTargetPlatformOverride = null;
  }

  Session session(int now) => Session(
    id: _sid,
    title: 'Create and read hello.txt tea notes',
    directory: _dir,
    time: SessionTime(created: now - 60000, updated: now - 20000),
  );

  // Gap 3: a long-press on Stop while it works offers "Stop after this
  // step"; a tap still stops at once.
  if (_wanted('chat-stop-after-step-dark')) {
    testWidgets('chat-stop-after-step-dark', (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final now = DateTime.now().millisecondsSinceEpoch;
      final api = _Api()
        ..sessionsById = {_sid: session(now)}
        ..busy = {_sid}
        ..messagesHandler = (_) async => [
          _prompt(
            now,
            'Create a file hello.txt with two lines about tea, then read it '
            'back',
          ),
          MessageWithParts(
            info: messageInfo('msg_reply', 'assistant', created: now - 30000),
            parts: [
              Part(
                id: 'part_words',
                messageID: 'msg_reply',
                type: 'text',
                text: 'Writing the two lines now, then I will read it back.',
              ),
              Part(
                id: 'call_edit',
                messageID: 'msg_reply',
                callID: 'call_edit',
                type: 'tool',
                toolName: 'edit',
                toolState: ToolState.fromJson({
                  'status': 'running',
                  'input': {'filePath': '$_dir/hello.txt'},
                }, toolName: 'edit'),
              ),
            ],
          ),
        ];
      final controller = _phoneController(prefs, api);
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const ChatScreen(sessionID: _sid),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      await tester.longPress(find.byKey(const Key('chat-stop-button')));
      await _frames(tester, 6);
      expect(find.byKey(const Key('composer-stop-after-step')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/chat-stop-after-step-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await done(tester);
    });
  }

  // Gaps 5 and 6: the changes screen says one plain sentence first, the
  // counts under it, and lists the files as folded rows, the first open.
  if (_wanted('review-dark')) {
    testWidgets('review-dark', (tester) async {
      phone(tester);
      final prefs = await _prefs();
      final controller = _phoneController(prefs, _Api());
      addTearDown(controller.dispose);
      FileDiff file(String path, String patch, int added, int removed) =>
          FileDiff(
            file: path,
            patch: patch,
            additions: added,
            deletions: removed,
          );
      final diffs = [
        file(
          'src/game/combo.js',
          '@@ -1,6 +1,14 @@\n'
              ' import { sound } from "./sound.js";\n'
              '+\n'
              '+export function comboCounter(hits) {\n'
              '+  if (hits < 3) return 1;\n'
              '+  return Math.min(hits - 1, 8);\n'
              '+}\n'
              ' \n'
              ' export function score(bricks, combo) {\n'
              '-  return bricks * 10;\n'
              '+  return bricks * 10 * combo;\n'
              ' }\n'
              '+\n'
              '+export const COMBO_FLASH_MS = 180;',
          9,
          1,
        ),
        file(
          'src/game/shake.js',
          '@@ -0,0 +1,6 @@\n'
              '+export function shake(canvas, strength) {\n'
              '+  const dx = (Math.random() - 0.5) * strength;\n'
              '+  const dy = (Math.random() - 0.5) * strength;\n'
              '+  canvas.style.transform = `translate(\${dx}px, \${dy}px)`;\n'
              '+  setTimeout(() => (canvas.style.transform = ""), 90);\n'
              '+}',
          6,
          0,
        ),
        file(
          'README.md',
          '@@ -3 +3,2 @@\n'
              '-Break the bricks.\n'
              '+Break the bricks. Chain hits for a combo;\n'
              '+the screen shakes on every third.',
          2,
          1,
        ),
      ];
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: ReviewWorkspace(
            loadDiffs: () async => diffs,
            summary: 'Added a combo counter and a screen shake.',
            onUndoAll: () async {},
          ),
          boundaryKey: key,
          controller: controller,
        ),
      );
      await _frames(tester);
      expect(find.byKey(const Key('review-summary-sentence')), findsOneWidget);
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/review-dark.png',
        await capturePng(tester, key, pixelRatio: 2),
      );
      await done(tester);
    });
  }
}

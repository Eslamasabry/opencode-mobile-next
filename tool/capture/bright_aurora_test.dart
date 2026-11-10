// Captures for the bright aurora (2026-10-20): the live background as the
// app paints it, behind the real Home, a chat, New conversation and
// Settings, dark and light. The look is painted (still, at a fixed second),
// blurred shadows render as on a phone, real fonts, no server. Phone
// 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 --dart-define=AURORA_PHASE=before \
//     tool/capture/bright_aurora_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/bright-aurora/<phase>-<scene>.png
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/chats/new_chat_screen.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/agents_fakes.dart';
import '../../test/support/chats_fakes.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _phase = String.fromEnvironment('AURORA_PHASE', defaultValue: 'after');
const _second = int.fromEnvironment('AURORA_SECOND', defaultValue: 21);
const _dir = String.fromEnvironment(
  'AURORA_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/bright-aurora',
);

void _mockSecureStorage(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  addTearDown(
    () => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(
      secure,
      null,
    ),
  );
}

/// A phone frame: the status bar and the gesture area are real insets.
void _phone(WidgetTester tester) {
  tester.view.physicalSize = const Size(412, 915) * 2;
  tester.view.devicePixelRatio = 2;
  tester.view.padding = const FakeViewPadding(top: 64, bottom: 48);
  addTearDown(tester.view.reset);
}

Future<void> _settle(WidgetTester tester, {int frames = 8}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
  // The look loads its warm-up off the fake clock.
  await tester.runAsync(
    () => Future<void>.delayed(const Duration(milliseconds: 200)),
  );
  await tester.pump(const Duration(milliseconds: 120));
}

List<MessageWithParts> _turn(int now) => [
  MessageWithParts(
    info: messageInfo('msg_user', 'user', created: now - 60000),
    parts: [
      Part(
        id: 'part_prompt',
        messageID: 'msg_user',
        type: 'text',
        text: 'Tidy up the settings page',
      ),
    ],
  ),
  MessageWithParts(
    info: messageInfo(
      'msg_reply',
      'assistant',
      created: now - 50000,
      completed: now - 20000,
    ),
    parts: [
      Part(
        id: 'part_words',
        messageID: 'msg_reply',
        type: 'text',
        text:
            'On it: I grouped the options into five tabs and moved the '
            'rarely used ones under Advanced.',
      ),
    ],
  ),
];

Future<void> _shot(WidgetTester tester, GlobalKey key, String scene) async {
  expect(tester.takeException(), isNull);
  await writePng(
    '$_dir/$_phase-$scene.png',
    await capturePng(tester, key, pixelRatio: 2),
  );
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump();
  // Put back before the test ends (the binding checks them).
  debugDisableShadows = true;
  debugDefaultTargetPlatformOverride = null;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitLiveBackground.frozenSeconds = _second.toDouble();
    // Blurred shadows as a phone draws them (the test binding flattens
    // them); put back at the end of each shot.
    debugDisableShadows = false;
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
  });
  tearDown(() {
    KitLiveBackground.paintWithoutLoops = false;
    KitLiveBackground.frozenSeconds = null;
  });

  for (final light in [false, true]) {
    final tone = light ? 'light' : 'dark';
    testWidgets('home $tone', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester);
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: CaptureApi(),
      );
      addTearDown(controller.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          home: const HomeScreen(),
          boundaryKey: key,
          controller: controller,
          light: light,
        ),
      );
      await _settle(tester);
      await _shot(tester, key, 'home-$tone');
    });

    testWidgets('chat $tone', (tester) async {
      _mockSecureStorage(tester);
      _phone(tester);
      final api = CaptureApi()..busy = {};
      final controller = await captureController(
        prefs: await setupCapturePreferences(),
        api: api,
      );
      addTearDown(controller.dispose);
      final now = DateTime.now().millisecondsSinceEpoch;
      api.messagesHandler = (_) async => _turn(now);
      controller.busySessions = {};
      controller.catalog = sampleCatalog();
      final key = GlobalKey();
      final navigator = GlobalKey<NavigatorState>();
      chatScreenFor = (id) => ChatScreen(sessionID: id);
      await tester.pumpWidget(
        captureApp(
          home: const HomeScreen(),
          boundaryKey: key,
          controller: controller,
          navigatorKey: navigator,
          light: light,
        ),
      );
      await _settle(tester, frames: 2);
      unawaited(navigator.currentState!.pushNamed('/chat/$checkoutSessionID'));
      await _settle(tester, frames: 10);
      await _shot(tester, key, 'chat-$tone');
    });
  }

  testWidgets('settings dark', (tester) async {
    _mockSecureStorage(tester);
    _phone(tester);
    final controller = await captureController(
      prefs: await setupCapturePreferences(),
      api: CaptureApi(),
    );
    addTearDown(controller.dispose);
    final key = GlobalKey();
    await tester.pumpWidget(
      captureApp(
        home: SettingsScreen(controller: controller),
        boundaryKey: key,
        controller: controller,
      ),
    );
    await _settle(tester);
    await _shot(tester, key, 'settings-dark');
  });

  testWidgets('new conversation dark', (tester) async {
    _phone(tester);
    final host =
        FakeChatsHost(
            FakeChatFeedSource(
              projects: [project('neon-breaker', kind: 'node')],
              lastUsed: '/root/projects/neon-breaker',
            ),
          )
          ..phoneAgents =
              (FakePhoneAgentsSource(
                  rows: [agentRowFor('claude', FakeAgentStage.ready)],
                  selected: 'claude',
                )
                ..alsoReady = const [('codex', 'Codex'), ('gemini', 'Gemini')]
                ..models['claude'] = const [
                  AgentModelChoice(
                    id: 'claude-opus-5-5',
                    name: 'Opus 5.5',
                    isDefault: true,
                  ),
                ]);
    final key = GlobalKey();
    await tester.pumpWidget(
      RepaintBoundary(
        key: key,
        child: chatsApp(host, const SizedBox.expand(key: ValueKey('opener'))),
      ),
    );
    unawaited(
      showNewChat(tester.element(find.byKey(const ValueKey('opener')))),
    );
    await _settle(tester);
    await _shot(tester, key, 'new-conversation-dark');
  });
}

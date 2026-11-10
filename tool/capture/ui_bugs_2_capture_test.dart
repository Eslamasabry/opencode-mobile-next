// Progress renders for the owner's phone bugs of 2026-10-22 (ui-bugs-2):
// Settings › Appearance (top and the Style picker) in every style, and the
// chat after "You stopped this reply.", on Midnight with the blue accent in
// light, as the owner saw them. Not a golden: one full phone screen per
// image under OUT (default build/ui-bugs-2), named
// pass<PASS>-<scene>-<style>-light.png.
//
//   TZ=UTC flutter test -j 1 --dart-define=PASS=1 \
//     --dart-define=OUT=/home/eslam/vision-assets/out/ui-bugs-2/progress \
//     tool/capture/ui_bugs_2_capture_test.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import 'fixtures.dart';

const _pass = String.fromEnvironment('PASS', defaultValue: '0');
const _out = String.fromEnvironment('OUT', defaultValue: 'build/ui-bugs-2');
const _size = Size(412, 915);

ThemeData _theme() {
  final blue = appearanceAccentById('blue');
  return AppTheme.light(
    themePack(ThemePackId.midnight),
    blue?.light,
  ).copyWith(platform: TargetPlatform.android);
}

class _Api extends CaptureApi {
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : const []);
}

/// A turn the person stopped: the prompt, a reply that read three files
/// and began a long command, then the stop.
List<MessageWithParts> _stoppedTurn() {
  final now = DateTime.now().millisecondsSinceEpoch;
  final transcript = sampleTranscript();
  final reply = transcript.last;
  return [
    transcript.first,
    MessageWithParts(
      info: MessageInfo(
        id: reply.info.id,
        sessionID: checkoutSessionID,
        role: 'assistant',
        providerID: 'anthropic',
        modelID: 'claude-sonnet-4',
        time: MsgTime(created: now - 80 * 1000, completed: now - 20 * 1000),
        errorText: 'MessageAbortedError: The operation was aborted.',
        errorKind: MessageErrorKind.aborted,
      ),
      parts: [
        textPart('part_intro', answerIntro),
        ...toolParts().take(3),
        Part(
          id: 'tool_bash',
          messageID: 'msg_assistant',
          type: 'tool',
          callID: 'tool_bash',
          toolName: 'bash',
          toolState: ToolState(
            status: 'error',
            input: {
              'command': checkoutCommand,
              'description':
                  'Run the checkout tests on a clean cache and retry when '
                  'the first run fails',
            },
            output: 'Tool execution aborted',
          ),
        ),
      ],
    ),
  ];
}

Future<void> _settle(WidgetTester tester, [int frames = 10]) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
}

Future<void> _shot(WidgetTester tester, GlobalKey key, String name) async {
  expect(tester.takeException(), isNull);
  await writePng(
    '$_out/pass$_pass-$name-light.png',
    await capturePng(tester, key, pixelRatio: 2),
  );
}

Future<GlobalKey> _mount(
  WidgetTester tester,
  KitSurfaceStyle style,
  Widget Function(CaptureController controller) home, {
  CaptureApi? api,
}) async {
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  tester.view.physicalSize = _size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  KitLiveBackground.paintWithoutLoops = true;
  KitLiveBackground.frozenSeconds = 8;
  KitMotion.loops = false;
  // An empty in-memory store per capture, through the public platform
  // interface (setMockInitialValues is test-only API and this is tool/).
  SharedPreferencesStorePlatform.instance =
      InMemorySharedPreferencesStore.empty();
  final prefs = await SharedPreferences.getInstance();
  await prefs.clear();
  final controller = await captureController(prefs: prefs, api: api);
  final effects = KitSurfaceLook.choose(KitEffects.defaults, style);
  controller.effects.value = effects;
  controller.themePack.value = ThemePackId.midnight;
  controller.appearanceAccent.value = 'blue';
  controller.appearance.value = AppAppearance.light;
  addTearDown(controller.dispose);
  final key = GlobalKey();
  await tester.pumpWidget(
    captureApp(
      home: home(controller),
      theme: _theme(),
      light: true,
      boundaryKey: key,
      controller: controller,
      aboveNavigator: (child) => ValueListenableBuilder<KitEffects>(
        valueListenable: controller.effects,
        builder: (context, effects, _) => KitEffectsScope(
          effects: effects,
          child: kitStyleTheme(child: child),
        ),
      ),
    ),
  );
  await _settle(tester);
  return key;
}

Future<void> _unmount(WidgetTester tester) async {
  await tester.pumpWidget(const SizedBox.shrink());
  await tester.pump(const Duration(seconds: 1));
  debugDefaultTargetPlatformOverride = null;
}

/// Scrolls the theme panel until [target]'s top sits just under the tab row.
Future<void> _scrollTo(WidgetTester tester, Key target) async {
  final list = find
      .descendant(
        of: find.byKey(const ValueKey('appearance-panel-theme')),
        matching: find.byType(Scrollable),
      )
      .first;
  await tester.scrollUntilVisible(find.byKey(target), 200, scrollable: list);
  await _settle(tester);
  final delta =
      tester.getRect(find.byKey(target)).top - tester.getRect(list).top - 12;
  // Jumped, not dragged: a short drag would land as a tap on a card.
  final position = tester.state<ScrollableState>(list).position;
  position.jumpTo(position.pixels + delta);
  await _settle(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final style in KitSurfaceStyle.values) {
    testWidgets('appearance ${style.name}', (tester) async {
      final key = await _mount(
        tester,
        style,
        (c) => AppearanceSettingsScreen(controller: c),
      );
      // A little scrolled, so the tab row has content under it.
      final list = find
          .descendant(
            of: find.byKey(const ValueKey('appearance-panel-theme')),
            matching: find.byType(Scrollable),
          )
          .first;
      final position = tester.state<ScrollableState>(list).position;
      position.jumpTo(position.pixels + 26);
      await _settle(tester);
      await _shot(tester, key, 'appearance-${style.name}');
      await _scrollTo(tester, const ValueKey('appearance-surfaces'));
      await _shot(tester, key, 'style-${style.name}');
      await _unmount(tester);
    });
  }

  for (final style in [
    KitSurfaceStyle.solid,
    KitSurfaceStyle.brutal,
    KitSurfaceStyle.paper,
  ]) {
    testWidgets('chat stopped ${style.name}', (tester) async {
      final api = _Api()
        ..busy = {}
        ..messagesHandler = (_) async => _stoppedTurn();
      final key = await _mount(
        tester,
        style,
        (_) => const ChatScreen(sessionID: checkoutSessionID),
        api: api,
      );
      await _shot(tester, key, 'chat-stopped-${style.name}');
      await _unmount(tester);
    });
  }
}

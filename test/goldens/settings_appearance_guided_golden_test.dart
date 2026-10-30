// Golden renders of the guided Settings › Appearance (owner approved
// 2026-10-23): the first visit's start view, Mode, Theme, Style and Look as
// a guided visit, and the returning one page (its top), in Solid; the start
// view and Style in Soft and Brutal. At 412x915, dark and light, with the app's real
// fonts. The scene is test/support/settings_scenes.dart's Appearance, built
// as main.dart builds the app's theme.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/settings_appearance_guided_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import '../support/settings_scenes.dart';

/// The chips each state taps, from the start view.
const _taps = <String, List<String>>{
  'home': [],
  'mode': ['appearance-group-colours'],
  'theme': ['appearance-group-colours', 'appearance-question-theme'],
  'style': ['appearance-group-style'],
  'look': ['appearance-group-background'],
  'returning': [],
};

Future<void> _shot(
  WidgetTester tester, {
  required KitSurfaceStyle style,
  required String state,
  required bool light,
}) async {
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  final done = await mountSettingsScene(
    tester,
    SettingsScene.appearance,
    light: light,
    boundary: boundary,
    followAppearance: true,
    configure: (controller) {
      controller.effects.value = KitSurfaceLook.choose(
        KitEffects.defaults,
        style,
      );
      if (state == 'returning') {
        controller.store.prefs.setString(
          'oc.appearanceGuide',
          '{"done":true,"lastIn":{"colours":"theme"},"last":"theme"}',
        );
      }
    },
  );
  try {
    for (final key in _taps[state]!) {
      await tester.tap(find.byKey(ValueKey(key)));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    }
    // Past the outline and the preview's play, to its rest.
    await tester.pump(const Duration(seconds: 6));
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile(
        'settings_appearance_guided_${style.name}_${state}_'
        '${light ? 'light' : 'dark'}.png',
      ),
    );
  } finally {
    await done();
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final state in _taps.keys) {
      testWidgets('solid · $state · $mode', (tester) async {
        await _shot(
          tester,
          style: KitSurfaceStyle.solid,
          state: state,
          light: light,
        );
      });
    }
    for (final style in [KitSurfaceStyle.soft, KitSurfaceStyle.brutal]) {
      for (final state in ['home', 'style']) {
        testWidgets('${style.name} · $state · $mode', (tester) async {
          await _shot(tester, style: style, state: state, light: light);
        });
      }
    }
  }
}

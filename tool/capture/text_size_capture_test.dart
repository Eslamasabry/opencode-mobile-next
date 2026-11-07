// Evidence for docs/qa/text-size-2026-10-14: real phone-size renders of the
// main screens at Default and Smallest, and the top bar over a strong live
// background. Run: flutter test tool/capture/text_size_capture_test.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

import '../../test/revamp/screen_phone_1_fixtures.dart';
import 'fixtures.dart';

const _out = 'docs/qa/text-size-2026-10-14';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  Widget sized(KitTextSize size, KitEffects effects, Widget child) =>
      KitEffectsScope(
        effects: effects,
        child: KitText.appScale(
          size: ValueNotifier(size),
          max: AppTheme.maxTextScale,
          child: child,
        ),
      );

  final screens = <String, Widget Function(KitTextSize, KitEffects)>{
    'chats': (s, e) => sized(s, e, const HomeScreen(initialTab: 0)),
    'settings': (s, e) => sized(
      s,
      e,
      Consumer(
        builder: (context, ref, _) =>
            SettingsScreen(controller: ref.watch(connProvider)),
      ),
    ),
    'appearance': (s, e) => sized(
      s,
      e,
      Consumer(
        builder: (context, ref, _) =>
            AppearanceSettingsScreen(controller: ref.watch(connProvider)),
      ),
    ),
    'this-phone': (s, e) => sized(s, e, const ThisPhoneScreen()),
  };

  for (final name in screens.keys) {
    for (final size in [KitTextSize.normal, KitTextSize.smallest]) {
      testWidgets('$name at ${size.name}', (tester) async {
        final key = GlobalKey();
        late ConnectionController controller;
        await pumpPhone(
          tester,
          home: screens[name]!(size, KitEffects.defaults),
          boundary: key,
          configure: (c) => controller = c,
        );
        for (var i = 0; i < 12; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
        await writePng(
          '$_out/$name-${size.name}.png',
          await capturePng(tester, key, pixelRatio: 2),
        );
        await unmountPhone(tester);
        controller.dispose();
      });
    }
  }

  testWidgets('top bar over a strong live background', (tester) async {
    final key = GlobalKey();
    await pumpPhone(
      tester,
      home: sized(
        KitTextSize.normal,
        const KitEffects(
          liveStyle: 'lightRays',
          liveIntensity: KitLiveIntensity.bold,
        ),
        Consumer(
          builder: (context, ref, _) =>
              AppearanceSettingsScreen(controller: ref.watch(connProvider)),
        ),
      ),
      boundary: key,
    );
    for (var i = 0; i < 12; i++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    await writePng(
      '$_out/top-bar-over-live-background.png',
      await capturePng(tester, key, pixelRatio: 2),
    );
    await unmountPhone(tester);
  });
}

// Captures of "Make it yours" (2026-10-22): the offer on the first visit to
// Appearance and its four steps over the real screen, dark and light. The
// real screen at 412x915 dp, 2x pixels, real fonts, blurred shadows, no
// server. One full phone screen per image.
//
//   TZ=UTC flutter test --concurrency=1 tool/capture/make_it_yours_test.dart
//   ... --dart-define=VISION_PASS=pass2 (file prefix)
//
// Output: /home/eslam/vision-assets/out/make-it-yours/progress/<pass>-<scene>-<dark|light>.png
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import 'fixtures.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/make-it-yours/progress',
);

/// The pass the images belong to ("pass1"), the file name's prefix.
const _pass = String.fromEnvironment('VISION_PASS', defaultValue: 'pass1');

/// Only these scenes (comma separated), when given.
const _only = String.fromEnvironment('VISION_ONLY');

bool _wanted(String scene) => _only.isEmpty || _only.split(',').contains(scene);

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
  // Past the first reply: the tour may be offered.
  await prefs.setBool('oc.nudges.firstReplySeen', true);
  return prefs;
}

CaptureController _controller(SharedPreferences prefs) {
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
    ..api = CaptureApi()
    ..repository = CaptureRepository()
    ..status = StreamStatus.connected;
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
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
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

  Future<void> done(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(const Duration(seconds: 5));
    debugDisableShadows = true;
    debugDefaultTargetPlatformOverride = null;
  }

  // "offer", then "step1".."step4".
  const scenes = ['offer', 'step1', 'step2', 'step3', 'step4'];
  for (final light in [false, true]) {
    for (final (i, scene) in scenes.indexed) {
      if (!_wanted(scene)) continue;
      final shade = light ? 'light' : 'dark';
      testWidgets('$scene $shade', (tester) async {
        phone(tester);
        final prefs = await _prefs();
        final controller = _controller(prefs);
        addTearDown(controller.dispose);
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: AppearanceSettingsScreen(controller: controller),
            boundaryKey: key,
            controller: controller,
            light: light,
          ),
        );
        await _frames(tester);
        if (i > 0) {
          await tester.tap(find.byKey(const ValueKey('kit-tour-offer-start')));
          await _frames(tester, 6);
          for (var step = 1; step < i; step++) {
            await tester.tap(find.byKey(const ValueKey('kit-tour-next')));
            await _frames(tester, 6);
          }
        }
        expect(tester.takeException(), isNull);
        await writePng(
          '$_out/$_pass-$scene-$shade.png',
          await capturePng(tester, key, pixelRatio: 2),
        );
        await done(tester);
      });
    }
  }
}

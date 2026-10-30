// Captures of the in-app Notifications page (vision slice, 2026-10-20): the
// one part of the notifications pick the app draws itself. Android draws the
// notifications; their final copy is the table in
// docs/qa/vision-notifications-2026-10-20/README.md.
// Phone 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_notifications_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_PASS=pass1 \
//     tool/capture/vision_notifications_test.dart
//
// Output: /home/eslam/vision-assets/out/vision-notifications/<pass>/
// <scene>.png (without a pass, straight into the folder).
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _pass = String.fromEnvironment('VISION_PASS', defaultValue: '');
const _root = '/home/eslam/vision-assets/out/vision-notifications';
String get _dir => _pass.isEmpty ? _root : '$_root/$_pass';

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  blocked('notifications-off-dark', granted: false),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.granted = true,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final bool granted;
  final Size size;
  final double scale;
  final Locale locale;
}

final _mac = ServerProfile(
  id: 'mac',
  name: 'MacBook',
  baseUrl: 'http://100.70.12.4:4096',
);
final _phone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
);

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
  });

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      tester.platformDispatcher.textScaleFactorTestValue = scene.scale;
      tester.platformDispatcher.localesTestValue = [scene.locale];
      tester.platformDispatcher.localeTestValue = scene.locale;
      addTearDown(tester.view.reset);
      addTearDown(tester.platformDispatcher.clearAllTestValues);
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
      final prefs = await setupCapturePreferences();
      final store = SeededProfileStore(prefs: prefs, seeded: [_mac, _phone]);
      final controller = CaptureController(store);
      final api = CaptureApi();
      controller
        ..api = api
        ..repository = CaptureRepository()
        ..status = StreamStatus.connected
        ..directory = projectDirectory
        ..sessionsById = Map.of(api.sessionsById);
      controller.backgroundLive.notificationGranted = scene.granted;
      controller.appearance.value = scene.light
          ? AppAppearance.light
          : AppAppearance.dark;
      final boundary = GlobalKey();
      await tester.pumpWidget(
        captureApp(
          // The app's own Arabic type, as main.dart applies it.
          home: Builder(
            builder: (context) => Theme(
              data: AppTheme.forLocale(Theme.of(context), scene.locale),
              child: NotificationsSettingsScreen(
                controller: controller,
                page: NotificationsPage.notifications,
              ),
            ),
          ),
          boundaryKey: boundary,
          controller: controller,
          light: scene.light,
        ),
      );
      await tester.pump();
      controller.backgroundLive.notificationGranted = scene.granted;
      await tester.pump(const Duration(milliseconds: 500));
      expect(tester.takeException(), isNull);
      await writePng(
        '$_dir/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump(const Duration(seconds: 10));
      debugDisableShadows = true;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}

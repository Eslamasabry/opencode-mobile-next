// Captures of the Settings hub (vision slice, 2026-10-20): the real shell on
// its Settings tab, the live background painted still, real fonts, no server.
// Phone 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_settings_hub_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_PASS=pass1 \
//     tool/capture/vision_settings_hub_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-settings-hub/<pass>/
// <scene>.png (without a pass, straight into the folder).
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _pass = String.fromEnvironment('VISION_PASS', defaultValue: '');
const _root = '/home/eslam/Storage2/launch-footage/vision-settings-hub';
String get _dir => _pass.isEmpty ? _root : '$_root/$_pass';

class _Api extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '2.0.28');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

/// Which servers are saved, and how the one in use is doing.
enum _Servers { phone, several, offline, none }

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  several('several-servers-dark', servers: _Servers.several),
  offline('offline-dark', servers: _Servers.offline),
  none('no-server-dark', servers: _Servers.none),
  search('search-dark', query: 'quiet'),
  noMatch('search-no-match-dark', query: 'xylophone'),
  agent('agent-page-dark', open: 'settings-hub-row-ai'),
  server(
    'server-page-several-dark',
    servers: _Servers.several,
    open: 'settings-hub-row-connections',
  ),
  conversations('conversations-page-dark', open: 'settings-hub-row-chat'),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.servers = _Servers.phone,
    this.query,
    this.open,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _Servers servers;
  final String? query;
  final String? open;
  final Size size;
  final double scale;
  final Locale locale;
}

final _phone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
);
final _mac = ServerProfile(
  id: 'mac',
  name: 'MacBook',
  baseUrl: 'http://100.70.12.4:4096',
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
      final profiles = switch (scene.servers) {
        _Servers.phone || _Servers.offline => [_phone],
        _Servers.several => [_mac, _phone],
        _Servers.none => <ServerProfile>[],
      };
      final store = SeededProfileStore(prefs: prefs, seeded: profiles);
      final controller = CaptureController(store);
      if (profiles.isNotEmpty) {
        final api = _Api();
        controller
          ..api = api
          ..repository = _Repository()
          ..status = scene.servers == _Servers.offline
              ? StreamStatus.disconnected
              : StreamStatus.connected
          ..directory = projectDirectory
          ..sessionsById = Map.of(api.sessionsById);
        controller.catalog = sampleCatalog();
      }
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
              child: const HomeScreen(initialTab: 2),
            ),
          ),
          boundaryKey: boundary,
          controller: controller,
          light: scene.light,
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      if (scene.query case final query?) {
        await tester.enterText(
          find.byKey(const ValueKey('settings-hub-search-field')),
          query,
        );
        await tester.pump(const Duration(milliseconds: 600));
        await tester.pump(const Duration(milliseconds: 300));
      }
      if (scene.open case final open?) {
        await tester.tap(find.byKey(ValueKey(open)));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 600));
      }
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

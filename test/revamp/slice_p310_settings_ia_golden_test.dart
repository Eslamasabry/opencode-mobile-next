// Golden renders of slice-P3.10 (Settings IA, target-ia §1.3): the whole
// Settings hub at phone width (dark, light and 200 % text), one wide window,
// the hub on a server that hides rows (Paseo: the one muted line per group),
// the one Tools page (OpenCode 1 and Codex; since FG6 it holds MCP, the
// catalogs and External agents as tabs), About without tabs and Privacy
// and data with the policy row. Real fonts at DPR 1; the phone shots are
// tall so every group is in the picture.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_p310_settings_ia_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/about_screen.dart';
import 'package:opencode_mobile/ui/screens/capabilities_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../support/setup_capture_preferences.dart';

class _Api extends CaptureApi {
  _Api(this._capabilities);

  final ServerCapabilities _capabilities;

  @override
  ServerCapabilities get capabilities => _capabilities;

  @override
  Future<Health> health() async => Health(healthy: true, version: '1.18.25');
}

class _Repository extends CaptureRepository {
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async =>
      const TerminalShellSettings(selected: '', options: []);
}

void _mockPlatform(WidgetTester tester) {
  final messenger = tester.binding.defaultBinaryMessenger;
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const info = MethodChannel('dev.fluttercommunity.plus/package_info');
  const termux = MethodChannel('oc/termux');
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  messenger.setMockMethodCallHandler(
    info,
    (call) async => <String, dynamic>{
      'appName': 'OpenCode Mobile',
      'packageName': 'com.opencode.mobile',
      'version': '1.0.44',
      'buildNumber': '52',
      'buildSignature': '',
    },
  );
  messenger.setMockMethodCallHandler(termux, (call) async => null);
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(info, null);
    messenger.setMockMethodCallHandler(termux, null);
  });
}

Future<void> _shot(
  WidgetTester tester,
  String name, {
  required bool light,
  required Widget Function(ConnectionController controller) home,
  Size size = const Size(412, 915),
  double textScale = 1,
  ServerCapabilities capabilities = ServerCapabilities.allV1,
  bool asset = false,
}) async {
  _mockPlatform(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final prefs = await setupCapturePreferences();
  final store = SeededProfileStore(
    prefs: prefs,
    seeded: [
      ServerProfile(
        id: 'laptop',
        name: 'Laptop',
        baseUrl: 'http://192.168.1.20:4096',
        password: 'synthetic',
      ),
    ],
  );
  final controller = CaptureController(store)
    ..api = _Api(capabilities)
    ..repository = _Repository()
    ..status = StreamStatus.connected
    ..directory = projectDirectory
    ..version = '1.18.25';
  controller.catalog = sampleCatalog();
  final model = controller.catalog!.models.first;
  controller.selectedModel = ModelRef(
    providerID: model.providerID,
    modelID: model.id,
  );
  controller.appearance.value = light
      ? AppAppearance.light
      : AppAppearance.dark;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      captureApp(
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(textScale)),
            child: home(controller),
          ),
        ),
        boundaryKey: boundary,
        controller: controller,
        light: light,
        routes: {'/servers': (_) => const Scaffold()},
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    if (asset) {
      // The notices come from the asset bundle, off the fake clock.
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 300)),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    }
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/$name.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
    debugDefaultTargetPlatformOverride = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  Widget hub(ConnectionController controller) =>
      SettingsScreen(controller: controller);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('hub, every group · $mode', (tester) async {
      await _shot(
        tester,
        'p310_settings_hub_full_$mode',
        light: light,
        home: hub,
        size: const Size(412, 2300),
      );
    });
  }

  testWidgets('hub at 200 % text · dark', (tester) async {
    await _shot(
      tester,
      'p310_settings_hub_text200_dark',
      light: false,
      home: hub,
      size: const Size(412, 4400),
      textScale: 2,
    );
  });

  testWidgets('hub, two panes · dark', (tester) async {
    await _shot(
      tester,
      'p310_settings_hub_1280x800_dark',
      light: false,
      home: hub,
      size: const Size(1280, 800),
    );
  });

  testWidgets('hub on a server that hides rows (Paseo) · dark', (tester) async {
    await _shot(
      tester,
      'p310_settings_hub_paseo_dark',
      light: false,
      home: hub,
      size: const Size(412, 2300),
      capabilities: paseoServerCapabilities,
    );
  });

  testWidgets('About · dark', (tester) async {
    await _shot(
      tester,
      'p310_about_loaded_dark',
      light: false,
      home: (controller) => AboutScreen(controller: controller),
      size: const Size(412, 1600),
      asset: true,
    );
  });

  testWidgets('Privacy and data · dark', (tester) async {
    await _shot(
      tester,
      'p310_privacy_loaded_dark',
      light: false,
      home: (controller) => PrivacySettingsScreen(controller: controller),
    );
  });

  // ---- the Tools page (the hub rows became tabs in FG6) -------------------
  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    testWidgets('Tools · $mode', (tester) async {
      await _shot(
        tester,
        'p310_tools_loaded_$mode',
        light: light,
        home: (controller) => CapabilitiesScreen(controller: controller),
      );
    });
  }

  testWidgets('Tools on Codex · dark', (tester) async {
    await _shot(
      tester,
      'p310_tools_codex_dark',
      light: false,
      home: (controller) => CapabilitiesScreen(controller: controller),
      capabilities: codexServerCapabilities,
    );
  });
}

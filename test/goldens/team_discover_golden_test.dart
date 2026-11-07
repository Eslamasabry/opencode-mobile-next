// Finding the AI Team while it is off (docs/qa/team-discover-2026-09-25),
// at 412x915, dark and light, with the app's real fonts: the intro for
// OpenCode inside the app (this phone) and for a computer, and Settings' AI
// Team row.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/goldens/team_discover_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/widgets/builtin_team_section.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:opencode_mobile/voice/device.dart';

import '../../tool/capture/fixtures.dart' show captureApp, loadCaptureFonts;
import '../support/fake_setup_engine.dart';
import '../support/work_tab_fixture.dart';

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

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required WorkController controller,
  required Widget home,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      captureApp(
        home: home,
        boundaryKey: boundary,
        controller: controller,
        light: light,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    // Every drawing finishes its entrance.
    await tester.pump(KitMotion.celebration);
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('${name}_${light ? 'light' : 'dark'}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  setUp(() {
    // Nothing of the team is installed yet, as the page's inventory reads it.
    final previousEngine = PhoneSetup.engine;
    PhoneSetup.engine = FakeSetupEngine();
    addTearDown(() {
      PhoneSetup.engine = previousEngine;
    });
  });
  tearDown(() {
    debugBuiltinTeam = null;
    debugPlatformCapabilities = null;
    teamHostProbe = defaultTeamHostProbe;
  });

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('settings · the AI Team row · $mode', (tester) async {
      final controller = await workController(
        name: 'dev-pc',
        baseUrl: 'http://100.100.1.2:4096',
      );
      await _golden(
        tester,
        'team_discover_settings',
        light: light,
        controller: controller,
        // The AI Team row lives on the Agents page now.
        home: AgentsHubScreen(controller: controller),
      );
    });

    testWidgets('intro · OpenCode inside the app · $mode', (tester) async {
      debugPlatformCapabilities = const PlatformCapabilities.android();
      final controller = await workController(
        name: 'This phone',
        baseUrl: 'http://127.0.0.1:4097',
      );
      await _golden(
        tester,
        'team_intro_phone',
        light: light,
        controller: controller,
        // The phone's pre-flight reads the device (P1.7); a capable phone
        // answers, as a real one does within a frame.
        home: TeamIntroScreen(
          controller: controller,
          deviceProbe: () async => const VoiceDeviceInfo(
            supportedAbis: ['arm64-v8a'],
            totalMemoryMb: 8192,
            memoryClassMb: 512,
            hasMicrophone: true,
            availableStorageBytes: 20000000000,
          ),
        ),
      );
    });

    testWidgets('intro · a computer · $mode', (tester) async {
      teamHostProbe = (url, {city}) async =>
          const ProbeUnreachable(error: 'no answer');
      final controller = await workController(
        name: 'dev-pc',
        baseUrl: 'http://100.100.1.2:4096',
      );
      await _golden(
        tester,
        'team_intro_computer',
        light: light,
        controller: controller,
        home: TeamIntroScreen(controller: controller),
      );
    });
  }
}

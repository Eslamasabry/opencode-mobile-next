// screen-team-1 galleries: the AI Team intro on the kit, at 412x915 and
// 1280x800, dark and light, with the app's real fonts. The agent page and
// the Gate sheet galleries are in test/goldens/team_agent_golden_test.dart.
//
// team_intro_phone: OpenCode inside the app (this phone), with the cost
// before the set-up primary (time and memory per worker).
// team_intro_computer: a computer where Gas City was not found yet.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_team_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitSkeletonRows;
import 'package:opencode_mobile/ui/kit/kit_motion.dart';
import 'package:opencode_mobile/ui/screens/team/team_intro_screen.dart';
import 'package:opencode_mobile/ui/widgets/team_host_form.dart';
import 'package:opencode_mobile/voice/device.dart';

import '../../tool/capture/fixtures.dart' show captureApp, loadCaptureFonts;
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

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

/// A phone the pre-flight lets the team run on.
const _capablePhone = VoiceDeviceInfo(
  supportedAbis: ['arm64-v8a'],
  totalMemoryMb: 8192,
  memoryClassMb: 512,
  hasMicrophone: true,
  availableStorageBytes: 20000000000,
);

Future<void> _golden(
  WidgetTester tester,
  String name, {
  required bool light,
  required Size size,
  required WorkController controller,
  Future<VoiceDeviceInfo> Function()? deviceProbe,
}) async {
  _mockSecureStorage(tester);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final boundary = GlobalKey();
  debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
  try {
    await tester.pumpWidget(
      captureApp(
        home: TeamIntroScreen(controller: controller, deviceProbe: deviceProbe),
        boundaryKey: boundary,
        controller: controller,
        light: light,
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump(KitMotion.celebration);
    expect(tester.takeException(), isNull);
    // A gallery of the intro, never of its loading rows (the phone's
    // pre-flight or the Gas City search still running).
    expect(find.byType(KitSkeletonRows), findsNothing);
    final suffix = size == _phone ? '' : '_1280x800';
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile(
        'goldens/$name${suffix}_${light ? 'light' : 'dark'}.png',
      ),
    );
  } finally {
    debugDefaultTargetPlatformOverride = null;
    await tester.pumpWidget(const SizedBox.shrink());
    controller.dispose();
    await tester.pump();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  tearDown(() {
    debugPlatformCapabilities = null;
    teamHostProbe = defaultTeamHostProbe;
  });

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';
    for (final size in [_phone, _wide]) {
      final at = size == _phone ? 'phone' : 'wide';

      testWidgets('intro · OpenCode inside the app · $mode · $at', (
        tester,
      ) async {
        debugPlatformCapabilities = const PlatformCapabilities.android();
        final controller = await workController(
          name: 'This phone',
          baseUrl: 'http://127.0.0.1:4097',
        );
        await _golden(
          tester,
          'team_intro_phone',
          light: light,
          size: size,
          controller: controller,
          // The phone's pre-flight (P1.7) asks the device; without an
          // answer the page shows its loading rows, not the intro.
          deviceProbe: () async => _capablePhone,
        );
      });

      testWidgets('intro · a computer · $mode · $at', (tester) async {
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
          size: size,
          controller: controller,
        );
      });
    }
  }
}

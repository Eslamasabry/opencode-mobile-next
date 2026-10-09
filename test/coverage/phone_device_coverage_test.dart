// Coverage ratchet for what the phone reports about itself before setup
// (oc/voice getDeviceInfo): each case is the answer, drawn by the real start
// screen with its real pre-flight (see paseo_coverage_support.dart).
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/voice/device.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/fake_setup_engine.dart';
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() {
    const secure = MethodChannel(
      'plugins.it_nomads.com/flutter_secure_storage',
    );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
  });
  final family = CoverageFamily('phone_device', prefix: '');
  registerLedgerTests(family);

  // The same reading AndroidVoiceDevicePlatform.getDeviceInfo makes of the
  // channel's map.
  VoiceDeviceInfo read(Map<String, dynamic> m) => VoiceDeviceInfo(
    availableStorageBytes: (m['availableStorageBytes'] as num?)?.toInt(),
    memoryClassMb: (m['memoryClassMb'] as num?)?.toInt(),
    totalMemoryMb: (m['totalMemoryMb'] as num?)?.toInt(),
    lowRamDevice: m['lowRamDevice'] == true,
    supportedAbis: (m['supportedAbis'] as List).cast<String>(),
    hasMicrophone: m['hasMicrophone'] as bool? ?? true,
  );

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('phone check · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 2000);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      PhoneSetup.engine = FakeSetupEngine();
      PhoneSetup.termux = FakeSetupEngine();
      final device = read(Map<String, dynamic>.from(variant['payload'] as Map));
      final boundary = GlobalKey();
      final (store, controller) = await serversState();
      controller.dispose();
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary,
          controller: controller,
          store: store,
          home: PhoneSetupStartScreen(
            termuxProbe: () async => const TermuxRunningServer.absent(),
            inAppProbe: () async => false,
            deviceProbe: () async => device,
            openProgress: (_) async {},
          ),
        ),
      );
      await frames(tester, 14);
      final screen = screenText(tester).join('\n');
      await writeCasePng(tester, boundary, 'phone_$id');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}

// Coverage ratchet for the tools phone setup offers (the registry): each
// field of a tool is drawn by the real Customize sheet and Start page and
// checked against its ledger (see paseo_coverage_support.dart). The sample
// is also compared with the live registry, so a changed title, default or
// time fails here until the sample and ledger follow.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/termux_running_server.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_customize_sheet.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_selection.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_start_screen.dart';
import 'package:opencode_mobile/voice/device.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../support/fake_setup_engine.dart';
import '../support/voice_setup_fakes.dart' show FakeSetupApp;
import 'paseo_coverage_support.dart';
import 'servers_support.dart';

const _device = VoiceDeviceInfo(
  availableStorageBytes: 2000000000,
  memoryClassMb: 256,
  totalMemoryMb: 4096,
  supportedAbis: ['arm64-v8a'],
  hasMicrophone: false,
);

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
  final family = CoverageFamily('phone_component', prefix: '');
  registerLedgerTests(family);
  final l10n = lookupAppLocalizations(const Locale('en'));

  // The registry as the app builds it, with voice typing's model download
  // stood in for (the real one asks the phone).
  List<SetupComponent> registry() => [
    for (final component in setupComponents(l10n))
      if (component.id != SetupComponentIds.voice) component,
    SetupComponent(
      id: SetupComponentIds.voice,
      title: l10n.voiceComponentTitle,
      shortTitle: l10n.voiceComponentTitle,
      summary: l10n.voiceComponentSummary,
      estimatedSeconds: 45,
      downloadBytes: 160 * 1000 * 1000,
      checkScript: '',
      installScript: '',
      app: FakeSetupApp(),
    ),
  ];

  for (final variant in family.cases) {
    final id = variant['id'] as String;
    testWidgets('setup tools · $id', (tester) async {
      tester.view.physicalSize = const Size(412, 2400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      final live = {for (final c in registry()) c.id: c};
      for (final sample
          in ((variant['payload'] as Map)['components'] as List).cast<Map>()) {
        final real = live[sample['id']]!;
        expect(real.title, sample['title'], reason: '${real.id} title');
        expect(real.required, sample['required'], reason: real.id);
        expect(real.defaultOn, sample['defaultOn'], reason: real.id);
        expect(real.estimatedSeconds, sample['estimatedSeconds']);
        expect(real.why, sample['why'], reason: '${real.id} why');
        expect(real.summary, sample['summary'], reason: '${real.id} summary');
        expect(real.shortTitle, sample['shortTitle']);
      }
      final boundary = GlobalKey();
      final (store, controller) = await serversState();
      controller.dispose();
      final components = installableComponents(registry());
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary,
          controller: controller,
          store: store,
          home: Scaffold(
            body: SetupCustomizeSheet(
              registry: components,
              deviceProbe: () async => _device,
            ),
          ),
        ),
      );
      await frames(tester, 14);
      for (var i = 0; i < 12; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      final seen = <String>[];
      // The sheet scrolls inside the phone-sized frame: read it screen by
      // screen, and note each switch's position (a mark, not a word).
      for (var i = 0; i < 8; i++) {
        seen.add(screenText(tester).join('\n'));
        for (final component in components) {
          final row = find.byKey(
            ValueKey('phone-setup-customize-${component.id}'),
          );
          if (row.evaluate().isEmpty) continue;
          final on = tester.widget<KitSwitchRow>(row).value;
          seen.add('[${component.id} ${on ? 'on' : 'off'}]');
        }
        await tester.drag(
          find.byType(Scrollable).last,
          const Offset(0, -300),
          warnIfMissed: false,
        );
        await frames(tester, 4);
      }
      await writeCasePng(tester, boundary, 'phone_${id}_customize');
      await tester.pumpWidget(const SizedBox.shrink());
      final boundary2 = GlobalKey();
      PhoneSetup.engine = FakeSetupEngine(registry: registry());
      PhoneSetup.termux = FakeSetupEngine(registry: registry());
      await tester.pumpWidget(
        captureApp(
          boundaryKey: boundary2,
          controller: controller,
          store: store,
          home: PhoneSetupStartScreen(
            termuxProbe: () async => const TermuxRunningServer.absent(),
            inAppProbe: () async => false,
            deviceProbe: () async => _device,
            openProgress: (_) async {},
          ),
        ),
      );
      await frames(tester, 14);
      seen.add(screenText(tester).join('\n'));
      await writeCasePng(tester, boundary2, 'phone_${id}_start');
      final screen = seen.join('\n');
      final problems = checkCase(family, variant, screen, primaryText: screen);
      await tester.pumpWidget(const SizedBox.shrink());
      expect(problems, isEmpty, reason: 'screen text:\n${flat(screen)}');
    });
  }
}

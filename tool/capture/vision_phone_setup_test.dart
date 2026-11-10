// Captures of the phone setup progress screen (vision slice, 2026-10-20):
// the real PhoneSetupProgressScreen over a scripted engine with the app's
// own component registry, the live aurora painted (still), blurred shadows
// as a phone draws them, real fonts. Phone 412x915 dp at 2x pixels, one
// full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_phone_setup_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_phone_setup_test.dart
//
// Output: /home/eslam/vision-assets/out/vision-phone-setup/<scene>.png
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/components.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/network.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/revamp/screen_phone_1_fixtures.dart';
import '../../test/support/fake_setup_engine.dart';
import 'fixtures.dart' show capturePng, writePng;

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/vision-assets/out/vision-phone-setup',
);

enum _Job {
  download,
  apt,
  starting,
  checking,
  paused,
  interrupted,
  failed,
  done,
}

enum _Scene {
  mainDark('main-download-dark'),
  mainLight('main-download-light', light: true),
  apt('installing-percent-dark', job: _Job.apt),
  starting('starting-steps-dark', job: _Job.starting),
  checking('checking-dark', job: _Job.checking),
  paused('paused-dark', job: _Job.paused),
  pausedLight('paused-light', job: _Job.paused, light: true),
  interrupted('interrupted-dark', job: _Job.interrupted),
  failed('failed-offline-dark', job: _Job.failed),
  done('done-dark', job: _Job.done),
  detailsSheet('details-sheet-dark', details: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.job = _Job.download,
    this.light = false,
    this.details = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final _Job job;
  final bool light;
  final bool details;
  final Size size;
  final double scale;
  final Locale locale;
}

const _linux = ComponentProgress(
  id: SetupComponentIds.linux,
  state: ComponentState.done,
  version: '24.04.5',
);
const _essentials = ComponentProgress(
  id: SetupComponentIds.essentials,
  state: ComponentState.done,
  version: '2.43.0',
);
const _python = ComponentProgress(
  id: SetupComponentIds.python,
  state: ComponentState.done,
  version: '3.12.3',
);
const _node = ComponentProgress(
  id: SetupComponentIds.node,
  state: ComponentState.done,
  version: '22.11.0',
);
const _start = ComponentProgress(
  id: SetupComponentIds.start,
  state: ComponentState.pending,
);

SetupProgress _progress(_Job job, AppLocalizations l10n) {
  const log =
      'Get:1 http://ports.ubuntu.com/ubuntu-ports noble InRelease [256 kB]\n'
      'Reading package lists...\n'
      '::oc stage Downloading OpenCode 1.4.2';
  switch (job) {
    case _Job.download:
      return const SetupProgress(
        jobId: 'job-1',
        state: SetupState.running,
        current: SetupComponentIds.openCode,
        overall: .7,
        etaSeconds: 170,
        logTail: log,
        firstSetup: true,
        components: [
          _linux,
          _essentials,
          _python,
          _node,
          ComponentProgress(
            id: SetupComponentIds.openCode,
            state: ComponentState.running,
            stage: 'Downloading OpenCode 1.4.2',
            bytesDone: 41200000,
            bytesTotal: 64000000,
          ),
          _start,
        ],
      );
    case _Job.apt:
      return const SetupProgress(
        jobId: 'job-1',
        state: SetupState.running,
        current: SetupComponentIds.python,
        overall: .4,
        etaSeconds: 260,
        logTail: log,
        components: [
          _linux,
          _essentials,
          ComponentProgress(
            id: SetupComponentIds.python,
            state: ComponentState.running,
            stage: 'Installing packages',
            percent: 62,
          ),
          ComponentProgress(
            id: SetupComponentIds.node,
            state: ComponentState.pending,
          ),
          ComponentProgress(
            id: SetupComponentIds.openCode,
            state: ComponentState.pending,
          ),
          _start,
        ],
      );
    case _Job.starting:
      return const SetupProgress(
        jobId: 'job-1',
        state: SetupState.running,
        current: SetupComponentIds.start,
        overall: .95,
        etaSeconds: 20,
        logTail: log,
        components: [
          _linux,
          _essentials,
          _python,
          _node,
          ComponentProgress(
            id: SetupComponentIds.openCode,
            state: ComponentState.done,
            version: '1.4.2',
          ),
          ComponentProgress(
            id: SetupComponentIds.start,
            state: ComponentState.running,
            stage: 'Starting OpenCode',
          ),
        ],
      );
    case _Job.checking:
      return SetupProgress(
        jobId: 'job-1',
        state: SetupState.running,
        overall: 0,
        components: [
          for (final id in _ids)
            ComponentProgress(id: id, state: ComponentState.checking),
        ],
      );
    case _Job.paused || _Job.interrupted:
      return SetupProgress(
        jobId: 'job-1',
        state: job == _Job.paused
            ? SetupState.cancelled
            : SetupState.interrupted,
        current: SetupComponentIds.openCode,
        overall: .6,
        logTail: log,
        components: const [
          _linux,
          _essentials,
          _python,
          _node,
          ComponentProgress(
            id: SetupComponentIds.openCode,
            state: ComponentState.pending,
          ),
          _start,
        ],
      );
    case _Job.failed:
      return SetupProgress(
        jobId: 'job-1',
        state: SetupState.failed,
        current: SetupComponentIds.openCode,
        overall: .6,
        error: l10n.phoneSetupErrorNoInternet,
        logTail:
            '$log\ncurl: (6) Could not resolve host: github.com\n'
            '[oc] download failed',
        components: [
          _linux,
          _essentials,
          _python,
          _node,
          ComponentProgress(
            id: SetupComponentIds.openCode,
            state: ComponentState.failed,
            stage: 'Downloading OpenCode 1.4.2',
            error: l10n.phoneSetupErrorOffline('OpenCode'),
          ),
          _start,
        ],
      );
    case _Job.done:
      return SetupProgress(
        jobId: 'job-1',
        state: SetupState.done,
        overall: 1,
        logTail: log,
        components: [
          for (final id in _ids)
            ComponentProgress(id: id, state: ComponentState.done),
        ],
      );
  }
}

const _ids = [
  SetupComponentIds.linux,
  SetupComponentIds.essentials,
  SetupComponentIds.python,
  SetupComponentIds.node,
  SetupComponentIds.openCode,
  SetupComponentIds.start,
];

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadKitGalleryFonts);
  setUp(() {
    KitLiveBackground.paintWithoutLoops = true;
    KitMotion.loops = false;
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
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
      KitLiveBackground.frozenSeconds = 21;
      final l10n = lookupAppLocalizations(scene.locale);
      final engine = FakeSetupEngine(registry: setupComponents(l10n))
        ..emit(_progress(scene.job, l10n));
      final boundary = GlobalKey();
      final screen = PhoneSetupProgressScreen(
        engine: engine,
        firstSetup: true,
        askNotifications: () async => true,
        openReady: (_) async {},
        readNetwork: () => Future.value(
          const NetworkReading(
            status: NetworkStatus.online,
            transport: NetworkTransport.wifi,
            connected: true,
          ),
        ),
      );
      await pumpPhone(
        tester,
        boundary: boundary,
        size: scene.size,
        pixelRatio: 2,
        light: scene.light,
        textScale: scene.scale,
        locale: scene.locale,
        home: const SizedBox.expand(key: ValueKey('opener')),
      );
      // Opened as the app opens it, so the page has its Back.
      unawaited(
        Navigator.of(
          tester.element(find.byKey(const ValueKey('opener'))),
        ).push(KitPageRoute<void>(builder: (_) => screen)),
      );
      for (var i = 0; i < 10; i++) {
        await tester.pump(const Duration(milliseconds: 100));
      }
      if (scene.details) {
        await tester.tap(find.byKey(const Key('setup-progress-details')));
        for (var i = 0; i < 10; i++) {
          await tester.pump(const Duration(milliseconds: 100));
        }
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await unmountPhone(tester);
      debugDisableShadows = true;
    });
  }
}

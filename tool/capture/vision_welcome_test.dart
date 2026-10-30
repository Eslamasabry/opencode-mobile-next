// Captures of the first-run welcome (vision slice, 2026-10-20): the real
// ServersScreen with nothing saved, as the app opens it on a fresh phone, the
// live aurora painted (still), real fonts, blurred shadows, no server. Phone
// 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_welcome_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_OUT=<dir> \
//     tool/capture/vision_welcome_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-welcome/<scene>.png
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/termux/termux_reach.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/capture_seams.dart';
import '../../test/support/fake_setup_engine.dart';
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import 'audit/audit_env.dart';

const _out = String.fromEnvironment(
  'VISION_OUT',
  defaultValue: '/home/eslam/Storage2/launch-footage/vision-welcome',
);

enum _Job { none, running, stopped, done }

enum _Scene {
  mainDark('welcome-dark'),
  mainLight('welcome-light', light: true),
  computerChosen('computer-chosen-dark', computer: true),
  jobRunning('setup-running-dark', job: _Job.running),
  jobStopped('setup-stopped-dark', job: _Job.stopped),
  jobDone('setup-done-not-connected-dark', job: _Job.done),
  noPhonePath('no-phone-path-dark', desktop: true),
  termuxFound('termux-found-dark', termux: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.computer = false,
    this.job = _Job.none,
    this.desktop = false,
    this.termux = false,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final bool computer;
  final _Job job;
  final bool desktop;

  /// Termux is on the phone with the app's OpenCode in it, not yet allowed.
  final bool termux;
  final Size size;
  final double scale;
  final Locale locale;
}

SetupProgress _progress(SetupState state, double overall) => SetupProgress(
  state: state,
  components: const [],
  overall: overall,
  jobId: 'first',
  firstSetup: true,
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
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');

  for (final scene in _Scene.values) {
    testWidgets(scene.file, (tester) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      capturePlatformCapabilities = scene.desktop
          ? const PlatformCapabilities.linuxDesktop()
          : const PlatformCapabilities.android();
      // Blurred shadows as a phone draws them (the test binding flattens
      // them); put back before the test ends.
      debugDisableShadows = false;
      tester.view.physicalSize = scene.size * 2;
      tester.view.devicePixelRatio = 2;
      addTearDown(tester.view.reset);
      KitLiveBackground.frozenSeconds = 21;
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(storage, (_) async => null);
      addTearDown(() => messenger.setMockMethodCallHandler(storage, null));

      if (scene.termux) {
        TermuxAccess.blocked = false;
        final previousProbe = captureTermuxRunningServerProbe;
        captureTermuxRunningServerProbe =
            ({required baseUrl, username, password, cancellation}) async =>
                const ServerProbeResult.failure(
                  'password required',
                  needsPassword: true,
                );
        addTearDown(() => captureTermuxRunningServerProbe = previousProbe);
        const termux = MethodChannel('oc/termux');
        messenger.setMockMethodCallHandler(termux, (call) async {
          if (call.method == 'getCapabilities') {
            return <String, Object>{
              'installed': true,
              'version': '0.118',
              'serviceAvailable': true,
              'protocolSupported': true,
              'permissionGranted': false,
            };
          }
          return null;
        });
        addTearDown(() => messenger.setMockMethodCallHandler(termux, null));
      }
      // The real component registry, so the cost line is the engine's own.
      final engine = FakeSetupEngine(registry: ChannelSetupEngine().registry);
      switch (scene.job) {
        case _Job.none:
          break;
        case _Job.running:
          engine.emit(_progress(SetupState.running, .42));
        case _Job.stopped:
          engine.emit(_progress(SetupState.interrupted, .42));
        case _Job.done:
          engine.emit(_progress(SetupState.done, 1));
      }
      final previous = PhoneSetup.engine;
      PhoneSetup.engine = engine;
      addTearDown(() => PhoneSetup.engine = previous);

      final prefs = await setupCapturePreferences();
      final store = SeededProfileStore(prefs: prefs, seeded: []);
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);
      final boundary = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: boundary,
          child: ProviderScope(
            overrides: [
              bootstrapProvider.overrideWithValue(AppBootstrap(store)),
              connProvider.overrideWithValue(controller),
            ],
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: AppTheme.forLocale(
                captureTheme(light: scene.light),
                scene.locale,
              ),
              locale: scene.locale,
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(context).copyWith(
                  disableAnimations: false,
                  textScaler: TextScaler.linear(scene.scale),
                ),
                child: auditWrap(context, child ?? const SizedBox.shrink()),
              ),
              home: const ServersScreen(),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.runAsync(
        () => Future<void>.delayed(const Duration(milliseconds: 200)),
      );
      await tester.pumpAndSettle();
      if (scene.computer) {
        await tester.tap(find.byKey(const ValueKey('welcome-choice-computer')));
        await tester.pumpAndSettle();
      }
      expect(tester.takeException(), isNull);
      await writePng(
        '$_out/${scene.file}.png',
        await capturePng(tester, boundary, pixelRatio: 2),
      );
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pump(const Duration(seconds: 4));
      debugDisableShadows = true;
      capturePlatformCapabilities = null;
      debugDefaultTargetPlatformOverride = null;
    });
  }
}

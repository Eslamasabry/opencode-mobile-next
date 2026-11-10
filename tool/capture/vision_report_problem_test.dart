// Captures of Report a problem (vision slice, 2026-10-20): the page as a
// failure opens it, real fonts, the ground painted still, no network.
// Phone 412x915 dp at 2x pixels, one full screen per image.
//
//   flutter test --concurrency=1 tool/capture/vision_report_problem_test.dart
//   flutter test --concurrency=1 --dart-define=VISION_PASS=pass1 \
//     tool/capture/vision_report_problem_test.dart
//
// Output: /home/eslam/Storage2/launch-footage/vision-report-problem/<pass>/
// <scene>.png (without a pass, straight into the folder).
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/app_diagnostics_screen.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

const _pass = String.fromEnvironment('VISION_PASS', defaultValue: '');
const _root = '/home/eslam/Storage2/launch-footage/vision-report-problem';
String get _dir => _pass.isEmpty ? _root : '$_root/$_pass';

class _Api extends CaptureApi {
  @override
  Future<Health> health() async => Health(healthy: true, version: '2.0.28');
}

/// What brought the person to the page.
enum _From {
  /// A failure state's "Report a problem", its error attached.
  failure,

  /// A failed job (phone setup), with its log.
  job,

  /// Settings, with an error kept on this phone.
  kept,

  /// Settings, with nothing noticed.
  nothing,
}

enum _Scene {
  mainDark('main-dark'),
  mainLight('main-light', light: true),
  details('details-open-dark', openDetails: true),
  withWords(
    'typed-dark',
    typed: 'It happened right after I switched projects.',
  ),
  kept('kept-error-dark', from: _From.kept),
  nothing('nothing-noticed-dark', from: _From.nothing),
  describeFirst('describe-first-dark', from: _From.nothing, send: true),
  job('failed-job-dark', from: _From.job, openDetails: true),
  kept2('kept-on-phone-page-dark', from: _From.kept, openKept: true),
  small('small-320-text2-dark', size: Size(320, 693), scale: 2),
  arabic('arabic-rtl-dark', locale: Locale('ar'));

  const _Scene(
    this.file, {
    this.light = false,
    this.from = _From.failure,
    this.openDetails = false,
    this.openKept = false,
    this.send = false,
    this.typed,
    this.size = const Size(412, 915),
    this.scale = 1,
    this.locale = const Locale('en'),
  });
  final String file;
  final bool light;
  final _From from;
  final bool openDetails;
  final bool openKept;
  final bool send;
  final String? typed;
  final Size size;
  final double scale;
  final Locale locale;
}

final _phone = ServerProfile(
  id: 'phone',
  name: 'This phone',
  baseUrl: 'http://127.0.0.1:4097',
);

KitReport? _error(_From from, Locale locale) => switch (from) {
  _From.failure => KitReport(
    title: locale.languageCode == 'ar'
        ? 'تعذّر تحميل المحادثات'
        : "Couldn't load conversations",
    details:
        'SocketException: Connection refused (OS Error: Connection '
        'refused, errno = 111), address = 100.70.12.4, port = 4096',
    errorType: 'SocketException',
    source: 'global-sessions',
  ),
  _From.job => const KitReport(
    title: "Python couldn't be installed",
    errorType: 'SetupJobFailed',
    source: 'setup-progress',
    log:
        'Get:1 http://ports.ubuntu.com noble InRelease\n'
        'E: Unable to fetch some archives, maybe run apt-get update\n'
        'E: Sub-process /usr/bin/dpkg returned an error code (1)',
  ),
  _From.kept || _From.nothing => null,
};

AppDiagnosticsController _diagnostics(_From from) {
  final diagnostics = AppDiagnosticsController();
  if (from == _From.nothing) return diagnostics;
  diagnostics.record(
    StateError('Connection closed before full header was received'),
    StackTrace.fromString(
      '#0      _EventStream._read (package:opencode_mobile/api2/sse.dart:142:7)\n'
      '#1      _EventStream.listen (package:opencode_mobile/api2/sse.dart:88:5)',
    ),
    source: 'sse',
    at: DateTime(2026, 10, 20, 9, 41),
  );
  return diagnostics;
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
      final store = SeededProfileStore(prefs: prefs, seeded: [_phone]);
      final controller = CaptureController(store);
      final api = _Api();
      controller
        ..api = api
        ..status = StreamStatus.connected
        ..directory = projectDirectory
        ..sessionsById = Map.of(api.sessionsById);
      controller.appearance.value = scene.light
          ? AppAppearance.light
          : AppAppearance.dark;
      final diagnostics = _diagnostics(scene.from);
      final boundary = GlobalKey();
      final navigator = GlobalKey<NavigatorState>();
      // Pushed over a plain page, as every entry opens it, so the bar
      // carries Back.
      await tester.pumpWidget(
        captureApp(
          home: const KitScreen(body: SizedBox.shrink()),
          navigatorKey: navigator,
          boundaryKey: boundary,
          controller: controller,
          light: scene.light,
        ),
      );
      unawaited(
        navigator.currentState!.push(
          KitPageRoute<void>(
            builder: (context) => Theme(
              data: AppTheme.forLocale(Theme.of(context), scene.locale),
              child: AppDiagnosticsScreen(
                controller: controller,
                diagnostics: diagnostics,
                error: _error(scene.from, scene.locale),
                version: () async => '1.4.0+54',
                linkLauncher: (_) async => true,
              ),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 600));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      if (scene.typed case final typed?) {
        await tester.enterText(find.byType(EditableText).first, typed);
        await tester.pump(const Duration(milliseconds: 300));
        FocusManager.instance.primaryFocus?.unfocus();
        await tester.pump(const Duration(milliseconds: 300));
      }
      if (scene.openDetails || scene.openKept) {
        final toggle = find.byKey(const ValueKey('report-problem-details'));
        if (toggle.evaluate().isNotEmpty) {
          await tester.tap(toggle);
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 400));
        }
      }
      if (scene.openKept) {
        final kept = find.byKey(const ValueKey('report-problem-kept'));
        if (kept.evaluate().isNotEmpty) {
          await tester.ensureVisible(kept);
          await tester.pump(const Duration(milliseconds: 300));
          await tester.tap(kept);
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 600));
        }
      }
      if (scene.send) {
        await tester.tap(find.byKey(const ValueKey('report-problem-send')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
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

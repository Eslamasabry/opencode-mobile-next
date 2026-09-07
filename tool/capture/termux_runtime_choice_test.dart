// Synthetic native-screen captures. No Termux commands or installs execute.
// flutter test --no-pub --concurrency=1 tool/capture/termux_runtime_choice_test.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/termux_setup_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

Map<String, Object> _result(String stdout) => {
  'stdout': stdout,
  'stderr': '',
  'exitCode': 0,
  'err': -1,
  'errorMessage': '',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final variant in [
    (name: 'light', light: true, width: 390.0, scale: 1.0, ratio: 3.0),
    (name: 'dark', light: false, width: 390.0, scale: 1.0, ratio: 3.0),
    (name: 'large', light: true, width: 320.0, scale: 2.0, ratio: 2.0),
  ]) {
    testWidgets('first-run runtime choice ${variant.name}', (tester) async {
      tester.view.physicalSize = Size(
        variant.width * variant.ratio,
        844 * variant.ratio,
      );
      tester.view.devicePixelRatio = variant.ratio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      const channel = MethodChannel('oc/termux');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'getCapabilities') {
          return {
            'installed': true,
            'version': '0.118',
            'serviceAvailable': true,
            'protocolSupported': true,
            'permissionGranted': true,
          };
        }
        if (call.method == 'runInTermux') {
          final script = (call.arguments as Map)['script'] as String;
          if (script.contains("printf 'opencode-bridge-ok'")) {
            return _result('opencode-bridge-ok');
          }
          if (script.contains('ubuntu=absent')) {
            return _result('ubuntu=absent\nversion=\n');
          }
          if (script.contains(' status')) {
            return _result(
              'phase=idle\nmessage=No setup has been started\nport=4096\nversion=\npid=\n',
            );
          }
        }
        throw StateError('Capture refuses a mutation: ${call.method}');
      });
      final store = SeededProfileStore(
        prefs: await setupCapturePreferences(),
        seeded: const [],
      );
      final controller = CaptureController(store);
      try {
        final boundary = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: Builder(
              builder: (context) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(variant.scale)),
                child: const TermuxSetupScreen(),
              ),
            ),
            boundaryKey: boundary,
            controller: controller,
            store: store,
            light: variant.light,
          ),
        );
        await tester.pumpAndSettle();
        await tester.scrollUntilVisible(
          find.text('Which OpenCode would you like to use?'),
          200,
        );
        await tester.pumpAndSettle();
        expect(find.text('OpenCode 1'), findsOneWidget);
        expect(
          tester
              .widget<RadioGroup<TermuxRuntime>>(
                find.byType(RadioGroup<TermuxRuntime>),
              )
              .groupValue,
          TermuxRuntime.openCode1,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/first-run-runtime/${variant.name}-choice.png',
          await capturePng(tester, boundary, pixelRatio: variant.ratio),
        );
        final beta = find.byKey(const Key('setup-runtime-opencode2'));
        await tester.scrollUntilVisible(beta, 160);
        await tester.ensureVisible(beta);
        await tester.pumpAndSettle();
        expect(beta.hitTestable(), findsOneWidget);
        await tester.tap(beta.hitTestable());
        await tester.pumpAndSettle();
        expect(
          tester
              .widget<RadioGroup<TermuxRuntime>>(
                find.byType(RadioGroup<TermuxRuntime>),
              )
              .groupValue,
          TermuxRuntime.openCode2,
        );
        await tester.scrollUntilVisible(find.text('Install & start'), 160);
        await tester.pumpAndSettle();
        expect(find.textContaining('0.0.0-beta-18600'), findsOneWidget);
        expect(store.profiles, isEmpty);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/first-run-runtime/${variant.name}-beta.png',
          await capturePng(tester, boundary, pixelRatio: variant.ratio),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        controller.dispose();
        messenger.setMockMethodCallHandler(channel, null);
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}

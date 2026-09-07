// Synthetic captures of the real setup screen; no device commands or network.
// Run: flutter test --concurrency=1 tool/capture/setup_progress_test.dart
// Writes docs/qa/setup-progress/{light,dark,choices,choices-dark}.png at 390x844 logical px.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/termux_setup_screen.dart';
import '../../test/support/setup_capture_preferences.dart';

import 'fixtures.dart';

const _output = '''[oc] existing Termux dependencies are healthy
[oc] Ubuntu environment is ready
[oc] Installing OpenCode 1.18.29
Resolving packages…
Downloading opencode-linux-arm64
  32 MB / 68 MB
  48 MB / 68 MB
npm WARN network connection slow; retrying
Download resumed
  64 MB / 68 MB
  68 MB / 68 MB
[oc] Checking the installed command
OpenCode 1.18.29
[oc] Refreshing the model catalog
Fetching available models…''';

Map<String, Object> _result(String output) => {
  'stdout': output,
  'stderr': '',
  'exitCode': 0,
  'err': -1,
  'errorMessage': '',
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final capture in [
    (name: 'light', light: true, running: true),
    (name: 'dark', light: false, running: true),
    (name: 'choices', light: true, running: false),
    (name: 'choices-dark', light: false, running: false),
  ]) {
    testWidgets('setup ${capture.name}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      const channel = MethodChannel('oc/termux');
      const storageChannel = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(storageChannel, (_) async => null);
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
        if (call.method != 'runInTermux') {
          throw StateError('Unexpected capture command: ${call.method}');
        }
        final script = (call.arguments as Map)['script'] as String;
        if (script.contains("printf 'opencode-bridge-ok'")) {
          return _result('opencode-bridge-ok');
        }
        if (script.contains('ubuntu=absent')) {
          return _result('ubuntu=installed\nversion=1.18.29\n');
        }
        if (script.contains('__OC_SETUP_OUTPUT__')) {
          return _result(
            '''phase=${capture.running ? 'refreshing_models' : 'stopped'}
message=${capture.running ? 'Refreshing the OpenCode model catalog' : 'Local server stopped'}
port=4096
runner=proot
version=${capture.running ? '1.18.29' : ''}
pid=${capture.running ? '123' : ''}
__OC_SETUP_OUTPUT__
${capture.running ? _output : ''}
''',
          );
        }
        throw StateError('Capture only permits mocked inspection calls.');
      });
      final store = SeededProfileStore(
        prefs: await setupCapturePreferences(),
        seeded: [
          ServerProfile(
            id: 'capture-local',
            name: 'This phone',
            baseUrl: TermuxBridge.managedServerUrl,
            username: 'opencode',
            password: 'synthetic-capture-only',
          ),
        ],
      );
      final controller = CaptureController(store);
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: const TermuxSetupScreen(),
            boundaryKey: key,
            controller: controller,
            store: store,
            light: capture.light,
          ),
        );
        // Bounded frames allow async mocks and scrolling to finish while the
        // screen's real progress indicator remains active.
        for (var frame = 0; frame < 20; frame++) {
          await tester.pump(const Duration(milliseconds: 50));
        }
        if (capture.running) {
          expect(find.text('LIVE OUTPUT'), findsOneWidget);
          expect(
            tester.getSize(find.byKey(const Key('setup-live-output'))).height,
            greaterThanOrEqualTo(300),
          );
        } else {
          expect(find.text('Start installed OpenCode'), findsOneWidget);
        }
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/setup-progress/${capture.name}.png',
          await capturePng(tester, key),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        controller.dispose();
        messenger.setMockMethodCallHandler(channel, null);
        messenger.setMockMethodCallHandler(storageChannel, null);
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}

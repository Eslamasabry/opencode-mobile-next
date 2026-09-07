// Synthetic capture of the actual Servers screen and explicit status action.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('managed health ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      const channel = MethodChannel('oc/termux');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'runInTermux');
        expect((call.arguments as Map)['script'], TermuxBridge.statusScript());
        return {
          'exitCode': 0,
          'stdout': 'phase=ready\nversion=1.18.29\nrunner=proot\npid=123\n',
          'stderr': '',
        };
      });
      final store = SeededProfileStore(
        prefs: await setupCapturePreferences(),
        seeded: [
          ServerProfile(
            id: 'local',
            name: 'This phone',
            baseUrl: TermuxBridge.managedServerUrl,
          ),
        ],
      );
      final controller = CaptureController(store);
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: const ServersScreen(),
            boundaryKey: key,
            controller: controller,
            store: store,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        await tester.ensureVisible(find.text('Check status'));
        await tester.tap(find.text('Check status'));
        await tester.pumpAndSettle();
        expect(find.text('Server process running'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/managed-health/${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
      } finally {
        await tester.pumpWidget(const SizedBox());
        controller.dispose();
        messenger.setMockMethodCallHandler(channel, null);
      }
    });
  }
}

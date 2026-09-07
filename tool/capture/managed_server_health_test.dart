// Synthetic capture: no Termux process, network, credential or clipboard access.
// flutter test --concurrency=1 tool/capture/managed_server_health_test.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/demo/demo_store.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/managed_server_recovery.dart';
import 'package:opencode_mobile/ui/widgets/managed_server_health.dart';

import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('managed server health ${light ? 'light' : 'dark'}', (
      tester,
    ) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      debugDefaultTargetPlatformOverride = TargetPlatform.android;
      final prefs = DemoPreferences();
      final store = SeededProfileStore(
        prefs: prefs,
        seeded: [
          ServerProfile(
            id: 'synthetic-local',
            name: 'On this phone',
            baseUrl: TermuxBridge.managedServerUrl,
          ),
        ],
      );
      final controller = CaptureController(store);
      const channel = MethodChannel('oc/termux');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(channel, (call) async {
        final script = (call.arguments as Map)['script'] as String;
        return {
          'exitCode': 0,
          'stderr': '',
          'stdout': script == TermuxBridge.storageScript()
              ? 'total_kib=125829120\navailable_kib=36700160\n'
              : 'phase=ready\nrunner=proot\nport=4096\nversion=1.18.29\noperation=capture\npid=12\n',
        };
      });
      try {
        final key = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            boundaryKey: key,
            controller: controller,
            store: store,
            light: light,
            home: Scaffold(
              appBar: AppBar(title: const Text('On this phone')),
              body: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: ManagedServerHealth(
                  prefs: prefs,
                  profileID: 'synthetic-local',
                  onManage: () {},
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('Check status'));
        await tester.pumpAndSettle();
        expect(
          find.text('Termux storage: 35.0 GiB free of 120.0 GiB'),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/managed-server/${light ? 'light' : 'dark'}.png',
          await capturePng(tester, key),
        );
        await tester.pumpWidget(const SizedBox());
      } finally {
        controller.dispose();
        ManagedServerRecovery.disposeForPreferences(prefs);
        messenger.setMockMethodCallHandler(channel, null);
        debugDefaultTargetPlatformOverride = null;
      }
    });
  }
}

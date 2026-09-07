import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show StreamStatus;
import '../../test/support/profile_monitor_fixture.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final dark in [false, true]) {
    testWidgets('profile monitor ${dark ? 'dark' : 'light'}', (tester) async {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
            (call) async =>
                call.method == 'readAll' ? <String, String>{} : null,
          );
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(
              const MethodChannel(
                'plugins.it_nomads.com/flutter_secure_storage',
              ),
              null,
            ),
      );
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final store = await monitorStore();
      await store.setActiveId('profile-2');
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) => (
          gateway: MonitorTestGateway(requests: [request(1)]),
          operations: MonitorTestOperations(),
        ),
      );
      controller
        ..api = (CaptureApi()..busy = {})
        ..repository = CaptureRepository()
        ..status = StreamStatus.connected
        ..directory = projectDirectory;
      try {
        await controller.profileMonitor.setEnabled('profile-1', true);
        await controller.profileMonitor.refresh();
        final boundary = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            boundaryKey: boundary,
            controller: controller,
            store: store,
            light: !dark,
            home: const HomeScreen(initialTab: 2),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
        expect(find.byType(HomeScreen), findsOneWidget);
        expect(find.byType(NavigationBar), findsOneWidget);
        expect(find.text('Saved-server attention'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/profile-monitor/${dark ? 'dark' : 'light'}.png',
          await capturePng(tester, boundary, pixelRatio: 1),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        controller.dispose();
        await tester.pump();
      }
      expect(tester.takeException(), isNull);
    });
  }
}

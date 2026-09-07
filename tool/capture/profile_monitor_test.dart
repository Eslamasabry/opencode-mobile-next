import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/profile_monitor_screen.dart';
import '../../test/support/profile_monitor_fixture.dart';
import 'fixtures.dart'
    show loadCaptureFonts, capturePng, captureTheme, writePng;

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
      final controller = ConnectionController(
        store,
        monitorGatewayFactory: (_) => (
          gateway: MonitorTestGateway(requests: [request(1)]),
          operations: MonitorTestOperations(),
        ),
      );
      try {
        await controller.profileMonitor.setEnabled('profile-1', true);
        await controller.profileMonitor.refresh();
        final boundary = GlobalKey();
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: captureTheme(light: !dark),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              home: Scaffold(
                appBar: AppBar(title: const Text('Activity')),
                body: SingleChildScrollView(
                  child: ProfileMonitorInbox(controller: controller),
                ),
              ),
            ),
          ),
        );
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 200));
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

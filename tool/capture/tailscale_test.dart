import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/screens/tailscale_setup_screen.dart';
import '../../test/support/profile_monitor_fixture.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final dark in [false, true]) {
    for (final scenario in [
      'installed',
      'missing',
      'narrow',
      'authentication',
    ]) {
      testWidgets('Tailscale $scenario ${dark ? 'dark' : 'light'}', (
        tester,
      ) async {
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        const native = MethodChannel('oc/tailscale');
        const secure = MethodChannel(
          'plugins.it_nomads.com/flutter_secure_storage',
        );
        messenger.setMockMethodCallHandler(
          native,
          (call) async => call.method == 'check'
              ? (scenario == 'missing' ? 'missing' : 'installed')
              : true,
        );
        messenger.setMockMethodCallHandler(
          secure,
          (call) async => call.method == 'readAll' ? <String, String>{} : null,
        );
        addTearDown(() {
          messenger.setMockMethodCallHandler(native, null);
          messenger.setMockMethodCallHandler(secure, null);
        });
        tester.view.physicalSize = Size(scenario == 'narrow' ? 320 : 390, 844);
        tester.view.devicePixelRatio = 1;
        tester.platformDispatcher.textScaleFactorTestValue =
            scenario == 'narrow' ? 2 : 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final store = await monitorStore(count: 0);
        final controller = ConnectionController(store);
        final boundary = GlobalKey();
        try {
          await tester.pumpWidget(
            captureApp(
              boundaryKey: boundary,
              controller: controller,
              store: store,
              light: !dark,
              home: scenario == 'authentication'
                  ? const ServersScreen()
                  : const TailscaleSetupScreen(
                      initialAddress: 'https://workstation.example.ts.net',
                    ),
            ),
          );
          await tester.pumpAndSettle();
          if (scenario == 'authentication') {
            await tester.ensureVisible(
              find.byKey(const ValueKey('welcome-tailscale-card')),
            );
            await tester.tap(
              find.byKey(const ValueKey('welcome-tailscale-card')),
            );
            await tester.pumpAndSettle();
            await tester.enterText(
              find.byType(TextField),
              'https://workstation.example.ts.net',
            );
            tester.testTextInput.hide();
            await tester.scrollUntilVisible(
              find.text('Continue to authentication'),
              220,
              scrollable: find.byType(Scrollable).first,
            );
            await tester.tap(find.text('Continue to authentication'));
            await tester.pumpAndSettle();
            tester.testTextInput.hide();
            await tester.pumpAndSettle();
            expect(
              find.byKey(const ValueKey('server-profile-editor')),
              findsOneWidget,
            );
          }
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/tailscale/$scenario-${dark ? 'dark' : 'light'}.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
          if (scenario == 'narrow') {
            await tester.scrollUntilVisible(
              find.text('Continue to authentication'),
              300,
              scrollable: find.byType(Scrollable).first,
            );
            await tester.pumpAndSettle();
            expect(tester.takeException(), isNull);
            await writePng(
              'docs/qa/tailscale/narrow-address-${dark ? 'dark' : 'light'}.png',
              await capturePng(tester, boundary, pixelRatio: 1),
            );
          }
        } finally {
          await tester.pumpWidget(const SizedBox());
          controller.dispose();
          await tester.pump();
        }
        expect(tester.takeException(), isNull);
      });
    }
  }
}

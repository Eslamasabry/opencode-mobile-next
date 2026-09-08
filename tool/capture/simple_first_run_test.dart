// Synthetic onboarding evidence; no saved user profile or network connection.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../test/support/setup_capture_preferences.dart';
import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  const storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  for (final light in [true, false]) {
    for (final scale in [1.0, 2.0]) {
      testWidgets('First run ${light ? 'light' : 'dark'} at ${scale}x', (
        tester,
      ) async {
        tester.view.physicalSize = const Size(1080, 2400);
        tester.view.devicePixelRatio = 2.625;
        tester.platformDispatcher.textScaleFactorTestValue = scale;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
        final messenger =
            TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
        messenger.setMockMethodCallHandler(storage, (_) async => null);
        addTearDown(() => messenger.setMockMethodCallHandler(storage, null));
        final prefs = await setupCapturePreferences();
        final store = SeededProfileStore(prefs: prefs, seeded: []);
        final controller = ConnectionController(store);
        addTearDown(controller.dispose);
        final boundary = GlobalKey();
        await tester.pumpWidget(
          captureApp(
            home: const ServersScreen(),
            boundaryKey: boundary,
            controller: controller,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Connect to a server'), findsOneWidget);
        expect(find.text('Try demo'), findsOneWidget);
        expect(tester.takeException(), isNull);
        final tone = light ? 'light' : 'dark';
        await writePng(
          'docs/qa/simple-first-run/welcome-$tone-${scale.toInt()}x.png',
          await capturePng(tester, boundary, pixelRatio: 2.625),
        );
        await tester.ensureVisible(find.text('Connect to a server'));
        await tester.tap(find.text('Connect to a server'));
        await tester.pumpAndSettle();
        expect(tester.testTextInput.isVisible, isFalse);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/simple-first-run/editor-$tone-${scale.toInt()}x.png',
          await capturePng(tester, boundary, pixelRatio: 2.625),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
        final savedStore = SeededProfileStore(
          prefs: prefs,
          seeded: [
            ServerProfile(
              id: 'laptop',
              name: 'Laptop',
              baseUrl: 'https://work.example',
            ),
          ],
        );
        final savedController = ConnectionController(savedStore);
        addTearDown(savedController.dispose);
        await tester.pumpWidget(
          captureApp(
            home: const ServersScreen(),
            boundaryKey: boundary,
            controller: savedController,
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text('Try demo'), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/simple-first-run/servers-$tone-${scale.toInt()}x.png',
          await capturePng(tester, boundary, pixelRatio: 2.625),
        );
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      });
    }
  }
}

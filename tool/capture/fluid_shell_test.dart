import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [false, true]) {
    for (final tab in [0, 1, 2, 3]) {
      testWidgets('fluid shell tab $tab ${light ? 'light' : 'dark'}', (
        tester,
      ) async {
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
        tester.view.physicalSize = const Size(390, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        SharedPreferences.setMockInitialValues({});
        final controller = await captureController(
          prefs: await SharedPreferences.getInstance(),
        );
        final boundary = GlobalKey();
        try {
          await tester.pumpWidget(
            captureApp(
              home: HomeScreen(initialTab: tab),
              boundaryKey: boundary,
              controller: controller,
              light: light,
            ),
          );
          await tester.pump();
          await tester.pump(const Duration(milliseconds: 500));
          expect(find.byType(NavigationBar), findsOneWidget);
          expect(tester.takeException(), isNull);
          await writePng(
            'docs/qa/fluid-shell/tab-$tab-${light ? 'light' : 'dark'}.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await tester.pump();
        }
      });
    }
  }
}

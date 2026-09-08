import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/home_screen.dart';
import '../../test/support/setup_capture_preferences.dart';

import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [false, true]) {
    for (final scenario in [
      for (final tab in [0, 1, 2, 3])
        (tab: tab, width: 390.0, scale: 1.0, contrast: false),
      (tab: 0, width: 320.0, scale: 2.5, contrast: false),
      (tab: 3, width: 320.0, scale: 2.5, contrast: false),
      (tab: 3, width: 390.0, scale: 1.0, contrast: true),
    ]) {
      final tab = scenario.tab;
      final name =
          'tab-$tab-${light ? 'light' : 'dark'}-${scenario.width.toInt()}-${scenario.scale}x${scenario.contrast ? '-contrast' : ''}';
      testWidgets('shell repair $name', (tester) async {
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
        tester.view.physicalSize = Size(scenario.width, 844);
        tester.view.devicePixelRatio = 1;
        addTearDown(tester.view.resetPhysicalSize);
        addTearDown(tester.view.resetDevicePixelRatio);
        final prefs = await setupCapturePreferences();
        final controller = await captureController(prefs: prefs);
        final boundary = GlobalKey();
        try {
          await tester.pumpWidget(
            captureApp(
              home: Builder(
                builder: (context) => MediaQuery(
                  data: MediaQuery.of(context).copyWith(
                    textScaler: TextScaler.linear(scenario.scale),
                    highContrast: scenario.contrast,
                  ),
                  child: HomeScreen(initialTab: tab),
                ),
              ),
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
            'docs/qa/shell-repair/$name.png',
            await capturePng(tester, boundary, pixelRatio: 1),
          );
          if (tab == 0 && scenario.scale == 1) {
            await tester.tap(find.byIcon(Icons.more_horiz_rounded));
            await tester.pump();
            await tester.pump(const Duration(milliseconds: 60));
            await writePng(
              'docs/qa/shell-repair/transition-${light ? 'light' : 'dark'}-60ms.png',
              await capturePng(tester, boundary, pixelRatio: 1),
            );
            await tester.pump(const Duration(milliseconds: 200));
            expect(tester.takeException(), isNull);
          }
        } finally {
          await tester.pumpWidget(const SizedBox.shrink());
          controller.dispose();
          await tester.pump();
        }
      });
    }
  }
}

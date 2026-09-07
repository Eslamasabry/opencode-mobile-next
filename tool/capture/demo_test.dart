// Captures the production offline demo route with its route-owned gateway.
// Run: flutter test --no-pub --concurrency=1 tool/capture/demo_test.dart
// Output: docs/qa/demo/{permission,complete}-{light,dark}.png.
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/demo/demo_copy.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/demo_screen.dart';

import 'fixtures.dart'
    show
        captureDevicePixelRatio,
        capturePng,
        captureTheme,
        loadCaptureFonts,
        writePng;

Future<void> _advance(WidgetTester tester) async {
  // A pending production turn animates until its permission is answered.
  for (var frame = 0; frame < 12; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final light in [true, false]) {
    testWidgets('production demo ${light ? 'light' : 'dark'}', (tester) async {
      tester.view.physicalSize = const Size(1170, 2532);
      tester.view.devicePixelRatio = captureDevicePixelRatio;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final boundary = GlobalKey();
      final variant = light ? 'light' : 'dark';
      try {
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: ProviderScope(
              child: MaterialApp(
                debugShowCheckedModeBanner: false,
                theme: captureTheme(light: light),
                localizationsDelegates: AppLocalizations.localizationsDelegates,
                supportedLocales: AppLocalizations.supportedLocales,
                builder: (context, child) => MediaQuery(
                  data: MediaQuery.of(
                    context,
                  ).copyWith(disableAnimations: true),
                  child: child!,
                ),
                home: const DemoScreen(),
              ),
            ),
          ),
        );
        await _advance(tester);
        await tester.tap(find.byKey(const Key('chat-send-button')));
        await _advance(tester);
        if (find.byKey(const Key('permission-sheet')).evaluate().isEmpty) {
          await tester.tap(find.byKey(const Key('permission-card-review')));
          await _advance(tester);
        }
        expect(find.byKey(const Key('permission-sheet')), findsOneWidget);
        expect(
          find.byKey(const Key('permission-diff-preview')),
          findsOneWidget,
        );
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/demo/permission-$variant.png',
          await capturePng(tester, boundary),
        );
        await tester.tap(find.byKey(const Key('permission-allow-once')));
        await _advance(tester);
        expect(
          find.textContaining(
            'You allowed the sample change.',
            findRichText: true,
          ),
          findsOneWidget,
        );
        expect(find.byTooltip(DemoCopy.exit), findsOneWidget);
        expect(tester.takeException(), isNull);
        await writePng(
          'docs/qa/demo/complete-$variant.png',
          await capturePng(tester, boundary),
        );
      } finally {
        await tester.pumpWidget(const SizedBox.shrink());
        await tester.pump();
      }
    });
  }
}

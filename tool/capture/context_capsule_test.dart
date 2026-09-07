// Real widgets with synthetic local content. No server or native picker runs.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/context_capsule.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/context_capsule_screen.dart';

import 'fixtures.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final mode in ['light', 'dark', 'narrow', 'rtl', 'text-only']) {
    testWidgets('context capsule $mode', (tester) async {
      final narrow = mode == 'narrow';
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = Size(narrow ? 320 : 390, 844);
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final scope = ValueNotifier(true);
      addTearDown(scope.dispose);
      final key = GlobalKey();
      await tester.pumpWidget(
        RepaintBoundary(
          key: key,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(light: mode != 'dark'),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(
                textScaler: TextScaler.linear(narrow ? 2 : 1),
                disableAnimations: true,
              ),
              child: Directionality(
                textDirection: mode == 'rtl'
                    ? TextDirection.rtl
                    : TextDirection.ltr,
                child: child!,
              ),
            ),
            home: ContextCapsuleScreen(
              sessionTitle: 'Fix mobile checkout',
              scopeChanges: scope,
              isCurrent: () => scope.value,
              pickImage: mode == 'text-only'
                  ? null
                  : (_) async => const PromptAttachment(
                      filename: 'checkout-error.png',
                      mime: 'image/png',
                      url:
                          'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=',
                    ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Error'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextField).last,
        'Payment timed out after returning from the bank app. The basket is still intact.',
      );
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      if (mode != 'text-only') {
        tester.testTextInput.hide();
        await tester.pumpAndSettle();
        await tester.scrollUntilVisible(
          find.text('Add screenshot or image'),
          200,
          scrollable: find
              .descendant(
                of: find.byType(ContextCapsuleScreen),
                matching: find.byType(Scrollable),
              )
              .first,
        );
        await tester.tap(find.text('Add screenshot or image'));
        await tester.pumpAndSettle();
      }
      final scroll = find
          .descendant(
            of: find.byType(ListView),
            matching: find.byType(Scrollable),
          )
          .first;
      tester.state<ScrollableState>(scroll).position.jumpTo(0);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/context-capsule-2026-09-08/$mode.png',
        await capturePng(tester, key),
      );
      tester.testTextInput.hide();
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('Apply to draft'),
        200,
        scrollable: find
            .descendant(
              of: find.byType(ContextCapsuleScreen),
              matching: find.byType(Scrollable),
            )
            .first,
      );
      await tester.pumpAndSettle();
      expect(find.text('Apply to draft').hitTestable(), findsOneWidget);
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/context-capsule-2026-09-08/$mode-review.png',
        await capturePng(tester, key),
      );
      await tester.pumpWidget(const SizedBox());
    });
  }
}

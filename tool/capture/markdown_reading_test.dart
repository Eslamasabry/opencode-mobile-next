// Actual renderer with local fixture content. No file/network actions.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/markdown.dart';
import 'fixtures.dart';

const _sample =
    '# Review the result\n\n'
    '| File | Status | Checks |\n| --- | :---: | ---: |\n'
    '| `checkout.dart` | Ready for review | 12 |\n'
    '| `payment_test.dart` | Passed the recorded checks | 8 |\n\n'
    '```dart\n'
    'Future<void> restoreCheckout() async {\n'
    '  final basket = await checkout.restoreDraftAndValidatePayment();\n'
    '  if (basket.isNotEmpty) showCheckout(basket);\n'
    '}\n```';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  for (final mode in ['light', 'dark', 'narrow', 'rtl']) {
    testWidgets('Markdown reading $mode', (tester) async {
      final narrow = mode == 'narrow';
      tester.view.physicalSize = Size(narrow ? 320 : 390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
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
            home: Scaffold(
              appBar: AppBar(title: const Text('Task reply')),
              body: const SingleChildScrollView(
                padding: EdgeInsets.all(16),
                child: MarkdownText(_sample),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/markdown-reading-2026-09-08/$mode-table.png',
        await capturePng(tester, key),
      );
      final tableScroll = find.ancestor(
        of: find.byType(DataTable),
        matching: find.byWidgetPredicate(
          (w) =>
              w is SingleChildScrollView &&
              w.scrollDirection == Axis.horizontal,
        ),
      );
      await tester.drag(tableScroll, Offset(mode == 'rtl' ? 700 : -700, 0));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/markdown-reading-2026-09-08/$mode-table-end.png',
        await capturePng(tester, key),
      );
      final tablePosition = tester
          .state<ScrollableState>(
            find
                .descendant(of: tableScroll, matching: find.byType(Scrollable))
                .first,
          )
          .position;
      tablePosition.jumpTo(tablePosition.minScrollExtent);
      await tester.pumpAndSettle();
      final scroll = find
          .byWidgetPredicate(
            (w) =>
                w is SingleChildScrollView &&
                w.scrollDirection == Axis.vertical,
          )
          .first;
      await tester.scrollUntilVisible(
        find.text('Wrap lines'),
        250,
        scrollable: find
            .descendant(of: scroll, matching: find.byType(Scrollable))
            .first,
      );
      await tester.tap(find.text('Wrap lines'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/markdown-reading-2026-09-08/$mode-wrap.png',
        await capturePng(tester, key),
      );
      await tester.ensureVisible(find.text('Full screen'));
      await tester.tap(find.text('Full screen'));
      await tester.pumpAndSettle();
      expect(find.text('Code reader'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await writePng(
        'docs/qa/markdown-reading-2026-09-08/$mode-reader.png',
        await capturePng(tester, key),
      );
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('Task reply'), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });
  }
}

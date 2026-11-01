// The frame never scales its words above 1.3x whatever the system font size;
// reading text keeps the full system scale.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Future<void> _pump(
  WidgetTester tester,
  Widget body, {
  Locale locale = const Locale('en'),
  double scale = 2.0,
  double width = 319,
}) async {
  tester.view.physicalSize = Size(width, 708);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQueryData(
          size: Size(width, 708),
          textScaler: TextScaler.linear(scale),
        ),
        child: child!,
      ),
      home: Scaffold(body: body),
    ),
  );
  await tester.pumpAndSettle();
}

Finder _words(String text) => find.byWidgetPredicate(
  (w) => w is RichText && w.text.toPlainText() == text,
);

double _scaleOf(WidgetTester tester, String text) =>
    tester.renderObject<RenderParagraph>(_words(text)).textScaler.scale(10) /
    10;

void main() {
  testWidgets('at 2x system text the top bar draws at 1.3x', (tester) async {
    await _pump(
      tester,
      Column(
        children: [
          const KitTopBar(title: 'Bar title', exit: KitTopBarExit.none),
          const KitText('A reply', role: KitTextRole.body),
        ],
      ),
    );
    expect(_scaleOf(tester, 'Bar title'), moreOrLessEquals(1.3));
    expect(_scaleOf(tester, 'A reply'), moreOrLessEquals(2.0));
  });

  testWidgets('at 2x system text a chip and a button draw at 1.3x', (
    tester,
  ) async {
    await _pump(
      tester,
      Column(
        children: [
          KitChip.action(
            label: 'Chip words',
            icon: AppIconography.shield,
            onPressed: () {},
          ),
          KitButton.primary(label: 'Button words', onPressed: () {}),
        ],
      ),
    );
    expect(_scaleOf(tester, 'Chip words'), moreOrLessEquals(1.3));
    expect(_scaleOf(tester, 'Button words'), moreOrLessEquals(1.3));
  });

  testWidgets('a sheet header draws at 1.3x', (tester) async {
    await _pump(
      tester,
      Builder(
        builder: (context) => TextButton(
          onPressed: () => showKitSheet<void>(
            context,
            title: 'Sheet title',
            body: (_) => const KitText('Sheet body', role: KitTextRole.body),
          ),
          child: const Text('open'),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(_scaleOf(tester, 'Sheet title'), moreOrLessEquals(1.3));
    expect(_scaleOf(tester, 'Sheet body'), moreOrLessEquals(2.0));
  });

  testWidgets('below the cap the frame follows the system', (tester) async {
    await _pump(
      tester,
      KitChip.action(label: 'Chip words', onPressed: () {}),
      scale: 1.15,
    );
    expect(_scaleOf(tester, 'Chip words'), moreOrLessEquals(1.15));
  });

  for (final (locale, label) in const [
    (Locale('en'), 'Ask first'),
    (Locale('ar'), 'اسأل أولًا'),
  ]) {
    testWidgets('a chip label is never cut at 2x (${locale.languageCode})', (
      tester,
    ) async {
      await _pump(
        tester,
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: KitChip.action(
            label: label,
            icon: AppIconography.shield,
            onPressed: () {},
          ),
        ),
        locale: locale,
      );
      final render = tester.renderObject<RenderParagraph>(_words(label));
      expect(render.didExceedMaxLines, isFalse);
    });
  }
}

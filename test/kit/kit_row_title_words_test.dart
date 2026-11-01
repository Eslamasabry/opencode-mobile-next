// A row title never breaks inside a word to make room for a trailing value:
// where the two do not share a line, the value goes under the title.
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

Future<void> _pump(
  WidgetTester tester, {
  required String title,
  required String value,
  required double scale,
  Locale locale = const Locale('en'),
}) async {
  tester.view.physicalSize = const Size(360, 800);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.light(),
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(scale)),
        child: child!,
      ),
      home: Scaffold(
        body: KitRow(
          title: title,
          titleKey: const Key('title'),
          trailing: KitRowValue(value, prominent: true),
          onTap: () {},
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

int _lines(WidgetTester tester, String title) {
  final render = tester.renderObject<RenderParagraph>(
    find.descendant(
      of: find.byKey(const Key('title')),
      matching: find.byType(RichText),
    ),
  );
  final one = TextPainter(
    text: render.text,
    textDirection: render.textDirection,
    textScaler: render.textScaler,
  )..layout();
  final lines = (render.size.height / one.height).round();
  one.dispose();
  return lines;
}

void main() {
  for (final (locale, title) in const [
    (Locale('en'), 'Project'),
    (Locale('ar'), 'المشروع'),
  ]) {
    testWidgets('at 2x ${locale.languageCode} the title keeps its word whole '
        'and the value goes under it', (tester) async {
      await _pump(
        tester,
        title: title,
        value: 'shopfront',
        scale: 2,
        locale: locale,
      );
      expect(_lines(tester, title), 1, reason: 'the word is not broken');
      final titleRect = tester.getRect(find.byKey(const Key('title')));
      final value = tester.getRect(find.text('shopfront'));
      expect(value.top, greaterThanOrEqualTo(titleRect.bottom - 1));
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('at 1x the value stays beside the title', (tester) async {
    await _pump(tester, title: 'Project', value: 'shopfront', scale: 1);
    final title = tester.getRect(find.byKey(const Key('title')));
    final value = tester.getRect(find.text('shopfront'));
    expect(value.center.dy, moreOrLessEquals(title.center.dy, epsilon: 2));
  });
}

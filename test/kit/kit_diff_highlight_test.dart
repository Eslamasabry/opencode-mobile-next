// KitDiffView syntax colour (issue #26): a foreground tint only, readable on
// the plain, added and removed row backgrounds in every theme pack and both
// brightnesses; unknown languages and very long files stay plain.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_diff_view.dart';
import 'package:opencode_mobile/ui/kit/kit_tokens.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

const _before = '''
class A {
  // the name, kept
  final String name = 'one';
  int count() => 1; // old name
}''';
const _after = '''
class A {
  // the name, kept
  final String name = 'two';
  // changed note
  int count() => 2; // new name
}''';

Widget _app(ThemeData theme, KitDiffFile file) => MaterialApp(
  theme: theme,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: KitDiffView(files: [file])),
);

Future<void> _pump(WidgetTester tester, ThemeData theme, KitDiffFile f) async {
  tester.view
    ..physicalSize = const Size(900, 900)
    ..devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(_app(theme, f));
  await tester.pump();
}

void _colors(InlineSpan span, Color inherited, Set<Color> out) {
  if (span is! TextSpan) return;
  final color = span.style?.color ?? inherited;
  if (span.text != null && span.text!.trim().isNotEmpty) out.add(color);
  for (final child in span.children ?? const <InlineSpan>[]) {
    _colors(child, color, out);
  }
}

/// The colours the code text of every row paints in.
Set<Color> _codeColors(WidgetTester tester) {
  final out = <Color>{};
  for (final rich in tester.widgetList<RichText>(find.byType(RichText))) {
    final text = rich.text.toPlainText();
    if (text.contains('name') || text.contains('count') || text.contains('x')) {
      _colors(rich.text, const Color(0x00000000), out);
    }
  }
  return out;
}

void main() {
  testWidgets('every theme pack and brightness keeps syntax colour at 4.5:1 '
      'on plain, added and removed rows', (tester) async {
    final file = KitDiffFile.fromTexts(
      'lib/a.dart',
      before: _before,
      after: _after,
    );
    final failures = <String>[];
    for (final id in ThemePackId.values.where(
      (i) => i != ThemePackId.dynamic,
    )) {
      for (final dark in [true, false]) {
        final pack = themePack(id);
        final theme = dark ? AppTheme.dark(pack) : AppTheme.light(pack);
        await _pump(tester, theme, file);
        final tokens = KitTokens.of(tester.element(find.byType(KitDiffView)));
        final roles = tokens.roles;
        final surface = tokens.detailsSurface;
        final grounds = [
          surface,
          Color.alphaBlend(roles.codeAddedSurface, surface),
          Color.alphaBlend(roles.codeRemovedSurface, surface),
        ];
        final colors = _codeColors(tester);
        expect(colors.length, greaterThan(2), reason: '${id.name} has colour');
        for (final c in colors) {
          for (final g in grounds) {
            final ratio = contrastRatio(Color.alphaBlend(c, g), g);
            if (ratio < 4.5) {
              failures.add(
                '${id.name}/${dark ? 'dark' : 'light'} '
                '$c on $g ${ratio.toStringAsFixed(2)}',
              );
            }
          }
        }
      }
    }
    expect(failures, isEmpty);
  });

  testWidgets('an unknown language stays plain', (tester) async {
    final file = KitDiffFile.fromTexts(
      'notes.zzz',
      before: _before,
      after: _after,
    );
    await _pump(tester, AppTheme.dark(), file);
    expect(_codeColors(tester).length, 1);
  });

  testWidgets('a file over 2,000 lines is plain, with no notice', (
    tester,
  ) async {
    final after = [for (var i = 0; i < 2100; i++) "final name$i = 'x';"];
    final file = KitDiffFile.fromTexts('big.dart', after: after.join('\n'));
    await _pump(tester, AppTheme.dark(), file);
    expect(_codeColors(tester).length, 1);
  });

  testWidgets('a dart file is highlighted', (tester) async {
    final file = KitDiffFile.fromTexts(
      'lib/a.dart',
      before: _before,
      after: _after,
    );
    await _pump(tester, AppTheme.dark(), file);
    expect(_codeColors(tester).length, greaterThan(2));
  });
}

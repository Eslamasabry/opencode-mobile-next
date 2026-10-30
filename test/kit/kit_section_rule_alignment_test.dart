// The section rule (Paper's ink line above a section's label) starts where
// the label's words start and where the section's content starts, in LTR
// and RTL: on a page, in a sheet's padding, above a row group, above a lazy
// row group, with a trailing part and with an explained label.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

Widget _app(Widget body, {required TextDirection direction}) => MaterialApp(
  theme: AppTheme.light(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: KitEffectsScope(
    effects: KitSurfaceLook.choose(
      const KitEffects(liveBackground: false),
      KitSurfaceStyle.paper,
    ),
    child: kitStyleTheme(
      child: Directionality(
        textDirection: direction,
        child: Scaffold(body: body),
      ),
    ),
  ),
);

/// The painted section rules, start to end, in screen order.
List<Rect> _rules(WidgetTester tester) => [
  for (final e
      in find
          .descendant(
            of: find.byType(KitSectionLabel),
            matching: find.byKey(const ValueKey('kit-divider-line')),
          )
          .evaluate())
    tester.getRect(find.byWidget(e.widget)),
];

/// Where [text]'s words start on the line: the left edge in LTR, the right
/// edge in RTL.
double _wordsStart(WidgetTester tester, String text, TextDirection d) {
  final r = tester.getRect(find.text(text).first);
  return d == TextDirection.ltr ? r.left : r.right;
}

double _ruleStart(Rect r, TextDirection d) =>
    d == TextDirection.ltr ? r.left : r.right;

double _ruleEnd(Rect r, TextDirection d) =>
    d == TextDirection.ltr ? r.right : r.left;

void main() {
  for (final d in TextDirection.values) {
    group('Paper section rule, ${d.name}', () {
      testWidgets('on a page: from the gutter, like the words and the box '
          'under it', (tester) async {
        await tester.pumpWidget(
          _app(
            const SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(height: 40),
                  KitSectionLabel('Themes'),
                  Padding(
                    padding: EdgeInsets.symmetric(horizontal: 16),
                    child: SizedBox(key: ValueKey('box'), height: 40),
                  ),
                  KitSectionLabel(
                    'Providers',
                    explanation: 'Where the models come from.',
                  ),
                  KitSectionLabel(
                    'Queued',
                    trailing: KitText('3 waiting', role: KitTextRole.caption),
                  ),
                ],
              ),
            ),
            direction: d,
          ),
        );
        await tester.pumpAndSettle();
        final rules = _rules(tester);
        expect(rules, hasLength(3), reason: 'Paper rules every section');
        final box = tester.getRect(find.byKey(const ValueKey('box')));
        final boxStart = d == TextDirection.ltr ? box.left : box.right;
        final boxEnd = d == TextDirection.ltr ? box.right : box.left;
        for (final (i, text) in ['Themes', 'Providers', 'Queued'].indexed) {
          expect(
            _ruleStart(rules[i], d),
            moreOrLessEquals(_wordsStart(tester, text, d), epsilon: 0.5),
            reason: '"$text": the rule starts where its words do',
          );
          expect(_ruleStart(rules[i], d), moreOrLessEquals(boxStart));
          expect(_ruleEnd(rules[i], d), moreOrLessEquals(boxEnd));
        }
      });

      testWidgets('in a sheet\'s padding (inline)', (tester) async {
        await tester.pumpWidget(
          _app(
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(height: 40),
                  KitSectionLabel.inline('Model', gapBefore: 22),
                ],
              ),
            ),
            direction: d,
          ),
        );
        await tester.pumpAndSettle();
        final rule = _rules(tester).single;
        expect(
          _ruleStart(rule, d),
          moreOrLessEquals(_wordsStart(tester, 'Model', d), epsilon: 0.5),
        );
        expect(_ruleStart(rule, d), d == TextDirection.ltr ? 24 : 800 - 24);
      });

      testWidgets('above a row group and a lazy row group, whose rows reach '
          'into the margin', (tester) async {
        await tester.pumpWidget(
          _app(
            CustomScrollView(
              slivers: [
                const SliverToBoxAdapter(
                  child: KitRowGroup(
                    label: 'Notifications',
                    gapBefore: 22,
                    children: [
                      KitRow(title: 'Sound', leading: Icon(Icons.abc)),
                      KitRow(title: 'Badge', leading: Icon(Icons.abc)),
                    ],
                  ),
                ),
                KitSliverRowGroup(
                  label: 'Earlier',
                  itemCount: 2,
                  itemBuilder: (_, i) => KitRow(title: 'Row $i'),
                ),
              ],
            ),
            direction: d,
          ),
        );
        await tester.pumpAndSettle();
        final rules = _rules(tester);
        expect(rules, hasLength(2));
        for (final (i, text) in ['Notifications', 'Earlier'].indexed) {
          expect(
            _ruleStart(rules[i], d),
            moreOrLessEquals(_wordsStart(tester, text, d), epsilon: 0.5),
            reason: '"$text": the rule starts where its words do',
          );
          expect(
            _ruleStart(rules[i], d),
            d == TextDirection.ltr ? 16 : 800 - 16,
          );
        }
      });
    });
  }
}

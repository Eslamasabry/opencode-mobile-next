// The fields ask the style resolver (style cleanup slice S4,
// docs/audits/style-coherence-2026-10-21.md §6): KitField, KitSearchField
// and KitAskField paint `KitSurfaceRole.field`, the composer's chips a
// `control` and its suggestion panel a `content` surface. Solid keeps the
// parts' own frames; a field's edge reads at 3:1 at rest in Solid too
// (several lines: owner, 2026-10-21; one line: audit P2-6, 2026-10-28).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';

Widget _host(
  Widget child, {
  KitSurfaceStyle style = KitSurfaceStyle.solid,
  bool light = false,
}) => MaterialApp(
  debugShowCheckedModeBanner: false,
  theme: light ? AppTheme.light() : AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: KitEffectsScope(
    effects: KitEffects(surfaceStyle: style),
    child: kitStyleTheme(
      child: Scaffold(
        body: Padding(padding: const EdgeInsets.all(16), child: child),
      ),
    ),
  ),
);

/// The decorations painted under [of] (its frame, rings and the like).
Iterable<Decoration> _decorations(WidgetTester tester, Finder of) => tester
    .widgetList<DecoratedBox>(
      find.descendant(of: of, matching: find.byType(DecoratedBox)),
    )
    .map((box) => box.decoration);

/// The rings drawn just outside a frame: a foreground border, outside.
Iterable<BoxBorder> _outerRings(WidgetTester tester, Finder of) => tester
    .widgetList<DecoratedBox>(
      find.descendant(of: of, matching: find.byType(DecoratedBox)),
    )
    .where((box) => box.position == DecorationPosition.foreground)
    .map((box) => box.decoration)
    .whereType<BoxDecoration>()
    .map((d) => d.border)
    .whereType<Border>()
    .where((b) => b.top.strokeAlign == BorderSide.strokeAlignOutside);

void main() {
  for (final light in [false, true]) {
    testWidgets('Solid: a several-line field reads at 3:1 at rest '
        '(${light ? 'light' : 'dark'})', (tester) async {
      await tester.pumpWidget(
        _host(
          const KitField(
            label: 'Anything to add?',
            kind: KitFieldKind.multiline,
          ),
          light: light,
        ),
      );
      final roles = KitTokens.of(tester.element(find.byType(KitField))).roles;
      final frame = _decorations(
        tester,
        find.byType(KitField),
      ).whereType<BoxDecoration>().firstWhere((d) => d.color == roles.surface1);
      final edge = (frame.border! as Border).top;
      expect(
        edge.width,
        KitTokens.focusRingWidth(tester.element(find.byType(KitField))),
      );
      for (final against in [roles.ground, roles.surface1]) {
        expect(
          contrastRatio(edge.color, against),
          greaterThanOrEqualTo(3),
          reason: 'edge against $against',
        );
      }
      // No ring of its own: Solid draws focus in its edge.
      expect(_outerRings(tester, find.byType(KitField)), isEmpty);
    });
  }

  for (final light in [false, true]) {
    testWidgets('Solid: a one-line field reads at 3:1 at rest, two pixels '
        'wide (${light ? 'light' : 'dark'})', (tester) async {
      await tester.pumpWidget(
        _host(const KitField(label: 'Name'), light: light),
      );
      final context = tester.element(find.byType(KitField));
      final roles = KitTokens.of(context).roles;
      final frame = _decorations(
        tester,
        find.byType(KitField),
      ).whereType<BoxDecoration>().firstWhere((d) => d.color == roles.surface1);
      final edge = (frame.border! as Border).top;
      expect(edge.width, KitTokens.focusRingWidth(context));
      for (final against in [roles.ground, roles.surface1]) {
        expect(
          contrastRatio(edge.color, against),
          greaterThanOrEqualTo(3),
          reason: 'edge against $against',
        );
      }
    });
  }

  for (final style in KitSurfaceStyle.values.where(
    (s) => s != KitSurfaceStyle.solid,
  )) {
    testWidgets('${style.name}: every field is the style\'s field, and a '
        'focused one is ringed outside', (tester) async {
      final ask = TextEditingController();
      final focus = FocusNode();
      addTearDown(ask.dispose);
      addTearDown(focus.dispose);
      await tester.pumpWidget(
        _host(
          style: style,
          Column(
            children: [
              const KitField(label: 'Name', autofocus: true),
              KitSearchField(label: 'Search', onChanged: (_) {}),
              KitAskField(
                controller: ask,
                focusNode: focus,
                hint: 'Ask for a change…',
                sendLabel: 'Send',
                onSend: () {},
              ),
            ],
          ),
        ),
      );
      await tester.pump();
      for (final part in [
        find.byType(KitField),
        find.byType(KitSearchField),
        find.byType(KitAskField),
      ]) {
        expect(
          _decorations(tester, part).whereType<KitSurfaceDecoration>(),
          isNotEmpty,
          reason: '$part paints through the resolver',
        );
      }
      expect(_outerRings(tester, find.byType(KitField)), hasLength(1));
      expect(_outerRings(tester, find.byType(KitSearchField)), isEmpty);
    });
  }

  testWidgets('Paper squares the prominent search pill; Solid keeps it '
      'round', (tester) async {
    BorderRadius? radiusIn(WidgetTester tester) {
      final looks = _decorations(tester, find.byType(KitSearchField));
      for (final d in looks) {
        if (d is KitSurfaceDecoration) return d.borderRadius;
        if (d is BoxDecoration && d.color != null) {
          return d.borderRadius as BorderRadius?;
        }
      }
      return null;
    }

    final field = KitSearchField(
      label: 'Search settings',
      onChanged: (_) {},
      prominent: true,
    );
    await tester.pumpWidget(_host(field));
    final tokens = KitTokens.of(tester.element(find.byType(KitSearchField)));
    expect(radiusIn(tester), BorderRadius.circular(tokens.buttonHeight / 2));
    await tester.pumpWidget(_host(field, style: KitSurfaceStyle.paper));
    await tester.pump(const Duration(milliseconds: 300));
    expect(radiusIn(tester), BorderRadius.zero);
  });

  testWidgets('Soft: the composer chips and the suggestion panel ask the '
      'resolver', (tester) async {
    await tester.pumpWidget(
      _host(
        style: KitSurfaceStyle.soft,
        Column(
          children: [
            KitComposerChips.model(label: 'Opus 5.5', onPressed: () {}),
            KitComposerChips.suggestions(
              suggestions: const [
                KitSuggestion(
                  id: 'review',
                  label: '/review',
                  kind: KitSuggestionKind.command,
                ),
              ],
              onSelected: (_) {},
            ),
          ],
        ),
      ),
    );
    final chips = find.byType(KitComposerChips);
    expect(
      _decorations(tester, chips.first).whereType<KitSurfaceDecoration>(),
      isNotEmpty,
    );
    expect(
      _decorations(tester, chips.last).whereType<KitSurfaceDecoration>(),
      isNotEmpty,
    );
  });
}

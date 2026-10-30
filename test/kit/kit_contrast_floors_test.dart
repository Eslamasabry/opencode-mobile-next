// The kit's contrast floors for disabled and resting controls (Android
// audit 2026-10-28, P2-5 and P2-6), measured in every Style, both modes and
// every theme pack:
// - the waiting hero's words read at 4.5:1 on its quiet mint (the floor
//   `ThemeRoles.text3` sets for the words on a disabled button);
// - a field's edge reads at 3:1 at rest against the page and its own fill
//   (WCAG 1.4.11), a one-line field as well as a several-line one.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

/// Every theme the app ships but the device's own colours, by name.
Map<String, ThemeData> _themes() => {
  for (final id in ThemePackId.values)
    if (id != ThemePackId.dynamic)
      for (final b in Brightness.values)
        '${id.name} ${b.name}': b == Brightness.dark
            ? AppTheme.dark(themePack(id))
            : AppTheme.light(themePack(id)),
};

Widget _host(Widget child, ThemeData theme, KitSurfaceStyle style) =>
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: theme,
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

Iterable<Decoration> _decorations(WidgetTester tester, Finder of) => tester
    .widgetList<DecoratedBox>(
      find.descendant(of: of, matching: find.byType(DecoratedBox)),
    )
    .map((box) => box.decoration);

void main() {
  final themes = _themes();

  test('the waiting hero\'s words read at 4.5:1 on its mint, and the mint '
      'stays quiet, in every style, mode and pack', () {
    final fails = <String>[];
    for (final style in KitSurfaceStyle.values) {
      for (final MapEntry(key: name, value: theme) in themes.entries) {
        final roles = ThemeRoles.resolve(kitStyleRestyled(theme, style));
        final under = roles.ground.withValues(alpha: 1);
        final fill = Color.alphaBlend(kitHeroWaitingFill(roles), under);
        final words = kitHeroWaitingWords(roles);
        final ratio = contrastRatio(words, fill);
        if (ratio < 4.5) {
          fails.add(
            '${style.name} $name: words ${ratio.toStringAsFixed(2)}:1 < 4.5',
          );
        }
        // Not ready: a quiet face, far from the ready hero's accent.
        final page = Color.alphaBlend(roles.surface1, under);
        final quiet = contrastRatio(fill, page);
        if (quiet > 1.5) {
          fails.add('${style.name} $name: mint ${quiet.toStringAsFixed(2)}');
        }
      }
    }
    expect(fails, isEmpty, reason: fails.join('\n'));
  });

  for (final style in KitSurfaceStyle.values) {
    for (final light in [false, true]) {
      final mode = light ? 'light' : 'dark';
      testWidgets('${style.name} $mode: the waiting hero is a plain '
          'disabled button in Solid and its quiet mint elsewhere, with '
          'readable words', (tester) async {
        await tester.pumpWidget(
          _host(
            const KitButton.primary(
              label: 'Start with OpenCode',
              hero: true,
              onPressed: null,
            ),
            light ? AppTheme.light() : AppTheme.dark(),
            style,
          ),
        );
        final context = tester.element(find.byType(KitButton));
        final roles = KitTokens.of(context).roles;
        final words = tester
            .widget<DefaultTextStyle>(
              find
                  .ancestor(
                    of: find.text('Start with OpenCode'),
                    matching: find.byType(DefaultTextStyle),
                  )
                  .first,
            )
            .style
            .color;
        // Unslop 2026-10-30: Solid's waiting hero is the standard disabled
        // look (surface3, text3); the accent appears once it can be pressed.
        final solid = style == KitSurfaceStyle.solid;
        expect(words, solid ? roles.text3 : kitHeroWaitingWords(roles));
        final fill = solid ? roles.surface3 : kitHeroWaitingFill(roles);
        final painted = style == KitSurfaceStyle.solid
            ? tester
                  .widgetList<Material>(
                    find.descendant(
                      of: find.byType(KitButton),
                      matching: find.byType(Material),
                    ),
                  )
                  .map((m) => m.color)
            : _decorations(
                tester,
                find.byType(KitButton),
              ).whereType<KitSurfaceDecoration>().map((d) => d.fill);
        expect(painted, contains(fill));
      });
    }
  }

  testWidgets('a field\'s edge reads at 3:1 at rest, one line or several, '
      'in every style, mode and pack', (tester) async {
    final fails = <String>[];
    for (final style in KitSurfaceStyle.values) {
      for (final MapEntry(key: name, value: theme) in themes.entries) {
        for (final kind in [KitFieldKind.text, KitFieldKind.multiline]) {
          // A fresh tree each time: the effects scope keeps its first style.
          await tester.pumpWidget(const SizedBox());
          await tester.pumpWidget(
            _host(KitField(label: 'Server password', kind: kind), theme, style),
          );
          final roles = KitTokens.of(
            tester.element(find.byType(KitField)),
          ).roles;
          final under = roles.ground.withValues(alpha: 1);
          final frames = _decorations(tester, find.byType(KitField));
          final Color face;
          final Color edge;
          final double width;
          if (style == KitSurfaceStyle.solid) {
            final frame = frames.whereType<BoxDecoration>().firstWhere(
              (d) => d.color == roles.surface1 && d.border != null,
            );
            final side = (frame.border! as Border).top;
            face = Color.alphaBlend(frame.color!, under);
            edge = Color.alphaBlend(side.color, face);
            // Two pixels: one pixel between two pixel rows blurs to about
            // 1.3:1.
            final two = KitTokens.focusRingWidth(
              tester.element(find.byType(KitField)),
            );
            width = side.width >= two ? side.width : 0;
          } else {
            final frame = frames.whereType<KitSurfaceDecoration>().first;
            face = Color.alphaBlend(frame.fill, under);
            edge = Color.alphaBlend(frame.edgeStart, face);
            width = frame.edgeWidth;
          }
          final outside = contrastRatio(edge, under);
          final inside = contrastRatio(edge, face);
          if (width <= 0 || outside < 3 || inside < 3) {
            fails.add(
              '${style.name} $name ${kind.name}: $width dp, '
              '${outside.toStringAsFixed(2)} out, '
              '${inside.toStringAsFixed(2)} in',
            );
          }
        }
      }
    }
    expect(fails, isEmpty, reason: fails.join('\n'));
  });
}

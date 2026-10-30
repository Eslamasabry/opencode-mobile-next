// Fix group A of the style re-check (docs/audits/
// style-coherence-recheck-2026-10-21.md §6):
// - F1: every part that tells the resolver a face is chosen reads its words
//   through `KitSurfaceLook.wordsOn` with the same state, so a style that
//   sets a chosen face in reverse (Paper's ink) keeps them legible (N1);
// - F4: the composer's `/` suggestions sit on the composer's face, with no
//   second box, and a row is highlighted only while it is active (N4);
// - F9: a control nested in the ask field casts nothing in a style that
//   flattens nested surfaces (N9, Brutal's mic).
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_persona.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/kit/terminal_key_bar.dart';

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
        body: SingleChildScrollView(
          padding: const EdgeInsets.all(16),
          child: child,
        ),
      ),
    ),
  ),
);

/// A made-up style whose chosen faces take [chosen] words, standing in for
/// any style that changes a chosen face's colour.
class _ChosenInkSpec extends KitStyleSpec {
  const _ChosenInkSpec();

  static const chosen = Color(0xFF00AA44);

  @override
  KitStyleTokens get tokens => const KitStyleTokens(defaults: KitKnobs());

  @override
  Decoration resolve(KitSurfaceRequest r) =>
      KitSurfaceDecoration(borderRadius: r.radius, fill: r.levelFill);

  @override
  Color? onColor(KitSurfaceRequest r) => r.selected ? chosen : null;
}

/// The colour [text] is painted in.
Color? _ink(WidgetTester tester, String text) =>
    tester.widget<Text>(find.text(text)).style?.color;

const _choices = [
  KitChoice(value: 'a', title: 'Default', supporting: 'Claude Sonnet 5.5'),
  KitChoice(value: 'b', title: 'Cheapest', supporting: 'Big Pickle'),
];

void main() {
  group('F1: words on a chosen face come from the style', () {
    setUp(
      () =>
          debugKitStyleSpecs = (style) =>
              style == KitSurfaceStyle.soft ? const _ChosenInkSpec() : null,
    );
    tearDown(() => debugKitStyleSpecs = null);

    testWidgets('KitChoiceTiles', (tester) async {
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.soft,
          KitChoiceTiles<String>(
            choices: _choices,
            selected: 'a',
            onSelected: (_) {},
          ),
        ),
      );
      expect(_ink(tester, 'Default'), _ChosenInkSpec.chosen);
      expect(_ink(tester, 'Claude Sonnet 5.5'), _ChosenInkSpec.chosen);
      expect(_ink(tester, 'Cheapest'), isNot(_ChosenInkSpec.chosen));
    });

    testWidgets('KitChoiceCards', (tester) async {
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.soft,
          KitChoiceCards<String>(
            choices: _choices,
            selected: 'a',
            onSelected: (_) {},
          ),
        ),
      );
      expect(_ink(tester, 'Default'), _ChosenInkSpec.chosen);
      expect(_ink(tester, 'Claude Sonnet 5.5'), _ChosenInkSpec.chosen);
      expect(_ink(tester, 'Cheapest'), isNot(_ChosenInkSpec.chosen));
    });

    testWidgets('KitAgentChoice', (tester) async {
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.soft,
          Column(
            children: [
              KitAgentChoice(
                persona: AgentPersona.other,
                otherName: 'Chosen',
                state: KitAgentChoiceState.selected,
                onPressed: () {},
              ),
              KitAgentChoice(
                persona: AgentPersona.other,
                otherName: 'Resting',
                state: KitAgentChoiceState.available,
                onPressed: () {},
              ),
            ],
          ),
        ),
      );
      expect(_ink(tester, 'Chosen'), _ChosenInkSpec.chosen);
      expect(_ink(tester, 'Resting'), isNot(_ChosenInkSpec.chosen));
    });

    testWidgets('KitWheel: the value in the band', (tester) async {
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.soft,
          KitWheel(
            columns: [
              KitWheelColumn<int>(
                values: const [1, 2, 3],
                selected: 2,
                label: (v) => 'v$v',
                onChanged: (_) {},
                semanticsLabel: 'Value',
              ),
            ],
          ),
        ),
      );
      final chosen = tester
          .widget<AnimatedDefaultTextStyle>(
            find
                .ancestor(
                  of: find.text('v2'),
                  matching: find.byType(AnimatedDefaultTextStyle),
                )
                .first,
          )
          .style;
      expect(chosen.color, _ChosenInkSpec.chosen);
    });

    testWidgets('TerminalKeyBar: a latched key', (tester) async {
      final keys = TerminalKeyBarController();
      addTearDown(keys.dispose);
      keys.toggle(TerminalBarKey.ctrl);
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.soft,
          TerminalKeyBar(
            controller: keys,
            onKey: (_, {required ctrl, required alt}) {},
          ),
        ),
      );
      Color? faceOf(String key) => tester
          .widget<Text>(
            find.descendant(
              of: find.byKey(ValueKey('terminal-key-$key')),
              matching: find.byType(Text),
            ),
          )
          .style
          ?.color;
      expect(faceOf('ctrl'), _ChosenInkSpec.chosen);
      expect(faceOf('left'), isNot(_ChosenInkSpec.chosen));
    });
  });

  for (final light in [true, false]) {
    testWidgets('Paper: the chosen tile\'s words read at AA on its ink '
        '(${light ? 'light' : 'dark'})', (tester) async {
      await tester.pumpWidget(
        _host(
          style: KitSurfaceStyle.paper,
          light: light,
          KitChoiceTiles<String>(
            choices: _choices,
            selected: 'a',
            onSelected: (_) {},
          ),
        ),
      );
      final roles = KitTokens.of(tester.element(find.text('Default'))).roles;
      // Paper sets a chosen control in reverse: the ink is its face.
      final face = roles.text1.withValues(alpha: 1);
      for (final words in ['Default', 'Claude Sonnet 5.5']) {
        expect(
          contrastRatio(_ink(tester, words)!, face),
          greaterThanOrEqualTo(4.5),
          reason: words,
        );
      }
    });
  }

  group('F4: the suggestions sit on the composer\'s face', () {
    Widget panel() => KitComposerChips.suggestions(
      suggestions: const [
        KitSuggestion(
          id: 'review',
          label: '/review',
          kind: KitSuggestionKind.command,
        ),
        KitSuggestion(
          id: 'copy',
          label: '/copy',
          kind: KitSuggestionKind.command,
        ),
      ],
      onSelected: (_) {},
    );

    Iterable<Decoration> decorations(WidgetTester tester) => tester
        .widgetList<DecoratedBox>(
          find.descendant(
            of: find.byType(KitComposerChips),
            matching: find.byType(DecoratedBox),
          ),
        )
        .map((box) => box.decoration);

    for (final style in [
      KitSurfaceStyle.brutal,
      KitSurfaceStyle.paper,
      KitSurfaceStyle.soft,
      KitSurfaceStyle.soft,
    ]) {
      testWidgets('${style.name}: no box of its own, the rows one group', (
        tester,
      ) async {
        await tester.pumpWidget(_host(style: style, light: true, panel()));
        await tester.pump(const Duration(milliseconds: 300));
        final faces = decorations(tester).whereType<KitSurfaceDecoration>();
        expect(faces, isNotEmpty);
        for (final face in faces) {
          expect(KitSurfaceDecoration.isBare(face), isTrue, reason: '$face');
        }
        expect(
          tester
              .widgetList<KitDivider>(find.byType(KitDivider))
              .map((d) => d.inset),
          everyElement(KitDividerInset.gutter),
        );
      });
    }

    testWidgets('Solid keeps its own panel and edge-to-edge lines', (
      tester,
    ) async {
      await tester.pumpWidget(_host(panel()));
      final roles = KitTokens.of(
        tester.element(find.byType(KitComposerChips)),
      ).roles;
      expect(
        decorations(tester).whereType<ShapeDecoration>().map((d) => d.color),
        contains(roles.surface2),
      );
      expect(
        tester
            .widgetList<KitDivider>(find.byType(KitDivider))
            .map((d) => d.inset),
        everyElement(KitDividerInset.none),
      );
    });
  });

  testWidgets('F9: Brutal\'s mic casts nothing inside the ask field', (
    tester,
  ) async {
    final controller = TextEditingController();
    final focus = FocusNode();
    addTearDown(controller.dispose);
    addTearDown(focus.dispose);
    await tester.pumpWidget(
      _host(
        style: KitSurfaceStyle.brutal,
        light: true,
        KitAskField(
          voiceKey: const ValueKey('voice'),
          controller: controller,
          focusNode: focus,
          hint: 'Ask for a change…',
          sendLabel: 'Send',
          voiceLabel: 'Speak',
          onSend: () {},
          onVoice: () {},
        ),
      ),
    );
    final discs = tester
        .widgetList<AnimatedContainer>(
          find.descendant(
            of: find.byKey(const ValueKey('voice')),
            matching: find.byType(AnimatedContainer),
          ),
        )
        .map((c) => c.decoration)
        .whereType<KitSurfaceDecoration>();
    expect(discs, isNotEmpty);
    for (final disc in discs) {
      expect(disc.casts, isFalse);
    }
  });
}

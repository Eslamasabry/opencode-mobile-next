// KitStatusMark and KitTaskMark v2 (docs/ux-system/kit-api/KitStatusMark.md):
// a paused modifier and a word beside every mark, so no state is shown by
// colour alone (slice-P9.5). The spec's required tests 1-5 live here,
// including G8 (MOT-7) for every new configuration (paused, showLabel) and
// for a state change, under both stillness sources; the default-state
// samples in the shared test/kit_motion_test.dart (G8x) stay as they are.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/theme_packs.dart';

import 'kit_motion_still.dart';

Widget _host(
  Widget child, {
  Locale locale = const Locale('en'),
  ThemeData? theme,
}) => MaterialApp(
  theme: theme ?? AppTheme.dark(),
  locale: locale,
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: Center(child: child)),
);

const _enMarkWords = {
  KitMarkState.waiting: 'Waiting',
  KitMarkState.working: 'Working',
  KitMarkState.done: 'Done',
  KitMarkState.failed: 'Failed',
};

const _arMarkWords = {
  KitMarkState.waiting: 'بانتظار',
  KitMarkState.working: 'يعمل',
  KitMarkState.done: 'انتهى',
  KitMarkState.failed: 'فشل',
};

const _enTaskWords = {
  KitTaskState.waiting: 'Waiting',
  KitTaskState.working: 'Working',
  KitTaskState.done: 'Done',
  KitTaskState.failed: 'Failed',
  KitTaskState.needsYou: 'Needs you',
  KitTaskState.stopped: 'Stopped',
};

const _arTaskWords = {
  KitTaskState.waiting: 'بانتظار',
  KitTaskState.working: 'يعمل',
  KitTaskState.done: 'انتهى',
  KitTaskState.failed: 'فشل',
  KitTaskState.needsYou: 'يحتاجك',
  KitTaskState.stopped: 'متوقف',
};

void main() {
  group('default word in semantics (English and Arabic)', () {
    for (final MapEntry(key: state, value: word) in _enMarkWords.entries) {
      testWidgets('KitStatusMark $state announces "$word" in English', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        try {
          await tester.pumpWidget(_host(KitStatusMark(state: state)));
          expect(find.bySemanticsLabel(word), findsOneWidget);
        } finally {
          semantics.dispose();
        }
      });
    }
    for (final MapEntry(key: state, value: word) in _arMarkWords.entries) {
      testWidgets('KitStatusMark $state announces "$word" in Arabic', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        try {
          await tester.pumpWidget(
            _host(KitStatusMark(state: state), locale: const Locale('ar')),
          );
          expect(find.bySemanticsLabel(word), findsOneWidget);
        } finally {
          semantics.dispose();
        }
      });
    }
    for (final MapEntry(key: state, value: word) in _enTaskWords.entries) {
      testWidgets('KitTaskMark $state announces "$word" in English', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        try {
          await tester.pumpWidget(_host(KitTaskMark(state: state)));
          expect(find.bySemanticsLabel(word), findsOneWidget);
        } finally {
          semantics.dispose();
        }
      });
    }
    for (final MapEntry(key: state, value: word) in _arTaskWords.entries) {
      testWidgets('KitTaskMark $state announces "$word" in Arabic', (
        tester,
      ) async {
        final semantics = tester.ensureSemantics();
        try {
          await tester.pumpWidget(
            _host(KitTaskMark(state: state), locale: const Locale('ar')),
          );
          expect(find.bySemanticsLabel(word), findsOneWidget);
        } finally {
          semantics.dispose();
        }
      });
    }
  });

  group('label overrides the word; showLabel shows it', () {
    testWidgets('a custom label replaces the default word in semantics', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _host(
            const KitStatusMark(
              state: KitMarkState.working,
              label: 'Installing',
            ),
          ),
        );
        expect(find.bySemanticsLabel('Installing'), findsOneWidget);
        expect(find.bySemanticsLabel('Working'), findsNothing);
      } finally {
        semantics.dispose();
      }
    });

    testWidgets('showLabel draws the word as visible text beside the mark', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.done, showLabel: true)),
      );
      expect(find.text('Done'), findsOneWidget);
    });

    testWidgets('without showLabel the word is not drawn as visible text', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.done)),
      );
      expect(find.text('Done'), findsNothing);
    });

    testWidgets('KitTaskMark showLabel draws needsYou\'s word', (tester) async {
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.needsYou, showLabel: true)),
      );
      expect(find.text('Needs you'), findsOneWidget);
    });
  });

  group('paused (G37)', () {
    testWidgets('paused waiting shows the pause glyph and word "Paused"', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      try {
        await tester.pumpWidget(
          _host(
            const KitStatusMark(
              state: KitMarkState.waiting,
              paused: true,
              showLabel: true,
            ),
          ),
        );
        expect(find.byIcon(AppIconography.pause), findsOneWidget);
        expect(find.text('Paused'), findsOneWidget);
        expect(find.bySemanticsLabel('Paused'), findsOneWidget);
      } finally {
        semantics.dispose();
      }
    });

    testWidgets('paused working also shows the pause glyph', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.working, paused: true)),
      );
      expect(find.byIcon(AppIconography.pause), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsNothing);
    });

    testWidgets('paused with done throws an AssertionError', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.done, paused: true)),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('paused with failed throws an AssertionError', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.failed, paused: true)),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('KitTaskMark paused with needsYou throws an AssertionError', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.needsYou, paused: true)),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('KitTaskMark paused with stopped throws an AssertionError', (
      tester,
    ) async {
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.stopped, paused: true)),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });
  });

  group('colour roles (LOOK-5, LOOK-6, STATE-9)', () {
    // A pack where accent is nowhere near success/attention/danger's hues
    // (the default "opencode" pack's accent and success happen to be the
    // same green, which would make a color-equality check meaningless), so
    // a role mix-up shows up as a real assertion failure.
    final roles = deriveRoles(
      accent: const Color(0xFF3B82F6),
      ground: const Color(0xFF0B0C0E),
      brightness: Brightness.dark,
    );
    final theme = AppTheme.fromRoles(roles);

    Color? iconColorIn(WidgetTester tester, Finder of) => tester
        .widget<Icon>(find.descendant(of: of, matching: find.byType(Icon)))
        .color;

    testWidgets('done paints success, never accent', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.done), theme: theme),
      );
      final color = iconColorIn(tester, find.byType(KitStatusMark));
      expect(color, AppTheme.successOf(theme));
      expect(color, isNot(roles.accent));
    });

    testWidgets('failed paints text1, never danger', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.failed), theme: theme),
      );
      final color = iconColorIn(tester, find.byType(KitStatusMark));
      expect(color, roles.text1);
      expect(color, isNot(roles.danger));
    });

    testWidgets('working paints accent', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.working), theme: theme),
      );
      final indicator = tester.widget<CircularProgressIndicator>(
        find.byType(CircularProgressIndicator),
      );
      expect(indicator.color, roles.accent);
      // The kit's one spinner stroke, in logical pixels (R5).
      expect(indicator.strokeWidth, KitTokens.spinnerStroke);
    });

    testWidgets('needsYou paints attention', (tester) async {
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.needsYou), theme: theme),
      );
      final color = iconColorIn(tester, find.byType(KitTaskMark));
      expect(color, roles.attention);
    });

    testWidgets('paused and stopped paint text2', (tester) async {
      await tester.pumpWidget(
        _host(
          const KitStatusMark(state: KitMarkState.waiting, paused: true),
          theme: theme,
        ),
      );
      expect(iconColorIn(tester, find.byType(KitStatusMark)), roles.text2);
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.stopped), theme: theme),
      );
      expect(iconColorIn(tester, find.byType(KitTaskMark)), roles.text2);
    });
  });

  group('existing callers compile unchanged (KIT-43)', () {
    testWidgets('KitStatusMark with only state still renders', (tester) async {
      await tester.pumpWidget(
        _host(const KitStatusMark(state: KitMarkState.waiting)),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(KitStatusMark), findsOneWidget);
    });

    testWidgets('KitTaskMark with only state still renders', (tester) async {
      await tester.pumpWidget(
        _host(const KitTaskMark(state: KitTaskState.needsYou)),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(KitTaskMark), findsOneWidget);
    });
  });

  group('reduced motion (G8, MOT-7)', () {
    // The spec's required test 4: under the system setting and, separately,
    // under Animations: Off, working is the still dot (paused: the pause
    // glyph) and one pump() leaves no ticker running.
    final samples = <String, (Widget, IconData)>{
      'working': (
        const KitStatusMark(state: KitMarkState.working),
        AppIconography.statusDot,
      ),
      'paused working': (
        const KitStatusMark(state: KitMarkState.working, paused: true),
        AppIconography.pause,
      ),
      'working with showLabel': (
        const KitStatusMark(state: KitMarkState.working, showLabel: true),
        AppIconography.statusDot,
      ),
      'task working': (
        const KitTaskMark(state: KitTaskState.working),
        AppIconography.statusDot,
      ),
      'task paused working with showLabel': (
        const KitTaskMark(
          state: KitTaskState.working,
          paused: true,
          showLabel: true,
        ),
        AppIconography.pause,
      ),
    };
    for (final still in KitStill.values) {
      for (final MapEntry(key: name, value: (mark, icon)) in samples.entries) {
        testWidgets('$name is still after one pump() under ${still.name}', (
          tester,
        ) async {
          await tester.pumpWidget(kitStillApp(mark, still));
          await tester.pump();
          expect(find.byIcon(icon), findsOneWidget);
          expect(find.byType(CircularProgressIndicator), findsNothing);
          expect(tester.hasRunningAnimations, isFalse);
        });
      }

      testWidgets('a state change swaps at once under ${still.name}', (
        tester,
      ) async {
        await tester.pumpWidget(
          kitStillApp(
            const KitStatusMark(state: KitMarkState.working, showLabel: true),
            still,
          ),
        );
        await tester.pump();
        await tester.pumpWidget(
          kitStillApp(
            const KitStatusMark(state: KitMarkState.done, showLabel: true),
            still,
          ),
        );
        await tester.pump();
        expect(tester.hasRunningAnimations, isFalse);
        // The old glyph is gone, not fading out beside the new one.
        expect(find.byIcon(AppIconography.statusDot), findsNothing);
        expect(find.byIcon(AppIconography.check), findsOneWidget);
      });

      testWidgets('a task turning to needsYou swaps at once under '
          '${still.name}', (tester) async {
        await tester.pumpWidget(
          kitStillApp(const KitTaskMark(state: KitTaskState.working), still),
        );
        await tester.pump();
        await tester.pumpWidget(
          kitStillApp(const KitTaskMark(state: KitTaskState.needsYou), still),
        );
        await tester.pump();
        expect(tester.hasRunningAnimations, isFalse);
        expect(find.byIcon(AppIconography.statusDot), findsNothing);
        expect(find.byIcon(AppIconography.question), findsOneWidget);
      });
    }

    testWidgets('with motion on, a state change cross-fades on '
        'KitMotion.quick', (tester) async {
      Widget app(KitMarkState state) => MaterialApp(
        home: Scaffold(
          body: Center(child: KitStatusMark(state: state)),
        ),
      );
      await tester.pumpWidget(app(KitMarkState.waiting));
      await tester.pumpWidget(app(KitMarkState.done));
      await tester.pump(KitMotion.quick ~/ 2);
      // Mid-fade: the check is fading in, not yet opaque.
      final fade = tester.widget<FadeTransition>(
        find
            .ancestor(
              of: find.byIcon(AppIconography.check),
              matching: find.byType(FadeTransition),
            )
            .first,
      );
      expect(fade.opacity.value, inExclusiveRange(0, 1));
      await tester.pump(KitMotion.quick);
      expect(tester.hasRunningAnimations, isFalse);
      expect(find.byIcon(AppIconography.check), findsOneWidget);
    });
  });

  group('waiting ring contrast (LOOK-8)', () {
    Color ringColor(WidgetTester tester) {
      final box = tester.widget<Container>(
        find.descendant(
          of: find.byType(KitStatusMark),
          matching: find.byType(Container),
        ),
      );
      final border = (box.decoration! as BoxDecoration).border! as Border;
      return border.top.color;
    }

    testWidgets('every pack draws the ring at 3:1 or more on the ground '
        'and surfaces, in text3 where it passes and text2 where not', (
      tester,
    ) async {
      final packs = [
        for (final id in ThemePackId.values)
          if (id != ThemePackId.dynamic) themePack(id),
      ];
      for (final pack in packs) {
        for (final theme in [AppTheme.dark(pack), AppTheme.light(pack)]) {
          // A fresh app per theme: MaterialApp would animate between two.
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pumpWidget(
            _host(
              const KitStatusMark(state: KitMarkState.waiting),
              theme: theme,
            ),
          );
          final roles = AppTheme.rolesOf(theme);
          final grounds = [
            roles.ground,
            roles.surface1,
            roles.surface2,
            roles.surface3,
          ];
          final text3Passes = grounds.every(
            (g) => contrastRatio(roles.text3, g) >= 3,
          );
          final ring = ringColor(tester);
          expect(
            ring,
            text3Passes ? roles.text3 : roles.text2,
            reason: '${pack.id.name} ${roles.brightness.name}',
          );
          for (final g in grounds) {
            expect(
              contrastRatio(ring, g),
              greaterThanOrEqualTo(3),
              reason: '${pack.id.name} ${roles.brightness.name}',
            );
          }
        }
      }
    });

    testWidgets('a pack whose text3 misses 3:1 draws the ring in text2', (
      tester,
    ) async {
      final base = AppTheme.rolesOf(AppTheme.dark());
      // text3 one shade off surface3: well under 3:1.
      final faint = Color.lerp(base.surface3, base.text3, .2)!;
      expect(contrastRatio(faint, base.surface3), lessThan(3));
      final roles = ThemeRoles(
        brightness: base.brightness,
        ground: base.ground,
        surface1: base.surface1,
        surface2: base.surface2,
        surface3: base.surface3,
        hairline: base.hairline,
        text1: base.text1,
        text2: base.text2,
        text3: faint,
        accent: base.accent,
        onAccent: base.onAccent,
        attention: base.attention,
        attentionFill: base.attentionFill,
        onAttentionFill: base.onAttentionFill,
        attentionSurface: base.attentionSurface,
        attentionLine: base.attentionLine,
        danger: base.danger,
        dangerFill: base.dangerFill,
        onDangerFill: base.onDangerFill,
        success: base.success,
        scrim: base.scrim,
        codeKeyword: base.codeKeyword,
        codeString: base.codeString,
        codeType: base.codeType,
        elevationShadow: base.elevationShadow,
      );
      await tester.pumpWidget(
        _host(
          const KitStatusMark(state: KitMarkState.waiting),
          theme: AppTheme.fromRoles(roles),
        ),
      );
      expect(ringColor(tester), roles.text2);
    });
  });

  group('glyph size (LOOK-33)', () {
    // Text 1.15 grows the 20 dp glyph to 23 dp, which at DPR 2.625 is
    // 60.375 physical px: the mark snaps it to 60 (22.857 dp).
    for (final (dpr, scale) in [(2.625, 1.15), (3.0, 1.3), (1.75, 2.0)]) {
      testWidgets('snaps to whole physical pixels at DPR $dpr, text $scale', (
        tester,
      ) async {
        tester.view.devicePixelRatio = dpr;
        addTearDown(tester.view.reset);
        for (final mark in const [
          KitStatusMark(state: KitMarkState.done),
          KitStatusMark(state: KitMarkState.waiting, paused: true),
          KitTaskMark(state: KitTaskState.needsYou),
          KitTaskMark(state: KitTaskState.stopped),
        ]) {
          // A fresh tree per mark: a rebuild would cross-fade the two.
          await tester.pumpWidget(const SizedBox.shrink());
          await tester.pumpWidget(
            MaterialApp(
              theme: AppTheme.dark(),
              localizationsDelegates: AppLocalizations.localizationsDelegates,
              supportedLocales: AppLocalizations.supportedLocales,
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(textScaler: TextScaler.linear(scale)),
                child: child!,
              ),
              home: Scaffold(body: Center(child: mark)),
            ),
          );
          final size = tester.widget<Icon>(find.byType(Icon)).size!;
          final physical = size * dpr;
          expect(physical, closeTo(physical.roundToDouble(), 1e-9));
          // Still within maxIconScale (1.5 x 20 dp) and about the scale.
          expect(size, lessThanOrEqualTo(30 + 1 / dpr));
          expect(size, closeTo(20 * (scale > 1.5 ? 1.5 : scale), 1 / dpr));
        }
      });
    }

    testWidgets('the working ring is the glyph size and grows with text', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: const TextScaler.linear(1.3)),
            child: child!,
          ),
          home: const Scaffold(
            body: Center(child: KitStatusMark(state: KitMarkState.working)),
          ),
        ),
      );
      final ring = tester.getSize(find.byType(CircularProgressIndicator));
      expect(ring.width, 26);
    });
  });
}

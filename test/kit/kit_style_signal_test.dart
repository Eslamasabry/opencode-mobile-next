// The Signal style (owner, 2026-10-30; concept CD): its tokens and enamel,
// the raised keys for actions and flat outlines for the rest, and the kit
// parts that run on its track: a conversation's signal in the list, the
// engraved section label, the printed slip with its two keys, a step's
// signal and the running time's segment timer. Every other style keeps
// its own look.
import 'package:clock/clock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/kit_surface_look.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_signal.dart';
import 'package:opencode_mobile/ui/kit/style/kit_style_theme.dart';
import 'package:opencode_mobile/ui/theme_packs_signal.dart';

final _now = DateTime(2026, 10, 30, 11, 6);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  KitSurfaceStyle style = KitSurfaceStyle.signal,
  bool light = true,
}) async {
  await withClock(Clock.fixed(_now), () async {
    await tester.pumpWidget(
      MaterialApp(
        theme: light ? AppTheme.light() : AppTheme.dark(),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, app) => KitEffectsScope(
          effects: KitEffects(surfaceStyle: style),
          child: kitStyleTheme(child: app!),
        ),
        home: Scaffold(body: ListView(children: [child])),
      ),
    );
    await tester.pumpAndSettle();
  });
}

KitSurfaceRequest _control({
  Color? color,
  bool pressed = false,
  bool action = true,
}) => KitSurfaceRequest(
  roles: graphiteLight,
  role: KitSurfaceRole.control,
  solid: const BoxDecoration(),
  knobs: kitStyleSpec(KitSurfaceStyle.signal).tokens.defaults,
  radius: BorderRadius.circular(4),
  color: color,
  pressed: pressed,
  action: action,
);

KitRequestCard _slip() => KitRequestCard.ask(
  kind: KitRequestKind.permission,
  title: 'Push to origin?',
  who: 'OpenCode',
  reason: KitNeedsYouReason.decision,
  ifIgnored: 'The agent waits; nothing is lost.',
  announcement: 'Permission needed: Push to origin?',
  summary: 'git push origin main',
  since: _now,
  onDetails: () {},
  answers: KitRequestDecide(
    allowLabel: 'Allow',
    rejectLabel: 'Reject',
    onAllow: () {},
    onReject: () {},
  ),
);

/// Whether [label]'s button is drawn as a raised key (a face with a lip).
bool _isKey(WidgetTester tester, String label) => find
    .ancestor(
      of: find.text(label),
      matching: find.byWidgetPredicate(
        (w) =>
            w is DecoratedBox &&
            w.decoration is KitSurfaceDecoration &&
            (w.decoration as KitSurfaceDecoration).shadows.any(
              (s) => s.offset.dx == 0 && s.offset.dy > 0 && s.blurRadius == 0,
            ),
      ),
    )
    .evaluate()
    .isNotEmpty;

void main() {
  group('tokens and enamel', () {
    test('Signal is a registered style on a track', () {
      final spec = kitStyleSpec(KitSurfaceStyle.signal);
      expect(spec, isA<KitStyleSignal>());
      final tokens = spec.tokens;
      expect(tokens.track, isTrue);
      expect(tokens.flatLists, isTrue);
      expect(tokens.type.engravedLabels, isTrue);
      expect(tokens.defaults.corners, KitCorners.sharp);
      expect(tokens.live.offWhenChosen, isTrue);
      expect(tokens.keepsUnderHighContrast, isTrue);
      // Every other style keeps its lists off the track.
      for (final style in KitSurfaceStyle.values) {
        if (style == KitSurfaceStyle.signal) continue;
        expect(kitStyleSpec(style).tokens.track, isFalse, reason: style.name);
      }
    });

    test('cream enamel by day, a night signal box by dark, words at AA', () {
      const spec = KitStyleSignal();
      final day = spec.adjustRoles(graphiteLight);
      final night = spec.adjustRoles(graphiteDark);
      expect(day.ground, SignalPalette.light.ground);
      expect(night.ground, SignalPalette.night.ground);
      for (final roles in [day, night]) {
        for (final fill in [roles.ground, roles.surface2, roles.surface3]) {
          for (final ink in [roles.text1, roles.text2, roles.text3]) {
            expect(contrastRatio(ink, fill), greaterThanOrEqualTo(4.5));
          }
        }
        // Not inverted: the main words are the theme's own.
        expect(
          roles.text1,
          (roles.isDark ? graphiteDark : graphiteLight).text1,
        );
      }
    });

    test('an action with its own fill is a raised key; the rest are flat '
        'outlines', () {
      const spec = KitStyleSignal();
      final key =
          spec.resolve(_control(color: graphiteLight.accent))
              as KitSurfaceDecoration;
      expect(key.fill, graphiteLight.accent);
      expect(key.shadows.single.offset, const Offset(0, 3));
      expect(key.shadows.single.blurRadius, 0);
      final held =
          spec.resolve(_control(color: graphiteLight.accent, pressed: true))
              as KitSurfaceDecoration;
      expect(held.shadows.single.offset, const Offset(0, 1));
      // A mark with its own tint (a status tag) is never a key.
      final tag =
          spec.resolve(_control(color: graphiteLight.surface3, action: false))
              as KitSurfaceDecoration;
      expect(tag.shadows, isEmpty);
      final filter = spec.resolve(_control()) as KitSurfaceDecoration;
      expect(filter.shadows, isEmpty);
      expect(filter.edgeWidth, KitStyleSignal.outline);
      expect(filter.fill.a, 0);
    });
  });

  group('parts on the track', () {
    Widget feed() => Column(
      children: [
        KitFeedItem(
          project: 'settings-demo',
          title: 'Push to origin?',
          state: 'Needs you',
          stateTone: KitFeedStateTone.needsYou,
          onTap: () {},
        ),
        KitFeedItem(
          project: 'IPTV_King',
          title: 'Fix the TV focus ring',
          state: 'Working',
          stateTone: KitFeedStateTone.running,
          onTap: () {},
        ),
        KitFeedItem(
          project: 'oc_app',
          title: 'Widget sizes',
          time: '3 h ago',
          onTap: () {},
        ),
      ],
    );

    testWidgets('a conversation shows its signal: red, green or a ring', (
      tester,
    ) async {
      await _pump(tester, feed());
      expect(find.byKey(const ValueKey('kit-signal-stop')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-signal-go')), findsOneWidget);
      expect(find.byKey(const ValueKey('kit-signal-ring')), findsOneWidget);
      // The words still say the state (never colour alone).
      expect(find.textContaining('Needs you'), findsOneWidget);

      await _pump(tester, feed(), style: KitSurfaceStyle.solid);
      expect(find.byKey(const ValueKey('kit-signal-stop')), findsNothing);
      expect(find.byKey(const ValueKey('kit-signal-ring')), findsNothing);
    });

    testWidgets('a section label is engraved in capitals, read as written', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      await _pump(tester, const KitSectionLabel('Today'));
      expect(find.text('TODAY'), findsOneWidget);
      expect(find.bySemanticsLabel('Today'), findsOneWidget);
      await _pump(
        tester,
        const KitSectionLabel('Today'),
        style: KitSurfaceStyle.paper,
      );
      expect(find.text('Today'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('a request is a printed slip with two raised keys', (
      tester,
    ) async {
      await _pump(tester, _slip());
      final face = tester.widget<DecoratedBox>(
        find.byKey(const ValueKey('kit-request-card')),
      );
      expect(
        (face.decoration as BoxDecoration).color,
        SignalPalette.light.slip,
      );
      expect(_isKey(tester, 'Allow'), isTrue);
      expect(_isKey(tester, 'Reject'), isTrue);
      // Off the slip, a quiet button is a flat outline.
      await _pump(
        tester,
        KitButton.secondary(label: 'Cancel', onPressed: () {}),
      );
      expect(_isKey(tester, 'Cancel'), isFalse);
    });

    testWidgets('a running step is a green signal with a segment timer', (
      tester,
    ) async {
      await _pump(
        tester,
        KitStepTimeline(
          label: 'Ran 1 command',
          icon: AppIconography.terminal,
          expanded: true,
          onPressed: () {},
          steps: [
            KitToolRow(
              kind: KitToolKind.read,
              title: 'Read',
              path: 'lib/main.dart',
              status: KitToolStatus.done,
            ),
            KitToolRow(
              kind: KitToolKind.shell,
              title: 'Run',
              path: 'flutter test',
              status: KitToolStatus.running,
              startedAt: _now.subtract(const Duration(seconds: 72)),
            ),
          ],
        ),
      );
      expect(find.byKey(const ValueKey('kit-step-signal-running')), findsOne);
      expect(find.byKey(const ValueKey('kit-signal-ring')), findsWidgets);
      // The timer's lit digits over their unlit ghost.
      expect(find.text('1 min 12 s'), findsOneWidget);
      expect(find.text('8 min 88 s'), findsOneWidget);
      expect(find.text('SHOW'), findsNothing);
      expect(find.text('HIDE'), findsOneWidget);
    });
  });
}

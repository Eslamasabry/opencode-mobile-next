// KitFineTunePanel: each knob is a segmented choice under its name with the
// style's own choice in words; a choice is handed to the host; the reset
// shows only while the host offers it; a held knob says why; the disabled
// panel is one line; and [KitFineTunePanel.read] applies the knob rules.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import 'kit_motion_still.dart';

Widget _app(Widget child) => MaterialApp(
  theme: AppTheme.dark(),
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: ListView(children: [child])),
);

KitFineTunePanel _panel({
  KitCorners corners = KitCorners.round,
  ValueChanged<KitCorners>? onCorners,
  VoidCallback? onReset,
  String? edgesHeld,
  String? disabledReason,
}) => KitFineTunePanel(
  resetLabel: 'Reset to Solid defaults',
  onReset: onReset,
  disabledReason: disabledReason,
  knobs: [
    KitFineTuneKnob<KitCorners>(
      title: 'Corners',
      defaultNote: 'Default: Round',
      selected: corners,
      segments: [
        for (final c in KitCorners.values)
          KitSegment(key: ValueKey('c-${c.name}'), value: c, label: c.name),
      ],
      onChanged: onCorners ?? (_) {},
    ),
    KitFineTuneKnob<KitEdges>(
      title: 'Edges',
      hint: 'A thin line around cards.',
      defaultNote: 'Default: Hairline',
      selected: KitEdges.hairline,
      segments: [
        for (final e in KitEdges.values)
          KitSegment(key: ValueKey('e-${e.name}'), value: e, label: e.name),
      ],
      disabledReason: edgesHeld,
      onChanged: edgesHeld == null ? (_) {} : null,
    ),
  ],
);

void main() {
  kitMotionStillTests(
    'KitFineTunePanel',
    builds: {'default': () => _panel()},
    changes: {
      'the reset unfolds': KitMotionChange(
        build: () => _panel(),
        act: (tester, stage) => stage.rebuild(_panel(onReset: () {})),
        shows: 'Reset to Solid defaults',
      ),
    },
  );

  testWidgets('names each knob, its hint and the style\'s own choice', (
    tester,
  ) async {
    await tester.pumpWidget(_app(_panel()));
    expect(find.text('Corners'), findsOneWidget);
    expect(find.text('Default: Round'), findsOneWidget);
    expect(find.text('A thin line around cards.'), findsOneWidget);
    expect(find.text('Reset to Solid defaults'), findsNothing);
  });

  testWidgets('a choice goes to the host, and the reset to its callback', (
    tester,
  ) async {
    KitCorners? chosen;
    var reset = 0;
    await tester.pumpWidget(
      _app(_panel(onCorners: (c) => chosen = c, onReset: () => reset++)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('c-sharp')));
    expect(chosen, KitCorners.sharp);
    await tester.tap(find.byKey(const ValueKey('kit-fine-tune-reset')));
    expect(reset, 1);
  });

  testWidgets('a held knob shows why and takes no choice', (tester) async {
    await tester.pumpWidget(_app(_panel(edgesHeld: 'Held by high contrast')));
    expect(find.text('Held by high contrast'), findsOneWidget);
  });

  testWidgets('disabled: the reason alone, no knobs', (tester) async {
    await tester.pumpWidget(
      _app(_panel(disabledReason: 'Saver keeps surfaces solid')),
    );
    expect(find.text('Saver keeps surfaces solid'), findsOneWidget);
    expect(find.text('Corners'), findsNothing);
  });

  group('read', () {
    test('a style\'s own knobs, and the choices laid over them', () {
      final r = KitFineTunePanel.read(
        const KitEffects(
          surfaceStyle: KitSurfaceStyle.brutal,
          fineTune: KitFineTune(density: KitDensity.compact),
        ),
      );
      expect(r.style, KitSurfaceStyle.brutal);
      expect(r.defaults.corners, KitCorners.soft);
      expect(r.knobs.density, KitDensity.compact);
      expect(r.differs, isTrue);
      expect(r.edgesHeldByDepth, isFalse);
    });

    test('a choice equal to the style\'s own does not differ', () {
      final r = KitFineTunePanel.read(
        const KitEffects(
          surfaceStyle: KitSurfaceStyle.paper,
          fineTune: KitFineTune(corners: KitCorners.sharp),
        ),
      );
      expect(r.differs, isFalse);
    });

    test('flat Soft holds its edges; high contrast holds depth and edges', () {
      final soft = KitFineTunePanel.read(
        const KitEffects(
          surfaceStyle: KitSurfaceStyle.soft,
          fineTune: KitFineTune(depth: KitDepth.flat),
        ),
      );
      expect(soft.edgesHeldByDepth, isTrue);
      expect(soft.knobs.edges, KitEdges.hairline);
      final contrast = KitFineTunePanel.read(
        const KitEffects(fineTune: KitFineTune(depth: KitDepth.raised)),
        highContrast: true,
      );
      expect(contrast.heldByContrast, isTrue);
      expect(contrast.knobs.depth, KitDepth.flat);
    });
  });
}

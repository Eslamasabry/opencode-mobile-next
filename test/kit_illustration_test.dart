// The motion and illustration kit (design standard §10): an entrance plays
// once and settles, an ambient loop runs only where asked and allowed, and
// reduced motion shows the finished drawing at once.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_phone_scene.dart';

/// Records the frames it is asked to paint.
class _Probe extends KitScene {
  _Probe(this.frames);

  final List<KitSceneFrame> frames;

  @override
  void paint(Canvas canvas, KitSceneFrame frame) => frames.add(frame);
}

Widget _host(Widget child, {bool reduce = false}) => MaterialApp(
  theme: AppTheme.dark(),
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduce),
    child: Scaffold(body: Center(child: child)),
  ),
);

void main() {
  tearDown(() => KitMotion.loops = false);

  testWidgets('the entrance plays once and the drawing settles finished', (
    tester,
  ) async {
    final frames = <KitSceneFrame>[];
    await tester.pumpWidget(_host(KitIllustration(scene: _Probe(frames))));
    expect(frames.first.entrance, 0);
    await tester.pump(KitMotion.entrance ~/ 2);
    expect(frames.last.entrance, inExclusiveRange(0, 1));
    await tester.pumpAndSettle();
    expect(frames.last.entrance, 1);
    expect(frames.last.looping, isFalse);
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('a celebration takes the longer entrance', (tester) async {
    final frames = <KitSceneFrame>[];
    await tester.pumpWidget(
      _host(
        KitIllustration(
          scene: _Probe(frames),
          entranceDuration: KitMotion.celebration,
        ),
      ),
    );
    await tester.pump(KitMotion.entrance);
    expect(frames.last.entrance, lessThan(1));
    await tester.pumpAndSettle();
    expect(frames.last.entrance, 1);
  });

  testWidgets('reduced motion draws the finished drawing at once', (
    tester,
  ) async {
    KitMotion.loops = true;
    final frames = <KitSceneFrame>[];
    await tester.pumpWidget(
      _host(
        KitIllustration(scene: _Probe(frames), ambient: true),
        reduce: true,
      ),
    );
    expect(frames.last.entrance, 1);
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('an ambient scene loops after its entrance when allowed', (
    tester,
  ) async {
    KitMotion.loops = true;
    final frames = <KitSceneFrame>[];
    await tester.pumpWidget(
      _host(KitIllustration(scene: _Probe(frames), ambient: true)),
    );
    await tester.pump(KitMotion.entrance);
    await tester.pump(const Duration(milliseconds: 16));
    await tester.pump(KitMotion.breath ~/ 4);
    expect(frames.last.looping, isTrue);
    expect(frames.last.loop, greaterThan(0));
    expect(tester.hasRunningAnimations, isTrue);
    // Leaving the screen ends it (the framework winds its last frame down
    // within a few frames).
    await tester.pumpWidget(_host(const SizedBox()));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('under flutter test an ambient scene still settles', (
    tester,
  ) async {
    expect(KitMotion.loops, isFalse);
    await tester.pumpWidget(
      _host(const KitIllustration(scene: KitPortalScene(), ambient: true)),
    );
    await tester.pumpAndSettle();
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('a state with an illustration draws it instead of the icon', (
    tester,
  ) async {
    await tester.pumpWidget(
      _host(
        const KitStateView(
          icon: Icons.link,
          title: 'Connecting',
          illustration: KitPortalScene(),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('kit-state-illustration')),
      findsOneWidget,
    );
    expect(find.byKey(const ValueKey('kit-state-icon')), findsNothing);
  });

  testWidgets('drawings are decorative unless labelled', (tester) async {
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      _host(
        const Column(
          children: [
            KitIllustration(scene: KitPortalScene()),
            KitIllustration(scene: KitPortalScene(), semanticLabel: 'OpenCode'),
          ],
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.bySemanticsLabel('OpenCode'), findsOneWidget);
    semantics.dispose();
  });

  group('Settings › Appearance effects', () {
    Widget scoped(KitEffects effects, Widget child) =>
        _host(KitEffectsScope(effects: effects, child: child));

    testWidgets('Animations: Calm draws in but never loops', (tester) async {
      KitMotion.loops = true;
      final frames = <KitSceneFrame>[];
      await tester.pumpWidget(
        scoped(
          const KitEffects(motion: KitMotionLevel.calm),
          KitIllustration(scene: _Probe(frames), ambient: true),
        ),
      );
      expect(frames.first.entrance, 0);
      await tester.pumpAndSettle();
      expect(frames.last.entrance, 1);
      expect(frames.last.looping, isFalse);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('Animations: Off shows the finished drawing at once', (
      tester,
    ) async {
      final frames = <KitSceneFrame>[];
      await tester.pumpWidget(
        scoped(
          const KitEffects(motion: KitMotionLevel.off),
          KitIllustration(scene: _Probe(frames)),
        ),
      );
      expect(frames.last.entrance, 1);
      expect(tester.hasRunningAnimations, isFalse);
    });

    testWidgets('Celebrations off: a celebration shows finished at once', (
      tester,
    ) async {
      final frames = <KitSceneFrame>[];
      final plain = <KitSceneFrame>[];
      await tester.pumpWidget(
        scoped(
          const KitEffects(celebrations: false),
          Column(
            children: [
              KitIllustration(
                scene: _Probe(frames),
                entranceDuration: KitMotion.celebration,
              ),
              KitIllustration(scene: _Probe(plain)),
            ],
          ),
        ),
      );
      expect(frames.last.entrance, 1);
      // An ordinary drawing still draws itself in.
      expect(plain.first.entrance, 0);
      await tester.pumpAndSettle();
    });
  });

  group('portal scene', () {
    for (final (name, theme) in [
      ('dark', AppTheme.dark()),
      ('light', AppTheme.light()),
    ]) {
      testWidgets('finished drawing, $name', (tester) async {
        await tester.pumpWidget(
          MaterialApp(
            theme: theme,
            home: Scaffold(
              body: Center(
                child: RepaintBoundary(
                  key: const ValueKey('portal'),
                  child: ColoredBox(
                    color: theme.colorScheme.surface,
                    child: const Padding(
                      padding: EdgeInsets.all(16),
                      child: KitIllustration(scene: KitPortalScene()),
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await expectLater(
          find.byKey(const ValueKey('portal')),
          matchesGoldenFile('goldens/kit_portal_$name.png'),
        );
      });
    }
  });

  testWidgets('a slot swaps one kind of scene for another without an error', (
    tester,
  ) async {
    // The connecting card draws the phone while the phone's server starts,
    // then the portal: one painter compared the two as the same kind and
    // threw on every cold start (build 4071).
    await tester.pumpWidget(
      _host(const KitIllustration(scene: SetupPhoneScene())),
    );
    await tester.pumpWidget(
      _host(const KitIllustration(scene: KitPortalScene())),
    );
    await tester.pumpWidget(
      _host(const KitIllustration(scene: SetupPhoneScene())),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}

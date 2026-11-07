// The setup and connecting drawings (slice A of
// docs/design/motion-and-illustration-2026-09-25.md): each scene's finished
// frame in dark and light, and how they move — an entrance that settles, a
// loop only while waiting, the finished drawing under reduced motion.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/setup_scenes_test.dart
// and look at every changed image before committing it.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_phone_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_ready_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_steps_scene.dart';
import 'package:opencode_mobile/ui/kit/scenes/setup_unplugged_scene.dart';

final _scenes = <String, (KitScene, double)>{
  'fresh': (const SetupPhoneScene(), 240),
  'starting': (const SetupPhoneScene(mood: SetupPhoneMood.starting), 240),
  'phone_ready': (const SetupPhoneScene(mood: SetupPhoneMood.ready), 240),
  'stopped': (const SetupPhoneScene(mood: SetupPhoneMood.stopped), 240),
  'ready': (const SetupReadyScene(), 240),
  'unplugged': (const SetupUnpluggedScene(), 240),
  'steps_download': (
    const SetupStepsScene(
      stage: SetupSceneStage.download,
      done: 3,
      total: 5,
      fraction: .6,
    ),
    320,
  ),
  'steps_unpack': (
    const SetupStepsScene(
      stage: SetupSceneStage.unpack,
      done: 3,
      total: 5,
      fraction: .5,
    ),
    320,
  ),
  'steps_install': (
    const SetupStepsScene(
      stage: SetupSceneStage.install,
      done: 1,
      total: 5,
      fraction: .3,
    ),
    320,
  ),
  'steps_start': (
    const SetupStepsScene(stage: SetupSceneStage.start, done: 5, total: 6),
    320,
  ),
  'steps_paused': (
    const SetupStepsScene(
      stage: SetupSceneStage.download,
      done: 3,
      total: 5,
      fraction: .6,
      halted: SetupSceneHalt.paused,
    ),
    320,
  ),
  'steps_failed': (
    const SetupStepsScene(
      stage: SetupSceneStage.download,
      done: 3,
      total: 5,
      fraction: .6,
      halted: SetupSceneHalt.failed,
    ),
    320,
  ),
};

Widget _host(Widget child, {ThemeData? theme, bool reduce = false}) =>
    MaterialApp(
      theme: theme ?? AppTheme.dark(),
      home: MediaQuery(
        data: MediaQueryData(disableAnimations: reduce),
        child: Scaffold(body: Center(child: child)),
      ),
    );

/// Records the frames a scene is asked to paint.
class _Probe extends SetupPhoneScene {
  _Probe(this.frames) : super(mood: SetupPhoneMood.starting);

  final List<KitSceneFrame> frames;

  @override
  void paint(Canvas canvas, KitSceneFrame frame) {
    frames.add(frame);
    super.paint(canvas, frame);
  }
}

void main() {
  tearDown(() => KitMotion.loops = false);

  group('finished frames', () {
    for (final (mode, theme) in [
      ('dark', AppTheme.dark()),
      ('light', AppTheme.light()),
    ]) {
      for (final MapEntry(key: name, value: (scene, width))
          in _scenes.entries) {
        testWidgets('$name · $mode', (tester) async {
          await tester.pumpWidget(
            _host(
              theme: theme,
              RepaintBoundary(
                key: const ValueKey('scene'),
                child: ColoredBox(
                  color: theme.colorScheme.surface,
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: KitIllustration(scene: scene, width: width),
                  ),
                ),
              ),
            ),
          );
          await tester.pumpAndSettle();
          await expectLater(
            find.byKey(const ValueKey('scene')),
            matchesGoldenFile('goldens/kit_setup_${name}_$mode.png'),
          );
        });
      }
    }
  });

  testWidgets('a scene with data repaints when its step changes', (
    tester,
  ) async {
    const a = SetupStepsScene(
      stage: SetupSceneStage.download,
      done: 1,
      total: 5,
      fraction: .2,
    );
    const b = SetupStepsScene(
      stage: SetupSceneStage.unpack,
      done: 1,
      total: 5,
      fraction: .2,
    );
    const c = SetupStepsScene(
      stage: SetupSceneStage.download,
      done: 1,
      total: 5,
      fraction: .2,
    );
    expect(b.differs(a), isTrue);
    expect(c.differs(a), isFalse);
    expect(
      const SetupPhoneScene(
        mood: SetupPhoneMood.stopped,
      ).differs(const SetupPhoneScene()),
      isTrue,
    );
  });

  testWidgets('the starting phone loops while waiting and stops after', (
    tester,
  ) async {
    KitMotion.loops = true;
    final frames = <KitSceneFrame>[];
    await tester.pumpWidget(
      _host(KitIllustration(scene: _Probe(frames), ambient: true)),
    );
    await tester.pump(KitMotion.entrance);
    await tester.pump(const Duration(milliseconds: 16));
    await tester.pump(KitMotion.breath ~/ 3);
    expect(frames.last.looping, isTrue);
    expect(tester.hasRunningAnimations, isTrue);
    await tester.pumpWidget(_host(const SizedBox()));
    await tester.pump(const Duration(milliseconds: 300));
    expect(tester.hasRunningAnimations, isFalse);
  });

  testWidgets('reduced motion shows every scene finished at once', (
    tester,
  ) async {
    KitMotion.loops = true;
    for (final (scene, width) in _scenes.values) {
      await tester.pumpWidget(
        _host(
          reduce: true,
          KitIllustration(scene: scene, width: width, ambient: true),
        ),
      );
      expect(tester.hasRunningAnimations, isFalse);
      await tester.pumpWidget(const SizedBox());
    }
  });
}

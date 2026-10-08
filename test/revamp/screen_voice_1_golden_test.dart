// Golden renders of screen-voice-1's pages (wave 2b), rebuilt from kit
// parts: the voice model setup sheet (not installed, installed, downloading,
// failed), its delete confirmation and the voice licenses group of About with one
// license open. The voice input sheet is gone: voice is a composer mode
// (P10.3, test/revamp/slice_p10_3_golden_test.dart). Phone 412x915 and one wide window (1280x800), dark and light
// (owner decision 2026-09-27: no Arabic), with the app's real fonts at
// DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_voice_1_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/voice/model_manager.dart';
import 'package:opencode_mobile/voice/model_manifest.dart';
import 'package:opencode_mobile/voice/voice_ui.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'screen_voice_1_fixtures.dart';

String _name(String shot, Size size, bool light) => [
  'voice_$shot',
  if (size != voicePhone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget home,
  Size size = voicePhone,
  Future<void> Function()? then,
  bool settle = true,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    await tester.pumpWidget(
      voiceHost(home: home, light: light, boundary: boundary),
    );
    if (then != null) await then();
    if (settle) {
      await tester.pumpAndSettle();
    } else {
      await pumpSheet(tester);
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

Future<void> _open(WidgetTester tester) async {
  await tester.tap(find.text('Open'));
  await pumpSheet(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  // A cached asset future from an earlier test's fake clock never lands in
  // the next one: every test reads the license text afresh.
  setUp(rootBundle.clear);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    for (final size in const [voicePhone, voiceWide]) {
      testWidgets('setup not installed ${size.width} · $mode', (tester) async {
        final models = await ScriptedVoiceModels.create(installed: {});
        addTearDown(models.dispose);
        await _shot(
          tester,
          'setup_not_installed',
          light: light,
          size: size,
          home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
          then: () => _open(tester),
        );
      });
    }

    testWidgets('setup installed · $mode', (tester) async {
      final models = await ScriptedVoiceModels.create(installed: {'base'});
      addTearDown(models.dispose);
      await _shot(
        tester,
        'setup_installed',
        light: light,
        home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
        then: () => _open(tester),
      );
    });

    testWidgets('setup downloading · $mode', (tester) async {
      final models = await ScriptedVoiceModels.create(installed: {});
      addTearDown(models.dispose);
      final pack = voiceModelPack('base');
      models.downloading(pack, pack.downloadBytes * 2 ~/ 5);
      await _shot(
        tester,
        'setup_downloading',
        light: light,
        home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
        then: () => _open(tester),
      );
    });

    testWidgets('setup failed · $mode', (tester) async {
      final models = await ScriptedVoiceModels.create(installed: {});
      addTearDown(models.dispose);
      models
        ..state = VoiceModelState.error
        ..error = StateError('HTTP 503 from the model server');
      await _shot(
        tester,
        'setup_error',
        light: light,
        home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
        then: () => _open(tester),
      );
    });

    testWidgets('delete confirmation · $mode', (tester) async {
      final models = await ScriptedVoiceModels.create(installed: {'base'});
      addTearDown(models.dispose);
      await _shot(
        tester,
        'setup_delete_confirm',
        light: light,
        home: voiceLauncher((c) => showVoiceModelSetupSheet(c, models)),
        then: () async {
          await _open(tester);
          final delete = find.textContaining(
            'Delete Balanced speech model (',
            skipOffstage: false,
          );
          await tester.ensureVisible(delete);
          await tester.pumpAndSettle();
          await tester.tap(delete);
        },
      );
    });

    for (final size in const [voicePhone, voiceWide]) {
      testWidgets('notices ${size.width} · $mode', (tester) async {
        await _shot(
          tester,
          'notices_loaded',
          light: light,
          size: size,
          home: noticesHost,
        );
      });
    }

    testWidgets('notices license open · $mode', (tester) async {
      await _shot(
        tester,
        'notices_license',
        light: light,
        home: noticesHost,
        then: () async {
          await tester.pumpAndSettle();
          await tester.tap(find.text('ONNX Runtime'));
          // The license text is a real asset read: let it land before the
          // frame is taken.
          for (var i = 0; i < 5; i++) {
            await tester.runAsync(
              () => Future<void>.delayed(const Duration(milliseconds: 40)),
            );
            await tester.pumpAndSettle();
          }
          expect(find.textContaining('Microsoft Corporation'), findsOneWidget);
        },
      );
    });
  }
}

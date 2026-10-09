// Before/after captures for the inline step timeline (2026-10-09): one turn's
// work line, folded and opened, at 412 dp wide with the app's real fonts, in
// dark, light and Arabic (right to left). Kit parts only, no server calls.
//
//   flutter test --concurrency=1 --dart-define=STEP_TIMELINE_CAPTURE=before \
//     tool/capture/step_timeline_test.dart     # on the old code
//   flutter test --concurrency=1 tool/capture/step_timeline_test.dart
//
// Output: docs/qa/step-timeline-2026-10-09/<before|after>-<scene>-<look>.png
// (look: dark, light, ar). The `before` images were rendered on the code at
// f0443a349, the pill chip and the plain list of rows.
import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_step_timeline.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_work_line.dart';
import 'package:opencode_mobile/ui/kit/kit_text.dart';

import '../../test/goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../../test/goldens/kit/kit_step_timeline_scenes.dart';
import 'fixtures.dart' show captureTheme;

const _prefix = String.fromEnvironment(
  'STEP_TIMELINE_CAPTURE',
  defaultValue: 'after',
);
const _only = String.fromEnvironment('STEP_TIMELINE_ONLY');
const _dir = 'docs/qa/step-timeline-2026-10-09';

/// `before` draws the work as the plain list under the chip (the old code);
/// `after` draws it as the timeline.
final _before = _prefix == 'before';

/// How the line is built: `work` is [KitWorkLine] (what the transcript uses);
/// `timeline` is [KitStepTimeline] directly (the kit part alone).
const _via = String.fromEnvironment('STEP_TIMELINE_VIA', defaultValue: 'work');

Widget _line(StepWords t, String scene) {
  final running = scene.startsWith('running');
  final open = scene == 'expanded' || scene == 'running-open';
  if (_via == 'timeline' && !_before) return stepTimelineWork(t, scene);
  return KitWorkLine(
    counts: stepTimelineCounts(running: running),
    state: running ? KitWorkState.running : KitWorkState.done,
    now: t('Editing lib/ui/router.dart', 'تعديل lib/ui/router.dart'),
    expanded: open,
    steps: stepTimelineSteps(t, running: running, preview: !_before),
  );
}

Widget _scene(StepWords t, String scene) => Padding(
  padding: const EdgeInsetsDirectional.symmetric(horizontal: 16, vertical: 12),
  child: Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      KitText(
        t(
          'I will add a home screen and check it with a test.',
          'سأضيف شاشة رئيسية وأتحقق منها باختبار.',
        ),
      ),
      const SizedBox(height: 12),
      _line(t, scene),
      const SizedBox(height: 12),
      KitText(
        t(
          'The screen is in and the route points to it.',
          'الشاشة جاهزة والمسار يشير إليها.',
        ),
      ),
    ],
  ),
);

Future<void> _shoot(
  WidgetTester tester, {
  required String scene,
  required String look,
}) async {
  final ar = look == 'ar';
  final light = look == 'light';
  String t(String en, String arabic) => ar ? arabic : en;
  tester.view.physicalSize = const Size(412, 1400) * 2;
  tester.view.devicePixelRatio = 2;
  addTearDown(tester.view.reset);
  final key = GlobalKey();
  final theme = AppTheme.forLocale(
    captureTheme(light: light),
    Locale(ar ? 'ar' : 'en'),
  );
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: theme,
      locale: Locale(ar ? 'ar' : 'en'),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: Scaffold(
        body: Align(
          alignment: Alignment.topCenter,
          child: RepaintBoundary(
            key: key,
            child: ColoredBox(
              color: theme.scaffoldBackgroundColor,
              child: _scene(t, scene),
            ),
          ),
        ),
      ),
    ),
  );
  for (var i = 0; i < 4; i++) {
    await tester.pump(const Duration(milliseconds: 120));
  }
  final boundary =
      key.currentContext!.findRenderObject()! as RenderRepaintBoundary;
  await tester.runAsync(() async {
    final image = await boundary.toImage(pixelRatio: 2);
    final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
    Directory(_dir).createSync(recursive: true);
    File(
      '$_dir/$_prefix-$scene-$look.png',
    ).writeAsBytesSync(bytes!.buffer.asUint8List());
  });
}

void main() {
  setUpAll(loadKitGalleryFonts);

  for (final scene in const [
    'collapsed',
    'expanded',
    'running',
    'running-open',
  ]) {
    if (_only.isNotEmpty && _only != scene) continue;
    for (final look in const ['dark', 'light', 'ar']) {
      testWidgets('step timeline · $scene · $look', (tester) async {
        await _shoot(tester, scene: scene, look: look);
      });
    }
  }
}

// Golden renders of screen-review-2's pages (wave 2b), rebuilt from kit
// parts: Review the undo (staged-revert) with its two outcomes, the "keep
// the undo" question (staged-revert-confirm-sheet), the undo sheet
// (stage-revert-sheet), the done state. Phone 412x915 and one wide window
// (1280x800), dark and light (owner decision 2026-09-27: no Arabic), with
// the app's real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/screen_review_2_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/ui/screens/staged_revert_screen.dart';

import '../../tool/capture/fixtures.dart' show captureTheme, loadCaptureFonts;
import '../staged_revert_workflow_test.dart' show setup;

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light, bool text2) => [
  'review_$shot',
  if (text2) 'text2',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

SessionRevert _staged() => SessionRevert(
  messageID: 'msg_1',
  snapshot: 'snap',
  files: [
    FileDiff(
      file: 'lib/settings/settings_screen.dart',
      status: 'modified',
      additions: 12,
      deletions: 4,
    ),
    FileDiff(
      file: 'lib/settings/theme_picker.dart',
      status: 'modified',
      additions: 3,
      deletions: 1,
    ),
  ],
);

Future<void> _shot(
  WidgetTester tester,
  String shot, {
  required bool light,
  required Widget Function(ConnectionController controller) home,
  Future<ConnectionController> Function()? controller,
  Size size = _phone,
  bool text2 = false,
  Future<void> Function()? then,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  final ready = controller == null
      ? (await setup(staged: _staged())).controller
      : await controller();
  try {
    await tester.pumpWidget(
      RepaintBoundary(
        key: boundary,
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
          theme: captureTheme(light: light),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context).copyWith(
              disableAnimations: true,
              textScaler: TextScaler.linear(text2 ? 2 : 1),
            ),
            child: child!,
          ),
          home: home(ready),
        ),
      ),
    );
    await tester.pumpAndSettle();
    if (then != null) {
      await then();
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await expectLater(
      find.byKey(boundary),
      matchesGoldenFile('goldens/${_name(shot, size, light, text2)}.png'),
    );
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

Widget _revert(ConnectionController c) =>
    StagedRevertScreen(controller: c, sessionID: 'a');

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final mode = light ? 'light' : 'dark';

    testWidgets('undo review · $mode', (tester) async {
      await _shot(tester, 'staged_revert_staged', light: light, home: _revert);
    });
    testWidgets('undo review wide · $mode', (tester) async {
      await _shot(
        tester,
        'staged_revert_staged',
        light: light,
        home: _revert,
        size: _wide,
      );
    });
    if (!light) {
      testWidgets('undo review text 2.0 · $mode', (tester) async {
        await _shot(
          tester,
          'staged_revert_staged',
          light: light,
          home: _revert,
          text2: true,
        );
      });
    }
    testWidgets('keep the undo question · $mode', (tester) async {
      await _shot(
        tester,
        'staged_revert_confirm_sheet_keep',
        light: light,
        home: _revert,
        then: () =>
            tester.tap(find.byKey(const ValueKey('commit-staged-revert'))),
      );
    });
    testWidgets('undo done · $mode', (tester) async {
      await _shot(
        tester,
        'staged_revert_done',
        light: light,
        home: _revert,
        then: () async {
          await tester.tap(find.byKey(const ValueKey('clear-staged-revert')));
          await tester.pumpAndSettle();
          await tester.tap(find.byKey(const ValueKey('confirm-staged-revert')));
        },
      );
    });
    testWidgets('undo sheet · $mode', (tester) async {
      await _shot(
        tester,
        'stage_revert_sheet_ready',
        light: light,
        home: (c) => Builder(
          builder: (context) => Center(
            child: TextButton(
              onPressed: () => showStageRevertSheet(
                context,
                controller: c,
                review: c.reviewSessionRevert('a'),
                prompt:
                    'Move the theme picker into Settings and keep the '
                    'current accent when switching packs.',
              ),
              child: const Text('open'),
            ),
          ),
        ),
        controller: () async => (await setup()).controller,
        then: () => tester.tap(find.text('open')),
      );
    });
  }
}

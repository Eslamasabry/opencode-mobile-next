// Golden renders for slice R17 (docs/qa/slice-R17-2026-09-27/README.md): the
// move question naming its destination (with and without working changes,
// and to a cloud machine) and the budget dialog's per-unit reason and Save,
// at the phone size (412x915) and one wide window (1280x800), on a fixed
// clock with real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/slice_r17_golden_test.dart
// and look at every changed image before committing it.
import 'package:clock/clock.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/ui/screens/session_destination_sheet.dart';
import 'package:opencode_mobile/ui/screens/usage_screen.dart';

import '../../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'screen_chat_1_support.dart';
import 'screen_usage_1_support.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String state, Size size, bool light) => [
  'slice_r17_$state',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

Future<void> _shot(
  WidgetTester tester,
  String state,
  Widget Function(GlobalKey boundary) app, {
  required bool light,
  required Size size,
  required Future<void> Function(WidgetTester tester) act,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  // A steady cursor: its blink phase moved with every animation length
  // change before the shot (a 200 ms button ease hid it on 2026-10-27).
  EditableText.debugDeterministicCursor = true;
  final boundary = GlobalKey();
  try {
    await withClock(Clock.fixed(usageNow), () async {
      await tester.pumpWidget(app(boundary));
      await tester.pumpAndSettle();
      await act(tester);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(boundary),
        matchesGoldenFile('goldens/${_name(state, size, light)}.png'),
      );
    });
  } finally {
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    EditableText.debugDeterministicCursor = false;
  }
}

Future<void> _tap(WidgetTester tester, Finder finder) async {
  await tester.ensureVisible(finder);
  await tester.pumpAndSettle();
  await tester.tap(finder);
  await tester.pumpAndSettle();
}

void main() {
  setUpAll(loadCaptureFonts);

  for (final light in [false, true]) {
    final theme = light ? 'light' : 'dark';
    for (final size in const [_phone, _wide]) {
      final where = size == _phone ? 'phone' : 'wide';

      Future<void> move(
        WidgetTester tester,
        String state, {
        ChatOneRepository? repo,
        SessionDestinationMode mode = SessionDestinationMode.move,
        required String pick,
      }) async {
        final conn = await chatOneConnection(repo);
        await _shot(
          tester,
          state,
          (boundary) => chatOneApp(
            opener(
              (context) => showSessionDestinationSheet(
                context,
                controller: conn,
                sessionID: 'ses_a',
                mode: mode,
              ),
            ),
            light: light,
            boundary: boundary,
          ),
          light: light,
          size: size,
          act: (tester) async {
            await _tap(tester, find.text('Open'));
            await _tap(tester, find.byKey(Key(pick)));
          },
        );
      }

      testWidgets('move question with changes ($theme, $where)', (
        tester,
      ) async {
        await move(
          tester,
          'move_with_changes',
          pick: 'move-destination-/work/checkout-retry',
        );
      });

      testWidgets('move question without changes ($theme, $where)', (
        tester,
      ) async {
        await move(
          tester,
          'move_no_changes',
          repo: ChatOneRepository()..changes = const [],
          pick: 'move-destination-/work/checkout-retry',
        );
      });

      testWidgets('cloud move question ($theme, $where)', (tester) async {
        await move(
          tester,
          'warp_with_changes',
          mode: SessionDestinationMode.warp,
          pick: 'warp-destination-ws-review',
        );
      });

      for (final (unit, amount) in const [('usd', '0'), ('tokens', '0')]) {
        testWidgets('$unit budget dialog after an invalid edit '
            '($theme, $where)', (tester) async {
          final h = usageHarness();
          await _shot(
            tester,
            'budget_${unit}_invalid',
            (boundary) => usageApp(
              UsageScreen(controller: h.connection, overview: h.overview),
              light: light,
              boundary: boundary,
            ),
            light: light,
            size: size,
            act: (tester) async {
              await _tap(tester, find.byKey(ValueKey('usage-budget-$unit')));
              await tester.enterText(
                find.byKey(const ValueKey('usage-budget-amount')),
                amount,
              );
              await tester.pumpAndSettle();
            },
          );
        });
      }
    }
  }
}

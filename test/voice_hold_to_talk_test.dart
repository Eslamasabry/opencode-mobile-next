// Hold the mic to talk: a tap, a hold and a slide-away are told apart; a
// hold records until the finger lifts and a slide-away throws it away; the
// tap that a hold leaves behind starts nothing.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_composer.dart';
import 'package:opencode_mobile/ui/kit/chat/kit_mic_hold.dart';

void main() {
  group('KitMicPress (tap, hold, cancel)', () {
    late List<String> log;
    KitMicPress make() => KitMicPress(
      onHoldStart: () => log.add('start'),
      onHoldEnd: (cancelled) => log.add(cancelled ? 'end-cancel' : 'end'),
      onArmedChanged: (armed) => log.add(armed ? 'armed' : 'disarmed'),
    );

    setUp(() => log = []);

    testWidgets('a quick touch is a tap and records nothing', (tester) async {
      {
        final press = make()..down(Offset.zero);
        await tester.pump(const Duration(milliseconds: 120));
        expect(press.up(), KitMicPressOutcome.tap);
        await tester.pump(const Duration(seconds: 2));
        expect(log, isEmpty);
      }
    });

    testWidgets(
      'a touch kept down starts recording once, and lifting ends it',
      (tester) async {
        {
          final press = make()..down(Offset.zero);
          await tester.pump(const Duration(milliseconds: 349));
          expect(log, isEmpty);
          await tester.pump(const Duration(milliseconds: 2));
          expect(log, ['start']);
          await tester.pump(const Duration(seconds: 3));
          expect(press.up(), KitMicPressOutcome.holdCommitted);
          expect(log, ['start', 'end']);
        }
      },
    );

    testWidgets('sliding far away arms the cancel, and lifting then cancels', (
      tester,
    ) async {
      {
        final press = make()..down(const Offset(300, 800));
        await tester.pump(const Duration(milliseconds: 400));
        press.move(const Offset(300, 760));
        expect(log, ['start'], reason: '40 px is still inside');
        press.move(const Offset(300, 700));
        expect(log, ['start', 'armed']);
        expect(press.up(), KitMicPressOutcome.holdCancelled);
        expect(log, ['start', 'armed', 'end-cancel']);
      }
    });

    testWidgets('sliding back inside un-arms the cancel', (tester) async {
      {
        final press = make()..down(const Offset(300, 800));
        await tester.pump(const Duration(milliseconds: 400));
        press.move(const Offset(300, 700));
        press.move(const Offset(300, 790));
        expect(log, ['start', 'armed', 'disarmed']);
        expect(press.up(), KitMicPressOutcome.holdCommitted);
      }
    });

    testWidgets('a drag before the hold is not a hold and not a tap', (
      tester,
    ) async {
      {
        final press = make()..down(Offset.zero);
        press.move(const Offset(0, 60));
        await tester.pump(const Duration(seconds: 1));
        expect(log, isEmpty);
        expect(press.up(), KitMicPressOutcome.none);
      }
    });

    testWidgets('the system taking the touch mid-hold discards the recording', (
      tester,
    ) async {
      {
        final press = make()..down(Offset.zero);
        await tester.pump(const Duration(milliseconds: 400));
        expect(press.cancel(), KitMicPressOutcome.holdCancelled);
        expect(log, ['start', 'end-cancel']);
      }
    });
  });

  group('the composer mic', () {
    late int taps;
    late List<String> hold;

    Future<void> pump(WidgetTester tester) async {
      taps = 0;
      hold = [];
      final controller = TextEditingController();
      final focus = FocusNode();
      addTearDown(controller.dispose);
      addTearDown(focus.dispose);
      tester.view.physicalSize = const Size(412, 915);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.dark(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: Align(
              alignment: Alignment.bottomCenter,
              child: KitComposer(
                controller: controller,
                focusNode: focus,
                hint: 'Ask OpenCode…',
                onSend: () {},
                onVoice: () => taps++,
                micHold: KitComposerMicHold(
                  onStart: () => hold.add('start'),
                  onEnd: (cancelled) =>
                      hold.add(cancelled ? 'end-cancel' : 'end'),
                  onArmedChanged: (armed) =>
                      hold.add(armed ? 'armed' : 'disarmed'),
                ),
                voiceButtonKey: const Key('voice-button'),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
    }

    testWidgets('a tap still starts voice and never a hold', (tester) async {
      await pump(tester);
      await tester.tap(find.byKey(const Key('voice-button')));
      await tester.pump(const Duration(seconds: 1));
      expect(taps, 1);
      expect(hold, isEmpty);
    });

    testWidgets('a hold records until the finger lifts, and starts no tap', (
      tester,
    ) async {
      await pump(tester);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(const Key('voice-button'))),
      );
      await tester.pump(const Duration(milliseconds: 500));
      expect(hold, ['start']);
      await gesture.up();
      await tester.pump(const Duration(seconds: 1));
      expect(hold, ['start', 'end']);
      expect(taps, 0, reason: 'the tap a hold leaves behind is not a tap');
    });

    testWidgets('sliding away and letting go cancels', (tester) async {
      await pump(tester);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(const Key('voice-button'))),
      );
      await tester.pump(const Duration(milliseconds: 500));
      await gesture.moveBy(const Offset(-120, 0));
      await tester.pump();
      await gesture.up();
      await tester.pump(const Duration(seconds: 1));
      expect(hold, ['start', 'armed', 'end-cancel']);
      expect(taps, 0);
    });

    testWidgets('the release is heard after the mic leaves the screen '
        '(voice mode replaces it)', (tester) async {
      await pump(tester);
      final gesture = await tester.startGesture(
        tester.getCenter(find.byKey(const Key('voice-button'))),
      );
      await tester.pump(const Duration(milliseconds: 500));
      expect(hold, ['start']);
      // Recording begins and the composer shows voice mode instead.
      await tester.pumpWidget(const SizedBox.shrink());
      await gesture.up();
      await tester.pump();
      expect(hold, ['start', 'end']);
    });

    testWidgets('a hold, then a later tap: the tap counts', (tester) async {
      await pump(tester);
      final mic = find.byKey(const Key('voice-button'));
      final gesture = await tester.startGesture(tester.getCenter(mic));
      await tester.pump(const Duration(milliseconds: 500));
      await gesture.up();
      await tester.pump(const Duration(seconds: 1));
      await tester.tap(mic);
      await tester.pump(const Duration(seconds: 1));
      expect(taps, 1);
    });
  });
}

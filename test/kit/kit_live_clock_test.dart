// KitLiveClock: the live background's shared slow tick. The look used to
// repaint on every vsync: 60 frames a second on an untouched page (37-79 %
// CPU on the owner's phone, 2026-10-23). It must now draw at most 12 frames
// a second (a slow look fewer), draw nothing between ticks, rest after a quiet spell
// and carry on from the same place on the next touch, and keep stopping
// completely under reduced motion, Calm, Saver, a hidden page and a
// paused app.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

/// A look that counts the frames it was drawn and the times it was given.
class _Counting extends KitLiveStyle {
  _Counting({this.fps = 12});

  final int fps;
  final List<double> times = [];

  int get paints => times.length;

  @override
  String get id => 'counting';

  @override
  String label(AppLocalizations l10n) => 'Counting';

  @override
  int get framesPerSecond => fps;

  @override
  void paint(Canvas canvas, Size size, KitLiveFrame frame) =>
      times.add(frame.t);
}

Widget _app(
  KitLiveStyle style, {
  KitEffects effects = const KitEffects(liveBackground: true),
  bool reduce = false,
  bool tickers = true,
}) => MaterialApp(
  theme: AppTheme.dark(),
  home: MediaQuery(
    data: MediaQueryData(disableAnimations: reduce),
    child: KitEffectsScope(
      effects: effects,
      child: TickerMode(
        enabled: tickers,
        child: KitLiveBackground(
          style: style,
          strength: 1,
          child: const SizedBox.expand(),
        ),
      ),
    ),
  ),
);

/// Pumps [d] of display frames at 60 Hz, as a phone would draw them if
/// anything asked.
Future<void> _run(WidgetTester tester, Duration d) async {
  const frame = Duration(microseconds: 16667);
  for (var t = Duration.zero; t < d; t += frame) {
    await tester.pump(frame);
  }
}

final _clock = KitLiveClock.instance;

void main() {
  setUp(() {
    KitMotion.loops = true;
    _clock.debugReset();
  });
  tearDown(() {
    KitMotion.loops = false;
    _clock.debugReset();
  });

  testWidgets('a look moves 12 times a second, not on every vsync', (
    tester,
  ) async {
    final style = _Counting();
    await tester.pumpWidget(_app(style));
    await tester.pump();
    final before = style.paints;
    await _run(tester, const Duration(seconds: 2));
    final drawn = style.paints - before;
    expect(drawn, inInclusiveRange(23, 25), reason: '12 a second over 2 s');
    // The time it is given follows the clock: 2 s of drift in 2 s.
    expect(style.times.last - style.times[before], closeTo(2, .2));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('nothing asks for a frame between two ticks', (tester) async {
    final style = _Counting();
    await tester.pumpWidget(_app(style));
    await _run(tester, const Duration(milliseconds: 500));
    // Right after a tick has been drawn, no frame is due until the next.
    await tester.pump(KitLiveClock.interval);
    await tester.pump();
    expect(tester.binding.hasScheduledFrame, isFalse);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('a slow look moves 6 times a second', (tester) async {
    final style = _Counting(fps: 6);
    await tester.pumpWidget(_app(style));
    await tester.pump();
    final before = style.paints;
    await _run(tester, const Duration(seconds: 2));
    expect(style.paints - before, inInclusiveRange(11, 13));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('every page moves on the same tick', (tester) async {
    final a = _Counting(), b = _Counting();
    await tester.pumpWidget(
      MaterialApp(
        home: KitEffectsScope(
          effects: const KitEffects(liveBackground: true),
          child: Row(
            children: [
              for (final s in [a, b])
                Expanded(
                  child: KitLiveBackground(
                    style: s,
                    strength: 1,
                    child: const SizedBox.expand(),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    expect(_clock.users, 2);
    await _run(tester, const Duration(seconds: 1));
    expect(a.times, b.times, reason: 'both drawn in the same frames');
    await tester.pumpWidget(const SizedBox());
    expect(_clock.users, 0);
    expect(_clock.running, isFalse);
  });

  testWidgets('rests after a quiet spell; a touch carries on from there', (
    tester,
  ) async {
    KitLiveClock.idleAfter = const Duration(seconds: 2);
    final style = _Counting();
    await tester.pumpWidget(_app(style));
    await _run(tester, const Duration(milliseconds: 2500));
    expect(_clock.awake.value, isFalse);
    expect(_clock.running, isFalse, reason: 'no timer while it rests');
    final rested = style.paints;
    final at = _clock.seconds.value;
    await _run(tester, const Duration(seconds: 3));
    expect(style.paints, rested, reason: 'not one frame while it rests');
    expect(tester.binding.hasScheduledFrame, isFalse);

    // A touch anywhere wakes it, and the drift goes on from where it
    // stopped: no leap over the time it rested.
    final gesture = await tester.startGesture(const Offset(10, 10));
    await gesture.up();
    expect(_clock.awake.value, isTrue);
    await _run(tester, const Duration(milliseconds: 500));
    expect(style.paints, greaterThan(rested));
    expect(style.times.last - at, lessThan(.6));
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('a key wakes it too', (tester) async {
    KitLiveClock.idleAfter = const Duration(seconds: 1);
    await tester.pumpWidget(_app(_Counting()));
    await _run(tester, const Duration(milliseconds: 1500));
    expect(_clock.awake.value, isFalse);
    await tester.sendKeyEvent(LogicalKeyboardKey.keyA);
    expect(_clock.awake.value, isTrue);
    expect(_clock.running, isTrue);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('touches keep it moving', (tester) async {
    KitLiveClock.idleAfter = const Duration(seconds: 2);
    await tester.pumpWidget(_app(_Counting()));
    for (var i = 0; i < 4; i++) {
      await _run(tester, const Duration(seconds: 1));
      await tester.tapAt(const Offset(10, 10));
    }
    expect(_clock.awake.value, isTrue);
    expect(_clock.running, isTrue);
    await tester.pumpWidget(const SizedBox());
  });

  final still = <String, Widget Function(KitLiveStyle)>{
    'reduced motion': (s) => _app(s, reduce: true),
    'Animations: Calm': (s) => _app(
      s,
      effects: const KitEffects(
        liveBackground: true,
        motion: KitMotionLevel.calm,
      ),
    ),
    'Animations: Off': (s) => _app(
      s,
      effects: const KitEffects(
        liveBackground: true,
        motion: KitMotionLevel.off,
      ),
    ),
    'Saver': (s) =>
        _app(s, effects: const KitEffects(liveBackground: true).underSaver),
    'Live background off': (s) =>
        _app(s, effects: const KitEffects(liveBackground: false)),
    'a hidden page': (s) => _app(s, tickers: false),
  };
  for (final MapEntry(key: name, value: build) in still.entries) {
    testWidgets('$name: no clock, no timer, not one frame', (tester) async {
      final style = _Counting();
      await tester.pumpWidget(build(style));
      await tester.pump();
      final drawn = style.paints;
      expect(_clock.users, 0);
      expect(_clock.running, isFalse);
      await _run(tester, const Duration(seconds: 1));
      expect(style.paints, drawn);
      expect(tester.binding.hasScheduledFrame, isFalse);
    });
  }

  testWidgets('a hidden page stops; shown again it moves', (tester) async {
    final style = _Counting();
    await tester.pumpWidget(_app(style, tickers: false));
    expect(_clock.users, 0);
    await tester.pumpWidget(_app(style));
    expect(_clock.users, 1);
    expect(_clock.running, isTrue);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('the app in the background stops the clock', (tester) async {
    await tester.pumpWidget(_app(_Counting()));
    expect(_clock.running, isTrue);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    expect(_clock.users, 0);
    expect(_clock.running, isFalse);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(_clock.running, isTrue);
    await tester.pumpWidget(const SizedBox());
  });
}

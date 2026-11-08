import 'package:flutter/material.dart';

import '../kit_illustration.dart';
import '../kit_tokens.dart';
import 'setup_cast.dart';
import '../kit_motion.dart';

/// What the phone in [SetupPhoneScene] is doing.
enum SetupPhoneMood {
  /// First run: the screen becomes a terminal and the portal forms on it.
  fresh,

  /// OpenCode starting (or the app connecting to it): the portal on the
  /// screen breathes, the cursor blinks and a dot circles the phone.
  starting,

  /// OpenCode installed and ready here: the portal on a lit screen.
  ready,

  /// Stopped: a dark screen, a quiet grey portal and a small moon.
  stopped,
}

/// A phone with a rounded screen, the setup's hero (design standard §10):
/// what OpenCode on this phone is doing, in the brand's line.
class SetupPhoneScene extends KitScene {
  const SetupPhoneScene({this.mood = SetupPhoneMood.fresh});

  final SetupPhoneMood mood;

  @override
  Size get box => const Size(160, 120);

  @override
  bool differs(covariant SetupPhoneScene old) =>
      super.differs(old) || old.mood != mood;

  static const _center = Offset(80, 60);
  static const _frameRect = Rect.fromLTRB(54, 8, 106, 112);
  static const _screenRect = Rect.fromLTRB(61, 17, 99, 101);
  static final Path _frame = SetupCast.phone(_frameRect, 12);
  static final RRect _screen = RRect.fromRectAndRadius(
    _screenRect,
    const Radius.circular(5),
  );
  static final Path _prompt = SetupCast.prompt(const Offset(66, 26), 8);
  static final Path _moon = Path.combine(
    PathOperation.difference,
    Path()..addOval(Rect.fromCircle(center: const Offset(128, 26), radius: 9)),
    Path()..addOval(Rect.fromCircle(center: const Offset(133, 21), radius: 8)),
  );

  @override
  void paint(Canvas canvas, KitSceneFrame frame) {
    final p = frame.palette;
    final t = frame.entrance;
    final breath = frame.looping ? KitDraw.wave(frame.loop) : 0.0;
    final stopped = mood == SetupPhoneMood.stopped;

    // The wash behind the phone grows in, and swells with each breath. A
    // stopped phone's wash is the quiet neutral at the accent wash's own
    // strength (LOOK-36).
    final wash = KitDraw.interval(t, 0, .4);
    if (wash > 0) {
      canvas.drawCircle(
        _center,
        (50 + 3 * breath) * (.7 + .3 * wash),
        KitDraw.fill(
          stopped
              ? KitDraw.fade(p.line, KitTokens.sceneWashAlpha * wash)
              : KitDraw.fade(p.accentSoft, wash),
        ),
      );
    }

    // A dot circling the phone while it starts.
    if (mood == SetupPhoneMood.starting && frame.looping) {
      final dot = SetupCast.orbit(_center, 56, frame.loop);
      canvas.drawCircle(dot, 2.6, KitDraw.fill(p.accent));
      canvas.drawCircle(
        SetupCast.orbit(_center, 56, frame.loop - .04),
        1.8,
        KitDraw.fill(KitDraw.fade(p.accent, .45)),
      );
    }

    // The screen lights, then the frame draws itself around it.
    final lit = KitDraw.interval(t, .25, .55);
    if (lit > 0) {
      canvas.drawRRect(
        _screen,
        KitDraw.fill(KitDraw.fade(stopped ? p.surface : p.surface, lit)),
      );
      if (!stopped) {
        canvas.drawRRect(
          _screen,
          KitDraw.fill(KitDraw.fade(p.accentSoft, lit * .9)),
        );
      }
    }
    KitDraw.partialPath(
      canvas,
      _frame,
      KitDraw.interval(t, .05, .5),
      KitDraw.pen(stopped ? p.muted : p.ink),
    );
    // The home indicator.
    final home = KitDraw.interval(t, .45, .6);
    if (home > 0) {
      canvas.drawLine(
        const Offset(74, 106.5),
        Offset(74 + 12 * home, 106.5),
        KitDraw.pen(KitDraw.fade(p.muted, .8), 2),
      );
    }

    switch (mood) {
      case SetupPhoneMood.fresh:
      case SetupPhoneMood.starting:
        _promptLine(canvas, frame, cursor: true);
        _portal(canvas, frame, breath, begin: .5);
      case SetupPhoneMood.ready:
        _portal(canvas, frame, breath, begin: .4, center: const Offset(80, 58));
      case SetupPhoneMood.stopped:
        SetupCast.portal(
          canvas,
          center: const Offset(80, 60),
          size: 30,
          color: KitDraw.fade(p.muted, .7),
          upper: KitDraw.interval(t, .45, .8),
          lower: KitDraw.interval(t, .55, .9),
          open: -2,
          width: 4,
        );
        final moon = KitDraw.interval(t, .7, 1);
        if (moon > 0) {
          canvas.drawPath(
            _moon,
            KitDraw.fill(KitDraw.fade(p.muted, .8 * moon)),
          );
        }
        _dots(canvas, frame, quiet: true);
        return;
    }
    _sparkles(canvas, frame, breath);
    _dots(canvas, frame);
  }

  void _promptLine(Canvas canvas, KitSceneFrame frame, {required bool cursor}) {
    final p = frame.palette;
    final t = frame.entrance;
    KitDraw.partialPath(
      canvas,
      _prompt,
      KitDraw.interval(t, .4, .55),
      KitDraw.pen(p.accent, KitDraw.hairline),
    );
    final typed = KitDraw.interval(t, .5, .62);
    if (typed > 0 && (!cursor || SetupCast.cursorOn(frame))) {
      canvas.drawLine(
        const Offset(73, 34),
        Offset(73 + 7 * typed, 34),
        KitDraw.pen(p.accent, KitDraw.hairline),
      );
    }
  }

  void _portal(
    Canvas canvas,
    KitSceneFrame frame,
    double breath, {
    required double begin,
    Offset center = const Offset(80, 66),
  }) {
    final p = frame.palette;
    final t = frame.entrance;
    SetupCast.portal(
      canvas,
      center: center,
      size: mood == SetupPhoneMood.ready ? 34 : 30,
      color: p.accent,
      upper: KitDraw.interval(t, begin, begin + .3),
      lower: KitDraw.interval(t, begin + .1, begin + .4),
      open: 1.6 * breath,
      width: 4.5,
    );
    final land = KitDraw.interval(t, begin + .3, 1, KitMotion.land);
    if (land > 0) {
      final spark = frame.looping
          ? SetupCast.orbit(center, 2.4, frame.loop)
          : center;
      canvas.drawCircle(spark, 3.2 * land, KitDraw.fill(p.accent));
    }
  }

  void _sparkles(Canvas canvas, KitSceneFrame frame, double breath) {
    final p = frame.palette;
    final t = frame.entrance;
    final big = KitDraw.interval(t, .7, .95, KitMotion.land);
    final small = KitDraw.interval(t, .8, 1, KitMotion.land);
    SetupCast.sparkle(
      canvas,
      const Offset(126, 26),
      9 + 1.2 * breath,
      p.accent,
      pop: big,
    );
    SetupCast.sparkle(
      canvas,
      const Offset(138, 46),
      4.5,
      KitDraw.fade(p.accent, .8),
      pop: small,
    );
    SetupCast.sparkle(
      canvas,
      const Offset(32, 90),
      5,
      KitDraw.fade(p.accent, .55),
      pop: small,
    );
  }

  void _dots(Canvas canvas, KitSceneFrame frame, {bool quiet = false}) {
    final p = frame.palette;
    final shown = KitDraw.interval(frame.entrance, .6, 1);
    if (shown <= 0) return;
    final paint = KitDraw.fill(
      KitDraw.fade(p.muted, (quiet ? .35 : .5) * shown),
    );
    canvas.drawCircle(const Offset(34, 34), 2.2, paint);
    canvas.drawCircle(const Offset(22, 62), 1.6, paint);
    canvas.drawCircle(const Offset(132, 94), 2.4, paint);
  }
}

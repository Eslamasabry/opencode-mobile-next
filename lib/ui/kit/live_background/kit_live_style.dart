import 'dart:ui';

import '../../../l10n/app_localizations.dart';

/// One look of the live background (Settings › Appearance › Live background).
///
/// A style only paints one frame over the flat ground; the machinery around
/// it (the page ground, the shared clock of 12 ticks a second that rests
/// when nobody touches the app, pausing when the app is not in front or
/// motion is reduced, the brighten while a reply runs) is
/// [KitLiveBackground]'s and is the same for every style. Rules:
///
/// - no blur of what is under it, nothing see-through that a card relies on
///   (no glass); cards, rows and sheets stay solid on top;
/// - at [KitLiveFrame.strength] 1 the look stays within the alpha ceilings
///   ([KitLivePalette.ceiling]: 6 % dark, 4.5 % light), or measures its
///   brightest spot against the theme's own text colours as the aurora does,
///   so text keeps its contrast (test/kit/kit_live_background_test.dart
///   checks every style);
/// - never red; amber is not used;
/// - a style that keeps state (particles, a trail) keeps it in
///   [KitLiveFrame.memory], never in fields, so one const instance can serve
///   many tiles at once.
abstract class KitLiveStyle {
  const KitLiveStyle();

  /// Stable storage key (`oc.effectsLiveStyle`); never renamed.
  String get id;

  /// The name shown in the picker.
  String label(AppLocalizations l10n);

  /// Paints one frame over the ground, which is already drawn.
  void paint(Canvas canvas, Size size, KitLiveFrame frame);

  /// How far past [KitLivePalette.ceiling] this look may go. Sparse looks
  /// (thin lines, glyphs, a few dots covering little area) override it, up
  /// to 2.0, so they can be seen; the contrast test paints each look at
  /// strength `ceilingFactor` and still demands AA text contrast.
  double get ceilingFactor => 1.0;

  /// Whether the quieter text over this look keeps a halo of the ground
  /// ([KitTextHalo]) in [palette]: a look brighter than that text could
  /// read on asks for one, and the contrast test then measures text2 and
  /// text3 through it.
  bool wantsTextHalo(KitLivePalette palette) => false;

  /// How often a second this look is drawn while it moves, at most the
  /// shared clock's 12 (`KitLiveClock.framesPerSecond`); a look that
  /// drifts slowly enough asks for fewer.
  int get framesPerSecond => 12;

  /// Loads whatever the style needs (a [FragmentProgram]); called once before
  /// the first frame. Until it completes the page shows the flat ground.
  Future<void> warmUp() async {}
}

/// What a style is given for one frame.
class KitLiveFrame {
  const KitLiveFrame({
    required this.t,
    required this.running,
    required this.strength,
    required this.palette,
    required this.memory,
  });

  /// Seconds on the shared clock. Styles loop on their own period; the clock
  /// never resets, so a page change carries on where the last one was.
  final double t;

  /// 0 idle .. 1 a reply is running, eased (about 600 ms either way).
  final double running;

  /// 1 = the strength that ships. Previews may differ; styles scale their
  /// alpha by it and never exceed [KitLivePalette.ceiling] at 1.
  final double strength;

  final KitLivePalette palette;
  final KitLiveMemory memory;
}

/// The theme's colours a style may use.
class KitLivePalette {
  const KitLivePalette({
    required this.ground,
    required this.ink,
    required this.muted,
    required this.faint,
    required this.green,
    required this.teal,
    required this.blue,
    required this.glow,
    required this.dark,
  });

  final Color ground;

  /// Primary text colour.
  final Color ink;

  /// The secondary and the faintest text colours (text2, text3): a bright
  /// look must keep them readable too.
  final Color muted;
  final Color faint;

  /// The theme's accent: the composer glow's hue.
  final Color green;
  final Color teal;
  final Color blue;

  /// The running glow's colour (the composer's).
  final Color glow;
  final bool dark;

  /// The most any single layer may be opaque at strength 1.
  double get ceiling => dark ? 0.06 : 0.045;
}

/// Per-style mutable state for one painted surface (particles, trails).
class KitLiveMemory {
  final Map<String, Object?> _slots = {};

  /// The value kept under [key], made by [make] the first time.
  T of<T>(String key, T Function() make) =>
      (_slots.putIfAbsent(key, make)) as T;

  void clear() => _slots.clear();
}

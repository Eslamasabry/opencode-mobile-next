part of 'kit_composer.dart';

/// The box's frame: the solid surface and, unless the person turned
/// "Glowing border while replying" off, the glowing border around it while a
/// reply runs: the classic ring (the original, a bright highlight travelling
/// the border over a breathing halo) or the soft ring sweep, in one or two
/// colours at the chosen speed. Nothing else lives on the composer's edge:
/// what the reply is doing is written in the turn ([KitTurnLive]), and the
/// box stays idle.
///
/// Full motion travels, Calm holds a still glow, and Off and the system's
/// remove-animations draw none. The lap's ticker runs only while a reply
/// does, and stops with the route (TickerMode).
class _ComposerFrame extends StatefulWidget {
  const _ComposerFrame({
    required this.surface,
    required this.radius,
    required this.active,
    this.activityGlow,
  });

  /// Null follows [KitEffects.activityGlow].
  final bool? activityGlow;
  final Widget surface;
  final double radius;

  /// A reply runs.
  final bool active;

  @override
  State<_ComposerFrame> createState() => _ComposerFrameState();
}

class _ComposerFrameState extends State<_ComposerFrame>
    with SingleTickerProviderStateMixin {
  late final AnimationController _lap = AnimationController(
    vsync: this,
    duration: _Classic.cycle,
  );
  bool _running = false;
  Duration _duration = _Classic.cycle;

  /// Re-reads the choices; starts or stops the lap to match.
  void _sync(bool travel, KitGlowSpeed speed) {
    final lap = Duration(microseconds: (1e6 / _Classic.laps(speed)).round());
    if (lap != _duration) {
      _duration = lap;
      _lap.duration = lap;
      if (_running) unawaited(_lap.repeat());
    }
    if (travel == _running) return;
    _running = travel;
    if (travel) {
      unawaited(_lap.repeat());
    } else {
      _lap.stop();
      _lap.value = 0;
    }
  }

  @override
  void dispose() {
    _lap.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final effects = KitEffects.of(context);
    final on = widget.activityGlow ?? effects.activityGlow;
    final level = effects.motion;
    final allowed =
        on && level != KitMotionLevel.off && !KitMotion.reduced(context);
    final classic = effects.glowStyle == KitGlowStyle.classic;
    final mode = level == KitMotionLevel.calm
        ? _GlowMode.calm
        : KitMotion.loops
        ? _GlowMode.live
        : _GlowMode.frame;
    _sync(
      allowed && classic && widget.active && mode == _GlowMode.live,
      effects.glowSpeed,
    );
    final (primary, partner) = _glowHues(context, effects.glowColours);
    final show = allowed && (classic ? widget.active : true);
    // Three fixed slots (halo, surface, ring), each the same kind of widget
    // whatever is chosen: Stack matches unkeyed children by type, and a
    // mismatch would rebuild the surface and lose the field's state when a
    // reply starts or a choice changes.
    return Stack(
      clipBehavior: Clip.none,
      children: [
        Positioned.fill(
          child: show && classic
              ? _ClassicHalo(
                  lap: _lap,
                  breathing: mode == _GlowMode.live,
                  radius: widget.radius,
                  color: primary,
                )
              : const SizedBox.shrink(),
        ),
        widget.surface,
        Positioned.fill(
          child: !show
              ? const SizedBox.shrink()
              : classic
              ? _ClassicRing(
                  lap: _lap,
                  travels: mode == _GlowMode.live,
                  radius: widget.radius,
                  primary: primary,
                  partner: partner,
                  mode: mode,
                )
              : _ActivityGlow(
                  active: widget.active,
                  mode: mode,
                  radius: widget.radius,
                  primary: primary,
                  partner: partner,
                  lapsPerSecond: effects.glowSpeed.softLaps,
                ),
        ),
      ],
    );
  }
}

/// The composer's outline: the rounded rectangle the surface clips to,
/// inset by half a hairline so the ring sits on the border. Starts at the
/// top centre and runs clockwise.
Path _composerOutline(Size size, double radius) {
  const inset = 0.5;
  final r = math.max(radius - inset, 1.0);
  final l = inset, t = inset, rt = size.width - inset, b = size.height - inset;
  return Path()
    ..moveTo(size.width / 2, t)
    ..lineTo(rt - r, t)
    ..arcTo(
      Rect.fromLTWH(rt - 2 * r, t, 2 * r, 2 * r),
      -math.pi / 2,
      math.pi / 2,
      false,
    )
    ..lineTo(rt, b - r)
    ..arcTo(
      Rect.fromLTWH(rt - 2 * r, b - 2 * r, 2 * r, 2 * r),
      0,
      math.pi / 2,
      false,
    )
    ..lineTo(l + r, b)
    ..arcTo(
      Rect.fromLTWH(l, b - 2 * r, 2 * r, 2 * r),
      math.pi / 2,
      math.pi / 2,
      false,
    )
    ..lineTo(l, t + r)
    ..arcTo(Rect.fromLTWH(l, t, 2 * r, 2 * r), math.pi, math.pi / 2, false)
    ..close();
}

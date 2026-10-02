part of 'kit_composer.dart';

/// The box's frame: the solid surface and, only when the person has turned
/// "Glowing border while replying" on, the soft ring sweep around it while a
/// reply runs. Nothing else lives on the composer's edge: what the reply is
/// doing is written in the turn ([KitTurnLive]), and the box stays idle.
///
/// The ring is Full motion's sweep, Calm's still glow, or nothing under Off
/// and reduced motion.
class _ComposerFrame extends StatelessWidget {
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
  Widget build(BuildContext context) {
    final on = activityGlow ?? KitEffects.of(context).activityGlow;
    final level = KitEffects.of(context).motion;
    final allowed =
        on && level != KitMotionLevel.off && !KitMotion.reduced(context);
    final roles = KitTokens.of(context).roles;
    // One tree whether or not the ring is on, so the field keeps its state
    // when the setting changes.
    return Stack(
      clipBehavior: Clip.none,
      children: [
        surface,
        if (!allowed)
          const SizedBox.shrink()
        else
          _ActivityGlow(
            active: active,
            mode: level == KitMotionLevel.calm
                ? _GlowMode.calm
                : KitMotion.loops
                ? _GlowMode.live
                : _GlowMode.frame,
            radius: radius,
            primary: roles.accent,
            // Never attention: amber means "needs you". The pack's keyword
            // hue is its own second colour and carries no meaning.
            partner: roles.codeKeyword,
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

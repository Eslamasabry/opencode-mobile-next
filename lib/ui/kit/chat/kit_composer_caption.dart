part of 'kit_composer.dart';

/// A failed send, in the composer's top row: neutral words after the neutral
/// error glyph, Retry, and Details when there is technical text (LOOK-5, B2:
/// a failure is never red). The words are a live region.
class _FailureRow extends StatelessWidget {
  const _FailureRow({required this.failure});

  final KitComposerFailure failure;

  @override
  Widget build(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    Widget text(String line) => KitText(
      line,
      role: KitTextRole.caption,
      tone: KitTextTone.primary,
      maxLines: 1,
    );
    return Padding(
      padding: EdgeInsetsDirectional.only(
        start: tokens.space3,
        end: tokens.space2,
        top: tokens.space1,
      ),
      child: Row(
        children: [
          const KitIcon(
            AppIconography.error,
            size: KitIconSize.small,
            tone: KitTextTone.primary,
          ),
          SizedBox(width: tokens.space1),
          Flexible(
            child: Semantics(
              liveRegion: true,
              label: failure.words,
              child: ExcludeSemantics(child: text(failure.words)),
            ),
          ),
          KitTappable(
            tappableKey: failure.retryKey,
            label: l10n.kitComposerRailRetry,
            shape: KitShape.button,
            onTap: failure.onRetry,
            child: SizedBox(
              height: tokens.minTarget - 8,
              child: Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: tokens.space2),
                  child: text('· ${l10n.kitComposerRailRetry}'),
                ),
              ),
            ),
          ),
          if (failure.onDetails != null)
            KitTappable(
              label: l10n.kitDetails,
              shape: KitShape.button,
              onTap: failure.onDetails,
              child: SizedBox(
                width: tokens.minTarget - 16,
                height: tokens.minTarget - 8,
                child: const Center(
                  child: KitIcon(AppIconography.info, size: KitIconSize.small),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _Circle extends StatelessWidget {
  const _Circle({
    super.key,
    required this.kind,
    required this.label,
    required this.onTap,
    this.tappableKey,
    this.shortcut,
    this.working = false,
    this.disabledReason,
  });

  final _CircleKind kind;
  final String label;
  final VoidCallback? onTap;
  final Key? tappableKey;
  final String? shortcut;
  final bool working;
  final String? disabledReason;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final stop = kind == _CircleKind.stop;
    final disabled = kind == _CircleKind.sendDisabled;
    // Stop is neutral (owner decision 2 Oct, 02B): a text-colour circle with
    // a ground square. Red is reserved for destructive actions.
    final fill = stop
        ? roles.text1
        : disabled
        ? roles.surface3
        : roles.accent;
    final ink = stop
        ? roles.ground
        : disabled
        ? roles.text3
        : roles.onAccent;
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final still = KitMotion.reduced(context);

    Widget glyph;
    if (working) {
      glyph = still
          ? Icon(
              AppIconography.statusDot,
              size: KitIconSize.small.logical,
              color: ink,
            )
          : SizedBox.square(
              dimension: KitIconSize.small.logical,
              child: CircularProgressIndicator(strokeWidth: 2, color: ink),
            );
    } else if (stop) {
      const side = KitTokens.composerStopSquare;
      glyph = SizedBox.square(
        key: const ValueKey('kit-composer-stop-square'),
        dimension: side,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: ink,
            borderRadius: BorderRadius.circular(side / 4),
          ),
        ),
      );
    } else {
      final icon = switch (kind) {
        _CircleKind.mic || _CircleKind.listen => AppIconography.mic,
        _CircleKind.voiceDone => AppIconography.check,
        _ => AppIconography.send,
      };
      glyph = Icon(icon, size: KitIconSize.small.logical, color: ink);
      // The send glyph points the way the words go (LAY-8); the mic does
      // not mirror.
      if (rtl && icon == AppIconography.send) {
        glyph = Transform.flip(flipX: true, child: glyph);
      }
    }

    Widget disc = SizedBox.square(
      dimension: KitTokens.composerActionSize,
      child: DecoratedBox(
        key: ValueKey('kit-composer-circle-${kind.name}'),
        decoration: BoxDecoration(color: fill, shape: BoxShape.circle),
        child: Center(child: glyph),
      ),
    );
    // Stop wears a slowly turning ring: the work has no known length.
    if (stop) {
      disc = _StopRing(color: roles.text2, track: roles.hairline, child: disc);
    }

    return KitTappable(
      tappableKey: tappableKey,
      shape: KitShape.circle,
      label: label,
      tooltip: label,
      shortcut: shortcut,
      disabledReason: onTap == null ? (disabledReason ?? label) : null,
      onTap: onTap,
      child: SizedBox.square(
        dimension: tokens.minTarget,
        child: Center(child: disc),
      ),
    );
  }
}

/// The ring around Stop (owner decision 2 Oct, 11B): an arc that turns
/// slowly and never stops at a value, because nobody knows how much work is
/// left. Full motion turns at [KitMotion.stopRingLapsPerSecond], Calm at
/// half that; Off, reduced motion and tests hold the arc still.
class _StopRing extends StatefulWidget {
  const _StopRing({
    required this.color,
    required this.track,
    required this.child,
  });

  final Color color;
  final Color track;
  final Widget child;

  @override
  State<_StopRing> createState() => _StopRingState();
}

class _StopRingState extends State<_StopRing>
    with SingleTickerProviderStateMixin {
  late final AnimationController _turn = AnimationController(vsync: this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final level = KitEffects.of(context).motion;
    final turns =
        KitMotion.loops &&
        !KitMotion.reduced(context) &&
        level != KitMotionLevel.off;
    if (!turns) {
      _turn.stop();
      return;
    }
    final laps = level == KitMotionLevel.calm
        ? KitMotion.stopRingCalmLapsPerSecond
        : KitMotion.stopRingLapsPerSecond;
    _turn.duration = Duration(milliseconds: (1000 / laps).round());
    if (!_turn.isAnimating) unawaited(_turn.repeat());
  }

  @override
  void dispose() {
    _turn.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => RepaintBoundary(
    child: CustomPaint(
      key: const ValueKey('kit-composer-stop-ring'),
      foregroundPainter: _RingPainter(
        turn: _turn,
        color: widget.color,
        track: widget.track,
      ),
      // The disc keeps its size; the ring turns just outside it.
      child: SizedBox.square(
        dimension: KitTokens.composerActionSize + 4,
        child: Center(child: widget.child),
      ),
    ),
  );
}

class _RingPainter extends CustomPainter {
  _RingPainter({required this.turn, required this.color, required this.track})
    : super(repaint: turn);

  final Animation<double> turn;
  final Color color;
  final Color track;

  @override
  void paint(Canvas canvas, Size size) {
    const width = 2.0;
    final rect = (Offset.zero & size).deflate(width / 2);
    canvas
      ..drawArc(
        rect,
        0,
        2 * math.pi,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = width
          ..color = track,
      )
      ..drawArc(
        rect,
        2 * math.pi * turn.value - math.pi / 2,
        2 * math.pi * 0.28,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = width
          ..strokeCap = StrokeCap.round
          ..color = color,
      );
  }

  @override
  bool shouldRepaint(_RingPainter old) =>
      old.color != color || old.track != track;
}

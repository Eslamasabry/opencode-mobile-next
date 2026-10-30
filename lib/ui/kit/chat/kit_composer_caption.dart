part of 'kit_composer.dart';

/// A failed send, in the composer's top row: neutral words after the neutral
/// error glyph, Retry (or the failure's own fix in its place), and Details
/// when there is technical text (LOOK-5, B2: a failure is never red). The
/// words are a live region.
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
            tappableKey: failure.onAction != null
                ? failure.actionKey
                : failure.retryKey,
            label: failure.actionLabel ?? l10n.kitComposerRailRetry,
            shape: KitShape.button,
            onTap: failure.onAction ?? failure.onRetry,
            child: SizedBox(
              height: tokens.minTarget - 8,
              child: Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(horizontal: tokens.space2),
                  child: text(
                    '· ${failure.actionLabel ?? l10n.kitComposerRailRetry}',
                  ),
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
    this.secondary = false,
    this.menu = const [],
  });

  /// A long-press (and right-click, Shift+F10) menu on the button: Send's
  /// "Send later or repeat", Stop's "Stop after this step".
  final List<KitMenuItem> menu;

  /// Not the main action now (the mic beside Stop): a quiet surface, not
  /// the solid accent.
  final bool secondary;

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
    // Send for an agent that stops its reply to take the message: an amber
    // pill with a stop square and the words, not a disc.
    final pill = kind == _CircleKind.stopAndSend;
    final disabled = kind == _CircleKind.sendDisabled;
    // Stop is neutral (owner decision 2 Oct, 02B): a text-colour circle with
    // a ground square. Red is reserved for destructive actions.
    final fill = pill
        ? roles.attentionFill
        : stop
        ? roles.text1
        : disabled
        ? roles.surface3
        : secondary
        ? roles.surface3
        : roles.accent;
    // The main action keeps the accent and Stop its text colour as their
    // own fills; the quiet discs (the mic beside Stop, a send that cannot
    // run yet) are the style's own round control.
    final primary = !stop && !disabled && !secondary;
    final Color? own = primary || pill || stop ? fill : null;
    final ink = pill
        ? roles.onAttentionFill
        : stop
        ? roles.ground
        : disabled
        ? roles.text3
        : secondary
        ? roles.text2
        // Hook H3: a style may say the words on its accent.
        : KitSurfaceLook.wordsOn(
            context,
            fallback: roles.onAccent,
            color: roles.accent,
          );
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
    } else if (stop || pill) {
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

    Widget disc = SizedBox(
      width: pill ? null : KitTokens.composerActionSize,
      height: KitTokens.composerActionSize,
      child: kitSurfaceBox(
        key: ValueKey('kit-composer-circle-${kind.name}'),
        style: KitSurfaceLook.styleOf(context),
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: fill,
            shape: pill ? BoxShape.rectangle : BoxShape.circle,
            borderRadius: pill
                ? BorderRadius.circular(KitTokens.composerActionSize / 2)
                : null,
          ),
          role: KitSurfaceRole.control,
          level: KitSurfaceLevel.surface3,
          circle: !pill,
          color: own,
        ),
        child: pill
            ? Padding(
                padding: EdgeInsets.symmetric(horizontal: tokens.space4),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    glyph,
                    SizedBox(width: tokens.space2),
                    ExcludeSemantics(
                      child: Text(
                        label,
                        style: KitText.styleOf(
                          context,
                          KitTextRole.body,
                        ).copyWith(color: ink),
                      ),
                    ),
                  ],
                ),
              )
            : Center(child: glyph),
      ),
    );
    // Stop wears a slowly turning ring: the work has no known length.
    if (stop) {
      // In the Accent (Settings › Appearance), the main colour keeping the
      // disc and the glow. A waiting stop shows the ring still and whole.
      disc = _StopRing(
        color: roles.partner,
        track: roles.partner.withValues(alpha: .75),
        child: disc,
      );
    }

    return KitTappable(
      tappableKey: tappableKey,
      shape: KitShape.circle,
      label: label,
      tooltip: label,
      shortcut: shortcut,
      disabledReason: onTap == null ? (disabledReason ?? label) : null,
      onTap: onTap,
      menu: onTap == null ? const [] : menu,
      child: SizedBox(
        width: pill ? null : tokens.minTarget,
        height: tokens.minTarget,
        child: Center(child: disc),
      ),
    );
  }
}

/// The ring around Stop (owner decision 2 Oct, 11B): an arc that turns
/// slowly and never stops at a value, because nobody knows how much work is
/// left. It turns at [KitMotion.stopRingLapsPerSecond] only when it is the
/// one moving part ([_movingPart]): while the glowing border is drawn and
/// travels it shows its track only, and under Calm, Off or reduced motion
/// (and in tests) the arc holds still.
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
    _sync();
  }

  void _sync() {
    if (_MovingPartScope.of(context) != _MovingPart.stopRing ||
        !KitMotion.loops) {
      _turn.stop();
      return;
    }
    _turn.duration = Duration(
      milliseconds: (1000 / KitMotion.stopRingLapsPerSecond).round(),
    );
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
        arc: _MovingPartScope.of(context) != _MovingPart.glow,
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
  _RingPainter({
    required this.turn,
    required this.color,
    required this.track,
    required this.arc,
  }) : super(repaint: turn);

  /// False while the glow is the moving part: the track alone, calm.
  final bool arc;

  final Animation<double> turn;
  final Color color;
  final Color track;

  @override
  void paint(Canvas canvas, Size size) {
    const width = 2.0;
    final rect = (Offset.zero & size).deflate(width / 2);
    canvas.drawArc(
      rect,
      0,
      2 * math.pi,
      false,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = width
        ..color = track,
    );
    if (!arc) return;
    canvas.drawArc(
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
      old.color != color || old.track != track || old.arc != arc;
}

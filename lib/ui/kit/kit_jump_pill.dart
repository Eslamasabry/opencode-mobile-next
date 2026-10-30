// KitJumpPill: the floating "3 new · Jump to latest" pill on transcripts,
// team output and logs (and its "Earlier messages" twin at the top)
// (docs/ux-system/kit-api/KitJumpPill.md; kit-v2.md §1.19, §8.2; STANDARDS
// Appendix A #24, KIT-31, LOOK-20, LOOK-27, MOT-2).
//
// A solid pill (a floating surface under Settings › Appearance › Style:
// soft raises it) with kit timings instead of the literal
// 200 ms `easeOutBack` pills it replaces. Always a button with words; an
// arrow alone is never enough.
//
// States: default (visible) and hidden (`visible: false`: kept mounted,
// invisible, not hittable, out of semantics and focus). No loading, empty,
// error or disabled — a pill that cannot act is hidden. No working — a
// jump is instant.
import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import 'kit_bottom_inset.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Which edge of its scroll area a pill belongs to.
enum KitJumpEdge {
  /// Newer content below: "3 new · Jump to latest". Arrow down.
  bottom,

  /// Older content above: "Earlier messages". Arrow up.
  top,
}

/// A floating pill that returns to one end of a scroll area (KitJumpPill.md
/// "Purpose", "Non-goals"). The host decides [visible] (not at the newest
/// end; for logs, the person scrolled up) and owns [onPressed] (scrolling);
/// the pill never scrolls anything itself and never invents its own count.
///
/// States: none — the host shows it only when it can act.
class KitJumpPill extends StatelessWidget {
  const KitJumpPill({
    super.key,
    required this.label,
    required this.onPressed,
    required this.visible,
    this.icon = AppIconography.down,
    this.edge = KitJumpEdge.bottom,
    this.pillKey,
  });

  /// The top pill: [edge] top, [icon] the up chevron.
  const KitJumpPill.older({
    super.key,
    required this.label,
    required this.onPressed,
    required this.visible,
    this.pillKey,
  }) : icon = AppIconography.chevronUp,
       edge = KitJumpEdge.top;

  /// The words; see [latestLabel]. Never truncated to a few letters — at
  /// 200 % text it wraps onto a second line and the pill grows.
  final String label;

  /// The host's own action (usually a scroll). Never the pill's own concern.
  final VoidCallback onPressed;

  /// Keep mounted; toggle this so the pill can animate out. `false` leaves
  /// the pill in the tree, invisible, not hittable, out of semantics and
  /// focus.
  final bool visible;

  /// `AppIconography.down` for the default constructor (K2 names
  /// `arrowDown`, which does not exist).
  final IconData icon;
  final KitJumpEdge edge;

  /// Lets a host give this pill's button a stable key for its own tests
  /// (TEST-5).
  final Key? pillKey;

  /// Kit copy: "Jump to latest", or "{count} new · Jump to latest" (ICU
  /// plural; ARB keys `kitJumpLatest`, `kitJumpNewLatest`). The count is
  /// whatever the host says is new — this never counts anything itself.
  static String latestLabel(BuildContext context, {int newCount = 0}) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    return newCount > 0 ? l10n.kitJumpNewLatest(newCount) : l10n.kitJumpLatest;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.chip(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => _KitJumpPillTransition(
    label: label,
    onPressed: onPressed,
    visible: visible,
    icon: icon,
    edge: edge,
    pillKey: pillKey,
  );
}

/// Lays [pill] over [child] (the scroll area it belongs to): centred on the
/// pill's edge, `space3` inside it, and never closer than `gutter` to the
/// area's sides (KitJumpPill.md "Adaptive"). A bottom pill also clears the
/// published bottom inset (the composer, the pinned primary, the dock)
/// unless [clearBottomInset] is false (a pill inside a panel mid-page, e.g.
/// `KitLogPanel`).
///
/// States: none — a layout layer that places one pill.
class KitJumpPillLayer extends StatelessWidget {
  const KitJumpPillLayer({
    super.key,
    required this.child,
    required this.pill,
    this.clearBottomInset = true,
  });

  final Widget child;
  final KitJumpPill pill;
  final bool clearBottomInset;

  @override
  Widget build(BuildContext context) {
    final bottomEdge = pill.edge == KitJumpEdge.bottom;
    return Stack(
      children: [
        child,
        PositionedDirectional(
          start: 0,
          end: 0,
          top: 0,
          bottom: 0,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final tokens = KitTokens.of(context);
              final maxWidth = (constraints.maxWidth - 2 * tokens.gutter).clamp(
                0.0,
                double.infinity,
              );
              final extraBottom = bottomEdge && clearBottomInset
                  ? KitBottomInset.of(context).bottom
                  : 0.0;
              final margin = tokens.space3 + extraBottom;
              return Padding(
                padding: bottomEdge
                    ? EdgeInsetsDirectional.only(bottom: margin)
                    : EdgeInsetsDirectional.only(top: margin),
                child: Align(
                  alignment: bottomEdge
                      ? AlignmentDirectional.bottomCenter
                      : AlignmentDirectional.topCenter,
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: maxWidth),
                    child: pill,
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}

/// Motion (KitJumpPill.md "Motion and haptics"): in, fades in and rises
/// `space2` over `KitMotion.standard` on `KitMotion.enter`; out, fades and
/// sinks over `KitMotion.quick` on `KitMotion.exit` (a top pill
/// drops/rises toward its own edge — the direction mirrors [edge], never a
/// scale). Under `KitMotion.reduced`, shows and hides at once and settles
/// in one `pump()`. No haptics.
///
/// Hidden (`visible: false`) takes effect the moment [visible] flips: the
/// pill is at once out of hit testing ([IgnorePointer]), semantics
/// ([ExcludeSemantics]) and focus ([ExcludeFocus]); only the fade keeps
/// painting until the exit settles. Settled hidden, a [Visibility] with
/// `maintainState: true` keeps it mounted but offstage.
class _KitJumpPillTransition extends StatefulWidget {
  const _KitJumpPillTransition({
    required this.label,
    required this.onPressed,
    required this.visible,
    required this.icon,
    required this.edge,
    required this.pillKey,
  });

  final String label;
  final VoidCallback onPressed;
  final bool visible;
  final IconData icon;
  final KitJumpEdge edge;
  final Key? pillKey;

  @override
  State<_KitJumpPillTransition> createState() => _KitJumpPillTransitionState();
}

class _KitJumpPillTransitionState extends State<_KitJumpPillTransition>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: KitMotion.standard,
    reverseDuration: KitMotion.quick,
  );
  late final CurvedAnimation _curve = CurvedAnimation(
    parent: _controller,
    curve: KitMotion.enter,
    reverseCurve: KitMotion.exit,
  );
  bool _ranInitial = false;

  @override
  void initState() {
    super.initState();
    // Rebuild when the exit settles (dismissed) or an entrance starts, so
    // the Visibility below goes offstage once the fade has finished — the
    // AnimatedBuilder alone only rebuilds its own subtree.
    _controller.addStatusListener(_onStatus);
  }

  void _onStatus(AnimationStatus status) {
    if (!mounted) return;
    if (status == AnimationStatus.dismissed ||
        status == AnimationStatus.forward) {
      setState(() {});
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_ranInitial) return;
    _ranInitial = true;
    if (!widget.visible) return;
    if (KitMotion.reduced(context)) {
      _controller.value = 1;
    } else {
      _controller.forward(from: 0);
    }
  }

  @override
  void didUpdateWidget(covariant _KitJumpPillTransition old) {
    super.didUpdateWidget(old);
    if (old.visible == widget.visible) return;
    final reduced = KitMotion.reduced(context);
    if (widget.visible) {
      if (reduced) {
        _controller.value = 1;
      } else {
        _controller.forward(from: 0);
      }
    } else {
      if (reduced) {
        _controller.value = 0;
      } else {
        _controller.reverse();
      }
    }
  }

  @override
  void dispose() {
    _controller.removeStatusListener(_onStatus);
    _curve.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    // Kept paintable through the whole exit animation; only fully offstage
    // once it has settled hidden (value 0, dismissed).
    final paintable =
        widget.visible || _controller.status != AnimationStatus.dismissed;
    // Bottom pill rises into place (its edge's "away" direction is down);
    // top pill drops into place (its edge's "away" direction is up) —
    // KitJumpPill.md "a top pill drops/rises toward its own edge".
    final away = widget.edge == KitJumpEdge.bottom
        ? tokens.space2
        : -tokens.space2;
    final hidden = !widget.visible;
    return Visibility(
      visible: paintable,
      maintainState: true,
      maintainAnimation: true,
      // The same three wrappers in both states (only their flags change),
      // so the button's own state survives a toggle.
      child: IgnorePointer(
        ignoring: hidden,
        child: ExcludeSemantics(
          excluding: hidden,
          child: ExcludeFocus(excluding: hidden, child: _animated(away)),
        ),
      ),
    );
  }

  Widget _animated(double away) => AnimatedBuilder(
    animation: _curve,
    builder: (context, child) {
      final t = _curve.value.clamp(0.0, 1.0);
      return Opacity(
        opacity: t,
        child: Transform.translate(
          offset: Offset(0, (1 - t) * away),
          child: child,
        ),
      );
    },
    child: _KitJumpPillButton(
      label: widget.label,
      icon: widget.icon,
      onPressed: widget.onPressed,
      pillKey: widget.pillKey,
    ),
  );
}

/// The pill's look (KitJumpPill.md "Tokens"): a [KitShape.pill] stadium in
/// `surface3`, a `hairline` border, `text1` icon and label, `minTarget`
/// (48) tall minimum. No shadow, no glass, no blur. The press, hover (a fine
/// pointer) and focus ring are [KitTappable]'s: hover and press step the
/// fill to `surface2`, focus draws an `accent` ring inside the stadium.
class _KitJumpPillButton extends StatelessWidget {
  const _KitJumpPillButton({
    required this.label,
    required this.icon,
    required this.onPressed,
    required this.pillKey,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;
  final Key? pillKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;

    // KitJumpPill.md "Tokens": the `button` label is one line, two from
    // 2.0 text (kit_request_card.dart's own large-text test).
    final maxLines = MediaQuery.textScalerOf(context).scale(10) >= 20 ? 2 : 1;

    final content = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(
          horizontal: tokens.space4,
          vertical: tokens.space2,
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            Icon(
              icon,
              size: tokens.iconSize(context, tokens.smallIconSize),
              color: roles.text1,
            ),
            SizedBox(width: tokens.space2),
            Flexible(
              child: KitText(
                label,
                role: KitTextRole.button,
                tone: KitTextTone.primary,
                maxLines: maxLines,
                // Last resort only: the pill takes the area's width less
                // two gutters before a label ever reaches its line limit.
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
      ),
    );

    final style = KitSurfaceLook.styleOf(context);
    final radius = KitSurfaceLook.stadium;
    // The resting fill sits under the tappable (whose hover and press fills
    // are surface2 over it); the hairline is painted over both.
    return Semantics(
      button: true,
      label: label,
      container: true,
      excludeSemantics: true,
      onTap: onPressed,
      child: kitSurfaceBox(
        style: style,
        decoration: KitSurfaceLook.decorate(
          context,
          solid: ShapeDecoration(
            color: roles.surface3,
            shape: tokens.shapeOf(KitShape.pill),
          ),
          level: KitSurfaceLevel.surface3,
          role: KitSurfaceRole.floating,
          borderRadius: radius,
        ),
        child: DecoratedBox(
          position: DecorationPosition.foreground,
          // The hairline is Solid's edge; the other styles draw their own.
          decoration: ShapeDecoration(
            shape: tokens.pillBorder(
              side: KitSurfaceLook.isPlain(context)
                  ? BorderSide(
                      color: roles.hairline,
                      width: KitTokens.hairlineWidth(context),
                    )
                  : BorderSide.none,
            ),
          ),
          child: KitTappable(
            tappableKey: pillKey,
            onTap: onPressed,
            shape: KitShape.pill,
            surface: KitSurfaceLevel.surface3,
            child: content,
          ),
        ),
      ),
    );
  }
}

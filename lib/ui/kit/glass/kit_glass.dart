import 'package:flutter/material.dart';

import '../kit_frost.dart';
import '../kit_surface_look.dart';
import '../kit_tokens.dart';

// The chat composer's layer (kit/chat, a part of kit_composer.dart) builds
// the frosted pane and the header's measure from these.
export '../kit_frost.dart'
    show
        GlassChoiceStore,
        KitFrost,
        KitFrostRole,
        KitFrostStrength,
        KitGlassChoice;
export '../kit_frost_header.dart'
    show KitFrostHeader, KitFrostHeaderHost, KitFrostTopClaim;

/// A plain solid surface for a bounded control or a short row of controls
/// that floats over scrolling content: the bottom dock, the side rail, the
/// shell's top controls and the chat composer (design standard §10).
///
/// Opaque `surface2`, the theme's hairline border, rounded corners and the
/// one tight elevation shadow ([KitTokens.surfaceShadows]). No blur, no
/// translucency, no rim highlight. (The name is historical: it used to be
/// the liquid glass part.) Under another Style (Settings › Appearance) it
/// is that style's floating piece ([KitSurfaceLook], role floating): Soft
/// raises it, Brutal and Paper ink it.
///
/// The one exception is the chat composer: inside its frosted pane
/// ([KitFrost.wellOf], from `KitComposer.layer`) the surface becomes the
/// pane's denser inner well ([KitFrost.wellAlpha]) while content is
/// scrolled under it (owner, 2026-10-30; [KitFrost]).
///
/// States: none — a surface around its child, it holds no data.
class KitGlass extends StatelessWidget {
  const KitGlass({
    super.key,
    required this.child,
    this.borderRadius,
    this.shadow = true,
  }) : trailing = null,
       joined = false;

  /// Two solid pieces in one row: [leading] at the start at its own width,
  /// [trailing] at the end. When [joined] the trailing piece sits one small
  /// gap from the leading one instead (the shell's server pill and search
  /// while the page is scrolled, [scrolledOf]).
  const KitGlass.pair({
    super.key,
    required Widget leading,
    required Widget this.trailing,
    this.joined = false,
    this.borderRadius,
    this.shadow = true,
  }) : child = leading;

  /// The content; for [KitGlass.pair], the leading piece.
  final Widget child;

  /// [KitGlass.pair] only: the piece at the end.
  final Widget? trailing;

  /// [KitGlass.pair] only: the trailing piece sits next to the leading one.
  final bool joined;

  /// The surface's corners. Null takes the floating tab bar's corners
  /// ([KitTokens.navRadius], 22).
  final BorderRadius? borderRadius;

  /// The one tight elevation shadow ([KitTokens.surfaceShadows]).
  final bool shadow;

  /// Watches the vertical scrolling under [child] and tells the pair above
  /// it whether the page is scrolled ([scrolledOf]): past one
  /// [KitTokens.minTarget] from the top. A new [resetOn] (the selected
  /// destination) starts again at the top. The shell (KitNav) puts it
  /// around the destination's page.
  static Widget trackScroll({required Widget child, Object? resetOn}) =>
      _KitGlassScrollTracker(resetOn: resetOn, child: child);

  /// Whether the page under the nearest [trackScroll] is scrolled; false
  /// with none. The caller rebuilds when it changes.
  static bool scrolledOf(BuildContext context) =>
      context
          .dependOnInheritedWidgetOfExactType<_KitGlassScrollScope>()
          ?.notifier
          ?.value ??
      false;

  Widget _piece(BuildContext context, Widget content, KitTokens tokens) {
    final well = trailing == null ? KitFrost.wellOf(context) : null;
    if (well != null) {
      // Inside the composer's frosted pane: the field's well, a little
      // denser than the pane, its surface thinning as the glass fades in.
      return AnimatedBuilder(
        animation: well,
        child: content,
        builder: (context, content) => _surface(
          context,
          content!,
          tokens,
          alpha: 1 - (1 - KitFrost.wellAlpha) * well.value,
        ),
      );
    }
    return _surface(context, content, tokens);
  }

  Widget _surface(
    BuildContext context,
    Widget content,
    KitTokens tokens, {
    double alpha = 1,
  }) {
    final radius = borderRadius ?? BorderRadius.circular(tokens.navRadius);
    final solid = BoxDecoration(
      color: alpha >= 1
          ? tokens.roles.surface2
          : tokens.roles.surface2.withValues(alpha: alpha),
      borderRadius: radius,
      border: Border.all(
        color: tokens.roles.hairline,
        width: KitTokens.hairlineWidth(context),
      ),
      // A shadow under a see-through well would darken it from inside:
      // it fades out as the glass fades in.
      boxShadow: !shadow
          ? null
          : alpha >= 1
          ? tokens.floatingShadows(context)
          : [
              for (final s in tokens.floatingShadows(context))
                s.copyWith(
                  color: s.color.withValues(
                    alpha:
                        s.color.a *
                        (alpha - KitFrost.wellAlpha) /
                        (1 - KitFrost.wellAlpha),
                  ),
                ),
            ],
    );
    final style = KitSurfaceLook.styleOf(context);
    // Under the Style choice (Settings › Appearance): that style's
    // floating piece.
    return kitSurfaceBox(
      style: style,
      decoration: KitSurfaceLook.decorate(
        context,
        solid: solid,
        level: KitSurfaceLevel.surface2,
        role: KitSurfaceRole.floating,
        borderRadius: radius,
      ),
      // The words on the pane on a layer of their own (scroll-perf,
      // 2026-10-23): the page around a floating piece is recorded again on
      // every frame of a scroll's stretch and while its scrollbar fades.
      child: ClipRRect(
        borderRadius: radius,
        child: RepaintBoundary(child: content),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final trailing = this.trailing;
    if (trailing == null) return _piece(context, child, tokens);
    final gap = SizedBox(width: tokens.space2);
    final lead = _piece(context, child, tokens);
    final trail = _piece(context, trailing, tokens);
    // The leading piece takes what the trailing one leaves, never more.
    if (joined) {
      return Row(
        children: [
          Expanded(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(child: lead),
                  gap,
                  trail,
                ],
              ),
            ),
          ),
        ],
      );
    }
    return Row(
      children: [
        Expanded(
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: lead,
          ),
        ),
        gap,
        trail,
      ],
    );
  }
}

// ── Scroll ──────────────────────────────────────────────────────────────

class _KitGlassScrollTracker extends StatefulWidget {
  const _KitGlassScrollTracker({required this.child, this.resetOn});

  final Widget child;
  final Object? resetOn;

  @override
  State<_KitGlassScrollTracker> createState() => _KitGlassScrollTrackerState();
}

class _KitGlassScrollTrackerState extends State<_KitGlassScrollTracker> {
  final ValueNotifier<bool> _scrolled = ValueNotifier(false);

  @override
  void didUpdateWidget(_KitGlassScrollTracker old) {
    super.didUpdateWidget(old);
    if (old.resetOn != widget.resetOn) _scrolled.value = false;
  }

  @override
  void dispose() {
    _scrolled.dispose();
    super.dispose();
  }

  void _read(ScrollMetrics metrics) {
    if (metrics.axis != Axis.vertical) return;
    final threshold = KitTokens.of(context).minTarget;
    _scrolled.value = metrics.pixels - metrics.minScrollExtent > threshold;
  }

  @override
  Widget build(BuildContext context) {
    return NotificationListener<ScrollMetricsNotification>(
      onNotification: (notification) {
        _read(notification.metrics);
        return false;
      },
      child: NotificationListener<ScrollUpdateNotification>(
        onNotification: (notification) {
          _read(notification.metrics);
          return false;
        },
        child: _KitGlassScrollScope(notifier: _scrolled, child: widget.child),
      ),
    );
  }
}

class _KitGlassScrollScope extends InheritedNotifier<ValueNotifier<bool>> {
  const _KitGlassScrollScope({required super.notifier, required super.child});
}

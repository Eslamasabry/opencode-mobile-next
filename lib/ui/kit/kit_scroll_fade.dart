import 'package:flutter/material.dart';

import 'kit_tokens.dart';

/// A sideways-scrolling line whose edges fade where there is more past them
/// ([KitFilterChips]' fade, for a line that is not the screen's filter
/// chips: the composer's status chips). A chip running past an edge melts
/// away over [width] instead of ending in a hard cut mid-word ("Auto Ac"):
/// the fade says the line scrolls. An edge with nothing beyond it stays
/// sharp; a line that fits fades nowhere. The fade follows the scroll.
///
/// [child] is the [SingleChildScrollView] (or another horizontal
/// scrollable); its scroll notifications drive the fade.
///
/// States: none - a mask over a line.
class KitScrollFade extends StatefulWidget {
  const KitScrollFade({super.key, required this.child, this.width});

  final Widget child;

  /// How wide an edge's fade is; null is [defaultWidth].
  final double? width;

  /// space6 and space4 (40): enough for a word to fade out rather than
  /// read as cut; the outer third is fully clear, so no letter stands
  /// half-visible at the very edge.
  static double defaultWidth(KitTokens tokens) => tokens.space6 + tokens.space4;

  /// Which edges of the line built at [element] fade right now, in reading
  /// order.
  @visibleForTesting
  static ({bool start, bool end}) fadesOf(Element element) {
    final state = (element as StatefulElement).state as _KitScrollFadeState;
    return (start: state._moreBefore, end: state._moreAfter);
  }

  @override
  State<KitScrollFade> createState() => _KitScrollFadeState();
}

class _KitScrollFadeState extends State<KitScrollFade> {
  bool _moreBefore = false;
  bool _moreAfter = false;

  bool _onMetrics(ScrollMetrics metrics) {
    if (metrics.axis != Axis.horizontal) return false;
    final before = metrics.extentBefore > 0.5;
    final after = metrics.extentAfter > 0.5;
    if (before != _moreBefore || after != _moreAfter) {
      // Metrics arrive during layout: the new fade waits for the frame.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        setState(() {
          _moreBefore = before;
          _moreAfter = after;
        });
      });
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final fadeLeft = rtl ? _moreAfter : _moreBefore;
    final fadeRight = rtl ? _moreBefore : _moreAfter;
    final fade = widget.width ?? KitScrollFade.defaultWidth(tokens);
    // Always masked, so the scrollable keeps its place in the tree (and its
    // offset) when an edge's fade comes or goes.
    return ShaderMask(
      blendMode: BlendMode.dstIn,
      shaderCallback: (bounds) {
        final edge = bounds.width <= 0
            ? 0.0
            : (fade / bounds.width).clamp(0.0, 0.5);
        // A mask: only the alpha counts (dstIn), so any opaque role works.
        final solid = tokens.roles.ground.withValues(alpha: 1);
        final clear = solid.withValues(alpha: 0);
        final left = fadeLeft ? clear : solid;
        final right = fadeRight ? clear : solid;
        return LinearGradient(
          colors: [left, left, solid, solid, right, right],
          stops: [0, edge / 3, edge, 1 - edge, 1 - edge / 3, 1],
        ).createShader(bounds);
      },
      child: NotificationListener<ScrollMetricsNotification>(
        onNotification: (n) => _onMetrics(n.metrics),
        child: NotificationListener<ScrollUpdateNotification>(
          onNotification: (n) => _onMetrics(n.metrics),
          child: widget.child,
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';

import 'kit_frost.dart';
import 'kit_max_width.dart';
import 'kit_tokens.dart';

/// The filter chips under a screen's title (Chats home: "All projects",
/// "Needs you", "Running"). The chips are [KitChip]s the caller builds; this
/// part only lays them on one line, on the screen gutter. The line scrolls
/// sideways when the chips do not fit (large text, a long name, Arabic): it
/// never wraps onto a second line, and no chip is cut or hidden.
///
/// An edge the line runs past fades over [fadeWidth] (the gutter and a
/// little more), so a chip that goes on past it melts away instead of ending
/// in a hard cut mid-word ("Runnin"): the fade says there is more. An edge
/// with nothing beyond it stays sharp, and a line that fits fades nowhere.
/// The fade follows the scroll: scrolled to the end, the far edge clears and
/// the near one fades.
///
/// States: none - a layout line; each chip carries its own selected look.
class KitFilterChips extends StatefulWidget {
  const KitFilterChips({super.key, required this.chips});

  final List<Widget> chips;

  /// How wide an edge's fade is: the gutter plus 32 dp, enough to show a
  /// word fading rather than cut.
  static double fadeWidth(KitTokens tokens) => tokens.gutter + 32;

  /// Which edges of the line built at [element] fade right now, in reading
  /// order.
  @visibleForTesting
  static ({bool start, bool end}) fadesOf(Element element) {
    final state = (element as StatefulElement).state as _KitFilterChipsState;
    return (start: state._moreBefore, end: state._moreAfter);
  }

  @override
  State<KitFilterChips> createState() => _KitFilterChipsState();
}

class _KitFilterChipsState extends State<KitFilterChips> {
  /// Whether chips lie past the line's start or end edge (in reading order).
  bool _moreBefore = false;
  bool _moreAfter = false;

  bool _onMetrics(ScrollMetrics metrics) {
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
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final chips = widget.chips;
    // Over the chat composer the line ends at the frosted pane's inner edge
    // (KitFrost.lineInsetOf): the chips scroll and fade inside the pane,
    // never across its edge. Their start stays on the gutter.
    final inset = KitFrost.lineInsetOf(context).clamp(0.0, tokens.gutter);
    final line = NotificationListener<ScrollMetricsNotification>(
      onNotification: (n) => _onMetrics(n.metrics),
      child: NotificationListener<ScrollUpdateNotification>(
        onNotification: (n) => _onMetrics(n.metrics),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: EdgeInsets.symmetric(horizontal: tokens.gutter - inset),
          child: Row(
            children: [
              for (var i = 0; i < chips.length; i++) ...[
                if (i > 0) SizedBox(width: tokens.space2),
                chips[i],
              ],
            ],
          ),
        ),
      ),
    );
    // Always masked, so the scroll view keeps its place in the tree (and its
    // offset) when an edge's fade comes or goes.
    // The scroll's start is the reading start: the left in a left-to-right
    // language, the right in Arabic.
    final rtl = Directionality.of(context) == TextDirection.rtl;
    final fadeLeft = rtl ? _moreAfter : _moreBefore;
    final fadeRight = rtl ? _moreBefore : _moreAfter;
    final fade = KitFilterChips.fadeWidth(tokens);
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: inset),
      child: ShaderMask(
        blendMode: BlendMode.dstIn,
        shaderCallback: (bounds) {
          final edge = bounds.width <= 0
              ? 0.0
              : (fade / bounds.width).clamp(0.0, 0.5);
          // A mask: only the alpha counts (dstIn), so any opaque role works.
          final solid = tokens.roles.ground.withValues(alpha: 1);
          final clear = solid.withValues(alpha: 0);
          return LinearGradient(
            colors: [
              fadeLeft ? clear : solid,
              solid,
              solid,
              fadeRight ? clear : solid,
            ],
            stops: [0, edge, 1 - edge, 1],
          ).createShader(bounds);
        },
        child: line,
      ),
    );
  }
}

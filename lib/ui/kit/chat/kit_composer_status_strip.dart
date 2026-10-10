// KitComposerStatusStrip: the line above the composer's field (docs/ux-system/
// kit-api/KitComposer.md). Split from kit_composer_chips.dart to keep that
// file under the size limit; it is exported from there.
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../kit_tokens.dart';
import 'kit_composer_chips.dart';

/// The line above the composer's field: standing facts about a
/// conversation's run as chips (automatic approvals, context waiting for the
/// agent's next step, "Background"), and the model chip at the end (owner
/// decision 2 Oct, 14A: the field gets its full width). One line while it
/// fits; at a narrow width or a large text size the line scrolls sideways
/// inside itself, never wrapping and never overlapping. Draws
/// nothing when there are neither [chips] nor a [model].
///
/// States: empty (nothing drawn).
class KitComposerStatusStrip extends StatelessWidget {
  const KitComposerStatusStrip({
    super.key,
    required this.chips,
    this.model,
    this.stripKey,
  });

  /// Kit chips ([KitChip] and its forms), in the host's order.
  final List<Widget> chips;

  /// [KitComposerChips.model], at the end of the line.
  final Widget? model;

  /// On the scrolling line, for tests (TEST-5).
  final Key? stripKey;

  @override
  Widget build(BuildContext context) {
    final model = this.model;
    if (chips.isEmpty && model == null) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final pad = EdgeInsetsDirectional.only(
      start: tokens.space3,
      end: tokens.space3,
    );
    // One line, always: when the chips do not fit (large text, narrow
    // screen) the line scrolls sideways inside itself instead of wrapping.
    Widget line(
      List<Widget> kids, {
      Widget? end,
      required Key? key,
    }) => LayoutBuilder(
      builder: (context, box) {
        // No chip is wider than the line; a longer label ends in an
        // ellipsis inside its own chip.
        Widget held(Widget chip) => ConstrainedBox(
          constraints: BoxConstraints(
            maxWidth: math.max(0, box.maxWidth - tokens.space3 * 2),
          ),
          child: chip,
        );
        final start = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < kids.length; i++) ...[
              if (i > 0) SizedBox(width: tokens.space2),
              held(kids[i]),
            ],
          ],
        );
        return SingleChildScrollView(
          key: key,
          scrollDirection: Axis.horizontal,
          // The padding scrolls with the chips, so a chip cut by the edge
          // is cut at the screen's edge, not inside the margin.
          padding: pad,
          child: ConstrainedBox(
            constraints: BoxConstraints(
              minWidth: math.max(0, box.maxWidth - tokens.space3 * 2),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                start,
                if (end != null) ...[SizedBox(width: tokens.space2), held(end)],
              ],
            ),
          ),
        );
      },
    );
    if (model == null) {
      return line(chips, key: stripKey);
    }
    if (chips.isEmpty) {
      return Padding(
        key: stripKey,
        padding: pad,
        child: Align(alignment: AlignmentDirectional.centerEnd, child: model),
      );
    }
    // The chips first, the model chip at the end of the same line.
    return line(chips, end: model, key: stripKey);
  }
}

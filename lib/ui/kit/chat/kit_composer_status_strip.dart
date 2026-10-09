// KitComposerStatusStrip: the line above the composer's field (docs/ux-system/
// kit-api/KitComposer.md). Split from kit_composer_chips.dart to keep that
// file under the size limit; it is exported from there.
import 'package:flutter/material.dart';

import '../kit_tokens.dart';
import 'kit_composer_chips.dart';

/// The line above the composer's field: standing facts about a
/// conversation's run as chips (automatic approvals, context waiting for the
/// agent's next step, "Background"), and the model chip at the end (owner
/// decision 2 Oct, 14A: the field gets its full width). One line while it
/// fits; at a narrow width or a large text size the chips wrap onto more
/// lines, each held to the line's width, and never overlap. Draws
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
    Widget chipRow() => Wrap(
      key: model == null ? stripKey : null,
      spacing: tokens.space2,
      runSpacing: tokens.space2,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: chips,
    );
    if (model == null) {
      return Align(
        alignment: AlignmentDirectional.centerStart,
        child: Padding(padding: pad, child: chipRow()),
      );
    }
    // Never two chips on one another: the chips and the model chip share the
    // line while both fit (the model at the end); when they do not, the
    // model chip drops to a line of its own. Each chip is held to the
    // line's width, so a long label ends in an ellipsis instead of running
    // under its neighbour.
    return Padding(
      padding: pad,
      child: Wrap(
        key: stripKey,
        // Alone, the model chip stays at the end of the line (14A).
        alignment: chips.isEmpty
            ? WrapAlignment.end
            : WrapAlignment.spaceBetween,
        spacing: tokens.space2,
        runSpacing: tokens.space2,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [if (chips.isNotEmpty) chipRow(), model],
      ),
    );
  }
}

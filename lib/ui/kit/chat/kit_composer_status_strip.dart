// KitComposerStatusStrip: the line above the composer's field (docs/ux-system/
// kit-api/KitComposer.md). Split from kit_composer_chips.dart to keep that
// file under the size limit; it is exported from there.
import 'package:flutter/material.dart';

import '../kit_tokens.dart';
import 'kit_composer_chips.dart';

/// The line above the composer's field: standing facts about a
/// conversation's run as chips (automatic approvals, context waiting for the
/// agent's next step, "Background"), and the model chip at the end (owner
/// decision 2 Oct, 14A: the field gets its full width). Always one line: short
/// labels keep every chip on a phone's width, and at large text sizes the
/// chips scroll sideways instead of stacking over the transcript. Draws
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
    final scroller = SingleChildScrollView(
      key: stripKey,
      scrollDirection: Axis.horizontal,
      padding: EdgeInsetsDirectional.only(
        start: tokens.space3,
        end: model == null ? tokens.space3 : tokens.space2,
      ),
      child: Row(spacing: tokens.space2, children: chips),
    );
    if (model == null) {
      return Align(
        alignment: AlignmentDirectional.centerStart,
        child: scroller,
      );
    }
    // The model chip shrinks first (it ellipsizes), never past 60 % of the
    // line; the chips take the rest and scroll.
    return LayoutBuilder(
      builder: (context, constraints) => Row(
        children: [
          if (chips.isEmpty)
            const Spacer()
          else
            Expanded(
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: scroller,
              ),
            ),
          ConstrainedBox(
            constraints: BoxConstraints(maxWidth: constraints.maxWidth * 0.6),
            child: Padding(
              padding: EdgeInsetsDirectional.only(end: tokens.space3),
              child: model,
            ),
          ),
        ],
      ),
    );
  }
}

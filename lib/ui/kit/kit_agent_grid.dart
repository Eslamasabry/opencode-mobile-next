import 'package:flutter/material.dart';

import 'kit_agent_grid_span.dart';
import 'kit_max_width.dart';
import 'kit_tokens.dart';

/// [children] (agent cards) in two columns of equal width, each pair as tall
/// as its taller card, with the kit's gap between them. An odd last card
/// spans both columns and lays out sideways (mark at the start, words
/// beside it), so no hole is left. At large text it is one column of
/// full-width cards. It scrolls with the page that holds it and follows the
/// reading direction, so the first card is at the start edge.
///
/// States: none — a layout of its children.
class KitAgentGrid extends StatelessWidget {
  const KitAgentGrid({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    // At large text two columns are too narrow for a name: one column of
    // full-width cards, each still mark above words, so the words keep the
    // whole width.
    if (MediaQuery.textScalerOf(context).scale(1) > 1.3) {
      return Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) SizedBox(height: tokens.space3),
            children[i],
          ],
        ],
      );
    }
    final odd = children.length.isOdd;
    final pairs = odd ? children.length - 1 : children.length;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 0; i < pairs; i += 2) ...[
          if (i > 0) SizedBox(height: tokens.space3),
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(child: children[i]),
                SizedBox(width: tokens.space3),
                Expanded(child: children[i + 1]),
              ],
            ),
          ),
        ],
        if (odd) ...[
          if (pairs > 0) SizedBox(height: tokens.space3),
          KitAgentGridSpan(child: children.last),
        ],
      ],
    );
  }
}

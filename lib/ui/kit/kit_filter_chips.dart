import 'package:flutter/material.dart';

import 'kit_tokens.dart';

/// One line of filter chips under a screen's title (Chats home: "All
/// projects", "Needs you", "Running"). The chips are [KitChip]s the caller
/// builds; this part only lays them on a single line that scrolls sideways
/// when they do not fit, on the screen gutter, so nothing wraps into a
/// second line and shifts the list below.
///
/// States: none — a layout line; each chip carries its own selected look.
class KitFilterChips extends StatelessWidget {
  const KitFilterChips({super.key, required this.chips});

  final List<Widget> chips;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Row(spacing: tokens.space2, children: chips),
    );
  }
}

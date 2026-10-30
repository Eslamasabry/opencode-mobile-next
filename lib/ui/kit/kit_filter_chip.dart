import 'package:flutter/material.dart';

import 'kit_chip.dart';
import 'kit_max_width.dart';

/// One on/off filter chip of a [KitFilterChips]. [needsYou] draws it in the
/// needs-you (amber) tone, so only the kit ever reads that colour; every
/// other filter is neutral.
///
/// States: none — on and off are the chip's own selected look, and a filter that cannot apply is not shown.
class KitFilterChip extends StatelessWidget {
  const KitFilterChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onPressed,
    this.needsYou = false,
  }) : opens = false;

  /// A chip in the filter row that opens a page instead of filtering (the
  /// Conversations list's "Needs you · 3" opens the Inbox): an action chip,
  /// never selected, with button semantics rather than a toggle.
  const KitFilterChip.opens({
    super.key,
    required this.label,
    required this.onPressed,
    this.needsYou = false,
  }) : selected = false,
       opens = true;

  final String label;
  final bool selected;

  /// [KitFilterChip.opens]: not an on/off filter.
  final bool opens;
  final VoidCallback onPressed;
  final bool needsYou;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.chip(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => KitChip.action(
    label: label,
    selected: opens ? null : selected,
    tone: needsYou ? KitChipTone.attention : KitChipTone.neutral,
    onPressed: onPressed,
  );
}

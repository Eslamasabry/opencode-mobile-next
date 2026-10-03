import 'package:flutter/material.dart';

import 'kit_chip.dart';

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
  });

  final String label;
  final bool selected;
  final VoidCallback onPressed;
  final bool needsYou;

  @override
  Widget build(BuildContext context) => KitChip.action(
    label: label,
    selected: selected,
    tone: needsYou ? KitChipTone.attention : KitChipTone.neutral,
    onPressed: onPressed,
  );
}

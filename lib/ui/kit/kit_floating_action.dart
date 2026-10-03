import 'package:flutter/material.dart';

import 'kit_bottom_inset.dart';
import 'kit_buttons.dart';
import 'kit_tokens.dart';

/// A screen's one floating primary ("New chat"): a primary [KitButton] with
/// its words, laid over the bottom end of [child] (the list it belongs to).
/// It sits on the gutter and clears whatever is published below
/// ([KitBottomInset]: the dock, the keyboard, a pinned block), so it never
/// covers the last row's actions. The list under it should end with
/// `KitScreen.endPadding` plus the button's height so its last row can
/// scroll clear.
///
/// States: none — one button; its press, hover and focus looks are the
/// button's own, and an action that cannot run is not floated.
class KitFloatingAction extends StatelessWidget {
  const KitFloatingAction({
    super.key,
    required this.child,
    required this.label,
    required this.onPressed,
    this.icon,
    this.buttonKey,
  });

  final Widget child;

  /// The action's words, naming what it starts.
  final String label;
  final IconData? icon;
  final VoidCallback onPressed;
  final Key? buttonKey;

  /// Room a scrolling list leaves under its last row so the button never
  /// hides it.
  static const clearance = 72.0;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Stack(
      children: [
        child,
        PositionedDirectional(
          end: tokens.gutter,
          bottom: tokens.space3 + KitBottomInset.of(context).bottom,
          child: KitButton.primary(
            key: buttonKey,
            label: label,
            icon: icon,
            expand: false,
            onPressed: onPressed,
          ),
        ),
      ],
    );
  }
}

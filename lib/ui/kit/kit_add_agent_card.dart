import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_surface.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// An action within the agent grid, distinct from its selectable agents:
/// a plus and a verb on an outlined, ground-level card. No brand, selected
/// badge or mutually-exclusive semantics. The whole card opens setup.
///
/// States: none. The target is at least 96 dp high and follows the grid's
/// row height; the words wrap for large text and either reading direction.
class KitAddAgentCard extends StatelessWidget {
  const KitAddAgentCard({
    super.key,
    required this.label,
    required this.onPressed,
    this.tappableKey,
  });

  final String label;
  final VoidCallback onPressed;
  final Key? tappableKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return KitMaxWidth.reading(
      child: KitTappable(
        tappableKey: tappableKey,
        label: label,
        onTap: onPressed,
        shape: KitShape.card,
        child: KitSurface(
          shape: KitShape.card,
          level: KitSurfaceLevel.ground,
          outlined: true,
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: tokens.minTarget * 2),
            child: Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const KitIcon(AppIconography.add, size: KitIconSize.large),
                  SizedBox(height: tokens.space3),
                  KitText(
                    label,
                    role: KitTextRole.headline,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

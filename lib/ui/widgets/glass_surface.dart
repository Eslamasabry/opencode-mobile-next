import 'package:flutter/material.dart';

/// A restrained glass-like material for navigation chrome.
///
/// This surface lives outside scrolling content, so a backdrop blur would only
/// blur a solid scaffold. Tint, a light-catching edge and shallow depth provide
/// the material without an expensive full-screen backdrop pass. Keep reading
/// surfaces opaque. Accessibility settings remove transparency and shadows.
class GlassSurface extends StatelessWidget {
  const GlassSurface({super.key, required this.child});

  final Widget child;

  static bool reduceEffects(BuildContext context) {
    final media = MediaQuery.of(context);
    return media.highContrast ||
        media.accessibleNavigation ||
        media.disableAnimations;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final opaque = reduceEffects(context);
    final dark = theme.brightness == Brightness.dark;
    const radius = BorderRadius.all(Radius.circular(24));
    final tint = Color.alphaBlend(
      scheme.primary.withValues(alpha: dark ? .035 : .018),
      scheme.surfaceContainerLow,
    );

    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: radius,
        boxShadow: opaque
            ? const []
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: dark ? .18 : .05),
                  blurRadius: 16,
                  offset: const Offset(0, 4),
                ),
              ],
      ),
      child: ClipRRect(
        borderRadius: radius,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: opaque
                ? scheme.surfaceContainerHigh
                : tint.withValues(alpha: .96),
            borderRadius: radius,
            border: Border.all(
              color: opaque
                  ? scheme.outline
                  : scheme.onSurface.withValues(alpha: dark ? .14 : .10),
            ),
          ),
          child: child,
        ),
      ),
    );
  }
}

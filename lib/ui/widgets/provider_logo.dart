import 'package:flutter/material.dart';

import '../../api/provider_presentation.dart';
import '../app_theme.dart';

/// A provider's logo in a rounded square tile.
///
/// The mark is the provider's website favicon, fetched at first use from
/// Google's favicon service (see [providerLogoUrl]) and cached by the image
/// pipeline like any other network image. While it loads, and whenever it
/// cannot load, the tile shows a two-letter monogram instead, so a row never
/// has an empty leading slot. OpenCode's own providers draw the app's prompt
/// glyph rather than a fetched image.
///
/// Decorative: the row that shows the logo already names the provider, so the
/// tile is excluded from the semantics tree.
class ProviderLogo extends StatelessWidget {
  const ProviderLogo(this.providerID, {super.key, this.size = 28});

  final String providerID;
  final double size;

  /// Test seam. When set, every logo asks this for its [ImageProvider]
  /// instead of building a [NetworkImage]; returning null skips the image
  /// entirely and renders the monogram, so widget tests never touch the
  /// network and render deterministically.
  static ImageProvider? Function(String url)? imageProviderOverride;

  /// Decode width for the fetched favicon: the service serves 128px PNGs
  /// and the tile never draws larger than that.
  static const int cacheWidth = 128;

  static const Duration fadeIn = Duration(milliseconds: 150);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (isOpenCodeProvider(providerID)) {
      return BrandTile(
        size: size,
        color: theme.colorScheme.primary.withValues(alpha: .12),
        child: Text(
          '❯',
          key: const ValueKey('provider-logo-prompt-glyph'),
          maxLines: 1,
          textAlign: TextAlign.center,
          style: TextStyle(
            fontFamily: AppTheme.monoFamily,
            fontSize: size * .5,
            fontWeight: FontWeight.w700,
            height: 1,
            color: theme.colorScheme.primary,
          ),
        ),
      );
    }
    final url = providerLogoUrl(providerID).toString();
    final override = imageProviderOverride;
    final image = override == null ? NetworkImage(url) : override(url);
    final monogram = ProviderMonogram(providerID, size: size);
    if (image == null) return BrandTile(size: size, child: monogram);
    final inset = (size * .14).roundToDouble();
    return BrandTile(
      size: size,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(BrandTile.radiusFor(size) * .55),
        child: Image(
          image: ResizeImage.resizeIfNeeded(cacheWidth, null, image),
          width: size - inset * 2,
          height: size - inset * 2,
          fit: BoxFit.contain,
          filterQuality: FilterQuality.medium,
          gaplessPlayback: true,
          excludeFromSemantics: true,
          errorBuilder: (context, error, stackTrace) => monogram,
          frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
            if (wasSynchronouslyLoaded) return child;
            if (frame == null) return monogram;
            return TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: 1),
              duration: fadeIn,
              curve: Curves.easeOut,
              child: child,
              builder: (context, opacity, child) =>
                  Opacity(opacity: opacity, child: child),
            );
          },
        ),
      ),
    );
  }
}

/// The two-letter stand-in drawn while a logo loads or when it cannot.
class ProviderMonogram extends StatelessWidget {
  const ProviderMonogram(this.providerID, {super.key, required this.size});

  final String providerID;
  final double size;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Text(
      providerMonogram(providerID),
      maxLines: 1,
      textAlign: TextAlign.center,
      style: TextStyle(
        fontSize: size * .38,
        fontWeight: FontWeight.w700,
        letterSpacing: -.2,
        height: 1,
        color: theme.colorScheme.onSurfaceVariant,
      ),
    );
  }
}

/// The rounded square behind a [ProviderLogo]: a neutral
/// `surfaceContainerHigh` fill with a hairline border. Sibling rows (MCP
/// servers) reuse it so their leading slot lines up with the provider tiles.
class BrandTile extends StatelessWidget {
  const BrandTile({
    super.key,
    required this.size,
    required this.child,
    this.color,
  });

  final double size;
  final Widget child;

  /// Overrides the neutral fill (the OpenCode glyph sits on a primary tint).
  final Color? color;

  /// The spec radius is for the 28px tile; smaller tiles scale it down so
  /// an 18px logo does not turn into a circle.
  static double radiusFor(double size) =>
      AppTheme.radiusControl * .6 * (size / 28).clamp(.6, 1.0);

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ExcludeSemantics(
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: color ?? theme.colorScheme.surfaceContainerHigh,
          borderRadius: BorderRadius.circular(radiusFor(size)),
          border: Border.all(color: AppTheme.hairline(theme), width: .8),
        ),
        child: child,
      ),
    );
  }
}

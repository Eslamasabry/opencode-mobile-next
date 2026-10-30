import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_motion.dart';
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'kit_surface_look.dart';
import 'kit_tokens.dart';

/// A small panel that floats over whatever is behind a transparent window
/// (the home screen, for a widget's steer box and style picker): a dimmed
/// ground that closes it when tapped, and a card that rises into place above
/// the keyboard.
///
/// Entering takes [KitMotion.standard] (a short rise and fade); under reduced
/// motion it is simply there. [onDismiss] null keeps it open (a message is
/// being sent), so the ground ignores taps then.
///
/// States: none — it only frames its child.
class KitFloatingPanel extends StatelessWidget {
  const KitFloatingPanel({
    super.key,
    required this.child,
    required this.onDismiss,
  });

  final Widget child;

  /// Closes the panel; null while closing is not allowed.
  final VoidCallback? onDismiss;

  static const double _maxWidth = 440;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final duration = reduced ? Duration.zero : KitMotion.standard;
    final solid = KitSurfaceLook.isPlain(context);
    final keyboard = MediaQuery.viewInsetsOf(context).bottom;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: reduced ? 1 : 0, end: 1),
      duration: duration,
      curve: KitMotion.enter,
      builder: (context, t, body) => Stack(
        fit: StackFit.expand,
        children: [
          Semantics(
            button: onDismiss != null,
            label: onDismiss == null ? null : l10n.widgetV2Cancel,
            child: GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: onDismiss,
              child: ColoredBox(
                color: roles.scrim.withValues(alpha: roles.scrim.a * t),
              ),
            ),
          ),
          AnimatedPadding(
            duration: duration,
            curve: KitMotion.enter,
            padding: EdgeInsets.only(bottom: keyboard),
            child: SafeArea(
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: EdgeInsets.all(tokens.space4),
                  child: Opacity(
                    opacity: t,
                    child: Transform.translate(
                      offset: Offset(0, (1 - t) * 24),
                      child: body,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: _maxWidth),
        child: solid
            ? Material(
                color: roles.surface2,
                elevation: tokens.sheetElevation,
                shadowColor: roles.elevationShadow,
                borderRadius: BorderRadius.circular(tokens.sheetRadius),
                clipBehavior: Clip.antiAlias,
                child: Padding(
                  padding: EdgeInsets.all(tokens.space4),
                  child: child,
                ),
              )
            // Any style but Solid at its defaults (Settings › Appearance).
            : kitSurfaceMaterial(
                context,
                level: KitSurfaceLevel.surface2,
                fill: roles.surface2,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(tokens.sheetRadius),
                ),
                role: KitSurfaceRole.floating,
                child: Padding(
                  padding: EdgeInsets.all(tokens.space4),
                  child: child,
                ),
              ),
      ),
    );
  }
}

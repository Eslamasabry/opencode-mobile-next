import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_bottom_inset.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'style/kit_signal.dart';
import 'style/kit_style.dart' show kitStyleSpec;

/// A screen's one floating primary ("New conversation"): a round [size] dp
/// button in the accent with one glyph (a plus unless [icon] says otherwise),
/// laid over the bottom end of [child] (the list it belongs to), where the
/// thumb rests. Its words are not drawn: [label] is what a screen reader and
/// the long-press tooltip say. It sits on the gutter and clears whatever is
/// published below ([KitBottomInset]: the dock, the keyboard, a pinned
/// block), so it never covers the last row's actions. The list under it
/// should end with `KitScreen.endPadding` plus [clearance] so its last row
/// can scroll clear. Under Signal it is a dark ink key that draws its
/// words ("+ New conversation", concept CD).
///
/// States: disabled.
///
/// One button; its press, hover and focus looks are the
/// tappable's own, and an action that cannot run is not floated: with
/// [onPressed] null the button fades out (and back in when it can run)
/// while [child] stays the same widget, so the list under it never rebuilds
/// or moves.
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

  /// The action's words, naming what it starts: heard, never drawn.
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final Key? buttonKey;

  /// Room a scrolling list leaves under its last row so the button never
  /// hides it.
  static const clearance = 80.0;

  /// The round button's width and height.
  static const size = 56.0;

  /// Signal (concept CD): the action is a dark ink key with its words,
  /// "+ New conversation", raised on its lip; by night a dim key.
  Widget _signalKey(
    BuildContext context, {
    required VoidCallback? onPressed,
    required bool shown,
  }) {
    final tokens = KitTokens.of(context);
    final colors = kitSignalColors(context);
    final words = KitText.styleOf(
      context,
      KitTextRole.headline,
    ).copyWith(color: colors.inkKeyWords, fontWeight: FontWeight.w600);
    // A key's machined corners (Signal's sharp radius).
    final radius = BorderRadius.circular(
      kitStyleSpec(KitSurfaceStyle.signal).tokens.sharpRadius,
    );
    return ConstrainedBox(
      constraints: BoxConstraints(
        maxWidth: MediaQuery.sizeOf(context).width - 2 * tokens.gutter,
      ),
      child: KitTappable(
        tappableKey: shown ? buttonKey : null,
        label: label,
        onTap: onPressed ?? () {},
        shape: KitShape.button,
        child: Builder(
          builder: (context) => kitSurfaceBox(
            style: KitSurfaceLook.styleOf(context),
            decoration: kitSignalKey(
              fill: colors.inkKey,
              edge: colors.inkKeyEdge,
              radius: radius,
              pressed: KitTappable.pressedOf(context),
            ),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: size),
              child: Padding(
                padding: EdgeInsetsDirectional.only(
                  start: tokens.space4,
                  end: tokens.space5,
                ),
                child: ExcludeSemantics(
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        icon ?? AppIconography.add,
                        size: tokens.iconSize(context, 22),
                        color: colors.inkKeyWords,
                      ),
                      SizedBox(width: tokens.space2),
                      Flexible(
                        child: Text(
                          label,
                          style: words,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final accent = tokens.roles.accent;
    // A floating piece in the accent, from the style resolver: Solid keeps
    // its round disc; a style adds its own edge and depth (an ink edge and
    // a hard shadow, a raise) and follows Corners like a primary button
    // (square under Paper's Sharp). The accent stays the face. Held, it
    // asks as pressed (KitTappable.pressedOf), so a face that casts sinks
    // into its shadow (Brutal, hook H2).
    Decoration look(BuildContext context) => KitSurfaceLook.decorate(
      context,
      // A stadium on a square box: round today, a box once Corners square
      // the pills.
      solid: BoxDecoration(borderRadius: KitSurfaceLook.stadium, color: accent),
      role: KitSurfaceRole.floating,
      color: accent,
      pressed: KitTappable.pressedOf(context),
      action: true,
    );
    final onPressed = this.onPressed;
    final shown = onPressed != null;
    final button = KitTrack.on(context)
        ? _signalKey(context, onPressed: onPressed, shown: shown)
        : KitTappable(
            tappableKey: shown ? buttonKey : null,
            label: label,
            tooltip: label,
            onTap: onPressed ?? () {},
            shape: KitShape.circle,
            child: SizedBox.square(
              dimension: size,
              child: Builder(
                builder: (context) => kitSurfaceBox(
                  style: KitSurfaceLook.styleOf(context),
                  decoration: look(context),
                  child: Center(
                    child: Icon(
                      icon ?? AppIconography.add,
                      size: tokens.iconSize(context, 28),
                      // Hook H3: a style may say the words on its accent.
                      color: KitSurfaceLook.wordsOn(
                        context,
                        fallback: tokens.roles.onAccent,
                        role: KitSurfaceRole.floating,
                        color: accent,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          );
    return Stack(
      children: [
        child,
        PositionedDirectional(
          end: tokens.gutter,
          bottom: tokens.space3 + KitBottomInset.of(context).bottom,
          // Only a change of whether it can run fades (in or out); at rest
          // the button paints with no opacity layer of its own.
          child: TweenAnimationBuilder<double>(
            tween: Tween<double>(end: shown ? 1 : 0),
            duration: KitMotion.reduced(context)
                ? Duration.zero
                : KitMotion.standard,
            curve: KitMotion.enter,
            builder: (context, value, button) => value >= 1
                ? button!
                : IgnorePointer(
                    child: ExcludeSemantics(
                      child: Opacity(opacity: value, child: button),
                    ),
                  ),
            child: button,
          ),
        ),
      ],
    );
  }
}

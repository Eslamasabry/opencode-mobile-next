import 'package:flutter/material.dart';

import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'motion/kit_motion_parts.dart' show KitSwap;
import 'kit_max_width.dart';
import 'kit_status_mark.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A tonal pill for a thing that is not a filter and opens a chooser: the
/// server switcher on the Conversations home. Filled, fully rounded, no
/// outline; its fill is the status colour at low alpha (a green tint while
/// connected). It holds the status dot, the name and a chevron, and says a
/// word only when the state is not the healthy one ([word], for example
/// "Offline" or "Connecting…"): the dot carries "connected".
///
/// The name is cut with an ellipsis when it is long; the pill never scrolls
/// (its host keeps it fixed). The tint cross-fades through the style
/// resolver and the word cross-fades ([KitSwap]); under reduced motion both
/// are instant.
///
/// [semanticsLabel] is what a screen reader says in full ("This phone,
/// connected, Switch server").
///
/// States: none — a look per status tone; the word appears only when the
/// connection is not healthy.
class KitStatusPill extends StatelessWidget {
  const KitStatusPill({
    super.key,
    required this.label,
    required this.semanticsLabel,
    required this.tone,
    required this.onPressed,
    this.word,
    this.pillKey,
  });

  /// The name; wrap a name the server or person chose with `KitBidi.auto`.
  final String label;
  final String semanticsLabel;
  final AppStatusTone tone;
  final VoidCallback onPressed;

  /// The state in words, only when it is not connected.
  final String? word;
  final Key? pillKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final tint = switch (tone) {
      AppStatusTone.neutral => roles.text3,
      AppStatusTone.progress => roles.accent,
      AppStatusTone.ok => roles.success,
      AppStatusTone.attention => roles.attention,
      AppStatusTone.failure => roles.text1,
    };
    final word = this.word;
    final dot = tone == AppStatusTone.progress
        ? KitStatusMark(state: KitMarkState.working, label: word)
        : SizedBox.square(
            dimension: tokens.iconSize(context, tokens.smallIconSize),
            child: Center(
              child: CustomPaint(
                size: Size.square(tokens.space2),
                painter: _KitStatusDot(tint),
              ),
            ),
          );
    // The status colour at low alpha over the ground: the fill the
    // resolver paints (and cross-fades when the state changes).
    final fill = Color.alphaBlend(tint.withValues(alpha: .16), roles.ground);
    return KitMaxWidth.chip(
      child: KitTappable(
        tappableKey: pillKey,
        onTap: onPressed,
        label: semanticsLabel,
        shape: KitShape.pill,
        surface: KitSurfaceLevel.ground,
        child: ExcludeSemantics(
          child: KitText.capScale(
            max: 1.3,
            child: SizedBox(
              height: KitTokens.chipHeight,
              child: kitSurfaceMaterial(
                context,
                level: KitSurfaceLevel.ground,
                shape: tokens.shapeOf(KitShape.pill),
                fill: fill,
                role: KitSurfaceRole.control,
                child: Padding(
                  padding: EdgeInsetsDirectional.only(
                    start: tokens.space3,
                    end: tokens.space2,
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      dot,
                      SizedBox(width: tokens.space2),
                      Flexible(
                        child: KitText(
                          label,
                          role: KitTextRole.secondary,
                          tone: KitTextTone.primary,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Flexible(
                        child: KitSwap(
                          alignment: AlignmentDirectional.centerStart,
                          child: word == null
                              ? const SizedBox.shrink()
                              : Padding(
                                  key: ValueKey(word),
                                  padding: EdgeInsetsDirectional.only(
                                    start: tokens.space1,
                                  ),
                                  child: KitText(
                                    '· $word',
                                    role: KitTextRole.secondary,
                                    tone: KitTextTone.secondary,
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                        ),
                      ),
                      SizedBox(width: tokens.space1),
                      Icon(
                        AppIconography.chevronDown,
                        size: tokens.iconSize(context, tokens.smallIconSize),
                        color: roles.text2,
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
}

/// The state dot: a filled circle in the status colour.
class _KitStatusDot extends CustomPainter {
  const _KitStatusDot(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) => canvas.drawCircle(
    size.center(Offset.zero),
    size.shortestSide / 2,
    Paint()..color = color,
  );

  @override
  bool shouldRepaint(_KitStatusDot old) => old.color != color;
}

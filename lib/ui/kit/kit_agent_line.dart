import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_max_width.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The one quiet line under a question that says who will do the work and
/// where, and opens the choices behind it: "Claude Code · on this phone ⌄".
/// Plain muted words and a small chevron, no fill and no border, so it never
/// competes with the field above it. The whole line is the touch target
/// (48 dp tall at least) and a screen reader hears [semanticsLabel].
///
/// States: none — one line; it always opens its sheet, and a line that has
/// nothing to change is not drawn.
class KitAgentLine extends StatelessWidget {
  const KitAgentLine({
    super.key,
    required this.agent,
    required this.place,
    required this.onPressed,
    required this.semanticsLabel,
    this.tappableKey,
  });

  /// The agent's name ("Claude Code").
  final String agent;

  /// Where it works ("on this phone", a project's name).
  final String place;
  final VoidCallback onPressed;

  /// What a screen reader says: the line and what a tap changes.
  final String semanticsLabel;

  /// The key of the tappable region.
  final Key? tappableKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Align(
      alignment: AlignmentDirectional.centerStart,
      child: KitTappable(
        tappableKey: tappableKey,
        label: semanticsLabel,
        onTap: onPressed,
        shape: KitShape.button,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.space2,
            vertical: tokens.space2,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: ExcludeSemantics(
                  child: KitText(
                    '$agent · $place',
                    role: KitTextRole.secondary,
                    tone: KitTextTone.secondary,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
              SizedBox(width: tokens.space1),
              ExcludeSemantics(
                child: Icon(
                  AppIconography.chevronDown,
                  size: tokens.smallIconSize,
                  color: tokens.roles.text2,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

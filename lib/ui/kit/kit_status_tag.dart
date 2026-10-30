import 'package:flutter/material.dart';

import '../theme_roles.dart' show readableOn;
import 'kit_max_width.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

/// Which state a [KitStatusTag] names.
enum KitStatusTagTone {
  /// "Needs you": the needs-you tone (amber).
  needsYou,

  /// "Running": the accent tone.
  running,

  /// "Done": the success tone (green).
  done,

  /// "Failed", "Out until 14:10": plain secondary text with no fill. Never
  /// red, and not amber either: nothing here is for the person to act on.
  failed,
}

/// A small worded tag at the end of a feed row ("Needs you", "Running", "Done",
/// "Failed").
/// It is a fact, not a control: it never takes a tap and always carries its
/// word, never colour alone (STATE-9). For a tap target see KitChip.
///
/// States: none — a fixed word in one of four tones, with nothing to press.
class KitStatusTag extends StatelessWidget {
  const KitStatusTag({super.key, required this.label, required this.tone});

  /// The word, from the caller's ARB.
  final String label;
  final KitStatusTagTone tone;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.chip(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final base = switch (tone) {
      KitStatusTagTone.needsYou => roles.attention,
      KitStatusTagTone.running => roles.accent,
      // Calm, not a second green beside Running: news to read, nothing to do.
      KitStatusTagTone.done => roles.text2,
      KitStatusTagTone.failed => roles.text2,
    };
    final plain = tone == KitStatusTagTone.failed;
    final fill = plain
        ? Colors.transparent
        : Color.alphaBlend(base.withValues(alpha: .18), roles.surface1);
    final ink = plain
        ? roles.text2
        : readableOn(base, [fill], 4.7, toward: roles.text1);
    // A small control surface in its own tint, from the style resolver
    // (an ink edge, a square box); the plain tag is words alone in every
    // style.
    final solid = BoxDecoration(
      color: fill,
      borderRadius: BorderRadius.circular(tokens.space3),
    );
    final Decoration look = plain
        ? solid
        : KitSurfaceLook.decorate(
            context,
            solid: solid,
            role: KitSurfaceRole.control,
            color: fill,
          );
    // A change of tone or word cross-fades (KitMotion.quick), so a tag that
    // turns from Running to Done does not pop.
    return KitSwap(
      alignment: AlignmentDirectional.centerStart,
      child: kitSurfaceBox(
        key: ValueKey<(KitStatusTagTone, String)>((tone, label)),
        style: KitSurfaceLook.styleOf(context),
        decoration: look,
        child: Padding(
          padding: EdgeInsets.symmetric(
            horizontal: tokens.space2,
            vertical: tokens.space1,
          ),
          child: Text(
            label,
            // A plain tag ("Out until 1:25 PM") wraps rather than cut.
            maxLines: plain ? null : 1,
            overflow: plain ? null : TextOverflow.ellipsis,
            style: KitText.styleOf(
              context,
              KitTextRole.caption,
              tone: KitTextTone.primary,
            ).copyWith(color: ink),
          ),
        ),
      ),
    );
  }
}

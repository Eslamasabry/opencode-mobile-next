import 'package:flutter/material.dart';

import '../theme_roles.dart' show readableOn;
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Which state a [KitStatusTag] names.
enum KitStatusTagTone {
  /// "Needs you": the needs-you tone (amber).
  needsYou,

  /// "Running": the accent tone.
  running,

  /// "Done": the success tone (green).
  done,
}

/// A small worded tag at the end of a feed row ("Needs you", "Running", "Done").
/// It is a fact, not a control: it never takes a tap and always carries its
/// word, never colour alone (STATE-9). For a tap target see KitChip.
///
/// States: none — a fixed word in one of three tones, with nothing to press.
class KitStatusTag extends StatelessWidget {
  const KitStatusTag({super.key, required this.label, required this.tone});

  /// The word, from the caller's ARB.
  final String label;
  final KitStatusTagTone tone;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final base = switch (tone) {
      KitStatusTagTone.needsYou => roles.attention,
      KitStatusTagTone.running => roles.accent,
      // Calm, not a second green beside Running: news to read, nothing to do.
      KitStatusTagTone.done => roles.text2,
    };
    final fill = Color.alphaBlend(base.withValues(alpha: .18), roles.surface1);
    final ink = readableOn(base, [fill], 4.7, toward: roles.text1);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: fill,
        borderRadius: BorderRadius.circular(tokens.space3),
      ),
      child: Padding(
        padding: EdgeInsets.symmetric(
          horizontal: tokens.space2,
          vertical: tokens.space1,
        ),
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: KitText.styleOf(
            context,
            KitTextRole.caption,
            tone: KitTextTone.primary,
          ).copyWith(color: ink),
        ),
      ),
    );
  }
}

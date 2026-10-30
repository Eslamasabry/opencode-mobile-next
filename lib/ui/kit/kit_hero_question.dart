import 'package:flutter/material.dart';

import 'kit_max_width.dart';
import 'kit_text.dart';

/// The one big question a first page opens on ("Where should your agent
/// run?"): display type, larger than a screen title, tight tracking, at the
/// start of the page's rails. It is the page's heading and the only thing
/// the person has to read before choosing, so nothing else on the page is
/// set as large.
///
/// The size grows with the person's text size up to 1.15 times, and steps
/// down a size on a narrow phone, then wraps at its words: display type is
/// already large, so at 200 % text the answers under it stay near the first
/// screen (A11Y-8: only kit parts clamp text scale).
///
/// States: none — it shows the words it is given.
class KitHeroQuestion extends StatelessWidget {
  const KitHeroQuestion(this.question, {super.key, this.textKey});

  /// The question, in a few plain words.
  final String question;

  final Key? textKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => Semantics(
    header: true,
    child: KitText.capScale(
      max: _maxScale,
      child: LayoutBuilder(
        builder: (context, constraints) => Text(
          question,
          key: textKey,
          style: _style(context, narrow: constraints.maxWidth < _narrow),
        ),
      ),
    ),
  );
}

/// The question's display size, past the status answer's 40, and a smaller
/// one on a narrow phone so a word never breaks inside itself.
const double _size = 48;
const double _quietSize = 40;
const double _narrowSize = 32;
const double _narrow = 340;
const double _maxScale = 1.15;

/// The resolved style of [KitHeroQuestion] here: the large-title face and
/// colour with the display metrics. Arabic keeps its zero tracking.
TextStyle _style(BuildContext context, {required bool narrow}) {
  final base = KitText.styleOf(context, KitTextRole.largeTitle);
  // Solid: 40 at 500, hierarchy from size alone; a Style with type of its
  // own keeps the heavy 48.
  final firm = KitText.firmType(context);
  final size = narrow ? _narrowSize : (firm ? _size : _quietSize);
  return base.copyWith(
    fontSize: size,
    height: (size + 6) / size,
    fontWeight: firm ? FontWeight.w700 : FontWeight.w500,
    letterSpacing: base.letterSpacing == 0 ? 0 : -size * (firm ? .036 : .02),
  );
}

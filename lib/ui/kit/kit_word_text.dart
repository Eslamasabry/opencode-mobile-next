import 'package:flutter/widgets.dart';

import 'kit_max_width.dart';
import 'kit_text.dart';

/// Words in a [KitText] role that end at a whole word when they do not fit
/// [maxLines]: "…and retry when…", never "…and retry w…" (owner, 2026-10-22).
/// A single word wider than the line falls back to the framework's own
/// ellipsis. A screen reader hears the words in full.
///
/// States: none — it shows the words it is given.
class KitWordText extends StatelessWidget {
  const KitWordText(
    this.text, {
    super.key,
    required this.maxLines,
    this.role = KitTextRole.body,
    this.tone,
  }) : assert(maxLines > 0);

  final String text;
  final int maxLines;
  final KitTextRole role;
  final KitTextTone? tone;

  /// [text] cut after the last whole word that, with "…", still fits
  /// [maxLines] lines of [maxWidth]; [text] itself when it fits, or when no
  /// word does.
  static String cut(
    String text, {
    required TextStyle style,
    required double maxWidth,
    required int maxLines,
    required TextScaler scaler,
    required TextDirection direction,
  }) {
    bool fits(String candidate) {
      final painter = TextPainter(
        text: TextSpan(text: candidate, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: maxLines,
      )..layout(maxWidth: maxWidth);
      final ok = !painter.didExceedMaxLines;
      painter.dispose();
      return ok;
    }

    if (!maxWidth.isFinite || fits(text)) return text;
    final ends = [
      for (final gap in RegExp(r'\s+').allMatches(text))
        if (gap.start > 0) gap.start,
    ];
    var low = 0;
    var high = ends.length - 1;
    String? best;
    while (low <= high) {
      final mid = (low + high) >> 1;
      final candidate =
          '${text.substring(0, ends[mid]).replaceFirst(RegExp(r'[\s,;:.!?·–—…-]+$'), '')}…';
      if (fits(candidate)) {
        best = candidate;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return best ?? text;
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => LayoutBuilder(
    builder: (context, box) {
      final shown = cut(
        text,
        style: KitText.styleOf(context, role, tone: tone),
        maxWidth: box.maxWidth,
        maxLines: maxLines,
        scaler: MediaQuery.textScalerOf(context),
        direction: Directionality.of(context),
      );
      return KitText(
        shown,
        role: role,
        tone: tone,
        maxLines: maxLines,
        overflow: TextOverflow.ellipsis,
        semanticsLabel: identical(shown, text) ? null : text,
      );
    },
  );
}

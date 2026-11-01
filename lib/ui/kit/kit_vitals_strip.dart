import 'package:flutter/material.dart';

import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

/// One reading of a [KitVitalsStrip]: a short value over the word that
/// names it ("34 °C" over "Cool", "2.1 GB" over "Memory free").
@immutable
class KitVital {
  const KitVital({
    required this.value,
    required this.word,
    required this.semanticsLabel,
  });

  /// Short by construction: a number with its unit, or one word.
  final String value;
  final String word;

  /// The whole reading for a screen reader: "Phone heat: cool".
  final String semanticsLabel;
}

/// A page's head of live readings (Linux › vitals, docs/design/
/// linux-manager-2026-10-31/3-ui.md §4.2): up to four [vitals], each a value
/// over its word, sharing the width equally, and one plain [summary]
/// sentence under them. A reading that cannot be had is left out by the
/// caller, never shown as 0.
///
/// Capacity: at most [maxVitals] cells (an assert). Values keep
/// one line (they shrink to fit, never ellipsize); words keep one line and
/// ellipsize; the summary wraps to three lines, then ellipsizes. Cells are
/// frame text, capped at 1.3× the normal size; the summary is reading text
/// and follows the system size. Under 340 dp, or from 1.6× text, more than
/// two cells wrap into rows of two. Its width is the reading cap.
///
/// Heat is a degraded state, never "needs you": no cell is amber (LOOK-4).
///
/// States: [loading] (every value "—"), normal, and a
/// tappable summary ([onSummaryTap]). A value change cross-fades
/// (KitMotion.quick; at once when motion is reduced). Not a live region:
/// it updates every few seconds.
class KitVitalsStrip extends StatelessWidget {
  const KitVitalsStrip({
    super.key,
    required this.vitals,
    this.summary,
    this.onSummaryTap,
    this.summaryLabel,
    this.loading = false,
    this.stripKey,
  }) : assert(vitals.length <= maxVitals, 'At most $maxVitals readings');

  static const maxVitals = 4;

  /// Frame text never scales past this (owner rule, 2026-10-31).
  static const frameTextCap = 1.3;

  final List<KitVital> vitals;
  final String? summary;

  /// Makes the summary a button (it names a program to look at).
  final VoidCallback? onSummaryTap;

  /// What a screen reader says the summary button does.
  final String? summaryLabel;
  final bool loading;
  final Key? stripKey;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final shown = vitals;
    final media = MediaQuery.of(context);
    final scale = media.textScaler.scale(1);
    final text = summary;
    return KitMaxWidth.reading(
      alignment: AlignmentDirectional.centerStart,
      child: Column(
        key: stripKey,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          if (shown.isNotEmpty)
            MediaQuery.withClampedTextScaling(
              maxScaleFactor: frameTextCap,
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final perRow =
                      shown.length > 2 &&
                          (constraints.maxWidth < KitLayout.vitalsRowMinWidth ||
                              scale >= 1.6)
                      ? 2
                      : shown.length;
                  final rows = <Widget>[];
                  for (var i = 0; i < shown.length; i += perRow) {
                    final slice = shown.skip(i).take(perRow).toList();
                    if (rows.isNotEmpty) {
                      rows.add(SizedBox(height: tokens.space3));
                    }
                    rows.add(
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          for (var j = 0; j < perRow; j++)
                            Expanded(
                              child: j < slice.length
                                  ? _Cell(vital: slice[j], loading: loading)
                                  : const SizedBox.shrink(),
                            ),
                        ],
                      ),
                    );
                  }
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    mainAxisSize: MainAxisSize.min,
                    children: rows,
                  );
                },
              ),
            ),
          if (text != null) ...[
            if (shown.isNotEmpty) SizedBox(height: tokens.space2),
            _Summary(text: text, onTap: onSummaryTap, label: summaryLabel),
          ],
        ],
      ),
    );
  }
}

class _Cell extends StatelessWidget {
  const _Cell({required this.vital, required this.loading});

  final KitVital vital;
  final bool loading;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final value = loading ? '—' : vital.value;
    return Semantics(
      container: true,
      label: loading ? vital.word : vital.semanticsLabel,
      excludeSemantics: true,
      child: Padding(
        padding: EdgeInsetsDirectional.only(end: tokens.space2),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            KitSwap(
              alignment: AlignmentDirectional.centerStart,
              child: FittedBox(
                key: ValueKey<String>(value),
                fit: BoxFit.scaleDown,
                alignment: AlignmentDirectional.centerStart,
                child: KitText(
                  value,
                  role: KitTextRole.headline,
                  tabular: true,
                  maxLines: 1,
                  softWrap: false,
                ),
              ),
            ),
            SizedBox(height: tokens.space1 / 2),
            KitText(
              vital.word,
              role: KitTextRole.caption,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}

class _Summary extends StatelessWidget {
  const _Summary({required this.text, this.onTap, this.label});

  final String text;
  final VoidCallback? onTap;
  final String? label;

  @override
  Widget build(BuildContext context) {
    final words = KitSwap(
      alignment: AlignmentDirectional.centerStart,
      child: KitText(
        text,
        key: ValueKey<String>(text),
        role: KitTextRole.secondary,
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
        tone: onTap == null ? null : KitTextTone.accent,
      ),
    );
    final tap = onTap;
    if (tap == null) return words;
    return KitTappable(onTap: tap, label: label ?? text, child: words);
  }
}

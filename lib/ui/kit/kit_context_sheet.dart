import 'package:flutter/material.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import 'kit_context_chart.dart';
import 'kit_context_ring.dart';
import 'kit_key_value.dart';
import 'kit_max_width.dart';
import 'kit_section_label.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// What the context details sheet shows (docs/ux-system/kit-api/
/// KitContextSheet.md): a big ring with the percent and the tokens in
/// words, the model, the window and when it was measured, one paragraph on
/// what the window is, and a bar per reply. It is the body of
/// `showKitSheet`; the sheet's title and its "Compact now" button belong to
/// the opener, which offers the button only when the server can compact.
///
/// States: none — it is the numbers it is given; with fewer than two
/// replies the chart is left out.
class KitContextSheet extends StatelessWidget {
  const KitContextSheet({
    super.key,
    required this.tokens,
    required this.limit,
    required this.modelName,
    required this.bars,
    this.compacting = false,
    this.chartKey,
  });

  /// Tokens in the context after the last reply.
  final int tokens;

  /// The model's window, in tokens (above zero).
  final int limit;

  /// The model's name as the catalog gives it (the server chose it).
  final String modelName;

  /// One bar per reply that reported tokens, oldest first.
  final List<KitContextBar> bars;

  final bool compacting;
  final Key? chartKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.sheet(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final kit = KitTokens.of(context);
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final format = NumberFormat.decimalPattern(
      Localizations.localeOf(context).toString(),
    );
    final fraction = limit > 0 ? tokens / limit : 0.0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      spacing: kit.space3,
      children: [
        Row(
          spacing: kit.space4,
          children: [
            KitContextRing(
              used: fraction,
              compacting: compacting,
              size: KitContextRingSize.large,
            ),
            Expanded(
              child: KitText(
                l10n.kitContextSheetTokens(
                  format.format(tokens),
                  format.format(limit),
                ),
                role: KitTextRole.rowTitle,
                tabular: true,
              ),
            ),
          ],
        ),
        KitKeyValue(
          rows: [
            KitKeyValueRow(label: l10n.kitContextSheetModel, value: modelName),
            KitKeyValueRow(
              label: l10n.kitContextSheetWindow,
              value: format.format(limit),
            ),
            KitKeyValueRow(
              label: l10n.kitContextSheetMeasured,
              value: l10n.kitContextSheetMeasuredValue,
            ),
          ],
        ),
        KitText(
          l10n.kitContextSheetExplain,
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
        if (bars.length >= 2) ...[
          KitSectionLabel.inline(l10n.kitContextSheetChartTitle),
          KitContextChart(bars: bars, chartKey: chartKey),
          if (bars.any((bar) => bar.compactedBefore))
            KitText(
              l10n.kitContextChartCompacted,
              role: KitTextRole.caption,
              tone: KitTextTone.tertiary,
            ),
        ],
      ],
    );
  }
}

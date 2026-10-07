import 'dart:async';

import 'package:flutter/widgets.dart';

import '../../domain/genui/gen_ui.dart';
import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../kit/kit.dart';
import 'external_link.dart' show openExternalLink;

/// How many changed files a card's table draws before "and N more".
const int agentCardDiffRows = KitMiniTable.maxRows;

/// The kit parts for a card's body nodes, in order. The agent describes; the
/// app draws: no node carries a style, a URL to load or anything to run.
/// Every string is the agent's plain text, isolated so mixed-direction words
/// cannot reorder their neighbours.
List<Widget> agentCardNodes(BuildContext context, List<GenUiNode> body) => [
  for (final node in body) agentCardNode(context, node),
];

Widget agentCardNode(BuildContext context, GenUiNode node) {
  final l10n = AppLocalizations.of(context);
  return switch (node) {
    GenUiText() => KitText(
      KitBidi.auto(node.text),
      role: KitTextRole.body,
      tone: KitTextTone.primary,
    ),
    GenUiKeyValue() => KitKeyValue(
      rows: [
        for (final row in node.rows)
          KitKeyValueRow(
            label: KitBidi.auto(row.key),
            value: KitBidi.auto(row.value),
          ),
      ],
    ),
    GenUiList() => _list(context, node),
    GenUiTable() => KitMiniTable(
      columns: [for (final column in node.columns) KitBidi.auto(column)],
      rows: [
        for (final row in node.rows)
          KitMiniTableRow([for (final cell in row) KitBidi.auto(cell)]),
      ],
      semanticsLabel: l10n.agentCardTableLabel(
        node.columns.length,
        node.rows.length,
      ),
    ),
    GenUiChart() => KitChart(
      kind: node.kind == GenUiChartKind.bar
          ? KitChartKind.bar
          : KitChartKind.line,
      labels: [for (final label in node.labels) KitBidi.auto(label)],
      series: [
        for (final series in node.series)
          KitChartSeries(
            name: KitBidi.auto(series.name),
            values: series.values,
          ),
      ],
      unit: node.unit,
      words: KitChartWords(
        barChart: l10n.agentCardChartBar,
        lineChart: l10n.agentCardChartLine,
        seriesCount: l10n.agentCardChartSeries,
        pointCount: l10n.agentCardChartPoints,
        highest: l10n.agentCardChartHighest,
        noData: l10n.agentCardChartNone,
      ),
    ),
    GenUiCode() => KitCodeBlock(
      text: node.text,
      language: node.language,
      copyable: false,
    ),
    GenUiDiffStat() => _diffStat(context, node),
    GenUiProgress() => KitProgressRow(
      title: KitBidi.auto(node.label),
      value: node.value.clamp(0.0, 1.0),
      valueLabel: l10n.agentCardProgressValue(
        (node.value.clamp(0.0, 1.0) * 100).round(),
      ),
    ),
    GenUiCallout() => KitNotice(
      message: KitBidi.auto(node.text),
      tone: switch (node.tone) {
        GenUiCalloutTone.info => AppStatusTone.neutral,
        // Amber is reserved for "needs you": a warning is a failure tone with
        // the warning glyph, an agent's success is the plain ok tone.
        GenUiCalloutTone.warning => AppStatusTone.failure,
        GenUiCalloutTone.success => AppStatusTone.ok,
      },
      icon: switch (node.tone) {
        GenUiCalloutTone.info => AppIconography.info,
        GenUiCalloutTone.warning => AppIconography.warning,
        GenUiCalloutTone.success => AppIconography.checkCircle,
      },
    ),
    GenUiLink() => _link(context, node),
  };
}

Widget _list(BuildContext context, GenUiList node) {
  final check = node.style == GenUiListStyle.check;
  return KitRowGroup(
    margin: EdgeInsets.zero,
    leadingIcons: true,
    children: [
      for (final item in node.items)
        KitRow(
          title: KitBidi.auto(item.text),
          titleMaxLines: 4,
          leading: KitRow.icon(
            context,
            !check
                ? AppIconography.statusDot
                : item.done
                ? AppIconography.checkCircle
                : AppIconography.radioEmpty,
          ),
        ),
    ],
  );
}

Widget _diffStat(BuildContext context, GenUiDiffStat node) {
  final l10n = AppLocalizations.of(context);
  final shown = node.files.take(agentCardDiffRows).toList();
  final more = node.files.length - shown.length;
  return Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    mainAxisSize: MainAxisSize.min,
    children: [
      KitMiniTable(
        columns: [
          l10n.agentCardDiffFile,
          l10n.agentCardDiffAdded,
          l10n.agentCardDiffRemoved,
        ],
        rows: [
          for (final file in shown)
            KitMiniTableRow([
              KitBidi.ltr(file.path),
              '+${file.added}',
              '−${file.removed}',
            ]),
        ],
        semanticsLabel: l10n.agentCardTableLabel(3, shown.length),
      ),
      if (more > 0)
        KitText(
          l10n.agentCardDiffMore(more),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
    ],
  );
}

Widget _link(BuildContext context, GenUiLink node) {
  final host = Uri.tryParse(node.url)?.host;
  return Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    mainAxisSize: MainAxisSize.min,
    children: [
      KitButton.tertiary(
        label: KitBidi.auto(node.label),
        icon: AppIconography.externalLink,
        // The one door for an address the app did not write.
        onPressed: () => unawaited(openExternalLink(context, node.url)),
      ),
      if (host != null && host.isNotEmpty)
        KitText(
          KitBidi.ltr(host),
          role: KitTextRole.secondary,
          tone: KitTextTone.secondary,
        ),
    ],
  );
}

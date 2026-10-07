import 'dart:math' as math;

import 'package:flutter/material.dart';

import 'kit_scrollbar.dart';
import 'kit_surface.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One row of a [KitMiniTable]: a cell per column. A short row is padded.
@immutable
class KitMiniTableRow {
  const KitMiniTableRow(this.cells);

  final List<String> cells;
}

/// A small table: a header row over up to [maxRows] rows of up to
/// [maxColumns] columns, for an agent's results ("File · Added · Removed").
/// Cells size to their words (a long cell wraps at [maxCellWidth]); when the
/// table is wider than its place it scrolls sideways inside its own panel and
/// never widens the page. A column whose cells are all numbers aligns to the
/// end with tabular figures. The header row is quiet text over a hairline.
///
/// States: none — it draws the cells it is given.
class KitMiniTable extends StatefulWidget {
  const KitMiniTable({
    super.key,
    required this.columns,
    required this.rows,
    this.semanticsLabel,
  });

  static const int maxColumns = 6;
  static const int maxRows = 20;

  /// The widest a cell grows before its words wrap.
  static const double maxCellWidth = 200;

  final List<String> columns;
  final List<KitMiniTableRow> rows;

  /// What the table is, for a screen reader ("Results, 3 columns, 5 rows").
  final String? semanticsLabel;

  @override
  State<KitMiniTable> createState() => _KitMiniTableState();
}

class _KitMiniTableState extends State<KitMiniTable> {
  final ScrollController _scroll = ScrollController();

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  static final _number = RegExp(r'^[\s+\-−]?[\d.,]+\s*[%a-zA-Z]{0,3}$');

  bool _numeric(int column, List<KitMiniTableRow> rows) =>
      rows.isNotEmpty &&
      rows.every(
        (row) =>
            column < row.cells.length &&
            _number.hasMatch(row.cells[column].trim()),
      );

  @override
  Widget build(BuildContext context) {
    assert(
      widget.columns.length <= KitMiniTable.maxColumns &&
          widget.rows.length <= KitMiniTable.maxRows,
      'KitMiniTable draws at most ${KitMiniTable.maxColumns} columns and '
      '${KitMiniTable.maxRows} rows (agent-card contract)',
    );
    final columns = widget.columns.take(KitMiniTable.maxColumns).toList();
    if (columns.isEmpty) return const SizedBox.shrink();
    final rows = widget.rows.take(KitMiniTable.maxRows).toList();
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final border = BorderSide(
      color: roles.hairline,
      width: KitTokens.hairlineWidth(context),
    );
    final numeric = [
      for (var c = 0; c < columns.length; c++) _numeric(c, rows),
    ];

    final cellPad = tokens.space3 * 2;
    TextStyle styleOf(bool header, int column) =>
        KitText.styleOf(
          context,
          header ? KitTextRole.label : KitTextRole.body,
          tone: header ? KitTextTone.secondary : KitTextTone.primary,
        ).copyWith(
          fontFeatures: numeric[column]
              ? const [FontFeature.tabularFigures()]
              : null,
        );

    // Column widths from the words themselves (never wider than a cell may
    // be), so the table is as wide as its words need.
    final scaler = MediaQuery.textScalerOf(context);
    final direction = Directionality.of(context);
    double measure(String text, TextStyle style) {
      final painter = TextPainter(
        text: TextSpan(text: text, style: style),
        textDirection: direction,
        textScaler: scaler,
        maxLines: 1,
      )..layout();
      final width = painter.width;
      painter.dispose();
      return width;
    }

    final widths = <double>[
      for (var c = 0; c < columns.length; c++)
        () {
          var widest = measure(columns[c], styleOf(true, c));
          for (final row in rows) {
            if (c < row.cells.length) {
              widest = math.max(
                widest,
                measure(row.cells[c], styleOf(false, c)),
              );
            }
          }
          return math.min(
                widest.ceilToDouble() + 1,
                KitMiniTable.maxCellWidth - cellPad,
              ) +
              cellPad;
        }(),
    ];

    Widget cell(String text, int column, double width, {required bool header}) {
      return SizedBox(
        width: width,
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(
            horizontal: tokens.space3,
            vertical: tokens.space2,
          ),
          child: KitText(
            text,
            role: header ? KitTextRole.label : KitTextRole.body,
            tone: header ? KitTextTone.secondary : KitTextTone.primary,
            tabular: numeric[column],
            textAlign: numeric[column] ? TextAlign.end : TextAlign.start,
          ),
        ),
      );
    }

    // One row is one thing to a screen reader: "lib/main.dart, 12, 3".
    Widget line(List<String> cells, double scale, {required bool header}) {
      return MergeSemantics(
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: header ? roles.surface2 : null,
            border: Border(bottom: border),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (var c = 0; c < columns.length; c++)
                cell(
                  c < cells.length ? cells[c] : '',
                  c,
                  widths[c] * scale,
                  header: header,
                ),
            ],
          ),
        ),
      );
    }

    return Semantics(
      container: true,
      label: widget.semanticsLabel,
      child: KitSurface(
        padding: KitSurfacePadding.none,
        outlined: true,
        child: LayoutBuilder(
          builder: (context, box) {
            final natural = widths.fold<double>(0, (a, b) => a + b);
            // A roomy place stretches the columns evenly; a tight one scrolls.
            final scale = natural < box.maxWidth ? box.maxWidth / natural : 1.0;
            return KitScrollbar(
              controller: _scroll,
              axis: Axis.horizontal,
              child: SingleChildScrollView(
                controller: _scroll,
                scrollDirection: Axis.horizontal,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    line(columns, scale, header: true),
                    for (final row in rows)
                      line(row.cells, scale, header: false),
                  ],
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}

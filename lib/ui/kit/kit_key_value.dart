import 'package:flutter/material.dart';

import 'kit_divider.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One label and its value in a [KitKeyValue].
@immutable
class KitKeyValueRow {
  const KitKeyValueRow({required this.label, required this.value});

  /// What the value is: "Duration". Wraps; never cut.
  final String label;

  /// The value, as the agent wrote it. Wraps; never cut.
  final String value;
}

/// Label and value rows on one panel: the facts of a report ("Duration
/// 4 min 12 s", "Files changed 7"). The label is quiet and leads, the value
/// follows in primary text with tabular figures so numbers line up down the
/// column. Long values wrap, and from a large text size (or a narrow
/// window) each row stacks its value under its label so neither is cut.
/// At most [maxRows] rows are drawn; the caller keeps to the same bound.
///
/// States: none — it draws the rows it is given (an empty list draws
/// nothing).
class KitKeyValue extends StatelessWidget {
  const KitKeyValue({super.key, required this.rows});

  /// The bound the agent-card contract sets.
  static const int maxRows = 20;

  final List<KitKeyValueRow> rows;

  @override
  Widget build(BuildContext context) {
    assert(
      rows.length <= maxRows,
      'KitKeyValue draws at most $maxRows rows (agent-card contract)',
    );
    final shown = rows.take(maxRows).toList();
    if (shown.isEmpty) return const SizedBox.shrink();
    final tokens = KitTokens.of(context);
    final stacked = MediaQuery.textScalerOf(context).scale(10) >= 15;
    final narrow = stacked;
    return Semantics(
      container: true,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          for (var i = 0; i < shown.length; i++) ...[
            if (i > 0) const KitDivider(),
            Padding(
              padding: EdgeInsetsDirectional.symmetric(vertical: tokens.space2),
              child: MergeSemantics(
                child: narrow
                    ? Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          _label(shown[i]),
                          _value(shown[i], TextAlign.start),
                        ],
                      )
                    : Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Expanded(flex: 2, child: _label(shown[i])),
                          SizedBox(width: tokens.space3),
                          Expanded(
                            flex: 3,
                            child: _value(shown[i], TextAlign.end),
                          ),
                        ],
                      ),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _label(KitKeyValueRow row) => KitText(
    row.label,
    role: KitTextRole.secondary,
    tone: KitTextTone.secondary,
  );

  Widget _value(KitKeyValueRow row, TextAlign align) => KitText(
    row.value,
    role: KitTextRole.body,
    tone: KitTextTone.primary,
    tabular: true,
    textAlign: align,
  );
}

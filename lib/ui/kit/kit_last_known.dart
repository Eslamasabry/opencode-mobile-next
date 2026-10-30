import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_max_width.dart';
import 'kit_row.dart';

/// One row of [KitLastKnown]: a label remembered from the last time the
/// list was read, and an optional short fact ("5m ago").
@immutable
class KitLastKnownRow {
  const KitLastKnownRow({required this.title, this.detail, this.key});

  /// The remembered label, shown as written (it was redacted when saved).
  final String title;

  /// A short muted fact under the title; null for none.
  final String? detail;

  /// On the row, for tests.
  final Key? key;
}

/// What a list held the last time it was read, shown read-only while the
/// live answer is on its way (docs/ux-system/kit-api/KitLastKnown.md), so a
/// page opens with the person's own labels instead of placeholders.
///
/// A labelled [KitRowGroup]: the label says how old the rows are
/// ([updated], "Updated 5 minutes ago") and, while [refreshing], that a
/// fresh read is under way. The rows have no tap, menu or swipe and no
/// running, waiting or pinned marks: remembered labels prove nothing about
/// what exists or runs now. The screen's own loading bar or connection
/// state carries progress; this part draws no spinner and runs no ticker.
///
/// The live list replaces it as soon as it arrives, including a live list
/// that is empty.
///
/// States: loading.
class KitLastKnown extends StatelessWidget {
  const KitLastKnown({
    super.key,
    required this.rows,
    required this.updated,
    this.refreshing = true,
    this.labelKey,
  });

  /// The remembered rows, in the order they were shown.
  final List<KitLastKnownRow> rows;

  /// How old the rows are, in words ("Updated 5 minutes ago").
  final String updated;

  /// A fresh read is under way: the label adds "Refreshing".
  final bool refreshing;

  /// On the group's label, for tests.
  final Key? labelKey;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final label = refreshing ? l10n.kitLastKnownRefreshing(updated) : updated;
    return Semantics(
      container: true,
      explicitChildNodes: true,
      // Said once for the group: these rows are a memory, not the list.
      hint: l10n.kitLastKnownHint,
      child: KitRowGroup(
        key: labelKey,
        label: label,
        leadingIcons: false,
        children: [
          for (final row in rows)
            KitRow(
              key: row.key,
              title: row.title,
              titleMaxLines: 2,
              supporting: row.detail == null
                  ? null
                  : TextSpan(text: row.detail),
            ),
        ],
      ),
    );
  }
}

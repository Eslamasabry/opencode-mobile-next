import 'package:flutter/material.dart';
import '../../../l10n/app_localizations.dart';
import '../kit_buttons.dart';
import '../kit_details_fold.dart';
import '../kit_max_width.dart';
import 'kit_team_data.dart';
import 'team_parts.dart';

/// TimelineDay: a localized, controlled team surface with no engine dependencies.
/// States: loading, empty, error, working, disabled, answered.
class KitTimelineDay extends StatelessWidget {
  const KitTimelineDay({
    super.key,
    required this.title,
    required this.status,
    this.state = KitTeamState.done,
    this.summary,
    this.moreLabel,
    this.items = const [],
    this.primary,
    this.actions = const [],
  });
  final String title;
  final String status;
  final KitTeamState state;
  final String? summary;
  final String? moreLabel;
  final List<KitTeamItem> items;
  final KitAction? primary;
  final List<KitAction> actions;
  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => teamCard(
    context,
    title: title,
    status: status,
    state: state,
    summary: summary,
    items: items.take(10).toList(),
    content: [
      if (items.length > 10)
        KitDetailsFold(
          label:
              moreLabel ??
              lookupAppLocalizations(
                Localizations.localeOf(context),
              ).kitSuggestionsShowAll,
          child: Column(
            children: [
              for (final item in items.skip(10))
                teamRow(
                  context,
                  title: item.title,
                  status: '',
                  detail: item.detail,
                  meta: item.meta,
                  state: item.state,
                  onPressed: item.onPressed,
                ),
            ],
          ),
        ),
    ],
    primary: primary,
    actions: actions,
    flat: true,
  );
}

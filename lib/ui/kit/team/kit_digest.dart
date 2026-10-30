import 'package:flutter/material.dart';
import '../kit_buttons.dart';
import '../kit_max_width.dart';
import 'kit_team_data.dart';
import 'team_parts.dart';

/// Digest: a localized, controlled team surface with no engine dependencies.
/// States: loading, empty, error, working, disabled, answered.
class KitDigest extends StatelessWidget {
  const KitDigest({
    super.key,
    required this.title,
    required this.status,
    this.state = KitTeamState.done,
    this.summary,
    this.items = const [],
    this.primary,
    this.actions = const [],
  });
  final String title;
  final String status;
  final KitTeamState state;
  final String? summary;
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
    items: items,
    primary: primary,
    actions: actions,
    flat: false,
  );
}

import 'package:flutter/material.dart';
import '../kit_max_width.dart';
import 'kit_team_data.dart';
import 'team_parts.dart';

/// MilestoneRow: a flat row with actual counted progress, never guessed progress.
/// States: disabled, loading, empty, error, working, answered.
class KitMilestoneRow extends StatelessWidget {
  const KitMilestoneRow({
    super.key,
    required this.title,
    required this.status,
    this.state = KitTeamState.done,
    this.detail,
    this.meta,
    this.onPressed,
    this.completed,
    this.total,
  });
  final String title;
  final String status;
  final KitTeamState state;
  final String? detail;
  final String? meta;
  final VoidCallback? onPressed;
  final int? completed;
  final int? total;
  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => teamRow(
    context,
    title: title,
    status: status,
    state: state,
    detail: detail,
    meta: meta,
    onPressed: onPressed,
    completed: completed,
    total: total,
  );
}

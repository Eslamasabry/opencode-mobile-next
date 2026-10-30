import 'package:flutter/material.dart';
import '../../app_iconography.dart';
import '../kit_buttons.dart';
import '../kit_icon.dart';
import '../kit_max_width.dart';
import '../kit_tappable.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_team_data.dart';
import 'team_parts.dart';

/// PlanCard: the plan awaiting a person's answer, or a plain summary card.
/// With [phases] it shows numbered tasks grouped by phase, each with a
/// one-line detail and who does it, then [more] when the list is cut short.
/// The primary action is the card's one primary; the rest are neutral.
/// States: loading, empty, error, working, disabled, answered.
class KitPlanCard extends StatelessWidget {
  const KitPlanCard({
    super.key,
    required this.title,
    required this.status,
    this.state = KitTeamState.done,
    this.summary,
    this.items = const [],
    this.phases = const [],
    this.more,
    this.primary,
    this.secondary,
    this.actions = const [],
  });
  final String title;
  final String status;
  final KitTeamState state;
  final String? summary;
  final List<KitTeamItem> items;
  final List<KitPlanPhase> phases;

  /// "… 4 more tasks": shown under the phases when the plan is cut short.
  final String? more;
  final KitAction? primary;

  /// The neutral "not now" answer, drawn beside the primary.
  final KitAction? secondary;
  final List<KitAction> actions;
  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final t = KitTokens.of(context);
    return teamCard(
      context,
      title: title,
      status: status,
      state: state,
      summary: summary,
      items: items,
      primary: primary,
      secondary: secondary,
      actions: actions,
      neutralStatus: phases.isNotEmpty,
      content: [
        for (final phase in phases) ...[
          SizedBox(height: t.space3),
          KitText(
            phase.flagLabel == null
                ? phase.title
                : '${phase.title} · ${phase.flagLabel}',
            role: KitTextRole.label,
            tone: phase.flagLabel == null
                ? KitTextTone.secondary
                : KitTextTone.attention,
          ),
          for (final task in phase.tasks) _PlanTaskRow(task: task),
        ],
        if (more != null) ...[
          SizedBox(height: t.space2),
          KitText(more!, role: KitTextRole.secondary),
        ],
      ],
      flat: false,
    );
  }
}

class _PlanTaskRow extends StatelessWidget {
  const _PlanTaskRow({required this.task});
  final KitPlanTask task;
  @override
  Widget build(BuildContext context) {
    final t = KitTokens.of(context);
    final large = MediaQuery.textScalerOf(context).scale(1) > 1.3;
    final who = task.who == null
        ? null
        : task.onChangeWho != null
        ? KitTappable(
            onTap: task.onChangeWho,
            label: task.who,
            child: Padding(
              padding: EdgeInsets.symmetric(vertical: t.space2),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Flexible(
                    child: KitText(
                      task.who!,
                      role: KitTextRole.secondary,
                      textAlign: large ? TextAlign.start : TextAlign.end,
                    ),
                  ),
                  SizedBox(width: t.space1),
                  const KitIcon(
                    AppIconography.chevronDown,
                    size: KitIconSize.small,
                  ),
                ],
              ),
            ),
          )
        : KitText(
            task.who!,
            role: KitTextRole.secondary,
            textAlign: large ? TextAlign.start : TextAlign.end,
          );
    return Padding(
      padding: EdgeInsets.symmetric(vertical: t.space2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: t.space5,
            child: KitText('${task.number}', role: KitTextRole.secondary),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                KitText(task.title, role: KitTextRole.rowTitle),
                if (task.detail != null)
                  KitText(task.detail!, role: KitTextRole.secondary),
                if (large && who != null) who,
              ],
            ),
          ),
          if (!large && who != null) ...[
            SizedBox(width: t.space2),
            Flexible(child: who),
          ],
        ],
      ),
    );
  }
}

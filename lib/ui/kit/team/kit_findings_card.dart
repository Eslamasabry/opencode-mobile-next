import 'package:flutter/material.dart';
import '../kit_buttons.dart';
import '../kit_chip.dart';
import '../kit_max_width.dart';
import '../kit_motion.dart';
import '../kit_tappable.dart';
import '../../app_iconography.dart';
import '../kit_text.dart';
import '../kit_tokens.dart';
import 'kit_team_data.dart';
import 'team_parts.dart';

/// A controlled selection of findings ranked by explicit, worded severity.
/// States: loading, empty, error, disabled, working, answered.
class KitFindingsCard extends StatelessWidget {
  const KitFindingsCard({
    super.key,
    required this.title,
    required this.status,
    required this.findings,
    this.state = KitTeamState.done,
    this.summary,
    this.primary,
    this.actions = const [],
  });
  final String title;
  final String status;
  final List<KitTeamFinding> findings;
  final KitTeamState state;
  final String? summary;
  final KitAction? primary;
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
      primary: primary,
      actions: actions,
      content: [
        for (final finding in findings)
          Padding(
            padding: EdgeInsets.symmetric(vertical: t.space2),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _FindingSelection(
                  selected: finding.selected,
                  label: '${finding.severityLabel}: ${finding.title}',
                  disabledReason: status,
                  onChanged:
                      state == KitTeamState.stale ||
                          state == KitTeamState.loading
                      ? null
                      : finding.onChanged,
                ),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      KitChip(
                        label: finding.severityLabel,
                        tone: switch (finding.severity) {
                          KitFindingSeverity.critical => KitChipTone.attention,
                          KitFindingSeverity.major => KitChipTone.attention,
                          KitFindingSeverity.minor => KitChipTone.neutral,
                        },
                      ),
                      KitText(finding.title, role: KitTextRole.rowTitle),
                      if (finding.location != null)
                        KitText.mono(finding.location!),
                      if (finding.detail != null)
                        KitText(
                          finding.detail!,
                          role: KitTextRole.secondary,
                          tone: KitTextTone.secondary,
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// The choice-mark pattern, with neutral ink and no animation when motion is off.
/// Keep the same tappable mounted as selection changes so keyboard focus stays.
class _FindingSelection extends StatelessWidget {
  const _FindingSelection({
    required this.selected,
    required this.label,
    required this.disabledReason,
    required this.onChanged,
  });

  final bool selected;
  final String label;
  final String disabledReason;
  final ValueChanged<bool>? onChanged;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final enabled = onChanged != null;
    final duration = KitMotion.reduced(context)
        ? Duration.zero
        : KitMotion.quick;
    final ink = enabled ? roles.text1 : roles.text3;
    return MergeSemantics(
      child: Semantics(
        checked: selected,
        child: KitTappable(
          label: label,
          onTap: enabled ? () => onChanged!(!selected) : null,
          disabledReason: enabled ? null : disabledReason,
          shape: KitShape.button,
          child: SizedBox.square(
            dimension: tokens.minTarget,
            child: Center(
              child: ExcludeSemantics(
                child: AnimatedContainer(
                  width: tokens.smallIconSize,
                  height: tokens.smallIconSize,
                  duration: duration,
                  curve: KitMotion.enter,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(
                      KitTokens.choiceMarkRadius,
                    ),
                    color: selected ? ink : null,
                    border: Border.all(
                      color: ink,
                      width: KitTokens.hairlineWidth(context),
                    ),
                  ),
                  child: AnimatedScale(
                    scale: selected ? 1 : 0,
                    duration: duration,
                    curve: KitMotion.enter,
                    child: Icon(
                      AppIconography.check,
                      size: tokens.smallIconSize - tokens.space1,
                      color: roles.surface1,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

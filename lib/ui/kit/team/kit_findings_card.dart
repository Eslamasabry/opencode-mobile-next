import 'package:flutter/material.dart';
import '../kit_buttons.dart';
import '../kit_chip.dart';
import '../kit_max_width.dart';
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
                SizedBox(
                  width: t.minTarget,
                  height: t.minTarget,
                  child: Checkbox(
                    value: finding.selected,
                    activeColor: t.roles.text1,
                    checkColor: t.roles.surface1,
                    onChanged:
                        state == KitTeamState.stale ||
                            state == KitTeamState.loading ||
                            finding.onChanged == null
                        ? null
                        : (value) => finding.onChanged!(value!),
                    semanticLabel: '${finding.severityLabel}: ${finding.title}',
                  ),
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

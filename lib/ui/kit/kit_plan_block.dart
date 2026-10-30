// KitPlanBlock (docs/ux-system/kit-api/KitPlanBlock.md): one plan of a
// group of plans: its name with a small word after it, one small summary at
// the end, and the lines of what it holds under them.
import 'package:flutter/material.dart';

import 'kit_max_width.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// One block of a plan group (Usage › Plans): [title] ("Codex") with a small
/// [titleNote] after it ("Plus"), one small [summary] at the end ("12% left",
/// "Back at 1:25 PM"; plain secondary text, never a pill) and [children]
/// under them: a `KitWindowRow` per limit window and any quiet note. The
/// whole block opens the plan. Put blocks in a `KitRowGroup`: it draws the
/// one card and the hairlines between blocks.
///
/// The title and the summary share the first line and wrap rather than cut:
/// from 1.3x text the summary drops under the title.
///
/// States: none — each window row and note it holds carries its own state.
class KitPlanBlock extends StatelessWidget {
  const KitPlanBlock({
    super.key,
    required this.title,
    required this.onTap,
    this.titleNote,
    this.summary,
    this.summaryKey,
    this.children = const [],
  });

  final String title;

  /// A small word after the title on the same line: the plan's tier.
  final String? titleNote;

  /// The one small summary at the end of the first line.
  final String? summary;
  final Key? summaryKey;

  /// What the plan holds, drawn under the first line.
  final List<Widget> children;

  /// Opens the plan.
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.conversation(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final note = titleNote;
    final summary = this.summary;
    final name = Text.rich(
      TextSpan(
        text: title,
        children: [
          if (note != null)
            TextSpan(text: ' $note', style: tokens.rowSupporting),
        ],
      ),
      style: tokens.rowTitle,
    );
    final first = summary == null
        ? name
        : Wrap(
            alignment: WrapAlignment.spaceBetween,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: tokens.space3,
            children: [
              name,
              KitText(
                summary,
                key: summaryKey,
                role: KitTextRole.secondary,
                tone: KitTextTone.secondary,
              ),
            ],
          );
    return KitTappable(
      onTap: onTap,
      label: [title, ?note, ?summary].join(', '),
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: tokens.rowHeight),
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            tokens.space4,
            tokens.space3,
            tokens.space4,
            tokens.space2,
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            mainAxisSize: MainAxisSize.min,
            children: [first, ...children],
          ),
        ),
      ),
    );
  }
}

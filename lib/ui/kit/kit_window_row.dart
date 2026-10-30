// KitWindowRow (docs/ux-system/kit-api/KitWindowRow.md): one limit window:
// its name and the amount left with its reset on one line, a bar under them.
import 'package:flutter/material.dart';

import '../app_theme.dart';
import 'kit_max_width.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// How a [KitWindowRow] is coloured.
enum KitWindowTone {
  /// Plenty left: the accent role.
  normal,

  /// Past the person's alert level: a strong neutral bar and value (amber is
  /// kept for "needs you", LOOK-4).
  low,

  /// Nothing left: the bar is empty and the value is strong (never red,
  /// LOOK-5).
  out,

  /// Old or offline numbers: muted.
  calm,
}

/// One quota window: "5 hours ........ 18% left · 14:10" with a full-width
/// bar under it. The bar fills with the share LEFT, like a fuel gauge, so a
/// short bar always means little left. Every bar has the same width, so two
/// windows under a plan's name line up.
///
/// It draws no surface and takes no taps: the plan card around it opens.
/// Screen readers hear one label, "{title}, {valueLabel}".
///
/// States: none — it only shows the numbers it is given; the tone carries
/// normal, low, out and calm.
class KitWindowRow extends StatelessWidget {
  const KitWindowRow({
    super.key,
    required this.title,
    required this.left,
    required this.valueLabel,
    this.tone = KitWindowTone.normal,
    this.note,
    this.inline = false,
    this.valueSemantics,
  });

  /// "5 hours", "Week".
  final String title;

  /// Share left, 0..1 (clamped for drawing).
  final double left;

  /// "18% left · 14:10".
  final String valueLabel;

  final KitWindowTone tone;

  /// A small line under the bar ("Resets at Wed 1:59 AM, in 14 h"), or null.
  final String? note;

  /// Inside a row or a block that already has the gutter: no side padding,
  /// and at ordinary text sizes the name, the bar and the value share one
  /// line ("5 hours [bar] 12% · Thu"). From 1.3× text it stacks, so nothing
  /// is cut.
  final bool inline;

  /// What a screen reader says for [valueLabel] when it is short for the
  /// words ("12% left, resets Thu"); null reads [valueLabel].
  final String? valueSemantics;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final roles = AppTheme.rolesOf(Theme.of(context));
    final tokens = KitTokens.of(context);
    final fill = switch (tone) {
      KitWindowTone.normal => roles.accent,
      KitWindowTone.low => roles.text1,
      KitWindowTone.out => roles.text1,
      KitWindowTone.calm => roles.text3,
    };
    final valueTone = switch (tone) {
      KitWindowTone.low || KitWindowTone.out => KitTextTone.primary,
      _ => KitTextTone.secondary,
    };
    final stacked = MediaQuery.textScalerOf(context).scale(10) >= 13;
    final bar = SizedBox(
      height: KitTokens.progressBarHeight,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: roles.surface3,
          borderRadius: BorderRadius.circular(KitTokens.progressBarRadius),
        ),
        child: Align(
          alignment: AlignmentDirectional.centerStart,
          child: FractionallySizedBox(
            widthFactor: left.clamp(0.0, 1.0),
            heightFactor: 1,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: fill,
                borderRadius: BorderRadius.circular(
                  KitTokens.progressBarRadius,
                ),
              ),
            ),
          ),
        ),
      ),
    );
    final stackedColumn = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        // Name and value share one line and wrap rather than cut.
        Wrap(
          alignment: WrapAlignment.spaceBetween,
          spacing: tokens.space2,
          children: [
            KitText(title, role: KitTextRole.secondary),
            KitText(
              valueLabel,
              role: KitTextRole.secondary,
              tone: valueTone,
              tabular: true,
            ),
          ],
        ),
        SizedBox(height: tokens.space1),
        bar,
        if (note case final note?) ...[
          SizedBox(height: tokens.space1),
          KitText(note, role: KitTextRole.caption, tone: KitTextTone.secondary),
        ],
      ],
    );
    Widget content = stackedColumn;
    if (inline && !stacked) {
      // One line only when the name, a bar of some length and the value all
      // fit; else the stacked layout, so nothing is cut or overflows.
      final nameWidth = MediaQuery.textScalerOf(context).scale(64);
      content = LayoutBuilder(
        builder: (context, constraints) {
          final valueWidth = _width(context, valueLabel);
          final fits =
              nameWidth + tokens.space3 + valueWidth + 48 <=
              constraints.maxWidth;
          if (!fits) return stackedColumn;
          return Row(
            children: [
              SizedBox(
                width: nameWidth,
                child: KitText(title, role: KitTextRole.secondary),
              ),
              Expanded(child: bar),
              SizedBox(width: tokens.space3),
              KitText(
                valueLabel,
                role: KitTextRole.secondary,
                tone: valueTone,
                tabular: true,
              ),
            ],
          );
        },
      );
    }
    return Semantics(
      container: true,
      label: note == null
          ? '$title, ${valueSemantics ?? valueLabel}'
          : '$title, ${valueSemantics ?? valueLabel}, $note',
      child: ExcludeSemantics(
        child: Padding(
          padding: inline
              ? EdgeInsetsDirectional.symmetric(vertical: tokens.space1)
              : EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                  vertical: tokens.space1,
                ),
          child: content,
        ),
      ),
    );
  }

  /// The width [text] takes on one line at the secondary role.
  static double _width(BuildContext context, String text) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: KitText.styleOf(context, KitTextRole.secondary),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final width = painter.width;
    painter.dispose();
    return width;
  }
}

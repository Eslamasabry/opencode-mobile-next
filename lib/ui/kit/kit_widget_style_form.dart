import 'package:flutter/material.dart';

import '../../l10n/app_localizations.dart';
import 'kit_max_width.dart';
import 'kit_section_label.dart';
import 'kit_segmented.dart';
import 'kit_swatch.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'kit_widget_preview.dart';

/// The choices that style one home-screen widget: a drawing of the result at
/// a chosen size, then the ground (solid, tinted, see-through) and the
/// accent. It owns no state: the host keeps the choices and hands them back.
///
/// [locked] holds every choice still while the style is being saved. The
/// accent names are shown as written by [accentName]; the colours come from
/// [accentColor] (the launcher's own table, or the app's accent for the two
/// choices that follow the phone or the app).
///
/// States: none — a form of choices that only holds still while saving.
class KitWidgetStyleForm extends StatelessWidget {
  const KitWidgetStyleForm({
    super.key,
    required this.previewSize,
    required this.background,
    required this.accent,
    required this.accentIds,
    required this.accentName,
    required this.accentColor,
    required this.onPreviewSize,
    required this.onBackground,
    required this.onAccent,
    this.locked = false,
  });

  /// `compact`, `card` or `list`.
  final String previewSize;

  /// `solid`, `tinted` or `transparent`.
  final String background;
  final String accent;
  final List<String> accentIds;
  final String Function(String id) accentName;
  final Color Function(String id) accentColor;
  final ValueChanged<String> onPreviewSize;
  final ValueChanged<String> onBackground;
  final ValueChanged<String> onAccent;
  final bool locked;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    final tokens = KitTokens.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitWidgetPreview(
          size: switch (previewSize) {
            'compact' => KitWidgetPreviewSize.compact,
            'list' => KitWidgetPreviewSize.list,
            _ => KitWidgetPreviewSize.card,
          },
          background: switch (background) {
            'tinted' => KitWidgetPreviewBackground.tinted,
            'transparent' => KitWidgetPreviewBackground.transparent,
            _ => KitWidgetPreviewBackground.solid,
          },
          accent: accentColor(accent),
        ),
        SizedBox(height: tokens.space3),
        KitSegmented<String>(
          semanticsLabel: l10n.widgetV2SizeLabel,
          selected: previewSize,
          onChanged: onPreviewSize,
          segments: [
            KitSegment(value: 'compact', label: l10n.widgetV2SizeCompact),
            KitSegment(value: 'card', label: l10n.widgetV2SizeCard),
            KitSegment(value: 'list', label: l10n.widgetV2SizeList),
          ],
        ),
        KitSectionLabel.inline(
          l10n.widgetV2BackgroundLabel,
          gapBefore: tokens.space4,
        ),
        KitSegmented<String>(
          semanticsLabel: l10n.widgetV2BackgroundLabel,
          selected: background,
          onChanged: locked ? null : onBackground,
          disabledReason: locked ? l10n.widgetV2Sending : null,
          segments: [
            KitSegment(value: 'solid', label: l10n.widgetV2BackgroundSolid),
            KitSegment(value: 'tinted', label: l10n.widgetV2BackgroundTinted),
            KitSegment(
              value: 'transparent',
              label: l10n.widgetV2BackgroundTransparent,
            ),
          ],
        ),
        KitSectionLabel.inline(
          l10n.widgetV2AccentLabel,
          gapBefore: tokens.space4,
        ),
        KitSwatchGrid(
          label: l10n.widgetV2AccentLabel,
          children: [
            for (final id in accentIds)
              KitSwatch.accent(
                swatchKey: ValueKey('widget-accent-$id'),
                colorsAnotherSurface: true,
                color: accentColor(id),
                label: accentName(id),
                selected: accent == id,
                onPressed: locked ? null : () => onAccent(id),
                disabledReason: locked ? l10n.widgetV2Sending : null,
              ),
          ],
        ),
        Padding(
          padding: EdgeInsets.only(top: tokens.space2),
          child: KitText(
            accentName(accent),
            role: KitTextRole.caption,
            tone: KitTextTone.secondary,
          ),
        ),
      ],
    );
  }
}

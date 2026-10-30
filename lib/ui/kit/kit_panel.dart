import 'package:flutter/material.dart';

import '../app_theme.dart';
import 'kit_max_width.dart';
import 'kit_surface.dart';
import 'kit_tokens.dart';

/// A block of content the person works with (design standard §3: "cards
/// are for content the person works with, not for wrapping a message"):
/// one `surface1` panel, 18 dp corners, no border and no shadow (surface
/// steps carry depth, visual language §4), 16 dp inside. It replaces the
/// hand-drawn containers of 10, 12, 14 and 20 dp radius that each screen
/// used to draw its own way.
///
/// Retired by kit-KitSurface (docs/ux-system/kit-api/KitSurface.md, KIT-43):
/// with [tone] neutral this is `KitSurface.panel`'s look, and with the
/// default [padding] and no [onTap] it forwards to `KitSurface.panel`
/// directly. A [tone] other than neutral keeps today's tinted-border,
/// tonal-wash look for its existing callers (team `agent_screen`,
/// `merge_section`, `start_run_sheet` and `team_board_card`) so they do not
/// change silently — the attention look belongs only to `KitNeedsYou`,
/// `KitRequestCard` and `KitNotice.card` (LOOK-24). New code names one of
/// those, or `KitSurface.panel` for a plain grouped panel, instead of a
/// tinted `KitPanel`.
///
/// States: none — a panel only draws its own fill, border and header.
class KitPanel extends StatelessWidget {
  const KitPanel({
    super.key,
    required this.child,
    this.tone = AppStatusTone.neutral,
    this.icon,
    this.title,
    this.titleKey,
    this.padding = _defaultPadding,
    this.onTap,
  });

  static const EdgeInsets _defaultPadding = EdgeInsets.all(
    KitTokens.panelPadding,
  );

  final Widget child;

  /// Retired by kit-KitSurface: attention look is KitNeedsYou/KitRequestCard (LOOK-24)
  final AppStatusTone tone;
  final IconData? icon;
  final String? title;
  final Key? titleKey;
  final EdgeInsetsGeometry padding;

  /// The whole panel opens something (a run, a sheet). Kept as today's
  /// `InkWell`; new interactive code wraps with `KitTappable` instead.
  final VoidCallback? onTap;

  /// The border colour for [tone]: none (transparent) when neutral, since
  /// surface steps carry depth (§4); `attentionLine` for needs you.
  static Color borderOf(ThemeData theme, AppStatusTone tone) {
    final roles = AppTheme.rolesOf(theme);
    return switch (tone) {
      AppStatusTone.neutral => Colors.transparent,
      AppStatusTone.attention => roles.attentionLine,
      _ => AppTheme.statusColor(theme, tone).withValues(alpha: .45),
    };
  }

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) => tone == AppStatusTone.neutral
      ? _buildNeutral(context)
      : _buildRetired(context);

  /// `tone: neutral` is `KitSurface.panel`'s look (KIT-43). The default
  /// padding and no [onTap] forward to it directly; a custom inset or an
  /// interactive panel still gets the panel fill and shape from
  /// `KitSurface` and the header from the shared `kitPanelBody`, so the
  /// caller's exact [padding] and `InkWell` still work (`KitSurface` itself
  /// takes neither).
  Widget _buildNeutral(BuildContext context) {
    if (onTap == null && padding == _defaultPadding) {
      return KitSurface.panel(
        title: title,
        icon: icon,
        titleKey: titleKey,
        child: child,
      );
    }
    final body = Padding(
      padding: padding,
      child: kitPanelBody(
        KitTokens.of(context),
        title: title,
        icon: icon,
        titleKey: titleKey,
        child: child,
      ),
    );
    // The InkWell sits outside the caller's padding, so the whole panel —
    // padding band included — takes the tap, and the ripple fills the panel
    // shape (KitSurface clips to it).
    return KitSurface(
      shape: KitShape.panel,
      padding: KitSurfacePadding.none,
      child: onTap == null ? body : InkWell(onTap: onTap, child: body),
    );
  }

  /// Retired: today's tinted-border, tonal-wash look, with two intended
  /// visual changes: the header icon (18 → 20, VL §7) and the border, now a
  /// hairline of exactly one physical pixel (`KitTokens.hairlineWidth`,
  /// LOOK-21) instead of the old one logical pixel (about 3× thinner at
  /// DPR 3).
  Widget _buildRetired(BuildContext context) {
    final theme = Theme.of(context);
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final attention = tone == AppStatusTone.attention;
    final tint = AppTheme.statusColor(theme, tone);
    final base = tokens.shapeOf(attention ? KitShape.card : KitShape.panel);
    final shape = base is OutlinedBorder
        ? base.copyWith(
            side: BorderSide(
              color: borderOf(theme, tone),
              width: KitTokens.hairlineWidth(context),
            ),
          )
        : base;
    final title = this.title;
    final icon = this.icon;
    final content = Padding(
      padding: padding,
      child: title == null && icon == null
          ? child
          : Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (icon != null) ...[
                      Icon(icon, size: tokens.smallIconSize, color: tint),
                      SizedBox(width: tokens.labelGap),
                    ],
                    if (title != null)
                      Expanded(
                        child: Text(
                          title,
                          key: titleKey,
                          style: tokens.cardTitle.copyWith(color: tint),
                        ),
                      ),
                  ],
                ),
                SizedBox(height: tokens.labelGap),
                child,
              ],
            ),
    );
    final fill = Color.alphaBlend(
      attention ? roles.attentionSurface : tint.withValues(alpha: .06),
      roles.surface1,
    );
    return kitSurfaceMaterial(
      context,
      level: KitSurfaceLevel.surface1,
      fill: fill,
      // The tone's wash stays under every style.
      color: fill,
      shape: shape,
      child: onTap == null ? content : InkWell(onTap: onTap, child: content),
    );
  }
}

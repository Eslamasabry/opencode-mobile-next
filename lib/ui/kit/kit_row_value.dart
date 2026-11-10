// KitRowValue: a row's trailing value (and its chevron). Split from
// kit_row.dart to keep that file under the size limit; kit_row.dart exports
// it.
import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_max_width.dart';
import 'kit_optical.dart';
import 'kit_row.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

/// A row's trailing value in `text3`, optionally before the chevron
/// (visual language §5: "Claude Sonnet 4 ›"). The value is what the row
/// is set to now; the chevron says the row opens a screen.
///
/// States: none — the row it sits in carries the states.
class KitRowValue extends StatelessWidget {
  const KitRowValue(
    this.value, {
    super.key,
    this.chevron = true,
    this.prominent = false,
  }) : count = null;

  /// A count badge instead of a value: the number in a small pill whose
  /// digits take the strong neutral ("3" errors kept), "99+" above 99.
  /// [value] is what it means in words ("3 errors kept"): the badge's
  /// semantics, so the number is never colour alone (STATE-9). A count
  /// of 0 or less shows only the chevron.
  const KitRowValue.count(
    int this.count,
    this.value, {
    super.key,
    this.chevron = true,
  }) : prominent = false;

  final String value;
  final bool chevron;
  final int? count;

  /// The value in body size and the secondary tone instead of the small
  /// quiet one: for a short group whose values are the point (a new
  /// conversation's Model and Project).
  final bool prominent;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.chip(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final count = this.count;
    final rail = KitRow.trailingRailOf(context) ?? tokens.space4;
    final chevronSize = tokens.iconSize(context, tokens.smallIconSize);
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (count == null)
            // A new value cross-fades (KitMotion.quick) rather than popping.
            Flexible(
              child: KitSwap(
                alignment: AlignmentDirectional.centerEnd,
                child: Text(
                  value,
                  key: ValueKey<String>(value),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: prominent
                      ? KitText.styleOf(
                          context,
                          KitTextRole.body,
                          tone: KitTextTone.secondary,
                        )
                      : tokens.rowValue,
                ),
              ),
            )
          else
            // The badge fades in, changes number and fades out with its count.
            KitSwap(
              alignment: AlignmentDirectional.centerEnd,
              child: count > 0
                  ? Semantics(
                      key: ValueKey<int>(count),
                      label: value,
                      excludeSemantics: true,
                      child: _KitCountPill(count > 99 ? '99+' : '$count'),
                    )
                  : const SizedBox.shrink(key: ValueKey<int>(0)),
            ),
          // The chevron's ink, or the value's last letter, ends on the row's
          // end rail, where a switch and a lone chevron end (KitOptical).
          if (chevron)
            Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.space1,
                end: KitOptical.endPad(
                  context,
                  AppIconography.chevronRight,
                  chevronSize,
                  rail,
                ),
              ),
              child: Icon(
                AppIconography.chevronRight,
                size: chevronSize,
                color: tokens.roles.text3,
              ),
            )
          else
            SizedBox(width: rail),
        ],
      ),
    );
  }
}

/// [KitRowValue.count]'s pill: at least `KitTokens.badgeHeight` /
/// `badgeMinWidth` and growing with its clamped text (A11Y-8,
/// `KitTokens.badgeTextScaleMax`), `surface2` with the digits in `text1`.
class _KitCountPill extends StatelessWidget {
  const _KitCountPill(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final scaler = MediaQuery.textScalerOf(
      context,
    ).clamp(maxScaleFactor: KitTokens.badgeTextScaleMax);
    return Container(
      constraints: const BoxConstraints(
        minWidth: KitTokens.badgeMinWidth,
        minHeight: KitTokens.badgeHeight,
      ),
      padding: EdgeInsets.symmetric(horizontal: tokens.space1),
      alignment: Alignment.center,
      decoration: KitSurfaceLook.decorate(
        context,
        solid: ShapeDecoration(
          color: tokens.roles.surface2,
          shape: tokens.shapeOf(KitShape.pill),
        ),
        role: KitSurfaceRole.control,
        level: KitSurfaceLevel.surface2,
      ),
      child: Text(
        text,
        textScaler: scaler,
        style: KitText.styleOf(context, KitTextRole.caption).copyWith(
          color: KitSurfaceLook.wordsOn(
            context,
            fallback: tokens.roles.text1,
            level: KitSurfaceLevel.surface2,
          ),
          fontWeight: FontWeight.w600,
          fontFeatures: const [FontFeature.tabularFigures()],
        ),
      ),
    );
  }
}

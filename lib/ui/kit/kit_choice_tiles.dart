import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_choice_list.dart' show KitChoice;
import 'kit_divider.dart';
import 'kit_icon.dart' show KitIconSize;
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_reach.dart';
import 'kit_surface_look.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// A few big choices that lead with the reason ("Default", "Cheapest"),
/// each its own tile, with one quiet "All models (42)" link under them
/// (docs/ux-system/kit-api/KitChoiceTiles.md; the vision rule "choices lead
/// with the reason").
///
/// A tap chooses and the host closes its sheet: there is no Apply. The
/// chosen tile has a check and the style's chosen control (Solid: an accent
/// ring; never a solid fill);
/// while the host applies a tap, [busy] shows a small progress mark where
/// the check goes and every tile waits.
///
/// States: disabled, working.
///
/// Disabled: the reason first on the supporting line. Working: [busy] is
/// applied. Titles and supporting lines wrap (200 % text), never truncate.
class KitChoiceTiles<T> extends StatelessWidget {
  const KitChoiceTiles({
    super.key,
    required this.choices,
    required this.selected,
    required this.onSelected,
    this.busy,
    this.more,
    this.semanticsLabel,
  });

  final List<KitChoice<T>> choices;

  /// The value in use; its tile has the check.
  final T? selected;
  final ValueChanged<T> onSelected;

  /// The value being applied: its tile shows progress, the others wait.
  final T? busy;

  /// The quiet last line that opens everything else.
  final KitChoiceMore? more;

  /// The group's name.
  final String? semanticsLabel;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.reading(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final waiting = busy != null;
    // Flat lists (Solid): one row per choice on the sheet itself, parted by
    // inset hairlines, the chosen one with the accent check.
    final flat = KitSurfaceLook.flatLists(context);
    // In a sheet the rows run out over its rails ([KitReach]): their words
    // and hairlines start on the title's line.
    final reach = flat ? math.min(tokens.space4, KitReach.of(context)) : 0.0;
    final words = reach > 0 ? reach : _wordsStart(tokens);
    final rule = Padding(
      padding: EdgeInsetsDirectional.only(start: words),
      child: KitDivider(endInset: reach),
    );
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: semanticsLabel,
      child: KitReach.extend(
        by: reach,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            for (var i = 0; i < choices.length; i++) ...[
              if (i > 0) flat ? rule : SizedBox(height: tokens.space3),
              _KitChoiceTile<T>(
                key: choices[i].key,
                choice: choices[i],
                selected: choices[i].value == selected,
                busy: waiting && choices[i].value == busy,
                flat: flat,
                inset: words,
                onTap: !choices[i].enabled || waiting
                    ? null
                    : () => onSelected(choices[i].value),
              ),
            ],
            if (more case final more?) ...[
              if (flat && choices.isNotEmpty)
                rule
              else
                SizedBox(height: tokens.space1),
              _KitChoiceMoreLine(
                more: more,
                enabled: !waiting,
                words: flat ? words : _wordsStart(tokens),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Where a tile's words (and the "more" line's) start inside it.
double _wordsStart(KitTokens tokens) => tokens.space4 + tokens.space1;

/// The quiet line under [KitChoiceTiles]: "All models (42) ›".
@immutable
class KitChoiceMore {
  const KitChoiceMore({required this.label, required this.onTap, this.key});

  final String label;
  final VoidCallback onTap;
  final Key? key;
}

class _KitChoiceTile<T> extends StatelessWidget {
  const _KitChoiceTile({
    super.key,
    required this.choice,
    required this.selected,
    required this.busy,
    required this.onTap,
    required this.inset,
    this.flat = false,
  });

  /// Where a flat row's words start inside it.
  final double inset;

  final KitChoice<T> choice;
  final bool selected;
  final bool busy;
  final VoidCallback? onTap;

  /// A row on the sheet instead of a tile ([KitSurfaceLook.flatLists]).
  final bool flat;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduced = KitMotion.reduced(context);
    final duration = reduced ? Duration.zero : KitMotion.quick;
    final enabled = choice.enabled;
    final supporting = [
      if (!enabled) ?choice.disabledReason,
      if (choice.supporting case final text? when text.isNotEmpty) text,
    ].join(' · ');
    final radius = BorderRadius.circular(tokens.panelCornerRadius);
    // A tile is one step up from the sheet: a lighter fill in dark; in
    // light, where a step up is white on white, the near-white fill with a
    // hairline.
    final light = Theme.of(context).brightness == Brightness.light;
    final markSize = tokens.iconSize(context, tokens.smallIconSize);
    final mark = busy
        ? SizedBox.square(
            key: const ValueKey('kit-choice-tile-busy'),
            dimension: markSize,
            // The kit's small spinner; still under reduced motion (MOT-7).
            child: CircularProgressIndicator(
              strokeWidth: KitTokens.spinnerStroke,
              value: reduced ? .75 : null,
              color: roles.accent,
              backgroundColor: Colors.transparent,
            ),
          )
        : flat
        ? AnimatedScale(
            key: ValueKey('kit-choice-tile-mark-${selected ? 'on' : 'off'}'),
            scale: selected ? 1 : 0,
            duration: duration,
            curve: KitMotion.enter,
            // The platform's chosen-row mark: the check alone, in the
            // accent; the row's words say which is in use as well.
            child: Icon(
              AppIconography.check,
              size: tokens.iconSize(context, KitIconSize.large.logical),
              color: roles.accent,
            ),
          )
        : AnimatedScale(
            key: ValueKey('kit-choice-tile-mark-${selected ? 'on' : 'off'}'),
            scale: selected ? 1 : 0,
            duration: duration,
            curve: KitMotion.enter,
            // The check on an accent disc: the one solid accent on the
            // tile, so the choice reads at a glance and never by colour
            // alone (the hairline and the check both say it).
            child: Container(
              width: markSize + tokens.space1,
              height: markSize + tokens.space1,
              decoration: BoxDecoration(
                color: roles.accent,
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: Icon(
                AppIconography.check,
                size: markSize - tokens.space1,
                color: roles.onAccent,
              ),
            ),
          );
    final level = light ? KitSurfaceLevel.surface1 : KitSurfaceLevel.surface3;
    final words = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        _KitChosenText(
          choice.title,
          role: KitTextRole.headline,
          tone: enabled ? KitTextTone.primary : KitTextTone.tertiary,
          level: level,
          selected: selected,
        ),
        if (supporting.isNotEmpty) ...[
          SizedBox(height: tokens.space1),
          _KitChosenText(
            supporting,
            role: KitTextRole.body,
            tone: KitTextTone.secondary,
            level: level,
            selected: selected,
          ),
        ],
      ],
    );
    if (flat) {
      return MergeSemantics(
        child: Semantics(
          inMutuallyExclusiveGroup: true,
          child: KitTappable(
            onTap: onTap,
            disabledReason: choice.disabledReason ?? '',
            selected: selected,
            shape: KitShape.panel,
            surface: KitSurfaceLevel.surface2,
            child: ConstrainedBox(
              constraints: BoxConstraints(minHeight: tokens.rowHeightTwoLine),
              child: Padding(
                padding: EdgeInsetsDirectional.fromSTEB(
                  inset,
                  tokens.space3,
                  tokens.space4,
                  tokens.space3,
                ),
                child: Row(
                  children: [
                    Expanded(child: words),
                    SizedBox(width: tokens.space3),
                    ExcludeSemantics(child: mark),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
    }
    // A tile is a control on its sheet: Solid paints today's tile, every
    // other style its own chosen and resting control.
    final decoration = KitSurfaceLook.decorate(
      context,
      solid: BoxDecoration(
        color: light ? roles.surface1 : roles.surface3,
        borderRadius: radius,
        border: Border.all(
          color: selected
              ? roles.accent
              : light
              ? roles.hairline
              : roles.surface3,
          // The kit's selection ring; unselected tiles keep the same
          // width in their own colour, so choosing never shifts the words.
          width: KitTokens.focusRingWidth(context),
        ),
      ),
      role: KitSurfaceRole.control,
      level: level,
      borderRadius: radius,
      selected: selected,
    );
    return kitSurfaceBox(
      style: KitSurfaceLook.styleOf(context),
      decoration: decoration,
      // A drawn border takes its room inside the tile, as a container's
      // does; a style's edge sits over the face.
      child: Padding(
        padding: decoration.padding,
        child: ClipRRect(
          borderRadius: radius,
          child: MergeSemantics(
            child: Semantics(
              inMutuallyExclusiveGroup: true,
              child: KitTappable(
                onTap: onTap,
                disabledReason: choice.disabledReason ?? '',
                selected: selected,
                shape: KitShape.panel,
                surface: level,
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    minHeight: tokens.rowHeightTwoLine + tokens.space3,
                  ),
                  child: Padding(
                    padding: EdgeInsetsDirectional.fromSTEB(
                      tokens.space4 + tokens.space1,
                      tokens.space4,
                      tokens.space4,
                      tokens.space4,
                    ),
                    child: Row(
                      children: [
                        Expanded(child: words),
                        SizedBox(width: tokens.space3),
                        ExcludeSemantics(child: mark),
                      ],
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

/// A tile's words: on the chosen tile, in the colour the style says reads
/// on its chosen control (hook H3), so a style that sets it in reverse
/// (Paper's ink) keeps them legible; elsewhere the part's own [KitText].
/// Resolved inside the tile, so the words keep its plate (hook H10).
class _KitChosenText extends StatelessWidget {
  const _KitChosenText(
    this.text, {
    required this.role,
    required this.tone,
    required this.level,
    required this.selected,
  });

  final String text;
  final KitTextRole role;
  final KitTextTone tone;
  final KitSurfaceLevel level;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    if (!selected) return KitText(text, role: role, tone: tone);
    final own = KitText.styleOf(context, role, tone: tone);
    final ink = KitSurfaceLook.wordsOn(
      context,
      fallback: own.color!,
      role: KitSurfaceRole.control,
      level: level,
      selected: true,
    );
    return Text(text, style: own.copyWith(color: ink));
  }
}

class _KitChoiceMoreLine extends StatelessWidget {
  const _KitChoiceMoreLine({
    required this.more,
    required this.enabled,
    required this.words,
  });

  final KitChoiceMore more;
  final bool enabled;

  /// Where its words start inside it.
  final double words;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return KitTappable(
      key: more.key,
      onTap: enabled ? more.onTap : null,
      disabledReason: '',
      shape: KitShape.panel,
      surface: KitSurfaceLevel.surface2,
      child: ConstrainedBox(
        constraints: BoxConstraints(
          minHeight: tokens.minTarget + tokens.space2,
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.fromSTEB(
            words,
            tokens.space2,
            tokens.space4,
            tokens.space2,
          ),
          child: Row(
            children: [
              Expanded(
                child: KitText(
                  more.label,
                  role: KitTextRole.rowTitle,
                  tone: KitTextTone.secondary,
                ),
              ),
              Icon(
                AppIconography.chevronRight,
                size: tokens.smallIconSize,
                color: roles.text3,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

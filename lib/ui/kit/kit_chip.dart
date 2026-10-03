// KitChip: the one small rounded label (docs/ux-system/kit-api/KitChip.md,
// visual language §4-§5). Five kinds share one look: plain (a fact), action
// (a tap target or an on/off filter, replacing FilterChip), removable (a
// chosen thing with its own × target, replacing InputChip), count (a label
// with a number, "Tasks · 3") and summary (folded work that opens, "Read 3
// files · edited 1"). Replaces Chip, ActionChip, InputChip and FilterChip;
// ChoiceChip stays with KitSegmented (KIT-24).
//
// States: there is no disabled chip (STATE-8: a chip that cannot act now is
// not shown) and no loading, empty or error state (KIT-12: a chip shows a
// value its host already has). Declared states: selected (action) and
// expanded (summary).
//
// Layout: every kind is a 48 dp-high box (KitTokens.minTarget) with the
// 32 dp pill (KitTokens.chipHeight) centred in it, so chips of different
// kinds line up in a KitChipWrap run. The tap zones fill the box and paint
// nothing themselves: hover, press and keyboard focus are drawn by the pill
// (its fill turns surface2, its accent ring appears), under the words and
// inside the pill's own stadium.
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart' hide TextDirection;

import '../../l10n/app_localizations.dart';
import '../app_iconography.dart';
import '../theme_roles.dart' show readableOn;
import 'kit_motion.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// Semantic severity for worded chips; neutral preserves the existing look.
/// [active] is a worded "this is on" state: an accent-tinted pill with
/// accent-readable words and glyph (the composer's Auto-approve chip).
enum KitChipTone { neutral, attention, danger, active }

/// The five kinds a [KitChip] can be (KitChip.md "Purpose").
enum KitChipKind { plain, action, removable, count, summary }

/// A small rounded label (visual language §4-§5). Every kind carries a
/// word, never colour alone (STATE-9), and has a 48 dp hit area around a
/// visually smaller pill ([KitTokens.chipHeight], 32).
///
/// Internal keys (TEST-5): `kit-chip-remove` (the × target) and
/// `kit-chip-check` (the selected check).
///
/// States: none — selected and expanded are looks; a chip that cannot act
/// now is not shown (STATE-8).
class KitChip extends StatelessWidget {
  /// A fact that does nothing when tapped: "main", "3 agents".
  const KitChip({
    super.key,
    required this.label,
    this.icon,
    this.tone = KitChipTone.neutral,
  }) : kind = KitChipKind.plain,
       onPressed = null,
       onRemove = null,
       count = null,
       selected = null,
       expanded = null;

  /// A tap target ("Open in terminal"). With [selected] non-null it is an
  /// on/off filter (replaces FilterChip): a check at the start when on,
  /// toggled semantics. One choice among several is `KitSegmented`, not
  /// this (KIT-24).
  ///
  /// The start has one glyph slot: while [selected] is true the check
  /// takes [icon]'s place there, cross-fading on `KitMotion.quick`.
  const KitChip.action({
    super.key,
    required this.label,
    this.tone = KitChipTone.neutral,
    required VoidCallback this.onPressed,
    this.icon,
    this.selected,
  }) : kind = KitChipKind.action,
       onRemove = null,
       count = null,
       expanded = null;

  /// Something the person chose that can be taken out: an attachment, a
  /// form value (replaces InputChip). The × at the end is its own 48 dp
  /// target labelled `l10n.kitChipRemove(label)`.
  const KitChip.removable({
    super.key,
    required this.label,
    this.tone = KitChipTone.neutral,
    required VoidCallback this.onRemove,
    this.icon,
    this.onPressed,
  }) : kind = KitChipKind.removable,
       count = null,
       selected = null,
       expanded = null;

  /// A label with a number: "Tasks · 3". The number uses tabular figures
  /// and `intl` formatting for the locale; semantics "Tasks, 3".
  const KitChip.count({
    super.key,
    required this.label,
    this.tone = KitChipTone.neutral,
    required int this.count,
    this.onPressed,
    this.icon,
  }) : kind = KitChipKind.count,
       onRemove = null,
       selected = null,
       expanded = null;

  /// Folded work that opens: "Read 3 files · edited 1" (the transcript's
  /// work chip, visual language §5). [expanded] non-null adds a chevron
  /// (down folded, up open) and expanded semantics.
  const KitChip.summary({
    super.key,
    required this.label,
    this.tone = KitChipTone.neutral,
    required VoidCallback this.onPressed,
    this.icon,
    this.expanded,
  }) : kind = KitChipKind.summary,
       onRemove = null,
       count = null,
       selected = null;

  final KitChipKind kind;
  final KitChipTone tone;

  /// The chip's words, from the caller's ARB (COPY-1). A name the person or
  /// the server chose is wrapped by the caller with `KitBidi.auto` (COPY-30).
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  final VoidCallback? onRemove;
  final int? count;
  final bool? selected;
  final bool? expanded;

  static const _removeKey = ValueKey('kit-chip-remove');
  static const _checkKey = ValueKey('kit-chip-check');

  /// Test-only (TEST-5): the removable chip's body tap zone is a sibling of
  /// its visible label, not an ancestor of it (the label stays in the pill
  /// so the × can sit beside it in one continuous surface), so a test needs
  /// a handle of its own to find it and check its focus.
  static const _bodyKey = ValueKey('kit-chip-body');

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final reduceMotion = KitMotion.reduced(context);
    // The label's type: KitTextRole.secondary (14/20) in KitTextTone.primary
    // (text1) where the States table calls for emphasis (action, removable,
    // summary and a count's number), KitTextTone.secondary (text2) otherwise
    // (plain, and a count's own label).
    final semanticColor = switch (tone) {
      KitChipTone.neutral => roles.text2,
      KitChipTone.attention => roles.attention,
      KitChipTone.danger => roles.danger,
      KitChipTone.active => roles.accent,
    };
    // An accent chip sits on an accent-tinted pill instead of the neutral
    // one; its words are made readable on that tint.
    final tint = tone == KitChipTone.active
        ? Color.alphaBlend(roles.accent.withValues(alpha: .22), roles.surface3)
        : null;
    final toneColor = tone == KitChipTone.neutral
        ? semanticColor
        : readableOn(
            semanticColor,
            tint != null ? [tint] : [roles.surface2, roles.surface3],
            4.7,
            toward: roles.text1,
          );
    final emphasized = KitText.styleOf(
      context,
      KitTextRole.secondary,
      tone: KitTextTone.primary,
    ).copyWith(color: tone == KitChipTone.neutral ? roles.text1 : toneColor);
    final quiet = KitText.styleOf(
      context,
      KitTextRole.secondary,
      tone: KitTextTone.secondary,
    );
    // A glyph and the gap after it (start) or before it (end).
    final glyphExtent = tokens.smallIconSize + tokens.space1;
    Widget startGlyph(IconData data, Color color, {Key? key}) => Padding(
      padding: EdgeInsetsDirectional.only(end: tokens.space1),
      child: Icon(data, key: key, size: tokens.smallIconSize, color: color),
    );
    Widget endGlyph(Widget glyph) => Padding(
      padding: EdgeInsetsDirectional.only(start: tokens.space1),
      child: glyph,
    );

    switch (kind) {
      case KitChipKind.plain:
        return _ChipFrame(
          tokens: tokens,
          tint: tint,
          span: TextSpan(
            text: label,
            style: quiet.copyWith(color: toneColor),
          ),
          textSemantics: label,
          leading: icon == null ? null : startGlyph(icon!, toneColor),
          leadingExtent: icon == null ? 0 : glyphExtent,
        );

      case KitChipKind.action:
        final selected = this.selected;
        final icon = this.icon;
        // One slot that always exists, so turning selection on or off
        // cross-fades (an AnimatedSwitcher never animates its first child).
        final Widget slotChild;
        if (selected == true) {
          slotChild = KeyedSubtree(
            key: const ValueKey('check'),
            child: startGlyph(
              AppIconography.check,
              roles.accent,
              key: _checkKey,
            ),
          );
        } else if (icon != null) {
          slotChild = KeyedSubtree(
            key: const ValueKey('icon'),
            child: startGlyph(
              icon,
              tone == KitChipTone.neutral ? roles.text1 : toneColor,
            ),
          );
        } else {
          slotChild = const SizedBox.shrink(key: ValueKey('none'));
        }
        return _ChipFrame(
          tokens: tokens,
          tint: tint,
          span: TextSpan(text: label, style: emphasized),
          textSemantics: label,
          leading: AnimatedSwitcher(
            duration: reduceMotion ? Duration.zero : KitMotion.quick,
            transitionBuilder: (child, animation) =>
                FadeTransition(opacity: animation, child: child),
            child: slotChild,
          ),
          leadingExtent: selected == true || icon != null ? glyphExtent : 0,
          onPressed: onPressed,
          semanticsLabel: label,
          toggled: selected,
        );

      case KitChipKind.summary:
        final expanded = this.expanded;
        return _ChipFrame(
          tokens: tokens,
          tint: tint,
          span: TextSpan(text: label, style: emphasized),
          textSemantics: label,
          leading: icon == null
              ? null
              : startGlyph(
                  icon!,
                  tone == KitChipTone.neutral ? roles.text1 : toneColor,
                ),
          leadingExtent: icon == null ? 0 : glyphExtent,
          trailing: expanded == null
              ? null
              : endGlyph(
                  AnimatedRotation(
                    turns: expanded ? .5 : 0,
                    duration: reduceMotion ? Duration.zero : KitMotion.standard,
                    curve: KitMotion.emphasized,
                    child: Icon(
                      AppIconography.chevronDown,
                      size: tokens.smallIconSize,
                      color: roles.text1,
                      textDirection: TextDirection.ltr,
                    ),
                  ),
                ),
          trailingExtent: expanded == null ? 0 : glyphExtent,
          onPressed: onPressed,
          semanticsLabel: label,
          expanded: expanded,
        );

      case KitChipKind.count:
        final formatted = NumberFormat.decimalPattern(
          Localizations.localeOf(context).toLanguageTag(),
        ).format(count!);
        return _ChipFrame(
          tokens: tokens,
          tint: tint,
          span: TextSpan(
            style: quiet,
            children: [
              TextSpan(text: label),
              const TextSpan(text: ' · '),
              TextSpan(
                text: formatted,
                style: emphasized.copyWith(
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
            ],
          ),
          leading: icon == null ? null : startGlyph(icon!, toneColor),
          leadingExtent: icon == null ? 0 : glyphExtent,
          onPressed: onPressed,
          semanticsLabel: '$label, $formatted',
        );

      case KitChipKind.removable:
        final l10n = lookupAppLocalizations(Localizations.localeOf(context));
        return _ChipFrame(
          tokens: tokens,
          tint: tint,
          span: TextSpan(text: label, style: emphasized),
          textSemantics: label,
          leading: icon == null
              ? null
              : startGlyph(
                  icon!,
                  tone == KitChipTone.neutral ? roles.text1 : toneColor,
                ),
          leadingExtent: icon == null ? 0 : glyphExtent,
          trailing: endGlyph(
            Icon(
              AppIconography.close,
              size: tokens.smallIconSize,
              color: roles.text2,
            ),
          ),
          trailingExtent: glyphExtent,
          onPressed: onPressed,
          semanticsLabel: label,
          onRemove: onRemove,
          removeLabel: l10n.kitChipRemove(label),
        );
    }
  }
}

/// The tap zones a chip can have: its body, and a removable chip's ×.
enum _Zone { body, remove }

/// Every kind's frame: the 48 dp box, the pill centred in it, the tap
/// zone(s) over the box, the truncation tooltip over the zone it belongs
/// to, and the pill's hover, press and focus look (KitChip.md "States").
class _ChipFrame extends StatefulWidget {
  const _ChipFrame({
    required this.tokens,
    required this.span,
    this.tint,
    this.textSemantics,
    this.leading,
    this.leadingExtent = 0,
    this.trailing,
    this.trailingExtent = 0,
    this.onPressed,
    this.semanticsLabel,
    this.toggled,
    this.expanded,
    this.onRemove,
    this.removeLabel,
  });

  final KitTokens tokens;

  /// The accent tint of an accent chip's pill; null keeps the neutral fill.
  final Color? tint;

  /// The words, drawn on one line with an ellipsis when they do not fit.
  final InlineSpan span;

  /// The visible text's own semantics label; null keeps [span]'s text.
  final String? textSemantics;

  /// The start glyph with its gap, and its width (for the truncation test).
  final Widget? leading;
  final double leadingExtent;

  /// The end glyph with its gap, and its width.
  final Widget? trailing;
  final double trailingExtent;

  /// The body's tap; null makes the body static.
  final VoidCallback? onPressed;

  /// The chip's announced name (the body button's, or a static count's).
  /// Null keeps the visible text's own semantics (plain).
  final String? semanticsLabel;
  final bool? toggled;
  final bool? expanded;

  /// The ×'s tap and name; null means the chip has no ×.
  final VoidCallback? onRemove;
  final String? removeLabel;

  @override
  State<_ChipFrame> createState() => _ChipFrameState();
}

class _ChipFrameState extends State<_ChipFrame> {
  final _hovered = <_Zone>{};
  final _focused = <_Zone>{};
  // Each zone's press shows on the next frame of a touch (KitPressTracker),
  // not after the InkWell's tap-or-scroll wait.
  late final _press = {
    for (final zone in _Zone.values)
      zone: KitPressTracker(() {
        if (mounted) setState(() {});
      }),
  };

  @override
  void dispose() {
    for (final press in _press.values) {
      press.dispose();
    }
    super.dispose();
  }

  void _mark(Set<_Zone> set, _Zone zone, bool on) {
    final changed = on ? set.add(zone) : set.remove(zone);
    if (changed) setState(() {});
  }

  /// Whether [widget.span] ellipsises at this width: the same sum the Row
  /// inside the pill makes (the pill's padding, the glyphs, then the label).
  bool _truncated(BuildContext context, BoxConstraints constraints) {
    if (!constraints.hasBoundedWidth) return false;
    final tokens = widget.tokens;
    final available =
        constraints.maxWidth -
        2 * tokens.space3 -
        widget.leadingExtent -
        widget.trailingExtent;
    if (available <= 0) return true;
    final painter = TextPainter(
      text: widget.span,
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout(maxWidth: available);
    final truncated = painter.didExceedMaxLines;
    painter.dispose();
    return truncated;
  }

  /// A tap zone: the whole area it is given, painting nothing itself (no
  /// ink over the words, no spread past the pill); it tells the pill when
  /// it is hovered, pressed or focused.
  Widget _zone(_Zone zone, VoidCallback onTap) => _press[zone]!.listen(
    context: context,
    enabled: true,
    child: Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: () {
          _press[zone]!.confirm();
          onTap();
        },
        onTapCancel: _press[zone]!.cancel,
        onHover: (on) => _mark(_hovered, zone, on),
        onFocusChange: (on) => _mark(_focused, zone, on),
        splashFactory: NoSplash.splashFactory,
        overlayColor: const WidgetStatePropertyAll(Colors.transparent),
        child: const SizedBox.expand(),
      ),
    ),
  );

  /// The full label or the ×'s name; no haptic on long-press (MOT-11).
  Widget _tooltip(String message, Widget child) => Tooltip(
    message: message,
    excludeFromSemantics: true,
    enableFeedback: false,
    child: child,
  );

  @override
  Widget build(BuildContext context) {
    final tokens = widget.tokens;
    final roles = tokens.roles;
    final onPressed = widget.onPressed;
    final onRemove = widget.onRemove;
    final interactive = onPressed != null || onRemove != null;
    final fullText = widget.span.toPlainText();

    return LayoutBuilder(
      builder: (context, constraints) {
        final truncated = _truncated(context, constraints);
        Widget row = Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            ?widget.leading,
            Flexible(
              child: Text.rich(
                widget.span,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                softWrap: false,
                semanticsLabel: widget.textSemantics,
              ),
            ),
            ?widget.trailing,
          ],
        );
        // A body that is a button (or a static count, which is announced
        // as "{label}, {count}") names itself; the visible text then says
        // nothing of its own. A removable chip with no body tap keeps the
        // text's own semantics beside its × button.
        final named = onRemove == null && widget.semanticsLabel != null;
        if (onPressed != null || named) {
          row = ExcludeSemantics(child: row);
        }
        final pill = _PillSurface(
          tokens: tokens,
          fill:
              widget.tint ??
              (_hovered.isNotEmpty || _press.values.any((p) => p.shown)
                  ? roles.surface2
                  : roles.surface3),
          focused: _focused.isNotEmpty,
          child: row,
        );

        final children = <Widget>[pill];
        if (onRemove != null) {
          // The body zone runs from the start to the ×'s 48 dp zone.
          Widget? body = onPressed == null
              ? null
              : Semantics(
                  key: KitChip._bodyKey,
                  button: true,
                  label: widget.semanticsLabel,
                  container: true,
                  excludeSemantics: true,
                  onTap: onPressed,
                  child: _zone(_Zone.body, onPressed),
                );
          if (truncated) {
            body = _tooltip(fullText, body ?? const SizedBox.expand());
          }
          if (body != null) {
            children.add(
              PositionedDirectional(
                start: 0,
                top: 0,
                bottom: 0,
                end: tokens.minTarget,
                child: body,
              ),
            );
          }
          children.add(
            PositionedDirectional(
              end: 0,
              top: 0,
              bottom: 0,
              width: tokens.minTarget,
              child: Semantics(
                key: KitChip._removeKey,
                button: true,
                label: widget.removeLabel,
                container: true,
                excludeSemantics: true,
                onTap: onRemove,
                child: _tooltip(
                  widget.removeLabel!,
                  _zone(_Zone.remove, onRemove),
                ),
              ),
            ),
          );
        } else if (onPressed != null) {
          children.add(
            PositionedDirectional(
              start: 0,
              end: 0,
              top: 0,
              bottom: 0,
              child: _zone(_Zone.body, onPressed),
            ),
          );
        }

        Widget frame = ConstrainedBox(
          constraints: BoxConstraints(
            minWidth: interactive ? tokens.minTarget : 0,
            minHeight: tokens.minTarget,
          ),
          child: Stack(
            alignment: AlignmentDirectional.center,
            children: children,
          ),
        );
        // The full label on long-press (touch) or hover (a fine pointer),
        // only when it is cut (KitChip.md "Adaptive"). It sits over the tap
        // zone, not under it, so the gesture reaches it.
        if (truncated && onRemove == null) frame = _tooltip(fullText, frame);

        if (onRemove != null) {
          // Delete/Backspace on either focused zone removes the chip
          // (KitChip.md "Keyboard"). canRequestFocus: false keeps this out
          // of Tab order; the event still bubbles here from either zone.
          return Focus(
            canRequestFocus: false,
            onKeyEvent: (node, event) {
              if (event is! KeyDownEvent) return KeyEventResult.ignored;
              final key = event.logicalKey;
              if (key == LogicalKeyboardKey.delete ||
                  key == LogicalKeyboardKey.backspace) {
                onRemove();
                return KeyEventResult.handled;
              }
              return KeyEventResult.ignored;
            },
            child: frame,
          );
        }
        if (widget.semanticsLabel == null) return frame;
        return Semantics(
          button: onPressed != null,
          label: widget.semanticsLabel,
          toggled: widget.toggled,
          expanded: widget.expanded,
          onTap: onPressed,
          container: true,
          excludeSemantics: true,
          child: frame,
        );
      },
    );
  }
}

/// The pill (KitChip.md "States", "200 % text": "the pill grows in height
/// with the text (chipHeight is a minimum)"): a [KitShape.pill] stadium in
/// [fill], with [KitTokens.space3] inner horizontal padding, and, when
/// [focused], a 2-physical-pixel `accent` ring around it (LOOK-21).
class _PillSurface extends StatelessWidget {
  const _PillSurface({
    required this.tokens,
    required this.fill,
    required this.focused,
    required this.child,
  });

  final KitTokens tokens;
  final Color fill;
  final bool focused;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final shape = tokens.shapeOf(KitShape.pill);
    Widget pill = DecoratedBox(
      decoration: ShapeDecoration(color: fill, shape: shape),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: KitTokens.chipHeight),
        child: Padding(
          padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space3),
          child: child,
        ),
      ),
    );
    if (focused) {
      pill = DecoratedBox(
        position: DecorationPosition.foreground,
        decoration: ShapeDecoration(
          shape: StadiumBorder(
            side: BorderSide(
              color: tokens.roles.accent,
              width: KitTokens.focusRingWidth(context),
              strokeAlign: BorderSide.strokeAlignOutside,
            ),
          ),
        ),
        child: pill,
      );
    }
    return pill;
  }
}

/// Lays chips out in a wrapping row with the kit's spacing: [KitTokens.space2]
/// between chips, and a run spacing that keeps every chip's 48 dp hit area
/// clear of the next run's (LAY-9): [KitTokens.minTarget] minus
/// [KitTokens.chipHeight]. Start-aligned in both directions.
///
/// States: none — it only lays its chips out.
class KitChipWrap extends StatelessWidget {
  const KitChipWrap({super.key, required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Wrap(
      spacing: tokens.space2,
      runSpacing: tokens.minTarget - KitTokens.chipHeight,
      alignment: WrapAlignment.start,
      runAlignment: WrapAlignment.start,
      crossAxisAlignment: WrapCrossAlignment.start,
      children: children,
    );
  }
}

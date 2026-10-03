import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../app_iconography.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_divider.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_row_parts.dart' show KitRowMenu;
import 'kit_section_label.dart';
import 'kit_swipe_action.dart';
import 'kit_tappable.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

/// The one list row (design standard §6, visual language §5;
/// docs/ux-system/kit-api/KitRow.md): a leading icon tile, a title, a muted
/// supporting line and a trailing value, chevron or one icon action, on a
/// grouped `surface1` panel. State lives in the row (a tinted icon, a
/// "Needs you" word in [supporting]), not in cards above the list.
///
/// One line each is the rule. A list whose titles are the person's own words
/// (conversation titles) may let them wrap with [titleMaxLines] and
/// [supportingMaxLines], so large text does not cut them to a few letters.
/// From 1.3× text a title always gets at least two lines, and a supporting
/// line two (three from 2.0×) before its ellipsis (A11Y-8).
///
/// The tap surface is a [KitTappable]: a 48 dp minimum, hover and pressed
/// fills from the surface steps, a keyboard focus ring, Enter and Space.
/// A non-empty [menu] opens on long-press, right-click, Shift+F10 and the
/// context-menu key, and each item is a semantic custom action (KIT-28).
///
/// States: default, two-line, disabled (with reason), unavailable,
/// selected, hover, focused, destructive (KIT-12). Loading, empty and error
/// belong to the list (KitSkeletonRows, KitStateView), not the row.
class KitRow extends StatelessWidget {
  const KitRow({
    super.key,
    required this.title,
    this.leading,
    this.supporting,
    this.trailing,
    this.onTap,
    this.onLongPress,
    this.titleMaxLines = 1,
    this.supportingMaxLines = 1,
    this.titleIsFileName = false,
    this.below,
    this.titleKey,
    this.supportingKey,
    this.padding,
    this.enabled = true,
    this.destructive = false,
    this.menu = const [],
    this.menuLabel,
    this.swipe,
    this.server,
    this.disabledReason,
    this.selected = false,
    this.action,
    this.titleAccent = false,
  }) : assert(
         action == null || trailing == null,
         'KitRow: the action takes the trailing slot; pass one or the other',
       ),
       capability = null,
       enable = null,
       _unavailable = false;

  /// The title in the accent colour: the row that starts something (New
  /// project), beside an accent [KitRow.badge].
  final bool titleAccent;

  /// A capability this server lacks (kit-v2.md §2.5; STATE-12): a dimmed
  /// row that says why and, when the capability can be turned on, offers
  /// the flow. Never a dead row; `whenMissing: hidden` only where no enable
  /// flow exists (built by KitCapabilityExplainer.row from the registry,
  /// kit-CapabilityExplainer, tier 1e). The reason wraps in full, never
  /// cut; from 1.3× text the enable action sits under it (A11Y-8).
  const KitRow.unavailable({
    super.key,
    required this.title,
    required String reason,
    this.enable,
    this.capability,
    this.leading,
    this.server,
    this.titleKey,
    this.supportingKey,
    this.padding,
  }) : supporting = null,
       disabledReason = reason,
       enabled = false,
       trailing = null,
       onTap = null,
       onLongPress = null,
       titleMaxLines = 2,
       supportingMaxLines = 2,
       titleIsFileName = false,
       below = null,
       destructive = false,
       menu = const [],
       menuLabel = null,
       swipe = null,
       selected = false,
       action = null,
       titleAccent = false,
       _unavailable = true;

  final Widget? leading;
  final String title;

  /// Muted by default; spans may carry their own colour for a state word.
  final InlineSpan? supporting;
  final Widget? trailing;
  final VoidCallback? onTap;

  /// Kept (KIT-43). Retired for menus by kit-KitRow-v2: use [menu]. A row
  /// passes one or the other (asserted).
  final VoidCallback? onLongPress;

  /// One line by default (§6). A row whose title is the person's own long
  /// words (a run's objective) may take two.
  final int titleMaxLines;

  /// One line by default (§6); two where the line's end carries the state
  /// ("… · Finished 5h ago"). At large text the row raises it: at least two
  /// lines from 1.3×, three from 2.0×.
  final int supportingMaxLines;

  /// The title is a file name ("checkout_page.dart"): from 1.3× text it
  /// wraps only after `_`, `-` or before the extension's dot, never
  /// mid-word ("checkout_page.da / rt"), and is shown whole (no line cap,
  /// no ellipsis). Screen readers read the name as given.
  final bool titleIsFileName;

  /// Shown under the supporting line: where a trailing state word moves at
  /// large text instead of squeezing the title.
  final Widget? below;

  /// Keys of the title and supporting texts, for tests.
  final Key? titleKey;
  final Key? supportingKey;

  /// The row's own padding; by default the 16 dp rails of a list. A row
  /// inside a block that already sits on the rails (a state, a card's
  /// content) passes its own, usually no side padding.
  final EdgeInsetsGeometry? padding;

  /// False for an action that cannot run now: the title and leading glyph
  /// turn `text3` (never an `Opacity`, LOOK-14) and the row ignores taps.
  /// [disabledReason] says why (STATE-8).
  final bool enabled;

  /// Deletes or ends something (LOOK-5): title in `danger`. The act
  /// confirms first or offers Undo per DATA-11 (K2 §4.1); this flag decides
  /// neither. In a [KitRowGroup] it sits last, after a divider.
  final bool destructive;

  /// The row's rarer actions (KIT-28): opened by long-press, right-click,
  /// Shift+F10 or the context-menu key, and exposed as semantic custom
  /// actions. Destructive items last (KitMenu ordering). A [swipe] adds its
  /// twin here automatically.
  final List<KitMenuItem> menu;

  /// The opened menu's name ("Conversation actions"), used when a tap on a
  /// row with a [menu] and no [onTap] opens it. (KitTappable, which opens
  /// the menu on long-press, right-click and the keys, has no name
  /// parameter yet; reported in the kit-KitRow-v2 QA record.)
  final String? menuLabel;

  /// The row's one swipe (always Undo, never confirm; KitSwipeAction.md).
  final KitSwipeAction? swipe;

  /// Shown first in the supporting line, "laptop · …", on rows that come
  /// from another server (multi-server vertical). Wrapped with KitBidi.auto.
  final String? server;

  /// With [enabled] false: the visible reason (STATE-8), shown as the
  /// supporting line (replacing [supporting]) and as the semantic hint.
  final String? disabledReason;

  /// The row currently open in the detail pane (KitScreen.twoPane).
  final bool selected;

  /// Only on [KitRow.unavailable]: the flow that turns the capability on.
  final KitAction? enable;

  /// The row's own one action, beside what a tap on the row opens (a team
  /// found on the server: "Turn on", while the row opens the team's page):
  /// a tertiary [KitButton] at the row's end, moved under the supporting
  /// line from 1.3× text so the title keeps the row's width (A11Y-8), the
  /// same place [KitRow.unavailable] puts [enable]. It takes the trailing
  /// slot, so a row with an action has no [trailing].
  final KitAction? action;

  /// Only on [KitRow.unavailable]: the capabilities.json id ("voice.model").
  final String? capability;

  final bool _unavailable;

  /// A leading icon in its tile (visual language §4): 30 dp of `surface3`
  /// with 9 dp corners, the icon in `text1` unless [color] is given. In a
  /// disabled or unavailable row the glyph turns `text3`.
  static Widget icon(BuildContext context, IconData icon, {Color? color}) =>
      _KitRowIconTile(icon: icon, color: color);

  /// A round icon badge for a row that starts something: [accent] tints the
  /// circle and the glyph with the accent (the row's main act), otherwise a
  /// quiet circle. Pair an accent badge with `titleAccent: true`.
  static Widget badge(
    BuildContext context,
    IconData icon, {
    bool accent = false,
  }) => _KitRowIconTile(icon: icon, round: true, accent: accent);

  List<KitMenuItem> _menuWithTwin(BuildContext context) {
    final swipe = this.swipe;
    if (swipe == null) return menu;
    final twin = swipe.menuItem;
    return [
      KitMenuItem(
        label: twin.label,
        icon: twin.icon,
        group: twin.group,
        onSelected: () => unawaited(swipe.run(context)),
      ),
      ...menu,
    ];
  }

  @override
  Widget build(BuildContext context) {
    assert(
      onLongPress == null || menu.isEmpty,
      'KitRow: long-press opens the menu; pass onLongPress or menu, not both '
      '(KIT-43).',
    );
    assert(
      this.swipe == null ||
          !menu.any((item) => item.label == this.swipe!.label),
      'KitRow: the swipe adds its own menu twin "${this.swipe?.label}"; do not '
      'pass a menu item with the same label (KitSwipeAction.md).',
    );
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final dimmed = !enabled;
    final reason = dimmed ? disabledReason : null;
    final server = this.server;

    InlineSpan? line = reason != null ? TextSpan(text: reason) : supporting;
    if (server != null && server.isNotEmpty) {
      line = TextSpan(
        children: [
          TextSpan(
            text: line == null
                ? KitBidi.auto(server)
                : '${KitBidi.auto(server)} · ',
            style: TextStyle(color: roles.text2),
          ),
          ?line,
        ],
      );
    }

    final titleColor = dimmed
        ? roles.text3
        : destructive
        ? roles.danger
        : titleAccent
        ? roles.accent
        : null;
    final textScale = MediaQuery.textScalerOf(context).scale(1);
    final titleLines = textScale >= 1.3
        ? math.max(titleMaxLines, 2)
        : titleMaxLines;
    // A list keeps its supporting line to one line at ordinary sizes (§6);
    // at large text the line wraps before it is cut, so it never ends after
    // a few words ("Editing workflow fil…", A11Y-8): two lines from 1.3×,
    // three from 2.0×.
    final supportingLines = textScale >= 2.0
        ? math.max(supportingMaxLines, 3)
        : textScale >= 1.3
        ? math.max(supportingMaxLines, 2)
        : supportingMaxLines;
    // From 1.3× text a file name wraps between its words and is shown
    // whole ([titleIsFileName]).
    final wholeFileName = titleIsFileName && titleLines > 1;

    // The unavailable row's enable flow, or an enabled row's own action:
    // one tertiary button, trailing, under the text from 1.3× text.
    final enable = _unavailable ? this.enable : action;
    final enableButton = enable == null
        ? null
        : KitButton.fromAction(
            enable,
            role: KitButtonRole.tertiary,
            expand: false,
          );
    // From 1.3× text the enable action moves under the reason, so the
    // reason keeps the row's width instead of sharing it (A11Y-8).
    final enableBelow = enableButton != null && textScale >= 1.3;
    final Widget? trailing = enableButton != null
        ? (enableBelow ? null : enableButton)
        : this.trailing;

    Widget? leading = this.leading;
    if (leading != null && dimmed) {
      leading = IconTheme.merge(
        data: IconThemeData(color: roles.text3),
        child: leading,
      );
    }

    final Widget content = ConstrainedBox(
      // 54 dp with one line, 60 with two (§4); minimums, so the row grows
      // with the text.
      constraints: BoxConstraints(
        minHeight: line == null && below == null
            ? tokens.rowHeight
            : tokens.rowHeightTwoLine,
      ),
      child: Padding(
        padding:
            padding ??
            EdgeInsetsDirectional.fromSTEB(
              tokens.space4,
              tokens.space2,
              trailing == null ? tokens.space4 : tokens.space1,
              tokens.space2,
            ),
        child: Row(
          children: [
            if (leading != null) ...[leading, SizedBox(width: tokens.space3)],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    wholeFileName ? kitBreakableFileName(title) : title,
                    key: titleKey,
                    semanticsLabel: wholeFileName ? title : null,
                    // A wrapping file name is shown whole, never cut.
                    maxLines: wholeFileName ? null : titleLines,
                    overflow: wholeFileName ? null : TextOverflow.ellipsis,
                    style: tokens.rowTitle.copyWith(color: titleColor),
                  ),
                  if (line != null) ...[
                    const SizedBox(height: KitTokens.rowLineGap),
                    Text.rich(
                      line,
                      key: supportingKey,
                      // An unavailable row's reason is why the row is dim:
                      // it wraps in full, never cut (A11Y-8, STATE-12).
                      maxLines: _unavailable ? null : supportingLines,
                      overflow: _unavailable ? null : TextOverflow.ellipsis,
                      style: tokens.rowSupporting,
                    ),
                  ],
                  if (below case final below?) ...[
                    const SizedBox(height: KitTokens.rowLineGap),
                    below,
                  ],
                  if (enableBelow) KitInset(child: enableButton),
                ],
              ),
            ),
            if (trailing != null)
              // A trailing value never pushes the title off the row: it
              // gets at most half the window (A11Y-8, G6: no overflow at
              // 320 dp and 2.0 text), and KitRowValue ellipsizes inside it.
              // Not a LayoutBuilder, so rows still answer intrinsic sizing
              // (dialogs, IntrinsicHeight parents).
              ConstrainedBox(
                constraints: BoxConstraints(
                  maxWidth: MediaQuery.sizeOf(context).width / 2,
                ),
                child: trailing,
              ),
          ],
        ),
      ),
    );

    final scoped = _KitRowScope(dimmed: dimmed, child: content);
    final Widget row;
    if (_unavailable) {
      row = _unavailableRow(scoped);
    } else if (dimmed) {
      row = Semantics(
        container: true,
        button: onTap != null,
        enabled: false,
        hint: reason,
        child: scoped,
      );
    } else {
      row = _enabledRow(context, scoped);
    }

    final Widget painted = selected
        ? ColoredBox(color: roles.surface3, child: row)
        : row;
    final swipe = this.swipe;
    if (swipe == null || dimmed) return painted;
    return _KitSwipeFrame(action: swipe, child: painted);
  }

  /// An unavailable row: not a Tab stop of its own (its enable button is),
  /// enabled: false with the reason as the hint; a tap anywhere on it runs
  /// [enable], since the whole row is the target.
  Widget _unavailableRow(Widget content) {
    final run = enable?.onPressed;
    return Semantics(
      container: true,
      enabled: false,
      hint: disabledReason,
      child: run == null
          ? content
          : GestureDetector(
              behavior: HitTestBehavior.opaque,
              excludeFromSemantics: true,
              onTap: run,
              child: content,
            ),
    );
  }

  Widget _enabledRow(BuildContext context, Widget content) {
    final menu = _menuWithTwin(context);
    final surface = selected
        ? KitSurfaceLevel.surface3
        : KitSurfaceLevel.surface1;
    if (onTap != null || menu.isNotEmpty) {
      return Builder(
        builder: (rowContext) => KitTappable(
          onTap:
              onTap ??
              () => unawaited(
                KitRowMenu.show(rowContext, menu, menuLabel: menuLabel),
              ),
          menu: menu,
          onLongPress: menu.isEmpty ? onLongPress : null,
          selected: selected ? true : null,
          surface: surface,
          shape: KitShape.square,
          child: content,
        ),
      );
    }
    final longPress = onLongPress;
    if (longPress != null) {
      // KIT-43: a long-press-only row keeps firing without a tap.
      return Semantics(
        container: true,
        selected: selected ? true : null,
        onLongPress: longPress,
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          excludeFromSemantics: true,
          onLongPress: longPress,
          child: content,
        ),
      );
    }
    return Semantics(
      container: true,
      selected: selected ? true : null,
      child: content,
    );
  }
}

/// Tells [KitRow.icon] tiles inside a disabled or unavailable row to paint
/// their glyph in `text3` (LOOK-14: colour, never opacity).
class _KitRowScope extends InheritedWidget {
  const _KitRowScope({required this.dimmed, required super.child});

  final bool dimmed;

  static bool dimmedOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitRowScope>()?.dimmed ??
      false;

  @override
  bool updateShouldNotify(_KitRowScope oldWidget) => oldWidget.dimmed != dimmed;
}

class _KitRowIconTile extends StatelessWidget {
  const _KitRowIconTile({
    required this.icon,
    this.color,
    this.round = false,
    this.accent = false,
  });

  final IconData icon;
  final Color? color;
  final bool round;
  final bool accent;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final dimmed = _KitRowScope.dimmedOf(context);
    return SizedBox.square(
      dimension: tokens.iconTileSize,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: accent
              ? tokens.roles.accent.withValues(alpha: .18)
              : tokens.roles.surface3,
          shape: round ? BoxShape.circle : BoxShape.rectangle,
          borderRadius: round
              ? null
              : BorderRadius.circular(tokens.iconTileRadius),
        ),
        child: Center(
          child: Icon(
            icon,
            size: tokens.smallIconSize,
            color: dimmed
                ? tokens.roles.text3
                : accent
                ? tokens.roles.accent
                : color ?? tokens.roles.text1,
          ),
        ),
      ),
    );
  }
}

/// The swipe around a row (KitSwipeAction.md "Wiring in KitRow"): end to
/// start only, the revealed panel behind it, always snapping back.
class _KitSwipeFrame extends StatelessWidget {
  const _KitSwipeFrame({required this.action, required this.child});

  final KitSwipeAction action;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final reduced = KitMotion.reduced(context);
    return Dismissible(
      key: action.id,
      // The reading end, mirrored in RTL (Dismissible follows
      // Directionality for endToStart).
      direction: DismissDirection.endToStart,
      // The snap-back; instant under reduced motion (G8). The drag itself
      // still follows the finger: direct manipulation, not an animation.
      movementDuration: reduced ? Duration.zero : KitMotion.standard,
      // The row always returns: the list owner removes or keeps it after
      // the act (KitSwipeAction.md "Flow on a completed swipe").
      confirmDismiss: (_) {
        unawaited(action.run(context));
        return Future<bool>.value(false);
      },
      background: _KitSwipePanel(action: action),
      child: child,
    );
  }
}

/// The revealed panel: `surface3`, the glyph and the verb in `text1` at the
/// end. Never `danger`: a swiped act is undoable by definition (LOOK-5).
class _KitSwipePanel extends StatelessWidget {
  const _KitSwipePanel({required this.action});

  final KitSwipeAction action;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    return ColoredBox(
      key: const ValueKey('kit-swipe-background'),
      color: roles.surface3,
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(horizontal: tokens.space4),
        child: Align(
          alignment: AlignmentDirectional.centerEnd,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              // The glyph does not scale with text (KitSwipeAction.md 200 %).
              Icon(action.icon, size: 22, color: roles.text1),
              SizedBox(width: tokens.space2),
              Flexible(
                child: Text(
                  action.label,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: tokens.rowTitle.copyWith(color: roles.text1),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A row's trailing value in `text3`, optionally before the chevron
/// (visual language §5: "Claude Sonnet 4 ›"). The value is what the row
/// is set to now; the chevron says the row opens a screen.
///
/// States: none — the row it sits in carries the states.
class KitRowValue extends StatelessWidget {
  const KitRowValue(this.value, {super.key, this.chevron = true})
    : count = null;

  /// A count badge instead of a value: the number in a small pill whose
  /// digits take the danger colour ("3" errors kept), "99+" above 99.
  /// [value] is what it means in words ("3 errors kept"): the badge's
  /// semantics, so the number is never colour alone (STATE-9). A count
  /// of 0 or less shows only the chevron.
  const KitRowValue.count(
    int this.count,
    this.value, {
    super.key,
    this.chevron = true,
  });

  final String value;
  final bool chevron;
  final int? count;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final count = this.count;
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (count == null)
            Flexible(
              child: Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: tokens.rowValue,
              ),
            )
          else if (count > 0)
            Semantics(
              label: value,
              excludeSemantics: true,
              child: _KitCountPill(count > 99 ? '99+' : '$count'),
            ),
          if (chevron)
            Padding(
              padding: EdgeInsetsDirectional.only(
                start: tokens.space1,
                end: tokens.space3,
              ),
              child: Icon(
                AppIconography.chevronRight,
                size: tokens.smallIconSize,
                color: tokens.roles.text3,
              ),
            )
          else
            SizedBox(width: tokens.space4),
        ],
      ),
    );
  }
}

/// [KitRowValue.count]'s pill: at least `KitTokens.badgeHeight` /
/// `badgeMinWidth` and growing with its clamped text (A11Y-8,
/// `KitTokens.badgeTextScaleMax`), `surface2` with the digits in `danger`.
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
      decoration: ShapeDecoration(
        color: tokens.roles.surface2,
        shape: tokens.shapeOf(KitShape.pill),
      ),
      child: Text(
        text,
        textScaler: scaler,
        style: KitText.styleOf(context, KitTextRole.caption).copyWith(
          color: tokens.roles.danger,
          fontWeight: FontWeight.w600,
          fontFeatures: const [FontFeature.tabularFigures()],
        ),
      ),
    );
  }
}

/// Rows grouped on one panel (visual language §4, §5): `surface1`, 18 dp
/// corners, a hairline of exactly one physical pixel between rows, inset
/// to where the row's words start, with an optional section [label] 8 dp
/// above. No per-row menus: a row's rarer actions open on long-press or
/// right-click (`KitRow.menu`).
///
/// **One inset (slice-R4).** The [label] is a [KitSectionLabel]: its words
/// start at the group's own edge (the gutter by default, or the sheet's
/// padding with `margin: EdgeInsets.zero`), on the same line as a
/// [KitField]'s label and a [KitDetailsFold]'s title. With [labelTerm] the
/// words explain themselves ([KitTerm]).
///
/// **Section gap.** A labelled group keeps [KitTokens.sectionGap] from what
/// is above it ([gapBefore] null), except as the first thing in a scroll
/// view or on its page; a fixed spacer the caller already put right above it collapses
/// into the gap rather than adding to it. An unlabelled group adds no gap
/// unless [gapBefore] asks for one. For a lazy list on one panel use
/// [KitSliverRowGroup].
///
/// Destructive order (§2.5, §4.2): direct [KitRow] children whose
/// `destructive` is true are moved, in a stable order, after every other
/// row, behind a full-width hairline.
///
/// States: none — each row it holds carries its own states.
class KitRowGroup extends StatelessWidget {
  const KitRowGroup({
    super.key,
    required this.children,
    this.label,
    this.labelTrailing,
    this.leadingIcons = true,
    this.margin,
    this.gapBefore,
    this.labelTerm,
  });

  final List<Widget> children;

  /// The section's name above the panel (never uppercase).
  final String? label;
  final Widget? labelTrailing;

  /// What [label] means, shown by a [KitTerm] on its words (Providers, MCP
  /// servers, Resources). Needs a [label].
  final String? labelTerm;

  /// The space above the group; see the class comment. Null is the section
  /// gap rule for a labelled group and none for an unlabelled one.
  final double? gapBefore;

  /// Whether the rows lead with an icon tile: the separators then start
  /// where the words do.
  final bool leadingIcons;

  /// Around the group; the screen gutter at the sides by default.
  final EdgeInsetsGeometry? margin;

  static bool _isDestructive(Widget child) =>
      child is KitRow && child.destructive;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inset = leadingIcons ? KitDividerInset.text : KitDividerInset.gutter;
    final label = this.label;
    final ordinary = [
      for (final child in children)
        if (!_isDestructive(child)) child,
    ];
    final destructive = [
      for (final child in children)
        if (_isDestructive(child)) child,
    ];
    return Padding(
      padding: margin ?? EdgeInsets.symmetric(horizontal: tokens.gutter),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (label != null)
            KitSectionLabel(
              label,
              trailing: labelTrailing,
              explanation: labelTerm,
              margin: EdgeInsets.zero,
              gapBefore: gapBefore,
            )
          else if (gapBefore case final gap? when gap > 0)
            SizedBox(height: gap),
          Material(
            color: tokens.roles.surface1,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
            ),
            clipBehavior: Clip.antiAlias,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                for (var i = 0; i < ordinary.length; i++) ...[
                  if (i > 0) KitDivider(inset: inset),
                  ordinary[i],
                ],
                for (var i = 0; i < destructive.length; i++) ...[
                  if (i == 0 && ordinary.isNotEmpty)
                    const KitDivider(
                      key: ValueKey('kit-row-group-destructive-divider'),
                    )
                  else if (i > 0)
                    KitDivider(inset: inset),
                  destructive[i],
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// [name] with zero-width break chances after `_` and `-` and before the
/// extension's dot, so a wrapping file name breaks between its words
/// ("checkout_page" / ".dart") instead of mid-word. A part longer than a
/// line still breaks where it must. [KitRow.titleIsFileName] uses it.
String kitBreakableFileName(String name) {
  const zwsp = '\u200B';
  final dot = name.lastIndexOf('.');
  final out = StringBuffer();
  for (var i = 0; i < name.length; i++) {
    final char = name[i];
    if (i == dot && i > 0) out.write(zwsp);
    out.write(char);
    if ((char == '_' || char == '-') && i < name.length - 1) out.write(zwsp);
  }
  return out.toString();
}

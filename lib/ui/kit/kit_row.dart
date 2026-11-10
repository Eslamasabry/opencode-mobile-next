import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';

import '../app_iconography.dart';
import 'kit_arrival.dart';
import 'kit_bidi.dart';
import 'kit_buttons.dart';
import 'kit_chip.dart';
import 'kit_divider.dart';
import 'kit_icon.dart';
import 'kit_max_width.dart';
import 'kit_menu.dart';
import 'kit_motion.dart';
import 'kit_optical.dart';
import 'kit_reach.dart';
import 'kit_row_parts.dart' show KitExpandRow, KitRowMenu, KitSwitchRow;
import 'kit_section_label.dart';
import 'kit_surface.dart' show kitSurfaceMaterial;
import 'kit_surface_look.dart';
import 'kit_swipe_action.dart';
import 'kit_tappable.dart';
import 'kit_tokens.dart';
import 'motion/kit_motion_parts.dart' show KitSwap;

export 'kit_row_value.dart';

part 'kit_row_group_seam.dart';

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
    this.titleDetail,
    this.disabledReason,
    this.selected = false,
    this.action,
    this.chip,
    this.titleAccent = false,
    this.roomy = false,
    this.titleMark,
    this.leadingAtTop = false,
  }) : assert(
         action == null || trailing == null,
         'KitRow: the action takes the trailing slot; pass one or the other',
       ),
       assert(
         chip == null || (trailing == null && action == null),
         'KitRow: the chip takes the trailing slot; pass one of chip, action '
         'or trailing',
       ),
       capability = null,
       enable = null,
       _unavailable = false;

  /// The title in the accent colour: the row that starts something (New
  /// project), beside an accent [KitRow.badge]; a row that acts in place
  /// instead of opening a page (Restart, Send a test notification), whose
  /// words then read as a text button's while a row that opens a page shows
  /// a chevron; or a file the project changed (Files), beside a
  /// [KitRow.changeMark].
  final bool titleAccent;

  /// A small mark right after the title on its first line ([changeMark]: a
  /// file with uncommitted changes), so the state is on the name itself and
  /// never colour alone. It carries its own semantics label.
  final Widget? titleMark;

  /// A door on a short index (the Settings hub's five rows): at least
  /// [roomyHeight] tall with one line or two, so a list of a few words
  /// reads calm instead of packed. Pair it with [KitRow.glyph]. Ordinary
  /// lists keep the 56/64 dp rows.
  final bool roomy;

  /// The leading mark sits level with the title's first line instead of
  /// the row's middle: a tall row (a step with a command or a button under
  /// its words) keeps its mark by the words that name it.
  final bool leadingAtTop;

  /// The height of a [roomy] row (one or two lines).
  static const roomyHeight = 64.0;

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
    this.chip,
    this.capability,
    this.leading,
    this.server,
    this.titleKey,
    this.supportingKey,
    this.padding,
  }) : assert(
         enable == null || chip == null,
         'KitRow.unavailable: pass the enable flow as a button or as a chip, '
         'not both',
       ),
       supporting = null,
       disabledReason = reason,
       enabled = false,
       trailing = null,
       onTap = null,
       onLongPress = null,
       titleMaxLines = 2,
       supportingMaxLines = 2,
       titleIsFileName = false,
       titleDetail = null,
       below = null,
       destructive = false,
       menu = const [],
       menuLabel = null,
       swipe = null,
       selected = false,
       action = null,
       titleAccent = false,
       roomy = false,
       leadingAtTop = false,
       titleMark = null,
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

  /// Deletes or ends something (LOOK-5): the title stays neutral, never red
  /// (owner rule); the confirmation guards the act. The act confirms first
  /// or offers Undo per DATA-11 (K2 §4.1); this flag decides neither. In a
  /// [KitRowGroup] it sits last, after a divider.
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

  /// A quiet word after the title on the same line, in the supporting tone
  /// ("Files · neon-breaker"). Wrapped with KitBidi.auto.
  final String? titleDetail;

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

  /// The row's one act as a short chip ("Install", "Sign in") where the
  /// title already names its target: every chip of a list starts at one
  /// edge of a shared trailing column ([chipColumnWidth]), so a list of
  /// acts reads as one column instead of ragged button labels of different
  /// widths (owner, 2026-10-08). Screen readers hear
  /// [KitRowChip.semanticsLabel], which names the target ("Install Codex").
  /// From 1.3× text it moves under the supporting line, like [action]. On
  /// [KitRow.unavailable] it is the way forward, and a tap anywhere on the
  /// row runs it.
  final KitRowChip? chip;

  /// The trailing column a [chip] starts in: wide enough for the short act
  /// words in both languages ("Install", "تسجيل الدخول"), so their start
  /// edges line up down a list.
  static const chipColumnWidth = 112.0;

  /// Only on [KitRow.unavailable]: the capabilities.json id ("voice.model").
  final String? capability;

  final bool _unavailable;

  /// A leading icon in its tile (visual language §4): 28 dp of `surface3`
  /// with 9 dp corners, the icon in `text1` unless [color] is given. In a
  /// disabled or unavailable row the glyph turns `text3`.
  ///
  /// [mark] names a bundled brand mark (`assets/marks/<slug>.svg`, an agent's
  /// logo); it is drawn in the glyph's place, same size and colour, and
  /// [icon] stays as the fallback for an unknown agent.
  ///
  /// [needsYou] adds the needs-you dot at the tile's top-end corner (the
  /// server sheet's row of a server waiting on the person): amber, ringed
  /// in the panel colour, and never alone, since the row's line says
  /// "Needs you" in words (STATE-9).
  static Widget icon(
    BuildContext context,
    IconData icon, {
    Color? color,
    String? mark,
    bool needsYou = false,
  }) =>
      _KitRowIconTile(icon: icon, color: color, mark: mark, needsYou: needsYou);

  /// A step's number in the icon tile's shape: "1", "2", "3" for a guide's
  /// ordered steps. Pair it with [leadingAtTop] when the row is tall.
  static Widget number(BuildContext context, int n) =>
      _KitRowIconTile(icon: AppIconography.check, number: n);

  /// A leading glyph without a tile, for [roomy] index rows: the icon at
  /// 26 dp in `text2`, centred in a slot as wide as a tile, so the words
  /// start where a tiled row's do. In a disabled row it turns `text3`.
  ///
  /// [needsYou] adds the needs-you dot at the glyph's top-end corner, as
  /// on [icon] (the server sheet's row of a server waiting on the person).
  /// [accent] draws it in the accent (a changed file's glyph, beside a
  /// [titleAccent] name).
  static Widget glyph(
    BuildContext context,
    IconData icon, {
    bool needsYou = false,
    bool accent = false,
  }) => _KitRowGlyph(icon: icon, needsYou: needsYou, accent: accent);

  /// The [titleMark] of a changed file or folder: a small accent dot after
  /// the name. [semanticsLabel] says the state in words ("Modified",
  /// "2 changed files"), so a screen reader hears it after the name.
  static Widget changeMark(
    BuildContext context, {
    required String semanticsLabel,
  }) => _KitRowChangeMark(semanticsLabel: semanticsLabel);

  /// A [glyph]'s size before the person's text size grows it.
  static const glyphSize = 26.0;

  /// A round icon badge for a row that starts something: [accent] tints the
  /// circle and the glyph with the accent (the row's main act), otherwise a
  /// quiet circle. Pair an accent badge with `titleAccent: true`.
  static Widget badge(
    BuildContext context,
    IconData icon, {
    bool accent = false,
  }) => _KitRowIconTile(icon: icon, round: true, accent: accent);

  /// The one-in-use mark at a row's end: a check in the accent colour, for
  /// the row a list is on now (the server in use). A selected row gets a
  /// check, never a solid accent fill (vision rule "mint fill means the
  /// main action", 2026-10-20). Pair it with a word that says it ("Connected
  /// · "), so the colour is never the only sign (STATE-9).
  /// Its ink ends on the row's end rail, where a chevron's and a switch's
  /// do ([trailingRailOf], [KitOptical]).
  static Widget check(BuildContext context, {Key? key}) => Builder(
    key: key,
    builder: (context) {
      final tokens = KitTokens.of(context);
      final size = tokens.iconSize(context, tokens.markIconSize);
      return Padding(
        padding: EdgeInsetsDirectional.only(
          end: KitOptical.endPad(
            context,
            AppIconography.check,
            size,
            trailingRailOf(context) ?? tokens.space2,
          ),
        ),
        child: ExcludeSemantics(
          child: Icon(
            AppIconography.check,
            size: size,
            color: tokens.roles.accent,
          ),
        ),
      );
    },
  );

  /// How far a trailing part's ink should end from the end of the trailing
  /// slot it sits in, so that it ends on the rail the row's words start
  /// from (impeccable layout pass, 2026-10-28): the row pads its start by
  /// space4 but its end by space1 (room for a 48 dp target), so the rail is
  /// space3 inside the slot. Null outside a [KitRow], or in a row with its
  /// own [padding].
  static double? trailingRailOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitRowScope>()?.trailingRail;

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
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
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
    final chip = this.chip;
    final Widget? enableButton = enable != null
        ? KitButton.fromAction(
            enable,
            role: KitButtonRole.tertiary,
            expand: false,
          )
        : chip != null
        ? _KitRowChipSlot(chip: chip)
        : null;
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

    // The words and the leading mark keep space2 above and below; a
    // trailing 48 dp target reaches into that room instead of adding to it,
    // so a one-line row is 56 dp with or without a chevron or a switch, and
    // a two-line row 64 (same-role rows, one height: impeccable layout
    // pass, 2026-10-28). A row with its own [padding] lays out as given.
    final own = padding == null;
    Widget padded(Widget child) => own
        ? Padding(
            padding: EdgeInsets.symmetric(vertical: tokens.space2),
            child: child,
          )
        : child;
    final Widget content = ConstrainedBox(
      // 56 dp with one line, 64 with two (§4); minimums, so the row grows
      // with the text.
      constraints: BoxConstraints(
        minHeight: roomy
            ? roomyHeight
            : line == null && below == null
            ? tokens.rowHeight
            : tokens.rowHeightTwoLine,
      ),
      child: Padding(
        padding:
            padding ??
            EdgeInsetsDirectional.only(
              start: tokens.space4,
              end: trailing == null ? tokens.space4 : tokens.space1,
            ),
        child: Row(
          crossAxisAlignment: leadingAtTop
              ? CrossAxisAlignment.start
              : CrossAxisAlignment.center,
          children: [
            // The leading column ([KitTokens.leadColumn]): any mark narrower
            // than a tile is centred in a tile-wide slot, so the words start
            // on the one text rail whatever the mark's size.
            if (leading != null) ...[
              padded(
                ConstrainedBox(
                  constraints: BoxConstraints(minWidth: tokens.iconTileSize),
                  child: Center(
                    widthFactor: 1,
                    heightFactor: 1,
                    child: leading,
                  ),
                ),
              ),
              SizedBox(width: tokens.space3),
            ],
            Expanded(
              child: padded(
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (titleDetail case final detail? when detail.isNotEmpty)
                      Text.rich(
                        TextSpan(
                          children: [
                            TextSpan(text: title),
                            TextSpan(
                              text: '\u00a0\u00b7 ${KitBidi.auto(detail)}',
                              style: tokens.rowSupporting.copyWith(
                                fontSize: tokens.rowTitle.fontSize,
                                color: roles.text2,
                              ),
                            ),
                          ],
                        ),
                        key: titleKey,
                        maxLines: titleLines,
                        overflow: TextOverflow.ellipsis,
                        style: tokens.rowTitle.copyWith(color: titleColor),
                      )
                    else if (titleMark case final mark?)
                      // The mark after the words, not inside them: the title
                      // stays plain text (found, read and copied as itself).
                      Row(
                        children: [
                          Flexible(
                            child: Text(
                              wholeFileName
                                  ? kitBreakableFileName(title)
                                  : title,
                              key: titleKey,
                              semanticsLabel: wholeFileName ? title : null,
                              maxLines: wholeFileName ? null : titleLines,
                              overflow: wholeFileName
                                  ? null
                                  : TextOverflow.ellipsis,
                              style: tokens.rowTitle.copyWith(
                                color: titleColor,
                              ),
                            ),
                          ),
                          SizedBox(width: tokens.space2),
                          mark,
                        ],
                      )
                    else
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
            ),
            // The text never runs into what trails it ("UnrestrictedOpen").
            if (trailing != null) SizedBox(width: tokens.space2),
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
                // A trailing mark that changes kind (chevron, spinner, check)
                // cross-fades instead of popping.
                child: KitSwap(
                  alignment: AlignmentDirectional.centerEnd,
                  child: KeyedSubtree(
                    key: ValueKey<Object>(trailing.key ?? trailing.runtimeType),
                    // Level with the title's first line, a top-aligned
                    // row's trailing part keeps the words' room above it.
                    child: leadingAtTop ? padded(trailing) : trailing,
                  ),
                ),
              ),
          ],
        ),
      ),
    );

    final scoped = _KitRowScope(
      dimmed: dimmed,
      trailingRail: own ? tokens.space4 - tokens.space1 : null,
      child: content,
    );
    final Widget row;
    if (_unavailable) {
      row = _unavailableRow(scoped);
    } else if (dimmed) {
      row = Semantics(
        container: true,
        button: onTap != null,
        enabled: false,
        // The reason is the row's visible supporting line, already in its
        // label: a hint too would have it said twice.
        child: scoped,
      );
    } else {
      row = _enabledRow(context, scoped);
    }

    // The chosen row is a content surface marked selected: Solid's
    // surface3 fill; pressed in under Soft, the tile step under Brutal and
    // Paper. On a flat list (Solid) it is a rounded plate inset from the
    // row's ends, like the rounded controls around it and the platform's
    // own two-pane highlight, never a square band across the page; a
    // chosen value's check stays the caller's.
    final Widget painted = selected && KitSurfaceLook.flatLists(context)
        ? Stack(
            children: [
              Positioned.fill(
                child: Padding(
                  padding: EdgeInsetsDirectional.symmetric(
                    horizontal: tokens.space2,
                  ),
                  child: kitSurfaceBox(
                    key: const ValueKey('kit-row-chosen-plate'),
                    decoration: KitSurfaceLook.decorate(
                      context,
                      solid: KitSurfaceDecoration(
                        fill: roles.surface3,
                        borderRadius: BorderRadius.circular(tokens.cardRadius),
                      ),
                      level: KitSurfaceLevel.surface3,
                      selected: true,
                    ),
                    child: const SizedBox.expand(),
                  ),
                ),
              ),
              row,
            ],
          )
        : selected
        ? kitSurfaceBox(
            style: KitSurfaceLook.styleOf(context),
            decoration: KitSurfaceLook.decorate(
              context,
              solid: _fill(roles.surface3),
              level: KitSurfaceLevel.surface3,
              selected: true,
            ),
            child: row,
          )
        : row;
    final swipe = this.swipe;
    if (swipe == null || dimmed) return painted;
    return _KitSwipeFrame(action: swipe, child: painted);
  }

  /// An unavailable row: not a Tab stop of its own (its enable button is),
  /// enabled: false with the reason as the hint; a tap anywhere on it runs
  /// [enable], since the whole row is the target.
  Widget _unavailableRow(Widget content) {
    final run = enable?.onPressed ?? chip?.onPressed;
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

/// A [KitRow]'s one act as a short chip (see [KitRow.chip]): the words on
/// the chip, the target screen readers hear, and what a tap does.
@immutable
class KitRowChip {
  const KitRowChip({
    required this.label,
    required this.semanticsLabel,
    required this.onPressed,
    this.key,
  });

  /// The act alone, from the caller's ARB: "Install", "Sign in".
  final String label;

  /// The act with its target (COPY-8, actions name their target): "Install
  /// Codex". The chip's semantic label and tooltip-free name.
  final String semanticsLabel;
  final VoidCallback onPressed;

  /// The chip's key, for tests.
  final Key? key;
}

/// [KitRow.chip] in its column: at least [KitRow.chipColumnWidth] wide with
/// the chip at the end, so a chip ends where the trailing parts of the rows
/// around it end.
/// One button node named with the target.
class _KitRowChipSlot extends StatelessWidget {
  const _KitRowChipSlot({required this.chip});

  final KitRowChip chip;

  @override
  Widget build(BuildContext context) => ConstrainedBox(
    constraints: const BoxConstraints(minWidth: KitRow.chipColumnWidth),
    // A Row at its minimum size still honours the column's minimum width,
    // with the chip at its end, so it ends on the same edge as the chevron
    // or value of the rows above and below it.
    child: Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        Flexible(
          child: Semantics(
            container: true,
            button: true,
            label: chip.semanticsLabel,
            onTap: chip.onPressed,
            excludeSemantics: true,
            child: KitChip.action(
              key: chip.key,
              label: chip.label,
              onPressed: chip.onPressed,
            ),
          ),
        ),
      ],
    ),
  );
}

/// Tells [KitRow.icon] tiles inside a disabled or unavailable row to paint
/// their glyph in `text3` (LOOK-14: colour, never opacity).
class _KitRowScope extends InheritedWidget {
  const _KitRowScope({
    required this.dimmed,
    required this.trailingRail,
    required super.child,
  });

  final bool dimmed;

  /// See [KitRow.trailingRailOf].
  final double? trailingRail;

  static bool dimmedOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_KitRowScope>()?.dimmed ??
      false;

  @override
  bool updateShouldNotify(_KitRowScope oldWidget) =>
      oldWidget.dimmed != dimmed || oldWidget.trailingRail != trailingRail;
}

class _KitRowGlyph extends StatelessWidget {
  const _KitRowGlyph({
    required this.icon,
    this.needsYou = false,
    this.accent = false,
  });

  final IconData icon;
  final bool needsYou;
  final bool accent;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final dimmed = _KitRowScope.dimmedOf(context);
    final size = tokens.iconSize(context, KitRow.glyphSize);
    final glyph = Icon(
      icon,
      size: size,
      color: dimmed
          ? tokens.roles.text3
          : accent
          ? tokens.roles.accent
          : tokens.roles.text2,
    );
    return SizedBox(
      width: size > tokens.iconTileSize ? size : tokens.iconTileSize,
      child: Center(child: needsYou ? _withNeedsYouDot(context, glyph) : glyph),
    );
  }
}

class _KitRowChangeMark extends StatelessWidget {
  const _KitRowChangeMark({required this.semanticsLabel});

  final String semanticsLabel;

  /// The dot's diameter before the person's text size grows it.
  static const _size = 7.0;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final size = tokens.iconSize(context, _size);
    return Semantics(
      label: semanticsLabel,
      child: SizedBox.square(
        dimension: size,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: tokens.roles.accent,
            shape: BoxShape.circle,
          ),
        ),
      ),
    );
  }
}

/// A plain fill: what Solid paints for a row's chosen or held state.
BoxDecoration _fill(Color color) => BoxDecoration(color: color);

/// The needs-you dot's size and the panel-coloured ring around it.
const double _needsYouDot = 10;
const double _needsYouDotRing = 2;

/// [child] with the needs-you dot over its top-end corner: amber inside a
/// ring of the panel colour, so it reads apart from what it overlaps.
Widget _withNeedsYouDot(BuildContext context, Widget child) {
  final roles = KitTokens.of(context).roles;
  const outset = (_needsYouDot + _needsYouDotRing * 2) / 3;
  return Stack(
    clipBehavior: Clip.none,
    children: [
      child,
      PositionedDirectional(
        top: -outset,
        end: -outset,
        child: Container(
          key: const ValueKey('kit-row-icon-needs-you'),
          width: _needsYouDot + _needsYouDotRing * 2,
          height: _needsYouDot + _needsYouDotRing * 2,
          padding: const EdgeInsets.all(_needsYouDotRing),
          decoration: BoxDecoration(
            color: roles.surface1,
            shape: BoxShape.circle,
          ),
          child: DecoratedBox(
            decoration: BoxDecoration(
              color: roles.attention,
              shape: BoxShape.circle,
            ),
          ),
        ),
      ),
    ],
  );
}

class _KitRowIconTile extends StatelessWidget {
  const _KitRowIconTile({
    required this.icon,
    this.color,
    this.mark,
    this.round = false,
    this.accent = false,
    this.needsYou = false,
    this.number,
  });

  final IconData icon;

  /// A step number drawn in the tile instead of [icon].
  final int? number;
  final String? mark;
  final Color? color;
  final bool round;
  final bool accent;
  final bool needsYou;

  @override
  Widget build(BuildContext context) {
    final tile = _tile(context);
    return needsYou ? _withNeedsYouDot(context, tile) : tile;
  }

  Widget _tile(BuildContext context) {
    final tokens = KitTokens.of(context);
    final dimmed = _KitRowScope.dimmedOf(context);
    // Flat lists (Solid): the icon stands on the row without a tile, in the
    // slot a tile takes, so the words start where they always do. A step's
    // number keeps its tile: a bare digit would read as words.
    if (number == null && KitSurfaceLook.flatLists(context)) {
      return _bare(context, tokens, dimmed: dimmed);
    }
    return SizedBox.square(
      dimension: tokens.iconTileSize,
      child: kitSurfaceBox(
        style: KitSurfaceLook.styleOf(context),
        decoration: KitSurfaceLook.decorate(
          context,
          solid: BoxDecoration(
            color: accent
                ? tokens.roles.accent.withValues(alpha: .18)
                : tokens.roles.surface3,
            shape: round ? BoxShape.circle : BoxShape.rectangle,
            borderRadius: round
                ? null
                : BorderRadius.circular(tokens.iconTileRadius),
          ),
          level: KitSurfaceLevel.surface3,
          role: KitSurfaceRole.control,
          color: accent ? tokens.roles.accent.withValues(alpha: .18) : null,
        ),
        child: Center(
          child: () {
            final size = tokens.iconSize(context, tokens.smallIconSize);
            final tint = dimmed
                ? tokens.roles.text3
                : accent
                ? tokens.roles.accent
                : color ?? tokens.roles.text1;
            final slug = mark;
            if (number case final n?) {
              return Text(
                '$n',
                style: tokens.rowTitle.copyWith(
                  color: tint,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              );
            }
            return slug == null
                ? Icon(icon, size: size, color: tint)
                : IconTheme(
                    data: IconThemeData(color: tint),
                    child: KitIcon.mark(slug, growsWithText: true),
                  );
          }(),
        ),
      ),
    );
  }
}

/// A [_KitRowIconTile]'s icon without its tile: the glyph at
/// [KitRowIconBare.size] in `text2` (the given colour, the accent, or
/// `text3` in a disabled row), centred in a tile-wide slot.
extension on _KitRowIconTile {
  Widget _bare(BuildContext context, KitTokens tokens, {required bool dimmed}) {
    final size = tokens.iconSize(context, KitRowIconBare.size);
    final tint = dimmed
        ? tokens.roles.text3
        : accent
        ? tokens.roles.accent
        : color ?? tokens.roles.text2;
    final slug = mark;
    return SizedBox(
      width: size > tokens.iconTileSize ? size : tokens.iconTileSize,
      height: tokens.iconTileSize,
      child: Center(
        child: slug == null
            ? Icon(icon, size: size, color: tint)
            : IconTheme(
                data: IconThemeData(color: tint),
                child: KitIcon.mark(
                  slug,
                  size: KitIconSize.large,
                  growsWithText: true,
                ),
              ),
      ),
    );
  }
}

/// A row icon without a tile ([KitSurfaceLook.flatLists]).
abstract final class KitRowIconBare {
  /// The glyph's size before the person's text size grows it: the
  /// platform's list icon.
  static const double size = 24;
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
    // The row held aside: the style's pressed state layer.
    return kitSurfaceBox(
      key: const ValueKey('kit-swipe-background'),
      style: KitSurfaceLook.styleOf(context),
      decoration: KitSurfaceLook.decorate(
        context,
        solid: _fill(roles.surface3),
        role: KitSurfaceRole.stateLayer,
        level: KitSurfaceLevel.surface3,
        pressed: true,
      ),
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
    this.leadingIcons,
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
  /// where the words do, past the icons. Null (the default) reads it off
  /// the rows themselves ([rowsLead]), so a group of rows without icons
  /// never draws its separators from an icon column that is not there.
  final bool? leadingIcons;

  /// Around the group; the screen gutter at the sides by default.
  final EdgeInsetsGeometry? margin;

  /// Whether [rows] lead with an icon: true when any [KitRow],
  /// [KitSwitchRow] or [KitExpandRow] among them (also inside a
  /// [KitArrival] or a [KeyedSubtree]) has a leading mark, false when every
  /// such row has none. Another widget does not count; with none of these
  /// rows to go by the answer is true, as before.
  static bool rowsLead(Iterable<Widget> rows) {
    bool? leads(Widget row) => switch (row) {
      KitRow(:final leading) => leading != null,
      KitSwitchRow(:final leading) => leading != null,
      KitExpandRow(:final leading) => leading != null,
      KitArrival(:final child) => leads(child),
      KeyedSubtree(:final child) => leads(child),
      _ => null,
    };
    var known = false;
    for (final row in rows) {
      switch (leads(row)) {
        case true:
          return true;
        case false:
          known = true;
        case null:
          break;
      }
    }
    return !known;
  }

  static bool _isDestructive(Widget child) =>
      child is KitRow && child.destructive;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.list(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inset = (leadingIcons ?? rowsLead(children))
        ? KitDividerInset.text
        : KitDividerInset.gutter;
    final label = this.label;
    final ordinary = [
      for (final child in children)
        if (!_isDestructive(child)) child,
    ];
    final destructive = [
      for (final child in children)
        if (_isDestructive(child)) child,
    ];
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(tokens.panelCornerRadius),
    );
    final margin =
        this.margin ??
        EdgeInsetsDirectional.symmetric(horizontal: tokens.gutter);
    final sides = margin.resolve(Directionality.of(context));
    // A panel the style sets straight on the page, with no fill, edge or
    // shadow of its own (Paper), would leave its rows' words a row inset
    // in from the label above them: the rows then reach out into the
    // margin by that inset (as far as the margin goes), so the words start
    // flush with the label, and the row inset stays inside the press
    // highlight.
    // Flat lists (Solid): the rows sit on the page itself, like a bare
    // panel, and reach out the same way.
    // In a sheet the rows reach on out over its rails ([KitReach]), so
    // their words start on the sheet title's line, not a row inset in.
    final flat = KitSurfaceLook.flatLists(context);
    final panel = flat
        ? KitSurfaceLook.bare
        : KitSurfaceLook.decorate(context, solid: _fill(tokens.roles.surface1));
    final margins = math.min(sides.left, sides.right);
    final reach = KitSurfaceDecoration.isBare(panel)
        ? math.min(tokens.space4, margins + KitReach.of(context))
        : 0.0;
    // What the margin cannot give, the rows take from around the group.
    final beyond = math.max(0.0, reach - margins);
    final back = EdgeInsetsDirectional.symmetric(horizontal: reach);
    // The rules between the rows stop at the margin at the end too, as the
    // section rule above them does, however far the rows reach.
    final rule = KitDivider(inset: inset, endInset: reach);
    // A panel the style frames (an edge or a shadow: Soft, Brutal)
    // set right under another group would double its edge on the one above
    // and catch that one's shadow: it keeps a small gap instead.
    final seam = label == null && (gapBefore ?? 0) == 0 && _frames(panel);
    // KitSectionLabel draws the section rule (Paper) itself, right above
    // the words.
    final Widget? heading = label == null
        ? null
        : KitSectionLabel(
            label,
            trailing: labelTrailing,
            explanation: labelTerm,
            margin: EdgeInsets.zero,
            gapBefore: gapBefore,
          );
    // Outermost, so a press in the reach is still the row's where the
    // parents above pass it on.
    return KitReach.extend(
      by: beyond,
      child: Padding(
        padding: margin.subtract(
          EdgeInsetsDirectional.symmetric(horizontal: reach - beyond),
        ),
        child: _KitRowGroupMark(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (heading != null)
                reach > 0 ? Padding(padding: back, child: heading) : heading
              else if (gapBefore case final gap? when gap > 0)
                SizedBox(height: gap)
              else if (seam)
                _KitGroupSeam(gap: tokens.space3),
              _panel(
                context,
                flat: flat,
                shape: shape,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (var i = 0; i < ordinary.length; i++) ...[
                      if (i > 0) rule,
                      ordinary[i],
                    ],
                    for (var i = 0; i < destructive.length; i++) ...[
                      if (i == 0 && ordinary.isNotEmpty)
                        Padding(
                          padding: back,
                          child: const KitDivider(
                            key: ValueKey('kit-row-group-destructive-divider'),
                          ),
                        )
                      else if (i > 0)
                        rule,
                      destructive[i],
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// The rows' panel: the page itself where lists are [flat] (a clear
  /// Material, so the rows' ink still has one), else the style's surface1.
  static Widget _panel(
    BuildContext context, {
    required bool flat,
    required ShapeBorder shape,
    required Widget child,
  }) => flat
      ? Material(type: MaterialType.transparency, child: child)
      : kitSurfaceMaterial(
          context,
          level: KitSurfaceLevel.surface1,
          fill: KitTokens.of(context).roles.surface1,
          shape: shape,
          child: child,
        );

  /// Whether [panel] draws a boundary of its own: an edge or a shadow.
  static bool _frames(Decoration panel) => switch (panel) {
    KitSurfaceDecoration(
      :final edgeStart,
      :final edgeEnd,
      :final edgeWidth,
      :final shadows,
      :final hardShadow,
      :final distance,
    ) =>
      (edgeWidth > 0 && (edgeStart.a > 0 || edgeEnd.a > 0)) ||
          shadows.isNotEmpty ||
          hardShadow.a > 0 ||
          distance > 0,
    _ => false,
  };
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

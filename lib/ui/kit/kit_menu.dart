// The one popup menu (docs/ux-system/kit-api/KitMenu.md): the row menu
// (`KitRowMenu`, and `KitRow.menu` on long-press and right-click), the top
// bar's overflow, the action block's "More" and the composer's chips all
// open it. Replaces `PopupMenuButton`, `PopupMenuItem`,
// `CheckedPopupMenuItem` and `PopupMenuDivider`.
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../l10n/app_localizations.dart';
import '../app_theme.dart';
import 'kit_bidi.dart';
import 'kit_copy.dart';
import 'kit_ink_layer.dart';
import 'kit_layout.dart';
import 'kit_max_width.dart';
import 'kit_motion.dart';
import 'kit_surface_look.dart';
import 'kit_text.dart';
import 'kit_tokens.dart';

void _noop() {}

/// One entry of a [KitMenuPanel] shown by [showKitMenu], a [KitRowMenu], a
/// `KitRow`'s menu or an action block's "More". A superset of the pre-v2
/// `KitMenuItem` (label, onSelected, key, destructive, enabled keep their
/// meaning and defaults, KIT-43).
@immutable
class KitMenuItem {
  const KitMenuItem({
    required this.label,
    required this.onSelected,
    this.key,
    this.destructive = false,
    this.enabled = true,
    this.icon,
    this.checked,
    this.group,
    this.disabledReason,
    this.shortcut,
    this.supporting,
  }) : copyText = null,
       redact = true;

  /// A copy entry (KIT-23): selecting it runs `KitCopy.copy(context, text())`
  /// after the menu closes; "Copied" is announced once; no SnackBar.
  /// [redact] (default true) masks secrets on the way to the clipboard; the
  /// person's own content (a message) passes false to copy verbatim
  /// (SEC-13).
  const KitMenuItem.copy({
    required this.label, // "Copy message", "Copy path"
    required String Function() text, // read at selection time
    this.key,
    this.icon = AppIconography.copy,
    this.group,
    this.shortcut,
    this.redact = true,
  }) : copyText = text,
       onSelected = _noop,
       destructive = false,
       enabled = true,
       checked = null,
       disabledReason = null,
       supporting = null;

  /// A verb that names what happens: "Archive conversation".
  final String label;

  /// Runs after the menu has closed (so a confirmation it opens never
  /// stacks on the menu).
  final VoidCallback onSelected;
  final Key? key;

  /// An act that loses data or ends running work (LOOK-5): label and icon in
  /// neutral (never red). Always shown last, after a divider (§4.2). What it
  /// opens confirms or offers Undo per DATA-11; the item itself never decides.
  final bool destructive;

  /// False: shown in `text3`, not selectable, with [disabledReason] as a
  /// second line (STATE-8). A disabled item with no reason should be left
  /// out.
  final bool enabled;
  final String? disabledReason;

  /// A 20 dp glyph at the start (one glyph per verb, COPY-18).
  final IconData? icon;

  /// Null: not checkable. True/false: a checkable item; the check sits in
  /// the start slot in `accent` (LOOK-6 current-selection mark), with
  /// `checked` semantics. Replaces `CheckedPopupMenuItem`.
  final bool? checked;

  /// Items with the same [group] sit together; a hairline divider separates
  /// consecutive groups. Null is its own group. Replaces `PopupMenuDivider`.
  /// A [KitMenuGroup] also names its group: a muted heading above its first
  /// item ("Go to", "Do").
  final Object? group;

  /// "Ctrl+Shift+C", shown at the end on a fine pointer (display only).
  final String? shortcut;

  /// One muted line under the label that says what choosing it means
  /// ("Edits files and runs commands"), for a menu of choices whose names
  /// alone do not say it. A disabled item shows its [disabledReason] there
  /// instead.
  final String? supporting;

  /// Set only by [KitMenuItem.copy].
  final String Function()? copyText;

  /// Whether [KitMenuItem.copy] masks secrets before copying (default
  /// true). Always true on a plain item, which copies nothing.
  final bool redact;
}

/// A named [KitMenuItem.group]: the menu draws [label] as a muted heading
/// above the group's first item, so a long menu reads as a few kinds of
/// act ("Go to", "Do") instead of one list. Equal by [label]. The heading
/// is not an item: it takes no focus and is read as a header.
@immutable
class KitMenuGroup {
  const KitMenuGroup(this.label);

  /// Short, a kind of act: "Go to", "Do".
  final String label;

  @override
  bool operator ==(Object other) =>
      other is KitMenuGroup && other.label == label;

  @override
  int get hashCode => label.hashCode;
}

/// A divider marker used by [kitMenuLayout]; never a [KitMenuItem].
class _KitMenuDividerMarker {
  const _KitMenuDividerMarker();
}

const _kitMenuDivider = _KitMenuDividerMarker();

/// [items], each still in its original relative order, with a divider
/// marker inserted where consecutive items' [KitMenuItem.group] differ,
/// and a named group's [KitMenuGroup] (its heading) before its first item.
List<Object> _withGroupDividers(List<KitMenuItem> items) {
  final out = <Object>[];
  for (var i = 0; i < items.length; i++) {
    final starts = i == 0 || items[i].group != items[i - 1].group;
    if (i > 0 && starts) out.add(_kitMenuDivider);
    final group = items[i].group;
    if (starts && group is KitMenuGroup) out.add(group);
    out.add(items[i]);
  }
  return out;
}

/// The structural ordering rule (KitMenu.md "Ordering"), not an assert:
///
/// 1. Items keep their order within their group.
/// 2. Groups keep the order of their first item.
/// 3. All `destructive` items are then moved, in a stable order, after a
///    divider at the end.
///
/// The result is a list of [KitMenuItem]s, [KitMenuGroup] headings and
/// divider markers (never exposed directly; check `is KitMenuItem` to tell
/// them apart).
@visibleForTesting
List<Object> kitMenuLayout(List<KitMenuItem> items) {
  final normal = [
    for (final item in items)
      if (!item.destructive) item,
  ];
  final destructive = [
    for (final item in items)
      if (item.destructive) item,
  ];
  final rows = _withGroupDividers(normal);
  if (destructive.isNotEmpty) {
    if (rows.isNotEmpty) rows.add(_kitMenuDivider);
    rows.addAll(_withGroupDividers(destructive));
  }
  return rows;
}

/// Opens the one popup menu (KIT-11: modal parts return a Future).
///
/// [position]: a global point (right-click, long-press). Null: anchored to
/// [context]'s render box, opening below it (above when there is no room)
/// and aligned to its end edge.
///
/// Returns the chosen item after its [KitMenuItem.onSelected] (or copy) has
/// run, or null when dismissed. Callers never act on the result a second
/// time; it exists for tests and focus return.
///
/// An empty [items] returns null at once without opening anything.
Future<KitMenuItem?> showKitMenu(
  BuildContext context, {
  required List<KitMenuItem> items,
  Offset? position,
  String? semanticsLabel, // the menu's name: "Conversation actions"
  Key? menuKey,
}) async {
  if (items.isEmpty) return null;
  final navigator = Navigator.of(context);
  final overlayBox =
      navigator.overlay?.context.findRenderObject() as RenderBox?;
  final ownBox = context.findRenderObject();
  Rect anchor;
  if (position != null) {
    anchor = position & Size.zero;
  } else if (ownBox is RenderBox && ownBox.attached && overlayBox != null) {
    final origin = ownBox.localToGlobal(Offset.zero, ancestor: overlayBox);
    anchor = origin & ownBox.size;
  } else {
    anchor = Offset.zero & Size.zero;
  }
  final gutter = KitTokens.of(context).gutter;
  final reduced = KitMotion.reduced(context);
  // The panel drops a few dp from what opened it as it fades in (rises,
  // when it opens above); Calm only fades it (KitMotion.travels).
  final travel = KitMotion.travels(context)
      ? KitTokens.of(context).space2
      : 0.0;
  final rtl = Directionality.of(context) == TextDirection.rtl;
  // Decided from the invoking gesture itself, not from the focus highlight
  // mode (a mouse click also switches that to "traditional"): Shift+F10,
  // the context-menu key and Enter all act on key down, so their key is
  // still held while the invoker calls this synchronously. A right-click or
  // long-press holds no key (a held modifier, as in Shift+right-click, does
  // not count).
  final fromKeyboard = _kitMenuOpenedFromKeyboard();
  // Focus returns to the invoker on close (KitMenu.md Keyboard, LAY-10).
  final invokerFocus = FocusManager.instance.primaryFocus;
  final selected = await navigator.push<KitMenuItem>(
    _KitMenuRoute(
      items: items,
      anchor: anchor,
      byPoint: position != null,
      rtl: rtl,
      gutter: gutter,
      reducedMotion: reduced,
      travel: travel,
      focusFirstItem: fromKeyboard,
      semanticsLabel: semanticsLabel,
      menuKey: menuKey,
    ),
  );
  if (invokerFocus != null &&
      invokerFocus.context != null &&
      invokerFocus.canRequestFocus &&
      !invokerFocus.hasPrimaryFocus) {
    invokerFocus.requestFocus();
  }
  if (selected == null) return null;
  final copyText = selected.copyText;
  if (copyText != null) {
    // The invoker may have unmounted while the menu was open (a list row
    // that rebuilt). The navigator outlives it and carries the same view,
    // localizations and direction, so the chosen copy always happens.
    final copyContext = context.mounted ? context : navigator.context;
    if (copyContext.mounted) {
      await KitCopy.copy(copyContext, copyText(), redact: selected.redact);
    }
  } else {
    selected.onSelected();
  }
  return selected;
}

/// The modifier keys, with left/right variants collapsed to one key each.
final _kitMenuModifierKeys = <LogicalKeyboardKey>{
  LogicalKeyboardKey.shift,
  LogicalKeyboardKey.control,
  LogicalKeyboardKey.alt,
  LogicalKeyboardKey.meta,
  LogicalKeyboardKey.capsLock,
  LogicalKeyboardKey.numLock,
  LogicalKeyboardKey.fn,
};

/// Whether a non-modifier key is held right now, i.e. the menu is being
/// opened from the keyboard (see [showKitMenu]).
bool _kitMenuOpenedFromKeyboard() => LogicalKeyboardKey.collapseSynonyms(
  HardwareKeyboard.instance.logicalKeysPressed,
).any((key) => !_kitMenuModifierKeys.contains(key));

/// The menu's panel on its own, without a route: for galleries and for a
/// part that shows a menu inline (a `KitTopBar` overflow on a PC). The same
/// ordering, grouping and item look as [showKitMenu].
///
/// States: none — a fixed list of items, no server data (KitMenu.md
/// "no loading or error state").
class KitMenuPanel extends StatelessWidget {
  const KitMenuPanel({
    super.key,
    required this.items,
    required this.onSelected, // ValueChanged<KitMenuItem>
    this.semanticsLabel,
  }) : _focusFirstItem = false;

  /// The panel [showKitMenu]'s route shows: [focusFirstItem] when the menu
  /// was opened from the keyboard (KitMenu.md Keyboard).
  const KitMenuPanel._route({
    super.key,
    required this.items,
    required this.onSelected,
    this.semanticsLabel,
    required bool focusFirstItem,
  }) : _focusFirstItem = focusFirstItem;

  final List<KitMenuItem> items;
  final ValueChanged<KitMenuItem> onSelected;
  final String? semanticsLabel;

  /// False: the panel itself takes focus (opened by pointer, or shown
  /// inline); true: the first enabled item does.
  final bool _focusFirstItem;

  @override
  Widget build(BuildContext context) =>
      KitMaxWidth.popover(child: _buildContent(context));

  Widget _buildContent(BuildContext context) {
    final rows = kitMenuLayout(items);
    final hasLeadingSlot = items.any(
      (item) => item.icon != null || item.checked != null,
    );
    final showShortcut = KitLayout.finePointer(context);
    final label =
        semanticsLabel ??
        lookupAppLocalizations(Localizations.localeOf(context)).kitMenu;
    return Semantics(
      scopesRoute: true,
      namesRoute: true,
      explicitChildNodes: true,
      label: label,
      child: _KitMenuBody(
        rows: rows,
        hasLeadingSlot: hasLeadingSlot,
        showShortcut: showShortcut,
        focusFirstItem: _focusFirstItem,
        onSelected: onSelected,
      ),
    );
  }
}

@immutable
class _KitMenuMoveIntent extends Intent {
  const _KitMenuMoveIntent(this.delta);
  final int delta;
}

@immutable
class _KitMenuJumpIntent extends Intent {
  const _KitMenuJumpIntent({required this.first});
  final bool first;
}

/// The panel's body: the surface, the border, the width and height caps
/// (a short window scrolls inside, with the border pinned, KitMenu.md
/// Adaptive), and the keyboard navigation (Up/Down wrap, Home/End jump,
/// skipping disabled items; LAY-10).
class _KitMenuBody extends StatefulWidget {
  const _KitMenuBody({
    required this.rows,
    required this.hasLeadingSlot,
    required this.showShortcut,
    required this.focusFirstItem,
    required this.onSelected,
  });

  final List<Object> rows;
  final bool hasLeadingSlot;
  final bool showShortcut;
  final bool focusFirstItem;
  final ValueChanged<KitMenuItem> onSelected;

  @override
  State<_KitMenuBody> createState() => _KitMenuBodyState();
}

class _KitMenuBodyState extends State<_KitMenuBody> {
  final _panelFocus = FocusNode(debugLabel: 'kit-menu');
  late List<FocusNode> _itemFocus;

  int get _enabledCount =>
      widget.rows.whereType<KitMenuItem>().where((item) => item.enabled).length;

  @override
  void initState() {
    super.initState();
    _itemFocus = [
      for (var i = 0; i < _enabledCount; i++)
        FocusNode(debugLabel: 'kit-menu-item'),
    ];
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.focusFirstItem && _itemFocus.isNotEmpty) {
        _itemFocus.first.requestFocus();
      } else {
        _panelFocus.requestFocus();
      }
    });
  }

  @override
  void didUpdateWidget(covariant _KitMenuBody oldWidget) {
    super.didUpdateWidget(oldWidget);
    final count = _enabledCount;
    if (count != _itemFocus.length) {
      for (final node in _itemFocus) {
        node.dispose();
      }
      _itemFocus = [
        for (var i = 0; i < count; i++) FocusNode(debugLabel: 'kit-menu-item'),
      ];
    }
  }

  @override
  void dispose() {
    _panelFocus.dispose();
    for (final node in _itemFocus) {
      node.dispose();
    }
    super.dispose();
  }

  void _move(int delta) {
    if (_itemFocus.isEmpty) return;
    final n = _itemFocus.length;
    final current = _itemFocus.indexWhere((node) => node.hasFocus);
    final next = current == -1
        ? (delta > 0 ? 0 : n - 1)
        : (((current + delta) % n) + n) % n;
    _itemFocus[next].requestFocus();
  }

  void _jump({required bool first}) {
    if (_itemFocus.isEmpty) return;
    (first ? _itemFocus.first : _itemFocus.last).requestFocus();
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final hairline = KitTokens.hairlineWidth(context);
    final surfaceStyle = KitSurfaceLook.styleOf(context);
    final plain = KitSurfaceLook.isPlain(context);
    // Between groups: the style's line, or its space alone (Soft).
    final divider = KitSurfaceLook.divider(
      context,
      color: roles.hairline,
      width: hairline,
    );

    var itemCursor = 0;
    var dividerCursor = 0;
    final tiles = <Widget>[
      for (final row in widget.rows)
        if (row is KitMenuItem)
          _KitMenuItemTile(
            item: row,
            hasLeadingSlot: widget.hasLeadingSlot,
            showShortcut: widget.showShortcut,
            focusNode: row.enabled ? _itemFocus[itemCursor++] : null,
            onSelected: widget.onSelected,
          )
        else if (row is KitMenuGroup)
          _KitMenuHeading(label: row.label)
        else
          // The outer key keeps siblings apart; the inner one is the
          // frozen TEST-5 handle, the same on every divider.
          KeyedSubtree(
            key: ValueKey<int>(dividerCursor++),
            child: SizedBox(
              key: const ValueKey('kit-menu-divider'),
              height: divider.width,
              child: ColoredBox(color: divider.color),
            ),
          ),
    ];

    final available = MediaQuery.sizeOf(context).height - tokens.gutter * 2;

    return Shortcuts(
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.arrowDown): _KitMenuMoveIntent(1),
        SingleActivator(LogicalKeyboardKey.arrowUp): _KitMenuMoveIntent(-1),
        SingleActivator(LogicalKeyboardKey.home): _KitMenuJumpIntent(
          first: true,
        ),
        SingleActivator(LogicalKeyboardKey.end): _KitMenuJumpIntent(
          first: false,
        ),
      },
      child: Actions(
        actions: <Type, Action<Intent>>{
          _KitMenuMoveIntent: CallbackAction<_KitMenuMoveIntent>(
            onInvoke: (intent) {
              _move(intent.delta);
              return null;
            },
          ),
          _KitMenuJumpIntent: CallbackAction<_KitMenuJumpIntent>(
            onInvoke: (intent) {
              _jump(first: intent.first);
              return null;
            },
          ),
        },
        child: Focus(
          focusNode: _panelFocus,
          // The border is painted above the content, so an item's hover or
          // focus fill never covers the panel's hairline (at the rounded
          // corners too).
          child: DecoratedBox(
            key: const ValueKey('kit-menu'),
            position: DecorationPosition.foreground,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(tokens.popoverCorner),
              // Soft draws no edge (Settings › Appearance › Style).
              border: plain
                  ? Border.all(
                      color: roles.hairline,
                      width: KitTokens.hairlineWidth(context),
                    )
                  : null,
            ),
            child: kitSurfaceBox(
              style: surfaceStyle,
              decoration: KitSurfaceLook.decorate(
                context,
                solid: BoxDecoration(
                  color: roles.surface2,
                  borderRadius: BorderRadius.circular(tokens.popoverCorner),
                ),
                level: KitSurfaceLevel.surface2,
                role: KitSurfaceRole.sheet,
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(tokens.popoverCorner),
                child: ConstrainedBox(
                  constraints: BoxConstraints(
                    minWidth: KitLayout.popoverMinWidth,
                    maxWidth: KitLayout.popoverMaxWidth,
                    maxHeight: available > 0 ? available : double.infinity,
                  ),
                  child: IntrinsicWidth(
                    child: Material(
                      type: MaterialType.transparency,
                      child: SingleChildScrollView(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: tiles,
                        ),
                      ),
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

/// A named group's heading: the label in the muted label role, read as a
/// header, never focusable or selectable.
class _KitMenuHeading extends StatelessWidget {
  const _KitMenuHeading({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    return Semantics(
      header: true,
      child: Padding(
        key: ValueKey('kit-menu-heading-$label'),
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.space4,
          tokens.space3,
          tokens.space4,
          tokens.space1,
        ),
        child: KitText(
          label,
          role: KitTextRole.label,
          tone: KitTextTone.secondary,
        ),
      ),
    );
  }
}

/// One row of the panel: the leading icon/check slot (only reserved when
/// [hasLeadingSlot]), the label and optional disabled reason, and the
/// shortcut on a fine pointer.
class _KitMenuItemTile extends StatelessWidget {
  const _KitMenuItemTile({
    required this.item,
    required this.hasLeadingSlot,
    required this.showShortcut,
    required this.focusNode,
    required this.onSelected,
  });

  final KitMenuItem item;
  final bool hasLeadingSlot;
  final bool showShortcut;

  /// Null for a disabled item: it takes no keyboard focus (KitMenu.md
  /// "disabled": "not focusable for selection, but still read by a screen
  /// reader").
  final FocusNode? focusNode;
  final ValueChanged<KitMenuItem> onSelected;

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final roles = tokens.roles;
    final enabled = item.enabled;

    Widget? leading;
    if (hasLeadingSlot) {
      final size = tokens.iconSize(context, tokens.smallIconSize);
      if (item.checked == true) {
        leading = Icon(
          AppIconography.check,
          size: size,
          color: enabled ? roles.accent : roles.text3,
        );
      } else if (item.icon != null) {
        leading = Icon(
          item.icon,
          size: size,
          color: !enabled ? roles.text3 : roles.text2,
        );
      } else {
        leading = SizedBox.square(dimension: size);
      }
      leading = SizedBox.square(
        dimension: size,
        child: Center(child: leading),
      );
    }

    final labelTone = !enabled ? KitTextTone.tertiary : KitTextTone.primary;

    final content = Padding(
      padding: EdgeInsetsDirectional.symmetric(
        horizontal: tokens.space4,
        vertical: tokens.space2,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          if (leading != null) ...[leading, SizedBox(width: tokens.space3)],
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                KitText(
                  item.label,
                  role: KitTextRole.rowTitle,
                  tone: labelTone,
                ),
                if ((!enabled ? item.disabledReason : item.supporting)
                    case final line?)
                  Padding(
                    padding: const EdgeInsetsDirectional.only(
                      top: KitTokens.rowLineGap,
                    ),
                    child: KitText(
                      line,
                      role: KitTextRole.secondary,
                      tone: KitTextTone.secondary,
                    ),
                  ),
              ],
            ),
          ),
          if (enabled && showShortcut && item.shortcut != null) ...[
            SizedBox(width: tokens.space3),
            KitBidi.ltrText(
              item.shortcut!,
              style: KitText.styleOf(
                context,
                KitTextRole.mono,
                tone: KitTextTone.secondary,
              ),
            ),
          ],
        ],
      ),
    );

    final sized = ConstrainedBox(
      constraints: BoxConstraints(minHeight: tokens.minTarget),
      child: Align(alignment: AlignmentDirectional.centerStart, child: content),
    );

    if (!enabled) {
      // No explicit `label:` here: KitText's own semantics (the title, and
      // the reason line below it) already merge into this node in order,
      // so setting one too would announce the title twice.
      return Semantics(
        key: item.key,
        button: true,
        enabled: false,
        checked: item.checked,
        hint: item.disabledReason,
        child: sized,
      );
    }

    final node = focusNode;
    return Semantics(
      button: true,
      checked: item.checked,
      hint: item.shortcut,
      // The native ripple on Android (KitInk), on its own clear Material so
      // no panel fill below hides it; no splash elsewhere.
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(
          key: item.key,
          focusNode: node,
          onTap: () => onSelected(item),
          hoverColor: roles.surface3,
          focusColor: roles.surface3,
          splashFactory: KitInk.splashFactory(context),
          splashColor: KitInk.color(context),
          highlightColor: Colors.transparent,
          child: node == null
              ? sized
              : ListenableBuilder(
                  listenable: node,
                  builder: (context, child) => Stack(
                    children: [
                      child!,
                      if (node.hasFocus) const _KitMenuFocusRing(),
                    ],
                  ),
                  child: sized,
                ),
        ),
      ),
    );
  }
}

/// The keyboard focus ring on the focused item (LAY-10, LOOK-21): `accent`,
/// [KitTokens.focusRingWidth] (two physical pixels), inset by `space1` with
/// corners concentric to the panel's, so it is never clipped at the first
/// or last row. Hover keeps the plain `surface3` fill, so the two differ.
class _KitMenuFocusRing extends StatelessWidget {
  const _KitMenuFocusRing();

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final inset = tokens.space1;
    return PositionedDirectional(
      start: inset,
      end: inset,
      top: inset,
      bottom: inset,
      child: IgnorePointer(
        child: DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(tokens.popoverCorner - inset),
            border: Border.all(
              color: tokens.roles.accent,
              width: KitTokens.focusRingWidth(context),
            ),
          ),
        ),
      ),
    );
  }
}

/// Positions the panel: below the invoker, end-aligned, flipping above when
/// there is no room (context-anchored), or with its top-start corner at
/// [anchor]'s point ([byPoint]); always inside the window minus the system
/// [insets] (status bar, cutout, on-screen keyboard) and the [gutter], both
/// in size and in position (KitMenu.md Adaptive, LAY-11). The position is
/// snapped to the physical pixel grid ([devicePixelRatio]) so the hairline
/// and the focus ring land on whole pixels.
class _KitMenuLayoutDelegate extends SingleChildLayoutDelegate {
  const _KitMenuLayoutDelegate({
    required this.anchor,
    required this.byPoint,
    required this.rtl,
    required this.gutter,
    required this.insets,
    required this.devicePixelRatio,
    this.drop,
    this.travel = 0,
  }) : super(relayout: drop);

  /// The route's entrance: while it runs forward the panel starts [travel]
  /// dp back towards its anchor and settles on its place. Only the panel's
  /// position moves; its own layout is untouched. Closing only fades.
  final Animation<double>? drop;
  final double travel;

  final Rect anchor;
  final bool byPoint;
  final bool rtl;
  final double gutter;
  final EdgeInsets insets;
  final double devicePixelRatio;

  /// The area the panel may occupy inside a window of [size].
  Rect _area(Size size) =>
      (insets + EdgeInsets.all(gutter)).deflateRect(Offset.zero & size);

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    final area = _area(constraints.biggest);
    return BoxConstraints(
      maxWidth: math.max(0, area.width),
      maxHeight: math.max(0, area.height),
    );
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    final area = _area(size);
    double left;
    double top;
    var above = false;
    if (byPoint) {
      left = rtl ? anchor.left - childSize.width : anchor.left;
      top = anchor.top;
    } else {
      left = rtl ? anchor.left : anchor.right - childSize.width;
      top = anchor.bottom;
      final fitsBelow = top + childSize.height <= area.bottom;
      final fitsAbove = anchor.top - childSize.height >= area.top;
      if (!fitsBelow && fitsAbove) {
        top = anchor.top - childSize.height;
        above = true;
      }
    }
    final maxLeft = math.max(area.left, area.right - childSize.width);
    final maxTop = math.max(area.top, area.bottom - childSize.height);
    final placed = Offset(
      _snap(left.clamp(area.left, maxLeft)),
      _snap(top.clamp(area.top, maxTop)),
    );
    final drop = this.drop;
    if (drop == null || travel == 0 || drop.status != AnimationStatus.forward) {
      return placed;
    }
    final still = 1 - KitMotion.enter.transform(drop.value);
    return placed.translate(0, still * travel * (above ? 1 : -1));
  }

  double _snap(double value) => devicePixelRatio > 0
      ? (value * devicePixelRatio).roundToDouble() / devicePixelRatio
      : value;

  @override
  bool shouldRelayout(_KitMenuLayoutDelegate oldDelegate) =>
      anchor != oldDelegate.anchor ||
      byPoint != oldDelegate.byPoint ||
      rtl != oldDelegate.rtl ||
      gutter != oldDelegate.gutter ||
      insets != oldDelegate.insets ||
      devicePixelRatio != oldDelegate.devicePixelRatio ||
      drop != oldDelegate.drop ||
      travel != oldDelegate.travel;
}

/// The route [showKitMenu] pushes: a transparent, dismissible barrier, a
/// cross-fade on [KitMotion.quick] with a short drop from the anchor under
/// Animations: Full (no scale, MOT-2), and Esc to close.
class _KitMenuRoute extends PopupRoute<KitMenuItem> {
  _KitMenuRoute({
    required this.items,
    required this.anchor,
    required this.byPoint,
    required this.rtl,
    required this.gutter,
    required this.reducedMotion,
    required this.travel,
    required this.focusFirstItem,
    required this.semanticsLabel,
    required this.menuKey,
  });

  final List<KitMenuItem> items;
  final Rect anchor;
  final bool byPoint;
  final bool rtl;
  final double gutter;
  final bool reducedMotion;

  /// How far the panel drops in as it opens; 0 fades only.
  final double travel;
  final bool focusFirstItem;
  final String? semanticsLabel;
  final Key? menuKey;

  @override
  Color? get barrierColor => null;

  @override
  bool get barrierDismissible => true;

  @override
  String? get barrierLabel => null;

  @override
  Duration get transitionDuration =>
      reducedMotion ? Duration.zero : KitMotion.quick;

  @override
  Duration get reverseTransitionDuration =>
      reducedMotion ? Duration.zero : KitMotion.quick;

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) => Builder(
    builder: (routeContext) => Shortcuts(
      shortcuts: const {
        SingleActivator(LogicalKeyboardKey.escape): DismissIntent(),
      },
      child: Actions(
        actions: <Type, Action<Intent>>{
          DismissIntent: CallbackAction<DismissIntent>(
            onInvoke: (intent) {
              Navigator.of(routeContext).maybePop();
              return null;
            },
          ),
        },
        child: CustomSingleChildLayout(
          delegate: _KitMenuLayoutDelegate(
            anchor: anchor,
            byPoint: byPoint,
            rtl: rtl,
            gutter: gutter,
            insets:
                MediaQuery.paddingOf(routeContext) +
                MediaQuery.viewInsetsOf(routeContext),
            devicePixelRatio: MediaQuery.devicePixelRatioOf(routeContext),
            drop: travel == 0 ? null : animation,
            travel: travel,
          ),
          child: KitMenuPanel._route(
            key: menuKey,
            focusFirstItem: focusFirstItem,
            items: items,
            semanticsLabel: semanticsLabel,
            onSelected: (item) => Navigator.of(routeContext).pop(item),
          ),
        ),
      ),
    ),
  );

  @override
  Widget buildTransitions(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
    Widget child,
  ) => FadeTransition(
    opacity: CurvedAnimation(
      parent: animation,
      curve: KitMotion.enter,
      reverseCurve: KitMotion.exit,
    ),
    child: child,
  );
}

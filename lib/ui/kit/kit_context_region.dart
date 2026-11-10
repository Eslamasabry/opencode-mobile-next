// The desktop right-click region (moved from lib/ui/desktop/context_menu.dart
// by screen-shell-1, R12): right-click, Shift+F10 and the context-menu key
// open the one popup menu (`showKitMenu`, KitMenu.md) around a child that
// keeps its own taps. The old `ContextMenuRegion` forwards here until the
// seven callers become `KitRow.menu`/`KitTappable.menu` in their units
// (KitMenu.md "Replaces"), and the unit that empties it deletes it.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../desktop/desktop_interaction.dart' show desktopInteractions;
import 'kit_menu.dart';
import 'kit_tokens.dart';

/// Adds a desktop right-click menu to [child].
///
/// Off desktop this returns [child] untouched: the long-press sheets stay
/// the only action surface on touch. On desktop the same actions open with
/// a secondary click at the pointer, or with Shift+F10 or the context-menu
/// key at the region's centre while it has focus; keyboard focus draws the
/// kit focus ring (2 physical px, `accent`, LOOK-21).
///
/// [menu] is a callback, so the menu is built from state at the moment of
/// the click rather than when the child was laid out.
///
/// The region is excluded from semantics: every action in the menu is
/// reachable from the child's own sheet or menu, which screen readers use.
///
/// States: idle, focused (the ring), menu-open (a second click is ignored
/// until the menu closes). No data states.
class KitContextRegion extends StatefulWidget {
  const KitContextRegion({
    super.key,
    required this.menu,
    required this.child,
    this.menuLabel,
  });

  final List<KitMenuItem> Function() menu;
  final Widget child;

  /// The opened menu's semantic name ("Conversation actions").
  final String? menuLabel;

  @override
  State<KitContextRegion> createState() => _KitContextRegionState();
}

class _KitContextRegionState extends State<KitContextRegion> {
  bool _showFocus = false;
  bool _menuOpen = false;

  Future<void> _open(Offset position) async {
    if (_menuOpen) return;
    _menuOpen = true;
    try {
      await showKitMenu(
        context,
        items: widget.menu(),
        position: position,
        semanticsLabel: widget.menuLabel,
      );
    } finally {
      _menuOpen = false;
    }
  }

  void _openFromKeyboard() {
    final box = context.findRenderObject();
    if (box is! RenderBox || !box.hasSize) return;
    unawaited(_open(box.localToGlobal(box.size.center(Offset.zero))));
  }

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return widget.child;
    final roles = KitTokens.of(context).roles;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.f10, shift: true):
            _openFromKeyboard,
        const SingleActivator(LogicalKeyboardKey.contextMenu):
            _openFromKeyboard,
      },
      child: FocusableActionDetector(
        onShowFocusHighlight: (value) => setState(() => _showFocus = value),
        child: DecoratedBox(
          position: DecorationPosition.foreground,
          decoration: BoxDecoration(
            border: _showFocus
                ? Border.fromBorderSide(
                    BorderSide(
                      color: roles.accent,
                      width: KitTokens.focusRingWidth(context),
                    ),
                  )
                : null,
          ),
          child: GestureDetector(
            behavior: HitTestBehavior.translucent,
            excludeFromSemantics: true,
            onSecondaryTapDown: (details) =>
                unawaited(_open(details.globalPosition)),
            child: widget.child,
          ),
        ),
      ),
    );
  }
}

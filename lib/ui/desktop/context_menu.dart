import 'dart:async';

import 'package:flutter/material.dart';

import 'desktop_interaction.dart';

/// One entry in a right-click menu.
class ContextMenuAction {
  const ContextMenuAction({
    required this.label,
    required this.icon,
    required this.onSelected,
    this.menuKey,
    this.destructive = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback onSelected;

  /// Key on the rendered menu item, so a test can name the entry it taps.
  final ValueKey<String>? menuKey;

  /// Rendered in the error colour, matching the destructive rows in the
  /// bottom sheets these menus mirror.
  final bool destructive;
}

/// Adds a desktop right-click menu to [child].
///
/// On Android this returns [child] untouched — the long-press sheets stay the
/// only action surface there, exactly as before. On desktop the same actions
/// become a secondary-tap menu, because a long press with a mouse is not a
/// gesture anyone performs.
///
/// [actions] is a callback rather than a list so the menu is built from state
/// at the moment of the click, not at the moment the row was laid out.
class ContextMenuRegion extends StatelessWidget {
  const ContextMenuRegion({
    super.key,
    required this.actions,
    required this.child,
  });

  final List<ContextMenuAction> Function() actions;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    if (!desktopInteractions) return child;
    return GestureDetector(
      behavior: HitTestBehavior.translucent,
      // Excluded from semantics: every action in the menu is reachable from
      // the row's own overflow button or sheet, which screen readers use.
      excludeFromSemantics: true,
      onSecondaryTapDown: (details) => unawaited(
        showContextMenu(context, details.globalPosition, actions()),
      ),
      child: child,
    );
  }
}

/// Opens a context menu at [globalPosition]. Exposed so a surface that
/// already owns a gesture recognizer can raise the same menu.
Future<void> showContextMenu(
  BuildContext context,
  Offset globalPosition,
  List<ContextMenuAction> actions,
) async {
  if (actions.isEmpty) return;
  final overlay = Overlay.of(context).context.findRenderObject();
  if (overlay is! RenderBox) return;
  final theme = Theme.of(context);
  final selected = await showMenu<int>(
    context: context,
    position: RelativeRect.fromRect(
      Rect.fromPoints(globalPosition, globalPosition),
      Offset.zero & overlay.size,
    ),
    items: [
      for (var index = 0; index < actions.length; index++)
        PopupMenuItem<int>(
          key: actions[index].menuKey,
          value: index,
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                actions[index].icon,
                size: 18,
                color: actions[index].destructive
                    ? theme.colorScheme.error
                    : theme.colorScheme.onSurfaceVariant,
              ),
              const SizedBox(width: 12),
              // A click near the right edge leaves the menu narrow, and a
              // clipped label must degrade to an ellipsis rather than an
              // overflow stripe.
              Flexible(
                child: Text(
                  actions[index].label,
                  softWrap: false,
                  overflow: TextOverflow.ellipsis,
                  style: actions[index].destructive
                      ? TextStyle(color: theme.colorScheme.error)
                      : null,
                ),
              ),
            ],
          ),
        ),
    ],
  );
  if (selected == null) return;
  actions[selected].onSelected();
}

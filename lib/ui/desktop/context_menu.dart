import 'package:flutter/widgets.dart';

import '../kit/kit_context_region.dart';
import '../kit/kit_menu.dart';

/// One entry in a right-click menu.
///
/// Retired by screen-shell-1: use [KitMenuItem]. Kept so the seven callers
/// keep compiling until their units move to `KitRow.menu` (KIT-43).
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

  /// Loses data or ends running work: shown last, after a divider, in the
  /// kit's destructive look (KitMenu ordering, KIT-28).
  final bool destructive;

  /// The same entry as the kit's one menu item.
  KitMenuItem toKitMenuItem() => KitMenuItem(
    label: label,
    icon: icon,
    onSelected: onSelected,
    key: menuKey,
    destructive: destructive,
  );
}

/// Adds a desktop right-click menu to [child].
///
/// Retired by screen-shell-1: use [KitContextRegion] (or, for a row,
/// `KitRow.menu`). Off desktop this returns [child] untouched; on desktop a
/// secondary click, Shift+F10 or the context-menu key opens the kit menu.
class ContextMenuRegion extends StatelessWidget {
  const ContextMenuRegion({
    super.key,
    required this.actions,
    required this.child,
  });

  final List<ContextMenuAction> Function() actions;
  final Widget child;

  @override
  Widget build(BuildContext context) => KitContextRegion(
    menu: () => [for (final action in actions()) action.toKitMenuItem()],
    child: child,
  );
}

/// Opens the kit menu at [globalPosition]. Exposed so a surface that already
/// owns a gesture recognizer can raise the same menu.
///
/// Retired by screen-shell-1: use `showKitMenu(context, items:, position:)`.
Future<void> showContextMenu(
  BuildContext context,
  Offset globalPosition,
  List<ContextMenuAction> actions,
) async {
  if (actions.isEmpty) return;
  await showKitMenu(
    context,
    items: [for (final action in actions) action.toKitMenuItem()],
    position: globalPosition,
  );
}

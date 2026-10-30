import 'package:flutter/widgets.dart';

import 'kit_tokens.dart';

/// Which leading column the parts under it start their words after
/// (impeccable layout pass, 2026-10-28: one text rail per surface).
///
/// By default the list's [KitTokens.leadColumn]: a mark centred in a
/// tile-wide slot ([KitTokens.iconTileSize]) and [KitTokens.space3], so a
/// notice's words start where a list row's do (56 dp on a page). Under
/// [KitLeadColumn.compact] (the conversation: KitTurn) the compact
/// [KitTokens.leadColumnCompact]: the 20 dp glyph and [KitTokens.space3],
/// so a notice, a step and their buttons share the transcript's 48 dp rail.
class KitLeadColumn extends InheritedWidget {
  const KitLeadColumn.compact({super.key, required super.child});

  /// Whether [context] is under [KitLeadColumn.compact].
  static bool compactOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<KitLeadColumn>() != null;

  /// The slot a leading mark is centred in, before the [KitTokens.space3]
  /// gap: a tile's width in a list, the bare glyph's in the conversation.
  static double slotOf(BuildContext context) {
    final tokens = KitTokens.of(context);
    return compactOf(context) ? tokens.smallIconSize : tokens.iconTileSize;
  }

  @override
  bool updateShouldNotify(KitLeadColumn oldWidget) => false;
}

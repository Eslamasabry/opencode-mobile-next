# KitReach — API (flat polish, 2026-10-26)

How far a flat list inside may run its rows out past its own box, on each side, into the padding around it. `KitSheet` offers its rails (`KitTokens.rail`) to its body; it is zero everywhere else.

A row group with no fill of its own (Solid's flat lists, Paper) already runs its rows out over the page's gutter, so their words start on the gutter and the press reaches the screen's edges. In a sheet the group has no margin to run into, so its words sat one row inset in from the sheet's title. With `KitReach` the rows run out over the rails instead: words, hairlines and the section label start on the title's line, and the press reaches the sheet's edges, as on the platform's own sheets.

## File

- `lib/ui/kit/kit_reach.dart`, exported from `kit.dart`.
- Test: `test/kit/kit_reach_test.dart`.
- Readers: `KitRowGroup`, `KitChoiceTiles` (flat). Writer: `KitSheet`'s body.

## Public API

```dart
class KitReach extends InheritedWidget {
  const KitReach({super.key, required double reach, required Widget child});
  final double reach;                 // on each side, logical px
  static double of(BuildContext context);   // 0 outside any KitReach
  /// [child] laid out [by] wider on each side and drawn that far out past
  /// this box, which keeps its own width; sets the reach back to 0 inside.
  static Widget extend({required double by, required Widget child});
}
```

## Rules

- A part reaches at most `KitTokens.space4`, its row inset, so its words land where the box around it starts.
- What reaches out resets the reach to zero for what it holds: nothing reaches twice.
- A press in the reach lands on the row where the parents above pass it on (a list straight in the sheet's body).

States: none — a scope that only carries a distance.

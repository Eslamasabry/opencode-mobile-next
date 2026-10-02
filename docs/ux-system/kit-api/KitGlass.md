# KitGlass: the plain solid surface on the floating navigation layer

Owner decision (final, 2026-10-02): "Remove this glass bs and let's stick to the basics." Liquid glass and frosted glass are gone from the app. The old fluid glass spec (lens-lift glow, press swell, flow, joined melt, shader, crash guard) is retired. The part keeps its name so no caller changes; it is the one solid floating surface.

## Purpose

`KitGlass` is a bounded surface floating over scrolling content: the bottom dock, the navigation rail, the shell's top controls (server pill, search) and the chat composer. It is never a sheet body, a card or a page background.

## Look

- Fill: opaque `surface2` (`ThemeRoles.surface2`). No translucency, no blur, no shader, no rim highlight.
- Border: the theme's `hairline`, one physical pixel (`KitTokens.hairlineWidth`).
- Corners: `KitTokens.navRadius` (22) unless the caller passes `borderRadius` (the composer passes its own).
- Shadow: at most the one tight elevation shadow, `KitTokens.surfaceShadows` (y 6, blur 16, spread -6, the `elevationShadow` role, 30 % black in both brightnesses); `shadow: false` draws none.
- No reduced-effects branch: it is always solid, so high contrast, accessible navigation and remove animations change nothing here.

## File

- `lib/ui/kit/glass/kit_glass.dart` only.

## Public API

```dart
class KitGlass extends StatelessWidget {
  const KitGlass({Key? key, required Widget child, BorderRadius? borderRadius, bool shadow = true});

  /// Two solid pieces in one row: leading at the start, trailing at the end;
  /// joined, trailing sits one KitTokens.space2 gap from leading.
  const KitGlass.pair({Key? key, required Widget leading, required Widget trailing,
      bool joined = false, BorderRadius? borderRadius, bool shadow = true});

  /// Vertical scroll tracking for the pair's joined state (KitNav puts it
  /// around the page).
  static Widget trackScroll({required Widget child, Object? resetOn});
  static bool scrolledOf(BuildContext context);
}
```

States: none. A surface around its child; it holds no data.

## Tests

`test/kit/kit_glass_test.dart` (solid fill, hairline, shadow, pair positions, scroll tracking), gallery `test/goldens/kit/kit_glass_golden_test.dart`.

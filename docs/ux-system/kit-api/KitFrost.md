# KitFrost: frosted glass on the chat composer and header

Owner decision (2026-10-30): glass the ColorOS / HarmonyOS way, in exactly two places, and only while the conversation is scrolled under them. Everything else stays solid. No refraction, rim lighting or liquid look (the old shader was "very artificial"). Study: `concepts-2026-10-30/glass/` (NOTES.md, `glass-balanced.png`, `glass-balanced-dark.png`).

## Where

1. The floating composer (`KitComposer.layer`): one pane from the top of the parts over it (the chip row, phrase chips, a waiting request) to one space2 under the composer, a space2 out from its sides, corners concentric with the composer's; it adds no height. A sideways line of chips over the composer ends and fades at the pane's inner edge (`KitFrost.lineInsetOf`, read by `KitFilterChips`), never across it. The field's `KitGlass` inside the pane becomes the denser inner well (`KitFrost.wellAlpha`, 80 %).
2. The chat header (`KitScreen`'s bar, `KitFrostHeader`): solid at rest, glass while content is under it, fading over 180 ms.

## Recipe

| Strength | Blur (sigma / CSS) | Saturation | Composer tint | Header tint |
|---|---|---|---|---|
| Weak | 6 / 12 px | 1.2 | 62 % | 72 % |
| Balanced (default) | 10 / 20 px | 1.4 | 70 % | 80 % |
| Strong | 16 / 32 px | 1.6 | 76 % | 85 % (cap) |

- Filter: `ImageFilter.compose(outer: ColorFilter.matrix(saturation), inner: ImageFilter.blur(tileMode: clamp, so the screen edge does not darken it))` inside a `ClipRRect`; `BackdropFilter.grouped` under the page's `BackdropGroup`, so the header and composer share one backdrop read.
- Tint: the page `ground` for both (per theme mode, as the study's paper tint); dark lays 10 % less (`KitFrost.darkTintRelief`) so the shapes behind read as blurred shapes, not a flat grey box.
- Noise: a seeded 64 px alpha tile generated in code (no asset), coloured with the `elevationShadow` role (black) at 3 %.
- Composer: a 1 px top hairline at 20 % (the light surface in light, the light ink in dark, so it shows in both) and a 1 px ink edge at 10 %. Header: a 1 px ink line along its bottom at 12 %.
- Dark: barely visible, as the study, but the blurred shapes still show.
- The glass never clips its child: the composer's running green glow and hue stay outside the pane.

## When it is solid

`KitFrost.strengthOf` returns null (solid) when: the person picks Off (Settings › Appearance › Glass, `GlassChoiceStore`, device key `oc.glass`); Saver; reduced motion (the system's Remove animations or the app's Animations Off); high contrast; a Style that keeps its own floating look (`KitFrost.styleAllows`: Solid and Signal allow it, Soft, Brutal and Paper do not). There is no low-end device guard in the app to reuse.

Performance: the blur layer exists only while content is under the surface (`shown`); at rest no `BackdropFilter` is in the tree. At rest the transcript stops at the band's top edge as before; while the pane is glass it runs on under the pane's own rounded rectangle only, never beside or beneath it.

## The header

`KitScreen` lays a page's bar over its content (`KitFrostHeader.frame`): the content is laid out below the bar as before, painted first, and the bar painted and hit-tested last. A composer layer in the body measures the gap between its top and the bar; when nothing sits between them (no status line, the loading bar idle) and its body opts in, it runs the body up under the bar by that much and reports whether content is above the viewport. A body opts in by adding `KitFrostHeader.topClearanceOf(context)` to its scroll view's top padding; a body that never reads it is never run under the bar, so its first item is never hidden.

## Public API

```dart
class KitFrost extends StatefulWidget {
  const KitFrost({Key? key, required KitFrostRole role, required KitFrostStrength? strength,
      required bool shown, required Color tint, required Widget child,
      BorderRadius borderRadius = BorderRadius.zero, EdgeInsets paneInsets = EdgeInsets.zero});

  static KitFrostStrength? strengthOf(BuildContext context);
  static KitFrostStrength? strengthFor({required KitGlassChoice choice,
      required KitSurfaceStyle style, bool saver, bool reducedMotion, bool highContrast});
  static bool styleAllows(KitSurfaceStyle style);
  static Animation<double>? wellOf(BuildContext context);
}

abstract final class KitFrostHeader {
  static double topClearanceOf(BuildContext context);
}
```

States: none — a surface around its child, it holds no data.

## Tests

`test/kit/kit_frost_test.dart` (strengths, off conditions, fade, well, layer, stored choice), `test/kit/kit_frost_header_test.dart` (run under the bar, opt-in, loading, off, hit testing), gallery `test/goldens/kit/kit_frost_golden_test.dart` (default at both gallery sizes and text 2.0, weak, strong, header, and a whole chat scrolled under both, light and dark).

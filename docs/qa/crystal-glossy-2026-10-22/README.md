# Crystal becomes glossy glass, 2026-10-22

Owner (2026-10-22), about Glass and Crystal: "the glass doesn't look glass enough, no glossy vs frosted variant, we [have] something in between." The two must be clearly different, each pushed to its extreme. Glass becomes **frosted** (a separate branch: `kit_style_glass.dart` and the blur policy in `kit_surface_look.dart`). This branch makes Crystal **glossy**.

Branch `feat/style-glossy` (from `1fa1f40bb`). Concepts: `/home/eslam/Storage2/launch-footage/glass-variants/glossy-{dark,light}/concept.png`. The look was taken from them, not the data.

## What changed

| Piece | File |
|---|---|
| `KitGloss` and `KitGlossDecoration`: the painted gloss. A curved sheen across the top with a crisp foot, a thin line drawn along that foot, a lit top rim, a darker bottom rim, a bevel that darkens towards the rim, and a faint rainbow fringe (a computed spectrum, not a theme colour). The sheen runs diagonally from the lit top start, and it mirrors in right-to-left. Painted, never captured: no backdrop read. The decoration keeps its gloss through the kit's derived looks (`face`, `glassFace`, `withoutCast`, `hosting`) and through a state fade, so a press never flickers it off. | `lib/ui/kit/glass/kit_gloss.dart` (new) |
| Crystal's spec: every pane is glossy (content, control and floating fully; track and sheet at half). The fill is clear and even, with no white top start. The frost is a third of what it was. In light the bevel takes the accent's hue, so the rim of the glass carries colour on a white page. Sheets are thicker (dark .46, light .62). Edges › None keeps the sheen but drops the rims. A part's own fill (an accent button, the mint FAB), code, fields and switches get no gloss. | `lib/ui/kit/style/kit_style_crystal.dart` |
| The lens is nearly clear: blur 1.2 (12 is kept under a menu's thick tint). It is lit from above (specular angle 72°, the bottom arc weaker), with dispersion 6–7, magnification 1.05–1.07 and saturation 1.3–1.4. The lite glass on sheets and dialogs blurs at 10. | `lib/ui/kit/glass/kit_crystal_engine.dart` |
| The live background shows a step stronger under Crystal. `KitLivePolicy.boost` is a multiplier before the cap. Crystal's is 1.6, capped at Bold, the strength where AA through the plate is already measured: Normal shows as Bold, and Subtle shows at 1.12. | `lib/ui/kit/style/kit_style.dart` (policy field), Crystal's tokens |
| Capture tool: `CRYSTAL_PACK`, `CRYSTAL_ACCENT`, `CRYSTAL_LIVE` and `CRYSTAL_STYLE` switches, plus an Appearance screen | `tool/capture/crystal_screens_test.dart` |

`kit_live_background.dart` is not touched. Two things there are not reachable from a style's tokens:
- the light-mode palette strength;
- Glass's "light across the middle of the page", which is hard-coded to `KitSurfaceStyle.glass`.

The boost lifts the look in both modes alike. A light-only lift needs a hook from the background's owner.

## Proof

Emulator `emulator-5584` (host GPU, Impeller OpenGLES through ANGLE), release APKs 2610–2613. Images are in `/home/eslam/Storage2/launch-footage/progress/glossy/`, as `passN-<screen>-<default|midnight>-<light|dark>.png`, and each pass is logged in `LOG.md`.
- `default` is Graphite over the aurora.
- `midnight` is Midnight with the Blue accent over Light rays.

Desktop shader-path renders come from `flutter test --enable-impeller tool/capture/crystal_screens_test.dart`.

## Frame timing

Emulator, SurfaceFlinger present intervals, 3×3 swipes on Conversations, default theme, dark:

| Style | Mean | p90 | > 25 ms |
|---|---|---|---|
| Solid | 18.2 ms | 18.8 | 4 % |
| Glass | 35.2 ms | 39.2 | 91 % |
| Crystal, glossy (2611) | 35.4 ms | 41.1 | 93 % |
| Crystal, glossy (2613, Midnight) | 35.8 ms | 41.6 | 97 % |

On this emulator any backdrop read halves the frame rate. The painted gloss costs nothing measurable beyond the lens and blur Crystal already had. Phone numbers are pending.

## Tests

- `test/kit/style/crystal_test.dart` gains these groups:
  - "the live background": the boost, its cap, and the other styles left unchanged;
  - "the gloss":
    - every pane is glossy, with a quieter sheet and track;
    - the fill is even;
    - no gloss on code, a field, a switch, the ground or a part's own fill (the FAB);
    - Edges › None;
    - the gloss is kept through the derived looks and a state fade;
    - it paints in both directions.

  The lens bounds now expect a near-clear lens. The light-pane test expects no white top start.
- Goldens: every `*_crystal_*` kit golden is regenerated. Ten `kit_surfaces_appearance_*` goldens and `settings_appearance_loaded_1280x800_dark` change only because the Style picker's Crystal card is now glossy.
- G5 (text contrast) stays green on the transcript gallery. The sheen was first a vertical gradient, so each row of the band was one colour, and that row outnumbered the words' own pixels in the framework's read. The diagonal sheen fixed it.

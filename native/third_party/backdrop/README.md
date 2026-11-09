# backdrop (vendored fork)

**Upstream:** [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), library `backdrop`, tag
`2.0.1` (Maven `io.github.kyant0:backdrop` 2.0.1, published 2026-08-26). Licence: Apache License 2.0, copied unchanged
in [LICENSE](LICENSE). Copyright 2025 Kyant.

**Sources:** `src/` holds the `commonMain` and `androidMain` sources of `backdrop-android-2.0.1-sources.jar` from Maven
Central, byte for byte (CRLF line endings kept), in the commit "vendor Kyant0 backdrop 2.0.1 sources unchanged". The
published AAR's SHA-256 is `009744ad39a09c886bb40261c2c3dbbb0094ec0ef680800af7d5600d37db7597`. The desktop
(`skikoMain`) sources are left out: the kit draws liquid glass on Android 13+ only.

**How it is used:** `settings.gradle.kts` substitutes this module for `io.github.kyant0:backdrop` wherever that
artifact resolves. `:ui:kit` keeps declaring the library it always used (the only module `ExternalRules` lets declare
it) and gets this fork. Its own dependencies (Compose foundation/ui/ui-graphics at the app's Compose version,
`io.github.kyant0:shapes` 1.2.1, `org.jetbrains:annotations` 26.1.0) are the published module's.

## What changed, and why

Two changes, marked `OC fork` in the code.

**Compose 1.13:** `androidMain/.../internal/Paint.kt` reads the framework paint through `Paint.nativePaint` instead of
`asFrameworkPaint()`, which Compose 1.13 (the app's version) deprecates at error level. Same object, same behaviour.

**Half resolution:** `drawBackdrop` / `drawPlainBackdrop` take `resolutionScale` (default 1:
upstream's behaviour, line for line).

- Files: `DrawBackdropModifier.kt` (the parameter, the node's `resolutionScale`, the layer's size, the scaled
  recording and the scaled draw, `scaledExtent`) and `BackdropEffectScope.kt` (`update` scales density and size).
- What it does: the node's backdrop layer (the backdrop, then the colour controls, blur and lens effects) is recorded
  at `resolutionScale` of the node's pixels per side, and drawn scaled back up (GPU bilinear filtering). The effects
  see the layer's own size and a density scaled with it, so a blur of 7 dp and a lens rim of 12 dp are the same dp on
  the smaller layer. The highlight (rim light), the shadow and everything `onDrawSurface` / `onDrawFront` draw (the
  kit's tint, sheen and specular) are not in that layer and stay at full resolution, as does the node's outer clip.
- Why: smoothness 2026-11-09 (docs/native/qa/smoothness-2026-11-09) measured Glass about 3 ms over the 8.3 ms frame
  of a 120 Hz Galaxy S24; the liquid panes' backdrop layers (an offscreen pass each for the colour, blur and lens) are
  the cost that stayed. At half resolution each pane's backdrop costs a quarter of the pixels. The blur already hides
  the detail the smaller layer loses, so the kit uses it only for panes frosted at least 4 dp (bars, the message box,
  sheets, menus; `liquidResolutionOf` in `:ui:kit`); the lightly frosted chips and the + stay at 1.
- Proof: the before/after frame numbers and the side-by-side screenshots (full and half resolution, same device, same
  scale) are in docs/native/qa/smoothness-2026-11-09/round-2.md.

## Updating

To take a new upstream release: replace `src/` with the new sources jar's `commonMain` and `androidMain`, commit that
alone, then re-apply the `OC fork` change (it is small: search for `resolutionScale`), and update the version and
hashes here. If upstream gains its own resolution control, drop the fork and go back to the published artifact.

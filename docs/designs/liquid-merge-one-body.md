# Fluid glass merges as one body of glass

**Status:** v1 draft, 2026-11-11. Not approved. Nothing in the kit has changed.

**Why now:** on 2026-11-10 the owner called the first fluid merges (the menu out of the bar, the jump pill out of the
message box) a first pass whose neck "needs more work". On 2026-11-11 they called the split-and-merge demo in
Kyant0/AndroidLiquidGlass issue #104 "amazing" and said "Do it".

**Reference:** the 5 s clip in that issue (a round button melts into a capsule and the two become one longer capsule,
over scrolling text), and its stated method: a smooth minimum of the shapes' distance fields whose strength falls as
the two shapes' edge directions line up, with both shapes drawn as one glass.

**Goal:** while a piece of glass leaves or joins another, the two are one body of glass on screen: one outline, one
bend of the content along that outline, one rim light. When they part, the liquid thins to a neck at the place the
piece came from and lets go there.

**Moving prototype (a spike, not app code):** `docs/designs/liquid-merge-one-body/merge-menu.mp4` and
`merge-pill.mp4`. Left half: today's method. Right half: this proposal. Both are drawn by
`docs/designs/liquid-merge-one-body/proto.py`, which holds the formulas below in numpy so they can be judged in motion
before any kit work. It is not the app: the page behind is a stand-in, and the lens is a close copy of the library's.

## Non-goals

- New places that merge. The same two stay: a menu opened from a control on the bar, and the jump pill on the message
  box.
- Any change to glass at rest. A pane that is not leaving or joining draws exactly as today.
- A general "glass container" for any number of shapes (the reference issue's larger idea). Two shapes at a time.
- Changes to the vendored `backdrop` library.
- Desktop parity of the look. Desktop keeps drawing the same shader so tests can render it, but only Android is judged.

## What is wrong today

`KitLiquidMerge.kt` draws a bridge between two panes that keep drawing themselves:

1. **Three pieces, not one.** Each pane keeps its own lens and its own rim light, so during a merge the bar's rim
   crosses the menu and the menu's outline shows inside the bar. The bridge between them is blurred and tinted but has
   no lens, so the content bends in the panes and not in the neck.
2. **The blend has one strength everywhere.** The smooth union uses a constant reach (40 dp at full pull). It swells
   the outline wherever the two shapes are near, including where one is still inside the other.
3. **Flat edges part as a film.** The bar's bottom edge and the menu's top edge are parallel. A smooth union between
   parallel edges is a film as wide as the menu, which thins evenly and then vanishes in one frame. There is no neck.

## The proposal

### 1. One distance field

For a pixel `p`, `da` and `db` are the signed distances to the body and to the drop (rounded boxes, as today), and
`ga`, `gb` their outward directions.

```
facing = clamp(0.5 - 0.5 * dot(ga, gb), 0, 1)     // 1 where the shapes face each other, 0 where they point the same way
k      = reach * pull * facing * hold(p)
h      = max(k - abs(da - db), 0) / k
d      = min(da, db) - h * h * k / 4               // the one body: inside where d < 0
```

`facing` is the reference's idea. A drop still inside its body no longer swells the body's outline, and the concave
corners where the two outlines cross get a fillet.

### 2. The liquid lets go at one place

`hold(p)` is 1 everywhere at full pull and closes in on the point the drop came from as the pull falls:

```
r    = holdRadius * pull / (1 - pull)
hold = exp(-distance(p, origin)^2 / r^2)
```

The origin is known in both cases: the centre of the control that opened the menu, and the point on the message box's
top edge under the pill's centre. With it, two flat edges part as a curtain that draws back to one neck, which thins
and breaks at the origin. `holdRadius` starts at 24 dp.

### 3. One lens, one tint, one rim light

While a drop's pull is above 0, the merge layer draws the whole body `d < 0`, not only the bridge:

- the page behind it through the body pane's colour controls and blur, then bent inside the rim with the library's own
  formula (`circleMap(1 - depth / rim) * refraction` along the direction of `d`), where the direction is the gradient
  of the one field `d`;
- the body pane's tint over it;
- one rim light along `d = 0`, brightest where the edge faces the top start and the bottom end, as the library's
  highlight.

For that time the two panes do not draw their own faces (backdrop, tint, rim light). They keep drawing their content:
words, icons, the message field.

### 4. Hand-over at rest

At pull 0 the field is exactly `min(da, db)`: two separate shapes. The merged drawing and the panes' own faces must
look the same at that moment, or the glass pops when a merge starts or ends.

- The rim, refraction, blur, colour controls and tint are read from the same `LiquidRecipe` the pane uses.
- The merged face cross-fades with the panes' own faces over the first and last 80 ms of the pull.
- The two panes can have different recipes (the bar 6 dp blur, a menu 12 dp; the message box 4 dp, the pill 2 dp).
  The merged body uses the body pane's recipe for both and eases to each pane's own during the cross-fade.

## How it is built

| Piece | Change |
|---|---|
| `KitLiquidMerge.kt` | `KitLiquidDrop` gains `origin` (window px). `MERGE_SHADER` becomes the one-body shader: the field above, and three outputs (cover, lens offset, rim light). `KitLiquidMergeLayer` draws the body through a runtime-shader render effect with the blurred page as its content. |
| `KitPlatform` | One new call, `liquidBodyEffect(program)`: the compiled program as a render effect with a `content` input. Android: `RenderEffect.createRuntimeShaderEffect` (13+). Desktop: `ImageFilter.makeRuntimeShader`. Same pattern as today's `liveFinish`. |
| `KitLiquidGlass.kt` (`kitLiquidFace`) | A pane that is a merge body or a drop reads "merging" and hands its face to the merge layer for that time (the cross-fade above). |
| `KitMenu.kt`, `KitJumpPill.kt` | Set the drop's `origin`. No change to their motion. |
| Tests | `KitLiquidGlassTest`-style desktop tests: the field is `min(da, db)` at pull 0; a drop inside its body leaves the body's outline unchanged; the cover is one connected region while the neck holds and two after it breaks; panes draw no face while merging and their own face at rest. A golden of three moments of each merge. |

Module: `:ui:kit` only (plus the two-line origin in `KitMenu` and `KitJumpPill`, same module). One owner.

## Cost

Today's bridge draws a box around the two shapes once per frame while pull > 0. The proposal draws the same box with a
heavier shader (two box distances, four more for the gradient, one content sample) and removes two pane faces for that
time. It runs only during the roughly 300 to 450 ms of a merge. The check is the existing `GlassBenchmark` menu path on
the Galaxy S24: the share of frames more than one vsync late must not rise against today's build. If it does, the body
is drawn at half resolution like the frosted panes.

## Fallbacks (unchanged)

No runtime shaders (below Android 13), reduced motion, battery saver, the Frosted look, or a style other than Glass:
no merge is drawn and the pieces move as they do today.

## Open questions

1. Does the cross-fade at the start and end hide the hand-over on the phone, or is a closer match of the lens needed?
2. A menu is much larger than the bar. Should the merged body take the bar's recipe (clearer) or the menu's (more
   frosted) while they are joined?
3. Is 24 dp the right `holdRadius`, and should the neck break earlier than the menu settles?

## Acceptance

- Recorded on the emulator and on the owner's phone, next to the reference clip: the menu out of the bar and back, the
  pill out of the message box and back, light and dark, over a moving transcript.
- No pane outline is visible inside the merged body at any frame.
- The neck breaks at the origin; no film, no line left behind.
- No visible pop at the start or the end of a merge.
- The benchmark check above.

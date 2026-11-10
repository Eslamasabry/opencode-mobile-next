# Joined glass: a menu and its bar, a pill and its box, as one body

**Status:** v3 draft, 2026-11-11 (v2 plus the owner's material and shape directions of the same night). Not approved. Nothing in the kit has changed. v1 is in git history (b22b4ccf68).

**The owner's words:**
- 2026-11-10: the first fluid merges are a first pass; the neck "needs more work".
- 2026-11-11, on the split-and-merge demo in Kyant0/AndroidLiquidGlass issue #104: "that's amazing", then "Do it".
- 2026-11-11, after the first prototype clips: "Don't separate them keep them as one glossy glass body maybe with a
  buldge?"
- 2026-11-11, on the prototype stills: "It still feels so washed off in general. Not vibrant or live." "I think we
  added too much blur." "Cut the blur and leave the saturation as is. On the shape itself you need to geometrically
  craft it as a marvel." Earlier the same night, on a rim that picks up passing content: "Yes I like the alive part".

**Goal:** while a menu is open from a control on a piece of glass, or the jump pill is shown on the message box, the
two are one body of glossy glass: one outline with a soft swell where they meet, the content bent along that one
outline, one rim light. They do not let go of each other.

**Reference:** the clip in issue #104 and the ShaderToy behind it (s3XSzM, by @preyneyv), which the owner pasted in
full. Its method is used; its text is not copied (ShaderToy code is non-commercial share-alike unless stated, the app
is MIT).

**Moving prototype (a spike, not app code):** `liquid-merge-one-body/joined-menu.mp4` and `joined-pill.mp4`, drawn by
`liquid-merge-one-body/proto2.py` (numpy; 2 px = 1 dp; four times slower than real); the crafted outline and the cut blur are in `shape.py` (rules and curvature check), `craft.py` and the two `shape-*-crafted.png` stills. Left half: today's kit. Right
half: this design: the field of section 1, each pane drawing its own side with its own frost, rim, bend and tint mixed
per pixel, the pieces moving on the kit's release spring. The page behind is a stand-in and the lens is a close copy
of the library's, so it shows the shape, the outline and the motion, not the final look of the glass.

## What v2 changes from v1, and why

Three reviews (scope, design, build-readiness) and the owner's direction changed the design:

| v1 | v2 | Why |
|---|---|---|
| The pieces let go through a neck | They stay joined while shown | The owner's direction. It also removes the pull spring, the hold function and both cross-fades, which the reviews found unbuildable as written. |
| One merge layer under the chrome draws the whole body | Each pane draws its own side of the body, in its own node | The pill's words are under that layer, a menu is drawn in a layer above the page, and the two read different pictures of the page. One layer cannot be the face of both. |
| Panes "stop drawing their faces" | A joined pane is drawn by a kit lens that knows its partner | The library's node always draws its backdrop, clips to its own shape and lights its own outline. There is nothing to switch off. |
| The body's recipe for both shapes | Rim, bend and tint are mixed per pixel between the two panes' own | A 22 dp rim on a 32 dp pill would lens to its middle, which the owner rejected on 2026-11-08. |
| Direction of the edge by differences | Exact directions, mixed, not normalised | Cheaper, no seams, and the bend fades in the junction by itself (the reference does this). |

## Non-goals

- Glass at rest. A pane with no partner is drawn by the library exactly as today.
- New places that join. The three that exist stay: a menu from a control on the bar, a menu from a chip on the message
  box, the jump pill on the message box.
- More than two drops on one body, or drops joining each other.
- Menus drawn in a separate popup window (outside a `KitSheetHost`): they stay unjoined.
- Changes to the vendored `backdrop` library.
- The reference's colour fringe (the owner's glass has no dispersion) and its edge reflection (see "Later").

## 1. The field

Pure maths, in `:ui:design` as `LiquidJoin`, with the shader text built from the same constants.

For a pixel `p`: `a` is the pane drawing, `b` its partner. Each shape gives its signed distance and its outward
direction together (a rounded box; the direction is the library's softened one, corner radius × 1.5, so a pane alone
bends exactly as the library bends it).

```
facing = (1 - dot(ga, gb)) / 2                       // 1 where the shapes face each other, 0 where they point the same way
inside = s(da) * s(db)                               // fades the blend out deep inside either shape
         where s(d) = 1 - smoothstep(0.5 m, m, -d),  m = min(corner radius, half the short side)
k      = max(reach * facing * inside, 0.001)
h      = max(k - abs(da - db), 0) / k                // 0 far from the junction, 1 on the line da = db
d      = min(da, db) - h * h * h * k / 6             // the joined body: inside where d < 0 (a CUBIC kernel)
m      = h * h / 2                                   // 0 far away, 0.5 on the line da = db
w      = (da <= db) ? m : 1 - m                      // the partner's share at p
g      = mix(ga, gb, w)                              // the body's direction, NOT normalised
```

- `facing` is the reference's trick: a drop still inside its body does not swell the body's outline.
- `inside` removes the step the direction makes on a shape's centre line (design review, finding 5).
- `g` shrinks where the two directions oppose. Cover and rim light use `d / max(length(g), 0.05)`; the bend uses `g`
  as it is, so the bend vanishes on the junction line instead of tearing there (finding 4, and the reference).
- The kernel is cubic, not the reference's quadratic: the fillet then meets both edges with matching curvature, so
  the rim light runs through the join without a kink. Measured on the prototype's outlines (`shape.py`), the largest
  step of curvature along the join is 3 times smaller for the menu and 6 times smaller for the pill.
- `reach` is not tuned by eye: it is solved for the fillet radius the shape rules below ask for (134 dp for the menu
  on the bar, 56 dp for the pill; both large because `facing` and `inside` scale it down). With no partner, or
  `reach` 0, `d = da` and `g = ga`: the library's own shape.

### The shape rules

The owner asked for the joined outline to be crafted geometry, not whatever a blend gives. The rules:

1. **Convex corners are the kit's own arcs.** A pane must not change shape at the moment it joins.
2. **One radius per join.** The tightest radius of every concave fillet equals a convex radius beside it: 24 dp for a
   menu (its corner radius), 16 dp for the pill (its end radius). Each flank is then an S-curve of two equal radii.
3. **Aligned flanks are one line.** A menu opened from a control at the bar's end has its outer edge on the bar's
   outer edge, and its field reaches up to the bar's centre line (inside the bar, so it changes nothing else). The
   bar's end cap then runs straight down into the menu's side, tangent, with no waist.
4. **The pill is a capsule sunk a quarter of its height** (8 dp of 32) into the message box, so it reads as a drop
   rising out of the box and not a tab laid on it.
5. **Curvature is checked, not assumed:** `LiquidJoinTest` measures the curvature along the outline of each rest
   layout and fails on a step larger than the measured values above plus a margin.

### The material

- Saturation stays 1.6 (the owner: "leave the saturation as is").
- The blur is cut. Proposed, to be confirmed on the phone before `LiquidEdge`'s recipes change: bar 6 to 3 dp, message
  box 4 to 2 dp, menu 12 to 6 dp, pill and chips 2 to 0.5 dp. This is a change to glass at rest and is its own
  commit, separate from the join.
- The rim that picks up passing content (the reference's edge reflection) is liked as an idea and is its own later
  step: it needs the page outside the pane as an input to the rim pass.
- No colour fringe.

## 2. The joined pane

A new kit node, `kitLiquidJoinedPane` (in `effects/KitLiquidJoin.kt`), draws a pane's face while it has a partner.
`kitLiquidFace` returns it instead of the library's pane when the pane is registered as a join body or drop, its
partner is present, and both faces are granted liquid. Otherwise nothing changes.

What it draws, in the pane's own node, behind the pane's content, over an area grown by `reach` on each side (the
node does not clip to the pane's shape):

1. **Backdrop and bend**, at the pane's liquid resolution (half on Android for panes frosted 4 dp or more, as today):
   the pane's own backdrop source, its colour controls and its blur, then a runtime-shader effect that samples it at
   `p + bend * g`, where `bend = circleMap(1 - depth / rim) * refraction` is the library's formula with
   `depth = -d / max(length(g), 0.05)`, and `rim` and `refraction` are `mix(own, partner's, w)`.
2. **Cover**, at full resolution: the result of 1 is kept only where `d < 0` and `da <= db` (this pane's side). The
   partner draws the other side from its own node. The two sides meet on the line `da = db`, where every mixed value
   is the same from both sides.
3. **Tint**, at full resolution: the pane's own face tint (`spec.fill`, `fillStart`, sheen, with its ink flip) mixed
   to the partner's by `w`.
4. **Rim light**, at full resolution: one line along `d = 0` on this pane's side, with the library's highlight
   formula on the direction `g`, its colour mixed by `w`.

The pane's touch light and picture rim light, which are drawn in the pane's own shape, are multiplied by `1 - 2w`, so
they fade out towards the junction and never draw the pane's old outline inside the body.

**The blur is not mixed.** A bar frosts 6 dp and a menu 12 dp (6 dp in light themes); the message box 4 dp and the
pill 2 dp. Each side keeps its own. Whether the change of frost across the junction line shows is the first thing the
spike must answer (open question 1).

**No hand-over animation.** A drop appears inside its body, where `facing` is 0 and the body's outline is unchanged,
so switching the body from the library's pane to the joined pane at that moment changes no pixel, provided the two
draw a lone pane the same. That is acceptance check 1.

### Who draws what, and from which picture

| Pane | Its node (unchanged) | Backdrop source (unchanged) | Draws |
|---|---|---|---|
| Bar | `KitScreen`'s top block | the transcript recording | its side, under the bar's content, above the status edge as today |
| Menu from the bar | the host's menu layer | the page recording | its side, under the menu's rows |
| Message box | `KitScreen`'s bottom block | the transcript recording | its side, under the field and chips |
| Jump pill | inside the body, over the list | the transcript recording | its side, under its label |
| Menu from a chip | the host's menu layer | the page recording | its side |

`KitLiquidMergeLayer`, its bridge and its blur layer are deleted.

### Wiring

- `KitLiquidMergeBody` and `KitLiquidDrop` (kept, renamed `KitLiquidJoinBody` / `KitLiquidJoinDrop`) each carry,
  as snapshot state written in the layout or draw phase and read in draw: `rect` (window px), `radius` (px), the
  pane's `LiquidRecipe`, and its current face tint and rim colour. `pull` and `origin` are removed.
- A body holds at most two drops; a third is not joined.
- `kitLiquidFace(role, source, shape, transform, chosen)` gains `join: KitLiquidJoinPart? = null`. Call sites that
  pass it: the bar (`KitScreen`), `KitMenuPanel` (`KitMenu`), the pill (`KitJumpPill`), and the message box
  (`KitComposerBox`, in `:ui:kit-chat`).
- `KitPlatform` gains `liquidJoinEffect(program: LiveShaderProgram): RenderEffect?`, called in the draw phase after
  the uniforms are set; it builds a new effect each frame (Android `RenderEffect.createRuntimeShaderEffect`, desktop
  `ImageFilter.makeRuntimeShader`), content input named `content`. Null (below Android 13) means no joined pane.
- One shader source with a `mode` uniform: 0 bend (used through the effect), 1 cover, 2 rim light (both as a brush).

### Motion

The kit's motion for the menu (grow from 55 % about the control, fade, 8 dp slide) and the pill (rise on the release
spring) is unchanged, with two exceptions:

- the menu's rest position: its top edge overlaps the bar's bottom edge by 4 dp (for a chip menu, the message box's
  top edge), and its outer edge sits on the body's outer edge when the control is at that end (shape rule 3);
- the pill's rest position: sunk 8 dp into the message box (today it rests 8 dp above it; shape rule 4).

The menu's own "frost while it grows" transform no longer applies to a joined menu: the field follows the growing
box each frame, so the lens is never misaligned.

## 3. Build order

Each phase ends with a recording for the owner, on the emulator and then the phone.

| Phase | What | Gate to go on |
|---|---|---|
| 0. Spike (labelled, may be thrown away) | The joined pane drawing the bar alone, no partner | Acceptance check 1: it matches the library's bar |
| 1. Pill and message box | The field, the node, both panes, the pill's rest position | The owner's eye on the recording; the benchmark |
| 2. Menu and bar | The menu layer side, the menu's rest position | Same |
| 3. Chip menu and message box | The second drop on one body | Same |

Modules: `:ui:design` (the maths), `:ui:kit` (node, platform call, menu, pill, screen), `:ui:kit-chat`
(`KitComposerBox`, one argument), `:androidApp` benchmark source set and `:benchmark` (proof scenes). One owner.

## 4. Tests

- `LiquidJoinTest` (`:ui:design`, common): `d = min(da, db)` at reach 0; a drop inside its body leaves the body's
  outline unchanged; `d` has no step anywhere across both boxes (sampled on a 1 px grid); the cover is one connected
  region for the three rest layouts; every mixed value is equal from both sides on `da = db`.
- `KitLiquidJoinShaderTest` (`:ui:kit`, desktop): the compiled shader's cover agrees with `LiquidJoin` at 200 sample
  points (desktop compiles the same text through `DesktopLiveShader`; the test platform is given `liveShaders`).
- `GlassShots` (Android, Test Lab and emulator): the bar drawn by the library and by the joined pane with no partner,
  same build (`--es liquid join`); the two PNGs differ by at most 2/255 per channel on 99.5 % of the bar's pixels.
- Kit tests: a pane with a partner that is frost, veil or beyond the budget stays the library's pane.

## 5. Cost

A joined pane replaces the library's pane one for one: one backdrop layer at the same resolution, one lens pass, two
small brush passes for cover and rim light. The pill is shown for as long as the reader is scrolled up, so this cost
is steady, not a short burst.

The check, on the Galaxy S24 in Test Lab, same method as the smoothness rounds:
- two new scenes in `GlassProofActivity`: `join-pill` (the chat scrolling with the pill shown) and `join-menu` (the
  bar's menu opened and closed eight times);
- baseline first, on the commit before phase 1; `FrameTimingMetric`, 5 iterations each;
- pass: the median run's share of frames more than one vsync late rises by no more than 1 point, and frame overrun
  P90 by no more than 0.5 ms.

If it fails, the cover and rim passes are folded into one, then `reach` is cut.

## 6. Fallbacks

Joined only when both panes are granted liquid faces: Glass style, a look with a lens, Android 13 or newer, motion
not reduced, not battery saver, within the window's liquid budget. In every other case (the Frosted look, a veiled
pill, a budget-held pane, a popup-window menu) the pieces are drawn and moved exactly as today, without today's
bridge.

## Open questions

1. Does the change of frost across the junction line show (bar 6 dp, menu 12 dp)? If it does, the drop's blur ramps
   to the body's inside the junction with a second blurred input.
2. With the pill nearly clear (0.5 dp), "Jump to latest" sits on readable text. Is 1 dp the floor for a pane that
   carries a label?
3. The pill lies inside the transcript recording that the message box's glass reads (design review, finding 1), so
   the box's side could show the pill's own label bent in it. To be checked in phase 1; if so, the pill is drawn
   outside that recording.

## Acceptance

1. A lone bar drawn by the joined pane matches the library's bar (the `GlassShots` check above).
2. On a recording stepped frame by frame: no outline of either pane inside the body, no line along the junction,
   no change of the body pane at the frame a drop appears or goes.
3. The swell is judged by the owner on the phone, next to the reference clip, in light and dark, over a moving
   transcript.
4. The benchmark check in section 5.

## Later

The reference lights its rim with bright content from just outside the glass (a bloom of the page sampled along the
outward direction). The kit tints its rim from the picture behind. Worth a side-by-side once the joined body is in.

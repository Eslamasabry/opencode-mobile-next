# Glass v3: crisper glossy glass, room inside the panes, real glass over the live looks. Emulator proof, 2026-11-09

Owner direction (2026-11-09): "Crisper glossy glass with good inside padding to let glass effects show."
Branch `native/glass-v3`, landed on `native/next` (see the commit list below). Emulator OC_API35 (API 35,
swiftshader), the liquid-glass proof server (OpenCode 2.2.6, `liquid-glass-qa` data), Graphite theme, Glass style,
Glass edges Liquid, Appearance Auto (dark and light by the system night mode).

- **Before** = `native/next` 8c3c3fc0d (after glass-panes, before this work), hosted run 42528551616.
- **After** = `native/glass-v3` a1a804826, hosted fast-lane run 42530473882 (compile, tests, APK green).

Both sets are taken over **the same two backgrounds**: the Painted aurora (Glass's own look) and Aurora drift (a
shader look). Aurora drift moves, so its two shots of a screen are a few minutes of drift apart.

## What changed

**Crisp**
- In-flow (clear) panes: a sharp 1 dp white rim, brighter at the top start (dark .24 to .07, light .60 to .16), with
  no long shadow halo and no soft glow inside the top edge (`GlassSpec.build`, clear path only; today's thick panes,
  the held and Frosted path, keep Flutter's look exactly).
- Liquid panes (bars, message box, pills, sheets, menus): the rim light is 1 dp with no blur (`Highlight` blur 0,
  falloff 1.6 so its lit arcs gather), and no pane casts a shadow (`LiquidEdge.recipe`).
- Moderate blur so colour and shapes behind stay visible: content panes 12 dp (was 24), chips, controls and fields
  10 dp (was 20); the frost's 1.7x saturation carries the vibrancy.

**Glossy**
- A stronger top sheen (dark .10, light .34), capped at 40 dp down a tall pane so the gloss stays at the top.
- A crisp specular on every glass face, in-flow and liquid alike (`drawGlassSpecular`): a bright segment of the rim
  round the top-start corner (fading within 120 dp along both edges) and a thin glint line 2.5 dp inside the top
  edge, brightest a quarter of the way along and gone at both ends. Restrained: white at .55 dark / .90 light on the
  segment, 60 % of that on the glint.

**Inside padding** (Glass only, through the style tokens: `StyleTokens.paneInsetDp` 6 dp, `paneCornerBoostDp` 4 dp;
every other style 0 and unchanged, tested)
- Row groups, cards (`KitSurface` with padding), sheets, menus and the message box keep 6 dp more inside.
- Pane corners grow 4 dp with it (`KitShapeId.shape`, `kitSurfaceLook`, `StyleResolver.paneCorner`), not under Sharp.
  A chosen row inside a group stays concentric (group corner less the inset less its own inset).
- The sheen is measured at a card's first line, below the inset, where it has mostly faded (`GlassFinish.sheenAt`).

**Real glass over the shader looks** (was problem 1: in-flow panes drew the solid page colour under their tint)
- Under Glass, where panes blur (Android 12+, not held by reduced motion or high contrast), a shader look is recorded
  into the glass backdrop (`rememberLiveShaderBackdrop`): ground plus the shader at half resolution into one offscreen
  layer, scaled up, exactly as the look's own layer drew it. The page shows that recording and every pane (in-flow
  and liquid) blurs the same one; the look's separate layer is not drawn. One shader render per frame.
- Only while the look moves: the shader reads the clock in the recording, so a still look (Calm motion, reduced
  motion, Saver, a resting clock after 30 s without a touch) records nothing.
- The picture is recorded on a node with its own layer beside the content, so a step re-records the picture alone,
  never the page (the painted aurora's 6 steps a second benefit too).

**Transcript behind the chips** (was problem 4): a soft fade of the ground under the chips strip over the message box
only (`kitLiquidChipsFade`, .86 at its core, feathered 14 dp), on the approval/model strip and the phrase row. The
message box stays glass over the transcript.

## Renders (one phone screen each; `before-*` and `after-*` share the background named in the file)

| Screen | Painted aurora | Aurora drift |
|---|---|---|
| Settings › This app | `{before,after}-painted-settings-this-app-{dark,light}` | `{before,after}-drift-settings-this-app-{dark,light}` |
| Settings home | `{before,after}-painted-settings-home-{dark,light}` | `{before,after}-drift-settings-home-{dark,light}` |
| Conversations list | `{before,after}-painted-list-{dark,light}` | `{before,after}-drift-list-{dark,light}` |
| Project sheet, All projects chosen | `{before,after}-painted-project-sheet-{dark,light}` | `{before,after}-drift-project-sheet-{dark,light}` |
| Chat with the composer | `{before,after}-painted-chat-{dark,light}` | `{before,after}-drift-chat-{dark,light}` |
| Approval menu open | `{before,after}-painted-menu-{dark,light}` | `{before,after}-drift-menu-{dark,light}` |

## Contrast (B-KIT-003, `GlassPanesTest`, every theme pack, light and dark)

Each clear pane (content, control, track, field; chosen content and control) is measured over two backdrops: the
painted aurora's spots under the lighter blur (`AuroraImage.clearSpotsOf`, the frosted spots moved 25 % towards the raw
ones), and the farthest any shader look can move the ground (`LiveSpots.of`: `liveOut`'s cap, .20 light / .30 dark,
along each look colour and each pair, unblurred, so conservative). Text1/2/3 at 4.5:1 (or Solid's own) and icons and
track edges at 3:1, with the sheen where the words lie; a clear pane now thickens for its icons too
(`GlassFinish.paneInks`). No pane falls back to opaque. Pass, every pack, light and dark (fast lane 42530473882).

992 measured panes.

| Pane | Over | Mode | Tint, all packs | Opencode | Gloss kept | Least margin |
|---|---|---|---|---|---|---|
| Content | aurora | dark | 16-16 % | 16 % | 2/62 | 0.02 |
| Content | aurora | light | 21-21 % | 21 % | 62/62 | 0.13 |
| Content chosen | aurora | dark | 25-25 % | 25 % | 4/62 | 0.01 |
| Content chosen | aurora | light | 32-32 % | 32 % | 62/62 | 0.13 |
| Control | aurora | dark | 20-20 % | 20 % | 0/31 | 0.13 |
| Control | aurora | light | 23-23 % | 23 % | 31/31 | 0.00 |
| Control chosen | aurora | dark | 30-30 % | 30 % | 0/31 | 0.38 |
| Control chosen | aurora | light | 34-34 % | 34 % | 31/31 | 0.13 |
| Field | aurora | dark | 18-18 % | 18 % | 0/31 | 0.20 |
| Field | aurora | light | 23-23 % | 23 % | 0/31 | 0.14 |
| Track | aurora | dark | 14-14 % | 14 % | 0/31 | 0.25 |
| Track | aurora | light | 20-20 % | 20 % | 31/31 | 0.12 |
| Content | shader | dark | 48-79 % | 56/60 % | 0/62 | 0.00 |
| Content | shader | light | 39-65 % | 40 % | 62/62 | 0.00 |
| Content chosen | shader | dark | 48-73 % | 56 % | 0/62 | 0.00 |
| Content chosen | shader | light | 64-96 % | 66 % | 62/62 | 0.00 |
| Control | shader | dark | 56-80 % | 64 % | 0/31 | 0.00 |
| Control | shader | light | 67-98 % | 75 % | 31/31 | 0.00 |
| Control chosen | shader | dark | 50-74 % | 56 % | 0/31 | 0.00 |
| Control chosen | shader | light | 64-96 % | 66 % | 31/31 | 0.00 |
| Field | shader | dark | 51-79 % | 57 % | 0/31 | 0.00 |
| Field | shader | light | 39-65 % | 40 % | 0/31 | 0.00 |
| Track | shader | dark | 47-75 % | 55 % | 0/31 | 0.00 |
| Track | shader | light | 39-65 % | 45 % | 31/31 | 0.00 |

Over the painted aurora the clear panes stay at their start tints (14-34 %). Over a shader look the measure is the
look's hard cap, unblurred, so the panes thicken to 39-98 % (Opencode 40-75 %): the look shows through, but these are
not the 14-24 % panes. Next step to thin them: measure each look's darkest and brightest spot under the 10-12 dp
blur (desktop renders of every look), and replace the cap bound with per-look spots.

## Cost of the shader backdrop (emulator, swiftshader: software GPU, so absolute frame times are not a phone's)

Conversations list, light, Glass, Aurora drift, `dumpsys gfxinfo` over 10 s:

| | Frames, animating | p50 / p90 / p99 frame time | Frames while still (clock at rest, 10 s) |
|---|---|---|---|
| Before (look on its own layer, panes solid under their tint) | 150 | 133 / 150 / 150 ms | 0 |
| After (look recorded once into the glass backdrop, every pane blurs it) | 126 | 150 / 150 / 200 ms | 0 |

- Still costs nothing: with the clock at rest (30 s after the last touch, or Calm / reduced motion / Saver) no frame
  is drawn and nothing is recorded, before and after.
- Moving: the shader still runs once per frame at half resolution (one offscreen layer); what is new is each pane's
  blur of it per frame. Under swiftshader that is about +13 % at p50 (CPU-rendered blur); a phone's GPU blur is far
  cheaper. Real-phone numbers are still to measure (the batcher's APK on the owner's phone).

## Not done / known

- Liquid faces over arbitrary content (problem 2) and the very dark Painted aurora in dark mode (problem 3) were not
  in this direction and are unchanged.
- The full lane's Glass goldens will differ (a deliberate look change); re-record with the goldens lane.
- The fast lane's `ui/gate` AlignRulesTest / AlignSpecFileTest fail on icon sizes (16/28 dp glyphs); not touched here.

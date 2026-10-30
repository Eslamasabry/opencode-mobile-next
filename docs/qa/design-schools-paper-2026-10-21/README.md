# Paper school: QA (2026-10-21)

Branch `feat/style-paper`. Spec: `lib/ui/kit/style/kit_style_paper.dart`.
Renders (not in git): `/home/eslam/vision-assets/out/style-paper/progress/`
(`passN-<screen>-<light|dark>.png`, one phone screen each; `LOG.md` has one
line per pass). Report: `/home/eslam/vision-assets/out/style-paper/REPORT.md`.

## What Paper does now

- Newsprint ground through `adjustRoles` (H6): light is the theme's white
  pulled 4 % toward ink with a 4 % warm wash (≈ `#F6F3EF`); dark is the
  theme's ground 2 % toward ink (≈ `#101012`). surface1 = ground (panels
  are not filled), surface3 = ground 5 % (light) / 8 % (dark) toward ink,
  hairline = ink α 0.20 (the row divider). No colour literal.
- Content panels and row groups: no fill, no box; rows parted by the ink
  hairline. A raised content step (your prompt) is a 1 dp ink box on the
  paper; under Edges › Off a block in the pressed tone.
- Controls, tracks, floating pieces (message box, server pill): paper fill
  with a 1 dp ink box (α 0.9 light, 0.6 dark); the accent keeps its fill
  and has no box. Sheets, dialogs, menus: their own white step, ruled.
- Chosen: the pressed step with a 2 dp ink rule. Pressed: the pressed step.
- Corners › Sharp = 0 on every box, including ones a part rounds itself;
  circles stay. No shadow at all except the floating layer's one drop
  under Depth › Raised. Live background off when chosen (H7), 1 dp glow
  with no halo (H8), type 34/38 at 750 with mono labels (H1).

## Checks run (all green)

- `test/kit/style/paper_test.dart` (21), `test/kit/kit_style_test.dart`,
  `test/kit/kit_surface_look_test.dart`
- `test/goldens/kit/kit_surfaces_golden_test.dart` (only the two Paper
  images changed)
- `test/kit_ratchet_test.dart`, `test/no_red_scan_test.dart`
- `flutter analyze`: no issues

## Capture harness change

`tool/capture/surfaces_test.dart` now mounts like `main.dart`: the effects
come from `KitSurfaceLook.choose` (so Paper starts with the live look off)
and the navigator sits under `kitStyleTheme` (so each style's ground shows).
Without it the captures showed neither H6 nor H7.

## Frame timing (UI thread, 100 frames, capture harness)

| | mean | p90 | worst |
|---|---|---|---|
| Solid light | 0.13 ms | 0.18 ms | 1.12 ms |
| Solid dark | 0.13 ms | 0.17 ms | 0.57 ms |
| Paper light | 0.22 ms | 0.32 ms | 3.34 ms |
| Paper dark | 0.16 ms | 0.18 ms | 3.16 ms |

No blur anywhere (blurs 0). The worst frame varies run to run (pass 1:
0.31 / 0.79 ms).

## Open (needs the coordinator: shared files)

1. Inverted selection (ink fill, paper words). `kit_style_test` requires
   text1/2/3 at AA on every Paper fill, so an ink fill fails it; and H3 can't
   tell a chip from a tab or the nav lens. The non-filled tab paints its own
   `surface3` (not through `decorate`), and the nav lens's icon and label
   don't read `wordsOn`. The segmented thumb passes `selected` only under
   Glass. Paper keeps the part's own words colour for now.
2. Section rules above section labels: no hook. Row groups also inset their
   rows by the panel padding, so with no box the rows sit a step in from the
   labels (not flush-start).
3. The switch track (a stadium) and the composer's mic (a grey disc with no
   rule) don't take Paper's look; the spec never sees them (not
   traced further).
4. Needs-you card: the part draws its own amber hairline ring and tint; the
   guide wants the 4 dp start rule alone.
5. The kit surfaces golden doesn't apply `kitStyleTheme`, so the golden
   shows Paper's surfaces over the theme's own ground.

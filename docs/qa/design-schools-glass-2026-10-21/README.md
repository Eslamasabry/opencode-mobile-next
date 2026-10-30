# Glass, tuned through its spec (2026-10-21)

Branch `feat/style-glass` from `feat/schools-baseline` (`cf25cdd1b`). Brief: `docs/design/design-schools/briefs/glass.md`. Only `lib/ui/kit/style/kit_style_glass.dart` changed in `lib/`.

## The problem

On the emulator Glass over the live background was far too opaque (`launch-footage/dryrun-2572/10-home-glass-live.png`): dark panes were 0.54-0.70 opaque and tinted 45 % towards the ground for AA, so the aurora barely showed.

## What changed

| Token | Before | Now (dark / light) | Why |
|---|---|---|---|
| content | 0.54 / 0.56 | **0.20 / 0.30** | the colour behind reads through |
| control | 0.52 / 0.56 | 0.28 / 0.38 | |
| track | 0.42 / 0.48 | 0.18 / 0.28 | |
| floating (blurred) | 0.60 / 0.66 | 0.34 / 0.42 | |
| sheet (blurred) | 0.70 / 0.76 | 0.46 / **0.66** | light: over the scrim a clearer white pane turns grey |
| dark tint | step lerped 45 % to the ground | step lifted one step + near-white frost (content 14 %, controls 10 %, floating/sheet 6 %) | a clear pane over a dark ground must still read as frost; the guide's lean to the ground is gone (0 <= 0.15) |
| sheen (top start) | none | +8 % near-white (16 % light), alpha +0.04 (+0.12 light) | the frost catches the light |
| rims | white 0.24 -> white 0.04 | top white 0.30 -> bottom shade 0.45 (light: white -> shade 0.10) | light top, dark bottom |
| words plate (H10) | none | the pane over the bare ground leaned to the shade (dark) / white (light): content 0.4, controls 0.45, floating/sheet 0.6 | AA through the plate, not by filling the pane; over the bare ground the plate is nearly the pane, so it shows only where a ribbon passes under the words |
| weight (H1) | 0 | +50 (body 450) | a half step: heavier strokes over the moving colour without turning body text bold |
| words on chosen controls (H3) | part's own | `text1` (own fill keeps its words) | no quiet or accent words on a clear pane |
| scrim (H6) | 0.58 | 0.40 / 0.32 | the blur separates the sheet |
| sheets cast | no | the floating drop | they float like the dock |

Blur is unchanged (sigma 18, bounded, floating pieces and sheets only, grouped per page by H11).

## AA, measured

`test/kit/kit_surface_look_test.dart` (glass group) paints every live look at **Bold** (21 looks for Graphite, the aurora for every other theme pack, light and dark; idle and running; four moments) and takes the brightest and darkest 4x4 block (a letter reads against the look averaged over a few pixels, as `kit_live_background_test.dart` reads it). Worst found: dark `#1A4E35` (flow threads), light `#BBD7C7`. For every role, at rest and chosen, on the pane's plain face and its lit top start, `text1`, `text2` and `text3` reach 4.5:1 against the pane over that backdrop with `KitTextHalo.cover` of the plate put back. Accent and attention words are never worse on a pane than on the bare look. With the plate's lean at 0.3 the test fails (midnight, solarized sheets), so it is sensitive.

The measurement uses 4x4 blocks; read per 2x2 pixel, the sparse dots of Constellation reach `#7B7B7B`, where no clear pane can keep `text3` at AA. Same rule as the live background's own test.

## Renders

`/home/eslam/Storage2/launch-footage/progress/glass/` (`LOG.md` lists each pass). `passN-<screen>-<mode>.png` from `tool/capture/surfaces_test.dart` (aurora frozen at 8 s); `passN-<screen>55s-<mode>.png` at 55 s, when ribbons cross the cards. Pass 0 is the starting point, pass 4 is this commit.

Compared with `glass-inspiration/owner-glass-home.png`, `e-glass/concept.png` and `dryrun-2572/10-home-glass-live.png`:

- Right now, dark: the cards are a frosted grey lifted off the ground with a lit top rim; where a ribbon passes (chat's question card, settings panel, the composer's right end) it shows clearly through. Light: white frost, green through the card's edges, white sheet over a light scrim.
- Still weak: the aurora lies along the screen edges in these frames, so a card over the empty middle shows dark ground, not colour. That is the look's layout, not the pane. A card nested in a card (the question's answers) stacks two frosts and reads lighter. Large headings on a sheet show a faint plate around their letters over the dimmed page.

## Frame timing (UI thread, Conversations scroll, same run)

| | blurs | mean | p90 | worst |
|---|---|---|---|---|
| solid light | 0 | 0.07 ms | 0.10 ms | 0.14 ms |
| solid dark | 0 | 0.07 ms | 0.10 ms | 0.17 ms |
| glass light | 1 | 0.06 ms | 0.08 ms | 0.30 ms |
| glass dark | 1 | 0.06 ms | 0.08 ms | 0.28 ms |

The raster cost of the blur is on the phone's GPU and not measured here; the blur count is unchanged from the baseline.

## Checks run

- `test/kit/style/glass_test.dart` (new: defaults, plate, chosen/pressed, all 24 knob combinations, RTL, lerp to and from Solid, HC and Saver, scrim)
- `test/kit/kit_surface_look_test.dart` (glass group re-derived), `test/kit/kit_style_test.dart`, `test/kit_ratchet_test.dart`, `test/no_red_scan_test.dart`, `test/kit/kit_text_halo_test.dart`, `test/kit/kit_glass_test.dart`
- goldens: `kit_surfaces_glass_dark.png` and `kit_surfaces_glass_light.png` regenerated; no other golden changed
- `flutter analyze`

## Not done here

- The contract §8 capture list beyond home, chat, settings and a sheet needs capture fixtures in `tool/capture/` (not mine to edit).
- Live background off: the spec cannot see it (the request has no flag), so a pane stays as clear with nothing behind it.
- "Accent words never on glass": the conversation row's "Working" is accent on a content pane; that colour belongs to the row part.
- The High-contrast theme pack does not yet turn Glass into Solid (owner Q7); the AA test keeps that pack in and it passes anyway.

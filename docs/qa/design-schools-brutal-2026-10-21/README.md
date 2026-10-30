# Design schools: Brutal, tuned through its spec (2026-10-21)

Branch `feat/style-brutal`. Guide: [schools/brutal.md](../../design/design-schools/schools/brutal.md). Brief: [briefs/brutal.md](../../design/design-schools/briefs/brutal.md). Spec: `lib/ui/kit/style/kit_style_brutal.dart`.

Renders are outside git, in `/home/eslam/vision-assets/out/style-brutal/progress/` (`passN-<screen>-<light|dark>.png`; `LOG.md` has one line per pass). Captured with `tool/capture/surfaces_test.dart --plain-name " brutal "` at 412×915 dp, 2× pixels.

## Before and after

| Screen | Before (placeholder) | After |
|---|---|---|
| Home | `pass0-home-{light,dark}.png`: grey ground, panels, chips and the server pill without shadows | `pass4-home-*`: cream ground, every panel and chip casts |
| Chat (running, question) | `pass0-chat-*` | `pass4-chat-*` |
| Settings | `pass0-settings-*` | `pass4-settings-*` |
| Tools sheet | `pass0-sheet-*` | `pass4-sheet-*` |
| Finished chat (§8 3) | — | `pass4-done-*` |
| Confirmation sheet (§8 6) | — | `pass4-confirm-*` |
| The main action held | — | `pass4-confirm-pressed-*`: the face sinks 3 dp into its shadow |

## Passes

1. The shell. Home draws its page as a `KitSurface` at the ground step. The placeholder gave it an ink edge and a shadow, so under hook H9 it was the one outer surface and everything on home lost its shadow. The ground step is now the paper itself: the part's own decoration, which casts nothing.
2. The ground (H6). In light, the ground becomes cream paper and the surfaces become warm paper. In dark, the near-black ground stays and cards take `surface2`. A tailed shape (the prompt bubble) casts again. A bottom sheet (rounded on top only) sits on the scrim with no shadow; dialogs and menus cast.
3. The dark shadow is an ink-grey and the cream is warmer.
4. Resting controls sit on paper; chosen or pressed ones take the tile step. Captures were added for a finished chat, a confirmation sheet and the main action held down.

## Checks

- `test/kit/style/brutal_test.dart` (17): tokens; the ground across every theme pack in both brightnesses (words stay at AA, and the accent guard re-runs); shadow per role and under Compact; Flat; Edges › Off; high contrast; chosen and pressed; the RTL mirror of the shadow and the pressed face; fades to and from Solid.
- `test/kit/kit_style_test.dart`, `test/kit/kit_surface_look_test.dart`, `test/kit_ratchet_test.dart` (G17: no colour literal; every colour comes from the roles) and `test/no_red_scan_test.dart` pass.
- Goldens: only `kit_surfaces_brutal_{light,dark}.png` changed. The gallery now applies `kitStyleTheme` as `main.dart` does; that is a no-op for the other styles, whose images are unchanged.
- Frame timing (UI thread, 100 frames of the Conversations scroll, same run): Brutal light mean 0.14 ms, p90 0.19 ms, worst 1.50; Solid light 0.23 / 0.23 / 3.46. Brutal dark 0.11 / 0.17 / 0.29; Solid dark 0.13 / 0.20 / 0.54. 0 blurs. The raster side runs on the phone's GPU and is not measured here.
- Owner rules: no red. Amber appears only on needs-you parts: the cream is about 12 % of the theme's amber over the ground, a paper tone that is never an amber fill (the invariant checks it). There is one mint main action. No new UI level. Press motion is the kit's 120 ms state fade. No `flutter_animate`. Saver still forces Solid (unchanged). High contrast keeps Brutal, Flat with its edges.

## Still not Brutal (needs a part-level hook; see the report)

- The light primary button is the theme's deep accent with white words. The guide wants the bright mint with ink words, but `KitButton` takes the primary's words from `onAccent`, not `KitSurfaceLook.wordsOn`.
- The question card's own shadow is clipped (open item in briefs/README.md).
- The "Needs you" tag is mono amber text, not an amber pill with an ink edge.
- These circles and boxes don't go through the look, so they have no ink edge or shadow: the new-conversation button (FAB), the composer's mic and send circle, the code block, the switch track, the model chip, and the icon tile in the confirmation sheet.
- The pressed state layer (16 % of the words' colour) lies over the ink edge and greys it while held.

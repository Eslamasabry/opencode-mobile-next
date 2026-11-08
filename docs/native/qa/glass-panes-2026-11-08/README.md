# Glass panes: clear in-flow glass, glass chosen states, no words plates. Emulator proof, 2026-11-08/09

Branch `native/glass-panes`, landed on `native/next` at f6ca78138. The fast lane was green on 21ede1a69 (run
42518438519); the after renders come from that run's preview APK. Before = `native/screens-merge41` preview APK
(run 42507949745, bbd8d1a88). Emulator OC_API35 (API 35, swiftshader), OpenCode 2.2.6 proof server (the liquid-glass
proof data), Graphite theme with the green accent, Glass style, Glass edges Liquid.

## What changed (owner decisions, 2026-11-08)

- **In-flow panes** (cards, row groups, chips, fields) are clear frosted glass: `GlassSpec.resolveClear` starts the
  tint at 14-24 % (today 38-56 %), with the content blur 24 dp and a soft top sheen. `GlassFinish.thickened`, the one
  contrast measure, thickens it only as far as text1/2/3 need over the ground and the frosted aurora, and drops the gloss
  first when it would cost more than 6 % of tint. Held (Saver, reduced motion, high contrast, frost Off, Glass edges
  Frosted) they keep today's panes (`FrostPolicy.clearPanes`).
- **No words plate anywhere** ("get rid of the haze behind the text"). Liquid faces carry their words with their own
  tint (`LiquidEdge.faceTint`, the same `GlassFinish.thickened`), and flip their face together with their ink.
- **Chosen states are glass**: accent-tinted frost with a soft accent rim (rows, segmented thumbs, chips, style cards),
  the check in the accent, no second ring. The accent appears only on chosen states.
- **Rims and the specular are neutral white light** on every glass edge (the HCT-pulled accent read blue-teal).
- **Corners knob**: a surface fade never read its progress on the pass that started it, so a Glass face kept the
  previous corners under the new clip and ring (the owner's Round=square, Sharp=round). Fixed and tested with motion on.
- **Composer**: the chips over the message box are small liquid drops; the fade band behind them is gone; the tray is
  four bare 28 dp glyphs (no discs, no words; the word is the accessibility label and the long-press tooltip); pills
  have a calm lens (narrow rim, light bend, no depth).
- **Menus and sheets keep the chrome liquid** (header capsule, composer, pill); beyond the budget (now 5) a part is
  frost of the same shape, never solid.

## Renders (one phone screen each)

| Screen | Before (merge41) | After |
|---|---|---|
| Settings › This app | before-settings-this-app-{dark,light} | after-settings-this-app-{dark,light} |
| Settings home | before-settings-home-{dark,light} | after-settings-home-{dark,light} |
| Conversations list | before-list-{dark,light} | after-list-{dark,light} |
| Project sheet over the list, a row chosen | before-project-sheet-{dark,light} | after-project-sheet-{dark,light} |
| Chat over a long reply (composer closed) | before-chat-{dark,light} | after-chat-{dark,light} |
| Composer with the tray open | before-composer-tray-dark | after-composer-tray-dark |
| Chat with the Auto-approve menu open | before-chat-approval-menu-dark | after-chat-approval-menu-dark |
| Agent and model sheet over New conversation | before-agent-model-sheet-dark | after-agent-model-sheet-dark |
| Appearance, Corners Round / Soft / Sharp | before-appearance-{round,soft,sharp}-dark | after-appearance-{round,soft,sharp}-dark |

**Background caveat.** Every before shot and the after light list, project sheet and chat were taken over the
*Aurora drift* shader look. The other after shots (all dark ones, and the light This app and Settings home) were taken
over Glass's own *Painted aurora*, after I found that the in-flow panes only blur a painted picture (next section). The
quiet hours stopped the emulator before the before set could be re-shot over Painted aurora. Re-shooting it is the
first thing to do after 02:00.

## Found while rendering

- **Under a shader look, Glass's in-flow panes do not blur at all.** Only the painted aurora is recorded for the panes
  (`KitLiveBackground`: `backdrop.painted` is the painted plate only). Over Aurora drift, Breathing mesh and the other
  shader looks, every pane draws the opaque ground under its tint, so it stays a near-solid slab whatever its tint. That
  is likely the owner's "why isn't this pure glass". Clear panes follow the blur, so they apply over Painted aurora only.
  Fix proposal (outside this fence, `ui/kit/live`): record the shader frame into the glass backdrop at the painted
  plate's 6 steps a second, and give `frostedSpots` from the look's palette.
- The before shots reproduce every owner report: Corners Round drew square cards (stale Sharp face) with a rounded
  ring; the chosen project row is an opaque square slab; the sheet title and agent rows sit on dark pills; opening the
  Auto-approve menu turns the header capsule into a plain bar and the composer flat.

## Contrast (B-KIT-003, every theme pack, light and dark; fast lane runs 42516693274 and 42518438519)

- In-flow panes, Opencode: Content 16 % dark / 21 % light, Control 20 / 23 %, Track 14 / 20 %, Field 18 / 23 %; none
  thickened above its start tint in Opencode; every pack reads text1/2/3 at 4.5:1 (or Solid's own) with no plate; icons
  and switch-track edges at 3:1; no pane, chosen or not, falls back to opaque (asserted).
- Liquid faces, no plate: dark 43-72 %, light 35-76 % across packs (they were 18-34 % with the plate). Measured over
  what they float over in this app (ground, surfaces, frosted aurora, words blurred to 20 %, a 25 % accent wash), with
  the ink flip covering pictures of the other brightness. Over arbitrary content up to the flip threshold ("band") the
  worst case is 2.3-3.3:1: the plate used to guard that.

## Not done

- The before set over Painted aurora (quiet hours).
- A menu over a sheet: no sheet in the app opened a menu from the paths I tried.

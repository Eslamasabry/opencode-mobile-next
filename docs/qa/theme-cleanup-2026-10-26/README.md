# Theme clean-up (2026-10-26)

Owner: "we haven't finished cleaning the themes yet. It's still slow,
artificial and broken", then "tune down the aurora brightness then and get
rid of this stupid looking UI".

## What was wrong

- **Artificial.** The painted aurora (default look of Glass and Crystal,
  and the emulator's chosen background in every style) showed at full
  strength. Under Glass and Crystal every run of words on the bare page sat
  on its own white plate; in a reply each paragraph became a separate pill.
- **Slow.** Under every other style the same picture gave each text run a
  six-shadow halo. Chat scrolled at 35-55 ms a frame in Solid, Soft, Brutal
  and Paper (84-100% janky frames, emulator, 3570).
- **Broken.** Glass's long conversation list split partway down: a clear
  top, a frosted or white bottom and a rainbow or grey band between (its
  backdrop read stopped short on the device).
- A step cut off with its turn showed "Failed", "Tool failed." and the raw
  note "Tool execution interrupted" in a code box; its steps line said
  "Didn't finish" while the next turn's said "Stopped".
- The Appearance preview drew the reply in a bubble with a mark, a look the
  chat no longer has; "Changed last" read "Style" over "Style · Glass".

## What changed

| Commit | Change |
|---|---|
| `fix(style): the painted aurora is a dim tint...` | The picture keeps only its colour: each pixel takes the ground's brightness plus 12% (light) / 14% (dark) of its chroma. No plates, no halo, no vivid wash. `test/kit/kit_live_aurora_dim_test.dart` measures every word tone over it in every theme, light and dark. |
| `fix(style): a long list's Glass panel no longer splits...` | A lazy list's panel takes the pane's tint without the blur; cards, the top bar, the message box and sheets keep their frost. |
| `fix(chat): a step cut off ... reads Stopped` and `... steps line ... reads Stopped` | The row and its line read Stopped; no raw note, no box. A tool's own error keeps its output box. |
| `fix(style): Glass panes blur unbounded...` | The same band crossed ordinary Glass cards too (Settings › Agent at Tools, Help at Report a problem). The frost's bounded blur read part of the pane from outside its backdrop on the device; the blur is now unbounded, kept in shape by the pane's clip (3660: no band on Settings, Agent, Help). |
| `fix(appearance): the preview's reply and Changed last...` | The preview's reply sits on the page as in the chat; Changed last shows the value alone when the chip and question share a name. |

Known limit: a few accent and success words in some light themes read at
4.45-4.49:1 over the tint (they sit at about 4.5 on the plain ground); the
test allows those a 2% loss and no more.

## Frame times (emulator-5584, scroll, SurfaceFlinger)

Continuous swipes (`frames.sh`, 8 x 400 ms). Mean ms per frame, janky = over 25 ms.

| Style | Conversations 3570 | Conversations 3630 | Chat 3630 |
|---|---|---|---|
| Solid light | 22.5 (29%) | 16.7 (0%) | 17.9 (6%) |
| Glass light | 17.2 (3%) | 16.9 (2%) | 17.6 (5%) |
| Crystal light | 18.6 (11%) | 19.7 (18%) | 17.5 (4%) |
| Soft light | 24.7 (41%) | 16.7 (0%) | 18.1 (8%) |
| Brutal light | 27.7 (58%) | 16.7 (0%) | 17.9 (7%) |
| Paper light | 22.2 (29%) | 16.8 (0%) | 18.0 (7%) |
| Soft / Brutal / Paper dark | 25-31 | 16.7 (0%) | 18.0 (7%) |

3570 chat (paused swipes): 35-55 ms in Solid, Soft, Brutal and Paper.
Crystal keeps real refracting lenses on its floating pieces, which redraw
every scroll frame; left as the style's own effect.

Screens: `launch-footage/style-audit-3570`, `-3580*`, `-3600`, `-3610`,
`-3630` (one image per screen and style).

## Open

- Crystal's Conversations list drops about 15-18% of frames (its lenses).
- On-device models: researched (`docs/research/on-device-llm-2026-10-26.md`)
  and parked by the owner as not feasible for now.

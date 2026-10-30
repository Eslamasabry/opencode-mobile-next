# Settings > Appearance, final design (2026-10-21)

Branch `feat/appearance-final` from int `fa4cb815e`. Owner-approved mocks:
`launch-footage/appearance-concepts-2/final-dark`, `final-saver`, `final-light`
(structure and polish only), plus the Surfaces rows of `e-glass` and
`f-neumorphism`.

## Technical honesty audit (written before editing)

Every element of the mocks, where it would come from, and whether the mock can
be built as drawn without lying about the app.

| Element in the mocks | Source in the app | Verdict |
| --- | --- | --- |
| Back arrow, "Appearance" | `KitTopBar` (exit auto, `e7AppearanceTitle`) | Honest as is. |
| Mint dot + "Live" at the end of the title row | `KitTopBar.accessory` slot exists; the dot+word is a new kit part (`KitLiveTag`) | Honest: the preview really does follow every choice at once. Replaces the "Live preview" line inside the old boxed card. |
| Preview drawn straight on the aurora, no box | `KitScreen`'s ground is already `KitLiveBackground` | Honest once the page's own `KitEffectsScope` sits above `KitScreen`, so the ground shows the choices in force (and goes flat / black under Saver). |
| "Tidy up the settings page" bubble | `KitMessage.prompt` (the chat's own part) | Honest. Its thin edge in the Accent colour is a change to the real bubble, so the chat gets it too. |
| `>_` agent tile | Mock invents a terminal glyph | Not honest. The mark comes from the persona registry: `AgentPersona.openCode.markSlug` drawn with `KitIcon.mark` in a tile. |
| Reply in a bubble, streaming word by word with a soft caret | The chat's reply is full-width prose; no caret part exists in the chat today | Partly honest. The bubble is the studio's approved simplification (kept from today). The caret is a new kit part `KitStreamCaret` (lib/ui/kit/chat/); the chat screen does not use it yet (that library belongs to another agent) - flagged in the report. |
| Three steps: check / spinning ring / dim dot | The chat's opened work line: `KitWorkLine` + `KitToolRow` | Honest only with the real parts, which differ from the mock: each step has its tool glyph tile (done = glyph, running = the step light, waiting = dim tile + "Waiting"), the line above reads "3 steps", and the seconds counter sits at the row's end. Built with the real parts, not a lookalike. |
| Message box with glow and Stop ring | `KitComposer(busy: true)` | Honest. The Stop ring colour moves from `text2` to the Accent role (real composer change). |
| Mode: Auto / Light / Dark / Saver with icons and labels | `KitSegmented` (icon-only today); `AppAppearance { system, light, dark }` | Buildable: a `tall` variant of `KitSegmented` (icon over word) and a new stored `AppAppearance.saver`. |
| Saver: true black, motion off, no live background, hairline glow, solid surfaces | Theme built in `main.dart`, effects in `KitEffectsScope` | Honest as an effective override: stored choices stay untouched; `main.dart` builds a black-ground dark theme and scopes `KitEffects` with motion off, live background off, surfaces solid. Motion Off draws no glow at all today, so the hairline glow needs a small Saver case in the composer frame. |
| "True black, no motion..." line | New copy | Honest. |
| Theme cards: background + main stripe + accent dot, name below, ring + check | `KitOptionCard.theme` + `KitCarousel` | Buildable as a compact variant. The background stays the person's chosen look as one still frame in each theme's colours (`KitLiveBackground roles:/still:`). The mock's per-theme dot colours (peach for Catppuccin...) are invented; the real packs' partner colours are used. |
| Saver theme cards: black with a faint drawing of the look | Same, with a black ground | Honest: the stored look painted faint on black (never empty black). |
| "Material You" card | `harvestedDynamicPack` | Keeps its disabled reason when unavailable. |
| Accent: "Match theme" + 7 round swatches | `ThemeRoles` has one colour (`accent` = the main colour); the glow's two-colour mode borrows `codeKeyword` | Needs a new role (`partner`, because `accent` already names the main colour), derived from the pack's secondary or the chosen swatch, guarded for contrast and kept away from amber (LOOK-39). The mock's cream swatch sits within 30 deg of the "needs you" amber - replaced by a neutral. Stored as `oc.appearanceAccent`, app-wide. |
| "Main / Accent" legend under the swatches (Saver mock) | - | AI noise; dropped (no new UI levels). |
| Surfaces: Solid / Glass / Soft cards | `KitEffects.surfaceStyle` (frozen, stored `oc.effectsSurfaceStyle`) | Honest as a setting and as three drawn samples. Kit-wide glass/soft rendering is another agent's slice; until it lands the choice is stored but only the samples show it. Disabled under Saver with the reason in words. |
| Carousel dots under the themes (e-glass, f-neumorphism) | `KitCarousel` place dots | Dropped for the compact strip (2.5 cards across already shows there is more). |
| Status bar, home bar | Device chrome | Not ours. |

## What was built

- **Header.** "Live" (`KitLiveTag`) sits at the end of the title row through `KitTopBar.accessory` (hidden at 1.3x text and up, so the title keeps the line). The preview has no box: it is drawn on the page's own ground, as tall as its content, followed by a hairline (`KitDivider`) and the tabs. The page's `KitEffectsScope` now wraps `KitScreen`, so the ground itself shows the choices in force. Pinned on phones, side by side on tablets (with a vertical hairline), scrolling with the page at large text.
- **Agent at work.** `KitAppearancePreview` loops every 8 s where loops may run: the reply streams in word by word (`KitStreamCaret`, new), holds, fades; the opened `KitWorkLine` shows three real `KitToolRow` steps (done / running with its counter / waiting); `KitComposer` with a reply running. Calm, Off, Saver, remove-animations and screenshots show one still frame. The mark is `AgentPersona.openCode`'s.
- **Mode.** `AppAppearance.saver` (stored as `oc.appearance = saver`). `KitSegmented(tall: true)`: icon over word, hairlines between resting segments, stacks at 2x text. Saver is applied in `main.dart` (theme: `AppTheme.saver`, a black-ground `ThemeRoles.saver`; effects: `KitEffects.underSaver` = motion off, live background off, surfaces solid, `saver: true`), and the same way on the Appearance page. Stored effect choices are never written by Saver. The composer frame draws a still focus-ring line in the main colour while a reply runs under Saver. A quiet line under the segments explains it.
- **Themes.** `KitOptionCard.theme` is now a compact swatch (ground + the person's look as a still frame, main-colour stripe, partner dot, name under it, ring + 20 dp check). `KitCarousel` gained `share`, `showPlace` and `growPerTextStep`. Under Saver the swatches are black with the stored look drawn at 35 %. Material You keeps its reason, written on the swatch.
- **Accent.** New role `ThemeRoles.partner`/`onPartner` (named so because `accent` already is the main colour): from the pack's secondary, then tertiary, then the kit's code violet or sky, the first that keeps 30° and ΔE 20 from amber and ΔE 20 from the main colour, guarded to 4.5:1 on ground and surface1 and for its on-colour. The swatch overrides it (`ThemeRoles.withPartner`). Used by: the Stop ring, the prompt bubble's hairline edge, Markdown links, the two-colour glow and the Match theme ring. Stored as `oc.appearanceAccent` (app-wide, in the coverage ledger; absent = Match theme). Seven swatches: mint, teal, blue, violet, pink, mono, silver (the mock's cream sits within 30° of amber, LOOK-39). `KitAccentRow` (new) scrolls sideways when the 48 dp targets do not fit.
- **Surfaces.** `KitOptionCard.surface` draws Solid / Glass / Soft samples and writes `KitEffects.surfaceStyle` (unchanged, `oc.effectsSurfaceStyle`). Under Saver: Solid shown selected, cards rest, "Saver uses solid surfaces" once under the row.

## Evidence

- Final renders (one full screen each): `/home/eslam/Storage2/launch-footage/appearance-final/` — dark main, Saver, light, Accent (pink), Surfaces dark/light/Saver, 320 dp at 2x text, Arabic RTL, tablet, Catppuccin with the circuit look, and the other four tabs.
- Every pass: `/home/eslam/Storage2/launch-footage/progress/appearance-final/passN-*.png` with `LOG.md`.
- Renders: `APPEARANCE_SHOTS_DIR=… flutter test --concurrency=1 tool/capture/appearance_revamp_test.dart`.
- Tests: `test/appearance_final_test.dart` (Saver stored/override/AA, Accent stored/restart/AA for every pack x swatch, Surfaces save, header layout), kit tests for the new parts and variants, `test/kit/kit_keyboard_test.dart` (Accent row), overflow scenes; gates G4, G6, G8x, G16, G17, G21, G28 and the l10n tests pass; `flutter analyze` clean.
- Goldens regenerated with `TZ=UTC` after a full golden sweep (260 files, in chunks): kit galleries (preview, option card, carousel, segmented + new `tall`), and the chat goldens the Accent touches (bubble edge, Stop ring, links): chat states, approval chip, composer, markdown, message, transcript excerpt, turn, viewer, team, settings, revamp chat/saved prompts/settings/slices.

## Not done / limits

- The chat screen does not use `KitStreamCaret` yet (that library belongs to another agent); the caret exists only in the preview.
- The mock's step marks (check / ring / dot) are not used: the real work line's glyph tiles are, with a "3 steps" line above them.
- At 412 dp the seventh swatch peeks (the row scrolls): seven 48 dp targets and the Match tile do not fit in 380 dp.
- Light themes whose second colour comes from a seeded tertiary get a muted partner after the contrast guard (Catppuccin Latte: a dusky rose).
- Glass and Soft are stored and drawn as samples only; the kit-wide rendering is the surfaces agent's slice.
- The Stop disc stays neutral (owner decision 02B); only its ring takes the Accent.

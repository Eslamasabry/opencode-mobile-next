# Showcase video plan — "Your coding agent, in your pocket"

> **Owner direction (locked):** the video focuses on MOBILE throughout —
> every body act is phone footage. Desktop appears exactly once, as a brief
> tease near the close ("…and it's coming to your desktop" over the Linux
> window still, REC-13), never as a second protagonist. Scenes that gave
> desktop more than tease-weight are trimmed to this rule at execution time.


Planning date: 2026-08-28. Sources: `.claude/skills/motion-design/` and
`.claude/skills/remotion-motion-graphics/` (rules cited per scene),
docs/internal/handoff.md Operation Facelift records, docs/design-inspiration.md.

## 1. Concept and narrative arc

**Concept.** The video *is* a terminal. Near-black green (`#101310`) base,
one hero color (the app's mint `#83CDAA`), JetBrains Mono for every hero
line, and the app's own signature — the `❯` prompt and blinking block caret
— as the recurring motion motif. The story is a developer's evening: the
agent is working on the desktop, the developer leaves the desk, and the
phone picks the work up. Every feature beat answers "what can I *do* from
the couch?"

**Audience.** Developers deciding whether to sideload the APK. Copy is
plain and specific; no marketing adjectives.

**Motion personality** (motion-design: choose ONE archetype): **Premium** —
350–600ms moves, zero overshoot, signature easing `cubic-bezier(0.4,0,0.2,1)`
for ~80% of animations, MD3 Emphasized `(0.05,0.7,0.1,1)` reserved for hero
entrances. Duration palette (30fps): quick = 8f, standard = 14f, slow = 24f.
Signature entrance: type-on with caret (brand motion identity rule).

**Format.** 30fps, total **4500 frames (2:30)**. Primary render
**1920×1080 landscape** — the audience watches on GitHub/YouTube at a desk;
the phone recording sits in a device frame with caption space beside it.
Secondary 1080×1920 vertical for Shorts/Reels (same scenes, captions move
above/below the device, safe-zone rule applies).

Arc (design-rules 30s structure scaled ×5, holds preserved):

| Act | Frames | Beat |
|---|---|---|
| HOOK | 0–120 | `❯ opencode` types on, caret blinks, one claim |
| CONTEXT | 120–360 | the desk problem, two lines |
| BODY 1 — core demo | 360–1680 | first run → quick-ask → live agent run |
| BODY 2 — control | 1680–2520 | model picker, steal, notification actions |
| BODY 3 — montage | 2520–3900 | hubs, files, review, terminal, themes, desktop |
| PAYOFF | 3900–4200 | numbers beat (real, verifiable counters) |
| CTA | 4200–4500 | identity close, one action |

## 2. Storyboard

Legend: REC-n = recorded shot (§3). GEN = generated in Remotion. Every scene
sits in the five-layer stack (mesh → assets → type → grade → grain+vignette;
remotion rule 5) and every entrance animates 2–3 properties with stagger
(rules 2–3). Exits ≈10f, entrances ≈20f (rule 4). Idle elements breathe
(rule 7).

| # | Scene | Frames | On screen | Motion treatment | On-screen copy | Rule citations |
|---|---|---|---|---|---|---|
| 1 | Hook | 0–120 | GEN: black-green mesh; `❯ opencode` types on in Mono 120px; caret blinks 2×; mint glow on the word "opencode" only | Type-on 2f/char from f8 (movement inside first 15f); caret opacity square-wave; word lands with 4px rise + scale 0.98→1 spring | `❯ opencode` → then below, WordReveal: "Your coding agent. In your pocket." | remotion 1,2,8; design-rules hook; motion: signature entrance |
| 2 | Context | 120–360 | GEN: two text blocks; faint desktop-terminal ASCII art drifts behind (parallax layer) | Lines enter staggered 5f, translateY 24px + fade + scale; HIT at f150 then 18f hold; exit accelerates down | "Your agent is mid-task on your desktop." / "You're not at your desk." | motion: emotion=calm→urgency; remotion 3,4; holds |
| 3 | First run | 360–570 | REC-1 in DeviceFrame, entering from right with 12px overshoot-free slide; caption panel left | Device enters 24f MD3-Emphasized; caption words reveal 3f stagger; Ken Burns 1→1.04 on the paused welcome frame at the end | "Fresh install to connected in under a minute." then "Paste an address. It tests the connection for you." | remotion 6 (KB), 2,3; motion: card entrance pattern |
| 4 | Quick-ask | 570–780 | REC-2: workspace with quick-ask pill; tap ripples; chat opens | Crop-zoom to the pill (scale 1→1.15 over 20f) before the tap — staging/hero rule; match-cut on the tap | "The home invites typing." | motion: staging, 1/3 distance; remotion 14 |
| 5 | Live run | 780–1380 | REC-3: prompt sent; caret blinks "working"; tool ticker cycles ("Shell · flutter test…"); tool group grows | Recording plays at 1× with two 15f holds frozen on: the blinking caret; the live ticker. Caption pill highlights the ticker text | "Watch it work. Tool by tool." / "The line under the composer? Your context window, filling." | design-rules holds; remotion 7 breathing on caption; motion: hero staging |
| 6 | Context meter | 1380–1680 | GEN macro recreation of the meter line (mint fill animating 0→32%) above REC-3 still | Meter fill via spring, 40f; percentage Counter ticks beside it (tabular-nums) | "77k of 400k tokens. Always visible." | remotion 9 counter; motion: progress=linear allowed exception |
| 7 | Model picker | 1680–1920 | REC-4: picker sheet; pinned apply bar; variant chips | Sheet recording; crop-pan down to the pinned bar (1/3-distance keyframes); hold 15f on the bar | "349 models. The apply button never scrolls away." | motion 1/3 rule; design-rules holds |
| 8 | Steal | 1920–2220 | REC-5: finder → Continue here → confirm sheet → chat opens; small desktop-terminal graphic hands off a session glyph to the phone (GEN overlay) | Session glyph arcs desktop→phone (curved path = friendly), 30f; confirm sheet HIT with SFX | "A session running elsewhere?" / "Steal it. Continue on the phone." | motion: path-as-language, arcs; remotion 16 SFX |
| 9 | Notifications | 2220–2520 | REC-6 (or staged fallback): notification shade with Allow once / Deny / Reply | Shade slides down (rigid material, 1.2× duration, 0% overshoot); button highlight pulse ≤2 cycles | "Approve a tool from the shade. Reply to a question. Without opening the app." | motion: material=rigid; remotion 2 |
| 10 | Hub montage | 2520–3180 | REC-7/8/9/10: More grid, Settings hub, Files glyphs, Review viewed-progress — 4 beats ×~160f | Each beat: HIT (device swap via slide+fade 12f) → 15f hold → one crop-zoom build; captions swap with WordReveal; ≤1/3 of elements moving at once | "Organized, finally." / "Settings that end." / "A file browser that knows file types." / "Review 114 files. It counts for you." | remotion rhythm ≤90f/element; motion 1/3 elements |
| 11 | Terminal | 3180–3420 | REC-11: real PTY, command typed, output streams | Straight playback, no chrome — the app IS the aesthetic here; grain slightly stronger this scene | "A real terminal. Cursor-safe across sleep." | design-rules: holds as contrast |
| 12 | Themes | 3420–3780 | REC-12: Appearance page cycling packs — Catppuccin, Gruvbox, Solarized, Material You | Each switch = whole-frame grade shifts with the recording; swatch row staggers in 4f; breathe on idle | "Your terminal, your colors." / "Catppuccin. Gruvbox. Solarized. Or Android's own." | remotion 3,7; one hero color per FRAME still holds (mint caption only) |
| 13 | Desktop | 3780–3900 | GEN: Linux desktop screenshot (STILL-1) with Ken Burns 1→1.08 + pan | KB mandatory on stills; single caption | "Oh — it runs on Linux too. Same codebase." | remotion 6 |
| 14 | Numbers | 3900–4200 | GEN: three counters stagger in on mesh | Counters (soft tick SFX), 6f stagger, biggest scale-in of the video (payoff rule) | "506 tests green." / "188 server operations mapped." / "5 preview releases in one day." | design-rules payoff; remotion 9,16 |
| 15 | CTA | 4200–4500 | GEN: `❯` + caret return; wordmark "OpenCode mobile"; one line | Mark in 0–24f, wordmark type-on, 20f breathe hold, caret keeps blinking to the last frame; everything else exits at f4440, caret stays | "Get the preview APK on GitHub." / small mono: "github.com/Eslamasabry/opencode-mobile-next/releases" | design-rules logo sting; motion: signature entrance closes the loop |

## 3. Shot list (parent records via adb screenrecord)

Record at the emulator's native **1080×2400 portrait**, `adb shell
screenrecord --bit-rate 12000000 /sdcard/shot.mp4`, pull each file. Name
exactly as below into the Remotion project's `public/shots/`. Animations ON
(the app's own motion is part of the demo). Dark theme, OpenCode pack,
except REC-12. Each shot: start recording, wait 1s, perform, wait 1s, stop.

| File | Flow | Needs live server? |
|---|---|---|
| `rec1-first-run.mp4` | `pm clear` → launch → welcome → Connect card → type `127.0.0.1:4096` → Test (success card) → Save → server list → tap → connected Workspace | Yes (health) |
| `rec2-quick-ask.mp4` | Workspace idle 2s (entrance motion) → tap quick-ask pill → empty chat with suggestions | Yes |
| `rec3-live-run.mp4` | In new chat: type a real prompt ("Explain this project's test strategy") → send → caret → tools ticking → response streams → meter visible at end. THE MONEY SHOT — needs a real model run (small prompt, one turn; budget a few cents) | Yes + model tokens |
| `rec4-picker.mp4` | Chat → context button → picker opens → scroll models → tap one → variant chip → Use | Yes |
| `rec5-steal.mp4` | Global finder → search → row with Continue-here icon → tap → confirm sheet → confirm. Needs a workspace-scoped session; if the server has no managed workspace, record UP TO the confirm sheet and cut before the (refused) call — honest footage, the sheet is the feature | Yes |
| `rec6-notifications.mp4` | Live mode on → background app → trigger permission-gated run → shade with Allow once/Deny → tap Allow → notification clears. If a permission-gated run can't be staged, fallback: record the shade with the posted alert only (no tap) and let the caption carry the claim — do not fake a tap | Yes + staging |
| `rec7-more-hub.mp4` | More tab: setup card + grid (entrance stagger) → open Commands & tools → swipe tabs | Yes |
| `rec8-settings.mp4` | Settings hub → Server page → back → Appearance | Yes |
| `rec9-files.mp4` | Files tab → browse a real directory (type glyphs visible) → open a Dart file (syntax highlight) | Yes |
| `rec10-review.mp4` | Review with several changed files → tap through 2 files (viewed count increments) | Yes, needs a dirty tree |
| `rec11-terminal.mp4` | Terminal tab → new terminal → `ls`, `git status` → output | Yes |
| `rec12-themes.mp4` | Settings → Appearance → tap each pack swatch, 1.5s pause per pack | Yes |
| `still1-linux.png` | Screenshot of the Linux desktop build window (run the built bundle from a desktop terminal) | Local build |

## 4. Remotion project plan

Location: `video/` at repo root (gitignored `node_modules`, `out/`).

**Packages** (pin current stable at install time): `remotion@^4`,
`@remotion/cli@^4`, `@remotion/google-fonts` (JetBrains Mono + Inter),
`@remotion/transitions`, `react@18`, `react-dom@18`. Chromium note: pass
`--browser-executable` if auto-download fails (skill §Render).

**Theme** (`src/theme.ts`, copied from skill asset, adapted): base `#101310`,
surface `#151A17`, hero `#83CDAA` (≤1 hero element per frame), accent
`#A8CBB9`, text `#E3E8E4`, error `#FFB4AB`; springs: `heroEntrance`
(damping 200), `uiPop` (damping 18); easings: `signature`
cubic-bezier(0.4,0,0.2,1), `emphasized` (0.05,0.7,0.1,1), `exit`
(0.3,0,1,1); fonts: JetBrainsMono (hero, 600–700), Inter (captions).

**Structure**:
- `src/Root.tsx` — two Compositions: `ShowcaseLandscape` 1920×1080×4500f
  (primary) and `ShowcaseVertical` 1080×1920×4500f (same scene components,
  layout prop).
- `src/scenes/S01Hook.tsx` … `S15Cta.tsx` mapped to the storyboard.
- `src/components/`: `SceneShell` (five-layer wrapper: BgMesh, children,
  Grade, Grain, Vignette), `DeviceFrame` (rounded bezel, `<OffthreadVideo>`
  inside, subtle breathe when idle), `TypeOn` (caret type-on, the signature),
  `Caret` (square-wave blink), `CaptionPanel` (WordReveal + one-word mint
  highlight), `Counter`, `CropZoom` (animated scale/translate on a recording
  with 1/3-rule keyframes), `SessionGlyphArc` (scene 8 overlay).
- `public/shots/` — the REC files verbatim; `public/sfx/` — synthesized WAV
  kit (whoosh ×2, click, riser, bass hit, tick, pop) generated by
  `scripts/make-sfx.mjs` per the skill's no-silent-delivery rule.

**Audio**: no VO. Synth SFX on every HIT starting 2–3f early; optional music
only if the owner supplies a licensed track — if none, SFX-only is the
deliberate choice (skill permits; silence is what's forbidden). If music
arrives: compute framesPerBeat and snap scene cuts to beats.

**Render**:
```bash
npx remotion render src/index.ts ShowcaseLandscape out/showcase-landscape.mp4 --codec h264 --crf 16
npx remotion render src/index.ts ShowcaseVertical  out/showcase-vertical.mp4  --codec h264 --crf 16
```
Landscape is primary (developer audience on GitHub/YouTube); vertical is the
social cut. Verify per skill: `npx remotion still` at frames 15, 110, 400,
900, 1400, 2000, 2400, 3000, 3500, 4100, 4480 — inspect every extracted
frame, fix, re-render, re-inspect, then run the design-rules checklist.

## 5. Execution checklist for the parent

1. Restart emulator + local `opencode serve` + adb reverse (10 min).
2. Record REC-1…REC-12 + STILL-1 per §3, pull to `video/public/shots/`,
   trim heads/tails with ffmpeg (60–90 min; REC-3 needs one real model turn,
   REC-6 needs the permission staging — do it last, fallback documented).
3. Scaffold `video/` per §4, install packages, copy theme, generate SFX
   (20 min).
4. Build scenes S01–S15 against the storyboard, landscape first (2–4 h).
5. Render landscape draft, extract the 11 check frames, inspect, fix, loop
   until clean (45–90 min).
6. Vertical variant: adjust layouts, re-verify safe zones (30–45 min).
7. Final renders at crf 16, attach both mp4s to the next GitHub preview
   release, link them in the README (15 min).

Total: roughly one focused day, dominated by scene-building and the
verify loop.

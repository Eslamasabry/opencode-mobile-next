# Visual language v1 (owner-approved 2026-09-26)

The owner looked at the kit's first galleries and said "it still looks like shit but x3". He then approved this direction ("This looks cool tbh, good job!").

- **Source of truth:** the design canvas at https://claude.ai/artifact/2Mf9YQmyBb5YkfAVYzRqgx.
- **Renders:** kept with the canvas files, in `docs/design/visual-language-2026-09-26/`.

This document turns the canvas into tokens. `lib/ui/app_theme.dart`, `lib/ui/theme_packs*.dart` and `KitTokens` (`lib/ui/kit/kit_tokens.dart`) implement them. The kit parts read only tokens (kit-v2.md §8, §9).

## 1. Character

It should feel like a calm instrument. The rules:

- One neutral ground, with depth from surface steps rather than shadows.
- One accent, used only for the primary action and for "working".
- **Amber always means "needs you".**
- Red appears only for acts that destroy or stop something.
- Text is large and bright. Grey is kept for secondary lines.
- Content is grouped on rounded panels with hairline separators, and there are no per-row ⋮ menus.

## 2. Type

- **Family:** Geist, with Geist Mono for code and technical values. Both are SIL OFL; bundle them as assets (`assets/fonts/geist/`) with their licence files.
- **Replaces:** Space Grotesk (AppDisplay) and the system sans-serif.
- **Arabic:** the system font (Noto) stays the fallback. Tests load Noto Sans Arabic from `test/fixtures/fonts`.

| Role (KitText) | Size / line height | Weight | Tracking | Use |
|---|---|---|---|---|
| `largeTitle` | 32 / 38 | 650 | −0.025em | the screen's own name (Settings, a project) |
| `title` | 23 / 29 | 650 | −0.02em | a sheet or dialog question |
| `headline` | 16.5 / 22 | 600 | −0.01em | top bar, card titles |
| `body` | 15.5 / 23 | 400 | 0 | transcript, paragraphs |
| `rowTitle` | 15.5 / 21 | 500 | 0 | a row's first line |
| `secondary` | 13.5 / 19 | 400 | 0 | a row's second line, meta |
| `label` | 13 / 18 | 600 | 0 | section labels (never uppercase) |
| `caption` | 12 / 16 | 600 | +0.02em | "Needs you · 40 s ago" |
| `button` | 15–16 / 20 | 550–600 | 0 | buttons |
| `mono` | 12.5 / 19 | 400 | 0 | code, commands, paths (LTR isolated) |

Geist is a variable font, so the fractional weights (550, 650) are real. If a static cut is bundled instead, use the nearest weight.

## 3. Colour

### Dark (default)

| Token | Value | Use |
|---|---|---|
| `ground` | `#0B0C0E` | screen background |
| `surface1` | `#141518` | grouped panels, cards |
| `surface2` | `#1B1C20` | sheets, composer, user bubble |
| `surface3` | `#26282D` | secondary buttons, chips, icon tiles |
| `hairline` | `rgba(255,255,255,.07)` | separators, borders |
| `text1` | `#F3F3F1` | primary text |
| `text2` | `#A3A5AB` | secondary text, section labels |
| `text3` | `#8A8D94` | meta, placeholders (≥ 4.5:1 on `ground`) |
| `accent` | `#3DDC8A` (per theme pack) | primary buttons, working marks, links |
| `onAccent` | `#03140B` | text on the accent |
| `attention` | `#FFB547` | needs you |
| `attentionSurface` | `rgba(255,181,71,.09)` | needs-you cards |
| `attentionLine` | `rgba(255,181,71,.30)` | needs-you borders |
| `danger` | `#FF7A7A` | destructive icons and text |
| `dangerFill` | `#E5484D` + white text | the one destructive button |
| `scrim` | `rgba(0,0,0,.58)`, with the content behind blurred at σ 3 where Impeller allows | behind sheets and dialogs |
| code | keyword `#C7A6FF`, string `#FFB88A`, type `#7FD1FF`, added `#3DDC8A` on 10 %, removed `#FF7A7A` on 10 % | code and diffs |

### Light

| Token | Value |
|---|---|
| `ground` | `#F3F3F1` |
| `surface1` / `surface2` | `#FFFFFF` |
| `surface3` | `#E9E9E6` |
| `hairline` | `rgba(0,0,0,.08)` |
| `text1` | `#111214` |
| `text2` | `#50535A` |
| `text3` | `#62656C` |
| `accent` | `#0B8A4A`, with `onAccent` `#FFFFFF` |
| `attention` (text) | `#9A5700` |
| `attentionSurface` | `rgba(255,166,0,.12)` |
| `attentionLine` | `rgba(190,110,0,.30)` |
| badge | `#FFB547` with `#1A1000` text |

### Theme packs

A theme pack changes only `accent`/`onAccent`, and optionally the ground's hue by a few degrees. The canvas offers green `#3DDC8A`, blue `#5AB0FF`, orange `#FF8A4C` and violet `#C7A6FF`; the light-theme equivalents are `#0B8A4A`, `#1F6FEB`, `#C2410C` and `#6D4AFF`.

Surfaces, attention and danger never change with the pack, so meaning stays constant.

## 4. Shape and space

- **Radii:**
  - panels: 18;
  - needs-you and request cards: 22;
  - sheet top: 30;
  - dialog panel: 24;
  - buttons: 13–16 (44–52 tall);
  - chips and pills: 999;
  - icon tiles: 9–14;
  - code blocks: 14–16;
  - composer: 26 on phones, 18 on PC.
- **Space:**
  - screen gutter: 16;
  - panel inner padding: 16;
  - rows: 60 tall with two lines, 54 with one;
  - separators are inset to the text start;
  - 22 between sections;
  - 8 between a section label and its panel.
- **Depth:** none from shadows. Surface steps carry it. The one exception is the needs-you ring (a 4 px attention glow at 6 %).
- **Icons:** stroke 1.8. A row's leading icon sits in a 30 px `surface3` tile. The bottom navigation is a floating 60 px bar: 22 px radius, `surface2` at 82 %, blurred.

## 5. Patterns that change with it

- **Buttons:**
  - primary: `accent` filled, with `onAccent` text;
  - tertiary: text in `accent` (`danger` when destructive, `text3` when disabled; R5 2026-09-27: `text2` read as disabled beside muted text);
  - tertiary: text in `text2`;
  - destructive: `dangerFill`, used only inside a confirmation.

  Pastel tonal pills go.
- **Rows:** grouped in `surface1` panels with hairlines, trailing a value in `text3` and a chevron. No per-row ⋮: long-press or right-click opens `KitRowMenu`.
- **Needs you:** always an attention card with its answer buttons in place (Work, Inbox, the transcript).
- **Transcript:**
  - the person's prompt is a right-aligned `surface2` bubble (20/20/6/20);
  - the agent's text is plain body text;
  - work is folded into one chip ("Read 3 files · edited 1");
  - code blocks have a file header with `+n −n` and a copy button.
- **Composer:** a `surface2` pill holding attach, the field, the model chip, voice, and send or stop. Send is an accent circle; stop is a `text1` circle with a `ground` square.
- **Sheets:** grabber, then an icon tile and a left-aligned `title`. Consequences are a `surface1` panel of rows. Buttons are stacked full-width on phones and right-aligned in a row on PC.
- **PC:** three panes (list 296 · conversation 700 max · changes 340), with keyboard hints on buttons.

## 6. Material: liquid glass (owner decision, 2026-09-26)

The owner saw liquid glass and liquid metal on the Work page and chose: "Forget metal, I like the liquid glass one in the artifact. Everything should be very very sharp and crisp." The reference is the canvas artboard "Work · liquid glass" (`GlassWork.dc.html`, variant `lens`), rendered at `docs/design/visual-language-2026-09-26/GlassWork.png`.

- **Where glass goes:** only the navigation layer that floats above content. That is:
  - the top controls (the server pill and the search button);
  - the composer;
  - the floating tab bar, whose active tab is a glass lens;
  - on PC, the sidebar header and toolbar.

  Content never gets glass: rows, cards, the needs-you card, the transcript and sheets' bodies stay solid surfaces. Glass never sits on glass.
- **The material:** built on `KitGlass` (`shaders/kit_glass.frag`), which is refraction at the rim, a background blur and a slight saturation lift.
  - **Behind anything with text:** the composer and any glass holding a text field or labels gets a dimming layer. Text behind glass must read as colour, never as letters.
  - **Fallbacks, as today:** Impeller and Android 12+ get the shader; older devices get frosted glass; Effects › Glass off gets a solid `surface2` at 94 %.
- **Ambient ground:** the theme may put two or three very soft colour fields on the ground behind content, so glass has something to bend. They belong to the theme; "none" is valid, and the default theme keeps them subtle.
- **Metal:** dropped. There is no metallic or chrome treatment anywhere.

## 7. Sharp and crisp (owner rule, 2026-09-26)

"Everything should be very very sharp and crisp." That means:

- **Type:** integer sizes only. Round the §2 table as follows:

  | Role | Before | After |
  |---|---|---|
  | `body` | 15.5 / 23 | 16 / 24 |
  | `rowTitle` | 15.5 / 21 | 16 / 22 |
  | `headline` | 16.5 / 22 | 17 / 22 |
  | `secondary` | 13.5 / 19 | 14 / 20 |
  | `mono` | 12.5 / 19 | 13 / 19 |
  | `title` | 23 / 29 | 24 / 30 |

  Text colours are opaque tokens, never text at partial opacity. No text shadows and no glow on text.
- **Edges:** separators and glass rims are exactly one physical pixel wide (`1 / devicePixelRatio`) and snapped to the pixel grid. A glass rim is a crisp 1 px light line on the top edge and a 1 px darker line on the bottom edge, not a soft glow.
- **Shadows:** none on content. Floating glass gets one tight shadow (y 6, blur 16, 30 %) so it separates without a halo.
- **Blur:** only behind glass, never on content, text or icons. Transitions do not blur or fade-scale; they slide or cross-fade on `KitMotion` quick/standard curves.
- **Native Android motion (owner rule, 2026-10-26: "Yes, max Android native animation and support."):** on Android a press answers with the platform's Material ripple (InkSparkle) bounded to the part's shape, pages use the predictive back transition, lists keep the stretch overscroll, and long-presses and selection changes tick like the platform's (kit-v2.md, the 2026-10-26 note at the top). This overrides "slide or cross-fade only" for those platform behaviours; the kit's own parts still never blur or fade-scale, and everything stays still under reduced motion.
- **Icons:** 20, 22 or 24 logical px only, at one stroke weight, aligned to whole pixels.
- **Images and illustrations:** decoded at the device pixel ratio with high filter quality. Illustrations are vector or shader, never scaled bitmaps.
- **Goldens:** the kit galleries render at the device pixel ratio (3.0 for phones). A golden that shows soft edges, a doubled hairline or a half-pixel offset is a bug.

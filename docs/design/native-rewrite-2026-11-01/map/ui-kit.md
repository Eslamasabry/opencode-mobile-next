# Map: UI kit, theming, styles, motion, tokens (area `ui-kit`)

Date of reading: 2026-11-01. Source: `/home/eslam/Storage/Code/wt/oc_app-batch1` (branch `int/batch1-2026-11-01`, HEAD `65e3f70a3`), read-only. Owner memory read from `/home/eslam/.claude/projects/-home-eslam-Storage-Code-oc-app/memory`.

Purpose: a rewrite agent that reads only this file must know every user-visible kit feature, every internal capability, every stored key, every rule the owner decided, and every trap already paid for. Feature ids are `UI-KIT-NN`. Appendix A maps every one of the 280 files in `lib/ui/kit/` to an id. Section K3 lists the Flutter-to-native equivalents with versions checked on the web on 2026-11-01; Appendix B is the reading list.

How to read the evidence tags: **[code]** = read in source; **[doc]** = stated in a repo doc; **[owner]** = a dated owner decision (memory or repo doc); **[commit]** = from `git log` bodies; **[web]** = checked on the web 2026-11-01; **[unread]** = I did not open the file, only its class docs, so details may be missing.

**Precedence warning.** Where docs and code disagree, code wins. Known drift: `docs/design/visual-language-2026-09-26.md` §2 and `STANDARDS` LOOK-12 list type sizes 32/24/17 and weights 650/600; the code (`KitText.styleFor`, after the 2026-10-30 "unslop" pass) uses 28/22/16 and weights 500. Section UI-KIT-03 gives the code values. `kit-v2.md` says "Nothing here is built yet" in its preamble; almost all of it is built.

---

## 0. Scale and shape of the thing

| Measure | Value | Evidence |
|---|---|---|
| Dart files in `lib/ui/kit/` | 280 files, about 106,900 lines (about 74,300 in the 180 top-level files) | [code] |
| Sub-folders | `chat/` (33), `diff/` (4), `glass/` (1), `live_background/` (+ `styles/` 22), `motion/` (7), `scenes/` (15), `style/` (9), `team/` (13) | [code] |
| Exported public parts in `kit.dart` | about 330 classes/functions in 376 lines of barrel with a doc table | [code] |
| Frozen API docs | 111 files in `docs/ux-system/kit-api/*.md` (18,958 lines) | [doc] |
| Spec | `docs/ux-system/kit-v2.md` (1,374 lines), `teaching.md`, `screen-standard.md`, `docs/ux-system/revamp/STANDARDS.md` (1,298 lines, rule ids LOOK-/LAY-/MOT-/STATE-/A11Y-/PERF-/SEC-/DATA-/KIT-) | [doc] |
| Theme files | `lib/ui/app_theme.dart` (600), `theme_roles.dart` (720), `theme_packs.dart` (381), `theme_packs_generated.dart` (624), `theme_packs_signal.dart` (125), `app_iconography.dart` (1,028) | [code] |
| Kit tests | `test/kit/` 278 files; `test/goldens/kit/` 3,136 files (golden PNGs and golden tests); capacity contract `test/kit/kit_capacity.json` (2,907 lines, 637 stress cases at 54 pumps each) | [code] |
| Localised strings | 9,468 keys in `lib/l10n/app_en.arb`; 619 start with `kit`; 124 kit files import `AppLocalizations`. Locales: en, ar, es, ja, pt, ru, zh (`AppLocaleStore.supportedLanguages`). Only Arabic is right-to-left. | [code] |
| Third-party packages used by the kit | `flutter`, `intl`, `clock`, `xterm`, `flutter_svg`, `highlight`, `webview_flutter`, `webview_flutter_android`, `shared_preferences`, `qr`, `mobile_scanner` (+ `dynamic_color` in `main.dart`) | [code] |
| Kit-owned MethodChannels | none (`grep MethodChannel lib/ui/kit` is empty). Native touches are indirect: `HapticFeedback`, `Clipboard`, `SystemChrome`, camera via `mobile_scanner`, web view via `webview_flutter_android`, Material You via `dynamic_color` | [code] |

The kit is the app's entire visual layer: AGENTS.md rule (owner, 2026-09-26): "No UI component to be used should remain outside our kit. All is coming from our library only." Outside `lib/ui/kit/`, screens may only arrange kit parts with layout, scroll, builder, semantics and focus widgets (gate G16, now absolute with an empty baseline).

### What a native rewrite must keep, in ten lines

1. One token set (colour roles x shape x space x type) that every part reads; screens never hold a colour, radius, duration or size literal.
2. Five switchable "Styles" (Solid default, Soft, Brutal, Paper, Signal) resolved by one function, plus Fine-tune (Corners, Density) and Saver (true black).
3. 31 colour packs (4 hand-spelled, 26 generated from four colours, Material You), a 7-swatch Accent, light/dark/system/saver, and a contrast guard that moves colours until they read.
4. Amber means "needs you" and nothing else. **Nothing is red, ever** (owner 2026-10-16). Accent never sits near amber (30 degrees of hue and DeltaE2000 of 20).
5. One motion vocabulary with five durations, a handful of springs, three motion levels (Full/Calm/Off) and the system "remove animations" always winning.
6. Android-native feel: Material ripple, predictive back, stretch overscroll, platform haptics, system bar colours (owner 2026-10-26: "max Android native animation and support").
7. Every part declares a capacity: a hard maximum width and an overflow rule per text slot; tested with 1,000-character strings, 300-character tokens, CJK, emoji, Arabic and 50-item lists.
8. Honest states: every wait escalates after 8 s, every disabled control shows its reason, every state has a word (never colour alone), every write has a receipt.
9. Technical text is isolated left-to-right, copyable, redacted of secrets, and lives under one "Details" fold.
10. Teach hidden gestures: tips (max twice, one at a time, cool-down 2 minutes) and a mini tour, replayable from Help > Tips.

---

## 1. Owner rules that bind every UI decision (checklist for the rewrite)

All are [owner] with dates unless a doc is cited. They override any earlier spec text.

| # | Rule | Where it lives |
|---|---|---|
| R1 | **Kit only.** Every UI component comes from one kit; a missing part is added to the kit even if one screen uses it (2026-09-26). | AGENTS.md, kit-v2 §9, gate G16 |
| R2 | **No red anywhere** (2026-10-16, absolute; sole exceptions: the Signal style's lamps/Stop key/alert ink, owner-approved 2026-10-30, and the markup pen's red ink): no danger role, no error colour, no red tint on text, icon, tile, chip, button, meter, diff line or terminal. A destructive act is made safe by confirmation (and a typed name), never by colour; the confirmed button is the strong neutral fill (`text1` fill, `ground` words). Amber (`attention`) = needs you. Stop is a neutral `text1` circle. | kit-v2 top note, STANDARDS LOOK-5, `test/no_red_scan_test.dart` |
| R3 | **No glass** (2026-10-02, 2026-10-26): "very artificial". Glass/Crystal styles, shaders and blur removed. **Narrow 2026-10-30 reversal:** frosted glass only on two places (chat composer pane and chat header while content scrolls under them), user strength Off/Weak/Balanced/Strong, default Balanced; no refraction, no rim light, no liquid look. | memory `no-glass-rule`, `KitFrost` |
| R4 | **Android-native feel to the maximum** (2026-10-26): ripple (InkSparkle), predictive back, stretch overscroll, haptics (long-press tick, selection click), system bars from the theme. | kit-v2 top note |
| R5 | **Micro animations everywhere** (2026-10-14): 150-250 ms transitions whenever UI appears or changes; framework APIs; reduced-motion honoured. Chat motion exceptions (2026-10-27): growing reply, work-line fold, step arrival, sent bubble lift from 0.92 scale, chat grows out of its row. | memory `micro-animations-wanted`, STANDARDS MOT-2/MOT-5 |
| R6 | **Hard maximum width per component** (2026-10-28): grows vertically, never sideways past its cap; contract file and stress gate. | kit-v2 §8.6 |
| R7 | **Frame text capped at 1.3x, reading text scales fully** (2026-10-31, option A): top bars, chips, buttons, composer, sheet headers, tabs never scale above 1.3x; replies, tool output, settings rows, help use the full system scale. Implemented as `KitChrome` (`kit_chrome.dart`, `kKitChromeMaxTextScale = 1.3`, clamps the system `TextScaler` so Android's nonlinear scaling is kept below the cap) on branch `fix/large-text-2026-10-31` (merge commit `f8c349394`), **not in batch1**. Treat as decided and pending merge. | memory `text-scale-cap` |
| R8 | **Simple and natural; judge every idea by what it removes** (2026-10-20). Plain words, never raw errors (raw text only under Details). | memory `simplify-and-natural`, `no-raw-errors-rule` |
| R9 | **Obvious options first**: main actions are visible first-page rows, never in a menu. **No new UI levels**: no new bar or row; fold status into an existing row. **Actions name their target** (no bare "Disconnect"). **Steer, never queue** where steering is possible. | memory files of those names |
| R10 | **Flat lists with dividers, fewer boxes** (2026-10-26, Solid): rows run the page width parted by inset hairlines; no panel, no icon tile. | `KitStyleTokens.flatLists`, memory `no-glass-rule` |
| R11 | **Teach what is hidden** (2026-10-21): tips and a tour, registry, Help > Tips replay. | teaching.md |
| R12 | **Arabic RTL**: directional layout only (no `left/right` literals), technical values isolated LTR, back at start, close at end, directional glyphs mirror. | LAY-8, gate G7 |
| R13 | **Design standard screen rules**: at most one primary action per screen, at most 7 items before scrolling, at most 3 text sizes, a row has at most one supporting line, pills only for exceptions, 8 s honest waiting. | `docs/ux-system/screen-standard.md` |
| R14 | **Design schools** are a user choice; Solid is the pixel-identical default; each style must work on every screen, light and dark, at AA contrast or not ship; Clay dropped as "too fluffy"; Signal added 2026-10-30. | memory `design-schools-direction` |
| R15 | **Show samples**: one full real phone screen per image, before/after. Judge visuals hard: compare a built screen side by side with the picked concept. **Owner tests last**: emulator pass before any APK reaches the phone. | memory files |

---

## 2. Feature index

| Id | Name | Area |
|---|---|---|
| 01 | Colour roles (`ThemeRoles`) and the contrast guard | Foundations |
| 02 | Theme packs, accent swatches, Material You, Saver, `AppTheme` | Foundations |
| 03 | Typography and text system | Foundations |
| 04 | Design tokens (`KitTokens`, shapes, status tone map) | Foundations |
| 05 | Surfaces: `KitSurface`, `KitPanel`, `KitGlass`, `KitDivider`, surface look | Foundations |
| 06 | Layout classes, maximum widths, rails | Foundations |
| 07 | Motion system and motion parts | Foundations |
| 08 | Page transitions, predictive back, row-to-page container transform | Foundations |
| 09 | Haptics | Foundations |
| 10 | Press feedback: `KitTappable`, press tracker, native ripple | Foundations |
| 11 | Icons and brand marks | Foundations |
| 12 | Text-safety utilities: bidi, time, copy, redaction, typing guard, link scope | Foundations |
| 13 | System bars, scroll behaviour, scrollbars, desktop affordances | Foundations |
| 14 | Effects model (`KitEffects`) and preference storage | Appearance |
| 15 | Styles (design schools): resolver, Solid, Soft, Brutal, Paper, Fine-tune | Appearance |
| 16 | Signal style | Appearance |
| 17 | Live background (20 looks) | Appearance |
| 18 | Frost (glass on composer and header) | Appearance |
| 19 | Appearance experience: guide, option cards, swatches, fine-tune, sliders, carousel | Appearance |
| 20 | Teaching: tip, tour, controller, registry | Appearance |
| 21 | `KitScreen` (the page frame) | Frames |
| 22 | `KitTopBar` family | Frames |
| 23 | Navigation: `KitNav`, rail, sidebar, tabs, breadcrumb, session tabs | Frames |
| 24 | Status system: status line, slot, scope, since, marks, tags, answer | Frames |
| 25 | Scroll helpers: jump pill, fade, arrival, refresh | Frames |
| 26 | `KitSheet` (the one sheet frame) | Modals |
| 27 | `KitConfirmSheet` and consequences | Modals |
| 28 | `KitDialog` (input, alert) | Modals |
| 29 | `KitMenu`, row menu, context region | Modals |
| 30 | `KitUndo` and `KitSwipeAction` | Modals |
| 31 | Technical details: fold, value, one-time code, `KitTerm` | Modals |
| 32 | Floating panel and floating action | Modals |
| 33 | `KitRow` family (rows, groups, switch, expand, section label, feed item) | Rows and inputs |
| 34 | Buttons and actions | Rows and inputs |
| 35 | `KitIconButton` | Rows and inputs |
| 36 | Chips, filter chips, status tags | Rows and inputs |
| 37 | Text inputs: field, search, ask field, start box | Rows and inputs |
| 38 | Choice controls: segmented, choice list, tiles, cards, hero question, slider | Rows and inputs |
| 39 | Pickers: date/time, wheel | Rows and inputs |
| 40 | State views, notices, report hook, error fallback, server trust | States |
| 41 | Progress and meters | States |
| 42 | Receipt, needs-you marker, capability explainer, checklist | States |
| 43 | Offline-first shells: last known, transcript excerpt, skeletons | States |
| 44 | Code, diff, log and file viewer | Content |
| 45 | Markdown (`KitMarkdown`) | Content |
| 46 | Images, gallery, markup, QR, scanner | Content |
| 47 | Terminal view and key bar | Content |
| 48 | Web preview | Content |
| 49 | Data display: chart, table, key/value, fact row, work graph | Content |
| 50 | Composer family | Chat |
| 51 | Transcript parts (turn, message, tool row, step timeline, work line, queue, agent strip) | Chat |
| 52 | Request/answer card family | Chat |
| 53 | Agent cards, start-screen parts, handoff | Chat |
| 54 | AI Team parts, board lanes, task cards | AI Team |
| 55 | Illustration system and scenes | Drawings |
| 56 | Home-screen widget UI parts (steer box, widget previews) | Widgets |
| 57 | Kit quality gates, galleries, capacity (test-first catalogue) | QA |
| 58 | Widgetbook screen catalogue | QA |

---

# PART A. FOUNDATIONS

## UI-KIT-01 Colour roles (`ThemeRoles`) and the contrast guard

**What exists.** One immutable `ThemeExtension<ThemeRoles>` carried on `ThemeData`; every colour in the app comes from one of its roles. Meaning never changes between themes, only hue. [code `lib/ui/theme_roles.dart`]

Roles (24 fields): `brightness`, `ground` (page), `surface1` (panels, cards), `surface2` (sheets, composer, own-message bubble, dock), `surface3` (secondary buttons, chips, icon tiles), `hairline` (translucent; never `withValues(alpha:)`), `text1/text2/text3`, `accent` + `onAccent` (primary action, "working", links), `partner` + `onPartner` (the theme's second colour or the chosen Accent swatch: rings Send/Stop, own-bubble edge, links, chosen mark), `attention` (needs-you text/icon), `attentionFill` + `onAttentionFill` (filled badge), `attentionSurface` + `attentionLine` (needs-you card), `success`, `scrim`, `codeKeyword/codeString/codeType`, `elevationShadow` (30 percent black in both brightnesses). Derived getters: `codeAdded = success`, `codeRemoved = text2`, `codeAddedSurface/codeRemovedSurface` = 10 percent of those. **No `danger` roles exist.**

Graphite (the default pack, `ThemePackId.opencode`, label "Graphite") spelled out [code]:

| Role | Dark | Light |
|---|---|---|
| ground | `#0B0C0E` | `#F3F3F1` |
| surface1 | `#141518` | `#FAFAF8` (one step under surface2 so a surface2 element keeps an edge) |
| surface2 | `#1B1C20` | `#FFFFFF` |
| surface3 | `#26282D` | `#E9E9E6` |
| hairline | white 7 percent (`0x12FFFFFF`) | black 8 percent (`0x14000000`) |
| text1 / text2 / text3 | `#F3F3F1` / `#A3A5AB` / `#8C8F96` | `#111214` / `#50535A` / `#62656C` |
| accent / onAccent | `#3DDC8A` / `#03140B` | `#087F43` / `#FFFFFF` |
| partner / onPartner | `#C7A6FF` / `#111214` | `#6D4AFF` / `#FFFFFF` |
| attention | `#FFB547` | `#9A5700` |
| attentionFill / on | `#FFB547` / `#1A1000` | same |
| attentionSurface / Line | amber 9 percent / 30 percent | `rgba(255,166,0,.12)` / `rgba(190,110,0,.30)` |
| success | `#3DDC8A` | `#087F43` |
| scrim | black 58 percent | black 58 percent |
| code kw / str / type | `#C7A6FF` / `#FFB88A` / `#7FD1FF` | `#6D4AFF` / `#B4480B` / `#0B6BA8` |

The spec hex values were nudged where they missed contrast (text3 dark `#8A8D94` to `#8C8F96`; light accent `#0B8A4A` to `#087F43`) : "where a VL hex misses a minimum, the minimum wins".

**Derivation and guard** (`deriveRoles`, `readableOn`, `_guardAccent`, `withAccent`, `withPartner`, `ThemeRoles.saver`): from an accent, ground and brightness it builds every role with contrast floors: text1 at least 7:1 on ground and every surface; text2/text3 at least 4.5:1 on ground and every surface they sit on (a disabled button is text3 on surface3); accent at least 4.5:1 on ground and surface1 (links are text) and carries onAccent at 4.5:1; attention at least 4.5:1; icons at least 3:1. A colour is moved only as far as needed toward text. WCAG contrast and CIEDE2000 (`deltaE2000`, Sharma-Wu-Dalal reference pairs tested) are implemented in Dart. `accentKeepsMeaning` (LOOK-39): an accent keeps at least 30 degrees of hue and DeltaE2000 at least 20 from attention in dark and light. `ThemeRoles.saver`: dark theme on `#000000` with surfaces moved one third toward black (OLED); light returns as is. `schemeFromRoles` builds a Material `ColorScheme` that says the same thing (secondary mapped to neutrals, tertiary to attention, error roles = neutral ink, because nothing is red). Picture-markup pen inks (4) are fixed and belong to the picture, not the theme.

**Rules to port:** LOOK-1 (no colour literal outside theme files), LOOK-3 (hairline alpha), LOOK-4 (attention only for needs-you; progress/warnings never use it), LOOK-5 (no red), LOOK-6 (accent only on primary fill, working marks, loading/progress bars, link text, current-selection mark, focus ring, cursor), LOOK-7 (a theme may change every value, never a meaning), LOOK-8 (contrast floors for every pack, light and dark), LOOK-14 (text at rest is opaque; disabled text uses `text3`; no text shadow or glow), LOOK-39.

**Tests to port:** `test/theme_roles_test.dart` (pack floors, whole role set, custom accent/ground, whole-pixel sizes, Geist faces), `test/accessibility_guidelines_test.dart`, `test/no_red_scan_test.dart`, `test/kit/kit_contrast_floors_test.dart`.

**Native rewrite note.** Keep this as the single source of truth in `commonMain` (pure Kotlin colour maths; `Color` as ARGB longs). A Material 3 `ColorScheme` is a derived view, not the model. Do not adopt Material 3's role set as the model: the kit has `surface1-3`, `partner`, `attention*` that M3 lacks.

## UI-KIT-02 Theme packs, accent swatches, Material You, Saver, `AppTheme`

**Packs** [code `theme_packs*.dart`, `preferences_types.dart`]: `ThemePackId` has 31 values, stored by name in `oc.themePack` (unknown name reads Graphite):
- 4 hand-spelled (goldens): `opencode` (Graphite), `catppuccin` (Mocha/Latte, mauve), `gruvbox`, `solarized`.
- 26 generated from four source colours per brightness (background, text, primary, secondary, optional success), one recipe for all so a test holds all of them to the same contrast floor: dracula, nord, tokyoNight, oneDark, monokai, rosePine, everforest, kanagawa, ayu, nightOwl, github, palenight, synthwave, cobalt, midnight, paper, forest, ocean, sunset, sakura, lavender, mint, coffee, slate, amber, highContrast.
- `dynamic` = Material You: `main.dart` calls `DynamicColorPlugin.getCorePalette()` once and fills `harvestedDynamicPack`; below Android 12 / desktop / tests it falls back to Graphite; success colour is fixed (`#86D8A5` dark / `#1E7A44` light) because dynamic palettes carry no stable green.
- Picker shows "most-used" first (`_themePicks`: Graphite, Nord, Catppuccin, Dracula, Tokyo Night, Gruvbox, Rose Pine, Solarized) then "See all"; Material You is offered only when harvested.

**Accent swatches** (`appearanceAccents`, stored by id in `oc.appearanceAccent`, empty = "Match theme"): mint `#3DDC8A/#087F43`, teal `#3CCFCF/#0D7377`, blue `#5AB0FF/#1F6FEB`, violet `#C7A6FF/#6D4AFF`, pink `#F2A7E6/#AD2E99`, mono `#F1F2F4/#262B36`, silver `#B9BDC6/#5A606C`. The Accent drives `partner`; `accent` stays the pack's main colour. Guarded again by `withPartner`. Also `graphiteAccents` (green, blue, teal, violet) for Graphite's main accent.

**Appearance mode** `AppAppearance { system, light, dark, saver }` in `oc.appearance`, default **dark**. Saver = true black + effects stripped (see UI-KIT-14).

**`AppTheme`** [code `app_theme.dart`]: builds `ThemeData` from roles (`dark/light/saver/fromPalette/fromRoles`), sets Material component themes so stray stock widgets still look right, all elevations 0, and `AppTheme.shaped(theme, kit)` re-applies corners from tokens for stock widgets (buttons, chips, FAB, fields, cards, dialogs, sheets, snackbars, menus, tooltips); memoised. `AppTheme.forLocale` for Arabic swaps the family to `sans-serif` with fallback `Noto Sans Arabic, Noto Naskh Arabic, Arial`, letter spacing 0, and rebuilds `KitTokens` from the new text theme. `AppTheme.maxTextScale = 2.5`. Status colour map `AppStatusTone { neutral, progress, ok, attention, failure }` (neutral=text2, progress=accent, ok=success, attention and failure=text1 in different shapes).

**Rules:** LOOK-9 (ambient colour fields on the ground belong to the theme; screens never add gradients), LOOK-7. **Tests:** `theme_roles_test`, pack goldens, `appearance_*` tests.

**Native rewrite note.** Port the 26-pack generator as data (four colours each) plus the same recipe; Material You via `dynamicDarkColorScheme/dynamicLightColorScheme` (Android 12+) feeding the same pack shape. Keep storing the pack by enum name so the first-launch migration can read `flutter.oc.themePack`.

## UI-KIT-03 Typography and text system

**Fonts** [code `pubspec.yaml`]: `AppSans` = Geist variable (regular + italic), `AppMono` = Geist Mono variable (+ italic), both SIL OFL (`assets/fonts/geist/`); variable weight axis so fractional weights are real. Icon fonts `AppPhosphorRegular/Duotone/Fill` (UI-KIT-11). Arabic: system sans with Noto fallback, zero letter spacing, tests load Noto Sans Arabic.

**Roles** (`KitTextRole`) as coded **today** (`KitText.styleFor`, 2026-10-30 "hierarchy from size and colour, not weight"): largeTitle 28/34 w500 tracking -0.28; title 22/28 w500; headline 16/22 w500 tracking -0.17; body 16/24 w400; rowTitle 16/22 w500; secondary 13/18 w400; label 13/18 w500; caption 13/18 w500; button 16/20 w500; mono 13/19 w400 (AppMono). Styles with their own type (`KitStyleType.firmWeights`: Soft, Brutal, Paper, Signal) use the older firm table: titles 650, headline/label/caption/button 600, secondary 500; Brutal display weight 800, Paper 750 (page title 24), Signal 700 (page title 22, engraved capital labels). Integer sizes only (LOOK-13). No `number` role: tabular figures via `KitText(tabular: true)` (LOOK-18).

**Tones** (`KitTextTone`): primary(text1), secondary(text2), tertiary(text3), accent, onAccent, attention, success. Always opaque (LOOK-14).

**Parts:**
- `KitText` (all roles; `.mono` with `KitMonoCut {wrap, end, middle}`; selectable variants; `pageTitle` flag), `KitSelectable` (selection region; toolbar offers Copy and Select all only; `KitSelectMode {always, finePointer}`), `KitLtr` (forces a technical block left-to-right).
- `KitWordText`: ends at a whole word with an ellipsis, never mid-word (owner 2026-10-22); no full stop before the ellipsis (commit `a319bd04e`).
- `KitTextHalo`: soft ground-coloured halo behind text2/text3 on a bright live background (only used when a live look is bright).
- Sentence case only (LOOK-15); `KitText.sentenceCase`.

**Text scale** [code `KitText.appScaler/appScale`, `main.dart`]: the system scaler passes through untouched (including below 1.0) up to `AppTheme.maxTextScale = 2.5`; the in-app **Text size** (`KitTextSize` smallest 0.85, small 0.92, normal 1.0, large 1.15; `oc.textSize`) multiplies on top, easing on change. Only kit parts clamp scale (A11Y-8), each as a named constant: `KitText.capScale`, icons by `maxIconScale` (1.5), nav labels (`navLabelMaxScale` 2), badges and counts (`badgeTextScaleMax` 1.3), terminal (2, keys 1.3), monogram (1.3), `KitTopBarScope` wraps at 1.3. The 1.3x frame cap of R7 is not in batch1 (see §1).

**Behaviour traps:** Android hands the font scale as a float, so 1.3 arrives as 1.2999999523: `KitTopBarPill` tests `>= 1.29`, not `>= 1.3` (impeccable audit 2026-10-28 #7). Supporting lines wrap rather than truncate (A11Y-8); titles may truncate only in one line below 1.3x and two lines from 1.3x.

**Tests:** `kit_text` goldens, `app_text_scale_test`, `text_scale_overflow_*` (G6/G19), `kit_mono_label_spacing_test`.

**Native rewrite note.** `TextStyle` per role in a `KitTypography` value; variable font weights via `FontVariation.weight` (Compose supports variable fonts on API 26+); `Modifier`-free tone enum. Android nonlinear font scaling (API 34+) means a 1.3 cap must clamp via `LocalDensity.fontScale` with `Density` copy, not a linear scaler.

## UI-KIT-04 Design tokens (`KitTokens`)

One `ThemeExtension<KitTokens>`: colours (the roles), spacing scale `space1..6 = 4, 8, 12, 16, 20, 24`, and every shape/size/type token parts read (`KitTokens.of(context)` = theme extension under the person's Fine-tune knobs, memoised; identical object at Round + Comfortable). [code `kit_tokens.dart`, `kit_shape.dart`]

Values (defaults): rail 20 (modal side rails); sheet radius 30 (top corners); dialog panel radius 24; panel corner 18; card (needs-you/request) 22; button radius 14 and height 52 (48 Compact, never below target); field radius 14; popover 14; code radius 14; icon tile 28 dp, radius 9; composer radius 26 (18 on wide); bubble radius 20, tail 6; row height 56 one-line / 64 two-line; gutter 16; section gap 24; label gap 8; nav height 60, radius 22; panel inner padding 16; pill = stadium; chip height 32 (48 dp hit area around it); `minTarget` 48; small icon 20; choice row min height 56; skeleton row 64; loading bar 2; progress bar 4 (radius 2); status line min height 52; handle 36x5 in a 22 band; confirm tile 44, icon 22, tint alpha .16, radius 12; request tile 36 radius 9; needs-you ring 4 dp at alpha .06, start edge 4, line alpha .5, dot 8; request card max height share .45 (.6 at large text); mark slot 32, ring 10, dot 12; avatar badge 16 with ring 2; QR max 240 with 4-module quiet zone; scanner window 240, bracket 28; meter 9 bars 6 wide, 12-20 tall; badge 18; swatch min width 112, preview 56; graph node 156, column gap 24, row gap 48, max 6 lanes; log folded lines 12; terminal bubble max height .4; illustration page 160 / inline 88; scene wash alpha .12; disabled alpha .38, stale alpha .6, duotone wash .2.

Shapes: `KitShape {square, tile, code, button, panel, card, dialog, sheet, pill, circle}`; surface levels `KitSurfaceLevel {ground, surface1, surface2, surface3}`.

**One status tone map** (decision D12): `AppStatusTone` to text tone and one glyph per status so no state is told apart by colour alone: neutral = empty ring, progress = sync arrows, ok = check, attention = warning triangle, failure = neutral error glyph.

**Hairlines:** exactly one physical pixel (`1/devicePixelRatio`), snapped (LOOK-21); focus ring two physical pixels; `KitTokens.hairlineWidth(context)`.

**Gate G20** pins these numbers; `kit_tokens_test`-style tests exist.

**Native rewrite note.** A `KitTokens` data class in commonMain, provided via `CompositionLocal`; density-aware pixel snapping must be done in px not dp.

## UI-KIT-05 Surfaces: `KitSurface`, `KitPanel`, `KitGlass`, `KitDivider`, surface look

- `KitSurface`: the one solid box: fill from surface step, token shape, token padding (`KitSurfacePadding {none, compact(12), panel(16)}`), optional hairline. A wrapper for cap purposes (cap applies to its content). [code]
- `KitPanel`: `surface1` block for content the person works with; header = 20 dp icon in text2 + headline. In Solid lists are flat so panels are rare.
- `KitGlass` (historical name): a plain solid floating surface for the dock, rail, shell top controls and composer: opaque `surface2`, hairline, token radius, at most one elevation shadow (y 6, blur 16, 30 percent; Solid dark has none after `6b7e6b09a`, "softer floating shadow in Solid, none in dark"). Content repaints on its own layer (perf, below).
- `KitDivider`: pixel-snapped hairline, optionally inset to a row's words (`KitDividerInset`); width follows the style (`dividerWidth`, Brutal 2).
- `KitSurfaceLook` (996 lines): the resolver every part calls (`decorate/resolve/wordsOn/divider/isPlain/flatLists/linkUnderline/styleFade`). Roles: content, control, track, floating, sheet, ground, field, code, divider, stateLayer, toggle. Rules in the resolver: the page is its fill alone and never casts; code stays opaque; fields and off-switches reach 3:1 at rest in every style but Solid (`kitStyleBoundary`); a content surface inside a content surface sits on the outer face (no nested fill/edge/shadow) in every style but Solid. `KitSurfaceDecoration` supports hard offset shadows (mirrored in RTL), paired soft shadows, inner edges and a face shift (pressed sinks into shadow). `kitSurfaceBox/kitSurfaceSliver/kitSurfaceMaterial` cross-fade 200 ms on style change, 120 ms on state.
- Gate G49: raw `BoxDecoration(`, `ShapeDecoration(`, `ColoredBox(`, `Material(color:` inside the kit outside the resolver only shrink.

## UI-KIT-06 Layout classes, maximum widths, rails

**Window classes** (`KitWindow`, `KitLayout`, measured on the window, never the device): compact < 600, medium 600-839, expanded 840-1199, large >= 1200; `shortHeight` 480 keeps the compact stacked layout (phone landscape); `KitLayout.finePointer` (mouse seen or desktop). Constants: reading 720, list 960, pane list 296, pane detail max 700 (min 360), side pane 340, sheet max 640, confirm medium 560, confirm dialog 480, dialog 480, undo 480, popover 320, state 440, lane 400, control 400, chip 320, glyph 96, bubble max share 85 percent, sidebar width (larger with large text). Nothing outside `kit_layout.dart` compares a width to a literal (G15x). [code, doc kit-v2 §8]

**`KitMaxWidth`** (`kit_max_width.dart`): the one place a part gets its cap; `.conversation(700) .reading(720) .list(960) .sheet(640) .confirm(560) .dialog(480) .popover(320) .toast(480) .state(440) .lane(400) .control(400) .chip(320) .glyph(96)` and `.sliver`; box narrower than the cap is untouched (phones unchanged); wider boxes get the cap and sit at the start edge (end edge for a value ending a row; middle for page states, sheets, confirmations). Roles `fill`, `wrapper`, `scope` have no cap. `KitTurn` paints 717 on purpose (copy/more buttons reach 17 dp past its edge so their ink ends on the turn's edge). Table of every part's role and measured width at 1,600 dp is in `kit-v2.md` §8.6.

**Other layout parts:** `KitReach` (how far a flat list may run its rows out over a sheet's rails so its words start on the title's line), `KitLeadColumn` (which leading column parts start words after: list 56 dp text rail = gutter 16 + leadColumn 40, or conversation compact 48), `KitOptical` (a trailing glyph's visible ink ends on the right rail: ink stands in from its box), `KitBottomInset`/`KitClearance` (how much of the bottom/start is covered by a pinned or floating part: dock, pinned primary, composer, keyboard, gesture inset; published by `KitScreen`, read by `KitUndo`, `KitJumpPill`, `endPadding`).

**Adaptive table** [doc kit-v2 §8.2]: sheet = bottom sheet (compact), bottom sheet capped 640 (medium), centred 560 panel or 400-480 end sheet (expanded/large); confirm = 480 centred dialog on wide; Esc closes; Enter confirms only neutral; top bar icons 2/3/labels; rows hover + focus ring + Enter; diffs side by side from expanded; request card shortcuts A (allow), D (deny), 1-9 (choices). Panes: list 296, detail 700, side 340 (LAY-5).

**Touch targets:** 48x48 minimum everywhere; no two targets' 48 dp areas overlap; a destructive target has 8 dp from any other (LAY-9).

**Gates:** G6 overflow (320, 360, 412, 600, 800, 840, 1280, 1600 and 915x412; text 1.0, 1.3, 2.0; LTR and RTL), G15/G15x, capacity gate (UI-KIT-57).

## UI-KIT-07 Motion system and motion parts

**`KitMotion`** [code `kit_motion.dart`] durations: `quick` 150 ms (control answering a touch), `standard` 250 ms (a part appearing/changing size), `rowReturn` 300 ms (page shrinking back into its row), `entrance` 900 ms (a drawing drawing in), `celebration` 1,400 ms, `breath` 4 s (ambient loop), `escalateAfter` 8 s (a wait becomes an explanation), `pressHold` 100 ms (least time a press stays visible), `undoWindow` 8 s, `copiedHold` 2 s, `logPoll` 2 s, `typingSettle` 300 ms (search debounce + result-count announce), `terminalCopiedNote` 2 s. Step light: `stepLap` 2000, `stepLapCalm` 4000, `stepBreathe` 1600, `stepFlash` 300, `stepFade` 200, `stepTick` 600, `stepSpring` 260, `stepSweep` 2200 ms. Context ring: value 600, colour 320, halo 700, drain 900, spin 1200 ms. Curves: `enter` easeOutCubic (anything progressing 0 to 1, including a part leaving that way), `exit` easeInCubic (reverse slot only, value runs 1 to 0; never `.flipped`), `emphasized` easeInOutCubicEmphasized, `rowReturnPath` = flipped emphasized, `land` easeOutBack (drawings only, never marks/controls), `pulse` easeInOut, `steady` linear, `stepEase/stepOut`. Marks never appear from nothing: `markFrom = .9` fade-in from there.

**Springs:** tab lens `lensLead` (stiffness 560, damping 32), `lensTrail` (210, 23), drag lead (700, 36), drag trail (260, 26), lift (260, 20); `replyGrow` (700, 53, critically damped, settles a new line in about 210 ms, carries speed when another line lands mid-way).

**Glow speeds:** `edgeLightMaxLapsPerSecond = 1/6`; `activityGlowLapsPerSecond = 1/8`; `classicGlowLapsPerSecond = 1/3.6`; `glowMaxLapsPerSecond = 1/1.8` (breath stays under 1.2 Hz); `stopRingLapsPerSecond = 1/2`; hue fade 450 ms.

**Three switches:** `reduced(context)` = system "remove animations" OR `KitEffects.motion == off` (only `kit_motion.dart` reads `disableAnimations`, MOT-8); `travels` = motion Full only (small changes may move as well as fade; Calm only cross-fades; Off instant); `loopsIn` = `loops && !reduced && Full` (ambient loops; `KitMotion.loops=false` under `flutter test` so screens settle).

**Parts** [code `motion/`]: `KitSwap` (cross-fade, no scale/size animation, **keeps the leaving child's state**, fix `8bff010c9`), `KitSpin`, `KitAnimatedBox` (paint-only fill/edge), `KitDim` (opacity for non-text only; debug assert fails if a paragraph is in the subtree), `KitAnimatedValue` (eased number; first build instant), `KitPace {quick, standard}`, `KitReveal` (part that unfolds downward and fades in on `child` non-null; keep mounted with `child: null` so it can fold; the only layout animation allowed), `KitEntrance` (fade + rise a few dp; rise only under Full), `KitAnimatedRows` (short keyed list whose rows arrive/leave; not for long lazy lists; restart with a new key when a search replaces rows), `KitPresence` (fades in/out with its slot along a row; not built while hidden), `KitRefresh` (pull-to-refresh drawing the portal brackets; same API as `RefreshIndicator`), `KitTabSwitcher`/`KitTabStrip` (UI-KIT-23).

**Rules:** MOT-1 (durations/curves only from `KitMotion`; waiting/hold times are named constants), MOT-2 (no blur, scale or fade-scale in transitions, named exceptions), MOT-5 (animate paint and transforms only; no layout animation in scrolling lists; tickers stop off screen), MOT-6 (a drawing's entrance plays once; at most one ambient loop per screen, only while waiting), MOT-7 (after one `pump()` no ticker under reduced motion), MOT-9 (scenes take time only from `KitIllustration`), MOT-10 (`flutter_animate` banned: pending timers fail widget tests), MOT-13 (battery saver or SEVERE heat drops effects to Calm), MOT-15 (while a reply runs only one thing moves: the glowing border OR Stop's ring).

**Tests:** `test/kit_motion_test.dart` iterates the manifest under both reduced paths; `kit_motion_app_test`, `kit/kit_motion_parts_test`, `kit_micro_animations_test`, `kit_native_motion_test`, motion baseline JSON.

## UI-KIT-08 Page transitions, predictive back, row-to-page container transform

[code `motion/kit_page_transitions.dart`, `kit_page_route.dart`]
- **Android**: `KitAndroidPageTransitionsBuilder` wraps Flutter's `PredictiveBackPageTransitionsBuilder` with fallback colour `ground`; system back gesture previews the page underneath (Android 14+, 13 with dev option) and can be dragged back to cancel; pushed page and Back button play the Android 16 fade-forwards slide; reduced motion = `KitPageTransitionsBuilder` (new page at once, a closing one only fades). `appliesTo` = Android build, not web.
- **Other platforms**: `KitPageTransitionsBuilder` = Material 3 shared axis along x, 250 ms.
- **`KitPageOrigin`** (owner request 2026-10-27): the conversation row the person taps; the next page pushed above that row's page grows out of the row's box into the full screen (container transform), contents fading in, and Back shrinks it into the row in 300 ms (`rowReturn`); the page under it stays still; `mark(context)` just before the push, claimed within 10 s else dropped; the system back gesture keeps the predictive preview and Flutter's builder takes over while the gesture runs (`route.popGestureInProgress`). **Demo lesson that matches:** predictive back fought the row-to-page container transform; the Flutter code solves it by yielding to the system builder for the duration of the gesture.
- `KitPageRoute` / `pushKitPage` / `replaceWithKitPage`: the only way to push; `MaterialPageRoute` and `PageRouteBuilder` are off the allowlist. A page keeps its state across the transition switch (route global key).
- Modal bottom sheets: Flutter 3.47 has no predictive back for them, so sheets keep their own drag and back.
- Fix history: the Conversations list holds still until a chat has landed in its row (`f7e775dd3`); tests wait out the Android transition (`2048185f7`).

## UI-KIT-09 Haptics

`KitHaptics` [code `motion/kit_haptics.dart`]: `send` (light impact: the person's words leave), `done` (success notification, once, skipped under reduced motion: a waited-for finish), `commit` (medium impact: a confirmed `stop/destructive/discard` act), `longPress` (platform long-press tick via `Feedback.forLongPress`: a long-press opens a menu or starts a selection), `select` (selection click: a switch flips, a segment is chosen, a wheel passes a value). Obeys Settings > Appearance > Vibration (`KitEffects.haptics`, mirrored in static `KitHaptics.enabled`). Never on scroll, plain taps of rows/buttons, failures, copy, sheets or dialogs (MOT-11). Gate G2: raw `HapticFeedback.` outside the kit only shrinks (now zero).

**Native:** `HapticFeedbackType.ToggleOn/ToggleOff/SegmentTick/ContextClick/Confirm/Reject/LongPress/TextHandleMove` exist in Compose `LocalHapticFeedback` [web, androidx.compose.ui.hapticfeedback]; map send=light tick, done=Confirm, commit=ContextClick-heavy or `Reject`-free firm tick, longPress=LongPress, select=SegmentTick/ToggleOn. Keep the "obey system touch feedback and the in-app Vibration switch" rule.

## UI-KIT-10 Press feedback: `KitTappable`, press tracker, native ripple

[code `kit_tappable.dart`, `kit_ink_layer.dart`]
- `KitTappable`: the one interactive primitive outside named controls: 48 dp hit area around a smaller child, keyboard focus ring (2 physical px), hover fill from surface steps, ripple on Android, same menu on right-click / long-press / Shift+F10 (`showKitMenu`), `KitTappableRole` for screen readers, disabled needs `disabledReason`, semantics `selected`, states enabled/hovered/pressed/focused/disabled/menu-open (menu-open holds the pressed fill).
- `KitPressTracker` (the shared press layer behind tappable, icon button, button, chip, segmented, terminal keys): reads **raw pointer events**, not the tap recogniser (which reports a press only after `kPressTimeout` 100 ms; a quick tap reports down and up together so a fill set on down and cleared on up never reaches the screen). Nothing to confuse with a scroll: pressed on pointer-down next frame. Inside a scrollable: waits `kPressTimeout`, a quick tap shows on release. When the tap fires: `confirm()` keeps the press visible at least `pressHold` 100 ms. Movement past touch slop, pointer cancel or lost arena clears at once.
- `KitInk`/`KitInkLayer`: on Android with motion on, a press draws the platform `InkSparkle` ripple at **12 percent** of the part's ink (`text1` or its foreground), bounded to its shape, drawn over the part's face by a layer keyed `inkKey`; elsewhere and under reduced motion the flat pressed fill. `InkSparkle.constantTurbulenceSeedSplashFactory` is used so goldens are deterministic. The tree is identical on every platform (a Stack) so toggling reduced motion never remounts the child. Terminal key caps keep the flat fill (as a keyboard's keys do).
- Fix: "one long-press path for the ripple and tick" (`2048185f7`); "Send with its long-press menu sends from the finger's lift" (`ee0815e9f`; Send fires on touch-down/up from the pointer because the composer slides away under the finger).

**Native:** `Modifier.clickable` with `ripple()` bounded to the shape already gives the platform ripple; the press-hold rule and the "lost first tap" lesson still apply to any custom pointer logic.

## UI-KIT-11 Icons and brand marks

- Icon family: **Phosphor 2.1.0** (MIT), shipped as three fonts (`AppPhosphorRegular`, `Duotone`, `Fill`), about 98 glyph pairs declared as `const IconData` in `AppIconography` (196 `IconData(` lines); `phosphor_flutter` package did not compile on pinned Flutter so glyph code points are literal. Regular is the action weight; duotone only for a selected nav destination; fill only for Stop. One glyph per verb through `AppIcons` (copy, run, stop, send, queue, retry, externalLink) (COPY-18). Sizes 20, 22 or 24 only (nav 24, action 24, inline 20). `matchTextDirection` set only on glyphs that point along the reading direction (back/forward, carets, reply, undo, sign-in, open-in-new, return); pinned by `app_iconography_mirroring_test`. Source hashes in `assets/fonts/phosphor/provenance.json`; glyph mapping in `docs/design/iconography/material-mapping.json`.
- `KitIcon` (sizes `s/m/l`, tone; decorative by default; `KitIcon.status(AppStatusTone)`; `KitIcon.mark(slug)` draws `assets/marks/<slug>.svg` in one flat colour). Leading and state icons grow with text up to `maxIconScale` 1.5, rounded to a whole physical pixel.
- `KitBrandMark` (the "open portal" mark from `assets/branding/open-portal/mark.svg` in the accent; sizes row-tile / standalone); retired `AppGlyph`, `AppBrandMark` stay reachable only via `app_iconography.dart`.
- 79 bundled single-colour brand/provider marks in `assets/marks/*.svg` (lobe-icons, MIT: anthropic, claude, codex, openai, gemini, grok, ollama, openrouter, opencode, pi, goose, kimi, deepseek and about 65 more); `KitVectorLogo` loads a provider logo SVG (`KitVectorLogoLoad`).
- Skill used for icon work (owner 2026-10-18): `agent-glyphs`; app stays Phosphor, brand marks monochrome.

## UI-KIT-12 Text-safety utilities

- `KitBidi`: wraps technical values (paths, commands, URLs, ids) in Unicode directional isolates (U+2066-2069) so Arabic copy around them never reorders; bidi literals are allowed only here and in a few files (G7). `KitLtr`, `KitTechnicalValue`, `KitCodeBlock`, `KitLogPanel`, `KitDiffView`, `KitField` mono/path/secret are forced LTR.
- `KitTime`: the one way to write a moment: clock in the person's 12/24-hour setting and locale, or day/date (F15). Uses `intl`.
- `KitCopy`: the one copy path: clipboard write + light tick + a single screen-reader announcement; never a SnackBar; the control that copied shows its own check for `copiedHold` 2 s.
- **`KitRedact`** (412 lines): display-safe masking of secrets in anything shown, copied or shared. A secret becomes `•••`; public prefixes stay as a hint (`sk-ant-•••`, `ghp_•••`, `AIza•••`). Masks: provider keys (`sk-`, `sk-ant-`, `sk-proj-`, `AIza`, `ghp_/gho_/ghs_/ghu_/ghr_`, `github_pat_`, `xoxa/b/p/r-`), `Bearer <token>`, whole values of `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie`, `x-api-key` headers (line start or explicitly quoted), values after `api_key/apikey/token/secret/password/passwd` + `=` or `:` (also UPPER_SNAKE `*_KEY/_SECRET/_TOKEN/_PASSWORD`, `access_key`, `secret_key`, `private_key`), URL user-info (`postgres://user:pass@host` to `postgres://•••@host`), PEM private key blocks, JWTs (three base64url segments whose first two decode to JSON objects). Never touches paths, git SHAs, UUIDs, ordinary long words; inside a file path `token=` is not a credential, after `?`/`&`/`#` in a URL it is. `registerKnownSecret(value)` (>= 6 chars, longest first) and `registerCredentialValues(json)` (walks a trusted credential response at ingress, before model decoding); process-local; tests call `clearKnownSecrets`. **Security invariant (AGENTS.md):** provider keys from `/config/providers` must never reach logs, diagnostics, notification copy, clipboard or test output (G12 `test/redaction_test.dart`).
- `kitTypingInField`: whether the person is typing now; bare-key shortcuts (N, P, [, ], PageUp, Home, End) must stand down.
- `KitLinkScope`: lets a screen claim a tapped link (local dev-server links in a conversation) before it goes to the browser. Every URL the app did not author otherwise goes through `openExternalLink` (`lib/ui/widgets/external_link.dart`); `launchUrl` is off-limits (security invariant).

## UI-KIT-13 System bars, scroll behaviour, scrollbars, desktop affordances

- `KitSystemBars.of(context)`: clear status bar over the page, navigation bar in `ground`, icon brightness opposite the theme; every `KitScreen` page announces it (owner 2026-10-26).
- `KitScrollBehavior extends MaterialScrollBehavior` and overrides neither physics nor overscroll, so Android keeps **stretch overscroll** and platform physics; on desktop a vertical scrollable with a controller gets an always-visible draggable thumb (`KitScrollArea` supplies the controller; `KitScrollbar` for an inner scroller such as a code block; `KitOwnScrollbar` stops a second thumb).
- `KitContextRegion`: desktop right-click menu around a child; off desktop returns the child untouched (long-press sheets stay the only touch action surface).
- Keyboard model (kit-v2 §8.3): Tab follows reading order, focus ring always visible, Esc closes the top modal and obeys draft/dirty rules, Enter confirms only a neutral confirmation, arrow keys move within segmented/choice groups, Home/End/PageUp/PageDown in lists, tooltips repeat a label only. Desktop seam: `lib/ui/desktop/desktop_interaction.dart` (`desktopInteractions`, `platformCapabilities.isDesktop`) feeds `KitLayout.finePointer`. Gate G14x. (Desktop/Linux is a future target; Android is first.)

---

# PART B. APPEARANCE

## UI-KIT-14 Effects model (`KitEffects`) and preference storage

`KitEffects` [code `lib/state/effects.dart`, re-exported by `kit_effects.dart`] is the immutable bundle of the person's appearance choices; provided above the navigator by `KitEffectsScope`; read with `KitEffects.of(context)` (subscribes) or `.read(context)` (handlers); tests default to `KitEffects.defaults`.

Fields and defaults: `motion` KitMotionLevel {full, calm, off} = full; `celebrations` = true (folded into motion: stored false + full reads as calm); `haptics` = true; `activityGlow` = true (the glowing border while replying); `glowStyle` {classic, softRing} = classic; `glowColours` {one, two}; `glowSpeed` {slow, normal, fast}; `liveBackground` = **false**; `liveHeldByStyle` = false; `liveStyle` = `'aurora'`; `liveIntensity` {subtle .7, normal 1.0, bold 1.6}; `surfaceStyle` {solid, soft, brutal, paper, signal} = solid; `fineTune` = none (nullable knobs); `saver` = false (never stored). `withSurfaceStyle(style)` resets Fine-tune. `underSaver()` yields motion off, no live background, solid surfaces, celebrations off, without touching stored choices.

**Storage** [code `lib/state/app_preferences.dart`; ledger `test/fixtures/coverage/settings_preferences_ledger.json`]. All device-wide (not per profile), in `SharedPreferences` (Flutter prefixes `flutter.`), enums stored by `.name`, unknown/absent reads default:

| Key | Type / values | Notes |
|---|---|---|
| `oc.appearance` | string: system/light/dark/saver | default `dark` |
| `oc.themePack` | string ThemePackId | default `opencode` |
| `oc.appearanceAccent` | string accent id or absent | absent = Match theme |
| `oc.textSize` | smallest/small/normal/large | |
| `oc.effectsMotion` | full/calm/off | stored `oc.effectsCelebrations=false` with full reads as calm |
| `oc.effectsCelebrations` | bool | folded into motion |
| `oc.effectsActivityGlow` | bool | default true |
| `oc.effectsGlowStyle` / `Colours` / `Speed` | enum names | the page now offers one choice (Off, Classic, Soft ring); colours and speed are the app's own |
| `oc.effectsBackgroundOn` | bool | **new name 2026-10-26**: the background went off by default; old `oc.effectsLiveBackground` retired, read for cleanup only |
| `oc.effectsLiveHeldByStyle` | bool | live look off only because a style turned it off |
| `oc.effectsLiveStyle` | string look id | default `aurora` |
| `oc.effectsLiveIntensity` | subtle/normal/bold | |
| `oc.effectsSurfaceStyle` | solid/soft/brutal/paper/signal | names no longer present (retired Glass, Crystal) read as solid |
| `oc.effectsCorners` / `Density` | round/soft/sharp ; comfortable/compact | nullable knob = style default. The Appearance page asks only Corners and Spacing; `oc.effectsDepth` and `oc.effectsEdges` still exist in the ledger but the reader ignores them ("each style keeps its own depth and edges, whatever an earlier version stored") |
| `oc.glass` | off/weak/balanced/strong | `GlassChoiceStore`, separate string pref, default balanced |
| `oc.transcript.reasoningExpanded`, `oc.transcript.timestampsVisible` | bool | transcript display prefs |
| `oc.appLocale` | language code | en, ar, es, ja, pt, ru, zh |
| `oc.appearanceGuide` | JSON `{done?, seen:[question names], lastIn:{group:question}, last?}` | guided Appearance state |
| `oc.tips` | JSON map id to `{count, last (UTC ISO), done?}` | teaching state |
| `oc.draft.<target>.<profileId>` | string | `KitDraft` text; profile-scoped, swept on profile deletion; app-wide drafts use profileId `app` |

Write semantics (`AppPreferencesStore`): serial queue so a slow old save never replaces a newer one; multi-key `_commit` restores attempted keys on refusal (SharedPreferences has no transaction); a refused save throws `SettingsFailure(storageFailed)` and the page puts the choice back and says so; snapshot is shared by all controllers; `reload()` when the disk may have changed.

**Migration to native (day-1, same package):** read the above from `SharedPreferences` file `FlutterSharedPreferences.xml` (keys prefixed `flutter.`); string lists are stored by the Flutter plugin as a prefixed encoded string (prefix `VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu`; from general knowledge of the plugin, verify against the installed `shared_preferences_android` version), booleans/strings/ints are plain; do not assume `getKeys()` names without the prefix.

## UI-KIT-15 Styles (design schools): resolver, Solid, Soft, Brutal, Paper, Fine-tune

**Model** [code `style/kit_style.dart`, `kit_surface_look.dart`; doc `docs/design/design-schools/contract.md` §0 "as built"]: `KitSurfaceStyle` enum picks a `KitStyleSpec` (token object + one `resolve(KitSurfaceRequest)` returning a decoration). Parts call `KitSurfaceLook`; screens never know a style exists. Adding a style = enum value + spec file + one line in `kitStyleSpec`. High contrast (platform) forces Solid unless the style `keepsUnderHighContrast` (Brutal, Paper, Signal); Saver forces Solid.

**Fine-tune knobs** (`KitKnobs`): `KitCorners {round, soft, sharp}`, `KitDepth {flat, raised}`, `KitDensity {comfortable, compact}`, `KitEdges {hairline, none}`. Corners: Round keeps radius, Soft about 60 percent, Sharp = style's `sharpRadius` (Paper 0, Signal 4, default 2); stadiums and circles unchanged. Compact: rows 48/54, button 48, section gap 16, label gap 6, touch targets never shrink. Rules: high contrast = depth flat + edges hairline; a style whose shape comes from shadows (Soft) turns Edges on when Depth is Flat ("no invisible controls", WCAG 1.4.11). Changing the style resets the knobs.

**Style defaults and identity:**

| Style | Defaults | Identity |
|---|---|---|
| **Solid** (default) | Round, Flat, Comfortable, Hairline | Matte panels, hairline edge; at defaults returns the part's own decoration so a solid screen is pixel-identical; **flat lists** (`flatLists: true`), keeps nested surfaces; owner 2026-10-26 "flat lists with dividers, fewer boxes". |
| **Soft** (neumorphism) | Round, **Raised**, Comfortable, **None** | Surfaces take the ground colour, shaped by paired soft shadows (raised at rest, pressed in when chosen); `dependsOnDepth`; live background at most Subtle (.7); raised card with 4 dp attention edge, no amber wash; firm type weights. |
| **Brutal** (neo-brutalism) | **Soft** corners, Raised | Ink and paper: bold 2 dp ink edge on content and inputs, hard offset shadow with no blur toward the bottom end (4 content / 3 control / 6 floating; 0 when chosen, pressed or flat; mirrored in RTL), cream paper ground in light, heavy display type (weight 800), mono labels, `flattensNested` (only the outer surface casts), glow = hard 2 dp ring no halo; pressed control sinks into its shadow; divider width 2; live look at most Subtle; keeps itself under high contrast. |
| **Paper** (flat print / Swiss) | **Sharp** (radius 0) | Ink on newsprint: no shadows, no blur, square; rows set on the page parted by fine ink rules; only what you act on (button, chip, field, message box, own prompt) is a box ruled in ink; page title 24, display weight 750, mono labels; choosing Paper turns the live background off (still the person's switch, flag `liveHeldByStyle`); glow = a thin rule. |
| **Signal** | Sharp (radius 4) | See UI-KIT-16. |

`KitStyleTokens` fields: `defaults`, `sharpRadius`, `keepsUnderHighContrast`, `dependsOnDepth`, `type` (`KitStyleType`: displayWeight/tracking, largeTitle size/weight, titleSize, bodyWeight, monoLabels, engravedLabels, firmWeights), `live` (`KitLivePolicy.maxStrength/offWhenChosen`), `glow` (`KitStyleGlow.halo/lineWidth`), `flattensNested`, `keepsNestedSurfaces`, `dividerWidth`, `needsYou` (`KitNeedsYouLook`: tag caption or pill, tint, ring, start edge), `linkUnderline`, `fieldHalo`, `flatLists`, `track`. Spec hooks: `resolve`, `onColor` (chosen words), `resolveFilledLayer`, `adjustRoles` (only ground, surface1-3, hairline, scrim, elevationShadow may change; the accent guard re-runs after: Soft's lifted ground, Brutal's cream paper; applied once in `main.dart` via `kitStyleTheme`; memoised). Hooks H1-H11 listed in contract §0 (type tweaks, face shift when pressed, words colour, pills/shape tokens follow corners/density, ground per style, live policy, glow per style, no nested shadows, plate behind words, one backdrop group per page).

**Gates / tests:** `test/kit/kit_style_test.dart` (five styles x brightness x all 24 knob combinations x 5 roles; uniform radii wherever a shadow is cast; AA words on Brutal/Paper fills; no amber fills; RTL mirror; storage round trip), `kit_surfaces_golden_test.dart` (all five styles light/dark), `kit_chosen_words_test`, `kit_field_style_test`; G21 (no raw type/radius literals in screens), G49.

**Owner caveat:** "the many options barely change a thing" (2026-10-22) led to Fine-tune being reduced to Corners and Spacing. Do not rebuild all four knobs in the UI; keep the model but expose two.

## UI-KIT-16 Signal style

[code `style/kit_style_signal.dart`, `kit_signal.dart`, `kit_signal_slip.dart`, `theme_packs_signal.dart`; owner 2026-10-30, concept CD "railway signal box crossed with a hardware console"; docs `design-schools/schools/signal.md`]
- Palette: cream enamel and ink in light (ground `#EFE9DA`, slip `#FBF9F2`, track `#191815`, key `#F7F3E8`, ink key `#22211D`), a **night signal box** (warm near-black ground `#12110E`, slip `#221F19`, track `#8E8673`, not inverted) in dark; fixed colours in `theme_packs_signal.dart` via `kitSignalColors(roles)`; signal lights are the only status colour (green running, red needs-you, hollow ring done).
- `KitStyleTokens.track = true`: lists and the transcript run on one **track** down the start edge (`KitTrack.on`, `KitTrackPainter`, `kitPaintTrack`, `KitTrackDecoration` dashed siding as a sliver decoration): a feed row's state is a lamp on the track (`kitSignalHead`: dark rounded body 16x26 with one lamp, halo, breathes only where loops allowed via `kitSignalBreathes`), a step's node is a signal, done is a hollow ring (`kitSignalRing`), a conversation starts at a buffer stop (`kitSignalStartTick`), sleepers (`kitSignalTies`) beside the folded work line, running time as a **segment timer** (`kitSegmentTimer`: green digits in a dark window with ghosted unlit segments, same length as the words).
- Actions with their own fill (primary, Allow, Send) are **raised hardware keys** (`kitSignalKey`: fill, 1 dp edge, 3 dp lip underneath, no blur); filters, segments and fields are flat ink outlines; a chosen control is set in reverse (ink fill, enamel words); Stop is the signal's red key (`kitSignalStopKey`, fill `#B3261E` edge `#6E130E` in `theme_packs_signal.dart`; `alertInk` `#A8231C` for "Needs you"). **This is the one owner-approved exception to R2:** `test/no_red_scan_test.dart` allows red in `lib/ui/theme_packs_signal.dart` ("red means needs you there, in that one chosen style only; every other style stays without red") and `lib/ui/theme_roles.dart` (`MarkupInk.red`, the picture-markup pen ink). Port the same scan with the same two exemptions.
- Requests print as a **slip** from a printer slot (`kitSignalSlip`, `kitSignalInSlip`, perforation line); "Needs you" is engraved small capitals in the signal's red ink widely tracked (`kitSignalNeedsYou`, `engravedLabels`; Arabic has no capitals and keeps zero tracking); `kitSignalSample` is the Style picker's sample.
- Page title 22, display weight 700; live look off when chosen; glow stays (owner); `flatLists` true; keeps itself under high contrast.

## UI-KIT-17 Live background (20 looks)

[code `kit_live_background.dart`, `live_background/*`]
- `KitLiveBackground`: the ground of a page with a calm slow look on it (Settings > Appearance > Background). **Off by default** (page = plain colour). Never glass; cards/rows/sheets stay solid on top. Painted aurora is a chroma-only tint (12 percent light / 14 percent dark of its colour on the ground's brightness), no word plates, no text halo (owner 2026-10-26: "stupid", slow).
- `KitLiveStyle` (abstract): `id`, `paint(canvas, size, KitLiveFrame)`, `framesPerSecond` (default 12), `warmUp()`; `KitLivePalette` (theme colours only), `KitLiveMemory` (per-style mutable state). Registry `kitLiveStyles` order: **ambient** aurora, mesh, dotRipple, topo, flowThreads, glowSpill, lightRays, constellation, halftone, liquidBlobs; **industrial** matrixRain, blueprint, circuit, oscilloscope, radar, honeycomb; **premium** brushedMetal, silk, caustics, bokeh. Default id `aurora`; unknown id falls back to it. Labels `liveStyle*` in ARB. Most picks: aurora, mesh, dotRipple, lightRays, bokeh, silk, topo.
- Looks react to a running reply (more/greener/faster: matrix rain, radar, circuit, honeycomb, brushed metal, silk, caustics, bokeh, blueprint scan line, oscilloscope).
- **`KitLiveClock`** (perf fix, owner phone 2026-10-23: 60 fps and 37-79 percent CPU with the look on): one shared timer for every page, **12 ticks a second**, nothing drawn between ticks, a slower look asks for fewer (aurora 6); after **30 s with no touch, scroll or key** it rests (no timer, no frame), the next input resumes without a leap; detached (no timer, no input listener) when the page is hidden (`TickerMode`), app not resumed, Live background off, motion reduced/Calm/Off, or Saver. Emulator result: 60 fps / 54.7 percent CPU to 6 fps / 3.9-5.8 percent, 0 at rest.
- `KitLiveLookPreview`: short live sample of the chosen look behind two mock chat lines (`live_mock_chat.dart`), "Off" when the background is off.
- Intensity: Subtle .7, Normal 1.0, Bold 1.6; style policy may cap (Soft, Brutal at most Subtle) or switch it off (Paper, Signal).

**Native note:** AGSL `RuntimeShader` (API 33+) or Canvas painters at 6-12 fps; keep the shared clock and the idle rest. Many looks are particle/mesh code in Dart; list is data-driven, port the six most-picked first.

## UI-KIT-18 Frost (frosted glass on composer and chat header)

[code `kit_frost.dart`, `kit_frost_header.dart`, `lib/state/glass_choice.dart`; owner 2026-10-30; commit `f52b92493`]
- ColorOS/HarmonyOS-style: blur + saturation lift + surface-colour tint + 3 percent seeded noise tile made in code + 1 px light line on top + faint ink edge. No refraction, no rim light.
- `KitFrostStrength`: weak (sigma 6, saturation 1.2, tint .62, header .72), **balanced** (10, 1.4, .70, header .80; default), strong (16, 1.6, .76, header .85 cap). Dark lays 10 percent less tint so blurred shapes still read.
- Exactly two places, and only while content is scrolled under them (`shown`): the composer pane (`KitComposer.layer`: chip row + field as one pane, adds no height, the field's `KitGlass` becomes the denser inner well; the green glow and hue stay outside the clip) and the chat header band (`KitScreen`'s bar; fades solid ground to glass over 180 ms while content is above the viewport; a body must opt in via `KitFrostHeader.topClearanceOf`). Solid means **no blur layer in the tree at all**.
- Held solid (`KitFrostHold`): `saver`, `motion` (reduced), `contrast` (high contrast), `style` (a style that keeps its own floating look). Stored in `oc.glass`. `KitFrostRole {composer, header}`.
- Perf: `ImageFilter.compose` (blur + saturation matrix) in a clip, no per-frame stacks.

**Native:** `Modifier.blur` is not backdrop blur; use Haze (`chrisbanes/haze`, KMP; 2.0.0-alpha03 on 2026-06-08 per a mirror, check Maven) or `RenderEffect` + `graphicsLayer` backdrop on API 31+; fall back to solid. [web]

## UI-KIT-19 Appearance experience parts

Settings > Appearance is built only from kit parts [code `lib/ui/screens/settings/appearance_screen.dart`, `appearance_one_page.dart`, `lib/state/appearance_guide.dart`]:
- **`KitGuide`** (897 lines): one decision at a time: a rail of chips that each show their current value (four groups: Colours, Style, Background, Text & motion), the open question with its "Changes" line, and on a guided visit a Next bar, a dot on the next chip and checks on answered ones. `KitGuideItem/Group/Question/Next`. First visit is guided; once finished or skipped (`oc.appearanceGuide`) the page becomes one scrolling page with every question in place ending with "Guide me again" and "Reset to defaults" (confirm in the page). Questions in order: mode, theme, accentColour, style, shape, look, strength, text, motion, glow. Saver holds style/shape/look/strength (chips say so; the guide skips them; each explains with a way to turn Saver off). Every choice saves at once; a refused save puts it back and says so. Arrival from search scrolls and washes the row (`KitArrival`).
- **`KitOptionCard`**: one visual choice drawn (a theme as a swatch in its own colours, a live look as its sample, a glow style as the border itself, a style's sample via the resolver, an icon+words); chosen = ring + check. **`KitAccentRow`/`KitAccentSwatch`**: "Match theme" tile (the theme's two colours overlapping) then round swatches. **`KitSwatch`/`KitSwatchGrid`/`KitThemePreview`**: pick colours by looking (min width 112, preview 56, columns reduce at 1.3x text; arrow keys; one Tab stop). **`KitFineTunePanel`/`KitFineTuneKnob`**: segmented knobs with the style's own choice in words and a reset. **`KitStepSlider`/`KitSliderStep`**: slider resting only on steps with small/large "A" at the ends (Text size). **`KitCarousel`**: cards swiped one page at a time with the next peeking and dots/bar (themes, looks), every card its own target.
- "Make it yours" tour (UI-KIT-20) walks the four chips.

## UI-KIT-20 Teaching: tip, tour, controller, registry

[code `kit_tip.dart`, `lib/state/tips.dart`, `lib/ui/teaching/teaching.dart`; doc `teaching.md`; owner 2026-10-21]
- **`KitTip`**: one sentence of 3-8 words anchored to a control (found by key); shows while `visible`, takes no touch of its own, any touch anywhere fades it, fades by itself after `hold` (about 4 s), then `onDismissed` once; announced politely; fades in and rises a few points (plain fade under reduced motion); above the anchor when there is room else below; aligned to the anchor's nearer edge; RTL and 2x safe; never amber. `KitTipBubble` is the look.
- **`KitTour`** (`KitTourStep(anchors, message, around)`): 2-4 steps, each highlights one real control cut out of a dim scrim, scrolls it into view, Next / Skip / Back / tap outside leaves; the highlight glides (jumps under reduced motion); card words are a live region. `KitTour.offer(...)` is the "Show me how . 30 s" card (never forced). `KitTour.show(context, steps)`.
- **`TipController`** (`ChangeNotifier`, the one instance is `ConnectionController.tips`): `offer(id, visit, maxCount=2, priority, moment)` returns whether shown; rules: at most 2 showings per tip, never again once `markUsed`, one tip at a time app-wide, **cooldown 2 minutes**, at most one new tip per screen visit (`Expando`), higher priority replaces lower, blocked by `TipMoment(questionWaiting, keyboardOverAnchor, onboarding)`; `replay(id)` from Help (counts nothing), `dismiss`, `reset` ("Show tips again"). Storage `oc.tips` (UI-KIT-14).
- **Registry** (`teachingRegistry`): `stop-after-step` (priority 2; anchor `chat-stop-button`; after a reply has run 10 s), `send-later` (`chat-send-button`; words wait and Send offers "Send later or repeat"), `hold-mic` (`composer-voice-button`; after a tap-use of the mic with an empty box), `make-it-yours` (tour over the four Appearance chips; offered once after the first reply; "Not now" counts as an answer); legacy id `appearance-studio` counts as answered. A test fails when an entry lacks a Help > Tips row or en/ar copy. Checklist for any new hidden gesture: visible twin first, tip registered with en and ar copy, trigger = moment of use, listed in Help > Tips.

---

# PART C. FRAMES AND NAVIGATION

## UI-KIT-21 `KitScreen` (the page frame)

[code `kit_screen.dart`] Top to bottom: top bar, status slot, pinned search, header rows, the one loading bar, body (with the jump pill floating over it), pinned bottom block. A page is `KitScreen(topBar: ...)`; a shell tab body is `KitScreen` without a bar. `KitScreenWidth {full, reading(720), list(960)}`. `KitScreen.twoPane` (list 296 / detail up to 700; selection fills the detail instead of pushing a route) and `threePane` (large: plus side 340). Pinned bottom sits `space2` above whatever is below (dock, gesture inset) or the keyboard; publishes its measured height via `KitBottomInset`. One status line per window: a screen with no `KitStatusLineSlot` above owns one; under a slot it contributes. Sets system bars (UI-KIT-13). Debug asserts after each frame: at most one primary `KitButton`, one drawn `KitStatusLine`, one `KitRefresh`, one `KitDetailsFold` visible (LAY-12). Hosts the frost header (UI-KIT-18). States: default, loading, status, search, keyboard, twoPane empty detail, twoPane selected, threePane.

## UI-KIT-22 `KitTopBar` family

[code `kit_top_bar*.dart`] `KitTopBar` (title where you are in the person's words, one line of state, optional switcher, brand variant, actions; compact title + at most 2 icons, extras in an overflow menu with disabled reasons; medium up to 3; wide actions with labels), `KitTopBarExit {back, close}` (Back at start, Close at end), `KitTopBar.shell` + `KitShellControls` (server pill, search, Settings gear on a phone: no nav bar on compact), `KitTopBarPill` (connected / reconnecting with word + working mark / not answering / needs-you badge; "Connected" gives way to the server name), `KitTopBarScope` (chip under the title naming what the page belongs to, with a compact menu; `KitTopBarLink` live line "Dev server running . Open preview"; `KitTopBarBranch` branch chip opening Git; `KitTopBarOrigin` "from Design review"), `KitTopBarCentred`, `KitTopBarTail`. Wraps at 1.3x text. Fixes: bar on the rails (`94004d94e`), buttons on the title's first line at large text (`7b3a2cd6a`), top bar painted on its own layer (perf, `b9428598b`; a page-level RepaintBoundary was tried and reverted `e7a017611`). `KitPageHeading`: large title under a bar with no title plus one quiet line.

## UI-KIT-23 Navigation

[code `kit_nav.dart`, `motion/kit_tab_switcher.dart`, `kit_session_tabs.dart`, `kit_breadcrumb.dart`]
- `KitNav`/`KitNavRail`/`KitNavDestination` (label, icon, selectedIcon duotone, `needsYou` count, optional sidebar `pane`): `KitNavLayout {none (compact: no bar, Conversations is home, search + gear in the top bar), rail (medium: floating rail at the start, icons + labels), sidebar (expanded/large: 296 dp, wider with large text)}`. Needs-you badge via `KitNeedsYou.badge`; KitNav never counts. Owner 2026-10-20 "no bottom nav" (Conversations home + gear; Files from inside the chat).
- `KitTabSwitcher` (destinations kept alive, fade-through: leaving one fades out in the first third of 250 ms, chosen fades in) and `KitTabStrip`/`KitTab` (labels, counts, needs-you dot paired with the count; scrolls sideways, keeps the selected tab in view; one Tab stop, arrows (mirrored RTL), Home/End, Enter/Space). The nav tab lens uses the springs of UI-KIT-07.
- `KitSessionTabs`/`KitSessionTab` (a terminal's open sessions as pills under the bar, shown from two; selected in solid mint; long-press for rename/stop). `KitBreadcrumb` (folder trail root, collapsed, truncated; current crumb semibold and not tappable; ancestors never run past the row).

## UI-KIT-24 Status system

[code `kit_status_line.dart`, `kit_status_slot.dart`, `kit_since.dart`, `kit_status_mark.dart`, `kit_status_tag.dart`, `kit_task_mark.dart`, `kit_status_answer.dart`]
- `KitStatusLine`: one row (icon, one line, optional action) for a condition on a working screen; never a card; replaces every `MaterialBanner` and update snackbar. `KitStatusKind` priority high to low: **connection**, **appStopped** (Android stopped the app), **heat**, **riskySwitch** (never dismissible), **work** (a team Now line, a live process, one-shot notices), **update** (app / code push / server), **info**. `KitStatus` is produced once per entity by its status source (screens pass the object, not strings); `KitStatusLine.of(status)`; `next` second line; `since` + `onSlow` escalate after 8 s. `KitStatusLineSlot` shows at most one, the highest; `KitStatusScope` carries app-wide conditions provided once above the Navigator; `KitStatusContribution` lets a mounted, ticker-enabled page add its own.
- `KitSince` (`KitSincePhase {idle, waiting, slow}`, `KitSinceTicks`): the kit's one wait timer: rebuilds exactly once when a wait turns slow at 8 s, then minute ticks. Used by state view, status line, receipt, field validation, checklist.
- `KitStatusMark` (`KitMarkState {waiting, working, done, failed}` + a `paused` flag): hollow ring / small spinner (a still dot under reduced motion; fixed to read `KitMotion.reduced`, kit-v2 §2.9) / check / failed glyph. `KitTaskMark` adds `needsYou` and `stopped`. `KitStatusTag` (`KitStatusTagTone`): small worded tag at the end of a feed row ("Needs you", "Running", "Done", "Failed"), never a control. `KitStatusAnswer` (`KitAnswerTone`: ok, paused, working, needs-you, off; `KitAnswerScale`): the one big centred plain answer ("Everything is running") + one small line (or live countdown) + one quiet action.
- Main-app glue: `main.dart` `_say`/`_showWaiting` post one-shot notices as `KitStatusKind.work` with `undoWindow` timer (not auto-cleared while accessible navigation is on).

## UI-KIT-25 Scroll and list helpers

- `KitJumpPill`/`KitJumpPillLayer`/`KitJumpEdge`: floating pill back to the newest end; centred on its edge, `space3` inside, never closer than gutter; host decides `visible`; for logs, when scrolled up; "away from latest" in chat is a 480 px threshold.
- `KitScrollFade`: a sideways-scrolling line whose edges fade where there is more past them (composer status chips, filter chips); never a hard cut mid-word.
- `KitArrival`/`KitArrivalScope`: a search result's arrival: scroll the row into view, move the screen reader to it, wash once in accent for `hold`; anchor ids like `effects-vibration`, never route strings.
- `KitRefresh` (UI-KIT-07). `KitAnimatedRows`, `KitPresence`.

---

# PART D. MODALS AND OVERLAYS

## UI-KIT-26 `KitSheet` (the one sheet frame)

[code `kit_sheet*.dart`, doc kit-v2 §1.1] `showKitSheet<T>(title, body, subtitle, height {content, half, full}, primary/secondary/tertiary KitActions, dirty, draft, routes, dismissible, sheetKey)`; `showKitFramedSheet` (a body that draws its own frame); `KitSheet` (frame for goldens/tablet); `KitSheetStep` (a second view replaces the first inside the one sheet via a listenable); `KitSheetTone` (attention tone used only by the request sheet). Frame: handle (bottom sheets only; semantic "Dismiss"), header (title <= 4 words, one-line subtitle, Close at end), scrolling body, pinned `KitActionBlock`; loading = 2 dp bar under the header; height changes use `KitReveal`, never an animated sheet height; keyboard lifts the actions and keeps the focused field visible; at 200 percent the pinned actions stay pinned.
**Adaptive:** bottom sheet on compact; capped 640 on medium; centred 560 panel on expanded/large; `full` becomes a 400-480 end-side sheet; a short window keeps the bottom sheet; Esc closes and obeys draft/dirty.
**Data safety (X2):** `KitDraft(target, profileId, controller)` keeps typed input silently across swipe/back/Esc/close (key `oc.draft.<target>.<profileId>`; `restore/save/clear`; empty removes the key); with only `dirty` the frame owns the drag, catches back/Esc and asks the discard question inside the sheet (**no sheet on a sheet**, §4.7); `dismissible: false` only while an irreversible step runs; `routes` (`RequestRoutes`) closes it when its request is answered elsewhere and leaves a receipt. Gate G10, G48 (a sheet with a multiline field must pass `draft:`).

## UI-KIT-27 `KitConfirmSheet`

[code `kit_confirm_sheet.dart`, `kit_consequences.dart`] `showKitConfirm(title, body, confirmLabel, kind {neutral, stop, destructive, discard}, cancelLabel, icon, consequences, typedName, alternative, details, routes)` returns true only for the confirm action (back, swipe, cancel = false). Start-aligned: neutral icon in a 44 dp tile, title (a question naming the thing, ends with "?"), body (what happens and whether it can be undone, <= 2 sentences), consequences panel (`KitConsequences`: one-line counted facts, marks `KitConsequenceMark`), typed-name `KitField` (confirm disabled with a visible reason until exact match; heavy deletes), `KitActionStack` (confirm first, cancel under it), optional alternative ("Export first"), details fold. Cancel words by kind: Cancel / "Keep running" (stop) / "Keep editing" (discard). Confirm labels are a verb naming the act, never OK/Yes/Continue/Confirm (G11). The confirmed button for stop/destructive/discard is the strong **neutral** fill (R2). Haptic `commit` for those kinds only, never with Vibration off. On failure the sheet stays open with a `KitNotice` and Try again. Raised from inside a `KitSheet` it replaces that sheet's content in place (no route). On wide windows: 480 centred dialog; Enter confirms only `neutral`. Undo vs confirm vs neither table: kit-v2 §4.1 (reversible = act then Undo; irreversible = confirm; harmless = neither; never both for the same act; never confirm restoring a prompt / starting a service).

## UI-KIT-28 `KitDialog`

[code `kit_dialog.dart`] `showKitInputDialog(title, label, confirmLabel, initial, helper, kind {text, mono, path, number, secret}, maxLength, validate, onSubmit async (error stays in dialog), alternative, dialogKey)` returns text or null on cancel/back/Esc/outside tap; `showKitAlert(title <= 4 words, body, details, action (max one), closeLabel)` blocks (tap outside does not dismiss). Field focused and selected on open, IME action submits, helper never truncated at 200 percent, text kept across rotation. 480 dp wide; Enter = primary for input on wide windows.

## UI-KIT-29 `KitMenu`

[code `kit_menu.dart`, 993 lines] The one popup: `KitMenuItem` (label, icon, onSelected, checked, destructive, disabled with reason, shortcut hint, group), `KitMenuGroup` (named heading), `showKitMenu(position | anchor context)`, `KitMenuPanel` (inline, galleries, top-bar overflow on PC). **Ordering rule** (`kitMenuLayout`): items keep their order within a group; groups keep the order of their first item; all destructive items move, stable, after a divider at the end. Max width 320. Opened by right-click, long-press (with `KitHaptics.longPress`), Shift+F10, or a `KitRowMenu` button. Rule: no per-row overflow button; the row's menu is `KitRow.menu`, opened by long-press / right-click.

## UI-KIT-30 `KitUndo` and `KitSwipeAction`

- `showKitUndo(message, onUndo, onCommit, onUndoFailed, undoLabel)` returns at once; the kit's **only** snackbar: "done, with Undo", one at a time, floating above the dock/composer/pinned primary (reads `KitBottomInset`), 8 s window (`undoWindow`), persists under accessible navigation (with a Dismiss variant), `working` while an async undo runs, error notice when the undo fails and no `onUndoFailed`; bar 480 dp wide from medium up at bottom-start; the control that acted shows feedback in place (copy shows a check on the button, a write shows a `KitReceipt`).
- `KitSwipeAction`: a row's swipe, only for acts whose treatment is Undo (archive, withdraw...); `id` is the Dismissible key, `onAct` returns true/false, then Undo shows; never a confirm and no destructive tint; **always has a twin** in the row's menu that runs the identical path (A11Y-5); states idle, revealing, acted, failed.

## UI-KIT-31 Technical details

- `KitDetailsFold`: the one technical fold, a 48 dp "Details" row that unfolds notes, values, text, child in that order; placed last and collapsed; engine words, ids, paths and ports appear only here; a value appears once, copyable, LTR; `showKitTechnicalDetails(title, text, ...)` = a sheet with the fold already open and the text whole (never an `AlertDialog`). `KitTechnicalValue` (label + value). `KitOneTimeCode` (a device code like `VPFI-KC2ZK` large and mono, LTR whatever the reading direction, with Copy; used by the Git sheet's GitHub device-flow sign-in and the integration tiles' provider sign-in).
- `KitTerm`/`showKitTerm`: a term that explains itself (48x48 target; bubble anchored; max 320).

## UI-KIT-32 Floating panel and floating action

- `KitFloatingPanel`: a small card floating over a transparent window (the home screen: widget steer box and style picker) on a dimmed ground, above the keyboard; tapping the ground closes it. A "fill" part (overlay).
- `KitFloatingAction`: a screen's one floating primary (56 dp round accent plus), laid over the bottom end of the body, clearing the dock/bottom inset.

---

# PART E. ROWS, BUTTONS AND INPUTS

## UI-KIT-33 `KitRow` family

[code `kit_row.dart`, `kit_row_parts.dart`, `kit_row_value.dart`, `kit_sliver_row_group.dart`, `kit_section_label.dart`, `kit_group_note.dart`, `kit_feed_item.dart`]
- `KitRow`: leading icon (tile, or bare in flat lists) / brand `mark` / `KitRowIcon(current: true)` filled accent circle, title (1 line; wraps from 1.3x; user's own titles may wrap, filenames break after `_`/`-` via `kitBreakableFileName`), at most one muted supporting line (two via `supportingMaxLines: 2`; at large text every line wraps, two from 1.3x, three from 2.0x), trailing `KitRowValue` (text3 value before the chevron) / `KitChevron` / switch / one `KitRowChip` act, `.menu` (long-press / right-click), `.swipe`, `.server:` label, `.unavailable(title, reason, enable)`, `destructive` (neutral; last in list after a divider), `enabled:false` + reason in supporting line (a disabled/locked row says its reason once to screen readers, `387bcc08e`). Heights 56/64; separators inset to the text start; hairline between icon-less rows starts at the words.
- `KitRowGroup` / `KitSliverRowGroup` (+ `kit_row_group_seam`): rows on one panel in Solid-flat: no panel, run the page width; in other styles surface1, 18 dp corners; group note (`KitGroupNote`: "2 settings aren't available on this server . Why").
- `KitSwitchRow` (whole row toggles; two-line supporting; `KitRisk(scope, until {off, conversation, hour})` risky-switch step unfolds inline and the screen's status line says it's on; `locked: 'Always included'`), `KitExpandRow` (controlled `expanded`; group or rare choice; chevron down/up), `KitRowMenu` (the one overflow button for a bar/section), `KitChevron`, `kitCurrentSpan` ("Connected . ") and `kitRecommendedSpan` ("Recommended . ") accent words so the mark is never colour-only.
- `KitSectionLabel` (sentence case, same start inset as group labels; trailing figures read with the heading, `8b3cf936b`), `KitFeedItem` (Chats home: title one line + one meta line with agent name, project, state, last line; `KitFeedRunning` since/command/line; `KitFeedStateTone`; read-only variant for remembered titles).
- Fixes: "one leading text rail, one row height" (`97c8fb98f`), "trailing glyphs end on the right rail" (`b950b8272`), "a row title never breaks inside a word; a value that cannot share the line goes under it" (`c9356d90c`, branch `fix/large-text-2026-10-31`).

## UI-KIT-34 Buttons and actions

[code `kit_buttons.dart`, `kit_action_stack.dart`] `KitAction(label, onPressed, disabledReason, icon, destructive, working, shortcut...)`, `KitButtonRole {primary, secondary, tertiary}`, `KitButton` (primary = accent fill + onAccent; secondary = surface3; tertiary = neutral `text1` words, `text3` when disabled; destructive tertiary neutral; no tonal pills; `working` only for the tap in flight, STATE-7), `KitActionBlock` (primary, secondary, up to two tertiary, rest in More; stacked full width on compact, one end-aligned row from medium with primary at the end; **switches to the stacked `KitActionStack` by itself when any tertiary is destructive**; asserts at most one primary; shows `disabledReason` as a muted line under the button), `KitActionStack` (each rare path on its own line), `KitInset` (pulls tertiary buttons back by their padding so labels align with the text above). Waiting hero fill (`kitHeroWaitingFill/Words`: accent at 14 percent over surface1, words at 55 percent moved toward text1 until 4.5:1). Height 52 (50 per LOOK-9, 48 compact). Labels wrap to two lines.

## UI-KIT-35 `KitIconButton`

[code `kit_icon_button.dart`] The one icon-only control: required semantic label (assert non-empty, G37), 48 dp target around a smaller glyph, `KitIconMotion` (glyph answer to press), `.copy` variant (clipboard + check + "Copied" announced, no SnackBar), states disabled (reason), working, selected, copied; tooltip on hover with label and shortcut on fine pointer.

## UI-KIT-36 Chips, filter chips, status tags

`KitChip` (`KitChipKind` five kinds; `KitChipTone` neutral... `active` = accent-tinted "this is on" pill; always carries a word; 32 dp pill inside a 48 dp hit area; removable kind with ×; bundled brand `mark`; long-press menu; internal keys `kit-chip-remove`), `KitChipWrap` (space2 gaps, run spacing keeps 48 dp hit areas clear: minTarget - chipHeight), `KitFilterChip` (on/off; `needsYou` draws amber so only the kit reads that colour), `KitFilterChips` (one scrolling line under a title with fading edges), `KitStatusTag`. Lines fade at the edge they run past, never a cut mid-word (`83f6e5d02`).

## UI-KIT-37 Text inputs

- `KitField` (1,262 lines): the one labelled input; label above (not floating), helper/error line under (error replaces helper, live region), counter only from 80 percent of `maxLength`, `KitFieldKind {text, multiline, mono, path, url, number, secret}`; `KitNumberFormatter` (digits only, Arabic-Indic digits and Arabic decimal separator become ASCII); `KitField.secret`: obscured, reveal + Paste buttons, asserts empty controller, no suggestions or IME learning, value excluded from diagnostics, "Saved . Replace" afterwards; `draft` required for multiline in a sheet; validating shows "Checking..." and escalates after 8 s; frame/ring/halo helpers `kitFieldFrame/kitFieldRing/kitFieldHalo` (field edge reads 3:1 at rest in every style; focus ring 2 px). Fix `b96da6fd4`.
- `KitSearchField` (+ `KitSearchNoMatch`): field + count line + active-filter chip; 300 ms debounce and one result-count announcement; `partial` ("12 loaded . searching the server..."), states empty/typing/results/partial/no-match/filtered/disabled; pinned in `KitScreen.search`; 2 lines at 1.3x.
- `KitAskField` (+ phase/note-tone enums): one glowing pill field that sends a request elsewhere ("Ask for a change..."), accent hairline + soft halo, mic while empty and Send once typed, note line never red/amber. `KitStartBox`: the new-conversation field (two lines, grows as you type, question as placeholder, read-only while starting).

## UI-KIT-38 Choice controls

- `KitSegmented`/`KitSegment` (2-4 short always-visible options; stacks into `KitChoiceRow`s when labels do not fit; chosen = neutral step in Solid with a check; arrows + Space on PC; `KitHaptics.select`).
- `KitChoiceList` (+ `KitChoice`, `KitChoiceOther` "Something else" with a draft, `KitChoiceRow`, `KitChoiceMark` radio/check, `KitPickerRow` "Language . English >" opens a sheet with `showKitChoiceSheet`; with one choice it is a plain row), `.single` (one tap = one callback; `sends: true` for answers), `.multi`; states loading/empty/choice-disabled/multi/other/sending/answered/answered-elsewhere. Replaces `DropdownButton`.
- `KitChoiceTiles` (+ `KitChoiceMore` "All models (42) >"): up to three big choices leading with the reason ("Default", "Cheapest"). `KitChoiceCards`: two or three large answers to a first page's one question (icon + title + cost line). `KitHeroQuestion`: display-type question a first page opens on. `KitStepSlider` (UI-KIT-19).

## UI-KIT-39 Pickers

`KitDateTimePicker` (1,431 lines; custom, no stock Material pickers): `showKitDatePicker/TimePicker/DateTimePicker` (calendar + typed entry + 12 h time with AM/PM kept whole above the button while the keyboard is open `e64657651`; invalid typed values show a reason) and `KitDateTimeRow` ("Due date . Oct 3, 2026 >", clearable, "Not set"). `KitWheel`/`KitWheelColumn`: roll a value under a band (1-3 columns: time, day, interval; scheduled prompts), capped text scale, `KitHaptics.select` per value.

---

# PART F. STATES AND FEEDBACK

## UI-KIT-40 State views, notices, report hook, error fallback, server trust

- `KitStateView` (862 lines): every not-normal moment: loading a whole screen, working, empty, error, missing capability, slow, finished; fixed slots icon (in an icon tile, not a tonal circle; or a `KitIllustration`), title saying the state now (never contradicting progress), body <= 2 sentences, progress, actions (the action hierarchy), Details fold last. Sizes `KitStateSize {page, inline}` (page: centred, no card; max width 440). `.missing(capability, title, why, enable, cost)`; `since` + `onSlow` change the body after 8 s to "Still waiting after 8 s" with Retry/Restart/Leave it running; error-tone states add **Copy details** and **Report a problem** automatically (network errors offer Try again / Switch server instead).
- `KitNotice`: a message about one part (connection test verdict, failed save): tinted icon, optional title, words, note lines, <= 2 tertiary actions, optional dismiss, one live region; no card; `.cost(items)` cost line ("About 550 MB memory per worker . uses battery"); `KitErrorKind` decides Report vs network fix; `KitReport` (already redacted details; never `error.toString()`); `KitReportHook.handler` is the one app-wide seam set at start-up so the kit imports no feedback code.
- `KitErrorFallback`: what a part shows in place of itself when it fails to build (`ErrorWidget.builder` via `lib/diagnostics/app_diagnostics.dart`): one plain sentence on the theme's ground, Back where a page can be left; safe above every theme and navigator.
- `KitServerTrust`/`showKitServerTrust`: explicit per-server HTTP consent and certificate fingerprint review; fingerprint stays expanded, wraps, LTR in Arabic; closing never grants trust. (Android cannot express Tailscale 100.64.0.0/10 in network security config, so the app checks addresses itself.)

## UI-KIT-41 Progress and meters

`KitLoadingBar` (the screen's one 2 dp bar under the top bar), `KitSkeletonRows` (placeholder rows, no words, no motion, hidden from screen readers), `KitProgress` (`waiting/known/staged/stopped/failed`; `.staged(step, of, label, eta)` "Step 3 of 5 . Installing . about 2 min left"; any job over 30 s uses staged or known with a caption) + `KitProgressView`, `KitProgressRow` (measured amount: quota window, download, context used, budget; loaded < 80 percent, near limit 80-99 percent automatic, at limit 100 percent in strong neutral, stale `asOf`, stacked `segments` with a legend that stacks at large text), `KitProgressRing` (+ `KitTrailMark`: one long job as one big ring with a real figure, a sentence, a slim step trail), `KitWindowRow` (quota window "5 hours ........ 18% left . 14:10" with a bar that fills with the share LEFT like a fuel gauge; tones normal/low/out/calm; stacks from 1.3x), `KitPlanBlock` (plan name + tier + summary + window rows), `KitContextRing` (+ `KitContextLevel` calm < 70, warning from 70, nearly full from 95; arc from 12 o'clock clockwise with the percent; one halo when 70 or 95 is crossed upward; compaction drain and spin), `KitContextChart`/`KitContextBar` (one bar per reply, dashed compaction line), `KitContextSheet`, `KitLevelMeter` (nine bars, decorative; the host says "Listening" in words).

## UI-KIT-42 Receipt, needs-you marker, capability explainer, checklist

- `KitReceipt` (`KitReceiptState`: sending, sent, confirmed, not confirmed, refused, answered elsewhere, automatic): a write's receipt in place; "Sent" never shown as "Done"; confirmed needs the server's echo; after 8 s "Not confirmed yet" + Try again; `automatic: true` + Undo for automation acts.
- `KitNeedsYou` (`KitNeedsYouReason` closed enum of three reasons; `.mark()`, `.span`, `.badge(count)`, `reasonWord`): the one attention marker, always with its word, counted once per request across servers; same words on tab, row, server row, header, notification; answered anywhere clears everywhere. Only `KitNeedsYou` and `KitRequestCard` draw the attention look (LOOK-24).
- `KitCapabilityExplainer`: a missing capability explained in place and offered where it can be turned on. Registry `KitCapabilities` mirrors `docs/ux-system/capabilities.json` (34 matrix capabilities; `KitCapability(host columns)`; `KitHost`), kept equal by `test/kit/kit_capability_explainer_test.dart`; 21 enable flows (`KitEnableFlows`: add-server, phone-setup, phone-setup-termux, server-generation, add-server-codex, add-server-paseo, add-tool-claude, model-sign-in, team-turn-on, team-host-guide, voice-model-setup, mcp-add, project-choose, project-git-init, allow-notifications, allow-background, allow-camera, ...); handlers registered at app start (`KitEnableRequest`) so the kit imports no screens. States explains, offers-enable, prerequisite, offer, folded, working. Rule: `whenMissing: hidden` only where no enable flow exists.
- `KitChecklist`/`KitStep`: a job made of steps with person-only steps mixed in (phone setup, Termux, Claude Code, AI Team turn-on, voice model, worktree, a team task's cycle); a mark per step from the engine, never inferred ("Already installed"); one optional overall bar; states before-start, working, person-step, slow, failed, paused, stopped, done, compact; `resume`.

## UI-KIT-43 Offline-first shells

`KitLastKnown`/`KitLastKnownRow` (what a list held last time, read-only while the live list loads), `KitTranscriptExcerpt`/`KitExcerptMessage` (a conversation's end as last saved, redacted and cut, read-only while history loads; "speed contract item 2"), `KitSkeletonTranscript`, `KitSkeletonRows`. Launch fix (`c45c02031`, jitter J1/J2, +4.2 s): the opening must be the home itself so nothing moves when the server answers (the root used to draw its own shell then swap it, moving bar, title, filters, rows; a saved-server card above the list moved rows by +47/-38 dp). STATE-18: offline values show last-known data with its age.

---

# PART G. CONTENT SURFACES

## UI-KIT-44 Code block, diff, log panel, file viewer

- **`KitCodeBlock`** (1,386 lines): code, command or output; always LTR, mono, selectable, redacted; `KitCodeKind`; header only for words (caption, file name, `+n -n`, "Copy commands"); capped (`Open full output`), wrapped or scrolling (`defaultWrap`: source code scrolls sideways on every window, output wraps on compact; per-reader preference), copied state; `$` prompt only where a new command starts (`kitCommandLineStarts` handles heredocs); line breaks after punctuation (`kitCodeBreakable`) so wrapping never splits an identifier. **Highlighting** (`KitCodeHighlight`): package `highlight` with an alias table of about 50 language hints (js/ts/python/bash/json/yaml/xml/css/sql/go/rust/kotlin/java/swift/cpp/cs/php/diff/gradle/dockerfile/makefile/protobuf/markdown ...), colours from the theme's code roles, **source over 20,000 characters renders plain**, parse cache of 48 entries, unknown hints are not guessed, an unclosed fence while streaming stays unhighlighted. Added/removed lines: `success`/`text2` at 10 percent with `+`/`-` glyphs carrying meaning.
- **`KitDiffView`** (+ `diff/*`, 4 files, 2,700 lines): the one diff renderer: unified on phone, side by side from expanded; one file header, "Change 1 of N" navigator across files, line selection when a handler is passed (`KitDiffSelection`), gaps `KitDiffGap` fold unchanged lines (`expandStep = 20`), statuses loading/empty/error/loaded/binary/renamed/too-big/selecting; model `KitDiffFile/Line/LineKind/FileStatus` (the caller builds it; the kit never sees API types); markdown rendering of file hunks; `showKitDiff` = a page (Close at end), never a sheet on a sheet.
- **`KitLogPanel`** (1,387 lines): the one log view: follows the newest line until the person scrolls up (then a `KitJumpPill`), virtualised, redacted, polls (`logPoll` 2 s) only while visible; `KitLogLine/Level/End/Size`, `KitLogBuffer` ring buffer (capacity `defaultMaxLines = 2000`, splits on newlines, keeps a partial last line open, counts dropped lines); states empty, live, quiet, ended, failed, failed-to-read, scrolled-up, dropped; folded under Details unless the page exists to show the log; one panel per source; search tools (`kit_log_panel_tools.dart`).
- **`KitViewer`** (`showKitViewer`, sheet/page/pane by window; `KitViewerContent` kinds text, code (line numbers + highlight + `initialLine` marked), markdown (Show source), image, pdf (`KitPdfPage` pre-rendered by `lib/platform/local_pdf.dart`, channel `oc/local_pdf`), svg (`flutter_svg`), delimited (CSV/TSV table, header flag), binary; `maxLines 2000`; truncated notice; wrap toggle; find (`finding`); states loading/error/empty/loaded/truncated/binary/page-failed).

## UI-KIT-45 `KitMarkdown`

[code `chat/kit_markdown*.dart`, about 3,200 lines; **hand-written parser**, no markdown package] Replies, thoughts, documents. Blocks: fences ( backtick or tilde, 3+; info string handed to `KitMarkdownBlockBuilder` for host blocks `choices`, `checklist`, `command`; else `KitCodeBlock`), headings 1-6, rules, bullet and ordered lists (ordered start number read from the first line; **a line indented under an item stays in that item**, `6c889be01`; nested list, fence or blank line ends the item), quotes, tables (`kit_markdown_table.dart`; mono cells; sideways scroll inside own box), inline code (path-like inline code becomes a **file link** via `KitMarkdownFileLinks.validate/open/readImage`; a path renders plain mono until it resolves true; `looksLikeFilePath` is conservative: multi-segment, no spaces, no scheme, anchored or with an extension <= 8 chars; strips `:line`), pictures (`![alt](target)` classified by `KitMarkdownImageKind`: drawable files, attachment ids (32-128 hex), server files rendered as tiles/thumbnails, unfinished image syntax hidden while streaming), links (through `openExternalLink`), **HTML comments hidden only as comment blocks** (`afe4d6bcc`, `1db87f739`; `kitMarkdownBlankComments`). Inline emphasis/strong/strikethrough via the `_inlineSource` pattern (compiled once, `9827da6d7`). `highlighter` hook for find-in-conversation (`KitFindMark`: accent wash .18 passive / .38 active behind a hit, never a text colour). Roles body or secondary only. States default, streaming, non-interactive, empty.
**Streaming** (owner 2026-10-27 chat motion): newly appended runs of the tail block start transparent and reach full strength over 250 ms easeOutCubic (colour only, no layout); the reply's box grows to each new height with `replyGrow` spring (clip leaves a 4 dp descender allowance, `2d9a65b45`; a shrink is instant); `KitStreamCaret` (thin accent bar after the last words of the tail block, zero-wide and no taller than the line, breathes once a second only where loops run; none after code, a table or pictures; the parsed blocks stay cached so the caret never forces a re-parse; dropped when `session.text.ended`/`time.end` arrives); follow-after-send glides to the newest row when on screen (jump when not); reduced motion = all instant.

## UI-KIT-46 Images, gallery, markup, QR, scanner

- `KitImage` (sharp raster: decoded at device pixel ratio for the laid-out size, `FilterQuality.high`, token `KitShape`; `KitImageSource` sealed - never a raw URL string, a network image arrives as a provider built by a service that authored its URL), `KitAvatar` (the one identity mark: image/icon or initials of the name on surface3; sizes tile 28 / mark 44; failed-image badge; never coloured by identity), `KitZoom`/`KitZoomController` (the one pinch/pan/zoom viewer with visible zoom buttons and keyboard zoom; replaces `InteractiveViewer`).
- `KitImageGallery` (`showKitImageGallery`): pictures of a conversation one swipe apart; each loads lazily (shown + neighbours), Try again; pinch/pan; Send to the message field. `KitImageMarkup` (`showKitImageMarkup`): finger pen in four fixed inks, Undo, Clear, crop rectangle with corner handles; result drawn at the picture's pixel size; `changed` false returns the original bytes; Send vs Done; PNG named `...-edited.png`. Photos picked are scaled to 2048 px q85 by the picker (a memory note: full-size frames overflowed the server's WebSocket queue).
- `KitQr` (package `qr`; dark on light card with a 4-module quiet zone in every theme; max 240; too-long link shows a notice in words). `KitScanner` (package `mobile_scanner`, QR only; square window 240 with corner brackets; instruction line; in-place line for a wrong code; **latches once `onCode` returns true**, stops the camera, ignores queued values; stops while the app is inactive/background and restarts; the decoded value is never stored in state, rendered, put in semantics or logged (SEC-2); permission and parsing stay with the host; `KitScannerCamera` abstract so tests fake it; `KitScannerFailure`).

## UI-KIT-47 Terminal

`KitTerminalView` (package `xterm`): `.live` (an xterm session painted in the theme's colours + `TerminalKeyBar`; phone terminal and server terminal are the same surface; selection handed to host via `selectedText`, copied through `KitCopy`; the live session is the person's shell and is not redacted) and `.output` (a finished/streaming command: program picked out of its path, flags and strings apart, output's own ANSI colours kept, the **end** of a long output shown first, passes through `KitRedact` before display and before `onOpenFull`); `KitTerminalText` pure span functions; errors are `text1` semibold never red (LOOK-5). `TerminalKeyBar`/`TerminalBarKey` (Esc, /, -, |, Home, ↑, End, PgUp / Tab, Ctrl, Alt, ~, ←, ↓, →, PgDn; optional `interrupt ^C` and `endOfInput ^D` row; `singleRow` compact with a sideways-scrolling tail; **sticky Ctrl and Alt** latch for the next key from the bar or the phone keyboard; written for this app because Termux's extra-keys view is GPLv3; caps stop growing at 1.3x so "PgDn" fits). `KitTerminalOsc52` (a program may put text on the clipboard; shown as a "Copied to clipboard" note 2 s), `KitTerminalLinks` + `showKitTerminalLinkSheet` (tapped link: whole address, Open link via `openExternalLink`, Copy link; wrapped links tappable; underline colons fixed `fcff85c2e`). Tablet input stays available with a mouse (`2a759917d`).
**Native note:** Termux `terminal-emulator` + `terminal-view` are Apache 2.0 (the app is GPLv3) [web, termux-app LICENSE.md]; ConnectBot `termlib` is a Compose terminal (libvterm) [web]; NovaTerm shows Termux modules wrapped in Compose. License of the exact modules must be checked before reuse.

## UI-KIT-48 Web preview

`KitWebPreview` + `KitWebPreviewController` (reload, goBack, `current`, `canGoBack`, phase loading/ready/failed): a dev server's page inside the app, **deliberately not a browser**: loads one origin (same host and port); navigation to anything else does not load, it is handed to `onLeave` (-> `openExternalLink`); **no JavaScript channel to the app is ever added**; cookies, cache and local storage cleared on open and on close; third-party cookies refused; file and content access off; camera, microphone, location denied; unsupported platforms show a plain explanation with an action. Android only (`webview_flutter_android`). Failure text is plain words with Try again; the technical reason is never shown. `lib/domain/dev_preview.dart` supplies the origin rules.

## UI-KIT-49 Data display

`KitChart` (bar/line, up to `maxSeries` series of `maxPoints` points, one scale, legend, text summary for a screen reader `KitChartWords`; colours accent, success, neutral, **never amber**), `KitMiniTable` (header + up to 20 rows x 6 columns, sideways scroll inside its own panel, cells wrap at a max), `KitKeyValue`/`KitKeyValueRow` (tabular figures, long values wrap), `KitFactRow` (+ `KitProvenance`: You told it / From a source / It guessed; FactsBook), `KitWorkGraph` (`KitWorkGraphNode/Edge/Geometry`, layouts `layers` (cubic curves with arrowheads) and `rows` (straight); nodes 156 wide, max 6 lanes; blocked links dashed 4; Fit on open; pure deterministic geometry tested; `KitZoom` inside).

---

# PART H. CHAT PARTS (single-owner chat library)

## UI-KIT-50 Composer family

[code `chat/kit_composer*.dart` 8 part files, about 3,800 lines; `kit_mic_hold.dart`; `kit_phrase_chips.dart`; `kit_task_chip.dart`; `kit_task_sheet.dart`; `kit_project_file_*`]
- **`KitComposer`** (owner 2026-10-20 concept C; 2026-10-30 Stop moved): one solid `surface2` pill holding "+" (tray), the field, and ONE trailing control: mic while empty, Send once there are words. **Stop is not in the pill**: it sits at the trailing end of the chip row above while a reply runs (`KitComposerStatusStrip.stop`; turns amber when stuck); the model chip also sits above with status chips (14A). Radius 26 (18 wide). `KitComposerDelivery {afterThisReply, addToThisTurn (steer), stopAndSend}` says what Send does mid-reply; offline = "Send when back online"; read-only with reason; `note`; attachments row, suggestions row, tools disabled reason; `onOpenEditor` full-screen editor; IME content insertion (`onContentInserted`, images). Voice mode (`KitComposerVoice`, `KitVoicePhase`: starting, listening, transcribing, waitingReply, speakingReply, replyReady, paused, micDenied, failed; `KitLevelMeter`; read-replies-aloud toggle). Never clears or rewrites the controller (host clears after an accepted send, DATA-1). Test keys: `chat-composer-surface`, `chat-composer-field`, `chat-send-button`, `chat-stop-button`, `composer-tools-button`, `composer-voice-button`, `prompt-editor-button`, `composer-delivery-control`.
- **Send gestures**: Send fires on touch-down; with a long-press "Send later or repeat" menu it sends on **lift** read from the pointer; mouse and screen readers keep the tap. **Fix `49d067f2d`:** a double tap on Send mid-turn stopped the whole turn (Send turned into Stop under the finger, cancelling sub-agents); Stop now ignores taps for **800 ms** after a send and after Send/its spinner turns into Stop. Also "first Send press sends" (`43107c309`).
- **Mic hold** (`KitMicHoldDetector`, `KitMicPress`, `KitMicPressOutcome {tap, hold, cancel}`): quick touch = tap, kept down = hold to talk (record; release stops and writes it), dragged far then released = cancel/throw away; followed through the pointer router because the mic widget changes under the finger.
- **Glowing border while replying** (`_ActivityGlow` soft ring, classic ring): ring sweep 2.2 dp line, 12 dp wash blur 7, peaks .75/.22, one lap in 8 s (never faster than 1/6 s); classic 3.6 s with breathing halo 15-35 percent of accent (painted by a CustomPainter on its own boundary); Calm = still glow at half strength; Off none; `_movingPart` decides the one thing that moves (MOT-15); styles override (Brutal hard ring, Paper rule). Edge layer `kit_composer_edge.dart` carries the caption/phase live region. `KitComposerFailure` (what failed in plain words, Retry, Details), `KitComposerTray` (round buttons attached above the field: Photos, Camera, File, More), `KitComposerTip` (teaching tips on Send/Stop).
- **`KitComposerChips`** (named forms `.model`, `.attachments`, `.suggestions`; `KitModelChipState`, `KitAttachment/Kind`, `KitSuggestion/Kind` for @-mentions and /commands) and `KitComposerStatusStrip` (standing facts: automatic approvals, context waiting, Background; model chip at the end; trailing Stop). A long model name ends in an ellipsis; chips fade at the line's edge; the approval chip becomes its glyph when Stop shares a phone's row at large text.
- **`KitPhraseChips`**: the person's own common short phrases above the box; tap sends, long-press offers Edit and Hide; stored `oc.phraseChips.<profileId>` (+ global `oc.phraseChips.enabled`). **`KitTaskChip`**/**`showKitTaskSheet`** (`KitTaskItem`, `KitTaskItemState`): the agent's task list as one chip with a progress ring beside the approval and model chips (owner 14 Oct: no level of its own), sheet titled "Tasks" with "5 of 6 done". **`KitProjectFileList`/`showKitProjectFileSheet`**: file mentions: search + files (folders too), returns the project-relative path.
- `KitComposer.layer` lays the composer over the transcript as the floating navigation layer and publishes its height with `KitBottomInset.add`; the keyboard lifts it; composer region transparent over a live background.

## UI-KIT-51 Transcript parts

Turn model (STATE-16, KIT-41; defined 2026-09-20; memory `chat-transcript-turn-model`): prompt, then a **turn** (everything until the agent hands back) made of **steps** whose boundaries are never drawn, and **notices** that do not end the turn. A finished turn has one footer and one More control on its last visible step; a running turn has none; a prompt has no control row and opens its menu on long-press (exposed to semantics). Work folds under one line titled by what was done.
- **`KitMessage`** (`KitMessageKind`: prompt, reply, thought (working/folded/open), notice (quiet/failed/action/open), marker): prompt = end-aligned `surface2` bubble radii 20/20/6/20 (light: `surface2` is white = page colour, so bubble fill is `text1` at 7 percent over ground, `kitPromptBubbleFill`; `kitPromptBubbleDecoration` per style); `KitBubbleWidth {auto, compact, full}`: auto hugs words up to width minus 48 (no 85 percent cap since `4a2660fa7`); reply is plain body text with no frame; sent bubble **lifts from the composer at 0.92 scale** (`kit_message_lift.dart`, owner exception MOT-2).
- **`KitTurn`** (1,234 lines; `KitTurnPhase`: starting, starting-slow, running, waitingForYou, finished, finished-latest, stopped, interrupted, failed, highlighted; `KitTurnSegment` for virtualised rows so a turn spanning several rows keeps its rhythm; `KitTurnActivity`; **`KitTurnLive`** the live line under the reply (01B): what it is doing and for how long, a soft light **sweeps over the words** (owner choice C 2026-10-06; no dot), the only motion in a chat; `KitTurnStall` watchdog line with Stop/Details and honest wording; `KitTurnFooter`). Fixes: "honest stop lines, no false connection dropped" (`3ea4a9381`: the chat ignored why OpenCode 2 interrupted a turn - `session.execution.interrupted data.reason` user/inactivity/shutdown/superseded - and said "You stopped this reply" for any aborted turn); "a Claude Code turn that ended on an API error reads as stopped"; "a command stuck past the limit says so" (`f4bdd4697`, `9c2cd5f1d`).
- **`KitToolRow`** (`KitToolKind` picks glyph only; `KitToolStatus`; states notRun, pending, running, waitingForYou, done, failed, stopped, background; no frame or fill, only printed output once opened is a block; inline cap 200, preview 6 lines; `KitStepCommand`: `$` + whole command wrapped once + first lines of output in muted mono + "Show all N lines" + copy icon). A failure the agent got past stays folded and unreddened (STATE-15); failed tool call is still and neutral with Retry (10A).
- **`KitStepTimeline`** (1,238 lines; replaces the old step list): a turn's work as one quiet line (icon, label, chevron; live mark while running) that opens in place to a timeline: thin rail, dot for a sentence, tile for a tool step, live mark for the step in progress, **preview card for a file write/edit** (`KitStepPreview`: few numbered lines, fades the last); `KitStepNodeState`; `kit_step_light.dart` (running step's light: ShaderMask sweep painted without rebuild on its own boundary, perf `4a907c935`), `kit_step_node.dart`, `kit_step_signal.dart` (Signal's signal head); opened steps fold in/out and arrive unfolding (owner exceptions MOT-5). `KitWorkLine` (`KitWorkState`, `KitWorkCounts`: "Read 3 files . edited 1"; running/waitingForYou/done/endedFailed/stopped; folded or expanded).
- `KitBackgroundWorkLine`/`KitBackgroundTask`: quiet line under a finished turn while background work still runs; fades out in `stepFade` 200 ms when empty; tap opens the list in place and goes to the step.
- **`KitQueuedMessage`** (STATE-17): everything waiting to reach the agent (offline drafts + OpenCode 2 inbox sends) merged into ONE bubble at the end of the conversation, styled as the prompt bubble; states waiting (offline), sending, not confirmed, reached server, failed, queued behind the reply, steering, mixed; one action line ("3 waiting . Send now", "Edit"); per-message actions in a row menu; carries a `KitReceipt`.
- `KitAgentStrip`/`KitAgent`/`KitAgentTint` (who works on a team task; lead, workers, reviewers; identity by name never colour), `KitFindMark`, `KitStreamCaret` (UI-KIT-45).

## UI-KIT-52 Request/answer card family

[code `kit_request_card.dart` 1,411 lines, `kit_request_answers.dart`, `kit_request_question.dart`, `kit_request_row.dart`, `kit_request_shape.dart`, `kit_request_sheet.dart` 796]
`KitRequestCard`: the one answer card for OC1 permissions/questions, OC2 forms, ` ```choices``` ` blocks, AI Team gates and external-task replies. `KitRequestKind {permission, question, form, choice, gate, reply}`; `KitRequestPhase {waiting, sending, answered, answeredElsewhere, expired}`; `KitRequestLook`. Content: who asks ("fox . on the laptop"), title, mono `KitRequestTarget` lines (command or path with change size), announcement, receipt, `since` ("waiting 4 min"), `onDetails`. **Answers are sealed types** so the kit owns labels, order, shortcuts, send haptic and double-send protection: `KitRequestDecide` (permission/gate: Allow once primary / Reject secondary; **A / D** on a PC), `KitRequestAlwaysAllowStep` (quiet third act asking one plain confirm "Always allow `ls` in this project?" before calling), `KitRequestChoose` (single choice: a tap sends; **1-9**), `KitRequestChooseMany` (opens the sheet with `KitChoiceList.multi` + pinned Send), `KitRequestReply` (multiline field with a draft + Send), `KitRequestGo` (a gate's one named act, e.g. "Merge into main", with "Review changes" above; no refusal), `KitRequestInSheet` (form/too long: one primary "Answer" opens the sheet). Visual: attention card (`attentionSurface`, `attentionLine`, radius 22, 4 dp ring at 6 percent) with answers in place; style-dependent via `KitNeedsYouLook` (tag caption or pill, tint, ring, 4 dp start edge); Signal prints a slip; **answered state** collapses to a one-line row ("Allowed once . 10:42" or "Answered on the laptop") with a receipt and Undo inside the window, no card/border/ring/attention colour (`kitAnsweredRow`); expired says so and offers nothing; the work line says "Waiting for you" while any request waits. Height cap: share .45 of the window (.6 from 250 percent text so decide buttons fit), body scrolls its buttons. `showKitRequestSheet` (header = the card's; `KitRequestSheetOutcome`; `KitRequestAlwaysAllow` risky switch with scope Until off / conversation / hour never a primary; `KitRequestMessage` reason with Reject / notes with Send back kept as a draft). Sheet closes itself via `RequestRoutes` if answered elsewhere and leaves "Answered on the laptop". In a landscape phone the connection line leaves the card its room (`61c6b39fa`).

## UI-KIT-53 Agent cards, start-screen parts, handoff

`KitAgentCard` (`KitAgentCardMode`: frame for a card an agent describes via generative UI: eyebrow "Claude Code asks", title, body parts from the kit, its ask, receipt, passed-over and unreadable looks; contract `docs/design/genui-plan-2026-10-07.md`, `genui-contract-2026-10-07.md`), `KitAgentChoice` (large card in the start grid; monochrome mark, name, calm line from its persona; chosen = mint ring + filled check; not set up = dimmed "Set up"), `KitAgentGrid` + `KitAgentGridSpan` (two equal columns, an odd last card spans sideways, one column above 1.3x text), `KitAgentLine` ("Claude Code . on this phone v" opens choices), `KitSenseAsk` (+ `KitSenseKind/Item/Action`: an agent asking for a photo, file or voice note: pick, preview, remove, send), `KitHandoffRecord` (the record a new conversation starts from, size, left-out, hidden secrets, folded Markdown preview, saved file name), `KitContinuedLine` ("Continued in Claude Code" / "Continued from <title>"; `gone`), `KitAskLine` (one-time question with two answers; inline or stacked).

# PART I. AI TEAM, DRAWINGS, WIDGETS

## UI-KIT-54 AI Team parts

`team/*` (13 files): `KitDigest`, `KitFindingsCard` (+ `KitTeamFinding`, `KitFindingSeverity` worded), `KitMergeQueue`, `KitMilestoneRow`, `KitPhaseCard`, `KitPlanCard` (+ `KitPlanTask`, `KitPlanPhase` with flagged review point), `KitProjectRow`, `KitPromoteCard`, `KitServerLane`, `KitSpecBlock`, `KitTimelineDay`, `KitTeamData`/`KitTeamState` (loading, empty, error, working, disabled, answered; caller supplies localized text and age), `team_parts.dart`. "AI Team controlled presentation surface": no engine dependencies, counted progress never guessed. Board: `KitBoardLanes` (column strip + lanes kept in step; paged on compact/medium/short, scrolled into view on expanded/large; lane surface = ground + 1 px hairline so a peeking neighbour reads as another column; arrivals via `KitAnimatedRows`), `KitBoardLane` (`.loading`), `KitBoardColumn`, `KitTaskCard` (task mark, title in the person's words, one meta line `KitTaskMeta` pieces joined " . ", one flag `KitTaskFlag`, or a receipt while a move is in flight; states default, needs you, blocked, failed, stopped, done, working), `KitPriorityGlyph` (`KitPriority` 5 levels as signal bars; urgent = filled square with a bang; someday = dashed line), `KitWorkGraph`. Team conversation reuses the chat parts (decision "team task = chat conversation, workers = sub-agents").

## UI-KIT-55 Illustration system and scenes

`KitIllustration` plays a `KitScene` (abstract; `KitPalette` from theme roles only; `KitSceneFrame`; `KitDraw` helpers for line weight and draw-in): entrance plays once (900 ms), ambient loop only when `ambient: true` and `loopsIn`, reduced = finished frame; decorative (excluded from semantics) unless it says something the text does not; each in its own `RepaintBoundary`; line art in the brand stroke (rounded caps/joins, one accent for what matters, flat fills <= 12 percent alpha, no gradient/blur/glow); 140-200 dp tall; only at moments (first run/setup, waiting, finished, empty, a few failures), never in a row, field or dialog. `KitStateView(illustration:)` takes one in place of its icon. Scenes (15 files): `KitPortalScene` (reference), `KitFoldersOpenScene`, `ServersLinkScene`/`ServersWelcomeScene`/`ServersCast` (phone, laptop, portal), `SetupPhoneScene` (`SetupPhoneMood`), `SetupReadyScene` (celebration with confetti), `SetupStepsScene` (`SetupSceneStage`, `SetupSceneHalt`), `SetupUnpluggedScene`, `StatesSheetScene`, `StatesFolderScene`, `StatesTrayScene`, `StatesSearchScene`, `StatesTerminalScene`, `StatesUnpluggedScene`, `StatesWorkingScene` (the mark beside Stop), team scenes (`TeamBoardScene`, `TeamPlanningScene`, `TeamWakingScene`, `TeamMergedScene`, `TeamNudgeScene`, `TeamRestScene`, `TeamIdleScene`, `TeamDiscoverTeaserScene`, `TeamDiscoverRelayScene`, `TeamCast`/`TeamAgentPose`). Fix `a7e0d40f4`: a picture that swaps scenes in one slot threw "Missing value" on every cold start (the painter asked the new scene whether it differed from the old one, a check each scene types to its own kind); compare kinds first. Docs: `docs/design/motion-and-illustration-2026-09-25.md`.

## UI-KIT-56 Home-screen widget UI parts

`KitSteerBox`/`KitSteerBoxPhase` (the small composer a widget opens: a title saying what sending will do ("Add to this turn", "Send after this reply"), one field, mic, Send), `KitWidgetPreview` (+ size 1x1/2x2/4x2 and background solid/tinted/see-through over a sample wallpaper), `KitWidgetStyleForm` (preview + ground + accent; saving never needs a live conversation scope). Hosted by `lib/widgets_overlay/*` (a second Flutter entry point in a transparent activity; channels `oc/widgets`, `oc/widget_action`) with `KitFloatingPanel`. Owner traps: ZTE launcher ignores `previewLayout` and mis-picks size maps (use `previewImage` + a single current-size render); the widget list is scoped to the active project. **Owner 2026-11-01: widget work comes later in the rewrite.**

---

# PART J. QUALITY GATES AND CATALOGUES

## UI-KIT-57 Kit gates, galleries, capacity (test-first catalogue)

Everything below is what "the kit is right" means today; a native kit should re-create each as a screenshot or unit test before porting the part.
- **Manifest gate G4** (`test/kit/kit_manifest_*`): every exported public part has the NAME-1 file, a doc-comment `States:` line using a fixed vocabulary {loading, empty, error, disabled, working, answered}, one gallery scene per declared state (light and dark; 412x915 and 1280x800; text 2.0 variants at both; Arabic RTL variant), a golden test, a row in the `kit.dart` doc table, a motion test, a keyboard test, an overflow scene; shrink-only allowlist `kit_manifest_allowlist.json`.
- **Galleries**: `test/goldens/kit/*_golden_test.dart`, 3,136 files; `kit_gallery.dart` sets DPR 3.0 and `debugDefaultTargetPlatformOverride = android` (G23); names per TEST-20; each golden header has the regenerate command.
- **Capacity gate** (`test/kit/kit_capacity_*`, 8 shards, about 22 min at three jobs on the build server): contract `kit_capacity.json` per exported part (role cap, per-text-slot overflow rule wrap/ellipsis:N/fade:N/scroll, per-list-slot `beyond` rule grows/wraps/scroll/more/page/fixed/not-items); stress content (`test/support/stress_content.dart`: ~1,000 chars prose, 300-char unbroken URL, CJK no spaces, emoji ZWJ/flags/keycaps, huge numbers, empty, pseudo-localisation +40 percent in brackets, Arabic RTL, lists of 50); 637 cases x 54 pumps (280 dp all fixtures LTR and RTL; 412 dp as written, pseudo-localised and prose both directions; 1,600 dp LTR) at text 1.0 and 2.0; fails on exceptions, words lost without ellipsis/fade/scroller, painted width over cap at 1,600, tap target under 48 at 412, clipped slots, lists over `shows`, missing contract. Shrink-only `kit_capacity_allowlist.json` (`KIT_CAPACITY_WRITE=1` tightens, never adds).
- **Other gates** (STANDARDS §18): G1 modal/toast entry points live in the kit; G2 raw-widget ratchet (`ListTile`, `ExpansionTile`, `TextField`, `IconButton`, `PopupMenuButton`, `Clipboard.setData`, `HapticFeedback.`, `launchUrl`, `Duration(milliseconds:`, `AnimatedSize`); G5 `androidTapTargetGuideline` + `labeledTapTargetGuideline` + `textContrastGuideline` + semantics traversal order on every gallery; G6 overflow; G7 directional layout; G8x reduced motion; G9x behaviour contracts per part; G10 drafts; G11 copy rules (confirm titles end "?" or Arabic "؟", labels not OK/Yes/Continue/Confirm, cancel words match kind, contradiction pairs never co-occur: Working+stopped, Paused+idle, Connected+reconnecting); G12 redaction through every technical part; G14x keyboard and pointer; G15/G15x width literals; G16 kit-only (absolute, empty baseline); G17 colour literals; G18 contrast of every pack; G19 type roles exact; G20 token pins; G21 raw type/radius literals; G22 `KitGlass` solid; G25 no `flutter_animate`; G27 en/ar key sets, placeholders, Arabic plurals zero/one/two/few/many/other; G33 commit-msg hook (`[skip ci]`, formatting); G36 every `Key('...')` literal appears in a test; G37 debug asserts (icon button label, disabled needs reason, one primary per screen); G42 frame-time protocol (janky-frame share no more than 1.0 point above baseline, p90 no more than 10 percent above, scripted scene `tool/qa/issue87`, three 30 s runs, median); G49 raw decorations in the kit.
- **Perf baseline** (PERF-1): cold start to usable Work tab <= 3.4 s on the emulator baseline; open project <= 1.6 s; Settings <= 0.2 s; p90 frame 40/36 ms on emulator, <= 17 ms on a real arm64 phone.
- **Test traps** (AGENTS.md): any test touching `ProfileStore` must mock `plugins.it_nomads.com/flutter_secure_storage`; `test/flutter_test_config.dart` sets `KitMotion.loops=false`; stale `build/unit_test_assets` ink_sparkle errors need `rm -rf`.

## UI-KIT-58 Widgetbook screen catalogue

`widgetbook/` (own `pubspec.yaml`, depends on the app with `path: ..`): a live web catalogue of the app's **real screens** with fakes, served on `:8766` (Tailscale) with `/map/` (navigation graph from the code), `/shots/` (one screenshot per use-case), `/status.json`. Addons: the app's real light and dark themes, English and Arabic, text scale 1.0/1.3/2.0, viewport. `FakeApp.create(profiles, connected, capabilities, prefs)` = a real `ConnectionController` over in-memory preferences and Keystore; `FakePlatform {android, web, none}`; `FakeAppScene`. 33 components registered in `all_components.dart` (scheduled prompts, usage plans/subscription/alerts/add flow/widget settings, agent phone check, `kitWindowRow`, server capabilities, dev preview, server trust editor/warning, guide, chats home, all conversations, conversation, `kitTaskChip`, `kitFileMentions`, `kitDiffView`, `kitWorkSteps`, this phone, notification settings, tips, team intro, setup customize sheet, team phone setup, files page, phone setup progress/details, terminal). The **screen standard check** runs on every rebuild (screen-standard.md items marked [auto]: one primary, <= 7 items first view, <= 2 text lines per row, <= 3 text sizes, nothing cut off, no overflow stripes). Coverage ratchet `coverage_baseline.json` and `standard_baseline.json`. There is no Widgetbook for individual kit parts; kit parts are covered by the galleries (UI-KIT-57). Owner: "replaces Figma mocks".
Native equivalent: Compose `@Preview` galleries + screenshot tests; JetBrains unified `@Preview` in `commonMain` arrives in CMP 1.10 [web].

---

# PART K. CROSS-CUTTING SECTIONS

## K1. Stored data (all kit-related)

See the table in UI-KIT-14 for appearance keys. Additional kit-owned or kit-read storage:

| Key / path | Format | Owner | Notes |
|---|---|---|---|
| `oc.tips` | JSON `{ "<tipId>": {"count": n, "last": "<UTC ISO>", "done": true?} }` in SharedPreferences | `TipController` | app-wide; reset by "Show tips again" |
| `oc.appearanceGuide` | JSON `{done?, seen[], lastIn{}, last?}` | `AppearanceGuideStore` | device-wide |
| `oc.draft.<target>.<profileId>` | plain string | `KitDraft` | target examples `note.<sessionId>`; app-wide `...app` listed in `kit_draft_manifest_test.dart`; swept by `ProfileStore.profileScopedPreferenceKeys` |
| `oc.glass` | string off/weak/balanced/strong | `GlassChoiceStore` | no profile id; not swept |
| `oc.phraseChips.<profileId>`, `oc.phraseChips.enabled` | string list / bool | phrase chips row | profile scope (swept) / global |
| `oc.nudges.*`, `oc.chatNoticesSeen`, `oc.defaultNotices` | misc | first-run notices | not kit but kit-shown |
| Fonts/assets in the APK | `assets/fonts/geist/*`, `assets/fonts/phosphor/*`, `assets/marks/*.svg` (79), `assets/branding/open-portal/mark.svg`, `assets/branding/app-icon-256.png` | pubspec | no shaders remain (`grep FragmentProgram` finds only comments); no bitmap illustrations |

Per-profile key naming rule (security invariant): `oc.<what>.<profileId>` so the deletion sweep finds it; extend `ConnectionController.deleteProfileAndLocalData` for shared blobs.

## K2. Native bridges touched by the kit

None owned. Indirect: Android haptics (`HapticFeedback`/`Feedback.forLongPress`); clipboard; `SystemChrome` bar colours; `mobile_scanner` (CameraX/ML Kit) for `KitScanner`; `webview_flutter_android` for `KitWebPreview`; `dynamic_color` (core palette) for Material You; `oc/local_pdf` renderer feeding `KitViewer`; `oc/camera`, `oc/voice`, `oc/read-aloud` feeding composer voice mode (hosts, not the kit); `oc/widgets` / `oc/widget_action` for the widget overlay. In Kotlin every one of these becomes a direct API call; there is nothing to bridge.

## K3. Packages used and their native counterparts

| Flutter package | Used by | Native / KMP counterpart (versions checked on the web 2026-11-01) |
|---|---|---|
| `flutter` + Material | everything | Compose Multiplatform **1.12.2** stable (maps to Jetpack Compose 1.12.1; needs Kotlin >= 2.1.0, 2.2.20 for iOS/web) [web kotlinlang.org compatibility page]. Material 3 Expressive is in the JetBrains M3 artifact 1.10.x-alpha line (conflicting numbers across sources; verify). Demo lesson: CMP M3 needed compileSdk 37. |
| `xterm` | terminal | Termux `terminal-emulator`/`terminal-view` (Apache 2.0 libs, GPL app) or ConnectBot `termlib` (Compose, libvterm). No KMP terminal view exists at production quality [web] |
| `highlight` | code blocks | `dev.snipme:highlights` 1.1.0 (KMP; used by mikepenz renderer's code module) [web] |
| hand-written markdown | `KitMarkdown` | `mikepenz/multiplatform-markdown-renderer` 0.45.0 (Kotlin markdown 0.7.9, Coil3 module, code module) [web] is a base but **does not document streaming**; the kit's tail-block fade, caret, file-link chips, `choices` fences and per-block cache need a custom layer. Evaluate `huarangmeng/Markdown` / `adamglin markdown-compose` (streaming-oriented) |
| `flutter_svg` | marks, viewer | Coil 3 SVG or `compose-multiplatform` vector resources |
| `qr` | `KitQr` | `alexzhirkevich/qrose` 1.0.0 (CMP QR painter, active Mar 2026) [web] |
| `mobile_scanner` | `KitScanner` | CameraX + ML Kit barcode (Android actual), iOS AVFoundation later; `QRKit` 1.0.2 exists but unverified [web] |
| `webview_flutter_android` | `KitWebPreview` | `AndroidView(WebView)` with the same restrictions, or `compose-webview-multiplatform` |
| `intl`, `clock` | KitTime, tests | `kotlinx-datetime` + platform `DateFormat.is24HourFormat`; injectable clock |
| `shared_preferences` | draft, tips, effects | read old file once (migration) then DataStore/`multiplatform-settings` |
| `dynamic_color` | Material You | `dynamicDarkColorScheme` / `dynamicLightColorScheme` (API 31+) |
| backdrop blur (`ImageFilter`) | `KitFrost` | Haze (`chrisbanes/haze`) 2.0.0-alpha03 (Jun 2026 per a mirror), or `RenderEffect` |
| screenshot tests (Flutter goldens) | gates | Roborazzi (supports Compose incl. Previews; multiplatform claim unverified), Paparazzi, Google Compose Preview Screenshot Testing [web] |
| `flutter_animate` | **banned** | n/a (springs via `animate*AsState`/`Animatable`) |

## K4. Tricky behaviours and hard-won fixes a rewrite must not lose

Format: symptom or rule, then the fix. Commits are in `lib/ui/kit` history unless noted.

**Press, input and gestures**
1. Quick taps showed no pressed fill: use raw pointer events + `pressHold` 100 ms (UI-KIT-10).
2. Send lost its first press / send path through the tap recogniser: send from the finger's lift when a long-press menu exists (`ee0815e9f`).
3. Double tap on Send mid-turn interrupted the entire turn incl. sub-agents (Send turns into Stop under the finger): Stop ignores taps for 800 ms (`49d067f2d`).
4. Mic hold must be followed through the pointer router because the mic's widget swaps under the finger (UI-KIT-50).
5. Bare-key shortcuts must stand down while typing in a field (`kitTypingInField`).
6. A swipe may only be an accelerator for an Undo-able act and must have a menu twin (A11Y-5).
7. Android passes font scale 1.3 as 1.2999999523: compare `>= 1.29` (`kit_top_bar_pill.dart`).

**Layout and text**
8. Never cut mid-word or mid-identifier: `KitWordText`, `kitBreakableFileName`, `kitCodeBreakable`, zero-width breaks after punctuation; no full stop before an ellipsis (`a319bd04e`).
9. One text rail per surface (`leadColumn` 40 + gutter 16 = 56; conversation compact 48) and trailing glyph ink ends on the rail (`97c8fb98f`, `b950b8272`, `8f0fc7bfc`).
10. Spacing tokens on the 4 dp scale (`7d58a29bc`); lists in a sheet start on the title's line via `KitReach` (`fe5824478`).
11. Hairlines are 1 physical pixel and snapped; focus ring 2 physical px; never `withValues(alpha:)` on the hairline role.
12. Request card at 250 percent text: cap 60 percent and scroll its buttons (`fdf91e80d`, `d42566aa6`).
13. A capped part follows its column: page-level parts centre, bars fill, a chip ends on the trailing edge (`d99028f52`); keep caps off containers and grounds (`1d603c78d`).
14. Parts must carry 1,000 characters and pseudo-localised text without cutting (`1f0dfd324`).

**Motion and rendering**
15. A streaming reply must not re-parse per token; the caret rides on a copy of the tail block (`edc3a8b1a`); the grow clip needs a 4 dp descender allowance (`2d9a65b45`).
16. `AnimatedSize` and layout animation are banned in scrolling lists; only `KitReveal` and the owner-approved chat exceptions may animate layout.
17. A state view/scene swapping kinds in one slot must compare kinds before diffing (`a7e0d40f4`).
18. `KitSwap` must keep the leaving child's state (`8bff010c9`); `KitAnimatedBox` outline is paint only.
19. The page and its top bar are decorations of one tree; a tried page-level RepaintBoundary was reverted, the glass content boundary replaced it (`e7a017611`, `d412c7ec7`: 47 to 17 ms/frame).
20. Ticking parts must repaint without rebuilding: in this Flutter a dirty element under a `LayoutBuilder` relays out that builder and repaints ancestors; the Appearance page scrolled at 28-35 ms/frame until the breathing halo, the step sweep and the streaming reply moved to `CustomPainter`/`ShaderMask` render objects on their own boundaries (`4a907c935`). Compose: use `drawBehind`/`graphicsLayer` reads in draw phase, not state reads in composition.
21. Live background 12 Hz shared clock with 30 s idle rest (UI-KIT-17).
22. InkSparkle noise is seeded once so goldens are deterministic; tests must wait out the Android transition.
23. Predictive back vs row-to-page transform: yield to the system builder during the gesture (UI-KIT-08).
24. Launch jitter: render the home itself at launch instead of a placeholder shell that swaps (`c45c02031`).

**State and honesty**
25. 8 s is the universal escalation (`KitMotion.escalateAfter`) with a way out.
26. An existing install shows "Already installed", never inferred; confirmed needs the server's echo (`KitReceipt`).
27. Stop wording honesty: interrupt reasons from OpenCode 2 `session.execution.interrupted.data.reason` map to distinct words; never say "connection dropped" without asking the server (`3ea4a9381`).
28. A failure the agent got past stays folded and unreddened; a work line opens only when the turn ended on a failure.
29. OpenCode 2 notices ("Context added", "Moved") arrive with role `user`; use turn helpers, never `role != 'assistant'`.
30. Disabled and locked rows say their reason once to screen readers (`387bcc08e`).

**Security and privacy**
31. Every URL the app did not author goes through `openExternalLink`; never `launchUrl` a server value.
32. Secrets: `KitField.secret` only; never prefilled; `KitRedact` before any display, copy or log; register known secrets at ingress.
33. `KitWebPreview`: single origin, no JS bridge, storage cleared on open and close.
34. `KitScanner` never stores or renders the decoded value; latches once accepted.
35. Per-profile keys `oc.<what>.<profileId>`.

**Appearance**
36. Choosing Paper/Signal turns the live background off but remembers it was the style, not the person (`liveHeldByStyle`); choosing another style turns it back on.
37. A style change resets Fine-tune; unknown stored enum names read as defaults (retired Glass/Crystal read as Solid).
38. Soft with Depth Flat must keep edges ("no invisible controls").
39. Accent swatch must keep 30 degrees and DeltaE 20 from amber; neutral pair (mono, silver) replaced a cream that failed.
40. High contrast forces Solid except Brutal/Paper/Signal; Saver forces motion off, live off, solid, true black.
41. Aurora must not use word plates or full-strength paint (owner 2026-10-26).
42. Do not bring back glass/painted backgrounds/boxed cards unless the owner asks; frost is the one exception, in two places only.

## K5. Performance notes

- Targets: PERF-1 above; frame budget 16.7 ms; `gfxinfo` comparisons gate any new animation (G42).
- Measured: Appearance 28-35 ms to 60 fps after `4a907c935`; live background 60 fps/54.7 percent CPU to 6 fps/4 percent (`37ed817b2`); glass content layer 47 to 17 ms (`d412c7ec7`); `KitMarkdown` patterns compiled once; code highlighting capped at 20,000 chars and cached (48); logs capped at 2,000 lines; viewer 2,000 lines; tool output inline cap 200 and preview 6 lines; diff gaps fold unchanged lines; long transcripts are virtualised (a turn can span several rows, `KitTurnSegment`); `KitAnimatedRows` only for short lists; no per-frame blur stacks (frost is one pane, two places).
- Battery/heat: motion drops to Calm under battery saver or SEVERE heat (MOT-13, not yet fully wired: G39 "missing"); loops stop off-screen via `TickerMode`; nothing polls off screen (PERF-4).
- Size: Flutter APK 107 MB and cold start about 0.4 s vs the native demo 2.5 MB and about 0.25 s (task context); Geist variable fonts and 79 SVG marks are the kit's own assets.

## K6. Risks for the rewrite

1. **Volume.** About 330 parts and 3,136 golden images; porting without the gates loses invariants. Mitigation: port tokens/colour maths and the gates first; run each part through the capacity stress list.
2. **Compose has no direct equivalents** for: a resolver that swaps whole surface decorations per style (needs a `KitSurface` composable that every part uses), `KitPressTracker` timing, `KitPageOrigin` container transform with predictive back (Compose SharedTransitionLayout + `PredictiveBackHandler`/Navigation Event API; JetBrains 1.10 notes mention a Navigation Event API replacing `PredictiveBackHandler`, from a secondary blog [web, unverified]), nonlinear font-scale capping, backdrop blur, a streaming markdown renderer with tail-block fade, an xterm view.
3. **Terminal view** is the largest missing library; Termux modules are Apache 2.0 but need a Compose wrapper; scrollback, selection, OSC 52, links, sticky Ctrl/Alt must be recreated.
4. **Markdown streaming** quality is the most visible feature; budget a custom layer.
5. **Preference migration**: Flutter's `FlutterSharedPreferences` file with `flutter.` prefixes and encoded list values; `flutter_secure_storage` v11 uses its own cipher/key path (unverified here), so read it with the same plugin logic on first launch before deleting anything (covered by another area map).
6. **Text-scale behaviour** (frame cap 1.3, reading full, up to 2.5) must be re-derived with Android 14's nonlinear scaling.
7. **RTL**: all offsets are directional; Compose `LayoutDirection` handles most, but hard shadows, tracks (Signal) and chevrons need explicit mirroring tests.
8. **Locale list**: 7 languages, 9,468 keys in ARB with plurals/placeholders; Compose resources or a converter must keep placeholder equality tests (G27).
9. **Owner taste is judged visually**: any pixel drift on Solid (the pixel-identical default) is a regression for a user who only has this app; keep Geist, the same radii and the same row heights, and compare side by side (memory `judge-visuals-hard`).
10. **Docs lag code**: several docs (visual-language §2, STANDARDS LOOK-12, kit-v2 preamble) describe superseded values; use this file's numbers.
11. **Pending/unmerged**: R7 text-scale cap is on `fix/large-text-2026-10-31`, not in batch1; check other integration branches before freezing the contract.
12. **Reduced-motion semantics** differ (system flag OR in-app Off); Compose has `ANIMATOR_DURATION_SCALE` via `Settings.Global`, not `disableAnimations` in `LocalAccessibilityManager`; read it explicitly.

## K7. Suggested port order and the test-first plan for the kit

1. Colour maths + `ThemeRoles` + 31 packs + contrast guard + role tests (pure Kotlin, JVM tests; port `theme_roles_test`, pack floors, `accentKeepsMeaning`).
2. `KitTokens`, type roles with Geist variable, `KitLayout`/`KitMaxWidth`, `KitMotion` constants, `KitEffects` + prefs reader (migration).
3. `KitSurfaceLook` + Solid only (pixel parity), then Soft/Brutal/Paper/Signal behind the same resolver.
4. Core controls: tappable/ripple/haptics, row family, buttons/action block, icon button, chip, field, segmented/choice list, switch/expand.
5. Frames: screen, top bar, nav, sheet, confirm, dialog, menu, undo, status line, state view, notice, progress.
6. Content: code block + highlight, markdown (static) then streaming layer, diff, log, viewer.
7. Chat: message, turn, tool row, step timeline, work line, queued message, composer + glow, request card + sheet.
8. Rest: terminal, scanner/QR, web preview, gallery/markup, charts/tables, team parts, scenes (draw the portal and 4-5 state scenes first), live looks (aurora, mesh, dotRipple first), frost, tips/tour, Appearance guide.
Test-first catalogue: re-create (a) the gates G5/G6/G7/G8/G11/G12/G18/G19/G20/G37 as Kotlin tests; (b) the 637-case capacity stress as a Compose test list; (c) golden screenshots for each part x light/dark x 412x915 and 1280x800 x text 2.0 x Arabic; (d) the `catalog` of logic tests in `/home/eslam/Storage/Code/wt/native-plan/catalog` for the pure-logic parts (colour maths, tip controller, redaction, markdown parser, menu ordering, diff model, log buffer, key-bar latching, mic press outcomes, tokens/knob maths, capability registry equality with `capabilities.json`).

---

# APPENDIX A. Every file in `lib/ui/kit/` and the feature that covers it

Also covered (outside the kit folder): `lib/ui/app_theme.dart`, `theme_roles.dart`, `theme_packs*.dart` (01, 02); `lib/ui/app_iconography.dart` (11); `lib/ui/teaching/teaching.dart` (20); `lib/state/effects.dart`, `app_preferences.dart`, `glass_choice.dart`, `appearance_guide.dart`, `tips.dart` (14, 18, 19, 20); `lib/ui/screens/settings/appearance_screen.dart` (19); `lib/ui/widgets/external_link.dart` (12).

| File (lib/ui/kit/) | Lines | Feature |
|---|---|---|
| chat/kit_agent_strip.dart | 294 | UI-KIT-51/54 |
| chat/kit_background_work_line.dart | 249 | UI-KIT-51 |
| chat/kit_composer.dart | 929 | UI-KIT-50 |
| chat/kit_composer_caption.dart | 377 | UI-KIT-50 |
| chat/kit_composer_chips.dart | 1500 | UI-KIT-50 |
| chat/kit_composer_classic.dart | 233 | UI-KIT-50 |
| chat/kit_composer_edge.dart | 257 | UI-KIT-50 |
| chat/kit_composer_glow.dart | 249 | UI-KIT-50 |
| chat/kit_composer_layer.dart | 715 | UI-KIT-50 |
| chat/kit_composer_status_strip.dart | 309 | UI-KIT-50 |
| chat/kit_composer_tray.dart | 153 | UI-KIT-50 |
| chat/kit_composer_types.dart | 230 | UI-KIT-50 |
| chat/kit_find_mark.dart | 35 | UI-KIT-45 |
| chat/kit_markdown.dart | 1363 | UI-KIT-45 |
| chat/kit_markdown_fresh.dart | 501 | UI-KIT-45 |
| chat/kit_markdown_image.dart | 935 | UI-KIT-45 |
| chat/kit_markdown_table.dart | 358 | UI-KIT-45 |
| chat/kit_message.dart | 1298 | UI-KIT-51 |
| chat/kit_message_lift.dart | 117 | UI-KIT-51 |
| chat/kit_mic_hold.dart | 252 | UI-KIT-50 |
| chat/kit_phrase_chips.dart | 111 | UI-KIT-50 |
| chat/kit_project_file_list.dart | 193 | UI-KIT-50 |
| chat/kit_project_file_sheet.dart | 48 | UI-KIT-50 |
| chat/kit_queued_message.dart | 472 | UI-KIT-51 |
| chat/kit_step_light.dart | 729 | UI-KIT-51 |
| chat/kit_step_node.dart | 93 | UI-KIT-51 |
| chat/kit_step_signal.dart | 160 | UI-KIT-51 |
| chat/kit_step_timeline.dart | 1237 | UI-KIT-51 |
| chat/kit_stream_caret.dart | 97 | UI-KIT-45/51 |
| chat/kit_tool_row.dart | 1484 | UI-KIT-51 |
| chat/kit_transcript_excerpt.dart | 145 | UI-KIT-43 |
| chat/kit_turn.dart | 1233 | UI-KIT-51 |
| chat/kit_work_line.dart | 404 | UI-KIT-51 |
| diff/kit_diff_chrome.dart | 1046 | UI-KIT-44 |
| diff/kit_diff_markdown.dart | 351 | UI-KIT-44 |
| diff/kit_diff_model.dart | 653 | UI-KIT-44 |
| diff/kit_diff_rows.dart | 677 | UI-KIT-44 |
| glass/kit_glass.dart | 262 | UI-KIT-05 |
| kit.dart | 376 | UI-KIT-00 (barrel + doc table) |
| kit_action_stack.dart | 94 | UI-KIT-34 |
| kit_agent_card.dart | 287 | UI-KIT-53 |
| kit_agent_choice.dart | 277 | UI-KIT-53 |
| kit_agent_grid.dart | 66 | UI-KIT-53 |
| kit_agent_grid_span.dart | 15 | UI-KIT-53 |
| kit_agent_line.dart | 86 | UI-KIT-53 |
| kit_arrival.dart | 156 | UI-KIT-25 |
| kit_ask_field.dart | 480 | UI-KIT-37 |
| kit_ask_line.dart | 169 | UI-KIT-34/42 |
| kit_bidi.dart | 53 | UI-KIT-12 |
| kit_board_lane.dart | 706 | UI-KIT-54 |
| kit_bottom_inset.dart | 152 | UI-KIT-06 |
| kit_breadcrumb.dart | 466 | UI-KIT-23 |
| kit_buttons.dart | 1290 | UI-KIT-34 |
| kit_capability_explainer.dart | 1048 | UI-KIT-42 |
| kit_carousel.dart | 241 | UI-KIT-19 |
| kit_chart.dart | 557 | UI-KIT-49 |
| kit_checklist.dart | 737 | UI-KIT-42 |
| kit_chip.dart | 902 | UI-KIT-36 |
| kit_choice_cards.dart | 319 | UI-KIT-38 |
| kit_choice_list.dart | 946 | UI-KIT-38 |
| kit_choice_tiles.dart | 433 | UI-KIT-38 |
| kit_code_block.dart | 1386 | UI-KIT-44 |
| kit_code_block_parts.dart | 351 | UI-KIT-44 |
| kit_code_surface.dart | 31 | UI-KIT-05 |
| kit_confirm_sheet.dart | 623 | UI-KIT-27 |
| kit_consequences.dart | 139 | UI-KIT-27 |
| kit_context_chart.dart | 215 | UI-KIT-41 |
| kit_context_region.dart | 111 | UI-KIT-13/29 |
| kit_context_ring.dart | 530 | UI-KIT-41 |
| kit_context_sheet.dart | 115 | UI-KIT-41 |
| kit_continued_line.dart | 83 | UI-KIT-53 |
| kit_copy.dart | 39 | UI-KIT-12 |
| kit_date_time_picker.dart | 1431 | UI-KIT-39 |
| kit_details_fold.dart | 552 | UI-KIT-31 |
| kit_dialog.dart | 841 | UI-KIT-28 |
| kit_diff_view.dart | 813 | UI-KIT-44 |
| kit_divider.dart | 271 | UI-KIT-05 |
| kit_effects.dart | 5 | UI-KIT-14 |
| kit_error_fallback.dart | 136 | UI-KIT-40 |
| kit_fact_row.dart | 349 | UI-KIT-49 |
| kit_feed_item.dart | 591 | UI-KIT-33 |
| kit_field.dart | 1261 | UI-KIT-37 |
| kit_filter_chip.dart | 49 | UI-KIT-36 |
| kit_filter_chips.dart | 125 | UI-KIT-36 |
| kit_fine_tune_panel.dart | 198 | UI-KIT-19 |
| kit_floating_action.dart | 207 | UI-KIT-32 |
| kit_floating_panel.dart | 113 | UI-KIT-32 |
| kit_frost.dart | 514 | UI-KIT-18 |
| kit_frost_header.dart | 280 | UI-KIT-18 |
| kit_group_note.dart | 62 | UI-KIT-33 |
| kit_guide.dart | 896 | UI-KIT-19 |
| kit_handoff_record.dart | 163 | UI-KIT-53 |
| kit_hero_question.dart | 67 | UI-KIT-38 |
| kit_icon.dart | 477 | UI-KIT-11 |
| kit_icon_button.dart | 549 | UI-KIT-35 |
| kit_illustration.dart | 425 | UI-KIT-55 |
| kit_image.dart | 1433 | UI-KIT-46 |
| kit_image_gallery.dart | 391 | UI-KIT-46 |
| kit_image_markup.dart | 733 | UI-KIT-46 |
| kit_ink_layer.dart | 193 | UI-KIT-10 |
| kit_jump_pill.dart | 432 | UI-KIT-25 |
| kit_key_value.dart | 110 | UI-KIT-49 |
| kit_last_known.dart | 89 | UI-KIT-43 |
| kit_layout.dart | 198 | UI-KIT-06 |
| kit_lead_column.dart | 30 | UI-KIT-06 |
| kit_level_meter.dart | 180 | UI-KIT-41 |
| kit_link_scope.dart | 33 | UI-KIT-12/48 |
| kit_live_background.dart | 354 | UI-KIT-17 |
| kit_live_look_preview.dart | 225 | UI-KIT-17/19 |
| kit_log_panel.dart | 1387 | UI-KIT-44 |
| kit_log_panel_tools.dart | 69 | UI-KIT-44 |
| kit_max_width.dart | 246 | UI-KIT-06 |
| kit_menu.dart | 992 | UI-KIT-29 |
| kit_mini_table.dart | 245 | UI-KIT-49 |
| kit_motion.dart | 221 | UI-KIT-07 |
| kit_nav.dart | 1138 | UI-KIT-23 |
| kit_needs_you.dart | 376 | UI-KIT-42 |
| kit_notice.dart | 882 | UI-KIT-40 |
| kit_one_time_code.dart | 113 | UI-KIT-31 |
| kit_optical.dart | 54 | UI-KIT-06 |
| kit_option_card.dart | 1308 | UI-KIT-19 |
| kit_page_heading.dart | 142 | UI-KIT-33 |
| kit_page_route.dart | 174 | UI-KIT-08 |
| kit_panel.dart | 176 | UI-KIT-05 |
| kit_plan_block.dart | 103 | UI-KIT-41 |
| kit_presence.dart | 61 | UI-KIT-07 |
| kit_progress.dart | 437 | UI-KIT-41 |
| kit_progress_ring.dart | 505 | UI-KIT-41 |
| kit_progress_row.dart | 830 | UI-KIT-41 |
| kit_qr.dart | 200 | UI-KIT-46 |
| kit_reach.dart | 129 | UI-KIT-06 |
| kit_receipt.dart | 580 | UI-KIT-42 |
| kit_redact.dart | 411 | UI-KIT-12 |
| kit_request_answers.dart | 287 | UI-KIT-52 |
| kit_request_card.dart | 1411 | UI-KIT-52 |
| kit_request_question.dart | 438 | UI-KIT-52 |
| kit_request_row.dart | 345 | UI-KIT-52 |
| kit_request_shape.dart | 572 | UI-KIT-52 |
| kit_request_sheet.dart | 796 | UI-KIT-52 |
| kit_row.dart | 1424 | UI-KIT-33 |
| kit_row_group_seam.dart | 122 | UI-KIT-33 |
| kit_row_parts.dart | 999 | UI-KIT-33 |
| kit_row_value.dart | 168 | UI-KIT-33 |
| kit_scanner.dart | 558 | UI-KIT-46 |
| kit_screen.dart | 768 | UI-KIT-21 |
| kit_scroll_fade.dart | 96 | UI-KIT-25 |
| kit_scrollbar.dart | 131 | UI-KIT-13 |
| kit_search_field.dart | 641 | UI-KIT-37 |
| kit_section_label.dart | 362 | UI-KIT-33 |
| kit_segmented.dart | 1037 | UI-KIT-38 |
| kit_sense_ask.dart | 254 | UI-KIT-53 |
| kit_server_trust.dart | 134 | UI-KIT-40 |
| kit_session_tabs.dart | 233 | UI-KIT-23 |
| kit_shape.dart | 41 | UI-KIT-04 |
| kit_sheet.dart | 846 | UI-KIT-26 |
| kit_sheet_frame.dart | 327 | UI-KIT-26 |
| kit_sheet_modal.dart | 517 | UI-KIT-26 |
| kit_sheet_parts.dart | 203 | UI-KIT-26 |
| kit_since.dart | 284 | UI-KIT-24 |
| kit_skeleton_transcript.dart | 104 | UI-KIT-43 |
| kit_sliver_row_group.dart | 226 | UI-KIT-33 |
| kit_start_box.dart | 201 | UI-KIT-37 |
| kit_state_view.dart | 862 | UI-KIT-40 |
| kit_status_answer.dart | 472 | UI-KIT-24 |
| kit_status_line.dart | 995 | UI-KIT-24 |
| kit_status_mark.dart | 207 | UI-KIT-24 |
| kit_status_slot.dart | 266 | UI-KIT-24 |
| kit_status_tag.dart | 103 | UI-KIT-24 |
| kit_steer_box.dart | 214 | UI-KIT-56 |
| kit_step_slider.dart | 340 | UI-KIT-19/38 |
| kit_surface.dart | 372 | UI-KIT-05 |
| kit_surface_look.dart | 996 | UI-KIT-05/15 |
| kit_surface_paint.dart | 130 | UI-KIT-05 |
| kit_swatch.dart | 1056 | UI-KIT-19 |
| kit_swipe_action.dart | 128 | UI-KIT-30 |
| kit_system_bars.dart | 28 | UI-KIT-13 |
| kit_tappable.dart | 750 | UI-KIT-10 |
| kit_task_card.dart | 570 | UI-KIT-54 |
| kit_task_chip.dart | 233 | UI-KIT-50 |
| kit_task_mark.dart | 150 | UI-KIT-24 |
| kit_task_sheet.dart | 110 | UI-KIT-50 |
| kit_technical_value.dart | 45 | UI-KIT-31 |
| kit_term.dart | 891 | UI-KIT-31 |
| kit_terminal_links.dart | 164 | UI-KIT-47 |
| kit_terminal_view.dart | 904 | UI-KIT-47 |
| kit_text.dart | 1098 | UI-KIT-03 |
| kit_text_halo.dart | 101 | UI-KIT-03 |
| kit_time.dart | 60 | UI-KIT-12 |
| kit_tip.dart | 740 | UI-KIT-20 |
| kit_tokens.dart | 1007 | UI-KIT-04 |
| kit_top_bar.dart | 1293 | UI-KIT-22 |
| kit_top_bar_centred.dart | 62 | UI-KIT-22 |
| kit_top_bar_pill.dart | 280 | UI-KIT-22 |
| kit_top_bar_scope.dart | 527 | UI-KIT-22 |
| kit_top_bar_tail.dart | 55 | UI-KIT-22 |
| kit_typing_guard.dart | 15 | UI-KIT-12 |
| kit_undo.dart | 781 | UI-KIT-30 |
| kit_vector_logo.dart | 83 | UI-KIT-11 |
| kit_viewer.dart | 745 | UI-KIT-44 |
| kit_viewer_bodies.dart | 463 | UI-KIT-44 |
| kit_viewer_build.dart | 545 | UI-KIT-44 |
| kit_viewer_content.dart | 257 | UI-KIT-44 |
| kit_web_preview.dart | 269 | UI-KIT-48 |
| kit_wheel.dart | 345 | UI-KIT-39 |
| kit_widget_preview.dart | 345 | UI-KIT-56 |
| kit_widget_style_form.dart | 133 | UI-KIT-56 |
| kit_window_row.dart | 206 | UI-KIT-41 |
| kit_word_text.dart | 95 | UI-KIT-03 |
| kit_work_graph.dart | 1388 | UI-KIT-49 |
| live_background/kit_live_clock.dart | 146 | UI-KIT-17 |
| live_background/kit_live_style.dart | 129 | UI-KIT-17 |
| live_background/kit_live_styles.dart | 58 | UI-KIT-17 |
| live_background/live_mock_chat.dart | 89 | UI-KIT-17 |
| live_background/styles/kit_live_aurora.dart | 464 | UI-KIT-17 |
| live_background/styles/kit_live_blueprint.dart | 103 | UI-KIT-17 |
| live_background/styles/kit_live_bokeh.dart | 71 | UI-KIT-17 |
| live_background/styles/kit_live_brushed_metal.dart | 69 | UI-KIT-17 |
| live_background/styles/kit_live_caustics.dart | 95 | UI-KIT-17 |
| live_background/styles/kit_live_circuit.dart | 130 | UI-KIT-17 |
| live_background/styles/kit_live_constellation.dart | 87 | UI-KIT-17 |
| live_background/styles/kit_live_dot_ripple.dart | 43 | UI-KIT-17 |
| live_background/styles/kit_live_flow_threads.dart | 86 | UI-KIT-17 |
| live_background/styles/kit_live_glow_spill.dart | 68 | UI-KIT-17 |
| live_background/styles/kit_live_halftone.dart | 58 | UI-KIT-17 |
| live_background/styles/kit_live_honeycomb.dart | 82 | UI-KIT-17 |
| live_background/styles/kit_live_light_rays.dart | 48 | UI-KIT-17 |
| live_background/styles/kit_live_liquid_blobs.dart | 50 | UI-KIT-17 |
| live_background/styles/kit_live_matrix_rain.dart | 117 | UI-KIT-17 |
| live_background/styles/kit_live_mesh.dart | 50 | UI-KIT-17 |
| live_background/styles/kit_live_oscilloscope.dart | 82 | UI-KIT-17 |
| live_background/styles/kit_live_radar.dart | 92 | UI-KIT-17 |
| live_background/styles/kit_live_silk.dart | 69 | UI-KIT-17 |
| live_background/styles/kit_live_support.dart | 92 | UI-KIT-17 |
| live_background/styles/kit_live_toolkit.dart | 198 | UI-KIT-17 |
| live_background/styles/kit_live_topo.dart | 37 | UI-KIT-17 |
| motion/kit_animated_rows.dart | 247 | UI-KIT-07 |
| motion/kit_haptics.dart | 74 | UI-KIT-09 |
| motion/kit_motion_parts.dart | 535 | UI-KIT-07 |
| motion/kit_page_transitions.dart | 764 | UI-KIT-08 |
| motion/kit_refresh.dart | 299 | UI-KIT-25 |
| motion/kit_reveal.dart | 230 | UI-KIT-07 |
| motion/kit_tab_switcher.dart | 851 | UI-KIT-23 |
| scenes/folders_open_scene.dart | 142 | UI-KIT-55 |
| scenes/portal_scene.dart | 77 | UI-KIT-55 |
| scenes/servers_cast.dart | 174 | UI-KIT-55 |
| scenes/servers_link_scene.dart | 224 | UI-KIT-55 |
| scenes/servers_welcome_scene.dart | 69 | UI-KIT-55 |
| scenes/setup_cast.dart | 123 | UI-KIT-55 |
| scenes/setup_phone_scene.dart | 274 | UI-KIT-55 |
| scenes/setup_ready_scene.dart | 153 | UI-KIT-55 |
| scenes/setup_steps_scene.dart | 325 | UI-KIT-55 |
| scenes/setup_unplugged_scene.dart | 118 | UI-KIT-55 |
| scenes/states_parts.dart | 133 | UI-KIT-55 |
| scenes/states_scenes.dart | 521 | UI-KIT-55 |
| scenes/states_working_scene.dart | 79 | UI-KIT-55 |
| scenes/team_discover_scenes.dart | 228 | UI-KIT-55 |
| scenes/team_scenes.dart | 711 | UI-KIT-55 |
| style/kit_signal.dart | 555 | UI-KIT-16 |
| style/kit_signal_slip.dart | 354 | UI-KIT-16 |
| style/kit_style.dart | 508 | UI-KIT-15 |
| style/kit_style_brutal.dart | 318 | UI-KIT-15 |
| style/kit_style_paper.dart | 263 | UI-KIT-15 |
| style/kit_style_signal.dart | 298 | UI-KIT-16 |
| style/kit_style_soft.dart | 388 | UI-KIT-15 |
| style/kit_style_solid.dart | 72 | UI-KIT-15 |
| style/kit_style_theme.dart | 76 | UI-KIT-15 |
| team/kit_digest.dart | 42 | UI-KIT-54 |
| team/kit_findings_card.dart | 97 | UI-KIT-54 |
| team/kit_merge_queue.dart | 42 | UI-KIT-54 |
| team/kit_milestone_row.dart | 43 | UI-KIT-54 |
| team/kit_phase_card.dart | 42 | UI-KIT-54 |
| team/kit_plan_card.dart | 153 | UI-KIT-54 |
| team/kit_project_row.dart | 43 | UI-KIT-54 |
| team/kit_promote_card.dart | 43 | UI-KIT-54 |
| team/kit_server_lane.dart | 42 | UI-KIT-54 |
| team/kit_spec_block.dart | 53 | UI-KIT-54 |
| team/kit_team_data.dart | 91 | UI-KIT-54 |
| team/kit_timeline_day.dart | 70 | UI-KIT-54 |
| team/team_parts.dart | 257 | UI-KIT-54 |
| terminal_key_bar.dart | 863 | UI-KIT-47 |

# APPENDIX B. Source index (docs worth reading first)

`docs/ux-system/kit-v2.md` (§1 new parts, §2 changes, §4 rules, §8 adaptive and capacity, §9 kit only), `docs/ux-system/kit-api/*.md` (one frozen spec per part, 111 files), `docs/ux-system/revamp/STANDARDS.md` (rule ids), `docs/ux-system/teaching.md`, `docs/ux-system/screen-standard.md`, `docs/design/visual-language-2026-09-26.md`, `docs/design/design-standard.md`, `docs/design/design-schools/contract.md` + `schools/*.md`, `docs/design/appearance-system-2026-10-01.md` (proposal; superseded by the schools), `docs/design/motion-and-illustration-2026-09-25.md`, `docs/design/iconography/contract.md`, `docs/design/genui-*.md`, `widgetbook/README.md`. Owner memory: `no-glass-rule`, `design-schools-direction`, `android-native-feel`, `component-max-width-rule`, `text-scale-cap`, `chat-transcript-turn-model`, `chat-kit-decisions-2026-10-02`, `micro-animations-wanted`, `design-standard-rule`, `teach-what-is-hidden`, `simplify-and-natural`, `no-raw-errors-rule`, `obvious-options-first`, `no-new-ui-levels`, `settings-scope-model`.

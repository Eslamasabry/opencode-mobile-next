# Native looks pass: direction (2026-11-04)

Owner, on the first full native preview: "It's nothing like ours." And for this pass: "Looks pass but
don't just copy flutter make it better."

This is phase 1: evidence, direction, concepts. No product code changes. Phase 2 builds the kit
changes below in order, and each screen is judged side by side against the concept the owner picks.

## 1. What went wrong

The migration ported the tokens but not the look. `native/ui/design` already holds the Flutter
values: the graphite ramp (`#0B0C0E` to `#26282D`), mint `#3DDC8A` / `#087F43`, amber for "Needs
you", Geist and Geist Mono, the 2026-10-30 type scale, the radii and the 4 dp spacing steps. The
native screens still look foreign because the **parts and the arrangement** came across as
behaviour-first defaults:

- The screen titles are small top-bar labels. Flutter uses a 28 sp large title on every hub.
- Top-bar actions are text buttons that wrap ("New chat", "Settin / gs"). Flutter uses icon
  buttons and a mint floating button.
- Search is a labelled outlined box ("Search" above an empty field). In Flutter it is a filled,
  fully rounded field with a magnifier, or only an icon.
- Filters come as a cloud of chips over two rows ("Show conversations from", "Inbox · 1", one chip
  per project). Flutter has one short row.
- State is a grey word at the far right ("Running", "Done"). In Flutter it is a coloured word
  leading the subline ("Needs you" in amber, "Working" in mint).
- The first run offers two switches for an either/or choice, and its buttons are content-width. Flutter
  has selectable choice cards, a mint check, a line illustration and a full-width primary button.
- Actions are bare text. Servers shows about 15 text buttons in a loose flow ("Edit server",
  "Server settings", "Usage and cost", "Connector catalogue", "Remove server"...) and plain
  key-value lines with no row, icon or chevron. The file viewer stacks "Wrap code lines / More file
  actions / Open with / Save" as centred text under the code. In Flutter, these are rows with a
  leading Phosphor icon, a title, a supporting line and a chevron, grouped, with one mint primary.
- The composer, the app's signature, is gone. The native chat has a stack of separate boxes: a
  "Claude Sonnet 5" button, an "Attach file" button, a plain "Steer while it works..." field, and
  "Stop" and "Add to this turn" buttons. There is no 26 dp pill and no round mint send. The running
  glow (`KitComposerGlow`) exists but did not show: the render used motion off, so check it on a
  device. The person's prompt is a mint-tinted bubble, but in Flutter it is a graphite surface2
  bubble and mint is kept for actions and live states.
- The model picker has a radio, a provider letter and an "Add favorite" text button on every row,
  "Close" as text, and the Thinking choice as chips at the bottom. Flutter has one-line rows, a star
  icon and a single mint "Use Claude Opus 5.5 · Build" button.
- Settings and This phone have the right structure but no leading icons, and the search field is
  an outlined box with a label. "Everything is running" is a small checked line, where Flutter makes
  it the page's hero.
- Rows lose their leading icon column. Keep running has the right words but no icons, so the eye has
  no left edge to scan.

The evidence pairs are in `/home/eslam/Storage2/wt/looks/before/` (`native-NN-*` next to
`flutter-NN-*`, light and dark). The Flutter references come from the goldens on
`mobile-next/master` (8302de537, the 2.1.1 product). Local `master` is three weeks stale and still
has the old 32/650 type scale, so do not use it as the reference.

## 2. What makes the Flutter app "ours"

| Layer | The identity | Values (Solid, the default) |
|---|---|---|
| Ground | Graphite, never pure black, with a warm off-white in light mode | dark `#0B0C0E` › `#141518` › `#1B1C20` › `#26282D`; light `#F3F3F1` › `#FAFAF8` › `#FFFFFF` › `#E9E9E6` |
| Ink | Three text steps, warm-neutral | dark `#F3F3F1` / `#A3A5AB` / `#8C8F96`; light `#111214` / `#50535A` / `#62656C` |
| Accent | One mint. It marks the primary action, the selection and *live* things | `#3DDC8A` (dark) with `#03140B` on it; `#087F43` (light) with white on it |
| Attention | Amber only means "needs you" | `#FFB547`, its fill and surface at 9–12 % |
| Type | Geist at quiet weights (500 for titles, 400 for reading), sentence case, Geist Mono for paths and commands | 28/34 large title, 22/28 title, 16/22 row title, 16/24 body, 13/18 secondary, label and caption |
| Shape | Soft but not bubbly | button 14, field 14, code 14, panel 18, card 22, composer 26, sheet 30 (top) |
| Space | 4 dp grid, 16 dp gutter, 54 / 60 dp rows, 22 dp between sections | |
| Surfaces | Flat lists on the ground with 7–8 % hairlines; one raised layer (composer, sheet, prompt bubble) | |
| Icons | Phosphor regular, in the secondary ink, never coloured tiles | |
| Motion | 150–250 ms, Android ripple, predictive back, stretch overscroll, selection haptics | |
| Signature parts | The **composer**: a 26 dp pill with a round mint send and the **running green glow** along its edge. **Tool step rows**: a verb, a mono argument, status at the right. The **folded work line** ("Read 3 files · edited 1 file"). The **status word** in its colour leading a subline. **Choice cards** with a mint outline and check. The **mint primary button**, full width at the bottom. **Line-art scenes** in mint and grey (the phone with the prompt mark). Agent prose with no bubble; the person's prompt as a right-aligned bubble. | |

Those last two rows are what people recognise. Tokens alone don't do it: a screen with the right
colours but none of these parts is the screen the owner rejected.

## 3. What is weak in the Flutter app (do not port these)

Sources: `docs/audits/ui-ux-audit-2026-10-14.md`, `docs/design/settings-ia-audit-2026-10-12.md`, the
Finora notes (2c2456324) and a fresh look at the 2.1.1 goldens.

1. **One small size does three jobs.** Secondary, label and caption are all 13 sp. Meta (times, counts)
   can't step down from supporting text, so rows lean on colour alone for hierarchy, and the
   model picker reads as a grey paragraph.
2. **Flat hierarchy on Conversations.** Every row has the same weight. What needs you is a 13 sp
   amber word, and the time sits in the subline, so it doesn't line up in a column.
3. **⋯ menus carry real actions** (OpenCode ⋯, Conversation ⋯, a file ⋯), against "obvious options
   first".
4. **The above-composer stack adds a level.** "Ask first / Server default / Stop" is a chip row above
   the box (the audit counts 9 slots that can stack there).
5. **Composer actions are 40 dp**, under the 48 dp target, on the most-tapped button (audit P1).
6. **Top-heavy first-run pages**: half the screen stays empty, and the same action appears twice
   ("Set up OpenCode on this phone" above and "Set up on this phone" at the bottom).
7. **Light mode is an afterthought.** The surfaces collapsed until 2.1 (P1-1), and the mint on
   off-white loses its energy because light uses deep green everywhere, including live dots.
8. **The file viewer is a sheet over a sheet-like page**, with its own ⋯ and close. It loses the back
   gesture and the context of where you came from.
9. **Off-grid spacing** (22 dp section gap, 50 dp buttons, 54 dp rows) and nested radii that don't add
   up (the request card tile, the wide composer).

## 4. How native will be better, not a copy

The rule: **keep the identity layers in §2 exactly, and spend the native advantage on behaviour,
hierarchy and a few signature moments.** Every change should remove something.

**4.1 Material 3 Expressive, on our terms.** M3's structure and motion, with our skin: graphite
surfaces, one mint and Geist. We do not use M3 dynamic colour by default. Its tonal palette would
erase the identity, so it stays a choice under Appearance.
- Shape morph on press: buttons and chips tighten their corners for 100 ms (16 › 12), and the send
  button morphs into the Stop square with its progress ring (decided as 11B in the chat kit).
- Springs for movement, not durations: the M3 Expressive standard scheme (spatial 0.9 / 1400 /
  700 / 300, effects 1.0 / 3800 / 1600 / 800). Fades stay at 150–250 ms. Reduced motion snaps.
- Container transform from a conversation row into the chat, shared axis for Settings drill-ins,
  and predictive back with the 28 dp preview corner on every page and on sheets (Compose has
  predictive back for sheets; Flutter 3.47 did not).
- Real M3 parts underneath the kit: `LargeTopAppBar` collapse, an extended FAB that shrinks to an
  icon on scroll, `ModalBottomSheet` with drag and back, M3 switches, a wavy progress indicator
  for long jobs. Ripple with state layers from our ink (12 %), never stock purple.
- Haptics: confirm on send and Allow, a selection click on toggles and segments, reject on a failed
  send. Stretch overscroll everywhere.

**4.2 Hierarchy and density.**
- Split the 13 sp tier: supporting text 14/20, meta 12/16 in tabular numbers. A conversation row
  then reads as title › state and subject › time.
- One fixed **status column** at the start of feed rows: an amber dot for "needs you", a mint ring
  with the glow arc for running, nothing for done. The state word stays in the subline for
  colour-blind readers, but it is no longer repeated inside a "Needs you" section.
- Times in their own right-aligned column.
- Rows on the 4 dp grid: 56 dp one-line, 72 dp two-line. Buttons 52 dp, sections 24 dp apart.
- Composer actions get 48 dp targets inside the same 26 dp pill.

**4.3 Finora ideas we take** (owner liked them on 2026-10-30; tab bar and glass skipped):
- **Cards in a reply**: when a step's result is a diff or a test run, the turn shows one small
  card ("checkout_test.dart +6 −2", "41 passed · 1 failed" with a thin bar), so the person does
  not have to open the fold to learn the outcome.
- **A summary card**: at most one per screen, and only where it replaces something. On
  Conversations it replaces the "2 need you ›" link and the filter row (concept B). In Settings it
  replaces the Server group (concept B). On This phone it is the "Everything is running" status.
- **Top fade**: the list fades into the ground under the large title as it scrolls, as a tone
  step within one neutral, not a gradient between colours.
- Settings rows show their **current value** at the right, so nobody opens a page only to read it.
- First run: one trust line ("Your keys stay on this phone") and a visible "I already have a
  server" path.

**4.4 Fewer levels.**
- The model and approval chips move inside the composer's bottom row, and the chip row above the box
  goes away.
- No ⋯ for a primary action: Search and Settings are icons on the home bar; the file viewer is a
  full page with Ask, Copy and Share as visible actions.
- The first run says each thing once: one primary button at the bottom.

**4.5 Light mode as a first-class theme.** Deep green `#087F43` for text and the filled button, but
bright mint stays for small live marks (the running dot, the glow) on a 3:1 boundary. The surface
staircase from 2.1 stays.

## 5. Proposed tokens for `native/ui/design`

Values marked **new** or **changed** differ from today's port. The rest already match and are listed so
the table is complete.

### 5.1 Colour roles (Graphite, Solid)

| Role | Dark | Light | M3 role it feeds | Note |
|---|---|---|---|---|
| ground | `#0B0C0E` | `#F3F3F1` | background, surface, surfaceContainerLowest | |
| surface1 | `#141518` | `#FAFAF8` | surfaceContainerLow | lists' pressed fill, code |
| surface2 | `#1B1C20` | `#FFFFFF` | surfaceContainer / High | composer, sheets, prompt bubble, the one card |
| surface3 | `#26282D` | `#E9E9E6` | surfaceContainerHighest, secondaryContainer | chips, tiles, selected |
| hairline | white 7 % | black 8 % | outlineVariant | |
| outline **new** | `#3A3C42` | `#C9C9C4` | outline | 3:1 boundary for fields and unselected choices |
| text1 / 2 / 3 | `#F3F3F1` / `#A3A5AB` / `#8C8F96` | `#111214` / `#50535A` / `#62656C` | onSurface / onSurfaceVariant | |
| accent | `#3DDC8A` | `#087F43` | primary | |
| onAccent | `#03140B` | `#FFFFFF` | onPrimary | |
| accentContainer **new** | `#16352A` | `#D6EEE0` | primaryContainer | tonal buttons, the selected choice card's wash |
| live **new** | `#3DDC8A` | `#1DB56A` | (kit only) | running dot, glow, live line; on light it stays bright |
| glow **new** | live at 35 % | live at 28 % | (kit only) | composer halo; the top edge at 70 % |
| attention | `#FFB547` | `#9A5700` (text) / `#FFB547` (fill) | tertiary | "Needs you" only |
| danger | `#FF7A7A` / fill `#D93B40` | `#C62828` / fill `#D93B40` | error | |
| stateLayer **new** | text1 at 12 % pressed, 8 % hover, 10 % focus | same | (ripple) | the ripple colour, never stock |
| topFade **new** | ground 100 › 0 % over 24 dp | same | (kit only) | under a collapsed large title |

### 5.2 Type (Geist; sp; chrome capped at 1.3×, reading text scales fully)

| Role | Size / line | Weight | Tracking | Change |
|---|---|---|---|---|
| display | 34 / 40 | 500 | −0.5 | **new**: first-run headline, expanded large bar (B concepts) |
| largeTitle | 28 / 34 | 500 | −0.28 | same |
| title | 22 / 28 | 500 | 0 | same |
| headline | 17 / 22 | 500 | −0.1 | **changed** from 16: chat title, sheet titles, separates from row titles |
| body | 16 / 24 | 400 | 0 | same, reading (scales fully) |
| rowTitle | 16 / 22 | 500 | 0 | same |
| supporting | 14 / 20 | 400 | 0 | **changed** from 13/18 (the old "secondary") |
| label | 13 / 18 | 500 | 0 | same: chips, section labels |
| meta | 12 / 16 | 500 | +0.1, tabular | **new**: times, counts, sizes, step durations |
| button | 16 / 20 | 500 | 0 | same |
| mono | 13 / 20 | 400 | 0 | line height 19 › 20 (4 dp grid) |
| monoSmall | 12 / 16 | 400 | 0 | **new**: tool arguments in step rows |

### 5.3 Shape (corner radius in dp; pressed = the morph target)

| Shape | Rest | Pressed | Change |
|---|---|---|---|
| tile | 10 | | 9 › 10 (grid) |
| button / field / popover | 16 | 12 | **changed** 14 › 16, plus the morph |
| chip | full | 12 | **new** morph |
| code | 14 | | same |
| panel | 20 | | 18 › 20 |
| card / choice card | 22 | 18 | morph on the choice card only |
| fab (extended) | 16 | 12 | **new** |
| composer | 26 phone, 20 wide | | same (inner actions concentric: 48 dp target, 40 dp circle, 6 dp inset) |
| dialog | 28 | | 24 › 28 (M3) |
| sheet (top) | 28 | | 30 › 28 (M3) |
| predictive-back preview | 28 | | same |

### 5.4 Space and size

| Token | Value | Change |
|---|---|---|
| S1–S6 | 4 / 8 / 12 / 16 / 20 / 24 | same |
| S7, S8 | 32 / 40 | **new** (page top, empty states) |
| gutter | 16 phone, 24 from medium | **new** width rule |
| sectionGap | 24 | **changed** from 22 |
| row one-line / two-line / three-line | 56 / 72 / 88 | **changed** from 54 / 60 |
| button height | 52 | **changed** from 50 |
| touch target | 48, composer actions included | composer **changed** from 40 |
| status column | 24 wide, mark 8 (dot) / 12 (ring) | **new** |

### 5.5 Motion

| Token | Value |
|---|---|
| springs (M3 Expressive standard) | spatial fast 0.9 / 1400, default 0.9 / 700, slow 0.9 / 300; effects fast 1.0 / 3800, default 1.0 / 1600, slow 1.0 / 800 (already in `MotionTokens` / `KitMotionScheme`) |
| fades | quick 150, standard 250 |
| press morph | 100 ms in, spring back |
| row › chat | container transform, spatial default |
| drill-in | shared axis X, 30 dp travel |
| list entrance | first 5 rows, 30 ms stagger, fade + 8 dp rise; never on refresh |
| glow | classic lap 3600 ms, fade 450 ms; under reduced motion the glow is a still halo |
| reduced motion | every spatial spec snaps; fades stay at 150 ms |

### 5.6 Width caps (every part has one; phone, tablet and landscape checked)

| Part | Cap |
|---|---|
| reading column (chat, settings, forms) | 720 |
| lists on their own | 960 (two panes from 840 wide: list 360) |
| composer | 760, centred under the reading column |
| primary button | 480 |
| summary card, result card | 720 / 560 |
| sheet | 640 (a 560 dialog from 840 wide) |
| chip label | 240, ellipsis |

## 6. Kit parts to change, in build order

Each step is one commit with goldens (light, dark, 2× text, Arabic, 360 / 412 / 840 / 1280 widths).
Shared single-owner files go in order; screens only rearrange kit parts.

1. **`ui/design`**: the token changes in §5 (`TypeScale`, `Shapes` with pressed radii, `Space`,
   `Roles` with outline, accentContainer, live, glow and stateLayer, and the width caps in `Layout`).
2. **`KitTheme` / `MaterialBridge`**: map every M3 colour role from the roles above, set the ripple
   and state layers, stretch overscroll, edge-to-edge and bar colours. This alone should stop the
   stock-Material look leaking through.
3. **`KitTopBar`**: large collapsing title with `KitTopFade`, at most two icon actions on phones,
   and text actions only from expanded widths. This fixes the wrapping "Settin / gs".
4. **`KitButton` family** (filled, tonal, outlined, text) at 52 dp with the morph; **`KitIconButton`**
   at 48 dp; **`KitFab`** (extended, shrinks on scroll); **`KitChip`** with the morph.
5. **`KitSearchField`**: filled, fully rounded, with a magnifier and no floating label.
6. **`KitRow`** with a mandatory leading icon column (24 dp icon, 16 dp gap), supporting text, and a
   trailing value or chevron. Every list of actions becomes rows in a group, never a flow of text
   buttons (Servers, file actions, This phone). **`KitFeedRow`** (new, or a `KitRow` variant): status
   column, title, coloured state word and subject, meta time column, 72 dp. **`KitSectionLabel`**
   at the 13 sp label size.
7. **`KitSummaryCard`** (new): the one card per screen (needs-you, connection, phone status).
8. **`KitComposer`**: chips inside the bottom row, 48 dp actions, send › stop morph with ring, glow
   kept and checked in light mode.
9. **Chat parts**: `KitWorkLine` (folded "Read 3 files · edited 1 file"), `KitToolRow` (verb,
   monoSmall argument, live dot, meta duration), **`KitResultCard`** (new: changes and tests), the
   live line, and the prompt bubble on surface2 (not mint).
10. **`KitChoiceCard`** (selectable, mint outline, check, accentContainer wash) and **`KitScene`** (the
    line illustrations ported as vectors).
11. **`KitValueRow`** (settings value at the right) and `KitSwitchRow` on the M3 switch.
12. **`KitSheet`** for the model picker: sticky search, one-line model rows (name, provider · context),
    the price and abilities under a details fold.
13. **`KitViewer` page frame** (full page, not a sheet, with visible Ask, Copy and Share) and
    **`KitStatusHero`** (This phone: "Everything is running").

Then the screens in the journey order: first run, Conversations, chat, model picker, Settings,
Servers, file viewer, This phone / Keep running.

## 7. Concepts and open taste calls

The concepts are in `/home/eslam/Storage2/wt/looks/concepts/`, four screens × A/B × light/dark:

- **A, "Flutter identity, refined"**: today's layout family with the §4 fixes.
- **B, "bolder native-first"**: M3 Expressive structure (display-size titles, a summary card, an
  expressive FAB, tonal containers), with the same skin.

What each concept proposes (and what it must not smuggle in):

| Screen | A | B | Watch out when building |
|---|---|---|---|
| First run | Line scene, two choice cards (mint outline and check), one trust line, "Set up on this phone", "I already have a server" | Three example prompts with their outcomes, a 36 sp headline, two big toggle tiles, "Continue", "Try a demo first" | B drops the size and time ("About 4 min · 208 MB"); it must come back under the tile. The image font is heavier than Geist 500 in both. |
| Conversations | Large title, search and gear icons, one chip row, Needs you / Today / Earlier with a status column and a time column, "New conversation" FAB | The needs-you question as the one summary card with Allow / Open, a "Running now" row with a progress line, project-letter tiles, "New chat" FAB | A repeats "Needs you" (section, chip, dot, word): keep the dot and the section, drop the word in that section. B's Allow must show what it allows (the exact command), or it becomes Open only. B's progress line has no real percentage source, so it must be an indeterminate line. |
| Chat, running | Folded work line, prose, one diff card, step rows with a live dot, "Running tests..." line, glowing composer with the model and approval chips inside | Changes and Tests result cards, a wavy progress line with "Details", composer with the send morphed into a ringed Stop | A shows the model twice (subtitle and chip); keep the chip. The test counts in B need a parser for test output, so the build starts with the Changes card. |
| Settings | Search field, five groups, current value at the right, inline switches | Connection card with "Switch server", tonal icon circles, the value as the supporting line | B's circles add weight; the owner's "flat lists" rule may favour A's bare icons. |

Open calls for the owner:
1. Per screen, A or B (or A with B's named part).
2. Conversations: keep the filter chip row (A) or replace it with the needs-you summary card (B)?
3. First run: choice cards (A) or a side-by-side toggle pair with example prompts (B)?
4. Light mode: bright mint for live marks only (proposed), or deep green everywhere as today?
5. Titles: 28 sp (today) or 34 sp display on the hubs (B)?

## 8. How the build is judged

- Each screen is checked against its picked concept at the same size, side by side, in three passes
  or more. List every way the build is less polished and fix it.
- One screen per image, with a one-line caption, light and dark. No grids.
- Anything a concept invented (data, chips, numbers) is checked for a real source before it is built.

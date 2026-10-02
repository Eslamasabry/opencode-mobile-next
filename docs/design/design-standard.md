# Design standard

Every screen is built from the same few parts, and each part is used the same way everywhere. This sits on top of the existing tokens: colour, type, radii and icons in `lib/ui/app_theme.dart`, `app_iconography.dart`, and `docs/design/refinement/shared-visual-system.md`. Those do not change here.

Owner's complaint that started it (2026-09-24): "every screen looks different and adhoc".

The parts live in `lib/ui/kit/`. A screen that needs something the kit lacks adds it to the kit, never a one-off. `test/design_standard_test.dart` checks the rules that code can check.

## 1. Screen

- A top bar with a title, back, and at most one icon action plus an overflow menu. The title says where you are, in sentence case.
- The body is a single scroll view with 16 dp side rails.
  - The last content always scrolls clear of anything pinned below it: bottom bar, primary button, keyboard.
  - `KitScreen` handles the padding. No screen pads by hand.
- One primary action per screen at most. On phones it is a full-width filled button pinned to the bottom, or it lives in the body when the screen has none pinned.

## 2. Buttons: one hierarchy

| Role | Widget | Where | How many |
|---|---|---|---|
| Primary: the one thing this screen or state is for | Filled | full width on phone, bottom of its block | at most 1 visible |
| Secondary: the likely other path | Tonal or outlined, full width under the primary | same block | at most 1 |
| Tertiary: rare paths | Text button, or the overflow menu when there are more than 2 | under the secondary, left-aligned | at most 2, the rest in overflow |
| Destructive | Error-coloured text or tonal, always confirmed | never primary unless the whole screen is the delete | — |

- Buttons in a block are stacked, full width, in the order primary, secondary, tertiary. Never right-aligned clusters, never mixed alignment.
- Where two tertiary actions must never sit side by side (a server's Update beside its destructive Stop), `KitActionStack` puts each on a line of its own, in the same order.
- On widths of 600 dp and up, they may sit in one row, primary rightmost.
- A button is never a status display. "Starting the server…" is progress (§4), not a disabled button with a spinner.
- A disabled button needs a reason visible near it, or it is hidden.

## 3. States: one component

Every "not the normal content" moment uses `KitStateView`: loading for a whole screen, empty, error, stopped/offline, blocked or permission needed. It has fixed slots:

1. **Icon** in a tonal circle. Its tone is neutral, working (accent), warning or error, from `AppStatusTone`. Never a solid red block.
2. **Title**: one line that says the state now. It must never contradict the progress: "Starting OpenCode…", not "stopped" while it starts.
3. **Body**: at most two short sentences on what happened and what to do.
4. **Progress** (optional): §4.
5. **Actions**: §2 hierarchy.
6. **Details** (optional): a collapsed "Details" row at the bottom for technical text (address, error, logs), in mono. Never above the actions.

It shows in two sizes:
- **Page:** fills the body, centred vertically, with no card around it.
- **Inline:** inside a list, with the same slots at smaller type.

Cards are for content the person works with, not for wrapping a message.

A message that belongs to one part of a form or list (a connection test's verdict, a save that failed, a credential the app can no longer read) is too small for a whole state: it uses `KitNotice`. Tinted icon, optional title, the words, optional note lines, at most two tertiary actions, optional dismiss. It sits on the content's rails with no filled block and no card, and it is one live region.

## 4. Progress and loading

- **One loading bar per screen**: a 2 dp linear bar directly under the top bar or header, with a semantic label. Nothing else on the screen shows a bar.
- Lists that are loading show skeleton rows (`KitSkeletonRows`), never an empty-state text and never "load more".
- **Known progress** (bytes, steps): a determinate bar with one line under it ("29 of 30 MB · about 1 min left"), inside `KitStateView` or `KitProgressRow`.
- **Waiting on something slow:** after 8 s without an answer, the state says so in plain words and offers the way out (Retry, or Restart for a server the app owns). A spinner never runs silently longer than 8 s.

## 5. Status line

`KitStatusLine`: one row with icon, one line of text and an optional action, used for conditions on an otherwise working screen (offline, stale, something running).
- At most one per screen, the most important one.
- It is dismissible only when dismissing it changes nothing real.
- It is not a card.

## 6. Lists and sections

- Section header: `SectionLabel`, sentence case, with an optional count or action on the right.
- Rows: `KitRow`, with a leading icon or status dot, title (1 line), supporting line (1 line, muted), and a trailing value, chevron or single icon action.
  - A list of steps (setup's checklist) leads each row with `KitStatusMark`: waiting, working, done, failed. The mark is a state, not a second bar.
  - Only titles in the person's own words (conversation titles) may wrap, and app words may wrap at large text, so nothing is cut to a few letters.
  - A setting whose supporting line explains what it does, or carries an error to act on, may take two lines (`supportingMaxLines: 2`). A list of things keeps one at ordinary sizes; at large text every supporting line wraps (two lines from 1.3×, three from 2.0×) before it is cut.
  - An action that cannot run now dims (`enabled: false`) and its supporting line says why.
  - A row that deletes something is `destructive: true`: error-coloured title and icon, and it confirms before acting.
- State lives in the row (dot, mark, "Needs you"), not in extra cards above the list.
- The thing in use now (the server the app is connected to) carries the current mark: `KitRowIcon(current: true)`, a filled accent circle, and its supporting line starts with the word ("Connected · …") so the mark is never colour-only.
- A row's rarer actions are in its overflow menu (`KitRowMenu`), never buttons in the row; destructive entries are error-coloured and confirm. A row that opens another screen ends with `KitChevron`; one that unfolds in place (a group, a rare choice) is `KitExpandRow`; an on/off setting is `KitSwitchRow`.
- The same thing appears once per screen.

## 7. Words

- Plain, short, in the person's terms: conversation, project, "this phone", "OpenCode".
- No paths, ports or internal names in titles. Put them under Details.
- The glossary test (`test/ui_glossary_test.dart`) is part of this standard.

## 8. Checked by tests (`test/design_standard_test.dart`)

- No `LinearProgressIndicator`, `CircularProgressIndicator`, `Card(` or raw `FilledButton` in screen files outside `lib/ui/kit/` once the screen is migrated.
  - Migrated screens are listed in the test. The list only grows.
  - An allowlist entry needs a reason.
- Each migrated screen has a golden render at 412×915, in dark and light, in `test/goldens/`. The renders are reviewed like code.

## 9. Migration order

1. Connection and server states (the connecting card, "stopped", "isn't answering").
2. The Work tab.
3. Phone setup (start, progress, ready) and the "This phone" card.
4. AI Team home and run.
5. Chat states (empty, errors, permission).
6. Settings.

Each step ships with its goldens and joins the checked list.

## 10. Motion and illustration

The owner (2026-09-25): "be more creative — add animations, cool graphics … they could even move". Consistent is not enough; the app should feel alive where a person waits, finishes something, or finds nothing yet. Spec and slices: `docs/design/motion-and-illustration-2026-09-25.md`.

- **One drawing style.** Line art in the brand's stroke (the "open portal" mark, `assets/branding/open-portal/mark.svg`): rounded caps and joins, one accent (`colorScheme.primary`) for what matters, muted and hairline strokes for the rest, soft accent washes for fills. Drawn in code as a `KitScene` (`lib/ui/kit/kit_illustration.dart`), never a bitmap, so it follows dark and light and scales. `KitPortalScene` is the reference.
- **Where drawings go.** A moment, not every screen: first run and setup, waiting (connecting, installing, a team at work), finished (setup ready, a task merged), empty (nothing here yet), and a few failures (not answering, offline). `KitStateView(illustration: …)` takes one in place of its icon circle. Never on a list row, a form field or a dialog.
- **How things move.** `KitMotion` holds the timings and curves: `quick` 150 ms for a control answering a touch, `standard` 250 ms for a part appearing, `entrance` 900 ms for a drawing drawing itself in, `celebration` 1.4 s for a finished moment, `breath` 4 s per ambient loop. Curves: `enter`, `exit`, `emphasized`. No other durations.
- **Loops only while waiting.** A drawing's entrance plays once. An ambient loop (`ambient: true`) runs only on a screen where the person waits, at most one per screen, and stops when the wait ends. Resting screens are still.
- **Respect the person and the phone.** With the system's "remove animations", every drawing shows its finished frame and nothing loops (`KitMotion.reduced`). Animate paint and transforms only — no layout, blur or shadow animation; each drawing sits in its own `RepaintBoundary`; tickers stop off screen. Issue #87's complaint was a choppy app: a new animation that drops frames on the emulator's `gfxinfo` is a regression.
- **Motion parts** (`lib/ui/kit/motion/`, all on `KitMotion.standard`, all instant on the first paint and under reduced motion). `KitPageTransitions` (`KitPageTransitionsBuilder` in the theme): the one page transition (shared axis); screens never pick their own. `KitRefresh`: every pull to refresh, a drop-in for `RefreshIndicator` (same `onRefresh`, same gesture) that draws the portal in; never the stock spinner. `KitReveal`: a part that comes and goes while the person looks — a refresh failure, a form verdict, a status line — unfolds in and folds away; keep it mounted with `child: null` when there is nothing to show, so it can fold. `KitAnimatedRows`: a short keyed list whose rows arrive or leave while the person looks (Inbox requests, queued messages, saved servers, terminal sessions); not for a long lazily built list, and restart it (a new key) when a search or a page load replaces its rows, so those show at once. `KitHaptics`: `send(context)` when the person's words leave, `done(context)` once when something they waited for finishes (a reply, setup ready); nothing on scrolling, rows or failures. A finished moment's drawing takes `entranceDuration: KitMotion.celebration`.
- **Tests.** `test/flutter_test_config.dart` sets `KitMotion.loops = false`, so screens settle for `pumpAndSettle` and goldens show the finished drawing. `flutter_animate` stays banned. Each scene has a golden (dark and light) of its finished frame.
- **Decorative by default.** A drawing is excluded from semantics unless it says something the text does not.
- **Floating surface.** `KitGlass` (`lib/ui/kit/glass/`, the name is historical) is the one floating surface: a bounded control or short row of controls floating over content that scrolls beneath it — the bottom dock, the rail, the shell's top controls, the chat composer. It is plain and solid: opaque `surface2`, the theme's hairline border, the token radius and at most the one tight elevation shadow. No blur, no translucency, no rim highlight, no shader (owner decision 2026-10-02: liquid and frosted glass were removed). Never a sheet, a page background or a full screen.
- **Effects the person controls.** Settings › Appearance › Effects holds the app-wide choices (`KitEffects`, provided above the navigator by `KitEffectsScope`, stored next to the theme): **Animations** (Full: drawings draw in and waiting screens breathe; Calm: drawings draw in, nothing loops; Off: every drawing shows its finished frame and pages change at once) and **Glowing border while replying** (off by default). Celebrations follow Animations and vibration is fixed. The system always wins: its remove animations makes everything still, and the page says so. Code reads the choices only through the kit (`KitMotion.reduced`/`loopsIn`, `KitIllustration`, `KitHaptics`), never from the preference directly.

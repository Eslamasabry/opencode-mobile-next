# Motion review, native app, 2026-11-09

Branch `native/motion-review` (from `native/next` b1dea5ee9). Owner, 2026-11-09: "Clicking on All conversations has a
weird animation. Please get animations reviewed properly."

Bar: subtle 150–250 ms transitions when UI appears or changes, framework APIs only, Android-native feel, reduced motion
gives snaps, the composer's running glow stays, and nothing may flash, double-animate, come from the wrong edge or move
content the person did not touch. Method: Emil Kowalski's review-animations standards (`.claude/skills/emil`), the
compose-expert and navigation-event skills, the code of every kit motion part, and frames read on the paused test
clock (desktop Compose, Skia raster, 412 × 860 phone or 1000 × 700 wide window).

Each class lists what it does, what is wrong and the fix. **Fixed** means fixed on this branch with a test that fails
without it; **Left** is a proposal, not done.

Landed on `native/next`:

- Classes 1–2 (All conversations): fix 42cff1e38, merge 670efe766. CI run 42558394581: compile and all four desktop
  shards green.
- Classes 3–7: fixes 098f82c13 and 2f6ace44d, gates e823532fc, tests be78605fd, merges 85fe5a3b4 and 33b79379f.
  CI runs 42559710235 and 42561156010: compile green; the desktop shards fail only tests that `native/next` already
  fails (run 42561262817), none from this branch.
- Red first: MOT-ALL-2 and MOT-ALL-4 failed before their fix (PC), and MOT-RTL-1, MOT-MODAL-1 and MOT-CALM-1 all failed
  on be78605fd (VPS) before 098f82c13 and passed on e823532fc (VPS, 3 of 3).

## 1. Pages share row bounds between two lists (the All conversations bug), Fixed

- **What it did.** Home → All conversations is a fade-forwards push (Flutter's: 450 ms, a quarter-width slide). The
  home's Right now / Recent rows and All conversations' rows carry the same grow keys (each grows into its chat), and
  the shell runs every page inside one `SharedTransitionLayout`. Compose paired the two rows as shared bounds, so every
  conversation on both pages flew from its home place down to its All place, the two titles cross-fading on top of each
  other while the page slid in under them. Back did the same in reverse. Home → a project page and back hit it too.
- **Fix (source).** A row's grow key pairs only while a page that grows from a row (a chat) is on top or is the page the
  transition leaves: `GrowRoutes` in `ui/shell/.../nav/ShellNavDisplay.kt` feeds `KitGrowHost.rowTransformInFlight`,
  read by the row, page and title shared bounds in `ui/kit/.../motion/KitGrow.kt`. A back gesture starts a new pair
  (the top page and the one beneath). The card → sheet morph keeps its own gate. Flutter has no Hero on these rows.
- **Tests.** `ui/shell` `ListToListMotionTest` MOT-ALL-1 (the new list's row never moves vertically during the push),
  MOT-ALL-2 (the home row stays put during the pop; red before the fix: 522 dp instead of 342 dp), MOT-ALL-3 (a row of
  the second list still grows into its chat).
- **Proof.** `all-conversations-before.png` (frames 16–450 ms: rows doubled at 64 ms, then sliding down the page) and
  `all-conversations-after.png`.

## 2. The window shows through page transitions, Fixed

- **What it did.** Nothing painted under `NavDisplay`. Past the covered page's fade-out (112 ms) the arriving page is
  still a quarter width short of the start edge and half transparent, so the Activity window (the platform's dark
  Material background) showed: a dark band at the start edge and a grey veil over a light page for about 300 ms on every
  push and pop.
- **Fix.** The shell paints the kit ground under both pages, as Flutter's `FadeForwardsPageTransitionsBuilder` paints
  its background colour.
- **Test.** MOT-ALL-4 (the start-edge pixel mid-push is the page ground; red before the fix: transparent).

## 3. Calm still travels, Fixed (two parts), the rest pass

- **What it did.** Calm means fades only. `KitUndoBar` still rose half its height and `KitFloatingPanel` 24 dp at Calm.
- **Fix.** Both rise only when `KitMotion.state.travels`; Calm fades in place.
- **Test.** `ui/kit` `MotionReviewClassesTest` MOT-CALM-1. **Proof:** `undo-calm-before.png`, `undo-calm-after.png`.
- Checked and fine: `KitPresence`, `KitStaggerRow`, `kitItemMotion`, `KitMenu` drop, `KitTabSwitcher`, page routes
  (Calm cross-fades).

## 4. A modal grows in from 96 %, Fixed

- **What it did.** The kit's own rule MOT-2 (`KitModalMotionRules.INITIAL_SCALE`: "a modal never scales in") and
  Flutter's dialog (fade only) say no scale, but the centred `KitSheet` and `KitDialog` still did
  `scaleIn(0.96)`, at Calm too.
- **Fix.** Both use `KitModalMotion.dialogEnter()`: fade on STANDARD plus an 8 dp rise at Full, fade only at Calm,
  none at Off.
- **Test.** MOT-MODAL-1 (the panel keeps its width mid-open). **Proof:** `centred-sheet-before.png`,
  `centred-sheet-after.png`. Gate MOT-GATE-4.

## 5. A slide comes from the wrong edge in Arabic, Fixed

- **What it did.** On a wide window a Full sheet is a 440 dp end sheet. `slideInHorizontally { it }` is a physical
  offset, so in Arabic (end = left) the panel crossed the whole screen from the right to reach the left edge, and the
  predictive-back shrink pivoted on the wrong side.
- **Fix.** The slide and the shrink origin follow the layout direction (`KitSheet.kt`, End placement).
- **Test.** MOT-RTL-1. **Proof:** `end-sheet-rtl-before.png`, `end-sheet-rtl-after.png`. Gate MOT-GATE-3 (every
  horizontal slide reads the layout direction; the predictive preview's swipe-edge shift is the one reviewed
  exception).

## 6. Loops that ignore the loop gate, Fixed

- **What it did.** `KitChoiceBusyMark` and the composer's Stop ring checked only `reduced`, so they kept turning at
  Calm and in still frames (screenshots, gallery). Every other kit loop checks `ambientLoops`.
- **Fix.** Both gate on `ambientLoops` (the Stop ring still also stops in battery saver).
- **Gate.** MOT-GATE-2: every `rememberInfiniteTransition` in the kit has an `ambientLoops`/`stillFrame` check above it.

## 7. Specs built outside KitMotion, Fixed (two) and ratcheted

- **What it did.** The Jump to latest strip's fade used `tween(STANDARD)` on Compose's default curve (not the kit's
  Enter curve, not scaled by the debug time scale); the voice pill used `animateContentSize()` with the default spring.
  The FAB's exit used the enter pace (250 ms) instead of QUICK.
- **Fix.** `KitMotion.specs.enter()`, `KitMotion.specs.quick()` and `specs.exit()`.
- **Gate.** MOT-GATE-1: a kit, kit-chat, kit-content or shell file that builds a tween, spring, keyframes, loop or a
  default-spring size animation outside the motion core must be on a reviewed list with its reason; the list only
  shrinks.

## 8. Loading → content, Left (proposal)

- Home: while it opens, cached rows and the skeleton are list items with no motion; on the first load the sections
  arrive as new keys with `kitItemMotion()`, so the old rows vanish in one frame and the panels fade in from 0 over
  250 ms: a short blank. Proposal: the opening items take `kitItemMotion(fadeIn = false)` so the swap is a cross-fade
  (fade out QUICK while the panels fade in), in `feature/chats/.../ChatsScreen.kt`. Not done here: the chats screen is
  being edited by other lanes today.
- All conversations and project pages: skeleton → rows is a hard cut (no flash, no dissolve). Proposal: `KitSkeletonRows`
  waits one QUICK beat before it fades in, so a load under 150 ms never shows placeholder bars at all. Not done:
  it changes the look of every loading golden.

## 9. Two animations on one thing, Left

- `KitRowPanel` eases its height with `animateContentSize` while the lazy item around it also has `animateItem`
  placement: two clocks for one displacement when a conversation moves between Right now and Recent. Proposal: drop
  `animateContentSize` and let the rows inside fade (the item placement already glides the panels below).
- `KitAgentCard` animates the whole card's size, so an agent's body update grows the card the person did not touch.
  Proposal: animate only the part the person opened.
- `ComposerStripReveal`: the Stop's own fade runs inside the strip's fade when both leave together. Minor.
- `ChatTranscript` puts a `KitReveal` inside a lazy item that also places (rule R5); it is mitigated (`glide` off while
  a reply streams). Minor.

## 10. Missing motion, Left

- `KitPageOverlay` (full-page editor) pops in and out in one frame. Proposal: fade STANDARD in, QUICK out.
- `KitFloatingPanel` has no exit. `KitTabStrip`'s selection pill jumps (its `indicatorMoves()` rule is never used).
- `KitSwipeRow` settles on a timed tween, not the release spring with the finger's speed.

## 11. Long durations, reviewed and kept

- Page push/pop 450 ms (Flutter's fade-forwards, also Android's own page time); predictive preview 300 ms.
- `KitArrival` wash fades over 900 ms after a 2.4 s hold: a "you landed here" mark, not a response to a touch.
- `KitToolNode` finishing tick 860 ms and the light/dark switch rim 600 ms: Flutter's numbers, on a part the person
  watches finish or pressed. Candidates to shorten if the owner wants them crisper.

## Passed

Root switch cross-fade; SlideUp pages (new chat, forms); the row → chat container transform and its predictive back;
bottom sheet slide on the spatial spring with velocity on release; scrims; `KitMenu` (fade, 8 dp drop, scale from the
anchor, RTL-safe); toasts; `KitReveal`/`KitPresence`/`KitAnimatedRows`/`KitSwap`/`KitSheetStep`; chips and segmented
controls (colour on QUICK); `KitTabSwitcher` fade-through; `KitStaggerRow` (RTL sign correct); `kitItemMotion`
(first layout never animates); the composer glow and hue (kept); Jump to latest (scroll, pill fade and rise); streaming
text (draw-phase fade, no relayout); `KitRefresh` (loop gated); theme fade (one cover, 200 ms). Every kit spec snaps
under reduced motion (`KitMotionSchemeTest`), and features hold no motion literals (B-BUILD-023).

## Proof files

Frame strips from the desktop tests on the paused clock, left to right, each frame labelled with its time (16 → 450 ms
for pages, 16 → 320 ms for kit parts). The All conversations strips come from the shell harness (a home list and a
second list with the same grow keys; the before run had the second list 160 dp lower, the after run 240 dp). The kit
strips render in the default dark theme on a 1000 × 700 window (sheets) or a phone (undo bar). These are not recordings
of the app; the checkpoint reviewer records the combined build on a device.

| Class | Before | After |
|---|---|---|
| 1–2 All conversations | `all-conversations-before.png` | `all-conversations-after.png` |
| 5 End sheet, Arabic | `end-sheet-rtl-before.png` | `end-sheet-rtl-after.png` |
| 4 Centred sheet | `centred-sheet-before.png` | `centred-sheet-after.png` |
| 3 Undo bar at Calm | `undo-calm-before.png` | `undo-calm-after.png` |

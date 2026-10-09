# KitWorkLine — frozen API (wave 0, 2026-09-26)

Group: chat. Unit `kit-KitWorkLine` (wave 1, tier 1b; after kit-KitChip, C25). In wave 2c `lib/ui/kit/chat/kit_work_line.dart` joins chain link chat-1 (C03, C38). Spec: kit-v2.md §9.2 (chat parts), §5; visual language §5 ("work is folded into one chip: Read 3 files · edited 1"); cut review C25. Rules: STATE-15, STATE-16, KIT-41, AUTO-15, LOOK-26, LOOK-5 (B2 interim), MOT-5, PERF-2.

## Purpose

The one line a turn's work folds under: a chip that says what was done ("Read 3 files · edited 1"), or what is happening now, or "Waiting for you", and opens to the steps in the order they happened. It keeps a long turn to one line so the reply's prose stays readable on a phone.

## Replaces

- **Map** (kit-v2.json, module:chat transcript): `embedded-message-view#embedded-message-view-tool-group-toggle` (kitGap KitWorkLine). Also `team-conversation#team-conversation-lead`'s fold of the lead's earlier lines (no assignment; it shares `_FoldLine` today). Map `statesMissing` fixed here: "expanded work group repeats its own summary line", "waiting-for-you turn still shows 'Running tools'" (AUTO-15, P7.5 via chat-1).
- **Code** (`lib/ui/screens/chat/message_view.dart`, chat-1 writes it): `_WorkGroup` (:1346), `_FoldLine` (:1494), `_FoldSteps` (:1581), the fold header of `_ToolCallGroup` (:1075), and the lead fold in `team_conversation_view.dart` (chat-4). Their raw widgets (InkWell, AnimatedRotation, CircularProgressIndicator, Icon, Text, Container) are part of `message_view.dart`'s G16 count (111 in total) that chat-1 brings to zero.

## File

`lib/ui/kit/chat/kit_work_line.dart` (new).

## Public API

```dart
/// Where a turn's work stands. The host decides; the line only says it.
enum KitWorkState {
  running,       // a step is running now
  waitingForYou, // a request waits for the person (AUTO-15): never a spinner
  done,          // finished; failures the agent got past stay quiet (STATE-15)
  endedFailed,   // the work ended on a failure: opens by default
  stopped,       // the person stopped the turn
}

/// What the work did, counted by the host from its tool calls.
@immutable
class KitWorkCounts {
  const KitWorkCounts({
    this.read = 0,      // files read
    this.searched = 0,  // searches (glob, grep)
    this.listed = 0,    // folders listed
    this.edited = 0,    // files edited or written
    this.ran = 0,       // commands run
    this.fetched = 0,   // pages fetched or searched on the web
    this.delegated = 0, // sub-agents started
    this.other = 0,     // other calls
    this.notRun = 0,    // calls the server never ran
    this.steps = 0,     // model steps (the fallback words)
  });
  final int read, searched, listed, edited, ran, fetched, delegated, other, notRun, steps;
  bool get isEmpty;
}

/// A turn's work, folded under one chip (STATE-16, KIT-41). Steps are the
/// turn's work and the prose between steps, oldest first; step boundaries
/// are never drawn.
///
/// States: running, waitingForYou, done, endedFailed, stopped; folded or
/// expanded (KIT-12).
class KitWorkLine extends StatefulWidget {
  const KitWorkLine({
    super.key,
    required this.counts,
    required this.state,
    required this.steps,          // KitToolRow, KitMessage.thought, KitMessage.reply (prose between steps)
    this.now,                     // running: the live step's words ("Editing lib/main.dart"); the chip's label
    this.expanded,                // non-null: controlled (the host's expansion store survives recycling)
    this.onExpansionChanged,
    this.lineKey,                 // on the chip (today's Key('work-group-header'))
    this.stepsKey,                // on the opened steps (today's Key('work-group-steps'))
  });

  final KitWorkCounts counts;
  final KitWorkState state;
  final List<Widget> steps;
  final String? now;
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;
  final Key? lineKey;
  final Key? stepsKey;

  /// Opened without asking when the work ended on a failure; otherwise
  /// folded. The host passes its stored choice as [expanded] when there is one.
  static bool opensByDefault(KitWorkState state) => state == KitWorkState.endedFailed;

  /// The summary words for [counts]: "Read 3 files · edited 1 · ran 2
  /// commands" (first segment capitalised in scripts with case; " · "
  /// between segments). With no counted call it is "{steps} steps".
  static String summaryOf(BuildContext context, KitWorkCounts counts);

  /// The most steps an opened line builds at once; older ones sit behind a
  /// "Show {count} earlier steps" row that reveals them in place (PERF-2).
  static const int stepCap = 25;
}
```

**The chip's words, by state** (the chip is `KitChip.summary(label:, expanded:, onPressed:)`):

| State | Leading mark (before the chip) | Chip label | Semantics label |
|---|---|---|---|
| running | `KitStatusMark(state: working)` | `now` if given, else `summaryOf` | "Working, {label}" |
| running, expanded (2026-10-08) | none: the running step in the list carries the one spinner | "Hide steps" (`kitWorkHideSteps`) | "Working, Hide steps" |
| waitingForYou | none (the KitRequestCard below carries the attention) | "Waiting for you" | "Waiting for you, {summary}" |
| done | none (done is the normal state) | `summaryOf` | "{summary}" |
| endedFailed | `KitStatusMark(state: failed)` with its word | `summaryOf` | "Didn't finish, {summary}" |
| stopped | none | `summaryOf` + " · Stopped" | "Stopped, {summary}" |

- **Opened.** Steps hang off a one-physical-pixel `hairline` stroke at the start edge, indented `space3` (one indent level for everything inside; the owner's "one indent level"). The opened list never repeats the chip's summary as its first row (the map's "repeats its own summary" defect).
- **Cap.** With more than `stepCap` steps, the newest `stepCap` are built; the row "Show {count} earlier steps" (a tertiary `KitButton`) builds the rest in place.
- **Failure colour.** STATE-15 "reddens" is read under LOOK-5's B2 interim: the failed mark and the word "Didn't finish" in `text1`, a neutral error glyph, no `danger`.

**Kit copy** (ARB, `kit` prefix, en + ar, ICU plurals with all Arabic forms, COPY-23): `kitWorkRead` "read {count, plural, =1{1 file} other{{count} files}}", `kitWorkSearched` "searched {count, plural, =1{once} other{{count} times}}", `kitWorkListed` "listed {count, plural, =1{1 folder} other{{count} folders}}", `kitWorkEdited` "edited {count, plural, =1{1 file} other{{count} files}}" (the VL example "edited 1" becomes "edited 1 file"), `kitWorkRan` "ran {count, plural, =1{1 command} other{{count} commands}}", `kitWorkFetched` "fetched {count, plural, =1{1 page} other{{count} pages}}", `kitWorkDelegated` "delegated {count, plural, =1{1 task} other{{count} tasks}}", `kitWorkOther` "{count, plural, =1{1 other step} other{{count} other steps}}", `kitWorkNotRun` "{count, plural, =1{1 not run} other{{count} not run}}", `kitWorkSteps` "{count, plural, =1{1 step} other{{count} steps}}", `kitWorkSeparator` " · ", `kitWorkWaitingForYou` "Waiting for you", `kitWorkStopped` "Stopped", `kitWorkDidntFinish` "Didn't finish", `kitWorkWorking` "Working", `kitWorkEarlierSteps` "Show {count, plural, =1{1 earlier step} other{{count} earlier steps}}". The existing `chatUi*` keys stay for other callers (COPY-3; no key is renamed, R04).

## States

Declared (KIT-12): **running**, **waitingForYou**, **done**, **endedFailed**, **stopped**, each **folded** and **expanded**. No loading or empty: a turn without work has no work line (the host does not build one). No disabled: the chip always opens. No error state of its own: endedFailed is the work's state, not the part's.

## Tokens

- ThemeRoles: `hairline` (the steps' stroke), `text1` (chip label, failed word), `text2` (secondary words), `surface3` (the chip fill, via KitChip), `accent` (the working mark only, via KitStatusMark; LOOK-6).
- KitText roles: `label` for the chip (via KitChip), `secondary` for the "Show earlier steps" row.
- KitTokens: `space2` (mark to chip), `space3` (steps indent), `space1` (steps' vertical rhythm), `minTarget` (48, the chip's hit area, KitChip's), `hairlineWidth(context)` (§0.5 step 2 seam, `_new-tokens.md`).
- Shape: the chip is `KitShape.pill` (pre-wave seam enum, via KitChip). No new token.

## Adaptive

The line follows the turn's width (the conversation pane, at most `KitLayout.paneDetailMaxWidth`).

- **compact / medium / expanded / large:** the same chip at the prose's start edge; the label truncates only at the end of one line below 1.3× text and wraps to two lines from 1.3× (A11Y-8, chip rule), with the full words in semantics.
- **Fine pointer:** the chip's hover state (KitChip, surface step); a tooltip is not needed (the label is visible).
- **Keyboard:** the chip is one Tab stop; Enter and Space toggle; when it opens, focus stays on the chip and Tab continues into the steps in order.

## Accessibility

- The chip is a button with `expanded` semantics and the label in the table above; the leading mark is excluded (its word is in the label).
- Not a live region. A state change (running → waitingForYou) is announced once by the host's status line, not here (A11Y-3).
- 48 dp target (KitChip). At 200 % text the chip wraps to two lines and grows; the steps reflow; nothing clips at 320 dp.
- "Show earlier steps" is a labelled button that moves focus to the first revealed step.

## RTL

- The chip, its mark and the steps' stroke sit at the start edge (directional insets, G7); the stroke is on the right under Arabic.
- The chevron (KitChip's) points down/up and is not mirrored.
- Paths inside step titles are the steps' (KitToolRow isolates them). The separator " · " comes from ARB so Arabic can use its own.

## Motion and haptics

- Opening and closing: the chevron turns (KitChip, `KitMotion.standard`, `emphasized`); the steps appear at once and fade in over `KitMotion.quick` (paint only). No size or layout animation: the line lives in a scrolling list (MOT-5, K2 §4.11).
- The working mark is KitStatusMark's; it is still under reduced motion.
- Reduced motion (`KitMotion.reduced`): no fade; one `pump()` settles (G8x).
- Haptics: none (MOT-11).

## Data safety and honest state

- "Waiting for you" replaces any running words while a request waits, and no spinner is drawn (AUTO-15, G38); the host passes `waitingForYou` whenever the conversation has an unanswered request in this turn.
- A failure the agent got past stays folded and unmarked (STATE-15); only `endedFailed` opens and marks.
- The counts are the host's; the line never infers work it was not told about. An empty `KitWorkCounts` with `steps: 0` says "0 steps" only if the host builds it, which it should not.
- `stopped` says so; a stopped turn is never shown as done.

## Depends on

- **kit-KitChip** (C25): `KitChip.summary`.
- Existing `KitStatusMark` (working, failed; no v2 feature needed, so no edge to kit-KitStatusMark-v2), existing `KitButton.tertiary` (earlier steps), `KitMotion`.
- Pre-wave seams: `KitTokens.hairlineWidth`, `KitShape.pill`.
- Consumed by kit-KitTurn (C25). Holds kit-KitToolRow and kit-KitMessage instances as `steps`, typed `Widget` so this tier-1b part does not import them.

## Tests required

`test/kit/kit_work_line_test.dart`:

1. `summaryOf` for `KitWorkCounts(read: 3, edited: 1)` = "Read 3 files · edited 1 file"; one segment only = "Ran 2 commands"; all zero with `steps: 4` = "4 steps"; Arabic resolves every plural form.
2. running with `now: 'Editing main.dart'` shows those words and a working mark; waitingForYou shows "Waiting for you", no working mark and no progress indicator anywhere in its subtree (AUTO-15).
3. done shows the summary and no mark; endedFailed shows the failed mark with its word and is expanded on first build; its colours contain no `danger` role (LOOK-5 interim).
4. Tap toggles; controlled `expanded` + `onExpansionChanged` never toggles on its own; uncontrolled starts from `opensByDefault`.
5. Opened: the steps are in the tree under `stepsKey`, in the given order, and no step repeats the summary text.
6. 30 steps: 25 are built and "Show 5 earlier steps" reveals the rest in place; focus moves to the first revealed step.
7. No `AnimatedSize`, `AnimatedContainer` or size tween in the subtree; under reduced motion one `pump()` settles.
8. Semantics: a button with `expanded`, label per the table; not a live region; ≥ 48×48.
9. Desktop capabilities: Tab reaches the chip, Enter toggles, the focus ring is visible.
10. 200 % text at 320 dp, LTR and RTL: no overflow (G6).

## Galleries required

`test/goldens/kit/kit_work_line_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20), each over a transcript-like ground with a line of prose above it:

- States at 412×915, dark and light: `kit_work_line_running`, `kit_work_line_waiting_for_you`, `kit_work_line_done`, `kit_work_line_done_expanded` (three KitToolRow-like steps), `kit_work_line_ended_failed`, `kit_work_line_stopped`.
- Default (done) at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800, dark.
- About 26 PNGs.

## Non-goals

- Counting or classifying tool calls (the host maps tool names to `KitWorkCounts`; chat-1).
- Rendering a step (KitToolRow, KitMessage).
- Deciding which parts of a turn are work (the host's turn model, `_timelineDisplayParts`).
- The team's task steps (`_TeamStepsFold` stays a KitExpandRow of KitRows).

## Open questions

None.


## Update 2026-10-09: the line and its steps are a timeline (KitStepTimeline)

The chip and the hairline stroke are replaced (owner request: the Claude app's grouping sheet, inline, no sheet). `KitWorkLine` keeps its API and states and draws itself with [`KitStepTimeline`](KitStepTimeline.md):

- Closed: the group's glyph (by what the work mostly did), the words, the kit's chevron. No pill. The working mark replaces the glyph while running; the failed mark when the work ended on a failure, with "Didn't finish" appended to the words like "Stopped".
- Opened, running: the line keeps the summary and no mark (the running step on the rail carries the one mark); "Hide steps" is no longer drawn. The screen-reader label is "Working, {summary}".
- Opened: the steps hang off the rail (dot, tile, state mark, preview card).
- Cap: the newest `stepCap` steps are built; "Show {n} earlier steps" reveals them `stepCap` at a time (n is at most `stepCap`), focus landing on the first revealed step. A 200-step turn never lays out more than 25 steps at once.

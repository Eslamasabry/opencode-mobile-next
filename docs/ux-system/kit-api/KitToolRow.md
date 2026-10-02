# KitToolRow — frozen API (wave 0, 2026-09-26)

Group: chat. Unit `kit-KitToolRow` (wave 1, tier 1e; after kit-KitCodeBlock, kit-KitDiffView, kit-KitRowParts-v2 and kit-KitStatusMark-v2, C25, plus the edges in README.md). In wave 2c `lib/ui/kit/chat/kit_tool_row.dart` joins chain link chat-2 (C03, C38). Spec: kit-v2.md §9.2 (chat parts), §5 (the tool group line is a KitExpandRow; tool output is KitCodeBlock); owner Fix on `embedded-tool-card` ("cap all outputs (~12 lines + See all), words for exit codes, one indent level, plain subagent card ('Delegated to explore · Done') that matches the team worker card"); team-conversation-2026-09-26 (a worker is a sub-agent card that opens its conversation in watching mode). Rules: STATE-16, KIT-41, AUTO-15, STATE-9, STATE-15, LOOK-5, PERF-2, KIT-32, ARCH-1.

## Purpose

One step of the agent's work as one line of the reply: what it did ("Read main.dart", "Ran npm test"), where, and how it went, opening to its note, output or diff. Its `.agent` form is a sub-agent or an AI Team worker: who, on what, in what state and for how long, opening that agent's own conversation.

## Replaces

- **Map** (kit-v2.json, module:chat transcript): `embedded-tool-card#embedded-tool-card-header` (kitGap KitToolRow), `embedded-tool-card#embedded-tool-card-subagent` (2 elements). Also `team-conversation#team-conversation-agent` (today a hand-built line; decided to match the sub-agent card). The same page's `embedded-tool-card-output` is KitCodeBlock's, placed in this row's `body`. Map `statesMissing` fixed here: "long shell output capped like edits" (the host caps with `KitCodeBlock(maxLines: 12)`), "waiting for permission (shows running)" (`waitingForYou`).
- **Code** (chat-2 rewrites `lib/ui/widgets/tool_card.dart`; chat-4 `team_conversation_view.dart`): the header and frame of `ToolCard` (tool_card.dart:394) with `_runningAccent`, `_TaskHeader` (:1407), `_AgentChip` (:1454, the orange off-palette chip), `_TaskBadge` (:1491), `_TaskWorking` (:1652), `_SectionCaption` (:1523), `_PathCaption` (:1732), `_SeeAllButton` (:2300); `_Mono` and `_DiffPreview` become KitCodeBlock and KitDiffView in `body`; `_TeamAgentLine` in `team_conversation_view.dart` (:975) becomes `KitToolRow.agent`.
- **Ratchet:** `tool_card.dart` G16 = AnimatedContainer 1, AnimatedRotation 1, CircularProgressIndicator 4, ClipRRect 2, ClipRect 1, ColoredBox 1, Container 15, DecoratedBox 1, Icon 17, IconButton 1, Image 1, InkWell 3, IntrinsicWidth 1, Material 1, Positioned 1, SelectableText 3, ShaderMask 1, SingleChildScrollView 1, Text 32, TextButton 3 (91). chat-2 brings it to zero with this part, KitCodeBlock, KitDiffView and KitImage. `ToolCard`, `TitleWithDetail`, `StepNote`, `formatToolDuration`, `runningToolTicker` and `taskChildSessionId` stay public in `tool_card.dart` (run_result_view.dart and tool/capture import it; R11): `ToolCard` becomes the adapter that maps a `ToolState` to a KitToolRow.

## File

`lib/ui/kit/chat/kit_tool_row.dart` (new).

## Public API

```dart
/// What kind of step it was. It picks the glyph only (one glyph per verb,
/// COPY-18); the words are the host's [KitToolRow.title].
enum KitToolKind { read, list, search, shell, edit, web, agent, todo, question, skill, other }

/// How the step went. The host maps the server's status; the row says it.
enum KitToolStatus {
  notRun,        // the server never ran it
  pending,       // accepted, not started
  running,
  waitingForYou, // blocked on a request (permission, question): never a spinner (AUTO-15)
  done,
  failed,
  stopped,       // stopped by the person or the server
  background,    // started and handed to the background (a background sub-agent)
}

/// One step of a turn's work, drawn as a line of the reply (STANDARDS
/// STATE-16, KIT-41): no frame and no fill; only what it printed, once
/// opened, is a block.
///
/// States: notRun, pending, running, waitingForYou, done, failed, stopped,
/// background; folded or open (KIT-12).
class KitToolRow extends StatefulWidget {
  const KitToolRow({
    super.key,
    required this.kind,
    required this.title,             // "Read main.dart", or the agent's own heading for the step
    required this.status,
    this.detail,                     // host words: "3 matches", "Exit code 1 · failed", the tool's name under a heading
    this.path,                       // the file or folder touched: mono, LTR-isolated, middle ellipsis
    this.added,                      // an edit's "+n"
    this.removed,                    // an edit's "−n"
    this.duration,                   // shown once finished
    this.note,                       // KitMarkdown (secondary): why, shown first when opened
    this.body = const <Widget>[],    // what it produced, shown when opened (see below)
    this.expanded,                   // non-null: controlled (the host's expansion store)
    this.onExpansionChanged,
    this.rowKey,                     // today's Key('embedded-tool-row') and test handles
  });

  /// A sub-agent the turn started, or an AI Team worker or reviewer. One
  /// line: "[title] · [status word]", the task under it, the elapsed time
  /// while it runs. A tap opens its conversation (watching mode).
  const KitToolRow.agent({
    super.key,
    required this.title,             // host words: "Delegated to explore", "furiosa · Worker"
    required this.status,
    this.task,                       // what it works on, one or two lines
    this.startedAt,                  // running: "for 3 min", ticking by the minute (KitSince)
    this.onOpen,                     // null: not tappable (no session to open)
    this.openLabel,                  // semantics hint; null: "Open its conversation"
    this.rowKey,                     // today's ValueKey('team-conversation-agent-<id>')
  });

  final KitToolKind kind;            // .agent: KitToolKind.agent
  final String title;
  final KitToolStatus status;
  final String? detail;
  final String? path;
  final int? added;
  final int? removed;
  final Duration? duration;
  final KitMarkdown? note;
  final List<Widget> body;
  final bool? expanded;
  final ValueChanged<bool>? onExpansionChanged;
  final String? task;
  final DateTime? startedAt;
  final VoidCallback? onOpen;
  final String? openLabel;
  final Key? rowKey;

  /// The kit's word for [status]: Not run · Waiting · Running · Waiting for
  /// you · Done · Failed · Stopped · Started in the background.
  static String wordFor(BuildContext context, KitToolStatus status);
}
```

**Frozen behaviour.**

- **The line.** At least 48 dp tall: a 20 dp kind glyph in `text2` at the prose's start edge; the title in `secondary`/`text1` (one line below 1.3× text, two from 1.3×, A11Y-8); then `path` (mono, middle ellipsis, full value in semantics and tooltip) or `detail` (`secondary`/`text2`); `+added` in `codeAdded` and `−removed` in `codeRemoved`, each with its sign (STATE-9); at the end the status mark and, if it opens, a 20 dp chevron.
- **Status marks.** running and background: `KitStatusMark(working)`. pending and waitingForYou: `KitStatusMark(waiting)` plus the word ("Waiting for you" is always visible; no spinner, AUTO-15). failed: `KitStatusMark(failed)` plus "Failed" in `text1` (LOOK-5 interim; no `danger`). stopped: `KitTaskMark(stopped)` plus "Stopped". done: no mark (the normal state); `duration` in `text3` instead. notRun: the title in `text3` (LOOK-14 disabled text) and the word "Not run".
- **Opens** only when `note` or `body` is non-empty. Opened: `note` first, then `body` in order, indented `space3` at one level whatever the row's depth (the owner's "one indent level"). Body holds kit parts: `KitCodeBlock` for output (the host caps it at `maxLines: 12` with "Show all" or "Open full output", PERF-2), `KitDiffView` for an edit, `KitImage`/file rows for produced files, `KitNotice` for an error explained in words. The row asserts nothing about body types; G16 keeps the call site kit-only.
- **Agent form.** The line reads "[title] · [word]" with `startedAt` adding "for {n} min" while running; `task` sits under it in `secondary`/`text2`, up to two lines. With `onOpen` the whole row is one `KitTappable` with a forward chevron; it never opens a fold.
- **No colour identity.** Agents are named, not coloured (VL §1, KitAvatar's rule; see KitAgentStrip).

**Kit copy** (ARB, `kit` prefix, en + ar): `kitToolNotRun` "Not run", `kitToolWaiting` "Waiting", `kitToolRunning` "Running", `kitToolWaitingForYou` "Waiting for you", `kitToolDone` "Done", `kitToolFailed` "Failed", `kitToolStopped` "Stopped", `kitToolBackground` "Started in the background", `kitToolTookSeconds` "{count, plural, =1{1 second} other{{count} seconds}}", `kitToolTookMinutes` "{count, plural, =1{1 minute} other{{count} minutes}}", `kitToolForMinutes` "for {count, plural, =0{under a minute} =1{1 min} other{{count} min}}", `kitToolOpenConversation` "Open its conversation", and the +/− words are KitCodeBlock's `kitCodeChanges` "{added} added, {removed} removed" (one key, COPY-18; KitCodeBlock is tier 1b).

## States

Declared (KIT-12): **notRun**, **pending**, **running**, **waitingForYou**, **done**, **failed**, **stopped**, **background**, each **folded** and **open** where it has a body; agent form: **running** (ticking), **done**, **failed**, **waitingForYou**, **not tappable**. No loading (a produced file's loading is KitImage's inside `body`), no empty (a step always has a title), no disabled.

## Tokens

- ThemeRoles: `text1`, `text2`, `text3`, `codeAdded`/`codeRemoved` (getters on ThemeRoles: `success`/`danger`, the diff roles only), `hairline` (the opened body's start stroke), `accent` (only the working mark and focus ring).
- KitText roles: `secondary` (title, detail, task), `mono` (path), `caption` (duration).
- KitTokens: `minTarget` (48), `smallIconSize` (20: glyph, chevron), `space2` (glyph gap), `space3` (body indent), `space1` (line rhythm), `hairlineWidth(context)` (§0.5 step 2 seam, `_new-tokens.md`).
- No new token. No frame, fill, radius, shadow or glass on the row.

## Adaptive

- **compact:** one line; path middle-cut; body blocks wrap (KitCodeBlock default) and diffs are unified.
- **medium / expanded / large:** the same line in the conversation pane (≤ `KitLayout.paneDetailMaxWidth`); diffs inside `body` may go side by side from expanded (KitDiffView's rule).
- **Fine pointer:** hover step on the row (KitTappable, surface step); the path's tooltip shows its full value.
- **Keyboard:** a row that opens or an agent row with `onOpen` is one Tab stop; Enter and Space toggle or open; after opening, Tab moves into the body (Copy, Show all).

## Accessibility

- A row that opens is a button with `expanded` semantics; its label is "[title], [path or detail], [status word]"; the glyph, mark and chevron are excluded.
- An agent row is a button labelled "[title], [task], [status word], for 3 min" with the hint `openLabel`; without `onOpen` it is plain text.
- The elapsed minute ticks never announce (not a live region, A11Y-3).
- +/− counts read as "4 added, 1 removed".
- At 200 % text the title wraps to two lines, the detail wraps under it, and the mark stays at the end; no overflow at 320 dp.

## RTL

- Glyph at the start, mark and chevron at the end (directional insets). The agent row's forward chevron mirrors (LAY-8); the fold chevron is vertical and does not.
- `path` is LTR-isolated and middle-cut; `+4 −1` is laid out LTR.
- Host words ("Delegated to {agent}") come with KitBidi.auto around the agent's name (COPY-30).

## Motion and haptics

- Open/close: the chevron turns (`KitSpin.chevron`, `KitMotion.standard`); the body appears at once and fades in over `KitMotion.quick`. No size animation (MOT-5).
- The working mark is KitStatusMark's (still under reduced motion). The elapsed words change once a minute without animation.
- Reduced motion: nothing moves; one `pump()` settles (G8x).
- Haptics: none (MOT-11).

## Data safety and honest state

- Output is shown only through KitCodeBlock (redacted, capped; SEC-4, PERF-2), never as raw text in the row.
- `waitingForYou` is never drawn as running (the map's "shows running" defect); `notRun` is never drawn as failed; `background` says the work continues elsewhere.
- A failure the agent got past is still "Failed" on its own row (true for that step) but the work line stays quiet (STATE-15; KitWorkLine's job).
- The agent form's state comes from the host's session state (team-conversation decision: "state from the session, not the agents list"); the row adds nothing.

## Depends on

- **kit-KitCodeBlock**, **kit-KitDiffView** (C25): referenced in docs and galleries as body content; the row itself imports neither type (body is `List<Widget>`), so these edges only order the gallery.
- **kit-KitRowParts-v2** (C25): `KitRowIcon`, the chevron, `KitExpandRow`'s fold behaviour.
- **kit-KitStatusMark-v2** (C25): `KitStatusMark`, `KitTaskMark(stopped)`, `wordFor`.
- **kit-KitMarkdown** (tier 1c: `note`), **kit-KitTappable** (tier 1b: the agent row's tap), **kit-KitSince** (tier 1a: minute ticks) and **kit-KitMotionParts** (tier 1a: `KitSpin.chevron`): edges added (README.md), no tier change.
- Existing `KitText`, `KitMotion`.

## Tests required

`test/kit/kit_tool_row_test.dart`:

1. Each status shows its mark and word per the table above; `waitingForYou` has no progress indicator in its subtree; `failed` paints no `danger` role.
2. `notRun` title uses `text3` and reads "Not run"; `done` shows the duration words and no mark.
3. `path` renders mono, LTR under `TextDirection.rtl`, middle-ellipsized at 320 dp, with the full path in semantics.
4. `added: 4, removed: 1` shows "+4" and "−1" and reads "4 added, 1 removed".
5. A row without note or body is not a button; with a body, tap opens it (note first, then body in order) and closes it; controlled mode never toggles by itself.
6. Opened body is indented one level (the same indent inside a KitWorkLine as on its own).
7. Agent: label "Delegated to explore · Done"; with `startedAt` 3 min ago it reads "for 3 min" and ticks at the next minute under fake async; tap calls `onOpen` once; without `onOpen` it is not a button.
8. No `AnimatedSize` or size tween; reduced motion settles after one `pump()`.
9. Semantics: labels as specified; ≥ 48×48; no live region.
10. Desktop capabilities: Tab reaches the row, Enter opens it, the focus ring is visible, the path tooltip shows the full path.
11. 200 % text at 320 dp, LTR and RTL: no overflow (G6).

## Galleries required

`test/goldens/kit/kit_tool_row_golden_test.dart`, DPR 3, Android (TEST-9, TEST-20):

- States at 412×915, dark and light: `kit_tool_row_running`, `kit_tool_row_waiting_for_you`, `kit_tool_row_done`, `kit_tool_row_done_open` (a capped KitCodeBlock output), `kit_tool_row_edit_open` (a small KitDiffView and +/−), `kit_tool_row_failed`, `kit_tool_row_not_run`, `kit_tool_row_agent` (running, ticking frozen by a fixed clock), `kit_tool_row_agent_done`.
- Default (done, closed, with a path) at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000, dark and light.
- Default at text 2.0 and Arabic RTL at 412×915 and 1280×800, dark.
- About 32 PNGs.

## Non-goals

- Mapping `ToolState`, tool names or exit codes to words (the host adapter `ToolCard`, chat-2; ARCH-1 keeps api types out of the kit).
- Rendering output, diffs or images (KitCodeBlock, KitDiffView, KitImage).
- Grouping steps (KitWorkLine) and the todo checklist (`mobile_task_view.dart` moves to KitChecklist in chat-2).
- Opening a session (the host's `onOpen`).

## Open questions

None.

## Failed step: Retry (owner decision 10A, 2 Oct)

A failed step is still and neutral (neutral failed mark, the word in `text1`; no red, no shake). `KitToolRow(onRetry:)` adds a quiet tertiary "Retry" button (`kit-tool-retry`, l10n `kitToolRetry`) on its own line under a failed step, aligned with the title; it shows only while `status` is failed and `onRetry` is non-null. `ToolCard` passes `onRetry` through; for a failed shell call with `onRerunCommand` it retries by running the command again, and any other failed call shows Retry only when the host gives `ToolCard.onRetry`. Flat rows (09A): the step line has no frame or card; only its opened body is indented content.

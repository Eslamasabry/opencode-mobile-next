# KitStatusLine v2 — API freeze (wave 0)

Unit: `kit-KitStatusLine-v2` (wave 1, tier 1b, kind `kit-change`, model opus). Spec: kit-v2.md §2.3, §4.9, design standard §5, with cut review C25 (dependency: KitSince) and C26 (`since`/`onSlow` with a fake clock). Rules: KIT-35, STATE-5, STATE-14, STATE-19, ARCH-8, A11Y-3, LOOK-4, KIT-43.

## Purpose

One condition on an otherwise working screen: offline or reconnecting, Android stopped the app, heat, a risky switch that is on, an update ready, or the team's "Now" line. v2 adds:

- the 8 s escalation inside the kit;
- a `next` line (the team Now line);
- `KitStatus`, a status value one status source per entity produces, so words are mapped in one place;
- the priority order that `KitScreen`'s status slot uses to show exactly one line.

It replaces every `MaterialBanner` and the update and release snackbars.

## Replaces

- **Map elements, 30 on 26 pages (kit-v2.json assignment → `KitStatusLine`):**
  - `chat` (chat-auto-approval-indicator);
  - `demo` (demo-disclaimer);
  - `desktop-release-notice` (desktop-release-notice-snackbar);
  - `development-services` (unsupported-paragraph);
  - `embedded-auto-approval-indicator` (embedded-auto-approval-indicator-open);
  - `embedded-product-states` (embedded-product-states-refresh-retry);
  - `embedded-team-phone-section` (phone-status);
  - `embedded-termux-attention-line` (line);
  - `external-task` (external-task-status);
  - `files` (files-status-notice);
  - `model-picker-sheet` (model-footer);
  - `model-picker-sheet-options-dialog` (options-capabilities);
  - `phone-setup-customize-sheet` (totals);
  - `review-workspace` (review-workspace-refresh-banner);
  - `session-link-server-missing-banner` (…-banner, …-dismiss, …-open-servers);
  - `settings` (library-search-summary);
  - `share-session-failed-banner` (…-banner, …-retry);
  - `shorebird-update-notice` (…-got-it, …-receiving);
  - `team-agent` (team-agent-header);
  - `team-conversation` (team-conversation-now);
  - `team-home` (team-now-line);
  - `team-plugin-sheet` (team-sheet-status);
  - `terminal` (terminal-refresh-banner);
  - `terminal-surface` (terminal-surface-status);
  - `termux-processes` (summary);
  - `tools` (counters).
- **Banners and snackbars it replaces (G1 baseline):**
  - `MaterialBanner(`: `lib/main.dart` 2, `lib/ui/screens/run_result_screen.dart` 1, `lib/ui/screens/terminal_screen.dart` 1, `lib/ui/widgets/product_states.dart` 1;
  - the update and release snackbars: `lib/update/shorebird_update_notice.dart` SnackBar 2, `lib/update/desktop_release_check.dart` SnackBar 1.

  They are adopted by coord-main (C32), screen-system-2 (C31) and their screen units, not here.
- **Retires:** slice-P7.6's escalation for status lines (C41), and the hand-built Now lines in `team_now.dart` (adopted by shared-team-1).

## File

- `lib/ui/kit/kit_status_line.dart` (`KitStatusLine`, `KitStatus`, `KitStatusKind`)
- Tests: `test/kit/kit_status_line_test.dart`
- Gallery: `test/goldens/kit/kit_status_line_golden_test.dart`

**The slot.** `KitStatusScope`, `KitStatusLineSlot` and `KitStatusContribution` are frozen in `KitScreen.md` ("The status slot"), in a new file `lib/ui/kit/kit_status_slot.dart` that is in **this unit's write set** (README.md, decision D5; a build_units change). The reason is dependency order: kit-KitRowParts-v2 (tier 1d) adds its risky-switch condition through `KitStatusContribution`, and kit-KitScreen-v2 is in the same tier. This unit builds the file exactly as KitScreen.md freezes it; KitScreen-v2 only hosts the slot, and the one-per-window assert stays with KitScreen-v2.

## Public API

```dart
/// Which condition a status is, in priority order, highest first (KIT-35;
/// `work` placed below the shell conditions and above updates, see below).
enum KitStatusKind {
  connection,   // offline, reconnecting, not answering
  appStopped,   // Android stopped the app; background checks paused
  heat,         // the phone is hot; the team is paused
  riskySwitch,  // a risky switch is on for this screen (KitSwitchRow.risk)
  work,         // a working screen's own line: the team Now line, a live process
  update,       // an app, code-push or server update is ready (STATE-19)
  info,         // anything else a screen shows (a search summary, a counter)
}

/// One condition, produced by the one status source for its entity (ARCH-8):
/// the words are decided there, once; screens pass this object, not strings.
@immutable
class KitStatus {
  const KitStatus({
    required this.kind,
    required this.icon,
    required this.message,             // "Reconnecting to laptop…"
    this.id,                           // stable identity for dedupe and announcements: "connection:<profileId>"
    this.tone = AppStatusTone.neutral,
    this.supporting,
    this.next,
    this.action,
    this.more = const [],
    this.since,
    this.onSlow = const [],
    this.onDismiss,
  });

  final KitStatusKind kind;
  final String? id;
  final IconData icon;
  final String message;
  final AppStatusTone tone;
  final String? supporting;
  final String? next;
  final KitAction? action;
  final List<KitAction> more;
  final DateTime? since;
  final List<KitAction> onSlow;
  final VoidCallback? onDismiss;

  /// Lower is more important (kind.index).
  int get priority;

  /// The one status to show among [statuses] (nulls ignored): the lowest
  /// priority value; ties keep the first. Used by KitStatusLineSlot.
  static KitStatus? highest(Iterable<KitStatus?> statuses);
}

class KitStatusLine extends StatelessWidget {
  // UNCHANGED parameters, then NEW optional ones.
  const KitStatusLine({
    super.key,
    required this.icon,
    required this.message,
    this.tone = AppStatusTone.neutral,
    this.action,
    this.more = const [],
    this.onDismiss,               // only when dismissing changes nothing real
    this.messageKey,
    this.dismissKey,
    this.dismissTooltip,
    this.controlsTogether = false,
    this.supporting,
    this.supportingKey,
    this.supportingSemanticsLabel,
    // NEW
    this.next,                    // the Now line: "Working on the login fix · a reviewer checks it next · about 6 min"
    this.nextKey,
    this.since,                   // the wait started here; escalates after KitMotion.escalateAfter (8 s)
    this.onSlow = const [],       // ≤ 2 ways out after the escalation
    this.moreKey,                 // default ValueKey('kit-status-more') (kept)
  });

  /// NEW (K2 §2.3). Renders a status from its entity's status source.
  factory KitStatusLine.of(KitStatus status, {Key? key, Key? messageKey});
}
```

- **Why `work` sits between `riskySwitch` and `update`.** KIT-35 lists the shell's conditions (connection > Android stopped the app > heat > a risky switch > update ready) and says a pushed working screen's line "takes the shell's condition when that ranks higher". It does not say where the screen's own line ranks. Putting it above `update` keeps a team's Now line from being hidden by "Update ready", which STATE-19 calls the lowest condition. `info` is last.
- **`StatelessWidget` becomes `StatefulWidget`?** The escalation is `KitSince`'s rebuild, so the class may stay a `StatelessWidget` that wraps its content in `KitSince`. Either is API-compatible, because the public constructor is unchanged. The builder picks one.
- **`KitStatusLine.of`** maps every `KitStatus` field onto the constructor. The key defaults to `ValueKey('kit-status-${status.id ?? status.kind.name}')`.
- **Countdown (vision 2026-10-20).** `KitStatus.countdownTo` / `countdownLine` (and the same on `KitStatusLine`): the supporting line becomes `countdownLine(timeLeft)`, asked again once a second through `package:clock` until the moment passes, then once at zero ("Trying again in 12 s", "Trying again now…"). Tabular figures; never in the live region, so the seconds are not announced. Only for something the app really scheduled (a retry timer). When the line would stack (large text, narrow window) it is drawn as the quiet form with the countdown inline, "Laptop isn't answering · Trying again in 12 s · Try now", so the action stays in the slot's room. The quiet form now also draws `more` behind a small More button, and `KitStatus.onOpen` makes its words a tap target (the connection line opens Details that way, with no More); `compact()` leaves a quiet status as it is.

## States

| State | What shows |
|---|---|
| condition | icon, message, optional `supporting`, optional `next`, action, More, dismiss (as today) |
| working (`tone: progress`) | the icon in `accent`; no spinner in the line (a spinner never runs silently past 8 s) |
| slow (escalated) | after 8 s since `since`: the supporting line reads KitSince's "Still waiting after 8 s"; the first `onSlow` action takes the action slot when it is empty, otherwise the `onSlow` actions go first in More; one announcement |
| Now line | `next` under the message in `text2`; it unfolds with `KitReveal` when it first appears and cross-fades when it changes |
| stacked (large text) | the action moves under the words (unchanged measuring); `controlsTogether` is unchanged |
| error | `tone: failure`: the neutral error glyph in `text1` (LOOK-5); the action is the fix ("Try again"); never a SnackBar |
| disabled | an action with `onPressed: null` shows its `disabledReason` under the line (kit-KitAction-v2); the line itself is never disabled |

Loading and empty do not apply: a status line exists only while its condition does. The host removes it through `KitReveal`.

**Tone and LOOK-4.** `AppStatusTone.attention` draws the warning glyph in `text1`, not amber: amber is reserved for needs-you (LOOK-4). A risky-switch line is `neutral` with the switch's glyph.

KIT-12 doc comment: "States: condition, working, slow, now-line, error (+ disabled action)".

## Tokens

- **ThemeRoles:**
  - icon per the shared tone map;
  - message `text1`;
  - supporting and next `text2`;
  - the bottom rule `hairline` at `KitTokens.hairlineWidth(context)` (pre-wave, LOOK-21), replacing `BorderSide(width: 0)`.
- **KitText:** `body` (message; today `bodyMedium`), `secondary` (supporting, next), `button` (via KitButton).
- **KitTokens:**
  - `smallIconSize` (20);
  - `space1`, `space2`, `space3` and `space4` (replacing `EdgeInsetsDirectional.fromSTEB(16, 2, 4, 2)`, `SizedBox(width: 12)`, `top: 15`, `vertical: 4/8`);
  - `minTarget` (More and dismiss);
  - `gutter`.
- **New tokens (pre-wave, `_new-tokens.md`):**
  - `KitTokens.statusLineMinHeight` = 52 (today the literal `minHeight: 52`);
  - the shared tone map `KitTokens.toneFor` / `toneColor` (README.md decision D12).

## Adaptive

- **compact:** full width directly under the top bar or header, on the 16 dp gutter.
- **medium:** the same, spanning the content area of its screen.
- **expanded / large:**
  - the shell's line spans the content area to the end of the rail;
  - in `KitScreen.twoPane`, a pushed working screen's line spans its own pane;
  - the action stays inline far more often, because the stacking is measured.

  "Also a count badge on the rail destination" (§8.2) is KitNeedsYou's, not this part's.
- **Short windows:** the same single line. `controlsTogether` keeps the controls visible.
- **Pointer and keyboard:**
  - Tab reaches the action, then More, then dismiss;
  - Enter and Space activate, and More opens its menu with Enter;
  - hover tooltips on More and dismiss repeat their labels (LAY-11);
  - it is not modal, so Esc does nothing.

## Accessibility

- One live region (unchanged). An announcement fires once per change of `(id, kind, message)`, and once on escalation. A change to `supporting` or `next` that only updates a time ("about 6 min" → "about 5 min") is not re-announced. The part compares its announcement key, not the full text.
- More and dismiss are 48 dp labelled controls (dismiss: `dismissTooltip`, or the existing `kitSheetDismiss` "Dismiss"; More: `kitMore`).
- At 200 % text, the message, supporting and next wrap and never truncate. The action stacks under them.
- A risky-switch line's action is the switch's "Turn off", so the switch can be turned off where it is shown (SEC-9).

## RTL

- The icon sits at the start, and More and dismiss at the end. Padding is directional (the existing `EdgeInsets.only(top: …)` becomes `EdgeInsetsDirectional`, G7).
- Server and conversation names inside `message`, `supporting` and `next` are wrapped by the status source with `KitBidi.auto` (COPY-30). Durations come from `intl`.

## Motion and haptics

- `KitEntrance` when the condition appears or its icon or tone changes (unchanged).
- `next` unfolds with `KitReveal` (`KitMotion.standard`).
- The escalation's supporting text cross-fades on `KitMotion.quick`.
- **Reduced motion:** instant.
- **Haptics:** none (MOT-11).

## Data safety and honest state

- **One status source per entity:** `KitStatusLine.of(status)` is the path for new code (ARCH-8). The existing string constructor stays for compatibility (KIT-43).
- **At most one line per window.** KitScreen-v2's slot enforces this with `KitStatus.highest`, plus a G37 debug assert in KitScreen. This part only provides the ordering.
- **No silent waits:** a `tone: progress` line with `since` escalates after 8 s (STATE-5). A progress line without `since` gets a doc warning only; a missing `since` cannot be asserted without false positives.
- **Honest dismiss:** dismiss is offered only when dismissing changes nothing real (DS §5, unchanged). A risky switch is never dismissible (assert: `kind == riskySwitch` with `onDismiss != null` fails), because hiding it would hide a live risk (SEC-9).
- **No contradictions:** the words never contradict the state (COPY-17). That is the status source's job, and G11 checks the fixtures.

## Depends on

- **kit-KitSince** (C25).
- **Existing:** `KitButton`, `KitAction`, `KitInset`, `KitEntrance`, `KitReveal`, `KitIconButton` (v1, for dismiss once the raw `IconButton` is replaced; optional).
- **Pre-wave:** `KitTokens.hairlineWidth`, `KitMotion.escalateAfter`.

Depended on by: KitScreen-v2 (the slot), KitRowParts-v2 (risky switch), screen-system-2, coord-main (C25, C31, C32).

## Tests required

In `test/kit/kit_status_line_test.dart`, using KitSince's fake clock:

1. `since` 7 s ago: unchanged. At 8 s: the supporting line reads "Still waiting after 8 s", the first `onSlow` action is visible in the action slot, and the rest are in More. Exactly one semantics announcement for the escalation.
2. `onSlow` with more than 2 actions throws an `AssertionError`.
3. `next` renders under the message. Changing only its numbers does not produce a new announcement.
4. `KitStatus.highest` over [update, connection, work] returns connection; over [update, work] returns work; ties keep the first; nulls are ignored.
5. `KitStatusLine.of(status)` renders the same text, actions and keys as the equivalent constructor call.
6. `kind: riskySwitch` with `onDismiss` asserts.
7. Existing constructor calls, including `controlsTogether`, `supporting` and `dismissKey`, keep their keys (`kit-status-more`, `kit-status-dismiss`) and their behaviour. The large-text stacking still works (existing behaviour, pinned).
8. `tone: attention` paints `text1`, not `attention` (LOOK-4). `failure` paints `text1` (LOOK-5).
9. Under reduced motion it settles after one `pump()` (G8).

## Galleries required

`test/goldens/kit/kit_status_line_golden_test.dart`, DPR 3, Android platform, under a stub top bar.

- **Declared states × dark and light at 412×915:**
  - connection (reconnecting, with action);
  - slow (escalated);
  - now-line (work, with next);
  - risky switch (with Turn off);
  - update ready;
  - error with Try again;
  - stacked at a long message.
- **Default (connection)** × dark and light at 360×800, 915×412, 800×1280, 1280×800 and 1600×1000.
- **Text 2.0 and Arabic** (connection, now-line) at 412×915 and 1280×800.
- **Names:** `kit_status_line_<state>…png`.

## Non-goals

- The one-per-window assert and hosting the slot inside `KitScreen` (kit-KitScreen-v2). The slot file itself is this unit's (see File).
- The status sources themselves (connection, heat, team). They are wave-2 or wave-3 work in their modules and use `KitStatus`.
- No replacement of any `MaterialBanner` or SnackBar call site in this unit.

## Open questions

- The ranking of `work` and `info`, where KIT-35 is silent, is decided above. KitScreen.md uses the same order. The coordinator may re-rank before wave 1 without changing the API, because only the enum order changes.

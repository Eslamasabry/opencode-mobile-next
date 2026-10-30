# KitSince: API freeze (wave 0)

> **Copy (SEC-13, coordinator 2026-09-27):** copies of technical values, logs and details use `KitCopy.copy(context, text)` (redacted). A part that also copies the person's own content passes `redact: false` for that content.

Unit: `kit-KitSince` (kind `kit-part`, tier 1a; cut review C12, which absorbs slice-P7.6's "tests use a fake clock"). Spec for `revamp.workflow.js`. These units depend on it (C25): kit-KitStateView-v2, kit-KitStatusLine-v2, kit-KitReceipt, kit-KitField, kit-KitChecklist and kit-KitRequestCard-v2.

## Purpose

KitSince is the kit's one wait timer. It is a builder that is told when a wait started (`since`). It rebuilds its host exactly once when the wait becomes slow at `KitMotion.escalateAfter` (8 s), and optionally once a minute after that, so the host can say "Still waiting after 8 s" and offer a way out (§4.9, STATE-5, DS §4). It draws nothing itself. It reads time through `package:clock`, so widget tests control it with the fake clock `testWidgets` already runs.

## Replaces

- **`GraceTimer` and `notAnsweringGrace`** (`lib/ui/widgets/grace_timer.dart`). Callers:
  - `workspace_screen.dart:1054`;
  - `team/team_board_screen.dart:480`;
  - `team/team_states.dart:67`;
  - `chat/chat_states.dart:424`;
  - `work_status_line.dart:193`;
  - `chat/team_conversation_view.dart:699`.

  The file is outside this write set, so it stays. Its callers move with their screen units, which move to `KitStateView`/`KitStatusLine` `since` and to `KitSince`, and the last one deletes it (KIT-43). `notAnsweringGrace` becomes `KitMotion.escalateAfter`.
- **Other hand-rolled 8 s timers** in screens: `chat_screen.dart:1297` and `team/agent_output_screen.dart:64` `quietAfter`. `team/merge_section.dart:42` `teamMergeArmWindow` is an undo-style arm window, not a wait, so it goes to `KitMotion.undoWindow`, not here.
- **G16 baseline:** none. KitSince draws nothing; it removes `Timer` plumbing, not widgets.
- **kit-v2.json `assignment`:** no element is assigned.

## File

`lib/ui/kit/kit_since.dart` (new). The write set is that file, `test/kit/kit_since_test.dart` and `test/goldens/kit/kit_since_golden_test.dart`.

## Public API

```dart
/// Where a wait is.
enum KitSincePhase {
  idle,    // since == null: nothing is being waited for
  waiting, // since was less than KitMotion.escalateAfter ago
  slow,    // since was KitMotion.escalateAfter or more ago
}

@immutable
class KitSinceStatus {
  const KitSinceStatus({required this.phase, required this.elapsed});
  final KitSincePhase phase;
  final Duration elapsed;              // Duration.zero when idle; never negative
  bool get isSlow => phase == KitSincePhase.slow;
}

/// How often a slow wait rebuilds after it turned slow.
enum KitSinceTicks {
  none,    // once, at the escalation: "Still waiting after 8 s"
  minutes, // then once per whole minute of elapsed time: "Waiting 4 min"
}

class KitSince extends StatefulWidget {
  const KitSince({
    Key? key,
    required DateTime? since,          // when the wait began, on this phone's clock; null = idle
    required Widget Function(BuildContext context, KitSinceStatus status) builder,
    KitSinceTicks ticks = KitSinceTicks.none,
    VoidCallback? onEscalated,         // once per [since] value, when it turns slow (a host's log or telemetry; not for announcing).
                                       // Not `onSlow`: that name is the hosts' List<KitAction> ways out (README.md, decision D14, D15)
  });

  /// "Still waiting after 8 s" (kitSinceStillWaiting, {seconds} formatted with intl).
  static String slowLabel(BuildContext context);

  /// "Waiting 4 min" / "Waiting less than a minute" (kitSinceWaitingFor, an ICU
  /// plural on whole minutes).
  static String waitingLabel(BuildContext context, Duration elapsed);

  /// The age alone, for a host that places it mid-line: "4 min" / "less
  /// than a minute" (kitSinceAge, the same ICU plural). KitNeedsYou.row and
  /// KitRequestCard's caption compose "waiting {age}" with it, so the kit
  /// words an age in one place.
  static String ageLabel(BuildContext context, Duration elapsed);

  /// The phase of a wait that began at [since], at [now] (default clock.now()).
  /// For controllers and tests that need the rule without a widget.
  static KitSinceStatus statusOf(DateTime? since, {DateTime? now});
}
```

**Behaviour, frozen.**
- **The clock.** `elapsed = clock.now().difference(since)`, clamped at zero: a `since` in the future, from clock skew or a server timestamp, reads as just started, never as negative.
- **Already slow.** A `since` that is already `escalateAfter` or more in the past builds `slow` on the first build. For example, a wait that began before the page opened is honest at once.
- **The timer.** While `waiting`, exactly one `Timer` is pending, for `escalateAfter − elapsed`. When it fires, the widget rebuilds once as `slow` and calls `onEscalated` once.
- **Minute ticks.** With `ticks: minutes`, the widget then keeps one `Timer` to the next whole minute of elapsed time.
- **A new wait.** A new `since` value, or `since` becoming null, cancels the timer and starts over.
- **Returning to the app.** On `AppLifecycleState.resumed` the status is recomputed, so time passed in the background counts.
- **Cleanup.** Every timer is cancelled on dispose, so no timer is left pending at the end of a test.
- **No Ticker or AnimationController**, so the part never blocks `pumpAndSettle` and never animates.
- **The 8 s threshold is not a parameter.** It is `KitMotion.escalateAfter`, one value for the whole kit (STATE-5, MOT-1).

## States

- `idle`, `waiting` and `slow` are this part's whole state. What the host shows in each is the host's slot:
  - KitStateView: the body "Still waiting after 8 s" with `onSlow` (for example Try again, Restart; COPY-7: never "Retry");
  - KitStatusLine: the same words in its line;
  - KitReceipt: "Not confirmed yet" with Try again;
  - KitField: validation that says "Still checking…";
  - KitChecklist: its step.
- Loading, empty, error, disabled and working are the host's. KitSince only tells it how long.

## Tokens

- **KitMotion:** `escalateAfter` (8 s; a pre-wave §0.5 step 2 named wait in `kit_motion.dart`, MOT-1). No colours, sizes or type.
- **ARB keys** (COPY-1, COPY-3, COPY-22, COPY-23; en and ar in the same change):
  - `kitSinceStillWaiting`: "Still waiting after {seconds} s", with `seconds` an int placeholder;
  - `kitSinceWaitingFor`: `{minutes, plural, =0{Waiting less than a minute} =1{Waiting 1 min} other{Waiting {minutes} min}}`. The Arabic value has zero, one, two, few, many and other forms, phrased جارٍ الانتظار … (COPY-25).
  - `kitSinceAge`: `{minutes, plural, =0{less than a minute} =1{1 min} other{{minutes} min}}`, with all Arabic forms.
- **New tokens:** none. **New dependency (flagged):** `clock` as a direct dependency in `pubspec.yaml` (see Open questions).

## Adaptive

- KitSince draws nothing, so compact, medium, expanded and large are the same.
- **Pointer and keyboard:** none; it is not focusable.

## Accessibility

- KitSince adds no semantics and announces nothing.
- The host's live region announces the change to `slow` exactly once. KitSince guarantees the phase flips once per `since`, so the host cannot announce twice (A11Y-3).
- Minute ticks rebuild the words, but the host must not make them a live region, because a per-minute announcement is noise.
- **200 % text:** not applicable. The labels are plain strings; the host wraps them.

## RTL

- The labels come from `app_ar.arb`, and numbers come from `intl` formatting for the locale (COPY-30, B17). There are no bidi characters in values (COPY-26).

## Motion and haptics

- No motion. Under reduced motion the timing is identical: escalation is a state, not an animation. The host's swap to the slow words may use `KitSwap`/`KitReveal`, which are instant under reduced motion.
- No haptics. A slow wait is not a finish (MOT-11).

## Data safety and honest state

- **No silent wait.** Nothing waits silently past 8 s. Every waiting part takes `since` and escalates by itself (STATE-5, §4.9), and hosts may not re-implement the timer. The G2 ratchet should add `Timer(` with an escalation literal and `GraceTimer(` outside the kit as patterns (reported for kit-gates-ratchet).
- **Wall time, not mount time.** A wait that started before a rebuild, a navigation or the app being backgrounded is measured from its real start (`since`), not from when the widget mounted. That is the honest-state difference from `GraceTimer`.
- **Clock skew is clamped**, so a server's timestamp can never show a negative or a huge wait. Callers should pass the phone's own time of the request.

## Depends on

- **Pre-wave:** `KitMotion.escalateAfter`, and `clock` as a direct dependency.
- `package:flutter/widgets.dart`, `WidgetsBindingObserver` and the app's `AppLocalizations`.
- No wave-1 part.

## Tests required

`test/kit/kit_since_test.dart`, all under `testWidgets`, whose `FakeAsync` fakes `clock.now()`:

1. With `since` = now: the phase is `waiting` at 7.9 s and `slow` at exactly 8.0 s (`tester.pump(const Duration(milliseconds: 7900))`, then `pump(100 ms)`). The builder ran once for the change.
2. `since` 20 s in the past builds `slow` on the first frame. `since` 5 s in the future builds `waiting` with elapsed zero.
3. `since: null` builds `idle` with no pending timer (`tester.binding.transientCallbackCount == 0`, no pending timers at teardown).
4. Changing `since` restarts: at 6 s set a new `since`, and it is still `waiting` at 13 s and `slow` at 14 s.
5. `onEscalated` fires exactly once per `since`, and never for `idle`.
6. With `ticks: minutes`, rebuilds happen at 8 s, 60 s and 120 s, and `waitingLabel` reads "Waiting 1 min", then "Waiting 2 min" (en), and the ar plural forms for 0, 1, 2, 3 and 11. `ageLabel` reads "less than a minute", "1 min" and "4 min".
7. On disposal mid-wait, no timer is pending after the tree is gone.
8. Under reduced motion and Effects › Animations: Off the timing is the same, and there is no ticker (G8).
9. `statusOf(since, now:)` matches the widget's phase for 0, 7999 ms, 8000 ms and −1000 ms.
10. A lifecycle `resumed` after 30 s of fake time with the timer suppressed recomputes to `slow`.
11. `slowLabel` gives "Still waiting after 8 s" in en and its ar value, with the number from `KitMotion.escalateAfter.inSeconds`.

## Galleries required

KitSince draws nothing. The G4 manifest still needs a gallery for every public widget, so `test/goldens/kit/kit_since_golden_test.dart` renders a minimal host: a `KitText` line driven by `KitSince` in `waiting` ("Connecting…") and `slow` ("Still waiting after 8 s"), and `ticks: minutes` at 4 min.

- In dark and light at 412×915.
- The `slow` state at 360×800 and 1280×800.
- `_text2` and `_ar` at 412×915 and 1280×800, to catch the Arabic plural and wrapping.
- At DPR 3, Android, with a fake clock (TEST-11: no wall clock).

## Non-goals

- No visible part: no spinner, no line, no retry button. Those are the hosts' (`KitStateView`, `KitStatusLine`, `KitReceipt`).
- No configurable threshold.
- No polling or network: it measures time only.
- No undo windows (`KitMotion.undoWindow`, KitUndo), no copied-check hold (`KitMotion.copiedHold`, KitCopy).
- No edits to `grace_timer.dart` (outside the write set).
- No call-site migration.

## Open questions

- **`clock` as a direct dependency in `pubspec.yaml` (coordinator).** It is already in `pubspec.lock` as a transitive dependency of `flutter_test`/`fake_async`, so nothing is downloaded. `pubspec.yaml` is in no wave-1 write set, and without the direct entry the analyzer's `depend_on_referenced_packages` fails. Does the coordinator add it before wave 1? If not, the fallback is a `@visibleForTesting static DateTime Function() now = DateTime.now;` seam on `KitSince`. Tests would then set it and advance it alongside `tester.pump`. That is weaker: two clocks to keep in step.

## Seconds, then minutes (2026-10-20)

`ticks: KitSinceTicks.secondsThenMinutes` rebuilds every whole second for
the first minute and once per whole minute after it, for a line that counts
a wait the person should act on ("Needs you · 20 s", then "· 4 min").
`KitSince.shortAge(context, elapsed)` words it: "{seconds} s" under a minute
(`kitTurnLiveSeconds`), then `ageLabel`.

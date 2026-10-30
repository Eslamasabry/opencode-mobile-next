# KitBackgroundWorkLine — API (2026-10-27, revised 2026-10-29)

Group: chat. Owner report 2026-10-27: a Claude Code turn ended with "The owner build is compiling … Once it's done I'll install it", the composer went idle, and the owner could not tell whether anything was still running. Claude had started the build with Bash `run_in_background`. Owner report 2026-10-29: under "Still running in the background · grep · 8 min" the chevron only scrolled the chat a little (the starting step was folded in the turn's work line), and nothing said what ran ("I can't understand what's happening or what's running"). Rules: no new UI levels (the line folds into the space under the last turn), STATE-16 (work reads as one quiet line), MOT-5/MOT-7 (subtle motion, still under reduced motion).

## Purpose

While work the agent started in the background still runs after its turn ended, one quiet line under the last turn says so: "Still running in the background · Build the owner APK · 2 min", with the step light on its glyph. With several tasks: "2 tasks still running in the background". Either line opens in place (the chevron means "opens here") to one row per task: its name and how long it has run, the command itself as technical text, and a forward chevron. A tap on a row takes the person to the step that started it. When the last task ends the line fades out (150–250 ms, `KitMotion.stepFade`; at once under reduced motion) and takes no room. The task's ending notice row in the transcript is unchanged.

## File

`lib/ui/kit/chat/kit_background_work_line.dart`.

## Public API

```dart
@immutable
class KitBackgroundTask {
  const KitBackgroundTask({
    required this.name,
    required this.since,
    this.command = '',
  });
  final String name;      // short plain words; never a path or an id
  final DateTime since;   // when the work started
  final String command;   // the command as run; shown on the opened row
}

/// States: none — the host decides what runs.
class KitBackgroundWorkLine extends StatefulWidget {
  const KitBackgroundWorkLine({
    super.key,
    required this.tasks,        // oldest first; empty fades the line out
    required this.onOpenTask,   // go to the step that started task [index]
    this.lineKey,               // on the line's tap target
    this.afterStop = false,     // the turn above failed or stopped
  });
  static String elapsedText(AppLocalizations l10n, Duration elapsed);
}
```

## Look and behaviour

- Built from `KitStepTimeline`: the line is the timeline's line (glyph, words, chevron), `live` while folded so the glyph carries the running step light; opened (`KitStepTimeline`'s own expand motion), each task is a node in the calm `KitStepNodeState.background` light with "name · time", the command under it in `KitText.mono` (tertiary, at most three lines, ending in "…", passed through `KitRedact` first) and a forward chevron.
- `afterStop`: under a turn that ended on a failure or a stop the line reads "Still running after the turn stopped · grep · 7 min" ("2 tasks still running after the turn stopped"), so it never reads as the agent itself still working (owner, 2026-10-29: a turn ended on Claude Code's "API Error: …" line with a grep loop still running). The chat sets it from `lastTurnFailedIn(messages)`.
- Time from `KitSince` with `KitSinceTicks.minutes`: "under a minute", "2 min", "1 h 5 min".
- One task or several: the line's tap opens and closes the list; a row's tap calls `onOpenTask(i)`. The list closes by itself only when no task is left.
- Screen reader: the line reads its words and its open or closed state; a row reads "name · time" and "Shows the step that started it".

## Host rule (what counts as running)

The host decides; the chat uses `runningBackgroundTasks` in `lib/domain/background_tasks.dart`: a step marked as started in the background, with no ending notice naming its call or task, started less than 6 hours ago, and not before the agent's process was last seen down. The line shows only once the turn has ended (the live turn line speaks while it runs).

Names: the agent's own description of the work when the protocol gives one (the Paseo mapper reads the shell step's `description` when present; Paseo 0.9.1 does not send it), else `backgroundTaskName(command)`. A command that ends in a shell loop (`until grep -q ready log; do sleep 30; done`) is `BackgroundTask.waiting` with the program it waits on as its name, and the chat words it "Waiting for grep" (`chatBackgroundWorkWaitingFor`).

Going to the step (the chat): `_jumpToMessage(messageID, stepID: callID)` opens the work line that folds the step, scrolls to the message and then onto the step, and marks the step (not the whole turn) with a `KitAnimatedBox` surface band for two seconds (`KitPace.quick` in and out; at once under reduced motion). The work line stays open afterwards. A step the transcript no longer holds gets the existing "That message is no longer in this conversation" notice.

## Tests

`test/kit/kit_background_work_line_test.dart` (words, time ticks, one task opening in place with its command, redaction, tap targets, fade-out, reduced-motion samples including the opening), `test/chat_background_work_test.dart` (the folded step opened and marked, a step in place marked, "Waiting for grep"), `test/background_tasks_test.dart` (loop names, the Paseo description label) and the gallery `test/goldens/kit/kit_background_work_line_golden_test.dart`.

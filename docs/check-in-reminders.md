# Check-in reminders for long runs

A per-server rule in the saved-server monitor: "Check in on long runs" with a
duration (15, 30, 60 or 120 minutes; default off). When busy observations span
the chosen time, the monitor can remind the user once for that sampled interval
and point them at the session. Work may pause or restart between checks.

## What the app can actually observe

The reminder is built on `ProfileMonitor`, which polls each monitored server
on a schedule (every minute in the foreground, every five minutes in the
background while Keep live keeps the process alive). Each successful poll
reads `sessionStatuses()`: a map of session id to `idle` or a busy state. The
monitor does not subscribe to session events, so:

| Claim | Basis |
|---|---|
| "Busy at checks spanning N min" | Time between the first and latest busy samples. It is not a lower bound on continuous work: unobserved idle periods and separate runs may lie between them. |
| "First check HH:MM" | The first busy sample in this interval, not the current run's start time. |
| Interval ends | A successful poll reports the session `idle` or omits it. |
| Interval continues | A successful poll sees it busy again within `busyObservationGap` (20 minutes) of the previous busy sample. |
| Interval restarts | The next busy sample comes more than 20 minutes after the last one. The app was killed, the phone slept, or the server was unreachable; an idle→busy turn could hide in that gap, so continuity is not asserted. |
| Failed or partial poll | Neither ends nor extends anything. The last observation stands. |

Consequently the reminder is "once per observed interval", not "once per real
run": two runs separated by a short idle between polls look like one interval
and remind once; one run interrupted by a 20-minute observation gap looks like
two intervals and can remind twice.

## Delivery

- The reminder becomes eligible when the span between busy samples reaches
  the threshold. Poll gaps, policies or unavailable delivery can delay or
  prevent a notification; no bound relative to the real run's start is claimed.
- Notifications use the existing coding-alert path (`showCodingAlert` with
  the new fixed-copy kind `checkin`: "OpenCode is still working / Tap to check
  in on the session"). No session title, prompt or path reaches the lock
  screen; the session is named only inside the app.
- All existing policy applies: the server must be monitored, notifications
  on, outside quiet hours, Wi-Fi rule satisfied, and the app backgrounded with
  Keep live active. Without the background service nothing can poll, so the
  reminder appears as a row on the monitor screen and inbox the next time the
  app is open. The settings copy says so.
- Unlike request alerts, check-in reminders are also posted for the active
  server: the live connection has no reminder of its own to duplicate.
- Tapping the notification (or the in-app row) takes the existing monitor
  route: token → persisted route → profile/location revalidation → the chat
  screen for that session. Nothing is sent or resolved on the user's behalf.

## At most once

The reminder's alert key names the observed interval
(`checkIn:<session>:busy-<first observation ms>`). Before invoking native
delivery, the monitor must persist the interval with `reminderClaimed: true`.
A refused write prevents dispatch. This is at most one dispatch attempt,
not guaranteed delivery: a crash after the claim or a native refusal can miss
the notification. The due row remains available in the app.

Foreground/background admission is checked again after route and claim writes.
Returning to the app during either write suppresses native delivery; an already
saved claim is retained rather than rolled back into a possible duplicate.

Restarting, clearing posted notification keys, or changing the duration does
not repeat a claimed interval. Turning the reminder rule off dismisses its
notification; enabling it again preserves the claim. Disabling monitoring
forgets observations, so later opt-in starts a new observed interval. Idle,
absence, a changed project/workspace or a gap beyond 20 minutes also ends
continuity. Posted notification keys remain responsible for dismissal.

## Storage and deletion

- Rule: `checkInAfterMinutes` inside the existing `oc.notifyRules.<profile>`
  JSON. Older payloads without the key read as off.
- Intervals: `oc.monitorBusy.<profile>`. Removed when monitoring is disabled,
  when the profile's credentials change (source reconcile), and by the
  profile-scoped preference sweep on deletion.
- Interval persistence contains session identity, location, observed times and
  the dispatch claim. Session titles remain in memory only.
- Routes and alert keys: unchanged, shared with request alerts.

## Not in this slice

Desk-presence inference, repeated-approval counting, prompt-language
conditions, and any automatic prompt or provider call. Exact run timing needs
a session-event subscription the monitor deliberately does not hold.

## Completion checkpoint — 2026-09-08

The original `91bb994` checkpoint was preserved work in progress, not a passed
feature. The completed source adds durable dispatch claims, active-server
Inbox reminders, workspace continuity and narrow-screen settings. Pinned
Flutter 3.47.2 generation and formatting completed; 85 focused/related tests
and 10 production capture cases passed, and the analyzer is clean. See the
[verification record and captures](qa/check-in-reminders/README.md).

Independent review and integration remain with the coordinator. This is
widget/controller verification, not device notification delivery or a full
repository gate. No account, provider or live-user-server action was performed.

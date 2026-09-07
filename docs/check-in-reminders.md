# Check-in reminders for long runs

A per-server rule in the saved-server monitor: "Check in on long runs" with a
duration (15, 30, 60 or 120 minutes; default off). When a session has been
**observed** busy for at least that long, the monitor reminds the user once
for that observed interval and points them at the session.

## What the app can actually observe

The reminder is built on `ProfileMonitor`, which polls each monitored server
on a schedule (every minute in the foreground, every five minutes in the
background while Keep live keeps the process alive). Each successful poll
reads `sessionStatuses()`: a map of session id to `idle` or a busy state. The
monitor does not subscribe to session events, so:

| Claim | Basis |
|---|---|
| "Seen busy for at least N min" | Time between the first and the latest poll that saw the session busy. Never the time since the poll, never the server's own start time. |
| "First noticed HH:MM" | The first poll that saw it busy. The real run started somewhere before that. |
| Interval ends | A successful poll reports the session `idle` or omits it. |
| Interval continues | A successful poll sees it busy again within `busyObservationGap` (20 minutes) of the previous busy sample. |
| Interval restarts | The next busy sample comes more than 20 minutes after the last one. The app was killed, the phone slept, or the server was unreachable; an idle→busy turn could hide in that gap, so continuity is not asserted. |
| Failed or partial poll | Neither ends nor extends anything. The last observation stands. |

Consequently the reminder is "once per observed interval", not "once per real
run": two runs separated by a short idle between polls look like one interval
and remind once; one run interrupted by a 20-minute observation gap looks like
two intervals and can remind twice.

## Delivery

- The duration must be met by observations, so a reminder arrives on the
  first poll at or after the threshold — up to one interval late.
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
(`checkIn:<session>:busy-<first observation ms>`). Posted keys are persisted
with the other monitor alert keys, and the intervals themselves are persisted
under `oc.monitorBusy.<profile>`, so a restart continues the interval and does
not repost. When the interval ends, the key leaves the valid set and the
notification is dismissed. Turning the rule off or changing its duration
dismisses posted reminders; the next poll re-evaluates.

## Storage and deletion

- Rule: `checkInAfterMinutes` inside the existing `oc.notifyRules.<profile>`
  JSON. Older payloads without the key read as off.
- Intervals: `oc.monitorBusy.<profile>`. Removed when monitoring is disabled,
  when the profile's credentials change (source reconcile), and by the
  profile-scoped preference sweep on deletion.
- Routes and alert keys: unchanged, shared with request alerts.

## Not in this slice

Desk-presence inference, repeated-approval counting, prompt-language
conditions, and any automatic prompt or provider call. Exact run timing needs
a session-event subscription the monitor deliberately does not hold.

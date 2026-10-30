# Stuck tools and interrupted replies on the phone server (2026-10-25)

## Symptom

On the owner's phone (APK 3250, built-in OpenCode 2.0.29, chat "Design review",
Auto-approve on), replies stopped with "Tool execution interrupted", helper
agents showed "Subagent cancelled", and even a one-line `printf` never ran.

## Evidence

Collected from the phone through the app's own terminal (This phone › Open a
terminal): `/root/.oc-opencode2/data/opencode/log/opencode.log` and
`opencode.db`. Local copies:
`/home/eslam/Storage2/launch-footage/qa-stability/phone-data/` (not committed:
it holds the owner's conversations).

1. Every interrupted step in the chat (`session_message`, 10-19 to 10-24) has
   tools with `executed: false`: the model asked for the tool, the server never
   ran it. Then nothing happened for about an hour.
2. Each interruption lands on a `location services evicted` line in the
   server log, to the second (for example 16:21:59 and 18:40:28 UTC on 10-24).
   That is OpenCode's 60-minute idle sweep, which interrupts the project's
   running sessions with reason `inactivity` and cancels their subagents. The
   sweep cleans up; it is not the cause.
3. The session and all six subagents carry the rules the app installs:
   `*:* ask` plus every agent allowance turned into `ask`
   (`lib/api2/session_permission_policy.dart`). With Auto-approve on, the app
   still answered each request from the phone.
4. The server log has `GET /api/permission/request` → **400**, "Expected JSON
   value at data[2].metadata.hidden", repeatedly.

## Root cause

OpenCode's glob and grep tools put their optional parameters (`hidden`, `path`,
`include`, `literal`, `caseSensitive`, `limit`) into the permission request's
metadata even when the model left them out. The live `permission.asked` event
drops the undefined fields and arrives fine, but the pending-request lists
(`/permission/request` and `/session/{id}/permission`) fail to encode and
return 400 for every request while one such request waits.

So any request the app missed live (app in the background, event stream
reconnecting) could never be found again. The tool stayed unexecuted until
the idle sweep stopped the reply an hour later.

Reproduced on a local OpenCode 2.0.29 with a free model: a single glob request
turns both lists into 400s. This is OpenCode issue #37650, fixed upstream by
PR #54090 (merged 2026-10-19). The same test on 2.1.1 (the app's pin) and 2.1.4
returns 200 with the request listed. The phone and the emulator still ran
2.0.29: the app offers the 2.1.1 update only as a manual "Update OpenCode" row.

## Fix (branch fix/oc2-auto-approve-server-runs)

- A conversation approved automatically keeps its original rules, so the
  server runs what its own rules allow without waiting for the phone. Its own
  asks (for example `.env` reads) and denials still apply and still reach the
  phone. Subagents copy the parent's rules when they are created, so they
  follow.
- Switching Auto-approve on or off re-applies the mode to the conversation
  and its known subagents mid-reply. The next message installs it in any case.
- Also found during the emulator run: right after the server boots a project
  it dropped as idle, `GET /agent/build` briefly returns 404. The first message
  after a quiet hour then failed with "The request could not finish". The
  agent read now retries twice over that boot.

## Still open

- Phone servers on 2.0.29 keep the broken list until updated to 2.1.1. The
  app should bring the in-app server up to its pin instead of leaving it to a
  manual row.

## Checks

- `test/api2_permission_dispatch_test.dart`: an automatic session restores the
  server rules before sending; switching off turns requests back on; the agent
  404 during boot is retried. The first test fails with the fix disabled.
- `test/api2_session_permission_policy_test.dart`: automatic mode keeps
  denials, and switching back asks again.
- Device runs: see below.

## Device runs (emulator-5584, built-in OpenCode 2.0.29, chat "Running uptime command", Auto-approve on)

The rules were read from the server's own database (`session_v2.permission`)
after each step. Screenshots: `/home/eslam/Storage2/launch-footage/qa-stability/emu/`.

| Build | Step | Server rules for the chat | Result |
|---|---|---|---|
| 3420 | send a glob request | `*:* ask` + every allow turned to ask | glob answered from the phone (e05) |
| 3420 | first send after an idle hour | — | failed: `GET /agent/build` 404 6 ms after the project booted (e03); a resend worked |
| 3430 | send a grep request | `[]`: the server's own rules | grep ran with no request to the phone, "done" (e08) |
| 3430 | switch to Ask first, without sending | ask rules back at once | (e10) |
| 3430 | switch Auto-approve back on, without sending | `[]` at once | (e11) |

The owner's phone: the Design review chat and all 6 of its subagents carried
the ask rules (phone database). On 2026-10-25 at 02:50 its built-in server was
updated from 2.0.29 to 2.1.1 with This phone › Update OpenCode.

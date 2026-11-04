RESULT: APPROVED WITH FIXES

# Approval: job 47-port-rest-2 (Flutter platform, Linux and protocol, part 2)

- **Reviewer:** Opus 5.5 (cloud).
- **Branch:** `native/p12x/job-47-port-rest-2`.
- **Merge:** step 1 merged `origin/native/p12-head` (`b6c30a59`). The job already contained that head (merge base = head), so the merge was a no-op with no conflicts.
- **Commits reviewed:**
  - documentation: `6dc59d8e`, `7faf0c3a`, `e9e54ca9`, `da931de8`, `7d31c69f`;
  - implementation: `0ea7ce27`, `615b2bf9`, `8ebe2d0e`.
- **Reviewed against:** the Flutter sources in `lib/` and `test/`, `native/AGENTS.md`, plan 01 (binding) and plan 17.
- **Not done here:** no Gradle build, tests or APK. Google Maven is blocked in this environment, so the build VPS compiles next. The job's own compile gate predates these fixes.

## What holds

- **Not needed (26 rows):** these cite the right decisions:
  - browser/DOM and Dart conditional-import plumbing: there is no web target (01 §1);
  - AI Team fixtures and scripts: 01 §12 D6;
  - Termux host scripts: 01 §12 D7, migrate-out only;
  - the agent browser: plan 17 N17-D1.
- **Already there (21 rows):** every cited file exists, and the code at the cited lines does what the report says:
  - Paseo files, changes, worktrees, subagents, terminals, uncertain start, provider import, host update and questions;
  - updates (`AppRelease`, `AppUpdates`, `AndroidUpdates`);
  - voice packs and settings;
  - clipboard image.
- **Ported now:** each row is reachable from the real app. The checks:
  - **Terminal:** Files › Folder › Terminal opens `:feature:terminal`, and the module is installed in the app graph. It follows Flutter's request contract and frame cursor format. The socket reuses the connection's own pinned client, and the ticket is never printed.
  - **Paseo commands:** `PaseoOperations.commands` uses Paseo commands with the Flutter limits.
  - **Card history:** gen-UI history feeds `BoundedCardHistoryGateway`, which chat card recovery and the handoff reader use.
  - **Handoff:** the handoff writer runs from ⋯ › Continue with… › review. It is offered only when workspace editing was negotiated, and it refuses records with secrets.
  - **Agent settings:** the agent settings sheet is in the conversation menu.
  - **Sends and agent starts:** the payload-use and agent-start budget hooks are bound to the shared phone admission.
  - **Session inventory:** it uses the same key, format, limits and scope hash as Flutter. `oc.sessionInventory.<id>` and `oc.agentHandoffs.<id>` are registered for the profile deletion sweep.
  - **Schedules:** schedule taps carry an in-memory one-shot ticket that other apps cannot forge.
  - **Shares:** shared text is never logged and is never sent automatically.
  - **Update notice:** it uses engine state only, in the existing kit status slot.
  - **License texts:** they match `LICENSES/` byte for byte, and the website opener is limited to the authored component URLs.
  - **Widgets:** the accent colours match exactly, and the style keys are swept with the profile. Sends claim durably before sending, and an uncertain delivery stays fenced. The panel window is `FLAG_SECURE`.
  - **File opener:** it is limited to external storage, its FileProvider is not exported, and it confirms before opening or installing.
  - **Manage space:** it reads no secrets and does not use the product graph. ZIP export removes partial files, and clear-data runs only after the confirm dialog.
- **Blocked:** the `permissions.dart` row is honest. Card qualification names only OpenCode 2, so deferred card approval stays off.
- **The `TODO(contract)` in `PaseoGateway.kt`:** it was already in P12 head and the job only reformatted it, so it is not a new stub.
- **Rules:** the added code has no `Dispatchers.*`, `Clock.System`, `GlobalScope`, `runBlocking` or `runCatching`, and shows no exception text. Feature UI uses kit parts only. Every new string key exists in both English and Arabic.

## Findings and fixes

1. **High: product startup regression.** `dac35675`.
   - **What was wrong:** the job moved the product start (stores, saved connection, platform hub, diagnostics, exit recovery, Linux reset finish) from `OcApplication.onCreate` into `MainActivity`. A cold process started by something else then had an unstarted product: alert buttons, background and built-in server services, schedule workers, tiles, widgets. Also, `MainActivity` started the product after `super.onCreate`, so the lifecycle owners (resumed activities, mic/result registries, visibility) missed the first activity.
   - **Fix:** `OcApplication.onCreate` starts the product again in the main process. `ManageSpaceActivity` runs in its own `:manage` process, which keeps the job's intent that Manage space never warms profiles, connects or starts Linux. The `:local_pdf` parser process also skips startup. `MainActivity`'s idempotent call moved before `super.onCreate`.
2. **Medium: terminal ticket replay.** `06ec0642`.
   - **What was wrong:** the upgrade client removed the stale-connection retry, but OkHttp still re-sent the request on status follow-ups (503 `Retry-After: 0`, 421). That could send the one-use PTY ticket twice.
   - **Fix:** a per-request network guard now refuses a second attempt, and redirects and connection retries are off.
   - **Also fixed:** closed terminal channels now leave the gateway's set (they had built up for the whole connection), and the rename field resets when the terminal's title changes.
3. **Medium: managed history move failed too early.** `c19ccac0`.
   - **What was wrong:** Flutter `ManagedMoveRunner` retries quietly while the phone server is still starting after an update. Native failed on the first not-ready result.
   - **Fix:** a not-ready attempt is retried after 8, 20 and 45 s, and the selection is checked again at each step.
4. **Low: undo after Discard.** `c19ccac0`. Undo after Discarding a share counted as a failure ("Couldn't open" / "failed again"). It now restores the earlier notice, and retries opening if the share was waiting for a server.
5. **Low: Manage space sizes.** `c19ccac0`. Sizes showed as raw bytes. They are now formatted like Flutter `formatPhoneStorage`, with Latin digits in an LTR isolate. The unused `manageSpaceBytes` string is removed in English and Arabic.
6. **Low: missing browser.** `c19ccac0`. The license website opener crashed when no browser was installed. That error is now caught.
7. **Report:** the affected rows are amended, and the compile-evidence paragraph now says that the approval fixes postdate the job's compile gate.

## Left for later (not blockers)

- **Duplicate widget persistence:** `WidgetPanelsWiring` and `PlatformWiring` can each create a `WidgetFeed.persistence` instance in a cold widget process. Saves stay correct because they compare-and-set; only the shared publication lock is briefly duplicated.
- **Handoff deletion count:** `AgentHandoffDeletion.clear` reports a count of 1 even when nothing was removed. This affects only the receipt count.
- **Session inventory reads:** `NativeSessionInventory.savedListTime` re-reads the stored list for each agent. This is correct but slower than needed.
- **Terminal view:** the native terminal is a readable text view with rows and columns set by hand, not Flutter's full terminal surface.
- **Dead code:** `ShareSeed` is now unused by the app (it is still referenced by `PlatformRulesTest`).

## Build VPS note

All fixes stay inside existing modules and APIs and add no dependencies:

- `Interceptor` and `Request.tag(Class, T)` (OkHttp);
- `Application.getProcessName()` on API 28+, with an `ActivityManager` fallback for minSdk 26;
- `kotlinx.coroutines.delay`;
- `androidx.compose.runtime.key`.

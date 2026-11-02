# P12-SCHEDULES — scheduled prompts, native parity

Date: 2026-11-02/03. Branch pushed: **`claude/p12-schedules-parity-qmwrb4`** (the session's assigned push branch; the
brief named `native/p12-schedules-2026-11-03` — the integrator can fetch either name from this one). Base:
`native/m1-2026-11-03` at `8a4eee69`.

Finish line: a person can list, create, edit, pause/resume, delete and "Run now" scheduled prompts for a saved server;
timed runs fire through WorkManager, show "Running…" then the finished notice with the reply's first line in the same
place, never send an occurrence twice, and Flutter-era schedules keep firing after the update.
Non-goal: "send later" from the chat composer (long-press Send) and the chat ⋯ door — chat is chat-lead's library.

## Shipping state (kept separate)

| State | Status |
|---|---|
| Implemented | engine, Android scheduler/notices, list + editor screens, app wiring, Servers door |
| Enabled | yes, in `:androidApp` (three modules added to the app's list) |
| Verified | engine + feature presentation tests **pass under a standalone Kotlin 2.4.21 JVM compile** (see Tests). **Not** verified by Gradle, not assembled, no emulator: this container's network policy denies `dl.google.com` (Android SDK and Google Maven), so neither the SDK nor AGP/AndroidX could be fetched. |
| Committed / pushed | yes, `[skip ci]` commits on the branch above |
| Deployed / released | no |

**Integrator must run first:** `./gradlew :engine:schedules:desktopTest :feature:schedules:desktopTest
:android:schedules:testDebugUnitTest :feature:servers:desktopTest :quality:test :androidApp:assemblePreviewDebug`.
Compose screens, the Android module and the wiring file were written against the current APIs and reviewed by hand,
but were never compiled.

## Journeys now working (in code)

1. **Servers → "Scheduled prompts"** (new secondary button on each saved server) → K01 list for that server.
2. **List (K01)**: each schedule as one row group — prompt, repeat in plain words, next time ("Today 09:00"), an
   **On** switch (pause/resume), **Run now**, the last run with what happened and what to do next (tap opens its
   conversation), **Delete** (confirm sheet). A **Morning summary** switch. One **New scheduled prompt** at the thumb.
   Unavailable states in plain words (server can't schedule, phone can't, saved data unreadable) with Try again.
3. **Editor (K02)**: prompt, project folder (seeded from the conversation, the route, or the server's newest
   conversation), repeat (Once / Every day / Weekdays / Every few hours), time, date (once), hours (interval), missed
   run choice, model (server default or a configured model), conversation (new each time, or continues the one it was
   opened from, keeping that conversation's model and agent). One plain issue at a time; a refused save keeps the
   typed fields.
4. **Timed runs**: every change re-arms WorkManager (one unique work per occurrence `oc.nschedule.<profile>.<id:rev:due>`,
   delayed to its due time, `KEEP`). The worker claims the occurrence durably (`oc.scheduleClaims.<profile>`) before any
   network mutation, runs the one-dispatch runner, records history, advances the schedule once and re-arms.
5. **Notices**: "Running your scheduled prompt…" on start; the finished notice **replaces it** (same tag/id) — title is
   the conversation title, body the reply's first line (≤100 chars, key-like tokens hidden); failures say why with an
   Open action; lock screen shows only the generic title. Frozen channel `opencode_scheduled_prompts`. EN + AR.
6. **Tap a notice** → trampoline → the run's conversation if it reached one, else the schedule's list (highlighted);
   a deleted schedule or changed server opens just the app (B-NAV-054).
7. **Run now**: the same worker (expedited on API 31+), no claim, next time unchanged; a second tap while it runs does
   nothing.
8. **Never twice**: claim New → send at most once; a worker killed mid-run is restarted by WorkManager and the
   recovered claim records "interrupted" without sending; duplicates/stale revisions/future triggers never send; an
   ambiguous create or prompt is "uncertain_delivery", never retried; a busy conversation is skipped, never steered.
9. **Flutter migration**: the native prefs file *is* the adopted `FlutterSharedPreferences.xml`, so
   `oc.scheduledPrompts.<profile>` is read in place — the codec is byte-compatible with the Dart JSON (test pins a
   Flutter envelope round-trip). On first UI start `restoreAll()` re-stamps Flutter fingerprints (bare SHA-256) with the
   native one (`n1:` + SHA-256 of backend/origin/username/managed/trust), keeping id, revision and claims, then re-arms
   and cancels Flutter-era `oc.schedule.*` work names. Profile deletion: all three keys match `oc.<what>.<id>` and are
   swept by `DefaultProfileScope`; armed work is cancelled when the profile disappears.

## Modules and files

| Path | What |
|---|---|
| `native/engine/schedules/**` | models + Dart-compatible codec, recurrence (DST-safe), CAS store, claims/index, one-dispatch runner, engine (list/editor state, runs, digest, migration, notification routing), `ScheduleTime` |
| `native/android/schedules/**` | `WorkScheduleAlarms`, `ScheduleWorkPolicy`/`ScheduleWorkInput`, `ScheduleNotifications`, EN/AR strings, icon, manifest (POST_NOTIFICATIONS, trampoline declaration) |
| `native/feature/schedules/**` | `SchedulesViewModel`/`SchedulesScreen` (K01), `ScheduleEditViewModel`/`ScheduleEditScreen` (K02), `ScheduleWords` (EN/AR), `SchedulesEntries` |
| `native/ui/routes/.../SchedulesRoutes.kt` (new file) | `SchedulesRoute.List(profileId, projectDir, from, highlight)`, `SchedulesRoute.Edit(...)` |
| `native/androidApp/.../graph/SchedulesWiring.kt` (new file) | engine binding, `AppScheduleProfiles`, `AppScheduleServers`, `SchedulesBoot` (re-arm + profile removal), `ScheduledPromptWorker`, `ScheduleOpenActivity` |
| `native/androidApp/build.gradle.kts` | +3 module lines |

## Catalog rows

Covered by tests (engine `commonTest`, feature `commonTest`, feature `desktopTest`):
B-STATE-089, B-STATE-090, B-STATE-091, B-STATE-092, B-STATE-093, B-STATE-094, B-STATE-095, B-STATE-096, B-STATE-097,
B-AGENT-215, B-AGENT-216, B-AGENT-217, B-AGENT-218 (changed server refuses before transport), B-SEC-152 (sign-in code
only), B-MIGR-007 (format + removal), B-MIGR-013, B-NAV-054 (resolver), B-NAV-055, B-L10N-014, SCREENS-NAV-98.

Still missing / out of this unit: B-AGENT-219, B-ANDR-070, B-LINUX-034 (scheduled runs on the phone's built-in
runtime — no native phone-runtime lease exists yet; such runs would need it), B-ANDR-118 (widgets), B-PROTO-055/056
(FactsBook reminders), B-AGENT-197 (phone engine), B-SEC-152 certificate/cleartext variants (codes mapped, untested),
B-NAV-054 trampoline itself (needs an emulator).

## Tests and results

Standalone check (no Gradle possible here): kotlinc 2.4.21 JVM over `core:common/model/security/domain/data`
(common + desktop actuals), `core:testing`, `engine:schedules`, and `ui:routes` + `feature:schedules` presentation with
four tiny stubs for `ViewModel`/Metrox/Nav3/SavedState (scripts were in the session scratch area, not committed).

| Suite | Result |
|---|---|
| `:engine:schedules` commonTest (ScheduleModelTest 13, ScheduleStoreTest 11, ScheduleEngineTest 19, ScheduledOpenTest 2) | **45 passed, 0 failed** |
| `:feature:schedules` commonTest (SchedulesViewModelTest 9, ScheduleWordsTest 4) | **13 passed, 0 failed** |
| `:feature:schedules` desktopTest `SchedulesScreenUiTest` (5, visible text, EN + AR) | written, **not run** (needs Compose/Skiko from Google Maven) |
| `:android:schedules` `NoticeTextTest` | written, **not run** |
| `:androidApp:assemblePreviewDebug`, `:quality:test` (Konsist K1/K2/K4/K11, ModuleOwners) | **not run** (blocked) |

## Deviations (integrator please review)

1. **Outside the write set, minimal:** `protocol/oc2 Oc2Gateway.Capabilities.scheduledPrompts = true` (Flutter api2
   says true; native said false, which would hide the feature); `feature/servers` gets the door (`openSchedules` with a
   default body in `ServersActions`, VM override, one button, one TODO(words) label); `MODULE_OWNERS` +3 rows.
2. **Words** live in `ScheduleWords` (EN/AR, TODO(words)) because features own no resources and `:ui:words` is
   l10n-owned; the language follows layout direction (RTL → Arabic) since `Locale` is outside the K1 allowlist.
   Notification strings are Android resources in `:android:schedules` (EN/AR).
3. **K02 is a page** (`SchedulesRoute.Edit`, SlideUp), not `SheetKey.Schedule` (no sheet key exists yet). **No K4
   pickers in the kit**: time/date are typed fields with plain examples (kit owner request below).
4. **Worker FQCN** is `io.github.eslamasabry.opencode_mobile.graph.ScheduledPromptWorker`, not the frozen Flutter
   name; native work uses new unique names and cancels the Flutter ones, so the frozen name is not needed for runs.
   Move it at the production switch if `ComponentFreezeTest` wants it. The trampoline activity is declared in
   `:android:schedules`' manifest by its `:androidApp` FQCN.
5. **Engine name** is `schedules` (brief), while `ModuleRules.engineOrder` says `schedule`; the engine depends only on
   core, so no rule fires. Rename either if engines ever need to depend on it.
6. **Exact alarms** (Flutter used AlarmManager + WorkManager) are not used: WorkManager delays can drift under Doze;
   runs that are late beyond 15 minutes follow the missed-run choice. A borrowed gateway is never re-pinned; a
   schedule's conversation is created by location and the model rides with the prompt when the live gateway is pinned
   to another project.

## Requests

- **Kit owner:** K4 date and time wheels (plan 07 §5 K) for the editor.
- **Chat lead:** chat ⋯ "Scheduled prompts" → `SchedulesRoute.List(profileId, session.directory, from = ref)`, and the
  long-press Send "send later" → `SchedulesRoute.Edit(profileId, from = ref)` (the routes and seeding already work).
- **l10n owner:** move `ScheduleWords` and the Servers label into `:ui:words`.
- **Notifications owner:** the runtime POST_NOTIFICATIONS request (Android 13+) is not asked here.

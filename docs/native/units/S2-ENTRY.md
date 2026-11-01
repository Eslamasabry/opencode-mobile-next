# S2-ENTRY: the entry router

Date: 2026-11-01. Branch: `native/entry-2026-11-01`, created from `claude/entry-router-bootstrap-gate-dy49we` at
`6b9a2b21`. Write set: `native/engine/entry/**` (new module), `EntryIntentReader.kt` in `native/android/platform/**`,
and this report.

Inputs: `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*`, plan `01` (ADR §6.2, §9.7), `07` §7.1–§7.13,
`15` (S2-ENTRY row, C15-4), `16` §1.8.1 (Astra M2) and §5.3 (`app_open`), `17` §4.2, the Flutter-era sources the rows
cite (`LaunchShortcutChannel.kt`, `LaunchLinkChannel.kt`, `LaunchShareChannel.kt`, `LaunchActivityBridge.kt`,
`SessionLinkIngress.kt`, `ScheduledPromptChannel.kt`, `lib/domain/session_handoff.dart`, `lib/domain/team_link.dart`).

## Result

**Implemented and verified on desktop (JVM); Android target compiles. Not wired into an app and not enabled:**
`MainActivity`, the app graph and `:ui:shell`'s `EntryHandoffCollector` are coordinator-owned and untouched.

- `:engine:entry:desktopTest`: **56 tests, 0 failures** (5 classes).
- `:engine:entry:compileAndroidMain`: passes.
- `EntryIntentReader.kt` compiled and passed a 2-test Robolectric smoke run (SDK 35) under a **temporary,
  uncommitted** `native/android/platform/build.gradle.kts`; `:android:platform` is S2-PLATFORM's module, so neither
  the build file nor the test is committed (see "Open problems").
- Planted faults (gate check removed, rule-5 generation check removed, untrusted `new_task` creating a session) make
  6 tests fail; all reverted.
- `:quality:test`: could not run, because `:engine:connection` does not compile on the base right now (see "Commands and results"). The Konsist rules and `ModuleOwnersTest` are therefore unverified for this module.

## What was built (package `io.github.eslamasabry.oc.engine.entry`, commonMain only)

| File | What |
|---|---|
| `EntryTypes.kt` | The 07 §7.2 contract: `EntryRequest` (8 kinds, each with `channel`, `receivedAt`, `origin`), `EntryOrigin` (Untrusted, Internal), `LaunchAction`, `AlertKind` (the 13 `CodingAlertKind` wire values), `MonitorToken` (redacted `toString`), `EntryTarget` (07's 17 targets + 2, D-E2), `EntryHandoff`, `EntryWaiting`, `EntryNotice` (keys only; words belong to `:ui:words`), `EntryLimits` |
| `EntryWorld.kt` | `EntryWorld`, the immutable snapshot `decide` reads (bootstrap phase, gate, active and saved profiles, per-profile `LinkState`, platform, `setup.json` status, stored schedules, usage providers); `EntryWorldSource` (bound by the app graph) |
| `EntryDecision.kt` | `EntryDecision` (Wait, Go, Drop), `EntryEffect` (Connect, ResolveRef, CreateSession, SeedDraft, ResolveMonitoredRequest, ResolveWidgetConversation, VerifyQuotaToken, ParkSessionLink), `Fallback` |
| `EntryDecider.kt` | The pure `decide(request, world, now)`: 07 §7.3 rules 3–4 and 7, the §7.4–§7.9 tables, and the Astra M2 trust rule (Connect/CreateSession only for `Internal` origin, plus the share's draft session) |
| `EntryRouter.kt` | `EntryRouter` interface and `DefaultEntryRouter`: one slot per channel, newer wins (rule 1); re-evaluates on submit, world change, `consume`, `resume`, and a 1 s tick only while a scheduled tap settles (rule 2); one serial lane; effects run in the router behind `attempt` (rule 6); a slot is cleared only if its generation is unchanged, a stale effect result is discarded (rule 5); one acknowledged `pending` handoff at a time (rule 8); memory only (rule 9). `EntryEffects` is the effect port |
| `EntryLinks.kt` | `EntryValidators` (the ported `SessionLinkIngress.accepts`, `isSafeIdentifier`, `TeamLink.isSafeId`, `ScheduledPromptPolicy.validId`, the launch whitelist and `usage_target` regex, the monitor-token shape) and `EntryLinkParser` (exact-shape session and team links; `session/v2` is not parsed, N4-O4) |
| `EntryIntentParser.kt` | `EntryExtras` (the frozen §7.13 extra names, actions and tile FQCNs), `IntentFields` (plain values copied out of an Intent; `toString` prints keys only) and `EntryIntentParser.parse(fields, origin, now)` → zero or more requests |
| `AppBootstrap.kt` | The ADR §9.7 order as ports (`BootstrapSteps`: crash handler → Redactor → crash-loop guard → graph on the caller's thread; StartupFence → warm prefs on IO; then problem-report capture, 17 §4.2), `GateSignal`, `AppOpenSink`. Publishes `BootstrapPhase` and emits `app_open` exactly once per process after Ready **and** gate open (16 §5.3); never in safe mode |

`native/android/platform/src/main/kotlin/io/github/eslamasabry/oc/android/platform/EntryIntentReader.kt`: `read(intent,
origin, clock)` copies only the contract's extras (non-string values read as absent; a Bundle that fails to unparcel
reads as "no extras") and delegates to `EntryIntentParser`; `strip(intent)` removes every owned extra, sets action
MAIN and clears data/type so a configuration change cannot replay it (07 §7.1).

## Catalog rows covered

Test names start with the row id (`native/AGENTS.md` rule 11).

| Row | Where | What is proven at this level |
|---|---|---|
| B-NAV-005 | `EntryDeciderTest`, `EntryRouterTest`, `EntryIntentParserTest` | each alert kind's typed target; Reply focuses the composer, Open does not; the resolved ref replaces the carried ids; internal widget rows resolve (missing → Land, never a broken chat); untrusted other-server alerts ask first; monitored alerts revalidate and a stale token gives a notice only; malformed tokens void the alert |
| B-NAV-025 | `EntryDeciderTest` | team links and team alerts open Chats with their filter (`NeedsYou` / `Running` / `None`) |
| B-NAV-054 | `EntryDeciderTest`, `EntryRouterTest`, `EntryIntentParserTest` | reached run → chat, else history; morning summary → history; deleted schedule or changed server → nothing; 30 s hold with a 1 s tick on virtual time; id validation |
| B-NAV-061 (amended) | `EntryDeciderTest`, `EntryRouterTest` | plain session link: active → chat; another saved server → switch sheet (D-E1); not saved → `SessionAddress` ticket that keeps the link; re-entry → edit with password focus. Address-link clauses dropped (N4-O4) |
| B-NAV-063 | `EntryLinkParserTest` | only the two ids; exact round trip; wrong scheme/host/path/fragment/port/user-info, repeated or missing parameters, unsafe or over-long ids → ignored without throwing |
| B-NAV-064 | `EntryRouterTest`, `EntryDeciderTest` | the launching link opens once; a live link is accepted; malformed links never become pending |
| B-NAV-077 | `EntryDeciderTest` | provider sign-in, agent sign-in for Claude on this phone, Read usage from for set-up rows; deleted server or unknown provider → Plans + notice; another server: untrusted asks to connect, internal connects first |
| B-ANDR-027 | `EntryRouterTest`, `EntryDeciderTest`, `EntryIntentParserTest` | delivered once; newer live tap beats a stale cold-start one; an item arriving while effects run is not swallowed; payloads missing either id are ignored; another server's pin never switches silently |
| B-ANDR-028 | `EntryDeciderTest`, `EntryRouterTest`, `EntryIntentParserTest` | every action's target (§7.4); unknown, null and non-matching values ignored; no-profile finals; Linux/phone setup off Android dropped; **untrusted arrivals: zero Connect/CreateSession for every action × every link state × active profile, and zero gateway calls through the router**; the same `new_task` from an internal origin runs `CreateSession`; tile long-press |
| B-ANDR-071 (amended) | `EntryLinkParserTest`, `EntryIntentParserTest` | only `opencode-mobile` session/team VIEW intents captured; the 1,024-char ASCII `0x21..0x7e` ceiling; invalid intents dropped without logging values. Address-link (`session/v2`, descriptor fetch) clauses dropped |
| B-ANDR-073 | `EntryRouterTest`, `EntryDeciderTest`, `AppBootstrapTest`, `EntryIntentParserTest` | held until bootstrap Ready and the gate open, then drained once; the reader strips every owned extra; ADR §9.7 step order; `app_open` once after the gate, never in safe mode |
| B-ANDR-075 | `EntryRouterTest`, `EntryDeciderTest`, `EntryIntentParserTest` | waits for a connected server, delivered once, the newer share wins, the text is seeded unsent; failure → notice; empty or non-text shares ignored; subject join; 8,000-char bound |

Several of these rows are `e2e`/`integration` in the catalog. This unit proves their common (router) half only. The
Robolectric `UntrustedArrivalTest` / `TrustedArrivalTest` of 07 §7.4 need `MainActivity`, `.InternalEntryActivity`
and the app graph (coordinator and S2-PLATFORM), so they are not written here.

## Commands and results

JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), Android SDK at `/opt/android-sdk` (`platform-tools`,
`platforms;android-37.1`, `build-tools;37.0.0`; AGP also pulled `build-tools;36.0.0`), `native/local.properties`
(gitignored). Maven Central answered **429 Too Many Requests** to this container, so a local-only init script
`~/.gradle/init.d/mirror.gradle.kts` puts Google's Central mirror
(`maven-central.storage-download.googleapis.com/maven2`) ahead of the settings repositories. Nothing in the repo changed for
this.

| Command (from `native/`) | Result |
|---|---|
| `./gradlew :engine:entry:desktopTest` | BUILD SUCCESSFUL; 56 tests, 0 failures (AppBootstrapTest 2, EntryDeciderTest 26, EntryIntentParserTest 9, EntryLinkParserTest 5, EntryRouterTest 14) |
| same, with three planted faults | 56 tests, 6 failed (as intended); reverted with `git checkout` |
| `./gradlew :engine:entry:compileAndroidMain` | BUILD SUCCESSFUL |
| `./gradlew :android:platform:testDebugUnitTest` with a temporary build file + smoke test (both deleted) | BUILD SUCCESSFUL; 2 tests, 0 failures |
| `./gradlew :quality:test` | **Not runnable now:** BUILD FAILED in `:engine:connection:compileKotlinDesktop` (`ConnectionRegistry.kt:48` `GatewayFactory.backend` unresolved, `ConnectionSession.kt:26` `StreamStatus` mismatch), the core contract the integration session is reconciling. Not caused by this unit; rerun after the integration merge |

No formatter is configured in `native/` (`tool/format-changed.sh` does not exist yet); files follow the existing style.

## Deviations

- **D-E1, untrusted arrivals never switch servers.** 07 §7.6 gives a link to another saved server the effect `Connect`
  then `Land(Chat)`; 07 §7.9 switches for a scheduled run. Links and everything reaching `MainActivity` are untrusted
  (16 §1.8.1, Astra M2: "network-silent", no connect before a tap), so those become `SwitchServerThen(profile, reason,
  then)`, the sheet that asks first. Internal (app-owned PendingIntent) scheduled taps and usage rows still connect.
- **D-E2, two targets added.** `NewConversation(profileId)` (untrusted `new_task`: the sheet with the server named and
  **Start**) and `ConfirmConnect(profileId, then)` (untrusted `usage_target` for a server that is not connected:
  "Connect to <server>?"). 07 §7.4 describes both behaviours, but §7.2's list has no target for them. `EntryTargetMapper`
  needs two rows.
- **D-E3, notice-only handoffs.** `EntryHandoff.target` is nullable: `Drop(notice)` and effect failures whose fallback
  has no target (share failed, monitored request gone) still reach the shell as a notice with nothing to navigate.
- **D-E4, trust is `origin: EntryOrigin`** on every request rather than a `trusted: Boolean`. `Link`, `SharedText`,
  `TilePreferences` are always Untrusted; `Internal` is always Internal.
- **D-E5, `EntryRouter` adds `resume()`, `linkFor(ticket)` and `forgetLink(ticket)`.** `resume` is the 07 §7.3 rule 2
  trigger. The ticket store keeps a not-saved server's link in memory for the `SessionAddress` sheet.
- **D-E6, the parser is common code.** `EntryIntentParser` + `IntentFields` hold every whitelist and bound, so they are
  table-tested on the desktop. `EntryIntentReader.read(intent, origin, clock)` returns a **list**, because an intent can
  carry several channels (Flutter's `LaunchActivityBridge` captured each one separately). The `MainActivity` sketch in
  07 §7.1 changes to `read(...).forEach(entryRouter::submit)`.
- **D-E7, `ShellWorld` is not built.** It reads profiles, diagnostics, settings, update and safe-mode state from engines
  that do not exist yet. The router reads `EntryWorld` through `EntryWorldSource` instead, and `ShellWorld` can be
  built over the same snapshot later.
- **D-E8, ports instead of engine edges.** `:engine:entry` depends on `:core:common` only. `:engine:analytics` is not in
  `ModuleRules.engineOrder`, and `:core:data`'s `MigrationGate`, profiles and connections are reached through
  `GateSignal`, `AppOpenSink`, `EntryWorldSource` and `EntryEffects`, bound by the app graph. No Metro
  `@Inject`/`@Contributes*` annotations, so nothing unbound enters the app graph.
- **D-E9, pinned-session ids must be safe identifiers.** Flutter accepted any non-empty trimmed text. 07 §7.1 says ids
  that pass must be `SafeId`s, and Flutter profile ids are timestamps, so real pins pass.
- **D-E10, `LinkState.Idle` on the active profile waits.** Treating it as final would drop every cold-start request
  before the start-up connection begins. "Disconnected" is already final because the active profile is then null
  (07 §7.3 rule 4).
- **Tests were written alongside the code, not red first** (AGENTS rule 11). The planted-fault run above stands in for
  the red step.

## Catalog requests

- `MODULE_OWNERS`: add `:engine:entry	S	coordinator	slice-2` (or the owner the coordinator names). Without it,
  `ModuleOwnersTest` fails, as it did for `:engine:analytics`.
- `:android:platform` build file (S2-PLATFORM): `implementation(project(":engine:entry"))` and
  `implementation(libs.androidx.core.ktx)` (already in the catalog; `IntentCompat`). Robolectric needs JDK 21 and,
  in this container, `robolectric.dependency.repo.url` pointed at a Central mirror.
- No new libraries.

## Open problems

1. **Not wired.** The coordinator needs to: bind `DefaultEntryRouter` and `AppBootstrap` in the app graph (world source
   over profiles, connections, `MigrationGate`, setup and schedules; effects over `ConnectionRegistry`, composer and
   monitors); call `AppBootstrap.onCreate()` from `OcApplication`; and add the 07 §7.1 capture to `MainActivity` and the
   new non-exported `.InternalEntryActivity` (with `EntryOrigin.Internal`). `:ui:shell` needs `EntryHandoffCollector` and
   mapper rows for D-E2/D-E3.
2. **`:android:platform` does not exist yet.** `EntryIntentReader.kt` is committed but compiles only once S2-PLATFORM
   adds the module. The verified smoke test (read and strip for link + shortcut, share, tile) is a good seed for its
   `EntryContractTest`. 07 §7.13's `quality/freeze/entry-extras.txt` does not exist yet either.
3. **Share vs. Astra M2.** 07 §7.7 lets an untrusted share create an empty session holding the unsent draft.
   `UntrustedArrivalTest` (07 §7.4) is scoped to launcher actions, so this is consistent, but any app can still cause
   an empty session on the active server. If M2 should cover shares too, the decider change is one line: send
   untrusted shares to `NewConversation` with a seeded draft.
4. **Share "Try again".** 07 §7.7's retry needs the text after a failure. The router drops it with the `ShareFailed`
   notice. Keeping it would need a held-text slot (memory only).
5. **`SwitchServerThen.then` carries unresolved ids.** The switch sheet resubmits `then` as `EntryRequest.Internal`,
   which does not run `ResolveRef`. A phone-agent conversation reached this way would need the sheet to resubmit the
   original request instead.
6. **`app_open` sink.** 16 §5.3 names `app_open`, but S1-ANALYTICS's schema has `app_start`. The `AppOpenSink` binding
   decides which event to send; analytics owns that choice.

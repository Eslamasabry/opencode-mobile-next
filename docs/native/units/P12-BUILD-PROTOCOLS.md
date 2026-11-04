VERIFIED: green

# P12-BUILD-PROTOCOLS: 52 protocol rows ported natively

Branch `claude/native-parity-push-build-gm3g9d`, based on `native/p12-integrate-2026-11-03` (`71f74de8`). The
brief named a different branch, but this cloud session may push only to its assigned `claude/…` branch.

Commits:

| Commit | What it holds |
|---|---|
| `c09c577c` | Row counter tool |
| `fcd58a3d` | `:protocol:oc2` |
| `f121f484` | `:protocol:paseo` |
| `be509462` | `:protocol:codex` (new module) |
| `a0e32235` | App wiring and the new `:feature:computer` page |
| this report | — |

## Rows proven: 52 of 52

A row counts as proven when at least one passing JUnit test's name starts with the row id.

**OpenCode 2 (`:protocol:oc2`), 18 rows:**
- B-CHAT-124
- B-PROTO-013, 016, 017, 021, 026, 030, 035, 036, 037, 038, 059, 060, 063, 083, 124, 125, 126

**Codex (`:protocol:codex`, new module), 18 rows:**
- B-AGENT-051, 052, 053, 054, 055, 056, 057, 058, 059, 060, 062, 063, 064, 065, 067, 068, 070, 220

**Paseo (`:protocol:paseo`), 16 rows:**
- B-AGENT-034, 035, 146, 151, 156, 157, 158, 159, 165, 167, 173, 174, 182, 189, 190, 191

The brief's `native/tool/parity/ledger.py` does not exist on this base; P12-CHAT-TRANSCRIPT noted the same. In its place,
`native/tool/parity/rows_proven.py` reads every `native/**/build/test-results/**/TEST-*.xml` and counts a row as proven
only when a passing, non-skipped test names it and no failing test does. After the runs below,
`python3 -I native/tool/parity/rows_proven.py <the 52 ids>` printed **`proven 52/52`**. The passing tests per row range
from 1 to 13.

## Journeys now in the app

1. **Codex servers connect.**
   - `ProtocolsWiring.kt` adds an OkHttp socket for the Codex app server:
     - the `Authorization: Bearer` token goes only to the host the endpoint rules checked;
     - no redirects and no proxy;
     - a 4 MiB message cap and a 20-second ping;
     - 401 or 403 means authentication failed.
   - It also registers `AppCodexGatewayFactory` (`BackendKind.Codex`) in the ConnectionRegistry, pinned to the
     profile's saved `codexDirectory`.
   - A saved Codex server, carried over from Flutter, now connects like any other: Servers → the server →
     Conversations → Chat. That gives send, stream, approve, abort, steer, and recovery after a lost link
     (B-AGENT-067).
   - Codex advertises no Project tab tools (B-AGENT-068).
2. **Paseo computers show their files, changes, worktrees and terminals.**
   - The Paseo gateway pair now carries a real `PaseoOperations`, with capability flags on for
     `fileBrowsing`, `terminal`, `sessionDiff`, `worktreeCreate`, `worktreeBrowsing` and `subagentSessions`.
   - Behind them:
     - `FileGateway`, for listing, reading and finding files;
     - `VcsGateway`, for the changes and the diff;
     - `HostGateway`, for worktrees;
     - `TerminalGateway`.
   - The app's existing screens gated on those flags now read a Paseo computer (B-AGENT-156 to 159). Sub-agents
     open as their own conversations (B-AGENT-165).
   - The chat feed reads the background-task book (B-AGENT-034, 035, 151):
     - "running" while a command is still going;
     - "failed" until the next prompt.
3. **Settings › Agents › a computer › Update and import** (new `:feature:computer`, `ComputerRoute(profile)`).
   - Each computer row in the Agents hub has a visible **Update and import** button (Arabic: التحديث والاستيراد).
   - **Update agents on {computer}**, B-AGENT-167:
     - The row shows the running helper version. Tapping it asks first (kit confirm: what happens, what pauses, what
       is kept).
     - While it runs, the page shows each phase: Starting, Downloading, Installing, Finishing.
     - It ends with one plain line: "Updated to X", "already up to date", "did not finish" (Details holds the failure
       kind) or "connection dropped … reconnecting".
     - The computer's own error text is never shown.
   - **Import from Claude Code**, B-AGENT-174:
     - Lists the project's Claude Code conversations newest first, each with its folder and a relative time, plus
       "N more conversations are already here."
     - Tapping a row imports that conversation and opens the chat. A refusal shows a plain failure and opens nothing.
     - Empty and failed lists use plain words with Try again.
   - Copy reuses the Flutter strings already in `:ui:words` in English and Arabic (`hostUpdate*`, `claudeImport*`).
     One new label, `computerToolsOpen`, was added in a new file `strings_build_protocols.xml` (EN and AR).

## Rows proven at the protocol level only (not yet reachable as UI)

| Row | What exists | What the integrator or another unit needs to do |
|---|---|---|
| B-PROTO-063 | `Oc2Gateway.historyMigrationStatus()` and `HistoryMigrationStatus.line` (Copying(done, total) / Failed) | A core facet (`HistoryMigrationGateway`) and a page; three sentences in `:ui:words` |
| B-CHAT-124, B-PROTO-125, 126 | `WebSourcesReview` (review, confirm, scope invalidation); `Oc2Gateway` implements `WebSearchGateway`; `websearch.updated` becomes `RefreshHint("websearch")` | A web-sources sheet in chat (chat unit); words for `AddProblem` / `WebSourceSelection.Problem` |
| B-PROTO-036 | Ticket handshake, plus `Oc2TerminalSocketOpener` / `Oc2Gateway.connectTerminalWith` | An OkHttp opener injected through `Oc2GatewayFactory`; `terminal` stays false until then. There is no OC2 terminal screen yet |
| B-PROTO-038 | `Oc2Errors.failureFor(status, body, credentialsSent)` reads tags, extras and the 503 boot state | `core:net` must hand a failed reply's body to the protocol; until then live tagged errors stay status-only |
| B-PROTO-021 | `health()` returns `Incompatible(ProtocolMismatch)` for unsupported replies | `:engine:connection` must treat that as "re-detect the server type" |
| B-PROTO-030 | Catalog and MCP scope facts | Integration OAuth (the native gateway has no integration code; `integrationCredentials` stays false) |
| B-PROTO-124 | Protocol half | Flutter expects `forms` on for v2, but the existing native test B-PROTO-024 asserts it off; left off. The "unattached controller" default belongs to `:engine:connection` |
| B-AGENT-146 | `PaseoGateway.agentFeatures` / `setAgentFeature` | No core facet or picker for agent switches yet |
| B-AGENT-173 | Payload-use lease on every send path | The phone helper graph must set `gateway.beforePayloadUse` / `afterPayloadUse` |
| B-AGENT-189–191 | `PaseoProviderUsageReader` and `PaseoUsageMonitor` (off by default, Paseo only, widget rows without free text) | `:engine:usage` should own the monitor: an opt-in store `oc.paseoUsage.<id>` (registered for profile deletion), a widget sink, a 5- or 15-minute cadence, and Settings › Usage widget |
| B-AGENT-051–054 | `CodexGateway.openAccountSession()` | No Codex account screen yet (sign-in, usage) |
| B-AGENT-055–059 | Handoff record builder and draft preparer (read-only) | No handoff screen yet; the code is meant to move to a handoff engine later |
| B-AGENT-060, 220 | `CodexGatewayFactory.checkConnection` and `probe` | The add-server form (`:feature:servers`, another unit) offers no Codex kind. Codex servers arrive only through the Flutter migration today |

## Deviations

- **Existing test changed.** `:protocol:oc2` `ComposerCommandsFilesTest` (B-NAV-051) now expects the stable dialect's
  `{"name":…}` for slash commands, which is the Flutter dialect's rename. The test name already said "posts name and
  text".
- **OC2 capability flags turned on, each backed by working code:**
  - `gitInit`, `worktreeCreate`, `shellSettings`, `developmentServices`
  - `globalEventStream`, `mcpRuntimeAdds`, `webSearch`
- **Codex differs from Flutter in places:**
  - a lost send returns `PromptAdmission.Uncertain` (the core contract) rather than throwing;
  - Stop with nothing running returns `NothingRunning`;
  - the "always" approval is refused.
- **Paseo background-task rules are duplicated** in `PaseoBackgroundTasks.kt`, because `:protocol:paseo` cannot depend
  on `:engine:transcript`.
- **Agents hub edit.** `:feature:agents` (owner S3-AGENTSUI) got the smallest possible edit:
  - one default `openComputer(id)` on `AgentsActions`;
  - one presenter callback;
  - one button.

  Existing tests are unchanged; a new `AgentsComputerReachTest` covers the button.
- **`MODULE_OWNERS`.** Added `:protocol:codex` and `:feature:computer` (owner build-protocols). The brief allows a new
  module to register itself there, although the file header reserves edits for the coordinator.

## Commands and results (this container, JDK 21, Android SDK platforms 36 and 37.1, build-tools 37.0.0)

Setup:
- Maven Central answered 429 to Gradle here. A local, unversioned `~/.gradle/init.d/central-mirror.init.gradle` sends
  Central to Google's mirror. Nothing in the repo changed.
- The git-ignored sherpa-onnx AAR was fetched with `native/tools/vendor/fetch.sh`; its SHA-256 matched the pin.

| Command | Result |
|---|---|
| `./gradlew :protocol:oc2:desktopTest` (rerun after the last test edit) | 182 tests, 0 failed, 1 skipped (the pre-existing live-server smoke test) |
| `./gradlew :protocol:paseo:desktopTest` | 322 tests, 0 failed |
| `./gradlew :protocol:codex:desktopTest` (rerun after the last test edit) | 80 tests, 0 failed |
| `./gradlew :feature:computer:desktopTest` | 13 tests, 0 failed |
| `./gradlew :feature:agents:desktopTest` | 47 tests, 0 failed |
| `./gradlew :ui:words:desktopTest` | passed |
| `./gradlew :quality:test` | 64 tests, 0 failed (module owners and architecture rules accept both new modules) |
| `./gradlew :androidApp:testPreviewDebugUnitTest` | 50 tests, 0 failed |
| `./gradlew :androidApp:assemblePreviewDebug` | BUILD SUCCESSFUL; `androidApp-preview-debug.apk` (90 MB) |
| `python3 -I native/tool/parity/rows_proven.py <52 ids>` | `proven 52/52` |
| `native/tool/check_commits.sh 71f74de8 HEAD` | OK, 5 commits |
| `native/tool/scan_push.sh 71f74de8 HEAD` | clean |

Not run as a gate: `:quality:secTest`.
- It fails on the pre-existing K-SEC-10 rule (URL literals in feature/git, feature/servers, engine/*) and K-SEC-4
  rule (engine/runtime, engine/attention, …).
- None of its findings are in files this unit changed.

Two private-address literals in new tests were replaced before the final push: a documentation-range address, and an
address built at runtime for the private-address rule. The branch was rebuilt into the five commits above, so no
commit carries them.

## Needs a device, a real server or later work

- **Device.** On an emulator or phone:
  - a Codex profile (migrated from Flutter) connecting and chatting;
  - a Paseo computer's Files, Changes, Worktrees and Terminal screens;
  - Update and import against a real Paseo daemon.
  None of these were exercised here; there is no device or daemon in this container.
- **Real servers.** The live OC2 checks for the new dialect routes (stable 2.1 line) are not run here.
  `LiveOc2ServerSmokeTest` stays opt-in.

## P12 integration repair (2026-11-04)

Merged integration head `6973eefcd33aa5fe019280bc3b898ddce04f550b` into
`p12x/protocols-abddca7`, starting at `abddca71ec636915871cec590832a5783bd6165f`.
The only conflict was `native/MODULE_OWNERS`: retained both protocol/computer rows
and all five integrations/Plans rows. The automatic Android dependency merge
retains every project dependency from both parents. No Kotlin source or tests
needed changes.

The fresh worktree lacked the gitignored sherpa-onnx AAR. The first compile run
failed eight Android tasks because that dependency was missing; all desktop
source and test-source compilation completed. Ran `native/tools/vendor/fetch.sh`
and verified the artifact against its committed SHA-256, then reran the complete
requested command under the shared PC build lock:

```bash
cd native
export JAVA_HOME=/home/eslam/Storage2/tools/jdk21
export ANDROID_HOME="$HOME/Android/Sdk"
../tool/qa/machine_lock.sh build -- nice -n 5 ./gradlew --console=plain --continue \
  -Dorg.gradle.workers.max=2 \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  :androidApp:assemblePreviewDebug compileKotlinDesktop compileTestKotlinDesktop
```

Final result: **BUILD SUCCESSFUL**, 29 seconds; **3/3 requested gates passed**,
1,215 actionable tasks (145 executed, 9 from cache, 1,061 up-to-date), zero failed
tasks and zero Kotlin errors. Desktop source compilation covered 68 modules;
desktop test-source compilation covered 65 modules, with three additional
`NO-SOURCE` tasks. Existing compiler/configuration warnings remain.

Local logs: `/tmp/p12-protocols-compile-20261104.log` (initial dependency failure)
and `/tmp/p12-protocols-compile-final-20261104.log` (successful final gate).
Behavior tests were not executed, as requested; the integrator owns that gate.
No push, CI run, release, or device validation was performed.

The merge message passes `native/tool/check_commits.sh --message-file ... --merge`,
and the ownership/dependency/report diff passes whitespace checks. Checking the
entire imported history additionally reports pre-existing commit-message violations
in six integration commits; the imported `docs/native/inventory/rest.md` also has
existing Markdown trailing whitespace. Those other units' history/content were
preserved. These findings do not affect the requested compile gate.

## P12 integration repair against ca65eb2 (2026-11-04)

Merged `ca65eb2f0a89a6560c597f9ea30895189d511555` into
`p12x/protocols-e59de09`, starting at
`e59de09007222d912b4b7f4e0e0a8fc73b7b32bd`.

- Resolved the sole textual conflict, `native/MODULE_OWNERS`, by retaining
  both protocol/computer entries and all four incoming diagnostics/background
  entries. Verified all 89 module paths are unique and both parents' entries
  survive.
- Verified the automatically merged `native/androidApp/build.gradle.kts`
  retains every project dependency from both parents.
- Reconciled the automatic OC2 gateway merge: `health()` keeps this unit's
  `Incompatible(ProtocolMismatch)` re-detection signal, while `probe()` also
  recognizes that signal before checking for a legacy server. Without the
  extra condition, the integration's existing B-PROTO-094 malformed-health
  case could no longer reach legacy recognition. The existing tests and their
  assertions are unchanged. Applied ktfmt to the changed condition; retained
  surrounding formatting to avoid unrelated churn.

The first compile attempt completed both desktop compilation groups with zero
Kotlin errors, but eight Android tasks failed because this fresh worktree lacked
its gitignored sherpa-onnx AAR. Restored it with `native/tools/vendor/fetch.sh`,
which verified the committed SHA-256, then reran the entire requested command.

```bash
cd native
export JAVA_HOME=/home/eslam/Storage2/tools/jdk21
export ANDROID_HOME="$HOME/Android/Sdk"
../tool/qa/machine_lock.sh build -- nice -n 5 ./gradlew --console=plain --continue \
  -Dorg.gradle.workers.max=2 \
  -Dorg.gradle.daemon.idletimeout=300000 \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  :androidApp:assemblePreviewDebug compileKotlinDesktop compileTestKotlinDesktop
```

**Final result: BUILD SUCCESSFUL in 46 seconds; 3/3 requested gates passed.**
1,268 actionable tasks: 149 executed, 11 from cache, 1,108 up-to-date.
Zero failed tasks and zero Kotlin errors. Desktop source compilation covered
70 modules; desktop test-source compilation covered 70 tasks (68 with source,
two `NO-SOURCE`). Existing compiler/configuration warnings remain.

Compiled candidate tree: `15e09dd0e1805dc9f8214cb72062fe89550a5e15`
(before this documentation-only verification append).
Logs: `/tmp/p12-protocols-ca65eb2-compile-20261104.log` (initial dependency
failure) and `/tmp/p12-protocols-ca65eb2-compile-final-20261104.log` (green).

Tests executed: **0**, per the owner's compile-only migration boundary. The
Flutter preflight also executes tests and was therefore not run. The resolved
ownership, Android dependency, OC2 source and verification-report diffs pass
whitespace checks; the imported `docs/native/inventory/ui-c.md` retains two
pre-existing whitespace findings. No other unit's content was changed to remove
those findings. No push, CI run, release or device validation was performed.

The new merge message passes `native/tool/check_commits.sh --message-file ...
--merge`. A post-merge range scan (`HEAD^1..HEAD`, 118 commits) also traverses
incoming integration history and reports pre-existing subject/body/skip-CI
violations there. Those imported commits were preserved without rewriting.

## P12 integration repair against 1999c91 (2026-11-04)

Merged `1999c91f0b9bc2040db70d71f2981b1386fed0ee` into
`p12x/protocols-01a99f9`, starting at
`01a99f9cc237f50518681523afb1de9cb5dd8829`.

Resolved the sole conflict in `native/MODULE_OWNERS` by keeping Codex, Computer
and Development services. Verified both parents' ownership entries survive
with 90 unique module paths, and the Android app retains the union of both
parents' project dependencies (74 paths). No Kotlin source or tests needed
changes. Restored the missing gitignored sherpa-onnx AAR using
`native/tools/vendor/fetch.sh`; its committed SHA-256 matched.

Ran the requested compile-only command under the shared PC build lock:

```bash
cd native
export JAVA_HOME=/home/eslam/Storage2/tools/jdk21
export ANDROID_HOME="$HOME/Android/Sdk"
../tool/qa/machine_lock.sh build -- nice -n 5 ./gradlew --console=plain --continue \
  -Dorg.gradle.workers.max=2 \
  -Dorg.gradle.daemon.idletimeout=300000 \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  :androidApp:assemblePreviewDebug compileKotlinDesktop compileTestKotlinDesktop
```

**BUILD SUCCESSFUL in 5m 9s; 3/3 requested gates passed.**
1,285 actionable tasks: 953 executed, 332 from cache. Zero failed tasks and
zero Kotlin errors; existing compiler/configuration warnings remain.
Desktop source compilation covered 71 modules. Desktop test-source compilation
covered 71 tasks (69 with source, two `NO-SOURCE`). Tests executed: **0**, per
the owner's migration boundary; the test-running Flutter preflight was omitted.

Compiled tree: `7467c184aa6cf72978eff5b6938ea560b33b2637`, unchanged through
completion, before this documentation-only append. Local build log:
`/tmp/p12-protocols-1999c91-compile-20261104.log`.

The merge message and resolved ownership/dependency/report whitespace checks
pass. The incoming runtime file `ServerRestartBudget.kt` retains its existing
extra blank line at EOF. No push, CI run, release or device validation performed.

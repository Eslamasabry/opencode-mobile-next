VERIFIED: green

# P12-BUILD-AGENTS — 68 agent-area Flutter behaviours ported natively (parity push, 2026-11-03)

Branch `claude/native-parity-push-build-eqicbz`, base `native/p12-integrate-2026-11-03` at `71f74de8`. Owning module of
every row: `:engine:agents` (`native/catalog/scope.jsonl`); no new module, so no `MODULE_OWNERS` row.

| State (rule 8) | What |
|---|---|
| Implemented | 67 rows ported as `:engine:agents` code + row-named tests; two journeys wired into the app (below) |
| Verified here | the commands in §5, all green, on this branch head |
| Committed / pushed | yes, to the branch above. No PR, no CI run, no signing, no release |
| Not verified | no device or emulator run; no live server |

## 1. Journeys now working in the Android app

1. **Continue on computer** (B-AGENT-227, B-AGENT-228, B-SEC-159, B-SEC-160). Path: Chat → ⋯ → **Continue on computer**
   (after "Continue with…"). The sheet shows the whole command `cd '<folder>' && opencode2 --session '<id>'`, built
   from the folder the server reports when the sheet opens, plus **Copy command**. Copying only writes the clipboard.
   A Details fold names the CLI versions the syntax was checked against. A cloud workspace, a missing folder or an
   unsafe id gets one plain sentence instead of a guessed command. All words already existed in `:ui:words`
   (EN + AR).
   - The menu row is gated on `ServerCapabilities.cliSessionResume`. OC2 now reports `true`, as Flutter's api2 does;
     Paseo still reports `false`.
2. **Recent models survive a restart** (B-STATE-032). The composer model picker's Recent group used to live in memory
   only (5 picks, one app run). It is now saved per server, newest first, 8 picks, in the Flutter
   `oc.modelLibrary.<profileId>` record.
   - Favorites in that record are kept.
   - Before the migration gate opens, the store is asked again on the next read.
   - The binding is `SavedRecentModels` in the new `graph/BuildAgentsWiring.kt`.

Every other row is engine logic with no screen yet (§3).

## 2. Rows proven: 67 of 68

`native/tool/parity/ledger.py` does not exist on this base: there is no `native/tool/parity/` folder, and the file is in
no branch I could fetch. I applied the ledger's rule with my own script instead: a row counts only when a JUnit
test whose name starts with the row id passes, with no failing or skipped test for that id. It ran over every
`build/test-results` folder after the §5 runs. **Result: 67 of 68 proven.** The integrator should re-run the real
`ledger.py` once it lands.

Proven: B-AGENT-002 003 005 012 014 015 016 017 018 019 020 021 029 050 121 122 123 126 127 128 147 227 228 316,
B-BUILD-006, B-CHAT-001 002 003, B-LINUX-001 002 003 004 053 114 115 116 117 119 120 121 122 123 127 128 138, B-NAV-033
035 036, B-PROTO-062 097 098 099 102, B-SEC-002 003 004 005 103 111 159 160 161, B-STATE-001 032 111 112 158.

Four of these were already proven by existing `:android:agenthost` tests, which now ran green here: B-AGENT-050,
B-AGENT-316, B-LINUX-115 and B-LINUX-117.

### Proven, but not every fact is ported

- **B-LINUX-116.** Flutter cases 1–3 are ported (the block replaces only its own part and keeps the person's text).
  Case 4, "both servers write it before start", is not: the server-start scripts live in `:android:linux`, which an
  engine may not depend on. The block and its script are in `engine/agents/phonecontext`. That module's owner calls
  them before start.
- **B-BUILD-006.** Only "the inventory file is the live catalog" is ported (`guard/CatalogInventoryTest`). The other
  checks (ledgers, server-capability switches, tools-context and team-engine) cover Flutter UI and have no native
  subject yet. The comparison skips `limitation` and `resumeReason`: Dart stores English text there, native stores
  word keys.
- **B-LINUX-127.**
  - Ported: BD13 helper evidence, BA4 arm64 certification and BA1 missing bridge (`phoneagents/PhoneAgentsSession*`).
  - Not ported: "qualified cards skip asks… disable revokes". Its owner is `:engine:cards` (`ShowCardPermission`),
    which already tests allow-once and disable-revokes (B-CHAT-049). Two facts are untested anywhere: the feed and
    the chat use separate sockets, and auto-approval stays off.
- **B-LINUX-128.**
  - BA5 protocol switch, deletion close order and the sign-in reset close are ported on the agents side.
  - The ordering "before ProfileStore" is owned by `:engine:profiles`' `ProfileDeletion` (B-AGENT-193 covers it
    there).
  - `:engine:profiles`' `SignInReset` does not call the new `PhoneAgentsSession.close(SignInReset)` yet.
- **B-LINUX-138.** Flutter asserts a second probe on a second list refresh. Native `AgentAuth.refresh` reuses a recent
  answer, so the test uses `recheck` for that step. There is no native refresh-the-list loop to port onto.
- **B-AGENT-147.** The identity rules and the dispatch fence after the provider check are ported. The real Paseo
  traffic (create agent, timeline, a second gateway on the same daemon) belongs to `:protocol:paseo`. Here it runs
  against a fake daemon map.

### Not done

- **B-BUILD-003** (every `capabilities.json` enable flow resolves on Android). Native has no enable-flow registry
  (`KitCapabilities` / `KitEnableFlows`); `grep enableFlow|canEnable` finds nothing in `native/`. It belongs to the
  kit owner.

## 3. What was ported, by package (`native/engine/agents/src/*/kotlin/io/github/eslamasabry/oc/engine/agents/`)

| Package | Rows | Notes |
|---|---|---|
| `resume` | AGENT-227/228, SEC-159/160 | `SessionResumeCommand` (POSIX quoting; refuses ids that could read as flags, and control characters) |
| `models` | PROTO-062, SEC-103, STATE-032, NAV-033/035/036, STATE-111/112, PROTO-102 | refs, library (recent 8 + favorites), cycling, recommendations, title words, per-session choices, OC2 selection projection; stores `oc.modelLibrary.<id>`, `oc.sessionModels.<id>` |
| `workspace` | STATE-158 | new project folder name rule |
| `errors` | AGENT-012 | cause classifier + EN/AR words table (`AgentErrorWords.kt`) |
| `tools` | AGENT-029 | tool adapter registry contract against `AgentCatalog.builtIn` |
| `handoff` | AGENT-014–021, 147, SEC-003/004/111, STATE-001 | record builder (reuses `:core:security` `Redactor`), history reader, links store `oc.agentHandoffs.<id>`, `HandoffEngine` (StateFlow + Outcome), record-file rules |
| `sessionimport` | PROTO-097/098/099, SEC-161 | export parsing, review snapshot, `POST /api/session/import` over a `RouteTransport` port |
| `context` | CHAT-001/002/003 | active context fetch/mapper, inspector projection, search preview |
| `account` | AGENT-002/003 | account panel controller |
| `probe` | AGENT-005 | sign-in status scripts, byte-identical to the agenthost fixtures and their pinned SHA-256 |
| `phoneagents` | LINUX-138/127/128 | row sign-in read; session close paths and helper evidence |
| `guard` | LINUX-001, SEC-002, BUILD-006 | install space guard, artifact safety, catalog inventory |
| `runtime` | AGENT-121–128 | local agent runtime (Termux), status parsing, dispatch/poll |
| `scripts` | LINUX-002/003/004/053/114/119–123, SEC-005 | removal, Claude install, phone agent install/check/rollback, locked Paseo install; `desktopTest` runs them with `/bin/sh` / `bash` against temp roots |
| `phonecontext` | LINUX-116 | instruction block |

## 4. Deviations and what the integrator must do

- **Edits outside `:engine:agents`, kept small:**
  - `feature/chat`: the header menu, sheet and presenter; `HeaderFacts.workspaceId`; `RecentModels` gained an
    optional backing; a new `ChatDraftBindings.recentModelsBackings` multibinding; one dependency line on
    `:engine:agents`. One existing test list gained `ContinueOnComputer`, the Flutter menu order; no assertion was
    removed.
  - `protocol/oc2` `Oc2Gateway.Capabilities`: `cliSessionResume = true`, one line.
  - New file `androidApp/.../graph/BuildAgentsWiring.kt`.
- **Words in the engine.** Rows whose Flutter tests assert wording carry small EN+AR tables in the engine:
  `models/ServerPlaceWords.kt`, `errors/AgentErrorWords.kt`, `workspace/ProjectFolderNameWords.kt` (EN only; Flutter has
  no Arabic for these) and `handoff/HandoffWords.kt`. They should move to `:ui:words` once screens use them.
- **ProfileScope.** The new per-profile keys (`oc.modelLibrary.`, `oc.sessionModels.`, `oc.agentHandoffs.`) already
  follow `oc.<what>.<profileId>`, so the deletion sweep removes them by name (`ProfileKeyRule`). They are not yet
  registered as `KeyShape` ledger rows; that registration belongs to the profiles owner.
- **Choices the integrator may want to review:**
  - `LocalAgentRuntime` returns its own `LocalAgentResult`, because the script's failure kinds have no flat
    `Failure`.
  - The Paseo install takes a `PaseoLockCodec` (gzip, SHA-256) that the app must bind with `java.util.zip` and
    `MessageDigest`.
  - Generators throw `IllegalArgumentException` where Dart throws.
  - `HandoffEngine` reports `StaleScope` when a profile closes before any remote step; Flutter reports
    `fileWriteFailed` there.
- **No screens yet for:** handoff to another agent, session import, the active-context inspector, the agent account
  panel and model recommendations. Each needs a gateway route or facet that does not exist natively. Examples:
  `ServerCapabilities.sessionImportExport` is false; there is no ActiveContext facet; the handoff ports need gateway
  adapters. These belong to their protocol and feature owners. The engine ports are ready for them.
- **Container fixes, not committed:**
  - `~/.gradle/init.d/central-mirror.gradle.kts`: Maven Central answered 429 through the proxy; Google's mirror of
    Central was used.
  - The gitignored vendored `sherpa-onnx-android-2.0.1.aar` was missing. It was fetched from the k2-fsa v2.0.1
    release and matched `native/third_party/SHA256SUMS`.

## 5. Commands and results (branch head, `native/`, JDK 21, `ANDROID_HOME=/opt/android-sdk`)

`./gradlew :engine:agents:desktopTest :feature:chat:desktopTest :protocol:oc2:desktopTest :androidApp:assemblePreviewDebug :android:agenthost:testDebugUnitTest :quality:test --continue` → **BUILD SUCCESSFUL**

| Task | Tests | Failed | Skipped |
|---|---|---|---|
| `:engine:agents:desktopTest` | 523 | 0 | 0 |
| `:feature:chat:desktopTest` (includes the new Compose screen test by visible text) | 270 | 0 | 0 |
| `:protocol:oc2:desktopTest` | 120 | 0 | 1 (the opt-in live-server test) |
| `:android:agenthost:testDebugUnitTest` | 137 | 0 | 0 |
| `:quality:test` | 64 | 0 | 0 |
| `:androidApp:assemblePreviewDebug` | `androidApp-preview-debug.apk` built | — | — |

Also: `native/tool/check_commits.sh 71f74de8 HEAD` OK, and `native/tool/scan_push.sh 71f74de8 HEAD` clean. One public
literal IP (`1.2.3.4:443`) remains, inside an error-text test fixture.

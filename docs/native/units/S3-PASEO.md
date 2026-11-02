# Unit S3-PASEO: `:protocol:paseo`

Date: 2026-11-01. Branch: `native/paseo-2026-11-01`, made from `claude/paseo-websocket-transport-saixgy`
(= `native/m0-2026-11-01` at `6b9a2b21`). Write set: `native/protocol/paseo/**` (new module), plus this report.

Inputs:
- `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*` (S0-CORE, S1-NET, S1-OC2 in particular);
- plan 01 (binding), plan 15 row S3-PASEO, plan 04 §5 (WebSockets), §8 (Paseo), WP-04.10, §11;
- `map/agents-backends.md`;
- the Flutter client `lib/paseo/**` (9.8k lines) and its tests `test/paseo_*_test.dart` (12.4k lines), plus the
  fixture `test/fixtures/paseo/native_questions_0_9_2.json`;
- FX-PASEO (`native/e2e/fixtures/servers/fx_paseo.py`).

**Finish line (plan 15):** "The Paseo transport, gateway, mappers and chat feed pass the Paseo live proof and the
FX-PASEO fixtures." **Non-goal:** Paseo P1 extras (S5-PASEOP1).

**Result.** The finish line is met for the Kotlin side:
- The transport, gateway, mappers, requests (permissions, questions, ACP host agents), chat feed and helper-wide work
  inventory are ported.
- 228 desktop tests pass. The module also compiles for Android (`compileAndroidMain`).
- The live proof passes all 11 steps against FX-PASEO in both modes:
  - **real**: the real `@getpaseo/cli@0.11.2` daemon, driving the fixture's fake Claude Code;
  - **stub**: the fixture's protocol stub.
  The steps cover hello, the version pin, ready runtimes, the scoped list, a full chat round trip
  (draft → create → streamed turn → history → archive), a wrong password and a reconnect after a dropped socket.

**Not done:**
- **The production WebSocket.** `:core:net` has no `WsSession` yet (S1-NET open problem 1, P-N1). The module talks
  to a `PaseoSocketFactory` seam; the live proof uses a test-only JDK socket.
- **The emulator half of the live proof** (E2E-30 through the in-app helper).
- **The P1-extra rows** listed in §2.3.

**Shipping state:** implemented, verified locally (desktop tests, Android compile, live proof against the real
0.11.2 daemon on loopback), committed and pushed to `native/paseo-2026-11-01`. There is no PR, no CI run, no release
and no emulator run.

---

## 1. What was built

Module `:protocol:paseo` (`oc.kmp.library`, explicit API; `api(project(":core:domain"))` only).
Package `io.github.eslamasabry.oc.protocol.paseo`. No platform source sets (ADR §7.2): code is in `commonMain`, tests
are in `commonTest` and `desktopTest`. Settings discover the module, so `settings.gradle.kts` was not edited.

| Area | Files (`src/commonMain/.../paseo/`) | What it does |
|---|---|---|
| Pins | `PaseoTestedVersions.kt` | Ported verbatim from `tested_versions.dart`. Daemon: 0.9.2, 0.11.2, 0.12.0, 0.13.2, 0.14.0 (phone pin 0.11.2). Claude Code: 2.1.283, 2.1.294, 2.1.316. |
| Failures | `PaseoFailure.kt` | `PaseoFailureKind` (13 kinds). `PaseoException` is internal to the module. `toFailure()` maps it to the core `Failure` (plan 04 §8 table), and `paseoAttempt {}` is the module boundary. Daemon text is tidied, capped at 240 characters and passed through the `Redactor`, and it reaches only `details`. |
| Endpoint | `PaseoEndpoint.kt` | `ws`/`wss` only, on loopback or the tailnet (`AddressRules`); the path becomes `/ws`; credentials, query and fragment are refused. Also the profile validators (`PaseoUrlProblem`, normalizer, password rule). |
| Wire | `PaseoJson.kt` | Ports of the Dart readers: strict `paseoObject`/`String`/`List`, lenient `paseoText`/`paseoMillis`, and `paseoReply`. |
| Transport | `transport/PaseoSocket.kt`, `transport/PaseoTransport.kt` | See the transport list below. |
| Gateway | `gateway/PaseoGateway.kt`, `gateway/PaseoStream.kt` | See the gateway list below. |
| Factory, probe | `gateway/PaseoGatewayFactory.kt` | `GatewayFactory`: `probe` validates and then only says hello; `open` does no I/O; the password comes from `CredentialReader` at connect time. `probePaseoConnection` is the pairing probe (B-PROTO-070). |
| Capabilities | `PaseoCapabilities.kt` | The Dart `paseoServerCapabilities`, as the 49 flags that differ from the defaults. The P1-extra flags are off (D3). |
| Mappers (slice A) | `mappers/PaseoMappers.kt`, `mappers/PaseoToolMappers.kt` | `mappers.dart`: agent → session, stale-working status, titles, tool names, status, merge, input, output, metadata and declined calls, background-task notices, timeline → messages (error rows, sign-in rows, todo, compaction, notices), pictures. |
| Requests (slice C) | `requests/PaseoRequestBook.kt`, `PaseoPermissionMapper.kt`, `PaseoHostAgents.kt` | Covers `permissions.dart`, `questions.dart`, `host_agent_providers.dart` and `paseoPermission`. Native permissions use revisions, snapshots and stale-snapshot fencing. Questions and plans are typed questions, never permissions. ACP permissions offer exactly the actions the agent offered. Also the ACP catalog, sign-in classification and continuation. |
| Feed and work (slice D) | `feed/*.kt`, `work/*.kt` | `chat_feed_source.dart` through the `PaseoFeedPort` seam, which the gateway implements in `gateway/PaseoGatewayFeedPort.kt` (with `copies.dart`). `idle_work.dart` becomes `PaseoWorkInventory`, and `idle_guard.dart` becomes `PaseoIdleGuard`. |
| Live proof (test only) | `src/desktopTest/.../live/JdkPaseoSocket.kt`, `PaseoLiveProofTest.kt` | A `java.net.http` socket: no extensions offered, no redirects, `NO_PROXY`, an 8 MiB message cap. Plus the 11-step proof. |

**Transport** (`PaseoTransport`, coroutines on one serial lane):
- the `hello` frame, with exactly the four claimed capabilities and never `explicit_event_subscriptions`;
- `server_info` within 8 s, recording the version and `workspaceFileEditing`;
- an application `ping` every 20 s;
- connection epochs;
- request ids of the form `m<epoch>_<n>`;
- at most 64 pending requests;
- on a timeout or a lost socket, a read fails as `Disconnected` and a mutation as `DeliveryUnknown`;
- `rpc_error` handling, with the password masked in the reason;
- `.progress` frames, and answers that arrive as pushes;
- `hello.rejected` and the close codes 4401 and 4003;
- frames of 64 K characters or more are decoded on the decode dispatcher, still in arrival order;
- `requestThen`, the Dart `holdingMessages`;
- the bounded-history connection (1 MiB frames, 10 s, tested versions only);
- binary terminal frames.

**Gateway** (`PaseoGateway`):
- **Interfaces:** `ServerGateway`, `SessionSelectionGateway`, `CorrelatedPromptGateway` and `MidTurnPromptGateway`, all
  on `GatewayEvent`.
- **Sessions:** this folder's unarchived agents, with drafts first; a draft stays local until its first prompt; the
  app id is kept apart from the daemon id; pushes that race ahead of the create answer are replayed.
- **Turn state:** the stream owns busy state; a record's running state counts for 3 minutes only.
- **Prompts:** steer, uncertain sends, thinking, mode and model selection, slash commands as `/name args`.
- **Providers:** waits while models are still loading; turns modes into agents.
- **Events:** history primes the live stream; the stream maps to events (B-CHAT-070/071).
- **Delete** archives the agent.
- **Reconnect** follows plan 04 §5.5:
  - retry delays of 1 s doubling to 16 s, with `Disconnected` after 6 tries;
  - a sign-in failure or a refused host is terminal (`Stopped`);
  - "connected" is announced after the recovery list or 3 s, whichever comes first;
  - turn state resets only on a new epoch.
- **Phone helper:** `trackLocalWork` wires the work inventory.

The work was split, as AGENTS rule 4 asks. The signatures were frozen first (`a6dae19e`). Three forked agents then
worked in their own git worktrees: slice A (mappers), slice C (requests) and slice D (feed and work). The coordinator
wrote the transport, the gateway, the factory and the live proof, merged each slice and wired it in. The merges are
in the history.

## 2. Catalog rows and tests

Every test is named `B-AREA-NNN what it proves`, or `WP-04-10 …` for plan items that have no row.

### 2.1 Covered (tests per row)

| Row | Tests | Where |
|---|---|---|
| B-PROTO-070 | 2 | `PaseoProbeAndFailureTest`: bad input never opens a socket; a verified daemon names its runtimes; the gateway is closed. |
| B-PROTO-071 | 10 | `PaseoHandshakeTest`: hello rejections, the close code alone, claimed capabilities and the password, unknown frames, version pins, an 8 s hello timeout, a refused upgrade. |
| B-SEC-113 | 3 | `PaseoEndpointTest`. |
| B-SEC-109 | 11 | `PaseoHostAgentsTest`. |
| B-SEC-110 | 2 | Raw agent errors never reach message text (mapper half); public endpoints are refused (host half). |
| B-SEC-114 | 15 | `PaseoPermissionsTest` (book) + `PaseoGatewayPermissionsTest` (end to end through the gateway and daemon). |
| B-SEC-115 | 12 | `PaseoPermissionsTest` + a gateway test (a dropped link retires the request without resending). |
| B-CHAT-070 | 16 | Streaming (gateway) and history mapping (mappers). |
| B-CHAT-071 | 3 | Fixed copy for a failed turn; no second error; a refused request is a fixed failure. |
| B-CHAT-072 | 22 | `PaseoQuestionsTest` + `NativeQuestionFixtureTest`, the parity of the inlined fixture with `test/fixtures/paseo/native_questions_0_9_2.json`. |
| B-AGENT-142, 143, 145 | 4, 4, 3 | `PaseoHostAgentsTest`. |
| B-AGENT-144 | 1 | The continuation rule (`paseoHostAgentContinuation`); the gateway exposes `hostAgentContinuation`. |
| B-AGENT-148 | 1 | The Paseo half; the OpenCode half is an OC2 parsing concern. |
| B-AGENT-149 | 1 | `PaseoGatewayFeedTest`: an idle older copy is archived once, a busy one is left alone, and a failed archive keeps the list. |
| B-AGENT-150 | 3 | `PaseoTransportTest`: off-lane decoding keeps order and equals inline decoding; malformed JSON drops the link; a long history is read while later frames wait. |
| B-AGENT-153, 154 | 11, 7 | `PaseoChatFeedTest` (fake port) + `PaseoGatewayFeedTest` (real gateway). |
| B-AGENT-155 | 1 | The exact correlated id after the fence; a refused fence sends nothing; drafts are refused. |
| B-AGENT-160, 161 | 1, 1 | Failure mapping; the first-message reason appears in Details only. |
| B-AGENT-162, 163, 164 | 7, 4, 4 | `PaseoGatewayTest` and the mapper tests. |
| B-AGENT-168, 169 | 20, 9 | `PaseoWorkInventoryTest`, `PaseoIdleGuardTest`, `PaseoGatewayLocalWorkTest`. |
| B-AGENT-170, 171 | 5, 3 | Mappers, plus a gateway refusal before anything is sent. |
| B-AGENT-172 | 1 | Partial: draft thinking reaches create; a follow-up applies thinking before the message; null resets to the default; a refusal stops the send. |
| B-AGENT-175, 176, 177, 179 | 1 each | Pure rules. |
| B-AGENT-178 | 1 | Mapper half only: a loaded snapshot never expires. |
| B-AGENT-180, 181 | 4, 4 | Titles (feed and gateway `savedTitle`). |
| B-AGENT-183…187 | 2, 2, 5, 3, 3 | Mappers. |
| B-AGENT-188 | 1 | A lost send answer reads as `Uncertain`, and the chat will not send again until it is reloaded. |
| B-AGENT-066 (plan 04 §5.4) | 2 | Read vs mutation timeout; a socket lost after the write. |
| WP-04-10 | 10 | Request bookkeeping, 64 pending, epochs, progress, binary frames, keepalive and close, bounded history, draft selection to create, mid-turn kind, and the live proof. |

### 2.2 Live proof (`PaseoLiveProofTest`, desktopTest)

The test is skipped (it passes trivially) unless `OC_PASEO_URL` is set. Its results:

| Step | real 0.11.2 + fake Claude Code | stub |
|---|---|---|
| 1 probe verifies the daemon | PASS (version 0.11.2, runtimes Claude Code, Pi) | PASS |
| 2 version is a tested pin | PASS | PASS |
| 3 ready runtimes listed | PASS | PASS |
| 4 listening connects and reads the folder's agents | PASS | PASS |
| 5 chat defaults name a model | PASS (the fixture's default model) | PASS |
| 6 the first prompt creates the agent | PASS | PASS |
| 7 the turn streams and ends | PASS (9 events, Succeeded) | PASS (14 events, Succeeded) |
| 8 history has the user prompt and a reply | PASS (3 messages) | PASS (4 messages) |
| 9 delete archives the agent | PASS | PASS |
| 10 a wrong password reads as sign-in needed | PASS (`SignInNeeded(PasswordRejected)`) | PASS |
| 11 a dropped socket reconnects (FX ctl `drop-socket`) | PASS (connects = 2) | PASS |

### 2.3 Not covered (rows in the unit's list)

| Rows | Why |
|---|---|
| B-AGENT-147, B-SEC-111 (handoff); 151, 187 envelope runtime (background tasks); 156–159 (files, changes, worktrees, terminal); 165 (sub-agents); 166 (gen-UI recovery); 167 (host update); 174 (import); 189–191, B-SEC-116 (usage) | Paseo P1 extras, S5-PASEOP1 (plan 15 non-goal; plan 15 lists the same features under S5-PASEOP1). Their capability flags are off (D3). |
| B-AGENT-013, 146 (agent feature switches, options before a conversation) | `agent_features.dart` is not ported. Only the draft model and thinking reaching create are tested (under WP-04-10 and B-AGENT-172). |
| B-AGENT-152 (browser launch), 173 (payload-use admission) | Phone-host owner hooks (`BrowserClaudeLaunchRegistry`, the process budget) belong to S3-AGENTHOST and S3-AGENTSE. |
| B-AGENT-182 (`provider/model` matched to the list) | `_canonicalModelId` is not ported. |
| The B-AGENT-178 gateway half, the B-AGENT-144 "explicit new chat with acknowledgement" flow, the B-AGENT-143 provider-cache fencing per project | Partial; see D8. |

## 3. Commands and results

All commands were run from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` and
`ANDROID_HOME=/opt/android-sdk`, on a container with 4 vCPU and 15 GB. The Android SDK came from
`commandlinetools-linux-16111833_latest.zip`, with `platforms;android-37.1`, `build-tools;37.0.0` and platform-tools
installed. `local.properties` is gitignored. Container-only Gradle init: the Google Central mirror is put first, as in
the P1 and S0-CORE reports.

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew --no-configuration-cache :core:domain:compileKotlinDesktop :core:testing:compileKotlinDesktop :protocol:oc2:compileKotlinDesktop` (baseline) | BUILD SUCCESSFUL. The core contract compiles, so depending on `:core:domain` is safe. |
| 2 | `./gradlew --no-configuration-cache :protocol:paseo:desktopTest` (final) | BUILD SUCCESSFUL; `python3 /tmp/results.py protocol/paseo` gives **TOTAL 228, failed 0, skipped 0** across 20 classes. |
| 3 | `./gradlew --no-configuration-cache :protocol:paseo:compileAndroidMain` | BUILD SUCCESSFUL. |
| 4 | `python3 -I e2e/fixtures/fxctl.py start --run-dir /tmp/fxrun-real FX-PASEO` with `FX_PASEO_MODE=real`, `FX_PASEO_BIN=<npm i @getpaseo/cli@0.11.2>/node_modules/.bin/paseo` and a per-run `FX_PASEO_PASSWORD` (mode 0600, never printed); then `OC_PASEO_URL=ws://127.0.0.1:4310 OC_PASEO_DIR=<fixture project> OC_PASEO_PASSWORD=… OC_PASEO_CTL=http://127.0.0.1:4311 ./gradlew --no-configuration-cache :protocol:paseo:desktopTest --tests '*PaseoLiveProofTest*'`; then `fxctl.py stop` (exact PID) | 11/11 PASS (§2.2). |
| 5 | The same with `FX_PASEO_MODE=stub` | 11/11 PASS. |
| 6 | `./gradlew --continue :quality:test --tests '*KonsistRulesTest*'` | **Could not run.** `:engine:connection` and `:ui:kit-chat` do not compile on this base; this is the parallel core reconciliation the brief warns about. Checked by hand instead: no `Dispatchers.`, `Clock.System`, `runCatching`, `runBlocking`, `GlobalScope`, `println`, `System.`, `Thread.` or `java.*` in `commonMain`, and no platform source sets (K10). Konsist's `production` scope does not include `protocol/` yet. |

Test-first notes:
- The first red for the transport, gateway and mapper tests was a missing type or a `TODO()` stub. Slice A ran 65
  tests against the stubs: 39 failed.
- Slices C and D each did a mutation red check, and the mutations were then reverted:
  - C: plan approval changed to send `implement_resume`, which failed the plan test;
  - D: the snapshot-revision fence and the repeated-cursor stop removed, which failed exactly the two matching tests.
- No tool reformatted the code: `tool/format-changed.sh` does not exist on this branch.

Private IPs and emails: `git diff origin/native/m0-2026-11-01...HEAD` was grepped for RFC 1918, CGNAT
(100.64/10) and email patterns. The test vectors that used a private-LAN literal and Tailscale IP literals now use an RFC 5737
documentation address and `*.tail1234.ts.net` MagicDNS names (the IP-range classifier itself is `:core:security`'s,
tested there). The only email-like matches are Kotlin
`this@PaseoGateway` labels and the commit trailers.

## 4. Deviations

| # | Plan / Flutter | Built | Why |
|---|---|---|---|
| D1 | Plan 04 §8: Paseo on `:core:net`'s `WsSession` + `WsRpc` | A module-local `PaseoSocket` seam, with the request bookkeeping in `PaseoTransport`. The production socket is not built here. | `:core:net` has no WebSocket path yet (S1-NET open problem 1), and this unit may write only `protocol/paseo/**`. The seam has the shape §5.2 asks for (a frame channel, a close code, send, close), so a `WsSession` adapter is about 30 lines. |
| D2 | Plan 04 §2.2 file names (`PaseoHello.kt`, `PaseoReconnect.kt`, `PaseoTerminalFrames.kt`) | The hello is in `PaseoTransport`, reconnect is in `PaseoGateway` (lifecycle section), and terminal frames are only `PaseoBinaryFrame`. | They are ported from the Dart files with the same split. Terminal is P1. |
| D3 | Dart turns on `fileBrowsing`, `terminal`, `sessionDiff`, `worktreeCreate`, `worktreeBrowsing`, `subagentSessions` and `agentHandoffFiles` | Off in `PaseoCapabilities.Base` (`TODO(S5-PASEOP1)`). | Their gateways are P1 extras. UI gates on capabilities, so it must not offer them. |
| D4 | Plan 04 §8: questions as `ServerEvent.QuestionAsked` / `QuestionSettled` | `GatewayEvent.RefreshHint(HintKind("question"))`, with questions read through `pendingQuestions()`. | The frozen `GatewayEvent` has no question events (contract request 1). |
| D5 | Dart `session.error` on a failed turn | `RunEnded(Failed(kind = null, tag = PaseoTurnFailed, details = null))`. When the reply already showed the agent's error row: `RunEnded(Succeeded)`, which is Dart's bare `session.idle`. | Neutral vocabulary; no second error (B-CHAT-071). The words come from `:ui:words`. |
| D6 | Dart throws `ProductException` copy for pictures | `PaseoImages.Refused(problem)`, which the gateway answers as `Failure.Unavailable(FeatureId("promptAttachments"))` with the problem in Details. | No copy in protocol code. There is no `RefusalReason` for it (contract request 2). |
| D7 | Pins: plan 04 §8 says "{0.9.2, 0.11.2}", CC "{2.1.283, 2.1.294}" | The Flutter set of 2026-10-31: 0.9.2, 0.11.2, 0.12.0, 0.13.2, 0.14.0 and CC 2.1.283, 2.1.294, 2.1.316. | `tested_versions.dart` is the source of truth with evidence (`docs/qa/pins-2026-10-31`). The phone pin is still 0.11.2. |
| D8 | Several gateway paths from Flutter | Not ported: `resumeHostAgentChat`, `startNewHostAgentChat`, `_canonicalModelId`, watched-start-over-stale-record, `loadHostAgentContinuation`'s new-chat acknowledgement, the provider-cache fence per project, `fetchAgent` feeding the work inventory, `beforeAgentStart`/browser/payload hooks, sub-agent card linking. | The core slice first. Each is listed in §2.3 or §6. |
| D9 | Dart answers `prompt` with void | `PromptAdmission.Confirmed(null)`. A `DeliveryUnknown` send is `Ok(PromptAdmission.Uncertain)`, and further sends answer `Uncertain` until the history is read again (B-AGENT-188). | Plan 04 §11.1: "a lost response is Uncertain, never a retry". |
| D10 | Slice A: Dart `ToolState.fromJson` fields | A failed call's words go to `output` (status `Error`); declined uses the fixed "The user declined this tool call" in `output`; raw-text input goes to `inputRaw`; pretty JSON uses kotlinx `prettyPrint`. | The core `ToolState` has no `error`, `declined` or `inputJson` (S2-MODEL owns it; contract request 5). |
| D11 | Slice D: Dart feed reads `ChatFeedItem` / `AgentCatalog` | Local `PaseoFeedRow`/`PaseoFeedSnapshot` (`TODO(move-to-core)`); labels injected through `agentLabel`; no preview and no copy (an untitled row is `title = null`). | No feed vocabulary in core; the catalog belongs to `:engine:agents`. |
| D12 | Live proof "JVM task" against the VPS daemon | A desktopTest gated on `OC_PASEO_URL`, run against FX-PASEO (real 0.11.2 and stub) on loopback in this container. | There is no VPS access here. FX-PASEO real mode had not been run before (S0-FX open problem 1); it works with `@getpaseo/cli@0.11.2` from npm. |
| D13 | `GatewayEvents` "the gateway owns the loop" | Listening starts with `PaseoGateway.startListening()` (Dart `openEventChannel().start()`). | The core interface has no start; the connection engine calls it after `open`. |

## 5. Requests

**Catalog / build-logic:** no new library. The JDK socket uses only `java.net.http`, and
`kotlinx.coroutines.future.await` comes from coroutines-core on the JVM.

**`MODULE_OWNERS`** (coordinator): add `:protocol:paseo	F	backend (Codex)	slice-3`. `:protocol:oc2` is missing too.

**Contract requests** (`:core:*` owners):
1. `GatewayEvent.QuestionAsked(PendingQuestion)` and `QuestionSettled(sessionId, requestId, Replied|Rejected)`
   (plan 04 §8). Paseo emits `RefreshHint("question")` until then.
2. `RefusalReason` values for these cases, which today use stand-ins:

   | Case | Stand-in today |
   |---|---|
   | "pictures only" / "picture too large" / "picture type" | `Unavailable(promptAttachments)` |
   | "start a new chat" (Paseo `newChatRequired`) | `Net.Conflict` |
   | "choose an available project" / "choose an available agent" (feed) | `StaleScope` / `Unavailable(hostAgentProviders)` |

3. `PermissionReply.Cancel` (deny and interrupt). The gateway exposes `cancelPermission` beside `replyPermission`.
4. Host-agent types (`HostAgentProvider`, `HostAgentCatalog`, `HostAgentContinuation`, `HostAgentPermissionRequest`)
   and a `HostAgentProviderGateway` / `HostAgentPermissionGateway` facet in core.
5. `ToolState` gains `declined` and a raw-input field for non-JSON tool input (S2-MODEL).
6. The chat-feed vocabulary (`PaseoFeedRow`, `PaseoFeedStatus`, `PaseoFeedFilter`, `PaseoFeedSnapshot`,
   `PaseoProjectSummary`) moves to `:core:model`.
7. **For S1-NET / P-N1:** a `WsSession` and an adapter `WsSession → PaseoSocket` with the Paseo settings of §5.3:
   - subprotocol `paseo.bearer.<pw>` and `Authorization: Bearer <pw>`, only when a password is set;
   - an 8 MiB cap, 1 MiB on the bounded connection;
   - 403 maps to `Authentication` with a password and `HostRefused` without one;
   - no extensions.

## 6. Open problems

1. **Production socket.** Until `:core:net` ships `WsSession`, `PaseoGatewayFactory` has no production
   `PaseoSocketFactory` to bind, and the app cannot reach a daemon. This is D1 / contract request 7.
2. **E2E-30 on the emulator** (Claude Code and Pi through the phone helper) has not been run. It needs the socket, the
   agents engine (S3-AGENTSE) and the helper (S3-AGENTHOST).
3. **Fake Claude Code only.** FX-PASEO real mode ran the real daemon with the fixture's fake Claude Code. A signed-in
   real Claude turn was not run, as in the Flutter pin evidence.
4. **`:quality` could not run** on this base. Re-run `KonsistRulesTest` and `ModuleOwnersTest` once
   `:engine:connection` and `:ui:kit-chat` compile again. Also consider adding `protocol/` to Konsist's
   `production` scope: it is not scanned today.
5. **Rows not covered**, in §2.3: the P1 extras for S5-PASEOP1, and for S3-AGENTSE/AGENTHOST the agent features,
   browser launch and payload-use admission.
6. **Thread confinement is by convention.** The non-suspend `PaseoFeedPort` members and `PaseoGateway.localWorkBusy`
   read lane-confined state, so callers must be on the gateway's lane. The connection engine owner should confirm that
   the feed runs on that lane.
7. **Turn failure is not flagged in that case.** A turn that fails after its reply already showed the error emits
   `RunEnded(Succeeded)` (D5). If `:engine:transcript` needs `lastRunFailed` set in that case, the contract needs a
   `RunOutcome.Failed(alreadyShown = true)`.

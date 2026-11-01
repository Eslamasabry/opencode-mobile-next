# S2-DLV: send pipeline, steer, Stop, live line, offline queue, markers and receipts

Date: 2026-11-01. Branch: `native/dlv-2026-11-01`, created from `claude/native-s2-dlv-unit-w93fw0` at `96b8f66f`.
Write set: `native/engine/delivery/**` (new module) and the send files of `native/protocol/oc2/**`
(`…/protocol/oc2/send/`), plus this report.

Inputs: `native/AGENTS.md`, `docs/native/slice0/*`, `docs/native/units/*`, plan `01` (binding), `15` (S2-DLV row,
C15-1), `09` §4 (delivery, steer, Stop, live line), `04` §7.13 and §13 (receipts, never send twice, the retry trap),
`05` (queue format), and the Flutter sources the rows cite (`lib/state/offline_queue.dart`,
`lib/state/connection/queue.dart`, `lib/state/pending_command_journal.dart`, `lib/domain/command_receipts.dart`,
`lib/domain/command_receipt_transport.dart`, `lib/ui/screens/chat/chat_send.dart`, `lib/api2/client.dart`,
`lib/api2/gateway.dart`).

## Result

**Implemented and verified on desktop (JVM); the Android target compiles. Not wired into the app graph and not
enabled** (the bindings are the coordinator's; see Open problems).

- `:engine:delivery:desktopTest`: **65 tests, 0 failures** (9 classes).
- `:protocol:oc2:desktopTest`: **60 tests, 0 failures** (9 new in `Oc2PromptSendTest`, the 51 existing still green).
- `:engine:delivery:compileAndroidMain`, `:protocol:oc2:compileAndroidMain`: pass.
- Planted faults (two runs, reverted with `git checkout`): (a) the flush ignores the dispatch marker (both guards
  removed), (b) resend ignores an existing receipt, (c) an uncertain online send puts its words back, (d) the steer table
  answers "after reply" where steering exists. 14 test failures across the two runs, every one a never-twice,
  receipt or steer test. With only the first marker guard removed nothing failed: the flush's second guard (re-read
  under the queue lock) still held, so that guard is load-bearing.
- `:quality:test`: 29 of 31 pass; the 2 failures are a missing `MODULE_OWNERS` row (catalog request) and a K4 hit in `:ui:kit-chat` (another unit). Details under "Commands and results".

**Never twice, across crashes and reconnects.** Every send test counts what the fake server holds. Proven paths:
lost response (flush and online), post-dispatch failure, accepted-but-unrecorded, process death with the request on
the wire (flush and online), restart after each of those, a reconnect between bubble and dispatch, a location change
after the wake, Retry/Check again with receipts, resend with and without receipts, profile deletion mid-send, and a
corrupt journal. In each the server holds the prompt at most once and the device keeps a review entry, not a resend.

## What was built

### `:protocol:oc2` send files (`io.github.eslamasabry.oc.protocol.oc2.send`)

| File | What |
|---|---|
| `Oc2ClientIds.kt` | Plan 04 §7.13: `message()` / `session()` in OpenCode's sortable shape (prefix, 12 hex of `(ms*4096)&0xffffffffffff`, sessions XOR-ed, 14 base62). Clock and Random injected |
| `Oc2PromptSend.kt` | `POST /session/{id}/prompt` body with Dart's key order (`id`, `text`, `files`, `agents`, `delivery` only when chosen); the staged-revert preflight (`revertBlocksPrompt`); `receiptConfirms` / `inboxConfirms` / `messageConfirms`: only our id, our session and a prompt type (`user`, the real one, and `prompt`, Flutter's) confirm |

### `:engine:delivery` (`io.github.eslamasabry.oc.engine.delivery`, commonMain only)

| File | What |
|---|---|
| `queue/QueuedPrompt.kt` | The `oc.offlineQueue` entry and `QueuedPromptCodec`: Flutter's exact JSON (every key, explicit `null`, via `DartJson.encode`), Dart's lenient `fromJson`, and the places Dart fails the whole queue (non-list, missing ids, repeated id, a non-number where a number is cast). `payloadBytes` in UTF-16 units |
| `queue/QueueLimits.kt` | 50 entries, 14 days, 60 MiB / 20 MiB measured as Flutter does; oldest first; unknown ages kept; a single entry never evicted into nothing |
| `queue/OfflineQueueStore.kt` | `oc.offlineQueue` over `KeyValueStore` (app lane, `commit()`); fail-closed read; FIFO saves; a non-empty save over an unreadable blob is refused, the empty save is the clear |
| `queue/OfflineQueue.kt` | Port of `connection/queue.dart`: queue/remove/clear/resend/retry, online holds (`holdOnline` → `canDispatchOnline` → `finishOnline`), the flush (marker first, rollback if the policy changed, receipts, status-bearing failures continue, others stop, removal persisted after acceptance, "accepted, unrecorded"), receipt reconcile at flush start and Check again, profile close/inspect/sweep, `unconfirmedTexts` |
| `receipts/CommandReceipt.kt` | The receipt (Dart validation: seven keys, opaque ids, SHA-256 scope, int ≥ 0, known state) and `DispatchScope` with `receiptScope = sha256(jsonEncode([profileId, baseUrl, directory, workspace]))` |
| `receipts/PendingCommandJournal.kt` | `oc.pendingCommands.<profileId>` (`{"schemaVersion":1,"commands":[…]}`, ≤ 1000 / 1 MiB, tombstones, atomic claims, valid transitions only, read-back after write, unavailable for the process after any doubt), `PendingCommandJournals` (one per profile, closed set), `ProfileDirectory` port |
| `receipts/CommandReceiptController.kt` | `send` (claim first, dispatch once, `uncertain` unless proven) and `retry` (look up only) |
| `port/DeliveryPorts.kt` | `SendLink` (one connected server; instance identity = gateway epoch), `DeliveryConnection`, `AutomationPolicy`, `ConversationFacts`, `StopMemory`; `Failure.hasStatus`, `Failure.nothingSent` |
| `port/GatewaySendLink.kt` | `SendLink` over `ServerGateway` + `CorrelatedPromptGateway`, `SessionSelectionGateway`, `MidTurnPromptGateway`; lookup reads the inbox, then user messages (≤ 100 pages, cursor loops stop) |
| `SteerPolicy.kt` | The 09 §4.3 table → `SendRoute` (Plain, Steer, JoinTurn, RestartTurn, Blocked, AfterReply, Offline); no input can select queue delivery |
| `PromptDelivery.kt` | 09 §4.2 steps 1–10 (typed-text routing, undo refusal, ≤ 10 s offline wait then queue, marker before the composer clears, bubble first / steers transport first, epoch and fence re-check, receipt-bearing dispatch, outcomes), `outcomes`/`acknowledge` (≤ 32), `stop` (800 ms ignore, call off a waiting send, stop again after an on-wire one, `noteStopFromThisApp` before the call, `markStoppedByPerson`), `checkAgain`, `resend(confirmedByPerson)`, `routeFor`, `localTurns`, `unconfirmedTexts` |
| `live/ConversationActivity.kt` | `LiveTurnRules.derive` (rules 1–6 of 09 §4.1) and `ConversationActivity.liveTurn(ref)`, ticking only for a retry countdown or the 8 s slow flip |

Tests: `OfflineQueueStoreTest`, `OfflineQueueTest`, `ReceiptsTest`, `PromptDeliveryTest`, `SteerTest`, `StopTest`,
`ConversationActivityTest`, `GatewaySendLinkTest` (commonTest) and `FlutterQueueFixtureTest` (desktopTest, real files).

## Catalog rows

Test names start with the row id (AGENTS rule 11).

| Row | Status | Where |
|---|---|---|
| B-STATE-008 | covered | `ReceiptsTest` (lost response → lookup, no resend; restart reconciles on flush, keeps the tab; absent receipt and lost capability never resend), `PromptDeliveryTest` (online path, Check again) |
| B-STATE-009 | covered | `ReceiptsTest` (one dispatch for simultaneous sends, durable claim before dispatch, no borrowing across tab/scope, tombstones and transitions, late lookup keeps a replacement, unsupported gateways use normal dispatch, deletion closes the journal, malformed journal fails closed, Flutter's bytes and scope hash) |
| B-STATE-033 | covered | `OfflineQueueStoreTest`, `FlutterQueueFixtureTest` (real blobs) |
| B-STATE-034 | covered | `OfflineQueueStoreTest` (corrupt JSON, wrong shapes, repeated id, wrong cast, wrong preference type) |
| B-STATE-035 | covered | `OfflineQueueStoreTest` |
| B-STATE-036 | covered | `OfflineQueueStoreTest` (with the UTF-16 vector) |
| B-STATE-037 | covered | `OfflineQueueTest` |
| B-STATE-038 | covered except "only confirmed deliveries are logged" (automatic-act log not ported) | `OfflineQueueTest` |
| B-STATE-039 | covered | `OfflineQueueTest`, `PromptDeliveryTest` (a send after a resume waits) |
| B-STATE-040 | covered | `OfflineQueueTest`, `FlutterQueueFixtureTest`, `PromptDeliveryTest` |
| B-STATE-041 | covered | `OfflineQueueTest` (two tests) |
| B-STATE-042 | covered | `OfflineQueueTest`, `PromptDeliveryTest` |
| B-STATE-043 | covered | `OfflineQueueTest`, `PromptDeliveryTest` (process death on the wire, both paths) |
| B-STATE-044 | covered | `OfflineQueueTest` (two tests) |
| B-STATE-045 | covered | `OfflineQueueTest` |
| B-STATE-046 | covered | `OfflineQueueStoreTest` (four tests) |
| B-STATE-062 | covered (queue half) | `OfflineQueueTest` |
| B-STATE-066 | **partial**: count (never zero for an unreadable queue) and sweep with markers kept on refusal. Not built: "keep" → Saved prompts (`oc.keptQueuedPrompts`), moves between servers | `OfflineQueueTest` |
| B-STATE-067 | **not covered** (removal Undo/repair flows need the profiles engine and `oc.keptQueuedPrompts`) | — |
| B-STATE-119 | covered | `OfflineQueueTest`, `PromptDeliveryTest` |
| B-PROTO-023 | **partial**: prompt wire fields, model/agent kept off the v2 prompt, command/shell routing, staged-revert preflight. Not: read-state `viewSession`, explicit selection replacement at creation | `Oc2PromptSendTest`, `PromptDeliveryTest` |
| B-PROTO-024 | **not covered** (forms/inbox routes and typed 400/409 belong to the OC2 gateway, which is not built yet) | — |
| B-PROTO-025 | covered (delivery half; the permission reject-message half is the requests unit's) | `Oc2PromptSendTest`, `SteerTest` |
| B-PROTO-028 | **not covered**: permission preparation before a send needs `:engine:requests` (S2-REQ); `SendLink` has no preflight hook for it yet | — |
| B-PROTO-032 | **not covered** (session permission policy composition; requests/permissions unit) | — |
| B-PROTO-042 | covered | `Oc2PromptSendTest`, `GatewaySendLinkTest` |
| B-PROTO-043 | covered | `Oc2PromptSendTest`, `GatewaySendLinkTest`, `OfflineQueueTest` (location change after preflight), `PromptDeliveryTest` (epoch) |
| B-PROTO-045 | **not covered** (inbox projection of pending sends, cancel → composer, delivery flip: needs the OC2 event adapter and the "Waiting to send" strip) | — |
| B-PROTO-073, 074 | **not covered** (permission parsing and idempotent answers: `:engine:requests`) | — |
| B-PROTO-135 | covered (row is in plan 04 §15.5, not yet in `behaviors.jsonl`) | `Oc2PromptSendTest`, `GatewaySendLinkTest` |
| B-PROTO-140 | covered as a side effect (plan 04 §15.5 row) | `Oc2PromptSendTest` |
| B-CHAT-106 | covered (step 2) | `PromptDeliveryTest` |
| map CHAT-GENUI-08/23/31/40/50 | covered (rules of 09 §4.1, step 1 routing, Stop) | `ConversationActivityTest`, `PromptDeliveryTest`, `StopTest` |
| B-AGENT-243 | delivery half (records `stoppedByPerson`, notes this app before the call) | `StopTest` |

## Commands and results

JDK 21 (`/usr/lib/jvm/java-21-openjdk-amd64`); Android SDK at `/opt/android-sdk` (`commandlinetools-linux-16111833`,
`platform-tools`, `platforms;android-37.1`, `build-tools;37.0.0`); `native/local.properties` (gitignored). Maven
Central answered 429 to this container, so a local-only `~/.gradle/init.d/mirror.gradle.kts` adds Google's Central
mirror in `beforeSettings` (with `google()` before the plugin portal). Nothing in the repo changed for this.

| Command (from `native/`) | Result |
|---|---|
| `./gradlew :protocol:oc2:desktopTest --tests '*Oc2PromptSendTest*'` | BUILD SUCCESSFUL, 9 tests, 0 failures |
| `./gradlew :engine:delivery:desktopTest` | BUILD SUCCESSFUL, 65 tests, 0 failures (ConversationActivityTest 6, FlutterQueueFixtureTest 3, GatewaySendLinkTest 4, OfflineQueueStoreTest 8, OfflineQueueTest 13, PromptDeliveryTest 10, ReceiptsTest 11, SteerTest 5, StopTest 5) |
| `./gradlew :engine:delivery:compileAndroidMain :protocol:oc2:compileAndroidMain :protocol:oc2:desktopTest` | BUILD SUCCESSFUL; oc2 60 tests, 0 failures |
| planted faults (b)(c)(d) + a masked (a) | 7 failures, all intended; reverted |
| planted fault (a) with both marker guards removed | 7 failures (B-STATE-040 ×2, 041, 042, 043 ×2, 008); reverted |
| `./gradlew :quality:test` | BUILD FAILED, 31 tests, 2 failed, **neither from this unit's code**: `ModuleOwnersTest` (no `MODULE_OWNERS` row for `:engine:delivery`, the expected catalog request below; `:engine:entry` and `:android:platform` are missing too, from earlier units) and Konsist K4 (`ui/kit-chat/…/KitChatLocals.kt`, S2-KCB). Every other Konsist rule and the module-dependency rules pass with this module in the graph |

No formatter is configured in `native/` (`tool/format-changed.sh` does not exist); files follow the existing style.

## Deviations

- **D-DLV1, ports instead of engine edges.** `:engine:delivery` depends on `:core:common/model/domain/data` only. The
  transcript (`ConversationFacts`, `TurnFacts`), the connection (`DeliveryConnection`, `SendLink`, `StopMemory`), the
  automation policy and the profile list (`ProfileDirectory`) arrive through ports bound by the app graph, like
  S2-ENTRY D-E8. No Metro `@Inject`/`@SingleIn` yet, so nothing unbound enters the graph. `GatewaySendLink` is the
  production `SendLink`.
- **D-DLV2, the entry `error` field holds a symbolic reason** for entries native writes (`Unconfirmed`,
  `AcceptedUnrecorded`, `SessionRevertPending`, `Server:502`, `Rejected:409`, …), never server prose or exception text
  (AGENTS rules 5 and 7). Flutter's English sentences are read and kept unchanged; `:ui:words` words both.
- **D-DLV3, a repeated queue id is refused** (`QueueRefusal.DuplicateId`). Flutter would write it and then fail the
  whole queue closed on the next read.
- **D-DLV4, `SendLink.prompt` contract.** A `Refused`, `Unavailable` or `StagedRevert` answer promises nothing left the
  device, so the online send puts the words back instead of leaving a review entry. Flutter treated every failure after
  the call started as possibly sent. The queue flush keeps Flutter's conservative rule.
- **D-DLV5, status-bearing failures after dispatch let the batch continue on the receipt path too.** Flutter's
  receipt path threw a non-API exception and stopped the batch.
- **D-DLV6, accepted prompt types are `user` and `prompt`** (N4-D13: the real server answers `user`; Flutter's tests
  send `prompt`).
- **D-DLV7, API shape.** `SendOutcome` adds `Unconfirmed` (status line + Check again) and `Queued`; `stop` takes a
  `ConversationRef` (actions name their target); `TypedKind` is public for the composer.
- **D-DLV8, "Stop after this step" answers `Unavailable`** for now. The steering note's words belong to `:ui:words` and
  the inbox take-back is not built (see Open problems).
- **D-DLV9, an inbox without steering routes as Plain** (delivery unset, no promise). 09 §4.3 does not list the case.
- **D-DLV10, online sends use the receipt journal** where the server has receipts (`queue:online-<µs>` → the wire
  client id), so Check again works for a lost online send too. Flutter used receipts for queued sends only.
- **D-DLV11, eviction order on equal `createdAt`.** Kotlin's sort is stable; Dart's `List.sort` is not guaranteed
  stable. Only entries created in the same millisecond can differ.
- **The writer-shaped fixture** `engine/delivery/src/desktopTest/resources/flutter/offline_queue.json` was produced by a
  Python script that mirrors `QueuedPrompt.toJson` + `jsonEncode` key for key; there is no Dart in this container. The
  second blob is the S1 migration fixture's own `oc.offlineQueue`. A Dart-written golden would close this
  (`tools/flutter-fixtures/dart_vectors.dart` is still pending, as `DartJson` notes).
- **Tests were written with the code**, slice by slice, not red first; the planted-fault runs stand in for the red step.

## Catalog requests

- `MODULE_OWNERS`: add `:engine:delivery	F	(S2-DLV)	slice-2` (or the owner the coordinator names). Without it
  `ModuleOwnersTest` fails.
- `behaviors.jsonl`: append B-PROTO-135 and B-PROTO-140 from plan 04 §15.5 (tests already carry those ids).
- No new libraries. `kotlin("test-junit")` for desktopTest only, as other modules do.

## Open problems

1. **Not wired.** The coordinator binds, per connection graph: `GatewaySendLink(profile, baseUrl, gateway)` as the
   `DeliveryConnection.link` (a new instance per gateway epoch), `OfflineQueueStore(kv, lanes.app)`,
   `PendingCommandJournals(kv, lanes, profiles)`, `OfflineQueue`, `PromptDelivery`, `ConversationActivity`, with the
   connection scope, `ConversationFacts`/`TurnFacts` from the open `TranscriptHandle`, `StopMemory` from
   `:engine:connection`, and the automation policy. The flush trigger (connect, reconnect, explicit) is the connection
   engine's call to `OfflineQueue.flush()`. Profile deletion must call `OfflineQueue.closeProfile` before the sweep.
2. **Rows not covered** (table above): B-STATE-066 keep/move and 067, B-PROTO-024, 028, 032, 045, 073, 074. 028 needs a
   permission-preparation preflight hook in `SendLink` once `:engine:requests` exists; 045 needs the OC2 inbox events.
3. **Not built from 09 §4:** the steer merge (several waiting plain-text steers taken back and sent as one), "Stop
   after this step" with Undo, `KitQueuedMessage` states (waiting / not confirmed after 8 s / …), the 09 §4.6
   `PendingReconcile.visiblePending`, the `prompt_sent` analytics event and the `chat.sendToBubble`/`sendToAck` traces,
   voice-conversation sends, and `sendInsteadOfDraft`'s status-line Try again.
4. **Not built from 04 §13 / 05:** `outbound-tombstones.json` and the migration journal's `outboundHold` (05 §9.4),
   `SessionCreationReceiptTest` (B-AGENT-226, same journal), the Room table at switch+1, and the automatic-act log of
   queued sends ("While you were away").
5. **Journal profile check.** Flutter's journal reads `oc.profiles` itself; here `ProfileDirectory` must be bound to
   the profiles engine, or receipts are refused for every profile.

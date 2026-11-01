**Verdict: REQUEST CHANGES — integration, persistence, and cancellation defects remain in `5ec8616d..f885d05f`.**

1. **BLOCKER — OC2 adapter cannot feed the existing core contract.**  
   [Oc2EventAdapter.kt:112](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/protocol/oc2/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/oc2/adapter/Oc2EventAdapter.kt:112)

   **Problem/evidence:** The adapter declares its own `GatewayEvent`, `MessageInfo`, `Part`, and related types. Core already contains different contracts at the reviewed revision. Assigning an adapter result to `core.domain.events.GatewayEvent` fails by type incompatibility. Replacing imports alone also fails: core `SessionUpserted` takes a `Session`, identifiers use wrappers, and retry failures require `Redacted` details. Local `RunIdle` has no core equivalent; plan 04 §7.6 requires idle to terminate the run while preserving previous failure/interruption.

   **Fix:** Remove the duplicate vocabulary and map into the existing core types. Coordinate missing core variants with their owner. Make adapter tests consume core events, including idle-only completion and failure followed by idle.

2. **MAJOR — Failed secret deletion can report success on retry and resurrect the credential after restart.**  
   [AeadSecretStore.kt:83](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/core/data/src/commonMain/kotlin/io/github/eslamasabry/oc/core/data/secret/AeadSecretStore.kt:83)

   **Problem/evidence:** `SharedPreferencesSecretEntries.remove()` uses Android `commit()`, which changes platform memory before reporting disk failure. First deletion: cache is cleared, platform memory loses the entry, `commit()` returns false, and disk can retain it. Retry: `entries.get(name)` returns null, so line 88 skips persistence and returns `Ok`. Restart restores the supposedly deleted password. Failed `put()` similarly leaves uncompensated platform memory, allowing a refused value to be persisted by a later successful edit.

   **Fix:** Always perform and acknowledge deletion, including retries when platform memory says absent. Compensate failed writes using the previous encrypted entry, and keep unresolved persistence failures explicit. Add failure-injection tests with separate memory/disk state and restart readback.

3. **MAJOR — Cancellation can suppress notifications after a successful preference commit.**  
   [PrefsKeyValueStore.kt:104](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/core/data/src/commonMain/kotlin/io/github/eslamasabry/oc/core/data/kv/PrefsKeyValueStore.kt:104)

   **Problem/evidence:** Disk commit and snapshot publication occur inside `withContext(io + NonCancellable)`, but `changes.emit` occurs afterward. When the caller uses another dispatcher and is cancelled during the commit, prompt cancellation on returning from `withContext` prevents line 110 from executing. CAS has the same split at lines 76–86, with an additionally cancellable emission. Persistence changes, observers receive nothing, and the lane releases its ticket.

   **Fix:** Keep commit, snapshot update, and successful change publication inside one noncancellable operation. Test cancellation while the backend is blocked, then assert persisted state and notification delivery before drain completes.

4. **MAJOR — WriterLane does not preserve its promised admission order.**  
   [WriterLane.kt:48](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/core/data/src/commonMain/kotlin/io/github/eslamasabry/oc/core/data/lane/WriterLane.kt:48)

   **Problem/evidence:** Admission registration and mutex acquisition are separate. Admit writer A, pause it before line 53, then admit B: B acquires the free mutex and writes first; A subsequently overwrites it. Mutex fairness orders mutex waiters, not admission tickets. This violates the method contract and plan 03 §17.3’s FIFO requirement. The existing ordering test starts A inside the mutex, so it misses this interleaving.

   **Fix:** Atomically enqueue an ordered ticket during admission and execute tickets in that order, preserving cancellation and drain accounting. Add a test that pauses A after admission, admits B, and verifies B cannot overtake A.

5. **MAJOR — Ordinary preference reads bypass the migration gate.**  
   [PrefsKeyValueStore.kt:57](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/core/data/src/commonMain/kotlin/io/github/eslamasabry/oc/core/data/kv/PrefsKeyValueStore.kt:57)

   **Problem/evidence:** `get()` and `keys()` load and expose preferences during Closed, Running, or Blocked migration states. Plan 05 §2.2 explicitly requires `GateNotOpen` in debug/QA/tests and an early-read diagnostic in release, with pre-open access restricted to `MigrationReader`. The test at `PrefsKeyValueStoreTest.kt:125` instead asserts that reads are never gated. The Android secret factory also exposes the raw store without the gated binding required by §4.2.

   **Fix:** Implement the preference read guard and restricted migration reader; expose secrets through `GatedSecretStore`, retaining a migration-only raw handle. Replace the contradictory test with closed/running/blocked gate cases.

6. **MAJOR — Adapter introduces refresh traffic explicitly excluded by the plan.**  
   [Oc2EventAdapter.kt:324](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/protocol/oc2/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/oc2/adapter/Oc2EventAdapter.kt:324)

   **Problem/evidence:** Filesystem, MCP, and VCS events emit refresh hints; `refresh()` also forwards command, skill, reference, project, worktree, and LSP updates. Plan 04 N4-D22 and §7.6 explicitly require these families to be counted and dropped at the switch. In particular, `filesystem.changed` fires on tool writes, turning normal execution into repeated reload requests without the required per-kind debounce policy.

   **Fix:** Restrict emitted hints to the plan’s switch allowlist. Add negative adapter assertions for every excluded family.

7. **MINOR — Compaction events decode but disappear before reaching the transcript.**  
   [Oc2EventAdapter.kt:330](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/protocol/oc2/src/commonMain/kotlin/io/github/eslamasabry/oc/protocol/oc2/adapter/Oc2EventAdapter.kt:330)

   **Problem/evidence:** Every `SessionCompaction` phase returns an empty list. Plan 04 §7.6 requires compaction notice parts. Fixture decoding/parity tests cannot detect this loss because they stop before adaptation; live compaction progress and failure remain invisible until a later refetch.

   **Fix:** Map the phases to the shared compaction notice representation and add decode-through-adapter assertions for started, delta, ended, and failed.

8. **MINOR — The advertised CAS-versus-commit regression test never races the same key.**  
   [ConcurrencyTest.kt:54](/home/eslam/Storage/Code/wt/native-reviews/src-w1-data-oc2/native/core/data/src/desktopTest/kotlin/io/github/eslamasabry/oc/core/data/ConcurrencyTest.kt:54)

   **Problem/evidence:** CAS modifies `oc.counter`; ordinary commits exclusively modify `oc.noise.<i>`. The overlap detector catches simultaneous backend calls, but cannot detect a stale same-key comparison or compensation performed between nonoverlapping calls. Its refusal backend also returns false before changing memory, omitting Android’s critical failure behavior. This does not prove the same-key race required by plan 05 §2.2.

   **Fix:** Use deterministic barriers to race CAS and plain commits on one key. Inject a refused write that changes memory first, and assert compensation cannot overwrite an intervening acknowledged update.

Static test-sensitivity checks, without executing tests:

- `WriterLaneTest.kt:28` would fail if `drain()` returned before the paused admitted writer completed.
- `PrefsKeyValueStoreTest.kt:35` would fail if refused-write compensation were removed: its platform-memory assertion would observe `"light"`.
- `Oc2EventsTest.kt:21` would fail if unknown-event payloads were discarded: its `"spin"` assertion requires preserved raw data.

No files changed; no builds or tests run.
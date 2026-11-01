# 14. Testing and parity: tests first, from the catalog

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR) and `02-principles-scope.md`. Where this
file and either of them differ, they win, and the difference is listed in §13.2 for the coordinator. Sections 03, 04,
05, 06, 08, 09, 11, 13, 15 and 16 already define parts of the test system (04 the wire fixtures, 15 the calendar, 16
the canaries and egress). This section reuses their names and only adds what they leave open. Tags: **[V]** checked today against a primary source or on this machine; **[R]** read in the repo, a
map, a research file, a plan section or a memory file; **[E]** my estimate, replaced by the measured value; **[U]**
unverified, proven in slice 0 (§14).

## For the owner, in plain words

1. Before an agent builds anything, a different agent writes its tests from the list of 1,544 things today's Flutter tests prove. The builder's only job is to turn those tests green.
2. The phone flows (37 today, plus a few new ones for features merged since the list was made) are first made to pass on today's Flutter app. The same flow files must then pass, unchanged, on the new app. That is how we know nothing was lost.
3. A parity page shows what share of behaviours pass, overall and per area. It is updated at every integration check, and the number may only go up.
4. The 7,967 screen tests wait until you schedule them, as you said. Nothing starts without your yes. Only free preparation happens now. The one exception is the small set of accessibility screen tests, which the switch rules need before the switch (§5.2).
5. Every slice ends with its tests green, its phone flows green on the release build, an emulator sweep of every screen and a short video. You get an APK only after all of that.
6. On your phone you only check the upgrade by eye and try five flows by hand. The first-launch flow never runs on your phone, because it wipes app data, including the built-in Linux.

Decisions this section adds (work goes ahead on the default until you answer; §13): T14-1 red tests never land on the
main branch (except device tests, which only the emulator can turn green); T14-2 the emulator set; T14-3 free about
40 GB on the PC by deleting six old emulators; T14-4 when the screen-test phase starts (you are asked at the switch);
T14-5 the flake rule; T14-6 the stability bar for the switch candidate.

---

## 0. What this section owns

| Topic | Owner | This section adds |
|---|---|---|
| Parity terms, row status (incl. `stale`), done, check ladder T0–T4, commit trailers, S-criteria | 02 §4, §6, §8, §9 | the machinery: lanes, branch model, runner, dashboard, flake rule, budgets, the device recipes that produce S-2, S-3, S-4, S-18, S-19, S-21, S-22 and S-28 evidence |
| Module and task names, `:quality` gates, `:core:testing` list | 03 §8.3, §10.8, §19.6 | the extra fakes and gates in §3.3 and §6.5 (requests to their owners) |
| Wire fixture tree, `OcFixtures.ROOT`, `FixtureEngine`, the recorder and replay server (`:tools:contracts`), live proofs | 04 §14 | only the flow-side fixture servers (§4.4) and two requests to 04 (§3.2) |
| Canary values (`Canaries`), `canary_scan.py`, the egress harness and `config/egress-allowlist.toml` | 16 §1.2, §2.5 | running them inside every T3 (R11) |
| Migration tests and upgrade journeys J1–J8 (both lineages) | 05 §10 | how they sit in the T3 tiers, the seed flows' authorship, the device recipes (§8) |
| Kit capture matrix, capacity, accessibility | 06 §15 | the screenshot-row rule, the screen-capture home and the visual verdicts (§3.5, §7) |
| Motion checks | 08 §9 | the animations-on E2E-05 run (§4.8) |
| Budgets, benchmarks, Perfetto, journeys PJ-* | 13 | FX-OC2 and the stub model, which 13 §3.8 assigns here |
| Device flows (39 + the new flows of §4.11), flow-side fixtures, Flutter baseline, step → row mapping | **this section** (02 §6.1, §10 item 7) | all of §4 |
| The deferred widget-test phase | **this section** (ADR §4 row 18, 02 NG12) | all of §5 |
| Flake policy, CI layout and runtime budgets for tests | **this section** | §9, §10 |

---

## 1. The numbers this plan works from

| What | Count | Source |
|---|---|---|
| Flutter logic tests (`test(`) | 6,663 (6,671 raw `test(` calls) | catalog summary [R]; grep of `oc_app-batch1/test` at `65e3f70a3` [V] |
| Flutter widget tests (`testWidgets(`) | 7,967 (8,003 raw calls) in 1,090 files; 809 files hold only widget tests | catalog [R]; grep [V] |
| Widget tests by folder | `test/kit` 2,386 · `test/revamp` 921 · `test/goldens` 839 · `test/*.dart` 3,740 · others 117 | grep [V] |
| Widget tests in files the logic catalog already cites | 2,609 (the other 5,404 sit in files no row cites) | `behaviors.jsonl` `sources` × grep [V] |
| Flutter golden images | 4,937 PNG | `find test -name '*.png'` [V] |
| Catalog rows | **1,544**: unit 677 · integration 446 · contract 267 · migration 65 · screenshot 60 · e2e 29 | `behaviors.jsonl` [V] |
| Rows a device flow proves (`portable_e2e`) | 240, by 39 flows | e2e-flows [R] |
| Rows in switch scope | **≈ 1,336** [E], 208 deferred (02 §3.3); ≈ 224 of the 240 device-flow rows (02 §3.4). Per level, scaling 02's total by the catalog mix [E]: unit ≈ 586 · integration ≈ 386 · contract ≈ 231 · migration ≈ 56 · screenshot ≈ 52 · e2e ≈ 25. **Every gate reads the real numbers from `scope.jsonl`**, never from this table. | 02 §3.3, §3.4 |
| Flows at the switch | 37 of 39 (E2E-28 AI Team and E2E-32 Memory follow D6), plus the switch-scope flows §4.11 adds (E2E-40, E2E-41 and the freeze-tip top-up flows) | 02 §3.4; §4.11 |
| Flutter test sources the rows cite | 7,106 references to 800 distinct test files | `behaviors.jsonl` [V] |
| Kotlin tests that carry over | **freeze tip `fc5c1e78f`: 52 JVM files with 674 `@Test`** (batch1 had 46 / 645, the list 11 §4.3 names); 13 instrumented Kotlin files + `RuntimeQaGuard.java` in `androidTest`, step-driven custom `Instrumentation` classes with no `@Test` | `git -C oc_app-freeze` and `oc_app-batch1`, `android/app/src/{test,androidTest}` [V] |
| Native tests at the switch | ≈ 3,800 [E]: ≈ 1,700 row tests (1.3 per row) + ≈ 1,500 kit, motion and gate tests + the 674 carried | estimate |
| Native screenshots at the switch | ≈ 3,300 [E]: ≈ 2,600 kit + ≈ 600 screens (06 §15.3) + ≈ 130 motion frames (08 §9.4) | 06, 08 |

**Test suites outside `test/`** (not in the 6,663 or the 7,967; each has one owner and one disposition):

| Suite | Size [V] | Disposition | Owner |
|---|---|---|---|
| `integration_test/` on-device Dart tests (`bb8_agent_auth_device_test`, `bd9_device_smoke_test` and helpers) | 6 files | retired with the Flutter QA flavours; their checks are covered by E2E-17, 18 and 31 (11 §4.3) | — |
| `tool/qa/*live*` proofs (`oc2_live_proof_test`, `paseo_live_proof_test`, `paseo_first_message_live_test`, `p16a_paseo_probe_test`) | 4 files | become 04 §14.6's live proofs (`:protocol:oc2:liveProof`, `:protocol:paseo:liveProof`) | 04 |
| Bundled shell and Python scripts tested from Dart (`termux_scripts` 48, `agent_removal_scripts` 31, `phone_agent_scripts` 21, `setup_scripts` 15, `host_script_pin` 9, `host_script_install` 8, `claude_install_script` 4 …) | ≈ 150 rows | ported to the script home of §3.9, run under dash **and** bash | test-writer lane |
| Python and shell tests (`test/*.py`, `test/*.sh`, `test/byo_host/*.py`; `tool/qa/test_*.py` 42; `tool/release/test_*.py`; `tool/l10n/test_*.py`) | 6 + 42 + 7 + 2 files | release and APK-size tooling → section 16 and 13's gates; l10n completeness → `:tools:strings`; the `tool/qa` harness tests retire with their harnesses; `byo_host` follows the BYO-VPS feature's disposition (02 §3.7) | 16, 13, l10n owner |
| Rust phone engine (`engine/phone`, 204 `#[test]`) and the `PhoneEngine*` instrumented classes | 204 + 3 classes | D6, after the switch (`:android:team`) | — |

---

## 2. Test-first: how a catalog row becomes a native test before its code exists

### 2.1 The lanes

| Lane | Model | Works on | Produces | Never does |
|---|---|---|---|---|
| **Test-writer lane** (02 §7.6) | Sonnet 5.5; Opus 5.5 for rows of `:engine:transcript`, `:android:migration` and the runtime split (ADR §10.3) | the rows of slice N+1 while builders do slice N; slice 0's own rows in W1 (W14-19, §2.11) | one `native/s<N>-tests-<unit>-<date>` branch per builder unit, with red tests named after rows | writes product code other than contract stubs (§2.3) |
| **Fixture lane** | Codex: section 15's S0-FX builds every flow fixture server in W1–W3; 04's `:tools:contracts` owners record wire fixtures (04 §14, WP-04.7); Sonnet helps with Python stubs | fixture servers before their flow batch; wire recordings one slice ahead | `native/e2e/fixtures/**`; `native/fixtures/**` in 04's tree | records against the owner's live servers |
| **Flows lane** | Sonnet writers (at most 2) plus one emulator holder (the T3 holder) | the 39 Maestro flows and the new flows of §4.11, Flutter first | `native/e2e/flows/**`, the Flutter baseline record | changes the Flutter app to make a flow pass |
| **T3 holder** (02 §6.9) | Sonnet, one at a time | device runs | `build/native-t3/<sha>/` + QA records | lends the emulator mid-run |
| **T4 holder** (13) | Sonnet | Macrobenchmark, Perfetto | `perf/results/**` | — |
| **Builders** | Codex (core, protocol, engine), Sonnet (kit, features, `:android:*` ports), Opus (hard units) | make red tests green | product code | weaken, skip or delete an assertion (02 §7.6) |

The test writer and the builder of a unit are always **two different agents**. The point of test-first is that the
tests come from the row and the Flutter test, not from the native code.

### 2.2 Branch model: red tests never land on `native/main` (T14-1)

`native/main` is always green **for everything T1 and T2 run**. A red T1/T2-proven test lives only on branches, until
the builder of its unit turns it green.

```
native/main ──●──────────────────────────────────────────●───────────────●────►
              │ contract freeze s3 (skeleton + stubs)      ▲ merge unit A    ▲ merge unit B
              ├─► native/s3-tests-agents-2026-12-01 (red) ─┐               │
              │                                            └─► native/s3-agents-2026-12-08 (builder: red → green)
              └─► native/s3-tests-phone-2026-12-01 (red) ───► native/s3-phone-2026-12-09 ───────────────┘
```

1. **Contract freeze** of slice N+1 (coordinator, 02 §7.6) lands on `native/main`. It contains the
   `native/contracts/s<N+1>.md` text and, for each module the slice starts, a compiling skeleton: the build file, the
   public API from the contract, and stub bodies (§2.3). This is when the module "starts" in the ADR §5.2 sense; it
   holds no behaviour yet.
2. **One tests branch per builder unit**, created from `native/main` right after the freeze:
   `native/s<N+1>-tests-<unit>-<date>`. It touches only test source sets, `native/fixtures/**` and the unit's
   `native/catalog/` notes. Several units never share a tests branch, so no builder ever inherits another unit's red
   tests.
3. **The builder branch is created from the tests branch head**:
   `git worktree add -b native/s3-agents-2026-12-08 /home/eslam/Storage/Code/wt/ocn-s3-agents native/s3-tests-agents-2026-12-01`.
   If `native/main` moved meanwhile, the builder merges it in.
4. **Merge rule** (02 §6.7 unit done, item 2): the coordinator merges the builder branch (which contains the tests
   branch) only when
   - every **T1/T2-proven** row of the unit is green at T1, and
   - every **T3-proven** row (`scope.jsonl` field `proof: t3`: `e2e`, the instrumented half of `migration`, and the
     rows the coordinator marks T3, for example B-ANDR-036 native-crash injection, B-SEC-073 device credential,
     B-KIT-054 motion, Keystore, proot, `PdfRenderer` and sherpa-onnx rows) has a test or flow step that compiles and
     fails for the reason the row states. These tests **do land on `native/main` before they pass**: T2 never runs
     them (they are outside T2's pass set), and they count only from the lane's next T3 on a `native/main` commit
     (§2.7, §6.1).
5. **Consequences:** T2 on `native/main` is a plain "everything T2 runs passes" gate. `native/LAST_GREEN` means what it
   says. "Pre-existing failure" can only mean a regression since the last green commit (memory
   `pre-existing-means-last-green`).

If a row cannot go green inside one unit (it needs two units), the coordinator splits it in `scope.jsonl` with
`split_from` (02 §6.1), or moves it to the later unit. A T1/T2-proven row never lands red.

### 2.3 Contract stubs

The test writer needs code to compile against before the builder starts. The contract freeze therefore ships stubs:

```kotlin
// native/engine/delivery/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/delivery/PromptDelivery.kt
// Stub from contracts/s2.md §Delivery (frozen 2026-11-24). Bodies are replaced by the unit native/s2-delivery-*.
public class PromptDelivery(
    private val gateway: ServerGateway,
    private val queue: OfflineQueueStore,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) {
    public val state: StateFlow<DeliveryState> = MutableStateFlow(DeliveryState.Idle)   // a plain initial value is allowed
    public suspend fun send(draft: OutgoingPrompt): Outcome<Receipt> = TODO("B-STATE-040")
    public suspend fun flushQueue(): Outcome<FlushReport> = TODO("B-STATE-039")
}
```

Class and method names in this section's code examples are illustrative. The slice contract is the source of the
real names.

Rules:

1. A stub body is `TODO("<one row id>")` (the Kotlin standard library function; it throws `NotImplementedError` with
   the row id in its message), or the plain initial value of a `StateFlow`. Nothing else.
2. `NoStubsLeftTest` (`:quality`, T2) is keyed by **row**, not by module, because modules span slices
   (`:android:linux` gets rows in slices 1 and 3; engines get rows in later slices). It reads the row id inside every
   `TODO("B-…")` in `commonMain` and `main` source sets and fails when that row's `scope.jsonl` slice is at or before
   the last closed slice. Stubs for rows of open slices may stay; the preview APK never reaches them, because routes of
   unbuilt features are absent (07 §2.8 "absent, never disabled").
3. Stubs never invent behaviour. A method the row needs but the contract lacks is a "Requests to owners" line in
   `native/contracts/s<N>.md` (02 §7.4). The coordinator amends the contract; the test writer waits for that.

### 2.4 Writing one row test, step by step (the test-writer recipe)

1. Print the row with everything it carries:
   `python3 -I native/tool/catalog/row.py B-STATE-040`
   It prints the `behaviors.jsonl` line, the `scope.jsonl` line (scope, slice, module, amendment), the flow that
   proves it (from `native/catalog/flow-rows.json`), and each Flutter `sources` path with the test name read at that
   line in the worktree the row names: `oc_app-batch1` for catalog rows, `oc_app-freeze` at the row's
   `sources_commit` for top-up rows (02 §3.5, §6.2), later `oc_app-flutter-final`.
2. Use the **amended** text when `scope.jsonl` has one (02 §3.6). Split the Given/When/Then into numbered clauses.
   Example for B-STATE-040: [1] the server accepted the send and the reply was lost; [2] the entry is parked for review;
   [3] no automatic resend, even after time passes; [4] exactly one request reached the server.
3. Read every Flutter source test at its line (read-only). Write down the inputs, fixtures, timers, expected values
   and what the Flutter test treated as "the same". Port the **assertion**, not the Dart structure (02 §7.6).
4. Find the home in §3.8 from the row's level and the module in `scope.jsonl`.
5. Copy literal test data verbatim. If the expected values come out of Dart code (encodings, orderings, schedules),
   generate them with the vector generator (§2.6). Never hand-compute a vector.
6. Write the test against the frozen public API and the fakes in `:core:testing` / `:ui:testing` (§3.3). If a fake
   is missing, it is a request to the S core owner, not a private fake in the test.
7. Name it and give it the header (§2.5).
8. Run it alone: `native/tool/t0.sh :engine:delivery 'B-STATE-040'`. It must fail with an assertion or a
   `NotImplementedError` naming a row of this brief (§2.7). A row the brief lists as `pre-green` passes instead, and
   gets its mutation proof (§2.7). An instrumented or flow row only has to compile here; it is judged at the lane's
   next T3.
9. Commit one commit per row group, subject `test(engine-delivery): <plain words> [skip ci]`, trailer
   `Behaviors: B-STATE-040, …` (02 §9.1).
10. When the unit's rows are all written: `native/tool/t1.sh --expect-red` on the tests branch, then the final report
    (02 §7.14) with the list of rows and the red reason (or the pre-green mutation proof) of each.

### 2.5 The test file template

```kotlin
// native/engine/delivery/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/delivery/DispatchMarkerRowsTest.kt
package io.github.eslamasabry.oc.engine.delivery

class DispatchMarkerRowsTest {

    /**
     * B-STATE-040 — A failure after the prompt was dispatched parks the entry for review instead of auto-retrying.
     * Given a prompt the server accepted, When the reply is lost, Then it waits for review and is never sent twice.
     * Clauses: [1] accepted then lost  [2] parked for review  [3] no automatic resend after time passes  [4] one request
     * Flutter: test/offline_queue_test.dart:387
     */
    @Test
    fun `B-STATE-040 a lost reply after dispatch parks the prompt and never resends`() = runTest {
        val gateway = FakeServerGateway().apply { acceptThenDropReply(onCall = 1) }            // [1]
        val delivery = PromptDelivery(gateway, FakeOfflineQueueStore(), testScheduler.asClock(),
            TestDispatchers(testScheduler))

        delivery.send(prompt("hello"))
        delivery.state.test {
            assertEquals(DeliveryState.NeedsReview(promptId("hello")), awaitItem())          // [2]
            advanceTimeBy(15.minutes)                                                        // [3]
            expectNoEvents()
        }
        assertEquals(1, gateway.calls.count { it is GatewayCall.Prompt })                    // [4]
    }
}
```

Rules, checked by `RowHeaderTest` (`:quality`, T1 and T2, a text scan of every test source set):

- A test whose name starts with a row id has a KDoc whose first line starts with the same id(s), a `Clauses:` line
  with at least one `[n]`, and a `Flutter:` line (or `Flutter: none (<reason>)` for rows written without a Flutter
  test, such as B-MIGR-004…013, catalog Appendix E).
- Each `[n]` in `Clauses:` appears at least once as a `// [n]` comment in the test body.
- `RowIdsExistTest` fails when a test name carries an id that is not in `behaviors.jsonl`, or whose scope is `dropped`.
  Typos cannot inflate parity.
- Names follow 02 §6.1: backticks with spaces in JVM tests, `B_STATE_040_…` in instrumented tests. A test that proves
  several rows starts with all of them.
- Assertions are `kotlin.test` with the power-assert compiler plugin on for `kotlin.assert` and the `kotlin.test`
  assert functions (the list is in §9.2; by default the plugin transforms only `kotlin.assert`), so a failure prints
  every sub-value.

### 2.6 Flutter-generated vectors (expected values computed by the reference build)

When a row's truth is a table of inputs and outputs that Dart code computes, the native test reads vectors produced
by **running the Flutter code**. The Flutter implementation becomes the oracle, case by case.

- **Where it runs:** a dedicated, writable worktree per source commit, `/home/eslam/Storage/Code/wt/oc_app-vectors-<sha>`,
  on a branch `tools/flutter-vectors-<sha>` **created at** that commit. The commit is either R (02 §6.2: `65e3f70a3`
  now, `flutter-final` later) or, for top-up rows, the row's `sources_commit` in `scope.jsonl` (02 §3.5: freeze-tip
  features exist only in `oc_app-freeze`). These branches are never merged anywhere. Agents still never edit
  `oc_app-batch1` or `oc_app-freeze`.
- **Generators:** `native/tool/flutter-fixtures/vectors/<family>_vectors_test.dart` (scripts live under
  `native/tool/`; `native/tools/` holds only Gradle `:tools:*` modules, §4.1). `run.sh <family>` copies the generator
  into the vectors worktree's `test/vectors/`, runs it with the pinned Flutter (AGENTS.md:
  `~/.shorebird/bin/cache/flutter/91f8bd75…/bin/flutter test test/vectors/<family>_vectors_test.dart`), and copies the
  JSON it writes to `native/fixtures/vectors/<family>.json`. It runs on the VPS or the PC; both have the pinned Flutter
  (memory `build-vps`).
- **Families that depend on the time zone** (`recurrence`, the schedule digest): the Dart code reads the **process**
  zone (`lib/scheduling/recurrence.dart` uses `after.toLocal()` and local `DateTime(…)`; `controller.dart` the same
  [V]). A plain run on the PC (Asia/Dubai, no DST) or the VPS would silently compute every DST case in the host zone.
  So `run.sh` runs the generator **once per zone** and merges the outputs:
  `TZ=Europe/Berlin flutter test test/vectors/recurrence_vectors_test.dart --dart-define=ZONE=Europe/Berlin` (and the
  same for UTC, America/New_York, Australia/Lord_Howe). The generator first asserts that the process really is in that
  zone (the offset on a known summer and winter date, for example `DateTime(2027, 7, 1).timeZoneOffset == 2 h` for
  Berlin) and fails otherwise, so a wrong `TZ` can never write a wrong vector. Each case records `zone` and the host's
  `tzdataVersion` (`dpkg-query -W tzdata`).
- **One mechanism for Dart values:** 05's byte-exact `jsonEncode` vectors (05 §2.5, `dart_vectors.dart`) are generated
  the same way into `native/fixtures/vectors/dartjson.json`, not into a `commonTest/resources` file (KMP `commonTest`
  has no resource loader). A request to the 05 owner, listed in §13.2.
- **File format:**

```json
{"family":"recurrence","sourcesCommit":"65e3f70a3",
 "generator":"native/tool/flutter-fixtures/vectors/recurrence_vectors_test.dart",
 "generatedAt":"2026-11-05","rows":["B-STATE-093"],
 "cases":[{"name":"daily 09:00 Europe/Berlin across spring-forward","zone":"Europe/Berlin","tzdataVersion":"2026b-0ubuntu0.24.04",
           "in":{"rule":"daily","at":"09:00","after":"2027-03-27T10:00:00Z"},
           "out":{"next":"2027-03-28T07:00:00Z"}}]}
```

- `VectorsProvenanceTest` (`:quality`) checks that every vector file names a generator that exists, at least one row,
  and a `sourcesCommit` equal to **either** `native/catalog/REFERENCE` (one line: the commit of R) **or** the
  `sources_commit` that `scope.jsonl` gives every row of the file. Zone-dependent families must carry `zone` on every
  case. When R changes, the fixture lane regenerates every family and the diff is reviewed like a behaviour change.

| Family | Rows (examples) | Native consumer |
|---|---|---|
| `dartjson` (byte-exact `jsonEncode`, fingerprints) | STATE fingerprint rows; 05 §2.5 | `DartJsonTest` (`:core:common`) |
| `redaction` | B-SEC redaction rows, B-NAV-004 titles | `RedactorVectorsTest` (`:core:security`) |
| `address-rules` | B-SEC-027 and the cleartext-consent rows | `AddressRulesTest` (`:core:security`) |
| `capability-defaults` | B-PROTO-124, B-AGENT-231 | `CapabilityDefaultsTest` (`:core:domain`; 03 §22.1 asks for this file) |
| `recurrence` (zones UTC, Europe/Berlin, America/New_York, Australia/Lord_Howe, one run each) | B-STATE-092, B-STATE-093 | `RecurrenceDstTest` (`:engine:schedule`) |
| `reconnect-backoff` (top-up row, `sources_commit` = freeze tip) | the `test/reconnect_backoff_test.dart` row (S-18) | `ReconnectBackoffTest` (`:engine:connection`) |
| `sse-frames` (the 9 vectors of `test/api2_sse_test.dart` + chunk-boundary cases) | PROTO stream rows | `SseParserTest` (`:core:net`) |
| `failure-keys` (failure → copy key, not text) | B-NAV-007, B-NAV-008, B-NAV-009 | `FailureWordsTest` (`:ui:words`) |
| `settings-search` | B-NAV-071 | `SettingsSearchTest` (`:feature:search`) |
| `model-recommendations` | B-NAV-033…036 | `ModelPickerRulesTest` (`:engine:integrations`) |
| `prose-for-speech`, `path-chips` | B-CHAT-060, B-CHAT-114, B-CHAT-026 | `:core:markdown` tests |

Add a family whenever a row has more than about ten input cases. A row with a few hand-readable cases keeps its literal
values in the test.

### 2.7 "Red for the right reason" (`t1.sh --expect-red`)

The tests branch passes its own gate when **every** row test of the brief fails for an accepted reason, or is an
approved `PRE_GREEN` row:

| Accepted outcome | Not accepted (the test-writer unit is not done) |
|---|---|
| `AssertionError` (power-assert output) | compile error; `ClassNotFoundException`; a missing fixture file |
| `NotImplementedError` whose message names a row of **this** brief | `NotImplementedError` naming another unit's row (the test reaches too far) |
| Turbine `AssertionError` (timeout, wrong item) | a coroutine test timeout with no assertion (a hung test) |
| Roborazzi verify difference (screenshot rows) | `UncaughtExceptionsBeforeTest`, setup crashes |
| **`PRE_GREEN`**: the test passes, and the brief lists the row as `pre-green: <frozen contract \| carried code>` with the coordinator's OK. Examples: `:core:model` data classes, the 85 `ServerCapabilities` defaults S0-CORE must already match, route keys, and carried Kotlin moved green (`:android:linux` in slice 1, whose JVM tests move with their subjects). For each such row the test writer records **one mutation proof** instead of a red run: break the subject on a scratch commit, watch the test fail, record the message, drop the commit (§2.8). | a passing row that the brief does not list as pre-green (a test that passes against a stub proves nothing) |

`t1.sh --expect-red` reads the JUnit XML, classifies each result with these rules (`:quality` `RedReasonCheck`, which
reads the brief's `pre-green` list and the mutation-proof lines in the unit's QA record), and exits 1 if a brief row
passes without being an approved pre-green row, or fails for an unaccepted reason.

**T3-proven rows** (§2.2 item 4) cannot be judged by T1. Their tests are compiled at T1 (`assembleStableQaAndroidTest`
on the VPS, or `lint_flows.py` for flow steps). At the lane's next T3, the T3 holder runs
`t3.sh --expect-red --rows <list>` for them, with the same accepted reasons, plus "a flow step that fails at the step
labelled with the row" for flows.

### 2.8 The builder's side

1. Start from the tests branch (§2.2), read the contract, make the red tests green. Never edit an assertion. A wrong
   test goes back to the test-writer lead, and the fix carries `Test changed because <reason>` (02 §9.1).
2. **Mutation proofs:** before the final report, the builder picks 3 rows of the unit (the coordinator may name them),
   reverts the one line that makes each row pass, runs that row's test, records the failing message, and restores the
   line. The three proofs go into the unit's QA record (02 §9.4). This is the native form of memory
   `verify-fixes-with-a-failing-test`, and it catches tests that pass for the wrong reason.
3. Every bug fix after merge follows the same rule: a test that was watched failing without the fix.

### 2.9 Kotlin tests that already exist

- **The baseline is the freeze tip, not batch1.** The twins come from `oc_app-freeze` (02 §3.5, §5.6): 52 JVM test
  files with 674 `@Test` at `fc5c1e78f` [V] (batch1 had 46 / 645, the list in 11 §4.3). When `flutter-final` is
  tagged, the baseline is re-counted there. 02 §10 item 11 records each file and its `@Test` count in `TWINS.md`.
- **JVM tests** move with their subjects into `:android:*` `src/test` (11 §4.3 lists the batch1 set; the six freeze-tip
  additions follow the twin rule). They keep their names. Where one already proves a catalog row, the mover adds the
  row id to the front of the test name and the header of §2.5, so it counts. They are JUnit 4 (Robolectric 4.17,
  JUnit 4.13.2), so their tasks run on the JUnit Platform **with the vintage engine** (§9.2).
- **Instrumented acceptance harnesses** (13 Kotlin files + `RuntimeQaGuard.java` [V]) are custom `Instrumentation`
  subclasses (`class BuiltinRuntimeAcceptance : Instrumentation()`), driven one step at a time with
  `am instrument -e step <s>` and the Python drivers under `tool/qa/bb*`. They have no `@Test`, so no JUnit wrapper can
  hold them. 11 §4.3 is followed exactly:
  - the 7 runtime acceptance classes (`BuiltinComponentFilesystemAcceptance`, `BuiltinComponentUpdateAcceptance`,
    `BuiltinIdleAcceptance`, `BuiltinRestoreAcceptance`, `BuiltinRuntimeAcceptance`, `BuiltinRuntimeReclaimAcceptance`,
    `BuiltinWorkLeaseAcceptance`) move to **`:androidApp` `src/androidTest`** and run on the **`qa` build type**. They
    stay there because they drive `MainActivity`, `BuiltinServerService` and the restore, reclaim and idle receivers,
    which a library module's test APK does not have;
  - `BuiltinRuntimeAcceptance` stays the `testInstrumentationRunner`; every other harness is declared as an extra
    `<instrumentation android:name=… android:targetPackage="io.github.eslamasabry.opencode_mobile">` element in
    `:androidApp`'s `src/androidTest/AndroidManifest.xml`, so `pm list instrumentation` shows each one
    [U14-13: the manifest merger keeps several `<instrumentation>` elements in the test APK];
  - `ProtectionMarkerAcceptance` (new in slice 1, 11 §4.3) joins them; the 3 `PhoneEngine*` classes follow D6; the
    3 `Bb8*`/`Bd9*` classes retire (E2E-17, 18 cover them).
- **Every acceptance step counts.** `native/android/acceptance-steps.json` lists every step of every harness (generated
  from the harnesses' step dispatch by a `:quality` scan, so a step cannot vanish unnoticed). `t3.sh` runs each step
  with `adb shell am instrument -w -r -e step <s> <pkg>.test/<Harness>` and writes one synthetic JUnit `<testcase>`
  per step from the result bundle: `name="B_LINUX_029_<step>"` when the step proves a row, otherwise
  `name="ACC_<Harness>_<step>"`. Every step runs in the slice-3 T3 and in every `full` T3; the parity report counts them
  under `kotlin_tests` (§6.4). A step present at the freeze tip and missing from the manifest fails
  `KotlinTestsRatchetTest`.
- **The Kotlin-test ratchet** (02 S-1, §6.5): `KotlinTestsRatchetTest` (`:quality`, T2) compares the moved JVM tests
  (by class and method name) and the acceptance steps with the baseline list in `TWINS.md`. It fails when one is
  missing, renamed without a `TWINS.md` line, or not green in the last T2 (JVM) or the last same-commit T3 (steps).
  `parity.json` gets `kotlin_tests: {total, green, deleted}`; `deleted` stays 0.

### 2.10 Rows that move during the rewrite

| Event | What happens | Who |
|---|---|---|
| Catalog top-up (02 §3.5): logic tests added on the freeze line after `65e3f70a3` | new rows with the next free id and a `sources_commit`; their tests are written in the slice that owns them; their vectors are generated at that commit (§2.6) | test-writer lead |
| **Flows top-up**, in parallel with the catalog top-up: switch-scope freeze-tip features with no device flow (02 §3.5) | new flows written and proven on R' (§4.7, §4.11), entered in `flow-rows.json`, `FlowRowsTest` and `slices.json` | flows lane |
| Flutter fix during F1 (02 §5.3, `Behaviors:` trailer) | the row is added or amended; the native test is written **in the same week**, red on a tests branch, green with its unit | test-writer lane |
| ADR or owner amendment (02 §3.6) | the native test asserts the amended text; the header quotes it | test writer |
| A row too big for one unit | split with `split_from` | coordinator |
| A row that turns out untestable as written | the test-writer lead writes the reason; the coordinator amends the row; never `@Ignore` | lead + coordinator |

### 2.11 Throughput and calendar [E, replaced by slice-0/1 measurements, ADR P10]

| Level | Rows/agent-day (test writing only) | Switch rows (§1, scaled from 02's 1,336) | Agent-days |
|---|---|---|---|
| unit | 20 | ≈ 586 | ≈ 29 |
| contract | 8 (recording included) | ≈ 231 | ≈ 29 |
| integration | 8 | ≈ 386 | ≈ 48 |
| migration | 3 | ≈ 56 | ≈ 19 |
| screenshot | 8 | ≈ 52 | ≈ 7 |
| **All** (e2e rows are the flows lane's) | | ≈ 1,311 + the catalog top-up | **≈ 132 (≈ 26 agent-weeks)** |

With two test-writer agents in parallel, that is about 13–14 calendar weeks, which fits "one slice ahead" over slices
1–5. The e2e rows and the new flows are the flows lane's work (§4.8, §4.11). If slice-1 throughput comes in more than
30 % under these numbers, the coordinator adds a third test writer before slice 3. The PC ceiling (memory
`pc-capacity-ceiling`) is not affected, because test writers run their checks on the VPS.

**Slice 0 has no slice before it.** Its ≈ 12 rows (Redactor, external-link, address-rule and capability-default
vectors; the BUILD and XPLAT rows `contracts/s0.md` assigns) get their own test-writer unit **W14-19** in W1, in
parallel with S0-CORE (W1–W2), so the test writer and the builder stay two different agents. Section 15's S0-TW1
(W2–W3) then writes the slice-1 tests as planned.

---

## 3. Per level: what a native test of each kind looks like

### 3.1 unit (677 rows)

- **Home:** the owning module's `commonTest` (runs as `desktopTest` on the JVM); Android-only helpers in the
  `:android:*` module's `src/test`.
- **Tools:** `kotlin.test` + power-assert, `kotlinx-coroutines-test` 1.11.0 (`runTest`, virtual time), Turbine 1.2.1
  for flows (ADR §8).
- **Shape:** pure function or reducer in, value out. State machines are tested as a table over
  `reduce(state, event) → Transition(state, effects)` (ADR §9.1):

```kotlin
@Test
fun `B-STATE-093 recurrence stays anchored and survives DST`() {
    val vectors = Vectors.load("recurrence")                 // :core:testing, reads <OcFixtures.ROOT>/vectors/recurrence.json
    for (case in vectors.cases) {
        val next = Recurrence.parse(case.input["rule"]).nextAfter(case.instant("after"), case.zone("zone"))
        assertEquals(case.instant("next"), next, case.name)  // [1] anchored  [2] DST
    }
}
```

- **Property tests** only for parsers and tolerant decoders: `kotest-property` 6.2.6 `checkAll` inside `runTest`
  (testing-perf §3.1). Example: random chunk boundaries over an SSE byte stream give identical events.

### 3.2 contract (267 rows)

A contract row is checked against **recorded bytes**: real server traffic for wire rows, or a recorded native call
for the bridge rows that used to cross a MethodChannel (for example B-ANDR-032, the four notification fields).

**04 §14 owns all of the wire side**, and this section does not define a second one:

- the fixture tree `native/fixtures/` (`oc2/flutter-v57/`, `oc2/2.1.1/<scenario>/`, `paseo/0.11.2/`, `codex/0.153.4/`,
  `coverage/` ledgers and samples, `capabilities/`, `genui/`, `host_agent_providers/`, `agent_auth/`, the pinned
  `openapi.json` snapshots) (04 §14.1);
- `FixtureEngine` with its fault knobs (04 §14.2), engine-level tests (04 §14.3), drift checks (04 §14.5);
- the recorder and the replay server, both in `:tools:contracts` (04 §14.4; the replay server answers the flows'
  failure cases, §4.4), and the live proofs (04 §14.6);
- the recording recipe: a scratch install of the pinned server on the VPS, `HOME` and XDG dirs in the scratch dir, the
  password from a generated file, the server started by absolute path, its listener found with `ss -ltnp` and stopped
  by that exact PID. Never on the PC against the owner's OpenCode data, and never against the owner's live servers.

**How tests find the files (one mechanism for every reader).** `WireFixtures`, `Vectors.load`, `catalogRow` and
`OcFixtures.ROOT` (04's name) all live in `:core:testing`, which is `commonMain`-only: no `System.getProperty` (K11
bans `System.` there), no `expect/actual` outside `:core:common`, and no `androidMain` (ADR §7.2). This section asks
04, 03 and the build-logic owner for one change, listed in §13.2: **`OcFixtures.ROOT` is resolved at run time in common
code**, not generated as an absolute path. With kotlinx-io, start at `SystemFileSystem.resolve(Path("."))` (the
working directory of `desktopTest` and `androidHostTest` is the module directory) and walk up until
`catalog/REFERENCE` exists; that directory is the `native/` root, and `fixtures/` and `catalog/` hang off it. An
absolute path baked into generated source would differ between `~/ocn/main` and `~/wt/ocn-*` and defeat the build
cache (§9.2). The JVM-resource route of 03 §10.8 and the system property of the first draft of this section are both
dropped.

**Two requests to 04's recorder** (§13.2), because the flows and the canary scan depend on them:

1. Add to the fail-the-recording patterns: `gho_`/`ghu_` tokens (04 already has `ghp_`), the value of any
   `auth_token` query parameter, and the base64 form of `opencode:<password>` (the `?auth_token=` shape the OC2 web
   client uses), next to 04's `sk-`, `ghp_`, `xox`, `AIza`, JWT and PEM patterns.
2. Set both `OPENCODE_PASSWORD` and `OPENCODE_SERVER_PASSWORD` from the generated file (the server reads the first and
   falls back to the second; the Flutter repo's scripts set both, for example `tool/qa/fq3/device.dart` [V]), and pass
   that file to the recorder's secret check, so the leak check never runs against an empty string. Scrub the OC2
   `/provider` and `/config` bodies (OpenCode 1's `/config/providers` route is not used by OC2).

**What the flows lane needs from 04's recordings, and when** (the fixture lane records one slice ahead; 04 WP-04.7
and section 15's S1-FIXT own the work):

| Server @ pinned version | Scenarios the flows and their contract rows lean on | Needed by |
|---|---|---|
| OpenCode 2 @ **2.1.1** | probe/health, auth 401, pairing payload, sessions paged, prompt round-trip (SSE), permission, question, form, abort, reconnect then refetch, providers (scrubbed), files, PTY WebSocket | slice 1 (PTY slice 5) |
| OpenCode 2 replay-only (04 §14.7) | the 2.0.29-style 400 on the pending list, 503, a stuck permission, a dropped stream mid-turn | slice 2 (E2E-03, E2E-06) |
| Paseo @ **0.11.2** [V `assets/agents/paseo-package-lock.json`] | daemon probe, agents, chat stream, permission, terminal stream, update versions | slice 3 |
| Codex app-server @ 0.153.4 | health, threads, a turn with an approval, steer, abort, socket drop | slice 5 |

When a pin moves (02 §5.6 twin rule), 04 re-records; N4 (§9.4) reports a pin bump without a re-recording.

### 3.3 integration (446 rows)

Several real parts together, fakes only at the edges. Same `commonTest` home; Android parts as Robolectric host tests.

**Fakes catalogue.** 03 §10.8 lists the base set: `TestDispatchers`, `TestScheduler.asClock()`, `FakeKeyValueStore`,
`FakeSecretStore`, `FakeBlobStore`, `FakeServerGateway`, `FakeOperationsGateway`, `FakeForegroundWork`,
`FakeDeviceFacts`, `WireFixtures`, `catalogRow()`, and `RecordingNavigator` in `:ui:testing`. 04 §14.2 adds
`FixtureEngine`; section 16 §1.2 adds `Canaries` (the one canary list every leak test uses, with `Canaries.ALL`). This
section requests these additions from the S core owner and the kit owner. Their signatures freeze with slice 0:

| Fake | Module | What it does | Rows that need it first |
|---|---|---|---|
| `Vectors.load(family)` | `:core:testing` | §2.6, through `OcFixtures.ROOT` (§3.2) | many |
| `FakeWebSocketSession` | `:core:testing` | replays `NNN-WS-*.jsonl` and asserts the outgoing frames in order (if 04 §14.2 does not already provide it) | PTY, Paseo, Codex contract rows |
| `FakeRuntimeHost` | `:core:testing` | scripted install/start/probe/stop results, a `/proc`-like process table, disk and RAM facts | LINUX rows of slice 3 |
| `FakeAgentHost` | `:core:testing` | scripted agent install, sign-in, readiness and process counts (32-process limit) | AGENT rows of slice 3 |
| `FakeActivityResults`, `FakePermissionGate` | `:core:testing` | scripted picker, camera and permission outcomes (ADR §7.3) | MEDIA, ANDR rows |
| `RecordingLogWriter` | `:core:testing` | a Kermit `LogWriter` behind the real `RedactingWriter`, keeping every line for assertions against `Canaries.ALL` | SEC, MIGR privacy rows |
| `TestFiles` | `:core:testing` | a temp directory per test (kotlinx-io paths), deleted after | STATE, MEDIA rows |
| `FakeKitPlatform` | `:ui:testing` | records haptic kinds, link opens, frame-rate votes; fixed insets and reduced motion | KIT rows |

**Robolectric (Android-only behaviour):** Robolectric 4.17 on a JDK 21 test toolchain, SDK 37 (ADR P7). Real
`SharedPreferencesImpl`, `ShadowNotificationManager` for posted notifications, `work-testing` `TestDriver` for
WorkManager, `ShadowAlarmManager` for alarms, `ApplicationProvider` for the graph smoke. Robolectric and
`createComposeRule` tests are JUnit 4; their tasks need the vintage engine (§9.2).

**What needs the emulator anyway** (instrumented or scripted, T3; each has a recipe in §8):

- the real `AndroidKeyStore` (05 §10.3; testing-perf §13 item 8);
- proot running real binaries (the `:androidApp` acceptance harnesses, §2.9);
- sherpa-onnx loading its native library; the isolated `PdfRenderer` service;
- widgets through a real host and the notification shade (UIAutomator 2.4.0, 11 §11.6; R10);
- OS limits the emulator can provoke on demand: the Android 15+ `dataSync` 6-hour cap and `onTimeout`, the
  `shortService` timeout, the phantom-process limit, thermal status, time and time-zone changes, a revoked exact-alarm
  permission (R17–R20). e2e-flows calls these "manual or lab"; on the emulator they are scripted.

### 3.4 migration (65 rows)

Section 05 §10 owns the tests: the JVM tests over a real Flutter dump, the instrumented Keystore tests, and journeys
J1–J8. This section adds three things:

1. **The seed flows are flows-lane work.** `native/e2e/seed/flutter-final.yaml` (05 §10.4 step 4) and
   `native/e2e/upgrade/*.yaml` are written and proven on Flutter R exactly like the parity flows (§4.7). The seed's
   Part C assertions run on Flutter first (E2E-39 Part B).
2. **Every other migration-level row** (Appendix D of the catalog: 22 STATE, 9 SEC, 15 LINUX and others) gets an
   `AdoptedKeysFixtureTest` assertion in its owning engine (05 §10.1) that reads the **real dump** from
   `native/android/migration/src/test/resources/fixtures/flutter-final-<VC>/`. A synthetic map written by the test
   writer does not count for a migration row.
3. **The journeys sit in the T3 tiers of §4.8**, on `OCN_U`, as 05 §10.5 schedules them: J1, J1b, J2 and J3 in
   `upgrade` (every candidate that touches migration code and every owner APK until the switch, ADR §10.2), from every
   owner-lineage seed; J1c, J3b, J4, J4b and J5–J8 in `full` and on the switch candidate; the public-lineage J1 + J3 on
   the switch candidate only (§4.10).

### 3.5 screenshot (60 rows)

| Area | Rows |
|---|---|
| KIT (26) | B-KIT-001, 003, 004, 005, 007, 010, 011, 012, 014, 015, 016, 019, 023, 027, 028, 029, 031, 032, 036, 042, 043, 044, 045, 055, 058, 060 |
| NAV (15) | B-NAV-014, 019, 023, 027, 029, 037, 044, 047, 048, 052, 055, 060, 062, 074, 079 |
| CHAT (6) | B-CHAT-004, 016, 056, 061, 063, 123 |
| ANDR (4) | B-ANDR-008, 018, 042, 043 |
| AGENT (3), LINUX (3) | B-AGENT-039, 210, 294; B-LINUX-172, 179, 238 |
| SEC, L10N, XPLAT (1 each) | B-SEC-133; B-L10N-018; B-XPLAT-003 |

**Where screen-level captures live.** 03 S15 turns on `androidHostTest` (`withHostTest`) only in KMP modules with an
`androidMain` (`:core:common`, `:core:data`, `:core:db`, `:core:net`, the three kit modules, `:ui:shell`, `:ui:testing`).
`:feature:*` modules have `commonMain` and `commonTest` only, so the NAV, CHAT, AGENT and LINUX screenshot rows (for
example B-NAV-037, a `:feature:chats` behaviour) have no Robolectric/Roborazzi home there. This section asks for **one
host-test module, `:ui:screenshots`** (`oc.kmp.compose` with `withHostTest`, no `commonMain` code of its own, depending
on the feature modules and `:ui:testing`). It renders `XScreen(canned XUi, FakeXActions)` for every screen-level
capture: the screenshot rows of features, 06 §15.3's "every feature screen preview" rows, and later the widget phase's
Android-only seams (§5.3). Goldens live under `ui/screenshots/src/screenshots/rows/<feature>/`. Kit rows stay in their
kit module's `androidHostTest` (06 §15.3). A request to 03, 06 and the build-logic owner (§13.2).

A screenshot row's native test is one Roborazzi capture test (or a few), named after the row, rendering the state the
row describes from canned `XUi` (or kit parameters):

```kotlin
// native/ui/screenshots/src/androidHostTest/kotlin/io/github/eslamasabry/oc/ui/screenshots/chats/ChatsRowsTest.kt
@Test
fun `B-NAV-037 phones have no bottom navigation bar`() {
    composeRule.setContent { TestShell(window = Phone412, state = ConversationsUi.sample()) }
    composeRule.onAllNodes(hasTestTag("bottom-navigation")).assertCountEquals(0)          // [1] semantics first
    composeRule.onRoot().captureRoboImage("src/screenshots/rows/chats/B-NAV-037-phone-light-en.png")   // [2] the look
}
```

- **Semantics first:** whatever the row says is *shown* or *absent* is asserted through semantics, so the test proves
  behaviour even before a golden exists.
- **The golden rule:** a capture becomes a golden only with an accepted visual verdict (§7.3). `ScreenshotVerdictTest`
  (`:quality`, T2) fails when a committed golden under any `src/screenshots/rows/` has no verdict line with its SHA-256
  in `docs/qa/native/visual-verdicts.jsonl`. A re-record needs a new verdict.
- **Variants:** light/en always; dark/ar when the row mentions direction, Arabic or RTL; **text scale 2.5** (the app's
  root cap, 02 S-25 and S-28) when it mentions text size, with a semantics assertion that frame text measures at most
  1.3× its 1.0 size while reading text keeps scaling (B-KIT-052; owner rule `text-scale-cap`); 1280×800 when it mentions
  tablets (06 §15.3 matrix, cut down to what the row claims).
- Recorded and verified **on the VPS only**, the single golden host (ADR §8). A PC compare against VPS goldens is
  informational only and never evidence: the VPS's EPYC and the PC's i7 rasterise glyph edges about 0.2 % differently
  (memory `build-vps`), so a PC compare always shows small diffs.

### 3.6 e2e (29 rows) and the 240 device-flow rows (≈ 224 in switch scope, 02 §3.4)

- The 29 `e2e`-level rows have **no** JVM test. Only their flows prove them, so they are `proof: t3`: B-NAV-005, 024,
  050, 054, 057, 059, 061, 068, 069, 070, 073; B-CHAT-020; B-AGENT-031, 067, 098, 276; B-PROTO-046, 088; B-ANDR-011,
  027, 028, 050, 109; B-LINUX-029, 069, 113, 119, 125, 151.
- The other 211 `portable_e2e` rows have a JVM test at their own level **and** a flow. `scope.jsonl`'s `proof` field
  decides how they count: a `t1` row is green from its JVM test, and its flow result goes into the T3 ratchet (§6.3);
  a row the coordinator marks `t3` needs both, on the same commit (02 §6.3, §6.4).
- §4 is the whole device-flow system.

### 3.7 Kit and UI tests that are not ports (the line between "now" and "later")

New native parts get their own tests now: capacity, accessibility, reduced motion, recomposition counts and the
capture matrix (06 §15, 08 §9.3; 06 Q2's default). They are the kit's definition of done. They are **not** ports of
Flutter widget tests, and they carry no catalog id unless a KIT row names the behaviour. Porting the behaviours of the
7,967 Flutter widget tests is the deferred phase of §5. A builder who notices that a screen lacks a behaviour that
a Flutter widget test protects writes it down in the unit's QA record under "Widget-test candidates". It does not
port the test.

### 3.8 Level → where, how, when

| Level | Home | Tool | Tier | Machine | Counts for the row when |
|---|---|---|---|---|---|
| unit | `commonTest` (`desktopTest`); `:android:*` `src/test` for Android-only | kotlin.test, coroutines-test, Turbine | T0, T1, T2 | VPS (T0 also PC) | passed in the last T2 |
| contract | `commonTest` of `:protocol:*` / `:core:net`; schema check in `desktopTest` | `FixtureEngine` (04 §14.2), networknt | T1, T2 | VPS | passed in the last T2 |
| integration | `commonTest`; Robolectric in the `androidHostTest` of S15 modules or `:android:*` `src/test` | fakes §3.3, Robolectric 4.17 (JDK 21) | T1, T2 | VPS | passed in the last T2 |
| integration, script (§3.9) | `desktopTest` of the module that ships the script | `ProcessBuilder` over dash and bash | T1, T2 | VPS (preflight) | passed under **both** shells in the last T2 |
| migration | `:android:migration` `src/test` (Robolectric, real dump) + `androidTest` (Keystore) + journeys | Robolectric, AndroidJUnit4, Maestro | T2 + T3-upgrade | VPS + PC | JVM part in T2 **and** the instrumented part in a T3 on the **same commit** (02 §6.3) |
| screenshot | kit rows: the kit module's `androidHostTest`; screen rows: `:ui:screenshots` `androidHostTest` (§3.5) | Roborazzi 1.77.0 | T1, T2 | VPS only | verify passed in the last T2 and the golden has a verdict |
| T3-proven rows of any level (`proof: t3`) | `:androidApp` `androidTest` (qa build type) or a flow step | AndroidJUnit4, UIAutomator 2.4.0, the acceptance harnesses, Maestro | T3 | PC emulator | passed in a T3 on the **same commit** as the T2 being reported; otherwise `stale` |
| e2e | `native/e2e/flows/**` | Maestro 2.12.0 + runner | T3 | PC emulator | its flows passed on the release build in a T3 on the same commit (§6.1) |

### 3.9 Script tests (the bundled shell and Python scripts)

About 150 rows come from Dart tests that run the app's bundled scripts with real `dash`, `bash`, `python3`, `curl` and
`sha256sum` (`termux_scripts`, `agent_removal_scripts`, `phone_agent_scripts`, `setup_scripts`, `host_script_pin`,
`host_script_install`, `git_script`, `claude_install_script` and others; §1). builtin-linux T14 requires every script to
run under both dash and bash. `commonTest` cannot hold them (it also compiles for iOS and has no process API).

- **Home:** the `desktopTest` source set (JVM only) of the module that ships the script as a resource (for example
  `:engine:runtime` for `runtime/**/*.sh`). Tests start the asset script with `ProcessBuilder` from a `TestFiles`
  directory, once under `dash` and once under `bash` (a parameterised test, both names carrying the row id).
- **No silent skips:** several Flutter tests skip when a tool is missing, and 02 §6.3 counts a skip as red. So
  `native/tool/preflight_scripts.sh` runs before T1 and T2 and **fails the run (exit 2), never skips**, when `dash`,
  `bash`, `python3` (≥ the version pinned in `TWINS.md`), `curl`, `sha256sum` or `node` ≥ 20 (09's
  `CardJsTwinCorpusTest`) is missing on the VPS.
- **Shellcheck:** `shellcheck -s sh` (and `-s bash` for bash-only scripts) runs at T1 on every changed asset script.

---
## 4. Device flows: Flutter first, native second

The 39 flows of `../catalog/e2e-flows.md` are the device half of parity (ADR §10.1 item 2; 02 S-2). Each flow is
written and proven on the Flutter reference build R **before** the native slice that needs it starts. Then the same
file, byte for byte, judges the native release build.

### 4.1 Layout

Path rule for the whole native tree: **scripts live under `native/tool/`** (t0–t4, e2e, device, ci, catalog,
flutter-fixtures, upgrade); **`native/tools/` holds only Gradle `:tools:*` modules** (`:tools:strings`,
`:tools:contracts`), because Gradle maps `:tools:x` to `tools/x`. 05 and 15 still name `native/tools/flutter-fixtures`
and 15 names `native/e2e/runner/`; both are asked to follow this rule (§13.2).

```
native/e2e/
├── README.md                          how to run; points here
├── maestro.version                    2.12.0 + SHA-256 of the CLI zip from the GitHub release cli-2.12.0
├── flows/<area>/E2E-NN-<slug>.yaml    the flows (37 + the switch-scope flows of §4.11; E2E-28 and E2E-32 follow D6)
├── flows/<area>/E2E-NN-<slug>-qa.yaml companions for steps that need a QA hook (§4.4), run on stableQa
├── flows/_sub/*.yaml                  shared subflows: connect-fx-oc2, open-settings, open-chat, send-prompt, open-shade …
├── flows/_fx/*.js                     fixture control calls for [fx] steps (Maestro runScript, http.post to the fixture proxy)
├── flows/_fx/adb_hook.js              the one bridge for [adb] steps (§4.2 rule 8)
├── sweep/S-<route>.yaml               one tiny flow per route, generated from 07 §5's screen inventory (§4.8, R24)
├── seed/flutter-final.yaml, seed/surfaces.py     05 §10.4
├── upgrade/partA-seed.yaml, upgrade/partC-assert.yaml, upgrade/J*.yaml   E2E-39 and 05 §10.5
├── strings/strings.en.json, strings.ar.json, android.en.json, android.ar.json, extra.en.json, extra.ar.json   §4.3
├── fixtures/                          FX-* servers, ports.json (the only list of ports), fixture-data.json (§4.4)
├── helper/                            the e2e helper APK: widget host, clipboard image, accessibility event recorder (R10, R22, R24)
├── avd/create.sh, avd/roles.json, avd/README.md    §4.6
├── refs/manifest.json                 screenshot references (§7)
├── known-gaps.md                      every KNOWN-FLUTTER-GAPS step: row, step, reason, decision
├── slices.json                        per flow: slice, AVD, tier, `phone` (allowed | never), step ranges that earn
│                                      credit early (§4.9), and its Flutter proof (reference R or R', APK SHA-256, date,
│                                      flow-file SHA-256) or `flutterProof: "n/a (qa hook)"` with a decision id
└── build/                             git-ignored: strings.js for the run, outputs
native/catalog/flow-rows.json          row → flows and steps, generated from BEHAVIORS.md's e2e column by rows.py
native/tool/e2e/   run.py  gen_strings.py  lint_flows.py  rows.py  scrub.py  pair.py  a11y_sweep.py  gen_sweep.py
native/tool/device/                    the recipes of §8
native/tool/upgrade/                   manifest.sh, runtime-paths.txt (S-4, R7)
```

### 4.2 Flow file rules

```yaml
# E2E-05 Send a prompt, watch it stream, stop it, search it, follow links
# slice: 2   avd: A   fixtures: FX-OC2, fx-proxy   snapshot: base
# rows: B-CHAT-017, B-CHAT-060, B-CHAT-086, B-CHAT-115, B-SEC-064, B-CHAT-064, B-CHAT-082, B-PROTO-061, B-PROTO-034
# KNOWN-FLUTTER-GAPS: none
appId: ${APP_ID}
tags: [parity, slice-2, chat, smoke]
---
- runScript: ../../build/strings.js
- runFlow: ../_sub/connect-fx-oc2.yaml
- tapOn:
    text: ${output.fx.projectApp}
    label: "S1 open project app [B-CHAT-017]"
- assertVisible: ${output.re.starterWhatChanged}
# S2 [B-CHAT-060] … steps copied from e2e-flows.md as comments, then the commands
```

Checked by `native/tool/e2e/lint_flows.py` (T1 when a flow changes; T2 always):

1. The header has `slice`, `avd`, `fixtures`, `rows`, `KNOWN-FLUTTER-GAPS`. `rows` equals the flow's entry in
   `native/catalog/flow-rows.json`. `FlowRowsTest` (`:quality`) checks that the union of all flows' rows equals the
   set of `portable_e2e` rows **read from `scope.jsonl`** (switch scope plus the deferred rows of E2E-28/32, plus the
   rows of the §4.11 flows), never a number written in a test.
2. `appId: ${APP_ID}`, never a literal package [U: Maestro interpolates env in `appId`; fallback: the runner writes a
   copy with the literal].
3. **Selectors:** `${output.s.<key>}` (exact text) or `${output.re.<key>}` (pattern) from the strings table, or
   `${output.fx.<name>}` from `fixtures/fixture-data.json`. Any other literal needs a `# literal-ok: <reason>` comment.
   `id:` selectors are banned in parity flows (02 §6.6; testing-perf §6.2).
4. Every step's first command carries `label: "S<n> <words> [<rows>]"`, so a failure names the step and its rows in
   the report [U: label support in 2.12.0].
5. **App data is never cleared where it matters.**
   - No `clearState: true` in a flow that needs built-in Linux (the rootfs lives in app data). Such flows start from a
     snapshot (§4.6).
   - A flow that clears app data (E2E-01, step 1) carries `phone: never` in `slices.json`. `run.py` refuses
     `clearState` and `pm clear` on any serial that is not an `OCN_*` AVD listed in `roles.json` (02 §6.7 owner phone
     test, step 3; 02 §7.9).
6. Waits are `extendedWaitUntil` with a timeout of 60 s or less (300 s for Linux setup steps), never a sleep.
7. `retry:` (Maestro's command; `maxRetries` 0–3 [V docs.maestro.dev]) is allowed only around system UI that is not
   ours (the notification shade, permission dialogs, the system install dialog, the launcher), with `# retry-reason:`.
   Never around an assertion about our app (§10).
8. **`[fx]` and `[adb]` steps use the same bridge.** A Maestro flow is one `maestro test` process, so a YAML comment
   cannot pause it for the host. Both kinds of step are a `runScript` call:
   - `[fx]`: `runScript: ../_fx/<action>.js` → `http.post` to the fixture proxy's control port.
   - `[adb]`: `runScript: {file: ../_fx/adb_hook.js, env: {HOOK: <name>, ARG: <value>}}` → `http.post` to
     `http://127.0.0.1:<hook port>/hook/<name>` (port from `ports.json`, default 8897; Maestro's JavaScript runs on the
     PC). `run.py` hosts that endpoint, runs the matching adb command **synchronously** against the run's serial from a
     fixed allowlist of hooks (`proc-tree`, `clip-image`, `mic-feed`, `camera-qr`, `media-seed`, `notif-shade`,
     `notif-dump`, `airplane`, `fill-disk`, `a11y-dump`, `revoke`, `date-set` …; R9–R24), and returns JSON. The script stores it in
     `output.hook.<name>`, and later steps assert on it (`assertTrue: ${output.hook.procTree.orphans == 0}`).
   - Steps Maestro can do itself use Maestro commands, not hooks: `setClipboard` for text, `stopApp`/`killApp` then
     `launchApp` for force-stop and relaunch, `setAirplaneMode` [U14-14: each command in 2.12.0].
   - `lint_flows.py` fails a flow where a `# adb:` comment is not followed by that `runScript`, or names a hook outside
     the allowlist.

### 4.3 Strings table and copy parity

- **Sources:**
  - the frozen ARB of R (`lib/l10n/app_en.arb`, `app_ar.arb`, read from the reference worktree). The ARB → XML
    conversion (03 §21.4) is a copy of the same text, so one table serves both apps;
  - the **Android resource strings** of R: every `<string>` and `<plurals>` in `android/app/src/main/res/values/*.xml`
    and `values-ar/*.xml` (143 in `strings.xml` alone [V], plus `widget_v2_strings.xml`, `scheduled_prompts.xml` and
    others). They hold what E2E-07, 08, 25 and 26 assert on widgets, tiles, shortcuts, notification channels and the
    shade (`widget_label`, `tile_need_you`, `shortcut_*`, `native_live_channel` …). Their keys get the prefix
    `android.` in the table.
- `native/tool/e2e/gen_strings.py --arb <R>/lib/l10n --android-res <R>/android/app/src/main/res --out native/e2e/strings/`
  writes `{"key": {"text": "…", "re": "…"}}`. `{name}` and `%1$s` placeholders become `.*` in `re`. ICU plural and
  select forms (and `<plurals>` items) become an alternation of all forms, with `\d+` for the count. Arabic keeps all
  six plural forms.
- `extra.<lang>.json` holds labels the Flutter app hard-codes in Dart rather than the ARB (`docs/localization-todo.md`
  lists them [R]). The flows lane records each one from the first Flutter run with its Dart path. The l10n owner adds
  it to `:ui:words` with the same text.
- At run time `run.py` writes `native/e2e/build/strings.js` for the run language:
  `output.s = {…text}; output.re = {…re}; output.fx = {…fixture data}`.
- **Copy-parity gate** `FlowStringsFrozenTest` (`:quality`, T2):
  - For every key the flows use (`lint_flows.py` writes `native/e2e/strings/used-keys.txt`), the native en and ar
    value equals the frozen value after placeholder conversion (`{name}` ↔ `%1$s`): ARB keys against the converted
    `:ui:words` XML, `android.` keys against the native `:android:res` and module `res/values{,-ar}` XML (02 §5.5 keeps
    these OS-visible names).
  - The key map comes from the one-off conversion. This needs `:tools:strings convert` to write
    `config/arb-key-map.tsv` and an `android.` section for the resource strings (a request to the l10n owner).
  - A difference fails T2. Changing an asserted string needs the owner's OK and a `native-only` flow variant
    (02 §6.6, NG11).

### 4.4 Fixtures

All fixture servers run on the PC. They bind `127.0.0.1`; the emulator reaches them as `10.0.2.2`. They are started
by `run.py` (§4.5) and stopped by **exact PID** (AGENTS.md, memory `pgrep-self-match-wait-trap`).

- **`native/e2e/fixtures/ports.json` is the only list of ports.** Section 16's egress harness names the OC2 fixture at
  `10.0.2.2:4096`; it reads `ports.json` instead (FX-OC2 is 4097, the e2e-flows contract), a request in §13.2.
- **Secrets are the fixed canary values**, not random per-run passwords. Every fixture serves section 16's `Canaries`
  (§1.2 there): FX-OC2 and FX-OC2-B use `Canaries.SERVER_PASSWORD`, `/provider` and `/config` return
  `Canaries.PROVIDER_KEY` in every key-shaped field, FX-GH issues `Canaries.GITHUB_TOKEN`, FX-PASEO uses
  `Canaries.PASEO_PASSWORD`, pairing codes carry `Canaries.PAIRING_SECRET`. Each value is unique, so a scan of
  everything the app wrote cannot hit by accident, and a leak is always reported, never scrubbed away (§4.5 steps 6.5 and 9).
  The fixtures bind loopback only, so a fixed password costs nothing.
- **Who builds them:** section 15's S0-FX (Codex, W1–W3) builds every fixture below, as 15 plans; W14-7 and W14-8 are
  its acceptance lists (§12). Sonnet writes the Python stubs when Codex asks.

| Id | Implementation | Port(s) [E, final in ports.json] | Flows | Needed by |
|---|---|---|---|---|
| FX-OC2 | real `opencode2 serve` at the pinned **2.1.1**, HTTP Basic `opencode` + `Canaries.SERVER_PASSWORD`; projects `app` (Git, `README.md`, `notes.txt`, `data.csv`, `lib/main.dart`, a binary) and `other`; model = `stub_model.py`; 09's `oc-ui` helper (`server.cjs`, copied byte for byte from the reference build's gen-UI install scripts) registered as an MCP server in its config | 4097; model 4299 (4199, `mock_llm.py`'s default, is already taken on the PC [V `ss -ltn`]) | most | W1 |
| `stub_model.py` | extends `tool/qa/issue87/mock_llm.py` [R] (OpenAI-compatible, deterministic): the `e2e:*` prompts of e2e-flows (echo, long, cut, slow-finish, ask-edit, ask-shell, question/choice/choice4, secret, files-edit; tool calls for permissions), **`e2e:card-choice`, `e2e:card-form`, `e2e:card-photo`** (each emits one `oc-ui_show` tool call with a card v1 payload; E2E-40), `e2e:tools-loop` (a long run of tool calls for S-19, R21), and 09's `perf:stream-6000`, `perf:history-5000`, `perf:tools-200` (13 §3.8) | 4299 | all chat flows; T4 | W1 |
| FX-OC2-B | second FX-OC2 with its own data dir, shared session ids, a placeholder ISO title and a leaked tool-call title | 4098 | E2E-21, 22, 35, 36 | W1 |
| fx-proxy | `fx_proxy.py`: the emulator's HTTP proxy (`emulator -http-proxy http://127.0.0.1:8899`; the docs say it carries "all TCP connections" [V developer.android.com], U14-3 proves it for both apps); control API on `127.0.0.1:8898` to cut, throttle or refuse by host, and to drop a stream mid-reply. HTTPS passes as a `CONNECT` tunnel, so throttling and cutting work **without TLS interception**. It logs every host the app contacted (R11). | 8899, ctl 8898 | E2E-05 s9, E2E-10, 13, 17, 19 | W1 |
| Replay server | `:tools:contracts replay --root native/fixtures` (04 §14.4) for failures a real server cannot produce on demand | 4300 | E2E-03, 06; 13 PR-02/04 | W1 |
| FX-TLS | Python `ssl` reverse proxies in front of FX-OC2: trusted (the PC's `<host>.ts.net` name with a `tailscale cert` certificate, emulator started with `-dns-server 100.100.100.100` [U]), self-signed A and B (swappable), expired, a redirector, plain HTTP on a private address, plain HTTP on a public-looking name | 8443–8449 | E2E-03, 04 | W1 |
| FX-PASEO | Paseo daemon **0.11.2** with scripted agents (`fake-claude`, `fake-pi`: shell scripts that speak each agent's stream format), Claude history files on disk, terminals | ports.json | E2E-30 | W2 |
| FX-NPM + FX-AUTH | a static npm registry (packuments + tarballs) serving fake agent packages whose sign-in command prints FX-AUTH's fixed code; reached by the `stableQa` build through `QaHooks.npmRegistryUrl` | ports.json | E2E-31 (`-qa` companion) | W2 |
| FX-LINUXDL | local mirror of the pinned Ubuntu rootfs, OpenCode and tools, with the same SHA-256 pins, and the cases a real host cannot give: corrupt checksum, truncated archive, wrong size | 8700 | `-qa` companions of E2E-17 and 19 only | W2 |
| FX-GH | GitHub device-flow stub **and an update feed**: `releases/latest` with an APK asset and `SHA256SUMS`, switchable through its control API to a bad checksum, a wrong-signer APK (signed with a throwaway key), and a download cut halfway | 8701 | E2E-15 s4, E2E-39 s13, **E2E-41** | W2 |
| FX-GIT | bare repo over `git daemon` / smart HTTP | 9418 / 8702 | E2E-15 | W3 |
| FX-CODEX | `tool/qa/codex_fixture/fixture_server.py` [R], copied as a twin, plus a scripted turn with an approval, steer, abort and socket drop | ports.json | E2E-29 | W3 |
| FX-USAGE | Codex and MiniMax usage stubs: reading, expired token, offline | 8703 | E2E-33 | W3 |
| FX-NODE | memory-node stub | — | E2E-32 | after the switch (D6) |
| run.py hook endpoint | the `[adb]` bridge (§4.2 rule 8) | 8897 | every flow with `[adb]` steps | W1 |

**Hosts the apps hard-code.** Flutter R hard-codes `https://github.com/login/device/code` and
`https://github.com/login/oauth/access_token` [V `lib/domain/git/github_device_flow.dart`], the update feed, the Ubuntu
base download (`https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/…` [V `BuiltinLinux.kt:4445`]) and the
npm tarballs of agents and OpenCode (`https://registry.npmjs.org/…` [V `lib/domain/agent_catalog.dart:286`]), with no
override. A stub cannot stand in for them without intercepting TLS, which this plan does not do. So:

- **Main flow files, on both apps:** Linux setup and agent installs use the **real pinned hosts**. fx-proxy throttles
  and cuts them by host through the `CONNECT` tunnel (E2E-17's slow and cut steps, E2E-19). The `flutter-linux`
  snapshot is built the same way (§4.6). This needs the PC online; a run without network exits `2`.
- **`-qa.yaml` companions, native only, on `stableQa`:** the steps that need a stub (a corrupt checksum, a fake agent's
  sign-in, the GitHub device flow, an update feed) read base-URL overrides from `QaHooks` (below).
- **On Flutter R** those steps are `KNOWN-FLUTTER-GAPS` (no override exists, coordinator OK). Their rows keep their
  Flutter unit tests as the reference (B-SEC-084 for the GitHub sign-in, B-MIGR-012 for the update notice, the LINUX
  checksum rows, the E2E-31 sign-in rows). A companion can therefore never have a Flutter proof: `slices.json` gives it
  `flutterProof: "n/a (qa hook)"` with the decision id, and `run.py` accepts that entry instead of a `flowSha256` match
  (§4.9).
- A flow counts as green when its main file passes on `stableRelease` **and** its companion passes on `stableQa`.

**`QaHooks`** (the test-facing contract of 11 §4.3's `qa` build type and `AppBuildInfo.qaHooks`; a request to the
coordinator, who owns `:androidApp`; it freezes in slice 0, so every field below is in from the start):

```kotlin
// :core:domain (read by engines through AppBuildInfo); the implementation that reads the file exists only in src/qa/
public data class QaHooks(
    val stepInterrupter: Boolean = false,      // 05 J2: stop after the step named in files/native/qa-kill-after
    val builtinRuntime: Boolean = false,       // 11: BuiltinRuntime* acceptance steps
    val holdRuntimeRestoreUntilManifest: Boolean = false, // 02 S-4: after migration, wait for the after-manifest (R7)
    val downloadBaseUrl: String? = null,       // FX-LINUXDL: rootfs, OpenCode, tools, voice packs; SHA-256 pins still checked
    val npmRegistryUrl: String? = null,        // FX-NPM: agent and OpenCode packages (E2E-31 companion)
    val githubBaseUrl: String? = null,         // FX-GH device flow
    val updateFeedUrl: String? = null,         // FX-GH releases/latest for AppUpdater (B-MIGR-012, E2E-41)
    val hapticLog: Boolean = false,            // 08 H-1..3
    val strictMode: Boolean = false,           // 02 S-21: penaltyLog for disk and network on the main thread (R23)
    val testTagsAsResourceId: Boolean = false, // UIAutomator handles in androidTest
) { public companion object { public val None: QaHooks = QaHooks() } }
```

A separate `agentAuthBaseUrl` is not needed: the fake agent packages carry FX-AUTH's address themselves.

- **The file:** the app is force-stopped first (it reads the file once at process start). Then, as root:
  `cat > /data/data/$PKG/files/native/qa-hooks.json` (the pattern of memory `restore-proof-recipe`), followed by
  `chown $(stat -c %u:%g /data/data/$PKG/files) …/qa-hooks.json`, `chmod 600 …` and `restorecon …`. A file created
  by the root shell is owned by root and lacks the app's SELinux categories, so without these three the app cannot read
  it and silently runs with `QaHooks.None`.
- **Proof it was read:** the `stableQa` app logs one category-only line, `OCQA qa-hooks loaded fields=<n>` (no values).
  `run.py` asserts that line in logcat before it runs a companion, and fails the run (exit 2) without it.
- **Only `stableQa` reads it.** Every other build binds `QaHooks.None`, and a Konsist rule in the spirit of 13's K-P10
  keeps `src/qa/` out of `src/main/`.
- **The variant:** `stableQa` = the `stable` flavor × the `qa` build type: package
  `io.github.eslamasabry.opencode_mobile`, R8 on, built on the VPS and re-signed on the PC with the **local key** like a
  release build. It has to install over Flutter R and over `stableRelease` (05 J1–J2). 13 §3.2 lists `qa` as
  `….native` with debug signing; §13.2 flags that difference.

### 4.5 The runner (`native/tool/e2e/run.py`)

One script drives both apps (e2e-flows "runner contract"). It is Python 3 standard library only, run with `-I`.

```
run.py --app flutter|native --apk PATH [--variant stableRelease|stableQa|previewRelease]
       --avd A|S|T|U|A11Y|16K|36|26  (--tier smoke|slice|full|switch|upgrade | --flows E2E-05,E2E-06 | --tags slice-2)
       [--steps E2E-21:S1-S7] [--lang en|ar] [--animations off|on] [--repeat N] [--no-retry]
       [--resume <out>] [--out build/native-t3/<sha>/maestro]
```

It always runs **detached**, inside `t3.sh`, which runs under the emulator-lock holder (R1) with `setsid` and writes
a done-file (02 §8 script contract; memory `background-task-killer`: long harness tasks get killed on this PC).

1. **Refuse to start** (exit 2) unless all of these hold:
   - the caller holds the emulator lock (`/home/eslam/Storage/tmp/oc-emulator.lock`; R1);
   - `free -m` shows ≥ 7 GB available (02 §7.8);
   - the filesystem that holds the AVD (from `roles.json`) has ≥ 20 GB free (§4.6);
   - the serial is an `OCN_*` AVD from `roles.json` whenever a selected flow clears app data (§4.2 rule 5).
2. **Prepare the AVD** for the role (R2): boot it with `-no-snapshot-save`, demo-mode status bar, the locale, the
   role's font scale and density, gesture navigation. Animations are **off** (the three scales at 0) unless
   `--animations on` sets them to 1.0.
3. **Generate** `build/strings.js` for `--lang`.
4. **Start the fixtures** named in the selected flows' headers (R4) and the `[adb]` hook endpoint. Write their PIDs to
   `<out>/pids`.
5. **Install** with R3 (signer, lineage, versionCode and ABI checks; `adb install -r`, never `-d`), unless the same APK
   SHA-256 is already installed.
6. **For each flow:**
   1. Load its snapshot (`adb emu avd snapshot load <name>`). Check that the snapshot's recorded commit and APK
      SHA-256 in `roles.json` match what the flow expects, and that the installed versionCode is the expected one
      (`dumpsys package`); if the snapshot holds an older build of the same app, run `adb install -r` again (keeps the
      data). A mismatch the runner cannot fix is exit 2.
   2. For `-qa` companions: write `qa-hooks.json` (§4.4 recipe) and assert the `qa-hooks loaded` marker.
   3. `adb logcat -G 16M` and `adb logcat -c`, so a 40-minute flow does not wrap the buffer.
   4. Run `maestro --device <serial> test --format junit --output <out>/<flow>/report.xml
      --test-output-dir <out>/<flow> --debug-output <out>/<flow>/debug -e APP=<app> -e APP_ID=<pkg>
      -e SERVER_PASSWORD=… <file>` (flags [V docs.maestro.dev CLI reference]).
   5. Save `adb logcat -d -b all` (the whole buffer of every process: earlier app PIDs, the isolated PDF process,
      `system_server` logging notification or intent extras). Run the **canary scan** (R11) on it and on the other
      app-side outputs **before** anything is scrubbed.
   6. Run the accessibility node check (R24) when the role or tier asks for it, and the StrictMode scan (R23) on
      `stableQa`.
   7. On failure, retry **once** from the same snapshot, unless `--no-retry`.
   8. Classify the flow as `PASSED`, `FAILED`, `FLAKY` (fail then pass), `UNPROVEN` (§4.9) or, with `--steps`,
      `PARTIAL_OK` (every named step passed; a later failure is recorded as `beyond-scope`).
   9. **Append** the result to `<out>/flows.jsonl` at once, so an interrupted run keeps its finished flows.
7. **Resume:** `--resume <out>` skips every flow already `PASSED` in `<out>/flows.jsonl` for the same APK SHA-256 and
   the same flow-file SHA-256, and runs the rest (AGENTS.md rule 6: resume only unfinished items for an unchanged
   candidate).
8. **Clean up** (in `finally`): stop the fixtures by PID, restore the emulator settings (font scale, density, locale,
   animations, `auto_time`, `device_config` overrides), and delete the fill file if one was made.
9. **Write the outputs:**
   - `flows.json` (built from `flows.jsonl`), `report.xml` (merged JUnit, one `<testcase>` per flow step from the
     labels), `durations.json` and `summary.txt` (02 §8 contract).
   - **Leaks fail first, scrubbing comes second.** The canary scan of step 6.5 has already failed the run if any
     `Canaries.ALL` value appeared in an app-side output (02 S-22, B-MIGR-011). Only then does `scrub.py` mask canary
     values in the harness's **own** files (Maestro's command log and debug output, which legitimately contain the
     password the flow typed), so reports can be shared.
   - Exit codes: `0` pass, `1` a flow failed, `2` could not run.

```json
{"flow":"E2E-05","file":"flows/chat/E2E-05-stream-stop-search-links.yaml","flowSha256":"…","app":"native",
 "variant":"stableRelease","commit":"<sha>","apkSha256":"…","avd":"OCN_A","lang":"en","animations":"off",
 "attempts":[{"status":"FAILED","step":"S3","secs":212},{"status":"PASSED","secs":205}],
 "steps":{"S1":"PASSED","S2":"PASSED","S3":"PASSED","…":"…"},
 "result":"FLAKY","rows":["B-CHAT-017","B-CHAT-060","…"]}
```

The animations-on run of E2E-05 is recorded as flow `E2E-05@anim` with `"animations":"on"`.

### 4.6 Emulators, snapshots and disk

Checked on this PC today [V]: the installed system images are `android-34`, `android-35` and `android-37.0` (all
`google_apis` x86_64); `OC_API37` exists (API 37, 4 GB RAM, 16 GB data). e2e-flows names API 36 for AVD-A, but no API 36
image is installed. T14-2 sets the roles below.

| Role (e2e-flows / 02) | AVD | Image | RAM / data | Used for |
|---|---|---|---|---|
| A | `OCN_A` | android-37.0 google_apis x86_64 | 4 GB / 16 GB | every flow unless noted; started with host audio for E2E-13 and read aloud (R2, R22) |
| S | `OCN_S` | same | 1.5 GB / 8 GB | E2E-17 small-RAM steps |
| F | `OCN_A` + a fill file | — | `run.py` runs `fallocate -l <n> /data/local/tmp/oc-fill.bin` until free space is under the setup requirement, and deletes it in `finally` | E2E-17 full-disk steps |
| T | `OCN_T` | same, `pixel_tablet` device, `hw.keyboard=yes` | 4 GB / 8 GB | E2E-22, E2E-24 |
| AR | `OCN_A` booted Arabic | `emulator -change-locale ar-EG` [U]; fallback as root: `setprop persist.sys.locale ar-EG; setprop ctl.restart zygote` | — | Arabic repeats of E2E-22, E2E-23 |
| U | `OCN_U` | android-37.0 google_apis x86_64 (rootable) | 4 GB / 16 GB | the upgrade journeys J1–J8 and E2E-39, both lineages; the Flutter dumps of 05 §10.4 |
| A11Y | `OCN_A11Y` | same, a 360×740 dp profile (1080×2220 px at 480 dpi), `font_scale` 2.5; the 320 dp case on the same AVD with `wm density 540` | 4 GB / 8 GB | 02 S-28: E2E-01, 05, 06, 08, 25, 26 in en and ar at slice ends (R15, R24) |
| 16K | `OCN_16K` | a 16 KB page-size x86_64 image (`google_apis_ps16k`; offered for API 35, [U] for 36/37) | 4 GB / 8 GB | E2E-01 at slice ends, so 16 KB alignment problems show before the phone (R3) |
| 36 | `OCN_36` | android-36 google_apis x86_64 (to install, about 1.5 GB [E]) | 4 GB / 8 GB | the six emulator smoke flows at slice ends (the owner's NX721J runs Android 16) |
| 26 | `OCN_26` | android-26 google_apis x86_64 (to install) | 2 GB / 8 GB | E2E-01 and E2E-05 at the ends of slices 2, 4 and 6 (minSdk 26: glow overscroll, no `RenderEffect`, older Keystore) |
| — | `OC_EGRESS` (section 16 §2.5) | API 36 `default` image | 16's | the egress harness; counted in the disk budget below |
| — | `OC_API37` (exists) | — | — | section 13 only (`perf-base` snapshot); never wiped by flows |

- `native/e2e/avd/create.sh <role>` creates each AVD with `avdmanager` at the path `roles.json` gives it, sets
  `disk.dataPartition.size` per role (table above), and writes `roles.json`. Only `run.py` may uninstall or
  `clearState` the main package, and only on `OCN_*` AVDs. This extends 02 §7.9, which allows it on the upgrade-journey
  AVD.
- **GPU:** host GPU while the owner's desktop session is logged in; `-gpu swiftshader_indirect` headless otherwise.
  The second needs `emulator/lib64/gles_swiftshader` renamed back (memory `launch-video-2026-10-19`). The mode is
  recorded in `summary.txt`. Flows do not depend on it; benchmarks do (13 §3.7).
- **Snapshots are saved only on purpose.** The emulator docs say `-snapshot <name>` "loads the snapshot of that name and
  saves it back under the same name on exit" [V developer.android.com]. So **every** boot (R2, R6, R13, `run.py`)
  passes `-no-snapshot-save`, and a snapshot is written only by an explicit `adb emu avd snapshot save <name>` inside
  `avd/create.sh` and the seed recipes. `roles.json` records, per snapshot: the commit, APK SHA-256 and versionCode it
  holds, and the date. `run.py` checks them before each flow (§4.5 step 6.1). Without this, one native install would
  overwrite `base`, and J1–J8 would test native-over-native and still pass.

  | Snapshot | AVD | Holds | Remade when |
  |---|---|---|---|
  | `base` | every `OCN_*` | booted, settings applied, no app | the image changes |
  | `flutter-linux` | `OCN_A` | R with built-in Linux set up **from the real pinned hosts through fx-proxy** (§4.4) and project `app`, connected to FX-OC2 | R changes |
  | `native-linux` | `OCN_A` | the same for native | the native setup engine or the rootfs pins change; otherwise a newer native APK goes over it with `install -r` |
  | `native-seeded` | `OCN_A` | native `stableRelease` + the native twin of the seed flow's data, no Linux | the seed or the stored formats change; newer APKs go over it with `install -r` |
  | `flutter-<VC>-seeded` | `OCN_U` | 05 §10.4's seed on one owner-lineage starting build (S-3 a): the exact build on NX721J, rebuilt from its commit; the local-key build of `flutter-final` (the freeze gate commit until it exists); the bridge release if D4 ships it. Each seed includes one retired widget provider, bound through the helper host (R10) | whenever the versionCode installed on NX721J changes (read with read-only `dumpsys package`), or the seed changes |
  | `public-v57-seeded` | `OCN_U` | the same seed on the GitHub `v2.1.0+57` APK (CI signer `842284B2…1C`) (S-3 b) | the seed changes |

  At most 4 snapshots on `OCN_A` and 5 on `OCN_U`; every other role keeps `base` only.
- **Disk** [V `df`/`du`, 2026-11-01 16:35]: `/` has 25 GB free, `/home/eslam/Storage` 29 GB and
  `/home/eslam/Storage2` 60 GB. Free space moves by tens of GB in a day on this PC (memory `storage2-offload`), so the
  rule is per filesystem and checked at every run, not this snapshot of numbers.
  - The stale AVDs hold 39.6 GB. On Storage2: `FB_F6` 5.9, `OCMN_Stable_1042_QA` 6.4, `OCMN_Stable_1042_Public_QA`
    4.4, `OCMN_UI_Refinement` 12 (28.7 GB). On Storage: `Pixel_6` 6.0 (through the relocated `~/.android`) and
    `OCMN_Live_Demo_20260913` 4.9 (10.9 GB). `OCMN_Luna_Audit` is only a dangling `.ini`: its `.avd` under
    `~/.cache/ocmn-emulator` is already gone.
  - T14-3 deletes those six and the dangling `.ini`, and keeps `OC_API37` (1.5 GB, Storage), `OC_API35` (5.4 GB, `/`)
    and `OC_Launch_Demo` (13 GB, Storage2; launch videos). Expected free space afterwards: Storage2 ≈ 89 GB, Storage
    ≈ 40 GB, `/` unchanged.
  - Need per AVD [E, measured on `OCN_A` first, then recomputed]: `OCN_A` ≈ 20 GB, `OCN_U` ≈ 20, `OCN_T` 6,
    `OCN_A11Y` 5, `OC_EGRESS` 6 → **Storage2** (≈ 57 GB, leaving ≈ 32 GB); `OCN_S` 4, `OCN_36` 6, `OCN_26` 4,
    `OCN_16K` 5 → **Storage** (≈ 19 GB, leaving ≈ 21 GB). `/` holds no new AVD. Each AVD's path is in `roles.json`.
  - **The floor:** the filesystem that holds an AVD keeps **≥ 20 GB free** (a full disk mid-run corrupts snapshot
    saves). `create.sh` refuses to create an AVD that would break it, and `run.py` refuses to start (§4.5 step 1).
    Roles that do not fit wait; until the owner answers T14-3, only `OCN_A` and `OCN_U` are created, and the other
    roles' runs wait.

### 4.7 Proving a flow on Flutter

1. **Build the references once.**
   - **R:** a detached worktree `/home/eslam/Storage/Code/wt/oc_app-ref-build` at `65e3f70a3` (the 39 catalog flows).
   - **R':** a detached worktree `/home/eslam/Storage/Code/wt/oc_app-ref-build-freeze` at the freeze-tip commit the
     brief names (02 §3.5), for the top-up flows of §4.11, whose features do not exist at `65e3f70a3`.
     `docs/native/reference.md` records both: commit, versionCode, APK SHA-256 and signer (02 §6.2).
   - `oc_app-batch1` and `oc_app-freeze` stay read-only.
   - Run `tool/qa/vps_build_apk.sh <N>` in each. N is a Flutter build number reserved in `native/BUILD_NUMBERS.md`,
     below 10000 (for example 4600 and 4610 [E]). The script builds on the VPS and re-signs on the PC with the local key
     (memory `build-vps`).
   - Before installing, check that `dexdump <apk> | grep -c GeneratedPluginRegistrant` is greater than 0 (memory
     `shared-emulator-contention`).
   - Keep `R-<N>.apk`, `Rf-<N>.apk` and their SHA-256 in `/home/eslam/oc-artifacts/flutter-ref/` on the PC and in
     `~/oc-artifacts/flutter-ref/` on the VPS.
   - When `flutter-final` is tagged, rebuild one R from it the same way (it replaces both) and re-prove every flow
     (02 §6.2).
2. The writer turns the e2e-flows text into YAML. The step text stays as comments.
3. The T3 holder runs `run.py --app flutter --apk R-<N>.apk --avd A --flows E2E-NN`.
4. **On failure:**
   - **Wrong label or timing:** fix the flow. A label the ARB and the Android resources lack goes into
     `extra.en.json`.
   - **A real Flutter bug:** add a `KNOWN-FLUTTER-GAPS` line in the header and a row in `known-gaps.md` (row, step,
     reason, decision). Wrap the step in `runFlow: {when: {true: "${APP == 'native'}"}, file: …}`.
   - A gap needs the coordinator's OK. A flow with more than two gap steps is redesigned.
5. **Proven on Flutter** means green twice in a row on Flutter. `slices.json` then records the reference (R or R'),
   the APK SHA-256, the date, and the flow file's SHA-256. Companions get `flutterProof: "n/a (qa hook)"` (§4.4).
6. **Record:** after each batch, the T3 holder writes `docs/qa/native/parity-baseline-flutter-<date>/README.md` in the
   02 §9.4 format: flows, APK hash, AVD, durations, the screenshot reference manifest (§7.1) and the known gaps.

### 4.8 Writing order, slices and tiers

**When flows are proven on Flutter** (one schedule: 02 §10 item 7 as section 15's C15-6 resolves it; recorded in
§13.2): the flows lane proves flows in batches in W1–W4, with the new flows by the end of slice 1, not one group per
slice.

| Batch | Flows | Proven on Flutter by | Fixtures (S0-FX) |
|---|---|---|---|
| 1, before slice-1 fan-out (15 C15-6) | E2E-01, 02, 03, 04, 05, 06, 10, 21, 36 and E2E-39 Parts A and C | end of W2 | FX-OC2, stub model, FX-OC2-B, fx-proxy, replay, FX-TLS, hook endpoint (W1) |
| 2 | the other 28 of the 39 (E2E-28 and E2E-32 included, so they are ready when D6 brings them back) | end of W4 (15 S0-FLOWS), before any slice-2 unit needs them | FX-PASEO, FX-NPM/AUTH, FX-LINUXDL, FX-GH (W2); FX-GIT, FX-CODEX, FX-USAGE (W3) |
| 3, the new flows of §4.11 | E2E-40, the main file of E2E-41 where one exists, and the freeze-tip top-up flows (on R') | end of slice 1, together with the catalog top-up (02 §3.5) | as above |

Every flow is therefore proven at least one slice before the slice that first needs it.

**Which slice turns each flow green on the native release build** (section 15 §14.2 is the source; `slices.json`
follows it, and section 15 may move a flow):

| Slice | Flows | AVD | Est. minutes per run [E] |
|---|---|---|---|
| 2 (chat daily path) | E2E-01, 03, 04, 05, 10. **Step credit** (§4.9): E2E-21 steps 1–7, E2E-02 the typed and code steps, E2E-06 approve and deny | A | 4, 8, 8, 10, 8 |
| 3 (phone runtime and agents) | E2E-02, 06, 11, **12**, 17, 18, 30, 31; top-up flows for the process budget and kill line, the phone server restarting after an OpenCode update, paste a picture | A, S, F | 6, 8, 8, 8, 40, 20, 12, 10 |
| 4 (surfaces, settings, schedules) | E2E-07, 08, 09, **21** (all steps), 22, 23, 25, 26, 27, 34, 36, 38; **E2E-41** in-app update; top-up flows for the Updates page, star/share/What's new and tested pins | A, T, AR, U | 8, 10, 6, 8, 10, 10, 10, 10, 8, 8, 8, 5 |
| 5 (project, media, integrations) | E2E-13, 14, 15, 16, **19**, 20, 24, 29, 33, **35**, 37; **E2E-40** cards (with S5-CARDS); top-up flows for Linux services, vitals, backup and restore, Wi-Fi share of chat file links and opening a phone file from a chat link | A, T | 10, 10, 12, 6, 15, 12, 5, 8, 5, 5, 8 |
| 6 (switch) | E2E-39 upgrade in place (Parts A and C proven on Flutter in batch 1; steps 3–13 native); the public-lineage leg (§4.10) | U | 45 |
| after the switch (D6) | E2E-28 AI Team, E2E-32 Memory | A | — |

Totals [E]: about 6 h 30 min for one pass of the 37 flows (slice 2: 38 min, 3: 112, 4: 101, 5: 96, 6: 45), plus about
1 h 30 min for the new flows of §4.11, up to 40 min when the Linux snapshots are stale, and about 1 h for the Arabic and
tablet repeats. `durations.json` from the Flutter baseline replaces these numbers.

**Tiers** (the "T3" of 02 §8 is the **smoke** tier; the others are longer T3 runs):

| Tier | When | What runs | Budget |
|---|---|---|---|
| `smoke` | every APK that reaches the owner; every preview APK | on `OCN_A`: the six emulator smoke flows (E2E-01, 05, 06, 08, 25, 26) that exist natively, the flows of the units merged since the last owner APK, **`E2E-05@anim`** (02 S-2), and the **screen sweep** (`sweep/S-<route>.yaml` for every route that exists, light, dark and ar; R24; memory `test-on-emulator-not-phone`) | target ≤ 60 min, hard limit 90 min [E]. If measured over, the coordinator moves the slowest smoke flows to `slice` and records it in `slices.json`; the sweep never moves |
| `slice` | slice end | every flow of slices ≤ N that touches the slice's modules (`slices.json` maps flows to modules), with the UIAutomator node check after each flow (R24); plus `smoke`; `OCN_A11Y`: the six critical flows in en and ar (S-28); `OCN_36` smoke; `OCN_16K` E2E-01; `OCN_26` E2E-01 and 05 at slices 2, 4, 6; one `stableQa` pass of the six smoke flows with StrictMode on (R23); the acceptance steps (slice 3, §2.9); the R17–R20 recipes whose features the slice built; **monkey 3 seeds × 10,000** (02 S-21) as its own detached job inside the same lock hold | ≤ 3 h, monkey ≈ 75 min on top |
| `full` | weekly from slice 2 on (the T3 holder picks a day the emulator is free); every switch candidate | all native-runnable flows with the node check; Arabic and tablet repeats; every `slice` extra; every acceptance step; R17–R21 including the S-18 and S-19 probes; section 16's egress scenarios (EG-1…EG-7, 16 §2.5) | ≤ 9 h (one working day or one night) |
| `upgrade` | every candidate that touches `:android:migration`, `:core:data` or a stored format; every owner APK until the switch (ADR §10.2) | on `OCN_U`, from each owner-lineage `flutter-<VC>-seeded` snapshot: J1, J1b, J2, J3 (05 §10.5) with the S-4 manifests (R7); E2E-39 steps 3–13 once they exist; from slice 4, **E2E-41** (native N → N+1 through the app's own updater) | ≤ 75 min |
| `switch` | the switch candidate only | `full` twice with `--no-retry`; the six emulator smoke flows three times; J1–J8 from every owner-lineage seed; **J1 + J3 on `public-v57-seeded` with the CI-signed candidate** (S-3 b; only through CI with the owner's explicit OK, §4.10); E2E-41; monkey 3 seeds × 50,000 events (02 S-21) | ≤ 24 h of emulator time over three days [E] |

### 4.9 Judging the native app with the same files

- A flow counts for native parity only when its file's SHA-256 equals the `flowSha256` of its last Flutter proof in
  `slices.json` (on R, or R' for the top-up flows). Any edit to a flow file means proving it on Flutter again first.
  `run.py` refuses `--app native` parity credit otherwise and marks the result `UNPROVEN`.
- **Companions** (`-qa.yaml`) are the one exception: they carry `flutterProof: "n/a (qa hook)"` with a decision id, and
  `run.py` accepts that entry instead of a `flowSha256` match (§4.4). The main file of the same flow still needs its
  Flutter proof.
- `native-only` variants (`E2E-NN-<slug>.native-only.yaml`) exist only with an owner decision (02 §6.6). They are
  listed in `slices.json` with the decision. They count for flow parity, but they are never compared against Flutter.
- **Step credit** (section 15 gives slices partial flows, for example "E2E-21 steps 1–7"): `slices.json` may name a
  step range per flow and slice. `run.py --steps E2E-21:S1-S7` runs the whole file, judges the named steps from the
  per-step results, and records `PARTIAL_OK` when they all passed. `SliceDoneTest` accepts `PARTIAL_OK` only where
  `slices.json` names that range for that slice. A Maestro run stops at the first failure, so the range must be a
  **prefix** (S1…Sk); a non-prefix range in section 15 needs the steps reordered in the flow (and a new Flutter proof)
  or a prefix instead (§13.2).
- A step that is `flutter-known-bad` must pass on native (testing-perf §6.5).
- A flow that fails on native and passes on Flutter is a native bug. The runner's step labels name the rows the failed
  step proves, and the coordinator routes the bug to the unit that owns them.

### 4.10 E2E-39 and the upgrade journeys (both lineages)

- Part A (seed) and Part C (assert) are written once, as `upgrade/partA-seed.yaml` and `upgrade/partC-assert.yaml`.
  They are proven on Flutter in batch 1: E2E-39 Part B runs Part C on Flutter.
- The slice-0 short form for ADR P5 runs steps 3, 4, 6 and 8 against the P5 spike build. The full flow runs from
  slice 6.
- J1–J8 (05 §10.5) are run by `native/tool/device/upgrade_journey.sh J<n> --start <snapshot> --lineage local|public`
  (R7), on `OCN_U`.
- **(a) Owner lineage** (local key `1DE5BF08…D60C`; 02 S-3 a): from every `flutter-<VC>-seeded` snapshot (§4.6): the
  exact build on NX721J, the local-key `flutter-final` (or freeze gate) build, and the bridge release if D4 ships it.
  The snapshot of the phone's build is remade whenever its versionCode changes, read with read-only
  `dumpsys package` (R14).
- **(b) Public lineage** (CI signer `842284B2…1C`; 02 S-3 b): from `public-v57-seeded` (the GitHub `v2.1.0+57` APK,
  tag commit `e44b581aa`) to a **CI-built, CI-signed** native candidate. R3 accepts that signer only with
  `--lineage public`, only for an artifact CI built, and only with the owner's explicit OK for that CI run (AGENTS.md CI
  budget; 02 §7.9). It runs once per switch or public candidate; the result goes into that candidate's QA record.
- The seed carries section 16's `Canaries` values (the fixtures serve them, §4.4), so B-MIGR-011 and S-22 are checked
  on real migrated data, and one retired widget provider, which must redraw after the update (02 S-3).
- S-4's before and after manifests are R7's.

### 4.11 Flows the catalog does not have yet

The 39 flows were written from batch1. Three gaps need new flows; each is written, proven (on R or R') and given a
slice in `slices.json` like the others, and `FlowRowsTest` includes its rows. Ids continue the catalog's numbering.
These flows are switch scope, so the switch flow count becomes **37 + these** (§13.2: 02 S-2 says 37/37).

1. **E2E-40 Generative UI cards** (09 §17; slice 5 with S5-CARDS). FX-OC2 registers the `oc-ui` helper as an MCP server,
   and the stub model's `e2e:card-choice`, `e2e:card-form` and `e2e:card-photo` prompts each emit one `oc-ui_show`
   call. Steps: the card shows; answer it; Undo within 3 s; a double tap sends **once** (FX-OC2's request log counts
   one answer); kill the app (`killApp`) mid-send and relaunch: an uncertain send is **not** resent and history
   recovers the card's state. Proven on R (whether R qualifies FX-OC2's agent for cards is [U]; if not, the steps go to
   `KNOWN-FLUTTER-GAPS` with the coordinator's OK, and B-CHAT-037 keeps its Flutter unit tests as the reference).
2. **E2E-41 In-app update** (the native `AppUpdater`, the only update path once Shorebird ends; 02 §3.5; slice 4 with
   S4-UPDATER). A `-qa` companion on `stableQa` (the feed is hard-coded on R, §4.4), so `flutterProof: "n/a (qa hook)"`:
   install native N; FX-GH serves N+1 with `SHA256SUMS`; Settings › About › Updates › Update now; the download
   finishes; the `REQUEST_INSTALL_PACKAGES` grant screen and the system install dialog are tapped (system UI: `retry:`
   allowed there only); the app comes back after its own update; `dumpsys package` shows N+1's versionCode and the data
   is intact. Switched cases: a bad checksum and a wrong-signer APK are refused in plain words with nothing installed;
   a cut download resumes or fails in plain words. It runs in the `upgrade` and `switch` tiers. The top-up rows from
   `test/app_update_policy_test.dart` cite E2E-41 in `flow-rows.json`.
3. **Freeze-tip flows** (02 §3.5's switch-scope features, which do not exist at `65e3f70a3`): Linux backup and
   restore; Linux services and vitals; the process budget with the kill line and the child-process card; the phone
   server restarting itself after an OpenCode update; Wi-Fi share of chat file links and opening a phone file from a
   chat link; pasting a picture from the clipboard (R22 `clip-image`); star, share and What's new; tested pins with
   phone agents updating themselves only when idle and only on Wi-Fi. The flows lane writes them in parallel with the
   catalog top-up (§2.10), proves them on R' until `flutter-final` exists, and gives them the slices of the section 15
   units that build them (§4.8). Section 15 already expects "services, vitals and backup" and the Updates page to pass
   at T3.

---

## 5. The deferred widget-test catalog (7,967 tests)

The owner: "7400 for widgets can come later" (02 NG12; ADR §4 row 18). This section plans the phase so that waiting
costs nothing, and so the owner can schedule it with numbers in hand.

### 5.1 What happens now (zero-cost preparation only)

1. Test tags equal the Flutter `ValueKey` strings where one existed (ADR §9.1). Screens take `(XUi, XActions)` and
   ship previews for loading, content, failure and empty (03 §20.7). Both let a widget behaviour be ported later
   without touching the screen.
2. **Widget-test name index** (T14-4, half a day, a script, no agent reads tests):
   - `native/tool/flutter-fixtures/widget_index.py` reads the frozen Flutter `test/` tree and writes
     `native/catalog/widget-index.tsv`.
   - Columns: `file, line, group path, test name, folder (kit|revamp|goldens|root|other), screen guess (from the file
     name and 07 §5's inventory), cited_by_logic_catalog (yes/no)`.
   - Builders get the lines for their screen in their brief's read set, as a checklist of what the Flutter screen was
     tested for. Reading them is optional and porting them is not asked.
3. Builders note "Widget-test candidates" in their QA record (§3.7) when they see a behaviour only a widget test
   protects.

### 5.2 When it starts (T14-4), and the one part that does not wait

- **Default: nothing starts without the owner's yes** (ADR §4 row 18: "No widget-test work until the owner schedules
  it"). At the switch handover, the coordinator asks the owner, with the W-1 cost estimate (§5.4), whether and when to
  start. No date starts it by itself.
- **Measured trigger to ask earlier:** the coordinator counts UI regressions found by the slice-end emulator sweeps
  (02 §6.7) and by the owner, where a ported widget test would have caught the bug. If one slice finds **5 or more**,
  or the owner finds **2 or more** in one preview APK, the coordinator **asks** the owner whether to start the phase at
  the next slice. The trigger is a question, never an automatic start. The count is kept in `native/LEDGER.md` under
  "Widget-test trigger".
- **Carved out, before the switch (02 S-28):** the accessibility widget tests are not part of the deferred phase.
  02 S-28 says they "are ported before the switch; NG12 does not cover them". Unit **W14-18** (slice 4) ports the
  behaviours of `accessibility_guidelines_test` (9 widget tests), `terminal_accessibility_test` (10) and the
  `text_scale_overflow_*` files: labels on every interactive node, ≥ 48 dp targets, one live-region announcement per
  status change, and no overflow at text scale **2.5** as well as the 2.0 the Flutter tests used. They run as
  `runComposeUiTest` in `desktopTest` where the behaviour is common, and in `:ui:screenshots` (§3.5) where it needs
  Android. They are new native tests, not catalog rows, unless a KIT row names the behaviour.

### 5.3 The phase, step by step

1. **W-1, classify a sample (2 agent-days).** Classify 200 tests, sampled in proportion to the folders: `test/kit` 60,
   `test/revamp` 23, `test/goldens` 21, root 93, others 3. Each gets one class:

   | Class | Meaning | Becomes |
   |---|---|---|
   | DUP | a logic row already proves it | a link from the widget index to that row |
   | UI | a screen or part behaviour (enabled state, menu contents, focus, semantics, scroll target) | a new row, level `ui` |
   | LOOK | a visual-only check | covered by the Roborazzi matrix (06 §15.3), or a new `screenshot` row |
   | FLUTTER | a check of Flutter structure or harness | dropped (catalog Appendix C precedent) |
   | GONE | the screen was dropped or merged (07 §6) | dropped, citing the 07 decision |

   The output is the class ratios and a projection. The owner then chooses the depth with the numbers.
2. **W-2, catalog (6–10 agent-days [E]).** Use the same raw-file method as the logic catalog, with sequential agents
   and no workflow fan-out (memory `no-heavy-workflows`).
   - New rows take the next free id in their area (02 §6.1) and the new level `ui`.
   - The coordinator extends `ScopeFileTest` and the parity report for that level.
   - Scope is `after-switch` unless the owner pulls the phase before the switch; then it is `switch`.
3. **W-3, port (per screen module).** The feature agent who owns the screen does the porting, or a UI test-writer
   lane does.
   - Behaviour runs in the feature's `commonTest` with `runComposeUiTest` against `XScreen(canned XUi, FakeXActions)`,
     on the desktop JVM.
   - Android-only seams go to Robolectric `createComposeRule` (v2) in `:ui:screenshots`, the one host-test module for
     screen-level tests (§3.5); feature modules have no host tests (03 S15).
   - Looks go to Roborazzi.
   - Tests find nodes by visible text. Tags are used only where the Flutter test used a `ValueKey` (AGENTS.md).
4. **W-4, parity.** `ui` rows join the dashboard with their own area and level columns (§6.4).

### 5.4 Size [E, replaced by W-1]

- The logic catalog compressed 6,663 tests into 1,544 rows (4.3 : 1).
- Widget tests repeat more: per state, per style, per width. At 5–8 : 1 that is **1,000–1,600 rows**.
- At about 1.5 tests per row: 1,500–2,400 Compose tests, about 0.2–0.5 s each on the desktop JVM. That is 5–20 CPU
  minutes, affordable in T2 thanks to the build cache (§9.2).
- Porting at 12–20 rows per agent-day: **60–130 agent-days**.
- The W-1 numbers replace all of this before the owner decides the depth.

### 5.5 What the wait does not risk

- Behaviour rules behind screens are already in the logic catalog (most NAV, CHAT and KIT rows).
- The 60 screenshot rows and the 240 device-flow rows cover what a person sees on the daily paths.
- The screen-by-screen emulator sweep before every owner or preview APK (R24, every route, light, dark and ar) and the
  independent visual auditor (02 §7.10) cover the rest by eye.
- The accessibility widget tests do not wait at all (§5.2, W14-18).
- The trigger in §5.2 turns any growing risk into a number the owner sees.

---

## 6. The parity dashboard

### 6.1 Inputs and computation

`:quality:catalogReport` is a `JavaExec` task in `:quality`; `CatalogRatchetTest` (03 §19.6) uses the same code. It
runs last in `t2.sh`, and again at the end of every T3 run. It implements 02 §6.3–§6.5 exactly.

| Input | Path |
|---|---|
| Catalog | `native/catalog/behaviors.jsonl` (copy, 02 §3.6) + `scope.jsonl` (with each row's `proof: t1\|t3`) |
| JVM and Robolectric results | every `**/build/test-results/**/TEST-*.xml` of the run (restored from the build cache for untouched modules, §9.2) |
| Instrumented results | `build/native-t3/<sha>/instrumented/TEST-*.xml`, written by `t3.sh` from `am instrument -r` output (§9.3), including one synthetic `<testcase>` per acceptance step (§2.9) |
| Flow results | `build/native-t3/<sha>/maestro/flows.json` (per flow and per step) |
| Flow → rows | `native/catalog/flow-rows.json` |
| Kotlin-test baseline | `native/TWINS.md` + `native/android/acceptance-steps.json` |
| Previous state | `docs/native/parity-rows.json` (written at the last green T2) |
| Flakes | `native/FLAKES.md` (§10.5) |
| Visual verdicts | `docs/qa/native/visual-verdicts.jsonl` |

Per row:

1. Collect every test whose name matches the ratchet regex (02 §6.1) for this id, and every flow step whose label
   carries it.
2. Apply 02 §6.3 and §6.4: `todo` (no test), `red` (any failed, skipped or ignored test; skipped counts as red),
   `green`, `deferred` or `dropped` (from scope).
3. **T3-proven rows** (`proof: t3`: `e2e` rows, the instrumented half of `migration` rows, and every row the
   coordinator marks T3) are `green` only when they also passed in a T3 **on the same commit** as the T2 being
   reported. A T3 result from any other commit makes the row **`stale`**, which counts as not green. There is no carry
   over from an ancestor commit.
4. **Screenshot rows** need a verdict too (§3.5).
5. `kotlin_tests`: the moved JVM tests from T2 and the acceptance steps from the same-commit T3, against the baseline.

### 6.2 Kotlin

```kotlin
// native/quality/src/main/kotlin/io/github/eslamasabry/oc/quality/catalog/
@JvmInline value class RowId(val value: String) { init { require(ROW_REGEX.matches(value)) { value } } }
enum class Level { UNIT, CONTRACT, INTEGRATION, SCREENSHOT, MIGRATION, E2E, UI }
enum class Proof { T1, T3 }                                      // from scope.jsonl
enum class RowStatus { TODO, RED, STALE, GREEN, DEFERRED, DROPPED }
enum class ResultSource { JVM, ROBOLECTRIC, ROBORAZZI, INSTRUMENTED, ACCEPTANCE_STEP, MAESTRO }
enum class TestOutcome { PASSED, FAILED, SKIPPED, FLAKY, PARTIAL_OK }

data class TestResult(val id: String, val source: ResultSource, val outcome: TestOutcome, val commit: String, val ms: Long)
data class RowReport(
    val id: RowId, val area: String, val level: Level, val proof: Proof, val slice: Int?, val status: RowStatus,
    val tests: List<TestResult>, val t3: T3Evidence?, val since: String?,   // since = first green commit
)
data class T3Evidence(val flows: List<String>, val steps: List<String>, val status: RowStatus, val commit: String)

object RowIds { fun idsIn(testName: String): Set<RowId> }          // the 02 §6.1 regex, both - and _ forms
class CatalogReport(private val catalog: Catalog, private val scope: Scope) {
    fun compute(t2: List<TestResult>, t3: List<TestResult>, flows: List<FlowResult>, previous: ParityRows?): ParityReport
}
fun main(args: Array<String>)   // --t2 <dir> [--t3 <dir>] ; writes parity.json, parity.md, rows.json, history line
```

### 6.3 Two ratchets (02 §6.5)

| Ratchet | Counts | Stored in | May drop? | Checked |
|---|---|---|---|---|
| **T2 ratchet** | green T1/T2-proven rows, and `kotlin_tests.green` (moved JVM tests) | `native/config/catalog-coverage.txt` (03 §19.6): the sorted green id set at `LAST_GREEN` | never between two T2 runs on `native/main`; `kotlin_tests.deleted` stays 0. T3-proven rows that are only `stale` do not lower it | every T2 |
| **T3 ratchet** | green T3-proven rows, the acceptance steps, and flows green on the release build | `native/config/device-coverage.txt`: the green set at the last `slice` or `full` T3 | never between two T3 runs on `native/main`; a drop blocks the next owner APK until fixed | every `slice` / `full` T3 (`catalogReport --t3`) |

Slice-done and switch-ready need a T2 and a T3 on the **same commit**, so they have no stale rows (02 §6.5).

### 6.4 Outputs

1. `build/native-t2/<sha>/parity.json`, with 02 §6.5's keys (numbers are read from `scope.jsonl`; the ones below are
   02's estimates) plus the `slices`, `levels`, `flows` and `widget` detail:

```json
{"commit":"<t2 sha>","date":"2026-11-20",
 "t3":{"commit":"<sha of the last T3 run>","date":"2026-11-19","same_commit":false},
 "rows":{"switch":1336,"green":512,"red":40,"stale":12,"todo":772,"deferred":208,"dropped":0},
 "areas":{"STATE":{"switch":153,"green":90,"red":3,"stale":0,"todo":60}},
 "levels":{"unit":{"switch":586,"green":301},"contract":{"switch":231,"green":64}},
 "slices":{"1":{"switch":260,"green":251},"2":{"switch":310,"green":180}},
 "flows":{"switch":37,"green":0,"proven_on_flutter":15,"partial_ok":2,"flaky_7d":1},
 "kotlin_tests":{"total":674,"green":674,"deleted":0,"acceptance_steps":{"total":0,"green":0}},
 "features":{"total":700,"dispositioned":700},
 "flakes":{"open":2},"widget":{"cataloged":0,"green":0}}
```

2. `docs/native/parity-rows.json`: one entry per row (id, status, tests, since, t3). It is committed with every green T2
   and is the "previous" input of the next run.
3. `docs/native/parity.md`, the human page, generated and never hand-edited. It holds:
   - the headline;
   - one table per area, level and slice;
   - the flows table (Flutter proof, native result, step credit, last run, flakiness);
   - red and stale rows with their failing test or their last T3 commit;
   - todo rows of the current and next slice;
   - the Kotlin-test line;
   - open flakes;
   - the widget-phase line.
4. `docs/native/parity-history.csv`: one line appended per green T2 (`sha,date,switch,green,stale,flows_green`).
5. **The owner line** goes into each weekly report and each APK handover (`short-replies-rule`), for example:
   `Parity 41 % (548 of 1,336 behaviours). Phone flows 7 of 37. Upgrade journey green. Next: slice 3.`

### 6.5 Gates built on the dashboard (all in `:quality`)

| Gate | Tier | Fails when |
|---|---|---|
| `ScopeFileTest` (02 §3.6) | T2 | scope rows incomplete or malformed, or a row lacks `proof` |
| `CatalogRatchetTest` (03 §19.6) | T2 | the T2 green set lost an id |
| `KotlinTestsRatchetTest` (02 S-1) | T2 | a moved JVM test or an acceptance step is missing, renamed without a `TWINS.md` line, or not green (§2.9) |
| `RowIdsExistTest` | T1, T2 | a test names an unknown or dropped row |
| `RowHeaderTest` | T1, T2 | §2.5 header missing or a clause has no `// [n]` |
| `NoStubsLeftTest` | T2 | a `TODO("B-…")` names a row whose slice is at or before the last closed slice (§2.3) |
| `VectorsProvenanceTest` | T1, T2 | a vector file lacks provenance, names a commit other than R or its rows' `sources_commit`, or a zone family lacks `zone` |
| `FlowRowsTest` | T2 | flows' rows ≠ the `portable_e2e` rows in `scope.jsonl` plus the §4.11 flows' rows |
| `FlowStringsFrozenTest` | T2 | an asserted ARB or Android resource string differs from the frozen value |
| `ScreenshotVerdictTest` | T2 | a row golden has no verdict for its SHA-256 |
| `SliceDoneTest` (`--slice N`, run by the coordinator) | slice end | any row with `slice ≤ N` is not green (stale counts as not green), or a flow of slices ≤ N is not green on the release build in a T3 on the same commit, except a `PARTIAL_OK` that `slices.json` names for that slice (§4.9, §11) |

---

## 7. Screenshot parity references

### 7.1 Three reference sources

| Source | What | Where | Used for |
|---|---|---|---|
| **Flutter emulator captures** | `takeScreenshot: <flow>-S<n>` at the key steps of each flow, taken during the Flutter baseline runs (§4.7); 720 px wide JPEGs plus full PNGs; about 250 [E] | `/home/eslam/oc-artifacts/refs/flutter-<N>/` on the PC, mirrored to the VPS; `native/e2e/refs/manifest.json` (name, SHA-256, size, R build) in git | screen-level comparison with the native capture of the same step |
| **Flutter goldens** | the 4,937 PNGs of the frozen tree (real-font renders) | the reference worktree (read-only) | part-level reference for the kit and screenshot rows. 06 maps parts to goldens; the test writer cites the golden next to `Flutter:` |
| **Owner-picked concepts** | concept images the owner approved (memory `judge-visuals-hard`) | `docs/design/**` [R] | the bar where a concept replaced the Flutter look |

Reference PNGs stay out of git (the PC's disks are at 91–99 %); only the manifest is committed. Losing a copy is
recoverable, because the baseline flows can be re-run on R at any time.

### 7.2 Pairing and triage (`native/tool/e2e/pair.py`)

After each native `slice` or `full` run, `pair.py` pairs each native `<flow>-S<n>.png` with the Flutter capture of the
same name and does three things:

1. **Crops** the status and navigation bars. Demo mode keeps them identical anyway (§8 R2).
2. **Scores SSIM** with Pillow + NumPy (about 30 lines; no ImageMagick dependency; the PC's ImageMagick is 6.9.12
   [V]). The score is a **triage signal, never a gate**: Flutter and Compose differ in font hinting and rounding, and
   NG10 ports the approved look, not its pixels.
3. **Writes** `build/native-t3/<sha>/visual-diff.md`, listing every pair under **0.90** [E, tuned on the first slice]
   with both image paths, for the visual auditor.

### 7.3 Verdicts

The independent visual auditor (Opus, auditor-only, never edits code; 02 §7.10, memory `independent-visual-auditor`)
reviews each listed pair and each new row golden. It appends one line per review to
`docs/qa/native/visual-verdicts.jsonl`:

```json
{"image":"ui/screenshots/src/screenshots/rows/chats/B-NAV-037-phone-light-en.png","sha256":"…","ref":"refs/flutter-4600/E2E-01-S7.png",
 "verdict":"same","note":"","by":"opus-auditor","date":"2026-11-28"}
```

- Verdicts are `same`, `better-approved` (needs the owner decision id), `accepted-diff` (a stated reason inside the
  port-the-look rule) and `regression`.
- A `regression` blocks slice done (§11) until a later verdict on a new image replaces it.
- `ScreenshotVerdictTest` reads this file (§6.5).

### 7.4 What the owner sees

One full emulator screen per image with one sentence (memory `one-screen-per-image`), never pairs or grids. For a
before/after, two separate images in order. Feature proofs are short screen-recorded videos (memory
`live-video-proof`; R6).

---
## 8. Device proof recipes

All recipes live as scripts in `native/tool/device/`. Agents run the scripts and do not retype the commands. The
commands are shown here so a reviewer can check what each script does.

**Shared rules:**

- one emulator holder at a time (02 §7.9);
- `adb install -r`, never `-d`;
- stop processes by exact PID, never by pattern;
- every emulator boot passes `-no-snapshot-save` (§4.6);
- restore font scale, density, locale, animation scales, `auto_time` and every `device_config` override after the run;
- never print a password;
- never screen-capture the owner's phone.

**R1, hold the emulator lock** (`emu_lock.sh acquire <budget-s>|release`; memory `emulator-lock-holder`,
`shared-emulator-contention`)

```bash
LOCK=/home/eslam/Storage/tmp/oc-emulator.lock
HOLDER=/home/eslam/Storage/tmp/oc-emulator.holder
HOLD=$(( ${BUDGET_S:?tier budget} + 1800 ))                       # the tier's budget plus 30 min, never a fixed 4 h
setsid flock -w 1800 "$LOCK" sleep "$HOLD" >/dev/null 2>&1 &      # waits up to 30 min for the lock
FLOCK_PID=$!
SLEEP_PID=
for _ in $(seq 1860); do                                          # 30 min wait + 60 s grace, then give up
  SLEEP_PID=$(ps -o pid= --ppid "$FLOCK_PID" | tr -d ' ')
  [ -n "$SLEEP_PID" ] && break
  kill -0 "$FLOCK_PID" 2>/dev/null || { echo "lock timeout"; exit 2; }   # flock gave up: it never starts sleep
  sleep 1
done
[ -n "$SLEEP_PID" ] || { kill "$FLOCK_PID" 2>/dev/null; echo "lock timeout"; exit 2; }
echo "$FLOCK_PID $SLEEP_PID $PPID $(date +%s) $HOLD" > "$HOLDER"
# the caller (t3.sh) sets:  trap 'native/tool/device/emu_lock.sh release' EXIT
# release:
read -r FLOCK_PID SLEEP_PID _ < "$HOLDER"; kill "$SLEEP_PID"; flock -n "$LOCK" true && echo released
```

- The hold is bounded by the tier's budget, so a lost holder cannot keep the emulator for hours, and the `full` and
  `switch` tiers (up to 9 h per run) do not lose the lock mid-run.
- **Stale holder:** `acquire` reads `$HOLDER` first. If the recorded owner PID is dead (`kill -0` fails) but the lock is
  held, it kills the recorded `SLEEP_PID` (exact PID), logs it in `summary.txt`, and takes the lock.

**R2, boot and prepare a flows AVD** (`emu_up.sh <role> [--animations on]`; ports fixed per role: A 5560, S 5562,
T 5564, U 5566, A11Y 5568, 16K 5570, 36 5572, 26 5574). **Fixture and seed recipes** (05 §10.4, the `flutter-final-seeded` snapshot and every
recipe that writes data into an app before a journey) run only on **`emulator-5584`** under the 05 §10.4 preamble
(`set -euo pipefail`, `ANDROID_SERIAL`, the `ro.kernel.qemu` and AVD-name check before any mutation); no recipe ever
addresses a phone. The flows roles above never seed fixtures

```bash
SERIAL=emulator-5560
AUDIO=-no-audio; [ "$ROLE" = A ] && AUDIO=-allow-host-audio            # voice flows need the mic (R22) [U]
PULSE_SOURCE=oc_mic.monitor \
emulator -avd OCN_A -port 5560 -no-boot-anim -no-snapshot-save -snapshot base \
         -http-proxy http://127.0.0.1:8899 $AUDIO &
EMU_PID=$!                                                             # the emulator runs on the PC, so the proxy is 127.0.0.1
adb -s $SERIAL wait-for-device
until [ "$(adb -s $SERIAL shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb -s $SERIAL root; adb -s $SERIAL wait-for-device
A="adb -s $SERIAL shell"
SCALE=0; [ "$ANIMATIONS" = on ] && SCALE=1.0                          # off for parity runs; on for E2E-05@anim and R6
for k in animator_duration_scale transition_animation_scale window_animation_scale; do $A settings put global $k $SCALE; done
$A settings put system font_scale "$FONT_SCALE"; $A wm density reset; $A wm size reset   # FONT_SCALE from roles.json (1.0; A11Y 2.5)
$A cmd overlay enable com.android.internal.systemui.navbar.gestural    # predictive back needs gesture navigation
$A settings put global sysui_demo_allowed 1                            # fixed status bar for screenshot pairs (§7.2)
$A am broadcast -a com.android.systemui.demo -e command enter
$A am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
$A am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
$A am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
```

Shut down with `adb -s $SERIAL emu kill`, then confirm with `ps -p $EMU_PID`. Because of `-no-snapshot-save`, nothing
the flows did is written back to `base`. Snapshots are saved only by `adb emu avd snapshot save <name>` in
`avd/create.sh` and the seed recipes, which then update `roles.json` (§4.6).

**R3, install with checks** (`install_checked.sh <apk> <package> --lineage local|public [--split-of <universal>]`)

```bash
case "$LINEAGE" in
  local)  WANT=1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c ;;   # AGENTS.md local release key
  public) WANT=$(cat native/tool/device/public-signer.sha256)                          # 842284B2…1C (02 §6.2)
          [ "$CI_BUILT" = 1 ] && [ -n "$OWNER_OK_REF" ] || { echo "public lineage needs a CI artifact and the owner's OK"; exit 1; } ;;
esac
apksigner verify --print-certs "$APK" | grep -qi "SHA-256 digest: $WANT" || { echo "wrong signer"; exit 1; }
NEW=$(aapt2 dump badging "$APK" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
OLD=$($A dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
[ -z "$OLD" ] || [ "$NEW" -ge "$OLD" ] || { echo "would be a downgrade: $OLD -> $NEW"; exit 1; }
adb -s $SERIAL install -r "$APK"
```

- **Which APK T3 installs:** the emulators are x86_64, the owner gets the arm64-v8a split. T3 installs the
  **universal** APK of the candidate build. Before any arm64 split leaves the PC, `--split-of <universal>` proves it is
  the same build: every `classes*.dex` and `resources.arsc` has the same SHA-256 in both; `zipalign -c -P 16 -v 4`
  passes; every `lib/arm64-v8a/*.so` has `LOAD` segments aligned to 16 KB (`llvm-readelf -l`, alignment ≥ `0x4000`).
  The arm64 native code (proot, loader and talloc, sherpa-onnx) first runs on the owner's phone otherwise, so the slice
  tier also boots `OCN_16K` with E2E-01, and, where the image's ARM translation allows it, installs the arm64 split
  with `adb install --abi arm64-v8a` for one E2E-01 smoke [U].
- For VPS-built Flutter APKs, also check `dexdump "$APK" | grep -c GeneratedPluginRegistrant` > 0 (memory
  `shared-emulator-contention`).
- The public digest's full value is recorded from `apksigner verify --print-certs` on the GitHub `v2.1.0+57` asset.
  AGENTS.md also lists a "stable CI" key `2D010C21…D0EC`, which memory `stable-release-1.0.44-2026-09-21` calls
  the CI maintainer lineage, not the public one; §13.2 asks the coordinator to confirm.

**R4, fixtures up and down** (`fixtures.sh up <ids> | down`)

- `up` starts each fixture in its own process group. It writes `name pid port` lines to
  `build/native-t3/<run>/pids` and waits until each port answers (`ss -ltn`). Ports come from `ports.json` only.
- FX-OC2 runs the server **binary** by absolute path, not the npm wrapper: the wrapper's child keeps listening after the
  wrapper's PID is killed (memory `opencode2-stable-2.0-port`). The recorded PID is the one `ss -ltnp 'sport = :4097'`
  shows, not `$!`. The password is `Canaries.SERVER_PASSWORD`, set as both `OPENCODE_PASSWORD` and
  `OPENCODE_SERVER_PASSWORD`.
- Each FX-OC2 gets its own `HOME`, `XDG_DATA_HOME` and `XDG_CONFIG_HOME` under the run directory, so fixture sessions
  never mix with any OpenCode the owner runs on the PC, and nothing of the owner's is ever recorded.
- `down` sends `kill <pid>` for each line, then requires `ps -p <pid>` to be empty within 10 s; otherwise `kill -9`
  on that exact PID.

**R5, run flows**: `run.py` (§4.5) inside `t3.sh --tier <tier>`, detached (§9.3).

**R6, video proof for the owner** (`record.sh <name>`; memory `live-video-proof`)

```bash
adb -s $SERIAL shell screenrecord --time-limit 180 --bit-rate 8000000 /sdcard/oc-$NAME-1.mp4 & REC=$!
# … drive the flow: run.py --flows E2E-NN --animations on (R2 boots with the scales at 1.0 for the recording) …
wait $REC; adb -s $SERIAL pull /sdcard/oc-$NAME-1.mp4 build/videos/; adb -s $SERIAL shell rm /sdcard/oc-$NAME-1.mp4
```

Segments of 3 minutes or less are edited with Remotion on the VPS (step captions, a visible ×N for speed-ups, real
footage only), checked frame by frame, and hosted on the APK share server, never in git.

**R7, upgrade journeys J1–J8 and the S-4 manifests** (`upgrade_journey.sh J<n> --start <snapshot> --lineage
local|public`): exactly 05 §10.5, on `OCN_U` (§4.6, §4.10). File checks go through `adb root` with
`native/tool/upgrade/manifest.sh`, which implements 02 S-4:

- **Protected set** (SHA-256 of every file must match): the rootfs package files under `files/linux/**` minus the paths
  in `native/tool/upgrade/runtime-paths.txt`; `files/projects/`, `voice_models/`, `local_llm_models/`,
  `draft-attachments-v1/`, `prompt-stash-attachments-v1/`, `prompt-photo-recovery-v1/`, `diagnostics/`, the crash
  files, `migrations/<jobId>/`, `oc.teamEngine.<id>/`, `oc.teamEngineDeletion.<id>`, `oc.sharedProjects`,
  `termux-setup-v2.json`, and the eight Kotlin-owned prefs files under `shared_prefs/`.
- **Runtime set** (existence and size only): the paths listed by name in `runtime-paths.txt` (the OpenCode database
  and its wal and shm, `/tmp`, logs, home dot-files).
- **Timing:** the **before** manifest is taken after the Flutter app's server is stopped ("Stop the server on this
  phone", confirmed with R12), right before `adb install -r`. The native candidate is the `stableQa` build with
  `holdRuntimeRestoreUntilManifest` (§4.4): after `MigrationRunner` sets `native.migration.v1`, the app writes
  `files/native/qa/restore-held` and waits; the script takes the **after** manifest, then writes
  `files/native/qa/restore-release`. The restore is never switched off to pass S-4.
- The seed also holds a stash with an attachment and a pending photo (02 S-4), compared like the rest.

```bash
$A "cd /data/data/$PKG && sha256sum files/linux/ubuntu.ready && find files/linux | wc -l"
manifest.sh before > before.sha; adb -s $SERIAL install -r "$NATIVE"; manifest.sh wait-held; manifest.sh after > after.sha
manifest.sh release
diff <(sort before.sha) <(sort after.sha)                     # 02 S-4: protected set byte-identical, runtime set present
```

**R8, restore after reboot or update** (memory `restore-proof-recipe`): `am force-stop` → edit prefs as root if the
case needs it → **`pm unstop $PKG`** (otherwise `MY_PACKAGE_REPLACED` is never delivered and the proof proves nothing)
→ `adb install -r` or `adb reboot` → poll `ps -A -o PID,PPID,USER,NAME` for the app uid's server process. Delivery
shows in `dumpsys activity broadcasts history`.

**R9, notifications**: Maestro opens the shade (hook `notif-shade`: `cmd statusbar expand-notifications`) and asserts
the visible words. For fields and channels: hook `notif-dump` runs `$A dumpsys notification --noredact`, filtered to
the package, checked against 11 §10's channel ids; the canary scan (R11) reads the same dump.

**R10, widgets and tiles** (UIAutomator 2.4.0 tests in `:androidApp` `androidTest`, `qa` build type, named `B_ANDR_…`;
run by `t3.sh` through `am instrument`, §9.3)

- **Placing a widget:** the two widgets the picker shows (`widget_sessions_wide`, `widget_status`) go through the
  launcher's long-press → Widgets path. The other five providers are `hide_from_picker` [V
  `android/app/src/main/res/xml/widget_*_info.xml`], among them the Usage widgets E2E-26 step 5 needs, and the retired
  provider the S-3 seed needs. They are placed through the **e2e helper APK** (`native/e2e/helper/`, test-only, never
  shipped): an `AppWidgetHost` activity bound with `adb shell appwidget grantbind --package <helper> --user 0`, which
  binds any provider by component name (`bindAppWidgetIdIfAllowed`) and shows it, so Maestro and UIAutomator assert on
  the hosted widget and its redraw after an update.
- **Tiles:** `cmd statusbar add-tile <component>` [U14-12 on API 37].
- ZTE launcher quirks remain an owner-phone check (11 §11.4).

**R11, canary scan** (02 S-22; B-MIGR-011): section 16 §2.5 owns the tool (`native/tool/qa/egress/canary_scan.py`)
and the values (`Canaries.ALL`). This section runs it in every T3, after every flow, **before** any scrubbing (§4.5):

```bash
adb -s $SERIAL logcat -G 16M; adb -s $SERIAL logcat -c                      # before the flow (run.py)
# … flow …
adb -s $SERIAL logcat -d -b all > logcat.txt                                   # whole buffer: every pid, system_server, isolated processes
python3 -I native/tool/qa/egress/canary_scan.py --serial "$SERIAL" --pkg "$PKG" --logcat logcat.txt \
        --report report.txt --out canary.json                                   # + dumpsys notification --noredact, files under
                                                                                #   /data/data/<pkg>/ (16's exceptions), the
                                                                                #   analytics queue and sent log, the widget snapshot
```

- No PID filter: canaries are unique strings, so there are no false hits, and filtering by today's PIDs would miss
  earlier app processes, the isolated PDF process and `system_server` logging notification or intent extras.
- The scan covers **analytics payloads** too: `files/native/analytics/queue.json` and the sent log (16 §5), pulled as
  root (02 S-22).
- **Egress** (16 §2.5): fx-proxy's connection log is checked against `native/config/egress-allowlist.toml` (generated
  from `KnownHosts`) plus the fixture ports; with the analytics sender set to none, it must show **0** analytics
  requests (02 S-29). 16's pcap-based harness runs the EG scenarios in the `full` tier.
- `CanarySecrets`, `canaries.txt` and the `.txt` allowlist of the first draft are gone; there is one canary list and
  one allowlist file.

**R12, process tree** (hook `proc-tree`): `U=$($A stat -c %U /data/data/$PKG)`, then
`$A ps -A -o PID,PPID,USER,RSS,NAME | awk -v u=$U '$3==u'`. After "Stop the server on this phone", no process of that
uid may have PPID 1 (memory `proot-orphans-heat`). Used by E2E-18/20 through the `[adb]` hook (§4.2 rule 8) and by the
slice-3 acceptance.

**R13, monkey** (`native/tool/monkey.sh <apk> --events <n>`; 02 S-21): its own detached job inside the lock hold,
outside T3's budget. Boot `OCN_A` from the **`native-seeded`** snapshot (native app installed, seed data, no Linux;
`-no-snapshot-save`, so nothing is written back). For each seed in 11, 23 and 37:
`$A monkey -p $PKG -s <seed> --throttle 50 --pct-syskeys 0 -v <n>`, with n = 10,000 at slice ends and 50,000 for
switch candidates; reload the snapshot between seeds. Then `$A dumpsys activity exit-info $PKG` and logcat must show
no `FATAL EXCEPTION` and no `ANR in`.

**R14, the owner's phone** (02 §6.7 owner phone test, §7.9; memory `no-owner-phone-screenshots`,
`owner-phone-debug-allowed`, `phone-logcat-silenced`)

- **Read-only, always allowed:** `dumpsys package $PKG | grep -E 'versionCode|versionName|lastUpdateTime'`,
  `ps -A -o PID,PPID,USER,NAME` filtered by the app uid, and `dumpsys activity exit-info $PKG`. The phone's logcat is
  silenced (`persist.log.tag=S`), so expect little from it. The versionCode read here drives the
  `flutter-<VC>-seeded` snapshot (§4.6).
- **Before any install** (02 §6.7 step 1): the busy check, `adb shell ps -A -o PID,ETIME,%CPU,NAME` under the app's uid
  shows no working `claude`, `node` or `paseo` process, because an install kills everything running in the built-in
  Linux. If an agent is working, wait or ask. For the switch candidate, the R1 rollback build must exist and have passed
  J4.
- **Installs only while the owner is present**, with `adb install -r`, never `-d`, never a data clear.
- **The upgrade check, then the five phone checks**, by the owner by hand: first the E2E-39 Part C assertions (the
  Conversations list shows, with no welcome screen and no first-run guide; the servers are connected with no password
  prompt), then the five phone checks E2E-05, 06, 08, 25 and 26. "Six smoke flows" means the emulator smoke tier
  only.
- **E2E-01 never runs on the phone's main package:** its first step clears app data, which would wipe the rootfs,
  servers and secrets. It runs on the emulator, or on the `.native` preview package, which has its own data.
  `slices.json` marks it `phone: never` (§4.2 rule 5), and the phone-smoke row of e2e-flows is corrected to match
  (§13.2).
- **Every owner-facing build stores its `mapping.txt`** next to its `native/BUILD_NUMBERS.md` entry, so a stack from
  `exit-info` or a Details text dump can be mapped with R8 `retrace`.
- The coordinator never captures the screen and never changes a device setting.

**R15, Arabic and text-size runs**: `run.py --lang ar --avd A`, with `OCN_A` booted in Arabic (§4.6). For text size,
the flows change the app's own setting (E2E-23). The S-28 runs use `OCN_A11Y` (font scale 2.5, 360×740 dp, en and
ar), and its 320 dp case sets `wm density 540` and **restores it in `finally`**.

**R16, network loss**: Maestro `setAirplaneMode: enabled` (or hook `airplane`); cutting one server goes through
fx-proxy `POST /ctl/refuse?host=10.0.2.2:4097`. The reconnect numbers of S-18 are R21's.

**R17, foreground-service time limits** (AGENTS.md: Android 15+ caps `dataSync` at 6 h per 24 h; T9):

```bash
$A am compat enable FGS_INTRODUCE_TIME_LIMITS $PKG                         # [V developer.android.com FGS timeouts]
$A device_config put activity_manager data_sync_fgs_timeout_duration 60000  # 1 min instead of 6 h
# shortService (widget actions, the finished-reply service): device_config key [U]
```

Instrumented `B_ANDR_…`/`B_LINUX_…` tests then assert: `onTimeout` stops the service within seconds, with no crash
(`exit-info`, no "did not stop within its timeout" in logcat); a refused restart
(`ForegroundServiceStartNotAllowedException`) is handled with plain words. Restore with
`device_config delete …` and `am compat reset FGS_INTRODUCE_TIME_LIMITS $PKG`.

**R18, phantom-process limit** (02 §3.5 process budget): `$A device_config set_sync_disabled_for_tests persistent`,
`$A device_config put activity_manager max_phantom_processes 8` [U]; a phone agent run must show the kill line and the
guided child-process card at the lowered limit. Restore both in `finally`.

**R19, heat**: `$A cmd thermalservice override-status 3` → work pauses with plain words; `override-status 0` → it
resumes; `cmd thermalservice reset` in `finally`.

**R20, time, zone and alarms**: `$A settings put global auto_time 0` before E2E-27's `date` change (otherwise network
time puts it back); `TIME_SET` via `$A date <MMDDhhmmYYYY.ss>`, `TIMEZONE_CHANGED` via the time-zone detector's shell
command [U]; after each, `dumpsys alarm` shows the schedules re-armed (T29). The exact-alarm permission is revoked with
`appops set $PKG SCHEDULE_EXACT_ALARM deny` [U: which permission the app declares], and the app must say so in plain
words and fall back. Restore `auto_time 1` and the app-op in `finally`.

**R21, background pacing probes** (02 S-18, S-19; `reconnect_pacing.sh`, `status_rate.sh`; `full` and `switch` tiers):

- `reconnect_pacing.sh`: connect to FX-OC2, send the app to the background (`input keyevent HOME`), refuse FX-OC2
  through fx-proxy for 10 minutes, then airplane mode for 5 minutes, then bring the app back. From FX-OC2's request
  log: the first two tries at foreground pace (0.5 s, 1 s), then **≤ 1** attempt per 60–120 s; **0** attempts in
  airplane mode; **one** immediate try within 2 s of returning.
- `status_rate.sh`: three sessions running `e2e:tools-loop` for 10 minutes with the app hidden. Count status
  notification posts (polled `dumpsys notification --noredact` post times; the `qa` build's category-only post marker
  as fallback [U]): no two posts within 5 s, and ≤ 10 per 10 minutes (≤ 60 per hour).

**R22, device inputs** (hooks; each [U], proven in W14-20):

- `mic-feed <wav>`: a PulseAudio null sink `oc_mic` on the PC; the emulator is started with `PULSE_SOURCE=oc_mic.monitor`
  and host audio on (R2), and `paplay --device=oc_mic <wav>` feeds it. Only the emulator reads that source; the
  desktop's default input is never changed. E2E-13 uses it for the 3-minute speech, the corrupted WAV at 1:30 and the
  6-second phrase; read aloud needs audio on too.
- `camera-qr <png>`: the emulator's virtual-scene camera with the image as a poster
  (`-camera-back virtualscene -virtualscene-poster wall=<png>`), with a v4l2loopback webcam as the fallback. The native
  app decodes with zxing-cpp and R with ML Kit, so both must read the same image (E2E-02 step 5).
- `media-seed <dir>`: `adb push` into `/sdcard/Pictures/oc-e2e/`, then a media scan
  (`content call --uri content://media --method scan_volume --arg external_primary`) for E2E-12.
- `clip-image <png>`: the helper APK puts a `content://` image on the clipboard from a short foreground activity (only
  the foreground app may set the clipboard), for the paste-a-picture flow.

**R23, StrictMode scan** (02 S-21; `stableQa` runs): with `QaHooks.strictMode` the app sets `penaltyLog()` for disk and
network on the main thread. After each `stableQa` flow, `run.py` collects logcat lines with `StrictMode policy
violation`, retraces their frames with the build's `mapping.txt`, and fails the run on any violation not matched by
`native/config/strictmode-allowlist.txt` (step 0 only, 02 S-21).

**R24, accessibility node check and screen sweep** (02 S-28; `a11y_sweep.py`)

- After each flow (once Maestro has exited, so its UIAutomator session does not clash), `uiautomator dump` of the
  final screen; and for the sweep, one dump and one screenshot per route (`sweep/S-<route>.yaml`, generated by
  `gen_sweep.py` from 07 §5's screen inventory: every tab, every Settings page and sub-page). Checks: every clickable
  or focusable node has text or a content description; its bounds are ≥ 48 dp in both directions (px ÷ density).
- **Live regions announced once:** the helper APK's accessibility service records announcement and live-region events
  to logcat while it is enabled (`OCN_A11Y` runs only); one status change must give one announcement [U].
- The sweep's screenshots (light, dark and ar) go to the visual auditor (§7.3) with the slice QA record; they are
  emulator screens, never the owner's phone.

---

## 9. CI layout and runtime budgets

### 9.1 Where tests run

| Job | Machine | Trigger | Run by |
|---|---|---|---|
| T0 | PC or VPS | while editing | builder, test writer |
| T1 | VPS (`~/wt/ocn-<unit>`, synced by `vps_gradle.sh`, 02 §7.3) | branch ready; after merging `native/main` into the branch | builder, test writer (`--expect-red`) |
| T2 | VPS (`~/ocn/main`) | integration checkpoint (at least daily while merges land); every preview or switch candidate | coordinator |
| Nightly and weekly jobs (§9.4) | VPS cron | schedule | coordinator owns the crontab |
| T3 tiers (§4.8) | APKs built on the VPS (§9.3); runs on the PC + one emulator | owner APKs, slice ends, weekly, switch | T3 holder |
| T4 | PC + `OC_API37` (13) | slice ends, perf-sensitive merges | T4 holder |
| GitHub Actions | — | **only on an explicit owner request** (AGENTS.md CI budget). 03's `.github/workflows/native-*.yml` mirror `t2.sh` for the public release lineage. | coordinator, with the owner's OK |

### 9.2 VPS layout

```
~/Storage/Code/oc_app.git        the bare repo (git remote "vps" in the PC checkout; memory build-vps)
~/ocn/main/                      T2 worktree, detached at the commit under test; only t2.sh writes here
~/ocn/nightly/                   N1 to N4 worktree
~/wt/ocn-<unit>/                 one rsync target per unit (02 §7.3); never shared by two runs (memory vps-suite-shared-dir-trap)
~/.gradle/                       shared Gradle home: local build cache, toolchains (JDK 17 + Temurin 21), wrapper dists
~/oc-artifacts/                  reports (14 days), APKs of green T2 (until superseded twice), flutter-ref APKs (kept)
~/.oc-native-t2.lock             T2 and N1, exclusive (02 §7.8)
~/.oc-native-t1.slot1 / .slot2   at most two concurrent T1 runs next to one T2
```

- **Memory [E]:** T2 needs about 9 GB at peak (Gradle 3 GB, Kotlin daemon 3 GB, 3 Robolectric forks at 1 GB each); a
  T1 needs about 5 GB. One T2 plus two T1 runs fit into 23 GB plus 8 GB swap. P8 measures the real numbers.
- **Disk:** `df -h /` must show ≥ 30 GB free before T2 (02 §7.8) and ≥ 15 GB before T1. N5 cleans up (§9.4).
- **Build cache, the main speed lever:**
  - `Test` and Roborazzi verify tasks are cacheable, so a module whose inputs did not change restores its JUnit XML
    from the cache instead of re-running. The parity report still sees every result, and the result is valid for the
    tree, because the inputs are identical.
  - For this to work across `~/ocn/main` and `~/wt/ocn-*`, every test input must be path-relative. The convention
    plugin declares `native/fixtures` and `native/catalog` on every `Test` task with
    `inputs.dir(...).withPathSensitivity(PathSensitivity.RELATIVE)`, and tests find them at run time by walking up from
    the working directory (§3.2). No absolute path goes into a system property or into generated source (a request to
    the build-logic owner, §13.2).
- **Test JVM settings** (the `oc.kmp.library`, `oc.kmp.compose` and `oc.android.library` conventions):
  - `maxHeapSize = "1g"`;
  - `user.timezone=UTC`, `user.language=en`, `user.country=US`;
  - JUnit XML always on, `failFast = false`;
  - `maxParallelForks` from `oc.test.forks` (PC 1, VPS 3; 03 §4);
  - Robolectric host tests on a Temurin 21 `javaLauncher` (ADR §8);
  - **one JUnit setup per task kind:**
    - `desktopTest`: JUnit Platform with `kotlin-test-junit5` (Jupiter);
    - `androidHostTest` and `:android:*` `src/test`: JUnit Platform with **`junit-vintage-engine`** added by the
      convention plugin, because Robolectric, Roborazzi (`createComposeRule`) and the 674 carried tests are JUnit 4
      (11 §4.3: JUnit 4.13.2). Without the vintage engine they are not discovered, and in a module that also has
      Jupiter tests Gradle's "no tests discovered" failure does not fire, so they would be skipped silently;
    - `JUnit4WithoutVintageTest` (`:quality`, T1/T2) fails when a module has `org.junit.Test` or `@RunWith` in a test
      source set whose task lacks the vintage engine.
  - **power-assert** for test compilations: the Kotlin plugin transforms only `kotlin.assert` unless told otherwise
    [V kotlinlang.org/docs/power-assert.html], so the convention plugin sets
    `powerAssert { functions = listOf("kotlin.assert", "kotlin.test.assertEquals", "kotlin.test.assertTrue",
    "kotlin.test.assertFalse", "kotlin.test.assertNull", "kotlin.test.assertNotNull") }`.
- **Golden guard:** the Roborazzi record task refuses to run unless `OC_GOLDEN_HOST=vps` is set (ADR §8; 06 §15.3). A
  PC compare against VPS goldens is informational only and never evidence, because the two CPUs rasterise glyph edges
  about 0.2 % differently (memory `build-vps`).
- **Script tools preflight:** `native/tool/preflight_scripts.sh` (§3.9) runs before T1 and T2 on the VPS and exits 2
  when a tool the script tests need is missing.

### 9.3 What each tier runs (the test side of 02 §8 and 03 §8.3)

| Tier | Test-side steps, in order |
|---|---|
| T0 | `./gradlew <module>:desktopTest --tests "*B-XXX-NNN*"` (or `testAndroidHostTest`, or `:android:x:testDebugUnitTest`) |
| T1 | script preflight (§3.9) → touched modules (merge-base with `native/main` + uncommitted): `desktopTest`, `testAndroidHostTest`, Roborazzi verify, `:quality:test` (Konsist + §6.5 gates that need no T2 input), `lint_flows.py` if `native/e2e/**` changed, shellcheck on changed asset scripts, `assembleStableQaAndroidTest` when instrumented tests changed (they must compile) → **rerun each failing test once** → classify (§10) → `--expect-red` mode on tests branches (§2.7) → `summary.txt` |
| T2 | script preflight → everything in 03 §8.3's T2 line, `--continue` → rerun-once classifier → `:quality:catalogReport --t2` → `FlowStringsFrozenTest`, `FlowRowsTest`, `ScreenshotVerdictTest`, `KotlinTestsRatchetTest` → if green: `native/LAST_GREEN` line, then commit `docs/native/parity.md`, `parity-rows.json`, `parity-history.csv` and `config/catalog-coverage.txt` (coordinator, `docs(native): parity <pct> at <sha> [skip ci]`) |
| T3 | see below |

**T3** (`t3.sh <commit> --tier <tier>`), always detached (`setsid`, done-file; 02 §8):

1. **On the VPS** (release builds never run on the PC, ADR §10.3; the PC keeps its one build slot, 02 §7.8):
   `assembleStableRelease assembleStableQa assembleStableQaAndroidTest` for the commit, with `testBuildType = "qa"` for
   every instrumented test (11 §4.3 and 05 already run the acceptance harnesses and `UpgradeJourneyAssertions` on `qa`;
   03's `testBuildType = "release"` is changed, §13.2). The APKs come back to the PC.
2. **On the PC:** re-sign the app APKs **and** the androidTest APK with the local key (an instrumentation must carry
   the target's certificate), then R1 (lock, with the tier's budget) → R2 → R3 for the app (`--lineage local`, and the
   arm64 split check) → `adb install -r` of the test APK.
3. **Instrumented tests** run with `adb shell am instrument -w -r -e class <filter>
   io.github.eslamasabry.opencode_mobile.test/androidx.test.runner.AndroidJUnitRunner`; the acceptance steps (§2.9) with
   `am instrument -w -r -e step <s> io.github.eslamasabry.opencode_mobile.test/<Harness>`. `t3.sh` converts the raw
   `-r` output into JUnit XML (one synthetic `<testcase>` per step). **Nothing is ever uninstalled**: this replaces
   `connectedStableReleaseAndroidTest`, which would build and minify a release APK on the PC, install its own APK over
   the checked one, and uninstall both at the end, wiping app data and the rootfs.
4. R4 + `run.py` for the tier's flows (§4.5, §4.8), with R11 (canary) after every flow, R24 (node check) where the tier
   asks, R23 (StrictMode) on `stableQa` runs, and **`E2E-05@anim`** in every tier (02 S-2).
5. The tier's extras (§4.8): `OCN_A11Y`, `OCN_16K`, `OCN_36`, `OCN_26`, the acceptance steps, R17–R21, the upgrade
   journeys, the egress scenarios.
6. Monkey (R13) as its own detached job in the same lock hold: 3 × 10,000 at slice ends, 3 × 50,000 for the switch.
7. `:quality:catalogReport --t3` (T3 ratchet) → `pair.py` → `summary.txt` → done-file. If the run is interrupted,
   `t3.sh --resume <out>` re-runs only what has not passed for the same APK and flow SHA-256 (§4.5).

**Repository gates that are catalog rows (from the gap closure, 17 §6).** These BUILD rows are T2 gates in `:quality`
(03 §19.6), not product tests; each gate's test method is named after its row so `CatalogRatchetTest` counts it:
B-BUILD-001 `BoundaryRulesTest`, 005 `ActionCoverageTest`, 023 `KitMapGateTest`, 031 `ToolchainPinTest`, 033
`UiLedgerTest`, 034 `ApkPluginRegistrantTest`; B-BUILD-027 `PromptTimelineTraceTest` (`:engine:diagnostics`, T1); B-BUILD-029
`ReleaseContractTest` (`native/tool/release`, T2 and the release dry run, 16 §7.4); B-BUILD-013…021 are product tests of
`:engine:project` (17 §2.9). **`GestureLedgerTest`** (06 §11.1) joins T2 from slice 1: every hidden gesture call site is in
`native/config/gesture-ledger.json` with a visible twin and a registered tip; the ledger only grows with new gestures
and an entry is removed only with its gesture. It ports `gesture_audit_test` (DESKTOP-WEB-CROSS-29) as a gate rather
than a widget test, so it does not wait for the 7,967 widget tests.

### 9.4 Scheduled jobs (VPS systemd timers)

The VPS runs Ubuntu 24.04 (memory `build-vps`), whose cron does not support `CRON_TZ` ("cron will only handle tasks in a
single timezone", Ubuntu `cron(8)` [V]). The jobs are therefore **systemd timers** with an explicit zone, for example
`OnCalendar=*-*-* 03:30 Asia/Dubai`, so they run in owner time (UTC+4) whatever the VPS zone is. The unit files live in
`native/tool/ci/systemd/`; `systemctl list-timers` must show the expected next run after any change. They are
VPS-only, so they are unaffected by the PC quiet-hours rule (lifted anyway, 02 §7.8).

| Job | When (owner time) | What | Budget [E] |
|---|---|---|---|
| N1 clean nightly | daily 03:30 | `~/ocn/nightly` at the `native/main` tip; `--rerun-tasks` for every `Test` and Roborazzi verify task (no cache); compares each result with the cached T2 of the same commit. A difference means a cache or determinism bug and opens a `FLAKES.md` entry. | ≤ 75 min |
| N2 iOS klibs | daily 05:00 | `-Poc.ios=true compileKotlinIosArm64 compileKotlinIosSimulatorArm64` (ADR §6.3, 03 §8.3) | ≤ 30 min |
| N3 flake hunt | Sunday 00:30 | all JVM tests ×5 with a recorded seed: Jupiter tests with random method order (`junit.jupiter.testmethod.order.default=…MethodOrderer$Random`); JUnit 4 tests (vintage: Robolectric, Roborazzi, the carried tests) with the class list shuffled by the same seed and split into several `--tests` runs, because Jupiter's orderer never reorders vintage tests | ≤ 3 h |
| N4 drift | Sunday 04:30 | dependency-analysis 3.20.1; R8 analyzer score and arm64 size trend (13 owns the gates); `twin_check.sh` (02 §5.6); pins in `TWINS.md` vs the versions in `native/fixtures/*/` (a pin bump without a re-recording is reported) | ≤ 45 min |
| N5 disk | daily 21:30 | removes `~/wt/ocn-<unit>` whose branch is an ancestor of `native/main` (`git -C ~/Storage/Code/oc_app.git merge-base --is-ancestor`), reports older than 14 days, and superseded APKs; reports when `/` has under 40 GB free. Moved from 02:30 so it never overlaps N1 or a late T2 | ≤ 5 min |

Each job writes `~/oc-artifacts/nightly/<date>/<job>/summary.txt`. The coordinator reads the summaries every morning.
A red N1 or N3 opens flake or regression work before new merges.

### 9.5 Budgets

| Run | Target | Hard limit | Basis [E, P8 replaces] |
|---|---|---|---|
| T0, one row, warm daemon | ≤ 60 s | 3 min | one module compile + one test class |
| T1 | ≤ 10 min | 20 min | 1–4 modules × 2 targets; about 200 tests and 100 captures |
| T2, cache warm | ≤ 20 min | 30 min | changed modules re-run; others from cache; lint; `assembleRelease` for 2 flavors + R8 about 8 min |
| N1, no cache | ≤ 75 min | 120 min | about 3,800 tests (JVM about 4 min wall; Robolectric about 6 min at 3 forks) + about 3,300 captures (about 22 CPU-min, about 8 min at 3 forks) + full compile about 20 min + release about 8 min |
| N3 | ≤ 3 h | 5 h | 5 × the JVM and Robolectric suites |
| T3 tiers | §4.8 | §4.8 | flow durations from `durations.json` |
| T4 | 13 §3 | 13 §3 | — |

The per-test rates behind these numbers [E]: `commonTest` about 30 ms per test; Robolectric about 3 s per class start
plus 0.1 s per test; Roborazzi about 0.4 s per capture.

### 9.6 When a budget is missed (three runs in a row over target)

1. Find the slow tasks with `--profile` (local HTML report; no build scans, for privacy).
2. Fix in this order:
   1. a test that uses real time or a huge fixture → fix the test;
   2. Robolectric start-up → move logic tests from host tests to `commonTest`, and merge tiny Robolectric classes;
   3. too many captures for unchanged parts → trim to 06 §15.3's minimum, with the kit owner's OK;
   4. more forks, if memory allows;
   5. D15, a second VPS: T2 tests on one host, the release build on the other.
3. Never meet a budget by skipping tests or by moving a gate out of T2 without a coordinator note in 02 §8's table.

### 9.7 The PC

- T0 only, one Gradle build at a time (`flock /home/eslam/Storage/tmp/oc-native-gradle.lock`; `free -m` ≥ 7 GB first,
  02 §7.8). Plus T3, T4 and APK re-signing.
- T1 and T2 do not run on the PC. Exception: if the VPS is down, `t1.sh --local` may run with a note in `summary.txt`.
  A local T1 never substitutes for T2.
- `hog_watch.py` stays armed through Monitor during T3 runs (memory `hog-watch-monitor`).

---

## 10. Flake policy (T14-5)

### 10.1 Prevention (each rule has a check)

| Rule | Check |
|---|---|
| Virtual time only: no `Thread.sleep`, no `runBlocking`, no `System.currentTimeMillis`, no `Clock.System` in tests | `NoRealTimeInTestsTest` (`:quality`, a text scan of every test source set) |
| Dispatchers are injected (`TestDispatchers`) | ADR Konsist rule 4, applied to test sources too |
| No real network: only `FixtureEngine` / `MockEngine`; `mockwebserver3` only in `:core:net-okhttp`, bound to 127.0.0.1 | Konsist: an `HttpClient(` in a test must take one of the two engines |
| Fixed time zone and locale (§9.2); tests that care set them explicitly | convention plugin |
| Seeded randomness only; property tests print their seed | review; `PropTestConfig(seed = …)` reproduces |
| Temporary files only through `TestFiles` | Konsist: `File("/tmp` and `createTempFile(` banned in tests |
| No shared mutable `object` state between tests | N3 random order |
| Compose: framework infinite animations only; `mainClock.autoAdvance = false` around motion; `waitUntil` ≤ 5 s | 08 R4; Konsist bans hand loops in UI code |
| Screenshots: `stillFrame`, bundled fonts, one golden host | 06 §15; the golden guard (§9.2) |
| Flows: animations off (except the one `E2E-05@anim` run), waits not sleeps, the deterministic stub model, snapshot starts with `-no-snapshot-save`, `retry:` only on system UI | `lint_flows.py` (§4.2); `run.py` (§4.5) |

### 10.2 Detection

- `t1.sh`, `t2.sh` and `run.py` rerun every failure **once**, in isolation: `--tests` with the exact name, or the same
  flow from the same snapshot.
- N1 compares cached and uncached results of the same commit.
- N3 hunts order dependence with random method order (Jupiter) and a shuffled class order (JUnit 4 under vintage).
- `parity.md` shows flake rates over 7 days.

### 10.3 Response

| Result | Meaning | Action |
|---|---|---|
| `FAILED` (failed twice) | regression | the run is red; the merge that caused it is reverted or fixed before the next merge (02 §6.5) |
| `FLAKY` (failed, then passed) | non-determinism | the run counts as passed; the script appends an entry to `native/FLAKES.md`: owner = the module's owner from `MODULE_OWNERS`, due in 2 working days |
| The same test `FLAKY` twice within 7 days | a real flake | the module is **flake-frozen**: the coordinator merges nothing touching it except the fix |
| A flake past its due date | — | the coordinator makes it a unit (Sonnet; Opus when concurrency is suspected) |
| Exit `2` (could not run: VPS down, emulator boot failure, disk full) | infrastructure, not a flake | rerun the whole tier; if it repeats, fix the machine first |

**Never allowed:** `@Ignore`, `assumeTrue`, retry annotations or rules, raising a timeout without a written cause,
`Thread.sleep`, deleting or weakening a test, `retry:` around an assertion about our app.

**Targets:** flaky results ≤ 0.1 % of JVM test executions and ≤ 2 % of flow runs, over 7 days. Above either, the
coordinator calls a one-day merge stop for flake fixing.

T4 noise is not a flake. 13 §3.7's comparison rule (two failing runs) decides benchmarks.

### 10.4 The switch candidate allows no retries (T14-6)

- T2 green on the first attempt.
- N1 green for the same commit.
- `full` twice with `--no-retry`, including `E2E-05@anim`, the S-28 runs and the R21 probes.
- The six emulator smoke flows three times.
- J1–J8 green from every owner-lineage seed; the public-lineage J1 + J3 green on the CI-signed candidate (with the
  owner's OK); E2E-41 green.
- Monkey 3 × 50,000 with 0 crashes and 0 ANRs; StrictMode clean.
- No open `FLAKES.md` entry that touches a switch-scope row.

A `FLAKY` here counts as `FAILED`.

### 10.5 `native/FLAKES.md`

```markdown
| id | test or flow | first seen (sha, date, tier) | times | owner | due | cause | fix commit |
|---|---|---|---|---|---|---|---|
| FL-001 | :engine:delivery `B-STATE-040 a lost reply after dispatch parks the prompt…` | 4f2a1c9 2026-11-21 T2 | 1 | S chat lead | 2026-11-25 | open | — |
```

---

## 11. Definition of done for every slice (the test view)

02 §6.7 defines unit done, slice done and switch-ready. This table names the test evidence each slice must show. The
flows per slice follow section 15 §14.2 (§4.8); section 15 may move them, and `slices.json` follows.

| Slice | Rows green at T2 (+ T3-proven rows at a T3 on the same commit) | Flows green on the native release build | Device and instrumented | Screens and visuals | Test infrastructure ready at the end of the slice |
|---|---|---|---|---|---|
| **0** skeleton and proofs | the rows of `:core:common`, `:core:security`, `:core:model`, `:core:domain` that `contracts/s0.md` assigns (Redactor, external-link, address-rule and capability-default vectors; 03 §22.1), tested by W14-19, not by their builders | none native. **Batch 1 proven on Flutter R** (E2E-01, 02, 03, 04, 05, 06, 10, 21, 36, E2E-39 Parts A/C); batch 2 under way | P5 short upgrade (E2E-39 steps 3, 4, 6, 8 on the spike build); one v11 secret decrypted on the emulator | one Roborazzi golden recorded on the VPS (P7) | W14-1, 2, 3, 6, 7, 17 and 19 done; all fixtures of S0-FX up (W1–W3); the first W14-5 families; the batch-1 baseline record (W14-13); slice-1 tests branches red and passing `--expect-red` |
| **1** foundations | every `slice = 1` row; the carried Kotlin tests green (`kotlin_tests`) | none (no screens yet) | `SyntheticFlutterSecretsTest`, `KeystoreSecretStoreDeviceTest` (05 §10.3); `ProtectionMarkerAcceptance` | KIT screenshot rows of the foundation parts, with verdicts | **all 39 flows proven on Flutter** (by W4); batch 3 (§4.11) proven on R/R'; slice-2 tests branches red; the widget-name index; P10 numbers fed into §2.11 |
| **2** chat daily path | every `slice = 2` row | **E2E-01, 03, 04, 05, 10**; step credit for E2E-21 S1–S7, E2E-02 typed and code steps, E2E-06 approve and deny | `upgrade` tier on every candidate that touches migration; `OCN_26` E2E-01 and 05 | CHAT and NAV screenshot rows of the slice; first SSIM triage and verdicts | first weekly `full`; canary scan (R11) and section 16's egress harness running (WP-16.4) |
| **3** phone runtime and agents | every `slice = 3` row | **E2E-02, 06, 11, 12, 17, 18, 30, 31** (AVD S and F steps included) + this slice's top-up flows | every acceptance step (§2.9); R12 shows no orphan after a stop; R18 phantom limit | LINUX and AGENT screenshot rows | — |
| **4** surfaces, settings, schedules | every `slice = 4` row | **E2E-07, 08, 09, 21, 22, 23, 25, 26, 27, 34, 36, 38, 41**, with the Arabic and tablet repeats, + this slice's top-up flows | UIAutomator widget, tile and notification tests (11 §11.6), widgets through the helper host (R10); R8 restore after reboot; R17 time limits; R20 alarms; R11 clean | ANDR and NAV screenshot rows; the auditor pass over every style × light/dark + Saver + RTL (02 §7.10) | `OCN_T`, `OCN_36`, `OCN_A11Y`; the accessibility widget tests (W14-18) green |
| **5** project, media, integrations | every `slice = 5` row | **E2E-13, 14, 15, 16, 19, 20, 24, 29, 33, 35, 37, 40** + this slice's top-up flows | sherpa-onnx load, the isolated PDF service, a CameraX scan of the R22 QR (E2E-02 again); R19 heat | MEDIA and the remaining screenshot rows | — |
| **6** migration hardening and switch | every `slice = 6` row, and **100 %** of switch rows on the candidate | **every switch flow** (37 + §4.11) + **E2E-39** on the candidate | J1–J8 from every owner-lineage seed; the public leg; the `switch` tier (§4.8, §10.4); R14 owner phone test | zero open `regression` verdicts; owner images and videos | `SliceDoneTest --slice 6` green |

Every slice end also needs these things:

1. `SliceDoneTest --slice N` green on a `native/main` commit, with a T2 and a T3 **on that same commit** (§6.5).
2. `t3.sh --tier slice` green on the release build of **that** commit, including `E2E-05@anim`, the `OCN_A11Y` runs of
   the six critical flows in en and ar (S-28), the `OCN_16K` boot of E2E-01, and the node check after every flow.
3. Monkey 3 seeds × 10,000 events: 0 crashes, 0 ANRs (02 S-21); StrictMode clean on the `stableQa` pass (R23).
4. The **screen-by-screen sweep** (R24) of every route the slice touched, at least, in light, dark and ar, on the
   preview APK the owner will get; its captures go to the visual auditor (02 §6.7 slice done, item 4; memory
   `test-on-emulator-not-phone`).
5. No `TODO("B-…")` left for rows of slices ≤ N (`NoStubsLeftTest`).
6. No `FLAKES.md` entry against a slice-N row past its due date.
7. The tests branches of slice N+1 exist and pass `--expect-red` (with approved `PRE_GREEN` rows, §2.7).
8. The QA record `docs/qa/native/slice-<N>-<YYYY-MM-DD>/README.md` (02 §9.4). It holds the `parity.json` excerpt,
   the flows table with run links, the verdicts, the video links and "NOT proven".

**Switch-ready, test view** (ADR §10.1 and 02 §6.7), on the candidate commit:

- `rows.green == rows.switch`, `red == 0`, `stale == 0`, `todo == 0`; `kotlin_tests.green == kotlin_tests.total`,
  `deleted == 0`;
- the T3 evidence commit equals the candidate commit;
- the `switch` tier is green (§10.4), both upgrade legs included, and E2E-41;
- 13's numbers are met or owner-relaxed;
- the owner phone test has passed (R14: the busy check, the E2E-39 Part C check and the five phone checks, then two
  days of daily use).

---

## 12. Work units for this section

One agent per unit, in its own worktree and branch (02 §7.1). Sizes are estimates [E], re-planned at M0 (ADR P10).
Section 15's units are the calendar; where they overlap, the 15 unit is named.

| # | Unit | Paths / modules | Model | When | Needs | Size [E] | Done when |
|---|---|---|---|---|---|---|---|
| W14-1 | `:core:testing` additions (§3.3) | `:core:testing` | S core (Codex) | slice 0 | 03 §10.8 base set; 04 §14.2; 16 `Canaries` | ~1.2k lines + self-tests | signatures frozen in `contracts/s1.md`; self-tests green; `OcFixtures.ROOT` found by walk-up from `~/ocn/main` and `~/wt/ocn-*` |
| W14-2 | catalog report and gates (§6) | `:quality` | coordinator + Sonnet | slice 0 | `scope.jsonl` with `proof` | ~1.7k | `parity.json` and `parity.md` from a real T2; each §6.5 gate fails on a planted error; a planted cross-commit T3 shows `stale` |
| W14-3 | test side of `t0`–`t3.sh` (VPS-built qa APKs, `am instrument` + XML conversion, detach and resume), rerun classifier, `RedReasonCheck` with `PRE_GREEN`, `FLAKES.md` automation, systemd timers | `native/tool/` | coordinator + build-logic owner | slice 0 | 02 §8 scripts | ~1.1k | `--expect-red` right on a planted tests branch; a planted flaky test lands in `FLAKES.md`; a T3 run leaves the app and rootfs installed |
| W14-4 | requests to 04 (§3.2): runtime `OcFixtures.ROOT`, the extra redaction patterns, both password variables | 04's `:tools:contracts` | (04's WP-04.7 / 15's S1-FIXT) | slices 0–1 | — | — | 04 confirms; the recorder fails on a planted `auth_token` |
| W14-5 | vectors worktrees + the first six families (incl. per-zone `recurrence`, `dartjson`) | `native/tool/flutter-fixtures/vectors`, `native/fixtures/vectors` | Sonnet | slices 0–1 | pinned Flutter | ~700 Dart + JSON | `VectorsProvenanceTest` green; a run with the wrong `TZ` fails; each family read by its test |
| W14-6 | `run.py` (hooks, steps, resume, animations), `gen_strings.py` (ARB + Android res), `lint_flows.py`, `rows.py`, `scrub.py`, `pair.py`, `a11y_sweep.py`, `gen_sweep.py`; R1–R4, R11–R16, R23, R24; `avd/create.sh` | `native/tool/e2e`, `native/tool/device`, `native/e2e/avd` | Sonnet | slice 0 (W1–W2) | `OCN_A` | ~2.2k Python/sh | E2E-01 green twice on Flutter through `run.py`; a planted canary in logcat fails the run before any scrub; a `clearState` flow is refused on a non-`OCN_*` serial; an `[adb]` hook result is asserted in a later step; `base` is unchanged after a run |
| W14-7 | acceptance list for FX-OC2, `stub_model.py` (incl. card and tools-loop prompts), FX-OC2-B, fx-proxy, the hook endpoint | `native/e2e/fixtures` | **15's S0-FX** (Codex) | W1 | — | ~1k | E2E-05 and E2E-10 green on Flutter; 13's PJ-05 runs on `perf:stream-6000` |
| W14-8 | acceptance list for FX-TLS, FX-PASEO, FX-NPM/AUTH, FX-LINUXDL, FX-GH (with the update feed), FX-GIT, FX-CODEX, FX-USAGE | `native/e2e/fixtures` | **15's S0-FX** (Codex) | W1–W3 | — | ~2.3k | each fixture up before its batch is proven on Flutter (§4.8) |
| W14-9 | flows batch 1 + E2E-39 Parts A/C | `native/e2e/flows`, `native/e2e/upgrade` | flows lane (15's S0-FLOWS) | W1–W2 | W14-6, W14-7 | 10 flows | proven on Flutter (§4.7) |
| W14-10 | flows batch 2 (the other 28) | same | flows lane (15's S0-FLOWS) | W2–W4 | W14-8 | 28 flows | same |
| W14-11 | flows batch 3: E2E-40, E2E-41 (companion), the freeze-tip flows (§4.11) | same | flows lane | slice 1 | R' built; FX-GH feed | ≈ 12 flows | proven on R/R' or `n/a (qa hook)` recorded |
| W14-12 | the phone-smoke correction: `phone: never` for E2E-01 in `slices.json` and the e2e-flows phone-smoke row (§13.2) | `native/e2e/slices.json`, `native/catalog/` copy | flows lane | W1 | — | — | `run.py` refuses E2E-01 on a non-`OCN_*` serial |
| W14-13 | Flutter baseline records + reference captures | `docs/qa/native/parity-baseline-flutter-*`, `native/e2e/refs` | T3 holder | W1–W4, slice 1 | the flows | — | one record per batch; manifest committed |
| W14-14 | the test-writer lane (continuous) | test source sets | Sonnet ×2; Opus for transcript, migration and runtime rows | slices 1–5 (15's S0-TW1 onward) | frozen contracts | ≈ 132 agent-days | every unit's tests branch passes `--expect-red` before its builder starts |
| W14-15 | widget-name index | `native/tool/flutter-fixtures/widget_index.py`, `native/catalog/widget-index.tsv` | Sonnet | slice 1 | — | ~150 | the index is committed and briefs cite it |
| W14-16 | the widget phase W-1…W-4 (§5) | — | per T14-4 | only after the owner's yes | owner | §5.4 | per §5.3 |
| W14-17 | AVD set and PC disk clean-up | `native/e2e/avd`, the AVD paths in `roles.json` | coordinator | slice 0 | T14-3 | — | the `OCN_*` set exists at its `roles.json` paths; every filesystem that holds one keeps ≥ 20 GB free |
| W14-18 | accessibility widget tests carved out of §5 (02 S-28) | `:ui:kit*`, `:ui:screenshots`, feature `commonTest` | Sonnet (kit owner reviews) | slice 4 | `:ui:screenshots` | ≈ 40 tests | the behaviours of `accessibility_guidelines` and `text_scale_overflow_*` green, text cases also at 2.5 (`terminal_accessibility` moved to `TerminalAccessibleModeTest` in slice 3 with the sign-in terminal: 06 §7.7, 17 §2.9) |
| W14-19 | slice-0 row tests (§2.11) | test sets of `:core:{common,security,model,domain}` | Sonnet (TW) | W1 | `contracts/s0.md` | ≈ 12 rows | red or approved `PRE_GREEN` with mutation proofs before S0-CORE merges |
| W14-20 | the e2e helper APK (widget host, clipboard image, accessibility event recorder) and the device-input hooks (R22) | `native/e2e/helper`, `native/tool/device` | Sonnet | slices 1–3 | W14-6 | ~800 | E2E-26 step 5 places the Usage widget on Flutter R through the helper; a WAV reaches the emulator mic; the QR poster decodes in both apps |
| W14-21 | OS-limit recipes and probes R17–R21 with their instrumented tests | `native/tool/device`, `:androidApp` androidTest | Sonnet | slices 3–4 | the features they test | ~600 | each recipe fails on a planted bug (for example a service that ignores `onTimeout`) |
| W14-22 | the script-test home and preflight (§3.9) | `desktopTest` of the shipping modules, `native/tool/preflight_scripts.sh` | Sonnet (TW) | slice 3 | — | ≈ 150 rows | every script row green under dash and bash; a missing `dash` gives exit 2 |
| W14-23 | the arm64 split check and `OCN_16K` (R3) | `native/tool/device` | Sonnet | slice 1 | the image | ~150 | a planted 4 KB-aligned `.so` fails the check |

Flows-lane load [E]: about 0.75 agent-day per flow including proving (≈ 30 agent-days for 39 flows, ≈ 9 for batch 3);
fixtures are S0-FX's 6 agent-days plus Sonnet help. The emulator is the bottleneck in W1–W4, so the T3 holder books it
per 15's slot table.

Order: W14-1, 2, 3, 6, 7, 12 and 19 in W1. W14-9 finishes by the end of W2, W14-10 by the end of W4. W14-14 runs from the
slice-1 contract freeze until slice 5.

---

## 13. Decisions and differences

### 13.1 Decisions for the owner (work proceeds on the default)

| # | Decision | Default | If the owner says no |
|---|---|---|---|
| T14-1 | Red tests that T1/T2 run live only on tests branches, never on `native/main`; device tests may land red because only the emulator can turn them green (§2.2) | Yes | Red tests merge to `native/main`, and T2 becomes a ratchet-only gate ("no previously green row fails") |
| T14-2 | Emulators: flows on API 37 (`OCN_A`; the only recent image installed), plus `OCN_U` for the upgrade journeys, `OCN_A11Y` for the large-text checks, `OCN_16K` for 16 KB pages, `OCN_36` for the owner's Android 16 and `OCN_26` for minSdk smoke (§4.6) | Yes | Install API 36 and use it for AVD-A as e2e-flows wrote; API 37 then only for T4 |
| T14-3 | Delete six stale emulators (39.6 GB: `FB_F6`, `Pixel_6`, `OCMN_Stable_1042_QA`, `OCMN_Stable_1042_Public_QA`, `OCMN_UI_Refinement`, `OCMN_Live_Demo_20260913`) and the dangling `OCMN_Luna_Audit` entry; keep `OC_API37`, `OC_API35`, `OC_Launch_Demo` | Yes | Only `OCN_A` and `OCN_U` exist; the other roles' runs wait for space |
| T14-4 | The screen-test (widget) phase: you are asked at the switch handover, with the W-1 cost estimate, and nothing starts without your yes; you are asked earlier only if the §5.2 trigger fires; the name index is made now; the accessibility screen tests are ported before the switch because the switch rules need them | Ask at the switch | Start at slice 4 with one dedicated UI test writer; or no index |
| T14-5 | Flakes: one automatic rerun; a pass on the rerun counts but opens a 2-day fix ticket; a second flake freezes merges into that module (§10) | Yes | No reruns at all: any failure is red |
| T14-6 | Switch candidate: no retries, two clean full flow runs, three clean smoke runs (§10.4) | Yes | Name the bar |

### 13.2 Differences with other sections (for the coordinator)

1. **AVD-A's API level.** e2e-flows and 02's glossary say API 36. This section uses API 37 (T14-2), because no API 36
   image is installed [V].
2. **The `qa` build type.** 13 §3.2 lists `qa` as `….native` with debug signing. 05 J2, 11 §4.3 and this section need
   `stableQa`: the main package, signed with the local key, so it installs over Flutter R and over `stableRelease`.
   Proposal: 13's row becomes `previewQa`, and `stableQa` is added.
3. **`testBuildType` and T3's instrumented step.** 03 sets `testBuildType = "release"` and 02 §8 / 03's T3 line run
   `connectedStableReleaseAndroidTest`. 11 §4.3 and 05 already run the acceptance harnesses and
   `UpgradeJourneyAssertions` on `qa`, and a module has only one `testBuildType`. Proposal: `testBuildType = "qa"`;
   APKs built on the VPS (`assembleStableQa assembleStableQaAndroidTest`), re-signed on the PC, run with `am instrument`,
   never uninstalled (§9.3). 02 §8's T3 row and 03's T3 line change their wording.
4. **Which emulator wipes.** 05 §10.4 records the dumps on `OC_API37` with `-wipe-data`, but 13 §3.7 keeps its
   `perf-base` snapshot on `OC_API37`. Proposal: dumps and journeys move to `OCN_U` (§4.6).
5. **T3's budget.** 02 §8's 20–40 min fits no tier any more: `smoke` now carries the screen sweep and `E2E-05@anim`
   (target 60 min, §4.8). The other tiers are defined in §4.8; monkey runs outside them (02 S-21).
6. **Egress and canaries.** Section 16 owns `Canaries`, `canary_scan.py` and `config/egress-allowlist.toml`; this
   section's first draft had its own `CanarySecrets`, `canaries.txt` and a `.txt` allowlist, now removed. Request to
   16: read fixture ports from `native/e2e/fixtures/ports.json` (16 §2.5 names the OC2 fixture at 4096; FX-OC2 is 4097).
   Telemetry (02 §1.1 row 17 vs memory `freeze-owner-decisions-2026-11-01`) is 16's decision; only the allowlist
   changes.
7. **The `ui` level.** 02 §6.1 has no `ui` level. §5.3 adds it when the widget phase starts; until then nothing uses
   it.
8. **The flow schedule.** This section now follows section 15 (S0-FX, S0-FLOWS) and C15-6, 15's reading of 02 §10
   item 7: batch 1 before slice-1 fan-out, all 39 by W4, all fixtures by S0-FX in W1–W3. The first draft's "one group
   per slice" is dropped, so this section adds no new difference with 02 or 15. 02 §10 item 7's literal "39 flows
   green on Flutter R before slice-1 fan-out" still differs from C15-6; that is 15's open item, not this section's.
   Batch 3 (new flows) is new work for slice 1, and the emulator booking table of 15 §4.3 needs its days.
9. **Flows per slice.** §4.8 and §11 now copy section 15 §14.2 (E2E-12 in slice 3; E2E-21 all steps in 4; E2E-19 and
   E2E-35 in 5). Step credit needs a prefix range: 15's "E2E-02 typed and code steps" and "E2E-06 approve and deny"
   must be the first steps of those flows, or the flows lane reorders them before their Flutter proof.
10. **The switch flow count.** 02 S-2 says 37/37. §4.11 adds switch-scope flows (E2E-40, E2E-41, the freeze-tip
    flows). Proposal: S-2 reads "every switch-scope flow in `slices.json`".
11. **Screen captures.** 03 S15 gives features no host tests, and 06 §15.3 captures "every feature screen preview".
    Proposal: one host-test module `:ui:screenshots` (§3.5) for screen-level captures and the widget phase's Android
    seams; a request to 03, 06 and the build-logic owner.
12. **Fixture root.** 04 §14.1 generates `OcFixtures.ROOT` as an absolute path; 03 §10.8 reads wire fixtures as JVM
    resources. Proposal (§3.2): keep 04's name, resolve it at run time by walking up from the working directory with
    kotlinx-io, declare the inputs as relative, and drop 03's resource route. 05's `dart-vectors.json` moves to
    `native/fixtures/vectors/dartjson.json`.
13. **Script paths.** Scripts under `native/tool/`, Gradle modules under `native/tools/` (§4.1). 05 and 15 name
    `native/tools/flutter-fixtures`, and 15 names `native/e2e/runner/`; both are asked to use `native/tool/…`.
14. **The phone smoke.** e2e-flows' phone-smoke row still lists E2E-01 (02 §6.7 asks the flows lane to fix it). The
    plan copy `catalog/e2e-flows.md` and its `native/catalog/` copy get the row "E2E-39 Part C by eye, then E2E-05, 06,
    08, 25, 26 by hand; E2E-01 on the emulator or the `.native` package only" (W14-12).
15. **Kotlin-test counts.** 11 §4.3 lists batch1's 46 classes; the twins come from the freeze tip (52 files, 674
    `@Test`). 11 §4.3 is asked to add the six freeze-tip classes.
16. **The public signer.** 02 names the public lineage `842284B2…1C`; AGENTS.md's signer list names a "stable CI" key
    `2D010C21…D0EC`, which memory `stable-release-1.0.44-2026-09-21` calls the CI maintainer lineage. The
    coordinator confirms which one signed the GitHub `v2.1.0+57` asset (`apksigner verify --print-certs`) before R3's
    public value is committed.
17. **Text scale for screenshot rows.** 06 §15.3 captures parts at 2.0; screenshot rows about text size are captured at
    2.5 (02 S-25, S-28), with the frame-cap semantics check (§3.5).

---

## 14. Unknowns this section depends on (slice 0 proves them)

| # | Question | How it is settled | Fallback |
|---|---|---|---|
| U14-1 | Does Maestro 2.12.0 interpolate `${APP_ID}` in `appId`? | one flow in W14-6 | `run.py` writes a copy with the literal package |
| U14-2 | Does `label:` on commands appear in reports and the debug output (per-step results, step credit)? | same | step comments plus the debug output's command list |
| U14-3 | Does `emulator -http-proxy` carry every guest TCP connection of both apps, HTTPS by `CONNECT` (the docs say all TCP)? | fx-proxy's connection log during E2E-01 | cut and throttle inside each fixture server; FX-LINUXDL throttles itself in companions |
| U14-4 | Does `-change-locale ar-EG` work on the API 37 image? | boot `OCN_A` | as root: `setprop persist.sys.locale ar-EG; setprop ctl.restart zygote` |
| U14-5 | Does `tailscale cert` plus `-dns-server 100.100.100.100` give FX-TLS a host both apps trust? | E2E-04 step 1 | use `tailscale serve` on the PC's real `.ts.net` name for the trusted case |
| U14-6 | Are test results cacheable across `~/ocn/main` and `~/wt/ocn-*` with relative inputs and the walk-up root? | T1 then T2 of the same commit shows `FROM-CACHE` | accept a longer T2 within its hard limit; one fixed checkout path |
| U14-7 | Do JUnit XML test names keep backticks and spaces through the AGP-KMP host-test task? | the first T2 | `RowIds` already accepts `-` and `_` forms; add a normaliser |
| U14-8 | Does Flutter R expose every label the flows need on the API 37 image? | batch-1 proving | `extra.*.json`; a missing label becomes a KNOWN gap (a Flutter fix only if the owner hits it, 02 §5.2) |
| U14-9 | Does networknt 3.0.8 resolve OAS 3.1 `$ref`s? (testing-perf §13 item 9) | 04 WP-04.7 | bundle with swagger-parser 2.1.48 first |
| U14-10 | Robolectric 4.17 at SDK 37 on JDK 21; headless desktop `runComposeUiTest` on the VPS | ADR P7 | `@Config(sdk = [35])` for most tests; `xvfb-run` |
| U14-11 | Do framework infinite transitions pause under the CMP test harness? (08 S8-5) | ADR P7 kit test | `stillFrame` everywhere in tests |
| U14-12 | Does `cmd statusbar add-tile` work on the API 37 image? | R10 | add the tile through the shade's edit mode with UIAutomator |
| U14-13 | Does the androidTest manifest keep several `<instrumentation>` elements, and does `am instrument` run each harness against the R8-minified `qa` app (keep rules for test-referenced classes)? | the first T3 of slice 1 (S1-LINUX) | one harness per test APK variant; widen the `qa` keep rules |
| U14-14 | Maestro 2.12.0 `killApp`, `setClipboard`, `setAirplaneMode`, and `runScript` with `env` and `http.post` returning JSON | W14-6 | an `[adb]` hook for each missing command |
| U14-15 | Host mic feed: `-allow-host-audio` plus `PULSE_SOURCE` on this PC's PipeWire | W14-20 | a virtual mic inside the guest is not available; E2E-13's speech steps become a KNOWN gap with the coordinator's OK |
| U14-16 | `-virtualscene-poster` on API 37, and both decoders reading it | W14-20 | v4l2loopback webcam fed by ffmpeg (needs the owner's OK for a kernel module) |
| U14-17 | A 16 KB page-size x86_64 image for API 36/37; ARM translation for an arm64-only install | W14-23 | the API 35 16 KB image; the ELF and zipalign checks alone |
| U14-18 | The `device_config` keys for `shortService` and the phantom limit, and the time-zone shell command, on API 37 | W14-21 | instrumented tests that call the service's timeout path directly |
| U14-19 | Does `appwidget grantbind` let the helper bind `hide_from_picker` providers on API 37? | W14-20 | UIAutomator through the launcher's full widget list where the launcher shows hidden providers; otherwise a KNOWN gap for E2E-26 step 5 on Flutter |

---

## 15. Sources

- Binding: `plan/01-architecture-decision.md` (§1, §4 rows 16–18, §5.2, §6.3, §7.2, §8, §9.1, §9.7, §9.9, §10, §11,
  §12); `plan/02-principles-scope.md` (§3, §4 S-1…S-29 and the S-4 sets, §5.3, §6, §7, §8, §9, §10, NG11, NG12).
- Plan sections read: 03 (§1, §4, §7.1 module table and S15, §8, §10.1, §10.8, K11, §19.6, §20.7, §21.1, §22); 04
  (§14); 05 (§2.5, §10, §11); 06 (§15); 08 (§9); 09 (§14, §17 cards); 11 (§4.3, §11.6); 13 (§3.1–§3.8); 15 (§4.2
  S0-FX, S0-FLOWS, S0-TW1, S0-CORE; §6; §14.2; C15-6, C15-12); 16 (§1.2 `Canaries`, §2.5 harness, §5 analytics queue).
- Catalog: `catalog/BEHAVIORS.md` (summary, MIGR, Appendices B–E), `catalog/behaviors.jsonl` (all counts in §1 and
  §3 computed today), `catalog/e2e-flows.md` (rules, environment matrix, phone smoke, fixtures, stub prompts, E2E-01,
  E2E-02, E2E-05, E2E-13, E2E-15, E2E-26, E2E-39, coverage).
- Research: `research/testing-perf.md` (all sections); `research/kmp-cmp-status.md` (task naming, headless desktop).
- Repo, read-only (`/home/eslam/Storage/Code/wt/oc_app-batch1` at `65e3f70a3`; `/home/eslam/Storage/Code/wt/oc_app-freeze`
  at `fc5c1e78f`): `test/` (counts, script test files), `test/fixtures/api2/` (24 files), `tool/qa/issue87/mock_llm.py`,
  `tool/qa/codex_fixture/`, `tool/qa/fq3/device.dart` (password variables), `integration_test/`, `tool/qa/*live*`,
  `engine/phone` (204 `#[test]`), `android/app/src/test` (batch1 46 files / 645 `@Test`; freeze 52 / 674),
  `android/app/src/androidTest` (13 Kotlin files + `RuntimeQaGuard.java`, custom `Instrumentation` classes),
  `android/app/src/main/res/xml/widget_*_info.xml` (5 of 7 providers `hide_from_picker`),
  `android/app/src/main/res/values{,-ar}/strings.xml` (143 strings each), `BuiltinLinux.kt:4445`,
  `lib/domain/agent_catalog.dart:286`, `lib/scheduling/recurrence.dart`, `lib/scheduling/controller.dart`,
  `lib/domain/git/github_device_flow.dart`, `lib/builtin/agents/gen_ui_install_scripts.dart`,
  `assets/agents/paseo-package-lock.json` (Paseo 0.11.2), AGENTS.md.
- This PC, checked 2026-11-01 [V]: `~/.android/avd/*.ini` paths (AVDs on `/`, Storage and Storage2; `OCMN_Luna_Audit`
  dangling), `du` of the stale AVDs (39.6 GB), `df` at 16:35 (`/` 25 GB, Storage 29 GB, Storage2 60 GB free), the PC's
  zone (+04), `~/Android/Sdk/system-images` (34, 35, 37.0), ImageMagick 6.9.12-98.
- Web, 2026-11-01 [V]: Android emulator command line (`-snapshot` saves back on exit, `-no-snapshot-save`,
  `-http-proxy` carries all TCP, `-no-audio`) at https://developer.android.com/studio/run/emulator-commandline;
  foreground-service timeouts (`am compat enable FGS_INTRODUCE_TIME_LIMITS`,
  `data_sync_fgs_timeout_duration`) at https://developer.android.com/develop/background-work/services/fgs/timeout;
  Kotlin power-assert (`functions` default `kotlin.assert` only) at https://kotlinlang.org/docs/power-assert.html;
  Ubuntu `cron(8)` (one time zone, no `CRON_TZ`) at https://manpages.ubuntu.com/manpages/jammy/man8/cron.8.html;
  Maestro CLI reference (`--format JUNIT|HTML|NOOP`, `--output`, `--test-output-dir`, `--debug-output`,
  `--include-tags`, `--exclude-tags`, `-e`, `--shard-split`/`--shard-all`) at
  https://docs.maestro.dev/maestro-cli/maestro-cli-commands-and-options and
  https://docs.maestro.dev/cli/test-suites-and-reports; Maestro `retry` command (`maxRetries` 0–3, default 1) at
  https://docs.maestro.dev/api-reference/commands/retry.
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `build-vps`,
  `vps-test-env-gaps`, `vps-suite-shared-dir-trap`, `shared-emulator-contention`, `emulator-lock-holder`,
  `background-task-killer`, `test-on-emulator-not-phone`, `owner-tests-i-gate`, `pre-existing-means-last-green`,
  `no-full-suite-for-unrelated-changes`, `audit-trail-rule`, `live-video-proof`, `apk-build-numbers`,
  `restore-proof-recipe`, `hog-watch-monitor`, `pc-capacity-ceiling`, `independent-visual-auditor`,
  `one-screen-per-image`, `no-owner-phone-screenshots`, `owner-phone-debug-allowed`, `phone-logcat-silenced`,
  `launch-video-2026-10-19`, `opencode2-stable-2.0-port`, `proot-orphans-heat`, `verify-fixes-with-a-failing-test`,
  `no-heavy-workflows`, `judge-visuals-hard`, `short-replies-rule`, `storage2-offload`, `stable-release-1.0.44-2026-09-21`,
  `text-scale-cap`.
- The owner's words relayed for this run: "7400 for widgets can come later."

---

## Review notes (2026-11-01 revision)

Every issue was checked against 01, 02, 03, 04, 05, 11, 15, 16, the batch1 and freeze worktrees, this PC and the web
before editing. All 53 were real; none was rejected outright. Duplicates were merged, and a few fixes were adapted
where the suggested form did not fit the facts.

**Changed:**

- **Owner phone (both blockers on R14):** R14 now matches 02 §6.7: busy check, Part C by eye, then the five phone
  checks (E2E-05, 06, 08, 25, 26); E2E-01 is `phone: never`; `run.py` refuses `clearState` off `OCN_*` AVDs;
  `mapping.txt` kept per owner build. The catalog's phone-smoke row is not edited here (outside this file); W14-12 and
  §13.2 item 14 carry it.
- **In-app update (blocker):** FX-GH serves a real update feed with bad-checksum, wrong-signer and cut cases; new flow
  E2E-41 runs N → N+1 through the app's own updater in the `upgrade` and `switch` tiers (§4.11).
- **S-28 (blocker):** `OCN_A11Y` (360×740, 2.5, en/ar, 320 dp case), the node check after each flow and the screen
  sweep (R24), and the accessibility widget tests carved out of §5 as W14-18 in slice 4.
- **T3 mechanics:** qa APKs built on the VPS, re-signed on the PC, run with `am instrument`, never uninstalled;
  harnesses stay in `:androidApp` per 11 §4.3 and each step becomes a synthetic test case; `-no-snapshot-save`
  everywhere with explicit saves and checked snapshot provenance; the lock holds for the tier budget with a timeout
  check and stale-holder recovery; detached runs with `flows.jsonl` and `--resume`.
- **02 alignment:** `stale` status and `proof: t1|t3`, same-commit T3 only, T3-proven tests may land compiled-but-red;
  `PRE_GREEN` with mutation proofs; the Kotlin-test ratchet from the freeze tip (52 / 674); `E2E-05@anim` in every
  T3; monkey 3 × 10,000 at slice ends and the StrictMode scan; S-18/S-19 probes (R21); the full S-4 protected and
  runtime sets with `holdRuntimeRestoreUntilManifest`; both S-3 lineages with per-build seeds and the retired widget
  provider; analytics in the canary scan; 02's 1,336 / 208 / ≈ 224 numbers, read from `scope.jsonl` by every gate.
- **Section 16 and 04 own their parts:** `CanarySecrets`, `canaries.txt` and the `.txt` allowlist removed in favour of
  16's `Canaries`, `canary_scan.py` and `.toml`; the logcat grep replaced by a whole-buffer scan before any scrub;
  fixtures serve the canary values. The fixture tree, recorder and replay server defer to 04 §14, with two requests
  (extra redaction patterns, both password variables).
- **Flutter-side truth:** Linux setup on both apps uses the real pinned hosts through fx-proxy tunnels, FX-LINUXDL only
  in companions; companions get `flutterProof: n/a (qa hook)`; per-zone `TZ` runs for recurrence vectors with a zone
  assertion; vectors may name a row's `sources_commit`; Android resource strings join the strings table.
- **Homes and plumbing:** `:ui:screenshots` for screen captures; one fixture-root mechanism (walk-up in common code);
  JUnit Platform with the vintage engine and a guard; power-assert function list; PC compares informational; the
  script-test home with dash/bash and a failing preflight; systemd timers instead of `CRON_TZ`; `qa-hooks.json`
  ownership, SELinux label and a loaded marker; `[adb]` hooks through `runScript` to a run.py endpoint; `native/tool/`
  vs `native/tools/` rule; `NoStubsLeftTest` keyed by row; a slice-0 test writer (W14-19); `native-seeded` for monkey.
- **Coverage gaps:** flows top-up for freeze-tip features (on R'), E2E-40 for generative UI cards, OS-limit recipes
  R17–R20, device-input hooks and the helper APK (R22, R10), the arm64 split check and `OCN_16K`, the screen sweep in
  every smoke tier, the table of suites outside `test/`, text-size rows at 2.5.
- **Schedules:** flows follow section 15 (batches by W2 and W4, fixtures by S0-FX in W1–W3, flows per slice from
  15 §14.2); §5.2 no longer starts anything without the owner's yes.

**Adapted, with the reason:**

- Disk (two issues): the premise "Storage2 has 7.3 GB free" was out of date; at 16:35 today Storage2 had 60 GB, Storage
  29 GB and `/` 25 GB. The per-filesystem rule, per-AVD paths, partition sizes and snapshot caps were adopted anyway,
  with recomputed numbers. `OCMN_Luna_Audit` is a dangling `.ini` (0 bytes), not a seventh AVD.
- Lock timeout: a flat 60-second cap would fail whenever another holder keeps the lock for more than a minute, so the
  loop waits for flock's own 30 minutes plus 60 s and exits at once when flock gives up.
- Hook endpoint: port 8897, not 8898, which is fx-proxy's control port.
- `agentAuthBaseUrl` was not added: the fake agent packages carry FX-AUTH's address; `npmRegistryUrl` was.
- "Wrap every acceptance step in a JUnit method" became one synthetic test case per step, because the harnesses are
  custom `Instrumentation` classes with no `@Test`.
- Step credit: only a prefix range (S1…Sk) can be judged, because a Maestro run stops at its first failure; §13.2
  item 9 asks section 15 to use prefixes.
- Per-run random passwords were dropped rather than added to the scan list; the fixed canary values make leaks
  reportable, and only the harness's own outputs are scrubbed, after the scan.

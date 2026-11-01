# 14. Testing and parity: tests first, from the catalog

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR) and `02-principles-scope.md`. Where this
file and either of them differ, they win, and the difference is listed in §13.2 for the coordinator. Sections 03, 05,
06, 08, 09, 11 and 13 already define parts of the test system. This section reuses their names and only adds what
they leave open. Tags: **[V]** checked today against a primary source or on this machine; **[R]** read in the repo, a
map, a research file, a plan section or a memory file; **[E]** my estimate, replaced by the measured value; **[U]**
unverified, proven in slice 0 (§14).

## For the owner, in plain words

1. Before an agent builds anything, a different agent writes its tests from the list of 1,544 things today's Flutter tests prove. The builder's only job is to turn those tests green.
2. The 37 phone flows are first made to pass on today's Flutter app. The same flow files must then pass, unchanged, on the new app. That is how we know nothing was lost.
3. A parity page shows what share of behaviours pass, overall and per area. It is updated at every integration check, and the number may only go up.
4. The 7,967 screen tests wait until you schedule them, as you said. Only free preparation happens now, and a measured trigger tells you if waiting starts to cost us.
5. Every slice ends with its tests green, its phone flows green on the release build, an emulator sweep and a short video. You get an APK only after all of that.

Decisions this section adds (work goes ahead on the default until you answer; §13): T14-1 red tests never land on the
main branch; T14-2 the emulator set; T14-3 free about 40 GB on the PC by deleting six old emulators; T14-4 when the
screen-test phase starts; T14-5 the flake rule; T14-6 the stability bar for the switch candidate.

---

## 0. What this section owns

| Topic | Owner | This section adds |
|---|---|---|
| Parity terms, row status, done, check ladder T0–T4, commit trailers | 02 §6, §8, §9 | the machinery: lanes, branch model, runner, dashboard, flake rule, budgets |
| Module and task names, `:quality` gates, `:core:testing` list | 03 §8.3, §10.8, §19.6 | the extra fakes and gates in §3.3 and §6.5 (requests to their owners) |
| Migration tests and upgrade journeys J1–J8 | 05 §10 | how they sit in the T3 tiers, the seed flows' authorship, the device recipes (§8) |
| Kit capture matrix, capacity, accessibility | 06 §15 | the screenshot-row rule and the visual verdicts (§3.5, §7) |
| Motion checks | 08 §9 | — (referenced only) |
| Budgets, benchmarks, Perfetto, journeys PJ-* | 13 | fixtures FX-OC2 and the replay server, which 13 §3.8 assigns here |
| Device flows (39), fixtures, Flutter baseline, step → row mapping | **this section** (02 §6.1, §10 item 7) | all of §4 |
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
| Rows in switch scope | ≈ 1,294 [E] (02 §3.3); about unit 590 · integration 385 · contract 190 · migration 54 · screenshot 53 · e2e 26 by the same filename rule [E] | 02 §3.3; my rerun of its rule gives 1,308 |
| Flows at the switch | 37 of 39 (E2E-28 AI Team and E2E-32 Memory follow D6) | 02 §3.4 |
| Flutter test sources the rows cite | 7,106 references to 800 distinct test files | `behaviors.jsonl` [V] |
| Kotlin tests that carry over | 46 JVM files with 645 `@Test`; 14 instrumented acceptance files (step-driven, no `@Test`) | `android/app/src/{test,androidTest}` [V] |
| Native tests at the switch | ≈ 3,800 [E]: ≈ 1,700 row tests (1.3 per row) + ≈ 1,500 kit, motion and gate tests + the 645 carried | estimate |
| Native screenshots at the switch | ≈ 3,300 [E]: ≈ 2,600 kit + ≈ 600 screens (06 §15.3) + ≈ 130 motion frames (08 §9.4) | 06, 08 |

---

## 2. Test-first: how a catalog row becomes a native test before its code exists

### 2.1 The lanes

| Lane | Model | Works on | Produces | Never does |
|---|---|---|---|---|
| **Test-writer lane** (02 §7.6) | Sonnet 5.5; Opus 5.5 for rows of `:engine:transcript`, `:android:migration` and the runtime split (ADR §10.3) | the rows of slice N+1 while builders do slice N | one `native/s<N>-tests-<unit>-<date>` branch per builder unit, with red tests named after rows | writes product code other than contract stubs (§2.3) |
| **Fixture lane** | Codex (recorder, replay server); Sonnet (Python fixture servers) | wire recordings and fixture servers a slice needs, one slice ahead | `native/fixtures/wire/**`, `native/e2e/fixtures/**` | records against the owner's live servers |
| **Flows lane** | Sonnet writers (at most 2) plus one emulator holder (the T3 holder) | the 39 Maestro flows, Flutter first | `native/e2e/flows/**`, the Flutter baseline record | changes the Flutter app to make a flow pass |
| **T3 holder** (02 §6.9) | Sonnet, one at a time | device runs | `build/native-t3/<sha>/` + QA records | lends the emulator mid-run |
| **T4 holder** (13) | Sonnet | Macrobenchmark, Perfetto | `perf/results/**` | — |
| **Builders** | Codex (core, protocol, engine), Sonnet (kit, features, `:android:*` ports), Opus (hard units) | make red tests green | product code | weaken, skip or delete an assertion (02 §7.6) |

The test writer and the builder of a unit are always **two different agents**. The point of test-first is that the
tests come from the row and the Flutter test, not from the native code.

### 2.2 Branch model: red tests never land on `native/main` (T14-1)

`native/main` is always all-green. A red test lives only on branches, until the builder of its unit turns it green.

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
4. **Merge rule:** the coordinator merges the builder branch (which contains the tests branch) only when every row of
   the unit is green at T1 (02 §6.7 unit done).
5. **Consequences:** T2 on `native/main` is a plain "everything passes" gate. `native/LAST_GREEN` means what it says.
   "Pre-existing failure" can only mean a regression since the last green commit (memory
   `pre-existing-means-last-green`).

If a row cannot go green inside one unit (it needs two units), the coordinator splits it in `scope.jsonl` with
`split_from` (02 §6.1), or moves it to the later unit. It never lands red.

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
2. `NoStubsLeftTest` (`:quality`, T2) fails when `commonMain` or `main` of a module whose slice is closed still holds
   `TODO("B-`. Open slices may hold them; the preview APK never reaches them, because routes of unbuilt features are
   absent (07 §2.8 "absent, never disabled").
3. Stubs never invent behaviour. A method the row needs but the contract lacks is a "Requests to owners" line in
   `native/contracts/s<N>.md` (02 §7.4). The coordinator amends the contract; the test writer waits for that.

### 2.4 Writing one row test, step by step (the test-writer recipe)

1. Print the row with everything it carries:
   `python3 -I native/tool/catalog/row.py B-STATE-040`
   It prints the `behaviors.jsonl` line, the `scope.jsonl` line (scope, slice, module, amendment), the flow that
   proves it (from `native/catalog/flow-rows.json`), and each Flutter `sources` path with the test name read at that
   line in the reference worktree (02 §6.2).
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
   `NotImplementedError` naming a row of this brief (§2.7).
9. Commit one commit per row group, subject `test(engine-delivery): <plain words> [skip ci]`, trailer
   `Behaviors: B-STATE-040, …` (02 §9.1).
10. When the unit's rows are all written: `native/tool/t1.sh --expect-red` on the tests branch, then the final report
    (02 §7.14) with the list of rows and the red reason of each.

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
- Assertions are `kotlin.test` with power-assert on (ADR §8), so a failure prints every sub-value.

### 2.6 Flutter-generated vectors (expected values computed by the reference build)

When a row's truth is a table of inputs and outputs that Dart code computes, the native test reads vectors produced
by **running the Flutter code** at the reference commit. The Flutter implementation becomes the oracle, case by case.

- **Where it runs:** a dedicated, writable worktree `/home/eslam/Storage/Code/wt/oc_app-vectors`, detached at the
  reference commit (02 §6.2: `65e3f70a3` now, `flutter-final` later), on branch `tools/flutter-vectors`. That branch is
  never merged anywhere. Agents still never edit `oc_app-batch1`.
- **Generators:** `native/tools/flutter-fixtures/vectors/<family>_vectors_test.dart` (05 already keeps
  `tools/flutter-fixtures/`). `native/tools/flutter-fixtures/vectors/run.sh <family>` copies the generator into the
  vectors worktree's `test/vectors/`, runs it with the pinned Flutter (AGENTS.md:
  `~/.shorebird/bin/cache/flutter/91f8bd75…/bin/flutter test test/vectors/<family>_vectors_test.dart`), and copies the
  JSON it writes to `native/fixtures/vectors/<family>.json`. It runs on the VPS or the PC; both have the pinned Flutter
  (memory `build-vps`).
- **File format:**

```json
{"family":"recurrence","flutterCommit":"65e3f70a3","generator":"native/tools/flutter-fixtures/vectors/recurrence_vectors_test.dart",
 "generatedAt":"2026-11-05","rows":["B-STATE-093"],
 "cases":[{"name":"daily 09:00 Europe/Berlin across spring-forward","in":{"rule":"daily","at":"09:00","zone":"Europe/Berlin","after":"2027-03-27T10:00:00Z"},
           "out":{"next":"2027-03-28T07:00:00Z"}}]}
```

- `VectorsProvenanceTest` (`:quality`) checks that every vector file names a generator that exists, a commit equal to
  `native/catalog/REFERENCE` (one line: the commit of R), and at least one row. When R changes, the fixture lane
  regenerates every family and the diff is reviewed like a behaviour change.

| Family | Rows (examples) | Native consumer |
|---|---|---|
| `dartjson` (byte-exact `jsonEncode`, fingerprints) | STATE fingerprint rows; 05 §2.5 | `DartJsonTest` (`:core:common`) |
| `redaction` | B-SEC redaction rows, B-NAV-004 titles | `RedactorVectorsTest` (`:core:security`) |
| `address-rules` | B-SEC-027 and the cleartext-consent rows | `AddressRulesTest` (`:core:security`) |
| `capability-defaults` | B-PROTO-124, B-AGENT-231 | `CapabilityDefaultsTest` (`:core:domain`; 03 §22.1 asks for this file) |
| `recurrence` (zones UTC, Europe/Berlin, America/New_York, Australia/Lord_Howe) | B-STATE-092, B-STATE-093 | `RecurrenceDstTest` (`:engine:schedule`) |
| `sse-frames` (the 9 vectors of `test/api2_sse_test.dart` + chunk-boundary cases) | PROTO stream rows | `SseParserTest` (`:core:net`) |
| `failure-keys` (failure → copy key, not text) | B-NAV-007, B-NAV-008, B-NAV-009 | `FailureWordsTest` (`:ui:words`) |
| `settings-search` | B-NAV-071 | `SettingsSearchTest` (`:feature:search`) |
| `model-recommendations` | B-NAV-033…036 | `ModelPickerRulesTest` (`:engine:integrations`) |
| `prose-for-speech`, `path-chips` | B-CHAT-060, B-CHAT-114, B-CHAT-026 | `:core:markdown` tests |

Add a family whenever a row has more than about ten input cases. A row with a few hand-readable cases keeps its literal
values in the test.

### 2.7 "Red for the right reason" (`t1.sh --expect-red`)

The tests branch passes its own gate when **every** row test of the brief fails, and fails for an accepted reason:

| Accepted red reason | Not accepted (the test-writer unit is not done) |
|---|---|
| `AssertionError` (power-assert output) | compile error; `ClassNotFoundException`; a missing fixture file |
| `NotImplementedError` whose message names a row of **this** brief | `NotImplementedError` naming another unit's row (the test reaches too far) |
| Turbine `AssertionError` (timeout, wrong item) | a coroutine test timeout with no assertion (a hung test) |
| Roborazzi verify difference (screenshot rows) | `UncaughtExceptionsBeforeTest`, setup crashes |

`t1.sh --expect-red` reads the JUnit XML, classifies each failure with these rules (`:quality` `RedReasonCheck`), and
exits 1 if any brief row passes (a test that passes against a stub proves nothing) or fails for an unaccepted reason.

### 2.8 The builder's side

1. Start from the tests branch (§2.2), read the contract, make the red tests green. Never edit an assertion. A wrong
   test goes back to the test-writer lead, and the fix carries `Test changed because <reason>` (02 §9.1).
2. **Mutation proofs:** before the final report, the builder picks 3 rows of the unit (the coordinator may name them),
   reverts the one line that makes each row pass, runs that row's test, records the failing message, and restores the
   line. The three proofs go into the unit's QA record (02 §9.4). This is the native form of memory
   `verify-fixes-with-a-failing-test`, and it catches tests that pass for the wrong reason.
3. Every bug fix after merge follows the same rule: a test that was watched failing without the fix.

### 2.9 Kotlin tests that already exist

- The 46 JVM test classes (645 `@Test`) move with their subjects into `:android:*` `src/test` (11 §4.3 lists them).
  They keep their names. Where one already proves a catalog row, the mover adds the row id to the front of the test
  name and the header of §2.5, so it counts.
- The 14 instrumented acceptance harnesses are step-driven (`am instrument -e step …`, with Python drivers under
  `tool/qa/bb*`). The `BuiltinRuntime*` set moves to `:android:linux` `androidTest` (11 §4.3). Each step that proves a
  row gets a thin JUnit wrapper named after the row (`B_LINUX_029_…`), so its result reaches the dashboard.

### 2.10 Rows that move during the rewrite

| Event | What happens | Who |
|---|---|---|
| Catalog top-up (02 §3.5): logic tests added on the freeze line after `65e3f70a3` | new rows with the next free id; their tests are written in the slice that owns them | test-writer lead |
| Flutter fix during F1 (02 §5.3, `Behaviors:` trailer) | the row is added or amended; the native test is written **in the same week**, red on a tests branch, green with its unit | test-writer lane |
| ADR or owner amendment (02 §3.6) | the native test asserts the amended text; the header quotes it | test writer |
| A row too big for one unit | split with `split_from` | coordinator |
| A row that turns out untestable as written | the test-writer lead writes the reason; the coordinator amends the row; never `@Ignore` | lead + coordinator |

### 2.11 Throughput and calendar [E, replaced by slice-0/1 measurements, ADR P10]

| Level | Rows/agent-day (test writing only) | Switch rows | Agent-days |
|---|---|---|---|
| unit | 20 | ≈ 590 | ≈ 30 |
| contract | 8 (recording included) | ≈ 190 | ≈ 24 |
| integration | 8 | ≈ 385 | ≈ 48 |
| migration | 3 | ≈ 54 | ≈ 18 |
| screenshot | 8 | ≈ 53 | ≈ 7 |
| **All** | | ≈ 1,272 | **≈ 127 (≈ 25 agent-weeks)** |

With two test-writer agents in parallel, that is about 13 calendar weeks, which fits "one slice ahead" over slices
0–5. The e2e rows are the flows lane's work (§4.8). If slice-1 throughput comes in more than 30 % under these numbers,
the coordinator adds a third test writer before slice 3. The PC ceiling (memory `pc-capacity-ceiling`) is not
affected, because test writers run their checks on the VPS.

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
    val vectors = Vectors.load("recurrence")                 // :core:testing, reads native/fixtures/vectors/recurrence.json
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

**Fixture layout** (all under `native/fixtures/`, read by `WireFixtures`; the convention plugin passes the absolute
root to every `Test` task as the system property `oc.fixtures.root`, a request to the build-logic owner):

```
native/fixtures/
├── wire/<server>/<version>/<scenario>/NNN-<METHOD>-<slug>.json   one exchange: {request:{…allowlisted headers…}, response:{status, headers, body|bodyFile}}
│                                      NNN-SSE-<slug>.sse         raw text/event-stream bytes; pacing as legal comments ": t=+40ms"
│                                      NNN-WS-<slug>.jsonl        one WebSocket frame per line {dir:"in"|"out", t:+ms, text|b64}
├── wire/legacy/<dir>/…               the Flutter fixtures copied byte for byte (test/fixtures/api2 (24 files), paseo, genui,
│                                      host_agent_providers, office); twins in native/TWINS.md (02 §5.6)
├── openapi/opencode2-2.1.1.json      snapshot of the pinned server's OpenAPI 3.1 (twin of the root contracts/ file, or a fresh /doc)
└── vectors/<family>.json              §2.6
```

`native/` never reads files outside itself (03 §1.1), so the OpenAPI snapshot and the legacy fixtures are copies,
tracked as twins.

**The recorder** (`:tools:wire-recorder`, a JVM CLI like `:tools:strings`; request to the build-logic owner for the
module and for test-only Ktor server artifacts, which never enter the app classpath):

```bash
# 1. a real server at the pinned version, started by exact PID; its password only in an env var, never echoed
OC2_PASSWORD="$(openssl rand -hex 16)" opencode2 serve --port 4097 --hostname 127.0.0.1 & OC2_PID=$!
# 2. the recording proxy between a driver and the server
./gradlew :tools:wire-recorder:run --args="proxy --listen 127.0.0.1:4197 --upstream http://127.0.0.1:4097 \
   --server oc2 --version 2.1.1 --scenario prompt-roundtrip --out fixtures/wire --secret-env OC2_PASSWORD"
# 3. the driver: a scripted client (scenarios/oc2/prompt-roundtrip.kts) or the Flutter R app pointed at :4197
./gradlew :tools:wire-recorder:run --args="drive --script scenarios/oc2/prompt-roundtrip.kts --target http://127.0.0.1:4197"
kill "$OC2_PID"                       # exact PID only (AGENTS.md; memory pgrep-self-match-wait-trap)
```

Redaction is part of the recorder, not a later step:

- drop `Authorization`, `Cookie` and `Set-Cookie`;
- mask JSON fields named `password`, `token`, `apiKey`, `key`, `secret` and `authorization`;
- replace provider keys in `/config/providers` bodies with `"<redacted:provider-key>"` (AGENTS.md: never echo
  provider credentials);
- **fail the recording** if the value of `--secret-env`, or anything matching `sk-[A-Za-z0-9-]{8,}`, survives
  anywhere in the output.

**Replay** (`:core:testing`, Ktor `MockEngine` 3.6.1; the sketch in testing-perf §8.3 made concrete):

```kotlin
public class FixtureEngine(private val scenario: Scenario, private val scope: CoroutineScope) {
    public val engine: HttpClientEngine                    // MockEngine, reuseHandlers = false (strict order)
    public val unmatched: List<HttpRequestData>            // requests no exchange matched
    public fun assertAllConsumed()                         // fails on leftover expected exchanges
}
public class Scenario internal constructor(public val exchanges: List<Exchange>) {
    public companion object { public fun load(path: String): Scenario }   // "oc2/2.1.1/prompt-roundtrip"
}
public data class SseFrame(val delayMs: Long, val raw: String)            // from ": t=+40ms" comments
```

- Matching: method + path + an allowlist of query keys + body shape (JSON keys, not values, unless the exchange says
  `matchBody: exact`).
- SSE responses are written into a `ByteChannel` from `scope` (the `runTest` `backgroundScope`), so the `t=` delays are
  virtual time.
- WebSockets (OpenCode PTY, Paseo): a `FakeWebSocketSession` replays `NNN-WS-*.jsonl`, and asserts the outgoing frames
  in order.

**Schema check:** `OpenApiConformanceTest` in `:protocol:oc2` `desktopTest` (JVM-only; networknt json-schema-validator
3.0.8) validates every recorded `oc2/2.1.1` response body against the snapshot's operation schema
([U]: whether networknt resolves OAS 3.1 `$ref`s directly or needs a bundled schema; testing-perf §13 item 9).

**Initial recording sets** (fixture lane, one slice ahead of the rows that need them):

| Server @ pinned version | Scenarios | For slice |
|---|---|---|
| OpenCode 2 @ **2.1.1** (pinned on the phone [V `lib/` and memory]) | probe/health, auth 401, pairing payload, sessions paged, create, prompt round-trip (SSE), permission ask/answer, question, form pending/answer/invalid, abort, reconnect then refetch, providers (scrubbed), models, commands, files list/read, revert, PTY WebSocket | 1–2 (PTY 5) |
| OpenCode 2 replay-only | the 2.0.29-style 400 on the pending list, 503, a stuck permission, a dropped stream mid-turn (memory `oc2-stuck-tools-root-cause`) | 2 |
| Paseo @ **0.11.2** [V `assets/agents/paseo-package-lock.json`] | daemon probe, agent list with models and modes, create and resume, chat stream, permission, terminal stream, update versions | 3 |
| Codex app-server (the version 04 pins) | health, threads, a turn with an approval, steer, abort, socket drop; start from `tool/qa/codex_fixture/` transcripts [R] | 5 |
| Flutter legacy fixtures | copied as they are (above) | as their rows need them |

When a pin moves (02 §5.6 twin rule), the fixture lane re-records the affected scenarios, refreshes the OpenAPI
snapshot, and lets the contract tests show what changed.

### 3.3 integration (446 rows)

Several real parts together, fakes only at the edges. Same `commonTest` home; Android parts as Robolectric host tests.

**Fakes catalogue.** 03 §10.8 lists the base set: `TestDispatchers`, `TestScheduler.asClock()`, `FakeKeyValueStore`,
`FakeSecretStore`, `FakeBlobStore`, `FakeServerGateway`, `FakeOperationsGateway`, `FakeForegroundWork`,
`FakeDeviceFacts`, `WireFixtures`, `catalogRow()`, and `RecordingNavigator` in `:ui:testing`. This section requests
these additions from the S core owner and the kit owner. Their signatures freeze with slice 0:

| Fake | Module | What it does | Rows that need it first |
|---|---|---|---|
| `FixtureEngine`, `Scenario`, `FakeWebSocketSession` | `:core:testing` | §3.2 | PROTO, AGENT contract rows |
| `Vectors.load(family)` | `:core:testing` | §2.6 | many |
| `FakeRuntimeHost` | `:core:testing` | scripted install/start/probe/stop results, a `/proc`-like process table, disk and RAM facts | LINUX rows of slice 3 |
| `FakeAgentHost` | `:core:testing` | scripted agent install, sign-in, readiness and process counts (32-process limit) | AGENT rows of slice 3 |
| `FakeActivityResults`, `FakePermissionGate` | `:core:testing` | scripted picker, camera and permission outcomes (ADR §7.3) | MEDIA, ANDR rows |
| `RecordingLogWriter` | `:core:testing` | a Kermit `LogWriter` behind the real `RedactingWriter`, keeping every line for assertions | SEC, MIGR privacy rows |
| `CanarySecrets` | `:core:testing` | fixed fake secrets (`sk-e2e-FAKE-0123456789abcdef`, `canary-pass-7c1e`) + `assertNoCanary(text)` | SEC rows, B-MIGR-011, S-22 |
| `TestFiles` | `:core:testing` | a temp directory per test (kotlinx-io paths), deleted after | STATE, MEDIA rows |
| `FakeKitPlatform` | `:ui:testing` | records haptic kinds, link opens, frame-rate votes; fixed insets and reduced motion | KIT rows |

**Robolectric (Android-only behaviour):** Robolectric 4.17 on a JDK 21 test toolchain, SDK 37 (ADR P7). Real
`SharedPreferencesImpl`, `ShadowNotificationManager` for posted notifications, `work-testing` `TestDriver` for
WorkManager, `ShadowAlarmManager` for alarms, `ApplicationProvider` for the graph smoke.

**What needs the emulator anyway** (instrumented, T3): the real `AndroidKeyStore` (05 §10.3; testing-perf §13 item 8),
proot running real binaries (`:android:linux` acceptance), sherpa-onnx loading its native library, the isolated
`PdfRenderer` service, widgets on a real launcher and the notification shade (UIAutomator 2.4.0, 11 §11.6).

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
3. **The journeys sit in the T3 tiers of §9.3:** J1–J3 in `T3-upgrade` (every candidate that touches migration code and
   every owner APK until the switch, ADR §10.2); J4–J8 in `T3-full` and on the switch candidate.

### 3.5 screenshot (60 rows)

| Area | Rows |
|---|---|
| KIT (26) | B-KIT-001, 003, 004, 005, 007, 010, 011, 012, 014, 015, 016, 019, 023, 027, 028, 029, 031, 032, 036, 042, 043, 044, 045, 055, 058, 060 |
| NAV (15) | B-NAV-014, 019, 023, 027, 029, 037, 044, 047, 048, 052, 055, 060, 062, 074, 079 |
| CHAT (6) | B-CHAT-004, 016, 056, 061, 063, 123 |
| ANDR (4) | B-ANDR-008, 018, 042, 043 |
| AGENT (3), LINUX (3) | B-AGENT-039, 210, 294; B-LINUX-172, 179, 238 |
| SEC, L10N, XPLAT (1 each) | B-SEC-133; B-L10N-018; B-XPLAT-003 |

A screenshot row's native test is one Roborazzi capture test (or a few) in the owning module's `androidHostTest`,
named after the row, rendering the state the row describes from canned `XUi` (or kit parameters):

```kotlin
@Test
fun `B-NAV-037 phones have no bottom navigation bar`() {
    composeRule.setContent { TestShell(window = Phone412, state = ConversationsUi.sample()) }
    composeRule.onAllNodes(hasTestTag("bottom-navigation")).assertCountEquals(0)          // [1] semantics first
    composeRule.onRoot().captureRoboImage("src/screenshots/rows/B-NAV-037-phone-light-en.png")   // [2] the look
}
```

- **Semantics first:** whatever the row says is *shown* or *absent* is asserted through semantics, so the test proves
  behaviour even before a golden exists.
- **The golden rule:** a capture becomes a golden only with an accepted visual verdict (§7.3). `ScreenshotVerdictTest`
  (`:quality`, T2) fails when a committed golden under `src/screenshots/rows/` has no verdict line with its SHA-256 in
  `docs/qa/native/visual-verdicts.jsonl`. A re-record needs a new verdict.
- **Variants:** light/en always; dark/ar when the row mentions direction, Arabic or RTL; text 2.0 when it mentions text
  size; 1280×800 when it mentions tablets (06 §15.3 matrix, cut down to what the row claims).
- Recorded and verified **on the VPS only**, the single golden host (ADR §8; memory `build-vps` on CPU raster
  differences).

### 3.6 e2e (29 rows) and the 240 device-flow rows

- The 29 `e2e`-level rows have **no** JVM test. Only their flows prove them: B-NAV-005, 024, 050, 054, 057, 059, 061,
  068, 069, 070, 073; B-CHAT-020; B-AGENT-031, 067, 098, 276; B-PROTO-046, 088; B-ANDR-011, 027, 028, 050, 109;
  B-LINUX-029, 069, 113, 119, 125, 151.
- The other 211 `portable_e2e` rows have a JVM test at their own level **and** a flow. Their row status comes from
  the JVM test (02 §6.3), and the flow adds the device proof that §6.3 reports separately.
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
| contract | `commonTest` of `:protocol:*` / `:core:net`; schema check in `desktopTest` | `FixtureEngine`, networknt | T1, T2 | VPS | passed in the last T2 |
| integration | `commonTest`; Robolectric in `androidHostTest` / `src/test` | fakes §3.3, Robolectric 4.17 (JDK 21) | T1, T2 | VPS | passed in the last T2 |
| migration | `:android:migration` `src/test` (Robolectric, real dump) + `androidTest` (Keystore) + journeys | Robolectric, AndroidJUnit4, Maestro | T2 + T3-upgrade | VPS + PC | JVM part in T2 **and** the instrumented part in the last T3-upgrade (02 §6.3) |
| screenshot | `androidHostTest` of the owning module | Roborazzi 1.77.0 | T1, T2 | VPS only | verify passed in the last T2 and the golden has a verdict |
| e2e | `native/e2e/flows/**` | Maestro 2.12.0 + runner | T3 | PC emulator | its flows passed on the release build (§6.3) |

---
## 4. Device flows: Flutter first, native second

The 39 flows of `../catalog/e2e-flows.md` are the device half of parity (ADR §10.1 item 2; 02 S-2). Each flow is
written and proven on the Flutter reference build R **before** the native slice that needs it starts. Then the same
file, byte for byte, judges the native release build.

### 4.1 Layout

```
native/e2e/
├── README.md                          how to run; points here
├── maestro.version                    2.12.0 + SHA-256 of the CLI zip from the GitHub release cli-2.12.0
├── flows/<area>/E2E-NN-<slug>.yaml    the flows (37 at the switch; E2E-28 and E2E-32 follow D6)
├── flows/<area>/E2E-NN-<slug>-qa.yaml companions for steps that need a QA hook (§4.4), run on stableQa
├── flows/_sub/*.yaml                  shared subflows: connect-fx-oc2, open-settings, open-chat, send-prompt, open-shade …
├── flows/_fx/*.js                     fixture control calls for [fx] steps (Maestro runScript, http.post to the fixture proxy)
├── seed/flutter-final.yaml, seed/surfaces.py     05 §10.4
├── upgrade/partA-seed.yaml, upgrade/partC-assert.yaml, upgrade/J*.yaml   E2E-39 and 05 §10.5
├── strings/strings.en.json, strings.ar.json, extra.en.json, extra.ar.json   §4.3 (generated, committed)
├── fixtures/                          FX-* servers, ports.json, fixture-data.json (§4.4)
├── avd/create.sh, avd/roles.json, avd/README.md    §4.6
├── refs/manifest.json                 screenshot references (§7)
├── known-gaps.md                      every KNOWN-FLUTTER-GAPS step: row, step, reason, decision
├── slices.json                        flow → slice, AVD, tier, Flutter proof (APK SHA-256, date, flow-file SHA-256)
└── build/                             git-ignored: strings.js for the run, outputs
native/catalog/flow-rows.json          row → flows, generated once from BEHAVIORS.md's e2e column by native/tool/e2e/rows.py
native/tool/e2e/   run.py  gen_strings.py  lint_flows.py  rows.py  scrub.py  pair.py
native/tool/device/                    the recipes of §8
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
   `native/catalog/flow-rows.json`. `FlowRowsTest` (`:quality`) checks that the union of all flows' rows is exactly
   the 240 `portable_e2e` rows (219 in switch scope [E] plus those of E2E-28/32).
2. `appId: ${APP_ID}`, never a literal package [U: Maestro interpolates env in `appId`; fallback: the runner writes a
   copy with the literal].
3. **Selectors:** `${output.s.<key>}` (exact text) or `${output.re.<key>}` (pattern) from the strings table, or
   `${output.fx.<name>}` from `fixtures/fixture-data.json`. Any other literal needs a `# literal-ok: <reason>` comment.
   `id:` selectors are banned in parity flows (02 §6.6; testing-perf §6.2).
4. Every step's first command carries `label: "S<n> <words> [<rows>]"`, so a failure names the step and its rows in
   the report [U: label support in 2.12.0].
5. No `clearState: true` in a flow that needs built-in Linux (the rootfs lives in app data). Such flows start from a
   snapshot (§4.6).
6. Waits are `extendedWaitUntil` with a timeout of 60 s or less (300 s for Linux setup steps), never a sleep.
7. `retry:` (Maestro's command; `maxRetries` 0–3 [V docs.maestro.dev]) is allowed only around system UI that is not
   ours (the notification shade, permission dialogs, the launcher), with `# retry-reason:`. Never around an assertion
   about our app (§10).
8. `[fx]` steps call `runScript: ../_fx/<action>.js`. `[adb]` steps are runner hooks
   (`# adb: <hook-name>`, implemented in `run.py`), because Maestro cannot run host shell commands.

### 4.3 Strings table and copy parity

- **Source:** the frozen ARB of R (`lib/l10n/app_en.arb`, `app_ar.arb`, read from the reference worktree). The
  ARB → XML conversion (03 §21.4) is a copy of the same text, so one table serves both apps.
- `native/tool/e2e/gen_strings.py --arb <R>/lib/l10n --out native/e2e/strings/` writes
  `{"key": {"text": "…", "re": "…"}}`. `{name}` placeholders become `.*` in `re`. ICU plural and select forms become an
  alternation of all forms, with `\d+` for the count. Arabic keeps all six plural forms.
- `extra.<lang>.json` holds labels the Flutter app hard-codes in Dart rather than the ARB (`docs/localization-todo.md`
  lists them [R]). The flows lane records each one from the first Flutter run with its Dart path. The l10n owner adds
  it to `:ui:words` with the same text.
- At run time `run.py` writes `native/e2e/build/strings.js` for the run language:
  `output.s = {…text}; output.re = {…re}; output.fx = {…fixture data}`.
- **Copy-parity gate** `FlowStringsFrozenTest` (`:quality`, T2):
  - For every key the flows use (`lint_flows.py` writes `native/e2e/strings/used-keys.txt`), the native en and ar XML
    value equals the frozen ARB value after placeholder conversion (`{name}` ↔ `%1$s`).
  - The key map comes from the one-off conversion. This needs `:tools:strings convert` to write
    `config/arb-key-map.tsv` (a request to the l10n owner).
  - A difference fails T2. Changing an asserted string needs the owner's OK and a `native-only` flow variant
    (02 §6.6, NG11).

### 4.4 Fixtures

All fixture servers run on the PC. They bind `127.0.0.1`; the emulator reaches them as `10.0.2.2`. They are started
by `run.py` (§4.5) and stopped by **exact PID** (AGENTS.md, memory `pgrep-self-match-wait-trap`). Per-run passwords
are generated by the runner, passed to Maestro with `-e`, never printed, and scrubbed from every output (§4.5 step 8).
`native/e2e/fixtures/ports.json` is the one list of ports.

| Id | Implementation | Port(s) [E, final in ports.json] | Flows | Unit (§12) | Needed by |
|---|---|---|---|---|---|
| FX-OC2 | real `opencode2 serve` at the pinned **2.1.1**, HTTP Basic `opencode` + per-run password; projects `app` (Git, `README.md`, `notes.txt`, `data.csv`, `lib/main.dart`, a binary) and `other`; model = `stub_model.py` | 4097; model 4299 (4199, `mock_llm.py`'s default, is already taken on the PC [V `ss -ltn`]) | most | W14-7 | slice 0 (group G2) |
| `stub_model.py` | extends `tool/qa/issue87/mock_llm.py` [R] (OpenAI-compatible, deterministic): adds the `e2e:*` prompts of e2e-flows (echo, long, cut, slow-finish, ask-edit, ask-shell, question/choice/choice4, secret, files-edit; tool calls for permissions) and 09's `perf:stream-6000`, `perf:history-5000`, `perf:tools-200` (13 §3.8) | 4299 | all chat flows; T4 | W14-7 | slice 0 |
| FX-OC2-B | second FX-OC2 with its own data dir, shared session ids, a placeholder ISO title and a leaked tool-call title | 4098 | E2E-21, 22, 35, 36 | W14-7 | slice 0 |
| fx-proxy | `fx_proxy.py`: the emulator's HTTP proxy (`emulator -http-proxy http://127.0.0.1:8899`; the emulator process runs on the PC [U: all guest TCP goes through it]); control API on `127.0.0.1:8898` to cut, throttle or refuse by host, and to drop a stream mid-reply. Works for both apps without TLS interception. | 8899, ctl 8898 | E2E-05 s9, E2E-10, 13, 17 | W14-7 | slice 0 |
| Replay server | `:tools:wire-recorder replay --root native/fixtures/wire` (§3.2) for failures a real server cannot produce on demand | 4300 | E2E-03, 06; 13 PR-02/04 | W14-4 | slice 0 |
| FX-TLS | Python `ssl` reverse proxies in front of FX-OC2: trusted (the PC's `<host>.ts.net` name with a `tailscale cert` certificate, emulator started with `-dns-server 100.100.100.100` [U]), self-signed A and B (swappable), expired, a redirector, plain HTTP on a private address, plain HTTP on a public-looking name | 8443–8449 | E2E-03, 04 | W14-8 | slice 0 |
| FX-PASEO | Paseo daemon **0.11.2** with scripted agents (`fake-claude`, `fake-pi`: shell scripts that speak each agent's stream format), Claude history files on disk, terminals | ports.json | E2E-30 | W14-8 | slice 1 |
| FX-AUTH | sign-in stub for phone agents; prints a fixed code | ports.json | E2E-31 | W14-8 | slice 1 |
| FX-LINUXDL | local mirror of the pinned Ubuntu rootfs, OpenCode and tools, with the same SHA-256 pins; throttle and cut via fx-proxy | 8700 | E2E-17, 19 | W14-8 | slice 1 (E2E-39 Part A uses the real pinned download hosts until it exists) |
| FX-GH | GitHub device-flow and `releases/latest` stub | 8701 | E2E-15 s4; E2E-39 s13 | W14-8 | slice 3 |
| FX-GIT | bare repo over `git daemon` / smart HTTP | 9418 / 8702 | E2E-15 | W14-8 | slice 3 |
| FX-CODEX | `tool/qa/codex_fixture/fixture_server.py` [R], copied as a twin, plus a scripted turn with an approval, steer, abort and socket drop | ports.json | E2E-29 | W14-8 | slice 3 |
| FX-USAGE | Codex and MiniMax usage stubs: reading, expired token, offline | 8703 | E2E-33 | W14-8 | slice 3 |
| FX-NODE | memory-node stub | — | E2E-32 | after the switch (D6) | — |

**Hosts the apps hard-code.** The Flutter app hard-codes `https://github.com/login/device/code` and
`https://github.com/login/oauth/access_token` [V `lib/domain/git/github_device_flow.dart`]. The same holds for the
update feed and the download hosts. A stub cannot stand in for them without intercepting TLS, which this plan does not
do. So:

- On **native**, the `stableQa` build reads base-URL overrides from `QaHooks` (below). The steps that need them live
  in `-qa.yaml` companions, run on `stableQa` of the same commit.
- On **Flutter R** they are `KNOWN-FLUTTER-GAPS` (no override exists). Their rows still have their Flutter unit
  tests as the reference (B-SEC-084 for the GitHub sign-in, B-MIGR-012 for the update notice, the LINUX checksum
  rows).
- A flow counts as green when its main file passes on `stableRelease` **and** its companion passes on `stableQa`.

**`QaHooks`** (the test-facing contract of 11 §4.3's `qa` build type and `AppBuildInfo.qaHooks`; a request to the
coordinator, who owns `:androidApp`):

```kotlin
// :core:domain (read by engines through AppBuildInfo); the implementation that reads the file exists only in src/qa/
public data class QaHooks(
    val stepInterrupter: Boolean = false,      // 05 J2: stop after the step named in files/native/qa-kill-after
    val builtinRuntime: Boolean = false,       // 11: BuiltinRuntime* acceptance steps
    val downloadBaseUrl: String? = null,       // FX-LINUXDL: rootfs, OpenCode, tools, voice packs; SHA-256 pins still checked
    val githubBaseUrl: String? = null,         // FX-GH device flow
    val updateFeedUrl: String? = null,         // FX-GH releases/latest for AppUpdater (B-MIGR-012)
    val hapticLog: Boolean = false,            // 08 H-1..3
    val strictMode: Boolean = false,           // 02 S-21
    val testTagsAsResourceId: Boolean = false, // UIAutomator handles in androidTest
) { public companion object { public val None: QaHooks = QaHooks() } }
```

- **The file:** the runner writes `files/native/qa-hooks.json` with `adb root` (the `qa` build is not debuggable, so
  `run-as` is out).
- **Only `stableQa` reads it.** Every other build binds `QaHooks.None`, and a Konsist rule in the spirit of 13's K-P10
  keeps `src/qa/` out of `src/main/`.
- **The variant:** `stableQa` = the `stable` flavor × the `qa` build type: package
  `io.github.eslamasabry.opencode_mobile`, R8 on, re-signed on the PC with the **local key** like a release build. It
  has to install over Flutter R and over `stableRelease` (05 J1–J2). 13 §3.2 lists `qa` as `….native` with debug
  signing; §13.2 flags that difference.

### 4.5 The runner (`native/tool/e2e/run.py`)

One script drives both apps (e2e-flows "runner contract"). It is Python 3 standard library only, run with `-I`.

```
run.py --app flutter|native --apk PATH [--variant stableRelease|stableQa|previewRelease]
       --avd A|S|T|36|26  (--tier smoke|slice|full|switch|upgrade | --flows E2E-05,E2E-06 | --tags slice-2)
       [--lang en|ar] [--repeat N] [--no-retry] [--out build/native-t3/<sha>/maestro]
```

1. **Refuse to start** unless all of these hold:
   - the caller holds the emulator lock (`/home/eslam/Storage/tmp/oc-emulator.lock`; `t3.sh` takes it, R1);
   - `free -m` shows ≥ 7 GB available (02 §7.8);
   - the AVD's filesystem has ≥ 10 GB free.
2. **Prepare the AVD** for the role (R2): boot it or reuse it, animations off, demo-mode status bar, the locale, font
   scale 1.0, density reset, gesture navigation.
3. **Generate** `build/strings.js` for `--lang`.
4. **Start the fixtures** named in the selected flows' headers (R4). Write their PIDs to `<out>/pids`. Generate the
   per-run passwords into the environment, masked.
5. **Install** with R3 (signer and versionCode checks; `adb install -r`, never `-d`), unless the same APK SHA-256 is
   already installed.
6. **For each flow:**
   1. Load its snapshot. If the snapshot holds an older build of the same app, run `adb install -r` again; this keeps
      the data.
   2. Push `qa-hooks.json` for `-qa` companions.
   3. Run `maestro --device <serial> test --format junit --output <out>/<flow>/report.xml
      --test-output-dir <out>/<flow> --debug-output <out>/<flow>/debug -e APP=<app> -e APP_ID=<pkg>
      -e SERVER_PASSWORD=… <file>` (flags [V docs.maestro.dev CLI reference]).
   4. Save `adb logcat -d` for the app's uid.
   5. On failure, retry **once** from the same snapshot, unless `--no-retry`.
   6. Classify the flow as `PASSED`, `FAILED` or `FLAKY` (fail then pass).
7. **Clean up:** stop the fixtures by PID, restore the emulator settings, and delete the fill file if one was made.
8. **Write and scrub the outputs:**
   - `flows.json`, `report.xml` (merged JUnit), `durations.json` and `summary.txt` (02 §8 contract).
   - `scrub.py` replaces every per-run password with `<redacted>` in all outputs.
   - It **fails the run** if a `CanarySecrets` value or a fixture provider key appears in app logcat (02 S-22).
   - Exit codes: `0` pass, `1` a flow failed, `2` could not run.

```json
{"flow":"E2E-05","file":"flows/chat/E2E-05-stream-stop-search-links.yaml","flowSha256":"…","app":"native",
 "variant":"stableRelease","commit":"<sha>","apkSha256":"…","avd":"OCN_A","lang":"en",
 "attempts":[{"status":"FAILED","step":"S3","secs":212},{"status":"PASSED","secs":205}],
 "result":"FLAKY","rows":["B-CHAT-017","B-CHAT-060","…"]}
```

### 4.6 Emulators, snapshots and disk

Checked on this PC today [V]: the installed system images are `android-34`, `android-35` and `android-37.0` (all
`google_apis` x86_64); `OC_API37` exists (API 37, 4 GB RAM, 16 GB data). e2e-flows names API 36 for AVD-A, but no API 36
image is installed. T14-2 sets the roles below.

| Role (e2e-flows) | AVD | Image | RAM / data | Used for |
|---|---|---|---|---|
| A | `OCN_A` | android-37.0 google_apis x86_64 | 4 GB / 16 GB | every flow unless noted; the upgrade journeys (05) |
| S | `OCN_S` | same | 1.5 GB / 16 GB | E2E-17 small-RAM steps |
| F | `OCN_A` + a fill file | — | `run.py` runs `fallocate -l <n> /data/local/tmp/oc-fill.bin` until free space is under the setup requirement, and deletes it in `finally` | E2E-17 full-disk steps |
| T | `OCN_T` | same, `pixel_tablet` device, `hw.keyboard=yes` | 4 GB / 16 GB | E2E-22, E2E-24 |
| AR | `OCN_A` booted Arabic | `emulator -change-locale ar-EG` [U]; fallback as root: `setprop persist.sys.locale ar-EG; setprop ctl.restart zygote` | — | Arabic repeats of E2E-22, E2E-23 |
| 36 | `OCN_36` | android-36 google_apis x86_64 (to install, about 1.5 GB [E]) | 4 GB / 16 GB | the 6 phone-smoke flows at slice ends (the owner's NX721J runs Android 16) |
| 26 | `OCN_26` | android-26 google_apis x86_64 (to install) | 2 GB / 8 GB | E2E-01 and E2E-05 at the ends of slices 2, 4 and 6 (minSdk 26: glow overscroll, no `RenderEffect`, older Keystore) |
| — | `OC_API37` (exists) | — | — | section 13 only (`perf-base` snapshot); never wiped by flows |

- `native/e2e/avd/create.sh <role>` creates each AVD with `avdmanager` under `/home/eslam/Storage2/avd/` and writes
  `roles.json`. Only `run.py` may uninstall or `clearState` the main package, and only on `OCN_*` AVDs. This extends
  02 §7.9, which allows it on the upgrade-journey AVD.
- **GPU:** host GPU while the owner's desktop session is logged in; `-gpu swiftshader_indirect` headless otherwise.
  The second needs `emulator/lib64/gles_swiftshader` renamed back (memory `launch-video-2026-10-19`). The mode is
  recorded in `summary.txt`. Flows do not depend on it; benchmarks do (13 §3.7).
- **Snapshots** (at most 4 per AVD):

  | Snapshot | Holds | Remade when |
  |---|---|---|
  | `base` | booted, settings applied, no app | the image changes |
  | `flutter-linux` | R with built-in Linux set up through FX-LINUXDL and project `app`, connected to FX-OC2 | R changes |
  | `native-linux` | the same for native | the native setup engine or the rootfs pins change; otherwise a newer native APK goes over it with `install -r` |
  | `flutter-final-seeded` | 05 §10.4, on `OCN_A` | R changes |

- **Disk:** today `/` has 20 GB free, `/home/eslam/Storage` 4.2 GB and `/home/eslam/Storage2` 7.3 GB [V `df`]. The
  `OCN_*` set with snapshots needs about 35–45 GB [E].
  - Six stale AVDs hold 39.6 GB [V `du`]: `FB_F6` 5.9, `Pixel_6` 6.0, `OCMN_Stable_1042_QA` 6.4,
    `OCMN_Stable_1042_Public_QA` 4.4, `OCMN_UI_Refinement` 12 and `OCMN_Live_Demo_20260913` 4.9.
  - T14-3 deletes those six and keeps `OC_API37`, `OC_API35` and `OC_Launch_Demo` (launch videos).
  - Until the owner answers, only `OCN_A` is created, and the S, T, 36 and 26 runs wait.

### 4.7 Proving a flow on Flutter

1. **Build R once.**
   - Make a detached worktree `/home/eslam/Storage/Code/wt/oc_app-ref-build` at `65e3f70a3`. `oc_app-batch1` stays
     read-only.
   - Run `tool/qa/vps_build_apk.sh <N>` there. N is a Flutter build number reserved in `native/BUILD_NUMBERS.md`,
     below 10000 (for example 4600 [E]). The script builds on the VPS and re-signs on the PC with the local key
     (memory `build-vps`).
   - Before installing, check that `dexdump <apk> | grep -c GeneratedPluginRegistrant` is greater than 0 (memory
     `shared-emulator-contention`).
   - Keep `R-<N>.apk` and its SHA-256 in `/home/eslam/oc-artifacts/flutter-ref/` on the PC and in
     `~/oc-artifacts/flutter-ref/` on the VPS.
   - When `flutter-final` is tagged, rebuild R from it the same way and re-prove every flow (02 §6.2).
2. The writer turns the e2e-flows text into YAML. The step text stays as comments.
3. The T3 holder runs `run.py --app flutter --apk R-<N>.apk --avd A --flows E2E-NN`.
4. **On failure:**
   - **Wrong label or timing:** fix the flow. A label the ARB lacks goes into `extra.en.json`.
   - **A real Flutter bug:** add a `KNOWN-FLUTTER-GAPS` line in the header and a row in `known-gaps.md` (row, step,
     reason, decision). Wrap the step in `runFlow: {when: {true: "${APP == 'native'}"}, file: …}`.
   - A gap needs the coordinator's OK. A flow with more than two gap steps is redesigned.
5. **Proven on Flutter** means green twice in a row on Flutter. `slices.json` then records the APK SHA-256, the date,
   and the flow file's SHA-256.
6. **Record:** after each group, the T3 holder writes `docs/qa/native/parity-baseline-flutter-<date>/README.md` in the
   02 §9.4 format: flows, APK hash, AVD, durations, the screenshot reference manifest (§7.1) and the known gaps.

### 4.8 Writing order, slices and tiers

Default slice assignment (section 15 may move a flow, and `slices.json` follows). A group is proven on Flutter
before its slice starts: G2 by the end of slice 0, G3 by the end of slice 1, and so on.

| Group / slice | Flows | AVD | Fixtures | Est. minutes per run [E] |
|---|---|---|---|---|
| G2 / 2 (chat daily path) | E2E-01 first launch; 03 failures in plain words; 04 transport trust; 05 stream/stop/search/links; 10 network loss, never twice; 21 conversations list; 35 remove a server | A | FX-OC2, FX-OC2-B, fx-proxy, FX-TLS, replay | 4, 8, 8, 10, 8, 8, 5 |
| G3 / 3 (phone runtime and agents) | E2E-02 pairing and QR; 06 approve/deny/always; 11 drafts, saved, queued; 17 Linux setup; 18 phone server; 19 remove Linux; 30 Claude Code and Pi via Paseo; 31 agents on this phone | A, S, F | + FX-LINUXDL, FX-PASEO, FX-AUTH | 6, 8, 8, 40, 20, 15, 12, 10 |
| G4 / 4 (surfaces, settings, schedules) | E2E-07 answer from the shade; 08 notifications; 09 keep running; 22 find a setting; 23 language, text size, look; 25 shortcuts, tile, links, share; 26 widgets; 27 schedules; 34 privacy; 36 several servers; 38 tips and tour | A, T, AR | no new fixture | 8, 10, 6, 10, 10, 10, 10, 8, 8, 8, 5 |
| G5 / 5 (project, media, integrations) | E2E-12 attachments; 13 voice; 14 files and sharing; 15 Git; 16 terminal; 20 dev servers and ports; 24 command launcher; 29 Codex; 33 usage; 37 providers and models | A, T | + FX-GH, FX-GIT, FX-CODEX, FX-USAGE | 8, 10, 10, 12, 6, 12, 5, 8, 5, 8 |
| G6 / 6 (switch) | E2E-39 upgrade in place (Parts A and C proven on Flutter in slice 0; steps 3–13 native) | A | FX-OC2, FX-LINUXDL, FX-GH | 45 |
| after the switch (D6) | E2E-28 AI Team, E2E-32 Memory | A | FX-NODE | — |

Totals [E]: about 6 h 30 min for one pass of the 37 flows (G2 51 min, G3 119, G4 93, G5 84, G6 45), plus up to 40 min
when the Linux snapshots are stale, plus about 1 h for the Arabic and tablet repeats. `durations.json` from the Flutter
baseline replaces these numbers.

**Tiers** (the "T3" of 02 §8 is the **smoke** tier; the others are longer T3 runs):

| Tier | When | Flows | Budget |
|---|---|---|---|
| `smoke` | every APK that reaches the owner; every preview APK | the six phone-smoke flows (E2E-01, 05, 06, 08, 25, 26) that exist natively at that point, plus the flows of the units merged since the last owner APK | target ≤ 40 min (02 §8); hard limit 60 min. The six alone are about 52 min [E]; if measured over 40, the coordinator moves the slowest two to `slice` and records it in `slices.json` |
| `slice` | slice end | every flow of slices ≤ N that touch the slice's modules (`slices.json` maps flows to modules), plus smoke | ≤ 2 h |
| `full` | weekly from slice 2 on (the T3 holder picks a day the emulator is free); every switch candidate | all native-runnable flows; Arabic and tablet repeats; `OCN_36` smoke | ≤ 8 h (one working day or one night) |
| `upgrade` | every candidate that touches `:android:migration`, `:core:data` or a stored format; every owner APK until the switch (ADR §10.2) | J1, J2, J3 (05 §10.5) + E2E-39 steps 3–13 once they exist | ≤ 60 min |
| `switch` | the switch candidate only | `full` twice with `--no-retry`; the six smoke flows three times; J1–J8; monkey 3 seeds × 50,000 events (02 S-21) | ≤ 24 h of emulator time over three days [E] |

### 4.9 Judging the native app with the same files

- A flow counts for native parity only when its file's SHA-256 equals the `flowSha256` of its last Flutter proof in
  `slices.json`. Any edit to a flow file means proving it on Flutter again first. `run.py` refuses `--app native`
  parity credit otherwise and marks the result `UNPROVEN`.
- `native-only` variants (`E2E-NN-<slug>.native-only.yaml`) exist only with an owner decision (02 §6.6). They are
  listed in `slices.json` with the decision. They count for flow parity, but they are never compared against Flutter.
- A step that is `flutter-known-bad` must pass on native (testing-perf §6.5).
- A flow that fails on native and passes on Flutter is a native bug. The runner's step labels name the rows the failed
  step proves, and the coordinator routes the bug to the unit that owns them.

### 4.10 E2E-39 and the upgrade journeys

- Part A (seed) and Part C (assert) are written once, as `upgrade/partA-seed.yaml` and `upgrade/partC-assert.yaml`.
  They are proven on Flutter in slice 0: E2E-39 Part B runs Part C on Flutter.
- The slice-0 short form for ADR P5 runs steps 3, 4, 6 and 8 against the P5 spike build. The full flow runs from
  slice 6.
- J1–J8 (05 §10.5) are run by `native/tool/device/upgrade_journey.sh J<n>` (R7). The canary secrets of `CanarySecrets`
  are part of the seed, so B-MIGR-011 and S-22 are checked on real migrated data.

---

## 5. The deferred widget-test catalog (7,967 tests)

The owner: "7400 for widgets can come later" (02 NG12; ADR §4 row 18). This section plans the phase so that waiting
costs nothing, and so the owner can schedule it with numbers in hand.

### 5.1 What happens now (zero-cost preparation only)

1. Test tags equal the Flutter `ValueKey` strings where one existed (ADR §9.1). Screens take `(XUi, XActions)` and
   ship previews for loading, content, failure and empty (03 §20.7). Both let a widget behaviour be ported later
   without touching the screen.
2. **Widget-test name index** (T14-4, half a day, a script, no agent reads tests):
   - `native/tools/flutter-fixtures/widget_index.py` reads the frozen Flutter `test/` tree and writes
     `native/catalog/widget-index.tsv`.
   - Columns: `file, line, group path, test name, folder (kit|revamp|goldens|root|other), screen guess (from the file
     name and 07 §5's inventory), cited_by_logic_catalog (yes/no)`.
   - Builders get the lines for their screen in their brief's read set, as a checklist of what the Flutter screen was
     tested for. Reading them is optional and porting them is not asked.
3. Builders note "Widget-test candidates" in their QA record (§3.7) when they see a behaviour only a widget test
   protects.

### 5.2 When it starts (T14-4)

- **Default:** the first week after the switch release ships, before the Room move of switch+1.
- **Measured trigger to start earlier:** the coordinator counts UI regressions found by the slice-end emulator sweeps
  (02 §6.7) and by the owner, where a ported widget test would have caught the bug. If one slice finds **5 or more**,
  or the owner finds **2 or more** in one preview APK, the coordinator asks the owner to start the phase at the next
  slice. The count is kept in `native/LEDGER.md` under "Widget-test trigger".

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
   - Android-only seams go to Robolectric `createComposeRule` (v2).
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
- The slice-end emulator sweep (02 §6.7) and the independent visual auditor (02 §7.10) cover the rest by eye.
- The trigger in §5.2 turns any growing risk into a number the owner sees.

---

## 6. The parity dashboard

### 6.1 Inputs and computation

`:quality:catalogReport` is a `JavaExec` task in `:quality`; `CatalogRatchetTest` (03 §19.6) uses the same code. It
runs last in `t2.sh`, and again at the end of every T3 run.

| Input | Path |
|---|---|
| Catalog | `native/catalog/behaviors.jsonl` (copy, 02 §3.6) + `scope.jsonl` |
| JVM and Robolectric results | every `**/build/test-results/**/TEST-*.xml` of the run (restored from the build cache for untouched modules, §9.2) |
| Instrumented results | `androidApp/build/outputs/androidTest-results/connected/**/TEST-*.xml` and the `:android:*` equivalents |
| Flow results | `build/native-t3/<sha>/maestro/flows.json` |
| Flow → rows | `native/catalog/flow-rows.json` |
| Previous state | `docs/native/parity-rows.json` (written at the last green T2) |
| Flakes | `native/FLAKES.md` (§10.5) |
| Visual verdicts | `docs/qa/native/visual-verdicts.jsonl` |

Per row:

1. Collect every test whose name matches the ratchet regex (02 §6.1) for this id.
2. Apply 02 §6.3 and §6.4: `todo` (no test), `red` (any failed, skipped or ignored test; skipped counts as red),
   `green` (all passed in this run), `deferred` or `dropped` (from scope).
3. **Device evidence**, kept apart (§6.3): for `e2e`-level rows and the instrumented half of `migration` rows, the
   status comes from the newest T3 result whose commit is an ancestor of this one.
4. **Screenshot rows** need a verdict too (§3.5).

### 6.2 Kotlin

```kotlin
// native/quality/src/main/kotlin/io/github/eslamasabry/oc/quality/catalog/
@JvmInline value class RowId(val value: String) { init { require(ROW_REGEX.matches(value)) { value } } }
enum class Level { UNIT, CONTRACT, INTEGRATION, SCREENSHOT, MIGRATION, E2E, UI }
enum class RowStatus { TODO, RED, GREEN, DEFERRED, DROPPED }
enum class ResultSource { JVM, ROBOLECTRIC, ROBORAZZI, INSTRUMENTED, MAESTRO }
enum class TestOutcome { PASSED, FAILED, SKIPPED, FLAKY }

data class TestResult(val id: String, val source: ResultSource, val outcome: TestOutcome, val commit: String, val ms: Long)
data class RowReport(
    val id: RowId, val area: String, val level: Level, val slice: Int?, val status: RowStatus,
    val tests: List<TestResult>, val device: DeviceEvidence?, val since: String?,   // since = first green commit
)
data class DeviceEvidence(val flows: List<String>, val status: RowStatus, val commit: String)

object RowIds { fun idsIn(testName: String): Set<RowId> }          // the 02 §6.1 regex, both - and _ forms
class CatalogReport(private val catalog: Catalog, private val scope: Scope) {
    fun compute(results: List<TestResult>, flows: List<FlowResult>, previous: ParityRows?): ParityReport
}
fun main(args: Array<String>)   // --t2 <dir> | --t3 <dir> ; writes parity.json, parity.md, rows.json, history line
```

### 6.3 Two ratchets

| Ratchet | Counts | Stored in | May drop? | Checked |
|---|---|---|---|---|
| **JVM ratchet** | rows green from T2 evidence | `native/config/catalog-coverage.txt` (03 §19.6): the sorted green id set at `LAST_GREEN` | never between two T2 runs on `native/main` (02 §6.5); a drop fails T2 | every T2 |
| **Device ratchet** | e2e rows, instrumented migration rows, and flows green on the release build | `native/config/device-coverage.txt`: the green set at the last `full` or `slice` T3 | never between two `full` runs; a drop blocks the next owner APK until fixed | every `slice` / `full` T3 |

02 §6.5's single `green` number is the JVM ratchet plus the device evidence carried from the newest ancestor T3. It
falls only through a JVM regression or a newer device failure, so 02's "may never go down" holds. The **switch** needs
all evidence on the candidate commit itself (§11).

### 6.4 Outputs

1. `build/native-t2/<sha>/parity.json`, using 02 §6.5's keys plus these:

```json
{"commit":"<sha>","date":"2026-11-20",
 "rows":{"switch":1294,"green":512,"red":0,"todo":782,"deferred":250,"dropped":0},
 "areas":{"STATE":{"switch":151,"green":90,"red":0,"todo":61}},
 "levels":{"unit":{"switch":590,"green":301},"contract":{"switch":190,"green":64}},
 "slices":{"1":{"switch":260,"green":251},"2":{"switch":310,"green":180}},
 "device":{"commit":"<sha of the newest ancestor T3>","rows_green":35,"rows_switch":219},
 "flows":{"switch":37,"green":5,"proven_on_flutter":15,"flaky_7d":1},
 "features":{"total":700,"dispositioned":700},
 "flakes":{"open":2},"widget":{"cataloged":0,"green":0}}
```

2. `docs/native/parity-rows.json`: one entry per row (id, status, tests, since, device). It is committed with every
   green T2 and is the "previous" input of the next run.
3. `docs/native/parity.md`, the human page, generated and never hand-edited. It holds:
   - the headline;
   - one table per area, level and slice;
   - the flows table (Flutter proof, native result, last run, flakiness);
   - red rows with their failing test;
   - todo rows of the current and next slice;
   - open flakes;
   - the widget-phase line.
4. `docs/native/parity-history.csv`: one line appended per green T2 (`sha,date,switch,green,flows_green`).
5. **The owner line** goes into each weekly report and each APK handover (`short-replies-rule`), for example:
   `Parity 41 % (536 of 1,301 behaviours). Phone flows 7 of 37. Upgrade journey green. Next: slice 3.`

### 6.5 Gates built on the dashboard (all in `:quality`)

| Gate | Tier | Fails when |
|---|---|---|
| `ScopeFileTest` (02 §3.6) | T2 | scope rows incomplete or malformed |
| `CatalogRatchetTest` (03 §19.6) | T2 | the JVM green set lost an id |
| `RowIdsExistTest` | T1, T2 | a test names an unknown or dropped row |
| `RowHeaderTest` | T1, T2 | §2.5 header missing or a clause has no `// [n]` |
| `NoStubsLeftTest` | T2 | `TODO("B-` left in a closed slice's module |
| `VectorsProvenanceTest` | T1, T2 | a vector file lacks provenance or names another R |
| `FlowRowsTest` | T2 | flows' rows ≠ the 240 device-flow rows |
| `FlowStringsFrozenTest` | T2 | an asserted string differs from the frozen ARB |
| `ScreenshotVerdictTest` | T2 | a row golden has no verdict for its SHA-256 |
| `SliceDoneTest` (`--slice N`, run by the coordinator) | slice end | any row with `slice ≤ N` is not green, or a flow of slices ≤ N is not green on the release build (§11) |

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
{"image":"feature/chats/src/screenshots/rows/B-NAV-037-phone-light-en.png","sha256":"…","ref":"refs/flutter-4600/E2E-01-S7.png",
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
- restore font scale, density and locale after the run;
- never print a password;
- never screen-capture the owner's phone.

**R1, hold the emulator lock** (`emu_lock.sh acquire|release`; memory `emulator-lock-holder`)

```bash
LOCK=/home/eslam/Storage/tmp/oc-emulator.lock
setsid flock -w 1800 "$LOCK" sleep 14400 >/dev/null 2>&1 &            # waits up to 30 min, holds up to 4 h
FLOCK_PID=$!
until SLEEP_PID=$(ps -o pid= --ppid "$FLOCK_PID" | tr -d ' '); [ -n "$SLEEP_PID" ]; do sleep 1; done
echo "$FLOCK_PID $SLEEP_PID $$" > /home/eslam/Storage/tmp/oc-emulator.holder
# release:
kill "$SLEEP_PID"; flock -n "$LOCK" true && echo released             # killing the sleep child ends flock too
```

**R2, boot and prepare a flows AVD** (`emu_up.sh <role>`; ports fixed per role: A 5560, S 5562, T 5564, 36 5566,
26 5568)

```bash
SERIAL=emulator-5560
emulator -avd OCN_A -port 5560 -no-boot-anim -no-audio -http-proxy http://127.0.0.1:8899 -snapshot base &
EMU_PID=$!                                                             # the emulator runs on the PC, so the proxy is 127.0.0.1
adb -s $SERIAL wait-for-device
until [ "$(adb -s $SERIAL shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb -s $SERIAL root; adb -s $SERIAL wait-for-device
A="adb -s $SERIAL shell"
for k in animator_duration_scale transition_animation_scale window_animation_scale; do $A settings put global $k 0; done
$A settings put system font_scale 1.0; $A wm density reset; $A wm size reset
$A cmd overlay enable com.android.internal.systemui.navbar.gestural    # predictive back needs gesture navigation
$A settings put global sysui_demo_allowed 1                            # fixed status bar for screenshot pairs (§7.2)
$A am broadcast -a com.android.systemui.demo -e command enter
$A am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
$A am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
$A am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4
```

Shut down with `adb -s $SERIAL emu kill`, then confirm with `ps -p $EMU_PID`.

**R3, install with checks** (`install_checked.sh <apk> <package>`)

```bash
WANT=1de5bf08146f269bcd9eb5c2ffc94469ce4617d37806285955f978a62494d60c        # local key (AGENTS.md); public lineage only via CI (D2)
apksigner verify --print-certs "$APK" | grep -qi "SHA-256 digest: $WANT" || { echo "wrong signer"; exit 1; }
NEW=$(aapt2 dump badging "$APK" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
OLD=$($A dumpsys package "$PKG" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
[ -z "$OLD" ] || [ "$NEW" -ge "$OLD" ] || { echo "would be a downgrade: $OLD -> $NEW"; exit 1; }
adb -s $SERIAL install -r "$APK"
```

For VPS-built Flutter APKs, also check `dexdump "$APK" | grep -c GeneratedPluginRegistrant` > 0 (memory
`shared-emulator-contention`).

**R4, fixtures up and down** (`fixtures.sh up <ids> | down`)

- `up` starts each fixture in its own process group. It writes `name pid port` lines to
  `build/native-t3/<run>/pids` and waits until each port answers (`ss -ltn`).
- FX-OC2 runs the server **binary**, not the npm wrapper: the wrapper's child keeps listening after the wrapper's PID
  is killed (memory `opencode2-stable-2.0-port`). After start, `ss -ltnp` must show the recorded PID on port 4097.
- Each FX-OC2 gets its own `XDG_DATA_HOME` and `XDG_CONFIG_HOME` under the run directory, so fixture sessions never
  mix with any OpenCode the owner runs on the PC.
- `down` sends `kill <pid>` for each line, then requires `ps -p <pid>` to be empty within 10 s; otherwise `kill -9`
  on that exact PID.

**R5, run flows**: `run.py` (§4.5) inside `t3.sh --tier <tier>`.

**R6, video proof for the owner** (`record.sh <name>`; memory `live-video-proof`)

```bash
adb -s $SERIAL shell screenrecord --time-limit 180 --bit-rate 8000000 /sdcard/oc-$NAME-1.mp4 & REC=$!
# … drive the flow (run.py --flows E2E-NN with animations ON for the recording) …
wait $REC; adb -s $SERIAL pull /sdcard/oc-$NAME-1.mp4 build/videos/; adb -s $SERIAL shell rm /sdcard/oc-$NAME-1.mp4
```

Segments of 3 minutes or less are edited with Remotion on the VPS (step captions, a visible ×N for speed-ups, real
footage only), checked frame by frame, and hosted on the APK share server, never in git.

**R7, upgrade journeys J1–J8** (`upgrade_journey.sh J<n>`): exactly 05 §10.5, on `OCN_A` from the
`flutter-final-seeded` snapshot. File checks go through `adb root`:

```bash
$A "cd /data/data/$PKG && sha256sum files/linux/ubuntu.ready && find files/linux | wc -l"
$A "cd /data/data/$PKG && find files/projects files/voice_models -type f -exec sha256sum {} +" > after.sha
diff <(sort before.sha) <(sort after.sha)                     # 02 S-4: byte-identical
```

**R8, restore after reboot or update** (memory `restore-proof-recipe`): `am force-stop` → edit prefs as root if the
case needs it → **`pm unstop $PKG`** (otherwise `MY_PACKAGE_REPLACED` is never delivered and the proof proves nothing)
→ `adb install -r` or `adb reboot` → poll `ps -A -o PID,PPID,USER,NAME` for the app uid's server process. Delivery
shows in `dumpsys activity broadcasts history`.

**R9, notifications**: Maestro opens the shade (`[adb] cmd statusbar expand-notifications`) and asserts the visible
words. For fields and channels: `$A dumpsys notification --noredact`, filtered to the package, checked against 11
§10's channel ids and against `CanarySecrets` (R11).

**R10, widgets and tiles**: UIAutomator 2.4.0 tests in `:androidApp` `androidTest` (`uiAutomator { … }` DSL), named
`B_ANDR_…`, run by `t3.sh` through `connectedStableReleaseAndroidTest` with a class filter. Placing a widget goes
through the launcher's long-press → Widgets path; adding a tile uses `cmd statusbar add-tile <component>` [U on API
37]. ZTE launcher quirks remain an owner-phone check (11 §11.4).

**R11, canary scan** (`canary_scan.sh`; 02 S-22; B-MIGR-011)

```bash
CANARIES=native/core/testing/src/commonMain/resources/canaries.txt     # the fixed fake values of CanarySecrets
PIDS=$($A pidof $PKG | tr ' ' '|')
$A logcat -d | grep -E "\( *($PIDS)\)" > logcat.txt || true
$A dumpsys notification --noredact > notif.txt
# + the problem report saved through the app's share sheet by the flow (E2E-34), + the widget snapshot file (as root)
if grep -F -f "$CANARIES" logcat.txt notif.txt report.txt widget-snapshot.json; then echo "CANARY LEAK"; exit 1; fi
```

The egress half of S-22 (destinations within the allowlist) is section 16's. The test reads
`native/config/egress-allowlist.txt` from fx-proxy's connection log, which records every host the app contacted
during a run.

**R12, process tree** (`proc_tree.sh`): `U=$($A stat -c %U /data/data/$PKG)`, then
`$A ps -A -o PID,PPID,USER,RSS,NAME | awk -v u=$U '$3==u'`. After "Stop the server on this phone", no process of that
uid may have PPID 1 (memory `proot-orphans-heat`). Used by E2E-18/20 `[adb]` hooks and slice-3 acceptance.

**R13, monkey** (`monkey.sh`; 02 S-21): load the `base` snapshot with seeded data (never `native-linux`, and never
save the snapshot afterwards). For each seed in 11, 23 and 37:
`$A monkey -p $PKG -s <seed> --throttle 50 --pct-syskeys 0 -v 50000`. Then
`$A dumpsys activity exit-info $PKG` and logcat must show no `FATAL EXCEPTION` and no `ANR in`.

**R14, the owner's phone** (02 §6.7; memory `no-owner-phone-screenshots`, `owner-phone-debug-allowed`,
`phone-logcat-silenced`):

- Read-only, always allowed: `dumpsys package $PKG | grep -E 'versionCode|versionName|lastUpdateTime'`,
  `ps -A -o PID,PPID,USER,NAME` filtered by the app uid, and `dumpsys activity exit-info $PKG`. The phone's logcat is
  silenced (`persist.log.tag=S`), so expect little from it.
- Installs only while the owner is present. The owner runs the six smoke flows by hand: E2E-01, 05, 06, 08, 25 and 26.
  The coordinator never captures the screen and never changes a device setting.

**R15, Arabic and text-size runs**: `run.py --lang ar --avd A`, with `OCN_A` booted in Arabic (§4.6). For text size,
the flows change the app's own setting (E2E-23). The capacity smoke sets `$A settings put system font_scale 2.0` and
**restores 1.0 in `finally`**.

**R16, network loss**: Maestro `setAirplaneMode: enabled` (Android) or `[adb] cmd connectivity airplane-mode enable`;
cutting one server goes through fx-proxy `POST /ctl/refuse?host=10.0.2.2:4097`. Reconnect counts (02 S-18) come from
FX-OC2's request log.

---

## 9. CI layout and runtime budgets

### 9.1 Where tests run

| Job | Machine | Trigger | Run by |
|---|---|---|---|
| T0 | PC or VPS | while editing | builder, test writer |
| T1 | VPS (`~/wt/ocn-<unit>`, synced by `vps_gradle.sh`, 02 §7.3) | branch ready; after merging `native/main` into the branch | builder, test writer (`--expect-red`) |
| T2 | VPS (`~/ocn/main`) | integration checkpoint (at least daily while merges land); every preview or switch candidate | coordinator |
| Nightly and weekly jobs (§9.4) | VPS cron | schedule | coordinator owns the crontab |
| T3 tiers (§4.8) | PC + one emulator | owner APKs, slice ends, weekly, switch | T3 holder |
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
  - For this to work across `~/ocn/main` and `~/wt/ocn-*`, every test input must be path-relative. The fixture root
    (§3.2) is therefore passed through a `CommandLineArgumentProvider` whose `@InputDirectory` is
    `@PathSensitive(PathSensitivity.RELATIVE)`, never as an absolute `systemProperty` (a request to the build-logic
    owner).
- **Test JVM settings** (the `oc.kmp.library` and `oc.android.library` conventions):
  - `maxHeapSize = "1g"`;
  - `user.timezone=UTC`, `user.language=en`, `user.country=US`;
  - JUnit XML always on, `failFast = false`;
  - `maxParallelForks` from `oc.test.forks` (PC 1, VPS 3; 03 §4);
  - Robolectric host tests on a Temurin 21 `javaLauncher` (ADR §8);
  - `useJUnitPlatform()`, so `kotlin.test` uses its JUnit 5 adapter and N3 can randomise method order.
- **Golden guard:** the Roborazzi record task refuses to run unless `OC_GOLDEN_HOST=vps` is set. The PC may compare,
  never record (ADR §8; 06 §15.3).

### 9.3 What each tier runs (the test side of 02 §8 and 03 §8.3)

| Tier | Test-side steps, in order |
|---|---|
| T0 | `./gradlew <module>:desktopTest --tests "*B-XXX-NNN*"` (or `testAndroidHostTest`, or `:android:x:testDebugUnitTest`) |
| T1 | touched modules (merge-base with `native/main` + uncommitted): `desktopTest`, `testAndroidHostTest`, Roborazzi verify, `:quality:test` (Konsist + §6.5 gates that need no T2 input), `lint_flows.py` if `native/e2e/**` changed → **rerun each failing test once** → classify (§10) → `--expect-red` mode on tests branches (§2.7) → `summary.txt` |
| T2 | everything in 03 §8.3's T2 line, `--continue` → rerun-once classifier → `:quality:catalogReport --t2` → `FlowStringsFrozenTest`, `FlowRowsTest`, `ScreenshotVerdictTest` → if green: `native/LAST_GREEN` line, then commit `docs/native/parity.md`, `parity-rows.json`, `parity-history.csv` and `config/catalog-coverage.txt` (coordinator, `docs(native): parity <pct> at <sha> [skip ci]`) |
| T3 | `t3.sh <apk> --tier <tier>`: R1 → R2 → R3 → instrumented classes for the tier (`connectedStableReleaseAndroidTest` with a filter) → R4 + `run.py` → R11 → R13 (`switch` only) → `:quality:catalogReport --t3` (device ratchet) → `pair.py` → `summary.txt` |

### 9.4 Scheduled jobs (VPS cron)

The jobs run in owner time with `CRON_TZ=Etc/GMT-4` (UTC+4); the lines live in `native/tool/ci/crontab.txt`. They are
VPS-only, so they are unaffected by the PC quiet-hours rule (lifted anyway, 02 §7.8).

| Job | When | What | Budget [E] |
|---|---|---|---|
| N1 clean nightly | daily 03:30 | `~/ocn/nightly` at the `native/main` tip; `--rerun-tasks` for every `Test` and Roborazzi verify task (no cache); compares each result with the cached T2 of the same commit. A difference means a cache or determinism bug and opens a `FLAKES.md` entry. | ≤ 75 min |
| N2 iOS klibs | daily 05:00 | `-Poc.ios=true compileKotlinIosArm64 compileKotlinIosSimulatorArm64` (ADR §6.3, 03 §8.3) | ≤ 30 min |
| N3 flake hunt | Sunday 00:30 | all JVM tests ×5 with random method order (`junit.jupiter.testmethod.order.default=…MethodOrderer$Random`, seed recorded) | ≤ 3 h |
| N4 drift | Sunday 04:30 | dependency-analysis 3.20.1; R8 analyzer score and arm64 size trend (13 owns the gates); `twin_check.sh` (02 §5.6); pins in `TWINS.md` vs the versions in `native/fixtures/wire/*/` (a pin bump without a re-recording is reported) | ≤ 45 min |
| N5 disk | daily 02:30 | removes `~/wt/ocn-<unit>` whose branch is an ancestor of `native/main` (`git -C ~/Storage/Code/oc_app.git merge-base --is-ancestor`), reports older than 14 days, and superseded APKs; reports when `/` has under 40 GB free | ≤ 5 min |

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
| Flows: animations off, waits not sleeps, the deterministic stub model, snapshot starts, `retry:` only on system UI | `lint_flows.py` (§4.2) |

### 10.2 Detection

- `t1.sh`, `t2.sh` and `run.py` rerun every failure **once**, in isolation: `--tests` with the exact name, or the same
  flow from the same snapshot.
- N1 compares cached and uncached results of the same commit.
- N3 hunts order dependence with random method order.
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
- `full` twice with `--no-retry`.
- The six phone-smoke flows three times.
- J1–J8 green.
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
flow groups follow §4.8; section 15 may move them.

| Slice | Rows green at T2 | Flows green on the native release build | Device and instrumented | Screens and visuals | Test infrastructure ready at the end of the slice |
|---|---|---|---|---|---|
| **0** skeleton and proofs | the rows of `:core:common`, `:core:security`, `:core:model`, `:core:domain` that `contracts/s0.md` assigns (Redactor, external-link, address-rule and capability-default vectors; 03 §22.1) | none native. **G2 and E2E-39 Parts A/C proven on Flutter R** | P5 short upgrade (E2E-39 steps 3, 4, 6, 8 on the spike build); one v11 secret decrypted on the emulator | one Roborazzi golden recorded on the VPS (P7) | W14-1, 2, 3, 6, 7, 9 and 17 done; the first W14-5 families and the G2 baseline record (W14-13); the recorder, the replay server and FX-TLS (G2 needs them), with the slice-1/2 OC2 recordings; Flutter baselines (02 §4.1); slice-1 tests branches red and passing `--expect-red` |
| **1** foundations | every `slice = 1` row | none (no screens yet) | `SyntheticFlutterSecretsTest`, `KeystoreSecretStoreDeviceTest` (05 §10.3) | KIT screenshot rows of the foundation parts, with verdicts | FX-LINUXDL, FX-PASEO, FX-AUTH; **G3 proven on Flutter**; slice-2 tests branches red; the widget-name index; P10 numbers fed into §2.11 |
| **2** chat daily path | every `slice = 2` row | **E2E-01, 03, 04, 05, 10, 21, 35** | `upgrade` tier on every candidate that touches migration; `OCN_26` smoke of E2E-01 and 05 | CHAT and NAV screenshot rows of the slice; first SSIM triage and verdicts | **G4 proven on Flutter** (no new fixture); first weekly `full` |
| **3** phone runtime and agents | every `slice = 3` row | **E2E-02, 06, 11, 17, 18, 19, 30, 31** (AVD S and F steps included) | `:android:linux` acceptance through `B_LINUX_…` wrappers; R12 shows no orphan after a stop | LINUX and AGENT screenshot rows | FX-GH, FX-GIT, FX-CODEX, FX-USAGE; **G5 proven on Flutter** |
| **4** surfaces, settings, schedules | every `slice = 4` row | **E2E-07, 08, 09, 22, 23, 25, 26, 27, 34, 36, 38**, with the Arabic and tablet repeats | UIAutomator widget, tile and notification tests (11 §11.6); R8 restore after reboot; R11 clean | ANDR and NAV screenshot rows; the auditor pass over every style × light/dark + Saver + RTL (02 §7.10) | `OCN_T`, `OCN_36`; the `-qa.yaml` companion for E2E-39 step 13 |
| **5** project, media, integrations | every `slice = 5` row | **E2E-12, 13, 14, 15, 16, 20, 24, 29, 33, 37** | sherpa-onnx load, the isolated PDF service, a CameraX scan (E2E-02 again) | MEDIA and the remaining screenshot rows | — |
| **6** migration hardening and switch | every `slice = 6` row, and **100 %** of switch rows on the candidate | **all 37** + **E2E-39** on the candidate | J1–J8; the `switch` tier (§4.8, §10.4); R14 owner phone test | zero open `regression` verdicts; owner images and videos | `SliceDoneTest --slice 6` green |

Every slice end also needs these six things:

1. `SliceDoneTest --slice N` green on a `native/main` commit (§6.5).
2. `t3.sh --tier slice` green on the release build of **that** commit.
3. No `TODO("B-` left in slice-N modules (`NoStubsLeftTest`).
4. No `FLAKES.md` entry against a slice-N row past its due date.
5. The tests branches of slice N+1 exist and pass `--expect-red`. The flow group of slice N+2 is proven on Flutter.
6. The QA record `docs/qa/native/slice-<N>-<YYYY-MM-DD>/README.md` (02 §9.4). It holds the `parity.json` excerpt,
   the flows table with run links, the verdicts, the video links and "NOT proven".

**Switch-ready, test view** (ADR §10.1 and 02 §6.7), on the candidate commit:

- `rows.green == rows.switch`, `red == 0`, `todo == 0`;
- the device evidence commit equals the candidate commit;
- the `switch` tier is green;
- 13's numbers are met or owner-relaxed;
- the owner phone test has passed.

---

## 12. Work units for this section

One agent per unit, in its own worktree and branch (02 §7.1). Sizes are estimates [E], re-planned at M0 (ADR P10).

| # | Unit | Paths / modules | Model | Slice | Needs | Size [E] | Done when |
|---|---|---|---|---|---|---|---|
| W14-1 | `:core:testing` additions (§3.3) | `:core:testing` | S core (Codex) | 0 | 03 §10.8 base set | ~1.2k lines + self-tests | signatures frozen in `contracts/s1.md`; self-tests green |
| W14-2 | catalog report and gates (§6) | `:quality` | coordinator + Sonnet | 0 | `scope.jsonl` | ~1.5k | `parity.json` and `parity.md` from a real T2; each §6.5 gate fails on a planted error |
| W14-3 | test side of `t0`–`t3.sh`, rerun classifier, `RedReasonCheck`, `FLAKES.md` automation | `native/tool/` | coordinator + build-logic owner | 0 | 02 §8 scripts | ~800 | `--expect-red` right on a planted tests branch; a planted flaky test lands in `FLAKES.md` |
| W14-4 | wire recorder, replay server, OC2 2.1.1 recordings, schema check | `:tools:wire-recorder`, `native/fixtures/wire` | Codex | 0–1 | build-logic request | ~1.5k + fixtures | scrubbed recordings for the slice-1/2 contract rows; replay serves E2E-03's failures; `OpenApiConformanceTest` green |
| W14-5 | vectors worktree + the first six families | `native/tools/flutter-fixtures/vectors`, `native/fixtures/vectors` | Sonnet | 0–1 | pinned Flutter | ~600 Dart + JSON | `VectorsProvenanceTest` green; each family read by its test |
| W14-6 | `run.py`, `gen_strings.py`, `lint_flows.py`, `rows.py`, `scrub.py`, `pair.py`; R1–R4 and R11–R13; `avd/create.sh` | `native/tool/e2e`, `native/tool/device`, `native/e2e/avd` | Sonnet | 0 | `OCN_A` | ~1.5k Python/sh | E2E-01 green twice on Flutter through `run.py`; a planted password is scrubbed; a planted canary fails the run |
| W14-7 | FX-OC2, `stub_model.py`, FX-OC2-B, fx-proxy | `native/e2e/fixtures` | Sonnet | 0 | — | ~900 | E2E-05 and E2E-10 green on Flutter; 13's PJ-05 runs on `perf:stream-6000` |
| W14-8 | FX-TLS, FX-PASEO, FX-AUTH, FX-LINUXDL, FX-GH, FX-GIT, FX-CODEX, FX-USAGE | `native/e2e/fixtures` | Sonnet (Codex for the Paseo and Codex scripts) | 0–3, in the slice where their flow group is proven on Flutter | — | ~2k | each group's fixtures up before that group is proven on Flutter |
| W14-9 | flows G2 + E2E-39 Parts A/C | `native/e2e/flows`, `native/e2e/upgrade` | flows lane | 0 | W14-6, W14-7 | 9 flows | proven on Flutter (§4.7) |
| W14-10 | flows G3 | same | flows lane | 1 | W14-8 | 8 flows | same |
| W14-11 | flows G4 | same | flows lane | 2 | W14-8 | 11 flows | same |
| W14-12 | flows G5 | same | flows lane | 3 | W14-8 | 10 flows | same |
| W14-13 | Flutter baseline records + reference captures | `docs/qa/native/parity-baseline-flutter-*`, `native/e2e/refs` | T3 holder | 0–3 | the flows | — | one record per group; manifest committed |
| W14-14 | the test-writer lane (continuous) | test source sets | Sonnet ×2; Opus for transcript, migration and runtime rows | 0–5 | frozen contracts | ≈ 127 agent-days | every unit's tests branch passes `--expect-red` before its builder starts |
| W14-15 | widget-name index | `native/tools/flutter-fixtures/widget_index.py`, `native/catalog/widget-index.tsv` | Sonnet | 1 | — | ~150 | the index is committed and briefs cite it |
| W14-16 | the widget phase W-1…W-4 (§5) | — | per T14-4 | later | owner | §5.4 | per §5.3 |
| W14-17 | AVD set and PC disk clean-up | `native/e2e/avd`, `/home/eslam/Storage2/avd` | coordinator | 0 | T14-3 | — | the `OCN_*` set exists; ≥ 20 GB still free |

Flows-lane load [E]: about 0.75 agent-day per flow including proving (≈ 28 agent-days for 37 flows), plus about 15
for fixtures. The emulator is the bottleneck, so the pace is one group per slice, as §4.8 plans.

Order: W14-1, 2, 3, 6 and 7 in the first week of slice 0. W14-9 is finished before slice 0 closes. W14-14 runs from
the slice-0 contract freeze until slice 5.

---

## 13. Decisions and differences

### 13.1 Decisions for the owner (work proceeds on the default)

| # | Decision | Default | If the owner says no |
|---|---|---|---|
| T14-1 | Red tests live only on tests branches, never on `native/main` (§2.2) | Yes | Red tests merge to `native/main`, and T2 becomes a ratchet-only gate ("no previously green row fails") |
| T14-2 | Emulators: flows on API 37 (`OCN_A`; the only recent image installed), plus `OCN_36` for the owner's Android 16 and `OCN_26` for minSdk smoke (§4.6) | Yes | Install API 36 and use it for AVD-A as e2e-flows wrote; API 37 then only for T4 |
| T14-3 | Delete six stale emulators (39.6 GB: `FB_F6`, `Pixel_6`, `OCMN_Stable_1042_QA`, `OCMN_Stable_1042_Public_QA`, `OCMN_UI_Refinement`, `OCMN_Live_Demo_20260913`); keep `OC_API37`, `OC_API35`, `OC_Launch_Demo` | Yes | Only `OCN_A` exists; the S, T, 36 and 26 runs wait for space |
| T14-4 | The screen-test (widget) phase starts the week after the switch, earlier only when the §5.2 trigger fires; the name index is made now | Yes | Start at slice 4 with one dedicated UI test writer; or no index |
| T14-5 | Flakes: one automatic rerun; a pass on the rerun counts but opens a 2-day fix ticket; a second flake freezes merges into that module (§10) | Yes | No reruns at all: any failure is red |
| T14-6 | Switch candidate: no retries, two clean full flow runs, three clean smoke runs (§10.4) | Yes | Name the bar |

### 13.2 Differences with other sections (for the coordinator)

1. **AVD-A's API level.** e2e-flows and 02's glossary say API 36. This section uses API 37 (T14-2), because no API 36
   image is installed [V].
2. **The `qa` build type.** 13 §3.2 lists `qa` as `….native` with debug signing. 05 J2 and this section's QA
   companions need `stableQa`: the main package, signed with the local key, so it installs over Flutter R and over
   `stableRelease`. Proposal: 13's row becomes `previewQa`, and `stableQa` is added.
3. **Which emulator wipes.** 05 §10.4 records the dumps on `OC_API37` with `-wipe-data`, but 13 §3.7 keeps its
   `perf-base` snapshot on `OC_API37`. Proposal: dumps and journeys move to `OCN_A` (§4.6).
4. **T3's budget.** 02 §8's 20–40 min fits the `smoke` tier only. The other tiers are defined in §4.8.
5. **Telemetry.** 02 §1.1 row 17 says no telemetry. Memory `freeze-owner-decisions-2026-11-01` says the owner
   reversed that for the native app (an analytics core, BASIC always on, DETAILED opt-in). Section 16 decides. The
   canary and egress checks here read `native/config/egress-allowlist.txt` either way, so only that file changes.
6. **The `ui` level.** 02 §6.1 has no `ui` level. §5.3 adds it when the widget phase starts; until then nothing uses
   it.

---

## 14. Unknowns this section depends on (slice 0 proves them)

| # | Question | How it is settled | Fallback |
|---|---|---|---|
| U14-1 | Does Maestro 2.12.0 interpolate `${APP_ID}` in `appId`? | one flow in W14-6 | `run.py` writes a copy with the literal package |
| U14-2 | Does `label:` on commands appear in reports? | same | step comments plus the debug output's command list |
| U14-3 | Does `emulator -http-proxy` carry every guest TCP connection (both apps, HTTPS by CONNECT)? | fx-proxy's connection log during E2E-01 | cut and throttle inside each fixture server; FX-LINUXDL throttles itself |
| U14-4 | Does `-change-locale ar-EG` work on the API 37 image? | boot `OCN_A` | as root: `setprop persist.sys.locale ar-EG; setprop ctl.restart zygote` |
| U14-5 | Does `tailscale cert` plus `-dns-server 100.100.100.100` give FX-TLS a host both apps trust? | E2E-04 step 1 | use `tailscale serve` on the PC's real `.ts.net` name for the trusted case |
| U14-6 | Are test results cacheable across `~/ocn/main` and `~/wt/ocn-*` with the relative fixture root? | T1 then T2 of the same commit shows `FROM-CACHE` | accept a longer T2 within its hard limit; one fixed checkout path |
| U14-7 | Do JUnit XML test names keep backticks and spaces through the AGP-KMP host-test task? | the first T2 | `RowIds` already accepts `-` and `_` forms; add a normaliser |
| U14-8 | Does Flutter R expose every label the flows need on the API 37 image? | G2 proving | `extra.*.json`; a missing label becomes a KNOWN gap (a Flutter fix only if the owner hits it, 02 §5.2) |
| U14-9 | Does networknt 3.0.8 resolve OAS 3.1 `$ref`s? (testing-perf §13 item 9) | W14-4 | bundle with swagger-parser 2.1.48 first |
| U14-10 | Robolectric 4.17 at SDK 37 on JDK 21; headless desktop `runComposeUiTest` on the VPS | ADR P7 | `@Config(sdk = [35])` for most tests; `xvfb-run` |
| U14-11 | Do framework infinite transitions pause under the CMP test harness? (08 S8-5) | ADR P7 kit test | `stillFrame` everywhere in tests |
| U14-12 | Does `cmd statusbar add-tile` work on the API 37 image? | R10 | add the tile through the shade's edit mode with UIAutomator |

---

## 15. Sources

- Binding: `plan/01-architecture-decision.md` (§1, §4 rows 16–18, §5.2, §6.3, §7.2, §8, §9.1, §9.7, §9.9, §10, §11,
  §12); `plan/02-principles-scope.md` (§3, §4, §5.3, §6, §7, §8, §9, §10, NG11, NG12).
- Plan sections read: 03 (§1, §8, §10.1, §10.8, §19.6, §20.7, §21.1, §22); 05 (§10, §11); 06 (§15, §19 Q2); 08 (§9);
  09 (§14 flows list); 11 (§4.3, §11.6); 13 (§3.1–§3.8).
- Catalog: `catalog/BEHAVIORS.md` (summary, MIGR, Appendices B–E), `catalog/behaviors.jsonl` (all counts in §1 and
  §3 computed today), `catalog/e2e-flows.md` (rules, environment matrix, fixtures, stub prompts, E2E-05, E2E-15,
  E2E-39, coverage).
- Research: `research/testing-perf.md` (all sections); `research/kmp-cmp-status.md` (task naming, headless desktop).
- Repo, read-only (`/home/eslam/Storage/Code/wt/oc_app-batch1` at `65e3f70a3`): `test/` (counts),
  `test/fixtures/api2/` (24 files), `tool/qa/issue87/mock_llm.py`, `tool/qa/codex_fixture/`,
  `tool/qa/run_tests_fast.sh`, `tool/qa/machine_lock.sh`, `android/app/src/test` (46 files, 645 `@Test`),
  `android/app/src/androidTest` (14 files), `lib/domain/git/github_device_flow.dart`,
  `assets/agents/paseo-package-lock.json` (Paseo 0.11.2), AGENTS.md.
- This PC, checked 2026-11-01 [V]: `~/.android/avd/*` (OC_API37 = android-37.0 google_apis x86_64, 4 GB, 16 GB),
  `~/Android/Sdk/system-images` (34, 35, 37.0), `du` of the stale AVDs (39.6 GB), `df` (`/` 20 GB, Storage 4.2 GB,
  Storage2 7.3 GB free), ImageMagick 6.9.12-98.
- Web, 2026-11-01 [V]: Maestro CLI reference (`--format JUNIT|HTML|NOOP`, `--output`, `--test-output-dir`,
  `--debug-output`, `--include-tags`, `--exclude-tags`, `-e`, `--shard-split`/`--shard-all`) at
  https://docs.maestro.dev/maestro-cli/maestro-cli-commands-and-options and
  https://docs.maestro.dev/cli/test-suites-and-reports; Maestro `retry` command (`maxRetries` 0–3, default 1) at
  https://docs.maestro.dev/api-reference/commands/retry.
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `build-vps`,
  `vps-test-env-gaps`, `vps-suite-shared-dir-trap`, `shared-emulator-contention`, `emulator-lock-holder`,
  `test-on-emulator-not-phone`, `owner-tests-i-gate`, `pre-existing-means-last-green`,
  `no-full-suite-for-unrelated-changes`, `audit-trail-rule`, `live-video-proof`, `apk-build-numbers`,
  `restore-proof-recipe`, `hog-watch-monitor`, `pc-capacity-ceiling`, `independent-visual-auditor`,
  `one-screen-per-image`, `no-owner-phone-screenshots`, `owner-phone-debug-allowed`, `phone-logcat-silenced`,
  `launch-video-2026-10-19`, `opencode2-stable-2.0-port`, `proot-orphans-heat`, `verify-fixes-with-a-failing-test`,
  `no-heavy-workflows`, `judge-visuals-hard`, `short-replies-rule`.
- The owner's words relayed for this run: "7400 for widgets can come later."

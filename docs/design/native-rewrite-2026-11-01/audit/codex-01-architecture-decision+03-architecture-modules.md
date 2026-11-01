**Verdict: BLOCKED — the plan contains a deletion race, a known build configuration mismatch, and incompatible contracts that must be resolved before implementation fans out.**

This was a read-only audit of sections 01 and 03, supporting research, relevant Flutter source, and available Gradle artifacts. No builds or tests ran. Direct Maven retrieval was unavailable; uncached versions are not independently artifact-verified here.

**1. BLOCKER — `WriterLane.drain()` can finish before an admitted writer runs**

**Where:** [03 §17.3](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:2548); [01 §9.5](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:611).

**Problem:** The supplied implementation does not provide the deletion guarantee it claims.

**Evidence:** This interleaving is possible:

1. Writer reads `admitting == true`, then its thread pauses before acquiring `mutex`.
2. Deletion calls `stopAdmission()`.
3. `drain()` acquires the currently free mutex and returns.
4. Deletion sweeps the profile.
5. Writer resumes, acquires the mutex and restores deleted data.

Mutex fairness cannot account for a writer that has not reached the lock. This regresses B-STATE-013 and B-CHAT-051. Today’s [ContextUsageMemory.closeProfile](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/context_usage_memory.dart:36) returns the already-registered write tail.

**Concrete fix:** Make admission and registration of outstanding work atomic. Close admission atomically, then await every registered operation, including operations waiting for the write mutex. Specify cancellation handling and add a regression scenario that pauses a writer between admission and lock acquisition.

**2. BLOCKER — the default compile SDK contradicts the pinned Compose dependency**

**Where:** [01 §8](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:479); [03 §6.3](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:657).

**Problem:** `COMPILE = 37` is presented as executable configuration, with 37.1 treated as conditional.

**Evidence:** The research already identifies the requirement in `research/kmp-cmp-status.md` §13.2. AndroidX’s release notes explicitly state that Compose 1.13 requires applications and consuming libraries to update to compileSdk 37.1. [Compose UI release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui?hl=en).

**Concrete fix:** Specify API 37 minor level 1, the required SDK package, and the supported AGP DSL in both application and library conventions. Make resolving and checking the pinned AAR metadata an early P1 prerequisite.

**3. BLOCKER — public signatures require forbidden module dependencies**

**Where:** [01 §6.1](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:292); [03 §6.6](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:850), [§10.2](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1341), [§12.2](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1773).

**Problem:** Following the signatures and following the dependency rules produce different implementations.

**Evidence:**

| Required reference | Conflict |
|---|---|
| `:core:security` uses `ServerOrigin` in address normalization and cleartext consent | `ServerOrigin` lives in `:core:model`; security may depend only on common |
| `:ui:kit-content` implements `WebPreview(url: ApprovedLink, …)` | `ApprovedLink` lives in security; kit-content cannot declare that dependency |
| `:core:net` resolves named secrets through `SecretStore` at request time | `SecretStore` lives in data; net cannot depend on data, and no credential-reader seam is specified |

The first two are concrete type-visibility problems. The third leaves the required transport implementation without a defined legal dependency path.

**Concrete fix:** Reconcile type ownership with the ADR before freezing signatures. Move foundational address types down or explicitly allow security → model; permit kit-content → security or introduce an appropriate shared link type; define an injected transport credential-reader interface with its store adapter assembled above both modules.

**4. BLOCKER — there are incompatible definitions of the frozen storage contracts**

**Where:** [03 §10.5](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1505), [§22.4](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3156); supporting [05 §2.1](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:87), [§7.3](/home/eslam/Storage/Code/wt/native-plan/plan/05-data-migration.md:562).

**Problem:** Storage, migration and scheduler agents cannot implement against one agreed API.

**Evidence:**

- Section 03 defines suspending typed reads and `edit`; section 05 defines synchronous `get(): PrefValue?`, `commit` and `compareAndSet`.
- Section 03 blocks reads until migration opens; section 05 explicitly permits reads and gates writes.
- Section 03 defines `awaitOpen()` and `MigrationState`; section 05 defines `awaitWritable()`, `GateState` and `MigrationActions`.
- Section 05 requires failure variants such as `StorageRefused` and `InvalidValue`, absent from section 03’s closed `Failure` hierarchy.
- The two `ProfileScope` interfaces also differ.

These affect migration recovery and schedule CAS, not merely naming.

**Concrete fix:** Publish one authoritative, compilable contract for these seams. Include corrupt-value representation, CAS, migrator write authority, recovery actions and complete failure variants. Make the migration and schedule examples compile against that contract before declaring it frozen.

**5. MAJOR — the proposed profile model loses existing project and migration state**

**Where:** [03 §10.3](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1354).

**Problem:** The shown `ServerProfile` is insufficient for day-one parity and byte-compatible profile fingerprints.

**Evidence:** It omits `codexDirectory`, `requiresOpenCode2Migration`, `openCode2HistoryPending`, `serverVersion` and retained orchestration metadata. These are persisted by today’s [profile serializer](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/profiles/models.dart:301). Section 05 §2.5 requires those same fields and their ordering when reproducing schedule and widget fingerprints.

Dropping the project directory changes agent scope. Dropping history-pending state loses the distinction between copied history and confirmed import. Reconstructing fingerprints from the reduced model invalidates existing schedules and widget targets.

**Concrete fix:** Freeze the complete persisted profile DTO, including retained deferred-feature metadata, separately from any reduced screen model. Add explicit runtime reentry state and preserve exact-origin trust information.

**6. MAJOR — cancellation is treated as a replacement for identity fencing without wiring commands into the cancelled scope**

**Where:** [01 §9.5](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:613); [03 §11.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1663), [§17.1–17.5](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:2528).

**Problem:** Cancelling a connection generation does not automatically cancel a command invoked from `viewModelScope`.

**Evidence:** The engine template runs commands with `withContext(lane)`. A dispatcher change retains the caller’s job; injecting `ConnectionCoroutineScope` does not make those calls its children. The generation scope appears in the table but is absent from the command contract.

Also, `limitedParallelism(1)` serializes execution **between suspension points**, not whole operations. Its official documentation explicitly warns that it is not a mutex. [Coroutines API](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html).

Today’s [selection mutation path](/home/eslam/Storage/Code/wt/oc_app-batch1/lib/state/connection/models.dart:316) combines serialized operations with identity checks before and after remote work.

**Concrete fix:** Define how commands execute under the owning connection generation, how callers await them, and where mutation admission occurs. Retain endpoint/location/generation checks immediately before dispatch and before publishing results. Require cancellation-and-join where replacement depends on old work stopping.

**7. MAJOR — WorkManager initialization contradicts the migration startup ordering**

**Where:** [03 §13.6](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:2008), [§21.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:2999); [01 §9.7](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:647).

**Problem:** Section 03 keeps the default WorkManager initializer, while migration requires controlled startup before stores and background work.

**Evidence:** Section 05 §7.1 explicitly removes `WorkManagerInitializer` and implements `Configuration.Provider`. Section 03 explicitly retains default initialization and omits that interface. Android documents default initialization through App Startup and the required removal for on-demand initialization. [WorkManager configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration).

**Concrete fix:** Amend section 03’s manifest and `OcApplication` recipe to match the migration startup contract. Keep the default worker constructor if desired; that is independent of retaining the default initializer.

**8. MAJOR — first-launch backup can block the very recovery UI intended to handle migration failures**

**Where:** [01 §9.7](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:649), [§11 P5](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:802); [03 §21.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3006).

**Problem:** Potentially large copies and filesystem synchronization run on the main thread before the graph or first frame exists.

**Evidence:** P5 permits a 100 MB backup and accepts a nominal emulator time below one second. That does not bound slow or nearly full device storage. The supplied `onCreate` also calls backup without a failure-to-bootstrap-state path. Returning `SafeMode` from the preceding guard still leads straight back through backup.

**Concrete fix:** Make asynchronous backup behind a pre-store startup fence the default, rather than an optional fallback. Build the opening/recovery shell without depending on migrated preferences. Convert backup failures into a recoverable gate state before starting consumers.

**9. MAJOR — the language fallback does not work with the chosen activity on API 26–32**

**Where:** [01 §4 row 6](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:144); [03 §12.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1746), [§21.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3031).

**Problem:** `AppCompatLocaleHost` relies on `AppCompatDelegate`, while the activity is `ComponentActivity`.

**Evidence:** Android documents that the backward-compatible locale APIs require an `AppCompatActivity` context on Android 12 and earlier. A composition-local override alone also does not establish the language for service and notification contexts. [Per-app language documentation](https://developer.android.com/guide/topics/resources/app-languages).

Section 06 §13.6 already specifies a different implementation using `LocaleManager`, wrapped contexts and an explicit Compose locale override.

**Concrete fix:** Adopt section 06’s platform contract in sections 01 and 03, or explicitly choose AppCompatActivity and document its integration. Require P6 on both API 30 and API 33+, including notifications and widgets.

**10. MAJOR — the advertised test and portability gates lack required wiring**

**Where:** [03 §6.4](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:709), [§22.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3115), [§23](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:3182).

**Problem:** The “complete” convention and slice-zero inventory cannot deliver all their stated checks.

**Evidence:**

- The convention requests JDK 17 and configures only test forks. It never selects a JDK 21 test launcher. Foojay makes toolchain downloads possible; it does not select Java 21 for Robolectric. The missing launcher configuration is already shown in `research/testing-perf.md` §5.4.
- Slice zero requires actual implementations only for Android and desktop, but acceptance runs `compileKotlinIosArm64`. `platformIoDispatcher` and `platformTrace()` need iOS actual implementations too.
- The Room example configures KSP for Android and desktop only, despite declaring iOS targets when the property is enabled.

**Concrete fix:** Specify Java 21 launchers for Android host tests; include the required iOS actuals in the inventory; conditionally configure Room code generation for each enabled iOS target. Freeze the resulting task names and expected non-skipped execution.

**11. MAJOR — the Room replacement has no defined public persistence surface**

**Where:** [03 §0.2 S14](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:62), [§10.6](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:1586), [§6.6](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:910).

**Problem:** Repository implementations remain in engines, but DAOs are internal to `:core:db`, and engines cannot declare Room dependencies.

**Evidence:** The only public API specified is an `OcDatabase` handle. That does not define how engine repositories perform typed reads, writes and transactions through internal DAOs. Agents must invent an additional API, expose the DAOs, or move implementations across the stated boundary.

**Concrete fix:** Define a public storage façade in `:core:db` with explicit read/write/transaction methods and neutral types, or relocate repository contracts so DB implementations can legally live there. Provide one complete repository example before promising that Room changes only one class per store.

**12. MAJOR — the stability policy can increase transcript cost and hide invalid assumptions**

**Where:** [01 §9.3](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:565), [§11 P4](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:801); [03 §19.2](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:2689).

**Problem:** Whole packages are declared stable, and success is judged by fewer recompositions.

**Evidence:** The packages include service interfaces and scopes as well as immutable values. Stability configuration asserts a contract; it does not establish immutability. Structural comparisons of large collection-bearing states can also cost more than the recompositions removed. Android’s guidance specifically warns against using recomposition counts alone and against forcing structural equality on large lazy-layout models. [Compose stability guidance](https://developer.android.com/develop/ui/compose/performance/stability/fix).

P4’s fallback to “widen the config” can conceal the underlying problem.

**Concrete fix:** Allowlist audited value types rather than entire service packages. Measure frame time, comparison cost and allocations alongside recomposition counts. Replace the widening fallback with investigation of state ownership and reference reuse.

**13. MAJOR — the documented fallback violates the release dependency policy**

**Where:** [01 §8](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:480), [§12 D12](/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md:828); [03 §5](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:370).

**Problem:** The release policy permits beta only for Material 3, but the default toolkit is beta and the documented fallback contains alpha Material 3.

**Evidence:** The default pins CMP runtime/UI/foundation to `1.13.0-beta01`. The fallback uses `material3:1.12.0-alpha03`. I inspected that cached artifact’s `.module`: its Android variant requires Jetpack Material 3 `1.5.0-alpha22`. It therefore cannot satisfy “no alpha artifact in the release classpath.”

**Concrete fix:** Separate development pins from switch-release admission. Specify an eligible fallback with its feature consequences, or make stable availability an explicit release dependency. Enforce the policy against resolved runtime artifacts, including transitive dependencies.

**14. MINOR — Flutter removal has two different dates**

**Where:** [03 §1.1](/home/eslam/Storage/Code/wt/native-plan/plan/03-architecture-modules.md:75).

**Problem:** The repository recipe directs conflicting cleanup actions.

**Evidence:** The tree says Flutter is removed at switch+1; the following paragraph says it is deleted at the switch. ADR D14 also requires retaining the frozen Flutter Linux client until native desktop ships.

**Concrete fix:** State separately when Android packaging stops using Flutter, when migration/rollback tooling may be retired, and when the Linux client’s source and release path may be removed.

The three things I would change first:

1. Replace `WriterLane` with an atomic admission-and-drain contract that preserves deletion guarantees.
2. Unify the module graph and storage/migration interfaces into one compilable contract, retaining the complete legacy profile schema.
3. Correct compileSdk and test toolchains, then freeze one startup sequence covering backup, WorkManager and recovery before parallel implementation begins.
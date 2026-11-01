# 03. Architecture and modules: the buildable skeleton

Written 2026-11-01. This section turns `01-architecture-decision.md` (the ADR) into files, folders, build scripts and
Kotlin signatures that an agent can create without asking questions. **The ADR is binding.** Where this section adds a
choice the ADR did not make, the choice is listed in §0.2 with its reason. Where the two ever disagree, the ADR wins and
this file gets corrected. Several contracts this section shows are **owned by another section** (§0.3): for those, the
owner section's shape wins over the copy shown here, and the coordinator merges all of them into one
`contracts/slice-1.md` before any slice-1 brief goes out.

## For the owner, in five lines

1. This is the blueprint for the new app's code: every folder, every module, which module may use which, and the
   exact library versions, so 60-odd small pieces can be built by different agents without stepping on each other.
2. The new code lives in a `native/` folder of today's repository, beside the frozen Flutter app; adding a screen
   means adding one folder, with no shared list for agents to fight over.
3. Each layer has one job: protocols talk to servers, engines are the app's brain, the kit draws every pixel, and
   screens only arrange kit parts. The build refuses a wrong import before any test runs.
4. Errors turn into plain words by construction, secrets are scrubbed before any log line is written, and background
   work always runs inside a scope that ends cleanly when its server, screen or app goes away.
5. Slice 0 builds about a dozen of these modules and proves them with the checks in §22–§23. You have one decision
   here (D03-1): keep the native code in `native/` of the current repository (recommended) or start a new repository.

Tags: **[V]** checked today (2026-11-01) against Maven/Google Maven metadata, published sources or official docs.
**[R]** read in the repo, a map, a research file, the ADR or memory. **[U]** unverified; the named slice-0 proof (P1–P10
from ADR §11, or P-03-x from §22.3) settles it. Behaviour ids such as `B-STATE-042` are rows of
`../catalog/BEHAVIORS.md`.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read | Then |
|---|---|---|
| Coordinator, slice 0 | all of it | create the files in §1–§8 and §22, run §23 |
| Engine or protocol builder (mostly Codex) | §0, §2, §7, §9–§13, §16–§18, §20.2–§20.3 | write the module's `contracts/<slice>.md` entries first |
| Feature builder (mostly Sonnet) | §0, §2, §7, §14–§16, §19.4, §20.1, §20.7 | build against frozen `XUi`/`XActions` |
| Kit builder | §0, §2, §12 (`KitPlatform`), §15, §19.4–§19.6, §20.5 | follow the kit section of the plan for parts |
| Android-module builder | §0, §2.3, §12, §13.6, §20.4, §21.1 | never touch `:feature:*` |
| Reviewer | §19.7 (the rules text) and §20.7 (definition of done) | reviewers never launch test processes (ADR §10.3) |

Every reader also reads §0.3: it says which section owns each frozen contract.

What this section does **not** decide: dates and slice contents (delivery plan), the kit part list and visual
rules (kit section), migration steps (migration section, ADR §9.7), test tooling beyond module wiring (testing
section). It gives them a place to live.

### 0.2 Choices this section adds to the ADR

| # | Choice | Why (and what it removes) |
|---|---|---|
| S1 | The Gradle root is `native/` inside the existing `oc_app` repository (owner decision **D03-1**, default yes). | Same history, same worktree habit, same CI home, the Flutter Kotlin can be copied with blame intact. Removes a second repo to keep in sync. |
| S2 | `native/settings.gradle.kts` discovers modules from folders; `MODULE_OWNERS` is the gate (a `:quality:rules` test fails on a module without an owner row). | No central include list that every feature agent edits. |
| S3 | Backends register as a **set** of `GatewayFactory` (each knows its `BackendKind` through `backend`; its members are owned by 04 §11), not a Metro map with a custom key. | One fewer Metro feature to learn; a duplicate backend fails a startup check and the graph smoke test. |
| S4 | Connection-scoped engines are reached through **accessor extensions** on `ConnectionGraph` (`graph.transcript`), each contributed by its own engine with `@ContributesTo(ConnectionScope::class)`. | `:engine:connection` sits below transcript, delivery and requests (ADR §6.2) and must not import them. |
| S5 | ViewModels that need a route argument use Metro's `ManualViewModelAssistedFactory` + `assistedMetroViewModel`. | Type-safe route arguments, no `SavedStateHandle` string keys. |
| S6 | The `Redacted` *type* lives in `:core:common` with its constructor behind the opt-in `@RedactorApi`; only `:core:security` (the `Redactor`) opts in. | `Failure` (in `:core:common`) must name `Redacted`, and `:core:common` cannot depend on `:core:security`. The ADR rule "only the Redactor constructs it" still holds and is checked by Konsist rule K13. |
| S7 | `ExternalLinkOpener.open()` accepts only an `ApprovedLink`. Like `Redacted` (S6), the `ApprovedLink` *type* lives in `:core:common` with its constructor behind the opt-in `@LinkPolicyApi`; only `ExternalLinkPolicy` (`:core:security`) opts in (Konsist K13b). The web-preview slot does **not** take an `ApprovedLink` (§12.2). | The "every URL the app did not author goes through the link policy" invariant becomes a type, not a review item, and the type can be held by `:ui:kit-content`, features and `:android:platform`, none of which may see `:core:security` (ADR §6.1). |
| S8 | Formatting is **ktfmt 0.64, kotlinlang style**, run on changed files only by `tool/format-changed.sh`; no Gradle formatting plugin. | Agents reflowed unrelated files before (memory `agent-format-churn`); no config-time cost. |
| S9 | Flutter-era Kotlin is **copied, not `git mv`ed**, into `:android:*` modules until the switch — but only files with **no `io.flutter` import** (36 of 168 have one [R]); those are rewritten as `…Body` classes (§2.3). Copied files are exempt from the "everywhere" Konsist rules through a shrink-only list (`config/copied-kotlin-exemptions.txt`). `tool/native/kotlin-drift.sh` lists Flutter-side Kotlin commits made after the copy point. | The frozen Flutter app still ships bug fixes until parity (ADR §10); the copy commit compiles and passes T2 at the same time. |
| S10 | `EntryInstaller`, `AppNavigator`, `RouteMeta`/`RouteMotion` and the sealed `AppRoute` live in `:ui:routes`. Their **shapes and behaviour are owned by 07 §2** (§0.3). | Features and `:ui:shell` both depend on `:ui:routes`; neither may depend on the other. |
| S11 | Kit content slots (terminal, scanner, web preview) are an interface `KitContentSlots` declared in `:ui:kit`, implemented in `:ui:kit-content` `androidMain`, and handed to `AndroidKitPlatform` by `:androidApp`. Signatures are owned by 06 §6; slot parameters use only kit, design, model and common types. | `:ui:kit` cannot depend on `:ui:kit-content`; Termux and CameraX stay in one `androidMain`. |
| S12 | `EntryRouter` hands targets over as **state plus acknowledgement**, never a channel. The shapes (`EntryRequest`, `EntryTarget`, `EntryHandoff`) are owned by 07 §7. | Same "no one-shot events" rule as ViewModels; survives configuration changes. |
| S13 | `AppLog` is a process-wide facade with a 64-line pre-install buffer that is flushed *through* the redactor at install. | Lines logged before the writers exist can never escape unredacted. |
| S14 | A repository interface lives in the **engine that owns the data**. Its implementation is over `KeyValueStore` now and over Room (`:core:db`) later, in the same engine. | The Room switch (ADR §9.8) changes one class per store and no caller. |
| S15 | `withHostTest {}` (Android host tests) is on only in KMP modules that have an `androidMain` (ADR §7.2 list). Every other KMP module runs `commonTest` once, as `desktopTest`. | Halves JVM test time for 40+ modules with identical code on both targets. |
| S16 | The ADR's product flavour "`main`" is named **`stable`** in Gradle. | A flavour named `main` collides with the `src/main` source set. The `.native` preview flavour keeps the ADR name `preview`. |
| S17 | Every route key implements one sealed `AppRoute : NavKey` in `:ui:routes`; the `SerializersModule` registers `subclassesOfSealed<AppRoute>()` once. | Registration can no longer be forgotten (ADR rule K8 becomes a round-trip test). |
| S18 | Feature modules generate **no** `Res` class: all copy comes from `:ui:words`, all icons from the kit. | One string source (ADR §7.5); no stray resources in features. |
| S19 | Every engine type that a feature or another engine consumes is a **public interface** with an `internal` `@Inject @ContributesBinding` implementation; the engine ships a `Fake…` for it in its `fake` package (§11.1). | Feature agents can write red-first ViewModel tests without building a graph (§13.1 rule 6); Kotlin cannot subclass a final engine class. |
| S20 | WorkManager initializes **on demand**: `OcApplication` implements `Configuration.Provider` and the manifest removes `androidx.work.WorkManagerInitializer` (§13.6). | The default initializer runs from a `ContentProvider` before `Application.onCreate`, so pending work could start before the graph exists. |
| S21 | Per-app language without AppCompat: `LocaleManager` on API 33+, the stored `oc.appLocale` plus an `attachBaseContext` override below 33, and one `LocalizedContext` for every OS surface (§12.3). Activities stay `ComponentActivity`. | `AppCompatDelegate.setApplicationLocales` does nothing on API 26–32 for a `ComponentActivity`, and services never see an activity's locale. |
| S22 | `:quality` is two modules: `:quality:rules` (Konsist and text gates, no project dependencies; T0) and `:quality:graph` (graph smoke, depends on `:desktopApp`; T2 on the VPS only). | A feature agent's quick rule check never compiles the whole app. |
| S23 | Every convention plugin sets the Kotlin **module name** to the namespace (`Namespaces.of(path)`) for every compilation. | `:engine:settings`/`:feature:settings` and five other pairs share a leaf name; default `META-INF/<name>.kotlin_module` files would collide in the APK. |
| S24 | `ServerOrigin` lives in `:core:common`, next to the ids (`ProfileId`, `SessionId`, `RequestId`, `ConversationRef`, …), which stay there although 07 §2.1 lists them under `:core:model`. | `:core:security` (address rules, consent) may depend only on `:core:common` (ADR §6.1), and `Failure` (in `:core:common`) names `RequestId` (04 §3.8). Module placement is 03's (07 §0.2). |
| S25 | `:core:common` holds several `expect` primitives, all with nothing to fake: the IO dispatcher source and the byte primitives (SHA-256, HMAC-SHA-256, secure random bytes, raw inflate). `Trace` is an interface provided by each app graph, not an `expect`. iOS actuals exist from slice 0. | ADR §7.3 item 4 allows `expect` only in `:core:common`, for such primitives; agents otherwise invent `java.security` in `commonMain`. |
| S26 | OS-surface resources live with the code that reads them: widget layouts in `:android:widgets/res`, notification icons in `:android:background/res`, generated `strings_os` in a resources-only module `:android:res` that every `:android:*` module and `:androidApp` may depend on. `:androidApp/res` keeps only manifest-referenced XML and launcher icons. | With `nonTransitiveRClass` and nothing allowed to depend on `:androidApp`, code in `:android:*` cannot read `:androidApp`'s `R`. |
| S27 | **Changed 2026-11-01:** the analytics core is its own engine module **`:engine:analytics`** (package `io.github.eslamasabry.oc.engine.analytics`), second in the engine order, right after `settings` and before `diagnostics`, so every other engine (diagnostics included, for crash and freeze categories) may emit. Its design is plan section 18. | The owner made analytics a day-one core that can talk to other analytics tools, and a cloud session already built it as `native/engine/analytics` (branch `native/analytics-2026-11-01`, 49 tests); 16 N16-D9's package-in-diagnostics home is superseded. It depends only on `:core:common`; senders reach `:core:net` through an `AnalyticsTransport` interface bound in the app graph. |

### 0.3 Who owns each frozen contract (precedence)

ADR §10.3 says "freeze before fan-out". This table names **one owner section per frozen contract**. The order of
authority is: the ADR, then the owner section named here, then any other section (03 included). Where 03 below shows a
signature for a contract it does not own, the copy is context only and the owner's shape is the one frozen. The
coordinator copies this table into ADR §10.3.

| Contract (frozen at the end of slice 0) | Owner | Where | What 03 keeps |
|---|---|---|---|
| `Outcome` (`Ok`/`Failed`), the one flat `Failure` hierarchy, `RefusalReason`, `FeatureId`, `Redacted`, `ApprovedLink` type, `BackendKind` | **03** | §10.1, §10.3 | everything; other sections add fields, subtypes and reasons through change requests applied here before the freeze |
| Detail fields of the transport subtypes (`Network(why)`, `Certificate(why)`, `SignIn(why)`, `Busy(kind)`, …), `Incompatible`, four `RefusalReason`s, the input → `Failure` table | 04 | §3.8, §11.0 | the subtype list (§10.1) |
| `StorageRefused`, `SecretUnavailable` | 05 | §2.2, §4.2 | — |
| `Media` + `MediaProblem` | 10 | §4.1 (C10-1) | — |
| `Runtime` + `RuntimeProblem`; `RefusalReason.BackgroundLimit`, `NotificationsBlocked`, `PhoneLocked` | 11 | §5.5 (CR11-1, CR11-2) | — |
| `GatewayFactory { backend; probe(target); connect(spec) }`, `GatewayPair`, `GatewaySpec`, `ServerGateway` + facets, `ServerOperationsGateway`, `ServerCapabilities`, `ServerEvent`, `StreamStatus`, `NetworkStatus`, the transport (`TransportFactory`, SSE, downloads, pinning), `ConnectionRegistry`/`ConnectionSession`/`ProfileSource` members | 04 | §3, §6, §11, §12 | module placement, the interface rule S19, DI wiring (§13.4) |
| `ServerProfile` persisted fields (`ServerProfileJson`), `ProfileFingerprintV1`, `KeyValueStore`/`PrefEdit`/`PrefValue`, `SecretStore`, `BlobStore`/`BlobRoot`, `MigrationGate`/`MigrationStatus`/`GateState`, `KotlinPrefsFile`, the profile-deletion storage order and generic sweep | 05 | §2–§5, §7 | `WriterLane`/`WriterLanes` (§10.5, §17.3), the deletion orchestration signature (§10.5) |
| `KitTheme`, `KitAppearance`, `KitPlatform`, `KitContentSlots` and slot params, kit part signatures | 06 | §5, §6 | module placement (§12), the constraint that slot params use no `:core:security` type |
| `AppRoute`, route files and keys, `RouteMeta`/`RouteMotion`, `AppNavigator`, arrivals, `SheetKey`, `SearchEntry`, `CapabilityFlow`, `EntryRequest`/`EntryTarget`/`EntryHandoff`/`ShellWorld`, `ConversationRef.backend`, `MainActivity` intent capture | 07 | §2, §7 | `RouteSaving` pattern (S17), DI scoping of the navigator (§15.3) |
| Transcript, requests and delivery engine APIs (`PromptDelivery` and its state machine) | 09 | §4 | the engine template (§11.1) |
| `PickedFile`, `PhotoTarget`, `DocumentTarget`, `SharedFile`, image resolver | 10 | §4.3, §9 | `ActivityResults` home (§10.4) |
| OS components and manifest attributes, FGS kinds and the promise rules, `StatusOwners`, system-event restore, `DeviceFacts` fields, `KeepAliveSettings`, `TailscaleApp`, `OsSettingsPages`, `LaunchSurfaces` split | 11 | §2, §3, §8, §9, §14, §16 | the thin-shell pattern (§13.6) |
| Analytics event schema, tiers, sinks, upload policy, ids | **18** | §3–§7 | the module home and engine-order place (S27) |
| What analytics may never collect, PRIVACY.md and the "What we collect" disclosure, the F-Droid label | 16 | §4, §5 | — |
| Module list, plugin code, module rules and engine order, DI graph shape and scopes, `WriterLane`, `AppLog`/`LogSink`/`LogRing`/`Trace`, `AppDispatchers`, byte primitives, `Formats`, `ProcessStart`/`StartupTask`, the homes of shared platform services, Konsist rules, ids placement | **03** (rules mirror ADR §6) | §1–§9, §13, §17–§19 | — |

**Reconciliation before fan-out.** In slice 0 the coordinator writes `contracts/slice-1.md` with exactly one shape per
type, taken from the owner column, and records every place where a non-owner section differs (known today: 03's own
older copies in §10–§15; 07 §2.1 listing the ids under `:core:model` (they stay in `:core:common`, S24); any section still writing `Outcome.Err` where the name is `Outcome.Failed`; 11 writing
`Failure.Storage` where the name is `StorageRefused`; 16 §5.1's analytics package in `:engine:diagnostics` (now
`:engine:analytics`, S27); 11 S11-8's two
`strings_os` copies vs S26; 04 §12.1 showing `ConnectionRegistry` as a class where S19 makes it an interface). Acceptance check 15 (§23) compiles that file's
interfaces once before any slice-1 brief goes out.

**Slice-0 code that differs from this section** (built from the draft on `native/m0-2026-11-01`, read 2026-11-01; the
coordinator resolves each in `contracts/slice-1.md`, and this section wins unless it says otherwise):
- P1 put `ServerOrigin` in `:core:model` and allowed `:core:security → :core:model` and `:ui:kit-content →
  :core:security` in `ModuleRules`. This section keeps S24/S7 (`ServerOrigin` and `ApprovedLink` in `:core:common`):
  move the two types and delete the two edges and their test vectors in slice 1; it is a one-commit change now and
  never cheaper later.
- P1's `ModuleRules` KDoc plans a `:core:net` credential-reader interface. Superseded: credentials arrive resolved in
  `GatewaySpec.credentials` (§10.7); no reader seam is added.
- P1 used `@BindingContainer` objects for `CommonBindings` and `GatewayBindings` (Metro 1.5.1 warns otherwise, and
  warnings fail T2). Adopted.
- P1 serialised `ConnectionRegistry` open/close with a `Mutex` and close does cancel-and-join. Adopted; it is the
  `GenerationRunner` rule of §11.1.
- P1 set `-Xexpect-actual-classes` on every KMP module. Narrow it back to `:core:common` (§6.4) in slice 1; Konsist K10
  still limits `expect` meanwhile.
- `ModuleOwnersTest` is red on the M0 tip: `:tools:arb2xml`, `:tools:flutter-migration-readers` and
  `:engine:analytics` have no `MODULE_OWNERS` row. Coordinator action before the next T2.

---

## 1. Repository layout

### 1.1 Where the code lives (D03-1)

```
oc_app/                                  existing repository (Flutter app at the root, frozen; ADR §1)
├── native/                              ← Gradle root of the rewrite. Everything in this section is under here.
├── lib/  test/  android/  linux/  ...   Flutter app (bug fixes only until the switch; removed at switch+1)
├── docs/design/native-rewrite-2026-11-01/   the plan (copied from wt/native-plan when approved)
└── .github/workflows/native-*.yml       native CI jobs, `working-directory: native` (only on explicit request; AGENTS.md CI budget)
```

- Agents keep working as today: one `git worktree` of `oc_app` per feature branch. The native integration branch name
  belongs to the delivery plan.
- `native/` never references files outside itself, except two named readers: `tool/native/kotlin-drift.sh` (S9), which
  reads `../android/app/src/main/kotlin` to report drift, and the one-off `:tools:strings convert` (§21.4), which reads
  `../lib/l10n/*.arb`. The behaviour catalog and wire fixtures are **copied** in (`native/catalog/`, §10.8), never read
  from `wt/native-plan` or `docs/`.
- Three different end dates (audit 01+03 #14), never one "Flutter removal":
  1. **Android packaging stops using Flutter at the switch.** The switch APK is built from `native/` only.
  2. **The Flutter tree leaves `master` at switch+1** in one commit, after the tag `flutter-final-<version>` marks the
     last Flutter commit. Until then the switch release keeps the Flutter hotfix path, the copied-Kotlin drift check
     and the R1 rollback build (05 §9.3, which builds from its own frozen commit anyway).
  3. **The Flutter Linux client keeps its source and release path until native desktop ships** (ADR §12 D14). After
     switch+1 it is built only from the `flutter-final-<version>` tag on a `flutter-linux` maintenance branch (Linux
     fixes only, no Android build), so deleting the tree from `master` does not end it.
  `native/` stays where it is (moving it would rename every path in history and every agent brief for no product
  value).
- The slice-0 commit adds one header line to the root `AGENTS.md`: "The rules below apply to the Flutter tree. Work
  under `native/` follows `native/AGENTS.md`." Agents (and Codex, which merges `AGENTS.md` files from the root down)
  otherwise load the Flutter toolchain, `flutter test` gates and the `lib/ui/kit` rule for native work.

**Alternative (if the owner says no to D03-1):** a new repository `oc-native` with the same tree at its root. Nothing in
this section changes except the leading `native/`.

### 1.2 The `native/` tree

```
native/
├── settings.gradle.kts              module discovery (§3.1)
├── build.gradle.kts                 root: plugin versions with `apply false`, nothing else (§3.2)
├── gradle.properties                shared flags (§4.1); per-host caps live in ~/.gradle/gradle.properties (§4.2)
├── version.properties               versionName / versionCode (base 10000, ADR §9.9)
├── gradle/
│   ├── libs.versions.toml           the one version catalog (§5)
│   └── wrapper/gradle-wrapper.properties   Gradle 9.7.1 + distributionSha256Sum
├── gradlew  gradlew.bat
├── AGENTS.md                        the rewrite's rules (§19.7 verbatim) + pointers to this plan
├── MODULE_OWNERS                    one row per module (§2.4)
├── JETPACK_AHEAD.md                 Android-only androidx APIs used ahead of CMP (ADR §7.6); rows owned by kit/shell owners
├── config/
│   ├── compose-stability.conf       (§19.2)
│   ├── detekt.yml                   (§19.3)
│   ├── kit-capacity.json            hard max widths per kit part (kit section owns the values)
│   ├── copied-kotlin-exemptions.txt copied Flutter-era files exempt from K4/K5/K6/K7/K12/K15; may only shrink (§2.3)
│   └── freeze-baseline.txt          the exact Flutter APK the component freeze compares against (§19.6)
├── catalog/                         behaviors.jsonl copied from the plan's catalog (coordinator-owned; drift-checked, §10.8)
├── contracts/                       contracts/<slice>.md: frozen interfaces, XUi/XActions, Failure categories, rows, what the slice deletes
├── build-logic/                     included build with the convention plugins (§6)
├── core/        common security model domain data db net net-okhttp markdown testing
├── protocol/    oc2 paseo codex                      (a2a, team only if ADR §12 D6 keeps them)
├── engine/      settings diagnostics profiles connection transcript requests delivery composer
│                runtime agents attention schedule media project integrations entry analytics   (team later)
├── ui/          design words routes kit kit-chat kit-content shell testing
├── feature/     onboarding chats chat chat-composer chat-pages servers phone agents project
│                settings usage schedules about search widget-panels               (team, factsbook later)
├── android/     res linux agenthost terminal background schedule widgets media platform migration benchmark
│                termux (migrate-out only under the D7 default; dropped only if the owner picks "drop")   (team later, D6)
├── androidApp/  the APK (§21.1)
├── desktopApp/  kit gallery now, desktop product later (§21.2)
├── quality/     rules graph — architecture tests and gates (§21.3, S22)
├── tools/strings/  one-off ARB → XML converter + drift check (§21.4)
├── third_party/
│   ├── maven/                       vendored artifacts as a local Maven repo (sherpa-onnx AAR)
│   └── SHA256SUMS                   one line per vendored file; checked by :quality
├── tool/                            shell helpers: format-changed.sh, native/kotlin-drift.sh, qa/*.sh
└── e2e/                             Maestro flows (owned by the testing section)
```

Gradle project path = folder path with `/` replaced by `:` (`native/engine/connection` → `:engine:connection`). That is
the only naming rule for modules.

---

## 2. Module anatomy

### 2.1 A KMP logic module (core, protocol, engine)

```
engine/connection/
├── build.gradle.kts                              (§7.2: three lines)
├── README.md                                     ≤ 15 lines: what it owns, public entry points, catalog areas, owner
├── consumer-rules.pro                            optional; R8 rules this module needs in the app (published by the plugin)
└── src/
    ├── commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/
    │   ├── ConnectionRegistry.kt                 public API (explicitApi forces `public`)
    │   ├── ConnectionGraph.kt                    Metro graph extension (§13.4)
    │   ├── ConnectionSession.kt                  public API
    │   ├── state/ConnectionStatus.kt             public immutable state (stable for Compose, §19.2)
    │   └── internal/ReconnectPolicy.kt           `internal` classes: not visible to other modules
    └── commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/
        ├── ConnectionRegistryTest.kt             test names start with catalog ids (§19.4 K12)
        └── ReconnectPolicyTest.kt
```

- No `androidMain` or `desktopMain` folder unless the module is on the ADR §7.2 list. Konsist rule K10 fails otherwise.
- `internal` is the default for everything that is not the module's contract. The public surface of a module is what
  `contracts/<slice>.md` names.

### 2.2 A feature module

```
feature/chats/
├── build.gradle.kts                              plugins { id("oc.kmp.feature") } + engine deps (§7.2)
└── src/
    ├── commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/
    │   ├── ChatsEntries.kt                       the EntryInstaller; the ONLY file that imports androidx.navigation3.runtime
    │   ├── presentation/                         no androidx.compose.* imports (Konsist K2)
    │   │   ├── ChatsUi.kt                        data class ChatsUi + interface ChatsActions
    │   │   ├── ChatsViewModel.kt                 combines engine flows → StateFlow<ChatsUi>; implements ChatsActions
    │   │   └── ChatsMapping.kt                   pure functions engine state → ChatsUi (unit-tested)
    │   └── ui/
    │       ├── ChatsScreen.kt                    @Composable fun ChatsScreen(state: ChatsUi, actions: ChatsActions)
    │       └── ChatsPreviews.kt                  canned ChatsUi values + FakeChatsActions + @Preview functions
    └── commonTest/kotlin/io/github/eslamasabry/oc/feature/chats/
        └── ChatsViewModelTest.kt
```

The feature's other two files live in shared modules, one file each, owned by the feature agent (ADR §4 row 22):

```
ui/routes/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/routes/ChatsRoutes.kt
ui/words/src/commonMain/composeResources/values/strings_chats.xml        (+ values-ar/, values-es/, values-ja/,
                                                                          values-pt/, values-ru/, values-zh/)
```

### 2.3 An Android library module

```
android/linux/
├── build.gradle.kts
├── src/main/AndroidManifest.xml                  permissions/queries only; NO <service>/<receiver>/<activity> (they live in :androidApp)
├── src/main/kotlin/io/github/eslamasabry/opencode_mobile/   files copied from the Flutter app, package unchanged (S9)
├── src/main/kotlin/io/github/eslamasabry/oc/android/linux/ new files, e.g. AndroidRuntimeHost.kt (@ContributesBinding)
├── src/main/jniLibs/arm64-v8a/ libproot.so libproot-loader.so libtalloc.so ...   (x86_64 likewise)
├── src/main/res/                                 resources this module's code reads (S26); strings_os comes from :android:res
├── src/test/kotlin/...                           JVM/Robolectric tests (the module's share of the 46 existing test files, copied)
└── src/androidTest/kotlin/...                    emulator acceptance classes (BuiltinRestoreAcceptance, ...)
```

Copy recipe for Flutter-era Kotlin (S9), in this order:

1. **Sort the files.** 36 of the 168 Flutter-era files import `io.flutter` (`FlutterEngine`, `MethodChannel`,
   `FlutterActivity`: `MainActivity`, `ScheduledPromptRuntime`, `WidgetActionRuntime`, `ManageSpaceActivity`,
   `WidgetOverlayActivity`, …) [R `grep -l '^import io.flutter'`]. They cannot compile in an `:android:*` module and are
   **not copied**: each is rewritten as a `…Body` class (or as the engine-interface implementation that replaces its
   channel), and the module README lists them by name under "Rewritten, not copied". 11 §2–§4 assigns each one.
2. `cp` every other file the module owns from `oc_app/android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/`
   into the module's `src/main/kotlin/io/github/eslamasabry/opencode_mobile/`. Record the source commit in the module
   README (`Copied from <sha>`). Copy the matching JVM tests (46 test files today [R]) into `src/test/`.
3. Replace `import io.github.eslamasabry.opencode_mobile.R` (28 files reference `R` today [R]) with the `R` of the
   module that now owns the resource (S26): the module's own `R` for layouts, drawables and icons it moved into its
   `src/main/res`, and `io.github.eslamasabry.oc.android.res.R` for `strings_os` copy.
4. Add every copied path to `config/copied-kotlin-exemptions.txt`. Copied code still uses `android.util.Log` (6 files),
   `getSharedPreferences(` (18), `Throwable.message` (8), `ACTION_VIEW` (1), and its tests lack catalog-id names; the
   list exempts exactly those files from K4, K5, K6, K7, K12 and K15 (§19.4). The list may only shrink (`:quality:rules`
   checks it against `git` history); each slice brief names the files it takes off the list by refactoring them.
5. Classes that `:androidApp` must call and that were `internal` become `public`. Nothing else changes in the copy
   commit; the copied JVM tests must pass unchanged before any refactor (builtin-linux map risk 2). They need
   `org.json:json` on the test classpath, as the Flutter build has it (`oc.android.library` adds it, §6.1).
6. The 10 copied files that read or write `FlutterSharedPreferences` directly (for example `BackgroundPauseStore`
   turning off `flutter.oc.keepLiveInBackground`, and the schedule CAS store) keep working because the Android
   `KeyValueStore` is backed by the **same** `SharedPreferences` instance, with its `changes` flow fed by an
   `OnSharedPreferenceChangeListener` and no separate snapshot (05 §2.2). Each one moves onto `KeyValueStore`
   (`commit`/`compareAndSet`) in its owner's slice and leaves the exemption list then.

### 2.4 `MODULE_OWNERS`

One row per module, tab-separated: `path  kind  owner  since`. `kind` is `S` (single owner; others propose changes
through them) or `F` (one feature agent end to end), as in the ADR §5.2 table.

```
:core:common            S   core-owner          slice-0
:engine:connection      S   backend-owner       slice-1
:feature:chats          F   (slice brief names the agent)   slice-2
...
```

`:quality:rules`' `ModuleOwnersTest` fails when a Gradle project has no row, or a row names a missing project. Only the
coordinator edits `MODULE_OWNERS`.

---

## 3. Settings and root build files

### 3.1 `native/settings.gradle.kts` (complete)

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads JDK 21 for the Robolectric test JVM when a host lacks it (ADR §8: JDK 17 build, JDK 21 Robolectric).
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // Termux terminal libraries (Apache-2.0) are built by JitPack from release tags; only that group may resolve here.
        exclusiveContent {
            forRepository { maven("https://jitpack.io") }
            filter { includeGroup("com.github.termux.termux-app") }
        }
        // Vendored, hash-pinned artifacts (sherpa-onnx AAR). third_party/SHA256SUMS is checked by :quality:rules.
        // The AAR is vendored with a hand-written minimal POM; artifact() also lets it resolve if the POM is missing.
        exclusiveContent {
            forRepository {
                maven {
                    url = settingsDir.resolve("third_party/maven").toURI()
                    metadataSources { mavenPom(); artifact() }
                }
            }
            filter { includeGroup("com.k2fsa.sherpa.onnx") }
        }
    }
}

rootProject.name = "opencode-mobile-native"

// Module discovery (S2): every direct child folder of these layer folders that holds a build.gradle.kts is a module.
// Adding a module never edits this file. MODULE_OWNERS is the gate (§2.4).
val layerFolders = listOf("core", "protocol", "engine", "ui", "feature", "android", "tools", "quality")
for (layer in layerFolders) {
    val dir = settingsDir.resolve(layer)
    if (!dir.isDirectory) continue
    dir.listFiles()!!
        .filter { it.isDirectory && it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":$layer:${it.name}") }
}
listOf("androidApp", "desktopApp")
    .filter { settingsDir.resolve("$it/build.gradle.kts").isFile }
    .forEach { include(":$it") }
```

Several projects share a leaf name (`:engine:settings`/`:feature:settings`, `:engine:project`/`:feature:project`,
`:engine:agents`/`:feature:agents`, `:engine:media`/`:android:media`, `:engine:schedule`/`:android:schedule`, later the
team modules). That is allowed because every convention plugin sets an explicit Kotlin module name (S23, §6.4), and
`:quality:rules`' `ModuleNameTest` fails if two projects would get the same module name.

### 3.2 `native/build.gradle.kts` (complete)

Every plugin is put on the root classpath once, with `apply false`, so subprojects never repeat versions (Gradle
best practice; the demo did the same [R]).

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.dependency.analysis) // applied at the root only; runs on the VPS (ADR §6.3)
}
```

Nothing else goes in the root build file: no `allprojects {}`, no `subprojects {}` (both break project isolation and
configuration cache hygiene). Shared configuration is the convention plugins' job.

---

## 4. Gradle properties

### 4.1 `native/gradle.properties` (checked in)

```properties
# Safe defaults for a 16 GB PC shared with agents (memory pc-capacity-ceiling). The VPS raises them in ~/.gradle.
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -XX:+UseParallelGC -Dfile.encoding=UTF-8
org.gradle.workers.max=2
org.gradle.caching=true
org.gradle.configuration-cache=true
org.gradle.parallel=true
kotlin.code.style=official
# One JVM for Gradle + Kotlin on the PC (the demo's setting); the VPS may switch to the daemon strategy.
kotlin.compiler.execution.strategy=in-process
android.useAndroidX=true
android.nonTransitiveRClass=true
# Targets: iOS klibs only when a job passes -Poc.ios=true (ADR §7.1).
oc.ios=false
# Warnings fail the build only at T2 (-Poc.werror=true), so an agent's T0 loop is not blocked by a deprecation.
oc.werror=false
# Robolectric forks per module test task (PC 1; the VPS sets 3 in ~/.gradle/gradle.properties).
oc.test.forks=1
```

### 4.2 Per-host overrides (`~/.gradle/gradle.properties`, not in the repo)

`~/.gradle/gradle.properties` wins over the project file [R demo note]. The coordinator writes these once per machine.

| Key | PC (8 CPU / 16 GB, one emulator) | VPS (`$OC_VPS_HOST`, no KVM) |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx2g -XX:MaxMetaspaceSize=768m` | `-Xmx4g -XX:MaxMetaspaceSize=1g` |
| `org.gradle.workers.max` | `2` | unset (all cores) |
| `kotlin.compiler.execution.strategy` | `in-process` | `daemon` with `kotlin.daemon.jvmargs=-Xmx3g` |
| `oc.test.forks` | `1` | `3` |
| `kotlin.incremental.native` | unset | `true` (nightly iOS klib job only) |

Rules (ADR §10.3): one Gradle build at a time on the PC, agents run target-specific tasks only (never `build` or
`assemble` on multi-target modules), full and release builds run on the VPS, midnight–2 am is VPS-only.

---

## 5. Version catalog: `native/gradle/libs.versions.toml` (complete)

Versions are the ADR §8 pins. Every coordinate below was checked to exist on Maven Central or Google Maven on
2026-11-01 [V]. Only the `build-logic` owner edits this file; Kotlin, the Compose compiler, Metro and KSP move together.

```toml
[versions]
# Toolchain (move together; ADR §8)
kotlin = "2.4.21"
agp = "9.3.3"
ksp = "2.3.13"
metro = "1.5.1"
# Compose Multiplatform. Development pins. The 1.12.2 fallback (ADR §11 P1) is DEVELOPMENT-ONLY: its material3
# 1.12.0-alpha03 resolves Jetpack M3 1.5.0-alpha22 on Android, which ADR §12 D12 bars from a release. Release admission
# is checked on the resolved runtime classpath by ReleaseClasspathPolicyTest (§19.6), not on these lines (audit #13).
cmp = "1.13.0-beta01"
cmp-material3 = "1.13.0-beta01"
cmp-adaptive = "1.3.0"
nav3 = "1.2.0"
navevent = "1.1.0"
lifecycle = "2.11.0"
savedstate = "1.4.0"
activity = "1.13.0"
# Kotlin libraries
coroutines = "1.11.0"
serialization = "1.12.0"
collections-immutable = "0.5.2"
datetime = "0.8.0"
kotlinx-io = "0.9.2"
# Network and data
ktor = "3.6.1"
okhttp = "5.5.1"
room3 = "3.0.3"
sqlite = "2.7.1"
datastore = "1.3.0"
# Logging and tracing
kermit = "2.2.0"
tracing = "2.0.5"
# Content
markdown = "0.7.18"
syntaxmp = "0.3.0"
coil = "3.6.3"
telephoto = "0.19.0"
qrose = "1.3.1"
camerax = "1.6.2"
zxing-cpp = "3.1.1"
sherpa-onnx = "2.0.1"
termux = "v0.118.3"
commons-compress = "1.27.1"
# Android platform
androidx-core = "1.19.1"
work = "2.12.0"
# appfunctions: no entry until its post-switch slice (11 §13, 12 N-61); PlatformCapabilities has no flag for it until then
profileinstaller = "1.4.1"
benchmark = "1.5.0"
uiautomator = "2.4.0"
androidx-test-ext = "1.3.0"
androidx-test-runner = "1.7.0"
androidx-compose-test-manifest = "1.13.0-beta02"
# Tests and quality
turbine = "1.2.1"
junit4 = "4.13.2"
org-json = "20180813"       # copied Flutter-era JVM tests need the real org.json (the Flutter build pins this one [R])
robolectric = "4.17"
roborazzi = "1.77.0"
preview-scanner = "0.9.3"
konsist = "0.18.1"
kover = "0.9.11"
dependency-analysis = "3.20.1"
# Desktop loop (optional trial, never required; ADR §4 row 16)
hotreload = "1.2.0"

[libraries]
# --- Kotlin
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "collections-immutable" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kotlinx-io-core = { module = "org.jetbrains.kotlinx:kotlinx-io-core", version.ref = "kotlinx-io" }
# --- DI
metro-runtime = { module = "dev.zacsweers.metro:runtime", version.ref = "metro" }
metrox-viewmodel = { module = "dev.zacsweers.metro:metrox-viewmodel", version.ref = "metro" }
metrox-viewmodel-compose = { module = "dev.zacsweers.metro:metrox-viewmodel-compose", version.ref = "metro" }
# --- Compose Multiplatform (catalog coordinates; the plugin's `compose.*` aliases are deprecated)
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "cmp" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "cmp" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "cmp" }
compose-animation = { module = "org.jetbrains.compose.animation:animation", version.ref = "cmp" }
compose-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "cmp" }
compose-ui-tooling-preview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "cmp" }
compose-ui-test = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "cmp" }
compose-ui-test-junit4 = { module = "org.jetbrains.compose.ui:ui-test-junit4", version.ref = "cmp" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest", version.ref = "androidx-compose-test-manifest" }
# material3 and adaptive: kit modules (material3) and kit + shell (adaptive) ONLY — enforced in build-logic (§6.6)
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "cmp-material3" }
compose-material3-adaptive = { module = "org.jetbrains.compose.material3.adaptive:adaptive", version.ref = "cmp-adaptive" }
compose-material3-adaptive-layout = { module = "org.jetbrains.compose.material3.adaptive:adaptive-layout", version.ref = "cmp-adaptive" }
compose-material3-adaptive-navigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "cmp-adaptive" }
# --- Navigation and lifecycle
navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3" }
navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "nav3" }
navigationevent-compose = { module = "org.jetbrains.androidx.navigationevent:navigationevent-compose", version.ref = "navevent" }
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
savedstate = { module = "org.jetbrains.androidx.savedstate:savedstate", version.ref = "savedstate" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity" }
# --- Network
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
# --- Data
room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
datastore-preferences-core = { module = "androidx.datastore:datastore-preferences-core", version.ref = "datastore" }
# --- Logging and tracing
kermit = { module = "co.touchlab:kermit", version.ref = "kermit" }
androidx-tracing = { module = "androidx.tracing:tracing", version.ref = "tracing" }
# --- Content
jetbrains-markdown = { module = "org.jetbrains:markdown", version.ref = "markdown" }
syntaxmp-tokenizer = { module = "com.gallatinapps.syntaxmp:syntaxmp-tokenizer", version.ref = "syntaxmp" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-svg = { module = "io.coil-kt.coil3:coil-svg", version.ref = "coil" }
coil-gif = { module = "io.coil-kt.coil3:coil-gif", version.ref = "coil" }
# NO coil-network-*: Coil would register a ServiceLoader network fetcher with its own HttpClient (10 M9/S7, 12 R12-4)
telephoto-zoomable = { module = "me.saket.telephoto:zoomable", version.ref = "telephoto" }
telephoto-zoomable-image-coil3 = { module = "me.saket.telephoto:zoomable-image-coil3", version.ref = "telephoto" }
qrose = { module = "io.github.alexzhirkevich:qrose", version.ref = "qrose" }
camerax-core = { module = "androidx.camera:camera-core", version.ref = "camerax" }
camerax-camera2 = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
camerax-compose = { module = "androidx.camera:camera-compose", version.ref = "camerax" }
zxing-cpp = { module = "io.github.zxing-cpp:android", version.ref = "zxing-cpp" }
sherpa-onnx = { module = "com.k2fsa.sherpa.onnx:sherpa-onnx-android", version.ref = "sherpa-onnx" }   # vendored (third_party/maven)
termux-terminal-emulator = { module = "com.github.termux.termux-app:terminal-emulator", version.ref = "termux" }
termux-terminal-view = { module = "com.github.termux.termux-app:terminal-view", version.ref = "termux" }
commons-compress = { module = "org.apache.commons:commons-compress", version.ref = "commons-compress" }
# --- Android platform
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "work" }
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileinstaller" }
# --- Tests
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
org-json = { module = "org.json:json", version.ref = "org-json" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }   # tests only (sealedSubclasses)
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidx-test-ext" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidx-test-runner" }
androidx-uiautomator = { module = "androidx.test.uiautomator:uiautomator", version.ref = "uiautomator" }
androidx-benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
roborazzi-compose-desktop = { module = "io.github.takahirom.roborazzi:roborazzi-compose-desktop", version.ref = "roborazzi" }
roborazzi-preview-scanner-support = { module = "io.github.takahirom.roborazzi:roborazzi-compose-preview-scanner-support", version.ref = "roborazzi" }
composable-preview-scanner-android = { module = "io.github.sergio-sastre.ComposablePreviewScanner:android", version.ref = "preview-scanner" }
konsist = { module = "com.lemonappdev:konsist", version.ref = "konsist" }
# --- Gradle plugin artifacts (compileOnly in build-logic only)
gradle-android = { module = "com.android.tools.build:gradle", version.ref = "agp" }
gradle-kotlin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
gradle-compose-compiler = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }
gradle-compose = { module = "org.jetbrains.compose:compose-gradle-plugin", version.ref = "cmp" }
gradle-metro = { module = "dev.zacsweers.metro:gradle-plugin", version.ref = "metro" }
gradle-ksp = { module = "com.google.devtools.ksp:symbol-processing-gradle-plugin", version.ref = "ksp" }
gradle-roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi-gradle-plugin", version.ref = "roborazzi" }
gradle-room3 = { module = "androidx.room3:room3-gradle-plugin", version.ref = "room3" }

[bundles]
compose-core = ["compose-runtime", "compose-foundation", "compose-ui", "compose-animation", "compose-ui-tooling-preview"]
feature-runtime = ["lifecycle-viewmodel-compose", "lifecycle-runtime-compose", "metrox-viewmodel-compose", "navigation3-runtime"]
common-test = ["kotlinx-coroutines-test", "turbine"]

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
android-kmp-library = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
android-test = { id = "com.android.test", version.ref = "agp" }
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "cmp" }
compose-hotreload = { id = "org.jetbrains.compose.hot-reload", version.ref = "hotreload" }
metro = { id = "dev.zacsweers.metro", version.ref = "metro" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room3 = { id = "androidx.room3", version.ref = "room3" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
dependency-analysis = { id = "com.autonomousapps.dependency-analysis", version.ref = "dependency-analysis" }
# Convention plugins from build-logic (no version)
oc-kmp-library = { id = "oc.kmp.library" }
oc-kmp-compose = { id = "oc.kmp.compose" }
oc-kmp-feature = { id = "oc.kmp.feature" }
oc-jvm-library = { id = "oc.jvm.library" }
oc-android-library = { id = "oc.android.library" }
oc-android-application = { id = "oc.android.application" }
oc-desktop-application = { id = "oc.desktop.application" }
```

Notes for the catalog owner:

- `androidx.navigation3:navigation3-runtime` is published by Google as a multiplatform artifact (it has `-desktop` and
  iOS variants on Google Maven [V]); JetBrains republishes only `navigation3-ui`. Both resolve to 1.2.0.
- `ui-test-manifest` has no CMP republish (404 [V]); the Android-only `androidx.compose.ui:ui-test-manifest` goes in
  `:ui:testing` `androidMain` only. Its version follows the Compose UI that CMP 1.13.0-beta01 maps to; Gradle resolves
  the highest, so the pin is a floor [U, P7 checks the resolved version].
- Detekt 1.23.8 runs as the CLI from `tool/qa/detekt.sh` (as in the Flutter repo [R]), so it has no catalog entry and no
  Gradle plugin (§19.3).
- ktfmt 0.64 is a CLI jar fetched by `tool/format-changed.sh` with a pinned SHA-256 (§19.1); no catalog entry.
- There is deliberately no `androidx.appcompat` entry: per-app language is done without it (S21, §12.3).
- No `coil-network-*` artifact may be declared anywhere; `ExternalRules` bans the group pattern and section 10's
  `AppImageLoaderTest` asserts the release `ImageLoader` has no network fetcher.
- `okhttp-mockwebserver` (`mockwebserver3`, exists for 5.5.1 [V]) is for `:core:net-okhttp`'s pinning tests only;
  add `com.squareup.okhttp3:okhttp-tls:5.5.1` [V] next to it when a test needs a self-signed leaf.

---

## 6. Convention plugins (`native/build-logic`)

### 6.1 Plugin ids and what each one applies

| Id | Class | Applies | Used by |
|---|---|---|---|
| `oc.kmp.library` | `OcKmpLibraryPlugin` | `kotlin.multiplatform`, `com.android.kotlin.multiplatform.library`, `kotlin.plugin.serialization`, `dev.zacsweers.metro`; targets `android` + `jvm("desktop")` (+ iOS with `-Poc.ios=true`); namespace from path; explicit API by layer; opt-ins; common deps; test deps; host tests (S15); consumer keep rules; module rules (§6.6) | core, protocol, engine, `:ui:design`, `:ui:routes` |
| `oc.kmp.compose` | `OcKmpComposePlugin` | `oc.kmp.library` + `kotlin.plugin.compose` + `org.jetbrains.compose`; `compose-stability.conf`; CMP resources config (S18); compose deps; `kotlinx-coroutines-swing` in `desktopMain`; `compose-ui-test` in `commonTest`; the Roborazzi Gradle plugin in `:ui:kit*` and `:ui:shell` (the modules with goldens) | `:ui:words`, `:ui:kit*`, `:ui:shell`, `:ui:testing` |
| `oc.kmp.feature` | `OcKmpFeaturePlugin` | `oc.kmp.compose` + the standard feature dependencies (kit, design, words, routes, `core:domain`, `core:model`, `core:common`, lifecycle, metrox-viewmodel-compose, navigation3-runtime) + `:ui:testing` in tests | every `:feature:*` |
| `oc.jvm.library` | `OcJvmLibraryPlugin` | `kotlin("jvm")`, JDK 17 toolchain, `kotlin("test")`, explicit module name (S23), module rules | `:core:net-okhttp`, `:tools:strings`, `:quality:rules`, `:quality:graph` |
| `oc.android.library` | `OcAndroidLibraryPlugin` | `com.android.library` (AGP 9 built-in Kotlin; never `kotlin.android`), Metro, SDK levels, explicit module name (S23), `testOptions.unitTests.isIncludeAndroidResources = true`, test deps (junit4, robolectric, `org-json`, coroutines-test, turbine), Robolectric test tasks on a JDK 21 launcher, lint, module rules | every `:android:*` except `:android:benchmark` (`:android:res` uses it too; it has no Kotlin) |
| `oc.android.application` | `OcAndroidApplicationPlugin` | `com.android.application`, compose compiler, Metro, baseline profile; id, flavours, version, splits, packaging, R8, signing hook (§21.1) | `:androidApp` only |
| `oc.desktop.application` | `OcDesktopApplicationPlugin` | `kotlin("jvm")`, compose compiler, `org.jetbrains.compose`, desktop application DSL | `:desktopApp` only |

`:android:benchmark` applies `com.android.test` + `androidx.baselineprofile` directly in its own build file (one
module; no plugin needed).

### 6.2 `build-logic/settings.gradle.kts` and `build-logic/convention/build.gradle.kts`

```kotlin
// build-logic/settings.gradle.kts
dependencyResolutionManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } }
}
rootProject.name = "build-logic"
include(":convention")
```

```kotlin
// build-logic/convention/build.gradle.kts
plugins { `kotlin-dsl` }

// Gradle and build-logic run on JDK 21: Metro 1.5.1's Gradle plugin is published for JVM 21 and fails to resolve from
// a JVM 17 build [V slice-0 P1 D1]. Every module still compiles with toolchain 17 / JvmTarget 17 (§6.4).
kotlin { jvmToolchain(21) }

dependencies {
    compileOnly(libs.gradle.android)
    compileOnly(libs.gradle.kotlin)
    compileOnly(libs.gradle.compose.compiler)
    compileOnly(libs.gradle.compose)
    compileOnly(libs.gradle.metro)
    compileOnly(libs.gradle.ksp)
    compileOnly(libs.gradle.roborazzi)
    compileOnly(libs.gradle.room3)
    testImplementation(kotlin("test"))          // ModuleRulesTest: the §6.1 table as test vectors
}

gradlePlugin {
    plugins {
        register("ocKmpLibrary") { id = "oc.kmp.library"; implementationClass = "oc.buildlogic.OcKmpLibraryPlugin" }
        register("ocKmpCompose") { id = "oc.kmp.compose"; implementationClass = "oc.buildlogic.OcKmpComposePlugin" }
        register("ocKmpFeature") { id = "oc.kmp.feature"; implementationClass = "oc.buildlogic.OcKmpFeaturePlugin" }
        register("ocJvmLibrary") { id = "oc.jvm.library"; implementationClass = "oc.buildlogic.OcJvmLibraryPlugin" }
        register("ocAndroidLibrary") { id = "oc.android.library"; implementationClass = "oc.buildlogic.OcAndroidLibraryPlugin" }
        register("ocAndroidApplication") { id = "oc.android.application"; implementationClass = "oc.buildlogic.OcAndroidApplicationPlugin" }
        register("ocDesktopApplication") { id = "oc.desktop.application"; implementationClass = "oc.buildlogic.OcDesktopApplicationPlugin" }
    }
}
```

Files in `build-logic/convention/src/main/kotlin/oc/buildlogic/`:

```
Sdk.kt                     compile/min/target SDK constants
Namespaces.kt              path → namespace (§6.3)
Catalog.kt                 Project.libs, lib("alias"), bundle("alias"), version("alias")
ModuleRules.kt             ADR §6.1 + §6.2 as data (§6.6) — coordinator-owned
ExternalRules.kt           which modules may use material3, Ktor, OkHttp, Room, Termux, CameraX, sherpa (§6.6)
EnforceRules.kt            Project.enforceModuleRules(): wires both rule sets into dependency declaration
OcKmpLibraryPlugin.kt      §6.4
OcKmpComposePlugin.kt      §6.5
OcKmpFeaturePlugin.kt
OcJvmLibraryPlugin.kt
OcAndroidLibraryPlugin.kt
OcAndroidApplicationPlugin.kt   §21.1
OcDesktopApplicationPlugin.kt   §21.2
```

### 6.3 Shared helpers

```kotlin
// Sdk.kt
internal object Sdk {
    const val COMPILE = 37      // API 37, minor level 1: Compose 1.13 AARs require 37.1 (checkAarMetadata fails on 37.0)
    const val COMPILE_MINOR = 1 //   [V slice-0 P1: `platforms;android-37.1` r1; audit 01+03 #2]
    const val TARGET = 36       // targetSdk 37 is its own slice after the switch (ADR §8)
    const val MIN = 26
}

// Namespaces.kt
internal object Namespaces {
    const val ROOT = "io.github.eslamasabry.oc"
    const val APP = "io.github.eslamasabry.opencode_mobile"   // the app's namespace = application id (ADR §5.2)

    /** ":ui:kit-chat" → "io.github.eslamasabry.oc.ui.kitchat". Hyphens drop out: a namespace is a Java package. */
    fun of(path: String): String = when (path) {
        ":androidApp" -> APP
        ":desktopApp" -> "$ROOT.desktop"
        else -> ROOT + "." + path.removePrefix(":").split(':').joinToString(".") { it.replace("-", "") }
    }
}

// Catalog.kt
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")
internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()
internal fun VersionCatalog.bundle(alias: String) = findBundle(alias).get()
```

The Kotlin root package of every KMP, JVM and new Android-module file equals the module namespace. Exceptions, and only
these: `:androidApp` OS-visible classes keep `io.github.eslamasabry.opencode_mobile` (ADR §5.2), and Kotlin copied
from the Flutter app keeps that package inside its new `:android:*` module (S9).

### 6.4 `OcKmpLibraryPlugin` (complete)

```kotlin
package oc.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class OcKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        pluginManager.apply("dev.zacsweers.metro")

        val layer = ModuleRules.layerOf(path)
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)
            if (layer.explicitApi) explicitApi()

            // The AGP-KMP `android {}` block. In a build script it is `kotlin { android { … } }`; in a binary plugin the
            // target is an extension OF THE KOTLIN EXTENSION. KotlinMultiplatformExtension is not statically
            // ExtensionAware, so a bare `extensions` here would silently resolve to the Project's container (the
            // outer `with(target)` receiver) and fail. Cast first [U P1: confirm on AGP 9.3.3; fallback P-03-1].
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = Namespaces.of(path)
                compileSdk { version = release(Sdk.COMPILE) { minorApiLevel = Sdk.COMPILE_MINOR } }   // Sdk.ocCompileSdk() [V P1]
                minSdk = Sdk.MIN
                if (layer.hasPlatformSourceSets) withHostTest { isIncludeAndroidResources = true }   // S15
                if (file("consumer-rules.pro").isFile) {
                    optimization { consumerKeepRules.apply { publish = true; file("consumer-rules.pro") } }
                }
            }
            jvm("desktop")
            if (providers.gradleProperty("oc.ios").orNull == "true") {
                iosArm64()
                iosSimulatorArm64()
            }

            compilerOptions {
                // Both are opt-in markers on Kotlin 2.4.21's stdlib today [U: drop each once stable]:
                optIn.addAll("kotlin.time.ExperimentalTime", "kotlin.concurrent.atomics.ExperimentalAtomicApi")
                allWarningsAsErrors.set(providers.gradleProperty("oc.werror").map(String::toBoolean).orElse(false))
                if (path == ":core:common") freeCompilerArgs.add("-Xexpect-actual-classes")  // ADR §7.3: only here
            }
            // S23: unique META-INF/<name>.kotlin_module per project, whatever the leaf name [U P1: exact property path
            // for KMP compilations on Kotlin 2.4.21; fallback `-module-name` in freeCompilerArgs per compilation].
            targets.configureEach {
                compilations.configureEach {
                    compileTaskProvider.configure { compilerOptions.moduleName.set(Namespaces.of(path)) }
                }
            }

            sourceSets.commonMain.dependencies {
                api(libs.lib("kotlinx-coroutines-core"))            // api: StateFlow and ImmutableList/Map are in
                implementation(libs.lib("kotlinx-serialization-json"))
                api(libs.lib("kotlinx-collections-immutable"))      // public engine and gateway signatures [V P1 D5]
            }
            sourceSets.commonTest.dependencies {
                implementation(kotlin("test"))
                implementation(libs.bundle("common-test"))
                if (path != ":core:testing") implementation(project(":core:testing"))
            }
        }

        tasks.withType<Test>().configureEach {
            maxParallelForks = providers.gradleProperty("oc.test.forks").get().toInt()
            // Catalog ids in test names contain spaces; JUnit XML keeps them, the ratchet greps them (§19.6).
        }
        // Robolectric 4.17 at SDK 36/37 needs a Java 21 test JVM (ADR §8, testing-perf §3.2); the build stays on 17.
        // The foojay resolver in settings downloads JDK 21 where a host lacks it.
        if (layer.hasPlatformSourceSets) {
            val toolchains = extensions.getByType<JavaToolchainService>()
            tasks.withType<Test>().matching { it.name.contains("AndroidHostTest") }.configureEach {
                javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
                // Robolectric 4.17 on JDK 21 fails without these ("cannot access jdk.internal.access.SharedSecrets");
                // AGP's unit-test tasks add them, AGP-KMP host tests do not [V slice-0 P2 §7.7, P7 D12].
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                        "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.lang=ALL-UNNAMED")
            }
        }
        enforceModuleRules()
    }
}
```

`OcAndroidLibraryPlugin` and `OcAndroidApplicationPlugin` set the same Java 21 launcher on every `test*UnitTest` task,
and `OcJvmLibraryPlugin` sets `kotlin { compilerOptions { moduleName.set(Namespaces.of(path)) } }`.

`ModuleRules.layerOf(path)` returns a `Layer` with three flags used above:

| Flag | True for |
|---|---|
| `explicitApi` | `:core:*`, `:protocol:*`, `:engine:*`, `:ui:design`, `:ui:routes`, `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content` |
| `hasPlatformSourceSets` | the ADR §7.2 list: `:core:common`, `:core:data`, `:core:net`, `:core:db`, `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content`, `:ui:shell`, `:ui:testing` |
| `isTestOnly` | `:core:testing`, `:ui:testing` (may appear only in test configurations of other modules) |

### 6.5 `OcKmpComposePlugin` and `OcKmpFeaturePlugin` (complete)

```kotlin
class OcKmpComposePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("oc.kmp.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("org.jetbrains.compose")

        extensions.configure<ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFiles.add(isolated.rootProject.projectDirectory.file("config/compose-stability.conf"))
            if (providers.gradleProperty("oc.composeReports").orNull == "true") {     // used by P4 and perf work only
                reportsDestination.set(layout.buildDirectory.dir("compose/reports"))
                metricsDestination.set(layout.buildDirectory.dir("compose/metrics"))
            }
            // Do NOT set StrongSkipping/IntrinsicRemember flags: they are DeprecationLevel.ERROR on Kotlin 2.4 [V research].
        }

        // S18: only these modules own composeResources and a public Res class.
        val ownsResources = path in setOf(":ui:words", ":ui:kit", ":ui:kit-chat", ":ui:kit-content")
        extensions.configure<ComposeExtension> {
            extensions.configure<ResourcesExtension> {
                publicResClass = ownsResources
                packageOfResClass = "${Namespaces.of(path)}.res"
                generateResClass = if (ownsResources) ResourcesExtension.ResourceClassGeneration.Always
                                   else ResourcesExtension.ResourceClassGeneration.Never
            }
        }
        if (ownsResources) {
            extensions.configure<KotlinMultiplatformExtension> {
                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                    androidResources { enable = true }
                }
            }
        }
        // Goldens (T1 runs verifyRoborazzi*): the plugin goes where the screenshot tests live.
        if (path.startsWith(":ui:kit") || path == ":ui:shell") pluginManager.apply("io.github.takahirom.roborazzi")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                implementation(libs.bundle("compose-core"))
                implementation(libs.lib("compose-resources"))
            }
            sourceSets.getByName("desktopMain").dependencies { implementation(libs.lib("kotlinx-coroutines-swing")) }
            sourceSets.commonTest.dependencies { implementation(libs.lib("compose-ui-test")) }
        }
    }
}

class OcKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("oc.kmp.compose")
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.commonMain.dependencies {
                listOf(":ui:kit", ":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
                    .forEach { implementation(project(it)) }
                implementation(libs.bundle("feature-runtime"))
            }
            sourceSets.commonTest.dependencies { implementation(project(":ui:testing")) }
        }
    }
}
```

A feature's own `build.gradle.kts` adds only what differs: the engines it reads and, if needed, `:ui:kit-chat` or
`:ui:kit-content` (§7.2).

### 6.6 Module rules as code (`ModuleRules.kt`, `ExternalRules.kt`, `EnforceRules.kt`)

The ADR §6.1 table and §6.2 order, executable. A violation fails **configuration**, before any compile, with a message
that tells the agent what to do.

```kotlin
internal object ModuleRules {
    /** ADR §6.2. An engine may use only engines listed above it. Only the coordinator edits this list. */
    val engineOrder = listOf(
        "settings", "analytics", "diagnostics", "profiles", "connection", "transcript", "requests", "delivery", "composer",
        "runtime", "agents", "attention", "schedule", "media", "project", "integrations", "entry", "team",
    )
    private val core = setOf(":core:common", ":core:security", ":core:model", ":core:domain",
        ":core:data", ":core:db", ":core:net", ":core:markdown")
    private val kits = setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content")
    private val testOnly = setOf(":core:testing", ":ui:testing")

    /** null = allowed. [inTest] is true for any configuration whose name contains "test" (case-insensitive). */
    fun violation(from: String, to: String, inTest: Boolean): String? {
        if (to in testOnly) return if (inTest) null else "$to is test-only; use it from a test source set."
        val ok = when {
            from == ":androidApp" || from == ":quality:graph" -> true
            from == ":quality:rules" -> false                                 // S22: no project dependencies
            from == ":desktopApp" -> !to.startsWith(":android:") && to != ":androidApp"
            from == ":core:common" || from == ":core:net-okhttp" || from.startsWith(":tools:") -> false
            from == ":core:security" || from == ":core:model" -> to == ":core:common"
            from == ":core:domain" -> to in setOf(":core:common", ":core:model", ":core:security")
            from == ":core:data" -> to in setOf(":core:common", ":core:security", ":core:model")
            from == ":core:db" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:data")
            from == ":core:net" -> to in setOf(":core:common", ":core:security", ":core:model", ":core:net-okhttp")
            from == ":core:markdown" -> to in setOf(":core:common", ":core:model")
            from == ":core:testing" -> to in core || to == ":core:net-okhttp"
            from.startsWith(":protocol:") ->
                to in setOf(":core:common", ":core:model", ":core:domain", ":core:net", ":core:security")
            from.startsWith(":engine:") -> to in core || (to.startsWith(":engine:") && engineIsAbove(to, from))
            from == ":ui:design" -> to == ":core:common"
            from == ":ui:words" || from == ":ui:routes" -> to in setOf(":core:common", ":core:model")
            from == ":ui:kit" -> to in setOf(":ui:design", ":ui:words", ":core:common", ":core:security", ":core:model")
            from == ":ui:kit-chat" -> to in setOf(":ui:kit", ":ui:design", ":ui:words", ":core:markdown",
                ":core:model", ":core:common", ":core:security")
            from == ":ui:kit-content" -> to in setOf(":ui:kit", ":ui:design", ":ui:words", ":core:model", ":core:common")
            from == ":ui:shell" -> to in kits || to in setOf(":ui:design", ":ui:words", ":ui:routes", ":engine:entry",
                ":engine:attention", ":engine:settings", ":core:domain", ":core:model", ":core:common")
            from == ":ui:testing" -> to in kits || to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:common", ":core:model")
            from.startsWith(":feature:") -> to in kits || to.startsWith(":engine:") ||
                to in setOf(":ui:design", ":ui:words", ":ui:routes", ":core:domain", ":core:model", ":core:common")
            from == ":android:res" -> false                                   // resources only (S26)
            from == ":android:migration" ->
                to in setOf(":core:common", ":core:security", ":core:data", ":core:model", ":core:db", ":android:res")
            from.startsWith(":android:") ->
                to.startsWith(":core:") || to.startsWith(":engine:") || to == ":ui:words" || to == ":ui:design" ||
                    to == ":android:res" || to in androidToAndroid[from].orEmpty()
            else -> false
        }
        return if (ok) null else "Module rule: $from may not depend on $to (ADR §6.1/§6.2). " +
            "Move the shared piece down (into a lower engine or :core:domain) or ask the coordinator."
    }

    private fun engineIsAbove(to: String, from: String): Boolean =
        engineOrder.indexOf(to.substringAfterLast(':')) in 0 until engineOrder.indexOf(from.substringAfterLast(':'))

    /** The only :android:* → :android:* edges (11 CR11-0, once the coordinator amends ADR §6.1). */
    private val androidToAndroid = mapOf(
        ":android:agenthost" to setOf(":android:linux"),
        ":android:terminal" to setOf(":android:linux"),
    )

    data class Layer(val explicitApi: Boolean, val hasPlatformSourceSets: Boolean, val isTestOnly: Boolean)

    private val platformSourceSets = setOf(":core:common", ":core:data", ":core:net", ":core:db",
        ":ui:kit", ":ui:kit-chat", ":ui:kit-content", ":ui:shell", ":ui:testing")      // ADR §7.2

    fun layerOf(path: String): Layer = Layer(
        explicitApi = path.startsWith(":core:") || path.startsWith(":protocol:") || path.startsWith(":engine:") ||
            path in setOf(":ui:design", ":ui:routes") || path in kits,
        hasPlatformSourceSets = path in platformSourceSets,
        isTestOnly = path in testOnly,
    )
}
```

```kotlin
internal object ExternalRules {
    /** group (or group:artifact) → module paths that may declare it. Test configurations are exempt for *-mock/*-test. */
    val allowed: Map<String, (String) -> Boolean> = mapOf(
        "org.jetbrains.compose.material3:material3" to { p -> p in setOf(":ui:kit", ":ui:kit-chat", ":ui:kit-content") },
        "org.jetbrains.compose.material3.adaptive" to { p -> p.startsWith(":ui:kit") || p == ":ui:shell" },
        "io.ktor" to { p -> p == ":core:net" || p.startsWith(":protocol:") || p == ":core:testing" ||
            p == ":androidApp" || p == ":desktopApp" },
        "com.squareup.okhttp3" to { p -> p == ":core:net-okhttp" },
        "androidx.room3" to { p -> p == ":core:db" },
        "androidx.sqlite" to { p -> p == ":core:db" },
        "co.touchlab" to { p -> p == ":core:common" },                       // Kermit stays behind AppLog (§18)
        "com.github.termux.termux-app" to { p -> p == ":android:terminal" || p == ":ui:kit-content" },
        "androidx.camera" to { p -> p == ":ui:kit-content" || p == ":android:media" },
        "io.github.zxing-cpp" to { p -> p == ":ui:kit-content" || p == ":android:media" },
        "com.k2fsa.sherpa.onnx" to { p -> p == ":android:media" },
        "org.jetbrains:markdown" to { p -> p == ":core:markdown" },
        "io.coil-kt.coil3:coil-network-core" to { _ -> false },               // no module may fetch through Coil (10 M9)
        "io.coil-kt.coil3:coil-network-ktor3" to { _ -> false },
        "io.coil-kt.coil3:coil-network-okhttp" to { _ -> false },
        "androidx.appcompat" to { _ -> false },                               // S21
    )
}
```

```kotlin
// EnforceRules.kt
internal fun Project.enforceModuleRules() {
    val from = path
    configurations.configureEach {
        val inTest = name.contains("test", ignoreCase = true)
        val configName = name
        dependencies.withType(ProjectDependency::class.java).configureEach {
            ModuleRules.violation(from, path, inTest)?.let { throw GradleException("$it [configuration $configName]") }
        }
        dependencies.withType(ExternalModuleDependency::class.java).configureEach {
            val key = ExternalRules.allowed.keys.firstOrNull { k -> "$group:$name" == k || group == k } ?: return@configureEach
            val isTestTool = inTest && (name.endsWith("-mock") || name.contains("test"))
            if (!isTestTool && !ExternalRules.allowed.getValue(key)(from)) {
                throw GradleException("Module rule: $from may not declare $group:$name (ADR §6.1, §19.4). " +
                    "Use the kit / :core:net / the owning module instead.")
            }
        }
    }
}
```

`ProjectDependency.path` exists since Gradle 8.11 (the old `dependencyProject` is gone in Gradle 9) [R]. The rules
only see **declared** dependencies; transitive leaks are stopped by `implementation` (never `api`) for these
libraries and by the Konsist import rules (§19.4). `build-logic`'s own `ModuleRulesTest` holds one vector per §6.1
row (allowed and denied) and runs in `./gradlew -p build-logic test`. It also holds the edges that §11.2 depends on, so
the engine table and the order cannot drift apart again:

| Vector | Expected | Why |
|---|---|---|
| `:engine:composer` → `:engine:media` | denied | attachment limits and the MIME gate live in `:core:domain` (§10.4), not in media |
| `:engine:integrations` → `:engine:attention` | allowed | integrations pushes usage and quota facts down into attention's sinks (§11.2) |
| `:engine:attention` → `:engine:integrations` | denied | attention never pulls from integrations |
| `:engine:profiles` → `:engine:agents`, `:engine:integrations` | denied | sign-in reset reaches them through the `SignInResetParticipant` multibinding (§13.5) |
| `:engine:agents` → `:engine:connection` | allowed | agents opens the phone-agents backend through the registry (§13.4) |
| `:core:security` → `:core:model` | denied | `ServerOrigin` is in `:core:common` (S24) |
| `:ui:kit-content` → `:core:security` | denied | `ApprovedLink` is in `:core:common` (S7); the web-preview slot takes no policy type (§12.2) |
| `:android:background` → `:android:res` | allowed | S26 |
| `:engine:diagnostics` → `:engine:analytics` | allowed | diagnostics reports crash and freeze categories (S27) |
| `:engine:analytics` → `:engine:diagnostics`, `:core:net` | denied | analytics depends only on `:core:common`; HTTP arrives through `AnalyticsTransport` (S27, 18 §5) |
| a `:core:net-okhttp` file importing `io.github.eslamasabry.oc.*` | denied | the module has no project dependencies (04 §3.3 split table) |
| `:quality:rules` → any project | denied | S22 |

---

## 7. Module catalogue

### 7.1 Every module: plugin, namespace, platform folders

Namespaces follow §6.3. "a/d" = has `androidMain`/`desktopMain`. "Host" = Android host tests on (S15). Owners and
start slices are in the ADR §5.2 table and `MODULE_OWNERS`; they are not repeated here.

| Module | Plugin | Namespace = root package | a/d | Host | External deps beyond the plugin's |
|---|---|---|---|---|---|
| `:core:common` | kmp.library | `io.github.eslamasabry.oc.core.common` | yes (+ `iosMain` with `-Poc.ios=true`) | yes | Kermit; `androidx-tracing` (a); `kotlinx-coroutines-android` (a); `-swing` (d); kotlinx-datetime |
| `:core:security` | kmp.library | `….oc.core.security` | no | no | — |
| `:core:model` | kmp.library | `….oc.core.model` | no | no | kotlinx-datetime |
| `:core:domain` | kmp.library | `….oc.core.domain` | no | no | — |
| `:core:data` | kmp.library | `….oc.core.data` | yes | yes | kotlinx-io-core; `androidx-core-ktx` (a); `datastore-preferences-core` (d, desktop phase) |
| `:core:db` | kmp.library + `ksp` + `androidx.room3` | `….oc.core.db` | yes | yes | room3-runtime, sqlite-bundled; `kspAndroid`/`kspDesktop` room3-compiler |
| `:core:net` | kmp.library | `….oc.core.net` | yes | yes | ktor-client-core, -websockets, kotlinx-io-core (`Downloader`); ktor-client-okhttp + `:core:net-okhttp` (a, d) |
| `:core:net-okhttp` | jvm.library | `….oc.core.netokhttp` | — | — | okhttp |
| `:core:markdown` | kmp.library | `….oc.core.markdown` | no | no | jetbrains-markdown |
| `:core:testing` | kmp.library | `….oc.core.testing` | no | no | `api`: coroutines-test, turbine, ktor-client-mock |
| `:protocol:oc2` / `paseo` / `codex` | kmp.library | `….oc.protocol.oc2` / `.paseo` / `.codex` | no | no | ktor-client-core (+ websockets for paseo, codex, PTY) |
| `:engine:<name>` (17, `analytics` included) | kmp.library | `….oc.engine.<name>` | no | no | as the engine needs (none by default; `analytics` depends only on `:core:common`) |
| `:ui:design` | kmp.library | `….oc.ui.design` | no | no | — |
| `:ui:words` | kmp.compose | `….oc.ui.words` (`Res` in `….oc.ui.words.res`) | no | no | — |
| `:ui:routes` | kmp.library | `….oc.ui.routes` | no | no | navigation3-runtime, savedstate (`SavedStateConfiguration`); `kotlin-reflect` in `desktopTest` only (the `sealedSubclasses` round-trip test) |
| `:ui:kit` | kmp.compose | `….oc.ui.kit` | yes | yes | material3; coil-compose; navigationevent-compose; `androidx-core-ktx` (a) |
| `:ui:kit-chat` | kmp.compose | `….oc.ui.kitchat` | yes | yes | material3 |
| `:ui:kit-content` | kmp.compose | `….oc.ui.kitcontent` | yes | yes | material3, syntaxmp, qrose, telephoto, coil-svg/-gif; (a) termux-terminal-view, camerax-compose/-camera2/-lifecycle, zxing-cpp |
| `:ui:shell` | kmp.compose | `….oc.ui.shell` | yes | yes | navigation3-ui, navigationevent-compose, lifecycle-viewmodel-navigation3, material3-adaptive-navigation3, metrox-viewmodel-compose, savedstate |
| `:ui:testing` | kmp.compose | `….oc.ui.testing` | yes | yes | `api`: compose-ui-test; roborazzi-compose-desktop (d); compose-ui-test-junit4, robolectric, roborazzi-compose, ui-test-manifest (a) |
| `:feature:<name>` (15) | kmp.feature | `….oc.feature.<name>` (`chat-composer` → `chatcomposer`) | no | no | engines it reads |
| `:android:<name>` (10 incl. `termux`) | android.library | `….oc.android.<name>` | — | — | see §12 per service; every one may add `:android:res` |
| `:android:res` | android.library | `….oc.android.res` | — | — | none: generated `values*/strings_os.xml` only (S26, §20.6) |
| `:android:benchmark` | `com.android.test` | `….oc.android.benchmark` | — | — | benchmark-macro-junit4, uiautomator |
| `:androidApp` | android.application | `io.github.eslamasabry.opencode_mobile` | — | — | activity-compose, work-runtime, profileinstaller |
| `:desktopApp` | desktop.application | `….oc.desktop` | — | — | compose desktop |
| `:quality:rules` | jvm.library | `….oc.quality.rules` | — | — | konsist (test); no project dependencies (S22) |
| `:quality:graph` | jvm.library | `….oc.quality.graph` | — | — | `testImplementation(project(":desktopApp"))` |
| `:tools:strings` | jvm.library | `….oc.tools.strings` | — | — | kotlinx-serialization-json |

(`….oc` = `io.github.eslamasabry.oc`.)

Under the ADR D7 default ("migrate-out only"), `:android:termux` **ships at the switch** (slice 5, 11 §2.1): it holds
the migrate-out bridge and `TermuxResultService`'s body, and has a `MODULE_OWNERS` row and its freeze entries
(`TermuxResultService`, the `RUN_COMMAND` permission, the `com.termux` `<queries>` entry, the `files/migrations/<jobId>/`
archives). It is dropped only if the owner picks "drop" for D7.

### 7.2 Example `build.gradle.kts` files (copy, then change only the marked lines)

```kotlin
// engine/connection/build.gradle.kts
plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:domain"))     // ← the core and engine modules this engine uses
        implementation(project(":core:data"))
        implementation(project(":core:net"))
        implementation(project(":core:security"))
        implementation(project(":core:model"))
        implementation(project(":core:common"))
        implementation(project(":engine:profiles")) // ← must be ABOVE connection in ADR §6.2, or configuration fails
    }
}
```

```kotlin
// feature/chats/build.gradle.kts
plugins { id("oc.kmp.feature") }

kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":engine:connection"))   // ← engines this feature reads (public API only)
        implementation(project(":engine:agents"))       //   ChatFeed (merged list across live connections)
        implementation(project(":engine:attention"))
    }
}
```

```kotlin
// protocol/oc2/build.gradle.kts
plugins { id("oc.kmp.library") }

kotlin {
    sourceSets.commonMain.dependencies {
        listOf(":core:common", ":core:model", ":core:domain", ":core:net", ":core:security").forEach { implementation(project(it)) }
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.websockets)
    }
    sourceSets.commonTest.dependencies { implementation(libs.ktor.client.mock) }
}
```

```kotlin
// android/linux/build.gradle.kts
plugins { id("oc.android.library") }
// jniLibs ship from src/main/jniLibs; extraction to nativeLibraryDir is set in :androidApp (useLegacyPackaging, §21.1)

dependencies {
    implementation(project(":engine:runtime"))   // implements RuntimeHost
    implementation(project(":core:data"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.commons.compress)        // Ubuntu Base tarball (as in the Flutter app [R])
    implementation(libs.androidx.core.ktx)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

```kotlin
// core/db/build.gradle.kts  (starts at switch+1, or earlier by the ADR §9.8 trigger)
plugins {
    id("oc.kmp.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}
room3 { schemaDirectory("$projectDir/schemas") }   // schemas are committed; :quality parses them (§19.6)
kotlin {
    sourceSets.commonMain.dependencies {
        implementation(project(":core:data")); implementation(project(":core:model")); implementation(project(":core:common"))
        implementation(libs.room3.runtime)
        implementation(libs.sqlite.bundled)
    }
}
dependencies {
    add("kspAndroid", libs.room3.compiler)   // KSP 2.3.10+ deprecates plain ksp(...) in KMP [R network-data §6.2]
    add("kspDesktop", libs.room3.compiler)
    if (providers.gradleProperty("oc.ios").orNull == "true") {   // one entry per enabled iOS target (audit 01+03 #10)
        add("kspIosArm64", libs.room3.compiler)
        add("kspIosSimulatorArm64", libs.room3.compiler)
    }
}
```

---

## 8. Source sets, targets and the tasks agents run

### 8.1 Hierarchy

The default Kotlin hierarchy template applies (no custom `dependsOn`):

```
commonMain ─┬─ androidMain          (AGP-KMP android target)
            ├─ desktopMain          (jvm("desktop"))
            └─ iosMain ─┬─ iosArm64Main            only with -Poc.ios=true
                        └─ iosSimulatorArm64Main
commonTest ─┬─ androidHostTest      only in S15 modules (withHostTest)
            ├─ desktopTest          ← where every commonTest runs by default
            └─ iosTest               nightly klib compile only; never run on Linux
```

There is no android+jvm shared source set (unsupported by Kotlin [R ADR §7.4]); JVM code shared by both goes in an
`oc.jvm.library` module with no project dependencies (`:core:net-okhttp` today).

### 8.2 What runs where (ADR §7.2, made concrete)

| Source set | Put here | Never here |
|---|---|---|
| `commonMain` | all business rules, state machines, ViewModels, screens, kit parts, protocol clients | `android.*`, `java.*`, `javax.*`, `org.json`, `String.format`, `System.*`, `Thread`, `R.*`, `LocalContext` |
| `androidMain` (S15 modules only) | `actual`s (`:core:common`), prefs adoption + Keystore (`:core:data`), OkHttp engine wiring (`:core:net`), Room driver (`:core:db`), `AndroidKitPlatform` + `JETPACK_AHEAD` items (kit, shell), Robolectric helpers (`:ui:testing`) | business rules |
| `desktopMain` | the same modules' desktop bindings; until the desktop phase only in-memory stores or `Unavailable*` stubs | product work before the desktop phase |
| `commonTest` | catalog-row tests: `kotlin.test`, coroutines-test, Turbine, fakes from `:core:testing` | Android types |
| `androidHostTest` | Robolectric tests of the module's `androidMain` (prefs XML parsing, Keystore fakes, kit Android seams, Roborazzi) | logic already covered by `commonTest` |
| Android library `src/test` | Robolectric/JVM tests for `:android:*` | — |
| Android library `src/androidTest` | emulator acceptance (`BuiltinRestoreAcceptance`, migration with a real Keystore) | — |

### 8.3 Tasks per tier (exact names; never `build` or `assemble` on a multi-target module)

| Tier (ADR §10.3) | Module kind | Task |
|---|---|---|
| T0 one row | any KMP | `./gradlew :engine:delivery:desktopTest --tests "*B-STATE-042*"` |
| T0 | KMP with `androidMain` | `./gradlew :core:data:testAndroidHostTest --tests "*B-MIGR-*"` [U P1: exact host-test task name on AGP 9.3.3] |
| T0 | Android library | `./gradlew :android:linux:testDebugUnitTest --tests "*NativeRuntimeOwnership*"` |
| T0 compile check | feature | `./gradlew :feature:chats:compileKotlinDesktop` |
| T1 (VPS) | touched modules | `./gradlew :<m>:desktopTest :<m>:testAndroidHostTest :<m>:verifyRoborazzi<Variant>` (§22.3 P-03-4 names the variant) |
| T0 rules | feature or any module | `./gradlew :quality:rules:test --tests "*KitOnlyRuleTest*"` (no project dependencies, so it never compiles the app; S22) |
| T2 (VPS) | everything | `./gradlew desktopTest testAndroidHostTest testReleaseUnitTest :quality:rules:test :quality:graph:test :androidApp:lintStableRelease :androidApp:assembleStableRelease :androidApp:analyzeStableReleaseR8Config` + `tool/qa/detekt.sh` + size gate + component freeze [U P1: flavoured task names] |
| T2 nightly (VPS) | iOS klibs | `./gradlew -Poc.ios=true compileKotlinIosArm64 compileKotlinIosSimulatorArm64` (core, protocol, engine first; UI modules once P1 says they compile) |
| T3 (PC + emulator) | app | `./gradlew :androidApp:connectedStableReleaseAndroidTest` (testBuildType release, as today) + Maestro |
| T4 (PC + emulator) | perf | `./gradlew :android:benchmark:connectedBenchmarkAndroidTest`, `:androidApp:generateStableReleaseBaselineProfile` |

`--tests` filters match catalog ids because test functions are named `` `B-STATE-042 dispatch marker blocks auto resend`() ``.

---

## 9. Packages and naming

### 9.1 Packages inside a module

| Package (under the module root) | Holds | Visibility |
|---|---|---|
| (root) | the module's public API: interfaces, engine classes, entry points | `public` |
| `.state` | immutable state types published in `StateFlow`s (stable for Compose via `compose-stability.conf`) | `public` |
| `.internal` | implementation details | `internal` |
| `.presentation` (features) | `XUi`, `XActions`, `XViewModel`, mapping functions; **no `androidx.compose.*` imports** | `public` for the screen, else `internal` |
| `.ui` (features) | `XScreen`, private composables, previews | `internal` except `XScreen` |
| `.fake` (`:core:testing`, `:ui:testing`, and every engine's `commonMain`) | fakes and fixtures; an engine's `fake` package holds one `Fake…` per public engine interface (S19) | `public`; imported only from test source sets and previews (Konsist K21); R8 strips it from release |

### 9.2 Name suffixes (one meaning each)

| Suffix | Meaning | Example |
|---|---|---|
| `…Gateway` | protocol-neutral server API (`:core:domain`) | `ServerGateway`, `PromptGateway` |
| `…Engine` / plain noun | app- or connection-scoped logic holder exposing `StateFlow`; a public interface, implemented by an `internal` class named `Default…` (S19) | `TranscriptEngine`, `PromptDelivery`, `ConnectionRegistry` |
| `…Store` | persistence seam (key-value or file) | `KeyValueStore`, `OfflineQueueStore` |
| `…Host` | platform service the engine drives (Android/desktop implementation) | `RuntimeHost`, `AgentHost`, `ScheduleHost` |
| `…Policy` | pure decision function or object | `ExternalLinkPolicy`, `MotionPolicy` |
| `…State` / `…Event` / `…Effect` / `reduce` | a state machine | `TurnState`, `TurnEvent`, `TurnEffect` |
| `XUi` / `XActions` / `XViewModel` / `XScreen` / `XEntries` | one screen's contract, logic, view, navigation entry | `ChatsUi`, `ChatsActions`, … |
| `XRoute` | navigation key (`:ui:routes`) | `ChatRoute` |
| `Fake…` | test double with recorded calls | `FakeServerGateway` |
| `Unavailable…` | binding that reports its capability off (desktop/iOS) | `UnavailableRuntimeHost` |
| `Android…` / `Desktop…` | platform implementation | `AndroidRuntimeHost`, `DesktopKitPlatform` |
| `Kit…` | kit part or kit seam | `KitRow`, `KitPlatform` |

Kotlin style is the official Kotlin coding conventions (`kotlin.code.style=official`), formatted by ktfmt kotlinlang
style (§19.1). Test functions use backtick names that begin with the catalog id.

---

## 10. Core seams (Kotlin signatures)

These are the shapes slice 0 freezes (ADR §10.3 "freeze before fan-out"). Method lists marked "…" are completed by the
owning module's `contracts/<slice>.md`, using the named Flutter file as the source of truth. Signatures use Kotlin
2.4 features only where stable (explicit backing fields, context parameters are allowed; neither is required).
**Where §0.3 names another owner for a type, this section gives only its home and the constraints 03 adds; the shape
comes from the owner section.**

### 10.1 `:core:common`

```kotlin
package io.github.eslamasabry.oc.core.common

// ---- Scopes (Metro). AppScope is Metro's built-in dev.zacsweers.metro.AppScope [V: in metro runtime 1.5.1].
public abstract class ConnectionScope private constructor()

/** Process-lifetime scope: SupervisorJob + default dispatcher + a handler that logs (never crashes the process). */
public class AppCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope
/** One per live connection; a child of AppCoroutineScope. Cancelled by ConnectionSession.close(). */
public class ConnectionCoroutineScope(override val coroutineContext: CoroutineContext) : CoroutineScope

public fun supervisedContext(parent: Job?, dispatcher: CoroutineDispatcher, name: String): CoroutineContext =
    SupervisorJob(parent) + dispatcher + CoroutineName(name) +
        CoroutineExceptionHandler { _, e -> AppLog.tagged(name).e(e) { "uncaught failure in $name" } }

// ---- Results
public sealed interface Outcome<out T> {
    public data class Ok<out T>(val value: T) : Outcome<T>
    public data class Failed(val failure: Failure) : Outcome<Nothing>
}
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) { is Outcome.Ok -> Outcome.Ok(transform(value)); is Outcome.Failed -> this }
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) { is Outcome.Ok -> transform(value); is Outcome.Failed -> this }
public inline fun <T> Outcome<T>.onOk(block: (T) -> Unit): Outcome<T> = also { if (it is Outcome.Ok) block(it.value) }
public inline fun <T> Outcome<T>.onFailed(block: (Failure) -> Unit): Outcome<T> = also { if (it is Outcome.Failed) block(it.failure) }
public fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value
public fun Outcome<*>.failureOrNull(): Failure? = (this as? Outcome.Failed)?.failure

/**
 * The only sanctioned way to call code that may throw (Konsist K3 bans runCatching around suspend calls).
 * Cancellation is rethrown, never turned into a Failure. [classify] is the boundary's translator (§16.1).
 */
public suspend inline fun <T> attempt(classify: (Throwable) -> Failure, block: () -> T): Outcome<T> =
    try { Outcome.Ok(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Throwable) { Outcome.Failed(classify(e)) }

// ---- Failure: ONE flat, closed hierarchy (03 owns it; §0.3). Every Flutter ProductFailureCategory
// (lib/domain/product_failure.dart [R]) maps to exactly one subtype. Other sections add FIELDS or subtypes through
// their change requests, applied here before the freeze: 04 §3.8 (transport detail fields, Incompatible), 05 §2.2/§4.2
// (StorageRefused, SecretUnavailable), 10 §4.1 C10-1 (Media), 11 §5.5 CR11-1 (Runtime). No section adds a nested
// sub-hierarchy, so `when` over Failure stays one flat exhaustive list.
public sealed interface Failure {
    /** Technical text for the Details fold ONLY (ADR §9.6). Null only for app-authored refusals. */
    public val details: Redacted?

    // App-authored
    public data class Refused(val reason: RefusalReason, override val details: Redacted? = null) : Failure      // `words`
    public data class Unavailable(val feature: FeatureId, override val details: Redacted? = null) : Failure    // capability default
    // Transport and server (detail fields and the input table: 04 §3.8)
    public data class Network(val why: NetworkWhy = NetworkWhy.Unknown, override val details: Redacted?) : Failure
    public data class TimedOut(override val details: Redacted?) : Failure          // network timeouts, 408, and LOCAL waits
                                                                                   // (FGS acknowledgement, gate)
    public data class Certificate(val why: CertificateWhy, override val details: Redacted?) : Failure
    public data class SignIn(val why: SignInWhy, override val details: Redacted?) : Failure
    public data class NotFound(val tag: WireTag? = null, val requestId: RequestId? = null, override val details: Redacted?) : Failure
    public data class Conflict(val tag: WireTag? = null, val requestId: RequestId? = null, override val details: Redacted?) : Failure
    public data class Busy(val kind: BusyKind = BusyKind.Overloaded, val tag: WireTag? = null, override val details: Redacted?) : Failure
    public data class Server(val status: Int?, val tag: WireTag? = null, override val details: Redacted?) : Failure
    public data class Rejected(val status: Int?, val tag: WireTag? = null, val extras: WireExtras = WireExtras.None,
                               override val details: Redacted?) : Failure
    public data class Incompatible(val kind: IncompatibleKind, override val details: Redacted?) : Failure
    public data class ComputerDidNotAnswer(override val details: Redacted?) : Failure                         // `computer` (Paseo host)
    public data class UnreadableReply(override val details: Redacted?) : Failure                              // decode failed / over cap
    // Storage (05)
    public data class StorageRefused(override val details: Redacted?) : Failure    // prefs commit refused, disk, I/O
    public data class SecretUnavailable(override val details: Redacted?) : Failure // Keystore; "enter the password again"
    // Media (10) and the built-in Linux (11)
    public data class Media(val problem: MediaProblem, override val details: Redacted?) : Failure
    public data class Runtime(val problem: RuntimeProblem, override val details: Redacted?) : Failure
    // Platform and project
    public data class Device(override val details: Redacted?) : Failure                                       // platform API refused
    public data class Folder(val problem: FolderProblem, override val details: Redacted?) : Failure          // 4 folder* categories
    public data class ProjectGit(override val details: Redacted?) : Failure
    public data class StagedRevert(override val details: Redacted?) : Failure
    public data class Termux(override val details: Redacted?) : Failure                                       // kept while D7 = migrate-out
    public data class Unexpected(override val details: Redacted?) : Failure                                   // a bug path, still plain words
    public data class Unknown(override val details: Redacted?) : Failure                                      // the generic connectivity line
}
public enum class FolderProblem { NotInstalled, Missing, Denied, Linked }
// NetworkWhy, CertificateWhy, SignInWhy, BusyKind, IncompatibleKind, WireTag, WireExtras: values in 04 §3.8.
// MediaProblem (10 §4.1) and RuntimeProblem (11 §5.5) are append-only enums in this package.
/** App-authored refusal reasons. Append-only; each has exactly one sentence in :ui:words (exhaustive when). */
public enum class RefusalReason {
    ProfileClosing, ProfileDeleted, StaleScope, ConsentMissing, CleartextNotConfirmed, PasswordMissing,
    AlreadyAnswered, DispatchUncertain, OfflineQueueFull, MigrationPending, NotOnThisPhone,
    OriginChanged, CredentialsInUrl, PinNeedsHttps, HostNotAllowedForBackend,   // 04 §3.8
    BackgroundLimit, NotificationsBlocked, PhoneLocked,          // 11 CR11-2
    UndoUnresolved,                                              // 09 §4 (staged undo blocks send)
}
/** Names a ServerCapabilities/PlatformCapabilities flag exactly as spelled in the Kotlin data class. */
@JvmInline public value class FeatureId(public val name: String)

// ---- ApprovedLink (S7): the type lives here so kit-content, features and :android:platform can hold it; only
// ExternalLinkPolicy (:core:security) opts in to the constructor (Konsist K13b).
@RequiresOptIn("Only ExternalLinkPolicy in :core:security constructs ApprovedLink.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR)
public annotation class LinkPolicyApi
public enum class LinkOrigin { AppAuthored, ServerContent, Markdown, AgentCard, Terminal, Notification }
public class ApprovedLink @LinkPolicyApi constructor(public val uri: String, public val origin: LinkOrigin)

// ---- Server origin (S24): here, not in :core:model, because :core:security's address rules return it.
@Serializable public data class ServerOrigin(val scheme: String, val host: String, val port: Int)

// ---- Redacted (S6)
@RequiresOptIn("Only the Redactor in :core:security constructs Redacted text.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CONSTRUCTOR, AnnotationTarget.FUNCTION)
public annotation class RedactorApi

@JvmInline
public value class Redacted @RedactorApi constructor(public val text: String) {
    public companion object {
        /** Exception class name + optional HTTP status only. A class name cannot carry a secret; a message can. */
        @OptIn(RedactorApi::class)
        public fun shape(error: Throwable?, status: Int? = null): Redacted =
            Redacted(listOfNotNull(error?.let { it::class.simpleName }, status?.let { "HTTP $it" }).joinToString(" · ").ifEmpty { "no message" })
    }
}

// ---- Dispatchers and time (ADR §9.5). Dispatchers.* and Clock.System appear ONLY in this module (Konsist K4).
public interface AppDispatchers {
    public val main: CoroutineDispatcher       // Main.immediate (Android Looper; desktop Swing EDT)
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher
    /** One serial lane per engine or per-profile store: default.limitedParallelism(1, name). */
    public fun serial(name: String): CoroutineDispatcher
}
internal expect val platformIoDispatcher: CoroutineDispatcher   // Dispatchers.IO on JVM/Android/Native (iosMain actual too; S25)
internal object PlatformDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = platformIoDispatcher
    override fun serial(name: String): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, name)
}
@ContributesTo(AppScope::class)
public interface CommonBindings {
    @Provides @SingleIn(AppScope::class) public fun dispatchers(): AppDispatchers = PlatformDispatchers
    @Provides public fun clock(): Clock = Clock.System              // kotlin.time.Clock
    @Provides @SingleIn(AppScope::class) public fun appScope(d: AppDispatchers): AppCoroutineScope =
        AppCoroutineScope(supervisedContext(null, d.default, "app"))
}

// ---- Identifiers (S24): here, because Failure names RequestId and :core:common sees nothing else. Shapes and the one
// id rule `SafeId` (regexes) are 07 §2.2.1. Serializable for route keys and storage.
@Serializable @JvmInline public value class ProfileId(public val raw: String)
@Serializable @JvmInline public value class SessionId(public val raw: String)
@Serializable @JvmInline public value class MessageId(public val raw: String)
@Serializable @JvmInline public value class PartId(public val raw: String)
@Serializable @JvmInline public value class RequestId(public val raw: String)
@Serializable @JvmInline public value class ScheduleId(public val raw: String)
public enum class ConversationBackend { Main, PhoneAgents }                     // 07 §2.2.1
@Serializable public data class ConversationRef(                                // shape owned by 07 §2.2.1
    val profileId: ProfileId,                   // always a SAVED profile id (phone agents: the owning phone profile)
    val sessionId: SessionId,
    val backend: ConversationBackend = ConversationBackend.Main,
)
/** The connection id of a phone-agents backend: "<owner>.agents" (a valid SafeId; 07 §2.2.1, 04 §12.1). */
public fun ProfileId.agentsBackend(): ProfileId = ProfileId("$raw.agents")

// ---- Byte primitives (S25; shapes merged from 05 §2.5, 10 §4.2 C10-2 and 12 W-list). The only expects besides the
// IO dispatcher. android/desktop actuals: java.security.MessageDigest / Mac / SecureRandom, java.util.zip.Inflater.
// iosMain actuals: CommonCrypto CC_SHA256 / CCHmac, SecRandomCopyBytes, zlib inflate (platform libs; no new library).
public expect class Sha256() {
    public fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size)
    public fun digest(): ByteArray                       // resets the instance
}
public expect fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
/** Cryptographically secure. kotlin.random.Random is never used for tokens, passwords or nonces (16 K-SEC-12). */
public expect fun secureRandomBytes(count: Int): ByteArray
/** Raw DEFLATE (no zlib header). Null on corrupt input or when the output would exceed [maxOutputBytes]. */
public expect fun inflateRaw(input: ByteArray, maxOutputBytes: Int): ByteArray?
public fun ByteArray.toLowerHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
public fun sha256Hex(text: String): String = Sha256().apply { update(text.encodeToByteArray()) }.digest().toLowerHex()

// ---- Build facts (KMP modules have no BuildConfig; ADR §5.2 :androidApp) and launch mode (crash-loop guard)
public enum class Channel { Stable, Preview, Desktop }
public data class AppBuildInfo(val versionName: String, val versionCode: Int, val channel: Channel, val debuggable: Boolean) {
    /** Factories live with the app modules as extensions: `AppBuildInfo.Companion.fromBuildConfig(...)` in
     *  :androidApp, `AppBuildInfo.Companion.desktop()` in :desktopApp. */
    public companion object
}
public enum class LaunchMode { Normal, SafeMode }

// ---- Locale-aware formatting for screens AND OS surfaces (notifications, widgets, tiles). kotlinx-datetime has no
// localized formatting and commonMain may not use java.*, so this is the one seam; it lives here so engines, the kit,
// features and :android:* can all see it. Implemented over android.icu (:android:platform) and java.text
// (:desktopApp); Latin digits (nu-latn); respects the 12/24-hour setting. 06's KitPlatform.formatMoment/formatNumber
// are backed by the same instance, which :androidApp hands to the AndroidKitPlatform factory.
public interface Formats {
    public fun timeOfDay(epochMs: Long, localeTag: String): String
    public fun date(epochMs: Long, localeTag: String, style: DateStyle): String
    public fun relativeAge(epochMs: Long, nowMs: Long, localeTag: String): String
    public fun byteSize(bytes: Long, localeTag: String): String     // decimal MB (10 §17)
    public fun number(value: Double, localeTag: String, style: NumberStyle): String
}

// ---- Platform view handle (ADR §7.3 item 3): only kit androidMain casts `native`.
public interface PlatformViewHandle { public val native: Any }

// ---- Wire JSON (network-data §4.3): one instance; never echoes input into exceptions.
public val WireJson: Json = Json {
    ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = false
    exceptionsWithDebugInfo = false
}
// + TolerantListSerializer<T>, LenientDoubleSerializer, and the open-enum pattern `@JvmInline value class Phase(val raw: String)`.
```

`AppLog`, `LogSink`, `LogRing` and `Trace` are in §18. `LogRing` (the in-memory 128-entry / 256 KiB report ring) is a
plain `LogSink` in `:core:common`, so `OcApplication` can build it in slice 0 before `:engine:diagnostics` exists;
diagnostics' `ProblemReportBuilder` reads it later.

Slice 0 ships an `iosMain` for `:core:common` with every actual above (Konsist K10 allows `iosMain` in this module
only), so acceptance check 6 compiles. An iOS actual that is not ready may `throw NotImplementedError("iOS phase")`;
each such stub is listed in `native/docs/ios-gaps.md`, and no iOS binary ships before the iOS phase.

### 10.2 `:core:security`

```kotlin
package io.github.eslamasabry.oc.core.security

/** The one redactor (port of KitRedact + the redaction profiles). Registered secrets plus token/URL-credential patterns.
 *  Created in OcApplication.onCreate step 2, BEFORE the graph, and handed to the graph factory (§13.2); not @Inject. */
public class Redactor {
    public fun registerSecret(value: String)          // called on EVERY secret read and write, before any log line (ADR §9.7)
    public fun forget(value: String)
    public fun redact(text: String): String            // known secrets → "•••", Authorization headers, ?token=, user:pass@
    @OptIn(RedactorApi::class)
    public fun details(text: String): Redacted = Redacted(redact(text).take(12_000))   // 12,000 = Flutter sanitize limit [R]
    public fun details(error: Throwable, status: Int? = null): Redacted               // shape + redacted, length-capped message
}

/** The root log sink; AppLog.install accepts only a RedactingLogSink (§18.1). */
public class RedactingWriter(private val redactor: Redactor, private val delegates: List<LogSink>) : RedactingLogSink

// ---- Links (AGENTS.md invariant; S7). ApprovedLink and LinkOrigin are declared in :core:common; only this
// module opts in to @LinkPolicyApi.
public sealed interface LinkDecision {
    public data class Open(val link: ApprovedLink) : LinkDecision
    public data class AskFirst(val link: ApprovedLink, val shownHost: String) : LinkDecision   // kit confirm sheet first
    public data class Refuse(val reason: LinkRefusal) : LinkDecision
}
public enum class LinkRefusal { BadScheme, CredentialsInUrl, TooLong, NotAUrl, IntentScheme }
@Inject public class ExternalLinkPolicy { public fun check(raw: String, origin: LinkOrigin): LinkDecision }
/** Implemented once in :android:platform (Intent.ACTION_VIEW lives only there and in the kit handler, Konsist K6).
 *  Declared here; the kit's ExternalLinkUriHandler (:ui:kit may see :core:security) is the only UI caller. */
public fun interface ExternalLinkOpener { public fun open(link: ApprovedLink): Outcome<Unit> }

// ---- Addresses and cleartext consent (PROTOCOLS-01/02; Android NSC cannot express 100.64.0.0/10, so code decides)
public enum class HostClass { Loopback, PrivateLan, Tailnet, LinkLocal, Public, Invalid }
public object AddressRules {
    public fun classify(host: String): HostClass       // RFC1918, 100.64.0.0/10, *.ts.net, ::1, fe80::/10 …
    public fun normalize(input: String): Outcome<ServerOrigin>   // ServerOrigin is :core:common (S24)
}
public sealed interface CleartextConsent {             // three-state reads: missing ≠ refused
    public data object Unknown : CleartextConsent
    public data class Granted(val origin: ServerOrigin, val atMs: Long) : CleartextConsent
    public data object Refused : CleartextConsent
}
/** A consent or pin applies only while it names the profile's CURRENT origin; an address edit voids both
 *  (Flutter stores `oc.cleartextOk.<id>` as the exact origin and `oc.certificatePin.<id>` as {origin, sha256}). */
public fun CleartextConsent.appliesTo(origin: ServerOrigin): Boolean = this is CleartextConsent.Granted && this.origin == origin
```

### 10.3 `:core:model`

Plain, immutable, `@Serializable` where stored or routed. All collections are `ImmutableList`/`ImmutableMap`. No
behaviour beyond pure helpers. The full list ports `lib/domain/server_gateway/value_types*.dart` (PROTOCOLS-39).

```kotlin
// Ids, ConversationRef and ServerOrigin are in :core:common (§10.1, S24).

// ---- Backends. Wire names openCode|paseo|codex; `openCode` reads as OpenCode2 (04 §11.0). OpenCode 1 is
//      recognition-only through `flavor` (ADR §5.2 :protocol:oc2).
public enum class BackendKind { OpenCode2, Paseo, Codex }

/**
 * Every field Flutter persists in the `oc.profiles` row (05 §2.5, `profiles/models.dart:301`), so the schedule and
 * widget fingerprints (05 `ProfileFingerprintV1`) recompute byte-identically and no write drops AI Team config.
 * The JSON codec is 05's `ServerProfileJson` (stored key order, lenient read); this class is its parsed form.
 */
@Serializable public data class ServerProfile(
    val id: ProfileId,
    val name: String,
    val baseUrl: String,                               // as stored; `origin` is derived, never stored separately
    val backend: BackendKind,
    val username: String,                              // "" when none; the password lives in SecretStore only
    val agentDirectory: String,                        // wire name `codexDirectory`; emitted only for Paseo/Codex
    val flavor: ServerFlavor,                          // wire `flavor` (v1 | v2)
    val requiresOpenCode2Migration: Boolean,
    val openCode2HistoryPending: Boolean,
    val serverVersion: String?,
    val orchestration: JsonObject?,                    // AI Team config, opaque and kept byte-for-byte (ADR D6)
    val transientTransport: Boolean = false,           // never stored (Flutter's in-memory phone-agents profile)
)
// The origin is derived by whoever needs it with AddressRules.normalize(baseUrl) (:core:security; :core:model may not
// depend on it), cached by :engine:profiles next to the profile list.
public enum class ServerFlavor { V1, V2 }

/** Per-profile trust, stored under its own keys exactly as Flutter does; each half names the origin it was given for. */
@Serializable public data class CertificatePin(val origin: ServerOrigin, val leafSha256: String)   // oc.certificatePin.<id>
@Serializable public data class CleartextOk(val origin: ServerOrigin, val atMs: Long?)            // oc.cleartextOk.<id>
public data class ProfileTrust(val pin: CertificatePin?, val cleartext: CleartextOk?) {
    /** Only the halves that name [origin] apply; an address edit makes both inert, and :engine:profiles deletes them
     *  in the same commit as the new baseUrl. */
    public fun forOrigin(origin: ServerOrigin): ProfileTrust =
        ProfileTrust(pin?.takeIf { it.origin == origin }, cleartext?.takeIf { it.origin == origin })
}
/** Transient, never stored: the Flutter "enter it again" flags, set when SecretStore cannot return a secret. */
public data class ProfileReentry(val password: Boolean, val agentToken: Boolean)
// `oc.tailscale.<id>` stays a separate per-profile key owned by :engine:profiles (05 Appendix A), not a profile field.

// StreamStatus (sealed: Connecting, Connected, Reconnecting, WaitingForNetwork, Suspended, Stopped(failure)),
// StreamSignal<T>, NetworkStatus/NetworkState: shapes owned by 04 §6.3–§6.4. After every Connected the consumer
// reconciles by refetch, never replay (AGENTS.md OpenCode 2 rule).
public data class ServerPage<T>(val items: ImmutableList<T>, val nextCursor: String?)
// Session, MessageInfo, MessageWithParts, sealed Part (Text, Reasoning, Tool, File, Agent, Notice, Unknown),
// ToolState (Pending | Running | Completed | Error), Tokens, ModelRef, ProviderInfo, AgentInfo, FileNode, FileDiff,
// PermissionRequest, FormSpec (the one form model), CardSpec (oc-ui card v1), ScheduleSpec, …  — ported 1:1 (04 owns
// their exact shapes).
```

Writes of `oc.profiles` **merge over the raw stored object** (`ServerProfileJson.write(raw, profile)` replaces only
the keys it knows and keeps any unknown key), because `WireJson` ignores unknown keys on read. The slice-0 `:core:model`
gate includes 05's fingerprint vector test: a profile parsed from each Dart vector gives the Dart
`profileFingerprint` exactly (05 §2.5 `DartJsonTest`). `managedOnPhone` is not a field: `:engine:profiles` derives it
from the `oc.builtinServerOwner` pointer.

### 10.4 `:core:domain` — gateways, capabilities, shared platform services

**Gateway members and capabilities are owned by 04 §11** (§0.3): `GatewayFactory { backend; probe(target);
connect(spec: GatewaySpec): Outcome<GatewayPair> }`, `GatewayPair`, `GatewaySpec` (profile, transport permit, resolved
credentials, location, scope, options), `ServerGateway` with its facets (a 1:1 port of `interfaces.dart`; every call
`suspend` returning `Outcome`; hot `events` with `start()`), `ServerOperationsGateway` with default bodies returning
`Failure.Unavailable(FeatureId(flag))` (`unavailable(...)`), the optional facets, `ServerCapabilities` (85 flags, Dart
names and defaults) and its snapshot tests. 03 fixes only where they live and how features may use them:

- All of it is in `:core:domain` `commonMain`; OC2-only types that Dart leaked into the interfaces move to `:core:model`.
- Only **engines** test for optional facets (`gateway.facet<F>()`); features gate on `ServerCapabilities` /
  `PlatformCapabilities` (ADR §6, rule 10 of §19.7).
- The prompt request names delivery with `DeliveryMode { Steer, Queue }` (not `PromptDelivery`, which is the
  `:engine:delivery` engine, 09 §4). The composer offers Steer wherever it exists (owner rule "steer, never queue").

```kotlin
package io.github.eslamasabry.oc.core.domain

public enum class DeliveryMode { Steer, Queue }

/** What this build can do on this platform (B-XPLAT-012/013). Bound once per app graph. */
public data class PlatformCapabilities(
    val builtinLinux: Boolean, val termuxMigration: Boolean, val voice: Boolean, val voiceConversation: Boolean,
    val readAloud: Boolean, val localPdf: Boolean, val promptPhotos: Boolean, val backgroundService: Boolean,
    val notifications: Boolean, val homeWidgets: Boolean, val launchShortcuts: Boolean, val quickSettingsTiles: Boolean,
    val qrPairing: Boolean, val tailscaleHandoff: Boolean, val usbHostBridge: Boolean, val inAppUpdater: Boolean,
    val desktopReleaseCheck: Boolean, val wifiFileShare: Boolean, val sharedStorageProjects: Boolean,
    // appFunctions: added with its post-switch slice (11 §13); no flag until something backs it.
) {
    /** Factories are extensions in the app modules: `PlatformCapabilities.Companion.android(sdkInt)` in :androidApp,
     *  `PlatformCapabilities.Companion.desktop()` in :desktopApp (all Android-only flags false). */
    public companion object
}

// ---- Platform services shared by several engines or by features (ADR §4 row 27). Shapes of PickedFile, PhotoTarget,
// DocumentTarget, SharedFile: 10 §4.3 (C10-4).
public interface ActivityResults {                         // impl: :android:platform, attached to the RESUMED host activity
    public suspend fun pickImages(limit: Int): Outcome<ImmutableList<PickedFile>>
    public suspend fun takePhoto(target: PhotoTarget): Outcome<PickedFile?>      // null = person cancelled
    public suspend fun openDocument(mimeTypes: List<String>): Outcome<PickedFile?>
    public suspend fun createDocument(suggestedName: String, mime: String): Outcome<DocumentTarget?>
    public suspend fun openDocumentTree(): Outcome<TreeTarget?>
    /** System RecognizerIntent (the widget voice panel); null = cancelled. Unavailable when no recognizer exists. */
    public suspend fun recognizeSpeech(languageTag: String): Outcome<String?>
}
public enum class AppPermission {
    Microphone, Camera, Notifications, ExactAlarms, IgnoreBatteryOptimizations, InstallPackages,
    /** MANAGE_EXTERNAL_STORAGE on API 30+ (a settings round-trip; request() waits up to 10 min for the person to come
     *  back), legacy storage on 26–29. Asked only when a shared-storage project opens (11 CR11-4, ANDROID-PLATFORM-46). */
    AllFilesAccess,
    LocalNetwork,                                          // the targetSdk-37 slice (10 C10-4)
}
public enum class PermissionStatus { Granted, Denied, PermanentlyDenied, NotApplicable }
public interface PermissionGate {
    public suspend fun status(permission: AppPermission): PermissionStatus
    public suspend fun request(permission: AppPermission): PermissionStatus   // never called from composition
    public fun openSettings(permission: AppPermission)
}

// ---- Foreground work. Kinds, services, rows and start points are 11 §9 (owner); this is the interface engines use.
public enum class ForegroundKind {
    ReplyWatch, LiveMode, SignIn,  // dataSync: ALL THREE SHARE ONE 6 h / 24 h background budget (Android 15+), reset by
                                   // opening the app. SignIn (11 CR11-3) is allowed during a durable pause; LiveMode is not.
    PhoneServer, Setup,            // specialUse
    WidgetAction,                  // shortService on API 34+, dataSync on 26–33 (chosen by API level in :android:background)
}
public enum class ForegroundOrigin { PersonAsked, AutoStart }   // AutoStart never begins a dataSync kind from boot (11 §9.2 rule 4)
public enum class PauseReason { BatteryRestricted, UserStopped, AppUpdated, DeviceRestarted, TimeLimit, Interrupted }
public data class PauseReceipt(val reason: PauseReason, val atMs: Long)   // durable; blocks automatic restart until cleared
public interface ForegroundWork {
    public val active: StateFlow<ImmutableSet<ForegroundKind>>
    /** The durable pause receipt (copied BackgroundPauseStore); null = not paused. */
    public val pause: StateFlow<PauseReceipt?>
    /** Starts or joins the hosting service and waits up to 5 s for its startForeground acknowledgement. Typed failures
     *  (11 §9.2): refused by Android → Refused(BackgroundLimit); receipt not saved → StorageRefused; no acknowledgement
     *  → TimedOut. Never throws ForegroundServiceStartNotAllowedException. */
    public suspend fun begin(kind: ForegroundKind, origin: ForegroundOrigin): Outcome<ForegroundTicket>
    public suspend fun end(ticket: ForegroundTicket)
}
// :android:background invariant (11 §9.2 rule 2): notification 4747 is ONE row shared by StatusOwners {live, server,
// reply, signin}; it is removed only when the last owner leaves. Every FGS shell handles both onTimeout overloads (§13.6).

public interface DeviceFacts {                             // + thermalHeadroom, battery, appVisible (11 CR11-5)
    // network: moved to NetworkStatus/NetworkState in :core:model (04 §6.4), which :core:net can see
    public val thermal: StateFlow<ThermalLevel>
    public val powerSave: StateFlow<Boolean>
    public val totalRamBytes: Long                      // voice packs gate on total RAM, never memoryClass [R memory]
}
public interface ShareService {
    public fun shareText(text: String, subject: String?): Outcome<Unit>     // excludes this app from the chooser
    public fun shareFile(file: SharedFile): Outcome<Unit>
}
/** Keep-running guidance (ANDROID-PLATFORM-44; shape 11 §8.4, CR11-9): maker facts + the per-maker pages. */
public interface KeepAliveSettings {
    public suspend fun facts(): KeepAliveFacts                       // manufacturer, brand, sdk, exempt, known autostart page
    public fun open(page: KeepAlivePage): Boolean                    // Battery | Autostart | AppDetails; false = nothing opened
}
/** Tailscale handoff (11 §14.6, CR11-9). */
public interface TailscaleApp {
    public fun check(): TailscaleState                               // Installed | Missing | Unavailable
    public fun open(): Boolean
}
/** Named system settings pages (11 §16, CR11-9). */
public interface OsSettingsPages { public fun open(page: OsSettingsPage): Boolean }
/** Wi-Fi file share host (rules in :engine:media, 10 §13): one file, bound to the Wi-Fi IPv4 only, 15-min life. */
public interface WifiShareHost {
    public fun wifiAddress(): String?                                // null = not on Wi-Fi
    public suspend fun serve(file: SharedFile, token: String, ttlMs: Long): Outcome<WifiShareHandle>
}

// Formats (locale-aware time, date, relative age, byte size, number) is declared in :core:common, not here, because
// the kit (which may not see :core:domain) needs it too: see §10.1 and §12.1.

/** Pure attachment rules shared by :engine:composer (admission) and :engine:media (viewer, share): 5 attachments and
 *  32 MiB per draft, 256 MiB per vault root, photos 10 MiB at 2,048 px / quality 85, the MIME gate. Here because
 *  composer (8) sits above media (13) in the engine order. */
public object AttachmentLimits { /* constants + admit(file): Outcome<Admitted> — 10 §4, 05 §5 */ }

/** Work Bootstrap runs once the gate is open. `runsOn` keeps a widget tap or alarm from starting a phone server. */
public interface StartupTask {
    public val order: Int
    public val runsOn: Set<ProcessStartReason>
    public suspend fun run(): Outcome<Unit>
}
public enum class ProcessStartReason { Ui, Boot, PackageReplaced, Alarm, Worker, Widget, Tile, Service }
/** Made by OcApplication before the graph (initial reason Service); each component shell calls record() first. */
public class ProcessStart {
    public val reasons: StateFlow<ImmutableSet<ProcessStartReason>>
        field = MutableStateFlow(persistentSetOf())
    public fun record(reason: ProcessStartReason) { reasons.update { (it + reason).toPersistentSet() } }
}
```

`ProcessStartReason` is recorded by the component that started the process (the activity, receiver, worker, provider
or tile shell calls `processStart.record(…)` before anything else; a later UI start adds `Ui`). `Bootstrap` runs a task
once, when the first recorded reason in its `runsOn` appears; restoring the phone server and resuming live mode are
`Ui`-only tasks.
Boot and update events go through 11's system-event restorer (11 §15), never through `StartupTask`, and a foreground
service is started only from an exempt context (11 §9.2 rule 4, 05 §7.1).

### 10.5 `:core:data`

**The store interfaces are owned by 05** (§0.3). Their frozen shapes, summarised so this section's other code reads
right:

| Type | Owner shape (05) | Notes 03 adds |
|---|---|---|
| `KeyValueStore` | `get(key): PrefValue?`, `keys(prefix)`, `changes: SharedFlow<Set<String>>`, `suspend commit(lane, PrefEdit.() -> Unit)`, `suspend compareAndSet(key, expected, next)` (§2.1–§2.2) | Android: backed by the **same** `FlutterSharedPreferences` instance the copied Kotlin uses, with `changes` fed by one `OnSharedPreferenceChangeListener`, no separate snapshot (§2.3 step 6) |
| `SecretStore` | `read/write/delete/names`, `Failure.SecretUnavailable`, bound as `GatedSecretStore` (§4.2) | registers every value with the `Redactor` |
| `BlobStore`, `BlobRoot` | `Drafts("draft-attachments-v1")`, `Stash("prompt-stash-attachments-v1")`, `Photos("prompt-photo-recovery-v1")`; `put`, `read(ref, expectedBytes, expectedSha256)` (bounded streaming read, length + SHA-256 verify), `collect` (§5) | **stash and drafts never share a root** (the drafts GC does not know stash references); every other directory (voice models, `diagnostics/`, `provider_logos/`, `migrations/<jobId>/`, crash files in `files/`, the Linux rootfs, `oc.teamEngine.<id>/`) is not a blob root: it stays where it is with the owner in 05 Appendix C |
| `MigrationGate` | `state: StateFlow<GateState>` (`Closed`, `Running`, `Open`, `Blocked`), `awaitWritable()`, `awaitOpen()`; `MigrationStatus`/`MigrationActions` for the shell (§7) | receivers bound their wait (§13.6) |
| `KotlinPrefsFile`, `Context.kotlinPrefs(file)` | the eight Kotlin-owned prefs files (Appendix D) | the only `getSharedPreferences(` call site besides the adopted file and `:android:migration` (K7) |

An earlier draft here had its own `BlobArea` enum (no stash root, `Migration("migration")` instead of `migrations/`,
no `provider_logos/`); it is superseded by 05's `BlobRoot` plus 05 Appendix C. `FilesInventoryTest` (`:core:data`)
asserts that every Flutter-era directory in 05's file table maps to exactly one owner (a blob root or an Appendix C
row), and the stash and drafts roots carry a comment that they must never share a GC.

`BlobStore` paths are `files/<root>/<sha256(owner)>/<sha256(content)>.blob` (05 §5), using `Sha256` from §10.1. Large
payloads (32 MiB drafts, voice packs of hundreds of MB) are never loaded whole: `BlobStore.read` streams, and voice packs
and downloads go through `Downloader` (§10.7) and kotlinx-io `Source`/`Sink`, never `ByteArray`.

03 owns the writer lanes and the deletion orchestration:

```kotlin
package io.github.eslamasabry.oc.core.data

/** Admission + in-flight count + FIFO Mutex: drain() returns after every admitted write finished (§17.3). */
public class WriterLane(public val name: String) { /* complete code in §17.3 */ }

public interface WriterLanes {
    public val app: WriterLane                             // device-wide preferences
    /**
     * FAIL-CLOSED (state-data 2.1; ~12 Flutter stores check existence before writing). Returns a lane that refuses
     * every write with Refused(ProfileDeleted) unless [profile] is in ProfileStore's current list, or is the agents
     * backend id of a saved profile (`owner.agentsBackend()`, "<owner>.agents"). Ids deleted in this process stay in a
     * tombstone set until process death, so a late network callback or worker cannot re-create an orphan
     * `oc.*.<deletedId>` key. Test: delete, then a late write → no key appears (catalog row in the slice contract).
     */
    public fun forProfile(profile: ProfileId): WriterLane
}

// ProfileScope, ProfileResourceCleaner and SweepReceipt: ONE shape, owned by 05 §3.3 (Astra B1). The earlier
// ProfileScopeRegistration / registrations+sweep interface that stood here is withdrawn; do not copy it.
```

The deletion command itself is `:engine:profiles`' (§11.2):
`ProfileDeletion.delete(id, options: DeletionOptions): Outcome<DeleteProfileResult>`, a port of state-data §5 phases
0–2 in 05 §3.5's order: close admission and drain every lane for the profile → `ProfileCloser`s → offline queue
(drop, or keep as drafts per `DeletionOptions.keepQueuedPromptsAsDrafts` and the confirmed `QueuedPromptRemovalPlan`;
refuse if the live queue differs) → drafts → `ProfileResourceCleaner`s → stash (metadata, then files) → the generic
sweep minus retained keys → profile row and secrets as one unit → retained keys last. **Any failure keeps the server**:
`DeleteProfileResult(removedProfile = false, failedOwners)`, plain words, everything reopened.

### 10.6 `:core:db` (switch+1, or earlier by the ADR §9.8 trigger)

- One database `oc.db`, `BundledSQLiteDriver` on every target, schemas committed under `core/db/schemas/`.
- **Public surface (audit 01+03 #11).** Room entities and DAOs stay `internal`; engines may not see Room types
  (`ExternalRules` bans `androidx.room3` outside `:core:db`). `:core:db` publishes one neutral façade per table family,
  with plain `:core:model`/`:core:common` types, and one transaction runner:

```kotlin
package io.github.eslamasabry.oc.core.db

public interface DbTransactions {
    /** One immediate transaction; [block] may call any façade below; no network, no suspension on other locks. */
    public suspend fun <T> immediate(block: suspend DbScope.() -> T): Outcome<T>
}
public interface DbScope { public val queue: QueuedPromptRows; public val drafts: SessionDraftRows /* … one per family */ }

public data class QueuedPromptRow(val entryId: String, val profileId: ProfileId, val position: Long,
                                  val createdAtMs: Long, val dispatchedAtMs: Long?, val entryJson: String, val sha256: String)
public interface QueuedPromptRows {
    public suspend fun list(profile: ProfileId): ImmutableList<QueuedPromptRow>
    public suspend fun upsert(row: QueuedPromptRow)
    public suspend fun markDispatched(entryId: String, atMs: Long): Boolean
    public suspend fun delete(entryId: String): Boolean
    public fun changes(profile: ProfileId): Flow<Unit>          // invalidation only; callers re-read
}
// SessionDraftRows, StashPromptRows, PromptHistoryRows, CommandReceiptRows, FirstPaintRows, CollectionStateRows:
// the same shape, one per 05 §6.2 table. ProfileRows.deleteEverything(profile) runs in one immediate transaction.
```

  The engine keeps its repository interface and both implementations (S14): `:engine:delivery`'s
  `OfflineQueueStore` has `PrefsOfflineQueueStore` (over `KeyValueStore`) and `DbOfflineQueueStore` (over
  `DbTransactions` + `QueuedPromptRows`), chosen by `oc.native.movedToDb` (05 §6.3). That one example is written in
  full, with its contract test run against both implementations (`OfflineQueueStoreContractTest`), before the Room
  slice fans out; every other store copies it. `:engine:*` → `:core:db` is already allowed by ADR §6.1.
- Every table has `profile_id` or is on `config/room-allowlist.txt`; `ProfileRows.deleteEverything(profileId)` runs
  inside one immediate transaction.
- Import from prefs records counts and a SHA-256 per blob in one transaction. Source keys stay frozen in the prefs file
  for one release, **except** when the P5 trigger brought Room in: then they move to verified files (05 §6.4).

### 10.7 `:core:net` and `:core:net-okhttp`

**The transport is owned by 04 §3, §5 and §6** (§0.3): `TransportFactory`/`Transport` with one client per connection,
`BackoffPolicy` + `EventStreamLoop` (our own reconnect loop, not Ktor's), the byte-level SSE parser with its 16 MiB frame
cap, `ResumableDownloader` (206 append, 200 restart, 416 complete, SHA-256 mismatch deletes, atomic rename; kotlinx-io
`Source`/`Sink`), `HardenedOkHttp` (no retries, no redirects, `Proxy.NO_PROXY`, leaf-DER pin) and the status/exception
→ `Failure` mapping. An earlier draft here (`HttpClientFactory.clientFor`, `TransportFailures`, `ReconnectPolicy`,
`Downloader`, `OkHttpClients.hardened`) is superseded.

03 keeps the module rules that shape it:

- `:core:net` may depend on `:core:common`, `:core:security`, `:core:model` and (Android/desktop only) `:core:net-okhttp`,
  never on `:core:data` (ADR §6.1). So **the transport never reads `SecretStore`**: credentials arrive already resolved
  in `GatewaySpec.credentials` (`ConnectionCredentials`), built by `:engine:connection` through the matching
  `ProfileSource` (04 §12.1), which reads `SecretStore` and registers every value with the `Redactor` before handing
  it over. A changed password reaches the transport by reconnecting with new credentials, never by a lookup from
  `:core:net`.
- `:core:net-okhttp` is a plain JVM module with no project dependencies (ADR §7.4); `PinnedLeafTrustManager` accepts a
  leaf iff `sha256(leaf.encoded)` equals the pin **for the profile's current origin** (§10.3 `ProfileTrust.forOrigin`).
- No Ktor `Logging`, `HttpRequestRetry` or `ContentNegotiation`, no OkHttp `HttpLoggingInterceptor` (K5, 04 §11 bans).

### 10.8 `:core:markdown` and `:core:testing`

- `:core:markdown`: `StreamingMarkdown` (wraps `StreamingMarkdownFile`; re-parses only the unstable tail),
  `sealed interface Block` (Prose, Heading, Quote, ListItem, Code, Table, PictureGroup, AgentBlock, Rule),
  `PathChipScanner`, `proseForSpeech(text): String`. Pure; tests from the Markdown fixtures (rendering-chat §11 item 1).
- `:core:testing` (`commonMain`, test-only by rule): `TestDispatchers(scheduler)` (every dispatcher is the test
  dispatcher, `serial()` included), `TestScheduler.asClock()`, `FakeKeyValueStore` (with a "refuse next commit" switch),
  `FakeSecretStore`, `FakeBlobStore` (in memory), `FakeServerGateway` + `FakeOperationsGateway` (record every call,
  scripted replies, scripted `events()`), `FakeForegroundWork`, `FakeDeviceFacts`,
  `WireFixtures.load("oc2/2.1.1/prompt-roundtrip.sse")`, and `catalogRow("B-STATE-042")` (reads the row's
  given/when/then for test messages). `RecordingNavigator` (an `AppNavigator` that records calls) lives in
  `:ui:testing`, because `:core:testing` may not see `:ui:routes` (§6.6). Fakes of **engine** interfaces live in each
  engine's `fake` package (S19), because `:core:testing` may not see engines.
- Where the test data lives (inside `native/`, §1.1): the catalog is copied to `native/catalog/behaviors.jsonl`
  (coordinator-owned; `:quality:rules`' `CatalogDriftTest` compares it with the plan copy in
  `docs/design/native-rewrite-2026-11-01/catalog/` and fails on any difference), and `catalogRow` reads it through a
  JVM resource added to `desktopTest` by `oc.kmp.library`. Wire fixtures live in
  `native/core/testing/src/commonTest/resources/wire/<backend>/<version>/…` and are read through the same JVM resource
  loader in the desktop test target (and `androidHostTest` where a module runs host tests).

---

## 11. Engines (public API shape)

### 11.1 The engine template (every engine follows it)

```kotlin
package io.github.eslamasabry.oc.engine.delivery

/** The public contract (S19). Features and other engines depend on THIS; the API itself is 09 §4's. */
public interface PromptDelivery {
    public val state: StateFlow<DeliveryState>                  // public, immutable, stable
    /** Commands are suspend and return Outcome; they never throw for expected failures. */
    public suspend fun send(request: OutgoingPrompt): Outcome<SendReceipt>
    public suspend fun steer(session: SessionId, text: String): Outcome<SendReceipt>
    public suspend fun stop(session: SessionId): Outcome<Unit>
}

@SingleIn(ConnectionScope::class)              // or AppScope for app-wide engines
@Inject
@ContributesBinding(ConnectionScope::class)    // one supertype → bound type inferred
internal class DefaultPromptDelivery(
    private val gateway: ServerGateway,
    private val queue: OfflineQueueStore,      // repository interface owned by this engine (S14)
    private val receipts: CommandReceipts,
    private val dispatchers: AppDispatchers,
    private val clock: Clock,
    private val scope: ConnectionCoroutineScope,
) : PromptDelivery {
    private val lane = dispatchers.serial("delivery")            // ADR §9.5: all state mutation on this lane

    override val state: StateFlow<DeliveryState>
        field = MutableStateFlow(DeliveryState.Idle)            // Kotlin 2.4 explicit backing field

    override suspend fun send(request: OutgoingPrompt): Outcome<SendReceipt> = withContext(lane) { /* reduce → effects */ }
    override suspend fun steer(session: SessionId, text: String): Outcome<SendReceipt> = withContext(lane) { /* … */ }
    override suspend fun stop(session: SessionId): Outcome<Unit> = withContext(lane) { /* … */ }
}

// engine/delivery/src/commonMain/…/engine/delivery/fake/FakePromptDelivery.kt — shipped with the engine (S19)
public class FakePromptDelivery(initial: DeliveryState = DeliveryState.Idle) : PromptDelivery {
    override val state: MutableStateFlow<DeliveryState> = MutableStateFlow(initial)
    public val sent: MutableList<OutgoingPrompt> = mutableListOf()          // recorded calls
    public var nextSend: Outcome<SendReceipt> = Outcome.Failed(Failure.Unexpected(null))   // scripted reply
    override suspend fun send(request: OutgoingPrompt): Outcome<SendReceipt> { sent += request; return nextSend }
    // …
}

// The state machine: sealed state + pure reducer, table-tested with no coroutines (ADR §9.1).
public sealed interface TurnState { /* Idle, Sending(id), AwaitingEcho(id), Running(run), Stopping, Stalled(sinceMs), … */ }
public sealed interface TurnEvent { /* SendTapped, Accepted, EchoSeen, StreamStarted, StopTapped, Interrupted, Stalled … */ }
public sealed interface TurnEffect { /* WriteDispatchMarker(id), CallPrompt(req), Refetch(session), ScheduleStallCheck(atMs) … */ }
public data class Transition(val state: TurnState, val effects: List<TurnEffect> = emptyList())
internal fun reduce(state: TurnState, event: TurnEvent): Transition = when (state) { /* exhaustive, no else */ }
```

Rules: no Compose, no copy strings, no `android.*`; every buffered/conflated/debounced flow carries a comment with the
catalog row it implements (ADR §9.5); app-scoped engines never hold a reference to a connection-scoped object.

**Connection-scoped commands run in the generation, not in the caller (audit 01+03 #6).** `withContext(lane)` keeps the
caller's `Job` (a ViewModel's), so cancelling a connection generation would not stop it. And `limitedParallelism(1)`
serializes only between suspension points; it is not a mutex. So every command of a connection-scoped engine goes
through one helper on `ConnectionSession`:

```kotlin
/** :engine:connection. Runs [block] as a child of the CURRENT generation's Job, on [lane], under [mutex] when the
 *  operation spans suspension points. The caller awaits the result; the caller's own cancellation cancels only its
 *  wait, never an admitted mutation (prompt cancellation guarantee is the generation's). */
public suspend fun <T> GenerationRunner.command(
    lane: CoroutineDispatcher, mutex: Mutex?, block: suspend GenerationToken.() -> Outcome<T>,
): Outcome<T>
public interface GenerationToken {
    public val generation: Long                       // one per (re)connect or location change
    /** true while endpoint, location and generation are the ones this command was admitted under. */
    public fun isCurrent(): Boolean
}
```

- **Admission** happens inside `command`: a closed or superseded generation returns `Refused(StaleScope)`
  without running `block` (`StaleScope`, the existing reason).
- **Identity checks stay** (as Flutter's selection path does, `connection/models.dart:316`): a mutation calls
  `isCurrent()` immediately before dispatching to the gateway and again before publishing its result to state or a
  store; a false check drops the result (it never publishes into a newer generation).
- **Replacement waits for the old work:** a new generation starts only after `oldGeneration.cancelAndJoin()`, where
  the replacement depends on old work having stopped (location change, profile switch, close).
- **Whole operations that suspend** (send, steer, selection change, queue flush) take the engine's `Mutex`; pure
  state reductions without suspension may rely on the lane alone (P3 proved that for the transcript apply/derive).
- `GenerationRunnerTest`: a command parked between dispatch and publish while the generation is replaced must not
  publish; a caller cancelled mid-send does not cancel the admitted send.

**Interface rule (S19).** Every engine type that a feature or another engine consumes (`ChatFeed`, `AttentionFeed`,
`SessionList`, `PromptDelivery`, `TranscriptEngine`, …) is a `public interface` with an `internal`
`@Inject @ContributesBinding` implementation named `Default…`. The engine's own tests construct `Default…` directly
(`internal` is visible to the module's tests, as in 14 §2's `PromptDelivery` example, which then names
`DefaultPromptDelivery`). The engine's `fake` package ships one `Fake…` per public interface, recording calls and
taking scripted replies, and `contracts/<slice>.md` lists each fake next to its interface. Pure value types, reducers
and `…Policy` objects stay concrete.

### 11.2 Each engine's entry points (frozen per slice in `contracts/<slice>.md`)

| Engine (scope) | Public entry points (signatures abbreviated; all commands `suspend … : Outcome<…>`) |
|---|---|
| `settings` (App) | (settings scope engine spec: 07 §5 I) `Preferences { appearance: StateFlow<AppearanceChoice>; motion: StateFlow<MotionLevel>; saver: StateFlow<Boolean>; locale: StateFlow<LocaleChoice>; set(change: PreferenceChange) }`, `TeachingRegistry { tips: Set<TipDefinition> (multibinding); shouldShow(id); recordShown(id) }`, `interface LocaleHost { fun apply(choice: LocaleChoice) }` (Android: `PlatformLocaleHost`, S21 / §12.3), `FirstRun`, `SearchMatcher` (07 §2.1). |
| `diagnostics` (App) | `Diagnostics { errors: StateFlow<ImmutableList<ErrorEntry>> (20, deduped); record(...) }`, `ProblemReportBuilder.build(): ProblemReport` (128 entries / 256 KiB; `ReportStore` and its start in §18.6), `interface CrashStore`, `interface ExitHistory`, `PerfTrace { mark(name); span(name) }` (2,000 entries), `UpdateNotice { state: StateFlow<UpdateNoticeState> }`, `interface UpdateInstaller { check(): Outcome<ReleaseInfo?>; install(release, progress): Outcome<Unit> }`, `interface StorageReport`. Diagnostics reports crash, freeze and error **categories** to `:engine:analytics` (above it in the order). |
| `analytics` (App) | plan 18 §4: `Analytics { track(event); count(counter); consent; sent }` (never throws, blocks or suspends), the closed `AnalyticsSchema`, `EventQueue`, `UploadPolicy`, `Identity` (rotating install id, per-process session id), `PrivacyScrubber`, `WhatWeCollectSource`, `interface AnalyticsSink` (bound per build: `NoOpSink` until the owner picks a destination, 16 D16-1), `interface AnalyticsTransport` (HTTP through `:core:net`, bound in the app graph). Events hold only enums, booleans, buckets and the version code (K-SEC-11). |
| `profiles` (App) | `ProfileStore { profiles: StateFlow<ImmutableList<ServerProfile>>; trust(id): StateFlow<ProfileTrust>; upsert(profile, password?); setActive(id) }` (an origin change drops pin and consent in the same commit, §10.3), `ProfileDeletion.delete(id, options: DeletionOptions): Outcome<DeleteProfileResult>` (order in §10.5; `ProfileCloser`s and `ProfileResourceCleaner`s are multibindings, so profiles never imports the engines below it), `SignInReset` (calls every `SignInResetParticipant`: agent host secrets from `agents`, BYO identities and GitHub token from `integrations`). |
| `connection` (App + Connection) | `ConnectionRegistry` (App, §13.4; members 04 §12.1, incl. `openTransient` for the phone-agents helper and BYO hosts), `ConnectionGraph` (Connection), `ConnectionSession { status: StateFlow<ConnectionStatus>; capabilities: StateFlow<ServerCapabilities>; location; reconnectNow(); close() }`, `SessionList { sessions: StateFlow<SessionListState>; refresh() }`, `Selection { model/agent/variant per session }`, `ReadState { markRead(ref, idle) }`. |
| `transcript` (Connection) | `TranscriptEngine.open(session): TranscriptHandle` (ref-counted; LRU of open transcripts), `TranscriptHandle { state: StateFlow<TranscriptState>; loadOlder(); close() }`. Publishes at most every 50 ms; first delta at once (ADR §9.3). `TranscriptSearch`. |
| `requests` (Connection) | `RequestsEngine { pending: StateFlow<ImmutableList<PendingRequest>>; answer(id, RequestAnswer) }`, `FormEngine` (one engine for OC2 forms, MCP/ACP elicitation, Codex, AskUserQuestion), `CardCodec`/`CardValidator`/`CardReceipts`. |
| `delivery` (Connection) | `PromptDelivery` (above), `OfflineQueueStore`, `CommandReceipts`; dispatch marker written before a prompt leaves (B-STATE-042). |
| `composer` (Connection) | `Drafts { draft(target): StateFlow<Draft>; onDraftChanged(target, text, selectionUtf16) (debounced save) }`, `AttachmentVault` (BlobRoot `Drafts`), `Stash` (BlobRoot `Stash`, never the drafts root), `PhotoRecovery` (BlobRoot `Photos`), `PhraseChips`, `PromptHistory`; admission uses `AttachmentLimits` from `:core:domain` (§10.4). |
| `runtime` (App) | `interface RuntimeHost` (§12), `PhoneServer { state: StateFlow<PhoneServerState>; ensureRunning(reason); stop(reason) }`, `SetupEngine`, `Healing`, `IdlePolicy`, `ThermalGuard`, `RuntimeScripts` (scripts as resources with SHA-256, generated into Kotlin by a module-local Gradle task). |
| `agents` (App) | `interface AgentHost` (§12; signature, flows and rows in 17 §1), `AgentCatalog` (verified pins copied verbatim), `PhoneAgentsBackend` (contributes the `PhoneAgentsHelper` `ProfileSource` and opens, keeps and recovers the `"<owner>.agents"` transient connection while the helper runs; §13.4), `ChatFeed { items: StateFlow<ImmutableList<ChatFeedItem>> }` (merged across live connections, phone agents included), `Handoff`, `AutoApproval`, `AgentSignIn`; contributes a `SignInResetParticipant`. |
| `attention` (App) | `AttentionFeed { items }`, `StatusLine { current: StateFlow<StatusLineState> }` (the one combiner), `NotificationPolicy`, `interface NotificationSurface`, `interface LaunchSurfaces` (split per 11 CR11-7), `WidgetSnapshotBuilder`, and the two sinks that higher engines push into: `UsageFacts` (plan line, `oc.widgetUsageSubs` feed) and `QuotaAlertSink` (`quota`/`quota_reset` alerts). |
| `schedule` (App) | `ScheduleEngine { schedules; save(spec); delete(id); runDue(claim) }`, `Recurrence` (DST golden tests), `interface ScheduleHost { arm(id, atMs); cancel(id) }`, claims `id:revision:dueAtMs`. |
| `media` (App) | `VoiceController`, `interface AudioCapture`, `interface SpeechRecognizer`, `interface SystemTts`, `interface PcmPlayer`, `interface AudioSession`, `ModelPacks` (+ total-RAM gate), `ReadAloud`, `interface PdfPageRenderer`, `SvgSanitizer`, `OfficeText`, Wi-Fi share rules over `WifiShareHost` (§10.4). `AttachmentLimits` is in `:core:domain`. |
| `project` (Connection) | design and rows in 17 §2: `ProjectHub`, `FileBrowser`, `GitPage` (GitHub device flow), `ReviewModel` (diff, staged revert), `interface TerminalHost { open(target): Outcome<TerminalModel> }`, `interface TerminalModel { view: PlatformViewHandle; title: StateFlow<String>; write(bytes); resize(cols, rows); close() }`, `DevPreview`, `interface ProjectExport`. |
| `integrations` (Connection) | design and rows in 17 §3: `Mcp`, `Providers` (never echoes keys), `Usage` (pushes usage and quota facts into attention's `UsageFacts` / `QuotaAlertSink`), `ByoHost`, `interface ByoSigner`; contributes a `SignInResetParticipant` and `ProfileResourceCleaner`s (BYO identity, GitHub token, quota collector). |
| `entry` (App) | `Bootstrap { state: StateFlow<BootState> }` (runs `StartupTask`s whose `runsOn` matches the process start, §10.4), `EntryRouter`, `ShellWorld` (07 §2.1, §7). |

---

## 12. Platform services: interfaces, homes and bindings

### 12.1 Binding table

Rule (ADR §7.3): an interface lives in the engine that consumes it, or in `:core:domain` when several engines or
features consume it. Android implements it in an `:android:*` module with `@ContributesBinding(AppScope::class)`;
`:desktopApp` binds the `Unavailable*` (or a real JVM implementation where marked) explicitly; tests use fakes.

| Interface | Home | Android implementation (module) | Desktop now | Capability flag |
|---|---|---|---|---|
| `KeyValueStore`, `SecretStore`, `BlobStore`, `MigrationGate` | `:core:data` | `FlutterPrefsStore`, `GatedSecretStore` over `KeystoreSecretStore`, `FilesBlobStore` (`:core:data` a); the gate object is bound by `:androidApp` from `:android:migration`'s runner (05 §7) | in-memory (d) | — |
| `TransportFactory` (04 §3) | `:core:net` | OkHttp-engine factory (`:core:net` a → `:core:net-okhttp`) | same (d) | — |
| `GatewayFactory` (set) | `:core:domain` | `Oc2GatewayFactory`, `PaseoGatewayFactory`, `CodexGatewayFactory` (protocol modules, common) | same | — |
| `ActivityResults`, `PermissionGate` | `:core:domain` | `AndroidActivityResults`, `AndroidPermissionGate` (`:android:platform`; attached to the registry of **whichever host activity is resumed** — `MainActivity` or `WidgetOverlayActivity` (10 C10-5) — and detached on pause; a result returns to the activity that asked; calls with no activity return `Failure.Device`) | `Unavailable*` | `promptPhotos`, `sharedStorageProjects`, … |
| `ForegroundWork` | `:core:domain` | `AndroidForegroundWork` (`:android:background`, 11 §9.2) | in-process no-op that reports active = ∅, pause = null | `backgroundService` |
| `KeepAliveSettings`, `TailscaleApp`, `OsSettingsPages` | `:core:domain` | `AndroidKeepAliveSettings`, `AndroidTailscaleApp`, `AndroidOsSettingsPages` (`:android:platform`, 11 CR11-9) | `Unavailable*` | `tailscaleHandoff` |
| `WifiShareHost` | `:core:domain` | `AndroidWifiShareHost` (`:android:media`, 10 §13) | `Unavailable*` | `wifiFileShare` |
| `Formats` | `:core:common` | `IcuFormats` (`:android:platform`, `android.icu`) | `JavaTextFormats` (`:desktopApp`) | — |
| `StartupTask` (set) | `:core:domain` | contributed by the engines that act at start (runtime, schedule, …) | same | — |
| `DeviceFacts` | `:core:domain` | `AndroidDeviceFacts` (`:android:platform`: `ConnectivityManager` callback, thermal listener, power-save receiver) | `JvmDeviceFacts` (static) | — |
| `ShareService` | `:core:domain` | `AndroidShareService` (`:android:platform`) | `Unavailable*` | — |
| `ExternalLinkOpener` | `:core:security` | `AndroidLinkOpener` (`:android:platform`) | `DesktopLinkOpener` (`xdg-open` via `java.awt.Desktop`, in `:desktopApp`) | — |
| `RuntimeHost` | `:engine:runtime` | `AndroidRuntimeHost` (`:android:linux`) | `UnavailableRuntimeHost` | `builtinLinux` |
| `AgentHost` | `:engine:agents` | `AndroidAgentHost` (`:android:agenthost`) | `Unavailable*` | `builtinLinux` |
| `TerminalHost` | `:engine:project` | `AndroidTerminalHost` (`:android:terminal`) | `Unavailable*` (server PTY still works through `TerminalGateway`) | — |
| `NotificationSurface`, `LaunchSurfaces` | `:engine:attention` | `AndroidNotificationSurface` (`:android:background`), `AndroidLaunchSurfaces` (`:android:widgets` + shortcuts/tiles in `:android:background`) | `Unavailable*` | `notifications`, `homeWidgets`, `launchShortcuts`, `quickSettingsTiles` |
| `ScheduleHost` | `:engine:schedule` | `AndroidScheduleHost` (`:android:schedule`: AlarmManager + WorkManager) | in-process timer (desktop phase) | — |
| `AudioCapture`, `SpeechRecognizer`, `SystemTts`, `PcmPlayer`, `AudioSession`, `PdfPageRenderer` | `:engine:media` | `:android:media` classes of the same names prefixed `Android` | `Unavailable*` | `voice`, `readAloud`, `localPdf` |
| `CrashStore`, `ExitHistory`, `UpdateInstaller`, `StorageReport` | `:engine:diagnostics` | `NativeCrashStore` (reused), `AndroidExitHistory`, `PackageInstallerUpdater`, `AndroidStorageReport` (`:android:platform`) | file-based crash store; `GitHubReleaseCheck` | `inAppUpdater`, `desktopReleaseCheck` |
| `LocaleHost` | `:engine:settings` | `PlatformLocaleHost` (`:android:platform`; S21, §12.3) | JVM `Locale.setDefault` | — |
| `ProjectExport` | `:engine:project` | `SafProjectExport` (`:android:platform`) | `Unavailable*` | — |
| `ByoSigner` | `:engine:integrations` | `KeystoreByoSigner` (`:android:platform`) | `Unavailable*` | — |
| `PlatformCapabilities` (value) | `:core:domain` | `@Provides` in `AndroidAppGraph` (`PlatformCapabilities.android(sdkInt)`, an extension in `:androidApp`) | `@Provides` in `DesktopAppGraph` (`PlatformCapabilities.desktop()`, all Android-only flags false) | — |
| `Trace` | `:core:common` | `@Provides` in `AndroidAppGraph` (`AndroidXTrace`, `:core:common` a) | `@Provides NoopTrace` in `DesktopAppGraph` | — |
| `KitPlatform` (composable seams) | `:ui:kit` | `AndroidKitPlatform` (`:ui:kit` a) + `AndroidKitContentSlots` (`:ui:kit-content` a) | `DesktopKitPlatform` | — |

### 12.2 `KitPlatform` (the only composable seam; features never see it)

`KitTheme`, `KitAppearance`, `KitPlatform` and `KitContentSlots` are **owned by 06 §5–§6** (§0.3): `KitTheme(appearance:
KitAppearance, platform: KitPlatform, localeTag: String, content)`; `KitPlatform` with `family`, `signals`,
`pressIndication(ink)`, `hapticPerformer()`, `SystemBars`, `frostEffect`, `frameDensity`, `highFrameRate`,
`formatMoment`/`formatNumber` (backed by `Formats`, §10.4), `clipEntry`, `links: ExternalLinkUriHandler`,
`content: KitContentSlots`; and `KitContentSlots { Terminal(params); Scanner(params); WebPreview(params) }`. 03 adds only
these constraints, which 06's slot parameter types must meet:

- **Module placement.** `KitPlatform`, `LocalKitPlatform` and `ExternalLinkUriHandler` in `:ui:kit` `commonMain`;
  `AndroidKitPlatform` in `:ui:kit` `androidMain`; `AndroidKitContentSlots` in `:ui:kit-content` `androidMain` (the only
  home of Termux `terminal-view`, CameraX, zxing-cpp and `android.webkit`); `DesktopKitPlatform` in `:ui:kit`
  `desktopMain`; `FakeKitPlatform` in `:ui:testing`.
- **Slot parameter types use only kit, design, words, model and common types**, because `:ui:kit-content` may not see
  `:core:security` (ADR §6.1). In particular `WebPreviewSlotParams` carries no `ApprovedLink`: the dev preview is
  app-detected loopback/LAN content, not an external link. It needs, and 06 freezes with the slot:
  - `origin: PreviewOrigin` (`:core:model`; loopback or private-LAN only, built by `:engine:project`'s dev-preview
    rules from the saved host + port + path, never from link text, 07 C37);
  - `controller: KitWebPreviewController` (`:ui:kit`): `reload()`, `goBack()`, `canGoBack: State<Boolean>`,
    `phase: State<PreviewPhase>` (`Loading | Ready | Failed`);
  - one-origin restriction: navigation to any other origin is cancelled and handed to `links` (the link policy);
  - Back steps the page's own history first (`KitBackGuard(controller.canGoBack)`), then leaves the page.
- `KitPlatform.links` is the root `ExternalLinkUriHandler(policy, opener, confirmHost)`; it is the only UI path to
  `ExternalLinkOpener` (16 SI-1).

`:androidApp` builds the platform once in each host activity (`graph.kitPlatformFactory.create(activity)`) and passes it
to `App()` or `StandaloneRoot()` (§21.1); `:ui:shell` passes it to `KitTheme`. `:desktopApp` provides
`DesktopKitPlatform` the same way. Konsist K1 bans `LocalKitPlatform` and the `…ui.kit.platform` package in features.

### 12.3 Per-app language and OS-surface copy (S21)

| Piece | Where | What it does |
|---|---|---|
| `PlatformLocaleHost : LocaleHost` | `:android:platform` | API 33+: `LocaleManager.setApplicationLocales` (the system applies it to activities, services and the application context). API 26–32: writes `oc.appLocale` (already stored) and recreates the resumed activities. |
| `LocalizedContext` | `:android:platform` | `wrap(base: Context): Context` = `base.createConfigurationContext(config with the app locale)`, where the app locale is read from the ≤ 64-byte mirror `files/native/app-locale` (05 §7.1 step 5c; rewritten by `:engine:settings` on every change of `oc.appLocale`), falling back to the device's own list (never "the first supported language"). It **never opens the prefs file**: `attachBaseContext` runs on the main thread before the first frame, and on the first native launch the prefs file may be tens of MiB and not yet backed up. |
| Activity shells | `:androidApp` | every activity overrides `attachBaseContext(LocalizedContext.wrapBelow33(base))` (a no-op on API 33+); activities stay `ComponentActivity` (no AppCompat). |
| Compose resources | `:ui:shell` | `App()`/`StandaloneRoot()` provide CMP's resource environment from the app locale (the ADR P6 fallback, `LocalResourceEnvironment` override [U P6]); `localeTag` goes to `KitTheme`. |
| OS surfaces | every `:android:*` | notifications, widgets, tiles, shortcuts and channel names read **Android** `strings_os` resources (from `:android:res`, S26) through `LocalizedContext.wrap(context).getString(R.string.…)`, resolved per render (services run with no UI). They never call CMP `getString(Res…)`, which follows the system resource environment off-UI. On a language change, channels are renamed in place and widgets redrawn (11 §15 `relocalize`). |

Test (`:android:background`, Robolectric at API 30 and 34): with the app language `ar` on an `en` device, a built
notification's title and channel name are Arabic. P6 runs on an API 30 AVD as well as API 36/37.
11 S11-8 reads OS copy through the `Words` resolver; the coordinator replaces that with this table in
`contracts/slice-1.md` (or, if P6 proves CMP `getString` follows a `LocalizedContext` off-UI, keeps `Words` on top of it).

---

## 13. The DI graph (Metro 1.5.1)

### 13.1 Shape

```
AndroidAppGraph  @DependencyGraph(AppScope::class)            in :androidApp (final graph lives in platform code [R research §3.4])
│  extends ViewModelGraph (metrox)  → metroViewModelFactory
│  Factory.create(application, buildInfo, redactor, logRing, launchMode, processStart)   (objects made before the graph, §21.1)
│  merges every @ContributesTo / @ContributesBinding / @ContributesIntoSet / @ContributesIntoMap (AppScope) on the classpath:
│    core (dispatchers, clock, app scope, stores, Redactor, TransportFactory) · protocols (GatewayFactory set)
│    engines (App-scoped) · android:* (platform bindings) · ui:shell (navigator, VM factory) · features (VMs, entry installers)
│  accessors used by OS components (§13.6)
│
└── ConnectionGraph  @GraphExtension(ConnectionScope::class)   declared in :engine:connection, one per LIVE connection
       created by ConnectionRegistry.open(profile) with (profile, GatewayPair, ConnectionCoroutineScope)
       merges @ContributesTo(ConnectionScope::class) accessor interfaces from transcript, requests, delivery, composer,
       project, integrations (S4)

DesktopAppGraph  @DependencyGraph(AppScope::class)             in :desktopApp; same contributions minus :android:*,
                                                               plus explicit Unavailable* bindings (§12.1)
```

Graph rules for agents:

1. Classes use **constructor injection** (`@Inject` on the class). No field injection, no service locator calls in
   classes, no `createGraph` outside `:androidApp`/`:desktopApp`/tests of the graph itself.
2. Scope every stateful class: `@SingleIn(AppScope::class)` or `@SingleIn(ConnectionScope::class)`. Stateless helpers
   are unscoped.
3. Bind implementations with `@ContributesBinding(Scope::class)` on the implementation class. One implementation per
   interface per graph; desktop's are bound explicitly in `DesktopAppGraph`.
4. Never inject a `ConnectionScope` type into an `AppScope` class. App-scoped code asks `ConnectionRegistry`.
5. Metro's experimental features stay off (suspend providers, top-level function injection, `hiddenDependencies`)
   [R research §3.5].
6. Tests never build a graph; they construct classes with fakes. The one exception is the graph smoke test (§13.8).
7. Metro infers the bound type of `@ContributesBinding`/`@ContributesIntoSet`/`@ContributesIntoMap` **only when the
   class has exactly one supertype** (Metro aggregation docs [V 2026-11-01]). A ViewModel always has two (`ViewModel()`
   and its `XActions`), so it always writes `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`.
   Any other class with more than one supertype names `binding<…>()` the same way (`ConnectionRegistry` below).

### 13.2 `AndroidAppGraph` and `GraphOwner`

```kotlin
// androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/AndroidAppGraph.kt
package io.github.eslamasabry.opencode_mobile.graph

@DependencyGraph(AppScope::class)
interface AndroidAppGraph : ViewModelGraph {
    // Accessors for components the OS instantiates (minSdk 26 < 28, so no metrox-android; ADR §9.1)
    val appScope: AppCoroutineScope
    val processStart: ProcessStart                              // every component shell records why it ran (§10.4)
    val migrationGate: MigrationGate
    val migrationRunner: MigrationRunner                        // from :android:migration (ADR §9.7 step 6)
    val prefsWarmup: PrefsWarmup                                // from :core:data androidMain (step 7)
    val activityResults: AndroidActivityResults                 // from :android:platform; the resumed host activity attaches
    val entryRouter: EntryRouter
    val shell: ShellDependencies                 // navigator, installers, settings flows the App() root needs
    val kitPlatformFactory: AndroidKitPlatform.Factory
    val workConfiguration: androidx.work.Configuration           // S20: OcApplication's Configuration.Provider returns it
    val phoneServerServiceBody: PhoneServerServiceBody          // :android:linux until slice 4, then :android:background (11 CR11-10)
    val backgroundServiceBody: BackgroundConnectionServiceBody  // from :android:background
    val scheduleReceiverBody: ScheduleReceiverBody              // from :android:schedule
    val scheduledRunWorkerBody: ScheduledRunWorkerBody
    val widgetBodies: WidgetBodies                               // from :android:widgets
    val tileBodies: TileBodies
    val notificationActionBody: NotificationActionBody
    val restoreReceiverBody: RestoreReceiverBody                // :android:background from slice 4 (11 CR11-10)

    /** Android has every platform feature; a few depend on the API level (B-XPLAT-012). */
    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.android(Build.VERSION.SDK_INT)
    @Provides fun trace(): Trace = AndroidXTrace                 // :core:common androidMain (S25: not an expect)

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Application,
            @Provides buildInfo: AppBuildInfo,
            @Provides redactor: Redactor,        // built in onCreate step 2, before any secret is read (ADR §9.7)
            @Provides logRing: LogRing,          // the diagnostics ring the log chain already writes to (§18.2)
            @Provides launch: LaunchMode,        // Normal | SafeMode, from the crash-loop guard (step 3)
            @Provides processStart: ProcessStart, // why this process started; components upgrade it (§10.4)
        ): AndroidAppGraph
    }
}

// androidApp — the only way OS components reach the graph
interface GraphOwner { val appGraph: AndroidAppGraph }
internal val Context.appGraph: AndroidAppGraph get() = (applicationContext as GraphOwner).appGraph
```

`OcApplication` (`io.github.eslamasabry.opencode_mobile.OcApplication`, frozen FQCN) implements `GraphOwner` and
builds the graph in step 5 of the ADR §9.7 order (full code in §21.1).

**The graph in slice 0.** Only these accessors exist at the end of slice 0: `appScope`, `processStart`,
`migrationGate`, `migrationRunner`, `workConfiguration` and `shell` (bound to a stub `ShellDependencies` that shows
"Opening…"). Every
other accessor is added by the slice that creates its module (the coordinator's line, §20.4). The slice-0 homes of the
objects `OcApplication` builds before the graph:

| Object | Slice-0 home | Moves to |
|---|---|---|
| `NativeCrashStore`, `CrashHandler`, `CrashLoopGuard` | `:androidApp` `…opencode_mobile.crash` (copied `NativeCrashStore`) | `:android:platform` in slice 2 (a move, no API change) |
| `LogRing`, `LogcatSink` | `:core:common` (S13, §18.2) | stays |
| `Redactor` | `:core:security` | stays |
| `FlutterBackup` (step 0), `MigrationRunner`, gate object | `:android:migration` (P5 spike code re-created on mainline) | stays |
| `PrefsWarmup` | not built in slice 0 (`:core:data` starts in slice 1); step 7 is a no-op line | `:core:data` androidMain, slice 1 |
| `EntryIntentReader` (07 §7.1; earlier draft name `IntentIngress`) | not built; `MainActivity` captures nothing in slice 0 | `:android:platform`, slice 2 |
| `EntryRouter` | not built | `:engine:entry`, slice 2 |

### 13.3 `DesktopAppGraph`

```kotlin
// desktopApp/src/main/kotlin/io/github/eslamasabry/oc/desktop/DesktopAppGraph.kt
@DependencyGraph(AppScope::class)
interface DesktopAppGraph : ViewModelGraph {
    val shell: ShellDependencies
    @Binds val UnavailableRuntimeHost.bindRuntime: RuntimeHost       // explicit Unavailable* bindings (§12.1)
    @Binds val UnavailableAgentHost.bindAgents: AgentHost
    // … one line per §12.1 row with "Unavailable*" in the Desktop column
    @Provides fun platformCapabilities(): PlatformCapabilities = PlatformCapabilities.desktop()   // extension in :desktopApp
    @Provides fun trace(): Trace = NoopTrace
    @Binds val JavaTextFormats.bindFormats: Formats

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides buildInfo: AppBuildInfo, @Provides redactor: Redactor, @Provides logRing: LogRing): DesktopAppGraph
    }
}
```

[U P1: Metro's exact `@Binds` property syntax on 1.5.1; fallback is an `@Provides` function per binding.]

### 13.4 `ConnectionGraph`, `ConnectionRegistry` and the accessor pattern (S4)

```kotlin
package io.github.eslamasabry.oc.engine.connection

@GraphExtension(ConnectionScope::class)
public interface ConnectionGraph {
    public val profile: ServerProfile
    public val session: ConnectionSession
    public val sessions: SessionList
    public val selection: Selection
    public val readState: ReadState
    public val scope: ConnectionCoroutineScope

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    public fun interface Factory {
        public fun create(
            @Provides profile: ServerProfile,        // saved, or transient (the phone-agents helper, a BYO host)
            @Provides gateways: GatewayPair,
            @Provides scope: ConnectionCoroutineScope,
        ): ConnectionGraph
    }
}

@ContributesTo(ConnectionScope::class)
public interface GatewayBindings {
    @Provides public fun live(pair: GatewayPair): ServerGateway = pair.live
    @Provides public fun operations(pair: GatewayPair): ServerOperationsGateway = pair.operations
}

/** Members owned by 04 §12.1 (§0.3): open(profile, purpose), openTransient(spec, purpose), openEphemeral(...),
 *  openDemo(script), graphFor(ref), close(profile), and `live` keyed by connection id (a saved ProfileId, or
 *  "<owner>.agents" for the phone-agents helper). 03 adds only: it is an interface (S19), and this wiring. */
public interface ConnectionRegistry { /* 04 §12.1 */ }

@SingleIn(AppScope::class)
@Inject
@ContributesBinding(AppScope::class, binding = binding<ConnectionRegistry>())
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())     // two supertypes → name both (§13.1 rule 7)
internal class DefaultConnectionRegistry(
    private val factories: Set<GatewayFactory>,
    private val sources: Set<ProfileSource>,                 // saved profiles + PhoneAgentsHelper (agents) + ByoHost (integrations)
    private val graphFactory: ConnectionGraph.Factory,
    private val appScope: AppCoroutineScope,
    private val dispatchers: AppDispatchers,
) : ConnectionRegistry, ProfileCloser {
    init { check(factories.groupBy { it.backend }.all { it.value.size == 1 }) { "two GatewayFactory for one backend" } }
    /** Closes the profile's own connection AND its "<id>.agents" helper connection. */
    override suspend fun closeForDeletion(profile: ProfileId): Unit { close(profile); close(profile.agentsBackend()) }
    // …
}
```

```kotlin
// in :engine:transcript (and the same three lines in requests, delivery, composer, project, integrations)
@ContributesTo(ConnectionScope::class)
public interface TranscriptAccessors { public val transcriptEngine: TranscriptEngine }
public val ConnectionGraph.transcript: TranscriptEngine get() = (this as TranscriptAccessors).transcriptEngine
```

The cast is safe because Metro merges every `@ContributesTo(ConnectionScope::class)` interface into the generated
extension; the graph smoke test (§13.8) asserts that the real `ConnectionGraph` implements every accessor interface,
so a missing contribution fails on the VPS, never on the phone [U P1: Metro merges contributed interfaces into
graph extensions — documented for `@GraphExtension` [V docs], to be compiled once in P1].

**The phone-agents backend.** Claude Code, Codex and ACP agents on this phone run on a virtual backend
(AGENTS-BACKENDS-04): never a saved profile, created on first need, its transport leased from the host (port 4099,
`oc.agentHostSecret.<owner>`), kept connected while the helper runs, recovered when Android stops the helper, at most
four feed sources (`paseo:<dir>`). Native models it as a **transient connection** with id `"<owner>.agents"`, opened
by `:engine:agents`' `PhoneAgentsBackend` through `ConnectionRegistry.openTransient(TransientProfile(PhoneAgentsHelper,
…), Purpose.AgentsBackend)` with the `PhoneAgentsHelper` `ProfileSource` that agents contributes (04 §12.1). It is
never written to `oc.profiles`, never saved as a fake profile, and agent chats never go through the main connection
(the per-conversation gateway swap that broke banners, auto-approve and Stop in Flutter). `graphFor(ref)` picks the
graph from `ref.backend` (07 §2.2.1): `Main` → `live[ref.profileId]`, `PhoneAgents` → `live[ref.profileId.agentsBackend()]`.
Every per-profile key the agent backend writes keeps the `oc.<what>.<ownerId>.agents` shape, which the owner's generic
sweep (`.<id>.` infix, §10.5) already covers, and `closeForDeletion` closes both connections.

### 13.5 Multibindings (no central lists anywhere)

Every multibinding element type must live in a module that **both** its contributors and its consumer may import;
the "Type home" column is that module.

| Multibinding | Type home | Contributed by | Consumed by |
|---|---|---|---|
| `Set<GatewayFactory>` | `:core:domain` (04) | `:protocol:*` (`@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())`) | `ConnectionRegistry` |
| `Set<EntryInstaller>`, `Set<SheetInstaller>` | `:ui:routes` (07 §2.1) | every feature's `XEntries` / `XSheets` | `:ui:shell` `NavShell` |
| `Map<KClass<out ViewModel>, () -> ViewModel>` and the two assisted maps | metrox | features (`@ViewModelKey` + `binding = binding<ViewModel>()`, `@ManualViewModelAssistedFactoryKey`) | `ShellViewModelFactory` (§13.7) |
| `Set<KeyShape>`, `Set<ProfileResourceCleaner>` (05 §3.3) | `:core:data` | engines and `:android:*` modules that write per-profile data | `ProfileScope` |
| `Set<ProfileResourceCleaner>` | `:core:data` | `:android:widgets` (ledger, targets, `widget-status-profile-<id>` tag), `:android:background` (pinned shortcuts, tile), `:android:platform` (Keystore alias `oc.byoHostSsh.<id>`), `:engine:integrations` (BYO identity, GitHub token, quota collector), `:engine:runtime` (`files/oc.teamEngine.<id>/`, agent homes) | `ProfileDeletion` |
| `Set<ProfileCloser>` | `:engine:profiles` | `:engine:connection` (both keys), `:engine:runtime` (phone-server leases), `:engine:agents` (phone-agents backend, helper lease), `:engine:schedule`, `:engine:composer` (vault writes), `:engine:delivery` (queue lane), `:engine:attention` (alerts, widget rows) | `ProfileDeletion` |
| `Set<SignInResetParticipant>` | `:engine:profiles` | `:engine:agents` (agent host secrets), `:engine:integrations` (BYO identities, tokens) | `SignInReset` |
| `Set<TipDefinition>` | `:engine:settings` | features and engines that own a hidden gesture (owner rule "teach what is hidden") | `TeachingRegistry` |
| `Set<SearchEntry>`, `Set<SearchExclusion>` | `:ui:routes` (07 §2.1; formerly `SettingsSearchEntry`) | each feature's `XSearch.kt` | `:feature:search` through `SearchMatcher` (`:engine:settings`), never by importing another feature |
| `Set<CapabilityFlow>` (the 21 enable flows, SCREENS-NAV-20) | `:ui:routes` (07 §2.1) | the feature that can run the flow (`XFlows.kt`) | kit capability explainer (via `ShellDependencies`) |
| `Set<StartupTask>` | `:core:domain` (§10.4) | `:engine:runtime` (restore phone server, `Ui` only), `:engine:schedule` (re-arm, `Ui` + start events), engines that warm caches | `Bootstrap` (`:engine:entry`), on `appScope`, by `order`, filtered by `runsOn` |
| `AnalyticsSink` (one per build, or a fan-out sink over several, plan 18 §5) | `:engine:analytics` (18) | `:androidApp`/`:desktopApp` bind `NoOpSink` until 16 D16-1 | `Analytics` |

Types that `EntryTarget` refers to (`SettingsSection`, `PhonePage`, `UsageSection`, …) live in `:core:model` (or in
`:engine:entry` itself), never in `:ui:routes`, which `:engine:entry` cannot see; 07 §7 owns their values.

Declare an empty-allowed multibinding where zero contributions are legal (`@Multibinds(allowEmpty = true)`), in the
module that consumes it.

### 13.6 How Android components reach the graph

Every OS-visible class lives in `:androidApp` at its frozen FQCN and is a thin shell; its logic is a `…Body` class in
an `:android:*` module, obtained from the graph.

```kotlin
// :androidApp — io/github/eslamasabry/opencode_mobile/BuiltinServerService.kt   (FQCN frozen, ADR §9.9)
// A shell forwards EVERY lifecycle callback its Flutter-era class overrides; dropping one is a behaviour change.
class BuiltinServerService : Service() {
    private val body by lazy { appGraph.phoneServerServiceBody }
    override fun onCreate() { super.onCreate(); appGraph.processStart.record(Service); body.onCreate(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        body.onStartCommand(this, intent, flags, startId)        // the body decides START_STICKY vs NOT_STICKY
    // Android 15+ dataSync/specialUse time limit: the body commits the pause receipt FIRST, then stops within seconds,
    // or Android crashes the app (11 §9.2 rule 3).
    override fun onTimeout(startId: Int, fgsType: Int) = body.onTimeout(this, startId, fgsType)
    // Android 14 shortService: the one-argument overload; same rule.
    override fun onTimeout(startId: Int) = body.onTimeout(this, startId, fgsType = 0)
    override fun onTaskRemoved(rootIntent: Intent?) { body.onTaskRemoved(this, rootIntent); super.onTaskRemoved(rootIntent) }
    override fun onTrimMemory(level: Int) { super.onTrimMemory(level); body.onTrimMemory(level) }
    override fun onDestroy() { body.onDestroy(this); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
```

Every foreground-service shell (`BuiltinServerService`, `BackgroundConnectionService`, `SetupService`, and the widget
action service) has this shape; the promise rules (`promised`, `foregroundShown`, `stopPending`, startForeground before
any other work) live in the body (11 §9.2 rule 1). `FgsShellTest` (`:androidApp` Robolectric) asserts that each FGS
shell in the freeze list overrides both `onTimeout` overloads, `onTaskRemoved` and `onStartCommand`, and that each
delegates to its body (a fake body records the calls).

```kotlin
// Receivers: never block the main thread; wait for the gate inside goAsync only until the component budget (05 §7.1:
// min(onReceive + 4 s, ProcessStart + 6 s), cold start counted), persist the follow-up, then hand off and finish.
class ScheduledPromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = context.appGraph
        graph.processStart.record(Alarm)
        val pending = goAsync()
        graph.appScope.launch {
            try {
                // Re-arming alarms reads through MigrationReader and needs no gate (05 §7.1).
                graph.scheduleReceiverBody.rearmFromRawReads(intent)
                val budget = graph.processStart.receiverBudget()           // 05 §7.1 table; never more than 6 s
                val open = withTimeoutOrNull(budget) { graph.migrationGate.awaitOpen() } != null
                if (open) graph.scheduleReceiverBody.onReceive(intent)
                else MigrationFollowUpWorker.recordAndEnqueue(context, kind = "schedule", action = intent.action)   // journal first
            } finally { pending.finish() }
        }
    }
}
```

- A receiver that may start a foreground service (`BuiltinRestoreReceiver` on `MY_PACKAGE_REPLACED` and
  `BOOT_COMPLETED`) **never waits for the gate first**: it decides from `MigrationReader` and the Kotlin-owned recovery
  file and starts the service inside the ~20 s broadcast exemption (05 §7.1). The switch-day update delivers exactly
  these broadcasts before the person opens the app; acceptance check 16 (§23) proves it.
- `FlutterBackup` (step 0) **never runs on the main thread**: it runs on the `StartupFence` thread started by
  `OcApplication.onCreate` (05 §7.1, ADR §9.7). A receiver started into the first native process therefore sees the
  gate `Running("S0")`, waits only inside its component budget (05 §7.1 table: at most `ProcessStart + 6 s`, `finish()`
  by `+ 7 s`), and hands off to `MigrationFollowUpWorker`. The code above shows the shape; its `withTimeoutOrNull`
  takes the budget from `graph.processStart.receiverDeadline()`, not a fixed 8 s.
- Widget providers follow the receiver pattern; tile services call their body from `onStartListening`/`onClick` on
  `appScope` (with `processStart.record(Tile)`).
- `LocalPdfService` (isolated `:local_pdf` process) is self-contained: it never reads `appGraph` (which does not exist
  in that process), never touches `filesDir`, and talks only through its binder.

```kotlin
// Workers keep their class names (WorkManager stores them) and the default (Context, WorkerParameters) constructor,
// so no custom WorkerFactory is needed. WorkManager is initialized ON DEMAND (S20): the manifest removes
// androidx.work.WorkManagerInitializer from androidx.startup's InitializationProvider
//   <provider android:name="androidx.startup.InitializationProvider" … tools:node="merge">
//     <meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove" /></provider>
// and OcApplication implements Configuration.Provider, so WorkManager (and its ForceStopRunnable and greedy scheduler)
// starts only after onCreate has built the graph. Before, it ran from a ContentProvider BEFORE Application.onCreate
// and could start pending work while step 0 was still copying files.
class ScheduledPromptWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = applicationContext.appGraph                  // initialized: WorkManager started after onCreate
        graph.processStart.record(Worker)
        graph.migrationGate.awaitOpen()                          // workers have no 10 s window; MigrationFollowUpWorker likewise
        return graph.scheduledRunWorkerBody.run(inputData)      // ≤ 8 min (ADR §9.5)
    }
}
```

P5 adds a check: with a `ScheduledPromptWorker` and a widget-status job pending in the Flutter build's WorkManager
database, install the native build over it; the jobs run after the graph exists and none fails.

### 13.7 ViewModels through Metro

```kotlin
// :ui:shell commonMain — one factory for both apps
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class ShellViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders: Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
```

- The root provides it once: `CompositionLocalProvider(LocalMetroViewModelFactory provides graph.metroViewModelFactory)`.
- A ViewModel always names `binding = binding<ViewModel>()` in `@ContributesIntoMap`, because it also implements its
  `XActions` and Metro infers the bound type only for a single supertype (§13.1 rule 7; P-03-3 compiles one).
- Plain screens: `val vm = metroViewModel<ChatsViewModel>()` (metrox-viewmodel-compose 1.5.1 [V source]).
- Route-keyed screens (S5): `assistedMetroViewModel<ChatViewModel, ChatViewModel.Factory> { create(route) }` [V source].
- Per-entry ViewModel stores come from `rememberViewModelStoreNavEntryDecorator()` (lifecycle-viewmodel-navigation3
  2.11.0), so a ViewModel dies when its entry leaves the back stack.

```kotlin
@AssistedInject
public class ChatViewModel(
    @Assisted private val route: ChatRoute.Chat,            // route keys and arrivals: 07 §2
    registry: ConnectionRegistry,
    private val nav: AppNavigator,
) : ViewModel(), ChatActions {
    /** Follows the conversation's own connection (main or phone agents, §13.4); when it closes, state says so
     *  instead of holding stale engines. */
    private val graph: StateFlow<ConnectionGraph?> = registry.live
        .map { registry.graphFor(route.ref) }                  // main or phone-agents graph, by ref.backend
        .stateIn(viewModelScope, SharingStarted.Eagerly, registry.graphFor(route.ref))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)                    // one supertype (ManualViewModelAssistedFactory): inferred
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ChatRoute.Chat): ChatViewModel
    }
    // …
}
```

### 13.8 Graph smoke test (`:quality:graph`, VPS, every T2)

`:quality:graph` depends on `:desktopApp` (and, through an Android host test in `:androidApp`, on the Android graph). It
never runs at T0: compiling it compiles the whole desktop app (S22). It:

1. builds `DesktopAppGraph` and the Android graph (Robolectric `Application`), and resolves every accessor;
2. resolves every key of every multibinding in §13.5 and instantiates every ViewModel through the factory with fake
   route arguments;
3. opens a `ConnectionGraph` with a `FakeServerGateway` pair and casts it to every `@ContributesTo(ConnectionScope::class)`
   interface found by Konsist in `engine/*/src/commonMain`;
4. asserts `Set<GatewayFactory>` has exactly one factory per `BackendKind`.

---

## 14. State holders

### 14.1 Who owns what (ADR §9.1)

| Holder | Lives | Owns | Exposes | Never |
|---|---|---|---|---|
| Engine | `AppScope` or `ConnectionScope` | truth, timers, streams, state machines, persistence | `StateFlow<…State>` + `suspend` commands returning `Outcome` | Compose, copy strings, `android.*` |
| ViewModel | one per Nav3 entry | the mapping engine state → `XUi`, small UI-only state (filter, expanded rows) | `StateFlow<XUi>` + `XActions` methods named after intents | business rules, one-shot channels, platform types, copy |
| Screen composable | composition | nothing but `remember`ed visual state (scroll, `TextFieldState` for the composer, ADR §9.2) | — | ViewModel references below the screen; derived models built in composition (crash risk, ADR §9.6) |
| Kit part | composition | its own animation and interaction state | plain parameters + lambdas | a ViewModel, an engine, `Failure` mapping beyond `KitFailureView` |

### 14.2 The feature template, complete (`:feature:chats`)

```kotlin
// presentation/ChatsUi.kt — no androidx.compose.* imports (Konsist K2)
package io.github.eslamasabry.oc.feature.chats.presentation

public data class ChatsUi(
    val loading: Boolean = true,
    val rows: ImmutableList<ChatRowUi> = persistentListOf(),
    val filter: ChatsFilter = ChatsFilter.All,
    val needsYouCount: Int = 0,
    val failure: Failure? = null,                  // rendered by the kit's failure view; never a string
    val undo: UndoOffer? = null,                   // acknowledged state, not an event (§14.4)
)
public data class ChatRowUi(
    val ref: ConversationRef, val title: String, val project: String?, val status: RowStatus,
    val lastActivityMs: Long, val finishedUnseen: Boolean,
)
public enum class ChatsFilter { All, NeedsYou, Running }
public enum class RowStatus { NeedsYou, Running, Failed, Idle }

/** Methods are intents, never widget names (ADR §9.1). */
public interface ChatsActions {
    public fun open(ref: ConversationRef)
    public fun startNew()
    public fun showOnly(filter: ChatsFilter)
    public fun refresh()
    public fun undoShown(id: Long)
    public fun undo(id: Long)
}
```

```kotlin
// presentation/ChatsViewModel.kt
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())   // two supertypes → name the bound type (§13.1 rule 7)
public class ChatsViewModel(
    feed: ChatFeed,                       // :engine:agents public INTERFACE (merged across live connections; S19)
    attention: AttentionFeed,             // :engine:attention public interface
    private val nav: AppNavigator,        // :ui:routes
) : ViewModel(), ChatsActions {
    private val filter = MutableStateFlow(ChatsFilter.All)

    public val state: StateFlow<ChatsUi> =
        combine(feed.items, attention.items, filter, ::toChatsUi)      // pure function in ChatsMapping.kt
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUi())

    override fun open(ref: ConversationRef): Unit = nav.openChat(ref)            // AppNavigator and routes: 07 §2.4
    override fun startNew(): Unit = nav.open(ChatsRoute.NewConversation(profileId = null, projectDir = null))
    override fun showOnly(filter: ChatsFilter) { this.filter.value = filter }
    override fun refresh() { viewModelScope.launch { /* feed.refresh() returns Outcome; failure goes into state */ } }
    override fun undoShown(id: Long) { /* clear `undo` when the kit bar has gone */ }
    override fun undo(id: Long) { /* … */ }
}
```

```kotlin
// ui/ChatsScreen.kt — arranges kit parts only (Konsist K1)
@Composable
public fun ChatsScreen(state: ChatsUi, actions: ChatsActions) {
    KitScreen(title = stringResource(Res.string.chats_title)) {
        when {
            state.loading -> KitSkeletonList()
            state.failure != null -> KitFailureView(state.failure, onRetry = actions::refresh)
            else -> LazyColumn {
                items(state.rows, key = { it.ref.toString() }, contentType = { "conversation" }) { row ->
                    KitConversationRow(
                        title = row.title, supporting = row.project, status = row.status.toKitStatus(),
                        sharedKey = row.ref,                 // the kit's grow-from-row seam (§15.6)
                        onClick = { actions.open(row.ref) },
                    )
                }
            }
        }
    }
}
```

```kotlin
// ChatsEntries.kt — the only file in the module that imports androidx.navigation3.runtime
@Inject
@ContributesIntoSet(AppScope::class)
public class ChatsEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatsRoute.Conversations> {
            val vm = metroViewModel<ChatsViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            ChatsScreen(state, vm)
        }
    }
}
```

```kotlin
// ui/ChatsPreviews.kt — UI work starts the day contracts/<slice>.md freezes ChatsUi and ChatsActions
internal object FakeChatsActions : ChatsActions { /* every method empty */ }
internal object ChatsSamples {
    val loading = ChatsUi()
    val list = ChatsUi(loading = false, rows = persistentListOf(/* 3 rows: needs you, running, idle */), needsYouCount = 1)
    val failed = ChatsUi(loading = false, failure = Failure.Network(details = null))
}
@Preview @Composable private fun ChatsListPreview() = KitPreview { ChatsScreen(ChatsSamples.list, FakeChatsActions) }
```

```kotlin
// commonTest — logic only; the widget/screenshot tests come later by owner decision (ADR §4 row 18)
class ChatsViewModelTest {
    @Test
    fun `B-NAV-030 merged list sorts attention first then running then recent`() = runTest {
        withTestMain {                                   // :core:testing helper: Dispatchers.setMain/resetMain
            // FakeChatFeed / FakeAttentionFeed come from the engines' `fake` packages (S19); RecordingNavigator from :ui:testing
            val vm = ChatsViewModel(FakeChatFeed(samples()), FakeAttentionFeed(), RecordingNavigator())
            vm.state.test { /* Turbine: skip initial, assert order */ }
        }
    }
}
```

### 14.3 Engine state flows into ViewModels

- `combine(...)` + `stateIn(viewModelScope, WhileSubscribed(5_000), initial)` for screens (Google's recommendation
  [R research §8.4]). The initial value is a real `XUi` (loading), never `null`.
- Mapping runs in the `combine` lambda on `Main.immediate` only when cheap; heavy mapping (transcript blocks) happens in
  the engine on its lane and arrives ready (ADR §9.3).
- `collectAsStateWithLifecycle()` in the entry, never `collectAsState()`.

### 14.4 Events become acknowledged state

There are no `Channel`/`SharedFlow` events from ViewModel to UI. Pattern:

1. The ViewModel sets a state field with an id: `undo = UndoOffer(id = 7, target = ref)`.
2. The kit part shows it and calls `actions.undoShown(7)` when it is gone (or `actions.undo(7)`).
3. The ViewModel clears the field only if the id still matches (a newer offer is never swallowed).

Navigation is not an event: ViewModels call `AppNavigator` directly (§15.3). The same pattern is used by `EntryRouter`
(S12).

### 14.5 Saved state and process death

- The back stack is saved by `rememberNavBackStack(configuration, start)` (route keys only; §15.1 rule: ids, enums and
  booleans, nothing secret or large).
- Drafts, selection, scroll anchors that must survive process death are persisted by engines (drafts autosave; the
  Flutter app did the same), not by `SavedStateHandle` [R kmp-cmp-status §5: its KMP support is unconfirmed].
- `rememberSaveable` only for transient UI state (an expanded section).

---

## 15. Navigation graph shape

### 15.1 Route keys (`:ui:routes`)

**Route keys, `RouteMeta`/`RouteMotion`, `RootRoute`/`TopLevelRoute`, arrivals, `SheetKey` and the full route
inventory are owned by 07 §2** (§0.3). An earlier draft of this section showed `ChatsRoute.Home`/`.New`,
`RouteMotion { Root, SharedAxisX, GrowFromRow, SlideUp }` and `ChatRoute(ref, focusComposer)`; those are superseded by
07's `ChatsRoute.Conversations`/`.NewConversation`, `RouteMeta(motion = Page | FullScreenDialog, growsFromRow, pane,
requires)` and `ChatRoute.Chat(ref)` + `ChatArrival(focusComposer, landOn, menuAction, opened)` (which carries
Flutter's `landOnRequestID`, `landOnFailure`, `discardIfUntouched`, `menuAction` and first-run). Share-in text travels
as a draft key into the composer engine, never in a route. 03 keeps only the build mechanics:

```kotlin
package io.github.eslamasabry.oc.ui.routes

public object RouteSaving {
    @OptIn(ExperimentalSerializationApi::class)
    public val module: SerializersModule = SerializersModule {
        polymorphic(NavKey::class) { subclassesOfSealed<AppRoute>() }   // kotlinx.serialization ≥ 1.10 [R network-data §4.2]
    }
    public val configuration: SavedStateConfiguration = SavedStateConfiguration { serializersModule = module }  // savedstate
}
```

- One file per feature in the same package (sealed subclasses must share module and package; they may span files).
- Every `AppRoute` subclass round-trips through `RouteSaving.module`: a parameterised `desktopTest` enumerates
  `AppRoute::class.sealedSubclasses` (needs `kotlin-reflect`, test-only, §7.1).
- Parameters are ids, enums, booleans and short strings (07 §2.2.1 `SafeId`); never a password, token, prompt text,
  file content or whole model object (routes are written to saved state). Konsist K8 checks that every `@Serializable`
  `NavKey` implements `AppRoute`.
- `ManageSpaceRoute.*` and `WidgetPanel.*` (07 §2.2) are keys like any other, installed by their features'
  `EntryInstaller`s; the two secondary activities host them through `StandaloneRoot` (§21.1).

### 15.2 `EntryInstaller`

```kotlin
/** Features contribute these with @ContributesIntoSet(AppScope::class); the shell installs them all. No central list. */
public fun interface EntryInstaller {
    public fun EntryProviderScope<NavKey>.install()    // EntryProviderScope is Nav3 1.2.0's DSL receiver [V source]
}
```

### 15.3 `AppNavigator` (the only navigation API ViewModels see)

The interface and its behaviour — `open`, `openChat(ref, arrival, fromOrigin)` (on the chat already on top it posts the
new arrival, so a widget Reply on the open chat focuses the composer; deeper it pops to it), `replaceTop`,
`resetTo(root, then)`, `land(then)`, `replaceRoot(root: RootRoute)`, `selectDestination(TopLevel)` and `back()` — are
**07 §2.4–§2.7** (rules N1–N12). That includes wide-window Back: from a non-Conversations destination on a rail or
sidebar layout, Back returns to `ChatsRoute.Conversations` before the system takes it (07 B01, N-rules), so Back from
Settings never exits the app on a tablet. Tests use `RecordingNavigator` (`:ui:testing`).

03 owns the wiring:

- `ShellNavigator` (`:ui:shell`, `@SingleIn(AppScope::class) @ContributesBinding(AppScope::class)`) implements
  `AppNavigator` over **per-activity slots**. Each `NavShell` attaches its `NavBackStack` with an instance token in a
  `DisposableEffect` and marks itself resumed/paused from the host's lifecycle. Commands go to the slot of the
  most recently resumed shell; commands made while no shell is attached (activity recreation) are kept, latest wins,
  and applied on attach. `taskAffinity=""` plus `NEW_TASK` launches from notifications and widgets can create a second
  `MainActivity` (07 §7.1 keeps that manifest), so a single "the back stack" would cross-wire two windows.
- `AndroidActivityResults` and `AndroidPermissionGate` attach the same way (resumed host wins; a result returns to the
  requesting activity's registry).
- `ShellNavigatorTest` (Robolectric, two `MainActivity` instances): a ViewModel call lands in the resumed instance, the
  paused instance's stack is untouched, and an activity result returns to its requester.

### 15.4 The shell (`:ui:shell`)

```kotlin
@Composable
public fun NavShell(start: AppRoute, deps: ShellDependencies) {
    val backStack = rememberNavBackStack(RouteSaving.configuration, start)
    val token = remember { ShellToken() }                           // one per activity instance (§15.3)
    DisposableEffect(backStack) { deps.navigator.attach(token, backStack); onDispose { deps.navigator.detach(token) } }
    val window = rememberWindowClass()                              // :ui:design classes: 600 / 840 / 1200, short < 480
    val provider = remember(deps.installers) {
        withRouteMotion(entryProvider { deps.installers.forEach { with(it) { install() } } })
    }
    ShellFrame(window, deps) {                                      // compact: single pane, no bottom bar; medium: rail;
        SharedTransitionLayout {                                    // expanded: sidebar ("absent, never disabled")
            NavDisplay(
                backStack = backStack,
                onBack = { deps.navigator.back() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategy = ShellScenes.strategyFor(window),    // list-detail for Settings and Files on wide windows
                sharedTransitionScope = this,
                transitionSpec = { ShellMotion.forward(targetState.metadata) },
                popTransitionSpec = { ShellMotion.back(initialState.metadata) },
                predictivePopTransitionSpec = { edge -> ShellMotion.predictiveBack(initialState.metadata, edge) },
                entryProvider = provider,
            )
        }
    }
}

/** Adds each key's RouteMeta (07 §2.9: `withRouteMeta`) to its entry metadata, so features never write it themselves. */
internal fun withRouteMotion(base: (NavKey) -> NavEntry<NavKey>): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = base(key)
    NavEntry(key, entry.contentKey, entry.metadata + ShellMotion.metadataFor((key as AppRoute).meta)) { entry.Content() }
}
```

[U P2: the exact `NavDisplay` parameter names on CMP navigation3-ui 1.2.0 (`sceneStrategy` vs a list,
`sharedTransitionScope`, the `predictivePopTransitionSpec` lambda shape) and the public `NavEntry` constructor +
`Content()`; the shape above is from the 1.1/1.2 release notes [R kmp-cmp-status §4].]

`ShellDependencies` (`@Inject`, App-scoped) bundles what the root needs: `navigator`, `installers`, `world`
(07's `ShellWorld`: bootstrap, gate, profiles, update and safe-mode state), `appearance: StateFlow<AppearanceState>`
(mapped to 06's `KitAppearance` by `ui/shell/theme/AppearanceMapping.kt`), `locale`, `statusLine`, `capabilityFlows`.

### 15.5 Back and motion policy, implemented once

The motion class of every route and operation is 07 §3 (owner); the table says where each case is implemented.

| Case | Where | How |
|---|---|---|
| Root | shell | no back handler; with one entry Nav3 lets the system take Back (predictive back-to-home) |
| List → chat (GrowFromRow) | shell `ShellMotion` + kit `Modifier.growFromRow(key)` | option A: `sharedBounds` on tap and button-back; off while a gesture is in progress (the kit reads `KitRouteTransition.gestureInProgress`, which the shell provides per entry); option B as a `JETPACK_AHEAD` row in `:ui:shell` `androidMain` |
| Chat with unsaved composer | kit `KitBackGuard(enabled)` used by the chat screen | wraps `NavigationBackHandler`; enabled only while the composer differs from the saved draft |
| Settings pages | shell | shared-axis X with system predictive back |
| New conversation, search launcher | shell | slide up; the gesture closes |
| Sheets and dialogs | kit `KitSheetHost` | screen state, not routes; M3 1.5 in-window `BottomSheet` handles predictive back |
| Reduced motion (system scale 0, user Off/Calm, Saver, severe heat) | kit `MotionPolicy` + shell | cross-fade (Calm) or none (Off) |

Features never import `navigationevent`, `SharedTransitionScope` or `AnimatedContentScope` (Konsist K1); they pass a
`sharedKey` to kit parts.

### 15.6 Kit ↔ shell transition seam

```kotlin
// :ui:kit commonMain
public class KitRouteTransition(
    public val sharedScope: SharedTransitionScope,
    public val contentScope: AnimatedContentScope,
    public val gestureInProgress: State<Boolean>,
)
public val LocalKitRouteTransition: ProvidableCompositionLocal<KitRouteTransition?> = compositionLocalOf { null }
// Kit parts that grow from a row (KitConversationRow, KitScreen) read it and apply sharedBounds only when it is
// non-null, the motion level is Full, and no back gesture is in progress.
```

`:ui:shell` provides `LocalKitRouteTransition` per entry from a `NavEntryDecorator` (it has the `AnimatedContentScope` of
the entry and the navigation-event transition state).

### 15.7 Sheets, dialogs and menus

Screen state, not routes (ADR §9.4). A feature puts `sheet: XSheet?` in its `XUi`; the screen renders
`KitSheetHost(visible = state.sheet != null, onDismiss = actions::closeSheet) { … }`. A sheet becomes a route only when
a deep link must open it or it must survive process death alone; that needs the coordinator's approval in the slice
contract.

### 15.8 `EntryRouter` (`:engine:entry`) and the shell mapping

`EntryRequest`, `EntryTarget`, `EntryHandoff`, `RootDecision`, the link parsers and `ShellWorld` are **owned by 07 §7**
(§0.3). 07's `EntryTarget` already carries what entry flows need today (SCREENS-NAV §3.1, §4): `Chat(ref,
focusComposer, landOn, opened)` with `LandOn = Request(id) | OldestRequest | Failure`, new-conversation drafts held by
key, the unknown-server confirmation, the address-handoff consent sheet (`session/v2`, gated by
`sessionAddressHandoff`), quota alerts → Usage plans after token re-validation, and phone-setup progress vs ready
chosen from `setup.json`. An earlier draft here (`PendingEntry`, `OpenChat`, `OpenChats`, `OpenSettings(section)`) is
superseded.

03 keeps the mechanics:

- `EntryRouter` is an app-scoped engine interface (S19) in `:engine:entry`; it holds a request until bootstrap and the
  gate allow it, re-evaluates on every connection change, and hands the target over as **state plus acknowledgement**
  (one pending handoff with an id; `consume(id)` exactly once, B-NAV-005; a request that arrives during handling stays
  pending) — never a channel (S12).
- The shell collects the pending handoff, maps the target with one exhaustive `when` in `EntryTargetRouting.kt`, calls
  `AppNavigator`, then acknowledges. This replaces the seven Flutter copies (SCREENS-NAV §4). Nav3's own deep-link
  matcher is not used (ADR §4 row 12).
- Types `EntryTarget` names live where `:engine:entry` can see them (`:core:model` or `:engine:entry`), never in
  `:ui:routes` (§13.5).
- `MainActivity` submits a request only for a fresh intent (§21.1): never on a restored activity or a launch from
  Recents, which redeliver the original intent.

---

## 16. Errors and results

### 16.1 Where translation happens (once, at the boundary; ADR §9.6)

| Boundary | Translator (file) | Turns | Into |
|---|---|---|---|
| `:core:net`, `:core:net-okhttp` | `StatusMapping`, `Transport.classify` (table: 04 §3.8) | `UnknownHostException`, `ConnectException`, `SocketTimeoutException`, SSL/pin failures, Ktor timeouts, HTTP status | `Network(why)`, `TimedOut`, `Certificate(why)`, `SignIn(why)`, `NotFound`, `Conflict`, `Busy(kind)`, `Server`, `Rejected`, `Refused(OriginChanged / CredentialsInUrl / CleartextNotConfirmed)` |
| `:protocol:oc2` | `Oc2Failures` | server `_tag` (`SessionNotFoundError`, `SessionBusyError`, `FormAlreadySettledError`, `ConflictError`, `WorktreeError`, …, protocol notes §12) and decode failures | `NotFound`, `Busy(SessionBusy)`, `Refused(AlreadyAnswered)`, `Conflict`, `ProjectGit`, `UnreadableReply`, `Incompatible` |
| `:protocol:paseo`, `:protocol:codex` | `PaseoFailures`, `CodexFailures` | daemon/RPC errors, version mismatch | `ComputerDidNotAnswer`, `UnreadableReply`, `SignIn(why)`, `Incompatible`, `Refused(DispatchUncertain / StaleScope)` (04 §8) |
| `:core:data` | `StorageFailures` | `commit() == false`, `IOException`, `KeyStoreException`, `AEADBadTagException` | `StorageRefused`, `SecretUnavailable` ("enter the password again", ADR §9.7) |
| `:android:*` | `<Module>Failures` | proot exit codes, `ErrnoException`, `SecurityException`, `ActivityNotFoundException`, CameraX/AudioRecord errors | `Device`, `StorageRefused`, `Folder(...)`, `Runtime(problem)`, `Media(problem)`, `Termux` |
| engines | none | engines pass `Failure`s through or create `Refused(reason)` / `Unavailable(feature)` | — |

Model and provider failures inside a session (`execution.failed`, `step.failed`, `tool.failed`, `retry.scheduled`)
are **not** `Failure`s; they are transcript parts rendered by `:ui:kit-chat` (PROTOCOLS-36).

Every translator builds `details` with `Redactor.details(...)`, and `details` is never empty: a failed step without a
message says its type and HTTP status (`Redacted.shape`) (PROTOCOLS-36).

### 16.2 Failure → plain words (`:ui:words`)

```kotlin
package io.github.eslamasabry.oc.ui.words

public data class FailureCopy(val title: StringResource, val wayForward: StringResource?, val canRetry: Boolean)

/** Exhaustive: adding a Failure subtype or a RefusalReason fails compilation here until the copy exists.
 *  Named toFailureCopy(), not copy(): every Failure subtype is a data class whose member copy() would win on a
 *  smart-cast subtype. Same for RefusalReason.toFailureCopy(). */
public fun Failure.toFailureCopy(): FailureCopy = when (this) {
    is Failure.Refused -> reason.toFailureCopy()
    is Failure.Unavailable -> FailureCopy(Res.string.failure_unavailable, null, canRetry = false)
    is Failure.Network -> FailureCopy(Res.string.failure_network, Res.string.failure_network_next, canRetry = true)
    is Failure.Certificate -> FailureCopy(Res.string.failure_certificate, Res.string.failure_certificate_next, canRetry = false)
    is Failure.SignIn -> FailureCopy(Res.string.failure_sign_in, Res.string.failure_sign_in_next, canRetry = false)
    is Failure.Media -> problem.toFailureCopy()       // 10 §4.1
    is Failure.Runtime -> problem.toFailureCopy()     // 11 §5.5
    is Failure.TimedOut -> FailureCopy(Res.string.failure_timed_out, Res.string.failure_timed_out_next, canRetry = true)
    is Failure.SecretUnavailable -> FailureCopy(Res.string.failure_secret, Res.string.failure_secret_next, canRetry = false)
    // … every subtype; no `else` branch (Konsist K20 checks `when` over Failure has no else in :ui:words)
}
```

The kit renders it: `KitFailureView(failure, onRetry, onReport)` shows the title and way forward in plain words,
Retry when `canRetry`, and a **Details** fold with `failure.details?.text` (left-to-right isolated, copyable) next to
"Report a problem". No other code reads `Redacted.text` (Konsist K14), so there is no path from server or exception
text to a visible label (B-NAV-009, owner rule "no raw errors").

### 16.3 Crashes

1. `OcApplication.onCreate` step 1 installs the uncaught-exception handler: it writes a category-only record to
   `NativeCrashStore` (reused Kotlin; records never contain provider keys or arbitrary exception values, B-SEC-043),
   then defers to the previous handler.
2. Every root scope (`AppCoroutineScope`, `ConnectionCoroutineScope`, service body scopes) has a `SupervisorJob` and a
   `CoroutineExceptionHandler` that logs at Error; one failed child never takes its siblings down.
3. An exception inside composition kills the process (Compose has no error widget), so `XUi` and kit models are built
   in ViewModels and engines, never computed in composition from raw data.
4. The crash-loop guard (three crashes within 10 s of start, `files/native/crashloop.json`) opens safe mode: Report a
   problem, Settings, Start fresh (ADR §9.7 step 3).

---

## 17. Coroutines, scopes and dispatchers

### 17.1 Scopes

| Scope | Created by | Job | Runs on | Ends when |
|---|---|---|---|---|
| `AppCoroutineScope` | `CommonBindings` at graph build | `SupervisorJob()` | `default` | process death |
| `ConnectionCoroutineScope` | `ConnectionRegistry.open` | `SupervisorJob(appJob)` | `default` | `close()`: stop admission → cancel streams → drain lanes → cancel |
| generation scope | `ConnectionSession` on each (re)connect or location change | `Job(connectionJob)` | the engine's lane | the next generation starts (replaces generation counters, ADR §9.5) |
| engine lane | `dispatchers.serial("<engine>")` | — | one task at a time | — |
| `viewModelScope` | ViewModel | `SupervisorJob` | `Main.immediate` | the Nav3 entry leaves the stack |
| service body scope | `…ServiceBody.onCreate` | `SupervisorJob(appJob)` | `default` | `onDestroy` |
| receiver / worker | `goAsync()` + `appScope.launch` / `CoroutineWorker` | child of app scope / WorkManager's | `default` | `finish()` / `doWork` returns |

### 17.2 Dispatcher rules

- State mutation happens only on the owning engine's serial lane (`withContext(lane) { … }` at each public command).
- Heavy pure work (decode a 100-message page, merge 5,000 parts, parse Markdown) runs in `withContext(dispatchers.default)`
  and returns a value to the lane.
- Blocking I/O runs in `dispatchers.io`. `runBlocking` and `GlobalScope` do not exist outside tests (Konsist K16).
- The UI thread runs composition, the navigator and ViewModel glue only.

### 17.3 `WriterLane` (complete; ADR §9.5, research §8.3)

```kotlin
public class WriterLane(public val name: String) {
    private val admitting = AtomicBoolean(true)            // kotlin.concurrent.atomics (opt-in, §6.4)
    private val inFlight = MutableStateFlow(0)             // admitted writes not yet finished (atomic update {})
    private val mutex = Mutex()                            // fair FIFO among admitted writes, like Dart's chained futures

    public suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> {
        inFlight.update { it + 1 }                         // 1. count FIRST …
        if (!admitting.load()) {                           // 2. … then check: a writer that counted before
            inFlight.update { it - 1 }                     //    stopAdmission is waited for by drain(); one that
            return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))   // counted after sees closed
        }
        try { return mutex.withLock { block() } }          // admitted → runs, in FIFO order
        finally { inFlight.update { it - 1 } }             // also on cancellation
    }
    public fun stopAdmission() { admitting.store(false) }  // synchronous, the first step of close/delete
    /** Returns when every admitted write has finished, including one admitted on another dispatcher that had not
     *  reached the mutex yet when stopAdmission() ran. */
    public suspend fun drain() { stopAdmission(); inFlight.first { it == 0 } }
}
```

A serial dispatcher alone is not enough: a write that suspends on disk I/O frees the dispatcher, and a close could
run in the middle. A mutex alone is not enough either: a writer can pass the admission check, lose the CPU and take the
lock only after `drain()` has returned. Counting before checking (both sequentially consistent atomics) closes that
window, so "drain" means "every admitted write finished" (B-STATE-002, B-STATE-013, B-CHAT-051). `WriterLaneTest`
holds the race vector: a writer parked between the check and the lock on another dispatcher, then `drain()` must not
return until it finishes.

### 17.4 Flow rules

| Purpose | Type | Rule |
|---|---|---|
| Current state | `StateFlow<T>` of an immutable data class | equality must be correct (data classes, `ImmutableList`) |
| Server events | the gateway's event stream (shape and hot/cold semantics: 04 §6.5), collected by one engine per connection | durable events: unbounded/suspending buffer, never dropped; volatile deltas may be coalesced; after every `Connected`, reconcile by refetch, never replay |
| UI state | `combine(…).stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | initial is a real loading `XUi` |
| One-consumer facts (reply finished → notification) | `SharedFlow(replay = 0)` with an explicit buffer and overflow | only between engines; never to UI |
| Transcript publishing | engine-side `StateFlow<TranscriptState>` updated at most every 50 ms; first delta at once | ADR §9.3; the publish-count test ports `perf_chat_test.dart` |

Every `buffer`, `conflate`, `debounce`, `sample` carries a comment naming the catalog row it implements (ADR §9.5).

### 17.5 Cancellation rules

1. Catch `CancellationException` only to rethrow it. Use `attempt {}`; never `runCatching` around suspend calls (K3).
2. `withContext(NonCancellable)` only to finish an admitted write or record a final receipt; each use names its
   catalog row in a comment, and its writes name the profile and generation they belong to.
3. After an `await`, re-check identity (`GenerationToken.isCurrent()`, §11.1) before every gateway dispatch and before
   publishing any result, whether into state or a store. Cancellation alone is not enough: a command started from a
   `viewModelScope` is not a child of the generation unless it runs through `GenerationRunner.command`.
4. Do not pass `Job()` to builders (`launch(Job())`); coroutines 1.11 lints it. Use the scope tree.

### 17.6 Tests

`runTest` with `StandardTestDispatcher`; `TestDispatchers(testScheduler)` and `testScheduler.asClock()` from
`:core:testing`; `backgroundScope` for collectors that never end; virtual time for 50 ms / 5 s / 15 min / 60 min rules;
Turbine `test {}` for flows; `withTestMain {}` for ViewModels (it calls `Dispatchers.setMain`/`resetMain`, which K4
allows in `:core:testing` and test source sets only).

---

## 18. Logging, diagnostics and tracing

### 18.1 `AppLog` (`:core:common`; Kermit 2.2.0 underneath, never imported elsewhere)

```kotlin
public enum class Severity { Debug, Info, Warn, Error }
/** A thrown error is logged by shape only: class name + up to 12 frames "Class.method:line". Never its message. */
public data class ErrorShape(val type: String, val frames: List<String>)
public data class LogEntry(val atMs: Long, val severity: Severity, val tag: String, val message: String, val error: ErrorShape?)
public interface LogSink { public fun write(entry: LogEntry) }
/** Marker for the root sink; only :core:security's RedactingWriter implements it. */
public interface RedactingLogSink : LogSink

public object AppLog {
    /** Once, in OcApplication.onCreate before any secret is read (ADR §9.7 step 2). Entries logged earlier wait in a
     *  64-entry in-memory buffer and are flushed THROUGH [root] here (S13); nothing is ever written unredacted. */
    public fun install(root: RedactingLogSink, minSeverity: Severity)
    public fun tagged(tag: String): Log
}
public class Log internal constructor(public val tag: String) {
    public fun d(message: () -> String)
    public fun i(message: () -> String)
    public fun w(error: Throwable? = null, message: () -> String)
    public fun e(error: Throwable? = null, message: () -> String)
}

// usage, one per class:
private val log = AppLog.tagged("Delivery")
log.i { "queued prompt ${id.raw} for ${profile.raw}" }          // ids are fine; prompt text is not
```

### 18.2 The writer chain

```
AppLog ─► Kermit Logger (min Info in release, Debug in debuggable builds)
        └► RedactingWriter (:core:security; Redactor.redact on message and frames)
             ├► LogRing (:core:common; 128 entries / 262,144 bytes — the Flutter report ring [R]; in memory only;
             │           read by :engine:diagnostics' ProblemReportBuilder)
             └► LogcatSink (:core:common androidMain; debuggable builds only — the owner's phone has logcat silenced anyway)
```

The ring is written to disk only when the person exports "Report a problem".

### 18.3 Diagnostics sizes (ported numbers [R lib/diagnostics])

| Store | Size | Notes |
|---|---|---|
| Error list (`Diagnostics.errors`) | 20 entries, repeats folded | message capped at 2,000 chars, stack at 12,000, both redacted |
| Report ring (`LogRing`) | 128 entries / 256 KiB | newest kept |
| `PerfTrace` | 2,000 marks | `OCTRACE <ms> name key=value parent=…` text format kept so the existing analysis scripts still parse it |
| Crash record | 8,192 bytes | category-only (B-SEC-043) |
| Failed-job report | 120 lines / 16,384 chars | setup and runtime jobs |

### 18.4 Never log

Provider credentials (`/config/providers` returns API keys), passwords, tokens, cookies, `Authorization` headers,
full URLs with query strings, prompt text, file contents, clipboard contents. Enforcement:

- K5 bans `android.util.Log`, `println`, `printStackTrace`, OkHttp `HttpLoggingInterceptor` and Ktor's `Logging`
  plugin everywhere, with one named exemption: `LogcatSink.kt` (`:core:common` androidMain), the sink behind the
  redacting root. Copied Flutter-era files are exempt only while on `config/copied-kotlin-exemptions.txt` (§2.3).
- `MigrationLogScrubTest` (`:android:migration`) runs every migration path with planted secrets and asserts none of
  them is in the ring (ADR §9.7, state-data §3.3).
- `RedactorTest` vectors port `KitRedact` and the redaction-profile tests (B-SEC rows).

### 18.5 Tracing

```kotlin
// :core:common
public interface Trace {
    public fun <T> section(name: String, block: () -> T): T
    public fun mark(name: String, attributes: Map<String, String> = emptyMap())
}
// Not an expect (S25): each app graph provides it (§12.1). Android: AndroidXTrace (androidx.tracing 2.0.5 sections,
// Perfetto; :core:common androidMain). Desktop and tests: NoopTrace.
public object NoopTrace : Trace {
    override fun <T> section(name: String, block: () -> T): T = block()
    override fun mark(name: String, attributes: Map<String, String>) {}
}
```

`PerfTrace` (`:engine:diagnostics`) records the Flutter marks with the same names: `app.main`, `app.first_frame`,
`app.first_connected`, `events.connected`, `events.first`, `events.first.session_log`, `messages.page`,
`prompt.admitted`, and HTTP spans ≥ 50 ms as `http oc2 METHOD /path-template` (PROTOCOLS-45). Engines never read traces
or logs: diagnostics are not product state.

### 18.6 Problem-report capture and its start (`:engine:diagnostics`; DESKTOP-WEB-CROSS-58; STATE report_problem rows)

The persisted problem report is the only diagnostics store that survives a restart. Port of `report_problem.dart`,
`report_problem_capture.dart` and `report_problem_startup.dart`:

```kotlin
public class ReportStore /* @SingleIn(AppScope) */ {
    public val entries: StateFlow<ImmutableList<ReportEntry>>        // committed snapshots only; entries read-only
    public suspend fun open(): Outcome<Unit>                          // once per process; idempotent
    public suspend fun append(entry: ReportEntry): Outcome<Unit>      // redact, truncate, evict, write, rename, then publish
    public suspend fun clear(): Outcome<Unit>                         // memory, committed file and any pending write
    public suspend fun close()                                        // detaches every source; repeatable
}
public sealed interface ReportEntry {                             // wire kinds error | timing | androidExit | thermal
    public val time: Instant
    public data class Error(override val time: Instant, val source: String, val message: String, val stack: String?) : ReportEntry
    public data class Timing(override val time: Instant, val source: String, val message: String) : ReportEntry
    public data class AndroidExit(override val time: Instant, val exit: TypedExit) : ReportEntry   // never OS description text
    public data class Thermal(override val time: Instant, val tier: HeatTier) : ReportEntry
}
```

| Rule | Value |
|---|---|
| File | `files/diagnostics/report_problem.json` `{"version":1,"entries":[…]}` (adopted, 05 Appendix C); temp `report_problem.pending` |
| Limits | 128 entries, 262,144 UTF-8 bytes per snapshot and per temp file; field caps source 128, message 2,048, stack 8,192 chars (code-point safe) |
| Order of a write | redact (`Redactor.opaqueTokens(text)`) **before** truncation; then `FileOutputStream` + `fd.sync()` + atomic rename on the engine's serial dispatcher, synchronously, because a crash handler cannot wait for a queue; publish to observers only after the rename |
| Eviction | drop the oldest **timing** first; an error, Android exit or thermal event is evicted only when no timing is left (fix `4cbdfca3e`: a shared oldest-first ring lost a failure within a minute) |
| Opening | `ReportStartup` runs as a `StartupTask` after the first frame; the launch never waits for it (the Flutter budget was 300 ms; native opens off the main thread and the first frame never waits at all); a store that cannot open leaves one fixed diagnostics line, never a crash (B-STATE-078) |
| Restore | an orphan `.pending` is discarded, never renamed into place (05 §7.8); an oversize, malformed or foreign-version file is deleted; today's redaction and limits are re-applied to what is read (B-STATE-079) |
| Capture | errors from `AppLog` at Error, typed exits once (no error copy, B-STATE-077), thermal changes, finished timings (a timing is on disk before its caller hears it completed, B-STATE-070); at start only as many historical timings as the capacity are imported (B-STATE-071); framework error callbacks are recorded with a category but without caller metadata (B-STATE-074) |
| Clear | erases memory, the committed snapshot and any pending write, survives restart, and old trace evidence cannot reappear (B-STATE-072) |
| Failures | a failed write or delete is reported (`storageFailed`, plain words), never published as success (B-STATE-080); storage failures never crash listeners (B-STATE-073) |
| Who reads | Report a problem (07 L-section), the safe mode (07 A02), and nothing in product logic |

Tests (`:engine:diagnostics` commonTest): `ReportStoreTest` (B-STATE-079, 080), `ReportCaptureTest` (070–074),
`ReportStartupTest` (075–078), `ReportEvictionTest`, `ReportOpenNeverBlocksTest`. Built by S1-ENG (rows assigned in
17 §4.2).

---

## 19. Code style and lint

### 19.1 Formatting

- ktfmt 0.64, `--kotlinlang-style`, run by `tool/format-changed.sh` on files changed since the merge base:
  `git diff --name-only --diff-filter=AM "$(git merge-base HEAD "$BASE")" -- '*.kt' '*.kts'`.
- The jar is the Maven Central fat jar
  `https://repo1.maven.org/maven2/com/facebook/ktfmt/0.64/ktfmt-0.64-with-dependencies.jar` (classifier
  `with-dependencies`; there is no `jar-with-dependencies` for 0.64 [V listing 2026-11-01]), cached under
  `~/.cache/oc-tools/`, verified against the SHA-256 pinned in the script:
  `5b3d5286fd2defcc7dc8e28c21ddf156cc6b2d8682bdcd929ce4333e7a6201f2` (Maven Central's published `.sha256` [V]).
- T2 runs the same script with `--dry-run --set-exit-if-changed`. Agents never format files they did not change
  (memory `agent-format-churn`).

### 19.2 Compiler and Compose settings

- `explicitApi()` per §6.4; `allWarningsAsErrors` at T2 (`-Poc.werror=true`); opt-ins listed in §6.4 only.
- `config/compose-stability.conf` (every `oc.kmp.compose` module):

```
// AUDITED VALUE TYPES ONLY (ADR §9.3; audit 01+03 #12). No package of services, scopes or interfaces.
// Each line names a package whose public types are all immutable data; a new line needs the kit owner's review.
io.github.eslamasabry.oc.core.common.Outcome
io.github.eslamasabry.oc.core.common.Failure
io.github.eslamasabry.oc.core.common.ids.**
io.github.eslamasabry.oc.core.model.**
io.github.eslamasabry.oc.core.domain.capabilities.**
io.github.eslamasabry.oc.engine.**.state.**
io.github.eslamasabry.oc.ui.design.**
kotlinx.collections.immutable.*
```

This is the list slice-0 P1 built [V `config/compose-stability.conf` on `native/m0-2026-11-01`]. Whole
`core.common.**` and `core.domain.**` (scopes, gateways, service interfaces) are **not** listed: declaring them stable
would assert a contract nobody checked. P4 judges the file by frame time, `equals` cost and allocations as well as by
recomposition counts (Android's guidance warns against counts alone); P3 showed the pattern that keeps comparison
cheap: a `revision` as the first field of a large state, short-circuiting `equals`, and reused row instances. If P4
still shows extra work, the fix is state ownership and reference reuse, never a wider list.

### 19.3 detekt

`tool/qa/detekt.sh` runs the detekt 1.23.8 CLI (no type resolution, as in the Flutter repo) with `config/detekt.yml`:
`complexity` (LongMethod 80, CyclomaticComplexMethod 15, `@Composable` functions excluded from LongParameterList),
`exceptions` (`TooGenericExceptionCaught` on, except `attempt` and `*Failures.kt`; `SwallowedException` on),
`style.ForbiddenComment` (a `TODO` must name an owner and a slice: `TODO(slice-3, chat-lead)`), `MaxLineLength` 120.
Formatting rules are off (ktfmt owns formatting). [U P-03-2: detekt 1.23.8 embeds an older Kotlin parser; if it
rejects Kotlin 2.4 syntax such as explicit backing fields, slice 0 either moves to detekt 2.0 or drops detekt — the
Konsist rules and the compiler carry every rule this plan depends on.]

### 19.4 Konsist rules (`:quality:rules`, Konsist 0.18.1) — the rewrite's architecture tests

K1–K12 are the ADR §6.3 rules; K13–K22 are added here. Analytics privacy is 16's K-SEC-11.

| # | Rule | Scope |
|---|---|---|
| K1 | **Kit only**: feature files import only the allowlist below; `*Entries.kt` alone may import `androidx.navigation3.runtime`; `*Previews.kt` alone may import `@Preview` | `feature/**` |
| K2 | `presentation` packages import no `androidx.compose.*` and no `…ui.words.res` (no copy in ViewModels); ViewModel constructors take no `android.*` types | `feature/**` |
| K3 | no `runCatching` in a file that declares or calls a `suspend` function | production code |
| K4 | no `Dispatchers.` and no `Clock.System` outside `:core:common` (and `:core:testing`, test source sets) | production code; copied files on the exemption list (§2.3) |
| K5 | no `android.util.Log`, `println`, `printStackTrace`, `HttpLoggingInterceptor`, Ktor `Logging` | everywhere except `LogcatSink.kt` and listed copied files |
| K6 | no `openUri`, `Intent.ACTION_VIEW`, `startActivity(Intent(ACTION_VIEW…` outside `ExternalLinkUriHandler` and `:android:platform`'s `AndroidLinkOpener` | everywhere except listed copied files |
| K7 | `getSharedPreferences(` only in `:core:data` `androidMain` and `:android:migration`; Kotlin that owns a named prefs file opens it through `Context.kotlinPrefs(KotlinPrefsFile.X)` from `:core:data` (05 Appendix D) | everywhere except listed copied files |
| K8 | every `@Serializable` class implementing `NavKey` implements `AppRoute` (so S17 registers it) | `ui/routes` |
| K9 | every string literal shaped `"oc.<what>.$…"` matches a registered `KeyShape` prefix (05 §3.3) | production code |
| K10 | `src/androidMain` and `src/desktopMain` exist only in the §6.4 `hasPlatformSourceSets` modules; `src/iosMain` only in `:core:common` | all KMP modules |
| K11 | no `String.format`, `System.`, `Thread.`, `java.`, `javax.` in `commonMain` | KMP modules |
| K12 | test function names start with a catalog id `B-[A-Z0-9]+-\d{3}` (infra tests in `:quality:*` and `build-logic` exempt) | `commonTest`, `test`, `androidHostTest`; copied tests on the exemption list until renamed |
| K13 | `@OptIn(RedactorApi::class)` only in `:core:security` and `Redacted.kt`; K13b: `@OptIn(LinkPolicyApi::class)` only in `ExternalLinkPolicy.kt` | everywhere |
| K14 | `Redacted.text` is read only by the kit Details fold, `ProblemReportBuilder` and `RedactingWriter` | everywhere |
| K15 | no `.message`, `.localizedMessage`, `.stackTraceToString()` on a `Throwable` outside `*Failures.kt` translators, `:core:common`'s `AppLog` and `:core:security`'s `Redactor.kt` (`details(error)` redacts and caps it) | production code; listed copied files exempt |
| K16 | no `GlobalScope`, `runBlocking`, `launch(Job())` | production code |
| K17 | in features and `:ui:shell` `commonMain`: no `Color(`, `0xFF…` literals, numeric `.sp`, numeric `.dp` other than `0.dp`, `RoundedCornerShape`, `tween(`, `spring(`, `keyframes(`, `animate…AsState`, `Modifier.clickable`, `combinedClickable`, `Dialog(`, `Popup(`, `performHapticFeedback` (sizes, colours, shapes, motion and press come from kit tokens and parts) | `feature/**`, `ui/shell` |
| K18 | `androidx.compose.material3` imports only in `:ui:kit*`; `androidx.compose.material3.adaptive` also in `:ui:shell` | everywhere |
| K19 | a feature never imports another feature's package | `feature/**` |
| K20 | a `when` over `Failure` or `RefusalReason` in `:ui:words` has no `else` branch | `ui/words` |
| K21 | a `….fake` package (§9.1) is imported only from test source sets and `*Previews.kt` | everywhere |
| K22 | `config/copied-kotlin-exemptions.txt` only shrinks (compared with the merge base) and every path on it exists | `native/` |

K1 in full (the kit-only rule; the allowlist is kit-v2 §9 made concrete, ADR §6.3):

```kotlin
package io.github.eslamasabry.oc.quality

class KitOnlyRuleTest {
    private val allowed = listOf(
        // layout, scroll, lazy lists, state — arranging, not drawing
        "androidx.compose.runtime.",
        "androidx.compose.foundation.layout.",
        "androidx.compose.foundation.lazy.",
        "androidx.compose.foundation.rememberScrollState", "androidx.compose.foundation.ScrollState",
        "androidx.compose.foundation.verticalScroll", "androidx.compose.foundation.horizontalScroll",
        "androidx.compose.foundation.text.input.TextFieldState",        // text state holder for kit fields (ADR §9.2)
        "androidx.compose.foundation.text.input.rememberTextFieldState",
        "androidx.compose.ui.Modifier", "androidx.compose.ui.Alignment",
        "androidx.compose.ui.unit.", "androidx.compose.ui.semantics.", "androidx.compose.ui.focus.",
        "androidx.compose.ui.input.key.",
        "androidx.compose.ui.platform.LocalLayoutDirection", "androidx.compose.ui.platform.LocalDensity",
        "androidx.compose.ui.platform.LocalFocusManager", "androidx.compose.ui.platform.testTag",
        // ViewModels, DI, copy
        "androidx.lifecycle.", "dev.zacsweers.metro.", "dev.zacsweers.metrox.viewmodel.",
        "org.jetbrains.compose.resources.stringResource", "org.jetbrains.compose.resources.pluralStringResource",
        // our layers that features may see (ADR §6.1)
        "io.github.eslamasabry.oc.ui.kit.", "io.github.eslamasabry.oc.ui.kitchat.", "io.github.eslamasabry.oc.ui.kitcontent.",
        "io.github.eslamasabry.oc.ui.design.", "io.github.eslamasabry.oc.ui.words.", "io.github.eslamasabry.oc.ui.routes.",
        "io.github.eslamasabry.oc.engine.", "io.github.eslamasabry.oc.core.domain.", "io.github.eslamasabry.oc.core.model.",
        "io.github.eslamasabry.oc.core.common.",
        "kotlinx.coroutines.flow.", "kotlinx.coroutines.launch", "kotlinx.coroutines.CoroutineScope",
        "kotlinx.coroutines.Job", "kotlinx.coroutines.delay", "kotlinx.coroutines.withContext", "kotlinx.coroutines.async",
        "kotlinx.coroutines.Deferred", "kotlinx.coroutines.coroutineScope", "kotlinx.coroutines.supervisorScope",
        "kotlinx.coroutines.ensureActive", "kotlinx.coroutines.isActive", "kotlinx.coroutines.CancellationException",
        "kotlinx.collections.immutable.", "kotlinx.serialization.", "kotlin.",
    )
    /** Test sources add the test toolkit on top of `allowed` (never kit internals or platform types). */
    private val allowedInTests = allowed + listOf(
        "kotlin.test.", "kotlinx.coroutines.test.", "app.cash.turbine.",
        "io.github.eslamasabry.oc.core.testing.", "io.github.eslamasabry.oc.ui.testing.",
    )
    private val onlyInEntries = listOf("androidx.navigation3.runtime.")
    private val onlyInPreviews = listOf("androidx.compose.ui.tooling.preview.")
    private val bannedInside = listOf(
        "io.github.eslamasabry.oc.ui.kit.platform.",     // KitPlatform / LocalKitPlatform: kit-internal seam
        "io.github.eslamasabry.oc.core.common.AppDispatchers",
    )

    // scopeFromDirectory returns test files too; split by source set so feature tests are judged by the test list.
    private val featureFiles = Konsist.scopeFromDirectory("feature").files
    private fun isProduction(path: String) = "/src/commonMain/" in path
    private fun isTest(path: String) = "/src/commonTest/" in path

    @Test
    fun `K1 features arrange kit parts only`() {
        featureFiles.filter { isProduction(it.path) }.assertTrue { file -> file.importsAllowed(allowed) }
    }

    @Test
    fun `K1 feature tests use only the test toolkit on top of the allowlist`() {
        featureFiles.filter { isTest(it.path) }.assertTrue { file -> file.importsAllowed(allowedInTests) }
    }

    private fun KoFileDeclaration.importsAllowed(list: List<String>): Boolean {
        val own = packagee?.name?.split('.')?.take(6)?.joinToString(".")   // io.github.eslamasabry.oc.feature.<name>
        return imports.all { imp ->
            val n = imp.name
            when {
                bannedInside.any { n.startsWith(it) } -> false
                onlyInEntries.any { n.startsWith(it) } -> name.endsWith("Entries")
                onlyInPreviews.any { n.startsWith(it) } -> name.endsWith("Previews")
                own != null && n.startsWith(own) -> true
                else -> list.any { n.startsWith(it) }
            }
        }
    }
}
```

[U P-03-6 / ADR P9: Konsist's `files`, `imports`, `packagee`, `path` names on 0.18.1 and whether
`scopeFromDirectory("feature")` sees `src/commonMain` in this layout; P9 plants `material3`, `LocalContext` and a raw
`clickable` in a feature (the rule must fail on each) **and a feature test importing `kotlinx.coroutines.test`,
Turbine, `oc.core.testing` and `oc.ui.testing`** (the production rule must ignore it, the test rule must pass it).]

### 19.5 Android lint

`:androidApp:lintStableRelease` and each `:android:*` module's `lintDebug` at T2, `abortOnError = true`, no baseline.
`MissingTranslation` runs on the generated `strings_os` resources (ADR §7.5). `NewApi` guards minSdk 26.

### 19.6 Other gates in `:quality:rules` and `:quality:graph` (T2)

| Gate | What it checks |
|---|---|
| `ModuleOwnersTest` | every Gradle project has a `MODULE_OWNERS` row and every row names a project (§2.4) |
| `ComponentFreezeTest` | Against the **baseline APK named in `config/freeze-baseline.txt`**: the Flutter build actually installed on the owner's phone at the switch (freeze-tip commit, versionCode — local builds there are 4300–4511, newer than the published 2.1.0+57 — and APK SHA-256; the coordinator reads the versionCode read-only with `adb shell dumpsys package`, builds or fetches that exact APK, and records all three). It diffs the merged manifest of `:androidApp` against `aapt2 dump xmltree` of that APK: component FQCNs, exported flags, intent filters, `process`/`isolatedProcess`, `taskAffinity`, `launchMode`, `windowSoftInputMode`, FGS types and the `specialUse` subtype properties, the overlay's theme; the application's `allowBackup="false"`, `fullBackupContent`/`dataExtractionRules` (exclude everything, so profiles and credentials never leave the device), `manageSpaceActivity`, `supportsRtl`, `enableOnBackInvokedCallback`; `<queries>` (`com.tailscale.ipn`, `com.termux`); the merged permission list; channel ids, shortcut ids and the `opencode-mobile://` scheme. WorkManager worker class names are a **superset** of the baseline's (05 §7.1: `MigrationFollowUpWorker` is new; new names go in the component ledger). Until a component's slice lands it is listed in `config/freeze-pending.txt`, which may only shrink. `FLAG_SECURE` on the overlay is a runtime flag, checked by `WidgetOverlaySecureTest` (Robolectric) |
| `CatalogRatchetTest` | reads JUnit XML from the T2 run, counts passing tests per catalog id; the number of covered ids may only go up (`config/catalog-coverage.txt`) |
| `KitCapacityTest` | every public `@Composable fun Kit…` in `:ui:kit*` has a hard max-width row in `config/kit-capacity.json` (owner rule "component max width") |
| `NoRedTest` (in `:ui:design`) | no colour role in any style or pack falls in the red hue band, except the owner-approved Signal lamps/Stop/alert ink (ui-kit map R2) |
| `StringDriftTest` (in `:tools:strings`) | every key exists in all 7 languages with matching format args and plurals; `strings_<feature>.xml` names match feature modules |
| `RoomSchemaTest` (once `:core:db` exists) | every table has `profile_id` or is on `config/room-allowlist.txt` |
| `ThirdPartyHashTest` | `third_party/**` files match `third_party/SHA256SUMS` |
| `GraphSmokeTest` (`:quality:graph`) | §13.8 |
| `ModuleNameTest` | no two Gradle projects get the same Kotlin module name (S23) |
| `CatalogDriftTest` | `native/catalog/behaviors.jsonl` equals the plan's catalog copy (§10.8) |
| `CopiedKotlinExemptionsTest` | K22 (§2.3) |
| `BoundaryRulesTest` (B-BUILD-001) | the Konsist rules of §6.6 and §19.4 as one named gate: UI never imports a protocol module, never branches on the server flavour (allowlist shrink-only with reasons), background wire names stay with their owners, one notification posting path |
| `KitMapGateTest` (B-BUILD-023) | kit-only components, shrink-only allowlists, no raw dialogs/snackbars/route builders, no literal colours or motion values outside the kit, no URL launching from the kit, the left/right layout ban (06 §16, G16 port) |
| `ReleaseClasspathPolicyTest` (audit 01+03 #13, ADR §12 D12) | resolves `stableReleaseRuntimeClasspath` (transitives included) and fails on any `-alpha`/`-dev`/`-SNAPSHOT` version, and on any `-beta`/`-rc` outside the D12 allowlist (Material 3 1.5 inside the kit, plus whatever the owner has explicitly admitted, each line dated). Slice 0 found no alpha on the classpath; the betas were CMP/Compose 1.13.0-beta01 and M3 1.5.0-beta01 [V P1 §8.6]. Runs at every T2; blocking only for switch and preview candidates |
| `ToolchainPinTest` (B-BUILD-031) | `compileSdk`/`targetSdk`, the JDK level and the version string (name + positive build number) match `gradle/libs.versions.toml`, and every CI workflow file pins the same toolchain |
| `UiLedgerTest` (B-BUILD-033) | every screen in 07 §5 has a route or an explicit "merged/dropped" row; ids unique; every navigation edge lands on a page |
| `ApkPluginRegistrantTest` (B-BUILD-034, rewritten) | the VPS APK build fails without the expected classes in the dex (the Flutter plugin registrant check becomes a check for `OcApplication` and the Metro graph in any dex) and builds in its own folder (memory `vps-suite-shared-dir-trap`) |
| `ActionCoverageTest` (B-BUILD-005) | each area ledger's controls are proven both ways and each gateway sends the exact request for its action (port of `coverage/files_actions_coverage_test`, ledgers re-pointed at native test names) |
| `PromptTimelineTraceTest` (B-BUILD-027, in `:engine:diagnostics`) | the prompt timeline records sent, accepted, first token, turn done; a refused prompt is an error and ends tracking (`PerfTrace`, §18.5) |
| `ReleaseContractTest` (B-BUILD-029, in `native/tool/release`) | `scripts/release.sh` native mode is fail-closed; publication verifies source, quality and signed draft assets (16 §7.4) |
| `GestureLedgerTest` | 06 §11.1 gesture twins and tips |
| `NoTraceReadTest` | 16 §1.10: no `getTraceInputStream` outside the `qa` source set |


### 19.7 The rewrite's `native/AGENTS.md` rules (copy verbatim)

```
1. Read plan sections 01 (decisions) and 03 (modules) before touching code. The module rules fail the build; don't fight them.
2. Features arrange kit parts. A missing component is added to the kit by its owner, never drawn in a feature.
3. One screen = XUi + XActions + XViewModel + XScreen(state, actions) + XEntries. No copy, no Compose, no platform types in presentation.
4. Engines own the truth: StateFlow out, suspend commands returning Outcome in, one serial lane per engine.
5. Expected failures are Failure values. Use attempt {}; never runCatching around suspend code; never show exception text.
6. No Dispatchers.*, Clock.System, GlobalScope or runBlocking outside :core:common. Inject AppDispatchers and Clock.
7. Log only through AppLog with lambdas. Never log credentials, tokens, prompt text, URLs with queries or file contents.
8. Every URL the app did not author goes through ExternalLinkPolicy → ExternalLinkOpener.
9. Per-profile keys are oc.<what>.<profileId> and registered with ProfileScope; per-profile files and tables too.
10. UI gates on ServerCapabilities / PlatformCapabilities, never on BackendKind.
11. Name every test after its catalog row: `B-AREA-NNN what it proves`. Write it red first, from the row.
12. Run target tasks only (desktopTest, testAndroidHostTest, compileKotlinDesktop, :quality:rules:test). Never `build`. One Gradle build at a time on the PC.
13. Format only the files you changed (tool/format-changed.sh). Commit with [skip ci].
14. Shared modules (kit, words, routes, shell, core, connection, transcript) have one owner: propose, don't edit.
15. Steer, never queue, where steering exists. Actions name their target. No new bar or row levels. Plain words.
16. Engines expose interfaces; use the engine's Fake in tests. A ViewModel names binding<ViewModel>() in @ContributesIntoMap.
17. Evidence: a QA record docs/qa/native/slice-<N>-<date>/README.md (commit, APK SHA-256, device, steps, evidence, what is NOT proven); a render per visual pass; one screen per image; a short video for motion (plan 14 §11, 15).
18. Where plan sections disagree, the owner in plan 03 §0.3 wins; ask the coordinator, never pick silently.
```

---

## 20. How things plug in (recipes)

### 20.1 A new feature (no central file is edited)

1. Read your slice's `contracts/<slice>.md`: `XUi`, `XActions`, routes, catalog rows, and what the slice deletes.
2. Create the folders:
   `mkdir -p native/feature/<name>/src/{commonMain,commonTest}/kotlin/io/github/eslamasabry/oc/feature/<name>/`
   and inside `commonMain/…/<name>/` the packages `presentation/` and `ui/`.
3. Write `native/feature/<name>/build.gradle.kts` from §7.2, listing only the engines you read.
4. Check `MODULE_OWNERS` has your row (the slice brief adds it; only the coordinator edits the file).
5. Add your routes file `native/ui/routes/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/routes/<Name>Routes.kt`
   (a sealed interface extending `AppRoute`; ids-only parameters).
6. Add your copy: `native/ui/words/src/commonMain/composeResources/values/strings_<name>.xml` and the six other
   `values-<lang>/strings_<name>.xml` files (keys `<name>_…`; the l10n owner's rule decides untranslated text).
7. Tests first: turn each catalog row into a failing `commonTest` (`B-…` name) against the mapping functions and the
   ViewModel with fakes from `:core:testing` and the engines' `fake` packages (S19).
8. Write `presentation/<Name>Ui.kt`, `<Name>Mapping.kt`, `<Name>ViewModel.kt`; then `ui/<Name>Screen.kt` from kit parts;
   then `ui/<Name>Previews.kt` (canned `XUi` + `Fake<Name>Actions`); then `<Name>Entries.kt`.
9. T0: `./gradlew :feature:<name>:desktopTest :feature:<name>:compileKotlinDesktop :quality:rules:test --tests "*KitOnlyRuleTest*"`
   (`:quality:rules` has no project dependencies, so this never compiles the app, S22).
10. Format your files (`tool/format-changed.sh`), commit `[skip ci]`, hand over to the coordinator.

The feature appears in the app because Metro collects your `@ContributesIntoSet` entry installer and
`@ContributesIntoMap` ViewModel when `:androidApp` (which depends on every module) builds its graph.
`:androidApp/build.gradle.kts` lists features with `implementation(project(":feature:<name>"))` — that line is the
coordinator's, added at merge.

### 20.2 A new engine

1. The coordinator places it in the ADR §6.2 order (edit both the ADR and `ModuleRules.engineOrder`) and adds the
   `MODULE_OWNERS` row.
2. `plugins { id("oc.kmp.library") }`; public API in the root package as interfaces (S19), state in `.state`, the
   `Default…` implementations and the rest `internal`, one `Fake…` per public interface in `.fake`.
3. Platform needs become an interface in this engine (or `:core:domain` if shared) with an `Unavailable…` binding
   for desktop (§12.1).
4. Register per-profile data: `@ContributesTo(AppScope::class) interface XScopeKeys { @Provides @IntoSet fun keys(): KeyShape = … }` (and a `ProfileResourceCleaner` for non-key or embedded data, 05 §3.3),
   plus a `ProfileResourceCleaner` for anything that is not a prefs key, and a `ProfileCloser` if the engine holds
   per-profile work (§13.5). Every per-profile write goes through `WriterLanes.forProfile(id)` (fail-closed, §10.5).
5. Connection-scoped? Add the accessor interface + extension property (§13.4).

### 20.3 A new backend (protocol)

1. `BackendKind` gets a value (`:core:model`, core owner; 04 §11.0 maps its wire name).
2. New `:protocol:<x>` module; implement `GatewayFactory` with `@ContributesIntoSet(AppScope::class, binding = binding<GatewayFactory>())`.
3. Implement `ServerGateway` facets it supports; inherit every `ServerOperationsGateway` default for the rest.
4. Compute `ServerCapabilities` from the probed version.
5. Write `<X>Failures` (§16.1) and contract tests replaying recorded traffic through `ktor-client-mock`.
   No feature changes: features gate on capabilities.

### 20.4 A new OS component (service, receiver, widget, tile, activity)

1. Logic goes in an `:android:*` module as a `…Body` class (`@Inject`, `@SingleIn(AppScope::class)`). The body has a
   method for every lifecycle callback the component overrides today (services: `onTimeout` both overloads,
   `onTaskRemoved`, `onTrimMemory`, `onStartCommand` returning the sticky mode; §13.6).
2. The coordinator adds the accessor to `AndroidAppGraph` and the thin shell class + manifest entry in `:androidApp`;
   the shell calls `processStart.record(…)` first and forwards every callback.
3. An existing component keeps its exact FQCN, exported flag and intent filters (ADR §9.9); a new one is appended to
   the freeze list. Nothing is ever renamed.

### 20.5 A new kit part

Kit owners only: `Kit…` name, plain parameters, a row in `config/kit-capacity.json`, previews in light/dark × en/ar,
Roborazzi goldens recorded on the VPS, motion through `KitMotion` tokens, haptics through `KitHaptic`, and any Android-
only API behind `KitPlatform` with a `JETPACK_AHEAD.md` row if it is ahead of CMP.

### 20.6 A new string

Add the key to your own `strings_<feature>.xml` in all seven `values*/` folders (never to another feature's file;
`strings_common.xml` belongs to the l10n owner). Read it with `stringResource(Res.string.<key>)` in composables.
**Every string an OS surface shows** (notification titles and bodies, channel names, widget text, tile labels,
shortcut labels, the app label) goes in `strings_os.xml`, which the l10n task generates into the resources-only
`:android:res` module (S26); `:android:*` code reads it as an Android resource through `LocalizedContext` so it follows
the in-app language, not the system's (§12.3). OS-surface code never calls CMP `getString(Res…)`.

### 20.7 Definition of done (every slice, every agent)

- [ ] Every catalog row in the slice contract has a passing test named after it; the ratchet went up.
- [ ] `XUi`/`XActions` match the contract; previews exist for loading, content, failure and empty.
- [ ] No module rule, Konsist rule or detekt finding; no new `@Suppress`.
- [ ] Copy in all seven languages; Arabic checked in RTL; frame text capped at 1.3×; reading text scales fully.
- [ ] Per-profile data registered with `ProfileScope`; deletion test covers it.
- [ ] Failures map to plain words; Details fold shows redacted text only.
- [ ] Motion only from kit tokens; reduced motion respected; hidden gestures have a tip.
- [ ] Desktop compiles (`compileKotlinDesktop`); the Android behaviour is what is judged.
- [ ] What the slice deletes (from the brief) is deleted.
- [ ] Only changed files formatted; commits carry `[skip ci]`.
- [ ] Evidence recorded as 14 §11 and 15 require: the QA record `docs/qa/native/slice-<N>-<YYYY-MM-DD>/README.md`
      (commit, APK SHA-256, device, steps, evidence, what is not proven), progress renders for visual work, one screen
      per image, a short video for motion, and the PR items of the root `AGENTS.md` (screenshots, accessibility notes,
      privacy/security notes for credential or data changes, migration notes for stored-format changes).
- [ ] Copied files the slice refactored are off `config/copied-kotlin-exemptions.txt`.

---

## 21. The application, desktop, quality and tools modules

### 21.1 `:androidApp`

```
androidApp/
├── build.gradle.kts                    plugins { id("oc.android.application") } + one implementation(project(…)) per shipped module
├── key.properties                      gitignored; same format as today's android/key.properties (never committed, D2)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml         every component at its frozen FQCN, exported flags and intent filters (android-platform map §3)
    │   ├── kotlin/io/github/eslamasabry/opencode_mobile/
    │   │   ├── OcApplication.kt  MainActivity.kt  ManageSpaceActivity.kt
    │   │   ├── WidgetConfigureActivity.kt  WidgetRouteActivity.kt  WidgetOverlayActivity.kt
    │   │   ├── BackgroundConnectionService.kt  BuiltinServerService.kt  SetupService.kt  LocalPdfService.kt
    │   │   ├── AttentionTileService.kt  PauseTileService.kt
    │   │   ├── SessionsWidgetProvider.kt  SessionsWideWidgetProvider.kt  SessionsListWidgetProvider.kt
    │   │   ├── SessionsStripWidgetProvider.kt  UsageWidgetProvider.kt  UsageWideWidgetProvider.kt  StatusWidgetProvider.kt
    │   │   ├── WidgetActionReceiver.kt  CodingActionReceiver.kt  StatusActionReceiver.kt  LivePauseReceiver.kt
    │   │   ├── ScheduledPromptReceiver.kt  BuiltinRestoreReceiver.kt  BuiltinIdleReceiver.kt
    │   │   ├── ScheduledPromptWorker.kt  WidgetStatusWorker.kt          (class names stored by WorkManager)
    │   │   ├── TermuxResultService.kt                                    (only while D7 keeps Termux migrate-out)
    │   │   └── graph/  AndroidAppGraph.kt  GraphOwner.kt  AppBuildInfoFactory.kt
    │   ├── keepRules/  app.keep  workmanager.keep  sherpa.keep           (AGP 9.3 keepRules dir, ADR §9.9)
    │   └── res/        ONLY what the manifest references (S26): launcher icons, widget provider XML (which may point at
    │                   :android:widgets layouts and preview images), xml/shortcuts.xml, xml/network_security_config.xml
    │                   (same file and comment as today), xml/locales_config.xml, xml/backup_rules.xml and
    │                   xml/data_extraction_rules.xml (both exclude everything, as today). Widget layouts, shapes and
    │                   preview PNGs live in :android:widgets/res; notification icons in :android:background/res;
    │                   generated values*/strings_os.xml in :android:res
    ├── preview/res/    xml/shortcuts.xml targeting …opencode_mobile.native; label "OpenCode Native"
    ├── test/           Robolectric: Android graph smoke, manifest/freeze helpers
    └── androidTest/    upgrade-journey helpers, migration with the real Keystore (ADR §10.2)
```

`OcAndroidApplicationPlugin` essentials:

```kotlin
extensions.configure<ApplicationExtension> {
    namespace = Namespaces.APP
    compileSdk { version = release(Sdk.COMPILE) { minorApiLevel = Sdk.COMPILE_MINOR } }
    defaultConfig {
        applicationId = Namespaces.APP                      // io.github.eslamasabry.opencode_mobile (ADR §9.9)
        minSdk = Sdk.MIN; targetSdk = Sdk.TARGET
        versionCode = versionProps.code                     // version.properties; ≥ 10000, never goes back (D3)
        versionName = versionProps.name
        manifestPlaceholders["appLabel"] = "@string/app_label"          // same placeholders as the Flutter build [R]
        manifestPlaceholders["appShortcuts"] = "@xml/shortcuts"
    }
    flavorDimensions += "channel"
    productFlavors {
        create("stable") { dimension = "channel" }                      // S16 (the ADR's "main")
        create("preview") {
            dimension = "channel"
            applicationIdSuffix = ".native"                             // side-by-side preview, own data, no migration
            manifestPlaceholders["appLabel"] = "@string/app_label_preview"
        }
    }
    buildTypes {
        getByName("release") {
            optimization { enable = true }                               // R8 + resources; rules in src/main/keepRules
            // Acceptance check 7 only: sign the preview release with the debug key when asked, never by default.
            if (providers.gradleProperty("oc.debugSignRelease").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        // No custom "benchmark" type: the androidx.baselineprofile plugin creates benchmarkRelease and
        // nonMinifiedRelease itself, and a hand-made "benchmark" type collides with its naming.
    }
    testBuildType = "release"                                           // T3 runs connected tests on release, as today
    splits { abi { isEnable = true; reset(); include("arm64-v8a", "x86_64"); isUniversalApk = true } }  // one versionCode
    packaging { jniLibs { useLegacyPackaging = true } }                 // proot executables must be extracted
    buildFeatures { buildConfig = true; compose = true }
    lint { abortOnError = true }
}
```

The release signing config reads `androidApp/key.properties` when present; without it `validateSigning…Release` fails,
which still proves Kotlin compiles (as AGENTS.md says today). Both signer lineages stay exactly as they are (ADR D2).

`OcApplication` — the ADR §9.7 order, in code:

```kotlin
class OcApplication : Application(), GraphOwner, Configuration.Provider {
    override lateinit var appGraph: AndroidAppGraph
        private set

    // S20: WorkManager asks for this on first use, which is always after onCreate (its default initializer is removed
    // from the merged manifest, §13.6), so no worker or scheduler can run before appGraph exists.
    override val workManagerConfiguration: Configuration get() = appGraph.workConfiguration

    override fun onCreate() {
        super.onCreate()
        // 1. Crash records. The isolated :local_pdf process cannot use filesDir, so this is guarded exactly as the
        //    Flutter OcApplication guards it today (try/catch Throwable) and skipped outside the main process.
        val main = isMainProcess()
        if (main) try { CrashHandler.install(NativeCrashStore(filesDir)) } catch (_: Throwable) { }
        if (!main) return                                          //    :local_pdf stops here: no graph, no migration,
                                                                   //    no secrets; LocalPdfService never reads appGraph
        val redactor = Redactor()                                  // 2. before any secret is read
        val logRing = LogRing(maxEntries = 128, maxBytes = 262_144)                 // :core:common (§10.1)
        AppLog.install(RedactingWriter(redactor, listOfNotNull(logRing, LogcatSink.ifDebuggable(this))), Severity.Info)
        val launch = CrashLoopGuard(filesDir).onLaunch()           // 3. three crashes within 10 s → LaunchMode.SafeMode
                                                                   //    (reads one ≤ 4 KiB file: the only main-thread read)
        appGraph = createGraphFactory<AndroidAppGraph.Factory>()   // 4. stores are lazy, wait for the StartupFence and
            .create(this, AppBuildInfo.fromBuildConfig(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.FLAVOR),
                redactor, logRing, launch, ProcessStart())         //    MigrationGate
        appGraph.startupFence.start()                              // 5. ONE IO thread, never main (05 §7.1): finish an
                                                                   //    interrupted restore → S0 backup (first native
                                                                   //    launch) → app-locale mirror → MigrationRunner;
                                                                   //    gate opens at once if native.migration.v1
        // 6. warm the prefs file on IO after the fence (`appGraph.prefsWarmup.start()`; from slice 1)
    }
}
```

`onCreate` budget: under 50 ms on **every** path, the first native launch included (ADR §9.7): it copies, renames and
parses nothing. S0 is timed in P5 on the fence thread, and J8b (05 §10.5) runs a 64 MiB prefs file under main-thread
StrictMode `penaltyDeath`. Homes of the helpers (slice-0 homes
in §13.2): `NativeCrashStore`, `CrashHandler`, `CrashLoopGuard` → `:androidApp` in slice 0, `:android:platform` from
slice 2; `EntryIntentReader` → `:android:platform` (07 §7.1); `FlutterBackup`, `MigrationRunner` → `:android:migration`;
`LogRing`, `LogcatSink` → `:core:common`; `isMainProcess()` → `:androidApp` (compares `Application.getProcessName()` on
API 28+, `/proc/self/cmdline` below).

`MainActivity` (`ComponentActivity`, frozen FQCN, `singleTop`, `taskAffinity=""`, same intent filters; 07 §7.1 owns
the capture logic and this copy follows it):

```kotlin
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(LocalizedContext.wrapBelow33(base))  // S21
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = appGraph
        graph.processStart.record(Ui)
        // A restored activity (configuration change, process death, launch from Recents) gets its ORIGINAL intent
        // again: never replay it (B-NAV-005 "exactly once").
        val fromHistory = intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
        if (savedInstanceState == null && !fromHistory) capture(intent)
        val platform = graph.kitPlatformFactory.create(this)
        setContent { App(graph.shell, platform, graph.metroViewModelFactory) }   // App() is :ui:shell commonMain
    }
    override fun onNewIntent(intent: Intent) {                       // always a fresh request
        super.onNewIntent(intent)
        capture(intent)
    }
    private fun capture(intent: Intent) {
        val request = EntryIntentReader.read(intent)     // :android:platform: whitelists and bounds every field
        EntryIntentReader.strip(intent)                  // removes our extras, sets action MAIN, data null
        setIntent(intent)                                // a later recreation cannot replay it
        request?.let { appGraph.entryRouter.submit(it) }
    }
}
// AndroidActivityResults / AndroidPermissionGate attach from the host activity's onResume and detach in onPause
// (resumed host wins, §15.3), registered once per activity from onCreate.
```

```kotlin
// :ui:shell commonMain — the root both apps call
@Composable
public fun App(deps: ShellDependencies, platform: KitPlatform, viewModels: MetroViewModelFactory) {
    ShellRoot(deps, platform, viewModels) {
        val world by deps.world.state.collectAsStateWithLifecycle()      // 07's ShellWorld: bootstrap + gate + safe mode
        when (val b = world.boot) {
            is BootState.Opening -> ShellOpening(b)          // "Updating your app…" / "Opening…"; Try again after 8 s
            is BootState.SafeMode -> ShellSafeMode(deps)     // Report a problem, Settings, Start fresh
            is BootState.Ready -> NavShell(start = b.root, deps = deps)
        }
    }
}

/** Locals both roots share: platform, ViewModel factory, KitTheme (06 §5.1 signature) and the resource locale. */
@Composable
internal fun ShellRoot(deps: ShellDependencies, platform: KitPlatform, viewModels: MetroViewModelFactory,
                       content: @Composable () -> Unit) {
    val appearance by deps.appearance.collectAsStateWithLifecycle()
    val locale by deps.locale.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalMetroViewModelFactory provides viewModels) {
        ShellResourceLocale(locale) {                                     // §12.3 (P6 fallback)
            KitTheme(appearance = appearance.toKitAppearance(), platform = platform, localeTag = locale.tag, content = content)
        }
    }
}

/**
 * The root of the two secondary activities (ManageSpaceActivity, WidgetOverlayActivity; WidgetConfigureActivity and
 * WidgetRouteActivity are trampolines into the overlay, 07 §2.2). Same locals and theme as App(), the migration gate
 * wait ("Updating your app…"), and a FIXED single-entry back stack for [start] (a ManageSpaceRoute or WidgetPanel key,
 * installed by the owning feature's EntryInstaller). It runs NO Bootstrap, no EntryRouter, opens no connection and
 * never starts the phone server: Manage space must work while the app is otherwise not running (11 §3).
 */
@Composable
public fun StandaloneRoot(start: AppRoute, deps: ShellDependencies, platform: KitPlatform, viewModels: MetroViewModelFactory,
                          onFinish: () -> Unit)
```

```kotlin
// :androidApp — the shell of the overlay (the Manage space shell is the same ten lines with ManageSpaceRoute.Home,
// excludeFromRecents in the manifest, and no FLAG_SECURE)
class WidgetOverlayActivity : ComponentActivity() {                 // manifest: transparent theme, same configChanges
    override fun attachBaseContext(base: Context) = super.attachBaseContext(LocalizedContext.wrapBelow33(base))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)      // as today (11 §3)
        val graph = appGraph.also { it.processStart.record(Widget) }
        val panel = graph.widgetBodies.overlayPanelFor(intent) ?: return finish()   // routing identity bound natively
        val platform = graph.kitPlatformFactory.create(this)
        setContent { StandaloneRoot(panel, graph.shell, platform, graph.metroViewModelFactory, onFinish = ::finish) }
    }
}
```

### 21.2 `:desktopApp`

Slice 0: a window that proves the desktop target runs; slice 1 onward: the kit gallery (every kit part with fake data,
light/dark, LTR/RTL, text scale), used by agents as an emulator-free look loop. The desktop product shell arrives only
in the desktop phase (ADR §1).

```kotlin
fun main() = application {
    val graph = createGraphFactory<DesktopAppGraph.Factory>().create(AppBuildInfo.desktop(), Redactor(), LogRing())
    Window(onCloseRequest = ::exitApplication, title = "OpenCode kit gallery") {
        CompositionLocalProvider(LocalKitPlatform provides DesktopKitPlatform()) { KitGallery() }   // gallery/ package here
    }
}
```

Compose Hot Reload (`org.jetbrains.compose.hot-reload` 1.2.0) may be applied here for a trial; it is never a required
step (ADR §4 row 16).

### 21.3 `:quality:rules` and `:quality:graph` (S22)

- `:quality:rules`: `plugins { id("oc.jvm.library") }`, `testImplementation(libs.konsist)`, **no project
  dependencies**. Konsist and text scans read the source tree, so they need nothing compiled. Holds the Konsist rules
  (§19.4) and the file-level gates of §19.6 (`ModuleOwnersTest`, `ModuleNameTest`, `ComponentFreezeTest`,
  `CatalogRatchetTest`, `CatalogDriftTest`, `KitCapacityTest`, `ThirdPartyHashTest`, `CopiedKotlinExemptionsTest`).
  Run with `./gradlew :quality:rules:test` — T0 for any agent, always in T2.
- `:quality:graph`: `plugins { id("oc.jvm.library") }`, `testImplementation(project(":desktopApp"))`. Holds only the
  desktop graph smoke (§13.8). Runs at T2 on the VPS only; compiling it compiles the whole desktop app.
- The Android graph smoke lives in `:androidApp/src/test` (it needs Robolectric's `Application`), T2 on the VPS.

### 21.4 `:tools:strings`

A JVM CLI (`application` plugin, main `io.github.eslamasabry.oc.tools.strings.MainKt`). Its build file sets
`tasks.named<JavaExec>("run") { workingDir = rootDir }`, so every relative path below is relative to `native/` (the
default working directory would be `native/tools/strings`, where `../lib/l10n` reads nothing and output lands in the
wrong place). `../lib/l10n` is the second allowed reference outside `native/` (§1.1).

- `./gradlew :tools:strings:run --args="convert --arb ../lib/l10n --out ui/words/src/commonMain/composeResources --map config/strings-feature-map.txt"`:
  the one-off ARB → CMP XML conversion (ICU plurals → `<plurals>`, `{name}` → `%1$s`, the 6 ICU `select` messages
  reported for hand-porting into code, the 1,018 orphan Arabic keys dropped; ADR §7.5). `config/strings-feature-map.txt`
  maps ARB key prefixes to `strings_<feature>.xml`.
- `./gradlew :tools:strings:run --args="check"`: the drift check behind `StringDriftTest`.

After the conversion commit the ARB files are frozen with Flutter; the XML is the only source.

---

## 22. Slice 0: the skeleton, the spikes, the freeze

### 22.1 Mainline modules at the end of slice 0 (ADR §5.2 "Starts 0")

| Module | Files that must exist | Tests that must pass |
|---|---|---|
| (root) | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `version.properties` (`3.0.0-dev`, `10000`), catalog, wrapper (9.7.1 + SHA-256), `AGENTS.md`, `MODULE_OWNERS`, `JETPACK_AHEAD.md` (empty table), `config/{compose-stability.conf, detekt.yml, kit-capacity.json, freeze-pending.txt, freeze-baseline.txt, copied-kotlin-exemptions.txt (empty), catalog-coverage.txt}`, `catalog/behaviors.jsonl`, `contracts/slice-1.md` (§0.3 reconciliation), `tool/format-changed.sh`, `tool/qa/detekt.sh`, `tool/native/kotlin-drift.sh`; the one-line header in the repository-root `AGENTS.md` (§1.1) | — |
| `build-logic` | all seven plugins, `ModuleRules.kt`, `ExternalRules.kt`, `EnforceRules.kt` | `ModuleRulesTest` (one allowed + one denied vector per §6.1 row) |
| `:core:common` | §10.1 + §18 types (incl. ids, `ConversationRef`, `LogRing`, `ApprovedLink`, `ServerOrigin`, `Formats`, byte primitives), `actual`s for android, desktop **and ios** | `attempt` rethrows cancellation; `AppLog` pre-install buffer is flushed through the redacting root; `WireJson` decode errors never contain the input; `Sha256`/HMAC NIST vectors; Dart vectors (05 §2.5) |
| `:core:security` | §10.2 | `Redactor` vectors (B-SEC rows), `ExternalLinkPolicy` vectors, `AddressRules` vectors incl. `100.64.0.0/10` and `*.ts.net` |
| `:core:model` | the types slices 1–3 use (§10.3), the full `ServerProfile` | serialization round-trips; the 05 fingerprint vector test (a parsed Dart profile gives the Dart `profileFingerprint`); unknown profile keys survive a write |
| `:core:domain` | §10.4 with full method lists for the facets slices 1–3 use; all 85 `ServerCapabilities` flags | defaults equal the Dart defaults (generated vector file) |
| `:core:testing` | `TestDispatchers`, `asClock()`, `FakeServerGateway`, `FakeOperationsGateway`, `WireFixtures`, `catalogRow()` | self-tests of the fakes |
| `:ui:routes` | 07 §2's shapes (`AppRoute`, `RouteMeta`, `AppNavigator`, arrivals, `SearchEntry`, `CapabilityFlow`), `RouteSaving`, `EntryInstaller`, route files for slices 1–3 | every route round-trips; route parameters are ids/enums/booleans only |
| `:android:migration` | the P5 spike: `FlutterBackup` (step 0), `SecureStorageV11Reader` (decrypt one entry), `FlutterPrefsReader` | P5 on the emulator (ADR §11) |
| `:android:res` | `values*/strings_os.xml` with at least `app_label` and `app_label_preview` (the manifest needs them) | `MissingTranslation` lint |
| `:androidApp` | `OcApplication` (steps 1–6 with the slice-0 homes of §13.2; step 7 from slice 1), `Configuration.Provider` + the removed WorkManager initializer, `MainActivity` showing a plain "Opening…" placeholder, manifest with only the components already built, the slice-0 graph accessors (§13.2) | `assembleStableRelease` and `assemblePreviewRelease` compile; `ComponentFreezeTest` passes with `freeze-pending.txt` listing every component not yet built |
| `:desktopApp` | `main()` with an empty gallery window | `:desktopApp:run` opens (manual, PC) |
| `:quality:rules`, `:quality:graph` | §19.4 rules, §19.6 gates (those whose inputs exist) | all green; `KitOnlyRuleTest` passes vacuously on mainline |

### 22.2 Proof branches (never merged; findings move into slice-1 briefs)

Each proof runs on its own branch from the slice-0 mainline. A proof branch may add `"spike"` to `layerFolders` in
`settings.gradle.kts` and create `native/spike/<name>` modules; the branch is never merged. What a proof learns is
written to `native/docs/spikes/P<N>.md` on mainline, and reusable code is re-created in its real module by the slice
that starts that module (ADR "slice briefs name what they delete").

| Branch | Temporary modules | ADR proof | Also settles (this section) |
|---|---|---|---|
| `spike/p1-skeleton` | `spike/graph` (a `ConnectionGraph` extension, two ViewModels, two entry installers, the shell's `NavShell`) | P1 | P-03-1, P-03-3, P-03-5, P-03-8 |
| `spike/p2-predictive-back` | `spike/nav` (list + chat screens with `sharedBounds`) | P2 | the §15.4 `NavDisplay` parameter names |
| `spike/p3-transcript` | `spike/transcript` + `:android:benchmark` | P3, P4 | — |
| `spike/p5-identity` | stub classes for every frozen component in `:androidApp` | P5 | — |
| `spike/p6-strings` | `spike/words` + an early `:tools:strings` | P6 | P-03-9 |
| `spike/p7-kit-seams` | `spike/kit` (KitTappable, KitFrameText, KitSheetHost, frost) + an early `:ui:testing` | P7 | P-03-4 |
| `spike/p9-allowlist` | `feature/spike` with planted `material3`, `LocalContext`, raw `clickable`; `spike/terminal` rendering a Termux `TerminalView` through `PlatformViewHandle` | P9 | P-03-6 |
| (no branch) | measurements on mainline + `spike/p1` | P8, P10 | P-03-2 (detekt) |

### 22.3 This section's own unknowns

| # | Question | Settled by | Fallback |
|---|---|---|---|
| P-03-1 | The AGP-KMP `android {}` accessor from a binary convention plugin (`(this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>` on the kotlin extension), `withHostTest` and its task name (`testAndroidHostTest`) on AGP 9.3.3; one host test runs on the JDK 21 launcher at the default SDK | P1 | configure the block in each module's build script through a shared `fun KotlinMultiplatformExtension.ocAndroid(path)` helper |
| P-03-2 | detekt 1.23.8 parses Kotlin 2.4 syntax (explicit backing fields, context parameters) | half a day in slice 0 | detekt 2.0 alpha on the VPS only, or no detekt (Konsist + compiler carry the rules) |
| P-03-3 | Metro 1.5.1: `@Provides` parameters on `@GraphExtension.Factory`; contributed interfaces merged into graph extensions; `@Binds` extension-property syntax; `ContributesIntoSet(binding = binding<…>())`; a ViewModel with two supertypes contributed with `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` (and failing FIR without it); one class carrying both `@ContributesBinding` and `@ContributesIntoSet`; plus Kotlin accepting `@RequiresOptIn` on a value-class constructor | P1 | `@Provides` functions on a contributed interface instead of factory parameters; an internal `RedactedFactory` object instead of the opt-in constructor |
| P-03-4 | Roborazzi record/verify task names for AGP-KMP host tests and for desktop | P7 | record through Robolectric only (ADR P7 fallback) |
| P-03-5 | `subclassesOfSealed<AppRoute>()` registers nested sealed interfaces declared across files; wrapping `NavEntry` in `withRouteMotion` keeps decorators and ViewModel stores working | P1 | an explicit `polymorphic { subclass(…) }` block plus Konsist K8 as the ADR wrote it; per-entry `metadata =` in installers |
| P-03-6 | Konsist 0.18.1 sees `src/commonMain` of nested KMP modules via `scopeFromDirectory`, and its `imports`/`packagee` API | P9 | a plain-text import scanner in `:quality:rules` (regex over `^import `) |
| P-03-7 | `limitedParallelism(1, name)` and `kotlin.concurrent.atomics` need no extra opt-in on coroutines 1.11 / Kotlin 2.4.21 | P1 compile | add the opt-in marker to §6.4's list |
| P-03-8 | `compilerOptions.moduleName` set per compilation reaches the KMP Android and JVM compilations (two projects with one leaf name produce two distinct `META-INF/*.kotlin_module` in the APK) | P1 | `freeCompilerArgs += listOf("-module-name", …)` per compilation |
| P-03-9 | Per-app language without AppCompat (S21): `LocaleManager` on API 33+, `attachBaseContext` + `LocalizedContext` + the CMP resource-environment override on API 30; a notification built in a service follows the app language | P6, on an API 30 AVD and API 36/37 | add `androidx.appcompat` and make the activities `AppCompatActivity` (the other option of S21) |

**Status on 2026-11-01** (slice-0 reports on `native/m0-2026-11-01`): P-03-1, P-03-3 (except `@Binds` properties,
unused), P-03-5, P-03-6 and P-03-7 settled by P1 as written above; a new Metro finding is that a `@DependencyGraph` or
`@GraphExtension.Factory` cannot have a `companion object`. P-03-4 settled by P7 (`recordRoborazziAndroidHostTest`,
`verifyRoborazziAndroidHostTest`, `compareRoborazziAndroidHostTest`, and the `…Desktop` twins). P-03-2, P-03-8 and
P-03-9 are still open (P-03-9 needs the emulator part of P6).

### 22.5 Slice-0 learnings folded into this section (2026-11-01)

| Source | Learning | Applied in |
|---|---|---|
| P1 D1 | Gradle and `build-logic` need JDK 21 (Metro's Gradle plugin is JVM 21); modules stay on toolchain 17 | §6.2 |
| P1 audit fix | compileSdk is API 37 **minor 1** through `compileSdk { version = release(37) { minorApiLevel = 1 } }`; without it `checkAarMetadata` rejects the Compose 1.13 AARs | §6.3, §6.4, §21.1 |
| P1 D5 | coroutines and immutable collections are `api`, because their types are in public signatures | §6.4 |
| P1 D9 | `ExternalRules` fires at configuration resolution, not at `help` | §23 #4 |
| P1 D11 | the root build lists only plugins in use; each plugin joins with the slice that applies it | §3.2 (apply as slices land) |
| P1 D8 | desktop slice 0 runs the shared `App()` shell (it proves the desktop graph and Nav3 at run time); the gallery replaces it in slice 1 | §21.2 |
| P1 MAJOR 12 | `compose-stability.conf` lists audited value types only | §19.2 |
| P2 §7.7, P7 D12 | Robolectric 4.17 on JDK 21 needs `--add-exports/--add-opens java.base/jdk.internal.access` (and `java.io`, `java.lang`) on AGP-KMP host tests | §6.4 |
| P2 D9 | Compose UI tests live in `desktopTest`, not `commonTest` (AGP-KMP feeds `commonTest` into `androidHostTest`, where Compose UI needs Robolectric) | §8.2, §17.6 |
| P3 D7, P3 open 5 | Konsist K1 scans feature test sources too, so headless UI tests of features live in `:quality` or `:ui:testing`; the `:quality` owner decides whether to narrow K1 to main sources | §19.4 (decision for the `:quality` owner) |
| P3 D11 | a large `StateFlow` state puts a `revision` first so `equals` short-circuits; rows are reused | §19.2, ADR §9.3 |
| all reports | Maven Central answered HTTP 429 to cloud containers; each used a local-only init script with Google's Central mirror. Not committed; the cloud recipe (15 §2.1.1) installs it | 15 §2.1.1 |
| P1 §4 | at slice-0 size desktop adds about 14 % to a clean Android build; incremental edits cost 4–7 s with or without desktop; the Gradle daemon reaches its 2 GiB cap (measured on a 4-vCPU cloud container, not the PC or VPS) | P8 still owes PC and VPS numbers (§23 #14) |

### 22.4 What freezes at the end of slice 0 (ADR §10.3), and where it lives

Every row's **shape** comes from the owner in §0.3; this table only says where the frozen file lives.

| Frozen item | Owner (§0.3) | File(s) |
|---|---|---|
| `:core:model` types (full `ServerProfile`, `BackendKind`) and the ids, `ConversationRef`, `ServerOrigin` in `:core:common` | 03 + 07 (ref shape) + 05 (profile JSON) | `core/model/src/commonMain/**`, `core/common/.../Ids.kt` |
| `Outcome`, the flat `Failure` list with the amendments of 04, 05, 10, 11, `RefusalReason` values | 03 | `core/common/.../Outcome.kt`, `Failure.kt` |
| Gateway interfaces, `GatewayFactory`, `ServerCapabilities`, `ServerEvent`, `StreamStatus`, `NetworkStatus` | 04 | `core/domain/src/commonMain/**`, `core/model/…` |
| Shared platform services (`ActivityResults`, `PermissionGate`, `ForegroundWork`, `DeviceFacts`, `KeepAliveSettings`, `TailscaleApp`, `OsSettingsPages`, `WifiShareHost`, `StartupTask`, `AttachmentLimits`; `Formats` in `:core:common`) | 03 (homes) + 10, 11 (members) | `core/domain/src/commonMain/**` |
| Store interfaces, `MigrationGate`, `ServerProfileJson`, `ProfileFingerprintV1` | 05 | written in slice 0 inside `contracts/slice-1.md`, created with `:core:data` in slice 1 |
| `WriterLane`, `WriterLanes`, `ProfileScope`, `ProfileResourceCleaner`, the deletion signature | 03 | same |
| `AppDispatchers`, `Clock`, `Trace`, byte primitives | 03 | `core/common/.../Dispatchers.kt`, `CommonBindings.kt`, `Bytes.kt` |
| Route keys for slices 1–3, `RouteMeta`, `AppNavigator`, arrivals, `EntryRequest`/`EntryTarget` | 07 | `ui/routes/src/commonMain/**`; the entry types in `contracts/slice-1.md` until `:engine:entry` exists |
| `KitTheme`, `KitAppearance`, `KitPlatform`, `KitContentSlots` + slot params, the first 20 kit part signatures | 06 | `contracts/slice-1.md` (from `docs/ux-system/kit-api/*.md`), created with `:ui:kit` in slice 1 |
| Module rules and engine order | 03 (mirrors ADR §6.1/§6.2) | `build-logic/.../ModuleRules.kt` |

---

## 23. Acceptance checks for this section

The coordinator runs these on the slice-0 mainline (VPS unless noted) before any slice-1 agent starts. Each must give
the stated result.

**One checklist feeds every milestone gate (Astra M6).** This table is the machine-readable source
(`native/config/gates.tsv`: check id, owner section, gate, evidence path). 15 §4 M0 and every later milestone exit are
generated from it, never retyped. M0 requires checks **1–15 and 17**; check 16 has two rows: its harness (recorded
at M0) and its implementation proof (gate M4, when the receivers exist). A milestone cannot be marked passed while a
row assigned to it has no evidence path.

| # | Command | Expected |
|---|---|---|
| 1 | `cd native && ./gradlew -p build-logic test` | `ModuleRulesTest` passes |
| 2 | `./gradlew projects` | the project list equals the `MODULE_OWNERS` rows; `ModuleOwnersTest` agrees |
| 3 | add `implementation(project(":core:net"))` to `:core:domain`, run `./gradlew help` | configuration fails with "Module rule: :core:domain may not depend on :core:net (ADR §6.1/§6.2)…"; revert |
| 4 | add `implementation(libs.compose.material3)` to `:ui:routes`, run `./gradlew :ui:routes:compileKotlinDesktop` | fails with the `ExternalRules` message; revert. (Not at `help`: catalog dependencies in KMP `sourceSets {}` are added lazily, so the check fires when the configuration resolves [V slice-0 P1 D9]. Project dependencies, check 3, still fail at `help`.) |
| 5 | `./gradlew :core:common:desktopTest :core:common:testAndroidHostTest :core:security:desktopTest :core:domain:desktopTest :ui:routes:desktopTest` | green; test names carry catalog ids |
| 6 | `./gradlew -Poc.ios=true :core:common:compileKotlinIosArm64 :core:model:compileKotlinIosArm64 :core:domain:compileKotlinIosArm64` | green (nightly job wiring proven) |
| 7 | `./gradlew -Poc.debugSignRelease=true :androidApp:assemblePreviewRelease` (debug key; this APK is never delivered) | an APK with package `io.github.eslamasabry.opencode_mobile.native`, `versionCode` ≥ 10000 (`aapt2 dump badging`) |
| 8 | `./gradlew :androidApp:assembleStableRelease` without `key.properties` | fails only at `validateSigningStableRelease` |
| 9 | `./gradlew :quality:rules:test :quality:graph:test` | green, including `ComponentFreezeTest` (with the pending list), `ModuleNameTest`, `CatalogDriftTest` and `ThirdPartyHashTest`; `:quality:rules:test` alone compiles no project module |
| 10 | `tool/format-changed.sh --check` on the slice-0 commits | no changes needed |
| 11 | `tool/qa/detekt.sh` | green, or P-03-2's fallback recorded |
| 12 | PC, one Gradle build: `./gradlew :desktopApp:run` | an empty gallery window opens |
| 13 | proof branches P1–P9 | each meets its ADR §11 acceptance; findings in `native/docs/spikes/P<N>.md` |
| 14 | P8 numbers | clean and incremental times recorded for PC and VPS; the per-change vs nightly desktop compile rule (ADR §4 row 17) written into `native/AGENTS.md` |
| 15 | compile `contracts/slice-1.md`'s interfaces **with representative consumers** (the M1 census and deletion transaction over `ProfileScope`, the schedule CAS over `KeyValueStore`, one engine through `GenerationRunner`, one ViewModel) in a scratch module on the VPS | green; every type has exactly one shape and one owner (§0.3); zero open reconciliation notes (a listed difference is a failure, not a pass) |
| 16 | emulator `emulator-5584` (PC): install the baseline Flutter APK with a running phone server and an armed schedule, then `adb install -r` the native build **without opening the app** | `MY_PACKAGE_REPLACED` restores the phone server inside the broadcast window, alarms are re-armed, no ANR, no `ForegroundServiceStartNotAllowedException`; pending WorkManager jobs run after the graph exists (from slice 4, when the receivers exist; slice 0 records the harness) |
| 17 | `aapt2 dump xmltree --file AndroidManifest.xml` on the check-7 APK | `allowBackup=false`, `dataExtractionRules` and `fullBackupContent` present, `LocalPdfService` has `isolatedProcess=true` and `process=:local_pdf` (once built), no `WorkManagerInitializer` meta-data under `InitializationProvider` |

---

## 24. Owner decision raised here

| # | Decision | Recommended default | What changes if you say no |
|---|---|---|---|
| D03-1 | Where the native code lives: a `native/` folder in the existing `oc_app` repository, beside the frozen Flutter app (removed at switch+1). | **Yes, `native/` in `oc_app`**: one history, the copied Kotlin keeps its blame, agents keep the same worktree habit, CI and docs stay in one place. | A new repository (`oc-native`) with the same tree at its root; every path in this section loses the `native/` prefix; the Flutter-Kotlin copy and drift script work across two repositories. |

Every other choice here is technical and reversible inside slice 0 (§0.2, §22.3).

---

## 25. Sources

- Binding decision: `/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md` (§1–§12).
- Research: `../research/architecture-stacks.md` (§3 DI, §4 state, §5 navigation, §7 errors, §8 concurrency, §9 logging,
  §12 sketches), `../research/kmp-cmp-status.md` (§4, §5, §6, §8, §11, §12), `../research/network-data.md` (§0, §2.2,
  §3.3, §4.3, §6.2, §7.2), `../research/testing-perf.md` (§0, §3.2, §5.4, §9.4, §10.1, §11), `../research/motion-native.md`
  (§8, §9.1, §10, §11), `../research/rendering-chat.md` (§0, §11), `../research/genui.md` (§0).
- Maps: `../map/protocols.md` (PROTOCOLS-36–46), `../map/state-data.md` (§1, §14), `../map/screens-nav.md` (§2, §4),
  `../map/android-platform.md` (§3, §16), `../map/builtin-linux.md` (§12, §13), `../map/ui-kit.md` (§0, §1),
  `../map/media-voice.md` (§13.1), `../map/desktop-web-cross.md` (§9.3), `../map/packages-build.md` (§11, §12).
- Catalog rows cited: B-STATE-002, -004, -005, -013, -042; B-PROTO-033; B-SEC-043; B-NAV-005, -009, -030;
  B-CHAT-051; B-XPLAT-012, -013 (`../catalog/BEHAVIORS.md`).
- Flutter source read (read-only): `lib/domain/server_gateway/interfaces.dart`, `lib/domain/server_gateway/value_types_core.dart`,
  `lib/domain/product_failure.dart`, `lib/platform/platform_capabilities.dart`, `lib/diagnostics/{app_diagnostics,
  crash_diagnostics,report_problem,perf_trace,failed_job_report}.dart`, `android/app/build.gradle.kts` (168 Kotlin
  files in one package; 26 reference `R`; Termux JitPack exclusive repository; `useLegacyPackaging`).
- Demo: `/home/eslam/Storage/Code/kmp-demo` (`settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`,
  `shared/build.gradle.kts`, `androidApp/build.gradle.kts`).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `pc-capacity-ceiling`, `agent-format-churn`,
  `codex-backend-claude-frontend`, `no-raw-errors-rule`, `design-standard-rule`, `component-max-width-rule`,
  `text-scale-cap`, `android-native-feel`, `no-glass-rule`, `phone-logcat-silenced`, `voice-model-memory-gate`.
- Checked today [V], 2026-11-01:
  - Maven Central / Google Maven metadata and POMs for every catalog coordinate in §5 (e.g. `dev.zacsweers.metro:runtime`
    and `metrox-viewmodel-compose` 1.5.1; `androidx.room3:room3-runtime` 3.0.3 (latest 3.1.0-alpha03);
    `androidx.navigation3:navigation3-runtime` 1.2.0 with `-desktop` variants on Google Maven; CMP `ui-test-junit4`
    1.13.0-beta01 exists, CMP `ui-test-manifest` does not; `com.squareup.okhttp3:mockwebserver3` 5.5.1; Metro Gradle
    plugin marker → `dev.zacsweers.metro:gradle-plugin`; ktfmt 0.64; foojay resolver 1.0.0; androidx.test.ext:junit
    1.3.0; androidx.test:runner 1.7.0).
  - Metro sources (`metrox-viewmodel-compose-1.5.1-sources.jar`, `metrox-viewmodel-1.5.1-sources.jar`): `metroViewModel`,
    `assistedMetroViewModel`, `LocalMetroViewModelFactory`, `ViewModelGraph.metroViewModelFactory`,
    `MetroViewModelFactory` provider maps, `@ViewModelKey` (implicit class key), `@ManualViewModelAssistedFactoryKey`,
    `ManualViewModelAssistedFactory`; Metro runtime jar contains `dev.zacsweers.metro.AppScope`.
  - Metro docs: https://zacsweers.github.io/metro/latest/dependency-graphs/ , /aggregation/ , /installation/ (plugin id
    `dev.zacsweers.metro`), /metrox-viewmodel/.
  - Navigation 3 source, `navigation3-runtime/src/commonMain/kotlin/androidx/navigation3/runtime/EntryProvider.kt`
    (androidx-main): `public class EntryProviderScope<T : Any>`, `entry<K>(clazzContentKey, metadata, content)`.
  - Android KMP library plugin page: https://developer.android.com/kotlin/multiplatform/plugin (`android {}` replaces
    `androidLibrary {}` from AGP 8.12; `withHostTest { isIncludeAndroidResources }`, `withDeviceTest`,
    `androidResources { enable = true }`, `optimization { consumerKeepRules … publish = true }`).
  - Konsist scope API: https://docs.konsist.lemonappdev.com/writing-tests/koscope (`scopeFromDirectory`,
    `scopeFromProject(moduleName, sourceSetName)`).
  - Metro aggregation docs (https://zacsweers.github.io/metro/latest/aggregation/), re-read for the review: the bound
    type is inferred only for a single supertype; otherwise `binding = binding<T>()`.
  - Maven Central listing of `com/facebook/ktfmt/0.64/`: `ktfmt-0.64-with-dependencies.jar` and its `.sha256`.

---

## Review notes (2026-11-01)

Each review issue was checked against this file, the other plan sections (04, 05, 06, 07, 10, 11 and 16 as they
stand today; 04, 05, 06 and 07 were being revised in parallel), the ADR, the Flutter source in `oc_app-batch1` and,
where version-specific, the web. All 58 issues were real; none was rejected outright. Where the fix differs from the one
suggested, the reason is given.

**Contract ownership**
- Conflicting frozen seams (03 vs 04/05/06/07): fixed. New §0.3 names one owner per contract plus a reconciliation step
  and acceptance check 15. 03's copies of gateways (§10.4), stores (§10.5), transport (§10.7), `KitPlatform`/slots
  (§12.2), routes and `AppNavigator` (§15.1–§15.3) and entry types (§15.8) became references. Changed from the
  suggestion: revised 04 now adopts 03's flat `Failure` and `BackendKind { OpenCode2, … }`, so those stay 03-owned,
  with 04 owning the transport detail fields and gateway members. The ADR §10.3 copy of the table is the coordinator's
  edit.
- Missing `Failure` subtypes: fixed (§10.1). Added `StorageRefused`, `SecretUnavailable`, `Media`, `Runtime`,
  `Incompatible`, and 04's detail fields and refusal reasons. There is no `Net` sub-hierarchy, because 04 dropped it.

**Module boundaries**
- `ServerOrigin` used from `:core:security`: fixed. It moved to `:core:common` (S24). The ids stay in `:core:common`,
  differing from 07 §2.1, because `Failure` names `RequestId`.
- `ApprovedLink` was unreachable: fixed. The type is in `:core:common` behind `@LinkPolicyApi` (S7, K13b), and the
  web-preview slot takes no policy type (§12.2).
- `TransportAuth` read `SecretStore` from `:core:net`: fixed by using 04's model instead of a new seam. Credentials
  arrive already resolved in `GatewaySpec` through `ProfileSource` (§10.7).
- Engine order vs the §11.2 entry points: fixed. `AttachmentLimits` moved to `:core:domain`. Integrations pushes into
  attention's `UsageFacts`/`QuotaAlertSink`. `SignInResetParticipant` was added. `ModuleRulesTest` gained vectors for
  all three (§6.6).
- Homes of shared types: fixed (§13.5 type-home column). `StartupTask` is in `:core:domain`. `SearchEntry` and
  `CapabilityFlow` are in `:ui:routes`, where 07 puts them (not `:core:domain` as suggested). The `EntryTarget` enums
  are in model/entry.
- Analytics home: fixed (S27, §11.2). It is a package in `:engine:diagnostics`, per 16, with a single
  `AnalyticsSender` binding as 16 specifies, not a `Set`. 15 C15-9 is listed for reconciliation.

**Build and tooling**
- Metro ViewModel binding: fixed (§13.1 rule 7, §13.7, template, P-03-3).
- K1 scanned test files: fixed. Production and test scopes are separate, the test toolkit and coroutine names are
  allowed, and P9 plants a test.
- Robolectric on JDK 21 in KMP modules: fixed (§6.4 launcher, P-03-1).
- `:quality` compiled the app: fixed. It is split into `:quality:rules` and `:quality:graph` (S22, §8.3, §20.1, §21.3,
  check 9).
- `coil-network-ktor3`: fixed. Removed, and banned in `ExternalRules`.
- iOS actuals: fixed. `iosMain` actuals exist from slice 0 and K10 allows them.
- Single-expect wording: fixed (S25). The byte primitives are `expect`s and `Trace` comes from DI.
- Duplicate leaf names: fixed. An explicit module name is set (S23, `ModuleNameTest`, P-03-8). AGP's packaging
  behaviour is not proven, but the fix is cheap and harmless.
- `extensions` resolved to the wrong receiver: fixed with an `ExtensionAware` cast in §6.4 and §6.5.
- ktfmt jar name: fixed, with the SHA-256 pinned [V].
- `:tools:strings` working directory: fixed (`workingDir = rootDir`). The ARB path is now a second allowed outside
  reference.
- Missing declarations: fixed. Added kotlinx-io, savedstate, kotlin-reflect, the Roborazzi plugin, `KitTheme` via
  `KitAppearance`, the companions, `metadataSources`, plugin-made benchmark types, `testBuildType`, and
  `-Poc.debugSignRelease`. No components-resources dependency was added to `:android:*`, because OS copy now comes from
  Android resources.
- Root `AGENTS.md` and the removal point: fixed. A header line was added, and the Flutter tree is removed at switch+1
  everywhere.
- Catalog and fixtures outside `native/`: fixed. They are copied in, and `CatalogDriftTest` guards them (§10.8).

**Engines and state**
- Final engine classes vs fakes: fixed (S19, §11.1). Engines are interfaces with `Default…` implementations and fakes
  in each engine's `fake` package (K21).
- `WriterLane` race: fixed with an in-flight count taken before the admission check (§17.3).
- `Failure.copy()` name clash: fixed by renaming to `toFailureCopy()`.
- `PromptDelivery` name clash: fixed. The enum is now `DeliveryMode`.
- `ServerProfile` fields and origin-bound trust: fixed (§10.3). It has every persisted field, the pin and consent carry
  their origin, writes merge unknown keys, and the fingerprint vectors are in the slice-0 gate.
- `BlobArea`: fixed by deferring to 05's `BlobRoot` (separate stash root) and Appendix C, plus `FilesInventoryTest`.
- Registry-only deletion: fixed (§10.5). The generic sweep plus a retained set, registrations that only add,
  `ProfileResourceCleaner`, the ordering, `DeletionOptions`, and "any failure keeps the server".
- `forProfile` was not fail-closed: fixed, with a tombstone set and a late-write test.
- Agent backend: fixed using 04's `openTransient(PhoneAgentsHelper)` and `"<owner>.agents"` ids, not a new
  `ConnectionKey`/`openAgentBackend`, to avoid a fourth shape (§13.4).
- `BlobStore` streaming: fixed by deferring to 05, whose store does streaming reads and verification.
- Locale-aware formatting: fixed. `Formats` is in `:core:common`, so the kit, engines and `:android:*` all see it.

**Android platform**
- Intent replay in `MainActivity`: fixed. It uses 07's guard and `setIntent` strip, and `onNewIntent` stays
  unconditional.
- WorkManager initializer race: fixed (S20). On-demand init through `Configuration.Provider`, with a P5 pending-work
  check.
- AppCompat locale and OS-surface copy: fixed with option (b) (S21, §12.3). `LocaleManager` on API 33+,
  `attachBaseContext` plus `LocalizedContext` below that, `strings_os` read through it, P6/P-03-9 on API 30, and an
  Arabic-notification test. 11 S11-8 is listed for reconciliation.
- Copy recipe vs Konsist rules: fixed (§2.3). Files that import `io.flutter` are rewritten rather than copied, a
  shrink-only exemptions list was added (K22), `org.json` is on the test classpath, and the shared `SharedPreferences`
  instance covers the 10 direct writers.
- Slice-0 stubs: fixed. §13.2 now has a table of slice-0 homes and the slice-0 accessors, and `LogRing` moved to
  `:core:common`.
- Isolated process: fixed. The Flutter try/catch is kept, and `LocalPdfService` is self-contained.
- K5/K15 vs `LogcatSink`/`Redactor`: fixed with named exemptions.
- Resources in `:androidApp`: fixed (S26). A new resources-only `:android:res`, with widget and notification
  resources in their modules.
- FGS shell callbacks: fixed (§13.6). Both `onTimeout` overloads, `onTaskRemoved`, `onTrimMemory` and the sticky mode
  are forwarded, checked by `FgsShellTest`.
- `ForegroundWork`: fixed. Added `SignIn`, the origin, `PauseReceipt`, the typed failures (from 11 §9.2, not a new
  enum), the shared budget, and the 4747 invariant.
- Startup tasks and update-time receivers: fixed. `runsOn` with `ProcessStart`, an 8 s bounded wait with
  `MigrationFollowUpWorker`, an FGS start only from an exempt context, and check 16.
- Secondary activities: fixed with `StandaloneRoot`, which reuses the features' `EntryInstaller`s for 07's
  `ManageSpaceRoute`/`WidgetPanel` keys instead of a new `PanelHost` multibinding (one mechanism fewer), plus the
  overlay shell example.
- `ComponentFreezeTest` scope and baseline: fixed. It diffs the security and behaviour attributes and permissions
  against the exact on-phone baseline APK (`config/freeze-baseline.txt`), plus check 17.
- Missing platform features: fixed. Added `AllFilesAccess`, `KeepAliveSettings`, `TailscaleApp`, `OsSettingsPages`,
  `WifiShareHost`, `recognizeSpeech` and `voiceConversation`, with rows in §12.1.
- `ChatRoute`/`EntryTarget` gaps: fixed by deferring to 07. Its `ChatArrival` and `EntryTarget` already carry these;
  §15.3 records that on the open chat `openChat` posts the new arrival.
- Wide-window Back and multiple `MainActivity` instances: fixed. Back behaviour is 07's, and `ShellNavigator` and the
  activity results use per-activity slots with a two-instance test (§15.3). The launch mode was not frozen instead,
  because 07 and 11 keep today's manifest.
- AppFunctions flag: fixed. The flag and the catalog entry were dropped until the post-switch slice (11 §13).
- `:android:termux` under the D7 default: fixed. It ships at the switch, with an owner row and freeze entries.

**Process**
- Definition of done and evidence: fixed. Added a DoD line and rules 16–18 in §19.7.

## Codex Sol audit follow-up (2026-11-01, second pass)

The Sol audit of 01+03 (`../audit/codex-01-architecture-decision+03-architecture-modules.md`) was re-checked against
the current text. For this file:

| Finding | State |
|---|---|
| 1 BLOCKER, `WriterLane.drain()` race | already fixed (§17.3: count before the admission check; `WriterLaneTest` race vector). ADR §9.5 updated to match |
| 2 BLOCKER, compileSdk 37 vs Compose 1.13 | fixed (§6.3 `COMPILE_MINOR = 1`, the AGP DSL in every convention; proven by P1) |
| 3 BLOCKER, forbidden type edges | already fixed (S24, S7, §10.7). The slice-0 code took the other route; reconciled in §0.3 |
| 4 BLOCKER, two storage contracts | already fixed (§0.3: 05 owns the stores; §10.5 is a summary; `InvalidValue`/`StorageRefused` in the 05 shapes; check 15 compiles `contracts/slice-1.md`) |
| 5 MAJOR, reduced profile model | already fixed (§10.3: every persisted field, origin-bound trust, unknown keys merged; 05 `ProfileFingerprintV1`) |
| 6 MAJOR, cancellation is not identity fencing | fixed (§11.1 `GenerationRunner.command` + `GenerationToken.isCurrent()`, mutex for suspending operations, cancel-and-join on replacement; §17.5 rule 3) |
| 7 MAJOR, WorkManager default initializer | already fixed (S20, §13.6) |
| 8 MAJOR, backup on the main thread | fixed (§13.6, §21.1: `StartupFence` thread; `onCreate` copies nothing; `LocalizedContext` reads a 64-byte mirror, never prefs; 05 §7.1) |
| 9 MAJOR, AppCompat locale with `ComponentActivity` | already fixed (S21, §12.3); ADR §4 row 6 updated |
| 10 MAJOR, test and portability wiring | already fixed for the JDK 21 launcher and iOS actuals; fixed now for Room KSP on iOS targets (§7.2) and the Robolectric JVM flags (§6.4) |
| 11 MAJOR, no public Room surface | fixed (§10.6 neutral row façades + `DbTransactions`, one full repository example with a contract test before fan-out) |
| 12 MAJOR, stability policy | fixed (§19.2 audited value types; judged by frame time, `equals` cost and allocations; no widening fallback; ADR §9.3, §11 P4) |
| 13 MAJOR, alpha fallback vs release policy | fixed (§5 comment; `ReleaseClasspathPolicyTest` on the resolved release classpath, ADR §8 and D12) |
| 14 MINOR, two Flutter removal dates | fixed (§1.1: three separate end dates) |

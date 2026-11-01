# Architecture building blocks for the native rewrite (KMP + Compose)

Topic: `architecture-stacks`. Written 2026-11-01. Every web source was read on 2026-11-01; the list with URLs is at the end.
Companion file: `research/kmp-cmp-status.md` covers platform maturity, Material 3, Gradle/AGP 9 structure and the
toolchain pins. This file does not repeat that material. It covers the building blocks above it: dependency injection,
state management, navigation design, module layers, errors, concurrency and logging.

Labels (same as the companion file):

- **[V]** verified on 2026-11-01 from a primary source: official docs or release notes, Maven Central / Google Maven
  metadata, or the project's own GitHub releases.
- **[S]** secondary source: a blog post, community post or aggregator.
- **[U]** unverified, or my projection. Check it in slice 0 before relying on it.

Internal evidence: the area maps in `/home/eslam/Storage/Code/wt/native-plan/map/*.md`, the behaviour catalog in
`/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl` (1,574 behaviour rows condensed from the 6,663 logic tests),
the demo `/home/eslam/Storage/Code/kmp-demo`, and the Flutter source `/home/eslam/Storage/Code/wt/oc_app-batch1` (read-only).

---

## 0. Bottom line

| Concern | Pick | One-line reason |
|---|---|---|
| DI | **Metro 1.5.x** (compile-time, Kotlin compiler plugin, KMP) | Catches wiring mistakes at build time. Feature modules contribute bindings without editing a shared file, which suits parallel agents in separate worktrees. No KSP. |
| Screen state | **AndroidX ViewModel (KMP, lifecycle 2.11.0) + `StateFlow<UiState>`**, unidirectional data flow | Google's default, the best-known pattern for the coding agents, now on every KMP target. Close to a 1:1 port of today's `ChangeNotifier` controllers. |
| App logic ("engine") | **Plain Kotlin classes** in app- and connection-scoped DI graphs. They expose `StateFlow`, take suspend calls, and keep state machines as sealed types with pure reducers. | Most of this app's work outlives any screen: connections, streams, the offline queue, notifications, widgets, schedules. |
| Navigation | **Navigation 3 1.2.0** with an app-owned `AppNavigator` and one intent router | User-owned back stack, scenes for wide windows, per-entry predictive-back transitions, KMP deep links. Details are in the companion file §4. |
| Modules | `core` → `protocol` → `engine` → `ui:kit` → `feature` → `androidApp`, with Gradle-enforced rules | Mirrors today's `api/`, `domain/`, `state/`, `ui/` boundaries, and the single-owner rule maps onto module ownership. |
| Errors | An in-house `Outcome<T>` + sealed `Failure` (a port of `ProductFailure`). Exceptions only for bugs and cancellation. Plain-words tables live in one UI module. | Keeps the "never raw errors" rule a type-level fact. No Arrow for now. Kotlin rich errors are not stable yet. |
| Concurrency | Structured scopes per DI scope; **each engine confined to one serial dispatcher**; deltas coalesced before publishing | Dart code assumed one thread. Serial confinement keeps that assumption true and removes the generation-counter boilerplate. |
| Logging | **Kermit 2.2.0** with a redacting writer and an in-memory ring buffer for diagnostics. Tracing via `androidx.tracing` 2.0 once stable. | KMP, small, pluggable. Secrets are redacted before any writer sees a line. |
| Not chosen | Koin (plan B), kotlin-inject, Hilt, Circuit, Molecule, Decompose, Orbit/MVIKotlin/Workflow, Arrow, Napier | Reasons in each section. |

The main risk in the stack: Metro, like any Kotlin compiler plugin, must move in step with the Kotlin version.
Mitigation is in §3.5.

---

## 1. What this app demands from its architecture

These drivers come from the area maps and owner rules. Every pick below is judged against them.

1. **Work outlives screens.** Live event streams (SSE/WebSocket), the offline queue with dispatch markers, command receipts,
   approvals, the attention feed, notifications, the reply-finished notification (the demo needed a short foreground
   service), widgets, Quick Settings tiles, scheduled prompts and the built-in Linux runtime all run with no screen
   open. The app's brain must sit in process-scoped objects, not in screen presenters.
2. **Several live connections at once.** The main server, agents on this phone (Paseo/ACP), side connections and
   AI Team (`settings-scope-model`, `two-agents-one-phone`). Flutter swaps a conversation's own backend in per chat
   page (`ConversationBackendScope`, `screens-nav.md` §3.1). The native design needs a real per-connection scope.
3. **A hot streaming path.** Deltas arrive many times per second. Flutter learned to coalesce rebuilds to at most one
   per 50 ms and to avoid quadratic text merges (`chat-genui.md` C1 and perf notes; `protocols.md` line 453).
4. **Fail-closed, serialized persistence.** Per-profile writer lanes, `closeProfile()` that stops admission and drains,
   profile-scoped deletion by key shape, and no double-sends (`state-data.md` §1).
5. **Tests first, from the catalog.** The catalog has 712 unit, 445 integration, 268 contract, 63 screenshot,
   56 migration and 30 e2e rows. Logic must be testable in `commonTest` with fakes and virtual time, with no Android
   and no emulator.
6. **Parallel AI agents.** One feature per agent and worktree; shared libraries have one owner each; mostly Sonnet.
   The architecture must make the right thing the easy thing: few idioms, compile-time errors over runtime surprises,
   and no central file that every feature has to edit.
7. **Android components share the brain.** Services, receivers, workers, widget providers, tile services and the
   share/link activities need the same graph as the UI. In Flutter several of these ran separate Dart engines and had
   to `prefs.reload()` around each other (`state-data.md` §2.1). Native is one process.
8. **iOS and Linux desktop later.** Logic and UI go in `commonMain`. Android-only parts sit behind interfaces.

How Flutter does it today (measured in `oc_app-batch1/lib`):

- One `ConnectionController extends ChangeNotifier` split across 54 `part` files.
- About 100 `ChangeNotifier` and 71 `ValueNotifier` users, and 30 files with `StreamController`.
- Riverpod is used only as a service locator: `connProvider` and `bootstrapProvider` throw until overridden in bootstrap,
  and no `Notifier` classes exist.
- Tests inject behaviour through constructor `typedef` factories (`ServerGatewayFactory`, `EventStreamFactory`, ...).
- In size: `state/` is 68k lines, `domain/` 39k, `api2/` 10k, `paseo/` 9k and `ui/` 277k.

So the closest port is **plain Kotlin classes, constructor injection and `StateFlow`**. That is what this file recommends.

---

## 2. Version snapshot (Maven Central / Google Maven, checked 2026-11-01) [V]

| Library | Latest | Published | Notes |
|---|---|---|---|
| Kotlin | 2.4.21 (2.5.0-Beta2 pre-release) | 2026-10-08 | 2.4.0 made context parameters and explicit backing fields stable. 2.5.0 is planned for Dec 2026 (companion file). |
| kotlinx.coroutines | 1.11.0 | 2026-05-08 | Adds `SharedFlow.asFlow`, `CompletableDeferred.asDeferred`, Flow→Map terminals, and fixes a `SharedFlow` cancel race and an R8 issue that let `stateIn` coroutines be collected. |
| Metro (`dev.zacsweers.metro`) | 1.5.1 | 2026-10-29 | 1.0.0 on 2026-04-27. Tested against Kotlin 2.3.0 → 2.5.0-Beta2. |
| `metrox-viewmodel-compose` | 1.5.1 | 2026-10-29 | 1.5.0 added `linuxX64` and dropped Apple x86_64. |
| Koin core / compiler plugin | 4.2.2 / 1.2.1 | 2026-06-15 / 2026-09-10 | 4.2.0 (2026-03-17) added Navigation 3 support. The compiler plugin's 1.0 was its "first stable". |
| kotlin-inject | 0.9.0 | 2026-01-07 | `kotlin-inject-anvil` is in maintenance mode [S]. |
| AndroidX Lifecycle (JetBrains KMP) | 2.11.0 | 2026-07-13 | ViewModel-Compose and ViewModel-Navigation3 on every KMP target. |
| Navigation 3 (JetBrains KMP `navigation3-ui`) | 1.2.0 | 2026-10-23 | Jetpack 1.2.0 stable 2026-09-23. |
| Circuit (Slack) | 0.39.0 | 2026-09-15 | Built on Kotlin 2.4.20 / CMP 1.12.0. Still 0.x: 0.38.0 was breaking. |
| Molecule (Cash App) | 2.2.0 | 2025-09-24 | No release in 13 months. |
| Decompose | 3.5.0 stable / 3.6.0-beta02 | 2026-03-15 / 2026-10-13 | Single maintainer. |
| Orbit MVI / MVIKotlin / Square Workflow | 12.0.1 / 4.4.0 / 1.31.0 | 2026-08-28 / 2026-04-25 / 2026-08-25 | Considered, not chosen. |
| Kermit | 2.2.0 | 2026-09-08 | |
| kotlin-logging | 8.0.4 | 2026-05-27 | Alternative to Kermit. |
| Napier | 2.7.1 | 2024-01-04 | Stale; avoid. |
| Arrow core | 2.3.0 | 2026-10-26 | Not chosen for now. |
| kotlinx-collections-immutable | 0.5.2 | 2026-08-28 | Still pre-1.0. |
| Turbine | 1.2.1 | 2025-06-11 | Still the standard Flow test helper. |
| Konsist | 0.18.1 | 2026-10-10 | Architecture tests. |
| dependency-analysis Gradle plugin | 3.20.1 | 2026-10-31 | |
| detekt | 1.23.8 (repo pin) / 2.0.0-alpha.6 | 2025-02-21 / 2026-08-04 | The repo runs `detekt-cli-1.23.8` without type resolution (`tool/qa/kotlin_static_analysis.sh`). |
| androidx.tracing | 2.0.x (KMP API; the version naming is inconsistent between pages) | 2026 | See §9. [S/U] |

The toolchain pins (Kotlin 2.4.21, CMP, AGP, Gradle) are decided in `kmp-cmp-status.md` §11. Everything here must
stay compatible with them. In particular, **Metro and the Koin compiler plugin are both pinned by the Kotlin version.**

---

## 3. Dependency injection

### 3.1 Options

| Option | How it works | KMP | Strengths | Weaknesses for this app |
|---|---|---|---|---|
| **Metro 1.5.1** | Kotlin compiler plugin (FIR/IR); no KAPT or KSP. Dagger/Anvil/kotlin-inject feature set: graphs, scopes, `@Contributes*` aggregation, graph extensions, assisted injection, multibindings. | JVM, Android, JS, Wasm, Native. Multi-module aggregation needs Kotlin ≥2.3.20 on Wasm/Apple. | Full graph validation at compile time. Aggregation means a feature adds itself with an annotation, not by editing a central list. Square reports 5–56 % faster builds after leaving Dagger/Anvil/KAPT (one non-ABI case was 8.7 % slower; 7,000+ modules, 22 apps). Cash Android also moved. Interop with Dagger/Hilt during migrations. An IDE plugin with a graph browser. | The compiler plugin API is "not a stable API". Metro promises best-effort support up to about N+.2 Kotlin versions, so Kotlin upgrades need a matching Metro release. One primary maintainer, but heavy production users contribute fixes (Square merged 58 PRs). `metrox-android` (constructor-injected Activities/Services via `AppComponentFactory`) needs **API 28+**; the app's minSdk is 26. |
| **Koin 4.2.2** + compiler plugin 1.2.1 | Runtime service locator with a DSL. The compiler plugin (stable since 1.0, 2026) adds build-time checks of `get()`/`inject()`/`koinViewModel()` call sites and DSL definitions. | All targets. | Easiest to learn, well documented, no codegen for the runtime DSL. Navigation 3 and KMP ViewModel support. | Resolution is still at runtime: a missing binding in a path the tests miss crashes on the owner's phone. Plugin gaps [V]: leaf library modules get no diagnostics without an entry point, named scopes are treated as visible everywhere, lists built with `if`/`when` aren't resolved, and an empty module isn't detected incrementally. The plugin is coupled to the Kotlin version too (1.2.0 failed with `NoSuchMethodError` on Kotlin 2.4.20). Modules are listed centrally in `startKoin { modules(...) }`. |
| **kotlin-inject 0.9.0** (+ anvil extension) | KSP code generation. | All targets. | Compile-time safe, Kotlin-first. | KSP adds build time. The anvil extension is in maintenance mode and its maintainer moved to Metro [S]. Not a good bet for a new multi-year app. |
| **Hilt / Dagger** | KAPT/KSP code generation. | **No KMP support** [S: Dagger issue tracker and community posts; no official KMP release found]. | Best Android tooling, including WorkManager and ViewModel. | Android-only, so it fails the iOS/Linux requirement. |
| **Manual DI** | Constructor injection plus hand-written factories. | All targets. | No tool risk. | With roughly 200 injectable classes across three scopes, the graph becomes a large shared file every agent edits. That is a merge-conflict magnet and breaks the single-owner rule. |

### 3.2 Recommendation: Metro

Why Metro fits this app specifically:

1. **Build-time failure beats emulator-time failure.** The owner gets an APK only after an emulator pass. Agents
   iterate on the VPS and PC. A Sonnet agent that forgets a binding gets a compiler error naming the missing key
   and the path to it, not a crash two screens deep.
2. **Aggregation removes the central wiring file.** Protocol modules use `@ContributesIntoMap(AppScope::class)` with a
   backend key. Feature modules contribute their Nav3 entry installers with `@ContributesIntoSet`. ViewModels use
   `@ContributesIntoMap` with `@ViewModelKey`. No agent edits `AppGraph.kt` to add a feature.
3. **Graph extensions model the connection scope.** One `ConnectionGraph` is created per live connection
   (§3.3), as the Dagger subcomponent equivalent.
4. **No KSP.** One less code-generation pass per module. That matters on the 8-CPU PC, where only about one build
   fits at a time. Room KMP would bring KSP back if the data-layer research picks Room (`state-data.md` §14 is open).
5. **Classes stay plain.** Injected classes are ordinary Kotlin classes with `@Inject` constructors, so tests build them
   directly with fakes. No DI container runs in `commonTest` (see §10).

When to choose Koin instead: if slice 0 shows Metro breaking on the pinned Kotlin 2.4.21 + CMP combination, or the
IDE plugin is unusable on the agents' tooling. Koin with its compiler plugin is a working plan B. The injected classes
would not change, only the graph declarations.

### 3.3 Scope design for this app

```
AppScope (process)                    one per process: created in Application.onCreate; Android components reach it
 ├─ stores: KeyValueStore, SecretStore, BlobStore, ProfileStore, Redactor, Diagnostics, Clock, AppDispatchers
 ├─ engines: ConnectionRegistry, AttentionFeed, NotificationPolicy, ScheduleEngine, PhoneRuntime (Android),
 │           StatusLine (the one-status-line priority combiner), UpdateNotice, Teaching registry
 └─ ConnectionScope (graph extension, one per LIVE connection; several can be alive)
      created by ConnectionRegistry.open(profile); its CoroutineScope is a child of the app scope
      ├─ gateway pair from the protocol map (OpenCode 2 / Paseo / ACP / Codex), chosen by profile.backend
      ├─ EventStream, SessionList, Selection (model/agent), Approvals, PromptDelivery (queue + receipts + online marker),
      │  ReadState, Caches, Composer drafts: the STATE-DATA-10..28 split from state-data.md §14
      └─ close(): stop admission → cancel stream → drain writer lanes → release
Screen ViewModels                     per Nav3 entry (lifecycle-viewmodel-navigation3 decorator); they hold
                                      a reference to the right ConnectionGraph, never a protocol type
```

- The **conversation's own backend** (Flutter's `ConversationBackendScope`) becomes
  `ConnectionRegistry.graphFor(conversationRef)`. The chat ViewModel is created with that graph's engines, so models,
  approvals and the connection line are always the conversation's own.
- **Profile deletion** becomes a transaction run in reverse (`state-data.md` §5): close the `ConnectionGraph`, run the
  `ProfileScope` sweep, then delete the profile row and its secrets.
- **Android components** (services, receivers, workers, widget providers, tile services) reach the app graph through
  `(applicationContext as GraphOwner).appGraph` and member-inject or call accessors. Do not use `metrox-android`
  unless minSdk rises to 28 [V: AppComponentFactory needs API 28].

### 3.4 Code sketch (check names against the pinned Metro version) [U for exact API names]

```kotlin
// core:common — scope markers
abstract class AppScope private constructor()
abstract class ConnectionScope private constructor()

// engine:connection — the per-connection extension
@GraphExtension(ConnectionScope::class)
interface ConnectionGraph {
    val delivery: PromptDelivery
    val approvals: Approvals
    val sessions: SessionList

    @GraphExtension.Factory
    @ContributesTo(AppScope::class)
    fun interface Factory {
        fun create(@Provides profile: ServerProfile, @Provides scope: ConnectionCoroutineScope): ConnectionGraph
    }
}

// protocol:oc2 — contributes itself; nobody edits a central list
@ContributesIntoMap(AppScope::class)
@BackendKey(Backend.OpenCode)
@Inject
class Oc2GatewayFactory(private val http: HttpClientFactory) : GatewayFactory {
    override fun create(profile: ServerProfile): GatewayPair = /* ... */ TODO()
}

// engine:connection — an ordinary class; tests construct it directly with fakes
@SingleIn(ConnectionScope::class)
@Inject
class PromptDelivery(
    private val gateway: ServerOperationsGateway,
    private val queue: OfflineQueueStore,
    private val receipts: CommandReceipts,
    private val dispatchers: AppDispatchers,
    private val scope: ConnectionCoroutineScope,
) { /* ... */ }

// androidApp — the final graph must live in platform code when platform code contributes [V: Metro multiplatform docs]
@DependencyGraph(AppScope::class)
interface AndroidAppGraph : ViewModelGraph {
    @DependencyGraph.Factory
    fun interface Factory { fun create(@Provides app: Application): AndroidAppGraph }
}
```

### 3.5 Metro risk controls

- Pin Kotlin and Metro together in the version catalog. Upgrade them only as a pair, and only after reading Metro's
  compatibility table. Metro 1.5.x already lists 2.5.0-Beta2 [V].
- Add one tiny "graph smoke" module test: build the release graph and resolve every multibinding key. It runs in
  `jvmTest` on the VPS.
- Keep `@Inject` classes free of Metro-specific types other than annotations. If Metro had to go, a manual or Koin graph
  can wrap the same constructors.
- Turn off Metro's experimental features (suspend providers, top-level function injection, `hiddenDependencies`)
  until they are stable [V: marked experimental in the 1.4/1.5 notes].

---

## 4. State management

### 4.1 Options

| Option | Model | Fit for this app | Verdict |
|---|---|---|---|
| **ViewModel + `StateFlow<UiState>`** (UDF, "MVI-lite") | A screen-level `ViewModel` exposes one `StateFlow` built from repository/engine flows with `stateIn(viewModelScope, WhileSubscribed(5_000), initial)`. User actions are method calls. Google's own recommendation (page updated 2026-10-05) [V]. | KMP on every target since lifecycle 2.11 [V]; Nav3 per-entry scoping; Metro ViewModel injection. The pattern agents have seen most. Maps directly from `ChangeNotifier` controllers. | **Chosen** for screens. |
| **Plain state holders + `StateFlow`** (engines) | App- or connection-scoped classes, no framework. | This is where the app's logic really lives (§1.1). | **Chosen** for the engine layer. |
| **Circuit 0.39.0** | `Screen` key + `Presenter` (a Compose-runtime function returning `CircuitUiState` with an `eventSink`) + `Ui`. Its own `Navigator` and back stack, overlays, retained state. Used in production at Slack [V]. | Elegant and Metro-integrated. But it brings its own navigation, which would replace Nav3's scenes, deep links and predictive-back hooks. It is still 0.x with breaking releases (0.38.0 made `Screen` non-Parcelable). Compose-runtime presenters are a second state idiom next to the engine's Flow code, and long-running work still needs engines outside presenters. | Not chosen. Worth a look only if the team later wants presenters everywhere. |
| **Molecule 2.2.0** | Turns a `@Composable` function into a `StateFlow`. | Useful for a presenter that combines many inputs, but no release since 2025-09 [V], and a second idiom. | Not chosen. Allowed later case by case behind a ViewModel if a screen's combine logic gets unreadable. |
| **Decompose 3.5.0** | Component tree with its own lifecycle (Essenty `InstanceKeeper`), child stacks and navigation, and UI-toolkit-agnostic (SwiftUI-friendly). | Its big win is a native SwiftUI front end on iOS. The plan is Compose UI everywhere, so it would duplicate ViewModel and Nav3 with a third-party lifecycle. Single maintainer; 3.6 is in beta. | Not chosen. |
| **Orbit 12 / MVIKotlin 4.4 / Workflow 1.31** | Full MVI frameworks: containers, intents, side-effect channels. | Ceremony and idioms an agent has to learn. A sealed state plus a pure reducer gives the same testability without a framework. | Not chosen. |
| **Compose `retain {}`** (`androidx.compose.runtime:runtime-retain`, 1.12.1 stable on Google Maven) | Keeps an object across configuration changes, scoped to one composable. Circuit 0.37.1 switched its Android retained state to it [V]. | Android's state-lifespan guide places it between `remember` and `rememberSaveable`. It has no coroutine or saved-state support of its own [S], and Navigation 3 forgets retained values when an entry goes to the back stack [S]. | For small UI-only objects at most. |

### 4.2 Recommended pattern

**Two kinds of state holder, one idiom (`StateFlow`):**

1. **Engines** (app or connection scope, in `:engine:*`) own the truth, the timers, the streams and the state machines.
   They expose `StateFlow<…>` (state) and, rarely, `Flow<…>` of facts. They accept `suspend` commands that return
   `Outcome` (§7).
2. **Screen ViewModels** (in `:feature:*`) `combine` engine flows into one `StateFlow<XUiState>` and translate taps into
   engine calls. They hold no business rules. A ViewModel that grows rules is a sign the rules belong in an engine,
   because the same rule is usually needed by a notification, widget or tile.

Rules agents should follow (put them in the rewrite's AGENTS.md):

- **One `UiState` per screen**: a data class, or a sealed type when states exclude each other. Expose it with Kotlin 2.4
  explicit backing fields [V stable in 2.4.0]:
  ```kotlin
  class ChatViewModel(/* injected engines */) : ViewModel() {
      val state: StateFlow<ChatUi>
          field = MutableStateFlow(ChatUi())
      fun send(text: String) { viewModelScope.launch { /* delivery.send(...) → state.update { ... } */ } }
  }
  ```
- **No one-shot event channels** from ViewModel to UI. Google recommends handling the event in the ViewModel and
  updating state [V]. A "snackbar to show" or "navigate after create" is a state field that the UI consumes and
  acknowledges (`onShown(id)`). This avoids events lost during configuration changes and back gestures.
- **State machines are explicit.** For send/stop/steer, the turn stall, approvals, connection status and setup steps,
  write `sealed interface TurnState` and a pure `fun reduce(state, event): Transition` that returns the new state plus
  effects to run. The reducer is tested exhaustively in `commonTest` with no coroutines. `screens-nav.md` §11.1 asks
  for exactly this for the chat screen.
- **Strings never live in state.** State carries ids, enums and `Failure` categories. The UI resolves them to Compose
  string resources (Arabic RTL, plurals, plain words), so ViewModels stay locale-free and testable.
- Collect with `collectAsStateWithLifecycle()` [V: strongly recommended]. Screen-level ViewModels only. Reusable kit
  parts take plain parameters and hoisted state.

### 4.3 The streaming hot path (the chat transcript)

The demo felt smooth partly because it kept each message part in its own Compose snapshot state and revealed text at a
steady pace (`kmp-demo/.../state/ChatModel.kt`: `TextPart.text`, `shown`). The production design should keep that
property without putting Compose runtime types in the engine:

1. **Engine side** (`TranscriptEngine`, connection scope, serial dispatcher §8):
   - apply each event synchronously, in order, to a mutable internal model: a `StringBuilder` per live text part, a
     version fence per message/part (`chat-genui.md` 02/03);
   - **publish** an immutable `TranscriptState` at most once per frame-sized window. Use 50 ms to keep catalog parity
     (C1: first delta flushes at once, then leading+trailing every 50 ms) [V internal];
   - use `kotlinx.collections.immutable` `PersistentList` for turns, so a publish shares structure instead of copying
     history. Only the live turn's object changes.
2. **UI side**:
   - `LazyColumn` with `key = turn.id` and `contentType = row kind` [V Compose best practices];
   - row parameters are stable data, so strong skipping (on by default) skips every unchanged turn;
   - the word fade-in and steady reveal are a **kit text component** driven by the frame clock, not engine state.
     The engine says "text is now N chars"; the kit animates toward it and respects reduced motion.
3. **Escalation path, only if measurement shows jank:** expose the live part's text as its own
   `StateFlow<String>`, read inside the text composable (defer the read, the "defer reads" best practice [V]), so a delta
   recomposes one text node and never diffs the list.
4. Port `test/perf_chat_test.dart` (1,000/5,000 parts; 10 spaced deltas plus a 100-delta burst; it asserts counts,
   not timings) as the engine's publish-count test, and add a Macrobenchmark frame-timing test on the emulator.

### 4.4 Testing state holders

- Engines and ViewModels are constructed directly in `commonTest` with fakes. Prefer fakes to mocks [V Android
  recommendations]. A shared `:core:testing` module holds `FakeGateway`, `FakeKeyValueStore`, `TestClock` and
  `TestDispatchers`.
- `runTest` with `StandardTestDispatcher` for virtual time (50 ms coalescing, 5 s polls, 15-minute stuck command,
  60-minute sweeps). Turbine `test {}` for flows. `backgroundScope` for collectors that never end.
- ViewModels on JVM tests need `Dispatchers.setMain(testDispatcher)` because `viewModelScope` uses
  `Dispatchers.Main.immediate`.
- Name each test after its catalog row, e.g. `` `STATE-DATA-18 dispatch marker blocks auto resend`() ``, so coverage
  against the catalog can be counted by grep.

---

## 5. Navigation (architecture view; library status in `kmp-cmp-status.md` §4)

### 5.1 Library choice

**Navigation 3 1.2.0.** Android's architecture page now lists "single-activity app with Navigation 3" as strongly
recommended (updated 2026-10-05) [V]. What this app gets from it:

| App need (from `screens-nav.md`) | Nav3 feature [V] |
|---|---|
| "Landing" = clear and go Home; Servers is the root when no profile exists | The back stack is a list the app owns. Replace it in one assignment. |
| A chat that is already showing does not stack again (`c5842e836`) | Inspect `backStack.last()` before adding. |
| Rail / sidebar / two- and three-pane on wide windows | `SceneStrategy` list. Material `ListDetailSceneStrategy` from `adaptive-navigation3` (CMP 1.3.0 stable, 1.4.0-alpha) or a custom kit scene. |
| The chat grows out of its row; predictive back without fighting it | `SharedTransitionLayout` + `sharedTransitionScope` (1.1.0). Per-entry `PredictivePopTransitionKey` metadata, so the chat entry gets its own gesture transform. `NavigationBackHandler` + `rememberNavigationEventState` (1.2.0). |
| Pop the chat only when nothing is lost (draft rule) | `NavigationBackHandler` on the chat entry, enabled when there is unsaved work. |
| Links `opencode-mobile://session` and `://team`, share-in, notification and widget taps | KMP deep-link API (`DeepLinkMatcher`, `BackStackMatcher` builds a synthetic stack) (1.2.0). |
| Per-entry ViewModels | `lifecycle-viewmodel-navigation3` decorator (2.11.0). |
| Result from a picker back to its caller | `ResultEventBus` (1.2.0). Prefer engine state when the result is a domain choice (e.g. the selected model). |

Watch item: Jetpack Nav3 1.3.0-alpha01 "fixed predictive back handoff in `NavDisplay`" (`DeferredAnimatedContent`).
If the 1.2.0 handoff glitches during the slice-0 prototype, that fix is the reason [V release notes; impact U].

### 5.2 App-level design around the library

- **Routes:** each feature module has a tiny `:feature:x:api` module holding `@Serializable sealed interface XRoute : NavKey`
  and nothing else. A feature that opens another feature depends only on that `api` module, never on its `impl`.
  Register all route subclasses in one `SerializersModule`, as non-JVM targets need [V]. Each feature contributes its
  `polymorphic` block through Metro, so the app never edits a central list.
- **Entry installers:** each `impl` module contributes a
  `fun interface EntryInstaller { fun EntryProviderScope<NavKey>.install() }` into a set. The shell's
  `entryProvider { installers.forEach { with(it) { install() } } }` assembles them. [U: exact DSL receiver name in 1.2.0]
- **`AppNavigator`** (app-scoped interface, the only thing ViewModels and engines may call): `open(route)`,
  `openChat(ref, focusComposer)` (de-duplicates), `land()` (clear to Home), `replaceRoot(route)`, `back()`.
  The Compose shell implements it over the `NavBackStack`. Tests use a recording fake.
- **One `EntryRouter`** replaces the seven copies of the Flutter pending-intent mechanism (`screens-nav.md` §4,
  §11.2). Intents (launcher shortcut, link, share, notification, widget, tile, scheduled-run tap) become
  `EntryRequest` values. They wait until bootstrap and migration are done, are consumed exactly once, and are then
  turned into `AppNavigator` calls. This is pure logic, tested in `commonTest`.
- **Sheets and dialogs are screen state, not routes,** matching Flutter's `showKitSheet`/`showKitConfirm` model. Use a
  route (with a Nav3 `OverlayScene`) only when a sheet must be reachable by deep link or must survive process death on
  its own. The kit sheet handles Back with `NavigationBackHandler`.
- **Predictive back policy by route class** (decide once, in the kit):
  1. list → chat: container transform on tap and button-back, the platform predictive motion while a gesture is live
     (demo lesson);
  2. settings pages: shared-axis X with system predictive back;
  3. full-screen dialogs (new conversation): slide up, gesture closes;
  4. reduced motion: no transform, cross-fade or none.

---

## 6. Module layers

### 6.1 Layering

The companion file already decides the Gradle mechanics: AGP 9 needs a separate `:androidApp`, KMP library plugin,
`build-logic` convention plugins, one version catalog. This is the layer design on top of it. Names follow the
companion file where it set them.

```
:androidApp  (com.android.application; composition root; Activities, Services, Receivers, Workers, widgets, tiles;
              final Metro graph; Flutter migration runner)            :desktopApp (later)
   │
:feature:<name>:impl   screens + ViewModels + entry installers      (KMP, Compose)
:feature:<name>:api    Route keys only                              (KMP, no Compose)
   │
:ui:kit      every component, theme, motion, haptics seams. The ONLY module that may depend on material3.
:ui:words    plain-words tables (Failure → string), Compose resources for copy (en, ar, ...)
   │
:engine:<area>   app/connection-scoped logic: connection, transcript, delivery, approvals, attention, schedule,
                 teaching, status-line, agents, team, setup state ...  (KMP, no Compose)
   │
:core:domain     gateway interfaces (ServerGateway, ServerOperationsGateway), ServerCapabilities, protocol-neutral models
:protocol:<x>    oc2, paseo, acp, codex, a2a: implement core:domain, contribute themselves via Metro (KMP)
   │
:core:data       KeyValueStore, SecretStore, BlobStore, ProfileScope, WriterLane (interfaces + common logic)
:core:net        Ktor client factory, SSE / WebSocket framing, Tailscale/private-address checks, cert pins
:core:common     Outcome/Failure, AppDispatchers, Clock, Redactor, logging facade, ids
:core:model      plain data types
:core:testing    fakes and fixtures (testImplementation only)

:android:<bridge>  plain Android library modules: linux (proot, jniLibs), termux, voice, camera, background service,
                   widgets runtime, update installer. They implement interfaces from :engine/:core:domain.
:migration:flutter Android library: reads FlutterSharedPreferences, flutter_secure_storage v11, files; runs before the graph's engines start
```

- OpenCode 1 is gone in the data (`state-data.md` fact 9: profiles are forced to `flavor = v2`). The companion
  file's `:core:protocol-oc1` is not needed unless slice 0 finds a live OC1 profile on the owner's phone.
- I split `protocol` out of `core` (the companion file put it at `:core:protocol-*`) so one Gradle rule can say
  "nothing above `engine` sees `protocol`". The name is a detail. The rule is what matters.

### 6.2 Dependency rules (Gradle-enforced, not by convention)

| From | May depend on | Must not depend on |
|---|---|---|
| `feature:*:impl` | `feature:*:api`, `ui:kit`, `ui:words`, `engine:*`, `core:domain`, `core:model`, `core:common` | `protocol:*`, `core:net`, `core:data` implementations, other `feature:*:impl`, `material3` directly |
| `engine:*` | `core:*` | `ui:*`, `feature:*`, `protocol:*` (it gets gateways through DI interfaces) |
| `protocol:*` | `core:domain`, `core:net`, `core:model`, `core:common` | `engine:*`, `ui:*`, `feature:*` |
| `ui:kit` | `core:common` (motion, text-scale, platform seams) | business types |
| `androidApp` | everything | nothing |

This encodes AGENTS.md's "UI talks to the domain gateway only, never `api/` or `api2/`" and "gate on
`ServerCapabilities`, never on the flavor enum": features cannot even see the backend enum's implementations.

Enforcement (cheap, in this order):

1. Convention plugins `oc.feature`, `oc.engine`, `oc.protocol` check declared project dependencies against the table
   and fail configuration on a violation. Roughly 30 lines of Gradle code.
2. Konsist 0.18.1 tests in a `:quality` JVM module: "no file in `feature` imports `…protocol…`", "no `e.message`
   string reaches a `Text(`" (approximate), "every `@Serializable` `NavKey` is registered", and "ViewModels have no
   `Context` parameters" [V for Konsist's existence and version; the rule wording is my design].
3. `dependency-analysis` plugin 3.20.1 for unused or misdeclared dependencies, on the VPS only.
4. `explicitApi()` in `core:*`, `engine:*` and `protocol:*`, so each module's public surface is deliberate and small.

### 6.3 Granularity and ownership

- Start with about **35–45 modules**. Engines are split by the `state-data.md` §14 ids (connection-session, location,
  session-list, selection, approvals, delivery, composer, read-state, caches, alerts, agents) but grouped when a slice
  is small. More modules mean more parallel agents without conflicts and faster incremental builds. Each module also
  costs configuration time and memory on the 16 GB PC [V Android modularization guide; numbers U].
- **One owner per module**, recorded in a `MODULE_OWNERS` file. That is the single-owner rule made mechanical. Shared
  modules (`core:*`, `ui:kit`, `engine:connection`) get one coordinating owner. Others propose changes through it.
- Flutter → module map (first cut):

| Flutter (`lib/`) | Native |
|---|---|
| `api2/`, `paseo/`, `acp/`, `codex/`, `a2a/` | `protocol:oc2`, `protocol:paseo`, `protocol:acp`, `protocol:codex`, `protocol:a2a` |
| `domain/` (39k) | `core:domain` (+ `core:model`) |
| `state/` (68k) incl. `connection/*.dart` | `core:data` + `engine:*` |
| `builtin/`, `termux/`, `background/`, `voice/`, `platform/`, `scheduling/` | `engine:*` interfaces + `android:*` implementations |
| `orchestration/` (AI Team) | `engine:team` + `feature:team` |
| `diagnostics/`, `feedback/`, `update/` | `engine:diagnostics` + `feature:about` |
| `ui/kit` | `ui:kit` |
| `ui/screens`, `ui/widgets` | `feature:*` |
| `ui/*_words.dart`, `domain/product_failure.dart` tables | `ui:words` |
| `l10n/` | `ui:words` resources (+ Android `res/values*` for manifest and widget XML) |
| `main.dart`, `main_parts/` | `androidApp` + `engine:entry` (EntryRouter) |

---

## 7. Error handling

### 7.1 Principles

1. **Expected failures are values; bugs are exceptions.** Network down, sign-in refused, server busy, folder missing,
   storage refused: these return `Outcome.Failed(failure)`. An exception means a programming error (or cancellation)
   and goes to crash diagnostics.
2. **Translate at the boundary, once.** Ktor exceptions, `SerializationException`, HTTP status codes, Keystore errors
   and proot/Termux failures are caught inside `protocol:*`, `core:net`, `core:data` and `android:*`, and mapped to
   `Failure`. Nothing above `engine` ever sees a transport exception type. This mirrors `ProductFailure.from(Object)`,
   which today classifies about 15 exception types in one place.
3. **The user never sees technical text.** A `Failure` has a category plus optional authored words (app-written only)
   plus `details: Redacted`. The UI maps the category to plain words through `ui:words`. Details only appear under the
   Details fold, already redacted. This is the owner's "no raw errors" rule made a type-level fact: there is no
   `String` path from a server to a `Text`.
4. **Cancellation is not a failure.** Never swallow `CancellationException`.

### 7.2 Types (sketch)

```kotlin
// core:common
sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failed(val failure: Failure) : Outcome<Nothing>
}

sealed interface Failure {
    data object Offline : Failure
    data object TimedOut : Failure
    data class Certificate(val host: String) : Failure
    data object SignInRefused : Failure
    data object NotFound : Failure
    data object Conflict : Failure
    data object ServerDidNotAnswer : Failure
    data class Refused(val reason: RefusalReason) : Failure          // fail-closed store refusals, consent missing
    data class Authored(val words: AuthoredText) : Failure           // app-written copy only
    data class Unexpected(val details: Redacted) : Failure           // still plain words in UI; details under the fold
    // ... port the full ProductFailureCategory list from lib/domain/product_failure.dart
}

/** The only sanctioned way to call suspend code that may throw. It rethrows cancellation. */
suspend inline fun <T> attempt(map: (Throwable) -> Failure, block: () -> T): Outcome<T> =
    try { Outcome.Ok(block()) }
    catch (e: CancellationException) { throw e }
    catch (e: Throwable) { Outcome.Failed(map(e)) }
```

- Keep `Failure` a **closed sealed hierarchy** so `when` is exhaustive in `ui:words`. Adding a category forces the copy
  to be written.
- `Redacted` is a value class whose only constructor runs the `Redactor` (a port of `KitRedact`: known secrets
  registered at load, plus token and URL-credential patterns). A raw `String` cannot become `Redacted` any other way.

### 7.3 Library choices

- **Arrow 2.3.0** (2026-10-26) is healthy, KMP, and its `Raise` DSL works with Kotlin 2.4's stable context parameters.
  I still don't recommend it as a core dependency. A 20-line `Outcome` covers what this app needs. Arrow's idioms
  (`either {}`, `ensure`, `bind()`, accumulation) are one more dialect for the agents. Revisit if form validation or
  setup flows need error accumulation.
- **Kotlin rich errors** (error union types, KEEP-0462) are slated as experimental for Kotlin 2.5.0 [S]. The design
  is still being debated (KEEP discussion #487) [V]. Do not adopt. A sealed `Failure` today migrates mechanically later.
- **`kotlin.Result`/`runCatching`**: banned around suspend calls. `runCatching` catches `CancellationException` and
  breaks cooperative cancellation. detekt's `SuspendFunSwallowedCancellation` rule exists for this, but it needs type
  resolution [V]. The repo's detekt 1.23.8 CLI run has no type resolution, so enforce with a Konsist or text check
  ("no `runCatching` in `engine`/`protocol`/`feature`") until detekt 2.x is adopted.

### 7.4 Crashes

- `Thread.setDefaultUncaughtExceptionHandler` on Android, installed before anything else in `Application.onCreate`,
  writes a crash record (port of `CrashDiagnosticsStartup`; the `crash-diagnostics/` files must stay readable through
  the migration).
- Every app and connection scope carries a `CoroutineExceptionHandler` that logs and records, plus a `SupervisorJob`, so
  one failed child (one stream, one store write) does not take the connection down. The handler only applies to root
  coroutines and direct children of a supervisor [V coroutines exception docs].
- The plain-words error surface must always offer the Details fold and "Report a problem" (`screens-nav.md` §11.6).

---

## 8. Concurrency with coroutines and Flow

### 8.1 The key decision: confine each engine to one serial dispatcher

The Flutter code was written for a single-threaded event loop. It relies on "nothing else runs between these two lines
unless I `await`". A line-by-line port to Kotlin with `Dispatchers.Default` silently adds data races. Rule:

- Each engine (and each per-profile store) mutates its state only on its own **serial dispatcher**:
  `dispatchers.default.limitedParallelism(1)`, injected as `EngineDispatcher`. Only one task runs at a time and
  ordering is preserved, the same guarantee Dart's event loop gave.
- Heavy pure work (JSON decode of 100-message pages, transcript merge of 5,000 parts, markdown parse) moves out with
  `withContext(dispatchers.default)` and comes back with a value. This matches Flutter's "decode big JSON off the main
  thread" (`protocols.md` line 516).
- UI reads `StateFlow` (thread-safe). ViewModels run on `Main.immediate` and never mutate engine state directly.

### 8.2 Scopes replace generation counters

- Today, "generation + identity re-check after every await" fences stale results (`state-data.md` §14,
  `protocols.md` line 452). In Kotlin, each connection generation is a child `CoroutineScope` (the `ConnectionGraph`'s
  scope). Switching profile or reconnecting cancels it.
- Because of the **prompt cancellation guarantee**, a coroutine cancelled while suspended does not resume successfully,
  even if its result had already arrived [V `suspendCancellableCoroutine` KDoc]. Code after an `await` in a cancelled
  generation never runs, so most counters disappear.
- Keep an identity re-check only where a result is written into a store that **outlives** the generation (app-scoped
  caches, prefs), and wherever `withContext(NonCancellable)` is used. Writes there must name the profile and generation
  they belong to.

### 8.3 Writer lanes (fail-closed persistence)

```kotlin
/** Per-profile serial writer: admission stops synchronously on close; admitted writes drain in order. */
@OptIn(ExperimentalAtomicApi::class)           // kotlin.concurrent.atomics; or atomicfu [U: opt-in status on 2.4.21]
class WriterLane {
    private val admitting = AtomicBoolean(true)
    private val lane = Mutex()                    // kotlinx Mutex is fair: FIFO, like Dart's promise chain
    suspend fun <T> write(block: suspend () -> Outcome<T>): Outcome<T> {
        if (!admitting.load()) return Outcome.Failed(Failure.Refused(RefusalReason.ProfileClosing))
        return lane.withLock { block() }          // admitted before close → still runs
    }
    fun stopAdmission() = admitting.store(false)  // synchronous, like closeProfile()'s first step
    suspend fun drain() = lane.withLock { }        // returns after every admitted write has finished
}
```

A serial dispatcher alone is not enough here: a write that suspends inside `block` (disk I/O on `io`) frees the
dispatcher, and a `close` could run in the middle of it. The mutex makes "drain" mean "every admitted write finished".

- A write returns only after the platform write is acknowledged: `commit()`, not `apply()`, on the
  SharedPreferences actual, or a DataStore `updateData`. A refused write reloads from disk, as today (`state-data.md`
  fact 3).
- Cross-process readers: in one native process, widgets, tiles, workers and the scheduler read through the same graph.
  The Flutter-era `reload()` dance goes away, except where a separate process remains (check the manifest for
  `android:process` in slice 0 [U]).

### 8.4 Flow conventions

| Purpose | Type | Rule |
|---|---|---|
| Current state | `StateFlow<T>` | Always has a value. Equal values are conflated, so make state classes data classes with correct `equals`. |
| Server events (SSE / WebSocket) | `Flow<Event>` from a cold `callbackFlow`/`channelFlow`, shared once per connection with `shareIn(connectionScope, SharingStarted.WhileSubscribed(...))` or collected by one engine | **Durable** events (`durable.seq`) are never dropped: an unbounded or suspending buffer feeds the reducer. **Volatile** deltas may be coalesced. After reconnect, reconcile by refetch; never replay deltas (AGENTS.md). |
| UI state from engines | `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), initial)` | [V Google recommendation] |
| Facts for one consumer (e.g. "reply finished" → notification) | `SharedFlow` with `replay = 0` and an explicit buffer and overflow policy | Never `extraBufferCapacity` without saying why. |

- Name every `buffer`, `conflate` or `debounce` with a comment that cites the behaviour (catalog id) it implements.
- Inject `AppDispatchers(main, default, io)` and a `Clock`. Never touch `Dispatchers.*` or `Clock.System` directly
  outside `core:common`, so virtual time works in tests. `kotlin.time.Clock` is in the stdlib; check whether it needs
  an opt-in on 2.4.21 [U].
- 1.11.0 notes: passing a `Job` to a builder (`launch(Job())`) now has a lint deprecation, and using a
  `CoroutineDispatcher` as a context key is deprecated [V]. Agents should use the scope tree instead.

### 8.5 Android lifetime

- Engines live in the process. Android decides how long the process lives. Long work that must survive the UI
  (reply-finished watching, the built-in Linux server, team runs) runs under the existing foreground-service rules,
  within the Android 15+ `dataSync` 6-hour cap (AGENTS.md). WorkManager runs scheduled prompts.
- Engines must not assume they will run forever. Persist progress markers (dispatch markers, recovery markers) before
  side effects, exactly as today.

---

## 9. Logging, diagnostics and tracing

### 9.1 Logging library

| Option | Verdict |
|---|---|
| **Kermit 2.2.0** (Touchlab, 2026-09-08): `Logger` → pluggable `LogWriter`s; Logcat/OSLog/console platform writers; lazy lambda messages; tagged loggers; test writer; Crashlytics/Bugsnag modules | **Chosen.** KMP-native, small, and custom writers are the extension point this app needs. |
| kotlin-logging 8.0.4 | Fine on JVM/desktop. Its SLF4J-style model adds little on Android. |
| Napier 2.7.1 | Last release 2024-01. Avoid. |
| Timber | Android-only. |

Design:

- One `AppLog` facade in `core:common` wraps Kermit. Every message is a lambda (Kermit builds the string only when
  the severity passes).
- **Writers:**
  1. `RedactingWriter` wraps all the others and runs the `Redactor` on message and throwable text. It is installed
     **before** any secret is loaded. The Flutter app registers known secrets at load (`KitRedact.registerKnownSecret`);
     the migration must do the same before its first log line (`state-data.md` §3.3).
  2. `RingBufferWriter`: a bounded in-memory buffer (lines plus bytes cap) that feeds Diagnostics and "Report a
     problem". It is never written to disk unless the person exports a report.
  3. `LogcatWriter` in debug builds only. Release builds log Warn+ to the ring buffer only. The owner's phone has
     logcat silenced (`persist.log.tag=S`), so in-app diagnostics are the real channel (memory: phone-logcat-silenced).
- **Never log**: provider credentials (`/config/providers` returns API keys), passwords, tokens, prompt text by
  default, or file contents. Add a Konsist rule that flags log calls whose arguments mention `password`, `token`,
  `apiKey` or `secret`, plus a unit test that runs every log path in the migration with planted secrets and asserts
  they are absent from the buffer.

### 9.2 Tracing and performance

- Port `PerfTrace` marks (`app.main`, `app.first_frame`, route timing) to `androidx.tracing`. It has a KMP API
  (`tracing` in `commonMain`, `tracing-wire` with a Perfetto sink in `androidMain`). The release notes and the
  in-process tracing guide disagree on whether the latest is alpha06 or beta01 [S/U]. Until it is stable, wrap it in a
  `Trace` facade in `core:common` with a no-op default.
- Android guidance [V]: R8 full mode, Baseline and Startup Profiles, Macrobenchmark for startup and frame timing. The
  demo's 0.25 s cold start is the budget to defend. Measure it in CI on the emulator, not by feel.
- Logs and traces are diagnostics, not product state. Engines never read them.

---

## 10. Testing architecture (only what the stack decides)

- **No DI container in tests.** Construct classes with fakes. One `graph smoke` test (§3.5) proves the real graph
  resolves.
- **Layers of tests map to catalog levels:**

  | Catalog level | Lives in |
  |---|---|
  | `unit` (712) | `commonTest` of the owning module |
  | `contract` (268) | `protocol:*` `commonTest` with recorded wire fixtures, replayed through a fake Ktor engine (`MockEngine`) |
  | `integration` (445) | engine-level tests that wire several real engines over fake gateways and stores |
  | `migration` (56) | `migration:flutter` Android host tests (Robolectric or instrumented) against real `FlutterSharedPreferences.xml` and secure-storage fixtures |
  | `screenshot` / `e2e` | later, with the widget tests (owner: they come later) |

- Reducers get exhaustive table tests: every `(state, event)` pair the catalog names.
- Keep tests deterministic: virtual time, `TestClock`, and no real network. Real-server smoke checks stay as separate
  tools (as `tool/smoke_test.dart` is today).

---

## 11. Recommendations for this app (decisive)

1. **Adopt Metro 1.5.x** with `AppScope` and a `ConnectionScope` graph extension per live connection. Pin it with
   Kotlin. Plan B is Koin 4.2 + compiler plugin 1.2.1, with injected classes unchanged.
2. **Engines plus thin ViewModels.** The business brain lives in app- and connection-scoped engines (plain Kotlin,
   `StateFlow` out, `suspend` + `Outcome` in). ViewModels only combine and translate. No Circuit, Molecule,
   Decompose or MVI framework.
3. **Explicit state machines** (sealed state, pure reducer, effects) for chat turn send/stop/steer, delivery
   (queue/receipts/markers), approvals, connection status, setup and the phone runtime. Port their catalog rows first.
4. **Transcript path:** a serial engine, synchronous event apply, publish at most every 50 ms, `PersistentList` turns,
   keyed lazy rows, and reveal animation in the kit. Per-part state only if a measurement asks for it.
5. **Navigation 3 1.2.0** behind `AppNavigator`, plus one `EntryRouter` for every external entry. Route keys live in
   `feature:x:api`; features contribute entry installers through Metro. Sheets are screen state.
6. **Module layers with Gradle-enforced rules** (§6.2), `explicitApi()` below the UI, and one owner per module.
7. **`Outcome` + sealed `Failure`, redacted details, plain-words tables in `ui:words`.** `runCatching` is banned in suspend
   code. No Arrow, no rich errors yet.
8. **One serial dispatcher per engine** (`limitedParallelism(1)`), injected dispatchers and clock, structured scopes
   instead of generation counters.
9. **Kermit 2.2.0** behind `AppLog`, with the redacting writer first and an in-memory ring buffer for diagnostics.
   Tracing via a facade over `androidx.tracing` 2.0 once stable. Baseline profiles from the first release.
10. **Write the rewrite's own AGENTS.md section** stating these rules in ten lines, so every Sonnet agent gets the same
    idioms: one `UiState`, no event channels, no strings in state, no `runCatching`, no `Dispatchers.*` outside core,
    no feature→protocol imports, route keys in `api`, kit-only UI.

What to prototype in slice 0, before features start (each should take under a day):

- (a) Metro graph + `ConnectionScope` extension + `metrox-viewmodel-compose` + Nav3 per-entry ViewModels compiling for
  `android` and `jvm` (desktop), with the iOS klib compile check from the companion file.
- (b) The list → chat container transform with Nav3 predictive back (the demo's fight).
- (c) A transcript engine fed a recorded 5,000-part stream: publish counts plus a Macrobenchmark frame report on the
  emulator.
- (d) A `WriterLane` + `ProfileScope` sweep against a copied `FlutterSharedPreferences.xml`.

---

## 12. Extra code-level notes

```kotlin
// A screen ViewModel injected with Metro (metrox-viewmodel) [U: exact annotation set for 1.5.1]
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class)
class ChatsViewModel(
    feed: ChatFeed,                 // engine: merged feed across live connections
    private val nav: AppNavigator,
) : ViewModel() {
    val state: StateFlow<ChatsUi> = feed.items
        .map(::toUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUi.Loading)

    fun open(item: ChatRow) = nav.openChat(item.ref, focusComposer = false)
}

// Entry installer contributed by a feature module
@ContributesIntoSet(AppScope::class)
@Inject
class ChatEntries : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<ChatRoute>(metadata = KitTransitions.growFromRow()) { key -> ChatScreen(key) }
    }
}

// Reducer shape for a state machine (pure, exhaustively testable)
sealed interface TurnState { data object Idle : TurnState; data class Sending(val id: PromptId) : TurnState /* ... */ }
sealed interface TurnEvent { /* SendTapped, Accepted, StreamStarted, StopTapped, Interrupted, Stalled, ... */ }
data class Transition(val state: TurnState, val effects: List<TurnEffect> = emptyList())
fun reduce(s: TurnState, e: TurnEvent): Transition = when (s) { /* exhaustive */ else -> Transition(s) }

// Kermit with a redacting front writer
val log = Logger(
    loggerConfigInit(RedactingWriter(redactor, delegates = listOf(ringBuffer) + debugOnly(platformLogWriter()))),
    tag = "oc",
)
```

---

## 13. Unknowns and things to verify in slice 0

1. **Metro API names** (`@GraphExtension`/`.Factory`, `@ViewModelKey`, the `metrox-viewmodel-compose` composable entry
   point) for 1.5.1. The sketches above are from docs pages and memory [U].
2. **Metro + CMP 1.13-beta + Kotlin 2.4.21** compile together for `android` + `jvm`, and the iOS klib check passes
   with multi-module aggregation (needs Kotlin ≥2.3.20 on Apple targets [V]).
3. **Nav3 1.2.0 predictive back + `sharedBounds`** without the demo's latch workaround, or with the 1.3.0-alpha
   handoff fix [U].
4. **Exact Nav3 entry DSL receiver type** (`EntryProviderScope` vs another name) in 1.2.0 for the installer
   pattern [U].
5. **`androidx.tracing` 2.0** real status (alpha06 vs beta01) and its KMP targets [S/U].
6. **detekt 2.0 (alpha)** with type resolution on Kotlin 2.4, or keep 1.23.8 without type resolution plus Konsist rules
   [U].
7. **Whether any `android:process` remains** (widget overlay, scheduled runner) after the rewrite. That decides whether
   cross-process prefs consistency is still needed [U].
8. **Koin plan-B readiness:** whether the Koin compiler plugin 1.2.1 still works with the pinned Kotlin when slice 0
   runs [V for 2.4.20/2.4.21 range in its notes; 2.4.21 U].
9. **Molecule's activity:** no release since 2025-09. Irrelevant unless a screen opts in later.
10. **Rich errors timeline** (Kotlin 2.5 experimental per a community post [S]). Re-check when 2.5.0 ships.
11. **`kotlin.time.Clock` and `kotlin.concurrent.atomics`** stability and opt-in status on 2.4.21. If atomics still
    need an opt-in, use `atomicfu` or confine the flag to the engine's serial dispatcher [U].
12. **Konsist on KMP source sets:** whether its scope APIs see `commonMain` of every module in this layout [U].

---

## Sources (all accessed 2026-11-01)

Official docs and release notes:

- Android, Recommendations for Android architecture (last updated 2026-10-05): https://developer.android.com/topic/architecture/recommendations
- Android, State holders and UI state (last updated 2026-05-11): https://developer.android.com/topic/architecture/ui-layer/stateholders
- Android, Common modularization patterns (last updated 2026-03-05): https://developer.android.com/topic/modularization/patterns
- Android, Compose performance best practices (last updated 2026-10-22): https://developer.android.com/develop/ui/compose/performance/bestpractices
- Android, Navigation 3 animate destinations (last updated 2026-10-22): https://developer.android.com/guide/navigation/navigation-3/animate-destinations
- Android, Navigation 3 scenes / custom layouts (last updated 2026-10-22): https://developer.android.com/guide/navigation/navigation-3/custom-layouts
- Android, Navigation 3 release notes (1.1.0 2026-04-08, 1.2.0 2026-09-23, 1.3.0-alpha03 2026-10-21): https://developer.android.com/jetpack/androidx/releases/navigation3
- Android, Lifecycle release notes (2.10.0 2025-11-19, 2.11.0 2026-06-17, 2.12.0-alpha05 2026-10-21): https://developer.android.com/jetpack/androidx/releases/lifecycle
- Android, Strong skipping mode: https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- Android, Fix stability issues: https://developer.android.com/develop/ui/compose/performance/stability/fix
- Android, State lifespans in Compose (`retain`): https://developer.android.com/develop/ui/compose/state-lifespans
- Android, tracing release notes and in-process tracing guide: https://developer.android.com/jetpack/androidx/releases/tracing , https://developer.android.com/topic/performance/tracing/in-process-tracing
- Kotlin, Navigation 3 in Compose Multiplatform (last updated 2026-07-07): https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html
- Kotlin, What's new in Kotlin 2.4.0 (released 2026-07-14 per the page): https://kotlinlang.org/docs/whatsnew24.html
- Kotlin, What's new in Kotlin 2.4.20 (2026-09-07): https://kotlinlang.org/docs/whatsnew2420.html
- Kotlin, Coroutine exception handling (page dated 2026-07-20): https://kotlinlang.org/docs/exception-handling.html
- kotlinx.coroutines, `suspendCancellableCoroutine` KDoc (prompt cancellation guarantee): https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/suspend-cancellable-coroutine.html
- kotlinx.coroutines releases (1.11.0, 2026-05-08): https://github.com/Kotlin/kotlinx.coroutines/releases
- Kotlin, AGP 9 migration for KMP: https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html
- KEEP Rich Errors discussion #487: https://github.com/Kotlin/KEEP/discussions/487 ; use-case evaluation #498: https://github.com/Kotlin/KEEP/discussions/498

Libraries:

- Metro releases (1.5.1 2026-10-29): https://github.com/ZacSweers/metro/releases
- Metro docs (home, compatibility, stability, multiplatform, differences, MetroX ViewModel, MetroX Android): https://zacsweers.github.io/metro/latest/ , https://zacsweers.github.io/metro/latest/compatibility/ , https://zacsweers.github.io/metro/latest/stability/ , https://zacsweers.github.io/metro/latest/multiplatform/ , https://zacsweers.github.io/metro/latest/differences/ , https://zacsweers.github.io/metro/latest/metrox-viewmodel/ , https://zacsweers.github.io/metro/latest/metrox-android/
- Square / Block engineering, "Metro migration at Square Android" (2026-04-14): https://engineering.block.xyz/blog/metro-migration-at-square-android
- Cash App, "Cash Android moves to Metro" [S, not read in full]: https://code.cash.app/cash-android-moves-to-metro
- kotlin-inject-anvil maintenance-mode note [S via aggregator]: https://gittrend.io/repo/amzn/kotlin-inject-anvil , https://klibs.io/project/vRallev/kotlin-inject-anvil
- Koin releases (4.2.0 2026-03-17, 4.2.2 2026-06-15): https://github.com/InsertKoinIO/koin/releases
- Koin compiler plugin releases (1.2.1, 2026-09-10): https://github.com/InsertKoinIO/koin-compiler-plugin/releases ; compile safety docs: https://insert-koin.io/docs/reference/koin-compiler/compile-safety
- Dagger/Hilt KMP status [S]: https://github.com/google/dagger/issues/4130
- Circuit releases (0.39.0 2026-09-15) and docs: https://github.com/slackhq/circuit/releases , https://slackhq.github.io/circuit/ , https://slackhq.github.io/circuit/docs/navigation/
- Molecule releases (2.2.0 2025-09-24): https://github.com/cashapp/molecule/releases
- Decompose releases (3.5.0 2026-03-15, 3.6.0-beta02 2026-10-13): https://github.com/arkivanov/Decompose/releases
- Comparison posts [S]: https://www.quo.com/blog/modernize-your-kotlin-multiplatform-architecture/ , https://proandroiddev.com/diy-compose-multiplatform-navigation-with-decompose-94ac8126e6b5
- `retain {}` articles [S]: https://proandroiddev.com/understanding-retain-a-new-way-to-retain-state-in-jetpack-compose-5542962e56fa
- Kermit releases (2.2.0 2026-09-08) and docs: https://github.com/touchlab/Kermit/releases , https://kermit.touchlab.co/docs/
- Arrow releases (2.3.0 2026-10-26): https://github.com/arrow-kt/arrow/releases
- detekt coroutines rules (`SuspendFunSwallowedCancellation`): https://detekt.dev/docs/rules/coroutines
- Maven Central metadata used for every version and date in §2, e.g. https://repo1.maven.org/maven2/dev/zacsweers/metro/runtime/maven-metadata.xml (same pattern for each coordinate), and Google Maven, e.g. https://dl.google.com/android/maven2/androidx/navigation3/navigation3-ui/maven-metadata.xml

Internal:

- `/home/eslam/Storage/Code/wt/native-plan/research/kmp-cmp-status.md` (toolchain, Nav3/M3/AGP 9 details)
- `/home/eslam/Storage/Code/wt/native-plan/map/state-data.md` §1, §2, §3, §5, §14
- `/home/eslam/Storage/Code/wt/native-plan/map/screens-nav.md` §2–§5, §11
- `/home/eslam/Storage/Code/wt/native-plan/map/chat-genui.md` (C1 coalescing, perf notes), `protocols.md` (lines 271–306, 452–528), `packages-build.md` (R8 lessons)
- `/home/eslam/Storage/Code/wt/native-plan/catalog/raw-*.jsonl` (level counts in §1 and §10)
- `/home/eslam/Storage/Code/kmp-demo` (`App.kt` predictive-back latch, `state/ChatModel.kt` per-part state, `gradle/libs.versions.toml`)

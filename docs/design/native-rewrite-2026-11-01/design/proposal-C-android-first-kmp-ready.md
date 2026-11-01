# Proposal C: Android-first Jetpack Compose UI, Kotlin Multiplatform for everything below it

Written 2026-11-01 for the native rewrite of OpenCode Mobile (`io.github.eslamasabry.opencode_mobile`).
This is one of several competing architecture proposals. It argues for one angle as strongly as the
evidence allows, then states where that angle is weak.

**The angle.** Ship a Jetpack Compose Android app fast. Every module that is not UI is a Kotlin
Multiplatform (KMP) module from the first commit. That covers models, protocols, stores, engines,
**and the screen ViewModels**. The composables live in plain Android library modules. They are
written only with Compose APIs that also exist in Compose Multiplatform (CMP), so iOS and Linux
desktop can move them to CMP one module at a time, later. Android-only libraries sit behind
interfaces.

Inputs: the eleven maps in `../map/`, the nine research files in `../research/`, the catalog in
`../catalog/` (1,544 behaviours condensed from the 6,663 logic tests), the demo at
`/home/eslam/Storage/Code/kmp-demo`, the read-only Flutter source at
`/home/eslam/Storage/Code/wt/oc_app-batch1`, and the owner memory files. Nothing in any repo was
changed.

Tags: **[V]** checked today in a primary source. **[R]** read in the repo, the maps or the research
files (the file is named). **[S]** secondary source. **[U]** unverified or my estimate; check it in
slice 0.

---

## 0. Summary in one screen

| Question | Proposal C's answer |
|---|---|
| What runs on Android? | Jetpack Compose straight from Google's artifacts (Compose 1.12 → 1.13, Material 3 1.5, Navigation 3 1.2 → 1.3). CMP on Android runs this same code [R kmp-cmp-status §0], so smoothness is the same as the demo. C only removes the JetBrains wrapper and its release lag from the Android path. |
| What is multiplatform today? | Every module below the composables: `core`, `protocol`, `engine`, the screen **presentation** modules (ViewModels, `UiState`, reducers), routes, and the kit's design math. That is roughly 55–60 % of the code [U]. All of it compiles for `android` and `jvm` on every build. The `jvm` target is also where the 1,544 catalog behaviours run as tests. A nightly job compiles iOS klibs to keep `commonMain` free of JDK APIs. |
| What is Android-only today? | The composables (`:ui:kit`, `:feature:*:ui`), the Android string resources, and the bridges (`:android:*`, which reuse the existing 38k lines of Kotlin). |
| Why is that safe to port later? | The owner's kit-only rule already confines feature screens to "arrange kit parts with layout, scroll, semantics and focus" (kit-v2 §9). Those APIs exist unchanged in CMP. One import allowlist enforces both the kit-only rule and portability. The non-portable code therefore sits in **one** single-owner module, the kit, plus a mechanical `R.string` → `Res.string` codemod. |
| What does C buy? | (1) A shorter and lower-risk path to the Android in-place switch, because every UI-tooling choice is the mainstream Android default. (2) Jetpack-first versions for the "Android first and best" mandate: the Nav3 1.3 predictive-back fix, Compose 1.13 selection fixes and `imeNestedScroll` are usable the day Google ships them. (3) One Android string pool for the app, widgets, notifications, shortcuts and per-app language, which closes today's en/ar-only gap on native surfaces. (4) No portability cost for the roughly 15–20 % of the UI that only makes sense on Android [U]. (5) UI tests render with Android's own text stack on the VPS (Robolectric). (6) The iOS UI technology stays open: CMP, or SwiftUI over the shared ViewModels. |
| What does C cost? | A later conversion of the kit and of the screens a new platform needs: about **6–10 agent-weeks** [U]. Portability of the UI is a checked discipline, not a compiler guarantee. Agents get no desktop hot-reload loop for UI. Up-front savings are modest, about **3–6 agent-weeks** [U]. C is a schedule-risk bet, not a large total-cost win. |
| Decision rule | **Choose C** if iOS and Linux desktop are more than about 4–6 months away or uncertain, or if a SwiftUI iOS front end is a live option. **Choose CMP-UI-from-day-1** if a second platform is committed inside the Android build window. |
| Effort | About **95–125 agent-weeks** to parity, excluding AI Team and FactsBook (+8–12 agent-weeks for those). With 5 concurrent builders that is **19–25 calendar weeks**; the switch candidate for the owner's daily path comes at about **weeks 12–15**. Flutter-history calibration suggests the fast end is reachable (§18). Confidence is low to medium until slice 0 measures the real Kotlin throughput. |

---

## 1. The bet, stated plainly

The 2026-11-01 demo was already CMP (`shared/src/commonMain/.../ui/*.kt`) [R demo]. On Android,
CMP's `material3`, `ui` and `animation` resolve to Google's `androidx.compose.*` artifacts
[R kmp-cmp-status §0, motion-native §0]. **So C is not smoother than CMP-from-day-1. Anyone who says
otherwise is wrong.** C differs only in *where the composables live while Android is the only
platform*, and in *which release train the Android app rides*.

What C optimises:

1. **Time and risk to the in-place switch.** The switch is the one date that matters: Shorebird ends
   there, and every Flutter user moves at once. Every UI-side unknown that CMP adds is removed from that
   path:
   - CMP resources are packed as Android assets, and whether they follow per-app language is [U]
     [V CMP resources page; R kmp-cmp-status §6];
   - whether common UI tests run headless on the VPS is [U] [R testing-perf §4.3];
   - CMP Material 3 trails Jetpack M3 (CMP `1.13.0-beta01` = Jetpack M3 `1.5.0-beta01`, while Jetpack
     is at `beta02`);
   - CMP Navigation 3 is at 1.2.0, while Jetpack Navigation 3 has `1.3.0-alpha01` with "Fixed
     predictive back animation handoff in `NavDisplay`" [V Nav3 release notes].
2. **Android first and best, literally.** Android-only Compose APIs that the owner's rules need are
   used directly, with no `expect`/`actual`:
   - `Modifier.imeNestedScroll()` (Android source set only [V]);
   - the Android 14 non-linear font-scale converter for the 1.3× frame-text cap [R kmp-cmp-status §12.4];
   - `RenderEffect` for the two sanctioned frost surfaces [R chat-genui §0.3];
   - Termux `TerminalView` through `AndroidView`, because no KMP terminal view exists at production
     quality [R ui-kit UI-KIT-47];
   - `CameraXViewfinder`, `WebView`, and the Photo Picker contracts.
3. **Pay for portability only where it is certain to pay back.** Engines and ViewModels are portable
   from day 1 and are tested on the JVM, so Linux desktop gets them for free. Composables become
   portable when a second platform actually arrives, and only the screens that platform needs move.
   None of these screens ever goes to iOS: This phone, phone setup, Linux manager, Termux, local
   agent, the widget overlay, Manage space, the local terminal and the tiles screens
   [R screens-nav §11.10, builtin-linux §12].
4. **Keep the iOS UI decision open.** The owner froze Flutter because native feel won. On iOS the
   same reasoning may favour SwiftUI over CMP, since CMP draws its own widgets on iOS
   [R kmp-cmp-status §2]. C's boundary (KMP presentation, platform UI module) supports both choices
   without rework below the UI.

What C gives up: a compile-time proof that the composables are portable, and a desktop UI loop
(Compose Hot Reload with its MCP server) for agents during the Android phase.

---

## 2. Module graph

### 2.1 Layers (top depends on bottom; nothing reaches upward)

```
:androidApp                         application id io.github.eslamasabry.opencode_mobile, final Metro graph,
   │                                MainActivity + every OS-visible component name (frozen), signing, jniLibs,
   │                                migration gate, R8 rules, ABI splits
   ├── :feature:<x>:ui     ANDROID  composables, entry installers, @Previews, Robolectric/Roborazzi tests
   │      ├── :ui:kit      ANDROID  every visible component; the ONLY module that may depend on material3
   │      ├── :ui:words    ANDROID  all copy as Android string resources (7 languages, one file per feature)
   │      └── :ui:design   KMP      tokens, colour roles + contrast guard, styles resolver, motion tokens
   │                                + MotionPolicy, text-scale policy, window classes, capacity/max-width table
   ├── :feature:<x>        KMP      presentation: ViewModels, UiState, pure reducers, UI-facing mappers
   │      └── :app:routes  KMP      @Serializable NavKeys (one file per feature), AppNavigator interface
   ├── :android:<bridge>   ANDROID  reused Kotlin: runtime, agenthost, surfaces, schedule, media, platform,
   │                                terminal (+ termux only if kept). Implements engine interfaces.
   ├── :migration:flutter  ANDROID  first-launch reader of Flutter data; runs before engines start
   │
   ├── :engine:<area>      KMP      app- and connection-scoped logic (state machines, timers, streams)
   ├── :protocol:<x>       KMP      oc2, paseo, codex, a2a, (acp, gascity later); implement core:domain
   ├── :core:domain        KMP      ServerGateway, ServerOperationsGateway, ServerCapabilities,
   │                                PlatformCapabilities, the platform-service interfaces
   ├── :core:net           KMP      Ktor client factory, SSE parser + EventStream, WebSocket, TrustPolicy,
   │                                ExternalLinkPolicy
   ├── :core:data          KMP      KeyValueStore, SecretStore, BlobStore, ProfileScope, WriterLane, (Room later)
   ├── :core:markdown      KMP      streaming Markdown → Block model (org.jetbrains:markdown)
   ├── :core:common        KMP      Outcome/Failure, Redactor/Redacted, AppDispatchers, Clock, AppLog, Trace
   ├── :core:model         KMP      plain data types
   └── :core:testing       KMP      fakes, TestClock, TestDispatchers, fixture engine (test scope only)

:benchmark (Macrobenchmark + Baseline/Startup Profile generator)   :quality (Konsist rules, graph smoke,
component-name freeze test)                                       later: :desktopApp, iosApp/ (Xcode)
```

### 2.2 Module list with ownership and first slice

"Owner" is a role from `MODULE_OWNERS` (§17). *Decide* means an owner decision is pending (§21).

| Module | Kind | Holds (map ids) | Owner role | Starts |
|---|---|---|---|---|
| `:core:model`, `:core:common` | KMP | value types; `Outcome`, `Failure`, `Redactor` (one redaction module, DESKTOP-WEB-CROSS-36/37), dispatchers, clock, Kermit facade | core owner | slice 0 |
| `:core:domain` | KMP | gateway interfaces and `ServerCapabilities` (PROTOCOLS-37/38/42), `PlatformCapabilities` (16 flags), the platform-service interfaces in §11.3 | core owner (single) | slice 0 |
| `:core:net` | KMP + `jvmShared` | Ktor 3.6.1 on OkHttp 5.5.1, hardened (no retry-on-POST, no redirects, no proxy, per-profile leaf pin), cleartext consent, `100.64.0.0/10` and private-address checks, SSE parser (16 MiB cap) + backoff loop | backend lane 1 | slice 1 |
| `:core:data` | KMP + `androidMain` | `KeyValueStore` (Android = the existing `FlutterSharedPreferences` file), `SecretStore`, `BlobStore`, `ProfileScope` sweep, `WriterLane` | core owner (single) | slice 1 |
| `:core:markdown` | KMP | streaming block model, path chips, agent blocks, `proseForSpeech` | chat lead | slice 2 |
| `:protocol:oc2` | KMP | hand-written OpenCode 2 client, 91-event registry with `UnknownEvent`, dialect table, pairing (PROTOCOLS-*) | backend lane 1 | slice 1 |
| `:protocol:paseo`, `:protocol:codex` | KMP | Paseo WebSocket + mappers (P0 daily path), Codex app-server (P1) | backend lane 1 | slice 3 / 5 |
| `:protocol:a2a`; `:protocol:acp`, `:protocol:gascity` | KMP | external agents; ACP (P2); Gas City and phone-engine client (*Decide*) | backend lane 1 | later |
| `:engine:connection` | KMP | `ConnectionRegistry`, `ConnectionGraph` per live connection, status, location, quiet reconnect (STATE-DATA-10..13) | backend lane 1 (single) | slice 1 |
| `:engine:sessions` | KMP | session list, busy, pins, polling, read state, first-paint caches, return brief (14, 24–26) | backend lane 1 | slice 2 |
| `:engine:transcript` | KMP | event apply, 50 ms coalesced publish, turn model, settle, stall (CHAT-*, SCREENS-NAV-41/42) | chat lead (single) | slice 2 |
| `:engine:delivery` | KMP | offline queue, dispatch markers, receipts, online marker, steer-not-queue (18–20, 28) | backend lane 1 | slice 2 |
| `:engine:composer` | KMP | drafts, attachment vault, stash, photo recovery, phrase chips (21–23) | backend lane 1 | slice 3 |
| `:engine:requests` | KMP | approvals, questions, **one `FormSpec` engine**, gen-UI card codec/validator/receipts, auto-approval (16–17, 41) | backend lane 1 | slice 3 |
| `:engine:attention` | KMP | attention feed, monitor, notification rules, status-line combiner, alert copy, widget snapshot builder (29–33, ANDROID-PLATFORM Dart halves) | backend lane 2 | slice 4 |
| `:engine:agents` | KMP | phone agents, side connections, merged chat feed, handoff, session links (38–40) | backend lane 1 | slice 3 |
| `:engine:runtime` | KMP + `jvmShared` | built-in Linux policy ported from `lib/builtin`: setup engine, start/probe, recovery consumer, healing, idle, thermal guard, Linux manager parsers; and later the pure Kotlin policy files (§10) | backend lane 2 (single) | slice 3 |
| `:engine:schedule` | KMP | recurrence, claims, receipts, recovery, runner. One implementation replaces today's Dart + Kotlin pair (ANDROID-PLATFORM R3) | backend lane 2 | slice 4 |
| `:engine:voice`, `:engine:media` | KMP | voice controller, model manifest + RAM gate, resumable downloader, silence, auto-send filter; MIME gate, SVG sanitizer, office text, photo caps, PDF budgets | backend lane 2 | slice 5 |
| `:engine:settings` | KMP | prefs facade, appearance prefs, consent stores (three-state reads), settings scope, teaching, nudges, first run | backend lane 1 | slice 2 |
| `:engine:diagnostics` | KMP | ring buffer, problem report, crash builder, perf marks, update notice | backend lane 2 | slice 2 |
| `:engine:integrations`, `:engine:files` | KMP | MCP, providers, GitHub, usage/quota, BYO host; project hub, git, review/diff model, terminal session model | backend lanes | slice 5 |
| `:engine:entry` | KMP | `EntryRouter`: one pending-intent mechanism replacing seven (SCREENS-NAV §4) | coordinator | slice 2 |
| `:engine:team` | KMP | AI Team (*Decide*: which tier survives) | backend lane 2 | later |
| `:app:routes` | KMP | NavKeys (one file per feature), `AppNavigator` | coordinator; feature agents add only their own file | slice 0 |
| `:ui:design` | KMP | the kit's pure half (UI-KIT-01..07, 15) | kit owner (single) | slice 1 |
| `:feature:{shell, chats, chat, files, servers, phone, agents, team, settings, about, media, surfaces, factsbook}` | KMP | presentation for SCREENS-NAV features (map in §13) | UI lanes (chat = chat lead) | slices 2–7 |
| `:ui:kit` | Android | every component (UI-KIT-08..58), platform seams (§11.4) | kit owner (single) | slice 1 |
| `:ui:words` | Android | `res/values*/strings_<feature>.xml`, `Failure` → words mapping, locale config | l10n owner; each feature agent edits only its own file | slice 1 |
| `:feature:<x>:ui` × 13 | Android | composables arranging kit parts; frozen-FQCN activities where needed (`WidgetOverlayActivity`, `ManageSpaceActivity`) | UI lanes | slices 2–7 |
| `:android:runtime`, `:android:agenthost` | Android | `BuiltinLinux` split + helpers, services, receivers, PTY; `PhoneAgent*`, `PhoneEngine*`, leases | backend lane 2 (both halves) | slice 3 |
| `:android:surfaces` | Android | notification 4747, coding alerts, 7 widget providers (RemoteViews, same layouts and preview PNGs), 2 tiles, shortcuts | backend lane 2 | slice 4 |
| `:android:schedule`, `:android:media`, `:android:platform`, `:android:terminal` | Android | alarms + worker; sherpa-onnx, AudioRecord, TTS, CameraX + zxing-cpp, isolated PDF service; network/thermal/lifecycle/crash/storage/export/BYO signer/Tailscale/**APK updater**; PTY and terminal sessions (the `TerminalView` wrapper itself lives in the kit, §11.4) | backend lane 2 | slices 4–5 |
| `:migration:flutter` | Android | the §12 recipe | migration owner (single, Opus) | slice 0 spike, slice 6 |
| `:androidApp`, `:benchmark`, `:quality` | Android / JVM | composition root; perf; rules | coordinator | slice 0 |

At full size that is about 75 modules. **Create a module only when its first slice starts**, and
start small engines grouped (`:engine:settings` holds teaching and diagnostics until either grows),
so the graph is about 40 modules for the first two months. Module count costs configuration time and
memory on the 16 GB PC [R kmp-cmp-status §8]. Configuration cache is on from day 1.

### 2.3 Why two modules per feature

`:feature:chat` (KMP presentation) and `:feature:chat:ui` (Android composables) are separate modules
for three reasons:

1. The presentation is compiled for `jvm` and checked for iOS. That gives a compiler-backed guarantee
   that the part most expensive to get wrong, the screen logic, is portable.
2. It matches the owner's working rule "Codex builds backend with a frontend contract; Claude builds
   UI from it" (memory `codex-backend-claude-frontend`): one module per agent, so no shared files.
3. The UI module is a plain `com.android.library`. Full Android tooling therefore works without the
   AGP-KMP plugin's limits (single variant, Android resources and host tests opt-in) [R kmp-cmp-status §8]:
   - Android resources;
   - `src/test` Robolectric;
   - Android Lint with the Compose checks (lint coverage for KMP `commonMain` is unconfirmed [U]);
   - Android Studio previews.

---

## 3. Layering and dependency rules

### 3.1 The table (Gradle-enforced)

| From | May depend on | Must not depend on |
|---|---|---|
| `:feature:x:ui` | `:feature:x` (its own presentation only), `:ui:kit`, `:ui:words`, `:ui:design`, `:app:routes`, `:core:model`, `:core:common` | engines, protocols, `:core:net`, `:core:data`, any other feature, **material3**, any `:android:*` |
| `:feature:x` | `:engine:*`, `:core:domain`, `:core:model`, `:core:common`, `:app:routes`, lifecycle-viewmodel (KMP) | protocols, `:core:net`, `:core:data` implementations, Compose UI, other features |
| `:engine:*` | `:core:*`, lower engines in the declared DAG (`connection` at the bottom) | `:ui:*`, features, protocols (gateways arrive through DI interfaces) |
| `:protocol:*` | `:core:domain`, `:core:net`, `:core:model`, `:core:common` | engines, UI, features |
| `:ui:kit` | `:ui:design`, `:ui:words`, `:core:common`, material3, Coil, and the Android UI libraries its seams wrap (terminal-view, CameraX viewfinder; §11.4) | features, engines, business types |
| `:android:*` | the interfaces they implement (`:core:*`, `:engine:*`), `:ui:words` (copy), `:ui:design` (widget colours) | features, `:ui:kit` |
| `:androidApp` | everything | nothing |

This makes two AGENTS.md rules a matter of build structure:

- "UI talks to the domain gateway only." Features cannot see a protocol module.
- "Gate on `ServerCapabilities`, never on the flavor enum." Features cannot see backend implementations.

### 3.2 Enforcement (cheap first)

1. **Convention plugins** in `build-logic`: `oc.kmp.core`, `oc.kmp.presentation`,
   `oc.android.compose-ui`, `oc.android.bridge`, `oc.android.app`. Each one fails configuration if a
   declared project dependency breaks the table. That is about 30 lines of Gradle
   [R architecture-stacks §6.2].
2. **One import allowlist for `:feature:*:ui`, run as a Konsist test in `:quality`.** It is the kit-v2
   §9 allowlist (arrange, scroll, semantics, focus) intersected with the CMP API surface. Allowed:
   - `androidx.compose.runtime.*`;
   - `androidx.compose.foundation.layout.*`, `foundation.lazy.*`, and the scroll and
     `BasicText`-free parts of `foundation`;
   - `androidx.compose.ui.{Modifier, Alignment, unit.*, semantics.*, focus.*, input.key.*}`;
   - `androidx.compose.ui.platform.{LocalLayoutDirection, LocalDensity, LocalFocusManager}`;
   - `androidx.lifecycle.compose.*` and the Metro ViewModel accessor;
   - `androidx.navigation3.runtime.*` in the feature's `Entries.kt` only;
   - kit, design, routes, its own presentation, `core.model`, `core.common`;
   - **one sanctioned exception**: `androidx.compose.ui.res.stringResource` /
     `pluralStringResource` with the `:ui:words` `R`.

   **Everything else fails the build**, including:
   - `android.*`, `androidx.activity.*`, `androidx.core.*`, `material3`;
   - `LocalContext`, `LocalView`, `ui.viewinterop`;
   - `java.*`;
   - raw `tween`/`spring`/`animate*` (MOT-1);
   - raw `clickable` and `performHapticFeedback` (G2);
   - `Dialog` and `ModalBottomSheet`.

   A small text check also bans `String.format`, `System.` and `Thread.` in UI modules. Those are
   JVM-only APIs that need no import.
3. **The same rules for presentation** come mostly free from the compiler: `jvm` has no `android.*`.
   A nightly **iOS klib compile** (`-Poc.ios=true` on the VPS, no cinterop) catches JDK leaks into
   `commonMain` [R kmp-cmp-status §11.3]. Add Konsist checks:
   - "no `runCatching` in engine, protocol or presentation";
   - "no `Dispatchers.*` or `Clock.System` outside `:core:common`";
   - "ViewModels hold no strings";
   - "no `e.message` reaching a `Text(`" [R architecture-stacks §6.2, §7.3].
4. `dependency-analysis` 3.20.1 and `explicitApi()` in `core`, `protocol` and `engine` (run on the VPS).

### 3.3 Source sets (KMP modules)

| Source set | Targets | What may live there |
|---|---|---|
| `commonMain` | android, jvm (+ iOS klib nightly) | everything by default |
| `jvmShared` (custom intermediate, android + jvm) | android, jvm | JVM-only code that Android and Linux desktop share: the OkHttp engine factory and TrustManager pinning in `:core:net`; `/proc`, process-tree and orphan-reaper logic in `:engine:runtime`; `java.nio` file stores. iOS never needs these. [U: IDE support for custom android+jvm intermediates; prove it in slice 0] |
| `androidMain` | android | Android SDK actuals, used rarely (`:core:data` prefs and Keystore). Most platform code lives in `:android:*` modules instead. |
| `commonTest` | runs as `jvmTest` on the VPS | the catalog rows |

**No `expect`/`actual` outside `:core:common`.** Platform differences are interfaces bound in DI
(§5). Tests then use fakes, the `jvm` target needs no actuals during the Android phase, and agents
learn one mechanism instead of two.

---

## 4. Toolchain pins (C's variant)

These follow `kmp-cmp-status.md` §11, with one change: **UI modules use Google's Jetpack artifacts,
not JetBrains'.**

| Item | Pin now | Move when |
|---|---|---|
| Kotlin (+ Compose compiler plugin) / AGP / Gradle / JDK | 2.4.21 / 9.3.3 / 9.7.x / 17 (21 for the Robolectric test JVM) | Kotlin 2.5 with Metro's matching release |
| Jetpack Compose (ui, foundation, animation, runtime) | 1.12.1 | **1.13.x stable**: selection fixes while text updates, deferred transitions without opt-in [R rendering-chat §0, motion-native §0] |
| Jetpack Material 3 | 1.5.0-beta02, kit only | 1.5.0 stable |
| Jetpack Navigation 3 / NavigationEvent | 1.2.0 / 1.1.x | **1.3.x stable** (the predictive-back handoff fix) [V] |
| Lifecycle (ViewModel, KMP in presentation; compose in UI) | 2.11.0 | follow AndroidX |
| Ktor / OkHttp / serialization / coroutines | 3.6.1 / 5.5.1 / 1.12.0 / 1.11.0 | as network-data |
| Metro (DI) | 1.5.x, pinned in lockstep with Kotlin | with Kotlin |
| compileSdk / targetSdk / minSdk | 37 (37.1 if Compose 1.13 needs it) / **36 at the switch** / 26 | targetSdk 37 as its own slice after the switch [R android-surfaces §0.1] |
| versionCode | a new base **≥ 10000** | never goes back [R packages-build §0.4] |

CMP plugin versions do not appear in the Android build until the first UI module converts (§16).

---

## 5. Dependency injection

**Metro 1.5.x**, as `architecture-stacks.md` §3 recommends. Koin 4.2 with its compiler plugin is
plan B.

- **Scopes.**
  - `AppScope` (process): stores, Redactor, Diagnostics, `ConnectionRegistry`, `AttentionFeed`,
    `ScheduleEngine`, `PhoneRuntime`, `StatusLine`, `UpdateNotice`, `Teaching`.
  - `ConnectionScope`, a graph extension, one per **live** connection (several can be alive). It
    holds the gateway pair, `EventStream`, `SessionList`, `Selection`, `Approvals`, `PromptDelivery`,
    `ReadState`, caches and drafts. Closing it stops admission, cancels the stream, drains the writer
    lanes, then releases.
  - Flutter's per-conversation backend becomes `ConnectionRegistry.graphFor(conversationRef)`
    (SCREENS-NAV-110).
- **Aggregation, so no agent edits a central file:**
  - protocol factories via `@ContributesIntoMap(AppScope) @BackendKey`;
  - platform services via `@ContributesBinding(AppScope)` from each `:android:*` module;
  - ViewModels via the ViewModel multibinding (exact `metrox-viewmodel` annotation names [U]);
  - Nav3 entry installers via `@ContributesIntoSet`.
- **Android components** reach the graph through `(applicationContext as GraphOwner).appGraph`:
  services, receivers, workers, widget providers, tiles, and the overlay and manage-space activities.
  The Flutter app ran three separate Dart engines that had to `prefs.reload()` around each other
  [R state-data §1.10]. Native is one process and one graph. `metrox-android` stays out, because it
  needs API 28 and minSdk is 26.
- **Composables never touch DI.** A UI module receives its ViewModel through the entry installer and
  passes plain state into kit parts.
- **Risk controls** [R architecture-stacks §3.5]: Kotlin and Metro are upgraded as a pair; a
  graph-smoke `jvmTest` resolves every multibinding key; `@Inject` classes carry no other Metro
  types; experimental Metro features stay off.

---

## 6. State management

Two kinds of state holder share one idiom, `StateFlow` [R architecture-stacks §4]:

1. **Engines** (KMP, app or connection scope) own the truth: streams, timers, queues and state
   machines. They expose `StateFlow`, take `suspend` commands and return `Outcome`.
2. **ViewModels** (KMP presentation modules, `androidx.lifecycle.ViewModel`) `combine` engine flows
   into one `StateFlow<XUi>` and turn taps into engine calls. They hold no business rules. A rule that
   a widget, notification or tile also needs belongs in an engine.

Rules for the rewrite's AGENTS.md:

- One `UiState` per screen.
- No one-shot event channels. "Show undo" or "navigate after create" is a state field that the UI
  acknowledges.
- Explicit sealed-state reducers for send/stop/steer, turn stall, delivery, approvals, connection
  status, setup and the phone runtime. These get exhaustive table tests in `commonTest`.
- **No strings in state.** State carries ids, enums and `Failure` categories, and the UI resolves
  them through `:ui:words`. This is what lets the presentation stay KMP while the strings stay Android
  resources.
- Collect with `collectAsStateWithLifecycle()`.

**The streaming hot path** [R architecture-stacks §4.3, rendering-chat §0, motion-native §9.3]:

- `TranscriptEngine` runs on a serial dispatcher and applies each event synchronously.
- It publishes an immutable `TranscriptState` at most every 50 ms, flushing the first delta at once
  (catalog C1 parity), and uses `PersistentList` turns.
- The UI is one `LazyColumn(reverseLayout = true)` flattened to block items with stable keys and a
  `contentType` per kind.
- The live block grows on a critically damped spring; fresh words fade through a draw-phase mask;
  `preferredFrameRate(High)` applies only while the block reveals.
- Escalation, only if Macrobenchmark shows jank: give the live part its own `StateFlow<String>`.

---

## 7. Navigation

**Jetpack Navigation 3, used directly in the Android shell.**

- **Routes.** `:app:routes` (KMP) holds `@Serializable` keys. `NavKey` comes from
  `androidx.navigation3:navigation3-runtime`, which is itself KMP (JVM, Android, Native, Web) [V Nav3
  release notes]. Each feature owns one file with its own `sealed interface XRoute : NavKey`. A
  feature that opens another feature depends only on `:app:routes`, never on that feature.
- **`AppNavigator`** (commonMain interface; the only navigation ViewModels and engines may call):
  - `open(route)`;
  - `openChat(ref, focusComposer)`, which de-duplicates an already-showing chat (`c5842e836`);
  - `land()`, which clears to Home;
  - `replaceRoot(route)`;
  - `back()`.

  The shell in `:feature:shell:ui` implements it over the `NavBackStack`. Tests use a recording fake.
- **`EntryRouter`** (`:engine:entry`, pure) replaces seven copies of the pending-intent mechanism.
  Requests come from launcher shortcuts, links, share-in, notifications, widgets, tiles and
  scheduled-run taps. They wait for bootstrap and migration, are consumed once, then become
  `AppNavigator` calls [R screens-nav §11.2].
- **Entry installers** are contributed by each `:feature:x:ui` through Metro. The shell assembles
  them, so no central entry list exists.
- **Sheets and dialogs are screen state**, drawn by `KitSheetHost` on the in-window M3 1.5
  `BottomSheet`, which handles predictive back itself. A sheet becomes a route only if a deep link
  must reach it.
- **Wide windows.** Scenes for rail, sidebar and two- or three-pane layouts use the `KitLayout`
  thresholds (600/840/1200, short height below 480), with "absent, never disabled".
- **Predictive back with the row → chat container transform** (the demo's fight):
  - **Ship option A**: `sharedBounds` on tap and on button-back; the platform predictive motion while
    a gesture is live, with shared bounds dropped during `InProgress`; no 700 ms latch.
  - **Run spike B in parallel**: Jetpack Nav3 1.3 alpha + Compose 1.13 deferred two-stage
    transitions with `permitTransformDuringDeferredTransition` [R motion-native §0.1, §4.4]. One Opus
    agent, emulator only.
  - **This is where C's Jetpack-first train pays directly.** B can ship the week Google marks Nav3 1.3
    stable, without waiting for JetBrains to port it.
  - The root never consumes back. The chat pops only when nothing is lost (the draft rule).

---

## 8. Threading

As `architecture-stacks.md` §8, adopted unchanged:

- **One serial dispatcher per engine** and per per-profile store (`default.limitedParallelism(1)`).
  This restores the "nothing runs between two lines unless I await" assumption the Dart code was
  written under. Heavy pure work (big JSON pages, 5,000-part merges, Markdown parse) goes to `default`
  and comes back as a value.
- **Scopes replace generation counters.** Each `ConnectionGraph` has a child scope. Prompt
  cancellation means a cancelled generation never resumes after an `await`. Identity re-checks remain
  only for writes into stores that outlive the generation.
- **`WriterLane`**: an admission flag plus a fair `Mutex`, so `closeProfile()` means "stop now, drain
  what was admitted". Writes use `commit()`, never `apply()`, because callers branch on the result
  (fail-closed) [R state-data §10.4].
- **Flows.** `StateFlow` carries state. Durable SSE events are never dropped; volatile deltas may be
  coalesced. Reconnect reconciles by refetch, never by replay. Facts for one consumer travel on a
  `SharedFlow(replay = 0)` with an explicit buffer policy. `AppDispatchers` and `Clock` are injected
  so virtual time works in tests.
- **Android lifetime.** Engines live in the process; Android decides how long the process lives.
  Work that must outlive the UI runs under the existing foreground-service rules:
  - "watch this reply" as a short `dataSync` start on background-with-active-turn, stopped when the
    turn settles;
  - the phone server stays `specialUse`;
  - schedules run as AlarmManager + WorkManager with the 8-minute worker budget;
  - both `onTimeout` overloads are handled.

  Nothing assumes unbounded lifetime (the AGENTS.md 6 h `dataSync` rule) [R android-surfaces §3].
- **Process model.** One process. The isolated `PdfRenderer` service is the only extra process. No
  worker spawns processes, because Android kills an app's child processes above 32 [R builtin-linux §0.6].

---

## 9. Error model

- **`Outcome<T>` + sealed `Failure`**, a port of `ProductFailure` [R architecture-stacks §7]. Expected
  failures are values; exceptions mean bugs or cancellation. Exceptions from Ktor, kotlinx.serialization
  (with `exceptionsWithDebugInfo = false`), Keystore, proot and the shell are translated **once**, at
  the protocol, core and Android-bridge boundary. Nothing above `engine` sees a transport type.
- **Plain words.** `Failure` is closed, so `when` is exhaustive in `:ui:words`, and adding a category
  forces someone to write its copy. Details appear only as `Redacted` under the Details fold, next to
  "Report a problem". No `String` path exists from a server to a `Text`. The mapping tables
  (`agent_error_words`, `send_failure_words`, `git_words`, `permission_presentation`, …) become data
  plus resolvers. Their logic tests port first, asserting `Failure` → message key in `commonTest`
  and English text parity in one Robolectric test.
- **`runCatching` is banned** around suspend code (it swallows cancellation). Use
  `attempt(map) { }`, which rethrows `CancellationException`.
- **The crash model is different.** Flutter swapped a failing widget for `KitErrorFallback` and kept
  running; an exception in Compose composition kills the process [R desktop-web-cross §1.7]. Therefore:
  - the uncaught handler is installed first in `Application.onCreate` and writes the category-only
    crash record (`NativeCrashStore`, reused);
  - a **crash-loop counter** at startup opens a safe mode (Settings, Report a problem, Start fresh)
    after N fast crashes;
  - derived UI models are built in ViewModels (try/catch → `Failure`), never inside composition;
  - every root coroutine has a `SupervisorJob` and a logging `CoroutineExceptionHandler`.
- **Privacy.** Kermit sits behind `AppLog`. The `RedactingWriter` comes first, then a bounded ring
  buffer. Logcat is used in debug builds only. Nothing phones home. Release builds must not contain
  `HttpLoggingInterceptor` or `Log.*` calls outside the facade [R desktop-web-cross §9.2].

---

## 10. Reusing the 38,441 lines of existing Kotlin

The Android layer is already native: 168 files and 25,465 lines in `K/`, plus 46 JVM test files with
645 `@Test` and 5,109 lines of instrumentation. Only 36 files import `io.flutter`
[R packages-build §0.1, android-platform §10]. C reuses it **as Android code first**: copy, delete
the channel glue, stay green. Pure parts are promoted to KMP **only when a second platform or a
shared engine needs them**. That keeps day 1 green and makes every promotion a separate, reviewable
change.

| Group (lines) | Today | Native home | What changes | Tests |
|---|---|---|---|---|
| Built-in Linux core: `BuiltinLinux.kt` 4,511 + ~60 helpers ≈ 9k | runtime, install, processes, FGS, recovery, restore, idle, wake locks, setup job | `:android:runtime`. `BuiltinLinux` is split into RuntimeInstall, ProotLauncher, ServiceRegistry, InstallerSlot, ServerRecovery and IdleController; the process-wide singleton stays | Delete `MainActivity.handleBuiltinLinux`, `NativeChannelReplies` and `setupSpec` glue; a typed `RuntimeHost` interface (in `:engine:runtime`) replaces the channel. `flutter.*` reads stay working on day 1, because the same prefs file is the `KeyValueStore` (§12). **Never** simplify the `NativeRuntimeOwnership.plan` family. | ~30 JVM test classes + 8 instrumented acceptances carried unchanged |
| Pure policy Kotlin: `RestartBackoff`, `NativeRecoveryBudget`, `NativeProcessTree`, `NativeOrphanReaper`, `WorkLeases`, `IdleStopPolicy`, `SetupStepRetry`, … | JVM-tested rules | Phase 1: in `:android:runtime` as-is. Phase 2: moved into `:engine:runtime` `jvmShared` (they use `java.io` and `/proc`-shaped data, which Linux desktop also has) | package move only | tests move with them |
| Agent host + phone engine ≈ 4.7k | `PhoneAgent*`, `PhoneEngine*`, `PaseoStaleLock`, `Native*Admission/Readiness/Idle*` | `:android:agenthost` | glue removal; every admission and drain rule kept | carried |
| Notifications/background 2.9k | services (id 4747 shared row), receivers, tiles, `NativeStrings`, `NativeNotificationLocale` | `:android:surfaces` | Wording comes from `:engine:attention` (a port of Dart `buildNotificationStatus`). Copy comes from `:ui:words` in all 7 languages, which deletes `NativeNotificationLocale` and the en/ar-only gap (T16). | carried |
| Widgets 4.3k | 7 providers, RemoteViews binders, size policy tuned on the ZTE launcher, overlay activity, `WidgetActionRuntime` (headless Flutter engine) | `:android:surfaces` (providers, layouts, `previewImage` PNGs **unchanged**); `WidgetOverlayActivity` becomes Compose in `:feature:surfaces:ui` with the **same FQCN** | The headless engine is replaced by a coroutine in the existing short service, calling the shared gateway (~0.25 s cold core start instead of an engine boot) [R android-platform §2]. Watchdogs (30 s/420 s) kept. | Robolectric render tests carried |
| Scheduled prompts 1.3k | alarm + WorkManager + Kotlin CAS store + headless engine | `:android:schedule` (alarms, worker, receivers, store); the runner moves to `:engine:schedule` | `ScheduledPromptRuntime` is deleted. Exactly one recurrence implementation, with DST golden tests. Claims and receipts kept, never "unique work" [R android-platform R9]. | carried + the Dart suite ported |
| Launch/OS bridges 3.8k | `MainActivity`, `Launch*`, `ManageSpaceActivity`, Tailscale, storage, export, PDF, read aloud, network, thermal, lifecycle, crash, BYO signer | `:androidApp` (MainActivity, Launch*), `:android:platform`, `:android:media` (read aloud, PDF), `:feature:settings:ui` (Manage space, same FQCN) | `FlutterFragmentActivity` becomes `ComponentActivity`; `enableOnBackInvokedCallback` kept | carried |
| Termux 0.8k + `PtyAccess.java` | legacy host | `:android:termux` only if Q1 keeps it; `PtyAccess` → `:android:terminal` | — | per Q1 |

**OS-visible names are a contract** [R android-platform §0.2, R1]:

- These must keep the exact fully qualified class name (FQCN) or id: widget providers, tile
  services, `MainActivity` (pinned shortcuts), WorkManager worker class names, receivers,
  notification channel ids, shortcut ids and the `opencode-mobile://` scheme. Kotlin package names
  are independent of a library module's `namespace`, so classes can move between modules and keep
  their FQCN.
- `:quality` holds a **component-freeze test**: the merged manifest's component names plus the worker
  class names must equal a list extracted once from the Flutter 2.1.0+57 release APK.
- Keep the release-build guards: the engine-binary SHA task, the AGP strip exclusion for
  `libaiteam_*`, `useLegacyPackaging`, 16 KB alignment, `verify_scheduled_prompt_apk.py` and
  `verify_widget_release_rules.py` [R packages-build §0.6, testing-perf §0.9].

---

## 11. Android-only libraries behind interfaces

### 11.1 Rule

An Android-only library or API is touched in exactly one of three places:

- an `:android:*` module, behind an interface from `:core:domain` or an engine;
- the kit's platform seams (§11.4);
- `:androidApp`.

A desktop or iOS platform later supplies its own binding, or an `Unavailable*` binding with the
capability flag false. "Absent, never a dead button" (desktop-web-cross §1.2).

### 11.2 Day-1 picks (Android-best, fewest dependencies)

| Need | Day-1 Android implementation | Why not the KMP library now |
|---|---|---|
| Photos, files, camera | Photo Picker, SAF and `TakePicture` contracts directly, behind `FileDialogs`/`CameraPhoto` | FileKit 0.17 is good, but it is one more dependency until desktop needs it [R media §3]. Reconsider at desktop time. |
| QR | CameraX 1.6 + zxing-cpp 3.1.1 (drops ML Kit; 0.73 MB vs 2.4 MB) | zxing-cpp also has KN artifacts for later [R media §0] |
| Images | Coil 3.6 | already KMP |
| Speech | sherpa-onnx Kotlin API 2.0.1, models read in place from `filesDir/voice_models` | add consumer R8 keep rules [R media §0.3] |
| PDF | the existing isolated-process `PdfRenderer` service (102 lines) | — |
| Terminal | Termux `terminal-emulator`/`terminal-view` via `AndroidView` in the kit | no KMP terminal at production quality [R ui-kit UI-KIT-47] |
| Widgets | RemoteViews XML (not Glance) | ZTE launcher size-map trap [R android-surfaces §0.6] |
| Live Updates | `NotificationCompat` 1.19.1 `ProgressStyle` for user-started runs only | — |
| Secure storage | own Keystore AES-GCM `SecretStore` | `security-crypto` is deprecated [R network-data §7] |
| AppFunctions | thin service on `1.0.0-beta01`, never a required path | pre-1.0 [R android-surfaces §0.7] |

### 11.3 Interfaces (in `:core:domain` or the owning engine)

| Interface | Android binding |
|---|---|
| `RuntimeHost` | `:android:runtime` |
| `AgentHost` | `:android:agenthost` |
| `AttentionSurface`, `LaunchSurfaces` | `:android:surfaces` |
| `BackgroundHost`, `JobScheduler` | `:android:surfaces`, `:android:schedule` |
| `DeviceFacts` (network, thermal, exit history, storage) | `:android:platform` |
| `SecureSigner`, `AppUpdater` | `:android:platform` |
| `SpeechToText`, `NaturalVoice`, `SystemVoice`, `MicCapture`, `PcmPlayer`, `AudioCoordinator` | `:android:media` |
| `PdfRasterizer`, `QrScanner` | `:android:media` |
| `FileDialogs`, `ShareLinks` | `:android:platform` |
| `KeyValueStore`, `SecretStore`, `BlobStore` | `:core:data` androidMain |
| `PlatformCapabilities` | `:androidApp` |

### 11.4 Kit platform seams (the only non-portable UI)

These are the Android-specific pieces, all inside `:ui:kit`, which means a single owner:

- the text-scale cap (Android 14 non-linear converter);
- `imeNestedScroll`;
- frost `RenderEffect` (API 31+, solid fallback);
- the haptics mapping;
- ripple (platform `RippleDrawable`);
- the reduced-motion source (animator scale, Saver, thermal) behind `MotionPolicy`;
- edge-to-edge and system bars;
- `KitTerminal` (AndroidView);
- `KitWebPreview` (hardened WebView);
- `KitScanner` (CameraXViewfinder);
- the activity-result launchers wrapped as `rememberPickPhotos()` and similar;
- the permission prompt;
- `ExternalLinkUriHandler` routing every link through `openExternalLink`.

When the kit converts to CMP (§16), these and nothing else become `expect`/`actual` or interfaces.
Feature UI never sees them.

---

## 12. Persistence and the day-1 in-place migration

C's pragmatic rule: **the switch release changes as few stored formats as possible.** That release
has exactly one irreversible-feeling step, the secrets, and that step keeps its source.

1. **`KeyValueStore` on Android is the existing `FlutterSharedPreferences` file**, adopted in place:
   - every key keeps its `flutter.` prefix;
   - every encoding is kept: JSON strings, Long ints, both list encodings, the double prefix;
   - writes use `commit()`.

   This removes a copy step. Every reused Kotlin reader (widgets, tile, scheduler, recovery, locale)
   keeps working, and the `oc.<what>.<profileId>` deletion sweep is unchanged
   [R network-data §0.6, state-data §1].
2. **Big blobs stay in prefs for the switch release.** That covers the offline queue with its
   dispatch markers, the drafts index, stash and receipts. They are ported 1:1 with the same lenient
   JSON codecs, so the 158 STATE catalog rows test the same semantics. **Room 3 arrives in switch+1**
   behind the same repository interfaces, with a counted, SHA-verified import and a `profile_id`
   column on every table. *Trade-off, stated:* a large queue XML slows cold start until switch+1.
   Mitigations: warm prefs on an IO thread in `Application.onCreate`, and record the file size in
   local diagnostics. *Decide* (§21) if the owner wants Room in the switch release, as
   network-data recommends.
3. **Secrets.**
   - Back up first: copy `FlutterSharedPreferences.xml`, `FlutterSecureStorage.xml`,
     `FlutterSecureKeyStorage.xml` and `FlutterSecureStorageConfiguration*.xml` byte for byte to
     `files/migration/flutter-v57/`, before the first `getSharedPreferences` call.
   - Decrypt `flutter_secure_storage` v11 natively. The recipe is AES-128-GCM values under an app key
     that is RSA-OAEP-wrapped by an AndroidKeyStore alias derived from the package name. The same
     package and the same signer keep that alias reachable.
   - Register each secret with the Redactor **before** any log line, then re-encrypt into the own
     `SecretStore` and read it back to verify.
   - On failure, set that profile's "re-enter password" flag and continue. Never abort, and never
     treat a failure as an empty password.
   - Keep the old files [R network-data §8.3, §9; state-data §10.1].
4. **Files are not moved**: the Linux rootfs (`files/linux/ubuntu`), `projects/`, engine dirs,
   `voice_models/`, `draft-attachments-v1/`, `prompt-photo-recovery-v1/`, `diagnostics/`, the crash
   files with their consent epoch, and `app_flutter/` (read once).
5. **Authority gates.** The three `flutter.*` keys that let Kotlin restore the phone server after
   reboot or update must survive. An emulator test edits recovery prefs, runs `pm unstop`, installs
   with `install -r`, then reboots [R builtin-linux §0.4, T1; memory `restore-proof-recipe`].
6. **Gate UI.** `MigrationGate` renders "Updating your app…" **immediately**, so no Keystore stall can
   leave a blank window. Steps are idempotent, and the marker `native.migration.v1` is recorded per
   step. A failure shows plain words plus Details, and a retry is always possible because the source
   data is untouched. `EntryRouter` holds every launch intent until the gate opens.
7. **Identity.** The package is unchanged. **versionCode ≥ 10000.** On the signer, the brief says
   `1DE5BF08…D60C` (the owner's local key, which is on the owner's phone), while the map shows public
   GitHub releases signed by `842284B2…1C`; Android refuses an update across lineages
   [R packages-build §0.3]. Verify the installed signer with `apksigner` before delivery, and make
   the owner decide whether GitHub users get an update path at all (§21).
8. **Shorebird ends at the switch.** The native `AppUpdater` (GitHub `releases/latest` +
   `SHA256SUMS` + same-signer check + `PackageInstaller`, `REQUEST_INSTALL_PACKAGES`) must be in the
   switch release itself, because native crashes cannot be hot-patched [R android-platform R5].

---

## 13. How each subsystem in the maps lands

| Map (features) | Lands in | Day 1 / later | Notes specific to C |
|---|---|---|---|
| **protocols** (46) | `:protocol:oc2`, `:core:net`, `:core:domain` | Day 1 | Hand-written Ktor client. Own SSE loop: 1 s → 16 s backoff, reset after 30 s stable, "disconnected" after 6 tries while still retrying, reconcile on every (re)connect. No OpenAPI codegen; the spec is used for drift tests. OpenCode 1 is only *recognised*. Fresh probe of the 2.1.1 pin, plus a 2.2.x probe, before slice 1 [R protocols §0.3]. Contract rows (267) replay recorded fixtures through `MockEngine` in `commonTest`. |
| **state-data** (55) | `:core:data` + engines | Day 1 | Prefs in place (§12); writer lanes; three-state reads; lenient JSON written **before** any store, with the Redactor port first [R state-data §11 order]. |
| **agents-backends** (63) | `:protocol:paseo`, `:engine:agents`, `:android:agenthost`, `:feature:agents` | P0 day 1; P1/P2 after the switch | Claude Code on the phone through Paseo is the owner's daily path and ships in the switch release. The Rust engine, scripts and Kotlin hosts are kept as they are. Codex direct, ACP and Gas City follow. Do not port: the code-paste sign-in relay, Termux Claude, the unused ACP client [R agents-backends §2]. |
| **builtin-linux** (64) | `:android:runtime` (reused), `:engine:runtime` (Dart policy port), scripts as assets with checksums | Day 1 | Order: move the Kotlin and its tests (green) → scripts to assets with golden tests → interface replaces the channel → port the Dart policy → migration proof [R builtin-linux §12]. Termux per Q1. In-flight branches (services, vitals, backup): *decide* whether to land them in Flutter or port them from the branch. |
| **ui-kit** (58) | `:ui:design` (KMP, pure) + `:ui:kit` (Android) | Day 1 foundation, then by screen | Styles Solid (default)/Soft/Brutal/Paper/Signal + Fine-tune + Saver; 31 packs; contrast guard; amber means "needs you" and nothing is red; standard motion scheme only (no overshoot); hard max widths per part; frost only on the composer and header (owner exception 2026-10-30); live backgrounds deferred (*decide*: port or drop, judged by what it removes). |
| **screens-nav** (112) | 13 feature pairs (below) | By slice | Bootstrap gate, root decision, one status line, `EntryRouter`, adaptive scenes. Android-only screens stay Android-only for good. |
| **chat-genui** (81) | `:engine:transcript`, `:engine:requests`, `:engine:composer`, `:core:markdown`, `:feature:chat`(+`:ui`) | Day 1 | One chat lead owns transcript + chat, as `chat_screen.dart` has one owner today. Port as state machines, not widget by widget [R screens-nav §11.1]. Keep the `oc-ui` card contract (no A2UI); one `FormSpec` engine for OC2 forms, MCP/ACP elicitation, Codex and AskUserQuestion; honour OC2 `hidden` fields [R genui §0]. |
| **media-voice** (50) | `:engine:voice`, `:engine:media`, `:android:media`, `:feature:media` | Day 1 for dictation, read aloud, photos and the viewer; QR and Wi-Fi share in slice 5 | Speech runtime stays in the APK; read aloud is foreground-only (Android 17 background-audio rule); pending-photo record written before the picker opens [R media §0]. |
| **android-platform** (65) | `:android:surfaces`, `:android:schedule`, `:android:platform`, `:engine:attention`, `:engine:schedule` | Day 1 | Headless engines removed; frozen component names; widgets and notifications in 7 languages from `:ui:words`; Memory Limiter exit class `MemoryLimiter:AnonSwap` added [R android-surfaces §0.3]. |
| **packages-build** (41) | `build-logic`, `:androidApp`, release scripts | Day 1 | The Flutter-specific gates go. Kept: G33, size/startup budgets, Arabic ratchet, notice hygiene, detekt/lint, engine hash task. Release names kept (`opencode-mobile-<v>.apk`, `SHA256SUMS`, tag shape); the ~12 scripts that hard-code `build/app/outputs/flutter-apk/app-release.apk` change in the same commit. ABI splits arm64-v8a + x86_64 + universal, estimated ~18–20 MB arm64 [R testing-perf §0.10]. |
| **desktop-web-cross** (65) | `:core:common` (Redactor), `:core:net` (`ExternalLinkPolicy`), `:engine:diagnostics`, `PlatformCapabilities` | Day 1 for the cross-cutting parts; desktop later (§16); **web dropped** | Hardware-keyboard shortcuts (Esc, F2–F4, Ctrl+B/Enter, PgUp/PgDn) and fine-pointer hover are **Android features too**: they live in the kit and chat UI (`onPreviewKeyEvent`, common API) [R desktop-web-cross §1.8]. Rewrite PRIVACY.md before the switch. |

Feature pairs (presentation + UI):

| Pair | SCREENS-NAV ids |
|---|---|
| `shell` | 01–13, 21, 23–26, 107–109: bootstrap gate, root, home shell, status line, server switcher, search/Go to, adaptive layout, teaching, plain-words layer, a11y contract |
| `chats` | 27–37, 112: Conversations home, request cards, filters, new conversation, import, isolated task, all conversations, inbox, pins |
| `chat` | 38–62, 110–111: the conversation, composer, requests, model picker, approvals, demo |
| `files` | 63–70: project hub, browser/viewer, review/diff, git sheet, server and local terminal, Wi-Fi share |
| `servers` | 71–77 |
| `phone` (Android-only) | 78–83: This phone, setup A/B/C, Linux manager, Termux, local agent, phone health |
| `agents` | 84–85 |
| `team` | 86–89 (*Decide*) |
| `settings` | 90–98, 101: including Appearance, notifications/background, tools, providers/MCP, usage, schedules, Manage space |
| `about` | 102–104: report a problem, diagnostics, about/guide/tips, update notice |
| `media` | the viewer, gallery, markup, scanner, voice packs |
| `surfaces` | 100: widget overlay panels, usage-widget settings |
| `factsbook` | 99 (last, or dropped) |

---

## 14. Owner design rules → mechanisms

| Rule | Mechanism in C |
|---|---|
| Android-native feel to the maximum | Ripple (platform `RippleDrawable`), stretch overscroll (API 31+, glow on 26–30), predictive back, IME animation and haptics all come from AndroidX directly. Nothing is re-implemented. Predictive back follows the Jetpack Nav3 train. |
| Smooth streaming | §6 hot path; Baseline and Startup Profiles from the first release; Macrobenchmark streaming journey; smoothness judged only on R8 release builds. Native debug APKs work again, but only for iteration. |
| 150–250 ms subtle motion, reduced motion | `KitMotion` tokens on the *standard* motion scheme (settles in ~126/178/271 ms, 0.2 % overshoot); Compose already honours animator scale via `MotionDurationScale`; the kit's `MotionPolicy` adds Full/Calm/Off, Saver and heat; "no raw `tween`/`spring` outside the kit" check [R motion-native §0.2, §0.4]. |
| Kit-only UI | Gradle: only `:ui:kit` sees material3. Konsist: the §3.2 allowlist. A missing part is added to the kit. |
| No glass | No blur or translucency tokens; frost only in `KitFrost` on two surfaces, solid at rest and under reduced effects. |
| Simple and natural; judge by what it removes | Each slice brief names what it deletes (examples: three Dart engines, seven pending-intent copies, two form renderers, `NativeNotificationLocale`, `scrollable_positioned_list`). |
| Plain words, never raw errors | Closed `Failure`, `Redacted` details, no `String` path from a server to UI (§9). |
| Obvious options as visible rows; no new bar/row levels; actions name their target; steer, never queue | Screen-review checklist in every UI brief. `UiState` exposes a single status-line slot (`StatusLine` engine priority order), so a second indicator cannot exist. `PromptDelivery` steers whenever `ServerCapabilities.canSteer`. |
| Component hard max widths | Each kit part declares a cap in `:ui:design`'s capacity table. Kit tests assert it at 280/412/1600 dp with extreme content. |
| Frame text capped at 1.3×, reading text scales fully | `KitFrameText` density wrapper in the kit using Android's non-linear converter [U exact hook]; kit test at `fontScale = 2f` plus a Roborazzi capture. |
| Arabic RTL | Directional layout everywhere. Technical text is LTR-isolated. Every kit test also runs RTL. Arabic Roborazzi captures. |
| Teach hidden gestures | Teaching registry in `:engine:settings`; `KitTip`; Help › Tips. A hidden gesture without a registered tip fails a check. |
| Security invariants | `openExternalLink` is the only door (`LocalUriHandler` in the kit); no echo of provider credentials; per-profile key shape (and later `profile_id` columns) checked by tests. |

---

## 15. Testing and performance (what C changes)

Same pyramid as `testing-perf.md` §2. Where C differs:

- **Logic (now): 1,544 behaviours.** 677 unit, 267 contract and 446 integration rows go to
  `commonTest` and run as `jvmTest` on the VPS. Each test is named after its row
  (`` `STATE-DATA-18 dispatch marker blocks auto resend`() ``); a grep ratchet counts coverage.
  65 migration rows run under Robolectric, with the Keystore decrypt tests on the emulator.
- **UI (harness now, the 7,967 widget tests later, as the owner said).** Robolectric 4.17 +
  `createComposeRule` (v2) in each `:feature:*:ui` and in `:ui:kit`, on the VPS with a JDK 21 test JVM.
  - Real Android text layout makes the overflow, 2×/2.5× text-scale and Arabic tests truthful for
    the platform that ships.
  - Roborazzi 1.77 records goldens on the VPS only, from `@Preview`s and from explicit post-interaction
    states, light/dark × LTR/RTL. `-Proborazzi.dumpUiTree=true` gives agents a renders-plus-tree loop
    with no emulator. That loop also meets the owner's "save a render every visual pass" rule.
- **Parity E2E: Maestro 2.12** flows for the 240 device-flow rows, written **now against the Flutter
  release APK**, select by visible text. The same YAML later drives the native APK.
- **The upgrade journey is the most important test.** Seed a Flutter 2.1.0+57 build on a rootable
  emulator snapshot, `install -r` the native APK, then run file checks and Maestro. Also cover an
  interrupted migration, a reboot restore, and widget/tile/shortcut/schedule survival
  [R testing-perf §6.6].
- **Tiers.**

  | Tier | Where | What |
  |---|---|---|
  | T0/T1 | VPS | touched modules |
  | T2 | VPS | all JVM + Robolectric + Roborazzi verify + Konsist + lint + `assembleRelease` + R8 analyzer + size gate (target < 20 min) |
  | T3 | PC emulator, one lock holder | before any owner APK |
  | T4 | PC emulator | perf |

---

## 16. Adding iOS and Linux desktop later

### 16.1 What is already done by construction

- Every `core`, `protocol`, `engine` and presentation module compiles for `jvm` and is tested there
  on every build.
- Every `commonMain` is iOS-klib-checked nightly.
- `jvmShared` code (OkHttp engine, pinning, `/proc` and process logic, file stores) already runs on
  any JVM.
- Route keys, `AppNavigator`, `EntryRouter`, the design tokens and policies are common.

So a new platform needs three things: **bindings for the §11.3 interfaces, a shell, and a UI
conversion**.

### 16.2 The UI conversion (the cost C defers)

| Step | Work | Size [U] |
|---|---|---|
| 1. Kit to CMP | `:ui:kit` becomes `oc.kmp.compose`: composables move to `commonMain`, Android seams (§11.4) become `androidMain` actuals plus desktop/iOS versions; material3 switches to the JetBrains coordinate (same API on Android); Roborazzi desktop captures are added. Android behaviour is unchanged, which the Android goldens prove. | 3–5 agent-weeks |
| 2. Strings | Copy is regenerated into `composeResources/values*` by the same ARB→XML script; the format is the same XML with `%1$s` templates and `<plurals>` [V CMP resources page]. Android res stays for manifest, widget and shortcut XML. Codemod `R.string`/`R.plurals` → `Res.string`/`Res.plurals` in converted modules. *Note:* after this step there are two string pools again, both generated from one source. | 0.5–1 |
| 3. Each needed feature UI | Change the plugin; move `src/main/kotlin` → `src/commonMain/kotlin`; codemod; Robolectric tests stay in `androidHostTest`; add `@Preview`s for desktop. Thanks to the allowlist this is mostly mechanical: about 0.2–0.5 agent-week per module, more for `chat`. | 2–4 for ~8 modules |
| 4. Drift cleanup | Whatever the allowlist missed | 0.5–2 (unknown) |
| **Total** | | **6–10 agent-weeks** (+ step 1 adaptations per platform, below) |

Screens never converted for iOS: `phone`, `surfaces`, Manage space, the local terminal, Termux, and
the widget, tile and battery settings. For desktop, also skip `surfaces`; `phone` would come back
only as a desktop `RuntimeHost` UI if wanted.

### 16.3 Linux desktop

| Item | Work | Size [U] |
|---|---|---|
| Bindings | `SecretStore` on Secret Service (D-Bus; keep today's "keyring missing" sentence), `KeyValueStore` on a DataStore/JSON file under `$XDG_DATA_HOME`, notifications (libnotify/tray, which has GNOME gaps), `RuntimeHost` as direct process spawn (no proot; reuses `jvmShared`), file dialogs (FileKit or the XDG portal), `xdg-mime` link handler, single instance + window state | 3–5 |
| UI | Conversion (§16.2) for shell, chats, chat, files, servers, settings core, agents, about; desktop affordances (hover, context menus, scrollbars, focus rings); the keyboard layer already exists in the kit | included above + 2–3 |
| Shell and packaging | `:desktopApp`, `.deb` + tarball (reuse `.desktop` and metainfo), real AppImage via `appimagetool`; Flatpak later | 1–2 |
| Known platform limits | X11/XWayland only (SKIKO-28 open), fractional-scaling blur, tray issues [R kmp-cmp-status §2] | — |
| **Total** | | **~12–20 agent-weeks, ~5–7 calendar weeks** |

Until native desktop reaches the screens desktop users need, the **frozen Flutter Linux build stays
the desktop client**. It is experimental and has little use today [R desktop-web-cross §0].

### 16.4 iOS

| Item | Work | Size [U] |
|---|---|---|
| Prerequisites | Mac hardware or a macOS CI runner (iOS cannot be linked on Linux [R kmp-cmp-status §0.6]); Apple developer account; TestFlight; iOS 15+ | money + setup |
| Bindings | Darwin Ktor engine + leaf pinning (`handleChallenge` [U]), Keychain `SecretStore` (~150 lines cinterop), `NSUserDefaults` KV, Room/KSP iOS (if Room), `UNUserNotificationCenter`, sherpa-onnx xcframework [U], AVFoundation QR | 4–6 |
| Product gaps | No live background sockets: "reply finished" needs server push (OpenCode does not push today) or best-effort BGAppRefresh. No built-in Linux, no phone agents, no RemoteViews widgets (WidgetKit is Swift), share extension in Swift. iOS is a **remote client** of OpenCode servers and Paseo over Tailscale. | design + 2–4 |
| UI, option 1: CMP | Conversion (§16.2) + iOS-adaptive kit variants (no ripple → highlight, edge swipe-back, `UIFeedbackGenerator` haptics, text input quirks) | 6–10 |
| UI, option 2: SwiftUI | A SwiftUI kit + screens over the **same KMP ViewModels** (Swift export is alpha in Kotlin 2.4, or SKIE [U]). 2–3× the UI work of option 1, but iOS-native feel. **C keeps this option open at zero cost; CMP-day-1 would have spent its common-UI investment on a platform that might not use it.** | 15–25 |
| **Total** | | **~15–25 agent-weeks (CMP)** or **~25–40 (SwiftUI)**, 6–12 calendar weeks |

---

## 17. How agents build it in parallel

### 17.1 Principles (from AGENTS.md and owner memory)

- One feature per agent, worktree and branch.
- Shared modules have one owner each.
- Sonnet for most work, Opus for the hardest: transcript, migration, runtime split, navigation spikes.
- Codex builds backend modules and writes a frontend contract; Claude builds UI from it.
- No heavy Workflow fan-outs.
- Builds and JVM tests on the VPS; the emulator on the PC with one lock holder.
- The owner gets an APK only after an emulator pass.
- Every commit carries `[skip ci]`.

### 17.2 Ownership file

`MODULE_OWNERS` lists one role per module. The single-owner units are:

| Unit | Notes |
|---|---|
| `build-logic` + version catalog + `:androidApp` + `:quality` | coordinator |
| `:core:domain` + `:core:data` | core owner |
| `:engine:connection` | |
| `:engine:transcript` + `:feature:chat` + `:feature:chat:ui` + `:core:markdown` | chat lead: one library, like `chat_screen.dart` today |
| `:ui:design` + `:ui:kit` | kit owner |
| `:ui:words` tooling | l10n owner; feature agents only touch `strings_<feature>.xml` |
| `:migration:flutter` | |
| both halves of every bridge | interface in engine + `:android:*` implementation |
| `:app:routes` | coordinator; feature agents only add their own file |

### 17.3 Freeze before fan-out (end of slice 0)

These are frozen before agents fan out:

- `core:model` value types;
- `Outcome`/`Failure` categories;
- gateway interfaces and `ServerCapabilities`;
- store interfaces;
- `AppDispatchers`/`Clock`;
- route keys for slices 1–3;
- the kit's public API for the first 20 parts (from `docs/ux-system/kit-api/*.md`).

After the freeze, a change to any of these is a coordinator-reviewed contract change.

### 17.4 Slice recipe (test-first, the owner's idea)

1. **Contract.** `contracts/<slice>.md` is written by Codex or the coordinator. It names the
   interfaces, the `UiState` fields, the `Failure` categories, the catalog row ids, and what the
   slice deletes.
2. **Tests first.** A Sonnet test-writer turns the catalog rows and fixtures into failing
   `commonTest`s against the frozen interfaces. They compile and fail.
3. **Build.** A builder (Codex for engine and protocol, Claude for UI) makes them green; T1 on the VPS.
4. **UI slices also deliver:** Roborazzi renders (light/dark, en/ar, 2× text, tablet width), the
   owner-rule checklist (§14) ticked in the PR, and an a11y note.
5. **Merge.** The coordinator reviews and merges; T2 runs nightly; T3 runs on the emulator before
   any owner APK. Reviewers never launch test processes.

### 17.5 Lanes at steady state (5 concurrent builders)

| Lane | Model | Owns |
|---|---|---|
| L1 backend | Codex | core:net/data → protocol:oc2 → connection, sessions, delivery, composer, requests, agents, paseo |
| L2 platform | Codex (Opus for runtime split and migration) | android:* reuse, engine:runtime, attention, schedule, voice/media, migration |
| L3 kit + chat UI | Opus for the kit foundation, then Sonnet | ui:design, ui:kit, chat UI |
| L4 screens | Sonnet | shell, chats, servers, settings, phone, files, about, media, surfaces |
| L5 tests and parity | Sonnet/Haiku | catalog test-writing one slice ahead of L1–L4; Maestro parity flows on the Flutter APK; fixture recording |
| Coordinator | Opus | contracts, reviews, merges, T2, owner APKs |

### 17.6 Machines

- **Per-host `gradle.properties`.** PC: `-Xmx2g`, 2 workers, one local build at a time. VPS: `-Xmx3g`
  or more.
- **Build cache.** Configuration and build cache on; a shared local cache per host.
- **Builds go to the VPS.** A `vps_gradle.sh` modelled on today's `vps_test.sh` rsyncs the worktree
  and runs the target task only. Agents never run `build`/`assemble` on all targets.
- **Disk.** The VPS was 89 % full on 2026-11-01; prune before slice 0.
- **Quiet hours.** Midnight–2 am, VPS only.

---

## 18. Effort estimate

**Calibration.** The Flutter app (≈479k lines of Dart + 38k of Kotlin) was built between 2026-08-23
and 2026-11-01. October alone added ~479k and removed ~167k lines in `lib/` and `android/` [R git
history]. That is roughly 5–7k lines per agent-week at peak concurrency [U]. A rewrite from a full
spec (maps + catalog) should match or beat that per line. On the other side: review gates, emulator
passes, the owner's quality bar and the PC ceiling of about 3–5 concurrent builders slow it. The
native target is about **270k lines of new Kotlin + ~80k lines of tests**, plus the 25k reused
[U: Compose ≈ 0.55–0.65× the Dart UI; logic ≈ 0.75×; minus dropped parts].

| Work package | Agent-weeks [U] |
|---|---|
| Slice 0: toolchain skeleton, rules, harness, four proofs (§22), parity flows on Flutter | 5–6 |
| Core (model, common, net, data, markdown, testing) | 6–8 |
| Protocols: oc2, paseo, codex, a2a | 8–11 |
| Engines (all except team) | 32–42 |
| Kit + design | 7–9 |
| Feature UI (all except team, factsbook) | 24–30 |
| Android bridges: reuse, glue removal, updater, AppFunctions | 6–8 |
| Migration + upgrade journey | 3–4 |
| Hardening: parity pass, perf/profiles, a11y harness, release plumbing, PRIVACY.md | 4–5 |
| **Parity (excl. AI Team, FactsBook)** | **95–125** |
| AI Team (engine, gascity, UI), FactsBook | +8–12 |

Calendar at 5 concurrent builders:

| Milestone | Week | What it is |
|---|---|---|
| **M0** | 2 | Foundations proven (§22) |
| **M1** | 6–8 | Side-by-side **preview package** (`…opencode_mobile.native`, same key, own data). Remote OpenCode 2 chat end to end (kit, chats, chat, servers, basic settings), plus Paseo on the PC over Tailscale. The owner can feel it without risking their data. (It cannot reach the main app's built-in Linux, which is why phone agents wait for M2.) |
| **M2** | 12–15 | **Switch candidate** for the owner's daily path: built-in Linux runtime, Claude Code via Paseo on the phone, notifications and live mode, widgets, tiles, shortcuts, schedules, voice, files/review, migration, updater. Installed over Flutter on the emulator, then on the owner's phone. |
| **M3** | 19–25 | Remaining parity: Codex direct, usage depth, MCP/providers, AI Team tier, Room move (switch+1), targetSdk 37 slice |

At Flutter-era concurrency (~10+ builders) the calendar shrinks to roughly 12–16 weeks; the critical
path (kit → chat → runtime → migration → emulator passes) does not shrink below about 10 weeks [U].

**C compared with CMP-UI-from-day-1, honestly.**

| | C | CMP from day 1 |
|---|---|---|
| Before the Android switch | **saves ~3–6 agent-weeks** [U]: no Compose-resources pipeline + per-app-locale workaround, no `expect`/`actual` for Android-only UI seams, standard Robolectric UI tests, no desktop UI target to compile or keep green, fewer toolchain unknowns | pays those |
| At the second platform | **costs ~6–10 agent-weeks** (§16.2) | already paid |
| If a second platform never comes, or iOS goes SwiftUI | **net win**, ~3–6 (more with SwiftUI) | spent on common UI that is not reused |
| If both platforms come soon | **net loss**, ~3–5 | win |

---

## 19. Risks (ranked) and mitigations

| # | Risk | Mitigation |
|---|---|---|
| 1 | **Silent migration loss**: secrets, the restore-authority keys, dispatch markers, OS component names, the fingerprint byte format of schedules and widget targets | §12 backup + read-back verification; the component-freeze test; exact Dart `jsonEncode` canonicalisation or a recompute at migration [R android-platform R2]; upgrade journey on every candidate touching migration; migration owned by Opus |
| 2 | **Wrong signing lineage or versionCode** | Verify the installed signer before delivery; versionCode ≥ 10000; an owner decision about GitHub-release users |
| 3 | **Portability drift in Android UI modules** (C-specific) | The §3.2 allowlist fails the build; all non-portable code sits in kit seams; a **quarterly conversion rehearsal** (convert one feature module to KMP in a throwaway branch and count the fixes) gives an early, measured drift number |
| 4 | **Jetpack-first APIs that CMP lacks at conversion time** (C-specific): for example Nav3 1.3 APIs or Compose 1.13 APIs before JetBrains ports them | Track a `JETPACK_AHEAD.md` list in the kit; convert after CMP catches up, or wrap behind a kit seam |
| 5 | **Chat smoothness falls short of the demo at production size** (5,000 parts, block items, selection) | Slice-0 streaming spike with Macrobenchmark; Baseline Profile; per-part state escalation only if measured |
| 6 | **Compose crash model** (no `ErrorWidget`) | Crash-loop safe mode; models built outside composition; launch report |
| 7 | **Predictive back + container transform** feels wrong | Ship A; spike B on Jetpack 1.3; a `navigationevent-testing` test pins the gating |
| 8 | **Metro and Kotlin version coupling** | Upgrade them as a pair; graph-smoke test; Koin as plan B with constructors unchanged |
| 9 | **M3 1.5 beta churn** | Contained in the single-owner kit |
| 10 | **Module count on the 16 GB PC** (~75 at full size) | Create modules on demand; configuration cache; builds on the VPS; workers capped |
| 11 | **Throughput is lower than estimated** | Slice 0 measures lines and rows per agent-week; re-plan at M0; scope the switch to the owner's daily path |
| 12 | **Redaction drift and privacy regressions** from new libraries | Redactor + vectors first; egress allowlist test; ban `HttpLoggingInterceptor` and raw `Log` |
| 13 | **Android 17 Memory Limiter, phantom process limit, OEM kills** | New exit class on day 1; the existing budget guard and orphan reaper reused [R android-surfaces §0.3] |
| 14 | **Unmerged Flutter branches** (services, vitals, backup, app updates, AppFunctions) are lost or ported twice | Decide per branch before slice 3: land in Flutter, or port straight from the branch |

---

## 20. Real weaknesses of this proposal (plainly)

1. **It is not smoother, and it is not much faster.** The demo's smoothness comes from Compose, which
   both C and CMP-day-1 use. C's up-front saving is a few agent-weeks in a project of about 100.
   C's true value is lower schedule risk and Android-first tooling and versions. If that does not
   matter to the owner, C is mostly a deferral.
2. **Portability of composables rests on a lint rule.** The compiler proves that presentation is
   portable; for composables it proves nothing. The allowlist catches imports, not every JVM-only
   call, and agents under deadline will ask for exceptions. Drift cost is unknown until the first
   rehearsal.
3. **"Later" has a bill.** About 6–10 agent-weeks of conversion, plus a period with two kinds of UI
   module, two string pools and two test hosts. If iOS or desktop is committed soon, CMP-day-1 is
   cheaper overall.
4. **No desktop UI loop now.** Compose Hot Reload and its agent MCP server need a desktop target.
   During the Android phase, agents get Robolectric renders and the single shared emulator. The
   emulator is a real bottleneck.
5. **Presentation and UI as separate modules is more ceremony.** About 13 extra modules, and every
   screen change touches two modules (one Codex, one Claude), which suits the owner's
   backend/frontend split but slows small UI tweaks that need a new state field.
6. **The Jetpack-first train cuts both ways.** Using Google's newest APIs on Android widens the gap
   that conversion must close later.
7. **Linux desktop arrives later than in CMP-day-1.** Users of the Flutter Linux build keep a frozen
   client meanwhile. It is experimental and little used, but it is still a regression in freshness.

---

## 21. Decisions needed from the owner

1. **Second platform timing.** Is iOS or Linux desktop wanted within about 6 months? This is the
   deciding input between C and CMP-day-1. If iOS comes, is SwiftUI an option?
2. **Room in the switch release** (network-data) or in switch+1 (C's default)?
3. **Signing.** Day-1 APK signed with `1DE5BF08…D60C` (the owner's phone). What do GitHub-release
   users signed with `842284B2…1C` get?
4. **Termux** (builtin-linux Q1): keep, migrate-out only, or drop.
5. **AI Team tier** (Q3) and **FactsBook**: port, defer or drop.
6. **Live backgrounds** (20 looks, aurora already off by default): port or drop.
7. **In-flight Flutter branches**: land each before the freeze, or port from the branch.
8. **The switch scope**: the owner's daily path at M2, or full parity at M3.
9. **Web** dropped (confirm); **Windows** out of the first native slices.

---

## 22. Slice 0 (two weeks): prove before fan-out, each item at most a day

1. **Skeleton.**
   - `build-logic` with the five convention plugins and the dependency table.
   - One module of each kind: `:core:common`, `:protocol:oc2` stub, `:engine:connection` stub,
     `:feature:chats` + `:feature:chats:ui`, `:ui:kit`, `:ui:words`, `:androidApp`.
   - Compiles for android + jvm; nightly iOS klib compile on the VPS.
   - Proves the custom `jvmShared` intermediate source set.
2. **Metro + Nav3.**
   - A Metro graph with a `ConnectionScope` extension and KMP ViewModels contributed from a
     presentation module.
   - Consumed in the Jetpack Nav3 1.2 shell through entry installers contributed from a UI module.
   - Graph-smoke test.
3. **Predictive back + row → chat transform.** Option A on the emulator; spike B started.
4. **Transcript.** A recorded 5,000-part stream through `TranscriptEngine` + block `LazyColumn`;
   publish counts plus a Macrobenchmark frame report on a release build.
5. **Migration spike.**
   - Install Flutter 2.1.0+57 on an emulator and seed it.
   - Install the native APK with `install -r`.
   - Read `FlutterSharedPreferences` in place; decrypt one `flutter_secure_storage` v11 secret;
     verify the rootfs is untouched.
6. **Kit proof.**
   - `KitTappable` (ripple + haptic), `KitFrameText` (1.3× cap) and `KitSheetHost`.
   - Roborazzi goldens recorded on the VPS: light/dark, en/ar.
   - Robolectric on the JDK 21 test JVM at SDK 37.
7. **Allowlist proof.**
   - The Konsist rule fails on a planted `LocalContext` and a planted `material3` import.
   - **Conversion rehearsal zero**: move `:feature:chats:ui` to a CMP `commonMain` in a throwaway
     branch and time it. This is the measured basis for §16.2.
8. **Parity baseline.** String table from the ARBs; the first 20 Maestro flows green on the Flutter
   release APK.
9. **Throughput measure.** Lines and catalog rows per agent-week from slices 0 and 1, to re-plan §18
   at M0.

---

## Sources

Internal (read 2026-11-01):

- **Maps:** `../map/{protocols,state-data,agents-backends,builtin-linux,ui-kit,screens-nav,chat-genui,media-voice,android-platform,packages-build,desktop-web-cross}.md`.
- **Research:** `../research/{kmp-cmp-status,architecture-stacks,network-data,rendering-chat,motion-native,media,android-surfaces,genui,testing-perf}.md`.
- **Catalog:** `../catalog/BEHAVIORS.md`, `behaviors.jsonl`.
- **Demo:** `/home/eslam/Storage/Code/kmp-demo` (`shared/src/commonMain/...`).
- **Owner memory:** `dev-freeze-native-rewrite-2026-11-01`, `codex-backend-claude-frontend`, `pc-capacity-ceiling`, `build-vps`, `subagent-model-choice`, `no-heavy-workflows`, `owner-tests-i-gate`, `restore-proof-recipe`, `no-users-yet`.
- **Git history** of `oc_app-batch1` (first commit 2026-08-23).

Web (fetched 2026-11-01):

- Compose Multiplatform resources usage (placeholders, plurals, Android assets packaging): https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-usage.html
- Jetpack Navigation 3 release notes (1.2.0 stable 2026-09-23; 1.3.0-alpha01 predictive-back handoff fix; navigation3-runtime KMP targets): https://developer.android.com/jetpack/androidx/releases/navigation3
- `imeNestedScroll` (Android source set only, API 30+): https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/imeNestedScroll.modifier and https://composables.com/foundation-layout/imenestedscroll
- AGP 9 migration for KMP (plugin split): https://kotlinlang.org/docs/multiplatform/multiplatform-project-agp-9-migration.html (no source found on Android Lint coverage of KMP `commonMain` under AGP 9; marked [U])

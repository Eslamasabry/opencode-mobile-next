# Proposal B: shared Kotlin core, native UI on each platform

Written 2026-11-01 for the native rewrite of OpenCode Mobile (`io.github.eslamasabry.opencode_mobile`).
This is one of several competing architecture proposals. It argues the strongest honest case for its angle and
lists its real weaknesses. It does not pick the winner.

**Angle.** A Kotlin Multiplatform (KMP) core holds protocols, data, domain, engines, *and the screen logic*.
The UI is written in each platform's own toolkit:

- **Android, now:** Jetpack Compose with the `androidx` libraries, in plain Android modules.
- **iOS, later:** SwiftUI.
- **Linux desktop, later:** Compose for Desktop. It reuses the Android UI code, because Jetpack Compose and Compose for
  Desktop share the same API and package names.

**Inputs.** The 11 area maps in `/home/eslam/Storage/Code/wt/native-plan/map/`, the 9 research files in
`/home/eslam/Storage/Code/wt/native-plan/research/`, the behaviour catalog in
`/home/eslam/Storage/Code/wt/native-plan/catalog/` (1,544 behaviours from the 6,663 logic tests), the demo in
`/home/eslam/Storage/Code/kmp-demo`, and the owner's memory files. Version facts come from the research files, which
checked them on 2026-11-01. I added a few web checks (§23).

Labels, as in the research files:

- **[V]** verified from a primary source;
- **[S]** secondary source;
- **[U]** unverified, or my own estimate. Settle it in slice 0.

---

## 0. The proposal in one screen

1. **The pixel line.** Everything that decides *what* the person sees, and what happens when they act, is shared Kotlin:
   - protocols, stores and engines;
   - one *screen model* per screen, with its `UiState` and actions;
   - routes and the entry router;
   - the plain-words keys for every message.

   Only *how* it is drawn and touched is written per platform. Gradle enforces the line: the shared presentation
   modules are compiled **without** the Compose compiler and without Android, and an iOS klib compile in CI fails any
   leak.
2. **Android UI is ordinary Jetpack Compose.** It uses `androidx.compose.*` 1.12 (stable), Material 3 1.4.0 (stable),
   Navigation 3 1.2.0 (stable), Android string resources and Android test tooling. There are no expect/actual seams for
   UI, no JetBrains republication lag, and no alpha Material 3. The demo's smoothness comes from Jetpack Compose itself,
   so this angle keeps all of it.
3. **The 38k lines of existing Kotlin are moved, not rewritten.** They become plain Android library modules: built-in
   Linux, agents host, AI Team engine, services, widgets, schedules and device bridges. What goes away is the
   MethodChannel glue and the two headless Flutter engines.
4. **Two later platforms, two different costs.**
   - *Linux desktop* lifts the Android Compose UI into a multiplatform module. A cheap "portable Compose" rule, enforced
     from day 1, keeps that lift bounded.
   - *iOS* gets SwiftUI on top of the same screen models. That means real iOS text input, scrolling, sheets,
     VoiceOver and widgets.
   - iOS is the expensive one: about 1.5–2× the iOS cost of a shared-Compose-UI design (§16).
5. **Choose B if:**
   - Android must be the best it can be now;
   - iOS is at least ~9 months away, or is uncertain;
   - native feel on iOS matters as much as it does on Android.

   **Choose a shared-Compose-UI design if:** iOS must ship soon and cheaply. B keeps that door open, because the
   Android UI is Compose and can still be lifted for iOS later (§16.5). But lifting later costs more than writing it
   shared from day 1.

---

## 1. The case for B (strongest honest version)

### 1.1 Android gets the full platform, with nothing in between

- On Android, Compose Multiplatform *is* Jetpack Compose [V, kmp-cmp-status §0]. So B and a shared-UI design are
  equally smooth on Android. B's advantage is not frame time. It is **reach and simplicity**:

| Concern | Jetpack Compose in Android modules (B) | Compose UI in `commonMain` (shared-UI design) |
|---|---|---|
| Library line for the UI today | Compose 1.12.1 + **Material 3 1.4.0 + Navigation 3 1.2.0, all stable** [V] | CMP 1.13.0-beta01 with Material 3 **beta**, or CMP 1.12.2 with Material 3 **1.12.0-alpha03** (what the demo used, and why it needed compileSdk 37) [V] |
| When new Android UI fixes arrive | the day Google ships them. Example: Nav3 1.2.0 was stable on 2026-09-23; its JetBrains build appeared 2026-10-23 [V] | after JetBrains republishes them. `commonMain` must not use androidx APIs newer than CMP's (kmp-cmp-status §11.2) |
| The real fix for predictive back + container transform | Compose 1.13 deferred transitions + Nav3 1.3, adopted straight from Google when stable [V, motion-native §0.1] | waits for the CMP 1.13 / Nav3 1.3 republication |
| Strings | **One** Android resource system for screens, widgets, notifications, shortcuts and the manifest. Per-app language and Android lint (missing translations, format-argument mismatches) work out of the box | Compose resources are packed as *assets*, not `R` resources. Widgets, notifications and the manifest still need `res/values*`, so there are two string systems. Per-app language support is [U] (kmp-cmp-status §6) |
| Android-only Compose libraries and Views | Used directly: CameraX `CameraXViewfinder`, Termux `TerminalView` (Apache-2.0) via `AndroidView`, WebView, `PdfRenderer` pages, Media3, Glance later | each needs an expect/actual composable seam designed now, with no second platform to check the seam against |
| Module plugin | `com.android.library`: build types, flavours (the `.preview` build), `BuildConfig` (the built-in-runtime QA flags) | the AGP 9 KMP library plugin has one variant, no `BuildConfig`, no AIDL/NDK, and consumer keep rules off by default [V] |
| UI tests later (the 7,967 widget tests) | **One harness on Android's own rendering**: Robolectric `createComposeRule` + Roborazzi on the VPS JVM [V, testing-perf §4–5] | common UI tests "cannot run" as Android local tests [V]. They run on desktop Skia, so Android behaviour needs a second, Robolectric harness anyway |
| What the coding agents know | Jetpack Compose on Android is the most common Compose code in their training data | CMP coordinates and `Res.*` APIs differ in small ways that agents get wrong [U, judgement] |

- **What B removes** (the owner's "judge by what it removes"):
  - expect/actual seams for about 20 platform-bound UI parts: terminal, web preview, PDF, camera, QR, insets, haptics,
    predictive back;
  - the Compose-resources-versus-Android-resources split;
  - desktop rendering in the Android test path;
  - the alpha/beta Material 3 dependency.

### 1.2 iOS gets a native iOS app, not an Android app drawn on an iPhone

The owner's rule is "native feel to the maximum". On an iPhone that means UIKit and SwiftUI behaviour:

- system text input and selection;
- `List` scrolling physics and edge swipe-back;
- sheets with detents and `contextMenu`;
- Dynamic Type and VoiceOver;
- the system look of the current iOS release.

Compose on iOS draws its own widgets [V, kmp-cmp-status §2]. It would need iOS-styled kit variants to come close.

Several iOS surfaces must be SwiftUI whatever the UI choice:

- home-screen widgets (WidgetKit);
- Live Activities (ActivityKit);
- App Intents;
- the share extension.

B makes SwiftUI the iOS norm instead of an exception.

### 1.3 The pixel line makes the code better now, not only later

- **Screen logic becomes plain Kotlin, tested in `commonTest`.** Today the chat screen holds 27.7k lines of UI library
  with state machines inside it (`chat-genui.md` §0). In B, the send/stop/steer machine, the display passes
  (settle, fold, merge, turn owners) and the transcript rows model all live in `:shared:presentation:chat` and
  `:shared:engine:transcript`, with no Compose types.
  - The 1,544 catalogued behaviours land there.
  - Many of the 7,967 later widget tests can become cheaper screen-model tests (§15.2). For example, "the Send button
    is disabled while staged revert is pending" is a state assertion, not a pixel one.
- **The compiler enforces it.** A shared-UI design *can* keep logic out of composables, but nothing stops an agent from
  putting a rule in one. In B the presentation modules cannot even import Compose.
- **It matches how this team already works.** The owner's rule is "Codex researches and builds backend with a frontend
  contract; Claude builds the UI" (`codex-backend-claude-frontend`). In B:
  - the screen model *is* the frontend contract, as compiled Kotlin with tests;
  - Codex builds the shared core and the screen models;
  - Sonnet/Claude agents build Compose screens against frozen `UiState` types and fake models, in parallel and without
    waiting for engines.

### 1.4 Desktop costs little, because Android UI is already Compose

Jetpack Compose and Compose for Desktop share package names (`androidx.compose.*`) and APIs. Most kit and screen files
move into a multiplatform module unchanged, if one rule holds from day 1:

- `android.*`, `Context`, `R` and `AndroidView` appear only in files under a `platform/` package;
- the kit resolves copy through a `Words` facade (§10).

That rule costs a few lines per platform-bound part. Desktop is paid for when desktop starts, not before.

### 1.5 Fewer things to prove before Android ships

B's Android stack is the most-trodden path in Android development:

- AndroidX ViewModel and Navigation 3;
- Material 3 stable;
- Android resources, Robolectric and Macrobenchmark.

Slice 0 spends its risk budget on the app's real unknowns:

- the migration of Flutter data;
- predictive back with the container transform;
- the 5,000-part transcript;
- the built-in Linux restore authority.

It does not spend it on the UI toolchain.

---

## 2. Real weaknesses (stated plainly)

1. **iOS costs about 1.5–2× more than with shared Compose UI** (§16.4).
   - Every screen is written again in SwiftUI. That is roughly 170 pages, because iOS has no built-in Linux, no
     widgets overlay and no tiles.
   - The SwiftUI kit is thinner than the Compose kit, because native controls do more of the work. But it is a second
     kit, with the owner's rules applied a second time.
2. **iOS cannot be built or tested on this team's machines.** The PC and the VPS are Linux. Final Apple binaries need
   macOS [V].
   - Agents cannot iterate on SwiftUI without a Mac: either a Mac mini on the tailnet or macOS CI minutes.
   - A shared-UI design has the same build problem, but much less iOS UI work runs into it.
3. **Two or three UIs drift.** Each feature has to be built per platform, and owner rules (kit-only, plain words, max
   widths, 1.3× frame-text cap, RTL, teaching) must be re-checked per platform.
   - Shared screen models and shared copy keys limit the drift, but do not remove it.
   - A parity ledger becomes a permanent chore (§19).
4. **The presentation boundary takes discipline.** If agents leak Android types or rules into Android screens, iOS
   inherits less than planned. The shared layer can also become "Android-shaped", built around Android flows that do
   not fit iOS idioms; some iOS screens may then need their own models.
   - Mitigation: an iOS klib compile in CI, Konsist rules, and no Compose plugin in presentation modules (§5).
   - The "Android-shaped" part cannot be fully mitigated before an iOS designer looks at it.
5. **Compose stability across the pixel line.** Presentation classes are not compiled with the Compose compiler, so
   Compose treats them as unstable unless a stability configuration file says otherwise.
   - Strong skipping still compares instances, so immutable state with structural sharing skips correctly.
   - A missed configuration costs recompositions on the hottest path, the transcript. This must be proven in slice 0
     with recomposition counts [U].
6. **Desktop lift is not free.** The kit becomes a CMP module at that point, so for common code it inherits CMP's
   version lag. B's Android head start on library versions ends for shared kit code; Android-only parts keep it in
   `androidMain`. If the "portable Compose" rule was not enforced, desktop becomes a UI rewrite.
7. **No interactive desktop loop for agents now.** A shared-UI design gets Compose Hot Reload, and its MCP server, on
   a desktop target from day 1. B's emulator-free UI loop is Roborazzi screenshots of `@Preview`s on the VPS. That
   works, but it is not interactive.
8. **It departs from the demo's layout.** The demo put its UI in `commonMain` (`kmp-demo/shared/.../ui`). Android
   behaviour is identical, since CMP is Jetpack Compose on Android, but the module layout the owner saw working is
   not the one proposed here.
9. **The strings tooling serves three toolchains.** One source of truth has to feed Android `strings.xml`, iOS
   `.xcstrings` later and desktop Compose resources later. The generator is small, but it is one more owned tool.
10. **Everything any rewrite risks still applies** (§19, second half): the migration, the hundreds of hard-won
    behaviours, OpenCode 2 volatility, and the end of Shorebird.

---

## 3. What is shared and what is not

### 3.1 The pixel line, precisely

| Shared Kotlin (`commonMain`, compiled for Android + desktop JVM, iOS gated) | Per platform |
|---|---|
| Wire clients, event union, SSE/WebSocket, trust rules | Network engine actuals: OkHttp (Android + desktop) or Darwin (iOS); certificate pin hooks |
| Stores and their rules: writer lanes, profile sweep, fail-closed reads, redaction | Storage actuals: SharedPreferences, Keystore, files / Keychain / Secret Service |
| Engines: connection, transcript, delivery, approvals, composer, attention, schedule, agents, team, runtime rules, media rules, gen-UI codec, diagnostics | Services, workers, widgets, tiles, notifications, the proot runtime, audio and camera, the terminal PTY |
| **Screen models**: `UiState`, actions, state machines, rows models, capability gating | **Screens**: layout, scrolling, gestures, focus, animation, IME, transitions |
| Routes, `AppNavigator` interface, `EntryRouter` (links, shares, notifications, widgets, tiles) | Navigation host: Nav3 on Android, `NavigationStack` on iOS, a desktop shell |
| Copy *keys* (`StringKey`), `Failure → StringKey` tables, number/time values | Copy *text*: Android resources, iOS string catalog, desktop resources |
| Design data: colour maths, 31 packs, contrast guard, style resolver data, motion durations, max widths, text-scale caps | Kits that draw those tokens: Compose kit (Android, then desktop), SwiftUI kit (iOS) |
| Markdown block model (streaming parse), code tokens, diff model, form spec | Renderers for those models |

### 3.2 Size ledger (Dart today, so the split is concrete)

Today's Dart, from the maps: `lib/` is about 479k lines.

| Area | Dart lines | Where it lands in B |
|---|---|---|
| `ui/` | ~277k (kit ~107k; screens and widgets ~170k) | Android UI. About 25–40k of the screen code is state logic that moves to shared presentation, plus ~5k of theme maths to `:shared:design` [U] |
| `state/` 68k, `domain/` 39k, `api2/` 10k, `paseo/` 9k, `codex/` 2.5k, `acp/` 0.6k, `orchestration/` 12k | ~141k | shared core and engines |
| `builtin/` 25k, `termux/` 9.4k, `voice/` 5.6k, background/scheduling/platform/diagnostics and the rest ~20k | ~60k | rules go to shared engines; Android halves go to platform modules; Termux depends on the owner's Q1 |

- Roughly half of today's code by line count becomes shared. The other half is UI, written per platform.
- The existing 38k lines of Kotlin stay Android-specific (§11).
- Converted to Kotlin at an assumed 0.65–0.8× ratio, the native Android app is about **300–380k lines including
  tests** [U].

---

## 4. Module graph

### 4.1 Picture

```
                         :android:app   (application id io.github.eslamasabry.opencode_mobile, same signer,
                         │               manifest with identical component class names, Metro AndroidAppGraph,
                         │               Application, MainActivity = ComponentActivity, crash-loop guard)
       ┌─────────────────┼──────────────────────────────┬───────────────────────────────────┐
       │                 │                              │                                   │
 Android UI (Jetpack Compose)              Android platform (existing Kotlin, moved)   :android:migration:flutter
 :android:ui:shell   (Nav3, back policy,   :android:platform:linux-runtime  (+ jniLibs proot)
                      transitions, rail)   :android:platform:terminal       (Termux emulator + view)
 :android:feature:<name> (screens)         :android:platform:agents-host
 :android:ui:kit     (ONLY module on       :android:platform:team-engine    (+ libaiteam_*.so)
                      material3)           :android:platform:background     (FGS, status row, alerts, tiles)
 :android:ui:words   (res/values*, R map)  :android:platform:widgets        (RemoteViews providers)
                                           :android:platform:schedule       (alarm + WorkManager)
                                           :android:platform:media          (sherpa-onnx, TTS, PDF, CameraX, zxing-cpp)
                                           :android:platform:device         (network, thermal, exits, crash store, SAF, export)
                                           :android:platform:update         (PackageInstaller self-update)
       │                                            │ implements engine interfaces (Metro @ContributesBinding)
 ══════╪════════════════ the pixel line (no Compose, no Android below) ══════╪══════════════════════════
       ▼                                            ▼
 :shared:presentation:{core, nav, words, <feature>…}   screen models, routes, AppNavigator, EntryRouter, copy keys
       │
 :shared:engine:{connection, transcript, delivery, approvals, composer, attention, schedule,
                 agents, team, runtime, media, genui, diagnostics, settings, update}
       │                      :shared:content:{markdown, code}      :shared:design (tokens, colour maths)
 :shared:domain  ◄──────────  :shared:protocol:{oc2, paseo, codex, team, acp*}
       │
 :shared:core:{model, common, json, net, data}                       :shared:core:testing (test-only fakes)

 later:  :desktop:app (+ :shared:ui:kit lifted from :android:ui:kit)      iOS: :shared:umbrella → XCFramework → Xcode/SwiftUI
```

### 4.2 Module list with owners and contents

**Shared (KMP).**

- Targets: `android` and `jvm("desktop")` from day 1. `iosArm64` and `iosSimulatorArm64` exist behind
  `-Poc.ios=true`; a Linux CI job compiles them to klibs.
- Source sets:
  - `commonMain`;
  - an intermediate `jvmShared` for android + desktop (OkHttp, `java.security`, zxing core, the sherpa JVM API), so
    desktop reuses Android's JVM actuals as they are;
  - `androidMain` and `desktopMain`;
  - later `appleMain` / `iosMain`.

| Module | Holds | Comes from |
|---|---|---|
| `:shared:core:model` | plain data types | `lib/domain/models*` |
| `:shared:core:common` | `Outcome`/`Failure`, `Redactor`, `AppDispatchers`, `Clock`, `AppLog` (Kermit), `Trace` facade, ids | `domain/product_failure.dart`, `KitRedact` and the 6 other scrubbers merged |
| `:shared:core:json` | lenient `JsonElement` readers, tolerant serializers, `exceptionsWithDebugInfo = false` | the Dart tolerant `fromJson` |
| `:shared:core:net` | Ktor 3.6.1 client factory (OkHttp engine: no retry-on-failure, no redirects, `NO_PROXY`), leaf-DER pin trust, cleartext consent, address classes incl. `100.64.0.0/10`, SSE parser (16 MiB cap), `EventStream` backoff 1 s→16 s / reset after 30 s / "disconnected" after 6 tries, WebSocket | `api2/transport.dart`, `sse2.dart`, network-data §0 |
| `:shared:core:data` | `KeyValueStore`, `SecretStore`, `BlobStore`, `ProfileScope` sweep, `WriterLane`, Room 3 database (queue, receipts, drafts index, caches) | state-data §14, network-data §0.6 |
| `:shared:domain` | `ServerGateway`, `ServerOperationsGateway`, `ServerCapabilities` (85 flags), `PlatformCapabilities` (16 flags), value types, pure domain rules | `lib/domain` (39k) |
| `:shared:protocol:oc2` | dialect table, typed client, 91-event union with `UnknownEvent`, event adapter | `lib/api2` |
| `:shared:protocol:paseo`, `:codex`, `:team`, `:acp` (P2) | the agent and AI Team clients | `lib/paseo`, `lib/codex`, `lib/orchestration` |
| `:shared:engine:connection` | `ConnectionRegistry`, `ConnectionGraph` per live connection, lifecycle, quiet reconnect, location, session list, selection | `ConnectionController` (54 part files), split per state-data §14 |
| `:shared:engine:transcript` | event apply on a serial dispatcher, version fences, settle/fold/merge passes, publish ≤ every 50 ms, `PersistentList` turns | chat-genui 01–05, protocols §6 |
| `:shared:engine:delivery` | offline queue + dispatch markers, command receipts, online-send marker, steer-by-default | STATE-DATA-18..20 |
| `:shared:engine:approvals` | permissions, auto-approval, questions, **one** `FormSpec` engine (OC2 forms, MCP/ACP elicitation, Claude AskUserQuestion), Undo window | genui §0.5 |
| `:shared:engine:composer` | drafts, attachment vault, photo recovery, stash, phrase chips | STATE-DATA-21..23 |
| `:shared:engine:attention` | notification rules, status-row model (`buildNotificationStatus`, throttle, owners), alert copy *keys*, widget snapshot builder + redaction, tile model | android-platform §16 |
| `:shared:engine:schedule` | recurrence (one implementation), store transactions, permanent claims, receipts, runner | ANDROID-PLATFORM-36..41 |
| `:shared:engine:agents` | phone-agent rows, merged feed, handoff, sign-in state | agents-backends |
| `:shared:engine:team` | AI Team state and controllers (if kept) | `state/team_*`, `orchestration*` |
| `:shared:engine:runtime` | `RuntimeHost` interface, recovery budget, backoff, idle policy, setup protocol + job state, component registry and pins, Linux manager parsers, scripts as resources | builtin-linux §12 |
| `:shared:engine:media` | voice and attachment rules, model catalog, downloader state machine, MIME, office text, SVG validator, pairing parser | media-voice §13.1 |
| `:shared:engine:genui` | card codec, validation, state machine, answer envelope | chat-genui 70–78 |
| `:shared:engine:diagnostics`, `:settings`, `:update` | ring buffer, problem report, crash report builder / app prefs, appearance prefs, teaching registry, consents / release check model | desktop-web-cross §9.3 |
| `:shared:content:markdown` | `org.jetbrains:markdown` 0.7.18 streaming parse → `Block` model, path chips, agent blocks, prose-for-speech | rendering-chat §11.1 |
| `:shared:content:code` | `CodeTokenizer` (SyntaxMP 0.3.0 first), diff model | rendering-chat §8–9 |
| `:shared:design` | colour roles maths, 31 packs, contrast guard, style resolver data, motion durations, max widths, text-scale caps, as plain values | ui-kit K7 steps 1–2 |
| `:shared:presentation:core` | screen model base (AndroidX `ViewModel`, KMP lifecycle 2.11.0), `UiText`/`Notice` value types, `watch(flow)` helper for Swift | §7 |
| `:shared:presentation:nav` | the sealed `Route` hierarchy, the `AppNavigator` interface, `EntryRouter` + `EntryRequest` | screens-nav §3–4 |
| `:shared:presentation:words` | the generated `StringKey` catalog, `Failure → StringKey` tables, plain-words tables as data | screens-nav §0 tables |
| `:shared:presentation:<feature>` | one per feature (list in §18.2) | the logic half of `ui/screens/*` |
| `:shared:core:testing` | fakes, `TestClock`, `TestDispatchers`, fixture Ktor engine | testing-perf §12 |

**Android (plain Android modules, Jetpack Compose with `androidx` coordinates).**

| Module | Holds |
|---|---|
| `:android:app` | `com.android.application`, namespace and application id unchanged, signing, manifest, `AndroidAppGraph`, `OcApplication` (crash handler → redactor → migration → graph), activities, R8 rules, ABI splits, release verification tasks |
| `:android:ui:kit` | every component: tokens bound to `MaterialTheme`, 5 styles, motion, haptics, press, sheets, rows, composer parts, markdown/code/diff renderers, frost (the one exception) |
| `:android:ui:words` | `res/values*/strings.xml` (generated, 7 languages), the generated `StringKey → R` table, the `Words` resolver, `UiText` composables |
| `:android:ui:shell` | `NavDisplay`, the `AppNavigator` implementation, scene strategies (rail, list-detail), predictive-back policy, entry installers set |
| `:android:feature:<name>` | screens for one feature, plus fake screen models for previews |
| `:android:platform:*` | §11 |
| `:android:migration:flutter` | first-launch reader for Flutter data (§12) |
| `:android:benchmark`, `:android:testing`, `:quality` | Macrobenchmark + profile generator; Robolectric/Roborazzi rules; Konsist architecture tests |

**Size.** About 45 modules at slice 0, growing to about 80 at parity. Configuration cache plus build cache keep this
workable. Per-host caps on the PC (2 workers, `-Xmx2g`) come from kmp-cmp-status §12.5.

### 4.3 Build logic and pins

`build-logic` holds the convention plugins:

- `oc.kmp.core`, `oc.kmp.protocol`, `oc.kmp.engine`, `oc.kmp.presentation` (no Compose plugin);
- `oc.android.library`, `oc.android.compose`, `oc.android.feature`, `oc.android.application`;
- later `oc.desktop.application`.

The version catalog pins:

| Tool or library | Version |
|---|---|
| Kotlin | 2.4.21 |
| AGP | 9.3.3 |
| Gradle | 9.7.x |
| JDK | 17 (21 only for the Robolectric test JVM) |
| SDK | compileSdk 37, targetSdk 36 for the first native release, minSdk 26 |
| Jetpack Compose | 1.12.1 |
| Material 3 | 1.4.0 |
| Navigation 3 | 1.2.0 |
| activity-compose | 1.13.0 |
| Lifecycle | 2.11.0 (JetBrains KMP artifacts in shared code; on Android they resolve to androidx) |
| Metro | 1.5.1 |
| Ktor | 3.6.1 |
| kotlinx.serialization | 1.12.0 |
| kotlinx.coroutines | 1.11.0 |
| Room | 3.0.3 |
| Coil | 3.6.3 |
| Kermit | 2.2.0 |
| androidx.core | 1.19.1 |
| WorkManager | 2.12.0 |

All [V] in the research files on 2026-11-01. Move to Compose 1.13 / Material 3 1.5 / Nav3 1.3 when stable (projected
Dec 2026 [U]). That move is driven by the predictive-back fix (§8.3).

---

## 5. Layering and dependency rules

### 5.1 Rules (enforced by Gradle at configuration time)

| From | May depend on | Must not depend on |
|---|---|---|
| `:android:feature:*` | `:android:ui:kit`, `:android:ui:words`, its own `:shared:presentation:<feature>`, `:shared:presentation:{core,nav,words}`, `:shared:design`, Compose foundation **layout/lazy/semantics only** | engines, domain, protocols, data, net, `material3`, other features |
| `:android:ui:kit` | `:shared:design`, `:shared:presentation:words` (the `StringKey` type), Compose, Material 3 | anything with business types |
| `:android:ui:shell` | kit, words, `:shared:presentation:nav`, feature entry installers through Metro sets | engines, protocols |
| `:shared:presentation:*` | engines, domain, `core:model`, `core:common`, `presentation:words` | **Compose (plugin not applied), Android, protocols, `core:net`, `core:data` implementations** |
| `:shared:engine:*` | domain, `core:*` | presentation, protocols (gateways arrive through DI), Android |
| `:shared:protocol:*` | domain, `core:{net,json,common,model}` | engines, presentation |
| `:android:platform:*` | engine interfaces, domain, `core:*` | presentation, features, kit (except `widgets`, which renders RemoteViews and needs none of them) |
| `:android:migration:flutter` | `core:data`, `core:common` | everything above engines |
| `:android:app` | everything | nothing |

These rules encode existing AGENTS.md rules as build failures:

- "UI talks to the domain gateway only": Android features cannot see the gateway at all, only screen models.
- "Gate on `ServerCapabilities`, never the flavour": features never see the backend enum.

### 5.2 Extra checks (cheap, and each catches a real failure mode)

1. **iOS klib compile in Linux CI** of every `:shared:*` module (`-Poc.ios=true`, no cinterop). It catches JVM-only
   APIs leaking into `commonMain`, which is what kills an iOS port later [V: klibs build on Linux without cinterop].
2. **Konsist rules** (`:quality`):
   - no `androidx.compose`, `android.` or `java.` imports in `:shared:*` `commonMain`;
   - in Android UI modules, `android.*`, `LocalContext`, `R.` and `AndroidView` are allowed only under `…/platform/`
     packages (the portable-Compose rule for the desktop lift);
   - no `runCatching` in suspend code;
   - no `Dispatchers.*` or `Clock.System` outside `core:common`;
   - no raw `tween`/`spring`/`performHapticFeedback`/`ModalBottomSheet` outside the kit;
   - no `Text(` fed from a `Throwable` or a `String` field that came from a server (approximate rule);
   - every `@Serializable` route is registered.
3. **A Compose stability configuration file**, `compose-stability.conf`, applied to every Android UI module. It lists
   `io.github.eslamasabry.oc.presentation.**`, `io.github.eslamasabry.oc.design.**` and
   `kotlinx.collections.immutable.*` as stable. Without it, the cross-module types are treated as unstable (§2.5).
4. `explicitApi()` in `core`, `domain`, `protocol`, `engine` and `presentation`.

---

## 6. Dependency injection

**Metro 1.5.1.** It is a compile-time Kotlin compiler plugin with KMP support, and it is the pick of the
architecture-stacks research for the same reasons that apply here (§3 there):

- graph errors at build time;
- `@Contributes*` aggregation, so no agent edits a central wiring file;
- graph extensions for per-connection scopes;
- no KSP.

Room brings KSP back, but only in `:shared:core:data`.

How B uses it:

| Scope | Created by | Contents |
|---|---|---|
| `AppScope` | `OcApplication.onCreate` → `AndroidAppGraph`. Later `IosAppGraph` (`:shared:umbrella`) and `DesktopAppGraph` | stores, redactor, `ConnectionRegistry`, attention, schedule engine, runtime host (Android binding), teaching, diagnostics, update |
| `ConnectionScope` (graph extension, one per live connection; several alive at once) | `ConnectionRegistry.open(profile)` | gateway pair chosen by `profile.backend`, event stream, sessions, selection, approvals, delivery, read state, caches |
| Screen model | Nav3 entry, through the `lifecycle-viewmodel-navigation3` decorator + `metrox-viewmodel-compose` on Android; `ScreenHost` on iOS | engines from `AppScope`; for conversation screens, `ConnectionRegistry.graphFor(ref)` through assisted injection |

- Platform modules contribute their implementations with `@ContributesBinding(AppScope::class)`. For example,
  `AndroidRuntimeHost : RuntimeHost` lives in `:android:platform:linux-runtime`.
- Android components (services, receivers, workers, widget providers, tiles) reach the graph through
  `(applicationContext as GraphOwner).appGraph`. `metrox-android` needs API 28 and our minSdk is 26 [V].
- Pin Metro and Kotlin as a pair. Keep `@Inject` classes free of Metro-specific types so that Koin (plan B) can wrap
  the same constructors.

---

## 7. State management

### 7.1 Three tiers, one idiom (`StateFlow`)

1. **Engines** (app or connection scope, shared) own truth, timers, streams and state machines.
   - Out: `StateFlow`.
   - In: `suspend` commands that return `Outcome`.
   - State machines are a sealed state plus a pure `reduce(state, event) → Transition(state, effects)`. This covers
     send/stop/steer, delivery, approvals, connection status, setup steps and the phone runtime.
2. **Screen models** (shared, `:shared:presentation:<feature>`) are AndroidX `ViewModel` subclasses from the KMP
   lifecycle 2.11.0.
   - Each exposes one `StateFlow<XUi>` built by `combine(...).stateIn(viewModelScope, WhileSubscribed(5_000), …)` and
     takes actions as methods.
   - They hold no Android or Compose types and **no strings**: only ids, enums, `StringKey`s with arguments, and user
     data such as titles and message text.
   - One-shot events are state fields that the UI acknowledges (`onShown(id)`), never channels.
3. **UI-local state** (per platform) covers what only the toolkit can own: scroll position and anchoring, focus,
   animation progress, the IME composition, gesture progress, and the text field buffer.

### 7.2 Who owns the composer text (a native-UI decision)

- The text being typed lives in the platform text system: `TextFieldState` on Android, `UITextView`/`TextField` on
  iOS. IME composition, selection handles, keyboard images (`contentReceiver`) and autocorrect stay native and
  correct.
- The UI reports `onDraftChanged(text, selectionUtf16)` to the screen model. It is debounced for autosave and
  immediate for `/` and `@` detection.
- Mention ranges stay in **UTF-16 offsets**, as today. Kotlin strings are UTF-16 everywhere; the iOS bridge converts
  from `String.Index` (chat-genui §8.3).

### 7.3 The streaming hot path (the transcript)

- `TranscriptEngine` applies events in order on its serial dispatcher. It publishes an immutable `TranscriptState` at
  most every 50 ms: the first delta flushes at once, then leading and trailing edges every 50 ms. Turns sit in a
  `PersistentList`, so unchanged turns keep their identity.
- `ChatScreenModel` maps the state to **flattened block rows**: prompt, work line, each Markdown block, code, table,
  picture group, agent block, notice, footer and live line. Each row has a stable string key and a kind. This rows model
  is shared, so iOS and desktop get the same transcript structure for free.
- On Android: one `LazyColumn(reverseLayout = true)` keyed by row key with `contentType` = kind.
  - The kit's reveal mask fades fresh words in the draw phase.
  - The live block grows on a critically damped spring and requests `preferredFrameRate(High)` only while it grows.
  - Following the live edge uses `requestScrollToItem(0)`.
  - Line breaking stays `LineBreak.Simple` (rendering-chat §0).
- Escalation, only if the 5,000-part benchmark asks for it: expose the live block's text as its own `StateFlow<String>`
  and read it inside the text composable, so a delta recomposes one node.
- Port `test/perf_chat_test.dart` as an engine publish-count test, and add a Macrobenchmark frame test on the emulator.

### 7.4 Code sketch (names [U] against pinned versions)

```kotlin
// :shared:presentation:chats  (commonMain; no Compose plugin, no Android)
data class ChatsUi(
    val rows: PersistentList<ChatRowUi> = persistentListOf(),
    val needsYou: Int = 0,
    val notice: Notice? = null,            // Notice = StringKey + args + optional Redacted details, never text
)

@Inject @ViewModelKey(ChatsModel::class) @ContributesIntoMap(AppScope::class)
class ChatsModel(feed: ChatFeed, private val nav: AppNavigator) : ViewModel() {
    val state: StateFlow<ChatsUi> = feed.items.map(::toUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUi())
    fun open(row: ChatRowUi) = nav.openChat(row.ref, focusComposer = false)
}
```

```kotlin
// :android:feature:chats  (Jetpack Compose; kit parts + layout only)
@Composable
fun ChatsScreen(model: ChatsModel = metroViewModel()) {
    val ui by model.state.collectAsStateWithLifecycle()
    KitScreen(title = K.chatsTitle) {
        KitLazyList(ui.rows, key = { it.id }) { row -> KitChatRow(row, onOpen = { model.open(row) }) }
    }
}
```

```swift
// iOS, later: the same model rendered by SwiftUI
struct ChatsView: View {
    @State private var host = ScreenHost { AppGraph.shared.chatsModel() }   // owns a ViewModelStore; clears on deinit
    var body: some View {
        List(host.state.rows, id: \.id) { row in
            ChatRow(row: row).onTapGesture { host.model.open(row: row) }
        }
    }
}
```

---

## 8. Navigation

### 8.1 Shared

- `:shared:presentation:nav` holds one sealed, `@Serializable` `Route` hierarchy, with one file per feature and the
  shell owner as the single owner. There are about 40 routes:
  - the named routes from screens-nav §3.1;
  - the chat route with its arguments: `focusComposer`, `discardIfUntouched`, `landOnRequestID`, `landOnFailure`,
    `menuAction`.
- `AppNavigator` is an interface with:
  - `open(route)`;
  - `openChat(ref, focusComposer)`, which de-duplicates: a chat already on top is not stacked again;
  - `land()`, which clears to Home;
  - `replaceRoot(route)`, where Servers is the root when no profile exists;
  - `back()`.
- **One `EntryRouter`** replaces the seven Flutter pending-intent copies.
  - Launcher shortcuts, `opencode-mobile://session` and `://team` links, share-in, notification taps, widget taps,
    tiles and scheduled-run taps all become `EntryRequest`s.
  - Each waits for bootstrap and migration, is consumed exactly once, and becomes `AppNavigator` calls.
  - It is pure logic, tested in `commonTest` with the launch/link/share test vectors.
- Sheets and dialogs are screen state, not routes, matching `showKitSheet`/`showKitConfirm` today.

### 8.2 Android

- Jetpack **Navigation 3 1.2.0**. The back stack is a `NavBackStack` of a thin wrapper
  `@Serializable data class Dest(val route: Route) : NavKey`, so shared routes need no Android type.
- Feature modules contribute `EntryInstaller`s into a Metro set, and the shell assembles them.
- Entry decorators: saveable state holder, plus `rememberViewModelStoreNavEntryDecorator()` for per-entry screen models.
- Scenes: list-detail and rail at the Flutter thresholds 600/840/1200 dp, using Jetpack `material3-adaptive`.
- Deep links go through the `EntryRouter`, not through Nav3's own deep-link matcher. That keeps one rule set on every
  platform.

### 8.3 Predictive back policy (decided once, in the shell and kit)

1. **List → chat.** The container transform runs on tap and on button-back. While a back gesture is live, the platform
   predictive motion runs instead: `sharedBounds` is gated off while the navigation-event transition state is
   in progress. This is the demo's lesson, without the 700 ms latch.
   - The chat may pop on a gesture only when nothing is lost: the composer equals the saved draft and nothing is being
     recovered. Otherwise Back saves first and asks if the save is refused (screens-nav §3.2).
2. **Settings pages:** shared-axis X with the system predictive back.
3. **Full-screen new conversation:** slide up; the gesture closes it.
4. **Reduced motion:** no transform, only a cross-fade or nothing.
5. **The root never consumes Back** (motion-native §0.5). Home's "press back again to exit" stays only where the owner
   still wants it.
6. **Spike B, the real fix,** runs on Compose 1.13-beta + Nav3 1.3-alpha deferred transitions. It is adopted the day
   both are stable. B can do this without waiting for a JetBrains republication.

### 8.4 iOS and desktop (later)

- **iOS:** a Swift class implements the exported `AppNavigator` protocol over `NavigationStack(path:)`. Edge swipe-back
  is native. Deep links arrive as `EntryRequest`s from `onOpenURL` or the share extension.
- **Desktop:** the lifted Compose shell, with Nav3 (CMP) or a simple stack. Esc and mouse back go through the same
  `AppNavigator`. Keyboard shortcuts are a shared map (desktop-web-cross 11–19) applied by the shell.

---

## 9. Threading

- **Each engine and each per-profile store mutates state only on its own serial dispatcher**
  (`default.limitedParallelism(1)`, injected). This keeps the single-thread assumption the Dart code was written
  under. Heavy pure work goes out with `withContext(default)` and comes back with a value: JSON pages, 5,000-part
  merges, markdown parses.
- **Writer lanes:** a FIFO `Mutex` plus a synchronous `stopAdmission()`, so "drain" means every admitted write has
  finished. Writes return only after `commit()`, not `apply()`, on SharedPreferences.
- **Structured scopes replace generation counters.** Each `ConnectionGraph` owns a child scope, and a reconnect or a
  profile switch cancels it. Identity re-checks remain only where a result lands in a store that outlives the
  generation.
- **Screen models** run on `Main.immediate` (`viewModelScope`) and never mutate engine state directly.
- **Swift** observes `StateFlow` on the main queue through the `watch` helper, or through SKIE/Swift export (§16.2).
- **Android lifetime:** engines live in the process, and the process lives as Android allows. The rules are unchanged:
  - the finished-reply watch is a short `dataSync` foreground service started when the app goes to the background with
    a turn running (the demo lesson);
  - the phone server stays `specialUse`;
  - scheduled runs use WorkManager with a ≤ 8-minute budget;
  - nothing assumes unbounded lifetime (AGENTS.md).
- There are **no headless Flutter engines** any more. A widget action or a scheduled run cold-starts the same graph in
  the service or worker, in about 0.25 s, instead of booting an engine.

---

## 10. Error model, plain words and strings

### 10.1 Failures

- `Outcome<T>` = `Ok(value)` | `Failed(Failure)`.
- `Failure` is a closed sealed hierarchy, a port of `ProductFailureCategory`: `Offline`, `TimedOut`, `Certificate`,
  `SignInRefused`, `NotFound`, `Conflict`, `ServerDidNotAnswer`, `Refused(reason)`, `Authored(StringKey)`,
  `Unexpected(Redacted)`, and the rest.
- Exceptions mean bugs or cancellation, never expected failures.
- Translation happens once, at the edge: in `core:net`, the protocols, `core:data` and the Android platform modules.
  Nothing above the engines sees a transport exception.
- `Redacted` can only be constructed by the `Redactor`. **There is no `String` path from a server to a visible
  label**, which is the owner's "no raw errors" rule made a type.
- `runCatching` is banned around suspend calls. Cancellation is rethrown.

### 10.2 Plain words, shared

- `:shared:presentation:words` holds the `Failure → StringKey` table, an exhaustive `when`, so a new category forces
  new copy.
- It also holds the existing plain-words tables as data: `agent_error_words`, `send_failure_words`, `git_words`,
  permission presentation, connection failure, setup messages and recent-error words.
- Every error state carries a Details fold with the redacted details, plus "Report a problem". iOS and desktop inherit
  the same sentences.

### 10.3 Strings pipeline (one source, three toolchains)

- **Source of truth:** Android-format `strings.xml` under `/strings/values*/` at the repo root. It is one-off
  converted from the ARB files:
  - 9,468 English keys, 7 languages;
  - 261 plurals become `<plurals>`, which follow Arabic's CLDR forms;
  - the 6 ICU `select` messages are flattened into separate keys;
  - placeholders become `%1$s`;
  - the 1,018 orphan Arabic keys are dropped.
- **The generator** (`:tools:strings`, a JVM CLI) emits:
  1. the shared `StringKey` catalog (`commonMain`);
  2. the Android `StringKey → R` tables and `res/values*` for `:android:ui:words`;
  3. later, iOS `Localizable.xcstrings`;
  4. later, desktop Compose resources. Those use the same XML format, so this is a copy.
- **Android gains** from this:
  - every native surface (widgets, notifications, tiles, shortcuts) gets all 7 languages, closing today's en/ar-only
    gap (android-platform T16);
  - Android lint checks format arguments and missing translations;
  - per-app language works natively.
- Kit text goes through `Words` (`text(K.chatSend)`, `text(notice)`) instead of raw `stringResource(R.string…)`. That
  one indirection is what makes the desktop lift a file move.

---

## 11. Reusing the 38k lines of existing Kotlin

Rule: **move, don't rewrite.** Each group moves with `git mv` into a plain Android library module, keeping its
**package names**. Manifest components and WorkManager workers are stored by fully qualified class name, so the FQCNs
must not change; a module's namespace may differ from the class package [U: prove in slice 0].

The JVM tests move with their code and must be green on the first day of each move. That covers the 46 classes, 7,977
lines and 645 `@Test`s, plus 5,109 lines of instrumentation.

| Group (today's `android/app/src/main/kotlin`) | Lines (approx.) | Module in B | What changes |
|---|---|---|---|
| `BuiltinLinux.kt` + `Native*`/`Setup*`/`Phone*` runtime helpers | 4.5k + ~9k | `:android:platform:linux-runtime` (jniLibs proot/loader/talloc/shmem, `useLegacyPackaging`, 16 KB checks) | Split `BuiltinLinux.kt` into RuntimeInstall, ProotLauncher, ServiceRegistry, InstallerSlot, ServerRecovery, IdleController (builtin-linux §7). Delete `MainActivity.handleBuiltinLinux`. Implement `RuntimeHost` from `:shared:engine:runtime`. Keep the QA service in a QA source set behind `BuildConfig`, which a plain Android module allows. |
| Pure policy files (`RestartBackoff`, `NativeRecoveryBudget`, `NativeRuntimeOwnership`, `NativeProcessTree`, `NativeOrphanReaper`, `WorkLeases`, `IdleStopPolicy`, setup protocol parsers …) | part of the above | stay in `linux-runtime` on day 1 | Lift into `:shared:engine:runtime` (`jvmShared`) only when the desktop `RuntimeHost` needs them. Never "simplify" the `NativeRuntimeOwnership.plan` family. |
| `LocalTerminal` + `PtyAccess.java` + Termux `terminal-emulator` v0.118.3 | ~1k | `:android:platform:terminal` | Add Termux **`terminal-view`** (Apache-2.0 [V], §23) via `AndroidView` as the day-1 renderer instead of re-creating xterm. A Compose-drawn renderer becomes an optional later slice. |
| `PhoneAgent*`, `PaseoStaleLock`, `NativeAgentHostAdmission` | ~4.7k | `:android:platform:agents-host` | Channel glue removed; called by `:shared:engine:agents` through an interface. |
| `PhoneEngineNative`, `PhoneEngineAttestation`, `NativeEngineReadiness` + `libaiteam_*.so` | ~1.5k | `:android:platform:team-engine` | Keep the release-time hash check against `aiteam-engine-manifest.json`. Exists only if AI Team survives (owner Q3). |
| Notifications and background (`BackgroundConnectionService`, `StatusNotification*`, receivers, `PauseTileService`, `AttentionTileService`, `NativeStrings`) | ~2.9k | `:android:platform:background` | The Dart half (`buildNotificationStatus`, alert copy, redaction) becomes `:shared:engine:attention`. `NativeStrings` reads generated resources for all 7 languages. Notification id 4747 and channel ids stay. |
| Widgets (`*WidgetProvider`, binders, `WidgetV2*`, `WidgetStatus*`, `WidgetAction*`, `Usage*`) | ~4.3k | `:android:platform:widgets` | RemoteViews providers stay, with the same class names, layouts and `previewImage` PNGs (ZTE launcher). `WidgetActionRuntime` (a headless Flutter engine) is deleted; the 30 s short FGS calls `WidgetActionRunner` in the shared engine. `WidgetOverlayActivity`/`WidgetConfigureActivity` become Compose activities in `:android:feature:widgets-panel`, with the same FQCNs. |
| Scheduled prompts (`ScheduledPrompt*`) | ~1.3k | `:android:platform:schedule` | `ScheduledPromptRuntime` (a headless engine) is deleted; the worker calls `ScheduledRunner` from `:shared:engine:schedule`. There is **one** recurrence implementation (today Dart and Kotlin each have one, risk R3), golden-tested across DST changes. The Kotlin compare-and-set store and permanent claims stay. |
| Launch and OS bridges (`MainActivity`, `Launch*`, `SessionLinkIngress`, `PinnedSessionShortcuts`, `ManageSpaceActivity`, `TailscaleHandoff`, `StorageAccess`, `ProjectExportBridge`, `LocalPdf*`, `ReadAloudBridge`, `NetworkMonitor`, `ThermalMonitor`, `AppLifecycle`, `CrashDiagnosticsBridge`, `NativeCrashStore`, `ByoHostSigner`) | ~3.8k | `MainActivity`/`ManageSpaceActivity` → `:android:app` as `ComponentActivity`s with Compose. `Launch*` produce `EntryRequest`s. PDF/TTS go to `:android:platform:media`; the rest to `:android:platform:device` | MethodChannel handlers become interface implementations. Event channels (`oc/network/events`, `oc/thermal/events`) become `Flow`s. |
| Termux bridge (`Termux*`) | ~0.8k | delete, or keep in `:android:platform:termux` | Owner decision Q1 (builtin-linux §12). |
| Channel glue (`NativeChannelReplies`, `MainActivityBackgroundChannel`, `GeneratedPluginRegistrant`, ~16 channel registrations) | ~2k | deleted | — |
| Kotlin readers of `FlutterSharedPreferences` (`flutter.oc.profiles`, `flutter.oc.widgetSessions`, `flutter.oc.appLocale`, restore-authority gates) | — | unchanged on day 1 | Possible because the Android `KeyValueStore` *is* that file (§12). Readers move behind `KeyValueStore` in a later release, together with the writers (state-data risk 3). |

The Rust engine (`engine/phone`, ~12.7k lines) and every shell script asset carry over unchanged. The scripts become
resources with a checksum list, so an accidental edit fails CI (builtin-linux risk 5).

---

## 12. Day-1 in-place update under B

Nothing here is unique to B. What B adds is that the migration is an Android module that runs before the graph starts
any engine.

1. **`:android:app` identity.**
   - Same `applicationId` and the same signer as the *installed* build. The brief names `1DE5BF08…D60C`; GitHub
     releases use `842284B2…1C` (packages-build §0.3). Verify with `apksigner` before delivery.
   - versionCode on a new base ≥ 10000, above every installed local build (4300–4511).
   - Identical manifest component FQCNs, notification channel ids, shortcut ids and worker class names.
2. **`OcApplication.onCreate` order:**
   1. uncaught-exception handler (crash store, categories only);
   2. the `Redactor`;
   3. the crash-loop guard: consecutive crashes inside N seconds → safe mode with Report and Start fresh;
   4. `FlutterMigration.runIfNeeded()`;
   5. build `AndroidAppGraph`.

   The activity draws a kit "Opening…" frame at once, as the bootstrap gate does today.
3. **`:android:migration:flutter` steps** (network-data §8–9, state-data §3):
   1. Copy `FlutterSharedPreferences.xml`, `FlutterSecureStorage.xml`, `FlutterSecureKeyStorage.xml` and
      `FlutterSecureStorageConfiguration*.xml` byte for byte to `files/migration/flutter-v57/`.
   2. **Adopt `FlutterSharedPreferences` in place** as the Android `KeyValueStore`: same file, the `flutter.` prefix,
      `long` ints, both list encodings, and the double prefix.
   3. Decrypt the flutter_secure_storage v11 entries (AES-GCM, RSA-OAEP unwrap with alias
      `<package>.FlutterSecureStoragePluginKeyOAEP`). Re-store them in our Keystore AES-256-GCM `SecretStore` and verify
      each by read-back. Any undecryptable entry marks that profile "needs password again"; it never aborts.
   4. Run the idempotent Flutter-side migrations, such as the retired keys and the notification-rules merge.
   5. Leave every file untouched: `files/linux/**`, vaults, `voice_models/`, `diagnostics/`.
   6. Re-establish the built-in Linux restore-authority gates (builtin-linux trap T1).
   7. Run a count-and-hash audit.
   8. Record the marker `native.migration.v1`.
4. **Big blobs move to Room in their own versioned step:** the offline queue (up to 60 MiB in XML today), the drafts
   index and the caches. Dispatch markers are preserved byte for byte.
5. **Shorebird ends.** The native self-updater (`:android:platform:update`, GitHub release + `SHA256SUMS` + same-signer
   check + `PackageInstaller`) ships in the first native release. The in-app update notice keeps its lowest-priority
   slot.
6. **Proof:** the upgrade journey runs on the emulator before any owner APK (testing-perf §0.6):
   1. seed a real Flutter release;
   2. place widgets, tiles, a pinned shortcut, a schedule and a channel customisation;
   3. `adb install -r` the native APK;
   4. verify with Maestro flows plus file checks;
   5. reboot and check the restore;
   6. repeat with the migration interrupted.

---

## 13. How each subsystem lands

| Map (features) | Shared (KMP) | Android now | iOS later | Linux desktop later |
|---|---|---|---|---|
| **protocols** (46) | `core:net`, `protocol:oc2` (dialect table, SSE loop, 91-event union, adapter), `domain` (gateways, 85 capability flags, value types), command receipts, reconnect-by-refetch rules | `androidMain`: OkHttp engine, leaf-DER `X509TrustManager`, network-change hook | Darwin engine; pinning in a `URLSession` delegate; `NWPathMonitor` | the same OkHttp code through `jvmShared` |
| **state-data** (55) | `core:data` (`KeyValueStore`, `SecretStore`, `BlobStore`, `WriterLane`, `ProfileScope`), engines split per STATE-DATA ids, Room for the queue, drafts and caches | SharedPreferences actual (the Flutter file, `commit()`), Keystore AES-GCM `SecretStore`, `:android:migration:flutter` | `NSUserDefaults`/DataStore, Keychain (`AfterFirstUnlockThisDeviceOnly`) | DataStore or a properties file, Secret Service (libsecret over D-Bus) |
| **agents-backends** (63) | `protocol:paseo`, `protocol:codex`, `protocol:team`, `engine:agents` (merged feed, handoff, auto-approval), `engine:team` | `platform:agents-host`, `platform:team-engine`; screens `feature:agents`, `feature:team` | remote Paseo/Codex only; no phone agents | local Paseo/agents through the desktop `RuntimeHost` (no proot) |
| **builtin-linux** (64) | `engine:runtime` (rules, setup protocol, component registry and pins, manager parsers, scripts as resources) | `platform:linux-runtime`, `platform:terminal`; screens `feature:phone` (This phone, setup, Linux manager, services, terminal, keep running) | not available (`supportsBuiltinLinux = false`; screens absent, never disabled) | `RuntimeHost` spawns host processes directly; terminal via pty4j [U] |
| **ui-kit** (58) | `:shared:design` (colour maths, packs, contrast guard, style data, motion and width tokens, text-scale caps), teaching registry | `:android:ui:kit` (Compose, ~220–330 parts: Solid at pixel parity first, then Soft/Brutal/Paper/Signal; frost only on composer and header) | SwiftUI kit reading the same tokens (generated Swift constants) | lift `:android:ui:kit` → `:shared:ui:kit` (android + desktop) |
| **screens-nav** (112) | `presentation:nav` (routes, `AppNavigator`, `EntryRouter`), one screen model per screen group, capability gating | `:android:ui:shell` (Nav3, scenes, back policy) + `:android:feature:*` | `NavigationStack` + SwiftUI screens | lifted shell with multi-pane and keyboard shortcuts |
| **chat-genui** (81) | `engine:transcript`, `engine:composer`, `engine:approvals` (one `FormSpec`), `engine:genui`, `content:markdown`, `content:code`, `presentation:chat` (rows model, turn machine) | `feature:chat` (single owner): reverse `LazyColumn`, reveal mask, grow spring, `SelectionContainer`, composer on `imePadding`, request cards | SwiftUI transcript (a `UICollectionView`-backed list if `List` stutters [U]); `AttributedString` markdown renderer | lifted |
| **media-voice** (50) | `engine:media` (pure rules + `AudioCapture`, `SpeechRecognizer`, `SystemTts`, `PcmPlayer`, `QrScanner`, `PdfPageRenderer` interfaces) | `platform:media`: sherpa-onnx 2.0.1 (Whisper + Kokoro, keep rules added), `AudioRecord`, `TextToSpeech`, isolated `PdfRenderer` service, CameraX + zxing-cpp, Photo Picker, FileKit | Apple Speech or the sherpa C API [U], `AVSpeechSynthesizer`, PDFKit, VisionKit scanner, PHPicker | sherpa JVM jar [U], PDFBox, zxing core on picked images |
| **android-platform** (65) | `engine:attention`, `engine:schedule`, widget/tile/shortcut models, exit classifier, keep-alive advice | `platform:background`, `platform:widgets`, `platform:schedule`, `platform:device` | WidgetKit, Live Activities, App Intents, `UNNotificationCategory`, BGTaskScheduler | tray + libnotify, systemd user timer or in-process |
| **packages-build** (41) | catalog, convention plugins | signing tasks, versionCode base, ABI splits, R8 rules + analyzer, size gate, engine hash check, renamed release scripts (the output path changes from `build/app/outputs/flutter-apk/`) | XCFramework + SwiftPM, macOS CI | `.deb` + tarball, then a real AppImage |
| **desktop-web-cross** (65) | `PlatformCapabilities`, `ExternalLinkPolicy`, one `Redactor` with one vector suite, diagnostics, crash report builder, release-tag parser, shortcut map, accessibility rules as data | crash store + handler, crash-loop safe mode, WebView hardening, Wi-Fi share binding, manifest posture | remote-only capabilities, resume-refetch rule | window state, key router, file drop, keyring, packaging; Web: dropped |

---

## 14. Owner design rules: where each is enforced in B

| Rule | Enforced in | How |
|---|---|---|
| Android-native feel: ripple, predictive back, stretch, haptics | Android kit + shell | AndroidX defaults (Android `RippleDrawable`, stretch on API 31+, `LocalHapticFeedback`); one `KitHaptic` enum; the predictive-back policy in §8.3 |
| Smooth streaming | `engine:transcript` + Android `feature:chat` | ≤ 50 ms publishes, block rows, draw-phase fade, baseline profile, Macrobenchmark gate |
| 150–250 ms motion; reduced motion | `:shared:design` (durations), kit `MotionPolicy` | standard motion scheme, never expressive; `ANIMATOR_DURATION_SCALE` + in-app Full/Calm/Off; nothing moves at Off |
| Kit-only UI | Gradle rule + Konsist | only `:android:ui:kit` sees `material3`; features import layout/lazy/semantics only (kit-v2 §9 allowlist) |
| No glass (one frost exception) | kit | solid surfaces; `KitFrost` only on the chat composer and header while scrolled under, with a solid fallback |
| Plain words, never raw errors | `Failure`, `Redacted`, `presentation:words` | there is no type path from server text to a label |
| Obvious options as visible rows; no new bar or row levels | screen models expose primary actions as lists; design review + Roborazzi captures | — |
| Actions name their target | `StringKey` templates take the target name as an argument | — |
| Steer, never queue | `engine:delivery` | default delivery is steer; queue only when the person picks it, and only where the server cannot steer |
| Component hard max widths | `:shared:design` caps + kit | every kit part declares a cap; a tablet-width Roborazzi test |
| Frame text ≤ 1.3×, reading text full | kit `FrameTextScale` | prove it at 200 % on Android 14+ non-linear scaling [U] |
| Arabic RTL | kit + words | Compose `LayoutDirection`; mirrored icons; bidi isolates; code always LTR; every kit test runs twice |
| Teach hidden gestures | `engine:settings` teaching registry + kit tip/tour | tips at most twice, one at a time, 2 min cool-down, replay from Help › Tips |

On iOS the same table applies, with iOS meanings: system feedback generators, swipe-back, `UIAccessibility` reduce
motion. The owner must decide one thing there: the "no glass" rule versus iOS's system Liquid Glass chrome (§22).

---

## 15. Testing in B

### 15.1 Test-first, from the catalog (now)

- The 1,544 behaviours go into `commonTest` of the owning shared module:
  - 677 unit, 267 contract, most of the 446 integration;
  - Android-only integration rows (prefs, WorkManager, notifications, files) run as Robolectric host tests in the
    platform modules;
  - 65 migration rows run as Robolectric tests, plus instrumented tests for the Keystore.
- Each test is named after its catalog row, and a ratchet counts coverage by grep.
- Contract tests replay recorded OpenCode 2.1.1, Paseo 0.11.2, Codex and Gas City traffic through Ktor `MockEngine`.
- The existing Kotlin JVM tests move with their code and stay green.

### 15.2 The lever B adds: screen-model tests

- Every screen model is UI-free, so behaviour that today lives in widget tests becomes a fast `commonTest`. Examples:
  - "Send is disabled while a revert is staged";
  - "the notification tap lands on the request";
  - "an agent conversation opens on its own backend".
- My estimate is that a large share of the 7,967 widget tests assert behaviour rather than layout or pixels [U: sample
  200 of them in slice 0]. Those port as screen-model tests whenever their screen's slice lands, *without* waiting for
  the UI-test phase. The rest stay "later", as the owner asked.

### 15.3 Android UI tests (later, per the owner)

One harness, on Android's own rendering:

- Robolectric 4.17 `createComposeRule` on a JDK 21 test JVM;
- Roborazzi 1.77.0 goldens, recorded on the VPS only;
- tests find nodes by visible text, run every kit test LTR and RTL, and check font scale 2.0 and tablet width.

The emulator is used only for gesture feel, IME, real notifications and widgets. Maestro flows, written now against the
Flutter release, become the parity suite.

### 15.4 Checks

The T0–T4 tiers from testing-perf §2.2 apply as they are, with B's extra gates:

- the iOS klib compile;
- Konsist;
- a recomposition-count test on the transcript.

---

## 16. Adding iOS later

### 16.1 What iOS reuses

- All of `:shared:*`: protocols, data rules, engines, screen models, routes, `EntryRouter`, copy keys, `Failure`
  tables, design tokens, markdown/code/diff models, the form engine and the gen-UI codec.
- All of their tests, run again on the iOS simulator in `iosSimulatorArm64Test`.

### 16.2 What iOS needs that does not exist

1. **The bridge.**
   - `:shared:umbrella` (iOS targets) exports the presentation modules and `IosAppGraph`, built as an XCFramework and
     consumed through SwiftPM (Kotlin 2.4.20 generates `Package.swift` for Swift export [V, kmp-cmp-status §2]).
   - Interop, in order of preference:
     1. Obj-C export plus our own ~30-line `watch(flow): Closeable` helper, so there is no third-party dependency;
     2. SKIE 0.10.x, which turns sealed types into Swift enums, `Flow` into `AsyncSequence` and suspend functions into
        cancellable `async`. Its 0.10.15 notes list Kotlin 2.4.20 support [S];
     3. Swift export, which is still Alpha in 2.4.x [V/S], when it is stable.
   - `ScreenHost<Model, State>` in Swift is an `@Observable` wrapper that owns a `ViewModelStore`. KMP-ObservableViewModel
     1.0.x is the off-the-shelf alternative [S].
   - Plan on iOS 17+ for SwiftUI Observation [U: owner/market decision]. Kotlin/Native itself needs iOS 15+ [V].
2. **Platform actuals:** the Darwin HTTP engine with pinning, Keychain `SecretStore`, `KeyValueStore`, files,
   `NWPathMonitor`, notifications, speech, TTS, PDFKit, VisionKit, PHPicker, share sheet.
3. **iOS-only surfaces:** WidgetKit widgets and App Intents; a Live Activity for a run the person started; a share
   extension. All are SwiftUI or Swift whatever the UI choice.
4. **The SwiftUI kit:** thinner than Android's, because `List`, `Form`, `Toggle`, `Picker`, sheets with detents,
   `swipeActions`, `contextMenu` and `confirmationDialog` are native. Chat parts (transcript blocks, composer, request
   cards, markdown/code/diff renderers) are custom.
5. **The SwiftUI screens:** about 170 pages. There is no built-in Linux, setup, Linux manager, local terminal, widgets
   overlay, tiles or Termux.
6. **Product limits to state honestly** (not B-specific):
   - no built-in Linux, because iOS forbids executing downloaded code;
   - no long-lived background sockets, so "tell me when the reply finishes" works only in the foreground or through a
     push relay that OpenCode servers do not offer today.
7. **Machines:** a Mac (a mini on the tailnet) or macOS CI runners; Apple Developer Program enrolment; TestFlight.

### 16.3 What iOS would cost in B (estimate [U]; units = one agent, one worktree, one bounded slice, ~1–3k reviewed lines including tests)

| Block | Units |
|---|---|
| Bridge, umbrella, SwiftPM, macOS CI, `ScreenHost`, `AppNavigator` in Swift | 5–8 |
| iOS actuals (net, storage, secrets, notifications, media, files) | 12–18 |
| iOS surfaces (WidgetKit, App Intents, Live Activity, share extension) | 8–12 |
| SwiftUI kit (tokens, rows, sheets, chat parts, renderers) | 20–30 |
| SwiftUI screens (~170 pages; the chat is the long pole) | 40–60 |
| Fixes in shared code found by iOS (JVM leaks the klib check missed, Kotlin/Native performance, memory) | 5–10 |
| **Total** | **90–138 units** |

### 16.4 Compared with shared Compose UI

- A shared-UI design's iOS cost would be about **45–70 units**: the bridge, actuals and surfaces are the same, plus
  iOS-adaptive kit variants and polish instead of a SwiftUI kit and screens.
- **B's iOS costs about 45–70 units more, roughly 1.5–2×.** What that buys is an app that behaves like an iOS app, where
  a Compose app on iOS behaves like the Android app.

### 16.5 The hedge

- Because B's Android UI is Compose, the iOS UI decision can be made **when iOS starts**, not now.
- If SwiftUI's cost is too high then, lift the Compose kit and screens to CMP for iOS exactly as for desktop (§17),
  plus iOS-adaptive variants.
- That fallback costs about the desktop lift plus 15–25 units of iOS polish. It is more than writing shared UI from
  day 1, but it is only paid if iOS really ships.

---

## 17. Adding Linux desktop later

### 17.1 What it takes

1. **Lift the kit.** `:android:ui:kit` → `:shared:ui:kit` (KMP: android + desktop, CMP plugin).
   - Because of the portable-Compose rule (§5.2), the work is mostly:
     - moving files;
     - swapping `Words` and haptics providers;
     - giving `platform/` parts desktop actuals: ripple becomes hover + focus ring, predictive back becomes Esc,
       stretch becomes no overscroll.
   - Android-only parts stay in `androidMain`: `AndroidView` terminal, WebView, CameraX.
2. **Lift or adapt screens** feature by feature. Desktop needs a wide shell: rail, list-detail-detail, keyboard
   shortcuts, context menus, hover, window state. The Flutter app already has rail and 2–3-pane parts, and Android
   tablet scenes cover some of it.
3. **Desktop actuals:**
   - OkHttp, already shared through `jvmShared`;
   - Secret Service keyring;
   - notifications (libnotify/D-Bus) and tray (CMP tray, with known GNOME issues [V]);
   - XDG file dialogs (FileKit);
   - `RuntimeHost` with direct process spawning (no proot), so the setup scripts run against the host;
   - terminal: pty4j + a Compose renderer, or JediTerm through `SwingPanel` [U: licences and fit].
4. **Packaging:** `.deb` + tarball (parity with today), then a real AppImage via `appimagetool`, then Flatpak or
   Conveyor. Rendering is X11/XWayland until SKIKO-28 lands, and fractional scaling may blur on GNOME [V/S].

### 17.2 Cost (estimate [U])

| Block | B | Shared-UI design |
|---|---|---|
| Kit lift (with the portable rule; **10–15 without it**) | 4–7 | 0 |
| Screens: lift + desktop shell and layouts | 12–20 | 6–10 (shell and layouts only) |
| Desktop actuals, terminal, packaging, CI | 14–20 | 14–20 |
| **Total** | **30–47 units** | **20–30 units** |

B costs about 10–17 more units on desktop. That is the price of keeping Android's UI on plain Jetpack until desktop is
real.

---

## 18. How agents build it in parallel

### 18.1 Ownership, mechanical

- A `MODULE_OWNERS` file lists one owner per module.
- **Single-owner units** (the rewrite's equivalents of today's list):
  - `:shared:core:*`, `:shared:domain`;
  - `:shared:engine:connection`, `:shared:engine:transcript`;
  - `:shared:presentation:nav` + `:android:ui:shell`;
  - `:shared:presentation:chat` + `:android:feature:chat`, one owner for the pair, as `chat_screen.dart` +
    `chat/*.dart` is today;
  - `:android:ui:kit`, `:android:ui:words` + the strings generator;
  - `:android:app`, `:android:migration:flutter`;
  - `:android:platform:linux-runtime`. The MethodChannel's "both halves" rule disappears because there is only one
    half now.
- The pixel line gives each feature two modules with a frozen contract between them: `UiState` + actions + `Route`s.
  - **Codex** (backend rule) builds engines and screen models with tests and writes the contract.
  - **Claude/Sonnet** builds the Compose screens against fake screen models (`FakeChatsModel`) that drive `@Preview`s
    and Roborazzi.
  - UI work starts the day the contract freezes, not when the engine is done.
- Model choice (owner rule):
  - **Opus** for slice-0 spikes, the transcript engine, the migration, built-in Linux ownership and the chat pair;
  - **Sonnet** for protocol mappers with fixtures, stores, kit parts and screens;
  - **Haiku** for scripted chores (golden re-records, fixture capture).

### 18.2 Feature pairs (presentation + Android feature)

| Pair | Contents |
|---|---|
| `start` | first run, opening, safe mode |
| `servers` | list, add/edit, pairing QR, trust, re-enter password |
| `chats` | Home list, needs-you, search, Go to, other projects and servers |
| `chat` | transcript, composer, requests, forms, cards, session menu, find, timeline |
| `newchat` | project, agent and model chips |
| `session` | review/revert, context, export, note, relations, share |
| `files` | project hub, browser, viewer, gallery/markup, Wi-Fi share |
| `git` | — |
| `agents` | Settings › Agents, install/check/sign-in, account |
| `phone` | This phone, setup, Linux manager, services, terminal, keep running |
| `team` | AI Team (if kept) |
| `schedules` | — |
| `usage` | — |
| `settings` | five groups, appearance, notifications, voice, language, privacy |
| `about` | diagnostics, report a problem, update notice, licences |
| `widgets-panel` | — |
| `factsbook` | last; flag-gated |

### 18.3 Waves (each wave's units are conflict-free by write set)

| Wave | Goal | Parallel units (examples) | Exit |
|---|---|---|---|
| **0 (slice 0)** | Prove the risky parts | build-logic + rules + Metro graph smoke + iOS klib job; the in-place install proof (package, signer, versionCode, FQCN survival of widgets/tiles/shortcuts/worker); secure-storage decrypt on the emulator; predictive back + container transform on Jetpack Nav3 1.2 (and the 1.3-alpha spike); 5,000-part transcript with recomposition counts + Macrobenchmark; `WriterLane` + `ProfileScope` against a copied prefs file; strings converter dry run; sample 200 widget tests for §15.2 | every spike green or a named fallback; build times measured on the VPS and PC |
| **1 (foundations)** | Core, protocol and kit base | `core:common`+Redactor vectors; `core:json`; `core:net`; `core:data`+migration; `domain`; `protocol:oc2`+contract replays; `:shared:design`+kit tokens/motion/press/rows; words pipeline; `platform:linux-runtime` move (JVM tests green); `platform:background`/`widgets`/`device` moves | logic tests for these areas green on the VPS |
| **2 (daily driver)** | The owner updates in place and chats | engines connection/transcript/delivery/approvals/composer; `protocol:paseo` + `engine:agents` + `platform:agents-host`; pairs `chats`, `chat`, `newchat`, `servers`; shell; finished-reply service; notifications | emulator upgrade journey passes; OC2 chat and Claude Code via Paseo work end to end; first owner APK |
| **3 (breadth)** | Flutter feature parity | `phone`, `settings`, `agents`, `files`, `git`, `session`, `schedules`, `usage`, `about`, `widgets-panel`; media/voice slices M1–M6 (media-voice §13.2); gen-UI forms; self-updater | Maestro parity suite green on the release build |
| **4** | The rest | AI Team, FactsBook, gen-UI cards, AppFunctions, Glance evaluation, targetSdk 37 slice | owner sign-off |
| **Later** | UI tests; other platforms | the 7,967 widget tests (§15.2–15.3); desktop (§17); iOS (§16) | — |

### 18.4 Machines (owner constraints)

- **Builds, JVM tests and goldens run on the VPS** (`203.0.113.10`). It has no KVM, so no emulator there.
- **The PC** owns the single emulator; one holder at a time. Its ceiling is about 2 Codex + 1 Claude agent, and about
  one Gradle build at a time (`-Xmx2g`, 2 workers). Run `hog_watch` throughout.
- From 00:00 to 02:00 only the VPS runs heavy work.
- No owner APK before an emulator pass.
- Agents run target tasks only (`:shared:engine:delivery:jvmTest`, `:android:app:assembleRelease`), never `build`.

---

## 19. Risks (ranked) and mitigations

**Specific to B**

| # | Risk | Mitigation |
|---|---|---|
| B1 | iOS cost and Mac dependency (§16) | Defer the iOS UI decision to iOS start (§16.5). Budget a Mac mini or macOS CI before it starts. Keep the klib compile green from day 1 so shared code is iOS-ready |
| B2 | Presentation leaks: Android types or logic in screens | No Compose plugin in presentation; Konsist; the klib compile; reviewers check "would SwiftUI need to re-implement this?" |
| B3 | An Android-shaped presentation layer | Name screen-model actions after intents ("open", "approve", "steer"), not widgets ("onSheetDismiss"). An iOS design pass on the top 10 screen models before iOS UI starts |
| B4 | Compose stability across the pixel line | Stability config file; slice-0 recomposition counts on chats and chat; immutable state with structural sharing |
| B5 | Desktop lift balloons | Portable-Compose rule from day 1 (Konsist); a quarterly dry-run compile of `:android:ui:kit` sources against CMP desktop in a throwaway job [U: cost] |
| B6 | Parity drift once there are 2–3 UIs | Parity ledger generated from routes × platforms; shared screen-model tests are the behaviour contract; Maestro flows per platform where possible |
| B7 | Strings generator becomes a bottleneck | One owner; generator tests; CI fails on key drift between `strings.xml` and the `StringKey` catalog |

**Any native rewrite (B inherits them unchanged)**

| # | Risk | Mitigation (source map) |
|---|---|---|
| R1 | Losing data or secrets on the switch | §12; copy files first; per-profile "needs password again"; verify by read-back; emulator upgrade journey before any owner APK (state-data §10) |
| R2 | Identity drift: widgets, tiles, shortcuts or workers lost | Identical FQCNs and ids; install-over test with placed widgets (android-platform R1) |
| R3 | Silent loss of the built-in Linux restore authority | Migration test + reboot test (builtin-linux T1) |
| R4 | Hard-won behaviours lost | Catalog-first tests; "do not simplify a branch without finding the test that created it" (chat-genui §8.1) |
| R5 | OpenCode 2 volatility (2.2.5 unprobed; Rust engine pinned to 2.0.29) | Probe and record fixtures for 2.1.1 at slice 0; dialect as a table; capability flags follow the version (protocols §8) |
| R6 | Compose has no error widget, so a bad composable crash-loops | Crash-loop guard + safe mode + launch report (desktop-web-cross §9.2.3) |
| R7 | Privacy regression from a new dependency or log | Redacting log writer first; egress allowlist; no `HttpLoggingInterceptor` in release; `exceptionsWithDebugInfo = false` |
| R8 | Shorebird ends; no hot fixes | Self-updater in the first native release; staged owner-only APKs before any public release |
| R9 | Android 17 changes (local-network permission, Memory Limiter) | Ship targetSdk 36 first; a dedicated targetSdk 37 slice (android-surfaces §0) |
| R10 | Phantom-process limit (32) | Keep the process budget guard; no extra `android:process` |

---

## 20. Effort estimate

**Units.** One agent, one worktree, one bounded slice, roughly 1–3k reviewed lines including tests. That is the same
currency as the revamp's 163 units.

All numbers are [U] estimates. Calibrate them after Wave 1 by measuring units merged per day, and re-plan.

### 20.1 Android to Flutter parity (the frozen feature set)

| Block | Units |
|---|---|
| Slice 0: build-logic, rules, spikes, install proof | 10–14 |
| Core: common, json, net, data, redactor, diagnostics, testing | 14–20 |
| `protocol:oc2` + contract replays | 10–14 |
| `protocol:paseo`, `codex`, `team` | 12–18 |
| Domain + engines (connection, transcript, delivery, approvals, composer, selection, read state, caches) | 30–42 |
| Attention, schedule, settings, update engines + Android platform rewiring (background, widgets, tiles, shortcuts, schedule) | 18–26 |
| Built-in Linux: Kotlin move + Dart policy port + scripts as assets + terminal | 22–32 |
| Agents on the phone | 12–18 |
| Media, voice, attachments, viewer, QR, PDF | 14–20 |
| Gen UI + one form engine | 6–9 |
| Flutter migration + upgrade journey | 8–12 |
| Shared presentation (screen models for ~45 screen groups) | 25–35 |
| Android kit (tokens, 5 styles, motion, ~220–330 parts, markdown/code/diff renderers) | 35–50 |
| Android screens (~264 pages; the chat pair is the long pole) | 50–75 |
| Words pipeline, 7 locales, RTL checks | 3–5 |
| Performance, release plumbing, self-updater | 8–12 |
| AI Team (only if kept) | 18–26 |
| **Total** | **~295–430 units** (about 255–380 without AI Team) |

**Calendar [U].** The binding constraint is not code writing. It is the gate:

- one emulator;
- the PC ceiling;
- VPS build slots;
- owner testing.

With 3–5 agents active and VPS builds, I would expect:

- the **daily-driver milestone** (Wave 2: in-place update, OC2 + Claude Code chat, notifications) about **5–8 weeks**
  after start;
- **Android parity** in about **4–6 months**.

Treat both as planning numbers to replace after Wave 1.

### 20.2 Later platforms

| Platform | B | Shared-UI design | Difference |
|---|---|---|---|
| Android parity | 295–430 | ~300–445 | B is ~5–15 units **cheaper**: no UI seams, no second string system, no desktop harness for Android, no CMP-lag workarounds. Partly offset by B's presentation boundary, stability config and words facade |
| Linux desktop | 30–47 | 20–30 | B is ~10–17 units **dearer** |
| iOS | 90–138 | 45–70 | B is ~45–70 units **dearer**, and buys native SwiftUI |
| **All three** | **~415–615** | **~365–545** | B is about **10–15 % more in total** if both later platforms ship |

### 20.3 How to read this

- If iOS and desktop both ship, B is the more expensive programme, by roughly 50–70 units. In exchange:
  - iOS feels like iOS;
  - Android runs on all-stable Jetpack today and gets Google's fixes first;
  - the test suite is mostly fast screen-model tests.
- If either later platform slips by a year or is dropped, B is cheaper and simpler.
- B never closes the cheaper route: the Compose UI can still be lifted for iOS (§16.5).

---

## 21. Slice 0: what must be true before Wave 1

Each item is under a day for one agent, except the install proof.

1. `build-logic` with dependency rules failing a deliberate violation. Metro graph smoke across
   `:shared:presentation:*` and `:android:app`. The iOS klib job green on Linux.
2. **The in-place install proof** on the emulator:
   - Flutter 2.1.0+57 build → native APK at versionCode ≥ 10000 with the same signer;
   - widgets, tiles, a pinned shortcut, a WorkManager schedule and channel settings survive;
   - FQCNs are kept while the classes live in library modules.
3. flutter_secure_storage v11 decrypt and re-store on the emulator. Robolectric cannot do the Keystore [U].
4. **Predictive back + container transform** on Jetpack Nav3 1.2 (option A wiring), plus the Nav3 1.3-alpha deferred
   spike on Compose 1.13-beta.
5. **Transcript benchmark:** 5,000-part reply and 1,000-message history, recomposition counts with and without the
   stability config, Macrobenchmark frame timing, and a Baseline Profile.
6. `WriterLane` + `ProfileScope` sweep against a copied `FlutterSharedPreferences.xml` (both list encodings).
7. Strings converter dry run on all 7 ARBs. Count the flattened `select`s and the plural forms per locale.
8. Sample 200 widget tests and classify them as behaviour, layout or pixel, to size §15.2.
9. Measure clean and incremental `:android:app:assembleRelease` and `jvmTest` times on the VPS and the PC.

**Exit:** each item is green, or has a named fallback written into the plan. For example: Koin for Metro; SQLDelight
for Room; option A back wiring for the Nav3 1.3 spike.

---

## 22. Decisions needed from the owner

These are the B-specific ones. The general ones are in the maps: signer lineage, Termux, AI Team tier, durable log,
OpenCode pin, notification languages.

1. **iOS timeline.** Is iOS planned within ~6 months? If yes, a shared-Compose-UI design is cheaper. If later or
   unsure, B.
2. **iOS UI when it starts:** SwiftUI (B's default) or lift the Compose UI (§16.5). This can wait until iOS starts.
3. **iOS look versus "no glass".** A native iOS app built with current Xcode gets the system's Liquid Glass on bars and
   tab bars. Secondary sources disagree on whether opting out is still possible [S]. Accept system glass on system
   chrome, with the app's own surfaces solid? Recommended: yes, because "native feel" is the higher rule there.
4. **Portable-Compose rule from day 1** (recommended: yes; it costs little and caps the desktop lift).
5. **String source of truth:** Android `strings.xml` (recommended in B) instead of ARB.
6. **Minimum iOS version** when iOS starts: 17+ for SwiftUI Observation (recommended) or 16.

---

## 23. Sources

**Internal (read 2026-11-01).**

- Maps:
  - `map/protocols.md` §0, §3–§11;
  - `map/state-data.md` §1–§3, §7–§15;
  - `map/builtin-linux.md` §0, §2, §7, §8, §12, §13;
  - `map/android-platform.md` §0, §2, §10, §15–§17;
  - `map/packages-build.md` §0, §11, §12;
  - `map/desktop-web-cross.md` §1, §2, §9;
  - `map/screens-nav.md` §0–§3, §11;
  - `map/chat-genui.md` §0, §1, §7, §8;
  - `map/agents-backends.md` §0–§2, §8–§10;
  - `map/ui-kit.md` §0, K5–K7;
  - `map/media-voice.md` §0, §13.
- Research:
  - `research/kmp-cmp-status.md` (all);
  - `research/architecture-stacks.md` (all);
  - `research/network-data.md` §0;
  - `research/rendering-chat.md` §0, §11;
  - `research/motion-native.md` §0, §10, §11;
  - `research/media.md` §0, §10;
  - `research/android-surfaces.md` §0, §16, §17;
  - `research/genui.md` §0;
  - `research/testing-perf.md` §0, §2, §4, §5, §12.
- Catalog: `catalog/BEHAVIORS.md` (counts per area and level).
- Demo: `/home/eslam/Storage/Code/kmp-demo` (module layout).
- Owner memory files:
  - `dev-freeze-native-rewrite-2026-11-01`, `android-native-feel`, `no-glass-rule`;
  - `codex-backend-claude-frontend`, `subagent-model-choice`, `pc-capacity-ceiling`, `quiet-hours-vps-only`;
  - `revamp-plan` (unit currency), `no-users-yet` (superseded here by the brief's "plan as if real users exist").

**Web (fetched 2026-11-01; treat as data).**

- Termux licence: `terminal-view` and `terminal-emulator` use Apache-2.0 code under an exception to the app's GPLv3
  [V]: https://github.com/termux/termux-app/blob/master/LICENSE.md
- SKIE 0.10.15 release notes credit Kotlin 2.4.20 support; the release year was not shown on the page [S]:
  https://github.com/touchlab/SKIE/releases/tag/0.10.15 ; Kotlin Slack thread on 2.4.20 support:
  https://slack-chats.kotlinlang.org/t/34424949/any-word-on-when-skie-will-add-support-for-kotlin-2-4-20-off
- Kotlin 2.4.20, Swift export still Alpha, with Flow/suspend mapping: https://kotlinlang.org/docs/whatsnew2420.html ;
  https://blog.jetbrains.com/kotlin/2026/09/kotlin-2-4-20-released/ ;
  https://medium.com/@santimattius/kotlin-2-4-swift-export-flow-asyncsequence-and-swift-packages-6a97c3afe7b7 [S]
- KMP-ObservableViewModel 1.0.x (AndroidX ViewModel to SwiftUI) [S]:
  https://klibs.io/project/rickclephas/KMP-ObservableViewModel ;
  https://central.sonatype.com/artifact/com.rickclephas.kmp/kmp-observableviewmodel-core
- iOS 27 / Liquid Glass adoption; sources disagree on whether the opt-out remains [S]:
  https://www.sentia.com.au/blog/adopting-liquid-glass-before-ios-27 ;
  https://useyourloaf.com/blog/wwdc-2026-viewing-guide/ ;
  https://dev.to/mr_manushukla/ios-27-liquid-glass-in-xcode-27-the-migration-apple-actually-requires-2026-2bg5
- All other version facts are as verified in the research files above, with their own source lists.

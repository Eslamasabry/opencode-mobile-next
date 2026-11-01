# Proposal A: one Kotlin Multiplatform codebase with shared Compose UI (Android first, iOS and Linux desktop later)

Written 2026-11-01 for the OpenCode Mobile native rewrite. This file is a proposal, not a decision. It makes the strongest
honest case for one angle and states that angle's real weaknesses, so the owner can compare it with the other proposals.

**The angle:** the core logic (protocols, state, stores, rules) and the UI (kit, screens, navigation) are written once in
Kotlin Multiplatform `commonMain` with Compose Multiplatform (CMP). Android, Linux desktop (JVM) and iOS each add a thin
platform layer. Everything only Android can do (built-in Linux, widgets, notifications, foreground services, tiles, voice
runtime, camera) lives in Android modules behind interfaces. The Android build is the product; desktop is compiled and
tested from day 1 but not shipped; iOS is kept compilable but not built until its own phase.

Inputs read: the eleven maps in `../map/`, the nine research files in `../research/`, the behaviour catalog
(`../catalog/BEHAVIORS.md`, 1,544 rows from the 6,663 logic tests), the demo `/home/eslam/Storage/Code/kmp-demo`, the
Kotlin in `/home/eslam/Storage/Code/wt/oc_app-batch1/android`, and the owner memory files (MEMORY.md plus
`dev-freeze-native-rewrite-2026-11-01`, `pc-capacity-ceiling`, `no-heavy-workflows`, `subagent-model-choice`,
`codex-backend-claude-frontend`, `owner-tests-i-gate`, `settings-scope-model`, `design-schools-direction`, `no-glass-rule`,
`app-updates-two-layers`, `phantom-process-limit`, `build-vps`, `stabilize-before-shipping`).

Labels: **[V]** verified on 2026-11-01 from a primary source (most come from the research files, which carry their own
sources); **[R]** read in the repo, maps or memory; **[S]** secondary source; **[U]** unverified, my projection or estimate,
check in slice 0.

---

## 0. The proposal on one page

1. **What gets built.** About 60 Gradle modules in one build. Pure logic and all screens are `commonMain` Kotlin. The UI
   is Compose Multiplatform, which on Android *is* Jetpack Compose (the Android variant of
   `org.jetbrains.compose.material3:material3:1.13.0-beta01` depends on `androidx.compose.material3:material3:1.5.0-beta01`)
   [V, kmp-cmp-status §0]. So ripple, stretch overscroll, predictive back, IME animation and haptics are Google's own
   Android code, with no smoothness cost.
2. **The demo the owner liked is already Proposal A in miniature.** In `/home/eslam/Storage/Code/kmp-demo`, every
   screen (`ChatScreen.kt`, `ConversationsScreen.kt`, `Markdown.kt`, `Kit.kt`, `Theme.kt`, `App.kt`) is in
   `shared/src/commonMain`; the Android app module is four files (`MainActivity`, `DemoApplication`, `ReplyService`,
   `AndroidPlatform`) [R]. "So so so much smoother" was measured on shared CMP UI.
3. **Layers:** `core` → `protocol` → `engine` → `ui:kit` → `feature` → `ui:shell` → `androidApp`. Gradle enforces the
   rules: features cannot see protocols, only the kit may use Material 3, nothing above `engine` sees a transport
   exception.
4. **Stack:** Kotlin 2.4.21, CMP 1.13.0-beta01 (1.12.2 fallback), Navigation 3 1.2.0, Lifecycle/ViewModel 2.11.0,
   Metro 1.5.x for DI (Koin plan B), Ktor 3.6.1 on OkHttp 5.5.1, kotlinx.serialization 1.12.0, Room 3.0.3 with the bundled
   SQLite driver, Kermit 2.2.0 [V, research files]. The existing `FlutterSharedPreferences` file is adopted in place on
   day 1, so every Kotlin reader of `flutter.*` keys keeps working.
5. **Existing Kotlin (38,441 lines)** moves into plain Android library modules almost unchanged. Only the 36 files that
   import `io.flutter` (channel glue, the Flutter activities and the two headless-engine runners) are replaced [R, counted].
6. **Android first and best stays true by construction:** Android-only screens and services are native; desktop must only
   *compile and pass common tests*, never block an Android change; `androidMain` may use newer AndroidX than CMP when the
   Android experience needs it.
7. **Later cost (estimates [U]):** Linux desktop 6–10 agent-weeks after the Android switch; an iOS remote client
   12–18 agent-weeks, 18–30 with widgets and a push relay, plus a Mac and an Apple account. Under an Android-only UI the
   same targets would first need the kit and screens moved to CMP (25–40 agent-weeks) or rewritten natively (40–60).
8. **Android switch effort (estimate [U]):** 100–150 agent-weeks for full parity, 90–135 for the daily-use scope
   (AI Team, FactsBook, Termux and Gas City later). That is about 15–27 calendar weeks (central guess about 20) at 5–6
   concurrent agents, the ceiling the PC and VPS allow today. About 5–9 agent-weeks of that is the price of sharing the UI.
9. **The real weaknesses** (§2): CMP trails Jetpack by weeks to months on new Android APIs; Compose Desktop on Linux has
   no screen-reader support and runs on XWayland; iOS will not feel native without iOS-specific kit variants; build cost
   and memory rise with a second target; some libraries (WebView, terminal emulator, camera, PDF) are not multiplatform and
   each needs a seam. The strongest argument against A is that an Android-only Compose UI could be moved to CMP later.
   §2.3 answers it.

---

## 1. The case for A

### 1.1 It costs Android nothing in smoothness

- On Android, CMP resolves to Jetpack artifacts: `ui-android:1.12.2` → `androidx.compose.ui:ui:1.12.1`,
  `material3-android:1.13.0-beta01` → `androidx.compose.material3:material3:1.5.0-beta01` [V, published `.module` files,
  kmp-cmp-status §0; motion-native §0.3 checked the demo's resolved graph].
- The native-feel list the owner asked for (ripple as a platform `RippleDrawable`, stretch overscroll on API 31+, glow on
  26–30, predictive back via AndroidX `navigationevent`, `LocalHapticFeedback`, IME insets) is the same code under A as under
  an Android-only UI [V, motion-native §0.7].
- Reduced motion is free on Android: Compose reads `ANIMATOR_DURATION_SCALE` and installs it as `MotionDurationScale`
  [V, motion-native §0.4].
- Smooth streaming is a data-shape problem (coalesced publishes, block-level lazy items, paint-only word fade), not a
  toolkit problem [V/U, rendering-chat §0, motion-native §0.6]. None of that depends on where the composables live.

### 1.2 The UI is most of the app, so writing it once matters most

- In the Flutter app, `lib/ui/` is about 277k of the 479k Dart lines: the kit is 280 files and ~107k lines, screens ~125k,
  widgets ~36k [R, architecture-stacks §1, ui-kit §0, screens-nav scope].
- Under A, that work is done once for three platforms. Under an Android-only UI, iOS and desktop each need the UI again
  (in CMP later, or in SwiftUI/another toolkit).
- The owner's **kit-only rule** fits A exactly: one kit module family is the only home of UI parts on every platform. With
  per-platform UIs, "one kit" becomes two or three kits that drift.

### 1.3 Keeping parity with what ships today

- The Flutter app already runs on Linux desktop (experimental `.deb`/tarball with AppStream metadata, a keyboard layer,
  window-state restore, a release check) [R, desktop-web-cross 02–12]. An Android-only rewrite drops that product until a
  second UI exists. Under A the desktop target exists from the first commit and the desktop interaction layer
  (shortcuts, command launcher, context menus, fine-pointer hover) is common code that also serves Android tablets and
  hardware keyboards (desktop-web-cross §1.8).

### 1.4 A faster, emulator-free loop for the agents, on day 1

The machines are the bottleneck, not the architecture: one emulator on a 16 GB PC with a ceiling of about 2 Codex leads +
1 Claude subagent after two OOMs, and a VPS with no KVM [R, pc-capacity-ceiling, testing-perf §0.11]. A desktop JVM target
moves UI work off the emulator:

| Need | Under A (desktop target exists) | Under an Android-only UI |
|---|---|---|
| Behaviour tests of kit parts and screens | common `runComposeUiTest` on desktop JVM, headless on the VPS [V, testing-perf §0.3] | Robolectric host tests (also JVM, slower, Android-resource quirks) |
| Screenshots for review and the "progress images" rule | Roborazzi 1.77.0 on Compose Desktop, generated on the VPS [V] | Roborazzi on Robolectric (works too) |
| Live UI loop for an agent | Compose Hot Reload 1.2.0 on desktop, with an experimental MCP server agents can drive [V/S, kmp-cmp-status §9] | Emulator + install per change |
| Widgetbook replacement (the owner's live catalog of real screens with fakes) | a desktop "kit gallery" app from the same composables | Android-only gallery on the emulator |

Honest limit: desktop pixels are Skia-on-desktop, not Android HWUI, so they are not the owner's truth. Visual sign-off still
needs emulator captures ([R] judge-visuals-hard, test-on-emulator-not-phone). Robolectric covers much of the same ground
under an Android-only UI, so this is a solid advantage, not a decisive one.

### 1.5 Compile errors catch platform leaks early

- In `commonMain`, `android.*`, `Context`, `R.string`, `LocalContext` and Java-only APIs do not compile. A Sonnet agent
  that reaches for an Android-only idiom in a screen gets a build error on its own branch, not a migration project a year
  later.
- A Linux CI job compiles common code to iOS klibs (`iosArm64`), which also keeps JVM-only APIs out of `commonMain`
  [V/U, kmp-cmp-status §11.3: whether a cinterop-using dependency blocks the Linux klib compile is unverified].

### 1.6 It forces the boundaries the Flutter app had to learn

The rules the Flutter repo enforces by gates and review (UI talks to the domain gateway only, gate on
`ServerCapabilities`, kit-only, `openExternalLink`, no raw errors) become module and source-set boundaries. A seam
everywhere the platform differs is exactly what Android needs anyway for testability: fakes in `commonTest` for haptics,
pickers, notifications and the Linux runtime.

### 1.7 One process, one brain, every surface

Widgets, tiles, workers, receivers and services reach the same app graph in one process. The Flutter app ran separate Dart
engines for widget actions and scheduled prompts and had to `reload()` prefs around them [R, android-platform §2,
state-data §1.6]. That benefit holds for any native design; A keeps the brain in `commonMain` so desktop and iOS reuse
it too.

### 1.8 Production evidence at this scale exists

Markaz ships 100+ screens fully in CMP with 5M+ downloads; Bitkey shares 95% of its mobile code; JetBrains Toolbox is
Compose Desktop since 2021 [S, kmp-cmp-status §10, vendor case studies]. No public app matches this one's shape (streaming
chat + on-device Linux + widgets); the native services are plain Android under every proposal.

---

## 2. Real weaknesses (do not skip)

### 2.1 Weaknesses that cost something on Android today

1. **CMP trails Jetpack.** The predictive-back fix for the container transform (deferred two-stage transitions) is in
   Jetpack Compose 1.12/1.13 and Navigation3 1.3.0-alpha; the CMP Nav3 artifact is at 1.2.0 [V, motion-native §0.1].
   CMP Material3 1.13.0-beta01 wraps Jetpack M3 1.5.0-beta01 while Jetpack is at beta02 [V]. An Android-only UI gets each
   new Compose API weeks to months sooner. Mitigation: `androidMain` and Android modules may use newer AndroidX (Gradle
   picks the highest), but `commonMain` APIs are bound to what CMP publishes.
2. **Two string pipelines.** CMP resources are packed as Android *assets*, not `R` resources. Manifest labels, widget
   XML, `shortcuts.xml` and statically declared notification text still need Android `res/values*` [V, kmp-cmp-status §6].
   Both are generated from the same ARB source; per-app language with CMP resources is unverified [U].
3. **More seams to build.** Anything Android-only that a screen touches needs an interface: activity-result launchers
   (Photo Picker, SAF, permissions, "install unknown apps"), the dev-preview WebView, the camera viewfinder, the terminal
   emulator model, PDF pages, notification-permission prompts, the system share sheet. Roughly 15–20 seams (§11), each
   small, each a place where an agent can get it wrong.
4. **More build work.** Every KMP module compiles for `android` and `jvm("desktop")`. Expect roughly 1.3–1.6× the compile
   work of an Android-only build and one extra Gradle source-set graph per module [U, unmeasured; slice 0 measures it].
   On the 16 GB PC that matters; agents must run target-specific tasks only.
5. **Three compiler plugins move together.** Kotlin, the Compose compiler, Metro and KSP (for Room) are version-coupled.
   An upgrade is a pair or triple bump [V, architecture-stacks §3.5].
6. **Fewer examples for agents.** Most Android answers on the web assume Hilt, `R.string`, `LocalContext`, Jetpack-only
   Navigation. Agents will hit compile errors and need the rewrite's AGENTS.md rules (§16.6).

### 2.2 Weaknesses that show up only when desktop or iOS ship

1. **Linux desktop accessibility: none.** The Compose Desktop accessibility page lists Linux as "Not supported"
   (macOS full, Windows via Java Access Bridge) [V, kotlinlang.org compose-desktop-accessibility, page dated 2024-12-03].
   A screen-reader user on Linux cannot use the desktop app. This cannot be fixed inside this project.
2. **Linux desktop renders through X11/XWayland.** Native Wayland is open (SKIKO-28); fractional scaling on GNOME is
   blurry; tray icons have open bugs [V/S, kmp-cmp-status §2].
3. **iOS will not feel native without work.** Compose draws its own controls; there is no UIKit control underneath. iOS
   gets the back swipe and VoiceOver mapping [V, CMP iOS accessibility page], but ripple, Material sheets and Android
   motion look foreign. The kit needs iOS-adaptive variants (press highlight, overscroll bounce, sheet shape) to match an
   owner who prizes native feel.
4. **iOS needs a Mac.** Final Apple binaries cannot be built on Linux [V]. That is hardware or CI money, and a second
   toolchain to keep green.
5. **iOS background limits are independent of the UI choice.** No background sockets: "tell me when the reply finishes"
   needs push (APNs) through a server-side relay, which this project does not have [R, android-platform §16].

### 2.3 The strongest counter-argument, answered

*"CMP on Android is Jetpack Compose, so write an Android-only Compose UI now and move it to CMP when iOS or desktop is
actually funded."*

This is a fair argument. The answer:

- The move later is not free. It means hunting every `R.string`, `LocalContext`, `Activity` cast, Android-only library
  and Jetpack-only API across ~100k lines of screens and ~60k lines of kit, then adding the seams in §11 anyway. Under A each
  leak costs one compile error on the branch that introduces it.
- The day-1 premium of A is small: the seams, the second target in CI, CMP resources instead of `R`, desktop stubs.
  Estimated 5–9 agent-weeks out of 100–150 (§18).
- A gives Android-side benefits immediately (headless UI tests and screenshots on the VPS, Hot Reload, a desktop kit
  gallery), not only "someday" benefits.
- If iOS and desktop are never funded, A has paid 5–9 agent-weeks for a stricter architecture and a faster UI loop. If
  they are funded, A saves 25–60 agent-weeks.

Where the counter-argument wins: if the owner is sure no second platform will ever ship, and wants every new Jetpack API
the week it lands, an Android-only UI is simpler.

---

## 3. Toolchain and targets

| Item | Pin now | Notes |
|---|---|---|
| Kotlin + Compose compiler | 2.4.21 | 2.5.0 planned Dec 2026; move only with Metro and CMP [V] |
| AGP / Gradle / JDK | 9.3.3 / 9.7.x / 17 (Robolectric test JVM on 21) | AGP 9 requires separate `:androidApp`; KMP modules use `com.android.kotlin.multiplatform.library` [V] |
| CMP / CMP Material3 | 1.13.0-beta01 / 1.13.0-beta01 (fallback 1.12.2 / 1.12.0-alpha03, demo-proven) | stable 1.13.0 projected about mid-Dec 2026 [U] |
| Navigation 3 / NavigationEvent / Lifecycle / SavedState / M3 adaptive | 1.2.0 / 1.1.0 / 2.11.0 / 1.4.0 / 1.3.0 | [V] |
| compileSdk / targetSdk / minSdk | 37 (37.1 if Compose 1.13 demands it) / **36 for the switch release** / 26 | targetSdk 37 is its own later slice (local-network permission, CT, `IntentSender` rules) [V, android-surfaces §0.1] |
| Targets in every KMP module | `android`, `jvm("desktop")`; `iosArm64` + `iosSimulatorArm64` only with `-Poc.ios=true` | a Linux CI job compiles common code to iOS klibs nightly [V/U] |

One convention plugin decides targets for every module (`oc.kmp.library`, kmp-cmp-status §12.2), so turning iOS on is a
property flip, not 60 edits.

---

## 4. Module graph

### 4.1 Picture

```
 :androidApp (com.android.application; OS components with today's class names; final Metro graph; Flutter migration run)
   |            \
   |             :android:linux  :android:terminal  :android:background  :android:widgets
   |             :android:media  :android:platform  :android:migration    (plain Android libraries)
   |
 :ui:shell (App() composable, Nav3 NavDisplay, scenes, one status-line slot, theme root)  <- also used by :desktopApp later
   |
 :feature:* (KMP + CMP: screens + ViewModels + entry installers)
   |
 :ui:kit  :ui:kit-chat  :ui:kit-content  :ui:words  :ui:routes  (KMP + CMP; only :ui:kit* may use material3)
   |
 :engine:* (KMP, no Compose: app- and connection-scoped logic, state machines, StateFlow out, suspend + Outcome in)
   |
 :core:domain (gateway interfaces, ServerCapabilities, neutral models)  <-- implemented by -- :protocol:* (KMP)
   |
 :core:data  :core:db  :core:net  :core:markdown  :core:security  :core:model  :core:common      (:core:testing for tests)
```

### 4.2 Every module

"Owner" means the single-owner rule: **S** = one coordinating owner for the module, changes go through them; **F** = one
feature agent owns it end to end.

| Module | Plugin / targets | Holds | Owner |
|---|---|---|---|
| `build-logic` | included build | conventions `oc.kmp.library`, `oc.kmp.compose`, `oc.kmp.feature`, `oc.android.application`, `oc.android.library`, `oc.desktop.application`, `oc.quality`; dependency-rule checks | S |
| `:core:common` | KMP | `Outcome`, sealed `Failure`, `Redacted`, `AppDispatchers`, `Clock`, ids, `AppLog` (Kermit), `Trace` facade, `WireJson` + tolerant serializers | S |
| `:core:security` | KMP | the one `Redactor` (replaces seven scrubbers), `ExternalLinkPolicy`, address rules (loopback, RFC1918, `100.64.0.0/10`, `.ts.net`), consent three-state reads, `PlatformCapabilities` interface | S |
| `:core:model` | KMP | plain data: profiles, messages/parts/tool states, cards, `FormSpec`, schedules | S |
| `:core:domain` | KMP | `ServerGateway`, `ServerOperationsGateway`, `ServerCapabilities` (85 flags), value types, pure domain rules from `lib/domain` (PROTOCOLS-43) | S |
| `:core:data` | KMP | `KeyValueStore`, `SecretStore`, `BlobStore`, `ProfileScope`, `WriterLane`; Android actuals = adopted `FlutterSharedPreferences` + Keystore AES-GCM; desktop actuals later | S |
| `:core:db` | KMP + KSP | Room 3 `oc.db` (offline queue, drafts index, stash, receipts, caches; every table has `profile_id`) | S |
| `:core:net` | KMP | Ktor client factory, OkHttp hardening, leaf-DER pin trust manager, cleartext consent, SSE parser + event-stream loop, WebSocket helpers, resumable pinned downloader | S |
| `:core:markdown` | KMP | streaming Markdown model over `org.jetbrains:markdown` 0.7.18, blocks, path chips, agent blocks, `proseForSpeech` | F |
| `:core:testing` | KMP (test only) | fakes, `TestClock`, `TestDispatchers`, MockEngine fixtures | S |
| `:protocol:oc2` | KMP | OpenCode 2 client: dialect table, routes, DTOs, 91-event union, adapter, gateway | F (Codex) |
| `:protocol:paseo` | KMP | Paseo WebSocket transport, gateway, mappers, chat feed | F (Codex) |
| `:protocol:codex` | KMP | Codex app-server JSON-RPC | F (Codex) |
| `:protocol:team` | KMP | Gas City supervisor client + phone-engine `/v1` client (later in scope) | F (Codex) |
| `:engine:connection` | KMP | `ConnectionRegistry`, per-connection `ConnectionGraph`, session/status/lifecycle/quiet reconnect, location, session list, selection | S |
| `:engine:transcript` | KMP | event apply, version fences, settle/fold passes, 50 ms publish, run lifecycle, turn stall | S |
| `:engine:delivery` | KMP | send pipeline, steer/inbox, offline queue + dispatch markers, command receipts, stop | F |
| `:engine:composer` | KMP | drafts, attachment vault, stash, phrase chips, prompt history | F |
| `:engine:requests` | KMP | approvals, permission policy, questions, one `FormSpec` engine, agent cards (codec, validator, receipts, recovery) | F |
| `:engine:profiles` | KMP | profile store, trust, pins, sign-in reset, deletion transaction | S |
| `:engine:attention` | KMP | attention feed, profile monitor, notification policy, `StatusModel`, alert copy, widget snapshot + policies, read state, the one status-line combiner | F |
| `:engine:schedule` | KMP | recurrence, CAS store, claims, receipts, runner (one implementation replaces Dart + Kotlin) | F |
| `:engine:agents` | KMP | phone agents rows, catalog, merged feed, handoff, auto-approval, sign-in model | F |
| `:engine:team` | KMP | AI Team domain and controllers (later in scope) | F |
| `:engine:runtime` | KMP | `RuntimeHost` interface + portable built-in Linux rules (budget, backoff, idle, setup protocol, component registry, manager parsers, process grouping over `ProcEntry`, thermal policy), shell scripts as resources | S |
| `:engine:media` | KMP | voice logic, model packs + RAM gate, auto-send policy, read-aloud scope, MIME, office text, SVG sanitizer, PDF budgets, Wi-Fi share rules; platform interfaces | F |
| `:engine:project` | KMP | files, git page, terminals model, dev preview rules, staged revert, review | F |
| `:engine:settings` | KMP | preferences, appearance/effects, settings scope model, teaching/tips/nudges registry, locale | F |
| `:engine:diagnostics` | KMP | ring buffer, problem report, crash categories, exit classification, `PerfTrace`, app updates state | F |
| `:engine:entry` | KMP | bootstrap order, `EntryRouter` (one pending-intent mechanism instead of seven) | S |
| `:ui:kit` | KMP + CMP | tokens, five Styles + Fine-tune, motion, haptics/press/back seams, text-scale cap, layout classes + max widths, frames, sheets, rows, inputs, states, teaching parts | S |
| `:ui:kit-chat` | KMP + CMP | turn, message, work line, tool rows, Markdown blocks, stream reveal, composer parts, request/answer cards, agent cards | S |
| `:ui:kit-content` | KMP + CMP | code block + tokenizer seam, diff view, viewer, images, QR, terminal view, charts, web preview seam | S |
| `:ui:words` | KMP + CMP resources | plain-words tables (`Failure` → string), all copy in 7 languages, generated from ARB | S |
| `:ui:routes` | KMP | every `@Serializable` route key, one file per feature (frozen from the decided IA) | S |
| `:ui:shell` | KMP + CMP | `App()`, Nav3 display, scenes (rail, sidebar, two/three pane), `AppNavigator` impl, status-line slot, theme root | S |
| `:ui:testing` | KMP (test) | compose test rules, Roborazzi config, capacity/stress helpers | S |
| `:feature:onboarding` | KMP + CMP | bootstrap gate, root decision, first run, managed move | F |
| `:feature:chats` | KMP + CMP | chats home, inbox, all conversations, filters, new conversation, import | F |
| `:feature:chat` | KMP + CMP | chat frame, transcript list, scroll, top bar, status, find, timeline, message actions | F (single owner, like today) |
| `:feature:chat-composer` | KMP + CMP | composer, chips, tray, attachments, slash/mention, voice pill | F |
| `:feature:chat-pages` | KMP + CMP | session menu pages: review/revert, context, export, notes, relations, approvals sheet | F |
| `:feature:servers` | KMP + CMP | servers, editor, pairing, connection details, switcher, server page | F |
| `:feature:phone` | KMP + CMP | This phone, setup, Linux manager, local terminal, keep running (capability-gated) | F |
| `:feature:agents` | KMP + CMP | agents hub, agent sheet, sign-in, account, external agents | F |
| `:feature:project` | KMP + CMP | Files hub, browser, viewer, review/diff, git sheet, server terminal, dev preview, project tools, Wi-Fi share | F |
| `:feature:settings` | KMP + CMP | settings hub + groups + search, appearance, notifications, voice, providers/MCP, tools | F |
| `:feature:usage` / `:feature:schedules` | KMP + CMP | usage hub + widget settings / scheduled prompts | F |
| `:feature:about` | KMP + CMP | report a problem, diagnostics, about, guide, tips, updates, manage-space page | F |
| `:feature:search` | KMP + CMP | command launcher, Go to, settings search | F |
| `:feature:widget-panels` | KMP + CMP | overlay panels (style, project, steer, voice) hosted by a native activity | F |
| `:feature:team`, `:feature:factsbook` | KMP + CMP | later in scope | F |
| `:androidApp` | `com.android.application` | id and namespace `io.github.eslamasabry.opencode_mobile`; every OS-visible class with today's FQCN (activities, services, receivers, providers, tiles, workers, AppFunctions service); `Application`; final graph | S |
| `:android:linux` | `com.android.library` | `BuiltinLinux` split, proot launcher, ownership proofs, recovery, setup runner, agent host, phone engine bridge, `jniLibs` (proot, loader, talloc, shmem, `libaiteam_*`), `RuntimeHost` actual | S |
| `:android:terminal` | `com.android.library` | PTY (Termux `terminal-emulator` v0.118.3, `PtyAccess.java`), terminal session actual | F |
| `:android:background` | `com.android.library` | notification channels, status row 4747, alerts, Live Updates, FGS hosts (reply watch, live mode), WorkManager/alarms, tiles logic | F |
| `:android:widgets` | `com.android.library` | RemoteViews binders, layouts, size policy, `previewImage` PNGs, widget actions host | F |
| `:android:media` | `com.android.library` | sherpa-onnx (vendored AAR), `AudioRecord`, `TextToSpeech`, CameraX + zxing-cpp, isolated `PdfRenderer` service | F |
| `:android:platform` | `com.android.library` | network, thermal, lifecycle/exit info, crash store, storage, project export, BYO signer, Tailscale handoff, PackageInstaller updater, AppFunctions impl | F |
| `:android:migration` | `com.android.library` | Flutter data migrator (prefs adoption, secure-storage v11 decrypt, Room import, backups) | S |
| `:android:benchmark` | `com.android.test` | Macrobenchmark, Baseline and Startup Profile generator | F |
| `:quality` | JVM | Konsist rules, Metro graph smoke test, catalog-coverage ratchet, Room schema `profile_id` check | S |
| `:desktopApp` | `oc.desktop.application` | a compiling stub from day 1 (window + `App()` with fakes = the kit gallery); product work later | S |

That is about 60 modules at parity. Phase 1 creates about 35; the rest appear when their first slice starts.

**Why OS-visible classes stay in `:androidApp`:** the launcher, Quick Settings, WorkManager's database and pinned shortcuts
store component class names [R, android-platform §0.2, §3]. Keeping every such class as a thin shell in one module with its
exact FQCN (for example `io.github.eslamasabry.opencode_mobile.SessionsWideWidgetProvider`) makes "names are contracts" a
one-module review rule. The shells delegate to the library modules.

**Why `:ui:routes` is one module and not `:feature:x:api` per feature:** the IA is decided (`docs/ux-system/target-ia.md`) and
the route table is known (screens-nav §3.1). One small module, one file per feature, frozen in Phase 1, saves ~15 modules.
Features still register their own serializers and entries through Metro, so nobody edits a central list to add a screen.

---

## 5. Layering and dependency rules

| From | May depend on | Must not depend on |
|---|---|---|
| `:feature:*` | `:ui:kit*`, `:ui:words`, `:ui:routes`, `:engine:*` (public API), `:core:domain`, `:core:model`, `:core:common` | `:protocol:*`, `:core:net`, `:core:data`/`:core:db` internals, other features, `material3` |
| `:ui:kit*` | `:core:common`, `:core:security` (redaction for Details folds), `:core:markdown` (`kit-chat` only), `:core:model` (card/form shapes only) | engines, protocols, features |
| `:engine:*` | `:core:*`, other engines' public API (acyclic) | `:ui:*`, `:feature:*`, `:protocol:*` (gateways arrive through DI interfaces) |
| `:protocol:*` | `:core:domain`, `:core:net`, `:core:model`, `:core:common`, `:core:security` | engines, UI |
| `:android:*` | any `:core:*`/`:engine:*` interface they implement | `:feature:*` (except `:android:widgets` hosting `:feature:widget-panels` through `:androidApp`) |
| `:androidApp` | everything | nothing depends on it |

Enforcement, cheapest first:

1. **Gradle**: the convention plugins check declared project dependencies against this table and fail configuration
   (about 30 lines in `build-logic`).
2. **Compiler**: no `android.*` in `commonMain`; `explicitApi()` in `core`, `protocol` and `engine` modules.
3. **Konsist 0.18.1** tests in `:quality`: no `androidx.compose.material3` import outside `:ui:kit*`; no raw
   `tween`/`spring`/`performHapticFeedback`/`ModalBottomSheet`/`Dialog` outside the kit; no `runCatching` in suspend code
   paths; no `Dispatchers.*` or `Clock.System` outside `:core:common`; no `Log.*`; no `openUri`/`Intent.ACTION_VIEW` outside
   the external-link handler; every `@Serializable` route registered; ViewModels have no platform types [V Konsist exists;
   rule wording is this proposal's].
4. **Tests**: Room schema test (every table has `profile_id` or is on an allowlist), graph smoke test, catalog ratchet.

This is AGENTS.md's "UI talks to the domain gateway only" and "gate features on `ServerCapabilities`, never on the flavor
enum" turned into build failures.

---

## 6. Dependency injection

**Metro 1.5.x** (compile-time, Kotlin compiler plugin, KMP) [V, architecture-stacks §3]. Plan B: Koin 4.2.2 with its
compiler plugin; injected classes would not change.

- **Scopes.**
  - `AppScope`: stores, `Redactor`, diagnostics, `ConnectionRegistry`, `AttentionFeed`, `NotificationPolicy`,
    `ScheduleEngine`, `RuntimeHost` (Android), status-line combiner, update notice, teaching registry.
  - `ConnectionScope` (a Metro graph extension, one per **live** connection; several at once): the gateway pair for the
    profile's backend, event stream, session list, selection, approvals, prompt delivery, read state, caches, composer
    drafts. `close()` = stop admission → cancel stream → drain writer lanes → release.
  - Screen ViewModels per Nav3 entry. They hold a reference to the right `ConnectionGraph`, never a protocol type.
- **The conversation's own backend** (Flutter's `ConversationBackendScope`) becomes
  `ConnectionRegistry.graphFor(conversationRef)`. A chat about Claude Code on the phone gets the Paseo connection's engines,
  so models, approvals and the connection line are always its own.
- **Contributions instead of central lists.** Protocols contribute gateway factories by backend key
  (`@ContributesIntoMap`); features contribute Nav3 entry installers (`@ContributesIntoSet`) and ViewModels
  (`@ViewModelKey`). No agent edits a shared wiring file to add a feature.
- **Android components** (services, receivers, workers, widget providers, tiles) reach the graph through
  `(applicationContext as GraphOwner).appGraph`. `metrox-android` is not used because it needs API 28 and minSdk is 26 [V].
- **Desktop** creates the graph in `main()`; **iOS** in the app delegate. The final `@DependencyGraph` lives in each
  platform's app module, because platform code contributes bindings [V, Metro multiplatform docs].
- **Tests never use the container.** Classes are built directly with fakes in `commonTest`. One graph smoke test in
  `:quality` resolves the release graph and every multibinding key on the JVM.
- **Risk control:** Kotlin and Metro are pinned as a pair in the version catalog; experimental Metro features stay off.

---

## 7. State management

### 7.1 Two kinds of state holder, one idiom

1. **Engines** (app or connection scope) own the truth, timers, streams and state machines. They expose `StateFlow`
   (state) and rarely `Flow` (facts), and accept `suspend` commands returning `Outcome`.
2. **ViewModels** (one per screen, KMP Lifecycle 2.11.0) `combine` engine flows into one `StateFlow<XUi>` and turn taps
   into engine calls. No business rules: a rule a widget or notification also needs belongs in an engine.

Rules for every agent:

- One `UiState` per screen, exposed with Kotlin 2.4 explicit backing fields (`val state: StateFlow<ChatUi>` /
  `field = MutableStateFlow(...)`).
- No one-shot event channels from ViewModel to UI. "Show a snackbar" or "navigate after create" is a state field the UI
  acknowledges.
- **No strings in state.** State carries ids, enums and `Failure` categories; `:ui:words` turns them into copy. ViewModels
  stay locale-free and testable, and Arabic plurals live in one place.
- Collect with `collectAsStateWithLifecycle()`. Kit parts take plain parameters and hoisted state; they never see a
  ViewModel.
- **Explicit state machines** (sealed state + pure `reduce(state, event): Transition(state, effects)`) for send / stop /
  steer, delivery (queue, markers, receipts), approvals, connection status, setup steps, the phone runtime and card
  receipts. Reducers are table-tested in `commonTest` with no coroutines; screens-nav §11.1 asks for exactly this.
- No Circuit, Molecule, Decompose or MVI framework. Fewer idioms for mostly-Sonnet agents.

### 7.2 The streaming hot path

From rendering-chat and architecture-stacks §4.3, combined:

- `TranscriptEngine` applies each event synchronously on its serial dispatcher to a mutable model (a `StringBuilder` per
  live text part, version fences per message/part), then publishes an immutable `TranscriptState` at most once per 50 ms
  (first delta flushes at once), with `PersistentList` turns so unchanged history is shared.
- The transcript is one `LazyColumn(reverseLayout = true)` flattened to block items (prompt, work line, each Markdown
  block, code, table, pictures, notice, live line), stable keys, `contentType` per kind, `requestScrollToItem(0)` while
  following.
- Markdown re-parses only the unstable tail (`StreamingMarkdownFile`). Words fade in with a draw-phase mask, never
  per-frame `AnnotatedString` spans. The live block grows on the `replyGrow` spring (stiffness 700, critical damping).
  `LineBreak.Simple`. `preferredFrameRate(High)` only while revealing.
- Measured, not felt: port `perf_chat_test.dart` (1,000/5,000 parts) as publish-count tests, add a Macrobenchmark stream
  journey, and compare frame timing with the Flutter build on the same journey (testing-perf §12.5).

All of this is `commonMain`; only `preferredFrameRate` needs a check that the JetBrains fork exports it [U].

### 7.3 Persistence and process death

- Drafts, queue entries and recovery markers are persisted explicitly, as today. `SavedStateHandle` is not relied on for
  anything the person would lose.
- Stores return only after the platform write is acknowledged (`commit()`, Room transaction). A refused write rereads and
  reports, as in the Flutter fail-closed stores (state-data §1.3).

---

## 8. Navigation

- **Navigation 3 1.2.0** (multiplatform `org.jetbrains.androidx.navigation3:navigation3-ui`) with a user-owned back stack
  [V]. `rememberNavBackStack(SavedStateConfiguration, start)` with a `SerializersModule` that registers every route,
  because non-JVM targets have no reflection [V].
- **`AppNavigator`** is the only navigation API ViewModels see: `open(route)`, `openChat(ref, focusComposer)` (does not
  stack a chat that is already showing), `land()` (clear to Chats), `replaceRoot(route)`, `back()`. Tests use a recording
  fake.
- **`EntryRouter`** (in `:engine:entry`) replaces the seven copies of the Flutter pending-intent mechanism: launcher
  shortcut, deep link, share-in, notification tap, widget, tile, scheduled-run tap. Each becomes an `EntryRequest` that waits
  for bootstrap and migration, is consumed exactly once, and becomes `AppNavigator` calls. Pure logic, tested in
  `commonTest`. Nav3 1.2 deep-link matching handles `opencode-mobile://session` and `://team`.
- **Scenes:** compact = single pane, no bottom bar (owner 2026-10-20); medium = rail; expanded/large = sidebar; Settings,
  Files and Team projects use list-detail scenes. The window-class function is ported once from `KitLayout`
  (600/840/1200, short height < 480).
- **Sheets and dialogs are screen state, not routes**, like `showKitSheet` today. The kit sheet owns Back while it is
  visible.
- **Predictive back policy, decided once in `:ui:shell` + `:ui:kit`:**
  1. list → chat: container transform on tap and on button-back; while a back gesture is in progress, shared bounds are
     off and the Material predictive preview runs (option A, stable APIs, no 700 ms latch) [V/U, motion-native §4.4];
  2. settings pages: shared-axis X with system predictive back;
  3. full-screen dialogs (new conversation): slide up, the gesture closes;
  4. reduced motion: cross-fade or none.
  A one-agent spike tries the deferred two-stage transition (Compose 1.13 + Nav3 1.3-alpha) and switches when CMP
  publishes it stable (§19, motion-native §4.4 B).
- **The chat pops only when nothing is lost**: `NavigationBackHandler` on the chat entry is enabled while the composer
  differs from the saved draft (screens-nav §3.2).
- **Root back is never intercepted.** Intercepting Back on the root breaks Android's back-to-home animation
  [V, motion-native §0.5]. That means dropping Flutter's "Press back again to exit" guard. Owner decision (§20), and a
  "judge by what it removes" win.

---

## 9. Threading

- **Each engine is confined to one serial dispatcher** (`default.limitedParallelism(1)`, injected). The Dart code assumed
  one thread; serial confinement keeps that assumption true without locks.
- Heavy pure work (decoding 100-message pages, merging 5,000 parts, full Markdown parse) runs with
  `withContext(dispatchers.default)` and returns a value.
- **Writer lanes** (per profile): an admission flag that `close()` flips synchronously, plus a fair `Mutex` so "drain"
  means every admitted write finished. A serial dispatcher alone is not enough, because a write that suspends on disk I/O
  frees it (architecture-stacks §8.3).
- **Scopes replace generation counters.** Each connection generation is a child scope of the `ConnectionGraph`; switching
  profile or reconnecting cancels it. Prompt cancellation means code after an `await` in a dead generation never runs.
  Identity re-checks stay only for writes into stores that outlive the generation.
- **Flow conventions:** state is `StateFlow`; server events are a cold `channelFlow` per connection; durable events are
  never dropped (unbounded or suspending buffer); volatile deltas may be coalesced; after reconnect, reconcile by refetch,
  never replay (AGENTS.md). Every `buffer`/`conflate`/`debounce` carries a comment naming the catalog row it implements.
- **Platform main dispatchers:** Android `Main.immediate`; desktop needs `kotlinx-coroutines-swing`; iOS uses the main
  queue. ViewModels never mutate engine state directly.
- **Android lifetime:** engines live in the process; Android decides how long the process lives. A `dataSync` "watching
  this reply" service starts when the app goes to the background with a turn running and stops at settle (the demo's
  `ReplyService`, android-surfaces §3.2). The phone server stays `specialUse`. Engines persist progress markers before
  side effects and never assume unbounded lifetime.

---

## 10. Error model

- **Expected failures are values, bugs are exceptions.** Engines and protocols return `Outcome.Ok` or
  `Outcome.Failed(failure)`. Exceptions mean a programming error or cancellation.
- **Translate once, at the boundary.** Ktor and OkHttp exceptions, `SerializationException`, HTTP statuses, Keystore errors
  and proot/Termux failures are mapped to `Failure` inside `:protocol:*`, `:core:net`, `:core:data` and `:android:*`.
  Nothing above `engine` sees a transport exception type.
- **`Failure` is a closed sealed hierarchy** ported from `ProductFailure` (offline, timed out, certificate, sign-in
  refused, not found, conflict, server did not answer, refused(reason), authored(app copy), unexpected(details), and the
  rest of the Dart category list). `:ui:words` maps it with an exhaustive `when`, so a new category cannot ship without
  words.
- **No path from server or exception error text to a `Text`.** (Transcript content is content; error copy is always
  app-authored.) `details` is `Redacted`, a value class whose only constructor runs the
  `Redactor`. The kit's Details fold is the only place it renders, left-to-right and copyable.
- **Wire safety:** `WireJson` sets `exceptionsWithDebugInfo = false` so provider keys from `/config/providers`-style bodies
  never land in exception text [V, network-data §4.2]; unknown events become `Unknown(type, raw)` and never throw.
- **`runCatching` is banned around suspend calls** (it swallows `CancellationException`); one `attempt {}` helper rethrows
  cancellation.
- **Crashes:** an uncaught-exception handler installed first in `Application.onCreate` writes the category-only crash
  record (keeping `NativeCrashStore` and the consent epoch); every app and connection scope has a `SupervisorJob` and a
  logging `CoroutineExceptionHandler`. **Compose has no `ErrorWidget`**: an exception in composition kills the process.
  So the startup path keeps a crash-loop counter (consecutive crashes within N seconds → a safe mode that opens Report a
  problem and Settings), and screens build their UI models defensively (desktop-web-cross §9.2.3).

---

## 11. Platform seams: how Android stays first and best inside shared UI

Two mechanisms, chosen per case:

- **Interfaces provided through the graph** (preferred: fakeable in `commonTest`), for example
  `ActivityResults { suspend fun pickPhotos(limit): List<Picked>; suspend fun request(p: Permission): PermissionResult }`,
  implemented in `:androidApp` on `MainActivity`'s `ActivityResultRegistry`.
- **`expect`/`actual` or `CompositionLocal` platform parts inside the kit**, for leaf behaviour a composable needs
  (haptic flavour, reduced-motion source, text-scale source).

| Seam | Common API (kit or engine) | Android actual (day 1) | Desktop later | iOS later |
|---|---|---|---|---|
| Haptics | `KitHaptic` enum (send, commit, select, done, longPress) | `LocalHapticFeedback` / `performHapticFeedback` | no-op | UIFeedbackGenerator |
| Press feedback | `KitPress` | platform ripple (AndroidX) | hover + focus ring | highlight/dim |
| Overscroll | kit default factory | stretch (31+) / glow | none | bounce |
| Back | `NavigationBackHandler`, Nav3 | system predictive back | Esc / mouse back | edge swipe |
| Reduced motion + motion level | `MotionPolicy` (Full/Calm/Off, Saver, heat) | animator scale (free) + kit policy | none or gsettings [U] | `isReduceMotionEnabled` |
| Frame-text cap 1.3×, reading text full | `FrameText` provider in kit | clamp keeping nonlinear scaling (check at 200% [U]) | same | same |
| Insets / IME | kit modifiers | edge-to-edge, `imePadding`, `contentReceiver` for keyboard images | n/a | safe area |
| Frost on composer/header (owner 2026-10-30 exception) | `KitFrost` | `RenderEffect` blur on API 31+, solid below and when held | solid | solid or native blur [U] |
| Pickers, camera photo, SAF | `ActivityResults`, FileKit 0.17 | Photo Picker, `TakePicture` + FileProvider, SAF | XDG portal | PHPicker, UIDocumentPicker |
| Permissions | `PermissionGate` | runtime permissions, notification permission, exact alarm | always granted | iOS prompts |
| External links | `ExternalLinkPolicy` + kit `LocalUriHandler` | confirm sheet then `ACTION_VIEW` | `xdg-open` | `UIApplication.open` |
| Share out / share in | `ShareService`, `EntryRouter` | Sharesheet, `SEND` intents | clipboard / none | share sheet / extension |
| WebView (dev preview) | `KitWebPreview` seam | hardened `android.webkit.WebView` via `AndroidView` | open in browser (capability off) | `WKWebView` |
| Terminal | `TerminalModel` interface + kit canvas renderer | Termux `terminal-emulator` + PTY | JVM emulator reuse [U] | needs a KMP VT parser [U] |
| QR scan / draw | `QrScanner` / qrose (common) | CameraX + zxing-cpp | decode a picked image | AVFoundation |
| PDF pages | `PdfRasterizer` | isolated-process `PdfRenderer` (kept) | PDFBox | PDFKit |
| Voice | `AudioCapture`, `SpeechRecognizer`, `SystemTts`, `PcmPlayer`, `AudioSession` | sherpa-onnx, `AudioRecord`, `TextToSpeech`, `AudioTrack` | sherpa JVM jar [U] | sherpa xcframework [U] |
| Background work | `BackgroundHost`, `JobScheduler` | FGS types, WorkManager, AlarmManager | in-process | BGTaskScheduler + push |
| Built-in Linux | `RuntimeHost` | proot (`:android:linux`) | direct process spawn | absent (capability off) |
| Capabilities | `PlatformCapabilities` (the 16 Flutter flags + `isAndroid/isDesktop`) | all Android flags on | Android-only flags off | remote-only |

Rules that keep Android first:

1. **Desktop compiles; desktop never blocks.** A missing desktop behaviour is an `actual` that reports the capability as
   off. PRs are judged on Android behaviour plus "desktop compiles and common tests pass".
2. **Android-only screens are still common code**, gated by `PlatformCapabilities` and backed by engine interfaces (the
   Linux manager reads `RuntimeHost`, not proot). On desktop these screens later describe local servers on the computer,
   which the builtin-linux map already proposes (§12 "desktopMain: a `RuntimeHost` that runs the same scripts").
3. **Native Android surfaces stay native:** RemoteViews widgets, notifications, Live Updates, tiles, shortcuts,
   AppFunctions and foreground services are plain Android code in `:android:*`. Only the overlay panels and the manage-space
   page are CMP screens hosted by native activities.
4. **When CMP lags**, an Android-only improvement may go in `androidMain` of the kit behind the same kit API (for example a
   newer Jetpack transition), with a common fallback.

---

## 12. Reusing the 38,441 lines of existing Kotlin

Counted in `oc_app-batch1/android` [R]: 25,465 lines in 168 main files, 7,977 lines of JVM tests (46 classes, 645 `@Test`),
4,999 lines of instrumentation. 36 main files import `io.flutter`. 70 main files import nothing from `android.*`
(about 5.9k lines): 37 of them also use no `java.*`/`org.json` imports (about 2.8k lines), 33 do (about 3.0k lines).

| Group (map reference) | Lines (approx.) | Destination | Action |
|---|---|---|---|
| Pure policy with no Android or JVM imports (recovery budget, backoff, idle policy, lease registry, setup protocol, process-tree/orphan/list logic over parsed rows, widget/status policies, schedule policy) | ~2.8k | `:engine:runtime`, `:engine:attention`, `:engine:schedule` `commonMain` | **move as-is**; tests move to `commonTest` with `kotlin.test` |
| Android-free but JVM-using (`java.io`, `org.json`) | ~3.0k | same engines | **convert** `org.json` → kotlinx.serialization `JsonElement`, `java.io.File` → kotlinx-io, keeping behaviour; tests first. Rationale: Kotlin does not support a shared source set for Android + JVM only [V, multiplatform-hierarchy page dated 2026-10-01], so the alternatives are this conversion or leaving the file in an Android module. Leave it in Android when the code reads `/proc` or runs processes. |
| `BuiltinLinux.kt` (4,511) + ~60 `Native*`/`Setup*`/`Phone*`/`PaseoStaleLock`/`WorkLeases` helpers (~9k) | ~13.5k | `:android:linux` | **keep logic, split** into RuntimeInstall, ProotLauncher, ServiceRegistry, InstallerSlot, ServerRecovery, IdleController, AgentHostBridge, PhoneEngineBridge (builtin-linux §7); remove `flutterPreferences` reads only after their replacements exist; never "simplify" `NativeRuntimeOwnership.plan` |
| Services, receivers, tiles, status notification, coding alerts, pause stores (~2.9k) | ~2.9k | shells in `:androidApp`, logic in `:android:background` | **as-is minus channel glue**; Dart-written wording (`buildNotificationStatus`) moves to `:engine:attention` |
| Widgets (~4.3k) | ~4.3k | provider shells in `:androidApp`, binders/layouts in `:android:widgets` | **as-is**; keep layouts, `previewImage`, render-once size policy; `WidgetActionRuntime`'s headless Flutter engine is replaced by a coroutine against the shared gateway |
| Scheduled prompts (~1.3k) | ~1.3k | `:engine:schedule` (logic) + `:android:background` (alarms, worker) | **merge** with the Dart `ScheduledPromptRunner` into one implementation; `ScheduledPromptRuntime`'s headless engine goes |
| Launch/OS bridges (~3.8k): `MainActivity`, media, PDF, read aloud, network, thermal, lifecycle, crash store, export, signer, Tailscale | ~3.8k | `:androidApp`, `:android:platform`, `:android:media` | `MainActivity` becomes a `ComponentActivity` with `setContent { App(graph) }`; bridges become interface actuals |
| Terminal (`LocalTerminal`, `PtyAccess.java`) | — | `:android:terminal` | keep; JitPack pin verified on the VPS |
| Termux (8 files, ~0.8k) | ~0.8k | `:android:linux` or deleted | owner decision Q1 |
| Channel glue: `MainActivity.handleBuiltinLinux`, `NativeChannelReplies`, `MainActivityBackgroundChannel`, `GeneratedPluginRegistrant`, Flutter activities | — | — | **delete** |
| JVM tests (46 classes) and instrumentation (13 classes) | 12.9k | next to their code | **move first, green on day 1**; they encode device-found fixes |

**Why reuse is unusually safe here:** the migration adopts `FlutterSharedPreferences.xml` in place with the same
encodings (ints as Long, lists as the `…Qu!` JSON prefix, doubles as the prefix string) [V, network-data §8.1]. Every Kotlin
reader of `flutter.oc.*` keys (widgets, tile, scheduler, notification locale, Linux recovery gates) keeps working without a
change, which removes the highest-consequence risk in builtin-linux §12 (silent loss of restore authority). Moving a key
out of that file later is an explicit slice that changes writer and readers in one change.

The Dart half that must be written in Kotlin is larger than the reused part: `lib/builtin` (25.2k lines), the Dart parts of
`lib/background`, `lib/scheduling`, `lib/platform`, the Paseo/Codex clients, and the shell scripts embedded in Dart (moved
verbatim to resources with checksum tests).

---

## 13. How each subsystem lands

Feature counts are the maps' ids (700 in total). "Common" = `commonMain`.

### 13.1 Protocols (46 features, `map/protocols.md`)

- `:protocol:oc2` common: dialect table (beta vs stable), route table, tolerant DTOs ported from `lib/api2/models*`, the
  91-event union via an explicit `type → KSerializer` registry with `Unknown` fallback, the event adapter rules
  (PROTOCOLS-19/20), sessions, messages, prompt, permission policy, forms, inbox, PTY over WebSocket.
- `:core:net`: our own SSE loop (1 s doubling to 16 s, reset after 30 s healthy, "disconnected" after 6 tries while still
  retrying, refetch on every connect), 16 MiB frame cap; OkHttp with `retryOnConnectionFailure(false)`, no redirects, no
  proxy, leaf-DER pinning, cleartext consent interceptor [V, network-data §0].
- No OpenAPI codegen; a JVM drift test checks the route table against the committed `/openapi.json` of the pinned server.
- `:core:domain` gets the gateway interfaces, `ServerCapabilities` and value types verbatim. Capability flags that latch off
  at runtime (archive, notes, import/export) stay runtime facts.
- Tests first: the 127 PROTO catalog rows as MockEngine replays of `test/fixtures/api2/*` and new recordings of 2.1.1.
- Shared on desktop and iOS: all of it. iOS needs the Darwin engine and `handleChallenge` pinning [U].

### 13.2 State and data (55 features, `map/state-data.md`)

- `KeyValueStore` (adopted `FlutterSharedPreferences` on Android), `SecretStore` (Keystore AES-256-GCM with the entry name as
  AAD; never `security-crypto`), Room 3 `oc.db` for big blobs (offline queue up to 60 MB today, drafts, stash, receipts,
  caches), files unchanged.
- The Flutter `ConnectionController` (54 part files) splits by catalog id into `:engine:connection`, `:engine:delivery`,
  `:engine:composer`, `:engine:requests`, `:engine:attention`, `:engine:profiles` (state-data §14).
- `ProfileScope` keeps the `oc.<what>.<profileId>` sweep for prefs and adds a tested per-table registry for Room. Deletion
  stays a transaction run backwards.
- Lenient parsing that mirrors each Dart `fromJson` (strict models would brick old data); three-state reads preserved.
- Shared on desktop (DataStore/JSON under `$XDG_DATA_HOME`, Secret Service) and iOS (NSUserDefaults/Keychain).

### 13.3 Agents and backends (63 features, `map/agents-backends.md`)

- P0 daily path first: Claude Code on the phone through the Paseo helper (install, sign in, chat, permissions, steering,
  resume, finished alerts). `:protocol:paseo` and `:engine:agents` are common; the host side (`PhoneAgentHost`, sign-in,
  checks, auth probe, leases) is reused Kotlin in `:android:linux`.
- Codex direct in `:protocol:codex`; ACP later; A2A later.
- AI Team: `:protocol:team` + `:engine:team` + `:feature:team` (17.3k Dart lines of Team UI) are later in scope; the Rust
  engine stays as `libaiteam_engine.so`, but its OpenCode 2.0.29 pin must be reconciled with the app's 2.1.1 first
  (agents-backends §9.3).
- Owner fork to settle before porting 08–30: Paseo-on-phone vs the OpenCode 2 Claude Agent provider.
- Desktop: the same Paseo client talks to a daemon on the computer; iOS: Paseo on a PC over Tailscale only.

### 13.4 Built-in Linux (64 features, `map/builtin-linux.md`)

- Android-only in implementation, portable in rules. `:engine:runtime` holds the rules and `RuntimeHost`; `:android:linux`
  holds proot, ownership proofs, recovery, setup runner, services and `jniLibs` (`useLegacyPackaging = true`, 16 KB
  aligned).
- Order (builtin-linux §12): move the pure Kotlin + JVM tests green → scripts to resources with golden tests → replace the
  channel with a typed interface and rerun the instrumentation acceptances → port the Dart policy (server start/probe,
  recovery consumer, healing, idle, setup engine, thermal, reply watch, Linux manager) → migration with the restore proof →
  terminal, agent host, AI Team seam.
- New day-1 item: classify Android 17 `REASON_OTHER` + `MemoryLimiter:AnonSwap` as its own exit (android-surfaces §5).
- Desktop later: `RuntimeHost` spawns the same scripts directly, no proot. iOS: absent.

### 13.5 UI kit (58 features, `map/ui-kit.md`)

- The kit family is the owner's design system in code: tokens (colour roles + contrast guard, 31 packs, accent, Material
  You, Saver), five Styles (Solid default, Soft, Brutal, Paper, Signal) + Fine-tune, motion (`MotionScheme.standard()`,
  never expressive; five durations; Full/Calm/Off), max widths per part, the 1.3× frame-text cap, teaching parts.
- The 111 frozen API docs in `docs/ux-system/kit-api/*.md` and `kit-v2.md` are the contract each Compose part implements;
  the capacity contract (`kit_capacity.json`, 637 stress cases) becomes desktop-JVM capacity tests.
- Nothing red (one owner-approved Signal exception); no glass except the composer/header frost; flat lists with dividers.
- Three modules so three owners can work in parallel: `:ui:kit` (foundation and general parts), `:ui:kit-chat`,
  `:ui:kit-content`.
- Later-able to save effort: the 20 live-background looks and the illustration scenes.

### 13.6 Screens and navigation (112 features, `map/screens-nav.md`)

- Features map to the 15 `:feature:*` modules (§4.2). Nav3 + `AppNavigator` + `EntryRouter` + scenes (§8).
- Bootstrap: `MainActivity` draws the kit "Opening…" state on the first frame while the graph, migration and stores load;
  after 8 s it offers Try again; failure offers Report a problem and Start fresh (SCREENS-NAV-01).
- Stable test tags equal today's `ValueKey` strings (`chats-new-chat`, `server-switcher-button`, …) so Flutter widget
  tests port mechanically later.
- Desktop shortcuts, command launcher and context menus are common and also serve Android hardware keyboards.

### 13.7 Chat and generative UI (81 features, `map/chat-genui.md`)

- Three chat feature modules with frozen interfaces between them (`ChatUi` state, `ComposerState`, `ComposerHost`), each
  with one owner. The domain logic and its ~124 catalog behaviours are ported first (transcript model, settle/fold/merge,
  turn stall, run result, search, phrase chips, queue rules, card codec/state/answers).
- Requests v1 (permission, question, one `FormSpec` engine with the OC2 adapter honouring `hidden` and `when`) ship with
  the first chat slice; agent cards later, off by default, Claude-Code-qualified only (genui §9.3).
- Card contract unchanged; the 182 KB accept/reject corpus runs against the Kotlin validator in `commonTest`.
- Everything here is common except the dev-preview WebView and the voice/photo seams.

### 13.8 Media and voice (50 features, `map/media-voice.md`)

- Pure logic (silence detector, chunker, language, model catalog + RAM gate, auto-send policy, MIME, office text, SVG
  validator, pairing parser, attachment limits) in `:engine:media` common, test-first.
- `:android:media`: sherpa-onnx vendored AAR with R8 keep rules, `AudioRecord` 16 kHz, `TextToSpeech`, Kokoro to
  `AudioTrack`, CameraX + zxing-cpp (no ML Kit), the isolated `PdfRenderer` service.
- Coil 3, Telephoto `zoomable`, FileKit and qrose are KMP, so viewers and pickers are common kit parts.
- Voice models stay in `filesDir/voice_models/` (no re-download); the pending-photo record keeps its crash-proof order.

### 13.9 Android platform (65 features, `map/android-platform.md`)

- Component names, channel ids, notification id 4747 and WorkManager worker names are unchanged.
- One status row with many owners; Live Updates only for a run the person started (androidx.core 1.19.1).
- The scheduled-prompt engine has one implementation (claims `id:revision:dueAtMs`, receipts, phone-server lease, ≤ 8 min
  worker) in `:engine:schedule`, golden-tested across DST.
- Widget actions run a coroutine against the shared gateway with the 30 s lease and claim ledger kept.
- The in-app updater (GitHub `releases/latest`, `SHA256SUMS`, same-signer check, `PackageInstaller`) ships in release 1
  (app-updates-two-layers); `REQUEST_INSTALL_PACKAGES` added.
- Native copy in all 7 languages from the shared ARB source closes today's en/ar-only gap (T16).

### 13.10 Packages and build (41 features, `map/packages-build.md`)

- One Gradle build with a version catalog and `build-logic`; per-host `gradle.properties` (PC: 2 workers, 2 GB; VPS: more).
- ABI splits (arm64-v8a, x86_64, universal) with **one** versionCode; size gate on the arm64 APK (estimate 17–20 MB
  against ~36 MB today [U]).
- R8 with the AGP 9.3 `optimization {}` DSL, consumer keep rules published from KMP modules, `analyzeReleaseR8Config` at
  every integration checkpoint; the engine-manifest hash check and stripped-symbol exceptions for `libaiteam_*` kept.
- Release plumbing keeps asset names, tag shape and notes paths, or changes the dozen scripts in the same change.
- The ARB → CMP resources + Android `res` converter is written once; ICU `select` (6 messages) flattened into code.

### 13.11 Desktop, web and cross-cutting (65 features, `map/desktop-web-cross.md`)

- Day 1 (Android): `PlatformCapabilities`, `ExternalLinkPolicy`, the one `Redactor` with every vector suite ported first,
  diagnostics (ring, problem report, crash categories, exit history, `PerfTrace`), no analytics and no phoning home, the
  accessibility contract (labels, 48 dp targets, 2× text, live regions through one `Announcer`), manifest posture.
- Later: desktop window state, file drop, keyring, packaging, release check (PORT-LATER items). Web: dropped.
- PRIVACY.md must be rewritten before the first native release (the code no longer matches it).

---

## 14. Day-1 in-place update

The migration is independent of the UI choice, but A changes nothing about it, and two decisions are urgent.

1. **Signer.** The brief names `1DE5BF08…D60C` (the owner's local key). The packages-build map says every GitHub release
   (v2.0.0+56, v2.1.0+57) is signed by `842284B2…1C` and AGENTS.md names a third CI key [R, packages-build §0.3]. These
   cannot update each other. Read the installed signer on the owner's phone (`dumpsys package`, read-only adb is allowed)
   and decide which population the switch APK updates.
2. **versionCode.** Local builds on the owner's phone are in the 4300–4511 range. The first native build needs a new base
   above every installed number (suggest 10000+) [R].
3. **`:android:migration` runs before any engine starts**, as a step of the bootstrap gate:
   marker check → raw byte copies of `FlutterSharedPreferences.xml`, `FlutterSecureStorage.xml`,
   `FlutterSecureKeyStorage.xml`, `FlutterSecureStorageConfiguration*.xml` to `files/migration/flutter-v57/` → detect stray
   DataStore/default-prefs files (count keys only) → decrypt secrets natively (RSA-OAEP with MGF1-SHA1 unwrap of the AES-128
   key, AES-GCM values), write to the new `SecretStore`, read back, mark re-entry per profile on failure, never delete the
   source → adopt prefs in place and run the idempotent Dart-side migrations → copy big blobs into Room inside one
   transaction with counts and SHA-256 per blob, leave source keys for one release → files untouched (rootfs, voice models,
   vaults, `oc.teamEngine.<id>/`) [V, network-data §8–9].
4. Dispatch markers, `oc.cleartextOk.*`, `oc.certificatePin.*`, the OC1 upgrade booleans, consent epochs and teaching
   state survive byte for byte.
5. **Proof before any owner APK:** the upgrade journey on a rootable emulator (Flutter 2.1.0 build seeded with widgets,
   tiles, a pinned shortcut, a schedule, a channel customisation, secrets) → `adb install -r` the native APK → Maestro
   flows + file checks → rerun is a no-op → interrupted migration resumes (testing-perf §6.6, android-surfaces §15).
6. Shorebird ends at the switch. Existing Flutter users can only self-update into native if a Flutter release ships the
   installer first; otherwise they download the APK (Obtainium/GitHub) [R, packages-build §9.3].

---

## 15. Adding Linux desktop and iOS later

### 15.1 What already exists by the Android switch under A

- Every KMP module compiles for `jvm("desktop")` and runs `commonTest` on the JVM in CI.
- `:desktopApp` exists as a stub that runs `App()` against fakes (the kit gallery).
- A nightly Linux job compiles common code to iOS klibs, so `commonMain` has no JVM leaks [U on cinterop dependencies].
- Kit seams have desktop/iOS `actual`s that report "off" or no-op.

### 15.2 Linux desktop (estimate 6–10 agent-weeks [U])

| Work | Notes |
|---|---|
| `:desktopApp` shell | window state (`desktop_window_state_v1` semantics), title, icon, menus, tray (buggy on GNOME/Fedora) |
| Desktop actuals | `KeyValueStore` (JSON/DataStore in `$XDG_DATA_HOME/io.github.eslamasabry.opencode_mobile/`), `SecretStore` (Secret Service), notifications (libnotify over D-Bus or `notify-send`), external links (`xdg-open`), file drop, clipboard |
| Migration from the Flutter Linux build | import `shared_preferences.json` (native JSON types, `flutter.` prefix) and the single libsecret JSON item (look up by the `account` attribute) [V, network-data §8.5] |
| `RuntimeHost` for local servers | spawn OpenCode/Paseo directly, no proot; reuse the common rules |
| Release check + packaging | GitHub tag check that opens a page; `.deb` + tarball (parity), a real AppImage via `appimagetool`, Flatpak or Conveyor later [V, kmp-cmp-status §7] |
| Desktop QA | GNOME Wayland via XWayland, fractional scaling, IME for Arabic/CJK [U] |

What it will not have: screen-reader support on Linux [V], native Wayland, a crisp picture at fractional scales on GNOME.

### 15.3 iOS (estimate 12–18 agent-weeks for a remote client; 18–30 with widgets and push [U])

| Work | Estimate [U] |
|---|---|
| Turn on iOS targets; macOS CI runner or a Mac; signing, TestFlight | 1–2 aw + hardware/CI money + Apple Developer Program |
| Core actuals: Darwin Ktor engine with leaf pinning in `handleChallenge`, Keychain `SecretStore`, prefs, Room bundled driver | 2–3 aw |
| iOS shell and kit variants: safe areas, IME, back swipe, press highlight instead of ripple, bounce, sheet shape, haptics, Reduce Motion | 4–6 aw |
| Media: sherpa-onnx xcframework via cinterop, AVAudioEngine, AVFoundation QR, PHPicker via FileKit, PDFKit | 3–5 aw |
| Notifications and background: `UNUserNotificationCenter`, BGAppRefresh, and a **push relay** (server component + APNs) for "reply finished" | 3–6 aw |
| Widgets: WidgetKit + App Intents (SwiftUI, not shared) | 3–5 aw (optional) |
| Device QA, App Store review | 2–3 aw |

Scope rules for iOS: remote client only (no built-in Linux, no Paseo on the phone), capability flags hide the rest.
Kotlin/Native iOS minimum is 15.0 [V].

### 15.4 The same later work under other angles

| Later target | A (shared UI) | Android-only Compose UI first | Native UI per platform (e.g. SwiftUI on iOS) |
|---|---|---|---|
| Linux desktop | 6–10 aw | move kit + features to CMP (25–40 aw) then the same 6–10 | write a desktop UI (40–60 aw) or reuse CMP anyway |
| iOS remote client | 12–18 aw | 25–40 aw migration + 12–18 | 40–60 aw of SwiftUI + 8–12 aw of iOS plumbing |

All [U]; the migration figure depends on how much Android-only code crept into the UI.

---

## 16. Building it in parallel with agents

### 16.1 Constraints that set the pace

- Machines: PC 8 CPU / 16 GB with one emulator, ceiling about 2 Codex leads + 1 Claude subagent; VPS 8 vCPU / 23 GB, no
  KVM [R]. Realistic concurrency: **5–6 coding agents**, with Gradle-heavy work on the VPS under a lock and one emulator
  holder on the PC. A second build VPS would raise it to about 9–10.
- Owner rules: one agent per unit in its own worktree (no heavy Workflow fan-outs); Codex builds backend + a frontend
  contract, Claude builds UI from it; Sonnet for most builders, Opus for spikes, root causes and reviews; emulator pass
  before any APK; `[skip ci]` commits; no pushes, releases or signing without a fresh request [R].

The module graph is built for those rules: engine and protocol modules are Codex work behind frozen interfaces; kit and
feature modules are Claude work; Android shells and the migration are Opus work with emulator proofs.

### 16.2 Contracts frozen before fan-out

1. `:core:domain` gateway interfaces and `ServerCapabilities` (ported verbatim from Dart, so they are already designed).
2. Engine public APIs: state types, commands, `Failure` categories (written by the engine owner as a short contract file,
   the Codex "frontend contract" habit).
3. Kit API: one Kotlin signature per `docs/ux-system/kit-api/*.md` part.
4. `:ui:routes`, `PlatformCapabilities`, every seam interface in §11, `ComposerState`/`ComposerHost` between chat modules.

A `MODULE_OWNERS` file records one owner per module; shared modules change only through their owner.

### 16.3 Phases

| Phase | Weeks [U] | Concurrent work | Finish line |
|---|---|---|---|
| 0. Slice 0 | 1–2 | 3–4 Opus spikes (§19) + the Maestro parity flows written against the frozen Flutter build (testing-perf §12.2) | every spike green or its fallback chosen; skeleton builds android + desktop; iOS klib job runs |
| 1. Foundations | 3–6 | Codex: `:core:*`, `:protocol:oc2`; Claude: `:ui:kit` foundation, words pipeline; Opus: move existing Kotlin into `:android:*` green, `:android:migration` | existing JVM tests green in new modules; kit foundation parts with capacity tests; OC2 contract replays green |
| 2. Daily path | 6–10 | connection + transcript + delivery + requests engines; chats, chat, composer; Paseo + phone server start/restore; finished-reply FGS | on the emulator: connect, open a chat, stream, send/steer/stop, answer a permission, get the finished alert, survive a reboot. Owner dogfoods a side-by-side `.native` preview flavour (own data, no migration) |
| 3. Breadth | 10–20 | servers/pairing, phone setup + Linux manager + terminal, agents + sign-in, project/files/git/review, settings + Styles, usage, schedules, widgets/tiles/shortcuts, voice/read aloud/attachments/viewer/QR, diagnostics, search, teaching | every catalog row for these areas has a passing native test (ratchet); parity Maestro flows green on native |
| 4. Switch | 20–24 | upgrade journey, perf budgets vs Flutter, Baseline + Startup Profiles, release plumbing, signer + versionCode, updater, PRIVACY.md | release 3.0.0 candidate passes T0–T4 and the owner's phone test |

AI Team, FactsBook, Termux and Gas City follow the switch unless the owner pulls them in (§20).

### 16.4 Test tiers (testing-perf §2.2)

- **T0** while editing: one test class or catalog row on PC or VPS.
- **T1** branch ready: JVM tests of touched modules (common, desktop UI, Robolectric) + Roborazzi verify on the VPS.
- **T2** integration candidate: all JVM tests, Konsist, lint, `assembleRelease`, R8 analyzer, size gate, on the VPS.
- **T3** before any owner APK: emulator instrumented tests, Maestro smoke + parity subset on the release build, upgrade
  journey.
- **T4** milestones: Macrobenchmark startup + streaming, profile regeneration.

Every native test is named after its catalog row (`` `STATE-DATA-18 dispatch marker blocks auto resend`() ``), so a
script counts catalog coverage by grep and the ratchet only goes up.

### 16.5 What a typical slice looks like

One agent, one worktree, one module (or one engine + its screen when the same agent owns both, per
`same-agent-analyzes-and-builds`): read the map section and catalog rows → write the native tests from the rows → implement →
T1 on the VPS → hand to the coordinator for review and merge. UI slices add Roborazzi captures from the desktop target for
review and an emulator capture for any visual sign-off.

### 16.6 Ten lines for the rewrite's AGENTS.md

1. One `UiState` per screen; no event channels; no strings in state.
2. Engines own rules; ViewModels combine and translate.
3. Kit only: features import `:ui:kit*`, never `material3`, never raw `tween`/`spring`/haptics/sheets.
4. No `android.*` in `commonMain`; Android-only needs go through a seam in §11.
5. Expected failures are `Outcome.Failed`; no `runCatching` around suspend calls; no server text in UI.
6. No `Dispatchers.*` or `Clock.System` outside `:core:common`; inject them.
7. Every per-profile key or table is registered with `ProfileScope`.
8. Every URL the app did not author goes through `ExternalLinkPolicy`.
9. Tests are named after catalog rows and find UI by visible text.
10. Run target-specific Gradle tasks only; heavy builds on the VPS; one emulator holder.

---

## 17. Risks

| # | Risk | Likelihood / impact | Mitigation |
|---|---|---|---|
| 1 | Wrong signer or a low versionCode strands the owner or the public users | medium / severe | read the installed signer first; versionCode base ≥ 10000; upgrade journey in T3 |
| 2 | Migration loses secrets, markers or restore authority | low–medium / severe | byte backups, read-back verification, sources kept one release, prefs adopted in place, emulator proof incl. reboot restore |
| 3 | Predictive back vs container transform still fights | medium / visible | option A (gesture disables shared bounds) ships; option B spike; acceptance list in motion-native §4.4 |
| 4 | Streaming jank on long replies | medium / high (the reason for the rewrite) | block-level items, 50 ms publishes, draw-phase fade, 5,000-part spike in slice 0, Macrobenchmark vs Flutter |
| 5 | CMP pre-release churn (1.13 beta, M3 1.5 beta) | high / medium | churn stays inside `:ui:kit*`; 1.12.2 fallback; pin and upgrade on named triggers |
| 6 | Version coupling of Kotlin / Compose compiler / Metro / KSP | medium / medium | pair upgrades; Koin plan B; one owner for `build-logic` |
| 7 | Build time and memory on the 16 GB PC with two targets | high / medium | target-specific tasks, PC caps, VPS for full builds, measure in slice 0 |
| 8 | Agents bypass seams or leak Android types | high / low (compile errors) | compile-time failure in `commonMain`; Konsist rules; review by the coordinator |
| 9 | Compose crash model (no error widget) | medium / high | crash-loop safe mode, defensive UI models, launch report |
| 10 | Behaviour drift in ownership/drain logic of the Linux runtime | medium / severe | move Kotlin + tests first, unchanged; instrumentation acceptances rerun; no "simplifying" |
| 11 | Exactly-once semantics lost (queue, widget actions, schedules, card answers) | medium / severe | port their tests before code; reducers + durable claims before network |
| 12 | Redaction drift while merging seven scrubbers | medium / severe (privacy) | port all redaction vectors first as the ground truth |
| 13 | Owner judges the native UI less polished than Flutter's | medium / high | kit API docs as contract; concept-vs-build comparisons; emulator captures, one screen per image |
| 14 | Desktop/iOS weaknesses (§2.2) disappoint later | certain for Linux a11y / medium | state them now; desktop is opt-in; iOS budgeted separately |
| 15 | Machine ceiling stretches the calendar | high / medium | second VPS; desktop UI loop; quiet-hours rule respected |
| 16 | Upstream drift (OC2 2.2.x, Paseo, Codex, Claude Code pins) during a 4–6 month rewrite | high / medium | fresh probe of the pinned server before porting; drift tests; dialect table |

---

## 18. Effort estimate

Unit: **agent-week (aw)** = one agent slot for five working days, including tests from the catalog, review fixes and its
emulator pass. Calibration [R]: the Flutter app reached 479k lines in about ten weeks (first commit 2026-08-23) with up to
about 20 concurrent agents and much design churn; a port with maps, a behaviour catalog and frozen kit APIs should cost
less per line, while test-first and parity checks cost more. All numbers [U].

| Block | Source size | aw (low–high) | In daily-use scope? |
|---|---|---|---|
| Slice 0, `build-logic`, gates, test harness | — | 4–6 | yes |
| `:core:*` (redaction, trust, data, Room, net, SSE, downloader, Markdown model) | parts of state/domain, `KitRedact`, transport | 6–8 | yes |
| `:protocol:oc2` | `lib/api2` 10k + domain contracts | 4–6 | yes |
| `:protocol:paseo` + `:protocol:codex` | 9.1k + 2.6k | 3–5 | yes |
| `:protocol:team` | 12.1k | 2–3 | later |
| engines except team | `lib/state` 68k + domain logic | 12–17 | yes |
| `:engine:team` | ~8.5k | 3–5 | later |
| Built-in Linux (`:android:linux`, `:engine:runtime`, terminal; Termux excluded) | 25.2k Dart + 13.5k Kotlin moved | 7–10 | yes |
| Android surfaces (background, widgets, platform, schedules) | ~9.5k Dart + ~11k Kotlin moved | 4–6 | yes |
| Media and voice | ~18.7k Dart + 1.2k Kotlin | 4–6 | yes |
| Kit family | 107k | 11–16 | yes (live backgrounds and scenes can wait) |
| Features except Team and FactsBook | ~150k | 16–24 | yes |
| `:feature:team` | 17.3k | 4–6 | later |
| Migration + upgrade journey + identity checks | — | 2–3 | yes |
| Words pipeline, ARB conversion, native strings in 7 languages | — | 1–2 | yes |
| Perf, profiles, release plumbing, size gate, updater | — | 3–5 | yes |
| **Shared-UI premium** (desktop target in CI, seams, stubs, CMP resources) | — | 5–9 | yes |
| Coordination, review, integration (~10%) | — | 9–13 | yes |
| **Full parity total** | | **100–150** | |
| **Daily-use switch scope** (minus the three team blocks; Termux and FactsBook are already outside the rows above) | | **90–135** | |

Calendar at 5–6 concurrent agents: about **15–27 weeks** to the switch release for the daily-use scope (central guess
about 20 weeks), plus 2–3 weeks for the later blocks. At 9–10 concurrent agents (needs a second build VPS): about 10–15
weeks. Not included: the 7,967 widget tests (owner: later; about 15–25 aw on desktop/Robolectric harnesses), Linux
desktop (6–10 aw), iOS (12–30 aw).

Decisions that cut effort the most: drop Termux (about 1–2 aw), keep AI Team for after the switch (9–14 aw), postpone
live backgrounds and scenes (2–3 aw), retire hidden widget providers after a notice release, keep RemoteViews instead of
Glance (no rewrite of 4.3k lines).

---

## 19. Slice 0 (spikes before features; each under about two days)

| # | Spike | Acceptance | Fallback |
|---|---|---|---|
| a | Skeleton: `build-logic`, catalog, ~10 modules; Metro + `ConnectionScope` extension + `metrox-viewmodel-compose` + Nav3 per-entry ViewModels on CMP 1.13.0-beta01 for `android` + `desktop`; iOS klib compile on Linux | assembles; graph smoke test; klib job passes | CMP 1.12.2; Koin; iOS klib job only for `core`/`engine` |
| b | List → chat container transform with Nav3 predictive back (option A) on the emulator, RTL both edges, fling, reduced motion | the six motion-native §4.4 checks | plain shared-axis for list → chat until B |
| c | Transcript engine fed a recorded 5,000-part stream; block items; draw-phase fade | publish counts match the Flutter fixture; no frame over budget in Macrobenchmark on the emulator | escalate to per-part `StateFlow<String>` reads |
| d | Data: adopt a copied `FlutterSharedPreferences.xml`, `WriterLane` + `ProfileScope` sweep; Room 3 + KSP 2.3.13 on android + jvm (+ iOS klib); secure-storage v11 decrypt on the emulator from a real Flutter install | values decode; sweep matches Dart; secrets read back | SQLDelight 2.4.1 instead of Room |
| e | Test harness: desktop `runComposeUiTest` headless on the VPS (or `xvfb-run`), Roborazzi desktop + Robolectric (JDK 21, SDK 37), catalog ratchet tool | one kit part tested three ways; golden recorded on the VPS | Robolectric only |
| f | Strings: ARB → CMP resources + Android `res`; per-app language (Android 13 locale config) follows in CMP; Arabic plurals | switching language at runtime changes both UI and a notification | own `LocalResourceEnvironment` override |
| g | Kit seams: 1.3× frame-text cap with Android nonlinear scaling at 200%; `MotionPolicy`; haptics mapping; frost on API 31+ | matches Flutter behaviour on the emulator | linear cap |
| h | Build cost: clean and incremental times for android-only vs android + desktop on the PC and the VPS | numbers recorded; decide if desktop compiles per PR or nightly | desktop compile nightly only |

---

## 20. Owner decisions needed

1. **Choose the angle:** shared UI from day 1 (this proposal) vs Android-only UI first.
2. **Signer and versionCode base** for the switch APK (§14), after reading the installed signer.
3. **Bridge release:** ship a last Flutter release with the in-app installer so existing users self-update, or accept a
   manual download.
4. **Root Back:** drop "Press back again to exit" so predictive back-to-home works.
5. **Scope of the switch:** AI Team, FactsBook, Termux, Gas City, ACP and A2A after the switch?
6. **Paseo on the phone vs the OpenCode 2 Claude Agent provider** (changes agents-backends 08–30).
7. **Frost** on the composer and chat header (2026-10-30 exception): keep in native (API 31+, solid fallback)?
8. **Desktop expectations:** accept no Linux screen-reader support and XWayland rendering, or keep desktop on the
   Flutter build until Compose Desktop improves?
9. **iOS budget:** a Mac or macOS CI, the Apple Developer Program, and a push relay for finished-reply alerts.
10. **A second build VPS** to raise concurrency from 5–6 to 9–10 agents.

---

## 21. Unknowns (check in slice 0 or before the slice that needs them)

1. Exact Metro 1.5.1 annotation names and the Nav3 1.2 entry-provider receiver for the installer pattern.
2. Whether the Linux iOS-klib compile works with dependencies that use cinterop (Room's bundled SQLite, Coil).
3. CMP resources and Android per-app language; `preferredFrameRate` in the JetBrains fork; nonlinear font scaling under a
   capped density.
4. Build-time multiplier of the desktop target on this codebase size.
5. Compose Desktop IME on Linux for Arabic and CJK (IBus/Fcitx) [U, no current source found].
6. Terminal emulator model on iOS (Termux's library is Java, partly Android-bound).
7. Whether Compose 1.13 + Nav3 1.3 deferred transitions reach CMP stable in time for the switch.
8. Robolectric's lack of a usable `AndroidKeyStore` (assumed: secret migration tests run instrumented).
9. The OC2 SSE heartbeat interval (socket idle timeout) and 2.2.x differences.
10. Whether Linux desktop accessibility in Compose Desktop has moved since the 2024-12-03 page.

---

## 22. Sources

Internal (read 2026-11-01):

- Maps: `../map/{protocols,state-data,agents-backends,builtin-linux,ui-kit,screens-nav,chat-genui,media-voice,android-platform,packages-build,desktop-web-cross}.md`.
- Research: `../research/{kmp-cmp-status,architecture-stacks,network-data,rendering-chat,motion-native,media,android-surfaces,genui,testing-perf}.md`.
- Catalog: `../catalog/BEHAVIORS.md`, `behaviors.jsonl` (1,544 rows; 240 device-flow rows).
- Demo: `/home/eslam/Storage/Code/kmp-demo` (`shared/src/commonMain/.../ui/*`, `App.kt`, `androidApp/.../ReplyService.kt`).
- Repo counts: `/home/eslam/Storage/Code/wt/oc_app-batch1/android` (`find … -name '*.kt' | xargs wc -l`; `grep -l io.flutter`).
- Owner memory: `/home/eslam/.claude/projects/-home-eslam-Storage-Code-oc-app/memory/` (files listed at the top).

Web (fetched 2026-11-01):

- Compose Desktop accessibility (Linux "Not supported", page dated 2024-12-03):
  https://kotlinlang.org/docs/multiplatform/compose-desktop-accessibility.html
- Compose Multiplatform iOS accessibility (VoiceOver mapping, page dated 2025-07-22):
  https://kotlinlang.org/docs/multiplatform/compose-ios-accessibility.html
- KMP hierarchical project structure (no shared source set for JVM + Android, page dated 2026-10-01):
  https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html
- Version, library and platform facts are cited through the research files above, which list their own primary sources.

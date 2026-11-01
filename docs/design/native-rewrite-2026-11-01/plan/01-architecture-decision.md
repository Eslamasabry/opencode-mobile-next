# 01. Architecture decision record: the native rewrite

Written 2026-11-01. Status: **decided by the judge-panel rule**, with the owner confirmations listed in §12. Every
other plan section builds on this file. When a section and this file disagree, this file wins until it is amended
here.

Inputs: the three proposals in `../design/` (A shared UI, B native UI per platform, C Android-first), the three judge
reports, the eleven maps in `../map/`, the nine research files in `../research/`, the behaviour catalog in `../catalog/`
(1,544 rows, ids like `B-STATE-018`; 240 device-flow rows proven by 39 flows), the demo at
`/home/eslam/Storage/Code/kmp-demo`, the read-only Flutter source at `/home/eslam/Storage/Code/wt/oc_app-batch1`, and the
owner memory files.

Tags: **[V]** checked today against a primary source (Maven/Google Maven metadata, published `.module` files, official
docs). **[R]** read in the repo, a map, a research file or memory. **[U]** unverified; slice 0 proves it (§11).

---

## 1. The decision

**Build Proposal A, amended by the grafts in §4:** one Kotlin Multiplatform codebase. The logic *and* the UI (kit,
screens, navigation) live in `commonMain`, written with Compose Multiplatform (CMP). Android is the product and ships
first. Every KMP module also compiles for a desktop JVM target from the first commit, and a nightly job compiles common
code to iOS klibs. Desktop and iOS are built as products only in their own later phases.

What this means in one paragraph for a section writer: screens and kit parts are `commonMain` Compose code. Everything
only Android can do (built-in Linux, widgets, notifications, foreground services, tiles, voice runtime, camera, PDF,
the updater, the Flutter migration) is plain Android code in `:android:*` modules behind interfaces. Android-only
Compose APIs are allowed only inside the kit and shell's `androidMain`, behind kit APIs. On Android the shared UI *is*
Google's Jetpack Compose, so nothing is lost in smoothness or native feel.

The in-place switch from Flutter happens only at **parity** (§10). Before that the owner uses a side-by-side preview
package.

---

## 2. Why A

### 2.1 The judges' totals

| Proposal | Judge 1 | Judge 2 | Judge 3 | **Total** | Android feel (sum) | Ranked first by |
|---|---|---|---|---|---|---|
| A: shared CMP UI | 47 | 48 | 44 | **139** | 23.5 | judge 2 |
| C: Android-first Jetpack UI, KMP below | 48.5 | 43 | 47 | **138.5** | 27 | judges 1 and 3 |
| B: shared core, native UI per platform | 40.5 | 37 | 42 | **119.5** | 26 | nobody |

The rule given to this record was: the highest total wins, and only a tie is broken toward Android feel. A leads C by
0.5 points. That is the smallest step the judges scored in, but it is not a tie, so **A wins**. Two of three judges
put C first, and C scored higher on Android feel. This record therefore takes C's strongest ideas as grafts (§4), and
§12 D1 gives the owner one clean chance to overturn the platform bet before slice 1.

### 2.2 Why the result is sound beyond the arithmetic

1. **It matches the owner's brief.** The brief says "Android first and best, but built so iOS and Linux desktop can
   follow tomorrow". C's own decision rule says: choose CMP UI from day 1 if a second platform is committed inside the
   Android build window. The one judge who scored through the "iOS and desktop within a year" lens picked A.
2. **A's Android-feel deduction is mostly removable, and this was verified today.** On Android the JetBrains
   artifacts are empty shims over Google's:
   - `org.jetbrains.androidx.navigation3:navigation3-ui-android:1.2.0` is a 6 KB AAR. It depends on
     `androidx.navigation3:navigation3-ui:1.2.0` [V, published `.module` and AAR size].
   - `org.jetbrains.compose.material3:material3-android:1.13.0-beta01` is a 6.5 KB AAR over
     `androidx.compose.material3:material3:1.5.0-beta01` [V].

   So the Android APK runs Google's code. Gradle resolves the highest version, so the Android build can pin a newer
   Jetpack release than CMP has republished and get its fixes. Only *new APIs* must wait for CMP before `commonMain` may
   call them. Until then they live in the kit's or the shell's `androidMain` (§7.6, `JETPACK_AHEAD.md`). That covers the
   judges' examples: the Nav3 1.3 predictive-back handoff, the Compose 1.13 fix for selecting text while it streams,
   `imeNestedScroll`, non-linear font scaling and `RenderEffect` frost.
3. **Portability is a compiler fact, not a lint rule.** In `commonMain`, `android.*`, `R.string` and `LocalContext` do not
   compile. C's later conversion bill (6–10 agent-weeks, landing in the middle of the iOS and desktop push) disappears.
4. **The demo the owner felt was A in miniature.** Every screen of `/home/eslam/Storage/Code/kmp-demo` is in
   `shared/src/commonMain` [R].
5. **One kit everywhere** fits the owner's kit-only rule. B's design means two or three kits that drift.
6. **Agents get an emulator-free loop.** The common tests run on the desktop JVM, and screenshots render on the VPS.
   One emulator on a 16 GB PC is the real bottleneck [R pc-capacity-ceiling].

A's real costs stay on the books. Every KMP module compiles for a second target (an estimated 1.3–1.6× compile work,
measured in slice 0). There are about 15–20 platform seams. The switch release rides CMP 1.13 and Material 3 1.5, which
are beta today. Compose Desktop on Linux has no screen-reader support. §11 and §12 deal with each.

---

## 3. Rejected options and why

### 3.1 Proposal C (Android-first Jetpack UI, KMP below): rejected by 0.5 points, heavily grafted

- UI portability would rest on an import allowlist, not the compiler. C admits the allowlist misses JVM-only calls
  that need no import.
- It defers a 6–10 agent-week UI conversion to the moment iOS and desktop start. During that conversion there would be
  two kinds of UI module, two string pools and two test hosts.
- It needs two modules per feature (presentation plus UI), about 13 extra modules, and small UI changes touch both.
- Agents get no desktop UI loop while the single emulator is the bottleneck.
- Its `jvmShared` android+jvm source set is unsupported by Kotlin (hierarchy docs dated 2026-10-01 [R]).
- **Kept from C:** minimal stored-format change at the switch, the component-freeze test, the MigrationGate with
  EntryRouter holding intents, Redactor registration before any log line, the import allowlist for features, one
  platform-difference idiom, the test-writer lane, `contracts/<slice>.md`, slice briefs that name what they delete,
  `JETPACK_AHEAD.md`, the side-by-side `.native` preview package, and one string file per feature.

### 3.2 Proposal B (shared core, native UI per platform): rejected, lowest total (119.5)

- A SwiftUI iOS front end costs 1.5–2× the UI work. It cannot be built or iterated on the team's Linux machines, and it
  means a second kit with every owner rule applied again, plus a permanent parity ledger.
- It has about 80 modules.
- It pins Material 3 1.4.0, which lacks the public `MaterialTheme(motionScheme = …)` and the in-window `BottomSheet`
  with predictive back that the kit needs [R motion-native §0.7].
- It runs the migration synchronously in `Application.onCreate`, an ANR risk for cold-started receivers and widgets.
- Its in-place update in Wave 2 comes before settings, files, phone setup, schedules and voice are ported.
- **Kept from B:** the full install-over identity proof in slice 0, the `Application.onCreate` order, the Compose
  stability configuration with a recomposition-count test, platform-owned composer text (`TextFieldState`), ViewModel
  actions named after intents, fake screen state for previews, deep links through `EntryRouter` only, Termux
  `terminal-view` on day 1, CMP-format XML as the single string source, and an iOS design pass plus a Liquid Glass
  decision before iOS starts.

### 3.3 Stack pieces considered and rejected

| Rejected | Why | Chosen instead |
|---|---|---|
| Hilt/Dagger | no KMP | Metro |
| Koin as the primary DI | resolves at runtime, has central module lists, and its plugin is also tied to the Kotlin version | Metro; Koin 4.2.2 is plan B only |
| Circuit, Molecule, Decompose, Orbit/MVIKotlin/Workflow | extra idioms for mostly-Sonnet agents; 0.x or stale | plain `ViewModel` + `StateFlow` |
| Navigation 2 (`navigation-compose`) | Navigation 3 is the actively developed library, with scenes and KMP deep links | Navigation 3 |
| Glance widgets on day 1 | the ZTE launcher size-map trap; it would rewrite 4.3k working lines | keep RemoteViews |
| A2UI and Remote Compose for agent cards | pre-1.0, Android-only renderer, conflicts with kit-only | the existing `oc-ui` card contract, ported |
| OpenAPI codegen for OpenCode 2 | 390 `anyOf`, zero discriminators | a hand-written client plus drift tests |
| `security-crypto`, third-party secret wrappers | deprecated, or we lose ownership of the secret format | own Keystore AES-GCM `SecretStore` |
| ML Kit | Google telemetry, 2.4 MB | zxing-cpp |
| Paparazzi, Compose Preview Screenshot Testing | Android-only alpha, or unmaintained | Roborazzi |
| Ktor's built-in SSE reconnection | fixed delay, replays the first request, no frame cap | our own SSE loop |
| A custom android+jvm intermediate source set | unsupported by Kotlin | plain `kotlin("jvm")` modules (§7.4) |
| SwiftUI as the iOS default | the cost above | CMP with iOS kit variants; Swift only where Apple requires it (§12 D13) |

---

## 4. Disagreements between proposals and judges, resolved

Each row is final. "Graft" names the proposal the idea came from.

| # | Topic | Positions | Resolution |
|---|---|---|---|
| 1 | Winner | Judges 1 and 3: C. Judge 2: A. | A, by the stated rule (§2.1). |
| 2 | Where screens and kit live | A: `commonMain`. C: Android library modules. B: Android modules plus a presentation layer. | `commonMain` (CMP). Android-only Compose APIs only in `androidMain` of `:ui:kit*` and `:ui:shell`, behind kit APIs, each listed in `JETPACK_AHEAD.md` (graft C). |
| 3 | Kit modules | C: one kit owner. A: three modules. | `:ui:design` (pure tokens and policies, no Compose) plus `:ui:kit`, `:ui:kit-chat` and `:ui:kit-content`, each with one owner under one design authority (graft A; `:ui:design` from C). |
| 4 | Modules per feature | B and C: presentation plus UI. A: one module. | **One module per feature.** Inside it, the `presentation` package (ViewModels, `XUi`, `XActions`, reducers) must not import `androidx.compose.*` (Konsist), so a later split for a SwiftUI front end is a file move. |
| 5 | String source of truth | A and C: generate from ARB. B and judge 2: `strings.xml`. Judge 1 called B's choice a weakness. | **One-off conversion from ARB.** After it, CMP-format XML in `:ui:words` is the only source: `composeResources/values*/strings_<feature>.xml`, one file per feature per language. Android `res` for OS surfaces is generated from `strings_os.xml`. The ARB files freeze with Flutter. Once Flutter is frozen, ARB has no tooling of its own; XML is native to both CMP and Android lint. |
| 6 | Per-app language with CMP resources | Unverified everywhere. | Slice-0 proof. Fallback: our own `LocalResourceEnvironment` override driven by the `AppCompatDelegate` locale (§11 P6). |
| 7 | When the in-place switch happens | C: daily-path switch at weeks 12–15. A: at parity. | **Parity** (graft A, "nothing lost", §10). C's daily-path build ships only as the `.native` preview package. |
| 8 | Room for big blobs | A and network-data: in the switch release. C: switch+1. Judge 2: decide on evidence. | **Default switch+1**, with a measured trigger that pulls it into the switch release (§9.8). The schema rules apply either way. |
| 9 | Migration timing at startup | B: synchronous in `onCreate`. A and C: asynchronous gate. | B's *order* with A and C's *asynchrony*. Only step 0 (byte backup) runs synchronously, and only on the first native launch (§9.7). |
| 10 | JVM code shared by Android and desktop | C and B: `jvmShared`. A: convert or keep in Android. | Plain `kotlin("jvm")` modules (`oc.jvm.library`), e.g. `:core:net-okhttp`. `/proc` and process code stays in `:android:linux` until desktop work starts. |
| 11 | Platform-difference idiom | A: `expect/actual` plus interfaces plus CompositionLocals. C: interfaces only. | **Interfaces.** Services bind through Metro. Composable seams go through one `KitPlatform` interface provided by `LocalKitPlatform`, only inside the kit. `expect/actual` is allowed only in `:core:common` (graft C). |
| 12 | Deep links | A: Nav3 matcher. B: `EntryRouter` only. | **`EntryRouter` only** (graft B). Nav3's deep-link matcher is not used. |
| 13 | "Press back again to exit" | A and C: drop it. B: keep it as an option. | Drop it; owner confirms (§12 D5). The root never intercepts Back. |
| 14 | Material 3 line | B: 1.4.0. A and C: 1.5. | The **1.5 line** (CMP `material3` 1.13.x = Jetpack M3 1.5.x), kit only. |
| 15 | Terminal renderer | A: common canvas renderer. B and C: Termux `terminal-view`. | **Termux `terminal-view`** through `AndroidView` in `:ui:kit-content` `androidMain` on day 1. A common renderer is built only when desktop work starts. |
| 16 | Desktop gallery and Hot Reload | A: day 1. Judge 3: after the switch. | `:desktopApp` exists from slice 0 as the **kit gallery** (cheap, since the target exists anyway). Compose Hot Reload is an optional trial and never a required step. Visual sign-off stays on emulator captures. |
| 17 | Desktop compile per change or nightly | A: unmeasured. | Decided by slice-0 measurement P8. Default: a T1 build compiles both targets for touched modules on the VPS, and a local T0 compiles one. |
| 18 | Widget tests | Judges: sample 200 in slice 0, or port behaviour tests as slices land. Owner: "later". | **The owner wins.** No widget-test work until the owner schedules it, and the 200-test classification is that phase's first step. Only zero-cost preparation happens now: test tags equal Flutter `ValueKey` strings, and screens take `(XUi, XActions)`. |
| 19 | Composer text ownership | B: the platform text field. | `TextFieldState` in the UI owns text and IME composition. The ViewModel gets `onDraftChanged(text, selectionUtf16)` (graft B; §9.2). |
| 20 | Compose stability across modules | B flagged it; A and C missed it. | `compose-stability.conf` on every Compose module, plus a slice-0 recomposition-count test (graft B; §9.3). |
| 21 | iOS UI technology | A: CMP. B: SwiftUI. C: open. | CMP with iOS kit variants. Swift only for WidgetKit, App Intents and the share extension. An iOS design pass and a Liquid Glass decision come before the iOS phase (§12 D13). |
| 22 | `:ui:words` as a hotspot | Judge 3. | One string file per feature. Feature agents edit only their own; the l10n owner owns tooling and `strings_common.xml`. |
| 23 | Room schema discipline | A. | Every table has `profile_id` or is on an allowlist. A per-table `ProfileScope` registry. Import with counts and a SHA-256 per blob; source keys kept for one release (graft A). |
| 24 | OS-visible names | All agree; C adds the test. | A component-freeze test against the Flutter 2.1.0+57 APK, run at every T2 (graft C; §9.9). |
| 25 | Signer | The brief says `1DE5BF08…D60C`; the maps show public releases on `842284B2…1C`. | Keep both lineages exactly as today; never substitute (§12 D2). |
| 26 | Engine output vs routes | A: `EntryRouter` makes navigator calls. Layering bans engines from seeing routes. | `EntryRouter` emits neutral `EntryTarget`s (`OpenChat(ref)`, `OpenSettings(section)`, …). `:ui:shell` maps them to route keys. |
| 27 | Where platform-service interfaces live | C: all in `:core:domain`. A: spread across engines. | In the engine that consumes them (e.g. `RuntimeHost` in `:engine:runtime`). An interface consumed by more than one engine, or by features, goes in `:core:domain`. |

---

## 5. Module graph

### 5.1 Layers

```
:androidApp (application id io.github.eslamasabry.opencode_mobile; every OS-visible class at today's FQCN; final Metro graph)
  |-- :android:* (plain Android libraries: linux, agenthost, terminal, background, schedule, widgets, media, platform, migration)
  |-- :ui:shell (App(), Nav3 NavDisplay + scenes, AppNavigator impl, theme root, MigrationGate/safe-mode screens)
  |     |-- :feature:* (KMP + CMP: screens + ViewModels + entry installers)
  |           |-- :ui:kit  :ui:kit-chat  :ui:kit-content   (KMP + CMP; the only modules that may use material3)
  |           |-- :ui:words (CMP resources)   :ui:routes (route keys)   :ui:design (pure tokens and policies)
  |-- :engine:* (KMP, no Compose: app- and connection-scoped logic; StateFlow out, suspend + Outcome in)
  |-- :protocol:* (KMP: oc2, paseo, codex; implement :core:domain gateways)
  |-- :core:* (KMP: common, security, model, domain, data, db, net, markdown; JVM: net-okhttp; test: testing)
:desktopApp (kit gallery from slice 0; product later)   :quality (rules, graph smoke, freeze, ratchet)   :android:benchmark
build-logic (included build)   :tools:strings (JVM CLI)   iosApp/ (Xcode, later, not Gradle)
```

### 5.2 Every module

Plugins (convention plugins in `build-logic`):

- `oc.kmp.library`: KMP with `android` (AGP-KMP `com.android.kotlin.multiplatform.library`) and `jvm("desktop")`, plus
  `iosArm64` and `iosSimulatorArm64` only with `-Poc.ios=true`. No Compose.
- `oc.kmp.compose`: `oc.kmp.library` plus the Compose compiler, the CMP plugin, resources and the stability config.
- `oc.kmp.feature`: `oc.kmp.compose` plus the feature dependency rules and the import allowlist.
- `oc.jvm.library`: `kotlin("jvm")`.
- `oc.android.library`: `com.android.library`.
- `oc.android.application`: the app module.
- `oc.desktop.application`: the desktop app.

Source sets: **c** = `commonMain`, **a** = `androidMain`, **d** = `desktopMain`, **t** = `commonTest` (runs as
`desktopTest` on the JVM). Owner: **S** = single owner, changes go through them (recorded in `MODULE_OWNERS`);
**F** = one feature agent owns it end to end. "Starts" = the slice that first creates the module; no module exists
before it. Slice order: 0 skeleton and proofs, 1 foundations, 2 chat daily path, 3 phone runtime and agents, 4 surfaces,
settings and schedules, 5 project, media and integrations, 6 migration hardening and switch. The delivery plan owns the
dates.

| Module | Plugin | Source sets | Responsibility | Owner | Starts |
|---|---|---|---|---|---|
| `build-logic` | included build | — | the convention plugins above, the dependency-rule check, per-host properties | S coordinator | 0 |
| `:core:common` | kmp.library | c, a, d, t | `Outcome`, sealed `Failure`, `attempt {}`, `AppDispatchers`, `Clock`, ids, `AppLog` facade (Kermit), `Trace`, `WireJson` + tolerant serializers, `PlatformViewHandle`. **The only module allowed `expect/actual`.** | S core | 0 |
| `:core:security` | kmp.library | c, t | the one `Redactor` + `RedactingWriter`, `Redacted`, `ExternalLinkPolicy` (decision logic), address rules (loopback, RFC1918, `100.64.0.0/10`, `.ts.net`), cleartext consent model, three-state consent reads | S core | 0 |
| `:core:model` | kmp.library | c, t | plain data: profiles, messages/parts/tool states, cards, `FormSpec`, schedules | S core | 0 |
| `:core:domain` | kmp.library | c, t | `ServerGateway`, `ServerOperationsGateway`, `ServerCapabilities` (85 flags), `PlatformCapabilities`, interfaces shared by several engines or features, pure domain rules | S core | 0 |
| `:core:data` | kmp.library | c, a, d, t | `KeyValueStore`, `SecretStore`, `BlobStore`, `ProfileScope` (key and table registry + sweep), `WriterLane`, `MigrationGate`. a = adopted `FlutterSharedPreferences` + Keystore AES-256-GCM. d = in-memory until the desktop phase. | S core | 1 |
| `:core:db` | kmp.library + KSP | c, a, d, t | Room 3 `oc.db`: offline queue, drafts index, stash, receipts, first-paint caches; every table has `profile_id` | S core | switch+1, or earlier by trigger (§9.8) |
| `:core:net` | kmp.library | c, a, d, t | Ktor client factory, SSE parser (16 MiB cap) + `EventStream` loop, WebSockets, resumable pinned downloader, cleartext consent plugin, transport-to-`Failure` translation. a and d both call `:core:net-okhttp`. | S backend | 1 |
| `:core:net-okhttp` | jvm.library | main, test | hardened OkHttp 5 client builder (no retry on connection failure, no redirects, `NO_PROXY`) and the leaf-DER SHA-256 pinning `TrustManager`. **No project dependencies.** | S backend | 1 |
| `:core:markdown` | kmp.library | c, t | streaming Markdown → block model (`StreamingMarkdownFile`), path chips, agent blocks, `proseForSpeech` | F chat lead | 2 |
| `:core:testing` | kmp.library (test only) | c | fakes, `TestClock`, `TestDispatchers`, `MockEngine` fixtures, catalog-row helpers | S core | 0 |
| `:protocol:oc2` | kmp.library | c, t | OpenCode 2 client: dialect table, routes, tolerant DTOs, 91-event registry with `UnknownEvent`, pairing, forms, PTY over WebSocket, gateway. The legacy-server *recognition* only (OpenCode 1 is gone [R]). | F backend (Codex) | 1 |
| `:protocol:paseo` | kmp.library | c, t | Paseo WebSocket transport, gateway, mappers, chat feed (ACP agents go through Paseo) | F backend (Codex) | 3 |
| `:protocol:codex` | kmp.library | c, t | Codex app-server JSON-RPC | F backend (Codex) | 5 |
| `:protocol:a2a` | kmp.library | c, t | external agents over A2A, only if §12 D6 keeps them | F backend | later |
| `:protocol:team` | kmp.library | c, t | Gas City supervisor + phone-engine `/v1` clients, only if AI Team is ported | F backend | later |
| `:engine:settings` | kmp.library | c, t | preferences facade, appearance (Style, Fine-tune, Saver), consent, settings scope model, teaching/tips/nudges registry, locale, first run | F | 1 |
| `:engine:diagnostics` | kmp.library | c, t | ring buffer, problem report, crash categories, exit classification (incl. `MemoryLimiter:AnonSwap`), `PerfTrace`, update-notice state | F | 1 |
| `:engine:profiles` | kmp.library | c, t | profile store, trust, pins, sign-in reset, the deletion transaction (run backwards) | S core | 1 |
| `:engine:connection` | kmp.library | c, t | `ConnectionRegistry`, one `ConnectionGraph` per live connection, status, location, quiet reconnect, session list, selection, read state | S backend | 1 |
| `:engine:transcript` | kmp.library | c, t | event apply, version fences, settle/fold/merge, 50 ms publish, run lifecycle, turn stall, search | S chat lead | 2 |
| `:engine:requests` | kmp.library | c, t | approvals, permission policy, questions, the one `FormSpec` engine (OC2, MCP/ACP elicitation, Codex, AskUserQuestion), agent-card codec/validator/receipts | F | 3 |
| `:engine:delivery` | kmp.library | c, t | send pipeline, steer (never queue where steering exists), offline queue + dispatch markers, command receipts, stop | F | 2 |
| `:engine:composer` | kmp.library | c, t | drafts, attachment vault, stash, photo recovery record, phrase chips, prompt history | F | 3 |
| `:engine:runtime` | kmp.library | c, t | `RuntimeHost` interface + portable built-in Linux rules (setup engine, start/probe, recovery consumer, healing, idle, thermal, budget, manager parsers), shell scripts as resources with checksums | S runtime | 3 |
| `:engine:agents` | kmp.library | c, t | phone agents, agent catalog (verified pins copied verbatim), merged feed, handoff, auto-approval, sign-in model, `AgentHost` interface | F | 3 |
| `:engine:attention` | kmp.library | c, t | attention feed, profile monitor, notification policy and copy keys, the one status-line combiner, widget snapshot + policies | F | 4 |
| `:engine:schedule` | kmp.library | c, t | recurrence (one implementation, DST golden tests), CAS store contract, claims `id:revision:dueAtMs`, receipts, runner | F | 4 |
| `:engine:media` | kmp.library | c, t | voice controller, model packs + total-RAM gate, auto-send policy, read-aloud scope, MIME gate, SVG sanitizer, office text, photo caps, PDF budgets, Wi-Fi share rules | F | 5 |
| `:engine:project` | kmp.library | c, t | project hub, files, git page model (GitHub device flow), review/diff model, staged revert, terminal session model, dev-preview rules | F | 5 |
| `:engine:integrations` | kmp.library | c, t | MCP, providers (never echo keys), usage/quota, BYO host | F | 5 |
| `:engine:entry` | kmp.library | c, t | bootstrap order, `EntryRouter` (one pending-intent mechanism replacing seven) emitting `EntryTarget`s | S coordinator | 2 |
| `:engine:team` | kmp.library | c, t | AI Team domain and controllers | F | later (§12 D6) |
| `:ui:design` | kmp.library (no Compose) | c, t | colour roles (ARGB ints) + contrast guard, 31 packs, Style resolver (Solid default, Soft, Brutal, Paper, Signal) + Fine-tune, motion tokens + `MotionPolicy` (Full/Calm/Off, Saver, heat), text-scale policy (1.3× frame cap), window classes (600/840/1200, short < 480), capacity and max-width table | S design authority | 1 |
| `:ui:words` | kmp.compose (resources) | c, t | all copy (7 languages, one file per feature), the exhaustive `Failure` → words mapping, plain-words tables, `Words` resolver (composable and suspend). `strings_os.xml` also feeds Android `res`. | S l10n (feature files F) | 1 |
| `:ui:routes` | kmp.library | c, t | every `@Serializable` route key (`NavKey`), one file per feature, the `SerializersModule`, the `AppNavigator` interface | S coordinator (feature files F) | 0 |
| `:ui:kit` | kmp.compose | c, a, d, t | foundation and general parts (KitText, KitIcon, KitSurface, KitTappable, KitScreen/TopBar, rows, inputs, KitSheetHost, confirm, states, teaching parts, KitFrameText, KitMotion, KitFrost), `KitPlatform` + `LocalKitPlatform`, `ExternalLinkUriHandler`. a = `AndroidKitPlatform`. d = `DesktopKitPlatform`. | S kit owner (design authority) | 1 |
| `:ui:kit-chat` | kmp.compose | c, a, d, t | turn, message, work line, tool rows, Markdown block renderers, stream reveal (draw-phase fade), composer parts, request/answer cards, the `FormSpec` renderer, agent cards | S kit-chat owner | 2 |
| `:ui:kit-content` | kmp.compose | c, a, d, t | code block + `CodeTokenizer` seam, diff view, viewers (Coil + Telephoto, PDF pages), QR draw (qrose), charts. a = scanner (`CameraXViewfinder`), terminal (Termux `terminal-view`), hardened `WebView` preview. | S kit-content owner | 3 |
| `:ui:shell` | kmp.compose | c, a, d, t | `App()`, theme root, Nav3 `NavDisplay` + scenes (rail, sidebar, list-detail), `AppNavigator` impl, entry-installer assembly, `EntryTarget` → route mapping, status-line slot, MigrationGate / "Opening…" / safe-mode screens. a = Android-ahead transitions (§9.4). | S coordinator | 1 |
| `:ui:testing` | kmp.compose (test only) | c, a, d | compose test rules, Roborazzi config, RTL / font-scale / width harness, capacity stress helpers | S kit owner | 1 |
| `:feature:onboarding` | kmp.feature | c, t | bootstrap gate UI, root decision, first run, managed move | F | 2 |
| `:feature:chats` | kmp.feature | c, t | Conversations home, inbox, all conversations, filters, new conversation, import, pins | F | 2 |
| `:feature:chat` | kmp.feature | c, t | chat frame, transcript list, scroll and follow, top bar, status, find, timeline, message actions; owns the `ChatUi`/`ComposerState`/`ComposerHost` contract | S chat lead | 2 |
| `:feature:chat-composer` | kmp.feature | c, t | composer, chips, tray, attachments, slash and mention, voice pill, model picker | F | 3 |
| `:feature:chat-pages` | kmp.feature | c, t | session pages: review/revert, context, export, notes, relations, approvals sheet | F | 4 |
| `:feature:servers` | kmp.feature | c, t | servers, editor, pairing, connection details, switcher, server page | F | 2 |
| `:feature:phone` | kmp.feature | c, t | This phone, setup A/B/C, Linux manager, local terminal, keep running, phone health (gated by `PlatformCapabilities`) | F | 3 |
| `:feature:agents` | kmp.feature | c, t | agents hub, agent sheet, sign-in, account (external agents only if D6 keeps them) | F | 3 |
| `:feature:project` | kmp.feature | c, t | Files hub, browser, viewer, review/diff, git sheet, server terminal, dev preview, project tools, Wi-Fi share | F | 5 |
| `:feature:settings` | kmp.feature | c, t | settings hub + groups + search, Appearance, notifications/background, voice, providers/MCP, tools, Manage space page | F | 4 |
| `:feature:usage` | kmp.feature | c, t | usage hub, usage-widget settings | F | 5 |
| `:feature:schedules` | kmp.feature | c, t | scheduled prompts | F | 4 |
| `:feature:about` | kmp.feature | c, t | report a problem, diagnostics, about, guide, tips, updates | F | 4 |
| `:feature:search` | kmp.feature | c, t | command launcher, Go to, settings search | F | 5 |
| `:feature:widget-panels` | kmp.feature | c, t | overlay panels (style, project, steer, voice) hosted by `WidgetOverlayActivity` | F | 4 |
| `:feature:team`, `:feature:factsbook` | kmp.feature | c, t | AI Team; FactsBook | F | later (§12 D6) |
| `:android:linux` | android.library | main, test, androidTest | `BuiltinLinux` split (RuntimeInstall, ProotLauncher, ServiceRegistry, InstallerSlot, ServerRecovery, IdleController), ownership proofs (never "simplify" `NativeRuntimeOwnership.plan`), recovery, setup runner, `/proc` + process tree + orphan reaper, `jniLibs` (proot, loader, talloc, shmem), `RuntimeHost` impl | S runtime | 1 (move green), 3 (wire) |
| `:android:agenthost` | android.library | main, test | `PhoneAgent*`, `PaseoStaleLock`, admission/readiness/idle, leases; `AgentHost` impl | S runtime | 3 |
| `:android:terminal` | android.library | main, test | PTY (Termux `terminal-emulator`, `PtyAccess.java`), session manager (512 KiB scrollback, back-pressure), `TerminalModel` impl that hands a `PlatformViewHandle` to the kit | F | 5 |
| `:android:background` | android.library | main, test | notification channels, the shared status row (id 4747), coding alerts, Live Updates (user-started runs only), FGS hosts (reply watch `dataSync`, live mode, phone server `specialUse`), tile logic, boot/package-replaced logic | F | 4 |
| `:android:schedule` | android.library | main, test | AlarmManager, WorkManager worker (≤ 8 min, same class name), receivers, CAS store binding | F | 4 |
| `:android:widgets` | android.library | main, test | RemoteViews binders, layouts, render-once size policy, `previewImage` PNGs, widget-action runner (30 s short FGS, claim ledger) | F | 4 |
| `:android:media` | android.library | main, test | sherpa-onnx (vendored AAR + keep rule), `AudioRecord` 16 kHz, `TextToSpeech`, Kokoro → `AudioTrack`, audio focus, CameraX + zxing-cpp, isolated-process `PdfRenderer` service | F | 5 |
| `:android:platform` | android.library | main, test | network, thermal, lifecycle/exit info, crash store, storage, project export, BYO signer, Tailscale handoff, `PackageInstaller` updater, `ActivityResults` (Photo Picker, SAF, TakePicture, permissions), share in/out, AppFunctions (optional path) | F | 2 |
| `:android:migration` | android.library | main, test, androidTest | the Flutter data migrator (§9.7) | S migration (Opus) | 0 (spike), 6 |
| `:android:team`, `:android:termux` | android.library | main, test | phone engine bridge + `libaiteam_*` (with the release hash check); Termux bridge | S runtime | only if §12 D6 / D7 keep them |
| `:android:benchmark` | `com.android.test` | main | Macrobenchmark (cold start, open chat, fling, stream), Baseline and Startup Profile generator | F | 2 |
| `:androidApp` | android.application | main, test, androidTest, debug/qa | namespace and id `io.github.eslamasabry.opencode_mobile`; `OcApplication`; `MainActivity` (`ComponentActivity`, `setContent { App(…) }`); every OS-visible shell at its exact FQCN; final `@DependencyGraph AndroidAppGraph`; manifest, signing, ABI splits, R8 rules, generated `strings_os` res; `AppBuildInfo` (instead of `BuildConfig` in KMP modules); flavors `main` and `preview` (`.native` suffix) | S coordinator | 0 |
| `:desktopApp` | desktop.application | main | slice 0: kit gallery (`App()` and kit parts with fakes); product shell only in the desktop phase | S kit owner | 0 |
| `:quality` | jvm.library (tests) | test | Konsist rules (§6.3), dependency-rule test, Metro graph smoke, catalog coverage ratchet, component-freeze list, Room `profile_id` schema test | S coordinator | 0 |
| `:tools:strings` | jvm.library (CLI) | main, test | one-off ARB → XML converter; drift check (every key in 7 languages, matching format args and plurals) | S l10n | 1 |

That is about 66 modules at the switch. Slice 0 creates about 12 of them. Kotlin packages for new code are
`io.github.eslamasabry.oc.<layer>.<name>`, and module namespaces derive from the path. They must differ from the
app's namespace, an AGP 9 rule [R]. Reused Kotlin keeps its package. OS-visible classes keep
`io.github.eslamasabry.opencode_mobile.*` and live in `:androidApp`. Kotlin packages are independent of module
namespaces, so FQCNs survive module moves [R].

---

## 6. Dependency rules

### 6.1 Allowed project dependencies

| From | May depend on | Must not depend on |
|---|---|---|
| `:core:common` | (none) | everything |
| `:core:security`, `:core:model` | `:core:common` | the rest |
| `:core:domain` | `:core:common`, `:core:model`, `:core:security` | data, net, engines, UI |
| `:core:data`, `:core:db` | `:core:common`, `:core:security`, `:core:model` (`:core:db` also `:core:data`) | domain, net, engines, UI |
| `:core:net` | `:core:common`, `:core:security`, `:core:model`, `:core:net-okhttp` (a, d only) | data, engines, UI |
| `:core:net-okhttp` | (none; external OkHttp only) | every project module |
| `:core:markdown` | `:core:common`, `:core:model` | the rest |
| `:protocol:*` | `:core:common`, `:core:model`, `:core:domain`, `:core:net`, `:core:security` | engines, UI, features, other protocols, data, db |
| `:engine:*` | `:core:*` except `:core:net-okhttp` (`:core:testing` in tests only), engines **above it** in §6.2 (public API only) | `:ui:*`, `:feature:*`, `:protocol:*` (gateways arrive through DI), Compose |
| `:ui:design` | `:core:common` | Compose and everything else |
| `:ui:words` | `:core:common`, `:core:model` | engines, kit |
| `:ui:routes` | `:core:common`, `:core:model` | the rest |
| `:ui:kit` | `:ui:design`, `:ui:words`, `:core:common`, `:core:security` (`Redacted` for the Details fold), `:core:model` | engines, protocols, features, data, net |
| `:ui:kit-chat` | `:ui:kit`, `:ui:design`, `:ui:words`, `:core:markdown`, `:core:model`, `:core:common`, `:core:security` | same as `:ui:kit` |
| `:ui:kit-content` | `:ui:kit`, `:ui:design`, `:ui:words`, `:core:model`, `:core:common` | same as `:ui:kit` |
| `:ui:shell` | `:ui:kit*`, `:ui:design`, `:ui:words`, `:ui:routes`, `:engine:entry`, `:engine:attention`, `:engine:settings`, `:core:domain`, `:core:model`, `:core:common` | protocols, data, net, features (they arrive as Metro-contributed entry installers) |
| `:feature:*` | `:ui:kit*`, `:ui:design`, `:ui:words`, `:ui:routes`, `:engine:*` (public API), `:core:domain`, `:core:model`, `:core:common` | `:protocol:*`, `:core:net`, `:core:data`, `:core:db`, other features, `material3`, `:android:*` |
| `:android:*` | `:core:*`, `:engine:*` (to implement or call their interfaces), `:ui:words` (copy), `:ui:design` (widget and notification colours) | `:feature:*`, `:ui:kit*`, `:ui:shell`, `:protocol:*` |
| `:android:migration` | `:core:common`, `:core:security`, `:core:data`, `:core:model` (+ `:core:db` when Room ships) | engines, UI |
| `:androidApp` | everything | nothing depends on it |
| `:desktopApp` | everything except `:android:*` and `:androidApp` | nothing depends on it |

### 6.2 Engine order (an engine may use only engines listed above it)

1. `settings`
2. `diagnostics`
3. `profiles`
4. `connection`
5. `transcript`
6. `requests`
7. `delivery`
8. `composer`
9. `runtime`
10. `agents`
11. `attention`
12. `schedule`
13. `media`
14. `project`
15. `integrations`
16. `entry`
17. `team`

A needed upward edge means the shared piece moves down: into a lower engine, or into `:core:domain` as an interface.
Only the coordinator may change this list.

### 6.3 Enforcement, cheapest first

1. **Gradle at configuration time.** The convention plugins check declared project dependencies against §6.1 and §6.2
   and fail the build (about 30 lines in `build-logic`).
2. **The compiler.**
   - `commonMain` cannot see `android.*`.
   - `explicitApi()` in `core`, `protocol` and `engine`.
   - The nightly iOS klib compile on the VPS (`-Poc.ios=true`, no cinterop dependencies) catches JDK APIs in
     `commonMain`.
3. **Konsist 0.18.1 in `:quality`.** These rules are also the rewrite's AGENTS.md rules:
   1. **Feature import allowlist** (graft C, kit-v2 §9). A `:feature:*` file may import only:
      - `androidx.compose.runtime.*`, `androidx.compose.foundation.layout.*`, `androidx.compose.foundation.lazy.*`, and
        the foundation scroll states and modifiers;
      - `androidx.compose.ui.{Modifier, Alignment, unit.*, semantics.*, focus.*, input.key.*}`;
      - `androidx.compose.ui.platform.{LocalLayoutDirection, LocalDensity, LocalFocusManager, testTag}`;
      - `androidx.lifecycle.*` (viewmodel, compose collection), the Metro ViewModel accessor, and
        `androidx.navigation3.runtime.*` (only in the feature's `Entries.kt`);
      - `org.jetbrains.compose.resources.{stringResource, pluralStringResource}` with the `:ui:words` `Res`;
      - kit, design, words, routes, engine public APIs, `core.domain`, `core.model`, `core.common`, and `kotlinx.*`.

      Everything else fails, including `material3`, raw `clickable`, `Dialog`, `ModalBottomSheet`, raw
      `tween`/`spring`/`animate*`, `performHapticFeedback`/`LocalHapticFeedback`, `LocalContext`, `LocalView`,
      `ui.viewinterop` and `java.*`.
   2. `presentation` packages import no `androidx.compose.*`. ViewModels hold no platform types and no copy strings.
   3. No `runCatching` around suspend calls; use `attempt {}`, which rethrows cancellation.
   4. No `Dispatchers.*` or `Clock.System` outside `:core:common`.
   5. No `Log.*`, `println` or `HttpLoggingInterceptor` anywhere. Logging goes only through `AppLog`.
   6. No `openUri`, `Intent.ACTION_VIEW` or `launchUrl`-like calls outside `ExternalLinkUriHandler` and `:android:platform`'s
      link opener.
   7. `getSharedPreferences(` only in `:core:data` `androidMain` and `:android:migration`.
   8. Every `@Serializable` route is registered in the `SerializersModule`.
   9. Every per-profile key matches `oc.<what>.<profileId>` and is registered with `ProfileScope`.
   10. `androidMain` exists only in the modules listed in §7.2.
   11. A text check bans `String.format`, `System.` and `Thread.` in KMP modules.
   12. Tests are named after catalog rows.
4. **Tests.**
   - The Metro graph smoke test resolves the release graph and every multibinding key.
   - The catalog ratchet: native tests named `` `B-STATE-018 dispatch marker blocks auto resend`() ``, counted by grep;
     the count only goes up.
   - The Room schema test, once `:core:db` exists.
   - The component-freeze test (§9.9).
   - `dependency-analysis` 3.20.1 on the VPS.

---

## 7. Multiplatform rules

### 7.1 Targets

- Every KMP module declares `android` and `jvm("desktop")`.
- `iosArm64` and `iosSimulatorArm64` exist only when `-Poc.ios=true` is passed. On Linux hosts that compiles klibs only,
  in a nightly VPS job.
- One convention plugin decides targets, so turning iOS on is a property flip, not 60 edits.
- **Desktop compiles; desktop never blocks.** A change is judged on Android behaviour plus "desktop compiles and the
  common tests pass". A missing desktop behaviour is a binding that reports its capability as off.

### 7.2 What may live where

| Source set | Allowed | Banned |
|---|---|---|
| `commonMain` | Kotlin stdlib, `kotlinx.*`, Ktor core, Kermit, Metro annotations, CMP (`oc.kmp.compose` modules only), Coil, Telephoto `zoomable`, qrose, SyntaxMP, `org.jetbrains:markdown`, kotlinx-io paths | `android.*`, `java.*`, `javax.*`, `org.json`, `String.format`, `System.*`, `Thread`, Android resources, Jetpack-only APIs not yet in CMP |
| `androidMain` | Android SDK and AndroidX. Allowed **only** in `:core:common` (actuals), `:core:data` (prefs adoption, Keystore), `:core:net` (OkHttp engine), `:core:db` (driver), `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content` and `:ui:shell` (`KitPlatform` implementation and `JETPACK_AHEAD` items), and the test-only `:ui:testing`. Every other KMP module has an empty `androidMain`. | business rules (those belong in `commonMain`) |
| `desktopMain` | the desktop bindings, in the same modules as `androidMain`: until the desktop phase, in-memory or `Unavailable*` stubs only, so modules compile and the gallery runs | product work before the desktop phase |
| `commonTest` | catalog-row tests with `kotlin.test`, coroutines-test, Turbine and fakes; runs as `desktopTest` on the JVM | Android types |
| Android library `src/test` | Robolectric tests for Android-only behaviour | — |

Everything Android-only that is not a composable seam goes in an `:android:*` module, never in a KMP module's
`androidMain`.

### 7.3 One way to handle a platform difference

1. **Services: an interface, bound through Metro.** Example:
   `interface RuntimeHost` in `:engine:runtime`, implemented by `AndroidRuntimeHost` in `:android:linux` with
   `@ContributesBinding(AppScope::class)`. Desktop binds an `Unavailable*` with its capability flag false. Tests use
   fakes in `commonTest`.
2. **Composable seams: `KitPlatform`, only inside the kit.**
   - `:ui:kit` `commonMain` declares `interface KitPlatform`. It holds haptics, press feedback, overscroll, the
     reduced-motion source, frame-text density, insets/IME, frost, the frame-rate hint, the link handler, and
     `@Composable` slots for terminal, scanner and web preview.
   - Pickers, the camera and permission prompts are *services*, not kit seams: `ActivityResults` and `PermissionGate`
     in `:core:domain`, implemented in `:android:platform` against `MainActivity`'s `ActivityResultRegistry`. ViewModels
     call them; screens never do.
   - The platform app module provides it once at the root through `LocalKitPlatform`. Android uses
     `AndroidKitPlatform` from the kit's `androidMain`. Desktop uses `DesktopKitPlatform` from its `desktopMain`.
   - Features never see `KitPlatform`; only kit parts read it.
3. **Android views inside the kit** (Termux `TerminalView`, `CameraXViewfinder`, `WebView`):
   - The engine-side interface (for example `TerminalModel`) exposes a `PlatformViewHandle` (`:core:common`,
     `interface PlatformViewHandle { val native: Any }`).
   - The `:android:*` module puts the native object in it, for example the Termux `TerminalSession`.
   - Only the kit's `androidMain` casts it. The wrapped library is a dependency of that `androidMain` alone.
4. **`expect/actual`: only in `:core:common`**, for primitives with nothing to fake (for example the IO dispatcher
   source and platform name). A new `expect` anywhere else is a review rejection.

### 7.4 JVM code shared by Android and desktop

Kotlin does not support a shared source set for android+jvm only (hierarchy docs dated 2026-10-01 [R]). Such code goes
in an `oc.jvm.library` module with **no project dependencies**, consumed by both `androidMain` and `desktopMain`. The
day-1 instance is `:core:net-okhttp`. `/proc`, process-tree and orphan-reaper code stays in `:android:linux` until
desktop work starts; then it moves to a new `:engine:runtime-jvm`.

### 7.5 Resources

- **Copy:** `:ui:words` CMP resources are the single source of truth (`values/`, `values-ar/` and the other five
  languages; `strings_<feature>.xml`; `%1$s` placeholders; `<plurals>`).
  - The 6 ICU `select` messages become code.
  - The 1,018 orphan Arabic keys are dropped [R].
- **Android `res`:** a `:androidApp` task copies `strings_os.xml` from every values folder into generated `res`. That
  file holds the app label, shortcut labels, widget descriptions, notification channel names and tile labels. Android
  lint `MissingTranslation` runs there. `:tools:strings` checks every other key.
- **Non-composable reads** (notifications, widgets, services) use the suspend `getString(Res.string.x)`.
- **Icons and drawables** used by the UI are kit `composeResources`. Notification small icons, widget layouts,
  `previewImage` PNGs, `shortcuts.xml` and launcher icons stay Android `res` in `:androidApp` / `:android:*`.

### 7.6 Running ahead of CMP on Android (`JETPACK_AHEAD.md`)

- `androidMain` of `:ui:kit*` and `:ui:shell` may declare a newer `androidx.*` artifact than CMP maps to, and use its
  new APIs behind the same common kit API with a common fallback. Each use gets a row in
  `ui/kit/JETPACK_AHEAD.md`: the API, the androidx version, the reason, and the CMP version that lets it move to
  `commonMain`.
- `:androidApp` may raise the androidx Compose, Material 3 or Navigation 3 versions above CMP's mapping to pick up a
  *fix*, as long as the version is stable. Each such bump runs T2 plus the Macrobenchmark journeys. AndroidX keeps binary
  compatibility within a major version; every bump still proves it [U per bump].
- Known entries on day 1: `imeNestedScroll`; Android 14 non-linear font scaling for the 1.3× frame-text cap;
  `RenderEffect` frost on API 31+; Nav3 1.3 deferred two-stage transitions and Compose 1.13 selection fixes (when
  Google ships them stable); `preferredFrameRate`, if the CMP fork does not export it [U].

---

## 8. Core library choices and versions

All checked on 2026-11-01 against Maven Central or Google Maven metadata [V]. Pin them in `gradle/libs.versions.toml`.
Upgrades happen only on the named trigger. Kotlin, the Compose compiler, Metro and KSP move together, done by the
`build-logic` owner.

| Area | Choice | Pin | Move when |
|---|---|---|---|
| Language | Kotlin (+ Compose compiler plugin) | **2.4.21** | 2.5.0 (planned Dec 2026), only once Metro and CMP declare support |
| Build | AGP / Gradle / JDK | **9.3.3 / 9.7.1 / 17** (JDK 21 for the Robolectric test JVM) | AGP 9.5.x and Gradle 9.8.x together with Kotlin 2.5 |
| SDK levels | compileSdk / targetSdk / minSdk | **37 / 36 / 26** (compileSdk 37.1 if Compose 1.13 requires it) | targetSdk 37 in its own slice after the switch |
| UI toolkit | Compose Multiplatform (runtime, foundation, ui, animation, resources, ui-test) | **1.13.0-beta01**; fallback 1.12.2 (demo-proven) | 1.13.0 stable (projected about mid-Dec 2026 [U]) |
| Material | CMP `material3` (= Jetpack M3 1.5.0-beta01 on Android); kit modules only | **1.13.0-beta01** | stable with Jetpack M3 1.5.0 |
| Adaptive | CMP `material3-adaptive` + `adaptive-navigation3` | **1.3.0** | follow CMP |
| Navigation | Navigation 3 (CMP `navigation3-ui`); `navigationevent-compose` | **1.2.0; 1.1.0** | Android may run Jetpack 1.3.x once it is stable (§7.6) |
| Lifecycle | CMP lifecycle (viewmodel, viewmodel-compose, viewmodel-navigation3, runtime-compose); savedstate | **2.11.0; 1.4.0** | follow CMP |
| Android shell | activity-compose | **1.13.0** | follow AndroidX |
| DI | Metro + `metrox-viewmodel-compose` | **1.5.1** | in a pair with Kotlin. Plan B: Koin 4.2.2 + compiler plugin 1.2.1 |
| Coroutines | kotlinx.coroutines (+ `-swing` on desktop, `-test`) | **1.11.0** | — |
| Serialization | kotlinx.serialization, with `exceptionsWithDebugInfo = false` | **1.12.0** | — |
| Collections | kotlinx-collections-immutable | **0.5.2** | — |
| Time / IO | kotlinx-datetime / kotlinx-io | **0.8.0 / 0.9.2** | — |
| HTTP | Ktor client (core, okhttp, websockets; darwin later); no ContentNegotiation | **3.6.1** | — |
| HTTP engine | OkHttp (inside `:core:net-okhttp`) | **5.5.1** | follow Ktor's requirement |
| Database | Room 3 (`androidx.room3`) + `sqlite-bundled` + KSP; fallback SQLDelight 2.4.1 | **3.0.3 + 2.7.1 + KSP 2.3.13** | when `:core:db` starts (§9.8) |
| Key-value (desktop/iOS only) | DataStore | **1.3.0** | desktop phase |
| Logging | Kermit behind `AppLog`, `RedactingWriter` first, then a ring buffer | **2.2.0** | — |
| Tracing | androidx.tracing | **2.0.5** | — |
| Markdown | `org.jetbrains:markdown` (`StreamingMarkdownFile`); our own kit renderers | **0.7.18** | — |
| Code highlighting | SyntaxMP behind `CodeTokenizer`; benchmark against Highlights 1.1.0 | **0.3.0** | swap if the slice benchmark fails |
| Images | Coil 3 (`coil-compose`, `coil-svg`, `coil-gif`) | **3.6.3** | — |
| Zoom | Telephoto `zoomable` | **0.19.0** | — |
| QR | qrose (draw); CameraX viewfinder + zxing-cpp (scan) | **1.3.1; CameraX 1.6.2; zxing-cpp 3.1.1** | CameraX 1.7 when stable |
| Pickers | platform contracts (Photo Picker, SAF, TakePicture) in `:android:platform`; FileKit 0.17.0 only for desktop/iOS later | activity 1.13.0 | — |
| Speech | sherpa-onnx Kotlin API, vendored hash-pinned AAR, `-keep class com.k2fsa.sherpa.onnx.** { *; }` | **2.0.1** | — |
| Terminal | Termux `terminal-emulator` + `terminal-view` (JitPack, Apache-2.0) | **v0.118.3** | — |
| Android platform | androidx.core (NotificationCompat, Live Updates) / WorkManager / AppFunctions (optional) | **1.19.1 / 2.12.0 / 1.0.0-beta01** | — |
| Unit tests | `kotlin.test`, coroutines-test, Turbine, `ktor-client-mock` | **=Kotlin, 1.11.0, 1.2.1, 3.6.1** | — |
| Architecture tests | Konsist; dependency-analysis; detekt; Kover | **0.18.1; 3.20.1; 1.23.8; 0.9.11** | — |
| Android UI tests | Robolectric (`createComposeRule` v2) | **4.17** | — |
| Screenshots | Roborazzi (+ ComposablePreviewScanner); golden host = the VPS only | **1.77.0 (+0.9.3)** | — |
| Device flows | Maestro CLI (select by visible text); UIAutomator | **2.12.0; 2.4.0** | — |
| Performance | androidx.benchmark (macro + baseline-profile plugin), profileinstaller, Perfetto | **1.5.0, 1.4.1, v59.1** | — |
| Desktop loop (optional) | Compose Hot Reload (desktop only) | **1.2.0** | trial; never required |

---

## 9. Architecture rules every section writer applies

### 9.1 Scopes and state

- **`AppScope`** (process) holds the stores, `Redactor`, diagnostics, `ConnectionRegistry`, `AttentionFeed`,
  `NotificationPolicy`, `ScheduleEngine`, `RuntimeHost`, the status-line combiner, the update notice and teaching.
- **`ConnectionScope`** is a Metro graph extension, one per *live* connection, and several can be alive at once. It
  holds the gateway pair, event stream, session list, selection, approvals, delivery, read state, caches and drafts.
  `close()` stops admission, cancels the stream, drains the writer lanes, then releases.
- A conversation's backend is `ConnectionRegistry.graphFor(conversationRef)`.
- **Android components reach the graph** through `(applicationContext as GraphOwner).appGraph`: services, receivers,
  workers, widget providers, tiles, and the overlay and manage-space activities. `metrox-android` is not used, because
  it needs API 28 [R].
- **Engines own the truth.** They expose `StateFlow`, take `suspend` commands and return `Outcome`. State machines are a
  sealed state plus a pure `reduce(state, event) → Transition(state, effects)`, table-tested in `commonTest`.
- **ViewModels**:
  - One per screen.
  - They `combine` engine flows into one `StateFlow<XUi>`, using Kotlin 2.4 explicit backing fields.
  - They implement `XActions`, whose methods are named after intents (`open`, `approve`, `steer`), never widgets.
  - They hold no business rules: a rule a widget or notification also needs belongs in an engine.
  - They carry no copy strings, only ids, enums and `Failure` categories. User content such as titles and message
    text is fine.
  - No one-shot event channels: "navigate after create" is a state field the UI acknowledges.
- **Screens** are `XScreen(state: XUi, actions: XActions)`.
  - Previews and Roborazzi use canned `XUi` plus `FakeXActions`, so UI work starts the day `contracts/<slice>.md`
    freezes `XUi` and `XActions`.
  - Collect with `collectAsStateWithLifecycle()`.
  - Kit parts take plain parameters and never see a ViewModel.
- **Test tags:** a stable test tag equals the Flutter `ValueKey` string where one existed (`chats-new-chat`,
  `server-switcher-button`, …). Otherwise there is no tag, and tests find UI by visible text.

### 9.2 Composer text

- `TextFieldState` in `:feature:chat-composer` owns the text, selection and IME composition, including keyboard images
  through `contentReceiver`.
- The UI calls `actions.onDraftChanged(text, selectionUtf16)`. Autosave is debounced; `/` and `@` detection runs
  immediately. Mention ranges stay in UTF-16 offsets.
- Sending passes the text and mentions in `actions.send(...)`. Restored drafts seed the field.

### 9.3 Streaming hot path and stability

- `TranscriptEngine` applies each event on its serial dispatcher. It publishes an immutable `TranscriptState` at most
  every 50 ms; the first delta flushes at once. Turns sit in a `PersistentList`.
- The transcript is one `LazyColumn(reverseLayout = true)` flattened to block items, with stable keys and a
  `contentType` per kind. While following, it calls `requestScrollToItem(0)`.
- Markdown re-parses only the unstable tail. Fresh words fade in a draw-phase mask, never with per-frame
  `AnnotatedString` spans. The live block grows on a critically damped spring (stiffness 700). `LineBreak.Simple`.
- `preferredFrameRate(High)` applies only while the block reveals. One `SelectionContainer` covers the transcript, with
  selection off on the live block until the Compose 1.13 fix ships.
- Every `oc.kmp.compose` module gets `compose-stability.conf`, which marks these packages stable:
  `io.github.eslamasabry.oc.core.model.**`, `…oc.core.domain.**`, `…oc.engine.**.state.**`, `…oc.ui.design.**` and
  `kotlinx.collections.immutable.*`.
- Slice-0 proof P4 counts recompositions on the chats list and on a 5,000-part transcript, with and without the config.
- Escalate (give the live part its own `StateFlow<String>`) only if Macrobenchmark shows jank.

### 9.4 Navigation and motion

- **Nav3** with a user-owned back stack: `rememberNavBackStack(SavedStateConfiguration, start)` with the
  `:ui:routes` `SerializersModule`.
- **`AppNavigator`** is the only navigation API that ViewModels see: `open`, `openChat(ref, focusComposer)` (no
  duplicate on top), `land`, `replaceRoot`, `back`.
- Features contribute entry installers with `@ContributesIntoSet`, so there is no central entry list.
- **`EntryRouter`** takes launcher shortcuts, `opencode-mobile://session` and `://team` links, share-in, notification
  taps, widgets, tiles and scheduled-run taps. Each request waits for bootstrap and the migration gate, is consumed
  exactly once, and becomes an `EntryTarget`, which `:ui:shell` maps to routes.
- **Sheets and dialogs are screen state, not routes.** `KitSheetHost` uses the M3 1.5 in-window `BottomSheet`, which
  handles predictive back itself.
- **Scenes:**
  - compact: single pane, no bottom bar (owner, 2026-10-20);
  - medium: rail;
  - expanded: sidebar;
  - list-detail for Settings and Files (and Team projects if ported);
  - "absent, never disabled".
- **Predictive back policy**, implemented once in `:ui:shell` and the kit:
  1. List → chat:
     - Ship option A. The container transform (`sharedBounds`) runs on tap and on button-back. While a back gesture is
       in progress, shared bounds are off and the platform predictive motion runs. There is no 700 ms latch.
     - Option B (Nav3 1.3 + Compose 1.13 deferred two-stage transitions) lives in `:ui:shell` `androidMain` as a
       `JETPACK_AHEAD` item. It becomes the default the week Google marks both stable; it does not wait for CMP.
  2. Settings pages: shared-axis X with system predictive back.
  3. New conversation: slides up, and the gesture closes it.
  4. Reduced motion: a cross-fade or nothing.
- **The root never intercepts Back.** The chat pops only when nothing is lost: its `NavigationBackHandler` is enabled
  while the composer differs from the saved draft.
- **Motion:**
  - `MotionScheme.standard()`, never expressive (no overshoot).
  - Kit duration tokens within 150–250 ms.
  - Compose's animator-scale handling plus the kit's `MotionPolicy` (Full/Calm/Off, Saver, heat).
  - No raw `tween`/`spring` outside the kit.
- **Native feel is AndroidX defaults, never re-implemented:**
  - platform `RippleDrawable` ripple;
  - stretch overscroll on API 31+, glow on 26–30;
  - `LocalHapticFeedback` mapped from `KitHaptic` (send, commit, select, done, longPress);
  - edge-to-edge and IME animation.

### 9.5 Threading

- **Each engine and per-profile store is confined to one serial dispatcher**
  (`dispatchers.default.limitedParallelism(1)`, injected). Heavy pure work goes to `default` and comes back as a value.
- **`WriterLane`**: an admission flag plus a fair `Mutex`, so "drain" means every admitted write finished. Writes use
  `commit()`, never `apply()`.
- **Scopes replace generation counters.** Each connection generation is a child scope, and cancelling it ends stale
  work.
- **Flows:**
  - Durable events are never dropped; volatile deltas may be coalesced.
  - Reconnect reconciles by refetch, never by replay.
  - Every `buffer`/`conflate`/`debounce` has a comment naming its catalog row.
- **Android lifetime:**
  - A short `dataSync` "watching this reply" FGS starts when the app goes to the background with a turn running, and
    stops at settle.
  - The phone server stays `specialUse`.
  - Schedules use AlarmManager + WorkManager (8-minute worker).
  - Nothing assumes unbounded lifetime (6 h of `dataSync` per 24 h).
  - The isolated PDF service is the only extra process.

### 9.6 Errors, crashes and privacy

- **Expected failures are values; exceptions mean bugs or cancellation.** Transport, serialization, Keystore and proot
  errors are translated to the closed `Failure` hierarchy once, in `:core:net`, `:protocol:*`, `:core:data` and
  `:android:*`.
- **No path from server or exception text to a visible label.**
  - `details` is `Redacted`, which only the `Redactor` can construct.
  - The kit's Details fold, next to "Report a problem", is the only place it renders.
  - `:ui:words` maps `Failure` with an exhaustive `when`.
- **Crash handling.** An exception in composition kills the process, because Compose has no `ErrorWidget`. So:
  - derived UI models are built in ViewModels, never inside composition;
  - every root scope has a `SupervisorJob` plus a logging handler;
  - the crash-loop guard (§9.7) opens safe mode.
- **Provider credentials** never reach logs, diagnostics, notifications or test output.
- **Nothing phones home.** PRIVACY.md is rewritten before the switch.

### 9.7 Startup order and the Flutter migration

**`OcApplication.onCreate`** runs on the main thread, under 50 ms on the fast path:

1. Install the uncaught-exception handler. It writes the category-only crash record (`NativeCrashStore`, consent epoch
   kept).
2. Initialize the `Redactor`.
3. Run the crash-loop guard. Three consecutive launches that crash within 10 s of start turn on safe mode (Report a
   problem, Settings, Start fresh), recorded in `files/native/crashloop.json`.
4. **Step 0, only on the first native launch** (no `backup=done` in `files/native/migration.json`): byte-copy
   `shared_prefs/FlutterSharedPreferences.xml`, `FlutterSecureStorage.xml`, `FlutterSecureKeyStorage.xml` and
   `FlutterSecureStorageConfiguration*.xml` to `files/migration/flutter-v57/`, then mark it done. This is the only
   synchronous migration work. Its duration is measured in slice 0.
5. Build `AndroidAppGraph`. Stores are lazy and await `MigrationGate`.
6. Start `MigrationRunner` on IO. If `native.migration.v1` is complete, the gate opens at once.
7. Warm the prefs file on IO.

**After `onCreate`:**

- `MainActivity` draws the kit "Updating your app…" or "Opening…" state on the first frame. After 8 s it offers Try
  again; on failure it offers Report a problem and Start fresh.
- `EntryRouter` holds every launch intent until the gate opens.
- Receivers, workers, widget providers, tiles and services await the gate inside their own coroutines
  (`goAsync`, `CoroutineWorker`), never on the main thread.

**`MigrationRunner` steps** run asynchronously. Each step writes its own idempotent marker, and source files are never
deleted:

1. Count stray DataStore and default-prefs files (count only).
2. Decrypt the `flutter_secure_storage` v11 entries natively: the AES-128 key is unwrapped with RSA-OAEP (MGF1-SHA1)
   by the package-derived AndroidKeyStore alias, then the AES-GCM values are decrypted.
   - Register each secret with the `Redactor` **before** any log line.
   - Re-encrypt it into `SecretStore` and verify by read-back.
   - A failure sets that profile's "enter the password again" flag and continues. It never aborts and is never
     treated as an empty password.
3. Adopt `FlutterSharedPreferences.xml` **in place** as the Android `KeyValueStore`. Keep the `flutter.` prefix and
   every encoding: Long ints, both list encodings, the double prefix.
   - Run the idempotent Dart-side migrations (retired keys, notification-rules merge, the OC1 → OC2 profile flag).
   - Keep the canonical Dart `jsonEncode` fingerprints for schedules and widget targets, or recompute them exactly.
4. Verify the three built-in Linux restore-authority keys.
5. Run a count-and-hash audit into diagnostics.
6. Set `native.migration.v1`.

**Files are never moved:** `files/linux/**` (rootfs), `projects/`, `voice_models/`, `draft-attachments-v1/`,
`prompt-photo-recovery-v1/`, `diagnostics/`, `oc.teamEngine.<id>/`, the crash files, and `app_flutter/`, which is read
once.

### 9.8 Persistence

- **`KeyValueStore`** is the adopted Flutter prefs file on Android, written through per-profile `WriterLane`s.
- **`SecretStore`** is a Keystore AES-256-GCM key (non-exportable), with AAD = the entry name, in its own prefs file.
- **`BlobStore`** is files where they are today.
- **Room `oc.db`** (offline queue, drafts index, stash, receipts, first-paint caches):
  - **Default:** switch+1, behind the repository interfaces, which do not change.
  - **Trigger:** in slice 0 (P5), measure a release-build cold start on the API 37 emulator (Macrobenchmark, `COLD`,
    10 runs) with the prefs file seeded at 100 KB, and again at the larger of 10 MB or the owner's real size. If the
    median gap exceeds 50 ms, or a widget update or worker start spends more than 100 ms loading prefs, Room moves
    into the switch release.
  - **Either way:**
    - every table has `profile_id`;
    - the per-table `ProfileScope` registry extends today's `oc.<what>.<profileId>` sweep;
    - the import records counts and a SHA-256 per blob inside one transaction;
    - source keys stay for one release.
- **Deletion** stays a transaction run backwards. `ConnectionController.deleteProfileAndLocalData` semantics move to
  `:engine:profiles`.

### 9.9 Identity and release

- **Unchanged:** the package `io.github.eslamasabry.opencode_mobile` and the signing lineage of the population being
  updated (§12 D2).
- **versionCode** moves to a new base of **10000**, above every installed local build (4300–4511), and never goes
  back. The ABI splits (arm64-v8a, x86_64, universal) share one versionCode.
- **Component freeze.** The merged-manifest component FQCNs, WorkManager worker class names, notification channel ids,
  shortcut ids and the `opencode-mobile://` scheme must equal a list extracted once from the Flutter 2.1.0+57 release
  APK (`:quality`, run at every T2).
- **All authorities use `${applicationId}`,** so the `.native` preview flavor installs beside the main app.
- **Shorebird ends at the switch.** The native `AppUpdater` ships in the switch release itself: GitHub
  `releases/latest`, `SHA256SUMS`, a same-signer check, `PackageInstaller` and `REQUEST_INSTALL_PACKAGES`. The in-app
  update notice keeps its lowest-priority slot.
- **R8** uses the AGP 9.3 `optimization {}` DSL with `keepRules/*.keep`. KMP modules publish consumer keep rules,
  because the convention plugin turns that on. `analyzeReleaseR8Config` runs at every T2.
- **Kept build guards:** `useLegacyPackaging` for the proot executables, 16 KB alignment, the size gate on the arm64
  APK, and the engine-hash and strip exclusions for `libaiteam_*` if AI Team ships.

---

## 10. Switch gate and delivery rules that shape the architecture

### 10.1 The parity rule ("nothing lost")

The in-place update ships only when all four are true:

1. Every in-scope catalog row has a passing native test.
2. The Maestro parity flows (39 flows over 240 device-flow rows, written first against the Flutter release APK) are
   green on the native **release** build.
3. The upgrade journey (§10.2) passes.
4. The owner's phone test passes.

Anything left for later must be named and approved by the owner (§12 D6). Before the switch, the owner uses the
side-by-side **preview flavor** (`io.github.eslamasabry.opencode_mobile.native`, same local key, own data, no
migration).

### 10.2 The upgrade journey (T3, before any owner APK that touches migration)

1. Install a Flutter 2.1.0+57 build on a rootable emulator. Use the local-key build of that commit for the owner
   lineage, and the public build for the public lineage.
2. Seed it with secrets, a large offline queue, placed widgets, both tiles, a pinned shortcut, a WorkManager schedule, a
   notification-channel customisation and a built-in Linux server with restore authority.
3. `adb install -r` the native APK (versionCode ≥ 10000, same signer).
4. Check with Maestro flows plus `adb root` file checks.
5. Reboot and check the restore.
6. Repeat with the migration interrupted at each step. A rerun must be a no-op.

The slice-0 version of this journey runs while the classes live in library modules.

### 10.3 How agents work in this graph

- **`MODULE_OWNERS`** names one owner per S module. Kit, kit-chat and kit-content work in parallel under one design
  authority.
- **Freeze before fan-out (end of slice 0):** `:core:model` types, `Outcome`/`Failure` categories, gateway interfaces and
  `ServerCapabilities`, store interfaces, `AppDispatchers`/`Clock`, route keys for slices 1–3, `KitPlatform`, and the
  first 20 kit part signatures (from `docs/ux-system/kit-api/*.md`).
- **Slice recipe:**
  1. `contracts/<slice>.md` names the interfaces, `XUi`/`XActions`, `Failure` categories, catalog rows, and **what the
     slice deletes**.
  2. A test-writer lane, one slice ahead, turns the rows into failing `commonTest`s.
  3. A builder makes them green. Codex builds core, protocol and engine with the contract. Claude builds kit and
     features from it. Opus takes transcript, migration, the runtime split and the navigation spikes.
  4. The coordinator merges.
  5. Reviewers never launch test processes.
- **Test tiers:**
  - T0: one test class or catalog row, one target.
  - T1: touched modules' `desktopTest` plus Android host tests plus a Roborazzi verify, on the VPS.
  - T2: all JVM tests, Konsist, lint, `assembleRelease`, the R8 analyzer, the size gate and the component freeze, on the
    VPS.
  - T3: emulator tests, Maestro parity flows on the release build, and the upgrade journey, on the PC with one emulator
    lock holder.
  - T4: Macrobenchmark and profile regeneration.
- **Machines:**
  - PC `gradle.properties`: `-Xmx2g`, 2 workers, one Gradle build at a time.
  - VPS: `-Xmx3g` or more.
  - Configuration and build cache on.
  - Agents run target-specific tasks only, never `build`.
  - Full and release builds run on the VPS. Midnight–2 am is VPS-only.

---

## 11. What slice 0 must prove (each at most about two days, each with a fallback)

| # | Proof | Acceptance | Fallback |
|---|---|---|---|
| P1 | Skeleton: `build-logic`, catalog, ~12 modules on CMP 1.13.0-beta01 for android + desktop; Metro graph with a `ConnectionScope` extension, `metrox-viewmodel-compose`, Nav3 per-entry ViewModels, contributed entry installers; nightly iOS klib job | assembles; graph smoke passes; klib job passes for `core`/`engine` | CMP 1.12.2; Koin; klib job only for non-UI modules |
| P2 | Predictive back: list → chat container transform with option A on the emulator, RTL on both edges, fling, reduced motion; spike option B on Jetpack Nav3 1.3-alpha + Compose 1.13-beta in `:ui:shell` `androidMain` | the six motion-native §4.4 checks; Android resolves to Google's nav3 binary | plain shared-axis for list → chat until B |
| P3 | Transcript: a recorded 5,000-part stream through `TranscriptEngine` and block `LazyColumn` with the draw-phase fade | publish counts match the Flutter fixture; no frame over budget in a release-build Macrobenchmark | per-part `StateFlow<String>` |
| P4 | Stability: recomposition counts on the chats list and transcript with and without `compose-stability.conf` | the config removes the extra recompositions | widen the config, or move state types into Compose-compiled modules |
| P5 | Migration and identity: install over Flutter 2.1.0+57 (§10.2, short form); adopt prefs; decrypt one v11 secret; rootfs untouched; widgets, tiles, pinned shortcut, schedule, channel settings and FQCNs survive while classes live in library modules; time step 0; seed and measure the Room trigger (§9.8) | all survive; step 0 under 1 s on the emulator for 100 MB; trigger numbers recorded | move step 0 into the async runner, with the gate covering every prefs reader |
| P6 | Strings: ARB → CMP XML; per-app language (Android 13 locale config) switches both a screen and a notification at runtime; Arabic plurals; multiple `strings_<feature>.xml` per values folder | both switch; plurals right | own `LocalResourceEnvironment` override; one file per language |
| P7 | Kit seams: `KitTappable` (ripple + haptic), `KitFrameText` 1.3× cap with non-linear scaling at 200 %, `KitSheetHost`, frost on API 31+; Roborazzi goldens on the VPS (light/dark, en/ar); Robolectric on JDK 21 at SDK 37; desktop `runComposeUiTest` headless on the VPS (or `xvfb-run`) | one kit part tested three ways; matches Flutter on the emulator | linear cap; Robolectric only |
| P8 | Build cost: clean and incremental times and Gradle memory, android-only vs android + desktop, on the PC and the VPS | numbers recorded; per-change vs nightly desktop compile decided; host caps set | desktop compile nightly only |
| P9 | Allowlist and seams: the Konsist rules fail on a planted `material3`, `LocalContext` and raw `clickable` in a feature; the `PlatformViewHandle` terminal seam renders a Termux `TerminalView` | failures caught; terminal renders | — |
| P10 | Throughput: lines and catalog rows per agent-week from slices 0–1 | numbers feed the schedule re-plan at M0 | — |

---

## 12. Follow-up decisions for the owner

Each has a recommended default. Work proceeds on the default until the owner answers.

| # | Decision | Recommended default |
|---|---|---|
| D1 | **Confirm the platform bet.** Are iOS or Linux desktop wanted within about a year? A won by 0.5 points on the strength of that intent. If the honest answer is "probably never", C becomes the better plan, and switching costs almost nothing before slice 1 (convention plugins change; UI modules become `com.android.library`). | Keep A |
| D2 | **Signing.** Owner builds stay on the local key `1DE5BF08…D60C`; public GitHub releases stay on `842284B2…1C` through CI, exactly as today. The installed signer is checked with `apksigner` before every delivery. Never rotate or substitute a signer. | Keep both lineages |
| D3 | **versionCode base 10000** and switch version name 3.0.0. | Yes |
| D4 | **Bridge release:** one last Flutter release that carries the native-style installer, so Flutter users can self-update into native. It is a native change, so it needs a new Shorebird release, not a patch. The alternative is a manual download. | Ship the bridge release |
| D5 | **Drop "Press back again to exit"**, so predictive back-to-home works on the root. | Drop it |
| D6 | **What may follow the switch:** AI Team, FactsBook, Gas City, A2A external agents (no owner use seen). | AI Team and FactsBook after the switch; A2A dropped. Their data stays untouched on disk. |
| D7 | **Termux:** keep, migrate-out only, or drop (builtin-linux Q1). | Migrate-out only |
| D8 | **Phone agents path:** Paseo on the phone vs the OpenCode 2 built-in Claude Agent provider (agents-backends 08–30). | Paseo (today's daily path), revisited after the switch |
| D9 | **Room timing:** accept the measured trigger in §9.8. | Switch+1 unless triggered |
| D10 | **Frost** on the composer and chat header (2026-10-30 exception): `RenderEffect` on API 31+, solid below that and under reduced effects. | Keep it |
| D11 | **Live backgrounds (20 looks) and illustration scenes.** | Port after the switch, or drop if nobody misses them |
| D12 | **Pre-release libraries at the switch:** no alpha artifact in the release classpath. A beta is allowed only for Material 3 1.5 inside the kit if Google has not finished it by then. | As stated |
| D13 | **iOS phase prerequisites:** a Mac or macOS CI, the Apple Developer Program, and a push relay for "reply finished". Also: iOS system Liquid Glass chrome vs the "no glass" rule, and an iOS design pass over the top 10 screens before iOS UI starts. Swift bridge via SKIE or Obj-C export for WidgetKit, App Intents and the share extension. | Decide when iOS is funded |
| D14 | **Desktop expectations:** Compose Desktop has no Linux screen-reader support and renders through XWayland. The frozen Flutter Linux build remains the desktop client until native desktop ships. | Accept |
| D15 | **A second build VPS** to raise concurrency from 5–6 to about 9–10 agents. | Yes, if budget allows |
| D16 | **In-flight Flutter branches** (services, vitals, backup, app updates, AppFunctions): land each before the freeze, or port it straight from its branch. | Decide per branch before slice 3 |
| D17 | **Web dropped; Windows out** of the native plan. | Confirm |
| D18 | **Developer verification:** register the package and both signer fingerprints in the Android Developer Console before the global rollout ("2027 and beyond"), or the in-app updater breaks. | Register during the switch phase |

---

## 13. Sources

- Proposals: `../design/proposal-A-shared-ui.md`, `../design/proposal-B-native-ui-per-platform.md`,
  `../design/proposal-C-android-first-kmp-ready.md`, and the three judge reports passed to this step.
- Research: `../research/kmp-cmp-status.md` (§0, §1, §3–§8, §11–§13), `architecture-stacks.md` (§0, §2, §3),
  `network-data.md` (§0, §1, §8–§9), `testing-perf.md` (§0, §1), `media.md` (§0), `rendering-chat.md` (§0),
  `motion-native.md` (§0, §4.4), `android-surfaces.md` (§0), `genui.md` (§0).
- Maps: `../map/packages-build.md` (signers §0.3, versionCode §0.4), `protocols.md` (OpenCode 1 removed),
  `agents-backends.md` (pins, A2A stance), `builtin-linux.md`, `ui-kit.md`, `screens-nav.md`.
- Catalog: `../catalog/BEHAVIORS.md`, `behaviors.jsonl`, `e2e-flows.md`.
- Repo: `/home/eslam/Storage/Code/wt/oc_app-batch1/docs/ux-system/kit-v2.md` §9 (kit-only allowlist), `AGENTS.md`.
- Demo: `/home/eslam/Storage/Code/kmp-demo` (`shared/src/commonMain`, `shared/src/androidMain`).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `pc-capacity-ceiling`, `codex-backend-claude-frontend`,
  `no-heavy-workflows`, `subagent-model-choice`, `quiet-hours-vps-only`, `no-glass-rule`, `android-native-feel`,
  `text-scale-cap`, `app-updates-two-layers`.
- Checked today [V], 2026-11-01:
  - Maven Central / Google Maven `maven-metadata.xml` for every version in §8. Examples: Kotlin plugin latest
    2.5.0-Beta2 (stable line 2.4.21); CMP runtime and material3 1.13.0-beta01; CMP `navigation3-ui` 1.2.0; Jetpack
    Compose UI 1.13.0-beta02; Jetpack M3 1.5.0-beta02; Jetpack Nav3 1.3.0-alpha03; Metro 1.5.1; Ktor 3.6.1;
    OkHttp 5.5.1; serialization 1.12.0; KSP 2.3.13; Room 3 3.0.3 stable; Konsist 0.18.1; Roborazzi 1.77.0;
    Robolectric 4.17; androidx.core 1.19.1; WorkManager 2.12.0; navigationevent 1.2.0.
  - `https://services.gradle.org/versions/all`: Gradle 9.7.1 exists.
  - `navigation3-ui-android-1.2.0.module`: depends on `androidx.navigation3:navigation3-ui:1.2.0`; the AAR is 6,179
    bytes. `material3-android-1.13.0-beta01.aar` is 6,486 bytes. On Android these are shims over Google's
    artifacts.

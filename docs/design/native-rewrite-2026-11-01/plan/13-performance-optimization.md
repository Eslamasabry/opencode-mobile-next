# 13. Performance and optimization: budgets, measurement and gates

## For the owner, in plain words

1. The new app gets hard number targets: it opens in about 0.3 s, misses at most 1 frame in 100 while a reply streams, downloads at under 22 MB (we aim for 20), and uses almost nothing while hidden.
2. Every number is measured the same way each time, on the PC's emulator for comparisons and on your phone only when you say yes, and a build that gets more than 10 % worse is stopped before it reaches you.
3. The heavy parts stay where they work best: voice stays inside the app (about 10 MB), the phone's Linux downloads itself as today, and the AI Team engine adds nothing until AI Team comes back.
4. The lessons from your hot phone are built in from day one: agents slow down before the phone gets hot, nothing wakes the phone every few seconds when the app is hidden, and the app stops dialling out when there is no network.
5. Four small choices need you now (§19: 32-bit phones, where the AI Team engine lives later, an optional performance recorder on your phone, two measuring sessions on your phone); work goes ahead on the recommended answers.

---

Written 2026-11-01. This section builds on `01-architecture-decision.md` (the ADR, binding) and on `02-principles-scope.md`
§4, whose numbers S-5 to S-21 are the switch gate. 02 §4 says: "section 13 owns the full budget tables … and may only
tighten it." Every number here is equal to or stricter than 02 §4. Where this file and the ADR differ, the ADR wins.

Tags: **[V]** checked today against a primary source; **[R]** read in the repo, a map, a research file, a sibling plan
section or a memory file; **[E]** my estimate, replaced by the first measured value plus a margin and never loosened
after that (02 §4.2 rule 3); **[U]** unverified, settled by the proof named next to it.

---

## 0. How to use this section

| Reader | Read |
|---|---|
| Perf lead (the T4 lane holder, 02 §8) | everything; you own §3, §15 and the files in §0.2 |
| Feature and kit agents | §2 (your area's rows), §5, §6, §11 (if your code has a timer), §18 (brief lines) |
| Backend agents (Codex) | §2.6, §11, §12, §14 |
| Runtime agents (`:engine:runtime`, `:android:linux`) | §2.7, §11, §13 |
| Coordinator | §15 (gates), §17 (work packages), §19 (decisions and amendments) |

### 0.1 Budget ids and test names

- Every budget has an id **`PB-NN`** (performance budget). The id never changes and is never reused.
- A budget that equals a 02 §4 number names it (`= S-9`); a budget that tightens one says `tightens S-x`.
- A test that proves a budget starts its name with the id, the same way catalog tests start with `B-…`:
  ``fun `PB-42 no hidden-idle timer fires faster than 60 s`() = runTest { … }``.
  Instrumented and Macrobenchmark tests use underscores (`PB_11_streaming_frames`) for the DEX reason in 02 §6.1.
- When a catalog row covers the same behaviour, the test name lists both (`` `B-CHAT-096 PB-18 linear merge`() ``).
- `PB-…` tests are **new** budget tests written from §2's tables. They are not ports of Flutter widget tests: the owner
  said the widget tests come later ("7400 for widgets can come later"), and nothing in this section schedules that
  work. Where a Flutter test held the same number (for example `perf_catalog_budget_test.dart`), it is cited only as
  the source of the number. Six Flutter perf files are `testWidgets` with fake clocks and so are absent from the
  6,663-test logic catalog (`perf_catalog_budget_test`, `perf_builtin_pollers_test`, `perf_chat_test`,
  `perf_startup_test`, `perf_startup_connection_test`, `perf_profile_load_test`); their numbers are extracted here as
  JVM budgets (PB-03c, PB-49, PB-58, PB-59 and the runtime rows of §11.3), without porting the widget tests.
- Coordinator amendment **A1** (§19.2): Konsist rule 12 (ADR §6.3) accepts `PB-\d{2}[a-z]?` (for example `PB-42`, `PB-01e`) as well as catalog ids.
- Journeys have ids **`PJ-NN`** (§3.4) and scripted runs **`PR-NN`** (§3.5). Sections 08 and 09 numbered their
  journeys J1–J8 independently; §3.4 maps both onto one registry, which this section owns (08 §11 unit M6 names §13
  as owner of `:android:benchmark`).

### 0.2 Files this section's owner holds (single owner: the perf lead, recorded in `MODULE_OWNERS`)

| Path (under `native/`) | What |
|---|---|
| `android/benchmark/**` | the `:android:benchmark` `com.android.test` module: journeys, profile generator, seed helpers |
| `perf/budgets.json` | machine-readable copy of §2 (ids, targets, device, gate tier); the scripts read it |
| `perf/size-budget.json` | §8.2 component sub-budgets |
| `perf/sensitive-paths.txt` | §15.3: paths whose change requires a T4 subset before merge |
| `perf/last-green.json` | pointer to the last green T4 result per device (coordinator updates it at merge) |
| `perf/results/<yyyy-mm-dd>-<sha>/` | raw JSON + `summary.md` of every T4 and PR run |
| `tool/perf/*` | `t4.sh` helpers: `oc.pbtxt`, `analyze.py`, `compare.py`, `check_apk_size.py`, `check_compose_reports.py`, `check_startup_dex.py`, `hidden_run.sh`, `phone_session.sh`, `batterystats.py`, `restore_device.sh` (§3.7 rule 7), `timer_inventory.py` (§11.3), `make_perf_data.py` (§3.8), `sql/*.sql` |
| `perf/timer-inventory.csv` | §11.3: every repeating job of the Flutter reference, generated by `timer_inventory.py`, one row per job with its native `TimerSpec` or an approved "dropped" line |
| `perf/fixtures.json` | §3.8: SHA-256 and storage path of the FX-OC2-PERF data archive |
| `config/compose-unstable-allowlist.txt` | §6.3 shrink-only list |
| `config/ambient-loops.txt` | §5.6 allowlist of files that may start an infinite transition or a UI clock (`KitClock.kt`, §5.4 R-F6) |
| `androidApp/src/main/generated/baselineProfiles/{baseline-prof.txt,startup-prof.txt}` | §10; regenerated only by the perf lead (two files inside the coordinator-owned `:androidApp`, delegated in `MODULE_OWNERS`). This is the path the baseline-profile plugin reads with `mergeIntoMain`; 16 RB-4 names it too after amendment A15 |

The perf lead is a Sonnet agent for scripts and measurement. Fixes found by measurement go to the owner of the module
that is slow (a perf lead never edits another owner's module; ADR §10.3, memory `same-agent-analyzes-and-builds`).
Hot-path rework in `:engine:transcript`, `:ui:kit-chat` and `:core:markdown` goes to Opus (ADR §10.3).

---

## 1. Inputs and the constraints that shape every budget

| Input | What it fixes here |
|---|---|
| ADR §9.3 | 50 ms transcript publish, first delta at once; one `LazyColumn(reverseLayout = true)` of block items; draw-phase word fade; `preferredFrameRate(High)` only while a block reveals; `compose-stability.conf` |
| ADR §9.5 | serial dispatcher per engine; short `dataSync` reply watch; phone server `specialUse`; nothing assumes unbounded lifetime; the isolated PDF service is the only extra process |
| ADR §9.7 | `OcApplication.onCreate` ≤ 50 ms on **every** path, first native launch included; no migration work on the main thread (step 0 runs on the `StartupFence` thread, 05 §7.1) |
| ADR §9.8 | the Room trigger (P5): prefs 100 KB vs 10 MB cold-start gap > 50 ms, or a background start spending > 100 ms on prefs, moves Room into the switch release |
| ADR §9.9 | ABI splits arm64-v8a, x86_64, universal with **one** versionCode; R8 `optimization {}` DSL; `analyzeReleaseR8Config` at every T2; `useLegacyPackaging`, 16 KB alignment, the size gate on the arm64 APK |
| ADR §10.3, 02 §8 | tiers T0–T4 and their scripts; T4 = `native/tool/t4.sh` on the PC emulator; the phone only with the owner's OK |
| ADR §8 | pins: androidx.benchmark 1.5.0, baseline-profile plugin 1.5.0, profileinstaller 1.4.1, androidx.tracing 2.0.5, Perfetto v59.1, UIAutomator 2.4.0 |
| 02 §4 | S-5 to S-21: the switch gate; 02 §4.1: Flutter baseline first; 02 §4.2: misses block the switch; only the owner relaxes a number |
| 08 §8 | 120 Hz per-interaction UI-thread targets, frame-rate votes, adaptive refresh rule "never below 60" |
| 09 §14 | streaming counter tests, transcript memory bounds (§14.4), hidden rules (§14.5), stream fixtures `perf:stream-6000`, `perf:history-5000`, `perf:tools-200` |
| 10 §2.5, M2, M4, M9, §18 | media size table (≤ 13 MB), warm recognizer released 20 s after recording, verified stamp instead of start-up re-hash, one Coil loader without disk cache; 10 §18's media budgets are imported as §2.10 |
| 11 CR11-5, §9 | `DeviceFacts.thermalHeadroom` (polled ≤ every 30 s, only while collected), `battery`, `appVisible`; `ProcessHost.setAgentPriority`; FGS table |
| Perf audit 2026-11-01 (`Storage2/wt/perf-audit-2026-11-01/REPORT.md`) | the Flutter lessons in §16 |
| The demo (`/home/eslam/Storage/Code/kmp-demo`) | release APK 2,459,429 bytes [R `androidApp-release.apk`]; cold start ≈ 0.25 s in the owner's side-by-side (02 S-5 baseline) with **only library-shipped profiles** (merged ART profile 10,101 lines from Compose and friends; no app-generated profile) [R `build/intermediates/combined_art_profile`] |

Machines (memory `build-vps`, `pc-capacity-ceiling`, `quiet-hours-vps-only`, `shared-emulator-contention`) [R]:

- **VPS** (`$OC_VPS_HOST`): 8 vCPU, 23 GB, **no KVM**, so no emulator. Runs builds, JVM tests, R8, size gates and JVM
  microbenchmarks.
- **PC**: 8 CPU, 16 GB, one emulator `OC_API37` (android-37.0 google_apis x86_64). Runs T3 and T4 only, one lock
  holder at a time. T4 builds nothing on the PC: it installs APKs the VPS built (§3.10), so no Gradle runs during
  T4. Quiet hours follow the current memory `quiet-hours-vps-only` (the owner lifted the 00:00–02:00 rule on
  2026-11-01 "until they say otherwise"); `t4.sh` reads a configurable quiet window that defaults to off (§3.7
  rule 8). Long runs are launched detached, because long background harness tasks get SIGKILLed on this PC (memory
  `background-task-killer`; §3.7 rule 7). The low-end subset (§3.7 rule 9) uses 14's `OCN_26` (API 26, 2 GB).
- **NX721J** (owner's phone, Android 16, 16 GB, 120 Hz panel per 02 §4): absolute numbers, only with the owner's OK.
  Never a screen capture (`no-owner-phone-screenshots`); app logcat is silenced (`phone-logcat-silenced`), so field
  numbers come from the in-app recorder (§3.6).

---

## 2. Budget tables

### 2.1 How to read the tables

- **Device.** "Phone" = NX721J, absolute. "Emu" = `OC_API37`, relative: compared with the same-day Flutter reference
  (R, 02 §6.2) and with `perf/last-green.json`. "JVM" = a `commonTest` on the VPS (deterministic counts, never wall
  clock). "VPS-bench" = a JVM microbenchmark (§3.9), indicative only.
- **Build.** Every device number comes from the minified, non-debuggable, profileable `stableBenchmark` build (§3.2),
  which equals `stableRelease` except for the `.bench` suffix, debug signing and the `src/benchmark/` source set (seed
  receiver, `<profileable>`, `runtime-tracing` pinned to the exact Compose runtime the release resolves). A debug
  build proves nothing (02 §4). The only exception is PJ-01f, which measures Flutter R itself (§3.4).
- **Gate.** The tier where a miss fails (§15). "Switch" means it is also part of the switch decision (02 §4.2).
- **Regression rule (emulator).** Each metric has a direction and a threshold kind in `perf/budgets.json`
  (§3.7 rule 4): relative +10 % for positive, lower-is-better metrics (TTID, CPU ms, PSS, `frameDurationCpuMs`),
  absolute +0.5 ms for `frameOverrunMs` (mostly negative, so a percentage of it means nothing). A repeated journey
  regresses when its median is worse than last green by more than the threshold **and** its p25 is worse than the
  last-green median. Single-shot scripted runs are compared against the budget only. After the switch, the same rule
  applies release to release on the phone (02 §4.2 rule 4).

### 2.2 Start-up

| Id | What | Target | Device | Method (§3) | Gate |
|---|---|---|---|---|---|
| PB-01 | Cold start, time to initial display (TTID) | median **≤ 300 ms**, p90 **≤ 400 ms** (= S-5, adds p90) | Phone | PJ-01 on `….bench`, `StartupTimingMetric`, COLD, `Partial(Require)`, 10 iterations. This replaces S-5's phone method (`am start -W -S` on the stable package), because force-stopping the person's app is forbidden (§3.2 rule 1); coordinator amendment A11 to S-5 | T4 phone session; switch |
| PB-01e | same | **median** TTID ≤ **0.75×** Flutter R's **median** TTID, same AVD, same day, same tool (= S-5): native from PJ-01, Flutter R from PJ-01f (`StartupTimingMetric`, COLD, `CompilationMode.Ignore()`, 10 iterations); and ≤ +10 % vs last green | Emu | PJ-01 + PJ-01f | T4 every slice end |
| PB-01u | First launch after an update, before background dexopt (the switch-day state, and every sideloaded update after Shorebird ends) | cold TTID with `CompilationMode.None()` median **≤ 400 ms** [E] on the phone; on the emulator ≤ +10 % vs last green and ≤ 1.0× Flutter R's PJ-01f median | Phone, Emu | PJ-01 `PB_01u_cold_after_update` (`None()`, COLD, 10 iterations) | T4; switch |
| PB-03c | Secure reads at start | no `flutter_secure_storage`-adopted secret read before `app.first_frame`; secrets are read lazily at connect; 8 secure reads run with bounded overlap **4** and keep their order (numbers from `perf_profile_load_test.dart`) | JVM | `SecureReadOverlapTest` (`:core:data`, virtual time, fake Keystore counting concurrent reads) | T2 |
| PB-02 | Cold start, time to full display (Chats first page drawn from cache, `reportFullyDrawn`) | median **≤ 500 ms** (= S-6), p90 ≤ 650 ms [E] | Phone | PJ-01 `timeToFullDisplayMs` | T4; switch |
| PB-03 | `OcApplication.onCreate`, fast path (migration done) | **≤ 50 ms** (= S-7) phone; ≤ 80 ms emu [E] | Phone, Emu | trace section `app.onCreate`, `TraceSectionMetric(Mode.First)` in PJ-01 | T4 |
| PB-03b | Order before the first frame | no network request and no prefs read on the main thread before `app.first_frame`; only the ADR §9.7 steps run before `setContent` | Emu | PJ-01 trace order check in `analyze.py` (§3.4.3) + StrictMode in `stableQa` (PB-70) | T4, T3 |
| PB-04 | Warm start TTID (process alive, activity re-created) | median **≤ 150 ms** [E] | Phone | PJ-02 `StartupMode.WARM` | T4 phone session |
| PB-05 | Hot start TTID (activity stopped, then resumed) | median **≤ 60 ms** [E] | Phone | PJ-02 `StartupMode.HOT` | T4 phone session |
| PB-06 | Cold entry into a chat (notification, widget, shortcut, `opencode-mobile://session`) through `EntryRouter` | TTID ≤ 350 ms; first transcript from cache ≤ 800 ms; from the phone's own server ≤ 1.2 s [E] | Phone | PJ-03, async section `chat.open_to_first_transcript` (Flutter name kept, §3.4.3) | T4; switch |
| PB-07 | Cold start to first connected server | ≤ 800 ms to a LAN/Tailscale server, ≤ 500 ms to the phone server when it is already running [E] | Emu (FX-OC2-PERF on `10.0.2.2`) | PJ-01: async section `app.toFirstConnected` from `OcApplication.onCreate` to the moment the `app.first_connected` mark is recorded (a mark is an instant and has no duration; §3.4.3) | T4 |
| PB-08 | First native launch (the migration) | "Updating your app…" on the first frame; migration ≤ 3 s at the owner's real data size; step 0 ≤ 1 s per 100 MB (= S-8) | Emu | upgrade journey trace sections (05 §7) | T3; switch |
| PB-09 | Background start (receiver, worker, widget provider, tile) to its first useful line | ≤ 150 ms excluding network and excluding the migration gate's first open; no Compose, font, Coil or UI-graph code loaded; a cold worker start that watches a reply reaches `events.connected` without any frame | Emu | trace section `bg.<component>.ready`; PR-08's Perfetto class-load check (`sql/class_loads.sql`, names retraced with the build's mapping file); PR-08 cold-worker step | T4 |
| PB-10 | Profile effect | cold TTID with `Partial(BaselineProfileMode.Require)` at least **15 % faster** than `CompilationMode.None()` | Emu | PJ-01 both modes | T4 (a miss means the profile is broken) |
| PB-35 | Startup classes in `classes.dex` | ≥ **95 %** of the startup-profile classes live in `classes.dex` | VPS | `check_startup_dex.py` (§10.5) | T2 |

### 2.3 Smoothness

Frame terms: `frameDurationCpuMs` = UI thread + RenderThread CPU time of a frame; `frameOverrunMs` = how late the
frame was against its deadline (negative is early), so it is refresh-rate independent. A "janky frame" (PB-12) is a
SurfaceFlinger FrameTimeline frame of the app's layer whose `jank_type` contains `App Deadline Missed`; other jank
types (display HAL, SurfaceFlinger) are reported but are not ours.

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-11 | Streaming a reply (`perf:stream-6000`, 6,000 words, 3 code fences, glow running) at the live edge | `frameOverrunMs` p50 ≤ −3 ms, p90 ≤ −1 ms, **p95 ≤ 0, p99 ≤ 4 ms** (= S-9, adds p50/p90). `frameDurationCpuMs` p50 ≤ 4.0 ms, p90 ≤ 6.5 ms, p99 ≤ 10 ms at 120 Hz [E] | Phone | PJ-05 `FrameTimingMetric` | T4 phone; switch |
| PB-11e | same | per the comparison rule (§2.1): `frameOverrunMs` p50/p90/p95/p99 each ≤ +0.5 ms vs last green; `frameDurationCpuMs` percentiles ≤ +10 % | Emu | PJ-05 | T4 slice ends 2, 3, 6 and perf-sensitive merges |
| PB-11s | Every Style in both modes | PJ-05, PJ-06 and PJ-13 for each Style (Solid, Soft, Brutal, Paper, Signal) × light/dark: PB-11e and PB-12 hold against **that style's** last green; no style's `frameDurationCpuMs` p90 more than 15 % [E] above Solid-light in the same run (owner rule `stabilize-before-shipping`) | Emu (phone session 2: Solid and the owner's daily style) | PR-13 | T4 slice ends 2 and 6; any merge touching style tokens or surfaces |
| PB-11f | Frost (D10, default Balanced; Solid style only, since other styles hold it solid) | `kit.frost.draw` p95 ≤ 1.0 ms [E]; frost Balanced vs Off on PJ-05/06 adds ≤ 1.0 ms [E] to `frameDurationCpuMs` p90 and keeps PB-12 | Emu, Phone | PR-13 frost A/B | T4 slice ends 2 and 6 |
| PB-11a | Large text and Arabic | PJ-05 with font scale 2.0 and the Arabic locale: PB-12 holds; `md.parseTail` and layout sections within 1.25× [E] of the English 1.0 run | Emu | PR-13 | T4 slice ends 2 and 6 |
| PB-12 | Jank while streaming | **≤ 1 %** janky app-layer frames (= S-12); emulator ≤ +0.5 points vs last green | Phone, Emu | PJ-05 under Perfetto `oc.pbtxt`, `sql/jank_by_layer.sql` | T4; switch |
| PB-13 | Fling a 5,000-part transcript (`perf:history-5000`) | as PB-11 and PB-12 (= S-10) | Phone, Emu | PJ-06 | T4; switch |
| PB-14 | Open a 300-turn chat (warm) | first transcript **≤ 300 ms** (= S-11); no frame overruns by more than one deadline during the open | Phone, Emu | PJ-04 `TraceSectionMetric("chat.open_to_first_transcript")` (the async section carrying the kept Flutter name; S-11's method cell is amended to that name, A19) + `FrameTimingMetric` | T4; switch |
| PB-14b | Open Settings (the owner-visible `settings.open` mark) | ≤ 200 ms [E] (Flutter PERF-1 baseline ≤ 0.2 s [R ui-kit map PERF-1]) | Emu, Phone | PJ-21 | T4 |
| PB-14c | Switch project | ≤ 1.6 s to the new project's first page drawn [E] (Flutter PERF-1 ≤ 1.6 s [R]) | Emu | PJ-22 | T4 |
| PB-15 | Transitions: row → chat transform, predictive back (commit and cancel, both edges), sheet, keyboard, pull to refresh | `frameOverrunMs` p95 ≤ 0; p90 frame duration < 8.3 ms (= S-13); the first frame of row → chat may overrun once (08 §8.1) | Phone, Emu | PJ-07 to PJ-11 | T4; switch |
| PB-16 | List flings: Conversations (500 rows), Settings hub, Files browser (2,000 entries) | as PB-13 | Emu (phone at the switch session) | PJ-13, PJ-14 | T4 |
| PB-16b | Terminal output flood (`yes` for 10 s in the Termux view; 256 KiB back-pressure and 512 KiB replay unchanged) | frames as PB-13; the UI thread is never blocked > 1 deadline; input stays responsive (Ctrl+C reaches the shell ≤ 200 ms [E]) | Emu | PJ-19 | T4 slice 5 on |
| PB-16c | Code reader and diff viewer | open a 2,000-line file ≤ 200 ms to first page [E], fling as PB-13; open a 2,000-line diff ≤ 300 ms [E], fling as PB-13; markdown tables and gen-UI cards in PJ-05's fixture keep PB-11 | Emu | PJ-20 | T4 slice 5 on |
| PB-17 | Recomposition budget | per 50 ms publish while streaming: recomposed kit scopes ≤ 3 (the live block, its turn's live line, the list item wrapper), settled turns **0**; one Conversations row change recomposes **1** row; a status-line change recomposes only the status slot | JVM (Compose host test) + Emu (composition tracing) | `RecompositionBudgetTest` (08 §9.3), `TranscriptRecompositionTest` (09 §14.2), PJ-05 with `runtime-tracing` (§6.4) | T2 (JVM); T4 |
| PB-18 | Hot-path sections (§5.3 table) | each section's p95 within its row of §5.3 | Phone, Emu | `TraceSectionMetric(Mode.Sum/Count)` + `sql/sections.sql` | T4 |
| PB-19 | Nothing moves when nothing happens | on Conversations with nothing running and no waiting state, ≤ 2 frames produced in 10 s (no ambient loop runs outside a waiting state, 08 M8-6) | Emu | PJ-16 FrameTimeline frame count | T4 |
| PB-20 | Input to first visible response | **controls outside scrollable containers** (composer Send, the header gear, sheet buttons): ripple's first frame ≤ 1 frame after the touch-down frame. **Rows inside lists** (Conversations, Settings, any `LazyColumn`): ≤ tap timeout (`ViewConfiguration.getTapTimeout()`, ≈ 100 ms) + 1 frame; that delay is intended platform behaviour (a touch that turns into a scroll must not flash a ripple) and must never be removed (ADR §9.4, memory `android-native-feel`). Send tap → the prompt visible in the transcript ≤ 100 ms (the owner-visible `chat.send_to_bubble` mark); a keystroke in the composer drawn in the next frame | Emu | PJ-10 + Perfetto input slices [U P13-3: which input table trace_processor v59.1 offers] + `chat.send_to_bubble` | T4 |

### 2.4 Memory

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-21 | PSS on the streaming journey with the 5,000-part transcript open | **≤ 0.6×** Flutter R's PSS on the same AVD (= S-20); phone ≤ 250 MB [E] | Emu, Phone | `dumpsys meminfo <pkg>` TOTAL PSS, median of 3, at the end of PJ-15; `MemoryUsageMetric(Max)` | T4; switch |
| PB-22 | PSS on Conversations after 5 min idle, foreground | ≤ 160 MB; Java heap (Dalvik) ≤ 64 MB [E] | Phone | PR-09 | T4 phone |
| PB-23 | PSS hidden, 60 s after `onTrimMemory(UI_HIDDEN)` with no turn running | ≤ 110 MB [E] | Emu | PR-01 sample | T4 |
| PB-24 | No leak | 20 × (open chat, back) + 20 × (open Settings › Appearance, back): Java heap after a forced GC grows ≤ 2 MB and PSS ≤ 8 MB between cycle 5 and cycle 20 | Emu | PR-09 (`am dumpheap` only on failure) | T4 |
| PB-25 | Image memory | decoded bitmaps never larger than their box × density (zoom viewer: long edge ≤ 4,096 px or sub-sampled); Coil memory cache `maxSizePercent(0.15)` (09 §14.4) and ≤ 48 MB; hardware bitmaps except where pixels are read | JVM + Emu | `ImageSizingTest` (kit-content, §7.4) + PR-10 Perfetto `android.gpu.memory` [U] | T2, T4 |
| PB-26 | Voice memory lifecycle | recognizer warm only while recording; freed ≤ 20 s after the recording ends (10 M2); 60 s later the app's native heap is within 20 MB of its pre-dictation value | Emu | PR-11 | T4 (slice 5 on) |
| PB-26b | Read-aloud memory lifecycle (Kokoro natural voice, ≈ 148 MB of weights plus the runtime) | engine held only while reading + 30 s [E]; released at once when voice capture starts (10 A1) or on memory pressure (§7.2); 60 s after reading ends the native heap is within 20 MB of its pre-reading value | Emu | PR-11 read-aloud cycle | T4 (slice 5 on) |
| PB-27 | Threads | app-process thread count ≤ 100 with 3 live connections and a streaming turn [E] | Emu | `/proc/<pid>/status` `Threads:` at the end of PJ-05 | T4 |
| PB-28 | Low-end device | on `OCN_26` (API 26, 2 GB) booted with `-cores 2` at 60 Hz: PJ-01 and PJ-05 within +10 % of that AVD's last green; PSS at the end of PJ-05 ≤ 200 MB [E]; under PR-09's memory fill the caches drop (§7.2) and the app is not killed while visible | Emu (low-end) | PR-14 | T4 slice ends 2, 4, 6 |

### 2.5 Size (arm64 unless stated; compressed bytes in the signed APK)

| Id | What | Target | Gate |
|---|---|---|---|
| PB-30 | arm64-v8a APK | gate **≤ 22 MB** (= S-14); working target **≤ 20 MB** [E] | T2 size gate |
| PB-31 | Universal APK (arm64 + x86_64 only, §8.3) | gate **≤ 45 MB** (= S-14); working target **≤ 32 MB** [E] | T2 |
| PB-32 | Growth | ≤ 1 MiB per release unless the release notes say why (= S-14); ≤ 256 KiB per merge unless the slice brief names the growth (tightens S-14) | T2 (merge), release script (release) |
| PB-33 | Component sub-budgets | every row of §8.2 within its cap | T2 |
| PB-34 | Installed footprint (APK + extracted native libraries, no user data) | ≤ 55 MB [E] | T3 (`dumpsys package` code size, `du` of `nativeLibraryDir`) |

### 2.6 Background CPU, timers, wake-ups, network and radio

"Hidden" means `DeviceFacts.appVisible == false`. Link demand (04 §6.4) has three values: **Foreground**,
**BackgroundWatching** (the reply-watch FGS runs, or Stay connected is on) and **BackgroundIdle** (neither; every
stream is cancelled and reports `Suspended`). Timers need one more split, because Flutter runs its background monitors
hidden **only under live mode** (`backgroundAllowed: keepLiveInBackground && backgroundLive.active`
[R `lib/state/connection/lifecycle.dart:93-143`]), not under a plain reply watch. So the timer `Visibility` (§11.1)
has four values: **Foreground**, **HiddenLive** (Stay connected on), **HiddenReplyWatch** (the reply watch runs, live
mode off) and **HiddenIdle**. "Idle" in PB-40 also needs no phone server, no agent, no setup job and no Linux
service; runtime timers gated on such work (§11.2 rule 3) may run while hidden with no link demand.

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-40 | Hidden and idle (no turn, no phone server, no agents, no setup job, no Linux service, live mode off) | app CPU **≤ 1 s per hour**; **0** app wake-up alarms except schedules the person set, over 30 min screen off (= S-15) | Emu | PR-01 | T4; switch |
| PB-41 | Hidden while a phone agent streams (20 helper frames per second) | app process **≤ 5 %** of one core (= S-16) | Emu | PR-02 | T4 slice 3 end; switch |
| PB-42 | Hidden timers | no repeating timer fires more often than every **60 s** while hidden and idle (= S-17), except work-gated runtime timers whose gate means "not idle" (§11.2 rule 3); every timer is declared in a `*Timers.kt` table (§11.2) and every row of `perf/timer-inventory.csv` maps to one (§11.3) | JVM + Emu | `TimerTableTest` (JVM) + PR-01 section counts | T2 + T4 |
| PB-42b | Monitors under a plain reply watch | **0** reads by ProfileMonitor, the provider quota monitor, usage subscriptions and the Paseo usage monitor while the app is hidden under a reply watch without Stay connected; under Stay connected they run at their hidden cadence (5 min / 15 min) | JVM | `MonitorVisibilityTest` (`:engine:connection`, `TestPacer`) | T2 |
| PB-43 | Wake-up alarms | only: user schedules (exact alarms the person allowed), the idle-stop check of the phone server (request code 4095, only while that policy is armed and the app is hidden), the schedule fallback worker, and WorkManager's force-stop detector (`ForceStopRunnable` arms one `setExact` alarm about 10 years ahead once WorkManager initializes [R, work-runtime 2.10.5 bytecode]; matched by its PendingIntent target and a trigger time more than 1 year away; it never fires). The wake-up **count** comes from alarms that fired (`dumpsys batterystats` wakeup alarms), as S-15 does | Emu | `dumpsys alarm` + `dumpsys batterystats` after PR-01 and PR-03 | T4 |
| PB-44 | Wake locks | 0 ms held while hidden and idle. While working, only: (1) inside a `WorkLeases` lease (10–15 min hold, renewed every 5 min, released the moment the work ends; one **unbroken stretch** ≤ 6 h, after which the hold is capped until the work stops and starts again, Flutter `reply_watch.dart` `ceiling` / `phone_agent_work_watch.dart` `_ceiling` [R]); (2) the Linux manager's "Keep the phone awake while services run" switch (owner decision 2026-10-31, linux-manager S5): only while charging and a service is on, once that slice lands; (3) the Linux backup copy's lock while a backup runs, if 11 W11-06 keeps one [U] | Emu | `dumpsys batterystats` wakelock lines after PR-01/PR-03/PR-03b | T4 |
| PB-45 | Reply-watch FGS | starts only with a running turn when the app goes to the background; stops **≤ 5 s** after the turn settles; never runs with no running turn unless Stay connected is on | Emu | PR-02 + `dumpsys activity services <pkg>` | T3 |
| PB-46 | Periodic jobs | no periodic WorkManager request at all; widgets keep `updatePeriodMillis = 0` (as today [R]) | VPS | `ManifestPerfTest` (merged manifest + widget XML) + Konsist K-P12 (no `PeriodicWorkRequest`) | T2 |
| PB-47 | Foreground idle CPU (Conversations, connected, nothing running, screen on) | ≤ 1 % of one core averaged over 60 s [E] | Emu | PR-09 `/proc/<pid>/stat` | T4 |
| PB-48 | Foreground streaming CPU, app process only | ≤ 25 % of one core averaged over PJ-05 [E] | Phone | PJ-05 + `/proc/<pid>/stat` | T4 phone |
| PB-50 | One event stream per live OpenCode 2 connection | exactly **1** open `/api/event` request per connection (04 N4-D9, row B-PROTO-134) | JVM + Emu | contract test + fixture-server request log in PJ-05 | T2, T4 |
| PB-51 | Reconnect while hidden (link demand BackgroundWatching: HiddenLive or HiddenReplyWatch) | retries after 1 s, 2 s, then every **60–120 s, jittered**; ≤ 1 attempt per connection per 60 s after the second (= S-18); on return to the foreground a waiting retry fires at once | JVM + Emu | `BackoffPolicyTest` + PR-04 | T2, T3; switch |
| PB-52 | Offline | **0** connection attempts to non-loopback hosts while Android reports no default network (= S-18) | JVM + Emu | `NetworkGateTest` + PR-04 airplane mode | T2, T3; switch |
| PB-53 | BackgroundIdle | **0** open streams and **0** requests except the person's schedules (the update check never runs hidden, PB-55) | Emu | PR-01 fixture-server log | T4 |
| PB-54 | Request counts in the foreground (20 min of chat with 6 returns from the background and 3 chat opens) | provider catalogue reads ≤ 2; provider sign-in reads 0; integration reads 0; permission list reads ≤ 7; question list reads ≤ 7 (numbers from the Flutter budget test `perf_catalog_budget_test.dart:212`) | JVM | `RequestBudgetTest` in `:engine:connection` (virtual time, fake gateway counting calls) | T2 |
| PB-58 | Return to the foreground | the connection is ready after **one** health check, ≤ 300 ms in virtual time with a fast fake server (it was 2.5 s on the phone when the resume waited for the session list, the waiting requests and the catalogue); the reload behind it skips a catalogue that is still fresh; a load cut off by going to the background is dropped, not retried hidden; waiting requests are read on a return even with no event stream (source: `perf_catalog_budget_test.dart:242-338` [R]) | JVM | `ResumeBudgetTest` (`:engine:connection`) | T2 |
| PB-59 | Catalogue loads | concurrent readers share **one** load; Reload during a load queues exactly **one** more; a change event refreshes behind the list already shown; a catalogue older than **10 min** refreshes in the background behind the shown list (source: `perf_catalog_budget_test.dart:266-306` [R]) | JVM | `CatalogLoadDedupeTest` (`:engine:connection`) | T2 |
| PB-49 | Session-list reads | ≤ **1** `/session` read per return to the foreground and ≤ 1 per 2 s burst of session events; `/project` and `/session/active` likewise (the owner's 10-28 phone trace showed a back-to-back refresh loop and `catalog.load` at 8.1 s [R]) | JVM + Emu | `SessionReadBudgetTest` (`:engine:connection`, virtual time) + FX-OC2-PERF request log in PJ-04/PJ-05 | T2, T4 |
| PB-55 | App update check | automatic check on cold start and on each return to the foreground, at most once per **6 h** after the last success and again **30 min** after a failure; only while "Check GitHub for new versions" (`oc.appUpdate.enabled`) is on (0 checks when off); never while hidden; never schedules a wake-up; the manual "Check now" is exempt from the cap (Flutter `lib/update/app_update_controller.dart` `appUpdateCheckInterval`, `_retryAfterFailure` [R freeze tip]; 16 §8.2; memory `app-updates-two-layers`) | JVM | `UpdateCheckCadenceTest` (`:engine:diagnostics`): start, resume within 6 h (no check), resume after 6 h, failure then resume after 30 min, switch off, hidden, Check now | T2 |
| PB-56 | Phone server model list | the phone's own OpenCode 2 server does not fetch the model list at start; it is refreshed when the model picker opens (audit #8, owner decision 5 "yes") | Emu | `OPENCODE_DISABLE_MODELS_FETCH=1` in the server start script [U P13-5: 2.1.1 honours it] + fixture egress log | T3 |
| PB-57 | Mobile radio, hidden idle, 8 h overnight | app-attributed mobile radio active time ≤ 60 s [E] | Phone (passive read) | `dumpsys batterystats --charged <pkg>` after a normal night, with the owner's OK | phone session |

### 2.7 Battery and heat

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-60 | Hidden idle overnight drain | app's estimated power ≤ 10 mAh per 8 h with no phone server and no agents [E] | Phone (passive) | `dumpsys batterystats --charged <pkg>` "Estimated power use" | phone session |
| PB-61 | Phone server running, idle, app hidden | app uid CPU (app + proot + server) ≤ 30 s per hour [E]; the server's proot tracer 0 ticks in a 15 s idle window (audit measured 0 [R]) | Emu | PR-03 | T4 slice 3 end |
| PB-61b | One idle Linux service on, app hidden (the `oc-svd` supervisor ticks every 2 s through `read -t` on a FIFO and forks nothing while idle [R freeze tip `oc_svd_script.dart`]) | over 30 min: app uid CPU ≤ 40 s per hour [E]; the supervisor's proot tracer ≤ 1 tick per supervisor tick (no fork per tick); 0 new processes while nothing changes | Emu | PR-03b | T4 slice 5 on |
| PB-62 | Heat tier response | tier recomputed within 10 s of a thermal, headroom, battery, power-save or visibility change; leaves a tier only after 2 calm minutes (`calmFor`) | JVM + Emu | `HeatTierPolicyTest` + PR-05 | T2, T4 |
| PB-63 | Agent background class | at tier Slow or Hold, ≥ 95 % of the threads of every Paseo helper tree are at nice ≥ 10 (Slow) or 19 (Hold) within 10 s of the change and within 10 s of a new process appearing | Emu | PR-05: `ps -T -o TID,NI,NAME` over the tree | T4 |
| PB-64 | App smoothness while agents work hard | with the standard agent load (§3.8) at tier Slow: PB-12 jank ≤ 2 % while the transcript streams | Emu | PR-05 + PJ-05 under load | T4 slice 3 end |
| PB-65 | Orphans | 0 processes of the app's uid with ppid 1 and a proot loader `exe` alive more than 90 s after their proot died | Emu | PR-06 (kill a proot with SIGKILL, watch `ps`) | T3 slice 3 end |
| PB-66 | Status notification updates, per row | **phone-server FGS row** (`ServiceNoticeGate`): re-posted only when the words change, ≤ 1 per 5 s; **coding status row** (11 `showStatus`): ≤ 1 per 2 s, trailing post of the newest, only on changed content; **request alerts** ("Needs you"): posted at once, never throttled. Across all rows ≤ 60 posts per hour with 3 agents running tools (= S-19) | Emu | PR-07: `PerfTrace` `notify.post` count per channel/id + `dumpsys notification`; one assertion per row in `notify_run.sh` | T3; switch |
| PB-67 | Widget updates | ≤ 1 per 2 s per widget id and only when the rendered model's hash changes; 0 while nothing changes | JVM + Emu | `WidgetUpdateGateTest` + PR-07 | T2, T3 |

### 2.8 Storage I/O

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-70 | Main-thread disk and network | **0** StrictMode violations outside the allowlist (§14.1: ADR §9.7 step 0, the crash-loop file read **and** write ≤ 4 KB, any measured `NativeCrashStore` construction read) in the `stableQa` build over all T3 flows (tightens S-21) | Emu | StrictMode listener (API 28+) counting violations into the problem report; `penaltyLog` below 28 | T3 |
| PB-71 | Writes while streaming | ≤ **1 commit per 2 s per prefs file across all writer lanes** (SharedPreferences rewrites the whole file on each commit, so the adopted `FlutterSharedPreferences.xml` counts once however many lanes write to it); app `write_bytes` over a 10 min stream ≤ 5 MB [E]; one commit of the adopted file at the documented caps (60 MiB offline queue, 50 stash entries, 5+ profiles) ≤ 50 ms on the emulator [E], else a Room trigger hit (§4.3) | JVM + Emu | `WriteRateTest` (per file) + `/proc/<pid>/io` before and after PJ-05 ×N | T2, T4 |
| PB-71b | Diagnostics writes | timing records (`PerfTrace`, field stats) flushed at most every 2 s and on pause; error records written at once | JVM | `DiagnosticsFlushTest` (`:engine:diagnostics`) | T2 |
| PB-72 | Voice packs at start-up | 0 full SHA-256 re-hash of installed packs on a normal start (verified stamp, 10 M4) | JVM | `VerifiedStampTest` (`:engine:media`) | T2 |

### 2.9 Per-item work, processes and the phone's Linux

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-73 | Redaction cost (freeze-tip lesson `b992ec9da`: masking a widget snapshot was 22 % of the main thread while an agent streamed) | `Redactor` patterns compiled once; a bounded memo of **512** recent short inputs, cleared whenever the known secret set changes; batch redaction (widget snapshots, reports, notification sets) never on the main thread; a 150-session widget snapshot ≤ **15 ms** on the emulator [E] (Flutter after the fix: 13 ms on the VPS JIT [R]) | JVM + Emu | `RedactorMemoTest` (port of `test/kit/kit_redact_memo_test.dart`) + section `redact.batch` in PR-07 | T2, T4 |
| PB-74 | Child processes at idle (the 32-process phantom limit kills agents, memory `phantom-process-limit`) | with the phone server and the agents backend (Paseo) running and no turn: app-uid children ≤ **9** on the emulator (the freeze tip's measured idle set [R `docs/qa/process-budget-2026-11-01`]); list refreshes spawn **0** new processes after the first sign-in check (its answer is reused for 10 min); the usage collector starts on the first usage read and exits ≤ 2 min after the last | Emu | PR-03 `ps -A` sampling over the app uid (counts, never names in results) | T4 slice 3 on; switch |
| PB-75 | proot runs with seccomp acceleration (the largest proot speed factor: 622 vs 810 ms for `opencode --version`, 292 vs 461 ms for 200 × `/bin/true` [R builtin-linux map]) | `PerformanceReport.prootMode == seccomp` for the server proot and every agent proot | Emu, Phone | PR-03 + phone session 2 (read from the app's own report) | T4 slice 3 on; switch |

### 2.10 Media (imported from 10 §18; 10 owns the code, this section owns the table and the registry)

Numbers are 10's, unchanged. Measured on the `.bench` package (§3.2 rule 1), including on the phone.

| Id | What | Target | Device | Method | Gate |
|---|---|---|---|---|---|
| PB-80 | Cold-start cost of media | 0 ms: no media class touched before the first frame | Emu | PJ-01 with and without `:android:media` bindings, medians within ± 5 ms | T4 |
| PB-81 | Whisper `base` real-time factor | ≤ 0.35 [U] | Phone | PM-01 `SpeechBenchmark` (3-minute WAV fixture) | phone session 2 |
| PB-82 | Whisper `small` peak RSS | ≤ 1.0 GB [U]; over it, `small` is never chosen automatically below 6 GB total RAM | Phone | PM-01 | phone session 2 |
| PB-83 | Warm model load | `base` ≤ 1.5 s, `small` ≤ 4 s [U] | Phone | PM-01 | phone session 2 |
| PB-84 | Kokoro first sound and RTF | first sound ≤ 3 s cold, ≤ 1 s warm for 200 characters; RTF ≤ 0.5 [U] | Phone | PM-02 `SynthesisBenchmark` | phone session 2 |
| PB-85 | QR decode | p95 < 30 ms per 720p frame; first lock < 1 s | Emu, Phone | PM-03 `ScannerBenchmark` | T4 slice 5 on |
| PB-86 | PDF page | p95 ≤ 1.5 s including bind [U] | Phone | PM-04 `PdfPageBenchmark` | phone session 2 |
| PB-87 | Photo prepare (12 MP JPEG) | ≤ 400 ms [U] | Phone | PM-05 `ImageCodecBenchmark` | phone session 2 |
| PB-88 | Gallery swipe and 50-page PDF scroll | frames as PB-13 | Emu, Phone | PJ-17, PJ-18 | T4 slice 5 on |
| PB-89 | Download progress | ≤ **4** UI updates per second (the progress bar animates between them) and ≤ 1 notification per 1 %; one rate for every download (10 §18 said ≤ 10/s; the stricter number is kept, amendment A18) | JVM | `PackInstallerTest` / `DownloaderProgressTest` with a fake clock | T2 |

---

## 3. Measurement: builds, tools and procedures

### 3.1 What each machine may prove

| Claim | VPS | PC emulator | NX721J |
|---|---|---|---|
| counts (publishes, recompositions, requests, timers, retries) | **yes** (JVM) | yes | — |
| size, R8, DEX layout, manifest | **yes** | — | — |
| relative frame timing, start-up, memory, background behaviour | — | **yes** | — |
| absolute frame timing, start-up, battery, heat | — | no | **yes, with the owner's OK** |

### 3.2 Build types and what is in each

| Build | App id | Signing | Minified | Profileable | Extras | Used by |
|---|---|---|---|---|---|---|
| `stableRelease` | `io.github.eslamasabry.opencode_mobile` | release key per lineage (ADR D2) | yes (R8 full) | only if the owner says yes to P13-D3 part 1 and section 16 accepts it (Q2) | none | the product; phone field data |
| `previewRelease` | `….native` | local key | yes | as `stableRelease` | none | the owner's side-by-side preview |
| `stableBenchmark` (03 §21: `initWith(release)`) | `….bench` | debug (always the VPS's debug key, §3.10) | yes | **yes**, from `src/benchmark/AndroidManifest.xml` (`<profileable android:shell="true"/>`), so benchmarks never depend on P13-D3 | `src/benchmark/`: `BenchSeedReceiver`, `testTagsAsResourceId = true` at the root, `runtime-tracing` pinned to the release's Compose runtime (§6.4) | T4, Macrobenchmark, Perfetto |
| `stableBenchGen` (manual: `initWith(benchmark)`, `isMinifyEnabled = false`) | `….bench` | debug (VPS) | **no** | yes (same manifest) | the same `src/benchmark/` hooks, wired with `sourceSets["benchGen"]` pointing at `src/benchmark/{kotlin,AndroidManifest.xml}` | baseline/startup profile generation only (§10) |
| `stableQa` (`stable` flavor × `qa` build type: 16 CR16-11 `initWith(release)`, `versionNameSuffix = "-qa"`, not debuggable) | `io.github.eslamasabry.opencode_mobile` | **local key** `1DE5BF08…D60C`, re-signed on the PC (16 §6.2 `sign_local.sh`) | yes | yes (`src/qa/AndroidManifest.xml`) | `src/qa/`: `QaHooks` (14 §4.4), StrictMode (§14.1), migration step hooks | T3 functional and upgrade runs (installs over Flutter R and `stableRelease`, 05 J1–J2, 14 §4.4); PR-03 on its own snapshot; **never installed on the owner's phone** |
| `previewQa` (optional) | `….native` | local key | yes | yes | as `stableQa` | side-by-side QA beside Flutter R, only if wanted |

The plugin's own generated `benchmark<BuildType>` and `nonMinified<BuildType>` build types are not used: `t4.sh` never
builds them (P13-1 records only their names so nobody picks them up by mistake). The plugin's `nonMinifiedRelease`
cannot serve generation: it carries the main application id (a collision with Flutter R on the emulator) and does not
compile `src/benchmark/` (source sets are per build-type name), so it has no seed receiver and no resource-id tags.

Rules:

1. Never run Macrobenchmark or Perfetto against the `stable` package on the phone, and never against a `stable`
   package that holds the built-in Linux or the person's data. `am start -S` (force stop) kills the phone server and
   agents and cancels alarms until the next launch. Benchmarks target `….bench`. The single exception is PJ-01f:
   Flutter R on the emulator's `perf-base` snapshot (no Linux, no personal data), which is restored after every T4.
2. The benchmark build differs from release only by the source set `src/benchmark/` and the suffix. A Konsist rule
   (K-P10, §18) fails if `src/benchmark/` code is referenced from `src/main/`. A T2 check compares
   `stableBenchmarkRuntimeClasspath` with `stableReleaseRuntimeClasspath`: they must be equal apart from an allowlist
   (`runtime-tracing`, and nothing else without the perf lead's line in the allowlist).
3. `BenchSeedReceiver` is exported only in the bench builds and protected by `android:permission=
   "android.permission.DUMP"` (adb shell holds it; apps do not). It takes only a **host id** (`emulator` →
   `10.0.2.2`; `phone` → the PC's Tailscale address, given as one more extra since an address is not a secret) and a
   **port**, and seeds one profile that points at FX-OC2-PERF
   (§3.8) with the compiled-in bench-only password `BenchTarget.PERF_PASSWORD`. That password is not a secret (the
   instance serves only generated fixture data, §3.8) and never travels on a command line. It never touches another
   profile.
4. **P13-1 [U]:** the exact Gradle task names for `stableBenchmark`, `stableBenchGen` and the test APK on AGP 9.3.3
   with the plugin applied, settled on the slice-0 skeleton (WP-13.0) and written into `native/tool/perf/vps_build.sh`
   (§3.10).
5. `stableQa` and Flutter R both use the main package, so they never share an emulator state: T4 runs on the
   `perf-base` snapshot (Flutter R + `….bench`), PR-03 runs on its own snapshot `linux-ready` (`stableQa` with the
   built-in Linux installed), and `t4.sh` reloads `perf-base` afterwards (§3.7 rule 2).

### 3.3 The `:android:benchmark` module

```kotlin
// native/android/benchmark/build.gradle.kts
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")            // 1.5.0; no newDsl=false needed on AGP 9 [V testing-perf §1]
}
android {
    namespace = "io.github.eslamasabry.oc.android.benchmark"
    compileSdk = 37
    defaultConfig {
        minSdk = 28                            // [E] the module only ever runs on API 33+ devices here (OC_API37, NX721J;
                                               // root-free profile generation needs 33+); the app itself stays minSdk 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        missingDimensionStrategy("channel", "stable")
    }
    targetProjectPath = ":androidApp"
    experimentalProperties["android.experimental.self-instrumenting"] = true
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}
baselineProfile { useConnectedDevices = true }          // kept for the plugin's DSL; T4 does not run the plugin's
                                                        // generation task (§3.10 installs and runs with am instrument)
dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)  // 1.5.0
    implementation(libs.androidx.test.uiautomator)        // 2.4.0
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.tracing.perfetto)        // 1.0.x [R testing-perf; exact version pinned in WP-13.0]:
    implementation(libs.androidx.tracing.perfetto.binary) // needed by androidx.benchmark.fullTracing.enable (§6.4).
                                                          // This module only; never on the app's release classpath.
}
```

Source layout (`io.github.eslamasabry.oc.benchmark`):

```
BenchTarget.kt            const PACKAGE = "io.github.eslamasabry.opencode_mobile.bench"; const FLUTTER_PACKAGE (PJ-01f only);
                          PERF_PASSWORD; seed(hostId, port); fixture names
startup/StartupBenchmark.kt           PJ-01, PJ-01f, PJ-02
startup/EntryBenchmark.kt             PJ-03
chat/OpenChatBenchmark.kt             PJ-04
chat/StreamingBenchmark.kt            PJ-05   (08 J2, 09 J2)
chat/TranscriptFlingBenchmark.kt      PJ-06   (08 J3, 09 J3)
motion/RowToChatBenchmark.kt          PJ-07   (08 J4)
motion/PredictiveBackBenchmark.kt     PJ-08   (08 J5)
motion/SheetBenchmark.kt              PJ-09   (08 J6 + the sheets of 09 J4)
motion/ImeBenchmark.kt                PJ-10   (08 J7 + the keyboard of 09 J4)
motion/RefreshBenchmark.kt            PJ-11   (08 J8)
chat/WorkLineBenchmark.kt             PJ-12   (09 J5)
lists/ListFlingBenchmark.kt           PJ-13, PJ-14
memory/MemoryJourneyBenchmark.kt      PJ-15
idle/IdleFramesBenchmark.kt           PJ-16
media/GalleryBenchmark.kt             PJ-17   (10 §18)
media/PdfScrollBenchmark.kt           PJ-18   (10 §18)
tools/TerminalFloodBenchmark.kt       PJ-19
tools/CodeReaderBenchmark.kt          PJ-20
shell/SettingsOpenBenchmark.kt        PJ-21
shell/SwitchProjectBenchmark.kt       PJ-22
profile/BaselineProfileGenerator.kt   §10
```

Section 08's files under `motion/` are written by the 08 M6 unit (Sonnet) inside this module, and `media/` by the
media owner (10); the perf lead owns the module and reviews them.

The in-process media timings (PM-01 `SpeechBenchmark`, PM-02 `SynthesisBenchmark`, PM-03 `ScannerBenchmark`, PM-04
`PdfPageBenchmark`, PM-05 `ImageCodecBenchmark`, 10 §18) call the app's own classes, so they cannot live in this
self-instrumenting module. They are instrumented tests in `:android:media` `androidTest` (`testBuildType = "release"`,
not debuggable; their time is dominated by native inference and decoding, so R8 changes little [E]), written by the
media owner and registered in §3.4 by the perf lead. Their test APK has its own package
(`….oc.android.media.test`), so running them never touches the person's app; `t4.sh` and `phone_session.sh` install,
run and uninstall it like the bench APKs.

### 3.4 The journey registry

All journeys: `StartupMode` as listed, `CompilationMode.Partial(BaselineProfileMode.Require)` unless stated,
iterations 10 (startup) or 5 (journeys of 20 s or more), gesture navigation on (08 §9.5), animations **on** (frame
journeys measure motion), the perf instance FX-OC2-PERF on the PC (§3.8). Every intent a journey sends is explicit
(`setPackage(BenchTarget.PACKAGE)` or a component), because the frozen `opencode-mobile://` scheme is declared by
Flutter R, `stableRelease` and `….bench` alike, and an implicit VIEW could open a chooser or the person's app.

| Id | Class | Steps | Metrics | Budgets |
|---|---|---|---|---|
| PJ-01 | `StartupBenchmark.cold` (+ `PB_01u_cold_after_update` with `None()`) | `pressHome()`; `startActivityAndWait()`; wait for the first Conversations row | `StartupTimingMetric`, `TraceSectionMetric("app.onCreate")`, `TraceSectionMetric("app.toFirstConnected")` (async, §3.4.3) [U P13-2: async slices matched], `ArtMetric` (counts and summed times only: JIT, verification, class loading on API 35 or ART mainline ≥ 341511000 [V reference]; never a class list) | PB-01, 01u, 02, 03, 03b, 07, 10 |
| PJ-01f | `StartupBenchmark.coldFlutter` | the same steps against `BenchTarget.FLUTTER_PACKAGE` (Flutter R, main package, `perf-base` snapshot only, §3.2 rule 1); `CompilationMode.Ignore()` (leave Flutter R's compiled state as installed); ends on a Flutter-visible text of its conversation list | `StartupTimingMetric` | PB-01e (the Flutter median) |
| PJ-02 | `StartupBenchmark.warm/hot` | as PJ-01 in WARM and HOT | `StartupTimingMetric` | PB-04, 05 |
| PJ-03 | `EntryBenchmark` | kill; `startActivityAndWait(Intent(ACTION_VIEW, Uri.parse("opencode-mobile://session?…fixture")).setPackage(BenchTarget.PACKAGE))` | `StartupTimingMetric`, `TraceSectionMetric("chat.open_to_first_transcript")` | PB-06 |
| PJ-04 | `OpenChatBenchmark` | warm; tap the 300-turn conversation | `TraceSectionMetric("chat.open_to_first_transcript")`, `FrameTimingMetric` | PB-14 |
| PJ-05 | `StreamingBenchmark` | open an empty chat; type `perf:stream-6000` into `chat-composer-field`; tap `chat-send-button`; wait for idle | `FrameTimingMetric`, `TraceSectionMetric` for §5.3 sections; Perfetto in the PR-10 variant | PB-11, 12, 17, 18, 48, 50, 71 |
| PJ-06 | `TranscriptFlingBenchmark` | open `perf:history-5000`; fling up 10×, down 10× (09's count; 08 said 5, 09 wins as the chat owner) | `FrameTimingMetric` | PB-13 |
| PJ-07 | `RowToChatBenchmark` | 08 §9.5 J4 | `FrameTimingMetric`, `TraceSectionMetric("shell.rowTransform")` | PB-15 |
| PJ-08 | `PredictiveBackBenchmark` | 08 §9.5 J5 (commit and cancel, both edges) | `FrameTimingMetric` | PB-15 |
| PJ-09 | `SheetBenchmark` | 08 J6, then 09 J4's + sheet and request-details sheet | `FrameTimingMetric` | PB-15 |
| PJ-10 | `ImeBenchmark` | 08 J7 | `FrameTimingMetric` | PB-15, PB-20 |
| PJ-11 | `RefreshBenchmark` | 08 J8 | `FrameTimingMetric` | PB-15 |
| PJ-12 | `WorkLineBenchmark` | 09 J5 (`perf:tools-200`: open the work line, fold, jump from find) | `FrameTimingMetric` | PB-15 |
| PJ-13 | `ListFlingBenchmark.conversations` | seed 500 conversations (fixture); fling 10× each way | `FrameTimingMetric` | PB-16 |
| PJ-14 | `ListFlingBenchmark.settingsFiles` | Settings hub fling; Files browser over a 2,000-entry fixture folder | `FrameTimingMetric` | PB-16 |
| PJ-15 | `MemoryJourneyBenchmark` | PJ-05 then PJ-06 in one iteration | `@OptIn(ExperimentalMetricApi::class) MemoryUsageMetric(MemoryUsageMetric.Mode.Max)` (heap size, RSS anon, RSS file, GPU [V reference docs]) + `dumpsys meminfo` | PB-21 |
| PJ-16 | `IdleFramesBenchmark` | open Conversations; wait 2 s; measure 10 s doing nothing | Perfetto FrameTimeline frame count | PB-19 |
| PJ-17 | `GalleryBenchmark` | open a 20-picture conversation's gallery; swipe 10 | `FrameTimingMetric` | PB-88 |
| PJ-18 | `PdfScrollBenchmark` | open a 50-page PDF fixture; scroll to the end and back | `FrameTimingMetric` | PB-88 |
| PJ-19 | `TerminalFloodBenchmark` | open the terminal; run `yes` for 10 s; Ctrl+C | `FrameTimingMetric`, section `term.render` | PB-16b |
| PJ-20 | `CodeReaderBenchmark` | open a 2,000-line Kotlin file; fling 5× each way; open a 2,000-line diff; fling | `FrameTimingMetric`, section `code.tokenize` | PB-16c |
| PJ-21 | `SettingsOpenBenchmark` | tap the gear on Conversations; wait for the hub | `TraceSectionMetric("settings.open")` | PB-14b |
| PJ-22 | `SwitchProjectBenchmark` | switch from project `app` to `other` | `FrameTimingMetric`, `TraceSectionMetric("project.switch")` | PB-14c |
| PM-01..05 | `:android:media` `androidTest` (§3.3) | 10 §18's fixtures | per 10 §18 | PB-81..87 |

#### 3.4.1 Sketch: streaming journey

```kotlin
// android/benchmark/src/main/kotlin/io/github/eslamasabry/oc/benchmark/chat/StreamingBenchmark.kt
@RunWith(AndroidJUnit4::class)
class StreamingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun PB_11_streaming_frames() = rule.measureRepeated(
        packageName = BenchTarget.PACKAGE,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("chat.applyBatch", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("chat.publish", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("md.parseTail", TraceSectionMetric.Mode.Sum),
            TraceSectionMetric("kit.reveal.fade", TraceSectionMetric.Mode.Sum),
        ),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            BenchTarget.seed(this, hostId = "emulator", port = 4190)   // explicit `am broadcast -n …/BenchSeedReceiver`
                                     // as shell (holds DUMP); host id and port only, never a password (§3.2 rule 3)
            uiAutomator { startApp(BenchTarget.PACKAGE); onElement { textAsString() == "Fixture: empty" }.click() }
        },
    ) {
        uiAutomator {
            onElement { viewIdResourceName == "chat-composer-field" }.setText("perf:stream-6000")
            onElement { viewIdResourceName == "chat-send-button" }.click()
            onElement(120_000) { textAsString() == "END-OF-FIXTURE" }   // the fixture's last line; 120 s wait
        }
    }
}
```

`TraceSectionMetric.Mode` values other than `First` and `Sum` (e.g. `Count`) are used only after P13-2 confirms them
on 1.5.0 [U]. The exact members of the UIAutomator 2.4 scope inside `MacrobenchmarkScope` (it extends
`UiAutomatorTestScope` since benchmark 1.5.0 [V testing-perf §7]) are fixed by the first compile in WP-13.0 (P13-2).

#### 3.4.2 Sketch: cold start with both compilation modes

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Test fun PB_01_cold_profiled() = cold(CompilationMode.Partial(BaselineProfileMode.Require))
    @Test fun PB_10_cold_unprofiled() = cold(CompilationMode.None())   // also the PB-01u number (first launch after update)

    /** PJ-01f: Flutter R with the same tool, on the perf-base snapshot only (§3.2 rule 1). */
    @Test fun PB_01e_cold_flutter() = rule.measureRepeated(
        packageName = BenchTarget.FLUTTER_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Ignore(), startupMode = StartupMode.COLD, iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        uiAutomator { onElement { textAsString() == BenchTarget.FLUTTER_FIRST_ROW } }
    }

    private fun cold(mode: CompilationMode) = rule.measureRepeated(
        packageName = BenchTarget.PACKAGE,
        metrics = listOf(StartupTimingMetric(), TraceSectionMetric("app.onCreate"),
                         TraceSectionMetric("app.toFirstConnected"), ArtMetric()),
        compilationMode = mode, startupMode = StartupMode.COLD, iterations = 10,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        uiAutomator { onElement { textAsString() == "Fixture: empty" } }   // a cached row: TTFD reported by the app
    }
}
```

`compare.py` puts the two medians of PB-01e side by side in the T4 summary with the ratio; the ratio uses medians
only.

#### 3.4.3 Trace sections every build carries

Names never contain message text, paths, ids or host names (B-SEC-117). Recorded through the `Trace` facade
(`:core:common`, 03 §18.5) → `androidx.tracing` 2.0.5 on Android. The facade today has only a synchronous
`section {}` and an instant `mark`. Spans that start and end on different threads or coroutines (`app.toFirstConnected`,
`chat.open_to_first_transcript`, `bg.*.ready`, `settings.open`) need `beginAsync(name, cookie)` / `endAsync(name,
cookie)`, mapped to androidx.tracing async sections (coordinator amendment A13 to 03 §18.5 [U P13-13: the 2.0.5 async
API name]). Until A13 lands, those budgets are read from `PerfTrace` marks parsed by `analyze.py`.

New native sections (no Flutter counterpart; camelCase):

| Section | Where | Budget |
|---|---|---|
| `app.onCreate`, `app.crashLoopGuard`, `app.migrationStep0`, `app.graph` | `OcApplication` | PB-03, PB-08 |
| async `app.toFirstConnected` (from `OcApplication.onCreate` to the `app.first_connected` mark) | `OcApplication` → `:engine:connection` | PB-07 |
| `main.onCreate`, `shell.firstComposition` | `MainActivity`, `:ui:shell` | PB-01 |
| async `bg.<component>.ready` (`bg.widget.status`, `bg.worker.schedule`, `bg.receiver.restore`, …) | `:android:*` bodies | PB-09 |
| `sse.read`, `sse.parse`, `wire.decode` | `:core:net`, `:protocol:oc2` | PB-18 |
| `chat.applyBatch`, `chat.publish`, `chat.derive` | `:engine:transcript`, `:feature:chat` | PB-18 |
| `md.parseTail`, `code.tokenize`, `term.render` | `:core:markdown`, `:ui:kit-content`, `:ui:kit` terminal view | PB-18, PB-16b, PB-16c |
| `kit.reveal.word`, `kit.reveal.fade`, `kit.glow.draw`, `kit.frost.draw`, `shell.rowTransform`, `shell.predictive`, `kit.sheet.settle` | 08 §9.5, 06 K12 | PB-11f, PB-15, PB-18 |
| `redact.batch` | `:core:common` `Redactor` batch calls | PB-73 |
| `project.switch` | `:engine:connection` | PB-14c |
| `heat.tier`, `agents.priority` | `:engine:runtime`, `:android:linux` | PB-62, PB-63 |
| `notify.post`, `widget.update` | `:android:background`, `:android:widgets` | PB-66, PB-67 |

**Kept Flutter marks** (03 §18.5, 07 §10.7: same names, so the owner's pasted Performance report reads the same
before and after the switch; `PerfTrace` records them and emits the matching trace marks or async sections):

| Mark | Kind | Budget it feeds |
|---|---|---|
| `app.main`, `app.first_frame`, `app.bootstrap`, `app.shell_frame` | marks | PB-03b, PB-01 |
| `app.first_connected` | mark (+ async `app.toFirstConnected`) | PB-07 |
| `chat.open_to_first_transcript` (attribute `source: saved_excerpt\|live`), `chat.open_to_fresh` | async | PB-06, PB-14 |
| `chat.send_to_bubble`, `chat.send_to_ack` | async | PB-20 |
| `prompt.admitted`, `prompt.first_token`, `prompt.turn`, `reply.first_token`, `reply.done`, `reply.awake_capped` | marks / async | reported (field), PB-44 |
| `events.connected`, `events.first`, `events.first.session_log`, `messages.page` | marks | PB-09 (cold worker), PB-49 |
| `lifecycle.resume`, `lifecycle.reload` | async | PB-58 |
| `settings.open`, `catalog.load` | async | PB-14b, PB-59 |
| `nav.<from>-><to>` (07 N8) | async | reported |
| HTTP spans ≥ 50 ms `http oc2 METHOD /path-template` | spans | PB-03b, PB-49 |

The chat-genui map's mark list is added to this table by WP-13.3 from `lib/` (`grep -rn "PerfTrace\." lib`), so no
mark the owner reads is lost.

`analyze.py` checks PB-03b by order: no `http` span (`PerfTrace` HTTP spans, 03 §18.5) and no `prefs.load` section on
the main thread starts before the `app.first_frame` mark.

### 3.5 Scripted runs (not Macrobenchmark)

All run from `native/tool/perf/` on the PC with the emulator lock held, launched detached (§3.7 rule 7); all write
`perf/results/<date>-<sha>/PR-NN.json`.

| Id | Script | What it does | Budgets |
|---|---|---|---|
| PR-01 | `hidden_run.sh --scenario idle --minutes 30` | seed; open; connect; Home; screen off; 30 min; then 30 min more with `dumpsys deviceidle force-idle` | PB-23, 40, 42, 43, 44, 53 |
| PR-02 | `hidden_run.sh --scenario watching --minutes 10` | start `perf:stream-6000` (slowed fixture: 10 min); Home; screen off; sample; also a fake phone-agent feed at 20 frames/s (S-16 setup); assert the four monitors read nothing (PB-42b) | PB-41, 42b, 45 |
| PR-03 | `hidden_run.sh --scenario phone-server --minutes 30` | load the snapshot `linux-ready` (`stableQa` with the built-in Linux installed [U: snapshot made in WP-13.6]); server and the agents backend running, no turn; sample app-uid children and `prootMode`; 3 list refreshes; hidden 30 min; reload `perf-base` afterwards | PB-43, 44, 61, 74, 75 |
| PR-03b | `hidden_run.sh --scenario linux-service --minutes 30` | as PR-03 with one idle Linux service on (a `sleep infinity` service); count supervisor tracer ticks, new processes and app-uid CPU | PB-44, 61b |
| PR-04 | `offline_run.sh` | `cmd connectivity airplane-mode enable` for 10 min while BackgroundWatching, then disable; also hidden with the fixture server stopped for 10 min | PB-51, 52 |
| PR-05 | `heat_run.sh` | standard agent load (§3.8); `cmd thermalservice override-status 2` (moderate) then 0; `dumpsys battery unplug; dumpsys battery set level 15`; `cmd power set-mode 1` (battery saver); hidden/visible toggles; sample nice values every 5 s | PB-62, 63, 64 |
| PR-06 | `orphan_run.sh` | start an agent tool loop under proot; on the rootable `google_apis` emulator `adb root`, then `kill -9 <proot pid>` (exact PID from `ps`, never by pattern); watch for orphans every 5 s for 3 min | PB-65 |
| PR-07 | `notify_run.sh` | 3 fake agents running tools for 60 min (fixture Paseo); count `notify.post` per row and widget updates; time `redact.batch` per widget snapshot | PB-66, 67, 73 |
| PR-08 | `bg_start_run.sh` | send an **explicit** (`-n <pkg>/<component>`) widget update, a schedule alarm and a restore event to the cold process; read `bg.*.ready`; record a Perfetto trace with `atrace_apps` = the bench package and the `dalvik` category, list the class-load slices (`L…;`) of the app process with `sql/class_loads.sql`, retrace the names with the build's mapping file, and assert no `androidx/compose/`, `coil3/` or `org/jetbrains/compose/` class; then a cold **worker** start that watches a reply (no activity) must reach `events.connected` | PB-09 |
| PR-09 | `fg_idle_run.sh` | 5 min on Conversations; CPU, PSS; then the 20+20 open/close cycles of PB-24; then memory fill: a helper app allocates until `MemoryInfo.lowMemory`, then the app is backgrounded and foregrounded, and the caches must have dropped (§7.2) | PB-22, 24, 47, §7.2 |
| PR-10 | `trace_run.sh <journey>` | runs a PJ with `oc.pbtxt` passed through Macrobenchmark's own session (`measureRepeated(…, experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(oc.pbtxt)))` [U P13-12]); never a second external Perfetto session; runs `sql/*.sql` on the result | PB-12, 18, 19, 20 |
| PR-11 | `voice_run.sh` | 3 × (hold to talk 20 s, release, wait 60 s) with the `tiny` pack; then 3 × (read aloud a 600-character reply with the natural voice, wait 60 s); native heap via `dumpsys meminfo` | PB-26, 26b |
| PR-12 | `flutter_vs_native.sh <journey>` | the same Maestro flow on Flutter R and on native `.bench`, each under Perfetto; jank by layer for both (testing-perf §9.2) | PB-12 (baseline) |
| PR-13 | `style_matrix.sh` | PJ-05, PJ-06, PJ-13 for each Style × light/dark (set through the bench seed's appearance values); frost Off vs Balanced on PJ-05/06 in Solid; one PJ-05 at font scale 2.0 with the Arabic locale; style, frost, scale and locale written into each result JSON | PB-11s, 11f, 11a |
| PR-14 | `lowend_run.sh` | on `OCN_26` with `-cores 2`: PJ-01, PJ-05 and PR-09's memory-fill step | PB-28 |

Restoring. Every PR script and `t4.sh` **starts** with `restore_device.sh`, an unconditional preamble that resets the
battery (`dumpsys battery reset`), thermal (`cmd thermalservice reset`), power mode (`cmd power set-mode 0`),
airplane mode (`cmd connectivity airplane-mode disable`), deviceidle (`dumpsys deviceidle unforce`), animator scales
(1.0), font scale (1.0), density, locale and screen state. A `trap` repeats it at exit as a second layer only, because
a SIGKILL skips traps (§3.7 rule 7). A run that cannot restore exits 2 (02 §8 script contract).

### 3.6 Field numbers from the app itself (the owner's phone without adb)

The app records its own performance locally. Nothing leaves the phone (ADR §9.6). The person sees it on **App
diagnostics' Kept page** (07 L02, `AboutRoute.Kept`, the page that already holds the Performance report the owner
pastes, `PerfTraceSection`), as rows in that section, and its "Copy report" includes the field numbers (text, as
today). This is app-wide and works without the built-in Linux; "This phone › Details › Performance" stays the phone
server's page (reply speed, proot mode, leases, orphans). A recorded system trace is a binary file, so it is never
part of the text report: L02 hands it over only through an explicit Android share sheet ("Share performance trace"),
with one plain sentence saying what the file holds (timings of the app's own work and the system's, no messages, no
files, no passwords).

| Signal | API | Where | Shown as |
|---|---|---|---|
| Real cold, warm and hot starts with first-frame and fully-drawn times | `ApplicationStartInfo` (API 35+): `ActivityManager.addApplicationStartInfoCompletionListener(executor, listener)`, `getStartupTimestamps()[START_TIMESTAMP_FIRST_FRAME]`, `startType` (`START_TYPE_COLD/WARM/HOT`) [V reference; listener name U, checked at compile] | `:android:platform` `StartupHistory` → `:engine:diagnostics` | "Opens in 0.3 s (typical this week)" |
| Smoothness in the last hour | JankStats 1.0.0 (`metrics-performance`) [V Maven]: `JankStats.createAndTrack(window) { fd -> … }`; UI state tags `screen`, `streaming` through `PerformanceMetricsState` | `:android:platform` `FieldFrameStats`, started by `MainActivity` only while resumed; aggregated per minute in memory | "Smooth: 99 of 100 frames on time in the last hour" |
| Whether the speed-up profile is compiled | `ProfileVerifier.getCompilationStatusAsync()` (profileinstaller 1.4.1): `isCompiledWithProfile`, `profileInstallResultCode`, `hasProfileEnqueuedForCompilation` | `:android:platform` | "Speed-up: ready / waits for the phone to rest" |
| Why the app last stopped | `ApplicationExitInfo` (exists today: `AppLifecycle`, 11) incl. `MemoryLimiter:AnonSwap` (ADR) | `:engine:diagnostics` | existing rows |
| A system trace when the app starts slowly or stops responding | `ProfilingManager` (API 35) with `ProfilingTrigger.TRIGGER_TYPE_APP_FULLY_DRAWN` and `TRIGGER_TYPE_ANR` (API 36) [V reference], `setRateLimitingPeriodHours(24)`; the file path comes from `ProfilingResult.getResultFilePath()` (under `files/profiling/`) [V] | `:android:platform` `TraceRecorder` | Off by default; one switch row on L02 "Record a performance trace when the app starts slowly" (§19 P13-D3); a kept trace shows as a row with "Share performance trace" |
| Heat and battery while agents run | `DeviceFacts.thermal`, `thermalHeadroom`, `battery` (11 CR11-5) | `:engine:runtime` | existing heat line |

Costs: JankStats adds a per-frame `FrameMetrics` listener; budget ≤ 0.1 ms per frame on the UI thread [E], measured
once in PJ-05 with and without it (WP-13.4). All of it is off while the app is hidden.

### 3.7 Noise control and the comparison rule

1. **Quiet PC.** `t4.sh` refuses to start unless: the 1-minute load average < 2.0, no Gradle daemon is running
   (`./gradlew --status` shows none, or `pgrep` by exact PID file), free RAM ≥ 4 GB, and `hog_watch` reports nothing
   (memory `hog-watch-monitor`). T4 itself starts no Gradle: it installs the VPS-built APKs (§3.10). It holds the
   emulator lock for the whole run.
2. **Same emulator state.** `OC_API37`, cold-booted from the snapshot `perf-base`: Flutter R (main package, no
   built-in Linux, no personal data) and `….bench`, no other apps' updates, Play services idle; `-gpu host` [U P13-4:
   if host GPU fails on this PC, all runs use the same software mode and the result JSON says so]. Animator scales
   1.0, gesture navigation on, font scale 1.0, dark theme off, screen timeout 30 min, stay awake on. PR-03/PR-03b load
   `linux-ready` instead; `t4.sh` reloads `perf-base` when they finish. (14 §13.2 item 3 moves the flows lane's
   wiping dumps and journeys to `OCN_A`, so `perf-base` on `OC_API37` survives.)
3. **Iterations.** Start-up 10; frame journeys 5; scripted runs once (they are long).
4. **The rule** (`compare.py`). Each metric in `perf/budgets.json` has `direction` (`lower` or `higher` is better)
   and `threshold` (`{"kind":"relative","value":0.10}` for positive metrics such as TTID, CPU ms, PSS and
   `frameDurationCpuMs`; `{"kind":"absolute","value":0.5}` in ms for `frameOverrunMs`, which is mostly negative and
   near zero). A repeated journey **regresses** when `worse(median_new, median_green) > threshold` **and**
   `p25_new` is worse than `median_green` (the medians and p25 are taken across iterations of each percentile).
   On regression the journey reruns once; two failing runs make the result red. A single-shot scripted run (PR-NN)
   has no median: it is compared with its **budget** only, and a miss reruns it once before it counts. One
   improvement does not move `last-green.json`; the coordinator moves it when a merge's T4 is green.
5. **Same-day reference.** Every slice-end T4 also measures Flutter R with the same tool, PJ-01f, and PR-12, on the
   `perf-base` snapshot, so a slow PC day shows up in both numbers.
6. **Refresh rate.** The result JSON records `dumpsys display` refresh rate during the run. Budgets use
   `frameOverrunMs`, which does not depend on it.
7. **Detached runs.** Long background harness tasks are SIGKILLed on this PC (memory `background-task-killer`), and a
   SIGKILL skips every `trap`, which would leave the shared AVD in airplane mode, thermal override, unplugged at 15 %,
   battery saver or forced idle and break every later agent's proof (memory `shared-emulator-contention`). So every
   PR run and every `t4.sh` run is launched with `setsid nohup … &`, writes a per-run state file (completed steps, so
   a rerun resumes the unfinished ones) and a done-file; the calling agent polls the done-file in foreground chunks
   under 600 s. The emulator lock is held by a recorded PID (`setsid flock <lock> sleep N`, both PIDs recorded,
   released by killing the `sleep` child, memory `emulator-lock-holder`). `restore_device.sh` runs first in every
   script (§3.5).
8. **Quiet window.** `t4.sh` reads `OC_PERF_QUIET_WINDOW` (for example `00:00-02:00`); empty means none. Default
   empty, matching the owner's 2026-11-01 "Ignore the 2am rule" (memory `quiet-hours-vps-only`); the coordinator sets
   it again if the owner asks.
9. **Low-end subset.** At the ends of slices 2, 4 and 6, PR-14 runs PJ-01, PJ-05 and the memory-fill step on 14's
   `OCN_26` (API 26, 2 GB, no splash API, glow overscroll) booted with `-cores 2` and a 60 Hz display, compared only
   with that AVD's own last green (PB-28). No new AVD is created (disk space, 14 T14-3).

Result JSON (`perf/results/<date>-<sha>/<id>.json`):

```json
{
  "schema": 1, "id": "PJ-05", "sha": "…", "versionCode": 10012, "build": "stableBenchmark",
  "device": "OC_API37", "refreshHz": 60, "gpu": "host", "date": "2026-11-20",
  "look": { "style": "Solid", "mode": "light", "frost": "Balanced", "fontScale": 1.0, "locale": "en" },
  "metrics": { "frameOverrunMs": { "p50": -9.1, "p90": -4.2, "p95": -1.0, "p99": 2.8, "n": 3104 },
               "frameDurationCpuMs": { "p50": 3.1, "p90": 5.9, "p99": 9.4 } },
  "budgets": { "PB-11e": "pass", "PB-12": "pass" },
  "lastGreen": "perf/results/2026-11-18-ab12cd3/PJ-05.json"
}
```

`compare.py` compares a result only with a last green that has the same `device` and the same `look`, so the
style matrix (PR-13) compares like with like.

### 3.8 Fixtures and the standard loads

| Fixture | Owner | Content |
|---|---|---|
| FX-OC2 | flows lane (section 14) | 14 §4.4: real `opencode2 serve` 2.1.1, HTTP Basic with a **per-run** password, bound to 127.0.0.1; used by the flows, **not** by the bench journeys |
| **FX-OC2-PERF** | perf lead | a separate `opencode2 serve` 2.1.1 instance for T4 and the phone session (below) |
| Replay server | section 14 | recorded fixtures with `: t=+40ms` pacing (testing-perf §8.2); used for PR-02's slowed stream and PR-04 |
| `Fixture: empty`, `Fixture: 300 turns`, 500 conversations, projects `app` and `other` | perf lead | inside FX-OC2-PERF's prebuilt data directory (below); `BenchSeedReceiver` only adds the server profile |
| 2,000-entry folder, 2,000-line file and diff, 20-picture conversation, 50-page PDF | perf lead | in FX-OC2-PERF's project `app` (in the archive) |
| **Standard agent load** | perf lead | inside the emulator's built-in Linux: two `sh -c 'while :; do :; done'` loops started by a fake Paseo helper tree whose argv contains `paseo daemon run --home /home/oc/.oc-profiles/` (so `AgentPriority` finds it [R `AgentPriority.kt`]) |

**FX-OC2-PERF** (the bench profile cannot use FX-OC2: its password changes every run and may not travel on a
command line, AGENTS.md):

- **Started by `t4.sh`** (and by `phone_session.sh`) by exact PID, from the server **binary** (not the npm wrapper,
  14 §8), with its own `XDG_DATA_HOME`/`XDG_CONFIG_HOME` under the run directory and the stub model of 14
  (`stub_model.py`) for the `perf:*` prompts.
- **Password:** a fixed, bench-only, non-secret value given through the environment (`OPENCODE_SERVER_PASSWORD`, the
  variable the Flutter server recipe already uses [R `NativeServerRecipe.kt:54`]; P13-14 confirms 2.1.1 honours it).
  The same value is compiled into the bench build as `BenchTarget.PERF_PASSWORD`. It protects nothing but generated
  fixture data, so it may appear in the bench source; it still never appears in a log, a result JSON or a command
  line.
- **Tools:** its config denies the shell and file-writing tools; `perf:tools-200` uses read-only tools only [U:
  confirmed when the archive is built].
- **Data:** a prebuilt data directory with the 500 sessions, the 300-turn session, `Fixture: empty` and the files
  above, generated once by `tool/perf/make_perf_data.py` against a throwaway instance and stored as an archive
  (`fx-oc2-perf-2.1.1-<n>.tar.zst`, on the PC and the VPS, not in git); `perf/fixtures.json` pins its SHA-256.
  `t4.sh` unpacks a fresh copy for every run, so runs never see each other's writes.
- **Address:** port 4190 [E: free on the PC, checked with `ss -ltn` in WP-13.0]. Bound to `127.0.0.1` for emulator
  runs (the emulator reaches it as `10.0.2.2`). During a phone session only, bound to the PC's Tailscale address
  instead, and stopped when the session ends.

`bench` stays the replay server's fixed fixture password.

### 3.9 JVM microbenchmarks on the VPS (indicative)

- Tool: kotlinx-benchmark **0.5.0** (2026-09-01) [V Maven] (JMH) in a new JVM module `:tools:bench` that depends on the
  desktop variants of `:core:net`, `:core:markdown`, `:engine:transcript` and `:protocol:oc2` (coordinator amendment
  A7: `:tools:*` may depend on these; nothing depends on `:tools:bench`).
- Benchmarks: `SseParserBench` (16 MiB of recorded frames, MB/s), `WireDecodeBench` (`messages_2_1_1.json` page),
  `TranscriptApplyBench` (5,000 deltas into a 5,000-part transcript), `StreamingMarkdownBench` (2 KB appends to a
  20 KB message), `CodeTokenizerBench` (SyntaxMP vs Highlights 1.1.0 on a 2,000-line Kotlin file; the rendering-chat
  swap rule).
- Run nightly on the VPS only if no T2 runs; the VPS is shared, so the gate is a **25 %** regression against the
  7-day median, reported, not blocking. A blocking number always comes from the emulator or from a JVM count test.

### 3.10 The T4 pipeline: the VPS builds, the PC installs and runs

03 §8.3's T4 row (`./gradlew :android:benchmark:connectedBenchmarkAndroidTest`,
`:androidApp:generateStableReleaseBaselineProfile`) would compile R8-minified apps on the 16 GB PC right before
measuring, on the machine with past OOMs. Instead (coordinator amendment A12 to 03 §8.3):

1. **VPS** (`native/tool/perf/vps_build.sh <sha>`, its own build folder under a flock, memory
   `vps-suite-shared-dir-trap`): builds the `stableBenchmark` APKs (x86_64 and arm64 splits), the `stableBenchGen`
   APK, the `:android:benchmark` test APK, the `:android:media` test APK and `stableQa` (unsigned; the PC re-signs it
   with the local key, 16 §6.2). The bench APKs are always signed with the VPS's debug key, so `install -r` over an
   earlier bench build never fails on a signer change. Task names: P13-1.
2. **PC** (`t4.sh`, detached, §3.7 rule 7): pulls the APKs, checks each SHA-256 against the VPS's list, loads
   `perf-base`, `adb install -r` (never `-d`), then for each journey class:
   `adb shell am instrument -w -e class <Class> -e androidx.benchmark.suppressErrors EMULATOR
   -e androidx.benchmark.fullTracing.enable <true|false> <testpkg>/androidx.test.runner.AndroidJUnitRunner`.
3. **Results:** pulls `*-benchmarkData.json` (and the traces) from `/sdcard/Android/media/<testpkg>/`, runs
   `analyze.py` and `compare.py`, writes `perf/results/<date>-<sha>/`.
4. **Profiles** (§10): installs `stableBenchGen`, runs `BaselineProfileGenerator` the same way, pulls
   `*-baseline-prof.txt` and `*-startup-prof.txt` from the same folder; the perf lead copies them to
   `androidApp/src/main/generated/baselineProfiles/` in their own commit (§10.3), after the determinism check
   (§10.3).
5. **Cleanup:** uninstalls the test APKs, reloads `perf-base`, runs `restore_device.sh`, releases the lock.

No Gradle runs on the PC during T4. If the VPS is unreachable, the coordinator may allow one PC Gradle build
**before** the quiet check, followed by `./gradlew --stop`; that run is labelled in the result JSON.

---

## 4. Start-up plan

### 4.1 What runs before the first frame (and nothing else)

`OcApplication.onCreate` follows ADR §9.7 / 05 §7.1 exactly: (1) crash handler, (2) `Redactor`, (3) crash-loop guard,
(4) step 0 (first native launch only), (5) build `AndroidAppGraph`, (6) start `MigrationRunner` on IO, (7) warm the
prefs file on IO. `MainActivity.onCreate`: `enableEdgeToEdge()`, `super.onCreate`, `setContent { App(…) }`.

Start-up rules (SR), each with its check:

| Rule | Check |
|---|---|
| SR1 Only the steps above run on the main thread before `setContent`. | trace order (PB-03b); StrictMode in `stableQa` (PB-70) |
| SR2 Building `AndroidAppGraph` does no I/O and creates no store eagerly: every heavy binding is `Lazy<T>` or `Provider<T>` (Metro). Budget for `app.graph`: ≤ 5 ms [E]. | `app.graph` section; Metro graph smoke test lists eager bindings (allowlist in `:quality`) |
| SR3 The only `androidx.startup` initializers are `ProfileInstallerInitializer` (it defers its work past the first frame), `ProcessLifecycleInitializer` (needed for `appVisible`), `EmojiCompatInitializer` (pulled in by Compose; loads its font off the main thread) and `okhttp3.internal.platform.PlatformInitializer` (OkHttp 5's platform setup; kept, because removing it with `tools:node="remove"` changes how OkHttp finds the platform; it is timed under `app.onCreate`'s budget, and removing it is a separate measured decision). The only other `ContentProvider`s are `FileProvider`s and `org.jetbrains.compose.resources.AndroidContextProvider` (CMP resources need it for non-composable `getString`, which SR8's `Words.preload` on IO and the OS strings depend on). The demo's merged release manifest already has exactly these [R kmp-demo `processReleaseMainManifest`]; WP-13.3 freezes the list from the first merged manifest. WorkManager uses on-demand initialization (05 §7.1). | `ManifestPerfTest` (`:quality`, reads `processStableReleaseMainManifest` output; never "fixed" with `tools:node="remove"` without the perf lead's measured note) |
| SR4 No `getSharedPreferences` on the main thread: step 7 warms the file on IO; UI reads wait for the store's in-memory snapshot (03 §10.4). | StrictMode; `prefs.load` section must be off main |
| SR5 Network waits for the first frame **only when an activity started the process**. The signal is 03 §10.4's `ProcessStart` (`ProcessStartReason`: `Ui`, `Boot`, `PackageReplaced`, `Alarm`, `Worker`, `Widget`, `Tile`, `Service`), recorded by each component shell before anything else. If the first recorded reason is `Ui`, `Bootstrap` in `:engine:entry` lets `ConnectionRegistry` start streams after `app.first_frame`. If a service, receiver, worker, widget or tile started the process (Stay connected restarted by its FGS, a scheduled-prompt worker watching its reply, a widget action), it never draws a frame: streams start as soon as that component asks the registry, after the migration gate opens, with no frame wait. On API 35+ `ApplicationStartInfo` is recorded beside it for diagnostics only. | trace order (PB-03b) for `Ui` starts; PR-08's cold-worker step reaches `events.connected` (PB-09); `BootstrapStartKindTest` (JVM: `Worker` start opens the stream without `app.first_frame`) |
| SR6 No splash hold: `windowSplashScreenBackground` (API 31+) and `windowBackground` (26–30) from the theme; never `setKeepOnScreenCondition`; no `core-splashscreen` dependency. | review; `BannedDependenciesTest` |
| SR7 Fonts: Geist variable TTFs are kit compose resources (06 K7). The first composition uses one sans weight. P13-6 [U] measures `font.load` on the emulator; if the first use of the variable font costs > 10 ms, the kit's `androidMain` loads the same TTFs from Android `res/font` (platform-cached typefaces) behind the same `KitFonts` API, recorded in `JETPACK_AHEAD.md`. | `font.load` section |
| SR8 Strings: the first screen's CMP string file is read once on IO during step 7 (`Words.preload(Feature.Chats)`) so the first composition does not wait on it [U P13-6: measure `words.load`]. | `words.load` section |
| SR9 Background components (receivers, workers, widget providers, tiles, services) never touch Compose, fonts, Coil, CMP resources (they use `strings_os` Android res, ADR §7.5) or `:ui:*`. | PR-08's Perfetto class-load check (`dalvik` category, `sql/class_loads.sql`, names retraced with the mapping file): no `androidx/compose/`, `coil3/` or `org/jetbrains/compose/` class in a background start (`ArtMetric` gives only counts); Konsist: `:android:*` cannot depend on `:ui:kit*` (ADR §6.1 already) |
| SR10 `isMainProcess()` uses `Application.getProcessName()` (API 28+) or `/proc/self/cmdline` (26–27), never `ActivityManager.getRunningAppProcesses()` (a binder call). | review |

### 4.2 Fully drawn

`:ui:shell` `androidMain` wraps the root content:

```kotlin
// ui/shell/src/androidMain/kotlin/io/github/eslamasabry/oc/ui/shell/StartupReporting.android.kt
@Composable
internal fun ReportStartup(state: ShellStartupState) {
    // TTFD: the first page of Conversations rows drawn from the cached snapshot, or the "Opening…" state
    // when there is no cache. Never waits for the network (PB-02).
    ReportDrawnWhen { state.firstPageDrawn || state.showsOpeningState }
}
```

`ReportDrawnWhen` is `androidx.activity.compose` (activity 1.13.0, ADR §8). `ShellStartupState` comes from
`ConversationsViewModel`'s `XUi` (`firstPageDrawn`). The same moment is the `TRIGGER_TYPE_APP_FULLY_DRAWN` trigger of
§3.6.

### 4.3 The Room trigger (ADR §9.8, P5) is a performance measurement

The perf lead runs P5's numbers in slice 0 with PJ-01 on three seeded prefs files: 100 KB, max(10 MB, the owner's
real size), and the **documented caps** (an offline queue at its 60 MiB ceiling, 3 × 20 MiB entries
[R `lib/state/offline_queue.dart` `maxTotalBytes`], which stays in the adopted prefs file until Room at switch+1,
05 §1, §6.2; 50 stash entries' metadata (their attachments are files, 05 §1 `BlobStore`); a list of 5+ profiles). PR-08 covers
the background starts at the same sizes, and PB-08 the migration at the caps. The result goes to the coordinator with
the rule exactly as the ADR states it. Section 13 adds two observations to the same record: the time of one `commit()`
of the cap-size file on the emulator (SharedPreferences rewrites the whole XML on every commit), and the number of
commits of that one file per 2 s across all writer lanes during PJ-05. If one commit of the cap-size file exceeds
PB-71's 50 ms, that is recorded as a Room trigger hit, so a person with a full queue never pays several-second commits
or a slow cold start.

---

## 5. Rendering and streaming smoothness

### 5.1 The streaming path and who owns each step

```
socket ─► sse.read (IO, 16 KiB buffer) ─► sse.parse ─► Channel(4096) ─► wire.decode (Default)
      ─► chat.applyBatch (transcript serial dispatcher) ─► chat.publish ≤ every 50 ms (first delta at once)
      ─► ViewModel combine ─► Compose: live block item only ─► md.parseTail ─► layout of one block ─► draw (fade mask)
```

Owners: `:core:net` (04 §6.6), `:protocol:oc2`, `:engine:transcript` and `:core:markdown` (09), `:ui:kit-chat` (08 §5.1).
This section sets the budgets and the measurement; it changes none of their designs.

### 5.2 Frame budget arithmetic at 120 Hz (8.33 ms per frame)

| Phase on the UI thread | p90 budget [E] | Why it holds |
|---|---|---|
| Composition | ≤ 1.5 ms | only the live block item and its live line recompose (PB-17) |
| Measure and layout | ≤ 2.0 ms | one block re-measures per tail update (09 `BlockRelayoutCounterTest`); `LineBreak.Simple` |
| Draw (recording) | ≤ 1.0 ms | glow, fade, caret, rings are draw-phase only (08 §8.2) |
| Slack for input, IME, GC | ≥ 3.8 ms | |
| RenderThread | ≤ 4.0 ms p90 | one offscreen layer for the live block; no blur except D10 frost |

### 5.3 Hot-path section budgets (PB-18)

| Section | Thread | p95 budget per call [E] | Notes |
|---|---|---|---|
| `sse.parse` | IO | ≤ 0.2 ms per frame ≤ 4 KB | B-PROTO-033 vectors prove correctness; this proves speed |
| `wire.decode` | Default | ≤ 0.5 ms per event ≤ 4 KB; frames > 50 KB never on the main thread (B-AGENT-150) | `WireJson`, no reflection |
| `chat.applyBatch` | transcript dispatcher | ≤ 2 ms per 50 ms batch on a 5,000-part transcript | `PersistentList`; linear merge (B-CHAT-096) |
| `chat.publish` | transcript dispatcher | ≤ 0.5 ms | immutable `TranscriptState` |
| `chat.derive` | Default | ≤ 2 ms | 09; skipped with no subscriber (09 §14.5) |
| `md.parseTail` | Default | ≤ 1 ms per append to a 20 KB message | `StreamingMarkdownFile` re-parses the unstable tail only |
| `code.tokenize` | Default | ≤ 8 ms per 400-line fence, once per settled fence; sources > 20,000 chars stay plain | 48-entry cache (09 §14.4) |
| `kit.reveal.fade` | UI (draw) | ≤ 1 ms (08 S8-2) | |
| `kit.glow.draw` | UI (draw) | ≤ 0.5 ms (08 §8.1) | |

### 5.4 Rules that keep frames on time (each has a check)

| # | Rule | Check |
|---|---|---|
| R-F1 | State read in the latest phase possible: scroll offsets and animated values are read inside `Modifier.offset { }`, `graphicsLayer { }`, `drawBehind { }`, `drawWithCache { }` or layout lambdas, never in composition. | 08 R3; Compose host test of each kit loop part (no recomposition during 1 s of animation) |
| R-F2 | Every `LazyColumn`/`LazyRow` `items(…)` call passes `key =` and `contentType =`. | Konsist K-P6 (text check in `:ui:kit*` and `:feature:*`) |
| R-F3 | No `animateItem()` with a non-null `placementSpec` on transcript items (demo lesson: growing rows animated their neighbours). | Konsist K-P7 |
| R-F4 | No per-frame `AnnotatedString` changes; the word fade is a draw-phase mask (ADR §9.3). | 09 `RevealDrawOnlyTest` |
| R-F5 | `preferredFrameRate(High)` only through `KitPlatform.highFrameRate(active)`, only under motion level Full, and only while a reveal, transform or predictive preview runs (08 M8-16) **or while the composer's running glow shows** (a reply runs; §5.5, amendment A16 to 08 M8-16). No other ambient loop votes High. | Konsist K-P9 |
| R-F6 | Infinite transitions only in the files listed in `config/ambient-loops.txt` (composer glow, `KitSpin`, live-line sweep, step light, Stop ring, caret, refresh breath), each started only when `KitMotion.state` allows its loop class (08 §3). **Clocks** (text that changes on a period: the live-line elapsed time and the answer/Undo countdown at 1 s, the composer-layer tick, the chat retry countdown over `retry.nextAtMs` (09 §4.1), the file share sheet and setup clocks, the voice recording clock at 100 ms with no cap (10)) all come from one kit part, `rememberKitClock(period): State<Instant>` in `ui/kit/…/foundation/time/KitClock.kt`: it waits with `delay` to the next period boundary, publishes on the next frame, stops when its composition leaves (off screen) and while the activity is below STARTED, and never uses `Pacer`. It is the only UI clock source; `KitClock.kt` is listed in `ambient-loops.txt` and is the only `:ui:*` file K-P1 exempts. A hand loop `while (…) { delay() }` anywhere else in UI code is banned (it also keeps test schedulers busy, testing-perf §4.2). Engine-side clocks, if any, are `TimerSpec` rows with `hiddenIdle = null`; the retry countdown needs none (the engine stores `nextAtMs`, the UI counts down). | Konsist K-P1, K-P8; `KitClockTest` (stops off screen and while hidden; 0 ticks after leaving) |
| R-F7 | `remember` derived values; `derivedStateOf` for values computed from scroll state; no allocation of lambdas or lists inside `items { }` content beyond what strong skipping handles. | compiler reports (§6.3); review |
| R-F8 | Images in lists decode at the item's size (Coil `AsyncImage` with constrained size); GIFs animate only while visible. | §7.4 tests |
| R-F9 | Text: `LineBreak.Simple`, `Hyphens.None` in the transcript; no `onTextLayout` work on every frame. | review; 09 |
| R-F10 | Heavy UI work never runs on the main thread: no JSON, no regex over a whole message, no file reads in composition or in click handlers; ViewModels hand them to engines. | StrictMode (PB-70); Konsist bans `java.io` in features (ADR §6.3 rule 1 already) |

### 5.5 Frame-rate and adaptive refresh

- Follow 08 §8.3: on adaptive-refresh devices views default to "Normal" (≈ 60 Hz); the live block votes High while
  it reveals; nothing ever asks for less than 60.
- The audit's question "cap the glow at 60 fps" (#10) was answered by the owner: **not now** [R audit decisions].
  On an adaptive-refresh phone an alpha-only glow that does not vote High runs at Normal (≈ 60 Hz), which would be
  exactly the cap the owner declined. So the composer's running glow votes High while a reply runs, under motion
  Full only, like the Flutter app today (R-F5, amendment A16); the other ambient loops (spinner, sweep, step light,
  caret, breath) do not. PR-10 and E3 measure the glow both ways (High vote on and off: `kit.glow.draw`, the display
  rate, app CPU) on the emulator and in phone session 1, so the cap question can come back to the owner with data.
  Until the owner decides otherwise, no de facto cap ships.

### 5.6 The demo's streaming mistakes, as tests

| Demo behaviour [R rendering-chat §1] | Native guard |
|---|---|
| `remember(text) { parseMarkdown(text) }` re-parsed the whole reply every frame | 09 `TailUpdateTest`: 10 tail re-parses, 0 full parses |
| `fadeTail` built new `SpanStyle`s every frame, re-measuring the paragraph | 09 `RevealDrawOnlyTest` |
| `animateItem(placementSpec = …)` on every row animated rows above the growing reply | K-P7 |
| `reduceMotion` read once at start | Compose `MotionDurationScale` follows the live setting; `KitPlatform.signals.animatorScale` is a live flow (06/08) |

---

## 6. Compose stability configuration and recomposition budgets

### 6.1 The configuration file

`native/config/compose-stability.conf`, applied by `oc.kmp.compose` to every Compose module (03 §19.2). The content
in 03 §19.2 stands. Two additions, each confirmed by P4 before it is merged [U P4]:

```
// Actions interfaces are implemented by ViewModels and only hold methods. Screens take (XUi, XActions) (ADR §9.1).
io.github.eslamasabry.oc.feature.**.presentation.actions.*
// kotlin.time types carried by XUi (kotlinx-datetime 0.8 moved Instant into the stdlib).
kotlin.time.Instant
```

So the first rule matches, each feature keeps its `XActions` interface in a `presentation.actions` package
(coordinator amendment A8 to the 03 package table). Strong skipping is on by default (Kotlin 2.x); nothing turns it
off.

### 6.2 What a UI state type may hold (Konsist K-P3)

Classes named `*Ui` in `presentation` packages, and every type reachable from their properties inside our packages,
may hold only: primitives, `String`, enums, our `:core:model`/`:core:domain`/`:core:common` types,
`kotlinx.collections.immutable` types (`ImmutableList`, `PersistentList`, `ImmutableMap`, `ImmutableSet`), and other
such classes. Banned in them: `List`, `Map`, `Set`, `MutableList`, arrays, `Flow`, `StateFlow`, `MutableState`,
`Any`, lambdas. The rule runs over the Kotlin sources, so it fails the T1 that introduces the type.

### 6.3 Compiler reports as a gate

- A T2 step builds `-Poc.composeReports=true` for the `:ui:kit*` modules only (03 §19.2 already wires the reports
  directory) and runs `tool/perf/check_compose_reports.py`.
- It reads every `*-composables.txt` and fails when a **public** composable in `:ui:kit`, `:ui:kit-chat` or
  `:ui:kit-content` has a parameter marked `unstable`, or is `restartable` but not `skippable`, unless the
  function is listed in `config/compose-unstable-allowlist.txt` with a reason. The allowlist only shrinks (a ratchet,
  like the Flutter G16 kit count).
- Feature modules get the same report as information in the T2 summary (count of unstable parameters per module),
  not a gate.

```python
# tool/perf/check_compose_reports.py (core of the check)
pattern = re.compile(r'^(?P<flags>(restartable )?(skippable )?(readonly )?(inline )?)'
                     r'(scheme\("[^"]*"\) )?fun (?P<name>\w+)\((?P<params>.*)$')
# a parameter line inside the block starts with "  stable", "  unstable", "  runtime" or "  @static"
```

[U P13-7: the exact report line format of the Kotlin 2.4.21 Compose compiler; the script's unit test pins it with a
sample report from slice 0.]

### 6.4 Recomposition budgets (PB-17)

1. **JVM (T2):** 08's `RecompositionBudgetTest` and 09's `TranscriptRecompositionTest` and
   `OtherConversationQuietTest`. They count with a probe. Production code may not depend on the test-only `:ui:testing`
   (ADR §5.2), so the **interface** `RecompositionProbe` and the no-op `LocalRecompositionProbe` (a
   `staticCompositionLocalOf`) live in `:ui:kit` commonMain (`foundation/testing/RecompositionProbe.kt`, marked
   test-facing in its KDoc); only the **counting implementation** lives in `:ui:testing`. Six kit parts call
   `probe.hit(tag)` in a `SideEffect`: `KitRow`, `KitTurn`, `KitMarkdownBlock`, `KitWorkLine`, `KitComposer`,
   `KitStatusLine`. The release cost is one static local read per part [E: unmeasurable; checked once in PJ-05].
   08 §9.3's `RecompositionCounter` becomes that `:ui:testing` counting implementation (it counts inside the parts,
   which a wrapper around the call site cannot do once strong skipping skips the part); amendment A14 records the
   choice in 08.
2. **Device (T4):** the `stableBenchmark` build adds `androidx.compose.runtime:runtime-tracing`, pinned to **exactly**
   the Jetpack `androidx.compose.runtime:runtime` version that CMP 1.13.x resolves in `stableReleaseRuntimeClasspath`
   (a newer `runtime-tracing` would pull a newer runtime and the bench build would measure a different Compose than the
   release; §3.2 rule 2's classpath check catches it). Benchmark build only, so the D12 "no pre-release in release"
   rule holds. `:android:benchmark` adds `androidx.tracing:tracing-perfetto` and `tracing-perfetto-binary` (§3.3).
   PR-10 with `androidx.benchmark.fullTracing.enable=true` and `oc.pbtxt` passed through Macrobenchmark's
   `ExperimentalConfig` (one Perfetto session) counts slices named after the kit parts during 10 s of streaming with
   `sql/recompositions.sql`. Budget: settled-turn composables 0, live block ≤ 1 per publish.

---

## 7. Memory and image memory

### 7.1 Who holds memory, and the bounds

| Holder | Bound | Owner |
|---|---|---|
| Open transcript handles | LRU of 3 warm per connection; closed 60 s after the last holder (09 §14.4) | `:engine:transcript` |
| Finished-reply block cache | 512 parts per handle (09 §14.4) | `:engine:transcript` |
| Deferred parts and deltas | 256 keys and 1 MiB per handle (09 §3.2) | `:engine:transcript` |
| Code token cache | 48 entries (09) | `:ui:kit-content` |
| Coil memory cache | `maxSizePercent(context, 0.15)` and ≤ 48 MB | `:ui:kit-content` (`ImageLoading`, 10 M9) |
| Log ring / error list / `PerfTrace` | 128 entries + 256 KiB / 20 / 2,000 marks (03 §18.3) | `:engine:diagnostics` |
| `FieldFrameStats` | 60 one-minute buckets (≈ 10 KB) | `:android:platform` |
| Per live connection (session list, caches, OkHttp pool) | ≤ 8 MB when idle [E] | `:engine:connection` |
| Voice recognizer | only while recording + 20 s (10 M2) | `:android:media` |
| Natural voice (Kokoro, ≈ 148 MB weights + runtime) | only while reading aloud + 30 s [E]; released at once when voice capture starts (10 A1) or on memory pressure (PB-26b) | `:android:media` |
| `Redactor` memo | 512 recent short inputs; cleared when the known secret set changes (PB-73) | `:core:common` |
| Draft attachments | bytes + `Uri`, encoded to base64 only at send (media-voice map §8: a 20 MiB `data:` string was ≈ 27 MB per draft in Flutter) | `:engine:composer` |

### 7.2 Memory pressure

Since API 34 apps are no longer told about `TRIM_MEMORY_RUNNING_MODERATE`, `_RUNNING_LOW`, `_RUNNING_CRITICAL`,
`_MODERATE` or `_COMPLETE`, and `onLowMemory` is never called [V developer.android.com `ComponentCallbacks2`,
2026-11-01: "Apps are not notified of this level since API level 34"; `onLowMemory` "Since API level 34 this is
never called"]. OC_API37 and the owner's Android 16 phone are both past that, so `MemoryPressure` is built only from
signals that still arrive:

- `:android:platform` publishes `MemoryPressure` (`Normal`, `UiHidden`, `Background`, `Low`) through `DeviceFacts`
  (an addition to 11 CR11-5; coordinator amendment A9).
- `UiHidden` and `Background` come from `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)` and `(TRIM_MEMORY_BACKGROUND)`, which
  are still delivered.
- `Low` comes from `ActivityManager.getMyMemoryState()` (importance and last trim level) plus
  `ActivityManager.getMemoryInfo()` (`lowMemory`, or `availMem` below `threshold` × 1.5 [E]), **sampled at natural
  points only**: opening a transcript handle, starting a recording or read-aloud, decoding a picture larger than
  4 MP, starting heavy Linux work. Never by polling. Below API 34, `onLowMemory` and the running trim levels also map
  to `Low`.
- On `UiHidden`: transcript handles with no subscriber drop their derived blocks (they keep the engine state); the
  markdown and code caches drop to a quarter; Coil trims its memory cache (Coil registers its own callbacks).
- On `Background` or `Low`: the warm recognizer and the natural voice are freed at once (10 M2, PB-26b); handles
  beyond the selected conversation close; the 512-part caches drop to 64.
- Nothing is ever dropped that would cost a network refetch of durable data while the app is hidden (PB-53).
- PR-09's memory-fill step (§3.5) proves on the API 37 AVD that the caches drop.

### 7.3 Never judge native capacity by the Java heap

`ActivityManager.memoryClass` sizes Java-heap caches only (it is what Coil's `maxSizePercent` uses). Decisions about
native work (voice packs, the phone's Linux, agents) use **total RAM** (`MemoryInfo.totalMem`) and live
`MemAvailable`, never `memoryClass` (memory `voice-model-memory-gate`; 10 keeps the gate: Whisper small 3,400 MB,
base 1,536, tiny 1,024, Kokoro 2,000).

### 7.4 Image rules (PB-25)

1. All images go through `KitImage`/`KitZoomableImage`/`KitSvgImage` (kit only; 10 M9). Screens never call
   `AsyncImage`.
2. The kit passes the slot's size to Coil (`size(Size(widthPx, heightPx))` from the constraints); no
   `Size.ORIGINAL` outside the zoom viewer.
3. The zoom viewer decodes at most 4,096 px on the long edge; larger sources use Telephoto's sub-sampling in the
   kit's `androidMain` (media research §2.6) [U: needed only if a 12 MP screenshot stutters in PJ-14's viewer step].
4. Hardware bitmaps (Coil's Android default); `allowHardware(false)` only where pixels are read (copy image, QR
   decode from a picture).
5. Attachment thumbnails in the composer chips decode once at chip size and are cached by `ImageRef`.
6. PDF pages: one request at a time, visible pages only, ≤ 1,536 px long edge, ≤ 2 MP (10 M12, the isolated service).
7. Widgets carry no bitmaps (RemoteViews bitmap cap, android-surfaces §2.2).
8. The disk-cache question is a conflict between 09 §14.4 ("disk cache only for `file:`/`data:` decodes, 64 MiB") and
   10 M9 ("no disk cache"). Section 13 needs neither for its budgets; the coordinator resolves it (amendment A10).

`ImageSizingTest` (`:ui:kit-content` host test): a 4,000 × 3,000 source in a 96 dp chip at density 2.75 decodes to
≤ 264 × 264 px; in the viewer to ≤ 4,096 px long edge.

### 7.5 The phone's Linux is outside the app's PSS but inside its limits

- Android 12+ stops an app's child processes above 32 (builtin-linux 12, memory `phantom-process-limit`); the process
  budget (switch scope, 02 §3.5) holds new heavy work near the limit. The idle set is budgeted too (PB-74: ≤ 9
  children with the server and Paseo up and no turn; 0 new processes per list refresh; the usage collector on demand),
  so a native regression back to always-on helpers fails T4 instead of eating the agents' headroom.
- Android 17's memory limiter puts the app's children in its cgroup: about 5 GiB "not visible" on a 16 GB phone
  (android-surfaces §5) [U, proved in slice 0 on an API 37 emulator]. The process budget reads the app uid's summed
  RSS (`/proc/*/status VmRSS` of same-uid processes, already sampled by `NativeProcScan`) and holds new heavy work at
  80 % of that tier [E] once the hypothesis is proved. Owner of the rule: `:engine:runtime` (`ProcessBudget`).
- These are runtime behaviours, not app PSS budgets; section 13 measures them in PR-03 and PR-05 and reports them.

---

## 8. APK size and how the heavy parts are delivered

### 8.1 The packaging configuration (`:androidApp`)

```kotlin
// androidApp/build.gradle.kts (extends 03 §21; the ADR §9.9 splits)
android {
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true      // proot & co. must be extracted to nativeLibraryDir (ADR §9.9)
            // The universal APK otherwise carries every ABI found in dependencies (sherpa-onnx and zxing-cpp
            // ship 4 ABIs each). ndk.abiFilters cannot be combined with splits.abi, so exclude by path.
            excludes += setOf(
                "lib/armeabi-v7a/**", "lib/x86/**", "lib/armeabi/**", "lib/mips/**", "lib/mips64/**",
                "**/libsherpa-onnx-c-api.so", "**/libsherpa-onnx-cxx-api.so",   // Kotlin uses only the JNI lib [R media §8]
            )
        }
        resources {
            excludes += setOf("META-INF/*.version", "kotlin/**/*.kotlin_builtins", "DebugProbesKt.bin",
                              "kotlin-tooling-metadata.json")
        }
    }
}
```

Each resource exclusion is checked against `apkanalyzer files list` of the first slice-0 APK (WP-13.0); a line that
AGP already drops, or that removes something a library reads at runtime, is deleted from the list [U].

Checks at T2 (`check_apk_size.py`, below): the universal APK's `lib/` holds only `arm64-v8a/` and `x86_64/`; no
`libsherpa-onnx-c-api.so`; every `.so` has ELF `p_align` 0x4000 (`tool/native/check_so_alignment.py`, 10 §2.4). With
legacy packaging the libraries are deflated in the APK and extracted at install, so zip-level 16 KB alignment does not
apply; the ELF alignment does [R packages-build §05].

`DebugProbesKt.bin` is the coroutines debug agent resource and is safe to drop in release [U: confirm R8 does not
need it; it is only read by `kotlinx-coroutines-debug`].

### 8.2 Component sub-budgets (arm64, compressed, PB-33)

| Component | Cap [E] | Basis |
|---|---|---|
| sherpa-onnx (onnxruntime 7.9 + JNI 1.9) | ≤ 9.9 MB | 10 §2.5 |
| zxing-cpp | ≤ 0.8 MB | 10 §2.5 |
| CameraX (dex after R8) | ≤ 1.2 MB | 10 §2.5 |
| Coil 3 + svg + gif (dex after R8) | ≤ 1.0 MB | 10 §2.5 |
| qrose + licence texts | ≤ 0.2 MB | 10 §2.5 |
| **Media total** | **≤ 13.0 MB** | 10 §2.5 |
| All other dex (app code, Compose, Material 3 in the kit, Ktor, OkHttp, kotlinx, Metro, Nav3, Termux terminal) | ≤ 4.0 MB | demo 2.46 MB total APK for 3,170 lines with Compose + M3 + Ktor [R]; Flutter's Kotlin dex 2.0 MB compressed [R] |
| CMP resources (`composeResources/`: 7 languages × one string file per feature, Geist 4 TTFs, brand marks) | ≤ 1.5 MB | ARB en 1.76 MB raw incl. metadata [R]; Flutter fonts 0.74 MB raw [R] |
| Android `res/` + `resources.arsc` (strings_os, widget layouts, preview PNGs, launcher icons) | ≤ 0.6 MB | Flutter 1.4 MB incl. Flutter's own [R] |
| Built-in Linux `jniLibs` (proot, loader, talloc, shmem) + `libtermux.so` | ≤ 0.5 MB | 0.38 MB raw today [R packages-build §05] |
| AI Team engine (`libaiteam_*`) | **0** at the switch (D6) | §8.5 |
| `assets/dexopt/baseline.prof`, `META-INF`, manifest | ≤ 0.4 MB | binary profile must stay < 1.5 MB to be packaged [V Startup Profiles docs] |
| **Total** | **≤ 20.0 MB** working target (gate 22 MB) | |

`check_apk_size.py` sums `ZipInfo.compress_size` by path prefix (Python `zipfile`, no external tool), compares each
row with `perf/size-budget.json` and with the last green APK, and prints a table into the T2 summary. It also writes
`apkanalyzer apk compare` output when the cmdline-tools are present (testing-perf §10.1).

### 8.3 ABIs

| ABI | Shipped | Why |
|---|---|---|
| arm64-v8a | split APK + inside universal | the product |
| x86_64 | split APK + inside universal | the emulator (T3, T4) |
| armeabi-v7a | **no** (ADR §9.9) | today's Flutter release publishes a v7a APK (`android-release.yml:284`) for remote-client use; owner decision P13-D1 (§19) |
| x86 | no | — |

One versionCode for every output (ADR §9.9). The VPS-build → PC re-sign path signs every split and checks the signer
digest on each (testing-perf §10.1).

### 8.4 The voice engine

- Ships **inside** the APK (owner decision 2026-10-31 [R media-voice map]; 10 §2): `libonnxruntime.so` +
  `libsherpa-onnx-jni.so` ≈ 9.8 MB per ABI compressed, 27 MB raw extracted.
- Never downloaded: Android 10+ forbids executing code from the writable app home, and Google says to load only code
  embedded in the APK [V media research §7].
- Models stay downloads (Whisper tiny/base/small 103–375 MB, Kokoro 148 MB) into `files/voice_models/` with SHA-256
  pins, resumable, unchanged paths (10).
- Later saving (not before the switch): a reduced-operator ONNX Runtime build for exactly the Whisper and Kokoro
  graphs. Expected saving 4–6 MB compressed [E]; experiment E6 (§20).

### 8.5 The AI Team engine (when D6 brings AI Team back)

- At the switch: **not in the APK** (`libaiteam_*` absent; the hash-check tasks and `keepDebugSymbols` exclusions
  return with it, ADR §9.9).
- When it returns, default (P13-D2 option a): `jniLibs` as today (+≈ 3.1 MB arm64 compressed: engine 2.44, probe 0.53,
  sandbox ≈ 0.15 [R packages-build §07]), executed from `nativeLibraryDir`, attested by
  `aiteam-engine-manifest.json`. The size gate rises by exactly the measured amount in that release, named in its notes.
- Alternative (option b): a pinned download into the Linux rootfs, run under proot like the Gas City binaries
  (`aiteam-assets-N`). No APK cost; costs ptrace overhead on a server process (the audit saw tracer kernel time
  dominate [R]) and changes the attestation model.

### 8.6 The built-in Linux

| Part | Delivery | Size |
|---|---|---|
| proot, loader, talloc, android-shmem | `jniLibs`, arm64 + x86_64, executed from `nativeLibraryDir` | ≈ 0.37 MB raw [R] |
| Ubuntu Base 24.04.5 | download from `cdimage.ubuntu.com`, SHA-256 pinned per ABI, resumable `Range` (builtin-linux 02) | not in the APK |
| Node 24.21.0, OpenCode 2.1.1, Paseo, Claude Code, Python | the setup engine's component scripts, pinned and verified (builtin-linux 34) | not in the APK |
| The rootfs on disk | `files/linux/**`, never moved or touched by the migration (ADR §9.7) | several GB |

Download performance rules (shared downloader, ADR `:core:net`): progress published to the UI at most 4 times per
second and to the notification in 1 % steps (media-voice map §8; PB-89 makes this the one rate for every download,
replacing 10 §18's ≤ 10/s, amendment A18); large downloads on mobile data ask first (B-STATE-030); installs are "heavy
work" for the process budget and the heat tier Hold (§13). The proot speed factor that matters most is seccomp
acceleration: PB-75 asserts it stays on, and `perf/sensitive-paths.txt` sends any change to `--link2symlink` or the
seccomp environment handling through PR-03.

### 8.7 Dependencies that never enter the release classpath (size)

Added to `BannedDependenciesTest` (S-23) with a size reason: `androidx.compose.material:material-icons-extended`
(the kit generates Phosphor vectors, 06 K6), `org.jetbrains.kotlin:kotlin-reflect`, `com.google.guava:guava` (the
`ListenableFuture` from profileinstaller needs only `androidx.concurrent:concurrent-futures`), Jackson, Gson,
`io.ktor:ktor-client-content-negotiation` (ADR §8: no ContentNegotiation), `androidx.pdf:*` (10 M12),
`androidx.core:core-splashscreen` (SR6), Media3 (media research §8: no use).

### 8.8 The generated glyphs

06 §4.11 generates Phosphor glyphs as `val Back: ImageVector by lazy { … }`. One file of `by lazy` properties creates
every `Lazy` object (and its lambda) when the file's class initializes, the first time any glyph is touched.
Recommended pattern (the one Compose's own icon artifacts use): a nullable backing field and a getter per glyph, and
generate only the glyphs the kit and features reference.

```kotlin
// ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/foundation/glyphs/Back.kt (generated, one file per glyph)
public val KitGlyphs.Back: ImageVector
    get() {
        _back?.let { return it }
        return ImageVector.Builder(name = "Back", defaultWidth = 24.dp, defaultHeight = 24.dp,
                                   viewportWidth = 256f, viewportHeight = 256f, autoMirror = true)
            .apply { path(fill = SolidColor(Color.Black)) { /* generated */ } }
            .build().also { _back = it }
    }
private var _back: ImageVector? = null
```

Coordinator amendment A3 to 06 (the kit owner decides). R8 removes unreferenced glyph getters either way; the change
avoids the start-up cost, which `app.graph`/`shell.firstComposition` would show.

---

## 9. R8 full mode, keep rules, DEX layout

### 9.1 Configuration

```kotlin
// androidApp/build.gradle.kts (AGP 9.3.3)
android {
    buildTypes {
        getByName("release") {
            optimization { enable = true }      // R8 full mode (default since AGP 8.0) + resource shrinking [V testing-perf §9.4]
        }
    }
}
// Keep files: androidApp/src/main/keepRules/*.keep  (AGP 9.3 layout). KMP modules publish consumer rules through
// the convention plugin (ADR §9.9).
```

### 9.2 The only keep files

| File | Content | Why |
|---|---|---|
| `attributes.keep` | `-keepattributes SourceFile,LineNumberTable` and `-renamesourcefileattribute SourceFile` | readable frames after retrace (the problem report carries ≤ 12 frames, 03 §18.1) |
| `sherpa.keep` | `-keep class com.k2fsa.sherpa.onnx.** { *; }` | the AAR ships no rules and JNI reads config fields by name [R media §3] |
| `workers.keep` | `-keep class io.github.eslamasabry.opencode_mobile.ScheduledPromptWorker { public <init>(android.content.Context, androidx.work.WorkerParameters); }`, same for `WidgetStatusWorker`, and `-keep class androidx.work.impl.WorkDatabase_Impl { public <init>(); }` | the Flutter-era start-up crash (APK 900055, fix `c664ca2ae`) [R packages-build §06]; WorkManager's own rules should cover it, this stays as belt and braces |
| `logging.keep` | `-assumenosideeffects class io.github.eslamasabry.oc.core.common.Log { public void d(kotlin.jvm.functions.Function0); }` | removes debug log calls and their lambdas from release (Kermit's release minimum is Info anyway, 03 §18.2) [U P13-8: exact JVM signature after compilation] |
| `missing.keep` | only lines copied from AGP's `build/outputs/mapping/<variant>/missing_rules.txt`, one by one | the demo's blanket `-dontwarn io.ktor.**` is not repeated |

### 9.3 Banned in any keep file (`KeepRulesLintTest`, `:quality`, T2)

`-dontoptimize`, `-dontshrink`, `-dontobfuscate`, `-keep class io.github.eslamasabry.** { *; }` and any `{ *; }` on
our own packages, `-keepnames class **`, `-dontwarn **`, `-ignorewarnings`. Global options in KMP consumer rules are
filtered by AGP 9 anyway [V testing-perf §9.4]; the lint catches them in the app's own files.

### 9.4 Proof that R8 did no harm

1. `:androidApp:analyzeStableReleaseR8Config` at every T2 (ADR §9.9). Its shrink, optimize and obfuscate scores go
   into the T2 summary; a score that drops fails T2 unless the merge brief names the reason (a ratchet) [U P13-9:
   whether the HTML report has a machine-readable score; else parse the HTML].
2. The DEX guards keep working on the real APK: `verify_scheduled_prompt_apk.py` and `verify_widget_release_rules.py`
   ported to `native/tool/qa/` (packages-build §06).
3. T3 runs every flow on the minified build (testing-perf §9.4); the benchmark build is minified too.
4. Mapping files per build: `native/build/vps-apk/mapping-<versionCode>-<sha>.txt`, kept 400 days like the Dart
   symbols today [R packages-build §07]; `retrace` reads them for problem reports.

### 9.5 DEX layout

- The Startup Profile drives R8's DEX layout: startup classes go first into `classes.dex` (on by default since AGP 8.3;
  needs R8) [V developer.android.com, updated 2026-10-23].
- "Startup code should fit into `classes.dex`"; keep the startup journeys short (§10.2) [V same page].
- PB-35 checks it (§10.5).

---

## 10. Baseline and Startup Profiles

### 10.1 Configuration

```kotlin
// androidApp/build.gradle.kts
plugins { id("androidx.baselineprofile") }                 // 1.5.0
dependencies {
    implementation(libs.androidx.profileinstaller)          // 1.4.1
    baselineProfile(project(":android:benchmark"))
}
baselineProfile {
    automaticGenerationDuringBuild = false                   // never generated by a Gradle build; §3.10 step 4
    saveInSrc = true
    mergeIntoMain = true                                     // one profile for stable and preview flavours
}
```

Generation does not use the plugin's Gradle task: §3.10 step 4 installs `stableBenchGen` (non-minified, `.bench`,
with the seed hooks, §3.2) and runs `BaselineProfileGenerator` with `am instrument`; the perf lead copies the pulled
`*-baseline-prof.txt` and `*-startup-prof.txt` to `androidApp/src/main/generated/baselineProfiles/baseline-prof.txt`
and `startup-prof.txt`, the folder the plugin reads with `mergeIntoMain`. 16 RB-4 names
`androidApp/src/main/baseline-prof.txt`; amendment A15 moves 16 to this path so both sections check the same file.

### 10.2 The generator journeys

```kotlin
// android/benchmark/src/main/kotlin/io/github/eslamasabry/oc/benchmark/profile/BaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    // Runs against stableBenchGen (non-minified, ….bench, src/benchmark/ hooks; §3.2), never the plugin's
    // nonMinifiedRelease (main package, no hooks).
    @Test fun startup() = rule.collect(packageName = BenchTarget.PACKAGE, includeInStartupProfile = true) {
        BenchTarget.seed(this, hostId = "emulator", port = 4190)
        pressHome(); startActivityAndWait()
        uiAutomator { onElement { textAsString() == "Fixture: empty" } }        // Conversations drawn from cache
    }

    @Test fun dailyPath() = rule.collect(packageName = BenchTarget.PACKAGE, includeInStartupProfile = false) {
        startActivityAndWait()
        uiAutomator {
            onElement { textAsString() == "Fixture: 300 turns" }.click()          // open chat (PJ-04)
            activeWindow().waitForStable()
            onElement { viewIdResourceName == "chat-composer-field" }.setText("perf:stream-short")
            onElement { viewIdResourceName == "chat-send-button" }.click()       // stream (short fixture, 15 s)
            onElement(30_000) { textAsString() == "END-OF-FIXTURE" }
        }
        device.pressBack()                                                        // keyboard down
        device.pressBack()                                                        // back to Conversations
        uiAutomator { onElement { contentDescription == "Settings" }.click() }   // Settings + two pages
        uiAutomator { onElement { textAsString() == "Appearance" }.click() }; device.pressBack()
        uiAutomator { onElement { textAsString() == "This phone" }.click() }; device.pressBack()
        device.pressBack()
    }
}
```

- Only `startup()` feeds the Startup Profile (`includeInStartupProfile = true` only for journeys essential to the
  initial display [V docs]).
- `perf:stream-short` is a 15 s cut of `perf:stream-6000` (fixture owner: section 14).
- New screens join `dailyPath` when their slice lands (Files viewer slice 5, voice slice 5). Keep the whole run under
  5 minutes.

### 10.3 When profiles are regenerated

- At every slice end (T4), after any Kotlin/Compose/AGP/R8 version bump, and when a merge touches
  `perf/sensitive-paths.txt` paths listed as `profile` (start-up path, `:ui:shell`, `:feature:chats`, `:feature:chat`).
- Only the perf lead commits the two files, in a commit of their own:
  `perf(profiles): regenerate baseline and startup profiles at <sha> [skip ci]`.
- Gate: PB-10 (`Partial(Require)` at least 15 % faster cold start than `None()`), the size of
  `assets/dexopt/baseline.prof` < 1.5 MB [V docs] (`check_apk_size.py`), and 16's P16-5 two-build determinism check
  run on the candidate profile (two VPS builds of the same commit, different paths and worker counts, give
  byte-identical `baseline.prof`/`baseline.profm`; F-Droid lists them as a known source of drift). A profile that is
  not reproducible is not committed.

### 10.4 Sideloaded reality

- Outside Play, the Baseline Profile is compiled when background dexopt runs, "likely overnight" (idle and
  charging); Cloud Profiles are Play-only [V testing-perf §9.3]. ProfileInstaller writes the reference profile at
  first launch.
- The Startup Profile's DEX layout is baked into the APK and helps from the very first launch; that is why it is
  generated even though Play is not used.
- The demo reached ≈ 0.25 s with library profiles only [R], so the app profile is a margin, not the main lever.
- `ProfileVerifier` status is shown on App diagnostics' Kept page (07 L02) and in the problem report (§3.6).
- So the first launch after every update runs uncompiled until the phone rests: on switch day that is also the
  migration launch, the owner's first impression. PB-01u budgets that state (`CompilationMode.None()`, ≤ 400 ms on
  the phone [E]).
- Experiment E1 (§20): a `base.dm` (DexMetadata) written next to the APK in the `PackageInstaller` session, so updated
  users get compiled code on the first launch after each update. Unknown on Android 12–17 (the manual docs show
  Android 9–11 only) [U testing-perf §13.7]. E1 runs **before slice 6**. If it works, the `.dm` goes into both
  installers: the native updater and the Flutter bridge release's installer (ADR D4), because the switch install
  itself is done by the bridge release.

### 10.5 `check_startup_dex.py` (PB-35)

1. Read `startup-prof.txt`; collect class descriptors (`Lcom/…;`) from its rules.
2. Map them through the release mapping file to obfuscated names.
3. List classes per DEX file in the arm64 APK with `apkanalyzer dex packages --defined-only --files classes.dex <apk>`
   (and `classes2.dex`, …) [U P13-10: flags of the installed cmdline-tools version].
4. Pass when ≥ 95 % of the startup classes are in `classes.dex`.

---

## 11. Background CPU, timers, wake-ups, foreground services and jobs

### 11.1 Visibility, link demand and work gates

- `DeviceFacts.appVisible` (11 CR11-5) comes from `ProcessLifecycleOwner` in `:android:platform`.
- `LinkDemand` (04 §6.4) is computed in `:engine:connection` from `appVisible` plus "the reply watch runs" plus "Stay
  connected is on". It decides which streams stay open (three values, unchanged).
- Timers need a finer split, because Flutter runs its background monitors hidden **only under live mode**
  (`backgroundAllowed: keepLiveInBackground && backgroundLive.active` [R `lib/state/connection/lifecycle.dart:93-143`])
  and not under a plain reply watch. So the one shared timer value is `Visibility` in `:core:common` =
  `Foreground | HiddenLive | HiddenReplyWatch | HiddenIdle`, a `StateFlow` bound at graph level: Foreground when
  visible; HiddenLive when Stay connected is on (and its service runs); HiddenReplyWatch when only the reply watch
  runs; HiddenIdle otherwise. Engines below `:engine:connection` in the ADR §6.2 order receive the same `StateFlow`
  through DI, which breaks no module rule.
- Runtime work (the phone's Linux, setup, heat) does not follow link demand: a hidden app with a running proot or a
  setup job has no stream but is not idle. Such timers name a **work gate** (`WorkGate`) and tick while it holds,
  whatever the link demand. The gates are bound at graph level from `:engine:runtime` and `:android:linux`
  (`ProotRunning`, `SetupJobRunning`, `LeaseHeld`, `HeatCollector`).

### 11.2 One way to repeat work: `Pacer` and the timer tables

```kotlin
// core/common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/pace/Pacer.kt
public enum class Visibility { Foreground, HiddenLive, HiddenReplyWatch, HiddenIdle }

/** Work that makes the app "not idle" for PB-40 even with no link demand. */
public enum class WorkGate { ProotRunning, SetupJobRunning, LeaseHeld, HeatCollector }

/** How often a repeating job runs in each visibility. null = it does not run (no timer is armed). */
public data class Cadence(
    val foreground: Duration?,
    val hiddenLive: Duration?,
    val hiddenReplyWatch: Duration?,
    val hiddenIdle: Duration?,
    val jitter: Double = 0.0,                 // ±fraction of the period, 0.0–0.5
)

/** A declared repeating job. Every instance lives in a file named `*Timers.kt` (Konsist K-P2). */
public data class TimerSpec(
    val name: String,                         // trace-safe, e.g. "runtime.recoveryCheck"
    val cadence: Cadence,
    val reason: String,                       // one line: what breaks if it stops
    val rows: List<String> = emptyList(),     // catalog rows / PB ids it serves
    val gates: Set<WorkGate> = emptySet(),    // non-empty: ticks only while at least one gate holds
) {
    init {
        require(cadence.hiddenIdle == null || cadence.hiddenIdle >= 60.seconds || (gates.isNotEmpty() && rows.isNotEmpty())) {
            "PB-42: hidden-idle timers ≥ 60 s unless gated on work that means 'not idle' and tied to a row"
        }
    }
}

public val AlwaysActive: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

/** Bound at graph level from :engine:runtime and :android:linux (§11.1). */
public interface WorkGates { public fun holds(gate: WorkGate): StateFlow<Boolean> }

public interface Pacer {
    /** Ticks on [spec]'s cadence for the current visibility, only while [active] is true and (if the spec has gates)
     *  one of its gates holds. Suspends with no timer armed otherwise. The first tick after a change to Foreground
     *  comes at once (a returning person sees fresh state). */
    public fun ticks(spec: TimerSpec, active: StateFlow<Boolean> = AlwaysActive): Flow<Unit>
}
```

`DefaultPacer(visibility: StateFlow<Visibility>, gates: WorkGates, clock: Clock, dispatchers: AppDispatchers)` is the
only implementation; tests use `TestPacer` from `:core:testing`, driven by virtual time. A job's own condition ("while
a turn is open", "while the page is open") is the `active` flow.

Rules:

1. No `while (true)`, `while (isActive)` or recursive `delay` loop outside `:core:common` `pace/`; no
   `kotlinx.coroutines.channels.ticker`. Two kinds of loop are allowed elsewhere, because `Pacer` handles only fixed
   cadences:
   - **Retry loops** (04 §6.2 `EventStreamLoop`, the resumable downloader, the Paseo and Codex reconnects): the
     function is annotated `@RetryLoop` (declared in `pace/`) and every `delay(` in it waits a value returned by
     `BackoffPolicy.delayMs(…)` (04 §6.2; a new preset is a 04 amendment). A literal or constant delay inside a
     `@RetryLoop` still fails.
   - **The UI clock** `KitClock.kt` (§5.4 R-F6).
   Fixed-cadence keep-alives (the Paseo app ping, the Codex ping) are `TimerSpec` rows, not loops. Konsist K-P1 checks
   this (§18). One-shot `delay` (debounce, a deadline) is fine.
2. Every `Pacer.ticks(…)` argument is a `TimerSpec` constant declared in a `*Timers.kt` file of the same module
   (Konsist K-P2).
3. Each module's `*Timers.kt` ends with `internal val ALL_TIMERS: List<TimerSpec> = listOf(…)` naming every spec in
   the file, and the module's `commonTest` has `` `PB-42 timer table`() `` asserting: `hiddenIdle == null ||
   hiddenIdle >= 60.seconds`, unless the spec has work gates and names a row (also enforced by `TimerSpec`'s
   `init`); `hiddenLive` and `hiddenReplyWatch` ≥ 5 s unless the spec has work gates or names a catalog row that
   needs faster; every spec has a non-empty `reason`; every top-level `TimerSpec` in the file is in `ALL_TIMERS`.
   Konsist K-P2 also fails a module that has a `*Timers.kt` file and no such test (`TimerTableTest` in §15.1 is this
   set of per-module tests). No reflection is used.
4. Android-side repeating work (`:android:*`, in Kotlin threads or `Handler`s today) follows the same table through
   `ProcessTimers.kt` in `:android:linux`, checked by a host test.

### 11.3 The timer inventory and the rows this section decides

**The inventory is the complete list, not the table below.** The Flutter reference has 47 `Timer.periodic` sites in
`lib/` [R `grep -rn "Timer.periodic" lib`, 2026-11-01], plus re-armed one-shot `Timer`s (ProfileMonitor, the provider
quota monitor, usage subscriptions, the Paseo usage monitor), `Future.delayed` loops, and native `Handler`, thread and
executor loops in `android/`. Before WP-13.5 starts, WP-13.5a runs `tool/perf/timer_inventory.py` over the reference
and writes `perf/timer-inventory.csv`, one row per job: Flutter file:line, Flutter cadence, run condition, what Flutter
does in each visibility, native owner module, native `TimerSpec` name, and one of `parity`, `native change: <reason>`
or `dropped: <reason>` (a drop needs the coordinator's OK). UI clocks map to `rememberKitClock` (§5.4 R-F6); demo and
fixture gateways map to `dropped: test-only`. **A job that is not in the inventory does not run.** A new job needs an
inventory row, a `TimerSpec` and the coordinator's OK in the brief. WP-13.5's accept is "every inventory row resolved
and migrated", not this table.

The table below holds only the rows this section decides, corrects or changes. Values are Flutter's (parity) except
where a cell says "native change". "—" means the job does not run.

| Spec (module) | Foreground | Hidden, live | Hidden, reply watch | Hidden, idle | Source |
|---|---|---|---|---|---|
| `connection.busyPoll` (`:engine:connection`) | one 5 s timer doing three things: (a) re-read the session list while the event stream is **not connected**; (b) re-read busy sessions' status while connected and a turn is busy (a lost terminal `idle` must not leave the chat working forever); (c) phone-chat admission status while connected and in the foreground (must not strand admission in UNKNOWN) | 5 s, (a) and (b) (the transport stays up under live mode) | 5 s, (b) for the watched connection only — **native change**: Flutter's Dart side is suspended here, but the native reply watch keeps that one stream open and a lost `idle` would hold the FGS until its cap | — | `lib/state/connection/sessions.dart:522-547`, `status.dart:195` [R] |
| `requests.waitingReread` (`:engine:requests`) | 15 s while **any** turn of a held handle is open on a live stream (not only a known wait: the re-read exists for asks the stream lost, so the app cannot know a turn is waiting) | same | same, for the watched turn | — | `lib/domain/turn_stall.dart:13` `turnStallRereadEvery`, `lib/state/connection/turn_stall.dart:174-192` [R]; 09 §14.5; memory `oc2-stuck-tools-root-cause` (a missed ask hangs until the 60-min sweep) |
| `transcript.stallTick` (`:engine:transcript`) | 5 s while a turn is open and held | same | same | — | 09 §14.5 |
| `connection.localAlive` (`:engine:connection`) | 10 s while the link to an in-app server is not connected | 10 s, same | — | — (**native change**: one of the hidden 10 s timers of audit #11) | `status.dart:316-338` [R] |
| `connection.agentDaemonProbe` (`:engine:connection`) | 5 s while an agent backend's link is down and not suspended | 5 s, same | — | — | `status.dart:340-366` [R] |
| `connection.phoneAgentsStartup` (`:engine:agents`) | 3 s for the first 30 s after a phone-agent backend starts [R `phone_agents_startup.dart:15-19`], then the slower follow-up phase the inventory records | — | — | — | inventory confirms the second phase |
| `agents.backendWatch` (`:engine:agents`) | 10 s | 60 s | 60 s while the watched turn is an agent's | — | freeze tip `afd0c6bf3`, `f9598e8ad` [R] |
| `paseo.keepAlive`, `codex.keepAlive` (`:protocol:paseo`, `:protocol:codex`) | app-level ping 20 s while the socket is open (the WebSocket ping frame is OkHttp's `pingInterval(20 s)`, a transport setting) | same | same, for the watched agent's socket | — (socket closed) | `lib/paseo/transport.dart:435` [R] |
| `monitor.profiles` (`:engine:connection`) | 1 min | 5 min | — | — | `lib/state/profile_monitor.dart:63-64`, `lifecycle.dart:93-143` [R] |
| `monitor.providerQuota`, `monitor.usageSubscriptions`, `monitor.paseoUsage` | 5 min | 15 min | — | — | `provider_quota_monitor.dart:171-172`, `usage_subscriptions_controller.dart:117-118`, `paseo_usage_monitor.dart:38-39` [R]; PB-42b |
| `runtime.recoveryCheck` (`:engine:runtime`) | 5 s while the server is down or restarting, else 45 s | — | — | — | builtin-linux 21; Flutter `perf_builtin_pollers_test` "healthy server checked every 45 s in the foreground, none away", "a server that went down is rechecked every 5 s" [R] |
| `runtime.setupFollow` (`:engine:runtime`, gate `SetupJobRunning`) | 500 ms while a setup job runs; once a minute while a finished job's page is open; a watcher arriving brings the next read forward | 500 ms while a setup job runs (SetupService FGS) | same | same (gated; the setup job means "not idle" for PB-40) | Flutter `perf_builtin_pollers_test` [R]; rows: PB-42 and the setup-progress catalog row the inventory names |
| `runtime.processSample` (Running on this phone) | 2 readings 1 s apart, every 10 s while the page is open | — | — | — | builtin-linux 11 |
| `runtime.thisPhoneRunning` | 15 s while This phone is open | — | — | — | `lib/ui/screens/this_phone_screen.dart:166` [R] |
| `runtime.heatInputs` (`:engine:runtime`, gates `ProotRunning`, `HeatCollector`) | thermal status by listener; headroom ≤ every 30 s while collected (11 CR11-5) | headroom every 30 s while any proot runs or the heat tier has a collector | same | same (gated) — the hot-phone case this tier exists for is exactly "hidden, agents working, no watched turn" | §13; 11 CR11-5 "only while collected" |
| `diagnostics.updateCheck` (`:engine:diagnostics`) | on cold start and each return to the foreground, if 6 h passed since the last success (30 min after a failure), only while the switch is on; "Check now" is exempt | — | — | — | PB-55; Flutter `app_update_controller.dart` [R]; 16 §8.2 |
| `linux.orphanReaper` (`:android:linux`, gate `ProotRunning`) | 60 s while any proot runs; on `soon()` after a proot start/end | same | same | same (gated) | builtin-linux 10 |
| `linux.leaseRenew` (`:android:linux`, gate `LeaseHeld`) | 5 min while a lease is held (10–15 min hold); capped after 6 h of one unbroken stretch | same | same | same (gated) | `reply_watch.dart:142-144`, `phone_agent_work_watch.dart:22-24` [R] |
| `linux.leasePulse` (`:android:linux`) | today: 1 s while a lease is held [R `NativeWorkLeaseHost.kt:265`] | — | — | — | **native change** (WP-13.7, after the move-only split of 11 §5): one `Handler.postAtTime` for the next lease expiry instead of a 1 s pulse; `WorkLeasesTest` unchanged |
| `linux.idleCheck` alarm (request code 4095) | — | armed only while the idle-stop policy is on and the phone server runs | same | same | builtin-linux 25 |

The previous `attention.monitor` row is removed: the 10 s timer it cited (audit #11) is the local-server alive probe,
now `connection.localAlive`.

**Known rows the inventory must contain** (cadences from the Flutter reference [R] or the in-flight branches; the
per-visibility cells come from the inventory):

- Watching-mode transcript re-read every 4 s (`lib/ui/screens/chat/watching.dart:42`); ProjectDevServer every 15 s
  (`lib/state/project_dev_server.dart:29`); phone-server healing every 15 s (`lib/builtin/phone_server_healing.dart:243`).
- TermuxRunawayWatcher every 60 s; the Termux host setup poll every 1 s while it runs.
- Linux job log poll every 2 s while its page is open; Linux vitals every 5 s and the Services page every 2 s (both on
  in-flight switch-scope branches); the rootfs `bytesUsed` walk at most every 10 min.
- The installer ownership monitor and the migration transport at 100 ms while they run; the phone project scan at
  250 ms while it runs (`lib/platform/phone_project_scan.dart:404`).
- Voice recording clock 100 ms and read-aloud playback head 100 ms (both `rememberKitClock`/foreground-only UI
  clocks); the 1 s UI clocks of `kit_status_line`, `kit_status_answer`, `kit_composer_layer`, `chat_notices`,
  `file_share_sheet` and the setup screens (`rememberKitClock`).
- The AI Team rows (`cycles.dart`, `team_*`, `phone_engine_gateway.dart`) are inventoried as `dropped: returns with
  AI Team (D6)`.

### 11.4 Wake-ups, wake locks, FGS and jobs

| Mechanism | Allowed use | Budget |
|---|---|---|
| Exact alarms | scheduled prompts the person made (`ScheduledPromptReceiver`) | PB-43 |
| Alarm (same type as today's `NativeIdleTimer`) | phone-server idle check (request code 4095), armed only while the idle-stop policy is on and the server runs | PB-43 |
| WorkManager | schedule fallback worker (≤ 8 min, ADR §9.5); migration follow-up (05 §7.1); widget status worker | no periodic request (PB-46) |
| Partial wake lock `OpenCode:work` | inside `WorkLeases` (10–15 min hold, renew 5 min, ≤ 6 h per unbroken stretch, released when the work ends) | PB-44 |
| Partial wake lock for Linux services | the "Keep the phone awake while services run" switch (owner 2026-10-31, linux-manager S5): only while charging and a service is on, once that slice lands; the backup copy's lock while a backup runs if 11 W11-06 keeps one [U] | PB-44 |
| `dataSync` FGS | reply watch (S11-4) and Stay connected; 6 h per 24 h budget | PB-45 |
| `shortService` FGS | widget actions (≤ 30 s) | 11 §9 |
| `specialUse` FGS | phone server, setup | runs only while the person's server or install runs |
| `JobScheduler` direct | none | — |
| `setAlarmClock`, `setExactAndAllowWhileIdle` beyond schedules | none | — |
| Doze exemptions | the battery-optimization exemption is asked only at the first phone-server start (B-ANDR-025) | — |

### 11.5 The "agent wakes the whole app" lesson (PB-41)

The freeze tip fixed a Flutter bug where a streaming phone agent woke the whole app on every frame (56 % → 7 % of a
core on the emulator, commit `4ae686c5b` [R]). Native rules that keep it fixed:

- Paseo feed frames go to the agent's own `ConnectionGraph` (`:protocol:paseo` → `:engine:transcript` handle); a
  handle with no subscriber applies events but skips derivation (09 §14.5).
- The attention feed and the status-line combiner subscribe to **coalesced** summaries (`StateFlow` with
  `distinctUntilChanged`), never to the raw event stream.
- `PR-02` measures it with a fake agent at 20 frames per second.

---

## 12. Network and radio

### 12.1 The reconnect schedule (PB-51, PB-52)

04 N4-D6 keeps Flutter's foreground schedule (1, 2, 4, 8, then 16 s, no jitter, reset after 30 s stable). The freeze
tip added the hidden schedule (commit `1da77af8f`: "out of sight the OpenCode 2 event stream retries after 1 s, 2 s,
then every 60–120 s (jittered), skips a try when Android has no network, and tries at once on return" [R]). S-18 makes
that a switch gate, so 04's `BackoffPolicy` gets a visibility input. **04 §6.2's `BackoffPolicy` stays the
authoritative type** (a data class in the single-owner file `core/net/.../stream/BackoffPolicy.kt`, with `baseMs`,
`capMs`, `preIncrement`, `jitter`, `resetAfterStableMs`, `disconnectedLabelAfter`, `stallTimeoutMs`, the
`OpenCode2` and `GasCity` presets, the 45 s stall timeout and the 6-failure label). Coordinator amendment A2 to 04
N4-D6/§6.2 **extends** it; nothing is removed:

```kotlin
// core/net/src/commonMain/kotlin/io/github/eslamasabry/oc/core/net/stream/BackoffPolicy.kt (04 §6.2, extended by A2)
public data class BackoffPolicy(
    val baseMs: Long,
    val capMs: Long,
    val preIncrement: Boolean,
    val jitter: ClosedFloatingPointRange<Double>?,
    val resetAfterStableMs: Long = 30_000,
    val disconnectedLabelAfter: Int = 6,
    val stallTimeoutMs: Long,
    val hiddenAfterAttempt: Int = 2,                    // A2: from the 3rd hidden try on, wait hiddenRangeMs
    val hiddenRangeMs: LongRange = 60_000L..120_000L,   // A2: uniform, jittered
) {
    /** 04's formula, unchanged, for Foreground. */
    public fun delayMs(attempt: Int, random: Random): Long
    /** A2. Foreground → delayMs(attempt, random). HiddenLive / HiddenReplyWatch → delayMs for attempts ≤
     *  hiddenAfterAttempt, then a uniform draw from hiddenRangeMs. HiddenIdle → null: the stream is cancelled
     *  (Suspended, 04 §6.4) and nothing is dialled. */
    public fun delayMs(attempt: Int, random: Random, visibility: Visibility): Long?
    public companion object {
        public val OpenCode2: BackoffPolicy = BackoffPolicy(500, 16_000, true, null, 30_000, 6, 45_000)
        public val GasCity: BackoffPolicy = BackoffPolicy(500, 30_000, false, 0.5..1.5, 30_000, Int.MAX_VALUE, 90_000)
    }
}
```

`EventStreamLoop`'s wait is a `@RetryLoop` (§11.2 rule 1): it waits only for values from `delayMs(…)`.

`EventStreamLoop` (04 §6.2) also:

- calls `NetworkGate.awaitUsable(hostClass)` before every non-loopback try, so no attempt is spent offline (04 N4-D8);
- on a change to `Foreground` cancels a pending long wait and tries at once (`retryNow()`), counting it as attempt 1;
- never shortens a hidden wait because of a network change unless the default network id changed (then attempt 0,
  04 N4-D8).

Tests (`:core:net` `commonTest`, `TestPacer`/virtual time): `PB-51 hidden retries 1 s 2 s then 60–120 s`,
`PB-51 return to foreground retries at once`, `PB-52 no dial while offline`, `PB-50 one request serves both
channels` (with B-PROTO-134), the stall rule `B-PROTO-132` (45 s read stall).

### 12.2 Radio behaviour, in plain terms

- An open SSE stream with the server's 15 s heartbeat keeps the cellular radio awake for as long as it is open. That
  is the price of live updates, and it is paid only in the **Foreground**, **HiddenLive** and **HiddenReplyWatch**
  states. The reply
  watch ends ≤ 5 s after the turn settles (PB-45), so hidden radio time is bounded by the person's own running work.
- "Stay connected" (live mode) is the one path that can keep the radio awake for hours. Its row says so in plain
  words (11 §9): "Keeps a connection open while the app is closed. Uses more battery on mobile data."
- In **HiddenIdle** the app makes no request at all except the person's schedules (PB-53).
- The model list of the phone's own server is not fetched at server start (PB-56; owner decision 5 of the audit, "yes").
- The update check runs on cold start and on each return to the foreground, at most once per 6 h (30 min after a
  failure), only while its switch is on, never while hidden and never as a wake-up (PB-55, 16 §8.2, the owner's
  updater design). Its HTTP request is the only GitHub call outside a download the person started; "Check now" is
  the person's own request and is not capped.
- Downloads over 50 MB on mobile data ask first (B-STATE-030 thresholds unchanged).

### 12.3 Measuring the radio (audit decision 6: "measure for a day before changing it")

- Emulator: the fixture server's request log and connect-attempt counts (PR-01, PR-04) prove the rules.
- Phone (passive, owner OK, read-only adb): after a normal day, `dumpsys batterystats --charged
  io.github.eslamasabry.opencode_mobile` → "Mobile radio active" time and the per-uid network bytes. The first reading
  is taken on the Flutter app before the switch (the baseline the audit asked for: radio was 28 % of the app's drain
  [R]) and again after the switch.
- Analytics (18 §4.3, owner 2026-11-01): sends are batched (≤ 4 × 25 per upload), made when the app goes to the
  background (unmetered networks only) and at most every 30 minutes in the foreground, and never schedule a wake-up.
  No mobile-data upload in the background. Until the owner picks a destination (16 D16-1) nothing is sent.

---

## 13. Battery and heat

### 13.1 The heat tier (port of the freeze-tip `HeatTierPolicy`, switch scope 02 §3.5)

```kotlin
// engine/runtime/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/runtime/heat/HeatTierPolicy.kt
public enum class HeatTier { Normal, Slow, Hold }          // Slow: agents at lower priority; Hold: + new heavy work waits

public data class HeatInputs(
    val status: ThermalLevel,          // :core:domain, from the thermal listener; declared in order
                                       // None < Light < Moderate < Severe < Critical < Emergency < Shutdown,
                                       // and an unknown reading maps to None here (so `>=` compares severity)
    val headroom: Float?,              // getThermalHeadroom(30), ≤ every 30 s while collected (11 CR11-5)
    val batteryPercent: Int?, val charging: Boolean?,
    val powerSave: Boolean, val hidden: Boolean, val fullSpeed: Boolean,
)

public object HeatTierPolicy {
    public const val SLOW_HEADROOM: Float = 0.7f
    public const val HOLD_HEADROOM: Float = 0.9f
    public const val SLOW_BATTERY: Int = 20
    public const val HOLD_BATTERY: Int = 10
    public val CALM_FOR: Duration = 2.minutes

    public fun decide(i: HeatInputs): HeatTier {
        if (i.fullSpeed) return HeatTier.Normal
        val unplugged = i.charging == false
        val level = i.batteryPercent
        val headroom = i.headroom ?: 0f
        if (i.status >= ThermalLevel.Moderate || headroom >= HOLD_HEADROOM ||
            (unplugged && level != null && level <= HOLD_BATTERY)) return HeatTier.Hold
        if (i.status >= ThermalLevel.Light || headroom >= SLOW_HEADROOM ||
            (unplugged && level != null && level <= SLOW_BATTERY) || i.powerSave || i.hidden) return HeatTier.Slow
        return HeatTier.Normal
    }
}
```

The numbers are the freeze tip's [R `lib/builtin/heat_tier.dart` on `perf/native-2026-11-01`, merged into
`int/freeze-2026-11-01`]. The owner answered the audit's decisions 1–4 with the recommended answers (slow before
pausing; slower with the screen off with an "Always full speed" switch; 20 %; the phone note), which the freeze tip
implements.

Effects of a tier (owners in brackets):

| Tier | Agents' processes | New heavy work (installs, agent tool starts the process budget gates) | Linux services (11 §8.2–§8.3) | Notification |
|---|---|---|---|---|
| Normal | default priority | runs | run | — |
| Slow | Paseo helper trees in Android's background class, nice 10 (`ProcessHost.setAgentPriority(Slow)`, `:android:linux` `AgentPriority`) | runs | run | — |
| Hold | nice 19 (`Hold`) | waits (`ProcessBudget` asks `holdsNewWork`) | run | heat line in the one status line (no new level); one notification per episode only while hidden (B-ANDR-086 rule, today's `ThermalMonitor.notify`) |
| CRITICAL or worse (thermal status) | hold | waits | **held**: the `hold` file stops every Linux service (the person's on/off choices untouched) and they are given back after 2 cool minutes; persisted as a file so it outlives the app (owner decision 2026-10-31, linux-manager Q3; BUILTIN-LINUX-46) | one heat notice per pause and per resume, only while hidden (11 §8.3) |
| (SEVERE/critical) | AI Team pause/stop rules return with AI Team (B-ANDR-083..092, D6) | | | |

Motion is not a tier effect. It follows the kit's `MotionPolicy` (08 §3; 06 MOT-13): power save or SEVERE heat →
Calm; Saver → Off; otherwise the person's choice. The tier itself never changes motion, so Slow under power save is
Calm because of power save, not because of the tier. 11 §8.3's Motion column ("hold → Calm") is aligned to this by
amendment A17.

Rules:

- Agents and the OpenCode server are never killed or signalled by the heat tier; a running turn keeps streaming
  (audit slice 1 non-goal). Linux services are the one exception: they are held at CRITICAL or worse as above.
- "Always full speed" (Settings › Keep running): the row reads "Run phone agents at full speed" with the supporting
  line "Even when the phone is warm or the battery is low. Uses more battery." It also removes the phone note's
  thread caps (§13.3). Key `flutter.oc.fullSpeed` in the adopted prefs (not per profile [R `phone_budget.dart`]).
- The agent-priority sweep runs when the tier changes and on every proot start (`soon()` path of the reaper), so new
  processes inherit or get the class within 10 s (PB-63).
- The app's own UI thread is never lowered; the app runs as top-app while visible.

### 13.2 Candidate input: battery temperature (experiment E4, P13-D5)

On the owner's phone the thermal status read **0 at 74–90 °C CPU and 42 °C battery** [R audit]. Headroom may react
earlier than status; battery temperature (`ACTION_BATTERY_CHANGED` `EXTRA_TEMPERATURE`, tenths of °C, no permission)
certainly reacts. E4 logs headroom and battery temperature side by side for one evening of the owner's normal agent
use (passive `dumpsys thermalservice` and `dumpsys battery` every 30 s, read-only, with the owner's OK). If headroom
stays below 0.7 while the battery is above 41 °C for more than 5 minutes, `HeatInputs` gains `batteryTempC` with
Slow at ≥ 41.0 °C and Hold at ≥ 43.0 °C [E]; otherwise nothing changes. The change, if any, is an owner decision
(new behaviour beyond parity).

### 13.3 Telling agents the phone's budget (switch scope)

`AgentPhoneNote` (`:engine:agents`, 11 §7) ports `PhoneBudgetFiles` exactly [R `phone_budget.dart`]:

- a marked block (`<!-- oc-phone-budget:begin -->` … `end`) in the agent's own `CLAUDE.md` with the short note ("This
  is a phone … run tests one or two at a time … never leave watch modes running … if the person asks for something
  heavier, do it");
- `env` defaults in the agent's `settings.json` only where the person set none: `MAKEFLAGS=-j2`,
  `CMAKE_BUILD_PARALLEL_LEVEL=2`, `CARGO_BUILD_JOBS=2`, `GOMAXPROCS=2`, `OMP_NUM_THREADS=2`, `UV_THREADPOOL_SIZE=2`,
  `GRADLE_OPTS=-Dorg.gradle.workers.max=2 -Dorg.gradle.parallel=false`;
- removed again (only the values still equal to ours) by "Always full speed".

### 13.4 Orphans and the process budget

- `stopTree` and the orphan reaper port unchanged (builtin-linux 09, 10; 11 §5 "copy"): SIGTERM → 1.5 s → SIGKILL on
  same-uid, ppid 1, untraced processes whose `exe` is the proot loader; never the app, Android or the Rust engine.
- PB-65 measures it (PR-06). The 10-27 hot-phone incident (six orphans at 100 % each for 19 minutes [R memory
  `proot-orphans-heat`]) is the scenario.
- The process budget (32 children, Android 12+) and its kill line are switch scope (02 §3.5); section 13 adds the
  idle-set budget PB-74 (gated at T4 by PR-03) and PR-03/PR-05 measurements of the app uid's process count and RSS
  sum.

### 13.5 Measuring heat and battery

| Where | How | When |
|---|---|---|
| Emulator | PR-05 drives tiers with `cmd thermalservice override-status`, `dumpsys battery set level/ac`, `cmd power set-mode`; asserts tier timing and nice values (PB-62, PB-63) and jank under load (PB-64) | slice 3 end; every change to `:engine:runtime` heat or `:android:linux` priority code |
| Phone, passive | `dumpsys thermalservice`, `dumpsys battery`, `dumpsys batterystats --charged <pkg>`, `ps -A -o PID,PPID,NI,%CPU,RSS,NAME` for the app uid; read-only; every 30 s for one evening | once on Flutter before the switch (baseline), once on the switch candidate; owner OK each time |
| Phone, comparison | peak battery temperature and the app's mAh over the same 10-minute standard agent task, Flutter vs native | switch session; target: native peak ≤ Flutter peak − 2 °C, mAh ≤ Flutter [E] |

Never `dumpsys battery unplug` or `set` on the owner's phone (it changes what the phone reports); emulator only.

---

## 14. Storage I/O

### 14.1 StrictMode in the `stableQa` build (PB-70)

```kotlin
// androidApp/src/qa/kotlin/io/github/eslamasabry/opencode_mobile/QaStrictMode.kt (called first in OcApplication.onCreate)
internal fun installQaStrictMode(sink: (String) -> Unit) {
    val thread = StrictMode.ThreadPolicy.Builder()
        .detectDiskReads().detectDiskWrites().detectNetwork().detectCustomSlowCalls()
    val vm = StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectActivityLeaks()
    if (Build.VERSION.SDK_INT >= 28) {                       // penaltyListener is API 28; minSdk is 26 (lint NewApi)
        val executor = Executors.newSingleThreadExecutor()
        thread.penaltyListener(executor) { v -> sink(violationKey(v)) }
        vm.penaltyListener(executor) { v -> sink(violationKey(v)) }
    } else {
        thread.penaltyLog(); vm.penaltyLog()                 // API 26–27: logged only, not counted (the T3 AVDs are 37)
    }
    StrictMode.setThreadPolicy(thread.build())
    StrictMode.setVmPolicy(vm.build())
}
// violationKey = class + top app frame only (never the message), counted in PerfTrace as "strict.<key>".
```

`t3.sh` reads the counts from the `stableQa` build's exported Performance report after all flows. Allowlist (in
`native/perf/strict-allowlist.txt`): ADR §9.7 step 0 file copy; `files/native/crashloop.json` **read and write**
(≤ 4 KB: `CrashLoopGuard(filesDir).onLaunch()` must write the launch counter synchronously on every start, or a crash
in the first moments is never counted); and any synchronous read in `NativeCrashStore` construction, added only with
its measured size and time once WP-13.3 measures it.

### 14.2 Writes

- Every write goes through a `WriterLane` with `commit()` (ADR §9.5). SharedPreferences rewrites the **whole** XML on
  each commit, so write frequency matters more than value size.
- Hot keys and their pace: draft autosave debounced 1 s after the last keystroke (09/03), read state at most once per
  conversation open and once per settle, offline-queue writes only on enqueue/dispatch/settle. Nothing writes per
  delta while streaming. `WriteRateTest` (`:core:data`, fake lanes counting commits **per file** under a 10-minute
  virtual stream with several per-profile lanes active) asserts ≤ 1 commit of each prefs file per 2 s across all
  lanes (PB-71). Lanes that share the adopted `FlutterSharedPreferences.xml` coalesce: a lane that wants to commit
  within 2 s of another lane's commit of the same file joins the next commit instead.
- At the switch the offline queue (up to 60 MiB) still lives in that one file (Room only at switch+1, 05), so every
  commit of it rewrites the queue too; §4.3 measures that commit at the caps and PB-71's 50 ms ceiling turns a slow
  one into a Room trigger hit.
- PB-71's byte number comes from `/proc/<pid>/io` `write_bytes` across PJ-05 on the emulator.
- First-paint caches belong in Room at switch+1 (ADR §9.8), never in the prefs XML.

### 14.3 Voice packs

10 M4: the verified stamp `.native-verified.json` (length + mtime per file) replaces the start-up re-hash of up to
≈ 540 MB that Flutter ran in isolates each time the model manager was created [R media-voice map §8]. PB-72.

### 14.4 Redaction and diagnostics writes

- `Redactor` (`:core:common`) compiles its patterns once and keeps a memo of the 512 most recent short inputs, cleared
  whenever the known secret set changes (port of the freeze-tip fix `b992ec9da` and `kit_redact_memo_test.dart`).
- Batch redaction (a widget snapshot of every session's title, folder and preview; a problem report; a notification
  set) runs on `Default`, never on the main thread, inside a `redact.batch` section (PB-73).
- Redaction never runs per frame: UI text that needs it is redacted once when the engine publishes it.
- `PerfTrace` timings and field statistics are buffered and flushed at most every 2 s and on pause; error records
  are written at once (PB-71b).

---

## 15. Gates: what runs where, and what happens on a miss

### 15.1 Per tier

| Tier | Machine | Perf steps (scripts from 02 §8) | Budgets gated |
|---|---|---|---|
| T0 | PC or VPS | the agent's own `PB-…` JVM test | — |
| T1 | VPS | touched modules' `PB-…` JVM tests; Konsist K-P1..K-P13 | PB-03c, 17 (JVM), 42, 42b, 49 (JVM), 50–52, 54, 55, 58, 59, 62, 67, 71, 71b, 72, 73 (JVM), 89 |
| T2 | VPS | all of T1; `check_apk_size.py`; `check_so_alignment.py`; `check_startup_dex.py`; `check_compose_reports.py`; `analyzeStableReleaseR8Config` ratchet; `KeepRulesLintTest`; `ManifestPerfTest`; `BannedDependenciesTest` (size list); `TimerTableTest` (the per-module `PB-42` tests); the bench-vs-release classpath check (§3.2 rule 2); the timer-inventory check (every row of `perf/timer-inventory.csv` maps to a `TimerSpec` or an approved drop) | PB-30..35, 42, 46, R8 ratchet, compose allowlist ratchet |
| T3 | PC emulator | StrictMode counts over all flows (`stableQa`); PR-04, PR-06 (slice 3 on), PR-07; FGS stop timing; installed footprint | PB-45, 51, 52, 56, 65, 66, 67, 70, 34 |
| T4 | PC emulator (APKs built on the VPS, §3.10) | PJ-01..PJ-22, PJ-01f, PM-03; PR-01..PR-03b, PR-05, PR-08..PR-14 (PR-13 and PR-14 at the slice ends named in §2); profile regeneration by `am instrument` (§3.10 step 4) | all device budgets |
| Phone session | NX721J, owner present | §15.4 | PB-01, 01u, 02, 04, 05, 06, 11, 11f, 12, 13, 14, 15, 21, 22, 48, 57, 60, 75, 81–88 |
| GitHub release workflow | GitHub Actions | size gate + signer check on the tag build only (CI budget is limited; every commit carries `[skip ci]`) | PB-30, 31, 32 |

### 15.2 When T4 must run

- At every slice end (02 §8), on every switch or preview candidate, and before any APK goes to the owner after a
  merge that touched a perf-sensitive path.
- Outside the quiet window `t4.sh` reads (§3.7 rule 8; default none, per the owner's 2026-11-01 "Ignore the 2am
  rule" in memory `quiet-hours-vps-only`).
- Partial T4 (named journeys) is allowed for a branch; a slice-end T4 runs everything for one unchanged candidate.
  A run counts only for the tree it ran on (02 §8).

### 15.3 `perf/sensitive-paths.txt` (merge rule)

```
# path glob                                   journeys required before merge into native/main
engine/transcript/**                          PJ-05 PJ-06 PJ-04
core/markdown/**                              PJ-05
core/net/**/stream/**                         PJ-05 PR-04
ui/kit-chat/**                                PJ-05 PJ-06 PJ-12 PJ-16
ui/kit/**/motion/** ui/kit/**/KitTappable*    PJ-07 PJ-08 PJ-09 PJ-16
ui/kit/**/style/** ui/kit/**/effects/** ui/design/**   PR-13 (style matrix, frost A/B)
ui/shell/**                                   PJ-01 PJ-07 PJ-08 PJ-21 profile
feature/chats/** feature/chat/**              PJ-01 PJ-04 PJ-05 profile
androidApp/src/main/**                        PJ-01 PJ-03 PR-08 profile
android/background/** android/widgets/**      PR-01 PR-02 PR-07
android/linux/** engine/runtime/**            PR-03 PR-05 PR-06
engine/runtime/**/services/**                 PR-03b
android/linux/**/*Proot* android/linux/**/*Seccomp*   PR-03 (PB-75: seccomp on; --link2symlink and seccomp env handling)
core/common/**/Redact*                        PR-07 (PB-73)
core/common/**/pace/** **/*Timers.kt          PR-01 PR-02 PR-03
config/compose-stability.conf                 PJ-05 PJ-06 PJ-13
gradle/libs.versions.toml (compose, kotlin, agp, r8, coil, ktor lines)   all PJ, profile
androidApp/src/main/keepRules/**              PJ-01 PJ-05 + T3 full
```

The coordinator's merge checklist (02 §9.2) gains one line: "touched a sensitive path → the named journeys are green
against last green on this exact merge candidate".

### 15.4 The phone session (P13-D4)

Two sessions: end of slice 2 (daily chat path) and the switch candidate. About 30 minutes each, the owner present and
not using the phone, after the APK passed T3 and T4 on the emulator.

`native/tool/perf/phone_session.sh` (refuses `screencap`, `screenrecord`, `pm clear`, `am force-stop` and
`uninstall` of any package other than `….bench` and the test APKs; refuses any implicit `am start -d`/`-a VIEW`
without `-n` or a package, since the person's app declares the same `opencode-mobile://` scheme; prints each command
before it runs):

1. Read-only first: `dumpsys batterystats --charged <main pkg>`, `dumpsys thermalservice`, `dumpsys display`
   (refresh), the app's own Performance report from App diagnostics (field numbers, §3.6) if the owner shares it.
2. With the owner's OK: start FX-OC2-PERF bound to the PC's Tailscale address (§3.8); install `….bench`, the
   benchmark test APK and the `:android:media` test APK; `BenchSeedReceiver` gets host id `phone` and the port; run
   PJ-01 (both `Partial(Require)` and `None()` for PB-01u), PJ-02, PJ-03, PJ-04, PJ-05 (frost Off and Balanced in
   session 2), PJ-06 and PJ-07..PJ-11; in session 2 also PJ-17, PJ-18 and PM-01..PM-05 (10 §18 on the phone, replacing
   10 P-M2's install of the `.native` preview for these numbers); `dumpsys meminfo` at the end of PJ-15. `prootMode`
   (PB-75) is read from the person's own app report if they share it: a Flutter baseline before the switch, the
   native value after it; the gate itself runs on the emulator.
3. Uninstall exactly `io.github.eslamasabry.opencode_mobile.bench` and the test packages; stop FX-OC2-PERF; confirm
   the main package and its data are untouched (`dumpsys package <main> | grep versionCode`, `firstInstallTime`
   unchanged).
4. Results to `docs/qa/native/perf-phone-<date>/README.md` (audit-trail rule) with the JSON beside it.

### 15.5 When a budget is missed

1. T1/T2 misses fail the run; the branch does not merge.
2. A T4 emulator regression (§3.7 rule) blocks the merge of the branch that caused it; the perf lead names the
   first bad commit by bisecting merges on the branch, not the repository.
3. A phone-session miss of a switch number follows 02 §4.2: the coordinator fixes the cause or asks the owner to
   relax the number; only the owner can.
4. An `[E]` number is replaced by the first measured value plus a margin of 10 % (frame and time budgets) or 5 %
   (size), written into §2 and `perf/budgets.json` with the date, and never loosened after (02 §4.2 rule 3).

---

## 16. Lessons from the Flutter perf audit, the memory files and the demo

| # | Source | What happened | Native rule | Budget / test | Owner, slice |
|---|---|---|---|---|---|
| 1 | Audit #1 | The thermal guard paused only the AI Team; phone agents (Claude Code and its tests), the real heat source, were never touched | heat tier covers the Paseo helper trees (§13.1) | PB-62, 63 | `:engine:runtime`, `:android:linux`, 3 |
| 2 | Audit #2 | The guard acted only at SEVERE; at 74–90 °C the status still read 0 | headroom, battery level, power save and hidden are inputs; battery temperature is experiment E4 | PB-62; E4 | `:engine:runtime`, 3 |
| 3 | Audit #3 | Agent tree and proot tracer at nice 0; only the AI Team supervisor at 19 | `AgentPriority` to the background class (Slow) or 19 (Hold) | PB-63 | `:android:linux`, 3 |
| 4 | Audit #4 | Nothing told agents how small the phone is | `AgentPhoneNote` (§13.3) | B-row from the catalog top-up | `:engine:agents`, 3 |
| 5 | Audit #5 | Mobile radio was 28 % of the app's drain, source unknown | measure first (owner decision 6); hidden rules §12 | PB-51..53, 57 | `:core:net`, `:engine:connection`, 2 |
| 6 | Audit #6 | Reconnect 0.5 s → 16 s, no jitter, no network wait: a radio wake every 16 s offline or hidden | hidden 1 s, 2 s, then 60–120 s jittered; no dial offline; at once on return | PB-51, 52 | `:core:net`, 1 |
| 7 | Audit #7 | Swap pressure high; claude and opencode2 are native binaries, Node flags barely matter | process budget + memory limiter class (§7.5) | PR-03 report | `:engine:runtime`, 3 |
| 8 | Audit #8 | The chat server fetched the model list at start (the team server did not) | `OPENCODE_DISABLE_MODELS_FETCH=1` once 2.1.1 is proven to honour it; refresh on picker open | PB-56 | `:engine:runtime` scripts, 3 |
| 9 | Audit #9 | `notifyListeners()` fired at once, no per-frame coalescing | 50 ms publish (ADR §9.3); `StateFlow` conflation; 09 counter tests | PB-17, 18 | `:engine:transcript`, 2 |
| 10 | Audit #10 | 13 repeating animations at 120 Hz | ambient-loop allowlist, draw-phase only, one ambient loop per screen and only while waiting, no High vote for loops; the cap stays "not now" | PB-19; K-P8 | kit, 1–2 |
| 11 | Audit #11 | Dart timers kept running hidden (10, 10, 5, 5, 15 s) plus a native 1 s lease pulse and a 60 s orphan scan | `Pacer` + timer table; deadline-based lease expiry; reaper only while a proot runs | PB-40, 42 | all engines, 1–4 |
| 12 | Audit #12 | proot `-L`, link2symlink and 13 binds add work to every trapped syscall | experiment E5: A/B after the switch, adopted only with ≥ 10 % gain and green runtime parity tests | E5 | `:android:linux`, after switch |
| 13 | Audit 41 % jank (p90 61 ms, p99 200 ms) with GPU p90 9 ms | CPU contention, not raster cost | the app's UI outranks agents (tier Slow when hidden or warm), app work off the main thread, PB-64 under load | PB-12, 64 | 3 |
| 14 | android-platform T12 | 3,015 status updates in 1 h 50 min, 755 refused; every FGS start re-posted the row | `ServiceNoticeGate` port; re-post only on changed words; ≤ 1 per 5 s | PB-66 | `:android:background`, 4 |
| 15 | Freeze tip `4ae686c5b` | a streaming phone agent woke the whole app every frame (56 % → 7 %) | §11.5 | PB-41 | `:engine:transcript`, `:engine:attention`, 3 |
| 16 | Memory `proot-orphans-heat` | orphaned tool shells spun at 100 % for 19 min after proot died | reaper + `stopTree` ported unchanged | PB-65 | `:android:linux`, 3 |
| 17 | Memory 10-27 (idle agents loaded) | listing agents spawned one `kilo acp` process each without opening them | B-AGENT-169: a listed idle agent is not woken by background reads | B-AGENT-169 | `:engine:agents`, 3 |
| 18 | Memory `voice-model-memory-gate` | packs gated on `memoryClass` dimmed High accuracy on 16 GB phones | total-RAM gate (§7.3) | 10 | `:engine:media`, 5 |
| 19 | media-voice map §8 | model reloaded every 30 s chunk; 540 MB re-hashed at manager creation; attachments as base64 strings | warm recognizer (10 M2); verified stamp (10 M4); bytes + Uri | PB-26, 72 | `:android:media`, `:engine:media`, `:engine:composer`, 3–5 |
| 20 | Demo | whole-reply re-parse and span fade every frame; `animateItem` on growing rows; reduce-motion read once | §5.6 | 09 tests, K-P7 | kit-chat, 2 |
| 21 | Demo | finished-reply notification needed a short FGS | reply watch, bounded (PB-45) | PB-45 | `:android:background`, 4 |
| 22 | Demo | 2.5 MB, ≈ 0.25 s cold start, library profiles only | the start-up rules keep the product close to the demo; the app profile is margin | PB-01, 10 | 1–2 |
| 23 | packages-build §06 | R8 removed `WorkDatabase_Impl`: crash before any activity in APK 900055 | `workers.keep` + DEX guard scripts + T3 on minified builds | §9 | `:androidApp`, 0 |
| 24 | Freeze tip `b992ec9da` | masking every session's title, folder and preview for a widget snapshot was 22 % of the main thread while an agent streamed (a dozen regex passes plus JWT checks per string); a 512-entry memo and once-built patterns took a 150-session snapshot from 28 ms to 13 ms | `Redactor` memo, patterns built once, batch redaction off the main thread, never per frame; timing writes coalesced (§14.4) | PB-73, PB-71b; `RedactorMemoTest` | `:core:common`, `:android:widgets`, 1–4 |
| 25 | Freeze tip `498fb3a18` + `docs/qa/process-budget-2026-11-01` | always-on helpers (usage collector, a sign-in check of 4 processes on every list refresh) ate the 32-process headroom that killed the owner's agents (exit 137) | collector on demand (stops 2 min after the last read); sign-in answer reused 10 min; an idle-set budget | PB-74 | `:engine:agents`, `:engine:runtime`, 3 |
| 26 | Android 14 platform change | `ComponentCallbacks2` running trim levels and `onLowMemory` are no longer delivered since API 34, so a design built on them is dead code on the owner's Android 16 phone | `MemoryPressure` from `UI_HIDDEN`, `BACKGROUND` and sampled `MemoryInfo` (§7.2) | PR-09 memory fill | `:android:platform`, 2 |
| 27 | Owner's 10-28 phone trace | `catalog.load` 8.1 s and a back-to-back `/session`, `/project`, `/session/active` refresh loop | read-count budgets on resume and per event burst; catalogue dedupe and freshness | PB-49, 58, 59 | `:engine:connection`, 2 |

---

## 17. Work packages

Each is one agent, one worktree, one branch (02 §7.1). Read/write sets list only what the package edits.
"Accept" is the finish line; "Non-goal" is what it must not touch.

| WP | Slice | Agent | Write set | Accept | Non-goal |
|---|---|---|---|---|---|
| WP-13.0 Harness | 0 | Sonnet (perf lead) | `android/benchmark/**` (skeleton, PJ-01 and PJ-01f only), `tool/perf/{t4 helpers,vps_build.sh,restore_device.sh,compare.py,check_apk_size.py,oc.pbtxt,analyze.py,make_perf_data.py}`, `perf/*.json`, the FX-OC2-PERF archive; with the `:androidApp` owner: the `stableBenchGen` build type and the `src/benchmark/` wiring (seed receiver, `<profileable>` manifest, resource-id tags) into both `stableBenchmark` and `stableBenchGen` | `t4.sh --journeys PJ-01,PJ-01f` (detached, §3.7 rule 7) installs VPS-built APKs and produces result JSON on `OC_API37` for the slice-0 skeleton with no Gradle on the PC; `BenchSeedReceiver` and `chat-composer-field` resolve on **both** bench builds; P13-1 settled (task names); `perf-base` snapshot made; size gate runs at T2; the bench-vs-release classpath check runs | journeys beyond PJ-01/PJ-01f |
| WP-13.1 Flutter baseline | 0 | Sonnet (perf lead) | `docs/qa/native/baseline-flutter-<date>/` | 02 §4.1 list measured on Flutter R with the same tools the native app will use: PJ-01f (Macrobenchmark `StartupTimingMetric`, COLD, `CompilationMode.Ignore()`, 10 iterations, median) on the `perf-base` snapshot, PR-12 streaming and fling FrameTimeline, PSS, PR-01 hidden run, APK sizes; `baseline.json` committed | phone measurements without OK |
| WP-13.5a Timer inventory | 0–1 (before WP-13.5) | Sonnet | `tool/perf/timer_inventory.py`, `perf/timer-inventory.csv` | every `Timer.periodic` (47), re-armed one-shot `Timer`, `Future.delayed` loop and native `Handler`/thread/executor loop of the Flutter reference has a row with source, cadence, condition, Flutter visibility behaviour, owner module and a proposed native row; coordinator signs off the drops | writing any Kotlin |
| WP-13.2 P3/P5 support | 0 | Sonnet with the P3 Opus agent | PJ-05/PJ-06 draft on the spike branch; P5 Room-trigger runs | P3's "no frame over budget" measured; P5 numbers recorded with the ADR §9.8 rule | changing the spikes' code |
| WP-13.3 Start-up gates | 1 | Sonnet | `ManifestPerfTest`, trace sections and async sections in `OcApplication`/`MainActivity` (with the `:androidApp` owner), `ReportStartup` (with the shell owner), StrictMode in `stableQa`, the kept-Flutter-marks list (§3.4.3) | PB-03, 03b, 03c, 70 measured; SR1–SR10 checks green incl. `BootstrapStartKindTest`; every kept Flutter mark emitted under its Flutter name | migration logic |
| WP-13.4 Field recorder | 2 | Sonnet | `:android:platform` `StartupHistory`, `FieldFrameStats`, `ProfileStatus`, `TraceRecorder` (off); rows and the trace switch on App diagnostics' Kept page (07 L02, `:feature:about`, with its owner, kit parts only), the field numbers in its Copy report, "Share performance trace" through the Android share sheet | rows show on the emulator; Copy report carries the field numbers; JankStats cost measured (≤ 0.1 ms/frame); all off while hidden | analytics, any network, the This phone page |
| WP-13.5 `Pacer` + timer tables | 1 | Codex (backend) | `:core:common` `pace/` (incl. `@RetryLoop`, `WorkGate`), `:core:testing` `TestPacer`, `TimerTableTest`, Konsist K-P1/K-P2 | **every row of `perf/timer-inventory.csv` resolved** (a `TimerSpec` or an approved drop) and migrated; PB-42 and PB-42b green; the T2 inventory check passes | changing any cadence value except rows marked "native change" in §11.3 or the inventory |
| WP-13.6 Hidden behaviour | 2 | Codex | `:core:net` `BackoffPolicy` (with the backend owner, amendment A2), PR-01, PR-04 scripts, `linux-ready` emulator snapshot | PB-40, 51, 52, 53 green on the emulator | foreground schedule (stays 04 N4-D6) |
| WP-13.7 Runtime heat and leases | 3 | Codex (logic) + Sonnet (scripts) | `:engine:runtime` `HeatTierPolicy` port + tests; lease expiry change in `:android:linux` after the move-only split; PR-03, PR-05, PR-06 | PB-61..65 green; `WorkLeasesTest`, `NativeOrphanReaperTest` unchanged and green | AI Team guard (D6) |
| WP-13.8 Streaming journeys | 2 | Sonnet (perf lead) + Opus (fixes in the owners' modules) | PJ-04..PJ-06, PJ-12, PJ-16, PR-10 SQL; 08's PJ-07..PJ-11 reviewed | PB-11e, 12, 13, 14, 17 (device), 18, 19 recorded; regressions assigned | changing chat code (fixes go to the chat owners) |
| WP-13.9 Profiles | 2 | Sonnet (perf lead) | `BaselineProfileGenerator`, `check_startup_dex.py`, committed profiles; E1 spike before slice 6 | PB-10, PB-35 green; 16's P16-5 two-build check shows the regenerated `baseline.prof`/`.profm` reproducible before the commit | — |
| WP-13.10 Notifications and widgets | 4 | Sonnet | PR-07; `WidgetUpdateGateTest` (with the widgets owner); `RedactorMemoTest` with the `:core:common` owner | PB-66 (each row), 67, 73 green | widget visuals |
| WP-13.11 Media memory and size | 5 | Sonnet (perf lead) + media owner (PM-01..05, PJ-17, PJ-18) | PR-11, `ImageSizingTest` (with kit-content), size sub-budgets for media, §2.10 journeys | PB-25, 26, 26b, 33 (media rows), 80, 85, 88, 89 green on the emulator; PB-81..87 recorded in phone session 2 | voice features |
| WP-13.13 Style matrix and low-end | 2 | Sonnet (perf lead) | PR-13, PR-14 | PB-11s, 11f, 11a, 28 recorded at slice end 2; regressions assigned to the kit owner | changing kit code |
| WP-13.12 Switch performance record | 6 | Sonnet (perf lead) | full T4 on the candidate; phone session 2; `docs/qa/native/perf-switch-<date>/README.md` | every switch number of 02 §4 recorded with method and device; [E] numbers replaced | relaxing any number (owner only) |

---

## 18. Agent brief lines (performance)

Add to every brief that touches code (02 §7.13):

1. Benchmarks and traces run only against the `….bench` package and the minified `stableBenchmark` build (profiles:
   `stableBenchGen`). Never `am force-stop`, `pm clear` or uninstall the main package on the phone or on a state that
   holds the built-in Linux or personal data; every intent you send names its package or component.
2. Repeating work only through `Pacer` with a `TimerSpec` in a `*Timers.kt` file, and only if its job is in
   `perf/timer-inventory.csv`; retry loops only as `@RetryLoop` waiting on `BackoffPolicy.delayMs(…)`; UI clocks only
   through `rememberKitClock`; no other `while`+`delay` loops.
3. Long device runs (PR scripts, `t4.sh`) are launched detached with `setsid nohup`, polled through their done-file in
   foreground chunks under 600 s, and start with `restore_device.sh`; never a long background harness task (memory
   `background-task-killer`).
4. UI state types hold immutable collections only (K-P3); screens read scroll and animation values in layout or draw
   lambdas.
5. Every lazy `items(…)` call has `key` and `contentType`; no `animateItem(placementSpec = …)` in the transcript.
6. Infinite transitions only in files listed in `config/ambient-loops.txt`.
7. Never put message text, paths, ids or host names in trace section names, log lines or result JSON.
8. If your change touches a path in `perf/sensitive-paths.txt`, say so in your report; the coordinator runs the
   named journeys before merging.
9. A new dependency names its compressed size in the brief; `BannedDependenciesTest` lists what never enters release.
10. Do not judge smoothness on a debug build or on the emulator's absolute numbers.

Konsist perf rules (`:quality`, added to ADR §6.3's list by amendment A1):

| Id | Rule |
|---|---|
| K-P1 | no function body with both a `while` loop and `delay(` in any module, except: `:core:common` `pace/`; `ui/kit/**/foundation/time/KitClock.kt`; and functions annotated `@RetryLoop` in which every `delay(` argument is a value returned by `.delayMs(` (a literal, a constant or any other expression fails); no `ticker(` |
| K-P2 | every `Pacer.ticks(` argument is a `TimerSpec` declared in a `*Timers.kt` file of the same module |
| K-P3 | `*Ui` types hold only the allowed types (§6.2) |
| K-P4 | no `GlobalScope`, no `runBlocking` in main source sets |
| K-P5 | no `SharedPreferences.Editor.apply()` (ADR: `commit()` only) |
| K-P6 | lazy `items(`/`itemsIndexed(` calls in `:ui:*` and `:feature:*` pass `key =` and `contentType =` |
| K-P7 | no `animateItem(` with a `placementSpec` argument other than `null` in `:ui:kit-chat` transcript files |
| K-P8 | `rememberInfiniteTransition(` and `rememberKitClock(`'s implementation only in files listed in `config/ambient-loops.txt` |
| K-P9 | `preferredFrameRate(` only in `AndroidKitPlatform` |
| K-P13 | in `android/benchmark/**`, every `Intent(` and every `am start`/`am broadcast` string names a package or component (no implicit intent to the shared `opencode-mobile://` scheme); the shell scripts are covered by `phone_session.sh`'s and `t4.sh`'s own refusal (§15.4) |
| K-P10 | no reference from `src/main` to `src/benchmark` or `src/qa` classes |
| K-P11 | no `DexClassLoader`/`PathClassLoader`/`System.load(` with a computed path; `System.loadLibrary` only in `:android:media`, `:android:terminal` and `:android:linux` |
| K-P12 | no `PeriodicWorkRequest` anywhere (PB-46) |

---

## 19. Decisions

### 19.1 Owner decisions (work proceeds on the recommended default until answered)

| # | Decision | Options | Recommended default |
|---|---|---|---|
| P13-D1 | **32-bit phones (armeabi-v7a).** The Flutter release publishes a v7a APK today; the ADR's splits do not. Without it, a 32-bit phone cannot install the native app (no matching native libraries) and stays on the last Flutter build. | (a) follow the ADR: no v7a; the bridge release (D4) tells v7a users in plain words. (b) add a v7a split (no built-in Linux, voice and QR included; ≈ +9 MB for that APK only). | (a), after the coordinator reads the download count of the 2.1.0+57 v7a asset (`gh release view v2.1.0+57 --json assets`); if it shows real downloads, recommend (b) |
| P13-D2 | **Where the AI Team engine lives when AI Team returns (D6).** | (a) inside the APK as today (+≈ 3.1 MB). (b) a pinned download run inside the phone's Linux. | (a): it keeps today's proofs and works offline |
| P13-D3 | **A performance recorder on your phone.** (1) Release builds are "profileable" so a trace can be taken over USB with your OK (cannot read the app's data; section 16 reviews it). (2) An off-by-default switch in App diagnostics (the page that already holds your Performance report): "Record a performance trace when the app starts slowly or stops responding"; traces stay on the phone until you share one yourself through Android's share sheet. The app's own start and smoothness numbers are added to that page's Copy report either way. Measuring on the emulator and in the two sessions of P13-D4 does not depend on this answer (the test copy is always profileable). | yes / only (1) / no | yes to both |
| P13-D4 | **Two measuring sessions on your phone** (end of slice 2 and the switch candidate), about 30 minutes each, while you are not using it. A separate test copy of the app (and the voice, reading-aloud, scanner and PDF timing tests in the second session) is installed and removed afterwards; your app and its data are not touched, and nothing is force-stopped. | yes / emulator only | yes |
| P13-D5 | **Battery temperature as a heat input** if experiment E4 shows Android's heat signal reacts too late on your phone | adopt / keep today's inputs | decide after E4 |

The audit's decision 7 (cap the glow at 60 fps) stays "not now": the running glow keeps 120 Hz on fast phones as today (§5.5, amendment A16), and E3 records the numbers in case you want to revisit it.

### 19.2 Coordinator amendments (sibling sections; not owner decisions)

| # | Section | Amendment |
|---|---|---|
| A1 | ADR §6.3 rule 12 / 03 Konsist | tests may also be named after `PB-\d{2}[a-z]?` budgets; add K-P1..K-P13 |
| A2 | 04 N4-D6, §6.2 | **extends** 04's `BackoffPolicy` data class (04 stays authoritative; every field, the `OpenCode2` and `GasCity` presets, the 45 s stall timeout and the 6-failure label are kept): new fields `hiddenAfterAttempt = 2`, `hiddenRangeMs = 60_000..120_000` and `delayMs(attempt, random, visibility): Long?` (null for HiddenIdle); hidden schedule 1 s, 2 s, then 60–120 s jittered; immediate retry on return (freeze-tip `1da77af8f`; required by S-18); `EventStreamLoop` waits as a `@RetryLoop` |
| A3 | 06 §4.11 | generated glyphs use the nullable-backing-field getter pattern; generate only referenced glyphs |
| A4 | 03 §21 | add the manual `benchGen` build type (`initWith(benchmark)`, not minified, `.bench`, debug signing) next to 03's `benchmark`; wire `src/benchmark/{kotlin,AndroidManifest.xml}` (`BenchSeedReceiver`, `<profileable>`, resource-id tags) into both; adopt 14's `stableQa` (main package, local key) as the definition of `qa` (consistent with 16 CR16-11); the plugin's generated build types stay unused |
| A5 | 02 G7, rule 17, NG15, S-23 | conflict with memory `freeze-owner-decisions-2026-11-01` item 6 (the owner reversed "no tracking" for the native app: a Basic analytics tier always on, Detailed opt-in) — §19.3 Q1 |
| A6 | 11 §5 | after the move-only `BuiltinLinux` split, one logic change: deadline-based lease expiry instead of the 1 s pulse (WP-13.7) |
| A7 | ADR §6.1 | `:tools:bench` (JVM, kotlinx-benchmark 0.5.0) may depend on the desktop variants of `:core:net`, `:core:markdown`, `:engine:transcript`, `:protocol:oc2` |
| A8 | 03 package table | each feature's `XActions` interface lives in `…presentation.actions` (stability config, §6.1) |
| A9 | 11 CR11-5 | `DeviceFacts.memoryPressure: StateFlow<MemoryPressure>` (`Normal`, `UiHidden`, `Background`, `Low`) from `TRIM_MEMORY_UI_HIDDEN`/`TRIM_MEMORY_BACKGROUND` plus `ActivityManager.getMyMemoryState()`/`getMemoryInfo()` sampled at natural points (§7.2); running trim levels and `onLowMemory` only below API 34 |
| A10 | 09 §14.4 vs 10 M9 | Coil disk cache: 09 allows 64 MiB for `file:`/`data:` decodes, 10 says none |
| A11 | 02 S-5 | the phone method becomes Macrobenchmark `StartupTimingMetric` (COLD, `Partial(Require)`, median of 10) on `….bench`, because `am start -W -S` on the stable package force-stops the person's app (§3.2 rule 1); the emulator ratio uses the same tool for both apps (PJ-01 vs PJ-01f, medians) |
| A12 | 03 §8.3 (T4 row) | T4 = `native/tool/perf/vps_build.sh` on the VPS + `t4.sh` on the PC (install + `am instrument`), no Gradle on the PC (§3.10) |
| A13 | 03 §18.5 | the `Trace` facade gains `beginAsync(name, cookie)`/`endAsync(name, cookie)` mapped to androidx.tracing async sections |
| A14 | 08 §9.3 | `RecompositionProbe` interface + no-op local in `:ui:kit` commonMain, probes inside the six parts; `RecompositionCounter` is the counting implementation in `:ui:testing` |
| A15 | 16 RB-4, open items | the committed profile path is `androidApp/src/main/generated/baselineProfiles/{baseline-prof,startup-prof}.txt`; Q2 (profileable release) joins 16's open items for review |
| A16 | 08 M8-16, §8.3 | the composer's running glow votes `High` while a reply runs under motion Full (owner's "not now" on the 60 fps cap) |
| A17 | 11 §8.3 | the Motion column follows `MotionPolicy` (power save or SEVERE heat → Calm; Saver → Off); the heat tier does not set motion |
| A18 | 10 §18 | its budgets move into 13 §2.10 unchanged (10 keeps the code and the fixtures); download progress is ≤ 4 UI updates/s for every download |
| A19 | 02 S-11 | the method cell names the async section `chat.open_to_first_transcript` (the kept Flutter name, 03 §18.5, 07 §10.7) instead of `chat.openToFirstTranscript` |
| A20 | 14 §4.4, §13.2 item 2 | accepted as proposed there: `stableQa` (main package, local key) plus optional `previewQa`; 13 §3.2 updated |

### 19.3 Open questions for the coordinator

- **Q1 (analytics).** If the owner's Basic analytics tier ships, its sender must follow §12.2's last bullet; the perf
  field summary of §3.6 (cold-start median, jank %, ANR count) is a natural Basic candidate. Until 02 is amended, no
  sender is linked and §3.6 stays local.
- **Q2.** Whether `profileable` in `stableRelease` is acceptable to section 16 (P13-D3 part 1). 16 does not discuss
  it yet; amendment A15 adds it to 16's open items. Benchmarks do not depend on the answer (§3.2).

---

## 20. Unknowns, proofs and experiments

| # | Unknown | Settled by | Fallback |
|---|---|---|---|
| P13-1 | Exact AGP 9.3.3 task names for `stableBenchmark`, `stableBenchGen`, `stableQa` and the test APK with the baseline-profile plugin applied; that `sourceSets["benchGen"]` can share `src/benchmark/` | WP-13.0 on the slice-0 skeleton | a shared `src/benchHooks/` directory added to both build types' source sets by the convention plugin |
| P13-2 | `TraceSectionMetric.Mode` values on 1.5.0 beyond `First`/`Sum`; whether it matches async slices (`app.toFirstConnected`, `chat.open_to_first_transcript`) | WP-13.0 compile + one PJ-01 run | `Sum` + Perfetto SQL counts; async spans read by `analyze.py` from the trace |
| P13-3 | Which input-latency table trace_processor v59.1 exposes for PB-20 | WP-13.8 | measure tap → first changed frame with FrameTimeline only |
| P13-4 | Host GPU mode works for `OC_API37` on the PC | WP-13.0 | software rendering for all runs, labelled |
| P13-5 | OpenCode 2.1.1 honours `OPENCODE_DISABLE_MODELS_FETCH` | slice 3 runtime agent: start with the variable, watch FX egress | leave the fetch; PB-56 dropped with an owner note |
| P13-6 | First-use cost of CMP font and string resources on Android | WP-13.3 (`font.load`, `words.load`) | Android `res/font` behind `KitFonts`; preload strings on IO |
| P13-7 | Compose compiler report line format (Kotlin 2.4.21) | WP-13.3, pinned sample | gate on `*-composables.csv` instead |
| P13-8 | JVM signature of `Log.d(() -> String)` for `-assumenosideeffects` | WP-13.3, `analyzeStableReleaseR8Config` + DEX check that no `Log.d` call remains | drop the rule (Kermit's minimum severity still skips the work) |
| P13-9 | Machine-readable R8 analyzer scores | first T2 | HTML parse |
| P13-10 | `apkanalyzer dex packages --files` on the installed cmdline-tools | WP-13.9 | `dexdump` per DEX |
| P13-11 | `dumpsys alarm` shows WorkManager's `ForceStopRunnable` alarm in a form `t4.sh` can match (PendingIntent target, trigger > 1 year away) | PR-01 in WP-13.6 | match by the WorkManager receiver class only |
| P13-12 | `measureRepeated(…, experimentalConfig = ExperimentalConfig(perfettoConfig = PerfettoConfig.Text(…)))` on benchmark-macro 1.5.0 | WP-13.8 compile | Macrobenchmark's default trace config + SQL on the sections it records |
| P13-13 | The async-section API of androidx.tracing 2.0.5 (A13) | WP-13.3 compile | `android.os.Trace.beginAsyncSection` (API 29+) behind the facade; marks parsed by `analyze.py` below 29 |
| P13-14 | OpenCode 2.1.1 takes the server password from `OPENCODE_SERVER_PASSWORD` (the Flutter recipe sets it [R `NativeServerRecipe.kt:54`]) | WP-13.0 starting FX-OC2-PERF | a loopback proxy in front of the instance that injects auth (`tool/perf/auth_proxy.py`) |
| E1 | `base.dm` through a `PackageInstaller` session compiles at install on Android 12–17 | WP-13.9 spike on `OC_API37` **before slice 6** (`dumpsys package dexopt` shows `install-dm`?); if yes, the bridge release's installer (D4) and the native updater both write it | none needed; background dexopt as today; PB-01u still holds |
| E2 | Android 17 memory limiter puts proot children in the app's cgroup | slice 0 (android-surfaces §5 action 3) | no memory budget in `ProcessBudget` |
| E3 | Glow cost and display rate on the NX721J, with the glow's High vote on and off | phone session 1 | — (owner's "not now" stands; the glow keeps its High vote) |
| E4 | Battery temperature vs headroom on the NX721J | one passive evening, owner OK | P13-D5 |
| E5 | proot flag A/B (`-L`, link2symlink, binds) | after the switch | none |
| E6 | Reduced-operator ONNX Runtime for Whisper + Kokoro | after the switch, `:android:media` owner | keep the stock AAR |

---

## 21. Sources

- Plan: `01-architecture-decision.md` (§1, §5.2 `:android:benchmark`, §6.3, §7.6, §8, §9.3, §9.5, §9.7–§9.9, §10.3,
  §11 P3–P5, P8), `02-principles-scope.md` (§1 G1/G6, §3.5, §4, §4.1, §4.2, §6.1, §8), `03-architecture-modules.md`
  (§8.3, §10.4 `ProcessStart`, §13.6, §18, §19.2, §21), `04-protocols-networking.md` (N4-D1..D9, §6.2–§6.6, §12.4),
  `05-data-migration.md` (§1, §6.2, §7.1, §10.5 J1–J2), `07-screens-navigation.md` (L02, §10.7),
  `06-ui-kit-design-system.md` (K6, K7, §4.11, MOT-13, `KitPlatform`), `08-motion-animation.md` (M8-6, M8-16, §8,
  §9.3, §9.5, §11 M6), `09-chat-streaming-genui.md` (§4.1, §14.2–§14.5), `10-media-voice.md` (M2, M4, M9, M12,
  §2.4, §2.5, §18, P-M2), `11-android-platform-native.md` (CR11-5, S11-4, §5, §7, §8.1–§8.3, §9, `showStatus`),
  `14-testing-parity.md` (§4.4 `QaHooks`/`stableQa`/FX-OC2, §4.6 AVDs, §8, §13.2), `16-security-privacy-release.md`
  (CR16-11, §6.2, §8.2, RB-4, P16-5).
- Research: `testing-perf.md` (§0, §1, §2.2, §6.4, §9.1–§9.6, §10, §11, §13), `rendering-chat.md` (§0, §1),
  `motion-native.md` (§9), `media.md` (§0, §2, §7), `android-surfaces.md` (§2.2, §3, §5), `kmp-cmp-status.md`,
  `network-data.md`.
- Maps: `packages-build.md` (§05–§07, §19), `builtin-linux.md` (02, 09–12, 21, 25, 31, 32, 34), `android-platform.md`
  (15, T12, 63), `media-voice.md` (§8), `chat-genui.md` (PERF-2), `ui-kit.md` (PERF-1, PERF-4, MOT-13).
- Catalog rows cited: B-CHAT-006, B-CHAT-096, B-STATE-030, B-STATE-054, B-AGENT-150, B-AGENT-169, B-ANDR-025,
  B-ANDR-083..093, B-PROTO-033, B-PROTO-132, B-PROTO-134, B-SEC-117, B-BUILD-025..027, B-LINUX-049..052.
- Flutter reference (read-only): `/home/eslam/Storage/Code/wt/oc_app-batch1` — `test/perf_*_test.dart`
  (`perf_catalog_budget_test.dart:212-338`, `perf_builtin_pollers_test.dart`, `perf_profile_load_test.dart`),
  `lib/state/connection/{sessions.dart:522-547,status.dart:195-366,lifecycle.dart:85-145,turn_stall.dart:174-192,
  phone_agents_startup.dart}`, `lib/domain/turn_stall.dart:13`, `lib/state/{offline_queue.dart:244-257,
  profile_monitor.dart,provider_quota_monitor.dart,usage_subscriptions_controller.dart,paseo_usage_monitor.dart}`,
  `lib/paseo/transport.dart:435`, `lib/builtin/{reply_watch.dart,phone_agent_work_watch.dart}`, the 47
  `Timer.periodic` sites of `lib/`, `tool/release/{check_apk_size,cold_start,
  split_apk_by_abi}.py`, `android/app/src/main/res/xml/widget_*_info.xml` (`updatePeriodMillis="0"`),
  `.github/workflows/android-release.yml:284`.
- Freeze tip (`int/freeze-2026-11-01`, read with `git show`): `lib/builtin/heat_tier.dart`,
  `lib/builtin/agents/phone_budget.dart`, `AgentPriority.kt`, `NativeWorkLeaseHost.kt:265`, commits `1da77af8f`,
  `4ae686c5b`, `afd0c6bf3`, `f9598e8ad`, `522df47a2`, `5e5c8ab42`, `b992ec9da` (redaction memo), `498fb3a18` (idle
  processes), `f2a3c2c97` (`docs/qa/process-budget-2026-11-01`), `lib/update/app_update_controller.dart`,
  `lib/builtin/linux_services/oc_svd_script.dart`.
- Demo merged release manifest: `kmp-demo/androidApp/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml`.
- Perf audit: `/home/eslam/Storage2/wt/perf-audit-2026-11-01/REPORT.md`.
- Demo: `/home/eslam/Storage/Code/kmp-demo` (`androidApp/build.gradle.kts`, release APK 2,459,429 bytes, merged ART
  profile 10,101 lines).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `proot-orphans-heat`,
  `phantom-process-limit`, `voice-model-memory-gate`, `android-native-feel`, `micro-animations-wanted`,
  `no-owner-phone-screenshots`, `phone-logcat-silenced`, `owner-phone-debug-allowed`, `test-on-emulator-not-phone`,
  `quiet-hours-vps-only`, `pc-capacity-ceiling`, `shared-emulator-contention`, `hog-watch-monitor`, `build-vps`,
  `app-updates-two-layers`, `audit-trail-rule`, `background-task-killer`, `emulator-lock-holder`, `linux-manager-plan`,
  `design-schools-direction`, `stabilize-before-shipping`, `composer-green-glow-liked`, `vps-suite-shared-dir-trap`.
- Checked today [V], 2026-11-01:
  - Maven Central / Google Maven metadata: kotlinx-benchmark-runtime 0.5.0 (lastUpdated 2026-09-01);
    androidx.metrics:metrics-performance 1.0.0; profileinstaller 1.4.1; benchmark-macro-junit4 1.6.0-alpha01 latest
    (1.5.0 pinned by the ADR); compose runtime-tracing 1.13.0-beta02.
  - Startup Profiles / DEX layout (updated 2026-10-23): https://developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations
  - Baseline vs Startup Profiles: https://developer.android.com/topic/performance/baselineprofiles/difference-baseline-startup
  - ProfilingManager trigger-based capture: https://developer.android.com/topic/performance/tracing/profiling-manager/trigger-based-capture
  - ProfilingTrigger (API 36 constants): https://developer.android.com/reference/android/os/ProfilingTrigger
  - ApplicationStartInfo: https://developer.android.com/reference/android/app/ApplicationStartInfo
  - MemoryUsageMetric (experimental; modes Last/Max): https://developer.android.com/reference/kotlin/androidx/benchmark/macro/MemoryUsageMetric
  - Thermal API (headroom rate limit, NaN): https://developer.android.com/games/optimize/adpf/thermal
  - ComponentCallbacks2 (running trim levels and `onLowMemory` not delivered since API 34): https://developer.android.com/reference/android/content/ComponentCallbacks2
  - ArtMetric (counts and summed times; class loading needs API 35 or ART mainline ≥ 341511000): https://developer.android.com/reference/kotlin/androidx/benchmark/macro/ArtMetric

---

## Review notes (2026-11-01 revision)

Every issue was checked against the sibling sections, the Flutter reference, the freeze tip and the memory files
before editing. All were real; four were accepted only in part (marked "partly").

Changed:

- **Builds (§3.2, §3.10, §10):** `qa` became 14's `stableQa` (main package, local key, R8, never on the phone),
  with optional `previewQa`. Profiles are generated on a manual non-minified `stableBenchGen` that shares
  `src/benchmark/`, never on the plugin's `nonMinifiedRelease`. `<profileable>` lives in the bench manifest, so
  benchmarks no longer depend on P13-D3. The VPS builds every APK and the PC only installs and runs
  `am instrument`, with no Gradle during T4 (A12). Snapshots: `perf-base` (Flutter R + bench) and `linux-ready`
  (PR-03).
- **Fixtures (§3.8):** FX-OC2-PERF has a bench-only password passed through the environment, a prebuilt data archive,
  and binds to loopback, or to Tailscale only during a phone session. `BenchSeedReceiver` takes a host id and a port.
- **Comparison (§2.1, §3.7):** each metric has a direction and threshold kind. `frameOverrunMs` uses +0.5 ms
  absolute; single-shot runs compare against the budget only. Long runs are launched detached, with a restore
  preamble and recorded lock PIDs. The quiet window is configurable and off by default. A low-end subset runs on
  `OCN_26`.
- **Start-up (§2.2, §4.1):** PB-01e uses one tool for both apps (PJ-01f, medians), and the phone method is amended
  (A11). New: PB-01u (uncompiled first launch), with E1 before slice 6 covering the bridge installer too, and PB-03c
  (secure reads). SR3 allows OkHttp's initializer and CMP's context provider. SR5 keys stream start on 03's
  `ProcessStart`. SR9 and PB-09 use a Perfetto class-load check. PB-07 uses the async `app.toFirstConnected`.
- **Smoothness (§2.3, §5):** PB-20's one-frame ripple is limited to controls outside scrolling lists; list rows get
  tap timeout + 1 frame. New: the style × mode matrix, the frost A/B and the large-text Arabic run (PB-11s/f/a,
  PR-13), and journeys PJ-17 to PJ-22. The running glow votes High so no de facto 60 fps cap ships (A16).
  `rememberKitClock` is now the one UI clock (R-F6).
- **Traces (§3.4.3):** a table of kept Flutter marks under their Flutter names, plus async sections (A13, A19).
- **Timers (§11):** four-value `Visibility` (live vs reply watch) plus `WorkGate` for runtime timers. Corrected
  rows: `busyPoll`, `waitingReread`, `localAlive`/`agentDaemonProbe` (these two replace `attention.monitor`),
  `heatInputs`, `setupFollow`. A generated timer inventory (WP-13.5a) is now the complete list and WP-13.5's accept.
  K-P1 allows `@RetryLoop` waits on `BackoffPolicy.delayMs` and `KitClock.kt`. `BackoffPolicy` extends 04's type (A2).
- **Background (§2.6, §2.7, §11.4, §13):**
  - PB-55 follows the owner's updater: start and resume, 6 h cap, 30 min after a failure, switch on, never hidden,
    Check now exempt.
  - New: PB-42b (monitors only under Stay connected), PB-49/58/59 (budgets from the Flutter widget perf tests), the
    WorkManager alarm in PB-43, and PB-61b (an idle Linux service).
  - PB-44 now limits each unbroken lease stretch to 6 h and allows the services keep-awake lock.
  - PB-66 is per notification row.
  - The heat tier holds Linux services at CRITICAL. Motion follows `MotionPolicy` (A17).
- **Memory and I/O (§2.4, §2.8, §7, §14):** `MemoryPressure` no longer relies on trim levels Android stopped sending
  at API 34 (A9). Kokoro gets a holder and PB-26b. PB-71 counts commits per file, and P5 is measured at the documented
  caps. StrictMode allows the crash-loop write and guards `penaltyListener` for API 28. New: the redaction budget
  (PB-73, §14.4), idle child processes (PB-74) and seccomp (PB-75).
- **Media (§2.10):** 10 §18 imported as PB-80 to PB-89 (A18), run on `.bench` in phone session 2. Download progress
  is one rate, ≤ 4/s.
- **Field recorder (§3.6, WP-13.4, P13-D3):** moved to App diagnostics' Kept page (07 L02, `:feature:about`). A trace
  is handed over only through the Android share sheet.
- **Other:** recomposition probe interface moved into `:ui:kit` (A14); `runtime-tracing` pinned to the release's
  Compose runtime, with a classpath equality check, the tracing-perfetto dependencies, and PR-10 using one Perfetto
  session; explicit intents (K-P13, `phone_session.sh`); one profile path agreed with 16 plus the P16-5 check in
  WP-13.9 (A15).

Rejected or partly rejected:

- **Timer-row issue, part:** citing B-PROTO-034 for `requests.waitingReread` was rejected. That row covers the event
  stream's reconnect and resume (`api2_sse_test.dart`), not the turn-stall re-read. The row now cites the Flutter
  source, and the inventory names its catalog row.
- **Trace-name issue, part:** 02 S-11 does not require the Flutter name; its method cell names
  `chat.openToFirstTranscript`. 03 §18.5 and 07 §10.7 do require Flutter names, so the change stands, with A19 to
  align S-11.
- **Prefs-commit issue (PB-71), part:** stash attachments are files (05 §1 `BlobStore`), not inline in the prefs
  file. Only the stash metadata and the 60 MiB offline queue are seeded at the caps.
- **Process-count issue (PB-74), part:** the QA record says the emulator idle set went from 10 to 9; the "about 14"
  is the owner's phone. PB-74 uses the measured emulator number (≤ 9).
- **Media benchmarks, part:** the in-process media benchmarks (PM-01 to PM-05) cannot live in the self-instrumenting
  `:android:benchmark` module. They go in `:android:media` `androidTest` instead, registered in this section's table.
- **Low-end AVD issue, part:** no new API 30 AVD (disk space, 14 T14-3). The low-end subset reuses 14's `OCN_26`
  with 2 cores.

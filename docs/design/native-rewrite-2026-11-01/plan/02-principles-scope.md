# 02. Principles and scope: goals, limits, numbers, definitions, and how agents work

Written 2026-11-01. This section builds on `01-architecture-decision.md` (the ADR). The ADR is binding: where this file
and the ADR differ, the ADR wins until it is amended. Tags: **[V]** checked today against a primary source; **[R]**
read in the repo, a map, a research file or a memory file; **[E]** my estimate, to be replaced by the measured value;
**[U]** unverified, proven in slice 0.

## For the owner, in plain words

1. We rebuild the whole app in Kotlin so every screen feels like the demo did. Android comes first, and the same code later runs on Linux desktop and iOS.
2. Almost everything you use today carries over. About 1,340 of the 1,544 behaviours the Flutter tests prove get a native test first, and 37 phone flows must pass on both apps. Under the D6 default, three things are missing at the switch until they are ported: **AI Team, Memory (FactsBook) and the assistant on this phone (Gemma)**. Their data stays on the phone, and the app tells you once what moved (§3.7). Say so if you want any of them at the switch.
3. The new app installs over the old one, keeping your servers, passwords, chats and the phone's Linux. Until then you try it as a separate "preview" app next to the Flutter one.
4. Until the switch, the Flutter app gets bug and security fixes only, no new features.
5. Success is measured: it opens in about 0.3 s, drops no frames while a reply streams, the APK is under 22 MB, and it uses almost no battery while hidden.
6. The usage counts you asked for are built in from day one: a small anonymous Basic set that is always on, and a Detailed set only for people who opt in. Nothing is sent until you pick where the counts go (S02-5).

This section adds eight decisions; work goes ahead on the recommended default until you answer (§11):

- S02-1: the code lives in the same repo, under `native/`.
- S02-2: the numbers in §4.
- S02-3: what happens to the features merged into the freeze after the catalog was taken.
- S02-4: the owner phone test lasts two days.
- S02-5: nothing is sent until you confirm the Basic usage counts and where they go.
- S02-6: the switch waits for a stable Compose Multiplatform 1.13, unless you OK shipping the beta.
- S02-7: saved external agents (A2A) are not carried over and are erased with the old app's data later.
- S02-8: the app stops recording "While you were away" until a page for it is built.

---

## 0. How to use this section

- **Agents:** before your first commit, read §6 (definitions), §7 (working rules), §8 (check ladder) and §9 (commits and
  evidence). The draft `native/AGENTS.md` in §7.12 goes into every brief.
- **Coordinator:** read §3 (scope), §5 (freeze) and §10 (start-up checklist).
- **Section writers:** §1–§4 say what every other section must serve. The detail lives in:

  | Section | Topic |
  |---|---|
  | 03 | modules |
  | 04 | protocols |
  | 05 | data and migration |
  | 06 | kit |
  | 07 | screens |
  | 08 | motion |
  | 09 | chat and generative UI |
  | 10 | media and voice |
  | 11 | Android platform |
  | 12 | package registry |
  | 13 | performance |
  | 14 | testing and parity |
  | 15 | delivery |
  | 16 | security and release |

### 0.1 Which source wins when two disagree

1. The owner's latest words, once recorded as a decision (a memory file, or an answer to an ADR §12 or §11 item).
2. The ADR, including its §12 defaults until the owner answers.
3. This section.
4. Sections 03–16, each within its own topic.
5. The catalog row text (`catalog/behaviors.jsonl`), as amended in `native/catalog/scope.jsonl` (§3.6).
6. The maps (`map/*.md`), then the research files (`research/*.md`).
7. What the Flutter reference build does (§6.2), read from its tests and source.

An agent who finds a conflict among items 4–7 reports it to the coordinator and does not resolve it alone.

---

## 1. Goals

| # | Goal | What it means in practice | Proved by (§4) |
|---|---|---|---|
| G1 | **Smooth** | Streaming, scrolling, opening a chat, back gestures and sheets feel like the demo did on the owner's phone. | S-9 to S-13, S-27 |
| G2 | **Nothing lost** | Every in-scope behaviour of the Flutter reference build works the same in the native app. | S-1, S-2 |
| G3 | **Update in place on day 1** | Same package and the same signer per lineage. versionCode ≥ 10000. Every stored value is carried over and the built-in Linux is untouched. No uninstall. | S-3, S-4, S-8 |
| G4 | **Android-native feel to the maximum** | Platform ripple, predictive back, stretch overscroll, haptics, edge-to-edge and IME animation, all taken from AndroidX defaults and never re-implemented (ADR §9.4). | §1.1 rows 1–3, T3 checks |
| G5 | **Ready for Linux desktop and iOS** | Logic and UI live in `commonMain`. Desktop compiles from the first commit, and iOS klibs compile nightly. | S-24 |
| G6 | **Light** | A smaller APK, a fast start, little background CPU, few wakeups, less memory and a quiet radio. | S-5 to S-8, S-14 to S-20 |
| G7 | **As private and safe as today, with usage counts the owner asked for** | One analytics core of our own (owner, 2026-11-01, freeze decision 6 as reversed for native): one event API in an engine module, sending over `:core:net`, no vendor SDK. BASIC is always on, anonymous, minimal and disclosed in PRIVACY.md. DETAILED is opt-in. Events never carry content, paths, addresses or credentials. Nothing is sent until S02-5 names a destination. One redactor and one door for external links. Per-profile keys with a deletion sweep. Provider keys never echoed. Cleartext only to loopback or Tailscale, or with consent. | S-22, S-29, section 16 (§5 owns the analytics core) |
| G8 | **F-Droid-ready** | The release classpath has no Google Play Services, Firebase, ML Kit, Shorebird, or vendor analytics or crash-upload SDK. QR is Google-free (zxing-cpp; freeze decision 1). Our own analytics core is allowed, because it links no SDK; an always-on BASIC tier that sends anything earns F-Droid's "Tracking" anti-feature label (S02-5). Submission comes after the rewrite. | S-23 |
| G9 | **Agents can build it without asking** | Frozen contracts, one owner per shared module, failing tests written first, scripted checks and evidence records. | §7 to §9 |
| G10 | **The owner sees progress** | A preview-flavor APK at each slice end, run on the emulator first, with one-screen images and short videos. | §7.10 |

### 1.1 Design rules that every UI decision obeys

Each owner rule maps to one native mechanism from the ADR and one check. A UI change that breaks a row here is
rejected in review, whatever its tests say.

| # | Owner rule (memory file) | Native mechanism | Check |
|---|---|---|---|
| 1 | Android-native feel to the maximum (`android-native-feel`, 2026-10-26) | `KitTappable` = platform `RippleDrawable` ripple + `KitHaptic` (send, commit, select, done, longPress); stretch overscroll on API 31+ (glow on 26–30); Nav3 predictive back; edge-to-edge; IME animation | Konsist bans raw `clickable`, `LocalHapticFeedback` and `performHapticFeedback` in features (ADR §6.3); P2 gesture checks |
| 2 | Smooth streaming (the demo, 2026-11-01) | `TranscriptEngine` publishes every 50 ms; block `LazyColumn`; draw-phase word fade; `preferredFrameRate(High)` only while the block reveals (ADR §9.3) | S-9, S-10 |
| 3 | Subtle 150–250 ms motion with reduced-motion support (`micro-animations-wanted`) | Kit duration tokens of 150–250 ms; `MotionScheme.standard()` (no overshoot); `MotionPolicy` Full/Calm/Off plus the system animator scale; no raw `tween` or `spring` outside the kit. Owner-approved longer motions keep their timings as named exceptions in section 08: the chat grows out of its row in 450 ms and closes in 300 ms (`KitMotion.rowReturn`, screens-nav §1.4). | Konsist; KIT rows |
| 4 | Kit only (AGENTS.md, kit-v2 §9) | Feature import allowlist (ADR §6.3 rule 1). A missing part is added to the kit, never built on the screen. | Konsist; S-25 |
| 5 | No glass (`no-glass-rule`) | Solid is the default. The styles are Solid, Soft, Brutal, Paper and Signal (B-KIT-037). No blur, translucency or shaders, except the D10 frost on the composer and chat header (`RenderEffect` on API 31+, solid below that and under Saver or reduced effects). One optional glass trial may follow the switch (NG24). | Screenshot set; B-KIT-018 as amended |
| 6 | Simplify and make it natural; judge every idea by what it removes (`simplify-and-natural`) | Every UI brief states what the change removes. A new control, chip or label needs a written reason in the brief. | Review checklist (§7.13) |
| 7 | Plain words, never raw errors (`no-raw-errors-rule`) | A closed `Failure` hierarchy plus an exhaustive mapping to words in `:ui:words`. `Redacted` details render only in the kit's Details fold (ADR §9.6). | Konsist; B-NAV-007 to B-NAV-009 |
| 8 | Obvious options as visible rows (`obvious-options-first`) | A screen's main actions are first-page rows in the order the person needs them. Overflow menus hold only rare toggles. | Review; flows assert visible rows |
| 9 | No new bar or row levels (`no-new-ui-levels`) | One status-line slot in `:ui:shell` (`KitStatus` priority). New status folds into an existing row or chip. | Review; screenshots |
| 10 | Actions name their target (`actions-name-their-target`) | Copy keys carry the target ("Stop the server on this phone"). An action lives on the page of the thing it acts on. | Copy review in the slice brief |
| 11 | Steer, never queue (`steer-not-queue`) | `:engine:delivery` steers whenever the backend can. A queue exists only offline or where steering is impossible. | STATE and CHAT delivery rows |
| 12 | Hard max widths for components (`component-max-width-rule`) | The capacity and max-width table in `:ui:design`. Capacity tests use extreme content at 280/320/360/412/1600 dp × text scale 1.0/2.0/2.5 × en/ar. | S-25 |
| 13 | Frame text capped at 1.3×, reading text scales fully (`text-scale-cap`) | `KitFrameText` with the capped non-linear scaler (P7). TalkBack labels unchanged. | KIT rows; P7 |
| 14 | Arabic RTL (AGENTS.md) | Every key exists in en and ar at merge. Only direction glyphs are mirrored. Technical text (paths, ids, commands, URLs) is always LTR. | L10N rows; RTL screenshots; S-26 |
| 15 | Teach what is hidden (`teach-what-is-hidden`) | The teaching registry in `:engine:settings`. Every gesture or hidden feature registers a tip, and a visible path always exists. | B-NAV-019, B-NAV-038, B-NAV-039 |
| 16 | One status line, honest words (screens-nav §1.6, §1.11) | "Connected" only after a probe proves it; receipts read Sending / Not confirmed yet / Not accepted | NAV and CHAT rows |
| 17 | Usage counts with privacy rules (`freeze-owner-decisions-2026-11-01` item 6: "Let's build tracking SDK day one that can later talk to other analytics apps"; "Opt in for detailed but basics we need regardless") | One analytics core and one event API of our own (section 16 §5), senders written in our own HTTP code over `:core:net`. BASIC always on, anonymous and disclosed; DETAILED opt-in on the Privacy page. Events carry only enums, booleans and buckets: never content, paths, addresses, profile ids or credentials. No vendor analytics or crash-upload SDK is linked. Diagnostics stay local and category-only. | S-22, S-23, S-29 |
| 18 | The root never intercepts Back (D5) | "Press back again to exit" is dropped | P2 |
| 19 | No red anywhere (2026-10-16, absolute; map ui-kit R2). The only exceptions are the Signal style's lamps, Stop key and alert ink, and the markup pen's red ink. | `:ui:design` has no danger or error colour role. A destructive act is made safe by a confirmation (and a typed name), never by colour; the confirmed button is the strong neutral fill. Amber means "needs you" and nothing else. | `NoRedScanTest` (B-KIT-056; section 06 K21) with exactly the two exemptions |
| 20 | Flat lists with dividers, not boxes, pills and tiles (`no-glass-rule`, 2026-10-26; map ui-kit R10) | `KitStyleTokens.flatLists` is on for Solid: rows run the page width, parted by inset hairlines, with no panel and no icon tile. Pills only for exceptions. | Roborazzi screenshot set; independent visual auditor (§7.10) |
| 21 | The screen standard (`design-standard-rule`; map ui-kit R13) | At most one primary action per screen, at most 7 items before scrolling, at most 3 text sizes, at most one supporting line per row. `KitStateView` for loading, empty, error, stopped and blocked. After 8 s without an answer the kit says so and offers a way out (`KitMotion.escalateAfter`). | Kit debug asserts (one primary); the screen-standard check over the `:desktopApp` gallery; KIT rows |
| 22 | Keep the composer's green running glow (`composer-green-glow-liked`, 2026-10-14) and its "Glowing border while replying" switch (`no-glass-rule`) | A named kit effect in `:ui:kit-chat` (the classic glowing border and its green hue). It is a border glow, not glass, so row 5 does not remove it. While a reply runs only one thing moves: the glow or Stop's ring. | Roborazzi golden of the running composer; the switch is a settings row |
| 23 | Settings scope model (`settings-scope-model`, 2026-10-12) | Personal choices apply everywhere. A setting owned by a server, project, host or provider account stays on that owner's page. With several connections, server-owned rows say "By server". No global Advanced switch; detail goes under per-task Details. | Every settings row declares its scope in `:engine:settings`; review |
| 24 | Animations never run below 60 fps (`micro-animations-wanted`) | Animations run at the display rate, 60 fps minimum and up to 120. Cost is cut by pausing or caching, never by lowering the frame rate. | S-9 to S-13; review of any frame-rate cap |

---

## 2. Non-goals

Each non-goal names its decision. Reopening one needs an owner decision, not an agent's judgement.

| # | Not doing | Why (decision) | When it could come back |
|---|---|---|---|
| NG1 | An OpenCode 1 client | Removed from Flutter in `40ba4fa32`. Recognition of legacy servers stays (ADR §5.2), and so does the one-way OpenCode 1 → 2 history migration: the `opencode.db` copy (with its wal and shm) before OpenCode 2's first start, and the `requiresOpenCode2Migration` and `openCode2HistoryPending` profile flags (map PROTOCOLS-34, BUILTIN-LINUX-62; ADR §9.7 step 3). | Never (the client) |
| NG2 | A web app | D17 | Not in this plan |
| NG3 | Windows | D17 | Not in this plan |
| NG4 | iOS or desktop as products before their phases | ADR §1 and §7.1: they compile only | Their own phases after the switch (D13, D14) |
| NG5 | AI Team, FactsBook (Memory) and Gas City at the switch | D6 default. What the native app does about them at the switch is §3.7. | After the switch. Their data stays on disk, and the deletion sweep still removes it (§3.7). |
| NG6 | A2A external agents | D6 default: dropped. Their saved data is handled by S02-7. | Only if the owner reverses D6 |
| NG7 | Termux beyond migrate-out | D7 default | — |
| NG8 | The direct ACP client (`lib/acp`, never wired), the code-relay Claude sign-in, Termux-hosted Claude Code, Termux Gas City | agents-backends §2: "do not port" (features 17, 18, 39, 54) | Never |
| NG9 | New features before the switch, other than what the switch itself needs (native `AppUpdater`, `MigrationGate` and safe mode, the migration, the crash-loop guard) | Parity comes first | After the switch, as normal feature slices |
| NG10 | Redesigning screens | We port the approved look. A deliberate visual change needs a concept image the owner picked (`judge-visuals-hard`). | After the switch, or earlier with the owner's OK |
| NG11 | Changing copy that a parity flow asserts | Flows select by visible text (§6.6) | After the switch, or earlier with the owner's OK and a flow variant |
| NG12 | Porting the widget tests (7,967 counted) | The owner: "7400 for widgets can come later" | When the owner schedules it. The first step is the 200-test classification (ADR §4 row 18). |
| NG13 | Glance widgets, new widget sizes, a widget redesign | ADR §3.3; freeze decision 2 (keep Conversations 4×2 and Status 2×2) | After the switch |
| NG14 | Live backgrounds (20 looks) and illustration scenes | D11 | After the switch, or dropped |
| NG15 | A vendor analytics or crash-upload SDK (Aptabase, PostHog, Firebase, Sentry and the like, registry X-04); any analytics send before the owner names a destination; crash upload | The owner's analytics model (freeze decision 6, reversed for native) is our own core with our own HTTP senders (G7, §1.1 row 17). A Firebase sender would need Google's SDK, so it can never be in the F-Droid-ready release build. | A vendor SDK only in a separate non-F-Droid flavor, by owner decision. Destinations: S02-5. |
| NG16 | Moving data into Room at the switch | D9 / ADR §9.8: default switch+1 unless the measured trigger fires | Switch+1 |
| NG17 | Code push in the native app | Shorebird ends at the switch (ADR §9.9) | Never (there is no Kotlin code push) |
| NG18 | Rewriting what is already native and working: the Rust phone engine, the proot binaries, the shell scripts, the Termux terminal library | ADR §5.2; the agents-backends reuse map | — |
| NG19 | Changing OpenCode, Paseo or Codex servers | We are a client | — |
| NG20 | A Google Play listing; the F-Droid submission | Play: issue #42 closed as not planned by the owner on 2026-10-31 ("don't complicate things"); it is also risky for the on-phone Linux. F-Droid: freeze decision 1 (after the rewrite). | Play: only if the owner reopens #42. F-Droid: after the switch. |
| NG21 | A new identity: package, signers, URL scheme, channel ids, shortcut ids, worker class names | ADR §9.9 | Never |
| NG22 | AI Team merge undo, the agent browser, per-file review | Freeze decisions 3 and 5; the server can only revert per message | Not now |
| NG23 | Pre-release libraries in the switch release | D12. The one exception is Material 3 1.5 beta inside the kit, if Google has not finished it by then. The rest of the CMP 1.13 stack (runtime, foundation, ui; ADR §8 pins 1.13.0-beta01) must be stable at the switch, or the owner OKs shipping the beta (S02-6). | — |
| NG24 | Glass styles at the switch, beyond the D10 frost on the composer and chat header | `no-glass-rule` (Glass and Crystal removed 2026-10-26) | One optional native glass Style trial after the switch (owner answer "A", 2026-11-01): Haze/`RenderEffect` blur plus AGSL refraction, on the top bar and sheets only. It ships only if the owner likes it on the phone. |
| NG25 | An app lock or biometric gate | Issue #37 closed as not planned by the owner on 2026-10-31 ("don't complicate things"), although native makes it cheap (desktop-web-cross item 44) | Only if the owner reopens #37 |

---

## 3. Scope

### 3.1 The units of scope

| Unit | Count | Where | Id format | Used for |
|---|---|---|---|---|
| Map feature | 700 (protocols 46, state-data 55, agents-backends 63, builtin-linux 64, ui-kit 58, screens-nav 112, chat-genui 81, media-voice 50, android-platform 65, packages-build 41, desktop-web-cross 65) | `map/*.md` | `<MAP>-NN`, e.g. `SCREENS-NAV-14`, `AGENTS-BACKENDS-08` | Planning: each feature lands in exactly one slice (section 15) |
| Catalog row (behaviour) | 1,544 (unit 677, contract 267, integration 446, screenshot 60, migration 65, e2e 29) | `catalog/BEHAVIORS.md`, `catalog/behaviors.jsonl` | `B-<AREA>-<NNN>` | Parity: each row gets a native test |
| Device flow | 39, proving the 240 rows with `portable_e2e = true` | `catalog/e2e-flows.md` | `E2E-NN` | Parity on a real device, Flutter first |
| Existing Kotlin test | 46 JVM files with 645 `@Test` and 13 instrumented classes at `65e3f70a3`; 52 files with 674 `@Test` at the freeze tip `fc5c1e78f` [R]. The catalog does not cover them: its sources are Dart tests only. | `android/app/src/{test,androidTest}` | the class and method name, unchanged | They encode device findings (runtime ownership, drain rules, schedule DST and claims, widget size policy, restore authority). They move green with their source (§5.6) and are counted by the Kotlin-test ratchet (§6.5). |

### 3.2 Scope per map

| Map | At the switch | After the switch (decision) | Not ported |
|---|---|---|---|
| protocols (46) | OpenCode 2 client (routes, the 91-event registry, pairing, forms, PTY over WebSocket); Paseo transport; Codex app-server; probes; cleartext consent; our own SSE loop; the OpenCode 1 → 2 history import and its two profile flags (PROTOCOLS-34; NG1) | Gas City supervisor and phone-engine `/v1` clients (D6) | OpenCode 1 client (gone), A2A (D6), direct ACP client |
| state-data (55) | Profiles, secrets, trust, drafts, offline queue, stash, schedules, read state, the deletion sweep (including the keys, files and secrets of deferred features, §3.7), the day-1 migration | AI Team state (D6); the move into Room (D9) | The "While you were away" recording (`oc.automaticActivity.*`) has no screen today (screens-nav risk 11): S02-8 |
| agents-backends (63) | P0 daily path: Claude Code on the phone through Paseo (install, check, sign-in, chat, permissions, steer, resume, alerts). P1: Paseo on the PC, Codex, usage, handoff, sub-agents. ACP agents through Paseo ("limited"). Also: MCP chat, connector search and integration sign-in (58); usage subscriptions and the quota collector with its token (59); BYO host, VPS adoption over SSH with the `oc/byo_host_signer` Keystore keys (60); the AI setup assistant, review-only (61); isolated task launch and the free-model notice (62). | AI Team phone engine and Gas City (D6); features 40, 41, 56 (Claude Agent provider, Paseo exit, role cycle) through D8 and D6. The Claude Agent provider is not in the product (owner, 2026-10-31): a spike may use only an Anthropic API key, never the subscription login, and is branded "Claude Agent", never "Claude Code". | Features 17, 18, 39, 54 |
| builtin-linux (64) | Setup engine, start, probe, recovery, healing, idle, thermal, process budget, local terminal, ports and dev servers, storage, packages and the `linux-job` (42), Reset Linux (43), Git on the phone (58), the quota collector host (60), the OpenCode 1 → 2 history copy (62), and the Linux manager (services, vitals, backup; see §3.5). The built-in Linux bridge code that lives in `lib/termux` today is built-in Linux, not Termux (§3.3). | AI Team inside Ubuntu (D6) | Termux as a runtime host, except migrate-out (D7) |
| ui-kit (58) | Tokens; 5 styles + Fine-tune + Saver; every part that switch screens use; motion; text scale; max widths; teaching parts; the running composer glow (§1.1 row 22) | Live backgrounds, illustration scenes (D11); one optional native glass Style trial (owner "A", 2026-11-01, NG24), shipped only if the owner likes it on the phone | Glass and Crystal (removed by the owner); widgetbook (replaced by the `:desktopApp` gallery) |
| screens-nav (112) | Every screen, sheet and dialog of the reference build except those named here. Conversations home with no bottom bar on phones; rail or sidebar on wide windows. | AI Team and FactsBook screens (D6) | "Press back again to exit" (D5); web shell (D17). Merges under the simplify rule only with the owner's OK (section 07). |
| chat-genui (81) | Transcript; turns, steps, notices; cards (the `oc-ui` contract, ported); forms; permissions and questions; composer; review and revert; find; export; phrase chips | AI Team team-conversation views (D6) | A2UI and Remote Compose are not used (ADR §3.3). That removes no feature. |
| media-voice (50) | Voice typing (sherpa-onnx); model packs and the total-RAM gate; read aloud, including Kokoro; attachments; image viewer; PDF; QR scan (zxing-cpp); Wi-Fi share; Manage space and Storage list the Gemma model folder `files/local_llm_models/` with a Delete action (§3.7) | The Gemma assistant (§3.5, S02-3) | ML Kit (freeze decision 1) |
| android-platform (65) | Notifications (channels, status row 4747, Live Updates for user-started runs); FGS; tiles; schedules; the two widgets in the picker (Conversations 4×2, Status 2×2; RemoteViews), widget actions and overlay panels; the five retired providers hidden from the picker (Sessions card 2×2, list 4×4, strip 4×1; Usage 2×2 and 4×2) keep drawing wherever they are already placed (section 11 D11-3 default: the sessions ones draw the Conversations or Status layout at their current size, the usage ones draw usage rows); shortcuts; share in and out; restore after boot and update; exit reasons; Tailscale handoff | AppFunctions (D16 default); widget sizes and redesign (freeze decision 2) | — |
| packages-build (41) | Release plumbing (tag `v<x.y.z>+<N>`, asset names, `SHA256SUMS`, notes format); size gates; notices and licences; the commit gate; ABI splits; R8 | — | Shorebird, the pinned Flutter toolchain, the Flutter golden harness, widgetbook, `GeneratedPluginRegistrant` checks, the `flutter_animate` ban |
| desktop-web-cross (65) | Security invariants; privacy; the analytics core (G7); diagnostics; category-only crash handling; the accessibility contract, proved by S-28; the keyboard and mouse layer wherever it serves Android (desktop-web-cross §1.8) | Desktop product (desktop phase); iOS (D13) | Web shell (D17), Windows (D17); an app lock (NG25) |

### 3.3 Catalog rows in switch scope (estimate)

**Rule: a row is scoped by its behaviour text, never by its file name.** File names mislead here: `lib/termux` is
the built-in Linux bridge folder, `paseo_acp_pilot_test.dart` is ACP through Paseo, `web_search_test.dart` is an
OpenCode 2 chat feature, and `perf_memory_test.dart` is about log chunks, not Memory.

1. **First pass (a filter, not a decision):** a row is a *candidate* for moving out when every one of its source files
   is named for AI Team, Gas City, the phone engine, FactsBook, A2A, Termux, the web shell or the direct ACP client,
   with these tightened patterns:
   - direct ACP means `acp_client_test` only;
   - web means `web_shell`, `browser_mode` or `browser_*`, never `web_search`;
   - Termux means `termux_*` and `setup_termux_*`, minus `termux_migration_*` (D7 keeps migrate-out);
   - Memory means files under `test/factsbook/`, not any name containing "memory".
2. **Decision, per row:** the coordinator reads the row text. A row leaves the switch only with a one-line `reason` in
   `scope.jsonl` tied to D6, D7, D17 or NG8 ("Termux as a runtime host: D7"). The test-writer lead reviews the list.
   **When in doubt, the row stays in the switch.**
3. **Data protection stays:** any row that deletes, sweeps or redacts data of a deferred or dropped feature, or says
   that data must never be logged, stays `scope = switch`, amended to "even though the feature is absent" (§3.6, §3.7).

**Rows the first pass would wrongly move out (enter as `switch` on day 1):**

| Rows | Why they stay |
|---|---|
| B-LINUX-233 to 238, B-SEC-177 | Termux migrate-out, which D7 keeps |
| B-LINUX-227 to 229, 231, 232, 239, 240, 242 to 248, B-SEC-170 | The built-in Linux (setup scripts, Linux bridge, process manager, "the phone server counts as running only on a live loopback answer", Ubuntu bootstrap, storage cleanup). B-LINUX-227, 229, 240, 245 and 247 are `portable_e2e`, proven in E2E-17, 18 and 19. |
| B-LINUX-230, 241 | The OpenCode 1 → 2 move of managed installs (NG1 keeps it) |
| B-AGENT-142 to 145, B-SEC-109, B-SEC-110 | ACP agents through Paseo (§3.2 "limited") |
| B-CHAT-124, B-PROTO-125, B-PROTO-126 | OpenCode 2 web search, a chat feature |
| B-BUILD-024 | Log chunk boundaries (`perf_memory_test.dart`) |
| B-STATE-020, B-STATE-145, B-SEC-075, B-SEC-176, B-AGENT-193, B-AGENT-301, B-AGENT-302, B-SEC-063 | Deletion, sweep and redaction of AI Team, Memory and A2A data (rule 3). The coordinator's read finds any others, for example B-STATE-058. |

The rows that still leave with the Termux label are B-LINUX-216 to 219 (Termux as a runtime host, D7).

**Estimate [E]** after the rules above. The exact numbers come from `native/catalog/scope.jsonl` (§3.6), which slice
0 writes after the per-row read.

| Area | Rows | Est. moved out by the D6/D7/D17 defaults | Est. in switch scope |
|---|---|---|---|
| NAV | 82 | 2 | 80 |
| CHAT | 124 | 0 | 124 |
| AGENT | 318 | 109 | 209 |
| PROTO | 127 | 32 | 95 |
| STATE | 158 | 5 | 153 |
| SEC | 183 | 15 | 168 |
| ANDR | 121 | 1 | 120 |
| LINUX | 251 | 40 | 211 |
| MEDIA | 34 | 0 | 34 |
| KIT | 62 | 1 | 61 |
| L10N | 24 | 2 | 22 |
| XPLAT | 13 | 0 | 13 |
| BUILD | 34 | 1 | 33 |
| MIGR | 13 | 0 | 13 |
| **All** | **1,544** | **208** | **1,336** |

What still moves out is mostly AI Team and Gas City (about 140 rows), FactsBook (about 50), the direct ACP client (11),
A2A (7) and Termux as a host (4); some rows carry more than one label. The catalog top-up in §3.5 adds rows on top of
the 1,336.

### 3.4 Flows at the switch

- **37 of 39 flows** must pass.
- Under the D6 default, two flows move to after the switch: E2E-28 (AI Team on the phone) and E2E-32 (Memory on the
  phone). Together with the deferred rows, about 16 `portable_e2e` rows leave the switch set, so about 224 of the 240
  stay [E]. The built-in Linux rows of E2E-17, 18 and 19 stay (§3.3). The exact count comes from `scope.jsonl`.
- E2E-39 (upgrade in place) must pass.
- If the owner reverses D6, the count goes back to 39.

### 3.5 Features merged after the catalog was taken (the freeze-tip delta)

The catalog and the maps were taken from `oc_app-batch1` at `65e3f70a3`. The freeze branch `int/freeze-2026-11-01`
(tip `fc5c1e78f` when this was written) contains that commit and adds 22 merged branches. Counted on 2026-11-01 [R]:

- 66 new test files;
- 396 changed test files;
- about 459 `test(`/`testWidgets(` calls in the new files;
- 85 new `lib/` files;
- 23 changed Kotlin files under `android/` (`git diff --name-only 65e3f70a3..fc5c1e78f`), among them the new
  `AppUpdateChannel.kt`, `AppUpdatePolicy.kt`, `AgentPriority.kt`, `ServiceNoticeGate.kt` and
  `ClipboardImageBridge.kt`.

**None of these features exist in `oc_app-batch1`.** Until `flutter-final` is tagged, the source for every feature in
the table below, for every twin (§5.6) and for every top-up row is the read-only worktree
`/home/eslam/Storage/Code/wt/oc_app-freeze` (branch `int/freeze-2026-11-01`), read at a commit named in the brief.

Each feature gets a disposition. This is S02-3, and it answers ADR D16 for the branches the freeze has already merged.
"Switch" means it ships in the switch release and is covered by the catalog top-up below.

| Feature (freeze ledger item) | Default | Why |
|---|---|---|
| App updates: notice, badge on the gear, Update now sheet, Updates page (`feat/app-updates-2026-10-31`) | Switch | ADR §9.9: the native `AppUpdater` ships in the switch release; B-MIGR-012 |
| Process budget, the kill line and the guided card for the child-process limit | Switch | Phone agents die at 32 processes (`phantom-process-limit`); this is the daily path |
| Background-CPU fix, heat and battery pacing, agent background priority (`fix/background-cpu`, `perf/native`) | Switch | G6; S-15 to S-19 |
| The phone server restarts itself after an OpenCode update | Switch | Daily path |
| Wi-Fi share of chat file links; opening a phone file from a chat link | Switch | `:engine:media`, `:feature:project` |
| Linux services pages and Linux vitals (`feat/linux-vitals`) | Switch | D16; the owner approved the Linux manager plan on 10-31 |
| Linux backup and restore (`feat/linux-backup`) | Switch | D16; it protects the owner's data |
| Paste a picture from the clipboard | Switch | Small; composer |
| Star the app, share the app, What's new walkthrough (`feat/star-share`) | Switch | Small; About |
| Steer-join, partial-syntax streaming, large-text and free-limit fixes; the `codex/audit3` security fixes | Switch | They are behaviour, and the audit fixes are security |
| Tested pins and phone agents updating themselves when idle; updates that yield to the person's work; Wi-Fi only for big downloads (`fix/pins-2026-10-31`, merged inside `int/combined` at `fc4c8f126` and `021d454c4`) | Switch | Layer 2 of `app-updates-two-layers` (owner option A: tested pins only). After Shorebird ends, pins move only with APK releases. |
| Claude Agent tried beside Claude Code | Follows D8 (default: Paseo) | Agent-path decision. Owner, 2026-10-31: not in the product. A spike may use only an Anthropic API key, never the subscription login, and is branded "Claude Agent", never "Claude Code" (Agent SDK terms). |
| Role voices in AI Team task conversations | Follows D6 (after the switch) | AI Team |
| "Assistant on this phone" (Gemma, `feat/gemma-assistant`, merged at `62a306a24`) | **After the switch**. The bridge release hides its entry points (Settings row, search entry, the chat menu item), so no user gets it and then loses it. Manage space and Storage list `files/local_llm_models/` (about 3.7 GB) with Delete. | Unproven on a device (freeze ledger §2), and it needs a large on-device model runtime |
| Browser mode (`fix/web-preview`) | Dropped | D17 |
| AppFunctions (not merged, blocked) | After the switch | D16; optional path in `:android:platform` |

**Catalog top-up rule.**

- **What:** before slice 1 closes, the test-writer lane catalogs the logic tests added or changed in
  `65e3f70a3..<freeze tip>` (later `..flutter-final`), with the same method as the first pass. Each changed Kotlin test
  file is handled by the twin rule (§5.6), not by a catalog row.
- **Ids:** only the test-writer lead hands out ids (§3.6). A new row is **appended** with the next free id in its
  area. Changed behaviour never edits an existing row: it goes into that row's `amended` field in `scope.jsonl`, with
  the reason.
- **Sources:** `sources` cite paths in `oc_app-freeze` and name the commit they were read at
  (`"sources": ["test/app_update_policy_test.dart:41"], "sources_commit": "fc5c1e78f"`).
- **Owner:** the test-writer lane lead. The coordinator reviews.

### 3.6 The scope file: `native/catalog/scope.jsonl`

- **What it is:** one line per catalog row. It holds the row's scope, slice and proving module, plus any amendment the
  ADR or an owner decision forces. The coordinator writes it in slice 0 and is its only editor.
- **The catalog copy is append-only:** `catalog/behaviors.jsonl` is copied once to `native/catalog/behaviors.jsonl`.
  - Existing rows are never edited, renumbered or removed. A changed behaviour goes only into `scope.jsonl` `amended`;
    a removed one becomes `scope: dropped` there.
  - New rows (the top-up in §3.5, fix-forward proposals in §5.3) are **appended**.
  - The test-writer lead is the file's only editor and the only one who hands out ids, so two lanes never take the
    same "next free id". `ScopeFileTest` fails if a committed line of an existing row changes.
- **Amendments:** an amendment changes what the native test must assert, never the row id.

```json
{"id":"B-MIGR-010","scope":"switch","slice":6,"module":":android:migration","decision":"ADR §9.7","amended":"Source files are never deleted in the switch release; source keys stay at least one release (ADR §9.7, §9.8)."}
{"id":"B-AGENT-038","scope":"after-switch","slice":null,"module":null,"decision":"D6","reason":"AI Team phone engine setup states; no switch feature uses them"}
{"id":"B-STATE-020","scope":"switch","slice":1,"module":":engine:profiles","decision":"02 §3.3 rule 3","amended":"Profile deletion removes the Memory pairing, token, key and outbox for that server only, even though Memory is not in the app."}
```

```kotlin
// :quality, read by ScopeFileTest and CatalogReport
@Serializable enum class Scope { @SerialName("switch") SWITCH, @SerialName("after-switch") AFTER_SWITCH,
    @SerialName("desktop-phase") DESKTOP_PHASE, @SerialName("ios-phase") IOS_PHASE, @SerialName("dropped") DROPPED }

@Serializable
data class ScopeRow(
    val id: String,                 // "B-STATE-018"
    val scope: Scope,
    val slice: Int? = null,         // 0..6, required when scope == SWITCH
    val module: String? = null,     // Gradle path whose tests prove it, e.g. ":engine:delivery"
    val decision: String? = null,   // required when scope != SWITCH or amended != null: "D6", "ADR §9.7", "S02-3"
    val reason: String? = null,     // required when scope != SWITCH: one line read from the row text (§3.3 rule 2)
    val amended: String? = null,    // replacement wording the native test must assert
    @SerialName("amended_at") val amendedAt: String? = null, // ISO date; the ratchet exemption in §6.5 reads it
    @SerialName("split_from") val splitFrom: String? = null,
)
```

`ScopeFileTest` fails the build when any of these is false:

- every id in `behaviors.jsonl` (including top-up rows) appears exactly once;
- `decision` is present whenever `scope != switch` or `amended` is set, and `reason` whenever `scope != switch`;
- `slice` is present whenever `scope == switch`;
- every id matches the regex in §6.1;
- no committed line of an existing row in `native/catalog/behaviors.jsonl` changed (append-only).

**Rows the ADR or an owner decision already amends, or explicitly leaves alone** (enter them on day 1):

| Row(s) | Amendment | Decision |
|---|---|---|
| B-MIGR-004 | The signer is the one of the lineage being updated: `1DE5BF08…D60C` for owner builds, `842284B2…1C` for public releases. versionCode ≥ 10000. | ADR §9.9, D2, D3 |
| B-MIGR-010 | Source data is never deleted in the switch release. Keys stay at least one release; files are never moved. | ADR §9.7, §9.8 |
| B-ANDR-068, B-MIGR-012 | Shorebird clauses become the native `AppUpdater` (GitHub `releases/latest`, `SHA256SUMS`, same-signer check, `PackageInstaller`) | ADR §9.9 |
| B-NAV-058, B-CHAT-122, B-AGENT-229, B-PROTO-041, B-LINUX-124, B-LINUX-169, B-BUILD-004 | Clauses about the OpenCode 1 **client and wire** are dropped (for example B-CHAT-122's "OC1 edit" wording, B-AGENT-229's OC1 chat agent, B-PROTO-041's OC1 history confirmation). The OpenCode 2 clauses stay. Each row is checked by hand when its slice starts. | NG1 |
| B-LINUX-077, B-LINUX-107, B-LINUX-110, B-LINUX-194, B-LINUX-197, B-LINUX-230, B-LINUX-241, B-MIGR-002 | **Not amended.** These are legacy recognition and the one-way OpenCode 1 → 2 history migration: copy `opencode.db` with its wal and shm before OpenCode 2's first start, keep `requiresOpenCode2Migration` and `openCode2HistoryPending`, refuse to connect until the import is confirmed, never install OpenCode 1. All of it stays (NG1, PROTOCOLS-34). | NG1 |
| B-KIT-018 | Frost only on the composer and chat header, per D10 | D10 |
| B-KIT-020, B-KIT-047 | After the switch (live background) | D11 |
| The day-1 switch rows of §3.3 rule 3 (B-STATE-020, B-STATE-145, B-SEC-075, B-SEC-176, B-AGENT-193, B-AGENT-301, B-AGENT-302) | "Even though the feature is absent": the sweep removes the feature's keys, files and secrets by their registered shapes (§3.7). Clauses that need the running feature (attach, simulator, node) are dropped. | §3.3 rule 3, D6 |
| B-SEC-063 | The native app holds no A2A credential. The v3 source cleanup erases every `oc.a2aBearer.*` secure entry with the `oc.externalAgents` and `oc.a2aTasks.*` keys, and no late write can recreate them. | S02-7 |

### 3.7 Deferred and dropped features at the switch

Under the D6 default, AI Team (with Gas City and the phone engine), Memory (FactsBook) and the Gemma assistant are not
in the switch release, and A2A is dropped. People who used them must not meet dead ends, stray processes or orphaned
secrets. Section 05 owns the data details; this is the rule every section follows.

1. **Absent, never dead.** Their entry points are not drawn: settings rows, search results, conversation-list rows,
   AI Team Approve and Resume rows on the Conversations widget, team progress notifications, deep links
   (`opencode-mobile://team` opens the Conversations list). No disabled button stands in for them.
2. **Never started.** The native app never starts or restarts their engines or services: not after boot, not after an
   update, not from a restore record. If it finds one running inside the built-in Linux at first launch (the phone
   engine, `aiteam` or the FactsBook node), it stops it by exact PID through the runtime host, and records that in the
   migration audit.
3. **Data kept and still swept.** Their keys, files and secrets stay untouched on disk (05 Appendix C, class D). The
   deletion sweep still removes them when the person deletes a server, so `ProfileScope` registers every shape on day
   1: `oc.orchestration.<id>.*`, `oc.team*.<id>`, the `files/oc.teamEngine.<id>/` folder (auth token and server
   credentials file), `files/oc.teamEngineDeletion.<id>`, `oc.teamEngineAuth.<id>`, `oc.factsbook.<id>`,
   `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>` and `oc.factsbookNode.<id>`. (Switch features register their
   own shapes as usual, for example `oc.quotaToken.<id>` for the quota collector.) The catalog rows that prove this
   stay in the switch (§3.3 rule 3). A2A data is not profile-scoped and follows S02-7.
4. **One notice.** On the first native launch after an update where any of these held data, one plain status-line
   notice names what is not in this version yet and says the data is kept ("AI Team and Memory aren't in this version
   yet. Your projects and notes stay on this phone."). It is shown once and never again.
5. **Space can be freed.** Manage space and Storage list `files/local_llm_models/` (the Gemma model, about 3.7 GB),
   with a Delete action that names it. Section 05 adds the folder to its Appendix C, and the ADR §9.7 never-moved
   list gains it.

---

## 4. Success criteria (numbers)

Every number has a method, a machine and a gate.

- **Phone:** "NX721J" means the owner's ZTE nubia. Absolute numbers are taken there only with the owner's OK each time
  (`owner-phone-debug-allowed`; read-only adb otherwise, never a screen capture).
- **Emulator:** "OC_API37" is the PC emulator. It gives relative numbers only (testing-perf §9.1).
- **Builds:** every number comes from a release build that is minified, non-debuggable and profileable. A debug Compose
  build proves nothing.
- **Owning section:** section 13 owns the full budget tables. This table is the switch gate, and section 13 may only
  tighten it.

| # | Criterion | Target | Flutter baseline | Method | Gate |
|---|---|---|---|---|---|
| **Parity and update** | | | | | |
| S-1 | Catalog parity | **100 %** of `scope = switch` rows green (≈ 1,336 [E] + top-up), each proven at its level (§6.3). The green counts never drop (the T2 and T3 ratchets of §6.5), except for the amendment exemption there. **Kotlin tests:** every Flutter-tree Kotlin test that moved with its source passes, none is deleted, and the `kotlin_tests.green` count never drops (§6.5). | — | `:quality:catalogReport` reads the JUnit XML of T2 and of the matching T3 (§6.5) | Switch (ADR §10.1-1); a ratchet at every T2 |
| S-2 | Flow parity | **37/37** switch flows green on the native release build, each on the AVD it names (AVD-A unless the flow says AVD-S, AVD-F, AVD-T or AVD-AR). Each flow was green on the Flutter reference build first. **Parity runs use animations off**; this overrides the "animations on" in the catalog's AVD-A row (e2e-flows). Every T3 also runs one animations-on smoke flow, E2E-05, so the default-motion paths (container transform, word fade) run on a device (testing-perf §6.2). | Green on Flutter R by definition | Maestro 2.12.0 | Switch (ADR §10.1-2) |
| S-3 | Upgrade journey | **Every step** of ADR §10.2 passes. Also passes when interrupted after step 0 and after each `MigrationRunner` step; a rerun is a no-op. Two legs, each starting build named by commit and versionCode: **(a) owner lineage** (`1DE5BF08…D60C`): from the exact build installed on NX721J (read with `dumpsys package`, rebuilt from its commit and seeded on the rootable AVD; 05 §10.4), from the local-key build of `flutter-final` (or of the freeze gate commit until it exists), and from the bridge release on that lineage if D4 ships it. **(b) public lineage** (`842284B2…1C`): from the GitHub `v2.1.0+57` APK (tag commit `e44b581aa`, versionCode 57) to a CI-signed native candidate. | — | Rootable `google_apis` emulator + Maestro + `adb root` file checks. The seed includes one retired widget provider (§3.2 android-platform), which must redraw. | (a) at every T3 that touches migration, before any owner APK; switch. (b) once per switch or public candidate, through CI with the owner's explicit OK (§7.9), recorded in that candidate's QA record. |
| S-4 | No silent loss | **0** secrets lost silently: each unreadable one sets the "enter the password again" flag and the notice names the server. **0** changed bytes in the protected set, and **0** missing paths in the runtime set (below). The count-and-hash audit matches. | — | Upgrade journey; `adb root` + `find … -type f -exec sha256sum`. The journey also seeds a stash with an attachment and a pending photo, and compares them. | T3; switch |
| **Start-up** | | | | | |
| S-5 | Cold start, time to initial display | Median **≤ 300 ms** on NX721J [E], and on OC_API37 **≤ 0.75×** the Flutter R median measured the same day [E] | ≈ 400 ms (owner's side-by-side, 2026-11-01); demo ≈ 250 ms [R brief] | Macrobenchmark `StartupTimingMetric`, `StartupMode.COLD`, `CompilationMode.Partial()`, 10 iterations. On the phone: `am start -W -S` `TotalTime`, median of 10. | T4 at every slice end; switch |
| S-6 | Time to full display (Chats list with cached rows) | Median **≤ 500 ms** on NX721J [E] | Measure in slice 0 | `reportFullyDrawn()` when the first page of rows is drawn from cache | T4; switch |
| S-7 | `OcApplication.onCreate` | **≤ 50 ms** on the fast path (migration already done) | — | `androidx.tracing` section `app.onCreate` | T4 |
| S-8 | First native launch | The "Updating your app…" state appears on the first frame. Migration completes in **≤ 3 s** at the owner's real data size, seeded on the emulator [E]. Step 0 takes **≤ 1 s** for 100 MB (ADR P5). Try again appears at 8 s (ADR §9.7). | — | Trace sections + upgrade journey | T3; switch |
| **Smoothness** | | | | | |
| S-9 | Streaming a reply (mock model, a 6,000-word reply with 3 code fences, at the live edge) | `frameOverrunMs` p95 **≤ 0 ms**, p99 **≤ 4 ms** on NX721J at 120 Hz [E]. A tail update on a 200-block reply re-lays out **1** block. On OC_API37: no more than 10 % worse than the last green. | Measure Flutter R on the same journey with Perfetto (§4.1) | Macrobenchmark `FrameTimingMetric`; unit counter for the re-layout | T4 at the end of slices 2, 3 and 6; switch |
| S-10 | Fling a 5,000-part transcript | Same as S-9 | Measure in slice 0 | Macrobenchmark | T4; switch |
| S-11 | Open a 300-turn chat (warm) | First transcript **≤ 300 ms** | Flutter mark `chat.open_to_first_transcript` | Trace section `chat.openToFirstTranscript` | T4; switch |
| S-12 | Jank while streaming | **≤ 1 %** janky app-layer frames on NX721J [E] | A snapshot under load: 41 % janky, p90 61 ms, p99 200 ms (perf audit 2026-11-01; not a clean baseline) | Perfetto SurfaceFlinger FrameTimeline, by layer. This also works for Flutter, whose frames Macrobenchmark may not see (testing-perf §9.2). | T4; switch |
| S-13 | Transitions: list → chat container transform, predictive-back cancel, sheet open, keyboard open | `frameOverrunMs` p95 **≤ 0 ms** on NX721J; p90 frame duration **< 8.3 ms** [E] | — | Macrobenchmark journeys 4 and 5 (testing-perf §9.1) | T4; switch |
| **Size** | | | | | |
| S-14 | APK size | arm64-v8a **≤ 22 MB**, universal **≤ 45 MB**. Growth **≤ 1 MiB** per release unless the release notes explain it. | arm64 ≈ 36 MB, universal 108 MB [R] | `check_apk_size.py` on the signed files | T2 size gate |
| **Background and battery** | | | | | |
| S-15 | Idle and hidden (no turn, no phone server, no agents, live mode off) | App process CPU **≤ 1 s per hour**. **0** wakeup alarms from the app except schedules the person set, over a 30-minute screen-off run [E]. | Hidden timers of 5–15 s (Dart), plus a 1 s lease pulse and a 60 s orphan scan (native) [R perf audit #11] | `/proc/<pid>/stat` utime+stime delta; `dumpsys batterystats <pkg>` wakeup alarms after `--reset` | T4; switch |
| S-16 | Hidden while a phone agent streams (20 helper frames/s) | App process **≤ 5 %** of one core [E] | 56 % before the fix, 7 % after (emulator, commit `4ae686c5b`) [R] | `/proc/<pid>/stat` delta over 60 s | T4 (slice 3 end); switch |
| S-17 | Hidden timers | No repeating timer in the app process fires more often than every **60 s** while hidden and idle (no lease held, no turn running) | See S-15 | Code review plus a `TraceSectionMetric` count during the S-15 run | T4 |
| S-18 | Reconnect while hidden or offline | Matches the reference `ReconnectBackoff` (`lib/api2/reconnect_backoff.dart` at the freeze tip; row source `test/reconnect_backoff_test.dart`, a top-up row). **Foreground:** 0.5 s doubling to a 16 s cap. **Hidden:** the first two tries keep the foreground pace, so a short drop heals fast and needs-you and finished-reply alerts are not delayed; after that **≤ 1** attempt per connection every 60–120 s (jittered). **Return to the app:** one immediate try. **0** attempts while Android reports no network. | A radio wake every 16 s before the fix (perf audit #6) [R] | Request log of the fixture server + airplane-mode run | T3; switch |
| S-19 | Status-notification updates | Re-posted only when the words change, at most **1 per 5 s**. **≤ 60 per hour** with 3 agents running tools [E]. | 3,015 updates in 1 h 50 min [R perf/native record] | `dumpsys notification` / `lastStartId` | T3; switch |
| **Memory and stability** | | | | | |
| S-20 | Memory | App PSS on the streaming journey with the 5,000-part transcript open: **≤ 0.6×** Flutter R's PSS on the same AVD [E] | 458 MB for the app (owner phone snapshot under load) [R] | `dumpsys meminfo <pkg>` TOTAL PSS, median of 3 | T4; switch |
| S-21 | Stability | Monkey on the release build: **0** crashes and **0** ANRs, at **3 seeds × 10,000 events** for slice ends and **3 seeds × 50,000 events** for switch candidates. **0** crashes in all T3 runs. StrictMode in the `qa` build: **0** main-thread disk or network violations outside the allowlist (step 0 only). | — | `adb shell monkey -p <pkg> -s <seed> --throttle 50 --pct-syskeys 0 <events>`. At `--throttle 50`, 50,000 events take at least 2,500 s per seed, so monkey runs as its own detached job (`native/tool/monkey.sh`, §8 script contract), outside T3's budget, while the emulator lock is held. | Slice ends (10,000); switch candidates (50,000); switch |
| **Privacy and platform** | | | | | |
| S-22 | Privacy | Seeded canary secrets appear **0** times in logcat, diagnostics, the problem report, notifications, widget snapshots, **analytics payloads (the queue and the sent log)** and test output. Network destinations stay within the checked-in host allowlist (`KnownHosts`, section 16 N16-D7, generating `native/config/egress-allowlist.toml`), plus the servers the person configured. The list is seeded from the hosts the Flutter code at the freeze tip contacts on its own (for example `api.github.com` for releases, device-flow sign-in and starring; `registry.modelcontextprotocol.io`; the pinned download hosts) and the analytics endpoint once S02-5 names it. Adding a host needs a reason in the commit body. | — | Canary seeding in the upgrade journey and T3; egress harness (section 16 §2.5) | T2 + T3; switch |
| S-23 | F-Droid-ready | **0** artifacts in `releaseRuntimeClasspath` from `com.google.android.gms`, `com.google.firebase`, `com.google.mlkit`, `com.google.android.play`, `com.google.android.datatransport`, `dev.shorebird` or `io.sentry`, nor any vendor analytics or crash SDK (registry X-04, for example `com.posthog`, `com.aptabase`). Our own analytics core links no SDK and is allowed. F-Droid shows the "Tracking" anti-feature if BASIC sends anything (S02-5). | ML Kit 2.1 MB and Shorebird today [R] | `:quality` `BannedDependenciesTest` over the resolved graph | T2 |
| S-24 | Multiplatform | Every KMP module compiles for desktop. The nightly iOS klib compile is green for `core`, `protocol` and `engine` (and for UI modules if P1 allows). The `:desktopApp` gallery starts headless on the VPS (`xvfb-run`). | — | T2 + nightly VPS job | T2 |
| S-25 | Kit | **0** Konsist allowlist violations. Capacity tests green at 280/320/360/412/1600 dp × text scale 1.0/2.0/2.5 × en/ar, with extreme content (`component-max-width-rule`; 2.5 is the app's root cap). | — | Konsist + `:ui:kit` capacity tests | T1/T2 |
| S-26 | Words | **en and ar are complete at merge:** every key exists in both. **es, ja, pt, ru and zh may lack keys** and fall back to English (at the freeze tip they hold 2,695, 661, 661, 661 and 8,654 of en's 9,837 keys [R]). The `:tools:strings` drift check fails only on mismatched format arguments or plural forms in keys that exist; it reports per-language coverage without gating on it. This is how 02 reads the ADR §5.2 wording "every key in 7 languages". | — | `:tools:strings` | T1 when strings change; T2 |
| **Owner** | | | | | |
| S-27 | Owner verdict | The owner phone test passes (§6.7), and the owner's own words on smoothness are recorded with the date | The owner felt the demo was "so so so much smoother" | Owner phone test record | Switch (ADR §10.1-4) |
| **Accessibility and analytics** | | | | | |
| S-28 | Accessibility contract (desktop-web-cross §1.9) | Over the 37 switch flows: every interactive node has a label and a touch target of **≥ 48 dp**. Status changes are announced **once** through a live region. Critical flows (E2E-01, 05, 06, 08, 25, 26) pass at text scale **2.5** on 360×740 and 320 dp wide, in en and ar. Under reduced motion no animation runs past its end frame. | Flutter: `accessibility_guidelines_test` (9 widget tests), `terminal_accessibility_test` (10), `text_scale_overflow_*` | A UIAutomator node sweep run by `t3.sh` after each flow; Maestro runs at font scale 2.5 on a 360×740 AVD profile; the kit's reduced-motion tests. The accessibility widget tests are the first item of the widget-test phase and are ported before the switch; NG12 does not cover them. | T3 at slice ends; switch |
| S-29 | Analytics privacy (G7) | Every event class holds only enums, booleans and buckets (no `String`, number or collection fields). DETAILED events are dropped unless the person opted in. With the sender set to none, **0** analytics requests leave the device. The Privacy page and PRIVACY.md list every BASIC event. | — | `AnalyticsPrivacyTest` and Konsist rule K-SEC-11 (section 16 §5); egress harness | T1/T2; T3; switch |

**What S-4 compares, and when.** A running built-in Linux server writes into the rootfs (its database, logs, `/tmp`,
home dot-files), and the native app restores that server after the update (B-MIGR-008, B-MIGR-013). A whole-tree hash
would therefore always differ. So:

- **Before** manifest: taken after the Flutter app's server is stopped, right before `adb install -r`.
- **After** manifest: taken when `MigrationRunner` has set `native.migration.v1` and **before** the runtime restore. A
  `qa`-build flag holds the restore until the manifest is written. The restore is never switched off to pass S-4.
- **Protected set (SHA-256 of every file must match):** every `files/` and `shared_prefs/` path that section 05 marks
  "untouched" (Appendices C and D): the rootfs package files under `files/linux/**` (minus the runtime set), `projects/`,
  `voice_models/`, `local_llm_models/`, `draft-attachments-v1/`, `prompt-stash-attachments-v1/`,
  `prompt-photo-recovery-v1/`, `diagnostics/`, the crash files, `migrations/<jobId>/`, `oc.teamEngine.<id>/`,
  `oc.teamEngineDeletion.<id>`, `oc.sharedProjects`, `termux-setup-v2.json`, and the eight Kotlin-owned prefs files.
  Section 05 Appendix C gains `local_llm_models/`; the ADR §9.7 never-moved list gains it and
  `prompt-stash-attachments-v1/`.
- **Runtime set (existence and size only):** paths inside the rootfs that the server or agents write while running,
  listed by name in `native/tool/upgrade/runtime-paths.txt` (for example the OpenCode database and its wal and shm,
  `/tmp`, log files, home dot-files). Adding a path there needs a reason in the commit.

### 4.1 Baselines come first

The T4 holder measures Flutter R **once, in slice 0**, on the same AVD and with the same tools, before any native
number is judged:

- cold start (S-5);
- the streaming and fling journeys with Perfetto FrameTimeline (S-9, S-10, S-12);
- open a chat (S-11);
- background CPU, wakeups and reconnects (S-15 to S-18);
- PSS (S-20);
- APK sizes (S-14).

Where the results go:

- the record: `docs/qa/native/baseline-flutter-<YYYY-MM-DD>/README.md`;
- the numbers: `baseline.json` next to it;
- the phone half of the baseline is measured only with the owner's OK.

### 4.2 When a number is missed

1. A missed **S-1 to S-4, S-21, S-22, S-23, S-28 or S-29** blocks the switch with no exceptions.
2. A missed performance or size number (S-5 to S-20) blocks the switch. The coordinator either fixes the cause or asks
   the owner to relax the number. Only the owner can relax one, and the new number is written into §4 with the
   decision date.
3. The `[E]` targets are replaced by measured values plus a margin the first time a native build hits them, and never
   loosened after that. The NX721J budgets of S-5, S-9 and S-13 are `[E]` because testing-perf §9.1 sets them only
   after the first measurements; they become fixed after the first T4 on NX721J.
4. After the switch, every number turns into a regression gate: a release may not be more than 10 % worse than the
   previous one on the same device.

---

## 5. Freeze rules for the Flutter app during the rewrite

The owner froze Flutter feature work on 2026-11-01 (`dev-freeze-native-rewrite-2026-11-01`). These rules make that
freeze concrete.

### 5.1 Phases

| Phase | From → to | Line | What happens |
|---|---|---|---|
| F0 closing | Now → the freeze gate passes | `int/freeze-2026-11-01` | Finish and merge the branches in `docs/verification/branch-ledger-2026-11-01.md`. Then run the gate: full split suite, release APK, emulator sweep (none of it has run yet, per the ledger). The commit that passes is the candidate for **`flutter-final`**. |
| F1 maintenance | Gate passed → switch | The freeze line, fast-forwarded to `master` only at owner-approved milestones | Only the "allowed" list in §5.2 |
| F2 after the switch | Switch → native desktop ships | `flutter/maint`, cut from tag `flutter-final` | Flutter Android is finished. The Flutter Linux desktop build gets security fixes only (D14). |

**Tag name:** the last Flutter commit is tagged `flutter-final`, one name used everywhere. Its version, versionCode per
lineage and commit are recorded in the tag message and in `docs/migration/flutter-final.md` (05 §9.3).

### 5.2 Allowed and not allowed on the Flutter line during F1

| Allowed | Not allowed |
|---|---|
| Security fixes (the Flutter 3.47.8 TLS fix, merged at `fc5c1e78f`), and dependency bumps that fix a vulnerability | New features, screens, settings, sheets, widgets or notification kinds |
| Bug fixes for things the owner hits (crash, data loss, broken daily path, wrong behaviour): smallest diff, with a test that fails without the fix | Visual redesigns, new styles, motion work, copy rewrites. Fixing wrong or unclear copy counts as a bug fix and is mirrored (§5.6). |
| A runtime pin bump (OpenCode, Paseo, agents) when the pinned version breaks. The same pin is copied into the native app (§5.6). | New stored keys, files, secure entries or format changes, unless they are part of a security or bug fix (§5.4) |
| The bridge release (D4, §5.7) | New or renamed OS-visible names: components, channels, shortcut ids, worker class names, the URL scheme, authorities |
| Docs, QA records, catalog updates | Refactors; dependency or Flutter SDK upgrades for their own sake |
| | Deleting Flutter tests before the switch (they are the reference) |

### 5.3 Fix-forward: every Flutter fix reaches the native app

- Every Flutter commit made after F0 ends carries a `Behaviors:` trailer that **proposes** rows. Flutter fixers never
  write `native/**` (§7.2) and never hand out ids:
  - `Behaviors: new NAV: <one sentence, Given/When/Then> (test/<file>_test.dart:<line>)` for a new behaviour;
  - `Behaviors: amends B-CHAT-060: <what changed>` for a changed one;
  - `Behaviors: none: <one-line reason>` otherwise.
- The coordinator checks the trailer when merging the Flutter fix and hands it to the test-writer lead.
- The lead turns each proposal into an appended row with the next free id (§3.6), or into an `amended` entry in
  `scope.jsonl` with `amended_at`, and the lane writes or changes the native test within the same week.
- **An amended row never blocks fix-forward.** Its test change lands in the same native merge as the implementation
  change. Until that merge, the ratchet exempts rows whose `amended_at` is newer than the last T2 (§6.5).

### 5.4 Stored formats are frozen

- From the moment the freeze gate passes, the set of Flutter `SharedPreferences` keys, files and `flutter_secure_storage`
  entries is the migration inventory of section 05.
- A fix that must change any of them updates that inventory and adds a STATE or MIGR row **in the same commit**.
- The migration owner (Opus) reviews it.

### 5.5 OS-visible names are frozen (a superset rule)

- The component-freeze list (ADR §9.9) is the **union** of the names in the public GitHub `v2.1.0+57` APK (tag commit
  `e44b581aa`) and in the release build of the freeze tip, later of `flutter-final`, as section 11 §3 already does.
  The freeze tip adds names that `v2.1.0+57` lacks (for example the app-update installer receiver
  `AppUpdateStatusReceiver`).
- If any later Flutter release adds an OS-visible name (for example a receiver or permission the bridge release needs
  for its installer), the list gains that name. The list never loses one.

### 5.6 Twin rule (code and data that exist in both trees during the rewrite)

The native modules copy several Flutter-tree assets, always **from the freeze tip** (`oc_app-freeze`, §3.5), later
from `flutter-final`, never from `65e3f70a3`, which lacks the 22 freeze merges:

- Kotlin from `android/app/src/main/kotlin/…`, into `:android:*` with packages unchanged (ADR §5.2);
- **the Kotlin tests that prove that Kotlin**, from `android/app/src/test/…` and `android/app/src/androidTest/…`. They
  move green with their source in the same commit ("move green", ADR §5.2) and keep their class and method names.
  A refactor or split (for example the `BuiltinLinux` split) moves or rewrites them; it never drops one. Section 14
  §2.9 adds row ids where a Kotlin test already proves a catalog row.
- Android `res` (widget layouts, `previewImage` PNGs, `shortcuts.xml`);
- the agent pin files (`assets/agents/paseo-package-lock.json` and similar);
- the shell scripts held in Kotlin;
- until the one-off ARB → XML conversion, `lib/l10n/app_*.arb`.

Each copy is a **twin**, recorded in `native/TWINS.md`:

```markdown
| Native path | Flutter source path | Source commit at copy | Last synced Flutter commit | Native owner |
|---|---|---|---|---|
| native/android/linux/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt | android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt | fc5c1e78f | fc5c1e78f | S runtime |
| native/android/linux/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnershipTest.kt | android/app/src/test/kotlin/io/github/eslamasabry/opencode_mobile/NativeRuntimeOwnershipTest.kt | fc5c1e78f | fc5c1e78f | S runtime |
```

- `native/tool/twin_check.sh` is the **one** drift mechanism. It lists the Flutter commits that touched a twinned
  source path since its last synced commit. The `tool/native/kotlin-drift.sh` that section 03 S9 names is its
  Kotlin-only subset: a thin wrapper that calls `twin_check.sh --kind kotlin`, not a second implementation.
- The coordinator runs it weekly and after every Flutter fix merge.
- The native owner of that module mirrors the change within the week, then updates the "last synced" column.
- After the ARB conversion, Flutter copy fixes are mirrored by hand into `:ui:words` XML. ARB tooling does not run on
  the native side.

### 5.7 Shipping during the freeze

- **Dart-only fixes** ship as `scripts/release.sh patch` against the exact release. **Native, asset or pubspec
  changes** need a new Shorebird release. Both need the owner's explicit OK (`shorebird-is-mandatory`).
- **The bridge release (D4)** is the last Flutter release. It carries the native-style installer, so Flutter users can
  update themselves into the native app.
  - The freeze line already holds `feat/app-updates-2026-10-31` (merged at `6b686bb7c`). So the bridge release is the
    freeze line, released after its gate, as a new Shorebird release, with the Gemma entry points hidden (S02-3).
  - It is signed exactly as today on both lineages, and its versionCode stays below 10000 on both:
    - **public lineage:** the next public number, for example 2.2.0+58 [E];
    - **local (owner) lineage:** above the highest versionCode in `native/BUILD_NUMBERS.md` (the owner's phone already
      runs local builds up to 4511), because Android refuses a downgrade. A bridge numbered 58 cannot install over the
      owner's phone.
- **After the switch:** a Shorebird patch to the bridge release is allowed only for a security fix that affects
  people who have not updated yet, with the owner's OK.

### 5.8 At the switch

1. Tag the last Flutter commit `flutter-final` (§5.1) and cut `flutter/maint` from it.
2. In one commit on `native/main`, remove the Flutter tree (`lib/`, `test/`, `pubspec.*`, `android/` (whose live code
   now sits in `native/`), `widgetbook/`, the Flutter-only tooling). Every catalog `sources` path stays readable at the
   tag.
3. Fast-forward `master` to `native/main` after the owner approves.

---

## 6. Definitions

### 6.1 Behaviour, row, behaviour id

- **Behaviour (catalog row):** one thing a person or a protocol can observe, written as Given/When/Then.
- **Behaviour id:** `B-<AREA>-<NNN>`.
  - AREA is one of `NAV, CHAT, AGENT, PROTO, STATE, SEC, ANDR, LINUX, MEDIA, KIT, L10N, XPLAT, BUILD, MIGR`.
  - NNN is three digits.
  - Ids are permanent: never renumbered and never reused.
- **New ids:**
  - A new row takes the next free number in its area. For example, the first top-up NAV row is B-NAV-083.
  - When a row is split, the first part keeps the id, and the other parts get new ids with `split_from`.
  - A removed row keeps its id with `scope: dropped` and a decision.
- **In test names:**
  - KMP, JVM and Robolectric tests use a backtick name that starts with the id:
    ``fun `B-STATE-018 dispatch marker blocks auto resend`() = runTest { … }``.
  - Android **instrumented** tests use underscores, because DEX before version 040 (API 30) rejects spaces in method
    names and our minSdk is 26: `fun B_STATE_018_dispatch_marker_blocks_auto_resend()`.
  - A test that proves several rows lists them all at the start of its name.
- **Ratchet regex** (used by `:quality` and by grep):
  `(?<![A-Za-z0-9])B[-_](NAV|CHAT|AGENT|PROTO|STATE|SEC|ANDR|LINUX|MEDIA|KIT|L10N|XPLAT|BUILD|MIGR)[-_](\d{3})(?![0-9])`.
- **Maestro flows** use section 14's one format (§4.2 there), which keeps e2e-flows.md's bracket form:
  - the flow header lists all its rows: `# rows: B-NAV-024, B-NAV-037`;
  - each step's first command carries its rows in brackets in its label: `label: "S6 lands on Conversations
    [B-NAV-024]"`, copied from the `[B-…]` after that step in `catalog/e2e-flows.md`.
  - Section 14's runner maps a passing step to its rows through `native/catalog/flow-rows.json` (the step map). A test
    name is not needed for a flow-proven row.

### 6.2 Flutter reference build (R)

"2.1.0+57" names three different trees, so every R and every upgrade-journey start is named by **commit or tag**:

| Name | What it is | Commit |
|---|---|---|
| Public `v2.1.0+57` | The GitHub release APK (versionCode 57, CI signer `842284B2…1C`) | tag `v2.1.0+57` = `e44b581aa` |
| batch1 | The tree the catalog and maps were taken from. Its pubspec also says 2.1.0+57, but it differs from the tag by 79 `lib/` and `android/` files (about +4.6k lines). | `65e3f70a3` |
| Freeze tip | `int/freeze-2026-11-01` with the 22 freeze merges; the owner's phone runs local-key builds of this line (4380–4511) | `fc5c1e78f` when written; the brief names the commit |
| `flutter-final` | The last Flutter commit, tagged at the switch phase (§5.1) | the tag |

- **R before `flutter-final` exists:** the local-key release build of `65e3f70a3` (batch1), the build the flows are
  first written against (e2e-flows). `docs/native/reference.md` records its commit, versionCode, APK SHA-256 and signer.
- **R from then on:** the release build of `flutter-final` (the bridge release, if D4 ships). When R changes, every flow
  is proven green on the new R before it judges the native app.
- **What R is for:**
  - parity questions ("what does the app do today?");
  - the baselines in §4.1;
  - the flows.
- **Read-only source trees:**
  - `/home/eslam/Storage/Code/wt/oc_app-batch1` (`65e3f70a3`): where the catalog's `sources` paths and line numbers
    point;
  - `/home/eslam/Storage/Code/wt/oc_app-freeze` (`int/freeze-2026-11-01`): every §3.5 feature, every twin (§5.6) and
    every top-up row;
  - later `/home/eslam/Storage/Code/wt/oc_app-flutter-final` replaces both.
- **Upgrade-journey starts** (S-3): public = the GitHub `v2.1.0+57` APK; owner lineage = the exact build on NX721J and
  the local-key build of `flutter-final` (what the phone will carry at the switch).
- Agents never edit any of these worktrees.

### 6.3 A native test of a row

Proof depends on the row's level. A row is proven for X when all of these are true:

1. **The test claims X.**
   - JVM, KMP, Robolectric and instrumented tests: the test name contains X (§6.1).
   - Maestro flows: a step whose label carries `[X]` (§6.1). The section 14 step map turns step results into row
     results.
2. **It ran on the right tier and passed on the commit being judged.**
   - `unit`, `contract`, `integration`, `screenshot` and the Robolectric half of `migration`: T1 or T2.
   - `e2e`, the instrumented half of `migration`, and any row that `scope.jsonl` marks T3-proven: T3, on the **same
     commit** as the T2 being reported. A T3 result from another commit makes the row `stale` (§6.4), never green.
3. It is not `@Ignore`d and not skipped by an assumption. A skipped test counts as red.
4. It asserts every Given/When/Then clause of the row, or of its amended text. The test-writer lead or the coordinator
   checks this once, when the test is first merged.

**Tests without a row are allowed.** Kit capacity tests (S-25), motion and gate tests, the moved Kotlin tests (§5.6)
and regression tests for native-only bugs need no row id. The naming rule (Konsist rule 12) applies only to a test
that claims a row: if a name contains an id, the id must exist in `scope.jsonl`.

| `native_level` | Native home (ADR §7.2, testing-perf §2.1) |
|---|---|
| unit, contract, integration | `commonTest` (runs as `desktopTest` on the VPS). Android-only behaviour goes in Robolectric host tests in `:android:*`. |
| migration | Robolectric for prefs and files. **Instrumented on the emulator** for Keystore-backed `flutter_secure_storage`. Plus the upgrade journey. |
| screenshot | Roborazzi on the VPS (the only golden host) |
| e2e | Maestro flows on the PC emulator, release build |

### 6.4 Row status (computed, never typed by hand)

| Status | Meaning |
|---|---|
| `todo` | `scope = switch`, and no native test or flow step claims the row |
| `red` | A test or step claims the row and failed, was skipped or was ignored in the last report |
| `stale` | A T3-proven row whose last T3 ran on a different commit from the T2 being reported. It counts as not green. |
| `green` | Every test or step that claims the row passed in the last T2, and, for T3-proven rows, in a T3 on the same commit (§6.3) |
| `deferred` | `scope` is `after-switch`, `desktop-phase` or `ios-phase` |
| `dropped` | `scope = dropped`, with a decision |

### 6.5 Parity and parity percentages

- **Row parity, area A** = green(A ∩ switch) ÷ |A ∩ switch| × 100.
- **App row parity** = green(switch) ÷ |switch| × 100. This is the headline "parity %".
- **Flow parity** = green switch flows ÷ switch flows (37 under the defaults).
- **Feature disposition coverage** = map features with a slice or a decision ÷ 700. It must be 100 % for an area
  before that area's first slice starts. Section 15 holds the feature → slice table.
- **Parity, the switch condition** = all four items of ADR §10.1: every in-scope row green, the switch flows green on
  the release build, the upgrade journey passes, and the owner phone test passes.

`:quality:catalogReport` writes, at every T2:

- `build/native-t2/<sha>/parity.json`;
- the human page `docs/native/parity.md`.

```json
{"commit":"<t2 sha>","date":"2026-11-20",
 "t3":{"commit":"<sha of the last T3 run>","date":"2026-11-19"},
 "rows":{"switch":1336,"green":512,"red":40,"stale":12,"todo":772,"deferred":208,"dropped":0},
 "areas":{"STATE":{"switch":153,"green":90,"red":3,"stale":0,"todo":60}},
 "flows":{"switch":37,"green":0},
 "kotlin_tests":{"total":674,"green":674,"deleted":0},
 "features":{"total":700,"dispositioned":700}}
```

- T2 results and T3 results are combined only when both ran on the same commit. Otherwise the T3-proven rows are
  `stale` in this report ("a run counts only for the tree it ran on", §8). Slice-done and switch-ready need a T2 and a
  T3 on the same commit, so they have no stale rows.
- **Ratchets.**
  - The T2 ratchet: the green count of T1/T2-proven rows and `kotlin_tests.green` may never go down between two T2
    runs on `native/main`, and `kotlin_tests.deleted` stays 0. T3-proven rows that are only `stale` do not lower it.
  - The T3 ratchet: the green count of T3-proven rows may never go down between two T3 runs on `native/main`
    (section 14's device ratchet, `catalogReport --t3`).
  - If either goes down, the merge that caused it is reverted or fixed before the next merge.
- **Amendment exemption:** a row whose `amended_at` in `scope.jsonl` is newer than the last T2 does not count against
  the ratchet until the merge that carries its changed test and implementation (§5.3). The report lists such rows.

### 6.6 Copy parity

- The flows find UI by visible text and accessibility labels, so every string a flow asserts is **frozen until the
  switch**.
- The native app takes these strings from the one-off ARB conversion. Icon-only controls get the same
  `contentDescription` as the Flutter semantics label.
- To change an asserted string before the switch, the owner must approve it. The flow then gets a `native-only`
  variant, and that variant may no longer be compared against Flutter.
- Strings that no flow asserts follow the normal copy rules (plain words, the target named).

### 6.7 Done

**Unit done (one agent's branch).** All of these hold:

1. The finish line in the brief is met, and the non-goal was respected.
2. Every T1/T2-proven row assigned to the unit is green at T1 on the branch head (merged with the current
   `native/main`). For an `e2e` or instrumented row, the test or flow step exists, compiles and fails for the reason the
   row states; the row turns green at the lane's next T3 on a `native/main` commit, because builders do not run T3.
3. T1 is clean: Konsist, the dependency rules, detekt on the touched modules, Roborazzi verify, and the strings drift
   check when strings changed.
4. The diff stays inside the brief's write set (`check_write_set.sh`).
5. Every new key exists in en and ar. Other languages may fall back to English (S-26).
6. Every new gesture or hidden feature has a tip registered.
7. Every icon-only control has the Flutter accessibility label.
8. Every kit part touched declares its max width and capacity.
9. The things the brief listed as obsolete are recorded in `native/contracts/<slice>.md` §Deletes.
10. Every bug fix has a test that was watched failing without the fix.
11. Any device-visible work has a QA record (§9.4). Visual work also has progress images.
12. The commits follow §9.1, there are no format-only changes to other files, and nothing was deleted that the brief
    did not name.
13. The final report (§7.14) says what is **not** proven.

A **test-writer unit** replaces items 2 and 10 with: every assigned row has a test (or flow step) that compiles,
carries the row id, runs in its tier (T1, or the lane's T3 for e2e and instrumented rows), and fails for the reason the
row describes (the failure message names the missing behaviour, not a compile or setup error).

**Slice done:**

1. Every row with `slice = N` is green at T2 on a `native/main` commit, and its T3-proven rows are green at a T3 on
   that same commit (§6.3).
2. The flows that section 15 assigns to the slice are green at T3 on the release build.
3. The T4 journeys listed for the slice are within budget.
4. The coordinator ran a preview APK on the emulator: a full screen-by-screen sweep of the screens the slice touched.
5. The owner received that APK with "what to try" lines, one-screen images and one short video.
6. A slice QA record exists.

**Switch-ready:**

1. Parity (§6.5).
2. All S-criteria in §4 are met or owner-relaxed.
3. No row is `red`, `stale` or `todo` in switch scope, and every moved Kotlin test passes.
4. Every deferral and drop names an owner decision and a per-row reason.
5. PRIVACY.md is rewritten (ADR §9.6), including the analytics tiers and the exact BASIC event table (section 16 §5).
6. The R1 rollback build is built, signed with the owner lineage and verified (05 §9.3, journey J4).
7. CMP 1.13.x is stable, or the owner OKed shipping the beta (S02-6).
8. The owner phone test has passed.

**Owner phone test.**

1. **Before installing,** the coordinator checks:
   - the R1 rollback build exists and passed J4 (switch-ready item 6), because the migration is one-way on the owner's
     real data;
   - nothing is busy: `adb shell ps -A -o PID,ETIME,%CPU,NAME` under the app's uid shows no working `claude`, `node`
     or `paseo` process. An install kills everything running in the built-in Linux (2026-10-12). If an agent is
     working, wait or ask.
2. The coordinator installs the switch candidate with `adb install -r` over the owner's daily Flutter install (local-key
   lineage). This happens only while the owner is present; that is a standing approval (`auto-install-latest-build`).
3. The owner checks the upgrade by hand with the E2E-39 Part C assertions: the Conversations list shows, with no welcome
   screen and no first-run guide, and the servers are connected with no password prompt. Then the owner runs E2E-05,
   06, 08, 25 and 26 by hand. **E2E-01 never runs on the phone:** its first step clears app data, which would wipe the
   rootfs, servers and secrets. It runs on the emulator, or on the `.native` preview package, which has its own data.
   (Follow-up: the phone-smoke row of `catalog/e2e-flows.md` still lists E2E-01; the flows lane changes it to match.)
4. The owner then uses the app daily for **2 days** (S02-4) with no blocker.
5. The coordinator diagnoses with read-only adb only and takes no screen captures. Logcat shows nothing from the app on
   this phone (`persist.log.tag=S`, `phone-logcat-silenced`), so use `dumpsys activity exit-info <pkg>`, `uiautomator`
   text dumps of the Details fold, and R8 `retrace` with the exact build's `mapping.txt`. Every owner-facing build
   stores its `mapping.txt` next to its `native/BUILD_NUMBERS.md` entry.

### 6.8 Shipping-state words (AGENTS.md productivity rule 8, native meaning)

| Word | Meaning |
|---|---|
| written | Code exists on a unit branch |
| tested | The unit's rows are green at T1 on that branch |
| merged | It is on `native/main` |
| verified | T2 is green on a `native/main` commit that contains it. Where it is device-visible, T3 also passed and a QA record exists. |
| previewed | It is in a preview-flavor APK the owner received |
| switched | It is in a `stable`-flavor native build installed over Flutter |
| released | It is in a published GitHub release |

Reports never say "done" alone; they use one of these words.

### 6.9 Work units

| Term | Meaning |
|---|---|
| Slice | One of ADR slices 0–6: skeleton and proofs, foundations, chat daily path, phone runtime and agents, surfaces/settings/schedules, project/media/integrations, migration hardening and switch |
| Unit | One agent's piece of a slice: one module, or one feature module plus its engine when the same agent owns both |
| Lane | A standing role across slices: the test-writer lane, the T3 emulator holder, the T4 performance holder |
| Contract | `native/contracts/<slice>.md`: interfaces, `XUi`/`XActions`, `Failure` categories, catalog rows, deletes (ADR §10.3) |
| Finish line | One sentence: what a person or a test can observe when the unit is done |
| Non-goal | One sentence: what the unit will not do, even if it looks related |

### 6.10 Releases and builds

| Term | Meaning |
|---|---|
| Stable flavor | The Gradle product flavor `stable` (section 03 S16): package `io.github.eslamasabry.opencode_mobile`, the one that installs over Flutter. The ADR calls it "main"; a flavor named `main` would collide with the `src/main` source set. Its tasks are `assembleStableRelease`, `analyzeStableReleaseR8Config`, `connectedStableReleaseAndroidTest`. |
| Switch | The first native `stable`-flavor release installed over Flutter |
| Switch release | Version 3.0.0, versionCode ≥ 10000 (D3), both lineages, Shorebird gone, native `AppUpdater` in |
| Switch+1 | The next release, for example the Room move (D9) |
| Bridge release | The last Flutter release, carrying the installer (D4) |
| Preview flavor | The Gradle flavor `preview`, package `io.github.eslamasabry.opencode_mobile.native`: same local key, its own data, no migration (ADR §10.1). It installs beside the Flutter app. Not to be confused with Flutter's own `.preview` variant. |

---

## 7. Working rules for agents

These rules carry the repo's AGENTS.md and the owner memory files over to the native tree. Where a rule names a Flutter
tool, its native equivalent is named next to it.

### 7.1 One unit, one agent, one worktree, one branch

- One feature per agent. The agent owns the whole unit: implementation, words in en and ar, tests, QA record, images
  and local commits.
- No Workflow-tool fan-outs for build units (`no-heavy-workflows`). Each unit gets one plain background agent. Codex
  jobs go through the detached `setsid` queue, so a Claude crash cannot kill them.
- The agent that researched a unit also builds it (`same-agent-analyzes-and-builds`). After a session crash, start
  fresh agents with short briefs over the work already on disk; do not resume huge transcripts
  (`resume-agents-fresh`).

### 7.2 Repository layout and branches (S02-1, default)

| Item | Rule |
|---|---|
| Repository | The same `oc_app` repository. GitHub releases, issues, `docs/qa`, the update-check URL and Obtainium all stay where they are. |
| Native Gradle root | `native/` at the repo root. Section 03 owns its insides. If section 03 names a different root folder, that name replaces `native/` everywhere in this section. |
| Slice contracts | `native/contracts/<slice>.md` (the ADR's `contracts/<slice>.md`). The root `contracts/` keeps the OpenAPI snapshots (`opencode2-openapi-beta-*.json`, Gas City), which both apps use. |
| Integration branch | `native/main`, created from the freeze-line tip when slice 0 starts (the freeze gate may still be running). Once a week, and after every Flutter fix merge, the coordinator merges the freeze line into it. The two trees never touch the same paths, so these merges have no conflicts. |
| Unit branches | `native/s<N>-<unit>-<YYYY-MM-DD>` from `native/main`, e.g. `native/s1-core-data-2026-11-10`. Codex units use the same prefix. |
| Flutter line | `int/freeze-2026-11-01` → `master` at owner-approved milestones, as today |
| Path ownership | Native agents write only `native/**`, `docs/native/**` and `docs/qa/native/**`. Flutter fixers never write there; they propose catalog rows in their commit trailer (§5.3), and the test-writer lead writes the rows. Exceptions go through the coordinator only. |
| At the switch | §5.8 |

### 7.3 Worktree recipe (sparse, so a native worktree does not carry the 479k-line Flutter tree)

This was checked on git 2.43.0 on this PC, 2026-11-01: the sparse setting stays per worktree, and the main checkout is
untouched.

```bash
# PC, from the main repo. Never inside the shared checkout's working files: worktree add only.
cd /home/eslam/Storage/Code/oc_app
U=s1-core-data; D=2026-11-10
git worktree add --no-checkout -b native/$U-$D /home/eslam/Storage/Code/wt/ocn-$U native/main
cd /home/eslam/Storage/Code/wt/ocn-$U
git sparse-checkout set --cone native docs/native docs/qa/native contracts
# cone mode also brings the files directly in docs/ and docs/qa/ (so docs/qa/README.md), not the 132 MB of Flutter records
git checkout native/$U-$D
# Flutter sources (read-only, §6.2): catalog sources in wt/oc_app-batch1 (65e3f70a3);
# freeze-tip features, twins and top-up rows in wt/oc_app-freeze (int/freeze-2026-11-01);
# later wt/oc_app-flutter-final
```

- **VPS copy:** `native/tool/vps_gradle.sh` rsyncs the worktree, uncommitted edits included, to `~/wt/ocn-$U` on the
  VPS. It runs the requested tasks there under a lock and pulls the reports back. It never builds an APK in a directory
  that a test run is syncing into (`shared-emulator-contention`, the 10-31 registrant trap).
- **No git on the VPS copy.** The copy has no usable `.git` (a worktree's `.git` is a file pointing into the PC's
  repository; `vps-test-env-gaps`), so `git diff` and `merge-base` fail there. Everything that needs git runs on the
  PC first: the touched-module list and `check_write_set.sh`. `vps_gradle.sh` passes the module list to the VPS as
  an argument.
- **Clean up after merge (coordinator):**
  1. `git merge-base --is-ancestor native/$U-$D native/main && git worktree remove /home/eslam/Storage/Code/wt/ocn-$U && git branch -d native/$U-$D`
  2. On the VPS: `rm -rf ~/wt/ocn-$U`, by that exact path.
  3. Check `df -h /` on both machines.

### 7.4 Single-owner modules

`native/MODULE_OWNERS` lists every **S** module from ADR §5.2, plus these shared files:

| File | Owner |
|---|---|
| `native/catalog/scope.jsonl` | Coordinator |
| `native/catalog/behaviors.jsonl` (append-only; the only place ids are handed out) | Test-writer lead |
| `native/catalog/flow-rows.json` and the flow files' `# rows:` headers | Flows lane (section 14) |
| `KnownHosts.kt` and `native/config/egress-allowlist.toml` (S-22) | `:core:security` owner (section 16) |
| `native/tool/upgrade/runtime-paths.txt` (S-4) | Migration owner (Opus) |
| `native/MODULE_OWNERS` | Coordinator |
| `native/gradle/libs.versions.toml` and `build-logic` | Build-logic owner |
| `:ui:words` `strings_common.xml` and the `:tools:strings` tooling | l10n owner |
| `ui/kit/JETPACK_AHEAD.md` | Kit owner |
| `native/TWINS.md` | Coordinator |

```text
# module-or-file            | owner role         | current agent / branch                   | since
:core:common                | S core             | codex core-1 / native/s0-core-2026-11-03 | 2026-11-03
:engine:transcript          | S chat lead        | opus chat-lead / native/s2-transcript-…  | …
:ui:kit                     | S kit owner        | sonnet kit-1 / native/s1-kit-…           | …
```

- Exactly one agent edits an S module at a time.
- Another agent who needs a change writes it under "Requests to owners" in `native/contracts/<slice>.md` and tells the
  coordinator. That agent never edits the module.
- An **F** module (one feature agent end to end) belongs to whichever unit holds it in `native/LEDGER.md`.

### 7.5 Read set and write set

- Every brief lists a **read set** (the files and docs the agent must read) and a **write set** (path globs it may
  change).
- `native/tool/check_write_set.sh <brief.md>` compares `git diff --name-only $(git merge-base native/main HEAD)` plus
  uncommitted changes against the write set. It fails on anything outside it.
- Agents run it **on the PC** (it needs git, §7.3) before their final report, and the coordinator runs it before
  merging.

### 7.6 Contracts first, failing tests first

- **Freeze before fan-out:** ADR §10.3 lists what freezes at the end of slice 0: `:core:model` types,
  `Outcome`/`Failure`, gateways and `ServerCapabilities`, store interfaces, `AppDispatchers`/`Clock`, route keys for
  slices 1–3, `KitPlatform`, and the first 20 kit part signatures. No feature unit starts before its contract is
  frozen.
- **Test-writer lane, one slice ahead:**
  - It turns rows into failing tests, reading the row and its Flutter `sources`.
  - It ports the assertion, not the Dart structure.
  - It commits them red, as `@Test` functions that fail; never as `@Ignore`.
- **Builders make red tests green.** A builder never weakens, deletes or skips an assertion.
  - If a test is wrong, the test-writer lead or the coordinator approves the change.
  - The commit body then says `Test changed because <reason>`.
- **Fix proof** (`verify-fixes-with-a-failing-test`): before claiming a fix, revert it, watch its test fail with a
  message naming the cause, then restore the fix.
- **"Pre-existing" is measured only against `native/LAST_GREEN`** (§9.3). Anything failing since then is a
  regression, and its owner fixes it.

### 7.7 Who builds what (model choice)

| Work | Agent | Notes |
|---|---|---|
| `:core:*`, `:protocol:*`, `:engine:*` units with a contract | **Codex** (gpt-6-astra, lean session) | Writes the "for the UI" part of the slice contract (`codex-backend-claude-frontend`). Quit the session as soon as it is merged. |
| Kit parts to spec, feature screens from a frozen contract, `:android:*` ports of existing Kotlin, the test-writer lane, QA write-ups | **Sonnet 5.5** | The default builder (`subagent-model-choice`, 2026-09-29) |
| `:engine:transcript`, `:android:migration`, the `BuiltinLinux` split, navigation and predictive-back spikes, premium visual screens (render → compare → fix, at least 3 passes), reviews of S-module merges, root-cause debugging, the independent visual auditor | **Opus 5.5** | ADR §10.3; `judge-visuals-hard`; `independent-visual-auditor` |
| Anything that writes code | Never Haiku | — |

If a worker stops on quota, a Sonnet agent takes over its worktree. Its brief lists the commits so far and the
uncommitted diff.

### 7.8 Machines and concurrency

| Machine | Runs | Settings and limits |
|---|---|---|
| VPS 203.0.113.10 (8 vCPU, 23 GB + 8 GB swap, 290 GB, no KVM) | T1, T2, release builds (then re-signed on the PC), Roborazzi record/verify (**the only golden host**), the headless desktop gallery, the nightly iOS klib job | `org.gradle.jvmargs=-Xmx3g` or more. One T2 at a time: `flock ~/.oc-native-t2.lock`. Before T2: `df -h /` must show ≥ 30 GB free (it filled up on 2026-11-01). The IP stays out of git: scripts read `OC_VPS_HOST`. |
| PC (8 CPU, 16 GB, one emulator) | T0, T3 (emulator, Maestro, upgrade journey), T4 (Macrobenchmark, profiles), APK re-signing, git-side checks (§7.3) | Starting values, which slice-0 proof P8 confirms or replaces when it sets the host caps: `-Xmx2g`, `org.gradle.workers.max=2`, `kotlin.compiler.execution.strategy=in-process`. **One build at a time across both apps:** native scripts take the **same build slot** that Flutter fixers use, `tool/qa/machine_lock.sh build` (the lock file `/home/eslam/Storage/tmp/oc-build.lock`), never a separate native lock. A native Gradle build and a Flutter release build running together caused the two global OOMs in `pc-capacity-ceiling`. Before any Gradle or emulator start, `free -m` must show ≥ 7 GB available. |

- **Concurrency:**
  - at most **6** builder agents with one VPS, rising to about 9–10 with a second VPS (D15);
  - at most **2** Codex sessions that run local Gradle on the PC;
  - Codex sessions start lean (`tmux-run-lean.sh`).
- **Process hygiene:**
  - Keep `hog_watch.py` armed through Monitor.
  - Stop leftovers **by exact PID** only. Never `pkill -f`, which matches the phone session's own server
    (AGENTS.md, `pgrep-self-match-wait-trap`).
  - Kill Gradle and Kotlin daemons by PID after a build when no lock holder needs them.
- **Long tasks:** anything longer than a few seconds runs in the background (`run-in-background`). Foreground chunks
  stay under 600 s (`background-task-killer`). T1 to T4 and monkey always run detached, per the §8 script contract,
  because each can exceed both the 600 s foreground cap and the roughly 50-minute tool timeout.
- **Quiet hours:** ADR §10.3 sets 00:00–02:00 as VPS-only. The owner lifted that on 2026-11-01 ("Ignore the 2am
  rule") until further notice. Briefs carry it as: "lifted; if reinstated, nothing heavy on the PC 00:00–02:00".

### 7.9 Emulator, build numbers, signers, the owner's phone

- **One emulator holder at a time:**
  1. Hold the lock with `flock /home/eslam/Storage/tmp/oc-emulator.lock`.
  2. Install and run inside one hold.
  3. Release by killing the `sleep` child, then confirm with `flock -n <lock> true` (`emulator-lock-holder`).
- **Before any install:**
  - Read the installed versionCode of the main and `.native` packages:
    `adb shell dumpsys package <pkg> | grep versionCode`.
  - Never use `adb install -d`.
  - Never `pm clear` the main package outside the upgrade-journey AVD or snapshot.
  - Restore font scale and density after a run.
- **Build numbers:**
  - Every native build of either flavor uses versionCode ≥ **10000** (D3).
  - The coordinator hands out 10-number ranges, in install order, in `native/BUILD_NUMBERS.md`.
  - An owner-facing APK is numbered above every APK already hosted (max + 10, `apk-build-numbers`).
- **Signers:**
  - Before any delivery, `apksigner verify --print-certs <apk>` must print the lineage's SHA-256: `1DE5BF08…D60C` for
    owner and emulator builds.
  - VPS-built APKs are re-signed on the PC, and every ABI split is checked.
  - The public lineage `842284B2…1C` is used only through CI, with the owner's OK. Never substitute or rotate a signer
    (D2).
- **The owner's phone (NX721J):**
  - Installs only while the owner is present (standing approval), and only after the busy check (§6.7 owner phone
    test, step 1).
  - Otherwise read-only adb: `ps`, `dumpsys`, `dumpsys activity exit-info`, `uiautomator` text dumps while our app is
    in front. Logcat shows nothing from the app there (`persist.log.tag=S`); map obfuscated frames with R8 `retrace`
    and the build's stored `mapping.txt`.
  - Never screen captures (`no-owner-phone-screenshots`).
  - Never restart or kill their live server, and never set a device setting on their behalf.
  - Basic testing belongs on the emulator (`test-on-emulator-not-phone`).

### 7.10 Visual work and what the owner sees

- **Progress images:** every visual pass saves `passN-<state>-<dark|light>.png` plus a one-line-per-pass `LOG.md`, in
  `launch-footage/progress/<slug>/` (PC) or `~/vision-assets/out/<slug>/renders/` (VPS) (`progress-images-rule`).
- **Judging visuals:**
  - The whole screen is judged side by side with the concept the owner picked. Any hesitation counts as a fail
    (`judge-visuals-hard`).
  - Row rhythm: one compact height, one state line and one trailing action per row.
- **After an app-wide look change** (styles, Fine-tune, Saver, theme), an independent auditor agent reviews every screen
  × style × light/dark, plus Saver, high contrast and RTL. The auditor never edits code.
- **Before any APK reaches the owner:** the coordinator runs it on the emulator. At slice ends, that run is a full
  screen-by-screen sweep (`stabilize-before-shipping`).
- **What the owner receives:**
  - images: one full phone screen per image, with one sentence each (`one-screen-per-image`);
  - device proofs: short videos (`adb shell screenrecord` segments of 3 minutes or less, edited with Remotion; see
    `live-video-proof`);
  - replies: a few lines plus "Decision needed: yes/no" (`short-replies-rule`). Anything reversible that takes under
    an hour is decided and done, not asked (`dont-wait-under-an-hour`).

### 7.11 Never do

1. Edit another agent's worktree, the shared checkout `/home/eslam/Storage/Code/oc_app` (worktree commands excepted),
   or the Flutter reference worktrees.
2. Run `git stash`, `git checkout -- <path>`, `git reset` or `git clean` on files you do not own
   (`no-stash-in-shared-checkout`).
3. Push to GitHub, open a PR, trigger CI, sign with a release key, tag, publish, or run a Shorebird command without a
   fresh explicit owner request. Pushing to the internal `vps` remote is allowed.
4. Commit secrets, keystores, `local.properties`, `key.properties`, the VPS IP, fixture passwords, or
   `.claude/skills/`.
5. Print or log provider credentials, the OpenCode 2 per-run password, or the VPS root password.
6. Write a commit with an author other than `Eslamasabry <104366884+Eslamasabry@users.noreply.github.com>`
   (`public-commit-email`; the repo config already sets it).
7. Run `./gradlew build` or `assemble` on multi-target modules. Use target-specific tasks (ADR §10.3).
8. Format files you did not change (`agent-format-churn`).
9. Make a body-less `wip` commit, or a squash or rebase that carries old copies of other people's files
   (`snapshot-commits-lose-features`).
10. Add `@Ignore`, `assumeTrue` or a skip to a catalog test.
11. Launch test processes as a reviewer (ADR §10.3).
12. Use `Log.*`, `println`, `HttpLoggingInterceptor`, `runCatching` around suspend calls, `Dispatchers.*` or
    `Clock.System` outside `:core:common`, `String.format`, or `material3` in a feature (ADR §6.3).

### 7.12 Draft `native/AGENTS.md` (goes into every brief)

```markdown
# AGENTS.md (native tree)

Binding: native-plan 01 (ADR) and 02 (principles). Section 03 owns module details.

## Before you start
- Your brief names: finish line, non-goal, read set, write set, contract, catalog rows, deletes, checks, evidence path, build-number range.
- Worktree: sparse, own branch native/s<N>-<unit>-<date>. Never edit outside your write set.
- Flutter sources are read-only. Catalog `sources` paths: /home/eslam/Storage/Code/wt/oc_app-batch1 (65e3f70a3). Freeze-tip features, twins (Kotlin, its tests, res, pins, scripts, ARB) and top-up rows: /home/eslam/Storage/Code/wt/oc_app-freeze (int/freeze-2026-11-01, at the commit in your brief). Later both become oc_app-flutter-final.

## Layers (the build fails if you break them)
- protocol and engine both sit on core. Engines never import protocols (gateways arrive through Metro). ui (kit, kit-chat, kit-content, words, routes, design) sits on core. Features see the kit, engines' public API and core.domain/model/common only; shell assembles features; androidApp assembles everything.
- Features import only the allowlist (ADR §6.3): no material3, no raw clickable, no Dialog/ModalBottomSheet, no raw tween/spring, no LocalContext, no java.*.
- Engines: StateFlow out, suspend + Outcome in, one serial dispatcher. ViewModels: one StateFlow<XUi>, XActions named after intents, no copy strings, no event channels.
- Platform differences: an interface bound through Metro. Composable seams only through KitPlatform inside the kit. expect/actual only in :core:common.

## Never
- Log.*, println, HttpLoggingInterceptor; runCatching around suspend calls (use attempt {}); Dispatchers.* or Clock.System outside :core:common; String.format, System., Thread. in KMP code.
- Exception or server text on screen. Failures map to words; details go only in the kit's Details fold, redacted.
- openUri / ACTION_VIEW outside ExternalLinkUriHandler. getSharedPreferences outside :core:data androidMain and :android:migration.
- A per-profile key not named oc.<what>.<profileId> or not registered with ProfileScope.
- Provider keys in logs, diagnostics, notifications, analytics events or test output.
- An analytics event with content, paths, addresses, ids or credentials; any vendor analytics or crash SDK.
- Entry points, widget actions, notifications or service starts for AI Team, Memory, Gemma or A2A (02 §3.7).

## UI rules
- Kit only; Android-native feel (ripple, predictive back, stretch, haptics through the kit); motion 150-250 ms via kit tokens, reduced motion respected; animations never below 60 fps.
- No glass (only the D10 frost on composer and chat header). Keep the composer's green running glow. No red anywhere (Signal's lamps and the markup pen excepted); destructive = confirm + strong neutral fill; amber = needs you.
- Flat lists with dividers, not boxes, pills or tiles. Per screen: one primary action, <= 7 items before scrolling, <= 3 text sizes, <= 1 supporting line per row; KitStateView for not-normal states; after 8 s say so and offer a way out.
- Plain words; actions name their target; obvious options as visible rows; no new bar or row levels; steer, never queue.
- Settings: personal choices apply everywhere; server-owned settings stay on that server's page ("By server"); no global Advanced switch.
- Every kit part declares max width + capacity. Frame text capped at 1.3x; reading text scales fully (to 2.5x).
- en and ar complete for every key (strings_<feature>.xml); es, ja, pt, ru, zh may fall back to English. Technical text LTR. Every hidden gesture registers a tip.
- Icon-only controls: contentDescription = the Flutter semantics label; touch targets >= 48 dp. Test tags only where Flutter had a ValueKey, same string.

## Tests
- A test that proves a catalog row starts its name with the id: `B-STATE-018 dispatch marker blocks auto resend` (instrumented: B_STATE_018_...). Maestro steps carry [B-...] in their label. Tests without a row (kit capacity, regressions) are fine.
- Make the red tests green; never weaken, skip or delete one. Prove a fix by watching its test fail without it.
- Moved Flutter-tree Kotlin tests travel with their source and are never dropped.

## Checks (scripts only)
- T0 while editing: native/tool/t0.sh <module> [filter] (seconds, foreground). T1 when the logical change is ready: native/tool/t1.sh, started detached; wait on its done-file. It computes touched modules and the write-set check on the PC, then runs on the VPS.
- After a failure, rerun that test only. T2/T3/T4 and monkey are the coordinator's and the lane holders'.
- One build at a time on the PC through tool/qa/machine_lock.sh build; free -m >= 7 GB available first; never ./gradlew build.
- Intended visual change: native/tool/t1.sh --record <module> (VPS only); commit the new goldens and name them in your report.

## Commits
- Subject: type(module): plain words [skip ci]. Body: what changed for the person and why.
- Trailers: Behaviors: B-..., (Change-Class: fix|feature|breaking|hotfix), Co-Authored-By, Claude-Session. Only the test-writer lead adds catalog rows or ids.
- Format only files you changed. No wip commits. No push, PR, CI, signing, tag or release.

## Report
- What now works, what remains, next action; state words (written/tested/merged/...); rows green; evidence paths; what is NOT proven.
```

### 7.13 Brief template (the coordinator fills it in; an agent must be able to start without asking)

```markdown
# Brief: native/s<N>-<unit>-<YYYY-MM-DD>
Model: <Codex|Sonnet|Opus>          Worktree: /home/eslam/Storage/Code/wt/ocn-<unit>
Finish line: <one sentence a person or a test can observe>
Non-goal: <one sentence>
Removes: <what this change takes away: controls, words, steps, code paths> (simplify rule)
Contract: native/contracts/s<N>.md §<part> (frozen <date>)
Catalog rows: green at T1: B-..., B-...; e2e/instrumented (exist, compile, fail for the stated reason): B-...   Flows touched: E2E-..
Flutter source commit: <freeze-tip sha in wt/oc_app-freeze, or flutter-final>
Read set: <files, map sections, research sections, Flutter test paths from `sources`>
Write set: <globs>   Single-owner modules you may NOT edit: <list>
Owner rules that apply word for word: <copied conditions, e.g. "chips leave only while the agent works">
Approved designs this must not replace: <list, or "none">
Checks: t0 while editing; t1 when ready; check_write_set.sh before reporting.
Device work: <none | emulator lock, build numbers <from>-<to>, steps>
Evidence: docs/qa/native/<unit>-<YYYY-MM-DD>/README.md (§9.4); images to <path> each visual pass.
Quiet hours: lifted 2026-11-01; if reinstated, nothing heavy on the PC 00:00-02:00.
Report: §7.14 format.
```

### 7.14 The agent's final report

```text
Works now: <what a person or test can do now, 1-3 lines>
State: written | tested | merged (choose) — rows green at T1: B-..., B-... (n of m)
Remains: <what is left, 1-3 lines>     Next action: <one line>
Evidence: <QA record path, T1 summary path, images/video paths>
NOT proven: <what these runs do not show>
Requests to owners / conflicts found: <or "none">
```

---

## 8. The check ladder (T0–T4)

Agents run only the scripts below. Slice 0 builds them; their owner is the coordinator together with the build-logic
owner. The exact Gradle task names inside them are confirmed by slice-0 proof P1 [U]; the scripts hide them, so this
table stays true.

| Tier | Who runs it | When | Where | Script | What it runs | Budget |
|---|---|---|---|---|---|---|
| T0 | The builder | While editing; again after a failure, on that test only | PC or VPS | `native/tool/t0.sh <module> [test filter]` | One module's `desktopTest` (common tests) or its Android host test, filtered, e.g. `./gradlew :engine:delivery:desktopTest --tests '*B-STATE-018*'` | Seconds (foreground) |
| T1 | The builder | Once when the logical change is ready, and after merging `native/main` into the branch | PC part, then VPS | `native/tool/t1.sh [--base native/main] [--record <module>]` | **On the PC** (needs git): the list of modules touched since the merge-base plus uncommitted changes, and `check_write_set.sh`. **On the VPS**, for that list: `desktopTest`, Android host tests (`testAndroidHostTest` [U]), Roborazzi verify, detekt; Konsist (`:quality:test`); `:tools:strings` drift when strings changed. `--record <module>` (VPS only) re-records that module's Roborazzi goldens after an intended visual change; the new goldens are committed and named in the report. | Minutes (target < 10 [E]) |
| T2 | The coordinator | At integration checkpoints (at least daily while merges land), and on every switch or preview candidate | VPS | `native/tool/t2.sh` | All JVM and Robolectric tests (the moved Kotlin tests included), Konsist, lint, `assembleStableRelease` and `assemblePreviewRelease`, `:androidApp:analyzeStableReleaseR8Config`, the size gate, the component freeze, Metro graph smoke, `ScopeFileTest`, `BannedDependenciesTest`, `AnalyticsPrivacyTest`, `:quality:catalogReport` | Target < 20 min (testing-perf §2.2) |
| T3 | The emulator lane holder (one at a time) | Before any APK reaches the owner; at every slice end | PC emulator | `native/tool/t3.sh <apk> [--flows <tags>] [--upgrade]` | versionCode and signer checks, install, Maestro flows on the **release** build with animations off, plus the animations-on smoke E2E-05; the instrumented classes (`connectedStableReleaseAndroidTest`), the moved Flutter-tree ones included; the S-28 accessibility sweep; the upgrade journey | 20–40 min |
| Monkey | The emulator lane holder, inside its lock hold | Slice ends (3 × 10,000 events); switch candidates (3 × 50,000) | PC emulator | `native/tool/monkey.sh <apk> --events <n>` | S-21, outside T3's budget | about 25 min at 10,000; about 2 h at 50,000 |
| T4 | The performance lane holder | Slice ends, performance-sensitive merges, switch candidates | PC emulator; NX721J only with the owner's OK | `native/tool/t4.sh [--journeys …]` | Macrobenchmark journeys, Perfetto analysis, Baseline and Startup Profile regeneration; writes JSON next to the APK | 15–30 min |

**Script contract (all of them):**

- Exit codes: `0` pass, `1` a test or gate failed, `2` the run could not happen (VPS unreachable, lock timeout, disk
  full). A `2` is never reported as a pass.
- Each script writes `build/native-t<N>/<timestamp-or-sha>/summary.txt`: commands, durations, failing tests by name,
  report paths. It also prints its last 20 lines; agents read the output through `| tail`.
- **T1 to T4 and monkey run detached:** `setsid nohup native/tool/tN.sh … &`. The script writes `summary.txt` and then
  a done-file `exit-code` holding its exit code. The caller waits on the done-file with a background wait, never a
  foreground command, because these runs exceed the 600 s foreground cap and can exceed the roughly 50-minute tool
  timeout (`background-task-killer`, `vps-test-env-gaps`). T0 stays in the foreground.
- T1 and T2 refuse to start without the matching lock and the disk/memory checks in §7.8.
- **A run counts only for the tree it ran on.** Never combine passes from different commits into one claim. A relevant
  edit after a pass invalidates that pass (AGENTS.md rule 6).

---

## 9. Commits, merges and evidence

### 9.1 Commit message

This is the repo's G33 rule (`tool/qa/check_commits.sh`) plus one trailer. Slice 0 copies the script to
`native/tool/check_commits.sh` and drops its Dart-format step; the format check for the native tree is detekt's
formatting on changed files, pinned by section 03.

```text
feat(engine-delivery): a dispatch marker stops a second automatic send [skip ci]

After a crash between send and acknowledgement the queue had no marker and
sent the prompt again. The marker is now written before the request and
checked on restart, so the person never sees the same prompt twice.

Behaviors: B-STATE-018, B-STATE-019
Change-Class: fix
Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_<id>
```

**Format rules:**

- The message contains `[skip ci]`.
- The subject is `type(scope): plain words`, at most 72 characters before `[skip ci]`. `type` is one of `feat`,
  `fix`, `test`, `refactor`, `perf`, `docs`, `build`, `chore` or `merge`, and `scope` is the module name with `:` and
  the leading layer separator replaced by `-` (`:engine:delivery` → `engine-delivery`).
- A blank line follows the subject. A non-merge commit has a body.
- The last paragraph is the trailer block:
  - `Behaviors:` is present whenever rows turn green or change;
  - `Change-Class:` is optional, and only `hotfix|fix|feature|breaking`;
  - `Co-Authored-By:` is required for agents;
  - `Claude-Session:` is an https URL.

**Content rules:**

- Test-only and docs-only commits leave out `Change-Class` (`change-class-values`).
- A changed test assertion adds the line `Test changed because <reason>` to the body.

### 9.2 Merge procedure (coordinator only)

1. Confirm that the agent's report has its T1 summary, its rows green, and a `check_write_set.sh` pass.
2. Run `git diff --stat native/main...<branch>` and read it all. List deleted files with
   `git diff --diff-filter=D --name-only native/main...<branch>`. Each deletion must be named in the brief or the
   contract's §Deletes.
3. Find files with large changes (`git diff --numstat native/main...<branch> | awk '$1+$2>80'`). Compare that list
   with the file lists of recent merges (`git diff --name-only M^1 M`), to catch old copies brought back by a rebase
   (`snapshot-commits-lose-features`).
4. Run `git merge --no-ff <branch>` into `native/main`.
5. Restore files whose only change is formatting (whitespace-stripped comparison, `agent-format-churn`).
6. If strings changed, run the `:tools:strings` drift check (the native twin of the l10n generated-merge trap).
7. Run T1 on the merged result for the touched modules. At a checkpoint, run T2. If T2 is green, update
   `native/LAST_GREEN`.
8. Update `native/LEDGER.md`, `native/MODULE_OWNERS` and `native/BUILD_NUMBERS.md`. Remove the worktree (§7.3) and
   quit the agent's Codex session.

### 9.3 Last green

`native/LAST_GREEN` holds one line per green T2 on `native/main`:
`<sha> <date> build/native-t2/<sha>/summary.txt parity=<app row parity %>`.

"It was already failing" counts only if the failure also happened at the latest line in this file
(`pre-existing-means-last-green`).

### 9.4 Evidence record (`docs/qa/native/<unit>-<YYYY-MM-DD>/README.md`)

This is the repo's existing record format (`docs/qa/README.md`), plus native fields. Each record gets one row in the
index `docs/qa/native/README.md`, which the coordinator adds at merge. `docs/qa/README.md` links to that index once.

```markdown
# <unit>: <finish line> (<YYYY-MM-DD>)
1. Scope: modules, contract part, catalog rows (B-...), flows (E2E-..).
2. Builds: branch, commit, flavor, versionCode, APK SHA-256, signer SHA-256 (apksigner output line).
3. Devices: AVD name / model, Android version and API, ABI, animations on/off, font scale, language.
4. Runs: numbered steps, expected vs actual, PASS/FAIL. Row ids per step.
5. Evidence: summary.txt paths (T1/T2/T3/T4), Maestro report, Perfetto/benchmark JSON, logcat excerpts from the
   emulator (redacted; on NX721J: exit-info and text dumps), images (one screen each), video links (Tailscale file server, not git).
6. Numbers: any §4 criterion touched, with method and value; compare with the Flutter baseline record.
7. How to reproduce: exact commands.
8. NOT proven: what these runs do not show.
```

### 9.5 Ledgers (all under the coordinator)

| File | Holds |
|---|---|
| `native/LEDGER.md` | One row per unit: branch, worktree, agent and model, status word (§6.8), rows, last T1 summary, merge commit |
| `native/MODULE_OWNERS` | §7.4 |
| `native/BUILD_NUMBERS.md` | Ranges handed out, what was installed where, the highest versionCode seen on the emulator and on the owner's phone, and the path of each owner-facing build's stored `mapping.txt` |
| `docs/native/reference.md` | The current R (§6.2): commit, versionCode, APK SHA-256 and signer; the commit of the build on NX721J used by S-3 |
| `native/TWINS.md` | §5.6 |
| `native/catalog/scope.jsonl` | §3.6 |
| `native/LAST_GREEN` | §9.3 |
| `docs/native/parity.md` | Generated at every T2 (§6.5) |
| `docs/qa/native/README.md` | The index of native QA records (one link to it from `docs/qa/README.md`) |

---

## 10. Start-up checklist (must exist before slice-1 fan-out)

| # | Item | Owner | Done when |
|---|---|---|---|
| 1 | Freeze gate passed; `flutter-final` candidate named | Coordinator | The ledger records suite, APK and emulator results; tag candidate written down |
| 2 | `native/main` created; `native/` root with AGENTS.md (§7.12), `MODULE_OWNERS`, `LEDGER.md`, `BUILD_NUMBERS.md`, `TWINS.md`, `LAST_GREEN` | Coordinator | Files committed on `native/main` |
| 3 | Scripts `t0.sh` to `t4.sh`, `monkey.sh`, `vps_gradle.sh`, `check_write_set.sh`, `twin_check.sh` (with the `kotlin-drift.sh` wrapper), `check_commits.sh` | Coordinator + build-logic owner | Each runs once against the slice-0 skeleton and writes its summary; the detached ones write their done-file |
| 4 | `native/catalog/scope.jsonl` with every row read by its text (§3.3), a `reason` on every move-out, and the §3.6 amendments; `ScopeFileTest` green | Coordinator, reviewed by the test-writer lead | T2 shows `rows.switch` and `deferred` counts |
| 5 | Catalog top-up for `65e3f70a3..<freeze tip>`, later `..flutter-final` (§3.5) | Test-writer lead | New rows **appended** to `native/catalog/behaviors.jsonl` and entered in `scope.jsonl`; coordinator review done |
| 6 | Flutter baselines (§4.1) | T4 holder | `docs/qa/native/baseline-flutter-<date>/README.md` + `baseline.json` |
| 7 | Maestro flows green on Flutter R | Flows lane (section 14) | 39 flows green, or `KNOWN-FLUTTER-GAPS` lines recorded |
| 8 | Slice-0 proofs P1 to P10 recorded, with fallbacks taken where needed | Per ADR §11 | One QA record per proof |
| 9 | Contracts frozen (ADR §10.3 list) | Coordinator | `native/contracts/s1.md` to `s3.md` marked frozen with a date |
| 10 | Owner answers or recorded defaults for D1–D18 and S02-1 to S02-8 | Coordinator | A table in `docs/native/decisions.md` with the date and the owner's words |
| 11 | The Kotlin-test baseline: every Flutter-tree Kotlin test file listed in `TWINS.md` with its `@Test` count at the freeze tip | Coordinator | `parity.json` shows `kotlin_tests.total` |
| 12 | `KnownHosts.kt` seeded from the hosts the freeze-tip code contacts on its own; `runtime-paths.txt` for S-4 | `:core:security` owner; migration owner | Both committed with a reason per entry |

---

## 11. Decisions this section adds

Work proceeds on the default until the owner answers. The coordinator adds these to ADR §12 as D19 to D26.

| # | Decision | Recommended default | If the owner says no |
|---|---|---|---|
| S02-1 | **Where the native code lives:** the same `oc_app` repo, Gradle root `native/`, integration branch `native/main` (§7.2) | Yes | A new repository. GitHub releases still have to come from `oc_app`, because the updater and Obtainium read them. |
| S02-2 | **The numbers in §4** as the switch gate, with `[E]` targets replaced by measured values | Yes | The owner names the numbers to change |
| S02-3 | **Freeze-tip delta** (§3.5): everything at the switch except the Gemma assistant and AppFunctions (after the switch), AI Team role voices (D6) and browser mode (dropped). The bridge release hides Gemma's entry points, so nobody gets it and then loses it. | Yes | Move any item between "switch" and "after the switch". Gemma at the switch means porting its Kotlin bridge as a twin plus one kit page, and the bridge keeps its entry points. |
| S02-4 | **Owner phone test length:** 2 days of daily use after the upgrade check and the five smoke flows (§6.7) | 2 days | Any other length |
| S02-5 | **Analytics** (G7; same question as 12 R12-1 and 16 D16-1): the core is built with BASIC always on and DETAILED opt-in, but **nothing is sent until the owner confirms the BASIC tier and names a destination** (Aptabase, PostHog, Plausible or self-hosted, each through our own HTTP sender). When BASIC sends, F-Droid shows the "Tracking" anti-feature; at the F-Droid submission the owner chooses between that label and a build whose BASIC sender is off. | Build the core; sender none until the owner answers | The owner names the destination now, or asks for BASIC to be off in the F-Droid build from the start |
| S02-6 | **CMP 1.13.x stable is a switch precondition** (NG23, D12). The ADR pins CMP 1.13.0-beta01 for the whole UI stack; 1.13.0 stable is projected for about mid-December 2026 [U]. | The switch waits for 1.13.x stable. If it slips more than two weeks past switch-ready, the coordinator asks the owner to OK shipping the beta. | The owner OKs shipping the beta now, with the beta version recorded in the release notes |
| S02-7 | **Saved external agents (A2A)** (NG6, B-SEC-063). A2A bearers are not profile-scoped, so the profile sweep never removes them. | The migration does not copy `oc.a2aBearer.*`, `oc.externalAgents` or `oc.a2aTasks.*` into native stores; they stay untouched until the v3 source cleanup erases them (05 §9.5, M-D5). This is recorded in `scope.jsonl`. | One visible "Remove saved external agents" row in Manage space, which erases them at once |
| S02-8 | **"While you were away"** (`oc.automaticActivity.*`): recorded today, but no screen shows it (screens-nav risk 11) | The native app stops recording at the switch. The existing keys are kept and swept with their profile. Building the page is an after-switch feature slice. | Build the page in the switch release (a new screen, so the owner picks a concept first, NG10) |

---

## 12. Glossary

| Term | Meaning |
|---|---|
| ACP | Agent Client Protocol. ACP agents reach the app through Paseo; the direct ACP client is not ported. |
| ADR | `plan/01-architecture-decision.md`, the binding architecture decision |
| AI Team | Multi-agent project orchestration (Rust phone engine or Gas City). After the switch under D6. |
| Amended row | A catalog row whose required behaviour an ADR or owner decision changed; the new text is in `scope.jsonl` (`amended`, `amended_at`); the row itself is never edited |
| Analytics core | Our own event API with BASIC (always on, anonymous, disclosed) and DETAILED (opt-in) tiers, sent by our own HTTP code over `:core:net`, no vendor SDK; nothing sent until S02-5 (section 16 §5) |
| `AppScope` / `ConnectionScope` | Metro graph scopes: the process, and one per live connection (ADR §9.1) |
| `AppUpdater` | Native in-app update: GitHub `releases/latest`, `SHA256SUMS`, same-signer check, `PackageInstaller` |
| AVD-A | The default flow emulator: Pixel 8 profile, API 36 Google APIs x86_64 (e2e-flows); parity runs use animations off (S-2) |
| Baseline Profile / Startup Profile | ART compile hints and DEX layout generated by Macrobenchmark journeys; the startup profile helps from the first launch |
| Behaviour id | `B-<AREA>-<NNN>` (§6.1) |
| Bridge release | The last Flutter release, carrying the installer (D4) |
| Built-in Linux | The Ubuntu rootfs under `files/linux/**`, run with proot inside the app. Shown to people as "This phone". |
| Catalog | `catalog/BEHAVIORS.md` + `behaviors.jsonl`: 1,544 rows from the 6,663 Flutter logic tests |
| Catalog ratchet | The count of green in-scope rows, and of green moved Kotlin tests; neither goes down on `native/main` (§6.5) |
| CMP | Compose Multiplatform (JetBrains). On Android it is a thin shim over Google's Jetpack Compose. |
| Coordinator | The lead session that writes briefs, merges, runs T2 and owns the ledgers |
| Deferred feature | A feature that follows the switch (AI Team, Memory, Gemma under D6 and S02-3): absent, never started, data kept and swept (§3.7) |
| Component freeze | The list of OS-visible names that must match the Flutter APK (ADR §9.9, §5.5) |
| Contract | `native/contracts/<slice>.md` (§6.9) |
| E2E-NN | One of the 39 Maestro device flows |
| `EntryRouter` / `EntryTarget` | The single path for shortcuts, links, shares, notification and widget taps; it emits neutral targets (ADR §9.4) |
| F-Droid-ready | Nothing from Google Play Services, Firebase, ML Kit, Shorebird, or any vendor analytics or crash SDK in the release classpath (S-23). Our own analytics core is allowed; if BASIC sends anything, F-Droid adds the "Tracking" anti-feature label (S02-5). |
| `Failure` / `Outcome` | Closed error categories, and the result type engines return (ADR §9.6) |
| FGS | Foreground service. `dataSync` is capped at 6 h per 24 h; the phone server uses `specialUse`. |
| Flow parity | Green switch flows ÷ switch flows (§6.5) |
| Frame text / reading text | App chrome text (capped at 1.3×) vs message and document text (scales fully) |
| `frameOverrunMs` | Macrobenchmark: how far a frame missed its deadline; ≤ 0 means it made it |
| Freeze line | `int/freeze-2026-11-01`, the Flutter branch during the rewrite; read-only worktree `wt/oc_app-freeze` |
| `flutter-final` | The tag on the last Flutter commit (§5.1) |
| `JETPACK_AHEAD.md` | The kit's list of Android-only Jetpack APIs used ahead of CMP (ADR §7.6) |
| KMP | Kotlin Multiplatform |
| Kit | `:ui:kit`, `:ui:kit-chat`, `:ui:kit-content`: the only source of UI components |
| `KitPlatform` | The kit's one interface for platform seams (haptics, ripple, overscroll, insets, frost…) |
| Konsist | Architecture-test library that enforces the import allowlist and code rules |
| Lane | A standing role across slices (§6.9) |
| Lineage (signer) | The certificate an installed app was signed with: local `1DE5BF08…D60C`, public `842284B2…1C`, CI test `2D010C21…D0EC` |
| Live Updates | Android 16 promoted ongoing notifications; used only for runs the person started |
| Maestro | A YAML device-flow runner that reads the accessibility tree, so the same flow drives Flutter and native |
| Macrobenchmark | `androidx.benchmark` startup and frame measurement on a real build |
| Metro | The compile-time DI framework (ADR §8) |
| `MigrationGate` / `MigrationRunner` | Holds stores and intents until the Flutter data migration finishes, and runs its idempotent steps (ADR §9.7) |
| `MODULE_OWNERS` | The one-owner-per-shared-module list (§7.4) |
| `MotionPolicy` | Full / Calm / Off, plus Saver and heat, on top of the system animator scale |
| Nav3 | Navigation 3: a back stack owned by the app, scenes, predictive back |
| NX721J | The owner's ZTE nubia phone (16 GB) |
| OC2 | OpenCode 2 server. HTTP Basic, `/api` prefix, 91-event stream, refetch after reconnect. |
| OC_API37 | The PC's API 37 emulator used for benchmarks and profiles |
| Owner phone test | §6.7 |
| Parity | ADR §10.1 conditions; row, flow and feature parity in §6.5 |
| Paseo | The daemon that drives Claude Code, Codex and ACP CLIs; inside the phone's Ubuntu or on the PC |
| Phone agents | Agent CLIs installed in the app's Ubuntu and driven through Paseo |
| Preview flavor | `…opencode_mobile.native`, installed beside the Flutter app (§6.10) |
| `ProfileScope` | Registry of per-profile keys and tables that the deletion sweep removes |
| proot | User-space chroot that runs the Ubuntu rootfs without root |
| R (reference build) | §6.2 |
| `Redactor` / `Redacted` | The one secret scrubber, and the only type allowed to carry technical details to the UI |
| RemoteViews | The Android widget rendering kept on day 1 (not Glance) |
| Roborazzi | Screenshot testing on JVM (Robolectric and desktop); the VPS is the only golden host |
| Row | A catalog behaviour (§6.1) |
| Saver | The light-or-dark choice that turns motion, live background and style effects off |
| Scope file | `native/catalog/scope.jsonl` (§3.6) |
| Shorebird | Flutter code push; ends at the switch |
| Slice | ADR slices 0–6 (§6.9) |
| Stable flavor | The Gradle flavor `stable` (section 03 S16), which the ADR calls "main" (§6.10) |
| Stale (row status) | A T3-proven row whose last T3 ran on another commit than the reported T2; not green (§6.4) |
| Status line | The one line per window that carries connection, heat, work and update states |
| Steer | Sending during a running turn reaches the agent now; it is not queued |
| Style / Fine-tune | Solid (default), Soft, Brutal, Paper, Signal; plus corners, depth, density and edges |
| Switch / switch release / switch+1 | §6.10 |
| T0–T4 | The check ladder (§8) |
| Tailscale range | `100.64.0.0/10` and `.ts.net` names; the app checks them in code, because the network security config cannot express them |
| Test-writer lane | Turns catalog rows into failing native tests one slice ahead |
| This phone | The in-app server and runtime, named this way in all copy |
| TTID / TTFD | Time to initial / full display (startup metrics) |
| Twin | A file copied from the Flutter tree into `native/`, tracked in `TWINS.md` (§5.6) |
| Unit | One agent's piece of a slice (§6.9) |
| versionCode base | 10000 and up for every native build (D3) |
| `WriterLane` | An admission flag plus a fair mutex. "Drain" means every admitted write finished. |
| `XUi` / `XActions` | A screen's one state object and its intent-named action interface |

---

## 13. Sources

- Binding: `/home/eslam/Storage/Code/wt/native-plan/plan/01-architecture-decision.md` (§1, §4, §5.2, §6.3, §7, §9,
  §10, §11, §12).
- Catalog:
  - `catalog/BEHAVIORS.md` (summary, areas, MIGR, Appendices C–E);
  - `catalog/behaviors.jsonl` (record shape; the §3.3 estimate and the §3.6 amendment ids, computed 2026-11-01);
  - `catalog/e2e-flows.md` (the rule "Flutter first", AVD matrix, phone smoke list, E2E-28/32/39).
- Maps:
  - `packages-build.md` §0 (signers, versionCode, Shorebird, distribution), §2;
  - `agents-backends.md` §0, §2 (tiers, do-not-port list);
  - `desktop-web-cross.md` §1, §9;
  - `screens-nav.md` §1 (owner UI rules);
  - the feature counts given in the plan brief.
- Research:
  - `testing-perf.md` §0, §2, §9 (budgets, journeys, Perfetto), §10 (APK size), §11;
  - `rendering-chat.md` §7.3;
  - `motion-native.md` §9 (120 Hz budget);
  - `kmp-cmp-status.md` §0, §12.5;
  - `architecture-stacks.md` §11;
  - `android-surfaces.md` (Memory Limiter; owner phone OS check).
- Repo, read-only:
  - `oc_app-batch1`: AGENTS.md, CONTRIBUTING.md, `docs/qa/README.md` (record format), `docs/release/change-classes.md`,
    `tool/qa/check_commits.sh` (G33), `contracts/`;
  - `wt/oc_app-freeze`: `docs/verification/branch-ledger-2026-11-01.md`, `docs/qa/perf-native-2026-11-01/README.md`,
    `git diff 65e3f70a3..fc5c1e78f` counts, `lib/api2/reconnect_backoff.dart`, `lib/l10n/app_*.arb` key counts,
    `lib/local_llm/local_llm_controller.dart` (`local_llm_models`), `tool/qa/machine_lock.sh` (build slot), the
    hosts in `lib/` and `android/app/src/main`, `android/app/src/{test,androidTest}` counts, tag `v2.1.0+57` =
    `e44b581aa` and `git diff --shortstat e44b581aa 65e3f70a3 -- lib android`;
  - plan sections 03 (S9, S16), 05 (Appendices B–D, §9.3, §9.5, §10.4), 11 (§3, §11.2, D11-3), 12 (X-04, R12-1),
    14 (§2.9, §4.2), 15 (C15-9, S4-ANALYTICS) and 16 (N16-D7, N16-D9, §2.5, §5);
  - `Storage2/wt/oc_app-bgcpu`: commit `4ae686c5b` (background CPU 56 % → 7 %);
  - `Storage2/wt/perf-audit-2026-11-01/REPORT.md` (frames, memory, radio, hidden timers, reconnect).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `no-users-yet`,
  `audit-trail-rule`, `owner-tests-i-gate`, `test-on-emulator-not-phone`, `stabilize-before-shipping`,
  `pc-capacity-ceiling`, `build-vps`, `no-heavy-workflows`, `codex-backend-claude-frontend`, `subagent-model-choice`,
  `same-agent-analyzes-and-builds`, `resume-agents-fresh`, `apk-build-numbers`, `shared-emulator-contention`,
  `emulator-lock-holder`, `pre-existing-means-last-green`, `snapshot-commits-lose-features`, `agent-format-churn`,
  `no-stash-in-shared-checkout`, `run-in-background`, `hog-watch-monitor`, `quiet-hours-vps-only`,
  `public-commit-email`, `verify-fixes-with-a-failing-test`, `android-native-feel`, `micro-animations-wanted`,
  `no-glass-rule`, `simplify-and-natural`, `no-new-ui-levels`, `actions-name-their-target`, `obvious-options-first`,
  `steer-not-queue`, `component-max-width-rule`, `text-scale-cap`, `teach-what-is-hidden`, `no-raw-errors-rule`,
  `judge-visuals-hard`, `independent-visual-auditor`, `progress-images-rule`, `one-screen-per-image`,
  `live-video-proof`, `short-replies-rule`, `dont-wait-under-an-hour`, `owner-phone-debug-allowed`,
  `phantom-process-limit`, `shorebird-is-mandatory`, `app-updates-two-layers`, `auto-install-latest-build`,
  `phone-logcat-silenced`, `vps-test-env-gaps`, `background-task-killer`, `design-standard-rule`,
  `composer-green-glow-liked`, `settings-scope-model`, `night-2026-10-16-state` (no red), `roadmap-issues-2026-10-31`
  (#37, #42 closed), `agents-brief-2026-10-30` (Claude Agent terms), `gemma-assistant`, `role-cycle-ai-team`.
- Checked on this machine, 2026-11-01: per-worktree sparse checkout with `git worktree add --no-checkout` +
  `git sparse-checkout set --cone` on git 2.43.0 (main checkout untouched).
- The owner's words relayed for this run: "7400 for widgets can come later."

---

## Review notes (2026-11-01 revision)

Every issue was checked against `catalog/behaviors.jsonl`, the `oc_app-freeze` and `oc_app-batch1` worktrees, the owner
memory and sections 01, 03, 05, 11, 12, 14, 15 and 16 before editing. All 37 point at a real gap. Where part of an
issue's claim or fix was wrong or already handled elsewhere, the adjustment is listed under "Rejected or adjusted".

**Changed**

1. **Analytics** (both blocker entries): G7, §1.1 row 17, NG15, S-22, S-23, G8 and the glossary now follow the owner's
   reversal: our own core and senders, BASIC always on, DETAILED opt-in, no content, paths, addresses or credentials.
   New S-29 and decision S02-5 (nothing sent until the owner confirms; the F-Droid "Tracking" label is decided at
   submission). Canary covers analytics payloads.
2. **Scoping** (two blocker entries plus the deferred-data blocker): §3.3 now scopes by row text with tightened
   first-pass patterns, per-row `reason`, "when in doubt it stays", and a data-protection rule. 42 rows are listed as
   switch on day 1. The table was recomputed (208 out, about 1,336 in) and the §3.4 count changed (16 `portable_e2e`
   leave, about 224 stay). New §3.7 covers deferred features: absent, never started, data kept and swept, one notice,
   and the `ProfileScope` shapes.
3. **OC1 → OC2 history**: B-LINUX-077, 107, 110, 194, 197, 230, 241 and B-MIGR-002 are no longer amended. NG1 and the
   §3.2 protocols row keep the history import and both profile flags.
4. **Source trees and versions**: §3.5, §5.5, §5.6, §6.2, §7.3 and the AGENTS.md draft now point at `oc_app-freeze`.
   §6.2 adds a table that tells apart public `v2.1.0+57` (`e44b581aa`), batch1 (`65e3f70a3`), the freeze tip and
   `flutter-final`. Twins are copied from the freeze tip. The component freeze is the union list.
5. **Upgrade journey**: S-3 is split into an owner leg (each migration T3, starting from the exact NX721J build) and
   a public leg (once per candidate, through CI with the owner's OK). In §5.7 the local-lineage bridge build is
   numbered above `BUILD_NUMBERS.md`. S-4 now has protected and runtime sets with set timing, and covers
   `local_llm_models/`, the stash and photo vaults, and the other untouched paths.
6. **Catalog editing**: the copy is append-only and the test-writer lead is the only one who hands out ids. Flutter
   commits propose rows in trailers. Amendments go to `scope.jsonl` with `amended_at`, and the ratchet exempts them.
7. **Parity proof per level** (§6.1, §6.3 to §6.5, §6.7): Maestro rows are proven through step labels `[B-…]` and the
   section 14 step map. A new `stale` status covers T3 results from another commit, and the ratchet is split into a
   T2 and a T3 ratchet. e2e and instrumented rows have their own unit-done rule. Tests without a row are allowed. A
   Kotlin-test ratchet was added (§3.1, §5.6, S-1).
8. **Words**: S-26 requires en and ar complete. The other five languages may fall back to English, and the drift
   check gates only format arguments and plurals.
9. **Checks**: monkey is now a separate detached job (10,000 events at slice ends, 50,000 for switch candidates).
   T1 to T4 run detached with a done-file. Git-side checks run on the PC. `t1.sh --record` was added. Native builds
   use machine_lock's build slot, and the PC values are starting values for P8.
10. **Names**: the `stable` flavor and its tasks, the single tag `flutter-final`, and `twin_check.sh` as the one drift
    tool with `kotlin-drift.sh` as its wrapper.
11. **Owner rules and scope**: §1.1 rows 19 to 24 were added (no red, flat lists, the screen standard, the composer
    glow, the settings scope model, 60 fps), plus S-28 accessibility (2.5×, 320/360 dp, 48 dp, live regions) and
    S-25 widths and scales. S-18 now matches `ReconnectBackoff`. S-5, S-9 and S-13 are tagged [E]. Also added: the
    pins row, Claude Agent terms, Gemma handling (bridge hides it; S02-3), and NG20 and NG25 (#42 and #37 closed).
    §3.2 now lists MCP, quota collector, BYO host, setup assistant, isolated launch, packages, Reset and Git, plus
    S02-8 for "While you were away".
12. **Owner phone test**: a busy check and the R1 build come first. E2E-39 Part C replaces E2E-01. Diagnostics use
    exit-info, text dumps and retrace. Each owner build keeps its `mapping.txt`.
13. **New decisions**: S02-6 (CMP 1.13 stable as a switch precondition, with a named fallback) and S02-7 (A2A data).

**Rejected or adjusted, and why**

- *Analytics, second entry:* a Firebase sender and narrowing S-23 to "Google, Firebase and crash-upload only" were
  **not** adopted. Registry X-04 and section 16 N16-D9 already settle on our own HTTP senders with every vendor SDK
  banned. A Firebase sender needs Google's SDK, so it fits only a separate non-F-Droid flavor, by owner decision
  (NG15).
- *No-red claim:* "nothing would enforce no-red" is wrong. B-KIT-056 and section 06 K21 (`NoRedScanTest`) exist, and
  raw-07:96 dropped only the allowlist-freshness meta-test. The rule rows were still added.
- *Glass trial "in NG14":* NG14 is live backgrounds, so the trial became a new NG24 instead.
- *Retired widgets:* this needs no new decision, because section 11 D11-3 already defaults to "keep them working".
  02 now references it, and the upgrade journey seeds one retired widget.
- *"Every t\*.sh detached":* T0 takes seconds, so it stays in the foreground.
- *Kotlin tests:* a `K-<class>-<method>` id space was not added. The `kotlin_tests` ratchet in `parity.json` is
  enough, and section 14 §2.9 already adds row ids where a Kotlin test proves a row.
- *A2A "erase at migration":* not the default. ADR §9.7 and §9.8 and B-MIGR-010 forbid deleting source data in the
  switch release, so S02-7 follows section 05 (untouched until the v3 cleanup), with the visible row as the
  alternative.

**Follow-ups for other files (not edited here):** section 14 `FlowRowsTest` "219 in switch scope" becomes about 224;
`catalog/e2e-flows.md` phone-smoke row (drop E2E-01) and AVD-A "animations on"; section 05 Appendix C and ADR §9.7
never-moved list gain `local_llm_models/` (and `prompt-stash-attachments-v1/` in the ADR list); ADR §9.6 "Nothing
phones home" per 16 CR16-1; ADR §5.2 `:tools:strings` wording per S-26; ADR D12 vs the §8 CMP beta pin per S02-6;
sections 15 and 16 disagree on the analytics module (`:engine:analytics` vs `:engine:diagnostics`).

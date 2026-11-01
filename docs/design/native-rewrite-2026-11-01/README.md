# OpenCode Mobile: the native rewrite plan

Written 2026-11-01. This is the front page. Start here, then follow the links. When this page and a section disagree,
the section wins, and `plan/01-architecture-decision.md` (the ADR) beats every other section.

Status (2026-11-01, evening): the Codex Sol audits of 01/03/04/05 (7 blockers, 20 majors) and the full Codex Astra
review (NO-GO, 13 findings) have all been applied to the text; each section ends with an audit follow-up table. The ADR
(01) was amended to match, so it is again the single authoritative revision. Gate G-A needs a re-check of the Astra
findings before more branches merge. **Building has started:** since 2026-11-01 up to 17 cloud sessions build slice-0
and slice-1 units; the slice-0 proofs P1, P2, P3, P5, P6 and P7 are on `native/m0-2026-11-01` (host and desktop tests
only, nothing run on an emulator or phone yet), and their findings are folded back into 01, 03, 05, 06, 07, 08 and 09.
Flutter feature work is frozen; it gets bug and security fixes only.

---

## 1. In plain words (for the owner)

**What we build.** The whole app again, in Kotlin with Jetpack Compose, the technology behind the demo you said felt
"so so so much smoother". Same app, same features, same name on your phone. Android comes first and gets the most care.
The code is written so a Linux desktop app and an iPhone app can follow later without a second rewrite.

**Why.** Smoothness and the Android feel. The demo was a 2.5 MB app that opened in about 0.25 s; today's app opens in
about 0.4 s. Compared fairly (the download a modern phone gets, the arm64 file, both release builds), today's app is
about 36 MB and the new one aims for about 18–20 MB (gate 22 MB). About 14 MB of that saving comes from the rewrite
itself, and about 3 MB is AI Team's engine, which comes back after the switch (`plan/12-package-registry.md` §11). The
new app also aims for about 0.3 s to open, no dropped frames while a reply streams, and almost no battery use while
hidden.

**How we make sure nothing is lost.** Your 6,663 Flutter logic tests became a list of 1,544 behaviours. Each one gets
a native test before the feature counts as done. 37 phone walk-throughs (from 39 written) must pass on today's app first,
then on the new one. The 7,967 screen (widget) tests come later, as you asked. We ask you about them at the switch, or
earlier if bugs show they are needed.

**How long.** About 160 agent-weeks of building plus about 60 agent-weeks of testing and support work. Dates follow what
we measure in the first two weeks:

- One build server only: the switch reaches your phone in **mid-June 2027**.
- With cloud agents added: **mid-May 2027** (planned with 3 sessions; up to 17 run since 2026-11-01, but the dates move only after the M0 re-plan measures merged, tested work, because the one emulator and reviews are now the limit).
- With a second build server: **mid-April 2027**.

**What you will see.**

- A video in mid-November.
- From mid-December, a separate "preview" app next to today's app at the end of each step. It has its own data and does not touch your real app.
- The first preview you can chat in arrives early February (mid-January with more capacity).
- Short weekly pictures or videos.
- Every APK passes the emulator before it reaches you.

**What stays the same for users.**

- The new app installs over the old one: same package, same signing key.
- On first start it moves everything across: your servers, passwords, chats, drafts, queued prompts, schedules, placed widgets, tiles, shortcuts, notification choices, voice models and the phone's own Linux. Nobody has to set anything up again.
- One last Flutter update (the "bridge") goes out first, so its Update button can deliver the new app later.
- The release goes out in stages: your phone for two days, then a GitHub pre-release for a week, then everyone.
- A ready "go back" build waits until you say the new app is fine.

**What changes.**

- Shorebird quick patches end at the switch. The in-app update notice stays.
- Three things arrive after the switch rather than in it: **AI Team, Memory (FactsBook) and the on-phone assistant (Gemma)**. Their data stays on the phone, and the app says once what moved.
- Phones that run only 32-bit apps stay on the last Flutter version (3 of 29 downloads of 2.1.0+57). The app tells them so plainly.

---

## 2. The decision

We build **one Kotlin Multiplatform codebase with shared Compose Multiplatform UI** (Proposal A), with the best ideas of
the Android-first Proposal C grafted on. The logic, the kit, the screens and navigation live in common code. On Android
that common UI *is* Google's own Jetpack Compose (the JetBrains artifacts are 6 KB shims, verified), so nothing is lost
in smoothness or native feel. Newer Android-only APIs live inside the kit and the shell, behind kit APIs.

Everything only Android can do lives in plain Android modules behind interfaces: built-in Linux, widgets (RemoteViews
kept), notifications, foreground services, tiles, voice, camera, PDF, the updater and the Flutter migration. Every
module also compiles for desktop from day one, and a nightly job compiles the common code for iOS. Desktop and iOS
become products only in their own later phases.

The main pieces:

- Metro for wiring
- plain `ViewModel` + `StateFlow`
- Navigation 3
- Ktor over a hardened OkHttp
- a hand-written OpenCode 2 client
- our own Keystore secret store
- Roborazzi screenshots
- Maestro device flows

The in-place switch ships only at **parity**: every in-scope behaviour row green, the parity flows green on the release
build, the upgrade journey passing, and your two-day phone test passing.

A won the judges' vote 139 to 138.5. Decision D1 below gives you one clean chance to switch to C before slice 1, at
almost no cost.

---

## 3. Contents

### The plan (`plan/`)

| File | One line |
|---|---|
| [01-architecture-decision.md](plan/01-architecture-decision.md) | The binding ADR: why A, rejected options, module graph, dependency rules, library pins, slice-0 proofs, owner decisions D1–D18 |
| [02-principles-scope.md](plan/02-principles-scope.md) | Goals, non-goals, what is in scope at the switch, success numbers, Flutter freeze rules, definitions, agent working rules |
| [03-architecture-modules.md](plan/03-architecture-modules.md) | Every module, its public interfaces, ownership, concurrency and error rules, Gradle conventions |
| [04-protocols-networking.md](plan/04-protocols-networking.md) | OpenCode 2 client, Paseo/agents wire, SSE and reconnect, never-resend rules, Tailscale address checks, offline queue |
| [05-data-migration.md](plan/05-data-migration.md) | First-launch migration of prefs, secure storage, files and the Linux rootfs; the go-back build; upgrade journeys J1–J8 |
| [06-ui-kit-design-system.md](plan/06-ui-kit-design-system.md) | The one kit: tokens, parts, styles, accessibility, RTL/Arabic, strings from ARB, max widths and text-scale cap |
| [07-screens-navigation.md](plan/07-screens-navigation.md) | Every screen and route, deep links and intents, adaptive layouts, build order |
| [08-motion-animation.md](plan/08-motion-animation.md) | Motion system, predictive back with the row-to-chat transform, haptics, overscroll, reduced motion |
| [09-chat-streaming-genui.md](plan/09-chat-streaming-genui.md) | Transcript engine, smooth streaming, composer, steering, permissions and questions, review/revert, agent cards |
| [10-media-voice.md](plan/10-media-voice.md) | Voice typing, read aloud, speech models, camera and QR (Google-free), files, viewers, Wi-Fi share |
| [11-android-platform-native.md](plan/11-android-platform-native.md) | Services, notifications, reply watch, restore after reboot/update, widgets, tiles, schedules, built-in Linux runtime |
| [12-package-registry.md](plan/12-package-registry.md) | Every Flutter package and its native replacement, licences, sizes and the APK size budget |
| [13-performance-optimization.md](plan/13-performance-optimization.md) | Start-up, frame, memory, battery and size budgets; baseline profiles; measuring on the emulator and phone |
| [14-testing-parity.md](plan/14-testing-parity.md) | Test tiers T0–T4, turning catalog rows into tests, Maestro parity flows, emulator set, flake rules, widget-test phase |
| [15-delivery-roadmap.md](plan/15-delivery-roadmap.md) | Milestones M0–M10, units per slice, three calendars, switch-over and rollout, risk register, decision calendar |
| [16-security-privacy-release.md](plan/16-security-privacy-release.md) | Security invariants, analytics core (Basic/Detailed), signing and key custody, releases, F-Droid, developer verification |
| [17-gap-closure.md](plan/17-gap-closure.md) | Designs no section owned: agents engine, project/Git/terminal engine, integrations engine, ownerless STATE rows |
| [18-analytics.md](plan/18-analytics.md) | The analytics core: Basic (always on, anonymous) and Detailed (opt-in) tiers, rotating install id, local queue, no mobile data in the background, pluggable sinks, "What we collect" page |

### The maps of today's app (`map/`, read from `oc_app-batch1` at `65e3f70a3`)

| File | One line |
|---|---|
| [agents-backends.md](map/agents-backends.md) | Paseo, Codex, ACP, phone agents host, AI Team, agent auth and permissions |
| [android-platform.md](map/android-platform.md) | Services, notifications, widgets, schedules, launcher surfaces, device bridges |
| [builtin-linux.md](map/builtin-linux.md) | The phone's Ubuntu, servers, setup and process safety |
| [chat-genui.md](map/chat-genui.md) | Chat screen, transcript, streaming, composer, requests, review/revert, generative UI |
| [desktop-web-cross.md](map/desktop-web-cross.md) | Desktop/web paths, accessibility, security invariants, privacy, diagnostics, crashes |
| [media-voice.md](map/media-voice.md) | Media, voice, camera, files and sharing |
| [packages-build.md](map/packages-build.md) | pubspec, Gradle, assets, l10n, CI, releases, Shorebird, APK contents |
| [protocols.md](map/protocols.md) | OpenCode 1/2 clients, domain gateway, contracts, reconnect/refetch |
| [screens-nav.md](map/screens-nav.md) | Screens, navigation, deep links, adaptive layout, shortcuts |
| [state-data.md](map/state-data.md) | Connection controller, profiles, secrets, caches and stores |
| [ui-kit.md](map/ui-kit.md) | Kit, theming, styles, motion and tokens |

### Research (`research/`, web sources read 2026-11-01)

| File | One line |
|---|---|
| [android-surfaces.md](research/android-surfaces.md) | Android 16/17 platform surfaces: widgets, tiles, services, notifications, limits |
| [architecture-stacks.md](research/architecture-stacks.md) | DI, state, navigation, errors, concurrency and logging choices for KMP + Compose |
| [genui.md](research/genui.md) | Generative UI options: agent cards, A2UI, Remote Compose, MCP Apps |
| [kmp-cmp-status.md](research/kmp-cmp-status.md) | Kotlin Multiplatform and Compose Multiplatform maturity, Material 3, Gradle/AGP 9 |
| [media.md](research/media.md) | Speech, audio, camera, QR, PDF and image stacks |
| [motion-native.md](research/motion-native.md) | Material motion, predictive back, shared elements, IME, overscroll, haptics |
| [network-data.md](research/network-data.md) | Networking and persistence for KMP |
| [rendering-chat.md](research/rendering-chat.md) | Markdown, code, diffs, long transcripts and smooth streaming in Compose |
| [testing-perf.md](research/testing-perf.md) | Logic, UI, screenshot, E2E and performance tooling |

### Proposals and audits

| File | One line |
|---|---|
| [design/proposal-A-shared-ui.md](design/proposal-A-shared-ui.md) | One KMP codebase with shared Compose UI (chosen) |
| [design/proposal-B-native-ui-per-platform.md](design/proposal-B-native-ui-per-platform.md) | Shared core, native UI per platform (rejected) |
| [design/proposal-C-android-first-kmp-ready.md](design/proposal-C-android-first-kmp-ready.md) | Android-first Jetpack UI, KMP below it (rejected by 0.5 points, heavily grafted) |
| [audit/codex-01-architecture-decision+03-architecture-modules.md](audit/codex-01-architecture-decision+03-architecture-modules.md) | First Codex audit of 01 and 03 (BLOCKED; fixes in the sections' review notes) |
| [audit/codex-04-protocols-networking+05-data-migration.md](audit/codex-04-protocols-networking+05-data-migration.md) | First Codex audit of 04 and 05 (BLOCKED; fixes in the sections' review notes) |
| [audit/astra-full-review.md](audit/astra-full-review.md) | Full Codex Astra review of the plan (NO-GO, 13 findings; each fixed in its section) |

### The behaviour catalog (`catalog/`)

| File | One line |
|---|---|
| [BEHAVIORS.md](catalog/BEHAVIORS.md) | 1,544 behaviours from the 6,663 Flutter logic tests, by area and test level; 240 proven by device flows |
| [behaviors.jsonl](catalog/behaviors.jsonl) | Machine-readable twin (`id, area, behavior, given_when_then, native_level, portable_e2e, sources`) |
| [e2e-flows.md](catalog/e2e-flows.md) | 39 Maestro device flows that prove the 240 device-flow rows on both apps |

---

## 4. Roadmap at a glance

Work starts Monday 2026-11-02 (W1). **A** = one build VPS (the plan of record). **B** = a second VPS from W3. **C** = A plus three cloud sessions from W3. The first re-plan, at M0, replaces these estimates with measured speed.

| When (A) | B | C | Milestone | You get |
|---|---|---|---|---|
| Nov 2–3 | | | Gate G-A: full Codex Astra review of the plan, fixes | — |
| Nov 15 (W2) | same | same | **M0 foundations**: skeleton builds, ten slice-0 proofs judged, slice 1–3 contracts frozen, parity flows run on Flutter | video of predictive back and streaming; re-planned dates |
| Nov 16–22 (W3) | same | same | **Bridge release**: last Flutter release, carries the native-ready installer | an update to today's app |
| Dec 14–20 (W7) | W7 | W7 | **M1 foundations**: data, network, OpenCode 2 client, connection engine, kit core, strings, shell | **P0 "kit tour"** preview |
| Feb 1–7 2027 (W14) | Jan 11–17 | Jan 11–17 | **M2 chat daily path**: add server, chat, stream, stop, steer, permissions, offline without double sends | **P1 "chat with your server"** |
| Mar 8–14 (W19) | Feb 8–14 | Feb 22–28 | **M3 phone and agents**: built-in Linux, phone server, Claude Code via Paseo, full composer, attachments, QR pairing | **P2 "phone + agents"** |
| Apr 12–18 (W24) | Mar 1–7 | Mar 15–21 | **M4 surfaces**: notifications, reply watch, restore after reboot/update, tiles, widgets, schedules, settings, five styles, updater | **P3 "around the phone"** |
| May 31–Jun 6 (W31) | Mar 29–Apr 4 | Apr 26–May 2 | **M5 everything else**: files, Git, terminals, voice, read aloud, viewers, Wi-Fi share, Codex, cards, integrations | **P4 feature-complete preview** |
| Jun 14–20 (W33) | Apr 12–18 | May 10–16 | **M6 switch**: upgrade journeys, performance, audit; parity met; your phone first (2 days) | the real app, updated in place |
| Jun 21–Jul 4 (W34–35) | Apr 19–May 2 | May 17–30 | Public: GitHub pre-release (1 week), then "latest" | everyone's Update button offers it |
| ~Aug 2027 | | | **M7 clean-up**: Room move, targetSdk 37, Flutter tree removed | a normal update |
| ~Aug–Sep 2027 | | | **M8 AI Team + Memory** (if D6 = start right after the public release) | AI Team back |
| 6–10 agent-weeks after start | | | **M9 Linux desktop** replaces the frozen Flutter Linux build | desktop app |
| 12–18 agent-weeks once funded | | | **M10 iOS** (needs a Mac, an Apple account and a push relay) | iPhone app |
| at the switch, or earlier on a trigger | | | **MW widget tests** (the 7,967 screen tests) | — |

What decides the date: the chain from skeleton → network → OpenCode 2 client → transcript → chat, and from Linux runtime → reply watch → restore → migration hardening. Each 27 agent-days added or removed moves calendar A by about a week. No amount of agents gets below about W15 without cutting scope.

---

## 5. Decisions the owner must make

Work proceeds on the recommended answer until you reply. Questions arrive one or two at a time in the weekly message, each just before the work it shapes (`plan/15-delivery-roadmap.md` §17.1).

**Already settled, not asked again** (memory `freeze-owner-decisions-2026-11-01`):

- The analytics core is built from day one (section 18): Basic always on (anonymous: app and Android version, phone
  class, crash and freeze kinds, start-up time, daily use counts, server and agent kinds, error kinds, a random id that
  changes every 30 days), Detailed opt-in, listed on a "What we collect" page, never on mobile data in the background,
  and able to send to Aptabase, PostHog, Firebase, Plausible or our own server later. Nothing is sent until you pick one.
- The QR decoder is Google-free (no ML Kit), ready for F-Droid.
- F-Droid comes after the switch; whether Basic counting stays on there is decided at that submission.
- A native glass look is tried after the switch, as an optional style (real blur plus refraction, top bar and sheets
  only). You judge it on your phone; it ships only if you like it. Until then the only blur is the composer and chat
  header frost.
- Widgets are redone in the native app.
- No agent browser, and no AI Team merge undo.
- The widget tests come later.

### Week 1

| # | Question | Recommended |
|---|---|---|
| D15 | More capacity: a second build server (~mid-April), or a test machine for a second emulator? Cloud agents are already running (up to 17 since 2026-11-01). | A second server only if M0 shows merging or emulator time is the limit. Cloud sessions do not move the dates by themselves: review, merging and the one emulator do (`plan/15-delivery-roadmap.md` §2.1.1). The date always follows parity. |
| D1 | Do you want iOS or Linux desktop within about a year? (That keeps one shared UI.) | Yes: keep A. If "probably never", switch to C now, almost free before slice 1. |
| D6 | AI Team and Memory leave your app at the switch. When do they return? (a) after the clean-up release, ~Sep–Nov 2027; (b) right after the public release, ~Aug–Sep; (c) keep AI Team in the switch, which moves the switch 2–3 weeks later. | (b) |
| S02-3 | What follows the switch rather than ships in it? | The Gemma assistant and AppFunctions follow it. Browser mode is dropped. The bridge release hides Gemma, so nobody gets it and then loses it. |
| D16-9 | Key custody: two offline backups of each signing key and its passwords? | Yes (your action). Also copy the local key out of `~/.android`, and put releases behind a GitHub environment that you approve. |
| T14-1…6 | Test rules: red tests stay off `native/main`; the emulator set; one flake rerun; no retries for the candidate. Also delete six stale emulators to free about 40 GB. | Yes to all |
| S02-1 / D03-1 | Native code lives in `native/` inside the `oc_app` repo? | Yes |

### Week 2

| # | Question | Recommended |
|---|---|---|
| D4 / D15-2 | Publish the bridge release right after the freeze gate (W3)? | Yes |
| 32-bit | The native app is 64-bit only. | Drop 32-bit. Those phones stay on 2.x and are told plainly. |
| M-D2 | Add a counts-only "migration check" to the bridge, so we see the real data shape before the switch? | Yes |
| D18 / D16-7 | Register the package and both signers for Android developer verification? | Yes, with the full account ($25), right after the bridge ships |
| ADR-D16 | In-flight Flutter branches (role voices, process budget, AppFunctions, …): land each before the freeze, or port it from its branch? | Decide each branch at the freeze gate |
| D15-9 | A tiny patch to 2.1.0+57 that only says "a new version is ready" with a link? | Yes, once the bridge is published |
| (inform) | The rest of the batch, each on its recommended answer (list below this table) | As stated |

The "inform" batch:

- Keep both signing lineages and never rotate (D2).
- versionCode 10000 and version name 3.0.0 (D3).
- Drop "Press back again to exit", so predictive back works (D5).
- Termux: move-out only (D7).
- No web, no Windows (D17).
- The success numbers in 02 §4 (S02-2).
- A two-day phone test (S02-4).
- Saved A2A agents are not carried over (S02-7).
- "While you were away" stops recording until it has a page (S02-8).
- Previews only at M1–M5 (D15-5).
- The switch date follows parity (D15-1).

### Week 3 onward (before the work each shapes)

| # | Question | Recommended |
|---|---|---|
| D16-1 | Where do the anonymous Basic counts go? | Aptabase EU. Nothing is sent until you answer. |
| D9 | When does storage move to a database (Room)? | After the switch, unless the slice-0 measurement says it must come earlier |
| N4-O1…O6 | Retire the direct-ACP and OpenCode-1-only rows. Pin OpenCode 2.1.1. Retire session-address links. Fix the dispatch receipt only in native. Make three small transport changes. | Yes to all |
| 06 Q1–Q11 | Kit questions (list below this table) | As listed |
| D10 | Frost on the composer and chat header (API 31+) | Keep |
| D8-1…5 | Motion: keep today's vibrations; keyboard drag gesture off; ticks as today; keep the portal pull-to-refresh; keep Android's own page slide | As listed |
| O-07-1…6 | Screens: keep the rail visible on wide screens; move Pin to the conversation's ⋯ menu; open files as a page; share-in and Direct Share after the switch; one Agents page; connection as the first step of Add plan | As listed |
| P13-D3 / D4 | Make release builds profileable and add an off-by-default "record a performance trace" switch? Two 30-minute measuring sessions on your phone with a separate test copy? | Yes to both |
| D8 | Phone agents through Paseo, or OpenCode 2's built-in Claude Agent provider? | Paseo; revisit after the switch |
| Q09-1…4 | Chat questions (list below this table) | (a) for all four |
| D15-4 | A second Linux inside the preview app on your phone? | Only with "Disable child process restrictions" on, or while the Flutter phone server and agents are stopped, and with ≥ 10 GB free. Otherwise videos. |
| D16-4 | The phone's Linux sees only folders you opened as projects, plus a `Download › OpenCode` outbox. A visible row brings back the old behaviour. | Yes, with the outbox |
| D16-3 | Lock-screen answers and quick Reply need the phone unlocked? | Yes |
| D11-1…14 | Android surfaces (list below this table) | As listed |
| D10-1…12 | Media (list below this table) | As listed |
| M-D1, M-D3…8 | Migration (list below this table) | As listed |
| D12 / S02-6 | Wait for a stable Compose Multiplatform 1.13 before the switch? | Yes. Ask you if it slips more than two weeks past switch-ready. No alpha libraries in the release. |
| D16-5, D16-6, D16-8, R12-8, D15-3, D15-6 | Release questions (list below this table) | As listed |
| GO-1, Stage 0, GO-2, GO-3 | Your GOs: candidate to master and CI draft; "switch went fine?" after two days; pre-release; latest | Your call each time |

**06 Q1–Q11, kit:**

- Ship 7 languages.
- Build the kit gates now.
- Use Android's own date and time pickers.
- Replies follow their own text direction.
- Keep the lens springs.
- No haptic tick on swipe actions.
- Selected text offers Copy and Select all only.
- No hinge-aware layouts on foldables.
- Western digits.
- Keep Flutter's list of texts under the 1.3× cap.
- No Vibration row.

**Q09-1…4, chat:**

- Answers wait until the agent is idle.
- Charts are not mirrored in Arabic.
- No word-level highlights in Changes.
- No "Add to message" action.

**D11-1…14, Android surfaces:**

- Keep the small Linux sandbox helper.
- A short background service carries "reply finished".
- Keep the old widget sizes working.
- Seven languages in notifications.
- After the switch: the richer progress card, share-in, the Pause-tile row.
- Keep the Termux permission through the switch.
- No lock-screen widgets, no app lock, no screen-off read aloud.
- The push relay waits for the iOS decision.

**D10-1…12, media:**

- Keep half-downloaded speech models.
- After the switch: share-in and scan from a picture.
- No background downloads.
- No screen-off dictation.
- F-Droid "Full" build.
- Keep the sealed PDF viewer and the system camera.
- Arabic natural voice waits.
- Wi-Fi share as today.
- Add Share <name> in the file viewer.
- Keep the "verified" note.

**M-D1, M-D3…8, migration:**

- The go-back build is for your phone only.
- Flutter-era copies are deleted no earlier than switch+2, and only with your yes.
- Dead keys are kept until v3.
- Keep the read-state choices.
- No password write-back.

**D16-5, D16-6, D16-8, R12-8, D15-3, D15-6, release:**

- F-Droid main repo after the switch.
- Bridge security patches for 3 months.
- One immutable GPL source release for proot and talloc.
- Rollout: 2 days on your phone, 7 days pre-release, then latest.
- The go-back build is for the owner lineage only.

### After the switch

| # | Question | Recommended |
|---|---|---|
| D11 | Live backgrounds (20 looks) and illustration scenes | Port after the switch, or drop if nobody misses them |
| D15-7 | Order after the switch | Clean-up, then AI Team/Memory and Linux desktop in parallel, then iOS when funded |
| MW / T14-4 | Start the 7,967 widget tests | Ask at the switch, or earlier if the bug trigger fires |
| D13 | iOS prerequisites (a Mac or macOS CI, the Apple Developer Program, a push relay, Liquid Glass vs "no glass", a design pass) | Decide when iOS is funded |
| D14 | Desktop limits: no Linux screen reader, XWayland | Accept; the frozen Flutter Linux build stays until native desktop ships |
| P13-D2 / D5 | Where the AI Team engine lives when it returns; battery temperature as a heat input | Inside the APK; decide after experiment E4 |
| D16-11 | Only if Google verification or F-Droid refuses our debug-named certificates: rotate that lineage | Decide when it happens |

---

## 6. Top 10 risks

Full registers: `plan/15-delivery-roadmap.md` §16, `plan/11-android-platform-native.md` §24, `plan/05-data-migration.md` §13.

| # | Risk | Guard |
|---|---|---|
| 1 | **Agents are slower than planned** and the switch slips weeks (R15-01) | Measure at M0, M2 and M4; the cloud lane or a second server; contracts frozen before fan-out; the scope valve only with your OK |
| 2 | **Migration loses or repeats things**: secrets, settings byte drift, or a restored queue or schedule sending twice (R15-06; audit blocker on R2) | Real-install fixture; a byte backup before any write; dispatch identities kept outside the restorable data; upgrade journeys J1–J8, interrupted at every step; migration owned by Opus |
| 3 | **Placed widgets, tiles, shortcuts or schedules vanish** because an OS-visible name changed (R15-07) | A freeze list taken from the 2.1.0+57 APK, checked at every T2; E2E-39 with every surface placed |
| 4 | **The phone's Linux behaves differently**: the server stops coming back after reboot or update, the wrong process is killed, orphans heat the phone (R15-08; 11 risks 2–4) | Copy the Kotlin first, tests before edits, one lock family, restore proofs from both baselines, FGS starters never wait for the migration gate |
| 5 | **Pre-release libraries**: Compose Multiplatform 1.13 or Material 3 1.5 not stable in time, or a Kotlin bump breaks the build (R15-02, R15-03) | Pins move only at slice boundaries; the switch waits for a stable 1.13 unless you OK the beta; no alpha in the release |
| 6 | **The one emulator and 16 GB PC throttle testing** or crash sessions (R15-09, R15-10) | Desktop and VPS tests first; emulator bookings; session cap measured at M0; extra sessions go to the cloud |
| 7 | **You reject the look late**, and UI regressions slip past while the widget tests wait (R15-17, R15-16) | P0 kit tour early; weekly side-by-side images; per-part kit gates and Roborazzi; a trigger that starts the widget tests earlier |
| 8 | **OpenCode 2, Paseo or Claude Code change under us** (R15-13) | Pins stay 2.1.1 / Paseo 0.11.2 at the switch; drift tests; fixtures re-recorded before affected work |
| 9 | **Native feel regresses**: predictive back fights the row-to-chat transform, or streaming janks on your phone (R15-04, R15-05) | Slice-0 proofs P2–P4; the transform is off during the gesture; a 50 ms publish with a draw-phase fade; benchmarks at S2, S3 and S6 |
| 10 | **Update path breaks**: wrong signer, Google developer verification before registration, a stray tag firing old workflows, 32-bit phones offered a file they cannot install (R15-18, R15-19, R15-30, R15-33) | `apksigner` check before every delivery; register right after the bridge; tag workflows limited to `v2.*`; 64-bit pre-check in the bridge |

Also tracked: AI Team is missing for months after the switch (R15-34, answered by D6), and the preview app clashing with the Flutter app's Linux on your phone (R15-20, answered by D15-4).

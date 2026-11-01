# 15. Delivery roadmap: milestones, slices, the switch-over, risks and what the owner sees

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR) and `02-principles-scope.md`. Where this
file and the ADR differ, the ADR wins. The few places where this file asks the coordinator to amend the ADR or a sibling
section are listed in §18 (C15-n), each with the default that work proceeds on.

Tags: **[V]** checked today against a primary source; **[R]** read in a plan section, map, research file, the repo or
owner memory; **[E]** my estimate, replaced by measured values at the M0 re-plan (§2.4); **[U]** unverified.

## For the owner, in five lines

1. We build the native app in seven steps (M0–M6). From mid-December you get a separate "preview" app beside today's app at the end of each step; the first one you can chat in arrives early February (mid-January with a second build server).
2. The real switch, where the native app installs over today's app and keeps everything including the phone's Linux, is planned for the end of May 2027 with one build server, or early April with two. The first two weeks measure our real speed and fix these dates.
3. Before the switch, one last Flutter update (the "bridge") goes out, so its Update button can later deliver the native app.
4. The switch goes in stages: your phone first for two days, then a GitHub pre-release for a week, then everyone. A ready "go back" build waits until you say the native app is fine.
5. After the switch: a clean-up release, then AI Team and Memory, then Linux desktop, then iOS once there is a Mac and an Apple account. The widget tests come when you say so.

Decision needed: **Yes**, two questions. (a) Rent a second build server like today's VPS, to bring the switch from late
May to early April? Recommended: yes. (b) Publish the bridge update right after the Flutter freeze gate passes (about
16–22 Nov)? Recommended: yes.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read first | Then |
|---|---|---|
| Coordinator | §1, §2, §3, §11, §15–§18 | write each brief from its unit row (§4–§10) with the §20 template; run the §2.4 re-plan at M0, M2 and M4 |
| Builder agent | your unit row, §3.2, §20.2 | start without asking; your brief is complete if every field of 02 §7.13 is filled |
| Test-writer lane | §3.1, §14 | stay one slice ahead |
| T3 / T4 lane holders | §2.5, §11 | |
| Owner | the five lines, §15, §17.1 | |

### 0.2 Words and units used here

| Word | Meaning |
|---|---|
| **ad** (agent-day) | One agent slot for one working day, including its T0/T1 runs, review fixes and its evidence record. **aw** = 5 ad. |
| **Slot** | One concurrently running *builder* agent that uses the build VPS. One VPS gives **6** slots (02 §7.8); a second VPS gives **9** (ADR D15). |
| **Lane** | A standing role with its own agent that does not take a slot: test-writer (TW), flows (FL), fixtures (FX), T3 emulator holder, T4 performance holder (02 §6.9). |
| **Week Wn** | W1 = Monday 2026-11-02 to Sunday 2026-11-08 [V, computed]. W14 = 2027-02-01…07. W30 = 2027-05-24…30. |
| **Calendar A / B** | A = one VPS (plan of record until D15 is answered). B = a second VPS from W3. Both computed by Appendix A. |
| **Slice** | ADR slices 0–6. A slice is a *landing label*: the slice whose end makes a feature complete and its rows green. Build windows of neighbouring slices overlap. |
| **Milestone Mn** | The end of slice n (M0…M6), plus the later phases M7…M10 and MW. Not to be confused with section 08's motion units, which this file writes as "08-M0…08-M7". |
| **Preview Pn** | An owner-facing APK of the `.native` preview flavor (02 §6.10), handed over only after an emulator sweep. |

### 0.3 What this section decides, and what it does not

Decides: milestone order and dates (both calendars), the unit list per slice with finish lines, non-goals, models,
dependencies and agent-days, the feature → slice table for all 700 map features (02 §6.5 asks for it here), the flow →
slice table, the switch-over and staged rollout, the risk register, the owner-decision calendar.

Does not decide: architecture (ADR), module internals (03), protocols (04), formats and migration steps (05), kit parts
(06), screens (07), motion (08), chat (09), media (10), Android surfaces (11), dependencies (12). Each unit row points at
the sibling section that owns its detail.

### 0.4 Naming every unit uses

| Thing | Pattern | Example |
|---|---|---|
| Unit id | `S<slice>-<NAME>` | `S2-TX` |
| Branch | `native/s<slice>-<name-lowercase>-<YYYY-MM-DD>` (02 §7.2) | `native/s2-tx-2026-12-21` |
| Worktree | `/home/eslam/Storage/Code/wt/ocn-s<slice>-<name-lowercase>` (sparse, 02 §7.3) | `/home/eslam/Storage/Code/wt/ocn-s2-tx` |
| VPS copy | `~/wt/ocn-s<slice>-<name-lowercase>` (by `native/tool/vps_gradle.sh`) | |
| Brief | `native/briefs/s<slice>-<name-lowercase>.md` (coordinator writes, §20) | |
| QA record | `docs/qa/native/s<slice>-<name-lowercase>-<YYYY-MM-DD>/README.md` (02 §9.4) | |
| Contract | `native/contracts/s<slice>.md` (frozen before the slice's builders start) | |

---

## 1. The plan on one page

### 1.1 Milestones

| Milestone | Slice | What it proves (one line) | Done, calendar A | Done, calendar B | The owner gets |
|---|---|---|---|---|---|
| **M0** foundations | 0 | The skeleton builds, the ten ADR proofs have verdicts, contracts for slices 1–3 are frozen, the test harness and the parity flows exist | gate end of W2 (Nov 15); tails into W3 | same | a video of predictive back and the streaming test; the re-planned dates |
| **M1** foundations | 1 | Data, network, OpenCode 2 client, connection engine, kit core, words and shell are green | W7 (Dec 14–20) | W7 | **P0 "kit tour"** preview APK |
| **M2** chat daily path | 2 | Add your server, open a chat, stream, stop, steer, answer a permission, go offline without sending twice | W14 (Feb 1–7) | W11 (Jan 11–17) | **P1 "chat with your server"** |
| **M3** phone and agents | 3 | Built-in Linux, the phone server, Claude Code via Paseo, the full composer, attachments, QR pairing | W19 (Mar 8–14) | W14 (Feb 1–7) | **P2 "phone + agents"** |
| **M4** surfaces | 4 | Notifications, reply watch, tiles, widgets, schedules, settings, the five styles, the in-app updater | W22 (Mar 29–Apr 4) | W16 (Feb 15–21) | **P3 "around the phone"** |
| **M5** everything else | 5 | Files, Git, terminals, voice, read aloud, viewers, Wi-Fi share, Codex, cards, integrations, Linux manager | W28 (May 10–16) | W21 (Mar 22–28) | **P4 feature-complete preview** |
| **M6** switch | 6 | Upgrade journeys J1–J8, R1, performance, audit, release plumbing; parity (ADR §10.1) | candidate W29–W30; your phone W30 (May 24–30) | candidate W22 (Mar 29–Apr 4) | the switch on your phone, then the staged release |
| Switch release public | — | pre-release, then "latest" (§11.4) | pre-release W31, latest W32 (Jun 7–13) | W23, W24 (Apr 12–18) | the in-app Update button offers it to everyone |
| **M7** switch+1 | — | Room move (unless triggered earlier), targetSdk 37, Flutter tree removal, first post-switch features | ~4 weeks after latest | same | a normal update |
| **M8** AI Team + Memory | — | ADR D6 deferrals, E2E-28 and E2E-32 | after M7 | | |
| **M9** Linux desktop | — | the native desktop app replaces the frozen Flutter Linux build | 6–10 aw after start [R proposal A §15.2] | | |
| **M10** iOS | — | remote client; needs ADR D13 prerequisites | 12–18 aw (18–30 with widgets and push) [R proposal A §15.3] | | |
| **MW** widget tests | — | the owner's "later" phase | only when the owner schedules it | | |

### 1.2 The critical path (what decides the date)

```
S0-SKEL ─► S0-CORE ─► S1-NET ─► S1-OC2 ─────────────────┐
                         └────► S2-MODEL ► S2-MD ► S2-TX ┴► S2-CHAT ─► (P1)
S0-KIT ─► S1-KITA1/2 ─► S2-KCA, S2-KCB ──────────────────┘
S1-LINUX ─► S3-RTSPLIT ─► S3-RTWIRE ─► S4-BG (reply watch, restore) ─► S6-MIGRHARD ─► owner phone ─► rollout
S1-DATA ─► S2-MIGRRUN ─────────────────────────────────────────────────┘
```

Under calendar B the chain, not the slot count, sets the date: below about W18 nothing gets faster without cutting scope
(§19) [E].

### 1.3 The numbers

| | Builder ad (slots) | Lane ad | Rows in switch scope [E, §14] | Flows that must turn green here |
|---|---|---|---|---|
| S0 | 50 | 47 | 12 | — (all 39 written and run on Flutter first) |
| S1 | 95 | 28 | 132 | — |
| S2 | 125 | 45 | 133 | 5 |
| S3 | 150 | 55 | 342 | 8 |
| S4 | 105 | 45 | 220 | 12 |
| S5 | 160 | 45 | 402 | 11 |
| S6 | 40 | 20 | 18 (+ every remaining red row) | 1 (E2E-39) |
| **Total to the switch** | **725 ad ≈ 145 aw** | **≈ 285 ad ≈ 57 aw** | **≈ 1,274** (+ 15 rows for manual triage) | **37** |

Comparison [R]: proposal A guessed 90–135 aw for the daily-use switch scope and proposal C 95–125 aw, both with tests
and emulator passes folded into units. The sibling sections' own estimates are higher where they gave one (06: kit 29–33
aw; 10: media 30.5 aw). The plan of record uses the central unit numbers in §4–§10; the high case uses the sibling
numbers (+≈ 105 ad, about +4 weeks on calendar A). The M0 re-plan replaces all of them with measured throughput.

---

## 2. Capacity, calendar and re-planning

### 2.1 Agents, machines and who runs where

| Role | How many | Model (02 §7.7) | Machine use |
|---|---|---|---|
| Builder slots | 6 (calendar A) or 9 (B, from W3) | Codex for `:core`, `:protocol`, `:engine`; Sonnet for kit parts, features, `:android:*` ports; Opus for the transcript, migration, the runtime split, navigation spikes, premium visual work | T0 local or VPS, T1 on the VPS through `native/tool/t1.sh`; at most one Gradle build at a time on the PC |
| Test-writer lane (TW) | 1 agent W2–W5, 2 agents W6–W26, 1 agent W27–W29 | Sonnet | T0 compile-only runs on the VPS |
| Flows lane (FL) | 2 agents W1–W4, then 1 part-time | Sonnet | writes Maestro YAML; never holds the emulator |
| Fixtures (FX) | 1 Codex session W1–W3, then on request | Codex | PC fixture servers by exact PID only |
| T3 emulator holder | 1 | Sonnet (Opus for owner-facing candidates) | the one PC emulator under `flock /home/eslam/Storage/tmp/oc-emulator.lock` |
| T4 performance holder | 1, part-time | Sonnet | PC emulator `OC_API37` by booking; NX721J only with the owner's OK |
| Coordinator | the lead session | Opus | briefs, merges, T2 on the VPS, ledgers, owner messages |

Concurrency ceiling on the PC (memory `pc-capacity-ceiling`): about 11 agents at peak on calendar A (6 builders + 2 TW
+ FL + T3 + T4), 14 on calendar B. That fits only because every build and test runs on a VPS, Codex sessions start lean
(`tmux-run-lean.sh`), and `free -m` ≥ 7 GB is checked before any PC Gradle run or emulator start (02 §7.8).

### 2.2 Effort per slice, by track (central [E])

| Slice | Codex (core/protocol/engine) | Sonnet (kit/features/Android) | Opus (hard units) | Total builder ad |
|---|---|---|---|---|
| S0 | 11 | 7 | 32 | 50 |
| S1 | 52 | 26 | 17 | 95 |
| S2 | 27.5 | 52.5 | 45 | 125 |
| S3 | 52 | 84 | 14 | 150 |
| S4 | 25.5 | 64.5 | 15 | 105 |
| S5 | 60.5 | 88.5 | 11 | 160 |
| S6 | 0 | 22 | 18 | 40 |
| **Total** | **228.5** | **344.5** | **152** | **725** |

Mixed units (for example "Codex + Sonnet") are split half and half; coordinator units count as Opus. Codex carries about
a third of the builder load, so a Codex quota stop (R15-12) matters: every Codex unit's brief names the Sonnet agent
that takes over.

### 2.3 The two calendars and how sensitive they are

Computed by Appendix A (`plan_calendar.py`) from the unit tables in §4–§10, at 0.9 planned agent-days per slot-day.

| Throughput factor (`--eff`) | Calendar A (6 slots): week each slice lands | Calendar B (9 slots from W3) |
|---|---|---|
| 0.7 (slower than planned) | S1 W9 · S2 W16 · S3 W23 · S4 W27 · S5 W35 · **S6 W37** | S1 W8 · S2 W13 · S3 W17 · S4 W20 · S5 W24 · **S6 W26** |
| **0.9 (plan of record)** | S1 W7 · S2 W14 · S3 W19 · S4 W22 · S5 W28 · **S6 W30** | S1 W7 · S2 W11 · S3 W14 · S4 W16 · S5 W21 · **S6 W22** |
| 1.17 (Flutter-era pace) | S1 W6 · S2 W11 · S3 W15 · S4 W17 · S5 W22 · **S6 W23** | S1 W6 · S2 W9 · S3 W11 · S4 W12 · S5 W16 · **S6 W18** |
| 1.5 | S1 W4 · S2 W9 · S3 W12 · S4 W13 · S5 W17 · **S6 W18** | S1 W4 · S2 W8 · S3 W9 · S4 W11 · S5 W13 · **S6 W14** |

The S6 week is the switch candidate on the emulator. Add one week for the owner's phone test (02 §6.7: two days) and the
pre-release, then one more for "latest" (§11.4).

### 2.4 Re-planning (at M0, then at the end of M2 and M4)

The coordinator does exactly this, in this order:

1. List every unit that finished since the last re-plan, with its planned ad (unit table) and its actual ad (agent
   start to merge, from `native/LEDGER.md`, counting only working time of that agent).
2. Factor `f = Σ planned / Σ actual`. If fewer than 5 units finished, use them anyway and mark the result [E].
3. Run `python3 -I native/tool/plan/plan_calendar.py --eff <0.9 × f>` for calendar A, and again with
   `--slots-from-w3 9` if a second VPS exists.
4. Write the new dates into §1.1 of this file (the coordinator edits it; nobody else does), with
   "re-planned <date>, f = <value>".
5. If the projected S6 week is later than **W32** on the active calendar, prepare the scope-valve proposal (§19) and
   send it to the owner as one decision. Never cut a row without the owner's OK (ADR §10.1).
6. Tell the owner in four lines: what moved, why, the new P1 and switch weeks, "Decision needed: yes/no".

Also measure at M0: P10 (lines and rows per agent-week, ADR §11) and P8 (build cost). If P8 shows the desktop compile
doubles T1 time, switch to the nightly-only desktop compile (ADR §4 row 17) before S1 fan-out.

### 2.5 The one emulator (the real bottleneck)

All device work shares one PC emulator. Rules:

1. **Prefer tests that need no emulator.** A builder may book the emulator only for what desktop tests and Robolectric
   cannot show: gestures, IME, real notifications, widgets, the camera, proot, the upgrade journey (testing-perf §0).
2. **Bookings** live in `native/EMULATOR.md` (coordinator-owned): one line per booking
   `<date> <from>-<to> <unit> <purpose> <apk versionCode>`. Unbooked use is not allowed.
3. **Daily blocks** (local time): 09:00–12:00 T3 holder (flows, sweeps); 13:00–16:00 builder device checks, at most
   90 minutes each; 16:00–18:00 T4; 19:00–23:00 migration upgrade journeys (long, snapshot-based). The quiet-hours rule is
   lifted (owner 2026-11-01); if reinstated, nothing runs 00:00–02:00.
4. **Priority when two bookings clash:** (1) a candidate APK going to the owner, (2) upgrade journeys, (3) slice-end
   flows, (4) builder device checks, (5) T4.
5. **Hand-off:** release the lock by killing the `sleep` child, confirm with `flock -n <lock> true`, restore font scale
   and density, and write the installed versionCode into `native/BUILD_NUMBERS.md` (02 §7.9).
6. If bookings are full for three days running, the coordinator asks the owner about a KVM-capable second test host
   (risk R15-09). Whether a rented VPS offers `/dev/kvm` is [U] per provider.

### 2.6 Build numbers

| Flavor | Range | Rule |
|---|---|---|
| Main (`io.github.eslamasabry.opencode_mobile`) | 10000–19999 | 05 §9.1: releases step by 10 (10000, 10010, …); numbers ending 1–9 are rollback builds. Upgrade-journey AVDs are reset from snapshots, so journey builds may reuse 10000. Owner-phone candidates start at **10010**. The public switch release keeps the versionCode of the candidate the owner approved. |
| Preview (`….native`) | **20000+** | Agents get blocks of 10 in install order (02 §7.9); each owner-facing preview takes "highest number installed on the emulator or the owner's phone + 10". Keeping the flavors in separate ranges makes every number say which app it is. |
| Bridge (Flutter) | below 10000 | public `2.2.0+58` [E]; owner-lineage local build above the phone's current 43xx (02 §5.7) |

### 2.7 The coordinator's week

| Day | Work |
|---|---|
| Monday | merge queue (02 §9.2), T2 on the VPS, `native/LAST_GREEN`, `docs/native/parity.md`, `twin_check.sh`, merge the Flutter freeze line into `native/main`, write briefs for every slot that frees this week, send the owner message (§15) |
| Every day | merges with T1 on the merged result; T2 at least once on days with merges; kill own Gradle/Kotlin daemons by PID; `df -h /` on both machines and `free -m` on the PC |
| Thursday | T3 holder sweeps the screens merged this week on the emulator, in light and dark, English and Arabic |
| Friday | images or a short video to the owner (one screen per image, video ≤ 3 minutes); at a slice end, the preview candidate (T3 + sweep) |

---

## 3. How every slice runs

### 3.1 Entry criteria (before the slice's first builder starts)

1. `native/contracts/s<N>.md` is frozen (date written at its top) with interfaces, `XUi`/`XActions`, `Failure`
   categories, the row list and **§Deletes**.
2. The TW lane has committed the slice's rows as red tests (02 §7.6), at least for the units starting this week.
3. The units it depends on are merged, or have reached the contract point written as `X@k` in Appendix A (X has run k
   agent-days and its public API is frozen).
4. The rows have `slice = N` and a `module` in `native/catalog/scope.jsonl`.
5. Every map feature landing in the slice (§13) has an owning unit.

### 3.2 A unit, start to finish (every builder follows this; no questions needed)

1. Read the brief, then `native/AGENTS.md` (02 §7.12), then the contract part named in the brief.
2. Create the sparse worktree with the 02 §7.3 recipe, using the branch and worktree names of §0.4.
3. List your rows (§3.4). Run them once with `native/tool/t0.sh <module>` and confirm they fail for the right reason.
4. Build until they pass. Format only the files you changed (`native/tool/format-changed.sh`).
5. When the logical change is ready, run `native/tool/t1.sh --base native/main` once. After a failure, rerun only that
   test.
6. Device work only with a booking (§2.5) and a build number from your block (§2.6).
7. Run `native/tool/check_write_set.sh native/briefs/<brief>.md`.
8. Write the QA record (02 §9.4). Visual work: save progress images every pass (02 §7.10).
9. Commit with the 02 §9.1 message format, `Behaviors:` trailer included. Never push.
10. Report in the 02 §7.14 format, including what is **not** proven.

### 3.3 Exit (slice done, 02 §6.7) and the preview hand-off

1. Every row with `slice = N` is green at T2 on a `native/main` commit.
2. The flows assigned to the slice (§14.2) are green at T3 on the **release** build.
3. The slice's T4 journeys are within budget, or the miss is written down with its owner.
4. The T3 holder ran a full screen-by-screen sweep of every screen the slice touched.
5. The coordinator installs the preview on the emulator, then hands it to the owner with five to eight "what to try"
   lines, one-screen images and one short video (owner rules `one-screen-per-image`, `live-video-proof`).
6. A slice QA record `docs/qa/native/slice-<N>-<date>/README.md` exists.

### 3.4 Listing a unit's rows (copy-paste)

```bash
cd /home/eslam/Storage/Code/wt/ocn-<unit>/native
# rows of this slice proved by this module:
jq -r 'select(.scope=="switch" and .slice==2 and .module==":engine:transcript") | .id' catalog/scope.jsonl > rows.txt
# their text (Given/When/Then) and Flutter sources (rows.txt stays untracked; delete it before committing):
jq -c --slurpfile ids <(jq -R . rows.txt) 'select(.id as $i | $ids | index($i))' catalog/behaviors.jsonl
```

Where a unit row below names rows explicitly (taken from the sibling section that owns them), those rows must be in the
module filter's output; if not, the coordinator fixes `scope.jsonl` before the unit starts.

### 3.5 What each slice deletes (ADR: briefs name what they delete)

| Slice | Removed or never carried over |
|---|---|
| S0 | nothing; spike branches are never merged (03 §22.2) |
| S1 | widgetbook (replaced by the `:desktopApp` gallery); `KitGlass` and Crystal; ARB tooling (frozen after the one-off conversion); `security-crypto` |
| S2 | Flutter's seven pending-intent paths (replaced by `EntryRouter`); the `scrollable_positioned_list` pinned-count workaround; the custom grow `RenderBox`; paint-only span copying; per-frame re-parse; "Press back again to exit" (D5); OpenCode 1 client paths |
| S3 | the `xterm` fork (replaced by Termux `terminal-view`); the second form renderer; the queue choice wherever a runtime steers (`steer-not-queue`); ML Kit QR; Termux beyond migrate-out (D7) |
| S4 | the four headless Dart entrypoints for widgets, schedules and actions (packages-build 39); the Dart schedule run engine; duplicate secret scrubbers |
| S5 | the Dart `highlight` package; the direct ACP client (never ported); A2A (D6) |
| S6 | Shorebird; the preview flavor's kit-tour entry; the Flutter tree on `native/main` follows one commit after the release is marked latest (C15-7) |

---

## 4. M0 — Slice 0: foundations, skeleton, migration spike, test harness (W1–W2)

- **Finish line:** a `native/` tree on `native/main` assembles both flavors' release skeleton, every ADR proof P1–P10
  has a written verdict with its fallback taken where needed, contracts for slices 1–3 are frozen, and the Flutter
  reference has baselines and runnable parity flows.
- **Non-goal:** any feature a person can use.
- **Owner sees:** W1 one image (the native "Opening…" screen on the emulator); W2 a video of under two minutes: the
  back gesture from a chat to its row with the grow-from-row motion, plus the 5,000-part streaming test; W3 the
  re-planned dates in four lines.
- **Rows:** ≈ 12 [E] (BUILD 6, XPLAT 3, ANDR 2, SEC 1) plus spike evidence for KIT, MIGR and L10N rows (no green claim).
- **Entry:** none. **Exit (M0 gate, end of W2):** 02 §10 items 1–4, 6, 8, 9 and 10 done; item 5 by end of S1; item 7 as
  C15-6 says; 03 §23 acceptance checks 1–14 pass; the §2.4 re-plan is done. K0, P3 and P6 may finish in the first two
  days of W3 (C15-8).

### 4.1 Builder units (50 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S0-SKEL | Opus (build-logic owner) | `native/{settings.gradle.kts,build.gradle.kts,gradle.properties,version.properties,gradle/**,build-logic/**,androidApp/**,desktopApp/**,quality/**,config/**,AGENTS.md,MODULE_OWNERS,JETPACK_AHEAD.md}` | Both flavors assemble, the Metro graph smoke resolves a `ConnectionScope` extension and two contributed entry installers, and the nightly iOS klib job is green for `:core:*` (ADR P1). | Kit parts, engine logic, screens. | gates only | — (first commit due end of day 2) | 6 | W1–W2 | ADR §11 P1; 03 §22, §23 |
| S0-KIT | Opus (kit owner) | `native/ui/{design,kit,testing}/**`, `native/contracts/kit-v1.md`, gallery files of `native/desktopApp/**` | `KitTappable` is tested three ways (desktop compose test, Robolectric, Roborazzi light/dark en/ar on the VPS), `KitFrameText` holds the 1.3× cap at 200 % with non-linear scaling, ripple and haptic are seen on the emulator, and the 20 signatures are frozen (ADR P7). | The K1 part list. | B-KIT-006, B-KIT-008, B-STATE-003, B-MIGR-005 (08-M0) | — | 10 | W1–W3 | 06 K0; 08 §11 08-M0 |
| S0-CORE | Codex | `native/core/{common,model,domain,security,testing}/**`, interface part of `native/contracts/s1.md` | The five core modules compile for android, desktop and iOS klibs; all 85 `ServerCapabilities` defaults equal the Dart vector file; `Outcome`/`Failure`, gateways, `GatewayEvent`, `StreamStatus`, `NetworkStatus` are frozen. | Implementations behind the interfaces. | B-PROTO-137; capability snapshot; Redactor vectors | S0-SKEL@2 | 6 | W1–W2 | 04 WP-04.0; 03 §22.4 |
| S0-NAV | Opus | branch `spike/p2-predictive-back`; `native/docs/spikes/P2.md` | Option A of the list → chat transform passes the six checks of motion-native §4.4 on the emulator; the option B spike result is recorded (ADR P2). | Production shell code. | B-KIT-054 (evidence) | S0-SKEL@2 | 5 | W1–W2 | 07 §3.2, §11; 08-M1 |
| S0-MIGR | Opus (migration owner) | `native/android/migration/**` (spike), branch `spike/p5-identity`, `native/docs/spikes/P5.md` | A skeleton installed with `adb install -r` over Flutter 2.1.0+57 adopts the prefs file, decrypts one v11 secret, leaves the rootfs byte-identical, keeps both widgets, both tiles, a pinned shortcut, a schedule and a channel setting, and records the step-0 time and the Room trigger numbers (ADR P5). | The runner, gate UI, any step beyond S0 and one secret. | B-MIGR rows (evidence) | S0-SKEL@2; emulator booking | 5 | W1–W2 | 05 W1, W8 (J1 short form) |
| S0-STREAM | Opus | branch `spike/p3-transcript`; `native/android/benchmark/**` spike | A recorded 5,000-part stream through a throwaway `TranscriptEngine` and a block `LazyColumn` with the draw-phase fade matches the Flutter publish counts, has no frame over budget in a release Macrobenchmark, and P4 recomposition counts are recorded with and without `compose-stability.conf`. | Production engine code. | — | S0-SKEL@2 | 4 | W2–W3 | ADR P3, P4; 09 §17 S0-P3 |
| S0-NETP | Codex | `spike/pn-*` branches; `docs/qa/native-oc2-probe-2026-11/README.md` | P-N1…P-N6 have verdicts (WebSocket over OkHttp, pinned TLS under Conscrypt, SSE against live OpenCode 2.1.1, the 2.1.1 vs 2.2.5 probe, the retry trap, network change). | Production network code. | — | — | 5 | W1–W2 | 04 §17 |
| S0-WORDS | Sonnet (l10n owner) | branch `spike/p6-strings`; early `native/tools/strings/**` | ARB → CMP XML converts, Android 13 per-app language switches a screen and a notification at runtime, Arabic plurals are right, several `strings_<feature>.xml` per values folder work (ADR P6). | Converting all strings (S1-WORDS). | B-L10N rows (evidence) | S0-SKEL | 3 | W2–W3 | 06 §13, W1 |
| S0-GATES | Sonnet + coordinator | `native/tool/**`, Konsist rules in `native/quality/**` | `t0.sh`…`t4.sh`, `vps_gradle.sh`, `check_write_set.sh`, `twin_check.sh`, `check_commits.sh` each ran once and wrote a summary; Konsist catches a planted `material3`, `LocalContext` and raw `clickable`; a Termux `TerminalView` renders through `PlatformViewHandle` (ADR P9). | New gate ideas. | — | S0-SKEL | 4 | W2–W3 | 02 §8, §10 item 3 |
| S0-ROUTES | coordinator | `native/ui/routes/**`, id types in `native/core/model/**` (with the S0-CORE owner) | Route keys for slices 1–3 exist; `RouteRoundTripTest` and `RouteKeyContentTest` are green. | Screens. | — | S0-SKEL | 2 | W2–W3 | 07 §2.2, §11 |

### 4.2 Lane units (47 ad)

| Unit | Who | Write set | Finish line | ad | Weeks |
|---|---|---|---|---|---|
| S0-SCOPE | coordinator + 1 Sonnet | `native/catalog/**`, `ScopeFileTest` in `native/quality/**` | `scope.jsonl` seeded by Appendix B, the 15 MANUAL rows and the AFTER/DROP buckets checked by hand, every row given a `module`, the 02 §3.6 amendments entered; `ScopeFileTest` green | 3 | W1–W2 |
| S0-FX | Codex | `native/fixtures/**`, `native/e2e/fixtures/**` | FX-OC2 (stub model with the `e2e:` prompts), FX-OC2-B, FX-TLS, FX-PASEO, FX-CODEX, FX-GH, FX-GIT, FX-LINUXDL, FX-USAGE, FX-AUTH start, stop and log as e2e-flows says; started and stopped by exact PID | 6 | W1–W3 |
| S0-FLOWS | FL ×2 + T3 holder | `native/e2e/flows/**`, `native/e2e/runner/**` | All 39 flows written; slice-1/2 flows green on Flutter R by end of W2, all by end of W4, or each gap written to `KNOWN-FLUTTER-GAPS` | 20 | W1–W4 |
| S0-BASE | T4 holder | `docs/qa/native/baseline-flutter-<date>/**` | 02 §4.1 baselines recorded on the same AVD (phone half only with the owner's OK) | 3 | W1–W2 |
| S0-FREEZE | coordinator | the Flutter freeze line only | The freeze gate passes (split suite, release APK, emulator sweep); the `flutter-final` candidate is named | 3 | W1–W2 |
| S0-BRIDGE | Sonnet on the Flutter freeze line | `lib/` census file + its test (05 W13) | The counts-only census is in the bridge candidate (if M-D2 is yes); the bridge's updater is proven to accept a 10000-range versionCode and the native asset names on the emulator | 2 | W2 |
| S0-TW1 | TW | test files of slice-1 modules | Every slice-1 row has a red test that fails for the reason the row describes | 8 | W2–W3 |
| S0-TOPUP | TW lead | `native/catalog/behaviors.jsonl` copy, `scope.jsonl` rows | Rows for the logic tests added or changed in `65e3f70a3..flutter-final` (02 §3.5) | 2 (+ 2 in S1) | W2–W4 |

### 4.3 Launch order and slots (calendar A)

| Slot | W1 | W2 | W3 (first days) |
|---|---|---|---|
| B1 | S0-SKEL | S0-SKEL → S0-ROUTES | S1 starts |
| B2 | S0-KIT | S0-KIT | S0-KIT (2 days) |
| B3 | S0-CORE (from day 3) | S0-CORE → S0-WORDS | S0-WORDS |
| B4 | S0-NAV (from day 3) | S0-NAV → S0-GATES | S0-GATES |
| B5 | S0-MIGR (from day 3) | S0-MIGR → S0-STREAM | S0-STREAM |
| B6 | S0-NETP | S0-NETP → S1-DATA (early start) | S1-DATA |
| Lanes | FL ×2, FX, T4 (baselines), coordinator (freeze gate, scope) | + TW1, TOPUP, BRIDGE | |

Emulator in M0 (booked in this order): Mon–Tue W1 freeze-gate sweep; Wed W1 baselines; Thu W1 P2; Fri W1 P5; W2
mornings Flutter flows; W2 afternoons P3 benchmark and P7 ripple and haptic checks.

---

## 5. M1 — Slice 1: foundations (W3–W7 on A)

- **Finish line:** the core stores read Flutter's data unchanged, the OpenCode 2 client passes its 24 fixtures and the
  live proof, the connection engine connects to several servers at once, the kit's core parts and all words exist, and
  the shell runs the preview flavor's kit tour.
- **Non-goal:** any feature screen (Servers, Conversations and the chat start in slice 2).
- **Owner sees:** P0 "kit tour" (W7): a preview APK beside the Flutter app whose only screen is a tour of kit parts in
  the Solid style, light and dark, English and Arabic: ripple, stretch, sheets with predictive back, text at 200 %. Plus a
  one-line proof that the new engine talked to the owner's OpenCode 2 on the VPS.
- **Rows:** ≈ 132 [E] (PROTO 35, SEC 33, KIT 27, L10N 12, STATE 12, NAV 5, ANDR 5, others 3).
- **Flows:** none required; the FL lane finishes all 39 on Flutter R by W4.
- **T4:** shell cold start (early S-5 and S-7 readings).
- **Entry:** M0 gate. **Exit:** §3.3 items 1, 4–6; P0 handed over.

### 5.1 Builder units (95 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S1-DATA | Codex | `native/core/data/**` | `KeyValueStore` over the adopted prefs file (Long ints, both list encodings, the double prefix, `DartJson`), `WriterLane`, `ProfileScope` and its sweep, `SecretStore` (Keystore AES-256-GCM) and the `MigrationGate` interface are green on the real-install fixture. | The runner; Room; `BlobStore`. | 05 §10.2 rows of W2–W4; `PrefsLedgerTest`, `ProfileSweepShapesTest` | S0-CORE | 10 | W2–W5 | 05 W2–W4 |
| S1-SECNET | Codex | `native/core/security/**`, `native/core/net-okhttp/**` | Address, redaction and link vectors and the hardened OkHttp builder with the leaf-pin trust manager pass; no `java.*` in common code (klib job). | Ktor client, SSE. | B-SEC-007, 113, 119…125, 153, 154, 174; B-PROTO-072, 128…131 | S0-CORE | 7 | W3–W4 | 04 WP-04.2, 04.3 |
| S1-NET | Codex | `native/core/net/**` | The Ktor client factory, the SSE parser (16 MiB cap, 10k-case fuzz), the `EventStreamLoop` timing table under virtual time and the WebSocket path from P-N1 pass. | Protocol DTOs. | B-PROTO-033, 132, 133 | S0-CORE, S0-NETP | 6 | W3–W4 | 04 WP-04.4 |
| S1-OC2 | Codex (Opus reviews the event adapter) | `native/protocol/oc2/**`, `native/fixtures/oc2/**` | All 24 Flutter fixtures decode to the same variants as Dart, and live proof steps 1–25 pass against OpenCode 2.1.1 on the VPS. | PTY, MCP, files, plugins (S5). | B-PROTO-010…019, 021, 027, 034, 037…039, 047, 061, 062, 089…093, 134, 136; B-CHAT-005; B-SEC-055, 056 | S1-NET | 12 | W4–W7 | 04 WP-04.5 |
| S1-CONN | Codex | `native/engine/connection/**` | The connect → stream → reconcile order passes with `FixtureEngine`; several live connections each get a `ConnectionGraph`; quiet reconnect timers are ported verbatim. | The status-line UI. | B-PROTO-094, 095, 124, 138; B-STATE-011 | S1-NET (OC2 through fakes until it lands) | 8 | W4–W6 | 04 WP-04.6, §12 |
| S1-ENG | Codex | `native/engine/{settings,diagnostics,profiles}/**` | The preferences facade, three-state consent reads, the teaching registry, the diagnostics ring with crash categories and the exit classifier, and the profile store with trust, pins and the backwards deletion transaction are green. | Settings screens (S4). | `scope.jsonl` filter on the three modules, slice 1 | S1-DATA | 9 | W5–W7 | ADR §5.2 |
| S1-KITA1 | Opus (kit owner) | `native/ui/{design,kit}/**`, part-A files listed in the brief | The 31 packs, accents, dynamic colour, the frames (`KitScreen`, top bar, status line and slot, rail, sidebar, page heading, system bars), the row family, `KitNotice` and `KitDetailsFold` meet 06 §16.3 with the capacity, manifest, Konsist and literal gates live. | Styles other than Solid (S4). | KIT rows of `:ui:kit`, slice 1 | S0-KIT | 10 | W3–W5 | 06 K1 |
| S1-KITA2 | Sonnet (kit agent under the owner) | part-B files of `native/ui/kit/**` | Buttons, action block, icon button, chips, `KitField` in all kinds, segmented, choice list, switch and expand rows, sheet, confirm, dialog, menu, undo, state view, loading bar, skeleton, progress, receipt and needs-you meet 06 §16.3. | Motion parts (S2-KITB). | same | S0-KIT | 10 | W3–W5 | 06 K1 |
| S1-WORDS | Sonnet (l10n owner) | `native/tools/strings/**`, `native/ui/words/**` | All 7 languages are converted once into `strings_<feature>.xml`, `strings_os` is generated into Android res, the drift check is green, and `convert-report.md` (unused keys) is reviewed. | New copy. | B-L10N rows, slice 1 | S0-WORDS | 8 | W3–W4 | 06 W1, §13 |
| S1-SHELL | Opus (coordinator role) | `native/ui/shell/**`, `native/androidApp/src/preview/**` (kit tour entry) | `App()`, the opening, gate and safe-mode screens, `ShellNavDisplay`, `AppNavigatorImpl` (rules N1–N9 table-tested), the rail and sidebar frame, the status slot and list-detail run on the emulator and in the desktop gallery; the preview flavor opens the kit tour. | Feature screens. | B-NAV-005, 024, 037 (navigator parts) | S0-ROUTES, S0-KIT | 7 | W5–W6 | 07 §2–§3, §11 |
| S1-LINUX | Sonnet (runtime owner reviews) | `native/android/linux/**` (copy only), rows in `native/TWINS.md` | Every built-in Linux Kotlin file is copied unchanged into `:android:linux`, and its 30 JVM tests and 11 instrumented acceptance classes pass with zero edits. | Any edit or split. | LINUX rows of the copied tests | S0-SKEL | 5 | W5–W6 | 11 §0.2 S11-1, §4 |
| S1-PLAT0 | Sonnet | three files in `native/android/platform/**` | `NativeCrashStore`, the network monitor and basic `DeviceFacts` run behind their `:core:domain` interfaces (C15-3). | Entry intents and pickers (S2). | ANDR rows of map features 42, 45 | S0-CORE | 3 | W6 | 11 §1.1 rows 01, 42, 45 |

### 5.2 Lanes in S1 (28 ad)

S1-FIXT (Sonnet + Codex, 4 ad): fixtures copied with a SHA-256 manifest, a recorder that refuses a planted password,
the 2.1.1 `openapi.json` committed (04 WP-04.7). TW: slice-2 rows red (10 ad). FL: the remaining flows green on Flutter
R by end of W4 (part of S0-FLOWS). TOPUP: finished by the end of S1 (2 ad). Coordinator: bridge release in W3 if the owner
says yes (§11.1); weekly `twin_check.sh`.

### 5.3 Launch order (calendar A)

W3: S1-DATA (running since W2), S1-SECNET, S1-NET, S1-KITA1, S1-KITA2, S1-WORDS. W4: S1-OC2 and S1-CONN replace
S1-SECNET and S1-NET. W5: S1-ENG, S1-SHELL, S1-LINUX as slots free. W6: S1-PLAT0, then the first slice-2 units
(S2-ENTRY, S2-MODEL, S2-KCB).

---

## 6. M2 — Slice 2: the chat daily path (W6–W14 on A, W3–W11 on B)

- **Finish line (09 §17, extended):** on the preview flavor, the owner adds their OpenCode 2 server by typed address or
  pairing code, sees Conversations, opens a chat, sends a prompt, watches a reply with code stream smoothly, stops it,
  sends while a reply runs (it steers), answers a permission once, scrolls up through older history without a jump,
  finds a word, and, with the network off, sees the prompt wait and never sent twice.
- **Non-goal:** drafts saved across restarts, attachments, code colours, forms, the phone's Linux, agents,
  notifications, widgets. (These are the known gaps of P1; 09 §17.)
- **Owner sees:** P1 "chat with your server" (W14 on A); weekly videos of streaming from W10; the J1–J3 numbers next to
  Flutter's.
- **Rows:** ≈ 133 [E] (STATE 45, CHAT 30, PROTO 17, NAV 16, ANDR 7, SEC 6, others 12).
- **Flows green at T3:** E2E-01, E2E-03, E2E-04, E2E-05, E2E-10. Partial and tracked: E2E-21 steps 1–7, E2E-02 typed
  and code steps, E2E-06 approve and deny.
- **T4:** J1 open a chat (S-11), J2 stream (S-9, S-12), J3 fling (S-10), cold start (S-5…S-7); first Baseline and
  Startup Profiles.
- **Entry:** contract s2 frozen (including ADR-09-1, C15-5); M1 units S1-CONN, S1-DATA, S1-KITA1/2, S1-SHELL merged.

### 6.1 Builder units (125 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S2-ENTRY | Codex | `native/engine/entry/**`, `EntryIntentReader.kt` in `native/android/platform/**` | `EntryRouter` holds every launch request until bootstrap and the gate open, consumes each exactly once and emits `EntryTarget`s; the 07 §7.4–§7.9 tables pass as common tests; the ADR §9.7 bootstrap order is wired. | Widget, tile and notification entries (S4). | B-NAV-005, 025, 054, 061, 063, 064, 077; B-ANDR-027, 028, 071, 073, 075 | S0-ROUTES, S0-CORE | 5 | W6–W7 | 07 §7 |
| S2-MODEL | Codex | chat types in `native/core/model/**`, `ToolPresentation` in `native/core/domain/**` | The 09 §2 types and `ToolPresentation` are green with their trap rows. | Wire adapters. | 09 §2.3 rows | S0-CORE | 3 | W6–W7 | 09 §2 |
| S2-MD | Codex | `native/core/markdown/**` | Streaming blocks, path chips, agent-block scanners and `proseForSpeech` pass on the ported fixtures. | UI. | CHAT Markdown rows | S2-MODEL | 5 | W7–W8 | 09 §9.1 |
| S2-TX | Opus (chat lead) | `native/engine/transcript/**` | Events become newest-first rows with version fences, merge and passes, published at most every 50 ms with the first delta at once; the 09 §14.2 counters are green. | The list UI. | CHAT-GENUI 01–06 engine rows; B-PROTO-015, 040, 088 | S2-MODEL, S2-MD@3 | 12 | W8–W11 | 09 §3 |
| S2-DLV | Codex | `native/engine/delivery/**`, send files of `native/protocol/oc2/**` | Bubble-first send, the steer table (never a queue where steering exists), Stop, the live line, and the offline queue with dispatch markers and command receipts, reading a real Flutter `oc.offlineQueue` blob unchanged (C15-1). | Drafts, attachments (S3). | B-STATE-008, 009, 033…046, 066, 067; B-PROTO-023…025, 028, 032, 042, 043, 045, 073, 074, 135 | S2-TX@4, S1-DATA | 10 | W9–W12 | 09 §4; 04 WP-04.8 |
| S2-REQ | Codex + Sonnet | permission and one-question parts of `native/engine/requests/**`, request files of `native/feature/chat/**` | A permission and a one-question ask are answered in the chat exactly once, with the 3 s hold. | Forms, always-allow, notifications. | CHAT-GENUI 43, 44 rows; OC2 permission dispatch rows | S2-TX | 5 | W11–W12 | 09 §5; C15-5 |
| S2-KCA | Opus | transcript, stream and Markdown files of `native/ui/kit-chat/**` | `KitTranscriptList`, `TranscriptListState`, `RevealRegistry`, the fresh-word fade, grow-only, the stream caret, the `KitTurn` family and the Markdown block renderers render the P3 recording within budget. | Composer parts. | B-CHAT-060, 096; kit-chat rows | S1-KITA1, S2-MD | 13 | W8–W11 | 06 KC2; 08-M4; 09 §7–§8 |
| S2-KCB | Sonnet (kit-chat owner) | message, tool, work, request and composer files of `native/ui/kit-chat/**` | `KitMessage` with lift, `KitToolRow`, `KitWorkLine`, `KitQueuedMessage`, `KitRequestCard` v1, `KitComposer` v1 (field, Send/Stop, chips row, frost) and `KitStartBox` meet 06 §16.3; the composer rides the IME frame by frame on the emulator. | Tray, attachments, voice pill. | composer KIT and CHAT rows | S1-KITA2 | 10 | W6–W9 | 06 KC2 |
| S2-KITB | Sonnet | the K1 rest and K2 files of `native/ui/kit/**` | Motion parts (08-M2), sheet and dialog motion (08-M3), and the K2 parts (tip, refresh, jump pill, tab strip and switcher, search field, filter chips, scroll fade, last known, server trust, top-bar pill, scope, link, typing guard) meet 06 §16.3 with reduced-motion tests. | Appearance parts (S4). | B-KIT-011, 014, 015, 016, 019, 022, 023, 025, 031, 032, 046, 050, 051; B-NAV-073 | S1-KITA1 | 13 | W7–W9 | 06 K1, K2; 08 §11 |
| S2-CHAT | Opus (chat lead) | `native/feature/chat/**` | The chat screen with the list, follow, jump pill, find, status line, message actions, `BasicComposerHost`, arrivals and the Back guard works on the preview build; J1–J3 are recorded on the emulator. | Composer extras (S3), chat pages (S4). | chat screen rows; B-NAV-005 (chat entry) | S2-TX@6, S2-DLV@3, S2-KCA@6, S2-KCB@5 | 12 | W11–W14 | 09 §6–§7; 07 §11 |
| S2-CHATS | Sonnet | `native/feature/{onboarding,chats}/**` | The bootstrap gate UI, root decision, first run, Conversations home, inbox, all conversations, filters and new conversation pass E2E-01 and E2E-21 steps 1–7 on the preview build. | Pins to shortcuts (S4); agent rows (S3). | Conversations NAV rows; B-NAV-017, 018 | S1-CONN, S1-SHELL, S2-KITB@5 | 10 | W9–W11 | 07 §5 A, B |
| S2-SERVERS | Sonnet | `native/feature/servers/**` | Servers, the editor, typed address and pairing code, connection details, the switcher, cleartext consent and pin review work; E2E-03 and E2E-04 pass. | QR scanning (S3). | B-NAV-006…009; B-SEC-107, 108, 124 | S1-CONN, S1-DATA, S1-KITA2 | 8 | W7–W8 | 07 §5 E; 04 WP-04.9 |
| S2-PLATFORM | Sonnet | entry, share-text, Tailscale, static-shortcut and `ActivityResults` files of `native/android/platform/**` | Deep links, text share in and out, the Tailscale handoff and static shortcuts reach `EntryRouter`; `ActivityResults` is registered on `MainActivity`. | Widgets, tiles, pinned shortcuts (S4). | B-ANDR-037; ANDR rows of map features 29, 32–34 | S2-ENTRY | 4 | W7–W8 | 11 §14, §16 |
| S2-MIGRRUN | Opus (migration owner) | `native/android/migration/**` | The journal, S0 backup, M1 census, M2 secrets, M3 prefs adoption, M4 restore check, M5 audit and M6 complete pass on the fixture; `StepInterrupter` exists in the `qa` build. | Hardening, R1, R2 (S6). | 05 §10.2 runner rows; core parts of B-MIGR-001…013 | S1-DATA | 8 | W7–W9 | 05 §7, W6 |
| S2-GATEUI | Sonnet (shell owner) | gate files of `native/ui/shell/**`, `strings_migration.xml` | "Updating your app…", "Opening…", Try again at 8 s, the failure screens and the re-enter-password sheet render light/dark, en/ar, at 200 % in VPS goldens. | Migration logic. | 05 §8.2 rows | S2-MIGRRUN@3, S1-SHELL | 3 | W9 | 05 W7 |
| S2-BLOB | Codex | blob files of `native/core/data/**` | `BlobStore` over today's files passes the ported vault tests. | — | 05 W5 rows | S1-DATA | 2 | W9 | 05 W5 |
| S2-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | Macrobenchmark J1, J2, J3 and cold start run on `OC_API37`; first Baseline and Startup Profiles generated. | Fixing what they find. | S-5, S-9…S-11 | S2-CHAT | 2 | W14 | 08-M6; testing-perf §9 |

### 6.2 Lanes in S2 (45 ad)

TW: slice-3 rows red (two agents from W6). FL: keep flows green on Flutter R; add the native release run for E2E-01,
03, 04, 05, 10. T3: weekly sweeps; slice-end flows. T4: J1–J3 on the emulator in W14.

---

## 7. M3 — Slice 3: the phone runtime, agents and the full composer (W10–W19 on A, W5–W14 on B)

- **Finish line:** on the preview flavor, a person sets up the phone's Linux, starts the phone's OpenCode server, signs in
  to Claude Code in its own terminal and chats with it through Paseo, writes with the full composer (drafts, saved
  prompts, phrase chips, mentions, commands, photos and files), answers forms, and pairs a server by scanning a QR code.
- **Non-goal:** notifications, widgets, tiles, schedules, settings pages, the Linux manager, voice, Codex.
- **Owner sees:** P2 "phone + agents" (W19 on A). The preview flavor has its own data and its own ports (S11-6), so on
  the owner's phone it needs its own Linux install; whether to do that is D15-4 (§17). Otherwise the owner gets emulator
  videos of setup, the phone server and Claude Code.
- **Rows:** ≈ 342 [E] (LINUX 123, AGENT 79, CHAT 34, STATE 34, SEC 24, ANDR 21, PROTO 11, NAV 8, others 8).
- **Flows green at T3:** E2E-02, E2E-06, E2E-11, E2E-12, E2E-17, E2E-18, E2E-30, E2E-31.
- **T4:** S-16 (hidden phone agent CPU ≤ 5 % of a core).
- **Entry:** contract s3 frozen; S1-LINUX, S2-DLV, S2-REQ, S2-KCB merged; ADR D8 (Paseo path) answered or defaulted.

### 7.1 Builder units (150 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S3-PASEO | Codex | `native/protocol/paseo/**` | The Paseo transport, gateway, mappers and chat feed pass the Paseo live proof and the FX-PASEO fixtures. | Paseo P1 extras (S5). | B-PROTO-070, 071; B-AGENT-013, 142…191; B-SEC-109…111, 113…116; B-CHAT-070…072 | S1-NET | 10 | W10–W12 | 04 WP-04.10, §8 |
| S3-RTSPLIT | Opus (runtime owner) | `native/android/{linux,agenthost}/**` | `BuiltinLinux` is split per 11 §5.3 with the one lock family (S11-2), `GuestLauncher` and `GuestProcessRegistry` exposed, `:android:agenthost` carved out (CR11-0), and every copied test still green. | New behaviour. | copied LINUX rows, unchanged | S1-LINUX | 10 | W10–W12 | 11 §5.3 |
| S3-RTENG | Codex | `native/engine/runtime/**` | The portable rules (setup engine, start and probe, recovery consumer, healing, idle, thermal, process budget, manager parsers) and the shell scripts as checked `.sh.tmpl` files (S11-3) are green in common tests. | Android bindings. | LINUX rows of `:engine:runtime`, slice 3 | S1-DATA | 13 | W11–W14 | 11 §5.2, §5.4, §5.6, §7, §8 |
| S3-RTWIRE | Sonnet (runtime owner reviews) | bindings in `native/android/linux/**`, service shells at frozen FQCNs in `native/androidApp/**` | `AndroidRuntimeHost` binds the engine; the phone server FGS, the setup FGS with notifications 4098/4099, the idle-return notice 4100, exit recovery and the process budget work on the emulator; E2E-17 and E2E-18 pass. | Linux manager pages (S5). | ANDR rows of map features 11–13, 43, 44, 63; LINUX lifecycle rows | S3-RTSPLIT, S3-RTENG@8 | 7 | W13–W15 | 11 §6, §7 |
| S3-AGENTSE | Codex | `native/engine/agents/**` | The agent catalog (pins copied verbatim), readiness, install pipeline, phone check, sign-in model, auto-approval, merged feed, failure/title/context memory and the `AgentHost` interface are green. | Handoff, sub-agents (S5). | AGENT rows of `:engine:agents`, slice 3 | S3-PASEO@5, S3-RTENG@6 | 11 | W13–W16 | agents-backends 01–15, 33, 42 |
| S3-AGENTHOST | Sonnet | `native/android/agenthost/**` | `PhoneAgent*`, `PaseoStaleLock`, admission, readiness, idle, leases, sign-in over private pipes and the auth probe run behind `AgentHost`; their Kotlin tests pass. | — | LINUX rows of map features 52–54 | S3-RTSPLIT | 5 | W12–W13 | 11 §1.2 |
| S3-REQFORMS | Codex + Sonnet | `native/engine/requests/**`, form files of `native/ui/kit-chat/**` | One `FormSpec` engine (OC2 `hidden` and `when`, MCP/ACP elicitation, AskUserQuestion), the question sheet, approval modes and the always-allow invitation work; forms come back after a forced reconnect. | Card forms (S5). | B-PROTO-029, 044, 048, 063, 069, 102, 103; CHAT-GENUI 45–48 rows | S2-REQ | 7 | W12–W14 | 09 §5; 04 WP-04.11 |
| S3-COMPE | Codex | `native/engine/composer/**` | Drafts, the attachment vault, stash, prompt history, phrase chips, references and the photo recovery record are green and read Flutter-written data unchanged. | UI. | STATE rows of map features 21–23, 26; CHAT-GENUI 35–37 rows | S2-DLV | 7 | W12–W13 | 09 §10 (as amended by C15-1) |
| S3-COMPUI | Sonnet | `native/feature/chat-composer/**` except attachment files | `FullComposerHost` replaces the basic one: chips row, `/` `@` `!` with the command sheet, the editor, saved prompts and the model/agent/thinking picker; E2E-11 passes. | Attachments (S3-MEDIAATT), voice (S5). | CHAT-GENUI 26, 27, 30, 31, 81 rows | S3-COMPE@4, S2-KCB | 10 | W14–W16 | 09 §10 |
| S3-MEDIAATT | Codex (logic) + Sonnet (Android, UI) | `native/engine/media/**/{attach,office,xml,preview}/**`, `native/android/media/**/{image,clipboard}/**`, attachment files of `native/feature/chat-composer/**` | The "+" tray (Photos, Camera, File, More), clipboard and keyboard pictures, chips and photo recovery work; E2E-12 passes on the release build. | Viewer and gallery (S5). | B-MEDIA-001, 006, 007, 008; B-CHAT-009, 010, 021…024, 107; B-STATE-088 | S3-COMPE@3 | 15 | W14–W18 | 10 MV-1a, MV-2 |
| S3-KCCONTENT | Sonnet (kit-content owner) | `native/ui/kit-content/**`, image files of `native/ui/kit/**` | The code block with `CodeTokenizer` (SyntaxMP benchmark first), log panel, `KitImage` and its resolver, QR drawing, the scanner slot (CameraX + zxing-cpp), terminal output view, capability explainer, checklist, progress and context rings, and the task chip meet 06 §16.3; scanning works on the emulator camera; `dependencyInsight` shows no ML Kit. | Diff page, viewer, gallery, live terminal extras (S5). | B-ANDR-049; content KIT rows | S1-KITA2 | 18 | W12–W16 | 06 KN3; 10 MV-3, MV-9 |
| S3-WORK | Sonnet | work files of `native/ui/kit-chat/**` and `native/feature/chat/**` | Timeline rows, the step light, the background work line and the running work sheet work. | — | CHAT-GENUI 12, 22, 25 rows | S2-KCA | 5 | W14–W15 | 09 §17 S3-WORK |
| S3-TERMMIN | Sonnet | terminal files of `native/ui/kit-content/src/androidMain/**`, sign-in files of `native/feature/agents/**` | The sign-in terminal runs Claude's own `claude auth login --claudeai` in a Termux `TerminalView` over the copied PTY; E2E-31 passes. | Key bar, OSC 52, server PTY (S5). | AGENT rows of map features 13, 14 | S3-KCCONTENT@8, S1-LINUX | 4 | W15–W16 | C15-2 |
| S3-PHONEUI | Sonnet | `native/feature/phone/**` (not the Linux manager) | This phone, setup A/B/C, Keep running and phone health pages work; E2E-17 and E2E-18 pass. | Linux manager (S5). | NAV rows of screens-nav 74, 76, 78, 79, 83 | S3-RTENG@6, S2-KITB | 12 | W16–W18 | 07 §5 F |
| S3-AGENTSUI | Sonnet | `native/feature/agents/**`, agent files of `native/feature/chats/**` | The agents hub, agent sheet, sign-in, account, the agent chip and agent rows in Conversations work; E2E-30 and E2E-31 pass. | Codex, handoff (S5). | NAV rows of screens-nav 82, 84; AGENT UI rows | S3-AGENTSE@5, S2-KITB | 10 | W16–W18 | 07 §5 G |
| S3-MEDIAPROOF | Opus (media lead) | `native/tools/proofs/media/**`, `docs/qa/native/media-proofs-*` | P-M1 (sherpa AAR), P-M2 (Whisper numbers), P-M3 (QR scan), P-M6 (voice model path) and P-M7 (keyboard paste) have verdicts. | Production media code. | — | S0-SKEL | 4 | W16–W17 | 10 §22 |
| S3-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-16 is measured on the emulator against the freeze-tip number. | — | S-16 | S3-AGENTSUI | 2 | W18–W19 | 02 §4 |

### 7.2 Lanes in S3 (55 ad)

TW: slice-4 rows red (two agents). FL: native-only variants approved by the owner only (02 §6.6). T3: E2E-17 needs AVD-S
and AVD-F (small RAM, full disk): book a full evening. FX: FX-LINUXDL and FX-AUTH running.

---

## 8. M4 — Slice 4: notifications, widgets, schedules, settings, styles, updater (W16–W22 on A, W11–W16 on B)

- **Finish line:** a reply that finishes in the background notifies through the short reply-watch service, answers can
  be given from the notification shade, tiles, widgets and pinned shortcuts work and survive a reboot or update,
  scheduled prompts run on time, every Settings page works with search, all five styles pass their contrast checks, and
  the in-app updater can install a newer native build.
- **Non-goal:** Glance widgets, new widget sizes (NG13), live backgrounds (D11), AppFunctions (D16).
- **Owner sees:** P3 "around the phone" (W22 on A): one image per style per mode, a video answering from the shade, both
  widgets on the emulator launcher. The preview's widgets and tiles appear with the preview app's own label.
- **Rows:** ≈ 220 [E] (ANDR 67, STATE 33, SEC 31, NAV 27, KIT 24, AGENT 16, CHAT 10, others 12).
- **Flows green at T3:** E2E-07, 08, 09, 21 (all steps), 22, 23, 25, 26, 27, 34, 36, 38.
- **T4:** S-13 transitions, S-15 idle CPU, S-17 hidden timers, S-18 reconnects, S-19 status updates.
- **Entry:** contract s4 frozen; S3-RTWIRE merged (reply watch and restore need it); R12-1 answered or defaulted.

### 8.1 Builder units (105 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S4-ATTNE | Codex | `native/engine/attention/**` | The attention feed, profile monitor, notification policy and copy keys, the one status-line combiner, live mode controller, pause policy and widget snapshot policies are green. | Android posting. | B-NAV-002…004, 010; B-PROTO-031, 046, 060, 083, 096…101, 127; STATE rows of map features 29–34 | S1-CONN, S2-TX@8 | 11 | W16–W18 | 04 WP-04.12; 11 §10 |
| S4-BG | Sonnet | `native/android/background/**`, receiver and service shells in `native/androidApp/**` | Channels (ids frozen), status row 4747, alerts, Live Updates for runs the person started, reply watch (S11-4), both tiles, notification actions, pinned shortcuts and boot/update restore through `SystemEventRestorer` (S11-11) work; E2E-07, 08, 09 pass. | Widgets. | B-ANDR-056…064, 103…107, 118, 119; B-SEC-180; ANDR rows of map features 02–09, 15, 30 | S4-ATTNE@5, S3-RTWIRE | 11 | W17–W19 | 11 §9, §10, §14, §15 |
| S4-SCHED | Codex (engine) + Sonnet (Android) | `native/engine/schedule/**`, `native/android/schedule/**` | One recurrence implementation with DST golden tests, the CAS store contract, claims `id:revision:dueAtMs`, receipts and the runner work under AlarmManager and WorkManager with the same worker class name; E2E-27 passes. | Schedules UI. | ANDR rows of map features 35–41; STATE 53 | S2-DLV, S1-CONN | 11 | W16–W19 | 11 §12 |
| S4-WIDGETS | Sonnet (Opus reviews the launcher traps) | `native/android/widgets/**` | Conversations 4×2 and Status 2×2 redraw from the native snapshot with RemoteViews, render-once sizing and `previewImage`; widget actions run in a 30 s short FGS through `ConnectionRegistry.oneShot` (CR11-6); E2E-26 passes. | Glance, new sizes. | ANDR rows of map features 17–28 | S4-ATTNE@6 | 11 | W18–W20 | 11 §11; memory `zte-launcher-widget-traps` |
| S4-PANELS | Sonnet | `native/feature/widget-panels/**` | Style, project, steer and the voice-panel shell open in `WidgetOverlayActivity` with `FLAG_SECURE`. | Voice capture in the panel (S5-VOICE). | NAV rows of screens-nav 100 | S4-WIDGETS@6 | 4 | W20 | 07 §5 M |
| S4-SETTINGS | Sonnet | `native/feature/settings/**` (not appearance, voice, providers) | The Settings hub, groups and search, notifications/background/Keep running/automation, the smaller pages and the per-server page work; E2E-22 passes. | Appearance, voice, providers pages. | NAV rows of screens-nav 75, 90, 92, 93 | S1-ENG, S2-KITB | 11 | W18–W21 | 07 §5 I |
| S4-APPEAR | Opus (kit owner) | style and appearance files of `native/ui/kit/**`, appearance files of `native/feature/settings/**` | Soft, Brutal, Paper and Signal with Fine-tune and Saver pass `StyleContrastTest` over every style × knob × pack × brightness; the guided Appearance page and the Make it yours tour work; E2E-23 and E2E-38 pass. | Live backgrounds and scenes (D11). | KIT rows of ui-kit 14–16, 19, 20, 39 | S1-KITA1 | 15 | W19–W22 | 06 K4 |
| S4-PAGES | Codex + Sonnet | review files of `native/engine/project/**`, `native/feature/chat-pages/**` | Undo from here (both shapes), Review the undo, context pages, export, the note, relations and the approvals sheet work. | Files hub (S5). | CHAT-GENUI 57, 58, 60, 61, 64, 65 rows; STATE 27 | S2-CHAT@6 | 10 | W19–W21 | 09 §17 S4-PAGES |
| S4-ABOUT | Sonnet | `native/feature/about/**` | Report a problem (counts only), diagnostics, about, guide, tips and the Updates page work; E2E-34 passes. | — | NAV rows of screens-nav 102–104; diagnostics XPLAT and SEC rows | S1-ENG | 5 | W19–W20 | 07 §5 L |
| S4-UPDATER | Sonnet (Opus reviews) | update files of `native/android/platform/**` and `native/engine/diagnostics/**` | The native `AppUpdater` reads `releases/latest`, downloads the ABI asset, checks `SHA256SUMS`, the same signer, the package and a higher versionCode, and installs through `PackageInstaller`; the notice, gear badge and sheet follow the two-layer design. | Updating managed servers' pins (runtime work). | B-ANDR-068, B-MIGR-012 (amended) | S1-NET, S1-ENG | 6 | W20–W21 | ADR §9.9; app-updates spec |
| S4-SCHEDUI | Sonnet | `native/feature/schedules/**` | The scheduled-prompt list, editor and "send later or repeat" work. | — | NAV 98; CHAT-GENUI 39 rows | S4-SCHED@6 | 4 | W20–W21 | 07 §5 K |
| S4-ANALYTICS | Codex | `native/engine/analytics/**` (new module after C15-9) | One event API with BASIC and DETAILED tiers, a local sink in diagnostics, a sender interface with **no sender bound**, and privacy tests (never content, paths, addresses or credentials). | Any vendor SDK; any network send until R12-1 names a destination. | new SEC rows (top-up) | S1-ENG | 4 | W21 | memory freeze decision 6; 12 R12-1; 10 §19.5 |
| S4-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-13, S-15, S-17, S-18 and S-19 are measured on the emulator. | — | — | S4-BG | 2 | W21 | 02 §4 |

### 8.2 Lanes in S4 (45 ad)

TW: slice-5 rows red (two agents). T3: E2E-26 also on the owner-launcher rules (11 §11.4); E2E-25 with `adb` intents;
the independent visual auditor reviews every style after S4-APPEAR merges (02 §7.10).

---

## 9. M5 — Slice 5: project, media and integrations (W21–W28 on A, W14–W21 on B)

- **Finish line:** every remaining in-scope feature works on the preview flavor: files, Git with GitHub sign-in,
  terminals, dev previews, voice typing, read aloud, viewers including PDF, Wi-Fi share, Codex, agent cards, MCP,
  providers, usage, the Linux manager with services, vitals and backup, Paseo's second-tier features, search and the
  Termux move-out.
- **Non-goal:** anything named "after the switch" in §13 (AI Team, Memory, AppFunctions, the Gemma assistant, Glance).
- **Owner sees:** P4 feature-complete (W28 on A), with a request to use it daily beside the Flutter app for a week and
  list every difference noticed.
- **Rows:** ≈ 402 [E] (AGENT 99, LINUX 64, SEC 57, CHAT 48, MEDIA 29, STATE 27, PROTO 26, NAV 18, ANDR 16, BUILD 11,
  others 7).
- **Flows green at T3:** E2E-13, 14, 15, 16, 19, 20, 24, 29, 33, 35, 37.
- **T4:** S-14 size gate; the media budgets of 10 §18.
- **Entry:** contract s5 frozen; S3-KCCONTENT, S3-REQFORMS, S3-AGENTSE, S3-RTWIRE merged.

### 9.1 Builder units (160 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S5-CODEX | Codex | `native/protocol/codex/**`, Codex files of `native/feature/{servers,agents}/**` | The Codex app-server client connects, lists threads, runs turns with approvals, steers, aborts and shows the account; E2E-29 passes. | — | Codex parts of B-AGENT-051…070, 220 | S1-NET | 6 | W21–W22 | 04 WP-04.13, §9 |
| S5-OC2MORE | Codex | the rest of `native/protocol/oc2/**` | PTY, shells, files, MCP, integrations, plugins, web search and usage operations are green; a PTY reconnect resumes at the right cursor. | — | B-PROTO-022, 026, 030, 035, 036, 049, 058, 059, 078…080, 082, 084…087, 104…108, 121…123, 125, 126 | S1-OC2 | 7 | W21–W23 | 04 WP-04.13 |
| S5-PROJE | Codex | `native/engine/project/**` (not review) | The project hub, files, the Git page model with the GitHub device flow, staged revert, the terminal session model and dev-preview rules are green. | UI. | Git and project rows | S1-CONN | 10 | W21–W24 | ADR §5.2 |
| S5-PROJUI | Sonnet | `native/feature/project/**` (not viewer or share files) | The Files hub, browser, review and diff, Git sheet, server terminal, dev preview and project tools work; E2E-14, 15, 16 pass. | — | NAV rows of screens-nav 63–70 | S5-PROJE@5, S3-KCCONTENT | 11 | W23–W25 | 07 §5 D |
| S5-TERMINAL | Sonnet | `native/android/terminal/**`, terminal files of `native/ui/kit-content/**` | `:android:terminal` is carved out of `:android:linux`; the live terminal slot has the key bar, OSC 52 and links; local and server shells outlive the screen. | — | LINUX 50; XPLAT 30 rows | S3-TERMMIN, S5-OC2MORE@4 | 7 | W23–W24 | 06 KN5; 11 §1.2 |
| S5-KC2 | Sonnet (kit-content owner) | `native/ui/kit-content/**` | The diff view and page, viewer, gallery, markup, zoom, web preview slot, charts and tables, session tabs, breadcrumb, mic hold and level meter meet 06 §16.3. | — | content KIT rows; B-KIT-026 | S3-KCCONTENT | 13 | W22–W24 | 06 KN5; 10 MV-3 |
| S5-MEDIALOGIC | Codex | `native/engine/media/**/{voice,models,setup,readaloud,conversation,share,qr}/**` | The pure voice, models, read-aloud, conversation, share and QR rules are green. | Android audio. | pure parts of B-MEDIA-002, 003, 005, 011, 013…016, 018, 021, 023…025, 028…031, 034 | S0-CORE | 10 | W22–W24 | 10 MV-1b |
| S5-VOICE | Opus (`VoiceController`, `AudioSession`) + Sonnet | `native/android/media/**/{audio,speech}/**`, `native/engine/media/**/voice/**`, voice files of `native/feature/{chat-composer,widget-panels}/**` | Dictation, hold to talk, send when I stop talking and the microphone-permission recovery work on the emulator. | Read aloud. | B-MEDIA-010, 017, 020, 023, 033 | S5-MEDIALOGIC@5, S3-MEDIAPROOF | 12 | W24–W26 | 10 MV-5 |
| S5-MODELS | Codex + Sonnet | `native/engine/media/**/{models,setup}/**`, `native/android/media/**/device/**`, voice files of `native/feature/settings/**` | Model packs, the total-RAM gate, the resumable verified download, Settings › Voice typing and the model sheet work; E2E-13 passes. | — | B-MEDIA-012, 019…022, 025…027, 030, 032; B-ANDR-101, 102; B-L10N-024; B-LINUX-220 | S5-MEDIALOGIC@5 | 10 | W24–W26 | 10 MV-6 |
| S5-READ | Opus (reply watch, conversation) + Sonnet | `native/android/media/**/tts/**`, `native/engine/media/**/{readaloud,conversation}/**`, read-aloud files of `native/feature/chat/**` | System and Kokoro read aloud, consent, voice choice and the voice conversation loop work; Kokoro stops at once. | Background audio (D10-5). | B-MEDIA-005, 009, 018 | S5-VOICE@6, S5-MODELS@5 | 10 | W25–W27 | 10 MV-7 |
| S5-VIEWER | Sonnet (Opus reviews PDF) | viewer files of `native/ui/kit-content/**` and `native/feature/project/**`, `native/android/media/**/pdf/**`, `LocalPdfService.kt` in `native/androidApp/**` | One viewer with every body kind, find, reader preferences, the isolated PDF service, and Save, Copy, Share and Open with. | — | B-MEDIA-004; B-NAV-028 | S3-MEDIAATT, S5-KC2@5 | 10 | W25–W27 | 10 MV-4 |
| S5-WIFI | Codex + Sonnet | `native/engine/media/**/share/**`, `native/android/media/**/share/**`, share-sheet files of `native/feature/project/**` | Wi-Fi share with the token server, the QR and link sheet, and chat file links work. | — | B-MEDIA-034; B-KIT-026 | S5-MEDIALOGIC@5 | 6 | W25–W26 | 10 MV-8 |
| S5-CARDS | Codex + Sonnet | card files of `native/engine/{requests,agents}/**`, `native/ui/kit-chat/**` and `native/feature/chats/**` | The cards contract works end to end: validator, states, answers sent exactly once with Undo, journal, recovery, install and verify for Claude Code, and the connector card. | OpenCode 2 card qualification beyond today's. | B-CHAT-029…053 | S3-REQFORMS, S3-AGENTSE | 13 | W22–W25 | 09 §17 S5-CARDS-*, S5-CONNECT |
| S5-INTEG | Codex + Sonnet | `native/engine/integrations/**`, `native/feature/usage/**`, providers, MCP and tools files of `native/feature/settings/**` | MCP, providers (keys never echoed), usage and quota, and BYO host work; E2E-33 and E2E-37 pass. | — | AGENT rows of map features 58–60; STATE rows 42, 45, 50 | S5-OC2MORE@3 | 10 | W25–W27 | ADR §5.2 |
| S5-LINUXMGR | Codex + Sonnet | manager files of `native/engine/runtime/**` and `native/feature/phone/**` | The Linux hub, Running, Ports, storage, packages, reset, services supervisor, vitals and backup work; E2E-19 and E2E-20 pass. | The designed-not-built items (builtin-linux 49). | LINUX rows of map features 40–48 | S3-RTWIRE | 9 | W26–W28 | 11 §1.2; C15-12 |
| S5-PASEOP1 | Codex + Sonnet | P1 files of `native/protocol/paseo/**` and `native/engine/agents/**` | Claude sub-agents, background tasks, files and changes over Paseo, import from Claude Code, the computer daemon update, plan usage and Continue with… work. | — | AGENT rows of map features 16, 26–32 | S3-AGENTSE | 7 | W26–W28 | agents-backends §2 tiers |
| S5-SEARCH | Sonnet | `native/feature/search/**`, shortcut files of `native/ui/shell/**` | The command launcher, Go to, the settings-search entry, keyboard shortcuts and the offline demo work; E2E-24 passes. | Desktop-only interaction. | NAV rows of screens-nav 10, 11, 61 | S4-SETTINGS | 4 | W27 | 07 §9 |
| S5-TERMUXOUT | Sonnet | `native/android/termux/**` | Moving from Termux into the built-in Linux works (migrate-out only, D7). | Termux as a host. | LINUX 63 rows | S3-RTWIRE | 3 | W27–W28 | 11 §1.2 |
| S5-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-14 and the 10 §18 media budgets are measured. | — | — | S5-VOICE | 2 | W27–W28 | 10 §18 |

### 9.2 Lanes in S5 (45 ad)

TW: the last red rows plus slice-6 MIGR rows. T3: E2E-13 needs the microphone and network control; E2E-15 needs FX-GH
and FX-GIT. T4: size and media budgets.

---

## 10. M6 — Slice 6: migration hardening and the switch (W28–W30 on A, W18–W22 on B)

- **Finish line:** the switch candidate installs over Flutter 2.1.0+57 on both lineages (and over the bridge release),
  keeps every stored value, secret, widget, tile, shortcut, schedule and the built-in Linux, passes all 37 switch flows
  on the release build and every S-criterion, and the owner's two-day phone test passes.
- **Non-goal:** any new feature; the Room move (switch+1 unless the P5 trigger fired).
- **Owner sees:** a video of the upgrade journey on the emulator (Flutter → native, everything kept), then the switch on
  their own phone (§11.3).
- **Rows:** ≈ 18 [E] (MIGR 13, others 5) plus every row still red or todo.
- **Flows:** E2E-39 green; all 37 switch flows green on the candidate's release build.
- **T4:** S-5…S-21 on the candidate; NX721J numbers with the owner's OK.

### 10.1 Builder units (40 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S6-MIGRHARD | Opus (migration owner) | `native/android/migration/**`, `native/e2e/upgrade/**`, `native/tools/flutter-fixtures/**` | J1–J8 pass on the candidate for both lineages and from the bridge release, including the media step and R2; E2E-39 is green. | Room v2. | B-MIGR-001…013; 05 A1–A7 | S2-MIGRRUN, S4-WIDGETS, S4-SCHED | 9 | W28–W29 | 05 §10.5, W6, W8; 10 MV-10 |
| S6-R1 | Sonnet | branch `rollback/flutter-final` of the frozen Flutter tree | The R1 build (native + 1, owner lineage) opens all data and J4 passes. | — | J4 | S6-MIGRHARD@4 | 2 | W29 | 05 §9.3, W9 |
| S6-COMPAT | Sonnet | `FlutterFormatCompat*` tests in `native/androidApp/**` and on the rollback branch | `FlutterFormatCompatTest` is green on the candidate. | — | — | S6-MIGRHARD@4 | 3 | W29–W30 | 05 §10.6, W10 |
| S6-PERF | Sonnet (T4) + Opus for fixes | `native/android/benchmark/**`; fixes through module owners | S-5…S-21 are measured on the candidate; profiles regenerated. | New features. | S-5…S-21 | S5-READ, S5-PROJUI | 6 | W28–W29 | 09 §17 S6-PERF; 02 §4 |
| S6-AUDIT | Opus (independent auditor; never edits) | `docs/qa/native/audit-<date>/**` | Every screen × style × light/dark, plus Saver, high contrast and RTL, is reviewed and findings are filed to owners; the 08-M7 motion evidence pass is done. | Editing code. | — | S4-APPEAR, S5-PROJUI, S5-VIEWER | 6 | W28–W29 | 02 §7.10; 08 §11 |
| S6-A11Y | Sonnet | `docs/qa/native/a11y-<date>/**`; fixes through module owners | The TalkBack pass and the 200 % text and Arabic capacity sweeps are clean. | — | a11y XPLAT and KIT rows | S5-PROJUI | 3 | W28–W29 | desktop-web-cross 25–33 |
| S6-RELEASE | Sonnet + coordinator | `native/tool/release/**`; repo-root `.github/workflows/native-release.yml`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md` (coordinator exception to 02 §7.2) | The native release pipeline produces `opencode-mobile-<v>-arm64-v8a.apk`, `…-x86_64.apk`, `…-armeabi-v7a.apk` (if D15-8 keeps it), the universal `opencode-mobile-<v>.apk`, `SHA256SUMS` and notes in today's format; PRIVACY.md is rewritten; the notices include proot, talloc and shmem (R12-8); the component freeze list is final. | Publishing (owner GO only). | BUILD rows of slice 6 | S4-UPDATER | 6 | W28–W29 | packages-build 29–34, 40 |
| S6-SWEEP | Sonnet ×2 | inside owning modules, through their owners | No switch-scope row is red or todo. | — | whatever remains | S5 done | 5 | W28–W29 | 02 §6.7 |

---

## 11. The switch-over plan

### 11.1 The bridge release (ADR D4; 02 §5.7)

| Item | Value |
|---|---|
| What | The Flutter freeze line after its gate passes, which already holds the native-style installer (`feat/app-updates-2026-10-31`, merged at `6b686bb7c`), plus the counts-only migration census if M-D2 is yes (05 W13, unit S0-BRIDGE). |
| Version | public `2.2.0+58` [E]; the owner-lineage local build gets the next number above the phone's installed 43xx. Both below 10000. |
| How | A **new Shorebird release, not a patch** (native code changed; memory `shorebird-is-mandatory`), through `scripts/release.sh`. Signed exactly as today: public lineage `842284B2…1C` by CI, owner lineage `1DE5BF08…D60C` locally (D2). |
| When | **W3** (Nov 16–22), right after the freeze gate, so as many installs as possible carry the Update button long before the switch (D15-2). |
| Needs | the owner's explicit GO for each of: snapshot commit to master, push, CI quality run, tag, release run (dispatched by hand: a `[skip ci]` snapshot commit does not start the tag workflow, memory `release-2.1-prep`), publish. |
| Proof before GO | on the emulator: the bridge's updater offers a dummy native APK built with versionCode 10000 and the native asset names, verifies `SHA256SUMS` and the signer, and hands it to `PackageInstaller` (S0-BRIDGE). |
| After the switch | a Shorebird patch to the bridge only for a security fix that affects people who have not updated, with the owner's OK. |

### 11.2 The switch candidate: gates, in order

1. **T2 green** on the candidate commit: all JVM and Robolectric tests, Konsist, lint, `assembleRelease` both flavors,
   `analyzeReleaseR8Config`, size gate (S-14), component freeze, `BannedDependenciesTest` (S-23), `ScopeFileTest`,
   `:quality:catalogReport` showing **0 red and 0 todo** in switch scope (S-1).
2. **T3 green** on the candidate's release APK: the 37 switch flows (S-2), J1–J8 for both lineages and from the bridge
   (S-3, S-4), monkey 3 seeds × 50,000 events with 0 crashes and 0 ANRs (S-21), the canary privacy scan (S-22).
3. **T4 within budget**: S-5…S-20 on `OC_API37` relative to the Flutter baseline; NX721J absolute numbers with the
   owner's OK.
4. **Switch-ready checklist** (02 §6.7): every deferral and drop names an owner decision; PRIVACY.md rewritten; notices
   complete; D18 registration status written down.
5. **Identity checks**: `aapt2 dump badging` shows package `io.github.eslamasabry.opencode_mobile`, versionCode ≥ 10010
   (owner candidate), versionName `3.0.0`; `apksigner verify --print-certs` prints exactly one signer equal to the
   lineage's SHA-256; every ABI split carries the same versionCode.

### 11.3 The owner's phone on switch day

Follow 05 §10.7 step by step. In short: read-only checks of the installed versionCode and signer (must be the owner
lineage `1DE5BF08…D60C`; if the phone carries the public lineage, stop and ask the owner, because the signers differ and
an install would need an uninstall); J1–J3 green on the emulator for this exact APK; the owner closes the Flutter app
normally (never force-stop); `adb install -r` while the owner is present; the owner opens the app and types any password
the re-enter sheet asks for; Report a problem (counts only) is compared with the bridge census; read-only check that the
phone server came back; a reboot within a day. Then the **two-day daily-use test** (S02-4). The coordinator uses
read-only adb only and takes no screen captures.

### 11.4 Release and staged rollout

GitHub has no percentage rollout. Its `GET /repos/{owner}/{repo}/releases/latest` returns "the most recent
non-prerelease, non-draft release" [V, docs.github.com], and the bridge's updater reads exactly that endpoint [R
app-updates SPEC]. So the stages are:

| Stage | Who gets 3.0.0 | How | Lasts | Go to the next stage when | Halt |
|---|---|---|---|---|---|
| 0 | the owner's phone (owner lineage) | `adb install -r` (§11.3) | 2 days of daily use | the owner says "fine" and no S-1…S-4, S-21…S-23 issue is open | fix forward (R0); R1 if data looks wrong |
| 1 | people who opt into pre-releases (Obtainium with pre-releases on, manual download) | CI builds the public lineage from the same commit and versionCode; published as a GitHub **pre-release** (owner GO for push, CI, tag, dispatch, publish) | 7 days | no blocker reported; Report a problem counts look normal | delete the pre-release assets or mark the release draft; nobody on the bridge sees it |
| 2 | everyone on the bridge release | flip the release from pre-release to **latest** (owner GO) | — | — | flip it back to pre-release: `releases/latest` then returns the bridge again and the in-app notice stops offering 3.0.0; installed native apps are fixed forward |
| 3 | people still on releases before the bridge | they see nothing in-app (no updater); release notes and the site say "download 3.0.0" | — | — | — |

The site (`docs/site/`) and the release notes change only at stage 2.

### 11.5 Rollback (05 §9 is the authority)

| If | Do | Notes |
|---|---|---|
| a bug, no data at risk | **R0 fix forward**: next versionCode (+10), through the in-app updater; on the owner's phone by adb while present | the default |
| data looks wrong on the owner's phone during R1's window | **R1**: install the Flutter rollback build (native + 1, owner lineage, built in S6-R1); everything written by the native app is read back; returning to native runs `R1.return` | owner lineage only by default (D15-6); valid only while `flutterReadable` is true |
| the migration failed on a device | **R2** in-app: "Restore data from before the update" on the failure screen or in safe mode, while `files/migration/flutter-v57/` exists | |
| a public release must stop spreading | stage-2 halt in §11.4 | GitHub release edit only |

R1 retires when the owner says the native app is fine, or when the Room import ships, whichever comes first (M-D1).
Nothing Flutter-era is deleted before switch+2, and then only with the owner's OK (M-D3).

### 11.6 What ends at the switch

- **Shorebird**: no code push in the native app (NG17). The native `AppUpdater` is the only update path.
- **The Flutter Android line**: tag `flutter-final-<version>`, cut `flutter/maint` (02 §5.8). The Flutter Linux desktop
  build keeps security fixes only until M9 (D14).
- **The Flutter tree on `native/main`**: removed in one commit **after stage 2** (C15-7); every catalog `sources` path
  stays readable at the tag. Then `master` is fast-forwarded to `native/main` with the owner's approval.
- **The preview flavor**: stays in the build as the place for future trials; its kit-tour entry is deleted.

### 11.7 Developer verification (ADR D18)

Google's page says verification "will be expanded globally for all apps on certified Android devices in 2027" [V,
developer.android.com/developer-verification, read 2026-11-01]; once it reaches a region, unregistered apps can be updated
only through a one-day advanced flow or adb [R android-surfaces §14], which would break the in-app updater. The owner
registers `io.github.eslamasabry.opencode_mobile` (and the preview package) with **every signer in use**: local
`1DE5BF08…D60C`, public `842284B2…1C`, and the CI test signer `2D010C21…D0EC` if any installed population carries it.
Deadline: before stage 2. This needs the owner's account and the $25 fee [R android-surfaces §14]; the coordinator checks
the page monthly (risk R15-18).

---

## 12. After the switch

| Phase | Starts | Contents | Size [E] | Gate to start |
|---|---|---|---|---|
| **M7 switch+1** | the week after stage 2 | Room `oc.db` import (05 W11) unless the P5 trigger already moved it; the targetSdk 37 slice (local-network permission UX, CT test against self-signed and pinned servers, the installer under the Android 17 `IntentSender` rules, reflection audit, RemoteViews budget; android-surfaces §16); Flutter tree removal; Kotlin 2.5 + AGP 9.5 + Gradle 9.8 bump together (ADR §8); first owner-chosen additions: shared pictures in and Direct Share (O-07-4, D10-2), "Scan from a picture" (D10-3) | 4–6 aw | switch at stage 2 |
| switch+2 | after M7 | v3 cleanup of Flutter-era copies (05 §9.5) | 0.5 aw | the owner's OK (M-D3) |
| **M8 AI Team + Memory** | after M7, in parallel with M9 (D15-7) | `:protocol:team`, `:engine:team`, `:feature:team`, `:android:team` (phone engine bridge with the release hash check), FactsBook; E2E-28 and E2E-32; AppFunctions (D16) and the Gemma assistant (02 §3.5) as separate units | 12–17 aw (team blocks 9–14 aw, proposal A §18) | ADR D6 |
| **M9 Linux desktop** | after M7 | `:desktopApp` shell, desktop bindings (prefs JSON, Secret Service, notifications, `xdg-open`, file drop), migration from the Flutter Linux build's `shared_preferences.json` and libsecret item, local `RuntimeHost` without proot, `.deb` + tarball + AppImage; desktop-phase rows of §14 | 6–10 aw | D14 accepted |
| **M10 iOS** | when funded | remote client only: iOS targets on, Darwin Ktor with leaf pinning, Keychain `SecretStore`, iOS kit variants, sherpa-onnx xcframework, push relay for "reply finished", WidgetKit optional, TestFlight | 12–18 aw (18–30 with widgets and push) | D13: a Mac or macOS CI, the Apple Developer Program, a push relay, a Liquid Glass decision, an iOS design pass over the top 10 screens |
| **MW widget tests** | only when the owner schedules it | step 1: classify a sample of 200 Flutter widget tests (ADR §4 row 18); then port by area on desktop and Robolectric harnesses | 15–25 aw [R proposal A §18] | the owner's words: "7400 for widgets can come later" |
| F-Droid submission | after M7 | Lite build first (D10-6); the "Tracking" anti-feature applies if BASIC analytics sends anything (R12-1) | 1–2 aw | the owner's OK |

Zero-cost preparation for MW that happens now: test tags equal Flutter `ValueKey` strings, and every screen takes
`(XUi, XActions)` so a widget test can render it with fakes (ADR §4 row 18).

---

## 13. Feature → slice table (all 700 map features)

Every map feature lands in exactly one slice or has a decision (02 §6.5). "After" = after the switch by a named decision;
"Desktop" and "iOS" = their phases; "Not ported" and "Dropped" name their decision. A feature that starts earlier lands
where it is complete; the owning unit is in §4–§10.

### 13.1 protocols (46)

| Lands in | Features (PROTOCOLS-nn) |
|---|---|
| S0 | 37, 38, 39, 42 |
| S1 | 01, 02, 03, 05, 06, 07, 08, 09, 10, 17, 18, 19, 20, 36, 44, 46 |
| S2 | 04, 11, 13, 15, 16, 22, 23, 35, 40, 45 |
| S3 | 12, 14, 24 |
| S4 | 25, 41 |
| S5 | 21, 26, 27, 28, 29, 30, 31, 32, 33, 34, 43 |

### 13.2 state-data (55)

| Lands in | Features (STATE-DATA-nn) |
|---|---|
| S1 | 04, 10, 11, 35 |
| S2 | 01, 02, 03, 09, 12, 13, 14, 18, 19, 20, 24, 25, 28 |
| S3 | 05, 15, 16, 17, 21, 22, 23, 26, 34, 38, 46, 52 |
| S4 | 06, 27, 29, 30, 31, 32, 33, 36, 37, 39, 53, 54 |
| S5 | 08 (the sweep completes once every store exists), 40, 41, 42, 43, 45, 47, 49, 50, 55 |
| After (D6) | 07, 44, 51 |
| Dropped (D6) | 48 |

### 13.3 agents-backends (63)

| Lands in | Features (AGENTS-BACKENDS-nn) |
|---|---|
| S3 | 01–15, 19–25, 33, 38, 42, 63 |
| S4 | 43, 44, 45 |
| S5 | 16, 26–32, 34, 35, 36, 37, 58, 59, 60, 61, 62 |
| After (D8) | 40, 41 |
| After (D6) | 47, 48, 49, 50, 51, 52, 53, 55, 56, 57 |
| Not ported (agents-backends §2) | 17, 18, 39, 54 |
| Dropped (D6) | 46 |

### 13.4 builtin-linux (64)

| Lands in | Features (BUILTIN-LINUX-nn) |
|---|---|
| S3 (moved green in S1) | 01–31, 33–38, 44, 51, 52, 53, 54 |
| S4 | 32 (reply watch, with S4-BG) |
| S5 | 39, 40, 41, 42, 43, 45, 46, 47, 48, 50, 57, 58, 59, 60, 62, 63 |
| After | 49 (designed, not built), 55, 56, 61 (D6) |
| Not ported (NG7) | 64 |

### 13.5 ui-kit (58)

| Lands in | Features (UI-KIT-nn) |
|---|---|
| S1 | 01–07, 09–13, 21–24, 26–31, 33–38, 40, 41, 57, 58 (replaced by the `:desktopApp` gallery) |
| S2 | 08, 18, 25, 32, 43, 45, 51 |
| S3 | 42, 50, 52 |
| S4 | 14, 15, 16, 19, 20, 39, 56 |
| S5 | 44, 46, 47, 48, 49, 53 |
| After | 17, 55 (D11); 54 (D6) |

### 13.6 screens-nav (112)

| Lands in | Features (SCREENS-NAV-nn) |
|---|---|
| S1 | 04, 23, 26, 105, 106, 107, 108, 109 |
| S2 | 01, 02, 03, 05, 06, 07, 08, 09, 16, 17, 21, 24, 27, 29, 30, 34, 35, 37, 38, 39, 40, 41, 42, 43, 50, 71, 72, 73 |
| S3 | 20, 28, 44, 46, 47, 48, 49, 51, 57, 62, 74, 76, 77, 78, 79, 82, 83, 84, 110, 111 |
| S4 | 14, 15, 18, 19, 22 (secondary Flutter entrypoints disappear), 25, 36, 54, 55, 56, 75, 90, 91, 92, 93, 98, 100, 102, 103, 104, 112 |
| S5 | 10, 11, 13, 31, 32, 33, 45, 52, 53, 58, 59, 61, 63, 64, 65, 66, 67, 68, 69, 70, 80, 81 (Termux move-out only), 94, 95, 96, 97, 101 |
| Desktop | 12 |
| After (D6) | 60, 86, 87, 88, 89, 99 |
| Dropped (D6) | 85 |

### 13.7 chat-genui (81)

| Lands in | Features (CHAT-GENUI-nn) |
|---|---|
| S2 | 01–11, 13, 14, 16, 21, 23, 32, 33, 34, 40, 43, 44, 50, 51, 52, 53, 54, 55, 79 |
| S3 | 12, 17, 20, 22, 25, 26, 27, 28, 29, 30, 31, 35, 36, 37, 45, 46, 47, 48, 81 |
| S4 | 39, 49, 56, 57, 58, 60, 61, 64, 65, 66, 69 |
| S5 | 15, 18, 19, 24, 38, 41, 42, 59, 62, 63, 70–78, 80 |
| After (D6) | 67 |
| Desktop | 68 |

### 13.8 media-voice (50), as section 10 §1 assigns them

| Lands in | Features (MEDIA-VOICE-nn) |
|---|---|
| S3 | 25, 26, 27, 28, 29, 30, 32, 33, 34, 46, 48 |
| S4 | 36 |
| S5 | 01–15, 17–24, 35, 37–45, 47, 49, 50 |
| S6 | 16 (voice licences, with the notices) |
| Desktop | 31 |

### 13.9 android-platform (65)

| Lands in | Features (ANDROID-PLATFORM-nn) |
|---|---|
| S0 | 56 |
| S1 | 01, 42, 45, 48 |
| S2 | 29, 32, 33, 34 |
| S3 | 11, 12, 13, 16, 43, 44, 54, 62, 63 |
| S4 | 02–09, 15, 17–28, 30, 31, 35–41, 60 |
| S5 | 14, 46, 47, 49, 50, 51, 52, 53, 55, 58 (closes the pure-logic sweep), 64, 65 |
| S6 | 57 (packaging facts, verified on the candidate) |
| After | 10 (D6), 59 (D16), 61 (02 §3.5) |

### 13.10 packages-build (41)

| Lands in | Features (PACKAGES-BUILD-nn) |
|---|---|
| S0 | 01, 02, 04, 08, 11, 35, 36, 41 |
| S1 | 05, 10, 12, 13, 21, 24, 26, 28 |
| S2 | 06, 07 |
| S3 | 15, 18, 19, 22 |
| S4 | 09, 20, 25, 27, 30, 39 |
| S5 | 14, 16, 17 |
| S6 | 03, 23, 29, 31, 32, 33, 34, 40 |
| Desktop / iOS | 37 |
| Dropped at the switch | 38 (Flutter-only tooling) |

### 13.11 desktop-web-cross (65)

| Lands in | Features (DESKTOP-WEB-CROSS-nn) |
|---|---|
| S0 | 01, 36, 43, 62 |
| S1 | 21, 25, 27, 28, 31, 32, 34, 35, 37, 38, 39, 40, 48, 51, 52, 55, 65 |
| S2 | 26, 53, 59, 64 |
| S3 | 18, 57 |
| S4 | 10, 29, 33, 45, 46 (as R12-1 decides), 54, 56, 58, 60, 63 |
| S5 | 12, 13, 19, 23, 30, 41, 42, 50, 61 |
| S6 | 47 (egress inventory test), 49 (privacy documents) |
| Desktop | 02, 03, 04, 05, 06, 09, 11, 14, 15, 16, 17, 20, 22 |
| iOS | 08 |
| Not at the switch (decision: no app lock; biometric stable is still 2021, android-surfaces §0) | 44 |
| Dropped (D17) | 07, 24 |

### 13.12 Counts

| | S0 | S1 | S2 | S3 | S4 | S5 | S6 | After | Desktop/iOS | Not ported/dropped | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Features | 17 | 89 | 97 | 151 | 105 | 167 | 12 | 33 | 18 | 11 | **700** |

---

## 14. Rows and flows by slice

### 14.1 Rows (estimate; the scope file is the truth)

Computed today by Appendix B, which reads each row's first Flutter source file name. The coordinator runs it in S0-SCOPE
to seed `slice` in `native/catalog/scope.jsonl`, then checks the 15 MANUAL rows and every AFTER and DROP row by hand,
and adds `module` per row.

| Bucket | Rows | Of which device-flow rows | By area |
|---|---|---|---|
| S0 | 12 | 1 | BUILD 6, XPLAT 3, ANDR 2, SEC 1 |
| S1 | 132 | 26 | PROTO 35, SEC 33, KIT 27, L10N 12, STATE 12, ANDR 5, NAV 5, AGENT 1, CHAT 1, MEDIA 1 |
| S2 | 133 | 29 | STATE 45, CHAT 30, PROTO 17, NAV 16, ANDR 7, SEC 6, BUILD 4, KIT 4, AGENT 2, L10N 2 |
| S3 | 342 | 47 | LINUX 123, AGENT 79, CHAT 34, STATE 34, SEC 24, ANDR 21, PROTO 11, NAV 8, MEDIA 3, BUILD 2, L10N 2, KIT 1 |
| S4 | 220 | 55 | ANDR 67, STATE 33, SEC 31, NAV 27, KIT 24, AGENT 16, CHAT 10, PROTO 6, L10N 3, LINUX 3 |
| S5 | 402 | 43 | AGENT 99, LINUX 64, SEC 57, CHAT 48, MEDIA 29, STATE 27, PROTO 26, NAV 18, ANDR 16, BUILD 11, XPLAT 3, KIT 2, L10N 2 |
| S6 | 18 | 10 | MIGR 13, SEC 2, BUILD 2, ANDR 1 |
| After (D6 and similar) | 206 | 17 | AGENT 111, LINUX 38, PROTO 23, SEC 17, STATE 6, KIT 4, others 7 |
| Dropped or not ported | 50 | 7 | LINUX 19, SEC 11, PROTO 9, AGENT 7, others 4 |
| Desktop / iOS | 14 | 0 | BUILD 7, XPLAT 7 |
| MANUAL (triage in S0-SCOPE) | 15 | 5 | |
| **Total** | **1,544** | **240** | |

In switch scope: 1,259 + up to 15 MANUAL ≈ 1,274, against 02 §3.3's independent estimate of 1,294. Both are [E].

### 14.2 Flows (the 37 switch flows; each must be green on Flutter R first)

| Slice | Flows that turn green on the native release build |
|---|---|
| S2 | E2E-01 first launch, E2E-03 failures in plain words, E2E-04 transport trust, E2E-05 send/stream/stop/find/links, E2E-10 network loss and never twice |
| S3 | E2E-02 pairing incl. QR, E2E-06 approve/deny/always-allow, E2E-11 drafts and queued prompts survive, E2E-12 attachments, E2E-17 Linux setup, E2E-18 the phone's server, E2E-30 Claude Code and Pi via Paseo, E2E-31 sign-in terminal |
| S4 | E2E-07 answer from the shade, E2E-08 needs-you and finished alerts, E2E-09 keep running, E2E-21 Conversations (all steps), E2E-22 settings search, E2E-23 language, text size and look, E2E-25 shortcuts, tile, links, share-in, E2E-26 widgets, E2E-27 scheduled prompts, E2E-34 privacy, E2E-36 several servers, E2E-38 tips and tour |
| S5 | E2E-13 voice, E2E-14 files and sharing, E2E-15 Git and GitHub, E2E-16 terminals, E2E-19 remove Linux and free space, E2E-20 dev servers and ports, E2E-24 launcher and Go to, E2E-29 Codex, E2E-33 plan usage, E2E-35 remove a server and its data, E2E-37 providers |
| S6 | E2E-39 upgrade in place |
| After (D6) | E2E-28 AI Team, E2E-32 Memory |

Counts: 5 + 8 + 12 + 11 + 1 = 37 (02 §3.4).

---

## 15. What the owner sees each week (calendar A; calendar B in the last column)

Every message follows the owner's rules: a few lines, then "Decision needed: yes/no" (memory `short-replies-rule`); one
full phone screen per image with one sentence (`one-screen-per-image`); feature proofs as short real recordings
(`live-video-proof`); an APK only after an emulator sweep (`stabilize-before-shipping`, `test-on-emulator-not-phone`).

| Week | Dates | What happened (one line) | What you get | Decision asked | Calendar B |
|---|---|---|---|---|---|
| W1 | Nov 2–8 | skeleton and the ten proofs started; the Flutter freeze gate runs | one image: the native "Opening…" screen | D15 second build server; D1 confirm the plan | same |
| W2 | Nov 9–15 | proofs finished; M0 gate | video: back gesture with the grow-from-row motion + the 5,000-part streaming test | D4 bridge release GO (with D15-2 timing) | same |
| W3 | Nov 16–22 | re-plan done; bridge published if you said yes | the new dates in four lines; kit images (light, dark, Arabic) | R12-1 analytics destination (default: nothing sent) | B starts with 9 slots |
| W4 | Nov 23–29 | the 37 phone flows run green on today's app | one line: "37 of 37 flows pass on the Flutter app" (or the gaps) | none | |
| W5 | Nov 30–Dec 6 | the new engine talked to your OpenCode 2 on the VPS | video: kit tour on the emulator | 06 Q3/Q4 (date pickers, reply direction) defaults | |
| W6 | Dec 7–13 | words in 7 languages; the shell | images: Arabic and 200 % text | none | |
| W7 | Dec 14–20 | **M1** | **P0 kit tour APK** + what to try | "Does the feel match the demo? yes/no" | M1 |
| W8 | Dec 21–27 | Servers screen | images: Servers and Add server, native next to Flutter | O-07-1…3 defaults | |
| W9 | Dec 28–Jan 3 | Conversations list | images | none | |
| W10 | Jan 4–10 | the transcript engine streams | video: a reply streaming in the native chat (emulator) | 08 D8-1…4 defaults | |
| W11 | Jan 11–17 | chat and delivery | images: chat with code; find | none | **M2: P1 APK** |
| W12 | Jan 18–24 | steer, Stop, permission | video | 09 Q09-1…4 defaults | |
| W13 | Jan 25–31 | chat polish; J1–J3 numbers vs Flutter | one numbers line | D8 phone agents path (Paseo) | |
| W14 | Feb 1–7 | **M2** | **P1 "chat with your server" APK** | D15-4 second Linux in the preview on your phone | **M3: P2 APK** |
| W15 | Feb 8–14 | the runtime split | video: phone setup on the emulator | none | |
| W16 | Feb 15–21 | Paseo and agents | video: Claude Code via Paseo on the emulator | 11 §25 defaults | **M4: P3 APK** |
| W17 | Feb 22–28 | the full composer | images: chips, tray, attachments | none | |
| W18 | Mar 1–7 | phone pages, agents hub | images | none | |
| W19 | Mar 8–14 | **M3** | **P2 "phone + agents" APK** (or videos if D15-4 is no) | none | |
| W20 | Mar 15–21 | notifications and tiles | video: answering from the shade | none | |
| W21 | Mar 22–28 | widgets and schedules | images: both widgets on the launcher; video: a scheduled run | none | **M5: P4 APK** |
| W22 | Mar 29–Apr 4 | **M4** | **P3 "around the phone" APK**; one image per style per mode | 06 Q5 lens springs; D10 frost | **candidate; your phone** |
| W23 | Apr 5–11 | Codex, files | video: files and Git | none | **pre-release** |
| W24 | Apr 12–18 | terminal, cards | video: terminal; cards | none | **latest** |
| W25 | Apr 19–25 | voice | video: dictation and read aloud | 10 D10-1…11 defaults | |
| W26 | Apr 26–May 2 | Linux manager, integrations | images | M-D1…M-D8 migration defaults; D12 libraries at the switch | |
| W27 | May 3–9 | the last S5 units | images | none | |
| W28 | May 10–16 | **M5** | **P4 feature-complete APK**: use it daily for a week beside Flutter and list differences | R12-8 licence wording | |
| W29 | May 17–23 | switch candidate built | video: Flutter → native upgrade on the emulator, everything kept | D18 register the package and signers (your account) | |
| W30 | May 24–30 | **M6: the switch on your phone** (adb while you are there) | two-day daily use | "Switch went fine? yes/no" | |
| W31 | May 31–Jun 6 | public pre-release | release page link | GO: push, CI, tag, dispatch, publish | |
| W32 | Jun 7–13 | marked latest; the bridge's Update button offers 3.0.0 | — | GO: mark latest | |

---

## 16. Risk register

Likelihood and impact: H/M/L. "Signal" is what the coordinator watches for; "Owner" is the role that acts.

| # | Risk | L / I | Signal | Mitigation (now) | Fallback | Owner |
|---|---|---|---|---|---|---|
| R15-01 | Throughput is lower than planned; the switch slips past June | M / H | re-plan factor `f` < 0.8 at M0 or M2 | second VPS (D15); one-unit-one-agent discipline; contracts frozen before fan-out; TW lane ahead | the scope valve (§19), owner-approved | coordinator |
| R15-02 | CMP 1.13 / Material 3 1.5 are not stable by the switch | M / M | no stable by W20 (A) | D12 allows a beta M3 only inside the kit; CMP 1.13 stable is projected about mid-Dec 2026 [U, kmp-cmp-status §13]; GitHub shows 1.13.0-beta01 as the newest 1.13 build on 2026-11-01 [V] | CMP 1.12.2 + material3 1.12.0-alpha03 (demo-proven) for everything but the kit | build-logic owner |
| R15-03 | A toolchain bump mid-rewrite (Kotlin 2.5 is planned for Dec 2026) breaks Metro or CMP | M / M | a bump request in a brief | pins in ADR §8; Kotlin, the Compose compiler, Metro and KSP move together, only at a slice boundary with T2 + T4 | stay on 2.4.21 until switch+1 | build-logic owner |
| R15-04 | Predictive back fights the row → chat transform again | M / M | P2 checks fail | option A disables shared bounds during the gesture (ADR §9.4) | plain shared-axis until option B | Opus (S0-NAV) |
| R15-05 | Streaming janks on the owner's phone | L / H | S-9…S-12 miss at a slice-end T4 | P3/P4 in M0; the 50 ms publish; draw-phase fade; benchmarks at S2, S3, S6 | per-part `StateFlow<String>` (ADR §9.3) | chat lead |
| R15-06 | Silent data loss in the migration (secrets, prefs encodings, `DartJson` fingerprints) | L / H | any J1–J8 failure; audit count mismatch | real-install fixture; byte backup S0; per-entry re-enter flags; Opus owner | R2 restore; R1 rollback; fix forward | migration owner |
| R15-07 | An OS-visible name changes, so widgets, tiles, shortcuts or schedules vanish on update | L / H | `ComponentFreezeTest` red at T2 | the freeze list from the 2.1.0+57 APK; J1 checks placed widgets and tiles | — (blocks the switch) | coordinator |
| R15-08 | Built-in Linux behaviour drifts during the move or split | M / H | a copied test changes or fails | move-green first (S1-LINUX); one lock family; tests before edits; Opus split | revert the split, keep the copy | runtime owner |
| R15-09 | The one emulator throttles T3, flows and proofs | H / M | bookings full three days running | §2.5 rules; desktop and Robolectric first; journeys in the evening | a KVM-capable second test host [U]; the owner decides | coordinator |
| R15-10 | The PC runs out of memory and kills sessions | M / H | `free -m` < 7 GB; swap full | builds and tests on the VPS; lean Codex; `hog_watch.py` armed; ≤ 1 PC Gradle build | pause agents (owner decision if work stops) | coordinator |
| R15-11 | The VPS disk fills mid-run | M / M | `df -h /` < 30 GB before T2 | worktree cleanup after merge (02 §7.3); prune Gradle caches | Storage2 offload | coordinator |
| R15-12 | Codex quota runs out | M / M | quota warning in a session | Codex units are small and contract-bound | a Sonnet agent takes over the worktree with the commits and diff (02 §7.7) | coordinator |
| R15-13 | OpenCode 2, Paseo or Claude Code change under us | H / M | probe drift (P-N4); live proof failures | the dialect table; pins stay 2.1.1 / Paseo 0.11.2 at the switch; drift tests | re-record fixtures before the affected WP | backend (Codex) |
| R15-14 | Flutter fixes during the freeze are not mirrored | M / M | `twin_check.sh` lists unsynced commits | `Behaviors:` trailer on every Flutter commit; weekly twin check | — | coordinator |
| R15-15 | A copy change breaks the parity flows | M / L | a flow fails on text | asserted strings frozen; `native-only` variants need the owner's OK (02 §6.6) | — | l10n owner |
| R15-16 | Widget tests come later, so UI regressions slip past logic rows | M / M | the auditor or sweep finds a regression | per-part kit gates (06 Q2), Roborazzi, flows, weekly sweeps, the S6 audit | MW phase | kit owner |
| R15-17 | The owner rejects the look late | M / H | "no" at P0 or P1 | P0 kit tour early; side-by-side images weekly; Opus render-compare for premium screens (`judge-visuals-hard`) | redo the kit part, not the screen | kit owner |
| R15-18 | Developer verification reaches the owner's users before registration | L / H | the Google page changes | register before stage 2 (§11.7); check monthly | — | owner |
| R15-19 | A delivered APK has the wrong signer | L / H | `apksigner` mismatch in `t3.sh` | scripted check before any delivery; never substitute signers | stop delivery | T3 holder |
| R15-20 | The preview app clashes with the Flutter app on the owner's phone (ports, Linux, storage) | M / M | the preview's server fails to start | separate ports (S11-6); separate data; D15-4 asks first | emulator videos only | runtime owner |
| R15-21 | The analytics question stays open | M / L | R12-1 unanswered by W16 (A) | the core is built with no sender bound; nothing is sent | ship without a sender | coordinator |
| R15-22 | Android 17 rules (memory limiter, FGS, background audio) change behaviour on the owner's phone | M / M | exit reasons `MemoryLimiter:AnonSwap`; FGS refusals | 11 §7.3, §9; P-M5 | per-rule fixes at switch+1 | android owners |
| R15-23 | The Room trigger fires late | L / M | P5 numbers near the threshold | measure in M0 (P5), decide D9 at W3 | Room in S6 (+1.5 aw) | migration owner |
| R15-24 | The desktop target doubles build cost | M / M | P8 numbers | nightly-only desktop compile (ADR §4 row 17) | — | build-logic owner |
| R15-25 | The owner is away when a preview or the switch is ready (holidays) | M / M | no reply in 3 days | emulator-first; the switch waits, never ships untested | — | coordinator |
| R15-26 | Shared files (`Sheets.kt`, route files, string files) become merge hotspots | M / L | repeated conflicts | single-owner files; one string file and one route file per feature | — | coordinator |
| R15-27 | In-flight Flutter branches change the scope late (D16) | M / M | a new branch merged on the freeze line | decide each branch before S3; catalog top-up | port from the branch | coordinator |
| R15-28 | A security invariant regresses (link gate, redaction, cleartext) | L / H | B-SEC rows red; canary hits | Konsist bans; S-22 canary scan at T2 and T3 | blocks the switch | backend owner |
| R15-29 | The APK exceeds 22 MB arm64 | M / L | size gate at T2 | registry budget (12 §11); R12-6 sherpa static variant | owner relaxes S-14 | build-logic owner |
| R15-30 | 32-bit phones get no working update | M / M | the bridge offers `…-armeabi-v7a.apk` and the native release has none | D15-8: keep a 32-bit remote-only split as today | publish only universal and let 32-bit fall back | release owner |
| R15-31 | The OC2 critical chain (S1-OC2 → S2-TX → S2-CHAT) slips and P1 moves | M / H | S1-OC2 not merged by W7 (A) | S2-TX and S2-CHAT start on frozen contracts with fakes (`@k` dependencies) | P1 with a fixture server first | coordinator |

---

## 17. Owner decisions

### 17.1 The decision calendar (asked one or two at a time, in the weekly message)

Work proceeds on the default until the owner answers (ADR §12, 02 §11). "Ask" weeks are calendar A; under B ask the same
items in the same order, earlier.

| # | Plain question | Default | Ask | Detail |
|---|---|---|---|---|
| D15 | A second build server (about doubles agent slots: switch late May → early April)? | Yes, if budget allows | W1 | ADR D15; §2.3 |
| D1 | Confirm: iOS or Linux desktop within about a year (keeps one shared UI)? | Keep A | W1 | ADR D1 |
| D4 + D15-2 | Publish the bridge update right after the freeze gate (W3)? | Yes | W2 | §11.1 |
| R12-1 | Analytics: where may BASIC events go? | Nowhere until you choose; the core is built (C15-9) | W3 | 12 R12-1; memory freeze decision 6 |
| S02-1 / D03-1 | Native code in `native/` of the same repo? | Yes | W1 (inform) | 02 §11; 03 §24 |
| D2, D3 | Keep both signers; versionCode base 10000, name 3.0.0? | Yes | W2 (inform) | ADR D2, D3 |
| D5, D6, D7, D17 | Drop "press back again"; AI Team and Memory after the switch; Termux move-out only; no web, no Windows | As stated | W2 (inform) | ADR §12 |
| S02-2…4 | The numbers in 02 §4; the freeze-tip delta; a two-day phone test | Yes | W2 (inform) | 02 §11 |
| D9 | Room timing after the P5 numbers | Switch+1 unless triggered | W3 | ADR §9.8 |
| N4-O1…O5 | Retire direct-ACP and OpenCode 1 rows; pin 2.1.1; retire session-address links; fix receipts natively only | Yes to all | W3 (inform) | 04 §19 |
| D10 | Frost on composer and chat header | Keep | W5 | ADR D10 |
| 06 Q1–Q9 | 7 languages; kit gates now; Material pickers; reply direction; lens springs; no swipe tick; Copy + Select all; no hinge layouts; digits as today | As 06 §19 | W5–W22 | 06 §19 |
| O-07-1…4 | Rail stays on wide screens; Pin moves to the menus; files open as a page; share pictures in after the switch | Yes / yes / yes / after | W8 | 07 §13 |
| D8-1…4 | Today's vibrations; no keyboard drag gesture; no extra ticks; keep the portal pull-to-refresh | As 08 §12 | W10 | 08 §12 |
| Q09-1…4 | Answers wait while the agent works; charts not mirrored; no word highlights in Changes; no "Add to message" | (a) for all | W12 | 09 §19 |
| D8 | Phone agents through Paseo (not the OpenCode 2 Claude Agent provider) | Paseo | W13 | ADR D8 |
| D15-4 | Install a second Linux inside the preview app on your phone for P2/P3 (needs ≈ the same storage as today's) | Yes, if ≥ 10 GB free; else videos | W14 | §7 |
| D16 | Each in-flight Flutter branch: land before the freeze or port from its branch | Per 02 §3.5 | before W10 | ADR D16 |
| 11 §25 | The 13 Android-platform questions | As 11 §25 | W16 | 11 §25 |
| D10-1…11 | Media choices (keep half downloads, no background dictation, F-Droid Lite, …) | As 10 §24 | W25 | 10 §24 |
| M-D1…M-D8 | Migration choices (keep R1, census, cleanup timing, …) | As 05 §12 | W26 | 05 §12 |
| D12 | No alpha library in the release; M3 beta allowed in the kit only | As stated | W26 | ADR D12 |
| R12-8 | Licence wording for proot, talloc and shmem | Mirror sources as release assets | W28 | 12 §13 |
| D18 | Register the package and every signer for developer verification (your account, $25) | Yes, before stage 2 | W29 | §11.7 |
| D11 | Live backgrounds and scenes after the switch, or drop | After | W33 | ADR D11 |
| D13, D14 | iOS prerequisites; desktop limits accepted | Decide when funded; accept | at M7 | ADR §12 |

### 17.2 Decisions this section adds

| # | Decision | Default | If the owner says no |
|---|---|---|---|
| D15-1 | The switch date follows parity; a date never forces an unapproved cut. | Yes | The owner names a fixed date; §19 then lists what moves after it, for approval |
| D15-2 | The bridge release ships in W3, right after the freeze gate. | Yes | Ship it two weeks before the switch candidate (fewer people can self-update) |
| D15-3 | Staged rollout: your phone 2 days → pre-release 7 days → latest. | Yes | Change the stage lengths |
| D15-4 | A second Linux in the preview app on your phone for P2 and P3. | Yes if ≥ 10 GB free | Emulator videos only until the switch |
| D15-5 | Previews only at M1–M5 (five APKs), plus weekly images or videos. | Yes | Add a mid-slice APK when a chat feature lands |
| D15-6 | The R1 "go back" build exists for the owner lineage only. | Yes | Also build a public-lineage R1 through CI (owner GO each time) |
| D15-7 | After the switch: M7 clean-up, then M8 AI Team and Memory and M9 Linux desktop in parallel, then M10 iOS when funded; MW when you say. | Yes | Reorder |
| D15-8 | Keep a 32-bit (`armeabi-v7a`) remote-only APK at the switch, as today. | Yes | Drop 32-bit phones; the release notes say so and the bridge offers the universal APK |

---

## 18. Reconciliations for the coordinator (C15-n)

Each item resolves a conflict between sibling sections, or asks for a small ADR amendment. Work proceeds on the
default; the coordinator records the outcome in `docs/native/decisions.md`.

| # | Conflict | Resolution (default) | Amends |
|---|---|---|---|
| C15-1 | 09 §17 puts offline-queue persistence in slice 3 (S3-COMP-E); 04 WP-04.8 and ADR §5.2 put the offline queue, markers and receipts in `:engine:delivery` in slice 2. | Slice 2 (S2-DLV); "never sent twice" is daily-path behaviour (E2E-10). S3-COMPE keeps drafts, vault, stash, history, phrase chips, references. | 09 §17 |
| C15-2 | AGENTS-BACKENDS-13 and E2E-31 need a live terminal in slice 3; 06 puts the live terminal slot in KN5 (slice 5) and ADR §5.2 starts `:android:terminal` in slice 5. | S3-TERMMIN adds the minimal sign-in terminal in slice 3 (kit-content `androidMain` slot over the PTY copied into `:android:linux` in S1); KN5 and S5-TERMINAL finish the rest. | 06 §18 |
| C15-3 | ADR §5.2 starts `:android:platform` in slice 2; 11 §1.1 needs the crash store, network monitor and device facts in slices 0–1. | Start `:android:platform` in slice 1 with exactly those three files (S1-PLAT0). | ADR §5.2 "Starts" column |
| C15-4 | 07 §11 builds the entry-router core in slice 1; ADR §5.2 starts `:engine:entry` in slice 2. | Slice 2 (S2-ENTRY, first week). | 07 §11 |
| C15-5 | ADR-09-1 asks `:engine:requests` to start in slice 2 for permissions and one-question asks. | Accept (S2-REQ). | ADR §5.2 |
| C15-6 | 02 §10 item 7 wants all 39 flows green on Flutter R before slice-1 fan-out; that needs the single emulator for most of M0. | Flows of slices 1–2 (E2E-01, 03, 04, 05, 10, 21, 36, 39 short form) before fan-out; the rest by end of W4, before any slice-2 unit needs them. | 02 §10 |
| C15-7 | 02 §5.8 removes the Flutter tree in one commit at the switch; 03 §24 says at switch+1. | One commit right after stage 2 (§11.4), so twin checks and fix-forward stay simple during the pre-release. | 02 §5.8 |
| C15-8 | 06 sizes K0 at 2–3 aw inside a two-week slice 0. | P7 and the 20 frozen signatures finish in M0; the rest of K0 runs into the first two days of W3. | — |
| C15-9 | ADR §9.6 and 02 NG15 say no telemetry; the owner's 2026-11-01 decision (memory, rank 1 under 02 §0.1) says build a tracking core with BASIC always on and DETAILED opt-in. | Build `:engine:analytics` in slice 4 (engine order: after `diagnostics`), with no sender bound until R12-1 names a destination; S-22's egress allowlist is unchanged until then. | ADR §5.2, §6.2, §9.6; 02 G7, NG15 |
| C15-10 | 11 §1.2 groups builtin-linux 29–32 in slice 3; the reply watch (32) is the S11-4 service in `:android:background` (slice 4). | BUILTIN-LINUX-32 lands in slice 4 with S4-BG. | — |
| C15-11 | 04 WP-04.9 lists E2E-02 for slice 2; scanning a QR needs the slice-3 scanner (10 MV-9). | E2E-02 is green in slice 3; its typed and code steps are tracked in slice 2. | 04 §16 |
| C15-12 | 07 §11 wants E2E-19 and E2E-20 green in slice 3; 11 §1.2 puts the Linux manager (40–48) in slices 3–5, and slice 3 is already the largest. | E2E-19 and E2E-20 move to slice 5 with S5-LINUXMGR. | 07 §11 |
| C15-13 | 02 §7.9 says every native build is ≥ 10000 with ranges handed out; 05 §9.1 steps main releases by 10. | Main flavor 10000–19999 (05's rule), preview flavor 20000+ (§2.6). | — |
| C15-14 | ADR §9.9 lists ABI splits arm64-v8a, x86_64 and universal; today's releases also ship `armeabi-v7a`, which the bridge's updater picks on 32-bit phones. | Add an `armeabi-v7a` remote-only split unless the owner answers D15-8 with no. | ADR §9.9 |

---

## 19. The scope valve (only with the owner's OK)

If a re-plan projects the switch past W32 (A), the coordinator offers these moves to "after the switch", in this order,
each with its row count from `scope.jsonl` and the weeks it saves (rerun Appendix A without the unit). Nothing moves
without the owner's yes (ADR §10.1; D15-1).

| Order | Move after the switch | Units removed from the critical load | Saves about [E] |
|---|---|---|---|
| 1 | Offline demo, keyboard shortcuts help, command launcher extras | part of S5-SEARCH | 0.5 week |
| 2 | Linux services, vitals and backup (freeze-tip additions) | part of S5-LINUXMGR | 1 week |
| 3 | BYO host | part of S5-INTEG | 0.5 week |
| 4 | Paseo second-tier: sub-agents, background tasks, import, daemon update | S5-PASEOP1 | 1 week |
| 5 | Codex direct connection | S5-CODEX | 1 week |
| 6 | The natural (Kokoro) voice and the voice conversation loop | part of S5-READ | 1 week |
| 7 | Connector card and connector search | part of S5-CARDS | 0.5 week |

Moving items 1–7 together saves about 4 weeks on calendar A [E]. Each moved item keeps its rows as `after-switch` with
the decision date, and its flows (E2E-24, E2E-29, parts of E2E-20 and E2E-30) leave the switch set.

---

## 20. Briefs: a filled example and the agent's start checklist

### 20.1 Example: `native/briefs/s2-chats.md` (the coordinator fills one per unit like this)

```markdown
# Brief: native/s2-chats-2027-01-04
Model: Sonnet 5.5                 Worktree: /home/eslam/Storage/Code/wt/ocn-s2-chats
Finish line: On the preview build, the bootstrap gate, first run, Conversations home, inbox, all conversations,
  filters and new conversation pass E2E-01 and E2E-21 steps 1-7.
Non-goal: pins feeding launcher shortcuts (S4); agent rows (S3); chat pages (S4).
Removes: the Flutter home's seven pending-intent paths (they now arrive as EntryTargets); no bottom bar on phones.
Contract: native/contracts/s2.md §ChatsUi, §ChatsActions, §OnboardingUi (frozen 2026-12-28)
Catalog rows (must end green at T1): jq filter slice==2 and module in [":feature:chats", ":feature:onboarding"]
  (includes B-NAV-017, B-NAV-018). Flows touched: E2E-01, E2E-21 (steps 1-7).
Read set: native/AGENTS.md; plan 07 §5 A-B, §2.5, §10; plan 06 §7.3, §7.5; map screens-nav 01-03, 06, 24, 27, 29,
  30, 34, 35, 37; Flutter: lib/ui/screens/chats_*, test/home_navigation_test.dart, test/first_run_*_test.dart.
Write set: native/feature/{onboarding,chats}/**, native/ui/words/**/strings_{chats,onboarding}.xml,
  native/ui/routes/**/{ChatsRoutes,OnboardingRoutes}.kt, docs/qa/native/s2-chats-*/**
Single-owner modules you may NOT edit: :ui:kit*, :ui:shell, :ui:routes (other files), :engine:*, :core:*.
Owner rules that apply word for word: no bottom bar on phones (2026-10-20); obvious options as visible first rows
  (New project / Search / Opened before); actions name their target; no new bar or row levels; plain words.
Approved designs this must not replace: the Conversations home of Flutter 2.1.0+57 (parity, NG10).
Checks: native/tool/t0.sh :feature:chats while editing; native/tool/t1.sh --base native/main when ready;
  native/tool/check_write_set.sh native/briefs/s2-chats.md before reporting.
Device work: emulator booking in native/EMULATOR.md (90 min max), build numbers 20140-20149.
Evidence: docs/qa/native/s2-chats-<date>/README.md; images per visual pass to launch-footage/progress/s2-chats/.
Quiet hours: lifted 2026-11-01; if reinstated, nothing heavy on the PC 00:00-02:00.
Report: 02 §7.14 format.
```

### 20.2 The agent's start checklist (paste into every brief's first reply)

1. I have read the brief, `native/AGENTS.md` and the contract part it names.
2. My worktree is sparse and on my branch; `git status` is clean.
3. My rows are listed (§3.4) and fail for the reason they describe.
4. I know my write set and the single-owner modules I may not touch.
5. I will format only the files I change, never push, and never run `./gradlew build`.
6. I will book the emulator before any device step and use only my build-number block.
7. My report will say what is **not** proven.

---

## 21. Acceptance checks for this section

| # | Check | Expected |
|---|---|---|
| 1 | Every map feature id appears once in §13 | 700 features; per-map totals equal the map counts (46, 55, 63, 64, 58, 112, 81, 50, 65, 41, 65) |
| 2 | Every switch flow appears once in §14.2 | 37 |
| 3 | `python3 -I native/tool/plan/plan_calendar.py` | `S0:W3 S1:W7 S2:W14 S3:W19 S4:W22 S5:W28 S6:W30` |
| 4 | `python3 -I native/tool/plan/plan_calendar.py --slots-from-w3 9` | `S0:W3 S1:W7 S2:W11 S3:W14 S4:W16 S5:W21 S6:W22` |
| 5 | `python3 -I native/tool/plan/seed_scope.py native/catalog/behaviors.jsonl` | the §14.1 bucket counts |
| 6 | Unit-table ad sums | S0 50, S1 95, S2 125, S3 150, S4 105, S5 160, S6 40 |
| 7 | Every unit row has a finish line, non-goal, model, write set, dependency and ad | yes |
| 8 | Every owner decision has a default and an ask week | yes (§17) |

---

## 22. Sources

- Plan: `01-architecture-decision.md` (§1, §4, §5.2, §6.2, §7.6, §8, §9.4–§9.9, §10, §11, §12),
  `02-principles-scope.md` (§2–§11), `03-architecture-modules.md` (§1.2, §22–§24), `04-protocols-networking.md`
  (§16–§19), `05-data-migration.md` (§7, §9–§12), `06-ui-kit-design-system.md` (§18–§20), `07-screens-navigation.md`
  (§5, §7, §11–§14), `08-motion-animation.md` (§11–§12), `09-chat-streaming-genui.md` (§17–§19), `10-media-voice.md`
  (§1, §21, §22, §24), `11-android-platform-native.md` (§0–§1, §5, §11), `12-package-registry.md` (§13–§14).
- Design: `../design/proposal-A-shared-ui.md` (§15, §18), `../design/proposal-C-android-first-kmp-ready.md` (§18).
- Maps: all eleven feature lists in `../map/*.md`; `packages-build.md` §0 items 6 and 10, open question 4.
- Catalog: `../catalog/BEHAVIORS.md` (counts), `../catalog/behaviors.jsonl` (bucket estimate, 2026-11-01),
  `../catalog/e2e-flows.md` (flow index, AVD matrix, fixtures).
- Research: `kmp-cmp-status.md` (§0, §1, §12–§13), `android-surfaces.md` (§0 items 8–9, §14–§16), `testing-perf.md` (§0).
- App-updates spec: `/home/eslam/Storage2/wt/app-updates-2026-10-31/SPEC.md` (the `releases/latest` source, asset
  names, verify-before-install).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `pc-capacity-ceiling`,
  `build-vps`, `subagent-model-choice`, `codex-backend-claude-frontend`, `no-heavy-workflows`, `short-replies-rule`,
  `live-video-proof`, `show-samples-rule`, `one-screen-per-image`, `quiet-hours-vps-only`, `apk-build-numbers`,
  `release-2.1-prep`, `no-users-yet`, `phantom-process-limit`, `stabilize-before-shipping`,
  `test-on-emulator-not-phone`, `shorebird-is-mandatory`, `judge-visuals-hard`.
- Checked today [V], 2026-11-01:
  - GitHub REST docs, "Get the latest release": "The latest release is the most recent non-prerelease, non-draft
    release, sorted by the created_at attribute." (https://docs.github.com/en/rest/releases/releases)
  - Android developer verification: September 30, 2026 for participating stores in Brazil, Indonesia, Singapore and
    Thailand; "expanded globally for all apps on certified Android devices in 2027"
    (https://developer.android.com/developer-verification).
  - Compose Multiplatform releases: 1.13.0-beta01 (23 Oct) is the newest 1.13 build; 1.12.2 (15 Oct) is "Latest"
    (https://github.com/JetBrains/compose-multiplatform/releases).
  - Week dates: 2026-11-02 is a Monday (computed).
- The owner's words relayed for this run: "7400 for widgets can come later."

---

## Appendix A: `native/tool/plan/plan_calendar.py`

The coordinator commits this file in S0 and edits only the `U` list (when units change) and nothing else. `X@k` means
"X has run k agent-days and its contract is usable". Run with `python3 -I`.

```python
#!/usr/bin/env python3
"""plan_calendar.py: slot-limited list scheduler for the native rewrite (plan section 15, Appendix A).
Usage: python3 -I native/tool/plan/plan_calendar.py [--slots 6] [--slots-from-w3 9] [--eff 0.9] [--units]
--eff = planned agent-days per slot-day (0.9 default; the M0 re-plan sets 0.9 x measured P10 factor).
Prints the week (W1 = Mon 2026-11-02) in which each slice's last unit finishes; --units prints every unit."""
import argparse
U = [
('S0-SKEL',0,6,[]),('S0-KIT',0,10,[]),('S0-CORE',0,6,['S0-SKEL@2']),('S0-NAV',0,5,['S0-SKEL@2']),
('S0-MIGR',0,5,['S0-SKEL@2']),('S0-STREAM',0,4,['S0-SKEL@2']),('S0-NETP',0,5,[]),
('S0-WORDS',0,3,['S0-SKEL']),('S0-GATES',0,4,['S0-SKEL']),('S0-ROUTES',0,2,['S0-SKEL']),
('S1-DATA',1,10,['S0-CORE']),('S1-SECNET',1,7,['S0-CORE']),('S1-NET',1,6,['S0-CORE','S0-NETP']),
('S1-OC2',1,12,['S1-NET']),('S1-CONN',1,8,['S1-NET']),('S1-ENG',1,9,['S1-DATA']),
('S1-KITA1',1,10,['S0-KIT']),('S1-KITA2',1,10,['S0-KIT']),('S1-WORDS',1,8,['S0-WORDS']),
('S1-SHELL',1,7,['S0-ROUTES','S0-KIT']),('S1-LINUX',1,5,['S0-SKEL']),('S1-PLAT0',1,3,['S0-CORE']),
('S2-ENTRY',2,5,['S0-ROUTES','S0-CORE']),('S2-MODEL',2,3,['S0-CORE']),('S2-MD',2,5,['S2-MODEL']),
('S2-TX',2,12,['S2-MODEL','S2-MD@3']),('S2-DLV',2,10,['S2-TX@4','S1-DATA']),('S2-REQ',2,5,['S2-TX']),
('S2-KCA',2,13,['S1-KITA1','S2-MD']),('S2-KCB',2,10,['S1-KITA2']),('S2-KITB',2,13,['S1-KITA1']),
('S2-CHAT',2,12,['S2-TX@6','S2-DLV@3','S2-KCA@6','S2-KCB@5']),('S2-CHATS',2,10,['S1-CONN','S1-SHELL','S2-KITB@5']),
('S2-SERVERS',2,8,['S1-CONN','S1-DATA','S1-KITA2']),('S2-PLATFORM',2,4,['S2-ENTRY']),
('S2-MIGRRUN',2,8,['S1-DATA']),('S2-GATEUI',2,3,['S2-MIGRRUN@3','S1-SHELL']),('S2-BLOB',2,2,['S1-DATA']),('S2-BENCH',2,2,['S2-CHAT']),
('S3-PASEO',3,10,['S1-NET']),('S3-RTSPLIT',3,10,['S1-LINUX']),('S3-RTENG',3,13,['S1-DATA']),
('S3-RTWIRE',3,7,['S3-RTSPLIT','S3-RTENG@8']),('S3-AGENTSE',3,11,['S3-PASEO@5','S3-RTENG@6']),('S3-AGENTHOST',3,5,['S3-RTSPLIT']),
('S3-REQFORMS',3,7,['S2-REQ']),('S3-COMPE',3,7,['S2-DLV']),('S3-COMPUI',3,10,['S3-COMPE@4','S2-KCB']),
('S3-MEDIAATT',3,15,['S3-COMPE@3']),('S3-KCCONTENT',3,18,['S1-KITA2']),('S3-WORK',3,5,['S2-KCA']),
('S3-TERMMIN',3,4,['S3-KCCONTENT@8','S1-LINUX']),('S3-PHONEUI',3,12,['S3-RTENG@6','S2-KITB']),('S3-AGENTSUI',3,10,['S3-AGENTSE@5','S2-KITB']),
('S3-MEDIAPROOF',3,4,['S0-SKEL']),('S3-BENCH',3,2,['S3-AGENTSUI']),
('S4-ATTNE',4,11,['S1-CONN','S2-TX@8']),('S4-BG',4,11,['S4-ATTNE@5','S3-RTWIRE']),('S4-SCHED',4,11,['S2-DLV','S1-CONN']),
('S4-WIDGETS',4,11,['S4-ATTNE@6']),('S4-PANELS',4,4,['S4-WIDGETS@6']),('S4-SETTINGS',4,11,['S1-ENG','S2-KITB']),
('S4-APPEAR',4,15,['S1-KITA1']),('S4-PAGES',4,10,['S2-CHAT@6']),('S4-ABOUT',4,5,['S1-ENG']),('S4-UPDATER',4,6,['S1-NET','S1-ENG']),
('S4-SCHEDUI',4,4,['S4-SCHED@6']),('S4-ANALYTICS',4,4,['S1-ENG']),('S4-BENCH',4,2,['S4-BG']),
('S5-CODEX',5,6,['S1-NET']),('S5-OC2MORE',5,7,['S1-OC2']),('S5-PROJE',5,10,['S1-CONN']),('S5-PROJUI',5,11,['S5-PROJE@5','S3-KCCONTENT']),
('S5-TERMINAL',5,7,['S3-TERMMIN','S5-OC2MORE@4']),('S5-KC2',5,13,['S3-KCCONTENT']),('S5-MEDIALOGIC',5,10,['S0-CORE']),
('S5-VOICE',5,12,['S5-MEDIALOGIC@5','S3-MEDIAPROOF']),('S5-MODELS',5,10,['S5-MEDIALOGIC@5']),('S5-READ',5,10,['S5-VOICE@6','S5-MODELS@5']),
('S5-VIEWER',5,10,['S3-MEDIAATT','S5-KC2@5']),('S5-WIFI',5,6,['S5-MEDIALOGIC@5']),('S5-CARDS',5,13,['S3-REQFORMS','S3-AGENTSE']),
('S5-INTEG',5,10,['S5-OC2MORE@3']),('S5-LINUXMGR',5,9,['S3-RTWIRE']),('S5-PASEOP1',5,7,['S3-AGENTSE']),
('S5-SEARCH',5,4,['S4-SETTINGS']),('S5-TERMUXOUT',5,3,['S3-RTWIRE']),('S5-BENCH',5,2,['S5-VOICE']),
('S6-MIGRHARD',6,9,['S2-MIGRRUN','S4-WIDGETS','S4-SCHED']),('S6-R1',6,2,['S6-MIGRHARD@4']),('S6-COMPAT',6,3,['S6-MIGRHARD@4']),
('S6-PERF',6,6,['S5-READ','S5-PROJUI']),('S6-AUDIT',6,6,['S4-APPEAR','S5-PROJUI','S5-VIEWER']),('S6-A11Y',6,3,['S5-PROJUI']),
('S6-RELEASE',6,6,['S4-UPDATER']),('S6-SWEEP',6,5,['S5-CARDS','S5-INTEG','S5-READ']),
]
def run(slots_by_week, eff):
    ids = {u[0]: u for u in U}; dur = {u[0]: u[2] / eff for u in U}
    order = [u[0] for u in U]; remaining = list(order)
    done, start, prog, running, day = {}, {}, {}, [], 0
    def ready(uid):
        for d in ids[uid][3]:
            if '@' in d:                      # 'X@k' = X has run k agent-days (its contract is usable)
                dd, k = d.split('@')
                if dd not in start or prog[dd] < float(k): return False
            elif d not in done: return False
        return True
    while remaining or running:
        cand = sorted([u for u in remaining if ready(u)], key=lambda u: (ids[u][1], order.index(u)))
        while len(running) < slots_by_week(day // 5) and cand:
            u = cand.pop(0); remaining.remove(u); running.append(u); start[u] = day; prog[u] = 0
        for u in list(running):
            prog[u] += 1
            if prog[u] >= dur[u] - 1e-9: done[u] = day; running.remove(u)
        day += 1
        if day > 1000: raise SystemExit('dependency loop')
    return start, done
if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--slots', type=int, default=6); p.add_argument('--slots-from-w3', type=int, default=None)
    p.add_argument('--eff', type=float, default=0.9); p.add_argument('--units', action='store_true')
    a = p.parse_args()
    later = a.slots_from_w3 or a.slots
    s, d = run(lambda w: a.slots if w < 2 else later, a.eff)
    last = {}
    for u in U: last[u[1]] = max(last.get(u[1], 0), d[u[0]])
    print(' '.join('S%d:W%d' % (k, v // 5 + 1) for k, v in sorted(last.items())))
    if a.units:
        for u in U: print('%-15s W%-2d..W%-2d %5.1f ad' % (u[0], s[u[0]] // 5 + 1, d[u[0]] // 5 + 1, u[2]))
```

The scheduler gives priority to the lowest slice, then to the order of the list. It does not model lanes, the emulator
or holidays; the coordinator adds those by judgement and writes any manual shift next to the dates in §1.1.

## Appendix B: `native/tool/plan/seed_scope.py` (first-pass slice for every catalog row)

Run once in S0-SCOPE: `python3 -I native/tool/plan/seed_scope.py native/catalog/behaviors.jsonl`. It prints the bucket
counts of §14.1; with `--write` (to be added by S0-SCOPE, 10 lines) it emits `{"id","scope","slice"}` lines that seed
`scope.jsonl`. The rules read the first Flutter source file of each row; the first matching rule wins.

```python
import json, re, sys, collections
RULES = [
 (r'factsbook|(^|/)team_|_team(_|$)|aiteam|gascity|od_team|phone_project_engine|phone_engine_|builtin_team|termux_aiteam|phone_team|slice_close_team|slice_team|shared_team|role_cycle|builtin_phone_engine|kit_work_graph', 'AFTER'),
 (r'external_agent|acp_client|session_address|browser_claude_launch', 'DROP'),
 (r'kit_live_background|kit_scenes|kit_states_scenes', 'AFTER'),
 (r'termux_migration', 'S5'),
 (r'termux|setup_termux', 'DROP'),
 (r'desktop_packaging|desktop_window_state|desktop_release_check', 'DESKTOP'),
 (r'ios_remote|ios_runner', 'IOS'),
 (r'release_script|release_metadata|release_blockers|shipped_download|context:day-1-update', 'S6'),
 (r'architecture_boundaries|platform_capabilities|repository_hygiene|vps_build_apk|desktop_platform_gating|kit_map_gate|ui_ledger_coverage', 'S0'),
 (r'kit/kit_(code_block)', 'S3'),
 (r'kit/kit_(diff_view|log_panel|viewer|terminal|chart|qr|level_meter|context_ring|markdown_image)', 'S5'),
 (r'kit/kit_(style|fine_tune|swatch|signal)|kit/style/|appearance_|app_theme|theme_packs', 'S4'),
 (r'kit/kit_(markdown|message|work_line|step_light|step_timeline|ask_line|frost|since|draft_manifest)', 'S2'),
 (r'kit/kit_capability_explainer', 'S3'),
 (r'kit/|kit_motion_app|text_scale|text_size|theme_roles|app_iconography|android_focus|ui_glossary', 'S1'),
 (r'audit2_.*deletion|profile_deletion|default_notice_deletion|byo_host/profile_signer_deletion', 'S5'),
 (r'redaction|credential_ingress|external_link|server_trust|private_network_cleartext|tailscale_profile|api2_transport|api2_sse|api2_dialect|api2_events|api2_gateway_events|api2_gateway_mappers|api2_client|api2_health|server_probe|connection_failure|^network|app_preferences_store|app_locale|locale_format|l10n|native_locale|app_diagnostics|native_crash|crash_diagnostics_startup|profile_store|profile_secure_storage|oc1_profile_upgrade|retired_prefs|product_error_text|audit2_product_error|no_raw_error|recent_errors_plain|connection_sse_wake|reconnect_hardening|connection_interaction|connection_location_restore|in_flow_consent|read_state_consent|mobile_download_consent|interaction_defaults|settings_scope_domain|perf_transport_decode|api2_stable_flags|v2_feature_gating|api2_global_pagination|api2_models|connection_v2_requests|product_repository_server|server_data_widening|redact|no_red_scan', 'S1'),
 (r'codex_|gen_ui|mcp_|connector_search|sv3_|agent_card_view|agent_handoff|paseo_agent_handoff|continue_with|review_handoff|session_handoff|agent_tool_adapter|agent_certification|vision_chat|paseo_genui', 'S5'),
 (r'voice_|natural_voice|read_aloud|setup_voice_component', 'S5'),
 (r'git/|git_link_repair|worktree_inspection|isolated_task|environment_projects|workspace_paths|project_hub|project_dev_server|detected_dev_servers|dev_preview|development_service|listening_ports|linux_manager|linux_reset|local_terminal(?!_signin)|terminal_|xterm_sgr|common_file_preview|static_svg|local_pdf|reader_preferences|wifi_file_share|shared_project_roots|shared_storage_path|byo_host|manage_space|usage_|provider_|quota_|computer_usage|in_app_quota|plugin_|web_search|library_integrations|integration_auth|host_agent_providers|session_import|opencode_history_copy|failed_job_report|shell_output|managed_shell|api2_mcp_scope|api2_provider_workflows|api2_terminal_auth|review_followups|run_result|search_go_to|builtin_folders|builtin_project|phone_storage_folders|paseo_files|paseo_usage|paseo_host_update|paseo_provider|paseo_background_tasks|paseo_archive|demo_isolation|product_repository_projects|product_repository_mcp|tools_screen|connect_method_order|model_library|free_model|chat_free_model|coverage/files_actions|revamp/slice_aisetup|setup_planner|session_skill|markdown_path_link|builtin_performance', 'S5'),
 (r'background_(live|pause|coding_alert|action|signin|shell_result)|notification_|attention|profile_monitor|elsewhere|conversations_elsewhere|widget_|launch_surfaces|launch_session|pinned_session|scheduled_prompt|while_away|reply_watch|first_reply_notify|live_update_chip|settings_|teaching|nudge|gesture_audit|crash_report|crash_diagnostics|report_problem|problem_report|coverage/diag|coverage/settings|about_|session_note|session_export|session_context|context_usage|staged_revert|undo_from_here|message_delete|api2_revert|session_pins|profile_sign_in_reset|in_app_auto_update|update_channel|device_diagnostics|exit_list_focus|chat_background_result|sign_in_foreground_causes|revamp/shared_settings|revamp/slice_p6_6a|android_group_names_native|server_capabilities_screen|search_index|phrase_chips_settings|session_link_native_contract|launch_shortcut_native|share_intent_native', 'S4'),
 (r'paseo_|phone_agent|phone_agents|local_agent|agent_|claude_install|claude_phone_auth|builtin_|phone_server|phone_host_runtime|phone_process|phone_processes|phone_storage|this_phone|setup_|managed_runtime|managed_move|installer_admission|thermal_guard|app_exit|lifecycle_report|leftover_process|running_on_phone|running_agents_strip|opencode_native_install|host_script|phone_setup|phone_own_server_icon|draft_attachments|session_draft|prompt_shelf|saved_prompts|stash_|prompt_photos|phrase_|chat_attachment_reader|office_text|delimited_text|form_request|api2_integration_form|session_auto_approval|automation_policy|automatic_activity|permission_migration|repeated_permission|model_|session_model_scope|prompt_file_mention|capability_flows|background_work|background_tasks|mobile_task_view|pairing_scanner|coverage/phone_agent|revamp/shared_phone|revamp/queued_prompt|queued_prompt|local_terminal_signin|forget_in_app_server|phone_agent_context|workspace|active_context|forget', 'S3'),
 (r'transcript|v2_transcript_rows|turn_stall|chat_|merged_chat_feed|session_inventory|session_tail_cache|session_title|session_selection_sync|session_read_state|return_brief|home_navigation|no_bottom_nav|first_run|offline_queue|command_receipt|pending_command_journal|session_creation_receipts|message_page_transport|api2_phone_chat_dispatch|api2_interaction_gateway|api2_permission_dispatch|api2_session_permission|permission_|tool_card|tool_state|tool_label_surfaces|live_status|perf_|pairing_payload|share_intent|launch_shortcut|session_link|servers_scenes|slice_close_servers|server_profile_reentry|revamp/screen_shell|app_lifecycle|connection_|ui_|product_repository|exit|recent|stuck', 'S2'),
]
def slice_for(row):
    if row['area'] == 'MIGR': return 'S6'
    stem = re.sub(r':\d+$', '', row['sources'][0]).replace('test/', '').replace('_test.dart', '')
    for rx, s in RULES:
        if re.search(rx, stem): return s
    return 'MANUAL'
rows = [json.loads(l) for l in open(sys.argv[1])]
by, area, e2e = collections.Counter(), collections.defaultdict(collections.Counter), collections.Counter()
for r in rows:
    s = slice_for(r); by[s] += 1; area[s][r['area']] += 1
    if r['portable_e2e']: e2e[s] += 1
for s in ['S0','S1','S2','S3','S4','S5','S6','AFTER','DROP','DESKTOP','IOS','MANUAL']:
    print(s, by[s], 'e2e', e2e[s], dict(sorted(area[s].items())))
print('total', sum(by.values()))
```

Known limits: a row whose first source is a shared test file can land in the wrong slice; S0-SCOPE reviews every
AFTER and DROP row (each needs a decision anyway) and every row whose module filter does not match its owning unit.

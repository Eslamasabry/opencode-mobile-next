# 15. Delivery roadmap: milestones, slices, the switch-over, risks and what the owner sees

Written 2026-11-01. Binding inputs: `01-architecture-decision.md` (the ADR) and `02-principles-scope.md`. Where this
file and the ADR differ, the ADR wins. The few places where this file asks the coordinator to amend the ADR or a sibling
section are listed in §18 (C15-n), each with the default that work proceeds on.

Tags: **[V]** checked today against a primary source; **[R]** read in a plan section, map, research file, the repo or
owner memory; **[E]** my estimate, replaced by measured values at the M0 re-plan (§2.4); **[U]** unverified.

## For the owner, in five lines

1. We build the native app in seven steps (M0–M6). From mid-December you get a separate "preview" app beside today's app at the end of each step; the first one you can chat in arrives early February (mid-January with more capacity).
2. The real switch, where the native app installs over today's app and keeps everything including the phone's Linux, reaches your phone in mid-June 2027 with today's one build server, mid-May with cloud agents added, or mid-April with a second build server. The first two weeks measure our real speed and fix these dates.
3. Before the switch, one last Flutter update (the "bridge") goes out, so its Update button can later deliver the native app.
4. The switch goes in stages: your phone first for two days, then a GitHub pre-release for a week, then everyone. A ready "go back" build waits until you say the native app is fine.
5. After the switch: a clean-up release, then AI Team and Memory, then Linux desktop, then iOS once there is a Mac and an Apple account. We ask you about the widget tests at the switch, or earlier if bugs show they are needed.

Decision needed: **Yes**, three questions. (a) More capacity: cloud agents (your phone gets the switch about mid-May),
a second build server (about mid-April, if this PC can run that many agents), or both (about late March)?
Recommended: cloud agents now; the second server only if the first two weeks show we are slow. (b) Publish the bridge
update right after the Flutter freeze gate passes (about 16–22 Nov)? Recommended: yes. (c) AI Team leaves your app at
the switch and comes back later: from mid-June until about September–November 2027 as planned, until about
August–September if we start it right after the switch, or not at all if we keep it in the switch (the switch moves
about 2–3 weeks later). Recommended: start it right after the switch.

---

## 0. How to use this section

### 0.1 Who reads what

| Reader | Read first | Then |
|---|---|---|
| Coordinator | §1, §2, §3, §11, §15–§18 | write each brief from its unit row (§4–§10) with the §20 template; push snapshots and bring cloud work back with §11.0; run the §2.4 re-plan at M0, M2 and M4 |
| Builder agent | your unit row, §3.2, §20.2 | start without asking; your brief is complete if every field of 02 §7.13 is filled |
| Test-writer lane | §3.1, §14 | stay one slice ahead |
| T3 / T4 lane holders | §2.5, §11 | |
| Owner | the five lines, §15, §17.1 | |

### 0.2 Words and units used here

| Word | Meaning |
|---|---|
| **ad** (agent-day) | One agent slot for one working day, including its T0/T1 runs, review fixes and its evidence record. **aw** = 5 ad. |
| **Slot** | One concurrently running *builder* agent that uses the build VPS. One VPS gives **6** slots (02 §7.8); a second VPS gives **9** (ADR D15). Every slot is also a session on the PC, so the PC memory cap measured at M0 (§2.1) limits slots too. |
| **Cloud session** | A Claude Code cloud session (memory `claude-code-cloud-howto`): no PC memory, no emulator, works from a pushed snapshot branch (§11.0). It takes only units marked `cloud` in Appendix A. |
| **Lane** | A standing role with its own agent that does not take a slot: test-writer (TW), flows (FL), fixtures (FX), T3 emulator holder, T4 performance holder (02 §6.9). |
| **Week Wn** | W1 = Monday 2026-11-02 to Sunday 2026-11-08 [V, computed]. W14 = 2027-02-01…07. W30 = 2027-05-24…30. W33 = 2027-06-14…20. |
| **Calendar A / B / C** | A = one VPS (plan of record until D15 is answered). B = a second VPS from W3 (9 slots). C = A plus 3 cloud sessions from W3. All three are computed by Appendix A. |
| **Snapshot** | One commit whose tree equals a local branch's tree and whose parent is `mobile-next/master`, so it can be pushed although local history holds a 181 MB APK (§11.0). |
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

| Milestone | Slice | What it proves (one line) | Done, calendar A | Done, calendar B | Done, calendar C | The owner gets |
|---|---|---|---|---|---|---|
| **M0** foundations | 0 | The plan passed the Codex Astra review (G-A, W1 days 1–2), the skeleton builds, the ten ADR proofs have verdicts, contracts for slices 1–3 are frozen, the test harness and the parity flows exist | gate end of W2 (Nov 15); tails into W3 | same | same | a video of predictive back and the streaming test; the re-planned dates |
| **M1** foundations | 1 | Data, network, OpenCode 2 client, connection engine, analytics core (sender none), kit core, words and shell are green | W7 (Dec 14–20) | W7 | W7 | **P0 "kit tour"** preview APK |
| **M2** chat daily path | 2 | Add your server, open a chat, stream, stop, steer, answer a permission, go offline without sending twice | W14 (Feb 1–7) | W11 (Jan 11–17) | W11 | **P1 "chat with your server"** |
| **M3** phone and agents | 3 | Built-in Linux, the phone server, Claude Code via Paseo, the full composer and its motion, attachments, QR pairing | W19 (Mar 8–14) | W15 (Feb 8–14) | W17 (Feb 22–28) | **P2 "phone + agents"** |
| **M4** surfaces | 4 | Notifications, reply watch, restore after boot and update, tiles, widgets, schedules, settings, the five styles, the in-app updater | W24 (Apr 12–18) | W18 (Mar 1–7) | W20 (Mar 15–21) | **P3 "around the phone"** |
| **M5** everything else | 5 | Files, Git, terminals, voice, read aloud, viewers, Wi-Fi share, Codex, cards, integrations, Linux manager | W31 (May 31–Jun 6) | W22 (Mar 29–Apr 4) | W26 (Apr 26–May 2) | **P4 feature-complete preview** |
| **M6** switch | 6 | Upgrade journeys J1–J8, R1, performance, audit, release plumbing; parity (ADR §10.1) | candidate W32 (Jun 7–13); your phone W33 (Jun 14–20) | candidate W23; your phone W24 (Apr 12–18) | candidate W27; your phone W28 (May 10–16) | the switch on your phone, then the staged release |
| Switch release public | — | pre-release, then "latest" (§11.4) | pre-release W34, latest W35 (Jun 28–Jul 4) | W25, W26 (Apr 26–May 2) | W29, W30 (May 24–30) | the in-app Update button offers it to everyone |
| **M7** switch+1 | — | Room move (unless triggered earlier), targetSdk 37, Flutter tree removal, first post-switch features | ~4 weeks after latest | same | same | a normal update |
| **M8** AI Team + Memory | — | ADR D6 deferrals, E2E-28 and E2E-32 | after M7, or right after stage 2 if the owner picks D6 (b) (§17.1) | | | |
| **M9** Linux desktop | — | the native desktop app replaces the frozen Flutter Linux build | 6–10 aw after start [R proposal A §15.2] | | | |
| **M10** iOS | — | remote client; needs ADR D13 prerequisites | 12–18 aw (18–30 with widgets and push) [R proposal A §15.3] | | | |
| **MW** widget tests | — | the owner's "later" phase (14 §5) | asked at stage 2, or earlier when the 14 §5.2 trigger fires (C15-15) | | | |

Calendar B also needs a PC that can host 9 builder sessions plus the lanes (§2.1, measured at M0); calendar C needs no
extra PC memory. Both lean on the one emulator, whose hours §2.5 budgets.

### 1.2 The critical path (what decides the date)

```
S0-SKEL ─► S0-CORE ─► S1-NET ─► S1-OC2 ─────────────────┐
                         └────► S2-MODEL ► S2-MD ► S2-TX ┴► S2-CHAT ─► (P1)
S0-KIT ─► S1-KITA1/2 ─► S2-KCA, S2-KCB ──────────────────┘
S1-LINUX ─► S3-RTSPLIT ─► S3-RTWIRE ─► S4-BG (reply watch) ─► S4-RESTORE ─► S6-MIGRHARD ─► owner phone ─► rollout
S1-DATA ─► S2-MIGRRUN ─────────────────────────────────────────────────────────────┘
S5-MEDIALOGIC ─► S5-MODELS (voice pack "Ready" in J1) ─────────────────────────────┘
```

Calendar A is capacity-bound: every 27 ad added or removed moves the switch about one week. More slots help until the
chain sets the floor. From Appendix A at `--eff 0.9`: 9 slots from W3 → S6 lands W23, 12 slots → W19, 15 slots → W16,
20 or more → **W15**, the chain floor. Below W15 nothing gets faster without cutting scope (§19). The emulator is not in
the model; §2.5 budgets it, and at high slot counts it, not the chain, is the likely limit.

### 1.3 The numbers

| | Builder ad (slots) | Lane ad | Rows in switch scope [E, §14] | Flows that must turn green here |
|---|---|---|---|---|
| S0 | 50 | 56 (+ 2 if S0-NOTICE runs) | 12 | — (all 39 written and run on Flutter first) |
| S1 | 108 | 30 | 140 | — |
| S2 | 132 | 49 | 136 | 5 |
| S3 | 169 | 55 | 357 | 8 |
| S4 | 122 | 45 | 220 | 12 |
| S5 | 169 | 45 | 404 | 11 |
| S6 | 42 | 20 | 18 (+ every remaining red row) | 1 (E2E-39) |
| **Total to the switch** | **792 ad ≈ 158 aw** | **≈ 300 ad ≈ 60 aw** | **≈ 1,287** catalog rows, + up to 15 MANUAL, + 40 sibling-defined rows (§14.1), + ≈ 100 top-up rows [E] ≈ **1,440** | **37** |

Comparison [R]: proposal A guessed 90–135 aw for the daily-use switch scope and proposal C 95–125 aw, both with tests
and emulator passes folded into units. The sibling sections' own estimates are higher where they gave one (06: kit 29–33
aw; 10: media 30.5 aw). The plan of record uses the central unit numbers in §4–§10, which since this revision include
the sibling work packages that had no unit (§21 check 9); the high case uses the sibling numbers where they are still
higher (+≈ 80–100 ad, about +3–4 weeks on calendar A). The M0 re-plan replaces all of them with measured throughput.

---

## 2. Capacity, calendar and re-planning

### 2.1 Agents, machines and who runs where

| Role | How many | Model (02 §7.7) | Machine use |
|---|---|---|---|
| Builder slots | 6 (calendar A) or 9 (B, from W3), never more than the PC cap measured at M0 (below) | Codex for `:core`, `:protocol`, `:engine`; Sonnet for kit parts, features, `:android:*` ports; Opus for the transcript, migration, the runtime split, navigation spikes, premium visual work | T0 local or VPS, T1 on the VPS through `native/tool/t1.sh`; at most one Gradle build at a time on the PC |
| Cloud lane | **since 2026-11-01: up to 17 Claude Code cloud sessions in parallel** building slice-0 and slice-1 units (§2.1.1); planned before as 3 sessions from W3 (calendar C). Only emulator-free units: `commonMain`, desktop and Android host tests | effort **high** for hard units (chat engine, migration, protocols, kit owner), **medium** for the rest; a Codex unit may still go to Codex when a PC slot is free (`codex-backend-claude-frontend`) | none on the PC; a 4 vCPU / 16 GB cloud machine with no emulator; T0/T1 on `desktopTest` and Android host tests there, T2 on the VPS after bring-back (§2.1.1) |
| Test-writer lane (TW) | 1 agent W2–W5, 2 agents W6–W29, 1 agent W30–W32 (calendar A; shorter on B and C) | Sonnet | T0 compile-only runs on the VPS |
| Flows lane (FL) | 2 agents W1–W4, then 1 part-time | Sonnet | writes Maestro YAML; never holds the emulator |
| Fixtures (FX) | 1 Codex session W1–W3, then on request | Codex | PC fixture servers by exact PID only |
| T3 emulator holder | 1 | Sonnet (Opus for owner-facing candidates) | the one PC emulator under `flock /home/eslam/Storage/tmp/oc-emulator.lock` |
| T4 performance holder | 1, part-time | Sonnet | PC emulator `OC_API37` by booking; NX721J only with the owner's OK |
| Coordinator | the lead session | Opus | briefs, merges, T2 on the VPS, ledgers, owner messages |

**PC memory is a hard cap, and it is not yet measured for this plan.** Calendar A needs about 11 PC-resident sessions at
peak (6 builders + 2 TW + FL + T3 + T4), calendar B about 14. The recorded ceiling (memory `pc-capacity-ceiling`) is
lower: after two global OOMs (2026-10-08 and 10-09) it was set to at most 2 Codex leads + 1 Claude subagent at a time;
on 2026-10-15 five lean Codex sessions fitted once every build and test ran on the VPS. So at M0 (W2) the coordinator
measures: raise PC-resident sessions 4 → 6 → 8 → 11 → 14, each step held one hour with one T1 running and the emulator
booked, and record `free -m` available memory and swap use per step in `native/LEDGER.md`. The cap is the highest step
that keeps ≥ 7 GB available and swap under half full. Sessions above the cap go to the cloud lane (cloud-eligible units
only) or wait. Calendar B is real only if the cap is ≥ 14, or its extra slots are cloud sessions. Every build and test
still runs on the VPS, Codex sessions start lean (`tmux-run-lean.sh`), and `free -m` ≥ 7 GB is checked before any PC
Gradle run or emulator start (02 §7.8).

**Gate G-A, before fan-out (owner, memory `dev-freeze-native-rewrite-2026-11-01`).** One full Codex Astra
(`gpt-6-astra`) review of plan sections 01–16 runs in W1 days 1–2, and its blocker and major findings are fixed before
any cloud session starts and before any slice-0 unit other than S0-SKEL and S0-NETP starts. Those two start on day 1
on local slots and take the fixes at their next commit. Since every other S0 unit waits for S0-SKEL@2 (day 3) anyway,
the gate costs no calendar time.

### 2.1.1 Cloud sessions (in use since 2026-11-01; owner: "let it be wild")

**What changed.** The plan expected 3 cloud sessions from W3 (calendar C). Since 2026-11-01 up to **17 Claude Code
cloud sessions** build slice-0 and slice-1 units in parallel. On day one they pushed the slice-0 proofs P1, P2, P3, P5,
P6 and P7 (merged on `native/m0-2026-11-01`) and unit branches for analytics, core, data, net, netp, linux, flows, fx and
scope [R `git ls-remote`, 2026-11-01]. They started **before** gate G-A (the Astra review), so every one of those
branches is re-checked against the post-review plan before it merges (§3.2), and the slice-0 reports already list
their deviations (ADR §11 status table, 03 §0.3, §22.5).

**The recipe** (memory `claude-code-cloud-howto`, `push-freely-snapshot-recipe`; the owner allows pushing work branches
and launching cloud sessions without asking; master, tags, releases, signing and publishing still need a GO):

1. **Launch** from the clean clone **`/home/eslam/Storage/Code/cloud-launch`** with `claude --cloud`, in tmux (the
   command needs a TTY). Never from the main repo, whose `origin` is the old repository. The coordinator first pushes
   a snapshot of `native/main` (§11.0) and checks the clone out at that snapshot branch.
2. **Effort:** high for hard units (chat engine and transcript, migration, protocols, the kit owner's units), medium
   for the rest. Set in the launch, never raised mid-unit without a reason in the ledger.
3. **First command in the session:** install the Android SDK (cmdline-tools, `platforms;android-37.1` (compileSdk 37
   minor 1, ADR §8), `build-tools;37.0.0`) into `/opt/android-sdk`; run Gradle on JDK 21; raise
   `BASH_DEFAULT_TIMEOUT_MS` and run Gradle detached with a done-file. Maven Central answers HTTP 429 to many parallel
   sessions: install the local-only init script that puts Google's Central mirror first
   (`~/.gradle/init.d/central-mirror.gradle.kts`); it is never committed [R every slice-0 report].
4. **No emulator, no phone:** T0/T1 on `desktopTest` and Android host tests only. A unit that needs a device step is not
   cloud-eligible; its device part is written as "emulator steps for the coordinator" and run on `emulator-5584`.
5. **Results come back as pushed branches** (`native/<unit>-<date>` or `cloud/<unit>`), each with a report in
   `docs/native/…`. Never master, tags or releases.
6. **Before each push: scrub.** Scan the added lines for key patterns, the owner's e-mail, passwords and **private IP
   addresses** (RFC 1918, `100.64.0.0/10` tailnet, link-local) and private host names (`native/tool/scan_push.sh`,
   written by S0-GATES; until it exists, the session greps by hand and says so in its report). A hit blocks the push.
   Author and committer are the GitHub no-reply address (memory `public-commit-email`).
7. **Bring back (coordinator):** fetch the branch, rebase it onto the `native/main` commit its snapshot was cut from,
   then the normal merge with T1 and T2 (02 §9.2). Never merge the snapshot itself. Shared files (`MODULE_OWNERS`,
   `ModuleRules.engineOrder`, the catalog) are coordinator-only: sessions list the rows they need in their report.
8. **Credit:** usage counts against the owner's plan and the cloud credit [R memory]. The coordinator records session
   hours per unit in `native/LEDGER.md`; at M0 it computes cost per agent-day [U] and stops the lane, with a line to the
   owner, before the credit runs out.

**What it does to the dates (honest).** Builder capacity is no longer the limit for emulator-free work. The limits now
are: the coordinator's review and merge rate (every branch rebased, T1/T2 on the one VPS); the one emulator for every
device step (§2.5), which no cloud session can use; the Codex/Opus review gates; the owner's answers; and CMP 1.13
stable (projected mid-December [U]). Day one shows the risk too: 15 branches, none run on a device, two red
`ModuleOwnersTest`s waiting on the coordinator. So the calendars in §2.3 are **not** moved yet. The M0 re-plan (§2.4)
measures merged-and-green agent-days per week, not pushed branches, and only then moves dates; calendar C (6 local
slots + 3 cloud) is now a floor for capacity, not a forecast.

### 2.2 Effort per slice, by track (central [E])

| Slice | Codex (core/protocol/engine) | Sonnet (kit/features/Android) | Opus (hard units) | Total builder ad |
|---|---|---|---|---|
| S0 | 11 | 7 | 32 | 50 |
| S1 | 58 | 31 | 19 | 108 |
| S2 | 30.5 | 56.5 | 45 | 132 |
| S3 | 54 | 92.5 | 22.5 | 169 |
| S4 | 27 | 71 | 24 | 122 |
| S5 | 63.5 | 94.5 | 11 | 169 |
| S6 | 0 | 23 | 19 | 42 |
| **Total** | **244** | **375.5** | **172.5** | **792** |

Mixed units (for example "Codex + Sonnet") are split half and half; coordinator units count as Opus. Codex carries about
a third of the builder load, so a Codex quota stop (R15-12) matters: every Codex unit's brief names the Sonnet agent
that takes over, and the cloud-eligible ones can move to the cloud lane.

### 2.3 The two calendars and how sensitive they are

Computed by Appendix A (`plan_calendar.py`) from the unit tables in §4–§10, at 0.9 planned agent-days per slot-day.

| Throughput factor (`--eff`) | Calendar A (6 slots): week each slice lands | Calendar B (9 slots from W3) | Calendar C (6 slots + 3 cloud from W3) |
|---|---|---|---|
| 0.7 (slower than planned) | S1 W9 · S2 W17 · S3 W24 · S4 W30 · S5 W38 · **S6 W41** | S1 W9 · S2 W13 · S3 W19 · S4 W22 · S5 W27 · **S6 W29** | S1 W9 · S2 W13 · S3 W21 · S4 W26 · S5 W32 · **S6 W35** |
| **0.9 (plan of record)** | S1 W7 · S2 W14 · S3 W19 · S4 W24 · S5 W31 · **S6 W32** | S1 W7 · S2 W11 · S3 W15 · S4 W18 · S5 W22 · **S6 W23** | S1 W7 · S2 W11 · S3 W17 · S4 W20 · S5 W26 · **S6 W27** |
| 1.17 (Flutter-era pace) | S1 W6 · S2 W11 · S3 W16 · S4 W19 · S5 W24 · **S6 W25** | S1 W6 · S2 W9 · S3 W12 · S4 W14 · S5 W18 · **S6 W19** | S1 W6 · S2 W9 · S3 W14 · S4 W17 · S5 W21 · **S6 W22** |
| 1.5 | S1 W5 · S2 W9 · S3 W12 · S4 W15 · S5 W19 · **S6 W20** | S1 W5 · S2 W8 · S3 W10 · S4 W11 · S5 W14 · **S6 W15** | S1 W5 · S2 W8 · S3 W11 · S4 W13 · S5 W16 · **S6 W17** |

B and C together (9 slots + 3 cloud) give S6 W20 at 0.9. The S6 week is the switch candidate on the emulator, with the
`switch` test tier in its nights (§2.5). The owner's two-day phone test (02 §6.7) is the next week, the pre-release the
week after, and "latest" one week later (§11.4).

### 2.4 Re-planning (at M0, then at the end of M2 and M4)

The coordinator does exactly this, in this order:

1. List every unit that finished since the last re-plan, with its planned ad (unit table) and its actual ad (agent
   start to merge, from `native/LEDGER.md`, counting only working time of that agent).
2. Factor `f = Σ planned / Σ actual`. If fewer than 5 units finished, use them anyway and mark the result [E].
3. Run `python3 -I native/tool/plan/plan_calendar.py --eff <0.9 × f> --asks` for calendar A, again with
   `--slots-from-w3 <slots>` if a second VPS exists (slots capped by the PC measurement of §2.1), and again with
   `--cloud <sessions>` while the cloud lane runs. Separate factors for cloud and PC units if they differ by more than
   20 %.
4. **Emulator hours.** From `native/EMULATOR.md`, sum the booked hours of each week since the last re-plan against the
   supply of §2.5 (12 h of day blocks + 9 h of night block per day). If demand exceeded supply in two of the last four
   weeks, or the S6-week demand table of §2.5 does not fit the projected S6 week and the week before it, move the
   owner-phone week one week later in §1.1 and offer D15 option (d), a KVM test host.
5. Write the new dates into §1.1 of this file (the coordinator edits it; nobody else does), with
   "re-planned <date>, f = <value>", and the new ask weeks into §17.1.
6. If the projected S6 week is more than two weeks later than the plan of record (later than **W34** on calendar A),
   prepare the scope-valve proposal (§19) and send it to the owner as one decision. Never cut a row without the
   owner's OK (ADR §10.1).
7. Tell the owner in four lines: what moved, why, the new P1 and switch weeks, "Decision needed: yes/no".

Also measure at M0: P10 (lines and rows per agent-week, ADR §11), P8 (build cost) and the PC session cap (§2.1). If P8
shows the desktop compile doubles T1 time, switch to the nightly-only desktop compile (ADR §4 row 17) before S1
fan-out.

### 2.5 The one emulator (the real bottleneck)

All device work shares one PC emulator. Rules:

1. **Prefer tests that need no emulator.** A builder may book the emulator only for what desktop tests and Robolectric
   cannot show: gestures, IME, real notifications, widgets, the camera, proot, the upgrade journey (testing-perf §0).
2. **Bookings** live in `native/EMULATOR.md` (coordinator-owned): one line per booking
   `<date> <from>-<to> <unit> <purpose> <apk versionCode>`. Unbooked use is not allowed.
3. **Daily blocks** (local time): 09:00–12:00 T3 holder (flows, sweeps); 13:00–16:00 builder device checks, at most
   90 minutes each; 16:00–18:00 T4; 19:00–23:00 migration upgrade journeys (long, snapshot-based); **23:00–08:00
   unattended runs** of the `full`, `switch` and `upgrade` tiers and monkey (14 §4.8), started detached by the T3
   holder with a done-file, the run recorded in `EMULATOR.md`. That is 12 h of day blocks and 9 h of night per day. The
   quiet-hours rule is lifted (owner 2026-11-01); if reinstated, nothing runs 00:00–02:00 and the night block shrinks
   to 7 h.
4. **Priority when two bookings clash:** (1) a candidate APK going to the owner, (2) upgrade journeys, (3) slice-end
   flows, (4) builder device checks, (5) T4.
5. **Hand-off:** release the lock by killing the `sleep` child, confirm with `flock -n <lock> true`, restore font scale
   and density, and write the installed versionCode into `native/BUILD_NUMBERS.md` (02 §7.9).
6. If bookings are full for three days running, the coordinator asks the owner about a KVM-capable second test host
   (risk R15-09). Whether a rented VPS offers `/dev/kvm` is [U] per provider.
7. **Sweep matrix.** The Thursday sweep (§2.7) covers the screens merged that week on `OCN_A` in light and dark, English
   and Arabic, plus two passes on the same screens: `OCN_T` in landscape (rail and sidebar, component max widths, owner
   rule `component-max-width-rule`) and font scale 200 % (frame text capped at 1.3×, reading text scaling fully,
   `text-scale-cap`). The slice-end sweep (§3.3 item 4) runs the whole matrix on every screen the slice touched.

**Emulator demand in the switch window [E].** The S6 week and the week before it must hold all of this; the re-plan
(§2.4 step 4) checks it against measured durations.

| Work | Hours [E] | Block |
|---|---|---|
| `switch` tier: `full` twice with `--no-retry`, smoke three times, monkey 3 × 50,000 (14 §4.8) | ≤ 24 | three nights |
| J1–J8 on the owner lineage and from the bridge (05 §10.5); the public-lineage legs on the CI draft (§11.2) | ≈ 9 | evenings |
| T4 S-5…S-20 on the candidate (S6-PERF) | ≈ 8 | 16:00–18:00 and one night |
| S6-AUDIT and S6-A11Y screen passes | ≈ 18 | mornings |
| `OCN_36` and `OCN_26` smoke on the candidate (§11.2) | ≈ 3 | one evening |
| **Total** | **≈ 62** | supply in two weeks: about 290 h (day + night blocks) |

It fits only with the night block. Without it (≈ 168 h of day blocks in two weeks, shared with builder checks), the
owner-phone week moves one week later.

### 2.6 Build numbers

| Flavor | Range | Rule |
|---|---|---|
| Main (`io.github.eslamasabry.opencode_mobile`) | **≥ 10000, no upper limit, no fixed start** (Astra M5) | One allocator and one ledger for every APK of every package (05 §9.1: `next_version_code.sh` + `docs/release/version-codes.md`): native candidates, releases, the bridge, R1 rollback builds and journey builds. Each number = above the largest number in the ledger, on the APK share server, installed on the owner's phone or on `emulator-5584` (signed local builds 900055 and 900058 exist on emulators, 11 CR11-18, so the next main-package number today is above 900058 unless those AVDs are wiped and recorded). The public switch release keeps the versionCode of the candidate the owner approved. Every produced APK is checked against its ledger row (`aapt2 dump badging`) before it is hosted. |
| Preview (`….native`) | its own package, same allocator and ledger | Agents get blocks of 10 in install order (02 §7.9); each owner-facing preview takes "highest number installed on the emulator or the owner's phone + 10". Keeping the flavors in separate ranges makes every number say which app it is. |
| Bridge (Flutter) | below the first native number | from the same allocator and ledger (no separate `BUILD_NUMBERS.md`); public `2.2.0+58` [E]. Local builds up to 4511 exist today, so the owner-lineage bridge is ≥ 4521, and the first native candidate is above every bridge number |

### 2.7 The coordinator's week

| Day | Work |
|---|---|
| Monday | merge queue (02 §9.2), T2 on the VPS, `native/LAST_GREEN`, `docs/native/parity.md`, `twin_check.sh`, merge the Flutter freeze line into `native/main`, push a fresh `snapshot/native-main-<date>` for the cloud lane (§11.0), write briefs for every slot that frees this week, send the owner message (§15) |
| Every day | merges with T1 on the merged result (cloud work brought back with §2.1.1 step 6); T2 at least once on days with merges; kill own Gradle/Kotlin daemons by PID; `df -h /` on both machines and `free -m` on the PC |
| Thursday | T3 holder sweeps the screens merged this week with the §2.5 item 7 matrix; the coordinator adds every UI regression found where a ported widget test would have caught it to "Widget-test trigger" in `native/LEDGER.md` (14 §5.2) |
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
5. Every map feature landing in the slice (§13) has an owning unit, and so does every item of 02 §3.5 marked "Switch"
   and every item of 02 §3.7 that the slice touches. The table in §13.13 names the unit of each; a "Switch" item with
   no unit blocks the slice's entry.

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
9. Commit with the 02 §9.1 message format, `Behaviors:` trailer included. A builder on a PC slot never pushes; a cloud
   session pushes only its `cloud/<unit>` branch, after the §2.1.1 step 5 scan.
10. Report in the 02 §7.14 format, including what is **not** proven.

### 3.3 Exit (slice done, 02 §6.7) and the preview hand-off

1. Every row with `slice = N` is green at T2 on a `native/main` commit.
2. The flows assigned to the slice (§14.2) are green at T3 on the **release** build.
3. The slice's T4 journeys are within budget, or the miss is written down with its owner.
4. The T3 holder ran a full screen-by-screen sweep of every screen the slice touched, with the whole §2.5 item 7 matrix
   (light/dark, English/Arabic, `OCN_T` landscape, 200 % font scale).
5. The coordinator installs the preview on the emulator, then hands it to the owner with five to eight "what to try"
   lines, one-screen images and one short video (owner rules `one-screen-per-image`, `live-video-proof`).
6. A slice QA record `docs/qa/native/slice-<N>-<date>/README.md` exists. It lists the slice's "Widget-test
   candidates" (14 §5.1 item 3) and the widget-test trigger count; if the slice found 5 or more regressions a widget
   test would have caught, or the owner found 2 or more in one preview, the coordinator asks the owner to start the MW
   phase at the next slice (14 §5.2, C15-15).

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
| S6 | Shorebird; the preview flavor's kit-tour entry; the Flutter tree on `native/main` follows one commit after the release is marked latest (C15-7), and reaches `master` as one more snapshot commit (§11.6) |

---

## 4. M0 — Slice 0: foundations, skeleton, migration spike, test harness (W1–W2)

- **Finish line:** a `native/` tree on `native/main` assembles both flavors' release skeleton, every ADR proof P1–P10
  has a written verdict with its fallback taken where needed, the slice-1 contracts are frozen (compiled with
  consumers, 03 §23 check 15), and the Flutter reference has baselines; the parity flows run from W4.
- **Non-goal:** any feature a person can use.
- **Owner sees:** W1 one image (the native "Opening…" screen on the emulator); W2 a video of under two minutes: the
  back gesture from a chat to its row with the grow-from-row motion, plus the 5,000-part streaming test; W3 the
  re-planned dates in four lines.
- **Rows:** ≈ 12 [E] (BUILD 6, XPLAT 3, ANDR 2, SEC 1) plus spike evidence for KIT, MIGR and L10N rows (no green claim).
- **Entry:** gate G-A (§2.1) for every unit except S0-SKEL and S0-NETP. **Exit (M0 gate, end of W2):** 02 §10
  items 1–4, 6, 8, 9 and 10 done; item 5 by end of S1; item 7 as C15-6 says; the M0 rows of the one gate checklist
  pass (03 §23 checks **1–15 and 17**, and check 16's harness; generated from `native/config/gates.tsv`, never retyped;
  Astra M6); the PC session cap is measured (§2.1); the §2.4 re-plan is done.
- **What M0 does not promise (Astra M6):** the Flutter parity flows finish in W4 and the catalog top-up in W5, so at M0
  only the slice-1 contracts freeze, and only after their catalog delta and baseline tests exist; slice 2–3 contracts
  freeze at the entry of their slice (§3.1), each with its own catalog delta. The M0 re-plan models the test lanes,
  the coordinator's merge capacity and the one emulator's bookings as resources in `plan_calendar.py` (`--resources`),
  not as assumptions. K0, P3 and P6 may finish in the first two days of W3
  (C15-8).

### 4.1 Builder units (50 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S0-SKEL | Opus (build-logic owner) | `native/{settings.gradle.kts,build.gradle.kts,gradle.properties,version.properties,gradle/**,build-logic/**,androidApp/**,quality/**,config/**,AGENTS.md,MODULE_OWNERS,JETPACK_AHEAD.md}`; of `native/desktopApp/**` only the build files and the entry point (`build.gradle.kts`, `src/**/Main.kt`) | Both flavors assemble, the Metro graph smoke resolves a `ConnectionScope` extension and two contributed entry installers, and the nightly iOS klib job is green for `:core:*` (ADR P1). | Kit parts, engine logic, screens. | gates only | — (first commit due end of day 2) | 6 | W1–W2 | ADR §11 P1; 03 §22, §23 |
| S0-KIT | Opus (kit owner) | `native/ui/{design,kit,testing}/**`, `native/contracts/kit-v1.md`, the gallery sources of `native/desktopApp/**` (`src/**/gallery/**`; S0-SKEL owns its build files) | `KitTappable` is tested three ways (desktop compose test, Robolectric, Roborazzi light/dark en/ar on the VPS), `KitFrameText` holds the 1.3× cap at 200 % with non-linear scaling, ripple and haptic are seen on the emulator, and the 20 signatures are frozen (ADR P7). | The K1 part list. | B-KIT-006, B-KIT-008, B-STATE-003, B-MIGR-005 (08-M0) | S0-SKEL@2 (convention plugins) | 10 | W1–W3 | 06 K0; 08 §11 08-M0 |
| S0-CORE | Codex | `native/core/{common,model,domain,security,testing}/**`, interface part of `native/contracts/s1.md` | The five core modules compile for android, desktop and iOS klibs; all 85 `ServerCapabilities` defaults equal the Dart vector file; `Outcome`/`Failure`, gateways, `GatewayEvent`, `StreamStatus`, `NetworkStatus` are frozen. | Implementations behind the interfaces. | B-PROTO-137; capability snapshot; Redactor vectors | S0-SKEL@2 | 6 | W1–W2 | 04 WP-04.0; 03 §22.4 |
| S0-NAV | Opus | branch `spike/p2-predictive-back`; `native/docs/spikes/P2.md` | Option A of the list → chat transform passes the six checks of motion-native §4.4 on the emulator; the option B spike result is recorded (ADR P2). | Production shell code. | B-KIT-054 (evidence) | S0-SKEL@2 | 5 | W1–W2 | 07 §3.2, §11; 08-M1 |
| S0-MIGR | Opus (migration owner) | `native/android/migration/**` (spike), branch `spike/p5-identity`, `native/docs/spikes/P5.md` | A local-key skeleton installed with `adb install -r` over a **local-key build of the 2.1.0+57 tree** (`e44b581aa`, the tree of the public release; its build number from `BUILD_NUMBERS.md`, below 10000) adopts the prefs file, decrypts one v11 secret, leaves the rootfs byte-identical, keeps both widgets, both tiles, a pinned shortcut, a schedule and a channel setting, and records the step-0 time and the Room trigger numbers (ADR P5). The public APK of 2.1.0+57 is signed `842284B2…` and refuses a local-key update (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); its leg waits for the CI draft (§11.2). | The runner, gate UI, any step beyond S0 and one secret. | B-MIGR rows (evidence) | S0-SKEL@2; emulator booking | 5 | W1–W2 | 05 W1, W8 (J1 short form) |
| S0-STREAM | Opus | branch `spike/p3-transcript`; `native/android/benchmark/**` spike | A recorded 5,000-part stream through a throwaway `TranscriptEngine` and a block `LazyColumn` with the draw-phase fade matches the Flutter publish counts; in a release Macrobenchmark on `OC_API37` (with `androidx.benchmark.suppressErrors=EMULATOR`, N ≥ 10, quiet PC) its frame overrun is no worse than the S0-BASE Flutter streaming baseline on the same AVD; P4 recomposition counts are recorded with and without `compose-stability.conf`. Absolute frame numbers come from the NX721J only with the owner's OK (C15-16). | Production engine code. | — | S0-SKEL@2; S0-BASE | 4 | W2–W3 | ADR P3, P4; 09 §17 S0-P3; testing-perf §9 |
| S0-NETP | Codex | `spike/pn-*` branches; `docs/qa/native-oc2-probe-2026-11/README.md` | P-N1…P-N6 have verdicts (WebSocket over OkHttp, pinned TLS under Conscrypt, SSE against live OpenCode 2.1.1, the 2.1.1 vs 2.2.5 probe, the retry trap, network change). | Production network code. | — | — | 5 | W1–W2 | 04 §17 |
| S0-WORDS | Sonnet (l10n owner) | branch `spike/p6-strings`; early `native/tools/strings/**` | ARB → CMP XML converts, Android 13 per-app language switches a screen and a notification at runtime, Arabic plurals are right, several `strings_<feature>.xml` per values folder work (ADR P6). | Converting all strings (S1-WORDS). | B-L10N rows (evidence) | S0-SKEL | 3 | W2–W3 | 06 §13, W1 |
| S0-GATES | Sonnet + coordinator | `native/tool/**`, Konsist rules in `native/quality/**` | `t0.sh`…`t4.sh`, `vps_gradle.sh`, `check_write_set.sh`, `twin_check.sh`, `check_commits.sh`, `snapshot_push.sh` and `scan_push.sh` (§11.0, §2.1.1: tree-equal snapshot, noreply author, secret and private-address scan) each ran once and wrote a summary; Konsist catches a planted `material3`, `LocalContext` and raw `clickable`; a Termux `TerminalView` renders through `PlatformViewHandle` (ADR P9). | New gate ideas. | — | S0-SKEL | 4 | W2–W3 | 02 §8, §10 item 3 |
| S0-ROUTES | coordinator | `native/ui/routes/**`, id types in `native/core/model/**` (with the S0-CORE owner) | Route keys for slices 1–3 exist; `RouteRoundTripTest` and `RouteKeyContentTest` are green. | Screens. | — | S0-SKEL | 2 | W2–W3 | 07 §2.2, §11 |

### 4.2 Lane units (56 ad, + 2 if S0-NOTICE runs)

| Unit | Who | Write set | Finish line | ad | Weeks |
|---|---|---|---|---|---|
| S0-SCOPE | coordinator + 1 Sonnet (the test-writer lead appends rows) | `native/catalog/**`, `ScopeFileTest` in `native/quality/**`, `native/tools/flutter-fixtures/widget_index.py` | Matches 02 §10 item 4: `scope.jsonl` seeded by Appendix B (FORCE table first), then **every row read by its text** (02 §3.3 rule 2), a `reason` on every move-out, every row given a `module` and an owning unit, the 02 §3.6 amendments and the B-NAV-061/B-ANDR-071 re-scope (N4-O4) entered; the sibling-defined rows appended with free ids (B-PROTO-128…148 from 04 §15.5, B-SEC-184…199 from 16 §14, B-MEDIA-035…037 from 10; no id collision exists today, 16 cites 04's B-PROTO-128/139 with the same meaning); the widget-test name index `native/catalog/widget-index.tsv` written (14 §5.1 item 2, half a day); `ScopeFileTest` green | 7 | W1–W2 |
| S0-FX | Codex | `native/fixtures/**`, `native/e2e/fixtures/**` | FX-OC2 (stub model with the `e2e:` prompts), FX-OC2-B, FX-TLS, FX-PASEO, FX-CODEX, FX-GH, FX-GIT, FX-LINUXDL, FX-USAGE, FX-AUTH start, stop and log as e2e-flows says; started and stopped by exact PID | 6 | W1–W3 |
| S0-FLOWS | FL ×2 + T3 holder | `native/e2e/flows/**`, `native/e2e/runner/**` | All 39 flows written; slice-1/2 flows green on Flutter R by end of W2, all by end of W4, or each gap written to `KNOWN-FLUTTER-GAPS` | 20 | W1–W4 |
| S0-BASE | T4 holder | `docs/qa/native/baseline-flutter-<date>/**` | 02 §4.1 baselines recorded on the same AVD (phone half only with the owner's OK) | 3 | W1–W2 |
| S0-FREEZE | coordinator | the Flutter freeze line only | The freeze gate passes (split suite, release APK, emulator sweep); the `flutter-final` candidate is named; every in-flight branch has its ADR-D16 answer (land or port from branch), including `feat/role-voices-2026-11-01` | 3 | W1–W2 |
| S0-BRIDGE | Sonnet on the Flutter freeze line | `lib/` census file + its test (05 W13); the Gemma entry points; `lib/update/{app_release,desktop_release_check}.dart` and the bridge ABI pre-check with their tests; the `on:` blocks of `.github/workflows/{android-release,desktop-linux,byo-host-bundle}.yml` | (1) The counts-only census is in the bridge candidate (if M-D2 is yes). (2) The Gemma assistant's entry points are hidden (Settings row, search entry, chat menu item; 02 §5.7, S02-3), with a Flutter test. (3) `AppRelease.apkFor` falls back to exactly `opencode-mobile-<version>.apk`, never another split. (4) If the 32-bit decision (§17.1) is "no 32-bit": the ABI pre-check of 16 §7.5 (WP-16.15) says "Version 3 needs a 64-bit phone. This version keeps working." and offers nothing, with its Flutter test. (5) `desktop_release_check` ignores releases with no `opencode-mobile-linux-` asset. (6) The three tag workflows run only on `v2.*` tags (§11.4 release channels). Proof on the emulator through `--dart-define=OC_APP_UPDATE_FEED`: the updater accepts a 10000-range versionCode and the native asset names; a feed with `-arm64-v8a`, `-x86_64` and universal assets and no `-armeabi-v7a` gives the universal file on an x86-only image and the 64-bit line on a 32-bit image | 5 | W2 |
| S0-NOTICE (optional, owner GO, D15-9) | Sonnet on the `patch/2.1.0-p1` line | the update-notice files of that line | A link-only Shorebird **patch** to v2.1.0+57: once the bridge is published, a status-line notice "A new version is ready" whose Open button goes through `openExternalLink` to the release page, reusing the `desktop_release_check` code path; Dart only; no installer | 2 | W3 |
| S0-TW1 | TW | test files of slice-1 modules | Every slice-1 row has a red test that fails for the reason the row describes | 8 | W2–W3 |
| S0-TOPUP | TW lead | `native/catalog/behaviors.jsonl` copy, `scope.jsonl` rows | Rows for the logic tests added or changed in `65e3f70a3..flutter-final` (02 §3.5): 66 new test files (≈ 459 tests, ≈ 100 rows at the catalog's 4.3 : 1 [E]) and 396 changed files read for amendments; every in-flight branch decided at the freeze gate (ADR-D16, W2) and anything landing later added as "port from branch" rows | 4 (+ 4 in S1) | W2–W5 |

### 4.3 Launch order and slots (calendar A)

| Slot | W1 | W2 | W3 (first days) |
|---|---|---|---|
| B1 | S0-SKEL | S0-SKEL → S0-ROUTES | S1 starts |
| B2 | S0-KIT (from day 3) | S0-KIT | S0-KIT (to mid-week) |
| B3 | S0-CORE (from day 3) | S0-CORE → S0-WORDS | S0-WORDS |
| B4 | S0-NAV (from day 3) | S0-NAV → S0-GATES | S0-GATES |
| B5 | S0-MIGR (from day 3) | S0-MIGR → S0-STREAM | S0-STREAM |
| B6 | S0-NETP | S0-NETP → S1-DATA (early start) | S1-DATA |
| Lanes | G-A Astra review and fixes (days 1–2), FL ×2, FX, T4 (baselines), coordinator (freeze gate, scope) | + TW1, TOPUP, BRIDGE, PC session-cap measurement | + cloud lane on calendar C; S0-NOTICE if GO |

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
- **Rows:** ≈ 140 [E] (PROTO 35, SEC 35, KIT 27, STATE 15, L10N 12, NAV 5, ANDR 5, AGENT 4, others 2), plus most of the
  sibling-defined PROTO and SEC rows (§14.1).
- **Flows:** none required; the FL lane finishes all 39 on Flutter R by W4.
- **T4:** shell cold start (early S-5 and S-7 readings).
- **Entry:** M0 gate. **Exit:** §3.3 items 1, 4–6; P0 handed over.

### 5.1 Builder units (108 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S1-DATA | Codex | `native/core/data/**` | `KeyValueStore` over the adopted prefs file (Long ints, both list encodings, the double prefix, `DartJson`), `WriterLane`, `ProfileScope` and its sweep, `SecretStore` (Keystore AES-256-GCM) and the `MigrationGate` interface are green on the real-install fixture. `ProfileScope` registers on day 1 the shapes of the deferred features too (02 §3.7 item 3: `oc.orchestration.<id>.*`, `oc.team*.<id>`, `files/oc.teamEngine.<id>/`, `files/oc.teamEngineDeletion.<id>`, `oc.teamEngineAuth.<id>`, `oc.factsbook.<id>`, `oc.factsbookOwner.<id>`, `oc.factsbookOutbox.<id>`, `oc.factsbookNode.<id>`). | The runner; Room; `BlobStore`. | 05 §10.2 rows of W2–W4; `PrefsLedgerTest`, `ProfileSweepShapesTest` | S0-CORE | 11 | W2–W5 | 05 W2–W4; 02 §3.7 |
| S1-SECNET | Codex | `native/core/security/**`, `native/core/net-okhttp/**` | Address, redaction and link vectors and the hardened OkHttp builder with the leaf-pin trust manager pass; no `java.*` in common code (klib job). | Ktor client, SSE. | B-SEC-007, 113, 119…125, 153, 154, 174; B-PROTO-072, 128…131 | S0-CORE | 7 | W3–W4 | 04 WP-04.2, 04.3 |
| S1-NET | Codex | `native/core/net/**` | The Ktor client factory, the SSE parser (16 MiB cap, 10k-case fuzz), the `EventStreamLoop` timing table under virtual time and the WebSocket path from P-N1 pass. | Protocol DTOs. | B-PROTO-033, 132, 133 | S0-CORE, S0-NETP | 6 | W3–W4 | 04 WP-04.4 |
| S1-OC2 | Codex (Opus reviews the event adapter) | `native/protocol/oc2/**`, `native/fixtures/oc2/**` | All 24 Flutter fixtures decode to the same variants as Dart, and live proof steps 1–25 pass against OpenCode 2.1.1 on the VPS. | PTY, MCP, files, plugins (S5). | B-PROTO-010…019, 021, 027, 034, 037…039, 047, 061, 062, 089…093, 134, 136; B-CHAT-005; B-SEC-055, 056 | S1-NET | 12 | W4–W7 | 04 WP-04.5 |
| S1-PACER | Codex | `native/core/common/**/pace/**`, `TestPacer` in `native/core/testing/**`, `TimerTableTest`, Konsist K-P1/K-P2 in `native/quality/**` (with the S0-GATES owner) | `Pacer` and the timer tables exist; every timer an engine will port is a `TimerSpec` row; K-P1/K-P2 fail on a planted raw `delay` loop; PB-42 green (13 WP-13.5). | Changing a cadence value except the table's "native change" rows. | timer rows of 13 §2.6 | S0-CORE | 3 | W3 | 13 WP-13.5 |
| S1-CONN | Codex | `native/engine/connection/**` | The connect → stream → reconcile order passes with `FixtureEngine`; several live connections each get a `ConnectionGraph`; quiet reconnect timers are ported verbatim as `Pacer` timer rows; it emits its `AnalyticsSchema` event `backend_connected` (16 §5.3). | The status-line UI. | B-PROTO-094, 095, 124, 138; B-STATE-011 | S1-NET (OC2 through fakes until it lands), S1-PACER | 8 | W4–W6 | 04 WP-04.6, §12; 16 §5.3 |
| S1-ENG | Codex | `native/engine/{settings,diagnostics,profiles}/**` | The preferences facade, three-state consent reads, the teaching registry, the diagnostics ring with crash categories and the exit classifier, and the profile store with trust, pins and the backwards deletion transaction are green. Diagnostics reports crash, freeze and error categories to `:engine:analytics` (S1-ANALYTICS, below). | Settings screens (S4); the analytics core itself. | `scope.jsonl` filter on the three modules, slice 1 | S1-DATA, S1-ANALYTICS | 11 | W5–W7 | ADR §5.2; 16 §5, WP-16.7 |
| S1-ANALYTICS | cloud session (medium effort) + Sonnet (wiring) | `native/engine/analytics/**`; the wiring in `:androidApp`, `:android:platform`, `:core:data` | The analytics core of section 18 (owner, 2026-11-01): `:engine:analytics`, engine order #2, BASIC always on (the owner's list, 18 §2.1) and DETAILED opt-in, rotating install id, local queue and batching, background uploads only on unmetered networks, `PrivacyScrubber`, `WhatWeCollectSource`, `NoOpSink` bound for `sender=none`; then the platform wiring (18 §8 S1-ANALYTICS-WIRE). **Core built 2026-11-01** on `native/analytics-2026-11-01` (49 desktop tests green); merge needs the `MODULE_OWNERS` row and the `engineOrder` edit. | Any remote sink until D16-1; the "What we collect" page (S4-ANALYTICS). | B-SEC-191; proposed B-ANLY-001…015 (18 §9) | S0-SKEL | 3 (2 left: wiring) | W3–W5 | 18; 03 S27 |
| S1-KITA1 | Opus (kit owner) | `native/ui/{design,kit}/**`, part-A files listed in the brief | The 31 packs, accents, dynamic colour, the frames (`KitScreen`, top bar, status line and slot, rail, sidebar, page heading, system bars), the row family, `KitNotice` and `KitDetailsFold` meet 06 §16.3 with the capacity, manifest, Konsist and literal gates live. | Styles other than Solid (S4). | KIT rows of `:ui:kit`, slice 1 | S0-KIT | 10 | W3–W6 | 06 K1 |
| S1-KITA2 | Sonnet (kit agent under the owner) | part-B files of `native/ui/kit/**` | Buttons, action block, icon button, chips, `KitField` in all kinds, segmented, choice list, switch and expand rows, sheet, confirm, dialog, menu, undo, state view, loading bar, skeleton, progress, receipt and needs-you meet 06 §16.3. | Motion parts (S2-KITB). | same | S0-KIT | 10 | W4–W6 | 06 K1 |
| S1-WORDS | Sonnet (l10n owner) | `native/tools/strings/**`, `native/ui/words/**` | All 7 languages are converted once into `strings_<feature>.xml`, `strings_os` is generated into Android res, the drift check is green, and `convert-report.md` (unused keys) is reviewed. | New copy. | B-L10N rows, slice 1 | S0-WORDS | 8 | W3–W5 | 06 W1, §13 |
| S1-SHELL | Opus (coordinator role) | `native/ui/shell/**`, `native/androidApp/src/preview/**` (kit tour entry) | `App()`, the opening, gate and safe-mode screens, `ShellNavDisplay`, `AppNavigatorImpl` (rules N1–N9 table-tested), the rail and sidebar frame, the status slot and list-detail run on the emulator and in the desktop gallery; the preview flavor opens the kit tour. | Feature screens. | B-NAV-005, 024, 037 (navigator parts) | S0-ROUTES, S0-KIT | 7 | W5–W6 | 07 §2–§3, §11 |
| S1-LINUX | Sonnet (runtime owner reviews) | `native/android/linux/**` (copy only), rows in `native/TWINS.md` | Every built-in Linux Kotlin file is copied unchanged into `:android:linux`, and its 30 JVM tests and 11 instrumented acceptance classes pass with zero edits. | Any edit or split. | LINUX rows of the copied tests | S0-SKEL | 5 | W6–W7 | 11 §0.2 S11-1, §4 |
| S1-PLAT0 | Sonnet | three files in `native/android/platform/**` | `NativeCrashStore`, the network monitor and basic `DeviceFacts` run behind their `:core:domain` interfaces (C15-3). | Entry intents and pickers (S2). | ANDR rows of map features 42, 45 | S0-CORE | 3 | W6–W7 | 11 §1.1 rows 01, 42, 45 |
| S1-SECGATES | Sonnet + coordinator | K-SEC rules in `native/quality/**`, `androidApp/lint.xml` and the lint part of build-logic (with the build-logic owner) | K-SEC-1…12, the lint severity table, `ExportedSurfaceTest`, `NetworkSecurityConfigTest`, `FileProviderPathsTest`, `TlsFloorTest`, `QaHooksAbsentTest`, `RepositoryHygieneTest` and `CanaryLeakTest` each fail on a planted violation (P9 style) and pass otherwise (16 WP-16.2). | New rules beyond 16 §2. | B-SEC-150, 193, 194, 195; B-PROTO-139 | S0-GATES | 4 | W6–W7 | 16 §2, WP-16.2 |
| S1-STARTUP | Sonnet | `ManifestPerfTest` and the trace sections (with the `:androidApp` owner), `ReportStartup` (with the shell owner), the StrictMode `qa` setup | PB-03, PB-03b and PB-70 are measured on the skeleton; the SR1–SR10 start-up checks are green (13 WP-13.3). | Migration logic. | — (performance budgets) | S1-SHELL | 3 | W7 | 13 §4, WP-13.3 |

### 5.2 Lanes in S1 (30 ad)

S1-FIXT (Sonnet + Codex, 4 ad): fixtures copied with a SHA-256 manifest, a recorder that refuses a planted password,
the 2.1.1 `openapi.json` committed (04 WP-04.7). TW: slice-2 rows red (10 ad). FL: the remaining flows green on Flutter
R by end of W4 (part of S0-FLOWS). TOPUP: finished by the end of S1 (4 ad). Coordinator: bridge release in W3 if the
owner says yes (§11.1); weekly `twin_check.sh`; the first cloud sessions on calendar C (§2.1.1).

### 5.3 Launch order (calendar A)

W3: S1-DATA (running since W2), S1-SECNET, S1-NET, S1-PACER, S1-KITA1, S1-WORDS. W4: S1-OC2 and S1-CONN replace
S1-SECNET and S1-NET; S1-KITA2 replaces S1-PACER. W5: S1-ENG, S1-SHELL as slots free. W6: S1-LINUX, S1-PLAT0,
S1-SECGATES. W7: S1-STARTUP, then the first slice-2 units (S2-ENTRY, S2-MODEL, S2-KCB). Appendix A `--units` prints the
exact order for each calendar.

---

## 6. M2 — Slice 2: the chat daily path (W7–W14 on A, W4–W11 on B)

- **Finish line (09 §17, extended):** on the preview flavor, the owner adds their OpenCode 2 server by typed address or
  pairing code, sees Conversations, opens a chat, sends a prompt, watches a reply with code stream smoothly, stops it,
  sends while a reply runs (it steers), answers a permission once, scrolls up through older history without a jump,
  finds a word, and, with the network off, sees the prompt wait and never sent twice.
- **Non-goal:** drafts saved across restarts, attachments, code colours, forms, the phone's Linux, agents,
  notifications, widgets. (These are the known gaps of P1; 09 §17.)
- **Owner sees:** P1 "chat with your server" (W14 on A); weekly videos of streaming from W10; the J1–J3 numbers next to
  Flutter's.
- **Rows:** ≈ 136 [E] (STATE 45, CHAT 30, PROTO 17, NAV 17, ANDR 8, SEC 7, others 12).
- **Flows green at T3:** E2E-01, E2E-03, E2E-04, E2E-05, E2E-10. Partial and tracked: E2E-21 steps 1–7, E2E-02 typed
  and code steps, E2E-06 approve and deny.
- **T4:** J1 open a chat (S-11), J2 stream (S-9, S-12), J3 fling (S-10), cold start (S-5…S-7); first Baseline and
  Startup Profiles.
- **Entry:** contract s2 frozen (including ADR-09-1, C15-5); M1 units S1-CONN, S1-DATA, S1-KITA1/2, S1-SHELL merged.

### 6.1 Builder units (135 ad: 132 + S2-TEACH, added by 17; the coordinator re-runs `plan_calendar.py`)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S2-ENTRY | Codex | `native/engine/entry/**`, `EntryIntentReader.kt` in `native/android/platform/**` | `EntryRouter` holds every launch request until bootstrap and the gate open, consumes each exactly once and emits `EntryTarget`s; the 07 §7.4–§7.9 tables pass as common tests; the ADR §9.7 bootstrap order is wired and emits `app_open` after the gate opens (16 §5.3). B-NAV-061 and B-ANDR-071 enter **amended** (N4-O4): their address-link (`session/v2`, descriptor fetch) clauses are dropped; the plain session link and the intent-capture clauses stay (07 §7.6). | Widget, tile and notification entries (S4); `SystemEventRestorer` (S4-RESTORE). | B-NAV-005, 025, 054, 061 (amended), 063, 064, 077; B-ANDR-027, 028, 071 (amended), 073, 075 | S0-ROUTES, S0-CORE | 5 | W7–W8 | 07 §7 |
| S2-MODEL | Codex | chat types in `native/core/model/**`, `ToolPresentation` in `native/core/domain/**` | The 09 §2 types and `ToolPresentation` are green with their trap rows. | Wire adapters. | 09 §2.3 rows | S0-CORE | 3 | W7–W8 | 09 §2 |
| S2-MD | Codex | `native/core/markdown/**` | Streaming blocks, path chips, agent-block scanners and `proseForSpeech` pass on the ported fixtures. | UI. | CHAT Markdown rows | S2-MODEL | 5 | W8–W9 | 09 §9.1 |
| S2-TX | Opus (chat lead) | `native/engine/transcript/**` | Events become newest-first rows with version fences, merge and passes, published at most every 50 ms with the first delta at once; the 09 §14.2 counters are green. | The list UI. | CHAT-GENUI 01–06 engine rows; B-PROTO-015, 040, 088 | S2-MODEL, S2-MD@3 | 12 | W9–W12 | 09 §3 |
| S2-DLV | Codex | `native/engine/delivery/**`, send files of `native/protocol/oc2/**` | Bubble-first send, the steer table (never a queue where steering exists), Stop, the live line, and the offline queue with dispatch markers and command receipts, reading a real Flutter `oc.offlineQueue` blob unchanged (C15-1). | Drafts, attachments (S3). | B-STATE-008, 009, 033…046, 066, 067; B-PROTO-023…025, 028, 032, 042, 043, 045, 073, 074, 135 | S2-TX@4, S1-DATA | 10 | W10–W12 | 09 §4; 04 WP-04.8 |
| S2-REQ | Codex + Sonnet | permission and one-question parts of `native/engine/requests/**`, request files of `native/feature/chat/**` | A permission and a one-question ask are answered in the chat exactly once, with the 3 s hold. | Forms, always-allow, notifications. | CHAT-GENUI 43, 44 rows; OC2 permission dispatch rows | S2-TX | 5 | W12–W13 | 09 §5; C15-5 |
| S2-KCA | Opus | transcript, stream and Markdown files of `native/ui/kit-chat/**` | `KitTranscriptList`, `TranscriptListState`, `RevealRegistry`, the fresh-word fade, grow-only, the stream caret, the `KitTurn` family and the Markdown block renderers render the P3 recording within budget. | Composer parts. | B-CHAT-060, 096; kit-chat rows | S1-KITA1, S2-MD | 13 | W9–W12 | 06 KC2; 08-M4; 09 §7–§8 |
| S2-KCB | Sonnet (kit-chat owner) | message, tool, work, request and composer files of `native/ui/kit-chat/**` | `KitMessage` with lift, `KitToolRow`, `KitWorkLine`, `KitQueuedMessage`, `KitRequestCard` v1, `KitComposer` v1 (field, Send/Stop, chips row, frost) and `KitStartBox` meet 06 §16.3; the composer rides the IME frame by frame on the emulator. | Tray, attachments, voice pill. | composer KIT and CHAT rows | S1-KITA2 | 10 | W7–W9 | 06 KC2 |
| S2-KITB | Sonnet | the K1 rest and K2 files of `native/ui/kit/**` | Motion parts (08-M2), sheet and dialog motion (08-M3), and the K2 parts (tip, refresh, jump pill, tab strip and switcher, search field, filter chips, scroll fade, last known, server trust, top-bar pill, scope, link, typing guard) meet 06 §16.3 with reduced-motion tests. | Appearance parts (S4). | B-KIT-011, 014, 015, 016, 019, 022, 023, 025, 031, 032, 046, 050, 051; B-NAV-073 | S1-KITA1 | 13 | W7–W10 | 06 K1, K2; 08 §11 |
| S2-TEACH | Sonnet (kit owner reviews) | tip files of `native/ui/kit/**`, `native/engine/settings/**/teaching/**`, `native/config/gesture-ledger.json` | Teaching ships with the first hidden gestures, not after them (owner rule "teach hidden gestures"; draft `native/AGENTS.md` "Every hidden gesture registers a tip"): `KitTip` and `TipController` (2 showings per tip, one at a time, 2 min cooldown, one new tip per screen visit, blocked while a question waits, the keyboard covers the anchor or onboarding runs; `oc.tips` read unchanged) work; the registry holds `stop-after-step`, `send-later`, `hold-mic` and a tip for every hidden gesture shipped in S1–S2 (row swipe actions, long-press menus, message actions), each with en and ar copy; `GestureLedgerTest` (06 §11.1) is green with every S1–S2 gesture listed with its visible twin. Every later unit that adds a gesture adds its ledger entry and tip in the same branch. | `KitTour`, the `make-it-yours` tour and the Help › Tips page (S4). | B-STATE-142; UI-KIT-20 tip rows; SCREENS-NAV-25 registry rows | S2-KITB@6, S1-ENG | 3 | W10–W11 | 06 §11.1; 17 §4.1; map ui-kit UI-KIT-20 |
| S2-CHAT | Opus (chat lead) | `native/feature/chat/**` | The chat screen with the list, follow, jump pill, find, status line, message actions, `BasicComposerHost`, arrivals and the Back guard works on the preview build; J1–J3 are recorded on the emulator. | Composer extras (S3), chat pages (S4). | chat screen rows; B-NAV-005 (chat entry) | S2-TX@6, S2-DLV@3, S2-KCA@6, S2-KCB@5 | 12 | W11–W14 | 09 §6–§7; 07 §11 |
| S2-CHATS | Sonnet | `native/feature/{onboarding,chats}/**`, and `native/engine/agents/**/feed/**` for the OpenCode-only `ChatFeed` and the return brief view model (17 §1.14, CR17-5; the agents engine owner reviews) | The bootstrap gate UI, root decision, first run, Conversations home, inbox, all conversations, filters and new conversation pass E2E-01 and E2E-21 steps 1–7 on the preview build. | Pins to shortcuts (S4); agent rows (S3). | Conversations NAV rows; B-NAV-017, 018; B-CHAT-013, 014, 093…095; B-STATE-029, 082, 083 | S1-CONN, S1-SHELL, S2-KITB@5 | 10 | W9–W11 | 07 §5 A, B (B15 return brief); 17 §1.6 |
| S2-SERVERS | Sonnet | `native/feature/servers/**` | Servers, the editor, typed address and pairing code, connection details, the switcher, cleartext consent and pin review work; E2E-03 and E2E-04 pass. | QR scanning (S3). | B-NAV-006…009; B-SEC-107, 108, 124 | S1-CONN, S1-DATA, S1-KITA2 | 8 | W7–W9 | 07 §5 E; 04 WP-04.9 |
| S2-PLATFORM | Sonnet | entry, share-text, Tailscale, static-shortcut and `ActivityResults` files of `native/android/platform/**` | Deep links, text share in and out, the Tailscale handoff and static shortcuts reach `EntryRouter`; `ActivityResults` is registered on `MainActivity`. | Widgets, tiles, pinned shortcuts (S4). | B-ANDR-037; ANDR rows of map features 29, 32–34 | S2-ENTRY | 4 | W8–W9 | 11 §14, §16 |
| S2-MIGRRUN | Opus (migration owner) | `native/android/migration/**` | The journal, S0 backup, M1 census, M2 secrets, M3 prefs adoption, M4 restore check, M5 audit and M6 complete pass on the fixture; `StepInterrupter` exists in the `qa` build. The census records whether AI Team, Memory or Gemma data exists (for S2-GATEUI's notice), the migration does not copy A2A data (B-SEC-063, S02-7), and the runner emits `app_updated` and `migration_done` (16 §5.3). | Hardening, R1, R2 (S6). | 05 §10.2 runner rows; core parts of B-MIGR-001…013; B-SEC-063 | S1-DATA | 8 | W8–W9 | 05 §7, W6; 02 §3.7 |
| S2-GATEUI | Sonnet (shell owner) | gate files of `native/ui/shell/**`, `strings_migration.xml` | "Updating your app…", "Opening…", Try again at 8 s, the failure screens and the re-enter-password sheet render light/dark, en/ar, at 200 % in VPS goldens. The one-time status-line notice of 02 §3.7 item 4 ("AI Team and Memory aren't in this version yet. Your projects and notes stay on this phone.") shows once on the first native launch when the census found their data, and never again. | Migration logic. | 05 §8.2 rows | S2-MIGRRUN@3, S1-SHELL | 4 | W9–W10 | 05 W7; 02 §3.7 |
| S2-BLOB | Codex | blob files of `native/core/data/**` | `BlobStore` over today's files passes the ported vault tests. | — | 05 W5 rows | S1-DATA | 2 | W9–W10 | 05 W5 |
| S2-FIELD | Sonnet | `StartupHistory`, `FieldFrameStats`, `ProfileStatus`, `TraceRecorder` (off) in `native/android/platform/**` | The field recorder runs on the emulator, JankStats costs ≤ 0.1 ms per frame, and everything is off while hidden; its This phone › Details › Performance rows are built by S3-PHONEUI from this API (13 WP-13.4, P13-D3). | Analytics, any network. | — (performance budgets) | S1-PLAT0 | 3 | W10–W11 | 13 §3.6, WP-13.4 |
| S2-BACKOFF | Codex | `BackoffPolicy` in `native/core/net/**` (with the `:core:net` owner), the PR-01 and PR-04 scripts in `native/tool/perf/**` | Hidden reconnects follow one `BackoffPolicy` (13 amendment A2); PB-40, 51, 52 and 53 are green in common tests and once on the emulator; the `linux-ready` snapshot recipe is written for S3 (13 WP-13.6). Needed for S-18. | The foreground schedule (04 N4-D6). | B-PROTO-146 | S1-CONN | 3 | W10–W11 | 13 WP-13.6 |
| S2-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | Macrobenchmark J1, J2, J3 and cold start run on `OC_API37`; first Baseline and Startup Profiles generated. | Fixing what they find. | S-5, S-9…S-11 | S2-CHAT | 2 | W14 | 08-M6; testing-perf §9 |

### 6.2 Lanes in S2 (49 ad)

TW: slice-3 rows red (two agents from W6). FL: keep flows green on Flutter R; add the native release run for E2E-01,
03, 04, 05, 10. T3: weekly sweeps; slice-end flows. T4: J1–J3 on the emulator in W14. S2-EGRESS (TW lane, Sonnet, 4 ad;
16 WP-16.4): the canary and egress harness of 16 §2.5 in `native/tool/qa/egress/` with `config/egress-allowlist.toml`;
P16-3 settled; EG-1 and EG-2 green on the first slice-2 release build. It is the builder of the S-22 gate (§11.2).

---

## 7. M3 — Slice 3: the phone runtime, agents and the full composer (W11–W19 on A, W8–W15 on B)

- **Finish line:** on the preview flavor, a person sets up the phone's Linux, starts the phone's OpenCode server, signs in
  to Claude Code in its own terminal and chats with it through Paseo, writes with the full composer (drafts, saved
  prompts, phrase chips, mentions, commands, photos and files), answers forms, and pairs a server by scanning a QR code.
- **Non-goal:** notifications, widgets, tiles, schedules, settings pages, the Linux manager, voice, Codex.
- **Owner sees:** P2 "phone + agents" (W19 on A). The preview flavor has its own data and its own ports (S11-6), so on
  the owner's phone it needs its own Linux install; whether to do that is D15-4 (§17), which also guards Android's
  phone-wide limit of 32 child processes (R15-20). Otherwise the owner gets emulator videos of setup, the phone server
  and Claude Code.
- **Rows:** ≈ 357 [E] (LINUX 137, AGENT 79, CHAT 34, STATE 34, SEC 25, ANDR 21, PROTO 11, NAV 8, others 8).
- **Flows green at T3:** E2E-02, E2E-06, E2E-11, E2E-12, E2E-17, E2E-18, E2E-30, E2E-31.
- **T4:** S-16 (hidden phone agent CPU ≤ 5 % of a core).
- **Entry:** contract s3 frozen; S1-LINUX, S2-DLV, S2-REQ, S2-KCB merged; ADR D8 (Paseo path) answered or defaulted.

### 7.1 Builder units (169 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S3-PASEO | Codex | `native/protocol/paseo/**` | The Paseo transport, gateway, mappers and chat feed pass the Paseo live proof and the FX-PASEO fixtures. | Paseo P1 extras (S5). | B-PROTO-070, 071; B-AGENT-013, 142…191; B-SEC-109…111, 113…116; B-CHAT-070…072 | S1-NET | 10 | W11–W13 | 04 WP-04.10, §8 |
| S3-RTSPLIT | Opus (runtime owner) | `native/android/{linux,agenthost}/**` | `BuiltinLinux` is split per 11 §5.3 with the one lock family (S11-2), `GuestLauncher` and `GuestProcessRegistry` exposed, `:android:agenthost` carved out (CR11-0), and every copied test still green. | New behaviour. | copied LINUX rows, unchanged | S1-LINUX | 10 | W12–W14 | 11 §5.3 |
| S3-RTENG | Codex | `native/engine/runtime/**` | The portable rules (setup engine, start and probe, recovery consumer, healing, idle, thermal, process budget, manager parsers) and the shell scripts as checked `.sh.tmpl` files (S11-3) are green in common tests, including the built-in Linux rows the first pass would have dropped (02 §3.3: B-LINUX-227…232, 239…246, B-SEC-170). | Android bindings. | LINUX rows of `:engine:runtime`, slice 3 (FORCE rows of Appendix B included) | S1-DATA | 13 | W12–W15 | 11 §5.2, §5.4, §5.6, §7, §8 |
| S3-RTWIRE | Sonnet (runtime owner reviews) | bindings in `native/android/linux/**`, service shells at frozen FQCNs in `native/androidApp/**` | `AndroidRuntimeHost` binds the engine; the phone server FGS, the setup FGS with notifications 4098/4099, the idle-return notice 4100, exit recovery and the process budget work on the emulator; E2E-17 and E2E-18 pass. At the first native launch, a phone engine, `aiteam` or FactsBook node found running inside the built-in Linux is stopped by exact PID through the runtime host and recorded for the migration audit (02 §3.7 item 2), and nothing ever starts them. | Linux manager pages (S5). | ANDR rows of map features 11–13, 43, 44, 63; LINUX lifecycle rows | S3-RTSPLIT, S3-RTENG@8 | 8 | W14–W16 | 11 §6, §7 |
| S3-AGENTSE | Codex | `native/engine/agents/**` | The agent catalog (pins copied verbatim), readiness, install pipeline, phone check, sign-in model, auto-approval, merged feed, failure/title/context memory and the `AgentHost` interface are green. | Handoff, sub-agents (S5). | AGENT rows of `:engine:agents`, slice 3 (17 §1.14) | S3-PASEO@5, S3-RTENG@6 | 11 | W14–W16 | 17 §1 (design); agents-backends 01–15, 33, 42 |
| S3-PINS | Codex (engine) + Sonnet (Android) | pin and self-update files of `native/engine/agents/**` and `native/engine/runtime/**` (with their owners) | Layer 2 of the app-updates design (02 §3.5, `fix/pins-2026-10-31`; memory `app-updates-two-layers` option A): managed hosts follow only the app's tested pins; phone agents and the phone server update themselves when idle; an update yields to the person's work; big downloads wait for Wi-Fi; the person's own computer gets the "runs X, tested with Y" line. | Following "latest"; pins moved by anything but an app release. | top-up rows of `fix/pins-2026-10-31` | S3-AGENTSE@5 | 4 | W15 | app-updates SPEC layer 2; 12 G8 |
| S3-LINUXSEC | Opus (runtime owner) | `native/android/linux/**` binds, `native/engine/runtime/**` listener rules, the row in `native/feature/phone/**` (with its owner) | Linux rules L3 and L6 of 16 §3.2: the phone's Linux sees only folders opened as projects unless the visible row "Let Linux see all phone files" is on (D16-4); every service the app starts listens on `127.0.0.1` with authentication; `SharedStorageBindsTest` and `ListenerAuthTest` green; on the emulator an agent cannot list `/sdcard/DCIM` unopened (16 WP-16.9). | Other Linux behaviour changes. | B-SEC-198, 199 | S3-RTWIRE | 3 | W16 | 16 §3.2, WP-16.9 |
| S3-AGENTHOST | Sonnet | `native/android/agenthost/**` | `PhoneAgent*`, `PaseoStaleLock`, admission, readiness, idle, leases, sign-in over private pipes and the auth probe run behind `AgentHost`; their Kotlin tests pass. | — | LINUX rows of map features 52–54 | S3-RTSPLIT | 5 | W14–W15 | 11 §1.2 |
| S3-REQFORMS | Codex + Sonnet | `native/engine/requests/**`, form files of `native/ui/kit-chat/**` | One `FormSpec` engine (OC2 `hidden` and `when`, MCP/ACP elicitation, AskUserQuestion), the question sheet, approval modes and the always-allow invitation work; forms come back after a forced reconnect. | Card forms (S5). | B-PROTO-029, 044, 048, 063, 069, 102, 103; CHAT-GENUI 45–48 rows | S2-REQ | 7 | W13–W14 | 09 §5; 04 WP-04.11 |
| S3-COMPE | Codex | `native/engine/composer/**` | Drafts, the attachment vault, stash, prompt history, phrase chips, references and the photo recovery record are green and read Flutter-written data unchanged. | UI. | STATE rows of map features 21–23, 26; CHAT-GENUI 35–37 rows | S2-DLV | 7 | W12–W14 | 09 §10 (as amended by C15-1) |
| S3-COMPUI | Sonnet | `native/feature/chat-composer/**` except attachment files | `FullComposerHost` replaces the basic one: chips row, `/` `@` `!` with the command sheet, the editor, saved prompts and the model/agent/thinking picker; E2E-11 passes. | Attachments (S3-MEDIAATT), voice (S5). | CHAT-GENUI 26, 27, 30, 31, 81 rows | S3-COMPE@4, S2-KCB | 10 | W14–W17 | 09 §10 |
| S3-MEDIAATT | Codex (logic) + Sonnet (Android, UI) | `native/engine/media/**/{attach,office,xml,preview}/**`, `native/android/media/**/{image,clipboard}/**`, attachment files of `native/feature/chat-composer/**` | The "+" tray (Photos, Camera, File, More), clipboard and keyboard pictures, chips and photo recovery work; E2E-12 passes on the release build. | Viewer and gallery (S5). | B-MEDIA-001, 006, 007, 008; B-CHAT-009, 010, 021…024, 107; B-STATE-088 | S3-COMPE@3 | 15 | W15–W18 | 10 MV-1a, MV-2 |
| S3-KCCONTENT | Sonnet (kit-content owner) | `native/ui/kit-content/**`, image files of `native/ui/kit/**` | The code block with `CodeTokenizer` (SyntaxMP benchmark first), log panel, `KitImage` and its resolver, QR drawing, the scanner slot (CameraX + zxing-cpp), terminal output view, capability explainer, checklist, progress and context rings, and the task chip meet 06 §16.3; scanning works on the emulator camera; `dependencyInsight` shows no ML Kit. | Diff page, viewer, gallery, live terminal extras (S5). | B-ANDR-049; content KIT rows | S1-KITA2 | 18 | W15–W19 | 06 KN3; 10 MV-3, MV-9 |
| S3-WORK | Sonnet | work files of `native/ui/kit-chat/**` and `native/feature/chat/**` | Timeline rows, the step light, the background work line and the running work sheet work. | — | CHAT-GENUI 12, 22, 25 rows | S2-KCA | 5 | W16–W17 | 09 §17 S3-WORK |
| S3-COMPMOTION | Sonnet (kit-chat owner; Opus for the glow render-compare loop) | the 08-M5 files of `native/ui/kit-chat/**`: `ComposerGlow.kt`, `MovingPart.kt`, `KitComposerStop` and the status strip's reveal, the "Stop and send" pill, `KitTurnLive` sweep and Signal lamp, step-light and Signal lamps, `KitToolRow` status, `KitRequestCard` motion, `KitQueuedMessage`, `KitBackgroundWorkLine`, `KitFindMark`, IME frame | 08-M5 "Composer and turn motion": the running composer glow the owner likes (memory `composer-green-glow-liked`) is one choice, Off / Classic / Soft ring, read from the migrated `oc.effectsActivityGlow` and `oc.effectsGlowStyle` (the stored `oc.effectsGlowColours` and `oc.effectsGlowSpeed` are ignored and deleted at migration, 05, 08 §3.1); Send→Stop ring, one moving part in the composer, reduced motion; `MovingPartTest`, `StopGuardTest`, `GlowColoursTest`, `HaloPainterReuseTest`, `StepLightPhaseTest`, `SignalLampTest`, `HeldAnswerTest`, `ComposerKeysUiTest`, `ImeClearanceTest` green; the glow compared side by side with a Flutter capture on the same emulator (`judge-visuals-hard`). | Voice circle ring (with S5-VOICE). | B-CHAT-006, 012, 050, 063, 117, 123; B-KIT-002, 036 | S2-KCB, S3-WORK | 11 | W17–W19 | 08 §11 M5 |
| S3-TERMMIN | Sonnet | terminal files of `native/ui/kit-content/src/androidMain/**`, sign-in files of `native/feature/agents/**` | The sign-in terminal runs Claude's own `claude auth login --claudeai` in a Termux `TerminalView` over the copied PTY, with the **accessible mode** of 06 §7.7 (transcript, "Send to terminal" field, grid excluded from semantics, auto-on under TalkBack) and `TerminalAccessibleModeTest` green; E2E-31 passes, also once with TalkBack on. | Key bar, OSC 52, server PTY (S5). | AGENT rows of map features 13, 14 | S3-KCCONTENT@8, S1-LINUX | 4 | W17–W18 | C15-2 |
| S3-PHONEUI | Sonnet | `native/feature/phone/**` (not the Linux manager) | This phone, setup A/B/C, Keep running and phone health pages work, including the This phone › Details › Performance rows over S2-FIELD's recorder and the guided "Disable child process restrictions" card (11 §7); E2E-17 and E2E-18 pass. | Linux manager (S5). | NAV rows of screens-nav 74, 76, 78, 79, 83 | S3-RTENG@6, S2-KITB | 12 | W16–W19 | 07 §5 F |
| S3-AGENTSUI | Sonnet | `native/feature/agents/**`, agent files of `native/feature/chats/**` | The agents hub, agent sheet, sign-in, account, the agent chip and agent rows in Conversations work; E2E-30 and E2E-31 pass. | Codex, handoff (S5). | NAV rows of screens-nav 82, 84; AGENT UI rows | S3-AGENTSE@5, S2-KITB | 10 | W17–W19 | 07 §5 G |
| S3-MEDIAPROOF | Opus (media lead) | `native/tools/proofs/media/**`, `docs/qa/native/media-proofs-*` | P-M1 (sherpa AAR), P-M2 (Whisper numbers), P-M3 (QR scan), P-M6 (voice model path) and P-M7 (keyboard paste) have verdicts. | Production media code. | — | S0-SKEL | 4 | W18–W19 | 10 §22 |
| S3-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-16 is measured on the emulator against the freeze-tip number. | — | S-16 | S3-AGENTSUI | 2 | W19 | 02 §4 |

### 7.2 Lanes in S3 (55 ad)

TW: slice-4 rows red (two agents). FL: native-only variants approved by the owner only (02 §6.6). T3: E2E-17 needs AVD-S
and AVD-F (small RAM, full disk): book a full evening. FX: FX-LINUXDL and FX-AUTH running.

---

## 8. M4 — Slice 4: notifications, widgets, schedules, settings, styles, updater (W18–W24 on A, W12–W18 on B)

- **Finish line:** a reply that finishes in the background notifies through the short reply-watch service, answers can
  be given from the notification shade, tiles, widgets and pinned shortcuts work and survive a reboot or update,
  scheduled prompts run on time, every Settings page works with search, all five styles pass their contrast checks, and
  the in-app updater can install a newer native build.
- **Non-goal:** Glance widgets, new widget sizes (NG13), live backgrounds (D11), AppFunctions (ADR-D16).
- **Owner sees:** P3 "around the phone" (W24 on A): one image per style per mode, a video answering from the shade, both
  widgets on the emulator launcher. The preview's widgets and tiles appear with the preview app's own label.
- **Rows:** ≈ 220 [E] (ANDR 67, STATE 33, SEC 31, NAV 27, KIT 24, AGENT 16, CHAT 10, others 12).
- **Flows green at T3:** E2E-07, 08, 09, 21 (all steps), 22, 23, 25, 26, 27, 34, 36, 38.
- **T4:** S-13 transitions, S-15 idle CPU, S-17 hidden timers, S-18 reconnects, S-19 status updates.
- **Entry:** contract s4 frozen; S3-RTWIRE merged (reply watch and restore need it); D16-3 and 11 §25 answered or
  defaulted. The analytics destination (D16-1) is not needed: the core already runs with `sender=none`.

### 8.1 Builder units (122 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S4-ATTNE | Codex | `native/engine/attention/**` | The attention feed, profile monitor, notification policy and copy keys, the one status-line combiner, live mode controller, pause policy and widget snapshot policies are green. | Android posting. | B-NAV-002…004, 010; B-PROTO-031, 046, 060, 083, 096…101, 127; STATE rows of map features 29–34 | S1-CONN, S2-TX@8 | 11 | W18–W21 | 04 WP-04.12; 11 §10 |
| S4-BG | Sonnet | `native/android/background/**`, receiver and service shells in `native/androidApp/**`; the moved service bodies leave `native/android/linux/**` in the same merge (with the runtime owner) | Channels (ids frozen), status row 4747, alerts, Live Updates for runs the person started, reply watch (S11-4), both tiles, notification actions with the lock-screen unlock rule (16 WP-16.8, CR16-7, D16-3: `NotificationActionAuthTest`, B-SEC-184), pinned shortcuts and the restore receiver body work; the phone-server and setup service bodies move from `:android:linux` into `:android:background` behind `PhoneServerForeground` and `PhoneServerServiceHooks` (S11-12, W11-09), with the temporary `NativeStrings` copy deleted; `StatusNotificationModelTest` moves green with its source, name unchanged, and is listed in `TWINS.md` (02 §5.6); E2E-07, 08, 09 pass. | Widgets; the restore orchestration (S4-RESTORE). | B-ANDR-056…064, 103…107, 118, 119; B-SEC-180, 184; ANDR rows of map features 02–09, 15, 30 | S4-ATTNE@5, S3-RTWIRE | 14 | W19–W22 | 11 §9, §10, §14, W11-09; 16 WP-16.8 |
| S4-SCHED | Codex (engine) + Sonnet (Android) | `native/engine/schedule/**`, `native/android/schedule/**` | One recurrence implementation with DST golden tests, the CAS store contract, claims `id:revision:dueAtMs`, receipts and the runner work under AlarmManager and WorkManager with the same worker class name; `ScheduledPromptPolicyTest`, `ScheduledPromptRecoveryTest` and `ScheduledPromptStoreTest` move green with their source, names unchanged, listed in `TWINS.md`; E2E-27 passes. | Schedules UI. | ANDR rows of map features 35–41; STATE 53 | S2-DLV, S1-CONN | 11 | W19–W21 | 11 §12 |
| S4-WIDGETS | Sonnet (Opus reviews the launcher traps) | `native/android/widgets/**` | Conversations 4×2 and Status 2×2 redraw from the native snapshot with RemoteViews, render-once sizing and `previewImage`; the five retired providers (Sessions card 2×2, list 4×4, strip 4×1; Usage 2×2 and 4×2) keep drawing wherever they are already placed, so nobody sees "Can't load widget" (02 §3.2, D11-3; J1 seeds one); widget actions run in a 30 s short FGS through `ConnectionRegistry.oneShot` (CR11-6); `WidgetProvidersTest`, `TwoWidgetsRenderTest`, `UsageWidgetFitTest`, `UsageWidgetPlansTest`, `WidgetActionLeaseTest`, `WidgetActionPolicyTest`, `StatusWidgetModelTest`, `WidgetCardRenderTest`, `WidgetConversationPresentationTest`, `WidgetSessionCardLayoutTest`, `WidgetStatusPolicyTest` and `WidgetV2PolicyTest` move green with their sources, names unchanged, listed in `TWINS.md` (the `kotlin_tests.green` ratchet, S-1); E2E-26 passes. | Glance, new sizes. | ANDR rows of map features 17–28 | S4-ATTNE@6 | 13 | W20–W22 | 11 §4.3, §11, W11-10; memory `zte-launcher-widget-traps` |
| S4-RESTORE | Opus (coordinator, with the migration owner) | `SystemEventRestorer` and the first-launch hooks H2/H3 in `native/engine/entry/**` (coordinator-owned), the calls from the restore shells in `native/androidApp/**` | One orchestrator for "the OS forgot us" (S11-11, W11-14a): after boot, after an update and at the first open after a force stop it re-arms schedules, restores the phone server once (11 R9: one start), and redraws widgets, tiles and pinned shortcuts through the surfaces S4-BG, S4-WIDGETS and S4-SCHED register; the 11 §15 table passes on the emulator. | The §6.8 proofs over a real Flutter install (S6-MIGRHARD). | B-MIGR-013; restore rows of 11 §15 | S2-ENTRY, S4-BG@5, S4-WIDGETS@5, S4-SCHED@5 | 4 | W21–W22 | 11 §15, W11-14 |
| S4-PANELS | Sonnet | `native/feature/widget-panels/**` | Style, project, steer and the voice-panel shell open in `WidgetOverlayActivity` with `FLAG_SECURE`, built from the K4 widget-panel parts of S4-APPEAR. | Voice capture in the panel (S5-VOICE). | NAV rows of screens-nav 100 | S4-WIDGETS@6, S4-APPEAR@6 | 4 | W21–W22 | 07 §5 M |
| S4-SETTINGS | Sonnet | `native/feature/settings/**` (not appearance, voice, providers) | The Settings hub, groups and search, notifications/background/Keep running/automation, the smaller pages and the per-server page work; Manage space (07 I18, `ManageSpaceActivity`) and the Storage page list `files/local_llm_models/` (the Gemma model, about 3.7 GB) with a Delete action that names it (02 §3.7 item 5); the Privacy page rows of S4-ANALYTICS are hosted here; E2E-22 passes. | Appearance, voice, providers pages. | NAV rows of screens-nav 75, 90, 92, 93 | S1-ENG, S2-KITB | 12 | W19–W22 | 07 §5 I |
| S4-APPEAR | Opus (kit owner) | style and appearance files of `native/ui/kit/**`, appearance files of `native/feature/settings/**` | All of 06 K4. First (by its sixth agent-day, the contract point `@6`): `KitDateTimePicker` on M3 1.5 (K10), the wheel, the widget-panel parts and `KitArrival`, which S4-SCHEDUI and S4-PANELS need. Then Soft, Brutal, Paper and Signal with Fine-tune and Saver pass `StyleContrastTest` over every style × knob × pack × brightness; the guided Appearance page and the Make it yours tour work; E2E-23 and E2E-38 pass. | Live backgrounds and scenes (D11). | KIT rows of ui-kit 14–16, 19, 20, 39, 56 | S1-KITA1 | 20 | W19–W24 | 06 K4, K10 |
| S4-PAGES | Codex + Sonnet | review files of `native/engine/project/**`, `native/feature/chat-pages/**` | Undo from here (both shapes), Review the undo, context pages, export, the note, relations and the approvals sheet work. | Files hub (S5). | CHAT-GENUI 57, 58, 60, 61, 64, 65 rows; STATE 27 | S2-CHAT@6 | 10 | W22–W24 | 09 §17 S4-PAGES |
| S4-ABOUT | Sonnet | `native/feature/about/**` | Report a problem (counts only), diagnostics, about, guide, tips and the Updates page work; star the app, share the app and the What's new walkthrough (02 §3.5, `feat/star-share`; 07 A15) work; E2E-34 passes. | — | NAV rows of screens-nav 102–104; diagnostics XPLAT and SEC rows; top-up rows of `feat/star-share` | S1-ENG | 6 | W22–W23 | 07 §5 L |
| S4-UPDATER | Codex (engine and platform) + Sonnet (Updates page rows, notice, badge, sheet); Opus reviews | update files of `native/android/platform/**` and `native/engine/diagnostics/**`; `ResumableDownloader` in `native/core/net/**` (with the `:core:net` owner); the Updates rows of `native/feature/about/**` (with S4-ABOUT's owner) | 16 §8 in full. The `AppUpdater` checks GitHub only when `UpdatePolicy.feedState` is `Active`: this package, the installed signer is the public lineage, and the first installer was not F-Droid (N16-D8, B-SEC-190); so the owner's local-lineage phone never polls GitHub and its Updates page says where updates come from. It reads `releases/latest`, picks the asset (arm64-v8a, else x86_64, else the universal file; "needs a 64-bit phone" otherwise), downloads through `ResumableDownloader` with the redirect loop of CR16-5 (also used later by voice packs), checks `SHA256SUMS`, package, higher versionCode, `minSdk` and the same signer set (B-SEC-189), and installs through `PackageInstaller`; `UpdateVerifierTest` covers every row of 16 §8.3; P16-4 passes on the emulator; it emits `update_result` (16 §5.3); the notice, gear badge and sheet follow the two-layer design. | Updating managed servers' pins (S3-PINS). | B-ANDR-068, B-MIGR-012 (amended); B-SEC-189, 190; B-PROTO-144 | S1-NET, S1-ENG | 9 | W22–W24 | ADR §9.9; 16 §8, WP-16.6, P16-4; app-updates spec |
| S4-SCHEDUI | Sonnet | `native/feature/schedules/**` | The scheduled-prompt list, editor and "send later or repeat" work, with the K4 date and time pickers (never hand-built). | — | NAV 98; CHAT-GENUI 39 rows | S4-SCHED@6, S4-APPEAR@6 | 4 | W23 | 07 §5 K |
| S4-ANALYTICS | Sonnet (page) + Codex (a sink, only after D16-1) | the Privacy rows and the "What we collect" page in `native/feature/settings/**` through the settings owner; a sink in `native/engine/analytics/**/sink/**` | The "What we collect" page from `WhatWeCollectSource` (18 §6) and the 16 §5.8 rows (the DETAILED switch, "What was sent"); a remote sink (Aptabase, PostHog, Firebase over the GA4 Measurement Protocol with no SDK, Plausible or self-hosted; our own HTTP code) only after D16-1 names it, with its contract test and the S-22 allowlist entry. | Any vendor or Google SDK; any network send until D16-1 names a destination. | B-SEC-191 (rows part); B-ANLY-011 | S1-ANALYTICS, S4-SETTINGS@6 | 2 | W23 | 16 §5, WP-16.7; 12 W-20 |
| S4-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-13, S-15, S-17, S-18 and S-19 are measured on the emulator. | — | — | S4-BG | 2 | W23–W24 | 02 §4 |

### 8.2 Lanes in S4 (45 ad)

TW: slice-5 rows red (two agents). T3: E2E-26 also on the owner-launcher rules (11 §11.4); E2E-25 with `adb` intents;
the independent visual auditor reviews every style after S4-APPEAR merges (02 §7.10).

---

## 9. M5 — Slice 5: project, media and integrations (W23–W31 on A, W16–W22 on B)

- **Finish line:** every remaining in-scope feature works on the preview flavor: files, Git with GitHub sign-in,
  terminals, dev previews, voice typing, read aloud, viewers including PDF, Wi-Fi share, Codex, agent cards, MCP,
  providers, usage, the Linux manager with services, vitals and backup, Paseo's second-tier features, search and the
  Termux move-out.
- **Non-goal:** anything named "after the switch" in §13 (AI Team, Memory, AppFunctions, the Gemma assistant, Glance).
- **Owner sees:** P4 feature-complete (W31 on A), with a request to use it daily beside the Flutter app for a week and
  list every difference noticed.
- **Rows:** ≈ 404 [E] (AGENT 99, LINUX 66, SEC 57, CHAT 48, MEDIA 29, STATE 27, PROTO 26, NAV 18, ANDR 16, BUILD 11,
  others 7).
- **Flows green at T3:** E2E-13, 14, 15, 16, 19, 20, 24, 29, 33, 35, 37.
- **T4:** S-14 size gate; the media budgets of 10 §18.
- **Entry:** contract s5 frozen; S3-KCCONTENT, S3-REQFORMS, S3-AGENTSE, S3-RTWIRE merged.

### 9.1 Builder units (169 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S5-CODEX | Codex | `native/protocol/codex/**`, Codex files of `native/feature/{servers,agents}/**` | The Codex app-server client connects, lists threads, runs turns with approvals, steers, aborts and shows the account; E2E-29 passes. | — | Codex parts of B-AGENT-051…070, 220 | S1-NET | 6 | W23–W25 | 04 WP-04.13, §9 |
| S5-OC2MORE | Codex | the rest of `native/protocol/oc2/**` | PTY, shells, files, MCP, integrations, plugins, web search and usage operations are green; a PTY reconnect resumes at the right cursor. | — | B-PROTO-022, 026, 030, 035, 036, 049, 058, 059, 078…080, 082, 084…087, 104…108, 121…123, 125, 126 | S1-OC2 | 7 | W24–W25 | 04 WP-04.13 |
| S5-PROJE | Codex | `native/engine/project/**` (not review) | The project hub, files, the Git page model with the GitHub device flow, staged revert, the terminal session model, development services, worktrees and isolated task launch, workspace paths and dev-preview rules are green; `GitInjectionTest` and `GitRealGitTest` pass on the VPS. | UI. | Git and project rows (17 §2.9) | S1-CONN | 10 | W24–W26 | 17 §2; ADR §5.2 |
| S5-PROJUI | Sonnet | `native/feature/project/**` (not viewer or share files) | The Files hub, browser, review and diff, Git sheet, server terminal, dev preview and project tools work; E2E-14, 15, 16 pass. | — | NAV rows of screens-nav 63–70 | S5-PROJE@5, S3-KCCONTENT | 11 | W25–W27 | 07 §5 D |
| S5-TERMFORK | Sonnet | `native/thirdparty/termux-terminal-view/**` (the fork) and `:thirdparty:termux-session-port` (12 W-14); `thirdparty` added to module discovery (03 §3.1) with the build-logic owner | The Termux `terminal-view` v0.118.3 source fork carries the ~100-line session-port patch, so one `TerminalView` shows both a local PTY and a remote `RemoteTerminalSession`; `TerminalSessionPort`, `LocalPtySession` and `TerminalLinkScanner` are green (R12-2). | Key bar, OSC 52 (S5-TERMINAL). | B-XPLAT-009, 010, 011 | S3-TERMMIN | 3 | W24 | 12 R-31, W-14, R12-2 |
| S5-TERMINAL | Sonnet | `native/android/terminal/**`, terminal files of `native/ui/kit-content/**` | `:android:terminal` is carved out of `:android:linux`; the live terminal slot has the key bar, OSC 52 and links; local and server shells (the server PTY through S5-TERMFORK's view) outlive the screen; accessible mode (06 §7.7) works on the local and server shells (07 D10, F14). | — | LINUX 50; XPLAT 30 rows | S5-TERMFORK, S5-OC2MORE@4 | 7 | W25–W26 | 06 KN5; 11 §1.2 |
| S5-KC2 | Sonnet (kit-content owner) | `native/ui/kit-content/**` | The diff view and page, viewer, gallery, markup, zoom, web preview slot, charts and tables, session tabs, breadcrumb, mic hold and level meter meet 06 §16.3. | — | content KIT rows; B-KIT-026 | S3-KCCONTENT | 13 | W24–W27 | 06 KN5; 10 MV-3 |
| S5-MEDIALOGIC | Codex | `native/engine/media/**/{voice,models,setup,readaloud,conversation,share,qr}/**` | The pure voice, models, read-aloud, conversation, share and QR rules are green. | Android audio. | pure parts of B-MEDIA-002, 003, 005, 011, 013…016, 018, 021, 023…025, 028…031, 034 | S0-CORE | 10 | W24–W27 | 10 MV-1b |
| S5-VOICE | Opus (`VoiceController`, `AudioSession`) + Sonnet | `native/android/media/**/{audio,speech}/**`, `native/engine/media/**/voice/**`, voice files of `native/feature/{chat-composer,widget-panels}/**` | Dictation, hold to talk, send when I stop talking and the microphone-permission recovery work on the emulator. | Read aloud. | B-MEDIA-010, 017, 020, 023, 033 | S5-MEDIALOGIC@5, S3-MEDIAPROOF | 12 | W26–W29 | 10 MV-5 |
| S5-MODELS | Codex + Sonnet | `native/engine/media/**/{models,setup}/**`, `native/android/media/**/device/**`, voice files of `native/feature/settings/**` | Model packs, the total-RAM gate, the resumable verified download, Settings › Voice typing and the model sheet work; E2E-13 passes. | — | B-MEDIA-012, 019…022, 025…027, 030, 032; B-ANDR-101, 102; B-L10N-024; B-LINUX-220 | S5-MEDIALOGIC@5 | 10 | W26–W28 | 10 MV-6 |
| S5-READ | Opus (reply watch, conversation) + Sonnet | `native/android/media/**/tts/**`, `native/engine/media/**/{readaloud,conversation}/**`, read-aloud files of `native/feature/chat/**` | System and Kokoro read aloud, consent, voice choice and the voice conversation loop work; Kokoro stops at once. | Background audio (D10-5). | B-MEDIA-005, 009, 018 | S5-VOICE@6, S5-MODELS@5 | 10 | W27–W30 | 10 MV-7 |
| S5-VIEWER | Sonnet (Opus reviews PDF) | viewer files of `native/ui/kit-content/**` and `native/feature/project/**`, `native/android/media/**/pdf/**`, `LocalPdfService.kt` in `native/androidApp/**` | One viewer with every body kind, find, reader preferences, the isolated PDF service, and Save, Copy, Share and Open with. | — | B-MEDIA-004; B-NAV-028 | S3-MEDIAATT, S5-KC2@5 | 10 | W25–W27 | 10 MV-4 |
| S5-WIFI | Codex + Sonnet | `native/engine/media/**/share/**`, `native/android/media/**/share/**`, share-sheet files of `native/feature/project/**` | Wi-Fi share with the token server, the QR and link sheet, and chat file links work. | — | B-MEDIA-034; B-KIT-026 | S5-MEDIALOGIC@5 | 6 | W27–W28 | 10 MV-8 |
| S5-CARDS | Codex + Sonnet | card files of `native/engine/{requests,agents}/**`, `native/ui/kit-chat/**` and `native/feature/chats/**` | The cards contract works end to end: validator, states, answers sent exactly once with Undo, journal, recovery, install and verify for Claude Code, and the connector card. | OpenCode 2 card qualification beyond today's. | B-CHAT-029…053 | S3-REQFORMS, S3-AGENTSE | 13 | W27–W30 | 09 §17 S5-CARDS-*, S5-CONNECT |
| S5-INTEG | Codex + Sonnet | `native/engine/integrations/**`, `native/feature/usage/**`, providers, MCP and tools files of `native/feature/settings/**` | MCP, providers (keys never echoed), usage and quota, and BYO host work; E2E-33 and E2E-37 pass. | — | AGENT rows of map features 58–61; STATE rows 42, 45, 50; B-CHAT-090, 091 (17 §3.8) | S5-OC2MORE@3 | 10 | W28–W30 | 17 §3; ADR §5.2 |
| S5-LINUXMGR | Codex + Sonnet | manager files of `native/engine/runtime/**` and `native/feature/phone/**` | The Linux hub, Running, Ports, storage (which also lists `files/local_llm_models/` with Delete, 02 §3.7 item 5), packages, reset, services supervisor, vitals and backup work, including B-LINUX-247 and 248 (storage scan and parsing, 02 §3.3); E2E-19 and E2E-20 pass. | The designed-not-built items (builtin-linux 49). | LINUX rows of map features 40–48 | S3-RTWIRE | 9 | W28–W30 | 11 §1.2; C15-12 |
| S5-PASEOP1 | Codex + Sonnet | P1 files of `native/protocol/paseo/**` and `native/engine/agents/**` | Claude sub-agents, background tasks, files and changes over Paseo, import from Claude Code, the computer daemon update, plan usage and Continue with… work. | — | AGENT rows of map features 16, 26–32 (handoff rows: 17 §1.14) | S3-AGENTSE | 7 | W29–W30 | 17 §1.7 (handoff); agents-backends §2 tiers |
| S5-SEARCH | Sonnet | `native/feature/search/**`, shortcut files of `native/ui/shell/**` | The command launcher, Go to, the settings-search entry, keyboard shortcuts and the offline demo work; E2E-24 passes. | Desktop-only interaction. | NAV rows of screens-nav 10, 11, 61 | S4-SETTINGS | 4 | W29–W30 | 07 §9 |
| S5-TERMUXOUT | Sonnet | `native/android/termux/**` | Moving from Termux into the built-in Linux works (migrate-out only, D7). | Termux as a host. | LINUX 63 rows | S3-RTWIRE | 3 | W30 | 11 §1.2 |
| S5-REPRO | Codex (build-logic owner reviews) | build-logic reproducibility settings, `native/tool/release/repro_check.sh` | The 16 §9.2 rules hold and P16-5 runs: two builds of one commit, with different paths and worker counts, are byte-identical unsigned, or the differing entries are named with fixes (report-only, A16-13; the owner's F-Droid-ready rule, 16 WP-16.13). | The F-Droid submission (after the switch). | — | S0-GATES | 3 | W30–W31 | 16 §9, WP-16.13 |
| S5-DOCS | Sonnet (docs) | `SECURITY.md`, the threat model and trust inventory in `docs/`, the notices and About sources in `native/feature/about/**` (with its owner), the `third-party-sources-1` release text | 16 WP-16.14: SECURITY.md (supported versions per D16-6), the threat model and trust inventory updated, `NoticesCurrentTest` and `KnownHostsDocTest` green, the GPL source release for proot and talloc prepared as an immutable `third-party-sources-1` release (D16-8; publishing it needs the owner's GO). PRIVACY.md stays with S6-RELEASE. | Publishing. | — | S4-ABOUT | 3 | W30–W31 | 16 §10, WP-16.14; 12 R12-8 |
| S5-BENCH | Sonnet (T4 lane builder) | `native/android/benchmark/**` | S-14 and the 10 §18 media budgets are measured. | — | — | S5-VOICE | 2 | W30–W31 | 10 §18 |

### 9.2 Lanes in S5 (45 ad)

TW: the last red rows plus slice-6 MIGR rows. T3: E2E-13 needs the microphone and network control; E2E-15 needs FX-GH
and FX-GIT. T4: size and media budgets.

---

## 10. M6 — Slice 6: migration hardening and the switch (W30–W32 on A, W21–W23 on B)

- **Finish line:** the switch candidate installs over Flutter 2.1.0+57 on both lineages (the public lineage on the CI
  draft of §11.2) and over the bridge release, keeps every stored value, secret, widget, tile, shortcut, schedule and the built-in Linux, passes all 37 switch flows
  on the release build and every S-criterion, and the owner's two-day phone test passes.
- **Non-goal:** any new feature; the Room move (switch+1 unless the P5 trigger fired).
- **Owner sees:** a video of the upgrade journey on the emulator (Flutter → native, everything kept), then the switch on
  their own phone (§11.3).
- **Rows:** ≈ 18 [E] (MIGR 13, others 5) plus every row still red or todo.
- **Flows:** E2E-39 green; all 37 switch flows green on the candidate's release build.
- **T4:** S-5…S-21 on the candidate; NX721J numbers with the owner's OK.

### 10.1 Builder units (42 ad)

| Unit | Model | Write set | Finish line | Non-goal | Rows | Depends on | ad | A weeks | Detail in |
|---|---|---|---|---|---|---|---|---|---|
| S6-MIGRHARD | Opus (migration owner) | `native/android/migration/**`, `native/e2e/upgrade/**`, `native/tools/flutter-fixtures/**` | J1–J8 pass on the candidate on the owner lineage and from the bridge release, including the media step (voice pack "Ready"), placed tiles and widgets, the Linux restore, the audit line for any stopped AI Team or FactsBook process (02 §3.7 item 2) and R2; E2E-39 is green; the 11 §6.8 proofs over a real Flutter install pass (W11-14b). The public-lineage legs run from the same scripts, without code changes, on the CI draft's APKs in gate 2 (§11.2): only CI holds the `842284B2…` key, so they cannot be part of this unit. | Room v2. | B-MIGR-001…013; 05 A1–A7 | S2-MIGRRUN, S3-RTWIRE, S4-BG, S4-RESTORE, S4-WIDGETS, S4-SCHED, S5-MODELS | 9 | W30–W32 | 05 §10.5, W6, W8; 10 MV-10 |
| S6-R1 | Sonnet | branch `rollback/flutter-final` of the frozen Flutter tree | The R1 build (native + 1, owner lineage) opens all data and J4 passes. | — | J4 | S6-MIGRHARD@4 | 2 | W32 | 05 §9.3, W9 |
| S6-COMPAT | Sonnet | `FlutterFormatCompat*` tests in `native/androidApp/**` and on the rollback branch | `FlutterFormatCompatTest` is green on the candidate. | — | — | S6-MIGRHARD@4 | 3 | W32 | 05 §10.6, W10 |
| S6-PERF | Sonnet (T4) + Opus for fixes | `native/android/benchmark/**`; fixes through module owners | S-5…S-21 are measured on the candidate; profiles regenerated. | New features. | S-5…S-21 | S5-READ, S5-PROJUI | 6 | W30–W31 | 09 §17 S6-PERF; 02 §4 |
| S6-AUDIT | Opus (independent auditor; never edits) | `docs/qa/native/audit-<date>/**` | Every screen × style × light/dark, plus Saver, high contrast and RTL, is reviewed and findings are filed to owners; the 08-M7 motion evidence pass is done. | Editing code. | — | S4-APPEAR, S5-PROJUI, S5-VIEWER | 6 | W31–W32 | 02 §7.10; 08 §11 |
| S6-A11Y | Sonnet | `docs/qa/native/a11y-<date>/**`; fixes through module owners | The TalkBack pass and the 200 % text and Arabic capacity sweeps are clean. | — | a11y XPLAT and KIT rows | S5-PROJUI | 3 | W31 | desktop-web-cross 25–33 |
| S6-RELEASE | Sonnet + coordinator | `native/tool/release/**`; repo-root `.github/workflows/*` (`native-release.yml`, `native-quality.yml`, and the stage-2 retirement of the Flutter workflows on `master`), `scripts/release.sh` native mode, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md` (coordinator exception to 02 §7.2) | The native release pipeline (16 §7.3, WP-16.12) produces `opencode-mobile-<v>-arm64-v8a.apk`, `…-x86_64.apk`, the universal `opencode-mobile-<v>.apk` (plus `…-armeabi-v7a.apk` only if the 32-bit decision keeps it), `SHA256SUMS` and notes in today's format, and passes a `dry_run` dispatch; step 5 compares **trees** (the tag's tree equals the tree of the approved owner candidate, §11.0) and requires the tag commit to be `master`'s head, which the snapshot commit makes true; `scripts/release.sh` native mode has `--prerelease`, `promote` (pre-release → latest, then reads back `GET /releases/latest`) and `demote` (latest → pre-release, for the stage-2 halt) and sets `make_latest` explicitly on every release; `native-quality.yml` ports android-quality's G33 commit check and the T2 gates and runs on `master` pushes once the snapshot lands; the BYO host bundle decision is recorded (native tags build it or not); the Flutter workflows' removal from `master` is prepared for the stage-2 snapshot (§11.6); PRIVACY.md is rewritten; the notices include proot, talloc and shmem (R12-8); the component freeze list is final. | Publishing, pushing and tagging (owner GO only). | BUILD rows of slice 6 | S4-UPDATER | 8 | W31–W32 | packages-build 29–34, 40 |
| S6-SWEEP | Sonnet ×2 | inside owning modules, through their owners | No switch-scope row is red or todo. | — | whatever remains | every S5 unit | 5 | W31–W32 | 02 §6.7 |

---

## 11. The switch-over plan

### 11.0 Getting native code to GitHub (every push is a snapshot)

Local history cannot reach GitHub. `native/main` is created from the freeze-line tip (02 §7.2), and that history holds
`e410b6d71` with a 181 MB APK, which GitHub rejects [V: `git merge-base --is-ancestor e410b6d71
int/freeze-2026-11-01` is true, 2026-11-01; memory `push-freely-snapshot-recipe`]. GitHub `master`
(`mobile-next/master`) is a chain of snapshot commits, and the freeze tip is not its ancestor. So `native/main` is never
pushed as it is, `master` can never be fast-forwarded to it, and a local build and a CI build can never share a commit.
What they share is the **tree**, and every check below compares trees.

| Purpose | Recipe (`native/tool/snapshot_push.sh`, written by S0-GATES) | GO |
|---|---|---|
| Work snapshot (cloud lane, off-site copy), every Monday | `S=$(git commit-tree native/main^{tree} -p mobile-next/master -m "snapshot: native/main at <sha> [skip ci]")` with author and committer `Eslamasabry <104366884+Eslamasabry@users.noreply.github.com>`; `scan_push.sh` over `git diff mobile-next/master $S`; `git push mobile-next $S:refs/heads/snapshot/native-main-<date>`; the source sha goes into `native/LEDGER.md` | none (owner 2026-11-01) |
| Bring cloud work back | §2.1.1 step 6 (`git rebase --onto <source sha> <snapshot sha> cloud/<unit>`, then the normal merge) | none |
| Release snapshot: the bridge (W3) and the switch candidate (GO-1, §11.2) | `R=$(git commit-tree <candidate>^{tree} -p mobile-next/master -F <msg>)`; the message carries the release subject, a `Change-Class:` trailer (G33; memory `change-class-values`) and `[skip ci]`; check `git rev-parse $R^{tree}` equals `git rev-parse <candidate>^{tree}`; then push `$R` to `master` | owner GO for the push (memory `release-2.1-prep`: "ONE snapshot commit on mobile-next/master") |
| Tag and release run | tag `v<name>+<code>` on `$R`; the tag push starts nothing (a `[skip ci]` commit), so the release workflow is dispatched by hand, once | owner GO for each |
| After stage 2 | the Flutter-tree removal commit on `native/main` (C15-7), then one more release-style snapshot of that tree onto `master` | owner GO |

Option for the coordinator at S0 (C15-17): root `native/main` on the pushed `snapshot/freeze-2026-11-01` (`580803d47`)
and take the freeze line in with tree-sync commits instead of merges. `native/main` itself is then pushable and cloud work
merges back normally, but `twin_check.sh` must track the last synced freeze commit in a file. Default: 02 §7.2 as
written, with snapshots.

### 11.1 The bridge release (ADR D4; 02 §5.7)

| Item | Value |
|---|---|
| What | The Flutter freeze line after its gate passes, which already holds the native-style installer (`feat/app-updates-2026-10-31`, merged at `6b686bb7c`), plus S0-BRIDGE: the counts-only migration census if M-D2 is yes (05 W13); **the Gemma assistant's entry points hidden** (Settings row, search entry, chat menu item, with a Flutter test; 02 §5.7, S02-3), so nobody gets it and then loses it; `apkFor` falling back to exactly the universal file; the 64-bit pre-check if the 32-bit decision is "no" (16 §7.5); the desktop release check ignoring releases without a Linux asset; and the tag workflows limited to `v2.*` (§11.4). |
| Version | public `2.2.0+58` [E]; the owner-lineage local build = max(installed on the NX721J, installed on the emulator, highest hosted) + 10 (≥ 4521 today, §2.6). Both below 10000. |
| How | A **new Shorebird release, not a patch** (native code changed; memory `shorebird-is-mandatory`), through `scripts/release.sh`. Signed exactly as today: public lineage `842284B2…1C` by CI, owner lineage `1DE5BF08…D60C` locally (D2). |
| When | **W3** (Nov 16–22), right after the freeze gate, so as many installs as possible carry the Update button long before the switch (D15-2). |
| Needs | the owner's explicit GO for each of: the release snapshot on `master` (§11.0), push, CI quality run, tag, release run (dispatched by hand: a `[skip ci]` snapshot commit does not start the tag workflow, memory `release-2.1-prep`), publish. |
| Proof before GO | on the emulator, through `--dart-define=OC_APP_UPDATE_FEED`: the bridge's updater offers a dummy native APK built with versionCode 10000 and the native asset names, verifies `SHA256SUMS` and the signer, and hands it to `PackageInstaller`; a feed with an `-x86_64.apk` asset and no `-armeabi-v7a.apk` never hands a 32-bit phone the x86_64 file (S0-BRIDGE). |
| After the switch | a Shorebird patch to the bridge only for a security fix that affects people who have not updated, with the owner's OK (16 §7.2, D16-6). |

### 11.2 The switch candidate: gates, in order

1. **T2 green** on the candidate commit: all JVM and Robolectric tests, Konsist, lint, `assembleRelease` both flavors,
   `analyzeReleaseR8Config`, size gate (S-14), component freeze, `BannedDependenciesTest` (S-23), `ScopeFileTest`,
   `:quality:catalogReport` showing **0 red and 0 todo** in switch scope (S-1).
2. **T3 green** on the owner-lineage release APK: the 37 switch flows (S-2), J1–J8 on the owner lineage and from the
   bridge (S-3, S-4), monkey 3 seeds × 50,000 events with 0 crashes and 0 ANRs (S-21), the canary privacy scan and
   egress EG-1…EG-7 (S-22, harness from S2-EGRESS); plus the `OCN_36` smoke (J1–J3 and E2E-05; the owner's NX721J runs
   Android 16) and the `OCN_26` minSdk smoke (E2E-01, E2E-05) of 14 T14-2.
3. **The public-lineage draft (GO-1).** With the owner's GO, the candidate's tree goes to `master` as one release
   snapshot (§11.0), is tagged `v3.0.0+<code>`, and `native-release.yml` is dispatched. The result is a **draft** release
   whose APKs CI signed with `842284B2…` (only CI holds that key: 02 §7.9, 16 §6). The T3 holder runs the public-lineage
   legs of J1–J8 (over the published 2.1.0+57 and over the public bridge) on the draft's APKs and records each
   versionCode and signer. The draft stays unpublished; stage 1 publishes this same draft. If the candidate changes
   after GO-1 (a fix forward), the draft is deleted unpublished and GO-1 repeats with the next versionCode.
4. **T4 within budget**: S-5…S-20 on `OC_API37` relative to the Flutter baseline; NX721J absolute numbers with the
   owner's OK.
5. **Switch-ready checklist** (02 §6.7): every deferral and drop names an owner decision; PRIVACY.md rewritten; notices
   complete; the release channels of §11.4 in place (the Flutter tag workflows limited to `v2.*`, the bridge's desktop
   check filtering by Linux asset); D18 registration status written down.
6. **Identity checks**: `aapt2 dump badging` shows package `io.github.eslamasabry.opencode_mobile`, versionCode ≥ 10010,
   versionName `3.0.0`; `apksigner verify --print-certs` prints exactly one signer equal to the lineage's SHA-256 (the
   local key on the owner candidate, the public key on the draft); every ABI split carries the same versionCode; and
   `git rev-parse v3.0.0+<code>^{tree}` equals the owner candidate's tree (**same tree, not same commit**).

### 11.3 The owner's phone on switch day

Follow 05 §10.7 step by step. In short: read-only checks of the installed versionCode and signer (must be the owner
lineage `1DE5BF08…D60C`; if the phone carries the public lineage, stop and ask the owner, because the signers differ and
an install would need an uninstall); J1–J3 green on the emulator for this exact APK; the owner closes the Flutter app
normally (never force-stop); `adb install -r` while the owner is present; the owner opens the app and types any password
the re-enter sheet asks for; Report a problem (counts only) is compared with the bridge census; read-only check that the
phone server came back; a reboot within a day. Then the **two-day daily-use test** (S02-4). The coordinator uses
read-only adb only and takes no screen captures.

### 11.4 Release channels and the staged rollout

GitHub has no percentage rollout. Its `GET /repos/{owner}/{repo}/releases/latest` returns "the most recent
non-prerelease, non-draft release" [V, docs.github.com], and both the bridge's updater and the native `AppUpdater` read
exactly that endpoint [R app-updates SPEC; 16 §8.1]. The Flutter desktop check reads the whole release list instead,
pre-releases included, and takes the highest build number [V `lib/update/desktop_release_check.dart` at the freeze tip].
Every release therefore states its channel and sets `make_latest` explicitly, and the coordinator reads back
`GET /releases/latest` after each change.

| Channel | Tag and base | Workflows | Assets | `make_latest` | Read by |
|---|---|---|---|---|---|
| Native Android 3.x | `v3.*` on a release snapshot on `master` | `native-release.yml`, by dispatch only | `-arm64-v8a.apk`, `-x86_64.apk`, universal, `SHA256SUMS` | stage 1: pre-release, `false`; stage 2: `true` | the bridge's updater and the native `AppUpdater`; Obtainium |
| The bridge (2.2.0+58) | `v2.*` on the freeze line's release snapshot on `master` | `android-release.yml`, `desktop-linux.yml`, `byo-host-bundle.yml`, limited to `v2.*` tags by S0-BRIDGE (today they run on every `v*` tag; on a `v3` tag `android-release.yml` would fail on the pubspec version and `desktop-linux.yml` would `--clobber` Flutter Linux files into the native release) | Flutter APKs, Linux `tar.gz`/`.deb`, `SHA256SUMS`, `SHA256SUMS-linux` | `true` (W3) | every updater until stage 2 |
| Flutter Linux desktop security fixes (until M9, D14) | `v2.*` on `flutter/maint` | `desktop-linux.yml` | Linux assets only | always `false` | the desktop check of the bridge and later builds (it ignores releases without an `opencode-mobile-linux-` asset); never `releases/latest` |
| Bridge security fixes after the switch | none: a Shorebird **patch** to the exact bridge release (16 §7.2) | — | — | — | Shorebird |

Desktop builds older than the bridge still count every release and will offer 3.0.0; the 3.0.0 notes and the site's
download section say Linux desktop stays on the 2.x line until the native desktop app (M9).

| Stage | Who gets 3.0.0 | How | Lasts | Go to the next stage when | Halt |
|---|---|---|---|---|---|
| 0 | the owner's phone (owner lineage) | `adb install -r` (§11.3) | 2 days of daily use | the owner says "fine" and no S-1…S-4, S-21…S-23 issue is open | fix forward (R0); R1 if data looks wrong |
| 1 | people who opt into pre-releases (Obtainium with pre-releases on, manual download) | GO-2: `scripts/release.sh github --prerelease` publishes the gate-3 draft unchanged as a GitHub **pre-release** (`make_latest=false`); `GET /releases/latest` still returns the bridge | 7 days | no blocker in GitHub issues or from the owner; the draft's asset download counts are read; the owner's phone shows nothing new; the BASIC `migration_done` and `update_result` counts only if D16-1 named a destination (until then nothing is sent, so there is no field signal beyond these) | delete the pre-release assets or turn the release back into a draft; nobody on the bridge sees it |
| 2 | everyone on the bridge release | GO-3: `release.sh promote` (pre-release → latest, `make_latest=true`), then `GET /releases/latest` must return 3.0.0 | — | — | `release.sh demote`: back to pre-release, so `releases/latest` returns the bridge again and the in-app notice stops offering 3.0.0; installed native apps are fixed forward |
| 3 | people still on releases before the bridge | they have no in-app updater; release notes and the site say "download 3.0.0". If the owner says yes to S0-NOTICE (D15-9), people on 2.1.0+57 see a link-only notice from a Shorebird patch | — | — | — |

The site (`docs/site/`) and the release notes change only at stage 2.

### 11.5 Rollback (05 §9 is the authority)

| If | Do | Notes |
|---|---|---|
| a bug, no data at risk | **R0 fix forward**: next versionCode (+10), through the in-app updater; on the owner's phone by adb while present | the default |
| data looks wrong on the owner's phone during R1's window | **R1**: install the Flutter rollback build (native + 1, owner lineage, built in S6-R1); everything written by the native app is read back; returning to native runs `R1.return` | owner lineage only by default (D15-6); valid only while `flutterReadable` is true |
| the migration failed on a device | **R2** in-app: "Restore data from before the update" on the failure screen or in safe mode, while `files/migration/flutter-v57/` exists | |
| a public release must stop spreading | stage-2 halt in §11.4 (`release.sh demote`) | GitHub release edit only |

R1 retires when the owner says the native app is fine, or when the Room import ships, whichever comes first (M-D1).
Nothing Flutter-era is deleted before switch+2, and then only with the owner's OK (M-D3).

### 11.6 What ends at the switch

- **Shorebird**: no code push in the native app (NG17). The native `AppUpdater` is the only update path. The bridge
  keeps Shorebird for security-only patches (16 §7.2, D16-6).
- **The Flutter Android line**: tag `flutter-final-<version>`, cut `flutter/maint` (02 §5.8). On `flutter/maint` the
  Flutter release path (`scripts/release.sh`, `android-release.yml`, `desktop-linux.yml`) takes `flutter/maint`, not
  `master`, as its required base; the coordinator makes that one change when the branch is cut. The Flutter Linux
  desktop build keeps security fixes only until M9 (D14), published as the §11.4 channel table says.
- **The Flutter tree on `native/main`**: removed in one commit **after stage 2** (C15-7); every catalog `sources` path
  stays readable at the tag. Then, with the owner's GO, **one snapshot commit of `native/main`'s tree onto
  `mobile-next/master`** (§11.0), which also drops the Flutter workflows (`android-quality`, `android-release`,
  `desktop-linux`, `desktop-windows`, `ios-quality`, `byo-host-bundle` unless the native release builds the bundle)
  from `master`; they keep working on `flutter/maint`. `master` is never fast-forwarded to `native/main` (02 §5.8 step
  3 is replaced, C15-17).
- **The preview flavor**: stays in the build as the place for future trials; its kit-tour entry is deleted.

### 11.7 Developer verification (ADR D18)

Google's page says verification "will be expanded globally for all apps on certified Android devices in 2027" and gives
no date for any region beyond the four of 30 September 2026 [V, developer.android.com/developer-verification, read
2026-11-01]; once it reaches a region, unregistered apps can be updated only through a one-day advanced flow or adb
[R android-surfaces §14]. That would break the in-app updater, and the **bridge's own self-update path** as well, so
registration does not depend on native code. The owner registers `io.github.eslamasabry.opencode_mobile` (and the
preview package) with **every signer in use**: local `1DE5BF08…D60C`, public `842284B2…1C`, and the CI test signer
`2D010C21…D0EC` if any installed population carries it. Routes: a full Android Developer Console account ($25, ID check;
16 D16-7), or Google's open-source app registration guide, which covers existing open-source apps whose repository ships
the developer's own signature, as our GitHub releases do [V,
developer.android.com/developer-verification/guides/open-source-app-registration, updated 2026-10-23]; limited
accounts cap at 20 devices and do not fit. **Ask in W2 with D4**; register before the bridge has spread, and in any case
before stage 2. The coordinator checks the page monthly (risk R15-18).

---

## 12. After the switch

| Phase | Starts | Contents | Size [E] | Gate to start |
|---|---|---|---|---|
| **M7 switch+1** | the week after stage 2 | Room `oc.db` import (05 W11) unless the P5 trigger already moved it; the targetSdk 37 slice (local-network permission UX, CT test against self-signed and pinned servers, the installer under the Android 17 `IntentSender` rules, reflection audit, RemoteViews budget; android-surfaces §16); Flutter tree removal; Kotlin 2.5 + AGP 9.5 + Gradle 9.8 bump together (ADR §8); first owner-chosen additions: shared pictures in and Direct Share (O-07-4, D10-2), "Scan from a picture" (D10-3) | 4–6 aw | switch at stage 2 |
| switch+2 | after M7 | v3 cleanup of Flutter-era copies (05 §9.5) | 0.5 aw | the owner's OK (M-D3) |
| **M8 AI Team + Memory** | after M7, in parallel with M9 (D15-7); right after stage 2, beside M7, if the owner picks D6 (b) (§17.1) | `:protocol:team`, `:engine:team`, `:feature:team`, `:android:team` (phone engine bridge with the release hash check), FactsBook; the role cycle and role voices ported from their branches (`feat/role-voices-2026-11-01`; memory `role-cycle-ai-team`); E2E-28 and E2E-32; AppFunctions (ADR-D16) and the Gemma assistant (02 §3.5) as separate units | 12–17 aw (team blocks 9–14 aw, proposal A §18) | ADR D6 |
| **M9 Linux desktop** | after M7 | `:desktopApp` shell, desktop bindings (prefs JSON, Secret Service, notifications, `xdg-open`, file drop), migration from the Flutter Linux build's `shared_preferences.json` and libsecret item, local `RuntimeHost` without proot, `.deb` + tarball + AppImage; desktop-phase rows of §14 | 6–10 aw | D14 accepted |
| **M10 iOS** | when funded | remote client only: iOS targets on, Darwin Ktor with leaf pinning, Keychain `SecretStore`, iOS kit variants, sherpa-onnx xcframework, push relay for "reply finished", WidgetKit optional, TestFlight | 12–18 aw (18–30 with widgets and push) | D13: a Mac or macOS CI, the Apple Developer Program, a push relay, a Liquid Glass decision, an iOS design pass over the top 10 screens |
| **MW widget tests** | asked at stage 2, with the default start the week after the switch release (14 T14-4); asked earlier when the 14 §5.2 trigger fires (C15-15) | 14 §5.3: W-1 classify a sample of 200 Flutter widget tests (ADR §4 row 18), W-2 catalog, W-3 port by screen module on desktop and Robolectric harnesses, W-4 parity | 60–130 ad after W-1 [R 14 §5.4] (proposal A guessed 15–25 aw) | the owner's yes; the owner's words: "7400 for widgets can come later" |
| F-Droid submission | after M7 | Lite build first (D10-6); QR is already Google-free (zxing-cpp, no ML Kit); the "Tracking" anti-feature applies if BASIC analytics sends anything, and the owner decides **at this submission** between the label and a `sender=none` F-Droid build (18 §7, D16-2) | 1–2 aw | the owner's OK |
| **Glass trial** (owner, 2026-11-01) | after the switch, any time after M7 | an optional sixth Style: real blur (`RenderEffect`, or Haze per 12 N-98) plus AGSL refraction, **top bar and sheets only**, solid fallback below API 33 and under Saver, motion Off and high contrast; behind an owner flag (06 WP G1). Until then the only blur is the two frost places (composer, chat header) | 1–2 aw | the owner judges it on the phone; it ships only if they like it, otherwise flag and file are deleted |

Zero-cost preparation for MW that happens now (14 §5.1): test tags equal Flutter `ValueKey` strings, and every screen
takes `(XUi, XActions)` so a widget test can render it with fakes (ADR §4 row 18); the widget-test name index
`native/catalog/widget-index.tsv` (S0-SCOPE) goes into every screen brief's read set as a checklist; builders note
"Widget-test candidates" in their QA records; the coordinator keeps the "Widget-test trigger" count in
`native/LEDGER.md` (§2.7, §3.3). These stand in for the 7,967 deferred tests until MW.

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
| S2 | 08, 18, 20 (`KitTip`, `TipController`, registry; `KitTour` and the `make-it-yours` tour follow Appearance in S4), 25, 32, 43, 45, 51 |
| S3 | 42, 50, 52 |
| S4 | 14, 15, 16, 19, 20 (tour only), 39, 56 |
| S5 | 44, 46, 47, 48, 49, 53 |
| After | 17, 55 (D11); 54 (D6) |

### 13.6 screens-nav (112)

| Lands in | Features (SCREENS-NAV-nn) |
|---|---|
| S1 | 04, 23, 26, 105, 106, 107, 108, 109 |
| S2 | 01, 02, 03, 05, 06, 07, 08, 09, 16, 17, 21, 24, 27, 29, 30, 34, 35, 37, 38, 39, 40, 41, 42, 43, 50, 71, 72, 73 |
| S3 | 20, 28, 44, 46, 47, 48, 49, 51, 57, 62, 74, 76, 77, 78, 79, 82, 83, 84, 110, 111 |
| S4 | 14, 15, 18, 19, 22 (secondary Flutter entrypoints disappear), 25 (Help › Tips page L06 and the tour; the registry and in-place tips land in S2 with S2-TEACH), 36, 54, 55, 56, 75, 90, 91, 92, 93, 98, 100, 102, 103, 104, 112 |
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
| After | 10 (D6), 59 (ADR-D16), 61 (02 §3.5) |

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
| S4 | 10, 29, 33, 45, 46 (the analytics rows; the core lands in S1, the sender as D16-1 decides), 54, 56, 58, 60, 63 |
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

### 13.13 Items outside the 700: freeze-tip features (02 §3.5) and the deferred-feature rules (02 §3.7)

The 700 map features were taken at `65e3f70a3`, before the freeze tip, so §3.1 item 5 also checks these.

| Item | Disposition | Owning unit |
|---|---|---|
| App updates: notice, gear badge, Update now sheet, Updates page | Switch | S4-UPDATER (with S4-ABOUT's Updates page) |
| Process budget, kill line, guided "Disable child process restrictions" card | Switch | S3-RTENG (budget), S3-RTWIRE (kill line), S3-PHONEUI (card) |
| Background-CPU fix, heat and battery pacing, agent background priority | Switch | S1-PACER, S3-RTENG, S2-BACKOFF (13 WP-13.5–13.7) |
| The phone server restarts itself after an OpenCode update | Switch | S3-PINS |
| Wi-Fi share of chat file links; opening a phone file from a chat link | Switch | S5-WIFI, S5-VIEWER (B-MEDIA-036, 037) |
| Linux services pages, vitals, backup and restore | Switch | S5-LINUXMGR |
| Paste a picture from the clipboard | Switch | S3-MEDIAATT (B-MEDIA-035) |
| Star the app, share the app, What's new walkthrough | Switch | S4-ABOUT |
| Steer-join, partial-syntax streaming, large-text and free-limit fixes; `codex/audit3` security fixes | Switch | S2-TX, S2-DLV, S2-KCA; the security fixes with their SEC rows' units (S0-TOPUP assigns them) |
| Tested pins, idle self-updates, updates that yield, Wi-Fi only for big downloads | Switch | S3-PINS |
| Claude Agent beside Claude Code | follows D8 | — (default Paseo) |
| Role voices in AI Team | after the switch (D6) | M8 |
| Gemma assistant | after the switch; entry points hidden in the bridge | S0-BRIDGE (hide); S4-SETTINGS and S5-LINUXMGR (the model folder with Delete) |
| Browser mode | dropped (D17) | — |
| AppFunctions | after the switch (ADR-D16) | M8 |
| 02 §3.7 item 1: absent, never dead (no entry points for AI Team, Memory, A2A) | switch rule | every screen unit's brief; S4-WIDGETS (no team rows on the widget); S2-ENTRY (`opencode-mobile://team` opens Conversations) |
| 02 §3.7 item 2: never started; stop by exact PID at first launch, recorded in the audit | switch rule | S3-RTWIRE (stop), S2-MIGRRUN (audit), S6-MIGRHARD (proved in J1) |
| 02 §3.7 item 3: data kept and still swept by registered shapes | switch rule | S1-DATA (`ProfileScope` shapes), S1-ENG (deletion transaction; the FORCE rows of Appendix B) |
| 02 §3.7 item 4: one notice | switch rule | S2-GATEUI (with S2-MIGRRUN's census flag) |
| 02 §3.7 item 5: space can be freed (`files/local_llm_models/`) | switch rule | S4-SETTINGS (Manage space, Storage), S5-LINUXMGR (Linux storage page) |

---

## 14. Rows and flows by slice

### 14.1 Rows (estimate; the scope file is the truth)

Computed today by Appendix B, which applies its FORCE table first (the rows 02 §3.3 says enter the switch on day 1,
and the S2-ENTRY re-scope of B-NAV-061 and B-ANDR-071) and then reads each other row's first Flutter source file name.
Without the FORCE table, 26 of the 02 §3.3 "stay" rows fell into DROP or AFTER (the built-in Linux rows named
`termux_*`, and the deletion and redaction rows of rule 3). The coordinator runs it in S0-SCOPE to seed `slice` in
`native/catalog/scope.jsonl`, then reads **every** row by its text (02 §10 item 4) and adds `module` and the owning
unit per row.

| Bucket | Rows | Of which device-flow rows | By area |
|---|---|---|---|
| S0 | 12 | 1 | BUILD 6, XPLAT 3, ANDR 2, SEC 1 |
| S1 | 140 | 26 | PROTO 35, SEC 35, KIT 27, STATE 15, L10N 12, ANDR 5, NAV 5, AGENT 4, CHAT 1, MEDIA 1 |
| S2 | 136 | 31 | STATE 45, CHAT 30, PROTO 17, NAV 17, ANDR 8, SEC 7, BUILD 4, KIT 4, AGENT 2, L10N 2 |
| S3 | 357 | 51 | LINUX 137, AGENT 79, CHAT 34, STATE 34, SEC 25, ANDR 21, PROTO 11, NAV 8, MEDIA 3, BUILD 2, L10N 2, KIT 1 |
| S4 | 220 | 55 | ANDR 67, STATE 33, SEC 31, NAV 27, KIT 24, AGENT 16, CHAT 10, PROTO 6, L10N 3, LINUX 3 |
| S5 | 404 | 44 | AGENT 99, LINUX 66, SEC 57, CHAT 48, MEDIA 29, STATE 27, PROTO 26, NAV 18, ANDR 16, BUILD 11, XPLAT 3, KIT 2, L10N 2 |
| S6 | 18 | 10 | MIGR 13, SEC 2, BUILD 2, ANDR 1 |
| After (D6 and similar) | 197 | 16 | AGENT 108, LINUX 37, PROTO 23, SEC 15, KIT 4, STATE 3, others 7 |
| Dropped or not ported | 31 | 1 | PROTO 9, SEC 9, AGENT 7, LINUX 4, NAV 1, STATE 1 |
| Desktop / iOS | 14 | 0 | BUILD 7, XPLAT 7 |
| MANUAL (triage in S0-SCOPE) | 15 | 5 | |
| **Total** | **1,544** | **240** | |

In switch scope: 1,287 + up to 15 MANUAL ≈ 1,302 catalog rows, against 02 §3.3's independent estimate of 1,336. Both are
[E]. On top come rows that do not exist in `behaviors.jsonl` yet, which S0-SCOPE appends before S0-TW1 (§4.2), so that
the §3.4 `jq` filter finds the rows the unit tables cite:

| Rows | Defined in | Count | Lands mostly in |
|---|---|---|---|
| B-PROTO-128…148 | 04 §15.5 (16 cites B-PROTO-128 and 139 with the same meaning; no collision) | 21 | S1 (`:core:net*`, `:protocol:oc2`), some S2, S4, S5 |
| B-SEC-184…199 | 16 §14 | 16 | S1–S5 by their tests |
| B-MEDIA-035…037 | 10 (freeze-tip media) | 3 | S3 (035), S5 (036, 037) |
| freeze-tip top-up (S0-TOPUP) | 02 §3.5 | ≈ 100 [E] | by feature (§13.13) |

So the switch gate counts about 1,440 rows [E].

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

## 15. What the owner sees each week (calendar A; calendar B's milestones and asks in the last column; calendar C from Appendix A with `--cloud 3 --asks`)

Every message follows the owner's rules: a few lines, then "Decision needed: yes/no" (memory `short-replies-rule`); one
full phone screen per image with one sentence (`one-screen-per-image`); feature proofs as short real recordings
(`live-video-proof`); an APK only after an emulator sweep (`stabilize-before-shipping`, `test-on-emulator-not-phone`).

| Week | Dates | What happened (one line) | What you get | Decision asked (calendar A) | Calendar B |
|---|---|---|---|---|---|
| W1 | Nov 2–8 | Astra review of the plan and its fixes (G-A); skeleton and the ten proofs started; the Flutter freeze gate runs | one image: the native "Opening…" screen | D15 capacity: cloud agents, a second build server, both, or a KVM test host; D1 confirm the plan; **D6 AI Team gap** (real decision); D16-9 key backups; T14-1…6 test setup | same |
| W2 | Nov 9–15 | proofs finished; M0 gate; the PC's agent limit measured | video: back gesture with the grow-from-row motion + the 5,000-part streaming test | D4 bridge GO (with D15-2); 32-bit phones (with the download counts); M-D2 census in the bridge; D18 registration; every in-flight branch (ADR-D16); D15-9 notice patch | same |
| W3 | Nov 16–22 | re-plan done; bridge published if you said yes | the new dates in four lines; kit images (light, dark, Arabic) | D16-1 analytics destination (default: nothing sent), D16-2, D9 Room | B starts with 9 slots; C starts the cloud lane |
| W4 | Nov 23–29 | the 37 phone flows run green on today's app | one line: "37 of 37 flows pass on the Flutter app" (or the gaps) | none | P13-D3 performance recorder |
| W5 | Nov 30–Dec 6 | the new engine talked to your OpenCode 2 on the VPS | video: kit tour on the emulator | 06 Q1–Q9 defaults, each before the unit that builds it | D10 frost; D8-1…4 motion |
| W6 | Dec 7–13 | words in 7 languages; the shell | images: Arabic and 200 % text | D10 frost; D8-1…4 motion | O-07-1…4 |
| W7 | Dec 14–20 | **M1** | **P0 kit tour APK** + what to try | "Does the feel match the demo? yes/no" | M1; D8 phone agents path |
| W8 | Dec 21–27 | Servers screen | images: Servers and Add server, native next to Flutter | O-07-1…3 defaults (and O-07-4) | Q09-1…4 |
| W9 | Dec 28–Jan 3 | Conversations list; the migration runner | images | P13-D3 performance recorder | |
| W10 | Jan 4–10 | the transcript engine streams | video: a reply streaming in the native chat (emulator) | D8 phone agents path (Paseo) | P13-D4 phone measuring sessions |
| W11 | Jan 11–17 | chat and delivery | images: chat with code; find | Q09-1…4 defaults | **M2: P1 APK**; D15-4; D16-4; 11 §25 and D16-3 |
| W12 | Jan 18–24 | steer, Stop, permission | video | none | |
| W13 | Jan 25–31 | chat polish; J1–J3 numbers vs Flutter | one numbers line | P13-D4 two measuring sessions on your phone | |
| W14 | Feb 1–7 | **M2** | **P1 "chat with your server" APK** | D15-4 second Linux in the preview on your phone (child-process limit, R15-20) | |
| W15 | Feb 8–14 | Paseo; the runtime split | video: phone setup on the emulator | D16-4 Linux sees only opened folders | **M3: P2 APK** |
| W16 | Feb 15–21 | the phone runtime wired; agents engine | video: Claude Code via Paseo on the emulator | none | D10-1…11 media |
| W17 | Feb 22–28 | the full composer; composer motion | images: chips, tray, attachments; the glow next to Flutter's | 11 §25 defaults; D16-3 lock-screen unlock | |
| W18 | Mar 1–7 | phone pages, agents hub | images | none | **M4: P3 APK** |
| W19 | Mar 8–14 | **M3** | **P2 "phone + agents" APK** (or videos if D15-4 is no) | none | |
| W20 | Mar 15–21 | notifications and tiles | video: answering from the shade | none | M-D1, M-D3…8; R12-8, D12, D16-5/6/8, D15-3 |
| W21 | Mar 22–28 | schedules; settings | video: a scheduled run | none | |
| W22 | Mar 29–Apr 4 | widgets; restore after boot and update | images: both widgets on the launcher | none | **M5: P4 APK**; GO-1 candidate draft |
| W23 | Apr 5–11 | styles; the in-app updater | one image per style per mode | D10-1…11 media defaults | **candidate** |
| W24 | Apr 12–18 | **M4** | **P3 "around the phone" APK** | none | **your phone** |
| W25 | Apr 19–25 | Codex, files | video: files and Git | none | **pre-release** (GO-2) |
| W26 | Apr 26–May 2 | terminal, viewer | video: terminal | none | **latest** (GO-3) |
| W27 | May 3–9 | voice | video: dictation | none | D11, D15-7 |
| W28 | May 10–16 | read aloud, cards, Linux manager | video: read aloud | none | |
| W29 | May 17–23 | integrations, Paseo extras | images | M-D1, M-D3…M-D8 migration defaults; D15-6 | |
| W30 | May 24–30 | the last slice-5 units; slice 6 starts | images | R12-8 licence wording; D12 libraries at the switch; D16-5, D16-6, D16-8; D15-3 | |
| W31 | May 31–Jun 6 | **M5** | **P4 feature-complete APK**: use it daily for a week beside Flutter and list differences | GO-1: snapshot to master, push, tag, CI draft | |
| W32 | Jun 7–13 | switch candidate built; public-lineage draft checked | video: Flutter → native upgrade on the emulator, everything kept | none | |
| W33 | Jun 14–20 | **M6: the switch on your phone** (adb while you are there) | two-day daily use | "Switch went fine? yes/no" | |
| W34 | Jun 21–27 | public pre-release | release page link | GO-2: publish the draft as a pre-release | |
| W35 | Jun 28–Jul 4 | marked latest; the bridge's Update button offers 3.0.0 | — | GO-3: mark latest | |
| W36 | Jul 5–11 | M7 starts (and M8 if D6 is (b)) | — | D11 live backgrounds; D15-7 order after the switch | |

---

## 16. Risk register

Likelihood and impact: H/M/L. "Signal" is what the coordinator watches for; "Owner" is the role that acts.

| # | Risk | L / I | Signal | Mitigation (now) | Fallback | Owner |
|---|---|---|---|---|---|---|
| R15-01 | Throughput is lower than planned; the switch slips more than two weeks past the plan of record | M / H | re-plan factor `f` < 0.8 at M0 or M2 | the cloud lane (calendar C) and a second VPS (D15); one-unit-one-agent discipline; contracts frozen before fan-out; TW lane ahead | the scope valve (§19, worth about 1–2 weeks), owner-approved | coordinator |
| R15-02 | CMP 1.13 / Material 3 1.5 are not stable by the switch | M / M | no 1.13.x stable by the week S6-RELEASE starts | D12 allows a beta M3 only inside the kit; CMP 1.13 stable is projected about mid-Dec 2026 [U, kmp-cmp-status §13]; GitHub shows 1.13.0-beta01 as the newest 1.13 build on 2026-11-01 [V] | 02 S02-6: the switch waits for 1.13.x stable; if it slips more than two weeks past switch-ready, the owner decides whether to ship the 1.13 beta (recorded in the notes). CMP 1.12.2 is a fallback only at the P1 verdict in M0 (ADR §11): it pairs with material3 1.12.0-alpha03 (= Jetpack M3 1.5.0-alpha22 [V, CMP release notes]), an alpha that D12 bans from the release classpath, and a later downgrade would undo months of code written against 1.13 | build-logic owner |
| R15-03 | A toolchain bump mid-rewrite (Kotlin 2.5 is planned for Dec 2026) breaks Metro or CMP | M / M | a bump request in a brief | pins in ADR §8; Kotlin, the Compose compiler, Metro and KSP move together, only at a slice boundary with T2 + T4 | stay on 2.4.21 until switch+1 | build-logic owner |
| R15-04 | Predictive back fights the row → chat transform again | M / M | P2 checks fail | option A disables shared bounds during the gesture (ADR §9.4) | plain shared-axis until option B | Opus (S0-NAV) |
| R15-05 | Streaming janks on the owner's phone | L / H | S-9…S-12 miss at a slice-end T4 | P3/P4 in M0; the 50 ms publish; draw-phase fade; benchmarks at S2, S3, S6 | per-part `StateFlow<String>` (ADR §9.3) | chat lead |
| R15-06 | Silent data loss in the migration (secrets, prefs encodings, `DartJson` fingerprints) | L / H | any J1–J8 failure; audit count mismatch | real-install fixture; byte backup S0; per-entry re-enter flags; Opus owner | R2 restore; R1 rollback; fix forward | migration owner |
| R15-07 | An OS-visible name changes, so widgets, tiles, shortcuts or schedules vanish on update | L / H | `ComponentFreezeTest` red at T2 | the freeze list from the 2.1.0+57 APK; J1 checks placed widgets and tiles | — (blocks the switch) | coordinator |
| R15-08 | Built-in Linux behaviour drifts during the move or split | M / H | a copied test changes or fails | move-green first (S1-LINUX); one lock family; tests before edits; Opus split | revert the split, keep the copy | runtime owner |
| R15-09 | The one emulator throttles T3, flows and proofs | H / M | bookings full three days running; emulator demand over supply in two of four weeks (§2.4 step 4) | §2.5 rules; desktop and Robolectric first; journeys in the evening; the night block for long tiers | a KVM-capable second test host [U] (D15 option d); the owner decides | coordinator |
| R15-10 | The PC runs out of memory and kills sessions | M / H | `free -m` < 7 GB; swap over half full | the session cap measured at M0 (§2.1); builds and tests on the VPS; lean Codex; `hog_watch.py` armed; ≤ 1 PC Gradle build; sessions above the cap go to the cloud lane | pause agents (owner decision if work stops) | coordinator |
| R15-11 | The VPS disk fills mid-run | M / M | `df -h /` < 30 GB before T2 | worktree cleanup after merge (02 §7.3); prune Gradle caches | Storage2 offload | coordinator |
| R15-12 | Codex quota runs out | M / M | quota warning in a session | Codex units are small and contract-bound | a Sonnet agent takes over the worktree with the commits and diff (02 §7.7) | coordinator |
| R15-13 | OpenCode 2, Paseo or Claude Code change under us | H / M | probe drift (P-N4); live proof failures | the dialect table; pins stay 2.1.1 / Paseo 0.11.2 at the switch; drift tests | re-record fixtures before the affected WP | backend (Codex) |
| R15-14 | Flutter fixes during the freeze are not mirrored | M / M | `twin_check.sh` lists unsynced commits | `Behaviors:` trailer on every Flutter commit; weekly twin check | — | coordinator |
| R15-15 | A copy change breaks the parity flows | M / L | a flow fails on text | asserted strings frozen; `native-only` variants need the owner's OK (02 §6.6) | — | l10n owner |
| R15-16 | Widget tests come later, so UI regressions slip past logic rows | M / M | the "Widget-test trigger" count in `native/LEDGER.md` (5 in a slice, or 2 the owner finds in one preview) | per-part kit gates (06 Q2), Roborazzi, flows, weekly sweeps with the §2.5 matrix, the widget-test name index in every screen brief, "Widget-test candidates" in QA records, the S6 audit | the owner is asked to start MW at the next slice (14 §5.2, C15-15) | kit owner |
| R15-17 | The owner rejects the look late | M / H | "no" at P0 or P1 | P0 kit tour early; side-by-side images weekly; Opus render-compare for premium screens (`judge-visuals-hard`) | redo the kit part, not the screen | kit owner |
| R15-18 | Developer verification reaches the owner's users before registration | L / H | the Google page changes | ask D18 in W2 and register right after the bridge spreads, before stage 2 at the latest (§11.7); check monthly | — | owner |
| R15-19 | A delivered APK has the wrong signer | L / H | `apksigner` mismatch in `t3.sh` | scripted check before any delivery; never substitute signers | stop delivery | T3 holder |
| R15-20 | The preview app clashes with the Flutter app on the owner's phone (ports, Linux, storage, and Android's limit of 32 child processes shared by every app on the phone, 11 §7.1) | M / H | the preview's server fails to start; exit 137 or a `PhantomProcessRecord` kill in read-only `dumpsys activity exit-info` for either app | separate ports (S11-6); separate data; D15-4 asks first and runs the preview's Linux only when "Disable child process restrictions" is on (read-only check of the settings flag) or while the Flutter app's phone server and agents are stopped | emulator videos only | runtime owner |
| R15-21 | The analytics question stays open | M / L | D16-1 (= R12-1 = S02-5) unanswered when stage 1 starts | the core is built in S1 with `sender=none`; nothing is sent; stage 1 uses other signals (§11.4) | ship without a sender | coordinator |
| R15-22 | Android 17 rules (memory limiter, FGS, background audio) change behaviour on the owner's phone | M / M | exit reasons `MemoryLimiter:AnonSwap`; FGS refusals | 11 §7.3, §9; P-M5 | per-rule fixes at switch+1 | android owners |
| R15-23 | The Room trigger fires late | L / M | P5 numbers near the threshold | measure in M0 (P5), decide D9 at W3 | Room in S6 (+1.5 aw) | migration owner |
| R15-24 | The desktop target doubles build cost | M / M | P8 numbers | nightly-only desktop compile (ADR §4 row 17) | — | build-logic owner |
| R15-25 | The owner is away when a preview or the switch is ready (holidays) | M / M | no reply in 3 days | emulator-first; the switch waits, never ships untested | — | coordinator |
| R15-26 | Shared files (`Sheets.kt`, route files, string files) become merge hotspots | M / L | repeated conflicts | single-owner files; one string file and one route file per feature | — | coordinator |
| R15-27 | In-flight Flutter branches change the scope late (ADR-D16) | M / M | a new branch merged on the freeze line | decide every branch at the freeze gate (W2), including `feat/role-voices-2026-11-01`, `feat/process-budget-2026-11-01` and AppFunctions; anything that lands later is "port from branch" with its rows added by the top-up (S0-TOPUP) | port from the branch | coordinator |
| R15-28 | A security invariant regresses (link gate, redaction, cleartext) | L / H | B-SEC rows red; canary hits | Konsist bans; S-22 canary scan at T2 and T3 | blocks the switch | backend owner |
| R15-29 | The APK exceeds 22 MB arm64 | M / L | size gate at T2 | registry budget (12 §11); R12-6 sherpa static variant | owner relaxes S-14 | build-logic owner |
| R15-30 | 32-bit phones get no working update | M / M | the bridge offers a 32-bit phone a file it cannot install | one 32-bit decision in W2 (§17.1, merging D15-8, P13-D1, D16-10, D11-14); with the default "no 32-bit", S0-BRIDGE adds the 64-bit pre-check and the exact universal fallback, so a 32-bit phone is told plainly and downloads nothing | if the owner keeps 32-bit: the costed unit S5-V7A and a `-armeabi-v7a` asset | release owner |
| R15-31 | The OC2 critical chain (S1-OC2 → S2-TX → S2-CHAT) slips and P1 moves | M / H | S1-OC2 not merged by W7 (A) | S2-TX and S2-CHAT start on frozen contracts with fakes (`@k` dependencies) | P1 with a fixture server first | coordinator |
| R15-32 | A snapshot push leaks a secret, a private address or the owner's e-mail | L / H | `scan_push.sh` hit; an author other than the noreply address | snapshots only through `snapshot_push.sh` with the scan and the noreply author (§11.0, §2.1.1); never master, tags or releases without a GO | forward scrub commit, then rotate anything secret (memory `push-freely-snapshot-recipe`) | coordinator |
| R15-33 | A native tag fires the Flutter release workflows, or the Flutter desktop check offers 3.0.0 to Linux users | M / M | a red `android-release` run or Flutter assets in a `v3` release | S0-BRIDGE limits the tag workflows to `v2.*` and filters the desktop check by Linux asset; `make_latest` set on every release (§11.4) | delete the stray assets; notes and site explain the Linux line | release owner |
| R15-34 | AI Team disappears from the owner's app for months after the switch | H / M | D6 answered (a) | ask D6 as a real decision in W1 with the gap in plain words (§17.1) | D6 (b): start M8 right after stage 2 | coordinator |

---

## 17. Owner decisions

### 17.1 The decision calendar (asked one or two at a time, in the weekly message)

Work proceeds on the default until the owner answers (ADR §12, 02 §11). Each ask is anchored to a week or to a unit, so
it lands before the work it shapes on every calendar; `plan_calendar.py --asks` prints the weeks (Appendix A), and the
columns below are its output for calendars A and B at `--eff 0.9`. Calendar C: run it with `--cloud 3 --asks`. "Inform"
means the default is told, not asked. "ADR-D16" is the ADR's in-flight-branches decision; "D16-n" are section 16's.

| # | Plain question | Default | Anchor | A | B | Detail |
|---|---|---|---|---|---|---|
| D15 (+ D15-1) | More capacity: cloud agents (your phone gets the switch about mid-May), a second build server (about mid-April, if this PC can run about 14 agent sessions, measured in W2), both (about late March), or a KVM test host for the one emulator that every option leans on? The switch date always follows parity, never the reverse. | Cloud agents now; a second server if the W2 measurement and the first re-plan show we are slow | fixed | W1 | W1 | ADR D15; §2.1, §2.3, §2.5 |
| D1 | Confirm: iOS or Linux desktop within about a year (keeps one shared UI)? | Keep A | fixed | W1 | W1 | ADR D1 |
| **D6** | AI Team (and Memory) leave your app at the switch, on your phone from stage 0. When do they come back? (a) as planned, after the clean-up release: about September–November 2027; (b) start right after stage 2, beside the clean-up: about August–September; (c) keep AI Team on the phone engine in the switch (+E2E-28, about 9–14 agent-weeks; the switch about 2–3 weeks later). You are building AI Team now (role cycle, role voices). | (b) | fixed | W1 | W1 | ADR D6; §12 M8; R15-34; memory `role-cycle-ai-team` |
| D16-9 | Confirm two offline backups of each signing key and its passwords; release job behind a GitHub `release` environment with you as reviewer | Yes (an action for you) | fixed | W1 | W1 | 16 §6.3 |
| T14-1…T14-6 | Red tests stay off `native/main`; the emulator set (with Android 16 and minSdk smoke images); delete six old emulators (≈ 40 GB); widget-test start rule; flake rule; no retries for the candidate | As 14 §13 | fixed | W1 | W1 | 14 §13 |
| S02-1 / D03-1 | Native code in `native/` of the same repo? | Yes | fixed | W1 (inform) | W1 | 02 §11; 03 §24 |
| D4 + D15-2 | Publish the bridge update right after the freeze gate (W3)? | Yes | fixed | W2 | W2 | §11.1 |
| 32-bit | Phones that run only 32-bit apps: the native app is 64-bit (ADR §9.9). Of the 2.1.0+57 APK downloads, 3 of 29 were the 32-bit file; for 2.0.0+56, 7 of 112 [V `gh release view`, 2026-11-01]. (a) No 32-bit: those phones stay on 2.x and the bridge tells them so in plain words; (b) a remote-only 32-bit APK without the built-in Linux (unit S5-V7A, ≈ 6 ad, plus a 32-bit test image and a release asset; Appendix A is then rerun with it) | (a) | fixed | W2 | W2 | merges D15-8, 13 P13-D1, 16 D16-10, 11 D11-14; ADR §9.9; 16 §7.5 |
| M-D2 | Add a counts-only "migration check" to the bridge, so we know the real data shape before the switch? | Yes, if the bridge ships | fixed (it ships in W3) | W2 | W2 | 05 §12 |
| D18 (+ D16-7) | Register the package and every signer for Android developer verification (your account; full account $25, or Google's open-source app route) | Yes, right after the bridge ships | fixed | W2 | W2 | §11.7; 16 §6.5 |
| ADR-D16 | Every in-flight Flutter branch (role voices, process budget, AppFunctions, …): land before the freeze or port from its branch | Per 02 §3.5 | the freeze gate | W2 | W2 | ADR D16; R15-27 |
| D15-9 | A small update to 2.1.0+57 that only shows "A new version is ready" with a link (no installer) | Yes, once the bridge is published | fixed | W2 | W2 | S0-NOTICE; §11.4 stage 3 |
| D2, D3, D5, D7, D17, S02-2…4, S02-7, S02-8, D15-5 | Keep both signers; versionCode 10000, name 3.0.0; drop "press back again"; Termux move-out only; no web, no Windows; the 02 §4 numbers, the freeze-tip delta, a two-day phone test; saved A2A agents left untouched until the v3 cleanup; "While you were away" stops recording; previews at M1–M5 | As stated | fixed | W2 (inform) | W2 | ADR §12; 02 §11; §17.2 |
| D16-1 (= R12-1 = S02-5) + D16-2 | Analytics: where may BASIC events go, and is BASIC always on? | Nothing is sent until you choose (`sender=none`); recommended destination Aptabase EU (16 §16); BASIC always on | fixed | W3 | W3 | 16 §5, §16; 12 R12-1 |
| D9 | Room timing after the P5 numbers | Switch+1 unless triggered | after P5 | W3 | W3 | ADR §9.8 |
| N4-O1…O5 | Retire direct-ACP and OpenCode 1 rows; pin 2.1.1; retire session-address links; fix receipts natively only | Yes to all | fixed | W3 (inform) | W3 | 04 §19 |
| 06 Q1–Q9 | 7 languages; kit gates now; Material pickers; reply direction; lens springs; no swipe tick; Copy + Select all; no hinge layouts; digits as today | As 06 §19 | before the kit unit that builds each | W3–W18 | W3–W12 | 06 §19 |
| D10 | Frost on composer and chat header | Keep | before S2-KCB | W6 | W5 | ADR D10 |
| D8-1…4 | Today's vibrations; no keyboard drag gesture; no extra ticks; keep the portal pull-to-refresh | As 08 §12 | before S2-KITB | W6 | W5 | 08 §12 |
| O-07-1…4 | Rail stays on wide screens; Pin moves to the menus; files open as a page; share pictures in after the switch | Yes / yes / yes / after | before S2-CHATS | W8 | W6 | 07 §13 |
| P13-D3 | Release builds "profileable", and an off-by-default "record a performance trace" switch | Yes to both | before S2-FIELD | W9 | W4 | 13 §19 |
| D8 | Phone agents through Paseo (not the OpenCode 2 Claude Agent provider) | Paseo | before S3-PASEO | W10 | W7 | ADR D8 |
| Q09-1…4 | Answers wait while the agent works; charts not mirrored; no word highlights in Changes; no "Add to message" | (a) for all | before S2-REQ | W11 | W8 | 09 §19 |
| P13-D4 | Two 30-minute measuring sessions on your phone (end of slice 2, the switch candidate), with a separate test copy | Yes | the week before S2 lands | W13 | W10 | 13 §19 |
| D15-4 | A second Linux inside the preview app on your phone for P2 and P3 | Yes only if "Disable child process restrictions" is on (read-only check) or while the Flutter app's phone server and agents are stopped, and ≥ 10 GB free; else videos | when S2 lands | W14 | W11 | §7; R15-20; 11 §7.1 |
| D16-4 | The phone's Linux sees only folders you opened as projects; a row turns the old behaviour back on | Yes | before S3-LINUXSEC | W15 | W11 | 16 §16 |
| 11 §25 + D16-3 | The 13 Android-platform questions; lock-screen answers need the phone unlocked | As 11 §25; yes | before S4-ATTNE | W17 | W11 | 11 §25; 16 §16 |
| D10-1…11 | Media choices (keep half downloads, no background dictation, F-Droid Lite, …) | As 10 §24 | before S5-MEDIALOGIC | W23 | W16 | 10 §24 |
| M-D1, M-D3…M-D8 + D15-6 | Migration choices (keep R1 for your phone only, cleanup timing, …) | As 05 §12; §17.2 | before S6-MIGRHARD | W29 | W20 | 05 §12 |
| R12-8, D12 (S02-6), D16-5, D16-6, D16-8, D15-3 | Licence wording for proot, talloc and shmem; no alpha library in the release and the CMP 1.13 stable rule; F-Droid route; supported versions; the GPL sources release; the staged rollout | As stated in each | before S6-RELEASE | W30 | W20 | 12 §13; ADR D12; 02 S02-6; 16 §16 |
| GO-1 | Put the candidate on GitHub `master` as one snapshot, tag it and let CI build the public draft | — (a GO) | the week before S6 lands | W31 | W22 | §11.0, §11.2 gate 3 |
| Stage 0 | "Switch went fine? yes/no" after two days | — | the week after S6 lands | W33 | W24 | §11.3 |
| GO-2, GO-3 | Publish the draft as a pre-release; a week later mark it latest | — (GOs) | +1 and +2 weeks | W34, W35 | W25, W26 | §11.4 |
| D11, D15-7 | Live backgrounds and scenes after the switch, or drop; the order of the phases after the switch | After; as §17.2 | after stage 2 | W36 | W27 | ADR D11 |
| MW (T14-4) | Start the widget tests | the week after the switch release, or earlier when the trigger fires | stage 2, or the trigger | W35 | W26 | 14 §5.2; C15-15 |
| D13, D14 | iOS prerequisites; desktop limits accepted | Decide when funded; accept | at M7 | — | — | ADR §12 |
| P13-D2, P13-D5 | Where the AI Team engine lives when it returns; battery temperature as a heat input | (a) inside the APK; after experiment E4 | at M8; after E4 | — | — | 13 §19 |

### 17.2 Decisions this section adds

| # | Decision | Default | If the owner says no |
|---|---|---|---|
| D15-1 | The switch date follows parity; a date never forces an unapproved cut. | Yes | The owner names a fixed date; §19 then lists what moves after it, for approval |
| D15-2 | The bridge release ships in W3, right after the freeze gate. | Yes | Ship it two weeks before the switch candidate (fewer people can self-update) |
| D15-3 | Staged rollout: your phone 2 days → pre-release 7 days → latest. | Yes | Change the stage lengths |
| D15-4 | A second Linux in the preview app on your phone for P2 and P3, only while the phone-wide child-process limit cannot bite (§17.1). | As §17.1 | Emulator videos only until the switch |
| D15-5 | Previews only at M1–M5 (five APKs), plus weekly images or videos. | Yes | Add a mid-slice APK when a chat feature lands |
| D15-6 | The R1 "go back" build exists for the owner lineage only. | Yes | Also build a public-lineage R1 through CI (owner GO each time) |
| D15-7 | After the switch: M7 clean-up, then M8 AI Team and Memory and M9 Linux desktop in parallel (M8 earlier if D6 is (b)), then M10 iOS when funded; MW as §17.1 says. | Yes | Reorder |
| D15-8 | (Merged into the 32-bit decision of §17.1, whose default follows ADR §9.9, 13 P13-D1, 16 D16-10 and 11 D11-14: no 32-bit at the switch.) | — | — |
| D15-9 | S0-NOTICE: a link-only Shorebird patch to 2.1.0+57 after the bridge is published. | Yes | People on 2.1.0+57 learn about the new version only from the release notes and the site |

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
| C15-9 | ADR §9.6 and 02 NG15 say no telemetry; the owner's 2026-11-01 decision (memory, rank 1 under 02 §0.1) says build a tracking core with BASIC always on and DETAILED opt-in. An earlier draft of this section also put the core in a new `:engine:analytics` module in slice 4, against 16 §5.1 and 12 W-20. | **Revised 2026-11-01:** the core is its own module `:engine:analytics` (03 S27, section 18), engine order #2, built in slice 1 by S1-ANALYTICS (a cloud session built it the same day), so every engine can emit BASIC events from day one; S4-ANALYTICS keeps the "What we collect" page, the Privacy rows and any sink; `sender=none` until D16-1 names a destination, and S-22's allowlist is unchanged until then. ADR §5.2 and §6.2 carry the module. | ADR §9.6 (per 16 CR16-1); 02 G7, NG15 |
| C15-10 | 11 §1.2 groups builtin-linux 29–32 in slice 3; the reply watch (32) is the S11-4 service in `:android:background` (slice 4). | BUILTIN-LINUX-32 lands in slice 4 with S4-BG. | — |
| C15-11 | 04 WP-04.9 lists E2E-02 for slice 2; scanning a QR needs the slice-3 scanner (10 MV-9). | E2E-02 is green in slice 3; its typed and code steps are tracked in slice 2. | 04 §16 |
| C15-12 | 07 §11 wants E2E-19 and E2E-20 green in slice 3; 11 §1.2 puts the Linux manager (40–48) in slices 3–5, and slice 3 is already the largest. | E2E-19 and E2E-20 move to slice 5 with S5-LINUXMGR. | 07 §11 |
| C15-13 | 02 §7.9 says every native build is ≥ 10000 with ranges handed out; 05 §9.1 steps main releases by 10. | Main flavor 10000–19999 (05's rule), preview flavor 20000+ (§2.6). | — |
| C15-14 | ADR §9.9 lists ABI splits arm64-v8a, x86_64 and universal; today's releases also ship `armeabi-v7a`. Sections 11 (D11-14), 13 (P13-D1), 16 (D16-10) and an earlier draft of this section answered differently. | One 32-bit decision (§17.1), asked in W2 with the download counts; the default follows the ADR (no 32-bit), with 16's 64-bit pre-check in the bridge (S0-BRIDGE). | — (the ADR stands) |
| C15-15 | 14 T14-4 starts the widget-test phase the week after the switch, earlier on a measured trigger; an earlier draft of this section waited for the owner to schedule it. | The owner is asked at stage 2 with the default start of T14-4, or earlier when the 14 §5.2 trigger fires; the zero-cost preparation (name index, candidates, trigger count) runs from S0. This matches the owner's "7400 for widgets can come later". | — |
| C15-16 | ADR §11 P3 asks for "no frame over budget in a release-build Macrobenchmark"; on the PC emulator only relative numbers mean anything, and Macrobenchmark errors there unless `suppressErrors=EMULATOR` is set (testing-perf §9). | P3 is judged relative to the S0-BASE Flutter baseline on the same AVD, with `suppressErrors=EMULATOR`; absolute numbers on the NX721J only with the owner's OK. | ADR §11 P3 |
| C15-17 | 02 §5.8 step 3 fast-forwards `master` to `native/main`, and 02 §7.2 creates `native/main` from the freeze tip; that history holds a 181 MB APK GitHub rejects, and GitHub `master` is a chain of snapshots. | Every push is a snapshot (§11.0); the switch and the stage-2 removal reach `master` as release snapshots with an owner GO; checks compare trees. Option at S0: root `native/main` on `snapshot/freeze-2026-11-01` with tree-sync commits from the freeze line. | 02 §5.8, §7.2; 16 §7.3 step 5 ("tag commit equals master" holds for the snapshot) |

---

## 19. The scope valve (only with the owner's OK)

If a re-plan projects the switch more than two weeks past the plan of record (§2.4 step 6), the coordinator offers these
moves to "after the switch", in this order, each with its row count from `scope.jsonl` and the weeks it saves, measured
by rerunning Appendix A with `--drop` (or with the unit's ad reduced) on the active calendar. Nothing moves without the
owner's yes (ADR §10.1; D15-1).

| Order | Move after the switch | Units removed from the critical load | Saves alone, A / B / C (Appendix A, `--eff 0.9`) |
|---|---|---|---|
| 1 | Offline demo, keyboard shortcuts help, command launcher extras | part of S5-SEARCH (4 → 2 ad) | under 1 week on every calendar |
| 2 | Linux services, vitals and backup (freeze-tip additions) | part of S5-LINUXMGR (9 → 5 ad) | under 1 week |
| 3 | BYO host | part of S5-INTEG (10 → 8 ad) | under 1 week |
| 4 | Paseo second-tier: sub-agents, background tasks, import, daemon update | S5-PASEOP1 (7 ad) | under 1 week |
| 5 | Codex direct connection | S5-CODEX (6 ad) | under 1 week |
| 6 | The natural (Kokoro) voice and the voice conversation loop | part of S5-READ (10 → 5 ad) | under 1 week |
| 7 | Connector card and connector search | part of S5-CARDS (13 → 10.5 ad) | under 1 week |

**What the valve really buys.** Items 1–7 as listed save **1 week** on A (S6 W32 → W31) and on B (W23 → W22), and
nothing measurable on C. Removing all seven units outright saves 2 weeks on A (→ W30) and 1 on B and C. Calendar A is
capacity-bound (about 27 ad per week of switch date), so any single item moves the date by a day or two at most. The
owner is told exactly that: the valve buys about 1–2 weeks. The levers that move the date by more are capacity (the
cloud lane: A → C saves 5 weeks; a second VPS: 9 weeks, if the PC can hold it) and the emulator hours of §2.5, which
decide whether the owner-phone week can follow the S6 week directly. Each moved item keeps its rows as `after-switch`
with the decision date, and its flows (E2E-24, E2E-29, parts of E2E-20 and E2E-30) leave the switch set.

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
  30, 34, 35, 37; Flutter: lib/ui/screens/chats_*, test/home_navigation_test.dart, test/first_run_*_test.dart;
  native/catalog/widget-index.tsv lines for these screens (a checklist of what the Flutter screen was tested for).
Write set: native/feature/{onboarding,chats}/**, native/ui/words/**/strings_{chats,onboarding}.xml,
  native/ui/routes/**/{ChatsRoutes,OnboardingRoutes}.kt, docs/qa/native/s2-chats-*/**
Single-owner modules you may NOT edit: :ui:kit*, :ui:shell, :ui:routes (other files), :engine:*, :core:*.
Owner rules that apply word for word: no bottom bar on phones (2026-10-20); obvious options as visible first rows
  (New project / Search / Opened before); actions name their target; no new bar or row levels; plain words.
Approved designs this must not replace: the Conversations home of Flutter 2.1.0+57 (parity, NG10).
Checks: native/tool/t0.sh :feature:chats while editing; native/tool/t1.sh --base native/main when ready;
  native/tool/check_write_set.sh native/briefs/s2-chats.md before reporting.
Device work: emulator booking in native/EMULATOR.md (90 min max), build numbers 20140-20149.
Push: never (PC slot). A cloud brief instead adds the §2.1.1 steps (SDK install, cloud/<unit> branch, scan, noreply author).
Evidence: docs/qa/native/s2-chats-<date>/README.md (with "Widget-test candidates"); images per visual pass to
  launch-footage/progress/s2-chats/.
Quiet hours: lifted 2026-11-01; if reinstated, nothing heavy on the PC 00:00-02:00.
Report: 02 §7.14 format.
```

### 20.2 The agent's start checklist (paste into every brief's first reply)

1. I have read the brief, `native/AGENTS.md` and the contract part it names.
2. My worktree is sparse and on my branch; `git status` is clean.
3. My rows are listed (§3.4) and fail for the reason they describe.
4. I know my write set and the single-owner modules I may not touch.
5. I will format only the files I change, never push (a cloud session pushes only `cloud/<unit>` after the scan), and
   never run `./gradlew build`.
6. I will book the emulator before any device step and use only my build-number block.
7. My report will say what is **not** proven.

---

## 21. Acceptance checks for this section

| # | Check | Expected |
|---|---|---|
| 1 | Every map feature id appears once in §13 | 700 features; per-map totals equal the map counts (46, 55, 63, 64, 58, 112, 81, 50, 65, 41, 65) |
| 2 | Every switch flow appears once in §14.2 | 37 |
| 3 | `python3 -I native/tool/plan/plan_calendar.py` | `S0:W3 S1:W7 S2:W14 S3:W19 S4:W24 S5:W31 S6:W32` |
| 4 | `python3 -I native/tool/plan/plan_calendar.py --slots-from-w3 9` | `S0:W3 S1:W7 S2:W11 S3:W15 S4:W18 S5:W22 S6:W23` |
| 4b | `python3 -I native/tool/plan/plan_calendar.py --cloud 3` | `S0:W3 S1:W7 S2:W11 S3:W17 S4:W20 S5:W26 S6:W27` |
| 5 | `python3 -I native/tool/plan/seed_scope.py native/catalog/behaviors.jsonl` | the §14.1 bucket counts (S1 140, S2 136, S3 357, S5 404, AFTER 197, DROP 31) |
| 6 | Unit-table ad sums equal the `U` list of Appendix A, and the "A weeks" column equals `--units` | S0 50, S1 108, S2 132, S3 169, S4 122, S5 169, S6 42 |
| 7 | Every unit row has a finish line, non-goal, model, write set, dependency and ad | yes |
| 8 | Every owner decision of sections 01–16 has a default and an ask anchor, and `--asks` prints its week on both calendars | yes (§17.1) |
| 9 | Every work package of sections 08, 11, 12, 13 and 16 that lands before the switch has an owning unit here or is named as lane work | yes (§4–§10; the new units S1-PACER, S1-SECGATES, S1-STARTUP, S2-FIELD, S2-BACKOFF, S2-EGRESS, S3-PINS, S3-LINUXSEC, S3-COMPMOTION, S4-RESTORE, S5-TERMFORK, S5-REPRO, S5-DOCS) |
| 10 | Every 02 §3.5 "Switch" item and every 02 §3.7 rule has an owning unit | yes (§13.13) |

---

## 22. Sources

- Plan: `01-architecture-decision.md` (§1, §4, §5.2, §6.2, §7.6, §8, §9.4–§9.9, §10, §11, §12),
  `02-principles-scope.md` (§2–§11), `03-architecture-modules.md` (§1.2, §22–§24), `04-protocols-networking.md`
  (§16–§19), `05-data-migration.md` (§7, §9–§12), `06-ui-kit-design-system.md` (§18–§20), `07-screens-navigation.md`
  (§5, §7, §11–§14), `08-motion-animation.md` (§11–§12), `09-chat-streaming-genui.md` (§17–§19), `10-media-voice.md`
  (§1, §21, §22, §24), `11-android-platform-native.md` (§0–§1, §4.3, §5, §7, §11, §15, §21, §25),
  `12-package-registry.md` (§13–§14, W-14, W-20), `13-performance-optimization.md` (WP-13.x, §19),
  `14-testing-parity.md` (§2.11, §4.6, §4.8, §5, §13), `16-security-privacy-release.md` (§5–§8, §12, §16).
- Design: `../design/proposal-A-shared-ui.md` (§15, §18), `../design/proposal-C-android-first-kmp-ready.md` (§18).
- Maps: all eleven feature lists in `../map/*.md`; `packages-build.md` §0 items 6 and 10, open question 4.
- Catalog: `../catalog/BEHAVIORS.md` (counts), `../catalog/behaviors.jsonl` (bucket estimate, 2026-11-01),
  `../catalog/e2e-flows.md` (flow index, AVD matrix, fixtures).
- Research: `kmp-cmp-status.md` (§0, §1, §12–§13), `android-surfaces.md` (§0 items 8–9, §14–§16), `testing-perf.md`
  (§0, §9).
- App-updates spec: `/home/eslam/Storage2/wt/app-updates-2026-10-31/SPEC.md` (the `releases/latest` source, asset
  names, verify-before-install).
- Owner memory: `dev-freeze-native-rewrite-2026-11-01`, `freeze-owner-decisions-2026-11-01`, `pc-capacity-ceiling`,
  `build-vps`, `subagent-model-choice`, `codex-backend-claude-frontend`, `no-heavy-workflows`, `short-replies-rule`,
  `live-video-proof`, `show-samples-rule`, `one-screen-per-image`, `quiet-hours-vps-only`, `apk-build-numbers`,
  `release-2.1-prep`, `no-users-yet`, `phantom-process-limit`, `stabilize-before-shipping`,
  `test-on-emulator-not-phone`, `shorebird-is-mandatory`, `judge-visuals-hard`, `push-freely-snapshot-recipe`,
  `claude-code-cloud-howto`, `public-commit-email`, `no-more-workflows-2026-11-01`, `role-cycle-ai-team`,
  `gemma-assistant`, `app-updates-two-layers`, `composer-green-glow-liked`, `change-class-values`.
- Checked today [V], 2026-11-01:
  - GitHub REST docs, "Get the latest release": "The latest release is the most recent non-prerelease, non-draft
    release, sorted by the created_at attribute." (https://docs.github.com/en/rest/releases/releases)
  - Android developer verification: September 30, 2026 for participating stores in Brazil, Indonesia, Singapore and
    Thailand; "expanded globally for all apps on certified Android devices in 2027"
    (https://developer.android.com/developer-verification).
  - Compose Multiplatform releases: 1.13.0-beta01 (23 Oct) is the newest 1.13 build; 1.12.2 (15 Oct) is "Latest"
    (https://github.com/JetBrains/compose-multiplatform/releases).
  - Google's open-source app registration guide for developer verification (updated 2026-10-23):
    https://developer.android.com/developer-verification/guides/open-source-app-registration.
  - Download counts of the published APKs (`gh release view v2.1.0+57` and `v2.0.0+56` on
    Eslamasabry/opencode-mobile-next): 2.1.0+57 arm64 17, armeabi-v7a 3, universal 9; 2.0.0+56 arm64 81, armeabi-v7a 7,
    universal 24.
  - Repository facts at the freeze tip `int/freeze-2026-11-01`: `e410b6d71` is an ancestor and the tip is not an
    ancestor of `mobile-next/master`; `android-release.yml`, `desktop-linux.yml` and `byo-host-bundle.yml` trigger on
    every `v*` tag; `lib/update/app_release.dart` `apkFor` falls back to the first `.apk` not matching
    `-[a-z0-9_]+-v\d+a\.apk$`; `lib/update/desktop_release_check.dart` counts pre-releases and takes the highest build.
  - Appendix A and B outputs quoted in §1, §2.3, §14.1, §17.1, §19 and §21 (run 2026-11-01).
  - Week dates: 2026-11-02 is a Monday (computed).
- The owner's words relayed for this run: "7400 for widgets can come later."

---

## Appendix A: `native/tool/plan/plan_calendar.py`

The coordinator commits this file in S0 and edits only the `U`, `CLOUD` and `ASKS` lists (when units or decisions
change) and nothing else. `X@k` means "X has run k agent-days and its contract is usable"; `S5-*` means every unit of
slice 5. Run with `python3 -I`. Lane units (§4.2 and the lane paragraphs) are not in `U`; a lane dependency such as
S0-STREAM's on S0-BASE is met by the lane's own weeks.

```python
#!/usr/bin/env python3
"""plan_calendar.py: slot-limited list scheduler for the native rewrite (plan section 15, Appendix A).
Usage: python3 -I native/tool/plan/plan_calendar.py [--slots 6] [--slots-from-w3 9] [--cloud 0] [--eff 0.9]
       [--drop U1,U2] [--units] [--asks]
--eff   = planned agent-days per slot-day (0.9 default; the M0 re-plan sets 0.9 x measured P10 factor).
--cloud = Claude Code cloud sessions from W3 that take only units in CLOUD (emulator-free commonMain work).
--drop  = units removed (the scope valve, section 19).
Prints the week (W1 = Mon 2026-11-02) in which each slice's last unit finishes; --units prints every unit;
--asks prints the owner-decision ask weeks for this calendar (section 17.1)."""
import argparse
U = [
('S0-SKEL',0,6,[]),('S0-KIT',0,10,['S0-SKEL@2']),('S0-CORE',0,6,['S0-SKEL@2']),('S0-NAV',0,5,['S0-SKEL@2']),
('S0-MIGR',0,5,['S0-SKEL@2']),('S0-STREAM',0,4,['S0-SKEL@2']),('S0-NETP',0,5,[]),
('S0-WORDS',0,3,['S0-SKEL']),('S0-GATES',0,4,['S0-SKEL']),('S0-ROUTES',0,2,['S0-SKEL']),
('S1-DATA',1,11,['S0-CORE']),('S1-SECNET',1,7,['S0-CORE']),('S1-NET',1,6,['S0-CORE','S0-NETP']),('S1-PACER',1,3,['S0-CORE']),
('S1-OC2',1,12,['S1-NET']),('S1-CONN',1,8,['S1-NET','S1-PACER']),('S1-ENG',1,11,['S1-DATA']),('S1-ANALYTICS',1,3,['S0-SKEL']),
('S1-KITA1',1,10,['S0-KIT']),('S1-KITA2',1,10,['S0-KIT']),('S1-WORDS',1,8,['S0-WORDS']),
('S1-SHELL',1,7,['S0-ROUTES','S0-KIT']),('S1-LINUX',1,5,['S0-SKEL']),('S1-PLAT0',1,3,['S0-CORE']),
('S1-SECGATES',1,4,['S0-GATES']),('S1-STARTUP',1,3,['S1-SHELL']),
('S2-ENTRY',2,5,['S0-ROUTES','S0-CORE']),('S2-MODEL',2,3,['S0-CORE']),('S2-MD',2,5,['S2-MODEL']),
('S2-TX',2,12,['S2-MODEL','S2-MD@3']),('S2-DLV',2,10,['S2-TX@4','S1-DATA']),('S2-REQ',2,5,['S2-TX']),
('S2-KCA',2,13,['S1-KITA1','S2-MD']),('S2-KCB',2,10,['S1-KITA2']),('S2-KITB',2,13,['S1-KITA1']),
('S2-CHAT',2,12,['S2-TX@6','S2-DLV@3','S2-KCA@6','S2-KCB@5']),('S2-CHATS',2,10,['S1-CONN','S1-SHELL','S2-KITB@5']),
('S2-SERVERS',2,8,['S1-CONN','S1-DATA','S1-KITA2']),('S2-PLATFORM',2,4,['S2-ENTRY']),
('S2-MIGRRUN',2,8,['S1-DATA']),('S2-GATEUI',2,4,['S2-MIGRRUN@3','S1-SHELL']),('S2-BLOB',2,2,['S1-DATA']),
('S2-FIELD',2,3,['S1-PLAT0']),('S2-BACKOFF',2,3,['S1-CONN']),('S2-BENCH',2,2,['S2-CHAT']),
('S3-PASEO',3,10,['S1-NET']),('S3-RTSPLIT',3,10,['S1-LINUX']),('S3-RTENG',3,13,['S1-DATA']),
('S3-RTWIRE',3,8,['S3-RTSPLIT','S3-RTENG@8']),('S3-AGENTSE',3,11,['S3-PASEO@5','S3-RTENG@6']),('S3-AGENTHOST',3,5,['S3-RTSPLIT']),
('S3-PINS',3,4,['S3-AGENTSE@5']),('S3-LINUXSEC',3,3,['S3-RTWIRE']),
('S3-REQFORMS',3,7,['S2-REQ']),('S3-COMPE',3,7,['S2-DLV']),('S3-COMPUI',3,10,['S3-COMPE@4','S2-KCB']),
('S3-MEDIAATT',3,15,['S3-COMPE@3']),('S3-KCCONTENT',3,18,['S1-KITA2']),('S3-WORK',3,5,['S2-KCA']),
('S3-COMPMOTION',3,11,['S2-KCB','S3-WORK']),
('S3-TERMMIN',3,4,['S3-KCCONTENT@8','S1-LINUX']),('S3-PHONEUI',3,12,['S3-RTENG@6','S2-KITB']),('S3-AGENTSUI',3,10,['S3-AGENTSE@5','S2-KITB']),
('S3-MEDIAPROOF',3,4,['S0-SKEL']),('S3-BENCH',3,2,['S3-AGENTSUI']),
('S4-ATTNE',4,11,['S1-CONN','S2-TX@8']),('S4-BG',4,14,['S4-ATTNE@5','S3-RTWIRE']),('S4-SCHED',4,11,['S2-DLV','S1-CONN']),
('S4-WIDGETS',4,13,['S4-ATTNE@6']),('S4-RESTORE',4,4,['S2-ENTRY','S4-BG@5','S4-WIDGETS@5','S4-SCHED@5']),
('S4-SETTINGS',4,12,['S1-ENG','S2-KITB']),('S4-APPEAR',4,20,['S1-KITA1']),
('S4-PANELS',4,4,['S4-WIDGETS@6','S4-APPEAR@6']),('S4-PAGES',4,10,['S2-CHAT@6']),('S4-ABOUT',4,6,['S1-ENG']),
('S4-UPDATER',4,9,['S1-NET','S1-ENG']),('S4-SCHEDUI',4,4,['S4-SCHED@6','S4-APPEAR@6']),
('S4-ANALYTICS',4,2,['S1-ANALYTICS','S4-SETTINGS@6']),('S4-BENCH',4,2,['S4-BG']),
('S5-CODEX',5,6,['S1-NET']),('S5-OC2MORE',5,7,['S1-OC2']),('S5-PROJE',5,10,['S1-CONN']),('S5-PROJUI',5,11,['S5-PROJE@5','S3-KCCONTENT']),
('S5-TERMFORK',5,3,['S3-TERMMIN']),('S5-TERMINAL',5,7,['S5-TERMFORK','S5-OC2MORE@4']),('S5-KC2',5,13,['S3-KCCONTENT']),
('S5-MEDIALOGIC',5,10,['S0-CORE']),
('S5-VOICE',5,12,['S5-MEDIALOGIC@5','S3-MEDIAPROOF']),('S5-MODELS',5,10,['S5-MEDIALOGIC@5']),('S5-READ',5,10,['S5-VOICE@6','S5-MODELS@5']),
('S5-VIEWER',5,10,['S3-MEDIAATT','S5-KC2@5']),('S5-WIFI',5,6,['S5-MEDIALOGIC@5']),('S5-CARDS',5,13,['S3-REQFORMS','S3-AGENTSE']),
('S5-INTEG',5,10,['S5-OC2MORE@3']),('S5-LINUXMGR',5,9,['S3-RTWIRE']),('S5-PASEOP1',5,7,['S3-AGENTSE']),
('S5-SEARCH',5,4,['S4-SETTINGS']),('S5-TERMUXOUT',5,3,['S3-RTWIRE']),('S5-REPRO',5,3,['S0-GATES']),('S5-DOCS',5,3,['S4-ABOUT']),
('S5-BENCH',5,2,['S5-VOICE']),
('S6-MIGRHARD',6,9,['S2-MIGRRUN','S3-RTWIRE','S4-BG','S4-RESTORE','S4-WIDGETS','S4-SCHED','S5-MODELS']),
('S6-R1',6,2,['S6-MIGRHARD@4']),('S6-COMPAT',6,3,['S6-MIGRHARD@4']),
('S6-PERF',6,6,['S5-READ','S5-PROJUI']),('S6-AUDIT',6,6,['S4-APPEAR','S5-PROJUI','S5-VIEWER']),('S6-A11Y',6,3,['S5-PROJUI']),
('S6-RELEASE',6,8,['S4-UPDATER']),('S6-SWEEP',6,5,['S5-*']),
]
# Emulator-free units whose write set is commonMain core/protocol/engine code (T0/T1 on desktopTest; no device step).
CLOUD = {'S1-SECNET','S1-NET','S1-PACER','S1-CONN','S1-ENG','S2-MODEL','S2-MD','S2-DLV','S2-BLOB','S2-BACKOFF',
         'S3-RTENG','S3-AGENTSE','S3-COMPE','S3-PINS','S4-ATTNE','S5-OC2MORE','S5-PROJE','S5-MEDIALOGIC','S5-REPRO'}
# Owner-decision ask weeks (section 17.1). 'Wn' fixed; 'before:X' the week before unit X starts;
# 'start:X' the week X starts; 'land:Sn' the week slice n lands; '+k' adds k weeks.
ASKS = [
('D15 capacity, D1, D6 AI Team, D16-9 key custody, T14-1..6','W1'),
('D4 bridge + D15-2, 32-bit (D15-8 = P13-D1 = D16-10 = D11-14), M-D2, D18/D16-7, ADR-D16 branches, D15-9 patch notice','W2'),
('D16-1 analytics destination (= R12-1 = S02-5), D16-2, D9 Room','W3'),
('D10 frost','before:S2-KCB'),('O-07-1..4','before:S2-CHATS'),('D8-1..4 (08 motion)','before:S2-KITB'),
('P13-D3 profileable + trace recorder','before:S2-FIELD'),('Q09-1..4','before:S2-REQ'),
('P13-D4 phone measuring sessions','land:S2+-1'),('D15-4 preview Linux on the phone','land:S2'),
('D8 phone agents path','before:S3-PASEO'),('D16-4 Linux sees opened folders only','before:S3-LINUXSEC'),
('11 section 25 (D11-1..13), D16-3 lock-screen unlock','before:S4-ATTNE'),('D10-1..11 media','before:S5-MEDIALOGIC'),
('M-D1, M-D3..M-D8, D15-6','before:S6-MIGRHARD'),
('R12-8, D12/S02-6, D16-5, D16-6, D16-8, D15-3','before:S6-RELEASE'),
('GO-1 candidate snapshot + CI draft','land:S6+-1'),('owner phone test (stage 0)','land:S6+1'),
('GO-2 publish pre-release','land:S6+2'),('GO-3 mark latest; MW widget tests asked (T14-4)','land:S6+3'),
('D11 live backgrounds, D15-7','land:S6+4'),
]
def run(slots_by_week, eff, cloud=0, drop=()):
    units = [(u[0], u[1], 0.0 if u[0] in drop else u[2], u[3]) for u in U]
    ids = {u[0]: u for u in units}; dur = {u[0]: u[2] / eff for u in units}
    order = [u[0] for u in units]; remaining = list(order)
    done, start, prog, running, onc, day = {}, {}, {}, [], set(), 0
    def deps(uid):
        out = []
        for d in ids[uid][3]:
            if d.endswith('*'): out += [x for x in order if x.startswith(d[:-1])]
            else: out.append(d)
        return out
    def ready(uid):
        for d in deps(uid):
            if '@' in d:                      # 'X@k' = X has run k agent-days (its contract is usable)
                dd, k = d.split('@')
                if dd not in start or prog[dd] < float(k): return False
            elif d not in done: return False
        return True
    while remaining or running:
        for u in [u for u in remaining if dur[u] == 0 and ready(u)]:
            remaining.remove(u); start[u] = day; prog[u] = float('inf'); done[u] = day
        cand = sorted([u for u in remaining if ready(u)], key=lambda u: (ids[u][1], order.index(u)))
        csl = cloud if day // 5 >= 2 else 0
        for u in [c for c in cand if c in CLOUD]:
            if len(onc) >= csl: break
            cand.remove(u); remaining.remove(u); running.append(u); onc.add(u); start[u] = day; prog[u] = 0
        while len(running) - len(onc) < slots_by_week(day // 5) and cand:
            u = cand.pop(0); remaining.remove(u); running.append(u); start[u] = day; prog[u] = 0
        for u in list(running):
            prog[u] += 1
            if prog[u] >= dur[u] - 1e-9: done[u] = day; running.remove(u); onc.discard(u)
        day += 1
        if day > 1000: raise SystemExit('dependency loop')
    return start, done, units
def week(day): return day // 5 + 1
def ask_week(anchor, s, d, last):
    base, _, off = anchor.partition('+'); off = int(off or 0)
    kind, _, x = base.partition(':')
    if kind.startswith('W'): w = int(kind[1:])
    elif kind == 'before': w = week(s[x]) - 1
    elif kind == 'start': w = week(s[x])
    else: w = week(last[int(x[1:])])
    return max(1, w + off)
if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--slots', type=int, default=6); p.add_argument('--slots-from-w3', type=int, default=None)
    p.add_argument('--cloud', type=int, default=0); p.add_argument('--eff', type=float, default=0.9)
    p.add_argument('--drop', default=''); p.add_argument('--units', action='store_true')
    p.add_argument('--asks', action='store_true')
    a = p.parse_args()
    later = a.slots_from_w3 or a.slots
    s, d, units = run(lambda w: a.slots if w < 2 else later, a.eff, a.cloud, set(filter(None, a.drop.split(','))))
    last = {}
    for u in units: last[u[1]] = max(last.get(u[1], 0), d[u[0]])
    print(' '.join('S%d:W%d' % (k, week(v)) for k, v in sorted(last.items())))
    if a.units:
        for u in units: print('%-15s W%-2d..W%-2d %5.1f ad' % (u[0], week(s[u[0]]), week(d[u[0]]), u[2]))
    if a.asks:
        for k, anchor in ASKS: print('W%-3d %s' % (ask_week(anchor, s, d, last), k))
```

The scheduler gives priority to the lowest slice, then to the order of the list. Cloud sessions take only `CLOUD` units,
from W3 (after gate G-A). It does not model lanes, the emulator or holidays; §2.5 budgets the emulator, §2.4 step 4
checks it, and the coordinator writes any manual shift next to the dates in §1.1. Like any list scheduler it has small
anomalies: adding a dependency can move a slice's week by one in either direction (S0-KIT's new dependency on S0-SKEL@2
alone moves S2 on calendar A from W14 to W13), so only the S6 week is quoted as a commitment.

## Appendix B: `native/tool/plan/seed_scope.py` (first-pass slice for every catalog row)

Run once in S0-SCOPE: `python3 -I native/tool/plan/seed_scope.py native/catalog/behaviors.jsonl`. It prints the bucket
counts of §14.1; with `--write` (to be added by S0-SCOPE, 10 lines) it emits `{"id","scope","slice","module"}` lines
that seed `scope.jsonl`. The FORCE table wins first: it holds the rows 02 §3.3 says enter the switch on day 1 (the
built-in Linux rows filed under `termux_*`, and the deletion and redaction rows of rule 3, B-STATE-058 included) and the
S2-ENTRY re-scope of B-NAV-061 and B-ANDR-071, each with slice and module. For every other row the rules read the first
Flutter source file; the first matching rule wins.

```python
import json, re, sys, collections
# FORCE wins over RULES: 02 §3.3 "rows that stay" (rule 3 included) and the S2-ENTRY re-scope against N4-O4.
# id: (slice, module). Rows not listed here follow RULES; S0-SCOPE still reads every row by its text (02 §10 item 4).
FORCE = {
 **{'B-LINUX-%03d' % n: ('S3', ':engine:runtime') for n in (227, 228, 229, 230, 231, 232, 239, 240, 241, 242, 243, 244, 245, 246)},
 'B-SEC-170': ('S3', ':engine:runtime'), 'B-LINUX-247': ('S5', ':engine:runtime'), 'B-LINUX-248': ('S5', ':engine:runtime'),
 **{i: ('S1', ':engine:profiles') for i in ('B-STATE-020', 'B-STATE-058', 'B-STATE-145', 'B-SEC-075', 'B-SEC-176',
                                             'B-AGENT-193', 'B-AGENT-301', 'B-AGENT-302')},
 'B-SEC-063': ('S2', ':android:migration'),
 'B-NAV-061': ('S2', ':engine:entry'), 'B-ANDR-071': ('S2', ':engine:entry'),  # amended: address-link clauses dropped (N4-O4)
 # 17 §6 (gap closure, 2026-11-01): wrong-slice rows of the agents, project and integrations engines
 **{'B-AGENT-%03d' % n: ('S3', ':engine:agents') for n in (9, 10, 11, 111, 112, 113)},
 **{'B-AGENT-%03d' % n: ('S5', ':engine:integrations') for n in (230, 233, 234, 235, 236)},
 'B-AGENT-241': ('S5', ':protocol:codex'),
 'B-CHAT-013': ('S2', ':engine:agents'), 'B-CHAT-014': ('S2', ':engine:agents'), 'B-STATE-029': ('S2', ':engine:agents'),
 # S02-8 / N17-D4: "While you were away" recording stops at the switch
 **{i: ('AFTER', '-') for i in ('B-AGENT-030', 'B-STATE-006', 'B-STATE-007', 'B-STATE-155', 'B-NAV-082', 'B-SEC-015')},
 # NG8 (Termux-hosted Claude Code) and N17-D1 (no agent browser); 120, 124, 125 stay S3 amended (17 §1.14)
 **{'B-AGENT-%03d' % n: ('DROP', '-') for n in (121, 122, 123, 126, 127, 128, 129, 152)}, 'B-SEC-112': ('DROP', '-'),
 # the 15 rows the rules leave MANUAL (each read by its text)
 'B-NAV-047': ('S5', ':feature:settings'),     # server sign-in words, accounts, unfinished sign-ins, tools, plugins
 'B-NAV-050': ('S4', ':feature:search'),       # "background checks" search lands on each server's checks; hub Changes count
 'B-NAV-051': ('S2', ':feature:chat'),         # session menu Go to / Do by capability (07 C06)
 'B-NAV-052': ('S3', ':feature:chat-composer'),# rows point with one receipt; Saved prompts not tied to one server
 'B-CHAT-096': ('S2', ':engine:transcript'),   # linear fragment merge (09 §15.2 HistoryMergeTest)
 'B-AGENT-212': ('S5', ':engine:integrations'),# MCP catalogue → add form (17 §3.2)
 'B-AGENT-213': ('AFTER', '-'),                # Team worker state (D6)
 'B-AGENT-243': ('S3', ':engine:transcript'),  # a person-stopped task is never "Failed"
 'B-SEC-174': ('S2', ':android:platform'),     # Tailscale setup (04 §15.3 TailscaleSetupTest)
 'B-LINUX-014': ('S3', ':engine:runtime'),     # Add tools job restart rules (setup engine)
 'B-LINUX-148': ('S5', ':engine:project'), 'B-LINUX-149': ('S5', ':engine:project'), 'B-LINUX-150': ('S5', ':engine:project'),
 'B-MEDIA-010': ('S5', ':engine:media'),       # 30 s dictation chunks (10)
 'B-L10N-015': ('S5', ':ui:words'),            # worktree copy never names OpenCode
 # settings facade and report store are built by S1-ENG; the tip store by S2-TEACH (17 §4)
 **{'B-STATE-%03d' % n: ('S1', ':engine:settings') for n in (5, 123, 124, 125, 126, 127, 128)},
 **{'B-STATE-%03d' % n: ('S1', ':engine:diagnostics') for n in range(70, 81)}, 'B-STATE-142': ('S2', ':engine:settings'),
 # repository gates (BUILD rows): 03 §19.6, 14 §9.3
 **{'B-BUILD-%03d' % n: ('S0', ':quality') for n in (1, 23, 31, 33, 34)}, 'B-BUILD-005': ('S5', ':quality'),
 'B-BUILD-027': ('S2', ':engine:diagnostics'), 'B-BUILD-029': ('S6', 'native/tool/release'),
 **{'B-BUILD-%03d' % n: ('S5', ':engine:project') for n in range(13, 22)},
}
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
    if row['id'] in FORCE: return FORCE[row['id']][0]
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

Known limits: a row whose first source is a shared test file can land in the wrong slice, and file names mislead (02
§3.3). So S0-SCOPE reads every row by its text, as 02 §10 item 4 requires, not only the AFTER, DROP and MANUAL rows; the
seed only saves typing.

---

## Review notes (2026-11-01 revision)

Every issue was checked against the sibling sections, owner memory, the freeze-tip repository and GitHub before editing.
All were real; none was rejected outright. Partly rejected or changed claims are marked **partly**.

**Changed**

- **GitHub path (both blockers).** New §11.0: every push is a snapshot (`git commit-tree <tree> -p mobile-next/master`);
  gates compare **trees**, not commits; the switch and the stage-2 removal reach `master` as owner-approved release
  snapshots; "fast-forward master" is gone (§11.6, C15-17). Verified: `e410b6d71` is an ancestor of the freeze tip, and
  the freeze tip is not an ancestor of `mobile-next/master`.
- **Release channels.** §11.4 table: S0-BRIDGE limits the three Flutter tag workflows to `v2.*` and makes the desktop
  check ignore releases without a Linux asset; desktop security fixes use `make_latest=false`; `make_latest` is set on
  every release; S6-RELEASE owns `.github/workflows/*`, `native-quality.yml`, and `--prerelease`/`promote`/`demote`.
  **Partly:** a tag on a `[skip ci]` snapshot starts no workflow at all, so the collision needs a dispatch or a
  non-skip tag; the fix stands because that is fragile.
- **Public lineage.** New gate 3 (GO-1): the candidate's CI draft is built before stage 0, the public-lineage J1–J8
  legs run on it, and stage 1 publishes that same draft. **Partly:** the legs run in the gate, not in S6-MIGRHARD,
  so the unit can finish without an owner GO. S0-MIGR now installs over a local-key build of the 2.1.0+57 tree.
- **Missing owners.** Added S0-BRIDGE items (Gemma hidden, exact universal fallback, 64-bit pre-check); S4-ABOUT
  (star, share, What's new); S3-PINS (layer-2 pins); §13.13 (every 02 §3.5 "Switch" item and 02 §3.7 rule with its
  unit); S1-DATA (deferred-feature shapes), S3-RTWIRE (stop by exact PID), S2-GATEUI (the one notice), S4-SETTINGS
  and S5-LINUXMGR (the Gemma folder with Delete); S4-RESTORE (`SystemEventRestorer`, W11-14a; **partly**: a separate
  slice-4 unit, not S2-ENTRY, because 11 builds it after the surfaces exist); S11-12 and WP-16.8 in S4-BG; the five
  retired widget providers and the 16 moved platform Kotlin tests in S4-WIDGETS, S4-SCHED and S4-BG (**partly**:
  `ServiceDiagnosticsTest` is a runtime test that S1-LINUX already moves); the K4 pickers, wheel, panel parts and
  arrival in S4-APPEAR, with S4-SCHEDUI and S4-PANELS depending on it; S3-COMPMOTION for 08-M5 (**partly**: it reads
  only `oc.effectsActivityGlow` and `oc.effectsGlowStyle`; 05, 06 and 08 mark `oc.effectsGlowColours` and
  `oc.effectsGlowSpeed` as ignored and deleted at migration); S1-PACER, S1-STARTUP, S2-FIELD, S2-BACKOFF (13),
  S1-SECGATES, S2-EGRESS, S3-LINUXSEC, S5-REPRO, S5-DOCS (16; S2-EGRESS stays a test-writer lane unit as 16 assigns
  it), S5-TERMFORK (12 W-14).
- **Analytics.** (Superseded 2026-11-01: the core is `:engine:analytics`, unit S1-ANALYTICS, section 18.) Earlier: the
  core moved to `:engine:diagnostics` in S1-ENG (16 §5, 12 W-20); S4-ANALYTICS shrank to the sender interface and
  Privacy rows; S1-CONN, S2-ENTRY, S2-MIGRRUN and S4-UPDATER emit their events; C15-9 rewritten.
- **S4-UPDATER** now carries 16 §8 in full (N16-D8 feed state, B-SEC-189/190, P16-4, CR16-5 downloader, asset choice)
  and is split Codex + Sonnet.
- **R15-02** fallback replaced with 02 S02-6's rule; CMP 1.12.2 is a fallback only at the P1 verdict.
- **Scope rows.** Appendix B gained a FORCE table: 26 of the 02 §3.3 "stay" rows (25 plus the "for example"
  B-STATE-058) had fallen into DROP or AFTER; §14.1, §1.3, the slice headers and check 5 use the new counts (switch
  1,287 + 15 MANUAL), and the 02 §3.3 figure is now 1,336. S0-SCOPE reads every row (3 → 7 ad, the widget index included)
  and appends the sibling-defined rows. B-NAV-061 and B-ANDR-071 stay in S2-ENTRY, **re-scoped** against N4-O4 (07
  §7.6 keeps plain session links). **Partly:** 04 and 16 do not collide on B-PROTO-128/139; 16 cites 04's rows with
  the same meaning.
- **Capacity and calendars.** New units raised the builder total from 725 to 792 ad; Appendix A was rerun (A: S6
  W32, owner phone W33; B: W23, W24; new calendar C with 3 cloud sessions: W27, W28); every "A weeks" cell now equals
  `--units`. §1.2 quotes the script (12 slots → W19, 15 → W16, floor W15; **partly**: the issue's W17/W15 were for
  the old unit list). §2.1 adds the cloud lane and its brief steps (SDK install, 2-minute timeout, `cloud/<unit>`,
  scan, noreply author, `rebase --onto`), gate G-A (Astra review before fan-out), and an M0 measurement of the PC
  session cap. The phone test now follows the S6 week (§2.3 and §1.1 disagreed before).
- **Emulator.** A 23:00–08:00 night block, a switch-window demand table, a weekly sweep matrix with `OCN_T` landscape
  and 200 % text, the `OCN_36` and `OCN_26` smoke in gate 2, and an emulator-hours step in the re-plan.
- **Decisions.** §17.1 now covers 01–16 (D16-1…10, P13-D1…5, T14-1…6, D15-1…9, S02-7/8), each anchored to a unit or
  week and printed for both calendars by `--asks`; M-D2 and D18 move to W2, D6 becomes a real W1 decision with the AI
  Team gap in plain words, the 32-bit answers of 11, 13, 15 and 16 merge into one W2 decision whose default follows
  the ADR (download counts verified with `gh`), D15-4 now guards the phone-wide 32-process limit (R15-20), and the
  ADR's in-flight-branches decision is "ADR-D16", decided at the freeze gate (W2).
- **Smaller fixes.** Bridge versionCode = max(phone, emulator, hosted) + 10 (≥ 4521); Appendix A dependencies of
  S6-MIGRHARD, S6-SWEEP and S0-KIT (**partly**: the S0-KIT dependency alone does move calendar A's S2 week, W14 → W13,
  a list-scheduler anomaly; the S6 week does not change); split `desktopApp` write sets; S0-STREAM judges P3 relative
  to the Flutter baseline (C15-16); D18 adds Google's open-source registration route [V]; S0-TOPUP resized to 4 + 4 ad;
  optional S0-NOTICE (Shorebird link-only patch to 2.1.0+57, D15-9); MW start rule reconciled with T14-4 (C15-15)
  with the index, candidates and trigger count running from S0; §19 states the measured valve (about 1–2 weeks).

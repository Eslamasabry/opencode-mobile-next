# OpenCode Mobile native rewrite: the runbook

**Read this first. One file, end to end:** what we are building, where everything is, the rules, how the work loop runs, the exact commands, where things stand, and what to do next. Written 2026-11-01 by the coordinator (Claude). A person or an agent should be able to continue from here without asking.

---

## 1. What and why, in plain words

- **The product:** OpenCode Mobile, an Android app (today Flutter, about 479k lines of Dart) that chats with coding agents. That covers OpenCode servers, and Claude Code, Codex and others via Paseo. It runs a built-in Ubuntu Linux on the phone. It also does voice, widgets, schedules, notifications, file viewing and sharing, and more.
- **The decision (owner, 2026-11-01):** after trying a small native demo, the owner said it felt "so so so much smoother". So the app is being **rewritten natively** in Kotlin Multiplatform with Compose Multiplatform. Android comes first and gets the most care; it is built so iOS and Linux desktop can follow.
- **Day 1 for users:** the native app installs **over** the Flutter app: same package `io.github.eslamasabry.opencode_mobile`, same signing key (SHA-256 `1DE5BF08…D60C`). On first launch it migrates everything: servers, passwords, chats, drafts, schedules, widgets, voice models, and the phone's Linux. Nothing is set up again.
- **Nothing lost:** the Flutter app's 6,663 logic tests became **1,544 behaviours** (the catalog). Each one needs a native test before its feature counts as done. 39 device flows (Maestro) must pass on both apps. The 7,967 widget tests are catalogued later (owner).
- **Flutter is frozen:** no new features, only bug and security fixes. The last Flutter release is the "bridge" to the native app.

## 2. Where everything is

| What | Where |
|---|---|
| The plan (authoritative) | `docs/design/native-rewrite-2026-11-01/` on branch `native/m0-2026-11-01`. Start at `README.md`. `plan/01-architecture-decision.md` wins every disagreement. The roadmap with every unit (write set, finish line) is `plan/15-delivery-roadmap.md`. |
| Behaviour catalog | `docs/design/native-rewrite-2026-11-01/catalog/` (`BEHAVIORS.md`, `behaviors.jsonl`, `e2e-flows.md`); the seeded scope file is in `native/catalog/` |
| Native code | `native/` on branch **`native/m0-2026-11-01`**, the integration branch; every unit merges here. Public repo: `github.com/Eslamasabry/opencode-mobile-next`. |
| Unit reports | `docs/native/units/<UNIT>.md` (what each cloud unit built, its tests, deviations) and `docs/native/slice0/*.md` (the slice-0 proofs) |
| Device proof report | `docs/native/slice0/DEVICE-PROOFS-2026-11-01.md` on branch `native/proof-p5-2026-11-01` (pending merge) |
| Second-eye reviews | `docs/native/reviews/` (Codex). Local copies: `/home/eslam/Storage/Code/wt/native-reviews/*.md` |
| Loop scripts | `docs/native/ops/` (copies of the coordinator's scripts; see §5) |
| Flutter freeze branch | local `int/freeze-2026-11-01` (worktree `/home/eslam/Storage/Code/wt/oc_app-freeze`); public snapshot `snapshot/freeze-2026-11-01`. Branch ledger: `docs/verification/branch-ledger-2026-11-01.md` |
| Launch clone for cloud sessions | `/home/eslam/Storage/Code/cloud-launch`, a shallow clone of opencode-mobile-next. Its only remote is `origin`. |
| Owner memory and decisions | `~/.claude/projects/-home-eslam-Storage-Code-oc-app/memory/` (`MEMORY.md` is the index) |

## 3. Rules everyone follows (owner rules; breaking them is a regression)

**Safety:**
- Never print or commit credentials, tokens, passwords, API keys, the owner's email, or private network addresses. That includes the PC's Tailscale IP, the build server IP and the phone's adb serial.
- Every push goes through `safe-push.sh`, which **blocks** on those values.

**Git:**
- `.claude/skills/` is never committed.
- Commits: subject ends with `[skip ci]`; author `104366884+Eslamasabry@users.noreply.github.com`; end with the two trailers `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and `Claude-Session: …`.
- Never push tags, releases or master without the owner's yes. Work branches and snapshot branches may be pushed freely (owner: "let it be wild").
- Local history can't be pushed (an old commit holds a 181 MB APK). Push **snapshots**: `git commit-tree <branch>^{tree} -p mobile-next/master`.
- Never stash, checkout or reset in a shared checkout. Never delete branches or worktrees without an exact path, and never delete branches on GitHub without the owner's yes.

**Processes and devices:**
- Kill processes only by exact PID.
- Never wait with `until ! pgrep -f "<pattern>"`: it matches itself forever.
- Never `adb kill-server`, never `pm clear`, never `install -d`.
- Hold `flock /home/eslam/Storage2/wt/emu.lock` around every emulator command. The emulator is `emulator-5584`.
- **The owner's phone:** no screenshots, read-only `adb` unless the owner says install. Never touch ports 4150/4199 (the owner's servers).

**UI and product:**
- UI is **kit only**: every component comes from the kit.
- Android-native feel: ripple, predictive back, haptics, 150–250 ms motion, reduced motion.
- Plain words, never raw errors. No new UI bar levels.
- Steer, never queue. Arabic RTL.
- Frame text capped at 1.3×; components have max widths.

**Testing:** tests first, from catalog row ids. Every APK passes the emulator before it reaches the owner.

**Tooling:**
- The owner said **no more Workflow-tool runs**. Use single agents, Claude Code cloud sessions, and Codex.
- Analytics: BASIC is always on and anonymous; DETAILED is opt-in.

## 4. The machines and tools

| Thing | How |
|---|---|
| Native build (PC) | `cd native && JAVA_HOME=/home/eslam/Storage/tools/jdk-21.0.13+9 ANDROID_HOME=/home/eslam/Android/Sdk ./gradlew --no-daemon :androidApp:assemblePreviewDebug compileKotlinDesktop`. Gradle needs **JDK 21**. Use `-Pkotlin.incremental=false` if a stale incremental cache errors. Preview APK: `native/androidApp/build/outputs/apk/preview/…` (package `…opencode_mobile.native`, beside the real app). |
| Release preview for the phone | `:androidApp:assemblePreviewRelease`, then zipalign and sign with the debug keystore (separate package, so any key works) |
| Flutter (frozen app) | Flutter 3.47.8 at `~/.shorebird/bin/cache/flutter/6e5e9495…/bin`; 3.47.1 (`91f8bd75…`) only for 2.1.0 patches. Tests: `tool/qa/vps_test.sh` from your own worktree. APKs: `tool/qa/vps_build_apk.sh <n>`. |
| Build VPS | key-only ssh; address in the owner's private notes (not in this repo) |
| Claude Code cloud | `claude --effort <high\|medium> --cloud "<task>"`. It needs a TTY, so run it in tmux (`ops/cloud-launch.sh`). It clones the launch folder's remote at its current branch; results come back as **pushed branches**, not PRs. Cloud machine: 4 vCPU, 16 GB, no emulator; install the Android SDK first (about 30 s, in every brief). Effort: high for hard units (chat engine, migration, protocols, integration), medium for the rest. |
| Codex | `codex exec -m gpt-6.1-sol -c mcp_oauth_credentials_store=file -s read-only -o <report>`. Astra (`gpt-6-astra`) is the stronger one-off reviewer. Codex is a separate account, so it keeps working when Claude limits hit. |

## 5. The work loop (how the native app gets built)

Run this loop continuously. It is how about 60 units were built on day 1.

1. **Pick units** from `plan/15-delivery-roadmap.md`: each row has a write set (folders) and a finish line. Pick units whose write sets **don't overlap** anything running. New modules are auto-discovered from folders, so there are no settings edits.
2. **Write a brief.** Copy `ops/example-unit-brief.txt`. The shared first paragraph says:
   - install the Android SDK;
   - read `native/AGENTS.md`, the unit reports and the plan;
   - write only inside the write set;
   - tests first, by catalog ids;
   - no PR, push branch `native/<unit>-<date>`;
   - write a report at `docs/native/units/<UNIT>.md`;
   - **use only the flat core contract from `INTEGRATE-W1/W2.md`**.
3. **Launch:** `ops/cloud-launch.sh <brief.txt> native/m0-2026-11-01 <effort>` inside `tmux new-session -d`. It prints a `claude.ai/code/session_…` link.
4. **Watch:** `ops/watch-finished.sh handled.txt` exits when a branch gains a **report file**. Units push early stubs, so only a report means done.
5. **Land:** `ops/land.sh <unit>` merges into m0, safe-pushes and records it.
   - On a conflict, resolve by keeping both sides' intent. The usual cases are shared build files (union the dependencies) and parallel kit files.
   - If the unit started **before** the latest integration and uses old contract shapes, **hold** it.
6. **Build check** after each landing wave (the command in §4). It must stay green.
7. **Integrate:** when held units pile up, or parallel units invented stand-in types, launch one **high-effort INTEGRATE session**. It merges the held branches one by one onto the settled contract and keeps the build and tests green. W1 and W2 are done; W3 is running.
8. **Second eye:** queue every landed range in `review-queue.txt`. `ops/review-runner.sh` runs one Codex review at a time (sparse clone, deleted afterwards). Findings go to the owning unit or a REVIEW-FIX session; each real finding gets a test that fails before the fix.
9. **Device proofs** on the PC emulator (cloud has no emulator):
   - Macrobenchmarks: streaming, start-up;
   - the migration install over a real Flutter 2.1.0 build, on the OC_API37 AVD so the real app on emulator-5584 stays untouched;
   - language switching.

   Then the owner's own hands for feel (the back gesture, haptics).
10. **Refill:** as each unit finishes, launch the next so about 18 run at once.

**Disk trap:** the PC fills up fast. Review clones are sparse and temporary now, and `df -h /home/eslam/Storage` is checked often. If it hits 0, Claude Code's own temp folder fails too.

## 6. Status at the time of writing (2026-11-01 evening)

**Native, merged on `native/m0-2026-11-01` (builds clean, 50 units):**
- **Slice-0 proofs:** P1–P7, the strings tool, the migration readers.
- **Core, network and protocols:** core, net, data, the OpenCode 2 client, network probes, backoff, analytics.
- **Platform and engines:**
  - built-in Linux (copied unchanged), platform basics, launch routing, security gates;
  - settings, diagnostics and profiles, the composer engine, the runtime engine, the agent host;
  - message delivery (never send twice), requests and forms, voice engine, attachments, QR camera (no ML Kit);
  - notifications, widgets, the field recorder.
- **UI:**
  - kit parts A and B, kit motion, chat renderers, composer motion (with the green glow);
  - the chat screen, Conversations, composer UI, This phone, Settings, the terminal and sign-in.
- **Plus:** fixtures, Maestro flows, catalog scope, check scripts, and integrations W1 and W2 plus REVIEW-FIX-W1.

**Held for INTEGRATE-W3** (built on older bases):
- **Engines and clients:** agents engine, Markdown, migration runner, Paseo client, transcript engine.
- **Screens and platform:** migration gate screens, Android entry points (share, Tailscale, shortcuts), the Servers screens.
- **Proof branches:** P3, P5, P6.
- **Then (W4):** the **stream fix**, which fixes P3. Per-update work was growing with reply length; it is now constant on the JVM. The device benchmark must be rerun after it merges.

**Launched, not yet reported (cloud):**
- **Integration:** INTEGRATE-W3.
- **Runtime and platform:** runtime wiring (component names and launcher shortcuts; its launch output was lost to a full disk, so check its branch exists or relaunch), tiles and shortcuts.
- **Screens:** Agents, Files, Git, Linux manager, web preview, Projects, review, search, usage, voice screens, background-work display.
- **Engines and data:** schedules, updater, blob store.

**Device proofs:**
- **P6 language:** pass.
- **Start-up:** 0.23 s on the emulator with the profile; 0.41 s on the owner's phone unoptimised.
- **P2 back gesture:** owner said "feels good".
- **P5 migration:** pass except the launcher shortcuts; the fix is in runtime wiring.
- **P3 long-reply streaming:** **failed**. The fix is built (stream fix); rerun the benchmark.

**Codex reviews:** 25 reports so far, findings fixed through REVIEW-FIX-W1. The queue continues in `review-queue.txt`.

**Flutter freeze:**
- The gate took the failing test files from 99 to **6** in round 1. Left: `file_size_ratchet` (three files over 1,500 lines), `redaction`, kit capacity shard 3 (KitContextRing), and three screenshot tests.
- Phone build **4501** passed the emulator and is hosted on the PC's APK share server. It isn't on the owner's phone yet: their phone dropped off adb, so they install it from the link or re-enable wireless debugging.

**Blocker at writing time:** the owner's **Claude monthly spend limit was reached**; the weekly limit resets Nov 6, 05:00 (Asia/Dubai). Local subagents can't start, and cloud sessions may stop. Codex still works. The owner chooses: raise the limit, wait for the reset, or both with Codex meanwhile (the recommendation).

## 7. What to do next, in order

1. **Spend limit:** confirm the owner's choice. Meanwhile run Codex (`workspace-write`) on the 6 remaining Flutter gate failures in the freeze worktree, then build the freeze APK, run an emulator sweep, and get it to the owner.
2. **Branches that land:** when INTEGRATE-W3 lands, land it, build-check, then launch **INTEGRATE-W4** for the stream fix plus anything held since.
3. **Streaming benchmark:** rerun on the emulator (OC_API37) using the benchmark module on `native/proof-p3-2026-11-01`. The target is no frames over budget on the 6,000-word reply.
4. **Migration proof:** rerun P5 (the install over Flutter) after runtime wiring lands. Shortcuts, placed widgets and channels must survive.
5. **Next native preview:** build it for the owner's phone (release, separate package) once W3 and W4 land. It should be the first that talks to a real server.
6. **Keep the loop running:** refill units from `plan/15`, slices 3–5 (media, integrations, the remaining screens), and keep a Codex review on every landing.
7. **Re-plan at the end of M0:** measure real throughput (units per day, review findings) and update the dates in `plan/15` and the README honestly.

## 8. Owner decisions already made (don't re-ask)

- **Architecture:** native KMP + Compose; Android first; iOS and Linux later. Glass: none until after the switch, then one optional trial.
- **Packages:** Google-free QR (no ML Kit); F-Droid after the switch.
- **Analytics:** BASIC always on (anonymous); DETAILED opt-in; pluggable senders.
- **Arabic numbers:** follow the phone's own setting, the same on screens and in notifications (one shared formatter).
- **Freeze-branch decisions:** QR not merged into Flutter; keep the two current widgets; no agent browser; navigation doc archived; no AI Team merge undo.
- **Delivery tooling:** push work branches freely; cloud effort high for hard units and medium for the rest; Codex as the async second eye; no Workflow runs; demo app deleted.

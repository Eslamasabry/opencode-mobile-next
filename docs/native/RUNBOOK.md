# OpenCode Mobile native rewrite: the runbook

## Consolidated handoff — 2026-11-02

**Owner instruction: stop implementation; update this one file with the work since the runbook was first read.** Implementation and review agents were interrupted. No new feature work, build, signing, push, PR, merge or release is authorized by this handoff. Existing source edits and worker outputs are preserved.

**Actual product state:** the delivered native app is still a side-by-side Servers → Conversations → Chat preview. Real authenticated chat, streaming, restart recovery, text drafts and useful connection errors work. It is **not the complete native app or Flutter parity**. Model selection and local-file attachments have substantial code and focused checks, but their combined app journey is unfinished and has no delivered APK.

This consolidated record takes precedence over the older November 1 status and historical “next” instructions below. Test counts belong to the named candidate/slice; they must not be added together or carried forward as a pass of the unfinished working tree.

### 1. Work preserved and integrated

- Recovered the two repair bundles lost by the earlier cleanup, preserved all **eleven** repair bundles outside disposable worktrees, verified their Git bundles and retained original histories under local `recovered/<unit>` refs. The preservation helper was implemented with an actual GLM 5.3 worker, reviewed, hardened and tested, including durable receipts and failure cases. The archive and manifest are in `/home/eslam/Storage2/wt/native-repair-artifacts-20261102/`.
- Integrated repairs for gates, connection/engine, composer engine, two kit groups, notifications, widgets, delivery, chat, conversations and requests. Preserved both sides of the overlapping delivery fix. Fixed Stop losing the local send identity, which could leave a chat bubble stuck Sending. Registered 17 previously landed modules in `MODULE_OWNERS`.
- Integrated the stream fix (`recovered/streamfix`, `57d3b9fb`), retaining scope/review protections and bounded text-row layout/chunking. This does not make all search extraction constant-cost or establish Android frame-budget parity.
- Corrected machine-dependent test fixtures, supplied frozen Flutter string/migration inputs to the VPS, and forced fresh Gradle Test execution after cached failed desktop results were found.
- Pinned the desktop golden suites' Arabic font inventory. Visually reviewed the measured KitRow change; exactly two English reference PNGs changed within the documented region. The Arabic notification reference remained byte-identical. Normal gates do not update baselines; watchdogs and pixel thresholds were not relaxed.

Evidence: [repair/integration report](units/REPAIR-INTEGRATION-2026-11-02.md), [stream integration](units/STREAM-FIX-INTEGRATION-2026-11-02.md), [test inventory](verification/2026-11-02/vps-test-inventory.json), [frame measurements](verification/2026-11-02/stream-frames.json).

### 2. Runnable native server/chat preview delivered

The Android entry point previously exposed the slice-0 empty feed and placeholder chat. The integration wired a real OC2 gateway, persistent profile/Keystore storage, durable delivery storage, per-profile connection graphs, merged conversation feed, server setup/navigation and the real chat screen.

- Verified isolated authenticated OC2 beta `0.0.0-beta-19242` and stable `2.2.5`: session creation, real model replies, streamed parts, interrupt and task-owned session cleanup. Stable route negotiation was corrected; only a clean health 404 permits read-only dialect negotiation, and mutations are not replayed.
- Fixed conversation-page cursor handling so short terminal pages do not display a false incomplete-source notice.
- Verified unreadable credentials fail before opening a gateway and resolved credentials reach only the captured target.
- The emulator caught the chat-entry dialog-host crash. Root-screen tests failed first; one shared `KitSheetHost` now encloses the frame and overlays, preserving the kit assertion.
- Profile deletion fences delivery admission and marker writes, checks queue/journal uncertainty, then sweeps local metadata/secrets. Unknown or unreadable work preserves the profile.
- Corrected the Android app's Kotlin target to JVM 17 and supplied the actual Activity window for Android status-bar contrast.
- API 35 emulator: saved server → authenticate → create/open conversation → real `NATIVE_APP_OK` response → restart/update → automatic reconnect and retained history. Server history contained exactly one matching user prompt after restart/updates. Profile removal removed the encrypted secret entry; the password was absent from plaintext preferences.

Candidate `4d744e8b`, native source ID `e857d5a0a5e194000734a295d3d710032322a286fe702d247e09205eb2e5be7d`:

| Verification | Recorded result |
|---|---|
| Assigned VPS inventory, including build-logic | 3,470 tests: 3,469 passed, zero failures, one explicit opt-in live-server skip |
| Live OC2 test, run separately | Passed against both isolated beta and stable servers |
| Two assigned PC rendering tests | Both passed fresh, unchanged one-minute limits |
| JVM chunked frame benchmark | 5,439 frames per shape; p95 3.82 ms; maximum 448 laid-out characters/frame |
| Additional focused gateway/chat-host/Android tests | Passed; details in the linked integration report |

The original broad VPS command **did fail** a renderer watchdog. That exact renderer passed fresh on the PC, and the remaining quality task passed fresh on the VPS on the unchanged source. This is the completed assigned split-machine matrix, not a claim that the original command or the complete release/T2 ladder passed.

### 3. Durable text drafts delivered

Implementation `7ff73263`, branch `native/chat-drafts-2026-11-02`; contracts `3c60f3e9` and `52f8a179`:

- Exact text, including whitespace, survives save and process restart. Initial restoration cannot overwrite storage with an empty editor value.
- Clearing is bound to the captured submission token and edit revision. Local dispatch success does not clear the field; Accepted/canonical evidence does. A newer edit, including an A → B → A edit, survives an older acknowledgement.
- Failed clears remain blocked and retry deletion rather than resurrecting a sent draft. Unknown acknowledgement never automatically resends. Explicit repeat confirmation is target/revision bound.
- App-scoped draft ownership, bounded disposal flushes and profile-generation fences protect late writes. Refused profile removal requires explicit recovery; old writer handles stay fenced.
- Wrong-typed/corrupt draft storage is preserved and blocks editing/sending. The text-only preview preserves/refuses attachments it cannot restore. No OC1 database was deleted.
- API 35 emulator proved exact restart restoration, one real response, accepted clear without resurrection, corrupt-index preservation, and profile/draft/secret removal. The owner phone and installed Flutter package were not modified by those emulator checks.
- Final focused checks: **143 composer + 68 chat + 32 selected kit + 44 selected quality tests**, all with zero failures/errors/skips. Earlier full-suite evidence does not qualify this new slice. An actual GLM 5.3 review worker found storage issues; its completion was not treated as final qualification.

Evidence: [draft verification](drafts-2026-11-02/verification.md), [check receipts](drafts-2026-11-02/drafts-checks.json), [GLM review triage](drafts-2026-11-02/GLM-review-triage.md). Source ID `6340f02501c4375d194ffdcfbc54168319621a3570acb403cb60de213a09a8fd` includes the final test-selector correction; production APK inputs were unchanged by that correction.

### 4. Connection diagnostics delivered; owner connection recovered

Implementation `7f23ae13`, contract `99d310ea`, branch `native/server-connection-errors-2026-11-02`:

- Connection failures retain their typed failure information and safe stage context. Generated Details contain closed categories/numeric status only, not raw exception text, server URLs, IDs or credentials.
- Server UI now distinguishes refused/offline/network failures, unreadable replies, authentication and storage problems using plain English/Arabic copy. Details start collapsed and can be copied. Unknown failures do not invent a network diagnosis.
- API 35 emulator proved malformed replies, refused ports, Details/Copy, a real stable-server connection/reply, cold restart/reconnect and no duplicate prompt. The APK updated with the same certificate.
- Final focused checks: **9 connection + 14 server UI + 44 selected quality tests**, zero failures/errors/skips. Corrected real-screen tests were observed failing against the old UI before passing; invalid initial harness runs were not counted as behavioral red evidence.
- The owner reported `OpenGateway · Network · Refused`, then confirmed “that fixed it” after saved-address and Android Tailscale app split-tunneling checks. The owner did **not** identify which of those checks fixed it, so no narrower root cause is established.
- Six actual OpenCode/GLM 5.3 worker runs completed for tests, connection implementation, UI, Linux feasibility, page inventory and review. Their results were reviewed and corrected. A cancelled duplicate inventory worker is not counted as completed work.

Evidence: [connection verification](server-errors-2026-11-02/verification.md), [receipts](server-errors-2026-11-02/checks.json), [worker receipts](server-errors-2026-11-02/glm-workers.json). Source ID `3e2f32bd779f818969c41dccd068029172ec6ce8a314c1a60334e5477b83f483`. Its incremental bundle was verified locally and backed up to the VPS with matching SHA-256.

### 5. APKs actually built and delivered

All three are the separate `io.github.eslamasabry.opencode_mobile.native` preview package, `3.0.0-dev`, with signer SHA-256 `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`.

| Version code | APK | Bytes | SHA-256 |
|---|---|---:|---|
| 10000 | `opencode-native-preview-20261102.apk` | 22,617,713 | `08445466e86c58b30bb589155c79bbc2aab4d26d54f036870255ba070178678d` |
| 10001 | `opencode-native-drafts-preview-20261102.apk` | 22,384,374 | `4fc04f3f144b6dc3a5f0874a2edf0457104edee187be9ffe6e6e348839cf5f09` |
| 10002 | `opencode-native-server-errors-10002.apk` | 22,386,235 | `4c1155d98e78d9dc1e23077a1ce012a7d5b5bce5e6a60fe69dd690fdb35267d7` |

Last delivered Tailscale download: [native preview 10002](the private APK share server on the owner PC; address in private notes). The download directory is `/home/eslam/Storage2/wt/native-preview-download-20261102` and contains APKs only. This is a private tailnet delivery, not a public release. The table records delivered artifacts; it does not claim the link was rechecked during this documentation-only stop.

There is **no built/delivered 10003 APK**. A working-tree version increment to 10003 is preparation only. Do not distribute the current composer working tree as a qualified update.

### 6. PC/VPS access and phone Linux boundary

- Reused the existing private build-server environment and key-only SSH for the VPS. JDK 21/Android SDK and private desktop renderer libraries were used there. Host address, SSH keys, provider credentials and login passwords are intentionally absent from this file.
- Used actual OpenCode CLI workers with model route **`zai-coding-plan/glm-5.3`**. This is distinct from the stable test backend's available `opencode/glm-5.3-flash-free` catalog entry.
- Heavy jobs were stopped when requested earlier. The owner later authorized both PC and VPS without CPU restrictions. Gradle/emulator ownership remained serialized by machine locks; owner processes were never killed by command-line pattern.
- Last known owner-facing PC backend: OC2 stable **2.2.5**, on the owner PC (address in private notes). Its private runtime state is under `~/.local/state/oc-app/native-preview-server-20261102`; use the saved/private login rather than publishing credentials. Backend PID `1249297` was still present at this handoff; it was not stopped. Owner listeners on 4150/4199 were not touched.
- API 35 emulator `OC_NATIVE_PREVIEW_20261102`, serial `emulator-5584`, PID `1425918`, was still present at the stop. Its session holds `/home/eslam/Storage2/wt/emu.lock`; no new emulator checks are authorized by the stop. The PC backend/download service remain available independently of implementation work.
- The native preview has a separate Android UID, preferences and Keystore. The Flutter built-in Linux bridge is private/in-process, with unexported services and no supported cross-package credential handoff. Automatic adoption/start/wake of the existing phone Linux was **not implemented**. Saving a verified compatible OC2 URL/login once can use existing startup reconnect; that does not import or control the Flutter Linux installation.
- No owner Linux reset/reinstall, OC1 database deletion, forced runtime migration or replacement of the installed Flutter app was performed.

Evidence: [existing-Linux handoff analysis](server-errors-2026-11-02/existing-linux-handoff.md).

### 7. Model/file composer work completed before the stop — unfinished product

Active checkout: `/home/eslam/Storage2/wt/native-full-composer-20261102`, branch `native/full-composer-2026-11-02`, based on `7f23ae13`. The Flutter checkout `/home/eslam/Storage/Code/oc_app` is a different Git repository; do not edit it to resume these native changes.

- Committed the finish line, non-goals and integration API at **`42d7ba61`**, including the platform-neutral `PromptFilePicker` interface. The old dormant `FullComposerHost` remains unbound: its early-clear behavior would regress acknowledged drafts. The intended visible richer composer extends the working chat host instead.
- Ran **six actual GLM 5.3 implementation workers** on the VPS in isolated snapshots: `draft-files`, `android-files`, `protocol-models`, `delivery-rich`, `world-rich`, `ui-rich`. All six OpenCode processes exited 0 and exported patches. This proves worker completion, not app correctness. Scoped provider auth was handled privately. Worker JSONL, patches and exit receipts are under `/home/eslam/Storage2/wt/native-full-composer-artifacts-20261102/vps-evidence/`.
- Adopted their scoped patches for review: whole-state text/file drafts; a real Android OpenDocument/ContentResolver adapter; model catalog and session-selection protocol APIs; rich delivery snapshots; chat-world model/files integration; visible model/Attach/remove controls, EN/AR copy and tests. App dependencies, graph and Activity lifecycle were prepared. These implementation changes are **uncommitted working-tree changes**.
- On a newly-created task-owned stable-server session, verified two model POST mutations returned 204 and GET readback matched provider/id. Confirmed model/default/provider/agent endpoints and safe catalog shape. No owner session was mutated by that feasibility probe. Its task-owned session remains recorded in the private artifacts; cleanup was not started after the stop.
- Coordinator regressions against the old OC2 implementation produced **2 real assertion failures**: selection readback was null and catalog unavailable. The protocol patch then needed immutable-list/import fixes and proper catalog capability projection before its focused checks passed.
- Reviewed the Android picker rather than trusting its exit status. Two coordinator regressions exposed reading a whole chunk past the byte limit and freeing a cancelled request before its old OS callback returned. Both were observed as assertion failures, then corrected: bounded reads stop after the one overflow byte; abandoned picker slots remain occupied until drained/detached. Registration visibility and launcher/metadata dispatcher handling were also corrected.
- New draft-attachment tests first failed to compile against the absent facade API; this is an API-red receipt, **not** a behavioral test execution. After adopting the facade, the focused new/existing draft tests passed. The former test rejecting every attachment now checks preservation/refusal of a corrupt stored attachment instead.

| Focused check completed before stop | Result and scope |
|---|---|
| `:engine:composer:desktopTest`, `ConversationDraftAttachmentsTest` + `ConversationDraftsTest` | **28 passed**, zero failures/errors/skips |
| `:protocol:oc2:desktopTest`, `CoordinatorComposerModelTest` + `Oc2SessionSelectionTest` | **11 passed**, zero failures/errors/skips |
| `:androidApp:testPreviewDebugUnitTest`, `CoordinatorPickerSafetyTest` + `AndroidComposerFilesTest` | **11 passed**, zero failures/errors/skips |

These are separate focused snapshots. The final combined UI/world/delivery candidate has **not** passed feature tests, affected full module suites, architecture gates, APK assembly or emulator acceptance. No model/file composer APK was signed, delivered or device-qualified. The 10002 APK therefore still looks like the earlier thin preview.

Known review findings remain open; do not silently treat the imported worker patches as ready:

1. Same-conversation model selection/readback and prompt admission can interleave across concurrent dispatches; a request can use another request's model. Ownership must span selection through admission without adding an invisible steer queue.
2. The new delivery preflight currently applies selection before checking transport failure/current epoch. Slash/shell syntax with files can also route through a command path that drops those files.
3. Delayed picker results need the captured graph/target generation; concurrent file removals need a synchronous operation latch. Send's direct gate must respect in-flight picker/commit/model operations even before StateFlow publishes.
4. Older model loads can overwrite a newer verified selection. Failed defaults/session reads are currently ignored; final stale readback and failed-selection UI handling need correction.
5. File-only canonical user messages are not represented/reconciled by the current transcript path, potentially leaving an empty Sending bubble after accepted delivery. The proposed transcript fix was interrupted before source edits.
6. Restart/ambiguity protection still relies partly on text or filenames instead of the complete immutable file identity. Equal text with different files and attachment-only unknown receipts need explicit regressions.
7. Full-state draft writes/restoration currently hardcode a null location, losing `file://` directory/workspace binding. New picker output is data URLs, but legacy/server-file references must remain safe before broader attachment support is enabled.
8. Default-model mapping uses `modelID` rather than the catalog's distinct mode ID; requested variant `default` is not normalized consistently during delivery readback. Both need regressions.

Private logs and receipts: `/home/eslam/Storage2/wt/native-full-composer-artifacts-20261102/`. Contract: [composer contract](full-composer-2026-11-02/contract.md). The separate in-progress verification note is a partial working record; this consolidated stop-state is authoritative.

### 8. Parity, other requests and shipping state

- [Reviewed page inventory](server-errors-2026-11-02/page-parity-map.md) and [CSV](server-errors-2026-11-02/page-parity-map.csv) distinguish routed product journeys from source-only modules. They are **not** an official completed-parity count. Loaded-transcript search/older-message cursor handling already exists; the all-conversations page is a stub and the list only loads its first page. Do not count every source module as a delivered page.
- Settings, runtime/This phone integration, voice, broader file/project/agent pages, saved-prompt UI and the other unrouted journeys remain unfinished. Automatic Linux adoption, in-place Flutter migration, physical-device qualification, full visual/RTL/accessibility parity, iOS and release gates remain open. No defensible exact “steps to parity” count was established.
- The requested `google/elephant-goldfish-model` clone exists at `/home/eslam/Storage2/wt/elephant-goldfish-model-explore-20261102`, origin `https://github.com/google/elephant-goldfish-model.git`, clean HEAD `403d8881d62022490637d740657b33f01e321ea8`. No native integration or completed evaluation/test receipt is claimed here.
- Delivered: three preview artifacts and the verified server/chat, text-draft and diagnostics journeys above. Enabled/verified in a delivered APK: those bounded journeys only. Current composer work: partly implemented, focused checks only, uncommitted, not delivered.
- Local implementation commits use `[skip ci]`. No public push, PR, M0/master fast-forward, tag, production deployment, Shorebird release or replacement of the installed Flutter app was performed. Recovery bundles and verified prior commits remain preserved locally/on the VPS.

**Resume only on a new owner instruction.** Preserve the dirty composer worktree and review findings. Do not launch additional workers, tests, builds or shipping actions from the historical work-loop instructions below while this stop is in effect.

---

## Earlier runbook snapshots (historical)

> **2026-11-02 product status:** the current APK is still a thin Servers/Conversations/Chat preview, not the full native product. Text drafts are integrated at local7ff73263. Preview10002 adds typed connection failures with safe Details;67 focused checks and API35 emulator journeys passed, and the owner confirmed their connection recovered. See [current evidence and limits](server-errors-2026-11-02/verification.md), [reviewed page gaps](server-errors-2026-11-02/page-parity-map.md), and [existing-Linux boundary](server-errors-2026-11-02/existing-linux-handoff.md). Next: integrate a complete full-composer journey with model selection and attachments. Most other feature modules remain unrouted; automatic Linux adoption and full Flutter parity are incomplete.

> **2026-11-02 continuation:** a separate, locally verified native server/chat preview now exists on local branch `native/repair-integration-2026-11-02`, based on M0 `3bb7632e`. Eleven review-fix bundles and the stream fix are preserved and integrated. Real beta/stable OC2 chat, Android 15 emulator restart/update recovery and profile-secret deletion have been exercised. See [the current evidence and limits](units/REPAIR-INTEGRATION-2026-11-02.md) and [preview proof](verification/2026-11-02/app-proof.json). The status below is the original November 1 snapshot; it is not a current qualification claim. M0/public branches, the installed Flutter app and releases have not been changed.

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

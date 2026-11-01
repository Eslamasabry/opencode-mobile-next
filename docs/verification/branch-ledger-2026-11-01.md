# Branch ledger for the 2026-11-01 freeze

Written 2026-11-01 for the owner.

- Freeze branch: `int/freeze-2026-11-01`, worktree `/home/eslam/Storage/Code/wt/oc_app-freeze`. Tip before this ledger: `fc8164b8f`. Built on `int/batch1-2026-11-01`. App version in `pubspec.yaml`: 2.1.0+57.
- Verified: `flutter analyze lib test` reported no issues at `92ed57977`, and again after the last repair commit `fc8164b8f`. Nothing else was run.
- Not done, by the freeze rules: no `flutter test`, no Kotlin compile, no APK, no emulator, no phone. Every test that was added or changed in this run is written but has never been run.
- Not done, by the house rules: nothing was pushed, tagged, released or signed. No branch and no worktree was deleted. The shared checkout `/home/eslam/Storage/Code/oc_app` was not touched.
- Words used (AGENTS.md rule 8): "merged" means in the freeze branch on this machine only. "Verified" means the analyzer passed. It does not mean a test or a device run.
- How to read "merged-equivalent": `git branch --merged` will not list most of these, because the integration line arrived through snapshot commits and `git cherry` shows their commits as missing. They were classified by comparing content (files, patch ids, ancestry), not by commit hashes.
- Outside this ledger: the branches `int/phone-backend`, `int/ui-batch-2026-10-14` and `build/aiteam-phase-a` exist but were not in the classified data. Nobody reviewed them here.

## 1. What the freeze branch now contains

22 branches were merged into the freeze branch. A 23rd, `sol/ba-2198-cert`, was skipped (section 3). Merge commits are in brackets.

1. `int/combined-2026-11-01` [815524c3f] - the 2.1.0 patch line plus the phone fixes, merged first. A user can now:
   - have the phone's OpenCode server restart itself after an OpenCode update;
   - share files, and file links found in chat, to another device over Wi-Fi (private files are refused);
   - try the Claude Agent beside Claude Code;
   - open a file that lives on the phone from a chat link;
   - see the process budget, the kill line and a guided card for the Android 14+ child-process limit;
   - get less background CPU, and pacing that follows heat, battery saver and a quiet network;
   - get fixes for large text, the free-tier limit, and agent pins and auto-update.
2. `feat/per-file-review-2026-11-01` [49e6d83f6] - a design note only. Per-file Keep/Undo (issue #27) stays unavailable because the server can only revert per message.
3. `feat/role-voices-2026-11-01` [50ffd8581] - each role in an AI Team task conversation now has its own voice.
4. `feat/linux-vitals-2026-11-01` [ed0ed879d] - a user can see and control Linux services on the phone (Services pages) and read Linux vitals.
5. `docs/role-cycle-plan-2026-11-01` [cb4528ad7] - research, use cases, architecture and UI notes for the role cycle. Docs only. Owner decisions are listed inside the use-case doc.
6. `fix/demo-video-deps-2026-11-01` [353dae72d] - pins two packages in the demo-video tool. No app change.
7. `feat/star-share-2026-10-31` [88559915b] - About can star the app on GitHub, share the app, and show a What's new walkthrough after an update.
8. `fix/stream-partial-syntax-2026-10-31` [0932767f0] - streaming replies no longer flash half-written Markdown.
9. `feat/app-updates-2026-10-31` [6b686bb7c] - an in-app update notice, a badge on the gear, an Update now / Open Settings sheet, and an Updates page at Settings > This app > Updates. The old check row left About.
10. `feat/paste-image-2026-10-31` [1f820f92a] - a user can paste a picture from the clipboard into the composer (a row in the + sheet).
11. `fix/steer-join-2026-10-30` [472d31795] - a draft kept after Stop no longer sticks to the next words typed.
12. `docs/changelog-2199` [97339728e], `sol/bd-bd7` [41e3a6afc], `sol/bd-gates` [94e1e0a0f] - changelog and QA records only.
13. `build/aiteam-kit` [0d7e0beb8] - the AI Team finding checkbox now honors reduced motion, with more reduced-motion tests.
14. `fix/termux-running-entry-20260913` [3c62c879d] and `fix/storage-cleanup-scope-20260913` [bf2ebbb73] - recorded with no tree change, because the freeze already had both fixes in later form.
15. `feat/linux-backup-2026-11-01` [a8da5f553] - a user can back up Linux settings and restore them from a zip (Backups page, projects only, "Back up first" in Reset). Removing OpenCode now clears the Linux settings and says saved backups stay where they were put.
16. `perf/native-2026-11-01` [32ebacfb9] - the agent helper runs at background priority, battery saver counts as heat, new heavy work waits while the phone is warm ("Always full speed" lifts that), and the agent-backend watch goes back to 10 seconds the moment the app returns.
17. `feat/gemma-assistant-2026-10-31` [62a306a24] - "Assistant on this phone" (Settings > Conversations): download an on-device model on Wi-Fi, then "Summarize on this phone" in a conversation menu.
18. `codex/audit3` [b28669287] - nine security and lifecycle fixes from the 09-29 audit, ported onto today's code: the installer probe needs an authenticated answer, profile deletion sweeps more keys, migration exports keep private files out, and Stop can no longer be overtaken by a server that is still launching.
19. `fix/web-preview-20260913` [05510150b] - browser mode: the app can run in a web browser against OpenCode 2 servers. Codex and Paseo are disabled there, with the reason shown. Drafts and attachments are kept in browser storage, and Help has a Browser mode page.

Follow-up commits on the freeze branch:

- `92ed57977` - a bug carried in from paste-image: it had left literal `true ? null : ...` and `if (false)` in place of its conditions, so every text field lost its long-press copy and paste menu and the + sheet never offered the clipboard picture. Fixed, plus six analyzer findings.
- `fc8164b8f` - the What's new "Show me" for Updates now opens the Updates page and highlights Check now (it pointed at the removed About row). The paste step points at the message box. Two stale tests and the 2.2.0 copy line follow the move (English and Arabic).
- `bd5eab004` - the UI ledger rebuilt after the merges. gen-l10n was run and committed after every merge that touched an ARB file.

## 2. Finished in this run

Nine branches were worked in their own worktrees. Five were finished and merged into the freeze (items 15 to 19 above). Four were not finished. Nothing in this section was run, only analyzed.

Finished and merged:

- `feat/linux-backup-2026-11-01` - closed the two deletion rows that S3 left open (Remove OpenCode clears the `oc.linux.*` settings through `forgetLinuxDevicePreferences`; both Remove choices say saved backups stay). Tests written, not run. Still not built: S4 package search, S6 apps catalog (needs the SSH spike first), S7 full copy, S5 boot restore, S2b native vitals, S8 merged Running pages, and the size estimate before a backup.
- `perf/native-2026-11-01` - removed the `setProcessGroup` spike, fixed the 60-second agent watch that noticed the app's return late (new `VisibilityPacedTimer`), wrote the QA record with the device steps still owed. Kotlin changes were never compiled, so this needs a new release and then emulator proof.
- `feat/gemma-assistant-2026-10-31` - added the Settings row that reaches the page, R8 keep rules so a release build can load the model, the UI-ledger entry, privacy notes and a data-and-deletion table. Open: model download, load, streaming, low-memory unload and the R8 rules are all unproven on a device. A cancel that never answers could hang the native worker.
- `codex/audit3` - merged `int/combined`, then ported the audit fixes (details in section 1, item 18). Dropped the provider-reload fixes because that code no longer exists. The merge resolution, the Kotlin changes and every new test need a real run. Open: PIN-1 (pin the hardened host script) needs a publication step.
- `fix/web-preview-20260913` - re-applied its 9 web commits on today's tree and fixed three web crashes on int (`InternetAddress.tryParse`, a `Platform.isAndroid` read at construction, the on-disk problem report). 24 new copy keys in English and Arabic. Web startup is unproven; dart:io is importable in a browser but throws when used.

Not finished (blocked, so only a note was added on the branch, not merged):

- `feat/appfunctions-2026-10-30` (note commit `03aa16be0`) - the five assistant-callable functions are built and register, but nobody has run them on a well-behaved API 36+ device.
- `feat/landlock-qual` (note commit `10e0a1ab0`) - the Landlock launch policy needs a signed native build and an idle device, and a re-port onto today's launch paths in `BuiltinLinux.kt`, not a rebase.
- `feat/web-shell-20260913` (note commit `78e6eb1d3`) and `feat/web-storage-20260913` (note commit `e1eca7a06`) - both were stale subsets of `fix/web-preview-20260913`. Once the web-preview line was merged, both became superseded (section 3).

Repair pass after verification: five gaps between merged features were found. Four were fixed in `fc8164b8f`. The fifth (`WhatsNewPlaces.openChat` never assigned) is open, see section 5.

## 3. Branches NOT merged, and why

Grouped by status. Names are in backticks so the section 4 list can be checked against this one.

### Merged-equivalent (108 branches)

The work is already in the freeze branch. Either it came in through `int/combined-2026-11-01`, or `int/batch1-2026-11-01` already had it.

Already inside `int/combined-2026-11-01`, which is merged:

`fix/restart-server-after-opencode-update-2026-11-01`, `feat/wifi-share-chat-links-2026-11-01`, `feat/claude-agent-trial-2026-11-01`, `fix/p1-tests-2026-11-01`, `feat/process-budget-2026-11-01`, `perf/dart-2026-11-01`, `test/file-open-native-2026-11-01`, `fix/background-cpu-2026-11-01`, `feat/process-budget-card-2026-11-01`, `fix/pins-2026-10-31`, `fix/paseo-file-open-2026-10-31`, `fix/large-text-2026-10-31`, `fix/free-limit-2026-10-31`

Already in `int/batch1-2026-11-01` (same patch under another hash):

`fix/b4-g1-polish`, `fix/m2-resend-card`, `qa/p1-recheck`, `feat/widget-picker`, `sol/ba-install-cert2`, `fix/aiteam-task-reason-sync`, `fix/aiteam-phone-ready-reconnect`, `fix/aiteam-checker-findings`, `fix/aiteam-phone-status-compat`, `fix/aiteam-run4-merge-projection`, `fix/aiteam-run4-chat`, `fix/aiteam-run4-planner-projection`, `fix/aiteam-run4-default-model`, `build/aiteam-phone-runtime-proof`, `build/aiteam-phone-status-fallback`, `build/aiteam-phone-native`, `build/aiteam-phone-store`, `build/aiteam-phone-gateway`, `build/aiteam-phone-git`, `build/aiteam-phone-protocol`, `build/aiteam-conversation`, `build/aiteam-engine`, `build/aiteam-screens`, `build/aiteam-editors`, `fix/github-draft-release-lookup`, `fix/stable-publication-1043`, `fix/calm-chat-20260913`, `fix/calm-workspace-20260913`, `fix/calm-files-20260913`

AI Team phone-engine fixes whose content is already in the freeze (checked by file content; later work extended them):

`fix/aiteam-proot-objects`, `fix/aiteam-stable-acceptance`, `fix/aiteam-phone-dart-command-refusals`, `fix/aiteam-phone-startup-tier`, `fix/aiteam-phone-attestation-tiers`, `fix/aiteam-phone-dart-boundary-tier`, `fix/aiteam-e2e-syscall`, `fix/aiteam-e2e-startup`, `fix/aiteam-e2e-bundle`, `fix/aiteam-phone-review-dart`, `fix/aiteam-phone-review-repositories`, `fix/aiteam-phone-review-native`, `fix/aiteam-phone-review-storage`, `fix/aiteam-phone-review-attestation`, `build/aiteam-phone-worker-recovery`, `build/aiteam-phone-acceptance-runner`, `build/aiteam-phone-runtime-fix`, `build/aiteam-phone-admission-fix`, `build/aiteam-phone-recovery-fix`, `build/aiteam-phone-dart-review-fix`, `build/aiteam-phone-acceptance-script`, `build/aiteam-phone-x86-native`, `build/aiteam-phone-x86-build`, `build/aiteam-phone-chat-heartbeat`

September 2026 branches carried forward by the snapshot history (cherry shows them as unmerged only because of the 09-18 reset; the files are in the freeze):

`feature/team-101-domain`, `feature/team-102-mappers`, `feature/team-103-fixture`, `feature/team-104-adapter`, `feature/team-105-controller`, `feature/team-106`, `feature/team-107`, `feature/team-108`, `feature/team-109`, `feature/team-110`, `feature/team-111`, `plan/gas-city-plugin`, `feature/session-auto-approval`, `feature/f4-session-handoff`, `feature/f6-launch-surfaces`, `research/gas-town-control-plane`, `feature/e7-fixes-integration`, `fix/e7-readers-tests`, `fix/e7-workspace-tests`, `feature/e7-appearance-preview`, `feature/e7-reader-preferences`, `fix/e7-chat-results`, `fix/e7-library-language`, `fix/e7-setup-language`, `fix/e7-voice-model-language`, `fix/e7-workspace-clarity`, `l10n/e7-arabic-core`, `l10n/e7-arabic-features`, `fix/e7-project-attention`, `feature/e7-locale`, `fix/oc2-setup-integration`, `fix/oc2-switch-progress`, `fix/oc2-managed-switch`, `fix/oc2-server-discovery`, `fix/daily-flow-integration`, `fix/chat-task-controls`, `fix/v2-subagent-contract`, `fix/phone-termux-discovery`, `design/simple-settings-discovery`, `revamp/slice-P0.7`, `revamp/shared-chat-2`, `gate/G33`

One loose end in this group: `gate/G33` added `.githooks/commit-msg`, and that hook file is not in the freeze (the check script `tool/qa/check_commits.sh` is). Keep the branch if you still want the hook.

### Superseded (38 branches)

Replaced by a newer design or by a branch that is merged. Merging any of them would bring old code back.

Old Gas City / Termux-era AI Team, replaced by the in-app AI Team (the TEAM-306 content was merged later, on its own):

`feature/team-301`, `feature/team-302`, `feature/team-304`, `feature/team-117`, `feature/team-116`, `feature/team-115`, `feature/team-207`, `feature/team-203`, `feature/team-205`, `feature/team-204`, `feature/team-202`, `feature/team-201`, `feature/team-206`, `feature/team-114`, `feature/team-112`, `feature/team-113`, `wip/team-306-recovered`, `feature/e7-session-auto-approval`

September 13 "stable" line, replaced by the UX reorganization, phone-setup-v2 and the October launch kit:

`feat/stable-ux-integration-20260913`, `feat/stable-ux-site-20260913`, `feat/stable-ux-chat-20260913`, `feat/stable-ux-setup-20260913`, `fix/stable-ui-20260913`, `fix/stable-team-home-20260913`, `fix/stable-workspace-20260913`, `fix/stable-chat-20260913`, `fix/stable-release-gates-20260913`, `marketing/stable-launch-final-20260913`, `marketing/stable-launch-20260913`

Contained in a branch that is now merged:

`feat/linux-services-core-2026-10-31`, `feat/linux-services-ui-2026-10-31` (both inside `feat/linux-vitals-2026-11-01`), `feat/web-transports-20260913`, `feat/web-shell-20260913`, `feat/web-storage-20260913` (all three are older subsets of `fix/web-preview-20260913`; the two blocker notes on the last two are moot)

Other:

`feat/oc-gaps` (the same Landlock commit as `feat/landlock-qual` on an older base; its doc is already in the freeze), `spike/appearance-system` (first two commits of `spike/liquid-glass-pkg`, replaced by the Style schools), `qa/aiteam-run4-ui` (its one unmerged commit would downgrade the engine .so files), `fix/project-restart-state` (the fix is in; its unverified-workspace banner was removed on purpose)

### Stale-experiment (7 branches)

Never meant for the product line, or the direction was dropped.

`feat/true-liquid-glass`, `feat/liquid-glass-hq`, `spike/liquid-glass-pkg` (glass was removed on 10-26 as "very artificial"), `spike/local-terminal-option-b` (a measurement-only spike), `review/aiteam-builtin` (a one-off review doc for the old runtime), `marketing/live-demo-20260913` (September demo plan and scripts, replaced by `marketing/launch-2026-10`), `heads/v1.0.16+17-patch1` (an old patch off v1.0.16; the feature lives on in a much-evolved form)

### Owner-decision (8 branches)

Each has one exact question. Nothing is lost by waiting.

- `feat/fdroid-2026-10-31` - Do you want to submit the app to F-Droid, and may the main build in every flavor use the new QR scanner that has no ML Kit (it changes pairing everywhere)? Native and pubspec change, so a new release either way.
- `feat/widget-sizes` - When widgets come back (you said 7400 can come later), do you keep the two-widget design already in the freeze (Conversations 4x2 and Status 2x2) and drop this five-size Graphite widget? One small fix on it, `6b91d9ff8` (Linux-reset banner), is not in the freeze and can be cherry-picked alone.
- `feat/agent-browser` - Should the phone agent browser (19 commits, proxy and trust zone, all behind closed gates, with a recorded runtime no-go) ever ship, or stay shelved until the device qualification passes?
- `design/discoverability-options` - Pick direction A, B, C or D for tool discoverability, or none because the 10-20 "no bottom nav" decision already settled it (then archive the doc).
- `feat/telemetry-gate` - Do you want opt-in Aptabase usage telemetry at all? Yes adds a third-party SDK, a Settings switch, consent copy and a deletion sweep; no means the branch can go.
- `codex/host` - Merge undo was left off because servers cannot undo a merge. Do you still want this reversible-merge work (host front, Gas City gateway, Dart models), or should it be dropped? It touches `server_gateway.dart`, a single-owner file.
- `feat/appfunctions-2026-10-30` (blocked on a device) - May the five AppFunctions be proven on your incoming Android 17 phone, or on an idle PC emulator with 6 GB or more free, before this merges into a native release?
- `feat/landlock-qual` (blocked on a device) - Do you approve a signed qualification build and an idle-device run to qualify the Landlock agent launch policy? It changes how agent tools start in built-in Linux, so it must not merge unqualified.

### Backup-or-integration-ref (5 branches)

Keep these. They are history, release lines or inputs to the freeze.

- `int/batch1-2026-11-01` - the base of the freeze branch.
- `int/combined-2026-11-01` - merged into the freeze first. Keep until the freeze is accepted.
- `patch/2.1.0-p1` - the 2.1.0 patch line. Dart-only fixes ship as patches against the exact released version, so keep it.
- `backup/dev-local-20260918` - the old local dev history before the reset onto mobile-next. Many September branches descend from it.
- `backup/pre-reword-20261012` - backup from before a history reword.

### Skipped by the integrator (1 branch)

- `sol/ba-2198-cert` - QA scripts and evidence for the 2198/2199 install certification. The freeze already has the later 2202/2203 line that generalised them (`device_install.py`, `low_storage.py`), and the conflicts in `launch.py`, `run.py` and `uninstall.py` are older versions of the same edits. The merge was aborted. Its docs (`BA-qa-floor-build.md`, the FQ-install2-2199 evidence) can be cherry-picked on their own if wanted. Not on the delete list: its evidence is not anywhere else.

## 4. Branches safe for the owner to delete

Only merged-equivalent, superseded and stale-experiment branches are listed (153 in all). The owner deletes them. Nothing here was run.

Before you start:

- A branch checked out in a worktree cannot be deleted. Remove that worktree first (list below).
- These branches are not ancestors of the freeze, so `git branch -d` will refuse. Use `git branch -D`, or check each one against section 3 first.
- Three branches hold a file that exists nowhere else, and deleting them loses it. All three are low value: `gate/G33` (the `.githooks/commit-msg` hook), `review/aiteam-builtin` (the review document) and `marketing/live-demo-20260913` (the demo scripts). The stale experiments also hold code that exists nowhere else, by design.
- The 22 merged branches, the owner-decision branches, the reference branches and `sol/ba-2198-cert` are deliberately not on the list. After the freeze is accepted, the merged ones become merged-equivalent and can go in the next ledger.

Worktrees to remove first (from the classified data):

- `fix/restart-server-after-opencode-update-2026-11-01` at `/home/eslam/Storage/Code/wt/oc_app-restart-after-update`
- `feat/wifi-share-chat-links-2026-11-01` at `/home/eslam/Storage/Code/wt/oc_app-wifilinks`
- `feat/claude-agent-trial-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-claudeagent`
- `fix/p1-tests-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-p1fix`
- `feat/process-budget-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-procbudget`
- `perf/dart-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-perf-dart`
- `test/file-open-native-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-fileopen-native`
- `fix/background-cpu-2026-11-01` at `/home/eslam/Storage2/wt/oc_app-bgcpu`
- `fix/pins-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-pins`
- `fix/paseo-file-open-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-fileopen`
- `fix/large-text-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-largetext`
- `fix/free-limit-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-freelimit`
- `fix/b4-g1-polish` at `/home/eslam/Storage2/wt/oc_app-b4-g1-polish`
- `fix/m2-resend-card` at `/home/eslam/Storage2/wt/oc_app-m2-resend-card`
- `qa/p1-recheck` at `/home/eslam/Storage2/wt/oc_app-p1-recheck`
- `fix/oc2-managed-switch` at `/home/eslam/Storage/Code/oc_app-oc2-managed-switch`
- `fix/daily-flow-integration` at `/home/eslam/Storage/Code/oc_app-ui-integration`
- `revamp/shared-chat-2` at `/home/eslam/Storage/Code/oc_app/.claude/worktrees/wf_9cdf1571-206-1`
- `gate/G33` at `/home/eslam/Storage/Code/oc_app/.claude/worktrees/wf_90f46939-fa7-1`
- `feat/stable-ux-site-20260913` at `/home/eslam/Storage/Code/oc_app-stable-ux-site`
- `feat/stable-ux-chat-20260913` at `/home/eslam/Storage/Code/oc_app-stable-ux-chat`
- `feat/stable-ux-setup-20260913` at `/home/eslam/Storage/Code/oc_app-stable-ux-setup`
- `marketing/stable-launch-20260913` at `/home/eslam/Storage/Code/oc_app-marketing-launch`
- `feat/linux-services-core-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-svccore`
- `feat/linux-services-ui-2026-10-31` at `/home/eslam/Storage2/wt/oc_app-svcui`
- `feat/web-shell-20260913` at `/home/eslam/Storage/Code/wt/fin-feat-web-shell-20260913`
- `feat/web-storage-20260913` at `/home/eslam/Storage/Code/wt/fin-feat-web-storage-20260913`

Branch lists, one command per group:

Merged-equivalent (108):

```
git branch -D fix/restart-server-after-opencode-update-2026-11-01 \
  feat/wifi-share-chat-links-2026-11-01 feat/claude-agent-trial-2026-11-01 fix/p1-tests-2026-11-01 \
  feat/process-budget-2026-11-01 perf/dart-2026-11-01 test/file-open-native-2026-11-01 \
  fix/background-cpu-2026-11-01 feat/process-budget-card-2026-11-01 fix/pins-2026-10-31 \
  fix/paseo-file-open-2026-10-31 fix/large-text-2026-10-31 fix/free-limit-2026-10-31 \
  fix/b4-g1-polish fix/m2-resend-card qa/p1-recheck feat/widget-picker sol/ba-install-cert2 \
  fix/aiteam-task-reason-sync fix/aiteam-phone-ready-reconnect fix/aiteam-checker-findings \
  fix/aiteam-phone-status-compat fix/aiteam-run4-merge-projection fix/aiteam-run4-chat \
  fix/aiteam-run4-planner-projection fix/aiteam-run4-default-model build/aiteam-phone-runtime-proof \
  build/aiteam-phone-status-fallback build/aiteam-phone-native build/aiteam-phone-store \
  build/aiteam-phone-gateway build/aiteam-phone-git build/aiteam-phone-protocol \
  build/aiteam-conversation build/aiteam-engine build/aiteam-screens build/aiteam-editors \
  fix/github-draft-release-lookup fix/stable-publication-1043 fix/calm-chat-20260913 \
  fix/calm-workspace-20260913 fix/calm-files-20260913 fix/aiteam-proot-objects \
  fix/aiteam-stable-acceptance fix/aiteam-phone-dart-command-refusals fix/aiteam-phone-startup-tier \
  fix/aiteam-phone-attestation-tiers fix/aiteam-phone-dart-boundary-tier fix/aiteam-e2e-syscall \
  fix/aiteam-e2e-startup fix/aiteam-e2e-bundle fix/aiteam-phone-review-dart \
  fix/aiteam-phone-review-repositories fix/aiteam-phone-review-native \
  fix/aiteam-phone-review-storage fix/aiteam-phone-review-attestation \
  build/aiteam-phone-worker-recovery build/aiteam-phone-acceptance-runner \
  build/aiteam-phone-runtime-fix build/aiteam-phone-admission-fix build/aiteam-phone-recovery-fix \
  build/aiteam-phone-dart-review-fix build/aiteam-phone-acceptance-script \
  build/aiteam-phone-x86-native build/aiteam-phone-x86-build build/aiteam-phone-chat-heartbeat \
  feature/team-101-domain feature/team-102-mappers feature/team-103-fixture feature/team-104-adapter \
  feature/team-105-controller feature/team-106 feature/team-107 feature/team-108 feature/team-109 \
  feature/team-110 feature/team-111 plan/gas-city-plugin feature/session-auto-approval \
  feature/f4-session-handoff feature/f6-launch-surfaces research/gas-town-control-plane \
  feature/e7-fixes-integration fix/e7-readers-tests fix/e7-workspace-tests \
  feature/e7-appearance-preview feature/e7-reader-preferences fix/e7-chat-results \
  fix/e7-library-language fix/e7-setup-language fix/e7-voice-model-language fix/e7-workspace-clarity \
  l10n/e7-arabic-core l10n/e7-arabic-features fix/e7-project-attention feature/e7-locale \
  fix/oc2-setup-integration fix/oc2-switch-progress fix/oc2-managed-switch fix/oc2-server-discovery \
  fix/daily-flow-integration fix/chat-task-controls fix/v2-subagent-contract \
  fix/phone-termux-discovery design/simple-settings-discovery revamp/slice-P0.7 revamp/shared-chat-2 \
  gate/G33
```

Superseded (38):

```
git branch -D feature/team-301 feature/team-302 feature/team-304 feature/team-117 feature/team-116 \
  feature/team-115 feature/team-207 feature/team-203 feature/team-205 feature/team-204 \
  feature/team-202 feature/team-201 feature/team-206 feature/team-114 feature/team-112 \
  feature/team-113 wip/team-306-recovered feature/e7-session-auto-approval \
  feat/stable-ux-integration-20260913 feat/stable-ux-site-20260913 feat/stable-ux-chat-20260913 \
  feat/stable-ux-setup-20260913 fix/stable-ui-20260913 fix/stable-team-home-20260913 \
  fix/stable-workspace-20260913 fix/stable-chat-20260913 fix/stable-release-gates-20260913 \
  marketing/stable-launch-final-20260913 marketing/stable-launch-20260913 \
  feat/linux-services-core-2026-10-31 feat/linux-services-ui-2026-10-31 feat/web-transports-20260913 \
  feat/web-shell-20260913 feat/web-storage-20260913 feat/oc-gaps spike/appearance-system \
  qa/aiteam-run4-ui fix/project-restart-state
```

Stale-experiment (7):

```
git branch -D feat/true-liquid-glass feat/liquid-glass-hq spike/liquid-glass-pkg \
  spike/local-terminal-option-b review/aiteam-builtin marketing/live-demo-20260913 \
  heads/v1.0.16+17-patch1
```


## 5. Open risks

1. Nothing was executed. The freeze branch has gone through the analyzer only. The full serial suite has not run on it and has taken 27 to 40 minutes before. Run it once at the next gate, in chunks.
2. Native code was never compiled. New or changed Kotlin and native pieces that came in with the merges: the clipboard bridge (paste-image), `AgentPriority` and `ServiceNoticeGate` (perf/native), `LocalLlmBridge` and the LiteRT-LM 0.19.0 dependency (gemma), the installer channel (app-updates), the Linux services supervisor (linux-vitals, 7.9k lines), and `BuiltinLinux.kt`, `MainActivity.kt`, `ProjectExportBridge.kt` and `TermuxActivityPermissions.kt` (codex/audit3). All of it needs a new Shorebird release, not a patch, and a device run.
3. Duplicate permission. `REQUEST_INSTALL_PACKAGES` is declared twice in `android/app/src/main/AndroidManifest.xml` (lines 6 and 41: app-updates and the local-file open). I could not confirm the manifest merger accepts it. Drop one copy.
4. Goldens will probably move. The findings card now uses a kit choice mark instead of the Material checkbox (`kit_findings_card` and `test/goldens/team/kit_teamprojects_findings_*.png`). About lost its update row and gained star-share rows (`test/revamp/fg6_fewer_pages_golden_test.dart`, `test/revamp/slice_p310_settings_ia_golden_test.dart`). The role-voices `kit_message` golden may need a regen. The Remove sheets should not change unless a backup was saved.
5. What's new is half wired. `WhatsNewPlaces.openChat` is never assigned, so the paste-image and hold-to-talk steps show text but no "Show me". `whatsNewByVersion` only has `2.2.0` while the app is 2.1.0+57, so the sheet will not open by itself after an update until the version is bumped; About > What's new still opens it.
6. A real bug was found at the merge. The paste-image commit had broken the long-press menu in every text field. It is fixed in `92ed57977` but only analyzed. Check on the emulator after the freeze: long-press in any text field, then paste a picture in the composer.
7. Hand-resolved conflicts to re-read. `build/aiteam-kit` had 253 add/add conflicts (kept the freeze's versions and re-applied its two new commits; the 11 new "changes:" motion scenarios have not run). `feat/linux-vitals` touched `builtin_server.dart` and `thermal_guard_teams.dart`. `feat/app-updates` touched `about_screen.dart`. `feat/gemma-assistant` touched `AndroidManifest.xml`, and the UI ledger was rebuilt because the merge driver dropped its entry.
8. Browser mode is a decision, not just a merge. A password can only be saved on HTTPS or localhost, so the Tailscale preview at `http://100.64.0.10:8789` works only for servers without a password. Question for the owner: is a browser build a product target, and if so which HTTPS hostname or reverse proxy hosts it (it also needs the server's CORS allow-list)? Web startup has never been booted.
9. Gemma assistant. The model is about 3.7 GB, and `LocalLlmBridge.run` waits on a latch with no timeout, so a cancel that never answers could block the next load. The R8 keep rules are unproven in a release build. A phone under about 5.4 GB of RAM is not offered the download.
10. codex/audit3 launch path. The new `startServerForLaunch` keeps the owner claim and reads the native intent generation after it. This was reasoned from the code, not exercised. The PIN-1 item (pin the hardened host script commit and SHA-256) is still open and needs an explicit publication step.
11. perf/native. All heat, battery-saver and priority behavior is implemented but unverified. The device steps are in `docs/qa/perf-native-2026-11-01/README.md`.
12. Small losses by redesign, low severity: the Termux running-server row no longer shows "Last checked at" or a Check status button (the state is re-read on resume, on Connect and after Start, Restart and Stop).
13. Work that exists only on unmerged branches: the Linux-reset banner fix `6b91d9ff8` (on `feat/widget-sizes`), the 2198/2199 certification docs (`sol/ba-2198-cert`), and the blocker notes on `feat/appfunctions-2026-10-30`, `feat/landlock-qual`, `feat/web-shell-20260913` and `feat/web-storage-20260913`.
14. Widgets: the request said 7400 for widgets can come later. No widget work is in this freeze.

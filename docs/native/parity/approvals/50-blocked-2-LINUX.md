RESULT: APPROVED WITH FIXES

# Approval: job 50-blocked-2-LINUX (Linux migration, second pass)

Branch `native/p12x/job-50-blocked-2-LINUX`. Reviewed: the job's non-merge commits over
`origin/native/p12-head`: `f9a9ead2` (process stops), `06683ff7` (terminal controls, storage
scan progress), `90abb646` (optional voice setup, Termux migrate-out). Report:
`docs/native/parity/migration/50-blocked-2-LINUX.md`. No tests were run and no APK was built
(owner: coding first). Android compile happens on the build VPS after this review.

## Merge

`git merge --no-ff origin/native/p12-head` (subject `merge: P12 integration head into
50-blocked-2-LINUX [skip ci]`) brought in one P12 commit (`fe2ef020`, a line in
`docs/native/units/INTEGRATE-P12.md`). There were no conflicts and nothing from either side was
dropped.

## Review

There are 29 rows: 15 built now, 3 already there, 11 blocked.

**Blocked rows (227, 229–232, 241–244, 246, 247)** are honest. Each one would bring back active
Termux management: install, setup, recovery, runtime switch, cleanup or the AI Team flow. ADR D7
(plan/01, migrate-out only) and D6 (AI Team after the switch) exclude that. The Termux binding
added by this job only exposes the migrate-out commands: `TermuxBridge.run` is private to
`AndroidTermuxMigration` and only reached from `BridgeTermuxMigrationTransport`. No manager or
setup dispatch can be reached.

**Already there (185, 221, 222)** checked against the code: the component inventory and the
removal re-check in `LinuxComponentsPanel`/`ComponentRemoval` are there. So are the storage
access request in `SharedFolderWiring` and the binds in `BuiltinLinux`. `Oc2Terminals.removeTerminal`
maps only `NotFound` to success.

**Built now: real and reachable.**
- 239: native process groups now stop by a frozen PID→start-tick tree. Protected PIDs are
  filtered out, and the native `stopProcesses` ownership/TERM-KILL path is still the authority.
  The parent-gone and CPU-without-owner reasons are localized (EN/AR).
- 223/224: input is batched per channel in order and dropped on channel loss, with no retry. A
  local queue lives at the native owner and is flushed before direct IME/hardware keys.
  `TerminalKeys` matches `test/terminal_key_bar_test.dart`: Ctrl letters and symbols, `?`→DEL,
  Esc first for Alt, one-use latches, cursor-mode arrows, modified arrows, page keys.
  `KitTerminalKeyBar` was added to the kit and is used by both terminals.
- 225: `rerunTerminalCommand` sends a single `POST /pty` with command/args/cwd/title under the
  location query. It is refused before sending when the location is stale or the snapshot is
  running, empty or invalid. A failure shows the fixed "Could not confirm the rerun" notice and is
  never retried. Exposed only when the gateway has the facet.
- 226: source switching between the built-in terminal and server terminals. A nameless server is
  called "OpenCode server", and a runtime suffix is added when a name collides.
- 251: `SgrNormalizer` runs before every emulator append, and the remote terminal now builds the
  real `AndroidRemoteTerminalRenderer` (it is in the `@Multibinds` set) instead of a null view.
- 248: the built-in storage scan runs one stage per category with authored `::storage-stage`
  markers only. Status and a bounded log are shown. GB figures keep one decimal.
- 220: voice is an app-side `SetupAppComponent` on the shared `VoiceHub` model manager. It is
  off by default, probed before Linux, and runs as a durable runner step. It resumes after the
  app is killed (a refresh on resume restarts the step), and cancel stops the download. Failure
  codes map to localized errors. Removal goes through `ComponentRemoval.apps`. The Linux Home
  row is the visible way to add it.
- 228, 233–238: `AndroidTermuxChannel` uses real RUN_COMMAND with an explicit-component
  PendingIntent callback, reusing the git-repair callback service. The bridge does the single
  shutdown retry. The controller, private archive store, KVS journal on the profile lane,
  conservative space budget, profile switcher, authenticated transport and done/sign-in pages
  are all bound. The flow is reachable through `LinuxRoute.Migration` from This phone and from
  Linux Home. The `oc.termuxMigration.<id>` and `oc.termuxMigrationOffer.<id>` keys are
  registered for the deletion sweep, and the `ProfileCloser`/`ProfileResourceCleaner` hooks
  drain the work and clean up partial files. Every `MigrationWord` name exists in
  `LinuxMigrationWord`, so `valueOf` cannot throw, and every word maps to a resource.
- Desktop gets inert bindings (`DesktopLinuxMigration`, `DesktopPhoneTermuxMigration`) because
  RUN_COMMAND only exists on Android. This is a platform absence, not an Android stub.

**Security and privacy:** no credentials reach logs or UI. The built-in profile password comes
from `SecureRandom` and goes through `SecretEdit.Set`. Termux errors are mapped to fixed problem
kinds, and the scan log only shows authored stage names. No new external URL is opened. The
settings and Termux intents are app-authored.

**UI** is kit-only: KitRow, KitNotice, KitButton, KitSwitchRow, KitConfirm, KitLogPanel,
KitProgressView and the new kit `KitTerminalKeyBar`. All copy is in string resources, in English
and Arabic.

## Fixes made (commit `cc9e94d9`)

1. **B-LINUX-224 strings.** The backslash key label is now `\\`, the documented Compose
   resources escape for `\`. The Arabic Control/Alt/Esc/Tab/Home/End labels now use the Latin key
   names, as Flutter's Arabic bar does. Before this, "خروج" (exit) was used for Esc and "جدولة"
   for Tab.
2. **B-LINUX-236 sizes.** The migration review's required/free space and the item sizes
   truncated to whole units (1.9 GB showed as "1 GB") and had no bidi isolate. They now follow
   Flutter `formatPhoneStorage`: one decimal above a kilobyte, inside an LTR isolate.
3. The report has an "Approval review fixes" section that records both fixes.

## Notes (not blocking)

- Native group stops now use the native frozen-tree path, and `RunawayWatch.stopped` is no longer
  recorded for groups. The runaway notice still clears on the next scan once the process is gone.
- On the migration page, every disabled button gives "Choose at least one" as its reason. That
  reason only fits Start.
- The job changed shared modules (kit-content, words, routes, core domain) additively, the same
  way earlier LINUX jobs did. The integrator should keep that in mind when merging.
- Type and import correctness was checked by reading the code. Android compilation is still
  pending on the build VPS.

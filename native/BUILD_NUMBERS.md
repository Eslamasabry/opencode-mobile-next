# Build numbers (versionCode ledger)

One counter and one ledger for every APK of the main package `io.github.eslamasabry.opencode_mobile` and its preview
twin `.native` (ADR §9.9, plan 05 §9.1, plan 15 §2.6, E01-S09). Android refuses an `adb install -r` with a lower
versionCode, and the only way past is an uninstall that loses the user's data. So every native build takes a number
above **every** number ever hosted or installed on the owner's phone or an emulator, and no number is handed out twice.

Use `native/tool/version_code.sh` only; never edit `native/version.properties` or a row below by hand.

| Need | Command |
|---|---|
| Next number, nothing written | `tool/version_code.sh next [--kind build\|release] [--installed N]... [--device SERIAL]...` |
| Take a number (row + `version.properties`) | `tool/version_code.sh allocate --kind build\|release --for "<what>" [--installed N]... [--device SERIAL]...` |
| Record a number seen on a device or the share server | `tool/version_code.sh seen --code N --for "<where, what>"` |
| Check the ledger and `version.properties` | `tool/version_code.sh check` |

- **release** (owner candidates, public releases): the next multiple of 10 above the largest number known, so a release
  after a release is +10 (D-02 in `docs/native/release-identity.md`) and an R1 rollback (largest + 1) still fits below
  the next one. With the dev line at 10004 the first delivered native build is **10010**, as that page says.
- **build** (agent and emulator proof builds, the dev line in `version.properties`): the largest number known + 1.
- `--installed N` / `--device SERIAL` (read-only `adb shell dumpsys package`) add what a device holds right now; a
  number found there above the ledger is recorded as a `seen` row first, and the new number goes above it.
- A number in the ledger is never handed out again, whatever its kind, `retired` included.
- Two branches that allocate at the same time collide on the same row: the merge conflict is the signal, and the
  coordinator re-allocates on the merged tree (`check` refuses a number used twice).

## Floor (decided 2026-11-06, E01-S09)

- **10000** is the floor (ADR D3); every native number is also above every `seen` row: the released Flutter app
  (2.1.4+62), the owner's phone (Flutter test builds up to 4610 on 2026-11-06, release-identity page) and the native
  previews already on emulators (10000–10003).
- **The emulator AVDs holding 900055 and 900058 are wiped instead.** Those two numbers are Flutter builds from the VPS
  signed with the local key and installed only on emulator AVDs (plan 11 CR11-18, `docs/qa/scheduled-prompts-2026-10-15/apk-build.txt`).
  Staying above them would push every native number to 900060+ and break the owner's 10010 / +10 numbering
  (release-identity page, D-02), for lab devices that hold no user data. So they are `retired`: they stay in the
  ledger, are never reused, and do not raise the floor. Before the next native install on an emulator, the emulator
  lane holder checks the installed code (`tool/version_code.sh next --device emulator-NNNN`: a higher number shows up
  there) and, if 900055 or 900058 is present, uninstalls that package on the AVD or uses a fresh AVD. The owner's phone
  is never wiped: it holds 4610 or less, and every native number is above that. `t3.sh` already refuses a downgrade
  on an emulator, so an AVD that was missed fails loudly instead of installing.
- Plan 05 §9.1 says the allocator never plans an uninstall. That rule protects user data and still holds for every
  phone; the two retired numbers concern lab AVDs only. Plan 05/11/15 text that says "above 900058" is superseded by
  this decision for the coordinator to fold back into the plan.

## Ledger

Rows are in the order numbers were taken. `seen` rows are numbers this tool did not hand out but that exist on a device
or server; they raise the floor and may be below 10000 (Flutter builds). `retired` rows are numbers whose devices are
wiped (see Floor); they never come back and do not raise the floor.

| versionCode | kind | date | for |
|---|---|---|---|
| 62 | seen | 2026-11-06 | Flutter public release 2.1.4+62 (CI lineage), the released Flutter app |
| 4610 | seen | 2026-11-06 | Flutter test builds on the owner's phone, up to 4610 (docs/native/release-identity.md) |
| 10000 | seen | 2026-11-02 | native preview `3.0.0-dev+10000` (REPAIR-INTEGRATION-2026-11-02), installed on emulators |
| 10001 | seen | 2026-11-02 | native preview 10001 (drafts-2026-11-02), emulator |
| 10002 | seen | 2026-11-02 | native preview 10002 (server-errors-2026-11-02), emulator |
| 10003 | seen | 2026-11-03 | native preview and switch 10003 (migration-proof-2026-11-03, wave4), emulators |
| 900055 | retired | 2026-10-15 | Flutter VPS build, local key, emulator AVD only; AVD wiped before native installs (Floor) |
| 900058 | retired | 2026-10-15 | Flutter VPS build, local key, emulator AVD only; AVD wiped before native installs (Floor) |
| 10004 | build | 2026-11-06 | native dev line (internal builds), first number the allocator handed out |
| 10010 | release | 2026-11-07 | E01-S01 switch release 3.0.0: first native release number (native/s15-release) |
| 10020 | release | 2026-11-10 | native release 3.0.0+10020 (owner lineage) |
| 10021 | build | 2026-11-14 | owner takeover build ca1e857d4 |
| 10022 | build | 2026-11-14 | owner test build 1f130e542 (edge light branch) |
| 10030 | release | 2026-11-14 | native release 3.0.0+10030 (owner lineage) |
| 10031 | build | 2026-11-14 | owner fix build of 0e4be570e (fix-only branch over release 10030) |
| 10032 | build | 2026-11-14 | owner fix build of 11d62865c (release 10030 + open-while-starting incl. the Claude Code conversation fix) |
| 10035 | build | 2026-11-14 | owner build of 679a414bc2 (10032 + Paseo 0.15.4, all agent pins, automatic agent update, visible loading) |
| 10038 | build | 2026-11-14 | owner build of e5c7bf25c5 (agent update survives a stop and resumes; agent conversations wait; home says what is starting) |
| 10039 | build | 2026-11-14 | owner build of 8716950331 (home says agents updating, sign-in said, Antigravity counts as behind, versions shown) |
| 10040 | build | 2026-11-14 | owner's build of e0c4f0673e (10039 + layout crash stop-gap + transcript row crash fix) |
| 10041 | build | 2026-11-15 | owner's build of 93808ba1a9 (the whole working line: agents list, step list, running work, animations back, crash fixes) |
| 10042 | build | 2026-11-15 | owner's build of 8bd1e053f7 (10041 plus the loading skeleton's prompt bubble) |
| 10043 | build | 2026-11-15 | owner's build of 0e0f2a721f (10042 plus the phone agents' conversation identity fix and the load-failure page naming the agent) |
| 10044 | build | 2026-11-15 | owner's build of b117326a86 (set-up first, one operation for phone agents, problem report build info and folded repeats) |
| 10045 | build | 2026-11-15 | owner's build of d3716893f7 (a phone agent's start not blocked by a left start; failed-send layout; stale refusals name their check) |
| 10046 | build | 2026-11-15 | owner's build of 162c446ec2 (issue 147 wave, first batch) |
| 10047 | build | 2026-11-15 | owner's build of 3d5f4d2677 (issue 147 wave, second batch) |
| 10048 | build | 2026-11-15 | owner's build of ebb0a33b37 (issue 147 wave, third batch) |
| 10049 | build | 2026-11-15 | owner's build of 00fc35f5a7 (issue 147 wave, fourth batch) |
| 10050 | build | 2026-11-15 | owner's build of b0ecdd9e6f (issue 147 wave, fifth batch; its commit passed full CI) |
| 10051 | build | 2026-11-16 | owner's build of cc50a34105 (storage folder kept after a helper restart, schedules, release gate; CI 43767355235 passed) |
| 10052 | build | 2026-11-16 | owner's build of f8229d2b43 (phones upright, edge light under sheets, Review Done, Find and long-chat cost; CI 43768070767 passed) |
| 10053 | build | 2026-11-16 | owner's build of f53772b5f7 (New conversation layout B and walker fixes on top of 10052; CI 43775330858 passed) |
| 10054 | build | 2026-11-16 | owner's build of 9713c1c40e (open-transitions, storage both-sides restart, edge light list, a11y guards; CI 43779221372 passed) |

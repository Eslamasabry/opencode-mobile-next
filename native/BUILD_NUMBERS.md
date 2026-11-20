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
| 10055 | build | 2026-11-16 | owner's build of f1ccc2db57 (sign-in truth: a usage limit no longer reads as signed out; CI 43782239875 passed) |
| 10056 | build | 2026-11-16 | owner's build of a19b8f0c31 (black frame on Home -> +, Plan holds on phone agents, Home top without a pane, sign-in follow-ups, compact width 2; CI 43788143805 passed) |
| 10057 | build | 2026-11-16 | release candidate for 3.0.0+10060: the owner's build of the commit that records this number (privacy wording made true, plus every release item merged by 06:45) |
| 10058 | build | 2026-11-16 | release candidate for 3.0.0+10060 (supersedes 10057): the owner's build of the commit that records this number (helper autostart fix on top of 10057, release notes updated) |
| 10059 | build | 2026-11-16 | final release candidate for 3.0.0+10060 (supersedes 10058): the owner's build of the commit that records this number (10058 + connecting-alive, schedules-migrate, server-refresh-metered, smoke script fix) |
| 10061 | build | 2026-11-16 | final release candidate 2 for 3.0.0+10070 (supersedes 10059): the owner's build of the commit that records this number (10059 + launch-timeline, agent-open-flash, notes as 3.0.0+10070) |
| 10062 | build | 2026-11-16 | owner's try build (branch native/speed-try, NOT the release candidate): 4af56a8082 + speed-a + speed-b, built from the commit that records this number |
| 10063 | build | 2026-11-16 | owner's try build (branch native/home-starting, NOT the release candidate): 10062 + a self-run server that is off raises no Home notice, a slow start reads starting, phone-agent rows show the working mark; built from the commit that records this row |
| 10064 | build | 2026-11-16 | owner's try build (branch native/home-starting, NOT the release candidate): 10063 + step words on the chat page's waiting line and on Home's Recent line; built from the commit that records this row |
| 10070 | release | 2026-11-16 | native release 3.0.0+10070 (owner lineage) |
| 10071 | build | 2026-11-16 | owner's test build of 4c341041a1 (3.0.1 wave 1: one name for This phone, plan holds, small-screen labels, schedules from 2.1.5, first read while the server starts, reply headings, Back to the exact line, haptics) |
| 10072 | build | 2026-11-17 | PROOF build for a clean cloud phone, not the owner's build: the first-run fix (native/first-run-start 9c3a95e761, This phone's readiness probe on direct loopback) on a side branch, cut from master 13ec57a919; not merged |
| 10073 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip 05234dc9d1 (waves 1 and 2, cut3, versions, first-run, clipboard, usage, own analytics, site text, security gates), owner key |
| 10074 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip b8d4cdf93d (10073 plus sec-gates release-script follow-up, stream-reconcile B-435 part A, Enter makes a new line), owner key NOT DELIVERED: the compile on the build server hit the script's 3000 s limit (server load 10-12), no APK was made; superseded by 10075 |
| 10075 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip 14d4352bc0 (b8d4cdf93d plus quiet-line: the waiting line keeps moving while the link is live), owner key; replaces undelivered 10074 |
| 10076 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip 7fcb2ffe8f (10075 plus enter-newline follow-ups, expired sign-in wording, model chip says the effort once, Chinese words completed), owner key |
| 10077 | build | 2026-11-17 | interim 3.0.1 line, not the candidate; quick lane 44079402632 not a pass: one unexplained failure in core/net TransportTrustTest B-SEC-007 (code untouched); line tip 8171925527 (sub-minute, chat page floats), owner key |
| 10078 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip 2b3cef7cd3 (10077 plus the B-SEC-007 close-crash fix, guard-skips tools, code-style fixes), owner key NOT DELIVERED: the compile on the build server stopped with "No space left on device" (no APK was made); to be built again under a new number or this one once the server has room |
| 10079 | build | 2026-11-17 | interim 3.0.1 line, not the candidate; first build with the crash fix: line tip 2b3cef7cd3 (10077 plus the B-SEC-007 close-crash fix, guard-skips tools, code-style fixes); replaces 10078 (not delivered), owner key |
| 10080 | build | 2026-11-17 | interim 3.0.1 line, not the candidate: line tip b8395c4c7f (10079 plus Japanese complete, candidate-lane tools, code-style ratchet, line fixes), first build with Japanese complete, owner key |
| 10081 | build | 2026-11-18 | interim 3.0.1 line, not the candidate (source 7b4096f918) |
| 10082 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: clipboard crash fix (source eaa11ac005) |
| 10083 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: mic in the message box + chat page edge fixes (source 3c761208fb) |
| 10084 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: silence in dictation + notice row at large text (source 1de9de53ce) |
| 10085 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: voice privacy pages + header capsule corners (source 3aabf1d837) |
| 10086 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: sign-in page confirms the sign-in B-454 (source 2a783d768a) |
| 10087 | build | 2026-11-18 | look build for next-update branches b2-modelorder + b8-approvalcard, not the line (source 34353bff1a, native/listb-look-1) |
| 10088 | build | 2026-11-18 | look build for next-update branches rich-analytics + b7-dailynews, not the line (source 179b82237f, native/listb-look-2) |
| 10089 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: soft ends at the capsule and message box corners (source 1ae291912b) |
| 10090 | build | 2026-11-18 | NOT BUILT: reserved for the next interim (to carry sign-in polish, plus-light, notify-signin-again, text-scale follow-up) on the coordinator's word; no APK exists for this number |
| 10091 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: sign-in page polish (signin-polish 97ba44edb3) + light round Home's plus (plus-light 689c6c744f) + notification names the agent to sign in again (notify-signin-again 1c68e24add); text-scale follow-up NOT in (source 57085ae213) |
| 10092 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: approval-stays 268569150a (B-459, whole-pixel snap) + font-scale-audit 92f688c3be + signin-clears-turn 7be47b9bb2 (B-458), on top of 10091's signin-polish, plus-light, notify-signin-again (source 783f60ea56) |
| 10093 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: the build for the owner: chat-edges-flow c467fa1444 + privacy-words 7ecf132333 on top of 10092's approval-stays, font-scale-audit, signin-clears-turn (source 4261c2e440) |
| 10094 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: voice-conversation-door c38a82213b (B-461) on top of 10093's content (source 09ba5d9698) |
| 10095 | build | 2026-11-18 | look build for the next-update branch b7-dailynews with web reads, not the line (source 3ccd091814, native/listb-look-3) |
| 10096 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: chat-edges-fix2 504850c7a9 (bottom edge of the chat page and the pinned row) on top of 10094's content (source 6f42ba9b85). 10096 given to the owner by the coordinator, known faults named |
| 10097 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: chat-edges-fix2 up to 4562c7a6d9 (request card max 40% at 2x, no-break sizes, voice text wraps) + font-scale-2 caa998b430, on top of 10096 (source 2259fec99c) |
| 10098 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: queue-survives 7418c79e33 + chat-edges-fix3 a4a5a9cba6 (pinned row hairline) + unanswered-not-run d1e650debc, on top of 10097 (source 605c95e184) |
| 10099 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: navbar-transparent 715de65e3e (navigation bar transparent on every Android version) on top of 10098 (source 1bc64c8e91). 10099 given to the owner by the coordinator, known faults named |
| 10100 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: chat-edges-fix4 e1644ad6c1 (floating parts' fills, request card at the measured room, plus-sheet scroll, bottom fade, steering wording) on top of 10099 (source 2994536a0c) |
| 10101 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: new agent versions for the device proof: OpenCode 2.3.2, Claude Code 2.1.327, Codex 0.170.0, Antigravity 1.5.11, Paseo 0.16.1, Node v24.22.3 (pins-301-r2 613e54535a) + release-gates + dock Stop + PR 149 + plus-sheet-fix (source de136b17e8) |
| 10102 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: 10101's agent versions + typing-keeps-words f5b8f678e3 (message box keeps fast-typed words) (source 59f8d5da2f) |
| 10103 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: 10102's content + colour marks and follow-up + typing-sent-not-back + child-needs-you f642cb12cb (source 0bda61114d) |
| 10104 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: 10103's content + opencode-hold-told, child-needs-you-2, update-path-unstuck, wait-marks-r2, install-switch-safe (source 26ada28b75) |
| 10105 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: 10104's content + opencode-update-moves, gate-k1-fix, gate test files, update-low-space-r2, umami-language-words (source 463a4524c5) |
| 10106 | build | 2026-11-18 | interim 3.0.1 line, not the candidate: 10105's content + sol-trial-301, update-backoff, umami-rich-301, sol-round2-301, opencode-retry-wakes (source 7d49f81fa3) |
| 10107 | build | 2026-11-20 | interim 3.0.1 line, not the candidate: everything since 10106 (source 470cbdfbca) |
| 10108 | build | 2026-11-20 | interim 3.0.1 line, not the candidate: update-sheet-shows, undo-truth-301-r2, crash-fixes-301-r2, reply-path-absolute-r2, root-file-chip-r2, thinking-first-newconv (no Italian) on top of 10107 (source ce09d2dc7b) |

# Development handover

Branch: `dev`, upstream `mobile-next/dev`. Continue in the existing checkout.
Read `AGENTS.md` and `CONTRIBUTING.md` before running tools.

## Latest (2026-09-11, later): AI Team · Gas City, Sprint B

`dev` carries local checkpoint **1.0.42+43**: Sprint B (TEAM-201 … 207)
on top of Sprint A — the host front, decisions with receipts,
notifications, agent/run controls, Start a run, merge, policy display —
all merged with tests (full suite 3590 green). Evidence in
`docs/qa/ai-team/` (read proof, write proof, spike reports).

- Live on this PC: supervisor `127.0.0.1:8372` (city `bright-lights`,
  lean profile) and the front `http://100.101.102.103:8373` (allow
  owner@example.com; state in `/home/eslam/Storage/Code/gascity-spike/front-state`).
  In the app: Settings › Plugins › AI Team › Add manually →
  `http://100.101.102.103:8373`, city `bright-lights` (the front is found by
  discovery too).
- Next: Sprint C on-device (TEAM-301..303 with the hybrid native layout
  from spike-phone §3f, plus the owner's TEAM-304 Storage and TEAM-305
  Running-now screens for the Termux server manager); emulator captures
  for the QA index; a `--formula` run recording; upstream reports (Gas
  City futex/seccomp, fixed 30 s ACP handshake).

## Earlier (2026-09-11): AI Team · Gas City plugin, Sprint A

`dev` carries local checkpoint **1.0.41+42**: the read-only first slice of
the "AI Team · Gas City" plugin (TEAM-101 … TEAM-114 in
`docs/plans/gas-city-plugin-2026-09-10/05-beads.md`), all merged with
tests, plus the two spikes.

- Plan and decisions: `docs/plans/gas-city-plugin-2026-09-10/` (06 has the
  owner decisions and the gate status; §F–H record what changed).
- Evidence: `docs/qa/ai-team/README.md` (spike reports, recordings, read
  proof, suite table). Host guide: `docs/ai-team-host.md`.
- Not yet: writes (Sprint B: front, decisions, controls, merge), the
  phone-hosted team (Sprint C: hybrid native layout is proven on the
  emulator — polecat committed and pushed — and mid-test on the phone;
  see `spike-phone-2026-09.md` §3f), notifications, emulator screenshot
  captures for the QA index, a `--formula` run recording.
- To try it: run Gas City on the PC per the host guide (city `bright-lights`
  in `/home/eslam/Storage/Code/gascity-spike/city2`, forwarder
  `tool/host/tailnet_proxy.py`), then Settings › Plugins › AI Team › Add
  manually with `http://100.101.102.103:8372` and city `bright-lights`.

## Earlier (2026-09-10, evening): backlog sprint + BRD research

On top of the E7 checkpoint below, `dev` now carries local checkpoint
**1.0.40+41** with the open backlog built and a research sprint for the
"AI Development Control Plane" BRD:

- **Session auto-approval with subagent inheritance** —
  `docs/qa/session-auto-approval/README.md`.
- **F4 session handoff** (Continue on computer / Continue on phone QR +
  `opencode-mobile://session` deep link) — `docs/qa/f4-session-handoff/`.
- **F6 launch surfaces** (pinned-session shortcuts, Quick Settings tile;
  F6-S3 voice deliberately deferred) — `docs/qa/f6-launch-surfaces/`.
- **Background subagent proof** against a real OpenCode 2 beta-18600 server
  and a live model, plus four bugs it exposed, all fixed with tests —
  `docs/qa/background-agent-proof/README.md`.
- **Control-plane research** — `docs/research/control-plane-2026-09-10/`
  (README, findings, architecture proposal, 3-sprint plan, open questions).
  Headline: target **Gas City** (typed HTTP+SSE supervisor API), not Gas
  Town; start with a read-only `OrchestrationGateway` slice behind a
  fixture server; the owner must answer `open-questions.md` first.

Still unverified on a physical phone: Arabic locale, launcher shortcuts,
the Quick Settings tile, scanning the handoff QR. Wireless ADB on the phone
was off, so the emulator carried this checkpoint.

## What landed earlier on 2026-09-10 (E7)

**E7 — Arabic and RTL — plus the "all fixes" batch** from the 2026-09-09
ten-worker swarm is integrated on `dev`. The Codex root session that drove
the swarm ran out of context before it could commit; every worker's
uncommitted state was checkpointed on its own branch and merged by the
integration record in [docs/qa/e7-fixes/README.md](docs/qa/e7-fixes/README.md).

- Settings › Appearance has a **System / English / Arabic** language picker
  that persists globally, updates locale and direction without losing the
  route, and survives restart. Arabic is a complete catalog (3418 keys),
  generated from reviewed fragments by `tool/assemble_arabic_arb.py`.
- Every app-authored string in chat, workspace, files, review, library,
  setup, Termux, voice and settings goes through `AppLocalizations`. Code,
  paths, URLs, model names and server content stay LTR and untranslated.
- Fixes from the backlog: canonical background-result cards, one Workspace
  inventory notice instead of repeats, grouped chat menu (`CHAT-08`),
  remembered source-first ordering and code wrap (`FILES-08`/`REVIEW-08`),
  real component preview with explicit Apply in Appearance (`SETTINGS-09`),
  project/attention/monitor localization, 320dp × 2.5x layouts for every
  slice.
- Composer feedback: the "sends after this run" hint and the OpenCode 2
  steer/queue toggle show only while a run is active **and** text is typed;
  OpenCode 1 says plainly that steering needs OpenCode 2.

## Historical E7 verification checkpoint

Pinned Flutter 3.47.2 at `~/.shorebird/bin/cache/flutter/e16cf749…/bin/flutter`
(Dart 3.13.2, JDK 17, Android SDK API 37).

```sh
flutter pub get
flutter analyze            # No issues found
flutter test --concurrency=4
(cd packages/opencode_sdk && dart analyze && dart test)
```

The historical commands above used concurrency 4; that is not the serial
integration gate required by `AGENTS.md` and does not validate later source.
Historical results are recorded in `docs/qa/e7-fixes/README.md` and its suite
summary. For current candidates, use the recursive runner described in
`tool/qa/README.md` with `--concurrency=1` inside each bounded chunk. Retain its
source fingerprint and resume only unfinished chunks of the unchanged source.

Not yet verified: real Arabic locale on a physical ARM64 phone, TalkBack,
device-generated Material You colours, and a provider-backed background agent
returning its result to the correct parent (the P1 item from
`docs/qa/oc2-setup/README.md`). Earlier Android checks of OC1 ⇄ OC2 switching
still stand.

## Remaining product limits

- Steering mid-run is an OpenCode 2 inbox feature; OpenCode 1 can only queue.
- Per-session auto-approval with child inheritance (requested 2026-09-09) was
  scoped but not built: OpenCode 2's saved "always allow" permissions apply
  across the project, and children have their own server rules. The honest
  design is a session setting that answers individual requests while the app
  is connected and lets children inherit it. `feature/e7-session-auto-approval`
  is an empty branch waiting for that work.
- Codex chat stays text-only (see `docs/codex-connection.md`).
- Paseo backend (Claude Code, Pi) is on `feat/paseo-backend-20260919`, proven
  on the emulator against daemon 0.8.0; Pi has no live turn yet (Copilot quota)
  and the physical-phone pass over Tailscale is the owner's to run (see
  `docs/paseo-connection.md`).

## Next backlog work

0. **AI Team (Gas City) plugin — plan only.** `docs/plans/gas-city-plugin-2026-09-10/`
   holds the PRD, UX, on-device onboarding, architecture, beads and the
   readiness gate. No product code until `06-decisions-and-readiness.md`
   is fully ticked; the two spikes TEAM-001/002 may run first.

1. Physical-phone pass for everything marked unverified above (turn on
   wireless ADB on `the owner's phone` or install the served APK by hand).
2. Control plane Sprint 1 from `docs/research/control-plane-2026-09-10/
   sprint-plan.md` once the owner has answered `open-questions.md`.
3. Model picker lists some models twice with the same provider label
   (seen live; see `docs/qa/background-agent-proof/README.md`).
4. Ambient voice (`F1c`) and F6-S3 launcher voice remain deferred.

Do not replace these with a queue of minor polish tasks.

## Local build and delivery

Checkpoint APKs are built locally (CI is intentionally off) with the Gradle
command recorded in `docs/qa/oc2-setup/local-build.json`, signed with the
accepted local key in `android/key.properties` (main checkout only, never
committed), and served from the machine-local `phone-preview` directory over
Tailscale. Generated APKs, credentials, transient logs and screenshots stay
out of Git.

# Decisions and the readiness gate

The goal says "no code unless plan is covered and ready 100 with UI and
UX". This file is that gate. Product code (any bead numbered TEAM-1xx or
higher) starts only when every box below is ticked. The two spikes
(TEAM-001, TEAM-002) are allowed before that because they produce evidence
the gate needs.

## A. Decisions — answered by the owner on 2026-09-10

| # | Decision | Answer |
|---|---|---|
| 1 | Orchestrator | **Gas City** supervisor API v0; Gas Town pack (Mayor/Polecats/Witness/Refinery semantics) |
| 2 | What a "Run" is | Formula runs **and** convoys, both shown as Runs; convoys subtitled "Batch" |
| 3 | Start a run from the phone (v1) | Send an objective + supervision level to the Mayor; watch beads appear; no plan editor |
| 4 | Hosts | **Wherever the user wants** — Ubuntu PC, other Mac/Windows/WSL hosts, and the phone (proot) — each with a visible performance disclaimer; phone path is spike-gated (TEAM-002). Reaffirmed 2026-09-10: the phone-hosted team is the product for users away from a PC and vibe coders with no PC; it is not optional to ship |
| 5 | Read plane exposure | Owner (2026-09-10, after TEAM-001): **plain HTTP over the tailnet, no Tailscale Serve, no HTTPS certs** — nothing about the setup appears publicly (a Let's Encrypt cert would put the hostname in public CT logs). WireGuard already encrypts and authenticates the hop. App rule becomes: allow `http://` only to loopback and tailnet addresses (100.64.0.0/10 or `*.ts.net`), refuse everything else. Funnel is never used |
| 6 | Write path | Host front + Tailscale identity allowlist; grants minted on the host; optional bearer not required |
| 7 | Harnesses | **OpenCode first** (`opencode acp`); Claude Code/Codex/others later via ACP. Evidence: one bead merged end to end with OpenCode 1.18.25 + glm-5.3-flash |
| 8 | Notifications | Decision requested, run failed, review ready, run completed — all four push |
| 9 | Merge from the phone | **All three**: readiness checklist, approve a merge request, and a Merge button behind two-step confirmation and the host's boundaries |
| 10 | Product name | **AI Team · Gas City** — provider named openly in headers and settings |
| 11 | Vocabulary | **Both side by side**: product term first, Gas City term in small secondary text (e.g. "Work · bead", "Run · convoy", "Agent · polecat"); no toggle needed |
| 12 | Review state convention | label `needs-review` (default kept) |
| 13 | Agent ↔ OpenCode session link | **Measured (TEAM-001): none.** Agents are `opencode acp` child processes, invisible to any OpenCode server; `sessionLink=false` for the OpenCode harness, transcript comes from the supervisor session stream |
| 14 | Front implementation | Python stdlib in `tool/host/cp_front/`, systemd user unit (default kept) |
| 15 | Grant key on the same host | yes (default kept) |

## B. Evidence that must exist

- [x] TEAM-001 report and recordings committed; spec pinned in `contracts/` (2026-09-10, [report](../../qa/ai-team/spike-pc-2026-09.md)); formula-run recording still missing (§F)
- [x] TEAM-002 report — proot-only layout **FAIL**; **hybrid native layout PASS on the emulator** (Android-built gc/bd/dolt native in Termux, agent in proot: polecat committed and pushed); phone confirmation pending the next awake window. See [spike-phone-2026-09.md](../../qa/ai-team/spike-phone-2026-09.md) §3f
- [x] Phone reaches the PC supervisor over plain HTTP on the tailnet — read proof 2026-09-11 over `100.101.102.103:8372` (supervisor `allowed_hosts` + `tool/host/tailnet_proxy.py`), counts match, resume clean
- [ ] The three fixture scenarios (`normal`, `blocked`, `failed`) plus `stream-drop` recorded from the real build

## C. Design sign-off

- [x] The design canvas (artifact) reviewed by the owner — v3 accepted with "start integration" (2026-09-10); no open comments
- [ ] Copy inventory approved (English); Arabic fragment planned
- [ ] Every screen in 02 has L/E/S/X/N states agreed
- [ ] Accessibility rules in 02 §11 accepted

## D. Engineering readiness

- [x] `04-plugin-architecture.md` names accepted (module paths, capability flags, profile field) — owner said "start building" on 2026-09-11 with the names as written
- [ ] Bead list in 05 imported into beads with dependencies (`bd create` + `bd dep add`)
- [ ] Quality gates confirmed to run at `--concurrency=6` under 5 minutes on the PC
- [x] Disk headroom ≥ 10 GB on Storage before Sprint A — 13 GB free after deleting regenerable build caches (2026-09-11)

## E. Explicit non-goals re-confirmed

- [ ] No merge button, no force actions, no worktree deletion in Sprints A–C
- [ ] No routing editor; display only
- [ ] No fifth tab; no Gas City admin console
- [ ] No offline mutation queue

When A–E are complete, start with TEAM-101 and TEAM-103 in parallel, then
TEAM-102 → TEAM-104 → TEAM-105, then the UI beads in the order of 05.

## F. Evidence from TEAM-001 (2026-09-10)

Full report: [docs/qa/ai-team/spike-pc-2026-09.md](../../qa/ai-team/spike-pc-2026-09.md).
Facts that change the plan:

1. **File beads store is out.** The Gas Town pack's work queries, claim protocol
   and formulas need `bd` + Dolt. `03-onboarding-on-device.md` §1 is amended;
   TEAM-002 must run Dolt + `bd` on arm64 in proot or be a FAIL.
2. **Host prerequisites** for the guide: `gc`, `bd` ≥ 1.0.4, `dolt`, `tmux`,
   `git`, `jq`; `gc` on PATH before `gc start`; the rig needs an `origin`
   remote; register with `gc rig add /path --name <rig>`.
3. **Runs:** direct bead slings never appear in `/runs` (only formula runs
   do); Work + convoys are the truthful v1 surface. A `--formula` sling
   recording is still owed before TEAM-102.
4. **Transcripts** exist only while a session is live; the app must persist
   what it streams if Agent detail is to show history after completion.
5. **Idle cost:** the pack keeps four patrol agents awake; the Settings and
   host cards must show this and the "Stop team" action must reap agent
   MCP child processes (two orphans pinned two cores for an hour).
6. **Tailnet HTTPS** is an owner action (enable Serve + HTTPS in the Tailscale
   admin) before phone-side connect can be verified.

## G. Owner decisions after TEAM-001 (2026-09-10, evening)

- Read plane: plain HTTP over the tailnet; no Tailscale Serve, no HTTPS
  certificates, never Funnel. (Decision 5 updated above.)
- Gas City stays as the engine; the stock Gas Town pack is run with a lean
  profile (`[[patches.agent]] … suspended = true` for mayor, deacon, boot;
  dog pool and witness under review) so idle agents do not burn tokens.
- Phone-hosted team stays in scope (decision 4). TEAM-002 runs with Dolt + bd
  in the rootfs; first attempt on 2026-09-10 was killed by Android — see
  [spike-phone-2026-09.md](../../qa/ai-team/spike-phone-2026-09.md).

## H. Gate status when Sprint A started (2026-09-11)

Owner asked "start building?" on 2026-09-11 after TEAM-001 passed. Sprint A
(computer-hosted read plane: TEAM-101, 103, 102, 104, 105, then UI beads)
started on that basis. Items still open and how they are handled:

| Open item | Handling |
|---|---|
| TEAM-002 verdict | Still running; second attempt on the phone was killed by Android even with the lean profile. Only Sprint C depends on it. |
| Phone → PC supervisor over tailnet HTTP | Verified in TEAM-104's real-read proof against the PC city. |
| Fixture scenarios `blocked`, `failed`, `stream-drop`; one formula run | `blocked`/`failed`/`stream-drop` are derived in TEAM-103 from the normal recording and clearly marked; the formula-run recording is a TEAM-102 pre-task. |
| Copy inventory approved; L/E/S/X/N states; a11y rules | Reviewed per UI bead with the owner on the canvas; not a blocker for the domain/fixture/adapter beads. |
| Beads imported into `bd` | Not done; the plan file is the tracker for now. |
| Non-goals re-confirmed | Carried as written; nothing in Sprint A touches them. |

## I. Sprint A closed (2026-09-11)

Checkpoint 1.0.41+42 carries TEAM-101 … TEAM-114. Owner decision on the
on-device path after §3f of the phone spike: **keep Gas City on the phone
in the hybrid native layout** (the "Solo agent" and "host on a server"
alternatives from §G stay as fallbacks, not the plan). Sprint C therefore
becomes: the manager script installs the Android-built `gc`/`bd`/`dolt`,
`libicu git jq tmux`, the `opencode` wrapper, applies the lean profile
after `gc rig add` + `gc import install`, launches the supervisor as its
own long-lived process, and asks for `termux-wake-lock` + battery
Unrestricted in the onboarding step.

Open before Sprint B starts: emulator screenshot captures for the QA index;
a `--formula` run recording; the refinery merge on the emulator (small
model dithered) and the phone polecat run to completion.

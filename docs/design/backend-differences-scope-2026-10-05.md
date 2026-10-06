# Backend differences: what the UI supports, and the next work (2026-10-05)

Follows [one backend per conversation](one-backend-per-conversation-2026-10-05.md).

Every conversation now runs on its own backend's controller. Every way into
a conversation (route, alert, monitor) goes through `chatLandingPage`, which
scopes the page to that backend. The rule that keeps the UI honest is the
existing one: **gate on `ServerCapabilities`, never on the flavor**. The agent
backend declares `paseoServerCapabilities`.

## Backends

| Backend | How the app reaches it | Capabilities |
|---|---|---|
| OpenCode 1 | saved server, HTTP + SSE | full v1 set |
| OpenCode 2 (beta / stable 2.0) | saved server, `/api` + Basic | v2 set: forms, inbox, steer/queue |
| Codex | saved server, app-server socket | restricted set |
| Paseo (saved) | saved server on the tailnet | `paseoServerCapabilities` |
| Agents on this phone (Claude Code; Codex, Gemini CLI, Qwen Code, Goose, Pi and fx installable) | `ConnectionController.agentBackend` over the in-app Paseo helper | `paseoServerCapabilities` |

## Surface matrix for agents on this phone

Legend: ✅ works · 🚫 hidden (cannot work) · ℹ️ explained in place · ⚠️ gap (next work).

| Surface | State | Notes |
|---|---|---|
| Conversations list rows, agent label, running / needs-you | ✅ | One paint, with saved rows while the helper starts |
| Open a live conversation | ✅ | Its own backend; OpenCode is not reconnected |
| Old conversation | ✅ | Resumed on the helper through Claude's own session handle (S4 done); a refused resume falls back to Start new |
| Composer "Ask Claude Code…" | ✅ | |
| Model chip / picker | ✅ | The agent's own models only |
| Thinking mode / Agent commands | 🚫 | Gated on `serverCatalog` / more than one agent or mode (fixed today) |
| Approval chip: ask / auto / approve everything | ✅ | Wording names the agent (fixed today); choices carried over |
| Request cards and auto-approve | ✅ | Proven on the emulator |
| Banner and Restart | ✅ | Names the agent; Restart starts the helper |
| Attach file / photos | ✅ | Photo library and Take photo send pictures (S5 done); other files are refused in words |
| Slash commands | ✅ | "Commands from Claude Code" listed by the helper and run as `/name args` (S6 done); a quiet command is not "no reply" |
| Files, terminal, diff, revert, fork, compact, share, notes, skills, MCP | 🚫 | Capability-gated |
| Open on another phone | 🚫 | Fixed today: an agent on this phone is reachable only here |
| Open on computer | 🚫 | `cliSessionResume` false |
| Rename | ✅ | |
| Delete / archive a conversation | ℹ️ | No backend offers Delete in the UI today (only untouched drafts are removed): a product decision, not an agent gap |
| Timeline / Find / Details | ✅ | Details is the usage view; reconnect wording names the agent |
| Sub-agents (Claude's Agent tool) | ✅ | Each is a read-only child conversation with its own live timeline (Paseo `agent.provider_subagents.*`); the card opens it (2026-10-06) |
| Search conversations | ✅ | The search lists matching conversations of every agent (S1 done) |
| New conversation › project chip | ✅ | A folder outside the agents' projects disables Send and says why (S1 done) |
| New conversation › "In a separate copy" | 🚫 | Hidden while another agent is chosen (fixed today) |
| Sign in | ✅ | Claude's own `claude auth login` on a terminal; the app never sees a code |
| Finished / needs-you notifications | ✅ | "Claude Code finished" seen on the emulator; opens on Claude's connection |
| App to background and back | ✅ | Follows the main connection |
| Usage / plan limit | ℹ️ | Status line "plan limit reached · try again later" |
| Other agents' sign-in (Gemini, Qwen, Goose, …) | ✅* | Their own login on the terminal; status from the helper (*not yet installed on a device) |

## Status (2026-10-05 evening)

Done: S1, S3, S4, S5, S6, S7, S8; S9 in part.

- S7: each agent's own login on the sign-in terminal (allowlisted programs);
  agents other than Claude read their sign-in from the helper's snapshot.
  Proven for Claude on the emulator; Codex and the others are covered by
  tests only.
- S8: "Claude Code finished" alert proven on the emulator with background
  mode on; tapping it opens the conversation on Claude's own connection.
- S9: the code-paste sign-in is gone from the app interface. Still open:
  New conversation's model chip reads the agents' models through the old
  `agentModels` path (works; unifying it on the agent backend's catalog is
  cleanup only).
- S2 is decided: no delete or archive (owner, 2026-10-06).
- S9's first part is dropped on purpose: New conversation keeps reading
  Claude's models through `agentModels`. It works and is proven, and
  moving it onto Claude's own connection would only add risk before a
  release.

## Next work, in order

Each slice ships with tests and an emulator proof, and changes nothing for the
OpenCode backends.

- **S1. Agents in search and the project picker.**
  - The launcher's Conversations search also matches agent rows: saved and
    live rows, by title, and opens them on their backend.
  - The New conversation project chip, while an agent is chosen, offers
    only folders under `/root/projects`, and says why the others are not
    offered.
- **S2. Remove an agent conversation.**
  - A Delete row (archive on the helper) on agent rows and in the
    conversation menu. It removes the saved row too.
- **S3. Details for an agent conversation.**
  - Agent, model, folder and "runs on this phone", instead of server facts.
  - Timeline and Find checked against Paseo timelines.
- **S4. Reopen old Claude conversations (feasibility first).**
  - Paseo keeps Claude's own session handle (`persistence.sessionId`).
  - Check against Paseo 0.9.2 whether an archived or stopped agent can be
    resumed with it.
  - If yes, set `resumeVerified` for Claude and drop the "Can't reopen"
    label. If no, keep the label.
- **S5. Images to Claude (feasibility first).**
  - Claude Code takes images. Check whether Paseo's `send_agent_message`
    accepts them.
  - If yes, turn on `promptAttachments` for agents whose runtime takes
    images. If no, keep the "text only" note.
- **S6. Claude's own commands (feasibility first).**
  - `/compact`, `/clear` and project commands, if Paseo lists or forwards
    them.
- **S7. Sign-in for the other agents.**
  - The catalog names each agent's own login command.
  - The same terminal screen runs it: no codes through the app, and API
    keys typed into the agent's own prompt.
  - Then a real-device install of one more agent (Gemini CLI) end to end.
- **S8. Device proof of notifications.**
  - A Claude run that finishes while the app is in the background, with
    background mode on: the alert appears, and tapping it opens the
    conversation on its backend.
- **S9. Cleanup.**
  - New conversation reads the agent backend's own catalog: drop
    `AgentModelChip` and `agentModels`.
  - Remove the unused code-paste sign-in path from the domain and
    `PhoneAgentSignIn.kt`, keeping the status reads.
  - Then run the full suite in chunks.

**Non-goals:** a different chat design per backend. Every backend uses the
same screens, and capabilities decide what shows.

## Found while auditing (not agent-specific)

- On the emulator the bottom tabs ignore `adb input tap`; 2120 does the same,
  so it predates this work. Check on the phone.

# Agent certification matrix

Updated 2026-10-07 (late) · device: emulator-5554 (OC_API35), not the owner phone.

An agent is certified only when every cell is pass (or n/a with a reason). Capabilities (resume, models, permissions, images, cancel) are granted from pass cells only (BA4).

agentVersion = the catalog pin the evidence ran on; helperVersion = the Paseo helper it ran through; architecture omitted = holds on every CPU (coordinator decision, protocol-level evidence).

Machine-readable copy: [agent-certification-matrix.json](agent-certification-matrix.json) (BA4 reads it). Legend: ✅ pass · 🟡 partial · · untested · 🔒 needs an account (owner item OW1) · ⛔ turned off · — not applicable.

| Agent | Version | Route | install | version | signedOut | signIn | models | smoke | tools | permission | images | abort | resume | network | cards |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Claude Code | 2.1.283 | Paseo native | ✅ | ✅ | · | · | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | · | ✅ |
| OpenCode 1 | 1.18.32 | OpenCode 1 server | · | · | · | · | · | ✅ | · | · | · | · | · | · | ✅ |
| OpenCode 2 |  | OpenCode 2 server | · | · | · | · | · | · | · | ✅ | · | · | · | · | ⛔ |
| fx | 0.0.12 | ACP via Paseo | ✅ | · | ✅ | 🟡 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Codex |  | Paseo native | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Gemini CLI |  | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Qwen Code |  | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Goose |  | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Oh My Pi |  | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |

## What each column means

- **install** — Installs from the pinned download, checksum verified
- **version** — Reports the pinned version on the phone
- **signedOut** — Signed-out state detected and named (no endless "Checking")
- **signIn** — Sign-in completes on the phone and the probe confirms it
- **models** — Model list loads and a model can be picked
- **smoke** — A plain prompt gets a streamed reply
- **tools** — A tool call runs and shows as a tool row
- **permission** — An approval request shows, Allow and Deny both work
- **images** — A photo sent to the agent (composer or photo card) reaches it and it answers about it
- **abort** — Stop ends the turn and the agent stays usable
- **resume** — After killing the app/helper the old chat reopens and continues
- **network** — Turning the network off then on recovers the turn or says why
- **cards** — Agent cards (oc-ui show) answered from chat and from the list

## Evidence

**Claude Code**

- install: pass — docs/verification/agents-on-phone-2026-10-03.md
- version: pass — docs/verification/agents-on-phone-2026-10-03.md
- models: pass — docs/qa/FQ2-2026-10-07/README.md
- smoke: pass — docs/qa/agent-tools-2026-10-07/README.md
- tools: pass — docs/qa/agent-tools-2026-10-07/README.md
- permission: pass — docs/qa/agent-tools-2026-10-07/README.md #2
- abort: pass — docs/qa/FQ2-2026-10-07/README.md
- resume: pass — docs/qa/FQ2-2026-10-07/README.md
- cards: pass — docs/qa/agent-tools-2026-10-07/README.md #5
- images: pass — docs/qa/agent-tools-2026-10-07/README.md #5 (photo card)

**OpenCode 1**

- smoke: pass — docs/qa/agent-tools-2026-10-07/README.md #6
- cards: pass — docs/qa/BA5-BA6-device-2026-10-07/README.md

**OpenCode 2**

- permission: pass — docs/verification/oc2-forms-device-2026-10-07.md (forms)
- cards: off — BA6

**fx**

- install: pass — #95 run, APK 2187
- signedOut: pass — #95 run, APK 2187
- signIn: partial — reaches the Vercel device page; account needed (OW1)
- cards: n/a — no cards adapter yet

**Codex**

- cards: n/a — no cards adapter yet

**Gemini CLI**

- cards: n/a — no cards adapter yet

**Qwen Code**

- cards: n/a — no cards adapter yet

**Goose**

- cards: n/a — no cards adapter yet

**Oh My Pi**

- cards: n/a — no cards adapter yet

## How to fill a cell

Run the scenario on a device, save a small JPG or log under `docs/qa/<item>-<date>/`, set the cell to `pass` with that path in the JSON, record agentVersion/helperVersion, then regenerate this table. A cell never turns `pass` from a unit test alone.

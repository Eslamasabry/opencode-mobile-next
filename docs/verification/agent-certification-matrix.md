# Agent certification matrix

Updated 2026-10-07 · device: emulator-5554 (OC_API35), not the owner phone.

An agent is certified only when every cell is pass (or n/a with a reason). Capabilities (resume, models, permissions, images, cancel) are granted from pass cells only (BA4).

Machine-readable copy: [agent-certification-matrix.json](agent-certification-matrix.json) (BA4 reads it). Legend: ✅ pass · 🟡 partial · · untested · 🔒 needs an account (owner item OW1) · ⛔ turned off · — not applicable.

| Agent | Route | install | version | signedOut | signIn | models | smoke | tools | permission | abort | resume | network | cards |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Claude Code | Paseo native | ✅ | ✅ | · | · | · | ✅ | ✅ | ✅ | · | · | · | ✅ |
| OpenCode 1 | OpenCode 1 server | · | · | · | · | · | ✅ | · | · | · | · | · | ⛔ |
| OpenCode 2 | OpenCode 2 server | · | · | · | · | · | · | · | ✅ | · | · | · | ⛔ |
| fx | ACP via Paseo | ✅ | · | ✅ | 🟡 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Codex | Paseo native | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Gemini CLI | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Qwen Code | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Goose | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Oh My Pi | ACP via Paseo | · | · | · | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |

## What each column means

- **install** — Installs from the pinned download, checksum verified
- **version** — Reports the pinned version on the phone
- **signedOut** — Signed-out state detected and named (no endless "Checking")
- **signIn** — Sign-in completes on the phone and the probe confirms it
- **models** — Model list loads and a model can be picked
- **smoke** — A plain prompt gets a streamed reply
- **tools** — A tool call runs and shows as a tool row
- **permission** — An approval request shows, Allow and Deny both work
- **abort** — Stop ends the turn and the agent stays usable
- **resume** — After killing the app/helper the old chat reopens and continues
- **network** — Turning the network off then on recovers the turn or says why
- **cards** — Agent cards (oc-ui show) answered from chat and from the list

## Evidence

**Claude Code**

- install: pass — docs/verification/agents-on-phone-2026-10-03.md
- version: pass — docs/verification/agents-on-phone-2026-10-03.md
- smoke: pass — docs/qa/agent-tools-2026-10-07/README.md
- tools: pass — docs/qa/agent-tools-2026-10-07/README.md
- permission: pass — docs/qa/agent-tools-2026-10-07/README.md #2
- cards: pass — docs/qa/agent-tools-2026-10-07/README.md #5

**OpenCode 1**

- smoke: pass — docs/qa/agent-tools-2026-10-07/README.md #6
- cards: off — BA6: enable (one trust zone decided)

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

Run the scenario on the emulator (FQ2 Claude, FQ3 OpenCode 1/2, FQ4–FQ8 the rest), save a small JPG or log under `docs/qa/<item>-<date>/`, then set the cell to `pass` with that path in the JSON and regenerate this table. A cell never turns `pass` from a unit test alone.

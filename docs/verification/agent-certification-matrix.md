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
- images: pass — docs/qa/agent-tools-2026-10-07/README.md#5

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

## FQ3 protocol certification

in-app Ubuntu protocol; no UI/restart/install qualification.

These results apply only to the recorded emulator build and in-app runtime. They do not update BA4 cells or qualify UI, installation, app/server restart or other CPU architectures. protocolSwitch means fresh-client connection switching with both owned histories refetched, not app UI switching.

| Agent | Expected | Observed | Build | Run | Base model scope | version | create | models | modelSwitch | stream | abort | reconnect | permissionAllow | permissionDeny | image | cards | protocolSwitch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| OpenCode 1 | 1.18.32 | 1.18.32 | 2196 | fq3-20261008b-cert | explicit: zai-coding-plan/glm-5.3 | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| OpenCode 2 | 2.0.10 | 2.0.10 | 2196 | fq3-20261008b-cert | explicit: opencode/big-pickle | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |

Model-dependent passes apply to the recorded base model selection. An explicit selection does not qualify server-default inference or other base models.

FQ3 legend: ✅ protocol assertion passed · ❌ assertion failed or prerequisite missing.

**OpenCode 1** — [fq3-20261008b-cert](../qa/FQ3b-2026-10-08/fq3-20261008b-cert.json)

- version: pass — `verified`; facts `{"asserted":true,"healthy":true}`
- create: pass — `verified`; facts `{"asserted":true,"retained":true}`
- models: pass — `verified`; facts `{"asserted":true,"connectedModels":18}`
- stream: fail — `oc1_prompt_error`; facts `{}`
- reconnect: pass — `verified`; facts `{"asserted":true,"messages":2,"refetched":true}`
- modelSwitch: pass — `verified`; facts `{"asserted":true,"selectionObserved":true}`
- abort: pass — `verified`; facts `{"asserted":true,"interrupted":true,"usableAfterAbort":true}`
- permissionAllow: pass — `verified`; facts `{"asserted":true,"replyObserved":true,"requestObserved":true}`
- permissionDeny: pass — `verified`; facts `{"asserted":true,"replyObserved":true,"requestObserved":true}`
- image: pass — `verified`; facts `{"asserted":true,"imageAnswerVerified":true}`
- cards: pass — `verified`; facts `{"answerReceipt":true,"asserted":true,"cardsToolCall":true}`
- protocolSwitch: pass — `verified`; facts `{"asserted":true,"bothHistoriesPreserved":true,"freshClients":true,"oc1Sessions":16,"oc2Sessions":16}`

**OpenCode 2** — [fq3-20261008b-cert](../qa/FQ3b-2026-10-08/fq3-20261008b-cert.json)

- version: pass — `verified`; facts `{"asserted":true,"healthy":true}`
- create: pass — `verified`; facts `{"asserted":true,"created":true}`
- models: pass — `verified`; facts `{"asserted":true,"enabledModels":11,"selectedModelAvailable":true}`
- stream: pass — `verified`; facts `{"asserted":true,"completedReply":true,"streamedDelta":true}`
- reconnect: pass — `verified`; facts `{"asserted":true,"refetched":true,"retainedMessages":3}`
- modelSwitch: fail — `timeout`; facts `{}`
- abort: pass — `verified`; facts `{"asserted":true,"interrupted":true,"midStreamObserved":true,"usableAfterAbort":true}`
- permissionAllow: fail — `timeout`; facts `{}`
- permissionDeny: fail — `timeout`; facts `{}`
- image: fail — `timeout`; facts `{}`
- cards: fail — `cards_tool_call_missing`; facts `{}`
- protocolSwitch: pass — `verified`; facts `{"asserted":true,"bothHistoriesPreserved":true,"freshClients":true,"oc1Sessions":16,"oc2Sessions":16}`

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
| Codex | 0.160.0 | Paseo native | ✅ | ✅ | ❌ | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Gemini CLI | 0.62.0 | ACP via Paseo | ✅ | ✅ | ❌ | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Qwen Code | 0.24.7 | ACP via Paseo | ✅ | ✅ | ❌ | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Goose | 1.53.0 | ACP via Paseo | ✅ | ✅ | ❌ | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |
| Oh My Pi | 18.5.1 | ACP via Paseo | ✅ | ✅ | ❌ | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | 🔒 | — |

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

- install: pass — docs/qa/FQ-install-2026-10-08/README.md#codex
- version: pass — docs/qa/FQ-install-2026-10-08/README.md#codex
- signedOut: fail — docs/qa/FQ-install-2026-10-08/README.md#codex
- cards: n/a — no cards adapter yet

**Gemini CLI**

- install: pass — docs/qa/FQ-install-2026-10-08/README.md#gemini
- version: pass — docs/qa/FQ-install-2026-10-08/README.md#gemini
- signedOut: fail — docs/qa/FQ-install-2026-10-08/README.md#gemini
- cards: n/a — no cards adapter yet

**Qwen Code**

- install: pass — docs/qa/FQ-install-2026-10-08/README.md#qwen
- version: pass — docs/qa/FQ-install-2026-10-08/README.md#qwen
- signedOut: fail — docs/qa/FQ-install-2026-10-08/README.md#qwen
- cards: n/a — no cards adapter yet

**Goose**

- install: pass — docs/qa/FQ-install-2026-10-08/README.md#goose
- version: pass — docs/qa/FQ-install-2026-10-08/README.md#goose
- signedOut: fail — docs/qa/FQ-install-2026-10-08/README.md#goose
- cards: n/a — no cards adapter yet

**Oh My Pi**

- install: pass — docs/qa/FQ-install-2026-10-08/README.md#omp-acp
- version: pass — docs/qa/FQ-install-2026-10-08/README.md#omp-acp
- signedOut: fail — docs/qa/FQ-install-2026-10-08/README.md#omp-acp
- cards: n/a — no cards adapter yet

## How to fill a cell

Run the scenario on a device, save a small JPG or log under `docs/qa/<item>-<date>/`, set the cell to `pass` with that path in the JSON, record agentVersion/helperVersion, then regenerate this table. A cell never turns `pass` from a unit test alone.

## FQ3 protocol certification

in-app Ubuntu protocol; no UI/restart/install qualification.

These results apply only to the recorded emulator build and in-app runtime. They do not update BA4 cells or qualify UI, installation, app/server restart or other CPU architectures. protocolSwitch means fresh-client connection switching with both owned histories refetched, not app UI switching.

| Agent | Expected | Observed | Build | Run | version | create | models | modelSwitch | stream | abort | reconnect | permissionAllow | permissionDeny | image | cards | protocolSwitch |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| OpenCode 1 | 1.18.32 | 1.18.32 | 2195 | fq3-20261008-settled | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| OpenCode 2 | 2.0.10 | 2.0.10 | 2195 | fq3-20261008-settled | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |

FQ3 legend: ✅ protocol assertion passed · ❌ assertion failed or prerequisite missing.

**OpenCode 1** — [fq3-20261008-settled](../qa/FQ3-2026-10-08/fq3-20261008-settled.json)

- version: pass — `verified`; facts `{"asserted":true,"healthy":true}`
- create: pass — `verified`; facts `{"asserted":true,"retained":true}`
- models: pass — `verified`; facts `{"asserted":true,"connectedModels":18}`
- stream: pass — `verified`; facts `{"asserted":true,"completedReply":true,"streamedDelta":true}`
- reconnect: pass — `verified`; facts `{"asserted":true,"messages":2,"refetched":true}`
- modelSwitch: pass — `verified`; facts `{"asserted":true,"selectionObserved":true}`
- abort: fail — `oc1_after_abort_reply_mismatch`; facts `{}`
- permissionAllow: pass — `verified`; facts `{"asserted":true,"replyObserved":true,"requestObserved":true}`
- permissionDeny: pass — `verified`; facts `{"asserted":true,"replyObserved":true,"requestObserved":true}`
- image: fail — `oc1_image_content_unverified`; facts `{}`
- cards: pass — `verified`; facts `{"answerReceipt":true,"asserted":true,"cardsToolCall":true}`
- protocolSwitch: pass — `verified`; facts `{"asserted":true,"bothHistoriesPreserved":true,"freshClients":true,"oc1Sessions":16,"oc2Sessions":16}`

**OpenCode 2** — [fq3-20261008-settled](../qa/FQ3-2026-10-08/fq3-20261008-settled.json)

- version: pass — `verified`; facts `{"asserted":true,"healthy":true}`
- create: pass — `verified`; facts `{"asserted":true,"created":true}`
- models: pass — `verified`; facts `{"asserted":true,"enabledModels":84,"selectedModelAvailable":true}`
- stream: fail — `timeout`; facts `{}`
- reconnect: fail — `timeout`; facts `{}`
- modelSwitch: fail — `inference_execution_failed`; facts `{}`
- abort: fail — `timeout`; facts `{}`
- permissionAllow: fail — `timeout`; facts `{}`
- permissionDeny: fail — `timeout`; facts `{}`
- image: fail — `inference_execution_failed`; facts `{}`
- cards: fail — `inference_execution_failed`; facts `{}`
- protocolSwitch: pass — `verified`; facts `{"asserted":true,"bothHistoriesPreserved":true,"freshClients":true,"oc1Sessions":16,"oc2Sessions":16}`

## Phone-agent install certification

App installation on x64 emulator; signed-out, no account qualification.

These cells qualify installation only. Phone check means the check completed; a signed-out agent remains unavailable for authenticated chat. No account sign-in, prompt smoke, or runtime capabilities are granted here.

| Agent | Expected | Observed | Build | install | version | signedOut | phoneCheck | launchNoAccount | cancelRetry | lowStorage | uninstall |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Codex | 0.160.0 | 0.160.0 | 2196 | ✅ | ✅ | ❌ | ✅ | 🟡 | ✅ | 🟡 | 🟡 |
| Gemini CLI | 0.62.0 | 0.62.0 | 2196 | ✅ | ✅ | ❌ | ✅ | 🟡 | ✅ | 🟡 | 🟡 |
| Qwen Code | 0.24.7 | 0.24.7 | 2196 | ✅ | ✅ | ❌ | ✅ | 🟡 | ✅ | 🟡 | 🟡 |
| Goose | 1.53.0 | 1.53.0 | 2196 | ✅ | ✅ | ❌ | ✅ | 🟡 | ✅ | 🟡 | 🟡 |
| Oh My Pi | 18.5.1 | 18.5.1 | 2196 | ✅ | ✅ | ❌ | ✅ | 🟡 | ✅ | 🟡 | 🟡 |

**Codex** — fq-install-codex-2196-20261008; docs/qa/FQ-install-2026-10-08/README.md#codex

- install: pass — `verified`; facts `{"asserted":true,"checksumVerified":true,"installedViaApp":true}`
- version: pass — `verified`; facts `{"asserted":true}`
- signedOut: fail — `probe_unsupported`; facts `{"asserted":false,"namedSignedOut":false}`
- phoneCheck: pass — `verified`; facts `{"asserted":true,"completed":true}`
- launchNoAccount: partial — `cli_only_app_route_unavailable`; facts `{"asserted":false,"emptyHome":true,"noHang":true,"noOrphans":true,"sentLogin":false,"sentPrompt":false}`
- cancelRetry: pass — `verified`; facts `{"asserted":true,"cancelObserved":true,"retryCompleted":true}`
- lowStorage: partial — `policy_harness_only`; facts `{"appThresholdOverride":false,"asserted":false,"deviceFilled":false,"guardPolicyVerified":true}`
- uninstall: partial — `no_app_removal_path`; facts `{"asserted":false,"bytesFreed":289132544,"leftoversRemoved":true,"noOrphans":true,"removedViaApp":false}`

**Gemini CLI** — fq-install-gemini-2196-20261008; docs/qa/FQ-install-2026-10-08/README.md#gemini

- install: pass — `verified`; facts `{"asserted":true,"checksumVerified":true,"installedViaApp":true}`
- version: pass — `verified`; facts `{"asserted":true}`
- signedOut: fail — `probe_unsupported`; facts `{"asserted":false,"namedSignedOut":false}`
- phoneCheck: pass — `verified`; facts `{"asserted":true,"completed":true}`
- launchNoAccount: partial — `cli_only_app_route_unavailable`; facts `{"asserted":false,"emptyHome":true,"noHang":true,"noOrphans":true,"sentLogin":false,"sentPrompt":false}`
- cancelRetry: pass — `verified`; facts `{"asserted":true,"cancelObserved":true,"retryCompleted":true}`
- lowStorage: partial — `policy_harness_only`; facts `{"appThresholdOverride":false,"asserted":false,"deviceFilled":false,"guardPolicyVerified":true}`
- uninstall: partial — `no_app_removal_path`; facts `{"asserted":false,"bytesFreed":99860480,"leftoversRemoved":true,"noOrphans":true,"removedViaApp":false}`

**Qwen Code** — fq-install-qwen-2196-20261008; docs/qa/FQ-install-2026-10-08/README.md#qwen

- install: pass — `verified`; facts `{"asserted":true,"checksumVerified":true,"installedViaApp":true}`
- version: pass — `verified`; facts `{"asserted":true}`
- signedOut: fail — `probe_unsupported`; facts `{"asserted":false,"namedSignedOut":false}`
- phoneCheck: pass — `verified`; facts `{"asserted":true,"completed":true}`
- launchNoAccount: partial — `cli_only_app_route_unavailable`; facts `{"asserted":false,"emptyHome":true,"noHang":true,"noOrphans":true,"sentLogin":false,"sentPrompt":false}`
- cancelRetry: pass — `verified`; facts `{"asserted":true,"cancelObserved":true,"retryCompleted":true}`
- lowStorage: partial — `policy_harness_only`; facts `{"appThresholdOverride":false,"asserted":false,"deviceFilled":false,"guardPolicyVerified":true}`
- uninstall: partial — `no_app_removal_path`; facts `{"asserted":false,"bytesFreed":112664576,"leftoversRemoved":true,"noOrphans":true,"removedViaApp":false}`

**Goose** — fq-install-goose-2196-20261008; docs/qa/FQ-install-2026-10-08/README.md#goose

- install: pass — `verified`; facts `{"asserted":true,"checksumVerified":true,"installedViaApp":true}`
- version: pass — `verified`; facts `{"asserted":true}`
- signedOut: fail — `probe_unsupported`; facts `{"asserted":false,"namedSignedOut":false}`
- phoneCheck: pass — `verified`; facts `{"asserted":true,"completed":true}`
- launchNoAccount: partial — `cli_only_app_route_unavailable`; facts `{"asserted":false,"emptyHome":true,"noHang":true,"noOrphans":true,"sentLogin":false,"sentPrompt":false}`
- cancelRetry: pass — `verified`; facts `{"asserted":true,"cancelObserved":true,"retryCompleted":true}`
- lowStorage: partial — `policy_harness_only`; facts `{"appThresholdOverride":false,"asserted":false,"deviceFilled":false,"guardPolicyVerified":true}`
- uninstall: partial — `no_app_removal_path`; facts `{"asserted":false,"bytesFreed":298692608,"leftoversRemoved":true,"noOrphans":true,"removedViaApp":false}`

**Oh My Pi** — fq-install-omp-acp-2196-20261008; docs/qa/FQ-install-2026-10-08/README.md#omp-acp

- install: pass — `verified`; facts `{"asserted":true,"checksumVerified":true,"installedViaApp":true}`
- version: pass — `verified`; facts `{"asserted":true}`
- signedOut: fail — `probe_unsupported`; facts `{"asserted":false,"namedSignedOut":false}`
- phoneCheck: pass — `verified`; facts `{"asserted":true,"completed":true}`
- launchNoAccount: partial — `cli_only_app_route_unavailable`; facts `{"asserted":false,"emptyHome":true,"noHang":true,"noOrphans":true,"sentLogin":false,"sentPrompt":false}`
- cancelRetry: pass — `verified`; facts `{"asserted":true,"cancelObserved":true,"retryCompleted":true}`
- lowStorage: partial — `policy_harness_only`; facts `{"appThresholdOverride":false,"asserted":false,"deviceFilled":false,"guardPolicyVerified":true}`
- uninstall: partial — `no_app_removal_path`; facts `{"asserted":false,"bytesFreed":280174592,"leftoversRemoved":true,"noOrphans":true,"removedViaApp":false}`

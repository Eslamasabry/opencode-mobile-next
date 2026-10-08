# OC2 permission Allow / Deny classification

Inspection date: 2026-10-08. Candidate: `sol/bc-fq3c`, base `f0e33d96d407ff68782b49702e88269d197ae96e`. Scope follows [the FQ3c contract](../../design/BC-FQ3c-contract.md): source inspection and this note only; no app changes, test processes, device jobs, or certification passes.

Finish line: distinguish a permission transport defect from a harness contract mismatch and an unavailable model/tool prerequisite using exact 2.0.10 source and scoped runtime facts. Non-goal: altering user permission grants, provider configuration, model selection, or server implementation to manufacture an ask.

## Findings

| Area | Classification | Evidence and limit |
|---|---|---|
| Stable tool and permission action | Proven harness mismatch | 2.0.10 registers `shell` and checks action `shell`; the harness uses `bash` for the session rule, prompt, asked action, and retained tool name. No live stage record yet proves this was the timeout's precise cause. |
| Deny execution terminal | Proven harness mismatch | Reject without feedback settles the tool as an aborted error and interrupts execution. The harness currently demands `session.execution.succeeded` for both outcomes. |
| HTTP reply / event payload | App and harness mapping align | Stable HTTP accepts `decision`; the native event still carries `reply`. The shared stable mapper converts the beta body correctly. |
| Pending request / source ID | App and harness mapping align | Own-session pending route, `source.messageID`, and `source.id` match the stable schema and ownership check. No reproduced app defect. |
| Effective ask rule | Runtime prerequisite remains unproven | Later project saved Allows and a permission evaluation hook can supersede session Ask. Correcting the action alone does not prove an ask will occur. |
| Actual old timeouts | Unresolved stage | Previous reports record a final timeout, without distinguishing model generation, ask, reply, or settlement waits. Source inspection cannot replace that observation. |

## Exact version and wire flow

Primary source is pinned to upstream commit `b8cedc1a7a5e2916bbb65dc1d4b620729c261638`; [the package version is 2.0.10](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/tui/package.json#L3). The installed local 2.0.10 binary also contains the `opencode.tool.shell` implementation and decline handling. The older September 14 checkout is not used as the stable contract.

The harness in [oc2.dart](../../../tool/qa/fq3/oc2.dart) creates a session with stable-only `permissions: [{action: 'bash', resource: '*', effect: 'ask'}]`, prompts for the exact harmless `printf FQ3_ALLOW` or `printf FQ3_DENY`, and requires a fresh session-scoped ask. It checks own pending state and the retained assistant call before replying. These ownership guards should remain.

Stable [ShellTool](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/tool/plugin/shell.ts#L20) instead registers `shell`, with `input.command`, and calls `Permission.assert` with that name as its action before spawning. Use `shell` consistently for stable sessions; preserve beta `bash` behavior. A plugin can rename an advertised tool, so runtime evidence must still bind the real call, source ID and known command rather than infer execution from a prompt.

[The protocol group](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/protocol/src/groups/permission.ts) defines GET `/api/permission/request`, GET `/api/session/:sessionID/permission`, and POST `/api/session/:sessionID/permission/:requestID/reply` with `{decision: 'once' | 'always' | 'reject', message?}`. [The handler](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/server/src/handlers/permission.ts) verifies request ownership and passes `decision` as the core reply. [The event schema](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/schema/src/permission.ts) retains `permission.asked` and `permission.replied`, with event field `reply`, and tool source `{type, messageID, id}`.

The app [stable mapper](../../../lib/api2/dialect.dart), [client](../../../lib/api2/client.dart), [request model](../../../lib/api2/models/models_inbox.dart), [domain mapper](../../../lib/api2/gateway_mappers.dart), and [event mapper](../../../lib/api2/gateway_events.dart) agree with those shapes. `respondPermission` recovers a missing legacy session owner from pending requests; `respondPermissionV2` receives the explicit session owner. There is no source-backed reason to rename the native event field to `decision` or change the pending route.

## Effective Ask and saved grants

[Core permission evaluation](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/permission.ts#L79) uses the last matching action/resource rule. It merges agent rules then session rules, protects configured Deny, appends project saved Allows, and finally runs the evaluation hook. Thus a saved matching Allow may override session Ask; configured Deny is protected first. This is a source-proven possibility, not evidence that a particular managed or isolated session had a matching grant.

[Shell parsing](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/shell/parse.ts#L188) produces resource `printf FQ3_ALLOW` / `printf FQ3_DENY` and save pattern `printf *`: `printf` uses the first-token prefix. [Saved storage](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/permission/saved.ts) persists project ID, action, and resource. A saved `shell`/`printf *`, `shell`/`*`, or wildcard-action matching grant can shadow Ask; an exact `bash` action does not match `shell`. [Wildcard matching](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/util/wildcard.ts) is anchored, with `*` and `?` patterns.

Inspect matching grants only into a boolean/count summary, never dump user patterns or erase grants. A programmatically created permission request can diagnose evaluation but does not prove an assistant tool actually paused; it cannot qualify the permission assertions.

## Exact Reject settlement

Plain Reject emits the matching replied event, removes pending requests for the session, and fails the waiting assertion with `DeclinedError`. [Model request execution](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/model-request.ts#L331) recovers that deliberate defect as a typed decline. Reject with feedback has different continuation behavior; this harness sends no feedback.

[The runner step](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/runner/step.ts#L180) publishes matching tool failure with error type `aborted`, fails the assistant step, and interrupts when the location remains open. [Execution classification](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/execution.ts#L46) therefore emits `session.execution.interrupted`, with default reason `shutdown` when no explicit interrupt reason was attached. That reason alone is not proof of a permission decline or a server shutdown.

[The message updater](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/message-updater.ts#L350) retains the same tool ID and input with `state.status: 'error'` and the aborted error. [The publisher](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/runner/publish-llm-event.ts#L313) uses `executed` as provider-execution provenance: local successful calls can also have `executed: false`. Do not treat that flag alone as proof that a process did or did not run. The [coordinator](https://github.com/anomalyco/opencode/blob/b8cedc1a7a5e2916bbb65dc1d4b620729c261638/packages/core/src/session/run-coordinator.ts#L95) releases active ownership after terminal publication; a bounded status check must wait for cleanup rather than assume terminal observation already means inactive.

## Proposed failing fixtures and safe live observations

Root must freeze the exact assertion contract before a new fixture file is implemented. No product API change is proposed.

1. Stable fixture advertises/calls `shell`, observes `action: shell`, and requires a stable session `shell` Ask rule. Current `bash` assumptions must fail. A beta fixture continues to require `bash` and no added stable session rule.
2. Deny fixture emits own ask, matching replied Reject, same-call aborted failure, retained same-command error, interrupted terminal, pending removal and bounded inactive state. It should qualify the permission denial despite the lack of a successful execution terminal. Current success-only guard must fail first.
3. Negative mutations must reject unrelated request/session/call, missing reply, arbitrary tool error, missing retained call, still-running execution, same-call success or command-start evidence after denial. Shutdown interruption alone must never pass.
4. Allow must retain its success requirement and prove the same-call success/completed state plus command result marker, pending removal and bounded inactive state. Generic completion or HTTP admission is insufficient.

For the next root-owned live attempt record fixed stage identifiers and counts/booleans only: selected model matches, retained Ask action matches, saved grant matches, actual known-command call exists, fresh asked/replied events match, pending before/after, same-call terminal/error category, known marker verified, active after settlement, scoped shell-start/progress/success counts, and retry classification/count. Do not retain raw model text, tool output, permission resources, provider configuration, or credentials. Preserve real inference and genuine assistant tool requirements.

Prior build-2196 reports [Allow](../FQ3b-2026-10-08/fq3-20261008b-cert-opencode2-allow.json) and [Deny](../FQ3b-2026-10-08/fq3-20261008b-cert-opencode2-deny.json) remain failed historical evidence. They used explicit `opencode/big-pickle` on owned and app-managed servers respectively, but lacked stage observations. Neither source findings nor fixtures qualify a build-2197 capability. Root runs focused tests and the authorized device attempt; this inspection launched neither.

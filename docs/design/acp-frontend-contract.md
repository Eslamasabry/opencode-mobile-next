# ACP frontend handoff

Date: 2026-10-02. Owner: backend/domain Codex; UI owner: Claude.
Status: research/spike only; no selectable ACP backend or gateway is enabled.
Decision and evidence: [ACP research](acp-research-2026-10-02.md).

## What exists

`lib/acp/acp_client.dart` is an isolated protocol client, not a `ServerGateway`.
Do not import it from `lib/ui/`, add a profile flavor, or offer ACP in Add server
on the strength of this spike. Existing OpenCode/Paseo/Codex routes remain the
product routes. There are no new storage keys, migration, or deletion hooks yet.

## Proposed integration seam (not callable today)

Prefer an additional ACP runtime behind the existing private Paseo connection if
its pinned daemon and our provider/session protocol prove compatible. A separate
`AcpGateway implements ServerGateway, ServerOperationsGateway` is an alternative
only after a host contract is selected. UI consumes gateway capabilities and
ordinary session/message/permission models; it does not consume ACP envelopes.
The following are requirements for the future implementation, not new APIs.

| UI behavior | Required backend evidence / contract |
|---|---|
| Test connection | Private host identity and scoped project check; authenticated relay plus ACP v1 handshake. Initialize alone does not prove login, folder access, or prompting. |
| Choose agent | Host-owned installed/runtime allowlist; name and safe version; no arbitrary executable, argv, environment, or MCP server entered from a server response. |
| New conversation / send | One agent and host project fixed for session lifetime; dispatch accepted separately from completed; no automatic resend after timeout/disconnect. |
| Stream reply / tools / changes | Map `session/update` to domain events; merge tool updates by `toolCallId`; tool diff previews are not session-wide Git diff/revert evidence. |
| Stop | `session/cancel` notification; show stopping until prompt resolves or connection is lost. A sent cancel is not proof the agent stopped. |
| Review permission | Preserve offered option IDs/kinds and scoped request identity; callback may select only an offered option. Closing/cancelling defaults to cancelled; never implicitly allow. |
| Restart / reconnect | Host-owned durable inventory and transcript snapshot/replay, scoped to profile + agent + project; no stale approvals or speculative replay of prompts. |
| Remove connection | Drain callbacks first; erase preferences, secure credentials, journals, and shared-index references; report local removal separately from host session deletion. |

## Capability policy

`ServerCapabilities` defaults many flags to **true**. A future ACP gateway must
explicitly set unsupported flags false, following the Codex/Paseo constants.
Start text-only. Keep attachments, mentions, offline queue, file browsing, user
terminal, project/worktree management, session-wide diff/revert, fork, compact,
import/export, notes, sharing, archive, forms/inbox, account panel, config writes,
global search/attention/events, and provider credential operations off until each
has a callable verified host/domain contract. Set `messageCompletionEndsRun=false`:
the `session/prompt` result ends a turn. `clientPromptMessageID=false` until the
host proves echoed message identity; JSON-RPC IDs are request correlation only.
Do not infer `terminal=true` from ACP's agent-to-client terminal capability.
Do not infer persistent or revocable grants from an `allow_always` option alone.
Optional list/load/fork capabilities differ by agent and protocol release.

If standalone ACP is selected, existing mandatory `SessionGateway` lifecycle
reads (including list, message pages, rename, delete) need explicit host support
or typed unavailable results, and the coordinator must add corresponding UI
gates where the current model has none. Returning empty history for unsupported
inventory would make missing data look like truth.

## Copy, security, and ownership

Use plain states: “Ready”, “Sign in on your computer”, “Connection lost”,
“The reply may still be running. Check your computer before sending again”,
and “This agent cannot reopen this conversation”. Put protocol/version/error
codes under Details; never put raw wire errors, agent stderr, token values,
credentials, or full commands/environment there. Claude owns kit parts and
English/Arabic localization; these phrases are intent, not localized output.

Relay connections are loopback/verified Tailscale or an owner-managed SSH tunnel,
including for TLS endpoints. Public `wss://` is not an exception. No service of
ours. Provider login stays on the host. Host-origin auth/documentation links go
through `openExternalLink`; no server-authored launch command is executed by UI.
Future per-profile preferences use `oc.acpMetadata.<profileId>` and secure secrets
use `oc.acpSecrets.<profileId>`; register secure/file deletion explicitly, since
the preference sweep does not erase every storage medium. Naming is proposed.

Acceptance before enabling: save → restart → list/reopen → prompt → stream →
permission allow/reject → cancel → disconnect/reconcile → profile deletion;
agent without resume has an honest limitation; no public listeners or secret
sentinels in logs; focused gateway tests, kit/UI/accessibility/RTL checks, and
the coordinator's frozen integration gate. This spike proves none of that UI.

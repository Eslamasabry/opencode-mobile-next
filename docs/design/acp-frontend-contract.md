# ACP through private Paseo: frontend contract

Date: 2026-10-02. Backend/domain: Codex. Kit UI/localization: Claude.
Owner decisions: existing private Paseo; only verified resumable agents; provider
sign-in on the computer. No new server type or provider credential form.

## Implemented status and hard limit

Discovery and safe permission handling are callable. **No new pilot agent is
selectable at Paseo 0.9.2.** Its provider snapshot does not carry negotiated ACP
`loadSession`, list/load support or structured authentication state. `ready`
means catalog discovery, not verified sign-in/restoration. Agent snapshot
`supportsSessionPersistence` and `supportsSessionListing` are hardcoded in the
ACP adapter. Do not turn any of these into a Ready badge. Extra undeclared
snapshot fields are ignored. See [source evidence](../verification/acp-paseo-pilot-2026-10-02.md).

Gemini, omp, `omp-acp` and fx are recognized candidate IDs when the host reports
them. The catalog does not invent installation rows for missing providers.
Built-in omp is RPC, not ACP. It needs a separately reviewed native-route
exception or a distinct ACP registration; `omp` is a reserved built-in ID.
Existing supported Paseo routes continue through their existing picker. Discovery
contains those rows too; `notPilot` refers only to this new preview.

## Domain API and gates

Import `lib/domain/server_gateway.dart`; never import `lib/paseo` or `lib/acp`
from UI. Additive flags default false for other gateways:

| Flag | Domain interface | Meaning |
|---|---|---|
| `hostAgentProviders` | `HostAgentProviderGateway` | Safe discovery including unavailable rows. It does not mean an agent can send. |
| `hostAgentPermissionActions` | `HostAgentPermissionGateway` | Exact actions for ACP requests; ordinary permissions still use the existing gateway. |

Both flags are true on Paseo. Capability-gate the interface lookup. Do not gate
on a server flavor. `loadHostAgentProviders(refresh: false)` reads a scoped
snapshot; `refresh: true` asks the host to refresh its catalog and reads again.
An error means the check failed; it must not look like an empty installed list.
Reload on connection/project changes and on explicit “Check again”. Retire the
old result when the connection/project changes. Backend caches are invalidated
on reconnect, project change and provider-snapshot pushes; old requests cannot
fill a new project's cache. No discovery/login state is persisted by this slice.

## Agent rows and host sign-in

`HostAgentProviderCatalog.providers` includes unavailable rows; `.selectable`
is the only list usable as agent choices. Use stable `id` as identity, the
app-authored `displayName`, `reason`, `availability`, `hiddenReason`,
`loginState`, `resumeSupport` and immutable `models`. IDs are opaque identifiers,
not executable commands. Do not replace these fields with host labels/errors.

| State/reason | Required row presentation |
|---|---|
| `ready` and `selectable=true` | “Ready”; selectable. Currently no pilot row can reach this. |
| `checking` | “Checking your computer.”; disabled. |
| `needsHostSignIn` | “Sign in on your computer first.”; disabled. Requires verified restoration support; currently not emitted by the pin. |
| `hidden/resumeUnverified` | “Conversation restoration has not been checked.”; absent from picker, visible in unavailable-agent explanations. |
| `hidden/resumeUnsupported` | “This agent cannot restore a conversation.”; absent from picker. |
| `hidden/unavailable` | “This agent is not available on your computer.”; absent from picker. |
| `hidden/disabled` | “This agent is turned off on your computer.”; absent from picker. |
| `hidden/notPilot` | “This agent is not available in this preview.”; refers to the ACP preview, not the existing native picker. |

An auth-required catalog error is reduced to
`loginState=needsHostSignIn`. Because resume proof is still missing, the row
remains hidden/resumeUnverified. Show both the restoration reason and the
host-sign-in instruction. Other raw errors produce `unknown`, never “Signed in”.
For Gemini show **“Sign in on your computer: run gemini there, then check again.”**
Use `signInMessage`; place fixed `hostSignInCommand` under Details:
Gemini `gemini`; omp/omp-acp `omp` followed by `/login` there; fx `fx login`.
Offer “Check again”. Do not run these commands, request provider tokens, launch
an OAuth flow, or display daemon diagnostics/stdout/stderr. Unknown agents have
no suggested sign-in command. Claude owns English/Arabic localization of this
app-authored copy.

## Permission card

Read `pendingHostAgentPermissions()` and match each `requestId` + `sessionId`
to the ordinary `pendingPermissions()` card. `permission.asked`/`replied` events
signal when to reread; do not construct an approval from raw event metadata.
The ordinary ACP card contains only the known task preview (shell command or
file path/content/diff), fixed permission copy and no arbitrary raw request,
input, host labels or suggested standing grants. Task previews may contain
private project content: render them as task content, never diagnostic logs.

Show “The agent needs your permission.” and the structured task preview. Each
`HostAgentPermissionChoice` has opaque `actionId`, fixed `label` and behavior
`allowOnce` or `rejectOnce`. Send exactly the chosen ID:
`respondHostAgentPermission(requestId, selectedActionId: choice.actionId)`.
Multiple offered allow-once choices keep distinct IDs; do not guess one or
collapse them into the legacy “once” reply. Use no arbitrary host action label.

Dismiss/back/timeout defaults to
`respondHostAgentPermission(requestId)` (null selection). The backend selects
an offered reject-once ID. With no reject-once it asks the host to cancel the
turn, because Paseo otherwise falls back to reject-always. A cancel
acknowledgement that still lists the request is a failure: the card remains
pending. Show the safe error and allow checking on the computer. A one-way
permission response means sent, not confirmed execution/completion.

Never show Always allow/Always reject for an ACP card. The global legacy
`persistentPermissionGrants` flag does not authorize standing ACP actions.
Invalid, expired or disconnected cards throw a safe failure; retire/reload them.
Do not retry or queue approvals onto a new connection. This backend stores no
standing grant. Existing non-ACP suggestions remain on their established path.

## Restart, reconnect and continuation wording

For a reopened session call `HostAgentProviderGateway.loadHostAgentContinuation(sessionId)`.
It returns `HostAgentContinuation` with `sessionId`, opaque `providerId`, `state`,
`blocked` and fixed `reason`; do not parse a session title/model or inspect host
handles. `existingRoute` means keep the existing gateway flow, not newly
verified ACP restoration. `resumeUnverified` and `missingHandle` block sending.
`missingHandle` already returns “This agent cannot reopen this conversation.
Check it on your computer.” Project changes retire the lookup.

For the current pilot show **“Conversation restoration has not been checked.”**
and disable starting/sending with that agent. Existing ACP conversations can
be listed/read through Paseo but cannot send merely because their snapshot has
generic persistence flags. If showing an existing candidate conversation,
explain **“This conversation cannot continue until restoration is checked on
your computer.”** Do not offer a replacement new conversation as continuation.

After a future verified host extension, the required behavior is load the same
native session handle before sending; list+load is also acceptable. Missing
handles or load failure must keep sending unavailable, with **“This agent
cannot reopen this conversation. Check it on your computer.”** This future
behavior is not enabled by this slice. The pinned daemon's send path normally
loads persisted agents internally, but can create a fresh session if no handle
exists; the pilot dispatch gate currently prevents reaching either branch.

On disconnect use “Connection lost”. On uncertain mutation delivery use the
existing gateway message: “Delivery is uncertain. Refresh the conversation
before sending again.” Never auto-resend. Old permission cards retire, scoped
inventory/history is refetched, and there is no claim of replaying ACP deltas.

## Security and completion boundary

Connections reject public `ws` **and** `wss`; use loopback, Tailscale or an
owner-managed SSH tunnel. Provider sign-in remains host-managed. No host config
or software install was performed. No new stored formats, keys, provider secrets
or deletion hooks. Any later preference uses `oc.<what>.<profileId>` and registers
its deletion. Links originating outside the app still use `openExternalLink`.

Claude acceptance: kit-only rows/cards, capability-gated unavailable reasons,
English/Arabic copy, accessibility/RTL, no credentials/logs in Details, and no
selectable pilot until host evidence exists. Backend fake transcripts verify
admission and permission safety; they do not prove a live resumable Gemini/omp/fx
journey. The coordinator owns the integration/full-suite gate.

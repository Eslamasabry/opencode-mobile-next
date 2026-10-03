# ACP through private Paseo: frontend contract

Date: 2026-10-03 (owner policy supersedes 2026-10-02). Backend/domain: Codex. Kit UI/localization: Claude.
Owner decisions: existing private Paseo; **don't hide unverified-restoration
agents**; provider sign-in stays host-managed. No new server type or provider
credential form. Missing install/sign-in still blocks sending.

## Implemented status and restoration limit

Host-ready/enabled providers are shown and selectable for NEW chats, including
ACP and future configured providers. Keep `resumeSupport=unknown` where the host
does not prove restoration, and show **"Can't reopen old chats"**. On reopening,
show **"Starts a new chat"** before the explicit new-chat action. No silent
fallback, original-row ID reuse, last-prompt replay or automatic replacement.

Paseo 0.9.2 snapshots do not expose negotiated ACP `loadSession`, list/load or
structured auth state. `ready` permits a new-chat attempt; it does not establish
`loginState=ready` or restoration. Generic session persistence/listing flags and
extra undeclared fields are not proof. Known auth-required errors disable the
agent; error, disabled, absent and loading providers cannot send through either
picker or direct calls. See [source evidence](../verification/acp-paseo-pilot-2026-10-02.md)
for the protocol limits; its old resume-only product policy is superseded here.

Gemini, omp/omp-acp, fx and other safe IDs reported by the host use that same rule.
Built-in omp is RPC, not ACP; omp-acp is a separate registration. Commands remain
host-owned data, never shell fragments constructed by UI. Distinct custom ACP
IDs are supported; custom registrations cannot override the existing native
provider identities. Discovery does not
invent installation rows for absent remote providers. The phone catalog has its
own pinned setup/sign-in/readiness gates: [phone contract](agents-frontend-contract.md).

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
| `ready` and `selectable=true` | Selectable for a new chat; show `resumeLabel` if restoration is unverified. Do not display "Signed in" for unknown login state. |
| `checking` | "Checking your computer."; disabled. |
| `needsHostSignIn` | "Sign in on your computer first."; disabled even when restoration is unknown. |
| `hidden/unavailable` | "This agent is not available on your computer."; cannot send. |
| `hidden/disabled` | "This agent is turned off on your computer."; cannot send. |

`resumeVerified` stays false for unknown/unsupported restoration;
`resumeLabel="Can't reopen old chats"` and `resumeNote="Starts a new chat"` are
plain app-authored data. Neither fact hides or disables an otherwise-ready agent.
An auth-required error becomes `loginState=needsHostSignIn` and
`availability=needsHostSignIn`. Raw errors never become UI text. Unknown login
state remains unknown; a host-ready snapshot is not a fabricated signed-in fact.
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

For a reopened session call
`HostAgentProviderGateway.loadHostAgentContinuation(sessionId)`.
It returns scoped `sessionId`, `providerId`, `state`, `requiresNewChat`,
`resumeNote` and fixed `reason`. `existingRoute` retains the established native
flow; `liveSession` identifies a chat created on this connection, which can send
without a restoration step. `resumeUnverified`/`missingHandle` mean an explicit
new-chat action is needed to avoid the pinned host's possible fresh fallback.
The legacy `blocked` getter refers only to silently continuing that old row;
it does not block this agent's picker or new-chat admission.

Show **"Can't reopen old chats"** and **"Starts a new chat"** first. After the
person explicitly chooses Start new chat, call
`startNewHostAgentChat(oldId, newChatAcknowledged: true)`. It rechecks provider
availability/sign-in failures and returns a DIFFERENT draft ID in the same
project with the same agent. Open that draft before accepting/sending the new
prompt. Refresh the scoped feed and retain both old/new route identities.
Passing false throws `newChatRequired` without mutation. Dismissal does
nothing; the old chat remains readable. Never auto-call this on failure/Resume,
resend an old prompt, relabel an old conversation as new, or queue the action.

New ACP chats can send and continue while live on the current connection. A
reopened ACP row cannot take the daemon's send path merely because generic
persistence flags/handle exist: that path can silently start fresh when load is
unsupported. A missing native handle also requires that explicit action. Direct sending then returns `PaseoFailureKind.newChatRequired`
with fixed copy; it does not create a replacement. The controller translates
this failure back to the domain continuation warning; kit UI never imports
Paseo transport/failure types. Disconnect, scope change,
retirement and error/closed snapshots retire live admission. A future verified
load path must retain the original handle; failed/missing load requires the
same explicit new-chat acknowledgement before replacement.

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
English/Arabic copy, accessibility/RTL, no credentials/logs in Details, and selectable host-ready agents with restoration labels and explicit
new-chat acknowledgement on resume. Backend fake transcripts verify
admission and permission safety; they do not prove a live resumable Gemini/omp/fx
journey. The coordinator owns the integration/full-suite gate.

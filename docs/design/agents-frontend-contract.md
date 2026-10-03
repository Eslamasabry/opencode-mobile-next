# Agents on this phone — frontend contract

2026-10-03; backend branch `codex/agents-on-phone`, base `ff04beb6`.
Claude owns kit UI and English/Arabic localization. No UI/controller files changed here.

## Domain surface and chip

UI imports `lib/domain/agent_catalog.dart`, `phone_agents.dart`,
`phone_agent_host.dart`, `agent_sign_in.dart`, `chat_feed.dart` and
`merged_chat_feed.dart`. It never imports protocol clients, builtin Linux,
installer scripts, the method channel, or credentials. The app shell supplies
`PhoneAgentHost` and `AgentSignInSession`; the controller owns concrete adapters.

Keep the project chip and add the agent chip beside it. Selecting an agent
changes the runtime within the current phone profile and project. The project
path stays `/root/projects/<project>`; no profile picker, terminal or second
“This phone” entry. Existing OpenCode 1/2 selection keeps its existing gateway.

`AgentCatalog.builtIn` supplies stable IDs, names, icon keys, routes, recipes,
per-architecture artifact sizes and sign-in methods. `buildAgentRow(...)`
combines a descriptor with matching `PhoneAgentRuntime` and the actual gateway
`ServerCapabilities`. Never infer a capability from a name, installation, or
successful connection. Missing/mismatched facts leave all capabilities false.
`runtime.signInPhase == null` means not inspected; it never means signed out.

Use `setupVisible` for the sheet inventory, including uninstalled candidates.
Use `fixAction` to enter setup/check/sign-in/resume. `chatSelectable` governs
committing a runtime for chat. `chatVisible` governs qualified chat choices,
not the inventory: hiding uninstalled Claude would remove its Install journey.
`hiddenReason` remains available for an explanation. The default resume policy
is `hide`; `label` explicitly permits new chats with “Can’t reopen old chats”.
The owner has not changed the earlier hide policy. Installation/self-test is
available without silently granting chat admission.

Suggested localization keys (copy is app-authored; do not display host errors):

| Key | English / condition |
|---|---|
| `agentChipLabel` | `{agent} ⌄` |
| `agentChooseTitle` | Choose an agent |
| `agentNeedsInstall` | Install to get started |
| `agentInstallAction` | Install |
| `agentCheckingSignIn` | Checking sign-in… |
| `agentSignedOut` | Signed out · Sign in (only inspected host truth) |
| `agentSignInAction` | Sign in |
| `agentStoppedBackground` | Stopped in the background · Resume |
| `agentResumeAction` | Resume |
| `agentPhoneCheckNeeded` | Run the phone check before using this agent |
| `agentPhoneCheckAction` | Run phone check |
| `agentResumeUnverified` | Can’t reopen old chats |
| `agentUnavailable` | This agent isn’t available here yet |
| `agentPlanLimitReset` | `{agent} plan limit reached · resets {time}` |
| `agentPlanLimitUnknown` | `{agent} plan limit reached · try again later` |

Limits require `limitReached` from the host. Format `resetAt` in the person's
locale/time zone when non-null. Current Claude auth status cannot supply reset
hours; never parse a guessed time or turn every failure into a plan limit.

## Same-sheet setup

The same sheet becomes `agentSetupTitle`: “Set up {agent} · {size}”. Read the
selected architecture's `downloadBytes` and `installedBytes` from the recipe.
These are agent payload evidence, not total setup or peak disk use. Shared Linux,
Python, pinned Node and the pinned Paseo dependency closure may also be needed.
Until all missing components' sizes are known, use `agentSizeUnknown`: “Size not
available”, and optionally show the known agent payload under Details. Never
label the agent payload as the complete download. Use existing download consent
rules; unknown sizes cannot be treated as small. Do not guess disk-space checks.

There is one Install action. `host.install(id)` starts the existing durable
component job, then observe `setupChanges`/`setupProgress`. It returns after
handover, not after completion. Show a determinate bar only when `fraction` is
present. Map `componentId` to app-authored names; do not show shell stages,
`SetupProgress.error`, log tails or service logs. Jobs restore through
`restoreInstall()` and are tagged with profile/agent ownership. Cancel stops only
that owned setup job; completed files remain for a retry. Do not start another
Linux/OpenCode setup job concurrently.

| Key | English |
|---|---|
| `agentSetupInstalling` | Installing {agent}… |
| `agentSetupPreparing` | Preparing the agent connection… |
| `agentSetupInterrupted` | Setup stopped · Continue |
| `agentSetupFailed` | Setup didn’t finish · Try again |
| `agentSetupBusy` | Another setup is running · Check again |
| `agentSetupUnsupported` | This phone cannot run this agent yet |
| `agentSetupStorageFailed` | Couldn’t save this setup · Try again |
| `agentSetupVersionFailed` | The installed agent didn’t pass its check |
| `agentSetupConnectionFailed` | The agent connection didn’t start · Try again |

After installation, run `host.selfTest(id)` for qualification. Its four visible
steps are Install → Version → Connection → Ready. Version also checks oc's
read/write access to the shared projects bind. The final step sends only an
authenticated Paseo `hello`; it makes no model request and opens no login.
`AgentPhoneCheckResult.arm64Qualified` is true only for an ARM64 pass. x64 does
not unlock ARM64. Proof is invalidated by architecture/payload/host pins and
cleared before a failed retry. This proves installation/host readiness, not
provider sign-in or session restoration. Provide this callable check to the
owner's real ARM64 phone; do not claim it has been run by these tests.

## Host sign-in

Inspect with `session.inspectStatus()` before showing account status. For
Claude, explicit `start()` uses the pinned CLI's subscription login; an already
signed-in subscription skips browser login. Console/API-key credentials never
qualify as a subscription. Native credentials live in the profile's Linux home.
The app never requests or reads provider tokens, keys, email or account IDs.

| State | UI/action |
|---|---|
| initial `inspected == false` | Checking sign-in; no signed-out claim |
| `signedOut`, inspected | Sign in |
| `urlReady` | Open the sign-in page; button passes `authorizationUrl.uri` to `openExternalLink` |
| `awaitingCode` | Keep the same page; paste the browser's one-time code and Continue when `acceptsCode` |
| `signedIn` | Signed in; return to the agent/chat journey after admission gates |
| `limitReached` | Use the limit copy above; no guessed reset |
| `failed` | Plain retry/reason from `AgentSignInFailure`, never exception text |

`AgentAuthorizationUrl` and `AgentSignInCode` are ephemeral/redacted objects.
Do not persist them, put them in diagnostics, clipboard history, analytics or
print their underlying values. Clear the code field immediately on submission;
submit it once through `session.submitCode(AgentSignInCode(value))`. Do not let
API keys enter this field. The complete Claude browser code includes its state
suffix. Native stdin receives it; it is never a shell argument or terminal event.

For `apiKeyHost`, show host-managed sign-in data and no app key field. The current
phone adapter supports Claude browser login only; other agents use the same
domain state machine but remain unavailable until their host protocol is
qualified. Use `agentSignInUnavailable`: “Sign-in isn’t ready on this phone yet”.
Do not substitute instructions to type commands or open Termux. Gemini/Codex/omp/fx
browser flows and Goose/Qwen host keys are follow-up adapters, not claimed ready.

On sheet dismissal, agent/profile change or deletion, await `session.close()`.
If drain is unconfirmed, keep retry/close pending; never start a replacement
flow while the old flow can still accept a code. Cancellation changes local
state, not the truth of whether the host account remains signed in.

## Chat feed, permissions and restart

`ChatFeedItem.agentId`/`agentLabel` label each row in the one list. Unknown agents
use “Other agent”; do not use daemon error/log text as a label or preview.
Use item `identity` for list keys; session IDs alone collide across sources.
`ChatFeedFilter.agentId` composes with project, attention/running and subagent
filters. Ordering is Needs you, Running, then most recent, with stable ties.
`MergedChatFeed.sourceChecks` retains each source's loading/complete/acrossProjects
flags. A partial Paseo source must not make OpenCode disappear or claim the phone
has no other chats. The current Paseo source is scoped to one project; the
controller can add separate scoped sources for remembered projects.

Open rows through `merged.routeFor(item)` / `merged.sourceFor(route)`, retaining
source, agent, directory and session. Never route a row through whichever gateway
is currently active. Empty drafts keep the agent chip selection for first prompt.

Gate images and permission controls using the row's capabilities intersected
with `ServerCapabilities`. Resume/model/cancel proof stays false until the host
actually supplies it; `cliSessionResume` is the older Continue-on-computer feature,
not an ACP load flag. Existing native Paseo routes retain their established
permission contract. ACP uses `HostAgentPermissionRequest` and its opaque choices:
“Allow once”/“Don’t allow”; send the matching `selectedActionId`. Deny/cancel is
default; no standing grant is synthesized. No choice means deny/cancel or keep
blocked, never auto-approve. An unanswered request must not disappear just because
cancel was acknowledged. See [ACP card contract](acp-frontend-contract.md).

After Android stops the host, show “Stopped in the background · Resume”. Resume
starts the same loopback daemon, rechecks sign-in, loads the existing session and
refreshes scoped feed/status/permissions. It never resends the last prompt or
creates a replacement chat. If restoration is unavailable, keep the original row
with “This agent can’t reopen this chat yet”. Android's existing foreground-service
timeout/stop policy still owns lifetime; no assumption of unlimited background work.

## Controller hook and current qualification boundary

Do not edit `connection.dart` or its parts for this branch. Claude's controller
hook should retain one `BuiltinPhoneAgents` per existing phone profile, supply
its domain host/sign-in data, and select OpenCode's existing gateway or
`openGateway(projectDirectory)` behind the agent chip. Bind agent/user actions
before handing the draft to the composer; do not change the profile's backend.
Refresh/replace scoped feed sources after gateway events/project changes and
merge them with the existing OpenCode source. Persist last-used project through
the provided source callback under an `oc.<what>.<profileId>` key.

Before full profile deletion: close auth, cancel the owned install, stop/dispose
host clients, and close/clear feed sources. `ProfileStore` then requires native
process drain/profile-home deletion before deleting `oc.agentHostSecret.<id>`;
the existing preference sweep removes install/gate metadata. Shared binaries and
projects survive. The clear-all-saved-sign-ins path also drains/erases phone
agent homes; deleted native owners stay blocked until an app process restart.
The controller must close clients and offer restarting the app after that reset,
rather than retrying a deleted owner. Controller-supplied capability evidence must come from verified
host operations, not catalog constants. A phone hello cannot authorize resume.

Pinned Paseo 0.9.2 does not expose negotiated ACP `loadSession`/auth proof; the
prior pilot intentionally blocks generic ACP dispatch. This branch registers
candidate host commands and adds installation/login/feed groundwork; it does
not widen that admission rule. Existing Claude/Codex native routes remain usable
through their established adapter once the controller supplies their verified
resume evidence. A future catalog entry supplies installation/config/discovery;
a new browser vendor also needs an audited login handler, and an ACP provider
still needs host resume evidence before selection.

## Why the projects stay shared

Native project storage is already outside the Ubuntu rootfs and bound at
`/root/projects`. oc gets that same bind inside a separate PRoot view: mask its
`/root` parent with an empty directory, then bind projects beneath it. Root's
OpenCode view remains intact. There is no chmod of root's private home, migration,
copy, or compatibility symlink. The root setup step creates oc; actual installer,
Paseo and agent execution use `--change-id=1000:1000` and clean scoped homes.
[PRoot's identity mapping](https://github.com/proot-me/proot/blob/master/doc/proot/manual.rst)
is compatibility, not an additional OS security boundary. Private host/auth
processes are tracked by the existing Android service owner, and raw output is
consumed/discarded privately; it is never a user Details log.

Primary pin/login/host evidence: [catalog](agent-catalog-evidence-2026-10-03.md),
[Claude login](agent-sign-in-evidence-2026-10-03.md),
[Paseo dependency closure](paseo-phone-install-evidence-2026-10-03.md).

# BA10: remove one phone agent

Finish line: removing one supported phone agent frees its installed payload and
temporary install files, while its accounts, conversations and the shared host
remain available. Account deletion, sign-out and process termination are outside
this operation.

## Frontend API

`PhoneAgentRemovalSource` is an optional domain interface implemented by
`ConnectionController`. Existing `PhoneAgentsSource` implementations need not
implement it. Import `lib/domain/phone_agents_source.dart`.

- `bool canRemoveAgent(String agentId)` — cheap held-state projection.
- `String? get removingAgentId` — null when idle; notify listeners when it changes.
- `Future<AgentRemovalResult> removeAgent(String agentId)` — await confirmation.

`AgentRemovalResult` lives in `lib/domain/phone_agent_host.dart`:

- `String agentId` — the requested catalog ID.
- `int freedBytes` — allocated bytes in the removed files, measured without
  following links; never an estimate from a download size or a device free-space
  delta. Shared files and retained account data are excluded.
- `bool alreadyAbsent` — no target payload, launcher, staging or lock existed.
  An absent agent returns zero bytes and still receives the running-process check.

Host adapter: optional `PhoneAgentRemovalPort.removeAgent(String agentId)` returns
the same result. `BuiltinPhoneAgents.removeAgent` supplies it. No UI bridge,
Kotlin changes or private shell output are required.

Supported IDs: `codex`, `gemini`, `qwen`, `goose`, `omp-acp`, `fx`. Claude Code is
excluded: its owner-managed install and real account must remain untouched.
Removal is available for installed or interrupted/failed installation rows when
the host supports the operation. Do not offer it during another removal.

## UI states and copy

Offer “Remove {agent name}”. Confirm with “Remove {agent name} from this phone?”
and “This removes the installed agent. Your accounts and conversations stay.
You can install it again.” Confirmation actions: “Cancel” / “Remove”.

While pending, show “Removing {agent name}…” and disable repeated removal and
new install actions. Existing conversations remain listed. On success show
“{agent name} removed. Freed {formatted bytes}.” If `alreadyAbsent`, show
“{agent name} is already removed.” Refresh the target row to its install action;
keep other agents' readiness, account state, phone-check gates and chats.

## Fixed failures

Controller throws `ProductException` with exactly these safe messages:

- Unsupported: “This agent can't be removed here.”
- Busy (target running, an install/check/launch/removal in progress):
  “This agent is in use. Finish its work and try again.”
- Unavailable, unsafe paths, failed deletion or missing measurement receipt:
  “Couldn't confirm this agent was removed. Check this phone and try again.”

Host throws `AgentHostException`: `busy`, `unavailable`, or `stale` if its owner
was disposed/replaced. Raw native output, paths, credentials and CLI errors never
enter user copy. A failed confirmation may follow a completed deletion; retry is
safe and returns the observed current state. The controller must not overwrite
a replacement owner's rows with a late result.

## Removal boundaries and ordering

Reserve the host operation before asynchronous checks. Refuse active setup jobs,
pending payload launch/resume/send operations and live target processes. Never
kill an agent or stop Node/Paseo to make removal succeed.

Delete only the catalog-owned target under
`/home/oc/.local/share/oc-agents/{agentId}`, including old versions and `.new`
staging, its authored launcher and decimal-PID temporary launcher links, and its
empty abandoned install lock. A live installer/target refuses removal; unsafe or
unrecognized lock content refuses removal. Take the same atomic installer lock
and recheck safety/running state before deletion. Do not follow links out of the
target; refuse replaced ancestor directories or foreign launcher targets.

Keep shared Node, Paseo, other agents, `.oc-profiles` account homes, stored chats,
credentials and phone-check gates. Use the fixed `oc` setup view for deletion.
The measured result crosses a narrowly validated numeric receipt, not arbitrary
agent output. Receipt files are temporary and removed after reading.

## Required evidence

Failing-first tests cover each recipe, absent/idempotent removal, stale lock and
staging cleanup, allocated-byte receipts, live process refusal, unsafe links,
concurrent install/launch exclusion, retained account/chat/shared files, owner
replacement, and fixed errors. Device proof uses APK 2197 and the shared emulator
lock, one agent at a time, recording version, cleanup, bytes and retained Claude.

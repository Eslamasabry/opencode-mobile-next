# ACP Paseo pilot evidence — 2026-10-02

> Policy update 2026-10-03: resume-only admission is superseded. Unverified agents
> are shown and selectable for new chats after install/sign-in gates; reopening
> requires the explicit "Starts a new chat" acknowledgement. Historical checks
> below describe their original candidate. See the current frontend contracts.

Branch base: `codex/acp-paseo-pilot`, `071a76f1`. Install pin is 0.9.2 in
`lib/termux/scripts/local_agents_script.dart`; historical gateway comments refer
to 0.8.0. No daemon config/login/install or public relay was used for this slice.
Owner approved private Paseo, verified resume only, and host-managed login.

## Source findings that change readiness

The pinned [wire schema](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/protocol/src/messages.ts)
reports provider status/enabled/source/models/modes and raw error. It has no
transport family, negotiated ACP capability or structured host auth fields.
The [generic catalog probe](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/generic-acp-agent.ts)
does initialize + session/new, discarding negotiated capabilities/auth. Generic
[ACP capabilities and restoration](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/acp-agent.ts)
set generic persistence/listing flags true, while actual restoration separately
chooses loadSession, experimental resume or failure. Snapshot readiness is not
proof. The original research's broad provider picker feasibility predates this
owner's stricter resume admission requirement.

| Candidate | Pinned path | Current pilot decision |
|---|---|---|
| Gemini | Host `extends: acp` provider with host-installed command/version | Hidden: no negotiated load/auth evidence reaches app. No live resume proof here. |
| omp | Built-in native RPC-ui; session file restoration | Hidden for this ACP preview. Native persistence does not prove ACP load/list. A separate `omp-acp` ID would still need negotiation evidence. |
| fx | Host `extends: acp` provider with host-installed command/version | Hidden: no negotiated load/auth evidence reaches app. No live resume proof here. |

Native omp source:
[agent.ts](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/omp/agent.ts),
[runtime.ts](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/providers/omp/runtime.ts).
[Registry](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/provider-registry.ts)
reserves builtin IDs; adding a derived ACP provider cannot replace `omp`.
Existing Copilot support is also ACP internally; this slice preserves existing
routes, and does not claim to have re-certified their restoration.

Paseo's ACP option selection falls back from reject_once to reject_always.
The client therefore always supplies an exact recognized one-call ID; if none
can deny, it cancels instead. The
[lifecycle cancel handler](https://github.com/getpaseo/paseo/blob/v0.9.2/packages/server/src/server/agent/lifecycle-command.ts)
can acknowledge cancellation without interrupting an idle agent. The client
must retain the card unless a scoped snapshot or resolved push confirms removal.
Approvals never reconnect onto a different epoch or project. Raw catalog/RPC/turn/
timeline errors become fixed copy; unknown timeline diagnostics are dropped.

## Built and remaining host contract

Implemented immutable domain discovery/login/resume/permission data and scoped
continuation state (missing handle versus unverified restoration); default-off
capability flags; scoped snapshot refresh/invalidation; missing-proof filtering
in both picker and direct prompt admission; exact permissions and safe default
denial; public endpoint rejection. No UI or persisted provider authentication.

A host extension is prerequisite to enabling the pilot. It must report
allowlisted provider ID, exact host agent/version and transport, negotiated
loadSession (or list+load), structured login requirement, and a scoped intact
native session handle; persist/reload must retain identity and fail on load
failure instead of new-session fallback. Do not accept ad hoc optional fields
as proof in the current wire schema. Pin/test the extension independently.
Owner options: keep this discovery groundwork unavailable (safe default); or
review/build that host metadata extension next. A separately reviewed native
omp persistence exception is another decision, not silently implemented here.

## Verification

Pinned Flutter pub get ran once. Pinned Dart format uses language version 3.10.
Final focused run (machine-locked, --no-pub --concurrency=1): 63 passed across
host_agent_providers_test.dart (6), paseo_acp_pilot_test.dart (27), and
paseo_gateway_test.dart (30). Initial fixture compile/teardown defects were fixed;
the final three-file candidate passed together. Repository analyzer result is
recorded in the c3 report. No new analyzer ignores.
No full-suite or live Gemini/omp/fx restart/auth/permission test is claimed.
The [frontend contract](../design/acp-frontend-contract.md) defines exact states,
copy, cards and the distinction between implemented discovery and enabled pilot.

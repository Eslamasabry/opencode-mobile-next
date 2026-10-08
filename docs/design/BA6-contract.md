# BA6 — OpenCode 1 agent cards

Finish line: the evidence-backed OpenCode 1 direct card tool becomes ready only
when the managed helper is intact and the running pinned server reports its MCP
connection; the chat and list answer journeys must be recorded on the emulator.
Non-goals: qualifying OpenCode 2, changing native isolation, starting/restarting
servers, changing credentials, and adding a new UI flow.

## Trust decision

The owner chose option A in
[in-app Ubuntu trust](../verification/in-app-ubuntu-trust-2026-10-07.md): all
processes in that Ubuntu share one Android UID and one trust zone. PRoot's guest
users, hidden `/root`, and file ownership checks prevent accidental misuse but
do not isolate agents from the server. Registering the display-only `oc-ui`
helper accepts this existing exposure. This supersedes the older root-isolation
qualification block in the October 7 OpenCode gap report. It does not assert
Landlock protection or a kernel boundary.

The installer still refuses symlinks, writable/foreign-owned guest paths,
unknown entries, conflicting JSONC overlays and damaged ownership manifests.
Those checks are installation guards, not a security boundary. The verifier
never prints its password, authorization header, HTTP payloads or exceptions;
it does not fetch provider configuration or credentials. The server remains
loopback-only, and the helper never receives the server password.

## Unchanged frontend API

`GenUiInstaller.setEnabled(profileId:, agents:, enabled:)` and
`GenUiSetupVerifier.verify(profileId:, agent:)` retain their signatures.
OpenCode 1 joins Claude Code in `AgentToolAdapter.cardsQualified`; OpenCode 2
stays unqualified. Existing connection and kit rendering consume status as
before. No UI files change.

States returned by `ManagedGenUiInstaller`:

| State | Meaning | Plain words / way forward |
| --- | --- | --- |
| `GenUiSetupOn` | Every requested agent passed its live check | Agent cards are ready. |
| `GenUiSetupPartial` | Some agents passed; the rest failed or are unqualified | Cards are ready for the listed agents. Check the other agent's setup. |
| `GenUiSetupFailed(verificationFailed)` | Owned registration exists, but the live runtime could not be verified | Cards could not be checked. Make sure the server is running, then try again. |
| `GenUiSetupUnavailable(notQualified)` | No evidence-backed transport/verifier | Agent cards are not available for this agent yet. |
| `GenUiSetupRestartRequired` | Managed config removed; an active catalog may retain it | Restart the server to finish turning cards off. |

Registration alone never becomes readiness. For OC1, the root bridge verifies
helper bytes, enabled marker, exact owned MCP config, profile ownership, safe
Node path, then requests the fixed loopback server using its private password
file. `/global/health` must say healthy and version `1.18.32`; `/mcp` must say
`oc-ui.status == connected`. Each request has a 5-second timeout and a 64 KiB
response cap. No redirects, config dumps, credential forwarding or server
restart are performed. The probe checks the server's startup directory
`/root/projects`; project-specific overrides can still make a particular
project unavailable. A missing marker, unknown owner, changed helper, stopped
server, auth failure, wrong generation/version or disconnected MCP fails shut.

Claude verification retains its pinned CLI and helper-discovery checks and runs
in the agent view. OC1 verification runs in the root view, which can access its
managed config; OC2 is rejected before running a script.

## Evidence boundary

The captured OC1 tool parts are in
[`runtime_opencode1_tool.json`](../../test/fixtures/genui/runtime_opencode1_tool.json).
The original SHA256 is
`47eff268d0240e0fc3d96fba94503fe0906b5efe8bf34a5a007143934eaea7e7`.
The October 7 maintainer capture records OpenCode 1.18.32, connected `oc-ui`, a
rejected malformed call, and a completed `oc-ui_show` with the accepted display
receipt. Tests parse these actual inputs, not invented wire names.

That fixture proves the direct tool dialect and acceptance. It does not prove
the Flutter card render, a user answer receipt, list interaction or restart
recovery. Current BA6 evidence and outstanding device steps are in
[BA6 QA](../qa/BA6-2026-10-07/README.md). Keep the complete item blocked until
those steps are recorded against the candidate APK.

## APK 2195 runtime-switch follow-up (2026-10-08)

Qualification belongs to a connection generation as well as the shared phone
owner. Retiring a transport drops cached setup/attempt state; the connected
runtime is checked again after the new transport becomes ready. Late results
from the retired generation cannot publish readiness. Explicit enable/disable
writes still drain, so changing connections cannot lose a queued disable.
Runtime qualification also runs in All projects, without a selected directory;
only directory-specific Cards source registration needs a project folder.
A phone check retries a current Cards verification failure when Cards is on and
the connection is ready. It never turns Cards on when the owner turned it off.
The strict production verifier and its runtime/file/auth guards are unchanged.

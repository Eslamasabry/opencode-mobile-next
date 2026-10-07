# BA1 / BA3 / BA8: phone agent authentication contract

`AgentPhoneScripts.authProbe(AgentDescriptor agent) -> String` emits exactly one
sanitized JSON object. Run it as Linux uid 1000 with profile-scoped `HOME`,
`CLAUDE_CONFIG_DIR=<HOME>/claude`, `CODEX_HOME=<HOME>/codex` using the requested private
native auth executor described below. The host must validate the profile ID before
constructing authored exports. Keep raw process output private. The script
requires the exact `.oc-profiles/<id>` namespace; generic `/home/oc` is rejected.

`AgentAuthProbeResult.fromJson(Map<String, dynamic>)` has immutable fields:
`state: AgentAuthProbeState` (`signedIn`, `signedOut`, `error`),
`accountDisplayName: String?`, `error: AgentAuthProbeError?`.
Unknown/malformed/contradictory JSON returns `invalidResponse`. No provider
snapshot error string participates in authentication classification.

Loading is the controller's pending state. A normal probe spends at most eight
seconds inside the CLI; the controller applies a ten-second overall deadline.
The process owns a new process group and kills only that group when cleaning up.
It caps captured stdout at 64 KiB and discards all stderr. The bridge receives
only state, a dedicated account display field, and a fixed enum error.

| Result | Plain words | Way forward |
| --- | --- | --- |
| signedIn + account | Signed in as {account} | Start a chat |
| signedIn without account | Signed in | Start a chat |
| signedOut | Sign in to use this agent | Sign in |
| probeUnsupported | This agent cannot check sign-in yet | Open its sign-in terminal; qualification is still needed |
| timedOut | The sign-in check took too long | Try again |
| notInstalled | This agent is not installed | Install the agent |
| signInExpired | Sign in again to use this agent | Sign in |
| hostUnavailable / invalidContext | The agent could not check sign-in | Restart the phone helper and try again |
| invalidResponse | The agent could not confirm sign-in | Try again; open Details for the fixed error code |
| signOutFailed | The agent could not confirm sign-out | Try again |

After terminal sign-in, keep the run pending until `authProbe` says `signedIn`.
Successful exit, printed success, provider availability and a stale earlier
probe cannot complete sign-in. `signedOut` means the sign-in is still needed;
`error` ends with the corresponding recoverable failure.

`AgentPhoneScripts.supportsSignOut(agent) -> bool` currently qualifies commands
for Claude and fx only. `AgentPhoneScripts.signOut(agent) -> String` executes
`claude auth logout` or `fx logout` privately and then runs the same status
probe. It succeeds only with `signedOut`; exit zero followed by a signed-in
status returns `signOutFailed`. The UI should expose Sign out only where
supported and signed in. No generic token/config deletion implements logout.
fx may retain independently configured API-key credentials after its OAuth
logout; the final probe correctly reports this as unconfirmed sign-out.

Both installed emulator agents have sanitized real captures under
`test/fixtures/agent_auth/`. Unsupported noninstalled agents remain errors
until their own bounded status and logout protocols are qualified.

## Native integration BLOCKED — BB request

`BuiltinLinux.run(agentUser=true)` accepts only PhoneAgentCheckOutput receipts,
so it deliberately strips this JSON. Do not relax that filter or use the root
`run` bridge to get account output. The Dart adapter now calls the absent private
`agentAuthProbe` method. Until BB supplies it, other-agent auth is unavailable,
logout is hidden, and Claude retains the existing native direct auth-status
fallback. Script device captures do not prove this absent channel integration.

Requested callable contract (BB owns MainActivity/BuiltinLinux/PhoneAgentSignIn):
`agentAuthProbe({profileId, agentId, action: probe|logout, script,
timeoutSeconds: 10|20}) -> {state, accountDisplayName?, error?}`.
`script` is only app-authored AgentPhoneScripts output with validated catalog ID
and profile exports. Native must validate IDs/action/size, run through
startAgentProcess(profileId, private argv), reject blocked/deleting profiles,
track the exact private process under its profile, enforce the deadline and
stop that process tree on timeout/profile deletion. Clear stale Claude login
locks using the existing safe no-live-process check before inspecting status.

No setup log, transcript, diagnostic or notification may receive raw stdout,
stderr, account data, script results or exceptions. The native result must
independently validate state/error enum + bounded optional dedicated account
label, returning only that projection. Unknown fields/JSON or output overflow
are invalidResponse; missing bridge returns probeUnsupported. Preserve the
existing PhoneAgentCheckOutput filter and generic setup API unchanged.

The host advertises logout only after this private bridge responds. Full BA1,
BA3 and BA8 item completion awaits BB implementation, candidate APK and UI
integration. Do not mark the fake-channel tests as native device proof.

# Agent sign-in evidence and frontend contract — 2026-10-03

Finish line: one safe, transient sign-in state machine and a callable private
host adapter for Claude subscription browser sign-in. Non-goals: UI, token/key
capture, new OAuth clients, account login during development, generic terminal
transcript parsing in Dart, and credential persistence in the app.

## Exact pinned Claude contract

The existing built-in installer pins Claude Code **2.1.283**, Linux x64 SHA-256
`1859583ce32920595c61ef868bee52e1b1594f7486db209935e01f1e5e804ae2`.
Downloaded the [official pinned executable](https://downloads.claude.ai/claude-code-releases/2.1.283/linux-x64/claude)
to `/tmp/oc-c5-claude-2.1.283` for read-only research; its checksum matches and
`--version` returns 2.1.283. No global software installation or account login.
The installed workstation CLI is 2.1.288 and is not evidence of the pin.

Callable pinned help confirms `claude auth login --claudeai` (subscription)
and `claude auth status --json`. It does not offer a device-code, pasted-code,
no-browser, or JSON-login flag. Embedded distributed source, located from
`export{de as authLogin` and the preceding `async function de(`, shows:

- Login uses a readline interface over **stdin**; a PTY is unnecessary.
- The CLI prints a browser authorization URL and accepts a line containing
  `authorizationCode#state`; incomplete input is rejected.
- PKCE, OAuth exchange, credential writes and refresh stay in the CLI process.
- The subscription authorization constant is exactly
  `https://claude.com/cai/oauth/authorize`; Console uses a different route.
- Managed policy can select a different login method or a gateway. Do not
  silently switch subscription billing or use that alternate flow.
- A refresh-token environment variable selects another login path. The native
  launcher must use the controlled host identity/environment and neutral cwd.

The [vendor authentication documentation](https://code.claude.com/docs/en/authentication)
describes browser sign-in and the pasted-code fallback for remote/container
callbacks. These current docs are supporting evidence; the distributed pinned
implementation and help establish the actual callable seam.

With a temporary empty HOME and CLAUDE_CONFIG_DIR, no inherited environment
credentials, and telemetry/updater disabled, pinned `auth status --json`
returned exit 1 and `loggedIn:false`, `authMethod:"none"`,
`apiProvider:"firstParty"`. Embedded status source also emits account metadata:
native must project only boolean/enumerated truth and discard email, org,
directory and credential-source text. Subscription readiness requires
`loggedIn:true`, `authMethod:"claude.ai"`, `apiProvider:"firstParty"` together.
It also requires absent/null `apiKeySource`: pinned source assigns claude.ai
authMethod to a legacy `/login managed key`, so the triple alone is insufficient.
An API-key, injected OAuth-token, third-party or gateway status must not become
subscription readiness. No reset timestamp is supplied by this status command.

Paseo is not a sign-in transport for this slice. Its [Claude guidance](https://github.com/getpaseo/paseo/blob/v0.9.2/public-docs/claude-code.md)
directs reauthentication through the CLI, and its [security documentation](https://github.com/getpaseo/paseo/blob/v0.9.2/public-docs/security.md)
keeps agent authentication with the wrapped agent. App raw local terminal
channels retain native recent output and Dart scrollback; `BuiltinLinux.run`
returns combined output. Neither should carry this private auth session.

## Implemented Dart boundary

- Domain: `lib/domain/agent_sign_in.dart`.
- Native adapter: `lib/builtin/agents/agent_sign_in.dart`.
- Private native controller: `android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/PhoneAgentSignIn.kt`.
- Shared method enum: `AgentSignInMethod` from `lib/domain/agent_catalog.dart`.
- States: signedOut, urlReady, awaitingCode, signedIn, limitReached, failed.
- Each run binds profile, agent, random run ID and sign-in method. Late results
  cannot mutate a cancelled/replaced run. A new run requires confirmed drain.
- Browser URL is an ephemeral validated wrapper with redacted `toString`.
  Only the pinned HTTPS vendor route is accepted; UI uses `openExternalLink`.
- Pasted code is transient, redacted, consumed once at the private host sink,
  cleared after submission and never converted into a persisted model.
- API-key agents expose `state.hostOnlyApiKey`; configure their key on the host.
  No key input, return field, shell argument or sign-in mutation exists here.
- Limits use a host-supplied UTC reset time; unknown stays null. No estimates.
- Exceptions are enum-only; native exception messages/details are discarded.

Frozen builtin method channel: `io.github.eslamasabry.opencode_mobile/builtin_linux`.
Methods are `startAgentSignIn`, `readAgentSignInChallenge`,
`submitAgentSignInCode`, `agentSignInStatus`, `cancelAgentSignIn`.
Inputs: `{profileId,agentId,runId,method}`; submit adds transient `code`.
Sanitized responses: `{runId,phase,url?,failure?,resetAt?}`. Unknown fields,
foreign run IDs, invalid URLs and non-UTC/malformed reset times are refused.
Cancellation requires `{runId,drained:true}`, not merely an acknowledgment.
Native must tombstone a cancelled run ID even when start is still in flight.
Native uses the coordinator's clean per-profile process launcher and exact
descendant-stop helper. It verifies the pinned CLI version privately, sanitizes
status, parses bounded private output, and tombstones/drains profile runs before
deletion. This document does not claim that Android integration passed.

Frontend flow: create `AgentSignInSession`; call `inspectStatus` without opening
login; consume `changes`; explicit `start` reuses that run. Initial/cancelled
signedOut has `inspected:false`, so it is not an assertion about credentials.
Open the ephemeral URL through the domain-approved external-link function.
Call `readChallenge` before accepting a code, then `submitCode` once; use
`refreshStatus` for host truth. On dismissal/profile deletion, await
`cancelAndDrain`/`close` before releasing ownership. Clear the text controller
immediately after constructing/submitting `AgentSignInCode`; never include
challenge/code in restoration state, diagnostics, notifications or telemetry.

## Verification and remaining limits

Added focused fake-host and mocked-method-channel tests in
`test/agent_sign_in_test.dart`: code flow/single use, URL rejection, host-only
keys, stale cancellation/drain, failure redaction, strict response binding and
host-only limit/reset truth. No test/analyzer process was run by this worker;
the coordinator owns machine-locked validation. Pinned formatter only.

Immutable Dart strings and method-codec buffers cannot promise memory erasure;
the contract prevents logging/persistence/reuse, not a forensic memory wipe.
No real account sign-in or Android credential storage/device lifecycle proof
was performed. Gemini and other browser agents remain unavailable through this
adapter until their own callable challenge/status protocol is verified.
Root must verify native cancellation kills/drains only the exact private
process and keeps output out of terminal streams, scrollback and logs.

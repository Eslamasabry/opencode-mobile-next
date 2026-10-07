# BA1: profile-scoped agent authentication probes

Finish line: each installed phone agent reaches signed in, signed out or a typed
error within ten seconds, based on its own status command. Non-goal: no new
accounts, account sign-in, model calls, agent installation or certification.

Emulator `emulator-5554`, 2026-10-07. The installed phone executables were Claude
Code and fx (plus Node/npm/Paseo infrastructure). No Codex, Gemini, Qwen, Goose or
Oh My Pi was installed. No APK build or install was needed for direct script
qualification; app/controller integration requires its own validation.

The production Python auth-probe body ran through the installed app's PRoot,
uid 1000, with the same per-profile HOME, CLAUDE_CONFIG_DIR and CODEX_HOME as
BuiltinLinux.startAgentProcess. It used existing profile `1790839392073695`.
Each short device session held `/home/eslam/Storage/tmp/oc-emulator.lock`.
Raw CLI stdout/stderr was captured only inside the private probe process, and
only its allowlisted JSON result was returned. The account identity was replaced
with a fixture stand-in before saving it. No sign-in or logout was run.

| Agent | Command | Result | Total elapsed | Fixture |
| --- | --- | --- | --- | --- |
| Claude Code 2.1.283 | `claude auth status --json` | signed in, account present | 3460 ms | [sanitized capture](../../../test/fixtures/agent_auth/claude-signedIn-device.json) |
| fx 0.0.12 | `fx status --json` | signed out (`auth=missing`) | 174 ms | [sanitized capture](../../../test/fixtures/agent_auth/fx-signedOut-device.json) |

Claude's callable JSON contract is also independently used by the existing native
PhoneAgentSignIn.inspect. fx's pinned upstream implementation confirms the
status schema, active credential source labels, expired flag, and logout command:
[CLI status/logout](https://github.com/vercel-labs/fx/blob/v0.0.12/src/core/cli/cli_surface.zig),
[credential source labels](https://github.com/vercel-labs/fx/blob/v0.0.12/src/core/auth/credentials.zig),
[JSON status projection](https://github.com/vercel-labs/fx/blob/v0.0.12/src/core/output/output_contracts.zig).

Gemini/Qwen/Goose/Oh My Pi status protocols are unqualified. Codex has a structured
`account/read` API, but no pinned device capture is available here. These return
`probeUnsupported`, never signed out, pending a real installed-agent contract.
A credential-source report proves saved sign-in, not model readiness or validity
of future provider requests. fx explicitly expired credentials report
`signInExpired`; unknown source labels report `invalidResponse`.

Focused automated check (lead owns machine slot):

```sh
tool/qa/machine_lock.sh test -- ~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/agent_auth_probe_test.dart
```

Regression mutation: temporarily replace Python `if data['loggedIn']:` with
`if code == 0:` and run the exact test `Claude explicit signed-out status
overrides successful exit`. It must fail; restore the guard and rerun. This
proves a successful process exit cannot replace the explicit status response.

Native integration blocker: generic agent-user run strips JSON receipts, so the
production Dart adapter requests the private agentAuthProbe method described in
BA1-contract.md. It is absent on this base. Existing Claude native status is
retained, other-agent auth stays unavailable, and logout is hidden. Device
script qualification and fake-channel tests do not prove native wiring.

Lead checks: affected serial Flutter tests passed; corresponding negative
control failed behaviorally, and production safeguards were restored. Failed
controls are recorded in this directory. Full repository suite not run.

# Connect the app to Codex app-server

Codex support is experimental and available in the development source.
See [verification status](verification/codex-connection-2026-09-07.md) for the
completed checks and remaining live-provider/device limits.

## Host setup

Run Codex app-server on the computer that owns the project. Keep provider
login in that computer's existing Codex setup; the app does not import, claim,
or display Codex account credentials.

Create a separate random capability token in a file readable only by the
server owner, then start the listener:

```sh
umask 077
python3 -c 'import secrets; print(secrets.token_urlsafe(32), end="")' > /absolute/path/codex-capability-token
chmod 600 /absolute/path/codex-capability-token
codex app-server \
  --listen ws://127.0.0.1:4141 \
  --ws-auth capability-token \
  --ws-token-file /absolute/path/codex-capability-token
```

Use an unused port. Do not put the token in the endpoint, a log, or a shell
history entry. The app sends it as a bearer token during the WebSocket
handshake.

For a device that cannot reach the host loopback directly, use an
operator-owned tunnel. For example, an Android device attached with ADB can
use:

```sh
adb reverse tcp:4141 tcp:4141
```

The app endpoint is then `ws://127.0.0.1:4141`. A remote deployment must use a
TLS WebSocket endpoint such as `wss://codex.example`; the app rejects remote
clear-text `ws://` endpoints. Configure TLS and any tunnel or reverse proxy on
the host yourself.

## App setup

Use this order:

**Add server → Codex (experimental) → ws/wss endpoint → absolute project
folder → separate connection token → Test → Save & connect.**

The project folder is the absolute folder path as seen by the Codex host. The
connection token is the capability token from the protected file, not a
provider API key or a Codex account password.

## Supported behavior

The supported surface is text-only chat with history list/resume,
one-shot approval requests, and cancel/interrupt. Reconnect can resume the
tracked thread history. A pending approval cannot be reloaded after a
connection loss because the app-server contract has no pending-approval list
operation; review that approval in the computer's Codex client.

The app does not claim background attention across profiles, background file
or terminal access, or automatic recovery of an approval that was pending at
disconnect. Provider authentication remains a user-owned host setup.

Offline prompts remain local drafts; reconnect does not automatically send
them. A connection probe checks authentication and a scoped history request;
an empty history result does not verify that the configured folder exists.

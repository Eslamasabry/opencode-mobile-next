# Test stand-in for `claude` (Claude Agent trial proofs)

These files are for emulator and PC proofs only; the app never ships or installs them.

`fake_claude.py` speaks the stream-json control protocol that Anthropic's Agent SDK uses with the `claude` program. OpenCode 2's Claude Agent provider talks to `claude` through that SDK. The file covers:
- the start-up handshake;
- the login check;
- one turn. In that turn it calls OpenCode's `shell` tool (`ls`), or `glob` when no shell is offered, through the SDK's MCP bridge, then replies in text with what the tool returned.

It needs no network and no credentials. If a file named `logged_out` sits next to the script, it answers the login check as signed out.

`startup.json` is the start-up reply. It is cut from OpenCode's recorded fixture `packages/core/test/plugin/provider/claude-agent/fixtures/recordings/startup.jsonl` at v2.2.5 (MIT), with these changes:
- the account replaced by a test one;
- the models cut to default, opus, sonnet and haiku, each named "(test replay)".

## Use

On the emulator, inside the in-app Ubuntu, as root:
1. Copy both files to `/root/.oc-claude-agent-fake/`.
2. Point the app's launcher at the fake by replacing the last line of `/usr/local/sbin/claude` with:

   ```sh
   exec python3 -I /root/.oc-claude-agent-fake/fake_claude.py "$@"
   ```

   Keep the marker line, so the app still treats the launcher as its own.

To go back to the real Claude Code, put the last line back (`exec /home/oc/.local/bin/claude "$@"`), or tap **Check again** in the model picker when it shows: that rewrites the launcher.

## Proof record

The proof itself is recorded in `docs/qa/claude-agent-trial-2026-11-01/README.md`.

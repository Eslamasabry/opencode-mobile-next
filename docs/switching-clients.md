# Adding this app to an existing server

If your other client uses the same supported OpenCode server and project,
OpenCode Mobile can show that server's sessions without a transfer. A client
that keeps its own database, relay or CLI-only sessions may need a different
connection or a supported import; this guide does not promise universal migration.

## The one-step version

An OpenCode server keeps every session server-side. Any client that talks
to it — the terminal TUI, the CLI, a desktop app, a web interface, another
phone app, this one — is just another window onto the same sessions. To
start here:

1. Add the server address and credentials through this app's connection flow.
   Use HTTPS outside phone loopback; another client's plaintext LAN/tailnet
   URL is not automatically accepted here.
2. Select the same project/location, open a session, and review its state
   before sending. Unsupported operations depend on server capabilities.

Same-server history usually needs no export/import. You still configure or
pair this client; credentials and drafts are not copied from another app.
Uninstalling this client does not itself delete server sessions, but mutations
you send while using it remain real server-side changes.

## What is shared, what is local

**Shared (server truth, visible to every client):** sessions and messages,
model/agent/variant selection on OpenCode 2 servers (selections are
per-session and server-authoritative — another client changing them shows
up here after reconnect), running work, provider and MCP configuration,
usage and cost totals.

**Local to this app only:** your drafts, the offline queue, pinned
conversations, the prompt stash, favorites/recents, read/unread history
(when sync is disabled or unavailable), and app appearance preferences. Settings
also contains server-backed controls, so not every setting is local. Removing
the app or a profile deletes only these (see Settings → Privacy and data
use for the exact list).

## Notes worth knowing

- **Requests can change in another client.** This app reconciles the original
  request and scope before replying and on reconnect; a busy/conflict response
  is not necessarily proof it was resolved. Review fresh request state rather
  than retrying an uncertain approval blindly.
- **Attachments and transcripts** you sent from another client appear
  here as normal history.
- **Read receipts** (OpenCode 2) can be synced or kept private to this
  device — Settings → Privacy.
- **On-device server:** if you run OpenCode inside Termux on this phone,
  that is simply a loopback connection; other devices cannot reach it by
  default.

## Connecting over a network

Use the existing in-app connection guide for its shipped setup paths.
[Private-network connectivity](connectivity-private-networks.md) describes
planned secure tailnet/tunnel guidance; it is not a claim that new setup
automation or a cleartext exception is available in the app today.

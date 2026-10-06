# All connections in one Conversations list (2026-10-06)

Follows [one backend per conversation](one-backend-per-conversation-2026-10-05.md).

**Finish line.** When the app starts, the Conversations list shows the chats of
every connection on this phone: OpenCode in the in-app Ubuntu, OpenCode in
Termux, and Claude Code (and other agents). Each row names where it runs. A
chat opens on its own connection, and switching servers is no longer needed
to see or open it. The person can hide any source.

**Non-goal.** Remote servers (VPS, PC over Tailscale) join only when the person
turns them on. Several remote servers live at once is a later slice.

## Today

- The list merges the *active* profile's OpenCode with the phone agents
  (`MergedChatFeed` in `phone_agents.dart`).
- `ProfileMonitor` polls other profiles, but only for attention. It reads no
  titles and keeps no rows.
- A row carries `sourceId`, but no profile. `backendForConversation` finds the
  agent backend by session id only.
- Opening a chat of another profile switches the whole app
  (`prepareMonitoredRequest`).

## Design

1. **A backend pool.** Generalise the single `_paBackend` into backends keyed
   by source. These are the agents (as today) and one `ConnectionController`
   per other local profile, created when first needed and closed with the
   main controller. Each is transient: it never becomes the active profile,
   and it shares the background-live service.
2. **Feed sources.** Each other local profile adds a source,
   `profile:<id>`, to `MergedChatFeed`. Its label is the profile's name
   (“Termux”), and it reads `sessionPage` through that backend. The rows
   carry the source, so `backendForConversation` routes by source and session
   together, never by session id alone.
3. **Start-up.** All sources are read at once.
   - The first paint is held for up to 4 s, as is done today for the agents.
   - After that, rows show, and the quiet line names whatever is still
     loading (“Loading Termux conversations…”) with the bar moving. This is
     already built for the agents in `ChatFeedSnapshot.stillLoading`.
   - Saved rows stand in per source, so a restart paints at once.
4. **Row mark.** Each row starts with what it runs on: the agent's mark and
   name, then the project and Git. When several OpenCode servers show, the
   OpenCode rows add the server name, for example “OpenCode · Termux”.
5. **Hide a source.** The filter row gets a “Showing: all” chip that opens a
   checklist of sources. It is stored per profile as
   `oc.chatSourcesHidden.<profileId>`, so the deletion sweep finds it.
   Hiding a source stops reading it.
6. **Which profiles are local.** The in-app Ubuntu is `BuiltinLinux.managesServerUrl`.
   Termux is any loopback URL that is not built in. Every other profile is
   remote and off by default.

## Slices

| # | Slice | Proof |
|---|---|---|
| A | Backend pool + `profile:` sources, routing by source | controller tests: two OpenCode profiles and the agents, one list, each row opens on its own backend |
| B | Start-up hold + still-loading per source + saved rows | tests + emulator cold start with Termux stopped (line says so, then rows arrive) |
| C | Row server name + Showing chip with hide | widget tests, goldens, emulator screenshots |
| D | Background: alerts and running state for chats of the other profiles go through the pool | emulator: Termux chat finishes in the background and opens from the alert |

## Open decisions

- Should remote servers join automatically when they answer, or only when
  turned on? Proposed: only when turned on.
- Should the active-server picker at the top stay? Proposed: keep it for New
  conversation's default and for Settings, and show “All connections” as its
  label when more than one source shows.

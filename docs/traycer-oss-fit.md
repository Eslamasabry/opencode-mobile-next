# Traycer OSS fit decision

Reviewed: 2026-08-27

## Decision

Do not merge or embed Traycer's OSS clients in OpenCode Mobile. Build a native
OpenCode task cockpit inspired by the useful workflow instead.

The assessment used Traycer upstream commit
[`589a425235b3119220ec3bc7d162fcfaca0958d5`](https://github.com/traycerai/traycer/commit/589a425235b3119220ec3bc7d162fcfaca0958d5).
Traycer's repository explicitly excludes its Host and cloud backends; the public
mobile client is a thin React/Capacitor remote-Host shell rather than reusable
Flutter functionality. Its own mobile documentation says Android has not been
emulator-verified, physical Android is untested, only staging is connectable,
and the production relay has no mobile release:

- [Repository scope](https://github.com/traycerai/traycer/blob/589a425235b3119220ec3bc7d162fcfaca0958d5/AGENTS.md#L3-L22)
- [Mobile readiness](https://github.com/traycerai/traycer/blob/589a425235b3119220ec3bc7d162fcfaca0958d5/clients/mobile/README.md#L36-L94)
- [Mobile architecture](https://github.com/traycerai/traycer/blob/589a425235b3119220ec3bc7d162fcfaca0958d5/clients/mobile/AGENTS.md#L5-L38)
- [MIT license](https://github.com/traycerai/traycer/blob/589a425235b3119220ec3bc7d162fcfaca0958d5/LICENSE)

Bundling Traycer would add a second privileged runtime, Traycer credentials,
relay/cloud trust, more persistent networking, and significant Termux battery
and memory pressure. The released proprietary Host has no redistribution grant
in the public repository, so it must not be bundled.

## Native OpenCode direction

OpenCode already exposes the primitives needed for the valuable experience:
root and child sessions, status, todos, diffs, messages, forks, files,
terminals, and capability-gated worktrees. OpenCode remains the only execution
authority and the selected OpenCode server remains the only data boundary.

Build in this order:

1. Agent tree: project root and child sessions into one live hierarchy that
   reconciles after cold start and wake. This works for old chats without data
   migration.
2. Task cockpit: reuse Files, Review, Todos, Terminal, and Requests as tabs for
   the selected root task instead of creating duplicate screens.
3. Artifacts: add native Spec, Ticket, Story, and Review documents with explicit
   Markdown export and durable anchored comments.
4. Worktrees: capability-detect create/select; guard remove/reset with dirty
   state checks, explicit confirmation, and server-result reconciliation.

Reconsider a Traycer connector only after Traycer ships stable production
Android support, a supported public protocol SDK/API, production relay access,
clear Host redistribution terms, and third-party compatibility guarantees.

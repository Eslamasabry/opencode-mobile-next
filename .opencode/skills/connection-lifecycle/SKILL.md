---
name: connection-lifecycle
description: Use for ConnectionController, reconnects, lifecycle recovery, offline queues, drafts, session scope, async races, or transport replacement.
---

# Connection Lifecycle

## State Model

Classify every value by app, profile, location, session, message, request, or
transport generation. A cache key must include every identity needed to stop
data crossing those boundaries.

`ConnectionController` coordinates transport replacement, repository state,
events, sessions, requests, queues, drafts, notifications, and lifecycle. Treat
`lib/state/connection.dart` as a single-owner high-coupling file.

## Async Rules

- Capture the relevant generation, repository/API identity, profile, location,
  and session before awaiting.
- After every await, reject stale completion before changing state or issuing a
  second operation.
- Retained screens must resolve the current action transport after lifecycle
  recovery; do not retain a gateway that can be closed and replaced.
- Reconnect and event hydration must be idempotent and must not duplicate,
  reorder, or erase newer state.
- Close old channels exactly once without closing their replacement.
- Cancel timers, subscriptions, completers, and observers on disposal or
  ownership change.
- Notify listeners consistently after observable mutation.

## Persistence Rules

- Verify persistence return values whenever failure could lose, duplicate, or
  falsely claim deletion of user work.
- Test persistence by recreating the store/controller, not only by inspecting
  current memory.
- Bound queues and drafts by count, bytes, or age.
- Malformed stored data degrades safely; migrations require failure and restart
  tests.
- Session state that can collide across servers must include profile identity.
- Profile deletion must quiesce concurrent writers and remove all owned state.

## Required Evidence

Use controlled `Completer` ordering rather than wall-clock delays to test:

- old response after a new profile or location;
- lifecycle pause/resume during an action;
- repository/API replacement while a screen is retained;
- repeated resume/disconnect events;
- failed queue save before and after delivery;
- deletion racing with a writer;
- same session/request IDs on two profiles.

Relevant tests include `connection_*`, `app_lifecycle_test.dart`,
`offline_queue_test.dart`, `session_draft_test.dart`,
`session_model_scope_test.dart`, and `profile_deletion_test.dart`.

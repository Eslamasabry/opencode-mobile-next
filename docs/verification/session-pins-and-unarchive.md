# Local session pins and the unarchive boundary

Cycle 16 implements the pinning half of issue #29. Session row menus offer
**Pin on this device** and **Unpin**, including desktop context menus. Workspace
shows a Pinned section before Active and Recent; pinned rows retain attention
and running state. Other consumers of `sortedSessions()` receive pinned-first
ordering with normal recency ordering inside each group.

Pins are device preferences, separated by saved server profile and selected
directory/workspace. They survive restart and do not synchronize to other
clients. A head refresh fetches pinned IDs absent from the loaded inventory,
using the existing coalesced, scope-guarded direct-session reads. This keeps older
pins reachable without exhausting every history page. Failed detail reads keep
the preference and expose refresh recovery in the inventory footer.

Preference writes serialize per profile and update visible pin state only after
storage acknowledges them. A failed write does not report success. Row actions
capture the location revision; a changed location rejects the write. Profile
deletion drains pin writes before its namespace sweep and forgets the cache.
Pinned rows do not duplicate in Active/Recent, and archived rows remain archived.

## Why unarchive remains pending

The pinned v1 HTTP schema accepts numeric `time.archived` only. Inspection of
the matching upstream implementation on 2026-09-05 confirms:

- The HTTP handler calls `setArchived` only when the archive field is supplied.
- Session storage preserves the supplied numeric value, including zero.
- The ordinary session query excludes rows whose archive column is non-null.

Therefore, sending zero is not a complete unarchive, and sending null violates
this contract. Omitting the field performs no update. The internal method can
clear the value, but the pinned HTTP adapter does not expose that operation.
The v2 beta-18600 contract likewise has no archive-write operation.

Sources at commit `f12e14cf1640cbf0dfb6b1ff425b2daaef459eec`:
[HTTP handler](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/server/routes/instance/httpapi/handlers/session.ts),
[session storage and listing](https://github.com/anomalyco/opencode/blob/f12e14cf1640cbf0dfb6b1ff425b2daaef459eec/packages/opencode/src/session/session.ts),
and the checked-in v1/v2 OpenAPI contracts.

No fabricated unarchive action, duplicated session, or local override of server
archive state is shipped. Issue #29 stays open for an actual supported inverse
archive endpoint, followed by implementation and live verification.

## Validation

Pin-specific checks cover persistence, serialized competing edits, profile and
location isolation, refused storage with recovery, recency ordering, older-page
hydration, retriable failures, and the row menu's Pin/Unpin behavior. Existing
inventory-pagination and profile-deletion suites also pass. This batch does not
claim native device verification or final release readiness.

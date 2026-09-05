# Session note verification — beta-18600

Verified on 2026-09-05 against the isolated, authenticated Windows server used
for the staged-revert fixture. No provider request was started. The helper was
stopped after the checks; its disposable session and source artifacts remain.
See [captured results](session-note-beta-18600.json).

The native `@opencode-ai/cli-windows-x64-baseline@0.0.0-beta-18600` executable
was rechecked against SHA-256
`29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`.
Health reported healthy and the exact version before any test request.

| Check | Observed result |
|---|---|
| GET, PUT and DELETE without authorization | Each returned 401 |
| Save a string with 8,190 ASCII characters | 204: 8,192 encoded bytes including JSON quotes |
| Save 8,191 ASCII characters | 413 with `actualBytes:8193`, `maxBytes:8192` |
| Read after the rejected replacement | Previously saved note preserved |
| Remove `mobile.note` | 204 and absent from the next list |
| Inspect the independently seeded fixture entry | Its key and structured value remained unchanged |

## Protocol interpretation

The embedded `InstructionEntry.put` implementation measures
`Buffer.byteLength(JSON.stringify(value), "utf8")` before writing and uses the
8,192-byte constant. The app therefore counts UTF-8 JSON bytes, including
escapes, rather than Dart string length. HTTP fixtures also cover Unicode,
newlines, a server-reported smaller limit, bodyless 204 responses, scoped
location queries, and preservation of typed authorization errors.

The pinned transcript projector handles `session.instructions.updated` by
appending a system message only when the event contains text. Its ID is the
event ID with `evt_` replaced by `msg_`, and its description starts with
`Instructions updated:`. Changes are announced at the next agent step boundary.
The mobile adapter uses matching IDs for live notices and redacts instruction
keys and values in both live and hydrated transcript paths. The editor exposes
only a string stored at `mobile.note`; an unfamiliar value is left untouched.

## Scope and limits

Fresh reads before Save/Remove reject a known remote replacement. Scope and
event revisions reject stale editors after a server/location change, deletion,
or instruction update; duplicate decisions share a write slot. This API has no
conditional write or ETag, so another client's write between the final read
and mutation cannot be ruled out. Refresh shows the new saved note alongside
the retained draft before another explicit save.

The live fixture verifies authorization and durable note storage. It does not
run a model to test the next-step announcement, perform a physical-device
interaction, or establish final release readiness. Those announcements are
covered by pinned source inspection and mapper/event/widget checks here.

# Session skill activation — cycle 19

## Implemented workflow

On v2, chat menu → Use a skill and the existing Skills command open the catalog
for that conversation. Selecting a skill shows its Markdown instructions and
the destination title. Add to conversation sends the selected skill's protocol
ID. Run agent now is explicit and enabled by default; turning it off sends
`resume: false`. Successful activation returns to chat, refreshes its canonical
history and shows confirmation. The composer draft is retained.

The general Library catalog remains a preview. V1 retains its existing skills
and command behavior; its pinned contract has no session skill activation route.
V2 skill IDs and display names are separate, consistent with the
[upstream v2 skill documentation](https://opencode.ai/v2/docs/skills#ids-and-validation).

The gateway posts `{skill, resume}` to `/api/session/{sessionID}/skill`, with
authentication headers and no invented location query. Catalog reads retain
the selected location. The controller checks the reviewed location after wake
and session hydration, waits for in-flight model/agent selection, and refuses
deleted sessions, changed connections, concurrent activation and staged revert.
A fresh session read checks server revert state immediately before dispatch.
The protocol has no conditional revision parameter, so another client's change
between that read and POST remains a server-side race boundary.

Network/5xx failures are uncertain: the sheet preserves its preview, explains
that the operation may have applied, and disables repeat submission until the
user closes it and checks the conversation. Known missing-skill/session errors
do not disable the whole capability; unsupported routes do. No automatic retry
or offline activation queue is introduced. A scope change after accepted
activation reports that it applied to the original conversation.

Remote skill-activated events request canonical transcript refresh without
exposing the event's instruction text. Existing redacted skill-message rendering
remains in place. Catalog refreshes discard stale results when the location
changes; a chat-scoped catalog requires reopening from that conversation.

## Evidence and remaining validation

- Nine focused tests in `test/session_skill_test.dart` pass, including exact
  ID/body/location/auth encoding, typed errors, scope changes during wake/read,
  staged revert, concurrent writes, privacy-preserving events, activation with
  resume disabled, uncertain response handling and compact controls.
- Nine existing catalog refresh regressions pass: 18 checks in the batch.
- Static analysis reports no issues.
- Inspected the 411px render at `.dart_tool/skill-activation-preview.png`.
  The 320px, 1.7x text fixture can reach and use the activation button.

The compact check also runs with Flutter's test font. It exposed a shared
Markdown preview overflow: Rendered/Raw controls now wrap instead of overflowing
at enlarged text. The activation check passes with and without the capture fonts.

Live activation is **not yet verified**. The authenticated, isolated Windows
beta-18600 fixture returned HTTP 200 with an empty `/api/skill` catalog, despite
the correct project location and fixture files. A committed project, explicit
source config and documented flat Markdown form did not resolve discovery.
This result does not establish the underlying server cause or prove activation
failure. The attempt stopped before the activation POST; no provider run began.
All owned fixture server processes were stopped. The reproducible helper and
catalog snapshots remain in `.dart_tool/skill-verification/`.

The final candidate still needs discovery/activation on a working live skill
source, persisted-history verification, the resume-enabled agent flow and native
device interaction. This is client implementation evidence, not release sign-off.

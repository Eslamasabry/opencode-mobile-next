# Active context inspection

Cycle 20 adds **Session context → Active context** on v2. The inspector reads
`GET /api/session/{sessionID}/context`, searches all returned messages, filters
by message type, and opens full selectable content with per-part copying.
The whole page scrolls, including search and filters, so compact screens with
large text and the keyboard open can still reach results.

The endpoint returns `{data: Session.Message.Info[]}`. It is the active message
history following compaction, including the completed compaction summary. It is
not a token breakdown or the complete transformed provider request. Existing
reported usage and explicitly estimated metrics remain separate. V1 retains its
existing metrics/history surface; the pinned v1 contract has no equivalent route.

The inspector deliberately shows available system, synthetic and skill text
when opened. Ordinary transcript redaction is unchanged. It includes user text,
reasoning, tool input and all supported text results, attachment names/MIME types,
shell output, summaries and session-change notices. It does not render binary
attachment bodies, attachment URLs or internal metadata. Pruned and truncated
content is labeled; unknown message types remain visible as notices. Full text
is available in the detail view rather than silently limited to the row preview.

No arbitrary message-count cap or invented location query is applied. Invalid
envelopes/entries and duplicate IDs produce an explicit error. Typed missing
sessions, auth errors and server errors do not disable the capability. Unsupported
routes do. A failed refresh keeps a labeled previous snapshot; Retry can recover.
Changing location clears both context screens and rejects pending reads. Revert
history changes invalidate open details and refresh the list; staged boundaries
exclude hidden messages. Idle transitions and foreground data refreshes reload
the snapshot.

## Evidence

- Nineteen focused/regression checks passed and the 411px phone render was
  inspected. Capture fixtures use bundled Latin fonts; Arabic/emoji preservation
  is verified in content and live responses, not by that screenshot. Native
  font fallback and RTL rendering remain part of the final device pass.
- Static analysis and the focused `active_context_test.dart` and existing
  `session_context_screen_test.dart` checks cover request path/auth, 5,001 returned
  messages, invalid responses, missing/auth/unsupported routes, Arabic and emoji,
  complete tool text results, instruction visibility, excluded metadata/bodies,
  distant search previews, type filtering, copy, refresh recovery, location races,
  staged history and 320px width at 1.7 text scale with a 260px keyboard inset.
- Pinned Windows server `0.0.0-beta-18600`, executable SHA-256
  `29443AC011DC37896A650A7267C8FA232533B9410E8185EA099C1D5214E658E0`,
  exercised on 2026-09-05 at 09:40 UTC in an isolated authenticated fixture.
  An imported history contained an old user message, completed compaction,
  current user prompt with Arabic/emoji and system instructions. The context
  response retained the latter three and excluded the old prompt. Before/after
  unredacted exports matched exactly. Unauthorized reads returned 401; a missing
  session returned 404 `SessionNotFoundError`. No provider was run. The owned
  server was stopped afterward. Local reproduction helper and evidence are in
  `.dart_tool/context-verification/verify.ps1` and `evidence.json`.

Final candidate device/TalkBack checks remain. This isolated imported-history
exercise verifies endpoint filtering and read behavior, not live model execution
or the provider's final request transformation.

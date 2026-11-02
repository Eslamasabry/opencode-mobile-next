# Final review — server connection failures (GLM, 2026-11-02)

Independent review per final-review.txt. Scope: ConnectionFailures.kt, ServerFailureKind.kt,
ServersScreen.kt, PreviewRuntime.kt, new EN/AR strings_server_failures_preview.xml, and the three
focused tests. 12 source reads (budget 15). No builds/tests/edits run; source treated as frozen.

## Verdict: no concrete defects in the reviewed scope

Checks against the brief, each verified in source:

1. Subtype/field preservation — PASS. `withDetails` (engine/.../ConnectionFailures.kt:107-134) is
   exhaustive with no `else`; every non-details field is forwarded and matches the real
   constructors (core/common/.../Failure.kt:25-116: Rejected status/tag/extras, NotFound and
   Conflict tag/requestId, Busy kind/tag, Server status/tag all kept). Tests assert subtype plus
   fields for Network/Certificate/SignIn/Incompatible/Server/Rejected/NotFound
   (ConnectionFailureTest.kt:254-266, 167-204).
2. Generated details tokens — PASS. `stageDiagnostics` (ConnectionFailures.kt:38-101) emits only
   `stage.name`, literal kind names, closed-enum `.name` values, and `HTTP <int>`; tag, requestId,
   extras and feature are never read into diagnostics. Both `when` expressions are
   exhaustiveness-checked (a new subtype fails to compile).
3. No leakage — PASS. Original details are replaced, not appended; B-PROTO-095/B-SEC-014 tests
   feed a credentialed URL, private host and HTML prose and assert none of it reaches the redactor
   input or the shown details (ConnectionFailureTest.kt:36-39, 287-303).
4. No false network copy — PASS. StorageRefused→Storage, SecretUnavailable→Secret, TimedOut→
   Timeout, Incompatible/Certificate/SignIn/UnreadableReply each their own kind
   (ServerFailureKind.kt:35-54); Other copy is neutral ("could not complete this connection"),
   null-failure keeps the unchanged per-issue fallback (ServersScreen.kt:249-259); storage copy
   never claims disk space (values/strings_server_failures_preview.xml:11); UI tests assert the
   old network sentence is absent for Unknown and UnreadableReply (ServerFailureNoticeTest.kt:
   56-101).
5. Kit Details/Copy wiring — PASS. ServersScreen.kt:135-140 passes the failure plus existing
   localized kitDetails/kitCopyDetails to the pre-existing KitErrorNotice (kit state/KitNotice.kt:
   169; fold starts collapsed, KitDetailsFold.kt:75); tests render the real screen, toggle the
   fold, and verify Arabic labels and HTTP status staying out of the body
   (ServerFailureNoticeTest.kt:71-93). SignIn/CleartextConsent/InvalidAddress reuse existing
   strings present in both locales (strings_servers_preview.xml:13,22,27 EN and AR).
6. Fences on success paths — PASS. `atStage` passes Outcome.Ok through unchanged (PreviewRuntime.kt:
   90-95); no retry logic added; consent gates intact (save 125-126, connectNow 152-155); draft
   fence retained (draftRecovery set on remove 338-339, reopened only on explicit connect 161-175);
   all seven stages annotated at their boundaries (99; 151/155/160; 165/170/172; 177; 180;
   229/234/238/244; 255). Coordinator corrections confirmed: real-screen tests, neutral unknown
   copy, StaleScope annotated at LoadConversations (227-244).

Non-defect observations (no action required):
- save()/remove() failures carry no stage annotation; the contract's stages cover only the connect
  flow, so this is in scope, and their UI fallbacks are unchanged.
- A non-null typed failure overrides issue-specific copy even for SaveFailed/RemoveFailed issues
  (ServersScreen.kt:236-248); the kind sentence stays accurate for those causes.
- ConnectionFailureTest.kt:28-29 KDoc still narrates the pre-landing refusal stub ("every test
  here fails"); harmless provenance note, now historical.

Limits of this review:
- No Gradle/tests/emulator run (worker rules), so compile/test pass and the phone-only failure
 itself remain unconfirmed; my checks are source-level only.
- The worker snapshot has a single squashed baseline commit, so PreviewRuntime "changes" were
  reviewed as final state, not as a diff against pre-change source.
- No automatic Linux handoff exists or was reviewed; per contract it stays out of scope.

Reviewed by OpenCode zai-coding-plan/glm-5.3, isolated worker snapshot, 2026-11-02.

## Coordinator qualification

This source review preceded the test-only harness correction for LocalKitSecretSink and the desktop resource locale. Production code is unchanged from the reviewed snapshot. The coordinator observed and corrected those harness failures rather than treating the review or the earlier UI reds as test proof; final execution receipts are recorded separately.

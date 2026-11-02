> Historical GLM worker handoff. Coordinator corrections and final execution results are in [verification.md](verification.md); this report alone is not a pass receipt.

# Worker report — connection builder (server-errors-2026-11-02)

Snapshot: `workers/connection-builder` (isolated). Contract: `docs/native/server-errors-2026-11-02/contract.md`.
State: **implemented, staged; NOT compiled, NOT tested** (workers run no Gradle/tests per runbook).

## Owned files (all and only these touched)

- `native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionFailures.kt`
  — replaced the failure-tests worker's refusal stub with the frozen annotator.
- `native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PreviewRuntime.kt`
  — stage annotation at the frozen boundaries.
- `docs/native/server-errors-2026-11-02/worker-connection-builder.md` (this report).

## engine/connection helper

- `enum class ConnectionStage` exactly as frozen: StorageReady, ProfileRead, DraftRecovery,
  OpenGateway, SaveActiveProfile, LoadConversations, LoadActivity (unchanged from the stub).
- `connectionFailure(failure, stage, redact)` returns the SAME concrete subtype with every field
  preserved except `details` (exhaustive `when`; a new Failure subtype breaks this compile, as
  the contract intends). `details` is exactly `redact(generated)` — one call, result used as-is.
- Generated text = `stage.name · Kind · <enum reason> · HTTP <n>` where the enum reason is the
  closed-set name (NetworkWhy/CertificateWhy/SignInWhy/BusyKind/IncompatibleKind/RefusalReason/
  MediaProblem/RuntimeProblem/FolderProblem) and HTTP status only for Server/Rejected when non-null.
  Never included: original details, tags, requestId, extras, FeatureId, URLs, IDs, exception text,
  server prose. Verified against `ConnectionFailureTest`'s forbidden-token list by construction
  (no stage or kind token contains `://`, an IP, `/api/session` or `ECONNRESET`).
- No core/type/dependency changes; no new Failure subtype; no transport or retry changes.

## PreviewRuntime wiring

- Injected `private val redactor: Redactor` (existing `core.security.Redactor`; binding already
  provided by `AndroidAppGraph.Factory.create(@Provides redactor)` — no graph edit needed).
- Private `Outcome<T>.atStage(stage)`: Ok passes through unchanged; Failed is rebuilt through
  `connectionFailure(failure, stage, redactor::details)`.
- Annotated boundaries (failed paths only):
  - StorageReady — `ready.await()` failure in `command`.
  - ProfileRead — missing saved profile (ProfileDeleted), trust (CleartextNotConfirmed),
    unreadable saved profile (InvalidAddress) in `connectNow`.
  - DraftRecovery — ProfileClosing fence refusal, missing drafts owner (StorageRefused), and
    `reopenProfile` failure inside the `draftRecovery` block.
  - OpenGateway — `registry.open(profile)` failure.
  - SaveActiveProfile — `profiles.setActive(id)` failure.
  - LoadConversations — `sessionPage()` failure in `refreshOne` (listing `failed` flag unchanged).
  - LoadActivity — `sessionStatuses()` failure via `statuses.map { Unit }.atStage(...)`
    (Ok path and `failed` flag unchanged).
- Untouched on purpose: `save()`, `remove()`, StaleScope refusals, success/clearing/fence/retry/
  transport policy, auth/persistence semantics, other workers' files, all test files.

## Verification NOT performed (coordinator's gate)

Not run (worker rules): Gradle, `:engine:connection:desktopTest`, `:androidApp` compile,
format script, analyzer. Expected green after coordinator's red→green capture:
`ConnectionFailureTest` (stub semantics removed: details now generated, subtypes preserved).
Compile notes: Redactor reaches PreviewRuntime via Metro constructor injection from the existing
`@Provides` graph input; `atStage` relies only on existing `Outcome` smart casts.

## Hand-off

Owned production files staged (`git add`, no commit). The refusal-stub comment in
ConnectionFailures.kt was removed with the stub; tests and all other modules are untouched.

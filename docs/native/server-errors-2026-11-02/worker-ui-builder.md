> Historical GLM worker handoff. Coordinator corrections and final execution results are in [verification.md](verification.md); this report alone is not a pass receipt.

# Worker report — UI builder (server-errors 2026-11-02)

Scope honored: only feature/servers production Kotlin under `src/commonMain/`, the two new unique
words files, and this report. No test, kit, engine, androidApp or shared-file edits. No Gradle,
builds, tests, installs, network, credentials, commits or pushes (worker rules, runbook).

## Implemented (frozen contract, docs/native/server-errors-2026-11-02/contract.md)

1. `presentation/ServerFailureKind.kt` — replaced the failure-tests refusal stub with the real
   classifier. Enum list unchanged (frozen order/names). Mapping: Network/TimedOut/Certificate/
   SignIn/Incompatible/UnreadableReply/Server/Rejected/StorageRefused/SecretUnavailable → their
   kinds; `Refused(CleartextNotConfirmed)` → CleartextConsent, `Refused(InvalidAddress)` →
   InvalidAddress; null/Unknown/Unexpected/other refusals/Unavailable → Other (neutral fallback,
   no invented network cause).
2. `ui/ServersScreen.kt` — `issueWords` is now kind-driven. SignIn/CleartextConsent/
   InvalidAddress keep the existing copy (`serversSignInFailed`, `serversCleartextRequired`,
   `serversInvalidAddress`). Other kinds use the new words. `Other` keeps the existing
   per-issue generic text (InputRequired/SaveFailed/ConnectFailed/CreateFailed/RemoveFailed/
   Connected) — the null/unknown fallback, unchanged. The error notice now passes
   `failure = state.failure` plus localized `kitDetails`/`kitCopyDetails` labels to
   `KitErrorNotice`, so safe redacted details (`failure.details`) fold away collapsed by default
   and Copy works; kit untouched. No body text ever includes failure prose, `toString` or HTTP
   status (status stays inside Details only).
3. New words `values/strings_server_failures_preview.xml` + `values-ar/…` — 9 keys
   (`serverFailureCertificate/Incompatible/Network/Rejected/Secret/Server/Storage/Timeout/
   UnreadableReply`), English + Arabic, one plain sentence each. Frozen test strings honored
   exactly: UnreadableReply EN body and Server EN body. Details/Copy labels reuse existing
   `kitDetails`/`kitCopyDetails` (Arabic التفاصيل / نسخ التفاصيل) — verified present in
   `strings_common.xml` en/ar.

## Behavior preserved

- Cleartext consent switch flow, origin-bound `allowCleartext`, unsaved secret field
  (`TextFieldState`, never saved instance state) and save/connect action semantics: untouched.
- No autoconnect/retry added; `ServersActions`/`ServersUi`/`ServersViewModel`/`ServersEntries`
  unchanged. Precedence vs old code kept: SignIn and CleartextNotConfirmed still win over issue
  text; new: a `Refused(InvalidAddress)` failure now shows the address copy (same string
  InputRequired already shows) instead of the generic connect line.
- `Connected` still renders `KitNotice` (no fold, no failure path).

## Verification status (distinct states)

- Implemented: yes (all owned slices above).
- Verified: static only — XML parse + en/ar key-set parity + alphabetical order (matches kit/
  common convention); exhaustive `when`s over the closed enum/sealed Failure; imports pruned
  (`RefusalReason` no longer imported in ServersScreen); no remaining stub references; grep shows
  no `serverFailure*` key collisions; frozen EN strings byte-matched against
  `ServerFailureNoticeTest`.
- NOT run (forbidden for workers): Gradle, `desktopTest`, analyzer/lint, `tool/format-changed.sh`.
  Handwritten formatting follows neighboring files (4-space, trailing commas, sorted imports);
  coordinator should run format + `:feature:servers:desktopTest` and the words checks.
- Committed: no (staged only). Deployed/released: no.

## Notes for the coordinator

- `ServerFailureNoticeTest` renders the production screen; its `kit-details-*` tags come from the
  existing kit fold, reached only because labels + `failure.details` are now passed — no kit edit
  was needed or made.
- New keys exist only in en/ar (like the sibling `strings_servers_preview.xml`); other locales
  fall back to default English. If a words parity gate requires more locales, that is a shared
  `:ui:words` owner decision.
- Evidence trail: contract §"Freeze APIs" item 2 (enum + mapper + copy + KitErrorNotice) fully
  covered; items 1 and 3 (engine helper, androidApp wiring) belong to the connection builder.

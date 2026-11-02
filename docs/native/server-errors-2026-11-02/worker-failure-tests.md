> Historical GLM worker handoff. Coordinator corrections and final execution results are in [verification.md](verification.md); this report alone is not a pass receipt.

# Worker report: failure tests (GLM, 2026-11-02)

Independent test writer for [contract.md](contract.md). Red-first tests plus refusal stubs only; no
production behavior, no existing test edited, no Gradle/build/test run (worker rule). The coordinator
runs the suite red before the builders, then green after.

## Owned paths (all new files, nothing else touched)

| Path | Kind |
|---|---|
| `native/engine/connection/src/commonMain/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionFailures.kt` | refusal stub (`ConnectionStage`, `connectionFailure` returns the original failure) |
| `native/engine/connection/src/commonTest/kotlin/io/github/eslamasabry/oc/engine/connection/ConnectionFailureTest.kt` | red tests (4) |
| `native/feature/servers/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/servers/presentation/ServerFailureKind.kt` | refusal stub (`ServerFailureKind`, `serverFailureKind` returns `Other`) |
| `native/feature/servers/src/commonTest/kotlin/io/github/eslamasabry/oc/feature/servers/presentation/ServerFailureKindTest.kt` | red tests (3) |
| `native/feature/servers/src/desktopTest/kotlin/io/github/eslamasabry/oc/feature/servers/presentation/ServerFailureNoticeTest.kt` | red desktop UI tests (3) |
| `docs/native/server-errors-2026-11-02/worker-failure-tests.md` | this report |

Stub signatures match the frozen contract exactly (enum order and entries included). Stubs are marked
in KDoc as refusal stubs so no one mistakes them for the annotator/mapper.

## What the tests pin

### engine/connection `connectionFailure` (B-PROTO-095, B-SEC-014, B-SEC-130, B-SEC-133)

- **Subtype and fields preserved**: a table over every covered kind — all six `NetworkWhy`, `TimedOut`,
  all three `CertificateWhy`, `SignIn` whys, all three `IncompatibleKind`, `UnreadableReply`,
  `StorageRefused`, `SecretUnavailable`, `Server` (502 and null status, `tag` kept), `Rejected`
  (403 with `tag` + `extras` kept), `NotFound` (`tag` + `requestId` kept) — asserts the returned value
  keeps the exact subtype and every field, changing only `details`.
- **Details are generated, never copied**: with an identity `(String) -> Redacted` that records its
  inputs, the shown `details` must be one of the strings handed to `redact` (routing through the
  caller's Redactor), must name the stage and the kind/reason/status tokens (exact enum spellings,
  e.g. `OpenGateway`, `Network`, `Dns`, `502`), and neither the shown details nor anything handed to
  `redact` may contain the original raw details, a credentialed URL, the private host or transport
  prose (`hunter2`, `://`, `10.0.0.2`, `ECONNRESET`, `/api/session`).
- **Server prose stays out**: a 502 whose details are an HTML prose body with an internal upstream URL
  keeps status 502 and gets only stage + kind + numeric status diagnostics; the prose never reaches
  the redactor either.
- **Every stage is named, alone**: each of the seven stages appears in the generated details when it
  is the failing stage, and no other stage name leaks in.
- **Null details become generated details**: failures that arrived with `details = null` still get
  non-null generated stage details (timeout, refused, storage, secret, unreadable reply, unsupported
  version).

Red today because the stub returns the original failure unchanged (no redactor call, raw details
intact, null details stay null).

### feature/servers `serverFailureKind` (B-PROTO-095, B-SEC-014, B-SEC-133)

- **Category table**: Network(why) → `Network`; `TimedOut` → `Timeout`; `Certificate(why)` →
  `Certificate`; `SignIn(why)` → `SignIn`; `Incompatible(kind)` → `Incompatible`; `UnreadableReply`
  → `UnreadableReply`; `Server(status?)` → `Server`; `Rejected(status?)` → `Rejected`;
  `StorageRefused` → `Storage`; `SecretUnavailable` → `Secret`. Every non-Network row additionally
  asserts it is not `Network` (B-PROTO-095: credential or TLS failures never claim a missing server).
- **Closed enum**: the 13 frozen entries, in order, and nothing else.
- **Fallback**: `null`, `Failure.Unknown`, `Failure.Unexpected`, app refusals
  (`ProfileClosing`, `MigrationPending`) and `Unavailable` stay `Other` (the generic issue line).
- **Cleartext vs invalid address**: `Refused(CleartextNotConfirmed)` → `CleartextConsent` and
  `Refused(InvalidAddress)` → `InvalidAddress`, distinct from each other and from `Network`.

Red today because the stub answers `Other` to everything (so every non-Other row fails; the
fallback test is the one that will already pass).

### feature/servers desktop notice (B-PROTO-095, B-SEC-014, B-SEC-130, B-SEC-133)

Composes the contract's mandated display path — kit-only `KitErrorNotice` with the supplied
`Failure`, localized Details/Copy labels — under `KitTheme` (dark, motion off), En and ar locale.
Test-local words stand in for the UI builder's future copy; the pins are the category choice and the
kit presentation, not the wording:

- **UnreadableReply is not connectivity**: kind maps to `UnreadableReply` (not `Network`), the
  notice offers retry + Report a problem, never Switch server, and the diagnostics are not body text
  while collapsed.
- **Collapsed Details, sanitized only**: for a `Network(Dns)` failure with generated details
  (`OpenGateway · Network · Dns`), Arabic labels, Switch server present (a transport failure offers
  the network fix), the fold starts collapsed (no body, no diagnostics text), opens on the toggle to
  exactly the sanitized string, and Copy all appears.
- **502 in words**: `Server(502)` shows its own words with no "502" anywhere on screen while
  collapsed; the number appears only inside Details after expanding; no Switch server.

Red today via the kind assertions (stub → `Other` → wrong message/assert fails).

## Assumptions the coordinator may need to arbitrate

1. **Token vocabulary**: generated details are pinned to exact enum spellings (`OpenGateway`,
   `Network`, `Dns`, `502`, …). The contract says "generated stage + failure kind/enum
   reasons/status"; enum names are the natural frozen vocabulary. If the builder formats differently
   (`stage=OpenGateway`, `HTTP 502`), the containment pins still hold for the tokens themselves but
   the exact-strings differ — coordinator call.
2. **`Failure.Unknown` → `Other`**: read from "Existing generic issue text remains the null/unknown
   fallback". If the builder wants `Unknown` → `Network`, that test line changes (the kit's
   `KitErrorKind` already treats `Unknown` as network for the *fix*, which is independent of the
   message category).
3. **Not pinned (deliberately)**: `ComputerDidNotAnswer`, other `Refused` reasons besides the two
   consent/address ones, and any UI copy wording. Left to the builders.
4. **Details routing**: tests assert the shown details equal one of the strings passed to `redact`
   (not "exactly one call"), so a builder may call the redactor freely.

## Limitations

- No Gradle, build, test or formatting run (worker rule): compile/red/green verification belongs to
  the coordinator (`:engine:connection:desktopTest`, `:feature:servers:desktopTest`).
- `native/feature/servers/build.gradle.kts` was NOT touched (not in my write set). The
  `oc.kmp.feature` convention already puts `compose-ui-test` on commonTest, and `:ui:kit` runs the
  same `runComposeUiTest` desktop tests with no extra dependency, so the semantics-only notice test
  should run as-is. If the coordinator finds this module needs `libs.compose.desktop.currentos`
  (skiko) like `:feature:settings`, that one-line desktopTest dependency is an integration edit for
  them.
- The desktop test does not exercise the real servers screen (its production failure presentation is
  the UI builder's slice); it pins the contract's KitErrorNotice integration shape that slice must
  use.
- English/Arabic strings in the desktop test are test stand-ins, not the shipped copy.

## Coordinator review correction

Before any red/green claim, the coordinator replaced the test-local notice/message wrapper with three tests rendering the real ServersScreen. Invented retry/report assertions were removed as outside this contract; actual body classification, Arabic collapsed Details/Copy and HTTP-status-only-in-Details are now exercised through production UI. Original GLM output is preserved in the artifact patch for provenance.

Final harness correction: the real screen receives its required LocalKitSecretSink, actual desktop resource locale is set/restored per test, and the UI test lives outside the presentation package. The first UI failures caused by missing setup were rejected as regression proof. Four valid assertions then failed on the old production screen before the final 14-test feature suite passed.

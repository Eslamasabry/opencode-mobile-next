# Remaining usage implementation — 2026-09-06

Working-tree implementation based on `db79069`; no feature commit, deployment,
provider-account query, native build or release was performed by this work.

## Checkpoint A: Codex collector and screen

- Added the optional Node collector under `tool/quota/`, the typed snapshot in
  `lib/domain/provider_quota.dart`, dedicated HTTP client, memory-only overview,
  and Settings → Remaining usage. See the collector README for the explicit
  same-origin authenticated proxy setup; this is not an OpenCode API endpoint.
- Account/window percentages, missing data, reset times and freshness stay
  distinct from existing server consumption. Consent is per screen visit;
  no provider tokens or quota snapshots are persisted in the app.
- Profile deletion and parent disposal invalidate reads synchronously through
  a separate liveness signal. Late requests cannot restore removed consent.
- Fixed unsupported-platform background opt-ins and added isolated iOS gating
  tests. An iOS runner, native plugin/Keychain build and device run remain absent.
- Dependency resolution regenerated Linux/Windows file-selector registrations
  for already-locked image-picker dependencies. No package version changed.

### Verification actually run at checkpoint A

Toolchain: `/tmp/opencode/flutter`, upstream Flutter **3.47.2**, Dart **3.13.2**;
CI-version matched, not a verified Shorebird fork. `pub get --enforce-lockfile`
completed without changing the lockfile. Collector tests used Node **24.19.0**.

| Check | Result |
|---|---|
| Node collector synthetic suite | 28 passed |
| Quota parser/client/controller focused checks | 68 passed |
| Quota UI focused checks | 21 passed, optional capture skipped |
| Synthetic widget capture | Passed and visually inspected; [preview](../qa/provider-quota/README.md) |
| `flutter analyze --no-pub` | No issues |
| `flutter test --no-pub --concurrency=1 --reporter expanded` | **1,814 passed, 2 skipped**, one serial invocation, 27m19s |

The full suite includes the existing Termux pin/setup and lifecycle/deletion
regressions. No real credential stores, provider APIs or session-server processes
were used by the new tests. Mocked iOS and widget evidence is not device evidence.

### Findings corrected before the passing gate

Synthetic nested maps needed nullable dynamic values so negative parser tests
reached the parser. Empty URI userinfo was lost by normalization, so the client
now rejects it before parsing. Cancellation observation is awaited explicitly.
Flutter's progress-bar semantics require a numeric value; localized prose now
lives in labels/text and the progress value is numeric. Route-dismissal checks
wait for removal instead of selecting ambiguous screen titles.

## Checkpoint B: Claude and provider-grouped consumption

- Added the separate Claude route/collector, explicit credential-source binding,
  legacy and structured core-window parsing, and Codex/Claude selection in the
  app. Provider changes cancel old reads and require fresh consent. Claude
  usage is not falsely presented as an independently identified account.
- Added provider consumption subtotals using returned provider/model IDs,
  preserving model detail and treating variants as records rather than new models.
- Added regression coverage for collector/source changes, nullable windows,
  wrong-provider responses and replacing a screen's controller.

One broad verification phase ran after these edits: **38 Node checks passed**,
analyzer reported no issues, and the serial Flutter suite reported **1,824
passed, 1 failed, 1 skipped** in 29m42s. The failure was an existing UI assertion
expecting exactly one `$3.42` text: the new provider subtotal correctly adds a
second instance. The assertion now targets the existing `usage-total-cost` key
before and after refresh. The corrected test was subsequently rerun by exact
name before the requested commit: **1 passed** in 3 seconds. No production code
changed after the broad run; this is a full run plus a focused correction,
not a claim of a second clean monolithic full-suite invocation.

At the user's direction, broad tests are now deferred while development
continues. Preserve the full-run/focused-rerun distinction. No additional
provider account or native deployment was exercised.

## Remaining external gates

Operator installation, authenticated proxy/token replacement, actual TLS/stream
behavior, vendor response/schema drift, and explicit live-account verification
are untested. Native Android/desktop builds and real iOS execution are separate
gates. The bundled privacy-policy change means the combined feature is a new
public sideload baseline candidate, not a proven Shorebird patch.

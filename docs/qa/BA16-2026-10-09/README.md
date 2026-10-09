# BA16 — partial agent payload removal

Finish line: after a failed version check, catalog-owned installation files keep Remove available through a fresh app/controller start and unrelated setup jobs, using the existing confirmation flow.
Non-goal: changing installation success, chat readiness, account state, UI copy, removal safety, or APK/device qualification.

Branch `sol/ba-partial-payload`, based on `feat/genui-fe` at `83913aa3933df186a56a19f22dae33f48e42cc69`.

## Implementation

The native `phoneAgentVersion` response now includes nullable `payloadPresent`. A read-only, bounded inventory examines only the six removable catalog agents' authored installation directories (including staged payloads), lock directories, and final/temporary launcher links. It accepts exact authored launcher targets, including dangling links. It does not follow guest ancestor symlinks, inspect account files, launch an inventory shell, or include CLI output. Unsafe/unreadable paths return unknown. Older/malformed replies are not treated as presence.

The runtime retains this fact separately from `installed`; a failed version plus positive inventory becomes `AgentRow.hasPartialPayload`. `canRemoveAgent` uses the fact independently of setup progress. Fresh inspection reconstructs it after restart; runtime copies preserve it. Existing guarded removal still decides whether deletion is safe and measures freed bytes. No presence fact grants chat capability, sign-in, or phone qualification.

FA6 already renders its Remove action on the install step when the source allows removal. The widget regression verifies Install and Remove together and opens the existing confirmation after tapping Remove. No `lib/ui/` changes or new UI contract were needed. The original [three-line contract](../../design/BA10-partial-payload-contract.md) is fulfilled by this backend change.

## Verification

All Flutter commands use the pinned Flutter 3.47.1 binary and `OC_TEST_SLOTS=2 tool/qa/machine_lock.sh test --`, one command at a time, with `test --no-pub --concurrency 1`.

Failing-first evidence:
- Native failed-version inventory test: expected `payloadPresent: false`, got no fact (`{installed=false, version=null}`).
- Host mapping and controller removal tests: expected true, got false.
- Widget: expected the Remove action, found none.
- Native harness initially exposed a pre-existing BD13 stub mismatch; added the missing empty `privateAgentDiagnostics` stub before obtaining the behavior failure.
- [Production-revert log](revert.txt): production behavior reverted with new interfaces/tests retained, then restored byte-for-byte. The regression must fail at native, host, controller, domain, and widget layers.

Final restored-source run: **206 tests passed** across the six files below; see [green output](green.txt). The file-size ratchet passes with no baseline changes. Native tests compile Kotlin/JVM through the existing Flutter wrapper; no Gradle is used. No full-suite or new APK/device qualification is claimed. Normal APK 2202 was not changed.


Final focused command (after the required lock/pinned binary prefix):

```text
test --no-pub --concurrency 1 test/phone_agent_host_native_test.dart test/phone_agents_host_test.dart test/phone_agents_controller_test.dart test/phone_agents_test.dart test/agents_remove_ui_test.dart test/file_size_ratchet_test.dart
```

The native case covers all six catalog IDs, fresh host construction, unrelated setup state, staging directories, dangling authored and temporary launcher links, leftover locks, absent payloads, foreign links, Claude exclusion, and unsafe ancestor symlinks. The latter remains unknown and does not touch the outside fixture. Five new regression tests failed when production behavior was reverted; all passed after restoration.

No local-state migration is required: the inventory is read anew from the existing host filesystem rather than a saved job or cached preference. The native fact is backward compatible; omitted, null, or malformed presence remains false in Dart. The existing unrelated widget test's hit-test warning appears in the log; it did not fail. No UI source, credential handling, or removal implementation was changed.

Full `analyze --no-pub` completed with one existing info (`unnecessary_import`) at `test/goldens/connector_card_golden_test.dart:17`; see [full analyzer output](analyzer.txt). The test and its `app_theme.dart` / `theme_roles.dart` imports are unchanged from the branch base. No ignore or unrelated source edit was added. A separate analysis of the affected production library and test entrypoints is recorded in [focused analyzer output](analyzer-focused.txt).

Affected-file analyzer: **no issues found**. Dart formatting, source-manifest verification, and `git diff --check` passed. This is a focused source/test qualification, ready for the coordinator to merge and build; no device result is attributed to BA16.

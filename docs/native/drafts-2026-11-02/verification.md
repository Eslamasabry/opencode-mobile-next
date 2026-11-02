# Native text drafts verification — 2026-11-02

The Android native preview now restores acknowledged text drafts after process restart, clears only the accepted submission revision, and fences profile deletion against late writes. Exact whitespace survives storage and restoration. This is one usable text-draft slice; native/Flutter parity and release qualification remain incomplete.

Candidate: branch `native/chat-drafts-2026-11-02`, native source ID `6340f02501c4375d194ffdcfbc54168319621a3570acb403cb60de213a09a8fd`. The APK was assembled before a test-only disabled-field selector correction; production inputs were unchanged. The source ID includes that final test correction. The tested implementation diff is committed with this record.

## Checks

All Gradle checks used JDK 21, the machine-wide lock, `--no-daemon --console=plain --no-configuration-cache --max-workers=4 -Pkotlin.incremental=false` on the PC (three workers on the VPS), with a test init script forcing fresh execution and disabling test-result caching.

| Check | Result |
|---|---|
| PC `:engine:composer:desktopTest` | 143 tests, zero failures/errors/skips |
| PC `:feature:chat:desktopTest` | 68 tests, zero failures/errors/skips |
| PC `:ui:kit:desktopTest --tests '*Kita2SecretReviewTest' --tests '*PartBRulesTest'` | 32 focused tests, zero failures/errors/skips |
| PC `:androidApp:assemblePreviewDebug` | Successful; installed over previous preview without clearing app data |
| VPS `:quality:test`, selecting KonsistRulesTest, GraphSmokeTest, ModuleOwnersTest, ChatWorldReviewTest, ScopeFileTest and KitOnlyPlantedTest | 44 tests, zero failures/errors/skips; final native source ID matches PC |

The initial engine integration was observed red (13 of 14 tests failed against a compiling scaffold); the initial four editor regressions failed against the old UI. Three additional GLM store regressions were observed red (three tests, three failures), then passed in the 143-test composer suite. The first integrated chat run had 67 passes and one selector failure: disabled fields remove their SetText action. The test now finds the visible Message control, asserts it is disabled and exposes no SetText action; the final complete 68-test run passed. Production behavior was not weakened.

Build output retains existing warnings, including deprecated maxLength and an unused expression in PreviewRuntime; this is not a warning-free claim. The repository-wide native suite, all renderer/golden gates, iOS and physical-phone qualification were not rerun for this candidate. Earlier full-suite evidence for the base preview does not qualify these new changes.

## Real Android runtime

API 35 emulator, own preview package `io.github.eslamasabry.opencode_mobile.native`, version code 10001. Owner phone and Flutter package were untouched. The isolated authenticated OpenCode 2 beta server used the real `zai-coding-plan/glm-5.3` provider.

1. Added and connected a saved server, opened a conversation, typed a fixed probe with two leading/trailing spaces. Readback found the exact persisted text. Force-stop/restart and reopening the same conversation restored it exactly: [storage proof](storage-after-restart.json), [screenshot](draft-restored-exact.png).
2. Sent the restored probe once. A real GLM reply arrived; Accepted/canonical acknowledgement cleared both editor and stored draft. After force-stop/restart the field remained empty. Server history retained one user probe and its assistant reply, including after profile removal: [history](history-after-removal.json), [cleared storage](storage-sent-after-restart.json), [screenshot](draft-accepted-clear.png).
3. With only the emulator app stopped, backed up its preferences and inserted a wrong-typed draft index. Reopening showed “Draft not saved. Copy your text or retry.” and disabled editing/sending; the boolean remained unchanged. Restored the exact backup before proceeding: [preservation proof](corruption-probe.json), [screenshot](draft-corrupt-store-refused.png).
4. Saved another unsent draft, verified its presence before confirming Remove server, then removed the profile through the UI. Draft count became zero; profile and secret-entry counts became zero: [before](storage-before-confirm-removal.json), [after](storage-after-removal.json), [metadata/secrets](profile-removal-storage.json), [screenshot](draft-profile-removed.png). Cross-profile isolation, refused deletion/recovery, same-text edits during acknowledgement and queued-writer fencing have focused engine/world tests; those additional fault/race cases were not injected into the real Android runtime.

The emulator and isolated server were stopped. Copied provider authentication, temporary server login, raw preference backup and private UI/history scratch were removed. An automation focus error exposed the temporary test login in a UI dump; that login was rotated immediately before the successful flow. No provider key was printed or committed.

## Worker and boundaries

[GLM review triage](GLM-review-triage.md) and [execution receipt](glm-worker-proof.json) identify the independent VPS worker: OpenCode 1.19.0 with `zai-coding-plan/glm-5.3`, exit 0. It reviewed an early integration snapshot, found actionable storage bugs, and did not independently qualify the final candidate. Other implementation workers used Codex.

Only text drafts are enabled by this slice. Existing attachments are preserved/refused by the text-only facade; attachment UX, stash, Flutter adoption/migration and complete composer parity remain outside it. Failed profile removal can already have deleted target drafts before a later stage fails; explicit Connect recovers new writing, not deleted data. Legacy empty-owner direct APIs remain a documented boundary in the triage.

The composer uses kit controls and localized English/Arabic failure/repeat copy. Disabled restoration fields expose disabled semantics and no edit action; ordinary user typing keeps selection, and target-bound explicit repeat confirmation cannot bypass unknown delivery or failed storage. No stored format migration was introduced; the existing draft index/vault format is reused. Draft text and submission diagnostics are redacted.

## APK

`opencode-native-drafts-preview-20261102.apk`, 22,384,374 bytes, SHA-256 `4fc04f3f144b6dc3a5f0874a2edf0457104edee187be9ffe6e6e348839cf5f09`. Certificate SHA-256 `1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C`, matching the previously installed emulator preview. This does not establish compatibility with an unspecified owner-phone signer.

The Tailscale-only download directory contains APKs only; the new file was verified HTTP 200 with the correct Content-Length. No public release, tag, push, PR or integration-branch merge was performed. Machine-local logs/JUnit receipts remain under `/home/eslam/Storage2/wt/native-drafts-artifacts-20261102`.

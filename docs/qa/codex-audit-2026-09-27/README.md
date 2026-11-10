# Branch security, storage and lifecycle audit — 2026-09-27

Read-only review of `codex/audit` at `6ea5e83c5da3465bc7975c76c17ed9a3eaacef74`, against `master` / merge base `c62f159ae3c1741cb4ec0ef92b4941c0ddfc0a18`, using `git diff master...HEAD`. The initial worktree was clean. Only this report was changed by the initial audit. The follow-up remediation is recorded in **Fixed** below.

**Finish line:** ranked, actionable findings with source locations, concrete failure scenarios, verification limits and a UI integration contract. **Non-goals:** implementing fixes, redesigning screens, changing stored formats, publishing, signing or pushing.

The complete diff contains 7,746 files. This is a prioritized audit of the requested state/domain/API, built-in/Termux/background, Kotlin, feedback and product-error surfaces, with necessary callers and tests traced. It is not an exhaustive review of every changed file. Source locations below refer to the audited revision. P1 means high priority; P2 means medium; P3 means low. “Reproduced” means a synthetic local test, never a live credential or production device experiment.

## Ranked findings

### F1 — P1: OpenCode 2 rejection text can expose credentials in the visible error body

**Location:** [product_states.dart:271](../../../lib/ui/widgets/product_states.dart#L271), especially lines 274–288 and 354–355.

The new `_rejectedReason` accepts a server's 400/422 message when it is short and does not match a technical-text regex, then interpolates it into `productErrorRejectedBecause`. Neither that path nor the body renderer redacts it. A server rejecting provider configuration with a short `Invalid apiKey=<value>` response puts the key directly in the ordinary error body, outside Details. Short prose is not a trust boundary.

This is newly exposed for `Api2Error`; master's older v1 `ApiException` pass-through is not counted as a new regression. The mapper's own comment at line 312 promises messages never pass through, but the rejection branch contradicts it.

**Suggested fix:** normalize protocol failures into a domain-owned error category; render generic localized rejection copy. Put the redacted technical reason in Details, or map an explicit allowlist of structured reason codes. Do not infer safe display text from length or prose shape.

**Test evidence:** `test/product_error_text_test.dart` tests a benign v1 “Bad model” reason. `test/no_raw_error_text_test.dart` excludes this mapper, so its passing scan cannot establish safety here. The audit probe calls `productErrorText(Api2RequestError(..., statusCode: 400))` with the transport's message shape and a synthetic key and checks whether the body retains it; result recorded below.

### F2 — P2: An aborted profile removal already erased activity history and Undo

**Location:** [connection.dart:6107](../../../lib/state/connection.dart#L6107); [automatic_activity.dart:71](../../../lib/state/automatic_activity.dart#L71) and [automatic_activity.dart:359](../../../lib/state/automatic_activity.dart#L359).

`deleteProfileAndLocalData` calls `AutomaticActivityController.closeProfile` before validating the confirmed queue at lines 6133–6155. This is destructive: it clears history and inverse actions and removes `oc.automaticActivity.<profileId>`.

If a queued item changes while the confirmation is open, or preserving queued drafts fails, removal then throws and retains the profile. Its activity history has nevertheless been deleted. This violates the failed-removal promise and removes the person's available Undo operations.

**Suggested fix:** separate closing/draining an owner from erasing its data. Validate the queue and complete preservation before destructive cleanup; erase activity in the committed deletion phase. Restore usable owners when removal aborts. Keep the entire sequence inside the controller's existing exclusion lanes.

**Test evidence:** `test/queued_prompt_removal_wiring_test.dart:165` says the changed-queue path “loses nothing”, but checks only the profile, queue and stash. The audit probe records real activity through the shared controller, changes the queue after inspection, and asserts that deletion throws while the history key has disappeared; result below.

### F3 — P2: An unreadable queue is converted to “no snapshot”, bypassing preservation

**Location:** [servers_screen.dart:590](../../../lib/ui/screens/servers_screen.dart#L590), lines 592–595 and 635–638; [connection.dart:6133](../../../lib/state/connection.dart#L6133), lines 6133–6165.

The new removal flow catches queue-inspection failure and continues with `queued = null`. It still invokes deletion with `keepQueuedPrompts: true`. The controller validates/preserves only when the plan is non-null. With an unreadable persisted queue and an empty decoded in-memory queue, it skips queue saving and can successfully remove the profile while leaving the unreadable shared blob behind. The queued work is neither preserved as drafts nor given a recoverable profile owner.

The older nullable deletion API existed before this branch; the new preservation UI specifically routes its fail-closed inspection error into that unchecked path. Unknown queue state must not mean an empty queue.

**Suggested fix:** stop removal and show a recoverable queue-unavailable error when inspection fails. In the controller, require a valid current plan for preservation and independently reject unreadable queue state, including callers that omit the plan. Retain the source blob and profile until recovery or an explicit, separately explained destructive decision.

**Test evidence:** `test/queued_prompt_removal_test.dart` proves the preservation service rejects corrupt input, but does not cover the UI catch-and-continue path. The audit controller probe seeds malformed synthetic queue data, confirms inspection throws, then invokes the exact null-plan/keep call made by the screen; result below. This is not an end-to-end widget reproduction.

### F4 — P2: New diagnostic/report sinks lack production registration of loaded secrets

**Location:** [report_problem.dart:175](../../../lib/diagnostics/report_problem.dart#L175); [problem_report.dart:116](../../../lib/feedback/problem_report.dart#L116); [kit_redact.dart:48](../../../lib/ui/kit/kit_redact.dart#L48).

The new durable diagnostic and public-report sinks depend on `KitRedact.text`. Its contract requires registering loaded credentials, but a search of production `lib/` finds only the `registerKnownSecret` definition and no caller. Pattern matching masks familiar provider prefixes and named fields, but cannot recognize arbitrary values.

If a loaded opaque server password or custom-provider key is echoed in an error/job log as ordinary prose, a short value without a known prefix survives persistence and report scrubbing. Public report previews, copied report text and GitHub form prefills can then contain it. The failure is conditional on an echo; this audit did not observe a real secret or claim that ordinary successful `/config/providers` responses are logged.

**Suggested fix:** register credentials at trusted load/upsert/transport ingress before capture, including provider credentials when they enter the app. Keep raw provider configuration out of diagnostics regardless of registration. Cover startup ordering and credential updates; use domain-safe errors wherever arbitrary response text is unnecessary.

**Test evidence:** `test/problem_report_test.dart:171` and `test/report_problem_startup_test.dart:97` manually register their synthetic credentials. Those tests verify the redactor after wiring it themselves, not production registration. Both the basic audit probe and a follow-up through the real `ProfileStore.load` path reproduced the leak: a mocked secure-storage read supplies a synthetic password, the loader restores it, and an error containing that loaded value survives the real on-disk `ReportProblem` snapshot and `problemReportScrub`. The echo is synthetic, not a live-server observation.

### F5 — P2: “Copy link” deliberately bypasses masking for credential-bearing URLs

**Location:** [external_link.dart:109](../../../lib/ui/widgets/external_link.dart#L109), lines 109–113.

The newly added alternative action calls `KitCopy.copy(context, address, redact: false)`, assuming a link cannot contain a secret. Allowed HTTP(S) links can contain `api_key`, `access_token` or other credential query parameters. A server/markdown link with such a parameter is copied verbatim when the person selects Copy link. The same gate is used for server-provided OAuth URLs in `lib/ui/screens/library/integrations_screen.dart:1997`.

This requires the explicit copy action; it is not automatic clipboard exfiltration. Rejecting URL user-info does not protect query credentials. In debug, the same raw address also triggers `KitDetailsFold`'s secret assertion. Release masks the displayed Details value, but that does not change the separate `redact: false` copy callback.

**Suggested fix:** disable copying credential/auth-bearing links or copy a clearly labeled redacted representation. Preserve the original URI only for the approved launch. Do not use `redact: false` in this generic untrusted-link action.

**Test evidence:** `test/external_link_test.dart:217` asserts full copying for an ordinary URL. The audit variant substitutes an `api_key` query with a synthetic fixture. It **fails** on the real `KitDetailsFold` secret assertion (`kit_details_fold.dart:153–165`), so it does not establish a successful clipboard copy. The release clipboard bypass is source-confirmed; a release-mode or isolated action test is still needed.

### F6 — P2: Concurrent native setup writers can overwrite terminal state or corrupt resumability

**Location:** [SetupRunner.kt:405](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt#L405), lines 405–411; also lines 166–169, 225–226 and 351–374.

The periodic writer and job worker both call `writeNow`. JSON serialization takes the lock, but file writes use the same `setup.json.tmp` outside it. A periodic writer can serialize `running`, pause, and overwrite the job worker's later `done` snapshot. Interrupting the writer without joining/draining it does not cancel file I/O already underway. Concurrent opens to the same temporary file can also collide; the loser may use the non-atomic `file.writeText` fallback.

After the worker exits, `status()` reads this file. Completion can appear still running or interrupted, and a malformed snapshot can lose the resumable job selection and parameters.

**Suggested fix:** use one serialized persistence owner covering snapshot creation through atomic replacement. Drain/stop the periodic writer before the terminal commit; avoid a direct-file fallback that exposes partial writes. Make persistence failure explicit instead of treating it as durable success.

**Test evidence:** source-confirmed interleaving, not a reproduced native race. Existing native Termux tests exercise `TermuxSetupShell`, not this `SetupRunner` writer. Source guards in `builtin_project_lifecycle_guard_test.dart` and `phone_setup_notification_route_test.dart` do not prove atomic persistence. Add a deterministic native test that pauses the old snapshot, commits completion, then releases it and reloads the job.

### F7 — P2: Thermal resume discards recovery state even when session wake fails

**Location:** [thermal_guard_teams.dart:179](../../../lib/builtin/thermal_guard_teams.dart#L179), lines 179–186; [thermal_guard.dart:437](../../../lib/builtin/thermal_guard.dart#L437), lines 437–445.

After successfully resuming the city, `GasCityThermalTeams.resume` ignores every session `/wake` result and returns true. A session wake returning HTTP 500 or timing out therefore causes `ThermalGuard` to erase the durable hold and announce recovery although the previously suspended work remains stopped. Automatic retries no longer retain the failed session IDs.

**Suggested fix:** require confirmed wake success or confirmed session absence. Retain the hold and failed IDs on an uncertain/failed wake; announce full recovery only after all owned work is accounted for. Preserve the existing rule that user-disabled teams are not restarted.

**Test evidence:** `test/thermal_guard_test.dart:442` covers only successful wake responses; its unreachable test mocks a false port result. The audit variant makes the city PATCH succeed and the wake return 500 while observing `resume() == true`; hold deletion follows the cited controller branch. It does not separately simulate an Android thermal event.

### F8 — P3: Default-notice writes have no owner to drain during profile deletion

**Location:** [interaction_defaults.dart:282](../../../lib/state/interaction_defaults.dart#L282), lines 282–300; [default_notices.dart:29](../../../lib/ui/widgets/default_notices.dart#L29).

`claimDefaultNotice` constructs a throwaway `InteractionDefaultsStore`. Its per-instance write queue has a `drain` method, but production deletion does not call it and the store has no profile-presence/closed guard. A platform write already pending when the scoped sweep runs can recreate `oc.defaultNotices.<deletedProfile>` after a reportedly complete deletion. Correct key naming alone cannot prevent late writes.

**Suggested fix:** share an owner per profile, stop write admission and drain it in the deletion transaction, and reject writes for absent profiles. Include project-default writes in the same lifecycle.

**Test evidence:** source-confirmed missing coordination; no race reproduced. Existing `test/interaction_defaults_test.dart` covers persistence/refusal/direct sweeping, not delayed writes overlapping actual profile deletion. Add a delayed platform-write test and reload preferences after removal. Notice contents are enum names, so this is lower priority than prompt or credential exposure.

## Other reviewed boundaries and limits

- **Saved-prompt migration:** inspected copy verification, attachment restoration, source-removal ordering, corrupt/full-store handling and profile-scoped backup keys. No additional confirmed migration data-loss regression. The current controller does integrate kept queued prompts under its queue lock; it is not merely an unwired service.
- **Provider configuration:** no direct new response-body/header logging of `/config/providers` found in reviewed API changes. F1/F4 concern alternate error/report paths. Perf tracing records route templates/status/timing, not provider response bodies.
- **External launch policy:** an OAuth `launchUrl` call inside the callback supplied to `openExternalLink` is not a bypass. F5 concerns its new clipboard alternative.
- **Architecture:** the new protocol-specific `Api2Error` import in `product_states.dart:9` is additional UI-to-API coupling; resolve it with F1's domain-owned error mapping. Pre-existing protocol imports are not listed as new regressions. No concrete new feature-gating failure based solely on flavor was established in the reviewed paths. UI integrations must still depend on domain contracts and capabilities.
- **Android 15:** existing `BackgroundConnectionService.onTimeout` stops foreground operation, tells Dart and stops itself. New setup/server services use `specialUse`, not `dataSync`. The six-hour shared dataSync budget still applies to the dataSync service; battery exemption does not remove it. See [Android's foreground-service timeout contract](https://developer.android.com/develop/background-work/services/fgs/timeout). No device timeout test was performed.
- **MethodChannels/scripts:** reviewed setup dispatch, identifiers, path quoting and cancellation/resume boundaries. No additional demonstrated injection finding; this is not a claim that all scripts or native channels are exhaustively validated. No live scripts were dispatched.
- **Tailscale:** existing host classification accepts the CGNAT range/`.ts.net` as a transport-policy hint, not evidence of an active encrypted tunnel. This classification predates the branch and is not ranked as an introduced defect. [Tailscale documents CGNAT conflicts with ISPs and other VPNs](https://tailscale.com/docs/reference/troubleshooting/network-configuration/cgnat-conflicts). The unauthenticated loopback team-control model also predates this branch; loopback does not isolate Android apps from each other. Neither assumption was treated as a verified security boundary.

## UI hook-up contract for Claude

This was the initial remediation contract. The implemented contracts and remaining controller work are recorded in **Fixed** below.

| Owner / boundary | Required behavior | Acceptance evidence |
| --- | --- | --- |
| Domain/API error mapping → kit error surface | Return a localized-safe error category and separately redacted technical details; no raw server string in body, notification or accessibility announcement. UI imports domain error types. | v2 400/422 credential fixture absent from visible text; redacted Details remains useful; raw-error guard covers the mapper. |
| Profile removal controller → Servers confirmation | Inspection failure is a blocker, distinct from zero items. Controller revalidates a mandatory preservation snapshot under serialization before destructive cleanup. | Corrupt queue and stale plan both retain profile, queue, activity/Undo and usable owners; retry after recovery works. |
| Credential ingress → diagnostics/reporting | Register loaded/updated secrets before capture and keep raw provider maps private. Only an already scrubbed snapshot reaches preview/copy/share. | Tests use real load/update paths rather than registering fixtures directly; inspect persisted/exported output without printing credentials. |
| External-link kit action | Show the destination host; copying sensitive links is unavailable or explicitly redacted. Launch receives the original only after confirmation. | Credential-query clipboard test and ordinary-link behavior test. |
| Native setup → setup progress UI | Durable status commits are ordered; terminal state survives restart. Storage failure is a typed, plain-language failure with safe Details. | Deterministic competing-write native test, process-restart recovery test. |
| Thermal controller → notice UI | Failed/unknown wake retains recoverable state; partial recovery is not presented as fully resumed. | Successful city resume plus failed wake keeps the durable hold and later retries. |
| Per-profile preference owners → deletion | Stop admission, drain pending writes, then sweep `oc.<what>.<profileId>`; separately handle shared blobs. | Delayed-write deletion/reload test leaves no resurrected scoped keys. |

Keep `connection.dart` single-owner. Keep both sides of native channels under one owner. Screens arrange kit components; add any missing UI part to `lib/ui/kit/`, with English/Arabic copy and capability-driven availability. No screen redesign is needed for these fixes.

## Verification

Validation ran against the unchanged candidate above, with the pinned toolchain:

```text
/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter
/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/dart
```

Heavy invocations use `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh` and are serialized. No reviewer launched tests. The audit coordinator ran the explicitly requested analyzer and focused checks; there is no full-suite claim.

- `flutter analyze`: **PASS**, no issues, 134.1 seconds. Dependency resolution completed as part of this command.
- Focused test batch: **PASS**, 355 tests across the 18 files below, 2 minutes 24 seconds; zero failed/skipped tests. The three native Termux shell scenarios actually ran with the available Kotlin compiler.
- Six adversarial probes: **5 reproduced the asserted broken behavior; 1 failed on the product's debug secret assertion**. The batch exit code was 1, not a clean test pass. A seventh, focused probe through the actual credential loader also reproduced F4 and exited 0. Details below.
- `dart format --language-version=3.10` applies only to temporary probe files outside the worktree; no production Dart file was edited.
- No emulator, APK build, signing, SDK regeneration, live-server request, real credential access or release operation was performed.
- Report validation: local file/line links resolved; staged `git diff --check` passed. No Dart formatting applies to this Markdown-only change.

Focused command (preceded by `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test --`, with the pinned binary):

```bash
flutter test --no-pub --concurrency=1 \
  test/queued_prompt_removal_test.dart \
  test/queued_prompt_removal_wiring_test.dart \
  test/saved_prompts_migration_test.dart \
  test/saved_prompts_photo_recovery_test.dart \
  test/profile_deletion_test.dart \
  test/interaction_defaults_test.dart \
  test/kit/kit_redact_test.dart \
  test/problem_report_test.dart \
  test/report_problem_startup_test.dart \
  test/failed_job_report_test.dart \
  test/product_error_text_test.dart \
  test/no_raw_error_text_test.dart \
  test/external_link_test.dart \
  test/background_live_test.dart \
  test/setup_engine_test.dart \
  test/setup_termux_resume_test.dart \
  test/termux_setup_native_test.dart \
  test/termux_scripts_test.dart --reporter expanded
```

| Probe | Result | What it establishes |
| --- | --- | --- |
| F1 v2 400 rejection | Reproduced | Synthetic key remains in `productErrorText` output. |
| F2 changed confirmation | Reproduced | Real shared activity history disappears even though profile removal throws and retains the profile. |
| F3 corrupt queue/null plan | Reproduced | Controller removes the profile while the unreadable queue blob remains. UI call flow is source-traced. |
| F4 opaque secret | Reproduced | Actual diagnostic file and report scrubber retain an unregistered synthetic secret. |
| F4 real loader follow-up | Reproduced | `ProfileStore.load` restores the synthetic password from mocked secure storage without registering it; the loaded value survives persistence and report scrubbing. |
| F5 credential-query copy | Debug assertion failure | The generic gate passes a secret to `KitDetailsFold`; release copying is source-only evidence. |
| F7 wake HTTP 500 | Reproduced | Adapter returns true despite failed wake; durable-hold removal is source-traced. |

Probe commands used the same pinned Flutter, machine lock, `--no-pub --concurrency=1 --reporter expanded` and `--plain-name AUDIT`, with these temporary files:

```text
/tmp/codex_audit_probes_test.dart
/tmp/codex_audit_clipboard_test.dart
/tmp/codex_audit_thermal_test.dart
```

The first file reuses `queued_prompt_removal_wiring_test.dart`'s secure-storage mock, temporary vault and boot fixture, then adds the F1–F4 checks described above. The clipboard file copies `external_link_test.dart`'s full-address copy test and changes its ordinary query to a synthetic `api_key` query. The thermal file copies the successful pause/resume adapter test and returns HTTP 500 specifically for `/wake`. These changes are outside the repository.

The loader follow-up uses `/tmp/codex_audit_loaded_secret_test.dart` with `--plain-name 'AUDIT loaded'`. It changes the boot fixture's secure-storage `read` reply to the synthetic password and uses the password returned by `ProfileStore.load` in the diagnostic event. No test-only registration is performed.

The temporary probes assert the currently broken outcomes to establish reproduction; a passing probe is evidence of a bug, not a passing safety regression. They use synthetic values only and are not committed. The first probe attempt had a harness compilation error from instantiating sealed `Api2Error`; it was corrected to the production subtype `Api2RequestError` before the final run. Native persistence and late-default-write races remain source-only findings.

Local raw validation logs (ephemeral, not committed): `/tmp/codex-audit-20260927-analyze.log`, `/tmp/codex-audit-20260927-tests.log`, `/tmp/codex-audit-20260927-probes-final.log`, `/tmp/codex-audit-20260927-loaded-secret.log`. The report retains their relevant outcomes; no private credential output is included.

Initial audit state: report committed without implementation changes or a push. The subsequent authorized remediation follows.


## Fixed

Remediation started from integration tip `d0a8abcc` on `codex/audit`. The six requested fixes have independent commits and committed regression tests. `lib/state/connection.dart` is unchanged from that tip. The original finding locations above remain historical; the locations below were rechecked after integration. No stored-format migration, UI redesign, live credential experiment, push or release was performed.

| Finding | Commit | Implemented behavior and current entry points | Regression evidence |
| --- | --- | --- | --- |
| F1 | `a65dcea9` | [domain/product_failure.dart:28](../../../lib/domain/product_failure.dart#L28) classifies both protocols. [product_states.dart:51](../../../lib/ui/widgets/product_states.dart#L51) renders localized categories without API imports; technical reasons pass through redaction only in `productErrorDetails` at line 94. Server-supplied staged-revert tags cannot promote server prose to authored copy. | `product_error_text_test.dart`: v1/v2 400/422 body masking and real widget Details; `no_raw_error_text_test.dart`: direct mapper coverage and no UI-to-API import. The mapper is no longer excluded wholesale from the raw-error scan. |
| F4 | `509c8cd0` | [profiles.dart:759](../../../lib/state/profiles.dart#L759) registers secure-storage reads; upsert registers before writing (line 804). Secret fields register before controller/change callbacks, authenticated transports before requests, provider credential submissions before transport, and provider/config responses before decoding exposes values. Registration handles credential fields without logging the containing response. | Seven tests in `credential_ingress_redaction_test.dart`, including real `ProfileStore.load` through mocked Keystore into persisted diagnostic/report output, failed upsert, entry callback ordering, transports, provider reads and both submission protocols. Fixtures are synthetic; assertions do not print credential values. |
| F5 | `8dc17fdc` | [external_link.dart:87](../../../lib/ui/widgets/external_link.dart#L87) creates the masked address for Details and Copy link; only the explicitly approved launcher receives the original URI. The G12 reviewed bypass list shrank by this file. | `external_link_test.dart` exercises the real dialog and clipboard: sensitive query absent, no launch, ordinary URL unchanged. |
| F6 | `c45c17d9` | [SetupRunner.kt:409](../../../android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/SetupRunner.kt#L409) holds one lock from snapshot through atomic persistence; the periodic writer is interrupted and joined before terminal commit (line 233). `SetupPersistence.kt` syncs a unique temporary file and atomically renames, with no direct-file fallback. `setup_persistence` crosses MethodChannel as a typed, safe failure and maps to `SetupFailureKind.persistence`. | `setup_runner_native_test.dart` runs the real Kotlin owner with deterministic competing-write and storage-failure scenarios; `setup_persistence_failure_test.dart` tests typed Dart status mapping. Kotlin release compile passed. |
| F7 | `3f14c4ae` | [thermal_guard_teams.dart:158](../../../lib/builtin/thermal_guard_teams.dart#L158) confirms every owned session running, successfully awakened, or absent from its session resource. A failed wake or wake-route 404 is insufficient. The durable hold and its IDs remain while any outcome is uncertain; retry skips confirmed running sessions. | `thermal_guard_test.dart` tests 500/null/wake-404 failure, confirmed absence, partial recovery and retry after restart through the real adapter/controller. No full-recovery notice on failed wake. |
| F8 | `7c6d009c` | [interaction_defaults.dart:249](../../../lib/state/interaction_defaults.dart#L249) shares one owner per preferences/profile. [profiles.dart:908](../../../lib/state/profiles.dart#L908) closes admission and drains that owner before scoped key discovery/removal, through the existing deletion transaction. Absent/closed profiles reject notice and project-default writes. | `default_notice_deletion_test.dart` delays a real preference write across `ProfileStore.remove`, verifies deletion waits, reloads disk, and verifies no resurrected keys or late writes. Existing interaction-default tests now seed authoritative profile membership. |
| F3 — screen portion only | `d0f68b35` | [servers_screen.dart:590](../../../lib/ui/screens/servers_screen.dart#L590) stops immediately on inspection failure and uses the existing localized failure surface. No deletion confirmation or controller call follows an unreadable queue. | `revamp/queued_prompt_removal_test.dart`: corrupt queue produces the recovery copy, no confirmation, no deletion call. The controller bypass remains deferred below. |

### Regression evidence and validation scope

Each finding's new safety assertion was run against its original implementation before the fix. F1 exposed server text; all seven F4 ingress tests failed; F5 hit the debug secret assertion; F6 failed deterministic old-writer ordering and typed-failure tests; F7 lost the hold on uncertain wake; all three F8 owner/admission/drain tests failed. F3's screen test found the destructive confirmation despite inspection failure. Those same safety assertions subsequently passed. These are behavior tests, not passing tests that merely assert the old broken outcome.

The local before/after logs are ephemeral: `/tmp/audit-f1-red-f4-f6-green.log`, `/tmp/audit-f4-f5-red.log`, `/tmp/audit-f6-red.log`, `/tmp/audit-f4-f7-red.log`, `/tmp/audit-f8-red.log`, `/tmp/audit-f1-f5-f8-green-f3-red.log`, and `/tmp/audit-f3-green.log`. Mixed discovery runs intentionally contain failures for findings not fixed yet; they are not claimed as successful integration gates.

Behavior-check candidate: `d0f68b35`. Final code candidate: `5da245fb`, which adds analyzer-required braces and removes one redundant test import; it changes no behavior. Pinned Dart formatting checked all 31 changed handwritten Dart files with `--language-version=3.10 --output=none --set-exit-if-changed`: zero changes. `git diff --check` passed.

The 21-file focused manifest below passed **436 tests, zero failures or skips**, serially under `OC_TEST_SLOTS=1 tool/qa/machine_lock.sh test`, with the pinned Flutter, `--no-pub --concurrency=1 --reporter expanded`. This includes all four requested gates. Log: `/tmp/audit-final-focused.log`.

```text
test/kit_ratchet_test.dart
test/redaction_test.dart
test/ui_glossary_test.dart
test/no_raw_error_text_test.dart
test/product_error_text_test.dart
test/external_link_test.dart
test/credential_ingress_redaction_test.dart
test/default_notice_deletion_test.dart
test/interaction_defaults_test.dart
test/profile_deletion_test.dart
test/profile_store_test.dart
test/profile_secure_storage_test.dart
test/queued_prompt_removal_wiring_test.dart
test/kit/kit_redact_test.dart
test/kit/kit_field_test.dart
test/setup_config_adapter_test.dart
test/server_probe_test.dart
test/thermal_guard_test.dart
test/setup_runner_native_test.dart
test/setup_persistence_failure_test.dart
test/setup_engine_test.dart
```

The removal-screen behavior group also passed **6 tests**, using `flutter test --no-pub --concurrency=1 test/revamp/queued_prompt_removal_test.dart --plain-name behaviour --reporter expanded` under the same machine lock. Log: `/tmp/audit-final-removal.log`. Coverage at `d0f68b35`: **442 tests**. Existing goldens were not regenerated.

Kotlin compile: from `android`, `../tool/qa/machine_lock.sh build -- ./gradlew :app:compileReleaseKotlin` passed in 2m55s, 177 tasks; log `/tmp/audit-f6-kotlin.log`. It covered the final native source. The ignored wrapper launcher/JAR were missing in this worktree and were restored from the existing sibling checkout before running the pinned wrapper. No tracked wrapper change, signing or APK delivery was needed. The deterministic JVM harness executed all three native scenarios without skips.

After the analyzer-only cleanup, the four gates plus `product_error_text_test.dart`, `credential_ingress_redaction_test.dart`, `default_notice_deletion_test.dart` and `interaction_defaults_test.dart` were rerun on `5da245fb`: **122 tests passed, no failures or skips**. Log: `/tmp/audit-final-lint-tests.log`. Unaffected native, thermal and link source is identical to the broader passing candidate.

Final analyzer: pinned `flutter analyze --no-pub`, serialized under the machine lock, passed with **No issues found** on `5da245fb` (78.1s). Log: `/tmp/audit-final-analyze-clean.log`. The first analyzer pass found six style issues; `5da245fb` resolves them without adding ignores.

No full repository suite, device process-death test, thermal hardware test or six-hour foreground-service test is claimed.

### Exact deferred F2/F3 controller plan

The following work is intentionally **not implemented** because `connection.dart` has another owner. Rebase these locations before editing.

1. **F2: split admission/drain from destructive cleanup.** `deleteProfileAndLocalData` at line 5997 currently disables recovery and changes owners before queue validation; lines 6027–6033 invalidate saved-prompt Undo and dispose its controller. `_deleteProfileAndLocalData` calls `AutomaticActivityController.closeProfile` around line 6110, which currently deletes history and disposes inverse actions (`automatic_activity.dart:71`). Move irreversible history deletion, Undo invalidation and controller disposal after successful queue preflight/preservation. Add reversible suspension/drain methods for owners that currently combine closing with deletion. Keep the existing deletion/admission epoch so callbacks cannot write during the transaction.
2. **F2: validate and preserve under the queue lane before deleting anything.** Inside `_serializeQueueChange` (currently line 6133), inspect authoritative stored queue data, compare the confirmed snapshot with both current in-memory and persisted entries, and verify retained drafts/attachments durably before removing source entries. Do not move preservation outside this serialized transaction. A changed queue, corrupt store, full retained store or refused write must leave profile, source queue, activity history and Undo intact. A verified retained copy may remain after a later failure; use existing idempotent IDs so retry cannot duplicate it.
3. **F2: reopen after aborted preflight.** On rejection, resume suspended activity/automation/consent/default owners, pending-auth admission, monitors and managed recovery only when the profile remains present and no destructive phase has begun. Restore the prior enabled state rather than blindly enabling services. Keep old callback epochs invalid, but allow new operations and a fresh deletion inspection. F8's shared defaults owner stays closed once its sweep begins; an explicit coordinated reopen is needed if a later partial deletion retains the profile. Do not construct a new owner during the sweep-to-profile-removal gap.
4. **F3: enforce inspection in the controller independently.** Replace the `if (queuedPrompts != null)` validation bypass at line 6134. Always reject unreadable queue storage, even for callers that omit a plan. For `keepQueuedPrompts: true`, require a valid confirmed plan or return the typed queue-removal failure without mutation; do not equate a missing plan with zero entries. Existing direct destructive callers still need a valid authoritative read. A separate explicit discard-corrupt-data product flow would require its own confirmation; this change must not infer permission for it.
5. **Acceptance tests for the controller owner.** Extend `queued_prompt_removal_wiring_test.dart` using the real shared activity owner and saved-prompt Undo. Test stale confirmation, unreadable disk with empty memory, null-plan keep, retained-store capacity/refusal, and a racing dispatch marker. Assert byte-preserved source data, retained profile/history/Undo and usable owners after rejection; then repair/reinspect and complete a successful retry. Preserve the existing attachment and duplicate-safe retention checks. Run profile deletion, queue wiring, activity, saved-prompt and default-notice tests before the integration gate.

### Current UI hook-up contract for Claude

- Error surfaces use `productErrorText` for localized prose and `productErrorDetails` only for folded Details/copy/report. `ProductFailure.technicalDetails` is untrusted; never bind it directly to visible body text or announcements. The UI imports the domain mapping, not protocol exception classes. F1 added English copy in `app_en.arb` and regenerated localizations; Arabic currently uses the generated fallback for this new key.
- Keep all untrusted links behind `openExternalLink`. Its Copy link action now produces a masked URL; Details shows the same representation. Do not bypass it for OAuth or server markdown URLs.
- Secret-entry kit fields and trusted credential loaders register values before downstream capture. Never render or report whole provider configuration objects. Registration supplements redaction and safe domain messages; it does not authorize raw diagnostic capture.
- Setup progress consumes typed persistence failure through the existing plain-language failure UI. A failed commit must not be presented as durable completion. The last durable snapshot may recover as interrupted after storage failure; no claim of persisted success is made.
- Thermal recovery UI announces resumed only when the adapter confirms all owned work. Partial wake failure keeps the existing hold/retry behavior.
- Construct default notices through the shared `InteractionDefaultsStore` factory. Missing/closed profiles do not admit writes. No screen-local replacement writer is allowed. Existing `ProfileStore.removeScopedPreferences` supplies the deletion hook without a new `connection.dart` edit.
- Servers removal now stops on inspection failure. The controller owner must complete the deferred F2/F3 transaction contract above before those findings can be marked fully fixed.

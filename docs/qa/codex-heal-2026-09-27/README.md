# Phone-server healing and voice RAM parity

Finish line: a confirmed phone-server crash is repaired by one foreground recovery owner, under the saved automation policy and a durable retry budget; setup and the first microphone use choose the same speech pack.

Non-goals: screen redesign, restarting interrupted agent tasks, automatically restoring the AI Team, background watchdogs, releases, or changing Android service limits.

This follows [the P6.5 report contract](../codex-p65-2026-09-27/README.md). Implementation started September 27; final checks also ran September 28, 2026. Backend/state changes only; the existing shell invokes them without changing its presentation.

## Runtime wiring

- `OcApp` eagerly reads `phoneServerHealingProvider`. Its `PhoneServerHealing.recovery` is a `BuiltinServerRecovery` notifier shared by launch, resume, the app-exit path, and a five-second foreground health timer. Opening This phone is not needed to enable healing.
- `ConnectionController` eagerly registers the Termux recovery owner and the existing `recordServerAct` callback. The same callback records `AutomaticActKind.restart`; the in-app launch and resume paths no longer create separate restart acts.
- Both runtime owners require `AutomationBehavior.restartPhoneServer` **and** `pollRestartHealth`. Lifecycle state must be explicitly `resumed`; unknown, inactive, paused, hidden and detached do not authorize automatic work. Turning either policy off invalidates pending admission.
- In-app Android state supplies desired-running intent and a recovery generation. Only an explicit manual start establishes intent; missing legacy intent is unknown and never guessed. Explicit Stop, notification Stop, uninstall and service timeout clear intent, even if the child already exited. Automatic dispatch is checked again natively at process creation. Cancellation kills only that attempt's unconfirmed process by identity, never a replacement manual process.
- In-app retries are reserved durably before dispatch: at most three automatic attempts between confirmed manual starts; 15/30/45-second retry timestamps survive app recreation. Automatic success and app resume do not replenish the budget. An unhealthy but still-running process is not overlapped with another start.
- A start is confirmed through authenticated server health, not a PID, successful command dispatch, or historical exit report. Uncertain outcomes remain uncertain. Stable event IDs let the existing automatic activity store deduplicate recorded restarts.
- Explicit Stop and runtime switching suspend Termux recovery without turning off the person's policy. A subsequent explicit, confirmed manual Start can adopt the new native operation. Automatic recovery never adopts an unrelated operation.

`PhoneServerHealing.report` wires the earlier `LifecycleReportController` into the same app lifetime. It invalidates on suspension, owner changes and non-ready recovery states; after confirmed readiness it checks the server and previously running services. `everythingBack` remains false when the team is still stopped or a historical service is unknown. Server recovery deliberately does not start the team.

## Storage and deletion

Per-profile state uses `oc.builtinRecovery.<profileId>`, `oc.managedServerRecovery.<profileId>` and the existing `oc.automation.<profileId>` / automatic activity keys. No credentials are stored in recovery budgets or receipts.

The in-app runtime has one shared profile pointer, `oc.builtinServerOwner`, written before explicit manual dispatch. Multiple legacy profiles without a known owner require an explicit Start; selecting another profile does not authorize restarting its runtime. Deletion synchronously closes admission, drains pending recovery, sweeps scoped state, and clears a matching shared owner pointer to an empty value so another alias is not silently selected. Native `builtin_server_recovery/wanted` is device-wide process intent, not profile storage.

## Claude UI hook-up contract

Read/listen to the existing policy controller for the intended server profile:

```dart
final policy = AutomationPolicyController.forProfile(prefs, profileId);
final enabled = policy.value.allows(AutomationBehavior.restartPhoneServer);
await policy.setBehavior(AutomationBehavior.restartPhoneServer, nextValue);
```

P6.1 and This phone's “Restart after a crash” must use this exact behavior, with the same profile ID. Do not add another saved boolean. The Termux `enabled` / `setEnabled` compatibility API delegates to this policy, and `forProfile(prefs, id)` always returns that profile’s facade. The in-app This phone switch can use the policy directly; this backend job does not add a screen row.

Read `ref.read(phoneServerHealingProvider).recovery.value` and listen to `recovery` for in-app healing. Fields: `phase`, `profileId`, `attempts`, `maxAttempts` (3), `nextAttemptAt`. Suggested localized status-line and This phone copy:

| Phase | Plain words / action |
| --- | --- |
| `restarting` | “Restarting the phone server…” — show progress, not a green connected state. |
| `waiting` | “The phone server stopped. Trying again soon.” Show the next attempt time and attempts used. |
| `exhausted` | “The phone server could not restart after 3 tries.” Offer the existing explicit Start action. |
| `unconfirmed` | “The phone server is not answering yet.” Offer Check / Details; do not claim a restart succeeded. |
| `storageUnavailable` | “Automatic restart is paused because its progress could not be saved.” Offer Details. |
| `stopped` | “The phone server is stopped.” Explicit Start is available; do not silently restart. |
| `paused` | Policy off: “Automatic restart is off.” Background: “Open the app to continue.” |
| `checking` | Keep the last neutral status while checking; no success claim. |
| `ready` | “The phone server is running.” Use the connection's own state for connected/session status. |
| `idle` | No managed in-app owner; no healing claim. |

Termux has one installation owner. Check `ownsInstallation`: an alias facade does not probe or restart. With multiple profiles and no retained owner, an explicit successful Start establishes ownership; policy is never borrowed from another alias. Attempts are mirrored across known live aliases so switching profiles cannot replenish the budget. The connection supplies a runtime map and a mismatching native runtime is not adopted.

For Termux, listen to `ManagedServerRecovery` and use its `phase`, `attempts`, `nextAttemptAt`, `manuallySuspended`, `foreground`, and typed `error`. Map `restarting`, `waitingToRetry`, `exhausted`, and `paused` to the equivalent words above; `monitoring` means its matching native operation passed health. `waitingForServer` is not a known crash. Manual Stop must say “The phone server is stopped,” not “Open the app to continue.” `retryCheck()` reconciles an uncertain result; it does not reset the retry budget.

For an Android-ended-app notice, listen to `PhoneServerHealing.report`. Use `showNotice`, `exitKind`, `stoppedAt`, `readiness`, `everythingBack` and `dismiss()`. Only `everythingBack` permits a services-restored claim; never say that interrupted tasks resumed. Existing `AppExitRecovery.notice.recoveryAllowed` is permission, not evidence that a restart occurred.

All failure presentation uses plain words plus the kit's Details affordance. Never render native messages, shell output, probe responses, or raw exception strings. No new user-facing strings or layouts were added here.

## Android lifetime limits

No code was added to `lib/background/`, no alarm/job/watchdog or sticky service was added, and no background budget is renewed by the recovery owner. The existing built-in service uses `specialUse`; its new defensive `onTimeout` revokes intent, drops foreground status/stops itself immediately and stops child processes asynchronously. The existing `dataSync` live-connection service retains its Android 15 timeout behavior. Foreground-only recovery is a deliberate conservative boundary, not a promise of unattended healing while Android has stopped or restricted the app.

Reference: [Android foreground-service timeout limits](https://developer.android.com/develop/background-work/services/fgs/timeout).

## Voice setup parity

`automaticVoicePack` in `lib/voice/automatic_pack.dart` is the single selector used by `VoiceSetupComponent.packFor` and first-microphone `AutomaticVoiceSetup`:

| Total RAM | Automatic model |
| --- | --- |
| 1024 MB | tiny |
| 1536 MB | base |
| 3400 MB | small |
| Unknown / nonpositive | No automatic model |

A supported, already installed selected model stays selected. A saved preference alone does not override device capacity. ABI and microphone/capture support still apply. Storage does not silently select a smaller model: setup uses its existing free-space preflight; first-mic blocks the selected pack with its existing typed problem if space is insufficient. A model installed on a different/higher-RAM device is not reported ready when unsupported here.

## Verification

The required Kotlin command `cd android && ./gradlew :app:compileReleaseKotlin` passed (177 tasks, 3m36s). This host uses Ubuntu OpenJDK 17.0.20; Temurin 17 was not installed. No signing key was used or generated for this job.

Pinned tools: `/home/owner/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/{flutter,dart}`. `flutter pub get` completed. Formatting all 26 changed/new Dart files with `--language-version=3.10 --output=none --set-exit-if-changed` passed with zero changes; `git diff --check` passed.

The selected test manifest below covers 456 cases across 23 files, including all requested gates. The first integration run had four failures: a timezone-sensitive receipt assertion, incomplete setup-route test fakes, and two deletion assertions exposing an unnecessary policy write for unmanaged profiles. The production deletion path now does nothing when no stored Termux recovery exists. The four affected files were rerun (69 cases); the final route-fixture correction passed all six autostart cases. A final report/binding run passed all 22 cases after invalidating readiness before an explicit manual start. The other 19 files passed the initial run and their covered behavior was unaffected by those corrections.

Tests ran serially (`flutter test --concurrency=1`):

```text
test/voice_automatic_setup_test.dart
test/setup_voice_component_test.dart
test/builtin_server_recovery_test.dart
test/builtin_recovery_bridge_test.dart
test/phone_server_healing_test.dart
test/builtin_server_autostart_test.dart
test/builtin_server_test.dart
test/app_exit_recovery_test.dart
test/lifecycle_report_test.dart
test/managed_server_recovery_test.dart
test/local_server_controls_recovery_test.dart
test/termux_setup_finish_test.dart
test/termux_healing_arm_test.dart
test/termux_recovery_scripts_test.dart
test/profile_deletion_test.dart
test/automation_policy_test.dart
test/automatic_activity_test.dart
test/app_lifecycle_test.dart
test/kit_ratchet_test.dart
test/redaction_test.dart
test/kit/kit_redact_test.dart
test/ui_glossary_test.dart
test/no_raw_error_text_test.dart
```

All named gates passed without changing baselines or adding ignores. Local logs: `/tmp/oc-heal-final-tests.log`, `/tmp/oc-heal-rerun.log`, `/tmp/oc-heal-autostart-final.log`, `/tmp/oc-heal-report-final.log`, `/tmp/oc-heal-kotlin.log`. Initial-run failures are retained in those logs; later focused results supersede only the affected files.

Final `flutter analyze` passed with no issues (64.3s); log `/tmp/oc-heal-analyze-final.log`.

Candidate base: `5eb487ec`. Source/test manifest (29 files, excludes this documentation): `/tmp/oc-heal-final-candidate-sha256.txt`; manifest SHA-256 `2eaa08d3e247b126235f96e35e222595172315da43a309e4765a5bf5c4e85577`.
 No APK installation, physical crash/background/FGS-timeout exercise, release, push or full-repository test-suite claim is made by this backend job.

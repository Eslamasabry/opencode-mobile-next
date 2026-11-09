# P6.1 — What runs by itself (backend, 2026-09-27)

Finish line: expose a per-server AutomationPolicy with contract defaults, individually reversible choices, durable storage and a deletion-safe controller for the later automation-settings UI.

Non-goals: UI, new automatic behaviours, remote policy mutation, changing existing executors, or migration of previously granted permissions.

Read set: AGENTS.md; STANDARDS.md sections 2, 3, 13, 15 only; personas-verticals.md section 3; existing profile deletion, session approval, monitoring, planning and domain gateway APIs; KitRedact.
Write set: lib/domain/automation_policy.dart; lib/state/automation_policy.dart; test/automation_policy_test.dart; this QA directory; COMMIT_MSG.txt if committing is blocked.
Dependencies: existing SharedPreferences, KitRedact and ProfileStore sweep. One small cohesive policy slice; no parallel writers needed.
Acceptance: oc.automation.<profileId>; restart and profile isolation; deletion sweep; default-on routine recovery and default-off consent behaviours; every choice can be disabled; persistence failure never reports success.
Focused checks: pinned Dart format; pinned Flutter test --concurrency=1 test/automation_policy_test.dart; pinned Flutter analyze lib test; git diff --check.

## Feasibility

Local persistence and policy observation are available. No credentials, network adapter or host access is needed. ProfileStore.profileScopedPreferenceKeys discovers the new key by its existing shape.

Host policy mutation is unavailable: OrchestrationPolicyGateway.policy is read-only. No adapter or workaround is added. Existing saved permission list/revoke is available through ServerOperationsGateway, gated by ServerCapabilities.savedPermissionList. Server-side allow rules can bypass phone prompts; a local switch cannot revoke them remotely.

## UI hook-up

Import `package:opencode_mobile/state/automation_policy.dart`. Create one shared `AutomationPolicyController(profileId: profile.id, preferences: prefs)` for each profile; do not create a second writable instance for the same profile. The controller exports the domain value types. `value` is an immutable persisted snapshot; subscribe with `addListener` (or a ListenableBuilder), await edits and show a save error on failure. No optimistic success is published. The owning scope disposes the controller.

```dart
final automation = AutomationPolicyController(
  profileId: profile.id,
  preferences: prefs,
);
await automation.setBehavior(AutomationBehavior.monitorOtherServers, true);
await automation.setSupervision(AutomationSupervision.balanced);
await automation.setAutoApprove(false);
await automation.setAutoMergeOnGreen(false);
// Stop accepting edits and drain storage BEFORE the existing profile deletion:
await automation.prepareForDeletion();
await connection.deleteProfileAndLocalData(profile.id);
automation.dispose();
```

- **Supervision:** bind `value.supervision` and `setSupervision`. Fresh profiles use High; selecting Balanced or Autonomous is explicit consent for local automatic approval and merge-on-green. Each can then be independently disabled with its setter. Use `allowsAutoApproval` / `allowsAutoMerge` for effective permission; High always returns false, even if a raw approval preference was enabled. Never call `setSupervision` during page build/load: an explicit selection grants both consents again. These are local desired preferences, not the observed host supervision.
- **Auto-approve rules / Always allowed actions:** list the current server's rules through the existing `ServerOperationsGateway.listSavedPermissions`, gated by `ServerCapabilities.savedPermissionList`; revoke only a specifically selected rule with `removeSavedPermission(id)` and refetch after success. Keep that subpage inside automation-settings. The policy stores no duplicate rule list, permission pattern or credential. Existing per-session choices remain in `SessionAutoApprovalStore`; the local policy's approval gate must be consulted before `ConnectionController._maybeAutoApprove` sends an automatic answer. Turning the local gate off does not revoke saved rules at the server. Do not label server rules disabled by this local switch.
- **Housekeeping consents:** use `setBehavior` for `restartDevServices`, `stopIdleHelpers` (the contract's 10-minute idle threshold), `cleanCaches` (low space), and `updateWhenIdle`. All start off. Enabling requires the in-flow consent; reading preferences never grants it.
- **Background monitoring:** `monitorOtherServers` and `monitorQuota` start off. Consult these in the relevant existing monitor before it schedules work. Preserve notification, Wi-Fi, quiet-hour, Android lifecycle and global background-service limits. A policy opt-in is not Android permission or proof that monitoring is running.
- **Routine recovery:** `value.allows(behavior)` / `setBehavior(behavior, enabled)` cover the individual default-on contract choices. Expose only behaviours the current host/capabilities support. These flags are preferences; they do not create executors for missing behaviours. Safety constraints, particularly thermal/device limits, must still apply when optional automatic recovery is disabled.
- **Turn everything off:** `disableAll()` persists High, disables approval/merge and all behaviour flags. It does not revoke remote rules, stop manual work or cancel actions already sent to a server.
- **Deletion:** the owner must call and await `prepareForDeletion()` before the existing deletion method on every profile-removal path. It permanently closes this controller and drains a write already in progress; subsequent edits fail. The existing `ProfileStore.removeScopedPreferences` then deletes `oc.automation.<profileId>`. Ordinary `dispose` alone cannot await an in-flight write.

### Required owner integration (not edited here)

`lib/main.dart` / the owning composition layer: create and share the controller. `lib/state/connection.dart`: consult approval/reconnect/queue policy at execution boundaries and quiesce it before the deletion sweep. Existing monitor, phone recovery, team and update owners must consult their respective flags before scheduling their existing automatic actions. UI owners must add automation-settings and the nested saved-rules view with truthful unsupported states. This unit does not claim that changing a preference already turns off those runtime paths.

**Contract problem:** personas-verticals.md §3 says Balanced consents to auto-merge-on-green, but `lib/state/team_planning.dart`'s `TeamSupervision.balanced.line` tells the planner to ask before merges. This policy follows the explicitly requested personas contract. Do not translate it into that planner message or claim remote supervision changed until the owner resolves the conflicting wording and provides a supported host mutation contract. Host supervision mutation remains unavailable; no workaround was built.

## Storage and migration

Schema v1 is a JSON object at `oc.automation.<profileId>` with supervision, two approval booleans and an enum-keyed map of behaviours. No free-form data, rules, server errors or credentials are persisted. Every payload passes KitRedact; if redaction would change semantics the save is rejected with a generic error. Empty/secret-bearing profile IDs are rejected without echoing the ID. Saved state changes only after the platform accepts a write; failure reloads the plugin cache and notifies no success.

Absent record: routine behaviours on, consent behaviours off, High supervision. Corrupt/unknown schema: all off. Missing fields in an existing v1 record: off, so adding a future behaviour does not silently opt existing profiles in. No migration from session approvals, host policy, notification or monitor stores is performed; owners must reconcile those existing consents explicitly when wiring the feature. Preferences remain local to this device.

## Verification

Base: `e60218d5`, branch `codex/p61`. Initial tree was clean. HANDOFF.md is absent in this worktree. Read STANDARDS sections 2, 3, 13 and 15 only.

Seven focused behaviour tests added in `test/automation_policy_test.dart`: contract defaults/no write; all switches/restart/isolation; concurrent edits/listener timing; refused and throwing saves/retry; malformed/future storage; in-flight save plus actual ProfileStore deletion sweep; KitRedact rejection. The deletion test calls only the scoped sweep, not ProfileStore.load/upsert or Keystore; it needs no secure-storage mock.

- Requested pinned Dart wrapper: blocked before formatting by read-only SDK engine cache.
- Same pinned SDK's `bin/cache/dart-sdk/bin/dart format --language-version=3.10` formatted all changed Dart files. It subsequently exited 1 on a read-only telemetry file; missing package configuration also prevented resolving flutter_lints. See [dart-format.txt](dart-format.txt). This is formatting evidence, not a clean command exit.
- Pinned `flutter test --concurrency=1 test/automation_policy_test.dart`: blocked before startup by read-only SDK cache, exit 1; **no tests executed**. See [flutter-test.txt](flutter-test.txt).
- Pinned `flutter analyze lib test`: same startup block, exit 1; **no analyzer result**. See [flutter-analyze.txt](flutter-analyze.txt).
- No full suite, device, UI, native, live-server or release checks attempted; no screenshots apply to this backend-only change.

Verifier: resolve packages using the pinned SDK in a writable environment, run the requested formatter, the focused serial test file above and `flutter analyze lib test`. Integration and runtime policy enforcement remain a separate owner's work.

## State

Implemented: local policy/controller and tests. Enabled: not wired into runtime or UI. Verified: formatter processed sources; tests/analyzer blocked by environment. Deletion key and quiesce flow have behaviour tests awaiting execution. Deployed/released/pushed: no.

Committed: no. `git add` failed because `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p61/index.lock` is on a read-only filesystem. The exact commit message, including both requested trailers and `[skip ci]`, is saved at the repository root in `COMMIT_MSG.txt`. All implementation changes remain in this worktree.

Whitespace validation: `git diff --check` and `git diff --no-index --check /dev/null <file>` for every added file passed. No existing tracked file was modified.

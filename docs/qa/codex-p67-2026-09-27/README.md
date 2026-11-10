# P6.7 consent once, in flow — backend/state (2026-09-27)

Finish line: expose remembered, per-server in-flow consent and third-identical-ask invitation state, with denial reasons for Settings, for the UI owner to wire.

Non-goals: UI/native edits, automatic permission grants, changing global notification preferences, router rewrites, downloads without a verified mobile-data signal, deployment or release.

Status: **partial implementation; not enabled; runtime verification blocked**. Battery, maker auto-start, notification-preset consent and repeated-permission invitation APIs are implemented. The mobile-download and notification-router portions failed feasibility checks and were stopped. This is not a completed P6.7 user journey.

Base: `e60218d50d17aa47223e5f12bbfabb1f5c94d911`, existing worktree branch `codex/p67`.

## Ownership and dependencies

Read: AGENTS.md; STANDARDS.md sections 2, 3, 13 and 15 only; profile preference deletion; gateway capabilities/permission reply contract; existing background/lifecycle bridges; maker advice; KitRedact; architecture gate.

Write: `lib/state/in_flow_consent.dart`, `lib/state/repeated_permission_consent.dart`, their two `test/*_test.dart` files, this QA directory and commit-message fallback. No UI, Kotlin, connection, main, domain gateway or product repository edits.

Three independent execution responsibilities: root owns in-flow consent/persistence and final review; worker owns repeated-permission invitation/persistence; worker checked notification routing and stopped at the ownership blocker. Only root attempts tests. No new external adapters or credential use.

Dependencies: existing `SharedPreferences`, `ProfileStore` suffix deletion, `PhoneMaker`/`keepAliveSteps`, `ServerCapabilities.persistentPermissionGrants`, `PermissionGateway` replies and KitRedact. The UI/composition-root integration is a later unit, per this task's scope.

## UI hook-up

### In-flow consent

Import `state/in_flow_consent.dart`. Await `InFlowConsent.load(prefs, profileId: savedProfile.id)` once per saved server; retain one owner, and dispose/drop it before deleting that profile. Profile IDs are opaque identifiers, never URLs or credentials. Callers must serialize deletion after pending consent writes so stale owners cannot recreate deleted records.

- At the phone server's first start (not remote connect, app startup or Settings entry), read `AppLifecycleBridge.keepAliveInfo()`, derive `PhoneMaker.of(manufacturer, brand)`, and call `phoneServerFirstStart(batteryAlreadyExempt: ..., maker: ...)`. Present the returned kinds sequentially, battery first. The entire batch is reserved durably before returning. An interrupted batch remains unfinished in Settings and is not automatically asked again.
- At the action offering **Tell me when the agent needs me**, call `requestNeedsYouPreset()`. Present only when it returns true. It does not change global preferences, enable monitoring or imply Android notification permission.
- On explicit accept/deny/back, await `answer(kind, allow: ...)`. Only after a successful accepted answer invoke the existing platform operation: battery exemption through `BackgroundLiveController.requestBatteryOptimizationExemption()`, maker advice through `AppLifecycleBridge.openKeepAliveSetting(KeepAliveSetting.autostart)`. Opening a settings page is not proof that a grant exists; refresh available OS status on return. Native auto-start grant state is not readable today.
- The notification UI owner must separately apply the desired per-server notification behavior and request/check Android notification permission using the existing background flow. Do not blindly set `NotificationPreferences.requests` or `finishedRuns`: those are app-wide. Treat the stored choice as per-server intent, not a permission grant or proof that notifications are working.
- Rebuild Settings from `row(kind).choice` and `.explanation`. Localize explanation selectors: `batteryMayStopServer` → declining may let Android stop the phone server; `makerMayPreventRestart` → declining may prevent automatic restart; `needsYouAlertsOff` → needs-you alerts were declined. `unfinished` means the earlier flow was interrupted; `acceptedCheckSystemSettings` requires OS status, not an enabled badge. No English display copy is hard-coded or persisted by the service.
- An explicit Settings action can use `changeFromSettings(kind, allow: ...)`. It does not reset automatic-prompt markers. Turning off any already-enabled native/background behavior is the caller's responsibility.
- Storage errors throw a fixed, content-free `StateError`. After a failed write, `storageAvailable` is false: disable consent actions and show a recoverable storage error. Never proceed with the platform action after an error. No prompt is returned until its claim was saved.

Storage is one versioned, KitRedact-checked JSON record at `oc.inFlowConsent.<profileId>`, containing enum choices and the first-start marker only. No migration is needed for old installations. The existing `ProfileStore.removeScopedPreferences` sweep deletes it. No shared-blob deletion hook or forbidden connection edit is required.

### Third identical permission ask

Import `state/repeated_permission_consent.dart`. Keep one `RepeatedPermissionConsent(prefs, profileId: ...)` per saved server. Build `PermissionConsentScope` with the current request's `sessionID`, `permission`, `patterns` and **both** the proposed standing-grant `always` patterns as `alwaysPatterns`; never use a rendered/redacted summary for identity.

Call `observe(scope: ..., requestID: ..., supportsPersistentGrants: gateway.capabilities.persistentPermissionGrants)` only for a real pending ask. Only `offerAlwaysAllow == true` authorizes showing the one-time invitation; the service never sends or authorizes an automatic approval. Distinct request IDs count, replayed IDs do not. Invitation history is per server and exact permission/proposed grant scope; request dedupe also includes session identity. Pattern order does not create a new scope.

On acceptance, the UI must show the current grant's actual scope/duration and explicitly use the existing domain/controller permission-answer flow with `always`. Only after that reply succeeds call `recordDecision(scope, accepted: true)`; for declining the invitation call it with false and continue the normal once/reject flow. History never determines or broadens the grant's scope. A failed gateway reply must never be recorded as an accepted grant.

`status(scope)` reads Settings metadata without consuming an invitation. `explainDeclinedInSettings` means continue asking for this permission each time. `decision == offered` can explain an interrupted offer; accepting history alone does not prove a server-side saved grant exists or has survived revocation. `storageAvailable == false` suppresses the invitation, while ordinary approval/rejection remains available.

Only SHA-256 identities and decision metadata are persisted, through KitRedact, under `oc.permissionConsent.<profileId>`. No commands, file paths, permission content or credentials are stored. The existing profile suffix sweep deletes the record. The bounded 256-scope history fails closed at capacity and does not evict old invitations to re-prompt.

### Blocked hook-ups / feasibility

1. **Downloads over 50 MB on mobile data:** `BackgroundLiveController.monitorWifiAvailable()` (`lib/background/live_background.dart:284`) returns only nullable Wi-Fi status. The native `monitorNetworkPolicy` response (`android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/MainActivity.kt:237`) contains only `wifi`. False also covers Ethernet/offline/other transport; unknown cannot safely be called mobile data. No mobile-download gate or substitute adapter was built. Coordinator needs an explicit current mobile/metered/unknown transport contract and trustworthy byte size before this consent can be implemented. Until then the download consent portion remains unavailable; do not infer permission from Wi-Fi false.
2. **NotificationRouter dedupe/cancel-on-answer:** existing `showCodingAlert`/`dismissCodingAlert` APIs are callable, but declaring `NotificationRouter` activates the all-posts-through-router gate in `test/architecture_boundaries_test.dart:797` and `:920`. Existing direct posts in forbidden `lib/state/connection.dart` (281, 373, 917, 1384, 1426) and the test-notification action in forbidden `lib/ui/screens/settings/notifications_settings_screen.dart:85` must migrate together. No alternate-named bypass or partial router was added. Coordinator must own that migration, dedupe by profile/session/kind/request, derive stable cancellation keys after restart, serialize cancellation behind in-flight posts, and cancel only after the domain confirms an answer; retain answer suppression if cancellation fails.

Both blockers are recorded once here. No prohibited files were changed. No existing notification path was rerouted.

## Behavior checks and evidence

Focused acceptance tests:

- `test/in_flow_consent_test.dart`: first-start dedupe under concurrent callers and restart; battery/maker denial explanations; exemption/maker filtering; notification preset interruption/denial/settings revision; profile isolation/deletion; corrupt/refused storage.
- `test/repeated_permission_consent_test.dart`: third distinct ask, concurrent replay, restart, scope/profile/session behavior, remembered denial, capability gate, no plaintext scope storage, failed persistence.

Pinned commands attempted:

```sh
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/in_flow_consent_test.dart
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter test --concurrency=1 test/repeated_permission_consent_test.dart
~/.shorebird/bin/cache/flutter/91f8bd75076e9c740aa13cf67eb9ec1a093f68f5/bin/flutter analyze
```

All stop before Flutter runs because the sandbox denies writes to the pinned engine cache (`engine.stamp.tmp.*`, `engine.realm`). See [in-flow-test.txt](in-flow-test.txt), [repeated-permission-test.txt](repeated-permission-test.txt), [analyze.txt](analyze.txt). No test pass or clean analyzer result is claimed. This worktree also starts without `.dart_tool/package_config.json`; the verifier may need `flutter pub get` first.

The requested pinned `bin/dart format --language-version=3.10` hits the same launcher failure. The **same pinned SDK's** `bin/cache/dart-sdk/bin/dart format --language-version=3.10` formats the four files successfully, then reports a read-only telemetry write under `~/.dart-tool`; see [format.txt](format.txt). No different SDK was substituted.

Source review checked call signatures, the profile deletion contract, forbidden-path scope and diff whitespace. Full suite, UI/goldens, native permissions, downloads, notification delivery and end-to-end flows are not verified. The verifier should run only the two affected files serially, then the whole-tree analyzer; full integration remains the coordinator's gate.

## Delivery state

Implemented: partial state APIs and focused tests. Enabled: no UI/native hook-up. Verified: source review/format only; tests/analyzer blocked by sandbox. Committed: **no** — `git add` failed because `/home/owner/Storage/Code/oc_app/.git/worktrees/oc_app-codex-p67/index.lock` is on a read-only filesystem. Changes remain in this working tree; [COMMIT_MSG.txt](../../../COMMIT_MSG.txt) contains the requested subject/body and attribution trailers. Deployed/released: no. No push requested or attempted.

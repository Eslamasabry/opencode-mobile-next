# BD10 native notification localization — 2026-10-08

Implemented on `sol/bd-native-l10n` in 3541d1c7e. Before final verification, merged current `feat/genui-fe` c99437e9e (including BB runtime ccd4c900) with `git merge`, without rebase. The resulting `BuiltinServerService.kt` diff contains exactly five string lookups. `BuiltinLinux.kt` and sign-in Kotlin have no BD10 changes.

Native-authored coding-alert titles/bodies/actions, live notification fallback/counters/chips, all notification channel copy, voice setup copy, phone-server notification, setup channel fallbacks, and widget fallback/time labels now use English/Arabic Android resources. Existing tile and pinned-shortcut lookups also follow the saved app language. Agent titles use positional templates rather than an English `OpenCode` prefix. Intent data, channel identifiers, permission/authentication requirements, supplied captions, and runtime control paths are preserved.

Shared Arabic wording matches `app_ar.arb` for Allow once, Deny, AI Team, Working, Stop and phone setup channel. Voice copy preserves its previous Arabic wording. Arabic count resources contain all six plural categories; natural zero/one/two forms may omit numerals while retaining compatible argument types.

## Locale behavior and contract

[BD10 contract](../../design/BD10-contract.md): native rendering reads the acknowledged `flutter.oc.appLocale` from legacy `FlutterSharedPreferences` on every lookup. This storage contract was checked against shared_preferences_android2.4.27 and shared_preferences2.5.5. English/Arabic overrides take priority over the system language; other valid app languages use English resource fallback. System mode scans the preferred locale list in system preference order, so `[fr, ar, en]` selects Arabic and `[fr, ja, ar]` selects the app's Japanese choice with English native fallback. Unsupported/invalid saved values follow system mode. Copies of Configuration keep the original activity/service configuration unchanged. No credentials or other preferences are read, logged or copied.

The language save calls a parameterless native refresh after storage acknowledgement. This bypasses unchanged live-status suppression, updates native channel labels without changing the person's importance/sound choices, and rebuilds active live/phone fixed copy while preserving task/server titles and notification action intents. It does not restart or stop any service. Event/result notifications retain their posted language until their normal repost/dismissal. Dart-authored setup/server/team-progress captions remain the caller's localized text; the contract identifies the chosen-language injection point.

## Failing controls

- Before resource/Background changes: both tests in `native_notification_copy_test.dart` failed against original Kotlin/XML. After the fix: both pass.
- Before service lookup changes: the new Arabic service-copy scenario compiled and failed at the rendered foreground-title assertion. It passes after the five server lookups and setup fallback.
- Before locale-refresh/preferred-locale fixes: saved-language-refresh expected `['ar', null]` but observed `[]`; system `[fr, ar, en]` rendered English and failed the Arabic assertion. Both pass after correction.
- [Mutants](mutants.json): removing the saved-choice branch makes the Arabic-override JVM scenario fail behaviorally; replacing native refresh with a no-op makes the phone-fallback JVM scenario fail. Restoring the merged-head server or widget files also makes their localized-copy regression fail. Sources were restored in `finally` before final verification. No sleeps in these new tests.

## Verification

All Flutter commands use the pinned Shorebird Flutter3.47.1 and `tool/qa/machine_lock.sh`; modified Dart formatted with language3.10.

- Focused final run: **80/80 PASS**, serial across `native_notification_copy_test.dart`, `native_strings_test.dart`, `native_locale_refresh_test.dart`, `phone_agent_service_native_test.dart`, `app_locale_test.dart`, `background_live_test.dart`, `live_status_test.dart`.
- Named release blocker: **1/1 PASS**, Android background coding alerts are private and actionable.
- Native JVM coverage: real locale helper + committed XML through Android Context/Resources doubles; real refresh coordinator through NotificationManager/Notification doubles; real merged Setup/BuiltinServer services through existing service doubles. Covers opposite app/system languages, acknowledged changes/removal, process reopen, invalid/refused preferences, ordered system languages, unsupported native languages, positional formatting/all plural counts, channel policy preservation, supplied-title preservation and action-intent preservation. Peer live/widget entrypoints are captured doubles; this is not a device-render claim.
- Full Flutter analyzer: **clean**, 45.4s.
- Actual Android37 API compilation of NativeStrings/NativeNotificationLocale and `aapt2` compile/link of both resource files: **PASS**. No signed APK or Gradle daemon created.
- Full pinned detekt gate reports **315 findings, exit2**, also present on a temporary **untouched c99437e9e** Android snapshot. [Fingerprint comparison](detekt-comparison.json): **0 introduced, 0 removed**. Checked complete finding multisets by file/rule/message, independently of line shifts; no baseline additions, ignores or suppressions. The inherited global gate remains a coordinator/BB integration issue, not a claimed green run.
- Diff and G33 commit policy/staged-format checks: PASS at the local commit boundary.

No full suite, emulator session, APK installation, rendered RTL/device qualification, push, publication or release was performed for BD10. BD7 saved-report device rerun remains queued for coordinator APK2197.

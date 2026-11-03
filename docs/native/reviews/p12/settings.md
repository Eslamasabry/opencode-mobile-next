Verdict: FAIL — 0 BLOCKER, 7 MAJOR, 1 MINOR.

1. **MAJOR — Notification switches do not suppress alerts.** [PlatformWiring.kt:456](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:456)  
   Turn off “Finished work” or “Questions,” then background the app with a connected server. Completion and request alerts still post: the dispatcher never reads either saved switch. Flutter gates these paths on `notificationPreferences.finishedRuns` and `.requests`. **Catalog:** no specific row; plan 07 I05.

2. **MAJOR — Choosing a surface style has no visual effect.** [PreferencesChoices.kt:86](/home/eslam/Storage2/wt/codex-review-p12/native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PreferencesChoices.kt:86)  
   Select Paper, Brutal, Soft or Signal. The selection persists, but the root appearance mapping drops `surfaceStyle`; `KitAppearance` has no style field or corresponding renderer wiring. Screens retain their existing appearance after selection and restart. Flutter applies the selected effects above its navigator. **Catalog:** B-KIT-010, B-KIT-037.

3. **MAJOR — Migrated language overrides masquerade as System and cannot be cleared directly.** [PreferencesChoices.kt:110](/home/eslam/Storage2/wt/codex-review-p12/native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/PreferencesChoices.kt:110)  
   Start with a saved `es`, `ja`, `pt`, `ru` or `zh` locale. The engine retains that override, but Settings maps it to `AppLanguage.System`. Tapping “Use system language” then does nothing because `onLanguage` considers it already selected. The override survives restart; those five Flutter choices are also unavailable in the picker. **Catalog:** B-L10N-001, B-L10N-008.

4. **MAJOR — Quiet hours is a dead row and misreports migrated settings.** [VoiceNotificationsPages.kt:129](/home/eslam/Storage2/wt/codex-review-p12/native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/ui/VoiceNotificationsPages.kt:129)  
   Open Notifications with saved quiet hours such as 22:00–08:00. The adapter never reads their keys, so the row displays Off. Tapping it dispatches `SettingsOutside.QuietHours`, which the Android host silently ignores. Users cannot inspect or change the setting. **Catalog:** B-MIGR-005; plan 07 I05.

5. **MAJOR — Transcript display switches only change stored values.** [SettingsPresenter.kt:156](/home/eslam/Storage2/wt/codex-review-p12/native/feature/settings/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/settings/presentation/SettingsPresenter.kt:156)  
   Enable “Show reasoning” or “Show timestamps,” then return to a conversation or reopen it. Neither preference reaches the native chat/transcript rendering path; the switches acknowledge and persist changes without changing the transcript. Flutter consumes both preferences in its chat UI. **Catalog:** no specific row; plan 07 I10.

6. **MAJOR — Report a problem reads an unpopulated diagnostics instance.** [SettingsWiring.kt:418](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt:418)  
   Encounter a handled connection or send failure, then open Report a problem. The bound `Diagnostics` has no production error producer wired to it; Settings is its only consumer. Consequently, the report says “No errors kept on this phone” despite the failure. Flutter feeds handled errors into its report evidence. **Catalog:** no specific capture row; plan 07 I18.

7. **MAJOR — Normal notifications ignore the chosen app language.** [PlatformWiring.kt:204](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:204)  
   On an English phone, save Arabic in Settings and receive a coding alert. Normal alerts use `NotifyWords(application) { null }`, so they retain the application context’s system language. The settings test notification uses a different, preference-aware instance, masking this discrepancy. **Catalog:** B-L10N-002, B-L10N-013.

8. **MINOR — Notification permission state remains stale after returning from Android Settings.** [SettingsWiring.kt:303](/home/eslam/Storage2/wt/codex-review-p12/native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt:303)  
   Change notification permission externally and return. The singleton refreshes only during initialization, preference writes or Send test—not on resume or page entry. The blocked notice therefore remains wrong, including a missing fix action after notifications become blocked. Flutter refreshes on resume. **Catalog:** no specific row; plan 07 I05.

Static, read-only review. No files changed; no builds or tests run.
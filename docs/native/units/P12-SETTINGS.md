# P12-SETTINGS — Settings and first run in the Android app

Branch `native/p12-settings-2026-11-03` (based on `native/m1-2026-11-03` @ `8a4eee69`). Parity push 2026-11-03.

## 0. Read this first: nothing in this unit was compiled or run

The cloud session that wrote this unit could not run Gradle at all. Its network policy refuses `dl.google.com`, which
serves both the Android SDK (`sdkmanager`) and Google's Maven repository (`google()`: AGP 9.3.3, AndroidX). Plugin
resolution fails before any module configures, so no `desktopTest`, `:quality:test` or `:androidApp:assemblePreviewDebug`
was possible. Maven Central and the Gradle plugin portal were reachable.

Every line was written against the sources on the branch (signatures read, not guessed), and the tests were written
from the catalog rows, but **implemented ≠ verified**: the integrator must run the commands in §6 before merging. The
places most likely to need a touch are listed in §7.

## 1. Journeys now wired (implemented, not yet verified on a build)

| Journey | How to reach it |
|---|---|
| Settings hub with its groups, search, Back walking the pages (predictive Back via `KitBackGuard`) | Conversations header → gear (`SettingsGearDoor` replaces `PreviewSettingsDoor`, which opened Servers) |
| Appearance: light/dark/Saver, colour packs (most used + "See all 31"), the five Styles, Motion Full/Calm/Off, **Text size** (new) — applied live at the app root | Settings › This app › Appearance |
| Language: Use system language / English / العربية, each in its own name; the Activity recreates in the new locale and direction and stays on the Language page | Settings › This app › Language (now a page here, was an outside door) |
| Notifications: Android's on/off with the fix row, finished work / questions / Wi-Fi only (Flutter keys `oc.notify.finishedRuns`, `oc.notify.requests`, `oc.notify.wifiOnly`), a real test notification on the coding channel | Settings › This app › Notifications |
| Privacy and What we collect (real `:engine:analytics` engine, `NoOpSink`, file store `files/native/analytics/queue.json`) | Settings › This app › Privacy |
| About: version+build from `AppBuildInfo`, What's new / Star on GitHub (repo, app-authored), Share the app (share sheet), Privacy policy (repo document) | Settings › Help › About |
| Report a problem: preview of a generated report (versions, Android, language, text size, motion, server counts, redacted errors from `:engine:diagnostics`), Copy, Share report; failure says so with Try again | Settings › Help › Report a problem |
| First run: a device with nothing saved starts on the welcome → "Connect to my computer" → Servers → the first saved server that answers lands on Conversations and ends first run; Settings is the welcome's second way | Cold start with no saved server |
| Saved servers from Settings | Settings › Server › Servers |

### Live application of choices

- `AppPreferences` (androidApp) builds the ONE `:engine:settings` `Preferences` facade after `PreviewStores.warm()`
  and mirrors its acknowledged snapshot. `PreferenceMapping.appearance` turns it into `KitAppearance`;
  `MainActivity` collects it and passes it to `App(…, appearance, localeTag)` → `KitTheme`. Mode, pack, accent,
  text size, motion, density and frost all reach the theme at once.
- Language: Compose resources read the platform locale, so `AppLocale` (androidApp) sets `Locale.setDefault`, wraps
  the base context in `attachBaseContext`, and `recreate()`s when the stored tag changes. The saved back stack and the
  ViewModels survive. The first Activity of a process reads `flutter.oc.appLocale` read-only from
  `FlutterSharedPreferences`, so an Arabic start does not flash English. `KitTheme(localeTag)` sets RTL.

## 2. What changed, by write set

- `ui/routes`: new files `SettingsRoute.kt` (`SettingsRoute.Home`) and `OnboardingRoute.kt` (`OnboardingRoute.Welcome`, Root motion).
- `feature/settings`:
  - depends on `:engine:settings`;
  - `PersonalPrefs` gains `textSize` and `language` (`AppLanguage` System/English/Arabic); `PersonalChange.TextSize`/`Language`;
  - `PreferencesChoices` + `PreferenceMapping`: the `Preferences` adapter (the follow-up the SETTINGS-UI report named) and the theme mapping;
  - `SettingsPage.Language`, `SettingsPage.ReportProblem`; `ReportUi`; notes `ReportCopied`, `ShareFailed`;
  - `DiagnosticsExport` port and `DiagnosticsReport` (pure text builder, newest 20 errors, 300-char lines);
  - `SettingsFacts.diagnostics` and `SettingsFacts.opens`: a row leading to a place the host cannot open is absent,
    never dead (02 §3.7). Null keeps the old behaviour (every existing test);
  - `AboutFacts.notices` hides the notices row where no notices page exists;
  - `SettingsHost` (the port bundle) and `SettingsViewModel` now Metro-contributed (`@ViewModelKey`), with `back()`/`exit()`;
  - `SettingsEntries` installs `SettingsRoute.Home`;
  - UI: `LanguageReportPages.kt`, Text size section at the end of Appearance, words for the new notes/sizes.
- `feature/onboarding`: `FirstRunFlow` (RootDecision + FirstRun + navigation), `WelcomeViewModel`, `WelcomeScreen`
  (kit `KitStateView`, app name, "Connect to my computer", "Settings"), `OnboardingEntries`.
- `engine/settings`: unchanged (it already compiled against the flat core contract on this base).

### Edits outside the write set (smallest possible; please review)

| File | Edit | Why |
|---|---|---|
| `ui/shell/.../App.kt` | `App()` takes `appearance: KitAppearance = Default` and `localeTag: String? = null` and passes them to `KitTheme` | The only way a stored choice reaches `KitTheme`; desktop keeps calling it unchanged. Shell owner: please take it over or move it into `ShellDependencies`. |
| `androidApp/.../MainActivity.kt` | reads `SettingsAppGraph` (contributed interface), starts first run, collects appearance, recreates on a language change, wraps `attachBaseContext` | root application of the choices |
| `androidApp/build.gradle.kts` | adds `:feature:settings`, `:feature:onboarding`, `:engine:settings`, `:engine:analytics`, `:engine:diagnostics`, `:android:notifications` | module list |
| `androidApp/src/main/AndroidManifest.xml` | `POST_NOTIFICATIONS` | Android 13+ cannot notify (or offer the toggle) without it; NOTIFY will need it too |
| `androidApp/.../graph/SettingsWiring.kt` (new) | every Android binding of this unit | the one wiring file |

## 3. Catalog rows covered by new tests

`feature/settings` commonTest `PreferencesChoicesTest` (real `Preferences` over an in-memory Flutter preference file):
B-KIT-061 (defaults), B-KIT-059 (text size stored as `oc.textSize`, reaches the theme), B-L10N-001 (Arabic stored as
`oc.appLocale`, system removes the key), B-STATE-005 (refused write keeps the value; unopened facade refuses instead of
hanging), B-KIT-006 (Motion under its Flutter key), B-KIT-040 (new style clears Fine-tune, keeps other effects),
B-STATE-003 (every pack round-trips by its Dart name), B-KIT-010 (stored choices → `KitAppearance`).

`feature/settings` commonTest `SettingsParityTest` (real presenter): B-KIT-059 ×2, B-L10N-001, B-SEC-060, B-STATE-078,
02 §3.7 (absent rows), B-SEC-041 ×2 (report content, ordering, bounds).

`feature/settings` desktopTest `SettingsParityUiTest` (by visible text): B-L10N-001 (Language page, tap العربية),
B-KIT-059 (Text size, Large), B-SEC-060 (Report a problem, Copy, "Report copied"), 02 §3.7 (no dead Agent group).

`feature/onboarding` commonTest `FirstRunFlowTest`: B-NAV-017 ×2 (welcome only for a new device), A03 ×3 (welcome →
servers → first answer lands on Conversations; no yank from elsewhere; Settings as the second way).

Existing tests updated: `SettingsPresenterTest` (Language is now a page here, so the outside-door example uses Servers);
`FakeChoices` handles the two new changes.

Rows still missing in this area: B-L10N-002/013 (notification text refreshed after a language save — NOTIFY's
`ChannelRegistry.relocalize` should be called on `storedLocaleTag`), B-L10N-017 (coverage percentages on the language
list), B-NAV-059/070/073 (search deep link highlight), B-SEC-042..045 (crash capture; no crash store is bound),
B-ANDR-026 (the one notification question after landing — `FirstRun.notifyAskPending()` becomes true but nothing asks
yet), B-NAV-001 (about notice), B-STATE-123..129 (scope edit tokens; `SettingsScope` is not used by these pages).

## 4. Deviations and honest limits

- The Agent group (model and mode, providers, usage, tools, agents), Default shell, Permissions, Guide, Tips, This
  phone, Background, Voice, Updates and the notices page have no native page yet, so their rows are absent (the hub
  shows four groups while none of Agent's rows can open). When a unit lands one of these, add its place to `OPENS` and
  the `when` in `AndroidSettingsHost.openers` (one line each), or drop the platform flag override in `shipped`.
- Show reasoning / Show timestamps are stored (`oc.transcript.reasoningExpanded`, `oc.transcript.timestampsVisible`), but the chat
  transcript has to read them; that is the chat unit's half.
- Notification toggles are stored under the Flutter keys; the native app does not post coding alerts yet (NOTIFY).
  The test notification is real.
- Privacy policy and release notes open the repository on GitHub (app-authored URLs, opened with `ACTION_VIEW`); the
  Flutter app shows the policy in-app.
- Appearance before warm-up: the first frame uses the defaults (Dark/Graphite) and switches once the preference file
  is read (usually before the first frame is visible). The language does not flash (read synchronously).
- First run decides after `PreviewStores.warm()`, so a new device sees the Conversations root for a moment before the
  welcome replaces it. The opening gate (`OpeningGateScreen`, A01) is still not drawn by the shell; it would hide this.
- Report a problem text is fixed English (it is read by maintainers, as in Flutter). No typed description field yet.

## 5. Integrator checklist

1. `./gradlew :engine:settings:desktopTest :feature:settings:desktopTest :feature:onboarding:desktopTest`
2. `./gradlew :quality:test` (Konsist K1/K2/K3/K11/K19 and module rules were checked by reading, not run)
3. `./gradlew :androidApp:assemblePreviewDebug` (Metro validates the graph here: `SettingsHost`, `AnalyticsEngine`,
   `Diagnostics`, `AppPreferences`, `FirstRunStarter`, and the `replaces` on `SettingsGearDoor`)
4. Goldens: the Appearance page gained a Text size section at its end. If `SettingsGoldensTest` `appearance-*.png`
   differ, look at the images and re-record with `-Poc.recordGoldens=true` only if the difference is that section.
5. Emulator: no saved server → welcome → add server → Conversations; gear → Settings › This app › Language › العربية
   (page stays, RTL, Arabic words) → back to System; Appearance › Large text and Calm motion visible on Conversations;
   Help › Report a problem › Share report.

## 6. Commands that should have been run (and were not, see §0)

```
./gradlew :feature:settings:desktopTest :feature:onboarding:desktopTest :engine:settings:desktopTest
./gradlew :quality:test
./gradlew :androidApp:assemblePreviewDebug
```

## 7. Where a first compile is most likely to complain

- `SettingsWiring.kt`: Metro `replaces = [ClosedSettingsDoor::class, PreviewSettingsDoor::class]`; the bound
  suspend reference `prefs::preferences` passed as `suspend () -> Preferences?`; `NotifyR` resource names
  (`notify_ic_status`, `notify_test_title`, `notify_test_body` exist in `:android:notifications`).
- `PreferencesChoicesTest`: `InMemoryPrefsBackend.commit` override signature and `PrefsKeyValueStore` constructor
  (copied from `:engine:settings` `PrefsTestSupport`).
- `KitStateView` inside a scrolling settings column on the Report page (Loading/Error states).

## Verified on the VPS (2026-11-02, branch `p12v/settings`)

Base: `origin/native/p12-settings-2026-11-03` @ `0eac8f3d3` (already on top of `origin/native/m1-2026-11-03` @ `8a4eee69c`; no
rebase needed). JDK 21, Android SDK, real Gradle (`-Dorg.gradle.workers.max=3 -Xmx3g`, `nice -n 5`).

| Command | Result |
|---|---|
| `:engine:settings:desktopTest` | 120 tests, 0 failures |
| `:feature:settings:desktopTest` (incl. `PreferencesChoicesTest`, `SettingsParityTest`, `SettingsParityUiTest`, `SettingsGoldensTest`) | 66 tests, 0 failures; goldens matched without re-recording |
| `:feature:onboarding:desktopTest` (incl. `FirstRunFlowTest`) | 17 tests, 0 failures |
| `:ui:routes:desktopTest` | 1 test, 0 failures |
| `:ui:shell:desktopTest` / `:ui:shell:testAndroidHostTest` | 22 / 14 tests, 0 failures |
| `:androidApp:assemblePreviewDebug` | first run failed to compile, green after fix 1 (Metro graph validated) |
| `:androidApp:testPreviewDebugUnitTest` | 11 tests, 0 failures |
| `:quality:test` | did not compile on the base (fix 2); then 48 tests, 2 failures, both outside this unit (below) |

Fixes:

1. `androidApp/build.gradle.kts` (this unit's module list edit): add `implementation(libs.compose.resources)`. `SettingsWiring.kt`
   calls `org.jetbrains.compose.resources.getString(Res.string.shareAppMessage, …)` from `:ui:words`, which keeps Compose
   resources as an `implementation` dependency, so the app could not see `getString`/`StringResource`. (The `R as NotifyR`
   names, the suspend reference and the `replaces` list the report worried about all compiled.)
2. Shared file, outside the write set: `quality/.../ChatWorldReviewTest.kt` passes `filePickers = emptySet()` to
   `BasicComposerViewModel`. The m1 composer WIP added that constructor parameter without updating this test, so
   `:quality:test` could not compile at all. The test checks durable delivery admission, which has nothing to do with file
   pickers, so its meaning is unchanged.

Still failing in `:quality:test`, not caused by this unit (none of these files or their modules are in its diff):

- `KonsistRulesTest` K1: `feature/chat/.../composer/BasicComposerHost.kt` (m1 composer WIP) imports a non-kit framework
  part. The check is static, so it fails on the m1 base in the same way. Chat/composer owner to fix.
- `p3/TranscriptStreamUiTest` "6,000-word reply streams at the live edge": `UncompletedCoroutinesError` after the 1 min
  `runTest` timeout, both in the full run and when run alone, with load average ~20 on the 8-CPU shared VPS. It uses
  only chat/transcript/kit-chat, none of which this unit touches. Most likely load-bound; rerun on an idle machine.

Not done here: §5.5 (emulator journeys) — no emulator on the VPS.

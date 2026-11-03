VERIFIED: PENDING

# P12-FIX-SETTINGS-PREVIEWQR — Codex second-eye findings on SETTINGS and PREVIEW-QR

Branch `claude/native-parity-p12-r55qds`, based on `native/p12-qa-wave4-2026-11-03` @ `e61b9d22`. Parity push 2026-11-03.
Reviews read: `docs/native/reviews/p12/settings.md` (8 findings) and `previewqr.md` (9 findings). Each finding was
checked against the native code and the Flutter app (lib/, catalog rows) before it was touched.

Finish line: every real finding in these two units is fixed in the Android app with a test, or named with the exact
reason it is not fixed here. Non-goal: work in other units' modules (chat, transcript, kit, platform's alert dispatcher).

## 1. Findings

### Settings (`settings.md`)

| # | Finding | Verdict | What changed | Tests |
|---|---|---|---|---|
| 1 | Notification switches do not suppress alerts | **Real — left to FEATURE-FIX-PLATFORM.** It is the same defect as `platform.md` #4 (`PlatformWiring.kt` `alerts()` hard-codes `serverAlertsOn`/`quietHours`/`wifiOnly`). Fixing it twice in one function would conflict. The values it needs are now all stored and readable: `NotificationKeys.FINISHED/REQUESTS/WIFI_ONLY/QUIET_START/QUIET_END` (`:feature:settings`), and `NotificationKeys.quiet(start, end)` decodes quiet hours. | — | — |
| 2 | Choosing a surface style has no visual effect | **Real.** `KitAppearance` has no style field and nothing in `:ui:kit`/`:ui:design` draws Paper/Brutal/Soft/Signal. The renderer belongs to the kit owner (shared module, "propose, don't edit"). Until it exists, the choice is **not offered** where the app does not draw it (02 §3.7: a choice must change something): `SettingsFacts.surfaceStyles` (default true), set false by the Android host; the stored value is kept untouched. | `SettingsStructure.kt`, `SettingsUi.kt` (`AppearanceUi.styles`), `SettingsPresenter.kt`, `AppearancePage.kt`, `SettingsWiring.kt` | `P12FixSettingsTest` "02 3_7 Style is not offered…", `P12FixSettingsUiTest` "02 3_7 Appearance has no Style choice…" |
| 3 | Migrated es/ja/pt/ru/zh read as System and cannot be cleared | **Fixed.** `AppLanguage` now lists every shipped language in Flutter's picker order (`AppLocaleStore.pickerLanguages`: en, ar, es, ja, pt, ru, zh); the mapping reads each stored locale as itself, so "Use system language" clears it. Words already existed in `:ui:words` (`e7LocaleUiSpanish`…). | `PersonalPrefs.kt`, `PreferencesChoices.kt`, `LanguageReportPages.kt` | `P12FixSettingsTest` B-L10N-008, B-L10N-001 (Spanish cleared); `P12FixSettingsUiTest` B-L10N-008 (all eight names on the page, Spanish selected, tap System) |
| 4 | Quiet hours is a dead row and misreports migrated values | **Fixed.** Flutter's shape: a "Quiet hours" switch (on = 22:00–08:00), and when on, "Quiet hours start" / "Quiet hours end" rows, each opening a page of times (every half hour plus the saved time; the kit has no time picker, so a kit check-mark list), "quiet all day" note when equal. Read/written under the Flutter keys `oc.notify.quietStart`/`quietEnd` (Dart int → `putLong`), cleared together when off. `SettingsOutside.QuietHours` is no longer used. | `SettingsUi.kt` (`NotificationChange.Quiet`, `QuietHours.Default/choices/allDay`), `SettingsPages.kt` (`SettingsPage.QuietTime`), `SettingsActions.kt`, `SettingsPresenter.kt`, `VoiceNotificationsPages.kt`, `SettingsScreen.kt`, `SettingsPorts.kt` (`NotificationKeys`), `SettingsWiring.kt` | `P12FixSettingsTest` B-MIGR-005 (keys decode), I05 (on → change start/end → all day → off), I05 (time list); `P12FixSettingsUiTest` I05 (switch, rows, 23:30, off) |
| 5 | Show reasoning / Show timestamps only change stored values | **Real — consumer is the chat/transcript unit** (nothing in `:feature:chat` or `:ui:kit-chat` reads `oc.transcript.*`; this unit may not touch chat). Until the transcript follows them, the two switches are **absent** (02 §3.7): `SettingsFacts.transcriptDisplay` (default true), false on Android. Values stay stored. Chat owner: read `DevicePreferences.reasoningExpanded/timestampsVisible` (via `AppPreferences.value`) and drop `transcriptDisplay = false` in `AndroidSettingsHost`. | `SettingsStructure.kt`, `SettingsWiring.kt` | `P12FixSettingsTest` I10 (rows and search) |
| 6 | Report a problem reads an unpopulated `Diagnostics` | **Fixed.** Two producers: (a) the redacting log root now has a delegate, `DiagnosticsLogSink`, that turns every Warn/Error log line (already redacted) into a handled error; (b) `DiagnosticsFeed` watches every live connection's stream status and records a stop (by failure kind) and the first drop into Disconnected — never an address. Mapping is pure (`HandledErrors`), started with first run. | `HandledErrors.kt` (new), `SettingsWiring.kt`, `OcApplication.kt` (one line: the delegate) | `P12FixSettingsTest` I18 ×2 |
| 7 | Normal notifications ignore the chosen app language | **Fixed** (smallest edit in `PlatformWiring.kt`): `PlatformHub` takes `AppPreferences`; its `NotifyWords` reads `localeTag`, and channel names are relocalized on every acknowledged language value (`storedLocaleTag`, B-L10N-002). | `PlatformWiring.kt` (3 lines) | compile + Metro graph (`assemblePreviewDebug`); no JVM test (needs a real NotificationManager) |
| 8 | Permission state stale after returning from Android settings | **Fixed.** `NotificationSettings.refresh()` re-reads `areNotificationsEnabled()`; the presenter calls it on opening Notifications and on `onResume()`, which `SettingsScreen` sends from `LifecycleResumeEffect`. | `SettingsPorts.kt`, `SettingsActions.kt`, `SettingsPresenter.kt`, `SettingsScreen.kt`, `SettingsWiring.kt` | `P12FixSettingsTest` I05 ×2 |

### Preview and QR (`previewqr.md`)

| # | Finding | Verdict | What changed | Tests |
|---|---|---|---|---|
| 1 | Pairing discards fallback addresses | **Fixed.** The fill keeps every candidate in order; the server form's ViewModel picks one before filling: first that works, else first that answered (401 counts), else the best-ordered guess (Flutter `selectPairingUrl`). Plain HTTP to another device is never probed before consent (B-SEC-124). Android binds `GatewayPairingProbe` over the protocols' own `GatewayFactory.probe` (health/version only, `credentials = null`), 8 s per address. | `PairingProbe.kt` (new), `PairingInbox.kt` (`candidates`, `at()`), `PairingPayload.kt` (`mayProbe`), `ServersViewModel.kt`, `PreviewQrWiring.kt` | `P12FixPairingTest` B-PROTO-068 ×3, B-SEC-124 |
| 2 | A late camera result can pop two screens | **Fixed.** The scanner ViewModel is finished after Close, a delivered code, or `onCleared`; later results are ignored. | `PairingScannerViewModel.kt` | `P12FixPairingTest` B-PROTO-069 ×2 |
| 3 | Cancelling scanning loses the server draft | **Fixed.** Name, address and username are saveable (`rememberTextFieldState`, kept by the shell's `rememberSaveableStateHolderNavEntryDecorator`); the password is held in the ViewModel's memory only (`holdPassword`/`heldPassword`, never saved state). The address watcher ignores its first value, so a restored draft keeps its HTTP consent; the "clear password after save" effect runs once per save. | `ServersScreen.kt`, `ServersUi.kt`, `ServersViewModel.kt` | `P12FixFormDraftTest` B-PROTO-069 (desktop UI), `P12FixPairingTest` (password not in state) |
| 4 | Mentioned previews lose path and query | **Fixed.** `PreviewPort` and `PreviewRoute.Page` carry `path` (path + query); the page loads `scheme://host:port/path?query`. Their `toString()` leaves the path out so a query never reaches a log. The old test "the ports route carries only ports and schemes" encoded the defect and was replaced by the B-CHAT-025 row. | `PreviewRoute.kt`, `PreviewUi.kt`, `PreviewViewModels.kt`, `PortsScreen.kt` | `PreviewRulesTest` B-CHAT-025, `PreviewViewModelTest` B-CHAT-025 |
| 5 | Tapped localhost links bypass preview routing | **Fixed** (smallest edit in `ChatFeelWiring.kt`, outside the write set — integrator please review): `checkLink` first asks `PreviewRoute.Page.claim(url, ref)`; a local dev link opens the in-app preview on the conversation on screen (top `ChatRoute`), path kept; anything else goes on to the policy as before. | `PreviewRoute.kt` (`Page.claim`), `ChatFeelWiring.kt` (6 lines) | `PreviewClaimTest` ×2 (`:ui:routes`) |
| 6 | Same-origin redirects break relative resources | **Fixed.** When the document fetch followed redirects within the origin, the old navigation ends with 204 (no history entry) and the WebView loads the final address itself, so relative URLs, the shown address and history are right; the final document still gets the CSP. | `KitWebPreview.kt`, `PreviewNavigation.kt` (new, pure) | `PreviewNavigationTest` (`:android:webview`, JVM) |
| 7 | Same-origin writes are blocked | **Fixed.** On the preview's own origin GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS pass (CONNECT/TRACE never); the boundary stays the destination (other origins 403). Non-GET requests go through the WebView natively (their bodies cannot be re-sent from `shouldInterceptRequest`), so a POST's response document does not get the injected CSP; subresources it asks for are still origin-checked. | `PreviewRules.kt`, `KitWebPreview.kt` (docs) | `PreviewRulesTest` B-SEC-058 (rewritten from "posts never leave") |
| 8 | Ports mentioned only in tool output disappear | **Real — needs the chat/transcript owner.** `TranscriptRow`s carry no tool output (`WorkLineRow` has counts only), and the collection lives in `:feature:chat` `ChatEntries.kt`, which this unit may not touch. Suggested fix: the transcript exposes tool output text (or its `SearchText` entries) and `ChatEntries` adds them to `texts`. | — | — |
| 9 | Scripted navigation can raise external-link prompts | **Fixed.** Leaving asks only for a main-frame navigation with `hasGesture()`; scripts, frames and redirects (the document fetch passes the request's gesture) are refused silently (plan 16 §1.8.5). | `KitWebPreview.kt`, `PreviewNavigation.kt` | `PreviewNavigationTest` |

No finding was false. Three are real but owned elsewhere (settings #1 → platform fix, settings #5 and previewqr #8 →
chat/transcript); settings #2 needs a kit renderer. For #2 and #5 the dead choices are hidden until then.

## 2. How to reach each fixed screen

- Language: Conversations gear → Settings › This app › Language (eight choices).
- Quiet hours: Settings › This app › Notifications › Quiet hours (switch) › Quiet hours start / end.
- Report a problem: Settings › Help › Report a problem (now lists logged warnings/errors and connection stops).
- Pairing: Settings › Server › Servers (or Conversations → Servers) → Scan code / Pairing code.
- Preview: Chat → bar action "Preview a running app" → a port (path kept); or tap a `http://localhost:PORT/...` link in a reply.

## 3. Tests and results

PENDING

## 4. Deviations and notes for the integrator

- Outside-the-write-set edits (each the smallest possible): `PlatformWiring.kt` (S7: one constructor parameter, the
  words' locale, one relocalize collector), `ChatFeelWiring.kt` (P5: one constructor parameter, the claim before the
  policy), `OcApplication.kt` (S6: the log root's delegate list). Expect trivial merge conflicts with the platform and
  chat-feel fix branches in those three files.
- `android/webview/build.gradle.kts` gains JVM test dependencies (kotlin-test-junit, junit4).
- Local only, not committed: `~/.gradle/init.d/mirror.gradle.kts` puts Google's Maven Central mirror first, because
  Maven Central answered HTTP 429 to this container.
- Quiet hours are written without `oc.notify.version`; a downgrade to the Flutter app reads them only once its own
  migration has run (Flutter reads shared rules only at version 1).
- The Language page has no "Partly translated (N %)" note yet (B-L10N-017): the native per-language coverage is not
  measured, and Flutter's percentages describe the Flutter catalogues.

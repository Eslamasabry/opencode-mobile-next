# Unit SETTINGS-UI: `:feature:settings` (report)

Date: 2026-11-01. Branch: `native/settings-2026-11-01`, created from `claude/native-settings-ui-ljdwms` at `997da66b`
(the M0 tip with the composer merge). Write set: `native/feature/settings/**` (new module) plus this report. Nothing
outside it was edited (`native/tool/check_write_set.sh --base 997da66b`: OK, 42 paths before this report).

Inputs read: `native/AGENTS.md`, `docs/native/units/INTEGRATE-W1.md` (the flat core contract; no `Failure.Net.*` is
used), `S3-COMPUI.md` (sibling feature, same harness), `S1-ANALYTICS.md`; plan 01 (binding), plan 15 rows S4-SETTINGS,
S4-APPEAR, S4-ANALYTICS (no row is named SETTINGS-UI; the brief's task text is the scope), plan 07 §2.2.2 rows 58–69,
§3.4, §4.2 (five groups), §5 I01–I20 and L04, §10.2; plan 06 §4.2 (packs), K4; plan 16 §5.8; plan 18 §2 and §6; plan 02
§1.1 rows 8 (`obvious-options-first`) and 23 (`settings-scope-model`), and `no-new-ui-levels`; the catalog rows below.

**Finish line used (one slice).** The Settings hub (search plus the five groups Server, Agent, Conversations, This
app, Help) and its group pages, and the Appearance (light or dark, the 31 colour packs, the five styles, Motion
Full/Calm/Off), Voice, Notifications, Updates, About, Privacy and "What we collect" pages, built from kit parts only,
driven by one presenter over ports; personal choices and the analytics opt-in write through those ports and show only
acknowledged values; tests by visible text and goldens green on the desktop JVM.
**Non-goals:** "By server" pages and the several-servers block (I03), the settings scope catalog, the language sheet,
Keep running, saved permissions, providers, tools, usage, the guided Appearance first visit, fine-tune, accent, text
size, glow, Manage space, "What was sent", Metro/Nav3 wiring, and every engine adapter except analytics.

**Shipping state:** implemented; verified locally (desktop tests, goldens, Android compile of this module); committed
and pushed to `native/settings-2026-11-01`. Not wired into the shell, not bound in Metro, not merged. No PR, CI run,
device or emulator run, or release. Plan 15's S4-SETTINGS finish line (every Settings page with search, E2E-22) is
**not** met; §7 lists what is missing.

---

## 1. What was built

Package `io.github.eslamasabry.oc.feature.settings`. Dependencies: the `oc.kmp.feature` set plus `:engine:analytics`.

| File | What it does |
|---|---|
| `presentation/SettingsPages.kt` | `SettingsGroupId` (the five groups, screen order); `SettingsPage` (Hub, Group, Appearance, Voice, Notifications, Updates, About, Privacy, WhatWeCollect), a stand-in for plan 07's `SettingsRoute` keys; `SettingsOutside` (25 places other features own: servers, providers, language sheet, keep running, privacy policy, …) and `SettingsOpeners`. |
| `presentation/SettingsStructure.kt` | `SettingsRowId` (22 rows, each with its group and stable `settings-*` test tag); `SettingsDoor` (open a page here, hand off to the host, or flip a switch); `SettingsFacts` (`PlatformCapabilities`, the connected server's line, Material You available, system animations off); `SettingsStructure.rows/groups/searchable`. Rows a platform or server cannot serve are **absent**, never greyed: This phone and Agents need `builtinLinux`; Background `backgroundService`; Notifications `notifications`; Voice `voice ‖ readAloud`; Updates `inAppUpdater ‖ desktopReleaseCheck`; Model and mode, Providers, Tools and Default shell need a server. A group with no rows has no door. Gates are capability flags, never the backend kind (rule 10). |
| `presentation/SettingsSearch.kt` | Search over the existing rows' words: every query word literal (inside or a prefix of a word) ranks first, then fuzzy (one edit or one neighbour swap, words of 4+ letters, also against a word's prefix); ties keep input order. Works on Arabic aliases. A port of the freeze tip's matcher rules, to be replaced by the shared `SearchMatcher` (S5-SEARCH). |
| `presentation/PersonalPrefs.kt` | `PersonalPrefs` (mode, pack, style, fine-tuned, motion, reasoning, timestamps; the engine's defaults: Dark, Graphite, Solid, Full) and `PersonalChange`. Types are `:ui:design`'s `AppearanceMode`, `PackId` (31 packs) and `MotionLevel`, plus a local `SurfaceStyle` (Solid, Soft, Brutal, Paper, Signal). |
| `presentation/SettingsPorts.kt` | `PersonalChoices` (acknowledged `StateFlow` + `suspend set(change): Outcome`), with the field-for-field mapping its `:engine:settings` adapter will do in the KDoc; `AnalyticsChoices` with the real adapter `EngineAnalyticsChoices(Analytics)`; `VoiceSettings`, `NotificationSettings`, `UpdateSettings` (the contracts the voice, notification and updater engines implement). |
| `presentation/SettingsUi.kt` | `SettingsUi` (back stack, hub, group, appearance, voice, notifications, updates, about, privacy, one `SettingsNote`) and the page states: `AppearanceUi` (listed packs, "See all" count, Saver hold, system motion off), `VoiceFacts`/`SpeechModel`, `NotificationFacts`/`QuietHours`/`BackgroundState`/`NotificationChange`, `UpdateFacts`/`UpdateState`/`UpdateFailure`, `AboutFacts`, `PrivacyUi` (the engine's `WhatWeCollect` + saving). No Compose, no copy (K2). |
| `presentation/SettingsActions.kt` | Every act on the pages, each naming its target. |
| `presentation/SettingsPresenter.kt` | Builds `SettingsUi` from the facts, the ports' acknowledged values and local navigation; hub → group → page and Back (Back at the hub first clears the search, plan 07 §3.4); rows open their page, hand off to `SettingsOpeners`, or flip in place; packs: the 8 most used plus the pack in use, "See all 31 themes" for the rest, Material You only with the platform's tones; a new style is one `Style` change (which clears fine-tune in the engine, B-KIT-040), the same style writes nothing; Saver holds style and motion; a refused write leaves the value and sets one note (B-STATE-005); the detailed-usage switch ignores taps while a save is in flight. |
| `presentation/SettingsViewModel.kt` | Thin `ViewModel` owning one presenter on `viewModelScope`. |
| `ui/SettingsScreen.kt` | `SettingsScreen(state, actions, modifier, onExit)`: the page on top of the stack inside `frame.KitScreen` (bar with Back, Reading width, one dismissable `KitNotice` for a save that did not stick, a scrolling column). |
| `ui/HubPage.kt` | `KitField` search (its words go to the presenter through `snapshotFlow`; Back's clear flows back to the field); five `KitRow`s, only Server with a value line ("laptop · Connected", "On this phone", or "Not set up yet"); results as rows with their group as the supporting line; "Nothing matches …". Group pages: `KitSwitchRow` for the two transcript switches, `KitRow` with chevron for the rest. |
| `ui/AppearancePage.kt` | One page: "Light or dark?" `KitSegmented` (Auto, Light, Dark, Saver) with the Auto/Saver note; "Which colours?" `KitChoiceList` of packs by their brand names plus "See all 31 themes"; "How should surfaces feel?" `KitChoiceList` of the five styles (disabled with the Saver reason under Saver); "How much should move?" `KitSegmented` Full/Calm/Off with the chosen level's hint, the Saver hold, and the "Remove animations" note. Every choice applies at once. |
| `ui/VoiceNotificationsPages.kt` | Voice: voice typing row with the speech model's state (opens the setup sheet), "Read replies aloud" switch only where read aloud exists, Voice licenses. Notifications: when Android blocks them, a notice leads with "Turn on notifications in Android"; "What notifies me" (Finished work, Questions for you switches; Usage alerts row when shown); quiet hours row with its range or Off, Wi-Fi only, Send a test notification; the background summary row that opens Keep running. |
| `ui/UpdatesAboutPages.kt` | Updates: "Version 3.0.0" with the state line (never checked, checking, up to date, ready with size, downloading %, downloaded, installing, each failure), the state's one main action (Update, Cancel, Install, Try again), Check now, the checks switch with its body and the "checking is off" note, What's new and the build. About: version, What's new, "Tell others" (Star on GitHub, Share the app), all package licenses, privacy policy. |
| `ui/PrivacyPages.kt` | Privacy: "What we collect" row with the sending state, "Share detailed usage" switch only when something can be sent (plan 16 §5.8), privacy policy. What we collect: generated from `WhatWeCollectSource` — sending state, the switch, every BASIC and DETAILED event (DETAILED rows say On/Off) with every value it can take, the envelope fields, the never-collected list and the id rotation. |
| `ui/SettingsWords.kt` | Enum → `:ui:words` copy (groups, 22 row titles, bilingual search aliases, modes, styles, motion and hints, background states, update failures, notes). |
| `ui/PendingWords.kt` | **TODO(words):** the English lines `:ui:words` lacks (all of the What we collect page, the test-notification failure, "Material You"); §6 lists them. |
| `build.gradle.kts` | `oc.kmp.feature` + `:engine:analytics`; `compose-desktop-currentos` in `desktopTest`; golden dir and record switch. |

Tests: `commonTest` 5 classes / 31 tests (`SettingsStructureTest` 6, `SettingsSearchTest` 4, `SettingsPresenterTest`
16, `PrivacyPresenterTest` 4, `PendingWordsTest` 1) plus `FakeSettingsPorts`; `desktopTest` `SettingsScreenUiTest` 9 (real
presenter, real kit theme, fake ports, found by visible text) and `SettingsGoldensTest` 5 scenes × 3 variants = 15 PNGs.

## 2. Catalog rows and plan tests covered

| Row / plan test | Status | Tests |
|---|---|---|
| **B-NAV-068** (one group list for one server; per-server pages for several) | one-server half: five groups in order, rows lined up, a way back. **Not** the several-servers "By server" pages (I03). | `SettingsStructureTest`, `SettingsPresenterTest`, `SettingsScreenUiTest`, golden `hub` |
| **B-NAV-071** (typos, swaps, prefixes, bilingual aliases, literal above fuzzy, input order on ties) | covered for this module's matcher; the shared `SearchMatcher` and legacy-alias upgrade are S5-SEARCH's | `SettingsSearchTest` (3), `SettingsScreenUiTest` (typing "apperance" opens Appearance) |
| **B-NAV-072** (rows the phone lacks stay out of search; dead aliases lead nowhere) | covered except "crash still opens diagnostics" (the Report a problem row carries the crash alias; not tested) | `SettingsStructureTest`, `SettingsSearchTest` |
| **B-NAV-073** (a search result lands on the row) | half: the result opens the page holding the row; no `HighlightArrival` (not in the kit/routes yet) | `SettingsPresenterTest` |
| **B-NAV-081** (settings rows the server cannot serve are hidden) | settings half | `SettingsStructureTest`, `SettingsPresenterTest` (rows leave when the server goes) |
| **B-XPLAT-003** (desktop hides Android-only settings entries) | settings hub half | `SettingsStructureTest` |
| **B-KIT-061** (Graphite default, all packs, the choice persists) | picker half: Graphite default, most used first, all 31 behind See all, Material You only with the platform's tones, a pack in use stays listed. Persistence is the engine's. | `SettingsPresenterTest` (2), `SettingsScreenUiTest`, golden `appearance` |
| **B-KIT-040** (a new style resets fine-tune) | page half: a different style is one `Style` change, which the port contract defines as clearing fine-tune; the same style writes nothing | `SettingsPresenterTest` |
| **B-KIT-006** (Motion Full until chosen; the choice is written) | page half | `SettingsPresenterTest`, `SettingsScreenUiTest` |
| **B-KIT-008** (Saver turns style and motion off) | page half: both held with the reason, taps write nothing | `SettingsPresenterTest`, `SettingsScreenUiTest` |
| **B-STATE-005** (a refused write keeps the acknowledged value) | page half: value unchanged, one plain note, dismissable | `SettingsPresenterTest` |
| **B-SEC-191** (plan 16; not yet in `behaviors.jsonl`) | rows part: nothing sent → "Not sent yet…" and no switch; with a sender the switch shows, is off until switched on, reaches the engine, and a refused save keeps the old answer | `PrivacyPresenterTest` (3), `SettingsScreenUiTest` (2), golden `what-we-collect` |
| Plan 18 §6 / proposed B-ANLY-011 (an event without words fails) | covered against `PendingWords` | `PendingWordsTest`, `PrivacyPresenterTest` |
| Map SCREENS-NAV-90 (hub, groups), 91 (appearance), 92 (notifications), 93 (privacy, about rows) | partly, as above | above |
| Plan 07 I05, I20 page rules | covered at the presenter and screen | `SettingsPresenterTest` (2), `SettingsScreenUiTest` (2), goldens `notifications`, `updates` |
| E2E-22, E2E-23, E2E-34 | **not done** (need the shell, routes and a device) | — |

Test names start with the row id; plan-only checks use `plan 07 …` / `plan 18 …` / `SCREENS-NAV-…`.

## 3. Commands and results

Environment: Android command-line tools `commandlinetools-linux-16111833_latest.zip` from dl.google.com into
`/opt/android-sdk/cmdline-tools/latest`, licences accepted, `sdkmanager "platform-tools" "platforms;android-37.1"
"build-tools;37.0.0"`; `native/local.properties` (gitignored) `sdk.dir=/opt/android-sdk`; Gradle 9.7.1 on JDK 21.0.12
(JDK 17 toolchain via foojay). Container-only (not committed): `~/.gradle/gradle.properties` `-Xmx6g`, 4 workers;
`~/.gradle/init.d/central-mirror.gradle.kts` (Google's Central mirror first, `robolectric.dependency.repo.url` set).

All from `native/` with `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ANDROID_HOME=/opt/android-sdk`; only this
module's tasks (plus `:quality` Konsist once):

| # | Command | Result |
|---|---|---|
| 1 | `./gradlew :engine:analytics:compileKotlinDesktop :engine:settings:compileKotlinDesktop :ui:kit:compileKotlinDesktop :ui:words:compileKotlinDesktop --continue` (dependency check) | **`:engine:settings` FAILED**: `Unresolved reference 'StorageProblem'`, `'dart'`, `'DartJson'`, `'Storage'` (`ConsentStore.kt`, `InFlowConsent.kt`; the flat-Failure change of INTEGRATE-W1 did not reach it). `:engine:analytics`, `:ui:kit`, `:ui:words` compiled. → §5 D1. |
| 2 | `./gradlew :feature:settings:desktopTest` (tests only, red first) | FAILED as intended: `Unresolved reference 'SettingsPresenter'` (main code compiled; the module passed the module rules). |
| 3 | same, after the presenter | 30 tests, 1 failure (my test expected 10 BASIC events; the schema has 11 — `app_updated`); test fixed → 30/30. |
| 4 | `./gradlew :feature:settings:compileKotlinDesktop` (screens) | BUILD SUCCESSFUL. |
| 5 | `./gradlew :feature:settings:desktopTest` (with UI tests) | BUILD SUCCESSFUL, 40 tests, 0 failures. |
| 6 | `./gradlew :feature:settings:desktopTest -Poc.recordGoldens=true --tests '*SettingsGoldensTest*'` | 15 goldens recorded; looked at by eye (§4); 5 event lines truncated at one line → shortened; `what-we-collect` re-recorded. |
| 7 | Mutation check: `updates-light-en.png` copied over `hub-light-en.png`, `--tests '*SettingsGoldensTest*hub*'` | FAILED as intended: "hub-light-en: 4.508 % of pixels differ (limit 0.5 %)"; golden restored. |
| 8 | `./gradlew :feature:settings:desktopTest :feature:settings:compileAndroidMain --rerun` (final tree) | **BUILD SUCCESSFUL**, **45 tests, 0 failures** (Structure 6, Search 4, Presenter 16, Privacy 4, PendingWords 1, ScreenUi 9, Goldens 5). No warnings from this module (4 warnings in `:ui:kit` and `:engine:analytics`). |
| 9 | `./gradlew :quality:test --tests '*KonsistRulesTest*'` | 2 failures: **K1** on test files only — this module's `SettingsPresenterTest`, `PrivacyPresenterTest` (`kotlinx.coroutines.test`), `SettingsHarness`, `SettingsScreenUiTest`, `SettingsGoldensTest` (`compose.ui.test`, AWT, `java.*`) and the composer's 4 test files; every production file passes K1 and K2. **K4** on `:ui:kit-chat` `KitChatLocals.kt` (not mine). → §5 D5. |
| 10 | `tool/check_write_set.sh --base 997da66b --globs 'native/feature/settings/**,docs/native/units/SETTINGS-UI.md'` | OK, 42 paths before this report. |
| 11 | `tool/check_commits.sh 997da66b HEAD` | OK. |
| 12 | `tool/scan_push.sh 997da66b`, and `git diff 997da66b` grepped for RFC 1918/CGNAT addresses and e-mail addresses | clean / no match (the only address-like text is `collect.invalid`, a reserved test host). |

Not run: `build` (rule 12), `:androidApp`, other modules' tests, iOS, any device or emulator.

## 4. Goldens, by eye

`native/feature/settings/src/desktopTest/goldens/<scene>-<light-en|dark-en|dark-ar>.png`, 412 dp wide (the capture
is the test window's top 768 px):

- **hub**: "Settings", the search field, Server with "laptop · Connected", Agent, Conversations, This app, Help, each with a chevron.
- **appearance**: Back, "Light or dark?" with Dark filled, "Which colours?" with Nord chosen among the 8 most used, "See all 31 themes".
- **notifications**: the blocked notice with "Turn on notifications in Android", the two switches, quiet hours "22:00–08:00", Wi-Fi only off, the test row, "Stay connected in the background · On · running now". Arabic mirrors (switches at the start edge, chevrons point left).
- **updates**: "Version 3.0.0 · 3.1.0 is ready · 41 MB", accent Update, secondary Check now, the checks switch with its body, What's new, Build 10001.
- **what-we-collect**: "Sent to collect.invalid", the detailed switch off, "Always sent" with each event and its values.

Seen and left: the analytics value lists show the schema's enum names (`Under100Ms`, `ThisPhone`, `true, false`) — no
words exist for them (§6); in Arabic the pending English lines stay English, and Latin supporting lines sit at the left
edge under right-aligned titles (the kit's text direction for Latin runs; same as S3-COMPUI saw).

## 5. Deviations and decisions

| # | Decision | Why |
|---|---|---|
| D1 | **No dependency on `:engine:settings`.** Personal choices go through `PersonalChoices` with this module's `PersonalPrefs`/`PersonalChange` on `:ui:design`'s types; the adapter mapping is written in the port's KDoc. | `:engine:settings` does not compile on this tip (§3 #1); the brief says to depend only on modules that compile. |
| D2 | **One presenter and one ViewModel for the hub and all its pages**, not one `XViewModel` per screen (rule 3, plan 07 I01–I20). | The pages share the back stack, the note and the personal choices; splitting now would duplicate the stack until routes exist. Each page has its own state type and composable, so splitting later is mechanical. |
| D3 | **`SettingsPage` is feature-local; no `XEntries`, no Nav3, no Metro.** Places other features own go to `SettingsOpeners` as `SettingsOutside`. | `:ui:routes` has no `SettingsRoute` keys (plan 07 §2.2.2 rows 58–69) and is the coordinator's. When they land, `SettingsPage` maps 1:1 and the presenter's stack becomes the navigator's. |
| D4 | **Copy `:ui:words` lacks lives in `ui/PendingWords.kt` (English only, `TODO(words)`).** | The What we collect page has no keys at all (plan 18 §6 puts them in `strings_privacy.xml`, 7 languages) and this unit may not edit `:ui:words`. One file, so the l10n owner moves it in one step. Words come from plan 16 §5.8 where it gives them. |
| D5 | UI tests and goldens in this module's `desktopTest` (K1 red on test files, as for S3-COMPUI D5). | Same coordinator decision: slice K1 to production sources, or move these into `:ui:screenshots` when it exists. |
| D6 | Goldens are desktop Skiko captures compared in-test (≤ 0.5 % of pixels may differ by more than 24 per channel). | Feature modules have no Robolectric host (plan 14 §3). |
| D7 | Search is a local port of the matcher rules, over the rows that exist now. | `SearchMatcher` and the settings `SearchEntry` set (plan 07 §10.4) are S5-SEARCH's and absent. |
| D8 | Agent-group rows that need a server (Model and mode, Providers, Tools; Default shell in Conversations) are absent without one, rather than "N settings aren't available – Why". | Plan 07 I01's "N settings aren't available on this server" needs `ServerCapabilities` per row (the scope catalog); without a server there is no server to ask. |
| D9 | Saver: the chosen style stays enabled (it is what Saver returns to), the others are disabled with "Saver keeps cards solid…"; motion is disabled with "Saver keeps motion off." | B-KIT-008; a disabled control must say why (B-KIT-031/051). |
| D10 | Pack names are the packs' brand names from `Packs.of(id).label`; Material You uses pending words. | Brand names are not translated (plan 06 §4.2); `Packs.of(Dynamic)` falls back to Graphite's label without the platform's tones. |
| D11 | No colour preview next to each pack, and no style preview. | No kit part draws a swatch or a style sample; rule 2 forbids drawing it here (§6, kit request). |
| D12 | Quiet hours show as 24-hour numbers built from minutes. | Numbers, not copy; the platform's time format comes with the quiet-hours sheet (S4). |
| D13 | No formatter run. | `tool/format-changed.sh` (rule 13) does not exist in the tree; the code follows the surrounding style by hand. |

## 6. Catalog requests and requests to other owners

**Catalog (libraries):** none. `compose-desktop-currentos` already exists and is used in `desktopTest` only.

- **Coordinator:** add `:feature:settings	F	(slice brief names the agent)	slice-4` (or the owner you name) to
  `native/MODULE_OWNERS` (`ModuleOwnersTest` will flag the new module; not run here); decide K1's scope (D5).
- **Routes owner:** `SettingsRoute.Hub/Group(group)/Appearance/Notifications(page, section)/Privacy/Voice/Updates` and
  `AboutRoute.About` (plan 07 rows 58–69), then this module adds `SettingsEntries` and maps `SettingsPage` onto them.
- **INTEGRATE-W2 / settings-engine owner:** make `:engine:settings` compile on the flat `Failure` (`StorageProblem` →
  `StorageRefused`, `core.common.dart.DartJson`). Then the first follow-up here is `PreferencesChoices(Preferences)`
  (about 40 lines; mapping in `SettingsPorts.kt`), plus `SettingsScopeCatalog` rows for these row ids.
- **`:ui:words` (l10n owner):** move every line of `ui/PendingWords.kt` into `strings_privacy.xml` (and en/ar at least):
  the page title "What we collect"; "Not sent yet. Basic reports wait on this phone, at most 90 days, until a service is
  chosen."; "This is a test build: reports are written on this phone only."; "Sent to %1$s"; "Share detailed usage" and
  its body; "Always sent" and its note; "Only if you share detailed usage"; "Sent with every report"; "Never collected";
  "The random id changes every %1$d days. A visit ends after %2$d minutes without use."; 16 event lines, 11 envelope
  lines, 17 never-collected lines; "The test notification wasn't sent. Check that Android lets this app notify you.";
  "Material You". Also **words for every property value** the schema can send (`Under100Ms`, `ThisPhone`, `Kotlin`, …;
  plan 18 §6 "every value it can take", B-ANLY-011).
- **Kit owner:** a pack swatch part (the pack's accent/ground pair for a choice row) and a style sample part for the
  Appearance picker (D11); `KitRow` start-aligning Latin supporting lines under RTL titles; `-Poc.werror` still fails in
  `:ui:kit` (deprecated `maxLength`, two needless `!!` in `KitRow.kt`).
- **Voice (S5-MODELS), notifications (S4-NOTIFY), updater owners:** implement `VoiceSettings`, `NotificationSettings`,
  `UpdateSettings` (`presentation/SettingsPorts.kt`); the pages read nothing else.
- **Shell:** host `SettingsScreen` from the gear, route system Back to `SettingsActions.onBack()` first, and implement
  `SettingsOpeners` for the 25 `SettingsOutside` places (the language sheet and privacy policy sheet included).
- **Catalog owner:** add B-SEC-191 and the B-ANLY rows to `behaviors.jsonl` (plan 18 §9) so these tests can name them.

## 7. Open problems

1. Plan 15's S4-SETTINGS finish line is not met: By server (I03), the several-servers block, "N settings aren't
   available – Why", Language sheet, Keep running, Permissions and automation, saved permissions, unsent work on the
   Privacy page, Manage space, Help › Tips, and E2E-22/23/34 are not built here; nothing is wired into the shell.
2. Only analytics has a real engine behind its port. Personal choices, voice, notifications and updates are tested
   against fakes; nothing persists until the adapters exist (§6).
3. `HighlightArrival` after a search result (B-NAV-073's second half) needs the routes and the kit part.
4. The What we collect page is English in every language until `:ui:words` takes `PendingWords`, and its value lists
   are raw enum names (§4).
5. `:quality:test` K1 stays red on feature test files until the coordinator decides D5; `ModuleOwnersTest` needs the new row.
6. Teaching: this unit adds no hidden gesture (no long-press, swipe or hold), so no tip is owed.
7. Goldens were recorded in this container (Skiko software raster, bundled fonts); another host may need re-recording.

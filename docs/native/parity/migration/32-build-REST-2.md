# NAV KIT L10N BUILD CHAT XPLAT MIGR native migration — part 2 of 2

Base: `cde0afb45f598c00e89de766d9d0a68ba89ae470`; branch `p12x/32-build-REST-2`.
Implementation evidence only. No tests were written or run. No push, signing or release.
54 rows (second half of the sorted switch-scope, non-proven rows). Compile-checked with
`:androidApp:compilePreviewDebugKotlin compileKotlinDesktop`. Implementation status is not parity proof.

Counts: built now 6 · already there 17 · blocked 31 (every blocked row is unbuilt, not stubbed).

Approval review (docs/native/parity/approvals/32-build-REST-2.md) corrected B-L10N-006, B-L10N-012, B-NAV-056 and a stale line reference in B-L10N-013 below.

B-L10N-003 -> blocked: logic exists (engine/composer/.../phrases/Starters.kt:66, strings in en/ar) but no chat empty-state renders it; needs a folder-facts port (entries, Git, changes) in the androidApp graph plus a feature:chat empty-state composable
B-L10N-004 -> built now (native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/presentation/ChatsUi.kt:RelativeAge, ui/ChatsScreen.kt agoText): Home rows show "Just now / N min / N h / N d ago" in the app language with the phone's digits; untitled placeholders were already localized (ChatsScreen.kt `RowTitle.New`). All-conversations and project rows do not show times yet
B-L10N-006 -> built now, isolate half (native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/text/KitBidi.kt:1): ltr/auto isolates once-only, empty left alone; request words use it (feature/chat/.../requests/ui/RequestWords.kt:60). The fact-row half (Dart `KitFactRow.isolateValues`: amounts, card endings, times and addresses isolated inside a sentence, only in RTL) is blocked: no native KitFactRow exists to carry it
B-L10N-007 -> blocked: a hardcoded-string gate is a quality-module rule plus baseline (native/quality BuildRestGates UI_RULES); that is test-side work deferred by the owner
B-L10N-008 -> built now (native/androidApp/src/main/res/xml/locales_config.xml): the system per-app picker now offers all seven shipped languages (en ar es ja pt ru zh), matching AppLanguage and the resource folders; the agreement test is deferred
B-L10N-009 -> built now (native/core/common/src/commonMain/kotlin/io/github/eslamasabry/oc/core/common/text/AppLanguageResolver.kt:1, native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/SettingsWiring.kt systemLocaleTag): the phone's whole language list is walked, region/script match by code, unshipped falls to the next entry, then English
B-L10N-011 -> already there (native/ui/words/src/commonMain/composeResources/values/strings_phone.xml:708): reset leftover copy says "phone" once; no screen renders it yet (other rows)
B-L10N-012 -> built now (native/feature/usage/.../UsageFigures.kt:money, ui/UsageScreen.kt:cost): cost follows the reading direction (Arabic symbol after the amount, phone digits); a model price label does not exist natively yet
B-L10N-013 -> already there (native/androidApp/src/main/kotlin/io/github/eslamasabry/opencode_mobile/graph/PlatformWiring.kt:324 relocalize on storedLocaleTag): notification words follow the app language and relocalize on change; widgets ship en/ar (android/widgets NativeStrings.kt:51)
B-L10N-015 -> already there (native/ui/words/src/commonMain/composeResources/values/strings_project.xml:235): worktree copy never names OpenCode (no screen renders it yet)
B-L10N-016 -> already there (native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/requests/ui/RequestWords.kt:84, native/engine/requests/.../ask/PermissionAsk.kt:111): plain action titles, unknown ids "Permission needed"
B-L10N-017 -> blocked: no language-coverage percentages (strings_settings.xml languagePickerPartlyTranslated unused) and no theme preview sheet; needs a generated coverage table plus the sheet in feature:settings
B-L10N-018 -> blocked: no server capabilities screen exists natively (words capabilityScreenIntro/AllAvailable unused); needs feature-id list, screen and route in feature:settings
B-L10N-019 -> blocked: engine mapper exists (engine/runtime/.../messages/SetupMessages.kt) but no setup/server screen resolves it to text; needs words resolver and wiring in PhoneSetup/Servers
B-L10N-021 -> already there (native/tools/strings/src/main/kotlin/io/github/eslamasabry/oc/tools/strings/Glossary.kt): glossary scan of the string resources
B-L10N-022 -> already there (native/tools/strings/.../Glossary.kt:50, :203): contradiction scan
B-L10N-023 -> already there (native/tools/strings/.../Glossary.kt): scanning rules and allow-list audit
B-L10N-024 -> already there (native/feature/voice/.../presentation/VoiceSettingsPresenter.kt:250, ui/words values-ar/strings_kit.xml): voice model pack names and categorized failures translated
B-MIGR-001 -> already there (native/android/migration/.../steps/M3LegacyTransforms.kt:50, androidApp/.../graph/MigrationWiring.kt:105): notification settings merge, run once through MigrationRunner
B-MIGR-002 -> already there (native/engine/profiles StoredProfileJson requiresOpenCode2Migration/openCode2HistoryPending): migration requirement kept through saves
B-MIGR-003 -> already there (native/android/migration/.../steps/M3LegacyTransforms.kt:71): consent migrated once
B-MIGR-008 -> already there (native/android/migration/.../steps/M1Census.kt, M5Audit.kt:81): files directory kept in place, Linux rootfs untouched
B-MIGR-009 -> already there (native/android/migration/.../steps/M1Census.kt:141, M5Audit.kt:81-86): app_flutter inventoried and its vaults/models read without re-download
B-NAV-001 -> blocked: no about notice model; About page (feature/settings/.../UpdatesAboutPages.kt:133) lacks provenance/desktop-limits text and an intro-free notices model
B-NAV-015 -> blocked: FailedJobReport/FailedServiceReport models exist but the Report route has no job context, no log-first diagnostics and no Report-this-failure actions
B-NAV-016 -> blocked: SetupCommands.kt (private-password command) is only referenced by tests; first-run and Servers screens still hold their own command text
B-NAV-019 -> blocked: TeachingRegistry is not constructed by any app wiring and there is no gesture ledger; needs ledger file, Help tips wiring (shared with B-NAV-074)
B-NAV-023 -> blocked: no branch chip in the chat header (ChatViewModel.openGit only); needs a branch-read port and header chip in single-owner feature/chat
B-NAV-027 -> blocked: Signal-style home is not built (surfaceStyles off); a large feature/chats + kit piece
B-NAV-029 -> blocked: RootChoice.ManagedMove has no screen and nothing sets needsManagedMove; OpenCode 1 to 2 move page not built
B-NAV-033 -> blocked: ModelChoices cycling (engine/agents/.../ModelChoices.kt:38) has no instance in the graph and no composer action; composer is single-owner
B-NAV-035 -> blocked: ServerPlaceTitles exists engine-only; the per-server default-model settings page that shows "New conversations on …" is not built
B-NAV-036 -> blocked: ModelRecommendations is engine-only; no model-defaults/recommendations sheet exists
B-NAV-040 -> already there (native/feature/servers/.../ui/ServersScreen.kt:241, engine/lifecycle/PhoneOwnServer.kt): only the phone's own servers are marked
B-NAV-042 -> blocked: no phrase-chips settings row/page/search entry (scope entry only at SettingsScopeCatalog.kt:93)
B-NAV-043 -> blocked: no project dev-server signal for the terminal/project header (scan exists in DetectedDevServers.kt:275 for the services screen only)
B-NAV-046 -> blocked: no queued-prompt move operation in engine/delivery queue nor move sheet
B-NAV-047 -> blocked: sign-in, manage accounts, unfinished sign-ins, tools and plugins screens not built (settings routes Providers/Tools unhandled)
B-NAV-050 -> blocked: "background checks" search target and the project hub Changes count are missing (only the notification line is done, BackgroundScreen.kt:125)
B-NAV-052 -> blocked: Saved prompts half is there (SavedPromptsScreen.kt:152) but "rows point with one receipt" has no native surface (AI Team gate receipt retired)
B-NAV-056 -> built now (native/feature/search/.../presentation/GoToMenu.kt, SearchViewModel.kt goTo/loadGoTo, ui/SearchScreen.kt goTo, androidApp/.../graph/SearchUsageWiring.kt goToFacts): Search shows Go to rows (Needs you, Projects when the server supports project management, Settings, Servers/Add a server, New conversation) then the last three conversations; typing filters; each row opens its real route (Needs you lands on the Chats home, which carries the Needs-you count and filter; Dart pre-selects that filter, native does not yet). Files, Terminal and Help are not offered because they have no stand-alone page to open yet; the waiting count is not shown
B-NAV-058 -> blocked: only settings search is capability-gated; no tab/project-tool/AI-setup index and no "Running on this phone"/Linux Services entries
B-NAV-059 -> already there (native/feature/settings/.../ui/HubPage.kt:40, SettingsSearch.kt:25): settings search finds and opens settings
B-NAV-065 -> blocked: SessionPinStore is not bound in the app graph and feature:chats has no pinned ordering/pin action/fetch-once/retry
B-NAV-070 -> blocked: search results open a page, not the row inside it (no row scroll/highlight target)
B-NAV-074 -> blocked: SettingsRow Tips has no screen/route (Help › Tips) and no teaching registry wiring or Make it yours tour
B-NAV-076 -> blocked: no Tools screen consumes tool descriptions; the first-sentence rule needs that screen (shared with B-NAV-047)
B-NAV-079 -> blocked: Plans engine is built with only the sign-ins source (PlansWiring.kt:97); in-app Codex, PC read and stale numbers need a phone usage source
B-NAV-080 -> blocked: UsageWidgetFeed phone reading (UsageWidgetFeed.kt:173) has no caller; same phone usage source as B-NAV-079
B-XPLAT-009 -> blocked: TerminalOsc52 parser has no caller; needs the Termux client clipboard hook in the Android terminal slot
B-XPLAT-010 -> blocked: TerminalLinks parser has no hit-test/tap in the live terminal view
B-XPLAT-011 -> blocked: terminal key bar, command-line spans, masking and overflow UI are not built in :ui:kit-content (only key bytes in TerminalText.kt:34)
B-XPLAT-012 -> already there (native/core/domain/.../capabilities/Capabilities.kt:5, PlatformFamily.kt:20; wired AndroidAppGraph.kt:35, DesktopAppGraph.kt:22)
B-XPLAT-013 -> already there (native/core/domain/.../capabilities/PlatformFamily.kt:59): each capability is claimed by one update channel

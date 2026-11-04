RESULT: MERGED
- job-50-blocked-6-L10N-CHAT-PROTO-XPLAT-KIT: resolved: SettingsWiring.kt, ChatEntries.kt, PhoneSetupScreen.kt, SearchEntries.kt, SettingsScreen.kt, LocalTerminalViewModel.kt
- job-50-blocked-4-SEC: resolved: AttentionStateWiring.kt, NativeRuntimeActs.kt, SettingsEntries.kt (AppAutomaticActivities folded into AppActivityHistory)
- job-45-port-state: resolved: 38 files (chat requests/composer/world, files, projects, servers, settings, Oc2Gateway, PlatformWiring, PreviewRuntime, manifest)
- job-40-port-screens-3: resolved: 125 files (chat, settings, ui/kit, phone/servers/onboarding/runtime setup, agents/plans/usage/chats, platform/monitor/widgets/protocols) + folded duplicates
- job-47-port-rest-2: resolved: 67 files (chat header/cards/files/projects/delivery, settings/onboarding/terminal, paseo/handoff/domain, app shell/net/oc2, agents/schedules/PreviewRuntime) + folded duplicates
- job-46-port-rest-1: resolved: 44 files (chat/transcript/chats, settings/phone/linux/kit, oc2/net, app shell)
- job-27-build-AGENT-2: resolved: 20 files to the integration's tree (both parents already merged; its own merge resolution superseded; its deletions of PaseoAgentFeatures/PaseoBackgroundTasks/PaseoSubAgents not taken)
- job-44-port-ui: resolved: 27 files (chat requests/composer/transcript/cards, files, background, phone, shell, Linux voice, build agents)

# Mass merge of the native migration into the P12 integration

Branch `native/p12-mass`, started at `native/p12-mass-base` (ebd5cd19b). Each unit was merged with
`git merge --no-ff` in the requested order and pushed after each merge. Nothing was compiled here
(Google Maven is blocked in this environment); the build server compiles once after this record.

Rules applied to every conflict: both intents kept (what the unit adds and everything already merged),
no feature dropped, gradle dependency lists as unions, docs and ledgers keep both sides, and duplicate
declarations of the same class/function from two units merged into one.

Checks run on the whole branch (base..head, 732 files): no conflict markers; balanced braces,
parentheses and brackets in all 587 changed Kotlin files; no package-level class declared in two
files; no interface bound twice with @ContributesBinding; no new duplicate string resource names
(the 7 `linuxSvc*` names duplicated in both locales already existed at the base). Not run: Kotlin
compilation, tests, APK.

## Duplicates folded (one implementation kept, both behaviours)

- SEC: `AppAutomaticActivities` + `AppActivityHistory` (same registry key) -> `AppActivityHistory`.
- job-40: `LocalKitStyle` / `KitStyleState` (effects `SurfaceStyle`/`Corners` are typealiases of
  design.style), `KitTerminalKey` (`KitTerminalKeyBar.kt`), `SetupAppProgress`, `UsageAlertNote`,
  `AndroidPhraseSettings`, widget overlay/configure activities, six `widgetPanel*` strings,
  profile monitoring (`NativeProfileMonitoring`, `AndroidProfileMonitoring` typealias),
  `Oc2Integrations`, `createCommandTerminal`, What's new, appearance guide store, phrase-chip row,
  chat read marking, offline-queue version, sign-in close/drain, usage alert writer.
- job-47: `TerminalRoute`, managed move UI/actions/ViewModel, `OnboardingRoute.ManagedMove`,
  remote-terminal strings, `ProjectStorageActions`, `ChatHandoffBindings`,
  `AndroidProjectStorageAccess`, Paseo card auto-allow, handoff link book, OkHttp no-retry client,
  a third folder storage gate, two `onCleared()`.
- job-46: Linux vitals, chats relative age, bounded card history reader, oc2 terminal socket path,
  oc2 projects, `migrationStorageFailed` (local-model text now `localModelStorageFailed`).
- job-44: question sheet, model favorites backing and store, `cycleModel`/`toggleFavorite`, tool
  output renderer, file tables (the unit's `engine.files` copy removed), `numberText`.

## Security notes

- Request decisions stay bound to the reviewed content everywhere (cards, question sheet, shade
  actions, invitations); the units' unbound answer paths were not kept.
- Per-profile keys added by the units are registered with the shared `ProfileScope` used by profile
  deletion (`SecurityFeatureBindings`): form drafts, session read state, monitoring, project storage,
  phrase suggestions, read-state consent, permission invitations, connection forms, server defaults,
  plans readers/legacy collectors, and `oc.modelLibrary.` (previously unregistered).
- The oc2 terminal ticket check on the live path is tightened to 4096 printable ASCII characters.

## Follow-ups (not blocking the merge; for the build/approval pass)

- job-40 follow-ups:
  - Two style engines side by side (design.style + design.effects StyleSpec), two Signal palettes, LocalKitSignalInSlip/LocalKitSignalSlip.
  - oc.sharedProjects.* has three writers: SharedFolderWiring (HEAD), NativeSharedProjectFolders, ProjectStorageWiring (unit); projects storage gate built twice (ProjectStorageViewModel + inline storageBlock).
  - Phrase-chip keys registered by AndroidPhraseChipsWiring (HEAD) and PhraseSuggestionsWiring (unit); StoredPhraseSettings unused.
  - oc.appearanceGuide written by SettingsJourney (unit) and AppearanceGuideMemory (HEAD) in different JSON shapes; Settings uses the journey only for resetTips().
  - Two voice download classes (NativeVoiceSetup HEAD for removal, NativeVoiceSetupComponent unit for install).
  - Monitor engines MonitorRuntime + AppProfileMonitor + NativeProfileMonitoring may double-poll a server.
  - Two form systems (ChatForms inline + ConnectionForms/MonitoredFormCard), deduplicated by form id.
  - Unit's second alert pipeline (PlansAlertWiring, UsageAlertDelivery) and plans readers/legacy collectors left unwired (same keys as HEAD's).
  - Widget: unit's single-activity Compose host and RecognizerIntent dictation not ported (HEAD's overlay + on-device voice kept; FLAG_SECURE ported). Unused: ui/shell/widgets/*, WidgetPanelWiring, Oc2PtyOperations.
  - Product calls: Settings › Updates opens the Updates screen; Settings › Providers/Tools with several servers uses the unit's server picker.
  - Tests to update: servers pairing/form tests (P12FixPairingTest, PairingFlowTest, ServersViewModelTest, P12FixFormDraftTest, PairingScreensTest), PlansScreenUiTest, ChatRequestsModelTest:217, teaching registry size (8), SetupEngine app-component tests.
  - Pre-existing duplicate string names linuxSvc* (already at the P12 base).
- job-47 follow-ups:
  - Two payload guards (PhonePayloadGuard HEAD + NativePaseoAdmission unit), both applied to every send/mutation.
  - Two card qualifiers (PhoneAgentCardsQualifier HEAD + genUiShowAllowed unit) feed Paseo card auto-allow; either can allow.
  - Two chat handoff entry points (ChatHandoffs + ChatAgentHandoffs); HandoffLinkOwner (HEAD) and NativeHandoffLinks share one storage with two HandoffLinks instances.
  - AndroidManagedMove.move() unused; first-run managed move only starts when PreviewRuntime.connectNow calls managedUpgrade.prepare.
  - Remote-terminal strings: HEAD wording kept where both sides differed (Empty, Failed, Transcript, List; ar Readable).
  - PaseoHandoff.kt needs a ktfmt pass.
  - Manage space and widget overlay built twice: HEAD's ManageSpaceActivity/overlay kept (unit's update gate ported into the overlay); unit's :feature:manage-space, ManageSpaceWiring, :feature:widget-panels, WidgetPanelsWiring, WidgetPanelStore, widget_overlay_theme.xml unreachable.
  - Unit's Oc2Terminal.kt helpers and NetClient.openTerminal/OkHttpTerminalChannel path unused (HEAD's Oc2Terminals/connectSocket live). UnknownHostException now maps to DnsFailed / NetworkWhy.Dns.
  - PlatformWiring ShareSeed / SEED_WAIT_MS unused (unit replaced seeding with incomingShare).
- job-46 follow-ups:
  - Linux shortcut opens LinuxRoute.Home (HEAD) while EntryDecider maps Linux to ThisPhone; awaitInitialConnection completes only after restoreSavedConnection.
  - Unit's oc2 terminal socket path dropped (fourth implementation); Oc2PtyOperations still unused; ticket check tightened to 4096 printable ASCII on the live path.
  - Chats "ago" built twice: HEAD's RelativeAge kept, unit's updatedAtMs/nowMs dropped.
  - Linux vitals built twice: unit's engine-typed version kept with HEAD's redaction and MemoryInfo fallback.
  - Local-model failure text got its own key localModelStorageFailed (unit's text), migrationStorageFailed stays HEAD's.
  - LinuxVitalsPanel number formatting without bidi isolates (check RTL).
- job-44 follow-ups:
  - One question sheet (HEAD's) with the unit's Cancel and "Send answers"; the unit's unbound sendSheet dropped.
  - Model picker: the unit's ModelCatalogPanel replaces HEAD's inline picker; HEAD's provider logos, providerRouteName and "Server default" row ported in. QaWave1ComposerTest (chat-model-search tag) needs updating.
  - Rich tool output: one renderer (the unit's ToolStepBody); HEAD's KitToolOutput body dropped from TurnFactsRows.
  - File tables: one implementation (HEAD's engine.media DelimitedText + KitViewerTableBody); the unit's engine.files DelimitedText, DelimitedPreview, KitDelimitedPreview removed; fileTable* strings may be unused.
  - Card callout warning tone now Failure (unit's change).
  - oc.modelLibrary.<profileId> registered for profile deletion (was not registered before).

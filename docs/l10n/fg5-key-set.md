# FG5: the key set for the five new languages

**Finish line (backlog FG5).** The app offers Japanese (`ja`), Simplified
Chinese (`zh`), Spanish (`es`), Brazilian Portuguese (`pt`) and Russian
(`ru`) for the first-run screens and the chat core. Any message a language
does not carry reads in English (gen-l10n writes the English text into that
language's class), so nothing crashes and no build step fails on a missing
key. **Non-goals:** translating all ~7,400 messages, any layout change,
translating credentials, URLs, code identifiers or product names (OpenCode,
Claude Code, Codex, Termux, Ubuntu, Git, SSH, Node.js, AI Team, MCP).

## How the set was chosen

The set is every message the screens below render, found by reading the
screen and kit sources for ARB key names, then pruned by hand to what a person
meets in the first minutes and in every conversation. `tool/l10n/
partial_locales_core_keys.txt` is the same list as one key per line; the gate
`tool/l10n/check_partial_locales.py` fails when a language lacks any of them or
when the list shrinks.

## Language and file decisions

| Language | ARB | Why this code |
| --- | --- | --- |
| Japanese | `app_ja.arb` | Plain language code; polite-neutral register (です・ます, imperative chips as plain commands the agent can act on). |
| Simplified Chinese | `app_zh.arb` | The project's gen-l10n setup (`l10n.yaml`) takes one ARB per language and Flutter matches a device by language code, so script-coded files (`zh_Hans`) would need a second, language-only `zh` file anyway. `zh` carries Simplified; Traditional-Chinese devices (`zh_Hant`, `zh_TW`) match it too and read Simplified rather than English. |
| Spanish | `app_es.arb` | Latin-American-neutral wording (tú, "computadora", "Agregar"). |
| Brazilian Portuguese | `app_pt.arb` | Written as Brazilian Portuguese ("celular", "arquivo"); a device in Portugal matches it too. A separate `pt_BR` file would require a full `pt` file beside it. |
| Russian | `app_ru.arb` | Plural forms use `one/few/many/other`. |

One more thing the work found: Flutter resolves a device language the app
does not ship to the **first** supported locale, and the generated list is
alphabetical, so that was Arabic. `resolveAppLocales`
(`lib/state/app_locale.dart`, wired into the three `MaterialApp`s) makes
English the fallback (`test/l10n_new_locales_test.dart` pins both the premise
and the fix).

Terms, so the five read as one product: a *conversation* is `会話` / `对话` /
`conversación` / `conversa` / `чат`; an *agent* is `エージェント` / `智能体` /
`agente` / `agente` / `агент`; a *prompt* is `プロンプト` / `提示词` /
`mensaje` / `mensagem` / `запрос`; *draft* `下書き` / `草稿` / `borrador` /
`rascunho` / `черновик`.

## Review status

The translations were written by the coding agent (Claude) for this slice,
keeping ICU placeholders and plural syntax exact (checked mechanically by
`tool/l10n/check_partial_locales.py`) and product names untouched. No native
speaker has read them yet. Before a release that advertises the languages,
have one speaker per language read the **cards**, **composer** and **status**
groups first (permission and sending words), then the rest.

## Groups

### Language picker (14)

**Source:** `lib/ui/widgets/language_picker.dart`, Settings > Appearance (`personal_settings_screens.dart`)

**Why:** Someone who picked Japanese must be able to read the sheet that says so, find their way back, and see each language under its own name. The five native names are the same text in every language (never translated); "Partly translated (N %)" says honestly how far each language goes.

### First-run welcome (24)

**Source:** `lib/ui/screens/servers/server_rows.dart`, `servers_state.dart` (the `first-run-welcome` list), `lib/ui/screens/servers_screen.dart`

**Why:** The first screen of a new install: the value line, the one question "Where does your coding agent run?", its three choices and what hangs off them. A person who cannot read it cannot start.

### Phone setup (start, customize, progress, ready, Termux) (116)

**Source:** `lib/ui/screens/phone_setup/*.dart`, `lib/builtin/setup/*.dart` (component names and reasons)

**Why:** "On this phone" is the recommended first-run path: the start screen, the choose-what-to-install sheet, progress and stop, naming the first project, the Termux fallback. Excluded: the long tail of failure sentences in `setup_ui_messages.dart` (about 230 keys, mostly Termux and pairing diagnostics), project/profile names stored as data, and runtime names ("OpenCode 1/2").

### App shell and navigation (12)

**Source:** `lib/ui/screens/home_screen.dart`, `lib/ui/kit/kit_nav.dart`

**Why:** Bottom-navigation labels, the connection words beside the server name, the server switcher and the back-to-exit hint: seen on every screen from the first minute.

### Conversation list, new conversation, top bar, empty chat (70)

**Source:** `lib/ui/screens/chats/*.dart`, `lib/ui/screens/chat/chat_top_bar.dart`, `empty_chat.dart`

**Why:** The list the app opens on after setup (filters, empty states, sections), starting a conversation, and the starter chips of an empty one. Starter chips are also the prompt the agent receives when tapped, so they are written as the instruction a speaker would give.

### Composer: write, send, stop, attach, queue (107)

**Source:** `lib/ui/screens/chat/composer.dart`, `composer_tools.dart`, `chat_composer_region.dart`, `chat_send.dart`, `chat_queue.dart`, `pending_sends_strip.dart`, `lib/ui/kit/chat/kit_composer*.dart`

**Why:** Where a prompt is written and sent: field, Send and its variants (after this reply, when back online), Stop, attach and photo tools, drafts, the offline queue and its confirmations, the model chip. Excluded: the voice-conversation layer (`kitVoice*`), receipt IDs, web-source and session-note rows, slash-command descriptions.

### Turn status lines and transcript rows (83)

**Source:** `lib/ui/kit/chat/kit_turn.dart`, `kit_work_line.dart`, `lib/ui/screens/chat/chat_states.dart`, `transcript_rows.dart`, `chat_status_line.dart`

**Why:** What the agent is doing now (Sending, Thinking, Writing, Working, Waiting for you), how a reply ended (stopped, interrupted, reconnecting), the work summary ("ran 3 commands · read 2 files"), error states of a conversation and the free-model notice.

### Permission and question cards, approval modes (72)

**Source:** `lib/ui/screens/chat/permission_sheet.dart`, `question_sheet.dart`, `attention_card.dart`, `chat_requests.dart`, `approvals_sheet.dart`, `approval_mode_menu.dart`

**Why:** The moments the agent stops for a person: Allow once / Reject / Always allow, the question sheet, retry banners, and the approval-mode menu. A mistranslated permission button is a safety problem, so these are the first words for a native speaker to check (see Review status).

### Model and agent picker; agents on this phone (112)

**Source:** `lib/ui/widgets/pickers*.dart`, `lib/ui/screens/agents/*.dart` (state words and main actions only), `lib/ui/kit/chat/kit_composer_chips.dart`

**Why:** Choosing the model (search, favorites, recent, effort, "sign in to a provider"), the agent chip (Build / Plan stay as the server names them), and the phone-agent rows (Ready, Sign in needed, Install, Resume). Excluded: the long agent failure and sign-in explanations, model price and context lines.

### Shared kit words the screens above render (62)

**Source:** `lib/ui/kit/kit_*.dart` (sheet, top bar, request card, receipt, status marks, undo, choice list)

**Why:** Words the kit parts add around any screen: Close, Back, Try again, Undo, Copy, the discard-changes question, "Needs you", the Sent / Not confirmed receipt, risk-step durations. Only those the first-run and chat-core screens actually reach; the kit gallery, viewer, diff, log, date picker and capability explainer words are out of scope.

**Total: 672 messages**, 7 of them the language names, which read the same in every language (the five new names, `English` and `العربية`).

## The keys

English text beside each key, grouped as above.

### Language picker

- `e7LocaleUiArabic` — العربية
- `e7LocaleUiChinese` — 简体中文
- `e7LocaleUiClose` — Close
- `e7LocaleUiDescription` — Choose the language used throughout the app. Server messages and your text stay as written.
- `e7LocaleUiEnglish` — English
- `e7LocaleUiJapanese` — 日本語
- `e7LocaleUiLanguage` — Language
- `e7LocaleUiPortuguese` — Português (Brasil)
- `e7LocaleUiRussian` — Русский
- `e7LocaleUiSaveFailed` — Language could not be saved. Your previous choice is still active. Select a language to try again.
- `e7LocaleUiSaving` — Saving language…
- `e7LocaleUiSpanish` — Español
- `e7LocaleUiSystem` — Use system language
- `languagePickerPartlyTranslated` — Partly translated ({percent} %)

### First-run welcome

- `addServerTypePaseo` — Claude Code or Pi
- `e7LibraryReportABug` — Report a problem
- `e7SetupAboutNotices` — About and open source notices
- `e7SetupAddServer` — Add server
- `e7SetupConnect` — Connect
- `e7SetupEdit` — Edit
- `firstRunJustShowMe` — Just show me
- `firstRunOnComputer` — On my computer
- `firstRunOnComputerDetail` — Connect to an agent that runs there.
- `firstRunOnPhoneDetail` — Set one up here. No computer needed.
- `firstRunWhereQuestion` — Where does your coding agent run?
- `kitDetails` — Details
- `onboardingDemoNote` — A simulated conversation. No server needed.
- `onboardingSetupGuide` — Setup guide
- `onboardingTermuxSetup` — On this phone
- `onboardingValueBody` — Ask your coding agent for a change, review the result, and pick up where you left off.
- `onboardingValueTitle` — Keep your work moving.
- `otherServerWorking` — {count, plural, =1{1 working} other{{count} working}}
- `phoneSetupStartEntryDetail` — Run a coding agent right here. No computer needed.
- `phoneSetupStartOtherWays` — Other ways
- `serverRowConnected` — Connected
- `serverRowQueuedWaiting` — {count, plural, =1{1 prompt waiting to send} other{{count} prompts waiting to send}}
- `termuxInAppInstead` — Set up the in-app server instead
- `termuxInAppInsteadDetail` — A fresh start that runs inside this app. No Termux needed.

### Phone setup (start, customize, progress, ready, Termux)

- `aiteamComponentStageDownloading` — Downloading AI Team · {index} of {total}
- `aiteamComponentStagePreparing` — Getting AI Team ready
- `aiteamComponentTitle` — AI Team
- `builtinServerStopped` — Not running.
- `connectStartingPhone` — Starting OpenCode on this phone…
- `e7SetupAppSettings` — Allow the permission in Settings
- `e7SetupCheckingTermuxShort` — Checking Termux...
- `e7SetupCopyOpenTermux` — Copy & open Termux
- `e7SetupElapsedMinutes` — {minutes}m {seconds}s elapsed
- `e7SetupElapsedSeconds` — {seconds}s elapsed
- `e7SetupGetTermux` — Get Termux
- `e7SetupInstallTermuxDetail` — Install the current F-Droid build of Termux, then return here.
- `e7SetupSetupNotStarted` — Termux opened but the setup did not start. Retry once; if it happens again, copy the failure report.
- `e7SetupTermuxNoAnswer` — Termux didn't answer. Tap Copy & open Termux, paste the line in Termux and press Enter, then come back here.
- `e7SetupTermuxOutdated` — This Termux is too old for the app to use. Install the current one, then tap Continue setup.
- `phoneSetupCustomizeAllInstalled` — Every optional tool is already on this phone.
- `phoneSetupCustomizeIncluded` — Required
- `phoneSetupEssentialsShort` — Git and SSH
- `phoneSetupEssentialsTitle` — Git, SSH and certificates
- `phoneSetupEssentialsWhy` — Agents use Git and SSH to work on your projects.
- `phoneSetupLinuxTitle` — Linux base
- `phoneSetupLinuxWhy` — Everything else runs inside it.
- `phoneSetupNodeWhy` — OpenCode runs on Node.js.
- `phoneSetupOpenCodeWhy` — The coding agent itself.
- `phoneSetupOpenWelcomeContinue` — Continue
- `phoneSetupOpenWelcomeRunning` — Setting up OpenCode on this phone · {percent}%
- `phoneSetupOpenWelcomeShowProgress` — Show progress
- `phoneSetupOpenWelcomeStopped` — Setup on this phone is {percent}% done
- `phoneSetupOpenWelcomeStoppedDetail` — Continuing picks up where it left off.
- `phoneSetupPreflightLowMemoryBody` — OpenCode needs a phone with at least {minimum} MB of memory; this one has {actual} MB. Run it on a computer instead and connect this phone to it.
- `phoneSetupPreflightLowMemoryHeadline` — This phone doesn't have enough memory
- `phoneSetupPreflightLowSpaceBody` — Free about {size} on this phone, then come back to set this up.
- `phoneSetupPreflightLowSpaceHeadline` — Not enough free space
- `phoneSetupPreflightMayBeSlow` — It may be slow on this phone, which has {memory} MB of memory.
- `phoneSetupPreflightOpenStorage` — Open Storage settings
- `phoneSetupPreflightUnsupportedBody` — This app's Ubuntu only runs on a 64-bit Arm or Intel phone; this one reports {abi}.
- `phoneSetupPreflightUnsupportedHeadline` — This phone can't run it
- `phoneSetupProgressFirstSetupNote` — Step 1 of 3: install. Then name a project and start a conversation. You can leave the app. We'll notify you when it's ready.
- `phoneSetupProgressKeepGoing` — Keep going
- `phoneSetupProgressLeaveHint` — You can leave the app. We'll notify you when it's ready.
- `phoneSetupProgressStopConfirm` — Stop setup
- `phoneSetupProgressStopContinueLater` — Continue any time from On this phone.
- `phoneSetupProgressStopMessage` — What's finished stays installed.
- `phoneSetupProgressStopTitle` — Stop setup?
- `phoneSetupProgressTitle` — Setting up OpenCode on this phone
- `phoneSetupReadyCreateFailed` — The project could not be created: {reason}
- `phoneSetupReadyCreateOpen` — Create and open
- `phoneSetupReadyCreating` — Creating the project
- `phoneSetupReadyNameEmpty` — Enter a name.
- `phoneSetupReadyNameHelp` — Letters, numbers, - _ .
- `phoneSetupReadyNameInvalid` — Use letters, numbers, - _ or . and start with a letter or number (up to 64).
- `phoneSetupReadyNameLabel` — Project name
- `phoneSetupReadyNameOneFolder` — Use one name, without slashes.
- `phoneSetupReadyNameTitle` — Name your first project
- `phoneSetupReadyOpenFailed` — The project could not be opened: {reason}
- `phoneSetupReadyOpenFolderInstead` — Open a folder instead
- `phoneSetupReadyTitle` — OpenCode is ready
- `phoneSetupStartAboutMinutes` — {minutes, plural, =1{About a minute} other{About {minutes} minutes}}
- `phoneSetupStartAdd` — Add
- `phoneSetupStartAddTitle` — Add tools
- `phoneSetupStartByAddress` — Connect to a computer by address
- `phoneSetupStartChecking` — Checking what's installed…
- `phoneSetupStartConnect` — Connect
- `phoneSetupStartContinue` — Continue setup
- `phoneSetupStartCustomize` — Choose what to install
- `phoneSetupStartCustomizeTitle` — Choose what to install
- `phoneSetupStartDone` — Done
- `phoneSetupStartFailed` — That didn't work: {reason}
- `phoneSetupStartGigabytes` — {value} GB
- `phoneSetupStartHeadline` — Run a coding agent right here
- `phoneSetupStartIncludes` — Includes {tools}.
- `phoneSetupStartInstalled` — Installed
- `phoneSetupStartListPair` — {first} and {last}
- `phoneSetupStartListSeparator` — , 
- `phoneSetupStartMegabytes` — {value} MB
- `phoneSetupStartNothingChosen` — Nothing chosen yet
- `phoneSetupStartOpen` — Open
- `phoneSetupStartProgressHeadline` — Setup is {percent}% done
- `phoneSetupStartPromise` — No computer and no other apps. ~{size} to download the first time.
- `phoneSetupStartPromiseNoSize` — No computer and no other apps.
- `phoneSetupStartReadyBody` — Open it to start a conversation.
- `phoneSetupStartReadyHeadline` — OpenCode is ready on this phone
- `phoneSetupStartRunningBody` — It keeps going while you use other apps.
- `phoneSetupStartScreenTitle` — On this phone
- `phoneSetupStartSetUp` — Set up OpenCode on this phone
- `phoneSetupStartSetUpHere` — Set it up in this app instead
- `phoneSetupStartSteps` — Install, name a project, start a conversation · {time}
- `phoneSetupStartStepsTime` — {minutes, plural, =1{about 1 min} other{about {minutes} min}}
- `phoneSetupStartStoppedBody` — It stopped before finishing. Continuing picks up where it left off.
- `phoneSetupStartTermuxBody` — You set it up with Termux before. Connect to keep using it.
- `phoneSetupStartTermuxHeadline` — OpenCode is already set up in Termux
- `phoneSetupStartTermuxNotAllowed` — Termux is installed but hasn't let this app in yet. Finish its setup.
- `phoneSetupStartTermuxProgressHeadline` — Setup in Termux is {percent}% done
- `phoneSetupStartTitle` — Start OpenCode
- `phoneSetupStartUseTermux` — Use Termux instead
- `phoneSetupStartUseTermuxOne` — Use the one in Termux
- `phoneSetupStartUseTermuxOneDetail` — OpenCode is also set up in Termux. Connect to it instead.
- `phoneSetupStartWhy` — Setup ends with OpenCode running.
- `phoneSetupTermuxAllowHow` — In Termux, paste the copied line and press Enter.
- `phoneSetupTermuxConnecting` — Connecting
- `phoneSetupTermuxCost` — About 10–15 minutes the first time, in Termux's storage
- `phoneSetupTermuxGetCurrent` — Get the current Termux
- `phoneSetupTermuxLeaveHint` — You can leave the app. Termux keeps working and this list picks up where it is when you come back.
- `phoneSetupTermuxOtherRuntime` — Termux already runs the other OpenCode. Switch it on This phone, then continue setup.
- `phoneSetupTermuxStartingTitle` — Starting the server
- `phoneSetupTermuxUpdatingTitle` — Updating this phone
- `setupProgressViewOverallLabel` — Setup progress
- `setupSwitchProgressTitle` — Switching to {runtime}
- `termuxGuideTitle` — Connect Termux once
- `termuxPermissionDenied` — Android didn't let this app run commands in Termux.
- `voiceComponentRemove` — Remove voice typing
- `voiceComponentRemoveBody` — Deletes the speech model and frees {size}. Voice typing stops working until you add it here again.
- `voiceComponentRemoveTitle` — Remove voice typing?
- `voiceComponentSummary` — Speak instead of typing, even offline
- `voiceComponentTitle` — Voice typing
- `workLoadingLabel` — Loading

### App shell and navigation

- `chatsHomeTitle` — Conversations
- `commonRetry` — Try again
- `e7WorkspaceBackExit` — Press back again to exit
- `e7WorkspaceConnected` — Connected
- `e7WorkspaceConnecting` — Connecting
- `e7WorkspaceOffline` — Offline
- `kitNeedsYouBadgeSuffix` — {count, plural, =1{, 1 need you} other{, {count} need you}}
- `librarySettingsTitle` — Settings
- `serverSwitcherOpen` — Switch server
- `shellServerSwitching` — Switching…
- `shellTabChats` — Conversations
- `shellTabFiles` — Files

### Conversation list, new conversation, top bar, empty chat

- `agentsCancel` — Cancel
- `agentsResumeNoticeBody` — Starts a new conversation. {agent} can't reopen this one, and it stays as it is.
- `agentsResumeNoticeTitle` — Start a new conversation?
- `agentsStartNew` — Start new conversation
- `agentsStateCantReopen` — Can't reopen old conversations
- `chatProjectMenuLabel` — Project tools
- `chatStartAddTests` — Add tests
- `chatStartBuildWebPage` — Build a small web page
- `chatStartChangeCount` — {count, plural, =1{1 change} other{{count} changes}}
- `chatStartEmptyFolder` — Empty folder
- `chatStartExplainProject` — Explain this project
- `chatStartFindBug` — Find and fix a bug
- `chatStartItemCount` — {count, plural, =1{1 item} other{{count} items}}
- `chatStartListFolder` — List what's in this folder
- `chatStartLooking` — Looking at the folder…
- `chatStartNodeProject` — Start a Node.js project
- `chatStartPythonScript` — Write a Python script that…
- `chatStartReadme` — Set up a README
- `chatStartServerFolder` — Server folder
- `chatStartSuggestionsLabel` — Ways to start
- `chatStartTip` — Type / for commands · long-press a message for its actions
- `chatStartWhatChanged` — What changed recently?
- `chatUiAskAgent` — Ask {agent}…
- `chatUiSessionMenu` — Conversation menu
- `chatsFilterChatCount` — {count, plural, =1{1 conversation} other{{count} conversations}}
- `chatsFilterNeedsYouWord` — needs you
- `chatsFilterOpenProject` — Open a project…
- `chatsFilterRunningCount` — {running} running
- `chatsFilterSheetTitle` — Show conversations from
- `chatsHomeAllProjects` — All projects
- `chatsHomeClearFilters` — Clear filters
- `chatsHomeDone` — Done
- `chatsHomeEarlier` — Earlier
- `chatsHomeEmptyBody` — Start a conversation and it shows up here.
- `chatsHomeEmptyTitle` — No conversations yet
- `chatsHomeIncomplete` — Some conversations couldn't load
- `chatsHomeNeedsYou` — Needs you
- `chatsHomeNeedsYouCount` — Needs you · {count}
- `chatsHomeNewChat` — New conversation
- `chatsHomeNoMatchBody` — Nothing fits the filters you chose.
- `chatsHomeNoMatchTitle` — No matching conversations
- `chatsHomeOpenFailed` — This conversation couldn't be opened. Pull down to refresh the list.
- `chatsHomeOpening` — Opening…
- `chatsHomeOtherFolders` — Other folders
- `chatsHomeProjectEmptyTitle` — No conversations in {project}
- `chatsHomeRunning` — Running
- `chatsHomeStartChat` — Start a conversation
- `chatsHomeStartChatIn` — Start a conversation in {project}
- `chatsHomeStillLoading` — Loading {agents} conversations…
- `chatsHomeToday` — Today
- `chatsHomeUnreachable` — {servers} isn't answering. Its conversations aren't shown.
- `chatsNewAgentNeedsProjectsFolder` — {agent} works only in the projects on this phone. Choose one of them.
- `chatsNewChooseProject` — Choose a project
- `chatsNewFailed` — The conversation didn't start. Your message is still here.
- `chatsNewNeedProject` — Choose a project to start a conversation.
- `chatsNewPrompt` — What should we work on?
- `chatsSourcesAgents` — Agents on this phone
- `chatsSourcesAll` — All connections
- `chatsSourcesAlways` — Always shown
- `chatsSourcesElsewhere` — On another computer
- `chatsSourcesMain` — New conversations start here
- `chatsSourcesOnPhone` — On this phone
- `chatsSourcesSheetTitle` — Connections in this list
- `chatsSourcesSome` — {shown} of {total} connections
- `chatsSourcesUnreachable` — Isn't answering
- `libraryTerminalTitle` — Terminal
- `readerUiChanges` — Changes
- `readerUiFiles` — Files
- `refreshRetry` — Try again
- `workCount` — Tasks · {count} running

### Composer: write, send, stop, attach, queue

- `agentCardComposerHint` — Or type your answer
- `chatDraftCopy` — Copy draft
- `chatStripBackground` — Background
- `chatStripContextPending` — Context pending
- `chatSubagentReadOnly` — This sub-agent answers only its main conversation. Write there.
- `chatUi1ReferenceIsAddedAsTextWhen` — 1 reference is added as text when you send. Not saved with your draft.
- `chatUiAddAnImageOrFileToThe` — Add an image or file to the prompt
- `chatUiAskOpenCode` — Ask OpenCode…
- `chatUiAttachFile` — Attach file
- `chatUiAttachedCount` — {count} attached
- `chatUiAvailableWhenTheCurrentRunFinishes` — Available when the current run finishes
- `chatUiCancelAndReturnToTheComposer` — Cancel and return to the composer
- `chatUiCancelMessage` — Cancel message
- `chatUiCancelThisPendingMessage` — Cancel this pending message?
- `chatUiCommandUnavailable` — /{command} is not available right now.
- `chatUiDelegateThisPrompt` — Delegate this prompt
- `chatUiDiscardDraft` — Discard draft
- `chatUiDiscardQueuedDraft` — Discard queued draft?
- `chatUiEditDraft` — Edit draft
- `chatUiItsTextReturnsToTheComposerAs` — Its text returns to the composer as a draft.
- `chatUiKeepItPending` — Keep it pending
- `chatUiKeepItQueued` — Keep it queued
- `chatUiOpenCodeIsReconnectingTryAgainWhenThe` — OpenCode is reconnecting. Try again when the server is online.
- `chatUiPromptTools` — Prompt tools
- `chatUiQueuedWillSendWhenReconnected` — Queued — will send when reconnected
- `chatUiQueuedWithEviction` — Queued — will send when reconnected. {detail}
- `chatUiRecordsAndTranscribesOnThisDevice` — Records and transcribes on this device
- `chatUiReferencesAttachedNotice` — {count, plural, one{1 reference is added as text when you send. Not saved with your draft.} other{{count} references are added as text when you send. Not saved with your draft.}}
- `chatUiSendNowAndSteerInstead` — Send now and steer instead
- `chatUiSlashCommandsAndAgents` — Slash commands and agents
- `chatUiThisDraftHasNotBeenSentTo` — This draft has not been sent to OpenCode.
- `chatUiThisDraftIsTooLargeToQueue` — This draft is too large to queue, or the queue is full of newer drafts. Remove an attachment, or clear queued prompts in Settings.
- `chatUiVoiceInput` — Voice input
- `chatUiWaitForThisRunInstead` — Wait for this run instead
- `codexOfflineDraftSaved` — Reconnect before sending. Your draft is kept on this device.
- `codexReconnectBeforeSending` — Reconnect before sending.
- `composerBusyReason` — Getting your prompt ready…
- `composerClearTextSubtitle` — Keeps attachments · Undo available
- `composerClearTextTitle` — Clear draft text
- `composerDraftBlockedReason` — Answer the question about this draft first
- `composerFieldLabel` — Message to the agent
- `composerReturnedToDraft` — Returned to your draft
- `composerReuseSubtitle` — Reuse text from this conversation and recent sends
- `composerReuseTitle` — Reuse a prompt
- `composerToolCommandsTitle` — Commands and agents
- `composerToolNothingToSave` — Type or attach something first
- `composerToolSaveForLater` — Save prompt for later
- `composerToolSavedSubtitle` — Put a prompt you saved back in the draft
- `composerToolsAgentPicturesOnly` — {agent} takes pictures only: use Photo library or Take photo
- `composerToolsAgentTextOnly` — {agent} takes text only here
- `composerToolsMore` — More tools
- `composerToolsTextOnly` — This server takes text only
- `draftAttachmentsLocal` — Attachments save with this draft on this device.
- `draftRetrySave` — Try saving draft again
- `draftUnsaved` — Unsaved
- `kitAttachmentFile` — File, {label}
- `kitAttachmentFolder` — Folder, {label}
- `kitAttachmentImage` — Image, {label}
- `kitAttachmentOpen` — Preview {label}
- `kitAttachmentReference` — Reference, {label}
- `kitChipRemove` — Remove {label}
- `kitComposerAddToTurn` — Add to this turn
- `kitComposerAddToTurnShort` — Add to this turn
- `kitComposerCannotSendYet` — You can send when this reply finishes
- `kitComposerDeliveryLabel` — When to send
- `kitComposerEditor` — Open full-screen editor
- `kitComposerField` — Message
- `kitComposerOffline` — Offline · sends when you're back online
- `kitComposerRailRetry` — Try again
- `kitComposerSend` — Send
- `kitComposerSendAfter` — Send after this reply
- `kitComposerSendAfterShort` — Send after
- `kitComposerSendOffline` — Send when back online
- `kitComposerSending` — Sending
- `kitComposerSendsAfter` — Sends after this reply
- `kitComposerStop` — Stop the reply
- `kitComposerTools` — Attach and more
- `kitComposerVoice` — Talk instead of typing
- `kitModelActions` — Model shortcuts
- `kitModelChange` — Change model
- `kitModelChoose` — Choose a model
- `kitModelServerDefault` — Server default
- `kitModelSignIn` — Sign in to a model
- `kitSuggestionsLabel` — Suggestions
- `kitSuggestionsShowAll` — Show all
- `kitWorking` — Working
- `messageViewSendAgain` — Send this message again
- `modelServerDefault` — Server default
- `photoAddToDraft` — Add recovered photo to draft
- `photoCameraAction` — Take photo
- `photoDiscard` — Discard pending photo
- `photoLibraryAction` — Photo library
- `photoLibraryDescription` — Choose a photo or screenshot
- `photoPendingTitle` — Pending photo
- `promptOriginalDraft` — Restore original draft
- `promptStashDescription` — Save text, attachments and references for later
- `promptStashTitle` — Saved prompts
- `queueRemoveFailed` — Could not remove this draft from device storage. It is still queued. Check available storage and try again.
- `queueSaveFailed` — Could not save the queued draft on this device. Your text is still here. Check available storage and try again.
- `queuedDiscardUnconfirmedMessage` — Its earlier send was never confirmed; it may already be in the conversation.
- `queuedKeepForReview` — Keep for review
- `queuedResendConfirm` — Send again
- `queuedResendMessage` — It may already have reached OpenCode. Sending again can duplicate it.
- `queuedResendTitle` — Send this draft again?
- `queuedRetry` — Try again
- `queuedRetryAll` — {count, plural, other{Try all {count} again}}
- `voiceConversationTitle` — Voice conversation

### Turn status lines and transcript rows

- `chatErrorContentFilter` — The provider's safety filter stopped this reply.
- `chatErrorContextOverflow` — This conversation is too long for the model.
- `chatErrorModelNotFound` — The server doesn't have this model.
- `chatErrorOutputLength` — The reply reached the model's length limit.
- `chatErrorProviderAuth` — The model provider needs you to sign in again.
- `chatErrorUnknown` — The agent stopped because of an error.
- `chatLoadFailedBody` — Nothing is lost. Try again when OpenCode answers.
- `chatLoadFailedTitle` — Couldn't open this conversation
- `chatSendFailed` — Your message wasn't sent
- `chatSendFailedKept` — It's back in the message box.
- `chatUiAgent` — Agent
- `chatUiAttachment` — Attachment
- `chatUiBackgroundCancelled` — Cancelled
- `chatUiBackgroundComplete` — Completed
- `chatUiBackgroundError` — Failed
- `chatUiBackgroundResult` — Background result
- `chatUiChooseAnotherModel` — Choose another model
- `chatUiChooseModel` — Choose model
- `chatUiCompactAgain` — Compact again
- `chatUiCompactingConversation` — Compacting conversation…
- `chatUiCompactionFailed` — Compaction failed
- `chatUiCompactionFailedHint` — The conversation is still too long for the model, so the next turn will try again.
- `chatUiContextAdded` — Context added
- `chatUiContextCompacted` — Earlier messages were summarized to save space
- `chatUiDelegatedSession` — Delegated conversation
- `chatUiDetails` — Details
- `chatUiDismissPromptError` — Dismiss prompt error
- `chatUiDraftsQueued` — {count, plural, one{1 draft queued to send on reconnect.} other{{count} drafts queued to send on reconnect.}}
- `chatUiModel` — Model
- `chatUiMoved` — Moved
- `chatUiOpenParentSession` — Open parent conversation
- `chatUiResultOpenChild` — Open subagent conversation
- `chatUiSendPromptAgain` — Send again
- `chatUiSubagentCount` — Delegated conversation · {count}
- `chatUiSystemUpdate` — System update
- `chatUiUseModelAndResend` — Use {model} and resend
- `freeModelNotice` — Using OpenCode's free model — it's slower. Add an API key from your provider to use your own.
- `freeModelSignIn` — Add an API key
- `kitComposerPillNoAnswer` — No answer yet
- `kitMore` — More
- `kitSheetDismiss` — Dismiss
- `kitTurnActions` — Reply actions
- `kitTurnCopy` — Copy reply
- `kitTurnInterrupted` — The connection dropped before this reply finished.
- `kitTurnLiveFirstWordSlow` — Waiting for the model's first word
- `kitTurnLiveFirstWordSlowTeam` — AI Team is also working on this phone, so replies may be slower
- `kitTurnLiveFor` — {status} · {elapsed}
- `kitTurnLiveMinutes` — {minutes} min {seconds} s
- `kitTurnLiveNow` — {status}…
- `kitTurnLiveSeconds` — {seconds} s
- `kitTurnLiveSending` — Sending
- `kitTurnLiveServerQuiet` — The server has not answered yet
- `kitTurnLiveThinking` — Thinking
- `kitTurnLiveWaitingForServer` — Waiting for the server
- `kitTurnLiveWaitingForYou` — Waiting for you
- `kitTurnLiveWorking` — Working
- `kitTurnLiveWriting` — Writing
- `kitTurnMore` — More for this reply
- `kitTurnReconnecting` — Connection lost. Reconnecting to get the rest of this reply.
- `kitTurnStarting` — Starting the model…
- `kitTurnStillStarting` — Still waiting for the model · {seconds} s
- `kitTurnStopped` — You stopped this reply.
- `kitWorkDelegated` — delegated {count, plural, =1{1 task} other{{count} tasks}}
- `kitWorkDidntFinish` — Didn't finish
- `kitWorkEarlierSteps` — Show {count, plural, =1{1 earlier step} other{{count} earlier steps}}
- `kitWorkEdited` — edited {count, plural, =1{1 file} other{{count} files}}
- `kitWorkFetched` — fetched {count, plural, =1{1 page} other{{count} pages}}
- `kitWorkHideSteps` — Hide steps
- `kitWorkListed` — listed {count, plural, =1{1 folder} other{{count} folders}}
- `kitWorkNotRun` — {count, plural, =1{1 not run} other{{count} not run}}
- `kitWorkOther` — {count, plural, =1{1 other step} other{{count} other steps}}
- `kitWorkRan` — ran {count, plural, =1{1 command} other{{count} commands}}
- `kitWorkRead` — read {count, plural, =1{1 file} other{{count} files}}
- `kitWorkSearched` — searched {count, plural, =1{once} other{{count} times}}
- `kitWorkSteps` — {count, plural, =1{1 step} other{{count} steps}}
- `kitWorkStopped` — Stopped
- `kitWorkWaitingForYou` — Waiting for you
- `kitWorkWorking` — Working
- `revertReview` — Review
- `revertStaged` — Revert staged
- `undonePutBack` — Put back
- `undoneStatus` — Undone from a prompt
- `workExitCode` — Exit code {code}

### Permission and question cards, approval modes

- `activityAgentNeedsInput` — {agent} needs input
- `activityAnswerEveryQuestion` — Answer every question first.
- `activityOwnAnswer` — Or write your own answer
- `activityQuestionOptional` — Optional. You can leave this one empty.
- `activityQuestionProgress` — Question {index} of {total}
- `activitySendOffline` — Reconnect to the server to answer.
- `activitySending` — Sending…
- `approvalModeAskDetail` — You answer each request.
- `approvalModeAskTitle` — Ask first
- `approvalModeAutoDetail` — Allowed once, as they arrive.
- `approvalModeAutoTitle` — Auto-approve this conversation
- `approvalModeChange` — Change approval mode
- `approvalModeConfirmEverythingAction` — Approve everything
- `approvalModeConfirmEverythingBody` — Agents on this server will run commands and change files without asking, in every conversation.
- `approvalModeConfirmEverythingTitle` — Approve everything?
- `approvalModeEverythingAgentDetail` — Every {agent} conversation on this phone.
- `approvalModeEverythingDetail` — Every conversation on this server.
- `approvalModeEverythingTitle` — Approve everything
- `approvalModeMenuLabel` — Approval mode
- `approvalModeNowAsk` — This conversation asks first.
- `approvalModeNowAuto` — This conversation approves automatically.
- `approvalModeNowEverything` — Approving everything on this server.
- `approvalModeSettings` — Approval settings…
- `approvalsSheetFootnote` — Stops when the app disconnects. Server deny rules still apply.
- `approvalsSheetHistory` — Approved automatically
- `approvalsSheetSetByParent` — Set by {name}
- `approvalsSheetSetByServer` — Set by this server
- `approvalsSheetSubagents` — Subagents follow this
- `approvalsUiAutoApproved` — Auto-approved · {action}
- `approvalsUiFailedDetail` — Automatic approval failed. Review this request.
- `approvalsUiFollowParent` — Follow parent again
- `approvalsUiIndicatorOn` — Approving automatically
- `approvalsUiIndicatorPaused` — Auto-approval paused
- `approvalsUiInheritedFrom` — Inherited from parent conversation
- `approvalsUiMenu` — Approvals
- `approvalsUiPausedDetail` — This phone is not connected. Automatic approval resumes when it reconnects.
- `chatRequestAlwaysInProject` — in this project
- `chatRequestAlwaysOn` — Always allowed
- `chatRequestAlwaysScope` — From now on, {patterns} runs without asking you, {context}. You can take this back in Settings under Always allowed actions.
- `chatRequestAlwaysTitle` — Always allow these requests
- `chatRequestAnswerAllowed` — Allowed
- `chatRequestAnswerRejected` — Rejected
- `chatRequestDetailPatterns` — Requested patterns
- `chatRequestDetailTool` — Tool
- `chatRequestIfIgnored` — The agent waits until you answer. Nothing is lost.
- `chatRequestMoreWaiting` — {count, plural, one{1 more request is waiting.} other{{count} more requests are waiting.}}
- `chatRequestNoConnection` — Not connected to the server, so this can’t be answered here.
- `chatRequestOtherAnswer` — Something else
- `chatRequestOtherField` — Your answer
- `chatRequestWho` — The agent
- `chatStripApprovalAsk` — Asks first
- `chatStripAutoApprove` — Auto-approve
- `chatStripAutoApprovePaused` — Auto-approve paused
- `chatUiAlwaysAllow` — Always allow
- `chatUiAlwaysAllowWouldAlsoCover` — Always allow would also cover
- `chatUiOpenCodeNeedsInput` — OpenCode needs input
- `chatUiPendingChange` — Pending change
- `chatUiPermissionNeeded` — Permission needed: {title}
- `chatUiQuestionLabel` — Question: {title}
- `chatUiQuestionsSummary` — {question} · {count, plural, one{1 question} other{{count} questions}}
- `chatUiRateLimitCountdown` — Rate limited. Retrying{attempt} in {time}
- `chatUiRateLimitRetry` — Rate limited. Retrying{attempt}…
- `chatUiRetryingCountdown` — Retrying{attempt} in {time}
- `chatUiRetryingSoon` — Retrying{attempt}…
- `chatUiTellTheAgentWhyOrWhatTo` — Tell the agent why, or what to do instead (optional)
- `e7WorkspaceDismissDetail` — OpenCode will continue without answers to these questions.
- `e7WorkspaceDismissRequest` — Dismiss this request?
- `e7WorkspaceNeedsInput` — OpenCode needs input
- `e7WorkspaceSendAnswers` — Send answers
- `kitRequestAllowOnce` — Allow once
- `kitRequestReject` — Reject
- `workspaceDismissNotice` — Dismiss

### Model and agent picker; agents on this phone

- `agentsCancelSetup` — Cancel setup
- `agentsChecking` — Looking for agents on this phone…
- `agentsChipCheck` — Check
- `agentsChipResume` — Resume
- `agentsChipSignIn` — Sign in
- `agentsChooseTitle` — Choose an agent
- `agentsDone` — Done
- `agentsInstallAction` — Install {agent}
- `agentsInstallHint` — Install
- `agentsInstalling` — Installing {agent}…
- `agentsLimitUnknown` — {agent} plan limit reached · try again later
- `agentsModelDefault` — Default model
- `agentsModelLoading` — Reading the models…
- `agentsModelTitle` — Choose a model
- `agentsRemoveAction` — Remove {agent}
- `agentsRemoving` — Removing {agent}…
- `agentsResumeAction` — Resume {agent}
- `agentsSectionTitle` — Agents
- `agentsSetupBody` — {agent} runs on this phone. Installing downloads the agent and anything it needs.
- `agentsSetupTitle` — Set up {agent} · {size}
- `agentsSetupTitleNoSize` — Set up {agent}
- `agentsSignInAction` — Sign in to {agent}
- `agentsSignInAgain` — Sign in again
- `agentsSignInStart` — Sign in with {agent}
- `agentsSignInTitle` — Sign in with {agent}
- `agentsSignedIn` — Signed in
- `agentsSignedInAs` — Signed in as {account}
- `agentsStateChecking` — Checking sign-in…
- `agentsStateLimit` — Plan limit reached
- `agentsStateNeedsArm` — Needs a 64-bit phone
- `agentsStateNotInstalled` — Not installed · {size}
- `agentsStateNotInstalledNoSize` — Not installed
- `agentsStateReady` — Ready
- `agentsStateSignInNeeded` — Sign in needed
- `agentsStateStopped` — Stopped in the background
- `agentsStateUnavailable` — Not available on this phone yet
- `agentsStepConnection` — Connection
- `agentsStepInstall` — Installed
- `agentsStepReady` — Ready
- `agentsStepVersion` — Version
- `agentsStoppedLine` — {agent} stopped in the background
- `chatUiOpenProviders` — Open providers
- `e7ModelUiAgent` — Agent
- `e7ModelUiAllProviders` — All providers
- `e7ModelUiAnyCapability` — Any capability
- `e7ModelUiBrowseAll` — Browse all models
- `e7ModelUiClearFilters` — Clear filters
- `e7ModelUiClose` — Close model selector
- `e7ModelUiDefault` — Default
- `e7ModelUiFavorite` — Favorite {model}
- `e7ModelUiFavoritesEmpty` — Keep your go-to models here
- `e7ModelUiFavoritesFailed` — Could not save favorites. Try again.
- `e7ModelUiFavoritesHint` — Tap the star beside any model to find it here.
- `e7ModelUiLargestContext` — Largest context
- `e7ModelUiLoadFailed` — Could not load models
- `e7ModelUiLoading` — Loading model catalog
- `e7ModelUiNoMatches` — No matching models
- `e7ModelUiNoMatchesHint` — Try another search, provider, or capability filter.
- `e7ModelUiReasoning` — Reasoning
- `e7ModelUiRecentEmpty` — Your next choice starts here
- `e7ModelUiRecentHint` — Models you use will appear here, most recent first.
- `e7ModelUiRefresh` — Refresh models
- `e7ModelUiRetry` — Try again
- `e7ModelUiServerDefault` — Server default
- `e7ModelUiUnfavorite` — Remove {model} from favorites
- `e7ModelUiUseModelMode` — Use {model} · {agent}
- `e7ModelUiUseNewSessions` — Use for new conversations
- `e7ModelUiUseSession` — Use for this conversation
- `modelAll` — All models
- `modelChooseTitle` — Choose a model
- `modelEffortExtraHigh` — Extra high
- `modelEffortHigh` — High
- `modelEffortLow` — Low
- `modelEffortMax` — Max
- `modelEffortMedium` — Medium
- `modelEffortMinimal` — Minimal
- `modelEffortNone` — No thinking
- `modelFavorites` — Favorites
- `modelNewBadge` — New
- `modelNextFavorite` — Next favorite model
- `modelPickerAgentBuild` — Edits files and runs commands
- `modelPickerAgentBuildName` — Build
- `modelPickerAgentChip` — Agent: {agent}
- `modelPickerAgentPlan` — Reads and plans; does not change files
- `modelPickerAgentPlanName` — Plan
- `modelPickerCanReadAttachments` — Reads images and files you attach
- `modelPickerCanThink` — Thinks before answering
- `modelPickerCanUseTools` — Uses tools
- `modelPickerChooseFirst` — Choose a model first.
- `modelPickerCollections` — Which models to show
- `modelPickerInUse` — In use
- `modelPickerShowMore` — {count, plural, =1{Show 1 more model} other{Show {count} more models}}
- `modelPickerSignInBody` — No provider on this server has models yet. Sign in to one, then come back to choose a model.
- `modelPickerSignInTitle` — Provider sign-in needed
- `modelPickerThinking` — Thinking
- `modelPickerThinkingChip` — Thinking: {level}
- `modelPickerUnavailableReason` — Not available on this server right now.
- `modelPickerUseChosenModel` — Use model
- `modelPickerUseModel` — Use {model}
- `modelRecent` — Recent
- `modelSearchHint` — Search models
- `modelSelectionSaving` — Saving conversation selection…
- `modelSessionScopeNote` — Applies to this conversation's next turns.
- `modelSwitchSession` — Switch model for this conversation
- `pickerAddKeyFor` — Add an API key for {name}
- `pickerAddKeyNotConnectedHint` — Its models are not in this list yet
- `pickerConnectProvider` — Connect a provider
- `pickerConnectProviderHint` — Add an API key or sign in to use its models
- `pickerFreeOnlyNote` — Only OpenCode's free model is available — it's slower.
- `pickerProviderReady` — {name} is ready. Its models are in the list.
- `pickerSignInHint` — Opens the sign-in choices for this server
- `pickerSignInTo` — Sign in to {name}

### Shared kit words the screens above render

- `kitChoiceCurrent` — Current
- `kitChoiceOtherSend` — Send answer
- `kitChoiceRecommended` — Recommended
- `kitConfirmCancel` — Cancel
- `kitConfirmFailed` — That didn't finish. You can try again.
- `kitCopied` — Copied
- `kitCopy` — Copy
- `kitCopyDetails` — Copy details
- `kitDetailsHide` — Hide details
- `kitDiscardBody` — What you changed here isn't saved. Discarding it can't be undone.
- `kitDiscardConfirm` — Discard changes
- `kitDiscardTitle` — Discard your changes?
- `kitJumpLatest` — Jump to latest
- `kitJumpNewLatest` — {count, plural, =1{1 new · Jump to latest} other{{count} new · Jump to latest}}
- `kitMarkDone` — Done
- `kitMarkFailed` — Failed
- `kitMarkPaused` — Paused
- `kitMarkWaiting` — Waiting
- `kitMarkWorking` — Working
- `kitMenu` — Menu
- `kitNeedsYouReasonBlocked` — Stuck: needs you
- `kitNeedsYouReasonConsent` — Needs your OK
- `kitNeedsYouReasonDecision` — Needs your decision
- `kitNeedsYouSpan` — {count, plural, =1{Needs you · } other{{count} need you · }}
- `kitNeedsYouWaiting` — waiting {age}
- `kitNeedsYouWaitingSpoken` — {minutes, plural, =0{waiting less than a minute} =1{waiting 1 minute} other{waiting {minutes} minutes}}
- `kitNeedsYouWhoOnServer` — {who} on {server}
- `kitReceiptAnsweredElsewhere` — Answered on {where}
- `kitReceiptAnsweredElsewhereUnknown` — Answered on another device
- `kitReceiptConfirmed` — Done
- `kitReceiptNotConfirmed` — Not confirmed yet
- `kitReceiptRefused` — Not accepted
- `kitReceiptSending` — Sending…
- `kitReceiptSent` — Sent
- `kitReportProblem` — Report a problem
- `kitRequestAge` — waiting {age}
- `kitRequestAnswer` — Answer
- `kitRequestApprove` — Approve
- `kitRequestChooseOneReason` — Choose at least one answer.
- `kitRequestExpired` — Expired · the agent stopped waiting
- `kitRequestMoreAnswers` — {count, plural, one{1 more answer} other{{count} more answers}}
- `kitRequestReplyEmptyReason` — Type a reply first.
- `kitRequestSend` — Send
- `kitRequestSendBack` — Send back
- `kitRiskNotNow` — Not now
- `kitRiskTurnOff` — Turn off
- `kitRiskTurnOn` — Turn on
- `kitSheetClose` — Close
- `kitSheetLoading` — Loading
- `kitTaskNeedsYou` — Needs you
- `kitTaskStopped` — Stopped
- `kitTopBarBack` — Back
- `kitTopBarClose` — Close
- `kitTopBarMore` — More actions
- `kitTopBarSearch` — Search
- `kitTopBarSwitchProject` — Switch project
- `kitTopBarSwitchServer` — Switch server
- `kitTryAgain` — Try again
- `kitUndoAction` — Undo
- `kitUntilConversation` — For this conversation
- `kitUntilHour` — For an hour
- `kitUntilOff` — Until I turn it off


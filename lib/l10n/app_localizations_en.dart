// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get servicesTitle => 'Development services';

  @override
  String get servicesCopy => 'Copy command';

  @override
  String get servicesSubtitle => 'Project commands, logs, and preview links';

  @override
  String get servicesIntro =>
      'Keep your project\'s development commands and preview links together. Saving a service does not start it.';

  @override
  String get servicesAdd => 'Register service';

  @override
  String get servicesName => 'Service name';

  @override
  String get servicesCommand => 'Development command';

  @override
  String get servicesCommandHint =>
      'Use a foreground command, such as npm run dev. Background or detached commands cannot be tracked.';

  @override
  String get servicesUrl => 'Preview URL (optional)';

  @override
  String get servicesUrlHint =>
      'Use an address this phone can reach. localhost points to this phone. No ports are exposed or forwarded for you.';

  @override
  String get servicesSave => 'Save service';

  @override
  String get servicesUnavailable =>
      'This server cannot start and track development commands. You can save commands and review their preview links here.';

  @override
  String get servicesScopeChanged =>
      'The server or project changed. Reopen Development services from the intended project.';

  @override
  String get servicesNotStarted => 'Not started';

  @override
  String get servicesRunning => 'Running command';

  @override
  String get servicesStopped => 'Stopped';

  @override
  String get servicesUnknown => 'Status unknown';

  @override
  String get servicesStatusHint =>
      'Command status does not confirm that your app is ready or reachable.';

  @override
  String get servicesStart => 'Start';

  @override
  String get servicesStop => 'Stop';

  @override
  String get servicesRestart => 'Restart';

  @override
  String get servicesLogs => 'Logs';

  @override
  String get servicesVisit => 'Visit';

  @override
  String get servicesRemove => 'Remove configuration';

  @override
  String get servicesStopHint =>
      'Stop this service\'s tracked command? The server also removes its retained logs. Other commands are not affected.';

  @override
  String get servicesRestartHint =>
      'Stop this tracked command, remove its server log, then start the saved command again?';

  @override
  String get servicesForget => 'Forget last run';

  @override
  String get servicesForgetHint =>
      'Clear the local run record? This does not stop any server process. Starting again may create a duplicate if the previous command is still running.';

  @override
  String get servicesUnknownHint =>
      'The last run could not be confirmed. Refresh to reconcile it before starting again.';

  @override
  String get servicesLogEmpty => 'No captured output is available yet.';

  @override
  String get servicesWorking => 'Updating service…';

  @override
  String servicesExit(int code) {
    return 'Recorded exit code: $code';
  }

  @override
  String get servicesRefresh => 'Refresh status';

  @override
  String get isolatedTaskScopeChanged =>
      'The server or project changed while this was open. Close it and start again from the project you want.';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryModelsAgentsTitle => 'Models & agents';

  @override
  String get libraryProvidersTitle => 'Providers';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => 'Commands & tools';

  @override
  String get libraryTerminalTitle => 'Terminal';

  @override
  String get librarySettingsTitle => 'Settings';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'Signing certificate SHA-256';

  @override
  String get modelSwitchSession => 'Switch model for this conversation';

  @override
  String get modelNextFavorite => 'Next favorite model';

  @override
  String get modelChooseTitle => 'Choose a model';

  @override
  String get modelTitleCompact => 'Models';

  @override
  String get modelSearchHint => 'Search models';

  @override
  String get modelAll => 'All models';

  @override
  String get modelFavorites => 'Favorites';

  @override
  String get modelRecent => 'Recent';

  @override
  String get modelOptions => 'Options';

  @override
  String get modelThinkingMode => 'Thinking mode';

  @override
  String get modelSessionScopeNote =>
      'Applies to this conversation\'s next turns.';

  @override
  String get modelSelectionLoading => 'Loading conversation selection…';

  @override
  String get modelServerDefault => 'Server default';

  @override
  String get modelSelectionSaving => 'Saving conversation selection…';

  @override
  String get modelUnavailableSelection =>
      'The conversation\'s model is unavailable in this catalog. Refresh models or choose another.';

  @override
  String get modelScopeChanged =>
      'The server changed. Reopen the model selector to continue.';

  @override
  String get commonClearSearch => 'Clear search';

  @override
  String get workTitle => 'Tasks';

  @override
  String get workRefresh => 'Refresh';

  @override
  String get workRetry => 'Try again';

  @override
  String get workCancel => 'Cancel';

  @override
  String get workRunning => 'Running';

  @override
  String get workFinished => 'Finished';

  @override
  String get workTimedOut => 'Timed out';

  @override
  String get workStopped => 'Stopped';

  @override
  String get workUnknown => 'Status unavailable';

  @override
  String get workOutput => 'Command output';

  @override
  String get workNoOutput => 'Waiting for output…';

  @override
  String get workNoFinalOutput => 'This command produced no output.';

  @override
  String get workCopied => 'Output copied';

  @override
  String get workMoreOutput => 'Load more output';

  @override
  String get workTrimmed =>
      'Showing the most recent output. Earlier text was trimmed.';

  @override
  String get workStop => 'Stop command';

  @override
  String get workStopTitle => 'Stop this command?';

  @override
  String get workStopDescription =>
      'This stops the command and removes its saved output from the server. Text already loaded here stays visible until you close it.';

  @override
  String get workTimeout => 'Change timeout';

  @override
  String get workTimeoutDescription => 'The new timeout starts now.';

  @override
  String get workTimeoutOneMinute => '1 minute';

  @override
  String get workTimeoutFiveMinutes => '5 minutes';

  @override
  String get workTimeoutFifteenMinutes => '15 minutes';

  @override
  String get workTimeoutOneHour => '1 hour';

  @override
  String get workTimeoutNone => 'No timeout';

  @override
  String get workUnavailable =>
      'This command is no longer available. It may have been removed or cancelled when the server restarted.';

  @override
  String get workRestarted =>
      'The server restarted and this command is no longer available. Its loaded output is shown below.';

  @override
  String get workDisconnected =>
      'Reconnecting. Output will refresh when the server is available.';

  @override
  String get workContextChanged =>
      'The server or project changed. Close this view and reopen Running work.';

  @override
  String workCount(int count) {
    return 'Tasks · $count running';
  }

  @override
  String workExitCode(int code) {
    return 'Exit code $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => 'Clear draft text';

  @override
  String get composerClearTextSubtitle => 'Keeps attachments · Undo available';

  @override
  String get composerDraftCleared => 'Draft text cleared';

  @override
  String get composerReuseTitle => 'Reuse a prompt';

  @override
  String get queueSaveFailed =>
      'Could not save the queued draft on this device. Your text is still here. Check available storage and try again.';

  @override
  String get fileSave => 'Save';

  @override
  String get fileReload => 'Refresh';

  @override
  String get queueRemoveFailed =>
      'Could not remove this draft from device storage. It is still queued. Check available storage and try again.';

  @override
  String get composerReuseSubtitle =>
      'Reuse text from this conversation and recent sends';

  @override
  String get composerReuseSearch => 'Search recent prompts';

  @override
  String get composerReuseEmpty => 'No matching prompts';

  @override
  String get backgroundWorkTitle => 'Move running work to background';

  @override
  String get backgroundWorkShortcut =>
      'Continue this work while you use the conversation · Ctrl+B';

  @override
  String get backgroundWorkNoop => 'No foreground subagents to background.';

  @override
  String get backgroundWorkPromoted =>
      'Subagents are continuing in the background.';

  @override
  String get libraryNoModel => 'No model selected';

  @override
  String get chatAttachmentUnsupported =>
      'Images, PDFs, text files, and Excel or Word files (.xlsx, .docx) can be attached.';

  @override
  String get termuxRestartTitle => 'Restart the local server?';

  @override
  String get termuxRestartMessage =>
      'OpenCode will be briefly unavailable. The app will keep your current project and reconnect automatically.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count conversations are generating. Restarting will interrupt them.',
      one: '1 conversation is generating. Restarting will interrupt it.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'Restart';

  @override
  String get chatCopyCompleteReply => 'Copy complete reply';

  @override
  String get chatCopyReplySoFar => 'Copy reply so far';

  @override
  String commandRunTitle(String command) {
    return 'Run /$command';
  }

  @override
  String get commandDestination => 'Conversation';

  @override
  String get commandNewChat => 'New conversation';

  @override
  String get commandUntitledChat => 'Untitled conversation';

  @override
  String get commandArguments => 'Arguments (optional)';

  @override
  String get commandRun => 'Run';

  @override
  String get commandRunning => 'Starting…';

  @override
  String get commandLocationChanged =>
      'The server or project changed. Close this dialog and open the command again.';

  @override
  String get refreshFailed => 'Couldn’t refresh';

  @override
  String get refreshRetry => 'Try again';

  @override
  String get filesProjectRoot => 'Project root';

  @override
  String get globalSessionsLoadMore => 'Load more conversations';

  @override
  String get globalSessionsRefreshFailed => 'Could not refresh conversations.';

  @override
  String get workspaceSearchAllSessions => 'Search all conversations';

  @override
  String get workspaceProjectListUnavailable => 'Project list unavailable';

  @override
  String get workspaceRetryProjects => 'Try again';

  @override
  String get historyLoadOlder => 'Load older messages';

  @override
  String get historyReload => 'Refresh recent history';

  @override
  String get historyCursorExpired =>
      'Older history changed or expired. Refresh recent history to continue.';

  @override
  String get historyRefreshed =>
      'History refreshed. Older messages remain available above.';

  @override
  String get historyLoadedOnly =>
      'Only loaded messages are included. Load older history to include more.';

  @override
  String get historyLoadedTotals => 'Usage and loaded history';

  @override
  String get historyCopyLoadedReply => 'Copy loaded reply';

  @override
  String get historyLoadedMessages => 'Loaded messages';

  @override
  String get historyLoadedCost => 'Cost of loaded messages';

  @override
  String get historyServerTotalsNote =>
      'Rows marked reported by server cover the conversation. Message counts and other estimates cover loaded history.';

  @override
  String get sessionsDetailsUnavailable =>
      'Conversation details could not be loaded. Try again.';

  @override
  String get sessionsReload => 'Refresh recent conversations';

  @override
  String get revertReviewChanged =>
      'This conversation or its staged revert changed. Review the latest state before continuing.';

  @override
  String get revertReviewLatest => 'Review latest state';

  @override
  String get revertBusy =>
      'Wait for the current conversation action to finish.';

  @override
  String get revertClearAction => 'Clear staged revert';

  @override
  String get revertPreviewUnavailable =>
      'The server did not provide a file preview. This does not establish whether files changed.';

  @override
  String get revertStaged => 'Revert staged';

  @override
  String get revertReview => 'Review';

  @override
  String get revertFromHere => 'Undo from here';

  @override
  String get revertUndoDescription =>
      'Undo the last prompt and everything after it';

  @override
  String get revertClearShortDescription =>
      'Review and clear the staged revert';

  @override
  String get revertPromptUnavailable =>
      'The boundary prompt could not be loaded.';

  @override
  String get revertPromptLoading => 'Loading the boundary prompt…';

  @override
  String get revertAttachmentPrompt => 'Attachment-only prompt';

  @override
  String get revertResolveBeforeSending =>
      'Review the staged revert, then clear it or make it permanent before sending. Your draft is kept.';

  @override
  String get sessionNoteTitle => 'Note for the agent';

  @override
  String get sessionNoteDescription =>
      'Keep a short instruction for this conversation. Saving or deleting it takes effect at the next agent step and appears in the transcript then. It does not start a run.';

  @override
  String get sessionNoteHint =>
      'For example: Keep explanations brief and run the relevant checks before finishing.';

  @override
  String get sessionNoteSave => 'Save note';

  @override
  String get sessionNoteRemove => 'Delete saved note';

  @override
  String get sessionNoteSaved => 'Note saved';

  @override
  String get sessionNoteRemoved => 'Note deleted';

  @override
  String get sessionNotePending => 'Applies at the next agent step.';

  @override
  String get sessionInstructionsUpdated => 'Instructions updated';

  @override
  String get sessionInstructionsApplied =>
      'The agent\'s conversation instructions have been updated for this step.';

  @override
  String get sessionNoteUnsupported =>
      'This server does not support conversation notes.';

  @override
  String get sessionNoteAuthorization =>
      'Check this server\'s password and permissions, then try again. Your draft is kept.';

  @override
  String get sessionNoteChanged =>
      'The conversation or its instructions changed. Refresh the saved note before saving again. Your draft is kept.';

  @override
  String get sessionNoteInvalid =>
      'The saved note has a format this editor cannot safely change.';

  @override
  String get sessionNoteTooLarge =>
      'Shorten the note to fit the server\'s size limit.';

  @override
  String get sessionNoteBusy =>
      'A note change is already being saved. Try again when it finishes.';

  @override
  String get sessionNoteRefresh => 'Refresh saved note';

  @override
  String get sessionNoteSavedVersion =>
      'Current saved note — review before replacing';

  @override
  String get sessionNoteNone => 'No saved note';

  @override
  String get sessionNoteDiscard => 'Discard your note changes?';

  @override
  String get sessionNoteKeepEditing => 'Keep editing';

  @override
  String get sessionNoteDiscardAction => 'Discard changes';

  @override
  String get sessionNoteDiscardDetail =>
      'The edits you made to this note will be lost. This can\'t be undone.';

  @override
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit bytes';
  }

  @override
  String get usageTitle => 'Usage and cost';

  @override
  String get usageDescription =>
      'Activity recorded by this OpenCode server across your conversations.';

  @override
  String get usageRefresh => 'Refresh usage';

  @override
  String get usageToday => 'Today';

  @override
  String get usageThirtyDays => '30 days';

  @override
  String get usageYear => 'This year';

  @override
  String get usageAllTime => 'All time';

  @override
  String get usageScope => 'Project scope';

  @override
  String get usageAllProjects => 'All projects';

  @override
  String get usageCurrentProject => 'Current project';

  @override
  String get usageLoading => 'Loading usage';

  @override
  String get usageUnsupported =>
      'This server does not support aggregate usage.';

  @override
  String get usageProjectUnavailable =>
      'No current project could be identified. Choose All projects or open a project first.';

  @override
  String get usageTimezoneUnavailable =>
      'Could not read this device\'s timezone. Retry to load correctly dated usage.';

  @override
  String get usageRefreshInterrupted =>
      'The server changed while loading usage. Refresh to try again.';

  @override
  String get usageInvalidResponse =>
      'The server returned incomplete usage data. Refresh to try again.';

  @override
  String get usageAuthorization =>
      'Check this server\'s password and permissions, then refresh.';

  @override
  String get usagePreviousResult =>
      'Showing the previous result for these filters.';

  @override
  String get usageLocationChanged =>
      'The active server or project changed. Reopen Usage from Settings.';

  @override
  String get usageTinyCost => 'Less than \$0.000001';

  @override
  String get usageSessions => 'Conversations';

  @override
  String get usageSubagents => 'Subagent conversations';

  @override
  String get usagePrompts => 'Prompts';

  @override
  String get usageSteps => 'Agent steps';

  @override
  String get usageActiveDays => 'Active days';

  @override
  String get usageStreak => 'Longest streak · days';

  @override
  String get usageEmpty =>
      'No activity in this range. Try a wider range or All projects.';

  @override
  String get usageTokens => 'Tokens';

  @override
  String get usageTotalTokens => 'Total';

  @override
  String get usageInput => 'Input';

  @override
  String get usageOutput => 'Output';

  @override
  String get usageReasoning => 'Reasoning';

  @override
  String get usageCacheRead => 'Cache read';

  @override
  String get usageCacheWrite => 'Cache write';

  @override
  String get usageModels => 'Model usage';

  @override
  String get usageNoModels => 'No model usage was recorded in this range.';

  @override
  String get usageToolReliability => 'Tool reliability';

  @override
  String get usageToolsUnavailable =>
      'This response does not include tool reliability.';

  @override
  String get usageNoTools => 'No tool calls were recorded in this range.';

  @override
  String get usageNoFinishedTools => 'No finished tool calls yet.';

  @override
  String get usageToolCalls => 'Calls';

  @override
  String get usageSucceeded => 'Succeeded';

  @override
  String get usageFailed => 'Failed';

  @override
  String get usageUnfinished => 'Unfinished';

  @override
  String get usageCostDisclosure =>
      'Costs are estimates reported by OpenCode, not a provider invoice. Unfinished tool calls are excluded from the success rate.';

  @override
  String usagePeriod(String from, String to) {
    return '$from – $to';
  }

  @override
  String usageTimezone(String timezone) {
    return 'Timezone: $timezone';
  }

  @override
  String usageModelSteps(String steps) {
    return '$steps steps';
  }

  @override
  String usageModelTokens(String tokens) {
    return '$tokens tokens';
  }

  @override
  String usageSuccessRate(String rate) {
    return '$rate of finished calls succeeded';
  }

  @override
  String usageUpdated(String time) {
    return 'Updated at $time';
  }

  @override
  String get mcpRuntimeTitle => 'Until server restart';

  @override
  String get mcpDefaultLocation => 'OpenCode server’s default directory';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return 'Cloud environment: $workspace';
  }

  @override
  String get mcpLocationChanged =>
      'The server or project changed. Your draft is still here; reopen setup in the intended project before adding it.';

  @override
  String get mcpAdding => 'Adding MCP server';

  @override
  String get mcpAdd => 'Add MCP server';

  @override
  String get mcpRuntimeEmpty =>
      'Add tools for the current project until OpenCode restarts.';

  @override
  String get mcpRuntimeAdded => 'MCP server added for this project';

  @override
  String get mcpHeaderName => 'Header name';

  @override
  String get mcpHeaderValue => 'Header value';

  @override
  String get mcpAddHeader => 'Add another header';

  @override
  String get mcpRemoveHeader => 'Remove header';

  @override
  String get sessionUnread => 'Unread result';

  @override
  String get shareSessionViewsTitle => 'Sync read state';

  @override
  String get shareSessionViewsOn =>
      'Let your other OpenCode clients know which completed results you have viewed.';

  @override
  String get shareSessionViewsOff =>
      'Reading stays private to this device. Unread results use local read history.';

  @override
  String get shareSessionViewsSaveError =>
      'Could not save this preference. Read-state sharing is off on this device for now.';

  @override
  String get exportTitle => 'Export conversation';

  @override
  String get exportDescription =>
      'Choose a format to save this conversation on your device.';

  @override
  String get exportJson => 'Complete conversation · JSON';

  @override
  String get exportJsonDescription =>
      'Downloads the full conversation from the server, including older messages.';

  @override
  String get exportMarkdown => 'Readable transcript · Markdown';

  @override
  String get exportMarkdownDescription =>
      'Saves the messages currently loaded in this conversation. Load older messages first if you need them included.';

  @override
  String get exportRedact => 'Redact sensitive data';

  @override
  String get exportUnredacted =>
      'The unredacted file may contain secrets, local paths, and private tool output.';

  @override
  String get exportCancel => 'Cancel download';

  @override
  String get exportDownloading => 'Downloading complete conversation…';

  @override
  String get exportSaving => 'Saving file…';

  @override
  String get exportSaved => 'Conversation saved';

  @override
  String get exportChanged =>
      'The server or project changed. Reopen export from the intended conversation.';

  @override
  String get exportUnsupported =>
      'This server does not support JSON export. You can still save the loaded Markdown transcript.';

  @override
  String get exportAuthorization =>
      'The server denied access. Check your server credentials and try again.';

  @override
  String get exportMissing =>
      'This conversation no longer exists on the server. You can still save the loaded Markdown transcript.';

  @override
  String get exportFailed =>
      'Could not export the conversation. Check your connection and storage, then try again.';

  @override
  String get importTitle => 'Import conversation';

  @override
  String get importDescription =>
      'Restore a JSON export to this OpenCode server. Choose a file, then review where it will be imported.';

  @override
  String get importChoose => 'Choose JSON file';

  @override
  String get importChooseAnother => 'Choose another file';

  @override
  String get importAction => 'Import conversation';

  @override
  String get importUntitled => 'Untitled conversation';

  @override
  String get importRedacted =>
      'This file contains redacted placeholders. Import cannot recover the original text; use an unredacted export if you need it.';

  @override
  String importParent(String id) {
    return 'Parent conversation $id must already exist on this server. Import the parent first.';
  }

  @override
  String get importArchived =>
      'This conversation is archived. Import will keep its archived status.';

  @override
  String get importDestination => 'Import into';

  @override
  String get importChooseDestination => 'Choose a project on this server';

  @override
  String get importNoDestinations =>
      'No projects are available. Open a project on this server, then try again.';

  @override
  String get importDestinationFailed =>
      'Could not load destination projects or cloud environments. Try again; your file is still selected.';

  @override
  String get importPreserves =>
      'Your source file stays unchanged. Existing conversations are never replaced, and importing does not start an agent run.';

  @override
  String get importReading => 'Preparing import…';

  @override
  String get importSending => 'Importing conversation…';

  @override
  String get importSucceeded => 'Conversation imported';

  @override
  String get importOpen => 'Open conversation';

  @override
  String get importOpenFailed =>
      'The conversation was imported, but could not be opened. Find it in All conversations on the destination server.';

  @override
  String get importChanged =>
      'The server or project changed. Your file is still here. Reopen import on the intended server before continuing.';

  @override
  String get importUnsupported => 'This server does not support JSON import.';

  @override
  String get importInvalidFile =>
      'Choose a valid OpenCode JSON export with conversation information and message records. Markdown transcripts cannot be imported.';

  @override
  String get importTooLarge =>
      'This file exceeds the mobile import limit of 128 MiB. It has not been uploaded or truncated. Use a desktop or server transfer for this file.';

  @override
  String get importConflict =>
      'A conversation with this ID already exists on this server. Nothing was replaced. Find it in All conversations, or import this file on another server.';

  @override
  String get importAuthorization =>
      'The server denied access. Check your server credentials. Your file is still selected.';

  @override
  String get importParentMissing =>
      'The parent conversation is missing from this server. Import the parent first, then retry this file.';

  @override
  String get importRejected =>
      'The server rejected this export format. Your file is still selected; check that it came from a compatible OpenCode server.';

  @override
  String get importUnconfirmed =>
      'Import could not be confirmed. Check All conversations before retrying: the server may have received it. Your source file is unchanged.';

  @override
  String get sessionPin => 'Pin on this device';

  @override
  String get sessionUnpin => 'Unpin';

  @override
  String get sessionPinFailed =>
      'Could not save this pin. Check device storage and that the conversation’s project has not changed, then try again.';

  @override
  String get sessionPinsLoadFailed =>
      'Some pinned conversations could not be loaded. Refresh to try again.';

  @override
  String get promptStashSaveFailed =>
      'Could not save this prompt. Your composer is unchanged. Check device storage and try again.';

  @override
  String get promptOriginalDraft => 'Restore original draft';

  @override
  String get promptStashTitle => 'Saved prompts';

  @override
  String get promptStashSearch => 'Search saved prompts';

  @override
  String get promptStashDeleteFailed =>
      'Could not delete this saved prompt. Try again.';

  @override
  String get promptStashDelete => 'Delete';

  @override
  String get promptStashFull =>
      'Your stash has 50 prompts. Delete a saved prompt to make room; your current prompt is unchanged.';

  @override
  String promptStashAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count attachments',
      one: '1 attachment',
    );
    return '$_temp0';
  }

  @override
  String promptStashReferences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count references',
      one: '1 reference',
    );
    return '$_temp0';
  }

  @override
  String get promptHistorySaveFailed =>
      'Prompt sent, but its history could not be saved on this device.';

  @override
  String get promptStashMigrationPending =>
      'Some saved attachments could not be moved to local attachment storage yet. Your saved content has been kept. Free device storage and retry.';

  @override
  String get commonRetry => 'Try again';

  @override
  String get shareWaitingForServer =>
      'Connect to a server and the shared text opens in a new conversation.';

  @override
  String get webSourcesDisclosure =>
      'Web search is not available through this server’s app gateway. Paste a public URL and optionally an excerpt you want to include. No page is fetched. Nothing is sent to the model here.';

  @override
  String get webSourcesScopeChanged =>
      'Server changed. Close and reopen Add web source.';

  @override
  String get webSourcesUrl => 'Public URL';

  @override
  String get webSourcesLabel => 'Title (optional)';

  @override
  String get webSourcesExcerpt => 'Pasted excerpt (optional)';

  @override
  String get webSourcesExcerptHint =>
      'User-provided text, not verified page content.';

  @override
  String get digestStatusUnverified =>
      'Server reported idle. Success or failure is not verified.';

  @override
  String get digestChangedFilesUnknown => 'Changed files: unknown.';

  @override
  String digestChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count changed files in the conversation total; this run is unknown.',
      one: '1 changed file in the conversation total; this run is unknown.',
      zero: 'No changed files in the conversation total; this run is unknown.',
    );
    return '$_temp0';
  }

  @override
  String get digestPendingDecisionsUnknown => 'Pending decisions: unknown.';

  @override
  String digestPendingDecisions(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pending decisions in the current cache.',
      one: '1 pending decision in the current cache.',
      zero: 'No pending decisions in the current cache.',
    );
    return '$_temp0';
  }

  @override
  String get digestOutcomesUnknown =>
      'Tool outcomes and remaining tasks: unknown.';

  @override
  String get digestProvenance =>
      'Cached server metadata only. No AI summary or model call. Open the conversation to verify results and review changes or tasks.';

  @override
  String get digestOpenConversation => 'Open conversation';

  @override
  String get digestReview => 'Review next actions';

  @override
  String get digestCopy => 'Copy digest';

  @override
  String get digestCopySucceeded => 'Digest copied';

  @override
  String get digestCopyFailed => 'Could not copy digest';

  @override
  String get digestDismiss => 'Dismiss';

  @override
  String get digestRunResults => 'Run results';

  @override
  String get runResultsScopeChanged =>
      'The server or project changed. Close this view and reopen Run results from the intended project.';

  @override
  String get runResultsTitle => 'Run results';

  @override
  String get runResultsEmpty =>
      'The latest turn has no assistant step yet, so there is nothing to show.';

  @override
  String runResultsStarted(String time) {
    return 'Started $time';
  }

  @override
  String get runResultsStartedUnknown => 'Start time not recorded';

  @override
  String runResultsFinished(String time) {
    return 'Finished $time';
  }

  @override
  String get runResultsFinishedUnknown => 'Finish time not recorded';

  @override
  String get runResultsPartialHistory =>
      'The message that started this run was not found in the loaded history. Counts here are lower bounds and the run id is only the oldest loaded step.';

  @override
  String get runResultsOutcomeCompleted => 'Completed';

  @override
  String get runResultsOutcomeCutOff => 'Cut off by the provider';

  @override
  String get runResultsOutcomeFailed => 'Failed';

  @override
  String get runResultsOutcomeAborted => 'Aborted';

  @override
  String get runResultsOutcomeRunning => 'Still running';

  @override
  String get runResultsOutcomeNotReported => 'Outcome not reported';

  @override
  String runResultsFinishReason(String finish) {
    return 'Provider finish reason: $finish';
  }

  @override
  String get runResultsFinishReasonMissing =>
      'The provider gave no finish reason.';

  @override
  String runResultsEarlierErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count earlier steps reported errors; the newest step decides the outcome.',
      one:
          'An earlier step reported an error; the newest step decides the outcome.',
    );
    return '$_temp0';
  }

  @override
  String get runResultsObservedLive =>
      'This phone received the completion of the newest step live.';

  @override
  String get runResultsFromHistory =>
      'Recovered from server history. This phone did not observe the newest step complete.';

  @override
  String get runResultsNoToolEvidence =>
      'This run recorded no tool calls, so there is no file or command evidence. That is not the same as no changes.';

  @override
  String get runResultsChangedFilesTitle => 'Changed files';

  @override
  String get runResultsChangedFilesSource =>
      'From completed edit, write and patch tools in this run. Not a verified diff of the working tree.';

  @override
  String get runResultsNoChangedFiles =>
      'No completed file-changing tool in this run.';

  @override
  String get runResultsChangeEdited => 'Edited';

  @override
  String get runResultsChangeWritten => 'Written';

  @override
  String get runResultsChangePatched => 'Patched';

  @override
  String get runResultsCommandsTitle => 'Commands';

  @override
  String get runResultsCommandsSource =>
      'From bash and shell tools in this run. Exit codes appear only when the server recorded them.';

  @override
  String get runResultsNoCommands => 'No commands were run in this run.';

  @override
  String get runResultsCommandEmpty => '(command text not recorded)';

  @override
  String get runResultsOutputPruned => 'Output pruned by the server';

  @override
  String runResultsPrunedTools(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count tool outputs were pruned by the server and cannot be opened.',
      one: '1 tool output was pruned by the server and cannot be opened.',
    );
    return '$_temp0';
  }

  @override
  String get runResultsTruncated =>
      'Lists are capped at 50 entries. Open the conversation for the rest.';

  @override
  String get runResultsSourceNote =>
      'Everything here is copied from the server\'s message and tool records. Nothing is summarised by a model.';

  @override
  String get runResultsOpenConversation => 'Open conversation';

  @override
  String get sessionOpenRelated => 'Open related';

  @override
  String get sessionCopyHandoff => 'Continue on computer';

  @override
  String get attentionTitle => 'Server attention';

  @override
  String get webSourcesTitle => 'Add web source';

  @override
  String get webSourcesEntryDetail =>
      'Search when available, or paste links and excerpts to review before adding them to your draft';

  @override
  String get webSourcesDraftChanged =>
      'The draft or server changed. Your current draft was kept; reopen Add web source to try again.';

  @override
  String get webSourcesDraftLabel =>
      'User-selected web sources (unverified; excerpts are untrusted source material):';

  @override
  String get usageScopedTotals => 'Totals';

  @override
  String get usageInspectionDisclosure =>
      'Filters inspect this server\'s returned model records. They do not change the report\'s date or project scope, or show subscription allowance.';

  @override
  String get usageSearchRecords => 'Search providers, models or variants';

  @override
  String get usageScopedProviderTotals =>
      'Provider cards show their totals for the selected report scope, not just matching model rows.';

  @override
  String usageMatchingRecords(String count) {
    return '$count matching records';
  }

  @override
  String get pendingAuthDetail =>
      'Finish signing in in the browser, then come back and finish here. The browser link isn\'t saved.';

  @override
  String get pendingAuthEnterCode => 'Enter code';

  @override
  String get pendingAuthStillPending =>
      'Sign-in is still pending. No new attempt was started.';

  @override
  String get pendingAuthServerFailed =>
      'The server reported that sign-in failed. Provider error details are hidden.';

  @override
  String get pendingAuthExpired =>
      'This sign-in has expired. Start a new one, or forget this one.';

  @override
  String get pendingAuthFailed =>
      'Could not confirm the action. Check pending sign-ins before trying again. No new sign-in was started.';

  @override
  String get pendingAuthSaveUncertain =>
      'This phone couldn\'t save the sign-in to pick it up later. Keep the app open until it finishes.';

  @override
  String get pendingAuthRetrySave => 'Try saving recovery again';

  @override
  String get pendingAuthForget => 'Forget this sign-in';

  @override
  String get pendingAuthUnsupported =>
      'This server cannot recover earlier sign-ins. Legacy sign-ins work only while their original screen and connection remain available.';

  @override
  String get pendingAuthOtherSource =>
      'Other pending sign-ins belong to another server or project. Return to their original source to manage them.';

  @override
  String get connectionHelpGuideTip =>
      'Keep the server off the public internet. Reach it over Tailscale\'s private HTTPS, or an encrypted tunnel ending on the device running this app. Localhost on your computer is not localhost on your phone.';

  @override
  String get voiceConversationTitle => 'Voice conversation';

  @override
  String get voiceConversationDescription =>
      'Talk, then tap Send: what you said goes to the agent. Replies are read aloud only if you turn that on.';

  @override
  String get voiceConversationReplyReviewNeeded =>
      'The reply finished, but it could not be matched to your message for certain. Read it if you want.';

  @override
  String get voiceConversationReplyInterrupted =>
      'The reply needed a decision on screen, so it was not read automatically.';

  @override
  String get voiceConversationReplyNoProse =>
      'The reply has no prose to read. Code and tool details are not spoken.';

  @override
  String get voiceConversationReplyFailed =>
      'The reply could not be read aloud.';

  @override
  String get voiceConversationPausedDetail =>
      'Voice conversation is paused. Reconnect, wait for the reply, or review pending decisions on screen.';

  @override
  String get voiceConversationDraftFirst =>
      'Send, save, or clear your current draft before starting voice conversation.';

  @override
  String get voiceConversationCommandsOnly =>
      'Use the typed composer for slash commands.';

  @override
  String get voiceInputUnavailable =>
      'Voice input is unavailable. Check the local model and microphone settings.';

  @override
  String get desktopDropFailedTitle => 'Could not attach dropped files';

  @override
  String get desktopDropFailedRecovery =>
      'Check the attachments already added before trying again. You can also use the keyboard to open Add, then Attach file.';

  @override
  String get commandAuthMethodHint =>
      'Runs the provider\'s sign-in method on your selected server, not on this phone. You may need to finish interactive steps on the server.';

  @override
  String get commandAuthStart => 'Start server sign-in';

  @override
  String get commandAuthPending =>
      'Signing in on the server… Finish any steps it asks for there. Closing this doesn\'t stop it.';

  @override
  String get commandAuthCancel => 'Cancel sign-in';

  @override
  String get commandAuthFailed => 'Sign-in didn\'t finish.';

  @override
  String get commandAuthComplete => 'Signed in.';

  @override
  String get commandAuthExpired => 'Sign-in timed out before it finished.';

  @override
  String get commandAuthScopeChanged =>
      'You switched to another server or project. Go back to it to see this sign-in.';

  @override
  String get commandAuthUncertainStart =>
      'The server may have started signing in. Check on the server before you try again.';

  @override
  String get readAloudAction => 'Read reply prose';

  @override
  String get readAloudStop => 'Stop reading aloud';

  @override
  String get readAloudOtherVoice => 'Read with another voice';

  @override
  String get readAloudChooseVoice => 'Choose a reading voice';

  @override
  String get readAloudConsentTitle => 'Read replies aloud?';

  @override
  String get readAloudConsentDetail =>
      'Your phone\'s speech engine reads the reply aloud. Code and tool details are skipped. This phone remembers your answer.';

  @override
  String get readAloudContinue => 'Read aloud';

  @override
  String get readAloudUnsupported =>
      'Read-aloud is not available on this platform.';

  @override
  String get readAloudNoVoice =>
      'No installed voice marked offline is available. Configure an offline voice in your system speech settings and try again.';

  @override
  String get readAloudUnavailable =>
      'The speech engine could not read this reply. Try again or choose another voice.';

  @override
  String get readAloudTooLong =>
      'This reply is too long to read aloud. Choose a shorter reply.';

  @override
  String get readAloudBusy =>
      'Speech playback is unavailable while audio capture or another audio interruption is active.';

  @override
  String get readAloudNoProse =>
      'There is no reply prose to read. Code and tool details are not spoken.';

  @override
  String get credentialMetadataOnly => 'Keys stay on your server.';

  @override
  String get credentialActiveUpdated =>
      'Active account updated from the server.';

  @override
  String get credentialSwitchRequested =>
      'Switch requested. This request has not yet been confirmed by a server event.';

  @override
  String get credentialActive => 'Active';

  @override
  String get credentialRename => 'Rename account';

  @override
  String get credentialLabel => 'Account label';

  @override
  String get credentialSave => 'Save label';

  @override
  String get credentialScopeChanged =>
      'The server or project changed. Close and reopen account management before making changes.';

  @override
  String get credentialProviderMissing =>
      'This provider is no longer in the server\'s integration list.';

  @override
  String get credentialLoadFailed =>
      'Could not refresh saved accounts. Try again.';

  @override
  String get credentialMutationFailed =>
      'Could not confirm the account change. Refresh before retrying; the server may already have applied it.';

  @override
  String get credentialRefresh => 'Refresh accounts';

  @override
  String get credentialEmpty =>
      'No saved accounts were reported for this provider.';

  @override
  String get credentialEnvironment =>
      'Managed by the server environment. It cannot be removed here.';

  @override
  String credentialUnnamed(int index) {
    return 'Saved account $index';
  }

  @override
  String get mcpRemoveFailed =>
      'Could not confirm MCP removal. Refresh the list before trying again; the server may already have applied the change.';

  @override
  String get mcpLoadFailed => 'Could not refresh MCP data. Try again.';

  @override
  String get mcpSavedStatus => 'Saved in OpenCode';

  @override
  String get mcpRetryReconnect => 'Try reconnecting again';

  @override
  String get mcpReconnecting => 'Reconnecting';

  @override
  String get mcpStillDisconnected =>
      'OpenCode is still disconnected. Try again.';

  @override
  String get mcpScopeChanged =>
      'The server or project changed. Refresh to load its MCP servers before making changes.';

  @override
  String get promptStashRestoreFailed =>
      'Could not finish restoring the prompt. Saved copies remain available; check the composer before trying again.';

  @override
  String get promptStashContextOnly => 'Attachments and references';

  @override
  String get promptDefaultLocation => 'the server default directory';

  @override
  String get promptStashed => 'Prompt saved to your stash.';

  @override
  String get promptStashedDraftPending =>
      'Prompt saved to your stash. The composer draft still needs to be saved; try again from the draft warning.';

  @override
  String get promptStashReadFailed =>
      'Could not read saved prompts. Their stored data has been kept.';

  @override
  String get promptStashDescription =>
      'Save text, attachments and references for later';

  @override
  String get promptRestored => 'Saved prompt restored';

  @override
  String promptStashLocation(String directory) {
    return 'This prompt refers to files in $directory. Switch to its original project before restoring it.';
  }

  @override
  String get promptStashScopeChanged =>
      'The server or project changed. Close and reopen Saved prompts.';

  @override
  String get transcriptFindHint => 'Search conversation';

  @override
  String get transcriptFindClose => 'Close search';

  @override
  String get transcriptFindPrevious => 'Previous match';

  @override
  String get transcriptFindNext => 'Next match';

  @override
  String get transcriptFindNone => 'No matches';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$current of $total matches',
      one: '1 match',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'Loaded messages only. Load older messages to search further.';

  @override
  String get transcriptFindReasoning => 'Reasoning';

  @override
  String get transcriptFindTool => 'Tool data';

  @override
  String get transcriptFindFile => 'File name';

  @override
  String get transcriptFindAll => 'Search all history';

  @override
  String get skillUse => 'Add to conversation';

  @override
  String get skillActivationHelp =>
      'Adds these skill instructions to this conversation. Your unsent draft stays in the composer.';

  @override
  String get skillRunNow => 'Run agent now';

  @override
  String get skillRunHelp =>
      'Turn off to add the skill without starting another response.';

  @override
  String get skillLocationChanged =>
      'The server or project changed. Reopen Skills from the conversation.';

  @override
  String get skillUnsupported =>
      'Skill activation is unavailable on this server. You can still preview skills.';

  @override
  String get skillStaged =>
      'Resolve the staged revert in the conversation before adding a skill.';

  @override
  String get skillBusy =>
      'A skill is already being added to this conversation.';

  @override
  String get skillUncertain =>
      'The server did not confirm the result. The skill may have been added. Close this sheet and check the conversation before trying again.';

  @override
  String get skillApplied => 'Skill added to this conversation.';

  @override
  String get skillAppliedOriginal =>
      'Skill added to the original conversation. Close this sheet to return.';

  @override
  String get activeContextTitle => 'Active context';

  @override
  String get activeContextSubtitle => 'Inspect messages after compaction';

  @override
  String get activeContextRefresh => 'Refresh active context';

  @override
  String get activeContextSearch => 'Search active messages';

  @override
  String get activeContextEmpty =>
      'The server returned no active context messages.';

  @override
  String get activeContextNoText => 'No supported text content in this entry.';

  @override
  String get activeContextUnsupported =>
      'Active context inspection is unavailable on this server.';

  @override
  String get activeContextChanged =>
      'The server, project or conversation changed. Reopen this inspector from the conversation.';

  @override
  String get activeContextInvalid =>
      'The server returned an invalid context snapshot. Refresh to try again.';

  @override
  String activeContextRefreshFailed(String error) {
    return 'Showing the previous snapshot. Could not refresh: $error';
  }

  @override
  String get activeContextContentHelp =>
      'Snapshot of available message content. Binary attachment bodies, URLs and internal metadata are not displayed. This is not the complete provider request.';

  @override
  String get activeContextUser => 'User prompt';

  @override
  String get activeContextAssistant => 'Assistant';

  @override
  String get activeContextSystem => 'System instructions';

  @override
  String get activeContextSynthetic => 'Synthetic message';

  @override
  String get activeContextSkill => 'Skill';

  @override
  String get activeContextShell => 'Shell';

  @override
  String get activeContextCompaction => 'Compaction';

  @override
  String get activeContextChange => 'Conversation change';

  @override
  String get activeContextText => 'Text';

  @override
  String get activeContextToolInput => 'Tool input';

  @override
  String get activeContextToolOutput => 'Tool output';

  @override
  String get activeContextFile => 'File attachment';

  @override
  String get activeContextNotice => 'Server notice';

  @override
  String get activeContextPruned => 'Content pruned by the server';

  @override
  String get activeContextTruncated => 'Output truncated by the server';

  @override
  String get draftSaveFailed => 'Draft not saved. Copy your text or retry.';

  @override
  String get draftStorageFull =>
      'Draft storage is full. Copy your text before leaving.';

  @override
  String get draftProfileRemoved =>
      'The original server was removed. Copy your draft to keep it.';

  @override
  String get draftRetrySave => 'Try saving draft again';

  @override
  String get draftClearFailed =>
      'Could not clear the saved draft. Retry before leaving.';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => 'Your draft isn\'t saved';

  @override
  String get draftLeaveMessage =>
      'Copy your text to keep it, or try saving again. If you leave without saving, your latest changes may be lost.';

  @override
  String get draftLeaveAction => 'Leave without saving';

  @override
  String get draftKeepEditing => 'Keep editing';

  @override
  String get draftUnsaved => 'Unsaved';

  @override
  String get draftAttachmentsLocal =>
      'Attachments save with this draft on this device.';

  @override
  String get draftAttachmentsFailed =>
      'Attachments need recovery or could not be saved. Retry before sending.';

  @override
  String get draftAttachmentRecoveryTitle => 'Some attachments need attention';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return 'These saved attachments are missing, unreadable, or belong to another project: $names. Use the available attachments and remove these from the draft, or keep the saved draft and retry later.';
  }

  @override
  String get draftUseAvailableAttachments => 'Use available attachments';

  @override
  String get draftKeepSavedAttachments => 'Keep saved draft';

  @override
  String get photoLibraryAction => 'Photo library';

  @override
  String get photoLibraryDescription => 'Choose a photo or screenshot';

  @override
  String get photoCameraAction => 'Take photo';

  @override
  String get photoTooLarge => 'Choose a photo smaller than 10 MB.';

  @override
  String get photoStorageFailed =>
      'The photo could not be saved on this device. Free some space and retry.';

  @override
  String get photoPendingOther =>
      'A photo is still waiting for another conversation. Add or discard it there, then try again.';

  @override
  String get photoUnavailable =>
      'The photo could not be opened. Try adding it again from Photo library or Take photo.';

  @override
  String get photoPermissionDenied =>
      'Photo access was denied. Allow camera or photo access in Android app settings, then try again.';

  @override
  String get photoPendingTitle => 'Pending photo';

  @override
  String get photoDiscard => 'Discard pending photo';

  @override
  String get photoAddToDraft => 'Add recovered photo to draft';

  @override
  String get photoOtherLocation =>
      'Return to the photo\'s original server and project before adding it.';

  @override
  String get photoDraftFull =>
      'Remove an attachment first. A draft holds up to 5 files and 20 MB in total.';

  @override
  String get quotaTitle => 'Remaining usage';

  @override
  String quotaSourceTitle(String profile, String provider) {
    return '$profile · $provider';
  }

  @override
  String get quotaUnknownSource => 'No saved server';

  @override
  String get quotaSourceChanged =>
      'The server or project changed, or its local data is being removed. Reopen Remaining usage to review the source again.';

  @override
  String get quotaSetupDescription =>
      'Once it’s installed, confirm you trust it, then read. Provider tokens stay on the server.';

  @override
  String get quotaSetupNeeded =>
      'Use a saved server with a password and HTTPS, or phone loopback. Update its connection settings before checking the collector.';

  @override
  String get quotaConsent =>
      'I installed and trust this collector on this server.';

  @override
  String get quotaRead => 'Read remaining usage';

  @override
  String get quotaRefresh => 'Refresh remaining usage';

  @override
  String get quotaLoading => 'Reading remaining usage';

  @override
  String get quotaCollectorAuth =>
      'The collector route did not accept this server sign-in. Ask the server operator to check its authentication setup.';

  @override
  String get quotaUnavailable =>
      'Remaining usage could not be refreshed. Check the connection and collector, then retry.';

  @override
  String get quotaInvalidResponse =>
      'The collector returned an unsupported or invalid snapshot. No new allowance is shown.';

  @override
  String get quotaUnconfigured =>
      'The collector has no authorized account source configured. Ask its operator to finish setup.';

  @override
  String get quotaProviderUnsupported =>
      'The selected OAuth login or provider usage route is not supported by this collector.';

  @override
  String get quotaProviderAuth =>
      'Sign in again using the provider\'s existing login tool on the server. This app does not read or refresh that login.';

  @override
  String get quotaRateLimited =>
      'The provider limited quota checks. Wait before refreshing; this does not prove your coding allowance is exhausted.';

  @override
  String get quotaAccountUnverified =>
      'The collector could not verify the selected account. No allowance is shown. Check the login source on the server.';

  @override
  String get quotaStale =>
      'This is the last reading. Refresh to see the latest.';

  @override
  String get quotaUseBlocked =>
      'The provider reports that ordinary Codex use is currently blocked. Window percentages alone do not determine access.';

  @override
  String quotaUsed(String percent) {
    return '$percent used';
  }

  @override
  String get quotaSourceDisclosure =>
      'Read-only snapshot from the optional collector using an internal provider endpoint. Other product allowances, model-specific limits, credits and eligibility are not included. Missing data is unknown, not unlimited.';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

  @override
  String get quotaClaudeUnavailable =>
      'Claude subscription usage is unavailable here pending a supported, permitted integration. Current OpenCode does not include Claude Pro/Max sign-in. This app will not read or reuse that subscription login.';

  @override
  String get iosRemoteSummary =>
      'A remote client for the OpenCode server you choose. On-device server hosting and background monitoring are not available in this iOS build.';

  @override
  String get iosKeychainGuide =>
      'Server passwords use this device\'s Keychain. They are not stored in plain app preferences.';

  @override
  String get platformSecureStorageGuide =>
      'Server passwords use this platform\'s secure credential storage. They are not stored in plain app preferences.';

  @override
  String get quotaSourceBound =>
      'Tied to the collector\'s configured Claude login. The usage response does not independently identify the account.';

  @override
  String get usageProviders => 'Providers';

  @override
  String get usageProviderScope =>
      'Totals from this server\'s returned model records for the selected scope. Not provider billing or subscription allowances.';

  @override
  String usageProviderModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count models',
      one: '1 model',
    );
    return '$_temp0';
  }

  @override
  String get usageProviderCostUnavailable => 'Cost subtotal unavailable';

  @override
  String usageProviderCostShare(String percent) {
    return '$percent of reported cost';
  }

  @override
  String get setupOutputWaiting => 'Waiting for Termux output…';

  @override
  String get uncertainAuthDetail =>
      'The server may have started this sign-in without confirming it. Check on the server before you start again.';

  @override
  String get uncertainAuthCloseHint =>
      'To start over, close this and clear the unconfirmed sign-in from the provider\'s row.';

  @override
  String get pluginsUnsupported =>
      'This server does not support plugin inspection.';

  @override
  String get pluginsDisconnected =>
      'Connect to a server to inspect its plugins.';

  @override
  String get pluginsEmpty => 'No plugins reported for this project.';

  @override
  String get pluginsLoadFailed => 'Could not load plugins. Try again.';

  @override
  String get pluginsRefresh => 'Refresh plugins';

  @override
  String get pluginsRetry => 'Try again';

  @override
  String get pluginsUnnamed => 'Plugin without an ID';

  @override
  String get pluginsStatusActive => 'Active';

  @override
  String get pluginsStatusUnknown => 'Unknown status';

  @override
  String get pluginsSourceBuiltin => 'Built in';

  @override
  String get pluginsSourcePackage => 'Package';

  @override
  String get pluginsSourceLocal => 'Local file (path hidden)';

  @override
  String get pluginsSourceSdk => 'SDK';

  @override
  String get pluginsSourceUnknown => 'Unknown source';

  @override
  String get pluginsTerminalUi => 'Terminal UI declared';

  @override
  String get pluginsFailureDetail =>
      'Failure details are hidden because they may contain credentials.';

  @override
  String get demoReviewChanges => 'Review changes';

  @override
  String get demoSetUpServer => 'Set up your own server';

  @override
  String get handoffCopyCommand => 'Copy command';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get quotaMiniMaxSourceBound =>
      'Tied to the collector\'s configured MiniMax Subscription Key. The quota response does not independently identify the account. Only reported general-pool percentages are shown; other limits may apply.';

  @override
  String get managedHealthLifetime =>
      'Android may stop either app. Keeping the mobile connection alive does not guarantee the Termux server will keep running overnight.';

  @override
  String get quotaBudgetOff => 'Off';

  @override
  String quotaBudgetPercent(String percent) {
    return '$percent% used';
  }

  @override
  String get quotaBudgetSaveFailed =>
      'Could not save this budget change. Your last saved settings remain in effect.';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get usageBudgetTitle => 'Budgets';

  @override
  String get usageBudgetDescription =>
      'Budgets use all reported consumption for the selected server, project, timezone and date-window start. Model filters do not change them. A new window start needs a new budget. These do not change subscription allowances or stop requests.';

  @override
  String get usageBudgetUsd => 'Set USD budget';

  @override
  String get usageBudgetTokens => 'Set token budget';

  @override
  String get usageBudgetAmount => 'Budget amount';

  @override
  String get usageBudgetRemove => 'Remove budget';

  @override
  String usageBudgetProgress(String used, String limit, String unit) {
    return '$used of $limit $unit';
  }

  @override
  String get usageBudgetTokenUnit => 'tokens';

  @override
  String get usageBudgetReached => 'Personal budget reached in this reading.';

  @override
  String get usageBudgetPrevious =>
      'Previous reading reached this budget. Refresh to check current consumption.';

  @override
  String get usageBudgetClearAll => 'Clear both budgets';

  @override
  String get usageBudgetClearDescription =>
      'Remove all current and past consumption budgets for this saved server? Provider thresholds are kept.';

  @override
  String get usageBudgetClearTitle => 'Clear consumption budgets?';

  @override
  String get monitorScope =>
      'Counts cover each server’s last selected project, not every project on that server.';

  @override
  String get monitorDisclosure =>
      'Monitoring is off until you enable it for a server. Checks run about once a minute while this app is open. Background checks run no more often than every five minutes, only while Stay connected in the background is on and Android’s service is running. Android can stop that service; no remaining runtime is promised.';

  @override
  String get monitorOptIn => 'Monitor this server';

  @override
  String get monitorOptInDetail =>
      'Check pending permissions, questions and forms in its last selected project.';

  @override
  String get monitorNotifications => 'Notify when attention is needed';

  @override
  String get monitorWifiDetail =>
      'Checks pause unless Android reports an active Wi-Fi network. VPN or unavailable network information may pause checks.';

  @override
  String get monitorQuiet => 'Quiet hours';

  @override
  String get monitorQuietStart => 'Quiet hours start';

  @override
  String get monitorQuietEnd => 'Quiet hours end';

  @override
  String get monitorSaveFailed =>
      'Could not save monitoring settings. Try again.';

  @override
  String get monitorOpenFailed =>
      'This request or its project changed. Refresh the inbox and try again.';

  @override
  String get monitorSession => 'Conversation';

  @override
  String get monitorPermission => 'Permission needed';

  @override
  String get monitorQuestion => 'Answer needed';

  @override
  String get monitorForm => 'Form response needed';

  @override
  String get monitorUnknown => 'Unknown';

  @override
  String get monitorLastChecked => 'Last checked';

  @override
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  ) {
    return '$profile · $kind\n$lastChecked: $time';
  }

  @override
  String get monitorCheckIn => 'Check in on long runs';

  @override
  String get monitorCheckInDetail =>
      'Remind me when a run has been busy this long.';

  @override
  String get monitorCheckInDetailForeground =>
      'Show a reminder row when a run has been busy this long.';

  @override
  String get monitorCheckInAfter => 'Check in after';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes minutes',
      one: '1 minute',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => 'Time to check in';

  @override
  String get quotaBudgetClearAll => 'Clear saved provider thresholds';

  @override
  String managedRecoveryAttempts(int attempts) {
    return 'Attempts used: $attempts of 3. The limit survives app restarts.';
  }

  @override
  String get managedRecoveryExhausted =>
      'Recovery limit reached. Check the server and start it manually before resetting the retry budget.';

  @override
  String get managedRecoveryBackground =>
      'Recovery waits while the app is in the background.';

  @override
  String get managedRecoveryChecking =>
      'Checking the managed recovery operation…';

  @override
  String managedRecoveryNext(String time) {
    return 'Next recovery attempt no earlier than $time.';
  }

  @override
  String get managedRecoveryCheck => 'Check recovery status';

  @override
  String get managedRecoveryReset => 'Reset retry budget';

  @override
  String get managedRecoverySaveFailed =>
      'Recovery settings could not be saved. Retry.';

  @override
  String get managedRecoveryRevokeFailed =>
      'Could not save or revoke recovery. Keep this server and retry before removing it.';

  @override
  String get managedRecoverySettingsUnreadable =>
      'Recovery settings could not be read. Check the server before enabling recovery.';

  @override
  String get managedRecoveryEnableFailed =>
      'Could not enable recovery. Start the managed server, then try again.';

  @override
  String get managedRecoveryOwnershipChanged =>
      'The managed operation changed. Check the server before enabling recovery again.';

  @override
  String get managedRecoveryUncertain =>
      'Recovery paused because Termux did not confirm the result. Refresh to continue.';

  @override
  String get managedRecoveryRetryDisable => 'Try disabling recovery again';

  @override
  String get pluginMappingUnavailable =>
      'This plugin or command is no longer available here. Refresh and review your links.';

  @override
  String get mobileTasksUnfinished => 'Show unfinished only';

  @override
  String get mobileTasksNoUnfinished => 'No unfinished tasks in this list.';

  @override
  String get mobileTaskPending => 'Pending';

  @override
  String get mobileTaskInProgress => 'In progress';

  @override
  String get mobileTaskCompleted => 'Completed';

  @override
  String get mobileTaskCancelled => 'Cancelled';

  @override
  String mobileTasksProgress(int done, int total) {
    return '$done of $total done';
  }

  @override
  String get mobileTasksCopyAll => 'Copy all tasks';

  @override
  String get mobileTaskPriorityHigh => 'High priority';

  @override
  String get mobileTaskPriorityMedium => 'Medium priority';

  @override
  String get mobileTaskPriorityLow => 'Low priority';

  @override
  String get quotaMonitorTitle => 'Quota monitoring';

  @override
  String get quotaMonitorRuntime =>
      'Sources are checked in rotation, at most three per cycle; larger lists take several cycles. Background reads require the existing live service to be active; Android may stop it. Displayed readings expire when the collector says they do. Device alerts record past threshold readings, not current remaining allowance. This page never switches your active server.';

  @override
  String get quotaMonitorDisabled => 'Monitoring is off.';

  @override
  String get quotaMonitorWaiting => 'Waiting for a fresh reading.';

  @override
  String get quotaMonitorChecking => 'Checking now…';

  @override
  String get quotaMonitorPaused =>
      'Paused. Checks start again when the app is open or Stay connected in the background is on.';

  @override
  String get quotaMonitorWifiRequired => 'Waiting for Wi-Fi to check again.';

  @override
  String get quotaMonitorSourceChanged =>
      'The account on this server changed, so checks stopped. Open Remaining usage on that server and read it again.';

  @override
  String get quotaMonitorSaveFailed =>
      'Could not save quota monitoring. A failed disable stays paused in this app; retry before closing the app.';

  @override
  String quotaMonitorDisable(String provider, String server) {
    return 'Stop monitoring $provider on $server';
  }

  @override
  String get webSearchDisclosure =>
      'Search sends your query to this server’s selected search provider. Review results before adding them to your editable draft. Nothing is sent to the model here.';

  @override
  String get webSearchManual => 'Or paste a source';

  @override
  String get webSearchUnavailable =>
      'Web search is unavailable. Configure a search provider on this server, then refresh providers. You can still paste a source below.';

  @override
  String get webSearchAuthentication =>
      'The server did not authorize web search. Check this server’s credentials.';

  @override
  String get webSearchInvalidResponse =>
      'The search response did not match this server or the supported format. Refresh providers or paste a source.';

  @override
  String get webSearchFailed =>
      'Web search could not finish. Try again or paste a source.';

  @override
  String get webSearchRefresh => 'Refresh providers';

  @override
  String get webSearchProvider => 'Search provider';

  @override
  String get webSearchQuery => 'Search query';

  @override
  String get webSearchSubmit => 'Search';

  @override
  String get webSearchEmpty => 'No usable results for this query.';

  @override
  String get webSearchOmitted =>
      'Some results were omitted because their links or excerpts exceeded the review limits.';

  @override
  String get queueStorageUnreadable =>
      'Saved queued prompts could not be read. New prompts cannot be queued until this device data is cleared.';

  @override
  String get queueStorageDiscardUnreadable =>
      'This permanently deletes the unreadable queued prompts and their attachments from this device. Their contents and count are unknown. Nothing on the server is affected.';

  @override
  String get filesViewerScopeChanged =>
      'Server changed. Close and reopen this file.';

  @override
  String get queueStorageCountUnknown =>
      'Saved queued data could not be read. The number of queued prompts is unknown.';

  @override
  String get codexConnectionVerified =>
      'Connection verified. Save and connect to continue.';

  @override
  String get codexApprovalRecoveryNotice =>
      'After reconnecting, review any pending approvals on your computer.';

  @override
  String get connectionTokenRejected =>
      'The connection token was rejected. Update it to reconnect.';

  @override
  String connectionPasswordUnreadable(String server) {
    return 'Can\'t read the saved password for $server';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return 'Can\'t read the saved token for $server';
  }

  @override
  String get connectionEnterPassword => 'Enter the password';

  @override
  String get connectionEnterToken => 'Enter the token';

  @override
  String get connectionPasswordUnreadableDetails =>
      'This phone\'s secure storage couldn\'t open the password saved for this server. That can happen after the phone is restored from a backup or its screen lock is changed. The password itself was not changed: enter it again to connect.';

  @override
  String get connectionTokenUnreadableDetails =>
      'This phone\'s secure storage couldn\'t open the token saved for this server. That can happen after the phone is restored from a backup or its screen lock is changed. The token itself was not changed: enter it again to connect.';

  @override
  String get updateConnectionToken => 'Update token';

  @override
  String get codexDraftReconnectNotice =>
      'Review draft stays here; nothing is sent automatically.';

  @override
  String get codexTextOnlyPrompt =>
      'This server supports text only. Remove attachments before sending.';

  @override
  String get codexOfflineDraftSaved =>
      'Reconnect before sending. Your draft is kept on this device.';

  @override
  String get codexReconnectBeforeSending => 'Reconnect before sending.';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 or wss://paseo.example';

  @override
  String get paseoPasswordLabel => 'Daemon password (optional)';

  @override
  String get paseoPasswordHelp =>
      'Set one with \"paseo daemon set-password\". Stored in this device\'s secure storage.';

  @override
  String get paseoSetupNotice =>
      'Run \"paseo start --no-relay\" on the computer that has Claude Code or Pi installed. This app never uses the Paseo relay: connect on this device or over your own private network.';

  @override
  String get connectionDisplayName => 'Display name (optional)';

  @override
  String get connectionDisplayNameHint => 'Defaults to the server host';

  @override
  String get connectionServerAddress => 'Server address';

  @override
  String get codexAddressHint => 'wss://codex.example or ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => 'Project folder on server';

  @override
  String get codexTokenReentry => 'Re-enter connection token';

  @override
  String get codexTokenLabel => 'Connection token';

  @override
  String get codexTokenStorageHelp =>
      'Stored securely on this device and sent only to this Codex server.';

  @override
  String get projectConfiguredFolder => 'Configured folder';

  @override
  String get termuxGuideTitle => 'Connect Termux once';

  @override
  String get termuxGuideOpenFailed =>
      'The command was copied, but Termux could not open. Open Termux yourself or try Copy & open Termux again.';

  @override
  String get termuxGuideCopyOpenFailed =>
      'Could not copy the command or open Termux.';

  @override
  String get termuxPermissionDenied =>
      'Android didn\'t let this app run commands in Termux.';

  @override
  String get launchShortcutWaiting =>
      'Connecting to the saved server. The new conversation opens when it is ready.';

  @override
  String get launchShortcutNoServer =>
      'Choose a server, then start a new conversation.';

  @override
  String get launchShortcutReentry =>
      'Enter the credentials for the saved server, then start a new conversation.';

  @override
  String get launchShortcutConnectionFailed =>
      'Could not connect to the saved server. Choose or fix a server, then start a new conversation.';

  @override
  String get launchUiPinnedUntitled => 'Untitled conversation';

  @override
  String get launchUiSessionWaiting =>
      'Connecting to the saved server. The conversation opens when it is ready.';

  @override
  String get launchUiSessionNoServer =>
      'Choose a server, then open the conversation from its list.';

  @override
  String get launchUiSessionReentry =>
      'Enter the credentials for the saved server, then open the conversation from its list.';

  @override
  String get launchUiSessionConnectionFailed =>
      'Could not connect to the saved server. Choose or fix a server, then open the conversation from its list.';

  @override
  String get launchUiSessionOtherServer =>
      'That shortcut belongs to another server. Connect to that server, then open the conversation from its list.';

  @override
  String get launchUiActivityNoServer =>
      'Choose a server to see what needs your attention.';

  @override
  String get queuedResendTitle => 'Send this draft again?';

  @override
  String get queuedResendMessage =>
      'It may already have reached OpenCode. Sending again can duplicate it.';

  @override
  String get queuedResendConfirm => 'Send again';

  @override
  String get queuedKeepForReview => 'Keep for review';

  @override
  String get queuedDiscardUnconfirmedMessage =>
      'Its earlier send was never confirmed; it may already be in the conversation.';

  @override
  String get setupRuntimeOne => 'OpenCode 1';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count drafts with an unconfirmed send to review.',
      one: '1 draft with an unconfirmed send to review.',
    );
    return '$_temp0';
  }

  @override
  String get isolatedTaskTitle => 'Start in a separate copy';

  @override
  String get isolatedTaskIntro =>
      'Works on its own branch, so it can\'t clash with your other conversations.';

  @override
  String get isolatedTaskNameLabel => 'Name of the copy (optional)';

  @override
  String get isolatedTaskNameHelper =>
      'Leave it empty and a name is chosen for you.';

  @override
  String get isolatedTaskStart => 'Start';

  @override
  String get isolatedTaskCreating => 'Making the copy…';

  @override
  String get isolatedTaskCreatingHint =>
      'If you stop waiting, the copy may still be made. You\'ll find it under Project › Worktrees.';

  @override
  String isolatedTaskPreparing(String name) {
    return 'Setting up $name…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name is ready. Opening the conversation…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name is ready, but the conversation didn\'t open.';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return '$name is made, but its setup hasn\'t reported back.';
  }

  @override
  String get isolatedTaskUnconfirmedHint =>
      'Setup may still be running. Keep waiting, or start in it now.';

  @override
  String isolatedTaskFailed(String name) {
    return 'Setup failed in $name';
  }

  @override
  String get isolatedTaskCreateFailed => 'Couldn\'t make the copy';

  @override
  String get isolatedTaskCancelled => 'Stopped waiting.';

  @override
  String isolatedTaskOpening(String name) {
    return 'Opening the conversation in $name…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return 'The conversation in $name is ready.';
  }

  @override
  String get isolatedTaskStopWaiting => 'Stop waiting';

  @override
  String get isolatedTaskKeepWaiting => 'Keep waiting';

  @override
  String get isolatedTaskRetryOpen => 'Try again';

  @override
  String get isolatedTaskClose => 'Close';

  @override
  String get returnBriefStatusUnknown => 'Review status unknown';

  @override
  String get returnBriefReview => 'Review results';

  @override
  String get returnBriefContinue => 'Continue';

  @override
  String get capsuleError => 'Error';

  @override
  String get capsuleRemove => 'Remove';

  @override
  String get markdownWrapCode => 'Wrap lines';

  @override
  String get markdownScrollCode => 'Scroll lines';

  @override
  String get markdownReaderTitle => 'Code reader';

  @override
  String get tailscaleTitle => 'Connect with Tailscale';

  @override
  String get tailscaleIntro =>
      'Reach OpenCode on another computer through your own Tailscale network.';

  @override
  String get tailscaleAppStep => '1. Open your private network';

  @override
  String get tailscaleChecking => 'Checking for the Tailscale app…';

  @override
  String get tailscaleInstalled => 'Tailscale is installed.';

  @override
  String get tailscaleMissing =>
      'Tailscale is not installed. Install the official app, then return and check again.';

  @override
  String get tailscaleUnknown =>
      'Could not check the app. Try again, or open Tailscale from your phone.';

  @override
  String get tailscaleUnsupported =>
      'This device cannot open the Android app. Set up Tailscale on this device yourself, then review your HTTPS address below.';

  @override
  String get tailscaleVpnHandoff =>
      'In Tailscale, sign in to the network that can reach your server, approve Android’s VPN prompt if asked, and turn the connection on. OpenCode cannot see or change that VPN state.';

  @override
  String get tailscaleReturned =>
      'Welcome back. App presence was checked again; use Test connection on the next screen to check your server.';

  @override
  String get tailscaleOpen => 'Open Tailscale';

  @override
  String get tailscaleCheckAgain => 'Check Tailscale again';

  @override
  String get tailscaleAddressStep => '2. Review your server address';

  @override
  String get tailscaleAddressLabel => 'Private HTTPS server address';

  @override
  String get tailscaleAddressDetail =>
      'Use the full HTTPS origin printed by Tailscale Serve, such as https://computer.tailnet-name.ts.net. Keep any HTTPS port it prints. A short device name or a raw HTTP port may not provide a valid certificate.';

  @override
  String get tailscaleAddressError =>
      'That address won\'t work here. Copy the https:// address Tailscale Serve shows on your computer and paste it as it is, with nothing added after it.';

  @override
  String get tailscaleReviewDetail =>
      'Continue only with an address you recognize. The next screen reviews your server credentials before you explicitly test or save. This app cannot confirm that an address is private from its name alone.';

  @override
  String get tailscaleContinue => 'Continue to sign-in';

  @override
  String get tailscaleHelp => 'Tailscale setup and recovery';

  @override
  String get tailscaleServeHelp =>
      'On the server computer, Tailscale Serve can provide private HTTPS for a local OpenCode port. Use Serve, not public Funnel. Your tailnet access rules still apply. Enabling HTTPS publishes the certificate’s device and tailnet names in a public certificate log, although access stays private. Review the official guide before changing your server.';

  @override
  String get tailscaleServeDocs => 'Read the official Serve guide';

  @override
  String get tailscaleAndroidDocs => 'Read the official Android guide';

  @override
  String get tailscaleRecovery =>
      'If the server is unreachable, check Tailscale on both devices, the full HTTPS name and port, Serve on the server, and your network’s access rules. A VPN or DNS conflict may also prevent access. Keep HTTPS enabled. Correct the server password if authentication is rejected, then retry Test connection.';

  @override
  String get tailscaleEditorDetail =>
      'Your network connection is managed in Tailscale. Test connection checks this OpenCode server, not the VPN. Enter the server’s own username and password here, not your Tailscale login. Setup help keeps these fields intact.';

  @override
  String get a2aDraftSaveError =>
      'Draft changes could not be saved. Keep this screen open and retry before leaving.';

  @override
  String get a2aRetryDraftSave => 'Try saving draft again';

  @override
  String get a2aSavingDraft => 'Saving draft changes…';

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · Text tasks';

  @override
  String get a2aTitle => 'External agents';

  @override
  String get a2aAdd => 'Add agent';

  @override
  String get a2aDeleteLocal => 'Remove from this phone';

  @override
  String get a2aAddress => 'Agent address';

  @override
  String get a2aUnsupported =>
      'Unavailable: this card does not advertise the supported A2A 1.0 JSON-RPC, text and authentication combination on the same origin, or requires an unsupported extension. No task can be sent.';

  @override
  String get a2aBearer => 'Agent bearer credential';

  @override
  String get a2aSkills => 'Advertised skills';

  @override
  String get a2aTaskPrompt => 'Task text';

  @override
  String get a2aDeliveryUnconfirmed => 'Delivery unconfirmed';

  @override
  String get a2aDraft => 'Not sent';

  @override
  String get a2aCancelDetail =>
      'Ask this agent to stop this task. Work may already have finished, and the agent decides whether stopping is possible.';

  @override
  String get a2aYourReply => 'Your reply';

  @override
  String get a2aAgentOutput => 'Agent output';

  @override
  String get a2aBlockedLink => 'Unsupported link';

  @override
  String get a2aReviewLink => 'Review external link';

  @override
  String get a2aOmittedContent =>
      'Some output is omitted. This view shows bounded text and links; binary or structured artifacts are not downloaded or executed.';

  @override
  String get a2aSubmitted => 'Submitted';

  @override
  String get a2aWorking => 'Working';

  @override
  String get a2aInputRequired => 'Your input is needed';

  @override
  String get a2aAuthRequired => 'Agent requires authentication';

  @override
  String get a2aCompleted => 'Completed';

  @override
  String get a2aFailed => 'Failed';

  @override
  String get a2aCanceled => 'Canceled';

  @override
  String get a2aRejected => 'Rejected';

  @override
  String get a2aUnknown => 'Unsupported task state';

  @override
  String get a2aAddressError =>
      'Use an HTTPS origin or public Agent Card URL without credentials, query or fragment. HTTP is supported only on this device\'s loopback address.';

  @override
  String get a2aAuthenticationError =>
      'The agent rejected or could not use this credential. Return to the agent to update it, then reopen the saved task.';

  @override
  String get a2aUnavailable =>
      'The agent could not be reached or rejected this operation. Refresh a known task to check its state.';

  @override
  String get a2aInvalidResponse =>
      'The agent returned an unsupported, oversized or mismatched response. The saved task has not been replaced.';

  @override
  String get a2aUncertain =>
      'The agent may have received this message. It will not be resent. If a task ID was confirmed, refresh to check progress; otherwise check with the agent before starting another task.';

  @override
  String get a2aStorageError =>
      'Local data could not be saved or removed. Check device storage and retry the local operation. A message without a saved delivery marker is not sent.';

  @override
  String get a2aScopeError =>
      'This agent, credential or saved task changed. Close this view and reopen the agent to continue.';

  @override
  String get a2aCancelUnconfirmed =>
      'Cancellation is not confirmed. The agent still reports an active task; refresh to check again.';

  @override
  String get a2aAuthRequiredDetail =>
      'This agent requested an additional authentication flow, which this client does not support. No automatic login or task continuation will occur.';

  @override
  String get a2aUnknownDetail =>
      'This task state is not supported. You can refresh or forget the local record; sending and cancellation remain unavailable.';

  @override
  String get fileTable => 'Table';

  @override
  String get fileSource => 'Source';

  @override
  String fileLineOutsidePreview(int line) {
    return 'Line $line is outside this preview. Save the original to read that location.';
  }

  @override
  String get fileTableMalformed =>
      'This file has incomplete or inconsistent quoting. Read its source instead.';

  @override
  String get fileTableTooLarge =>
      'Table preview supports files up to 256 KB. Read the source or save the original file.';

  @override
  String get fileTableTooWide =>
      'This file has more than 32 columns. Read the source or save the original file.';

  @override
  String get fileTableFieldTooLong =>
      'A cell exceeds 4,096 characters. Read the source or save the original file.';

  @override
  String get fileImage => 'Image';

  @override
  String get fileSvgUnsupported =>
      'This SVG cannot be shown as a local static image. Read its source or save the original file. External resources, animation and complex SVG features are not supported.';

  @override
  String get filePdfEncrypted =>
      'This PDF requires a password or uses unsupported protection. Save the original to open it in a PDF app.';

  @override
  String get filePdfLimit =>
      'PDF preview supports files up to 10 MB and the first 200 pages. Save the original to read the full document.';

  @override
  String get filePdfUnavailable =>
      'PDF viewing is available on Android 10 or newer. You can still save the original file.';

  @override
  String get filePdfCancelled =>
      'PDF loading cancelled. Retry when you are ready.';

  @override
  String get filePdfFailed =>
      'This PDF page could not be displayed. Retry or save the original file.';

  @override
  String get agentAccountTitle => 'Codex account';

  @override
  String get agentAccountScopeLost =>
      'This server changed. Return to Servers and open the account for the connected server.';

  @override
  String get agentAccountRefresh => 'Refresh account';

  @override
  String get agentAccountLoading => 'Checking the host account';

  @override
  String get agentAccountUnavailable => 'Account panel unavailable';

  @override
  String get agentAccountReadFailed => 'Could not read the account';

  @override
  String get agentAccountDisconnected => 'Connection interrupted';

  @override
  String get agentAccountConnected => 'Signed in on the host';

  @override
  String get agentAccountSignedOut => 'Ready to sign in';

  @override
  String get agentAccountInProgress => 'Sign-in in progress';

  @override
  String get agentAccountNeedsAttention => 'Sign-in needs attention';

  @override
  String get agentAccountNoAuth => 'Host does not require sign-in';

  @override
  String get agentAccountApiKey => 'API key';

  @override
  String get agentAccountHostAuth => 'Host authentication';

  @override
  String get agentAccountHostNote =>
      'The sign-in is kept on the server\'s computer, not in this app. Signing in or out here changes it for every saved server on that computer.';

  @override
  String get agentAccountUnsupportedDetail =>
      'This panel is verified with Codex 0.153.4. The connected runtime may not support these account methods.';

  @override
  String get agentAccountReconnectDetail =>
      'Account data and the sign-in code were cleared. Reconnect to refresh. Sign-in will not restart automatically.';

  @override
  String get agentAccountSignIn => 'Sign in with ChatGPT';

  @override
  String get agentAccountSignInNote =>
      'Sign in with your ChatGPT account. You finish in the browser; this app never sees your password.';

  @override
  String get agentAccountLimits => 'Rate limits';

  @override
  String get agentAccountLimitsUnavailable =>
      'Rate limits are unavailable for this account or host.';

  @override
  String get agentAccountUsage => 'Token usage';

  @override
  String get agentAccountUsageUnavailable =>
      'Token usage is unavailable for this account or host.';

  @override
  String get agentAccountLifetimeTokens => 'Lifetime tokens';

  @override
  String get agentAccountPeakTokens => 'Peak daily tokens';

  @override
  String get agentAccountUsageNote =>
      'Values are reported by the host. Missing values are unknown, not zero. Token counts are not a bill or remaining message allowance.';

  @override
  String agentAccountUpdated(String time) {
    return 'Last checked $time';
  }

  @override
  String get agentAccountStarting => 'Requesting a sign-in code';

  @override
  String get agentAccountWaiting => 'Finish sign-in in your browser';

  @override
  String get agentAccountCancelling => 'Cancelling sign-in';

  @override
  String get agentAccountCancelled => 'Sign-in cancelled';

  @override
  String get agentAccountLoginFailed =>
      'Sign-in did not complete. Check the host and try again.';

  @override
  String get agentAccountLoginUncertain =>
      'The host could not confirm sign-in or cancellation. It may still be waiting. Check the official host runtime before starting again.';

  @override
  String get agentAccountLoginCompleted =>
      'Sign-in completed. Checking the account.';

  @override
  String get agentAccountCodeHint =>
      'Enter this one-time code on the official sign-in page. Keep it private.';

  @override
  String get agentAccountOpenSignIn => 'Open official sign-in';

  @override
  String get agentAccountCancel => 'Cancel sign-in';

  @override
  String get agentAccountAllowance => 'Reported allowance';

  @override
  String agentAccountPercentUsed(int percent) {
    return '$percent% used';
  }

  @override
  String get agentAccountWindowUnknown => 'Window duration unavailable';

  @override
  String agentAccountWindowMinutes(int minutes) {
    return '$minutes-minute window';
  }

  @override
  String agentAccountWindowHours(int hours) {
    return '$hours-hour window';
  }

  @override
  String agentAccountWindowDays(int days) {
    return '$days-day window';
  }

  @override
  String get agentAccountResetUnknown => 'Reset time unavailable';

  @override
  String get projectFolderChooserTitle => 'Choose a project folder';

  @override
  String get projectFolderCreate => 'Create a new folder';

  @override
  String get projectFolderOpen => 'Open a project folder';

  @override
  String get projectFolderNoCreateHint =>
      'This server cannot create folders from the app. Create the folder on that machine, then open it here by its path.';

  @override
  String get projectFolderNameLabel => 'Folder name';

  @override
  String get projectFolderNameHint => 'my-app';

  @override
  String get projectFolderCreateAction => 'Create';

  @override
  String get projectFolderCancel => 'Cancel';

  @override
  String get projectFolderOpenMessage =>
      'Enter the full path of a folder on the server. The home folder itself cannot be used; choose a project inside it.';

  @override
  String get projectFolderPathLabel => 'Folder path';

  @override
  String projectFolderPathHint(String directory) {
    return '$directory/my-app';
  }

  @override
  String get projectFolderOpenAction => 'Open';

  @override
  String projectFolderCreateSubtitle(String directory) {
    return 'In $directory on this device';
  }

  @override
  String get projectFolderOpenSubtitle =>
      'Enter the full path of a folder on the server';

  @override
  String get globalSessionsTitle => 'All conversations';

  @override
  String get globalSessionsSearchLabel => 'Search conversation titles';

  @override
  String get globalSessionsArchivedShort => 'Archived';

  @override
  String get globalSessionsUnknownLocation => 'Unknown project';

  @override
  String get globalSessionsEmptyTitle => 'No conversations yet';

  @override
  String get globalSessionsEmptyMessage =>
      'Conversations from every project on this server will appear here.';

  @override
  String get globalSessionsNoMatchTitle => 'No matching conversations';

  @override
  String get globalSessionsNoMatchMessage =>
      'Try a shorter title search or include archived conversations.';

  @override
  String get globalSessionsRefresh => 'Refresh';

  @override
  String get globalSessionsLoadMoreFailed =>
      'Could not load more conversations';

  @override
  String get globalSessionsOpen => 'Open';

  @override
  String get globalSessionsContinueHere => 'Continue here';

  @override
  String get globalSessionsActions => 'Conversation actions';

  @override
  String get globalSessionsWorking => 'Working';

  @override
  String get globalSessionsUntitled => 'Untitled conversation';

  @override
  String get workspaceNewSession => 'New conversation';

  @override
  String get workspaceDismissNotice => 'Dismiss';

  @override
  String get workspaceManageProjectHint =>
      'Switch project, where it runs, and its folder';

  @override
  String get reviewCopyFile => 'Copy updated file';

  @override
  String get reviewCopyPatch => 'Copy patch';

  @override
  String get onboardingValueTitle => 'Keep your work moving.';

  @override
  String get onboardingValueBody =>
      'Ask your coding agent for a change, review the result, and pick up where you left off.';

  @override
  String get onboardingDemoNote =>
      'A simulated conversation. No server needed.';

  @override
  String get onboardingPrivateNetwork =>
      'Reach a server over your private network';

  @override
  String get onboardingSetupGuide => 'Setup guide';

  @override
  String get onboardingSaveConnect => 'Save & connect';

  @override
  String get onboardingSaveChanges => 'Save changes';

  @override
  String get onboardingTermuxSetup => 'On this phone';

  @override
  String get activityClearHere => 'All clear here';

  @override
  String get activityStatusIncomplete => 'Status incomplete';

  @override
  String get activityUnknownStatusDetail =>
      'No requests loaded. Some server activity is still unknown.';

  @override
  String get activityCheckAgain => 'Try again';

  @override
  String get activitySavedServers => 'Saved servers';

  @override
  String get demoTaskTitle => 'Try a small change';

  @override
  String get demoTaskInstruction =>
      'Send the sample prompt below, then review the proposed edit.';

  @override
  String get reviewTitle => 'Review';

  @override
  String get modelChoiceReloadProviders => 'Reload providers';

  @override
  String get modelChoiceDone => 'Done';

  @override
  String get modelChoicePartialSaveError =>
      'Model saved. Agent choice was not confirmed. Try again.';

  @override
  String get modelChoiceModelSaveError =>
      'Could not confirm the model choice. Check your selection and try again.';

  @override
  String get workIdle => 'Idle';

  @override
  String get workStartedInBackground => 'Started in background';

  @override
  String get workBackgroundPending => 'Requesting background work…';

  @override
  String get workBackgroundRequested =>
      'Background work requested. Status will update when the server reports it.';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String setupSwitchConfirmTitle(String runtime) {
    return 'Switch to $runtime?';
  }

  @override
  String get setupSwitchConfirmDetail =>
      'Stops this phone’s server and running tasks. Conversations, provider settings and credentials stay separate; project files and configuration are shared. You can switch back.';

  @override
  String setupSwitchConfirm(String runtime) {
    return 'Switch to $runtime';
  }

  @override
  String get setupSwitchPending =>
      'The runtime switch has not finished. Retry the selected runtime or return to the previous one. Your saved runtime data is retained.';

  @override
  String setupSwitchReturn(String runtime) {
    return 'Return to $runtime';
  }

  @override
  String setupSwitchRetry(String runtime) {
    return 'Try $runtime again';
  }

  @override
  String get setupSwitchFailed =>
      'Could not finish switching runtimes. Check the setup output, then retry or return to the previous runtime.';

  @override
  String get setupSwitchLegacyTwo =>
      'This OpenCode 2 installation keeps its existing data. Switching it to OpenCode 1 is not available.';

  @override
  String setupSwitchProfileName(String runtime) {
    return 'This phone · $runtime';
  }

  @override
  String get setupSwitchMissingCredential =>
      'The saved credential for the previous runtime is unavailable. Its data is retained; restore the saved server before returning.';

  @override
  String setupSwitchProgressTitle(String runtime) {
    return 'Switching to $runtime';
  }

  @override
  String e7ConnectionFailure1(int attempts) {
    return 'Tried $attempts times. Retrying will not start a server that is not running.';
  }

  @override
  String get e7ConnectionFailure2 => 'Connection token required';

  @override
  String get e7ConnectionFailure3 =>
      'This Codex server needs a connection token before the app can connect.';

  @override
  String get e7ConnectionFailure4 =>
      'Open server settings and enter the Codex connection token.';

  @override
  String get e7ConnectionFailure5 => 'Connection token rejected';

  @override
  String get e7ConnectionFailure6 =>
      'The Codex server answered, but it did not accept the saved connection token.';

  @override
  String get e7ConnectionFailure7 =>
      'Open server settings and enter a current Codex connection token.';

  @override
  String get e7ConnectionFailure8 => 'Codex listener unavailable';

  @override
  String get e7ConnectionFailure9 => 'Codex endpoint unreachable';

  @override
  String get e7ConnectionFailure12 =>
      'Start the Codex listener on this device.';

  @override
  String get e7ConnectionFailure13 =>
      'If it is behind a tunnel, keep the tunnel running and verify its local endpoint.';

  @override
  String get e7ConnectionFailure14 =>
      'Use the Codex wss:// endpoint or an active secure tunnel.';

  @override
  String get e7ConnectionFailure15 =>
      'Check that the remote Codex listener is reachable from this device.';

  @override
  String get e7ConnectionFailure16 => 'Password rejected';

  @override
  String get e7ConnectionFailure17 =>
      'The server answered, but it did not accept the saved password. This happens when the server was restarted with a new password.';

  @override
  String get e7ConnectionFailure18 =>
      'Run opencode2 pair on the computer and paste the new code.';

  @override
  String get e7ConnectionFailure19 =>
      'If you set OPENCODE_SERVER_PASSWORD by hand, copy it again.';

  @override
  String get e7ConnectionFailure20 => 'Certificate not trusted';

  @override
  String get e7ConnectionFailure21 =>
      'The server is there, but this device does not trust its HTTPS certificate, so the app refused to send the password.';

  @override
  String get e7ConnectionFailure22 =>
      'Use a certificate from a trusted authority, or a Tailscale Serve address.';

  @override
  String get e7ConnectionFailure23 =>
      'For a self-signed certificate, install it on this device first.';

  @override
  String get e7ConnectionFailure24 => 'Nothing answered on this phone';

  @override
  String get e7ConnectionFailure26 =>
      'Running OpenCode in Termux? Open Termux and check that the server is still running.';

  @override
  String get e7ConnectionFailure27 =>
      'Using adb reverse or an SSH forward? Check that the tunnel is still connected, then try again.';

  @override
  String get e7ConnectionFailure28 =>
      'Connecting to another computer instead? Change the server to its HTTPS address or pair again.';

  @override
  String get e7ConnectionFailure29 => 'The server did not answer in time';

  @override
  String get e7ConnectionFailure31 =>
      'Are you on the same network or VPN (for example Tailscale) as the computer?';

  @override
  String e7ConnectionFailure32(int port) {
    return 'Is a firewall or captive portal blocking port $port?';
  }

  @override
  String get e7ConnectionFailure33 => 'Server not reachable';

  @override
  String get e7ConnectionFailure35 =>
      'Is opencode serve still running on the computer?';

  @override
  String get e7ConnectionFailure36 =>
      'Are you on the same network or VPN as the computer?';

  @override
  String get e7ConnectionFailure37 =>
      'Did the address change? Pair again to pick up the new one.';

  @override
  String get e7ConnectionFailure38 => 'The server answered with an error';

  @override
  String get e7ConnectionFailure39 =>
      'The server is running but reported itself unhealthy. Its own log will say why.';

  @override
  String get e7ConnectionFailure40 =>
      'Restart opencode serve and watch its output.';

  @override
  String get e7ConnectionFailure41 =>
      'Check that the server version is supported by this app.';

  @override
  String get e7ConnectionFailure42 => 'Could not connect';

  @override
  String get e7ConnectionFailure44 =>
      'Is the agent server running, and is this the right address?';

  @override
  String get e7ConnectionFailure45 =>
      'Is opencode serve running, and is this the right address?';

  @override
  String get e7PermissionAction1 => 'Run a shell command';

  @override
  String get e7PermissionAction2 => 'Edit a file';

  @override
  String get e7PermissionAction3 => 'Read a file';

  @override
  String get e7PermissionAction4 => 'Access an external directory';

  @override
  String get e7PermissionAction5 => 'Continue after repeated failures';

  @override
  String get e7PermissionAction6 => 'Permission needed';

  @override
  String e7PermissionAction7(String permission) {
    return 'Use $permission';
  }

  @override
  String get e7GlossaryMcpExplanation =>
      'Model Context Protocol. Small add-on servers that give the agent extra tools, like a browser, a database, or a design tool. You connect them once and every conversation can use them.';

  @override
  String get e7GlossaryWorktreeExplanation =>
      'A separate checkout of the same repository. Use one when you want the agent to try something on its own branch without touching the code you are working in.';

  @override
  String get e7GlossaryGotIt => 'Got it';

  @override
  String get e7BannerReconnectPassword =>
      'Server password changed — reconnect.';

  @override
  String get e7BannerUpdatePassword => 'Update password';

  @override
  String get e7BannerLost => 'Connection lost';

  @override
  String get e7BannerRetrying => 'Retrying';

  @override
  String get e7BannerDetails => 'Details';

  @override
  String e7BannerReconnectPasswordNote(String note) {
    return 'Server password changed — reconnect.\n$note';
  }

  @override
  String e7BannerReconnectingServer(String server) {
    return 'Reconnecting to $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return 'Reconnecting to $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      'What you see stays available while OpenCode is checked. Live updates resume on their own.';

  @override
  String get e7BannerStaleExplanation =>
      'What you see may be stale until OpenCode is reachable again.';

  @override
  String get e7SharedThreeStepsToYourFirstSession =>
      'Three steps to your first conversation';

  @override
  String get e7SharedOpenCodeRunsOnYourComputerThisApp =>
      'OpenCode runs on your computer. This app is the remote. Pairing connects the two with one command — no addresses or passwords to type.';

  @override
  String get e7SharedOnYourComputerRunOneCommand =>
      'On your computer, run one command';

  @override
  String get e7SharedInATerminalOnTheComputerWhere =>
      'In a terminal on the computer where OpenCode is installed:';

  @override
  String get e7SharedItStartsTheServerAndPrintsA =>
      'It starts the server and prints a pairing code — and a QR code you can scan.';

  @override
  String get e7SharedScanTheQROrPasteTheCode =>
      'Scan the QR or paste the code in this app';

  @override
  String get e7SharedPasteTheCodeInThisApp => 'Paste the code in this app';

  @override
  String get e7SharedStartTalking => 'Start talking';

  @override
  String get e7SharedPickAProjectAndSendYourFirst =>
      'Pick a project and send your first message. The work happens on your computer; this app shows it and lets you steer.';

  @override
  String get e7SharedAdvanced => 'Advanced';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServersTermuxInternals =>
      'HTTPS, SSH tunnels, older servers, Termux internals';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServers =>
      'HTTPS, SSH tunnels, older servers';

  @override
  String get e7SharedReachAServerOverHTTPSOrA =>
      'Reach a server over HTTPS or a tunnel';

  @override
  String get e7SharedPairingWorksWhenTheAddressTheServer =>
      'Pairing works when the address the server prints is one this device can reach. If it is not, expose the server through an HTTPS reverse proxy or an encrypted tunnel and add the resulting https:// URL by hand. Remote HTTP is intentionally blocked.';

  @override
  String get e7SharedOlderServersWithoutPairing =>
      'Older servers without pairing';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      'Servers started with “opencode serve” do not print a pairing code. Start them on loopback with a password:';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      'Then add the server manually with username opencode and that password.';

  @override
  String get e7SharedOnDeviceViaTermuxAutomated =>
      'On-device via Termux (automated)';

  @override
  String get e7SharedUseTheOnDeviceTermuxCardOn =>
      'Use the “On-device (Termux)” card on the Servers screen. The app installs Termux, unlocks the bridge, sets up opencode, starts the server and connects — all guided.';

  @override
  String get e7SharedOnlyTwoTapsNeedYouPersonallyDownloading =>
      'Only two taps need you personally: downloading the Termux APK and pasting one unlock line inside Termux once — both required by Android’s security model, not by this app.';

  @override
  String get e7SharedPreferManualInsideTermuxRun =>
      'Prefer manual? Inside Termux run:';

  @override
  String get e7SharedTheChrootSharesTheNetworkStackSo =>
      'The chroot shares the network stack, so http://127.0.0.1:4096 works from this app. Run `termux-wake-lock` to keep it alive.';

  @override
  String get e7SharedSecurityNotes => 'Security notes';

  @override
  String get e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding =>
      'Always set OPENCODE_SERVER_PASSWORD when binding beyond localhost.';

  @override
  String get e7SharedPasswordsAreStoredInTheAndroidKeystore =>
      'Passwords are stored in the Android Keystore on this device only.';

  @override
  String get e7SharedTheServerCanExecuteCommandsOnIts =>
      'The server can execute commands on its host — treat access like SSH access.';

  @override
  String get e7SharedOpenCodeIsReconnectingTryAgain =>
      'OpenCode is reconnecting. Try again.';

  @override
  String get e7SharedSessionContext => 'Conversation context';

  @override
  String get e7SharedRefreshContext => 'Refresh context';

  @override
  String get e7SharedNoContextUsageYet => 'No context usage yet';

  @override
  String get e7SharedSendAPromptAndWaitForAn =>
      'Send a prompt and wait for an assistant response. OpenCode will then report token usage for this conversation.';

  @override
  String get e7SharedEstimatedInputMakeup => 'Estimated input makeup';

  @override
  String get e7SharedSessionTotals => 'Conversation totals';

  @override
  String get e7SharedUsageComesFromTheLatestCompletedAssistant =>
      'Usage comes from the latest completed assistant message. The makeup is an estimate from visible prompt, response, and tool text; Other includes system instructions, tool definitions, and provider overhead.';

  @override
  String get e7SharedModelUnavailable => 'Model unavailable';

  @override
  String get e7SharedContextLimitUnavailable => 'Context limit unavailable';

  @override
  String get e7SharedLatestAssistantRequestIncludingCacheActivity =>
      'Latest assistant request, including cache activity';

  @override
  String get e7SharedContextLimit => 'Context limit';

  @override
  String get e7SharedUnavailable => 'Unavailable';

  @override
  String get e7SharedMessages => 'Messages';

  @override
  String get e7SharedAccumulatedCostReportedByServer =>
      'Accumulated cost · reported by server';

  @override
  String get e7SharedAccumulatedCost => 'Accumulated cost';

  @override
  String get e7SharedSessionTokensReportedByServer =>
      'Conversation tokens · reported by server';

  @override
  String get e7SharedUserPrompts => 'User prompts';

  @override
  String get e7SharedAssistantText => 'Assistant text';

  @override
  String get e7SharedToolCallsAndResults => 'Tool calls and results';

  @override
  String get e7SharedOtherContext => 'Other context';

  @override
  String get e7SharedSessionLocationChangedCloseAndReopenThis =>
      'The conversation’s project changed. Close and reopen this sheet.';

  @override
  String get e7SharedOpenCodeIsReconnecting => 'OpenCode is reconnecting.';

  @override
  String get e7SharedTheSessionProjectIsNotAvailableOn =>
      'The conversation project is not available on this server.';

  @override
  String get e7SharedLocalProject => 'Local project';

  @override
  String get e7SharedTheAppCouldNotInspectWorkingChanges =>
      'The app could not inspect working changes. For safety, this continues without transferring changes.';

  @override
  String get e7SharedMoveSession => 'Move conversation';

  @override
  String get e7SharedChooseAnotherDirectoryInThisProject =>
      'Choose another directory in this project.';

  @override
  String get e7SharedChooseAConnectedWorkspaceOrReturnTo =>
      'Choose a connected cloud environment, or return to the local project.';

  @override
  String get e7SharedFilterDestinations => 'Filter destinations';

  @override
  String get e7SharedCurrent => 'Current';

  @override
  String get e7SharedSwitchOrganization => 'Switch organization?';

  @override
  String get e7SharedSwitchOrganization462 => 'Switch organization';

  @override
  String get e7SharedNoSwitchableOpenCodeConsoleOrganizationsWereReturned =>
      'No switchable OpenCode Console organizations were returned.';

  @override
  String get e7SharedSessionLocationChangedReturnAndReopenRelated =>
      'The conversation’s project changed. Return and reopen related conversations.';

  @override
  String get e7SharedSessionIsNoLongerRelatedToThis =>
      'That conversation is no longer related to this one.';

  @override
  String get e7SharedSessionLocationChangedReturnAndTryAgain =>
      'The conversation’s project changed. Return and try again.';

  @override
  String get e7SharedSessionUnavailableOrLocationChangedReturnOr =>
      'Conversation unavailable or its project changed. Return or refresh to try again.';

  @override
  String get e7SharedCouldNotUpdateThePinReturnAnd =>
      'Could not update the pin. Return and try again.';

  @override
  String get e7SharedRefreshSubagentSessions =>
      'Refresh subagent conversations';

  @override
  String get e7SharedParentSession => 'Parent conversation';

  @override
  String get e7SharedNoSubagentSessionsYet => 'No subagent conversations yet';

  @override
  String get e7SharedDelegatedWorkWillAppearHereWithoutMixing =>
      'Delegated work will appear here without mixing subagent conversations into your main list.';

  @override
  String get e7SharedOpenInsecureHTTPLink => 'Open insecure HTTP link?';

  @override
  String get e7SharedOpenExternalLink => 'Open external link?';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP is not encrypted. Other devices on the network may read or change what you send and receive.';

  @override
  String get e7SharedOpenHTTPLink => 'Open HTTP link';

  @override
  String get e7SharedOpenLink => 'Open link';

  @override
  String get e7SharedNoAppCouldOpenThisLink => 'No app could open this link.';

  @override
  String get e7SharedRequired => 'Required';

  @override
  String get e7SharedDoesNotMatchTheExpectedFormat =>
      'Does not match the expected format';

  @override
  String get e7SharedEnterAWholeNumber => 'Enter a whole number';

  @override
  String get e7SharedEnterANumber => 'Enter a number';

  @override
  String get e7SharedDismissThisRequest => 'Decline this request?';

  @override
  String get e7SharedTheAgentContinuesWithoutYourAnswers =>
      'The agent continues without your answers.';

  @override
  String get e7SharedAskedByAnMCPServer => 'Asked by an MCP server';

  @override
  String get e7SharedAskedByTheAgentInThisSession =>
      'Asked by the agent in this conversation';

  @override
  String get e7SharedInputRequested => 'Input requested';

  @override
  String get e7SharedOther => 'Other…';

  @override
  String get e7SharedYourAnswer => 'Your answer';

  @override
  String get e7SharedAddYourOwn => 'Add your own';

  @override
  String get e7SharedAddAnswer => 'Add answer';

  @override
  String get e7SharedThisServerSentALinkThisApp =>
      'This server sent a link this app will not open.';

  @override
  String get e7SharedSendAnswers => 'Send answers';

  @override
  String e7SharedDetail307(int step) {
    return 'Step $step of 3';
  }

  @override
  String e7SharedDetail385(String count, String limit) {
    return '$count of $limit tokens';
  }

  @override
  String e7SharedDetail386(String count) {
    return '$count tokens · limit unavailable';
  }

  @override
  String e7SharedDetail429(String destination) {
    return 'Move conversation to $destination?';
  }

  @override
  String e7SharedDetail514(String error) {
    return 'Refresh failed: $error';
  }

  @override
  String e7SharedDetail714(int count) {
    return 'Must be at least $count characters';
  }

  @override
  String e7SharedDetail715(int count) {
    return 'Must be at most $count characters';
  }

  @override
  String e7SharedDetail721(String minimum, String maximum) {
    return 'Must be between $minimum and $maximum';
  }

  @override
  String e7SharedDetail722(String minimum) {
    return 'Must be at least $minimum';
  }

  @override
  String e7SharedDetail723(String maximum) {
    return 'Must be at most $maximum';
  }

  @override
  String e7SharedDetail726(int count) {
    return 'Pick at most $count';
  }

  @override
  String e7SharedDetail753(int minimum, int maximum) {
    return 'Pick $minimum–$maximum';
  }

  @override
  String e7SharedDetail754(int count) {
    return 'Pick at least $count';
  }

  @override
  String e7SharedDetail755(int count) {
    return 'Pick up to $count';
  }

  @override
  String e7SharedDetail756(String range, int count) {
    return '$range · $count selected';
  }

  @override
  String e7SharedDetail764(String host) {
    return 'Opens $host in your browser';
  }

  @override
  String e7SharedDetail765(String field) {
    return 'This server sent a field type this app does not understand (\"$field\").';
  }

  @override
  String get e7LocaleUiLanguage => 'Language';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => 'Use system language';

  @override
  String get e7LocaleUiClose => 'Close';

  @override
  String get e7LocaleUiDescription =>
      'Choose the language used throughout the app. Server messages and your text stay as written.';

  @override
  String get e7LocaleUiSaving => 'Saving language…';

  @override
  String get e7LocaleUiSaveFailed =>
      'Language could not be saved. Your previous choice is still active. Select a language to try again.';

  @override
  String get e7LocaleUiNewSession => 'New conversation';

  @override
  String get e7LocaleUiNewSessionHint =>
      'Start a conversation in the active project';

  @override
  String get e7LocaleUiWorkspace => 'Work';

  @override
  String get e7LocaleUiWorkspaceHint =>
      'Recent conversations and the active project';

  @override
  String get e7LocaleUiFiles => 'Project';

  @override
  String get e7LocaleUiFilesHint =>
      'Files, changes, terminal and other project tools';

  @override
  String get e7LocaleUiActivity => 'Inbox';

  @override
  String get e7LocaleUiActivityHint => 'Permissions, questions, and forms';

  @override
  String get e7LocaleUiMoreHint => 'Models, providers, notifications, settings';

  @override
  String get e7LocaleUiSettings => 'Settings';

  @override
  String get e7LocaleUiKeyboardShortcuts => 'Keyboard shortcuts';

  @override
  String get e7LocaleUiRefreshSessions => 'Refresh conversations';

  @override
  String get e7LocaleUiDiagnostics => 'Diagnostics';

  @override
  String get e7LocaleUiDiagnosticsHint => 'Recent errors and connection detail';

  @override
  String get e7LocaleUiCommandLauncher => 'Command launcher';

  @override
  String get e7LocaleUiFindSurface => 'Find on this screen';

  @override
  String get e7LocaleUiDestinations => 'Work, Inbox, Project, Settings';

  @override
  String get e7LocaleUiTerminal => 'Terminal';

  @override
  String get e7LocaleUiCloseScreen => 'Close this screen';

  @override
  String get e7LocaleUiSendPrompt => 'Send the prompt';

  @override
  String get e7LocaleUiCopyTranscript => 'Copy the selected transcript text';

  @override
  String get e7LocaleUiRecentModel =>
      'Next / previous recent model in this conversation';

  @override
  String get e7LocaleUiThisList => 'This list';

  @override
  String get e7LocaleUiCloseOverlay => 'Close a sheet, dialog, or menu';

  @override
  String get e7LocaleUiContextActions =>
      'Message, file, and conversation actions';

  @override
  String get e7LocaleUiContextKeys => 'Right click / Shift + F10 / Menu';

  @override
  String get e7LocaleUiConnectionChanged => 'The server changed.';

  @override
  String get e7AppearanceFollowAndroid => 'Follow Android';

  @override
  String get e7AppearanceFollowSystem => 'Follow system';

  @override
  String get e7AppearanceLight => 'Light';

  @override
  String get e7AppearanceDark => 'Dark';

  @override
  String get e7AppearanceFollowPhoneDescription =>
      'Match this phone’s current light or dark setting';

  @override
  String get e7AppearanceFollowDeviceDescription =>
      'Match this device’s current light or dark setting';

  @override
  String get e7AppearanceLightDescription => 'Use the bright editorial theme';

  @override
  String get e7AppearanceDarkDescription => 'Use the focused low-light theme';

  @override
  String get e7AppearanceTitle => 'Appearance';

  @override
  String get e7AppearancePreviewHint =>
      'Preview first. Your appearance changes only when you apply it.';

  @override
  String get e7AppearanceDynamicUnavailable =>
      'Material You colors are not available on this device.';

  @override
  String e7AppearanceUsesMode(String mode) {
    return 'Your light or dark setting stays $mode.';
  }

  @override
  String get e7AppearanceSaveFailed =>
      'Could not save the appearance. Your previous setting is unchanged. Try again.';

  @override
  String get e7AppearanceSaving => 'Saving…';

  @override
  String get e7AppearanceApply => 'Apply';

  @override
  String get e7AppearanceCurrent => 'Current appearance';

  @override
  String get e7AppearanceClose => 'Close';

  @override
  String get e7AppearancePreviewTitle => 'Text and controls';

  @override
  String get e7AppearancePreviewBody =>
      'See how reading, code and selected actions work together.';

  @override
  String get e7AppearanceSelection => 'Selected option';

  @override
  String get e7AppearanceTryControl => 'Try a control';

  @override
  String get e7AppearanceSampleHint =>
      'Sample controls only change this preview.';

  @override
  String get e7SettingsUi1 => 'Server';

  @override
  String get e7SettingsUi8 => 'Disconnect';

  @override
  String get e7SettingsUi9 => 'OpenCode server';

  @override
  String get e7SettingsUi11 => 'Checking server health…';

  @override
  String get e7SettingsUi12 => 'Stopped by Android';

  @override
  String get e7SettingsUi14 => 'On · running now';

  @override
  String get e7SettingsUi15 => 'On · starting';

  @override
  String get e7SettingsUi16 => 'this server';

  @override
  String get e7SettingsUi17 => 'unknown';

  @override
  String get e7SettingsUi18 => 'OpenCode is reconnecting.';

  @override
  String get e7SettingsUi19 => 'OpenCode is reconnecting. Try again.';

  @override
  String get e7SettingsUi22 => 'Android did not enable background mode.';

  @override
  String get e7SettingsUi23 => 'Android stopped the live connection';

  @override
  String get e7SettingsUi24 =>
      'Its daily limit for background data-sync work is spent, so live mode turned itself off. Turn it back on to reconnect; the limit resets within 24 hours.';

  @override
  String get e7SettingsUi25 => 'Stay connected in the background';

  @override
  String get e7SettingsUi26 =>
      'Keeps runs updating when the app is closed and notifies you when one needs you. Uses more battery and shows a persistent notification.';

  @override
  String get e7SettingsUi27 => 'Unrestricted battery access allowed';

  @override
  String get e7SettingsUi28 => 'Allow unrestricted battery access';

  @override
  String get e7SettingsUi29 =>
      'Android may still apply its foreground-service time limit.';

  @override
  String get e7SettingsUi30 =>
      'Optional. Helps preserve the live connection during Doze. Android 15+ limits data-sync background work to six hours per 24 hours.';

  @override
  String get e7SettingsUi31 => 'Stopped by Android — tap to restart';

  @override
  String get e7SettingsUi32 => 'Running now';

  @override
  String get e7SettingsUi34 =>
      'Android stops this after 6 hours a day. The app will tell you when it does.';

  @override
  String get e7SettingsUi35 => 'Default shell';

  @override
  String get e7SettingsUi36 =>
      'Used by new terminals and compatible shell commands on this OpenCode server.';

  @override
  String get e7SettingsUi37 =>
      'Terminal only; OpenCode uses a compatible fallback for shell tools.';

  @override
  String get e7SettingsUi39 => 'Automatic (server default)';

  @override
  String get e7SettingsUi41 => 'Loading shells from OpenCode…';

  @override
  String get e7SettingsUi46 => 'Restart OpenCode on its host';

  @override
  String get e7SettingsUi48 => 'Update remote OpenCode?';

  @override
  String get e7SettingsUi49 =>
      'The active server changed before the upgrade completed';

  @override
  String get e7SettingsUi50 => 'Update managed OpenCode';

  @override
  String get e7SettingsUi51 =>
      'Install the latest stable server, refresh models, restart safely, and reconnect.';

  @override
  String get e7SettingsUi52 => 'the previous version';

  @override
  String get e7SettingsUi53 => 'an unknown version';

  @override
  String get e7SettingsUi56 => 'Not connected';

  @override
  String get e7SettingsUi57 => 'Check server health';

  @override
  String get e7SettingsUi58 => 'Asking the server how it is doing';

  @override
  String get e7SettingsUi59 => 'Server healthy';

  @override
  String get e7SettingsUi60 => 'Health unavailable';

  @override
  String get e7SettingsUi62 => 'No server password saved';

  @override
  String get e7SettingsUi65 => 'Run as a Linux service';

  @override
  String get e7SettingsUi66 =>
      'Keep OpenCode running after you close the terminal.';

  @override
  String get e7SettingsUi67 => 'Server updates';

  @override
  String get e7SettingsUi68 => 'Upgrade from the machine running the server';

  @override
  String get e7SettingsUi69 => 'Light or dark';

  @override
  String get e7SettingsUi70 => 'Theme';

  @override
  String get e7SettingsUi74 => 'Always allowed actions';

  @override
  String get e7SettingsUi76 => 'On this device';

  @override
  String get e7SettingsUi77 => 'Storage used';

  @override
  String get e7SettingsUi78 => 'Clear queued prompts';

  @override
  String get e7SettingsUi79 => 'Nothing is waiting to send';

  @override
  String get e7SettingsUi80 => 'Delete queued prompts?';

  @override
  String get e7SettingsUi81 => 'Queued prompts deleted';

  @override
  String get e7SettingsUi82 =>
      'Could not delete the queued prompts. Check device storage and try again.';

  @override
  String get e7SettingsUi83 => 'Clear drafts';

  @override
  String get e7SettingsUi84 => 'No saved composer text';

  @override
  String get e7SettingsUi85 => 'Delete drafts?';

  @override
  String get e7SettingsUi86 => 'Drafts deleted';

  @override
  String get e7SettingsUi87 =>
      'Could not delete the drafts. Check device storage and try again.';

  @override
  String get e7SettingsUi88 => 'App diagnostics';

  @override
  String get e7SettingsUi92 => 'Privacy and data use';

  @override
  String get e7SettingsUi93 =>
      'Servers, providers, voice, files, Termux, and updates';

  @override
  String get e7SettingsUi94 => 'Voice licenses and provenance';

  @override
  String get e7SettingsUi95 =>
      'Whisper models, sherpa-onnx, ONNX Runtime, and record';

  @override
  String get e7SettingsUi96 => 'About and open source notices';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return 'Disconnect from $server?';
  }

  @override
  String e7SettingsHealthError(String error) {
    return 'Health unavailable — $error';
  }

  @override
  String e7SettingsHealthVersion(String version) {
    return 'Server healthy · $version';
  }

  @override
  String e7SettingsVersion(String version) {
    return 'Version $version';
  }

  @override
  String get e7AppearancePackOpencode => 'Terminal green, the default';

  @override
  String get e7AppearancePackCatppuccin => 'Mocha and Latte, mauve-led';

  @override
  String get e7AppearancePackGruvbox => 'Warm retro, orange-led';

  @override
  String get e7AppearancePackSolarized => 'The classic dual palette, blue-led';

  @override
  String get e7AppearancePackDynamic => 'This phone’s Material You colors';

  @override
  String e7SettingsStorageSummary(
    String total,
    int queued,
    String queueBytes,
    int drafts,
    String draftBytes,
    int days,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued queued prompts',
      one: '1 queued prompt',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts drafts',
      one: '1 draft',
    );
    return '$total of unsent work — $_temp0 ($queueBytes) and $_temp1 ($draftBytes). Queued prompts are discarded after $days days.';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    return 'Deletes all $count unsent prompts and their attachments, for every server';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    return 'This deletes $count unsent prompts and their attachments, for every server. They will never be sent. Nothing on the server is affected.';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    return 'Deletes composer text saved for $count conversations';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    return 'This deletes the composer text saved for $count conversations. Nothing on the server is affected.';
  }

  @override
  String e7SettingsRestartBody(String version, String current) {
    return 'OpenCode $version is installed, but the server is still running $current. Restart it on its computer the way you started it; the app then checks it again.';
  }

  @override
  String e7SettingsInstallVersion(String version) {
    return 'Install $version';
  }

  @override
  String e7SettingsInstalledVersion(String version) {
    return 'OpenCode $version installed. Restart its server process to use it.';
  }

  @override
  String e7SettingsRestartVersion(String version) {
    return 'Restart OpenCode to use $version';
  }

  @override
  String e7SettingsRetryError(String error) {
    return '$error Tap to retry.';
  }

  @override
  String e7SettingsUpdateVersion(String version) {
    return 'Update OpenCode to $version';
  }

  @override
  String get e7SettingsDetailUi17 => 'Privacy';

  @override
  String get e7SettingsDetailUi18 => 'Open source';

  @override
  String get e7SettingsDetailUi19 => 'About this build';

  @override
  String get e7SettingsDetailUi22 =>
      'A mobile client for an OpenCode server. Voice recognition runs locally after optional model downloads.';

  @override
  String get e7SettingsDetailUi23 => 'A desktop client for an OpenCode server.';

  @override
  String get e7SettingsDetailUi25 => 'Tools';

  @override
  String get e7SettingsDetailUi26 => 'Skills';

  @override
  String get e7SettingsDetailUi27 => 'References';

  @override
  String get e7SettingsAlphaBody =>
      'Android is the supported platform; desktop builds are experimental.';

  @override
  String get e7SettingsNonAffiliation =>
      'OpenCode Mobile is an independent community project. It is not built, maintained, endorsed by, or affiliated with the official OpenCode team.';

  @override
  String e7SettingsDiagnosticOccurrences(int count) {
    return '$count occurrences';
  }

  @override
  String get e7ProjectProjectsReconnect =>
      'OpenCode is reconnecting. Try again shortly.';

  @override
  String e7ProjectProjectRenameFailed(String error) {
    return 'Could not rename project: $error';
  }

  @override
  String get e7ProjectProjectDefaultDirectory =>
      'The server’s default directory';

  @override
  String get e7ProjectProjectSwitchUnavailableDetail =>
      'This server keeps the configured folder for conversations. Start a new conversation from Work to continue.';

  @override
  String get e7ProjectProjectsTitle => 'Projects';

  @override
  String get e7ProjectProjectsRefresh => 'Refresh projects';

  @override
  String get e7ProjectProjectsSearch => 'Search projects or paths';

  @override
  String get e7ProjectProjectsOpened => 'Open projects';

  @override
  String get e7ProjectProjectsEmpty => 'No projects opened';

  @override
  String get e7ProjectProjectsEmptyDetail =>
      'Projects opened by this server appear here; choose one for conversations, files, terminals, and coding tools. Create a new folder or open one by its path above, or open a project on this OpenCode server and refresh.';

  @override
  String get e7ProjectProjectsRefreshFailed => 'Project refresh failed';

  @override
  String e7ProjectProjectWorktrees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count worktrees',
      one: '1 worktree',
    );
    return '$_temp0';
  }

  @override
  String e7ProjectProjectRenameAction(String name) {
    return 'Rename $name';
  }

  @override
  String get e7ProjectProjectRenameTitle => 'Rename project';

  @override
  String get e7ProjectProjectNameLabel => 'Project name';

  @override
  String get e7ProjectProjectNameHint =>
      'Clear the name to use the project folder name.';

  @override
  String get e7ProjectProjectSave => 'Save';

  @override
  String get e7ProjectMonitorUnsupported =>
      'Background attention is unavailable for this server. Open the conversation to review current requests.';

  @override
  String get readerUiDisconnected => 'The server is not connected.';

  @override
  String get readerUiIndicatorsUnavailable =>
      'File change indicators are unavailable on this server.';

  @override
  String get readerUiIndicatorsFailed =>
      'File change indicators could not refresh.';

  @override
  String get readerUiReconnecting => 'OpenCode is reconnecting.';

  @override
  String get readerUiCommentAdded =>
      'Review comment added. Return to the conversation to continue.';

  @override
  String get readerUiCommentCopied =>
      'Review comment copied. Paste it into a conversation.';

  @override
  String get readerUiSearchSymbols => 'Search symbols';

  @override
  String get readerUiSearchFiles => 'Search files';

  @override
  String get readerUiSelectFile => 'Select a file to preview';

  @override
  String get readerUiEmptyFolder => 'Folder is empty';

  @override
  String get readerUiNoFiles => 'No files found';

  @override
  String get readerUiPullRefresh => 'Pull down to refresh this folder.';

  @override
  String get readerUiTryFileName => 'Try a different file name.';

  @override
  String get readerUiAttachPrompt => 'Attach to prompt';

  @override
  String get readerUiAddReference => 'Add as reference';

  @override
  String get readerUiOpenReview => 'Open in Review';

  @override
  String get readerUiCopyPath => 'Copy path';

  @override
  String get readerUiWorkspaceSymbols => 'Search project symbols';

  @override
  String get readerUiSymbolsHint =>
      'Find classes, functions, methods, and variables by name.';

  @override
  String get readerUiNoSymbols => 'No symbols found';

  @override
  String get readerUiSymbolsUnavailable =>
      'Try a different name. Some language services do not support project-wide symbol search.';

  @override
  String get readerUiFiles => 'Files';

  @override
  String get readerUiSymbols => 'Symbols';

  @override
  String get readerUiChanges => 'Changes';

  @override
  String get readerUiRefreshChanges => 'Refresh changes';

  @override
  String get readerUiEntireChange => 'Entire file change';

  @override
  String get readerUiWorkingTree => 'Uncommitted';

  @override
  String get readerUiSessionScopeHint => 'Files this conversation changed.';

  @override
  String get readerUiWorkingScopeHint =>
      'Everything not committed yet, whoever changed it.';

  @override
  String get readerUiBranchScopeHint =>
      'Everything on this branch, compared with the main branch.';

  @override
  String get readerUiSaveDevice => 'Save to device';

  @override
  String get readerUiPreviewUnavailable => 'Preview unavailable';

  @override
  String get readerUiRendered => 'Rendered';

  @override
  String get readerUiRaw => 'Raw';

  @override
  String get readerUiSaveFailed =>
      'Could not save reader preferences. Try again.';

  @override
  String get readerUiSourceFirst => 'Source first';

  @override
  String get readerUiServerOrder => 'Default order';

  @override
  String readerUiLine(int number) {
    return 'line $number';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return 'Added $label to the prompt';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label is already on the prompt';
  }

  @override
  String readerUiReferenceFull(int count) {
    return 'The prompt already holds $count references';
  }

  @override
  String readerUiAttached(String name) {
    return '$name attached.';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files',
      one: '$count changed file',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return '$name attached. Return to the conversation to add your comment.';
  }

  @override
  String readerUiSaveNamed(String name) {
    return 'Save $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return '$name saved to your device.';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · Line $line';
  }

  @override
  String readerUiOnPrompt(int count) {
    return '$count on prompt';
  }

  @override
  String readerUiNewLines(String label) {
    return 'new $label';
  }

  @override
  String readerUiOldLines(String label) {
    return 'old $label';
  }

  @override
  String readerUiLineRange(int first, int last) {
    return 'lines $first–$last';
  }

  @override
  String readerUiReviewPrompt(String path) {
    return 'Review `$path`';
  }

  @override
  String readerUiViewedCount(int viewed, int files) {
    return '$viewed of $files viewed';
  }

  @override
  String readerUiSaved(String name) {
    return '$name saved.';
  }

  @override
  String get readerUiSymbolFile => 'File';

  @override
  String get readerUiSymbolModule => 'Module';

  @override
  String get readerUiSymbolNamespace => 'Namespace';

  @override
  String get readerUiSymbolPackage => 'Package';

  @override
  String get readerUiSymbolClass => 'Class';

  @override
  String get readerUiSymbolMethod => 'Method';

  @override
  String get readerUiSymbolProperty => 'Property';

  @override
  String get readerUiSymbolField => 'Field';

  @override
  String get readerUiSymbolConstructor => 'Constructor';

  @override
  String get readerUiSymbolEnum => 'Enum';

  @override
  String get readerUiSymbolInterface => 'Interface';

  @override
  String get readerUiSymbolFunction => 'Function';

  @override
  String get readerUiSymbolVariable => 'Variable';

  @override
  String get readerUiSymbolConstant => 'Constant';

  @override
  String get readerUiSymbolEnummember => 'Enum member';

  @override
  String get readerUiSymbolStruct => 'Struct';

  @override
  String get readerUiSymbolEvent => 'Event';

  @override
  String get readerUiSymbolOperator => 'Operator';

  @override
  String get readerUiSymbolTypeparameter => 'Type parameter';

  @override
  String get readerUiSymbolSymbol => 'Symbol';

  @override
  String get readerUiAdded => 'Added';

  @override
  String get readerUiDeleted => 'Deleted';

  @override
  String get readerUiModified => 'Modified';

  @override
  String get readerUiChanged => 'Changed';

  @override
  String get readerUiSession => 'This conversation';

  @override
  String get readerUiBranch => 'Whole branch';

  @override
  String get readerUiAttachmentMissing =>
      'The attachment content is not included in this message.';

  @override
  String get readerUiRemoteAttachment =>
      'Remote attachment previews are not available.';

  @override
  String get readerUiAttachmentInvalid =>
      'The attachment data could not be decoded.';

  @override
  String get e7WorkspaceDisconnected => 'The server is not connected.';

  @override
  String get e7WorkspaceNoFolder => 'No project folder chosen';

  @override
  String get e7WorkspaceNoProjects => 'No projects opened';

  @override
  String get e7WorkspaceServerNoProjects => 'The server returned no projects.';

  @override
  String get e7WorkspaceChooseProject => 'Choose a project';

  @override
  String get e7WorkspaceNeedsYou => 'Needs you';

  @override
  String get e7WorkspaceNoRecent => 'No recent conversations';

  @override
  String get e7WorkspaceChooseFolderToStart =>
      'Choose a project folder to start a conversation.';

  @override
  String get e7WorkspaceStartInWorkspace =>
      'Start a conversation in the selected project.';

  @override
  String get e7WorkspaceNoProjectSelected => 'No project selected';

  @override
  String get e7WorkspaceSwitchProject => 'Switch project';

  @override
  String get e7WorkspaceThisComputer => 'This computer';

  @override
  String get e7WorkspaceNoShareLink => 'No share link was returned.';

  @override
  String get e7WorkspaceShareCopied => 'Share link copied';

  @override
  String get e7WorkspaceReconnectingShortly =>
      'OpenCode is reconnecting. Try again shortly.';

  @override
  String get e7WorkspaceRenameSession => 'Rename conversation';

  @override
  String get e7WorkspaceTitle => 'Title';

  @override
  String get e7WorkspaceShareConfirm => 'Share this conversation?';

  @override
  String get e7WorkspaceDeleteConfirm => 'Delete conversation?';

  @override
  String get e7WorkspaceArchive => 'Archive';

  @override
  String get e7WorkspaceShareSession => 'Share conversation';

  @override
  String get e7WorkspaceCompacting => 'Compacting…';

  @override
  String get e7WorkspaceStopSharing => 'Stop sharing';

  @override
  String get e7WorkspaceDisconnect => 'Disconnect';

  @override
  String get e7WorkspaceBackExit => 'Press back again to exit';

  @override
  String get e7WorkspaceConnected => 'Connected';

  @override
  String get e7WorkspaceConnecting => 'Connecting';

  @override
  String get e7WorkspaceOffline => 'Offline';

  @override
  String get e7WorkspaceReconnectingAgain =>
      'OpenCode is reconnecting. Try again.';

  @override
  String get e7WorkspaceRefreshFailed => 'Could not refresh';

  @override
  String get e7WorkspacePermissionRequired => 'Permission required';

  @override
  String get e7WorkspaceAssistantQuestion => 'Assistant question';

  @override
  String get e7WorkspaceInputRequested => 'Input requested';

  @override
  String get e7WorkspaceMcpAsked => 'Asked by an MCP server';

  @override
  String get e7WorkspaceDismissRequest => 'Dismiss this request?';

  @override
  String get e7WorkspaceDismissDetail =>
      'OpenCode will continue without answers to these questions.';

  @override
  String get e7WorkspaceNeedsInput => 'OpenCode needs input';

  @override
  String get e7WorkspaceSendAnswers => 'Send answers';

  @override
  String get e7WorkspaceReferenceRetry =>
      'Conversation reference unavailable. Refresh and try again.';

  @override
  String get e7WorkspaceLocationRetry =>
      'The conversation’s project is unavailable. Refresh and try again.';

  @override
  String get e7WorkspaceLocationChangedReturn =>
      'The conversation’s project changed. Return and try again.';

  @override
  String get e7WorkspaceLocationChangedRetry =>
      'The conversation’s project changed. Refresh and try again.';

  @override
  String get e7WorkspaceReferenceUnavailable =>
      'Conversation reference unavailable.';

  @override
  String get e7WorkspacePaginationStuck =>
      'Conversation pagination could not advance. Refresh the list to continue.';

  @override
  String e7WorkspaceCreateFailed(String error) {
    return 'Could not create a conversation: $error';
  }

  @override
  String get e7WorkspaceNoProjectsSearch =>
      'The server returned no projects. Search all conversations to find previous work.';

  @override
  String e7WorkspaceActiveDirectory(String directory) {
    return 'A conversation is running in this folder · $directory';
  }

  @override
  String e7WorkspaceOpenProjectCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count open on this server',
      one: '1 open on this server',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceArchivedToast(String title) {
    return 'Archived “$title”';
  }

  @override
  String e7WorkspaceDeleteDetail(String title) {
    return '“$title” and its history will be permanently removed.';
  }

  @override
  String e7WorkspaceShareDetail(String title) {
    return 'Anyone with the link can view “$title”, including its conversation and shared context. Do not share secrets, credentials, or private files.';
  }

  @override
  String e7WorkspaceRequestFor(String title) {
    return 'for $title';
  }

  @override
  String e7WorkspaceQuestionCount(int count, String title) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count questions · $title',
      one: '1 question · $title',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count subagents',
      one: '1 subagent',
    );
    return '$_temp0';
  }

  @override
  String e7WorkspaceSessionId(String id) {
    return 'Conversation $id';
  }

  @override
  String get e7WorkspaceUnknownProject => 'Unknown project';

  @override
  String get e7WorkspaceJustNow => 'Just now';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return '$count min ago';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return '${count}h ago';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return '${count}d ago';
  }

  @override
  String e7WorkspaceFileCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String get chatUiAllMatchingRequests => '(all matching requests)';

  @override
  String get chatUiNoOutput => '(no output)';

  @override
  String get chatUiNoResult => '(no result)';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen =>
      '1 reference is added as text when you send. Not saved with your draft.';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      'Add an OpenCode project reference to this prompt';

  @override
  String get chatUiAddAnImageOrFileToThe =>
      'Add an image or file to the prompt';

  @override
  String get chatUiAgent => 'Agent';

  @override
  String get chatUiAllowOnce => 'Allow once';

  @override
  String get chatUiAlreadyAnsweredElsewhere => 'Already answered elsewhere';

  @override
  String get chatUiAlreadyDelivered => 'Already delivered';

  @override
  String get chatUiAlwaysAllow => 'Always allow';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover => 'Always allow would also cover';

  @override
  String get chatUiAnswerWasCutOffByTheLength =>
      'Answer was cut off by the length limit';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      'Anyone with the link can view this conversation and its shared context. Do not share conversations containing secrets, credentials, or private files.';

  @override
  String get chatUiAppDiagnostics => 'App diagnostics';

  @override
  String get chatUiAppearance => 'Appearance';

  @override
  String get chatUiApplyPatch => 'Apply patch';

  @override
  String get chatUiAskOpenCode => 'Ask OpenCode…';

  @override
  String get chatUiAttachFile => 'Attach file';

  @override
  String get chatUiAttachToPrompt => 'Attach to prompt';

  @override
  String get chatUiAttachment => 'Attachment';

  @override
  String get chatUiAttachmentLimitReached => 'Attachment limit reached';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 =>
      'Attachments must total no more than 20 MB.';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes =>
      'Available when the current run finishes';

  @override
  String get chatUiBrowseProjectAndGlobalSkills =>
      'Browse project and global skills';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      'Browse, preview, download, and attach project files';

  @override
  String get chatUiCancelAndReturnToTheComposer =>
      'Cancel and return to the composer';

  @override
  String get chatUiCancelMessage => 'Cancel message';

  @override
  String get chatUiCancelThisPendingMessage => 'Cancel this pending message?';

  @override
  String get chatUiChangeTheActiveOpenCodeConsoleOrganization =>
      'Change the active OpenCode Console organization';

  @override
  String get chatUiChangeTheTitleShownInTheSession =>
      'Change the title shown in the conversation list';

  @override
  String get chatUiChangeThisSessionSExperimentalWorkspace =>
      'Change this conversation’s experimental cloud environment';

  @override
  String get chatUiChangedFile => 'Changed file';

  @override
  String get chatUiChanges => 'Changes';

  @override
  String get chatUiChooseAServerModelByProviderAnd =>
      'Choose a server model by provider and capability';

  @override
  String get chatUiChooseAnotherModelInThePickerTo =>
      'Choose another model in the picker to build your recent list.';

  @override
  String get chatUiChooseModel => 'Choose model';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent =>
      'Choose the active OpenCode agent';

  @override
  String get chatUiChooseTheCurrentModelVariantOrReasoning =>
      'Choose the current model variant or reasoning effort';

  @override
  String get chatUiCollapseReasoning => 'Collapse reasoning';

  @override
  String get chatUiCommandMap => 'Command map';

  @override
  String get chatUiCompactContext => 'Compact context';

  @override
  String get chatUiCompactSession => 'Compact conversation';

  @override
  String get chatUiCompactingConversation => 'Compacting conversation…';

  @override
  String get chatUiCompactionFailed => 'Compaction failed';

  @override
  String get chatUiCompactionStarted => 'Compaction started';

  @override
  String get chatUiCompose => 'Compose';

  @override
  String get chatUiConnectProvider => 'Connect provider';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      'Connection health, server version, and live mode';

  @override
  String get chatUiContextAdded => 'Context added';

  @override
  String get chatUiContextCompacted =>
      'Earlier messages were summarized to save space';

  @override
  String get chatUiCopiedPasteItIntoTheComposer =>
      'Copied. Paste it into the composer';

  @override
  String get chatUiCopyMessageText => 'Copy message text';

  @override
  String get chatUiCopyShareLink => 'Copy share link';

  @override
  String get chatUiCopyTheRenderedConversationAsMarkdown =>
      'Copy the rendered conversation as Markdown';

  @override
  String get chatUiCopyTranscript => 'Copy transcript';

  @override
  String get chatUiCreateOrCopyAPublicSessionLink =>
      'Create or copy a public conversation link';

  @override
  String get chatUiCurrentSession => 'Current conversation';

  @override
  String get chatUiDelegate => 'Delegate';

  @override
  String get chatUiDelegateThisPrompt => 'Delegate this prompt';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent =>
      'Delegate this prompt to a server subagent';

  @override
  String get chatUiDelegatedSession => 'Delegated conversation';

  @override
  String get chatUiDeleteMessage => 'Delete message';

  @override
  String get chatUiDeleteThisMessage => 'Delete this message?';

  @override
  String get chatUiDetails => 'Details';

  @override
  String get chatUiDirectory => 'Directory';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink =>
      'Disable the current public conversation link';

  @override
  String get chatUiDiscardDraft => 'Discard draft';

  @override
  String get chatUiDiscardPromptChanges => 'Discard prompt changes?';

  @override
  String get chatUiDiscardQueuedDraft => 'Discard queued draft?';

  @override
  String get chatUiDismissPromptError => 'Dismiss prompt error';

  @override
  String get chatUiEachAttachmentMustBe10MBOr =>
      'Each attachment must be 10 MB or smaller.';

  @override
  String get chatUiEdit => 'Edit';

  @override
  String get chatUiEditDraft => 'Edit draft';

  @override
  String get chatUiEditTheCurrentPromptInAFocused =>
      'Edit the current prompt in a focused full-screen view';

  @override
  String get chatUiErrorDetails => 'Error details';

  @override
  String get chatUiExpandReasoning => 'Expand reasoning';

  @override
  String get chatUiExportSessionTranscript => 'Export conversation transcript';

  @override
  String get chatUiExportTranscript => 'Export transcript';

  @override
  String get chatUiFetchPage => 'Fetch page';

  @override
  String get chatUiFiles => 'Files';

  @override
  String get chatUiFilesAreUnavailableInThisPreview =>
      'Files are unavailable in this preview.';

  @override
  String get chatUiFindACommandOrAction => 'Find a command or action';

  @override
  String get chatUiFindAMessageJumpToItOr =>
      'Find a message, jump to it, or fork from a prompt';

  @override
  String get chatUiFindASubagent => 'Find a subagent';

  @override
  String get chatUiFindFiles => 'Find files';

  @override
  String get chatUiFindSessionsAcrossEveryOpenCodeProject =>
      'Find conversations across every OpenCode project';

  @override
  String get chatUiFollowAndroidOrChooseTheNativeLight =>
      'Follow Android or choose the native light or dark theme';

  @override
  String get chatUiForkFromThisPrompt => 'Fork from this prompt';

  @override
  String get chatUiForkSession => 'Fork conversation';

  @override
  String get chatUiGeneratedFile => 'Generated file';

  @override
  String get chatUiHideTimestamps => 'Hide timestamps';

  @override
  String get chatUiImageDataIsUnavailable => 'Image data is unavailable.';

  @override
  String get chatUiInputRequested => 'Input requested';

  @override
  String get chatUiInspectGitLanguageServicesAndFormattersFor =>
      'Inspect Git, language services, and formatters for this project';

  @override
  String get chatUiInspectMCPStatusAuthenticationAndResources =>
      'Inspect MCP status, authentication, and resources';

  @override
  String get chatUiInspectCurrentTokensCacheCostAndContext =>
      'Inspect current tokens, cache, cost, and context usage';

  @override
  String get chatUiInspectToolsCallableByTheActiveProvider =>
      'Inspect tools callable by the active provider and model';

  @override
  String get chatUiItsTextReturnsToTheComposerAs =>
      'Its text returns to the composer as a draft.';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      'Jump anywhere. Fork restores a prompt for editing.';

  @override
  String get chatUiKeepItPending => 'Keep it pending';

  @override
  String get chatUiKeepItQueued => 'Keep it queued';

  @override
  String get chatUiLanguageServer => 'Language server';

  @override
  String get chatUiList => 'List';

  @override
  String get chatUiLoadingSubagents => 'Loading subagents…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript =>
      'Long reasoning collapsed in the transcript';

  @override
  String get chatUiMCPServers => 'MCP servers';

  @override
  String get chatUiManageProviderAndIntegrationAuthentication =>
      'Manage provider and integration authentication';

  @override
  String get chatUiMessage => 'Message';

  @override
  String get chatUiMessageTimeline => 'Message timeline';

  @override
  String get chatUiMessageTimestampsHidden => 'Message timestamps hidden';

  @override
  String get chatUiMessageTimestampsShown => 'Message timestamps shown';

  @override
  String get chatUiModel => 'Model';

  @override
  String get chatUiModelAndAgent => 'Model and agent';

  @override
  String get chatUiMoveSession => 'Move conversation';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory =>
      'Move this conversation to another project';

  @override
  String get chatUiMoved => 'Moved';

  @override
  String get chatUiNavigate => 'Navigate';

  @override
  String get chatUiNoAnswer => 'No answer';

  @override
  String get chatUiNoMatchingMessages => 'No matching messages';

  @override
  String get chatUiNoShareLinkWasReturned => 'No share link was returned';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer =>
      'No subagents available from this server';

  @override
  String get chatUiNotConnectedToTheServerRightNow =>
      'Not connected to the server right now.';

  @override
  String get chatUiOpenParentSession => 'Open parent conversation';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals =>
      'Open persistent project terminals';

  @override
  String get chatUiOpenProviders => 'Open providers';

  @override
  String get chatUiOpenSubagentSession => 'Open subagent conversation';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      'OpenCode commands are unavailable offline.';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt =>
      'OpenCode could not complete this prompt.';

  @override
  String get chatUiOpenCodeIsReconnecting => 'OpenCode is reconnecting.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'OpenCode is reconnecting. Try again shortly.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'OpenCode is reconnecting. Try again when the server is online.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain =>
      'OpenCode is reconnecting. Try again.';

  @override
  String get chatUiOpenCodeNeedsInput => 'OpenCode needs input';

  @override
  String get chatUiOpenCodeServerCommand => 'OpenCode server command';

  @override
  String get chatUiOutputPruned => 'Output pruned';

  @override
  String get chatUiPendingChange => 'Pending change';

  @override
  String get chatUiProjectFiles => 'Project files';

  @override
  String get chatUiProjectHealth => 'Project health';

  @override
  String get chatUiProjectReference => 'Project reference';

  @override
  String get chatUiProjectReferences => 'Project references';

  @override
  String get chatUiProjectsAndWorkspaces => 'Projects and worktrees';

  @override
  String get chatUiPromptEditor => 'Prompt editor';

  @override
  String get chatUiPromptFromParentAgent => 'Prompt from parent agent';

  @override
  String get chatUiPromptTools => 'Prompt tools';

  @override
  String get chatUiQuestion => 'Question';

  @override
  String get chatUiQuestions => 'Questions';

  @override
  String get chatUiQueuedRunsAfterThisTurn => 'Queued · runs after this turn';

  @override
  String get chatUiQueuedWillSendWhenReconnected =>
      'Queued — will send when reconnected';

  @override
  String get chatUiRead => 'Read';

  @override
  String get chatUiReasoningExpandedInTheTranscript =>
      'Reasoning expanded in the transcript';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice =>
      'Records and transcribes on this device';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      'Reference kept for your next prompt — commands do not carry it.';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      'References kept for your next prompt — commands do not carry them.';

  @override
  String get chatUiReject => 'Reject';

  @override
  String get chatUiReloadMessages => 'Refresh messages';

  @override
  String get chatUiRename => 'Rename';

  @override
  String get chatUiRenameSession => 'Rename conversation';

  @override
  String get chatUiRestoreRevertedPrompt => 'Restore reverted prompt';

  @override
  String get chatUiRestoreTheCurrentlyRevertedSessionState =>
      'Restore the currently reverted conversation state';

  @override
  String get chatUiRetryLastPrompt => 'Retry last prompt';

  @override
  String get chatUiRevertLastPrompt => 'Undo last prompt';

  @override
  String get chatUiReviewCommentAddedToThePrompt =>
      'Review comment added to the prompt';

  @override
  String get chatUiReviewHandledAppErrorsAndSendA =>
      'Review handled app errors and send a redacted report';

  @override
  String get chatUiReviewTheActualDiffForThisSession =>
      'Review the actual diff for this conversation';

  @override
  String get chatUiRunOnYourComputer => 'Run on your computer';

  @override
  String get chatUiRunShellCommand => 'Run shell command';

  @override
  String get chatRunShellLabel => 'Command';

  @override
  String get chatRunShellHint => 'npm test';

  @override
  String get chatRunShellHelper =>
      'The agent runs it in this project, and its output joins the conversation.';

  @override
  String get chatRunShellEmpty => 'Type a command to run.';

  @override
  String get chatRenameEmpty => 'Type a title.';

  @override
  String get chatUiSaveTheConversationAsAMarkdownFile =>
      'Save the conversation as a Markdown file';

  @override
  String get chatUiScope => 'Scope';

  @override
  String get chatUiSearchMessages => 'Search messages';

  @override
  String get chatUiSearchMobileActionsAndServerProvidedCommands =>
      'Search mobile actions and server-provided commands';

  @override
  String get chatUiSearchText => 'Search text';

  @override
  String get chatUiSelectAModelBeforeCompactingThisSession =>
      'Select a model before compacting this conversation.';

  @override
  String get chatUiSendNowAndSteerInstead => 'Send now and steer instead';

  @override
  String get chatUiServerCommands => 'Server commands';

  @override
  String get chatUiServerMessage => 'Server message';

  @override
  String get chatUiServerStatus => 'Server status';

  @override
  String get chatUiSessionChanges => 'Conversation changes';

  @override
  String get chatUiSessionContext => 'Conversation context';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      'Conversation shared. Copy the visible link manually.';

  @override
  String get chatUiShareLinkCopied => 'Share link copied';

  @override
  String get chatUiShareSession => 'Share conversation';

  @override
  String get chatUiShareThisSession => 'Share this conversation?';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView =>
      'Shared: anyone with the link can view';

  @override
  String get chatUiShowAllSubagentSessions => 'Show all subagent conversations';

  @override
  String get chatUiShowTimestamps => 'Show timestamps';

  @override
  String get chatUiSkill => 'Skill ·';

  @override
  String get chatUiSkills => 'Skills';

  @override
  String get chatUiSlashCommandsAndAgents => 'Slash commands and agents';

  @override
  String get chatUiStartACleanSessionInThisWorkspace =>
      'Start a clean conversation in this project';

  @override
  String get chatUiStopSharing => 'Stop sharing';

  @override
  String get chatUiSubagent => 'Subagent';

  @override
  String get chatUiSubagentFailed => 'Subagent failed.';

  @override
  String get chatUiSubagentWorking => 'Subagent working…';

  @override
  String get chatUiSubagentsCouldNotBeLoaded => 'Subagents could not be loaded';

  @override
  String get chatUiSummarizeTheSessionUsingTheSelectedModel =>
      'Summarize the conversation using the selected model';

  @override
  String get chatUiSwitchOrganization => 'Switch organization';

  @override
  String get chatUiSwitchProjectDirectoryOrWorktree =>
      'Switch project, directory, or worktree';

  @override
  String get chatUiSystemUpdate => 'System update';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo =>
      'Tell the agent why, or what to do instead (optional)';

  @override
  String get chatUiThatMessageIsNoLongerInThis =>
      'That message is no longer in this conversation.';

  @override
  String get chatUiTheFileHasNoContentToAttach =>
      'The file has no content to attach.';

  @override
  String get chatUiTheFileHasNoContentToSave =>
      'The file has no content to save.';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe =>
      'The form or project changed. Reopen the current request.';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom =>
      'The generated file is not available from this server.';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      'The message and all of its parts are permanently removed from the conversation, so future replies no longer see them. File changes it made are not reverted.';

  @override
  String get chatUiTheServerReturnedEmptyImageData =>
      'The server returned empty image data.';

  @override
  String get chatUiThisDraftHasNotBeenSentTo =>
      'This draft has not been sent to OpenCode.';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      'This draft is too large to queue, or the queue is full of newer drafts. Remove an attachment, or clear queued prompts in Settings.';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn =>
      'This prompt cannot be restored because an attachment is unavailable.';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn =>
      'This prompt cannot be retried because an attachment is unavailable.';

  @override
  String get chatUiTimeline => 'Timeline';

  @override
  String get chatUiTimestampsUsage => 'Timestamps & usage';

  @override
  String get chatUiTitle => 'Title';

  @override
  String get chatUiTodos => 'Tasks';

  @override
  String get chatUiToggleCreationTimesBesideTranscriptEntries =>
      'Toggle creation times beside transcript entries';

  @override
  String get chatUiToggleLongReasoningDetailsAcrossTheTranscript =>
      'Toggle long reasoning details across the transcript';

  @override
  String get chatUiToolFailed => 'Tool failed.';

  @override
  String get chatUiToolsAndCapabilities => 'Tools and capabilities';

  @override
  String get chatUiTranscriptCopiedAsMarkdown =>
      'Transcript copied as Markdown';

  @override
  String get chatUiTranscriptDisplay => 'Transcript display';

  @override
  String get chatUiTranscriptSaved => 'Transcript saved';

  @override
  String get chatUiVoiceConversationWasInterrupted =>
      'Voice conversation was interrupted.';

  @override
  String get chatUiVoiceInput => 'Voice input';

  @override
  String get chatUiVoiceInputIsUnavailable => 'Voice input is unavailable.';

  @override
  String get chatUiWaitForThisRunInstead => 'Wait for this run instead';

  @override
  String get chatUiWebSearch => 'Web search';

  @override
  String get chatUiWrite => 'Write';

  @override
  String get chatUiWriteYourOpenCodePrompt => 'Write your OpenCode prompt…';

  @override
  String get chatUiYou => 'You';

  @override
  String get chatUiYourOriginalComposerDraftAndAttachmentsWill =>
      'Your original composer draft and attachments will stay unchanged.';

  @override
  String get chatUiInThisChat => 'in this conversation';

  @override
  String get chatUiNewFile => 'new file';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return 'Queued — will send when reconnected. $detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '/$command is not available right now.';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return 'You can attach up to $count files.';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Sent $count queued prompts',
      one: 'Sent 1 queued prompt',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count drafts waiting for other servers',
      one: '1 draft waiting for other servers',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return 'Next turns in this conversation use $model.';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '@$name is already in the prompt';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return '$filename attached. Add your comment.';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return 'Save $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return '$filename saved to your device.';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count drafts queued to send on reconnect.',
      one: '1 draft queued to send on reconnect.',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count drafts waiting for other servers.',
      one: '1 draft waiting for other servers.',
    );
    return '$_temp0';
  }

  @override
  String chatUiQuestionCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count questions',
      one: '1 question',
    );
    return '$_temp0';
  }

  @override
  String chatUiPermissionNeeded(Object title) {
    return 'Permission needed: $title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return 'Question: $title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count questions',
      one: '1 question',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return 'Rate limited. Retrying$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return 'Rate limited. Retrying$attempt in $time';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count references are added as text when you send. Not saved with your draft.',
      one:
          '1 reference is added as text when you send. Not saved with your draft.',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '$count attached';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier messages',
      one: '1 earlier message',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count tok';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '$position of $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return 'Delegated conversation · $count';
  }

  @override
  String chatUiToolsSummary(Object tools) {
    return 'Tools: $tools';
  }

  @override
  String chatUiFromLine(Object line) {
    return 'from $line';
  }

  @override
  String chatUiLineCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines',
      one: '1 line',
    );
    return '$_temp0';
  }

  @override
  String chatUiLineRange(Object start, Object end) {
    return 'L$start–$end';
  }

  @override
  String chatUiEntryCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count entries',
      one: '1 entry',
    );
    return '$_temp0';
  }

  @override
  String chatUiFoundCount(Object count) {
    return '$count found';
  }

  @override
  String chatUiMatchCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count matches',
      one: '1 match',
    );
    return '$_temp0';
  }

  @override
  String chatUiFileCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String chatUiProviderSearch(Object provider) {
    return '$provider search';
  }

  @override
  String chatUiResultCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results',
      one: '1 result',
    );
    return '$_temp0';
  }

  @override
  String chatUiCompletedCount(Object done, Object total) {
    return '$done/$total completed';
  }

  @override
  String chatUiAnsweredCount(Object count) {
    return '$count answered';
  }

  @override
  String chatUiAskedCount(Object count) {
    return '$count asked';
  }

  @override
  String chatUiDurationMinutesSeconds(Object minutes, Object seconds) {
    return '${minutes}m ${seconds}s';
  }

  @override
  String chatUiDurationSeconds(Object seconds) {
    return '${seconds}s';
  }

  @override
  String chatUiFileLoadFailed(Object error) {
    return 'Could not load this file from the OpenCode server: $error';
  }

  @override
  String chatUiMoreEntries(Object total) {
    return '$total total · more available';
  }

  @override
  String chatUiEntryTotal(Object total) {
    return '$total entries';
  }

  @override
  String chatUiAnsweredDetail(Object answer) {
    return 'Answered: $answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return 'Loading $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return 'Preview generated image $filename';
  }

  @override
  String chatUiParentSession(Object title) {
    return 'Parent · $title';
  }

  @override
  String chatUiChooseOption(Object option) {
    return 'Choose: $option';
  }

  @override
  String get chatUiMainSession => 'Main conversation';

  @override
  String get chatUiTodo => 'To do';

  @override
  String get chatUiBackgroundResult => 'Background result';

  @override
  String get chatUiBackgroundComplete => 'Completed';

  @override
  String get chatUiBackgroundError => 'Failed';

  @override
  String get chatUiBackgroundCancelled => 'Cancelled';

  @override
  String get chatUiResultSourceDetails => 'Server message details';

  @override
  String get chatUiResultOpenChild => 'Open subagent conversation';

  @override
  String get chatUiNoResultText => 'The server returned no result text.';

  @override
  String get chatUiTimedOut => 'Timed out';

  @override
  String get chatUiKilled => 'Stopped';

  @override
  String get chatUiTruncated => 'Truncated';

  @override
  String get chatUiUpdated => 'Updated';

  @override
  String get chatUiError => 'Error';

  @override
  String get chatUiAssistant => 'Assistant';

  @override
  String get chatUiUser => 'User';

  @override
  String get chatUiOpenCodeSession => 'OpenCode conversation';

  @override
  String get chatUiTool => 'Tool';

  @override
  String get chatUiFile => 'file';

  @override
  String get e7LibraryReportABug => 'Report a problem';

  @override
  String get e7LibraryKeyboardShortcuts => 'Keyboard shortcuts';

  @override
  String get e7LibraryHTTPHeader => 'HTTP header';

  @override
  String get e7LibraryEnvironmentVariable => 'environment variable';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      'OpenCode is reconnecting. Try again shortly.';

  @override
  String get e7LibraryWhatIsMCP => 'What is MCP?';

  @override
  String get e7LibrarySavingConfiguration => 'Saving configuration';

  @override
  String get e7LibrarySaveMCPServer => 'Save MCP server';

  @override
  String get e7LibraryThisProject => 'This project';

  @override
  String e7LibraryWritesOnlyTo(String detail1) {
    return 'Writes only to $detail1.';
  }

  @override
  String get e7LibraryWritesToThisOpenCodeServerSGlobal =>
      'Writes to this OpenCode server’s global configuration.';

  @override
  String get e7LibraryServerName => 'Server name';

  @override
  String get e7LibraryDocsOrBrowserTools => 'docs or browser-tools';

  @override
  String get e7LibraryUniqueWithinTheSelectedConfiguration =>
      'Unique within the selected configuration.';

  @override
  String get e7LibraryEnterAServerName => 'Enter a server name';

  @override
  String get e7LibraryRemoteURL => 'Remote URL';

  @override
  String get e7LibraryLocalCommand => 'Local command';

  @override
  String get e7LibraryOptional => 'Optional';

  @override
  String get e7LibraryEnterAValueGreaterThanZero =>
      'Enter a value greater than zero';

  @override
  String get e7LibraryMCPEndpointURL => 'MCP endpoint URL';

  @override
  String get e7LibraryHTTPIsAcceptedForLocalDevelopmentServers =>
      'HTTP is accepted for local development servers.';

  @override
  String get e7LibraryEnterAValidHTTPOrHTTPSURL =>
      'Enter a valid HTTP or HTTPS URL without credentials';

  @override
  String get e7LibraryDetectOAuthAutomatically => 'Detect OAuth automatically';

  @override
  String get e7LibraryTurnThisOffWhenTheServerUses =>
      'Turn this off when the server uses headers and should never start OAuth.';

  @override
  String get e7LibraryCommandAndArguments => 'Command and arguments';

  @override
  String get e7LibraryRunsOnTheOpenCodeServerNotThis =>
      'Runs on the OpenCode server, not this phone. Enter one argument per line.';

  @override
  String get e7LibraryEnterACommand => 'Enter a command';

  @override
  String get e7LibraryWorkingDirectory => 'Working directory';

  @override
  String get e7LibraryOptionalServerPath => 'Optional server path';

  @override
  String get e7LibraryEnvironmentVariables => 'Environment variables';

  @override
  String e7LibraryInvalidOnLineUseKEYVALUE(String detail1, String detail2) {
    return 'Invalid $detail1 on line $detail2. Use KEY=VALUE.';
  }

  @override
  String e7LibraryInvalidNameOnLine(String detail1, String detail2) {
    return 'Invalid $detail1 name on line $detail2.';
  }

  @override
  String e7LibraryDuplicateName(String detail1, String detail2) {
    return 'Duplicate $detail1 name \"$detail2\".';
  }

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgain =>
      'OpenCode is reconnecting. Try again.';

  @override
  String get e7LibraryRevokeAlwaysAllowedAction => 'Revoke access?';

  @override
  String get e7LibraryAction => 'Action';

  @override
  String get e7LibraryResource => 'Resource';

  @override
  String get e7LibraryRevokeAccess => 'Revoke access';

  @override
  String get e7LibraryAlwaysAllowedActionRevoked =>
      'Always allowed action revoked';

  @override
  String get e7LibraryAlwaysAllowedActions => 'Always allowed actions';

  @override
  String get e7LibraryNoAlwaysAllowedActions => 'No always allowed actions';

  @override
  String get e7LibraryTheLastActionFailed => 'The last action failed';

  @override
  String e7LibraryRevokeAccess2(String detail1) {
    return 'Revoke $detail1 access';
  }

  @override
  String get e7LibraryToolsAndCapabilities => 'Tools and capabilities';

  @override
  String get e7LibraryRefreshTools => 'Refresh tools';

  @override
  String get e7LibraryOpenCodeToolsDependOnTheProviderAnd =>
      'OpenCode tools depend on the provider and model used by the active conversation.';

  @override
  String get e7LibraryChooseModel => 'Choose model';

  @override
  String e7LibrarySearchTools2(String detail1) {
    return 'Search $detail1 tools';
  }

  @override
  String get e7LibraryRegisteredInventoryUnavailable =>
      'registered inventory unavailable';

  @override
  String get e7LibraryServerCapabilityUnavailable =>
      'server capability unavailable';

  @override
  String get e7LibraryNoToolsForThisModel => 'No tools for this model';

  @override
  String get e7LibraryNoDescriptionReturnedByOpenCode =>
      'No description returned by OpenCode';

  @override
  String get e7LibraryCopyParameterSchema => 'Copy parameter schema';

  @override
  String get e7LibraryNoProjectSelected => 'No project selected';

  @override
  String get e7LibraryNoProjectFolderIsOpenChooseOne =>
      'No project folder is open. Choose one from Work.';

  @override
  String get e7LibrarySwitchProject => 'Switch project';

  @override
  String get e7LibraryWorktrees => 'Worktrees';

  @override
  String get e7LibraryManagedWorkspaces => 'Cloud environments';

  @override
  String get e7LibraryProjectHealth => 'Project health';

  @override
  String get e7LibraryOpenCodeIsReconnecting => 'OpenCode is reconnecting.';

  @override
  String get e7LibraryCloudEnvironments => 'Cloud environments';

  @override
  String get e7LibraryDiscoverExistingEnvironments =>
      'Discover existing environments';

  @override
  String get e7LibraryNewEnvironment => 'New environment';

  @override
  String get e7LibraryEnvironments => 'Environments';

  @override
  String get e7LibraryNoCloudEnvironments => 'No cloud environments';

  @override
  String get e7LibraryEnvironmentRefreshFailed => 'Environment refresh failed';

  @override
  String get e7LibraryRetryCloudEnvironments => 'Try again';

  @override
  String get e7LibraryAdapterRefreshFailed => 'Adapter refresh failed';

  @override
  String get e7LibraryConnected => 'Connected';

  @override
  String get e7LibraryConnecting => 'Connecting';

  @override
  String get e7LibraryDisconnected => 'Disconnected';

  @override
  String get e7LibraryEnvironmentActions => 'Environment actions';

  @override
  String get e7LibraryOpenAgain => 'Open again';

  @override
  String get e7LibraryNewManagedWorkspace => 'New cloud environment';

  @override
  String get e7LibraryCreateAndOpen => 'Create and open';

  @override
  String e7LibraryRemove(String detail1) {
    return 'Delete $detail1?';
  }

  @override
  String get e7LibraryRemovePermanently => 'Delete permanently';

  @override
  String e7LibraryIsReady(String detail1) {
    return '$detail1 is ready';
  }

  @override
  String get e7LibraryOpenCodeCouldNotPrepareThisWorktree =>
      'OpenCode could not prepare this worktree.';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return '$detail1 was created. Its setup status is not yet confirmed.';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis =>
      'Wait for OpenCode to finish preparing this worktree.';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations =>
      'OpenCode did not switch projects.';

  @override
  String e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
    String detail1,
    String detail2,
  ) {
    return 'Could not verify $detail1 before this destructive action: $detail2';
  }

  @override
  String e7LibraryResetToTheDefaultBranch(String detail1) {
    return '$detail1 reset to the default branch';
  }

  @override
  String e7LibraryAndItsBranchWereRemoved(String detail1) {
    return '$detail1 and its branch were deleted';
  }

  @override
  String e7LibraryReset(String detail1) {
    return 'Reset $detail1?';
  }

  @override
  String get e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes =>
      'This permanently discards tracked changes and deletes all untracked and ignored files. Submodules are also reset and cleaned. This cannot be undone.';

  @override
  String get e7LibraryResetWorktree => 'Reset worktree';

  @override
  String get e7LibraryRefreshWorktrees => 'Refresh worktrees';

  @override
  String get e7LibraryNewWorktree => 'New worktree';

  @override
  String get e7LibraryNoIsolatedWorktreesYet => 'No isolated worktrees yet';

  @override
  String get e7LibraryPreparingFilesAndProjectTasks =>
      'Preparing files and project tasks…';

  @override
  String get e7LibraryWorktreeActions => 'Worktree actions';

  @override
  String get e7LibraryReset2 => 'Reset';

  @override
  String get e7LibraryNameOptional => 'Name (optional)';

  @override
  String get e7LibraryTheWorktreeDirectoryAndItsGitBranch =>
      'The worktree directory and its Git branch will be permanently deleted. Existing conversations remain in history, but their working directory will no longer exist.';

  @override
  String get e7LibraryInitializeGitRepository => 'Initialize Git repository?';

  @override
  String get e7LibraryOpenCodeWillRunGitInitInThe =>
      'OpenCode will run git init in the current project. Existing files will not be changed or committed. This enables branch, working-tree, and Review features.';

  @override
  String get e7LibraryInitializeGit => 'Initialize Git';

  @override
  String get e7LibraryGitRepositoryInitialized => 'Git repository initialized';

  @override
  String get e7LibraryVersionControl => 'Version control';

  @override
  String get e7LibraryLanguageServices => 'Language services';

  @override
  String get e7LibraryFormatters => 'Formatters';

  @override
  String get e7LibraryGitIsNotInitialized => 'Git is not initialized';

  @override
  String get e7LibraryInitializeThisProjectToEnableBranchesWorking =>
      'Initialize this project to enable branches, working-tree changes, and Review.';

  @override
  String get e7LibraryRunGitInitFromATerminal =>
      'Run `git init` from a terminal';

  @override
  String get e7LibraryGitInitializationFailed => 'Git initialization failed';

  @override
  String get e7LibraryNoActiveBranch => 'No active branch';

  @override
  String e7LibraryDefaultBranch(String detail1) {
    return 'Default branch: $detail1';
  }

  @override
  String get e7LibraryWorkingTreeIsClean => 'Working tree is clean';

  @override
  String e7LibraryChangedFiles(String detail1) {
    return '$detail1 changed files';
  }

  @override
  String get e7LibraryNoUncommittedChanges => 'No uncommitted changes';

  @override
  String get e7LibraryNoActiveLanguageServices => 'No active language services';

  @override
  String get e7LibraryOpenCodeActivatesThemWhileItInspectsSupported =>
      'OpenCode activates them while it inspects supported source files during coding.';

  @override
  String get e7LibraryNoFormattersConfigured => 'No formatters configured';

  @override
  String get e7LibraryEnabled => 'Enabled';

  @override
  String get e7LibraryDisabled => 'Disabled';

  @override
  String e7LibraryLoading(String detail1) {
    return 'Loading $detail1';
  }

  @override
  String get e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe =>
      'Automatic callback capture is unavailable. Paste the callback URL or authorization code.';

  @override
  String get e7LibraryThePhoneIsSecurelyListeningForThis =>
      'The phone is securely listening for this authorization callback. You can also enter it manually.';

  @override
  String get e7LibraryCallbackURLOrCode => 'Callback URL or code';

  @override
  String get e7LibraryUpdating => 'Updating…';

  @override
  String get e7LibraryNotConnected => 'Not connected';

  @override
  String get e7LibraryServerEnvironment => 'Server environment';

  @override
  String get e7LibraryServerManaged => 'Server-managed';

  @override
  String get e7LibraryConnect => 'Connect';

  @override
  String get e7LibraryAuthenticationFailed => 'Authentication failed';

  @override
  String get e7LibraryAuthenticationAttemptExpired =>
      'Authentication attempt expired';

  @override
  String get e7LibraryAuthenticationComplete => 'Authentication complete';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe =>
      'Return from the browser and enter the authorization code.';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      'Finish authentication in the browser, then check its status.';

  @override
  String get e7LibraryAuthorizationCode => 'Authorization code';

  @override
  String get e7LibrarySelectAnOption => 'Select an option';

  @override
  String get e7LibraryEnterAValue => 'Enter a value';

  @override
  String get e7LibraryTheServerReturnedAnUnsafeAuthorizationLink =>
      'The server returned an unsafe authorization link. Only HTTPS links with a valid host and no embedded credentials are allowed.';

  @override
  String get e7LibraryCouldNotLoadThisSection => 'Could not load this section';

  @override
  String get e7LibrarySkills => 'Skills';

  @override
  String get e7LibraryNoSkillsAvailable => 'No skills available';

  @override
  String get e7LibraryModelsAndAgents => 'Models and agents';

  @override
  String get e7LibraryUnavailable => 'Unavailable';

  @override
  String get e7LibraryFinishOrCancelTheCurrentMCPAuthorization =>
      'Finish or cancel the current MCP authorization first.';

  @override
  String get e7LibraryCouldNotOpenTheAuthorizationPage =>
      'Could not open the authorization page';

  @override
  String e7LibraryAuthenticated(String detail1) {
    return '$detail1 authenticated';
  }

  @override
  String get e7LibraryCouldNotConfirmMCPAuthentication =>
      'Could not confirm MCP authentication';

  @override
  String get e7LibraryMCPServerSavedInOpenCode =>
      'MCP server saved in OpenCode';

  @override
  String get e7LibraryMCPUnavailable => 'MCP unavailable';

  @override
  String get e7LibraryMCPAndIntegrations => 'Providers and MCP';

  @override
  String get e7LibraryCouldNotSaveSignInRecovery =>
      'Could not save sign-in recovery.';

  @override
  String get e7LibraryNoProviderConnectionsAvailable =>
      'No provider connections available';

  @override
  String get e7LibraryThisServerDidNotReturnAnyProvider =>
      'This server did not return any provider integrations.';

  @override
  String get e7LibrarySearchProvidersOrModels => 'Search providers or models';

  @override
  String get e7LibraryNoMCPServersConfigured => 'No MCP servers configured';

  @override
  String get e7LibrarySaveOneForThisProjectOrEvery =>
      'Save one for this project or every project on the server.';

  @override
  String get e7LibraryAddAnMCPServer => 'Add an MCP server';

  @override
  String get e7LibraryAuthorizing => 'Authorizing';

  @override
  String get e7LibraryResources => 'Resources';

  @override
  String get e7LibraryNoResourcesAvailable => 'No resources available';

  @override
  String get e7LibraryConnectedMCPServersHaveNotExposedAny =>
      'Connected MCP servers have not exposed any resources.';

  @override
  String get e7LibraryOpenBrowser => 'Open browser';

  @override
  String get e7LibraryConnectedAndToolsAreAvailable =>
      'Connected and tools are available';

  @override
  String get e7LibraryConnectionFailed => 'Connection failed';

  @override
  String get e7LibraryAuthenticationRequired => 'Authentication required';

  @override
  String get e7LibraryClientRegistrationRequired =>
      'Client registration required';

  @override
  String e7LibraryStoredCredential(String detail1) {
    return 'Stored credential: $detail1';
  }

  @override
  String get e7LibraryNoConnectionMethodsAvailable =>
      'No connection methods available';

  @override
  String get e7LibraryConfiguredOnTheServer => 'Configured on the server';

  @override
  String e7LibraryDisconnect2(String detail1) {
    return 'Disconnect $detail1?';
  }

  @override
  String e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
    String detail1,
  ) {
    return '$detail1 credential removed; server environment remains active';
  }

  @override
  String e7LibraryDisconnected2(String detail1) {
    return '$detail1 disconnected';
  }

  @override
  String e7LibraryConnect2(String detail1) {
    return 'Connect $detail1';
  }

  @override
  String get e7LibraryAuthorizationWasNotOpenedThePendingAttempt =>
      'Authorization was not opened. The pending attempt is retained.';

  @override
  String get e7LibraryCouldNotOpenOAuth => 'Could not open OAuth';

  @override
  String e7LibraryIsConnected(String detail1) {
    return '$detail1 is connected';
  }

  @override
  String get e7LibraryTheSignInSourceChanged => 'The sign-in source changed.';

  @override
  String get e7LibraryCouldNotConfirmAuthenticationReturnToThe =>
      'Could not confirm authentication. Return to the original source and try again.';

  @override
  String get e7LibraryNoServerCommandsFound => 'No server commands found';

  @override
  String get e7LibraryCommandsFromYourProjectAndSkillsAppear =>
      'Commands from your project and skills appear here.';

  @override
  String get e7LibraryNoDescription => 'No description';

  @override
  String get e7LibraryServerCommands => 'Server commands';

  @override
  String get e7LibraryReferences => 'References';

  @override
  String get e7LibraryNoReferencesConfigured => 'No references configured';

  @override
  String e7LibraryChangedFilesDetected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files were detected.',
      one: '1 changed file was detected.',
    );
    return '$_temp0';
  }

  @override
  String get e7LibraryEnvironmentRemainsAfterDisconnect =>
      'This provider also uses the server environment, which mobile cannot remove and which will remain active.';

  @override
  String get e7LibrarySearchImportAliases =>
      'backup restore transfer JSON conversation session chat';

  @override
  String get e7LibrarySearchShortcutsAliases => 'hotkeys help desktop';

  @override
  String get e7SetupApiKeyHint => 'Paste an API key';

  @override
  String get e7SetupBrowserHint =>
      'Opens a browser. If the redirect cannot reach OpenCode, paste the callback URL here.';

  @override
  String get e7SetupDeviceCodeHint =>
      'Uses a one-time code. Works from a phone.';

  @override
  String get e7SetupAccountHint => 'Sign in with your account';

  @override
  String get e7SetupNewTerminalDetail => 'Start a shell in the active project.';

  @override
  String get e7SetupShowPassword => 'Show server password';

  @override
  String get e7SetupAuthFailed =>
      'The server started but authentication failed.';

  @override
  String get e7SetupScanInstruction =>
      'Point the camera at the QR code printed by opencode2 pair.';

  @override
  String get e7SetupRightKey => 'Right arrow key';

  @override
  String get e7SetupRestartReconnectFailed =>
      'The local server restarted, but the app could not reconnect.';

  @override
  String get e7SetupInstallingOpenCode => 'Installing OpenCode';

  @override
  String get e7SetupNoOutput => 'No terminal output yet.';

  @override
  String get e7SetupFollowLog => 'Follow the server log';

  @override
  String get e7SetupOpenSetupGuide => 'Open the setup guide';

  @override
  String get e7SetupDownKey => 'Down arrow key';

  @override
  String get e7SetupVerifyContinue => 'Verify & continue';

  @override
  String get e7SetupAccessibleTerminal => 'Use accessible transcript and input';

  @override
  String get e7SetupCameraFailedDetail =>
      'Another app may be holding the camera. Pasting the pairing code works either way.';

  @override
  String get e7SetupTermuxNoAnswer =>
      'Termux didn\'t answer. Tap Copy & open Termux, paste the line in Termux and press Enter, then come back here.';

  @override
  String get e7SetupCopyOpenTermux => 'Copy & open Termux';

  @override
  String get e7SetupStopTerminalDetail =>
      'The running process and its child processes will be terminated.';

  @override
  String get e7SetupEdit => 'Edit';

  @override
  String get e7SetupServerPassword => 'Server password';

  @override
  String get e7SetupHttpsHint =>
      'https:// for other computers; http:// only on this device or a private network.';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      'The local server is ready, but its observed version could not be saved. Refresh setup to try again.';

  @override
  String get e7SetupPasteInstead => 'Paste it instead';

  @override
  String get e7SetupConfirmUpdate => 'Update managed OpenCode?';

  @override
  String get e7SetupInstallServiceDetail =>
      'Official installer plus a systemd user service that survives closed terminals and reboots.';

  @override
  String get e7SetupUpdateHost => 'Update OpenCode on the host';

  @override
  String get e7SetupServiceStatus => 'Service status';

  @override
  String get e7SetupKeepAfterLogout => 'Keep it running after logout';

  @override
  String get e7SetupGetTermux => 'Get Termux';

  @override
  String get e7SetupRestartServer => 'Restart the server';

  @override
  String get e7SetupResumeSetup => 'Retry — resumes where setup left off';

  @override
  String get e7SetupHidePassword => 'Hide server password';

  @override
  String get e7SetupControlKeys =>
      'Terminal control keys. Swipe horizontally for more.';

  @override
  String get e7SetupEndInputKey => 'End of input, Control D';

  @override
  String get e7SetupGuidanceSaveFailed =>
      'Could not save connection guidance. Retry saving.';

  @override
  String get e7SetupServerOperation => 'Server operation in progress';

  @override
  String get e7SetupNewTerminal => 'New terminal';

  @override
  String get e7SetupUnsavedProfile => 'The server has not been saved.';

  @override
  String get e7SetupCheckingInstall => 'Checking installed environment...';

  @override
  String get e7SetupInspectTermuxFailed => 'Android could not inspect Termux.';

  @override
  String get e7SetupUsername => 'Username (optional)';

  @override
  String get e7SetupFirstSetupDuration =>
      'First-time setup can take 10–15 minutes. You can leave this screen and return; setup keeps running.';

  @override
  String get e7SetupNoTerminals => 'No terminal processes';

  @override
  String get e7SetupEscapeKey => 'Escape key';

  @override
  String get e7SetupLeftKey => 'Left arrow key';

  @override
  String get e7SetupInstallService =>
      'Install OpenCode as a background service';

  @override
  String get e7SetupSaveToFinish => 'Connected — save to finish.';

  @override
  String get e7SetupPasswordStartupHint =>
      'Shown when the server starts. Leave empty if it has none.';

  @override
  String get e7SetupInstallTermuxDetail =>
      'Install the current F-Droid build of Termux, then return here.';

  @override
  String get e7SetupStopBeforeUpdate =>
      'Stop active generation before updating OpenCode.';

  @override
  String get e7SetupRestartingLocal => 'Restarting the local server';

  @override
  String get e7SetupAddServer => 'Add server';

  @override
  String get e7SetupInteractiveTerminal => 'Use interactive terminal';

  @override
  String get e7SetupInstallingUbuntu => 'Setting up Ubuntu';

  @override
  String get e7SetupInterruptKey => 'Interrupt, Control C';

  @override
  String get e7SetupServerUrl => 'Server URL';

  @override
  String get e7SetupUsbAccess => 'Reach it from this phone over USB';

  @override
  String get e7SetupWaitingTermux =>
      'Waiting for Termux to respond. This can take a little while.';

  @override
  String get e7SetupDiscardChanges => 'Discard server changes?';

  @override
  String get e7SetupPasswordRequired => 'Can\'t read the saved password';

  @override
  String get e7SetupPairing => 'Pairing…';

  @override
  String get e7SetupCommandInput => 'Terminal command input';

  @override
  String get e7SetupCameraFailed => 'The camera could not be opened';

  @override
  String get e7SetupPastePassword => 'Paste server password';

  @override
  String get e7SetupSavingLocal => 'Saving local server settings';

  @override
  String get e7SetupCopyFailureReport => 'Copy failure report';

  @override
  String get e7SetupCameraDisabled => 'Camera access is turned off';

  @override
  String get e7SetupPastePairing => 'Paste pairing code';

  @override
  String get e7SetupServers => 'Servers';

  @override
  String get e7SetupStartingSetup => 'Starting setup in Termux';

  @override
  String get e7SetupSetupNotStarted =>
      'Termux opened but the setup did not start. Retry once; if it happens again, copy the failure report.';

  @override
  String get e7SetupThisDevice => 'This device (Termux)';

  @override
  String get e7SetupTransportReconnecting =>
      'The server transport is reconnecting.';

  @override
  String get e7SetupStepUnavailable => 'not yet available';

  @override
  String get e7SetupUpdateHostDetail =>
      'When the server reports an update, Settings offers the native upgrade first; this is the host-side equivalent.';

  @override
  String get e7SetupMissingPasswordShort =>
      'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires one.';

  @override
  String get e7SetupTesting => 'Testing…';

  @override
  String get e7SetupScanPairing => 'Scan pairing code';

  @override
  String get e7SetupRestartUnconfirmed =>
      'Could not confirm this restart. Refresh its progress before retrying.';

  @override
  String get e7SetupExistingMissingCredential =>
      'A local server exists, but its saved credential is unavailable. Run setup again to replace it safely.';

  @override
  String get e7SetupPreparingModels => 'Getting models ready';

  @override
  String get e7SetupHostInstructions =>
      'These commands run on the computer that hosts this server — the app cannot run them for you. Copy each one into a terminal on that machine.';

  @override
  String get e7SetupTranscript => 'Terminal transcript';

  @override
  String get e7SetupHostFirstSetup => 'First-time setup — run on your computer';

  @override
  String get e7SetupNoCameraDetail =>
      'There is nothing to scan with. Run opencode2 pair on the server, copy the code it prints, and paste it into the server editor.';

  @override
  String get e7SetupDiscard => 'Discard';

  @override
  String get e7SetupConnectionClosed => 'Connection closed';

  @override
  String get e7SetupUpKey => 'Up arrow key';

  @override
  String get e7SetupV1Limited =>
      'This app targets OpenCode 2; some features are unavailable on v1 servers.';

  @override
  String get e7SetupConnect => 'Connect';

  @override
  String get e7SetupRemoveTerminalDetail =>
      'This terminal record will be removed.';

  @override
  String get e7SetupInputDisconnected =>
      'Input is unavailable while disconnected.';

  @override
  String get e7SetupHostCopied => 'Copied. Run it on the server\'s computer.';

  @override
  String get e7SetupCameraPrivacy =>
      'The camera is used only to read the QR that opencode2 pair prints, and only while this screen is open. You can paste the code instead — it does exactly the same thing.';

  @override
  String get e7SetupIsV2 => 'This is an OpenCode 2 server.';

  @override
  String get e7SetupResumeLive => 'Resume live view';

  @override
  String get e7SetupTabKey => 'Tab key';

  @override
  String get e7SetupCloseScanner => 'Close the scanner';

  @override
  String get e7SetupChooseContinue => 'Choose how to continue';

  @override
  String get e7SetupTermuxOutdated =>
      'This Termux is too old for the app to use. Install the current one, then tap Continue setup.';

  @override
  String get e7SetupCameraNeeded => 'Camera access is needed to scan';

  @override
  String get e7SetupTerminalActions => 'Terminal actions';

  @override
  String get e7SetupReadPassword => 'Read the server password for this app';

  @override
  String get e7SetupStartInstalled => 'Start installed OpenCode?';

  @override
  String get e7SetupTestConnection => 'Test connection';

  @override
  String get e7SetupCheckingTermux => 'Checking Termux connection';

  @override
  String get e7SetupCheckingTermuxShort => 'Checking Termux...';

  @override
  String get e7SetupDefaultServer => 'OpenCode server';

  @override
  String get e7SetupSaving => 'Saving…';

  @override
  String get e7SetupLinuxService => 'Run as a Linux service';

  @override
  String get e7SetupPairingDesktopHint =>
      'Check that the server is running, and that the address it printed is one this machine can reach.';

  @override
  String get e7SetupAboutNotices => 'About and open source notices';

  @override
  String get e7SetupSetupLost => 'Lost track of the setup running in Termux';

  @override
  String get e7SetupReportCopied => 'Failure report copied.';

  @override
  String get e7SetupAppSettings => 'Allow the permission in Settings';

  @override
  String get e7SetupNoCamera => 'This device has no camera';

  @override
  String get e7SetupServerDisconnected => 'The server is not connected.';

  @override
  String get e7SetupEmptyPasswordHint =>
      'Leave empty only if this server no longer uses a password.';

  @override
  String get e7SetupAndroidOnly => 'Setup on this phone is Android only';

  @override
  String get e7SetupEditServer => 'Edit server';

  @override
  String get e7SetupReenterPassword => 'Re-enter password';

  @override
  String get e7SetupMissingCredential =>
      'The saved credential for this managed server is unavailable. Run setup again to replace it safely.';

  @override
  String get e7SetupReconnect => 'Reconnect';

  @override
  String get e7SetupSwitchNotStarted => 'The runtime switch did not start.';

  @override
  String get e7SetupNoUbuntu => 'No managed Ubuntu installation found.';

  @override
  String get e7SetupKeyUnavailable =>
      'Unavailable while the terminal is disconnected';

  @override
  String get e7SetupCopyTerminal => 'Copy terminal selection or transcript';

  @override
  String get e7SetupCommandHint => 'Type a command';

  @override
  String get e7SetupCheckInstallFailed =>
      'Could not check the installed environment.';

  @override
  String get e7SetupConnectionFailed => 'Connection failed.';

  @override
  String get e7SetupOpenAppSettings => 'Open app settings';

  @override
  String get e7SetupFullWalkthrough => 'Full walkthrough (opens in browser)';

  @override
  String get e7SetupPairingPhoneHint =>
      'A server bound to its own 127.0.0.1 is not reachable from this phone until you bridge it — “adb reverse tcp:PORT tcp:PORT” over USB, or an SSH forward. To reach it over the network instead, put it behind HTTPS.';

  @override
  String get e7SetupOutputCopied => 'Setup output copied.';

  @override
  String get e7SetupMissingPasswordLong =>
      'The saved password is unavailable. Enter it again, or leave it empty only if this server no longer requires a password.';

  @override
  String get e7SetupNoServerGuide =>
      'No server there yet? The setup guide shows how to start one.';

  @override
  String get e7SetupStartingLocal => 'Starting local server';

  @override
  String get e7SetupTerminalSemantics =>
      'Interactive terminal. Use the accessibility button for a readable transcript and labeled input.';

  @override
  String get e7SetupRestartingLocalStage => 'Restarting local server';

  @override
  String get e7SetupEmptyPairClipboard =>
      'The clipboard is empty. Run “opencode2 pair” on the server and copy the code it prints.';

  @override
  String get e7SetupRestartActiveChanged =>
      'The local server restarted, but the active server changed. Reconnect when you are ready.';

  @override
  String get e7SetupTokenRequired => 'Can\'t read the saved token';

  @override
  String get e7SetupPairingInstructions =>
      'On your computer run “opencode2 pair”, then paste or scan the code it prints.';

  @override
  String get e7SetupHostDaily => 'Day-to-day — run on your computer';

  @override
  String get e7SetupStopLocal => 'Stop local server';

  @override
  String get e7SetupReadingProgress => 'Reading setup progress';

  @override
  String get e7SetupCameraSettingsDetail =>
      'Android will not ask again, so this has to be changed in app settings: turn on Camera, then come back. Pasting the code needs no permission at all and works right now.';

  @override
  String get e7SetupVerifyTermuxFailed => 'Termux bridge verification failed.';

  @override
  String get e7SetupUbuntuOnly =>
      'Ubuntu is installed. OpenCode is not installed yet.';

  @override
  String get e7SetupRenameTerminal => 'Rename terminal';

  @override
  String get e7SetupInputUnavailable => 'Terminal input unavailable';

  @override
  String get e7SetupUpdateInterruption =>
      'The server will be briefly unavailable. Active generation should be stopped first.';

  @override
  String get e7SetupRunningOnPhone => 'OpenCode is running on this phone.';

  @override
  String get e7SetupLocalStopped =>
      'The local server is stopped. Its installed files are kept.';

  @override
  String get e7SetupDidNotConnect => 'The server did not connect.';

  @override
  String get e7SetupSendCommand => 'Send command to terminal';

  @override
  String get e7SetupUnsupportedSetup =>
      'Setting up on the device itself works only on Android phones. On this computer, start OpenCode yourself and add it as a server.';

  @override
  String get e7SetupSendKey => 'Sends this key to the terminal';

  @override
  String get e7SetupRemoveTerminal => 'Remove terminal?';

  @override
  String e7SetupTerminalNumber(int number) {
    return 'Terminal $number';
  }

  @override
  String e7SetupCopyCommandLabel(String label) {
    return 'Copy command: $label';
  }

  @override
  String e7SetupConnectFailedDetail(String name, String detail) {
    return 'Could not connect to $name. $detail Check the server address and credentials, then try again.';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return '$name was saved, but it could not connect. Check the server address and credentials, then try again. ($detail)';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return 'Could not save $name. The existing server was left unchanged. Check device storage and try again. ($detail)';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return 'Remove $name?';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return '$name was removed, but its connection could not be closed cleanly. Restart the app before connecting elsewhere. ($detail)';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return 'Could not remove $name. The saved server and current connection were kept. Check device storage and try again. ($detail)';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return 'Paired with $host — chosen from $count addresses in the code.';
  }

  @override
  String e7SetupPaired(String host) {
    return 'Paired with $host.';
  }

  @override
  String e7SetupStartFailed(String detail) {
    return 'Could not save or start the local setup: $detail';
  }

  @override
  String e7SetupRestartFailed(String detail) {
    return 'Could not restart the local server: $detail';
  }

  @override
  String e7SetupStopFailed(String detail) {
    return 'Could not stop the local server: $detail';
  }

  @override
  String e7SetupElapsedSeconds(int seconds) {
    return '${seconds}s elapsed';
  }

  @override
  String e7SetupElapsedMinutes(int minutes, int seconds) {
    return '${minutes}m ${seconds}s elapsed';
  }

  @override
  String e7SetupDeleteDisclosure(int queued, int drafts) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued queued prompts will be deleted.',
      one: '1 queued prompt will be deleted.',
      zero: '',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts unsent drafts will be deleted.',
      one: '1 unsent draft will be deleted.',
      zero: '',
    );
    return 'This deletes everything this device stored for the server: its password, selected model and agent, project choice, and any conversations shown in the home-screen widget.\n\n$_temp0 $_temp1\n\nNothing is deleted on the server itself or at your AI providers.';
  }

  @override
  String e7SetupPairingFailed(String detail, String hint) {
    return 'No address in that pairing code answered:\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String e7SetupProbeV1(String version) {
    return 'OpenCode 1 · $version — limited feature set';
  }

  @override
  String get e7SetupPairNone =>
      'There is no pairing code here. Run “opencode2 pair” on the server and scan or copy what it prints.';

  @override
  String get e7SetupPairLong =>
      'That is far too long to be a pairing code. Copy only the line “opencode2 pair” prints, or scan its QR code.';

  @override
  String get e7SetupPairInvalid =>
      'That is not a pairing code. Run “opencode2 pair” on the server and scan or copy what it prints.';

  @override
  String get e7SetupPairShape =>
      'That pairing code is the wrong shape — it should be a JSON object with “urls”, “username”, and “password”.';

  @override
  String get e7SetupPairNoUrls =>
      'That pairing code has no “urls” field, so there is no address to connect to.';

  @override
  String get e7SetupPairUrlsType =>
      'That pairing code\'s “urls” field is not a list of addresses.';

  @override
  String get e7SetupPairTooMany =>
      'That pairing code lists more addresses than this app will try. Bind the server to one interface and pair again.';

  @override
  String get e7SetupPairAddressType =>
      'That pairing code lists an address that is not text.';

  @override
  String get e7SetupPairAddressLong =>
      'That pairing code lists an address far too long to be a server URL.';

  @override
  String get e7SetupPairAddressMissing =>
      'That pairing code carries no server address. Check that the server is actually listening, then run “opencode2 pair” again.';

  @override
  String get e7SetupPairUsernameType =>
      'That pairing code\'s “username” field is not text.';

  @override
  String get e7SetupPairPasswordMissing =>
      'That pairing code has no “password” field. It may have been truncated — scan or copy the whole code.';

  @override
  String get e7SetupPairPasswordType =>
      'That pairing code\'s “password” field is not text.';

  @override
  String get e7SetupPairTestFailed =>
      'The connection test failed before the server could be checked. Try another address.';

  @override
  String get e7SetupNotOpenCode =>
      'The address did not answer as an OpenCode server. Check the address and try again.';

  @override
  String get e7SetupNoServerAnswer =>
      'The server did not answer. Check that it is running and that the address is right.';

  @override
  String get e7SetupPairPasswordRejected =>
      'Password rejected. Check the pairing code and try again.';

  @override
  String get e7SetupPairAddressUnusable =>
      'That pairing code contains an unusable server address.';

  @override
  String get e7SetupInvalidAddress => '<invalid address>';

  @override
  String get e7SetupEnterUrl => 'Enter a server URL.';

  @override
  String get e7SetupIncludeScheme =>
      'Include https://. Plain http:// works only on this device or a private network address.';

  @override
  String get e7SetupCompleteUrl =>
      'Enter a complete server URL, such as https://server.example:4096.';

  @override
  String get e7SetupUrlScheme =>
      'Server URLs must use https://, or http:// for a local server.';

  @override
  String get e7SetupTermuxUrlScheme =>
      'Server URLs must use https://, or http:// for local Termux.';

  @override
  String get e7SetupUrlCredentials =>
      'Do not put credentials in the URL. Use the fields below.';

  @override
  String get e7SetupUrlQuery =>
      'Remove query parameters and fragments from the server URL.';

  @override
  String get e7SetupUrlPath =>
      'Remove the path from the server URL. Enter only its origin.';

  @override
  String get e7SetupRequireHttps =>
      'A password is only sent to another computer over https://. Use the computer\'s https:// address, pair with a code, or connect with Tailscale.';

  @override
  String get e7SetupLocalHttp =>
      'An http:// address works only for this phone or a private network address such as 192.168.x.x. For anything else, pair with a code, use its https:// address, or connect with Tailscale.';

  @override
  String get e7SetupRefused =>
      'The computer refused the connection. Check that the server is running there and that the address and port are right.';

  @override
  String get e7SetupTimeout =>
      'The connection timed out. Check the address, and that the server is reachable from this phone.';

  @override
  String get e7SetupDns =>
      'That host name could not be found. Check the address spelling.';

  @override
  String get e7SetupCertificate =>
      'The server’s TLS certificate was rejected. Use a certificate this phone trusts.';

  @override
  String get e7SetupUnhealthy =>
      'The server responded but reported itself unhealthy. Check its logs, then try again.';

  @override
  String get e7SetupServerStarting =>
      'The server is starting. Try again in a moment.';

  @override
  String get e7SetupPasswordNeeded =>
      'This server requires its serve password.';

  @override
  String get e7SetupPasswordRejected =>
      'Password rejected. Copy the current \"server password\" line from the server output — it changes on every restart unless OPENCODE_PASSWORD is set.';

  @override
  String get e7SetupCredentialsRefused =>
      'The server refused the credentials. Check the username and password.';

  @override
  String get e7SetupCodexUrl => 'Enter a Codex server URL.';

  @override
  String get e7SetupCodexCompleteUrl => 'Enter a complete Codex server URL.';

  @override
  String get e7SetupCodexScheme =>
      'Codex server URLs must use wss://, or ws:// for a local server.';

  @override
  String get e7SetupCodexCredentials =>
      'Do not put credentials in the Codex URL.';

  @override
  String get e7SetupCodexQuery =>
      'Remove query parameters and fragments from the Codex URL.';

  @override
  String get e7SetupCodexPath => 'Remove the path from the Codex server URL.';

  @override
  String get e7SetupCodexPlain =>
      'ws:// works only for a server on this phone. Use a wss:// address for another computer.';

  @override
  String get e7SetupCodexDirectory =>
      'Enter an absolute Codex project directory.';

  @override
  String get e7SetupCodexToken => 'Enter a valid Codex connection token.';

  @override
  String get e7SetupRefreshPackages => 'Refreshing Termux packages';

  @override
  String get e7SetupRepairPackages => 'Repairing the Termux package set';

  @override
  String get e7SetupInstallDependencies => 'Installing Termux dependencies';

  @override
  String get e7SetupPrepareTermux => 'Preparing Termux';

  @override
  String get e7SetupInstallUbuntu => 'Installing Ubuntu environment';

  @override
  String get e7SetupRefreshModels => 'Refreshing the OpenCode model catalog';

  @override
  String get e7SetupStartLocalServer => 'Starting the local server';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode is ready';

  @override
  String get e7SetupPrepareRuntime => 'Preparing the selected OpenCode runtime';

  @override
  String get e7SetupSwitchLocal => 'Switching the managed local server';

  @override
  String get e7SetupCheckRestart => 'Checking the local server before restart';

  @override
  String get e7SetupStoppingLocal => 'Stopping the local server';

  @override
  String get e7SetupStoppedLocal => 'Local server stopped';

  @override
  String get e7SetupUnknownSetup => 'Unknown setup state';

  @override
  String get e7SetupUnexpectedStop =>
      'The local OpenCode server stopped unexpectedly';

  @override
  String get e7SetupSetupInterrupted =>
      'Setup stopped unexpectedly; see live output for details';

  @override
  String get e7SetupRecoveryDisabled => 'Automatic recovery disabled';

  @override
  String get e7SetupRecoveryWasDisabled => 'Automatic recovery was disabled';

  @override
  String get e7SetupPortBusy =>
      'The local server port is still in use; no replacement was started';

  @override
  String get e7SetupNoReturnData =>
      'This OpenCode 2 installation has no separate OpenCode 1 data to return to';

  @override
  String get e7SetupCredentialMismatch =>
      'The saved server credential differs from this runtime; restore its original saved credential before returning';

  @override
  String get e7SetupUbuntuUnavailable =>
      'The managed Ubuntu environment is unavailable';

  @override
  String get e7SetupRuntimeUnavailable =>
      'The selected OpenCode command is unavailable';

  @override
  String get e7SetupIdentityMismatch =>
      'The tracked process is not the managed OpenCode server';

  @override
  String get e7SetupPasswordMissing => 'The local server password is missing';

  @override
  String get e7SetupVersionMissing =>
      'OpenCode installed but did not report a version';

  @override
  String get e7SetupModelsRefreshFailed =>
      'OpenCode updated, but its model catalog could not be refreshed';

  @override
  String get e7SetupStartupExited => 'OpenCode server exited during startup';

  @override
  String get e7SetupReadinessTimeout =>
      'OpenCode server did not become authenticated and ready within 30 seconds';

  @override
  String get e7SetupUnreadableData =>
      'The OpenCode 2 data location record is unreadable';

  @override
  String get e7SetupUnreadablePrevious =>
      'The previous runtime record is unreadable';

  @override
  String get e7SetupReadManagerFailed => 'Could not read setup manager status';

  @override
  String get e7SetupMissingManager => 'Setup manager is missing';

  @override
  String get e7SetupRemoveInterruptedFailed =>
      'Could not remove the interrupted app-owned Ubuntu install';

  @override
  String get e7SetupCheckStorageFailed =>
      'Could not check available storage before setup';

  @override
  String get e7SetupReadStorageFailed =>
      'Could not read available storage before setup';

  @override
  String get e7SetupRepositoryFailed =>
      'Could not select the official Termux package repository';

  @override
  String get e7SetupRepositoryRefreshFailed =>
      'Could not refresh packages.termux.dev; check the network and retry';

  @override
  String get e7SetupRepairFailed =>
      'Could not repair the interrupted Termux package transaction';

  @override
  String get e7SetupUpgradeFailed =>
      'Could not complete the safe Termux package upgrade';

  @override
  String get e7SetupDependenciesFailed =>
      'Could not install the Termux dependencies';

  @override
  String get e7SetupDependenciesUnusable =>
      'Termux dependencies are still unusable after the package repair';

  @override
  String get e7SetupUbuntuUnusable =>
      'An existing Ubuntu container is not usable; setup will not delete it';

  @override
  String get e7SetupExtractionFailed =>
      'Ubuntu Base extraction did not create a usable container';

  @override
  String get e7SetupLockFailed =>
      'Setup manager could not claim its launch lock';

  @override
  String get e7SetupSetupGroupFailed =>
      'Setup manager did not start in an isolated process group';

  @override
  String get e7SetupSwitchGroupFailed =>
      'Switch manager did not start in an isolated process group';

  @override
  String get e7SetupServerGroupFailed =>
      'Managed server did not start in an isolated process group';

  @override
  String get e7SetupRecordIdentityFailed =>
      'Could not record the managed server process identity';

  @override
  String e7SetupConnectingProfile(String name) {
    return 'Connecting to $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return 'Connecting again (attempt $attempt)';
  }

  @override
  String get e7SetupOpeningWorkspace => 'Opening your saved project.';

  @override
  String get e7SetupWhatToCheck => 'What to check';

  @override
  String get e7SetupUpdatePassword => 'Update password';

  @override
  String e7SetupLastSetupDetail(String detail) {
    return 'Last setup output: $detail';
  }

  @override
  String e7SetupBridgeDetail(String detail) {
    return 'Bridge detail: $detail';
  }

  @override
  String e7SetupDiagnosticsUnavailable(String detail) {
    return 'Diagnostics unavailable: $detail';
  }

  @override
  String e7SetupProbeHttp(String status) {
    return 'The address responded, but not like an OpenCode server (HTTP $status). Check that the URL points at opencode serve.';
  }

  @override
  String e7SetupProbeError(String detail) {
    return 'Connection test failed: $detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'OpenCode server exited (code $code)';
  }

  @override
  String get e7SetupCheckTermux => 'On this phone';

  @override
  String get e7SetupCommandFailed => 'Termux command failed.';

  @override
  String get e7SetupUnexpectedBridge =>
      'Termux returned an unexpected bridge response.';

  @override
  String get e7SetupSetupQueued => 'Setup queued';

  @override
  String get e7SetupNoSetup => 'No setup has been started';

  @override
  String get e7SetupManagerMissingAfterLaunch =>
      'Setup manager is missing after launch';

  @override
  String get e7SetupBootstrapCleared => 'Bootstrap state cleared';

  @override
  String get e7SetupInstallingBeta => 'Installing OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => 'Authentication failed';

  @override
  String get e7SetupUnknownVersion => 'unknown version';

  @override
  String get e7ModelUiClose => 'Close model selector';

  @override
  String get e7ModelUiClearSearch => 'Clear model search';

  @override
  String get e7ModelUiLoadFailed => 'Could not load models';

  @override
  String get e7ModelUiRetry => 'Try again';

  @override
  String get e7ModelUiBasicCatalog =>
      'This server returned a basic catalog. Capability and context details are unavailable.';

  @override
  String get e7ModelUiEditFilters => 'Edit model filters';

  @override
  String get e7ModelUiFilterModels => 'Filter models';

  @override
  String get e7ModelUiFiltered => 'Filtered';

  @override
  String get e7ModelUiFilters => 'Filters';

  @override
  String get e7ModelUiRefresh => 'Refresh models';

  @override
  String get e7ModelUiAnyCapability => 'Any capability';

  @override
  String get e7ModelUiFastModes => 'Fast modes';

  @override
  String get e7ModelUiReasoning => 'Reasoning';

  @override
  String get e7ModelUiLargestContext => 'Largest context';

  @override
  String get e7ModelUiNoneAvailable => 'No models available';

  @override
  String get e7ModelUiFavoritesEmpty => 'Keep your go-to models here';

  @override
  String get e7ModelUiRecentEmpty => 'Your next choice starts here';

  @override
  String get e7ModelUiNoMatches => 'No matching models';

  @override
  String get e7ModelUiConfigureProvider =>
      'Configure a provider on the OpenCode server, then refresh.';

  @override
  String get e7ModelUiFavoritesHint =>
      'Tap the star beside any model to find it here.';

  @override
  String get e7ModelUiRecentHint =>
      'Models you use will appear here, most recent first.';

  @override
  String get e7ModelUiNoFastModes =>
      'No model reports an explicit fast or low-effort mode.';

  @override
  String get e7ModelUiNoMatchesHint =>
      'Try another search, provider, or capability filter.';

  @override
  String get e7ModelUiClearFilters => 'Clear filters';

  @override
  String get e7ModelUiBrowseAll => 'Browse all models';

  @override
  String get e7ModelUiAgent => 'Agent';

  @override
  String get e7ModelUiServerDefault => 'Server default';

  @override
  String get e7ModelUiProvider => 'Provider';

  @override
  String get e7ModelUiAllProviders => 'All providers';

  @override
  String get e7ModelUiCurrent => 'Current model';

  @override
  String get e7ModelUiUnavailable => 'Unavailable';

  @override
  String get e7ModelUiDeprecated => 'Deprecated';

  @override
  String get e7ModelUiPreview => 'Preview';

  @override
  String get e7ModelUiFavoritesFailed => 'Could not save favorites. Try again.';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return 'Use $model · $agent';
  }

  @override
  String get e7ModelUiUseSession => 'Use for this conversation';

  @override
  String get e7ModelUiUseNewSessions => 'Use for new conversations';

  @override
  String get e7ModelUiTools => 'Tools';

  @override
  String get e7ModelUiAttachments => 'Attachments';

  @override
  String get e7ModelUiDefault => 'Default';

  @override
  String get e7ModelUiSelectionGone =>
      'This choice is no longer available. Refresh models and try again.';

  @override
  String get e7VoiceUiLocalInput => 'Local voice input';

  @override
  String get e7VoiceUiChooseModel => 'Choose a multilingual Whisper INT8 model';

  @override
  String get e7VoiceUiPrivacyDownload => 'Audio never leaves this phone.';

  @override
  String get e7VoiceUiNoBuiltInMic =>
      'Android reports no built-in microphone. Voice input may still work with a wired or USB microphone.';

  @override
  String get e7VoiceUiLanguage => 'Transcription language';

  @override
  String get e7VoiceUiVerifying => 'Verifying downloaded model';

  @override
  String get e7VoiceUiCancelDownload => 'Cancel download';

  @override
  String get e7VoiceUiNotNow => 'Not now';

  @override
  String get e7VoiceUiUseModel => 'Use model';

  @override
  String get e7VoiceUiDownload => 'Download';

  @override
  String get e7VoiceUiKeep => 'Keep';

  @override
  String get e7VoiceUiDelete => 'Delete';

  @override
  String get e7VoiceUiDefaultBadge => 'default';

  @override
  String get e7VoiceUiOptionalBadge => 'optional';

  @override
  String get e7VoiceUiInstalledBadge => 'installed';

  @override
  String get e7VoiceUiNotInstalledBadge => 'not installed';

  @override
  String get e7VoiceUiSetupBusy =>
      'Unavailable while model setup is in progress';

  @override
  String get e7VoiceUiSelected => 'Selected';

  @override
  String get e7VoiceUiSelectHint => 'Double tap to select';

  @override
  String get e7VoiceUiDefault => 'Default';

  @override
  String get e7VoiceUiOptional => 'Optional';

  @override
  String get e7VoiceUiInstalled => 'Installed';

  @override
  String get e7VoiceUiRedownload => 'Re-download';

  @override
  String get e7VoiceUiOpenSettings => 'Open app settings';

  @override
  String get e7VoiceUiRetry => 'Try again';

  @override
  String get e7VoiceUiStartListening => 'Start listening';

  @override
  String get e7VoiceUiDraftReady => 'Transcript ready to review';

  @override
  String get e7VoiceUiNeedsAttention => 'Voice input needs attention';

  @override
  String get e7VoiceUiModelRequired => 'A local model is required';

  @override
  String e7ModelUiCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count models',
      one: '1 model',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiContext(String count) {
    return '$count context';
  }

  @override
  String e7ModelUiOutput(String count) {
    return '$count output';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return 'Favorite $model';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return 'Remove $model from favorites';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · $effort effort';
  }

  @override
  String get e7ModelUiLoading => 'Loading model catalog';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input in · $output out /1M';
  }

  @override
  String e7VoiceUiDownloadPercent(int percent) {
    return 'Downloading voice model $percent percent';
  }

  @override
  String e7VoiceUiDownloadProgress(String received, String total) {
    return '$received of $total';
  }

  @override
  String e7VoiceUiSetupFailed(String error) {
    return 'Model setup failed: $error';
  }

  @override
  String e7VoiceUiDeletePack(String model) {
    return 'Delete $model speech model?';
  }

  @override
  String e7VoiceUiDeleteDetail(String size) {
    return 'This removes $size from app-private storage. You can download it again later.';
  }

  @override
  String e7VoiceUiDownloadSize(String size) {
    return '$size download';
  }

  @override
  String e7VoiceUiPackSemantics(
    String model,
    String size,
    String badges,
    String description,
  ) {
    return '$model, $size, $badges. $description';
  }

  @override
  String get e7VoiceUiLicenses => 'Voice licenses and provenance';

  @override
  String get e7VoiceUiNoticesFailed =>
      'Could not load voice licenses. Try again.';

  @override
  String get e7VoiceUiAuto => 'Auto detect';

  @override
  String get e7VoiceUiEnglish => 'English';

  @override
  String get e7VoiceUiArabic => 'Arabic';

  @override
  String get e7VoiceUiBalanced => 'Balanced';

  @override
  String get e7VoiceUiBalancedDetail =>
      'Recommended quality, storage, and speed tradeoff.';

  @override
  String get e7VoiceUiAccurate => 'High accuracy';

  @override
  String get e7VoiceUiAccurateDetail =>
      'Optional best quality; requires substantially more memory.';

  @override
  String get e7VoiceUiCompact => 'Compact fallback';

  @override
  String get e7VoiceUiCompactDetail =>
      'Fastest and smallest; reduced accuracy in difficult audio.';

  @override
  String get e7VoiceUiUnsupportedAbi =>
      'No bundled voice runtime supports this device ABI.';

  @override
  String get e7VoiceUiPermissionBlocked =>
      'Microphone access is blocked. Allow it in Android app settings.';

  @override
  String get e7VoiceUiPermissionRequired =>
      'Microphone permission is required for local voice input.';

  @override
  String get e7VoiceUiDeviceUnavailable =>
      'Local voice input is unavailable. Stop playback, check microphone settings, and try again.';

  @override
  String get e7VoiceUiInputUnavailable =>
      'Local voice input is unavailable on this platform.';

  @override
  String get e7VoiceUiNoAudio => 'No audio was captured.';

  @override
  String get e7VoiceUiInterrupted => 'Recording was interrupted.';

  @override
  String get e7VoiceUiMicrophoneError =>
      'The microphone reported an error. Check its settings and try again.';

  @override
  String get e7VoiceUiInputFailed => 'Voice input could not finish. Try again.';

  @override
  String get e7VoiceUiTechnicalDetails => 'Technical details';

  @override
  String get e7VoiceUiHttpsRequired =>
      'Voice models may only be downloaded over HTTPS.';

  @override
  String get e7VoiceUiTransportClosed => 'Voice download transport is closed.';

  @override
  String get e7VoiceUiInvalidRedirect =>
      'Voice model download returned an invalid redirect.';

  @override
  String get e7VoiceUiUnsafeRedirect =>
      'Voice model download redirected to a non-HTTPS URL.';

  @override
  String get e7VoiceUiNoResponse => 'Model server returned no response.';

  @override
  String get e7VoiceUiDownloadTimeout =>
      'The model download timed out. Check the connection and try again.';

  @override
  String get e7VoiceUiChecksumFailed =>
      'The downloaded model failed checksum verification. Re-download it.';

  @override
  String get e7VoiceUiVerificationFailed =>
      'The model failed final verification. Re-download it.';

  @override
  String get e7VoiceUiHttpFailed =>
      'The model server rejected the download. Try again.';

  @override
  String get e7VoiceUiLengthFailed =>
      'The model download has an unexpected size. Re-download it.';

  @override
  String get e7VoiceUiIncomplete =>
      'The model download is incomplete. Try again.';

  @override
  String get e7VoiceUiDownloadFailed =>
      'The voice model could not be downloaded. Try again.';

  @override
  String e7VoiceUiMemory(String model, int required, int available) {
    return '$model needs a phone with about $required MB of memory; this one has $available MB.';
  }

  @override
  String e7VoiceUiStorage(String model, String size) {
    return '$model needs $size free, including a safety margin.';
  }

  @override
  String get e7ModelUiProviderFallback => 'a provider';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first and $last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first, and $last';
  }

  @override
  String get e7ModelUiListSeparator => ', ';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Signed in to $providers, but the server has not loaded them yet, so their models cannot answer.',
      one:
          'Signed in to $providers, but the server has not loaded it yet, so its models cannot answer.',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain =>
      'OpenCode is unreachable. Try again.';

  @override
  String get approvalsUiMenu => 'Approvals';

  @override
  String get approvalsUiTitle => 'Approvals for this conversation';

  @override
  String get approvalsUiAskDetail => 'Every permission request waits for you.';

  @override
  String get approvalsUiAutoTitle => 'Approve automatically while connected';

  @override
  String get approvalsUiAutoDetail =>
      'This phone answers each permission request with “Allow once” as it arrives. Nothing is saved as always allowed.';

  @override
  String get approvalsUiInheritTitle => 'Subagents inherit this';

  @override
  String get approvalsUiInheritDetail =>
      'Subagent conversations started by this one follow the same choice unless they have their own.';

  @override
  String get approvalsUiInheritUnavailable =>
      'Available once automatic approval is on.';

  @override
  String get approvalsUiInheritedFrom => 'Inherited from parent conversation';

  @override
  String get approvalsUiInheritedDetail =>
      'This conversation follows its parent’s approvals. Override it to choose for this conversation only.';

  @override
  String get approvalsUiOverride => 'Override for this conversation';

  @override
  String get approvalsUiFollowParent => 'Follow parent again';

  @override
  String get approvalsUiIndicatorOn => 'Approving automatically';

  @override
  String approvalsUiAutoApproved(String action) {
    return 'Auto-approved · $action';
  }

  @override
  String get approvalsUiFailedDetail =>
      'Automatic approval failed. Review this request.';

  @override
  String approvalsUiSaveFailed(String error) {
    return 'Couldn’t save the approval setting: $error';
  }

  @override
  String get approvalsUiOpenSettings => 'Open approval settings';

  @override
  String get approvalsUiIndicatorPaused => 'Auto-approval paused';

  @override
  String approvalsUiRecordTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count requests approved automatically on this server',
      one: '1 request approved automatically on this server',
      zero: 'Nothing approved automatically on this server yet',
    );
    return '$_temp0';
  }

  @override
  String get handoffUiComputerTitle => 'Continue on computer';

  @override
  String handoffUiComputerIntro(String binary) {
    return 'Run this in a terminal on the computer that runs this server. It opens the same conversation in the $binary interface. Nothing is sent until you type.';
  }

  @override
  String get handoffUiComputerDirectoryNote =>
      'Conversations belong to a project folder, so the command changes into this conversation’s folder first.';

  @override
  String handoffUiComputerVerify(String verified, String binary) {
    return 'Verified against $verified. If your installed version differs, check $binary --help for the --session flag.';
  }

  @override
  String get handoffUiUnavailableDirectory =>
      'The server did not report a project folder for this conversation, so there is no folder to open it in. Reload the conversation and try again.';

  @override
  String get handoffUiUnavailableWorkspace =>
      'This conversation runs inside a cloud environment. Its folder belongs to the environment’s host, so a plain terminal command cannot open it. Export and import the conversation instead.';

  @override
  String get handoffUiUnavailableReference =>
      'This conversation’s reference cannot be placed in a command safely.';

  @override
  String get handoffUiPhoneTitle => 'Open on another phone';

  @override
  String get handoffUiPhoneIntro =>
      'Scan with OpenCode Mobile on the other phone. The code holds only the server and conversation IDs.';

  @override
  String get handoffUiPhoneQrLabel =>
      'QR code that opens this conversation on another phone';

  @override
  String get handoffUiPhoneLinkLabel => 'Link';

  @override
  String get handoffUiPhoneCopyLink => 'Copy link';

  @override
  String get handoffUiPhoneUnavailable =>
      'A link cannot be built for this conversation. Reload the conversation and try again.';

  @override
  String get handoffUiLinkServerMissing =>
      'The conversation is on a server this phone has not saved. Add it here, then scan the code again.';

  @override
  String get handoffUiLinkDismiss => 'Dismiss';

  @override
  String get handoffUiLinkWaiting =>
      'Opening the conversation once the server connects…';

  @override
  String get handoffUiLinkReentry =>
      'Enter this server’s password again, then scan the code again.';

  @override
  String get handoffUiLinkConnectionFailed =>
      'Could not connect to the saved server. Check it under Servers, then scan the code again.';

  @override
  String get teamUiAccessControls => 'Decisions and controls';

  @override
  String get teamUiAccessReadOnly => 'Read-only';

  @override
  String get teamUiAddAddressHint => 'http://100.x.x.x:8373';

  @override
  String get teamUiAddAddressLabel => 'Address';

  @override
  String get teamUiAddManually => 'Add manually';

  @override
  String get teamUiAddSubmit => 'Test and turn on';

  @override
  String get teamUiAddTesting => 'Checking the address…';

  @override
  String get teamUiAddTitle => 'Add AI Team host';

  @override
  String get teamUiAddressRequired => 'Enter the host address.';

  @override
  String get teamUiChange => 'Change';

  @override
  String get teamUiDisclaimerComputer =>
      'Runs as fast as your computer; keep it awake';

  @override
  String get teamUiDisclaimerPhone =>
      'Android may stop it when the screen is off; slower than a computer';

  @override
  String get teamUiEditorBody =>
      'If this computer runs Gas City, the app can find it automatically.';

  @override
  String teamUiEditorConfigured(String url) {
    return 'AI Team host: $url';
  }

  @override
  String get teamUiEditorTitle => 'AI Team (optional)';

  @override
  String get teamUiHostGuideIntro =>
      'Everything stays on your Tailscale network; nothing is published to the internet.';

  @override
  String get teamUiHostGuideStep1 =>
      'Install Gas City\'s three tools, gc, bd and dolt, on your PATH; the full guide has each download with its checksum. Then check that all three are found:';

  @override
  String get teamUiHostGuideStep2 =>
      'Save the team file from the full guide in a folder next to your project. Then set up the team and add your project, folder first:';

  @override
  String get teamUiHostGuideStep3 =>
      'Start the team and check that it answers:';

  @override
  String get teamUiHostGuideStep4 =>
      'Download the front that lets this phone in over Tailscale, check it and start it, with your own Tailscale login after --allow. Then add it here: the computer\'s Tailscale address with the port in the command, and the team\'s name.';

  @override
  String get teamUiHostGuideTitle => 'Run an AI team on your computer';

  @override
  String get teamUiHostModeComputer => 'Computer';

  @override
  String get teamUiHostModePhone => 'This phone';

  @override
  String get teamUiHow => 'How';

  @override
  String get teamUiKeep => 'Keep';

  @override
  String get teamUiLabelAccess => 'Access';

  @override
  String get teamUiLabelAddress => 'Address';

  @override
  String get teamUiLabelCity => 'City';

  @override
  String get teamUiLabelHost => 'Host';

  @override
  String get teamUiLabelProvider => 'Provider';

  @override
  String get teamUiLabelVersion => 'Version';

  @override
  String get teamUiLearnHow => 'Learn how';

  @override
  String get teamUiNoServer => 'Connect to a server to use plugins.';

  @override
  String get teamUiPluginsTitle => 'Plugins';

  @override
  String get teamUiReadOnlyBody =>
      'You can watch this team from the phone. Answering and steering need the front on the computer.';

  @override
  String get teamUiReasonCityNotRunning => 'team host starting';

  @override
  String get teamUiReasonNotGasCity => 'no AI team found';

  @override
  String get teamUiReasonPlainHttp => 'address is not on Tailscale';

  @override
  String get teamUiReasonReadFailed => 'last read failed';

  @override
  String get teamUiReasonUnreachable => 'host unreachable';

  @override
  String get teamUiRefresh => 'Refresh';

  @override
  String get teamUiRowConnecting => 'On · connecting…';

  @override
  String get teamUiRowNotAvailable => 'Not available on this server';

  @override
  String teamUiRowNotAvailableReason(String reason) {
    return 'Not available on this server · $reason';
  }

  @override
  String get teamUiRowOff => 'Off';

  @override
  String teamUiRowOn(String server) {
    return 'On · $server';
  }

  @override
  String teamUiRowOnReadOnly(String server) {
    return 'On · $server · view only';
  }

  @override
  String get teamUiRowReconnecting => 'On · reconnecting…';

  @override
  String get teamUiRowTitle => 'AI Team · Gas City';

  @override
  String teamUiRowUnreachable(String minutes) {
    return 'On · host unreachable since $minutes min';
  }

  @override
  String get teamUiTailnetRequired =>
      'AI Team works over your Tailscale network or on this device. Use the computer\'s Tailscale address (100.x.x.x or name.ts.net).';

  @override
  String get teamUiTechnicalDetails => 'Technical details';

  @override
  String get teamUiTechnicalLastAnswer => 'Last answer from the host';

  @override
  String get teamUiTermAgent => 'Agent · polecat';

  @override
  String get teamUiTermProject => 'Project · rig';

  @override
  String get teamUiTermRun => 'Run · convoy';

  @override
  String get teamUiTermTeam => 'Team · city';

  @override
  String get teamUiTermWork => 'Work · bead';

  @override
  String get teamUiTermsHeading => 'Terms';

  @override
  String get teamUiTurnOffBody =>
      'Removes its card, attention items and cached team data from this phone. Nothing changes on the host.';

  @override
  String get teamUiTurnOffConfirm => 'Turn off AI Team';

  @override
  String teamUiTurnOffTitle(String server) {
    return 'Turn off AI Team for $server?';
  }

  @override
  String get teamUiVerdictCityNotRunning =>
      'The team host is starting. Try again in a moment.';

  @override
  String get teamUiVerdictNotGasCity =>
      'This server doesn\'t run an AI team yet. Set one up on the computer — it takes a few minutes.';

  @override
  String get teamUiVerdictUnreachable =>
      'No answer from this address. Check it, and that the computer is awake and on your Tailscale network.';

  @override
  String get teamUiVersionUnknown => 'unknown';

  @override
  String teamUiCardAgentsSummary(
    int total,
    int working,
    int waiting,
    int idle,
    int stopped,
  ) {
    return '$total agents: $working working, $waiting waiting, $idle idle, $stopped stopped';
  }

  @override
  String get teamUiCardEmptyHint => 'Start tasks on the computer for now.';

  @override
  String get teamUiCardEmptyTitle => 'No recent tasks';

  @override
  String get teamUiCardErrorCityNotRunning =>
      'The team host is starting. Try again in a moment.';

  @override
  String get teamUiCardErrorNotGasCity =>
      'This server doesn’t run an AI team yet. Set one up on the computer — it takes a few minutes.';

  @override
  String get teamUiCardErrorPlainHttp =>
      'AI Team works over your Tailscale network or on this device. Use tailscale serve on the computer, then try again.';

  @override
  String get teamUiCardErrorUnreachable =>
      'The team host can’t be reached. AI Team works over your Tailscale network or on this device.';

  @override
  String get teamUiStateUnreachableTitle => 'Can’t reach the team host';

  @override
  String get teamUiStateNotGasCityTitle => 'No AI team on this server';

  @override
  String get teamUiStateStartingTitle => 'The team host is starting';

  @override
  String get teamUiStatePlainHttpTitle => 'AI Team can’t use this address';

  @override
  String get teamUiStateNotAnsweringTitle => 'The team isn’t answering';

  @override
  String get teamUiCardLoading => 'Connecting to the team host…';

  @override
  String teamUiCardRefreshFailed(String time) {
    return 'Last refresh failed · showing data from $time';
  }

  @override
  String get teamUiCardRetry => 'Try again';

  @override
  String get teamUiCardRunStateBlocked => 'Blocked';

  @override
  String get teamUiCardRunStateCancelled => 'Cancelled';

  @override
  String get teamUiCardRunStateCompleted => 'Done';

  @override
  String get teamUiCardRunStateFailed => 'Failed';

  @override
  String get teamUiCardRunStatePlanning => 'Planning';

  @override
  String get teamUiCardRunStateUnknown => 'Unknown';

  @override
  String get teamUiCardRunStateWaiting => 'Waiting for a worker';

  @override
  String get teamUiCardRunStateWorking => 'Working';

  @override
  String get teamUiCardRunStateWaitingMerge => 'Reviewing';

  @override
  String get teamUiCardRunStateMerged => 'Done · merged';

  @override
  String teamUiCardStale(String time) {
    return 'Showing data from $time · host unreachable';
  }

  @override
  String get teamUiHomeAgentNoWork => 'No current work';

  @override
  String get teamUiHomeAgentStateBlocked => 'Blocked';

  @override
  String get teamUiHomeAgentStateCrashed => 'Crashed';

  @override
  String get teamUiHomeAgentStateIdle => 'Idle';

  @override
  String get teamUiHomeAgentStateStopped => 'Stopped';

  @override
  String get teamUiHomeAgentStateUnknown => 'Unknown';

  @override
  String get teamUiHomeAgentStateWaiting => 'Waiting for you';

  @override
  String get teamUiHomeAgentStateWorking => 'Working';

  @override
  String get teamUiHomeFilterActive => 'Active';

  @override
  String get teamUiHomeFilterAll => 'All';

  @override
  String get teamUiHomeFilterBlocked => 'Blocked';

  @override
  String get teamUiHomeFilterCompleted => 'Done';

  @override
  String get teamUiHomeGateAnswerOnComputer =>
      'Answer this on the computer. The phone can only watch for now.';

  @override
  String get teamUiHomeGateAnswerOnPhone =>
      'Answer this in the host on this phone. The app can only watch for now.';

  @override
  String get teamUiHomeGateKindChoice => 'Decision';

  @override
  String get teamUiHomeGateKindConfirmation => 'Approval';

  @override
  String get teamUiHomeGateKindFreeText => 'Question';

  @override
  String get teamUiHomeGateKindGateBead => 'Gate';

  @override
  String get teamUiHomeGateKindReviewReady => 'Review ready';

  @override
  String get teamUiHomeGateKindRunFailed => 'Run failed';

  @override
  String get teamUiHomeGateKindUnknown => 'Needs you';

  @override
  String teamUiHomeGateLinkAgent(String name) {
    return 'Agent $name';
  }

  @override
  String teamUiHomeGateLinkRun(String title) {
    return 'Task $title';
  }

  @override
  String teamUiHomeGateLinkWork(String title) {
    return 'Work $title';
  }

  @override
  String get teamUiHomeGateOptions => 'Options';

  @override
  String get teamUiHomeHostRawHeading => 'Raw values';

  @override
  String get teamUiHomeRunNeedsYou => 'Needs you';

  @override
  String teamUiHomeRunProgress(int done, int total) {
    return '$done of $total done';
  }

  @override
  String get teamUiHomeRunsEmptyFiltered => 'No tasks match.';

  @override
  String get teamUiHomeRunsEmptyHint =>
      'Try another filter or clear the search.';

  @override
  String get teamUiHomeSearchHint => 'Search tasks';

  @override
  String get teamUiHomeTitle => 'AI Team';

  @override
  String get teamUiRunBack => 'Back';

  @override
  String teamUiRunBlockedByDeps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'waiting on $count other steps',
      one: 'waiting on one other step',
    );
    return '$_temp0';
  }

  @override
  String teamUiRunElapsedDays(int count) {
    return '$count d';
  }

  @override
  String teamUiRunElapsedHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String teamUiRunElapsedMinutes(int count) {
    return '$count min';
  }

  @override
  String teamUiRunSinceHandoff(String elapsed) {
    return '$elapsed since hand-off';
  }

  @override
  String get teamUiRunLabelFormula => 'Formula';

  @override
  String get teamUiRunLabelId => 'Run id';

  @override
  String get teamUiRunLabelKind => 'Kind';

  @override
  String get teamUiRunLabelLastError => 'Last error';

  @override
  String get teamUiRunLabelProject => 'Project';

  @override
  String get teamUiRunLabelRawState => 'Provider status';

  @override
  String get teamUiRunLabelStarted => 'Started';

  @override
  String get teamUiRunLabelTrackedWork => 'Tracked work';

  @override
  String get teamUiRunLabelUpdated => 'Updated';

  @override
  String get teamUiRunMissingHint =>
      'It may have been closed or removed. Refresh to check again.';

  @override
  String get teamUiRunMissingTitle => 'This task is no longer on the host';

  @override
  String get teamUiRunTermBatch => 'Task · convoy';

  @override
  String get teamUiRunTermFormula => 'Task · formula';

  @override
  String get teamUiRunTermUnknown => 'Task';

  @override
  String teamUiRunTimelineAgentStopped(String name) {
    return '$name stopped';
  }

  @override
  String teamUiRunTimelineAgentWoke(String name) {
    return '$name started';
  }

  @override
  String teamUiRunTimelineGateOpened(String title) {
    return 'Needs you: $title';
  }

  @override
  String teamUiRunTimelineGateResolved(String title) {
    return 'Answered: $title';
  }

  @override
  String teamUiRunTimelineRunChanged(String state) {
    return 'Run is now $state';
  }

  @override
  String teamUiRunTimelineWorkClosed(String title) {
    return '$title closed';
  }

  @override
  String teamUiRunTimelineWorkCreated(String title) {
    return '$title added';
  }

  @override
  String teamUiRunTimelineWorkUpdated(String title) {
    return '$title updated';
  }

  @override
  String teamUiAgentContextSemantics(int percent) {
    return 'Context $percent% used';
  }

  @override
  String teamUiAgentContextShort(int percent) {
    return 'ctx $percent%';
  }

  @override
  String get teamUiAgentLabelBranch => 'Branch';

  @override
  String get teamUiAgentLabelHarness => 'Harness';

  @override
  String get teamUiAgentLabelModel => 'Model';

  @override
  String get teamUiAgentLabelPack => 'Pack';

  @override
  String get teamUiAgentLabelPool => 'Pool';

  @override
  String get teamUiAgentLabelSessionAge => 'Session age';

  @override
  String get teamUiAgentLabelSessionId => 'Session';

  @override
  String get teamUiAgentLabelSessionName => 'Session name';

  @override
  String get teamUiAgentLabelWorkDir => 'Working directory';

  @override
  String get teamUiAgentMissingHint =>
      'It may have been recycled. Refresh to check.';

  @override
  String get teamUiAgentMissingTitle => 'This agent is no longer on the host';

  @override
  String get teamUiAgentNeedsYou => 'Needs you';

  @override
  String get teamUiAgentOutputConnecting => 'Connecting to the session…';

  @override
  String get teamUiAgentOutputCopy => 'Copy output';

  @override
  String get teamUiAgentOutputEnded =>
      'Session ended · output no longer on the host';

  @override
  String get teamUiAgentOutputJump => 'Jump to latest';

  @override
  String get teamUiAgentOutputLive => 'Live';

  @override
  String get teamUiAgentOutputTitle => 'Live output';

  @override
  String get teamUiAgentOutputUnavailable =>
      'Live output is not available for this agent';

  @override
  String get teamUiAgentRecyclingSoon => 'Recycling soon · context nearly full';

  @override
  String teamUiAgentSessionAge(String age) {
    return 'Session $age';
  }

  @override
  String teamUiAgentTermSession(String id) {
    return 'Agent · session $id';
  }

  @override
  String get teamUiAgentValueUnknown => 'Not reported';

  @override
  String get teamUiWorkLabelAssignee => 'Assignee';

  @override
  String get teamUiWorkLabelClosedReason => 'Close reason';

  @override
  String get teamUiWorkLabelDependsOn => 'Depends on (ids)';

  @override
  String get teamUiWorkLabelId => 'Work id';

  @override
  String get teamUiWorkLabelLabels => 'Labels';

  @override
  String get teamUiWorkLabelParent => 'Parent';

  @override
  String get teamUiWorkLabelProject => 'Project';

  @override
  String get teamUiWorkLabelRawState => 'Provider status';

  @override
  String get teamUiWorkLabelRun => 'Run id';

  @override
  String get teamUiWorkLabelSession => 'Session id';

  @override
  String get teamUiWorkLabelSessionName => 'Session name';

  @override
  String get teamUiWorkLabelType => 'Type';

  @override
  String get teamUiWorkOwnerNone => 'Unassigned';

  @override
  String get teamUiWorkSheetBlocking => 'Blocks';

  @override
  String get teamUiWorkSheetBranch => 'Branch';

  @override
  String get teamUiWorkSheetClosed => 'Closed';

  @override
  String get teamUiWorkSheetCreated => 'Created';

  @override
  String get teamUiWorkSheetDependencies => 'Depends on';

  @override
  String get teamUiWorkSheetDescription => 'Description';

  @override
  String get teamUiWorkSheetMissing =>
      'This work item is no longer on the host.';

  @override
  String get teamUiWorkSheetNoTimestamps => 'The host sent no timestamps.';

  @override
  String get teamUiWorkSheetOpenSession => 'Open session';

  @override
  String get teamUiWorkSheetOutput => 'Output';

  @override
  String teamUiWorkSheetStamp(String date, String clock, String age) {
    return '$date · $clock ($age)';
  }

  @override
  String get teamUiWorkSheetTarget => 'Merge target';

  @override
  String get teamUiWorkSheetTimestamps => 'Timestamps';

  @override
  String get teamUiWorkSheetUpdated => 'Updated';

  @override
  String get teamUiWorkSheetValidation => 'Validation';

  @override
  String get teamUiWorkSheetValidationFailed => 'Failed';

  @override
  String get teamUiWorkSheetValidationPassed => 'Passed';

  @override
  String get teamUiWorkSheetValidationUnknown => 'Result recorded';

  @override
  String get teamUiWorkSheetWorktree => 'Worktree';

  @override
  String get teamUiWorkStateBlocked => 'Blocked';

  @override
  String get teamUiWorkStateCancelled => 'Cancelled';

  @override
  String get teamUiWorkStateCompleted => 'Done';

  @override
  String get teamUiWorkStateFailed => 'Failed';

  @override
  String get teamUiWorkStateNeedsInput => 'Needs input';

  @override
  String get teamUiWorkStateQueued => 'Queued';

  @override
  String get teamUiWorkStateReady => 'Ready';

  @override
  String get teamUiWorkStateReview => 'Review';

  @override
  String get teamUiWorkStateUnknown => 'Unknown';

  @override
  String get teamUiWorkStateWaiting => 'Waiting';

  @override
  String get teamUiWorkStateWorking => 'Working';

  @override
  String teamUiUsageCostEstimated(String cost) {
    return '$cost est.';
  }

  @override
  String teamUiUsageTokens(String count) {
    return '$count tokens';
  }

  @override
  String get teamUiGateAnswerOnHost =>
      'Answer this on the host. The phone can only watch for now.';

  @override
  String get teamUiGateAnswerOnHostPhone =>
      'Answer this in the host on this phone. The app can only watch for now.';

  @override
  String get teamUiGateCloseOnHost =>
      'Close this on the host. The phone can only watch for now.';

  @override
  String get teamUiGateCloseOnHostPhone =>
      'Close this in the host on this phone. The app can only watch for now.';

  @override
  String get teamUiGateDestructive => 'Destructive';

  @override
  String get teamUiGateFailureActionAgent =>
      'Restart the agent on the host; it picks the work item up again.';

  @override
  String get teamUiGateFailureActionAuthentication =>
      'Sign in again on the host (provider key or token), then retry the run.';

  @override
  String get teamUiGateFailureActionContext =>
      'Restart the agent with a fresh context on the host; it resumes from the work item.';

  @override
  String get teamUiGateFailureActionDependency =>
      'Install or update the missing dependency on the host, then retry the run.';

  @override
  String get teamUiGateFailureActionExecution =>
      'Read the step log on the host, fix the command, then retry the run.';

  @override
  String get teamUiGateFailureActionInfrastructure =>
      'Check the host and its services, then retry the run.';

  @override
  String get teamUiGateFailureActionMergeConflict =>
      'Resolve the conflict in the worktree on the host, then retry the run.';

  @override
  String get teamUiGateFailureActionTest =>
      'Fix the failing tests on the host, then retry the run.';

  @override
  String get teamUiGateFailureActionUnknown =>
      'Read the error on the host and decide there; the phone cannot act on it yet.';

  @override
  String get teamUiGateFailureAffectedNone => 'No open work item of this run.';

  @override
  String get teamUiGateFailureAffectedWork => 'Affected work';

  @override
  String get teamUiGateFailureClassAgent => 'Agent';

  @override
  String get teamUiGateFailureClassAuthentication => 'Authentication';

  @override
  String get teamUiGateFailureClassContext => 'Context';

  @override
  String get teamUiGateFailureClassDependency => 'Dependency';

  @override
  String get teamUiGateFailureClassExecution => 'Execution';

  @override
  String get teamUiGateFailureClassInfrastructure => 'Infrastructure';

  @override
  String get teamUiGateFailureClassMergeConflict => 'Merge conflict';

  @override
  String get teamUiGateFailureClassTest => 'Test';

  @override
  String get teamUiGateFailureClassUnknown => 'Unknown';

  @override
  String get teamUiGateFailureClassification => 'What went wrong';

  @override
  String get teamUiGateFailureErrorNone => 'The host sent no error text.';

  @override
  String get teamUiGateGone =>
      'This is no longer waiting on you; it was answered or closed on the host.';

  @override
  String get teamUiGateKindAgentBlocked => 'Agent blocked';

  @override
  String get teamUiGateLabelKind => 'Provider kind';

  @override
  String get teamUiGateLabelRequestId => 'Request id';

  @override
  String get teamUiGateLabelRunId => 'Run id';

  @override
  String get teamUiGateLabelSessionId => 'Session id';

  @override
  String get teamUiGateLabelWorkId => 'Work id';

  @override
  String get teamUiGateNoDescription => 'The host sent no description.';

  @override
  String get teamUiGateReviewOnHost =>
      'Review this on the host. The phone can only watch for now.';

  @override
  String get teamUiGateReviewOnHostPhone =>
      'Review this in the host on this phone. The app can only watch for now.';

  @override
  String get teamUiGateUnblocks => 'Unblocks';

  @override
  String get teamUiGateUnblocksNone => 'Nothing waits on this yet.';

  @override
  String get teamUiHostKindDesktop => 'Desktop computer';

  @override
  String get teamUiHostKindDisclaimerLaptop =>
      'Sleep and lid-close pause the team; runs resume on wake';

  @override
  String get teamUiHostKindDisclaimerWsl =>
      'Sleep and lid-close pause the team; runs resume on wake. WSL also stops when its last terminal closes.';

  @override
  String get teamUiHostKindHint =>
      'Only changes the reminder shown with the team.';

  @override
  String get teamUiHostKindLabel => 'Kind of computer';

  @override
  String get teamUiHostKindLaptop => 'Laptop';

  @override
  String get teamUiHostKindWsl => 'Windows (WSL)';

  @override
  String get teamUiReceiptSent => 'Sent · waiting for the host to confirm';

  @override
  String get teamUiReceiptAnswered => 'Answered';

  @override
  String get teamUiReceiptUnconfirmed =>
      'Sent, unconfirmed — check on the host before re-sending';

  @override
  String get teamUiGateAnswerSend => 'Send';

  @override
  String get teamUiGateAnswerApprove => 'Approve';

  @override
  String get teamUiGateAnswerDeny => 'Deny';

  @override
  String get teamUiGateAnswerMarkDone => 'Mark done';

  @override
  String get teamUiGateAnswerHint => 'Type your answer';

  @override
  String teamUiGateAnswerRunRetry(String work, String agent) {
    return 'Send $work to $agent again';
  }

  @override
  String get teamUiGateAnswerRunLogs => 'Watch the agent';

  @override
  String get teamUiGateAnswerRunCancel => 'Stop work';

  @override
  String teamUiGateAnswerRejected(String message) {
    return 'Not accepted: $message';
  }

  @override
  String get teamUiGateAnswerRejectedNoMessage =>
      'The host did not accept this answer.';

  @override
  String get teamUiGateAnswerConfirmApproveTitle =>
      'Approve this destructive action?';

  @override
  String get teamUiGateAnswerConfirmApproveBody =>
      'The host marks this as destructive. It cannot be undone from the phone.';

  @override
  String get teamUiGateAnswerConfirmCancelRunTitle => 'Stop this work?';

  @override
  String get teamUiGateAnswerConfirmCancelRunBody =>
      'The run stops and its open work stays as it is.';

  @override
  String get teamUiControlMessage => 'Message';

  @override
  String get teamUiControlNudge => 'Nudge';

  @override
  String get teamUiControlPause => 'Pause';

  @override
  String get teamUiControlResume => 'Resume';

  @override
  String get teamUiControlStop => 'Stop';

  @override
  String get teamUiControlRestart => 'Restart';

  @override
  String get teamUiControlReassign => 'Reassign work…';

  @override
  String get teamUiControlCreateWork => 'Task sent to an agent';

  @override
  String teamUiControlStopConfirmTitle(String agent) {
    return 'Stop $agent?';
  }

  @override
  String get teamUiControlStopConfirmBody =>
      'Its session ends now. Its work stays where it is; the host can wake it again later.';

  @override
  String teamUiControlRestartConfirmTitle(String agent) {
    return 'Restart $agent?';
  }

  @override
  String get teamUiControlRestartConfirmBody =>
      'Its session stops and starts again. The agent loses what it had in context and picks its work up from the host.';

  @override
  String get teamUiControlKeep => 'Keep going';

  @override
  String get teamUiControlReceiptSent => 'Sent';

  @override
  String get teamUiControlReceiptConfirmed => 'Confirmed';

  @override
  String get teamUiControlReceiptUnconfirmed => 'Unconfirmed';

  @override
  String get teamUiControlReceiptRefused => 'Refused';

  @override
  String teamUiControlReceiptLine(String control, String state) {
    return '$control · $state';
  }

  @override
  String get teamUiControlCancelRun => 'Stop run';

  @override
  String get teamUiStartRunFab => 'Give the team a task';

  @override
  String get teamUiStartRunTitle => 'Give the team a task';

  @override
  String get teamUiStartRunObjectiveLabel => 'Objective';

  @override
  String get teamUiStartRunObjectiveHint =>
      'What should the team achieve? One outcome, in your words.';

  @override
  String get teamUiStartRunObjectiveEmpty => 'Write an objective first.';

  @override
  String get teamUiStartRunProjectLabel => 'Project';

  @override
  String get teamUiStartRunProjectAny => 'Let the planner choose';

  @override
  String get teamUiStartRunSupervisionLabel => 'Supervision';

  @override
  String get teamUiStartRunSupervisionHigh => 'High';

  @override
  String get teamUiStartRunSupervisionHighHint =>
      'The team asks before every decision, before tests that change state and before any merge.';

  @override
  String get teamUiStartRunSupervisionBalanced => 'Balanced';

  @override
  String get teamUiStartRunSupervisionBalancedHint =>
      'The team decides routine matters itself and asks before merges, on failures and on design choices.';

  @override
  String get teamUiStartRunSupervisionAutonomous => 'Autonomous';

  @override
  String get teamUiStartRunSupervisionAutonomousHint =>
      'The team works to completion inside the host\'s boundaries and asks only when it cannot continue.';

  @override
  String get teamUiStartRunPlannerLabel => 'Planner';

  @override
  String get teamUiStartRunPlannerMayor => 'Mayor';

  @override
  String teamUiStartRunSend(String planner) {
    return 'Send to the $planner';
  }

  @override
  String get teamUiStartRunHostGuide => 'Host guide';

  @override
  String get teamUiStartRunWaking => 'Waking the planner…';

  @override
  String get teamUiStartRunDirectIntro =>
      'The planner is off on this host. Give one task straight to the project\'s agent.';

  @override
  String get teamUiStartRunDirectTitle => 'Task';

  @override
  String get teamUiStartRunDirectTitleHint =>
      'One line: what should the agent do?';

  @override
  String get teamUiStartRunDirectTitleRequired => 'Write a task first.';

  @override
  String get teamUiStartRunDirectDetails => 'Details (optional)';

  @override
  String get teamUiStartRunDirectSend => 'Send to an agent';

  @override
  String teamUiStartRunRefused(String reason) {
    return 'The host refused the objective: $reason';
  }

  @override
  String get teamUiMergeTitleReady => 'Ready to merge';

  @override
  String get teamUiMergeTitleNotReady => 'Not ready to merge';

  @override
  String get teamUiMergeTitleMerged => 'Merged';

  @override
  String teamUiMergeRequest(String id) {
    return 'merge request $id';
  }

  @override
  String get teamUiMergeLineWork => 'Work items';

  @override
  String get teamUiMergeLineTests => 'Tests';

  @override
  String get teamUiMergeLineBuild => 'Build';

  @override
  String get teamUiMergeLineReview => 'Review';

  @override
  String get teamUiMergeLineConflicts => 'No conflicts';

  @override
  String get teamUiMergeLineAcceptance => 'Acceptance criteria';

  @override
  String teamUiMergeFiles(int files, int additions, int deletions) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files files · +$additions / −$deletions',
      one: '1 file · +$additions / −$deletions',
      zero: 'No file changes',
    );
    return '$_temp0';
  }

  @override
  String get teamUiMergeReviewChanges => 'Review changes';

  @override
  String get teamUiMergeApprove => 'Approve request';

  @override
  String teamUiMergeApprovedBy(String login) {
    return 'Approved by $login';
  }

  @override
  String get teamUiMergeMerge => 'Merge';

  @override
  String teamUiMergeConfirmTitle(String branch) {
    return 'Merge into $branch?';
  }

  @override
  String get teamUiMergeConfirmMessage =>
      'This cannot be undone from the phone';

  @override
  String teamUiMergeConfirmAction(String branch) {
    return 'Merge into $branch';
  }

  @override
  String teamUiMergeDisabledReason(String line, String detail) {
    return 'Merge is off: $line — $detail';
  }

  @override
  String teamUiMergeBoundary(String text) {
    return 'Host boundary: $text';
  }

  @override
  String teamUiMergeRefused(String text) {
    return 'The host refused: $text';
  }

  @override
  String teamUiMergeMerged(String branch, String commit) {
    return 'Merged into $branch · $commit';
  }

  @override
  String teamUiMergeAlready(String branch) {
    return 'Already on $branch';
  }

  @override
  String teamUiMergeUnavailable(String reason) {
    return 'Merge readiness unavailable: $reason';
  }

  @override
  String get teamUiMergeNoRoles => 'The host has no merge roles for this run';

  @override
  String get teamUiMergeLoading => 'Checking merge readiness…';

  @override
  String get teamUiMergePending => 'running on the host';

  @override
  String get teamUiMergeChangesTitle => 'Changes';

  @override
  String get teamUiMergeChangesEmpty => 'No file changes reported by the host';

  @override
  String get teamUiMergeChangesWork => 'Work items';

  @override
  String get teamUiMergeSent => 'Sent · waiting for the host to confirm';

  @override
  String get teamUiMergeApproveConfirmed => 'Approval recorded';

  @override
  String teamUiPolicySupervision(String level) {
    return 'Supervision · $level';
  }

  @override
  String get teamUiPolicyBoundariesLabel => 'Boundaries';

  @override
  String get teamUiPolicyBoundariesNone => 'No boundaries set on the host';

  @override
  String get teamUiPolicyFromHost => 'Set on the host · read-only here';

  @override
  String teamUiPolicyRig(String rig) {
    return 'for $rig';
  }

  @override
  String teamUiPolicySemantics(String level, String boundaries) {
    return 'Supervision $level. Boundaries: $boundaries';
  }

  @override
  String get teamUiRunLabelRawTitle => 'Provider title';

  @override
  String get termuxStorageTitle => 'Storage on this phone';

  @override
  String termuxStorageRowUsed(String size) {
    return '$size used';
  }

  @override
  String get termuxStorageRowNotScanned => 'Not scanned yet';

  @override
  String get termuxStorageRowScanning => 'Measuring…';

  @override
  String get termuxStorageScanAction => 'Scan storage';

  @override
  String get termuxStorageRescanAction => 'Scan again';

  @override
  String get termuxStorageIntro =>
      'See what Termux uses on this phone. Only caches that rebuild themselves can be cleaned here; your projects, sign-ins and conversations stay.';

  @override
  String get termuxStorageScanning => 'Measuring storage';

  @override
  String get termuxStorageScanningDetail =>
      'Large caches take a minute or two. You can leave this screen; the scan keeps going.';

  @override
  String get termuxStorageCancel => 'Stop scan';

  @override
  String get termuxStorageCancelled => 'Scan stopped';

  @override
  String get termuxStorageFailed => 'The scan did not finish. Try again.';

  @override
  String termuxStorageTotal(String size) {
    return '$size measured in Termux';
  }

  @override
  String termuxStorageDeletableTotal(String size) {
    return '$size can be cleaned';
  }

  @override
  String get termuxStorageScannedJustNow => 'Scanned just now';

  @override
  String termuxStorageScannedMinutesAgo(int minutes) {
    return 'Scanned $minutes min ago';
  }

  @override
  String termuxStorageScannedHoursAgo(int hours) {
    return 'Scanned $hours h ago';
  }

  @override
  String get termuxStorageCatBuildCaches => 'Build caches';

  @override
  String get termuxStorageNoteBuildCaches =>
      'Gradle caches and npm’s downloaded content cache only. Downloads may be needed again, so offline builds can be affected. Stop builds and package installs before cleaning.';

  @override
  String get termuxStorageCatAgentScratch => 'Agent scratch';

  @override
  String get termuxStorageNoteAgentScratch =>
      'Temporary folders may contain unfinished work or files used by other tools. Their sizes are shown for reference; they cannot be removed here.';

  @override
  String get termuxStorageCatProjectBuildOutputs =>
      'Folders with build-related names';

  @override
  String get termuxStorageNoteProjectBuildOutputs =>
      'Folders named build, .dart_tool, node_modules or target may also contain your files. Names alone cannot prove they are disposable, so they cannot be removed here.';

  @override
  String get termuxStorageCatToolchains => 'Toolchains';

  @override
  String get termuxStorageNoteToolchains =>
      'Android SDK, Java and Flutter installations. These may support other projects and cannot be removed here.';

  @override
  String get termuxStorageCatAiTeam => 'AI Team';

  @override
  String get termuxStorageNoteAiTeam =>
      'Team folders, databases and tools may contain work you need to keep. They cannot be removed here, even when the team is stopped.';

  @override
  String get termuxStorageCatOpenCode => 'OpenCode itself';

  @override
  String get termuxStorageNoteOpenCode =>
      'The server, its sign-ins and conversation history. Conversations have their own screen.';

  @override
  String get termuxStorageCatProjects => 'Projects (your files)';

  @override
  String get termuxStorageNoteProjects =>
      'Listed so you can see their size. Never removed from here.';

  @override
  String get termuxStorageWillRemove => 'What Clean removes';

  @override
  String get termuxStorageNothingHere => 'Nothing here';

  @override
  String termuxStorageCleanConfirmTitle(String size, String category) {
    return 'Delete $size of $category?';
  }

  @override
  String get termuxStorageCleanConfirmBody =>
      'Delete only the listed Gradle and npm content caches? Downloads may be needed again and offline builds can be affected. Stop builds and package installs first. Scan again afterward to update the measured sizes.';

  @override
  String termuxStorageCleanConfirm(String size) {
    return 'Delete $size';
  }

  @override
  String get termuxStorageKeep => 'Keep';

  @override
  String get termuxStorageCleaning => 'Deleting…';

  @override
  String termuxStorageFreed(String size) {
    return 'Removed $size';
  }

  @override
  String get termuxStorageFreedNothing => 'Nothing was removed';

  @override
  String termuxStorageInUse(String process) {
    return 'In use by $process. Stop it under Running on this phone first.';
  }

  @override
  String termuxStorageRefusedCount(int count) {
    return '$count paths were left in place';
  }

  @override
  String termuxStorageProjectBuild(String size, String build) {
    return '$size · $build in build-related folders';
  }

  @override
  String get termuxStorageOpenRunning => 'Open Running on this phone';

  @override
  String termuxStorageBytesGb(String value) {
    return '$value GB';
  }

  @override
  String termuxStorageBytesMb(String value) {
    return '$value MB';
  }

  @override
  String termuxStorageBytesKb(String value) {
    return '$value KB';
  }

  @override
  String termuxStorageBytesB(String value) {
    return '$value B';
  }

  @override
  String get termuxProcsTitle => 'Running on this phone';

  @override
  String get termuxProcsRowLoading => 'Checking…';

  @override
  String get termuxProcsAutoRefresh => 'Refreshes every 10 seconds while open';

  @override
  String termuxProcsStopSemantics(String name) {
    return 'Stop $name';
  }

  @override
  String termuxProcsStopOneTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get termuxProcsStopOneBody =>
      'It gets a polite stop, then a forced one after 5 seconds.';

  @override
  String get termuxProcsProtected => 'Protected · control it from This phone';

  @override
  String termuxProcsOrphanParentGone(String elapsed) {
    return 'Its parent is gone · running for $elapsed';
  }

  @override
  String termuxProcsOrphanCpu(String cpu) {
    return '$cpu of CPU with no owner';
  }

  @override
  String get termuxProcsStopping => 'Stopping…';

  @override
  String termuxProcsStopped(int count) {
    return 'Stopped $count';
  }

  @override
  String termuxProcsStoppedForced(int count, int forced) {
    return 'Stopped $count ($forced needed a forced stop)';
  }

  @override
  String termuxProcsRemaining(int count) {
    return '$count would not stop';
  }

  @override
  String termuxProcsRefused(int count) {
    return '$count protected, not stopped';
  }

  @override
  String get termuxProcsEmpty => 'Nothing is running in the phone server';

  @override
  String get termuxProcsCommand => 'Command';

  @override
  String get termuxProcsFolder => 'Folder';

  @override
  String termuxProcsDurationSeconds(int seconds) {
    return '$seconds s';
  }

  @override
  String termuxProcsDurationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String termuxProcsDurationHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String termuxProcsMemoryMb(int mb) {
    return '$mb MB';
  }

  @override
  String get teamUiPhoneOptionalTag => 'Optional · experimental';

  @override
  String get teamUiPhoneChooseProjectTitle => 'Choose a project';

  @override
  String get teamUiPhoneContinue => 'Continue';

  @override
  String get teamUiPhoneSuccessTitle => 'AI Team is running on this phone';

  @override
  String get teamUiPhoneRetry => 'Try again';

  @override
  String teamUiPhoneFailedChecksum(String name) {
    return 'The downloaded $name did not match the checksum this build pins, so it was not installed. Nothing from it was kept; check the network and try again.';
  }

  @override
  String get teamUiPhoneFailedUnsupportedArch =>
      'This phone\'s processor is not 64-bit ARM, which the team runtime needs.';

  @override
  String get teamUiPhoneFailedDownload =>
      'The download did not finish. Check the connection and try again.';

  @override
  String teamUiPhoneFailedDownloadDns(String host) {
    return 'The phone could not find the download server $host. Check that the phone is online and that no private DNS or ad blocker is blocking it, then try again.';
  }

  @override
  String teamUiPhoneFailedDownloadConnect(String host) {
    return 'The phone could not reach the download server $host. Check the connection, then try again; the download continues where it stopped.';
  }

  @override
  String teamUiPhoneFailedDownloadTimeout(String host) {
    return 'The download server $host took too long to answer. Try again on a steadier connection; the download continues where it stopped.';
  }

  @override
  String teamUiPhoneFailedDownloadTls(String host) {
    return 'A secure connection to $host could not be made. Check that the phone\'s date and time are right and that no proxy is in the way, then try again.';
  }

  @override
  String teamUiPhoneFailedDownloadHttp(String host, String code) {
    return 'The download server $host refused the file (HTTP $code). Try again later; if it keeps happening, this app version\'s AI Team download is unavailable.';
  }

  @override
  String teamUiPhoneFailedDownloadInterrupted(String host) {
    return 'The connection to $host broke off during the download. Try again; the download continues where it stopped.';
  }

  @override
  String get teamUiPhoneFailedDownloadWrite =>
      'The download could not be saved on this phone. Free some space, then try again.';

  @override
  String teamUiPhoneFailedDownloadOther(String host, String code) {
    return 'The download from $host failed (error $code). Check the connection, then try again.';
  }

  @override
  String get teamUiPhoneFailedPackages =>
      'Ubuntu could not install the prerequisites (tmux, jq, lsof, procps). The output below says which.';

  @override
  String get teamUiPhoneFailedProject =>
      'The project folder is missing or is not a git repository.';

  @override
  String get teamUiPhoneFailedCity =>
      'Gas City could not create the city. The output below says why.';

  @override
  String get teamUiPhoneFailedSupervisorExited =>
      'The supervisor stopped right after starting. The output below says why.';

  @override
  String teamUiPhoneFailedHealth(String url) {
    return 'The supervisor started but never answered on $url.';
  }

  @override
  String get teamUiPhoneFailedInterrupted =>
      'The setup stopped before it finished. Android may have stopped Termux while the app was away; nothing is lost.';

  @override
  String teamUiPhoneFailedReason(String reason) {
    return 'Reason: $reason';
  }

  @override
  String get teamUiPhoneDispatchFailed =>
      'Termux did not start the step. Open Termux once, then try again.';

  @override
  String get teamUiPhoneSectionTitle => 'On this phone';

  @override
  String get teamUiPhoneStatusChecking => 'Checking…';

  @override
  String get teamUiPhoneStatusNotInstalled => 'Not installed';

  @override
  String get teamUiPhoneStatusInstalled => 'Installed · no city yet';

  @override
  String get teamUiPhoneStatusStopped => 'Stopped';

  @override
  String get teamUiPhoneStatusStarting => 'Starting…';

  @override
  String get teamUiPhoneStatusStopping => 'Stopping…';

  @override
  String teamUiPhoneStatusWorking(String verb) {
    return 'Working… ($verb)';
  }

  @override
  String teamUiPhoneStatusRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Running · $count agents',
      one: 'Running · 1 agent',
      zero: 'Running',
    );
    return '$_temp0';
  }

  @override
  String get teamUiPhoneStatusFailed => 'Not running · the last step failed';

  @override
  String teamUiPhoneStatusUnreachable(String url) {
    return 'Started, but not answering on $url';
  }

  @override
  String get teamUiPhoneStatusUnknown => 'Status could not be read';

  @override
  String teamUiPhoneVersions(String gc, String bd, String dolt) {
    return 'gc $gc · bd $bd · dolt $dolt';
  }

  @override
  String get teamUiPhoneStopTitle => 'Stop the team on this phone?';

  @override
  String get teamUiPhoneStopBody =>
      'Running agents stop where they are. Nothing is lost; runs resume when you start it again.';

  @override
  String get teamUiPhoneKilled =>
      'Android stopped the team while the app was away. Nothing is lost.';

  @override
  String get teamUiPhoneKeepRunningTitle => 'Keep it running';

  @override
  String get teamUiPhoneKeepRunningSubtitle =>
      'Stop Android from closing the team in the background';

  @override
  String get teamUiPhoneTipsIntro =>
      'Android stops background work it considers excessive, and the team is exactly that: dozens of short gc and bd processes and an agent at full CPU. Three things keep it alive.';

  @override
  String get teamUiPhoneTipWakeLock =>
      'Keep Termux in front, or hold its wake lock: run termux-wake-lock in Termux, or tap Acquire wakelock in its notification. Screen off without it ends the run.';

  @override
  String get teamUiPhoneTipBattery =>
      'Settings › Apps › Termux › Battery › Unrestricted, and switch off your phone maker\'s auto-clean for Termux.';

  @override
  String get teamUiPhoneTipPhantom =>
      'Android 12 and later still kill the child processes of a background app (the phantom process killer). Turn that off once, from Termux itself over Wireless debugging; no computer needed:';

  @override
  String get teamUiPhoneTipsCopy => 'Copy commands';

  @override
  String get teamUiPhoneRemoveTitle => 'Delete AI Team from this phone?';

  @override
  String teamUiPhoneActionFailed(String reason) {
    return 'That did not work: $reason';
  }

  @override
  String get teamUiPhoneNotAvailable =>
      'Not available on this phone. Running a team needs the 64-bit Linux environment; this device or build can\'t provide it.';

  @override
  String teamUiPhoneFailedNoSpace(String detail) {
    return 'Not enough space on this phone. $detail Free some space (Storage on this phone can clean build caches), then try again.';
  }

  @override
  String get phoneServerConnect => 'Connect';

  @override
  String get phoneServerOpen => 'Open';

  @override
  String get phoneServerStart => 'Start';

  @override
  String get phoneServerStop => 'Stop';

  @override
  String get phoneServerMore => 'More server actions';

  @override
  String get phoneServerForget => 'Forget saved sign-in';

  @override
  String get phoneServerStartFailed =>
      'The server did not start. Open Manage setup to see why.';

  @override
  String get phoneServerRestartFailed =>
      'The server did not restart. Open Manage setup to see why.';

  @override
  String get phoneServerStopFailed =>
      'The server could not be stopped. Try again.';

  @override
  String get termuxStorageCatSharedCaches => 'Other caches and package data';

  @override
  String get termuxStorageNoteSharedCaches =>
      'Shared caches, package installs and download folders may support other tools or contain files worth keeping. They cannot be removed here.';

  @override
  String get termuxStorageRescanRequired =>
      'Previous scan · Scan again before cleaning more';

  @override
  String get safetyStopSharingTitle => 'Stop sharing this conversation?';

  @override
  String get safetyStopSharingBody =>
      'The link stops working for anyone who has it. The conversation itself is not changed.';

  @override
  String get safetyStopSharingKeep => 'Keep sharing';

  @override
  String get safetyStopLocalServerTitle => 'Stop the local server?';

  @override
  String get safetyStopLocalServerBody =>
      'Anything the agent is running on this phone is interrupted, and this app disconnects from it. Your projects and conversations stay on the phone; start the server again to continue.';

  @override
  String get safetyStopLocalServerKeep => 'Keep running';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return 'Disconnect $server?';
  }

  @override
  String get safetyMcpDisconnectBody =>
      'Agents lose its tools until you connect it again, and a tool call in progress may fail. Its configuration stays saved.';

  @override
  String get safetyMcpDisconnectKeep => 'Stay connected';

  @override
  String get safetyStopOrphanBody =>
      'Nothing is waiting on it, but whatever it was still doing is lost. It gets a polite stop, then a forced one after 5 seconds.';

  @override
  String get settingsHubGroupNotifications => 'Notifications and background';

  @override
  String get settingsHubGroupUsage => 'Usage';

  @override
  String get settingsHubGroupHelp => 'Help';

  @override
  String get settingsHubThisServer => 'This server';

  @override
  String get settingsHubAccounts => 'Accounts';

  @override
  String get settingsHubModelAndMode => 'Model and mode';

  @override
  String get settingsHubVoice => 'Voice';

  @override
  String get settingsHubPrivacyRow => 'Privacy and data';

  @override
  String get settingsHubSearchServerAliases =>
      'server host url address password profile connection health status version update service';

  @override
  String get settingsHubSearchSavedServersAliases =>
      'servers profiles host url password switch add edit remove profile connection';

  @override
  String get settingsHubSearchPhoneAliases =>
      'phone termux local on-device on device android install setup storage services';

  @override
  String get settingsHubSearchAccountsAliases =>
      'account codex sign in login logout';

  @override
  String get settingsHubSearchExternalAgentsAliases =>
      'a2a external agents remote';

  @override
  String get settingsHubSearchTailscaleAliases =>
      'tailscale vpn network remote private';

  @override
  String get settingsHubSearchDisconnectAliases => 'disconnect leave server';

  @override
  String get settingsHubSearchModelModeAliases =>
      'model mode agent variant thinking default selected session chat conversation';

  @override
  String get settingsHubSearchShellAliases => 'shell terminal bash zsh command';

  @override
  String get settingsHubSearchPermissionsAliases =>
      'permissions approvals always allow allowed revoke';

  @override
  String get settingsHubSearchTranscriptAliases =>
      'transcript display thinking reasoning timestamps usage';

  @override
  String get settingsHubSearchVoiceAliases =>
      'voice speech microphone dictation model';

  @override
  String get settingsHubSearchNotificationsAliases =>
      'notifications and background keep running what runs by itself automation always allowed actions alerts quiet hours background check-in check in wi-fi wifi monitor saved servers finished runs approvals questions quota thresholds';

  @override
  String get settingsHubSearchAppearanceAliases =>
      'appearance theme dark light language arabic english colors';

  @override
  String get settingsHubSearchModelsAliases =>
      'models agents provider AI reasoning favorites recent';

  @override
  String get settingsHubSearchProvidersAliases =>
      'provider api key keys authentication connect';

  @override
  String get settingsHubSearchMcpAliases => 'mcp integrations servers tools';

  @override
  String get settingsHubSearchCommandsAliases =>
      'commands tools skills references slash capabilities';

  @override
  String get settingsHubSearchPluginsAliases =>
      'plugins plugin installed source status AI Team Gas City';

  @override
  String get settingsHubSearchUsageAliases =>
      'usage cost tokens budget quota limit spent remaining threshold quota monitoring provider';

  @override
  String get settingsHubSearchPrivacyAliases =>
      'privacy drafts queue queued prompts read state storage clear delete';

  @override
  String get settingsHubSearchGuideAliases =>
      'help guide connect tutorial start';

  @override
  String get settingsHubSearchBugAliases =>
      'bug feedback issue support report problem crash diagnostics errors log github';

  @override
  String get settingsHubSearchDiagnosticsAliases =>
      'diagnostics debug errors log';

  @override
  String get settingsHubSearchAboutAliases =>
      'about version licenses open source notices privacy data';

  @override
  String get pluginsSectionInApp => 'In this app';

  @override
  String get pluginsSectionOnServer => 'Plugins on this server';

  @override
  String get notifySectionWhat => 'What notifies me';

  @override
  String get notifyFinishedRuns => 'Finished runs';

  @override
  String get notifyFinishedRunsDetail =>
      'When a run on the connected server finishes or fails.';

  @override
  String get notifyRequests => 'Approvals and questions';

  @override
  String get notifyRequestsDetail =>
      'When the agent is waiting for your answer, on any monitored server.';

  @override
  String get notifyQuotaAlerts => 'Quota thresholds';

  @override
  String get notifyQuotaAlertsDetail =>
      'When a monitored provider passes the threshold you set in Usage.';

  @override
  String get notifyBlockedTitle => 'Notifications are off for this app';

  @override
  String get notifyBlockedMessage =>
      'Android is not letting OpenCode notify you, so finished runs, approvals and quota alerts cannot arrive until this is fixed.';

  @override
  String get notifySendTest => 'Send a test notification';

  @override
  String get notifySendTestDetail =>
      'Confirms whether Android is actually delivering this app\'s notifications right now.';

  @override
  String get notifyQuietDetail => 'Silence notifications during these hours.';

  @override
  String get notifySectionBackground => 'Background';

  @override
  String get notifySectionServers => 'Saved servers';

  @override
  String get notifyWifiOnly => 'Check in the background on Wi-Fi only';

  @override
  String notifyHubBackgroundSummary(String state) {
    return 'Background: $state';
  }

  @override
  String get usageSectionSpent => 'Spent';

  @override
  String get usageSectionRemaining => 'Remaining';

  @override
  String get shellTabWork => 'Work';

  @override
  String get shellTabInbox => 'Inbox';

  @override
  String get shellTabProject => 'Project';

  @override
  String get serverSwitcherManage => 'Manage servers';

  @override
  String get serverSwitcherOpen => 'Switch server';

  @override
  String get discoverSearchGoTo => 'Go to';

  @override
  String discoverSearchIn(String parent) {
    return 'In $parent';
  }

  @override
  String get discoverWorkAliases =>
      'work home conversations sessions chats recent pinned new conversation';

  @override
  String get discoverInboxAliases =>
      'inbox activity needs you approvals permissions questions forms waiting running finished';

  @override
  String get discoverProjectAliases =>
      'project tools code folder files changes terminal health worktrees';

  @override
  String get discoverFilesAliases =>
      'files browse folder tree preview code open file';

  @override
  String get discoverChangesAliases =>
      'changes review changes diff uncommitted git edits working tree';

  @override
  String get discoverTerminalAliases =>
      'terminal shell console command line pty';

  @override
  String get discoverHealthAliases =>
      'project health branch changed files language services formatters lsp';

  @override
  String get discoverWorktreesAliases =>
      'worktrees branches isolated git branch';

  @override
  String get discoverSearchFilesAliases => 'search files find file name';

  @override
  String get discoverAllConversationsAliases =>
      'all conversations sessions chats history every project search';

  @override
  String get discoverTeamAliases =>
      'ai team agents runs needs you orchestration';

  @override
  String get discoverNotifyServersTitle => 'Notifications from saved servers';

  @override
  String get discoverNotifyWhatAliases =>
      'finished runs approvals questions check-ins check in quota alerts what notifies';

  @override
  String get discoverNotifyQuietAliases =>
      'quiet hours do not disturb night silence mute schedule';

  @override
  String get discoverNotifyBackgroundAliases =>
      'background connection stay connected keep alive service';

  @override
  String get discoverNotifyServersAliases =>
      'monitor saved servers attention wi-fi wifi check in the background';

  @override
  String get discoverAppearanceModeAliases =>
      'light dark mode system appearance night';

  @override
  String get discoverLanguageAliases =>
      'language arabic english locale translation rtl';

  @override
  String get discoverThemeAliases => 'theme colors palette pack accent';

  @override
  String get discoverSpentAliases => 'spent cost tokens usage statistics money';

  @override
  String get discoverRemainingAliases =>
      'remaining quota limit provider plan left';

  @override
  String get discoverBudgetAliases =>
      'budget budgets usd token budget spending limit';

  @override
  String get discoverQuotaMonitorAliases =>
      'quota monitoring threshold alert warn low';

  @override
  String get discoverCommandsAliases => 'commands server commands slash run';

  @override
  String get discoverToolsAliases =>
      'tools tools and capabilities inventory model tools';

  @override
  String get discoverSkillsAliases => 'skills skill instructions playbook';

  @override
  String get discoverReferencesAliases =>
      'references reference docs sources context';

  @override
  String get discoverRunningNowAliases =>
      'running now running on this phone processes termux services stop busy memory background';

  @override
  String get discoverStorageAliases =>
      'storage on this phone disk space clean termux';

  @override
  String get discoverMonitorAliases =>
      'saved-server attention monitor other servers waiting server attention';

  @override
  String get discoverConnectionHelpAliases =>
      'add server computer connect pair pairing code connection help cannot connect troubleshooting network refused timeout';

  @override
  String gestureEquivFileRowActions(String name) {
    return 'Actions for $name';
  }

  @override
  String get gestureEquivShortcutFindMatch =>
      'Next / previous match while finding in a conversation';

  @override
  String get gestureEquivShortcutPromptHistory =>
      'Earlier / later prompt, with the cursor at the start or end of the message box';

  @override
  String get emptyTeachInboxMessage =>
      'Nothing needs you. Approvals and questions from running work appear here.';

  @override
  String get emptyTeachWorkTitle => 'No conversations yet';

  @override
  String get emptyTeachWorkMessage =>
      'Conversations you start in this project are listed here, with the ones that need you first. Start one with New conversation.';

  @override
  String get emptyTeachChangesTitle => 'No changes yet';

  @override
  String get emptyTeachChangesMessage =>
      'Edits the agent makes show up here to review.';

  @override
  String get emptyTeachWorktreesMessage =>
      'A worktree is a separate copy of this project on its own branch, so parallel work does not mix. Worktrees of this project appear here.';

  @override
  String get emptyTeachAllowedMessage =>
      'When you choose Always allow on an approval in this project, it is listed here so you can take it back.';

  @override
  String get emptyTeachTeamRunsMessage =>
      'Say what you need, and the team splits it into steps and shows its progress here.';

  @override
  String get emptyTeachSkillsMessage =>
      'Skills are reusable instructions the agent can follow. Skills from this project and this server appear here.';

  @override
  String get emptyTeachToolsMessage =>
      'Tools the agent can call with this model appear here. This model has none.';

  @override
  String get capabilityScreenTitle => 'Available on this server';

  @override
  String get capabilityScreenAliases =>
      'available supported not available missing feature hidden why can\'t capabilities server support shell';

  @override
  String capabilityScreenIntro(String server) {
    return '$server decides what appears in this app. Anything it cannot do is left out of the menus and tabs instead of being shown greyed out.';
  }

  @override
  String get capabilityAllAvailable =>
      'This server supports everything the app offers.';

  @override
  String get capabilityFiles => 'Files';

  @override
  String get capabilityFilesDetail =>
      'Browse, search and preview the project\'s files';

  @override
  String get capabilityChanges => 'Changes';

  @override
  String get capabilityChangesDetail => 'Review what the agent edited';

  @override
  String get capabilityTerminal => 'Terminal';

  @override
  String get capabilityTerminalDetail => 'Run commands in the project';

  @override
  String get capabilityShell => 'Default shell';

  @override
  String get capabilityShellDetail =>
      'Choose the shell that commands and terminals use';

  @override
  String get capabilityAttachments => 'Attachments';

  @override
  String get capabilityAttachmentsDetail =>
      'Send files and photos with a prompt';

  @override
  String get capabilitySubagents => 'Delegate to a subagent';

  @override
  String get capabilitySubagentsDetail => 'Mention an agent with @ in a prompt';

  @override
  String get capabilityCompact => 'Compact';

  @override
  String get capabilityCompactDetail =>
      'Summarize a long conversation to free up context';

  @override
  String get capabilityShare => 'Share';

  @override
  String get capabilityShareDetail => 'Publish a link to a conversation';

  @override
  String get capabilityFork => 'Fork';

  @override
  String get capabilityForkDetail =>
      'Branch a conversation from an earlier message';

  @override
  String get capabilityRevert => 'Revert';

  @override
  String get capabilityRevertDetail => 'Undo a prompt and the edits it made';

  @override
  String get capabilityArchive => 'Archive';

  @override
  String get capabilityArchiveDetail =>
      'Put finished conversations away without deleting them';

  @override
  String get capabilityTodos => 'Todos';

  @override
  String get capabilityTodosDetail =>
      'See the agent\'s task list for a conversation';

  @override
  String get capabilityNotes => 'Note for the agent';

  @override
  String get capabilityNotesDetail =>
      'Keep standing instructions with a conversation';

  @override
  String get capabilityImportExport => 'Import and export';

  @override
  String get capabilityImportExportDetail =>
      'Move a conversation between servers as a file';

  @override
  String get capabilitySearchAll => 'All conversations';

  @override
  String get capabilitySearchAllDetail =>
      'Search conversations across every project';

  @override
  String get capabilityAlwaysAllow => 'Always allowed actions';

  @override
  String get capabilityAlwaysAllowDetail =>
      'Remember an approval so it is not asked again';

  @override
  String get capabilityModels => 'Models and providers';

  @override
  String get capabilityModelsDetail =>
      'Browse models and sign in to providers from the app';

  @override
  String get capabilitySkills => 'Skills and commands';

  @override
  String get capabilitySkillsDetail =>
      'List the server\'s skills, commands and references';

  @override
  String get capabilityMcp => 'MCP';

  @override
  String get capabilityMcpDetail => 'See and connect MCP servers';

  @override
  String get capabilityPlugins => 'Plugins';

  @override
  String get capabilityPluginsDetail =>
      'See the plugins installed on the server';

  @override
  String get capabilityCloud => 'Cloud environments';

  @override
  String get capabilityCloudDetail => 'Run a project in a managed environment';

  @override
  String get capabilityProjects => 'Projects';

  @override
  String get capabilityProjectsDetail =>
      'Switch projects and check a project\'s health';

  @override
  String get capabilityWorktrees => 'Worktrees';

  @override
  String get capabilityWorktreesDetail => 'Give a task its own isolated branch';

  @override
  String get capabilityUsage => 'Usage';

  @override
  String get capabilityUsageDetail => 'See what conversations have cost';

  @override
  String get capabilityOfflineQueue => 'Send later';

  @override
  String get capabilityOfflineQueueDetail =>
      'Queue a prompt while offline and send it on reconnect';

  @override
  String get capabilityContinueOnComputer => 'Continue on computer';

  @override
  String get capabilityContinueOnComputerDetail =>
      'Get a command that reopens the conversation at your desk';

  @override
  String get capabilityServerUpdates => 'Server updates';

  @override
  String get capabilityServerUpdatesDetail => 'Update the server from the app';

  @override
  String get capabilityBackgroundNotifications =>
      'Notifications in the background';

  @override
  String get capabilityBackgroundNotificationsDetail =>
      'Be told when work finishes or needs you while the app is closed';

  @override
  String get capabilityOnThisPhone => 'On this phone';

  @override
  String get capabilityOnThisPhoneDetail =>
      'Run the agent\'s server on this device';

  @override
  String get capabilityVoice => 'Voice';

  @override
  String get capabilityVoiceDetail =>
      'Dictate prompts with on-device speech models';

  @override
  String get discoverShowTipsAgain => 'Show tips again';

  @override
  String get discoverShowTipsSubtitle =>
      'One-time tips will appear again at their moment';

  @override
  String get discoverShowTipsAliases =>
      'tips hints nudges help reset show again tutorial';

  @override
  String get discoverShowTipsDone => 'Tips will show again.';

  @override
  String nudgeApprovals(String action) {
    return 'Asked for “$action” 3 times: this conversation can approve requests for you.';
  }

  @override
  String get nudgeReviewChanges =>
      'OpenCode changed files. Look them over before you go on.';

  @override
  String get nudgeLeave =>
      'You can leave: this phone tells you when the run is done.';

  @override
  String nudgeCompact(String percent) {
    return 'The context is $percent% full: compact to keep going.';
  }

  @override
  String get nudgePin =>
      'Pin conversations you return to from their menu; they stay at the top of Work.';

  @override
  String get nudgeDismiss => 'Hide tip';

  @override
  String get firstRunWhereQuestion => 'Where does your coding agent run?';

  @override
  String get firstRunOnComputer => 'On my computer';

  @override
  String get firstRunOnComputerDetail => 'Connect to an agent that runs there.';

  @override
  String get firstRunOnPhoneDetail => 'Set one up here. No computer needed.';

  @override
  String get firstRunJustShowMe => 'Just show me';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => 'On your computer, run:';

  @override
  String get firstRunPairingNextScan =>
      'It shows a code. Scan it, or copy it and paste it here.';

  @override
  String get firstRunPairingNextPaste => 'Then paste the code it prints.';

  @override
  String get firstRunNotSameNetwork => 'Not on the same network?';

  @override
  String get firstRunShowCommands => 'Show the commands';

  @override
  String get firstRunCommandsPaseoNetwork =>
      'To reach it from this phone over your private network, listen on that address and set a password:';

  @override
  String get firstRunCommandsCodexToken =>
      'Create the connection token before you start it:';

  @override
  String get firstRunCommandsCodexUsb =>
      'A phone on a USB cable reaches it with:';

  @override
  String get firstRunNotifyTitle => 'Notify you when the agent needs you?';

  @override
  String get firstRunNotifyBody =>
      'Leave the app while the agent works. You get a notification when it needs your answer. Android shows a small ongoing notification while it stays connected.';

  @override
  String get firstRunNotifyAccept => 'Notify me';

  @override
  String get firstRunNotifyDecline => 'Not now';

  @override
  String get localAgentTitle => 'Claude Code on this phone';

  @override
  String get localAgentOfferBody =>
      'Run Claude Code here with no computer. The app installs Node.js, the Paseo daemon and Claude Code into the Ubuntu it already manages, and reaches them on this phone only.';

  @override
  String get localAgentOfferSize =>
      'About 60 MB to download for Node.js, plus the packages; about 1 GB once installed. Needs 2 GB free.';

  @override
  String get localAgentOfferWarning =>
      'Android may stop Termux in the background. The battery settings that keep the OpenCode server alive keep this alive too.';

  @override
  String get localAgentSetUp => 'Set up Claude Code';

  @override
  String get localAgentNotNow => 'Not now';

  @override
  String get localAgentNeedsUbuntuBody =>
      'Claude Code runs inside the Ubuntu this app sets up. Finish the On this phone setup first, then come back here.';

  @override
  String get localAgentOpenSetup => 'Open phone setup';

  @override
  String get localAgentNeedsTermuxBody =>
      'Claude Code needs Termux for now; the in-app Linux does not run it yet. Set up Termux to use it here.';

  @override
  String get localAgentSetUpWithTermux => 'Set up with Termux';

  @override
  String get localAgentStepNode => 'Node.js';

  @override
  String get localAgentStepPaseo => 'Paseo daemon';

  @override
  String get localAgentStepClaude => 'Claude Code';

  @override
  String get localAgentStepSignIn => 'Sign in to Claude';

  @override
  String get localAgentStepStart => 'Start on this phone';

  @override
  String get localAgentInstalling => 'Setting up Claude Code';

  @override
  String get localAgentLeaveNote =>
      'You can leave this screen. The install keeps going in Termux.';

  @override
  String get localAgentSignInBody =>
      'Termux opens and Claude Code shows a link. Approve it in your browser, paste the code back into Termux, then return here. This app never sees or stores your Claude sign-in.';

  @override
  String get localAgentSignInAlready => 'I already signed in';

  @override
  String localAgentSignInOpenFailed(String command) {
    return 'Termux could not be opened. Open Termux yourself and run: $command';
  }

  @override
  String get localAgentSignInMissing => 'No Claude sign-in was found yet.';

  @override
  String get localAgentReadyTitle => 'Claude Code is running on this phone';

  @override
  String get localAgentReadyBody =>
      'It listens on this phone only (127.0.0.1), behind a password this app keeps.';

  @override
  String localAgentVersions(String claude, String paseo, String node) {
    return 'Claude Code $claude · Paseo $paseo · Node.js $node';
  }

  @override
  String get localAgentInstalledTitle => 'Claude Code is installed and stopped';

  @override
  String get localAgentKilled =>
      'Android stopped Claude Code while the app was away. Nothing is lost.';

  @override
  String get localAgentFailedTitle => 'Claude Code setup stopped';

  @override
  String localAgentFailedNoSpace(String detail) {
    return 'Not enough space on this phone. $detail Free some space, then try again.';
  }

  @override
  String get localAgentFailedDownload =>
      'Node.js could not be downloaded. Check the network, then try again.';

  @override
  String get localAgentFailedChecksum =>
      'The Node.js download did not match its pinned checksum, so nothing was installed. Try again; if it happens twice, something on the network is changing the file.';

  @override
  String get localAgentFailedNativeBuild =>
      'A package needs a native module with no ready-made build for this phone. Nothing was compiled; the output below names it.';

  @override
  String get localAgentFailedPackages =>
      'The packages could not be installed. Check the network, then try again.';

  @override
  String get localAgentFailedPortInUse =>
      'Port 6767 on this phone is already used by another program. Stop that program, then try again.';

  @override
  String get localAgentFailedTimeout =>
      'Claude Code did not answer within two minutes. The output below shows what it printed.';

  @override
  String get localAgentFailedInterrupted =>
      'Android stopped the step before it finished. Try again; it picks up where it stopped.';

  @override
  String get localAgentFailedUnsupported => 'Claude Code needs a 64-bit phone.';

  @override
  String get localAgentFailedDaemon =>
      'Claude Code stopped or does not answer. Start it again.';

  @override
  String localAgentFailedReason(String detail) {
    return 'It stopped: $detail';
  }

  @override
  String get localAgentProjectTitle => 'Choose a project folder';

  @override
  String get localAgentProjectBody =>
      'Claude Code works inside one folder of the Ubuntu on this phone.';

  @override
  String get localAgentProjectPathLabel => 'Or type a path inside Ubuntu';

  @override
  String get localAgentProjectPathInvalid =>
      'Enter a full path, like /root/projects/my-app.';

  @override
  String localAgentConnectFailed(String detail) {
    return 'Could not connect to Claude Code on this phone. $detail';
  }

  @override
  String localAgentCardActionFailed(String detail) {
    return 'That did not work. $detail';
  }

  @override
  String get localAgentRestartTitle => 'Restart Claude Code on this phone?';

  @override
  String get localAgentRestartBody =>
      'Claude Code is briefly unavailable and anything it is doing right now is interrupted. Your conversations stay on the phone.';

  @override
  String get localAgentStopTitle => 'Stop Claude Code on this phone?';

  @override
  String get localAgentStopBody =>
      'Anything Claude Code is doing on this phone is interrupted, and this app disconnects from it. Your projects, conversations and Claude sign-in stay; start it again to continue.';

  @override
  String get localAgentRemove => 'Remove from this phone';

  @override
  String get localAgentRemoveTitle => 'Remove Claude Code from this phone?';

  @override
  String get localAgentRemoveBody =>
      'Stops it and deletes Node.js, Paseo and Claude Code from Ubuntu, about 1 GB. Your projects and your Claude sign-in stay.';

  @override
  String get localAgentRemoveKeep => 'Keep it';

  @override
  String get localAgentMore => 'More Claude Code actions';

  @override
  String get firstRunAgentsSideBySide =>
      'They run side by side on the same computer. Start with one, and add the others any time from the server name at the top.';

  @override
  String get firstRunPaseoTitle => 'Agents through Paseo';

  @override
  String get approvalsUiEverythingTitle => 'Approve everything on this server';

  @override
  String get approvalsUiEverythingDetail =>
      'Dangerous. Every conversation on this server, new ones and subagents included, is approved automatically while this app is connected. A conversation set to “Ask each time” still asks.';

  @override
  String get approvalsUiEverythingConfirmBody =>
      'Agents on this server will run commands and change files without asking you, in every conversation. Turn this on only for a server and projects you can afford to break.';

  @override
  String get approvalsUiEverythingActive =>
      'Following “Approve everything on this server”';

  @override
  String get approvalsUiServerRulesNoteEverything =>
      'The server’s own deny rules still apply, and automatic approval stops whenever this app disconnects.';

  @override
  String get agentErrorConnectionDropped =>
      'The connection to the model dropped.';

  @override
  String get agentErrorConnectionDroppedHint =>
      'Usually brief, and OpenCode retries by itself. If it keeps happening, check the internet on the computer running OpenCode.';

  @override
  String get agentErrorTimedOut => 'The model took too long to answer.';

  @override
  String get agentErrorTimedOutHint =>
      'OpenCode retries by itself. A smaller request or another model may be faster.';

  @override
  String get agentErrorProviderUnreachable =>
      'The server couldn’t reach the model provider.';

  @override
  String get agentErrorProviderUnreachableHint =>
      'Check the internet connection on the computer running OpenCode, then try again.';

  @override
  String get agentErrorRequestTooLarge =>
      'This request was too large to send to the model.';

  @override
  String get agentErrorRequestTooLargeHint =>
      'Send fewer or smaller attachments, or compact the conversation, then try again.';

  @override
  String get agentErrorRateLimited =>
      'The model provider is limiting how fast you can send.';

  @override
  String get agentErrorRateLimitedHint =>
      'It retries by itself after a short wait.';

  @override
  String get agentErrorProviderBusy =>
      'The model provider is overloaded right now.';

  @override
  String get agentErrorProviderBusyHint =>
      'It retries by itself. Another model may answer sooner.';

  @override
  String get agentErrorOutOfCredit =>
      'The provider account is out of credit or quota.';

  @override
  String get agentErrorOutOfCreditHint =>
      'Top up the account, or switch to another provider or model.';

  @override
  String get agentErrorServerDiskFull =>
      'The computer running OpenCode is out of disk space.';

  @override
  String get agentErrorServerDiskFullHint =>
      'Free some space there, then try again.';

  @override
  String get agentErrorRecovered => 'The agent carried on after this.';

  @override
  String chatUiRetryingSoon(String attempt) {
    return 'Retrying$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return 'Retrying$attempt in $time';
  }

  @override
  String get chatUiCompactAgain => 'Compact again';

  @override
  String get chatUiCompactionFailedHint =>
      'The conversation is still too long for the model, so the next turn will try again.';

  @override
  String get chatStripContextPending => 'Context pending';

  @override
  String get chatStripAutoApprove => 'Auto-approve';

  @override
  String get chatStripAutoApprovePaused => 'Auto-approve paused';

  @override
  String get chatStripBackground => 'Background';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return 'Contents of $name, read on the phone: values and text only, without formatting, charts or formulas.';
  }

  @override
  String get chatAttachmentOfficeTruncated =>
      'The file was larger than a prompt can carry, so it is cut short below.';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return '$name could not be read. It may be password protected, damaged, or in the older Excel or Word format; save it as .xlsx, .docx or CSV and try again.';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return '$name has nothing in it to attach.';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return '$name attached as text.';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets sheets',
      one: '1 sheet',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows rows',
      one: '1 row',
    );
    return '$name attached as text · $_temp0, $_temp1';
  }

  @override
  String get otherProjectsForget => 'Remove from recent projects';

  @override
  String get otherProjectsUntitled => 'Untitled conversation';

  @override
  String get localAgentUpdate => 'Update Claude Code';

  @override
  String get localAgentUpdateAvailable =>
      'An update adds the newest Claude models, such as Opus 5.5.';

  @override
  String get localAgentUpdateNow => 'Update';

  @override
  String get modelNewBadge => 'New';

  @override
  String get modelEffortNone => 'No thinking';

  @override
  String get modelEffortMinimal => 'Minimal';

  @override
  String get modelEffortLow => 'Low';

  @override
  String get modelEffortMedium => 'Medium';

  @override
  String get modelEffortHigh => 'High';

  @override
  String get modelEffortExtraHigh => 'Extra high';

  @override
  String get modelEffortMax => 'Max';

  @override
  String get setupStoppedByRestart =>
      'The phone restarted, so the local server stopped. Start it again when you need it.';

  @override
  String get phoneServerStoppedTitle => 'The server on this phone is stopped';

  @override
  String get phoneServerStoppedBody =>
      'It stops when the phone restarts or Android closes Termux to save battery. Your conversations are kept; start it again to continue.';

  @override
  String get phoneServerStartAndConnect => 'Start and connect';

  @override
  String get otherServersTitle => 'On your other servers';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count working',
      one: '1 working',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return 'Ask $agent…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $countString earlier lines',
      one: 'Show 1 earlier line',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Open all $countString lines';
  }

  @override
  String get setupProgressViewOverallLabel => 'Setup progress';

  @override
  String get setupProgressViewGettingStarted => 'Getting started…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '~$minutes min left',
      one: '~1 min left',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => 'Less than a minute';

  @override
  String get setupProgressViewDone => 'All set';

  @override
  String get setupProgressViewFailedTitle => 'Setup didn\'t finish';

  @override
  String get setupProgressViewInterrupted =>
      'Setup was interrupted. What\'s finished is kept.';

  @override
  String get setupProgressViewCancelled =>
      'Setup stopped. What\'s finished stays installed.';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done of $total';
  }

  @override
  String setupProgressViewPercent(int percent) {
    return '$percent%';
  }

  @override
  String setupProgressViewStageMeasured(String stage, String measured) {
    return '$stage · $measured';
  }

  @override
  String get setupProgressViewChecking => 'Checking';

  @override
  String get setupProgressViewStarting => 'Starting';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return 'Stopped during: $stage. What went wrong is under Details.';
  }

  @override
  String get setupProgressViewFailedUnknown =>
      'Setup stopped before it finished. What went wrong is under Details.';

  @override
  String get setupProgressViewNoInternet =>
      'No internet connection — Continue when you\'re back online';

  @override
  String get setupProgressViewContinue => 'Continue setup';

  @override
  String get setupProgressViewCancel => 'Stop setup';

  @override
  String get setupProgressViewNoLog => 'Nothing logged yet.';

  @override
  String get phoneSetupProgressTitle => 'Setting up OpenCode on this phone';

  @override
  String get phoneSetupProgressLeaveHint =>
      'You can leave the app. We\'ll notify you when it\'s ready.';

  @override
  String get phoneSetupProgressStopTitle => 'Stop setup?';

  @override
  String get phoneSetupProgressStopMessage =>
      'What\'s finished stays installed.';

  @override
  String get phoneSetupProgressStopConfirm => 'Stop setup';

  @override
  String get phoneSetupProgressKeepGoing => 'Keep going';

  @override
  String get builtinServerChooseRuntime => 'Which OpenCode';

  @override
  String get builtinServerStopped => 'Not running.';

  @override
  String builtinServerStartFailed(String reason) {
    return 'OpenCode did not answer: $reason. Open the log to see why.';
  }

  @override
  String get builtinServerExited => 'the server stopped';

  @override
  String builtinServerTimedOut(int seconds) {
    return 'no answer within $seconds seconds';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return 'Could not connect: $reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return 'This phone, built-in ($runtime)';
  }

  @override
  String get phoneSetupProfileName => 'This phone';

  @override
  String get phoneSetupLinuxTitle => 'Linux base';

  @override
  String get phoneSetupLinuxWhy => 'Everything else runs inside it.';

  @override
  String get phoneSetupEssentialsTitle => 'Git, SSH and certificates';

  @override
  String get phoneSetupEssentialsShort => 'Git and SSH';

  @override
  String get phoneSetupEssentialsWhy =>
      'Agents use Git and SSH to work on your projects.';

  @override
  String get phoneSetupNodeWhy => 'OpenCode runs on Node.js.';

  @override
  String get phoneSetupOpenCodeWhy => 'The coding agent itself.';

  @override
  String get phoneSetupStartTitle => 'Start OpenCode';

  @override
  String get phoneSetupStartWhy => 'Setup ends with OpenCode running.';

  @override
  String get phoneSetupStageDownloadingLinux => 'Downloading Linux base';

  @override
  String get phoneSetupStageUnpackingLinux => 'Unpacking Linux base';

  @override
  String get phoneSetupStageStarting => 'Starting OpenCode';

  @override
  String get phoneSetupNotificationChannel => 'Phone setup';

  @override
  String get phoneSetupNotificationTitle => 'Setting up OpenCode on this phone';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return '$percent% done';
  }

  @override
  String get phoneSetupNotificationDone => 'OpenCode is ready on this phone';

  @override
  String get phoneSetupNotificationStopped =>
      'Setup stopped. Open the app to continue.';

  @override
  String get phoneSetupErrorNoInternet =>
      'No internet connection. Continue when you\'re back online.';

  @override
  String phoneSetupErrorOffline(String name) {
    return 'Could not download $name: no internet connection';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return 'Could not install $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return 'The download of $name was damaged. Continue to fetch it again.';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'OpenCode was downloaded, but its program was not in the download. Continue to fetch it again.';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'OpenCode was downloaded, but its program does not run on this phone. Details show what it said.';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'OpenCode was installed, but it did not start. Continue to try again; Details show what it said.';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return 'Not enough free space to install $name';
  }

  @override
  String phoneSetupErrorStart(String reason) {
    return 'Could not start OpenCode: $reason';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'OpenCode is installed, but the app could not start it here.';

  @override
  String get builtinServerLogTitle => 'Server log';

  @override
  String get phoneSetupReadyTitle => 'OpenCode is ready';

  @override
  String get phoneSetupReadyNameTitle => 'Name your first project';

  @override
  String get phoneSetupReadyNameLabel => 'Project name';

  @override
  String get phoneSetupReadyNameHelp => 'Letters, numbers, - _ .';

  @override
  String get phoneSetupReadyCreating => 'Creating the project';

  @override
  String get phoneSetupReadyCreateOpen => 'Create and open';

  @override
  String get phoneSetupReadyOpenFolderInstead => 'Open a folder instead';

  @override
  String get phoneSetupReadyNameEmpty => 'Enter a name.';

  @override
  String get phoneSetupReadyNameOneFolder => 'Use one name, without slashes.';

  @override
  String get phoneSetupReadyNameInvalid =>
      'Use letters, numbers, - _ or . and start with a letter or number (up to 64).';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return 'The project could not be created: $reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return 'The project could not be opened: $reason';
  }

  @override
  String get phoneServerCardTitle => 'This phone';

  @override
  String get phoneServerCardRunning => 'Running';

  @override
  String get phoneServerCardStopped => 'Stopped';

  @override
  String get phoneServerCardStarting => 'Starting';

  @override
  String get phoneServerCardStopping => 'Stopping';

  @override
  String get phoneServerCardRemoving => 'Removing';

  @override
  String get phoneServerCardChecking => 'Checking';

  @override
  String get phoneServerCardNotSetUp => 'Not set up';

  @override
  String get phoneServerCardSettingUp => 'Setting up';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => 'Set up';

  @override
  String get phoneServerCardShowProgress => 'Show progress';

  @override
  String get phoneServerCardContinueSetup => 'Continue setup';

  @override
  String get phoneServerCardLogTitle => 'Log';

  @override
  String get phoneServerCardLogEmpty => 'Nothing in the log yet.';

  @override
  String get phoneServerCardMore => 'More';

  @override
  String phoneServerCardSwitchTo(String runtime) {
    return 'Switch to $runtime';
  }

  @override
  String get phoneServerCardAddTools => 'Add tools (Python, AI Team…)';

  @override
  String get phoneServerCardUpdate => 'Update OpenCode';

  @override
  String get phoneServerCardRemove => 'Remove from this phone…';

  @override
  String get phoneServerCardRemoveTitle => 'Remove OpenCode from this phone?';

  @override
  String phoneServerCardActionFailed(String reason) {
    return 'That did not work: $reason';
  }

  @override
  String get serverEditorMoreOptions => 'More options';

  @override
  String get inAppServerStoppedTitle => 'OpenCode inside the app is stopped';

  @override
  String get inAppServerStoppedBody =>
      'It stops when the app is closed for a while or updated. Your conversations are kept; start it again to continue.';

  @override
  String get inAppServerNotRespondingTitle =>
      'OpenCode inside the app is not answering';

  @override
  String get inAppServerNotRespondingBody =>
      'Starting it again usually fixes this. Your conversations are kept.';

  @override
  String get inAppServerStartFailedTitle =>
      'OpenCode inside the app did not start';

  @override
  String get inAppServerStartFailedBody =>
      'Open its setup to see the server log, or try starting it again.';

  @override
  String get inAppServerStarting => 'Starting OpenCode inside the app…';

  @override
  String get inAppServerStartingBody => 'This takes a few seconds.';

  @override
  String get inAppServerOpenSetup => 'Open setup';

  @override
  String get projectFolderInAppTitle => 'Open a project';

  @override
  String get projectFolderNewProject => 'New project';

  @override
  String get projectFolderProjectNameLabel => 'Project name';

  @override
  String projectFolderNewProjectHelp(String directory) {
    return 'The app makes the folder in $directory and opens it.';
  }

  @override
  String get projectFolderEnterPath => 'Enter a path';

  @override
  String get projectFolderMissing => 'That folder does not exist yet.';

  @override
  String get projectFolderCreateIt => 'Create it';

  @override
  String projectFolderCreateFailed(String reason) {
    return 'The folder could not be created: $reason';
  }

  @override
  String projectFolderCheckFailed(String reason) {
    return 'The folder could not be checked: $reason';
  }

  @override
  String get folderBrowserUp => 'Up one folder';

  @override
  String folderBrowserCurrent(String path) {
    return 'Current folder: $path';
  }

  @override
  String folderBrowserOpen(String name) {
    return 'Open $name';
  }

  @override
  String get folderBrowserGit => 'Git repository';

  @override
  String get folderBrowserProject => 'OpenCode project';

  @override
  String folderBrowserShowInside(String name) {
    return 'Show the folders in $name';
  }

  @override
  String get folderBrowserProjectsHere =>
      'Your projects live here. Tap one to open it, or make a new one below.';

  @override
  String get folderBrowserHomeHere =>
      'The home folder and the root cannot be projects. Open a folder inside.';

  @override
  String get folderBrowserNoProjectsTitle => 'No projects yet';

  @override
  String get folderBrowserNoProjectsBody => 'Name one below to make it here.';

  @override
  String get folderBrowserEmptyTitle => 'No folders in here';

  @override
  String get folderBrowserEmptyBody =>
      'Make a new project in it below, or go up one folder.';

  @override
  String get folderBrowserErrorTitle => 'This folder can’t be shown';

  @override
  String get folderBrowserErrorNotInstalled =>
      'Ubuntu isn’t installed in the app yet.';

  @override
  String get folderBrowserErrorMissing => 'It isn’t there any more.';

  @override
  String get folderBrowserErrorDenied => 'The app isn’t allowed to read it.';

  @override
  String get folderBrowserErrorLinked =>
      'It is a link. Enter its path instead.';

  @override
  String get folderBrowserErrorFailed =>
      'Try again, or enter its path instead.';

  @override
  String get folderBrowserErrorTimedOut =>
      'It took too long to answer. Try again.';

  @override
  String get folderBrowserRetry => 'Try again';

  @override
  String get phoneSetupStartScreenTitle => 'On this phone';

  @override
  String get phoneSetupStartHeadline => 'Run a coding agent right here';

  @override
  String phoneSetupStartPromise(String time, String size) {
    return 'No computer and no other apps. $time and ~$size the first time.';
  }

  @override
  String phoneSetupStartPromiseNoSize(String time) {
    return 'No computer and no other apps. $time the first time.';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'About $minutes minutes',
      one: 'About a minute',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartMegabytes(String value) {
    return '$value MB';
  }

  @override
  String phoneSetupStartGigabytes(String value) {
    return '$value GB';
  }

  @override
  String get phoneSetupStartSetUp => 'Set up OpenCode on this phone';

  @override
  String phoneSetupStartIncludes(String tools) {
    return 'Includes $tools.';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first and $last';
  }

  @override
  String get phoneSetupStartListSeparator => ', ';

  @override
  String get phoneSetupStartCustomize => 'Choose what to install';

  @override
  String get phoneSetupStartOtherWays => 'Other ways';

  @override
  String get phoneSetupStartUseTermux => 'Use Termux instead';

  @override
  String get phoneSetupStartByAddress => 'Connect to a computer by address';

  @override
  String get phoneSetupStartSetUpHere => 'Set it up in this app instead';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return 'Setup is $percent% done';
  }

  @override
  String get phoneSetupStartRunningBody =>
      'It keeps going while you use other apps.';

  @override
  String get phoneSetupStartStoppedBody =>
      'It stopped before finishing. Continuing picks up where it left off.';

  @override
  String get phoneSetupStartContinue => 'Continue setup';

  @override
  String get phoneSetupStartReadyHeadline => 'OpenCode is ready on this phone';

  @override
  String get phoneSetupStartReadyBody => 'Open it to start a conversation.';

  @override
  String get phoneSetupStartOpen => 'Open';

  @override
  String get phoneSetupStartTermuxHeadline =>
      'OpenCode is already set up in Termux';

  @override
  String get phoneSetupStartTermuxBody =>
      'You set it up with Termux before. Connect to keep using it.';

  @override
  String get phoneSetupStartConnect => 'Connect';

  @override
  String phoneSetupStartFailed(String reason) {
    return 'That didn\'t work: $reason';
  }

  @override
  String get phoneSetupStartEntryDetail =>
      'Run a coding agent right here. No computer needed.';

  @override
  String get phoneSetupStartCustomizeTitle => 'Choose what to install';

  @override
  String get phoneSetupStartAddTitle => 'Add tools';

  @override
  String get phoneSetupStartInstalled => 'Installed';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => 'Nothing chosen yet';

  @override
  String get phoneSetupStartDone => 'Done';

  @override
  String get phoneSetupStartAdd => 'Add';

  @override
  String get phoneSetupStartChecking => 'Checking what\'s installed…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline =>
      'This phone can\'t run it';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return 'This app\'s Ubuntu only runs on a 64-bit Arm or Intel phone; this one reports $abi.';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline =>
      'This phone doesn\'t have enough memory';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'OpenCode needs a phone with at least $minimumString MB of memory; this one has $actualString MB. Run it on a computer instead and connect this phone to it.';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return 'It may be slow on this phone, which has $memoryString MB of memory.';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline => 'Not enough free space';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return 'Free about $size on this phone, then come back to set this up.';
  }

  @override
  String get phoneSetupPreflightOpenStorage => 'Open Storage settings';

  @override
  String phoneSetupOpenWelcomeRunning(int percent) {
    return 'Setting up OpenCode on this phone · $percent%';
  }

  @override
  String phoneSetupOpenWelcomeStopped(int percent) {
    return 'Setup on this phone is $percent% done';
  }

  @override
  String get phoneSetupOpenWelcomeStoppedDetail =>
      'Continuing picks up where it left off.';

  @override
  String get phoneSetupOpenWelcomeShowProgress => 'Show progress';

  @override
  String get phoneSetupOpenWelcomeContinue => 'Continue';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => 'Build a small web page';

  @override
  String get chatStartPythonScript => 'Write a Python script that…';

  @override
  String get chatStartNodeProject => 'Start a Node.js project';

  @override
  String get chatStartReadme => 'Set up a README';

  @override
  String get chatStartExplainProject => 'Explain this project';

  @override
  String get chatStartWhatChanged => 'What changed recently?';

  @override
  String get chatStartFindBug => 'Find and fix a bug';

  @override
  String get chatStartAddTests => 'Add tests';

  @override
  String get chatStartListFolder => 'List what\'s in this folder';

  @override
  String get chatStartEmptyFolder => 'Empty folder';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items',
      one: '1 item',
    );
    return '$_temp0';
  }

  @override
  String get chatStartGit => 'Git';

  @override
  String chatStartChangeCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changes',
      one: '1 change',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => 'Looking at the folder…';

  @override
  String get chatStartServerFolder => 'Server folder';

  @override
  String get chatStartTip =>
      'Type / for commands · long-press a message for its actions';

  @override
  String get chatLoadingConversation => 'Loading the conversation';

  @override
  String get chatLoadFailedTitle => 'Couldn\'t open this conversation';

  @override
  String get chatLoadFailedBody =>
      'Nothing is lost. Try again when OpenCode answers.';

  @override
  String get chatSendFailed => 'Your message wasn\'t sent';

  @override
  String get chatSendFailedKept => 'It\'s back in the message box.';

  @override
  String get chatStartSuggestionsLabel => 'Ways to start';

  @override
  String get perfTraceTitle => 'Performance';

  @override
  String get perfTraceBody =>
      'How long each step took while the app has been open: connecting, loading, every request to the server. Kept in memory only and cleared when the app closes. The report holds names and timings, never messages or passwords.';

  @override
  String get perfTraceCopy => 'Copy timing report';

  @override
  String get perfTraceCopied => 'Performance report copied';

  @override
  String get perfTraceEmpty => 'Nothing measured yet.';

  @override
  String get perfTraceSlowest => 'Slowest steps';

  @override
  String get perfTraceRecent => 'Latest steps';

  @override
  String perfTraceStatLine(int count, String p50, String p95, String max) {
    return '$count× · typical $p50 · slow $p95 · longest $max';
  }

  @override
  String perfTraceFailed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count failed',
      one: '1 failed',
    );
    return '$_temp0';
  }

  @override
  String perfTraceAt(String time) {
    return 'at $time';
  }

  @override
  String perfTraceWithin(String parent) {
    return 'in $parent';
  }

  @override
  String get aiteamComponentTitle => 'AI Team';

  @override
  String aiteamComponentStageDownloading(String index, String total) {
    return 'Downloading AI Team · $index of $total';
  }

  @override
  String get aiteamComponentStagePreparing => 'Getting AI Team ready';

  @override
  String aiteamComponentAddingTitle(String names) {
    return 'Adding $names';
  }

  @override
  String get aiteamComponentNotice =>
      'OpenCode and AI Team are running on this phone';

  @override
  String get aiteamComponentSectionTitle => 'AI Team on this phone';

  @override
  String aiteamComponentOfferBody(String size) {
    return 'Several agents share the work on one project, right here. About $size to download.';
  }

  @override
  String get aiteamComponentAdd => 'Add AI Team';

  @override
  String aiteamComponentTurnOn(String project) {
    return 'Turn on AI Team for $project';
  }

  @override
  String get aiteamComponentTurnOnBody =>
      'The team works on its own branches and keeps a copy of the project\'s history on this phone.';

  @override
  String get aiteamComponentNoProject =>
      'Open a project first, then turn AI Team on for it.';

  @override
  String get aiteamComponentStageTeam => 'Getting the team ready';

  @override
  String aiteamComponentStageProject(String project) {
    return 'Adding $project';
  }

  @override
  String get aiteamComponentStageStarting => 'Starting AI Team';

  @override
  String get aiteamComponentStageWaiting => 'Waiting for AI Team to answer';

  @override
  String get aiteamComponentTurnOnExpectation =>
      'This takes about 5 to 10 minutes the first time. You can leave this screen; it keeps going.';

  @override
  String get aiteamComponentStartExpectation =>
      'This takes a few minutes. You can leave this screen; it keeps going.';

  @override
  String aiteamComponentStageSoFar(String time) {
    return '$time so far';
  }

  @override
  String aiteamComponentStageTook(String time) {
    return 'Took $time';
  }

  @override
  String get aiteamComponentRunning => 'AI Team · Running';

  @override
  String get aiteamComponentStopped => 'AI Team · Stopped';

  @override
  String aiteamComponentProjects(String projects) {
    return 'Works on $projects';
  }

  @override
  String get aiteamComponentStart => 'Start AI Team';

  @override
  String aiteamComponentFailed(String reason) {
    return 'AI Team could not start: $reason';
  }

  @override
  String get aiteamComponentFailedExited => 'it stopped on its own';

  @override
  String get aiteamComponentFailedTimeout => 'it did not answer in time';

  @override
  String get aiteamComponentShowDetails => 'Show details';

  @override
  String get aiteamComponentChildProcesses =>
      'Android stops an app\'s extra programs when it runs many at once, and a team runs several. If the team stops while it works, turn on Developer options › Disable child process restrictions.';

  @override
  String get workUnreviewed => 'Unreviewed';

  @override
  String get workMarkReviewed => 'Mark as reviewed';

  @override
  String get workMarkReviewedFailed =>
      'Couldn\'t mark it as reviewed. Try again.';

  @override
  String get workOtherProjects => 'Other projects';

  @override
  String get workAllProjects => 'All projects';

  @override
  String workOpenLiveConversation(String title) {
    return 'Open “$title”';
  }

  @override
  String workRunningCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Running · $count',
      one: 'Running',
    );
    return '$_temp0';
  }

  @override
  String get workLoadingLabel => 'Loading';

  @override
  String get workServerNotAnsweringPhone =>
      'OpenCode on this phone isn\'t answering';

  @override
  String workServerNotAnswering(String server) {
    return '$server isn\'t answering';
  }

  @override
  String get workServerKeepsTrying => 'The app keeps trying in the background.';

  @override
  String get workServerRestart => 'Restart';

  @override
  String get workServerRestartTitle => 'Restart OpenCode on this phone?';

  @override
  String get workServerRestartBody =>
      'A running agent turn will stop. Your conversations are kept.';

  @override
  String get workStale => 'This may be out of date';

  @override
  String workRunaway(String duration) {
    return 'OpenCode has been busy for $duration with nothing to do';
  }

  @override
  String workRunawayInProject(String project, String duration) {
    return 'OpenCode has been busy in $project for $duration with nothing to do';
  }

  @override
  String get connectStartingPhone => 'Starting OpenCode on this phone…';

  @override
  String get connectStartingBody =>
      'Your conversations are kept. This can take a minute.';

  @override
  String aiteamBringInDone(String project, String commit) {
    return '$project has the team\'s latest work ($commit).';
  }

  @override
  String aiteamBringInDirty(String commit, String project, String files) {
    return 'The team\'s work ($commit) is not in $project yet: $project has changes of its own ($files), so it was left as it is.';
  }

  @override
  String aiteamBringInDiverged(String commit, String project) {
    return 'The team\'s work ($commit) is not in $project: $project has commits of its own. Merge the two with git.';
  }

  @override
  String aiteamBringInFailed(String project, String reason) {
    return 'The team\'s work could not be brought into $project: $reason';
  }

  @override
  String aiteamBringInAction(String project) {
    return 'Bring the team\'s work into $project';
  }

  @override
  String get teamUiHostPhrasePhone => 'On this phone';

  @override
  String teamUiHostPhraseComputerNamed(String name) {
    return 'On $name';
  }

  @override
  String get teamUiHostPhraseComputer => 'On your computer';

  @override
  String get teamUiHostPhrasePaused => 'Paused';

  @override
  String get teamUiHostPhraseNotAnswering => 'Not answering';

  @override
  String teamUiTaskSteps(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done of $total steps done',
      one: '$done of 1 step done',
    );
    return '$_temp0';
  }

  @override
  String teamUiTaskDoneAgo(String when) {
    return 'Done $when';
  }

  @override
  String teamUiTaskMergedAgo(String when) {
    return 'Done · merged $when';
  }

  @override
  String teamUiTaskCancelledAgo(String when) {
    return 'Cancelled $when';
  }

  @override
  String teamUiHomeDoneMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $count more',
      one: 'Show 1 more',
    );
    return '$_temp0';
  }

  @override
  String get teamUiHomeSearchClose => 'Close search';

  @override
  String get teamUiHomeNeedsYouAnswer => 'Answer';

  @override
  String get teamUiHomeNeedsYouFallbackTitle => 'The team has a question';

  @override
  String teamUiHomeNeedsYouAnnouncement(String question) {
    return 'Needs you: $question';
  }

  @override
  String get teamUiAgentRoleWorker => 'Worker';

  @override
  String get teamUiAgentRoleReviewer => 'Reviewer';

  @override
  String get teamUiAgentRolePlanner => 'Planner';

  @override
  String get teamUiAgentRoleSupervisor => 'Supervisor';

  @override
  String get teamUiAgentRoleHelper => 'Helper';

  @override
  String get teamUiAgentRoleOther => 'Agent';

  @override
  String get teamUiRunStageWaiting => 'Planned';

  @override
  String get teamUiRunStageWorking => 'Working';

  @override
  String get teamUiRunStageReviewing => 'In review';

  @override
  String get teamUiRunStageDone => 'Merged';

  @override
  String teamUiRunStageSemantics(int position, String stage) {
    return 'Stage $position of 4: $stage';
  }

  @override
  String get teamUiRunStepsHeading => 'Steps';

  @override
  String get teamUiRunDetailsUsage => 'Usage';

  @override
  String get serverRowConnected => 'Connected';

  @override
  String phoneServerRowStatus(String runtime, String state) {
    return '$runtime · $state';
  }

  @override
  String get phoneServerRowRestarting => 'Restarting';

  @override
  String get phoneServerRowNotAnswering => 'Not answering';

  @override
  String get phoneServerRowNotRunning => 'Not running';

  @override
  String get phoneServerRowDetails => 'Details';

  @override
  String get localAgentRowOptional => 'Optional';

  @override
  String get localAgentPageTitle => 'Claude Code';

  @override
  String get managedRecoveryRowTitle => 'Restart after a crash';

  @override
  String get managedRecoveryRowDetail =>
      'Up to 3 tries, only while this app is open. It never installs or updates.';

  @override
  String get pluginsTeamRowTitle => 'AI Team';

  @override
  String pluginsTeamRowFound(String server) {
    return 'Found on $server';
  }

  @override
  String get pluginsLoading => 'Loading plugins';

  @override
  String get pluginsBuiltinGroup => 'Built in';

  @override
  String pluginsBuiltinActive(int count) {
    return '$count active';
  }

  @override
  String pluginsBuiltinFailed(int count) {
    return '$count failed to load';
  }

  @override
  String get pluginsStatusFailedToLoad => 'Failed to load';

  @override
  String get pluginsDetailsId => 'ID';

  @override
  String get localTerminalSourcePhone => 'This phone';

  @override
  String get localTerminalSourceServer => 'OpenCode server';

  @override
  String localTerminalShellName(int number) {
    return 'Shell $number';
  }

  @override
  String localTerminalShellEnded(String name) {
    return '$name · ended';
  }

  @override
  String get localTerminalNewShell => 'New shell';

  @override
  String get localTerminalStopShell => 'Stop this shell';

  @override
  String get localTerminalCloseShell => 'Close this shell';

  @override
  String get localTerminalStopBody => 'Programs running in it stop too.';

  @override
  String get localTerminalStop => 'Stop';

  @override
  String get localTerminalStarting => 'Starting the shell';

  @override
  String get localTerminalNotSetUpTitle => 'Linux isn\'t set up on this phone';

  @override
  String get localTerminalNotSetUpBody =>
      'The terminal runs in the Linux that phone setup installs.';

  @override
  String get localTerminalEndedTitle => 'The shell ended';

  @override
  String localTerminalEndedBody(int code) {
    return 'It exited with code $code.';
  }

  @override
  String get localTerminalRestart => 'Restart';

  @override
  String get localTerminalFailedTitle => 'The shell didn\'t start';

  @override
  String get localTerminalFailedBody =>
      'Try again. If it keeps failing, Details says why.';

  @override
  String get localTerminalTryAgain => 'Try again';

  @override
  String localTerminalCost(int perShell, int limit) {
    return 'Each shell runs $perShell programs. With AI Team on, Android may stop the app\'s programs past $limit.';
  }

  @override
  String localTerminalCostNow(int perShell, int count, int limit) {
    return 'Each shell runs $perShell programs. With AI Team on, the app runs $count; Android may stop them past $limit.';
  }

  @override
  String get localTerminalKeysLabel => 'Terminal keys';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => 'Page up';

  @override
  String get localTerminalKeyPageDown => 'Page down';

  @override
  String get localTerminalSemantics =>
      'Terminal on this phone. Tap to type; touch and hold to select text.';

  @override
  String get phoneServerTermuxTitle => 'This phone · Termux';

  @override
  String get workNotAnsweringListTitle => 'Your conversations will be back';

  @override
  String get workNotAnsweringListBody =>
      'They show here again as soon as the server answers.';

  @override
  String get globalSessionsLoadFailedTitle => 'Couldn\'t load conversations';

  @override
  String get filesLoadFailedTitle => 'Couldn\'t open this folder';

  @override
  String get filesSymbolsFailedTitle => 'Couldn\'t search symbols';

  @override
  String get terminalListFailedTitle => 'Couldn\'t list the terminals';

  @override
  String get addServerConnectTo => 'Connect to';

  @override
  String get addServerTypeOpenCode => 'OpenCode on a computer';

  @override
  String get addServerTypeOpenCodeDetail =>
      'Pair with a code, or enter its address';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail =>
      'The Codex app-server on your computer';

  @override
  String get addServerTypePaseo => 'Claude Code or Pi';

  @override
  String get addServerTypePaseoDetail => 'Through Paseo on your computer';

  @override
  String get addServerScan => 'Scan code';

  @override
  String get addServerPaste => 'Paste code';

  @override
  String get addServerManual => 'Enter the address instead';

  @override
  String get addServerChecking => 'Checking the connection…';

  @override
  String addServerCheckingHost(String host) {
    return 'Checking $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return 'Connecting to $host…';
  }

  @override
  String get addServerSaveAnyway => 'Save anyway';

  @override
  String get appearanceDisplaySection => 'Display';

  @override
  String get effectsSection => 'Effects';

  @override
  String get effectsAnimations => 'Motion';

  @override
  String get effectsMotionFull => 'Full';

  @override
  String get effectsMotionFullHint =>
      'Drawings move, waiting screens breathe and finished moments celebrate';

  @override
  String get effectsMotionCalm => 'Calm';

  @override
  String get effectsMotionCalmHint =>
      'Drawings appear, nothing keeps moving and nothing celebrates';

  @override
  String get effectsMotionOff => 'Off';

  @override
  String get effectsMotionOffHint => 'Everything shows at once';

  @override
  String get effectsMotionSystemOff =>
      'Your phone’s Remove animations is on, so nothing moves whatever you choose here';

  @override
  String get effectsSaveFailed =>
      'Could not save this choice on this device. Try again.';

  @override
  String get teamDiscoverEntryTitle => 'Give a bigger job to a team';

  @override
  String get teamDiscoverIntroBody =>
      'Describe what you want done. A team of agents splits it into steps, works on them side by side and brings the finished work into your project.';

  @override
  String get teamDiscoverHowHeading => 'How it works';

  @override
  String get teamDiscoverStepPlanTitle => 'It plans';

  @override
  String get teamDiscoverStepPlanBody =>
      'A planner splits your job into steps.';

  @override
  String get teamDiscoverStepWorkTitle => 'It works';

  @override
  String get teamDiscoverStepWorkBody =>
      'Workers take the steps, each on its own copy of the project.';

  @override
  String get teamDiscoverStepCheckTitle => 'It checks';

  @override
  String get teamDiscoverStepCheckBody =>
      'A reviewer looks over each step\'s work.';

  @override
  String get teamDiscoverStepMergeTitle => 'It merges';

  @override
  String get teamDiscoverStepMergeBody =>
      'Finished work lands in your project. When it needs a decision, it asks you.';

  @override
  String get teamDiscoverNeedsPhone => 'What it needs on this phone';

  @override
  String teamDiscoverNeedsServer(String server) {
    return 'What it needs on $server';
  }

  @override
  String teamDiscoverDownloadTitle(String size) {
    return 'About $size to download';
  }

  @override
  String get teamDiscoverInAppDownloadBody =>
      'Installed once, next to OpenCode on this phone.';

  @override
  String get teamDiscoverTermuxDownloadBody =>
      'Installed into Termux, next to OpenCode.';

  @override
  String get teamDiscoverBatteryTitle => 'More battery while it works';

  @override
  String get teamDiscoverInAppBatteryBody =>
      'Several agents run at once, and Android may stop some if it runs too many.';

  @override
  String get teamDiscoverTermuxBatteryBody =>
      'Keep Termux open while it works; Android may stop it in the background. Nothing is lost.';

  @override
  String get teamDiscoverProjectTitle => 'You choose the projects';

  @override
  String get teamDiscoverProjectBody =>
      'Turn it on for each project you want it to work on.';

  @override
  String teamDiscoverComputerTitle(String server) {
    return 'Runs on $server';
  }

  @override
  String get teamDiscoverComputerBody =>
      'Install Gas City there once; the app finds it by itself.';

  @override
  String get teamDiscoverSpeedTitle => 'As fast as your computer';

  @override
  String get teamDiscoverSpeedBody => 'Keep it awake while the team works.';

  @override
  String teamDiscoverLooking(String server) {
    return 'Looking for it on $server…';
  }

  @override
  String get teamDiscoverFoundBody => 'It is ready to turn on.';

  @override
  String get teamDiscoverEnterAddress => 'Enter its address';

  @override
  String get teamDiscoverOnComputer => 'Run it on a computer';

  @override
  String get teamDiscoverTurningOn => 'Turning on…';

  @override
  String get teamDiscoverComputerChoiceTitle => 'A team on a computer';

  @override
  String get teamDiscoverComputerChoiceBody =>
      'Use Gas City on a computer instead';

  @override
  String get teamNowChecksEveryMinute => 'the team checks every minute';

  @override
  String teamNowChecksEvery(String minutes) {
    return 'the team checks every $minutes min';
  }

  @override
  String get teamNowNextCheck => 'a worker starts at the team\'s next check';

  @override
  String get teamNowNoWorkerStarted => 'no worker has started';

  @override
  String get teamNowPausedLine =>
      'The team is paused · nothing starts until you resume it';

  @override
  String get teamNowStartWorker => 'Start a worker';

  @override
  String get teamNowWhy => 'Why?';

  @override
  String get teamAgentDidNotStartTitle => 'The worker didn\'t start';

  @override
  String get teamAgentDidNotStartBody =>
      'A task is waiting, but this worker isn\'t running.';

  @override
  String get teamAgentStartIt => 'Start it';

  @override
  String teamOutputNotRunning(String name) {
    return '$name isn\'t running, so there is no output';
  }

  @override
  String get teamOutputSilent =>
      'No output yet · it can take a minute to start';

  @override
  String teamAgentTitle(String role, String name) {
    return '$role · $name';
  }

  @override
  String teamAgentWorksOn(String title) {
    return 'On “$title”';
  }

  @override
  String teamOutputStartingPhone(String age) {
    return 'Starting up · $age so far · this can take a few minutes on a phone';
  }

  @override
  String get teamNewModeSolo => 'Solo';

  @override
  String get teamNewModeTeam => 'Team';

  @override
  String get teamNewTask => 'New team task';

  @override
  String get teamTaskMark => 'Team';

  @override
  String get chatWatchEmptyTitle => 'Nothing here yet';

  @override
  String get chatWatchEmptyBody =>
      'This conversation fills in as the agent works.';

  @override
  String teamWatchBanner(String name, String role, String state) {
    return 'Watching $name · $role · $state';
  }

  @override
  String teamWatchBannerRole(String role, String state) {
    return 'Watching $role · $state';
  }

  @override
  String get teamWatchFallbackUnreadable =>
      'Its conversation can\'t be read from the server this app is connected to, so this is the team\'s live output.';

  @override
  String get teamWatchFallbackNotFound =>
      'Its conversation isn\'t on the server this app is connected to yet (it may still be starting, or the team runs on another computer), so this is the team\'s live output.';

  @override
  String get teamOpenConversation => 'Open conversation';

  @override
  String teamOpenConversationHint(String name) {
    return 'Watch $name\'s work in the chat';
  }

  @override
  String get teamOpenConversationFinding => 'Finding its conversation…';

  @override
  String get teamChatUntitled => 'Team task';

  @override
  String teamChatSubtitle(String host) {
    return 'AI Team · $host';
  }

  @override
  String get teamChatOpenTeam => 'AI Team';

  @override
  String get teamChatTaskDetails => 'Task details';

  @override
  String get teamChatStopTask => 'Stop task';

  @override
  String get teamChatStopConfirmTitle => 'Stop this task?';

  @override
  String teamChatStopConfirmBody(String task) {
    return '“$task” stops on the team\'s computer. Its workers still running stop now; work already finished stays. This cannot be undone from the phone.';
  }

  @override
  String get teamChatStopKeepRunning => 'Keep running';

  @override
  String get teamChatLoading => 'Loading the task';

  @override
  String get teamChatLeadName => 'The team';

  @override
  String get teamChatLeadSent => 'Sent to the team';

  @override
  String get teamChatLeadNothingYet =>
      'Nothing yet. The team has not planned this task.';

  @override
  String teamChatLeadPlanned(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Planned $count steps',
      one: 'Planned 1 step',
    );
    return '$_temp0';
  }

  @override
  String teamChatLeadRouted(String title) {
    return 'Sent “$title” to the workers';
  }

  @override
  String teamChatLeadStarting(String title) {
    return 'Worker started on “$title”';
  }

  @override
  String teamChatLeadClaimedWorker(String title) {
    return 'Worker took “$title”';
  }

  @override
  String teamChatLeadPushed(String title) {
    return 'Changes for “$title” are on a branch';
  }

  @override
  String teamChatLeadReview(String title) {
    return 'Handed “$title” to review';
  }

  @override
  String teamChatLeadMerged(String title) {
    return 'Merged “$title”';
  }

  @override
  String teamChatLeadStepFailed(String title) {
    return '“$title” failed';
  }

  @override
  String teamChatLeadStepCancelled(String title) {
    return '“$title” was cancelled';
  }

  @override
  String teamChatLeadNeedsYou(String question) {
    return 'Needs you: $question';
  }

  @override
  String get teamChatLeadTaskMerged => 'Merged. The task is done.';

  @override
  String get teamChatLeadTaskFinished => 'The task is done.';

  @override
  String get teamChatLeadTaskFailed => 'The task failed.';

  @override
  String get teamChatLeadTaskCancelled => 'The task was cancelled.';

  @override
  String get teamChatAWorker => 'A worker';

  @override
  String teamChatStepsSummary(int count, int done) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0 · $done done';
  }

  @override
  String get teamChatComposerHint => 'Message the team…';

  @override
  String teamChatComposerGoesTo(String name) {
    return 'Your message goes to $name';
  }

  @override
  String get teamChatComposerNobody =>
      'No agent is on this task to message yet.';

  @override
  String get teamChatComposerCannot =>
      'This team can\'t be messaged from here.';

  @override
  String get teamBoardTitle => 'Board';

  @override
  String get teamBoardOpenTooltip => 'Board';

  @override
  String get teamBoardColumnBacklog => 'Backlog';

  @override
  String get teamBoardColumnReady => 'Ready';

  @override
  String get teamBoardColumnWorking => 'Working';

  @override
  String get teamBoardColumnReview => 'Review';

  @override
  String get teamBoardColumnDone => 'Done';

  @override
  String get teamBoardEmptyBacklogTitle => 'Nothing waiting';

  @override
  String get teamBoardEmptyBacklogBody =>
      'Tasks you add but haven\'t started wait here.';

  @override
  String get teamBoardEmptyReadyTitle => 'Nothing queued';

  @override
  String get teamBoardEmptyReadyBody =>
      'Tasks given to the team wait here for a worker.';

  @override
  String get teamBoardEmptyWorkingTitle => 'Nobody is working';

  @override
  String get teamBoardEmptyWorkingBody =>
      'A task moves here when a worker picks it up.';

  @override
  String get teamBoardEmptyReviewTitle => 'Nothing to review';

  @override
  String get teamBoardEmptyReviewBody =>
      'Finished work waits here for its check and merge.';

  @override
  String get teamBoardEmptyDoneTitle => 'Nothing finished this week';

  @override
  String get teamBoardEmptyDoneBody =>
      'Merged, done and cancelled tasks from the last 7 days show here.';

  @override
  String get teamBoardEmptyTitle => 'No tasks yet';

  @override
  String get teamBoardEmptyBody =>
      'Tasks you give the team show up here, by where they stand.';

  @override
  String get teamBoardPriorityUrgent => 'Urgent';

  @override
  String get teamBoardPriorityHigh => 'High';

  @override
  String get teamBoardPriorityNormal => 'Normal';

  @override
  String get teamBoardPriorityLow => 'Low';

  @override
  String get teamBoardPrioritySomeday => 'Someday';

  @override
  String get teamBoardTypeBug => 'Bug';

  @override
  String get teamBoardTypeFeature => 'Feature';

  @override
  String get teamBoardTypeEpic => 'Epic';

  @override
  String get teamBoardTypeChore => 'Chore';

  @override
  String teamBoardEpicProgress(int done, int total) {
    return '$done of $total done';
  }

  @override
  String get teamBoardFlagNeedsYou => 'Needs you';

  @override
  String get teamBoardFlagBlocked => 'Blocked';

  @override
  String teamBoardFlagBlockedBy(String title) {
    return 'Blocked by $title';
  }

  @override
  String teamBoardFlagBlockedByMore(String title, int count) {
    return 'Blocked by $title + $count more';
  }

  @override
  String get teamBoardFlagFailed => 'Stopped with an error';

  @override
  String teamBoardFlagInEpic(String epic) {
    return 'In $epic';
  }

  @override
  String teamBoardFlagMoving(String column) {
    return 'Moving to $column…';
  }

  @override
  String get teamBoardFlagCancelled => 'Cancelled';

  @override
  String get teamBoardMoveMenuTooltip => 'Move or change';

  @override
  String teamBoardMoveSheetWhere(String column) {
    return 'In $column';
  }

  @override
  String get teamBoardMoveStartNow => 'Start now';

  @override
  String get teamBoardMoveStartNowHint => 'Give it to the team\'s workers';

  @override
  String get teamBoardMoveBackToBacklog => 'Move back to Backlog';

  @override
  String get teamBoardMoveBackToBacklogHint =>
      'The team won\'t pick it up until you start it';

  @override
  String get teamBoardMovePriority => 'Priority';

  @override
  String get teamBoardMoveCancel => 'Cancel task';

  @override
  String get teamBoardMoveCancelHint => 'Moves it to Done as cancelled';

  @override
  String get teamBoardMoveReopen => 'Put back in Backlog';

  @override
  String get teamBoardMoveReopenHint => 'Nobody works on it until you start it';

  @override
  String get teamBoardOpenConversation => 'Open conversation';

  @override
  String get teamBoardOpenDetails => 'Open details';

  @override
  String get teamBoardTeamMoves =>
      'The team moves this task. Open its conversation to message the team or stop it.';

  @override
  String get teamBoardReadOnlyNote =>
      'This host doesn\'t let the app change tasks, so the board is read-only here.';

  @override
  String get teamBoardStatusReadOnly =>
      'Read-only here · this host doesn\'t let the app change tasks';

  @override
  String teamBoardCancelTitle(String title) {
    return 'Cancel “$title”?';
  }

  @override
  String get teamBoardCancelBody =>
      'The team won\'t work on it. It moves to Done as cancelled, and you can put it back in the Backlog later.';

  @override
  String get teamBoardCancelKeep => 'Keep it';

  @override
  String teamBoardMoveFailedTitle(String title) {
    return 'Couldn\'t change “$title”';
  }

  @override
  String get teamBoardMoveFailedBody =>
      'The team\'s host said no, so it stays where it was.';

  @override
  String get teamBoardPriorityTitle => 'Priority';

  @override
  String get teamBoardAddFailed =>
      'Couldn\'t add it. The team\'s host said no.';

  @override
  String get teamBoardProjectTooltip => 'Choose project';

  @override
  String appExitForceStopped(String time) {
    return 'OpenCode Mobile was closed $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'Android closed OpenCode Mobile $time to free memory';
  }

  @override
  String appExitCrashed(String time) {
    return 'OpenCode Mobile stopped unexpectedly $time';
  }

  @override
  String appExitKilled(String time) {
    return 'Android stopped OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what. Your phone\'s OpenCode stopped with it; it\'s starting again.';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what. Your phone\'s OpenCode and the AI Team stopped with it; they\'re starting again.';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what. Your phone\'s OpenCode stopped with it. Start it again when you\'re ready.';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what. Your phone\'s OpenCode and the AI Team stopped with it. Start them again when you\'re ready.';
  }

  @override
  String appExitServerBack(String what) {
    return '$what. Your phone\'s OpenCode stopped with it and is running again.';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what. Your phone\'s OpenCode and the AI Team stopped with it; OpenCode is running again.';
  }

  @override
  String appExitAtTime(String time) {
    return 'at $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return 'on $day at $time';
  }

  @override
  String get appExitKeepRunning => 'Keep it running';

  @override
  String get keepRunningTitle => 'Keep running in the background';

  @override
  String get keepRunningRowSubtitle =>
      'What to allow so your phone doesn\'t close the app';

  @override
  String keepRunningIntro(String maker) {
    return 'Your phone\'s OpenCode and the AI Team run inside this app, so they stop when Android closes it. On this $maker, allow these:';
  }

  @override
  String get keepRunningSwipeWarning =>
      'This phone closes an app you swipe away from Recent apps, even while it works. Lock it there instead of swiping it away.';

  @override
  String get keepRunningBatteryTitle => 'Don\'t optimize battery';

  @override
  String get keepRunningBatteryDetail =>
      'Lets the app keep running while you use other apps.';

  @override
  String get keepRunningBatteryDone => 'Allowed';

  @override
  String get keepRunningLockTitle => 'Lock it in Recent apps';

  @override
  String get keepRunningLockNubia =>
      'Open Recent apps and pull OpenCode Mobile\'s card down until the lock shows.';

  @override
  String get keepRunningLockSamsung =>
      'Open Recent apps, tap OpenCode Mobile\'s icon above its card and choose Keep open.';

  @override
  String get keepRunningLockOther =>
      'Open Recent apps, long-press OpenCode Mobile\'s card and tap the lock.';

  @override
  String get keepRunningAutostartTitle => 'Allow auto-start';

  @override
  String get keepRunningAutostartDetail =>
      'Turn it on for OpenCode Mobile so the phone doesn\'t stop it in the background.';

  @override
  String get keepRunningAutostartHuawei =>
      'Under App launch, set OpenCode Mobile to Manage manually and turn on all three switches.';

  @override
  String get keepRunningBackgroundTitle => 'Allow background activity';

  @override
  String get keepRunningBackgroundXiaomi =>
      'In App info › Battery saver, choose No restrictions.';

  @override
  String get keepRunningBackgroundOppo =>
      'In App info › Battery usage, allow background activity.';

  @override
  String get keepRunningBackgroundVivo =>
      'In App info › Battery, allow high background power use.';

  @override
  String get keepRunningBackgroundSamsung =>
      'In App info › Battery, choose Unrestricted, and keep the app out of Sleeping apps.';

  @override
  String get keepRunningBackgroundOther =>
      'In App info › Battery, choose Unrestricted or allow background running.';

  @override
  String get keepRunningOpen => 'Open';

  @override
  String get keepRunningOpenFailed =>
      'This phone has no such screen. Open Settings › Apps › OpenCode Mobile instead.';

  @override
  String get keepRunningFootnote =>
      'If Android still closes the app, it starts your phone\'s OpenCode again the next time you open it.';

  @override
  String get keepRunningThisPhone => 'phone';

  @override
  String teamAgentLastStep(String step) {
    return 'Last step: $step';
  }

  @override
  String teamAgentLastActive(String elapsed) {
    return 'active $elapsed ago';
  }

  @override
  String teamChatLeadEarlier(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier updates',
      one: '1 earlier update',
    );
    return '$_temp0';
  }

  @override
  String get thermalPausedNotice =>
      'Your phone is hot — paused the AI Team to cool down. It resumes by itself.';

  @override
  String get thermalStoppedNotice =>
      'Your phone is very hot — stopped the AI Team to protect it. Its work is kept, and it starts again once the phone has cooled down.';

  @override
  String get thermalResumedNotice =>
      'Resumed the AI Team — your phone has cooled down.';

  @override
  String get thermalGuardSetting => 'Pause the AI Team when the phone is hot';

  @override
  String get thermalGuardSettingDetail =>
      'The team pauses with its work kept and resumes by itself once the phone cools.';

  @override
  String get kitSheetClose => 'Close';

  @override
  String get kitSheetDismiss => 'Dismiss';

  @override
  String get kitSheetLoading => 'Loading';

  @override
  String get kitConfirmCancel => 'Cancel';

  @override
  String get kitConfirmKeepRunning => 'Keep running';

  @override
  String get kitConfirmKeepEditing => 'Keep editing';

  @override
  String kitConfirmTypeName(String name) {
    return 'Type $name to confirm';
  }

  @override
  String get kitConfirmTypeNameReason =>
      'Type the name exactly as shown to turn this on.';

  @override
  String get kitConfirmFailed => 'That didn\'t finish. You can try again.';

  @override
  String get kitTryAgain => 'Try again';

  @override
  String get kitDetails => 'Details';

  @override
  String get kitDiscardTitle => 'Discard your changes?';

  @override
  String get kitDiscardBody =>
      'What you changed here isn\'t saved. Discarding it can\'t be undone.';

  @override
  String get kitDiscardConfirm => 'Discard changes';

  @override
  String get kitCopied => 'Copied';

  @override
  String get kitMore => 'More';

  @override
  String kitChipRemove(String label) {
    return 'Remove $label';
  }

  @override
  String get kitCopy => 'Copy';

  @override
  String get kitWorking => 'Working';

  @override
  String get kitImageUnavailable => 'Can\'t show this image';

  @override
  String get kitZoomIn => 'Zoom in';

  @override
  String get kitZoomOut => 'Zoom out';

  @override
  String get kitZoomReset => 'Reset zoom';

  @override
  String get kitZoomFit => 'Fit to screen';

  @override
  String get kitZoomAtStart => 'Already at full view';

  @override
  String get kitZoomAtMax => 'Largest zoom';

  @override
  String kitZoomLevel(String percent) {
    return '$percent %';
  }

  @override
  String kitZoomShortcut(String action, String key) {
    return '$action · Ctrl+$key';
  }

  @override
  String get kitMenu => 'Menu';

  @override
  String get kitCopyDetails => 'Copy details';

  @override
  String get kitReportProblem => 'Report a problem';

  @override
  String kitProgressStep(int step, int of) {
    final intl.NumberFormat stepNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String stepString = stepNumberFormat.format(step);
    final intl.NumberFormat ofNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String ofString = ofNumberFormat.format(of);

    return 'Step $stepString of $ofString';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: 'about $secondsString s left',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaMinutes(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'about $minutesString min left',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'about $hoursString h left',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong =>
      'This is too long for a QR code. Copy the link instead.';

  @override
  String kitSinceStillWaiting(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    return 'Still waiting after $secondsString s';
  }

  @override
  String kitSinceWaitingFor(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Waiting $minutesString min',
      one: 'Waiting 1 min',
      zero: 'Waiting less than a minute',
    );
    return '$_temp0';
  }

  @override
  String kitSinceAge(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutesString min',
      one: '1 min',
      zero: 'less than a minute',
    );
    return '$_temp0';
  }

  @override
  String get kitMarkWaiting => 'Waiting';

  @override
  String get kitMarkWorking => 'Working';

  @override
  String get kitMarkDone => 'Done';

  @override
  String get kitMarkFailed => 'Failed';

  @override
  String get kitMarkPaused => 'Paused';

  @override
  String get kitTaskNeedsYou => 'Needs you';

  @override
  String get kitTaskStopped => 'Stopped';

  @override
  String get kitSwatchInUse => 'In use';

  @override
  String get kitThemePreviewTitle => 'Fix the login bug';

  @override
  String get kitThemePreviewWorking => 'Working · 2 min';

  @override
  String get kitThemePreviewNeedsYou => 'Needs you';

  @override
  String get kitThemePreviewPrimary => 'Send';

  @override
  String get kitThemePreviewSecondary => 'Attach';

  @override
  String get kitThemePreviewSegment => 'On';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => 'Slash key';

  @override
  String get kitTerminalViewKeyDash => 'Dash key';

  @override
  String get kitTerminalViewKeyPipe => 'Pipe key';

  @override
  String get kitTerminalViewKeyTilde => 'Tilde key';

  @override
  String get kitTerminalViewKeyHome => 'Home key';

  @override
  String get kitTerminalViewKeyEnd => 'End key';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Showing the last $countString lines',
      one: 'Showing the last line',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => 'Undo';

  @override
  String kitUndoFailed(String message) {
    return 'Couldn\'t undo. $message';
  }

  @override
  String get kitUndoWorking => 'Undoing';

  @override
  String get kitTermHint => 'Explanation available';

  @override
  String get kitTermShow => 'Show explanation';

  @override
  String get kitTermClose => 'Close explanation';

  @override
  String kitFieldShowNamed(String label) {
    return 'Show $label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return 'Hide $label';
  }

  @override
  String get kitFieldPaste => 'Paste';

  @override
  String get kitFieldSaved => 'Saved';

  @override
  String get kitFieldReplace => 'Replace';

  @override
  String get kitFieldChecking => 'Checking…';

  @override
  String kitFieldStillChecking(int seconds) {
    return 'Still checking after $seconds s';
  }

  @override
  String kitFieldCount(int count, int max) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);
    final intl.NumberFormat maxNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String maxString = maxNumberFormat.format(max);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString of $maxString',
      one: '1 of $maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => 'Limit reached';

  @override
  String get kitFieldErrorLabel => 'Error';

  @override
  String get kitTappableShowActions => 'Show actions';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'read $_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count times',
      one: 'once',
    );
    return 'searched $_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count folders',
      one: '1 folder',
    );
    return 'listed $_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'edited $_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count commands',
      one: '1 command',
    );
    return 'ran $_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pages',
      one: '1 page',
    );
    return 'fetched $_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
    );
    return 'delegated $_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count other steps',
      one: '1 other step',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count not run',
      one: '1 not run',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => 'Waiting for you';

  @override
  String get kitWorkStopped => 'Stopped';

  @override
  String get kitWorkDidntFinish => 'Didn\'t finish';

  @override
  String get kitWorkWorking => 'Working';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier steps',
      one: '1 earlier step',
    );
    return 'Show $_temp0';
  }

  @override
  String get kitReceiptSending => 'Sending…';

  @override
  String get kitReceiptSent => 'Sent';

  @override
  String get kitReceiptConfirmed => 'Done';

  @override
  String get kitReceiptNotConfirmed => 'Not confirmed yet';

  @override
  String get kitReceiptRefused => 'Not accepted';

  @override
  String kitReceiptRefusedReason(String reason) {
    return 'Not accepted: $reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return 'Answered on $where';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown => 'Answered on another device';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act: $reason';
  }

  @override
  String kitReceiptAt(String time) {
    return 'at $time';
  }

  @override
  String get kitDetailsHide => 'Hide details';

  @override
  String get kitCopyAll => 'Copy all';

  @override
  String kitCopyValue(String label) {
    return 'Copy $label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show all $count lines',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label: $value';
  }

  @override
  String get kitProgressRowLoading => 'Loading';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString percent';
  }

  @override
  String get kitProgressRowNearLimit => 'Near limit';

  @override
  String get kitProgressRowAtLimit => 'Limit reached';

  @override
  String kitProgressRowAsOf(String time) {
    return 'as of $time';
  }

  @override
  String get kitProgressRowOther => 'Other';

  @override
  String get kitModelServerDefault => 'Server default';

  @override
  String get kitModelSignIn => 'Sign in to a model';

  @override
  String get kitModelChoose => 'Choose a model';

  @override
  String get kitModelChange => 'Change model';

  @override
  String get kitModelActions => 'Model shortcuts';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String get kitModelContextFull => 'Context almost full';

  @override
  String kitModelContextLabel(String percent) {
    return 'Context $percent % full';
  }

  @override
  String kitAttachmentOpen(String label) {
    return 'Preview $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return 'Image, $label';
  }

  @override
  String kitAttachmentFile(String label) {
    return 'File, $label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return 'Folder, $label';
  }

  @override
  String kitAttachmentReference(String label) {
    return 'Reference, $label';
  }

  @override
  String get kitSuggestionsShowAll => 'Show all';

  @override
  String get kitSuggestionsLabel => 'Suggestions';

  @override
  String get kitNeedsYouReasonDecision => 'Needs your decision';

  @override
  String get kitNeedsYouReasonBlocked => 'Stuck: needs you';

  @override
  String get kitNeedsYouReasonConsent => 'Needs your OK';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString need you · ',
      one: 'Needs you · ',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouBadgeSuffix(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: ', $countString need you',
      one: ', 1 need you',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return 'waiting $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'waiting $minutesString minutes',
      one: 'waiting 1 minute',
      zero: 'waiting less than a minute',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$who on $server';
  }

  @override
  String get kitWorkGraph => 'Work graph';

  @override
  String get kitWorkGraphEmpty => 'No work items yet';

  @override
  String kitWorkGraphNode(String title, String state) {
    return '$title, $state';
  }

  @override
  String kitWorkGraphNeeds(String title) {
    return 'needs $title';
  }

  @override
  String kitWorkGraphNeedsMore(String title, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: '1',
    );
    return 'needs $title and $_temp0 more';
  }

  @override
  String get kitJumpLatest => 'Jump to latest';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new · Jump to latest',
      one: '1 new · Jump to latest',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => 'Current';

  @override
  String get kitChoiceRecommended => 'Recommended';

  @override
  String get kitChoiceOtherSend => 'Send answer';

  @override
  String kitChoiceSelectedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count selected',
      one: '1 selected',
      zero: 'None selected',
    );
    return '$_temp0';
  }

  @override
  String get kitComposerField => 'Message';

  @override
  String get kitComposerSend => 'Send';

  @override
  String get kitComposerSending => 'Sending';

  @override
  String get kitComposerSendOffline => 'Send when back online';

  @override
  String get kitComposerSendAfter => 'Send after this reply';

  @override
  String get kitComposerAddToTurn => 'Add to this turn';

  @override
  String get kitComposerStop => 'Stop the reply';

  @override
  String get kitComposerSendAfterShort => 'Send after';

  @override
  String get kitComposerAddToTurnShort => 'Add to this turn';

  @override
  String get kitComposerDeliveryLabel => 'When to send';

  @override
  String get kitComposerSendsAfter => 'Sends after this reply';

  @override
  String get kitComposerCannotSendYet =>
      'You can send when this reply finishes';

  @override
  String get kitComposerOffline => 'Offline · sends when you\'re back online';

  @override
  String get kitComposerTools => 'Attach and more';

  @override
  String get kitComposerVoice => 'Talk instead of typing';

  @override
  String get kitComposerEditor => 'Open full-screen editor';

  @override
  String get kitVoiceLeave => 'Leave voice mode';

  @override
  String get kitVoiceStarting => 'Getting the microphone ready…';

  @override
  String get kitVoiceListening => 'Listening…';

  @override
  String get kitVoiceTranscribing => 'Writing down what you said…';

  @override
  String get kitVoiceWaitingReply => 'Waiting for the reply…';

  @override
  String get kitVoiceSpeaking => 'Reading the reply aloud';

  @override
  String get kitVoiceReplyReady => 'The reply is ready';

  @override
  String get kitVoicePaused => 'Paused · the agent needs you';

  @override
  String get kitVoiceMicDenied => 'The microphone is off for this app';

  @override
  String get kitVoiceFailed => 'Voice stopped';

  @override
  String get kitVoiceSend => 'Send';

  @override
  String get kitVoiceDone => 'Done';

  @override
  String get kitVoiceStopReading => 'Stop reading';

  @override
  String get kitVoiceReadReply => 'Read it aloud';

  @override
  String get kitVoiceListen => 'Listen';

  @override
  String get kitVoiceReadAloud => 'Read replies aloud';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => 'Clear search';

  @override
  String get kitSearchFilter => 'Filter';

  @override
  String kitSearchFilterActive(String name) {
    return 'Filter: $name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results',
      one: '1 result',
      zero: 'No results',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return '$count loaded · searching the server…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return 'Nothing matches $query';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return 'Nothing in $what matches $query';
  }

  @override
  String get kitTopBarBack => 'Back';

  @override
  String get kitTopBarClose => 'Close';

  @override
  String get kitTopBarSearch => 'Search';

  @override
  String get kitTopBarSwitchServer => 'Switch server';

  @override
  String get kitTopBarSwitchProject => 'Switch project';

  @override
  String get kitTopBarMore => 'More actions';

  @override
  String get kitAgentStripLabel => 'Agents on this task';

  @override
  String kitAgentOpen(String name) {
    return 'Open $name\'s conversation';
  }

  @override
  String kitAgentLabel(String hasRole, String name, String role, String state) {
    String _temp0 = intl.Intl.selectLogic(hasRole, {
      'yes': '$name, $role, $state',
      'other': '$name, $state',
    });
    return '$_temp0';
  }

  @override
  String get kitBreadcrumb => 'Folder path';

  @override
  String kitBreadcrumbOpen(String folder) {
    return 'Open folder $folder';
  }

  @override
  String kitBreadcrumbOpenRoot(String root) {
    return 'Open $root';
  }

  @override
  String kitBreadcrumbCurrent(String folder) {
    return 'Current folder: $folder';
  }

  @override
  String kitBreadcrumbMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more folders',
      one: '1 more folder',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeCopyCode => 'Copy code';

  @override
  String get kitCodeCopyCommand => 'Copy command';

  @override
  String get kitCodeCopyOutput => 'Copy output';

  @override
  String get kitCodeCopyFailedCode => 'Could not copy code. Try again.';

  @override
  String get kitCodeCopyFailedCommand =>
      'Could not copy the command. Try again.';

  @override
  String get kitCodeCopyFailedOutput => 'Could not copy the output. Try again.';

  @override
  String kitCodeShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show all $count lines',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => 'Open full output';

  @override
  String get kitWrapLines => 'Wrap lines';

  @override
  String kitCodeChanges(int added, int removed) {
    return '$added added, $removed removed';
  }

  @override
  String get kitCodeEmpty => 'Empty';

  @override
  String kitTabLabel(String label, int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$label, $countString';
  }

  @override
  String kitQueuedTitle(int count) {
    return 'Waiting to send · $count';
  }

  @override
  String get kitQueuedOffline => 'Sends when you\'re back online';

  @override
  String get kitQueuedWaiting => 'Waiting to send';

  @override
  String get kitQueuedReachedServer => 'Reached the server';

  @override
  String get kitQueuedAfterReply => 'Sends after this reply';

  @override
  String get kitQueuedAddToTurn => 'Adds to this turn';

  @override
  String get kitQueuedUpdate => 'Update waiting';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count attachments',
      one: '1 attachment',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return 'Waiting message $index of $count: $text. $state';
  }

  @override
  String get kitQueuedActions => 'Message actions';

  @override
  String get kitLogTitle => 'Output';

  @override
  String get kitLogShowOutput => 'Show output';

  @override
  String get kitLogLive => 'Live';

  @override
  String kitLogQuiet(String age) {
    return 'Last line $age ago';
  }

  @override
  String kitLogQuietSeconds(int seconds) {
    return 'Last line $seconds s ago';
  }

  @override
  String get kitLogEnded => 'Ended';

  @override
  String kitLogEndedExit(String code) {
    return 'Ended · exit $code';
  }

  @override
  String get kitLogFailed => 'Failed';

  @override
  String kitLogFailedExit(String code) {
    return 'Failed · exit $code';
  }

  @override
  String get kitLogEmpty => 'No output yet';

  @override
  String kitLogNewLines(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new lines',
      one: '1 new line',
    );
    return '$_temp0';
  }

  @override
  String kitLogDropped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count earlier lines not shown',
      one: '1 earlier line not shown',
    );
    return '$_temp0';
  }

  @override
  String get kitLogReadFailed => 'Couldn\'t read the output';

  @override
  String kitLogWarningLine(String line) {
    return 'Warning: $line';
  }

  @override
  String kitLogErrorLine(String line) {
    return 'Error: $line';
  }

  @override
  String get kitUntilOff => 'Until I turn it off';

  @override
  String get kitUntilConversation => 'For this conversation';

  @override
  String get kitUntilHour => 'For an hour';

  @override
  String get kitRiskTurnOn => 'Turn on';

  @override
  String get kitRiskNotNow => 'Not now';

  @override
  String get kitRiskTurnOff => 'Turn off';

  @override
  String get safetyDisconnectBody =>
      'Live updates stop and you return to the server list. The server keeps running and nothing on it changes.';

  @override
  String get safetyDisconnectBodyPhone =>
      'Live updates stop and you return to the server list. OpenCode keeps running on this phone, using battery, until you stop it.';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count messages waiting to send stay on this phone until you connect again.',
      one:
          '1 message waiting to send stays on this phone until you connect again.',
    );
    return '$_temp0';
  }

  @override
  String get quotaMonitorThreshold => 'Alert when used reaches';

  @override
  String get quotaMonitorSaving => 'Saving…';

  @override
  String get folderBrowserSlowTitle => 'Still reading this folder';

  @override
  String get folderBrowserSlowBody =>
      'Folders on this phone can take up to 15 seconds to list.';

  @override
  String get folderBrowserFirstProject => 'Name your first project';

  @override
  String kitChecklistNext(String step) {
    return 'next: $step';
  }

  @override
  String kitChecklistNeedsYou(String action) {
    return 'needs you, $action';
  }

  @override
  String get kitChecklistShowSteps => 'Show steps';

  @override
  String get kitChecklistHideSteps => 'Hide steps';

  @override
  String get kitRequestAllowOnce => 'Allow once';

  @override
  String get kitRequestReject => 'Reject';

  @override
  String get kitRequestApprove => 'Approve';

  @override
  String get kitRequestSendBack => 'Send back';

  @override
  String get kitRequestAnswer => 'Answer';

  @override
  String get kitRequestSend => 'Send';

  @override
  String get kitRequestReplyEmptyReason => 'Type a reply first.';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString more answers',
      one: '1 more answer',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => 'Expired · the agent stopped waiting';

  @override
  String kitRequestAge(String age) {
    return 'waiting $age';
  }

  @override
  String kitDiffFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String kitDiffChangeOf(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Change $indexString of $countString';
  }

  @override
  String get kitDiffPreviousChange => 'Previous change';

  @override
  String get kitDiffNextChange => 'Next change';

  @override
  String kitDiffLines(int start, int end) {
    final intl.NumberFormat startNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String startString = startNumberFormat.format(start);
    final intl.NumberFormat endNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String endString = endNumberFormat.format(end);

    return 'Lines $startString–$endString';
  }

  @override
  String kitDiffShowUnchanged(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $count unchanged lines',
      one: 'Show 1 unchanged line',
    );
    return '$_temp0';
  }

  @override
  String get kitDiffHideUnchanged => 'Hide unchanged lines';

  @override
  String kitDiffUnchangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unchanged lines',
    );
    return '$_temp0';
  }

  @override
  String get kitDiffNoChanges => 'No changes';

  @override
  String get kitDiffBinary => 'Binary file · not shown';

  @override
  String kitDiffRenamed(String path) {
    return 'Renamed from $path';
  }

  @override
  String get kitDiffAddedFile => 'New file';

  @override
  String get kitDiffDeletedFile => 'Deleted';

  @override
  String kitDiffTooBig(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Showing $shownString of $totalString lines';
  }

  @override
  String get kitDiffOpenAll => 'Open all';

  @override
  String kitDiffLineAdded(int number) {
    return 'Line $number added';
  }

  @override
  String kitDiffLineRemoved(int number) {
    return 'Line $number removed';
  }

  @override
  String get kitDiffComment => 'Comment';

  @override
  String get kitDiffAddToPrompt => 'Add to prompt';

  @override
  String get kitDiffCopyLines => 'Copy lines';

  @override
  String get kitDiffClearSelection => 'Clear selection';

  @override
  String kitDiffSelected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines selected',
      one: '1 line selected',
    );
    return '$_temp0';
  }

  @override
  String kitDiffCounts(int added, int removed) {
    return '$added added, $removed removed';
  }

  @override
  String get kitDiffLoadFailed => 'Couldn\'t load the changes';

  @override
  String kitDiffLine(int number) {
    return 'Line $number';
  }

  @override
  String kitBoardLane(String column, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'no tasks',
    );
    return '$column, $_temp0';
  }

  @override
  String kitBoardLaneLoading(String column) {
    return 'Loading $column';
  }

  @override
  String get kitMarkdownOpenFile => 'Open file';

  @override
  String kitMarkdownTable(int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: 'Table, $rows rows',
      one: 'Table, 1 row',
    );
    return '$_temp0';
  }

  @override
  String get kitToolNotRun => 'Not run';

  @override
  String get kitToolWaiting => 'Waiting';

  @override
  String get kitToolRunning => 'Running';

  @override
  String get kitToolWaitingForYou => 'Waiting for you';

  @override
  String get kitToolDone => 'Done';

  @override
  String get kitToolFailed => 'Failed';

  @override
  String get kitToolStopped => 'Stopped';

  @override
  String get kitToolBackground => 'Started in the background';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString seconds',
      one: '1 second',
    );
    return '$_temp0';
  }

  @override
  String kitToolTookMinutes(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString minutes',
      one: '1 minute',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => 'Open its conversation';

  @override
  String get kitViewerFind => 'Find in file';

  @override
  String kitViewerFindCount(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$indexString of $countString';
  }

  @override
  String get kitViewerFindNone => 'No matches';

  @override
  String get kitViewerFindPrevious => 'Previous match';

  @override
  String get kitViewerFindNext => 'Next match';

  @override
  String get kitViewerFindClose => 'Close find';

  @override
  String get kitViewerCopyContents => 'Copy contents';

  @override
  String get kitViewerShowSource => 'Show source';

  @override
  String get kitViewerEmpty => 'This file is empty';

  @override
  String kitViewerTruncated(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Showing the first $shownString of $totalString lines';
  }

  @override
  String get kitViewerPartial => 'Showing part of this file';

  @override
  String get kitViewerOpenAll => 'Open all';

  @override
  String get kitViewerCantShow => 'Can\'t show this file';

  @override
  String kitViewerCantShowBody(String type, String size) {
    return '$type · $size';
  }

  @override
  String get kitViewerUnknownType => 'Unknown type';

  @override
  String get kitViewerUnknownSize => 'size unknown';

  @override
  String kitViewerLoadFailed(String name) {
    return 'Couldn\'t open $name';
  }

  @override
  String kitViewerPage(int page, int count) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Page $pageString of $countString';
  }

  @override
  String kitViewerPageFailed(int page) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);

    return 'Couldn\'t show page $pageString';
  }

  @override
  String get kitCapServerAnyTitle => 'Server to work on';

  @override
  String get kitCapServerAnyWhy =>
      'There\'s no server yet. Set one up on this phone or connect a computer.';

  @override
  String get kitCapServerAnyEnable => 'Add a server';

  @override
  String get kitCapServerAnyOffer =>
      'Add a server to start working with an agent.';

  @override
  String get kitCapServerOc1Title => 'OpenCode 1 server';

  @override
  String get kitCapServerOc1Why =>
      'Needs this phone\'s own server or a computer running OpenCode 1.';

  @override
  String get kitCapServerOc2Title => 'OpenCode 2 server';

  @override
  String get kitCapServerOc2Why =>
      'Needs a server running OpenCode 2. This phone\'s server can switch to it.';

  @override
  String get kitCapServerOc2Enable => 'Switch to OpenCode 2';

  @override
  String get kitCapServerOc2Offer =>
      'This needs OpenCode 2. Switch this phone\'s server to it?';

  @override
  String get kitCapServerCodexTitle => 'Codex server';

  @override
  String get kitCapServerCodexWhy => 'Needs a computer running Codex.';

  @override
  String get kitCapServerCodexEnable => 'Connect Codex';

  @override
  String get kitCapServerCodexOffer =>
      'Connect a computer running Codex to use it here.';

  @override
  String get kitCapServerPaseoTitle => 'Claude Code or Pi';

  @override
  String get kitCapServerPaseoWhy =>
      'Needs Paseo, on a computer or on this phone.';

  @override
  String get kitCapServerPaseoEnable => 'Connect Paseo';

  @override
  String get kitCapServerPaseoOffer =>
      'Work with Claude Code or Pi. Connect Paseo?';

  @override
  String get kitCapPhoneBuiltinTitle => 'Server on this phone';

  @override
  String get kitCapPhoneBuiltinWhy =>
      'This phone has no server of its own yet.';

  @override
  String get kitCapPhoneBuiltinEnable => 'Set up this phone';

  @override
  String get kitCapPhoneBuiltinOffer =>
      'Run agents right on this phone. Set it up?';

  @override
  String get kitCapPhoneTermuxTitle => 'Server in Termux';

  @override
  String get kitCapPhoneTermuxWhy => 'Needs Termux on this phone.';

  @override
  String get kitCapPhoneTermuxEnable => 'Set up with Termux';

  @override
  String get kitCapPhoneTermuxOffer =>
      'Run this phone\'s server in Termux instead?';

  @override
  String get kitCapPhoneAnyTitle => 'Server on this phone';

  @override
  String get kitCapPhoneAnyWhy => 'Needs a server running on this phone.';

  @override
  String get kitCapModelAuthTitle => 'Model sign-in';

  @override
  String get kitCapModelAuthWhy =>
      'Sign in to a model provider so the agent can reply.';

  @override
  String get kitCapModelAuthEnable => 'Sign in to a model';

  @override
  String get kitCapModelAuthOffer =>
      'The agent needs a model to reply. Sign in to one?';

  @override
  String get kitCapTeamOnTitle => 'AI Team';

  @override
  String get kitCapTeamOnWhy => 'AI Team is off on this server.';

  @override
  String get kitCapTeamOnEnable => 'Turn on AI Team';

  @override
  String get kitCapTeamOnOffer =>
      'This server can also run an AI team. Turn it on?';

  @override
  String get kitCapTeamPhoneTitle => 'AI Team on this phone';

  @override
  String get kitCapTeamPhoneWhy => 'Runs only on this phone\'s own server.';

  @override
  String get kitCapTeamControlTitle => 'Team controls';

  @override
  String get kitCapTeamControlWhy =>
      'Answer this on the computer that runs the team.';

  @override
  String get kitCapTeamControlEnable => 'See how to set it up';

  @override
  String get kitCapTeamControlOffer =>
      'Control the team from here once the computer is set up. See how?';

  @override
  String get kitCapClaudeLocalTitle => 'Claude Code on this phone';

  @override
  String get kitCapClaudeLocalWhy =>
      'Needs Termux on this phone and a Claude subscription.';

  @override
  String get kitCapClaudeLocalEnable => 'Add Claude Code';

  @override
  String get kitCapClaudeLocalOffer =>
      'Add Claude Code to this phone? It needs a Claude subscription.';

  @override
  String get kitCapVoiceModelTitle => 'Voice typing';

  @override
  String get kitCapVoiceModelWhy => 'Needs a voice model on this phone.';

  @override
  String get kitCapVoiceModelEnable => 'Download voice model';

  @override
  String get kitCapVoiceModelOffer =>
      'Type by voice on this phone. Download a voice model?';

  @override
  String get kitCapMcpAnyTitle => 'Extra tools';

  @override
  String get kitCapMcpAnyWhy =>
      'This server can\'t add extra tools from the app.';

  @override
  String get kitCapMcpAnyEnable => 'Add a tool';

  @override
  String get kitCapMcpAnyOffer => 'Give the agent more tools. Add one?';

  @override
  String get kitCapProjectOpenTitle => 'Project';

  @override
  String get kitCapProjectOpenWhy => 'Choose a folder to work in first.';

  @override
  String get kitCapProjectOpenEnable => 'Choose a project';

  @override
  String get kitCapProjectOpenOffer =>
      'Choose a project folder to start working.';

  @override
  String get kitCapProjectGitTitle => 'Git project';

  @override
  String get kitCapProjectGitWhy => 'This folder isn\'t a Git project yet.';

  @override
  String get kitCapProjectGitEnable => 'Make this a Git project';

  @override
  String get kitCapProjectGitOffer =>
      'This needs a Git project. Make this folder one?';

  @override
  String get kitCapPermNotificationsTitle => 'Notifications';

  @override
  String get kitCapPermNotificationsWhy =>
      'Notifications are off for this app.';

  @override
  String get kitCapPermNotificationsEnable => 'Allow notifications';

  @override
  String get kitCapPermNotificationsOffer =>
      'Hear when an agent needs you or finishes. Allow notifications?';

  @override
  String get kitCapPermBatteryTitle => 'Running in the background';

  @override
  String get kitCapPermBatteryWhy =>
      'Android may stop the app while it\'s in the background.';

  @override
  String get kitCapPermBatteryEnable => 'Allow background running';

  @override
  String get kitCapPermBatteryOffer =>
      'Keep agents running when the app is closed?';

  @override
  String get kitCapPermCameraTitle => 'Camera';

  @override
  String get kitCapPermCameraWhy => 'Camera access is off for this app.';

  @override
  String get kitCapPermCameraEnable => 'Allow camera';

  @override
  String get kitCapPermCameraOffer =>
      'Scan pairing codes and add photos. Allow the camera?';

  @override
  String get kitCapPermMicTitle => 'Microphone';

  @override
  String get kitCapPermMicWhy => 'Microphone access is off for this app.';

  @override
  String get kitCapPermMicEnable => 'Allow microphone';

  @override
  String get kitCapPermMicOffer => 'Speak your prompts. Allow the microphone?';

  @override
  String get kitCapNetworkTailscaleTitle => 'Reach from anywhere';

  @override
  String get kitCapNetworkTailscaleWhy =>
      'Your phone and computer aren\'t on the same network.';

  @override
  String get kitCapNetworkTailscaleEnable => 'Set up Tailscale';

  @override
  String get kitCapNetworkTailscaleOffer =>
      'Reach your computer from anywhere with Tailscale. Set it up?';

  @override
  String get kitCapQuotaCollectorTitle => 'Remaining usage';

  @override
  String get kitCapQuotaCollectorWhy =>
      'This server doesn\'t report what\'s left of your plan.';

  @override
  String get kitCapQuotaCollectorEnable => 'See how to add it';

  @override
  String get kitCapQuotaCollectorOffer =>
      'See what\'s left of your plan here. Add it on the server?';

  @override
  String get kitCapAgentA2aTitle => 'Other agents';

  @override
  String get kitCapAgentA2aWhy => 'No other agents are added yet.';

  @override
  String get kitCapAgentA2aEnable => 'Add an agent';

  @override
  String get kitCapAgentA2aOffer =>
      'Work with agents from other apps. Add one?';

  @override
  String get kitCapFlagFileBrowsingTerminalTitle => 'Files and terminal';

  @override
  String get kitCapFlagFileBrowsingTerminalWhy =>
      'This server doesn\'t share its files or terminal.';

  @override
  String get kitCapFlagSessionDiffTitle => 'Review changes';

  @override
  String get kitCapFlagSessionDiffWhy =>
      'This server doesn\'t show the changes an agent made.';

  @override
  String get kitCapFlagServerCatalogTitle => 'Server settings';

  @override
  String get kitCapFlagServerCatalogWhy =>
      'This server doesn\'t share its providers, tools or commands.';

  @override
  String get kitCapFlagUsageStatisticsTitle => 'Spending';

  @override
  String get kitCapFlagUsageStatisticsWhy =>
      'This server doesn\'t report what was spent.';

  @override
  String get kitCapFlagStagedRevertSessionNotesTitle =>
      'Notes and step-by-step undo';

  @override
  String get kitCapFlagStagedRevertSessionNotesWhy =>
      'This server can\'t take notes for the agent or undo step by step.';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle =>
      'Isolated tasks and sharing';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy =>
      'This server can\'t run isolated tasks or share conversations.';

  @override
  String get kitCapFlagDevelopmentServicesTitle => 'Development services';

  @override
  String get kitCapFlagDevelopmentServicesWhy =>
      'This server can\'t start or stop development services.';

  @override
  String get kitCapFlagRemoteUpgradeTitle => 'Updating the server';

  @override
  String get kitCapFlagRemoteUpgradeWhy =>
      'This server can\'t be updated from the app.';

  @override
  String get kitCapFlagPromptAttachmentsTitle => 'Attach photos and files';

  @override
  String get kitCapFlagPromptAttachmentsWhy =>
      'This server can\'t take photos or files with a prompt.';

  @override
  String get kitHostThisPhone => 'this phone';

  @override
  String get kitHostTermux => 'Termux';

  @override
  String get kitHostOpenCode1 => 'computers with OpenCode 1';

  @override
  String get kitHostOpenCode2 => 'computers with OpenCode 2';

  @override
  String get kitHostCodex => 'Codex';

  @override
  String get kitHostPaseo => 'Paseo';

  @override
  String get kitHostDemo => 'the offline demo';

  @override
  String get kitHostOpenCode => 'computers with OpenCode';

  @override
  String kitCapNotOnHost(int count, String feature, String host) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$feature aren\'t available on $host',
      one: '$feature isn\'t available on $host',
    );
    return '$_temp0';
  }

  @override
  String kitCapWorksOn(String hosts) {
    return 'Works on $hosts';
  }

  @override
  String kitCapWhyElsewhere(String notHere, String worksOn) {
    return '$notHere. $worksOn.';
  }

  @override
  String kitCapAnd(String first, String last) {
    return '$first and $last';
  }

  @override
  String kitCapComma(String first, String next) {
    return '$first, $next';
  }

  @override
  String kitCapServerOnHost(String server, String host) {
    return '$server ($host)';
  }

  @override
  String get kitCapNotNow => 'Not now';

  @override
  String get desktopDropHint => 'Drop to attach';

  @override
  String get searchClaudeCodeGateTitle => 'Not on this device';

  @override
  String get searchClaudeCodeGateDevice =>
      'Claude Code runs on a phone only through Termux, which this device doesn\'t have. Run it on a computer with Paseo and add that computer as a server.';

  @override
  String get searchClaudeCodeGateDesktop =>
      'Claude Code on this phone is for Android phones with Termux. On a computer, run Claude Code with Paseo and add it as a server.';

  @override
  String get searchClaudeCodeGateServers => 'Open servers';

  @override
  String get activityDigestHidden => 'Digest hidden';

  @override
  String get activityOpenFailedTitle => 'Couldn\'t open conversation';

  @override
  String get activityLoading => 'Loading the Inbox';

  @override
  String get activityPickRequest => 'Pick a request';

  @override
  String get activityPickRequestDetail =>
      'Choose one from the list to answer it here.';

  @override
  String activityAllowOnceFailed(String reason) {
    return 'Not sent: $reason';
  }

  @override
  String get activitySendOffline => 'Reconnect to the server to answer.';

  @override
  String get activityLastSeenRunning => 'Last seen running';

  @override
  String get activityIfIgnored => 'The agent waits; nothing is lost.';

  @override
  String activityPermissionAnnouncement(String title) {
    return 'Permission needed: $title';
  }

  @override
  String activityFormAnnouncement(String title) {
    return 'Input requested: $title';
  }

  @override
  String get activityAnswerEveryQuestion => 'Answer every question first.';

  @override
  String get activitySending => 'Sending…';

  @override
  String activityQuestionProgress(int index, int total) {
    return 'Question $index of $total';
  }

  @override
  String get activityOwnAnswer => 'Or write your own answer';

  @override
  String get shortcutsPaletteSearch => 'Search commands and settings';

  @override
  String get shortcutsHelpAnywhere => 'Anywhere';

  @override
  String get shortcutsHelpConversation => 'In a conversation';

  @override
  String get homeShellProjectUnavailable => 'Project isn\'t available';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return '$server doesn\'t offer files, changes or code search. Connect to an OpenCode server to use them.';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return '$server has no project tools.';
  }

  @override
  String get workspaceDetailEmptyTitle => 'Choose a conversation';

  @override
  String get workspaceDetailEmptyBody =>
      'Open a conversation from the list to read and reply here.';

  @override
  String workspaceContextOn(String server) {
    return 'On $server';
  }

  @override
  String get workspaceContextCurrent => 'In use';

  @override
  String get workspaceContextNewProject => 'New project';

  @override
  String get workspaceContextRunsOn => 'Runs on';

  @override
  String get workspaceContextFolder => 'Folder';

  @override
  String get workspaceSessionSharedLink => 'Shared link';

  @override
  String workspaceArchiveFailed(String title) {
    return 'Couldn\'t archive “$title”. It is back in the list.';
  }

  @override
  String get workspaceShareCopiesLink =>
      'The link is copied once sharing starts.';

  @override
  String get workspaceDeleteSharedLink => 'Its shared link stops working.';

  @override
  String get workspaceChooserEnterPath => 'Enter a folder path';

  @override
  String get workspaceChooserRecentProjects => 'Open a project you used before';

  @override
  String get workspaceChooserLoadFailedTitle => 'Couldn\'t load your projects';

  @override
  String get workspaceChooserLoadFailedBody =>
      'You can still open a folder by its path.';

  @override
  String get managedWorkspacesRefresh => 'Refresh';

  @override
  String get managedWorkspacesDiscovered => 'Discovery finished';

  @override
  String get managedWorkspacesDiscoverFailed =>
      'Couldn’t discover environments';

  @override
  String get managedWorkspacesCreateFailed => 'Couldn’t create the environment';

  @override
  String managedWorkspacesOpenFailed(String name) {
    return 'Couldn’t open $name';
  }

  @override
  String managedWorkspacesRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String managedWorkspacesRemoveBody(String provider) {
    return 'The server asks $provider to delete this environment and what is in it.';
  }

  @override
  String get managedWorkspacesRemoveLeavesFirst =>
      'It is open now, so the app goes back to the project folder first.';

  @override
  String get managedWorkspacesRemoveHistoryStays =>
      'Conversations stay in history but can no longer open it.';

  @override
  String get managedWorkspacesRemoveAction => 'Remove';

  @override
  String managedWorkspacesRemoved(String name) {
    return '$name was removed';
  }

  @override
  String get managedWorkspacesProvider => 'Provider';

  @override
  String get managedWorkspacesProvidersFailed => 'Couldn’t load providers';

  @override
  String get managedWorkspacesNoProviderTitle => 'No provider set up';

  @override
  String get managedWorkspacesNoProviderBody =>
      'This server has no cloud environment provider. Add one to OpenCode’s config on the server, then refresh.';

  @override
  String managedWorkspacesEmptyBody(String project) {
    return 'Environments for $project appear here. Create one, or discover the ones a provider already has.';
  }

  @override
  String get managedWorkspacesLoadFailed => 'Couldn’t load cloud environments';

  @override
  String get managedWorkspacesCreating => 'Creating a cloud environment';

  @override
  String get managedWorkspacesCreatingBody =>
      'This usually takes a few minutes. It opens here when it’s ready.';

  @override
  String get managedWorkspacesCreateTakes =>
      'Creating one usually takes a few minutes. It opens here when it’s ready.';

  @override
  String get managedWorkspacesBranchLabel => 'Branch';

  @override
  String get managedWorkspacesBranchHelper =>
      'Leave empty to use the provider’s default branch.';

  @override
  String get managedWorkspacesInUse => 'In use';

  @override
  String get managedWorkspacesCopyId => 'Copy ID';

  @override
  String get projectHealthGitInitSupporting =>
      'Runs git init here. Nothing is committed.';

  @override
  String get projectHealthSetUp => 'Set up';

  @override
  String get projectHealthRunning => 'Running';

  @override
  String get projectHealthNotRunning => 'Not running';

  @override
  String projectHealthLineCounts(int added, int removed) {
    return '$added lines added, $removed removed';
  }

  @override
  String projectFolderCreateHelper(String directory) {
    return 'Made in $directory on this phone and opened as the project.';
  }

  @override
  String get projectFolderMissingTitle => 'Create this folder?';

  @override
  String get projectFolderCreateFailedTitle => 'Couldn’t create the folder';

  @override
  String get projectFolderOpenFailedTitle => 'Couldn’t open the folder';

  @override
  String get projectsOneFolderTitle => 'Server uses one folder';

  @override
  String servicesStarted(String name) {
    return '$name started';
  }

  @override
  String servicesRemoved(String name) {
    return '$name removed';
  }

  @override
  String servicesStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String servicesRestartTitle(String name) {
    return 'Restart $name?';
  }

  @override
  String servicesForgetTitle(String name) {
    return 'Forget $name\'s last run?';
  }

  @override
  String servicesRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get servicesRemoveRunningHint =>
      'Its command keeps running on the server, and this app can no longer stop it. Stop it first to end it.';

  @override
  String get servicesEmptyTitle => 'No dev commands yet';

  @override
  String get servicesOffline =>
      'The server is not answering. Commands cannot be started or checked until it reconnects.';

  @override
  String get servicesLogFailed => 'Could not read the log.';

  @override
  String get servicesProjectFolder => 'Project folder';

  @override
  String get servicesWorkspace => 'Environment';

  @override
  String get servicesNameRequired => 'Enter a name.';

  @override
  String get servicesDuplicateName =>
      'A service with this name already exists.';

  @override
  String get servicesCommandRequired => 'Enter a command, such as npm run dev.';

  @override
  String get servicesUrlInvalid =>
      'Enter an http or https address without a user name or password.';

  @override
  String get isolatedTaskProjectFolder => 'Project folder';

  @override
  String get isolatedTaskStageCreate => 'Making the copy';

  @override
  String get isolatedTaskStagePrepare => 'Running the project setup';

  @override
  String get isolatedTaskStageOpen => 'Opening the conversation';

  @override
  String get isolatedTaskUsually => 'Usually 1–3 minutes';

  @override
  String get savedPermissionsIntro =>
      'Actions the agent may take in this project without asking you first. Revoke one and the agent asks again.';

  @override
  String get savedPermissionsLoadFailed =>
      'Could not load the always allowed actions';

  @override
  String get savedPermissionsRevokeBody =>
      'The agent will ask you again the next time it wants to do this. Work that is already running keeps going.';

  @override
  String savedPermissionsRevokedDetail(String action) {
    return '$action now asks you first again.';
  }

  @override
  String get savedPermissionsDismiss => 'Dismiss';

  @override
  String get savedPermissionsCopyPattern => 'Copy pattern';

  @override
  String get savedPermissionsBusy => 'Wait for the current change to finish';

  @override
  String get savedPermissionsLoading => 'Loading always allowed actions';

  @override
  String get savedPermissionsAllResources =>
      'Anything this kind of action touches';

  @override
  String get settingsHubDetailEmpty => 'Choose a group of settings';

  @override
  String get notifyQuietStartPicker => 'Set when quiet hours start';

  @override
  String get notifyQuietEndPicker => 'Set when quiet hours end';

  @override
  String get notifyQuietSet => 'Set';

  @override
  String get notifyQuietAllDay =>
      'Start and end are the same, so notifications stay quiet all day.';

  @override
  String get notifySendingTest => 'Sending a test notification…';

  @override
  String get notifyNoServersTitle => 'No servers to watch';

  @override
  String get notifyNoServersDetail =>
      'Servers you save can be watched from here, so a request on one reaches you.';

  @override
  String get notifyDismiss => 'Dismiss';

  @override
  String get notifySaving => 'Saving';

  @override
  String get notifyMonitorDetails => 'How watching servers works';

  @override
  String get notifyRestartBackground => 'Restart the live connection';

  @override
  String get appearanceModeSystem => 'System';

  @override
  String get effectsPreviewWork => 'Work';

  @override
  String get effectsPreviewSettings => 'Settings';

  @override
  String get privacySharedSection => 'Shared with your server';

  @override
  String get privacySaving => 'Saving…';

  @override
  String get privacyDeleting => 'Deleting';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count queued prompts',
      one: 'Delete 1 queued prompt',
      zero: 'Delete queued prompts',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count drafts',
      one: 'Delete 1 draft',
      zero: 'Delete drafts',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => 'You said';

  @override
  String get kitMessageThinking => 'Thinking…';

  @override
  String get kitMessageThought => 'Thought';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count seconds',
      one: '1 second',
    );
    return 'Thought for $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutes',
      one: '1 minute',
    );
    return 'Thought for $_temp0';
  }

  @override
  String get kitMessageActions => 'Message actions';

  @override
  String get kitMessageNoticeFailed => 'Failed';

  @override
  String get kitRequestChooseOneReason => 'Choose at least one answer.';

  @override
  String get kitRequestSendAnswers => 'Send answers';

  @override
  String get serversRemoveBody =>
      'This phone forgets the server: its password, chosen model and agent, project and widget conversations.';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count unsent drafts will be deleted',
      one: '1 unsent draft will be deleted',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext =>
      'You are connected to it: the app disconnects and shows your servers';

  @override
  String get serversRemoveServerKeeps =>
      'Nothing is deleted on the server or at your AI providers';

  @override
  String get guideStepTwoScan =>
      'Tap Add server, then Scan code and point the camera at the QR, or Paste code.';

  @override
  String get guideStepTwoPaste =>
      'Copy the printed code, then tap Add server and Paste code.';

  @override
  String get guidePhonePathTitle => 'Use this phone instead';

  @override
  String get guidePhonePathBody =>
      'Install OpenCode on this phone and use it here, no computer needed';

  @override
  String get pairingScannerAllowCamera => 'Allow camera';

  @override
  String get pairingScannerStarting => 'Opening the camera…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return 'A run is going on $current. Switching shows $target in this app; the run on $current keeps going.';
  }

  @override
  String get profileMonitorOpenFailedTitle => 'Couldn\'t open it';

  @override
  String get profileMonitorIfIgnored => 'The agent waits until you answer';

  @override
  String get serverSettingsRestartCommandLabel =>
      'Set up with the Linux service script?';

  @override
  String get serverSettingsRestartedIt => 'I restarted it';

  @override
  String serverSettingsUpgradeBody(
    String target,
    String server,
    String current,
  ) {
    return 'Installs OpenCode $target on $server (now $current) with the server’s own installer.';
  }

  @override
  String serverSettingsUpgradeKeepsRunning(String current) {
    return 'The server keeps running $current while it installs';
  }

  @override
  String serverSettingsUpgradeRestartAfter(String target) {
    return 'Restart the OpenCode process on its computer to use $target';
  }

  @override
  String get serverSettingsUpgradeKeepsData => 'Server data stays in place';

  @override
  String serverSettingsCopyUpdateCommands(String server) {
    return 'Copy update commands for $server';
  }

  @override
  String get serverSettingsAddressLabel => 'Address';

  @override
  String get tailscaleSetupAppTitle => 'Tailscale on this phone';

  @override
  String get tailscaleSetupVpnTitle => 'Sign in and connect';

  @override
  String get tailscaleSetupVpnSupporting =>
      'Sign in and connect. OpenCode can’t check this.';

  @override
  String get tailscaleSetupOpenFailed =>
      'Tailscale didn’t open. Open it from your launcher, then come back.';

  @override
  String get tailscaleSetupAddressHelper =>
      'Paste the HTTPS address Tailscale Serve printed.';

  @override
  String get tailscaleSetupGetApp => 'Get Tailscale';

  @override
  String get tailscaleSetupContinueReason =>
      'Enter your server’s address first.';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return 'Partly translated ($percent %)';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return 'Preview of $name';
  }

  @override
  String get appearancePickerPreviewIn => 'Preview in';

  @override
  String get appearancePickerInUse => 'In use now';

  @override
  String appearancePickerThemeApplied(String name) {
    return 'Theme set to $name';
  }

  @override
  String get teamDiscoveryCardTurnOnFailed =>
      'Could not turn the AI team on. Nothing changed. Try again.';

  @override
  String get teamDiscoveryCardTurningOn => 'Turning the AI team on…';

  @override
  String get serverSwitcherTitle => 'Servers';

  @override
  String get serverSwitcherCurrentMenu => 'Server actions';

  @override
  String get localAgentEntryStillStarting =>
      'Still starting · this can take a minute';

  @override
  String get localAgentEntryDidNotStart => 'Didn\'t start';

  @override
  String get localAgentEntryRemoving => 'Removing';

  @override
  String localAgentEntrySignedOut(String state) {
    return '$state · Not signed in to Claude';
  }

  @override
  String get formRendererFinishLater => 'Finish later';

  @override
  String get formRendererSending => 'Sending your answers…';

  @override
  String get formRendererChoose => 'Choose';

  @override
  String get formRendererChooseDate => 'Choose a date';

  @override
  String get formRendererChooseDateTime => 'Choose a date and time';

  @override
  String formRendererDateAndTime(String date, String time) {
    return '$date at $time';
  }

  @override
  String get formRendererUseDate => 'Use date';

  @override
  String get formRendererUseTime => 'Use time';

  @override
  String get filePreviewPdfIsolated =>
      'PDF pages don\'t render in this isolated view. Save the file to read it in a PDF app.';

  @override
  String get filePreviewCopyOriginal => 'Copy original file';

  @override
  String get filePreviewOpenInFiles => 'Open in Files';

  @override
  String get filePreviewViewMode => 'Show file as';

  @override
  String get filePreviewAttachFailed => 'Couldn\'t attach file';

  @override
  String get filePreviewSaveFailed => 'Couldn\'t save file';

  @override
  String get kitTurnStarting => 'Starting the model…';

  @override
  String kitTurnStillStarting(int seconds) {
    return 'Still waiting for the model · $seconds s';
  }

  @override
  String get kitTurnStopped => 'You stopped this reply.';

  @override
  String get kitTurnInterrupted =>
      'The connection dropped before this reply finished.';

  @override
  String get kitTurnCopy => 'Copy reply';

  @override
  String get kitTurnMore => 'More for this reply';

  @override
  String get kitTurnActions => 'Reply actions';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return 'Disconnect from $serverName';
  }

  @override
  String serverSettingsDisconnectDetail(String serverName) {
    return 'Stops live updates from $serverName. Conversations stay on $serverName; unsent messages stay on this phone until you reconnect.';
  }

  @override
  String get kitScannerStarting => 'Opening the camera…';

  @override
  String get kitScannerSlow => 'Still opening the camera';

  @override
  String get kitScannerPaused => 'Camera paused';

  @override
  String get kitScannerPreview => 'Camera view';

  @override
  String get kitDateSet => 'Set date';

  @override
  String get kitTimeSet => 'Set time';

  @override
  String get kitDateTimeSet => 'Set';

  @override
  String get kitDateType => 'Type a date';

  @override
  String get kitDateCalendar => 'Show calendar';

  @override
  String kitDateFormatHint(String example) {
    return 'e.g. $example';
  }

  @override
  String get kitDateField => 'Date';

  @override
  String get kitDateInvalid => 'Not a date';

  @override
  String kitDateOutOfRange(String first, String last) {
    return 'Pick a date between $first and $last';
  }

  @override
  String get kitTimeHour => 'Hour';

  @override
  String get kitTimeMinute => 'Minute';

  @override
  String get kitTimePeriod => 'Morning or afternoon';

  @override
  String get kitTimeInvalid => 'Not a time';

  @override
  String get kitDateTimeNotSet => 'Not set';

  @override
  String kitDateTimeClear(String title) {
    return 'Clear $title';
  }

  @override
  String get kitDateUnavailable => 'That day can’t be chosen';

  @override
  String get filesLoadingFolder => 'Opening folder…';

  @override
  String get filesSearching => 'Searching…';

  @override
  String get filesShowHidden => 'Show hidden files';

  @override
  String get filesOnlyHidden =>
      'This folder has only hidden files and folders.';

  @override
  String get filesCopyName => 'Copy name';

  @override
  String globalSessionsMoveTitle(String project) {
    return 'Move to $project?';
  }

  @override
  String globalSessionsMoveBody(String title, String from, String to) {
    return '“$title” moves from $from to $to through the server’s sync system.';
  }

  @override
  String get globalSessionsMoveWhileWorking =>
      'It is working now. Moving it may interrupt the current step.';

  @override
  String globalSessionsMoveBack(String project) {
    return 'To move it back, open $project and choose Continue here in All conversations.';
  }

  @override
  String get globalSessionsFilterLabel => 'Show';

  @override
  String get globalSessionsFilterActive => 'Active';

  @override
  String get globalSessionsArchivedNoMatchMessage =>
      'No archived conversation has that title. Try a shorter search.';

  @override
  String get globalSessionsArchivedEmptyTitle => 'No archived conversations';

  @override
  String get globalSessionsArchivedEmptyMessage =>
      'Conversations you archive in Work appear here.';

  @override
  String get globalSessionsShowActive => 'Show active conversations';

  @override
  String globalSessionsProjectInUse(String project) {
    return '$project · In use';
  }

  @override
  String get globalSessionsCopyFolder => 'Copy folder path';

  @override
  String get worktreesStartConversation => 'New conversation here';

  @override
  String get worktreesCreateHelper =>
      'OpenCode makes a separate branch and folder and runs the project’s startup tasks. Spaces become dashes.';

  @override
  String get worktreesFolder => 'Folder';

  @override
  String get worktreesMainCopy => 'Main copy';

  @override
  String get worktreesCopyFolder => 'Copy folder path';

  @override
  String get worktreesLoadFailedTitle => 'Couldn\'t load worktrees';

  @override
  String get worktreesSetupFailedWord => 'Setup failed';

  @override
  String get importNeedsFile => 'Choose a JSON file first.';

  @override
  String get importNeedsDestination => 'Choose where to import it first.';

  @override
  String get importFileLabel => 'File';

  @override
  String get importPreviewLabel => 'Conversation';

  @override
  String importMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count messages',
      one: '1 message',
    );
    return '$_temp0';
  }

  @override
  String get importNoDestinationsTitle => 'Nowhere to import';

  @override
  String importOnServer(String server) {
    return 'On $server';
  }

  @override
  String get importChangeDestinationShort => 'Change';

  @override
  String get importConversationId => 'Conversation ID';

  @override
  String get importParentId => 'Parent conversation ID';

  @override
  String get importFolder => 'Folder';

  @override
  String get importEnvironmentId => 'Cloud environment ID';

  @override
  String get phoneSetupStartUseTermuxOne => 'Use the one in Termux';

  @override
  String get phoneSetupStartUseTermuxOneDetail =>
      'OpenCode is also set up in Termux. Connect to it instead.';

  @override
  String get phoneSetupStartTermuxNotAllowed =>
      'Termux is installed but hasn\'t let this app in yet. Finish its setup.';

  @override
  String get phoneSetupCustomizeAllInstalled =>
      'Every optional tool is already on this phone.';

  @override
  String get phoneSetupCustomizeIncluded => 'Required';

  @override
  String get termuxProcsLoadFailedTitle => 'Couldn\'t read what\'s running';

  @override
  String get termuxProcsEmptyBody =>
      'When OpenCode, the AI Team or a build runs here, it shows up in this list.';

  @override
  String get termuxProcsNotStoppedTitle => 'Not everything stopped';

  @override
  String get termuxProcsCopyCommand => 'Copy command';

  @override
  String get termuxProcsOpenControls => 'Open This phone';

  @override
  String get termuxProcsProcessId => 'Process ID';

  @override
  String get termuxProcsParentId => 'Parent process ID';

  @override
  String get termuxProcsAboutOpenCode =>
      'Part of the OpenCode server on this phone.';

  @override
  String get termuxProcsAboutAiTeam =>
      'Part of the AI Team. Stopping it stops the work the team is doing.';

  @override
  String get termuxProcsAboutBuild =>
      'A build helper. The next build starts it again when it needs it.';

  @override
  String get termuxProcsAboutOrphan =>
      'Nothing is waiting on it, so stopping it is safe.';

  @override
  String get termuxProcsAboutOther =>
      'Started by something else on this phone.';

  @override
  String get termuxProcsNoRestart => 'It can\'t be started again from here.';

  @override
  String get termuxProcsStopGroupTeamLost =>
      'Any task the team is working on stops too.';

  @override
  String get termuxProcsStopGroupTeamRestart =>
      'You can start the team again from AI Team.';

  @override
  String get phoneSetupProgressStopContinueLater =>
      'Continue any time from On this phone.';

  @override
  String teamMergeConfirmTask(String title) {
    return 'Task: $title';
  }

  @override
  String get teamMergeFailedNext =>
      'Nothing was merged. Fix what the host says, then try again, or review the changes.';

  @override
  String get teamStartRunRefusedKept =>
      'Your task is still here. Edit it and send it again.';

  @override
  String get transcriptTogglesReasoningOn =>
      'When on, the model\'s reasoning opens under each answer.';

  @override
  String get transcriptTogglesUsageOn =>
      'When on, each message shows its time, tokens and cost.';

  @override
  String get transcriptTogglesScope =>
      'These apply to every conversation on this device.';

  @override
  String get handoffSheetCopyCommand => 'Copy command';

  @override
  String get handoffSheetReloadConversation => 'Try again';

  @override
  String get handoffSheetPhoneServerNote =>
      'If the other phone does not have this server saved yet, it says so and offers to open Servers so you can add it.';

  @override
  String get modelPickerChooseFirst => 'Choose a model first.';

  @override
  String get modelPickerThinking => 'Thinking';

  @override
  String get modelPickerAgentBuild => 'Edits files and runs commands';

  @override
  String get modelPickerAgentPlan => 'Reads and plans; does not change files';

  @override
  String modelPickerDetailsOutput(String count) {
    return 'Up to $count tokens per answer';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '$input per million tokens read, $output per million written';
  }

  @override
  String get modelPickerCanThink => 'Thinks before answering';

  @override
  String get modelPickerCanUseTools => 'Uses tools';

  @override
  String get modelPickerCanReadAttachments =>
      'Reads images and files you attach';

  @override
  String get modelPickerCopyId => 'Copy model id';

  @override
  String get modelPickerInUse => 'In use';

  @override
  String get modelPickerUnavailableReason =>
      'Not available on this server right now.';

  @override
  String get modelPickerCollections => 'Which models to show';

  @override
  String get modelPickerSignInTitle => 'Provider sign-in needed';

  @override
  String get modelPickerSignInBody =>
      'No provider on this server has models yet. Sign in to one, then come back to choose a model.';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show $count more models',
      one: 'Show 1 more model',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'Build';

  @override
  String get modelPickerAgentPlanName => 'Plan';

  @override
  String phoneServerCardDisconnect(String server) {
    return 'Disconnect from $server';
  }

  @override
  String get phoneServerCardStartOpenCode => 'Start OpenCode';

  @override
  String get phoneServerCardStopOpenCode => 'Stop OpenCode on this phone';

  @override
  String get phoneServerCardShowServerLog => 'Show server log';

  @override
  String get phoneServerCardOpenTerminal => 'Open terminal';

  @override
  String get phoneServerCardFailedTitle => 'Could not finish';

  @override
  String get phoneServerRestartFailedTitle => 'Restart failed';

  @override
  String get setupTerminalTitle => 'Setup output';

  @override
  String get teamPhoneStopTeam => 'Stop the team';

  @override
  String get teamPhoneStartTeam => 'Start the team';

  @override
  String get teamPhoneStartTeamAgain => 'Start the team again';

  @override
  String get teamPhoneDeleteTeam => 'Delete the team from this phone';

  @override
  String get teamPhoneRemoveBody =>
      'The team stops, and the AI Team turns off for this server.';

  @override
  String get teamPhoneRemoveLost =>
      'The team\'s programs, its files and its task list are deleted';

  @override
  String get teamPhoneRemoveKept =>
      'Your project files and their git history stay';

  @override
  String teamPhoneRemoveFrees(int size) {
    return 'Frees about $size MB';
  }

  @override
  String get teamPhoneRemoveConfirm => 'Delete the team';

  @override
  String get productStatesActionFailedTitle => 'Couldn\'t finish that';

  @override
  String get productStatesSwitchServer => 'Switch server';

  @override
  String get externalLinkBlockedTitle => 'Link blocked';

  @override
  String get externalLinkBlockedBody =>
      'This app opens only https:// links, and http:// links after you confirm.';

  @override
  String externalLinkOpensHost(String host) {
    return 'Opens $host outside this app.';
  }

  @override
  String get externalLinkDontOpen => 'Don\'t open';

  @override
  String get externalLinkCopy => 'Copy link';

  @override
  String get externalLinkAddress => 'Full address';

  @override
  String get externalLinkOpenFailedTitle => 'Couldn\'t open link';

  @override
  String get runCommandReconnecting =>
      'OpenCode is reconnecting. Try again in a moment.';

  @override
  String runCommandArgumentsHelper(String command) {
    return 'Text passed to /$command. Leave it empty if the command takes none.';
  }

  @override
  String get runCommandRunsIn => 'Runs in';

  @override
  String runCommandFailedTitle(String command) {
    return 'Couldn\'t run /$command';
  }

  @override
  String get teamNowWakeRefusedNoReason => 'The host didn\'t say why.';

  @override
  String get teamHostFormTeamLabel => 'Team name (optional)';

  @override
  String get teamHostFormTeamHelper =>
      'Leave it empty to use the team the computer runs.';

  @override
  String get teamHostFormHowAction => 'How to set up the computer';

  @override
  String get teamHostFormCancelTest => 'Cancel test';

  @override
  String get teamHostFormSaveAnyway => 'Save the address anyway';

  @override
  String get teamHostFormSaveAnywayNote =>
      'AI Team shows the team as not answering until the computer answers.';

  @override
  String get teamHostFormConnectionDetails => 'Connection details';

  @override
  String teamAgentScreenPause(String agent) {
    return 'Pause $agent';
  }

  @override
  String teamAgentScreenPaused(String agent) {
    return 'Paused $agent';
  }

  @override
  String teamAgentScreenResume(String agent) {
    return 'Start $agent again';
  }

  @override
  String teamAgentScreenNudge(String agent) {
    return 'Nudge $agent';
  }

  @override
  String teamAgentScreenRestart(String agent) {
    return 'Restart $agent';
  }

  @override
  String teamAgentScreenStop(String agent) {
    return 'Stop $agent';
  }

  @override
  String teamAgentScreenStopBody(String agent, String task) {
    return '$agent stops working on “$task” now. The task stays on the host, and you can start $agent again from this page.';
  }

  @override
  String teamAgentScreenStoppedTitle(String agent) {
    return '$agent is stopped';
  }

  @override
  String get teamAgentScreenStoppedBody =>
      'Its work stays where it is. Start it again when you want it back.';

  @override
  String teamAgentScreenCrashedTitle(String agent) {
    return '$agent stopped unexpectedly';
  }

  @override
  String get teamAgentScreenCrashedBody =>
      'Its session ended on its own. Start it again to pick its work up from the host.';

  @override
  String get teamAgentScreenRecyclingBody =>
      'It starts a fresh session soon and picks its work up from the host.';

  @override
  String teamAgentScreenModelFrom(String model, String provider) {
    return '$model from $provider';
  }

  @override
  String teamAgentScreenGateIfIgnored(String agent) {
    return '$agent waits until you answer';
  }

  @override
  String teamAgentScreenControlsElsewhere(String agent) {
    return 'This phone can\'t pause, stop or message $agent on this host yet. Run the team\'s host front on the computer to control it from here.';
  }

  @override
  String get gateSheetDestructiveBody =>
      'The host marks this action as destructive. Approving it can\'t be undone from the phone.';

  @override
  String get gateSheetAnswerLabel => 'Your answer';

  @override
  String get gateSheetAfterAnswer =>
      'The team carries on as soon as the host confirms your answer.';

  @override
  String get gateSheetFixIt => 'Ask the team to fix it';

  @override
  String gateSheetFixItDetail(String agent) {
    return 'Sends the error to $agent and asks it to find the cause and carry on.';
  }

  @override
  String gateSheetFixRequest(String task, String error) {
    return 'The task “$task” failed with this error:\n$error\nPlease find the cause, fix it and carry on.';
  }

  @override
  String gateSheetOpenAgent(String agent) {
    return 'Open $agent\'s page';
  }

  @override
  String get teamIntroTurnOnPhone => 'Turn on AI Team on this phone';

  @override
  String get teamIntroInstalledTitle => 'Installed on this phone';

  @override
  String get teamIntroInstalledBody =>
      'It is not turned on yet. Turning it on starts the team for your project; nothing more to download.';

  @override
  String get teamIntroSetUpPhone => 'Set up AI Team on this phone';

  @override
  String teamIntroSetUpOn(String server) {
    return 'Set up AI Team on $server';
  }

  @override
  String teamIntroTurnOn(String server) {
    return 'Turn on AI Team on $server';
  }

  @override
  String get teamIntroCostTitle => 'Before you set it up';

  @override
  String get teamIntroCostTime => 'About 8–10 minutes the first time';

  @override
  String get teamIntroCostMemory => 'About 550 MB of memory for each worker';

  @override
  String get teamAgentScreenLabelId => 'Agent id';

  @override
  String get gateSheetSendNeedsText => 'Type an answer first';

  @override
  String teamAgentsChecked(String age) {
    return 'checked $age ago';
  }

  @override
  String get teamWorkSheetMissingTitle => 'Work item gone';

  @override
  String get teamWorkSheetMissingBody =>
      'It may have been finished or removed. Close this sheet to see the task as it is now.';

  @override
  String get teamWorkSheetNotOnHost => 'No longer listed';

  @override
  String get teamWorkSheetOpenStepConversation =>
      'Open this step\'s conversation';

  @override
  String teamWorkSheetOpenAgentConversation(String name) {
    return 'Open $name\'s conversation';
  }

  @override
  String get usageRangeLabel => 'Time range';

  @override
  String get usageAboutNumbers => 'About these numbers';

  @override
  String get usageBudgetHelperUsd =>
      'In US dollars for this range. You’re told when the report reaches it; nothing is stopped.';

  @override
  String get usageBudgetHelperTokens =>
      'Whole tokens for this range. You’re told when the report reaches it; nothing is stopped.';

  @override
  String get usageBudgetClearConfirm => 'Clear budgets';

  @override
  String get usageBudgetNotSet => 'Not set';

  @override
  String get usageBudgetWaitReason => 'Available once usage has loaded.';

  @override
  String get usageBudgetUsdTitle => 'USD budget';

  @override
  String get usageBudgetTokensTitle => 'Token budget';

  @override
  String get agentAccountScopeLostTitle => 'This server changed';

  @override
  String get agentAccountBackToServers => 'Back to Servers';

  @override
  String get agentAccountNotConnected =>
      'Connect to this server to see its Codex account.';

  @override
  String get agentAccountSignInMethod => 'Signed in with';

  @override
  String get agentAccountPlanTitle => 'Plan';

  @override
  String get agentAccountCopyCode => 'Copy sign-in code';

  @override
  String agentAccountLimitReached(String reset) {
    return 'You’ve reached a Codex limit. $reset';
  }

  @override
  String get agentAccountResetDue => 'Resets any moment';

  @override
  String agentAccountResetInDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Resets in $days days',
      one: 'Resets in 1 day',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInHours(int hours) {
    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Resets in $hours h',
      one: 'Resets in 1 h',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Resets in $minutes min',
      one: 'Resets in 1 min',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetWhen(String relative, String time) {
    return '$relative ($time)';
  }

  @override
  String get reviewWorkspaceScopes => 'Changes to show';

  @override
  String get reviewWorkspaceRefreshFailed => 'Couldn\'t refresh the changes';

  @override
  String get reviewWorkspaceSlowTitle => 'Still reading the changes';

  @override
  String get reviewWorkspaceSlowBody =>
      'The server runs git to compare the files. A big project can take a minute.';

  @override
  String get reviewWorkspaceAllViewedTitle => 'You\'ve seen every file';

  @override
  String reviewWorkspaceAllViewedMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count notes are on the prompt, ready to send from the conversation.',
      one: '1 note is on the prompt, ready to send from the conversation.',
    );
    return '$_temp0';
  }

  @override
  String get reviewWorkspaceBackToChat => 'Back to the conversation';

  @override
  String reviewWorkspaceCommentOnFile(String file) {
    return 'Comment on $file';
  }

  @override
  String reviewWorkspaceAddFileToPrompt(String file) {
    return 'Add $file to the prompt';
  }

  @override
  String get reviewWorkspaceAddComment => 'Add comment to prompt';

  @override
  String get reviewWorkspaceCommentEmpty => 'Type a comment first.';

  @override
  String get reviewWorkspaceCommentLabel => 'Your comment';

  @override
  String get reviewWorkspaceCommentHint =>
      'What should the agent check or change?';

  @override
  String get reviewWorkspaceCommentHelper =>
      'Kept if you close this, until you add it.';

  @override
  String get integrationsMcpTitle => 'MCP servers';

  @override
  String get integrationsMcpServersLabel => 'MCP servers';

  @override
  String integrationsModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count models',
      one: '1 model',
    );
    return '$_temp0';
  }

  @override
  String integrationsProviderActions(String name) {
    return '$name actions';
  }

  @override
  String integrationsManageAccounts(String name) {
    return 'Manage $name accounts';
  }

  @override
  String integrationsServerSignIn(String name) {
    return 'Sign in to $name on the server';
  }

  @override
  String get integrationsServerSignInUnavailable =>
      'This server can\'t run a sign-in command from the app.';

  @override
  String integrationsDisconnectNamed(String name) {
    return 'Disconnect $name';
  }

  @override
  String integrationsDisconnectBody(String name) {
    return 'Removes the $name key from this server. A reply already running finishes first.';
  }

  @override
  String get integrationsConnectMethodSubtitle => 'Choose how to connect';

  @override
  String get integrationsKeyHelper =>
      'The key is stored on this server. The app never shows it again.';

  @override
  String get integrationsKeyEmpty => 'Paste the key first.';

  @override
  String get integrationsKeyRejected =>
      'The server didn\'t accept this key. Check it and try again.';

  @override
  String integrationsSignInAtHost(String host) {
    return 'Sign in at $host?';
  }

  @override
  String get integrationsSignInBody =>
      'Approve access in your browser, then come back to this app.';

  @override
  String integrationsSignInInstructions(String instructions) {
    return 'The server says: $instructions';
  }

  @override
  String get integrationsFinishSignInTitle => 'Finish signing in';

  @override
  String get integrationsFinishSignInAction => 'Finish signing in';

  @override
  String get integrationsFinishSignInEmpty => 'Paste the code first.';

  @override
  String get integrationsFinishSignInMcpHelper =>
      'Paste the address your browser ended on after you approved access, or the code it showed.';

  @override
  String get integrationsFinishSignInProviderHelper =>
      'Paste the code the sign-in page showed after you approved access.';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return 'Open $name sign-in';
  }

  @override
  String get integrationsCancelSignIn => 'Cancel sign-in';

  @override
  String get integrationsPendingNotRecoverable =>
      'Keep this screen open until you finish: this server can\'t resume a sign-in after you leave.';

  @override
  String integrationsMcpActions(String name) {
    return '$name actions';
  }

  @override
  String integrationsMcpSignIn(String name) {
    return 'Sign in to $name';
  }

  @override
  String integrationsMcpReconnect(String name) {
    return 'Reconnect $name';
  }

  @override
  String integrationsMcpSigningIn(String name) {
    return 'Signing in to $name';
  }

  @override
  String get integrationsMcpSignInOnServer =>
      'Sign in on the server\'s computer; this server can\'t do it from the app.';

  @override
  String integrationsMcpRemoveUntilRestart(String name) {
    return 'Remove $name until restart';
  }

  @override
  String integrationsMcpRemoveTitle(String name) {
    return 'Remove $name until restart?';
  }

  @override
  String get integrationsMcpRemoveBody =>
      'Its tools stop working in this project now. If it\'s in the server\'s configuration, it comes back when the server restarts.';

  @override
  String get integrationsMcpRemoveConfirm => 'Remove until restart';

  @override
  String get integrationsCopyResourceAddress => 'Copy address';

  @override
  String get terminalScreenSourceLabel => 'Where the shell runs';

  @override
  String get terminalScreenNameLabel => 'Name';

  @override
  String get terminalScreenRenameConfirm => 'Rename';

  @override
  String get terminalScreenNameEmpty => 'Type a name.';

  @override
  String terminalScreenStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get terminalScreenStopBody =>
      'The program and everything it started stop, and the terminal goes away. Its output can\'t be brought back.';

  @override
  String terminalScreenRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get terminalScreenRemoveBody =>
      'The terminal and its output go away. This can\'t be undone.';

  @override
  String get terminalScreenStopConfirm => 'Stop terminal';

  @override
  String get terminalScreenRemoveConfirm => 'Remove terminal';

  @override
  String get terminalScreenCreateFailed => 'Couldn\'t start a terminal';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove $count ended terminals',
      one: 'Remove 1 ended terminal',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove $count ended terminals?',
      one: 'Remove 1 ended terminal?',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody =>
      'Their output goes away too. Running terminals stay.';

  @override
  String get terminalScreenUsePhone => 'Use this phone\'s terminal';

  @override
  String terminalScreenRowRunning(String command) {
    return 'Running · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return 'Ended · code $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return 'Ended · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return 'Actions for $name';
  }

  @override
  String terminalScreenOpen(String name) {
    return 'Open $name';
  }

  @override
  String terminalScreenRename(String name) {
    return 'Rename $name';
  }

  @override
  String terminalScreenStop(String name) {
    return 'Stop $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return 'Remove $name';
  }

  @override
  String get terminalScreenLoading => 'Loading terminals';

  @override
  String get terminalScreenPaused =>
      'Paused while the app is in the background';

  @override
  String get terminalScreenConnecting => 'Connecting to the terminal';

  @override
  String get terminalScreenCopy => 'Copy output';

  @override
  String terminalScreenPaste(String name) {
    return 'Paste into $name';
  }

  @override
  String get terminalScreenDetails => 'Terminal details';

  @override
  String terminalScreenDetailsTitle(String name) {
    return '$name details';
  }

  @override
  String get terminalScreenDetailCommand => 'Command';

  @override
  String get terminalScreenDetailFolder => 'Folder';

  @override
  String get terminalScreenDetailPid => 'Process id';

  @override
  String get terminalScreenDetailExit => 'Exit code';

  @override
  String localTerminalStopNamedTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String localTerminalPasteNamed(String name) {
    return 'Paste into $name';
  }

  @override
  String get localTerminalCopySelection => 'Copy selection';

  @override
  String defaultShellOnlyOne(String name) {
    return '$name · the only shell this server offers';
  }

  @override
  String defaultShellSaveFailed(String error) {
    return 'Couldn\'t change the shell. $error Tap to try again.';
  }

  @override
  String get terminalScreenReadableMode => 'Show as readable text';

  @override
  String get terminalScreenLiveMode => 'Show as live terminal';

  @override
  String get localTerminalSetUpLinux => 'Set up Linux on this phone';

  @override
  String get messageViewSendAgain => 'Send this message again';

  @override
  String get messageViewContinueReply => 'Continue this reply';

  @override
  String get reviewRunResultsLoadingTitle => 'Loading run results';

  @override
  String get reviewRunResultsErrorTitle => 'Couldn\'t load run results';

  @override
  String get reviewRunResultsErrorBody =>
      'The server didn\'t send this run\'s history.';

  @override
  String get reviewRunResultsEmptyTitle => 'Nothing to show yet';

  @override
  String get reviewRunResultsScopeChangedTitle => 'The project changed';

  @override
  String get reviewRunResultsCloseAction => 'Close run results';

  @override
  String get reviewRunResultsRunningNotice =>
      'Still running. This shows what it has done so far; pull down for the latest.';

  @override
  String get reviewRunResultsRefreshFailed =>
      'Couldn\'t refresh. This is what was loaded before.';

  @override
  String get reviewRunResultsReviewChanges => 'Review changed files';

  @override
  String get reviewRevertSheetTitle => 'Undo from this prompt?';

  @override
  String get reviewRevertSheetBody =>
      'This prompt and everything after it are hidden while you review. Nothing is final until you choose.';

  @override
  String get reviewRevertPromptLabel => 'From this prompt';

  @override
  String get reviewRevertFilesToggle => 'Put files back too';

  @override
  String get reviewRevertFilesToggleHint =>
      'Files go back to how they were before this prompt.';

  @override
  String get reviewRevertSheetAction => 'Undo and review';

  @override
  String get reviewRevertStageFailed =>
      'Couldn\'t set up the undo. Nothing was hidden.';

  @override
  String get reviewRevertScreenTitle => 'Review the undo';

  @override
  String get reviewRevertScreenIntro =>
      'This prompt and everything after it are hidden. Nothing is final until you choose below.';

  @override
  String get reviewRevertFilesLabel => 'Files in this undo';

  @override
  String get reviewRevertNoFiles => 'No files change with this undo.';

  @override
  String reviewRevertFileLines(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String reviewRevertFileSupporting(String folder, String lines) {
    return '$folder · $lines';
  }

  @override
  String get reviewRevertRestoreTitle => 'Put everything back';

  @override
  String get reviewRevertKeepTitle => 'Delete the hidden messages';

  @override
  String get reviewRevertKeepConfirmTitle => 'Delete hidden messages forever?';

  @override
  String get reviewRevertKeepConfirmBody => 'This can\'t be undone.';

  @override
  String get reviewRevertKeepConfirmAction => 'Delete hidden messages';

  @override
  String get reviewRevertKeepConsequenceMessages =>
      'The hidden prompt and every message after it are deleted';

  @override
  String get reviewRevertKeepConsequenceFiles => 'Files stay as they are now';

  @override
  String get reviewRevertRestoreConfirmTitle => 'Put everything back?';

  @override
  String get reviewRevertRestoreConfirmBody =>
      'The hidden messages come back, and the files in this undo return to how they were when you set it up. You can undo from a prompt again later.';

  @override
  String get reviewRevertRestoreConsequenceMessages =>
      'The hidden messages come back';

  @override
  String reviewRevertRestoreConsequenceFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files are replaced, with any edits made since',
      one: '1 file is replaced, with any edits made since',
    );
    return '$_temp0';
  }

  @override
  String get reviewRevertRestoreConsequenceUnknownFiles =>
      'Files in this undo are replaced, with any edits made since';

  @override
  String get reviewRevertStaleTitle => 'The undo changed';

  @override
  String get reviewRevertNoneTitle => 'Nothing to review';

  @override
  String get reviewRevertNoneBody =>
      'There\'s no undo waiting in this conversation.';

  @override
  String get reviewRevertBackAction => 'Back to the conversation';

  @override
  String get reviewRevertKeptTitle => 'Undo kept';

  @override
  String get reviewRevertKeptBody =>
      'The hidden messages are deleted. Files stay as they are.';

  @override
  String get reviewRevertRestoredTitle => 'Everything is back';

  @override
  String get reviewRevertRestoredBody =>
      'The messages and files are back as they were.';

  @override
  String get reviewRevertFailed =>
      'That didn\'t finish. Check the conversation, then try again.';

  @override
  String get perfTraceClearTimings => 'Clear timings';

  @override
  String appDiagnosticsClearTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Clear $count errors?',
      one: 'Clear 1 error?',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'The $count errors kept on this phone are removed, also from the saved report. This can\'t be undone.',
      one:
          'The error kept on this phone is removed, also from the saved report. This can\'t be undone.',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Clear $count errors',
      one: 'Clear 1 error',
    );
    return '$_temp0';
  }

  @override
  String get capabilityStateHere => 'Works here';

  @override
  String get capabilityStateNotServer => 'Not on this server';

  @override
  String get capabilityStateNotDevice => 'Not on this device';

  @override
  String get capabilityNeedsAndroid => 'Needs the Android app';

  @override
  String capabilityAvailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count features work here',
      one: '1 feature works here',
    );
    return '$_temp0';
  }

  @override
  String get capabilityAvailableCountDetail =>
      'Show what this server and device can do';

  @override
  String get capabilityAddServer => 'Add a server that has these';

  @override
  String get capabilityAddServerDetail =>
      'Connect another computer or set one up on this phone, then switch to it';

  @override
  String get keepRunningAllSetTitle => 'You\'re set';

  @override
  String get keepRunningAllSetBody =>
      'Android leaves the app running in the background. There is nothing else to allow on this phone.';

  @override
  String get keepRunningDailyLimit =>
      'On Android 15 and newer, Android allows background syncing for about 6 hours a day, even with everything here allowed. After that the app pauses in the background until you open it.';

  @override
  String get aboutTitle => 'About';

  @override
  String get aboutCopyVersion => 'Copy version';

  @override
  String get aboutCheckUpdates => 'Check for updates';

  @override
  String get aboutUpdateIdle => 'Looks for a newer version of this app';

  @override
  String get aboutUpdateChecking => 'Checking…';

  @override
  String get aboutUpdateCurrent => 'You have the latest version';

  @override
  String get aboutUpdateDownloading => 'Downloading the update…';

  @override
  String get aboutUpdateReady =>
      'Update ready. Close and reopen the app to use it.';

  @override
  String get aboutUpdateCannot =>
      'This build can\'t update itself. Install the newest release instead.';

  @override
  String get aboutUpdateFailed =>
      'Couldn\'t check for updates. Check your connection and try again.';

  @override
  String get aboutAllLicences => 'All package licenses';

  @override
  String get aboutAllLicencesDetail =>
      'The license text of every library bundled in this build';

  @override
  String get aboutPackageId => 'Package id';

  @override
  String get providerQuotaProviderLabel => 'Provider';

  @override
  String get providerQuotaRouteLabel => 'Collector route';

  @override
  String get usageHubUnavailableTitle => 'No usage to show';

  @override
  String get usageHubUnavailableBody =>
      'Connect to a saved server to see what it spent and what your provider accounts have left.';

  @override
  String get voiceSetupSubtitle =>
      'Download a speech model once. After that, voice input runs on this phone without the internet.';

  @override
  String voiceSetupDownloadPack(String model, String size) {
    return 'Download $model ($size)';
  }

  @override
  String voiceSetupUsePack(String model) {
    return 'Use $model';
  }

  @override
  String voiceSetupRedownloadPack(String model) {
    return 'Download $model speech model again';
  }

  @override
  String voiceSetupDeletePack(String model, String size) {
    return 'Delete $model speech model ($size)';
  }

  @override
  String voiceSetupKeepPack(String model) {
    return 'Keep $model';
  }

  @override
  String voiceSetupDownloadingPack(String model) {
    return 'Downloading $model';
  }

  @override
  String get voiceSetupModelLabel => 'Speech model';

  @override
  String get voiceNoticesTitle => 'Voice licenses';

  @override
  String get voiceNoticesIntro =>
      'Voice input is built on these open-source parts. Open one to read its license.';

  @override
  String voiceNoticesMadeBy(String maker, String license) {
    return '$maker · $license';
  }

  @override
  String voiceNoticesOpenWebsite(String name) {
    return 'Open the $name website';
  }

  @override
  String get voiceNoticesWhisper => 'Whisper speech models';

  @override
  String get voiceSetupBusyReason => 'Available after the download';

  @override
  String get shorebirdUpdateReadyTitle => 'App update ready';

  @override
  String get shorebirdUpdateReadyBody =>
      'It takes effect when you fully close the app and open it again.';

  @override
  String desktopReleaseAvailable(String tag) {
    return 'Update $tag is available';
  }

  @override
  String get desktopReleaseWhatChanged =>
      'The release page lists what changed and has the downloads.';

  @override
  String get desktopReleaseOpenPage => 'Open release page';

  @override
  String get runningWorkTitle => 'Work in this conversation';

  @override
  String get runningWorkFailed => 'Failed';

  @override
  String runningWorkAgentState(String state) {
    return 'Agent · $state';
  }

  @override
  String runningWorkCommandState(String state) {
    return 'Command · $state';
  }

  @override
  String get runningWorkOffline =>
      'Reconnecting. Try again once the server answers.';

  @override
  String runningWorkStopAgent(String title) {
    return 'Stop “$title”';
  }

  @override
  String runningWorkStopAgentTitle(String title) {
    return 'Stop “$title”?';
  }

  @override
  String get runningWorkStopAgentBody =>
      'The agent stops where it is. Its conversation and the files it changed are kept.';

  @override
  String get runningWorkStopAgentConfirm => 'Stop agent';

  @override
  String get runningWorkScopeChangedTitle => 'Server or project changed';

  @override
  String get runningWorkAgentsFailed =>
      'Couldn\'t load this conversation\'s agents.';

  @override
  String get runningWorkCommandsFailed =>
      'Couldn\'t load this conversation\'s commands.';

  @override
  String get runningWorkEmptyTitle => 'Nothing running';

  @override
  String get runningWorkEmptyBody =>
      'Agents and commands this conversation starts show here while they run and after they end.';

  @override
  String get runningWorkBackgroundBody =>
      'The work keeps running on the server and its results come back here.';

  @override
  String get runningWorkBackgroundAction => 'Keep chatting while it runs';

  @override
  String get shellOutputCopyFirst => 'Copy output first';

  @override
  String get shellOutputLimitTitle => 'Stop it after…';

  @override
  String shellOutputStopsIn(String time) {
    return 'stops in $time';
  }

  @override
  String get shellOutputNoLimit => 'no time limit';

  @override
  String shellOutputAboutToStop(String time) {
    return 'It stops in $time. Change timeout to give it longer.';
  }

  @override
  String get shellOutputReadFailed => 'Couldn\'t read the output.';

  @override
  String get shellOutputLimitFailed => 'Couldn\'t change the time limit.';

  @override
  String get shellOutputDetailCommand => 'Command as typed';

  @override
  String get shellOutputDetailFolder => 'Folder';

  @override
  String get shellOutputDetailExit => 'Exit code';

  @override
  String get shellOutputDetailId => 'Command ID';

  @override
  String get shellOutputReading => 'Reading output';

  @override
  String get sessionDestinationWarpTitle => 'Move to the cloud';

  @override
  String get sessionDestinationSeparateCopy => 'Separate copy';

  @override
  String sessionDestinationCloudKind(String state) {
    return 'Cloud machine · $state';
  }

  @override
  String get sessionDestinationConnected => 'Connected';

  @override
  String get sessionDestinationNotConnected => 'Not connected';

  @override
  String get sessionDestinationNotConnectedWhy =>
      'Not connected. It can be picked once it connects.';

  @override
  String sessionDestinationChangesGo(String destination) {
    return 'With changes, they go with it to $destination.';
  }

  @override
  String sessionDestinationChangesCopied(String destination) {
    return 'With changes, a copy goes with it to $destination.';
  }

  @override
  String sessionDestinationChangesStay(String place) {
    return 'Without changes, they stay in $place.';
  }

  @override
  String sessionDestinationMoveWithout(String destination) {
    return 'Move to $destination without changes';
  }

  @override
  String get sessionDestinationMoveFailed => 'Couldn\'t move the conversation.';

  @override
  String get sessionDestinationLoadFailed =>
      'Couldn\'t load the places to move to';

  @override
  String get sessionDestinationNoneTitle => 'Nowhere to move it';

  @override
  String get sessionDestinationNoneMoveBody =>
      'This project has only this folder. A separate copy of the project shows here once it exists.';

  @override
  String get sessionDestinationNoneWarpBody =>
      'This project has no cloud machine yet.';

  @override
  String get consoleOrganizationWhatChanges =>
      'Models, providers and billing follow the organization you pick.';

  @override
  String consoleOrganizationSwitchBody(String organization) {
    return '$organization becomes the organization for models, providers and billing. Models reload; nothing running is stopped.';
  }

  @override
  String consoleOrganizationSwitchConfirm(String organization) {
    return 'Switch to $organization';
  }

  @override
  String get consoleOrganizationLoadFailed =>
      'Couldn\'t load your organizations';

  @override
  String get consoleOrganizationNoneTitle => 'No organizations';

  @override
  String get consoleOrganizationOnlyOne =>
      'This is your only organization, so there is nothing to switch to.';

  @override
  String get sessionContextLoading => 'Loading context';

  @override
  String get sessionContextMovedTitle => 'This conversation moved';

  @override
  String get sessionContextLoadFailed => 'Couldn\'t load the context';

  @override
  String get sessionContextRefreshFailed =>
      'Couldn\'t refresh. The numbers below are from the last read.';

  @override
  String sessionContextVerdictPlenty(String percent) {
    return '$percent% used · plenty left';
  }

  @override
  String sessionContextVerdictUsed(String percent) {
    return '$percent% used';
  }

  @override
  String sessionContextVerdictNear(String percent) {
    return '$percent% used';
  }

  @override
  String sessionContextVerdictFull(String percent) {
    return '$percent% used · at the limit';
  }

  @override
  String get sessionContextNearLimitTitle => 'Near the limit';

  @override
  String get sessionContextNearLimitBody =>
      'Older details may be dropped from what the model sees. Compact the conversation to keep going, or start a new one.';

  @override
  String get sessionContextCompactAction => 'Compact this conversation';

  @override
  String get sessionContextCompactTitle => 'Compact this conversation?';

  @override
  String get sessionContextCompactBody =>
      'OpenCode summarizes the conversation so far and continues from the summary, so it takes less of the model\'s limit.';

  @override
  String get sessionContextCompactKept => 'Every message stays in the history.';

  @override
  String get sessionContextCompactConfirm => 'Compact conversation';

  @override
  String get sessionContextCompactStarted =>
      'Compacting started. The numbers update when it finishes.';

  @override
  String get sessionContextCompactBusy => 'Wait for the reply to finish.';

  @override
  String get sessionContextMakeupTitle => 'Latest request input';

  @override
  String sessionContextTokens(String count) {
    return '$count tokens';
  }

  @override
  String get sessionContextModelId => 'Model ID';

  @override
  String get demoScreenTitle => 'Try it offline';

  @override
  String get demoScreenSimulated => 'Simulated · nothing is saved';

  @override
  String get demoScreenFinished =>
      'That\'s the whole loop: a prompt, a reply and a reviewed edit.';

  @override
  String sessionContextPercent(String percent) {
    return '$percent %';
  }

  @override
  String sessionDestinationChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files are present.',
      one: '1 changed file is present.',
    );
    return '$_temp0';
  }

  @override
  String get activeContextLoading => 'Reading the active context…';

  @override
  String activeContextAllCount(int count) {
    return 'All messages · $count';
  }

  @override
  String get activeContextChangedTitle => 'This view is outdated';

  @override
  String get activeContextFailedTitle => 'Couldn\'t read the context';

  @override
  String get activeContextIntro =>
      'What the model reads on its next turn, after the latest summary.';

  @override
  String get activeContextEmptyDetail =>
      'Nothing is kept for the next turn yet. Pull down to check again.';

  @override
  String get activeContextWhat => 'active context';

  @override
  String activeContextRowMenu(String type) {
    return 'Actions for $type';
  }

  @override
  String activeContextOpenMessage(String type) {
    return 'Open $type';
  }

  @override
  String activeContextCopyMessage(String type) {
    return 'Copy $type text';
  }

  @override
  String get activeContextMessageId => 'Message id';

  @override
  String activeContextCopyPart(String part) {
    return 'Copy $part';
  }

  @override
  String get sessionNoteDeleting => 'Deleting the note…';

  @override
  String get sessionNoteSaving => 'Saving the note…';

  @override
  String get sessionNoteLoading => 'Reading the saved note…';

  @override
  String get sessionNoteLoadFailed => 'Couldn\'t read the note';

  @override
  String get sessionNoteSaveFailed => 'Couldn\'t save the note';

  @override
  String get sessionNoteFieldLabel => 'Note';

  @override
  String get sessionNoteFieldLocked => 'Refresh the saved note before editing.';

  @override
  String sessionNoteTooLong(int over, int limit) {
    return '$over bytes too long. A note can be up to $limit bytes.';
  }

  @override
  String get sessionNoteWriteFirst => 'Write a note to save it.';

  @override
  String get sessionNoteEmptyUseDelete =>
      'To remove the note, use Delete saved note.';

  @override
  String get sessionRelationsTitle => 'Subagents';

  @override
  String sessionRelationsStopTitle(String title) {
    return 'Stop $title?';
  }

  @override
  String get sessionRelationsStopBody =>
      'The subagent stops its current step. What it already did stays in its conversation.';

  @override
  String get sessionRelationsStopConfirm => 'Stop subagent';

  @override
  String get sessionRelationsFailedTitle => 'Couldn\'t load the subagents';

  @override
  String get sessionRelationsLoading => 'Loading subagents…';

  @override
  String get sessionRelationsStartedFrom => 'Started from';

  @override
  String sessionRelationsSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count subagents',
      one: '1 subagent',
    );
    return '$_temp0';
  }

  @override
  String get sessionRelationsOpenToAnswer => 'open to answer';

  @override
  String get sessionRelationsIdle => 'Idle';

  @override
  String get sessionRelationsThisConversation => 'This conversation';

  @override
  String get sessionRelationsOpening => 'Opening…';

  @override
  String sessionRelationsRowMenu(String title) {
    return 'Actions for $title';
  }

  @override
  String sessionRelationsOpen(String title) {
    return 'Open $title';
  }

  @override
  String sessionRelationsCopyHandoff(String title) {
    return 'Continue $title on computer';
  }

  @override
  String sessionRelationsPin(String title) {
    return 'Pin $title';
  }

  @override
  String sessionRelationsUnpin(String title) {
    return 'Unpin $title';
  }

  @override
  String sessionRelationsStop(String title) {
    return 'Stop $title';
  }

  @override
  String get webSourcesInvalidUrl =>
      'Enter an HTTP or HTTPS address without a user name or password.';

  @override
  String get webSearchFailedTitle => 'Search didn\'t finish';

  @override
  String get webSearchTryAgain => 'Search again';

  @override
  String get webSearchBusy => 'Wait for the search to finish.';

  @override
  String get webSearchQueryHint => 'For example: flutter golden tests';

  @override
  String get webSearchNeedsProvider =>
      'Set up a search provider on this server first.';

  @override
  String get webSearchEmptyDetail => 'Try other words, or paste a link below.';

  @override
  String webSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results',
      one: '1 result',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesAdded => 'Added';

  @override
  String webSourcesAddNamed(String title) {
    return 'Add $title to prompt';
  }

  @override
  String webSourcesRowMenu(String title) {
    return 'Actions for $title';
  }

  @override
  String webSourcesOpenHost(String host) {
    return 'Open $host in browser';
  }

  @override
  String get webSourcesAddLink => 'Add link to prompt';

  @override
  String get webSourcesPasteDetail =>
      'A public address, with an optional excerpt';

  @override
  String webSourcesRemoveNamed(String title) {
    return 'Remove $title from prompt';
  }

  @override
  String get webSearchSearching => 'Searching…';

  @override
  String get webSearchFindingProviders => 'Finding search providers…';

  @override
  String webSourcesDone(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Add $count sources to prompt',
      one: 'Add 1 source to prompt',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesScopeChangedTitle => 'The server changed';

  @override
  String get sessionExportFormatLabel => 'Format';

  @override
  String get sessionExportJsonUnavailable =>
      'This server can\'t send a complete copy. Save the readable transcript instead.';

  @override
  String get sessionExportPrivacyLabel => 'Privacy';

  @override
  String get sessionExportRedactKeeps =>
      'Keeps who wrote each message; the words become placeholders. Not a backup.';

  @override
  String get sessionExportRedactBusy =>
      'Wait until the file is saved to change this.';

  @override
  String get sessionExportRedactChanged =>
      'Open export again from the conversation to change this.';

  @override
  String get sessionExportSaveJson => 'Save complete conversation';

  @override
  String get sessionExportSaveMarkdown => 'Save readable transcript';

  @override
  String get sessionExportSaveFailed =>
      'Couldn\'t write the file on this device. Nothing changed on the server. Try again, or choose another folder.';

  @override
  String get capabilitiesToolsMissingTitle => 'Tools aren\'t listed';

  @override
  String capabilitiesToolsMissingOnServer(String server) {
    return '$server doesn\'t list its tools';
  }

  @override
  String get mcpSetupWhere => 'Where it goes';

  @override
  String get mcpSetupHowItRuns => 'How it runs';

  @override
  String get mcpSetupHeaders => 'Headers';

  @override
  String get mcpSetupAdvanced => 'Advanced';

  @override
  String get mcpSetupAdvancedRemote => 'Sign-in detection and timeout';

  @override
  String get mcpSetupAdvancedLocal => 'Working folder and timeout';

  @override
  String get mcpSetupNoProject => 'Open a project first';

  @override
  String get mcpSetupRuntimeNote =>
      'It connects now and is gone when OpenCode restarts. For a lasting setup, edit the server configuration.';

  @override
  String mcpSetupSaveNamed(String name) {
    return 'Save $name';
  }

  @override
  String mcpSetupAddNamed(String name) {
    return 'Add $name';
  }

  @override
  String get mcpSetupLocationChangedShort => 'The server or project changed';

  @override
  String get mcpSetupSaveFailed => 'Couldn\'t add the MCP server';

  @override
  String get mcpSetupDiscardTitle => 'Discard this MCP server?';

  @override
  String get mcpSetupDiscardBody =>
      'What you typed here isn\'t saved and will be lost.';

  @override
  String get mcpSetupDiscardConfirm => 'Discard server';

  @override
  String get externalAgentsEmptyTitle => 'No outside agents yet';

  @override
  String get externalAgentsEmptyBody =>
      'Add one by its web address. You see what it says about itself before anything is saved.';

  @override
  String get externalAgentsBoundary =>
      'Only the text you send reaches an outside agent. Your projects, files and other conversations stay on this phone.';

  @override
  String get externalAgentsRemovalIncomplete =>
      'Removal didn\'t finish · tap to try again';

  @override
  String externalAgentsRemoveNamed(String name) {
    return 'Remove $name from this phone';
  }

  @override
  String externalAgentsRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get externalAgentsRemoveBody =>
      'Its saved tasks and key leave this phone. Work it already started carries on, and what it keeps stays with it.';

  @override
  String get externalAgentsBusy => 'Wait for the current step to finish';

  @override
  String get externalAgentsAddressHelper =>
      'Its web address, or the address of its Agent Card.';

  @override
  String get externalAgentsCheck => 'Check agent';

  @override
  String get externalAgentsCheckNeedsAddress => 'Type the agent address first';

  @override
  String get externalAgentsStopChecking => 'Stop checking';

  @override
  String get externalAgentsCheckFailedTitle => 'Couldn\'t check this agent';

  @override
  String externalAgentsSaveNamed(String name) {
    return 'Save $name';
  }

  @override
  String get externalAgentsSaveNeedsKey => 'Enter the agent key first';

  @override
  String get externalAgentsAboutLabel => 'What it says about itself';

  @override
  String get externalAgentsUnverified =>
      'The agent describes itself. This app hasn\'t verified who runs it, what it can do or what it costs.';

  @override
  String get externalAgentsUnsupportedTitle => 'Agent not supported';

  @override
  String get externalAgentsUnsupportedBody =>
      'It doesn\'t take text tasks the way this app sends them, or it asks for a sign-in this app doesn\'t support.';

  @override
  String get externalAgentsKeyLabel => 'Agent key';

  @override
  String get externalAgentsKeyHelper =>
      'The key its owner gave you. It stays in this phone\'s secure storage and is sent only to this agent.';

  @override
  String get externalAgentsNoKey =>
      'This agent asks for no key. Don\'t send private information unless you trust it.';

  @override
  String get externalAgentsDetailCard => 'Agent Card';

  @override
  String get externalAgentsDetailEndpoint => 'Endpoint';

  @override
  String get externalAgentsDetailVersion => 'Version';

  @override
  String get externalAgentsDetailConnection => 'Connection';

  @override
  String externalAgentsNewTaskNamed(String name) {
    return 'New task for $name';
  }

  @override
  String externalAgentsReplaceKeyNamed(String name) {
    return 'Replace key for $name';
  }

  @override
  String externalAgentsReplaceKeyTitle(String name) {
    return 'Replace key for $name';
  }

  @override
  String get externalAgentsSaveKey => 'Save key';

  @override
  String get externalAgentsTasksLabel => 'Tasks';

  @override
  String get externalAgentsNoTasksTitle => 'No tasks yet';

  @override
  String get externalAgentsNoTasksBody =>
      'Write a task and read it over before it\'s sent. Opening a sent task checks on it; it is never sent twice.';

  @override
  String get externalAgentsUntitledTask => 'New task';

  @override
  String externalAgentsSendNamed(String name) {
    return 'Send to $name';
  }

  @override
  String externalAgentsReplyNamed(String name) {
    return 'Reply to $name';
  }

  @override
  String get externalAgentsSendNeedsText => 'Write the task first';

  @override
  String get externalAgentsReplyNeedsText => 'Write your reply first';

  @override
  String get externalAgentsSendNote =>
      'Only this text is sent. The agent may use its own services and charge for them; check its terms.';

  @override
  String externalAgentsCheckedAt(String age) {
    return 'Checked with the agent $age ago';
  }

  @override
  String get externalAgentsPullToCheck =>
      'Saved on this phone · pull down to check with the agent';

  @override
  String externalAgentsStopMenu(String name) {
    return 'Ask $name to stop this task';
  }

  @override
  String get externalAgentsStopUnavailable =>
      'Check with the agent first; pull down to refresh';

  @override
  String get externalAgentsForgetMenu => 'Forget this task on this phone';

  @override
  String get externalAgentsForgetTitle => 'Forget this task?';

  @override
  String get externalAgentsForgetBody =>
      'It leaves this phone. Work the agent already started carries on, and its own copy stays with it.';

  @override
  String get externalAgentsForgetConfirm => 'Forget task';

  @override
  String mcpSetupSavedNamed(String name) {
    return 'Saved $name on this server';
  }

  @override
  String mcpSetupSavedNotConnectedBody(String reason) {
    return 'The app didn\'t reconnect afterwards. $reason';
  }

  @override
  String get mcpSetupSavedElsewhere =>
      'The server or project changed after saving, so this page can\'t reconnect for it. Close it and check MCP servers.';

  @override
  String get mcpSetupUnavailableTitle => 'Can\'t add MCP servers';

  @override
  String get mcpSetupUnavailableBody =>
      'It doesn\'t accept new MCP servers from the app. Add them in its configuration on the computer; they then show under MCP servers.';

  @override
  String get commandAuthSheetWorking => 'Asking the server…';

  @override
  String get credentialSheetLoading => 'Reading saved accounts…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return 'Sign in to $provider again from Providers to add an account.';
  }

  @override
  String credentialSheetActions(String label) {
    return 'Actions for $label';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return 'Use $label';
  }

  @override
  String get credentialSheetInUse => 'Already in use';

  @override
  String credentialSheetRenameNamed(String label) {
    return 'Rename $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return 'Remove $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return 'Removes $label from this server. Projects that use it will need another $provider account.';
  }

  @override
  String credentialSheetRenamed(String label) {
    return 'Renamed to $label.';
  }

  @override
  String get credentialSheetLabelEmpty => 'Give the account a name.';

  @override
  String get credentialSheetLabelInvalid =>
      'Use up to 128 characters, without line breaks or control characters.';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return 'Forget the $integration sign-in?';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      'The app stops tracking it on this device. Nothing is cancelled on the server; an unfinished sign-in there expires on its own.';

  @override
  String get toolsScreenLoadFailed => 'Couldn\'t load this model\'s tools';

  @override
  String get toolsScreenSearchWhat => 'tools';

  @override
  String get toolsScreenRegisteredOnly =>
      'Registered on this project · this model can’t call it';

  @override
  String get toolsDetailTakes => 'Takes';

  @override
  String get toolsDetailTakesNothing => 'Takes nothing.';

  @override
  String get toolsDetailRequired => 'required';

  @override
  String get toolsDetailOptional => 'optional';

  @override
  String get toolsDetailTypeText => 'text';

  @override
  String get toolsDetailTypeNumber => 'number';

  @override
  String get toolsDetailTypeYesNo => 'yes or no';

  @override
  String get toolsDetailTypeList => 'list';

  @override
  String get toolsDetailTypeGroup => 'group of values';

  @override
  String get toolsDetailTypeAny => 'any value';

  @override
  String commandsScreenRunsWith(String agent) {
    return 'Runs with $agent';
  }

  @override
  String get commandsScreenMenuLabel => 'Command actions';

  @override
  String commandsScreenCopy(String command) {
    return 'Copy $command';
  }

  @override
  String get referencesScreenLoading => 'Loading references';

  @override
  String get referencesScreenLoadFailed => 'Couldn’t load references';

  @override
  String get referencesScreenIntro =>
      'Folders this project points its agents to. Add one to a prompt and the agent can read what it holds.';

  @override
  String get referencesScreenEmptyBody =>
      'A reference is a folder the project’s agents can read. References set up for this project appear here.';

  @override
  String get referencesScreenMenuLabel => 'Reference actions';

  @override
  String referencesScreenAdd(String mention) {
    return 'Add $mention to the prompt';
  }

  @override
  String referencesScreenShowDetails(String name) {
    return 'Show $name details';
  }

  @override
  String referencesScreenCopyMention(String mention) {
    return 'Copy $mention';
  }

  @override
  String get referencesScreenCopyPath => 'Copy path';

  @override
  String referencesScreenSheetBody(String mention) {
    return 'Write $mention in a prompt and the agent reads this folder for that reply.';
  }

  @override
  String get referencesScreenPathLabel => 'Path';

  @override
  String get skillsScreenLoading => 'Loading skills';

  @override
  String get skillsScreenLoadFailed => 'Couldn’t load skills';

  @override
  String get skillSheetViewLabel => 'How to show the skill';

  @override
  String get skillSheetLocation => 'File';

  @override
  String skillSheetCopyCommand(String command) {
    return 'Copy $command';
  }

  @override
  String get skillSheetCheckConversation =>
      'Check the conversation before trying again.';

  @override
  String get skillSheetSending => 'Adding the skill…';

  @override
  String toolCardDelegatedTo(String agent) {
    return 'Delegated to $agent';
  }

  @override
  String get toolCardExitPassed => 'Passed · exit code 0';

  @override
  String toolCardExitFailed(int code) {
    return 'Failed · exit code $code';
  }

  @override
  String get toolCardRunCommandAgain => 'Run this command again';

  @override
  String get toolCardCopyCommand => 'Copy command';

  @override
  String toolCardLoadImageAgain(String name) {
    return 'Load $name again';
  }

  @override
  String toolCardChangesIn(String file) {
    return 'Changes in $file';
  }

  @override
  String mobileTasksShowAll(int count) {
    return 'Show all $count tasks';
  }

  @override
  String get composerBusyReason => 'Getting your prompt ready…';

  @override
  String get composerToolsTextOnly => 'This server takes text only';

  @override
  String get composerToolCommandsTitle => 'Commands and agents';

  @override
  String get composerToolSavedSubtitle =>
      'Put a prompt you saved back in the draft';

  @override
  String get composerToolSaveForLater => 'Save prompt for later';

  @override
  String get composerToolNothingToSave => 'Type or attach something first';

  @override
  String get composerToolsMore => 'More tools';

  @override
  String get composerReturnedToDraft => 'Returned to your draft';

  @override
  String get promptHistoryIntro => 'Tap a prompt to add it to your draft.';

  @override
  String get promptEditorDiscardChanges => 'Discard changes';

  @override
  String get promptEditorDone => 'Use in draft';

  @override
  String get promptEditorFieldLabel => 'Prompt';

  @override
  String get promptStashDeleted => 'Saved prompt deleted';

  @override
  String get promptStashIntro => 'Newest first · kept on this device';

  @override
  String get promptStashEmptyTitle => 'No saved prompts yet';

  @override
  String get promptStashEmptyBody =>
      'Choose Save prompt for later in the + menu to keep a prompt here.';

  @override
  String get promptStashBusy => 'Wait for the current step to finish';

  @override
  String get promptStashRowActions => 'Saved prompt actions';

  @override
  String get promptStashRestoreToDraft => 'Restore to draft';

  @override
  String get promptStashDeleteAction => 'Delete saved prompt';

  @override
  String get modelShortcutsNextRecent => 'Next recent model';

  @override
  String get modelShortcutsPreviousRecent => 'Previous recent model';

  @override
  String get modelShortcutsNoRecent =>
      'Use another model first to cycle back to it';

  @override
  String get modelShortcutsNoFavorite =>
      'Mark a model as a favorite in the model picker first';

  @override
  String get composerDraftBlockedReason =>
      'Answer the question about this draft first';

  @override
  String get commandLauncherSubtitle =>
      'Run an action in this conversation, or a command from this server';

  @override
  String get teamChatRefusedTitle => 'Task not taken';

  @override
  String get teamChatRefusedRetry => 'Send the task again';

  @override
  String get teamChatGoneTitle => 'Task no longer listed';

  @override
  String get teamChatGoneBody =>
      'It may have been removed on the team\'s computer. The AI Team page lists the tasks it has now.';

  @override
  String get teamChatGoneOpenTeam => 'Open AI Team page';

  @override
  String activityFinishedRow(String when) {
    return 'Finished · $when';
  }

  @override
  String get activityOfflineRequests =>
      'Requests can\'t load while you\'re offline.';

  @override
  String connectionReconnectTo(String server) {
    return 'Reconnect to $server';
  }

  @override
  String get workspaceIsolatedTaskRowDetail =>
      'Works on a separate copy so your main folder stays untouched.';

  @override
  String get workspaceSearchAllDetail =>
      'Every project on this server, archived ones too';

  @override
  String serverDisconnectFrom(String server) {
    return 'Disconnect from $server';
  }

  @override
  String get localAgentStopNamed => 'Stop Claude Code';

  @override
  String get localAgentStartNamed => 'Start Claude Code';

  @override
  String monitorSwitchToTitle(String server) {
    return 'Switch to $server?';
  }

  @override
  String monitorSwitchTo(String server) {
    return 'Switch to $server';
  }

  @override
  String servicesStartNamed(String service) {
    return 'Start $service';
  }

  @override
  String servicesStopNamed(String service) {
    return 'Stop $service';
  }

  @override
  String serverSettingsChangeSignIn(String server) {
    return 'Change sign-in for $server';
  }

  @override
  String serverSettingsAuthBasic(String user) {
    return 'Basic authentication as $user';
  }

  @override
  String get serverSettingsUpdateHint =>
      'Uses OpenCode\'s official installer; restart the server afterwards.';

  @override
  String get settingsHubModelRow => 'Model';

  @override
  String get notifyTurnOnInAndroid => 'Turn on notifications in Android';

  @override
  String get serversAddOtherWays => 'Or connect another way';

  @override
  String get libraryImportAConversation => 'Import a conversation';

  @override
  String runResultsStepsShort(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0';
  }

  @override
  String runResultsStepsShortAtLeast(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'At least $count steps',
      one: 'At least 1 step',
    );
    return '$_temp0';
  }

  @override
  String get runResultsUnderAMinute => 'under a minute';

  @override
  String runResultsMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String runResultsHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String get runResultsHowMade => 'How this was put together';

  @override
  String get runResultsRunIdLabel => 'Run id';

  @override
  String get runResultsAgentLabel => 'Agent';

  @override
  String runResultsCommandFailedExit(int code) {
    return 'Failed · exit $code';
  }

  @override
  String runResultsCommandPassedExit(int code) {
    return 'Passed · exit $code';
  }

  @override
  String get runResultsCommandFailedNoExit => 'Failed · exit not recorded';

  @override
  String get runResultsExitNotRecorded => 'Exit not recorded';

  @override
  String get projectHubHealthSubtitle =>
      'Branch, language services and formatters';

  @override
  String projectHubChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files changed',
      one: '1 file changed',
      zero: 'No changes',
    );
    return '$_temp0';
  }

  @override
  String projectHubTerminalsRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running',
      one: '1 running',
    );
    return '$_temp0';
  }

  @override
  String get projectHubCopyFolderPath => 'Copy folder path';

  @override
  String get terminalScreenNoTerminalThisServer =>
      'This server doesn\'t share a terminal';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return '$server doesn\'t share a terminal';
  }

  @override
  String get terminalScreenNoTerminalWhy =>
      'Terminals open here only on servers that share them.';

  @override
  String get integrationsSignInWaiting => 'Sign-in waiting';

  @override
  String get integrationsSignInMayNotHaveStarted =>
      'Sign-in may not have started';

  @override
  String get integrationsSignInExpired => 'Sign-in expired';

  @override
  String get integrationsSignInFailed => 'Sign-in failed';

  @override
  String get integrationsSignInComplete => 'Signed in · tap to finish';

  @override
  String integrationsFinishSigningIn(String provider) {
    return 'Finish signing in to $provider';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return 'Enter code for $provider';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return 'Cancel $provider sign-in';
  }

  @override
  String get integrationsForgetSignInOnPhone =>
      'Forget this sign-in on this phone';

  @override
  String integrationsSignInActions(String provider) {
    return 'Sign-in actions for $provider';
  }

  @override
  String integrationsAccountCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count accounts',
      one: '1 account',
    );
    return '$_temp0';
  }

  @override
  String get toolsScreenNoBackgroundSubagents => 'no background subagents';

  @override
  String get usageRefreshSpending => 'Refresh spending';

  @override
  String get quotaSetupTrustNote =>
      'Your server operator must install and protect this route at the same origin as OpenCode. Reading it uses this saved server\'s sign-in. Confirm only if you installed or trust that deployment.';

  @override
  String get quotaAlertsRowTitle => 'Quota alerts';

  @override
  String get quotaAlertsRowSupporting => 'Sound, Wi-Fi only and quiet hours';

  @override
  String modelPickerUseModel(String model) {
    return 'Use $model';
  }

  @override
  String get modelPickerUseChosenModel => 'Use model';

  @override
  String get handoffUiComputerCommandLabel => 'Terminal command';

  @override
  String get formRendererDecline => 'Decline this request';

  @override
  String get perfTraceActions => 'Timing report actions';

  @override
  String voiceSetupNotDownloaded(String size) {
    return 'Not downloaded · $size';
  }

  @override
  String get voiceSetupDone => 'Done';

  @override
  String get voiceAllowMicInSettings => 'Allow microphone in Android settings';

  @override
  String sessionContextMessagesSplit(String count, String yours, String agent) {
    return '$count ($yours yours, $agent agent)';
  }

  @override
  String get webSourcesClose => 'Close';

  @override
  String get webSourcesPastedLinks => 'Links you added';

  @override
  String get thisPhoneHostInApp => 'In the app';

  @override
  String get thisPhoneHostTermux => 'In Termux';

  @override
  String get thisPhoneNeedsAttention => 'Needs you';

  @override
  String get thisPhoneSetUp => 'Set up OpenCode';

  @override
  String get thisPhoneStart => 'Start the server';

  @override
  String get thisPhoneStop => 'Stop the server';

  @override
  String get thisPhoneUpdate => 'Update OpenCode';

  @override
  String thisPhoneUpdateDetail(String version) {
    return 'Installs version $version';
  }

  @override
  String get thisPhoneAddTools => 'Add tools';

  @override
  String get thisPhoneInstalled => 'Installed';

  @override
  String get thisPhoneTerminal => 'Open a terminal';

  @override
  String get thisPhoneStorage => 'Storage';

  @override
  String get thisPhoneConnect => 'Connect';

  @override
  String get thisPhoneRemove => 'Remove OpenCode';

  @override
  String get thisPhoneBusy => 'Wait for the current step to finish';

  @override
  String get phoneSetupTermuxAllowHow =>
      'In Termux, paste the copied line and press Enter.';

  @override
  String get phoneSetupTermuxUpdatingTitle => 'Updating this phone';

  @override
  String get phoneSetupTermuxStartingTitle => 'Starting the server';

  @override
  String get phoneSetupTermuxConnecting => 'Connecting';

  @override
  String get phoneSetupTermuxLeaveHint =>
      'You can leave the app. Termux keeps working and this list picks up where it is when you come back.';

  @override
  String get phoneSetupTermuxCost =>
      'About 10–15 minutes the first time, in Termux\'s storage';

  @override
  String removeFromPhoneKeepBody(String size) {
    return 'OpenCode and its tools are removed, freeing about $size.';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured =>
      'OpenCode and its tools are removed.';

  @override
  String get removeFromPhoneKeepConfirm => 'Remove OpenCode, keep my projects';

  @override
  String get removeFromPhoneDeleteAll => 'Delete everything';

  @override
  String get removeFromPhoneDeleteTitle => 'Delete OpenCode and projects?';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return 'OpenCode, its tools and every project on this phone are deleted, freeing about $size. This cannot be undone.';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      'OpenCode, its tools and every project on this phone are deleted. This cannot be undone.';

  @override
  String get thisPhoneManage => 'Manage This phone';

  @override
  String get chatRequestWho => 'The agent';

  @override
  String get chatRequestIfIgnored =>
      'The agent waits until you answer. Nothing is lost.';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more requests are waiting.',
      one: '1 more request is waiting.',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection =>
      'Not connected to the server, so this can’t be answered here.';

  @override
  String get chatRequestAlwaysTitle => 'Always allow these requests';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return 'From now on, $patterns runs without asking you, $context. You can take this back in Settings under Always allowed actions.';
  }

  @override
  String get chatRequestAlwaysOn => 'Always allowed';

  @override
  String get chatRequestDetailTool => 'Tool';

  @override
  String get chatRequestDetailPatterns => 'Requested patterns';

  @override
  String get chatRequestOtherAnswer => 'Something else';

  @override
  String get chatRequestOtherField => 'Your answer';

  @override
  String get formFlowAnsweredElsewhereBody =>
      'This form was answered on another device, so nothing was sent from this phone.';

  @override
  String get approvalsUiPausedDetail =>
      'This phone is not connected. Automatic approval resumes when it reconnects.';

  @override
  String get teamUiHomeRunReviewNext => 'a reviewer checks it next';

  @override
  String teamUiGateRunStoppedTitle(String title) {
    return '$title stopped';
  }

  @override
  String get termuxProcsKindParentGone => 'Parent gone';

  @override
  String get termuxProcsKindNoOwner => 'No owner';

  @override
  String termuxProcsStopOrphans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop $count orphaned helpers',
      one: 'Stop 1 orphaned helper',
    );
    return '$_temp0';
  }

  @override
  String termuxProcsStopOrphansTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop $count orphaned helpers?',
      one: 'Stop the orphaned helper?',
    );
    return '$_temp0';
  }

  @override
  String get teamPhoneStopTeamRow => 'Stop the team on this phone';

  @override
  String get teamPhoneStopTeamRowSupporting =>
      'Agents stop where they are; nothing is lost';

  @override
  String teamUiPhoneWorkingOn(String name) {
    return 'Working on $name';
  }

  @override
  String get teamUiPhoneVersionsLabel => 'Engine versions';

  @override
  String get teamUiPhoneProjectLabel => 'Project folder';

  @override
  String get phoneServerNameInSentence => 'this phone';

  @override
  String get teamAgentWorkUnblockedShort => 'nothing blocking it';

  @override
  String get teamAgentWorkBlockedShort => 'blocked';

  @override
  String get teamAgentStepCommand => 'Ran a command';

  @override
  String get teamAgentStepTest => 'Ran the tests';

  @override
  String get teamAgentStepRead => 'Read a file';

  @override
  String get teamAgentStepEdit => 'Edited a file';

  @override
  String get teamAgentStepSearch => 'Searched the code';

  @override
  String teamAgentStepTool(String tool) {
    return 'Used $tool';
  }

  @override
  String get teamAgentLastCommandLabel => 'Last command';

  @override
  String get termuxStorageOnlyBuildCaches =>
      'Only build caches can be cleaned here';

  @override
  String get termuxStorageWhereItIs => 'Where it is';

  @override
  String termuxStorageCleanBuildCaches(String size) {
    return 'Clean build caches ($size)';
  }

  @override
  String get monitorBackgroundChecks => 'Background checks';

  @override
  String get settingsTryDemo => 'Try the demo';

  @override
  String quotaMonitorCheckNow(String provider, String server) {
    return 'Check $provider on $server now';
  }

  @override
  String get searchArchivedConversations => 'Archived conversations';

  @override
  String get readAloudConsentEngine =>
      'Only offline voices are offered, but the speech engine is separate software with its own privacy terms.';

  @override
  String get readAloudConsentHeard =>
      'People near you may hear it. Reading stops when you leave this conversation or the app.';

  @override
  String get transcriptFindStopSearchingAll => 'Stop searching older messages';

  @override
  String nudgeReviewChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'OpenCode changed $count files. Look them over before you go on.',
      one: 'OpenCode changed 1 file. Look it over before you go on.',
    );
    return '$_temp0';
  }

  @override
  String get voiceComponentTitle => 'Voice typing';

  @override
  String get voiceComponentSummary => 'Speak instead of typing, even offline';

  @override
  String get voiceComponentRemove => 'Remove voice typing';

  @override
  String get voiceComponentRemoveTitle => 'Remove voice typing?';

  @override
  String voiceComponentRemoveBody(String size) {
    return 'Deletes the speech model and frees $size. Voice typing stops working until you add it here again.';
  }

  @override
  String get setupAppStageDownloading => 'Downloading';

  @override
  String get setupAppStageVerifying => 'Checking the download';

  @override
  String kitDiffFilePosition(int index, int count) {
    return '$index of $count';
  }

  @override
  String kitDiffFilePositionSpoken(int index, int count) {
    return 'file $index of $count';
  }

  @override
  String get kitDiffViewed => 'Viewed';

  @override
  String get kitDiffSelectHunk => 'Select these lines';

  @override
  String get kitCapFlagTerminalTitle => 'Terminal';

  @override
  String get kitCapFlagTerminalWhy =>
      'This server doesn\'t open a terminal for you.';

  @override
  String get kitCapFlagToolInventoryTitle => 'Tool list';

  @override
  String get kitCapFlagToolInventoryWhy =>
      'This server doesn\'t list the tools its agent can use.';

  @override
  String get demoScreenReset => 'Reset demo';

  @override
  String get demoScreenLeave => 'Leave demo';

  @override
  String get demoScreenDisclosure =>
      'Everything here is simulated on this device. No server, provider, or files are accessed.';

  @override
  String capabilityScreenIntroWithGaps(String server) {
    return '$server decides what appears in this app. Anything it cannot do is left out of the menus and tabs instead of being shown greyed out. Missing features work on other OpenCode servers.';
  }

  @override
  String activeContextMessageTitle(String role) {
    String _temp0 = intl.Intl.selectLogic(role, {
      'user': 'User message',
      'assistant': 'Assistant message',
      'system': 'System message',
      'synthetic': 'Synthetic message',
      'skill': 'Skill message',
      'shell': 'Shell message',
      'compaction': 'Summary message',
      'change': 'Conversation change',
      'other': 'Message',
    });
    return '$_temp0';
  }

  @override
  String get newConversationLastUsed => 'Last used';

  @override
  String get newConversationSoloDetail => 'You and the assistant';

  @override
  String newConversationSoloDetailIn(String project) {
    return 'You and the assistant, in $project';
  }

  @override
  String get newConversationTeamDetail =>
      'The AI Team plans the work and shares it out';

  @override
  String get newConversationTeamOffDetail =>
      'Off on this server · opens the AI Team to set it up';

  @override
  String newConversationCopyTitle(String project) {
    return 'Separate copy of $project';
  }

  @override
  String newConversationCloudTitle(String machine) {
    return 'On $machine';
  }

  @override
  String get newConversationCloudDetail => 'A cloud machine for this project';

  @override
  String get chatDraftCopy => 'Copy draft';

  @override
  String get reportProblemIntro =>
      'Say what went wrong. You see the whole report before anything leaves this phone.';

  @override
  String get reportProblemDescribeLabel => 'What happened?';

  @override
  String get reportProblemDescribeHint =>
      'What you did, what you expected, what you got instead';

  @override
  String get reportProblemDescribeFirst => 'Say what happened first';

  @override
  String reportProblemAttached(String title) {
    return 'Attached: $title';
  }

  @override
  String get reportProblemIncludeDiagnostics => 'Include recent diagnostics';

  @override
  String reportProblemIncludeDiagnosticsBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count events from this phone',
      one: '1 event from this phone',
    );
    return '$_temp0, with keys, passwords and server addresses removed';
  }

  @override
  String get reportProblemReview => 'Review report';

  @override
  String get reportProblemReviewHint =>
      'Then open it on GitHub, copy it or share it. Screenshots can be added on the GitHub form.';

  @override
  String reportProblemErrorsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count recent errors',
      one: '1 recent error',
    );
    return '$_temp0';
  }

  @override
  String get reportProblemClearFailed =>
      'Couldn\'t clear the saved report. Try again.';

  @override
  String get reportProblemPreviewSubtitle => 'This is exactly what is sent';

  @override
  String get reportProblemPublicNotice =>
      'GitHub issues are public. Nothing is filed until you submit the form there.';

  @override
  String get reportProblemOpenGitHub => 'Open GitHub form';

  @override
  String get reportProblemLinkCopiesDiagnostics =>
      'The diagnostics are too long for the link. Opening the form copies them, so paste them into its Diagnostics field.';

  @override
  String get reportProblemLinkCopiesWhole =>
      'The report is too long for the link. Opening the form copies it, so paste it into the form.';

  @override
  String get reportProblemCopy => 'Copy report';

  @override
  String get reportProblemCopied => 'Report copied';

  @override
  String get reportProblemDiagnosticsCopied =>
      'Diagnostics copied: paste them into the form';

  @override
  String get reportProblemShare => 'Share report';

  @override
  String get reportProblemShareFallback =>
      'Sharing didn\'t open, so the report is copied';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count errors kept',
      one: '1 error kept',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail =>
      'Python, AI Team, voice typing and more';

  @override
  String get phoneSetupTermuxOtherRuntime =>
      'Termux already runs the other OpenCode. Switch it on This phone, then continue setup.';

  @override
  String get undoFromHereNowAction => 'Undo now';

  @override
  String undoFromHereBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'This prompt and the $count messages after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.',
      one:
          'This prompt and the message after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.',
      zero:
          'This prompt is removed, and files go back to how they were before it. You can put it back until you send another prompt.',
    );
    return '$_temp0';
  }

  @override
  String get undoFromHereBodyUnknown =>
      'This prompt and everything after it are removed, and files go back to how they were before it. You can put them back until you send another prompt.';

  @override
  String get undoFromHereFilesLabel => 'Files the agent edited after it';

  @override
  String get undoFromHereNoEdits =>
      'The agent reported no file edits after this prompt.';

  @override
  String get undoneStatus => 'Undone from a prompt';

  @override
  String get undonePutBack => 'Put back';

  @override
  String reviewRevertScreenIntroCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'This prompt and the $count messages after it are hidden. Nothing is final until you choose below.',
      one:
          'This prompt and the message after it are hidden. Nothing is final until you choose below.',
      zero:
          'This prompt is hidden; nothing came after it. Nothing is final until you choose below.',
    );
    return '$_temp0';
  }

  @override
  String reviewRevertKeepConsequenceCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'The hidden prompt and the $count messages after it are deleted',
      one: 'The hidden prompt and the message after it are deleted',
      zero: 'The hidden prompt is deleted',
    );
    return '$_temp0';
  }

  @override
  String addServerConnectedHost(String host) {
    return 'Connected to $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return '$host has not answered yet. A slow network can take a while.';
  }

  @override
  String get addServerCheckCancel => 'Stop checking';

  @override
  String get addServerRemoteHttpAdvice =>
      'A computer on your network needs an https:// address. Tailscale gives it a private one that only your devices can reach.';

  @override
  String get addServerUseTailscale => 'Use Tailscale';

  @override
  String get addServerStepsLabel => 'Add server progress';

  @override
  String get addServerStepKind => 'What runs there';

  @override
  String get addServerStepTailscale => 'Tailscale on this phone';

  @override
  String get addServerStepPair => 'Pair or enter the address';

  @override
  String get addServerStepAddress => 'Address and sign-in';

  @override
  String get addServerStepCheck => 'Checking';

  @override
  String get addServerStepReady => 'Ready';

  @override
  String addServerReadyTitle(String name) {
    return '$name is connected';
  }

  @override
  String get addServerReadyBody =>
      'Its conversations open next. Start one, or pick up one already there.';

  @override
  String addServerReadyOpen(String name) {
    return 'Open $name';
  }

  @override
  String get handoffUiLinkAddTitle => 'Add this server?';

  @override
  String get handoffUiLinkAddServer => 'Add server';

  @override
  String get failedJobReport => 'Report this failure';

  @override
  String get reportProblemJobLog => 'Log of the failed job';

  @override
  String get reportProblemJobLogNone =>
      'No log was kept for this job, so none is attached.';

  @override
  String get sessionsOlderLoadFailed => 'Could not load older conversations.';

  @override
  String get sessionsLoadFailed => 'Could not load your conversations.';

  @override
  String get sessionsListChanged =>
      'The conversation list changed on the server. Refresh it to see older conversations.';

  @override
  String get handoffUiComputerUnsupported =>
      'This server can’t give a command that continues a conversation on a computer.';

  @override
  String get handoffUiComputerChanged =>
      'This conversation moved or its server changed. Go back and try again.';

  @override
  String commandAuthSheetIntro(String provider) {
    return 'Runs this sign-in on your server, not on this phone. Start it only if you trust the server and $provider. You may need to finish steps on the server.';
  }

  @override
  String commandAuthCheckNamed(String provider) {
    return 'Check $provider sign-in now';
  }

  @override
  String credentialRemoveAccountTitle(String provider, String name) {
    return 'Remove $provider account “$name”?';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return 'Remove “$name”';
  }

  @override
  String quotaMonitorOffer(String provider, String server) {
    return 'Alert me about $provider on $server';
  }

  @override
  String quotaMonitorOfferDetail(String percent) {
    return 'Keeps checking in the background, including after a restart, and alerts when use reaches $percent. You can change the percentage once it’s on.';
  }

  @override
  String workspaceChooserBody(String server) {
    return 'Conversations run inside a folder on $server.';
  }

  @override
  String get discoverServicesAliases =>
      'services dev server preview logs run commands processes';

  @override
  String get discoverCloudEnvironmentsAliases =>
      'cloud environments managed workspaces remote sandbox';

  @override
  String promptRestoredWithout(String names) {
    return 'Restored without $names; attach them again before sending';
  }

  @override
  String get promptStashOlderDraftsWaiting =>
      'Some older drafts have not moved here yet. They are kept on this device.';

  @override
  String get promptStashOlderDraftsFull =>
      'Older drafts are waiting to move here. Delete saved prompts to make room.';

  @override
  String quotaAnswerLeft(String percent) {
    return 'About $percent left';
  }

  @override
  String quotaAnswerLeftWeek(String percent) {
    return 'About $percent left this week';
  }

  @override
  String quotaAnswerLeftDays(String percent, int days) {
    return 'About $percent left in this $days-day window';
  }

  @override
  String quotaAnswerLeftHours(String percent, int hours) {
    return 'About $percent left in this $hours-hour window';
  }

  @override
  String quotaAnswerResetsAt(String time) {
    return 'resets at $time';
  }

  @override
  String quotaAnswerResetsOn(String day) {
    return 'resets $day';
  }

  @override
  String get quotaAnswerResetPassed => 'reset time passed, refresh to check';

  @override
  String quotaAnswerFromCodex(String server) {
    return 'From your Codex account on $server';
  }

  @override
  String get quotaAnswerAgeNow => 'Last known reading, from just now';

  @override
  String quotaAnswerAgeMinutes(int minutes) {
    return 'Last known reading, from $minutes min ago';
  }

  @override
  String quotaAnswerAgeHours(int hours) {
    return 'Last known reading, from $hours h ago';
  }

  @override
  String quotaAnswerAgeDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Last known reading, from $days days ago',
      one: 'Last known reading, from yesterday',
    );
    return '$_temp0';
  }

  @override
  String quotaAnswerAlert(String percent) {
    return 'Alert me at $percent used';
  }

  @override
  String get quotaAnswerAlertDetail =>
      'Says so here when a fresh reading reaches it.';

  @override
  String get quotaAnswerAlertSaveFailed =>
      'Couldn’t save this. The alert stays as it was.';

  @override
  String quotaAnswerAttention(String percent) {
    return 'You’ve used $percent or more of a Codex limit.';
  }

  @override
  String quotaAnswerNotConnected(String server) {
    return 'Connect to $server to see what’s left on its Codex account.';
  }

  @override
  String quotaAnswerSignIn(String server) {
    return 'Sign in to Codex on $server';
  }

  @override
  String get quotaAnswerSignInDetail =>
      'What’s left shows here once you’re signed in with ChatGPT.';

  @override
  String get quotaAnswerUnsupported =>
      'This Codex sign-in has no plan limits to show. They show for ChatGPT sign-ins, not API keys.';

  @override
  String get quotaAnswerUnavailable =>
      'Couldn’t read the Codex limits. Check the connection, then refresh.';

  @override
  String get quotaAnswerInvalid =>
      'Codex sent limits this app can’t read. Nothing new is shown.';

  @override
  String get quotaAnswerNoWindows =>
      'Codex reported no limits for this account.';

  @override
  String get quotaAnswerCodexNote =>
      'Read from the Codex account on this server. Other limits, credits and model-specific caps are not included. Missing data is unknown, not unlimited.';

  @override
  String quotaNeedsCollector(String server) {
    return 'Needs the quota collector on $server';
  }

  @override
  String get quotaCollectorHowTo => 'How to get it';

  @override
  String quotaCollectorStepInstall(String server) {
    return 'Ask whoever runs $server to install the quota collector. It needs Node 20 or later.';
  }

  @override
  String get quotaCollectorStepRoute =>
      'They keep the provider sign-in on the server and put the collector behind the same HTTPS address and password as OpenCode.';

  @override
  String get quotaCollectorStepRetry => 'Then come back here and read again.';

  @override
  String get quotaCollectorGuide => 'Open the collector guide';

  @override
  String quotaCollectorFrom(String provider, String server) {
    return '$provider, from the quota collector on $server';
  }

  @override
  String quotaCollectorNoWindows(String provider, String server) {
    return 'The quota collector on $server reported no limits for $provider.';
  }

  @override
  String quotaStopCollector(String server) {
    return 'Stop using the quota collector on $server';
  }

  @override
  String get quotaStopCollectorDetail =>
      'The reading goes away, and Remaining asks you again before the next read.';

  @override
  String get quotaCollectorAddressLabel => 'Collector address';

  @override
  String get quotaPlanLabel => 'Plan';

  @override
  String get quotaReadAtLabel => 'Read at';

  @override
  String get usageSpentToday => 'Spent today';

  @override
  String get usageSpentThirtyDays => 'Spent in the last 30 days';

  @override
  String get usageSpentYear => 'Spent this year';

  @override
  String get usageSpentAllTime => 'Spent in total';

  @override
  String usageSpentPeriod(String period) {
    return 'Spent · $period';
  }

  @override
  String get automationTitle => 'What runs by itself';

  @override
  String get automationSearchAliases =>
      'automation automatic supervision auto approve approvals always allow permissions background watch monitor team level';

  @override
  String get automationSaveFailed =>
      'This choice wasn\'t saved on this phone. The level above is still the one in use; try again.';

  @override
  String get automationSaving => 'Saving…';

  @override
  String get automationTeamLabel => 'How much the AI Team decides alone';

  @override
  String get automationTeamFootnote =>
      'New team tasks start at this level. You can pick another level for one task when you start it.';

  @override
  String get automationWithoutAskingLabel => 'Without asking you';

  @override
  String get automationSavedRulesDetail =>
      'What the agent may run here without asking you.';

  @override
  String phoneSetupStartTermuxProgressHeadline(int percent) {
    return 'Setup in Termux is $percent% done';
  }

  @override
  String get teamPhoneReadyChooseTitle => 'Choose the team\'s project';

  @override
  String get teamPhoneReadyTurningOnTitle => 'Turning on AI Team';

  @override
  String get teamPhoneReadyFailedTitle => 'AI Team didn\'t start';

  @override
  String teamPhoneReadyBody(String project) {
    return 'Give it a first task. It plans the work, shares it between its agents and brings the result back into $project.';
  }

  @override
  String get teamPhoneReadyFirstTask => 'Give the team a first task';

  @override
  String get teamUiStateNotAnsweringPhone =>
      'The app keeps trying while the team starts on this phone.';

  @override
  String get teamUiStateNotAnsweringComputer =>
      'The app keeps trying. Check that your computer is on and online.';

  @override
  String teamUiStateNotAnsweringComputerNamed(String computer) {
    return 'The app keeps trying. Check that $computer is on and online.';
  }

  @override
  String get teamHomeChangeAddress => 'Change address';

  @override
  String get teamHomeTurnOffFailed =>
      'Couldn’t stop the team on this phone, so it is still on. Try again.';

  @override
  String get teamHomeHostStopped => 'Stopped';

  @override
  String get teamHomeHostCooling => 'Cooling down';

  @override
  String get teamHomeHostStoppedForHeat => 'Stopped to cool down';

  @override
  String teamHomeHeatPausedLine(String time) {
    return 'The phone got hot at $time, so the team paused. It carries on by itself once the phone has cooled.';
  }

  @override
  String teamHomeHeatStoppedLine(String time) {
    return 'The phone got very hot at $time, so the team stopped. Its work is kept, and it starts again once the phone has cooled.';
  }

  @override
  String get teamHomePhoneControls => 'Keep it running, stop it or remove it';

  @override
  String teamHomeSpentToday(String usage) {
    return 'Today · $usage';
  }

  @override
  String get teamHomeSpentHint =>
      'The whole team since midnight where it runs, estimated. The server doesn’t report what each task cost.';

  @override
  String get teamHomeSpentPartial =>
      'Some of today’s use has no price yet, so it cost more than this.';

  @override
  String teamIntroNotFound(String server) {
    return 'No AI Team found on $server';
  }

  @override
  String get pluginsTeamOpenPage => 'See the team’s tasks';

  @override
  String get chatErrorModelNotFound => 'The server doesn\'t have this model.';

  @override
  String get chatErrorContextOverflow =>
      'This conversation is too long for the model.';

  @override
  String get chatErrorProviderAuth =>
      'The model provider needs you to sign in again.';

  @override
  String get chatErrorOutputLength =>
      'The reply reached the model\'s length limit.';

  @override
  String get chatErrorContentFilter =>
      'The provider\'s safety filter stopped this reply.';

  @override
  String get chatErrorUnknown => 'The agent stopped because of an error.';

  @override
  String modelPickerThinkingChip(String level) {
    return 'Thinking: $level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return 'Agent: $agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count queued prompts move to Saved prompts',
      one: '1 queued prompt moves to Saved prompts',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count of them may already have been sent',
      one: '1 of them may already have been sent',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Remove and delete $count queued prompts',
      one: 'Remove and delete the queued prompt',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return 'The queued prompts for $name changed, so nothing was removed. Remove it again to see the new count.';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return 'Could not move the queued prompts for $name to Saved prompts, so nothing was removed. Delete some saved prompts or free up storage, then try again.';
  }

  @override
  String get settingsHubGroupAgent => 'Agent';

  @override
  String get settingsHubGroupConversations => 'Conversations';

  @override
  String get settingsHubGroupThisApp => 'This app';

  @override
  String get settingsHubProvidersRow => 'Providers and accounts';

  @override
  String get settingsHubToolsRow => 'Tools';

  @override
  String get settingsHubShowReasoning => 'Show reasoning';

  @override
  String get settingsHubShowTimestamps => 'Show timestamps and usage';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count settings aren\'t available on this server',
      one: '1 setting isn\'t available on this server',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => 'Why';

  @override
  String get toolsHubMcpSubtitle => 'Servers that give the agent more tools';

  @override
  String get toolsHubCatalogSubtitle =>
      'Slash commands, skills, the model\'s tools and references';

  @override
  String get toolsHubExternalAgentsSubtitle =>
      'Agents on other services you can hand work to';

  @override
  String get privacyPolicyTitle => 'Privacy policy';

  @override
  String get aboutHelpSection => 'Tips and shortcuts';

  @override
  String get settingsHubSearchToolsAliases =>
      'tools mcp integrations commands skills references slash capabilities plugins external agents a2a';

  @override
  String get whileAwayActReconnected => 'Reconnected by itself';

  @override
  String get whileAwayActRestarted => 'Restarted by itself';

  @override
  String get whileAwayActHeatPaused => 'AI Team paused while the phone was hot';

  @override
  String get whileAwayActHeatStopped =>
      'AI Team stopped while the phone was hot';

  @override
  String get whileAwayActHeatResumed => 'AI Team resumed once the phone cooled';

  @override
  String get whileAwayActUpdated => 'Update downloaded by itself';

  @override
  String get whileAwayActAllowed => 'Allowed a request by itself';

  @override
  String get whileAwayActQueuedSent => 'Sent your queued message by itself';

  @override
  String get whileAwayActOther => 'Done automatically';

  @override
  String whileAwayUndoFailed(String act) {
    return '$act · Undo didn\'t go through';
  }

  @override
  String get whileAwayDismiss => 'Dismiss';

  @override
  String get whileAwayHistoryUnreadable =>
      'The list of what ran by itself couldn\'t be read, so earlier automatic actions aren\'t shown.';

  @override
  String get whileAwayHistoryUnsaved =>
      'An automatic action couldn\'t be saved to this list. It happened, but it may not be listed.';

  @override
  String whileAwayActUndone(String act) {
    return '$act · Undone';
  }

  @override
  String whileAwayUndoUnconfirmed(String act) {
    return '$act · Undo not confirmed';
  }

  @override
  String whileAwayDismissed(String what) {
    return 'Dismissed “$what”';
  }

  @override
  String get whileAwayMark => 'Done by itself';

  @override
  String get aiteamComponentTurnOff => 'Turn off AI Team';

  @override
  String get aiteamComponentTurnOffTitle => 'Turn off AI Team?';

  @override
  String get aiteamComponentTurnOffBody =>
      'The team stops and stays off until you turn it on again.';

  @override
  String get aiteamComponentTurnOffKept =>
      'Its tasks and settings, and your projects, stay';

  @override
  String get aiteamComponentTurnOffFailed =>
      'AI Team could not be turned off. Try again, or restart the app.';

  @override
  String thisPhoneRemoveTool(String tool) {
    return 'Remove $tool';
  }

  @override
  String thisPhoneRemoveToolTitle(String tool) {
    return 'Remove $tool?';
  }

  @override
  String thisPhoneRemoveToolBody(String size) {
    return 'About $size comes back. You can add it again from Add tools.';
  }

  @override
  String get thisPhoneRemoveToolBodyUnmeasured =>
      'You can add it again from Add tools.';

  @override
  String thisPhoneRemoveToolNeededBy(String tools) {
    return 'Needed by $tools';
  }

  @override
  String get thisPhoneRemoveToolDetail =>
      'Deletes it from this phone. Your projects stay.';

  @override
  String get thisPhoneRemovePythonDetail =>
      'Deletes pip and venv. Python and your projects stay.';

  @override
  String get thisPhoneRemovePythonLost =>
      'pip and venv are deleted, with the packages only they used';

  @override
  String get thisPhoneRemovePythonKept =>
      'Python itself and your projects stay';

  @override
  String get thisPhoneRemoveTeamDetail =>
      'Deletes the team\'s programs, tasks and settings. Projects stay.';

  @override
  String get thisPhoneRemoveTeamLost =>
      'The team\'s programs, tasks and settings are deleted';

  @override
  String get thisPhoneRemoveTeamLostWork =>
      'Team work not yet brought into your projects is lost';

  @override
  String get thisPhoneRemoveTeamKept =>
      'Your project files and their git history stay';

  @override
  String get thisPhoneRemoveVoiceDetail =>
      'Deletes the speech model. Voice typing stops until you add it again.';

  @override
  String get thisPhoneRemoveVoiceLost =>
      'Voice typing stops until you add it again';

  @override
  String get thisPhoneRemoveToolKept => 'Your projects stay';

  @override
  String get removeFromPhoneKeepLost =>
      'Conversations and settings inside OpenCode are deleted';

  @override
  String get removeFromPhoneKeepKept =>
      'Your projects stay and come back when you set up again';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return 'Your projects ($size) stay and come back when you set up again';
  }

  @override
  String get removeFromPhoneDeleteLost =>
      'Project files not saved anywhere else are lost for good';

  @override
  String get productErrorTimedOut =>
      'The server took too long to answer. Try again.';

  @override
  String get productErrorCertificate =>
      'The server\'s security certificate isn\'t trusted, so the app stopped. Check the server address.';

  @override
  String get productErrorSignIn =>
      'The server didn\'t accept the sign-in. Check the password in the server\'s settings.';

  @override
  String get productErrorNotFound =>
      'The server couldn\'t find it. It may have been moved or deleted.';

  @override
  String get productErrorConflict =>
      'It changed on the server in the meantime. Refresh, then try again.';

  @override
  String get productErrorBusy =>
      'The server is busy. Wait a moment, then try again.';

  @override
  String get productErrorRejected =>
      'The server didn\'t accept the request. Try again, or report the problem.';

  @override
  String get productErrorUnknown =>
      'That didn\'t work. Details show what happened. Try again, or report the problem.';

  @override
  String get productErrorUnexpected =>
      'The server\'s answer didn\'t make sense to the app. Try again, or report the problem.';

  @override
  String get productErrorDevice =>
      'Something on this device didn\'t work. Try again.';

  @override
  String get productErrorStorage =>
      'The app couldn\'t read or save a file on this device.';

  @override
  String get productErrorTermux =>
      'Termux didn\'t finish that. Check that Termux is installed and open, then try again.';

  @override
  String get productErrorDetailsLabel => 'Error details';

  @override
  String productErrorServer(int code) {
    return 'The server had a problem (error $code). Try again in a moment.';
  }

  @override
  String get usageBudgetInvalidUsd => 'Enter an amount above 0, like 2.50';

  @override
  String get usageBudgetInvalidTokens =>
      'Enter a whole number of tokens above 0';

  @override
  String get usageBudgetSaveUsd => 'Save USD budget';

  @override
  String get usageBudgetSaveTokens => 'Save token budget';

  @override
  String sessionDestinationMoveWithChanges(String destination) {
    return 'Move to $destination with changes';
  }

  @override
  String sessionDestinationWarpWithChanges(String destination) {
    return 'Move to $destination with a copy of changes';
  }

  @override
  String sessionDestinationMoveTo(String destination) {
    return 'Move to $destination';
  }

  @override
  String sessionDestinationNoChanges(String place) {
    return 'No working changes in $place, so only the conversation moves.';
  }

  @override
  String get settingsBackgroundOffFailed =>
      'Android did not turn background mode off.';

  @override
  String defaultProjectOnlyNotice(String project) {
    return 'Opened $project, the only project on this server.';
  }

  @override
  String defaultProjectLastUsedNotice(String project) {
    return 'Opened $project, the project worked on most recently.';
  }

  @override
  String get defaultProjectChange => 'Choose another project';

  @override
  String defaultReviewScopeNotice(String scope) {
    return 'Showing $scope: it is the view with changes.';
  }

  @override
  String defaultModelNotice(String model) {
    return 'Using $model, this server\'s default model.';
  }

  @override
  String get defaultModelChange => 'Choose another model';

  @override
  String teamControlReceiptSending(String control) {
    return '$control · Sending…';
  }

  @override
  String get teamGateCardRunFailedOpen => 'Choose what to do';

  @override
  String get teamGateCardIfIgnored =>
      'The team waits until you answer. Nothing is lost.';

  @override
  String get teamGateCardIfIgnoredFailed =>
      'The task stays stopped until someone acts on it.';

  @override
  String get teamGateCardIfIgnoredReview =>
      'The work waits for review. Nothing is lost.';

  @override
  String termuxProcsBudget(int count, int limit) {
    return '$count of $limit background processes';
  }

  @override
  String termuxProcsBudgetNote(int limit) {
    return 'Android 12 and later may stop the oldest ones when all apps together run more than $limit.';
  }

  @override
  String termuxProcsBudgetOver(int limit) {
    return 'More than $limit: Android may stop the oldest of these at any time.';
  }

  @override
  String get termuxProcsLoadFailedBody =>
      'Termux did not answer. Open Termux, then try again.';

  @override
  String get termuxProcsRefreshFailed =>
      'Couldn\'t read the list again, so it shows the last reading.';

  @override
  String get termuxProcsStopFailed =>
      'Couldn\'t stop it. Try again, or stop it from Termux.';

  @override
  String get termuxProcsKindOpenCode => 'OpenCode server';

  @override
  String get termuxProcsKindAiTeam => 'AI Team';

  @override
  String get termuxProcsKindClaudeCode => 'Claude Code';

  @override
  String get termuxProcsKindDevService => 'Dev service';

  @override
  String get termuxProcsKindTerminal => 'Terminal';

  @override
  String get termuxProcsKindHelper => 'Helper';

  @override
  String get termuxProcsKindHostApp => 'Termux app';

  @override
  String get termuxProcsBusy => 'Busy';

  @override
  String get termuxProcsIdle => 'Idle';

  @override
  String termuxProcsRunningFor(String elapsed) {
    return 'running $elapsed';
  }

  @override
  String termuxProcsStopKindBody(String names) {
    return '$names: each gets a polite stop, then a forced one after 5 seconds.';
  }

  @override
  String termuxProcsStopKind(int count, String things) {
    return 'Stop all $count $things';
  }

  @override
  String termuxProcsStopKindTitle(int count, String things) {
    return 'Stop all $count $things?';
  }

  @override
  String get termuxProcsKindsAiTeam => 'AI Team processes';

  @override
  String get termuxProcsKindsClaudeCode => 'Claude Code processes';

  @override
  String get termuxProcsKindsDevServices => 'dev services';

  @override
  String get termuxProcsKindsTerminals => 'terminals';

  @override
  String get termuxProcsKindsHelpers => 'helpers';

  @override
  String get termuxProcsStopDevRestart =>
      'The next build starts them again when it needs them.';

  @override
  String get termuxProcsAboutClaudeCode =>
      'Claude Code, the coding agent. Stopping it ends the answer it is writing.';

  @override
  String get termuxProcsAboutTerminal =>
      'A terminal. Stopping it closes it and whatever runs in it.';

  @override
  String get termuxProcsAboutHostApp =>
      'The Termux app itself. It is not stopped from here.';

  @override
  String get termuxProcsAverageCpu => 'Average processor use';

  @override
  String get termuxProcsCpuTime => 'Processor time';

  @override
  String get consentBatteryTitle => 'Keep the server running?';

  @override
  String get consentBatteryBody =>
      'Android may stop the server on this phone while the app is closed. Allow background running and Android asks you to confirm.';

  @override
  String get consentBatteryAllow => 'Allow background running';

  @override
  String get consentMakerTitle => 'Restart the server automatically?';

  @override
  String consentMakerBody(String maker) {
    return '$maker phones stop apps that aren\'t allowed to start by themselves, and the server then stays off. Turn on auto-start for this app on the screen that opens.';
  }

  @override
  String get consentMakerBodyUnnamed =>
      'Some phones stop apps that aren\'t allowed to start by themselves, and the server then stays off. Turn on auto-start for this app on the screen that opens.';

  @override
  String get consentMakerAllow => 'Open auto-start settings';

  @override
  String get consentNotNow => 'Not now';

  @override
  String get consentSaveFailed =>
      'Your answer couldn\'t be saved on this phone, so nothing was changed. Try again.';

  @override
  String get consentStorageFailed =>
      'Your earlier answers on this server couldn\'t be read, so the app won\'t ask them again for now. Reopen this page to try again.';

  @override
  String get consentGroupLabel => 'Your answers';

  @override
  String get consentRowBattery => 'Background running';

  @override
  String get consentRowMaker => 'Start again by itself';

  @override
  String get consentRowNeedsYou => 'Tell me when the agent needs me';

  @override
  String get consentRowAlwaysAllow => 'Always allow offers';

  @override
  String get consentWhyBattery =>
      'Android may stop the server on this phone while the app is closed.';

  @override
  String get consentWhyMaker =>
      'This phone may not start the server again after it stops.';

  @override
  String get consentWhyNeedsYou =>
      'You won\'t get a notification when the agent waits for your answer.';

  @override
  String get consentWhyUnfinished =>
      'The question closed before you answered. Tap to answer now.';

  @override
  String get consentAllowedSystem =>
      'The phone\'s own setting decides. Tap to check it or turn it off.';

  @override
  String get consentAllowedNeedsYou => 'Tap to change it in Notifications.';

  @override
  String get consentWhyAlwaysAllow =>
      'Still asked each time. Tap to be offered Always allow again.';

  @override
  String get consentValueAllowed => 'Allowed';

  @override
  String get consentValueDeclined => 'Declined';

  @override
  String get consentValueUnanswered => 'Not answered';

  @override
  String consentValueDeclinedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count declined',
      one: '1 declined',
    );
    return '$_temp0';
  }

  @override
  String get consentAlwaysAgainTitle => 'Ask to always allow?';

  @override
  String get consentAlwaysAgainBody =>
      'After 3 more identical asks, the app offers to always allow them again. Nothing is allowed until you say so.';

  @override
  String get consentAlwaysAgainConfirm => 'Offer again';

  @override
  String consentAlwaysAllowQuestion(String what) {
    return 'Asked 3 times. Always allow $what?';
  }

  @override
  String get consentAlwaysAllowDecline => 'Keep asking';

  @override
  String get consentAlwaysAllowFailed =>
      'The server didn\'t save Always allow. The request is still waiting; try again or answer it once.';

  @override
  String get consentAlwaysAllowTitle => 'Always allow this request?';

  @override
  String get consentNeedsYouAllow => 'Turn on notifications';

  @override
  String get bootstrapOpeningTitle => 'Opening…';

  @override
  String get bootstrapOpeningBody => 'Reading your saved servers.';

  @override
  String get bootstrapFailedTitle => 'Can\'t read saved servers';

  @override
  String get bootstrapFailedBody =>
      'If your phone just restarted, unlock it, then try again.';

  @override
  String get shareFailedLine =>
      'Shared text saved · couldn\'t open a conversation';

  @override
  String get shareFailedAgainLine =>
      'Still couldn\'t open a conversation · shared text saved';

  @override
  String get shareFailedCopy => 'Copy shared text';

  @override
  String get shareFailedDiscard => 'Discard shared text';

  @override
  String get shareDiscarded => 'Shared text discarded';

  @override
  String get shareConnectionChanged =>
      'The server or project changed while it opened. Try again.';

  @override
  String get appNewConversationFailed => 'Couldn\'t start a new conversation';

  @override
  String get rootPhoneServerStartFailed =>
      'OpenCode on this phone didn\'t start';

  @override
  String get connectionFailureLocalCodexBody =>
      'A local Codex listener should answer on this phone, but nothing did.';

  @override
  String get connectionFailureRemoteCodexBody =>
      'Nothing answered at the Codex endpoint.';

  @override
  String get connectionFailureLoopbackBody =>
      'The app looked for a server running on this phone and got no answer. Start that server, or reconnect the tunnel that brings one here, then try again.';

  @override
  String get connectionFailureTimedOutBody =>
      'Something is at that address, but it did not reply. Usually the network in between, not the server.';

  @override
  String get connectionFailureNothingAnsweredBody =>
      'Nothing answered. Either the server is not running, or this phone cannot reach its address.';

  @override
  String get connectionFailureUnknownBody =>
      'The connection failed. What went wrong is under Details.';

  @override
  String get connectionFailureTailnetCheck =>
      'This is a Tailscale address: is Tailscale on, on this phone and on the server?';

  @override
  String get teamHomeSpentHistoryMissing =>
      'Part of today’s history is missing, so it cost more than this.';

  @override
  String get teamHomeSpentNotRecording =>
      'The team isn’t counting new use right now.';

  @override
  String get teamRunCostUnreported =>
      'Not reported for one task. The AI Team page shows today’s estimate for the whole team.';

  @override
  String get teamHomeUpkeepTitle => 'Team upkeep';

  @override
  String get teamHomeUpkeepPatrol => 'Patrol';

  @override
  String get teamHomeUpkeepChore => 'Chore';

  @override
  String teamHomeUpkeepGroup(int count, String kind, String state) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$kind ×$count · $state',
      one: '$kind · $state',
    );
    return '$_temp0';
  }

  @override
  String get teamAgentLooksAfterTeam => 'whole team';

  @override
  String get teamAgentLooksAfterWatchdog => 'watchdog';

  @override
  String get teamAgentLooksAfterWorkers => 'workers';

  @override
  String get servicesStopConfirm => 'Stop service';

  @override
  String get servicesRestartConfirm => 'Restart service';

  @override
  String get managedWorkspacesRemoveConfirm => 'Remove environment';

  @override
  String managedWorkspacesCreateIn(String provider) {
    return 'In $provider';
  }

  @override
  String get voiceAutoSetupTitle => 'Voice typing';

  @override
  String get voiceAutoSetupChecking => 'Checking what this phone can run';

  @override
  String get voiceAutoSetupOffer =>
      'Speak instead of typing. Speech turns into text on this phone, even offline, and audio never leaves it. It needs a one-time download.';

  @override
  String voiceAutoSetupPicked(String model) {
    return '$model speech model, picked for this phone\'s memory';
  }

  @override
  String get voiceAutoSetupMobileData =>
      'You\'re on mobile data. This download counts against your data plan.';

  @override
  String get voiceAutoSetupMaybeMetered =>
      'This connection may count against a data plan.';

  @override
  String voiceAutoSetupDownload(String size) {
    return 'Download $size';
  }

  @override
  String voiceAutoSetupDownloadMobile(String size) {
    return 'Download $size on mobile data';
  }

  @override
  String get voiceAutoSetupOtherModel => 'Choose another speech model';

  @override
  String get voiceAutoSetupNotified =>
      'Progress also shows in your notifications. Listening starts when it\'s done.';

  @override
  String get voiceAutoSetupStartsAfter => 'Listening starts when it\'s done.';

  @override
  String get voiceAutoSetupReady => 'The speech model is on this phone.';

  @override
  String get voiceAutoSetupChooseModel => 'Choose a speech model';

  @override
  String get voiceAutoSetupUnknownMemory =>
      'This phone didn\'t say how much memory it has, so no speech model was picked.';

  @override
  String get voiceAutoSetupOffline =>
      'No internet connection. Connect, then try again.';

  @override
  String get voiceAutoSetupBusy => 'A speech model is already downloading.';

  @override
  String get voiceAutoSetupShowDownload => 'Show the download';

  @override
  String get voiceAutoSetupNoCapture =>
      'This phone can\'t record speech for voice typing.';

  @override
  String get voiceAutoSetupDetailFiles => 'Files';

  @override
  String get voiceAutoSetupDetailSize => 'Exact size';

  @override
  String get voiceAutoSetupDetailMemory => 'Memory';

  @override
  String voiceAutoSetupDetailMemoryValue(int required, int available) {
    return 'Needs $required MB; this phone has $available MB';
  }

  @override
  String get terminalScreenEmptyTitle => 'No terminals yet';

  @override
  String terminalScreenEmptyBody(String project) {
    return 'Start one in $project.';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => 'Start one in this project.';

  @override
  String get integrationsProvidersExplanation =>
      'The model providers this server can use. Connect one to start chatting.';

  @override
  String get integrationsResourcesExplanation =>
      'Files and data that connected MCP servers give the agent.';

  @override
  String externalAgentsStopTaskTitle(String task) {
    return 'Stop “$task”?';
  }

  @override
  String externalAgentsStopTaskConfirm(String agent) {
    return 'Ask $agent to stop';
  }

  @override
  String get externalAgentsStopTaskKeep => 'Keep running';

  @override
  String toolsDetailMenu(String tool) {
    return '$tool actions';
  }

  @override
  String kitDurationHours(int hours) {
    return '$hours h';
  }

  @override
  String kitDurationHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String kitDurationDays(int days) {
    return '$days d';
  }

  @override
  String kitDurationDaysHours(int days, int hours) {
    return '$days d $hours h';
  }

  @override
  String kitToolFor(String duration) {
    return 'for $duration';
  }

  @override
  String kitSinceWaitingForLong(String duration) {
    return 'Waiting $duration';
  }

  @override
  String get teamChatLeadRoutedIt => 'Sent it to the workers';

  @override
  String get teamChatLeadStartingIt => 'Worker started';

  @override
  String get teamChatLeadClaimedWorkerIt => 'Worker took the task';

  @override
  String get teamChatLeadPushedIt => 'Its changes are on a branch';

  @override
  String get teamChatLeadReviewIt => 'Handed it to review';

  @override
  String get teamChatLeadMergedIt => 'Merged it';

  @override
  String get teamChatLeadStepFailedIt => 'It failed';

  @override
  String get teamChatLeadStepCancelledIt => 'It was cancelled';

  @override
  String teamChatNowNoProgress(String elapsed) {
    return 'No progress for $elapsed';
  }

  @override
  String teamChatNoProgressBody(String name, String time) {
    return '$name hasn\'t moved this task since $time. Nudge it to carry on, restart it, or report the problem.';
  }

  @override
  String teamChatNoProgressBodyNoControls(String name, String time) {
    return '$name hasn\'t moved this task since $time. This server can\'t nudge or restart it from here; report the problem or check the team\'s computer.';
  }

  @override
  String get teamChatNoProgressReport => 'Report the problem';

  @override
  String teamChatNoProgressReportTitle(String elapsed) {
    return 'No progress for $elapsed';
  }

  @override
  String get teamTaskDetailsReported => 'What the server reported';

  @override
  String get kitToolOpenDetails => 'Open its details';

  @override
  String get teamStartRunKeepInBacklog => 'Keep in backlog';

  @override
  String workRunawayStopped(String helper) {
    return 'Stopped $helper';
  }

  @override
  String workRunawayStopFailed(String helper) {
    return 'Couldn\'t stop $helper. Try again, or stop it from Termux.';
  }

  @override
  String get serverSettingsUpdateCommandsDetail =>
      'Run them in a terminal on the server\'s computer; this app can\'t update it.';

  @override
  String get serverSettingsUpdateCommandsCopied =>
      'Copied. Run them in a terminal on the server\'s computer.';

  @override
  String hostServiceTitle(String server) {
    return 'Linux service for $server';
  }

  @override
  String hostServiceIntro(String server) {
    return 'These commands run on $server\'s computer; copy each into a terminal there.';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return 'To do · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList =>
      'OpenCode can’t list the devices on your tailnet.';

  @override
  String get productErrorStagedRevert =>
      'Review the staged revert before sending this queued prompt.';

  @override
  String teamWatchComposerHint(String name) {
    return 'Message $name…';
  }

  @override
  String get teamWatchComposerHintWorker => 'Message the worker…';

  @override
  String get teamWatchComposerHintAgent => 'Message this agent…';

  @override
  String teamWatchAbout(String name) {
    return 'About $name';
  }

  @override
  String teamWatchAboutRole(String role) {
    return 'About the $role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return 'The queued prompts for $name cannot be read. The server and its queued prompts were kept. Try removing it again after the queue can be read.';
  }

  @override
  String get bootstrapStartFresh => 'Start fresh';

  @override
  String get bootstrapStartFreshTitle => 'Remove saved sign-ins?';

  @override
  String get bootstrapStartFreshBody =>
      'This removes saved passwords and connection tokens from this phone and clears the selected server. Your saved servers, queued prompts and drafts are kept.';

  @override
  String get bootstrapStartFreshConfirm => 'Remove saved sign-ins';

  @override
  String get bootstrapResettingTitle => 'Removing saved sign-ins…';

  @override
  String get bootstrapResettingBody => 'Keep the app open while this finishes.';

  @override
  String get bootstrapResetFailedTitle => 'Sign-in reset failed';

  @override
  String get bootstrapResetFailedBody =>
      'Some saved sign-ins could not be removed. Try again.';

  @override
  String get workStalled => 'Stalled';

  @override
  String get teamNowActivityPlanning => 'Waiting for a plan';

  @override
  String get teamNowActivityWaitingForWorker => 'Waiting for a worker';

  @override
  String get teamNowActivityStartingWorker => 'Starting a worker';

  @override
  String get teamNowActivityWorking => 'Working on your task';

  @override
  String get teamNowActivityReviewing => 'Reviewing the changes';

  @override
  String get teamNowActivityNeedsYou => 'Waiting for your answer';

  @override
  String get teamNowActivityDelayed => 'Taking longer than expected';

  @override
  String get teamNowActivityUnconfirmed => 'Request not confirmed';

  @override
  String get teamNowActivityRefused => 'Request not accepted';

  @override
  String get teamNowActivityUnavailable => 'The team isn\'t answering';

  @override
  String get teamNowActivityCompleted => 'Finished';

  @override
  String get teamNowActivityFailed => 'Could not finish';

  @override
  String get teamNowActivityCancelled => 'Stopped';

  @override
  String get teamNowReasonNoPlanReported =>
      'No plan has been reported yet. The reason is unknown.';

  @override
  String get teamNowReasonNoWorkerReported =>
      'No worker has been reported yet.';

  @override
  String get teamNowReasonWorkerStarting =>
      'The worker has started but hasn\'t begun the task.';

  @override
  String teamModelRowTitle(String model) {
    return 'Workers use $model';
  }

  @override
  String get teamModelDefault => 'Same as this phone\'s OpenCode';

  @override
  String get teamModelDefaultHint =>
      'Uses the model this phone\'s OpenCode is set to.';

  @override
  String get teamModelChange =>
      'Change. Takes effect the next time a worker starts.';

  @override
  String get teamModelSheetTitle => 'Model for the workers';

  @override
  String get teamModelSheetNote =>
      'Only models this phone\'s OpenCode can use. A worker that is already running keeps its model.';

  @override
  String get teamModelNoneLoaded =>
      'This phone\'s models have not loaded yet. Close this and try again in a moment.';

  @override
  String get teamModelFailed =>
      'Could not change the model. The team keeps the one it had.';

  @override
  String get teamNowReasonWorkerPreparing =>
      'The worker is being set up: its folder is made and its program is starting.';

  @override
  String get teamNowReasonWorkerRunning =>
      'The worker\'s program is running. The team has not reported the task reaching it yet.';

  @override
  String get teamNowReasonWorkerTaskDelivered =>
      'The task has reached the worker. It is reading it before it begins.';

  @override
  String teamNowLastStart(String duration) {
    return 'took $duration last time';
  }

  @override
  String get teamUiHostPhraseBusyStartingWorker => 'Busy starting a worker';

  @override
  String get teamNowReasonWorkInProgress => 'The task is being worked on.';

  @override
  String get teamNowReasonReviewPending =>
      'Review or completion is still pending.';

  @override
  String get teamNowReasonAnswerNeeded =>
      'The team is waiting for your answer.';

  @override
  String get teamNowReasonWorkerCouldNotStart =>
      'The worker couldn\'t stay running.';

  @override
  String get teamNowReasonProviderLimit =>
      'The AI service reported a usage limit.';

  @override
  String get teamNowReasonWorkTakingLonger =>
      'The work is taking longer than expected.';

  @override
  String get teamNowReasonConfirmationMissing =>
      'We can\'t confirm the request arrived. Check before sending it again.';

  @override
  String get teamNowReasonRequestRefused => 'The request was not accepted.';

  @override
  String get teamNowReasonConnectionUnavailable =>
      'Progress can\'t be checked while disconnected.';

  @override
  String get teamNowReasonCauseUnknown =>
      'The reason is unknown. Check what the team is doing.';

  @override
  String get teamNowWhyPlanning =>
      'The planner turns your task into steps. This conversation follows the task as soon as the team lists them. Stopping following it here doesn\'t cancel it on the team\'s computer.';

  @override
  String get teamNowWhyWaitingForWorker =>
      'The team looks for new work regularly and starts a worker for it when one is free.';

  @override
  String get teamNowWhyStartingWorker =>
      'A new worker makes its own copy of the project and starts its program before it reads the task. That is the slow part on a phone, and the stage above is what the team reports.';

  @override
  String get teamNowWhyWorking =>
      'The worker makes the changes on its own copy, then hands them to review.';

  @override
  String get teamNowWhyReviewing =>
      'A reviewer checks the changes before they are merged.';

  @override
  String get teamNowWhyUnconfirmed =>
      'The app sent the task but didn\'t hear back. Sending it again could start it twice, so look at the planner first.';

  @override
  String get teamNowWhyWorkerCouldNotStart =>
      'The worker stopped while it was starting. Its conversation may say why.';

  @override
  String get teamNowWhyProviderLimit =>
      'The AI service limits how much can be used in a period. Work continues when the limit resets, or you can stop the task.';

  @override
  String get teamNowWhyWorkTakingLonger =>
      'Large tasks can take a while. Watching the worker shows whether it is still moving.';

  @override
  String get teamNowWhyCauseUnknown =>
      'What the team reports doesn\'t say why it is waiting.';

  @override
  String get teamNowWhyHide => 'Hide';

  @override
  String get teamNowNextPlan => 'Next: the team lists the steps';

  @override
  String get teamNowNextWorker => 'Next: a worker starts';

  @override
  String get teamNowNextWork => 'Next: the worker begins the task';

  @override
  String get teamNowNextReview => 'Next: the changes are reviewed';

  @override
  String get teamNowNextFinish => 'Next: the task finishes';

  @override
  String get teamNowWatchPlanner => 'Watch the planner';

  @override
  String get teamNowDismissRequest => 'Stop following this request';

  @override
  String teamNowUsuallyWithin(String duration) {
    return 'usually within $duration';
  }

  @override
  String teamNowWatchAgent(String name) {
    return 'Watch $name';
  }

  @override
  String get teamNowNotStartingLine => 'The team isn\'t starting a worker';

  @override
  String get aiSetupTitle => 'AI setup';

  @override
  String get aiSetupEntryDetail =>
      'Models, tools and suggestions for this server';

  @override
  String get aiSetupRefresh => 'Read this server\'s setup again';

  @override
  String get aiSetupLoading => 'Reading this server\'s setup…';

  @override
  String get aiSetupReviewOnly =>
      'Review only. Changes are made on the server for now.';

  @override
  String get aiSetupUnsupportedTitle => 'AI setup isn\'t available';

  @override
  String get aiSetupUnsupportedBody =>
      'This server doesn\'t share its configuration with the app. Set up its models and tools on the server itself.';

  @override
  String get aiSetupSignInTitle => 'Sign-in needed';

  @override
  String aiSetupSignInBody(String server) {
    return '$server didn\'t accept the saved sign-in, so its setup can\'t be read.';
  }

  @override
  String get aiSetupErrorTitle => 'Couldn\'t read setup';

  @override
  String get aiSetupErrorBody =>
      'The server didn\'t answer as expected. Try again, or check the server on its settings page.';

  @override
  String get aiSetupTryAgain => 'Try again';

  @override
  String get aiSetupOfflineTitle => 'You\'re offline';

  @override
  String aiSetupOfflineBody(String server) {
    return 'Reconnect to $server to read its setup.';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return 'Offline. This is $server\'s setup as last read; it updates when you reconnect.';
  }

  @override
  String get aiSetupEmptyTitle => 'Nothing set up yet';

  @override
  String get aiSetupEmptyBody =>
      'This server runs on its defaults, with no model chosen and no tool servers. Changes are made on the server for now.';

  @override
  String get aiSetupSuggestionsLabel => 'Suggestions';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return 'Sign in to $name';
  }

  @override
  String get aiSetupSuggestSignInDetail =>
      'Its tools stay off until someone signs in to it on the server.';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return 'Check $name\'s settings';
  }

  @override
  String get aiSetupSuggestFixDetail =>
      'It failed to start. Fix its entry in the server\'s configuration, then restart the server.';

  @override
  String get aiSetupSuggestModelTitle => 'Choose a default model';

  @override
  String get aiSetupSuggestModelDetail =>
      'No model is set, so new conversations use the server\'s own pick. Set “model” in the server\'s configuration.';

  @override
  String get aiSetupSuggestToolsTitle => 'Add tool servers';

  @override
  String get aiSetupSuggestToolsDetail =>
      'No MCP servers are set up. Add one in the server\'s configuration to give the agent more tools.';

  @override
  String get aiSetupToolsLabel => 'Tool servers';

  @override
  String get aiSetupToolsTerm =>
      'MCP servers give the agent extra tools. Each shows whether it is working now.';

  @override
  String get aiSetupToolConnected => 'Connected';

  @override
  String get aiSetupToolWaiting => 'Waiting';

  @override
  String get aiSetupToolOff => 'Off';

  @override
  String get aiSetupToolFailed => 'Failed';

  @override
  String get aiSetupToolNeedsSignIn => 'Needs sign-in';

  @override
  String get aiSetupToolUnknown => 'Unknown';

  @override
  String get aiSetupEffectiveLabel => 'Settings in effect';

  @override
  String get aiSetupEffectiveTerm =>
      'What this server\'s conversations use, after combining its configuration files.';

  @override
  String get aiSetupModel => 'Model';

  @override
  String get aiSetupServerDefault => 'Not set: the server picks';

  @override
  String get aiSetupSmallModel => 'Small model';

  @override
  String get aiSetupDefaultAgent => 'Default agent';

  @override
  String get aiSetupProviders => 'Providers';

  @override
  String get aiSetupPermissions => 'Permissions';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules',
      one: '1 rule',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => 'All settings';

  @override
  String get aiSetupSourcesLabel => 'Configuration sources';

  @override
  String get aiSetupSourcesTerm =>
      'Listed from lowest to highest priority, as the server reports them. The app doesn\'t combine them.';

  @override
  String get aiSetupNoSources => 'No configuration files';

  @override
  String get aiSetupNoSourcesDetail => 'This server runs on its defaults.';

  @override
  String aiSetupSourceUnnamed(String type) {
    return 'Source without a file ($type)';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position. Sets $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position. Sets nothing';
  }

  @override
  String get aiSetupAllSources => 'All sources';

  @override
  String get integrationsPageLoadFailed => 'Could not load this page';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · Refreshing';
  }

  @override
  String get kitLastKnownHint =>
      'Saved from last time. They open once the live list loads.';

  @override
  String get kitTranscriptExcerptHint =>
      'Saved from last time. The conversation opens fully once it loads.';

  @override
  String get lastKnownUpdatedJustNow => 'Updated just now';

  @override
  String lastKnownUpdatedAgo(String ago) {
    return 'Updated $ago';
  }

  @override
  String serverRowQueuedWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts waiting to send',
      one: '1 prompt waiting to send',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Move $count waiting prompts to $destination',
      one: 'Move 1 waiting prompt to $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveTitle => 'Move queued prompts';

  @override
  String queuedMoveSubtitle(String source) {
    return 'From $source';
  }

  @override
  String get queuedMovePromptsLabel => 'Prompts';

  @override
  String get queuedMoveConversationLabel => 'Conversation';

  @override
  String get queuedMoveNewConversation => 'New conversation';

  @override
  String queuedMoveQueuedAt(String time) {
    return 'Queued $time';
  }

  @override
  String queuedMoveFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveBlockedUncertain(String source) {
    return 'May already have been sent. Check it on $source first.';
  }

  @override
  String queuedMoveBlockedFile(String source) {
    return 'Has a file only $source can open';
  }

  @override
  String get queuedMoveBlockedMentions =>
      'Hiding a password in it would break its agent mentions';

  @override
  String queuedMoveHidesSecrets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Passwords and keys in $count prompts stay hidden',
      one: 'Passwords and keys in 1 prompt stay hidden',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUsesCurrentModel(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts use the model chosen on $destination',
      one: '1 prompt uses the model chosen on $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveAction(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Move $count prompts to $destination',
      one: 'Move 1 prompt to $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveChooseOne => 'Choose at least one prompt';

  @override
  String queuedMoveNoneLeft(String source) {
    return 'Nothing waits for $source any more';
  }

  @override
  String queuedMoveFailedDisconnected(String destination) {
    return '$destination disconnected, so nothing moved. Connect to it and try again.';
  }

  @override
  String queuedMoveFailedConversationGone(String destination) {
    return 'That conversation is no longer on $destination, so nothing moved. Choose another one.';
  }

  @override
  String queuedMoveFailedNothing(String source) {
    return 'These prompts no longer wait for $source, so nothing moved.';
  }

  @override
  String queuedMoveFailedNewConversation(String destination) {
    return 'Could not start a new conversation on $destination, so nothing moved. Try again or choose an existing conversation.';
  }

  @override
  String queuedMoveFailedNotSaved(String source) {
    return 'Could not save the move, so nothing moved. The prompts still wait for $source.';
  }

  @override
  String queuedMoveDone(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count prompts moved to $destination',
      one: '1 prompt moved to $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveDonePartial(int moved, int total, String destination) {
    return 'Moved $moved of $total prompts to $destination. The rest no longer waited.';
  }

  @override
  String queuedMoveUndoNone(String destination) {
    return 'The prompts already started sending on $destination, so they stay there.';
  }

  @override
  String queuedMoveUndoPartial(int count, String destination, String source) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count prompts already started sending on $destination and stay there. The rest wait for $source again.',
      one:
          '1 prompt already started sending on $destination and stays there. The rest wait for $source again.',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUndoFailed(String destination) {
    return 'Could not put the prompts back. They stay on $destination.';
  }

  @override
  String workStalledSince(String time) {
    return 'Stalled since $time';
  }

  @override
  String attentionOnServer(String server) {
    return 'on $server';
  }

  @override
  String get attentionTeamTask => 'Team task';

  @override
  String attentionChecksOff(String servers) {
    return 'Not checking $servers';
  }

  @override
  String get attentionChecksOffDetail =>
      'Their requests don\'t show here. Turn on checks in Notifications.';

  @override
  String attentionUnchecked(String server) {
    return 'Couldn\'t check $server';
  }

  @override
  String get attentionUncheckedDetail =>
      'Requests waiting there may be missing here.';

  @override
  String attentionUncheckedSince(String time) {
    return 'Last checked $time. Requests waiting there may be missing here.';
  }

  @override
  String attentionWaitsForWifi(String server) {
    return '$server is checked on Wi-Fi only';
  }

  @override
  String attentionChecksPaused(String server) {
    return 'Checks on $server are paused';
  }

  @override
  String get sessionAddressInclude => 'Include this server’s address';

  @override
  String get sessionAddressDisclosure =>
      'The link then shows this address and the conversation ID, never a password: the other phone still needs its own access. Screenshots, messages and the clipboard can keep it.';

  @override
  String get sessionAddressIntro =>
      'Scan with OpenCode Mobile on the other phone. The code holds this server’s address and the conversation ID.';

  @override
  String get sessionAddressUnsupportedHost =>
      'Only a private HTTPS address ending in .ts.net can go in a link.';

  @override
  String get sessionAddressOpenTitle => 'Open a shared conversation';

  @override
  String get sessionAddressConsentSaved => 'Open on this saved server?';

  @override
  String get sessionAddressConsentNew => 'Add this server?';

  @override
  String get sessionAddressNotSaved => 'Not saved on this phone';

  @override
  String get sessionAddressConsentNote =>
      'The link grants no access. Checking only asks the server which installation it is; nothing signs in and no password is sent.';

  @override
  String get sessionAddressCheck => 'Check server';

  @override
  String sessionAddressChecking(String host) {
    return 'Checking $host…';
  }

  @override
  String get sessionAddressAddBody =>
      'This server is not saved on this phone. Add it with your own sign-in; the link does not carry one.';

  @override
  String get sessionAddressAddServer => 'Add server';

  @override
  String get sessionAddressChooseBody =>
      'More than one saved server uses this address. Choose the one to open the conversation on.';

  @override
  String sessionAddressVerifyBody(String name) {
    return 'Confirm that $name is the server this link came from. The phone remembers this for $name; it does not sign in or share a password.';
  }

  @override
  String get sessionAddressVerify => 'Verify server';

  @override
  String sessionAddressReadyBody(String name) {
    return '$name matches this link.';
  }

  @override
  String sessionAddressSignInBody(String name) {
    return 'Sign in to $name with your own account first, then open the conversation.';
  }

  @override
  String get sessionAddressSignIn => 'Sign in';

  @override
  String get sessionAddressOpen => 'Open conversation';

  @override
  String get sessionAddressOpening => 'Opening the conversation…';

  @override
  String get sessionAddressReason => 'Reason';

  @override
  String get sessionAddressFailUnavailable =>
      'Conversation links with a server address are not available yet.';

  @override
  String get sessionAddressFailInvalidLink =>
      'This conversation link is not valid. Scan or copy it again.';

  @override
  String get sessionAddressFailTooLarge =>
      'This link is too long. Ask the sender for a new link.';

  @override
  String get sessionAddressFailCredentials =>
      'This link contains private sign-in information and cannot be used.';

  @override
  String get sessionAddressFailConsentRequired =>
      'Choose whether to include this server’s address first.';

  @override
  String get sessionAddressFailPrivateRouteRequired =>
      'This server cannot be reached through the required private connection. Check your connection.';

  @override
  String get sessionAddressFailUnreachable =>
      'The server could not be reached. Check your connection and try again.';

  @override
  String get sessionAddressFailTimedOut =>
      'The server did not answer in time. Try again.';

  @override
  String get sessionAddressFailTlsRejected =>
      'The server’s secure connection could not be verified, so the link was not opened.';

  @override
  String get sessionAddressFailRedirectsRejected =>
      'This server tried to send the request somewhere else. The link was not opened.';

  @override
  String get sessionAddressFailAccessDenied =>
      'Your access to this server or conversation was refused.';

  @override
  String get sessionAddressFailInvalidDescriptor =>
      'This server did not provide the information needed to open this link.';

  @override
  String get sessionAddressFailInstanceMismatch =>
      'This link and the saved server do not identify the same installation.';

  @override
  String get sessionAddressFailBindingRequired =>
      'Verify this saved server before opening the conversation.';

  @override
  String get sessionAddressFailAmbiguousProfile =>
      'Choose which saved server to use.';

  @override
  String get sessionAddressFailProfileMissing =>
      'This saved server is no longer available.';

  @override
  String get sessionAddressFailStorage =>
      'The server verification could not be saved or read. Try again after restarting the app.';

  @override
  String get sessionAddressFailSignInRequired =>
      'Sign in to this server with your own account before continuing.';

  @override
  String get sessionAddressFailUnsafeLookup =>
      'This server has not been verified for private conversation links.';

  @override
  String get sessionAddressFailSessionMissing =>
      'This conversation is not available on this server.';

  @override
  String get sessionAddressFailCancelled => 'Opening this link was cancelled.';

  @override
  String get removeFromPhoneDeleteAllChoice => 'Delete everything…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return 'Delete everything, freeing about $size…';
  }

  @override
  String get phoneServerCardErrorDetail => 'Error';

  @override
  String get sessionMenuGoTo => 'Go to';

  @override
  String get sessionMenuDo => 'Do';

  @override
  String get sessionMenuFind => 'Find';

  @override
  String get sessionMenuSubagents => 'Subagents';

  @override
  String get sessionMenuDetails => 'Details';

  @override
  String get sessionMenuShareHint => 'Anyone with the link can read it';

  @override
  String get sessionMenuStopSharingHint => 'The public link stops working';

  @override
  String get sessionMenuCompactHint =>
      'Summarizes it so the agent has room again';

  @override
  String get sessionMenuForkHint => 'Opens a copy you can take another way';

  @override
  String get sessionMenuContinueComputerHint =>
      'Shows the command that resumes it there';

  @override
  String get sessionMenuContinuePhoneHint =>
      'Shows a code the app on that phone opens';

  @override
  String get sessionMenuNeedsPrompt => 'Available after the first prompt';

  @override
  String commandSheetServerGroup(String server) {
    return 'Commands from $server';
  }

  @override
  String commandSheetAgentMissingTitle(String agent) {
    return '$agent commands unavailable';
  }

  @override
  String commandSheetAgentMissingWhy(String agent) {
    return '$agent doesn\'t share its own commands with the app yet, so the app can\'t list them, run them, or run ! shell commands. The app\'s own actions still work.';
  }

  @override
  String commandSheetAgentCommandNotSent(String command, String agent) {
    return '$command wasn\'t sent: $agent doesn\'t share its commands with the app yet. Remove the / to send it as a message.';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return '$command wasn\'t sent: shell commands can\'t run on $agent from the app. Remove the ! to send it as a message.';
  }

  @override
  String get commandSheetShellDescription =>
      'Or start a message with ! to run it from the composer';

  @override
  String get commandSheetRetryDescription => 'Sends your last prompt again';

  @override
  String get commandSheetNoteDescription =>
      'A note the agent keeps in mind for this conversation';

  @override
  String get commandSheetApprovalsDescription =>
      'What this conversation may do without asking';

  @override
  String get commandSheetReloadDescription =>
      'Reads this conversation from the server again';

  @override
  String get commandSheetLibrarySubtitle =>
      'Pick a command, then the conversation it runs in';

  @override
  String get commandSheetAgentFallback => 'This agent';

  @override
  String get commandSheetPlanDescription =>
      'Opens the agent\'s latest plan in the conversation';

  @override
  String get chatUiSessionMenu => 'Conversation menu';

  @override
  String get commandsScreenLoadFailed => 'Couldn’t load commands';

  @override
  String get commandSheetSubtitleAppOnly =>
      'Run one of the app\'s actions in this conversation';

  @override
  String get voiceModeMicAsk =>
      'Voice typing needs the microphone. Tap Allow microphone, then choose Allow.';

  @override
  String get voiceModeMicAllow => 'Allow microphone';

  @override
  String get voiceModeMicBlocked =>
      'Android blocks the microphone for this app. Turn it on in Android settings, then come back here.';

  @override
  String get voiceModeNothingHeard =>
      'Nothing was heard. Tap the mic and try again.';

  @override
  String get teamDispatchCreating => 'Creating your task…';

  @override
  String get teamDispatchSending => 'Task created · sending it to the team…';

  @override
  String get teamDispatchAwaitingWorker =>
      'Task sent to the team · waiting for a worker';

  @override
  String get teamDispatchWorkerStarted => 'A worker started your task';

  @override
  String get teamDispatchCreateRefused =>
      'The task wasn’t made. Change it and send it again.';

  @override
  String get teamDispatchAssignRefused =>
      'Task created, but it could not be sent to the team';

  @override
  String get teamDispatchAssignRefusedHint =>
      'The task stays on the board, given to no one.';

  @override
  String get teamDispatchCreateUnconfirmed =>
      'Couldn’t confirm whether the task was created';

  @override
  String get teamDispatchDispatchUnconfirmed =>
      'Task created · couldn’t confirm it reached the team';

  @override
  String get teamDispatchCheckBoard =>
      'Check the board before sending it again. Your words are kept.';

  @override
  String get teamDispatchUnknown =>
      'Task sent · the team can’t be reached, so whether a worker started is unknown';

  @override
  String get teamDispatchCheckAgain => 'Check the team again';

  @override
  String get teamDispatchTaskId => 'Task ID';

  @override
  String get teamDispatchHostWords => 'The team’s reply';

  @override
  String get teamUiHostGuideOpen => 'Open the full guide';

  @override
  String hostServiceInstallChecked(String release) {
    return 'Downloads the script from release $release and checks its SHA-256 checksum first. If the file was changed, nothing runs.';
  }

  @override
  String get hostServiceWhatThisDoes => 'What this does';

  @override
  String get hostServiceWhatLinux =>
      'Needs Linux with systemd, such as Ubuntu. It does not run on macOS or Windows.';

  @override
  String get hostServiceWhatInstall =>
      'Installs OpenCode with its official installer if it is not there yet.';

  @override
  String get hostServiceWhatService =>
      'Adds a service for your account that keeps OpenCode running after reboots and closed terminals. It listens on that computer only.';

  @override
  String get hostServiceWhatPassword =>
      'Makes a password for the server and keeps it in a file only your account can read.';

  @override
  String get hostServicePinnedCommit => 'Script version';

  @override
  String get hostServiceChecksum => 'SHA-256 checksum';

  @override
  String get mcpAddBrowseTitle => 'Browse the catalogue';

  @override
  String get mcpAddBrowseDetail =>
      'Servers from the public MCP registry, turned on with a switch';

  @override
  String get mcpAddBrowseNone =>
      'No catalogue for this server: it doesn\'t accept new MCP servers from the app.';

  @override
  String get mcpAddManualTitle => 'Enter manually';

  @override
  String get mcpAddManualDetail =>
      'Type its address, or the command that starts it';

  @override
  String get mcpCatalogTitle => 'MCP catalogue';

  @override
  String get mcpCatalogConsentTitle => 'Load the MCP registry?';

  @override
  String get mcpCatalogConsentBody =>
      'The app asks registry.modelcontextprotocol.io for its list of MCP servers. It sends only what you search for, nothing about you or your servers.';

  @override
  String get mcpCatalogConsentLoad => 'Load the list';

  @override
  String get mcpCatalogForget => 'Stop using the registry';

  @override
  String get mcpCatalogForgetFailed =>
      'Couldn\'t forget the saved registry list. Try again.';

  @override
  String get mcpCatalogSearch => 'Search the registry';

  @override
  String get mcpCatalogInventoryFailed =>
      'Couldn\'t read this server\'s MCP servers';

  @override
  String get mcpCatalogInventoryFailedBody =>
      'The switches need to know what is already on. Check the connection, then try again.';

  @override
  String get mcpCatalogFailed => 'Couldn\'t load the public MCP registry';

  @override
  String get mcpCatalogSearchFailed =>
      'Couldn\'t search the public MCP registry';

  @override
  String get mcpCatalogFailedBody =>
      'Check the phone\'s internet connection, then try again. You can still enter a server by hand.';

  @override
  String get mcpCatalogEmpty => 'The registry listed no servers';

  @override
  String mcpCatalogNoMatch(String query) {
    return 'Nothing in the registry matches “$query”';
  }

  @override
  String get mcpCatalogEmptyBody =>
      'Try other words, or enter the server by hand.';

  @override
  String get mcpCatalogStale =>
      'Couldn\'t refresh the list from the registry. These are the listings loaded earlier.';

  @override
  String get mcpCatalogPriceNote =>
      'The registry lists no prices. A hosted server\'s owner may charge for it or ask for an account.';

  @override
  String get mcpCatalogAdding => 'Adding…';

  @override
  String get mcpCatalogRemoving => 'Removing…';

  @override
  String get mcpCatalogCannotRemove =>
      'On. This server keeps it in its configuration, and the app can\'t remove it.';

  @override
  String get mcpCatalogNeedsDocker =>
      'Runs in Docker. To add it anyway, use Enter manually.';

  @override
  String get mcpCatalogNoEndpoint =>
      'Lists nothing the app can start. To add it anyway, use Enter manually.';

  @override
  String mcpCatalogHostedBy(String host) {
    return 'Hosted by $host';
  }

  @override
  String get mcpCatalogNeedsNode => 'Needs Node on the server';

  @override
  String get mcpCatalogNeedsNodePhone => 'Needs Node on this phone';

  @override
  String get mcpCatalogNeedsPython => 'Needs Python with uv on the server';

  @override
  String get mcpCatalogNeedsKey => 'Needs an API key';

  @override
  String get mcpCatalogNeedsSettings => 'Needs extra settings';

  @override
  String mcpCatalogNodeTitle(String title) {
    return '$title runs with Node';
  }

  @override
  String get mcpCatalogNodeAdd => 'Add Node to this phone';

  @override
  String get mcpCatalogNodeAddDetail =>
      'Opens This phone. Choose Add tools, then Node, and turn this on again once it\'s added.';

  @override
  String get mcpCatalogNodeHave => 'Node is already on this phone';

  @override
  String get mcpCatalogNodeHaveDetail => 'Check the details and add it';

  @override
  String mcpSetupFromCatalog(String listing, String server) {
    return 'Filled in from “$listing” in the public MCP registry. Check it before you add it: $server will run or connect to what is here.';
  }

  @override
  String get mcpSetupThisServer => 'this server';

  @override
  String mcpSetupValueRequired(String name) {
    return 'Enter a value for $name';
  }

  @override
  String get mcpSetupNameFromCatalog => 'The registry listing needs this one';

  @override
  String get mcpVariableName => 'Variable name';

  @override
  String get mcpVariableValue => 'Variable value';

  @override
  String get mcpAddVariable => 'Add another variable';

  @override
  String get mcpRemoveVariable => 'Remove variable';

  @override
  String get mcpSetupTimeoutSeconds => 'Timeout in seconds';

  @override
  String get isolatedTaskPromptLabel => 'What should it work on?';

  @override
  String get isolatedTaskPromptHelper =>
      'Sent once the copy is ready. Leave it empty to write it in the conversation.';

  @override
  String get isolatedTaskOptions => 'Options';

  @override
  String get isolatedTaskPreparingHint =>
      'If you stop waiting, the copy stays. You\'ll find it under Project › Worktrees.';

  @override
  String get isolatedTaskFailedBody =>
      'The copy is made, but its setup didn\'t finish. Start in it anyway, or remove it.';

  @override
  String isolatedTaskSending(String name) {
    return 'Sending your task to $name…';
  }

  @override
  String get isolatedTaskSendFailed => 'Couldn\'t send your task';

  @override
  String get isolatedTaskSendFailedBody =>
      'It\'s waiting in the conversation\'s message box, ready to send.';

  @override
  String get isolatedTaskSendFailedLost =>
      'Copy your task below and send it in the conversation.';

  @override
  String get isolatedTaskOpenConversation => 'Open the conversation';

  @override
  String get isolatedTaskStartAnyway => 'Start anyway';

  @override
  String get isolatedTaskRemove => 'Remove the copy';

  @override
  String isolatedTaskRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get isolatedTaskRemoveBody =>
      'Its folder and branch are deleted. Your project itself is not touched.';

  @override
  String isolatedTaskRemoved(String name) {
    return 'Removed $name. You can start again.';
  }

  @override
  String get isolatedTaskSetupOutput => 'What the setup reported';

  @override
  String get isolatedTaskCopyFolder => 'Folder of the copy';

  @override
  String get isolatedTaskBranchLabel => 'Branch';

  @override
  String get isolatedTaskStageSend =>
      'Opening the conversation and sending your task';

  @override
  String get teamStartRunBlockedTitle => 'Team can\'t take tasks';

  @override
  String get teamStartRunPlannerOff => 'The planner is switched off';

  @override
  String get teamStartRunPlannerOffWakeBody =>
      'The planner turns each task into steps for the team. Wake it to give the team your task.';

  @override
  String get teamStartRunPlannerOffHostBody =>
      'The planner turns each task into steps for the team, and this app can\'t switch it on. Switch it on where the team runs, then try again.';

  @override
  String get teamStartRunNoPlanner => 'This team has no planner';

  @override
  String get teamStartRunNoPlannerBody =>
      'A planner turns each task into steps for the team. Add one where the team runs, then try again.';

  @override
  String get teamStartRunNoProject => 'This team has no project yet';

  @override
  String get teamStartRunNoProjectBody =>
      'Tasks go straight to a project\'s worker. Add a project to the team, then try again.';

  @override
  String get teamStartRunWake => 'Wake the planner';

  @override
  String get teamStartRunWakeAsked =>
      'Waking the planner. The task form opens as soon as it\'s awake.';

  @override
  String get teamStartRunStillOff => 'The planner is still switched off.';

  @override
  String get teamStartRunStillNoProject => 'The team still has no project.';

  @override
  String get teamStartRunWakeRefused => 'Couldn\'t wake the planner';

  @override
  String get teamStartRunWakeRefusedNext =>
      'Try again, or switch it on where the team runs.';

  @override
  String get addServerTailscaleNext => 'Enter the address';

  @override
  String get phoneSetupTermuxGetCurrent => 'Get the current Termux';

  @override
  String get phoneSetupUnsupportedTitle => 'Connect a server';

  @override
  String get phoneSetupUnsupportedBody =>
      'Setting up on the device itself works only on Android phones. On your computer, run this command, then add the server here with the code it prints.';

  @override
  String get termuxStorageStageTotal => 'The whole Termux install';

  @override
  String get setupProgressViewFailedStep =>
      'This step didn\'t finish. What went wrong is under Details.';

  @override
  String setupProgressViewFailedAt(String name) {
    return 'Stopped at $name. What went wrong is under Details.';
  }

  @override
  String workRunawayHelper(String helper, String duration) {
    return 'A leftover $helper process has been busy for $duration with nothing to do';
  }

  @override
  String workRunawayHelperInProject(
    String helper,
    String project,
    String duration,
  ) {
    return 'A leftover $helper process in $project has been busy for $duration with nothing to do';
  }

  @override
  String get workRunawaySeeRunning => 'See what\'s running';

  @override
  String thisPhoneUpToDate(String version) {
    return 'Up to date · $version';
  }

  @override
  String thisPhoneUpdateTitle(String runtime) {
    return 'Update $runtime?';
  }

  @override
  String thisPhoneUpdateBody(String version) {
    return 'Installs version $version, restarts the server on this phone and connects again.';
  }

  @override
  String get thisPhoneUpdateKept =>
      'Your conversations are kept. The server is away for a minute while it restarts.';

  @override
  String get thisPhoneUpdateBusy =>
      'A reply is still being written. Stop it or let it finish, then update.';

  @override
  String thisPhoneStartFailed(String runtime) {
    return '$runtime didn\'t start. Start it again; Details below says what went wrong.';
  }

  @override
  String get thisPhoneStartAgain => 'Start again';

  @override
  String thisPhoneInstallFailed(String runtime) {
    return 'Installing $runtime didn\'t finish. Install it again; your conversations are kept.';
  }

  @override
  String get thisPhoneInstallAgain => 'Install again';

  @override
  String thisPhoneStopFailed(String runtime) {
    return '$runtime didn\'t stop. Try stopping it again.';
  }

  @override
  String thisPhoneCheckFailed(String runtime) {
    return 'This phone couldn\'t check on $runtime. Try again in a moment.';
  }

  @override
  String thisPhoneSwitchStopped(String runtime) {
    return '$runtime didn\'t start after the switch. Your conversations are kept.';
  }

  @override
  String get addServerCheckFailedPlain =>
      'The server could not be checked. Check the address and this phone’s connection, then try again.';

  @override
  String serverRowDetailsTitle(String name) {
    return '$name details';
  }

  @override
  String get pluginsTeamRowTurnOn => 'Turn on';

  @override
  String get teamUiHostGuideEnterAddress => 'Enter the address';

  @override
  String get commandAuthStartFailed => 'Sign-in didn\'t start.';

  @override
  String get commandAuthCheckFailed =>
      'Couldn\'t check the sign-in. Try again.';

  @override
  String get commandAuthTryAgain => 'Try again';

  @override
  String get draftLeaveMessageNoText =>
      'Try saving again. If you leave without saving, your latest changes to this draft may be lost.';

  @override
  String get draftLeaveCopyAction => 'Copy draft and leave';

  @override
  String get draftLeaveRetry => 'Try saving again';

  @override
  String get draftLeaveStillFailing =>
      'Still not saved. Copy your text before you leave.';

  @override
  String get queuedRetry => 'Try again';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Try all $count again',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return 'Use $model and resend';
  }

  @override
  String get chatUiChooseAnotherModel => 'Choose another model';

  @override
  String get chatUiSendPromptAgain => 'Send again';

  @override
  String get chatUiPromptNotAnswered => 'Not answered';

  @override
  String get chatWatchEndedTitle => 'This conversation has ended';

  @override
  String get chatWatchEndedBody =>
      'It ended before the worker wrote anything here.';

  @override
  String get chatWatchBackToTask => 'Back to the task';

  @override
  String get chatWatchBackToWorker => 'Back to the worker';

  @override
  String get migrationTitle => 'Move from Termux';

  @override
  String get migrationChecking => 'Checking Termux and phone storage…';

  @override
  String get migrationReviewIntro =>
      'Your projects are copied into OpenCode inside this app. Nothing in Termux is changed or removed.';

  @override
  String get migrationGroupMoves => 'Copied and ready to use';

  @override
  String get migrationGroupExports => 'Saved privately, not turned on';

  @override
  String get migrationGroupNotMoved => 'Not moved';

  @override
  String get migrationItemProjects => 'Projects';

  @override
  String get migrationItemConfig => 'MCP and agent settings';

  @override
  String get migrationItemSessions => 'Conversation history (backup copy)';

  @override
  String get migrationItemGitConfig => 'Git settings';

  @override
  String get migrationItemShellFiles => 'Shell settings';

  @override
  String get migrationItemAiTeam => 'AI Team';

  @override
  String get migrationItemProjectsWhat =>
      'Into a new folder on the in-app server. Nothing there is overwritten.';

  @override
  String get migrationItemConfigWhat =>
      'To review before using: commands and paths may only work in Termux.';

  @override
  String get migrationItemSessionsWhat =>
      'May contain your sign-ins, and the app doesn\'t open it. Termux keeps your usable history.';

  @override
  String get migrationItemGitConfigWhat =>
      'Your Git name, email and options, to review.';

  @override
  String get migrationItemShellFilesWhat =>
      'Keeps .bashrc, .zshrc and your other shell start files; they never run.';

  @override
  String get migrationItemAiTeamWhat =>
      'The team\'s saved state. Setup installs its tools again.';

  @override
  String migrationItemSize(String size, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return '$size · $_temp0';
  }

  @override
  String get migrationSizeUnknown => 'Size unknown';

  @override
  String get migrationExportsNote =>
      'Private copies stay on this phone inside the in-app Linux. Nothing in them runs or turns on by itself.';

  @override
  String get migrationNotMovedSignIn =>
      'Sign-ins to AI providers: sign in again after the move';

  @override
  String migrationNotMovedSignInNamed(String names) {
    return 'Sign-ins to $names: sign in again after the move';
  }

  @override
  String get migrationNotMovedKeys => 'SSH keys and saved Git passwords';

  @override
  String get migrationNotMovedTools =>
      'Installed tools and caches: setup installs them again';

  @override
  String get migrationTermuxKept =>
      'Termux stays as it is, and its server keeps working until you remove it';

  @override
  String get migrationKeepOpen =>
      'Keep the app open while copying. Android stops it when you leave the app, and it picks up from here when you resume.';

  @override
  String get migrationStart => 'Copy to the in-app server';

  @override
  String get migrationChooseOne => 'Choose at least one item to copy.';

  @override
  String get migrationPacking => 'Preparing your files in Termux…';

  @override
  String get migrationCopying => 'Copying files to this app…';

  @override
  String get migrationUnpacking => 'Importing your files…';

  @override
  String get migrationVerifying => 'Checking the copied files…';

  @override
  String get migrationSwitching => 'Connecting to the in-app server…';

  @override
  String get migrationStepConnect => 'Connect to the in-app server';

  @override
  String get migrationStop => 'Stop copying';

  @override
  String get migrationStopTitle => 'Stop copying?';

  @override
  String get migrationStopBody => 'You can resume later from This phone.';

  @override
  String get migrationStopKept => 'What was copied so far is kept';

  @override
  String get migrationKeepGoing => 'Keep copying';

  @override
  String get migrationCancelled => 'Copy stopped. You can resume later.';

  @override
  String get migrationCancelledBody =>
      'What was copied is kept, and Termux isn\'t changed.';

  @override
  String get migrationStoppedLeaving =>
      'It stopped because the app left the screen: Android doesn\'t let it run in the background. What was copied is kept.';

  @override
  String get migrationResume => 'Resume copying';

  @override
  String get migrationNeedsSpace => 'More free space is needed before copying.';

  @override
  String migrationNeedsSpaceBody(String needed, String free) {
    return 'Needs about $needed, and $free is free. Free up space on this phone, or copy fewer items.';
  }

  @override
  String migrationNeedsSpaceBodyUnknown(String needed) {
    return 'Needs about $needed, and the free space couldn\'t be read. Free up space on this phone, or copy fewer items.';
  }

  @override
  String get migrationChooseFewer => 'Choose fewer items';

  @override
  String get migrationNeedsBuiltin => 'Set up the in-app server first.';

  @override
  String migrationNeedsBuiltinBody(String runtime) {
    return 'Your projects move into OpenCode inside this app, so it needs setting up. Setup installs Linux and $runtime, then this continues here.';
  }

  @override
  String get migrationSetUpBuiltin => 'Set up the in-app server';

  @override
  String get migrationSetupFailed =>
      'Setup couldn\'t start. Try again, or set it up from This phone.';

  @override
  String get migrationTermuxNotAnswering => 'Termux isn\'t answering';

  @override
  String get migrationTermuxUnavailable => 'Open Termux, then try again.';

  @override
  String get migrationOpenTermux => 'Open Termux';

  @override
  String get migrationFailedTitle => 'The move stopped';

  @override
  String get migrationSourceChanged =>
      'Files changed during copying. Try again when Termux is idle.';

  @override
  String get migrationSourceBusy =>
      'Termux is finishing the previous step. Try again shortly.';

  @override
  String get migrationUnsupportedFiles =>
      'This item contains files that cannot be copied safely.';

  @override
  String get migrationUnsupportedFilesFix =>
      'Links, sockets and Git worktrees can\'t be copied. Remove them in Termux and try again, or copy that project by hand.';

  @override
  String get migrationTooLarge =>
      'This item exceeds the migration size or file limit.';

  @override
  String get migrationTooLargeFix =>
      'Each item can hold up to 512 MB and 20,000 files. Delete build folders such as node_modules in Termux, then try again.';

  @override
  String get migrationVerificationFailed =>
      'The copy could not be verified. Your Termux files are unchanged.';

  @override
  String get migrationDestinationChanged =>
      'Imported files changed. They will not be overwritten.';

  @override
  String get migrationDestinationChangedFix =>
      'The files on the in-app server stay as you left them.';

  @override
  String get migrationStorageFailed =>
      'The copy could not be saved. Check phone storage and try again.';

  @override
  String get migrationTimedOut =>
      'This step took too long. Keep the app open and resume.';

  @override
  String get migrationSelectionChanged =>
      'Use the saved migration selection to resume.';

  @override
  String get migrationConnectionFailed =>
      'Files are copied, but the in-app server could not connect.';

  @override
  String get migrationFailureCode => 'Reason';

  @override
  String get migrationFailureItem => 'Item';

  @override
  String get migrationOpenThisPhone => 'Open This phone';

  @override
  String get migrationDoneTitle => 'Moved from Termux';

  @override
  String get migrationDone =>
      'Files copied. Your Termux server is still available.';

  @override
  String get migrationSignInAgain => 'Sign in to your AI providers again';

  @override
  String migrationSignInAgainNamed(String names) {
    return '$names appear in your Termux settings. Sign in here to use them.';
  }

  @override
  String get migrationSignInAgainAny =>
      'Sign-ins never move from Termux. Until you sign in here, replies use OpenCode\'s free model, which is slower.';

  @override
  String get migrationProjectsWhere => 'Your projects';

  @override
  String migrationProjectsWhereBody(String folder) {
    return 'In the folder $folder on the in-app server';
  }

  @override
  String get migrationExportsWhere => 'Private copies';

  @override
  String migrationExportsWhereBody(String items) {
    return '$items: saved inside the in-app Linux, not turned on';
  }

  @override
  String get migrationRemoveTermux =>
      'Remove the Termux server when you\'re ready';

  @override
  String get migrationRemoveTermuxBody =>
      'Nothing is removed for you. Until then it keeps working, and you can switch back to it on Servers.';

  @override
  String get migrationOpenBuiltin => 'Open the in-app server';

  @override
  String get migrationDetailProjects => 'Projects folder';

  @override
  String get migrationDetailExports => 'Private copies folder';

  @override
  String get migrationUnfinishedTitle => 'The move didn\'t finish';

  @override
  String get migrationUnfinishedBody =>
      'Resume to carry on where it stopped. What was already copied is kept, and Termux isn\'t changed.';

  @override
  String get migrationUnavailableTitle => 'The move can\'t start';

  @override
  String get migrationUnavailableBody =>
      'The app couldn\'t prepare its private storage for the copy. Try again, and if it keeps happening, restart the app.';

  @override
  String get migrationRowBody =>
      'Copy your projects into the in-app server. Termux stays as it is.';

  @override
  String get migrationRowResume => 'Resume moving to the in-app server';

  @override
  String get migrationRowResumeBody =>
      'Stopped before it finished. What was copied is kept.';

  @override
  String get migrationRowRunning => 'Moving to the in-app server';

  @override
  String get migrationRowDoneBody =>
      'Remove the Termux server when you\'re ready.';

  @override
  String get migrationOffer =>
      'Move your Termux projects into this app? Termux stays as it is.';

  @override
  String get migrationOfferAction => 'Review what moves';

  @override
  String get integrationsSignInUncertainNext =>
      'Check the server before you start again';

  @override
  String teamHomeLastKnownTasks(String time) {
    return 'Tasks as of $time';
  }

  @override
  String teamHomeLastKnownAgents(String time) {
    return 'Agents as of $time';
  }

  @override
  String get teamHomeStoppedStartFirst =>
      'Start the team again to give it a task or open one.';

  @override
  String get inAppServerStartExitedBody =>
      'OpenCode closed by itself while it was starting. Open setup to see its log, or start it again.';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'OpenCode did not answer within $seconds seconds. The phone may be busy or short on memory; close other apps, then start it again.';
  }

  @override
  String get inAppServerStartInterruptedBody =>
      'The start stopped because the app left the screen. Start it again to continue.';

  @override
  String get inAppServerStartPasswordBody =>
      'The app could not set up OpenCode\'s sign-in on this phone. Start it again; if this repeats, open setup.';

  @override
  String get inAppServerStartRefusedBody =>
      'The phone did not let the app start OpenCode just now. Start it again; if this repeats, restart the phone.';

  @override
  String get integrationsConnectWithKey => 'Add an API key';

  @override
  String get integrationsConnectOnServer => 'Set up on the server';

  @override
  String integrationsProviderDetails(String name) {
    return '$name details';
  }

  @override
  String get integrationsEnvironmentVariable => 'Server environment variable';

  @override
  String integrationsEnvironmentNote(String name) {
    return 'To connect $name without the app, set this where the server runs, then restart the server.';
  }

  @override
  String get termuxProblemAccessHeard =>
      'OpenCode is running in Termux, but this app can\'t reach Termux yet. Allow access and it connects.';

  @override
  String get termuxProblemAccessNeeded =>
      'This app can\'t reach Termux yet. Allow access so it can find OpenCode there and connect.';

  @override
  String get termuxProblemAccessBlocked =>
      'Android blocked Termux access for this app. In this app\'s permissions, turn on “Run commands in Termux environment”.';

  @override
  String get termuxProblemOtherAppsOff =>
      'Termux doesn\'t take commands from other apps yet. One line in Termux allows it.';

  @override
  String get termuxProblemAsleep =>
      'Termux didn\'t answer. Android may have put it to sleep. Open Termux to wake it.';

  @override
  String termuxProblemNotAnswering(String runtime) {
    return '$runtime is set up in Termux but isn\'t answering. A restart usually brings it back.';
  }

  @override
  String get termuxProblemNotInstalled =>
      'Termux isn\'t on this phone. Install it again, or set up the in-app server instead.';

  @override
  String get termuxProblemOutdated =>
      'This Termux is too old for the app to use. Install the current Termux from F-Droid.';

  @override
  String termuxProblemUnknown(String runtime) {
    return 'This phone couldn\'t check on $runtime in Termux. Try again in a moment.';
  }

  @override
  String get termuxFixAllowAccess => 'Allow access to Termux';

  @override
  String get termuxFixOpenPermissions => 'Open this app\'s permissions';

  @override
  String get termuxFixAllowOtherApps => 'Allow other apps in Termux';

  @override
  String get termuxFixOpenTermux => 'Open Termux';

  @override
  String termuxFixRestart(String runtime) {
    return 'Restart $runtime in Termux';
  }

  @override
  String get termuxFixGetTermux => 'Get Termux';

  @override
  String get termuxFixGetCurrentTermux => 'Get the current Termux';

  @override
  String get termuxOtherAppsTitle => 'Allow other apps';

  @override
  String get termuxOtherAppsBody =>
      'Paste this line in Termux and press Enter, then come back here. Open Termux copies it for you.';

  @override
  String get termuxLeadRunning => 'OpenCode is running in Termux';

  @override
  String get termuxLeadAccessLine =>
      'This app can\'t reach Termux yet. Allow access and it connects to your conversations.';

  @override
  String get termuxLeadSetUp => 'OpenCode is set up in Termux';

  @override
  String get termuxLeadTermuxOnly => 'Termux is on this phone';

  @override
  String get termuxLeadRunningBody => 'Connect to pick up your conversations.';

  @override
  String get termuxLeadStoppedBody =>
      'It\'s stopped. Start it to pick up your conversations.';

  @override
  String get termuxLeadConnect => 'Connect to the server in Termux';

  @override
  String get termuxLeadStart => 'Start the server in Termux';

  @override
  String get termuxInAppInstead => 'Set up the in-app server instead';

  @override
  String get termuxInAppInsteadDetail =>
      'A fresh start that runs inside this app. No Termux needed.';

  @override
  String get termuxInAppInsteadBlocked =>
      'A fresh start inside this app. To bring your projects from Termux, fix Termux access first.';

  @override
  String get aboutBundledComponents => 'Bundled components';

  @override
  String get aboutBundledComponentsDetail =>
      'Icons, fonts and other parts shipped inside this app';

  @override
  String get manageSpaceTitle => 'Clear this app\'s storage';

  @override
  String get manageSpaceMeasuring => 'Measuring what is stored…';

  @override
  String get manageSpaceIntro =>
      'Clearing deletes everything OpenCode Mobile keeps on this phone, and it cannot be undone. Export your projects first if you want to keep them.';

  @override
  String get manageSpaceExportFirst => 'Export projects first';

  @override
  String get manageSpaceClearCache => 'Clear the app\'s cache only';

  @override
  String manageSpaceClearCacheDetail(String size) {
    return 'Frees $size. Projects, servers and settings stay.';
  }

  @override
  String get manageSpaceClearCacheKeeps =>
      'Projects, servers and settings stay.';

  @override
  String manageSpaceCacheCleared(String size) {
    return 'Cache cleared. $size freed.';
  }

  @override
  String get manageSpaceCacheFailed => 'Could not clear the cache. Try again.';

  @override
  String get manageSpaceTryAgain => 'Try again';

  @override
  String get manageSpaceDeleteAll => 'Delete everything';

  @override
  String get manageSpaceDeleteAllDetail =>
      'Deletes all of the list below and closes the app';

  @override
  String get manageSpaceDeleteTitle => 'Delete everything?';

  @override
  String get manageSpaceDeleteBody =>
      'OpenCode Mobile then starts again as if it were new. This cannot be undone.';

  @override
  String get manageSpaceLostServer => 'The in-app server and its conversations';

  @override
  String manageSpaceLostProjects(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count projects ($size)',
      one: '1 project ($size)',
    );
    return '$_temp0';
  }

  @override
  String manageSpaceLostSettings(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count saved servers and all settings',
      one: '1 saved server and all settings',
      zero: 'All settings',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptAll =>
      'Termux, your computers and anything pushed to git stay';

  @override
  String get manageSpaceWaitForExport => 'Wait for the export to finish';

  @override
  String get manageSpaceDeletedLabel => 'Clearing deletes';

  @override
  String get manageSpaceServer => 'The in-app server';

  @override
  String get manageSpaceServerDetail =>
      'Ubuntu, OpenCode, its sign-ins and its conversations';

  @override
  String get manageSpaceSettings => 'Saved servers and settings';

  @override
  String manageSpaceSavedServers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count saved servers',
      one: '1 saved server',
      zero: 'No saved servers',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptLabel => 'Stays';

  @override
  String get manageSpaceKeptTermux => 'Termux and the projects in it';

  @override
  String get manageSpaceKeptComputers => 'Your computers and their servers';

  @override
  String get manageSpaceKeptGit => 'Anything you pushed to git';

  @override
  String projectExportDetail(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count projects, $size, as one zip file where you choose',
      one: '1 project, $size, as one zip file where you choose',
    );
    return '$_temp0';
  }

  @override
  String get projectExportNoProjects => 'No projects on the in-app server yet';

  @override
  String get projectExportSave => 'Save as a zip file';

  @override
  String get projectExportRunning => 'Exporting projects';

  @override
  String get projectExportPreparing => 'Listing files…';

  @override
  String projectExportProgress(String done, String total) {
    return '$done of $total';
  }

  @override
  String get projectExportStop => 'Stop the export';

  @override
  String get projectExportStopDetail => 'The half-written file is deleted';

  @override
  String get projectExportPrivate => 'Include sign-ins and conversations';

  @override
  String get projectExportPrivateDetail =>
      'Private: anyone with the file can use your accounts';

  @override
  String projectExportDone(String size, int files) {
    return 'Projects exported: $size in $files files.';
  }

  @override
  String get projectExportDonePrivate =>
      'This file holds sign-ins. Keep it private.';

  @override
  String projectExportDoneLeftOut(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files with sign-ins or keys were left out.',
      one: '1 file with sign-ins or keys was left out.',
    );
    return '$_temp0';
  }

  @override
  String get projectExportStopped => 'Export stopped. Nothing was saved.';

  @override
  String get projectExportFailedDestination =>
      'Could not write to the place you chose. Try again, or pick another place.';

  @override
  String get projectExportFailedSpace =>
      'The place you chose is full. Free some space there or pick another place.';

  @override
  String get projectExportFailedSource =>
      'A project file could not be read. Try again.';

  @override
  String get projectExportFailed =>
      'The export stopped before it finished. Try again.';

  @override
  String get projectExportProjectsLabel => 'Projects';

  @override
  String get thisPhoneExportProjects => 'Export projects';

  @override
  String get thisPhoneExportProjectsDetail =>
      'Save them as a zip file, to keep or move';

  @override
  String get demoNoCommands =>
      'The demo has no commands — send the sample prompt to see a change reviewed.';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Signed in to $providers, but this server could not load those sign-ins even after a reload, so their models cannot answer. Browser sign-ins for some providers, such as Anthropic and Google, do not load on this server. Add an API key under Providers instead, or pick another model.',
      one:
          'Signed in to $providers, but this server could not load that sign-in even after a reload, so its models cannot answer. Browser sign-ins for some providers, such as Anthropic and Google, do not load on this server. Add an API key under Providers instead, or pick another model.',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'The reload waits for $count running replies to finish, because reloading would stop them.',
      one:
          'The reload waits for 1 running reply to finish, because reloading would stop it.',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      'Using OpenCode\'s free model — it\'s slower. Add an API key from your provider to use your own.';

  @override
  String get freeModelSignIn => 'Add an API key';

  @override
  String get replySpeedTitle => 'Reply speed';

  @override
  String replySpeedLast(String first, String total) {
    return 'Last reply: first words after $first, finished after $total';
  }

  @override
  String replySpeedNoWords(String total) {
    return 'Last reply: ended after $total before any words came';
  }

  @override
  String replySpeedSeconds(String seconds) {
    return '$seconds s';
  }

  @override
  String get perfDetailLinuxMode => 'Linux speed mode';

  @override
  String get perfLinuxModeFast => 'Fast: proot with seccomp';

  @override
  String get perfLinuxModeSlow => 'Slow: proot without seccomp';

  @override
  String get perfLinuxModeUnknown => 'Not known while OpenCode is stopped';

  @override
  String get perfDetailAwake => 'Phone kept awake';

  @override
  String get perfAwakeNow => 'Now, while a reply runs';

  @override
  String get perfAwakeWhenWorking => 'Only while a reply runs';

  @override
  String get perfDetailFirstWords => 'First words, last reply';

  @override
  String perfFirstWordsSplit(String app, String server) {
    return '$app in the app · $server on the server';
  }

  @override
  String get perfDetailModel => 'Model, last reply';

  @override
  String get manageSpaceIntroNothingToExport =>
      'Clearing deletes everything OpenCode Mobile keeps on this phone, and it cannot be undone.';

  @override
  String integrationsKeyOnlyHelper(String name) {
    return '$name does not allow browser sign-in from other apps, so use an API key. It is billed separately from any subscription. The key is stored on this server and never shown again.';
  }

  @override
  String integrationsGetKey(String name) {
    return 'Get a key from $name';
  }

  @override
  String integrationsKeySavedReady(String name) {
    return '$name key saved. Pick one of its models in the model picker.';
  }

  @override
  String integrationsKeySavedWaiting(String name) {
    return '$name key saved. It loads once the running replies finish.';
  }

  @override
  String integrationsKeySavedUnusable(String name) {
    return '$name key saved, but this server could not load it after a refresh. Check the key, or try Reload providers in the model picker.';
  }

  @override
  String integrationsKeySavedPending(String name) {
    return '$name key saved. The server has not loaded it yet.';
  }

  @override
  String migrationReviewSpace(String needed, String free) {
    return 'Needs about $needed · $free free';
  }

  @override
  String migrationReviewSpaceUnknown(String needed) {
    return 'Needs about $needed · Free space unknown';
  }

  @override
  String get migrationReviewSpaceShort =>
      'Not enough free space for this. Choose fewer items, or free up space on this phone.';

  @override
  String get migrationDiscard => 'Discard saved copy';

  @override
  String get migrationDiscardTitle => 'Discard this saved copy?';

  @override
  String get migrationDiscardBody =>
      'Temporary copy files will be removed. Files already imported and everything in Termux will stay.';

  @override
  String get migrationDiscardFailed =>
      'The saved copy couldn\'t be removed. Try again in a moment.';

  @override
  String get migrationStopping => 'Stopping…';

  @override
  String get pickerConnectProvider => 'Connect a provider';

  @override
  String get pickerConnectProviderHint =>
      'Add an API key or sign in to use its models';

  @override
  String pickerAddKeyFor(String name) {
    return 'Add an API key for $name';
  }

  @override
  String get pickerAddKeyNotConnectedHint =>
      'Its models are not in this list yet';

  @override
  String pickerSignInTo(String name) {
    return 'Sign in to $name';
  }

  @override
  String get pickerSignInHint => 'Opens the sign-in choices for this server';

  @override
  String get pickerFreeOnlyNote =>
      'Only OpenCode\'s free model is available — it\'s slower.';

  @override
  String pickerProviderReady(String name) {
    return '$name is ready. Its models are in the list.';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return '$name is saved, but the server has not loaded it yet.';
  }

  @override
  String get integrationsSignedInUnusable =>
      'Signed in, but this server can\'t use it';

  @override
  String get effectsGlassCrashOff =>
      'Liquid glass was turned off after the app closed unexpectedly twice.';

  @override
  String get effectsGlassCrashOn => 'Turn it back on';

  @override
  String get chatUiCompactConfirmTitle => 'Compact this conversation?';

  @override
  String get chatUiCompactConfirmBody =>
      'Compact replaces earlier messages with a short summary to save space. It can\'t be undone.';

  @override
  String get chatUiCompactConfirmAction => 'Compact conversation';

  @override
  String get kitTurnReconnecting =>
      'Connection lost. Reconnecting to get the rest of this reply.';

  @override
  String get kitTurnLiveSending => 'Sending';

  @override
  String get kitTurnLiveWaitingForServer => 'Waiting for the server';

  @override
  String get kitTurnLiveServerQuiet => 'The server has not answered yet';

  @override
  String get kitTurnLiveThinking => 'Thinking';

  @override
  String get kitTurnLiveFirstWordSlow => 'Waiting for the model\'s first word';

  @override
  String get kitTurnLiveFirstWordSlowTeam =>
      'AI Team is also working on this phone, so replies may be slower';

  @override
  String get kitTurnLiveWriting => 'Writing';

  @override
  String get kitTurnLiveWorking => 'Working';

  @override
  String get kitTurnLiveWaitingForYou => 'Waiting for you';

  @override
  String get kitTurnLiveStop => 'Stop reply';

  @override
  String get kitTurnLiveStopping => 'Stopping…';

  @override
  String kitTurnLiveNow(String status) {
    return '$status…';
  }

  @override
  String kitTurnLiveFor(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String kitTurnLiveSeconds(int seconds) {
    return '$seconds s';
  }

  @override
  String kitTurnLiveMinutes(int minutes, int seconds) {
    return '$minutes min $seconds s';
  }

  @override
  String get chatNoReplyCameBack => 'No reply came back';

  @override
  String get composerFieldLabel => 'Message to the agent';

  @override
  String get e7WorkspaceYesterday => 'Yesterday';

  @override
  String get teamStripTitle => 'AI Team';

  @override
  String get teamStripIdle => 'AI Team · nothing running';

  @override
  String teamStripWorking(int count) {
    return '$count working';
  }

  @override
  String teamStripNeedsYou(int count) {
    return '$count needs you';
  }

  @override
  String teamProgressOne(String title) {
    return 'AI Team: $title';
  }

  @override
  String teamProgressStep(String title, int done, int total) {
    return 'AI Team: $title · step $done of $total';
  }

  @override
  String teamProgressMany(int count) {
    return 'AI Team: $count tasks working';
  }

  @override
  String get teamSettingsTitle => 'Team settings';

  @override
  String get teamSettingsOpenTooltip => 'Team settings';

  @override
  String get teamSettingsTurnOff => 'Turn off the AI Team';

  @override
  String teamChatWorkerNumbered(String role, int n) {
    return '$role $n';
  }

  @override
  String get teamRoleNameGeneral => 'General';

  @override
  String get teamRoleNameProduct => 'Product';

  @override
  String get teamRoleNameFrontend => 'Frontend';

  @override
  String get teamRoleNameBackend => 'Backend';

  @override
  String get teamRoleNameTester => 'Tester';

  @override
  String get teamRolePurposeGeneral => 'Any task, done the plain way';

  @override
  String get teamRolePurposeProduct =>
      'Turns an idea into clear requirements and a plan';

  @override
  String get teamRolePurposeFrontend =>
      'Screens, layout and how it feels to use';

  @override
  String get teamRolePurposeBackend =>
      'Servers, data and the code behind the screens';

  @override
  String get teamRolePurposeTester =>
      'Finds what breaks and shows that it works';

  @override
  String get teamRolesTitle => 'Agents';

  @override
  String get teamRolesNew => 'New role';

  @override
  String get teamRolesEmpty => 'No roles yet';

  @override
  String teamRoleWorkingOn(String task, String age) {
    return 'Working on “$task” · $age';
  }

  @override
  String teamRoleUses(String model) {
    return 'Uses $model';
  }

  @override
  String get teamRoleUsesTeamModel => 'Uses the team\'s model';

  @override
  String get teamRoleUsesComputerModel => 'Uses the computer\'s model';

  @override
  String teamRoleTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks',
      one: '1 task',
      zero: 'No tasks yet',
    );
    return '$_temp0';
  }

  @override
  String teamSettingsAgentsRow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Agents · $count roles',
      one: 'Agents · 1 role',
    );
    return '$_temp0';
  }

  @override
  String get teamSettingsAgentsHint =>
      'Who does the work, and how each one works';

  @override
  String get teamRoleNewTitle => 'New role';

  @override
  String get teamRoleFieldName => 'Name';

  @override
  String get teamRoleFieldPurpose => 'What it\'s for';

  @override
  String get teamRoleFieldPurposeHint =>
      'One line, for example: writes the guides';

  @override
  String get teamRoleFieldInstructions => 'Instructions';

  @override
  String get teamRoleFieldInstructionsHint =>
      'How this role should work, in your own words';

  @override
  String get teamRoleNameRequired => 'Give the role a name';

  @override
  String get teamRoleModelRow => 'Model';

  @override
  String get teamRoleTeamModel => 'Team\'s model';

  @override
  String get teamRoleComputerModel => 'The computer\'s model';

  @override
  String get teamRoleModelSheetDefault => 'Team\'s model';

  @override
  String get teamRoleModelSheetDefaultHint =>
      'Uses whatever model the whole team uses';

  @override
  String teamRoleWorkingNow(String task) {
    return 'Working on “$task”';
  }

  @override
  String get teamRoleOpenConversation => 'Open its conversation';

  @override
  String get teamRoleRecentTasks => 'Recent tasks';

  @override
  String teamRoleNoTasks(String role) {
    return 'Nothing given to $role yet';
  }

  @override
  String teamRoleGiveTask(String role) {
    return 'Give $role a task';
  }

  @override
  String get teamRoleSave => 'Save';

  @override
  String get teamRoleCreate => 'Create role';

  @override
  String teamRoleReset(String role) {
    return 'Reset $role';
  }

  @override
  String teamRoleResetTitle(String role) {
    return 'Reset $role?';
  }

  @override
  String get teamRoleResetBody =>
      'Its name, purpose, instructions and model go back to how they shipped.';

  @override
  String teamRoleDelete(String role) {
    return 'Delete $role';
  }

  @override
  String teamRoleDeleteTitle(String role) {
    return 'Delete $role?';
  }

  @override
  String teamRoleDeleteBody(String role) {
    return '$role is removed from this team. Tasks it already did keep its name.';
  }

  @override
  String get teamRoleWorkerName => 'Worker name';

  @override
  String get teamRoleExamplesLabel => 'Start from an example';

  @override
  String get teamRoleExampleDocs => 'Docs writer';

  @override
  String get teamRoleExampleDocsPurpose => 'Writes and updates the guides';

  @override
  String get teamRoleExampleDocsInstructions =>
      'You write and update documentation. Keep it short, accurate and in plain words. Check every command and path you mention before writing it down.';

  @override
  String get teamRoleExampleSecurity => 'Security reviewer';

  @override
  String get teamRoleExampleSecurityPurpose =>
      'Looks for ways the code could be abused';

  @override
  String get teamRoleExampleSecurityInstructions =>
      'You review code for security problems: secrets in code or logs, unchecked input, unsafe links, and missing permission checks. Report what you find with the file and line, and fix only what the task asks for.';

  @override
  String get teamRoleExampleDesigner => 'Designer';

  @override
  String get teamRoleExampleDesignerPurpose =>
      'Makes it clear, consistent and pleasant';

  @override
  String get teamRoleExampleDesignerInstructions =>
      'You improve how the product looks and reads. Reuse the parts and words already in the app, keep one design language, and check small screens and large text.';

  @override
  String get teamRoleStarterInstructions =>
      'You are the ___ on this team.\nFocus on: ___\nAlways: ___\nNever: ___';

  @override
  String get teamStartRunWho => 'Who';

  @override
  String get teamStartRunWhoSuggested => 'Suggested from your words';

  @override
  String get teamStartRunWhoChange => 'Change';

  @override
  String get teamStartRunWhoTitle => 'Who should take this?';

  @override
  String teamChatLeadStartingRole(String role, String title) {
    return '$role started on “$title”';
  }

  @override
  String teamChatLeadClaimedRole(String role, String title) {
    return '$role took “$title”';
  }

  @override
  String teamChatLeadStartingItRole(String role) {
    return '$role started';
  }

  @override
  String teamChatLeadClaimedItRole(String role) {
    return '$role took the task';
  }

  @override
  String get teamRolesSearchAliases =>
      'roles personas agents team frontend backend tester product designer instructions';

  @override
  String get teamUiStatePhoneStoppedTitle => 'AI Team stopped';

  @override
  String get teamUiStatePhoneStoppedBody =>
      'AI Team on this phone isn’t running. Start it to continue your tasks.';

  @override
  String get teamStartStepService => 'Starting the team’s service';

  @override
  String get teamStartStepAnswering => 'Waiting for the team to answer';

  @override
  String get teamStartStepStore => 'Opening the task store';

  @override
  String get teamStartStepAgents => 'Getting the agents ready';

  @override
  String get teamStartSlow => 'Taking longer than usual, the phone is busy';

  @override
  String get teamStartAgain => 'Start again';

  @override
  String get teamUiHostPhraseStarting => 'Starting';

  @override
  String get teamUiStartOnPhone => 'Start AI Team on this phone';

  @override
  String get chatCollapseAllSteps => 'Collapse all steps';

  @override
  String chatWatchTeamInstructions(int count, String time) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Instructions from the team · $countString words · $time',
      one: 'Instructions from the team · 1 word · $time',
    );
    return '$_temp0';
  }

  @override
  String get chatWatchEmptyStartingTitle => 'Starting';

  @override
  String get chatWatchEmptyStartingBody => 'Its steps appear here as it works.';

  @override
  String chatWatchEmptyWorkingOn(String task) {
    return 'Working on “$task”. Its steps appear here as it works.';
  }

  @override
  String chatWatchEmptyReviewing(String task) {
    return 'Reviewing the changes of “$task”. Its steps appear here as it works.';
  }

  @override
  String get chatWatchEmptyIdleTitle => 'Waiting';

  @override
  String get chatWatchEmptyIdleBody =>
      'It is waiting for its next task. Message it below to ask for something.';

  @override
  String get teamUiAgentLabelSessionTitle => 'Conversation title';

  @override
  String get kitComposerPillNoAnswer => 'No answer yet';

  @override
  String get kitComposerRailRetry => 'Try again';

  @override
  String get teamProjectHome => 'AI Team';

  @override
  String get teamProjectDemo => 'Demo';

  @override
  String get teamProjectNew => 'New project';

  @override
  String get teamProjectQuick => 'Give a quick task';

  @override
  String get teamProjectSettings => 'Project settings';

  @override
  String get teamProjectRoles => 'Roles and agents';

  @override
  String get teamProjectEmpty => 'Give your team a goal to start a project.';

  @override
  String get teamProjectSelect => 'Select a project';

  @override
  String get teamProjectSelectTask =>
      'Select a task to follow its conversation.';

  @override
  String get teamProjectLoad => 'Loading projects';

  @override
  String get teamProjectRetry => 'Try again';

  @override
  String get teamProjectError =>
      'The project could not be updated. Your saved work is still available.';

  @override
  String get teamProjectSpec => 'Open spec';

  @override
  String get teamProjectPlan => 'Review plan';

  @override
  String get teamProjectBoard => 'Board';

  @override
  String get teamProjectGraph => 'Dependencies';

  @override
  String get teamProjectTimeline => 'Timeline';

  @override
  String get teamProjectServers => 'Servers';

  @override
  String get teamProjectMilestones => 'Milestones';

  @override
  String get teamProjectLanes => 'Lanes';

  @override
  String get teamProjectMerge => 'Merge queue';

  @override
  String get teamProjectCost => 'Cost';

  @override
  String get teamProjectDecisions => 'Recent decisions';

  @override
  String get teamProjectPause => 'Pause project';

  @override
  String get teamProjectResume => 'Resume project';

  @override
  String get teamProjectStopConfirmTitle => 'Stop this project?';

  @override
  String get teamProjectStop => 'Stop project';

  @override
  String get teamProjectStopBody =>
      'Running tasks will stop. Their work and project history will be kept.';

  @override
  String get teamProjectAdvance => 'Advance demo';

  @override
  String get teamProjectDigest => 'Since you were away';

  @override
  String get teamProjectDigestRead => 'Mark as read';

  @override
  String get teamProjectAnswer => 'Answer';

  @override
  String get teamProjectAnswerLabel => 'Your answer';

  @override
  String get teamProjectAll => 'Everything';

  @override
  String get teamProjectMerges => 'Merges';

  @override
  String get teamProjectProblems => 'Problems';

  @override
  String get teamProjectMilestoneFilter => 'Milestone';

  @override
  String get teamProjectRepoFilter => 'Repo';

  @override
  String get teamProjectServerFilter => 'Server';

  @override
  String get teamProjectBacklog => 'Backlog';

  @override
  String get teamProjectReady => 'Ready';

  @override
  String get teamProjectWorking => 'Working';

  @override
  String get teamProjectReview => 'Review';

  @override
  String get teamProjectDone => 'Done';

  @override
  String get teamProjectNoTasks => 'No tasks in this view.';

  @override
  String get teamProjectMove => 'Move task';

  @override
  String get teamProjectMoveTo => 'Move to server';

  @override
  String get teamProjectHandoff => 'Hand-off note';

  @override
  String get teamProjectPaused => 'Paused';

  @override
  String get teamProjectStopped => 'Stopped';

  @override
  String get teamProjectFailed => 'Stopped unexpectedly';

  @override
  String get teamProjectStalled => 'No recent progress';

  @override
  String get teamProjectPlanning => 'Shaping the spec';

  @override
  String get teamProjectPlanWaiting => 'Plan ready to review';

  @override
  String get teamProjectNeedsYou => 'Needs your decision';

  @override
  String get teamProjectWaiting => 'Waiting for dependencies';

  @override
  String get teamProjectOnline => 'Reachable';

  @override
  String get teamProjectOffline => 'Not reachable · last known tasks';

  @override
  String get teamProjectNoLimit => 'No limit';

  @override
  String get teamProjectUnknown => 'Not reported';

  @override
  String get teamProjectAcceptMilestoneConfirmTitle => 'Accept this milestone?';

  @override
  String get teamProjectAccept => 'Accept milestone';

  @override
  String get teamProjectMergeConfirmTitle => 'Merge into dev?';

  @override
  String get teamProjectMergeNext => 'Merge checked work into dev';

  @override
  String get teamProjectCostDemo =>
      'Demo figures are simulated; device memory, battery, heat and conversation speed have not been measured.';

  @override
  String teamProjectProgress(int done, int total, int working) {
    return '$done of $total tasks complete · $working working';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy of $total lanes busy';
  }

  @override
  String teamProjectSpend(
    String today,
    String daily,
    String spent,
    String total,
  ) {
    return 'Today: $today / $daily. Total: $spent / $total.';
  }

  @override
  String get teamProjectEditorNewProject => 'New project';

  @override
  String get teamProjectEditorQuickTask => 'Quick task';

  @override
  String get teamProjectEditorSpec => 'Living spec';

  @override
  String get teamProjectEditorPlan => 'Review plan';

  @override
  String get teamProjectEditorSettings => 'Project settings';

  @override
  String get teamProjectEditorRoles => 'Roles and agents';

  @override
  String get teamProjectEditorStartPlanning => 'Start planning';

  @override
  String get teamProjectEditorStartTask => 'Start task';

  @override
  String get teamProjectEditorApproveSpec => 'Approve spec';

  @override
  String get teamProjectEditorApprovePlan => 'Approve and start';

  @override
  String get teamProjectEditorSave => 'Save changes';

  @override
  String get teamProjectEditorSaveDraft => 'Save draft';

  @override
  String get teamProjectEditorName => 'Project name';

  @override
  String get teamProjectEditorGoal => 'Goal';

  @override
  String get teamProjectEditorRepos => 'Repos';

  @override
  String get teamProjectEditorRepoName => 'Repo name';

  @override
  String get teamProjectEditorRepoPath => 'Repo folder';

  @override
  String get teamProjectEditorServer => 'Server';

  @override
  String get teamProjectEditorRemove => 'Remove';

  @override
  String get teamProjectEditorAddRepo => 'Add repo';

  @override
  String get teamProjectEditorRole => 'Role';

  @override
  String get teamProjectEditorPlanFirst => 'Plan first';

  @override
  String get teamProjectEditorMode => 'Execution mode';

  @override
  String get teamProjectEditorSingle => 'Single lane';

  @override
  String get teamProjectEditorParallel => 'Parallel agents';

  @override
  String get teamProjectEditorMaxLanes => 'Maximum lanes';

  @override
  String teamProjectEditorCostMeasured(String host, String memory) {
    return 'On $host: about $memory MB of memory per lane, measured. Battery and conversation speed are not measured yet.';
  }

  @override
  String teamProjectEditorCostNotMeasured(String host) {
    return 'Not measured on $host yet. Your conversation stays first.';
  }

  @override
  String get teamProjectEditorCostNoHost =>
      'Choose where the work runs to see what a lane costs there.';

  @override
  String get teamProjectEditorThisPhone => 'this phone';

  @override
  String get teamProjectEditorGoalRequired => 'Add a goal.';

  @override
  String get teamProjectEditorRepoMissing => 'Add at least one repo.';

  @override
  String get teamProjectEditorRepoIncomplete =>
      'Finish the repo: a name, a folder and where it runs.';

  @override
  String get teamProjectEditorNoFallback => 'No fallback model';

  @override
  String get teamProjectEditorNoFallbackHint =>
      'The work waits for the main model instead of switching.';

  @override
  String get teamProjectEditorReadOnlyRole =>
      'Read-only: this agent can read the project but not change it.';

  @override
  String get teamProjectEditorReadOnlyShort => 'Read-only';

  @override
  String get teamProjectEditorCharging => 'Only while charging';

  @override
  String get teamProjectEditorReview => 'Review level';

  @override
  String get teamProjectEditorMilestonesRisk => 'Milestones and risky points';

  @override
  String get teamProjectEditorEveryStep => 'Every step';

  @override
  String get teamProjectEditorBudget => 'Budget';

  @override
  String get teamProjectEditorSetLimits => 'Set limits';

  @override
  String get teamProjectEditorNoLimit => 'No limit';

  @override
  String get teamProjectEditorDailyBudget => 'Per day (USD)';

  @override
  String get teamProjectEditorTotalBudget => 'Total (USD)';

  @override
  String get teamProjectEditorTaskTokens => 'Token limit per task (optional)';

  @override
  String get teamProjectEditorAutoFix => 'Fix findings automatically';

  @override
  String get teamProjectEditorMaxRounds => 'Maximum fix rounds';

  @override
  String get teamProjectEditorConstraints => 'Constraints';

  @override
  String get teamProjectEditorDecisions => 'Decisions';

  @override
  String get teamProjectEditorOutOfScope => 'Out of scope';

  @override
  String get teamProjectEditorMilestones => 'Milestones';

  @override
  String get teamProjectEditorMilestoneTitle => 'Milestone title';

  @override
  String get teamProjectEditorCriteria => 'Acceptance criteria (one per line)';

  @override
  String get teamProjectEditorMoveUp => 'Move up';

  @override
  String get teamProjectEditorMoveDown => 'Move down';

  @override
  String get teamProjectEditorAddMilestone => 'Add milestone';

  @override
  String get teamProjectEditorHistory => 'Version history';

  @override
  String get teamProjectEditorVersion => 'Version';

  @override
  String get teamProjectEditorPlanHelp =>
      'Review the tasks and their acceptance criteria. Changes here are included when you approve the plan.';

  @override
  String get teamProjectEditorRisky => 'Review gate · risky';

  @override
  String get teamProjectEditorTaskTitle => 'Task title';

  @override
  String get teamProjectEditorRepo => 'Repo';

  @override
  String get teamProjectEditorDependencies => 'Depends on';

  @override
  String get teamProjectEditorRemoveTask => 'Remove task';

  @override
  String get teamProjectEditorRemoteModel => 'The computer\'s model';

  @override
  String get teamProjectEditorAddRole => 'Add role';

  @override
  String get teamProjectEditorRoleName => 'Role name';

  @override
  String get teamProjectEditorInstructions => 'Instructions';

  @override
  String get teamProjectEditorModel => 'Model';

  @override
  String get teamProjectEditorFallback => 'Fallback model';

  @override
  String get teamProjectEditorAllRoles => 'All roles';

  @override
  String get teamProjectEditorChooseMode =>
      'Choose Single lane or Parallel agents.';

  @override
  String get teamProjectEditorPositiveLanes =>
      'Enter a lane limit from 1 to 32.';

  @override
  String get teamProjectEditorChooseBudget =>
      'Set a budget or choose No limit.';

  @override
  String get teamProjectEditorPositiveBudget =>
      'Enter a limit per day and a total limit, each above zero.';

  @override
  String get teamProjectEditorSaveFailed =>
      'Changes could not be saved. Your edits are still here; try saving again.';

  @override
  String get teamProjectEditorRequired => 'Add a goal and at least one repo.';

  @override
  String get teamProjectEditorChooseRoleServer =>
      'Choose a role and a server for this task.';

  @override
  String get teamProjectEditorRepoRequired =>
      'Choose a server and enter the repo name and folder.';

  @override
  String get teamProjectEditorSpecRequired =>
      'Add a goal and at least one milestone with a title and acceptance criteria.';

  @override
  String get teamProjectEditorDraftFailed =>
      'The draft could not be kept on this device. Keep this screen open and try saving again.';

  @override
  String get teamProjectEditorChangedElsewhere =>
      'This project changed while you were editing. Close this sheet and review the latest project before approving changes.';

  @override
  String get teamProjectConversation => 'Task conversation';

  @override
  String get teamProjectTaskMissing => 'This task is no longer available';

  @override
  String get teamProjectRefreshTask => 'Refresh task';

  @override
  String get teamProjectTaskSaveFailed =>
      'The change was not saved. Refresh and try again; your message is still here.';

  @override
  String get teamProjectTaskMessage => 'Message the team…';

  @override
  String get teamProjectTaskInstructions => 'Instructions from the team';

  @override
  String get teamProjectTaskPlan => 'Plan';

  @override
  String get teamProjectTaskApprovePlan => 'Approve and start';

  @override
  String get teamProjectTaskReview => 'Review required';

  @override
  String get teamProjectTaskAccepted => 'Accepted';

  @override
  String get teamProjectTaskAcceptPhase => 'Accept phase';

  @override
  String get teamProjectTaskFindings => 'Verification findings';

  @override
  String get teamProjectTaskFix => 'Fix selected';

  @override
  String get teamProjectTaskRecheck => 'Re-check task';

  @override
  String get teamProjectTaskIgnore => 'Ignore selected finding';

  @override
  String get teamProjectTaskIgnoreReason =>
      'Why is this finding safe to ignore?';

  @override
  String get teamProjectTaskReasonRequired =>
      'Enter a reason to keep with this decision.';

  @override
  String get teamProjectTaskCritical => 'Critical';

  @override
  String get teamProjectTaskMajor => 'Major';

  @override
  String get teamProjectTaskMinor => 'Minor';

  @override
  String get teamProjectTaskMerge => 'Merge queue to dev';

  @override
  String get teamProjectTaskMergeRun => 'Check and merge to dev';

  @override
  String get teamProjectTaskPromoteConfirmTitle => 'Promote dev to main?';

  @override
  String get teamProjectTaskPromote => 'Promote dev to main';

  @override
  String get teamProjectTaskPromoteBody =>
      'This updates protected main to the dev commit you reviewed. The engine will check both commits again before changing main.';

  @override
  String get teamProjectTaskPromotion => 'Protected branch';

  @override
  String get teamProjectTaskDiff => 'View changes';

  @override
  String get teamProjectTaskPause => 'Pause task';

  @override
  String get teamProjectTaskResume => 'Resume task';

  @override
  String get teamProjectTaskStopConfirmTitle => 'Stop this task?';

  @override
  String get teamProjectTaskStop => 'Stop task';

  @override
  String get teamProjectTaskStopBody =>
      'Stop this task and keep its conversation and changes for review.';

  @override
  String get teamProjectTaskRestart => 'Start task again';

  @override
  String get teamProjectTaskAnswer => 'Send answer';

  @override
  String get teamProjectTaskAnswerLabel => 'Your answer';

  @override
  String get teamProjectTaskRunning => 'Working';

  @override
  String get teamProjectTaskWaiting => 'Waiting';

  @override
  String get teamProjectTaskDone => 'Done';

  @override
  String get teamProjectTaskFailed => 'Task stopped before finishing';

  @override
  String get teamProjectTaskStale => 'Last known state';

  @override
  String get teamProjectTaskCollapse => 'Collapse all';

  @override
  String get teamProjectTaskWork => 'Work completed';

  @override
  String get teamProjectTaskEmpty =>
      'The task is queued. Its replies and checks will appear here.';

  @override
  String get teamProjectTaskReceipt => 'Promotion receipt';

  @override
  String get teamProjectTaskVerify => 'Verify task';

  @override
  String get teamProjectTryDemo => 'Try AI Team demo';

  @override
  String get teamProjectLoadFailure =>
      'The demo could not be opened. Your saved project data has been kept.';

  @override
  String get teamProjectInboxOpen => 'Review project decision';

  @override
  String get teamProjectDemoDisclosure =>
      'Simulated projects. No agents run and no repositories change.';

  @override
  String get teamProjectOff => 'Leave demo';

  @override
  String get teamProjectEditorFixRoundsRange =>
      'Enter a fix-round limit from 0 to 3.';

  @override
  String get teamProjectEditorPositiveTokens =>
      'Enter a positive token limit or leave it empty.';

  @override
  String get teamProjectEditorReloadConfirmTitle => 'Refresh this project?';

  @override
  String get teamProjectEditorReload => 'Refresh latest project';

  @override
  String get teamProjectEditorDiscardDraft =>
      'This replaces your unsaved edits with the latest project. Your saved project is kept.';

  @override
  String get teamProjectEditorRoleRequired => 'Enter a name for this role.';

  @override
  String get teamProjectEditorDefaults => 'New project defaults';

  @override
  String get teamProjectEditorApplyPlan => 'Apply updated plan';

  @override
  String get teamProjectEditorContextFiles => 'Files to read first';

  @override
  String get teamProjectEditorContextFilesHelp =>
      'Optional. One path per line. The team reads these before it plans. The demo does not read or upload files.';

  @override
  String get teamProjectEditorScreenOff => 'Keep working with the screen off';

  @override
  String get teamProjectEditorScreenOffHelp =>
      'This preference is saved for the project. Background work remains subject to the server and system limits.';

  @override
  String get teamProjectEditorDraftApproval =>
      'Draft changes need your approval before they become the project spec.';

  @override
  String get teamProjectEditorChangeRequest =>
      'What should the planner change?';

  @override
  String get teamProjectEditorAskChange => 'Ask to change';

  @override
  String get teamProjectEditorChangeRequired =>
      'Add a goal and describe the change you want.';

  @override
  String get teamProjectTaskApprovedPlan => 'Approved plan';

  @override
  String get teamProjectTaskCriteria => 'Acceptance criteria';

  @override
  String get teamProjectTaskOpenFindings => 'Open findings';

  @override
  String get teamProjectTaskFindingsAddressed => 'Findings addressed';

  @override
  String get teamProjectMergeConfirmBody =>
      'Checked task branches will merge into dev, followed by combined checks. Main stays unchanged.';

  @override
  String get teamProjectEditorDraftClearFailed =>
      'Changes were saved, but the local draft could not be cleared. Close this sheet and review the project before trying again.';

  @override
  String get teamProjectRestartElsewhereConfirmTitle => 'Start over elsewhere?';

  @override
  String get teamProjectRestartElsewhere => 'Start over elsewhere';

  @override
  String get teamProjectRestartElsewhereBody =>
      'Start a new attempt on this server. The previous branch stays on its original server.';

  @override
  String get teamProjectWaitForServer => 'Wait for the original server';

  @override
  String get teamProjectBudgetNear =>
      'Approaching your budget. New work pauses at your chosen limit.';

  @override
  String get teamProjectDemoPlanFailure => 'Demo: unreadable plan';

  @override
  String get teamProjectTaskReviewFindings => 'Select open findings';

  @override
  String get teamProjectTaskResolveAgent => 'Resolve with agent';

  @override
  String get teamProjectTaskResolveManually => 'I’ll resolve';

  @override
  String get teamProjectTaskRecheckResolution => 'Re-check resolution';

  @override
  String get teamProjectTaskVerificationResults => 'Verification results';

  @override
  String get teamProjectTaskCriterionMet => 'Met';

  @override
  String get teamProjectTaskCriterionUnmet => 'Unmet';

  @override
  String get teamProjectTaskCriterionNotApplicable => 'Not applicable';

  @override
  String get teamProjectTaskDemoConflict => 'Demo: create a conflict';

  @override
  String get teamProjectTaskDemoCommit => 'Demo: add a manual commit';

  @override
  String get teamProjectEditorNoOptions =>
      'No options are available yet. Return to AI Team to add a server or role.';

  @override
  String get teamProjectEditorUnknownDate => 'Date unavailable';

  @override
  String get teamProjectEditorYou => 'You';

  @override
  String get teamProjectEditorApprovedBy => 'Approved by';

  @override
  String get teamProjectEditorPlanFailed =>
      'The planner did not return a usable plan. Keep the goal as one task, or ask for a new plan.';

  @override
  String get teamProjectEditorUseAsTask => 'Use as one task';

  @override
  String get teamProjectEditorAskAgain => 'Ask again';

  @override
  String get teamProjectDemoChip => 'Demo';

  @override
  String teamProjectHeadlineMilestone(int current, int total, int working) {
    return 'Milestone $current of $total · $working working';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return 'All $total milestones done';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    return 'Spec approved $age · $milestones milestones · $repos repos';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    return 'Draft spec · $milestones milestones · $repos repos';
  }

  @override
  String get teamProjectOpenSpec => 'Open spec';

  @override
  String teamProjectRequestWhere(String role, String server) {
    return '$role on $server';
  }

  @override
  String teamProjectRequestBlocks(String role, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks after it wait too',
      one: '1 task after it waits too',
    );
    return '$role waits; $_temp0';
  }

  @override
  String teamProjectMilestoneTasks(int done, int total) {
    return '$done of $total tasks';
  }

  @override
  String teamProjectMilestoneWaits(int number) {
    return 'Waits on $number';
  }

  @override
  String get teamProjectMilestoneNoTasks => 'No tasks yet';

  @override
  String teamProjectLanesTitle(int busy, int total) {
    return 'Lanes $busy/$total busy';
  }

  @override
  String get teamProjectLanesChange => 'Change';

  @override
  String teamProjectLaneRunning(String server, String elapsed) {
    return '$server · $elapsed';
  }

  @override
  String teamProjectLaneWaiting(String server) {
    return '$server · waiting for a free lane';
  }

  @override
  String teamProjectLaneTitle(String role, String task) {
    return '$role · $task';
  }

  @override
  String teamProjectLaneNoteParallel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lanes',
      one: '1 lane',
    );
    return 'Parallel · $_temp0';
  }

  @override
  String get teamProjectLaneNoteSingle => 'Single lane';

  @override
  String teamProjectLaneNoteDemo(String note) {
    return '$note · figures are simulated';
  }

  @override
  String teamProjectElapsedSeconds(int count) {
    return '$count s';
  }

  @override
  String teamProjectElapsedMinutes(int count) {
    return '$count min';
  }

  @override
  String teamProjectElapsedHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String teamProjectCostToday(String amount) {
    return '$amount today';
  }

  @override
  String teamProjectCostTodayOf(String amount, String limit) {
    return '$amount of $limit today';
  }

  @override
  String teamProjectCostTotal(String amount) {
    return '$amount total';
  }

  @override
  String teamProjectCostTotalOf(String amount, String limit) {
    return '$amount of $limit total';
  }

  @override
  String get teamProjectCostNoLimit => 'No limit set';

  @override
  String get teamProjectCostNotReported => 'Not reported yet';

  @override
  String teamProjectBoardSummary(int tasks, int milestones) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tasks',
      one: '1 task',
    );
    String _temp1 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones milestones',
      one: '1 milestone',
    );
    return '$_temp0 across $_temp1';
  }

  @override
  String get teamProjectBoardEmpty => 'No tasks yet';

  @override
  String teamProjectTimelineSummary(int count, String age) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count events',
      one: '1 event',
    );
    return '$_temp0 · latest $age';
  }

  @override
  String get teamProjectTimelineEmpty => 'Nothing has happened yet';

  @override
  String teamProjectServersSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servers',
      one: '1 server',
    );
    return '$_temp0';
  }

  @override
  String teamProjectSettingsSummaryParallel(int count) {
    return 'Parallel · up to $count lanes';
  }

  @override
  String get teamProjectSettingsSummarySingle => 'Single lane';

  @override
  String get teamProjectMenu => 'Project menu';

  @override
  String get teamProjectTaskMenu => 'Task menu';

  @override
  String teamProjectDecisionBy(String who, String age) {
    return '$who · $age';
  }

  @override
  String get teamProjectYou => 'You';

  @override
  String teamProjectPlanFor(int number) {
    return 'Plan for milestone $number · waiting for you';
  }

  @override
  String get teamProjectPlanForProject => 'Plan · waiting for you';

  @override
  String teamProjectPlanSummary(int phases, int tasks, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      phases,
      locale: localeName,
      other: '$phases phases',
      one: '1 phase',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tasks',
      one: '1 task',
    );
    String _temp2 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return '$_temp0 · $_temp1 · $_temp2';
  }

  @override
  String teamProjectPlanPhase(int number, String title) {
    return 'Phase $number · $title';
  }

  @override
  String get teamProjectPlanReviewPoint => 'Review gate · risky';

  @override
  String teamProjectPlanRepo(String name) {
    return '$name repo';
  }

  @override
  String teamProjectPlanAfter(int number) {
    return 'after $number';
  }

  @override
  String teamProjectPlanCriteria(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count criteria',
      one: '1 criterion',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPlanWho(String role, String server) {
    return '$role · $server';
  }

  @override
  String teamProjectPlanMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '… $count more tasks',
      one: '… 1 more task',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPlanEdit => 'Edit plan';

  @override
  String get teamProjectPlanAsk => 'Ask to change';

  @override
  String get teamProjectPlanNotYet => 'Not yet';

  @override
  String teamProjectPlanServerTitle(String task) {
    return 'Run \"$task\" on';
  }

  @override
  String get teamProjectPlanServerFixed =>
      'Each task runs on the computer it was planned for. This team cannot move tasks to another computer yet.';

  @override
  String teamProjectMergeEffectDev(String repo, int tasks) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks checked tasks',
      one: '1 checked task',
    );
    return '$_temp0 from $repo will be merged into dev.';
  }

  @override
  String get teamProjectMergeEffectMain => 'Main is not touched.';

  @override
  String teamProjectReceiptMerged(String repo) {
    return 'Merged into dev · $repo';
  }

  @override
  String teamProjectReceiptPromoted(String repo) {
    return 'Promoted to main · $repo';
  }

  @override
  String teamProjectTimelineRepeated(String text, int count) {
    return '$text · $count times';
  }

  @override
  String get teamProjectPromoteTitle => 'Promote dev → main';

  @override
  String teamProjectPromoteStatus(String repo) {
    return '$repo repo · main is protected. Only you can promote.';
  }

  @override
  String teamProjectPromoteMilestone(int number, String title) {
    return 'Milestone $number · $title';
  }

  @override
  String teamProjectPromoteMerged(int done, int total) {
    return '$done of $total tasks merged';
  }

  @override
  String teamProjectPromoteDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count days',
      one: '1 day',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteChecks => 'Checks after merge';

  @override
  String get teamProjectPromoteChecksPassed =>
      'Every check passed after the last merge';

  @override
  String get teamProjectPromoteReview => 'Review';

  @override
  String teamProjectPromoteAccepted(int number) {
    return 'You accepted milestone $number';
  }

  @override
  String get teamProjectPromoteNoReview => 'No review was needed for this work';

  @override
  String teamProjectPromoteChanges(int commits) {
    String _temp0 = intl.Intl.pluralLogic(
      commits,
      locale: localeName,
      other: '$commits commits',
      one: '1 commit',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPromoteFiles(int files) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files files',
      one: '1 file',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteSeeChanges => 'See changes';

  @override
  String get teamProjectPromoteNotYet => 'Not yet';

  @override
  String teamProjectFindingsTitle(String role, String summary) {
    return 'Checked by $role · $summary';
  }

  @override
  String get teamProjectEditorGoalLabel => 'What should the team achieve?';

  @override
  String get teamProjectEditorWhereRuns => 'Where it runs';

  @override
  String get teamProjectEditorMoreOptions => 'Optional details';

  @override
  String get teamProjectEditorNameHelp =>
      'Optional. Leave empty to use the start of the goal.';

  @override
  String get teamProjectEditorBudgetHelp =>
      'The team pauses when the day or the whole project reaches its limit.';

  @override
  String teamProjectFindingsCritical(int count) {
    return '$count critical';
  }

  @override
  String teamProjectFindingsMajor(int count) {
    return '$count major';
  }

  @override
  String teamProjectFindingsMinor(int count) {
    return '$count minor';
  }

  @override
  String get phoneTeamSetupTitle => 'Turn on AI Team';

  @override
  String get phoneTeamStepReply => 'Let your current reply finish';

  @override
  String get phoneTeamReplyWaiting => 'Finishing your current reply…';

  @override
  String get phoneTeamStepStop => 'Stop OpenCode and close terminals';

  @override
  String get phoneTeamStepCheck => 'Checking this phone is safe for the team';

  @override
  String get phoneTeamStepServer => 'Start OpenCode again, protected';

  @override
  String get phoneTeamNotNeeded => 'Not needed';

  @override
  String get phoneTeamStopTitle => 'Stop OpenCode briefly?';

  @override
  String get phoneTeamStopBody =>
      'Stops the OpenCode server on this phone for about a minute, then starts it again protected. Open terminals close.';

  @override
  String get phoneTeamStopConfirm => 'Stop and continue';

  @override
  String get phoneTeamStopCancel => 'Not now';

  @override
  String get phoneTeamStopWaiting => 'Waiting for your answer';

  @override
  String get phoneTeamDoneTitle => 'AI Team is ready';

  @override
  String get phoneTeamDoneBody =>
      'Give it a goal and the team plans, builds and checks the work.';

  @override
  String get phoneTeamFailUnsafeTitle => 'The team stays off';

  @override
  String get phoneTeamFailUnsafeBody =>
      'This phone can\'t keep the team\'s copy of your code separate from the agents, so the team stays off.';

  @override
  String get phoneTeamFailEngineTitle => 'AI Team didn\'t start';

  @override
  String get phoneTeamFailEngineBody =>
      'The team\'s engine didn\'t start on this phone.';

  @override
  String get phoneTeamFailStopTitle => 'The server didn\'t stop';

  @override
  String get phoneTeamFailStopBody =>
      'OpenCode or a terminal didn\'t close, so the check can\'t run safely yet.';

  @override
  String get phoneTeamFailServerTitle => 'The server didn\'t return';

  @override
  String get phoneTeamFailServerBody =>
      'This phone passed the check, but OpenCode didn\'t start again. Start again to finish.';

  @override
  String get phoneTeamFailNotReadyTitle => 'Not ready yet';

  @override
  String get phoneTeamFailNotReadyBody =>
      'OpenCode is back, but the team can\'t start work yet.';

  @override
  String get phoneTeamFailDeclinedTitle => 'Nothing changed';

  @override
  String get phoneTeamFailDeclinedBody =>
      'OpenCode stays as it is, so the team stays off. Start again when you\'re ready to restart it.';

  @override
  String get phoneTeamFailNoServerTitle => 'Add a server first';

  @override
  String get phoneTeamFailNoServerBody =>
      'The team works with the OpenCode on this phone, and there isn\'t one yet.';

  @override
  String get phoneTeamReasonNotPackaged =>
      'This copy of the app doesn\'t include the team\'s safety tools.';

  @override
  String get phoneTeamStateBackOn => 'OpenCode is back on.';

  @override
  String get phoneTeamStateStillOff =>
      'OpenCode is still off. Start again, or restart it from This phone.';

  @override
  String get phoneTeamStateNotStopped => 'OpenCode was not stopped.';

  @override
  String get phoneTeamStateTerminalsClosed => 'Open terminals were closed.';

  @override
  String get phoneTeamWhyUnsafe =>
      'The check that keeps the team\'s copy of your code separate from the agents did not pass.';

  @override
  String get phoneTeamWhyEngine =>
      'The team\'s engine stopped or did not answer while it was starting.';

  @override
  String get phoneTeamWhyStop =>
      'OpenCode or a terminal did not close when asked.';

  @override
  String get phoneTeamWhyServer =>
      'OpenCode did not answer after it was started again.';

  @override
  String get phoneTeamWhyNotReady =>
      'The team\'s engine answered but said it cannot run work yet.';

  @override
  String get phoneTeamDetails => 'Details';

  @override
  String get phoneTeamOffTitle => 'AI Team is off';

  @override
  String get phoneTeamOffBody =>
      'It needs a quick safety check, for example after an app update.';

  @override
  String get phoneTeamBlocked =>
      'The team can\'t start work until this phone is checked.';

  @override
  String get phoneTeamStripChecking => 'Checking AI Team on this phone';

  @override
  String get phoneTeamStripWaiting => 'AI Team needs to restart OpenCode';

  @override
  String get phoneTeamStripReview => 'Open the check to choose when';

  @override
  String get phoneTeamStripFailed => 'AI Team couldn\'t turn on';

  @override
  String phoneTeamStripStep(int step, int total) {
    return 'Step $step of $total';
  }

  @override
  String get phoneTeamBlockedTitle => 'Check this phone first?';

  @override
  String get teamMigrationTitle => 'AI Team has changed';

  @override
  String get teamMigrationBody =>
      'The new AI Team plans whole projects and runs them on this phone. Your old team keeps taking quick tasks but cannot plan projects, and switching asks before it stops anything.';

  @override
  String get teamMigrationSwitch => 'Switch to the new AI Team on this phone';

  @override
  String get teamMigrationKeep => 'Keep the old team for now';

  @override
  String get teamMigrationMenu => 'What\'s new in AI Team';

  @override
  String get teamProjectPages => 'Project pages';

  @override
  String get teamProjectMergeReady => 'Ready to merge';

  @override
  String get teamProjectMergeChecking => 'Waiting for its checks';

  @override
  String get teamRefusalUnsupportedCommand =>
      'This version of the team can\'t do that yet.';

  @override
  String get teamRefusalUnsupportedCommandNext =>
      'Update the app, then try again.';

  @override
  String get teamRefusalBoundaryUnverified =>
      'The team can\'t work until this phone\'s protection has been checked.';

  @override
  String get teamRefusalBoundaryUnverifiedNext =>
      'Open AI Team and turn it on again.';

  @override
  String get teamRefusalProtocolUnverified =>
      'The team is waiting for OpenCode to restart.';

  @override
  String get teamRefusalProtocolUnverifiedNext => 'Try again in a minute.';

  @override
  String get teamRefusalEngineUnavailable =>
      'The team isn\'t answering right now.';

  @override
  String get teamRefusalEngineUnavailableNext => 'Try again in a moment.';

  @override
  String get teamRefusalTransportUncertain =>
      'The team may not have received that.';

  @override
  String get teamRefusalTransportUncertainNext =>
      'Check the project list before you try again.';

  @override
  String get teamRefusalBusy => 'Another change is still being saved.';

  @override
  String get teamRefusalBusyNext => 'Try again in a moment.';

  @override
  String get teamRefusalSaveFailed =>
      'The change couldn\'t be saved on this phone.';

  @override
  String get teamRefusalSaveFailedNext =>
      'Your edits are still here. Try again.';

  @override
  String get teamRefusalReadOnly => 'The team is read-only right now.';

  @override
  String get teamRefusalReadOnlyNext =>
      'Turn on AI Team on this phone to make changes.';

  @override
  String get teamRefusalClosed => 'The team has been closed.';

  @override
  String get teamRefusalClosedNext => 'Open AI Team again to continue.';

  @override
  String get teamRefusalCommandRefused => 'The team turned this request down.';

  @override
  String get teamRefusalCommandRefusedNext =>
      'Check the goal and the repository folder, then try again.';

  @override
  String get teamRefusalImportFailed =>
      'The team couldn\'t read the repository folder.';

  @override
  String get teamRefusalImportFailedNext =>
      'Check the folder name, then try again.';

  @override
  String get teamRefusalPayloadInvalid =>
      'The team answered in a way this app doesn\'t understand.';

  @override
  String get teamRefusalPayloadInvalidNext => 'Update the app, then try again.';

  @override
  String get teamRefusalSchemaUnsupported =>
      'The team and this app are on different versions.';

  @override
  String get teamRefusalSchemaUnsupportedNext =>
      'Update the app, then try again.';

  @override
  String get teamRefusalEngineClosed => 'The team is shutting down.';

  @override
  String get teamRefusalEngineClosedNext =>
      'Turn on AI Team again to continue.';

  @override
  String get teamRefusalSessionFailed =>
      'The planner stopped before it answered.';

  @override
  String get teamRefusalSessionFailedNext =>
      'Check the model in Team settings › Model, then approve the spec again.';

  @override
  String get teamRefusalModelNotConfigured => 'The team needs a model.';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      'Pick one in Team settings › Model.';

  @override
  String get teamRefusalModelUnavailable =>
      'The chosen model isn\'t available.';

  @override
  String get teamRefusalModelUnavailableNext =>
      'Pick another model in Team settings › Model.';

  @override
  String get teamRefusalAuthFailed =>
      'The model\'s provider didn\'t accept the sign-in.';

  @override
  String get teamRefusalAuthFailedNext =>
      'Check the provider\'s key, then approve the spec again.';

  @override
  String get teamRefusalCloneFailed =>
      'The team couldn\'t copy the project to work on it.';

  @override
  String get teamRefusalCloneFailedNext =>
      'Check the project\'s repository, then approve the spec again.';

  @override
  String get teamRefusalSessionUncertain =>
      'The team isn\'t sure how far its last run got.';

  @override
  String get teamRefusalSessionUncertainNext =>
      'Resume if you can, or approve the spec again.';

  @override
  String get teamRefusalPlanInvalid =>
      'The plan that came back couldn\'t be used.';

  @override
  String get teamRefusalPlanInvalidNext =>
      'Approve the spec again and the team will plan again.';

  @override
  String get teamRefusalNeedsAnswer => 'The planner has a question for you.';

  @override
  String get teamRefusalNeedsAnswerNext => 'Open the spec and answer it.';

  @override
  String get teamRefusalRecoveryReview =>
      'The work stopped part way and needs a look.';

  @override
  String get teamRefusalRecoveryReviewNext =>
      'Check the project, then approve the spec again.';

  @override
  String get teamRefusalAppStopped =>
      'The app closed before the team finished.';

  @override
  String get teamRefusalAppStoppedNext => 'Resume to check where it got to.';

  @override
  String get teamRefusalChatBusy =>
      'The team is waiting for your conversation to finish replying.';

  @override
  String get teamRefusalChatBusyNext => 'It carries on by itself afterwards.';

  @override
  String get teamRefusalBudgetReached =>
      'The project reached its spending limit.';

  @override
  String get teamRefusalBudgetReachedNext =>
      'Raise the limit in the project\'s settings to go on.';

  @override
  String get teamRefusalModelNotConfiguredAction => 'Pick a model';

  @override
  String get teamProjectPlanFailedTitle => 'The plan wasn\'t made';

  @override
  String get teamProjectApproveAgain => 'Approve the spec again';

  @override
  String get teamProjectApproveAgainNote => 'Approve the spec again to retry.';

  @override
  String get teamProjectTaskWorkLive => 'Work so far';

  @override
  String get teamProjectTaskWorkLog => 'Work log';

  @override
  String get teamRefusalDidPlan => 'start planning';

  @override
  String get teamRefusalDidQuick => 'start that task';

  @override
  String get teamRefusalDidApprove => 'start the work';

  @override
  String get teamRefusalDidSpec => 'approve the spec';

  @override
  String get teamRefusalDidPromote => 'promote the work';

  @override
  String get teamRefusalDidStop => 'stop the project';

  @override
  String get teamRefusalDidPause => 'pause the project';

  @override
  String get teamRefusalDidResume => 'resume the project';

  @override
  String get teamRefusalDidSave => 'save your changes';

  @override
  String teamRefusalUnknown(String action) {
    return 'The team couldn\'t $action.';
  }

  @override
  String get teamRefusalUnknownNext =>
      'Try again. If it keeps happening, open Details for the code.';

  @override
  String get teamRefusalCode => 'Code';

  @override
  String get phoneTeamProtectedProot =>
      'Protected by this phone\'s Linux sandbox';

  @override
  String get phoneTeamProtectedLandlock =>
      'Protected by Android\'s file protection';

  @override
  String get teamRefusalRepositoryEmpty =>
      'This repository has no commits yet.';

  @override
  String get teamRefusalRepositoryEmptyNext =>
      'Make a first commit in it, then start planning again.';

  @override
  String get teamRefusalRepositoryLink =>
      'The team couldn\'t safely copy this repository.';

  @override
  String get teamRefusalRepositoryLinkNext =>
      'Try again. If it keeps happening, report the problem.';

  @override
  String get teamRefusalRepositoryDamaged =>
      'The repository copy didn\'t match the original.';

  @override
  String get teamRefusalRepositoryDamagedNext =>
      'Try again. If it keeps happening, report the problem.';

  @override
  String get teamRefusalPlanTaskName => 'A task in the plan has no name.';

  @override
  String get teamRefusalPlanTaskNameNext =>
      'Give every task a name, then approve again.';

  @override
  String get teamRefusalPlanPhase => 'A phase in the plan isn\'t complete.';

  @override
  String get teamRefusalPlanPhaseNext =>
      'Check each phase has tasks and criteria, then approve again.';

  @override
  String get teamServerPhoneFailed =>
      'AI Team on this phone isn\'t answering, so its work can\'t be reached.';

  @override
  String get teamServerPhoneNotReady =>
      'AI Team on this phone isn\'t ready: its safety check hasn\'t passed.';

  @override
  String get teamServerPhoneNoAnswer =>
      'OpenCode on this phone didn\'t answer the last check.';

  @override
  String get phoneTeamOffReview => 'Review and choose when';

  @override
  String teamProjectInterruptedRow(String name) {
    return '$name: work was interrupted, tap to resume';
  }

  @override
  String get teamProjectResumeUnavailable =>
      'This version of AI Team can\'t resume interrupted work yet. You can stop the project and start it again.';

  @override
  String get teamProjectInterrupted => 'Interrupted, ready to resume';

  @override
  String get teamProjectEditorContextFilesHelpReal =>
      'Optional. One path per line. The team reads these before it plans.';

  @override
  String get addServerCleartextWarning =>
      'This address uses plain HTTP. On this network, others could read your password and conversations. Use Tailscale or HTTPS if you can.';

  @override
  String get addServerCleartextConfirm => 'Use it anyway';

  @override
  String get addServerCleartextConfirmed =>
      'Plain HTTP is on for this address. Anyone on this network could read what you send.';

  @override
  String get storageAccessTitle => 'Allow access to files?';

  @override
  String get storageAccessBody =>
      'This folder is in your phone’s shared storage. Android hides the files in it from apps unless you allow All files access.';

  @override
  String get storageAccessWhyScope =>
      'The app reads and changes files only in folders you open as projects.';

  @override
  String get storageAccessWhyAgent =>
      'The agent needs it to work in your folder in place. Without it you would see only hidden items such as .git.';

  @override
  String get storageAccessWhyOff =>
      'You can turn it off any time in Android Settings, under All files access.';

  @override
  String get storageAccessAllow => 'Allow access to files';

  @override
  String get storageAccessNotNow => 'Not now';

  @override
  String get storageAccessUseAppSpace => 'Use the app’s project space';

  @override
  String get storageAccessRefusedTitle => 'Folder not opened';

  @override
  String get storageAccessRefusedBody =>
      'Without access to files this folder’s files cannot be shown, so it was not opened. Allow access, or choose a folder in the app’s project space.';

  @override
  String get storageTermuxTitle => 'Allow Termux storage?';

  @override
  String get storageTermuxBody =>
      'Your server runs in Termux, and Termux cannot read your phone’s shared storage yet. Without that, this folder shows only hidden items such as .git.';

  @override
  String get storageTermuxAllow => 'Open Termux';

  @override
  String get storageTermuxStillTitle => 'Allow storage in Termux';

  @override
  String get storageTermuxStillBody =>
      'In Termux, allow storage when Android asks (the command is termux-setup-storage), then open the folder again.';

  @override
  String get filesAccessNeededTitle => 'Files are hidden';

  @override
  String get filesAccessNeededBody =>
      'This folder is in your phone’s shared storage and the app cannot read most of its files yet. Only hidden items show.';

  @override
  String get filesAccessNeededTermuxBody =>
      'This folder is in your phone’s shared storage and Termux cannot read most of its files yet. Only hidden items show.';

  @override
  String get storageRestartTitle => 'Restart to open folder?';

  @override
  String get storageRestartBody =>
      'OpenCode on this phone has to restart before it can see this folder. This takes about 30 seconds.';

  @override
  String get storageRestartPause =>
      'Running replies and AI Team work pause and carry on afterwards.';

  @override
  String get storageRestartBusy =>
      'A reply or AI Team task is running right now. It will pause while OpenCode restarts.';

  @override
  String get storageRestartConfirm => 'Restart and open';

  @override
  String get storageRestartFailedTitle => 'Restart did not finish';

  @override
  String get storageRestartFailedBody =>
      'The folder was not opened. Try again, or start OpenCode from This phone.';
}

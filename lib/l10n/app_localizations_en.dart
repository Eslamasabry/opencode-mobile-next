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
  String get servicesInvalid =>
      'Enter a name, a foreground command, and an optional HTTP or HTTPS URL without credentials.';

  @override
  String get servicesUnavailable =>
      'This connection cannot start and track development commands. You can save commands and review their preview links here.';

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
  String get servicesRemoveHint =>
      'Remove this saved service and its local ownership record? This does not stop its command on the server. Stop it first if needed.';

  @override
  String get servicesStartHint =>
      'Run this saved command in the project shown below? It uses the server\'s environment. Keep it in the foreground; this panel cannot manage detached processes.';

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
  String get servicesLogTail =>
      'Bounded log tail. Earlier output may be omitted. Logs are kept on the server, not saved on this phone.';

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
      'The server or project changed. Close this sheet and reopen the task from the intended project.';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryBrowseSection => 'Browse';

  @override
  String get libraryManageSection => 'Manage';

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
  String get modelSwitchSession => 'Switch model for this session';

  @override
  String get modelNextRecent => 'Next recent model · F2';

  @override
  String get modelPreviousRecent => 'Previous recent model · Shift+F2';

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
  String get modelDefaultMode => 'Default mode';

  @override
  String get modelSessionScopeNote => 'Applies to this session\'s next turns.';

  @override
  String get modelSelectionLoading => 'Loading session selection…';

  @override
  String get modelServerDefault => 'Server default';

  @override
  String get modelSelectionSaving => 'Saving session selection…';

  @override
  String get modelAgentSaveFailed => 'Could not save the agent. Try again.';

  @override
  String get modelUnavailableSelection =>
      'The session\'s model is unavailable in this catalog. Refresh models or choose another.';

  @override
  String get modelScopeChanged =>
      'The connection changed. Reopen the model selector to continue.';

  @override
  String get commonClearSearch => 'Clear search';

  @override
  String get commonUndo => 'Undo';

  @override
  String get workTitle => 'Running work';

  @override
  String get workDescription => 'Agents and commands related to this chat.';

  @override
  String get workAgents => 'Agents';

  @override
  String get workCommands => 'Commands';

  @override
  String get workEmpty => 'Nothing running';

  @override
  String get workEmptyDescription => 'Finished work stays in the conversation.';

  @override
  String get workRefresh => 'Refresh';

  @override
  String get workClose => 'Close';

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
  String get workViewOutput => 'View output';

  @override
  String get workNoOutput => 'Waiting for output…';

  @override
  String get workNoFinalOutput => 'This command produced no output.';

  @override
  String get workCopyOutput => 'Copy output';

  @override
  String get workCopied => 'Output copied';

  @override
  String get workFollow => 'Follow output';

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
  String get workTimeoutTitle => 'Time remaining';

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
  String get workTimeoutSaved => 'Timeout updated';

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
      'The server or workspace changed. Close this view and reopen Running work.';

  @override
  String workCount(int count) {
    return 'Running work · $count';
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
  String get fileCopy => 'Copy';

  @override
  String get fileReference => 'Reference';

  @override
  String get fileAttach => 'Attach';

  @override
  String get fileSave => 'Save';

  @override
  String get fileReload => 'Reload';

  @override
  String get queueRemoveFailed =>
      'Could not remove this draft from device storage. It is still queued. Check available storage and try again.';

  @override
  String get composerReuseSubtitle =>
      'Reuse text from this conversation and recent sends';

  @override
  String get composerReuseDescription =>
      'Text from loaded prompts in this conversation and recent sends on this server. Selecting one appends it to your draft. Attachments are not copied. With a keyboard, use Up at the start or Down at the end to browse and restore your draft.';

  @override
  String get composerReuseSearch => 'Search recent prompts';

  @override
  String get composerReuseEmpty => 'No matching prompts';

  @override
  String get backgroundSubagentsTitle => 'Background subagents';

  @override
  String get backgroundWorkTitle => 'Move running work to background';

  @override
  String get backgroundWorkShortcut =>
      'Continue this work while you use the chat · Ctrl+B';

  @override
  String get backgroundWorkNoop => 'No foreground subagents to background.';

  @override
  String get backgroundWorkPromoted =>
      'Subagents are continuing in the background.';

  @override
  String get librarySearchHint => 'Find settings, tools, and help';

  @override
  String get libraryDefaultModel => 'Default for new chats';

  @override
  String get libraryNoModel => 'No model selected';

  @override
  String librarySearchResults(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results for “$query”.',
      one: '1 result for “$query”.',
      zero: 'No matching tools for “$query”.',
    );
    return '$_temp0';
  }

  @override
  String get chatAttachmentUnsupported =>
      'Only PNG, JPEG, GIF, WebP, PDF, and text files can be attached.';

  @override
  String get termuxRestartServer => 'Restart local server';

  @override
  String get termuxRestartTitle => 'Restart the local server?';

  @override
  String get termuxRestartMessage =>
      'OpenCode will be briefly unavailable. The app will keep your current workspace and reconnect automatically.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count sessions are generating. Restarting will interrupt them.',
      one: '1 session is generating. Restarting will interrupt it.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'Restart';

  @override
  String get termuxRestarting => 'Restarting local server...';

  @override
  String get termuxRestartProgress =>
      'The installed OpenCode version and saved credential are unchanged. The app will reconnect when the server is ready.';

  @override
  String get termuxRestartSucceeded =>
      'Local server restarted and reconnected.';

  @override
  String get termuxRestartNotPerformed =>
      'Restart was not performed. The existing local server is still running.';

  @override
  String get chatCopyCompleteReply => 'Copy complete reply';

  @override
  String get chatCopyReplySoFar => 'Copy reply so far';

  @override
  String commandRunTitle(String command) {
    return 'Run /$command';
  }

  @override
  String get commandDestination => 'Chat';

  @override
  String get commandNewChat => 'New chat';

  @override
  String get commandUntitledChat => 'Untitled chat';

  @override
  String get commandArguments => 'Arguments (optional)';

  @override
  String get commandRun => 'Run';

  @override
  String get commandRunning => 'Starting…';

  @override
  String get commandLocationChanged =>
      'The server or workspace changed. Close this dialog and open the command again.';

  @override
  String get refreshFailed => 'Couldn’t refresh';

  @override
  String get refreshRetry => 'Retry';

  @override
  String get filesProjectRoot => 'Project root';

  @override
  String filesOpenFolder(String folder) {
    return 'Open folder $folder';
  }

  @override
  String filesCurrentFolder(String folder) {
    return 'Current folder: $folder';
  }

  @override
  String get globalSessionsLoadMore => 'Load more sessions';

  @override
  String get globalSessionsRefreshFailed => 'Could not refresh sessions.';

  @override
  String get workspaceSearchAllSessions => 'Search all sessions';

  @override
  String get workspaceProjectListUnavailable => 'Project list unavailable';

  @override
  String get workspaceProjectListFallback =>
      'Your conversations can still be available. Search all sessions to find previous work.';

  @override
  String get workspaceRetryProjects => 'Retry projects';

  @override
  String get historyLoadOlder => 'Load older messages';

  @override
  String get historyReload => 'Reload recent history';

  @override
  String get historyCursorExpired =>
      'Older history changed or expired. Reload recent history to continue.';

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
      'Rows marked reported by server cover the session. Message counts and other estimates cover loaded history.';

  @override
  String get sessionsLoadedOnly =>
      'Showing loaded sessions. Load more to include older conversations.';

  @override
  String get sessionsDetailsUnavailable =>
      'Session details could not be loaded. Try again.';

  @override
  String get sessionsLoadMore => 'Load more sessions';

  @override
  String get sessionsReload => 'Reload recent sessions';

  @override
  String get sessionsNoLoadedRecent => 'No recent sessions in loaded results';

  @override
  String get sessionsNoLoadedArchived =>
      'No archived sessions in loaded results';

  @override
  String sessionsLoadedCount(int count) {
    return '$count loaded';
  }

  @override
  String get revertStageTitle => 'Stage a revert from this prompt?';

  @override
  String get revertStageDescription =>
      'This prompt and the conversation after it will be hidden while the revert is staged. Review the result before making it permanent.';

  @override
  String get revertApplyFiles => 'Revert file changes too';

  @override
  String get revertApplyFilesHint =>
      'Applies file changes immediately when staging. Clear can restore the staged files from the saved snapshot.';

  @override
  String get revertStageAction => 'Stage and review';

  @override
  String get revertReviewTitle => 'Review staged revert';

  @override
  String get revertReviewChanged =>
      'This session or its staged revert changed. Review the latest state before continuing.';

  @override
  String get revertReviewLatest => 'Review latest state';

  @override
  String get revertBusy => 'Wait for the current session action to finish.';

  @override
  String get revertCancel => 'Cancel';

  @override
  String get revertCommitTitle => 'Make this revert permanent?';

  @override
  String get revertCommitDescription =>
      'Removes the staged conversation history permanently. File changes already applied during staging will remain. You cannot clear this revert afterward.';

  @override
  String get revertCommitAction => 'Make revert permanent';

  @override
  String get revertClearTitle => 'Clear this staged revert?';

  @override
  String get revertClearDescription =>
      'Restores the hidden conversation and the files included in this stage from the saved snapshot. Changes made to those files since staging may be replaced. Queued work may resume.';

  @override
  String get revertClearAction => 'Clear staged revert';

  @override
  String get revertNoStage => 'There is no staged revert to review.';

  @override
  String get revertBoundaryLabel => 'Staged from prompt';

  @override
  String get revertPreviewDescription =>
      'These are the file changes reported for this stage. Staging may already have applied them.';

  @override
  String get revertPreviewUnavailable =>
      'The server did not provide a file preview. This does not establish whether files changed.';

  @override
  String get revertPreviewEmpty =>
      'No file changes were reported for this stage.';

  @override
  String get revertStaged => 'Revert staged';

  @override
  String get revertReview => 'Review';

  @override
  String get revertFromHere => 'Revert from this prompt';

  @override
  String get revertUndoDescription =>
      'Stage a revert and review the affected files';

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
      'Keep a short instruction for this session. Saving or removing it takes effect at the next agent step and appears in the transcript then. It does not start a run.';

  @override
  String get sessionNoteHint =>
      'For example: Keep explanations brief and run the relevant checks before finishing.';

  @override
  String get sessionNoteSave => 'Save note';

  @override
  String get sessionNoteRemove => 'Remove saved note';

  @override
  String get sessionNoteSaved => 'Note saved';

  @override
  String get sessionNoteRemoved => 'Note removed';

  @override
  String get sessionNotePending => 'Applies at the next agent step.';

  @override
  String get sessionInstructionsUpdated => 'Instructions updated';

  @override
  String get sessionInstructionsApplied =>
      'The agent\'s session instructions have been updated for this step.';

  @override
  String get sessionNoteUnsupported =>
      'This server does not support session notes.';

  @override
  String get sessionNoteAuthorization =>
      'Check this server\'s password and permissions, then try again. Your draft is kept.';

  @override
  String get sessionNoteChanged =>
      'The session or its instructions changed. Refresh the saved note before saving again. Your draft is kept.';

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
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit bytes';
  }

  @override
  String get usageTitle => 'Usage and cost';

  @override
  String get usageDescription =>
      'Activity recorded by this OpenCode server across your sessions.';

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
      'The connection changed while loading usage. Refresh to try again.';

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
      'The active server or location changed. Reopen Usage from Settings.';

  @override
  String get usageTinyCost => 'Less than \$0.000001';

  @override
  String get usageReportedCost => 'Reported cost · USD';

  @override
  String get usageSessions => 'Sessions';

  @override
  String get usageSubagents => 'Subagent sessions';

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
  String get usageCostShare => 'Share of reported cost';

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
  String get mcpRuntimeDescription =>
      'Adds this MCP server to the selected location and tries to connect it now. It is removed when OpenCode restarts. For permanent setup, edit the server configuration.';

  @override
  String get mcpCurrentLocation => 'Current location';

  @override
  String get mcpDefaultLocation => 'OpenCode server’s default location';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return 'Workspace: $workspace';
  }

  @override
  String get mcpLocationChanged =>
      'The connection or location changed. Your draft is still here; reopen setup in the intended location before adding it.';

  @override
  String get mcpAdding => 'Adding MCP server';

  @override
  String get mcpAdd => 'Add MCP server';

  @override
  String get mcpRuntimeEmpty =>
      'Add tools for the current location until OpenCode restarts.';

  @override
  String get mcpRuntimeAdded => 'MCP server added for this location';

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
      'Downloads the full session from the server, including older messages.';

  @override
  String get exportMarkdown => 'Readable transcript · Markdown';

  @override
  String get exportMarkdownDescription =>
      'Saves the messages currently loaded in this chat. Load older messages first if you need them included.';

  @override
  String get exportRedact => 'Redact sensitive data';

  @override
  String get exportRedactDescription =>
      'Replaces conversation text and sensitive fields with placeholders. Turn this off to back up the original text. Review any export before sharing.';

  @override
  String get exportUnredacted =>
      'The unredacted file may contain secrets, local paths, and private tool output.';

  @override
  String get exportSave => 'Save file';

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
      'The connection or location changed. Reopen export from the intended conversation.';

  @override
  String get exportUnsupported =>
      'This server does not support JSON export. You can still save the loaded Markdown transcript.';

  @override
  String get exportAuthorization =>
      'The server denied access. Check your connection credentials and try again.';

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
  String importMessageCount(int count) {
    return '$count message records';
  }

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
  String get importChooseDestination => 'Choose a directory on this server';

  @override
  String get importChangeDestination => 'Change destination';

  @override
  String get importNoDestinations =>
      'No project directories are available. Open a project on this server, then try again.';

  @override
  String get importDestinationFailed =>
      'Could not load destination projects or workspaces. Try again; your file is still selected.';

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
      'The conversation was imported, but could not be opened. Find it in All sessions on the destination server.';

  @override
  String get importChanged =>
      'The connection or location changed. Your file is still here. Reopen import on the intended server before continuing.';

  @override
  String get importUnsupported => 'This server does not support JSON import.';

  @override
  String get importInvalidFile =>
      'Choose a valid OpenCode JSON export with session information and message records. Markdown transcripts cannot be imported.';

  @override
  String get importTooLarge =>
      'This file exceeds the mobile import limit of 128 MiB. It has not been uploaded or truncated. Use a desktop or server transfer for this file.';

  @override
  String get importConflict =>
      'A conversation with this ID already exists on this server. Nothing was replaced. Find it in All sessions, or import this file on another server.';

  @override
  String get importAuthorization =>
      'The server denied access. Check your connection credentials. Your file is still selected.';

  @override
  String get importParentMissing =>
      'The parent conversation is missing from this server. Import the parent first, then retry this file.';

  @override
  String get importRejected =>
      'The server rejected this export format. Your file is still selected; check that it came from a compatible OpenCode server.';

  @override
  String get importUnconfirmed =>
      'Import could not be confirmed. Check All sessions before retrying: the server may have received it. Your source file is unchanged.';

  @override
  String get sessionsNoOtherRecent => 'No other recent conversations';

  @override
  String get sessionPin => 'Pin on this device';

  @override
  String get sessionUnpin => 'Unpin';

  @override
  String get sessionPinned => 'Pinned';

  @override
  String get sessionPinFailed =>
      'Could not save this pin. Check device storage and that the session location has not changed, then try again.';

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
  String get promptStashNoMatches =>
      'No saved prompts match your search. Clear or change the search to see more.';

  @override
  String get promptStashDeleteFailed =>
      'Could not delete this saved prompt. Try again.';

  @override
  String get promptRestoreTitle => 'Restore saved prompt?';

  @override
  String get promptRestorePreserve =>
      'Your current prompt will be saved to the stash first, including its attachments and references.';

  @override
  String get promptStashDelete => 'Delete';

  @override
  String get promptStashFull =>
      'Your stash has 50 prompts. Delete a saved prompt to make room; your current prompt is unchanged.';

  @override
  String get promptStashListDescription =>
      'Saved on this device for this server. Restoring a prompt also saves any current prompt for later.';

  @override
  String get promptStashDeleteTitle => 'Delete saved prompt?';

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
  String get promptRestoredCopyKept =>
      'Available content restored. A saved copy remains in your stash. Review attachments and references before sending.';

  @override
  String get promptAttachmentsUnavailable =>
      'Some attachments cannot be restored';

  @override
  String get promptRestore => 'Restore';

  @override
  String get promptHistorySaveFailed =>
      'Prompt sent, but its history could not be saved on this device.';

  @override
  String promptAttachmentsUnavailableDetail(String names) {
    return 'Missing, damaged or temporary attachments: $names. Restore the available content and reattach these files before sending. The saved copy will stay in your stash.';
  }

  @override
  String get promptStashMigrationPending =>
      'Some saved attachments could not be moved to local attachment storage yet. Your saved content has been kept. Free device storage and retry.';

  @override
  String get commonRetry => 'Retry';

  @override
  String get shareWaitingForServer =>
      'Connect to a server and the shared text opens in a new session.';

  @override
  String get shareSessionFailed =>
      'Shared text kept. Could not open a session. Retry when the connection is ready.';

  @override
  String get webSourcesDisclosure =>
      'Web search is not available through this connection’s app gateway. Paste a public URL and optionally an excerpt you want to include. No page is fetched. Nothing is sent to the model here.';

  @override
  String get webSourcesScopeChanged =>
      'Connection changed. Close and reopen Add web source.';

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
  String get webSourcesAdd => 'Add to review';

  @override
  String webSourcesReviewCount(int count) {
    return 'Review sources ($count/10)';
  }

  @override
  String get webSourcesReviewHint =>
      'Only checked sources will be returned to your draft.';

  @override
  String get webSourcesEmpty => 'No sources added yet.';

  @override
  String get webSourcesOpen => 'Open in browser';

  @override
  String webSourcesUseCount(int count) {
    return 'Use selected sources ($count)';
  }

  @override
  String get digestTitle => 'Completion digests';

  @override
  String get digestSubtitle => 'On demand · cached metadata, not AI summaries';

  @override
  String get digestEmpty =>
      'No ended-run metadata available in this location. Idle alone does not establish successful completion.';

  @override
  String get digestIdle => 'Server idle recorded · outcome unverified';

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
      other: '$count changed files in the session total; this run is unknown.',
      one: '1 changed file in the session total; this run is unknown.',
      zero: 'No changed files in the session total; this run is unknown.',
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
      'The connection or project changed. Close this view and reopen Run results from the intended project.';

  @override
  String get runResultsTitle => 'Run results';

  @override
  String get runResultsEmpty =>
      'The latest turn has no assistant step yet, so there is nothing to show.';

  @override
  String runResultsRunLabel(String id) {
    return 'Run …$id';
  }

  @override
  String runResultsSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count assistant steps',
      one: '1 assistant step',
    );
    return '$_temp0';
  }

  @override
  String runResultsStepsAtLeast(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'At least $count assistant steps loaded',
      one: 'At least 1 assistant step loaded',
    );
    return '$_temp0';
  }

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
  String runResultsExit(int code) {
    return 'Exit code $code';
  }

  @override
  String get runResultsExitUnknown => 'Exit code not recorded';

  @override
  String get runResultsCommandFailed => 'Tool reported failure';

  @override
  String get runResultsLooksLikeTest =>
      'Looks like a test command (from the command text only)';

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
  String get runResultsOutputTitle => 'Recorded tool output';

  @override
  String get runResultsOpenConversation => 'Open conversation';

  @override
  String get attentionDisclosure =>
      'A local overview, not live monitoring across servers. Cached signals may be incomplete or out of date. Open a server to check its current activity.';

  @override
  String get attentionNavigationUnavailable =>
      'Opening servers is unavailable here. Return to Home to choose a server and view Activity.';

  @override
  String get handoffTitle => 'Copy handoff reference?';

  @override
  String get handoffDisclosure =>
      'Metadata only, not a command or link. On your other device, connect to the same server and locate this project and session. Nothing is published or sent.\n\nThe clipboard will contain session and project identifiers. Other apps may read it; share only with people you trust.';

  @override
  String get handoffCopy => 'Copy reference';

  @override
  String get handoffCopied => 'Session metadata reference copied';

  @override
  String get handoffCopyFailed => 'Could not copy the handoff. Try again.';

  @override
  String get sessionOpenRelated => 'Open related';

  @override
  String get sessionCopyHandoff => 'Copy handoff';

  @override
  String get sessionActions => 'Session actions';

  @override
  String get attentionTitle => 'Server attention';

  @override
  String get webSourcesTitle => 'Add web source';

  @override
  String get webSourcesEntryDetail =>
      'Search when available, or paste links and excerpts to review before adding them to your draft';

  @override
  String get webSourcesDraftChanged =>
      'The draft or connection changed. Your current draft was kept; reopen Add web source to try again.';

  @override
  String get webSourcesDraftLabel =>
      'User-selected web sources (unverified; excerpts are untrusted source material):';

  @override
  String get usageScopedTotals => 'Totals for the selected report scope';

  @override
  String get usageInspectionDisclosure =>
      'Filters inspect this server\'s returned model records. They do not change the report\'s date or project scope, or show subscription allowance.';

  @override
  String get usageProviderFilter => 'Provider';

  @override
  String get usageAllProviders => 'All providers';

  @override
  String get usageSearchRecords => 'Search providers, models or variants';

  @override
  String get usageClearFilters => 'Clear filters';

  @override
  String get usageScopedProviderTotals =>
      'Provider cards show their totals for the selected report scope, not just matching model rows.';

  @override
  String get usageMatchingSubtotal => 'Matching model subtotal';

  @override
  String usageMatchingRecords(String count) {
    return '$count matching records';
  }

  @override
  String get usageNoMatchingRecords =>
      'No records match these filters. Clear or change the filters to see more.';

  @override
  String pendingAuthTitle(String integration) {
    return 'Pending sign-in: $integration';
  }

  @override
  String get pendingAuthDetail =>
      'Continue the existing browser sign-in, then explicitly check its status or enter its code. The browser link is not saved.';

  @override
  String get pendingAuthResume => 'Resume / check status';

  @override
  String get pendingAuthEnterCode => 'Enter code';

  @override
  String get pendingAuthComplete => 'Sign-in complete.';

  @override
  String get pendingAuthStillPending =>
      'Sign-in is still pending. No new attempt was started.';

  @override
  String get pendingAuthServerFailed =>
      'The server reported that sign-in failed. Provider error details are hidden.';

  @override
  String get pendingAuthExpired =>
      'This attempt is expired or outside the device’s recovery window. Cancellation is a separate server action.';

  @override
  String get pendingAuthFailed =>
      'Could not confirm the action. Check pending sign-ins before trying again. No new sign-in was started.';

  @override
  String get pendingAuthSaveUncertain =>
      'Recovery could not be saved reliably. Keep this app open and retry saving; restarting may lose this attempt. If no browser page opened, cancel the attempt before starting again.';

  @override
  String get pendingAuthRetrySave => 'Retry saving recovery';

  @override
  String get pendingAuthForget => 'Forget on this device';

  @override
  String get pendingAuthForgetDetail =>
      'Remove only this device’s recovery record? This does not cancel a server command, revoke credentials, or finish authorization. The server attempt may keep running until it expires.';

  @override
  String get pendingAuthUnsupported =>
      'This connection cannot recover earlier sign-ins. Legacy sign-ins work only while their original screen and connection remain available.';

  @override
  String get pendingAuthOtherSource =>
      'Other pending sign-ins belong to another server origin or location. Return to their original source to manage them.';

  @override
  String get connectionHelpTitle => 'Connection help';

  @override
  String get connectionHelpEntrySubtitle =>
      'Explain an address locally, without connecting';

  @override
  String get connectionHelpGuideTip =>
      'Keep the server off the public internet. Use private HTTPS or an encrypted tunnel ending on the device running this app. Localhost on your computer is not localhost on your phone. Open Connection help above for steps and examples.';

  @override
  String get connectionHelpPrivacy =>
      'This checks address rules only, not connectivity. Nothing is sent or saved. Input is hidden and cleared after checking. Paste only an address, not a password or pairing code.';

  @override
  String get connectionHelpAddress => 'Server address';

  @override
  String get connectionHelpCheck => 'Explain address';

  @override
  String get connectionHelpEmpty => 'Enter a server address to explain.';

  @override
  String get connectionHelpMalformed =>
      'This address could not be understood. Use a complete origin such as https://server.example, with no path, credentials or query.';

  @override
  String get connectionHelpCredentials =>
      'Credentials do not belong in a URL. Remove them and enter the server username and password separately in Servers. The pasted value has been cleared.';

  @override
  String get connectionHelpQuery =>
      'Remove query parameters and fragments. They can contain secrets; enter only the server origin. The pasted value has been cleared.';

  @override
  String get connectionHelpPath =>
      'Remove the path. This app needs the server origin, not a page or API route.';

  @override
  String get connectionHelpScheme =>
      'Use HTTPS for a remote server, or HTTP only for this device\'s supported loopback addresses.';

  @override
  String get connectionHelpRemoteHttp =>
      'Remote HTTP is blocked, including LAN and 100.64.0.0/10 addresses. A VPN does not change this rule. Set up private HTTPS or an encrypted tunnel ending on this device.';

  @override
  String get connectionHelpHttps =>
      'This address passes the HTTPS address rules. That does not verify its certificate, reachability, sign-in or privacy. A bare remote address is interpreted as HTTPS.';

  @override
  String get connectionHelpLoopback =>
      'This address passes the loopback address rules. Localhost means this device, not another computer. A server or tunnel must be listening here; this check does not verify that.';

  @override
  String get connectionHelpPrivateTitle => 'Private HTTPS or reverse proxy';

  @override
  String get connectionHelpPrivateSteps =>
      '1. Keep the server on its host\'s loopback with authentication enabled.\n2. Connect both devices to your private network and restrict access to intended users.\n3. Configure private HTTPS, such as Tailscale Serve, or a reverse proxy with a trusted certificate forwarding to the server. Support streaming and WebSockets.\n4. Add the HTTPS origin in Servers with sign-in in separate fields.\nTailscale Funnel exposes the service publicly; it is not a private-network fix. This app cannot infer VPN presence. The example below is a placeholder.';

  @override
  String get connectionHelpTunnelTitle => 'Localhost on the wrong device?';

  @override
  String get connectionHelpTunnelSteps =>
      'Localhost, 127.0.0.1 and [::1] refer to the device running this app. For a server on another computer, use private HTTPS or an encrypted tunnel ending here. If an SSH client is available on this device, adapt the example below, verify the host key and keep it running. Replace user@host with your SSH destination. Running it on another computer does not forward this device\'s port. Keep server authentication enabled.';

  @override
  String get connectionHelpVerifyTitle => 'Verify connectivity separately';

  @override
  String get connectionHelpVerifySteps =>
      'On this device, check private-network membership, DNS, firewall access and certificate trust using your network tools. Check server and proxy configuration on the host, then use Servers to connect. Never disable TLS verification or share passwords, pairing codes or unredacted logs. Access to this server is shell access.';

  @override
  String get connectionHelpCopyExample => 'Copy example';

  @override
  String get connectionHelpCopied => 'Example copied';

  @override
  String get connectionHelpCopyFailed =>
      'Could not copy the example. Select the example text to copy it manually.';

  @override
  String get voiceConversationTitle => 'Voice conversation';

  @override
  String get voiceConversationDescription =>
      'Listen, review, then Send. No automatic listening; replies are read aloud only if you turn that on.';

  @override
  String get voiceConversationSpeakReplies => 'Speak replies';

  @override
  String get voiceConversationSpeakRepliesDetail =>
      'Read a matched reply once after Send. Tap Listen to use the microphone.';

  @override
  String get voiceConversationWaitingReply => 'Waiting for the reply…';

  @override
  String get voiceConversationSpeakingReply => 'Speaking the reply';

  @override
  String get voiceConversationStopReply => 'Stop';

  @override
  String get voiceConversationReadReply => 'Read reply';

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
  String get voiceConversationPausedTitle => 'Voice conversation paused';

  @override
  String get voiceConversationPausedDetail =>
      'Voice conversation is paused. Reconnect, wait for the reply, or review pending decisions on screen.';

  @override
  String get voiceConversationDraftFirst =>
      'Send, save, or clear your current draft before starting voice conversation.';

  @override
  String get voiceConversationListen => 'Listen';

  @override
  String get voiceConversationExit => 'Exit voice mode';

  @override
  String get voiceConversationCommandsOnly =>
      'Use the typed composer for slash commands.';

  @override
  String get voiceConversationInterrupted =>
      'Voice conversation was interrupted. Review before sending again.';

  @override
  String get voiceReviewExplicitAction =>
      'Edit before inserting. Sending always requires an explicit action.';

  @override
  String get voiceInputInterrupted =>
      'Voice input was interrupted. Close and start again when ready.';

  @override
  String get voiceInputClose => 'Close voice input';

  @override
  String get voiceInputUnavailable =>
      'Voice input is unavailable. Check the local model and microphone settings.';

  @override
  String get voiceConversationInstructions =>
      'Review and insert your transcript, then tap Send in the composer. Replies are read aloud only while Speak replies is on, and only the reply to what you just sent. Unsent text is discarded when you leave voice mode, the chat, or the app.';

  @override
  String get desktopDropFailedTitle => 'Could not attach dropped files';

  @override
  String get desktopDropFailedRecovery =>
      'Check the attachments already added before trying again. You can also use the keyboard to open Add, then Attach file.';

  @override
  String get desktopContextMenuShortcutKeys =>
      'Right click / Shift + F10 / Menu';

  @override
  String get commandAuthManage => 'Server sign-in';

  @override
  String get commandAuthMethodHint =>
      'Runs the provider\'s sign-in method on your selected server, not on this phone. You may need to finish interactive steps on the server.';

  @override
  String get commandAuthConfirmTitle => 'Start sign-in on the server?';

  @override
  String get commandAuthConfirmDetail =>
      'OpenCode will execute this provider\'s declared sign-in method on the selected server. Continue only if you trust that server and provider. The app does not run or copy a shell command on your phone.';

  @override
  String get commandAuthStart => 'Start server sign-in';

  @override
  String get commandAuthPending =>
      'Sign-in is pending on the server. Finish any server-side interaction, then check its status. Closing this sheet does not cancel it.';

  @override
  String get commandAuthCheck => 'Check status';

  @override
  String get commandAuthCancel => 'Cancel sign-in';

  @override
  String get commandAuthFailed =>
      'Could not complete or confirm server sign-in. Check the existing attempt before starting another.';

  @override
  String get commandAuthComplete =>
      'The server reported that sign-in completed. Refresh Providers to see its current connections.';

  @override
  String get commandAuthExpired =>
      'This sign-in attempt expired. You can start a new attempt.';

  @override
  String get commandAuthScopeChanged =>
      'The server or project changed. Return to the original location and reopen sign-in to manage its attempt.';

  @override
  String get commandAuthUncertainStart =>
      'The server may have started sign-in, but the app could not safely recover its attempt. Check on the server before retrying; automatic restart is blocked to avoid duplicate processes.';

  @override
  String get readAloudAction => 'Read reply prose';

  @override
  String get readAloudStop => 'Stop reading aloud';

  @override
  String get readAloudOtherVoice => 'Read with another voice';

  @override
  String get readAloudChooseVoice => 'Choose a reading voice';

  @override
  String get readAloudConsentTitle => 'Use the system speech engine?';

  @override
  String get readAloudConsentDetail =>
      'The loaded reply prose will be sent to your system speech engine. Only voices marked offline are offered, but the engine is separate software and its privacy practices apply. Code blocks and tool details are omitted. Others may hear the audio. Playback stops when this chat is covered or the app goes into the background.';

  @override
  String get readAloudContinue => 'Choose voice';

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
  String get credentialManage => 'Manage accounts';

  @override
  String get credentialMetadataOnly =>
      'Only saved account labels are shown. API keys and login tokens stay on your server.';

  @override
  String get credentialActiveUnknown =>
      'Active account unknown. The saved-account list does not report which account is active.';

  @override
  String get credentialNoneActive =>
      'The server reported no active saved account.';

  @override
  String get credentialActiveObserved =>
      'The Active badge reflects the latest server event.';

  @override
  String get credentialActiveUpdated =>
      'Active account updated from the server.';

  @override
  String get credentialSwitchRequested =>
      'Switch requested. This request has not yet been confirmed by a server event.';

  @override
  String get credentialActive => 'Active';

  @override
  String get credentialSetActive => 'Set active';

  @override
  String get credentialRename => 'Rename account';

  @override
  String get credentialLabel => 'Account label';

  @override
  String get credentialSave => 'Save label';

  @override
  String credentialRemoveTitle(String label) {
    return 'Remove $label?';
  }

  @override
  String get credentialRemoveDetail =>
      'Remove this saved sign-in from the server. Other projects using it may be affected. This does not edit environment configuration; the server determines which account, if any, becomes active afterward.';

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
  String get mcpRemove => 'Remove';

  @override
  String mcpRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get mcpRemoveRuntimeDetail =>
      'Remove this MCP server from the current runtime location. Its tools will no longer be available there. This does not erase persistent server configuration; it may return after a server restart.';

  @override
  String get mcpRemoveFailed =>
      'Could not confirm MCP removal. Refresh the list before trying again; the server may already have applied the change.';

  @override
  String get mcpLoadFailed => 'Could not refresh MCP data. Try again.';

  @override
  String get mcpSavedStatus => 'Saved in OpenCode';

  @override
  String get mcpConnectionUnconfirmed => 'App connection not confirmed';

  @override
  String get mcpRetryReconnect => 'Retry reconnect';

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
  String get promptStashEmpty =>
      'Nothing saved yet. Use Stash current prompt in Prompt tools to keep a prompt for later.';

  @override
  String get promptStashContextOnly => 'Attachments and references';

  @override
  String get promptRestoredReferences =>
      'Prompt restored. Saved references are snapshots; their server files may have changed.';

  @override
  String get promptDefaultLocation => 'the server default directory';

  @override
  String get promptStashed => 'Prompt saved to your stash.';

  @override
  String get promptStashedDraftPending =>
      'Prompt saved to your stash. The composer draft still needs to be saved; use Retry in the draft warning.';

  @override
  String get promptStashReadFailed =>
      'Could not read saved prompts. Their stored data has been kept.';

  @override
  String get promptStashDeleteDetail =>
      'This removes the saved text, attachments and references from this device.';

  @override
  String get promptStashDescription =>
      'Save text, attachments and references for later';

  @override
  String get promptRestoreAvailable => 'Restore available content';

  @override
  String get promptRestored => 'Prompt restored. Review it before sending.';

  @override
  String get promptStashAction => 'Stash current prompt';

  @override
  String promptStashLocation(String directory) {
    return 'This prompt refers to files in $directory. Switch to its original project and workspace before restoring it.';
  }

  @override
  String get promptStashScopeChanged =>
      'The server or location changed. Close and reopen Saved prompts.';

  @override
  String get transcriptFindTitle => 'Find in conversation';

  @override
  String get transcriptFindHint => 'Search conversation';

  @override
  String get transcriptFindScope => 'Messages, reasoning and tool data';

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
  String transcriptFindTotal(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count matches in message text',
      one: '1 match in message text',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'Loaded messages only. Load older messages to search further.';

  @override
  String get transcriptFindComplete =>
      'All available message content searched.';

  @override
  String get transcriptFindReasoning => 'Reasoning';

  @override
  String get transcriptFindTool => 'Tool data';

  @override
  String get transcriptFindFile => 'File name';

  @override
  String get transcriptFindAll => 'Search all history';

  @override
  String get skillMenu => 'Use a skill';

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
      'The connection or project changed. Reopen Skills from the conversation.';

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
  String get activeContextHelp =>
      'Active messages returned by the server after its latest compaction. Message counts are not token counts.';

  @override
  String get activeContextRefresh => 'Refresh active context';

  @override
  String get activeContextSearch => 'Search active messages';

  @override
  String get activeContextAll => 'All';

  @override
  String activeContextCount(int shown, int total) {
    return '$shown of $total messages';
  }

  @override
  String get activeContextEmpty =>
      'The server returned no active context messages.';

  @override
  String get activeContextNoMatches =>
      'No active messages match these filters.';

  @override
  String get activeContextNoText => 'No supported text content in this entry.';

  @override
  String get activeContextUnsupported =>
      'Active context inspection is unavailable on this server.';

  @override
  String get activeContextChanged =>
      'The connection, project or conversation changed. Reopen this inspector from the conversation.';

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
  String get activeContextChange => 'Session change';

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
  String get draftRetrySave => 'Retry saving draft';

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
  String get draftLeaveTitle => 'Draft could not be saved';

  @override
  String get draftLeaveMessage =>
      'Keep editing to copy your text or retry saving. Leaving now may lose your unsaved changes.';

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
      'A photo is waiting in its original conversation. Keep it there, or discard it before choosing another photo.';

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
  String get legacyDraftsTitle => 'Older drafts';

  @override
  String get legacyDraftsDescription =>
      'Review drafts saved before server tracking';

  @override
  String get legacyDraftsExplanation =>
      'These drafts have no recorded server. Review their text before using it in this conversation.';

  @override
  String get legacyDraftInsertExplanation =>
      'Insert adds this text after your current draft. The original saved copy stays here until you delete it.';

  @override
  String get legacyDraftTextOnly =>
      'Only text can be inserted here. Any saved attachments remain with the older draft.';

  @override
  String get legacyDraftDelete => 'Delete saved copy';

  @override
  String get legacyDraftDeleteExplanation =>
      'Permanently remove this older draft and its saved attachments from this device?';

  @override
  String get legacyDraftDeleteFailed =>
      'The draft changed or could not be removed. Reopen it and retry.';

  @override
  String get legacyDraftInsert => 'Insert into draft';

  @override
  String get legacyDraftSearch => 'Search older drafts';

  @override
  String get legacyDraftsEmpty => 'No older drafts found';

  @override
  String get legacyDraftLocationChanged =>
      'The project changed. Reopen Older drafts to choose where to insert the text.';

  @override
  String get quotaTitle => 'Remaining usage';

  @override
  String get quotaSettingsSummary =>
      'Optional Codex collector · setup required';

  @override
  String get quotaDescription =>
      'Choose a provider to view its reported account windows. These are separate from OpenCode token usage and cost.';

  @override
  String get quotaSource => 'Collector server';

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
  String get quotaSetupTitle => 'An optional collector is required';

  @override
  String get quotaSetupDescription =>
      'Your server operator must install and protect this route at the same origin as OpenCode. Reading it uses this profile\'s server sign-in. Confirm only if you installed or trust that deployment. Provider tokens stay on the server.';

  @override
  String get quotaSetupGuide =>
      'Setup instructions are in tool/quota/README.md in the app repository. This screen does not install services or remember permission after you leave.';

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
  String get quotaForgetConsent => 'Stop using this collector';

  @override
  String get quotaCollectorAuth =>
      'The collector route did not accept this server sign-in. Ask the server operator to check its authentication setup.';

  @override
  String get quotaCollectorMissing =>
      'The optional collector route is not available on this server. Check its installation and proxy routing.';

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
  String get quotaCodexAccount => 'Codex account windows';

  @override
  String quotaPlan(String plan) {
    return 'Reported plan: $plan';
  }

  @override
  String quotaChecked(String time) {
    return 'Snapshot checked $time';
  }

  @override
  String get quotaStale =>
      'Previous snapshot — refresh to check the latest allowance.';

  @override
  String get quotaUseBlocked =>
      'The provider reports that ordinary Codex use is currently blocked. Window percentages alone do not determine access.';

  @override
  String get quotaNotReported => 'Not reported';

  @override
  String get quotaPrimaryWindow => 'Primary window';

  @override
  String get quotaSecondaryWindow => 'Secondary window';

  @override
  String quotaOtherWindow(int number) {
    return 'Usage window $number';
  }

  @override
  String quotaRemaining(String percent) {
    return '$percent remaining';
  }

  @override
  String quotaWindowRemainingLabel(String window) {
    return '$window: remaining percentage';
  }

  @override
  String quotaUsed(String percent) {
    return '$percent used';
  }

  @override
  String quotaResetAt(String time) {
    return 'Reported reset: $time';
  }

  @override
  String get quotaResetUnknown => 'Reset time not reported';

  @override
  String get quotaResetPassed =>
      'Reset time passed — refresh to check. The displayed allowance has not been replenished locally.';

  @override
  String quotaDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count-day window',
      one: '1-day window',
    );
    return '$_temp0';
  }

  @override
  String quotaHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count-hour window',
      one: '1-hour window',
    );
    return '$_temp0';
  }

  @override
  String quotaSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count-second window',
      one: '1-second window',
    );
    return '$_temp0';
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
  String get iosAppTitle => 'OpenCode for iOS';

  @override
  String get iosRemoteSummary =>
      'A remote client for the OpenCode server you choose. On-device server hosting and background monitoring are not available in this iOS build.';

  @override
  String get iosKeychainGuide =>
      'Server passwords use this device\'s Keychain. They are not stored in plain profile preferences.';

  @override
  String get platformSecureStorageGuide =>
      'Server passwords use this platform\'s secure credential storage. They are not stored in plain profile preferences.';

  @override
  String get quotaClaudeAccount => 'Claude login windows';

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
  String get setupOutputWaitingDetail =>
      'Setup messages will appear here when Termux responds.';

  @override
  String get setupStartInstalled => 'Start installed OpenCode';

  @override
  String get setupMissingCredential =>
      'This app has no saved credential for that installation. Connect with its server address, or run setup to configure it.';

  @override
  String get setupUbuntuOption => 'Managed Ubuntu installation';

  @override
  String get setupOwnOption => 'Use your own setup';

  @override
  String get setupOwnDescription =>
      'Connect an existing OpenCode 1 or OpenCode 2 server by address. A native musl installation needs a compatible Linux environment and is not managed by this app.';

  @override
  String get setupConnectExisting => 'Connect existing server';

  @override
  String get setupScreenTitle => 'On-device setup';

  @override
  String get setupInstallStart => 'Install & start';

  @override
  String get setupCheckAgain => 'Check again';

  @override
  String uncertainAuthTitle(String integrationID) {
    return 'Unconfirmed sign-in: $integrationID';
  }

  @override
  String get uncertainAuthDetail =>
      'The server may have started sign-in, but no attempt ID was received. Check on the server before starting again.';

  @override
  String get uncertainAuthForgetTitle => 'Forget uncertain start?';

  @override
  String get uncertainAuthForgetDetail =>
      'This clears only the local retry block. It does not cancel sign-in on the server. Check the server first to avoid running a second sign-in. No new sign-in will start.';

  @override
  String get uncertainAuthForget => 'Forget uncertain start';

  @override
  String get uncertainAuthCloseHint =>
      'Close this sheet and use the unconfirmed sign-in row to clear its local retry block after checking the server.';

  @override
  String get pluginsTitle => 'Plugins';

  @override
  String get pluginsDescription =>
      'Plugins reported for this server location. Inspect status and source here; manage plugins on the server.';

  @override
  String get pluginsUnsupported =>
      'This server does not support plugin inspection.';

  @override
  String get pluginsDisconnected =>
      'Connect to a server to inspect its plugins.';

  @override
  String get pluginsEmpty => 'No plugins reported for this location.';

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
  String get pluginsStatusFailed => 'Failed';

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
  String get handoffCommandTitle => 'Continue on computer';

  @override
  String get handoffCommandDisclosure =>
      'Run this command in a POSIX shell on a computer with OpenCode installed and access to this server. Set OPENCODE_SERVER_PASSWORD privately on that computer if the server requires it. The clipboard will contain the server address, username, project directory and session ID, but no password.';

  @override
  String get handoffCopyCommand => 'Copy command';

  @override
  String get handoffCommandCopied => 'Resume command copied';

  @override
  String get handoffCommandUnavailable =>
      'A resume command is unavailable for this connection or workspace. Continuing on another computer needs a supported OpenCode command and a reachable HTTPS server; a localhost address points to each device itself. You can still copy the session metadata below.';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get quotaMiniMaxAccount => 'MiniMax subscription windows';

  @override
  String get quotaMiniMaxSourceBound =>
      'Tied to the collector\'s configured MiniMax Subscription Key. The quota response does not independently identify the account. Only reported general-pool percentages are shown; other limits may apply.';

  @override
  String get managedHealthTitle => 'On-device server';

  @override
  String get managedHealthUnchecked =>
      'Check the server managed by this app in Termux.';

  @override
  String get managedHealthCheck => 'Check status';

  @override
  String get managedHealthChecking => 'Checking Termux…';

  @override
  String get managedHealthFailed =>
      'Could not check Termux. Open setup to check permissions or try again.';

  @override
  String get managedHealthReady => 'Server process running';

  @override
  String get managedHealthWorking => 'Setup is in progress';

  @override
  String get managedHealthStopped => 'Server stopped';

  @override
  String get managedHealthNeedsSetup => 'Setup needs attention';

  @override
  String get managedHealthAbsent => 'No managed setup found';

  @override
  String get managedHealthUnknown => 'Server state unavailable';

  @override
  String get managedHealthManage => 'Open setup controls';

  @override
  String managedHealthObserved(String time) {
    return 'Last checked at $time. Check again for the current state.';
  }

  @override
  String managedHealthVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get managedHealthUbuntu => 'Runner: Ubuntu';

  @override
  String get managedHealthLifetime =>
      'Android may stop either app. Keeping the mobile connection alive does not guarantee the Termux server will keep running overnight.';

  @override
  String get quotaBudgetTitle => 'Personal alert threshold';

  @override
  String get quotaBudgetDescription =>
      'Choose a percentage used for this source, account and window. This does not change provider limits.';

  @override
  String get quotaBudgetOff => 'Off';

  @override
  String quotaBudgetPercent(String percent) {
    return '$percent% used';
  }

  @override
  String get quotaBudgetOptIn => 'Show threshold attention';

  @override
  String get quotaBudgetAttentionScope =>
      'Only after a fresh read on this page. No background polling or device notifications. A window without a reset time alerts once until you change this rule.';

  @override
  String get quotaBudgetSaveFailed =>
      'Could not save this budget change. Your last saved settings remain in effect.';

  @override
  String get quotaBudgetAttention =>
      'A personal threshold was reached in the latest provider reading. Review the reported windows below.';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get quotaGlmAccount => 'Configured GLM Coding Plan source';

  @override
  String get quotaGlmTokenWindow => 'Reported token-plan window';

  @override
  String get quotaGlmMcpWindow => 'Reported MCP window';

  @override
  String get usageBudgetTitle => 'Personal consumption budgets';

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
  String get usageBudgetInvalid =>
      'Enter a positive finite amount. Token budgets must use whole numbers.';

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
  String get usageBudgetClearAll => 'Clear saved consumption budgets';

  @override
  String get usageBudgetClearDescription =>
      'Remove all current and past consumption budgets for this saved server? Provider thresholds are kept.';

  @override
  String get monitorTitle => 'Saved-server attention';

  @override
  String get monitorScope =>
      'Counts cover each server’s last selected location, not every project on that server.';

  @override
  String get monitorDisclosure =>
      'Monitoring is off until you enable it for a server. Checks run about once a minute while this app is open. Background checks run no more often than every five minutes, only while Keep live is already on and Android’s service is running. Android can stop that service; no remaining runtime is promised.';

  @override
  String get monitorConfigure => 'Monitoring settings';

  @override
  String get monitorRefresh => 'Check monitored servers';

  @override
  String get monitorOptIn => 'Monitor this server';

  @override
  String get monitorOptInDetail =>
      'Check pending permissions, questions and forms in its last selected location.';

  @override
  String get monitorNotifications => 'Notify when attention is needed';

  @override
  String get monitorWifi => 'Wi-Fi only';

  @override
  String get monitorWifiDetail =>
      'Checks pause unless Android reports an active Wi-Fi network. VPN or unavailable network information may pause checks.';

  @override
  String get monitorWifiUnsupported =>
      'Wi-Fi detection is unavailable on this platform.';

  @override
  String get monitorQuiet => 'Quiet hours';

  @override
  String get monitorQuietDetail =>
      'Mute attention alerts during these local times. Checks continue.';

  @override
  String get monitorQuietStart => 'Quiet hours start';

  @override
  String get monitorQuietEnd => 'Quiet hours end';

  @override
  String get monitorDisabled => 'Not monitored · attention unknown';

  @override
  String get monitorWaiting => 'Waiting for a check · attention unknown';

  @override
  String get monitorChecking => 'Checking · attention unknown';

  @override
  String get monitorUnavailable => 'Could not check · attention unknown';

  @override
  String get monitorWifiRequired => 'Waiting for Wi-Fi · attention unknown';

  @override
  String get monitorPaused => 'Paused in background · attention unknown';

  @override
  String get monitorCurrent => 'Current observation';

  @override
  String get monitorAllClear => 'No pending requests in the checked location';

  @override
  String get monitorNoServers => 'Add a server to monitor attention.';

  @override
  String get monitorSaveFailed =>
      'Could not save monitoring settings. Try again.';

  @override
  String get monitorOpenFailed =>
      'This request or its server location changed. Refresh the inbox and try again.';

  @override
  String get monitorSwitchTitle => 'Switch server to review?';

  @override
  String get monitorSwitchDetail =>
      'A run is active on the selected server. Switching changes the connection shown in this app; it does not stop that server’s run.';

  @override
  String get monitorSwitch => 'Switch server';

  @override
  String get monitorSession => 'Session';

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
  String get monitorNextCheck => 'Next check';

  @override
  String get monitorPending => 'Current pending requests';

  @override
  String get monitorUnknownServers => 'Servers with unknown attention';

  @override
  String monitorPendingSummary(int pendingCount, int unknownCount) {
    return 'Current pending requests: $pendingCount\nServers with unknown attention: $unknownCount';
  }

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
  String monitorLabeledTime(String label, String time) {
    return '$label: $time';
  }

  @override
  String get monitorSelected => 'Selected location';

  @override
  String get monitorNoNotifications =>
      'Background notifications also require Keep live and notification permission in Background settings.';

  @override
  String get monitorCheckIn => 'Check in on long runs';

  @override
  String get monitorCheckInDetail =>
      'Shows when busy checks span the chosen time. Work may pause or restart between checks. At most one notification is attempted per observed interval, while Keep live is on.';

  @override
  String get monitorCheckInDetailForeground =>
      'Shows a check-in row when busy checks span the chosen time. Work may pause or restart between checks. This device cannot deliver reminders in the background.';

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
  String monitorObservedBusy(int minutes, String since) {
    return 'Busy at checks spanning $minutes min · first check $since';
  }

  @override
  String get quotaBudgetClearAll => 'Clear saved provider thresholds';

  @override
  String get quotaBudgetClearDescription =>
      'Remove all provider thresholds and attention settings for this saved server, including previous accounts? Consumption budgets are kept.';

  @override
  String managedStorageSummary(String available, String total) {
    return 'Termux storage: $available GiB free of $total GiB';
  }

  @override
  String get managedStorageFailed =>
      'Termux storage could not be checked. Retry Check status.';

  @override
  String get managedRecoveryTitle => 'Recover a crashed managed server';

  @override
  String get managedRecoveryPolicy =>
      'Opt in to at most 3 restart attempts, with delays of at least 5, 15 and 45 seconds. Only while this app is in the foreground. No install or update.';

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
      'Could not save or revoke recovery. Keep this profile and retry before removing it.';

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
      'Recovery paused because Termux did not confirm the result. Check status to continue.';

  @override
  String get managedRecoveryRetryDisable => 'Retry disabling recovery';

  @override
  String get managedRecoveryStoppedWithCleanupError =>
      'The local server is stopped. Recovery settings could not be fully cleared; retry disabling recovery in Servers before removing the profile.';

  @override
  String get pluginMappingPersonal =>
      'Your command links · not verified plugin ownership';

  @override
  String pluginMappingReview(String command) {
    return 'Review /$command';
  }

  @override
  String get pluginMappingManage => 'Link commands';

  @override
  String get pluginMappingDescription =>
      'Choose commands you associate with this plugin. These personal links apply only to this server location. Each action opens a review of the chat and arguments before you run it.';

  @override
  String get pluginMappingEmpty => 'No server commands are available to link.';

  @override
  String get pluginMappingUnavailable =>
      'This plugin or command is no longer available here. Refresh and review your links.';

  @override
  String get pluginMappingLimit => 'Choose up to 16 commands for this plugin.';

  @override
  String get pluginMappingSave => 'Save links';

  @override
  String get pluginMappingSaveFailed =>
      'Links could not be saved. Check that this server location is still selected and try again.';

  @override
  String get pluginMappingLoadFailed =>
      'Commands could not be loaded. Try again when connected.';

  @override
  String get mobileTasksDescription => 'Server-reported tasks · mobile view';

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
  String get mobileTasksCopied => 'All tasks copied';

  @override
  String get mobileTasksCopyFailed => 'Could not copy the task list.';

  @override
  String get mobileTaskPriorityHigh => 'High priority';

  @override
  String get mobileTaskPriorityMedium => 'Medium priority';

  @override
  String get mobileTaskPriorityLow => 'Low priority';

  @override
  String get pluginMappingClearAll => 'Clear personal links';

  @override
  String get pluginMappingClearTitle => 'Clear all personal command links?';

  @override
  String get pluginMappingClearDescription =>
      'Remove personal plugin-command links for every location in this server profile, including previous locations. Server plugins and commands stay installed.';

  @override
  String get pluginMappingClearConfirm => 'Clear links';

  @override
  String get pluginMappingClearFailed =>
      'Personal links could not be cleared. Check that this server profile is still selected and try again.';

  @override
  String get quotaMonitorTitle => 'Quota monitoring';

  @override
  String get quotaMonitorConsentTitle => 'Monitor this provider source?';

  @override
  String get quotaMonitorConsent =>
      'Allow this app to keep reading the trusted collector for this exact provider account after you leave this page, including after app restart. A cycle checks at most three saved sources, every five minutes in the foreground or fifteen minutes while your existing background service is active. With more than three sources, each source may wait several cycles. Device alerts require the separate switch below and a freshly reported window at or above the selected percentage used. An alert records that past reading; open it to check current usage. Personal page thresholds are separate. No service is started here.';

  @override
  String get quotaMonitorRuntime =>
      'Sources are checked in rotation, at most three per cycle; larger lists take several cycles. Background reads require the existing live service to be active; Android may stop it. Displayed readings expire when the collector says they do. Device alerts record past threshold readings, not current remaining allowance. This page never switches your active server.';

  @override
  String get quotaMonitorEmpty =>
      'No provider sources are monitored. Read Remaining for a trusted collector, then enable monitoring for that source.';

  @override
  String get quotaMonitorEnable => 'Enable quota monitoring';

  @override
  String get quotaMonitorNotifications =>
      'Device alerts for reported quota thresholds';

  @override
  String get quotaMonitorWifi => 'Read only on confirmed Wi-Fi';

  @override
  String get quotaMonitorQuiet => 'Quiet hours: 22:00–08:00 local time';

  @override
  String get quotaMonitorDisabled => 'Monitoring is off.';

  @override
  String get quotaMonitorWaiting => 'Waiting for a fresh reading.';

  @override
  String get quotaMonitorChecking => 'Checking the trusted collector…';

  @override
  String get quotaMonitorCurrent =>
      'Fresh reading from the consented provider source.';

  @override
  String get quotaMonitorPaused =>
      'Monitoring is paused. Open the app or check the existing background service.';

  @override
  String get quotaMonitorWifiRequired =>
      'Waiting for confirmed Wi-Fi. Unknown network status does not permit a read.';

  @override
  String get quotaMonitorSourceChanged =>
      'This provider account or source changed, or could not be verified. Open Remaining, read it again and review new consent.';

  @override
  String get quotaMonitorSaveFailed =>
      'Could not save quota monitoring. A failed disable stays paused in this app; retry before closing the app.';

  @override
  String get quotaMonitorDisable => 'Disable quota monitoring';

  @override
  String get setupChooseServerTitle => 'Choose your server setup';

  @override
  String get setupChooseServerDescription =>
      'Connect an existing server, or use Termux to run OpenCode on this phone.';

  @override
  String get setupUncheckedTitle => 'Continue without an installation check?';

  @override
  String get setupUncheckedDescription =>
      'The current installation could not be checked. Continuing may install or update OpenCode 1 in the app-managed Ubuntu environment. Existing Ubuntu files are kept. You can check again or connect by address instead.';

  @override
  String get setupUncheckedContinue => 'Continue with Ubuntu';

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
      'The server did not authorize web search. Check this connection’s credentials.';

  @override
  String get webSearchInvalidResponse =>
      'The search response did not match this connection or the supported format. Refresh providers or paste a source.';

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
  String get setupReinstallStart => 'Reinstall & start';

  @override
  String setupInstallVersionStart(String version) {
    return 'Install $version & start';
  }

  @override
  String get setupReplaceTitle => 'Replace installed OpenCode?';

  @override
  String setupReplaceDescription(
    String installedVersion,
    String targetVersion,
  ) {
    return 'Replace OpenCode $installedVersion with $targetVersion in the managed Ubuntu environment and restart the local server. Existing Ubuntu files are kept.';
  }

  @override
  String get setupInstallRestart => 'Install & restart';

  @override
  String get queueStorageUnreadable =>
      'Saved queued prompts could not be read. New prompts cannot be queued until this device data is cleared.';

  @override
  String get queueStorageDiscardUnreadable =>
      'This permanently deletes the unreadable queued prompts and their attachments from this device. Their contents and count are unknown. Nothing on the server is affected.';

  @override
  String get filesViewerScopeChanged =>
      'Connection changed. Close and reopen this file.';

  @override
  String get filesViewerPathChanged =>
      'File context changed. Close and reopen this file.';

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
  String get updateConnectionToken => 'Update token';

  @override
  String get codexDraftReconnectNotice =>
      'Review draft stays here; nothing is sent automatically.';

  @override
  String get codexTextOnlyPrompt =>
      'This connection supports text only. Remove attachments before sending.';

  @override
  String get codexOfflineDraftSaved =>
      'Reconnect before sending. Your draft is kept on this device.';

  @override
  String get codexReconnectBeforeSending => 'Reconnect before sending.';

  @override
  String get connectionTypeLabel => 'CONNECTION TYPE';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get codexExperimentalLabel => 'Codex (experimental)';

  @override
  String get connectionDisplayName => 'Display name (optional)';

  @override
  String get connectionDisplayNameHint => 'Defaults to the server host';

  @override
  String get connectionServerAddress => 'Server address';

  @override
  String get codexAddressHint => 'wss://codex.example or ws://127.0.0.1:4500';

  @override
  String get codexAddressHelp =>
      'Use wss:// for remote servers. ws:// is limited to this device.';

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
  String get codexShowToken => 'Show connection token';

  @override
  String get codexHideToken => 'Hide connection token';

  @override
  String get codexPasteToken => 'Paste connection token';

  @override
  String get connectionCloseEditor => 'Close server editor';

  @override
  String get connectionCredentialUnavailable =>
      'A saved connection credential can no longer be read. Edit the active server and re-enter it before connecting.';

  @override
  String get projectContextTitle => 'Project context';

  @override
  String get projectConfiguredFolder => 'Configured folder';

  @override
  String get termuxGuideTitle => 'Connect Termux once';

  @override
  String get termuxGuideIntro =>
      'We copy the command for you. Here is what to do when Termux opens.';

  @override
  String get termuxGuideAutomaticCheck =>
      'When you return, we will check the connection automatically.';

  @override
  String get termuxGuideShowCommand => 'Show command';

  @override
  String get termuxGuideOpening => 'Opening Termux...';

  @override
  String get termuxGuideCopyTitle => '1. Copy & open';

  @override
  String get termuxGuideCopyDescription =>
      'Tap Copy & open Termux above. Allow Android\'s permission request if shown.';

  @override
  String get termuxGuidePasteTitle => '2. Press and hold, then Paste';

  @override
  String get termuxGuidePasteDescription =>
      'In Termux, press and hold near the blinking cursor. Tap Paste in the menu.';

  @override
  String get termuxGuideEnterTitle => '3. Enter, then return';

  @override
  String get termuxGuideEnterDescription =>
      'Press the keyboard Enter or return key. When Termux shows bridge-unlocked, switch back to this app.';

  @override
  String get termuxGuideCopied => 'Command copied';

  @override
  String get termuxGuidePaste => 'Paste';

  @override
  String get termuxGuideEnterKey => 'Enter';

  @override
  String get termuxGuideIllustrationNote =>
      'Illustrations only. Your keyboard and Paste menu may look different.';

  @override
  String get termuxGuideOpenFailed =>
      'The command was copied, but Termux could not open. Open Termux yourself or try Copy & open Termux again.';

  @override
  String get termuxGuideCopyOpenFailed =>
      'Could not copy the command or open Termux.';

  @override
  String get termuxPermissionDenied =>
      'Android denied the Termux command permission. Allow it in OpenCode app settings.';

  @override
  String get launchShortcutWaiting =>
      'Connecting to the saved server. The new task opens when it is ready.';

  @override
  String get launchShortcutNoServer =>
      'Choose a server, then start a new task.';

  @override
  String get launchShortcutReentry =>
      'Enter the credentials for the saved server, then start a new task.';

  @override
  String get launchShortcutConnectionFailed =>
      'Could not connect to the saved server. Choose or fix a server, then start a new task.';

  @override
  String launchShortcutNewTaskFailed(String error) {
    return 'Could not start a new task. $error';
  }

  @override
  String get queuedSending => 'Sending…';

  @override
  String get queuedDeliveryUnconfirmed =>
      'Delivery unconfirmed — review before resending';

  @override
  String queuedDeliveryUnconfirmedWithError(String error) {
    return 'Delivery unconfirmed: $error';
  }

  @override
  String get queuedResendTooltip => 'Send again';

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
      'Its earlier send was never confirmed; it may already be in the session.';

  @override
  String get setupRuntimeTitle => 'Which OpenCode would you like to use?';

  @override
  String get setupRuntimeOne => 'OpenCode 1';

  @override
  String get setupRuntimeOneDetail =>
      'Recommended for the widest feature support in this app.';

  @override
  String get setupRuntimeTwo => 'OpenCode 2 beta';

  @override
  String get setupRuntimeTwoDetail =>
      'Try the new server API. Some features are unavailable in this beta.';

  @override
  String setupRuntimeInstallDetail(String runtime, String version) {
    return 'Install $runtime ($version) in an app-managed Ubuntu environment. Existing Ubuntu files are reused.';
  }

  @override
  String setupRuntimeUpdateDetail(String runtime, String version) {
    return 'The app will install $runtime $version, restart only the managed local server, and reconnect this profile.';
  }

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
  String get isolatedTaskAction => 'Start a task in a fresh worktree';

  @override
  String get isolatedTaskTitle => 'New task in a fresh worktree';

  @override
  String isolatedTaskIntro(String project) {
    return 'OpenCode creates a new Git worktree and branch for $project and runs the project\'s setup. The worktree stays listed under Manage project until you remove it there.';
  }

  @override
  String get isolatedTaskNameLabel => 'Worktree name (optional)';

  @override
  String get isolatedTaskNameHelper =>
      'Leave empty to let OpenCode choose a name.';

  @override
  String get isolatedTaskStart => 'Create and start';

  @override
  String get isolatedTaskCreating => 'Creating the worktree…';

  @override
  String get isolatedTaskCreatingHint =>
      'Stopping now cannot undo a create the server may already be running.';

  @override
  String isolatedTaskPreparing(String name) {
    return '$name was created. OpenCode is preparing it…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name is ready. Opening a blank session…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name is ready.';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return '$name was created, but its setup status is not confirmed.';
  }

  @override
  String get isolatedTaskUnconfirmedHint =>
      'You can keep waiting or open it now. Setup may still be running.';

  @override
  String get isolatedTaskFailed => 'OpenCode could not prepare the worktree.';

  @override
  String get isolatedTaskCreateFailed => 'The worktree could not be created.';

  @override
  String isolatedTaskFailedKept(String name) {
    return '$name stays listed under Manage project. Nothing was deleted.';
  }

  @override
  String get isolatedTaskCancelled => 'Stopped waiting.';

  @override
  String isolatedTaskCancelledKept(String name) {
    return '$name was created and stays listed under Manage project.';
  }

  @override
  String get isolatedTaskCancelledUnknown =>
      'If OpenCode created the worktree, it appears under Manage project.';

  @override
  String isolatedTaskOpening(String name) {
    return 'Opening a blank session in $name…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return 'Session ready in $name. Nothing has been sent.';
  }

  @override
  String isolatedTaskBranch(String branch) {
    return 'Branch $branch';
  }

  @override
  String get isolatedTaskStopWaiting => 'Stop waiting';

  @override
  String get isolatedTaskKeepWaiting => 'Keep waiting';

  @override
  String get isolatedTaskOpenAnyway => 'Open anyway';

  @override
  String get isolatedTaskRetryOpen => 'Try again';

  @override
  String get isolatedTaskClose => 'Close';

  @override
  String get returnBriefTitle => 'Unreviewed work';

  @override
  String get returnBriefDescription =>
      'For this project on this device. Dismissing keeps conversations unread and requests pending.';

  @override
  String get returnBriefUntitled => 'Untitled session';

  @override
  String get returnBriefStale =>
      'Last observed state. Reconnect or refresh to check current work and requests.';

  @override
  String get returnBriefStatusUnknown => 'Review status unknown';

  @override
  String get returnBriefUnknown =>
      'This server does not report read state. Unreviewed results are unknown.';

  @override
  String get returnBriefPartial =>
      'Loaded sessions only. The session list is still incomplete.';

  @override
  String get returnBriefAnswer => 'Answer';

  @override
  String get returnBriefUnreviewed =>
      'Unreviewed session. Open results to check the outcome.';

  @override
  String get returnBriefReview => 'Review results';

  @override
  String get returnBriefContinue => 'Continue';

  @override
  String returnBriefMore(int count) {
    return 'Additional items: $count. They remain unacknowledged; see the sessions below or Activity.';
  }

  @override
  String get returnBriefSaveFailed =>
      'Dismissal was not saved. These items are still unreviewed. Try again.';

  @override
  String get returnBriefSaving => 'Saving dismissal...';

  @override
  String get returnBriefDismiss => 'Dismiss shown items';

  @override
  String get capsuleTitle => 'Context capsule';

  @override
  String get capsuleEntry =>
      'Collect notes, errors and screenshots for this task';

  @override
  String get capsuleDescription =>
      'Build a bundle for this task. Applying adds it to your existing draft; nothing is sent. Unapplied edits are kept only while this screen is open.';

  @override
  String get capsuleNote => 'Note';

  @override
  String get capsuleError => 'Error';

  @override
  String get capsuleCode => 'Code';

  @override
  String get capsuleLabel => 'Label';

  @override
  String get capsuleExcerpt => 'Excerpt';

  @override
  String get capsulePaste => 'Paste';

  @override
  String get capsuleRemove => 'Remove';

  @override
  String get capsuleAddImage => 'Add screenshot or image';

  @override
  String get capsulePreview => 'Tap to preview';

  @override
  String get capsuleApply => 'Apply to draft';

  @override
  String get capsuleApplied =>
      'Context added to your saved draft. Review it before sending.';

  @override
  String get capsuleScopeChanged =>
      'The task, connection or draft changed. Close this capsule and reopen it from the intended task.';

  @override
  String get capsuleTextOnly =>
      'This connection accepts text only. You can still collect notes, errors and code.';

  @override
  String get capsuleImagesOnly =>
      'Choose a PNG, JPEG, GIF or WebP image. Paste text into an excerpt instead.';

  @override
  String get capsuleImageFailed =>
      'Could not add that image. Use up to 5 attachments, 10 MB each and 20 MB total, including your existing draft.';

  @override
  String get capsulePasteFailed =>
      'Clipboard text is unavailable. You can type or paste into the excerpt.';

  @override
  String get capsuleTextLimit =>
      'Keep each excerpt under 16,000 characters and the bundle under 32,000.';

  @override
  String get markdownCopyCode => 'Copy code';

  @override
  String get markdownCopied => 'Code copied';

  @override
  String get markdownCopyFailed => 'Could not copy code. Try again.';

  @override
  String get markdownCopyRetry => 'Retry';

  @override
  String get markdownWrapCode => 'Wrap lines';

  @override
  String get markdownScrollCode => 'Scroll lines';

  @override
  String get markdownExpandCode => 'Full screen';

  @override
  String get markdownReaderTitle => 'Code reader';

  @override
  String get markdownSnapshot =>
      'Snapshot of the code when opened. Close and reopen to read later updates.';

  @override
  String get tailscaleTitle => 'Connect with Tailscale';

  @override
  String get tailscaleQuickAdd =>
      'Use your private network and an HTTPS server address';

  @override
  String get tailscaleIntro =>
      'Reach OpenCode on another computer through your own Tailscale network. You control sign-in and VPN access in the official Tailscale app.';

  @override
  String get tailscaleAppStep => '1. Open your private network';

  @override
  String get tailscaleChecking => 'Checking for the Tailscale app…';

  @override
  String get tailscaleInstalled =>
      'Tailscale is installed. VPN connection is unverified.';

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
  String get tailscaleOpenFailed =>
      'Tailscale could not open. Open it from your launcher, then return here. Your address stays in this form.';

  @override
  String get tailscaleOpen => 'Open Tailscale';

  @override
  String get tailscaleInstall => 'Get official Android app';

  @override
  String get tailscaleCheckAgain => 'Check app again';

  @override
  String get tailscaleAddressStep => '2. Review your server address';

  @override
  String get tailscaleAddressLabel => 'Private HTTPS server address';

  @override
  String get tailscaleAddressDetail =>
      'Use the full HTTPS origin printed by Tailscale Serve, such as https://computer.tailnet-name.ts.net. Keep any HTTPS port it prints. A short device name or a raw HTTP port may not provide a valid certificate.';

  @override
  String get tailscaleAddressError =>
      'Enter an HTTPS origin with a valid port (1–65535). Remove paths, credentials, query text and fragments. Use the full address from Serve; do not replace https with http.';

  @override
  String get tailscaleReviewDetail =>
      'Continue only with an address you recognize. The next screen reviews your server credentials before you explicitly test or save. This app cannot confirm that an address is private from its name alone.';

  @override
  String get tailscaleContinue => 'Continue to authentication';

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
  String get a2aRetryDraftSave => 'Retry saving draft';

  @override
  String get a2aSavingDraft => 'Saving draft changes…';

  @override
  String a2aCardVersion(String version) {
    return 'Agent version: $version';
  }

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · Text tasks';

  @override
  String get a2aTitle => 'External agents';

  @override
  String get a2aIntro => 'Bring an agent you trust.';

  @override
  String get a2aBoundary =>
      'Connect to an A2A agent and send a task you choose. Only the text you submit is shared. Your projects, files and other conversations stay on this phone.';

  @override
  String get a2aAdd => 'Add agent';

  @override
  String get a2aEmpty =>
      'No external agents yet. Start with an agent\'s HTTPS address or public Agent Card URL.';

  @override
  String get a2aDeleteAgent => 'Delete agent';

  @override
  String get a2aDeleteAgentDetail =>
      'Remove this agent, its saved tasks and its credential from this phone. This does not stop remote work or delete data held by the agent.';

  @override
  String get a2aDeleteLocal => 'Delete local data';

  @override
  String get a2aDeletionPending =>
      'Local deletion is incomplete. This agent is unavailable until its remaining data is removed.';

  @override
  String get a2aRetryDelete => 'Retry deletion';

  @override
  String get a2aInspectIntro => 'Inspect before you connect';

  @override
  String get a2aAddress => 'Agent address';

  @override
  String get a2aInspect => 'Inspect Agent Card';

  @override
  String get a2aUnsupported =>
      'Unavailable: this card does not advertise the supported A2A 1.0 JSON-RPC, text and authentication combination on the same origin, or requires an unsupported extension. No task can be sent.';

  @override
  String get a2aBearerDetail =>
      'Supply an HTTP bearer credential issued for this agent. It is stored in the phone\'s secure storage and sent only to the inspected origin. No sign-in or credential sharing with other agents is performed.';

  @override
  String get a2aNoAuthDetail =>
      'This card requests no authentication. Do not send private information unless you trust this agent.';

  @override
  String get a2aBearer => 'Agent bearer credential';

  @override
  String get a2aSave => 'Save agent';

  @override
  String get a2aCardClaim =>
      'Self-reported Agent Card. This app has not verified the agent\'s identity, skills or billing terms.';

  @override
  String get a2aSkills => 'Advertised skills';

  @override
  String get a2aNewTask => 'New task';

  @override
  String get a2aTaskPrompt => 'Task text';

  @override
  String get a2aSendDetail =>
      'Review the text and destination before sending. The agent may use its own compute or services; check its terms. This app cannot estimate or limit that usage.';

  @override
  String get a2aReviewTask => 'Review task';

  @override
  String get a2aUpdateCredential => 'Update credential';

  @override
  String get a2aSavedTasks => 'Saved tasks';

  @override
  String get a2aReopenDetail =>
      'Reopening checks the existing task. It never sends your task again.';

  @override
  String get a2aDeliveryUnconfirmed => 'Delivery unconfirmed';

  @override
  String get a2aDraft => 'Not sent';

  @override
  String get a2aBack => 'Back';

  @override
  String get a2aTaskTitle => 'Agent task';

  @override
  String get a2aFresh => 'Checked with the agent this visit.';

  @override
  String get a2aSavedSnapshot =>
      'Saved locally. Refresh a known task to check its current state.';

  @override
  String get a2aCancelTask => 'Cancel task';

  @override
  String get a2aCancelDetail =>
      'Ask this agent to cancel this task. Work may already have finished, and the agent decides whether cancellation is possible.';

  @override
  String get a2aRequestCancel => 'Request cancellation';

  @override
  String get a2aForgetTask => 'Forget saved task';

  @override
  String get a2aForgetDetail =>
      'Remove this saved task from the phone. Remote work may continue, including a send whose delivery is unconfirmed. This cannot delete the agent\'s copy.';

  @override
  String get a2aYourReply => 'Your reply';

  @override
  String get a2aSend => 'Send to agent';

  @override
  String get a2aReplySameTask => 'Reply to this task';

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
  String get a2aRefresh => 'Refresh task';

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
  String get fileSourceExcerpt =>
      'Up to the first 200,000 characters are displayed. Copy and Save keep the original content.';

  @override
  String get filePreviewPartialSource =>
      'Only part of this file is shown. Copy and Save keep the original content.';

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
  String get fileTableMoreRows =>
      'Showing the first 200 rows. More data remains in the original file.';

  @override
  String fileTableRows(int rows, int columns) {
    return '$rows rows shown · $columns columns';
  }

  @override
  String fileTableColumn(int number) {
    return 'Column $number';
  }

  @override
  String get fileTableEmpty => 'This file has no rows.';

  @override
  String get fileCopied => 'File contents copied';

  @override
  String get fileCopyFailed => 'Could not copy file contents. Try again.';

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
  String get filePdfPageLimit =>
      'Only the first 200 pages can be previewed. Save the original to read the full document.';

  @override
  String filePdfPage(int page, int count) {
    return 'Page $page of $count';
  }

  @override
  String get filePrevious => 'Previous';

  @override
  String get fileNext => 'Next';

  @override
  String get fileCancel => 'Cancel';

  @override
  String get agentAccountTitle => 'Codex account';

  @override
  String get agentAccountScopeLost =>
      'This connection changed. Return to Servers and open the account for the connected profile.';

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
  String agentAccountPlan(String plan) {
    return 'Plan: $plan';
  }

  @override
  String get agentAccountHostNote =>
      'The official Codex runtime keeps your provider credentials. Account changes apply to this host, including other profiles connected to it.';

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
      'Start an official device-code sign-in on this host. Complete it in your browser; the app never receives your provider tokens.';

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
  String agentAccountReset(String time) {
    return 'Resets $time';
  }

  @override
  String get projectFolderChooserTitle => 'Choose a project folder';

  @override
  String get projectFolderChooserMessage =>
      'OpenCode Mobile does not work in the server’s home folder. Create a new folder or open a project folder to start sessions.';

  @override
  String get projectFolderCreate => 'Create a new folder';

  @override
  String get projectFolderOpen => 'Open a project folder';

  @override
  String get projectFolderBrowse => 'Choose from opened projects';

  @override
  String get projectFolderNoCreateHint =>
      'This server cannot create folders from the app. Create the folder on that machine, then open it here by its path.';

  @override
  String projectFolderCreateMessage(String directory) {
    return 'The folder is created in $directory on this device and opened as the workspace.';
  }

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
  String get globalSessionsTitle => 'All sessions';

  @override
  String get globalSessionsSearchLabel => 'Search session titles';

  @override
  String get globalSessionsSearchHint => 'Across every folder on this server';

  @override
  String get globalSessionsIncludeArchived => 'Include archived';

  @override
  String get globalSessionsArchivedShort => 'Archived';

  @override
  String get globalSessionsAllFolders => 'All folders';

  @override
  String get globalSessionsUnknownLocation => 'Unknown location';

  @override
  String globalSessionsSummary(String count, int folders) {
    return '$count sessions in $folders folders';
  }

  @override
  String globalSessionsSummaryOneFolder(String count) {
    return '$count sessions in one folder';
  }

  @override
  String globalSessionsFilteredSummary(int count, String total) {
    return '$count of $total sessions shown';
  }

  @override
  String get globalSessionsEmptyTitle => 'No sessions yet';

  @override
  String get globalSessionsEmptyMessage =>
      'Sessions from every folder on this server will appear here.';

  @override
  String get globalSessionsNoMatchTitle => 'No matching sessions';

  @override
  String get globalSessionsNoMatchMessage =>
      'Try a shorter title search or include archived sessions.';

  @override
  String get globalSessionsRefresh => 'Refresh';

  @override
  String get globalSessionsLoadMoreFailed => 'Could not load more sessions';

  @override
  String get globalSessionsOpen => 'Open';

  @override
  String get globalSessionsContinueHere => 'Continue here';

  @override
  String get globalSessionsActions => 'Session actions';

  @override
  String get globalSessionsWorking => 'Working';

  @override
  String get globalSessionsUntitled => 'Untitled session';

  @override
  String get workspaceNewSession => 'New session';

  @override
  String get workspaceIsolatedTask => 'Isolated task';

  @override
  String get workspaceAllSessions => 'All sessions';

  @override
  String get workspaceDismissNotice => 'Dismiss';

  @override
  String get workspaceManageProject => 'Manage project';

  @override
  String get workspaceManageProjectHint =>
      'Switch project, worktrees, and project health';

  @override
  String get workspaceManage => 'Manage';

  @override
  String get reviewCopiedFile => 'Updated file copied';

  @override
  String get reviewCopiedPatch => 'Patch copied';

  @override
  String get reviewCopyFailed => 'Could not copy. Try again.';

  @override
  String get reviewCopyFile => 'Copy updated file';

  @override
  String get reviewCopyPatch => 'Copy patch';

  @override
  String get reviewNoChanges => 'No changes';

  @override
  String get reviewEmptyDiff => 'No diff content';

  @override
  String get reviewHideContext => 'Hide revealed context';

  @override
  String get reviewAdded => 'Added';

  @override
  String get reviewRemoved => 'Removed';

  @override
  String get reviewUnchanged => 'Unchanged';

  @override
  String get reviewPatchNote => 'Patch note';

  @override
  String reviewShowNext(int count) {
    return 'Show next $count lines';
  }

  @override
  String reviewShowPrevious(int count, int remaining) {
    return 'Show $count previous lines ($remaining hidden)';
  }

  @override
  String reviewMissingContext(int count) {
    return '$count unchanged lines not included in patch';
  }

  @override
  String reviewCounts(int added, int removed) {
    return '$added added, $removed removed';
  }

  @override
  String reviewLineDescription(String kind, int number, String text) {
    return '$kind, line $number: $text';
  }

  @override
  String reviewNoteDescription(String kind, String text) {
    return '$kind: $text';
  }

  @override
  String settingsDiscoveryNewChatsModel(String model) {
    return 'New chats: $model';
  }

  @override
  String get onboardingValueTitle => 'Keep your work moving.';

  @override
  String get onboardingValueBody =>
      'Ask your coding agent for a change, review the result, and pick up where you left off.';

  @override
  String get onboardingConnect => 'Connect to a server';

  @override
  String get onboardingDemoNote => 'A simulated session. No server needed.';

  @override
  String get onboardingMoreSetup => 'More setup options';

  @override
  String get onboardingPrivateNetwork =>
      'Reach a server over your private network';

  @override
  String get onboardingRunOnPhone => 'Run OpenCode on this phone';

  @override
  String get onboardingTermuxNote => 'Guided Termux setup';

  @override
  String get onboardingSetupGuide => 'Setup guide';

  @override
  String get onboardingSaveConnect => 'Save & connect';

  @override
  String get onboardingSaveChanges => 'Save changes';

  @override
  String get onboardingTermuxSetup => 'Termux setup';

  @override
  String get activityClearHere => 'All clear here';

  @override
  String get activityStatusIncomplete => 'Status incomplete';

  @override
  String get activityCheckedLocationsClear =>
      'Nothing needs you in the checked locations.';

  @override
  String get activityUnknownStatusDetail =>
      'No requests loaded. Some server activity is still unknown.';

  @override
  String get activityCheckAgain => 'Check again';

  @override
  String get activitySavedServers => 'Saved servers';

  @override
  String get activitySelectedLocationsOnly => 'Last selected locations only';

  @override
  String get activityBackgroundUpdates => 'Background updates';

  @override
  String get activityBackgroundOffDetail =>
      'Off · choose when to stay connected';

  @override
  String activityPendingCount(int count) {
    return '$count pending';
  }

  @override
  String activityUnknownCount(int count) {
    return '$count unknown';
  }
}

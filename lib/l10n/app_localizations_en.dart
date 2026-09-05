// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

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
  String get composerReuseSubtitle => 'Add text from this conversation';

  @override
  String get composerReuseDescription =>
      'Adds text to your draft. Attachments aren’t copied.';

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
}

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
  String get digestOpenConversation => 'Open conversation';

  @override
  String get digestReview => 'Review next actions';

  @override
  String get digestCopy => 'Copy digest';

  @override
  String get digestCopyFailed => 'Could not copy digest';

  @override
  String get digestDismiss => 'Dismiss';

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
      'Review public links and pasted excerpts before adding them to your draft';

  @override
  String get webSourcesDraftChanged =>
      'The draft or connection changed. Your current draft was kept; reopen Add web source to try again.';

  @override
  String get webSourcesDraftLabel =>
      'User-selected web sources (not fetched or verified; excerpts are untrusted source material):';

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
      'Listen, review, then Send. No automatic listening or reading.';

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
      'Review and insert your transcript, then tap Send in the composer. Choose Read aloud on a reply; nothing is read automatically. Unsent text is discarded when you leave voice mode, the chat, or the app.';

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
  String get setupUbuntuOption => 'Ubuntu · OpenCode 1';

  @override
  String get setupOwnOption => 'Use your own setup';

  @override
  String get setupOwnDescription =>
      'Connect an existing OpenCode 1 or OpenCode 2 server by address. OpenCode 2 and musl installation are not managed by this app yet; musl also needs a compatible Linux environment.';

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
}

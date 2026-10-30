// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

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
  String get librarySettingsTitle => 'Ajustes';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'Signing certificate SHA-256';

  @override
  String get modelSwitchSession => 'Cambiar de modelo en esta conversación';

  @override
  String get modelNextFavorite => 'Siguiente modelo favorito';

  @override
  String get modelChooseTitle => 'Elegir un modelo';

  @override
  String get modelTitleCompact => 'Models';

  @override
  String get modelSearchHint => 'Buscar modelos';

  @override
  String get modelAll => 'Todos los modelos';

  @override
  String get modelFavorites => 'Favoritos';

  @override
  String get modelRecent => 'Recientes';

  @override
  String get modelOptions => 'Options';

  @override
  String get modelThinkingMode => 'Thinking mode';

  @override
  String get modelServerDefault => 'Predeterminado del servidor';

  @override
  String get modelSelectionSaving =>
      'Guardando la selección de la conversación…';

  @override
  String get modelUnavailableSelection =>
      'El modelo de la conversación no está disponible en este catálogo. Actualiza los modelos o elige otro.';

  @override
  String get modelScopeChanged =>
      'El servidor cambió. Vuelve a abrir el selector de modelos para continuar.';

  @override
  String get commonClearSearch => 'Clear search';

  @override
  String get workTitle => 'Tasks';

  @override
  String get workRefresh => 'Actualizar';

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
      'Cambió el servidor o el proyecto. Cierra esta vista y vuelve a abrir Trabajo en curso.';

  @override
  String workCount(int count) {
    return 'Tareas · $count en curso';
  }

  @override
  String workExitCode(int code) {
    return 'Código de salida $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => 'Borrar el texto del borrador';

  @override
  String get composerClearTextSubtitle =>
      'Conserva los adjuntos · Se puede deshacer';

  @override
  String get composerDraftCleared => 'Texto del borrador borrado';

  @override
  String get composerReuseTitle => 'Reutilizar un mensaje';

  @override
  String get queueSaveFailed =>
      'No se pudo guardar el borrador en cola en este dispositivo. Tu texto sigue aquí. Revisa el espacio disponible e inténtalo de nuevo.';

  @override
  String get fileSave => 'Guardar';

  @override
  String get fileReload => 'Actualizar';

  @override
  String get queueRemoveFailed =>
      'No se pudo quitar este borrador del almacenamiento del dispositivo. Sigue en cola. Revisa el espacio disponible e inténtalo de nuevo.';

  @override
  String get composerReuseSubtitle =>
      'Reutiliza texto de esta conversación y de envíos recientes';

  @override
  String get composerReuseSearch => 'Search recent prompts';

  @override
  String get composerReuseEmpty => 'No matching prompts';

  @override
  String get backgroundWorkTitle => 'Move running work to background';

  @override
  String get backgroundWorkShortcut =>
      'Continúa este trabajo mientras usas la conversación · Ctrl+B';

  @override
  String get backgroundWorkNoop =>
      'No hay subagentes en primer plano que pasar a segundo plano.';

  @override
  String get backgroundWorkPromoted =>
      'Los subagentes continúan en segundo plano.';

  @override
  String get libraryNoModel => 'No model selected';

  @override
  String get chatAttachmentUnsupported =>
      'Se pueden adjuntar imágenes, PDF, archivos de texto y archivos de Excel o Word (.xlsx, .docx).';

  @override
  String get termuxRestartTitle => '¿Reiniciar el servidor local?';

  @override
  String get termuxRestartMessage =>
      'OpenCode no estará disponible por un momento. La app conservará tu proyecto actual y se reconectará sola.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count conversaciones están generando. Reiniciar las interrumpirá.',
      one: '1 conversación está generando. Reiniciar la interrumpirá.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'Reiniciar';

  @override
  String get chatCopyCompleteReply => 'Copiar la respuesta completa';

  @override
  String get chatCopyReplySoFar => 'Copiar la respuesta hasta ahora';

  @override
  String commandRunTitle(String command) {
    return 'Run /$command';
  }

  @override
  String get commandDestination => 'Conversación';

  @override
  String get commandNewChat => 'New conversation';

  @override
  String get commandUntitledChat => 'Untitled conversation';

  @override
  String get commandArguments => 'Arguments (optional)';

  @override
  String get commandRun => 'Run';

  @override
  String get commandRunning => 'Iniciando…';

  @override
  String get commandLocationChanged =>
      'The server or project changed. Close this dialog and open the command again.';

  @override
  String get refreshFailed => 'No se pudo actualizar';

  @override
  String get refreshRetry => 'Intentar de nuevo';

  @override
  String get globalSessionsLoadMore => 'Load more conversations';

  @override
  String get globalSessionsRefreshFailed => 'Could not refresh conversations.';

  @override
  String get workspaceRetryProjects => 'Try again';

  @override
  String get historyLoadOlder => 'Cargar mensajes anteriores';

  @override
  String get historyReload => 'Actualizar el historial reciente';

  @override
  String get historyCursorExpired =>
      'El historial anterior cambió o caducó. Actualiza el historial reciente para continuar.';

  @override
  String get historyLoadedOnly =>
      'Solo se incluyen los mensajes cargados. Carga el historial anterior para incluir más.';

  @override
  String get historyLoadedTotals => 'Usage and loaded history';

  @override
  String get historyCopyLoadedReply => 'Copiar la respuesta cargada';

  @override
  String get historyLoadedMessages => 'Loaded messages';

  @override
  String get historyLoadedCost => 'Cost of loaded messages';

  @override
  String get historyServerTotalsNote =>
      'Rows marked reported by server cover the conversation. Message counts and other estimates cover loaded history.';

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
  String get revertStaged => 'Reversión preparada';

  @override
  String get revertReview => 'Revisar';

  @override
  String get revertFromHere => 'Deshacer desde aquí';

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
      'Revisa la reversión preparada y bórrala o hazla permanente antes de enviar. Tu borrador se conserva.';

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
  String get sessionNoteSaved => 'Nota guardada';

  @override
  String get sessionNoteRemoved => 'Nota eliminada';

  @override
  String get sessionNotePending => 'Se aplica en el siguiente paso del agente.';

  @override
  String get sessionInstructionsUpdated => 'Instrucciones actualizadas';

  @override
  String get sessionInstructionsApplied =>
      'Se actualizaron las instrucciones de conversación del agente para este paso.';

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
  String get usagePrompts => 'Mensajes';

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
  String get shareSessionViewsTitle => 'Sincronizar estado de lectura';

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
  String get importParent =>
      'This conversation was started by another one, which must already exist on this server. Import that one first.';

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
  String get sessionPinsLoadFailed =>
      'Some pinned conversations could not be loaded. Refresh to try again.';

  @override
  String get promptStashSaveFailed =>
      'Could not save this prompt. Your composer is unchanged. Check device storage and try again.';

  @override
  String get promptOriginalDraft => 'Restaurar el borrador original';

  @override
  String get promptStashTitle => 'Mensajes guardados';

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
  String get commonRetry => 'Intentar de nuevo';

  @override
  String get shareWaitingForServer =>
      'Connect to a server and the shared text opens in a new conversation.';

  @override
  String get webSourcesDisclosure =>
      'This server can\'t search the web. Paste a public link, and an excerpt if you like. The page is not opened, and nothing is sent until you send your message.';

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
  String get digestOpenConversation => 'Abrir conversación';

  @override
  String get runResultsCommandsTitle => 'Commands';

  @override
  String get sessionOpenRelated => 'Open related';

  @override
  String get sessionCopyHandoff => 'Continue on computer';

  @override
  String get attentionTitle => 'Server attention';

  @override
  String get webSourcesTitle => 'Agregar fuente web';

  @override
  String get webSourcesEntryDetail =>
      'Busca cuando esté disponible, o pega enlaces y extractos para revisarlos antes de agregarlos a tu borrador';

  @override
  String get webSourcesDraftChanged =>
      'El borrador o el servidor cambió. Se conservó tu borrador actual; vuelve a abrir Agregar fuente web para intentarlo de nuevo.';

  @override
  String get webSourcesDraftLabel =>
      'Fuentes web elegidas por el usuario (sin verificar; los extractos son material de fuente no fiable):';

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
      'Termina de iniciar sesión en el navegador, vuelve y termina aquí. El enlace del navegador no se guarda.';

  @override
  String get pendingAuthEnterCode => 'Enter code';

  @override
  String get pendingAuthStillPending =>
      'El inicio de sesión sigue pendiente. No se inició un intento nuevo.';

  @override
  String get pendingAuthServerFailed =>
      'El servidor informó que el inicio de sesión falló. Los detalles del error del proveedor están ocultos.';

  @override
  String get pendingAuthExpired =>
      'Este inicio de sesión expiró. Empieza uno nuevo u olvida este.';

  @override
  String get pendingAuthFailed =>
      'No se pudo confirmar la acción. Revisa los inicios de sesión pendientes antes de reintentar. No se inició ningún inicio de sesión nuevo.';

  @override
  String get pendingAuthSaveUncertain =>
      'This phone couldn\'t save the sign-in to pick it up later. Keep the app open until it finishes.';

  @override
  String get pendingAuthRetrySave => 'Try saving recovery again';

  @override
  String get pendingAuthForget => 'Olvidar este inicio de sesión';

  @override
  String get pendingAuthUnsupported =>
      'Este servidor no puede recuperar inicios de sesión anteriores. Los inicios de sesión antiguos solo funcionan mientras su pantalla y conexión originales sigan disponibles.';

  @override
  String get pendingAuthOtherSource =>
      'Other pending sign-ins belong to another server or project. Return to their original source to manage them.';

  @override
  String get connectionHelpGuideTip =>
      'Keep the server off the public internet. Reach it over Tailscale\'s private HTTPS, or an encrypted tunnel ending on the device running this app. Localhost on your computer is not localhost on your phone.';

  @override
  String get voiceConversationTitle => 'Conversación por voz';

  @override
  String get voiceConversationDescription =>
      'Habla y luego toca Enviar: lo que dijiste llega al agente. Las respuestas solo se leen en voz alta si lo activas.';

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
      'Usa el cuadro de texto para los comandos con barra.';

  @override
  String get voiceInputUnavailable =>
      'La entrada de voz no está disponible. Revisa el modelo local y los ajustes del micrófono.';

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
      'Iniciando sesión en el servidor… Completa los pasos que te pida allí. Cerrar esto no lo detiene.';

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
  String get readAloudAction => 'Leer el texto de la respuesta';

  @override
  String get readAloudStop => 'Detener la lectura en voz alta';

  @override
  String get readAloudOtherVoice => 'Leer con otra voz';

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
  String get credentialMetadataOnly => 'Las claves permanecen en tu servidor.';

  @override
  String get credentialActiveUpdated =>
      'Cuenta activa actualizada desde el servidor.';

  @override
  String get credentialSwitchRequested =>
      'Cambio solicitado. Aún no lo confirma ningún evento del servidor.';

  @override
  String get credentialActive => 'Activa';

  @override
  String get credentialRename => 'Renombrar cuenta';

  @override
  String get credentialLabel => 'Etiqueta de la cuenta';

  @override
  String get credentialSave => 'Guardar etiqueta';

  @override
  String get credentialScopeChanged =>
      'El servidor o el proyecto cambió. Cierra y vuelve a abrir la gestión de cuentas antes de hacer cambios.';

  @override
  String get credentialProviderMissing =>
      'Este proveedor ya no está en la lista de integraciones del servidor.';

  @override
  String get credentialLoadFailed =>
      'No se pudieron actualizar las cuentas guardadas. Inténtalo de nuevo.';

  @override
  String get credentialMutationFailed =>
      'No se pudo confirmar el cambio de cuenta. Actualiza antes de reintentar; puede que el servidor ya lo haya aplicado.';

  @override
  String get credentialRefresh => 'Actualizar cuentas';

  @override
  String get credentialEmpty =>
      'No se informaron cuentas guardadas para este proveedor.';

  @override
  String get credentialEnvironment =>
      'Administrada por el entorno del servidor. No se puede quitar desde aquí.';

  @override
  String credentialUnnamed(int index) {
    return 'Cuenta guardada $index';
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
  String get mcpReconnecting => 'Reconectando';

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
      'Guarda texto, adjuntos y referencias para después';

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
  String get transcriptFindHint => 'Buscar en la conversación';

  @override
  String get transcriptFindClose => 'Cerrar búsqueda';

  @override
  String get transcriptFindPrevious => 'Coincidencia anterior';

  @override
  String get transcriptFindNext => 'Siguiente coincidencia';

  @override
  String get transcriptFindNone => 'Sin coincidencias';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$current de $total coincidencias',
      one: '1 coincidencia',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'Solo mensajes cargados. Carga mensajes anteriores para buscar más.';

  @override
  String get transcriptFindReasoning => 'Razonamiento';

  @override
  String get transcriptFindTool => 'Tool data';

  @override
  String get transcriptFindFile => 'File name';

  @override
  String get transcriptFindAll => 'Buscar en todo el historial';

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
  String get skillApplied => 'Habilidad añadida a esta conversación.';

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
  String get draftSaveFailed =>
      'Borrador sin guardar. Copia tu texto o reintenta.';

  @override
  String get draftStorageFull =>
      'El almacenamiento de borradores está lleno. Copia tu texto antes de salir.';

  @override
  String get draftProfileRemoved =>
      'Se eliminó el servidor original. Copia tu borrador para conservarlo.';

  @override
  String get draftRetrySave => 'Intentar guardar el borrador de nuevo';

  @override
  String get draftClearFailed =>
      'No se pudo borrar el borrador guardado. Inténtalo de nuevo antes de salir.';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => 'Tu borrador no está guardado';

  @override
  String get draftLeaveMessage =>
      'Copia tu texto para conservarlo o intenta guardar de nuevo. Si sales sin guardar, tus últimos cambios podrían perderse.';

  @override
  String get draftLeaveAction => 'Salir sin guardar';

  @override
  String get draftKeepEditing => 'Seguir editando';

  @override
  String get draftUnsaved => 'Sin guardar';

  @override
  String get draftAttachmentsLocal =>
      'Los adjuntos se guardan con este borrador en este dispositivo.';

  @override
  String get draftAttachmentsFailed =>
      'Los adjuntos necesitan recuperación o no se pudieron guardar. Inténtalo de nuevo antes de enviar.';

  @override
  String get draftAttachmentRecoveryTitle =>
      'Algunos adjuntos requieren atención';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return 'Estos adjuntos guardados faltan, no se pueden leer o pertenecen a otro proyecto: $names. Usa los adjuntos disponibles y quita estos del borrador, o conserva el borrador guardado e inténtalo más tarde.';
  }

  @override
  String get draftUseAvailableAttachments => 'Usar los adjuntos disponibles';

  @override
  String get draftKeepSavedAttachments => 'Conservar el borrador guardado';

  @override
  String get photoLibraryAction => 'Galería de fotos';

  @override
  String get photoLibraryDescription =>
      'Elige una foto o una captura de pantalla';

  @override
  String get photoCameraAction => 'Tomar foto';

  @override
  String get photoTooLarge => 'Elige una foto de menos de 10 MB.';

  @override
  String get photoStorageFailed =>
      'No se pudo guardar la foto en este dispositivo. Libera algo de espacio y reintenta.';

  @override
  String get photoPendingOther =>
      'Una foto sigue esperando en otra conversación. Agrégala o descártala allí e inténtalo de nuevo.';

  @override
  String get photoUnavailable =>
      'No se pudo abrir la foto. Intenta agregarla de nuevo desde la Biblioteca de fotos o con Tomar foto.';

  @override
  String get photoPermissionDenied =>
      'Se denegó el acceso a las fotos. Permite el acceso a la cámara o las fotos en los ajustes de Android e inténtalo de nuevo.';

  @override
  String get photoPendingTitle => 'Foto pendiente';

  @override
  String get photoDiscard => 'Descartar la foto pendiente';

  @override
  String get photoAddToDraft => 'Agregar la foto recuperada al borrador';

  @override
  String get photoOtherLocation =>
      'Vuelve al servidor y proyecto originales de la foto antes de agregarla.';

  @override
  String get photoDraftFull =>
      'Quita primero un adjunto. Un borrador admite hasta 5 archivos y 20 MB en total.';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

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
      'Puede que el servidor haya iniciado este inicio de sesión sin confirmarlo. Revisa el servidor antes de empezar de nuevo.';

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
  String get demoReviewChanges => 'Revisar cambios';

  @override
  String get demoSetUpServer => 'Configura tu propio servidor';

  @override
  String get handoffCopyCommand => 'Copiar comando';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get managedHealthLifetime =>
      'Android may stop either app. Keeping the mobile connection alive does not guarantee the Termux server will keep running overnight.';

  @override
  String get quotaBudgetOff => 'Desactivado';

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
      'Los recuentos abarcan el último proyecto seleccionado de cada servidor, no todos los proyectos de ese servidor.';

  @override
  String get monitorDisclosure =>
      'La supervisión está desactivada hasta que la actives para un servidor. Las comprobaciones se hacen cada minuto aproximadamente mientras esta app está abierta. Las comprobaciones en segundo plano se hacen como máximo cada cinco minutos, solo mientras Mantener la conexión en segundo plano esté activado y el servicio de Android esté en ejecución. Android puede detener ese servicio; no se garantiza ningún tiempo restante.';

  @override
  String get monitorOptIn => 'Supervisar este servidor';

  @override
  String get monitorOptInDetail =>
      'Revisa permisos, preguntas y formularios pendientes en su último proyecto seleccionado.';

  @override
  String get monitorNotifications => 'Notificar cuando se necesite atención';

  @override
  String get monitorWifiDetail => 'Las revisiones se pausan sin Wi-Fi.';

  @override
  String get monitorQuiet => 'Horas de silencio';

  @override
  String get monitorQuietStart => 'Inicio de las horas de silencio';

  @override
  String get monitorQuietEnd => 'Fin de las horas de silencio';

  @override
  String get monitorSaveFailed =>
      'No se pudieron guardar los ajustes de supervisión. Inténtalo de nuevo.';

  @override
  String get monitorOpenFailed =>
      'This request or its project changed. Refresh the inbox and try again.';

  @override
  String get monitorSession => 'Conversation';

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
  String get monitorCheckIn => 'Revisar trabajos largos';

  @override
  String get monitorCheckInDetail => 'Un aviso tras el tiempo que elijas.';

  @override
  String get monitorCheckInDetailForeground =>
      'Una fila de aviso tras el tiempo que elijas.';

  @override
  String get monitorCheckInAfter => 'Revisar después de';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes minutos',
      one: '1 minuto',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => 'Time to check in';

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
  String get usageWidgetTitle => 'Usage widget';

  @override
  String get usageWidgetIntro =>
      'Shows what’s left on your plans on the home-screen Usage widget. Updates while the app runs.';

  @override
  String get usageWidgetPhoneTitle => 'This phone';

  @override
  String get usageWidgetSearchAliases =>
      'usage widget home screen claude codex plan limit remaining paseo';

  @override
  String get quotaMonitorSourceChanged =>
      'The account on this server changed, so checks stopped. Open Remaining usage on that server and read it again.';

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
      'No se pudieron leer los mensajes en cola guardados. No se pueden poner mensajes nuevos en cola hasta que se borren estos datos del dispositivo.';

  @override
  String get queueStorageDiscardUnreadable =>
      'Esto elimina de forma permanente de este dispositivo los mensajes en cola ilegibles y sus adjuntos. Se desconocen su contenido y su número. No se ve afectado nada en el servidor.';

  @override
  String get filesViewerScopeChanged =>
      'El servidor cambió. Cierra y vuelve a abrir este archivo.';

  @override
  String get queueStorageCountUnknown =>
      'No se pudieron leer los datos en cola guardados. Se desconoce el número de mensajes en cola.';

  @override
  String get codexConnectionVerified =>
      'Conexión verificada. Guarda y conecta para continuar.';

  @override
  String get codexApprovalRecoveryNotice =>
      'Tras reconectar, revisa las aprobaciones pendientes en tu computadora.';

  @override
  String connectionPasswordUnreadable(String server) {
    return 'No se puede leer la contraseña guardada de $server';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return 'No se puede leer el token guardado de $server';
  }

  @override
  String get connectionEnterPassword => 'Introducir la contraseña';

  @override
  String get connectionEnterToken => 'Introducir el token';

  @override
  String get connectionPasswordUnreadableDetails =>
      'El almacenamiento seguro de este teléfono no pudo abrir la contraseña guardada para este servidor. Puede pasar tras restaurar el teléfono desde una copia de seguridad o al cambiar el bloqueo de pantalla. La contraseña no se modificó: vuelve a introducirla para conectar.';

  @override
  String get connectionTokenUnreadableDetails =>
      'El almacenamiento seguro de este teléfono no pudo abrir el token guardado para este servidor. Puede pasar tras restaurar el teléfono desde una copia de seguridad o al cambiar el bloqueo de pantalla. El token no se modificó: vuelve a introducirlo para conectar.';

  @override
  String get updateConnectionToken => 'Actualizar token';

  @override
  String get codexTextOnlyPrompt =>
      'Este servidor solo admite texto. Quita los adjuntos antes de enviar.';

  @override
  String get codexOfflineDraftSaved =>
      'Reconéctate antes de enviar. Tu borrador se guarda en este dispositivo.';

  @override
  String get codexReconnectBeforeSending => 'Reconéctate antes de enviar.';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 o wss://paseo.example';

  @override
  String get paseoPasswordLabel => 'Contraseña del daemon (opcional)';

  @override
  String get paseoPasswordHelp =>
      'Defínela con \"paseo daemon set-password\". Se guarda en el almacenamiento seguro de este dispositivo.';

  @override
  String get paseoSetupNotice =>
      'Ejecuta \"paseo start --no-relay\" en la computadora que tenga Claude Code o Pi instalado. Esta app nunca usa el relay de Paseo: conéctate desde este dispositivo o por tu propia red privada.';

  @override
  String get connectionDisplayName => 'Nombre para mostrar (opcional)';

  @override
  String get connectionDisplayNameHint => 'Por defecto, el host del servidor';

  @override
  String get connectionServerAddress => 'Dirección del servidor';

  @override
  String get codexAddressHint => 'wss://codex.example o ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => 'Carpeta del proyecto en el servidor';

  @override
  String get codexTokenReentry => 'Vuelve a ingresar el token de conexión';

  @override
  String get codexTokenLabel => 'Token de conexión';

  @override
  String get codexTokenStorageHelp =>
      'Se guarda de forma segura en este dispositivo y se envía solo a este servidor Codex.';

  @override
  String get projectConfiguredFolder => 'Configured folder';

  @override
  String get termuxGuideTitle => 'Conecta Termux una vez';

  @override
  String get termuxGuideOpenFailed =>
      'Se copió el comando, pero Termux no se pudo abrir. Abre Termux tú mismo o vuelve a probar Copiar y abrir Termux.';

  @override
  String get termuxGuideCopyOpenFailed =>
      'No se pudo copiar el comando ni abrir Termux.';

  @override
  String get termuxPermissionDenied =>
      'Android no permitió que esta app ejecute comandos en Termux.';

  @override
  String get launchShortcutWaiting =>
      'Conectando con el servidor guardado. La conversación nueva se abre cuando esté listo.';

  @override
  String get launchShortcutNoServer =>
      'Elige un servidor y luego inicia una conversación nueva.';

  @override
  String get launchShortcutReentry =>
      'Ingresa las credenciales del servidor guardado y luego inicia una conversación nueva.';

  @override
  String get launchShortcutConnectionFailed =>
      'No se pudo conectar con el servidor guardado. Elige o corrige un servidor y luego inicia una conversación nueva.';

  @override
  String get launchUiPinnedUntitled => 'Untitled conversation';

  @override
  String get launchUiSessionWaiting =>
      'Conectando con el servidor guardado. La conversación se abre cuando esté lista.';

  @override
  String get launchUiSessionNoServer =>
      'Elige un servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionReentry =>
      'Ingresa las credenciales del servidor guardado y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionConnectionFailed =>
      'No se pudo conectar con el servidor guardado. Elige o corrige un servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionOtherServer =>
      'Ese acceso directo pertenece a otro servidor. Conéctate a ese servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiActivityNoServer =>
      'Elige un servidor para ver qué necesita tu atención.';

  @override
  String get launchUiUsageNoServer =>
      'Guarda un servidor para ver cuánto te queda en tus planes.';

  @override
  String get queuedResendTitle => '¿Enviar este borrador otra vez?';

  @override
  String get queuedResendMessage =>
      'Puede que ya haya llegado a OpenCode. Enviarlo otra vez podría duplicarlo.';

  @override
  String get queuedResendConfirm => 'Enviar otra vez';

  @override
  String get queuedKeepForReview => 'Conservar para revisar';

  @override
  String get queuedDiscardUnconfirmedMessage =>
      'Su envío anterior nunca se confirmó; puede que ya esté en la conversación.';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores con un envío sin confirmar por revisar.',
      one: '1 borrador con un envío sin confirmar por revisar.',
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
  String get isolatedTaskRetryOpen => 'Intentar de nuevo';

  @override
  String get isolatedTaskClose => 'Close';

  @override
  String get returnBriefContinue => 'Continuar';

  @override
  String get capsuleError => 'Error';

  @override
  String get capsuleRemove => 'Quitar';

  @override
  String get markdownWrapCode => 'Ajustar líneas';

  @override
  String get markdownScrollCode => 'Desplazar líneas';

  @override
  String get markdownReaderTitle => 'Code reader';

  @override
  String get tailscaleTitle => 'Conectar con Tailscale';

  @override
  String get tailscaleIntro =>
      'Accede a OpenCode en otra computadora a través de tu propia red de Tailscale.';

  @override
  String get tailscaleAppStep => '1. Abre tu red privada';

  @override
  String get tailscaleChecking => 'Buscando la app de Tailscale…';

  @override
  String get tailscaleInstalled => 'Tailscale está instalado.';

  @override
  String get tailscaleMissing =>
      'Tailscale no está instalado. Instala la app oficial, vuelve y comprueba de nuevo.';

  @override
  String get tailscaleUnknown =>
      'No se pudo comprobar la app. Inténtalo de nuevo o abre Tailscale desde tu teléfono.';

  @override
  String get tailscaleUnsupported =>
      'Este dispositivo no puede abrir la app de Android. Configura Tailscale en este dispositivo por tu cuenta y luego revisa tu dirección HTTPS abajo.';

  @override
  String get tailscaleVpnHandoff =>
      'En Tailscale, inicia sesión en la red que llega a tu servidor, acepta la solicitud de VPN de Android si aparece y activa la conexión. OpenCode no puede ver ni cambiar el estado de esa VPN.';

  @override
  String get tailscaleReturned =>
      'Te damos la bienvenida. Se volvió a comprobar la presencia de la app; usa Probar conexión en la siguiente pantalla para comprobar tu servidor.';

  @override
  String get tailscaleOpen => 'Abrir Tailscale';

  @override
  String get tailscaleCheckAgain => 'Comprobar Tailscale de nuevo';

  @override
  String get tailscaleAddressStep => '2. Revisa la dirección de tu servidor';

  @override
  String get tailscaleAddressLabel => 'Dirección del servidor privado';

  @override
  String get tailscaleAddressDetail =>
      'Pega la dirección HTTPS completa de Tailscale Serve, o la dirección HTTP y el puerto de tu servidor en Tailscale. HTTP pide tu permiso antes de conectar.';

  @override
  String get tailscaleAddressError =>
      'Introduce una dirección de servidor HTTP o HTTPS completa, con su puerto si hace falta y sin ruta después.';

  @override
  String get tailscaleReviewDetail =>
      'Continúa solo con una dirección que reconozcas. La siguiente pantalla revisa las credenciales de tu servidor antes de que pruebes o guardes de forma explícita. Esta app no puede confirmar que una dirección sea privada solo por su nombre.';

  @override
  String get tailscaleContinue => 'Continuar con el inicio de sesión';

  @override
  String get tailscaleHelp => 'Configuración y recuperación de Tailscale';

  @override
  String get tailscaleServeHelp =>
      'En la computadora del servidor, Tailscale Serve puede ofrecer HTTPS privado para un puerto local de OpenCode. Usa Serve, no Funnel público. Se siguen aplicando las reglas de acceso de tu tailnet. Al activar HTTPS, los nombres del dispositivo y de la tailnet del certificado se publican en un registro público de certificados, aunque el acceso sigue siendo privado. Revisa la guía oficial antes de cambiar tu servidor.';

  @override
  String get tailscaleServeDocs => 'Leer la guía oficial de Serve';

  @override
  String get tailscaleAndroidDocs => 'Leer la guía oficial de Android';

  @override
  String get tailscaleRecovery =>
      'Si el servidor no es accesible, revisa Tailscale en ambos dispositivos, la dirección y el puerto, y tus reglas de acceso de red. Si usas una dirección de Serve, revisa también Serve en el servidor. Corrige la contraseña si se rechaza la autenticación y vuelve a probar la conexión.';

  @override
  String get tailscaleEditorDetail =>
      'Tu conexión de red se gestiona en Tailscale. Probar conexión comprueba este servidor OpenCode, no la VPN. Introduce aquí el usuario y la contraseña del propio servidor, no tu inicio de sesión de Tailscale. La ayuda de configuración conserva estos campos.';

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
  String get a2aTitle => 'Agentes externos';

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
  String get agentAccountTitle => 'Cuenta de Codex';

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
  String get agentAccountCancelled => 'Inicio de sesión cancelado';

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
  String get projectFolderCreate => 'Create a new folder';

  @override
  String get projectFolderOpen => 'Open a project folder';

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
  String get workspaceDismissNotice => 'Cerrar';

  @override
  String get reviewCopyFile => 'Copy updated file';

  @override
  String get reviewCopyPatch => 'Copy patch';

  @override
  String get onboardingPrivateNetwork =>
      'Accede a un servidor por tu red privada';

  @override
  String get onboardingSetupGuide => 'Guía de configuración';

  @override
  String get onboardingSaveConnect => 'Guardar y conectar';

  @override
  String get onboardingSaveChanges => 'Guardar cambios';

  @override
  String get onboardingTermuxSetup => 'En este teléfono';

  @override
  String get activitySavedServers => 'Saved servers';

  @override
  String get demoTaskTitle => 'Prueba un cambio pequeño';

  @override
  String get demoTaskInstruction =>
      'Envía el mensaje de ejemplo de abajo y luego revisa la edición propuesta.';

  @override
  String get reviewTitle => 'Revisar';

  @override
  String get modelChoiceReloadProviders => 'Recargar proveedores';

  @override
  String get modelChoiceDone => 'Done';

  @override
  String get modelChoicePartialSaveError =>
      'Modelo guardado. No se confirmó el agente elegido. Inténtalo de nuevo.';

  @override
  String get modelChoiceModelSaveError =>
      'No se pudo confirmar el modelo elegido. Revisa tu selección e inténtalo de nuevo.';

  @override
  String get workIdle => 'Idle';

  @override
  String get workStartedInBackground => 'Started in background';

  @override
  String get workBackgroundPending => 'Requesting background work…';

  @override
  String get workBackgroundRequested =>
      'Se solicitó trabajo en segundo plano. El estado se actualizará cuando el servidor lo informe.';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String get setupSwitchPending =>
      'La actualización a OpenCode 2 no ha terminado. Tus conversaciones guardadas se conservan. Inténtalo de nuevo.';

  @override
  String setupSwitchRetry(String runtime) {
    return 'Try $runtime again';
  }

  @override
  String get setupSwitchFailed =>
      'No se pudo terminar la actualización a OpenCode 2. Tus conversaciones guardadas se conservan. Inténtalo de nuevo.';

  @override
  String setupSwitchProfileName(String runtime) {
    return 'Este teléfono · $runtime';
  }

  @override
  String get setupSwitchMissingCredential =>
      'No se pudo leer la contraseña guardada del servidor. Tus conversaciones y archivos se conservan. Recupera el acceso a la contraseña guardada y vuelve a intentarlo.';

  @override
  String setupSwitchProgressTitle(String runtime) {
    return 'Cambiando a $runtime';
  }

  @override
  String e7ConnectionFailure1(int attempts) {
    return 'Se intentó $attempts veces. Reintentar no iniciará un servidor que no está en ejecución.';
  }

  @override
  String get e7ConnectionFailure2 => 'Se requiere un token de conexión';

  @override
  String get e7ConnectionFailure3 =>
      'Este servidor de Codex necesita un token de conexión para que la app pueda conectarse.';

  @override
  String get e7ConnectionFailure4 =>
      'Abre los ajustes del servidor e introduce el token de conexión de Codex.';

  @override
  String get e7ConnectionFailure5 => 'Token de conexión rechazado';

  @override
  String get e7ConnectionFailure6 =>
      'El servidor de Codex respondió, pero no aceptó el token de conexión guardado.';

  @override
  String get e7ConnectionFailure7 =>
      'Abre los ajustes del servidor e introduce un token de conexión de Codex vigente.';

  @override
  String get e7ConnectionFailure8 => 'Listener de Codex no disponible';

  @override
  String get e7ConnectionFailure9 => 'Endpoint de Codex inaccesible';

  @override
  String get e7ConnectionFailure12 =>
      'Inicia el listener de Codex en este dispositivo.';

  @override
  String get e7ConnectionFailure13 =>
      'Si está detrás de un túnel, mantén el túnel activo y verifica su endpoint local.';

  @override
  String get e7ConnectionFailure14 =>
      'Usa el endpoint wss:// de Codex o un túnel seguro activo.';

  @override
  String get e7ConnectionFailure15 =>
      'Comprueba que el listener remoto de Codex sea accesible desde este dispositivo.';

  @override
  String get e7ConnectionFailure16 => 'Contraseña rechazada';

  @override
  String get e7ConnectionFailure17 =>
      'El servidor respondió, pero no aceptó la contraseña guardada. Esto pasa cuando el servidor se reinició con una contraseña nueva.';

  @override
  String get e7ConnectionFailure18 =>
      'Ejecuta opencode2 pair en la computadora y pega el código nuevo.';

  @override
  String get e7ConnectionFailure19 =>
      'Si definiste OPENCODE_SERVER_PASSWORD a mano, cópiala de nuevo.';

  @override
  String get e7ConnectionFailure20 => 'Certificado no confiable';

  @override
  String get e7ConnectionFailure21 =>
      'El servidor está ahí, pero este dispositivo no confía en su certificado HTTPS, así que la app se negó a enviar la contraseña.';

  @override
  String get e7ConnectionFailure22 =>
      'Usa un certificado de una autoridad de confianza o una dirección de Tailscale Serve.';

  @override
  String get e7ConnectionFailure23 =>
      'Con un certificado autofirmado, edita este servidor y revisa su huella antes de confiar.';

  @override
  String get e7ConnectionFailure24 => 'Nada respondió en este teléfono';

  @override
  String get e7ConnectionFailure26 =>
      '¿Ejecutas OpenCode en Termux? Abre Termux y comprueba que el servidor siga en ejecución.';

  @override
  String get e7ConnectionFailure27 =>
      '¿Usas adb reverse o un reenvío SSH? Comprueba que el túnel siga conectado e inténtalo de nuevo.';

  @override
  String get e7ConnectionFailure28 =>
      '¿Te conectas a otra computadora? Cambia el servidor a su dirección HTTPS o vuelve a emparejar.';

  @override
  String get e7ConnectionFailure29 => 'El servidor no respondió a tiempo';

  @override
  String get e7ConnectionFailure31 =>
      '¿Estás en la misma red o VPN (por ejemplo, Tailscale) que la computadora?';

  @override
  String e7ConnectionFailure32(int port) {
    return '¿Un firewall o un portal cautivo bloquea el puerto $port?';
  }

  @override
  String get e7ConnectionFailure33 => 'Servidor inaccesible';

  @override
  String get e7ConnectionFailure35 =>
      '¿Sigue en ejecución opencode serve en la computadora?';

  @override
  String get e7ConnectionFailure36 =>
      '¿Estás en la misma red o VPN que la computadora?';

  @override
  String get e7ConnectionFailure37 =>
      '¿Cambió la dirección? Vuelve a emparejar para obtener la nueva.';

  @override
  String get e7ConnectionFailure38 => 'El servidor respondió con un error';

  @override
  String get e7ConnectionFailure39 =>
      'El servidor está en ejecución, pero se reportó como no saludable. Su propio registro dirá por qué.';

  @override
  String get e7ConnectionFailure40 =>
      'Reinicia opencode serve y observa su salida.';

  @override
  String get e7ConnectionFailure41 =>
      'Comprueba que esta app admita la versión del servidor.';

  @override
  String get e7ConnectionFailure42 => 'No se pudo conectar';

  @override
  String get e7ConnectionFailure44 =>
      '¿Está en ejecución el servidor del agente y es esta la dirección correcta?';

  @override
  String get e7ConnectionFailure45 =>
      '¿Está en ejecución opencode serve y es esta la dirección correcta?';

  @override
  String get e7PermissionAction1 => 'Ejecutar un comando de shell';

  @override
  String get e7PermissionAction2 => 'Editar un archivo';

  @override
  String get e7PermissionAction3 => 'Leer un archivo';

  @override
  String get e7PermissionAction4 => 'Acceder a un directorio externo';

  @override
  String get e7PermissionAction5 => 'Continuar tras fallos repetidos';

  @override
  String get e7PermissionAction6 => 'Se necesita permiso';

  @override
  String e7PermissionAction7(String permission) {
    return 'Usar $permission';
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
  String get e7BannerUpdatePassword => 'Actualizar contraseña';

  @override
  String get e7BannerLost => 'Conexión perdida';

  @override
  String get e7BannerDetails => 'Detalles';

  @override
  String e7BannerReconnectingServer(String server) {
    return 'Reconectando con $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return 'Reconectando con $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      'Lo que ves sigue disponible mientras se comprueba OpenCode. Las actualizaciones en vivo se reanudan solas.';

  @override
  String get e7BannerStaleExplanation =>
      'Lo que ves puede estar desactualizado hasta que OpenCode vuelva a estar accesible.';

  @override
  String e7BannerCheckingExplanationAgent(String agent) {
    return 'What you see stays available while $agent is checked. Live updates resume on their own.';
  }

  @override
  String e7BannerStaleExplanationAgent(String agent) {
    return 'What you see may be stale until $agent is reachable again.';
  }

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
      'Pairing works when this device can reach the server address. A remote HTTP address asks for your permission before sending the password. Prefer HTTPS or a private Tailscale connection.';

  @override
  String get e7SharedOlderServersWithoutPairing =>
      'Older servers without pairing';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      'Para una conexión local, inicia OpenCode 2 en esta computadora. Usa la contraseña que muestra:';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      'Luego agrega el servidor manualmente con el usuario opencode y esa contraseña.';

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
  String get e7SharedOpenInsecureHTTPLink => '¿Abrir enlace HTTP no seguro?';

  @override
  String get e7SharedOpenExternalLink => '¿Abrir enlace externo?';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP no está cifrado. Otros dispositivos de la red pueden leer o modificar lo que envías y recibes.';

  @override
  String get e7SharedOpenHTTPLink => 'Abrir enlace HTTP';

  @override
  String get e7SharedOpenLink => 'Abrir enlace';

  @override
  String get e7SharedNoAppCouldOpenThisLink =>
      'Ninguna app pudo abrir este enlace.';

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
  String get e7LocaleUiLanguage => 'Idioma';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => 'Usar el idioma del sistema';

  @override
  String get e7LocaleUiClose => 'Cerrar';

  @override
  String get e7LocaleUiDescription =>
      'Elige el idioma de toda la aplicación. Los mensajes del servidor y tu texto se muestran tal como están.';

  @override
  String get e7LocaleUiSaving => 'Guardando el idioma…';

  @override
  String get e7LocaleUiSaveFailed =>
      'No se pudo guardar el idioma. Sigue activa tu elección anterior. Elige un idioma para intentarlo de nuevo.';

  @override
  String get e7LocaleUiNewSession => 'New conversation';

  @override
  String get e7LocaleUiNewSessionHint =>
      'Start a conversation in the active project';

  @override
  String get e7LocaleUiWorkspaceHint => 'Every conversation, across projects';

  @override
  String get e7LocaleUiFilesHint =>
      'Files, changes, terminal and other project tools';

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
  String get e7LocaleUiDestinations => 'Switch tab';

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
  String get e7LocaleUiConnectionChanged => 'El servidor cambió.';

  @override
  String get e7AppearanceFollowAndroid => 'Seguir Android';

  @override
  String get e7AppearanceFollowSystem => 'Seguir el sistema';

  @override
  String get e7AppearanceLight => 'Claro';

  @override
  String get e7AppearanceDark => 'Oscuro';

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
  String get e7AppearanceTitle => 'Apariencia';

  @override
  String get e7AppearancePreviewHint =>
      'Primero la vista previa. Tu apariencia solo cambia cuando la aplicas.';

  @override
  String get e7AppearanceDynamicUnavailable =>
      'Los colores de Material You no están disponibles en este dispositivo.';

  @override
  String e7AppearanceUsesMode(String mode) {
    return 'Tu ajuste de claro u oscuro sigue siendo $mode.';
  }

  @override
  String get e7AppearanceSaveFailed =>
      'No se pudo guardar la apariencia. Tu ajuste anterior no cambió. Inténtalo de nuevo.';

  @override
  String get e7AppearanceSaving => 'Guardando…';

  @override
  String get e7AppearanceApply => 'Aplicar';

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
  String get e7SettingsUi8 => 'Desconectar';

  @override
  String get e7SettingsUi9 => 'OpenCode server';

  @override
  String get e7SettingsUi11 => 'Checking server health…';

  @override
  String get e7SettingsUi12 => 'Detenido por Android';

  @override
  String get e7SettingsUi14 => 'Activado · en ejecución';

  @override
  String get e7SettingsUi15 => 'Activado · iniciando';

  @override
  String get e7SettingsUi16 => 'este servidor';

  @override
  String get e7SettingsUi17 => 'unknown';

  @override
  String get e7SettingsUi18 => 'OpenCode is reconnecting.';

  @override
  String get e7SettingsUi19 => 'OpenCode is reconnecting. Try again.';

  @override
  String get e7SettingsUi22 => 'Android no activó el modo en segundo plano.';

  @override
  String get e7SettingsUi23 => 'Android detuvo la conexión en vivo';

  @override
  String get e7SettingsUi24 =>
      'Se agotó su límite diario de trabajo de sincronización de datos en segundo plano, así que el modo en vivo se desactivó. Vuelve a activarlo para reconectar; el límite se restablece en 24 horas.';

  @override
  String get e7SettingsUi25 => 'Mantener la conexión en segundo plano';

  @override
  String get e7SettingsUi26 =>
      'Mantiene las ejecuciones actualizadas con la app cerrada y te avisa cuando una te necesita. Usa más batería y muestra una notificación permanente.';

  @override
  String get e7SettingsUi27 =>
      'Acceso sin restricciones a la batería permitido';

  @override
  String get e7SettingsUi28 => 'Permitir acceso sin restricciones a la batería';

  @override
  String get e7SettingsUi29 =>
      'Android aún puede aplicar su límite de tiempo para servicios en primer plano.';

  @override
  String get e7SettingsUi30 =>
      'Opcional. Ayuda a conservar la conexión en vivo durante Doze. Android 15+ limita el trabajo de sincronización de datos en segundo plano a seis horas cada 24 horas.';

  @override
  String get e7SettingsUi31 => 'Detenido por Android: toca para reiniciar';

  @override
  String get e7SettingsUi32 => 'En ejecución ahora';

  @override
  String get e7SettingsUi34 =>
      'Android lo detiene tras 6 horas al día. La app te avisará cuando lo haga.';

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
  String get e7SettingsUi65 => 'Set up OpenCode 2';

  @override
  String get e7SettingsUi66 =>
      'Keep OpenCode running after you close the terminal.';

  @override
  String get e7SettingsUi67 => 'Server updates';

  @override
  String get e7SettingsUi68 => 'Upgrade from the machine running the server';

  @override
  String get e7SettingsUi69 => 'Claro u oscuro';

  @override
  String get e7SettingsUi70 => 'Tema';

  @override
  String get e7SettingsUi74 => 'Always allowed actions';

  @override
  String get e7SettingsUi76 => 'En este dispositivo';

  @override
  String get e7SettingsUi77 => 'Almacenamiento usado';

  @override
  String get e7SettingsUi78 => 'Borrar mensajes en cola';

  @override
  String get e7SettingsUi79 => 'No hay nada pendiente de enviar';

  @override
  String get e7SettingsUi80 => '¿Eliminar los mensajes en cola?';

  @override
  String get e7SettingsUi81 => 'Mensajes en cola eliminados';

  @override
  String get e7SettingsUi82 =>
      'No se pudieron eliminar los mensajes en cola. Revisa el almacenamiento del dispositivo e inténtalo de nuevo.';

  @override
  String get e7SettingsUi83 => 'Borrar borradores';

  @override
  String get e7SettingsUi84 => 'No hay texto del editor guardado';

  @override
  String get e7SettingsUi85 => '¿Eliminar los borradores?';

  @override
  String get e7SettingsUi86 => 'Borradores eliminados';

  @override
  String get e7SettingsUi87 =>
      'No se pudieron eliminar los borradores. Revisa el almacenamiento del dispositivo e inténtalo de nuevo.';

  @override
  String get e7SettingsUi88 => 'App diagnostics';

  @override
  String get e7SettingsUi92 => 'Privacy and data use';

  @override
  String get e7SettingsUi93 =>
      'Servidores, proveedores, voz, archivos, Termux y actualizaciones';

  @override
  String get e7SettingsUi94 => 'Voice licenses and provenance';

  @override
  String get e7SettingsUi96 => 'About and open source notices';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return '¿Desconectar de $server?';
  }

  @override
  String e7SettingsVersion(String version) {
    return 'Versión $version';
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
      other: '$queued mensajes en cola',
      one: '1 mensaje en cola',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts borradores',
      one: '1 borrador',
    );
    return '$total de trabajo sin enviar: $_temp0 ($queueBytes) y $_temp1 ($draftBytes). Los mensajes en cola se descartan tras $days días.';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Elimina los $count mensajes sin enviar y sus adjuntos, de todos los servidores',
      one:
          'Elimina el mensaje sin enviar y sus adjuntos, de todos los servidores',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Esto elimina $count mensajes sin enviar y sus adjuntos, de todos los servidores. Nunca se enviarán. No se ve afectado nada en el servidor.',
      one:
          'Esto elimina el mensaje sin enviar y sus adjuntos, de todos los servidores. Nunca se enviará. No se ve afectado nada en el servidor.',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Elimina el texto del editor guardado para $count conversaciones',
      one: 'Elimina el texto del editor guardado para 1 conversación',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Esto elimina el texto del editor guardado para $count conversaciones. No se ve afectado nada en el servidor.',
      one:
          'Esto elimina el texto del editor guardado para 1 conversación. No se ve afectado nada en el servidor.',
    );
    return '$_temp0';
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
  String get e7SettingsDetailUi25 => 'Built-in';

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
      'Projects you open or create appear here.';

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
      'La atención en segundo plano no está disponible para este servidor. Abre la conversación para revisar las solicitudes actuales.';

  @override
  String get readerUiDisconnected => 'El servidor no está conectado.';

  @override
  String get readerUiIndicatorsUnavailable =>
      'Los indicadores de cambios en archivos no están disponibles en este servidor.';

  @override
  String get readerUiIndicatorsFailed =>
      'No se pudieron actualizar los indicadores de cambios en archivos.';

  @override
  String get readerUiReconnecting => 'OpenCode se está reconectando.';

  @override
  String get readerUiCommentAdded =>
      'Comentario de revisión agregado. Vuelve a la conversación para continuar.';

  @override
  String get readerUiCommentCopied =>
      'Comentario de revisión copiado. Pégalo en una conversación.';

  @override
  String get readerUiSearchSymbols => 'Buscar símbolos';

  @override
  String get readerUiSearchFiles => 'Buscar archivos';

  @override
  String get readerUiSelectFile => 'Selecciona un archivo para previsualizarlo';

  @override
  String get readerUiEmptyFolder => 'La carpeta está vacía';

  @override
  String get readerUiNoFiles => 'No se encontraron archivos';

  @override
  String get readerUiPullRefresh =>
      'Desliza hacia abajo para actualizar esta carpeta.';

  @override
  String get readerUiTryFileName => 'Prueba con otro nombre de archivo.';

  @override
  String get readerUiAttachPrompt => 'Adjuntar al mensaje';

  @override
  String get readerUiOpenReview => 'Abrir en Revisión';

  @override
  String get readerUiCopyPath => 'Copiar ruta';

  @override
  String get readerUiWorkspaceSymbols => 'Buscar símbolos del proyecto';

  @override
  String get readerUiSymbolsHint =>
      'Encuentra clases, funciones, métodos y variables por nombre.';

  @override
  String get readerUiNoSymbols => 'No se encontraron símbolos';

  @override
  String get readerUiSymbolsUnavailable =>
      'Prueba con otro nombre. Algunos servicios de lenguaje no admiten la búsqueda de símbolos en todo el proyecto.';

  @override
  String get readerUiFiles => 'Archivos';

  @override
  String get readerUiSymbols => 'Símbolos';

  @override
  String get readerUiChanges => 'Cambios';

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
      'No se pudieron guardar las preferencias del lector. Inténtalo de nuevo.';

  @override
  String get readerUiSourceFirst => 'Código fuente primero';

  @override
  String get readerUiServerOrder => 'Orden predeterminado';

  @override
  String readerUiLine(int number) {
    return 'línea $number';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return '$label agregado al mensaje';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label ya está en el mensaje';
  }

  @override
  String readerUiReferenceFull(int count) {
    return 'El mensaje ya tiene $count referencias';
  }

  @override
  String readerUiAttached(String name) {
    return '$name adjuntado.';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos modificados',
      one: '$count archivo modificado',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return '$name adjuntado. Vuelve a la conversación para agregar tu comentario.';
  }

  @override
  String readerUiSaveNamed(String name) {
    return 'Guardar $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return '$name se guardó en tu dispositivo.';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · Línea $line';
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
  String readerUiSaved(String name) {
    return '$name saved.';
  }

  @override
  String get readerUiSymbolFile => 'Archivo';

  @override
  String get readerUiSymbolModule => 'Módulo';

  @override
  String get readerUiSymbolNamespace => 'Espacio de nombres';

  @override
  String get readerUiSymbolPackage => 'Paquete';

  @override
  String get readerUiSymbolClass => 'Clase';

  @override
  String get readerUiSymbolMethod => 'Método';

  @override
  String get readerUiSymbolProperty => 'Propiedad';

  @override
  String get readerUiSymbolField => 'Campo';

  @override
  String get readerUiSymbolConstructor => 'Constructor';

  @override
  String get readerUiSymbolEnum => 'Enum';

  @override
  String get readerUiSymbolInterface => 'Interfaz';

  @override
  String get readerUiSymbolFunction => 'Función';

  @override
  String get readerUiSymbolVariable => 'Variable';

  @override
  String get readerUiSymbolConstant => 'Constante';

  @override
  String get readerUiSymbolEnummember => 'Miembro de enum';

  @override
  String get readerUiSymbolStruct => 'Struct';

  @override
  String get readerUiSymbolEvent => 'Evento';

  @override
  String get readerUiSymbolOperator => 'Operador';

  @override
  String get readerUiSymbolTypeparameter => 'Parámetro de tipo';

  @override
  String get readerUiSymbolSymbol => 'Símbolo';

  @override
  String get readerUiAdded => 'Agregado';

  @override
  String get readerUiDeleted => 'Eliminado';

  @override
  String get readerUiModified => 'Modificado';

  @override
  String get readerUiChanged => 'Cambiado';

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
  String get e7WorkspaceNeedsYou => 'Needs you';

  @override
  String get e7WorkspaceStopSharing => 'Dejar de compartir';

  @override
  String get e7WorkspaceDisconnect => 'Desconectar';

  @override
  String get e7WorkspaceBackExit => 'Pulsa «Atrás» otra vez para salir';

  @override
  String get e7WorkspaceConnected => 'Conectado';

  @override
  String get e7WorkspaceConnecting => 'Conectando';

  @override
  String get e7WorkspaceOffline => 'Sin conexión';

  @override
  String get e7WorkspaceReconnectingAgain =>
      'OpenCode is reconnecting. Try again.';

  @override
  String get e7WorkspaceDismissRequest => '¿Descartar esta solicitud?';

  @override
  String get e7WorkspaceDismissDetail =>
      'OpenCode continuará sin respuestas a estas preguntas.';

  @override
  String get e7WorkspaceNeedsInput => 'OpenCode necesita tu respuesta';

  @override
  String activityAgentNeedsInput(String agent) {
    return '$agent necesita tu respuesta';
  }

  @override
  String get e7WorkspaceSendAnswers => 'Enviar respuestas';

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
  String e7WorkspaceSessionId(String id) {
    return 'Conversación $id';
  }

  @override
  String get e7WorkspaceUnknownProject => 'Unknown project';

  @override
  String get e7WorkspaceJustNow => 'Hace un momento';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return 'hace $count min';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return 'hace $count h';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return 'hace $count d';
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
  String get chatUiAllMatchingRequests =>
      '(todas las solicitudes coincidentes)';

  @override
  String get chatUiNoOutput => '(no output)';

  @override
  String get chatUiNoResult => '(no result)';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen =>
      '1 referencia se añade como texto al enviar. No se guarda con tu borrador.';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      'Add an OpenCode project reference to this prompt';

  @override
  String get chatUiAddAnImageOrFileToThe =>
      'Agrega una imagen o un archivo al mensaje';

  @override
  String get chatUiAgent => 'Agente';

  @override
  String get chatUiAlreadyAnsweredElsewhere => 'Already answered elsewhere';

  @override
  String get chatUiAlreadyDelivered => 'Ya entregado';

  @override
  String get chatUiAlwaysAllow => 'Permitir siempre';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover =>
      '«Permitir siempre» también cubriría';

  @override
  String get chatUiAnswerWasCutOffByTheLength =>
      'Answer was cut off by the length limit';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      'Cualquiera con el enlace puede ver esta conversación y su contexto compartido. No compartas conversaciones que contengan secretos, credenciales o archivos privados.';

  @override
  String get chatUiAppDiagnostics => 'App diagnostics';

  @override
  String get chatUiAppearance => 'Appearance';

  @override
  String get chatUiApplyPatch => 'Apply patch';

  @override
  String get chatUiAskOpenCode => 'Pregunta a OpenCode…';

  @override
  String get chatUiAttachFile => 'Adjuntar archivo';

  @override
  String get chatUiAttachToPrompt => 'Adjuntar al mensaje';

  @override
  String get chatUiAttachment => 'Adjunto';

  @override
  String get chatUiAttachmentLimitReached => 'Attachment limit reached';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 =>
      'Los adjuntos deben sumar como máximo 20 MB.';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes =>
      'Disponible cuando termine la ejecución actual';

  @override
  String get chatUiBrowseProjectAndGlobalSkills =>
      'Browse project and global skills';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      'Browse, preview, download, and attach project files';

  @override
  String get chatUiCancelAndReturnToTheComposer =>
      'Cancelar y volver al cuadro de mensaje';

  @override
  String get chatUiCancelMessage => 'Cancelar mensaje';

  @override
  String get chatUiCancelThisPendingMessage =>
      '¿Cancelar este mensaje pendiente?';

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
      'Elige otro modelo en el selector para crear tu lista de recientes.';

  @override
  String get chatUiChooseModel => 'Elegir modelo';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent =>
      'Choose the active OpenCode agent';

  @override
  String chatUiChooseAgentMode(String agent) {
    return 'Choose how $agent works: its modes';
  }

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
  String get chatUiCompactSession => 'Compactar conversación';

  @override
  String get chatUiCompactingConversation => 'Compactando la conversación…';

  @override
  String get chatUiCompactionFailed => 'Falló la compactación';

  @override
  String get chatUiCompactionStarted => 'Compactación iniciada';

  @override
  String get chatUiCompose => 'Compose';

  @override
  String get chatUiConnectProvider => 'Connect provider';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      'Connection health, server version, and live mode';

  @override
  String get chatUiContextAdded => 'Contexto agregado';

  @override
  String get chatUiContextCompacted =>
      'Los mensajes anteriores se resumieron para ahorrar espacio';

  @override
  String get chatUiCopiedPasteItIntoTheComposer =>
      'Copied. Paste it into the composer';

  @override
  String get chatUiCopyMessageText => 'Copiar el texto del mensaje';

  @override
  String get chatUiCopyShareLink => 'Copiar enlace para compartir';

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
  String get chatUiDelegateThisPrompt => 'Delegar este mensaje';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent =>
      'Delegate this prompt to a server subagent';

  @override
  String get chatUiDelegatedSession => 'Conversación delegada';

  @override
  String get chatUiDeleteMessage => 'Eliminar mensaje';

  @override
  String get chatUiDeleteThisMessage => '¿Eliminar este mensaje?';

  @override
  String get chatUiDetails => 'Detalles';

  @override
  String get chatUiDirectory => 'Directory';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink =>
      'Disable the current public conversation link';

  @override
  String get chatUiDiscardDraft => 'Descartar borrador';

  @override
  String get chatUiDiscardPromptChanges => 'Discard prompt changes?';

  @override
  String get chatUiDiscardQueuedDraft => '¿Descartar el borrador en cola?';

  @override
  String get chatUiDismissPromptError => 'Descartar el error del mensaje';

  @override
  String get chatUiEachAttachmentMustBe10MBOr =>
      'Cada adjunto debe pesar 10 MB o menos.';

  @override
  String get chatUiEdit => 'Edit';

  @override
  String get chatUiEditDraft => 'Editar borrador';

  @override
  String get chatUiEditTheCurrentPromptInAFocused =>
      'Edit the current prompt in a focused full-screen view';

  @override
  String get chatUiErrorDetails => 'Detalles del error';

  @override
  String get chatUiExpandReasoning => 'Expand reasoning';

  @override
  String get chatUiExportSessionTranscript =>
      'Exportar la transcripción de la conversación';

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
  String get chatUiForkFromThisPrompt => 'Bifurcar desde este mensaje';

  @override
  String get chatUiForkSession => 'Fork conversation';

  @override
  String get chatUiGeneratedFile => 'Archivo generado';

  @override
  String get chatUiHideTimestamps => 'Hide timestamps';

  @override
  String get chatUiImageDataIsUnavailable =>
      'Los datos de la imagen no están disponibles.';

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
      'Su texto vuelve al cuadro de mensaje como borrador.';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      'Jump anywhere. Fork restores a prompt for editing.';

  @override
  String get chatUiKeepItPending => 'Mantenerlo pendiente';

  @override
  String get chatUiKeepItQueued => 'Mantenerlo en cola';

  @override
  String get chatUiLanguageServer => 'Language server';

  @override
  String get chatUiList => 'List';

  @override
  String get chatUiLoadingSubagents => 'Loading subagents…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript =>
      'Razonamiento largo contraído en la transcripción';

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
  String get chatUiMessageTimestampsHidden =>
      'Marcas de hora de los mensajes ocultas';

  @override
  String get chatUiMessageTimestampsShown =>
      'Marcas de hora de los mensajes visibles';

  @override
  String get chatUiModel => 'Modelo';

  @override
  String get chatUiModelAndAgent => 'Model and agent';

  @override
  String get chatUiMoveSession => 'Move conversation';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory =>
      'Move this conversation to another project';

  @override
  String get chatUiMoved => 'Movido';

  @override
  String get chatUiNavigate => 'Navigate';

  @override
  String get chatUiNoAnswer => 'Sin respuesta';

  @override
  String get chatUiNoMatchingMessages => 'No matching messages';

  @override
  String get chatUiNoShareLinkWasReturned =>
      'No se devolvió ningún enlace para compartir';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer =>
      'No subagents available from this server';

  @override
  String get chatUiNotConnectedToTheServerRightNow =>
      'Not connected to the server right now.';

  @override
  String get chatUiOpenParentSession => 'Abrir la conversación principal';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals =>
      'Open persistent project terminals';

  @override
  String get chatUiOpenProviders => 'Abrir proveedores';

  @override
  String get chatUiOpenSubagentSession => 'Abrir la conversación del subagente';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      'OpenCode commands are unavailable offline.';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt =>
      'OpenCode no pudo completar este mensaje.';

  @override
  String get chatUiOpenCodeIsReconnecting => 'OpenCode is reconnecting.';

  @override
  String chatUiAgentIsReconnecting(String agent) {
    return '$agent is reconnecting.';
  }

  @override
  String chatUiAgentIsReconnectingTryAgainShortly(String agent) {
    return '$agent is reconnecting. Try again shortly.';
  }

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'OpenCode is reconnecting. Try again shortly.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'OpenCode se está reconectando. Inténtalo de nuevo cuando el servidor esté en línea.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain =>
      'OpenCode se está reconectando. Inténtalo de nuevo.';

  @override
  String get chatUiOpenCodeNeedsInput => 'OpenCode necesita tu respuesta';

  @override
  String get chatUiOpenCodeServerCommand => 'OpenCode server command';

  @override
  String get chatUiOutputPruned => 'Output pruned';

  @override
  String get chatUiPendingChange => 'Cambio pendiente';

  @override
  String get chatUiProjectFiles => 'Archivos del proyecto';

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
  String get chatUiPromptTools => 'Herramientas del mensaje';

  @override
  String get chatUiQuestion => 'Question';

  @override
  String get chatUiQuestions => 'Questions';

  @override
  String get chatUiQueuedRunsAfterThisTurn => 'Queued · runs after this turn';

  @override
  String get chatUiQueuedWillSendWhenReconnected =>
      'En cola: se enviará al reconectar';

  @override
  String get chatUiRead => 'Read';

  @override
  String get chatUiReasoningExpandedInTheTranscript =>
      'Razonamiento expandido en la transcripción';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice =>
      'Graba y transcribe en este dispositivo';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      'Referencia conservada para tu próximo mensaje: los comandos no la incluyen.';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      'Referencias conservadas para tu próximo mensaje: los comandos no las incluyen.';

  @override
  String get chatUiReloadMessages => 'Refresh messages';

  @override
  String get chatUiRename => 'Renombrar';

  @override
  String get chatUiRenameSession => 'Renombrar conversación';

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
      'Comentario de revisión agregado al mensaje';

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
  String get chatRenameEmpty => 'Escribe un título.';

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
      'Selecciona un modelo antes de compactar esta conversación.';

  @override
  String get chatUiServerCommands => 'Server commands';

  @override
  String get chatUiServerMessage => 'Mensaje del servidor';

  @override
  String get chatUiServerStatus => 'Server status';

  @override
  String get chatUiSessionChanges => 'Conversation changes';

  @override
  String get chatUiSessionContext => 'Conversation context';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      'Conversación compartida. Copia manualmente el enlace visible.';

  @override
  String get chatUiShareLinkCopied => 'Enlace para compartir copiado';

  @override
  String get chatUiShareSession => 'Compartir conversación';

  @override
  String get chatUiShareThisSession => '¿Compartir esta conversación?';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView =>
      'Compartida: cualquiera con el enlace puede verla';

  @override
  String get chatUiShowAllSubagentSessions =>
      'Mostrar todas las conversaciones de subagentes';

  @override
  String get chatUiShowTimestamps => 'Show timestamps';

  @override
  String get chatUiSkill => 'Skill ·';

  @override
  String get chatUiSkills => 'Skills';

  @override
  String get chatUiSlashCommandsAndAgents => 'Comandos con barra y agentes';

  @override
  String get chatUiStartACleanSessionInThisWorkspace =>
      'Start a clean conversation in this project';

  @override
  String get chatUiStopSharing => 'Dejar de compartir';

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
  String get chatUiSystemUpdate => 'Actualización del sistema';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo =>
      'Dile al agente por qué, o qué hacer en su lugar (opcional)';

  @override
  String get chatUiThatMessageIsNoLongerInThis =>
      'That message is no longer in this conversation.';

  @override
  String get chatUiTheFileHasNoContentToAttach =>
      'El archivo no tiene contenido para adjuntar.';

  @override
  String get chatUiTheFileHasNoContentToSave =>
      'El archivo no tiene contenido para guardar.';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe =>
      'The form or project changed. Reopen the current request.';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom =>
      'El archivo generado no está disponible en este servidor.';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      'El mensaje y todas sus partes se eliminan de forma permanente de la conversación, así que las próximas respuestas ya no los verán. Los cambios de archivos que hizo no se revierten.';

  @override
  String get chatUiTheServerReturnedEmptyImageData =>
      'The server returned empty image data.';

  @override
  String get chatUiThisDraftHasNotBeenSentTo =>
      'Este borrador aún no se ha enviado a OpenCode.';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      'Este borrador es demasiado grande para ponerlo en cola, o la cola está llena de borradores más recientes. Quita un adjunto o borra los mensajes en cola en Ajustes.';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn =>
      'No se puede restaurar este mensaje porque un adjunto no está disponible.';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn =>
      'No se puede reintentar este mensaje porque un adjunto no está disponible.';

  @override
  String get chatUiTimeline => 'Timeline';

  @override
  String get chatUiTimestampsUsage => 'Timestamps & usage';

  @override
  String get chatUiTitle => 'Título';

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
      'Transcripción copiada como Markdown';

  @override
  String get chatUiTranscriptDisplay => 'Transcript display';

  @override
  String get chatUiTranscriptSaved => 'Transcripción guardada';

  @override
  String get chatUiVoiceConversationWasInterrupted =>
      'La conversación por voz se interrumpió.';

  @override
  String get chatUiVoiceInput => 'Entrada de voz';

  @override
  String get chatUiVoiceInputIsUnavailable =>
      'La entrada de voz no está disponible.';

  @override
  String get chatUiLoadTools => 'Load tools';

  @override
  String chatUiToolFrom(String tool, String server) {
    return '$tool · $server';
  }

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
  String get chatUiInThisChat => 'en esta conversación';

  @override
  String get chatUiNewFile => 'new file';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return 'En cola: se enviará al reconectar. $detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '/$command no está disponible ahora mismo.';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return 'Puedes adjuntar hasta $count archivos.';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se enviaron $count mensajes en cola',
      one: 'Se envió 1 mensaje en cola',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores esperando a otros servidores',
      one: '1 borrador esperando a otros servidores',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return 'Los próximos turnos de esta conversación usan $model.';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '@$name ya está en el mensaje';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return '$filename adjuntado. Agrega tu comentario.';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return 'Guardar $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return '$filename se guardó en tu dispositivo.';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores en cola; se enviarán al reconectar.',
      one: '1 borrador en cola; se enviará al reconectar.',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores esperando a otros servidores.',
      one: '1 borrador esperando a otros servidores.',
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
    return 'Se necesita permiso: $title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return 'Pregunta: $title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count preguntas',
      one: '1 pregunta',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return 'Límite de velocidad alcanzado. Reintentando$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return 'Límite de velocidad alcanzado. Reintentando$attempt en $time';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count referencias se añaden como texto al enviar. No se guardan con tu borrador.',
      one:
          '1 referencia se añade como texto al enviar. No se guarda con tu borrador.',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '$count adjuntos';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes anteriores',
      one: '1 mensaje anterior',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count tok';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '$position de $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return 'Conversación delegada · $count';
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
      other: '$count archivos',
      one: '1 archivo',
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
    return 'No se pudo cargar este archivo desde el servidor de OpenCode: $error';
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
    return 'Respondió: $answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return 'Cargando $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return 'Vista previa de la imagen generada $filename';
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
  String get chatUiBackgroundResult => 'Resultado en segundo plano';

  @override
  String get chatUiBackgroundComplete => 'Completado';

  @override
  String get chatUiBackgroundError => 'Falló';

  @override
  String get chatUiBackgroundCancelled => 'Cancelado';

  @override
  String get chatUiResultSourceDetails => 'Detalles del mensaje del servidor';

  @override
  String get chatUiResultOpenChild => 'Abrir la conversación del subagente';

  @override
  String get chatUiNoResultText =>
      'El servidor no devolvió texto de resultado.';

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
  String get chatUiAssistant => 'Asistente';

  @override
  String get chatUiUser => 'Usuario';

  @override
  String get chatUiOpenCodeSession => 'Conversación de OpenCode';

  @override
  String get chatUiTool => 'Herramienta';

  @override
  String get chatUiFile => 'archivo';

  @override
  String get e7LibraryReportABug => 'Informar de un problema';

  @override
  String get e7LibraryKeyboardShortcuts => 'Keyboard shortcuts';

  @override
  String get e7LibraryHTTPHeader => 'HTTP header';

  @override
  String get e7LibraryEnvironmentVariable => 'environment variable';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      'The server is reconnecting. Try again shortly.';

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
      'The server is reconnecting. Try again.';

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
      'No project is open. Choose one at the top of Files.';

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
      'This worktree could not be prepared.';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return '$detail1 was created. Its setup status is not yet confirmed.';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis =>
      'Wait for this worktree to finish preparing.';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations =>
      'The server did not switch projects.';

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
  String get e7LibraryAuthenticationFailed => 'Falló la autenticación';

  @override
  String get e7LibraryAuthenticationAttemptExpired =>
      'El intento de autenticación expiró';

  @override
  String get e7LibraryAuthenticationComplete => 'Autenticación completada';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe =>
      'Vuelve del navegador e ingresa el código de autorización.';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      'Completa la autenticación en el navegador y luego revisa su estado.';

  @override
  String get e7LibraryAuthorizationCode => 'Código de autorización';

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
  String get e7LibraryUnavailable => 'No disponible';

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
    return '$detail1 está conectado';
  }

  @override
  String get e7LibraryTheSignInSourceChanged =>
      'La fuente del inicio de sesión cambió.';

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
  String get e7SetupApiKeyHint => 'Pega una clave de API';

  @override
  String get e7SetupBrowserHint =>
      'Abre un navegador. Si la redirección no llega a OpenCode, pega aquí la URL de retorno.';

  @override
  String get e7SetupDeviceCodeHint =>
      'Usa un código de un solo uso. Funciona desde un teléfono.';

  @override
  String get e7SetupAccountHint => 'Inicia sesión con tu cuenta';

  @override
  String get e7SetupNewTerminalDetail =>
      'Inicia un shell en el proyecto activo.';

  @override
  String get e7SetupShowPassword => 'Mostrar la contraseña del servidor';

  @override
  String get e7SetupAuthFailed =>
      'El servidor se inició pero falló la autenticación.';

  @override
  String get e7SetupScanInstruction =>
      'Apunta la cámara al código QR que imprime opencode2 pair.';

  @override
  String get e7SetupRightKey => 'Tecla flecha derecha';

  @override
  String get e7SetupRestartReconnectFailed =>
      'El servidor local se reinició, pero la app no pudo reconectarse.';

  @override
  String get e7SetupInstallingOpenCode => 'Instalando OpenCode';

  @override
  String get e7SetupNoOutput => 'Aún no hay salida del terminal.';

  @override
  String get e7SetupFollowLog => 'Seguir el registro del servidor';

  @override
  String get e7SetupOpenSetupGuide => 'Abrir la guía de configuración';

  @override
  String get e7SetupDownKey => 'Tecla flecha abajo';

  @override
  String get e7SetupVerifyContinue => 'Verificar y continuar';

  @override
  String get e7SetupAccessibleTerminal =>
      'Usar transcripción y entrada accesibles';

  @override
  String get e7SetupCameraFailedDetail =>
      'Puede que otra app esté usando la cámara. Pegar el código de emparejamiento funciona igual.';

  @override
  String get e7SetupTermuxNoAnswer =>
      'Termux no respondió. Toca «Copiar y abrir Termux», pega la línea en Termux, pulsa Intro y vuelve aquí.';

  @override
  String get e7SetupCopyOpenTermux => 'Copiar y abrir Termux';

  @override
  String get e7SetupStopTerminalDetail =>
      'Se terminará el proceso en ejecución y sus procesos hijo.';

  @override
  String get e7SetupEdit => 'Editar';

  @override
  String get e7SetupServerPassword => 'Contraseña del servidor';

  @override
  String get e7SetupHttpsHint =>
      'Prefiere HTTPS. HTTP remoto necesita tu permiso para cada servidor.';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      'El servidor local está listo, pero no se pudo guardar la versión observada. Actualiza la configuración para intentarlo de nuevo.';

  @override
  String get e7SetupPasteInstead => 'Pegar en su lugar';

  @override
  String get e7SetupConfirmUpdate => '¿Actualizar el OpenCode administrado?';

  @override
  String get e7SetupInstallServiceDetail =>
      'Instalador oficial más un servicio de usuario de systemd que sobrevive a terminales cerrados y reinicios.';

  @override
  String get e7SetupUpdateHost => 'Actualizar OpenCode en el host';

  @override
  String get e7SetupKeepAfterLogout =>
      'Mantenerlo en marcha tras cerrar sesión';

  @override
  String get e7SetupGetTermux => 'Obtener Termux';

  @override
  String get e7SetupRestartServer => 'Reiniciar el servidor';

  @override
  String get e7SetupResumeSetup =>
      'Reintentar — retoma donde se quedó la configuración';

  @override
  String get e7SetupHidePassword => 'Ocultar la contraseña del servidor';

  @override
  String get e7SetupControlKeys =>
      'Teclas de control del terminal. Desliza horizontalmente para ver más.';

  @override
  String get e7SetupEndInputKey => 'Fin de entrada, Control D';

  @override
  String get e7SetupGuidanceSaveFailed =>
      'No se pudo guardar la guía de conexión. Vuelve a intentar guardarla.';

  @override
  String get e7SetupServerOperation => 'Operación del servidor en curso';

  @override
  String get e7SetupNewTerminal => 'Terminal nuevo';

  @override
  String get e7SetupUnsavedProfile => 'El servidor no se ha guardado.';

  @override
  String get e7SetupCheckingInstall => 'Comprobando el entorno instalado...';

  @override
  String get e7SetupInspectTermuxFailed =>
      'Android no pudo inspeccionar Termux.';

  @override
  String get e7SetupUsername => 'Usuario (opcional)';

  @override
  String get e7SetupFirstSetupDuration =>
      'La primera configuración puede tardar de 10 a 15 minutos. Puedes salir de esta pantalla y volver; la configuración sigue en marcha.';

  @override
  String get e7SetupNoTerminals => 'No hay procesos de terminal';

  @override
  String get e7SetupEscapeKey => 'Tecla Escape';

  @override
  String get e7SetupLeftKey => 'Tecla flecha izquierda';

  @override
  String get e7SetupInstallService =>
      'Instalar OpenCode como servicio en segundo plano';

  @override
  String get e7SetupSaveToFinish => 'Conectado: guarda para terminar.';

  @override
  String get e7SetupPasswordStartupHint =>
      'Se muestra al iniciar el servidor. Déjala vacía si no tiene.';

  @override
  String get e7SetupInstallTermuxDetail =>
      'Instala la versión actual de Termux de F-Droid y vuelve aquí.';

  @override
  String get e7SetupStopBeforeUpdate =>
      'Detén la generación activa antes de actualizar OpenCode.';

  @override
  String get e7SetupRestartingLocal => 'Reiniciando el servidor local';

  @override
  String get e7SetupAddServer => 'Agregar servidor';

  @override
  String get e7SetupInteractiveTerminal => 'Usar terminal interactivo';

  @override
  String get e7SetupInstallingUbuntu => 'Configurando Ubuntu';

  @override
  String get e7SetupInterruptKey => 'Interrumpir, Control C';

  @override
  String get e7SetupServerUrl => 'URL del servidor';

  @override
  String get e7SetupUsbAccess => 'Accede desde este teléfono por USB';

  @override
  String get e7SetupWaitingTermux =>
      'Esperando la respuesta de Termux. Puede tardar un poco.';

  @override
  String get e7SetupDiscardChanges => '¿Descartar los cambios del servidor?';

  @override
  String get e7SetupPasswordRequired =>
      'No se puede leer la contraseña guardada';

  @override
  String get e7SetupPairing => 'Emparejando…';

  @override
  String get e7SetupCommandInput => 'Entrada de comandos del terminal';

  @override
  String get e7SetupCameraFailed => 'No se pudo abrir la cámara';

  @override
  String get e7SetupPastePassword => 'Pegar contraseña del servidor';

  @override
  String get e7SetupSavingLocal => 'Guardando los ajustes del servidor local';

  @override
  String get e7SetupCopyFailureReport => 'Copiar informe del fallo';

  @override
  String get e7SetupCameraDisabled => 'El acceso a la cámara está desactivado';

  @override
  String get e7SetupPastePairing => 'Pegar código de emparejamiento';

  @override
  String get e7SetupServers => 'Servers';

  @override
  String get e7SetupStartingSetup => 'Iniciando la configuración en Termux';

  @override
  String get e7SetupSetupNotStarted =>
      'Termux se abrió, pero la configuración no empezó. Inténtalo una vez más; si vuelve a pasar, copia el informe del fallo.';

  @override
  String get e7SetupThisDevice => 'Este dispositivo (Termux)';

  @override
  String get e7SetupTransportReconnecting =>
      'El transporte del servidor se está reconectando.';

  @override
  String get e7SetupStepUnavailable => 'aún no disponible';

  @override
  String get e7SetupUpdateHostDetail =>
      'Cuando el servidor informa de una actualización, Ajustes ofrece primero la actualización nativa; esto es el equivalente en el host.';

  @override
  String get e7SetupMissingPasswordShort =>
      'La contraseña guardada no está disponible. Introdúcela de nuevo, o déjala vacía solo si este servidor ya no requiere una.';

  @override
  String get e7SetupTesting => 'Probando…';

  @override
  String get e7SetupScanPairing => 'Escanear código de emparejamiento';

  @override
  String get e7SetupRestartUnconfirmed =>
      'No se pudo confirmar este reinicio. Actualiza su progreso antes de reintentar.';

  @override
  String get e7SetupExistingMissingCredential =>
      'Existe un servidor local, pero su credencial guardada no está disponible. Ejecuta la configuración de nuevo para reemplazarla de forma segura.';

  @override
  String get e7SetupPreparingModels => 'Preparando los modelos';

  @override
  String get e7SetupHostInstructions =>
      'Estos comandos se ejecutan en la computadora que aloja este servidor; la app no puede ejecutarlos por ti. Copia cada uno en un terminal de esa máquina.';

  @override
  String get e7SetupTranscript => 'Transcripción del terminal';

  @override
  String get e7SetupHostFirstSetup =>
      'Primera configuración: ejecútala en tu computadora';

  @override
  String get e7SetupNoCameraDetail =>
      'No hay nada con qué escanear. Ejecuta opencode2 pair en el servidor, copia el código que imprime y pégalo en el editor del servidor.';

  @override
  String get e7SetupDiscard => 'Descartar';

  @override
  String get e7SetupConnectionClosed => 'Conexión cerrada';

  @override
  String get e7SetupUpKey => 'Tecla flecha arriba';

  @override
  String get e7SetupConnect => 'Conectar';

  @override
  String get e7SetupRemoveTerminalDetail =>
      'Se quitará el registro de este terminal.';

  @override
  String get e7SetupInputDisconnected =>
      'La entrada no está disponible sin conexión.';

  @override
  String get e7SetupHostCopied =>
      'Copiado. Ejecútalo en la computadora del servidor.';

  @override
  String get e7SetupCameraPrivacy =>
      'La cámara se usa solo para leer el QR que imprime opencode2 pair, y solo mientras esta pantalla está abierta. Puedes pegar el código en su lugar: hace exactamente lo mismo.';

  @override
  String get e7SetupIsV2 => 'Este es un servidor OpenCode 2.';

  @override
  String get e7SetupResumeLive => 'Reanudar la vista en vivo';

  @override
  String get e7SetupTabKey => 'Tecla Tab';

  @override
  String get e7SetupCloseScanner => 'Cerrar el escáner';

  @override
  String get e7SetupChooseContinue => 'Elige cómo continuar';

  @override
  String get e7SetupTermuxOutdated =>
      'Esta versión de Termux es demasiado antigua para la app. Instala la actual y toca «Continuar la configuración».';

  @override
  String get e7SetupCameraNeeded =>
      'Se necesita acceso a la cámara para escanear';

  @override
  String get e7SetupTerminalActions => 'Acciones del terminal';

  @override
  String get e7SetupReadPassword =>
      'Leer la contraseña del servidor para esta app';

  @override
  String get e7SetupStartInstalled => '¿Iniciar el OpenCode instalado?';

  @override
  String get e7SetupTestConnection => 'Probar conexión';

  @override
  String get e7SetupCheckingTermux => 'Comprobando la conexión con Termux';

  @override
  String get e7SetupCheckingTermuxShort => 'Comprobando Termux…';

  @override
  String get e7SetupDefaultServer => 'OpenCode server';

  @override
  String get e7SetupSaving => 'Guardando…';

  @override
  String get e7SetupLinuxService => 'Configurar OpenCode 2';

  @override
  String get e7SetupPairingDesktopHint =>
      'Comprueba que el servidor esté en ejecución y que la dirección que imprimió sea accesible desde esta máquina.';

  @override
  String get e7SetupAboutNotices => 'Acerca de y avisos de código abierto';

  @override
  String get e7SetupSetupLost =>
      'Se perdió el rastro de la configuración que se ejecutaba en Termux';

  @override
  String get e7SetupReportCopied => 'Informe del fallo copiado.';

  @override
  String get e7SetupAppSettings => 'Permitir el permiso en Ajustes';

  @override
  String get e7SetupNoCamera => 'Este dispositivo no tiene cámara';

  @override
  String get e7SetupServerDisconnected => 'El servidor no está conectado.';

  @override
  String get e7SetupEmptyPasswordHint =>
      'Déjala vacía solo si este servidor ya no usa contraseña.';

  @override
  String get e7SetupAndroidOnly =>
      'La configuración en este teléfono solo está disponible en Android';

  @override
  String get e7SetupEditServer => 'Editar servidor';

  @override
  String get e7SetupReenterPassword => 'Vuelve a ingresar la contraseña';

  @override
  String get e7SetupMissingCredential =>
      'La credencial guardada de este servidor administrado no está disponible. Ejecuta la configuración de nuevo para reemplazarla de forma segura.';

  @override
  String get e7SetupReconnect => 'Reconectar';

  @override
  String get e7SetupSwitchNotStarted =>
      'El cambio de entorno de ejecución no se inició.';

  @override
  String get e7SetupNoUbuntu =>
      'No se encontró ninguna instalación de Ubuntu administrada.';

  @override
  String get e7SetupKeyUnavailable =>
      'No disponible mientras la terminal está desconectada';

  @override
  String get e7SetupCopyTerminal =>
      'Copiar la selección o la transcripción del terminal';

  @override
  String get e7SetupCommandHint => 'Type a command';

  @override
  String get e7SetupCheckInstallFailed =>
      'No se pudo comprobar el entorno instalado.';

  @override
  String get e7SetupConnectionFailed => 'Falló la conexión.';

  @override
  String get e7SetupOpenAppSettings => 'Abrir ajustes de la app';

  @override
  String get e7SetupFullWalkthrough =>
      'Guía completa (se abre en el navegador)';

  @override
  String get e7SetupPairingPhoneHint =>
      'Un servidor vinculado a su propio 127.0.0.1 no es accesible desde este teléfono hasta que lo conectes con un puente: “adb reverse tcp:PORT tcp:PORT” por USB, o un reenvío SSH. Para acceder por la red, colócalo detrás de HTTPS.';

  @override
  String get e7SetupOutputCopied => 'Salida de la configuración copiada.';

  @override
  String get e7SetupMissingPasswordLong =>
      'La contraseña guardada no está disponible. Introdúcela de nuevo, o déjala vacía solo si este servidor ya no requiere contraseña.';

  @override
  String get e7SetupNoServerGuide =>
      '¿Aún no hay un servidor? La guía de configuración explica cómo iniciar uno.';

  @override
  String get e7SetupStartingLocal => 'Iniciando el servidor local';

  @override
  String get e7SetupTerminalSemantics =>
      'Terminal interactivo. Usa el botón de accesibilidad para ver una transcripción legible y la entrada con etiqueta.';

  @override
  String get e7SetupRestartingLocalStage => 'Reiniciando el servidor local';

  @override
  String get e7SetupEmptyPairClipboard =>
      'El portapapeles está vacío. Ejecuta “opencode2 pair” en el servidor y copia el código que imprime.';

  @override
  String get e7SetupRestartActiveChanged =>
      'El servidor local se reinició, pero el servidor activo cambió. Reconéctate cuando quieras.';

  @override
  String get e7SetupTokenRequired => 'No se puede leer el token guardado';

  @override
  String get e7SetupPairingInstructions =>
      'En tu computadora ejecuta «opencode2 pair» y luego pega o escanea el código que imprime.';

  @override
  String get e7SetupHostDaily => 'Uso diario — se ejecuta en tu computadora';

  @override
  String get e7SetupStopLocal => 'Detener servidor local';

  @override
  String get e7SetupReadingProgress =>
      'Leyendo el progreso de la configuración';

  @override
  String get e7SetupCameraSettingsDetail =>
      'Android no volverá a preguntar, así que debes cambiarlo en los ajustes de la app: activa Cámara y vuelve. Pegar el código no necesita ningún permiso y funciona ahora mismo.';

  @override
  String get e7SetupVerifyTermuxFailed =>
      'Falló la verificación del puente de Termux.';

  @override
  String get e7SetupUbuntuOnly => 'Ubuntu está instalado. OpenCode aún no.';

  @override
  String get e7SetupRenameTerminal => 'Renombrar terminal';

  @override
  String get e7SetupInputUnavailable => 'Entrada del terminal no disponible';

  @override
  String get e7SetupUpdateInterruption =>
      'El servidor no estará disponible por un momento. Conviene detener antes la generación activa.';

  @override
  String get e7SetupRunningOnPhone =>
      'OpenCode se está ejecutando en este teléfono.';

  @override
  String get e7SetupLocalStopped =>
      'El servidor local está detenido. Sus archivos instalados se conservan.';

  @override
  String get e7SetupDidNotConnect => 'El servidor no se conectó.';

  @override
  String get e7SetupSendCommand => 'Enviar comando al terminal';

  @override
  String get e7SetupUnsupportedSetup =>
      'Configurar en el propio dispositivo solo funciona en teléfonos Android. En esta computadora, inicia OpenCode tú mismo y agrégalo como servidor.';

  @override
  String get e7SetupSendKey => 'Envía esta tecla al terminal';

  @override
  String get e7SetupRemoveTerminal => '¿Quitar el terminal?';

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
    return 'No se pudo conectar con $name. $detail Revisa la dirección y las credenciales del servidor e inténtalo de nuevo.';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return '$name se guardó, pero no pudo conectarse. Revisa la dirección y las credenciales del servidor e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return 'No se pudo guardar $name. El servidor existente quedó sin cambios. Revisa el almacenamiento del dispositivo e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return '¿Quitar $name?';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return 'Se quitó $name, pero su conexión no se pudo cerrar correctamente. Reinicia la app antes de conectarte a otro lugar. ($detail)';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return 'No se pudo quitar $name. Se conservaron el servidor guardado y la conexión actual. Revisa el almacenamiento del dispositivo e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return 'Emparejado con $host, elegido entre $count direcciones del código.';
  }

  @override
  String e7SetupPaired(String host) {
    return 'Emparejado con $host.';
  }

  @override
  String e7SetupStartFailed(String detail) {
    return 'No se pudo guardar o iniciar la configuración local: $detail';
  }

  @override
  String e7SetupRestartFailed(String detail) {
    return 'No se pudo reiniciar el servidor local: $detail';
  }

  @override
  String e7SetupStopFailed(String detail) {
    return 'Could not stop the local server: $detail';
  }

  @override
  String e7SetupElapsedSeconds(int seconds) {
    return '$seconds s transcurridos';
  }

  @override
  String e7SetupElapsedMinutes(int minutes, int seconds) {
    return '$minutes min $seconds s transcurridos';
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
    return 'Ninguna dirección del código de emparejamiento respondió:\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String get e7SetupPairNone =>
      'Aquí no hay ningún código de vinculación. Ejecuta «opencode2 pair» en el servidor y escanea o copia lo que imprime.';

  @override
  String get e7SetupPairLong =>
      'Eso es demasiado largo para ser un código de vinculación. Copia solo la línea que imprime «opencode2 pair» o escanea su código QR.';

  @override
  String get e7SetupPairInvalid =>
      'Eso no es un código de vinculación. Ejecuta «opencode2 pair» en el servidor y escanea o copia lo que imprime.';

  @override
  String get e7SetupPairShape =>
      'Ese código de vinculación tiene un formato incorrecto: debe ser un objeto JSON con «urls», «username» y «password».';

  @override
  String get e7SetupPairNoUrls =>
      'Ese código de vinculación no tiene el campo «urls», así que no hay dirección a la que conectar.';

  @override
  String get e7SetupPairUrlsType =>
      'El campo «urls» de ese código de vinculación no es una lista de direcciones.';

  @override
  String get e7SetupPairTooMany =>
      'Ese código de vinculación incluye más direcciones de las que la app probará. Asocia el servidor a una sola interfaz y vincula de nuevo.';

  @override
  String get e7SetupPairAddressType =>
      'Ese código de vinculación incluye una dirección que no es texto.';

  @override
  String get e7SetupPairAddressLong =>
      'Ese código de vinculación incluye una dirección demasiado larga para ser una URL de servidor.';

  @override
  String get e7SetupPairAddressMissing =>
      'Ese código de vinculación no trae ninguna dirección de servidor. Comprueba que el servidor esté escuchando y ejecuta «opencode2 pair» de nuevo.';

  @override
  String get e7SetupPairUsernameType =>
      'El campo «username» de ese código de vinculación no es texto.';

  @override
  String get e7SetupPairPasswordMissing =>
      'Ese código de vinculación no tiene el campo «password». Puede que esté cortado: escanea o copia el código completo.';

  @override
  String get e7SetupPairPasswordType =>
      'El campo «password» de ese código de vinculación no es texto.';

  @override
  String get e7SetupPairTestFailed =>
      'La prueba de conexión falló antes de poder comprobar el servidor. Prueba con otra dirección.';

  @override
  String get e7SetupNotOpenCode =>
      'La dirección no respondió como un servidor OpenCode. Revisa la dirección e inténtalo de nuevo.';

  @override
  String get e7SetupNoServerAnswer =>
      'El servidor no respondió. Comprueba que esté en marcha y que la dirección sea correcta.';

  @override
  String get e7SetupPairPasswordRejected =>
      'Contraseña rechazada. Revisa el código de vinculación e inténtalo de nuevo.';

  @override
  String get e7SetupPairAddressUnusable =>
      'Ese código de vinculación contiene una dirección de servidor inutilizable.';

  @override
  String get e7SetupInvalidAddress => '<dirección no válida>';

  @override
  String get e7SetupEnterUrl => 'Escribe una URL de servidor.';

  @override
  String get e7SetupIncludeScheme =>
      'Incluye https:// o http://. HTTP remoto necesita tu permiso.';

  @override
  String get e7SetupCompleteUrl =>
      'Escribe una URL completa del servidor, como https://server.example:4096.';

  @override
  String get e7SetupUrlScheme =>
      'Las URL de servidor deben usar https:// o http://.';

  @override
  String get e7SetupTermuxUrlScheme =>
      'Las URL de servidor deben usar https://, o http:// para Termux local.';

  @override
  String get e7SetupUrlCredentials =>
      'No incluyas credenciales en la URL. Usa los campos de abajo.';

  @override
  String get e7SetupUrlQuery =>
      'Quita los parámetros de consulta y los fragmentos de la URL del servidor.';

  @override
  String get e7SetupUrlPath =>
      'Quita la ruta de la URL del servidor. Escribe solo su origen.';

  @override
  String get e7SetupRequireHttps =>
      'Una contraseña solo se envía a otra computadora por https://. Usa la dirección https:// de la computadora, vincula con un código o conéctate con Tailscale.';

  @override
  String get e7SetupLocalHttp =>
      'Una dirección http:// solo funciona con este teléfono o con una dirección de red privada como 192.168.x.x. Para cualquier otra, vincula con un código, usa su dirección https:// o conéctate con Tailscale.';

  @override
  String get e7SetupRefused =>
      'La computadora rechazó la conexión. Comprueba que el servidor esté en marcha allí y que la dirección y el puerto sean correctos.';

  @override
  String get e7SetupTimeout =>
      'Se agotó el tiempo de la conexión. Revisa la dirección y que el servidor sea accesible desde este teléfono.';

  @override
  String get e7SetupDns =>
      'No se encontró ese nombre de host. Revisa cómo escribiste la dirección.';

  @override
  String get e7SetupCertificate =>
      'El certificado no es de confianza. Comprueba su huella antes de decidir si confiar en él.';

  @override
  String get e7SetupUnhealthy =>
      'El servidor respondió, pero informó que no está sano. Revisa sus registros e inténtalo de nuevo.';

  @override
  String get e7SetupServerStarting =>
      'El servidor se está iniciando. Inténtalo de nuevo en un momento.';

  @override
  String get e7SetupPasswordNeeded =>
      'Este servidor requiere su contraseña de serve.';

  @override
  String get e7SetupPasswordRejected =>
      'Contraseña rechazada. Copia la línea «server password» actual de la salida del servidor: cambia en cada reinicio a menos que se defina OPENCODE_PASSWORD.';

  @override
  String get e7SetupCredentialsRefused =>
      'El servidor rechazó las credenciales. Revisa el usuario y la contraseña.';

  @override
  String get e7SetupCodexUrl => 'Escribe una URL de servidor de Codex.';

  @override
  String get e7SetupCodexCompleteUrl =>
      'Escribe una URL completa del servidor de Codex.';

  @override
  String get e7SetupCodexScheme =>
      'Las URL de servidor de Codex deben usar wss://, o ws:// para un servidor local.';

  @override
  String get e7SetupCodexCredentials =>
      'No incluyas credenciales en la URL de Codex.';

  @override
  String get e7SetupCodexQuery =>
      'Quita los parámetros de consulta y los fragmentos de la URL de Codex.';

  @override
  String get e7SetupCodexPath =>
      'Quita la ruta de la URL del servidor de Codex.';

  @override
  String get e7SetupCodexPlain =>
      'ws:// solo funciona con un servidor en este teléfono. Usa una dirección wss:// para otra computadora.';

  @override
  String get e7SetupCodexDirectory =>
      'Escribe una ruta absoluta de proyecto de Codex.';

  @override
  String get e7SetupCodexToken =>
      'Escribe un token de conexión de Codex válido.';

  @override
  String get e7SetupRefreshPackages => 'Actualizando los paquetes de Termux';

  @override
  String get e7SetupRepairPackages =>
      'Reparando el conjunto de paquetes de Termux';

  @override
  String get e7SetupInstallDependencies => 'Instalando dependencias de Termux';

  @override
  String get e7SetupPrepareTermux => 'Preparando Termux';

  @override
  String get e7SetupInstallUbuntu => 'Instalando el entorno de Ubuntu';

  @override
  String get e7SetupRefreshModels =>
      'Actualizando el catálogo de modelos de OpenCode';

  @override
  String get e7SetupStartLocalServer => 'Iniciando el servidor local';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode está listo';

  @override
  String get e7SetupPrepareRuntime =>
      'Preparando el runtime de OpenCode seleccionado';

  @override
  String get e7SetupSwitchLocal => 'Cambiando el servidor local administrado';

  @override
  String get e7SetupCheckRestart =>
      'Comprobando el servidor local antes de reiniciar';

  @override
  String get e7SetupStoppingLocal => 'Deteniendo el servidor local';

  @override
  String get e7SetupStoppedLocal => 'Servidor local detenido';

  @override
  String get e7SetupUnknownSetup => 'Estado de configuración desconocido';

  @override
  String get e7SetupUnexpectedStop =>
      'El servidor local de OpenCode se detuvo de forma inesperada';

  @override
  String get e7SetupSetupInterrupted =>
      'La configuración se detuvo de forma inesperada; consulta la salida en vivo para ver los detalles';

  @override
  String get e7SetupRecoveryDisabled => 'Recuperación automática desactivada';

  @override
  String get e7SetupRecoveryWasDisabled =>
      'La recuperación automática estaba desactivada';

  @override
  String get e7SetupPortBusy =>
      'El puerto del servidor local sigue en uso; no se inició ningún reemplazo';

  @override
  String get e7SetupCredentialMismatch =>
      'La credencial guardada del servidor difiere de este runtime; restaura su credencial guardada original antes de volver';

  @override
  String get e7SetupUbuntuUnavailable =>
      'El entorno de Ubuntu administrado no está disponible';

  @override
  String get e7SetupRuntimeUnavailable =>
      'El comando de OpenCode seleccionado no está disponible';

  @override
  String get e7SetupIdentityMismatch =>
      'El proceso rastreado no es el servidor OpenCode administrado';

  @override
  String get e7SetupPasswordMissing => 'Falta la contraseña del servidor local';

  @override
  String get e7SetupVersionMissing =>
      'OpenCode se instaló, pero no informó ninguna versión';

  @override
  String get e7SetupModelsRefreshFailed =>
      'OpenCode se actualizó, pero no se pudo actualizar su catálogo de modelos';

  @override
  String get e7SetupStartupExited =>
      'El servidor de OpenCode terminó durante el inicio';

  @override
  String get e7SetupReadinessTimeout =>
      'El servidor de OpenCode no quedó autenticado y listo en 30 segundos';

  @override
  String get e7SetupUnreadableData =>
      'No se puede leer el registro de ubicación de datos de OpenCode 2';

  @override
  String get e7SetupUnreadablePrevious =>
      'No se puede leer el registro del runtime anterior';

  @override
  String get e7SetupReadManagerFailed =>
      'No se pudo leer el estado del administrador de configuración';

  @override
  String get e7SetupMissingManager => 'Falta el administrador de configuración';

  @override
  String get e7SetupRemoveInterruptedFailed =>
      'No se pudo quitar la instalación de Ubuntu interrumpida que pertenece a la app';

  @override
  String get e7SetupCheckStorageFailed =>
      'No se pudo comprobar el almacenamiento disponible antes de la configuración';

  @override
  String get e7SetupReadStorageFailed =>
      'No se pudo leer el almacenamiento disponible antes de la configuración';

  @override
  String get e7SetupRepositoryFailed =>
      'No se pudo seleccionar el repositorio oficial de paquetes de Termux';

  @override
  String get e7SetupRepositoryRefreshFailed =>
      'No se pudo actualizar packages.termux.dev; revisa la red e inténtalo de nuevo';

  @override
  String get e7SetupRepairFailed =>
      'No se pudo reparar la transacción de paquetes de Termux interrumpida';

  @override
  String get e7SetupUpgradeFailed =>
      'No se pudo completar la actualización segura de paquetes de Termux';

  @override
  String get e7SetupDependenciesFailed =>
      'No se pudieron instalar las dependencias de Termux';

  @override
  String get e7SetupDependenciesUnusable =>
      'Las dependencias de Termux siguen sin funcionar tras la reparación de paquetes';

  @override
  String get e7SetupUbuntuUnusable =>
      'Un contenedor de Ubuntu existente no se puede usar; la configuración no lo borrará';

  @override
  String get e7SetupExtractionFailed =>
      'La extracción de Ubuntu Base no creó un contenedor utilizable';

  @override
  String get e7SetupLockFailed =>
      'El administrador de configuración no pudo obtener su bloqueo de inicio';

  @override
  String get e7SetupSetupGroupFailed =>
      'El administrador de configuración no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupSwitchGroupFailed =>
      'El administrador de cambio no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupServerGroupFailed =>
      'El servidor administrado no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupRecordIdentityFailed =>
      'No se pudo registrar la identidad del proceso del servidor administrado';

  @override
  String e7SetupConnectingProfile(String name) {
    return 'Conectando con $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return 'Reconectando (intento $attempt)';
  }

  @override
  String get e7SetupOpeningWorkspace => 'Abriendo tu proyecto guardado.';

  @override
  String get e7SetupWhatToCheck => 'Qué revisar';

  @override
  String get e7SetupUpdatePassword => 'Actualizar contraseña';

  @override
  String e7SetupLastSetupDetail(String detail) {
    return 'Última salida de la configuración: $detail';
  }

  @override
  String e7SetupBridgeDetail(String detail) {
    return 'Detalle del puente: $detail';
  }

  @override
  String e7SetupDiagnosticsUnavailable(String detail) {
    return 'Diagnósticos no disponibles: $detail';
  }

  @override
  String e7SetupProbeHttp(String status) {
    return 'La dirección respondió, pero no como un servidor OpenCode (HTTP $status). Comprueba que la URL apunte a opencode serve.';
  }

  @override
  String e7SetupProbeError(String detail) {
    return 'Falló la prueba de conexión: $detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'El servidor de OpenCode terminó (código $code)';
  }

  @override
  String get e7SetupCheckTermux => 'En este teléfono';

  @override
  String get e7SetupCommandFailed => 'Falló el comando de Termux.';

  @override
  String get e7SetupUnexpectedBridge =>
      'Termux devolvió una respuesta inesperada del puente.';

  @override
  String get e7SetupSetupQueued => 'Configuración en cola';

  @override
  String get e7SetupNoSetup => 'No se ha iniciado ninguna configuración';

  @override
  String get e7SetupManagerMissingAfterLaunch =>
      'Falta el administrador de configuración tras el inicio';

  @override
  String get e7SetupBootstrapCleared => 'Estado de bootstrap borrado';

  @override
  String get e7SetupInstallingBeta => 'Instalando OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => 'Falló la autenticación';

  @override
  String get e7SetupUnknownVersion => 'versión desconocida';

  @override
  String get e7ModelUiClose => 'Cerrar el selector de modelos';

  @override
  String get e7ModelUiClearSearch => 'Clear model search';

  @override
  String get e7ModelUiLoadFailed => 'No se pudieron cargar los modelos';

  @override
  String get e7ModelUiRetry => 'Intentar de nuevo';

  @override
  String get e7ModelUiBasicCatalog =>
      'Este servidor devolvió un catálogo básico. No hay detalles de capacidades ni de contexto.';

  @override
  String get e7ModelUiEditFilters => 'Edit model filters';

  @override
  String get e7ModelUiFilterModels => 'Filter models';

  @override
  String get e7ModelUiFiltered => 'Filtered';

  @override
  String get e7ModelUiFilters => 'Filters';

  @override
  String get e7ModelUiRefresh => 'Actualizar modelos';

  @override
  String get e7ModelUiAnyCapability => 'Cualquier capacidad';

  @override
  String get e7ModelUiFastModes => 'Modos rápidos';

  @override
  String get e7ModelUiReasoning => 'Razonamiento';

  @override
  String get e7ModelUiLargestContext => 'Mayor contexto';

  @override
  String get e7ModelUiNoneAvailable => 'No models available';

  @override
  String get e7ModelUiFavoritesEmpty => 'Guarda aquí tus modelos favoritos';

  @override
  String get e7ModelUiRecentEmpty => 'Tu próxima elección empieza aquí';

  @override
  String get e7ModelUiNoMatches => 'No hay modelos que coincidan';

  @override
  String get e7ModelUiConfigureProvider =>
      'Configure a provider on the OpenCode server, then refresh.';

  @override
  String get e7ModelUiFavoritesHint =>
      'Toca la estrella junto a un modelo para encontrarlo aquí.';

  @override
  String get e7ModelUiRecentHint =>
      'Los modelos que uses aparecerán aquí, los más recientes primero.';

  @override
  String get e7ModelUiNoFastModes =>
      'Ningún modelo indica un modo rápido o de bajo esfuerzo explícito.';

  @override
  String get e7ModelUiNoMatchesHint =>
      'Prueba con otra búsqueda, proveedor o filtro de capacidad.';

  @override
  String get e7ModelUiClearFilters => 'Quitar filtros';

  @override
  String get e7ModelUiBrowseAll => 'Ver todos los modelos';

  @override
  String get e7ModelUiAgent => 'Agente';

  @override
  String get e7ModelUiServerDefault => 'Predeterminado del servidor';

  @override
  String get e7ModelUiProvider => 'Provider';

  @override
  String get e7ModelUiAllProviders => 'Todos los proveedores';

  @override
  String get e7ModelUiCurrent => 'Current model';

  @override
  String get e7ModelUiUnavailable => 'Unavailable';

  @override
  String get e7ModelUiDeprecated => 'Obsoleto';

  @override
  String get e7ModelUiPreview => 'Vista previa';

  @override
  String get e7ModelUiFavoritesFailed =>
      'No se pudieron guardar los favoritos. Inténtalo de nuevo.';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return 'Usar $model · $agent';
  }

  @override
  String get e7ModelUiUseSession => 'Usar en esta conversación';

  @override
  String get e7ModelUiUseNewSessions => 'Usar en conversaciones nuevas';

  @override
  String get e7ModelUiTools => 'Tools';

  @override
  String get e7ModelUiAttachments => 'Attachments';

  @override
  String get e7ModelUiDefault => 'Predeterminado';

  @override
  String get e7ModelUiSelectionGone =>
      'Esta opción ya no está disponible. Actualiza los modelos e inténtalo de nuevo.';

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
    return '$count de contexto';
  }

  @override
  String e7ModelUiOutput(String count) {
    return '$count output';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return 'Marcar $model como favorito';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return 'Quitar $model de favoritos';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · esfuerzo $effort';
  }

  @override
  String get e7ModelUiLoading => 'Cargando el catálogo de modelos';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input entrada · $output salida /1M';
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
  String get e7ModelUiProviderFallback => 'un proveedor';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first y $last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first y $last';
  }

  @override
  String get e7ModelUiListSeparator => ', ';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Sesión iniciada en $providers, pero el servidor aún no los ha cargado, así que sus modelos no pueden responder.',
      one:
          'Sesión iniciada en $providers, pero el servidor aún no lo ha cargado, así que sus modelos no pueden responder.',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain =>
      'No se puede acceder a OpenCode. Inténtalo de nuevo.';

  @override
  String get approvalsUiMenu => 'Aprobaciones';

  @override
  String get approvalsUiInheritedFrom =>
      'Heredado de la conversación principal';

  @override
  String get approvalsUiFollowParent => 'Volver a seguir a la principal';

  @override
  String get approvalsUiIndicatorOn => 'Aprobando automáticamente';

  @override
  String approvalsUiAutoApproved(String action) {
    return 'Aprobado automáticamente · $action';
  }

  @override
  String get approvalsUiFailedDetail =>
      'Falló la aprobación automática. Revisa esta solicitud.';

  @override
  String approvalsUiSaveFailed(String error) {
    return 'No se pudo guardar el ajuste de aprobación: $error';
  }

  @override
  String get approvalsUiIndicatorPaused => 'Aprobación automática en pausa';

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
  String get teamUiAddManually => 'Agregar manualmente';

  @override
  String get teamUiAddSubmit => 'Test and turn on';

  @override
  String get teamUiAddTesting => 'Checking the address…';

  @override
  String get teamUiAddTitle => 'Add AI Team host';

  @override
  String get teamUiAddressRequired => 'Enter the host address.';

  @override
  String get teamUiChange => 'Cambiar';

  @override
  String get teamUiDisclaimerComputer =>
      'Runs as fast as your computer; keep it awake';

  @override
  String get teamUiDisclaimerPhone =>
      'Android may stop it when the screen is off; slower than a computer';

  @override
  String get teamUiEditorBody =>
      'Si esta computadora ejecuta Gas City, la app puede encontrarla automáticamente.';

  @override
  String teamUiEditorConfigured(String url) {
    return 'Host de AI Team: $url';
  }

  @override
  String get teamUiEditorTitle => 'AI Team (opcional)';

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
  String get teamUiLearnHow => 'Más información';

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
  String get teamUiStartRunRefused => 'The host did not accept this task.';

  @override
  String get teamUiMergeTitleNotReady => 'Not ready to merge';

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
  String get teamUiMergeRefused =>
      'The host did not accept this, so nothing was merged.';

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
    return 'Detener $name';
  }

  @override
  String termuxProcsStopOneTitle(String name) {
    return '¿Detener $name?';
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
  String get teamUiPhoneOptionalTag => 'Opcional · experimental';

  @override
  String get teamUiPhoneChooseProjectTitle => 'Choose a project';

  @override
  String get teamUiPhoneContinue => 'Continuar';

  @override
  String get teamUiPhoneSuccessTitle => 'AI Team is running on this phone';

  @override
  String get teamUiPhoneRetry => 'Intentar de nuevo';

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
  String get phoneServerConnect => 'Conectar';

  @override
  String get phoneServerOpen => 'Abrir';

  @override
  String get phoneServerStart => 'Iniciar';

  @override
  String get phoneServerStop => 'Stop';

  @override
  String get phoneServerMore => 'More server actions';

  @override
  String get phoneServerForget => 'Olvidar el inicio de sesión guardado';

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
  String get safetyStopSharingTitle => '¿Dejar de compartir esta conversación?';

  @override
  String get safetyStopSharingBody =>
      'El enlace deja de funcionar para cualquiera que lo tenga. La conversación en sí no cambia.';

  @override
  String get safetyStopSharingKeep => 'Seguir compartiendo';

  @override
  String get safetyStopLocalServerTitle => '¿Detener el servidor local?';

  @override
  String get safetyStopLocalServerBody =>
      'Se interrumpe lo que el agente esté ejecutando en este teléfono y esta app se desconecta de él. Tus proyectos y conversaciones se quedan en el teléfono; vuelve a iniciar el servidor para continuar.';

  @override
  String get safetyStopLocalServerKeep => 'Seguir ejecutando';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return '¿Desconectar $server?';
  }

  @override
  String get safetyMcpDisconnectBody =>
      'Los agentes pierden sus herramientas hasta que lo vuelvas a conectar, y una llamada a una herramienta en curso puede fallar. Su configuración se conserva.';

  @override
  String get safetyMcpDisconnectKeep => 'Seguir conectado';

  @override
  String get safetyStopOrphanBody =>
      'Nada depende de él, pero se pierde lo que aún estaba haciendo. Primero recibe una detención suave y, tras 5 segundos, una forzada.';

  @override
  String get settingsHubGroupNotifications => 'Notificaciones y segundo plano';

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
  String get settingsHubPrivacyRow => 'Privacidad y datos';

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
      'usage cost tokens budget quota limit spent remaining threshold quota monitoring provider plans subscriptions spending alerts';

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
  String get pluginsSectionOnServer => 'Plugins on this server';

  @override
  String get notifySectionWhat => 'Qué me notifica';

  @override
  String get notifyFinishedRuns => 'Trabajo terminado';

  @override
  String get notifyFinishedRunsDetail =>
      'Varias llegan en una sola notificación.';

  @override
  String get notifyRequests => 'Preguntas para ti';

  @override
  String get notifyRequestsDetail => 'Respóndelas desde la notificación.';

  @override
  String get notifyQuotaAlerts => 'Alertas de uso';

  @override
  String get notifyQuotaAlertsDetail => 'Se define en Uso › Alertas.';

  @override
  String get notifyBlockedTitle =>
      'Las notificaciones están desactivadas para esta app';

  @override
  String get notifyBlockedMessage =>
      'Android no deja que OpenCode te avise, así que las preguntas y las conversaciones terminadas no pueden llegarte.';

  @override
  String get notifySendTest => 'Enviar una notificación de prueba';

  @override
  String get notifySendTestDetail => 'Comprueba si Android las deja pasar.';

  @override
  String get notifyQuietDetail => 'En silencio en las horas que elijas.';

  @override
  String get notifySectionBackground => 'Segundo plano';

  @override
  String get notifySectionServers => 'Servidores guardados';

  @override
  String get notifyWifiOnly => 'Revisar servidores solo con Wi-Fi';

  @override
  String get usageSectionSpent => 'Spent';

  @override
  String get usageSectionRemaining => 'Remaining';

  @override
  String get serverSwitcherManage => 'Administrar servidores';

  @override
  String get serverSwitcherOpen => 'Cambiar de servidor';

  @override
  String get discoverSearchGoTo => 'Go to';

  @override
  String discoverSearchIn(String parent) {
    return 'In $parent';
  }

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
      'plans subscriptions remaining quota limit provider plan left reset codex claude glm minimax';

  @override
  String get discoverBudgetAliases =>
      'budget budgets usd token budget spending limit';

  @override
  String get discoverQuotaMonitorAliases =>
      'usage alerts quota monitoring threshold alert warn low quiet hours wi-fi';

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
    return 'Acciones para $name';
  }

  @override
  String get gestureEquivShortcutFindMatch =>
      'Next / previous match while finding in a conversation';

  @override
  String get gestureEquivShortcutPromptHistory =>
      'Earlier / later prompt, with the cursor at the start or end of the message box';

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
  String get nudgeDismiss => 'Hide tip';

  @override
  String get firstRunWhereQuestion => '¿Dónde debería trabajar tu agente?';

  @override
  String get firstRunOnComputer => 'En mi computadora';

  @override
  String get firstRunJustShowMe => 'Solo quiero verlo';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => 'En tu computadora, ejecuta:';

  @override
  String get firstRunPairingNextScan =>
      'Muestra un código. Escanéalo, o cópialo y pégalo aquí.';

  @override
  String get firstRunPairingNextPaste => 'Luego pega el código que imprime.';

  @override
  String get firstRunNotSameNetwork => '¿No estás en la misma red?';

  @override
  String get firstRunShowCommands => 'Mostrar los comandos';

  @override
  String get firstRunCommandsPaseoNetwork =>
      'Para acceder desde este teléfono por tu red privada, escucha en esa dirección y define una contraseña:';

  @override
  String get firstRunCommandsCodexToken =>
      'Crea el token de conexión antes de iniciarlo:';

  @override
  String get firstRunCommandsCodexUsb =>
      'Un teléfono conectado por cable USB accede con:';

  @override
  String get firstRunNotifyTitle => '¿Avisarte cuando el agente te necesite?';

  @override
  String get firstRunNotifyBody =>
      'Leave the app while the agent works. You get a notification when it needs your answer. Android shows a small ongoing notification while it stays connected.';

  @override
  String get firstRunNotifyAccept => 'Avisarme';

  @override
  String get firstRunNotifyDecline => 'Ahora no';

  @override
  String get localAgentTitle => 'Claude Code en este teléfono';

  @override
  String get localAgentOfferBody =>
      'Ejecuta Claude Code aquí, sin computadora. La app instala Node.js, el daemon de Paseo y Claude Code en el Ubuntu que ya gestiona, y solo se accede a ellos desde este teléfono.';

  @override
  String get localAgentOfferSize =>
      'Unos 60 MB de descarga para Node.js, más los paquetes; unos 1 GB una vez instalado. Necesita 2 GB libres.';

  @override
  String get localAgentOfferWarning =>
      'Android puede detener Termux en segundo plano. Los ajustes de batería que mantienen activo el servidor de OpenCode también mantienen esto activo.';

  @override
  String get localAgentSetUp => 'Configurar Claude Code';

  @override
  String get localAgentNotNow => 'Ahora no';

  @override
  String get localAgentNeedsUbuntuBody =>
      'Claude Code se ejecuta dentro del Ubuntu que configura esta app. Termina primero la configuración de En este teléfono y vuelve aquí.';

  @override
  String get localAgentOpenSetup => 'Abrir configuración del teléfono';

  @override
  String get localAgentNeedsTermuxBody =>
      'Claude Code necesita Termux por ahora; el Linux integrado en la app aún no lo ejecuta. Configura Termux para usarlo aquí.';

  @override
  String get localAgentSetUpWithTermux => 'Configurar con Termux';

  @override
  String get localAgentStepNode => 'Node.js';

  @override
  String get localAgentStepPaseo => 'Daemon de Paseo';

  @override
  String get localAgentStepClaude => 'Claude Code';

  @override
  String get localAgentStepSignIn => 'Iniciar sesión en Claude';

  @override
  String get localAgentStepStart => 'Iniciar en este teléfono';

  @override
  String get localAgentInstalling => 'Configurando Claude Code';

  @override
  String get localAgentLeaveNote =>
      'Puedes salir de esta pantalla. La instalación continúa en Termux.';

  @override
  String get localAgentSignInBody =>
      'Se abre Termux y Claude Code muestra un enlace. Apruébalo en tu navegador, pega el código en Termux y vuelve aquí. Esta app nunca ve ni guarda tu inicio de sesión de Claude.';

  @override
  String get localAgentSignInAlready => 'Ya inicié sesión';

  @override
  String localAgentSignInOpenFailed(String command) {
    return 'No se pudo abrir Termux. Ábrelo tú y ejecuta: $command';
  }

  @override
  String get localAgentSignInMissing =>
      'Aún no se encontró ningún inicio de sesión de Claude.';

  @override
  String get localAgentReadyTitle =>
      'Claude Code se está ejecutando en este teléfono';

  @override
  String get localAgentReadyBody =>
      'Solo escucha en este teléfono (127.0.0.1), protegido por una contraseña que guarda esta app.';

  @override
  String localAgentVersions(String claude, String paseo, String node) {
    return 'Claude Code $claude · Paseo $paseo · Node.js $node';
  }

  @override
  String get localAgentInstalledTitle =>
      'Claude Code está instalado y detenido';

  @override
  String get localAgentKilled =>
      'Android detuvo Claude Code mientras la app estaba en segundo plano. No se perdió nada.';

  @override
  String get localAgentFailedTitle =>
      'La configuración de Claude Code se detuvo';

  @override
  String localAgentFailedNoSpace(String detail) {
    return 'No hay espacio suficiente en este teléfono. $detail Libera espacio e inténtalo de nuevo.';
  }

  @override
  String get localAgentFailedDownload =>
      'No se pudo descargar Node.js. Revisa la red e inténtalo de nuevo.';

  @override
  String get localAgentFailedChecksum =>
      'La descarga de Node.js no coincidió con su suma de verificación fijada, así que no se instaló nada. Inténtalo de nuevo; si pasa dos veces, algo en la red está modificando el archivo.';

  @override
  String get localAgentFailedNativeBuild =>
      'Un paquete necesita un módulo nativo sin compilación lista para este teléfono. No se compiló nada; la salida de abajo lo nombra.';

  @override
  String get localAgentFailedPackages =>
      'No se pudieron instalar los paquetes. Revisa la red e inténtalo de nuevo.';

  @override
  String get localAgentFailedPortInUse =>
      'El puerto 6767 de este teléfono ya lo usa otro programa. Detén ese programa e inténtalo de nuevo.';

  @override
  String get localAgentFailedTimeout =>
      'Claude Code no respondió en dos minutos. La salida de abajo muestra lo que imprimió.';

  @override
  String get localAgentFailedInterrupted =>
      'Android detuvo el paso antes de terminar. Inténtalo de nuevo; continúa donde se quedó.';

  @override
  String get localAgentFailedUnsupported =>
      'Claude Code necesita un teléfono de 64 bits.';

  @override
  String get localAgentFailedDaemon =>
      'Claude Code se detuvo o no responde. Vuelve a iniciarlo.';

  @override
  String localAgentFailedReason(String detail) {
    return 'Se detuvo: $detail';
  }

  @override
  String get localAgentProjectTitle => 'Elegir una carpeta de proyecto';

  @override
  String get localAgentProjectBody =>
      'Claude Code trabaja dentro de una carpeta del Ubuntu de este teléfono.';

  @override
  String get localAgentProjectPathLabel =>
      'O escribe una ruta dentro de Ubuntu';

  @override
  String get localAgentProjectPathInvalid =>
      'Introduce una ruta completa, como /root/projects/my-app.';

  @override
  String localAgentConnectFailed(String detail) {
    return 'No se pudo conectar con Claude Code en este teléfono. $detail';
  }

  @override
  String localAgentCardActionFailed(String detail) {
    return 'Eso no funcionó. $detail';
  }

  @override
  String get localAgentRestartTitle =>
      '¿Reiniciar Claude Code en este teléfono?';

  @override
  String get localAgentRestartBody =>
      'Claude Code no estará disponible un momento y se interrumpe lo que esté haciendo ahora. Tus conversaciones se quedan en el teléfono.';

  @override
  String get localAgentStopTitle => '¿Detener Claude Code en este teléfono?';

  @override
  String get localAgentStopBody =>
      'Se interrumpe lo que Claude Code esté haciendo en este teléfono y esta app se desconecta de él. Tus proyectos, conversaciones y el inicio de sesión de Claude se conservan; vuelve a iniciarlo para continuar.';

  @override
  String get localAgentRemove => 'Quitar de este teléfono';

  @override
  String get localAgentRemoveTitle => '¿Quitar Claude Code de este teléfono?';

  @override
  String get localAgentRemoveBody =>
      'Lo detiene y elimina Node.js, Paseo y Claude Code de Ubuntu, unos 1 GB. Tus proyectos y tu inicio de sesión de Claude se conservan.';

  @override
  String get localAgentRemoveKeep => 'Conservarlo';

  @override
  String get localAgentMore => 'Más acciones de Claude Code';

  @override
  String get firstRunAgentsSideBySide =>
      'Se ejecutan en paralelo en la misma computadora. Empieza con uno y agrega los demás cuando quieras desde el nombre del servidor, arriba.';

  @override
  String get firstRunPaseoTitle => 'Agentes a través de Paseo';

  @override
  String get agentErrorConnectionDropped =>
      'Se perdió la conexión con el modelo.';

  @override
  String get agentErrorConnectionDroppedHint =>
      'Revisa el internet del dispositivo donde se ejecuta OpenCode. Si la respuesta se detuvo, revisa la conversación antes de enviar de nuevo.';

  @override
  String get agentErrorTimedOut => 'El modelo tardó demasiado en responder.';

  @override
  String get agentErrorTimedOutHint =>
      'Si la respuesta se detuvo, prueba con una solicitud más pequeña u otro modelo.';

  @override
  String get agentErrorProviderUnreachable =>
      'El servidor no pudo contactar al proveedor del modelo.';

  @override
  String get agentErrorProviderUnreachableHint =>
      'Revisa la conexión a internet de la computadora donde se ejecuta OpenCode y vuelve a intentarlo.';

  @override
  String get agentErrorRequestTooLarge =>
      'Esta solicitud era demasiado grande para enviarla al modelo.';

  @override
  String get agentErrorRequestTooLargeHint =>
      'Envía menos adjuntos o más pequeños, o compacta la conversación, y vuelve a intentarlo.';

  @override
  String get agentErrorRateLimited =>
      'El proveedor del modelo limita la velocidad a la que puedes enviar.';

  @override
  String get agentErrorRateLimitedHint =>
      'Espera un poco. Si la respuesta se detuvo, inténtalo de nuevo o elige otro modelo.';

  @override
  String get agentErrorProviderBusy =>
      'El proveedor del modelo está sobrecargado ahora mismo.';

  @override
  String get agentErrorProviderBusyHint =>
      'Si la respuesta se detuvo, inténtalo más tarde o elige otro modelo.';

  @override
  String get agentErrorOutOfCredit =>
      'La cuenta del proveedor se quedó sin crédito o cuota.';

  @override
  String get agentErrorOutOfCreditHint =>
      'Recarga la cuenta o cambia a otro proveedor o modelo.';

  @override
  String get agentErrorUsageLimit =>
      'This model\'s usage limit is reached for now.';

  @override
  String get agentErrorUsageLimitHint =>
      'Choose another model to keep going, or continue after the limit resets.';

  @override
  String get agentErrorServerDiskFull =>
      'La computadora donde se ejecuta OpenCode se quedó sin espacio en disco.';

  @override
  String get agentErrorServerDiskFullHint =>
      'Libera espacio allí y vuelve a intentarlo.';

  @override
  String get agentErrorRecovered => 'El agente continuó después de esto.';

  @override
  String chatUiRetryingSoon(String attempt) {
    return 'Reintentando$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return 'Reintentando$attempt en $time';
  }

  @override
  String get chatUiCompactAgain => 'Compactar de nuevo';

  @override
  String get chatUiCompactionFailedHint =>
      'La conversación sigue siendo demasiado larga para el modelo, así que el próximo turno lo intentará de nuevo.';

  @override
  String get chatStripContextPending => 'Contexto pendiente';

  @override
  String get chatStripAutoApprove => 'Aprobación automática';

  @override
  String get chatChipAutoApproveOn => 'Auto-approve on';

  @override
  String get chatStripAutoApprovePaused => 'Aprobación automática en pausa';

  @override
  String get approvalModeMenuLabel => 'Modo de aprobación';

  @override
  String get approvalModeAskTitle => 'Preguntar primero';

  @override
  String get approvalModeAutoTitle =>
      'Aprobar automáticamente esta conversación';

  @override
  String get approvalModeAutoDetail => 'Se permiten una vez, según llegan.';

  @override
  String get approvalModeEverythingTitle => 'Aprobar todo';

  @override
  String get approvalModeEverythingDetail =>
      'Todas las conversaciones de este servidor.';

  @override
  String approvalModeEverythingAgentDetail(String agent) {
    return 'Todas las conversaciones de $agent en este teléfono.';
  }

  @override
  String get approvalModeSettings => 'Ajustes de aprobación…';

  @override
  String get approvalModeConfirmEverythingTitle => '¿Aprobar todo?';

  @override
  String get approvalModeConfirmEverythingAction => 'Aprobar todo';

  @override
  String get approvalModeNowAsk => 'Esta conversación pregunta primero.';

  @override
  String get approvalModeNowAuto =>
      'Esta conversación aprueba automáticamente.';

  @override
  String get approvalModeNowEverything => 'Aprobando todo en este servidor.';

  @override
  String get approvalModeChange => 'Cambiar el modo de aprobación';

  @override
  String get chatStripBackground => 'Segundo plano';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return 'Contenido de $name, leído en el teléfono: solo valores y texto, sin formato, gráficos ni fórmulas.';
  }

  @override
  String get chatAttachmentOfficeTruncated =>
      'El archivo era más grande de lo que cabe en un mensaje, así que se corta más abajo.';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return 'No se pudo leer $name. Puede estar protegido con contraseña, dañado o en el formato antiguo de Excel o Word; guárdalo como .xlsx, .docx o CSV e inténtalo de nuevo.';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return '$name no tiene nada que adjuntar.';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return '$name adjuntado como texto.';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets hojas',
      one: '1 hoja',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows filas',
      one: '1 fila',
    );
    return '$name adjuntado como texto · $_temp0, $_temp1';
  }

  @override
  String get localAgentUpdate => 'Actualizar Claude Code';

  @override
  String get localAgentUpdateAvailable =>
      'Una actualización añade los modelos de Claude más recientes, como Opus 5.5.';

  @override
  String get localAgentUpdateNow => 'Actualizar';

  @override
  String get modelNewBadge => 'Nuevo';

  @override
  String get modelEffortNone => 'Sin razonamiento';

  @override
  String get modelEffortMinimal => 'Mínimo';

  @override
  String get modelEffortLow => 'Bajo';

  @override
  String get modelEffortMedium => 'Medio';

  @override
  String get modelEffortHigh => 'Alto';

  @override
  String get modelEffortExtraHigh => 'Muy alto';

  @override
  String get modelEffortMax => 'Máximo';

  @override
  String get setupStoppedByRestart =>
      'El teléfono se reinició, así que el servidor local se detuvo. Vuelve a iniciarlo cuando lo necesites.';

  @override
  String get phoneServerStoppedTitle =>
      'El servidor de este teléfono está detenido';

  @override
  String get phoneServerStoppedBody =>
      'Se detiene cuando el teléfono se reinicia o Android cierra Termux para ahorrar batería. Tus conversaciones se conservan; inícialo de nuevo para continuar.';

  @override
  String get phoneServerStartAndConnect => 'Iniciar y conectar';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count en curso',
      one: '1 en curso',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return 'Pregunta a $agent…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar $countString líneas anteriores',
      one: 'Mostrar 1 línea anterior',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Abrir las $countString líneas';
  }

  @override
  String get setupProgressViewOverallLabel => 'Progreso de la configuración';

  @override
  String get setupProgressViewGettingStarted => 'Empezando…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '~$minutes min restantes',
      one: '~1 min restante',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => 'Menos de un minuto';

  @override
  String get setupProgressViewDone => 'Todo listo';

  @override
  String get setupProgressViewFailedTitle => 'La configuración no terminó';

  @override
  String get setupProgressViewInterrupted =>
      'La configuración se interrumpió. Lo ya terminado se conserva.';

  @override
  String get setupProgressViewCancelled =>
      'Configuración detenida. Lo ya terminado sigue instalado.';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done de $total';
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
  String get setupProgressViewChecking => 'Comprobando';

  @override
  String get setupProgressViewStarting => 'Iniciando';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return 'Se detuvo durante: $stage. Lo que falló está en Detalles.';
  }

  @override
  String get setupProgressViewFailedUnknown =>
      'La configuración se detuvo antes de terminar. Lo que falló está en Detalles.';

  @override
  String get setupProgressViewNoInternet =>
      'Sin conexión a internet — Continúa cuando vuelvas a estar en línea';

  @override
  String get setupProgressViewContinue => 'Continuar la configuración';

  @override
  String get setupProgressViewCancel => 'Detener la configuración';

  @override
  String get setupProgressViewNoLog => 'Aún no hay nada registrado.';

  @override
  String get phoneSetupProgressTitle =>
      'Configurando OpenCode en este teléfono';

  @override
  String get phoneSetupProgressStopTitle => '¿Detener la configuración?';

  @override
  String get phoneSetupProgressStopMessage =>
      'Lo que ya terminó se queda instalado.';

  @override
  String get phoneSetupProgressStopConfirm => 'Detener la configuración';

  @override
  String get phoneSetupProgressKeepGoing => 'Seguir';

  @override
  String get builtinServerChooseRuntime => 'Which OpenCode';

  @override
  String get builtinServerStopped => 'No se está ejecutando.';

  @override
  String builtinServerStartFailed(String reason) {
    return 'OpenCode no respondió: $reason. Abre el registro para ver por qué.';
  }

  @override
  String get builtinServerExited => 'el servidor se detuvo';

  @override
  String builtinServerTimedOut(int seconds) {
    return 'sin respuesta en $seconds segundos';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return 'No se pudo conectar: $reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return 'Este teléfono, integrado ($runtime)';
  }

  @override
  String get phoneSetupProfileName => 'Este teléfono';

  @override
  String get phoneSetupLinuxTitle => 'Base de Linux';

  @override
  String get phoneSetupLinuxWhy => 'Todo lo demás se ejecuta dentro de ella.';

  @override
  String get phoneSetupEssentialsTitle => 'Git, SSH y certificados';

  @override
  String get phoneSetupEssentialsShort => 'Git y SSH';

  @override
  String get phoneSetupEssentialsWhy =>
      'Los agentes usan Git y SSH para trabajar en tus proyectos.';

  @override
  String get phoneSetupNodeWhy => 'OpenCode se ejecuta sobre Node.js.';

  @override
  String get phoneSetupOpenCodeWhy => 'El agente de programación en sí.';

  @override
  String get phoneSetupStartTitle => 'Iniciar OpenCode';

  @override
  String get phoneSetupStartWhy =>
      'La configuración termina con OpenCode en marcha.';

  @override
  String get phoneSetupStageDownloadingLinux => 'Descargando la base de Linux';

  @override
  String get phoneSetupStageUnpackingLinux =>
      'Descomprimiendo la base de Linux';

  @override
  String get phoneSetupStageStarting => 'Iniciando OpenCode';

  @override
  String get phoneSetupNotificationChannel => 'Configuración del teléfono';

  @override
  String get phoneSetupNotificationTitle =>
      'Configurando OpenCode en este teléfono';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return '$percent% completado';
  }

  @override
  String get phoneSetupNotificationDone =>
      'OpenCode está listo en este teléfono';

  @override
  String get phoneSetupNotificationStopped =>
      'La configuración se detuvo. Abre la app para continuar.';

  @override
  String get phoneSetupErrorNoInternet =>
      'Sin conexión a internet. Continúa cuando vuelvas a estar en línea.';

  @override
  String phoneSetupErrorOffline(String name) {
    return 'No se pudo descargar $name: sin conexión a internet';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return 'No se pudo instalar $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return 'La descarga de $name se dañó. Continúa para descargarla de nuevo.';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'OpenCode se descargó, pero su programa no estaba en la descarga. Continúa para descargarlo de nuevo.';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'OpenCode se descargó, pero su programa no se ejecuta en este teléfono. Los detalles muestran lo que dijo.';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'OpenCode se instaló, pero no se inició. Continúa para intentarlo de nuevo; los detalles muestran lo que dijo.';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return 'No hay espacio libre suficiente para instalar $name. Libera almacenamiento y continúa la configuración.';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'OpenCode está instalado pero no responde. Continúa la configuración para intentarlo de nuevo. Los detalles muestran qué lo detuvo.';

  @override
  String get builtinServerLogTitle => 'Registro del servidor';

  @override
  String get phoneSetupReadyTitle => 'OpenCode está listo';

  @override
  String get phoneSetupReadyNameTitle => 'Ponle nombre a tu primer proyecto';

  @override
  String get phoneSetupReadyNameLabel => 'Nombre del proyecto';

  @override
  String get phoneSetupReadyNameHelp => 'Letras, números, - _ .';

  @override
  String get phoneSetupReadyCreating => 'Creando el proyecto';

  @override
  String get phoneSetupReadyCreateOpen => 'Crear y abrir';

  @override
  String get phoneSetupReadyOpenFolderInstead =>
      'Abrir una carpeta en su lugar';

  @override
  String get phoneSetupReadyNameEmpty => 'Escribe un nombre.';

  @override
  String get phoneSetupReadyNameOneFolder => 'Usa un solo nombre, sin barras.';

  @override
  String get phoneSetupReadyNameInvalid =>
      'Usa letras, números, - _ o . y empieza por una letra o un número (hasta 64).';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return 'No se pudo crear el proyecto: $reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return 'No se pudo abrir el proyecto: $reason';
  }

  @override
  String get phoneServerCardTitle => 'Ubuntu en la app';

  @override
  String get phoneServerCardRunning => 'En ejecución';

  @override
  String get phoneServerCardStopped => 'Detenido';

  @override
  String get phoneServerCardStarting => 'Iniciando';

  @override
  String get phoneServerCardStopping => 'Deteniendo';

  @override
  String get phoneServerCardRemoving => 'Quitando';

  @override
  String get phoneServerCardChecking => 'Comprobando';

  @override
  String get phoneServerCardNotSetUp => 'Sin configurar';

  @override
  String get phoneServerCardSettingUp => 'Configurando';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => 'Configurar';

  @override
  String get phoneServerCardShowProgress => 'Ver progreso';

  @override
  String get phoneServerCardContinueSetup => 'Continuar la configuración';

  @override
  String get phoneServerCardLogTitle => 'Registro';

  @override
  String get phoneServerCardLogEmpty => 'Aún no hay nada en el registro.';

  @override
  String get phoneServerCardMore => 'Más';

  @override
  String get phoneServerCardAddTools =>
      'Agregar herramientas (Python, AI Team…)';

  @override
  String get phoneServerCardUpdate => 'Actualizar OpenCode';

  @override
  String get phoneServerCardRemove => 'Quitar de este teléfono…';

  @override
  String get phoneServerCardRemoveTitle => '¿Quitar OpenCode de este teléfono?';

  @override
  String get serverEditorMoreOptions => 'Más opciones';

  @override
  String get inAppServerStoppedTitle =>
      'OpenCode dentro de la app está detenido';

  @override
  String get inAppServerStoppedBody =>
      'Se detiene cuando la app se cierra un tiempo o se actualiza. Tus conversaciones se conservan; inícialo de nuevo para continuar.';

  @override
  String get inAppServerNotRespondingTitle =>
      'OpenCode dentro de la app no responde';

  @override
  String get inAppServerNotRespondingBody =>
      'Iniciarlo de nuevo suele solucionarlo. Tus conversaciones se conservan.';

  @override
  String get inAppServerStartFailedTitle =>
      'OpenCode dentro de la app no se inició';

  @override
  String get inAppServerStartFailedBody =>
      'Abre su configuración para ver el registro del servidor, o intenta iniciarlo de nuevo.';

  @override
  String get inAppServerStarting => 'Iniciando OpenCode dentro de la app…';

  @override
  String get inAppServerStartingBody => 'Tarda unos segundos.';

  @override
  String get inAppServerOpenSetup => 'Abrir configuración';

  @override
  String get projectFolderNewProject => 'Proyecto nuevo';

  @override
  String get projectFolderProjectNameLabel => 'Nombre del proyecto';

  @override
  String get projectFolderEnterPath => 'Introducir una ruta';

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
  String get folderBrowserUp => 'Subir una carpeta';

  @override
  String folderBrowserCurrent(String path) {
    return 'Carpeta actual: $path';
  }

  @override
  String folderBrowserOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String get folderBrowserGit => 'Repositorio Git';

  @override
  String get folderBrowserProject => 'Proyecto de OpenCode';

  @override
  String folderBrowserShowInside(String name) {
    return 'Mostrar las carpetas de $name';
  }

  @override
  String get folderBrowserProjectsHere =>
      'Tus proyectos están aquí. Toca uno para abrirlo.';

  @override
  String get folderBrowserHomeHere =>
      'La carpeta personal y la raíz no pueden ser proyectos. Abre una carpeta dentro.';

  @override
  String get folderBrowserNoProjectsTitle => 'Aún no hay proyectos';

  @override
  String get folderBrowserNoProjectsBody => 'Empieza uno para crearlo aquí.';

  @override
  String get folderBrowserEmptyTitle => 'No hay carpetas aquí';

  @override
  String get folderBrowserEmptyBody =>
      'Ábrela como proyecto o sube una carpeta.';

  @override
  String get folderBrowserErrorTitle => 'No se puede mostrar esta carpeta';

  @override
  String get folderBrowserErrorNotInstalled =>
      'Ubuntu aún no está instalado en la app.';

  @override
  String get folderBrowserErrorMissing => 'Ya no existe.';

  @override
  String get folderBrowserErrorDenied => 'La app no tiene permiso para leerla.';

  @override
  String get folderBrowserErrorLinked => 'Es un enlace. Introduce su ruta.';

  @override
  String get folderBrowserErrorFailed =>
      'Inténtalo de nuevo o introduce su ruta.';

  @override
  String get folderBrowserErrorTimedOut =>
      'Tardó demasiado en responder. Inténtalo de nuevo.';

  @override
  String get folderBrowserRetry => 'Intentar de nuevo';

  @override
  String get phoneSetupStartScreenTitle => 'En este teléfono';

  @override
  String get phoneSetupStartHeadline =>
      'Ejecuta un agente de programación aquí mismo';

  @override
  String phoneSetupStartPromise(String size) {
    return 'Sin computadora ni otras apps. Unos $size de descarga la primera vez.';
  }

  @override
  String get phoneSetupStartPromiseNoSize => 'Sin computadora ni otras apps.';

  @override
  String phoneSetupStartSteps(String time) {
    return 'Instalar, nombrar un proyecto, empezar una conversación · $time';
  }

  @override
  String phoneSetupStartStepsTime(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'aprox. $minutes min',
      one: 'aprox. 1 min',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Aproximadamente $minutes minutos',
      one: 'Aproximadamente un minuto',
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
  String get phoneSetupStartSetUp => 'Configurar OpenCode en este teléfono';

  @override
  String phoneSetupStartIncludes(String tools) {
    return 'Incluye $tools.';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first y $last';
  }

  @override
  String get phoneSetupStartListSeparator => ', ';

  @override
  String get phoneSetupStartCustomize => 'Elegir qué instalar';

  @override
  String get phoneSetupStartOtherWays => 'Otras formas';

  @override
  String get phoneSetupStartUseTermux => 'Usar Termux en su lugar';

  @override
  String get phoneSetupStartByAddress =>
      'Conectar a una computadora por dirección';

  @override
  String get phoneSetupStartSetUpHere => 'Configurarlo en esta app en su lugar';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return 'La configuración va al $percent%';
  }

  @override
  String get phoneSetupStartRunningBody =>
      'Sigue avanzando mientras usas otras apps.';

  @override
  String get phoneSetupStartStoppedBody =>
      'Se detuvo antes de terminar. Al continuar se retoma desde donde se quedó.';

  @override
  String get phoneSetupStartContinue => 'Continuar la configuración';

  @override
  String get phoneSetupStartReadyHeadline =>
      'OpenCode está listo en este teléfono';

  @override
  String get phoneSetupStartReadyBody =>
      'Ábrelo para empezar una conversación.';

  @override
  String get phoneSetupStartOpen => 'Abrir';

  @override
  String get phoneSetupStartTermuxHeadline =>
      'OpenCode ya está configurado en Termux';

  @override
  String get phoneSetupStartTermuxBody =>
      'Ya lo configuraste antes con Termux. Conéctate para seguir usándolo.';

  @override
  String get phoneSetupStartConnect => 'Conectar';

  @override
  String phoneSetupStartFailed(String reason) {
    return 'No funcionó: $reason';
  }

  @override
  String get phoneSetupStartEntryDetail =>
      'Ejecuta un agente de programación aquí mismo. No necesitas computadora.';

  @override
  String get phoneSetupStartCustomizeTitle => 'Elegir qué instalar';

  @override
  String get phoneSetupStartAddTitle => 'Agregar herramientas';

  @override
  String get phoneSetupStartInstalled => 'Instalado';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => 'Aún no has elegido nada';

  @override
  String get phoneSetupStartDone => 'Listo';

  @override
  String get phoneSetupStartAdd => 'Agregar';

  @override
  String get phoneSetupStartChecking => 'Comprobando lo que está instalado…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline =>
      'Este teléfono no puede ejecutarlo';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return 'El Ubuntu de esta app solo funciona en teléfonos Arm o Intel de 64 bits; este informa $abi.';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline =>
      'Este teléfono no tiene suficiente memoria';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'OpenCode necesita un teléfono con al menos $minimumString MB de memoria; este tiene $actualString MB. Ejecútalo en una computadora y conecta este teléfono a ella.';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return 'Puede ir lento en este teléfono, que tiene $memoryString MB de memoria.';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline =>
      'No hay suficiente espacio libre';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return 'Libera unos $size en este teléfono y vuelve para configurarlo.';
  }

  @override
  String get phoneSetupPreflightOpenStorage =>
      'Abrir ajustes de almacenamiento';

  @override
  String get phoneSetupOpenWelcomeShowProgress => 'Ver progreso';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => 'Crea una página web sencilla';

  @override
  String get chatStartPythonScript => 'Escribe un script de Python que…';

  @override
  String get chatStartNodeProject => 'Crea un proyecto de Node.js';

  @override
  String get chatStartReadme => 'Prepara un README';

  @override
  String get chatStartExplainProject => 'Explica este proyecto';

  @override
  String get chatStartWhatChanged => '¿Qué cambió recientemente?';

  @override
  String get chatStartFindBug => 'Encuentra y corrige un error';

  @override
  String get chatStartAddTests => 'Agrega pruebas';

  @override
  String get chatStartListFolder => 'Muestra lo que hay en esta carpeta';

  @override
  String get chatStartEmptyFolder => 'Carpeta vacía';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count elementos',
      one: '1 elemento',
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
      other: '$count cambios',
      one: '1 cambio',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => 'Mirando la carpeta…';

  @override
  String get chatStartServerFolder => 'Carpeta del servidor';

  @override
  String get chatStartTip =>
      'Escribe / para ver los comandos · mantén pulsado un mensaje para ver sus acciones';

  @override
  String get chatLoadingConversation => 'Cargando la conversación';

  @override
  String get chatLoadFailedTitle => 'No se pudo abrir esta conversación';

  @override
  String get chatLoadFailedBody =>
      'No se perdió nada. Inténtalo de nuevo cuando OpenCode responda.';

  @override
  String get chatSendFailed => 'Tu mensaje no se envió';

  @override
  String get chatSendFailedKept => 'Volvió al cuadro de mensaje.';

  @override
  String get chatStartSuggestionsLabel => 'Formas de empezar';

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
    return 'Descargando AI Team · $index de $total';
  }

  @override
  String get aiteamComponentStagePreparing => 'Preparando AI Team';

  @override
  String aiteamComponentAddingTitle(String names) {
    return 'Agregando $names';
  }

  @override
  String get aiteamComponentNotice =>
      'OpenCode y AI Team se están ejecutando en este teléfono';

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
  String workOpenLiveConversation(String title) {
    return 'Open “$title”';
  }

  @override
  String get workLoadingLabel => 'Cargando';

  @override
  String agentNotAnsweringPhone(String agent) {
    return '$agent en este teléfono no responde';
  }

  @override
  String get workServerNotAnsweringPhone =>
      'OpenCode en este teléfono no responde';

  @override
  String workServerNotAnswering(String server) {
    return '$server no responde';
  }

  @override
  String get workServerKeepsTrying =>
      'La app sigue intentándolo en segundo plano.';

  @override
  String get workServerRestart => 'Reiniciar';

  @override
  String get connectionRestartAgent => 'Restart Claude Code';

  @override
  String get connectionRestartPhoneServer => 'Restart OpenCode';

  @override
  String get workServerRestartTitle => '¿Reiniciar OpenCode en este teléfono?';

  @override
  String get workServerRestartBody =>
      'Se detendrá el turno del agente en curso. Tus conversaciones se conservan.';

  @override
  String workRunaway(String duration) {
    return 'OpenCode lleva $duration ocupado sin nada que hacer';
  }

  @override
  String workRunawayInProject(String project, String duration) {
    return 'OpenCode lleva $duration ocupado en $project sin nada que hacer';
  }

  @override
  String get connectStartingPhone => 'Iniciando OpenCode en este teléfono…';

  @override
  String get connectStartingBody =>
      'Tus conversaciones se conservan. Esto puede tardar un minuto.';

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
  String get serverRowConnected => 'Conectado';

  @override
  String phoneServerRowStatus(String runtime, String state) {
    return '$runtime · $state';
  }

  @override
  String get phoneServerRowRestarting => 'Reiniciando';

  @override
  String get phoneServerRowNotAnswering => 'No responde';

  @override
  String get phoneServerRowNotRunning => 'No está en ejecución';

  @override
  String get phoneServerRowDetails => 'Detalles';

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
  String get localTerminalSourcePhone => 'Linux integrado';

  @override
  String get localTerminalSourceServer => 'Servidor de OpenCode';

  @override
  String localTerminalShellName(int number) {
    return 'Shell $number';
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
  String get localTerminalStarting => 'Iniciando el shell';

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
  String get localTerminalFailedTitle => 'El shell no arrancó';

  @override
  String get localTerminalFailedBody =>
      'Inténtalo de nuevo. Si sigue fallando, Detalles explica por qué.';

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
  String get localTerminalKeysLabel => 'Teclas de la terminal';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => 'Página arriba';

  @override
  String get localTerminalKeyPageDown => 'Página abajo';

  @override
  String get localTerminalSemantics =>
      'Terminal en este teléfono. Toca para escribir; mantén pulsado para seleccionar texto.';

  @override
  String get phoneServerTermuxTitle => 'Termux';

  @override
  String get globalSessionsLoadFailedTitle => 'Couldn\'t load conversations';

  @override
  String get filesLoadFailedTitle => 'No se pudo abrir esta carpeta';

  @override
  String get filesSymbolsFailedTitle => 'No se pudieron buscar los símbolos';

  @override
  String get terminalListFailedTitle => 'No se pudieron listar los terminales';

  @override
  String get addServerConnectTo => 'Conectar con';

  @override
  String get addServerTypeOpenCode => 'OpenCode en una computadora';

  @override
  String get addServerTypeOpenCodeDetail =>
      'Empareja con un código o introduce su dirección';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail =>
      'El app-server de Codex en tu computadora';

  @override
  String get addServerTypePaseo => 'Claude Code o Pi';

  @override
  String get addServerTypePaseoDetail => 'A través de Paseo en tu computadora';

  @override
  String get addServerScan => 'Escanear código';

  @override
  String get addServerPaste => 'Pegar código';

  @override
  String get addServerManual => 'Ingresar la dirección';

  @override
  String get addServerChecking => 'Comprobando la conexión…';

  @override
  String addServerCheckingHost(String host) {
    return 'Comprobando $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return 'Conectando a $host…';
  }

  @override
  String get addServerSaveAnyway => 'Guardar de todos modos';

  @override
  String get effectsSection => 'Efectos';

  @override
  String get effectsAnimations => 'Movimiento';

  @override
  String get effectsMotionFull => 'Completa';

  @override
  String get effectsMotionFullHint =>
      'Los dibujos se mueven, las pantallas de espera respiran y los momentos terminados se celebran';

  @override
  String get effectsMotionCalm => 'Tranquila';

  @override
  String get effectsMotionCalmHint =>
      'Los dibujos aparecen, nada se mueve de forma continua y nada celebra';

  @override
  String get effectsMotionOff => 'Desactivada';

  @override
  String get effectsMotionOffHint => 'Todo aparece de inmediato';

  @override
  String get effectsMotionSystemOff =>
      'La opción Quitar animaciones de tu teléfono está activada, así que nada se mueve elijas lo que elijas aquí';

  @override
  String get effectsActivityGlow => 'Borde brillante al responder';

  @override
  String get effectsSaveFailed =>
      'No se pudo guardar esta opción en este dispositivo. Inténtalo de nuevo.';

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
  String get chatWatchEmptyTitle => 'Nothing here yet';

  @override
  String get chatWatchEmptyBody =>
      'This conversation fills in as the agent works.';

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
    return 'OpenCode Mobile se cerró $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'Android cerró OpenCode Mobile $time para liberar memoria';
  }

  @override
  String appExitCrashed(String time) {
    return 'OpenCode Mobile se detuvo de forma inesperada $time';
  }

  @override
  String appExitKilled(String time) {
    return 'Android detuvo OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella; está iniciando de nuevo.';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella; están iniciando de nuevo.';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella. Inícialo de nuevo cuando quieras.';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella. Inícialos de nuevo cuando quieras.';
  }

  @override
  String appExitServerBack(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella y ya está en ejecución de nuevo.';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella; OpenCode ya está en ejecución de nuevo.';
  }

  @override
  String appExitAtTime(String time) {
    return 'a las $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return 'el $day a las $time';
  }

  @override
  String get appExitSwitchToPhone => 'Cambiar a este teléfono';

  @override
  String get appExitKeepRunning => 'Mantener en ejecución';

  @override
  String get keepRunningTitle => 'Mantener en ejecución en segundo plano';

  @override
  String get keepRunningRowSubtitle =>
      'What to allow so your phone doesn\'t close the app';

  @override
  String get keepRunningIntro =>
      'El OpenCode de tu teléfono y el AI Team se ejecutan dentro de esta app, así que se detienen cuando Android la cierra. En este teléfono, permite lo siguiente:';

  @override
  String get keepRunningSwipeWarning =>
      'Este teléfono cierra una app que deslizas fuera de Apps recientes, incluso mientras trabaja. Bloquéala ahí en lugar de deslizarla.';

  @override
  String get keepRunningBatteryTitle => 'No optimizar la batería';

  @override
  String get keepRunningBatteryDetail =>
      'Permite que la app siga en ejecución mientras usas otras apps.';

  @override
  String get keepRunningBatteryDone => 'Permitido';

  @override
  String get keepRunningLockTitle => 'Bloquéala en Apps recientes';

  @override
  String get keepRunningLockNubia =>
      'Abre Apps recientes y desliza la tarjeta de OpenCode Mobile hacia abajo hasta que aparezca el candado.';

  @override
  String get keepRunningLockSamsung =>
      'Abre Apps recientes, toca el ícono de OpenCode Mobile sobre su tarjeta y elige Mantener abierta.';

  @override
  String get keepRunningLockOther =>
      'Abre Apps recientes, mantén pulsada la tarjeta de OpenCode Mobile y toca el candado.';

  @override
  String get keepRunningAutostartTitle => 'Permitir inicio automático';

  @override
  String get keepRunningAutostartDetail =>
      'Actívalo para OpenCode Mobile para que el teléfono no la detenga en segundo plano.';

  @override
  String get keepRunningAutostartHuawei =>
      'En Inicio de apps, configura OpenCode Mobile en Administrar manualmente y activa los tres interruptores.';

  @override
  String get keepRunningBackgroundTitle =>
      'Permitir actividad en segundo plano';

  @override
  String get keepRunningBackgroundXiaomi =>
      'En Información de la app › Ahorro de batería, elige Sin restricciones.';

  @override
  String get keepRunningBackgroundOppo =>
      'En Información de la app › Uso de batería, permite la actividad en segundo plano.';

  @override
  String get keepRunningBackgroundVivo =>
      'En Información de la app › Batería, permite el alto consumo en segundo plano.';

  @override
  String get keepRunningBackgroundSamsung =>
      'En Información de la app › Batería, elige Sin restricciones y mantén la app fuera de Apps en suspensión.';

  @override
  String get keepRunningBackgroundOther =>
      'En Información de la app › Batería, elige Sin restricciones o permite la ejecución en segundo plano.';

  @override
  String get keepRunningOpen => 'Abrir';

  @override
  String get keepRunningOpenFailed =>
      'Este teléfono no tiene esa pantalla. Abre Ajustes › Apps › OpenCode Mobile.';

  @override
  String get keepRunningFootnote =>
      'Si Android sigue cerrando la app, volverá a iniciar el OpenCode de tu teléfono la próxima vez que la abras.';

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
  String get appExitQuietRestarted => 'Restarted after the app closed';

  @override
  String get appExitQuietRestarting => 'Restarting after the app closed';

  @override
  String get thermalQuietPaused =>
      'Phone is hot · AI Team paused until it cools';

  @override
  String get thermalQuietStopped =>
      'Phone is very hot · AI Team stopped until it cools';

  @override
  String get thermalQuietResumed => 'Phone cooled down · AI Team resumed';

  @override
  String get thermalPausedNotice =>
      'Tu teléfono está caliente: se pausó el AI Team para que se enfríe. Se reanuda solo.';

  @override
  String get thermalStoppedNotice =>
      'Tu teléfono está muy caliente: se detuvo el AI Team para protegerlo. Su trabajo se conserva y vuelve a empezar cuando el teléfono se enfríe.';

  @override
  String get thermalResumedNotice =>
      'AI Team reanudado: tu teléfono se ha enfriado.';

  @override
  String get thermalGuardSetting =>
      'Pausar el AI Team cuando el teléfono esté caliente';

  @override
  String get thermalGuardSettingDetail =>
      'El equipo se pausa conservando su trabajo y se reanuda solo cuando el teléfono se enfría.';

  @override
  String get kitSheetClose => 'Cerrar';

  @override
  String get kitSheetDismiss => 'Cerrar';

  @override
  String get kitSheetLoading => 'Cargando';

  @override
  String get kitConfirmCancel => 'Cancelar';

  @override
  String get kitConfirmKeepRunning => 'Mantener en ejecución';

  @override
  String get kitConfirmKeepEditing => 'Seguir editando';

  @override
  String kitConfirmTypeName(String name) {
    return 'Escribe $name para confirmar';
  }

  @override
  String get kitConfirmTypeNameReason =>
      'Escribe el nombre exactamente como se muestra para activarlo.';

  @override
  String get kitConfirmFailed => 'No se completó. Puedes intentarlo de nuevo.';

  @override
  String get kitTryAgain => 'Intentar de nuevo';

  @override
  String get kitDetails => 'Detalles';

  @override
  String get kitDiscardTitle => '¿Descartar tus cambios?';

  @override
  String get kitDiscardBody =>
      'Lo que cambiaste aquí no se ha guardado. Descartarlo no se puede deshacer.';

  @override
  String get kitDiscardConfirm => 'Descartar cambios';

  @override
  String get kitCopied => 'Copiado';

  @override
  String get kitMore => 'Más';

  @override
  String kitChipRemove(String label) {
    return 'Quitar $label';
  }

  @override
  String get kitCopy => 'Copiar';

  @override
  String get kitWorking => 'Trabajando';

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
  String get kitMenu => 'Menú';

  @override
  String get kitCopyDetails => 'Copiar detalles';

  @override
  String get kitReportProblem => 'Informar de un problema';

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

    return 'Paso $stepString de $ofString';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: 'faltan unos $secondsString s',
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
      other: 'faltan unos $minutesString min',
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
      other: 'faltan unas $hoursString h',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong =>
      'Es demasiado largo para un código QR. Copia el enlace.';

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
  String get kitMarkWaiting => 'En espera';

  @override
  String get kitMarkWorking => 'Trabajando';

  @override
  String get kitMarkDone => 'Listo';

  @override
  String get kitMarkFailed => 'Falló';

  @override
  String get kitMarkPaused => 'En pausa';

  @override
  String get kitTaskNeedsYou => 'Te necesita';

  @override
  String get kitTaskStopped => 'Detenido';

  @override
  String get kitSwatchInUse => 'En uso';

  @override
  String get kitThemePreviewTitle => 'Arreglar el error de inicio de sesión';

  @override
  String get kitThemePreviewWorking => 'Trabajando · 2 min';

  @override
  String get kitThemePreviewNeedsYou => 'Te necesita';

  @override
  String get kitThemePreviewPrimary => 'Enviar';

  @override
  String get kitThemePreviewSecondary => 'Adjuntar';

  @override
  String get kitThemePreviewSegment => 'Activado';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => 'Tecla barra diagonal';

  @override
  String get kitTerminalViewKeyDash => 'Tecla guion';

  @override
  String get kitTerminalViewKeyPipe => 'Tecla barra vertical';

  @override
  String get kitTerminalViewKeyTilde => 'Tecla tilde';

  @override
  String get kitTerminalViewKeyHome => 'Tecla Home';

  @override
  String get kitTerminalViewKeyEnd => 'Tecla End';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrando las últimas $countString líneas',
      one: 'Mostrando la última línea',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => 'Deshacer';

  @override
  String kitUndoFailed(String message) {
    return 'No se pudo deshacer. $message';
  }

  @override
  String get kitUndoWorking => 'Deshaciendo';

  @override
  String get kitTermHint => 'Explanation available';

  @override
  String get kitTermShow => 'Show explanation';

  @override
  String get kitTermClose => 'Close explanation';

  @override
  String kitFieldShowNamed(String label) {
    return 'Mostrar $label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return 'Ocultar $label';
  }

  @override
  String get kitFieldPaste => 'Pegar';

  @override
  String get kitFieldSaved => 'Guardado';

  @override
  String get kitFieldReplace => 'Reemplazar';

  @override
  String get kitFieldChecking => 'Comprobando…';

  @override
  String kitFieldStillChecking(int seconds) {
    return 'Aún comprobando tras $seconds s';
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
      other: '$countString de $maxString',
      one: '1 de $maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => 'Límite alcanzado';

  @override
  String get kitFieldErrorLabel => 'Error';

  @override
  String get kitTappableShowActions => 'Mostrar acciones';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return 'leyó $_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count veces',
      one: 'una vez',
    );
    return 'buscó $_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count carpetas',
      one: '1 carpeta',
    );
    return 'listó $_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return 'editó $_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count comandos',
      one: '1 comando',
    );
    return 'ejecutó $_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count páginas',
      one: '1 página',
    );
    return 'obtuvo $_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas',
      one: '1 tarea',
    );
    return 'delegó $_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos más',
      one: '1 paso más',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count no se ejecutaron',
      one: '1 no se ejecutó',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos',
      one: '1 paso',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => 'Esperándote';

  @override
  String get kitWorkStopped => 'Detenido';

  @override
  String get kitWorkDidntFinish => 'No terminó';

  @override
  String get kitWorkWorking => 'Trabajando';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos anteriores',
      one: '1 paso anterior',
    );
    return 'Mostrar $_temp0';
  }

  @override
  String get kitReceiptSending => 'Enviando…';

  @override
  String get kitReceiptSent => 'Enviado';

  @override
  String get kitReceiptConfirmed => 'Hecho';

  @override
  String get kitReceiptNotConfirmed => 'Aún sin confirmar';

  @override
  String get kitReceiptRefused => 'No aceptado';

  @override
  String kitReceiptRefusedReason(String reason) {
    return 'No aceptado: $reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return 'Respondido en $where';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown =>
      'Respondido en otro dispositivo';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act: $reason';
  }

  @override
  String kitReceiptAt(String time) {
    return 'a las $time';
  }

  @override
  String get kitDetailsHide => 'Ocultar detalles';

  @override
  String get kitCopyAll => 'Copiar todo';

  @override
  String kitCopyValue(String label) {
    return 'Copiar $label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar las $count líneas',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label: $value';
  }

  @override
  String get kitProgressRowLoading => 'Cargando';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString por ciento';
  }

  @override
  String get kitProgressRowNearLimit => 'Cerca del límite';

  @override
  String get kitProgressRowAtLimit => 'Límite alcanzado';

  @override
  String kitProgressRowAsOf(String time) {
    return 'a las $time';
  }

  @override
  String get kitProgressRowOther => 'Otros';

  @override
  String get kitModelServerDefault => 'Predeterminado del servidor';

  @override
  String get kitModelSignIn => 'Inicia sesión para usar modelos';

  @override
  String get kitModelChoose => 'Elegir un modelo';

  @override
  String get kitModelChange => 'Cambiar de modelo';

  @override
  String get kitModelActions => 'Atajos de modelo';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String kitAttachmentOpen(String label) {
    return 'Vista previa de $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return 'Imagen, $label';
  }

  @override
  String kitAttachmentFile(String label) {
    return 'Archivo, $label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return 'Carpeta, $label';
  }

  @override
  String kitAttachmentReference(String label) {
    return 'Referencia, $label';
  }

  @override
  String get kitSuggestionsShowAll => 'Mostrar todo';

  @override
  String get kitSuggestionsLabel => 'Sugerencias';

  @override
  String get kitNeedsYouReasonDecision => 'Necesita tu decisión';

  @override
  String get kitNeedsYouReasonBlocked => 'Bloqueado: te necesita';

  @override
  String get kitNeedsYouReasonConsent => 'Necesita tu visto bueno';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString te necesitan · ',
      one: 'Te necesita · ',
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
      other: ', $countString te necesitan',
      one: ', 1 te necesita',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return 'esperando $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'esperando $minutesString minutos',
      one: 'esperando 1 minuto',
      zero: 'esperando menos de un minuto',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$who en $server';
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
  String get kitJumpLatest => 'Ir a lo último';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nuevos · Ir a lo último',
      one: '1 nuevo · Ir a lo último',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => 'Actual';

  @override
  String get kitChoiceRecommended => 'Recomendado';

  @override
  String get kitChoiceOtherSend => 'Enviar respuesta';

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
  String get kitComposerField => 'Mensaje';

  @override
  String get kitComposerSend => 'Enviar';

  @override
  String get kitComposerSending => 'Enviando';

  @override
  String get kitComposerSendOffline => 'Enviar al reconectar';

  @override
  String get kitComposerSendAfter => 'Enviar tras esta respuesta';

  @override
  String get kitComposerAddToTurn => 'Añadir a la respuesta en curso';

  @override
  String get kitComposerStop => 'Detener la respuesta';

  @override
  String get kitComposerSendsAfter => 'Se envía tras esta respuesta';

  @override
  String get kitComposerStopAndSend => 'Detener y enviar';

  @override
  String get kitComposerStopAndSendNote =>
      'Detiene la respuesta actual y vuelve a empezar con tu mensaje.';

  @override
  String get kitComposerCannotSendYet =>
      'Podrás enviar cuando termine esta respuesta';

  @override
  String get kitComposerTools => 'Adjuntar y más';

  @override
  String get kitComposerVoice => 'Hablar en vez de escribir';

  @override
  String get kitComposerEditor => 'Abrir el editor a pantalla completa';

  @override
  String get kitVoiceLeave => 'Salir del modo de voz';

  @override
  String get kitVoiceStarting => 'Preparando el micrófono…';

  @override
  String get kitVoiceListening => 'Escuchando…';

  @override
  String get kitVoiceTranscribing => 'Escribiendo lo que dijiste…';

  @override
  String get kitVoiceWaitingReply => 'Esperando la respuesta…';

  @override
  String get kitVoiceSpeaking => 'Leyendo la respuesta en voz alta';

  @override
  String get kitVoiceReplyReady => 'La respuesta está lista';

  @override
  String get kitVoicePaused => 'En pausa · el agente te necesita';

  @override
  String get kitVoiceMicDenied => 'El micrófono está desactivado para esta app';

  @override
  String get kitVoiceFailed => 'La voz se detuvo';

  @override
  String get kitVoiceSend => 'Enviar';

  @override
  String get kitVoiceDone => 'Listo';

  @override
  String get kitVoiceStopReading => 'Dejar de leer';

  @override
  String get kitVoiceReadReply => 'Leerla en voz alta';

  @override
  String get kitVoiceListen => 'Escuchar';

  @override
  String get kitVoiceReadAloud => 'Leer las respuestas en voz alta';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => 'Borrar búsqueda';

  @override
  String get kitSearchFilter => 'Filtrar';

  @override
  String kitSearchFilterActive(String name) {
    return 'Filtro: $name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count resultados',
      one: '1 resultado',
      zero: 'Sin resultados',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return '$count cargados · buscando en el servidor…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return 'Nada coincide con $query';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return 'Nada en $what coincide con $query';
  }

  @override
  String get kitTopBarBack => 'Atrás';

  @override
  String get kitErrorFallbackMessage =>
      'This part of OpenCode hit an error. Go back and try again, or open App diagnostics for details.';

  @override
  String get kitTopBarClose => 'Cerrar';

  @override
  String get kitTopBarSearch => 'Buscar';

  @override
  String get kitTopBarSwitchServer => 'Cambiar de servidor';

  @override
  String get kitTopBarSwitchProject => 'Cambiar de proyecto';

  @override
  String get kitTopBarMore => 'Más acciones';

  @override
  String get kitAgentStripLabel => 'Agentes en esta tarea';

  @override
  String kitAgentOpen(String name) {
    return 'Abrir la conversación de $name';
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
      other: 'Mostrar las $count líneas',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => 'Open full output';

  @override
  String get kitWrapLines => 'Ajustar líneas';

  @override
  String kitCodeChanges(int added, int removed) {
    return '$added agregadas, $removed quitadas';
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
    return 'En espera de envío · $count';
  }

  @override
  String get kitQueuedOffline => 'Se envía cuando vuelvas a estar en línea';

  @override
  String get kitQueuedWaiting => 'En espera de envío';

  @override
  String get kitQueuedReachedServer => 'Llegó al servidor';

  @override
  String get kitQueuedAfterReply => 'Se envía después de esta respuesta';

  @override
  String get kitQueuedAddToTurn => 'Se agrega a este turno';

  @override
  String get kitQueuedUpdate => 'Actualización en espera';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count adjuntos',
      one: '1 adjunto',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return 'Mensaje en espera $index de $count: $text. $state';
  }

  @override
  String get kitQueuedActions => 'Acciones del mensaje';

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
  String get kitUntilOff => 'Hasta que lo desactive';

  @override
  String get kitUntilConversation => 'Durante esta conversación';

  @override
  String get kitUntilHour => 'Durante una hora';

  @override
  String get kitRiskTurnOn => 'Activar';

  @override
  String get kitRiskNotNow => 'Ahora no';

  @override
  String get kitRiskTurnOff => 'Desactivar';

  @override
  String get safetyDisconnectBody =>
      'Las actualizaciones en vivo se detienen y vuelves a la lista de servidores. El servidor sigue en marcha y nada en él cambia.';

  @override
  String get safetyDisconnectBodyPhone =>
      'Las actualizaciones en vivo se detienen y vuelves a la lista de servidores. OpenCode sigue ejecutándose en este teléfono, gastando batería, hasta que lo detengas.';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count mensajes en espera de envío se quedan en este teléfono hasta que vuelvas a conectar.',
      one:
          '1 mensaje en espera de envío se queda en este teléfono hasta que vuelvas a conectar.',
    );
    return '$_temp0';
  }

  @override
  String get folderBrowserSlowTitle => 'Aún leyendo esta carpeta';

  @override
  String get folderBrowserSlowBody =>
      'Listar las carpetas de este teléfono puede tardar hasta 15 segundos.';

  @override
  String get folderBrowserFirstProject => 'Nombra tu primer proyecto';

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
  String get kitRequestAllowOnce => 'Permitir una vez';

  @override
  String get kitRequestReject => 'Rechazar';

  @override
  String get kitRequestApprove => 'Aprobar';

  @override
  String get kitRequestSendBack => 'Devolver';

  @override
  String get kitRequestAnswer => 'Responder';

  @override
  String get kitRequestSend => 'Enviar';

  @override
  String get kitRequestReplyEmptyReason => 'Escribe una respuesta primero.';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString respuestas más',
      one: '1 respuesta más',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => 'Caducó · el agente dejó de esperar';

  @override
  String kitRequestAge(String age) {
    return 'esperando $age';
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
  String get kitDiffHideUnchanged => 'Hide unchanged lines';

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
  String kitDiffLinesAdded(int start, int end) {
    return 'Lines $start–$end added';
  }

  @override
  String kitDiffLinesRemoved(int start, int end) {
    return 'Lines $start–$end removed';
  }

  @override
  String get kitDiffShowSource => 'Source';

  @override
  String get kitDiffShowFormatted => 'Formatted';

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
  String get kitToolNotRun => 'No se ejecutó';

  @override
  String get kitToolWaiting => 'En espera';

  @override
  String get kitToolRunning => 'En ejecución';

  @override
  String get kitToolWaitingForYou => 'Esperándote';

  @override
  String get kitToolDone => 'Listo';

  @override
  String get kitToolFailed => 'Falló';

  @override
  String get kitToolDeclined => 'Lo rechazaste';

  @override
  String get kitToolStopped => 'Detenido';

  @override
  String get kitToolBackground => 'Iniciado en segundo plano';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString segundos',
      one: '1 segundo',
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
      other: '$countString minutos',
      one: '1 minuto',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => 'Abrir su conversación';

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
  String get kitCapServerOc2Title => 'OpenCode 2 server';

  @override
  String get kitCapServerOc2Why => 'Needs a server running OpenCode 2.';

  @override
  String get kitCapServerOc2Enable => 'Connect OpenCode 2';

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
  String get kitCapNetworkTailscaleEnable => 'Configurar Tailscale';

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
  String get activitySendOffline => 'Reconéctate al servidor para responder.';

  @override
  String get activityAnswerEveryQuestion =>
      'Responde todas las preguntas primero.';

  @override
  String get activityQuestionOptional => 'Opcional. Puedes dejar esta vacía.';

  @override
  String get activitySending => 'Enviando…';

  @override
  String activityQuestionProgress(int index, int total) {
    return 'Pregunta $index de $total';
  }

  @override
  String get activityOwnAnswer => 'O escribe tu propia respuesta';

  @override
  String get shortcutsPaletteSearch => 'Search or go to…';

  @override
  String get shortcutsHelpAnywhere => 'Anywhere';

  @override
  String get shortcutsHelpConversation => 'In a conversation';

  @override
  String get homeShellProjectUnavailable => 'Archivos no está disponible';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return '$server no ofrece archivos, cambios ni búsqueda de código. Conéctate a un servidor OpenCode para usarlos.';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return '$server no tiene herramientas de proyecto.';
  }

  @override
  String get workspaceContextFolder => 'Folder';

  @override
  String get workspaceSessionSharedLink => 'Shared link';

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
  String get settingsHubDetailEmpty => 'Elige un grupo de ajustes';

  @override
  String get notifyQuietStartPicker =>
      'Define cuándo empiezan las horas de silencio';

  @override
  String get notifyQuietEndPicker =>
      'Define cuándo terminan las horas de silencio';

  @override
  String get notifyQuietSet => 'Definir';

  @override
  String get notifyQuietAllDay =>
      'El inicio y el fin coinciden, así que las notificaciones permanecen en silencio todo el día.';

  @override
  String get notifySendingTest => 'Enviando una notificación de prueba…';

  @override
  String get notifyNoServersTitle => 'No hay servidores que vigilar';

  @override
  String get notifyNoServersDetail =>
      'Los servidores que guardes se pueden vigilar desde aquí, para que te llegue una solicitud de cualquiera de ellos.';

  @override
  String get notifyDismiss => 'Descartar';

  @override
  String get notifySaving => 'Guardando';

  @override
  String get notifyMonitorDetails =>
      'Cómo funciona la vigilancia de servidores';

  @override
  String get notifyRestartBackground => 'Reiniciar la conexión en vivo';

  @override
  String get privacySharedSection => 'Compartido con tu servidor';

  @override
  String get privacySaving => 'Saving…';

  @override
  String get privacyDeleting => 'Eliminando';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Eliminar $count mensajes en cola',
      one: 'Eliminar 1 mensaje en cola',
      zero: 'Eliminar mensajes en cola',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Eliminar $count borradores',
      one: 'Eliminar 1 borrador',
      zero: 'Eliminar borradores',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => 'Dijiste';

  @override
  String get kitMessageThinking => 'Pensando…';

  @override
  String get kitMessageThought => 'Pensó';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count segundos',
      one: '1 segundo',
    );
    return 'Pensó durante $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutos',
      one: '1 minuto',
    );
    return 'Pensó durante $_temp0';
  }

  @override
  String get kitMessageActions => 'Acciones del mensaje';

  @override
  String get kitMessageNoticeFailed => 'Falló';

  @override
  String get kitRequestChooseOneReason => 'Elige al menos una respuesta.';

  @override
  String get kitRequestSendAnswers => 'Send answers';

  @override
  String get serversRemoveBody =>
      'Este teléfono olvida el servidor: su contraseña, el modelo y agente elegidos, y las conversaciones de proyectos y widgets.';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se eliminarán $count borradores sin enviar',
      one: 'Se eliminará 1 borrador sin enviar',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext =>
      'Estás conectado a él: la app se desconecta y muestra tus servidores';

  @override
  String get serversRemoveServerKeeps =>
      'No se elimina nada en el servidor ni en tus proveedores de IA';

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
  String get pairingScannerAllowCamera => 'Permitir cámara';

  @override
  String get pairingScannerStarting => 'Abriendo la cámara…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return 'A run is going on $current. Switching shows $target in this app; the run on $current keeps going.';
  }

  @override
  String get profileMonitorOpenFailedTitle => 'Couldn\'t open it';

  @override
  String get profileMonitorIfIgnored => 'The agent waits until you answer';

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
  String get tailscaleSetupAppTitle => 'Tailscale en este teléfono';

  @override
  String get tailscaleSetupVpnTitle => 'Inicia sesión y conecta';

  @override
  String get tailscaleSetupVpnSupporting =>
      'Inicia sesión y conecta. OpenCode no puede comprobarlo.';

  @override
  String get tailscaleSetupOpenFailed =>
      'Tailscale no se abrió. Ábrelo desde tu launcher y vuelve.';

  @override
  String get tailscaleSetupAddressHelper =>
      'Pega la dirección HTTPS que mostró Tailscale Serve.';

  @override
  String get tailscaleSetupGetApp => 'Obtener Tailscale';

  @override
  String get tailscaleSetupContinueReason =>
      'Primero escribe la dirección de tu servidor.';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return 'Traducido en parte ($percent %)';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return 'Vista previa de $name';
  }

  @override
  String get appearancePickerPreviewIn => 'Vista previa en';

  @override
  String get appearancePickerInUse => 'En uso ahora';

  @override
  String appearancePickerThemeApplied(String name) {
    return 'Tema cambiado a $name';
  }

  @override
  String get serverSwitcherTitle => 'Servidores';

  @override
  String get serverSwitcherCurrentMenu => 'Acciones del servidor';

  @override
  String get localAgentEntryStillStarting =>
      'Aún iniciando · puede tardar un minuto';

  @override
  String get localAgentEntryDidNotStart => 'No se inició';

  @override
  String get localAgentEntryRemoving => 'Quitando';

  @override
  String localAgentEntrySignedOut(String state) {
    return '$state · Sin sesión en Claude';
  }

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
  String get kitTurnStarting => 'Iniciando el modelo…';

  @override
  String kitTurnStillStarting(int seconds) {
    return 'Aún esperando al modelo · $seconds s';
  }

  @override
  String get kitTurnStopped => 'Detuviste esta respuesta.';

  @override
  String get kitTurnInterrupted =>
      'La conexión se cortó antes de que terminara esta respuesta.';

  @override
  String get kitTurnCopy => 'Copiar respuesta';

  @override
  String get kitTurnMore => 'Más acciones para esta respuesta';

  @override
  String get kitTurnActions => 'Acciones de la respuesta';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return 'Disconnect from $serverName';
  }

  @override
  String get kitScannerStarting => 'Abriendo la cámara…';

  @override
  String get kitScannerSlow => 'La cámara sigue abriéndose';

  @override
  String get kitScannerPaused => 'Cámara en pausa';

  @override
  String get kitScannerPreview => 'Vista de la cámara';

  @override
  String get kitDateSet => 'Establecer fecha';

  @override
  String get kitTimeSet => 'Establecer hora';

  @override
  String get kitDateTimeSet => 'Establecer';

  @override
  String get kitDateType => 'Escribir una fecha';

  @override
  String get kitDateCalendar => 'Mostrar calendario';

  @override
  String kitDateFormatHint(String example) {
    return 'p. ej. $example';
  }

  @override
  String get kitDateField => 'Fecha';

  @override
  String get kitDateInvalid => 'No es una fecha';

  @override
  String kitDateOutOfRange(String first, String last) {
    return 'Elige una fecha entre $first y $last';
  }

  @override
  String get kitTimeHour => 'Hora';

  @override
  String get kitTimeMinute => 'Minuto';

  @override
  String get kitTimePeriod => 'Mañana o tarde';

  @override
  String get kitTimeInvalid => 'No es una hora';

  @override
  String get kitDateTimeNotSet => 'Sin establecer';

  @override
  String kitDateTimeClear(String title) {
    return 'Borrar $title';
  }

  @override
  String get kitDateUnavailable => 'Ese día no se puede elegir';

  @override
  String get filesLoadingFolder => 'Abriendo carpeta…';

  @override
  String get filesSearching => 'Buscando…';

  @override
  String get filesShowHidden => 'Mostrar archivos ocultos';

  @override
  String get filesOnlyHidden =>
      'Esta carpeta solo tiene archivos y carpetas ocultos.';

  @override
  String get filesCopyName => 'Copiar nombre';

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
      'A separate branch and folder are made and the project’s startup tasks run. Spaces become dashes.';

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
  String get phoneSetupStartUseTermuxOne => 'Usar el de Termux';

  @override
  String get phoneSetupStartUseTermuxOneDetail =>
      'OpenCode también está configurado en Termux. Conéctate a ese en su lugar.';

  @override
  String get phoneSetupStartTermuxNotAllowed =>
      'Termux está instalado, pero aún no deja entrar a esta app. Termina su configuración.';

  @override
  String get phoneSetupCustomizeAllInstalled =>
      'Todas las herramientas opcionales ya están en este teléfono.';

  @override
  String get phoneSetupCustomizeIncluded => 'Obligatorio';

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
  String get termuxProcsNoRestart => 'No se puede volver a iniciar desde aquí.';

  @override
  String get termuxProcsStopGroupTeamLost =>
      'Any task the team is working on stops too.';

  @override
  String get termuxProcsStopGroupTeamRestart =>
      'You can start the team again from AI Team.';

  @override
  String get phoneSetupProgressStopContinueLater =>
      'Puedes continuar cuando quieras desde «En este teléfono».';

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
  String get modelPickerChooseFirst => 'Elige primero un modelo.';

  @override
  String get modelPickerThinking => 'Razonamiento';

  @override
  String get modelPickerAgentBuild => 'Edita archivos y ejecuta comandos';

  @override
  String get modelPickerAgentPlan => 'Lee y planifica; no cambia archivos';

  @override
  String modelPickerDetailsOutput(String count) {
    return 'Hasta $count tokens por respuesta';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '$input por millón de tokens leídos, $output por millón escritos';
  }

  @override
  String get modelPickerCanThink => 'Piensa antes de responder';

  @override
  String get modelPickerCanUseTools => 'Usa herramientas';

  @override
  String get modelPickerCanReadAttachments =>
      'Lee las imágenes y archivos que adjuntas';

  @override
  String get modelPickerCopyId => 'Copiar id del modelo';

  @override
  String get modelPickerInUse => 'En uso';

  @override
  String get modelPickerUnavailableReason =>
      'No disponible en este servidor ahora mismo.';

  @override
  String get modelPickerCollections => 'Qué modelos mostrar';

  @override
  String get modelPickerSignInTitle =>
      'Hace falta iniciar sesión en un proveedor';

  @override
  String get modelPickerSignInBody =>
      'Ningún proveedor de este servidor tiene modelos todavía. Inicia sesión en uno y vuelve para elegir un modelo.';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar $count modelos más',
      one: 'Mostrar 1 modelo más',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'Build';

  @override
  String get modelPickerAgentPlanName => 'Plan';

  @override
  String phoneServerCardDisconnect(String server) {
    return 'Desconectar de $server';
  }

  @override
  String get phoneServerCardStartOpenCode => 'Iniciar OpenCode';

  @override
  String get phoneServerCardStopOpenCode => 'Detener OpenCode en este teléfono';

  @override
  String get phoneServerCardShowServerLog => 'Mostrar registro del servidor';

  @override
  String get phoneServerCardOpenTerminal => 'Abrir terminal';

  @override
  String get phoneServerCardFailedTitle => 'No se pudo terminar';

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
  String get productStatesActionFailedTitle => 'No se pudo completar';

  @override
  String get productStatesSwitchServer => 'Cambiar de servidor';

  @override
  String get externalLinkBlockedTitle => 'Enlace bloqueado';

  @override
  String get externalLinkBlockedBody =>
      'Esta app solo abre enlaces https://, y enlaces http:// tras tu confirmación.';

  @override
  String externalLinkOpensHost(String host) {
    return 'Abre $host fuera de esta app.';
  }

  @override
  String get externalLinkDontOpen => 'No abrir';

  @override
  String get externalLinkCopy => 'Copiar enlace';

  @override
  String get externalLinkAddress => 'Dirección completa';

  @override
  String get externalLinkOpenFailedTitle => 'No se pudo abrir el enlace';

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
    return 'Administrar cuentas de $name';
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
      'Pega el código que mostró la página de inicio de sesión después de aprobar el acceso.';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return 'Open $name sign-in';
  }

  @override
  String get integrationsCancelSignIn => 'Cancel sign-in';

  @override
  String get integrationsPendingNotRecoverable =>
      'Mantén esta pantalla abierta hasta terminar: este servidor no puede retomar un inicio de sesión si te vas.';

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
  String get terminalScreenSourceLabel => 'Dónde se ejecuta el shell';

  @override
  String get terminalScreenNameLabel => 'Nombre';

  @override
  String get terminalScreenRenameConfirm => 'Renombrar';

  @override
  String get terminalScreenNameEmpty => 'Escribe un nombre.';

  @override
  String terminalScreenStopTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String get terminalScreenStopBody =>
      'El programa y todo lo que inició se detienen, y el terminal desaparece. Su salida no se puede recuperar.';

  @override
  String terminalScreenRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get terminalScreenRemoveBody =>
      'El terminal y su salida se borran. No se puede deshacer.';

  @override
  String get terminalScreenStopConfirm => 'Detener terminal';

  @override
  String get terminalScreenRemoveConfirm => 'Quitar terminal';

  @override
  String get terminalScreenCreateFailed => 'No se pudo iniciar un terminal';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Quitar $count terminales finalizados',
      one: 'Quitar 1 terminal finalizado',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Quitar $count terminales finalizados?',
      one: '¿Quitar 1 terminal finalizado?',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody =>
      'Su salida también se borra. Los terminales en ejecución se conservan.';

  @override
  String get terminalScreenUsePhone => 'Usar el terminal de este teléfono';

  @override
  String terminalScreenRowRunning(String command) {
    return 'En ejecución · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return 'Finalizado · código $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return 'Finalizado · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return 'Acciones de $name';
  }

  @override
  String terminalScreenOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String terminalScreenRename(String name) {
    return 'Renombrar $name';
  }

  @override
  String terminalScreenStop(String name) {
    return 'Detener $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return 'Quitar $name';
  }

  @override
  String get terminalScreenLoading => 'Cargando terminales';

  @override
  String get terminalScreenPaused =>
      'En pausa mientras la app está en segundo plano';

  @override
  String get terminalScreenConnecting => 'Conectando con el terminal';

  @override
  String get terminalScreenCopy => 'Copiar salida';

  @override
  String terminalScreenPaste(String name) {
    return 'Pegar en $name';
  }

  @override
  String get terminalScreenDetails => 'Detalles del terminal';

  @override
  String terminalScreenDetailsTitle(String name) {
    return 'Detalles de $name';
  }

  @override
  String get terminalScreenDetailCommand => 'Comando';

  @override
  String get terminalScreenDetailFolder => 'Carpeta';

  @override
  String get terminalScreenDetailPid => 'ID de proceso';

  @override
  String get terminalScreenDetailExit => 'Código de salida';

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
  String get terminalScreenReadableMode => 'Mostrar como texto legible';

  @override
  String get terminalScreenLiveMode => 'Mostrar como terminal en vivo';

  @override
  String get localTerminalSetUpLinux => 'Set up Linux on this phone';

  @override
  String get messageViewSendAgain => 'Enviar este mensaje otra vez';

  @override
  String get messageViewContinueReply => 'Continuar esta respuesta';

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
  String reviewRevertKeepConfirmTitle(String title) {
    return 'Delete messages in “$title”?';
  }

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
  String reviewRevertRestoreConfirmTitle(String title) {
    return 'Restore “$title”?';
  }

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
  String get keepRunningAllSetTitle => 'Todo listo';

  @override
  String get keepRunningAllSetBody =>
      'Android mantiene la app en ejecución en segundo plano. No hay nada más que permitir en este teléfono.';

  @override
  String get keepRunningDailyLimit =>
      'En Android 15 y posteriores, Android permite unas 6 horas al día de sincronización en segundo plano, aunque todo lo de aquí esté permitido. Después, la app se pausa en segundo plano hasta que la abras.';

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
  String get demoScreenTitle => 'Pruébalo sin conexión';

  @override
  String get demoScreenSimulated => 'Simulado · no se guarda nada';

  @override
  String get demoScreenFinished =>
      'Ese es todo el ciclo: un mensaje, una respuesta y una edición revisada.';

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
  String get credentialSheetLoading => 'Leyendo las cuentas guardadas…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return 'Vuelve a iniciar sesión en $provider desde Proveedores para agregar una cuenta.';
  }

  @override
  String credentialSheetActions(String label) {
    return 'Acciones para $label';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return 'Usar $label';
  }

  @override
  String get credentialSheetInUse => 'Ya en uso';

  @override
  String credentialSheetRenameNamed(String label) {
    return 'Renombrar $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return 'Quitar $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return 'Quita $label de este servidor. Los proyectos que la usan necesitarán otra cuenta de $provider.';
  }

  @override
  String credentialSheetRenamed(String label) {
    return 'Renombrada a $label.';
  }

  @override
  String get credentialSheetLabelEmpty => 'Ponle un nombre a la cuenta.';

  @override
  String get credentialSheetLabelInvalid =>
      'Usa hasta 128 caracteres, sin saltos de línea ni caracteres de control.';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return '¿Olvidar el inicio de sesión de $integration?';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      'La app deja de seguirlo en este dispositivo. No se cancela nada en el servidor; un inicio de sesión sin terminar allí caduca por sí solo.';

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
    return 'Delegado a $agent';
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
    return 'Cargar $name de nuevo';
  }

  @override
  String toolCardChangesIn(String file) {
    return 'Cambios en $file';
  }

  @override
  String mobileTasksShowAll(int count) {
    return 'Show all $count tasks';
  }

  @override
  String get composerBusyReason => 'Preparando tu mensaje…';

  @override
  String get composerToolsTextOnly => 'Este servidor solo acepta texto';

  @override
  String composerToolsAgentTextOnly(String agent) {
    return 'Aquí $agent solo acepta texto';
  }

  @override
  String launcherConversationHint(String project, String agent) {
    return 'Conversation in $project · $agent';
  }

  @override
  String composerToolsAgentPicturesOnly(String agent) {
    return '$agent solo acepta imágenes: usa «Galería de fotos» o «Tomar foto»';
  }

  @override
  String chatsNewAgentNeedsProjectsFolder(String agent) {
    return '$agent solo trabaja en los proyectos de este teléfono. Elige uno de ellos.';
  }

  @override
  String get composerToolCommandsTitle => 'Comandos y agentes';

  @override
  String get composerToolSavedSubtitle =>
      'Vuelve a poner en el borrador un mensaje guardado';

  @override
  String get composerToolSaveForLater => 'Guardar mensaje para después';

  @override
  String get composerToolNothingToSave => 'Escribe o adjunta algo primero';

  @override
  String get composerToolsMore => 'Más herramientas';

  @override
  String get composerReturnedToDraft => 'Devuelto a tu borrador';

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
  String get modelShortcutsNextRecent => 'Siguiente modelo reciente';

  @override
  String get modelShortcutsPreviousRecent => 'Modelo reciente anterior';

  @override
  String get modelShortcutsNoRecent =>
      'Usa primero otro modelo para volver a este';

  @override
  String get modelShortcutsNoFavorite =>
      'Primero marca un modelo como favorito en el selector de modelos';

  @override
  String get composerDraftBlockedReason =>
      'Responde primero la pregunta sobre este borrador';

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
  String connectionReconnectTo(String server) {
    return 'Reconectar con $server';
  }

  @override
  String serverDisconnectFrom(String server) {
    return 'Desconectar de $server';
  }

  @override
  String get localAgentStopNamed => 'Detener Claude Code';

  @override
  String get localAgentStartNamed => 'Iniciar Claude Code';

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
    return 'Cambiar el inicio de sesión de $server';
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
  String get notifyTurnOnInAndroid => 'Activar las notificaciones en Android';

  @override
  String get serversAddOtherWays => 'O conecta de otra forma';

  @override
  String get libraryImportAConversation => 'Import a conversation';

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
      'Este servidor no comparte un terminal';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return '$server no comparte un terminal';
  }

  @override
  String get terminalScreenNoTerminalWhy =>
      'Aquí solo se abren terminales de servidores que los comparten.';

  @override
  String get integrationsSignInWaiting => 'Inicio de sesión en espera';

  @override
  String get integrationsSignInMayNotHaveStarted =>
      'Puede que el inicio de sesión no haya comenzado';

  @override
  String get integrationsSignInExpired => 'El inicio de sesión expiró';

  @override
  String get integrationsSignInFailed => 'Falló el inicio de sesión';

  @override
  String get integrationsSignInComplete =>
      'Sesión iniciada · toca para terminar';

  @override
  String integrationsFinishSigningIn(String provider) {
    return 'Terminar de iniciar sesión en $provider';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return 'Ingresar el código de $provider';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return 'Cancelar el inicio de sesión de $provider';
  }

  @override
  String get integrationsForgetSignInOnPhone =>
      'Olvidar este inicio de sesión en este teléfono';

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
  String modelPickerUseModel(String model) {
    return 'Usar $model';
  }

  @override
  String get modelPickerUseChosenModel => 'Usar modelo';

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
  String get thisPhoneSetUp => 'Set up OpenCode';

  @override
  String get thisPhoneStart => 'Start the server';

  @override
  String get thisPhoneStop => 'Stop OpenCode on this phone';

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
  String get thisPhoneRemove => 'Remove OpenCode';

  @override
  String get thisPhoneBusy => 'Wait for the current step to finish';

  @override
  String get phoneSetupTermuxAllowHow =>
      'En Termux, pega la línea copiada y pulsa Intro.';

  @override
  String get phoneSetupTermuxUpdatingTitle => 'Actualizando este teléfono';

  @override
  String get phoneSetupTermuxStartingTitle => 'Iniciando el servidor';

  @override
  String get phoneSetupTermuxConnecting => 'Conectando';

  @override
  String get phoneSetupTermuxLeaveHint =>
      'Puedes salir de la app. Termux sigue trabajando y esta lista continúa donde esté cuando vuelvas.';

  @override
  String get phoneSetupTermuxCost =>
      'Unos 10–15 minutos la primera vez, en el almacenamiento de Termux';

  @override
  String removeFromPhoneKeepBody(String size) {
    return 'Se quitan OpenCode y sus herramientas, liberando unos $size.';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured =>
      'Se quitan OpenCode y sus herramientas.';

  @override
  String get removeFromPhoneKeepConfirm =>
      'Quitar OpenCode y conservar mis proyectos';

  @override
  String get removeFromPhoneDeleteAll => 'Eliminar todo';

  @override
  String get removeFromPhoneDeleteTitle =>
      '¿Eliminar OpenCode y los proyectos?';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return 'Se eliminan OpenCode, sus herramientas y todos los proyectos de este teléfono, liberando unos $size. Esto no se puede deshacer.';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      'Se eliminan OpenCode, sus herramientas y todos los proyectos de este teléfono. Esto no se puede deshacer.';

  @override
  String get thisPhoneManage => 'Administrar Este teléfono';

  @override
  String get chatRequestWho => 'El agente';

  @override
  String get chatRequestIfIgnored =>
      'El agente espera hasta que respondas. No se pierde nada.';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Hay $count solicitudes más en espera.',
      one: 'Hay 1 solicitud más en espera.',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection =>
      'No hay conexión con el servidor, así que no se puede responder desde aquí.';

  @override
  String get chatRequestAlwaysTitle => 'Permitir siempre estas solicitudes';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return 'A partir de ahora, $patterns se ejecutará sin preguntarte, $context. Puedes revertirlo en Ajustes.';
  }

  @override
  String chatRequestAlwaysConfirm(String what) {
    return '¿Permitir siempre $what en este proyecto?';
  }

  @override
  String get chatRequestAlwaysInProject => 'en este proyecto';

  @override
  String get chatRequestAlwaysOn => 'Siempre permitido';

  @override
  String get chatRequestDetailTool => 'Herramienta';

  @override
  String get chatRequestDetailPatterns => 'Patrones solicitados';

  @override
  String get chatRequestOtherAnswer => 'Otra respuesta';

  @override
  String get chatRequestOtherField => 'Tu respuesta';

  @override
  String get formFlowAnsweredElsewhereBody =>
      'This form was answered on another device, so nothing was sent from this phone.';

  @override
  String get approvalsUiPausedDetail =>
      'Este teléfono no está conectado. La aprobación automática se reanuda cuando se reconecte.';

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
  String get phoneServerNameInSentence => 'el Ubuntu de la app';

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
  String get searchArchivedConversations => 'Archived conversations';

  @override
  String get readAloudConsentEngine =>
      'Only offline voices are offered, but the speech engine is separate software with its own privacy terms.';

  @override
  String get readAloudConsentHeard =>
      'People near you may hear it. Reading stops when you leave this conversation or the app.';

  @override
  String get transcriptFindStopSearchingAll =>
      'Dejar de buscar en mensajes anteriores';

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
  String get voiceComponentTitle => 'Escritura por voz';

  @override
  String get voiceComponentSummary =>
      'Habla en lugar de escribir, incluso sin conexión';

  @override
  String get voiceComponentRemove => 'Quitar la escritura por voz';

  @override
  String get voiceComponentRemoveTitle => '¿Quitar la escritura por voz?';

  @override
  String voiceComponentRemoveBody(String size) {
    return 'Elimina el modelo de voz y libera $size. La escritura por voz dejará de funcionar hasta que la vuelvas a agregar aquí.';
  }

  @override
  String get setupAppStageDownloading => 'Descargando';

  @override
  String get setupAppStageVerifying => 'Comprobando la descarga';

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
  String get demoScreenReset => 'Reiniciar demo';

  @override
  String get demoScreenLeave => 'Salir de la demo';

  @override
  String get demoScreenDisclosure =>
      'Todo aquí es simulado en este dispositivo. No se accede a ningún servidor, proveedor ni archivo.';

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
  String get chatDraftCopy => 'Copiar borrador';

  @override
  String get reportProblemDescribeLabel => 'What happened?';

  @override
  String get reportProblemDescribeHint =>
      'What you did, what you expected, what you got instead';

  @override
  String get reportProblemDescribeFirst => 'Say what happened first';

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
  String get reportProblemLinkCopiesDiagnostics =>
      'The diagnostics are too long for the link. Opening the form copies them, so paste them into its Diagnostics field.';

  @override
  String get reportProblemLinkCopiesWhole =>
      'The report is too long for the link. Opening the form copies it, so paste it into the form.';

  @override
  String get reportProblemCopied => 'Report copied';

  @override
  String get reportProblemDiagnosticsCopied =>
      'Diagnostics copied: paste them into the form';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count errores guardados',
      one: '1 error guardado',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail =>
      'Python, AI Team, voice typing and more';

  @override
  String get phoneSetupTermuxOtherRuntime =>
      'Termux ya ejecuta el otro OpenCode. Cámbialo en «En este teléfono» y continúa la configuración.';

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
  String get undoneStatus => 'Deshecho desde un mensaje';

  @override
  String get undonePutBack => 'Restaurar';

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
    return 'Conectado a $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return '$host aún no ha respondido. Una red lenta puede tardar un poco.';
  }

  @override
  String get addServerCheckCancel => 'Dejar de comprobar';

  @override
  String get addServerUseTailscale => 'Usar Tailscale';

  @override
  String get addServerStepsLabel => 'Progreso para agregar servidor';

  @override
  String get addServerStepKind => 'Qué se ejecuta allí';

  @override
  String get addServerStepTailscale => 'Tailscale en este teléfono';

  @override
  String get addServerStepPair => 'Empareja o introduce la dirección';

  @override
  String get addServerStepAddress => 'Dirección e inicio de sesión';

  @override
  String get addServerStepCheck => 'Comprobando';

  @override
  String get addServerStepReady => 'Listo';

  @override
  String addServerReadyTitle(String name) {
    return '$name está conectado';
  }

  @override
  String get addServerReadyBody =>
      'Sus conversaciones se abren a continuación. Empieza una o retoma una que ya esté ahí.';

  @override
  String addServerReadyOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String get handoffUiLinkAddTitle => 'Add this server?';

  @override
  String get handoffUiLinkAddServer => 'Add server';

  @override
  String get failedJobReport => 'Report this failure';

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
    return '¿Quitar la cuenta “$name” de $provider?';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return 'Quitar “$name”';
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
  String get quotaCollectorGuide => 'Open the collector guide';

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
    return 'La configuración en Termux va al $percent%';
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
  String get chatErrorModelNotFound => 'El servidor no tiene este modelo.';

  @override
  String get chatErrorContextOverflow =>
      'Esta conversación es demasiado larga para el modelo.';

  @override
  String get chatErrorProviderAuth =>
      'El proveedor del modelo necesita que vuelvas a iniciar sesión.';

  @override
  String get chatErrorOutputLength =>
      'La respuesta alcanzó el límite de longitud del modelo.';

  @override
  String get chatErrorContentFilter =>
      'El filtro de seguridad del proveedor detuvo esta respuesta.';

  @override
  String get chatErrorUnknown => 'El agente se detuvo por un error.';

  @override
  String modelPickerThinkingChip(String level) {
    return 'Razonamiento: $level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return 'Agente: $agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes en cola pasan a Mensajes guardados',
      one: '1 mensaje en cola pasa a Mensajes guardados',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Puede que $count ya se hayan enviado',
      one: 'Puede que 1 ya se haya enviado',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Quitar y eliminar $count mensajes en cola',
      one: 'Quitar y eliminar el mensaje en cola',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return 'Los mensajes en cola de $name cambiaron, así que no se quitó nada. Vuelve a quitarlo para ver el nuevo recuento.';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return 'No se pudieron mover los mensajes en cola de $name a Mensajes guardados, así que no se quitó nada. Elimina algunos mensajes guardados o libera almacenamiento e inténtalo de nuevo.';
  }

  @override
  String get settingsHubGroupAgent => 'Agent';

  @override
  String get settingsHubGroupConversations => 'Conversations';

  @override
  String get settingsHubGroupThisApp => 'Esta app';

  @override
  String get settingsHubProvidersRow => 'Providers and accounts';

  @override
  String get settingsHubToolsRow => 'Tools';

  @override
  String get settingsHubShowReasoning => 'Mostrar razonamiento';

  @override
  String get settingsHubShowTimestamps => 'Mostrar hora, tokens y costo';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ajustes no están disponibles en este servidor',
      one: '1 ajuste no está disponible en este servidor',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => 'Por qué';

  @override
  String get privacyPolicyTitle => 'Privacy policy';

  @override
  String get aboutHelpSection => 'Tips and shortcuts';

  @override
  String get settingsHubSearchToolsAliases =>
      'tools mcp integrations commands skills references slash capabilities plugins external agents a2a';

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
      'Las conversaciones y los ajustes dentro de OpenCode se eliminan';

  @override
  String get removeFromPhoneKeepKept =>
      'Tus proyectos se conservan y vuelven cuando lo configures de nuevo';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return 'Tus proyectos ($size) se conservan y vuelven cuando lo configures de nuevo';
  }

  @override
  String get removeFromPhoneDeleteLost =>
      'Los archivos de proyecto que no estén guardados en otro lugar se pierden para siempre';

  @override
  String get productErrorTimedOut =>
      'El servidor tardó demasiado en responder. Inténtalo de nuevo.';

  @override
  String get productErrorCertificate =>
      'El certificado de seguridad del servidor no es de confianza, así que la app se detuvo. Revisa la dirección del servidor.';

  @override
  String get productErrorSignIn =>
      'El servidor no aceptó el inicio de sesión. Revisa la contraseña en los ajustes del servidor.';

  @override
  String get productErrorNotFound =>
      'El servidor no lo encontró. Puede que se haya movido o eliminado.';

  @override
  String get productErrorConflict =>
      'Mientras tanto cambió en el servidor. Actualiza e inténtalo de nuevo.';

  @override
  String get productErrorBusy =>
      'El servidor está ocupado. Espera un momento e inténtalo de nuevo.';

  @override
  String get productErrorRejected =>
      'El servidor no aceptó la solicitud. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorUnknown =>
      'Eso no funcionó. Detalles muestra qué pasó. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorUnexpected =>
      'La app no entendió la respuesta del servidor. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorDevice =>
      'Algo en este dispositivo no funcionó. Inténtalo de nuevo.';

  @override
  String get productErrorStorage =>
      'La app no pudo leer ni guardar un archivo en este dispositivo.';

  @override
  String get productErrorTermux =>
      'Termux no terminó eso. Comprueba que Termux esté instalado y abierto, e inténtalo de nuevo.';

  @override
  String get productErrorDetailsLabel => 'Detalles del error';

  @override
  String productErrorServer(int code) {
    return 'El servidor tuvo un problema (error $code). Inténtalo de nuevo en un momento.';
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
      'Android no desactivó el modo en segundo plano.';

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
      'Tu respuesta no se pudo guardar en este teléfono, así que no se cambió nada. Inténtalo de nuevo.';

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
  String get consentRowNeedsYou => 'Notify me when the agent needs me';

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
    return 'Preguntado 3 veces. ¿Permitir siempre $what?';
  }

  @override
  String get consentAlwaysAllowDecline => 'Seguir preguntando';

  @override
  String get consentAlwaysAllowFailed =>
      'El servidor no guardó Permitir siempre. La solicitud sigue esperando; inténtalo de nuevo o respóndela solo esta vez.';

  @override
  String get consentAlwaysAllowTitle => '¿Permitir siempre esta solicitud?';

  @override
  String get consentNeedsYouAllow => 'Turn on notifications';

  @override
  String get bootstrapOpeningTitle => 'Abriendo…';

  @override
  String get bootstrapOpeningBody => 'Leyendo tus servidores guardados.';

  @override
  String get bootstrapFailedTitle =>
      'No se pueden leer los servidores guardados';

  @override
  String get bootstrapFailedBody =>
      'Si tu teléfono acaba de reiniciarse, desbloquéalo e inténtalo de nuevo.';

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
  String get appNewConversationFailed =>
      'No se pudo iniciar una conversación nueva';

  @override
  String get rootPhoneServerStartFailed =>
      'OpenCode no se inició en este teléfono';

  @override
  String get connectionFailureLocalCodexBody =>
      'Un listener local de Codex debería responder en este teléfono, pero no respondió nada.';

  @override
  String get connectionFailureRemoteCodexBody =>
      'Nadie respondió en el endpoint de Codex.';

  @override
  String get connectionFailureLoopbackBody =>
      'La app buscó un servidor en este teléfono y no obtuvo respuesta. Inicia ese servidor o reconecta el túnel que trae uno hasta aquí, y vuelve a intentarlo.';

  @override
  String get connectionFailureTimedOutBody =>
      'Hay algo en esa dirección, pero no respondió. Suele ser la red intermedia, no el servidor.';

  @override
  String get connectionFailureNothingAnsweredBody =>
      'Nadie respondió. O el servidor no está en ejecución, o este teléfono no puede llegar a su dirección.';

  @override
  String get connectionFailureUnknownBody =>
      'La conexión falló. Lo que salió mal está en Detalles.';

  @override
  String get connectionFailureTailnetCheck =>
      'Esta es una dirección de Tailscale: ¿está activado Tailscale en este teléfono y en el servidor?';

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
  String get terminalScreenEmptyTitle => 'Aún no hay terminales';

  @override
  String terminalScreenEmptyBody(String project) {
    return 'Inicia uno en $project.';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => 'Inicia uno en este proyecto.';

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
    return 'durante $duration';
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
  String get kitToolOpenDetails => 'Abrir sus detalles';

  @override
  String get teamStartRunKeepInBacklog => 'Keep in backlog';

  @override
  String workRunawayStopped(String helper) {
    return '$helper detenido';
  }

  @override
  String workRunawayStopFailed(String helper) {
    return 'No se pudo detener $helper. Inténtalo de nuevo o deténlo desde Termux.';
  }

  @override
  String get serverSettingsUpdateCommandsDetail =>
      'Run them in a terminal on the server\'s computer; this app can\'t update it.';

  @override
  String get serverSettingsUpdateCommandsCopied =>
      'Copied. Run them in a terminal on the server\'s computer.';

  @override
  String hostServiceTitle(String server) {
    return 'OpenCode on $server';
  }

  @override
  String hostServiceIntro(String server) {
    return 'These commands run on $server\'s computer; copy each into a terminal there.';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return 'Pendiente · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList =>
      'OpenCode no puede listar los dispositivos de tu tailnet.';

  @override
  String get productErrorStagedRevert =>
      'Revisa la reversión preparada antes de enviar este mensaje en cola.';

  @override
  String teamWatchComposerHint(String name) {
    return 'Message $name…';
  }

  @override
  String teamWatchAboutRole(String role) {
    return 'About the $role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return 'No se pueden leer los mensajes en cola de $name. Se conservaron el servidor y sus mensajes en cola. Intenta quitarlo de nuevo cuando se pueda leer la cola.';
  }

  @override
  String get bootstrapStartFresh => 'Empezar de cero';

  @override
  String get bootstrapStartFreshTitle =>
      '¿Quitar los inicios de sesión guardados?';

  @override
  String get bootstrapStartFreshBody =>
      'Esto quita las contraseñas y los tokens de conexión guardados de este teléfono y borra el servidor seleccionado. Tus servidores guardados, mensajes en cola y borradores se conservan.';

  @override
  String get bootstrapStartFreshConfirm => 'Quitar inicios de sesión guardados';

  @override
  String get bootstrapResettingTitle => 'Quitando inicios de sesión guardados…';

  @override
  String get bootstrapResettingBody =>
      'Mantén la app abierta hasta que termine.';

  @override
  String get bootstrapResetFailedTitle =>
      'Falló el restablecimiento de sesiones';

  @override
  String get bootstrapResetFailedBody =>
      'No se pudieron quitar algunos inicios de sesión guardados. Inténtalo de nuevo.';

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
  String get aiSetupTitle => 'Configuración de IA';

  @override
  String get aiSetupEntryDetail =>
      'Models, tools and suggestions for this server';

  @override
  String get aiSetupRefresh =>
      'Volver a leer la configuración de este servidor';

  @override
  String get aiSetupLoading => 'Leyendo la configuración de este servidor…';

  @override
  String get aiSetupReviewOnly =>
      'Solo lectura. Por ahora los cambios se hacen en el servidor.';

  @override
  String get aiSetupUnsupportedTitle =>
      'La configuración de IA no está disponible';

  @override
  String get aiSetupUnsupportedBody =>
      'Este servidor no comparte su configuración con la app. Configura sus modelos y herramientas en el propio servidor.';

  @override
  String get aiSetupSignInTitle => 'Hace falta iniciar sesión';

  @override
  String aiSetupSignInBody(String server) {
    return '$server no aceptó el inicio de sesión guardado, así que no se puede leer su configuración.';
  }

  @override
  String get aiSetupErrorTitle => 'No se pudo leer la configuración';

  @override
  String get aiSetupErrorBody =>
      'El servidor no respondió como se esperaba. Inténtalo de nuevo o revisa el servidor en su página de ajustes.';

  @override
  String get aiSetupTryAgain => 'Intentar de nuevo';

  @override
  String get aiSetupOfflineTitle => 'Estás sin conexión';

  @override
  String aiSetupOfflineBody(String server) {
    return 'Vuelve a conectarte a $server para leer su configuración.';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return 'Sin conexión. Esta es la configuración de $server tal como se leyó por última vez; se actualiza al reconectar.';
  }

  @override
  String get aiSetupEmptyTitle => 'Aún no hay nada configurado';

  @override
  String get aiSetupEmptyBody =>
      'Este servidor usa sus valores predeterminados, sin modelo elegido ni servidores de herramientas. Por ahora los cambios se hacen en el servidor.';

  @override
  String get aiSetupSuggestionsLabel => 'Sugerencias';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return 'Inicia sesión en $name';
  }

  @override
  String get aiSetupSuggestSignInDetail =>
      'Sus herramientas permanecen desactivadas hasta que alguien inicie sesión en él en el servidor.';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return 'Revisa los ajustes de $name';
  }

  @override
  String get aiSetupSuggestFixDetail =>
      'No se pudo iniciar. Corrige su entrada en la configuración del servidor y reinicia el servidor.';

  @override
  String get aiSetupSuggestModelTitle => 'Elige un modelo predeterminado';

  @override
  String get aiSetupSuggestModelDetail =>
      'No hay ningún modelo definido, así que las conversaciones nuevas usan el que elige el servidor. Define “model” en la configuración del servidor.';

  @override
  String get aiSetupSuggestToolsTitle => 'Agrega servidores de herramientas';

  @override
  String get aiSetupSuggestToolsDetail =>
      'No hay servidores MCP configurados. Agrega uno en la configuración del servidor para darle más herramientas al agente.';

  @override
  String get aiSetupToolsLabel => 'Servidores de herramientas';

  @override
  String get aiSetupToolsTerm =>
      'Los servidores MCP le dan herramientas adicionales al agente. Cada uno indica si funciona ahora.';

  @override
  String get aiSetupToolConnected => 'Conectado';

  @override
  String get aiSetupToolWaiting => 'En espera';

  @override
  String get aiSetupToolOff => 'Desactivado';

  @override
  String get aiSetupToolFailed => 'Falló';

  @override
  String get aiSetupToolNeedsSignIn => 'Requiere inicio de sesión';

  @override
  String get aiSetupToolUnknown => 'Desconocido';

  @override
  String get aiSetupEffectiveLabel => 'Ajustes en vigor';

  @override
  String get aiSetupEffectiveTerm =>
      'Lo que usan las conversaciones de este servidor, tras combinar sus archivos de configuración.';

  @override
  String get aiSetupModel => 'Modelo';

  @override
  String get aiSetupServerDefault => 'Sin definir: el servidor elige';

  @override
  String get aiSetupSmallModel => 'Modelo pequeño';

  @override
  String get aiSetupDefaultAgent => 'Agente predeterminado';

  @override
  String get aiSetupProviders => 'Proveedores';

  @override
  String get aiSetupPermissions => 'Permisos';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas',
      one: '1 regla',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => 'Todos los ajustes';

  @override
  String get aiSetupSourcesLabel => 'Fuentes de configuración';

  @override
  String get aiSetupSourcesTerm =>
      'Listadas de menor a mayor prioridad, tal como las informa el servidor. La app no las combina.';

  @override
  String get aiSetupNoSources => 'No hay archivos de configuración';

  @override
  String get aiSetupNoSourcesDetail =>
      'Este servidor usa sus valores predeterminados.';

  @override
  String aiSetupSourceUnnamed(String type) {
    return 'Fuente sin archivo ($type)';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position. Define $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position. No define nada';
  }

  @override
  String get aiSetupAllSources => 'Todas las fuentes';

  @override
  String get integrationsPageLoadFailed => 'Could not load this page';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · Actualizando';
  }

  @override
  String get kitLastKnownHint =>
      'Guardado de la vez anterior. Se abren cuando cargue la lista en vivo.';

  @override
  String get kitTranscriptExcerptHint =>
      'Guardado de la vez anterior. La conversación se abre completa cuando termine de cargar.';

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
      other: '$count mensajes esperando para enviarse',
      one: '1 mensaje esperando para enviarse',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mover $count mensajes en espera a $destination',
      one: 'Mover 1 mensaje en espera a $destination',
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
  String get removeFromPhoneDeleteAllChoice => 'Eliminar todo…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return 'Eliminar todo y liberar unos $size…';
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
    return '$command no se envió: $agent aún no comparte sus comandos con la app. Quita la / para enviarlo como mensaje.';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return '$command no se envió: los comandos de shell no se pueden ejecutar en $agent desde la app. Quita la ! para enviarlo como mensaje.';
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
  String get commandSheetAgentFallback => 'Este agente';

  @override
  String get commandSheetPlanDescription =>
      'Opens the agent\'s latest plan in the conversation';

  @override
  String get chatUiSessionMenu => 'Menú de la conversación';

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
  String get addServerTailscaleNext => 'Ingresa la dirección';

  @override
  String get phoneSetupTermuxGetCurrent => 'Obtener el Termux actual';

  @override
  String get phoneSetupUnsupportedTitle => 'Conectar un servidor';

  @override
  String get phoneSetupUnsupportedBody =>
      'Configurar en el propio dispositivo solo funciona en teléfonos Android. En tu computadora, ejecuta este comando y luego agrega aquí el servidor con el código que imprime.';

  @override
  String get termuxStorageStageTotal => 'The whole Termux install';

  @override
  String get setupProgressViewFailedStep =>
      'Este paso no terminó. Lo que falló está en Detalles.';

  @override
  String setupProgressViewFailedAt(String name) {
    return 'Se detuvo en $name. Lo que falló está en Detalles.';
  }

  @override
  String workRunawayHelper(String helper, String duration) {
    return 'Un proceso $helper sobrante lleva $duration ocupado sin nada que hacer';
  }

  @override
  String workRunawayHelperInProject(
    String helper,
    String project,
    String duration,
  ) {
    return 'Un proceso $helper sobrante en $project lleva $duration ocupado sin nada que hacer';
  }

  @override
  String get workRunawaySeeRunning => 'Ver qué se está ejecutando';

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
  String get thisPhoneHistoryMoving =>
      'Bringing your earlier conversations across…';

  @override
  String thisPhoneHistoryMovingCount(int done, int total) {
    return 'Bringing your earlier conversations across ($done of $total)…';
  }

  @override
  String get thisPhoneHistoryFailed =>
      'No se pudieron traer tus conversaciones anteriores. Sus datos originales siguen en este teléfono, intactos.';

  @override
  String get addServerCheckFailedPlain =>
      'No se pudo comprobar el servidor. Revisa la dirección y la conexión de este teléfono e inténtalo de nuevo.';

  @override
  String serverRowDetailsTitle(String name) {
    return 'Detalles de $name';
  }

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
      'Intenta guardar de nuevo. Si sales sin guardar, tus últimos cambios en este borrador podrían perderse.';

  @override
  String get draftLeaveCopyAction => 'Copiar borrador y salir';

  @override
  String get draftLeaveRetry => 'Intentar guardar de nuevo';

  @override
  String get draftLeaveStillFailing =>
      'Sigue sin guardarse. Copia tu texto antes de salir.';

  @override
  String get queuedRetry => 'Intentar de nuevo';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Reintentar las $count',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return 'Usar $model y reenviar';
  }

  @override
  String get chatUiChooseAnotherModel => 'Elegir otro modelo';

  @override
  String get chatUiSendPromptAgain => 'Enviar otra vez';

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
      'Into a new folder in On this phone. Nothing there is overwritten.';

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
      'Private copies stay in On this phone. Nothing in them runs or turns on by itself.';

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
  String get migrationStart => 'Copy to On this phone';

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
  String get migrationSwitching => 'Connecting to On this phone…';

  @override
  String get migrationStepConnect => 'Connect to On this phone';

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
  String get migrationNeedsBuiltin => 'Set up On this phone first.';

  @override
  String migrationNeedsBuiltinBody(String runtime) {
    return 'Your projects move into OpenCode inside this app, so it needs setting up. Setup installs Linux and $runtime, then this continues here.';
  }

  @override
  String get migrationSetUpBuiltin => 'Set up On this phone';

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
  String get migrationLinkedFileUnreadable =>
      'Some of this project\'s Git history can\'t be read in Termux, so it can\'t be moved safely.';

  @override
  String get migrationLinkedFileUnreadableFix =>
      'Open Termux, run git fsck in that project to see what is missing, then try again. Nothing was changed on your phone.';

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
      'The files in On this phone stay as you left them.';

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
      'Files are copied, but On this phone could not connect.';

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
    return 'In the folder $folder in On this phone';
  }

  @override
  String get migrationExportsWhere => 'Private copies';

  @override
  String migrationExportsWhereBody(String items) {
    return '$items: saved in On this phone, not turned on';
  }

  @override
  String get migrationRemoveTermux =>
      'Remove the Termux server when you\'re ready';

  @override
  String get migrationRemoveTermuxBody =>
      'Nothing is removed for you. Until then it keeps working, and you can switch back to it on Servers.';

  @override
  String get migrationOpenBuiltin => 'Open On this phone';

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
      'Copy your projects to the server on this phone. Termux stays as it is.';

  @override
  String get migrationRowResume => 'Resume moving to On this phone';

  @override
  String get migrationRowResumeBody =>
      'Stopped before it finished. What was copied is kept.';

  @override
  String get migrationRowRunning => 'Moving to On this phone';

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
      'Revisa el servidor antes de empezar de nuevo';

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
      'OpenCode se cerró solo al iniciarse. Abre la configuración para ver su registro, o inícialo de nuevo.';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'OpenCode no respondió en $seconds segundos. Puede que el teléfono esté ocupado o con poca memoria; cierra otras apps e inícialo de nuevo.';
  }

  @override
  String get inAppServerStartInterruptedBody =>
      'El inicio se detuvo porque la app salió de la pantalla. Inícialo de nuevo para continuar.';

  @override
  String get inAppServerStartPasswordBody =>
      'La app no pudo configurar el inicio de sesión de OpenCode en este teléfono. Inícialo de nuevo; si se repite, abre la configuración.';

  @override
  String get inAppServerStartRefusedBody =>
      'El teléfono no permitió que la app iniciara OpenCode en este momento. Inícialo de nuevo; si se repite, reinicia el teléfono.';

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
      'OpenCode se está ejecutando en Termux, pero esta app aún no puede llegar a Termux. Permite el acceso y se conectará.';

  @override
  String get termuxProblemAccessNeeded =>
      'Esta app aún no puede llegar a Termux. Permite el acceso para que pueda encontrar OpenCode allí y conectarse.';

  @override
  String get termuxProblemAccessBlocked =>
      'Android bloqueó el acceso de esta app a Termux. En los permisos de esta app, activa «Run commands in Termux environment».';

  @override
  String get termuxProblemOtherAppsOff =>
      'Termux aún no acepta comandos de otras apps. Una línea en Termux lo permite.';

  @override
  String get termuxProblemAsleep =>
      'Termux no respondió. Puede que Android lo haya suspendido. Abre Termux para despertarlo.';

  @override
  String termuxProblemNotAnswering(String runtime) {
    return '$runtime está configurado en Termux, pero no responde. Un reinicio suele arreglarlo.';
  }

  @override
  String get termuxProblemNotInstalled =>
      'Termux no está en este teléfono. Instálalo de nuevo o configura el servidor dentro de la app.';

  @override
  String get termuxProblemOutdated =>
      'Esta versión de Termux es demasiado antigua para que la app la use. Instala el Termux actual desde F-Droid.';

  @override
  String termuxProblemUnknown(String runtime) {
    return 'Este teléfono no pudo comprobar $runtime en Termux. Inténtalo de nuevo en un momento.';
  }

  @override
  String get termuxFixAllowAccess => 'Permitir acceso a Termux';

  @override
  String get termuxFixOpenPermissions => 'Abrir los permisos de esta app';

  @override
  String get termuxFixAllowOtherApps => 'Permitir otras apps en Termux';

  @override
  String get termuxFixOpenTermux => 'Abrir Termux';

  @override
  String termuxFixRestart(String runtime) {
    return 'Reiniciar $runtime en Termux';
  }

  @override
  String get termuxFixGetTermux => 'Obtener Termux';

  @override
  String get termuxFixGetCurrentTermux => 'Obtener el Termux actual';

  @override
  String get termuxOtherAppsTitle => 'Permitir otras apps';

  @override
  String get termuxOtherAppsBody =>
      'Pega esta línea en Termux y pulsa Intro; luego vuelve aquí. Abrir Termux la copia por ti.';

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
  String get termuxInAppInstead => 'Configurar mejor el servidor integrado';

  @override
  String get termuxInAppInsteadDetail =>
      'Un comienzo nuevo que se ejecuta dentro de esta app. No necesitas Termux.';

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
  String get manageSpaceLostServer => 'On this phone and its conversations';

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
  String get manageSpaceServer => 'On this phone';

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
  String get projectExportNoProjects => 'No projects in On this phone yet';

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
      'La demo no tiene comandos: envía el mensaje de ejemplo para ver una revisión de cambios.';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Sesión iniciada en $providers, pero este servidor no pudo cargar esos inicios de sesión ni tras recargar, así que sus modelos no pueden responder. Los inicios de sesión por navegador de algunos proveedores, como Anthropic y Google, no se cargan en este servidor. Agrega una clave de API en Proveedores o elige otro modelo.',
      one:
          'Sesión iniciada en $providers, pero este servidor no pudo cargar ese inicio de sesión ni tras recargar, así que sus modelos no pueden responder. Los inicios de sesión por navegador de algunos proveedores, como Anthropic y Google, no se cargan en este servidor. Agrega una clave de API en Proveedores o elige otro modelo.',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'La recarga espera a que terminen $count respuestas en curso, porque recargar las detendría.',
      one:
          'La recarga espera a que termine 1 respuesta en curso, porque recargar la detendría.',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      'Usas el modelo gratuito de OpenCode, que es más lento. Agrega una clave API de tu proveedor para usar el tuyo.';

  @override
  String get chatFreeModelNote => 'Free model, slower';

  @override
  String get chatFreeModelAddKey => 'Add your key';

  @override
  String get freeModelSignIn => 'Agregar una clave API';

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
  String get pickerConnectProvider => 'Conectar un proveedor';

  @override
  String get pickerConnectProviderHint =>
      'Agrega una clave API o inicia sesión para usar sus modelos';

  @override
  String pickerAddKeyFor(String name) {
    return 'Agregar una clave API para $name';
  }

  @override
  String get pickerAddKeyNotConnectedHint =>
      'Sus modelos aún no están en esta lista';

  @override
  String pickerSignInTo(String name) {
    return 'Iniciar sesión en $name';
  }

  @override
  String get pickerSignInHint =>
      'Abre las opciones de inicio de sesión de este servidor';

  @override
  String get pickerFreeOnlyNote =>
      'Solo está disponible el modelo gratuito de OpenCode, que es más lento.';

  @override
  String pickerProviderReady(String name) {
    return '$name está listo. Sus modelos están en la lista.';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return '$name está guardado, pero el servidor aún no lo ha cargado.';
  }

  @override
  String get integrationsSignedInUnusable =>
      'Sesión iniciada, pero este servidor no puede usarla';

  @override
  String get chatUiCompactConfirmTitle => '¿Compactar esta conversación?';

  @override
  String get chatUiCompactConfirmBody =>
      'Compactar reemplaza los mensajes anteriores con un resumen corto para ahorrar espacio. No se puede deshacer.';

  @override
  String get chatUiCompactConfirmAction => 'Compactar conversación';

  @override
  String get kitTurnReconnecting =>
      'Se perdió la conexión. Reconectando para recibir el resto de esta respuesta.';

  @override
  String get kitTurnLiveSending => 'Enviando';

  @override
  String get kitTurnLiveWaitingForServer => 'Esperando al servidor';

  @override
  String get kitTurnLiveServerQuiet => 'El servidor aún no ha respondido';

  @override
  String get kitTurnLiveThinking => 'Pensando';

  @override
  String get kitTurnLiveFirstWordSlow =>
      'Esperando la primera palabra del modelo';

  @override
  String get kitTurnLiveFirstWordSlowTeam =>
      'AI Team también está trabajando en este teléfono, así que las respuestas pueden tardar más';

  @override
  String get kitTurnLiveWriting => 'Escribiendo';

  @override
  String get kitTurnLiveWorking => 'Trabajando';

  @override
  String get kitTurnLiveWaitingForYou => 'Esperándote';

  @override
  String get kitTurnLiveStop => 'Detener respuesta';

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
  String get composerFieldLabel => 'Mensaje para el agente';

  @override
  String get e7WorkspaceYesterday => 'Ayer';

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
  String get chatCollapseAllSteps => 'Contraer todos los pasos';

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
  String get kitComposerPillNoAnswer => 'Aún sin respuesta';

  @override
  String get kitComposerRailRetry => 'Intentar de nuevo';

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
  String teamProjectPlanningElapsed(int minutes) {
    return 'Shaping the spec · $minutes min';
  }

  @override
  String teamProjectPlanningSlow(int minutes) {
    return 'Still shaping the spec · $minutes min';
  }

  @override
  String teamProjectAllTasksDone(int total) {
    return 'All $total tasks complete';
  }

  @override
  String teamProjectProgress(int done, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working working',
      zero: '',
    );
    return '$done of $total tasks complete$_temp0';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy of $total lanes busy';
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
  String get teamProjectTryDemo => 'Probar la demo de AI Team';

  @override
  String get teamProjectLoadFailure =>
      'The demo could not be opened. Your saved project data has been kept.';

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
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working working',
      zero: '',
    );
    return 'Milestone $current of $total$_temp0';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return 'All $total milestones done';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones milestones',
      one: '1 milestone',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return 'Spec approved $age · $_temp0 · $_temp1';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones milestones',
      one: '1 milestone',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return 'Draft spec · $_temp0 · $_temp1';
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
      'Open the Planner model choice and check it, then ask again.';

  @override
  String get teamRefusalModelNotConfigured =>
      'The model for this job didn\'t accept the request.';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      'It may need a sign-in, or it may not be free to use here. Open the Planner model choice, pick another model, then ask again.';

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
  String get teamRefusalModelNotConfiguredAction => 'Choose the Planner model';

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
      'Esta dirección de red privada o Tailscale usa HTTP sin cifrar. Tu contraseña y código viajan sin cifrar en la capa HTTP. Úsala solo en una red privada de confianza o con Tailscale; es arriesgado en Wi-Fi público.';

  @override
  String get addServerCleartextConfirm => 'Usar de todos modos';

  @override
  String get addServerCleartextConfirmed =>
      'HTTP simple está activado para esta dirección. Cualquiera en esta red podría leer lo que envías.';

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
  String get storageAccessAllow => 'Permitir acceso a los archivos';

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
  String get storageTermuxAllow => 'Abrir Termux';

  @override
  String get storageTermuxStillTitle => 'Allow storage in Termux';

  @override
  String get storageTermuxStillBody =>
      'In Termux, allow storage when Android asks (the command is termux-setup-storage), then open the folder again.';

  @override
  String get filesAccessNeededTitle => 'Archivos ocultos';

  @override
  String get filesAccessNeededBody =>
      'Esta carpeta está en el almacenamiento compartido del teléfono y la app aún no puede leer la mayoría de sus archivos. Solo se muestran los elementos ocultos.';

  @override
  String get filesAccessNeededTermuxBody =>
      'Esta carpeta está en el almacenamiento compartido del teléfono y Termux aún no puede leer la mayoría de sus archivos. Solo se muestran los elementos ocultos.';

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

  @override
  String get kitQueuedChecking => 'Comprobando si tu mensaje llegó…';

  @override
  String get kitQueuedUncertain =>
      'No pudimos confirmar que tu mensaje llegara.';

  @override
  String kitQueuedLastChecked(String time) {
    return 'Última comprobación: $time.';
  }

  @override
  String get kitQueuedStorageFull =>
      'Este teléfono no puede registrar envíos de forma segura ahora, así que tu mensaje no se envió. Se conserva aquí.';

  @override
  String get queuedReceiptConfirmed => 'Message received.';

  @override
  String get queuedReceiptStillUncertain =>
      'Still can\'t confirm it arrived. Nothing was sent again.';

  @override
  String get queuedReceiptStorageProblem =>
      'This phone can\'t safely record or check sends right now. Free up some storage and try again.';

  @override
  String get queuedReceiptDetailSentAt => 'Enviado';

  @override
  String get queuedReceiptDetailCommand => 'ID del comando';

  @override
  String get queuedReceiptDetailReceipt => 'ID del recibo';

  @override
  String get queuedReceiptDetailNote =>
      'La conexión terminó antes de que pudiéramos confirmar la recepción.';

  @override
  String get kitToolRetry => 'Intentar de nuevo';

  @override
  String get folderBrowserPlaceLabel => 'Lugar';

  @override
  String get folderBrowserPlaceProjects => 'Espacio de proyectos';

  @override
  String get folderBrowserPlacePhone => 'Este teléfono';

  @override
  String get folderBrowserInternalStorage => 'Almacenamiento interno';

  @override
  String get folderBrowserOpenedBefore => 'Abiertos antes';

  @override
  String get folderBrowserShowHidden => 'Mostrar carpetas ocultas';

  @override
  String get folderBrowserHintEmpty => 'Vacía';

  @override
  String folderBrowserHintMore(String names, int count) {
    return '$names, $count más';
  }

  @override
  String get folderBrowserEmptyPhoneBody => 'Ábrela o sube un nivel.';

  @override
  String folderBrowserNewProjectCreates(String path) {
    return 'Crea $path';
  }

  @override
  String get folderBrowserCreateAndOpen => 'Crear y abrir';

  @override
  String get folderBrowserFoldersLabel => 'Folders';

  @override
  String get folderBrowserPhoneRefused =>
      'El almacenamiento del teléfono está desactivado. Permite el acceso para ver aquí.';

  @override
  String get phoneScanTitle => 'Proyectos en este teléfono';

  @override
  String phoneScanStopped(int seconds, int count) {
    return 'Detenido tras $seconds segundos · $count encontrados';
  }

  @override
  String phoneScanFirstShown(int count) {
    return 'Se muestran los primeros $count';
  }

  @override
  String get phoneScanLookDeeper => 'Buscar más a fondo';

  @override
  String get phoneScanEmptyTitle => 'No se encontraron proyectos';

  @override
  String get phoneScanEmptyBody =>
      'Explora carpetas o empieza un proyecto nuevo.';

  @override
  String get phoneScanGit => 'Git';

  @override
  String get phoneScanKindDart => 'Dart';

  @override
  String get phoneScanKindNode => 'Node';

  @override
  String get phoneScanKindPython => 'Python';

  @override
  String get phoneScanKindRust => 'Rust';

  @override
  String get phoneScanKindGo => 'Go';

  @override
  String get phoneScanKindJava => 'Java';

  @override
  String get phoneScanKindRuby => 'Ruby';

  @override
  String get phoneScanKindPhp => 'PHP';

  @override
  String get phoneScanKindDotnet => '.NET';

  @override
  String get phoneScanKindCpp => 'C/C++';

  @override
  String get phoneScanKindGit => 'Folder';

  @override
  String get openProjectTitle => 'Abrir un proyecto';

  @override
  String get openProjectSearchPhone => 'Buscar en este teléfono';

  @override
  String get openProjectChooseFolder => 'Elegir una carpeta';

  @override
  String get openProjectChangeFolder => 'Cambiar carpeta';

  @override
  String openProjectUseFolder(String name) {
    return 'Usar $name';
  }

  @override
  String get phoneScanSearching => 'Buscando en este teléfono';

  @override
  String phoneScanChecked(int folders, int found) {
    String _temp0 = intl.Intl.pluralLogic(
      folders,
      locale: localeName,
      other: '$folders carpetas revisadas',
      one: '1 carpeta revisada',
    );
    return '$_temp0 · $found encontrados';
  }

  @override
  String phoneScanDoneIn(int count, String time) {
    return '$count encontrados en $time';
  }

  @override
  String get phoneScanStop => 'Detener';

  @override
  String get openProjectChange => 'Cambiar';

  @override
  String openProjectIn(String folder) {
    return 'En $folder';
  }

  @override
  String get effectsGlowStyle => 'Estilo';

  @override
  String get effectsGlowStyleClassic => 'Clásico';

  @override
  String get effectsGlowStyleSoft => 'Anillo suave';

  @override
  String get effectsGlowColours => 'Colores';

  @override
  String get effectsGlowSpeed => 'Velocidad';

  @override
  String get approvalModeAskDetail => 'Tú respondes a cada solicitud.';

  @override
  String get approvalModeConfirmEverythingBody =>
      'Los agentes de este servidor ejecutarán comandos y cambiarán archivos sin preguntar, en todas las conversaciones.';

  @override
  String get approvalsSheetSubagents => 'Los subagentes siguen esto';

  @override
  String approvalsSheetSetByParent(String name) {
    return 'Definido por $name';
  }

  @override
  String get approvalsSheetSetByServer => 'Definido por este servidor';

  @override
  String get approvalsSheetFootnote =>
      'Se detiene cuando la app se desconecta. Las reglas de denegación del servidor siguen aplicándose.';

  @override
  String get approvalsSheetHistory => 'Aprobado automáticamente';

  @override
  String get chatsHomeTitle => 'Conversaciones';

  @override
  String get chatsHomeAllProjects => 'Todos los proyectos';

  @override
  String get chatsHomeNeedsYou => 'Te necesita';

  @override
  String get chatsHomeRunning => 'En curso';

  @override
  String get chatsHomeRowWorking => 'Trabajando';

  @override
  String get chatsHomeStopped => 'Stopped';

  @override
  String get chatsHomeDone => 'Listo';

  @override
  String get chatsHomeFailed => 'Fallida';

  @override
  String get chatsHomeToday => 'Hoy';

  @override
  String get chatsHomeEarlier => 'Anteriores';

  @override
  String chatsHomeOnlyProject(String project) {
    return 'Mostrando solo las conversaciones de $project. Este servidor no puede listar todos los proyectos.';
  }

  @override
  String get chatsHomeIncomplete =>
      'Algunas conversaciones no se pudieron cargar';

  @override
  String chatsHomeIncompleteNamed(String servers) {
    return '$servers couldn\'t load';
  }

  @override
  String shellServerPlusOthers(String name, int count) {
    return '$name +$count';
  }

  @override
  String get chatSubagentReadOnly =>
      'Este subagente solo responde en su conversación principal. Escribe allí.';

  @override
  String get chatsHomeOpening => 'Abriendo…';

  @override
  String chatsHomeUnreachable(String servers) {
    return '$servers no responde. No se muestran sus conversaciones.';
  }

  @override
  String get chatsSourcesUnreachable => 'No responde';

  @override
  String get chatsSourcesAll => 'Todas las conexiones';

  @override
  String chatsSourcesSome(int shown, int total) {
    return '$shown de $total conexiones';
  }

  @override
  String get chatsSourcesSheetTitle => 'Conexiones en esta lista';

  @override
  String get chatsSourcesMain => 'Las conversaciones nuevas empiezan aquí';

  @override
  String get chatsSourcesOnPhone => 'En este teléfono';

  @override
  String get chatsSourcesElsewhere => 'En otra computadora';

  @override
  String get chatsSourcesAgents => 'Agentes en este teléfono';

  @override
  String get chatsSourcesAlways => 'Siempre visible';

  @override
  String chatsHomeStillLoading(String agents) {
    return 'Cargando las conversaciones de $agents…';
  }

  @override
  String get chatsHomeEmptyTitle => 'Aún no hay conversaciones';

  @override
  String get chatsHomeNoMatchTitle => 'No hay conversaciones que coincidan';

  @override
  String get chatsHomeNoMatchBody => 'Nada coincide con los filtros elegidos.';

  @override
  String get chatsHomeClearFilters => 'Quitar filtros';

  @override
  String chatsHomeProjectEmptyTitle(String project) {
    return 'No hay conversaciones en $project';
  }

  @override
  String chatsHomeStartChatIn(String project) {
    return 'Empezar una conversación en $project';
  }

  @override
  String get chatsHomeNewChat => 'Nueva conversación';

  @override
  String get chatsHomeOpenFailed =>
      'No se pudo abrir esta conversación. Desliza hacia abajo para actualizar la lista.';

  @override
  String get chatsFilterSheetTitle => 'Mostrar conversaciones de';

  @override
  String chatsFilterChatCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count conversaciones',
      one: '1 conversación',
    );
    return '$_temp0';
  }

  @override
  String chatsFilterRunningCount(int running) {
    return '$running en curso';
  }

  @override
  String get chatsFilterNeedsYouWord => 'te necesita';

  @override
  String get chatsFilterOpenProject => 'Abrir un proyecto…';

  @override
  String get chatsNewSeparateCopy => 'En una copia aparte';

  @override
  String get agentPersonaClaudeCodeStrength => 'Careful with big changes';

  @override
  String get agentPersonaClaudeCodeAbout =>
      'Plans before it edits; good for larger changes across files.';

  @override
  String get agentPersonaClaudeCodeNeeds => 'A Claude plan or an Anthropic key';

  @override
  String get agentPersonaCodexStrength => 'Fast edits and reviews';

  @override
  String get agentPersonaCodexAbout =>
      'Quick at focused fixes and reviewing changes.';

  @override
  String get agentPersonaCodexNeeds => 'A ChatGPT plan or an OpenAI key';

  @override
  String get agentPersonaGeminiStrength => 'Reads a lot at once';

  @override
  String get agentPersonaGeminiAbout =>
      'Handles long files and wide questions well.';

  @override
  String get agentPersonaGeminiNeeds => 'A Google account or a Gemini key';

  @override
  String get agentPersonaKiloStrength => 'Your choice of provider';

  @override
  String get agentPersonaKiloAbout =>
      'One agent that works with many model providers.';

  @override
  String get agentPersonaKiloNeeds => 'A key from any supported provider';

  @override
  String get agentPersonaClineStrength => 'Step by step, asks first';

  @override
  String get agentPersonaClineAbout =>
      'Works in small steps and checks in with you.';

  @override
  String get agentPersonaClineNeeds => 'A key from any supported provider';

  @override
  String get agentPersonaOpenCodeStrength => 'Any model, free ones too';

  @override
  String get agentPersonaOpenCodeAbout =>
      'Works with any provider, and has free models to start.';

  @override
  String get agentPersonaOpenCodeNeeds =>
      'Nothing to start; add a key for more models';

  @override
  String get agentPersonaOtherName => 'Another agent';

  @override
  String get agentMakerAnthropic => 'by Anthropic';

  @override
  String get agentMakerOpenAI => 'by OpenAI';

  @override
  String get agentMakerGoogle => 'by Google';

  @override
  String get agentMakerOpenSource => 'open source';

  @override
  String chatsNewStartWith(String agent) {
    return 'Start with $agent';
  }

  @override
  String get chatsNewSetUp => 'Set up';

  @override
  String get chatsNewPrompt => '¿En qué trabajamos?';

  @override
  String get chatsNewChooseProject => 'Elegir un proyecto';

  @override
  String get chatsNewNeedProject =>
      'Elige un proyecto para empezar una conversación.';

  @override
  String get chatsNewFailed =>
      'La conversación no se inició. Tu mensaje sigue aquí.';

  @override
  String get chatsNewFailedTitle => 'The conversation didn\'t start';

  @override
  String get chatsNewFailedReportHint =>
      'Report a problem includes these details.';

  @override
  String get shellTabChats => 'Conversaciones';

  @override
  String get shellTabFiles => 'Archivos';

  @override
  String get discoverChatsAliases =>
      'chats conversations sessions work home recent pinned new conversation inbox activity needs you approvals permissions questions forms waiting running finished';

  @override
  String chatProjectChipSemantics(String name) {
    return 'Proyecto $name. Abre un menú con sus archivos, terminal y cambios.';
  }

  @override
  String chatProjectServerChipSemantics(String name, String server) {
    return 'Proyecto $name en $server. Abre un menú con sus archivos, terminal y cambios.';
  }

  @override
  String get chatProjectMenuLabel => 'Herramientas del proyecto';

  @override
  String get agentsChooseTitle => 'Elegir un agente';

  @override
  String get agentsStateReady => 'Listo';

  @override
  String get agentsStateCantReopen =>
      'No puede reabrir conversaciones antiguas';

  @override
  String agentsStateNotInstalled(String size) {
    return 'No instalado · $size';
  }

  @override
  String get agentsStateNotInstalledNoSize => 'No instalado';

  @override
  String get agentsStateSignInNeeded => 'Falta iniciar sesión';

  @override
  String get agentsStateChecking => 'Comprobando el inicio de sesión…';

  @override
  String get agentsStatePhoneCheck => 'Se necesita comprobar el teléfono';

  @override
  String get agentsStateStopped => 'Detenido en segundo plano';

  @override
  String get agentsStateLimit => 'Límite del plan alcanzado';

  @override
  String get agentsStateUnavailable => 'Aún no disponible en este teléfono';

  @override
  String agentsSetupTitle(String agent, String size) {
    return 'Configurar $agent · $size';
  }

  @override
  String agentsSetupTitleNoSize(String agent) {
    return 'Configurar $agent';
  }

  @override
  String agentsSetupBody(String agent) {
    return '$agent se ejecuta en este teléfono. Al instalarlo se descarga el agente y lo que necesita.';
  }

  @override
  String agentsSetupSizeNote(String size) {
    return '$size es el agente en sí. Las partes compartidas pueden agregar más.';
  }

  @override
  String agentsInstallAction(String agent) {
    return 'Instalar $agent';
  }

  @override
  String get agentsCancelSetup => 'Cancelar la configuración';

  @override
  String agentsInstalling(String agent) {
    return 'Instalando $agent…';
  }

  @override
  String get agentsSetupInterrupted =>
      'La configuración se detuvo antes de terminar. Instala de nuevo para continuar.';

  @override
  String get agentsSetupFailed =>
      'La configuración no terminó. Instala de nuevo para intentarlo otra vez.';

  @override
  String get agentsCheckTitle => 'Comprobar este teléfono';

  @override
  String agentsCheckRunning(String agent) {
    return 'Comprobando $agent…';
  }

  @override
  String agentsCheckPassed(String agent) {
    return '$agent está listo en este teléfono.';
  }

  @override
  String agentsCheckFailed(String agent) {
    return '$agent no pasó la comprobación.';
  }

  @override
  String agentsCheckAction(String agent) {
    return 'Comprobar $agent';
  }

  @override
  String get agentsStepInstall => 'Instalado';

  @override
  String get agentsStepVersion => 'Versión';

  @override
  String get agentsStepConnection => 'Conexión';

  @override
  String get agentsStepReady => 'Listo';

  @override
  String get agentsFailUnavailable =>
      'Este teléfono aún no puede ejecutar este agente.';

  @override
  String agentsResumeFailed(String agent) {
    return '$agent didn\'t start again. Tap Resume to try once more. If it still won\'t start, run its phone check.';
  }

  @override
  String get agentsFailStorage =>
      'No se pudo guardar esta configuración. Libera algo de espacio y ejecútala de nuevo.';

  @override
  String get agentsFailInstall =>
      'El agente no se instaló. Instálalo de nuevo.';

  @override
  String get agentsFailInterrupted =>
      'La configuración se detuvo antes de terminar. Instálalo de nuevo.';

  @override
  String get agentsFailVersion =>
      'El agente instalado no pasó la comprobación de versión.';

  @override
  String get agentsFailDaemon => 'La conexión del agente no arrancó.';

  @override
  String get agentsFailHello =>
      'Paseo no confirmó la conexión. Reintenta para reiniciarla y comprobar de nuevo.';

  @override
  String get agentsFailArchitecture =>
      'Este agente no coincide con el procesador de este teléfono.';

  @override
  String get agentsFailStale =>
      'Esta comprobación está desactualizada. Ejecútala de nuevo.';

  @override
  String get agentsFailBusy =>
      'Hay otra configuración en curso. Espera a que termine.';

  @override
  String agentsSignInTitle(String agent) {
    return 'Iniciar sesión con $agent';
  }

  @override
  String agentsSignInIntro(String agent) {
    return '$agent inicia sesión con sus propios pasos, con tu propia cuenta de Claude y según los términos de Anthropic. La app nunca ve tu inicio de sesión.';
  }

  @override
  String agentsSignInTerminalIntro(String agent) {
    return '$agent abre su página de inicio de sesión. Inicia sesión allí con tu cuenta de Claude y vuelve. Si $agent pide un código, cópialo en la página y elige Pegar.';
  }

  @override
  String agentsSignInIntroOther(String agent) {
    return '$agent inicia sesión con sus propios pasos, con tu propia cuenta de su proveedor. La app nunca ve tu inicio de sesión.';
  }

  @override
  String agentsSignInTerminalIntroOther(String agent) {
    return '$agent inicia sesión aquí con sus propios pasos. Si abre una página, inicia sesión allí y vuelve. Si pide un código o una clave, elige Pegar.';
  }

  @override
  String get agentsSignInAgain => 'Iniciar sesión de nuevo';

  @override
  String agentsSignInTerminalNotYet(String agent) {
    return '$agent aún no ha iniciado sesión. Vuelve a iniciar el proceso cuando quieras.';
  }

  @override
  String get agentsSignInStopFailed =>
      'No se pudo detener el inicio de sesión. Mantén la app abierta e inténtalo de nuevo.';

  @override
  String get agentsSignInChecking => 'Comprobando el inicio de sesión…';

  @override
  String agentsSignInStart(String agent) {
    return 'Iniciar sesión con $agent';
  }

  @override
  String get agentsSignedIn => 'Sesión iniciada';

  @override
  String agentsSignedInBody(String agent) {
    return '$agent está listo para conversaciones nuevas.';
  }

  @override
  String agentsSignInDone(String agent) {
    return 'Usar $agent';
  }

  @override
  String agentsSignedInAs(String account) {
    return 'Sesión iniciada como $account';
  }

  @override
  String agentsSignOutAction(String agent) {
    return 'Cerrar sesión en $agent';
  }

  @override
  String agentsSignOutTitle(String agent) {
    return '¿Cerrar sesión en $agent?';
  }

  @override
  String agentsSignOutBody(String agent) {
    return '$agent no podrá iniciar conversaciones nuevas hasta que vuelvas a iniciar sesión.';
  }

  @override
  String agentsSignOutKept(String agent) {
    return 'Tus conversaciones con $agent se conservan.';
  }

  @override
  String agentsSignOutFailed(String agent) {
    return 'No se pudo confirmar que $agent cerró sesión. Inténtalo de nuevo.';
  }

  @override
  String agentsRemoveAction(String agent) {
    return 'Quitar $agent';
  }

  @override
  String agentsRemoveTitle(String agent) {
    return '¿Quitar $agent?';
  }

  @override
  String get agentsRemoveBody =>
      'Esto quita el agente instalado de este teléfono. Tus cuentas y conversaciones se conservan, y puedes instalarlo de nuevo.';

  @override
  String agentsRemoving(String agent) {
    return 'Quitando $agent…';
  }

  @override
  String agentsRemoved(String agent, String size) {
    return '$agent quitado. Se liberaron $size.';
  }

  @override
  String agentsAlreadyRemoved(String agent) {
    return '$agent ya está quitado.';
  }

  @override
  String get agentsRemoveUnsupported => 'Este agente no se puede quitar aquí.';

  @override
  String get agentsRemoveBusy =>
      'Este agente está en uso. Espera a que termine su trabajo e inténtalo de nuevo.';

  @override
  String get agentsRemoveUnconfirmed =>
      'No se pudo confirmar que el agente se quitó. Comprueba este teléfono e inténtalo de nuevo.';

  @override
  String get agentsDone => 'Listo';

  @override
  String get agentsModelTitle => 'Elegir un modelo';

  @override
  String get agentsModelDefault => 'Modelo predeterminado';

  @override
  String get agentsModelLoading => 'Leyendo los modelos…';

  @override
  String get agentsSignInBadCode =>
      'Ese código está incompleto. Copia el código completo del navegador (tiene un # en medio) y pégalo de nuevo.';

  @override
  String get agentsSignInFailed =>
      'El inicio de sesión no terminó. Vuelve a iniciarlo.';

  @override
  String agentsSignInRejected(String agent) {
    return '$agent no aceptó ese código. Puede que haya caducado o ya se haya usado. Toca Obtener un código nuevo.';
  }

  @override
  String get agentsSignInHostDown =>
      'El agente no está en ejecución. Reanúdalo y luego inicia sesión.';

  @override
  String get agentsSignInUnavailable =>
      'El inicio de sesión aún no está listo en este teléfono';

  @override
  String agentsSignInLimit(String agent) {
    return 'Límite del plan de $agent alcanzado · inténtalo más tarde';
  }

  @override
  String agentsLimitReset(String agent, String time) {
    return 'Límite del plan de $agent alcanzado · se restablece $time';
  }

  @override
  String agentsLimitUnknown(String agent) {
    return 'Se alcanzó el límite del plan de $agent · inténtalo más tarde';
  }

  @override
  String agentsSignedOutLine(String agent) {
    return '$agent cerró sesión';
  }

  @override
  String agentsSignInAction(String agent) {
    return 'Iniciar sesión en $agent';
  }

  @override
  String agentsStoppedLine(String agent) {
    return '$agent se detuvo en segundo plano';
  }

  @override
  String agentsResumeAction(String agent) {
    return 'Reanudar $agent';
  }

  @override
  String get agentsResumeNoticeTitle => '¿Empezar una conversación nueva?';

  @override
  String agentsResumeNoticeBody(String agent) {
    return 'Empieza una conversación nueva. $agent no puede reabrir esta, y se queda como está.';
  }

  @override
  String get agentsStartNew => 'Empezar conversación nueva';

  @override
  String get agentsCancel => 'Cancelar';

  @override
  String get agentsRestartLine =>
      'Cierra y vuelve a abrir la app para terminar de borrar los inicios de sesión.';

  @override
  String get agentsRestartAction => 'Cerrar la app';

  @override
  String get agentsSectionTitle => 'Agentes';

  @override
  String get agentsRunOnBuiltIn =>
      'Los agentes se ejecutan en el servidor integrado';

  @override
  String get agentsSwitchToBuiltIn => 'Cambiar al servidor integrado';

  @override
  String get agentsChecking => 'Buscando agentes en este teléfono…';

  @override
  String get agentsStateNeedsArm => 'Requiere un teléfono de 64 bits';

  @override
  String get agentsStateNoDownload => 'Aún no hay descarga verificada';

  @override
  String get agentsInstallHint => 'Instalar';

  @override
  String get agentsActionFailed =>
      'Eso no funcionó en este teléfono. Abre Detalles para ver por qué.';

  @override
  String get chatsHomeOtherFolders => 'Otras carpetas';

  @override
  String get agentsSignInPaste => 'Pegar';

  @override
  String get chatRequestAnswerAllowed => 'Permitido';

  @override
  String get chatRequestAnswerRejected => 'Rechazado';

  @override
  String agentCardAsks(String agent) {
    return '$agent asks';
  }

  @override
  String agentCardReports(String agent) {
    return '$agent reports';
  }

  @override
  String agentCardAsksAnnouncement(String agent, String title) {
    return '$agent asks: $title';
  }

  @override
  String get agentCardSend => 'Send';

  @override
  String get agentCardConfirmDefault => 'Yes, go ahead';

  @override
  String get agentCardCancelDefault => 'Cancel';

  @override
  String get agentCardChoosePrompt => 'Choose an option to send.';

  @override
  String get agentCardChooseOneOrMore => 'Choose at least one option to send.';

  @override
  String get agentCardFormFix => 'Fill in the required fields to send.';

  @override
  String get agentCardFieldRequired => 'This field is required.';

  @override
  String get agentCardFieldNumber => 'Enter a number.';

  @override
  String agentCardFieldMin(String min) {
    return 'Enter $min or more.';
  }

  @override
  String agentCardFieldMax(String max) {
    return 'Enter $max or less.';
  }

  @override
  String get agentCardTextNote =>
      'The agent sees this, and it is saved in the conversation.';

  @override
  String get agentCardBusy =>
      'The agent is still working. You can answer when it stops.';

  @override
  String get agentCardSending => 'Sending your answer…';

  @override
  String get agentCardUnknown =>
      'We can\'t tell yet whether this card was answered. It updates when the conversation does.';

  @override
  String get agentCardDeliveryUnknown =>
      'We couldn\'t confirm that your answer arrived. Check the conversation before answering again.';

  @override
  String get agentCardFailed =>
      'Your answer didn\'t go through. Nothing was sent, and you can try again.';

  @override
  String get agentCardSent => 'Sent';

  @override
  String get agentCardShow => 'Show the card';

  @override
  String get agentCardNotAnswered => 'Not answered';

  @override
  String get agentCardUnreadable => 'This card couldn\'t be shown';

  @override
  String get agentCardDetails => 'Details';

  @override
  String get agentCardComposerHint => 'O escribe tu respuesta';

  @override
  String get agentCardDangerTitle => 'Send this answer?';

  @override
  String get agentCardDangerBody =>
      'Your answer goes to the agent as a message. The agent decides what to do with it.';

  @override
  String get agentCardPhotoTake => 'Take photo';

  @override
  String get agentCardPhotoChoose => 'Choose photo';

  @override
  String agentCardPhotoRemove(int number) {
    return 'Remove photo $number';
  }

  @override
  String agentCardPhotoName(int number) {
    return 'Photo $number';
  }

  @override
  String agentCardPhotoCount(int count, int max) {
    return '$count of $max';
  }

  @override
  String get agentCardPhotoMax => 'That\'s all the photos this card takes.';

  @override
  String get agentCardPhotoNone => 'Add a photo to send.';

  @override
  String get agentCardPhotoUnavailable =>
      'Photos can\'t be added from here. Type your answer instead.';

  @override
  String get agentCardPhotoTooLarge =>
      'Photos can be up to 10 MB each and 20 MB together.';

  @override
  String get agentCardChartBar => 'Bar chart';

  @override
  String get agentCardChartLine => 'Line chart';

  @override
  String agentCardChartSeries(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count series',
      one: '1 series',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartPoints(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count points',
      one: '1 point',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartHighest(String where, String value) {
    return 'highest $where: $value';
  }

  @override
  String get agentCardChartNone => 'no data';

  @override
  String agentCardTableLabel(int columns, int rows) {
    return 'Table, $columns columns, $rows rows';
  }

  @override
  String get agentCardDiffFile => 'File';

  @override
  String get agentCardDiffAdded => 'Added';

  @override
  String get agentCardDiffRemoved => 'Removed';

  @override
  String agentCardDiffMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'and $count more files',
      one: 'and 1 more file',
    );
    return '$_temp0';
  }

  @override
  String agentCardProgressValue(int percent) {
    return '$percent%';
  }

  @override
  String get cardsFromAgentsTitle => 'Cards from agents';

  @override
  String get cardsFromAgentsSupporting =>
      'Agents can show you choices, forms and reports as cards, and ask for a photo.';

  @override
  String get cardsStatusOff => 'Off';

  @override
  String get cardsStatusChecking => 'Checking this phone…';

  @override
  String cardsStatusOn(String agents) {
    return 'On for $agents';
  }

  @override
  String cardsStatusPartial(String agents, String reason) {
    return 'On for $agents. $reason';
  }

  @override
  String cardsStatusRestart(String agents) {
    return 'Restart $agents to finish turning this on.';
  }

  @override
  String cardsStatusUnavailable(String reason) {
    return 'Not available. $reason';
  }

  @override
  String cardsStatusFailed(String reason) {
    return 'Couldn\'t turn this on. $reason';
  }

  @override
  String get cardsSettingFailed => 'Couldn\'t change this setting. Try again.';

  @override
  String get cardsAgentClaude => 'Claude Code';

  @override
  String get cardsProblemUnsupportedHost =>
      'Agents on this server can\'t show cards.';

  @override
  String get cardsProblemRuntimeMissing =>
      'The tools cards need aren\'t on this phone yet.';

  @override
  String get cardsProblemNotQualified =>
      'Cards haven\'t been checked to work with the agents here yet.';

  @override
  String get cardsProblemPermissionDenied =>
      'This phone wouldn\'t allow the change.';

  @override
  String get cardsProblemConflict =>
      'Another setup already uses the same name. Nothing was changed.';

  @override
  String get cardsProblemInstallationFailed => 'Cards couldn\'t be installed.';

  @override
  String get cardsProblemRegistrationFailed =>
      'The agent couldn\'t be told about cards.';

  @override
  String get cardsProblemVerificationFailed =>
      'Cards were installed but didn\'t pass the check.';

  @override
  String get cardsProblemRemovalFailed =>
      'Cards couldn\'t be fully removed. Try again after restarting the agent.';

  @override
  String get cardsProblemStorageFailed =>
      'The phone couldn\'t save the setting.';

  @override
  String get cardsProblemBusy =>
      'The phone is busy with something else. Try again in a moment.';

  @override
  String get agentCardFieldRequiredHint => 'Required';

  @override
  String get agentCardYourAnswer => 'Your answer';

  @override
  String get agentCardDangerConfirm => 'Send answer';

  @override
  String get agentsSignInOpenPage => 'Abrir página de inicio de sesión';

  @override
  String agentsSignInCopyCode(String code) {
    return 'Copiar código $code';
  }

  @override
  String cardsProblemNotQualifiedFor(int count, String agents) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$agents haven\'t been checked to work with cards yet.',
      one: '$agents hasn\'t been checked to work with cards yet.',
    );
    return '$_temp0';
  }

  @override
  String cardsProblemRuntimeMissingFor(String agents) {
    return 'Install $agents on this phone first.';
  }

  @override
  String cardsProblemRegistrationFailedFor(String agents) {
    return '$agents couldn\'t be told about cards.';
  }

  @override
  String cardsProblemVerificationFailedFor(String agents) {
    return 'Cards were installed for $agents but didn\'t pass the check.';
  }

  @override
  String cardsProblemRemovalFailedFor(String agents) {
    return 'Cards couldn\'t be fully removed from $agents. Try again after a restart.';
  }

  @override
  String get crashReportsLabel => 'Crash reports';

  @override
  String get crashReportsSwitch => 'Save crash reports on this phone';

  @override
  String get crashReportsSwitchBody =>
      'Kept on this phone. Never sent automatically.';

  @override
  String get crashReportsUnavailable =>
      'Crash reports aren\'t available right now. Restart the app and try again.';

  @override
  String get crashReportsFailed =>
      'Couldn\'t update crash reports. Restart the app and try again.';

  @override
  String get crashReportsNone => 'No crash reports yet';

  @override
  String get crashReportsNoneBody =>
      'One appears here if the app closes or stops responding.';

  @override
  String get crashKindError => 'The app hit an unexpected error';

  @override
  String get crashKindScreen => 'A screen couldn\'t be shown';

  @override
  String get crashKindClosed => 'The app closed unexpectedly';

  @override
  String get crashKindNotResponding => 'The app stopped responding';

  @override
  String get crashReportPreviewBody =>
      'Kept on this phone only. It\'s sent only if you include saved errors in a problem report yourself.';

  @override
  String get crashReportDetailSource => 'Source';

  @override
  String get crashReportDetailCategory => 'Category';

  @override
  String crashReportsDelete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count saved crash reports',
      one: 'Delete 1 saved crash report',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsDeleteTitle => 'Delete saved crash reports?';

  @override
  String get crashReportsDeleteBody =>
      'Deletes the saved crash reports. Saving crash reports stays on.';

  @override
  String get crashReportsDeleteConfirm => 'Delete crash reports';

  @override
  String get crashReportsNote =>
      'Only what kind of problem happened and when is kept: no error messages, conversations or passwords. Keeps the latest 20 reports.';

  @override
  String crashReportsOnClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Turning this on clears the $count saved errors above.',
      one: 'Turning this on clears the saved error above.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsOffDeletes =>
      'Turning this off deletes the saved crash reports.';

  @override
  String crashReportsOffDeletesAndClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Turning this off deletes the saved crash reports and clears the $count saved errors above.',
      one:
          'Turning this off deletes the saved crash reports and clears the saved error above.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsOffClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Turning this off clears the $count saved errors above.',
      one: 'Turning this off clears the saved error above.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsDeleteBodyWithErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Deletes the crash reports and clears the $count saved errors above. Saving crash reports stays on.',
      one:
          'Deletes the crash reports and clears the saved error above. Saving crash reports stays on.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsClearedToo =>
      'The saved crash reports are deleted too.';

  @override
  String get recentErrorScreen => 'A screen couldn\'t be drawn';

  @override
  String get recentErrorConnection => 'Lost the live connection to the server';

  @override
  String get recentErrorAndroidExit => 'Android closed the app';

  @override
  String get recentErrorTemperature => 'Phone temperature changed';

  @override
  String get recentErrorStartup => 'The app had trouble starting';

  @override
  String get recentErrorReportStore => 'Couldn\'t open saved problem reports';

  @override
  String get recentErrorGeneric => 'Something went wrong';

  @override
  String get chatUiToolBackgroundTaskFinished => 'Background task finished';

  @override
  String get chatUiToolBackgroundTaskFailed => 'Background task failed';

  @override
  String get chatUiToolBackgroundTaskEnded => 'Background task ended';

  @override
  String get chatUiToolBackgroundTaskStopped => 'Background task stopped';

  @override
  String chatUiToolMessageTo(String name) {
    return 'Message to $name';
  }

  @override
  String get chatUiToolMessageToMain => 'Message to the main conversation';

  @override
  String get chatUiToolMessageToEveryone => 'Message to the whole team';

  @override
  String get chatUiToolMessageToAgent => 'Message to an agent';

  @override
  String get toolCardMessageRecipientId => 'Recipient id';

  @override
  String chatUiToolBackgroundNamedFinished(String name) {
    return '$name finished';
  }

  @override
  String chatUiToolBackgroundNamedFailed(String name) {
    return '$name failed';
  }

  @override
  String chatUiToolBackgroundNamedStopped(String name) {
    return '$name stopped';
  }

  @override
  String chatUiToolBackgroundNamedEnded(String name) {
    return '$name ended';
  }

  @override
  String chatUiToolBackgroundNamedCutOff(String name) {
    return '$name stopped when the last conversation ended';
  }

  @override
  String get chatUiToolBackgroundTaskCutOff =>
      'Background task stopped when the last conversation ended';

  @override
  String get chatUiToolShowCard => 'Show card';

  @override
  String get chatUiToolShowedCard => 'Mostró una tarjeta';

  @override
  String get chatUiToolCardFailed => 'No se pudo mostrar esta tarjeta';

  @override
  String get chatStallModelSlow =>
      'El modelo está tardando más de lo normal. Espera, o detén la respuesta e inténtalo de nuevo.';

  @override
  String get chatStallHelperDown =>
      'El ayudante del agente en este teléfono se detuvo. Detén la respuesta e inténtalo de nuevo.';

  @override
  String get chatStallConnectionLost =>
      'Se perdió la conexión con el agente. Detén la respuesta e inténtalo de nuevo cuando vuelva.';

  @override
  String get chatStallConnectionUnchecked =>
      'No ha llegado nada en un rato y no se pudo comprobar la conexión. Espera, o detén la respuesta e inténtalo de nuevo.';

  @override
  String connectionSwitchingTo(String target) {
    return 'Cambiando a $target…';
  }

  @override
  String get shellServerSwitching => 'Cambiando…';

  @override
  String get agentsStateNotCertified => 'Aún no certificado en esta versión';

  @override
  String get agentsChipSignIn => 'Iniciar sesión';

  @override
  String get agentsChipResume => 'Reanudar';

  @override
  String get agentsChipCheck => 'Comprobar';

  @override
  String agentNamesPair(String first, String second) {
    return '$first and $second';
  }

  @override
  String get diagnosticsExitHistoryTitle => 'Recent app exits';

  @override
  String get diagnosticsExitHistoryNote =>
      'Android records each time the app closes. Nothing here is sent automatically.';

  @override
  String get diagnosticsExitHistoryUnsupported => 'Not available on this phone';

  @override
  String get diagnosticsExitHistoryUnsupportedBody =>
      'Android 11 and later keep this record.';

  @override
  String get diagnosticsExitHistoryFailed =>
      'Couldn\'t read recent app exits. Try again, or reopen the app.';

  @override
  String get diagnosticsExitNormal => 'App closed';

  @override
  String get diagnosticsExitUpdate => 'App updated';

  @override
  String get diagnosticsExitForceStop => 'App stopped';

  @override
  String get diagnosticsExitLowMemory => 'Phone needed memory';

  @override
  String get diagnosticsExitCrash => 'App stopped unexpectedly';

  @override
  String get diagnosticsExitKilled => 'Android ended the app';

  @override
  String get diagnosticsExitReasonCode => 'Reason code';

  @override
  String get diagnosticsExitImportance => 'Importance';

  @override
  String get diagnosticsExitSummary => 'Summary';

  @override
  String get diagnosticsReportOpen => 'Share saved crash reports';

  @override
  String get diagnosticsReportTitle => 'Preview crash report';

  @override
  String get diagnosticsReportPrivacy =>
      'Only error categories and times, no messages or conversations. Nothing leaves this phone until you tap Share report.';

  @override
  String diagnosticsReportSize(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reports · $size',
      one: '1 report · $size',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsReportShare => 'Share report';

  @override
  String get diagnosticsReportStale =>
      'Saved details changed. Check the new report before sharing.';

  @override
  String get diagnosticsReportShareFailed =>
      'Couldn\'t open sharing. Try again.';

  @override
  String get diagnosticsReportCaptureOff =>
      'Saved crash reports are off. Turn them on to save future app errors.';

  @override
  String get diagnosticsReportEmpty => 'No saved crash reports to share.';

  @override
  String get diagnosticsUnavailable =>
      'These details are unavailable. Reopen the app and try again.';

  @override
  String backgroundPauseTimeLimit(String at) {
    return 'Conexión en segundo plano en pausa $at para ahorrar batería.';
  }

  @override
  String get backgroundPauseRestricted =>
      'Conexión en segundo plano en pausa: el uso de batería está restringido.';

  @override
  String backgroundPauseUserStopped(String at) {
    return 'La conexión en segundo plano se detuvo al cerrar la app $at.';
  }

  @override
  String backgroundPauseAppUpdated(String at) {
    return 'La conexión en segundo plano se detuvo al actualizar la app $at.';
  }

  @override
  String get backgroundPauseInterrupted =>
      'La conexión en segundo plano se detuvo por un motivo desconocido.';

  @override
  String get backgroundPauseDeviceRestarted =>
      'Background connection stopped when the phone restarted.';

  @override
  String get backgroundPauseResume => 'Reanudar la conexión en segundo plano';

  @override
  String get backgroundPauseResumeShort => 'Reanudar';

  @override
  String get backgroundPauseResuming =>
      'Reanudando la conexión en segundo plano…';

  @override
  String get backgroundPauseResumeFailed =>
      'No se pudo reanudar. Abre los ajustes de Mantener en ejecución.';

  @override
  String get backgroundPauseOpenKeepRunning =>
      'Abrir ajustes de Mantener en ejecución';

  @override
  String get backgroundPauseResumed => 'Conexión en segundo plano reanudada.';

  @override
  String get diagnosticsExitNoProblems => 'No unexpected closes recently.';

  @override
  String diagnosticsExitRoutine(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count routine closes (updates, you closed it)',
      one: '1 routine close (updates, you closed it)',
    );
    return '$_temp0';
  }

  @override
  String diagnosticsExitShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Show all $count',
      one: 'Show all 1',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsExitUnknown => 'Closed for an unknown reason';

  @override
  String diagnosticsExitToday(String time) {
    return 'Today $time';
  }

  @override
  String diagnosticsExitYesterday(String time) {
    return 'Yesterday $time';
  }

  @override
  String get liveToolCommand => 'Running a command…';

  @override
  String get liveToolEdit => 'Editing files…';

  @override
  String get liveToolRead => 'Reading files…';

  @override
  String get liveToolSearch => 'Searching files…';

  @override
  String get liveToolWeb => 'Browsing the web…';

  @override
  String get liveToolSubagent => 'Running a subagent…';

  @override
  String get liveToolPlan => 'Planning…';

  @override
  String get liveToolWorking => 'Trabajando…';

  @override
  String get liveToolCard => 'Showing a card…';

  @override
  String get liveToolBackgroundTask => 'Checking a background task…';

  @override
  String liveToolOther(String tool) {
    return 'Running $tool…';
  }

  @override
  String get e7LocaleUiJapanese => '日本語';

  @override
  String get e7LocaleUiChinese => '简体中文';

  @override
  String get e7LocaleUiSpanish => 'Español';

  @override
  String get e7LocaleUiPortuguese => 'Português (Brasil)';

  @override
  String get e7LocaleUiRussian => 'Русский';

  @override
  String connectorCardSuggests(String agent) {
    return '$agent suggests a connector';
  }

  @override
  String connectorCardAnnouncement(String agent, String name) {
    return '$agent suggests a connector: $name';
  }

  @override
  String get connectorCardRuns => 'Runs';

  @override
  String get connectorCardLasts => 'Lasts';

  @override
  String get connectorCardLastsValue => 'While this server runs';

  @override
  String get connectorCardRunsNode => 'On this server, with Node';

  @override
  String get connectorCardRunsPython => 'On this server, with Python';

  @override
  String get connectorCardRunsDocker => 'In Docker, which the app cannot start';

  @override
  String get connectorCardRunsNone => 'Nothing the app can start';

  @override
  String get connectorCardRunsHosted => 'Hosted';

  @override
  String get connectorCardConnect => 'Connect';

  @override
  String get connectorCardConnecting => 'Connecting…';

  @override
  String get connectorCardChecking => 'Checking the connection…';

  @override
  String get connectorCardSignInNeeded =>
      'This connector needs you to sign in.';

  @override
  String get connectorCardSignIn => 'Sign in';

  @override
  String get connectorCardOpenSignIn => 'Open sign-in page';

  @override
  String get connectorCardSignInWaiting =>
      'Waiting for sign-in to finish in your browser.';

  @override
  String get connectorCardManualCode =>
      'Paste the browser return URL or authorization code to finish sign-in.';

  @override
  String get connectorCardCodeLabel => 'Return URL or code';

  @override
  String get connectorCardCodeRequired => 'Paste the return URL or code first.';

  @override
  String get connectorCardFinishSignIn => 'Finish sign-in';

  @override
  String get connectorCardCheckStatus => 'Refresh';

  @override
  String get connectorCardCancelSignIn => 'Cancel sign-in';

  @override
  String get connectorCardCancelTitle => 'Cancel sign-in?';

  @override
  String connectorCardCancelBody(String name) {
    return '$name stops waiting, and its saved sign-in is cleared. You would sign in again to use it.';
  }

  @override
  String get connectorCardKeepWaiting => 'Keep waiting';

  @override
  String get connectorCardConnected => 'Connected';

  @override
  String get connectorCardToolsReady => 'Tools ready';

  @override
  String get connectorCardLoadedTools =>
      'Loaded tools. Continue this conversation to use them.';

  @override
  String get connectorCardConnectedUnconfirmed =>
      'Connected. Tool availability has not been confirmed.';

  @override
  String get connectorCardFailureUnavailable =>
      'This agent cannot connect tools from a conversation.';

  @override
  String get connectorCardFailureInvalidSuggestion =>
      'This connector suggestion is not available. Browse connectors in Tools.';

  @override
  String get connectorCardFailureSetupRequired =>
      'This connector needs setup in Tools before it can connect.';

  @override
  String get connectorCardFailureNameConflict =>
      'A connector with this name already exists. Check it in Tools.';

  @override
  String get connectorCardFailureSourceChanged =>
      'This conversation\'s connection changed. Reopen the connector card.';

  @override
  String get connectorCardFailureConnectFailed =>
      'Could not confirm the connection. Check its status before trying again.';

  @override
  String get connectorCardFailureAuthenticationFailed =>
      'Sign-in could not be confirmed. Check its status before trying again.';

  @override
  String get connectorCardFailureOauthUnavailable =>
      'This connector cannot sign in from a conversation.';

  @override
  String get connectorCardFailureNotConnected =>
      'The connector is not connected yet. Check its setup in Tools.';

  @override
  String get connectorCardOpenTools => 'Open connectors in Tools';

  @override
  String get connectorCardTryAgain => 'Try again';

  @override
  String get connectorCardReopen => 'Reopen card';

  @override
  String get chatUiToolSearchedConnectors => 'Searched connectors';

  @override
  String get chatUiConnectorSearchEmpty =>
      'No connectors found in the loaded catalogue.';

  @override
  String get chatUiConnectorSearchNotLoaded =>
      'The catalogue could not be loaded right now. Ask again in a moment.';

  @override
  String get chatUiConnectorSearchOff => 'Catalogue is off';

  @override
  String get chatUiConnectorSearchOffBody =>
      'The connector catalogue is a public list of connectors. Turning it on downloads that list and sends your search words to the public registry; nothing gets connected.';

  @override
  String get chatUiConnectorSearchTurnOn => 'Turn on connector catalogue';

  @override
  String get chatUiConnectorSearchTurnedOn =>
      'Catalogue is on. Ask the agent to search again.';

  @override
  String get chatUiConnectorSearchTurnOnFailed =>
      'The catalogue could not be turned on. Try again.';

  @override
  String get chatUiConnectorSearchUnavailable =>
      'Connector search is unavailable. Try again from Tools.';

  @override
  String get chatUiConnectorSearchNotConnected => 'Not connected';

  @override
  String get kitMarkdownImage => 'Image';

  @override
  String get toolCardPlan => 'Plan';

  @override
  String get toolCardWorktreeSetup => 'Setting up a separate copy';

  @override
  String get toolCardWhatItDid => 'What it did';

  @override
  String get toolCardTechnicalDetails => 'Detalles técnicos';

  @override
  String toolCardPageStatus(String status) {
    return 'Page answered $status';
  }

  @override
  String toolCardAsked(String prompt) {
    return 'Asked: $prompt';
  }

  @override
  String get chatUiAgentNote => 'Nota';

  @override
  String get chatUiAgentWarning => 'Advertencia';

  @override
  String get chatUiAgentProblem => 'Problema';

  @override
  String get chatUiCompactedAutomatically =>
      'Se compactó automáticamente para hacer espacio.';

  @override
  String get chatUiCompactedWhenAsked => 'Se compactó cuando lo pediste.';

  @override
  String chatUiCompactedFrom(String tokens) {
    return 'Tenía unos $tokens tokens.';
  }

  @override
  String get chatRequestTitleFetch => 'Abrir una página web';

  @override
  String get chatRequestTitleWebSearch => 'Buscar en la web';

  @override
  String get chatRequestTitleSearch => 'Buscar en el proyecto';

  @override
  String get chatRequestTitleTask => 'Iniciar un agente auxiliar';

  @override
  String get chatRequestTitleSkill => 'Usar una habilidad';

  @override
  String get chatRequestTitleMode => 'Cambiar el modo del agente';

  @override
  String get chatRequestDetailRunsIn => 'Se ejecuta en';

  @override
  String get chatRequestDetailAsks => 'Qué solicita';

  @override
  String get chatRequestDetailRange => 'Parte del archivo';

  @override
  String get chatRequestDetailHelper => 'Tipo de ayudante';

  @override
  String chatRequestReadFrom(String line) {
    return 'desde la línea $line';
  }

  @override
  String chatRequestReadLines(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas',
      one: '1 línea',
    );
    return '$_temp0';
  }

  @override
  String get toolCardWhatWasSent => 'What was sent';

  @override
  String get toolCardWhatCameBack => 'What came back';

  @override
  String get kitRequestShowMore => 'Mostrar más';

  @override
  String get kitRequestShowLess => 'Mostrar menos';

  @override
  String paseoCheckReady(String agents) {
    return 'Listos en esa computadora: $agents.';
  }

  @override
  String get paseoCheckNoneReady =>
      'Aún no hay ningún agente listo en esa computadora.';

  @override
  String get serverRowDetailsVersion => 'Versión';

  @override
  String get capabilityDevServices => 'Dev servers';

  @override
  String get capabilityDevServicesDetail =>
      'Start a project\'s server or preview, and stop it again';

  @override
  String get capabilityWebSearch => 'Web search';

  @override
  String get capabilityWebSearchDetail =>
      'Let the agent look things up on the web';

  @override
  String get capabilityDeleteMessage => 'Delete a message';

  @override
  String get capabilityDeleteMessageDetail =>
      'Remove a message from a conversation';

  @override
  String get capabilitySavedPermissions => 'Saved approvals';

  @override
  String get capabilitySavedPermissionsDetail =>
      'See and remove the actions you always allowed';

  @override
  String get capabilityMcpAdd => 'Add MCP servers';

  @override
  String get capabilityMcpAddDetail =>
      'Add an MCP server or change its settings from the app';

  @override
  String get capabilityApprovals => 'Ask before acting';

  @override
  String get capabilityApprovalsDetail =>
      'Choose whether the agent asks for approval or acts on its own';

  @override
  String get capabilitySteer => 'Send while it works';

  @override
  String get capabilitySteerDetail =>
      'Add a message while the agent is busy; it reads it during the current turn';

  @override
  String get serverSettingsAuthPasswordSaved => 'Password saved on this phone';

  @override
  String get serverSettingsAuthTokenSaved =>
      'Connection token saved on this phone';

  @override
  String get serverSettingsAuthTokenMissing => 'No connection token saved';

  @override
  String connectionPasswordRejectedFor(String server) {
    return 'La contraseña de $server cambió. Actualízala para reconectar.';
  }

  @override
  String connectionTokenRejectedFor(String server) {
    return '$server rechazó el token de conexión. Actualízalo para reconectar.';
  }

  @override
  String serverSettingsEditTitle(String server) {
    return 'Edit $server';
  }

  @override
  String get serverSettingsEditDetail => 'Change its name, address or sign-in';

  @override
  String serverSettingsRemoveTitle(String server) {
    return 'Remove $server';
  }

  @override
  String get serverSettingsRecheckAgents => 'Check for agents again';

  @override
  String get serverSettingsRecheckAgentsDetail =>
      'Use it after signing in to an agent on that computer.';

  @override
  String get serverSettingsRecheckChecking => 'Checking for agents…';

  @override
  String get serverSettingsRecheckFailed =>
      'Could not check for agents. Try again in a moment.';

  @override
  String integrationsMcpDetails(String name) {
    return 'Details for $name';
  }

  @override
  String get usageBusiestDays => 'Busiest days';

  @override
  String usageDaySteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count steps',
      one: '1 step',
    );
    return '$_temp0';
  }

  @override
  String modelPickerAgentUses(String model) {
    return 'Usa $model';
  }

  @override
  String get sessionMenuArchive => 'Archivar conversación';

  @override
  String get sessionMenuArchiveHint =>
      'Moves it to Archived; the server keeps it';

  @override
  String get sessionMenuDelete => 'Eliminar conversación';

  @override
  String get sessionMenuDeleteHint => 'Removes it and its history for good';

  @override
  String sessionArchiveTitle(String title) {
    return '¿Archivar “$title”?';
  }

  @override
  String get sessionArchiveBody =>
      'Pasa a Archivadas, donde aún puedes leerla.';

  @override
  String sessionDeleteTitle(String title) {
    return '¿Eliminar “$title”?';
  }

  @override
  String get sessionDeleteBody =>
      'La conversación y su historial se eliminan para siempre. No se puede deshacer.';

  @override
  String monitorUnreachableTitle(String server) {
    return '$server can\'t be reached';
  }

  @override
  String get monitorUnreachableDetail =>
      'Its questions will show here once it\'s back.';

  @override
  String monitorUnreachableCheck(String server) {
    return 'Check $server again';
  }

  @override
  String agentsStateLimitReset(String time) {
    return 'Límite del plan alcanzado · se restablece $time';
  }

  @override
  String get exportMarkdownUnredacted =>
      'The transcript is not redacted. It holds exactly what you see, including any secrets or private paths in the conversation.';

  @override
  String teamUiGateAnswerConfirmCancelNamedBody(String task) {
    return '“$task” stops. Its open work stays as it is.';
  }

  @override
  String teamProjectStopNamedBody(String project) {
    return '“$project” stops. Its running tasks stop and their work and history stay.';
  }

  @override
  String teamProjectTaskStopNamedBody(String task) {
    return '“$task” stops. Its conversation and changes stay for review.';
  }

  @override
  String get teamAgentUnavailableState => 'Can\'t start';

  @override
  String get teamAgentUnavailableModelWords =>
      'Its model isn\'t set up on the computer';

  @override
  String get teamAgentUnavailableOtherWords =>
      'The computer can\'t run it right now';

  @override
  String teamAgentUnavailableTitle(String agent) {
    return '$agent can\'t start';
  }

  @override
  String teamAgentUnavailableBody(String reason) {
    return '$reason. Fix it on the computer, then check again.';
  }

  @override
  String get teamAgentUnavailableHostSays => 'What the computer says';

  @override
  String get teamControlForceStop => 'Force stop';

  @override
  String get teamProjectControlSuspendWord => 'Pause project';

  @override
  String get teamProjectControlResumeWord => 'Resume project';

  @override
  String get teamProjectControlRemoveWord => 'Delete project';

  @override
  String get teamJobTurnOnWord => 'Turn on';

  @override
  String get teamJobTurnOffWord => 'Turn off';

  @override
  String get teamHomeProjectsTitle => 'Projects';

  @override
  String get teamRigStateActive => 'Active';

  @override
  String get teamRigStatePaused => 'Paused';

  @override
  String teamRigPausedTitle(String project) {
    return '$project is paused';
  }

  @override
  String get teamRigPausedBody =>
      'No new work starts in it until you resume it.';

  @override
  String teamRigPause(String project) {
    return 'Pause project $project';
  }

  @override
  String teamRigResume(String project) {
    return 'Resume project $project';
  }

  @override
  String teamRigPauseTitle(String project) {
    return 'Pause $project?';
  }

  @override
  String teamRigPauseBody(String project) {
    return 'No new work starts in “$project” until you resume it. Nothing is deleted.';
  }

  @override
  String get teamRigPauseConfirm => 'Pause project';

  @override
  String teamRigDelete(String project) {
    return 'Delete project $project';
  }

  @override
  String teamRigDeleteTitle(String project) {
    return 'Delete $project?';
  }

  @override
  String teamRigDeleteBody(String project) {
    return '“$project” is removed from this team. Its folder and files on the computer are not touched.';
  }

  @override
  String get teamRigDeleteConfirm => 'Delete project';

  @override
  String get teamRigDeleteLost => 'The team stops working on it';

  @override
  String get teamRigDeleteKeptFiles => 'Its folder and files on the computer';

  @override
  String get teamRigDeleteKeptHistory => 'Its past tasks and history';

  @override
  String get teamRigFolderLabel => 'Folder on the computer';

  @override
  String get teamRigNotFoundTitle => 'This project is gone';

  @override
  String get teamRigNotFoundBody =>
      'The computer no longer lists it. Go back to the team.';

  @override
  String teamProjectDelete(String project) {
    return 'Delete project $project';
  }

  @override
  String teamProjectDeleteTitle(String project) {
    return 'Delete $project?';
  }

  @override
  String teamProjectDeleteBody(String project) {
    return '“$project” is deleted from this phone\'s AI Team, with the team\'s copy of its code. This can\'t be undone.';
  }

  @override
  String get teamProjectDeleteConfirm => 'Delete project';

  @override
  String get teamProjectDeleteLostPlan =>
      'Its plan, tasks and history are deleted';

  @override
  String get teamProjectDeleteLostRunning => 'Running work stops';

  @override
  String get teamProjectDeleteKept =>
      'The folder you added it from stays as it is';

  @override
  String teamAgentForceStop(String agent) {
    return 'Force stop $agent';
  }

  @override
  String teamAgentForceStopTitle(String agent) {
    return 'Force stop $agent?';
  }

  @override
  String teamAgentForceStopBody(String agent) {
    return '$agent ends at once, without finishing what it is doing. Work it hasn\'t saved may be lost.';
  }

  @override
  String teamAgentStopStuckTitle(String agent) {
    return '$agent didn\'t stop';
  }

  @override
  String get teamAgentStopStuckBody =>
      'The normal stop didn\'t end it. You can force it to end now.';

  @override
  String get teamJobsTitle => 'Scheduled jobs';

  @override
  String get teamJobsWholeTeamTitle => 'For the whole team';

  @override
  String get teamJobsEmptyTitle => 'No scheduled jobs';

  @override
  String get teamJobsEmptyBody =>
      'The computer starts nothing on its own for this project.';

  @override
  String get teamJobsFailedTitle => 'Couldn\'t read scheduled jobs';

  @override
  String get teamJobsFailedBody =>
      'Check the connection to the computer, then try again.';

  @override
  String get teamJobsLoading => 'Reading scheduled jobs';

  @override
  String teamJobEveryDayAt(String time) {
    return 'Every day at $time';
  }

  @override
  String teamJobEveryWeekdayAt(String time) {
    return 'Every weekday at $time';
  }

  @override
  String teamJobEveryDowAt(String day, String time) {
    return 'Every $day at $time';
  }

  @override
  String get teamJobEveryHour => 'Every hour';

  @override
  String teamJobEveryMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Every $count minutes',
      one: 'Every minute',
    );
    return '$_temp0';
  }

  @override
  String teamJobEveryHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Every $count hours',
      one: 'Every hour',
    );
    return '$_temp0';
  }

  @override
  String teamJobEverySeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Every $count seconds',
      one: 'Every second',
    );
    return '$_temp0';
  }

  @override
  String get teamJobOnSchedule => 'On a schedule';

  @override
  String get teamJobRepeats => 'Repeats on its own';

  @override
  String get teamJobWhenEvent => 'When something happens';

  @override
  String get teamJobManual => 'Only when started by hand';

  @override
  String get teamJobWebhook => 'When a web request arrives';

  @override
  String get teamJobCondition => 'When its check passes';

  @override
  String get teamJobOther => 'Starts on its own';

  @override
  String get agentFeaturesLabel => 'Ajustes del agente';

  @override
  String agentFeaturesSheetTitle(String agent) {
    return 'Ajustes de $agent';
  }

  @override
  String agentFeatureChipOff(String name) {
    return '$name, desactivado';
  }

  @override
  String agentFeatureChipOn(String name) {
    return '$name, activado';
  }

  @override
  String get agentFeatureFastChip => 'Rápido';

  @override
  String get agentFeaturesLoadFailed =>
      'No se pudieron cargar los ajustes de este agente.';

  @override
  String get agentFeatureFastTitle => 'Modo rápido';

  @override
  String get agentFeatureFastDetail =>
      'Respuestas más rápidas con los modelos compatibles. Cuesta más.';

  @override
  String get agentFeatureSaving => 'Guardando…';

  @override
  String agentFeatureChangeFailed(String name) {
    return 'No se pudo cambiar $name. Conserva su ajuste anterior.';
  }

  @override
  String get claudeImportTitle => 'Importar desde Claude Code';

  @override
  String claudeImportIntro(String project) {
    return 'Pick a conversation you started in Claude Code in $project. It opens here so you can carry on.';
  }

  @override
  String claudeImportRowDetail(String project, String when) {
    return '$project · $when';
  }

  @override
  String get claudeImportUntitled => 'Untitled conversation';

  @override
  String get claudeImportEmptyTitle => 'Nothing to import';

  @override
  String get claudeImportEmptyBody =>
      'Conversations you start in Claude Code in this project show up here.';

  @override
  String get claudeImportLoadFailedTitle => 'Could not look';

  @override
  String get claudeImportLoadFailedBody =>
      'The list did not load. Check the connection, then try again.';

  @override
  String claudeImportAlready(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more conversations are already here.',
      one: '1 more conversation is already here.',
    );
    return '$_temp0';
  }

  @override
  String get claudeImportWorking => 'Importing…';

  @override
  String get claudeImportFailed =>
      'That conversation could not be imported. Try again.';

  @override
  String hostUpdateRow(String computer) {
    return 'Update agents on $computer';
  }

  @override
  String hostUpdateRowRunning(String version) {
    return 'Running $version. Installs the newest agent software.';
  }

  @override
  String get hostUpdateRowUnknown =>
      'Installs the newest agent software on that computer.';

  @override
  String get hostUpdateConfirmTitle => 'Update agents?';

  @override
  String hostUpdateConfirmBody(String computer) {
    return 'This installs the newest agent software on $computer and restarts it.';
  }

  @override
  String hostUpdateConfirmPauses(String computer) {
    return 'Conversations on $computer pause while it restarts.';
  }

  @override
  String get hostUpdateConfirmKept =>
      'Your conversations and settings stay as they are.';

  @override
  String get hostUpdateConfirmAction => 'Update agents';

  @override
  String get hostUpdateStarting => 'Starting the update…';

  @override
  String get hostUpdateDownloading => 'Downloading the newest version…';

  @override
  String get hostUpdateInstalling => 'Installing…';

  @override
  String get hostUpdateFinishing => 'Finishing up…';

  @override
  String hostUpdateDone(String version) {
    return 'Updated to $version. The app reconnects by itself.';
  }

  @override
  String get hostUpdateDoneNoVersion =>
      'Updated. The app reconnects by itself.';

  @override
  String hostUpdateCurrent(String computer) {
    return '$computer is already up to date.';
  }

  @override
  String get hostUpdateFailed =>
      'The update did not finish. Try again, or update it on that computer.';

  @override
  String get hostUpdateDropped =>
      'The connection dropped during the update. The app is reconnecting.';

  @override
  String get hostUpdateDetailsRow => 'Why it failed';

  @override
  String get hostUpdateDetailsTitle => 'Update details';

  @override
  String get hostUpdateDetailsMessage => 'Message from the computer';

  @override
  String get filesTextSurface => 'Texto en archivos';

  @override
  String get filesSearchText => 'Buscar texto en archivos';

  @override
  String filesSearchInside(String query) {
    return 'Buscar \"$query\" dentro de los archivos';
  }

  @override
  String get filesTextHintTitle => 'Buscar dentro de los archivos';

  @override
  String get filesTextHintBody =>
      'Escribe una palabra o una frase para ver todas las líneas que la contienen.';

  @override
  String get filesNoTextTitle => 'Sin coincidencias';

  @override
  String get filesNoTextBody =>
      'Ningún archivo tiene ese texto. Revisa la ortografía o prueba con menos palabras.';

  @override
  String get filesTextFailedTitle => 'No se pudo buscar en los archivos';

  @override
  String filesTextLimited(int count) {
    return 'Mostrando las primeras $count coincidencias. Escribe más para acotarlas.';
  }

  @override
  String filesTextMatchLine(int line) {
    return 'Línea $line';
  }

  @override
  String get productErrorComputer =>
      'No se pudo contactar con tu computadora. Comprueba que esté encendida y conectada, e inténtalo de nuevo.';

  @override
  String get productErrorProjectGit =>
      'This project\'s Git history can\'t be read, so the conversation couldn\'t start. Restore the project from a copy, or clone it again.';

  @override
  String kitGalleryPosition(int current, int total) {
    return '$current of $total';
  }

  @override
  String kitGalleryPictureLabel(int current, int total, String name) {
    return 'Picture $current of $total: $name';
  }

  @override
  String get kitGalleryEdit => 'Edit';

  @override
  String get kitGallerySend => 'Send';

  @override
  String get kitGalleryLoading => 'Loading picture';

  @override
  String get kitGalleryLoadFailed => 'This picture could not be loaded';

  @override
  String get kitGalleryLoadFailedBody =>
      'Check the connection, then try again.';

  @override
  String get kitGalleryTryAgain => 'Try again';

  @override
  String get kitMarkupTitle => 'Edit picture';

  @override
  String get kitMarkupColours => 'Pen colour';

  @override
  String get kitMarkupRed => 'Red';

  @override
  String get kitMarkupYellow => 'Yellow';

  @override
  String get kitMarkupWhite => 'White';

  @override
  String get kitMarkupBlack => 'Black';

  @override
  String get kitMarkupUndo => 'Undo';

  @override
  String get kitMarkupClear => 'Clear';

  @override
  String get kitMarkupCrop => 'Crop';

  @override
  String get kitMarkupApply => 'Apply';

  @override
  String get kitMarkupCancel => 'Cancel';

  @override
  String get kitMarkupDone => 'Done';

  @override
  String get kitMarkupCanvas =>
      'Picture being edited. Draw on it with your finger.';

  @override
  String get kitMarkupCropHandle => 'Crop corner. Drag to resize.';

  @override
  String get kitMarkupCropArea => 'Crop area. Drag to move it.';

  @override
  String get kitMarkupFailed => 'The edited picture could not be made';

  @override
  String get kitMarkupFailedBody =>
      'Try again, or go back and send the original.';

  @override
  String kitContextRingSemantics(String percent) {
    return 'Context $percent % used';
  }

  @override
  String get kitContextRingTapHint => 'Show context details';

  @override
  String get kitContextRingCompacting => 'Compacting';

  @override
  String get kitContextRingCompactingSemantics => 'Context, compacting';

  @override
  String get kitContextSheetTitle => 'Ventana de contexto';

  @override
  String kitContextSheetTokens(String used, String limit) {
    return '$used of $limit tokens';
  }

  @override
  String get kitContextSheetModel => 'Model';

  @override
  String get kitContextSheetWindow => 'Context window';

  @override
  String get kitContextSheetMeasured => 'Measured';

  @override
  String get kitContextSheetMeasuredValue => 'After the last reply';

  @override
  String get kitContextSheetExplain =>
      'The context window is how much of the conversation the model can read at once: your messages, its replies, files and tool results. When it fills up, older parts have to be summarised or left out, so compacting before then keeps the answers sharp.';

  @override
  String get kitContextSheetChartTitle => 'Each reply';

  @override
  String kitContextChartSemantics(String count, String percent) {
    return 'Context after each of $count replies, the newest at $percent %';
  }

  @override
  String get kitContextChartCompacted => 'Compacted here';

  @override
  String get kitContextSheetCompactNow => 'Compactar ahora';

  @override
  String get phraseChipsRowLabel => 'Frases rápidas';

  @override
  String phraseChipsSend(String phrase) {
    return 'Enviar: $phrase';
  }

  @override
  String get phraseChipsMenuLabel => 'Opciones de frases';

  @override
  String get phraseChipsEdit => 'Editar';

  @override
  String get phraseChipsHide => 'Ocultar';

  @override
  String get settingsPhraseChipsTitle => 'Sugerencias de frases';

  @override
  String get settingsPhraseChipsSupporting =>
      'Your common short phrases, above the message box. Learned on this phone from what you send.';

  @override
  String get settingsPhraseChipsShowHidden => 'Mostrar frases ocultas';

  @override
  String settingsPhraseChipsHiddenCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count frases ocultas',
      one: '1 frase oculta',
    );
    return '$_temp0';
  }

  @override
  String get kitFactToldWord => 'You told it';

  @override
  String get kitFactSeenWord => 'From a source';

  @override
  String get kitFactGuessedWord => 'It guessed';

  @override
  String get kitFactCalculatedWord => 'Worked out';

  @override
  String kitFactToldLine(String date) {
    return 'You told it · $date';
  }

  @override
  String kitFactSeenLine(String source, String date) {
    return 'From $source · $date';
  }

  @override
  String kitFactSeenNoSourceLine(String date) {
    return 'From a source · $date';
  }

  @override
  String kitFactGuessedLine(String date) {
    return 'It guessed · $date';
  }

  @override
  String kitFactGuessedFromLine(String date, String source) {
    return 'It guessed · $date · from $source';
  }

  @override
  String kitFactCalculatedLine(String date) {
    return 'Worked out · $date';
  }

  @override
  String kitFactWaitingLine(String line) {
    return '$line · waiting for your OK';
  }

  @override
  String get fbSourceChat => 'a conversation you saved';

  @override
  String get fbSourceShare => 'text you shared';

  @override
  String get fbSourceNote => 'a note';

  @override
  String get fbSourceImport => 'pasted text';

  @override
  String get fbSourceStatement => 'a statement';

  @override
  String get fbSourceOther => 'another source';

  @override
  String get fbKindPerson => 'Person';

  @override
  String get fbKindDocument => 'Document';

  @override
  String get fbKindSubscription => 'Subscription';

  @override
  String get fbKindWarranty => 'Warranty';

  @override
  String get fbKindBill => 'Bill';

  @override
  String get fbKindCard => 'Card';

  @override
  String get fbKindAccount => 'Account';

  @override
  String get fbKindOrganization => 'Organization';

  @override
  String get fbKindPlace => 'Place';

  @override
  String get fbKindProject => 'Project';

  @override
  String get fbKindTopic => 'Topic';

  @override
  String get fbKindEvent => 'Event';

  @override
  String get fbKindOther => 'Thing';

  @override
  String get fbMemoryTitle => 'Memory';

  @override
  String fbMemoryFromFactsBook(int count) {
    return '$count waiting';
  }

  @override
  String get fbMemoryFromFactsBookNone => 'All clear';

  @override
  String fbMemoryReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changes to review',
      one: '1 change to review',
    );
    return '$_temp0';
  }

  @override
  String fbMemoryNotSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count notes not saved yet',
      one: '1 note not saved yet',
    );
    return '$_temp0';
  }

  @override
  String get fbMemoryNotSavedDetail => 'Kept on this phone';

  @override
  String get fbMemoryReviewDetail => 'Needs your OK';

  @override
  String get fbMemoryRecent => 'Recently added to memory';

  @override
  String get fbMemoryOpenAboutYou => 'Open About you';

  @override
  String get fbMemoryOpenAboutYouDetail =>
      'Search, browse and see what is waiting';

  @override
  String get fbMemoryAddSomething => 'Add something';

  @override
  String get fbMemoryAddSomethingDetail => 'A note or pasted text';

  @override
  String get fbAboutYouTitle => 'About you';

  @override
  String get fbStatusStale => 'Showing what was last saved';

  @override
  String get fbSearchLabel => 'Search your memory';

  @override
  String get fbEmptyTitle => 'Nothing here yet';

  @override
  String get fbEmptyBody =>
      'Add a note, share some text from another app, or paste text from another assistant. What you add shows up here with where it came from.';

  @override
  String get fbListAll => 'All';

  @override
  String get fbListCards => 'Cards';

  @override
  String get fbListPeople => 'People';

  @override
  String get fbListDocuments => 'Documents';

  @override
  String get fbListLockedNote =>
      'Locked notes are not shown. Unlock with your fingerprint or PIN to include them.';

  @override
  String fbKindLine(String kind, String detail) {
    return '$kind · $detail';
  }

  @override
  String get fbAreaMoney => 'Money area';

  @override
  String get fbAreaHealth => 'Health area';

  @override
  String fbAreaOther(String area) {
    return '$area area';
  }

  @override
  String get fbErrorTitle => 'Memory did not answer';

  @override
  String get fbErrorUnreachable =>
      'Memory could not be reached. What you add is kept on this phone until it can be saved.';

  @override
  String get fbErrorLocked => 'This is in the locked folder. Unlock it first.';

  @override
  String get fbErrorNotFound => 'That is not in memory any more.';

  @override
  String get fbErrorGeneric => 'Something went wrong. Try again.';

  @override
  String fbErrorRequest(String id) {
    return 'Request $id';
  }

  @override
  String get fbSearchTitle => 'Search';

  @override
  String fbSearchMatches(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count matches for “$query”',
      one: '1 match for “$query”',
      zero: 'No matches for “$query”',
    );
    return '$_temp0';
  }

  @override
  String get fbSearchWordsOnly =>
      'Matches words in your notes. It does not search by meaning.';

  @override
  String get fbSearchEachHit =>
      'Each result shows where it came from and when. A source can still be wrong.';

  @override
  String get fbSearchLocked =>
      'Locked notes are not searched until you unlock.';

  @override
  String get fbSearchNoneTitle => 'Nothing matches';

  @override
  String get fbSearchNoneBody => 'Try other words.';

  @override
  String get fbThingFacts => 'Facts';

  @override
  String get fbThingNoFacts => 'No facts yet.';

  @override
  String get fbThingLocked => 'Locked folder';

  @override
  String get fbCaptureTitle => 'Add something';

  @override
  String get fbCaptureKindLabel => 'What you are adding';

  @override
  String get fbCaptureNote => 'Note';

  @override
  String get fbCapturePaste => 'Paste';

  @override
  String get fbCaptureNoteLabel => 'What should it remember?';

  @override
  String get fbCaptureNoteHint =>
      'Dentist next Wednesday at 10. Bring the insurance card.';

  @override
  String get fbCapturePasteLabel => 'Paste text from another app or assistant';

  @override
  String get fbCaptureSave => 'Save';

  @override
  String get fbCaptureFootnote =>
      'Anything shared from another app lands here too. A note that cannot be saved yet waits in Not saved yet. It never disappears.';

  @override
  String get fbCaptureSaved =>
      'Saved. Memory will check it for changes for you to review.';

  @override
  String fbCaptureSavedReminder(String when) {
    return 'Saved with a reminder for $when.';
  }

  @override
  String get fbCaptureKept =>
      'It could not be saved yet. It is kept on this phone.';

  @override
  String get fbCaptureSeeNotSaved => 'See Not saved yet';

  @override
  String get fbReminderTitle => 'Check this reminder';

  @override
  String fbReminderWrote(String text) {
    return 'You wrote: “$text”';
  }

  @override
  String get fbReminderDate => 'Date';

  @override
  String get fbReminderTime => 'Time';

  @override
  String get fbReminderZone => 'Time zone';

  @override
  String get fbReminderZoneHome => 'Home time zone';

  @override
  String get fbReminderZoneHomeDetail =>
      'Rings at this time in your home zone, wherever you are.';

  @override
  String get fbReminderZoneFloating => 'This phone\'s time';

  @override
  String get fbReminderZoneFloatingDetail =>
      'Stays at the same clock time wherever you are.';

  @override
  String get fbReminderLocale => 'Language of dates';

  @override
  String get fbReminderRepeat => 'Repeats';

  @override
  String get fbRepeatNever => 'Never';

  @override
  String get fbRepeatDaily => 'Every day';

  @override
  String get fbRepeatWeekly => 'Every week';

  @override
  String get fbRepeatMonthly => 'Every month';

  @override
  String get fbRepeatYearly => 'Every year';

  @override
  String get fbReminderAmbiguousDate => 'I could not tell the date. Check it.';

  @override
  String get fbReminderAmbiguousTime => 'I could not tell the time. Check it.';

  @override
  String get fbReminderHelp => 'Change anything that is wrong before saving.';

  @override
  String get fbReminderSave => 'Save reminder';

  @override
  String get fbReminderChange => 'Change what I wrote';

  @override
  String get fbReminderWhat => 'Remind me about';

  @override
  String get fbNeedsYouTitle => 'Needs you';

  @override
  String fbNeedsYouWaiting(int count) {
    return '$count waiting';
  }

  @override
  String get fbNeedsYouNone => 'Nothing needs you right now.';

  @override
  String get fbNeedsYouNoneBody =>
      'Questions, changes to review and notes not saved yet show up here.';

  @override
  String get fbNotSavedTitle => 'Not saved yet';

  @override
  String fbNotSavedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items kept on this phone',
      one: '1 item kept on this phone',
    );
    return '$_temp0';
  }

  @override
  String get fbNotSavedIntro =>
      'Kept until you discard them: on this phone, or by Memory. None is in your cloud yet.';

  @override
  String get fbNotSavedFooter =>
      'They stay here until you discard them. At 30 days Memory asks: Retry, Export or Discard.';

  @override
  String get fbNotSavedEmptyTitle => 'Everything is saved';

  @override
  String get fbNotSavedEmptyBody =>
      'Notes that cannot be saved yet will wait here.';

  @override
  String get fbNotSavedTryNow => 'Try now';

  @override
  String get fbNotSavedCopy => 'Copy text';

  @override
  String get fbNotSavedDiscard => 'Discard note';

  @override
  String get fbNotSavedRetry => 'Try again';

  @override
  String get fbNotSavedExport => 'Export';

  @override
  String get fbNotSavedThirtyDays =>
      'This has been kept for 30 days. Retry it, export the text, or discard it.';

  @override
  String get fbNotSavedDiscardTitle => 'Discard this note?';

  @override
  String get fbNotSavedDiscardBody =>
      'It was never saved. Discarding removes it from this phone.';

  @override
  String get fbNotSavedSaved => 'Saved.';

  @override
  String get fbNotSavedStill => 'Still not saved. It stays here.';

  @override
  String fbNotSavedSince(String when) {
    return 'Since $when';
  }

  @override
  String get fbReasonUnreachable => 'Memory could not be reached.';

  @override
  String get fbReasonStorageFull =>
      'This phone is full. Free some space, then try again.';

  @override
  String get fbReasonCloudQuota =>
      'Your cloud is full. Free some space, then try again.';

  @override
  String get fbReasonKeyLocked =>
      'The locked folder is locked. Unlock it, then try again.';

  @override
  String get fbReasonRejected => 'Memory could not accept it.';

  @override
  String get fbReasonClassification =>
      'Memory could not sort it yet. Try again.';

  @override
  String get fbDismiss => 'Dismiss';

  @override
  String get fbCaptureNeedsText => 'Write something first.';

  @override
  String get fbAreaFamily => 'Family area';

  @override
  String get fbAreaWork => 'Work area';

  @override
  String get fbAreaNameMoney => 'Money';

  @override
  String get fbAreaNameHealth => 'Health';

  @override
  String get fbAreaNameFamily => 'Family';

  @override
  String get fbAreaNameWork => 'Work';

  @override
  String get fbAreasTitle => 'Areas and sharing';

  @override
  String get fbAreasIntro =>
      'Each area keeps its own rules. Other apps and agents read an area only if you let them.';

  @override
  String get fbAreasOthersRead => 'Other apps and agents can read it';

  @override
  String get fbAreasOthersReadDetail =>
      'Turn off to keep this area to you alone.';

  @override
  String get fbAreasLockedFolder => 'Locked folder';

  @override
  String get fbAreasNormalStorage => 'Normal storage';

  @override
  String get fbAreasReviewFirst => 'New facts wait for your OK first.';

  @override
  String get fbAreasReviewAuto => 'Only safe clean-up happens on its own.';

  @override
  String get fbAreasFixed => 'These areas are built in.';

  @override
  String fbAreasShareConfirmTitle(String area) {
    return 'Let others read $area?';
  }

  @override
  String get fbAreasShareConfirmBody =>
      'Apps and agents you have paired will be able to read what is in this area. You can turn it off again.';

  @override
  String get fbAreasShareConfirm => 'Let them read';

  @override
  String get fbCaptureFull =>
      'This phone\'s queue is full, so the note was not kept. Your text is still here. Send or discard a waiting note first.';

  @override
  String get fbCaptureQueuedNode =>
      'Memory is holding it until it can be saved.';

  @override
  String get fbCaptureReminderFailed =>
      'The reminder was not made. Try it again from the note.';

  @override
  String fbCareWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count waiting for your OK',
      one: '1 waiting for your OK',
    );
    return '$_temp0';
  }

  @override
  String get fbCareApprove => 'Approve';

  @override
  String get fbCareReject => 'Reject';

  @override
  String get fbCareApproved => 'Approved.';

  @override
  String get fbCareRejected => 'Rejected. It will not be proposed again.';

  @override
  String get fbCareSkipped => 'Skipped. It stays here.';

  @override
  String get fbCareStale =>
      'That changed after it was proposed, so nothing was applied. It is off this list now.';

  @override
  String get fbCareNeedsOwner =>
      'This phone cannot approve changes yet. Set up approval to continue.';

  @override
  String get fbCareNeedsOwnerShort => 'Set up approval first';

  @override
  String get fbCareSetupApproval => 'Set up approval';

  @override
  String get fbCareEditTitle => 'Edit before approving';

  @override
  String get fbCareEditLabel => 'Fact';

  @override
  String get fbCareEditSave => 'Approve with my edit';

  @override
  String get fbCareClassFactAdd => 'Add a fact';

  @override
  String get fbCareClassFactUpdate => 'Change a fact';

  @override
  String get fbCareClassFactSupersede => 'Replace a fact';

  @override
  String get fbCareClassMerge => 'Merge two things';

  @override
  String get fbCareClassLink => 'Link two things';

  @override
  String get fbCareClassArchive => 'Archive';

  @override
  String get fbCareClassForget => 'Forget';

  @override
  String get fbCareClassWatch => 'Keep an eye on something';

  @override
  String get fbCareClassReminder => 'Reminder';

  @override
  String get fbCareClassImport => 'From pasted text';

  @override
  String get fbCareClassPromotion => 'A fact for a stricter area';

  @override
  String get fbCareRiskMoney => 'Money';

  @override
  String get fbCareRiskHealth => 'Health';

  @override
  String get fbCareRiskId => 'ID';

  @override
  String get fbCareRiskContradiction => 'Contradicts something you told it';

  @override
  String get fbCareRiskLowConfidence => 'Not sure';

  @override
  String get fbConnectTitle => 'Connect to my server';

  @override
  String get fbConnectRow => 'Set up Memory';

  @override
  String get fbConnectRowDetail => 'Your notes, on this phone';

  @override
  String get fbConnectIntro =>
      'Connect this app to a Memory server on your home network or your tailnet, or on this phone.';

  @override
  String get fbConnectAddress => 'Address of the node';

  @override
  String get fbConnectAddressHint => '127.0.0.1:8787';

  @override
  String get fbConnectCode => 'One-time pairing code';

  @override
  String get fbConnectCodeHelp =>
      'The node prints it when you run its pair command. It works once, for five minutes.';

  @override
  String get fbConnectOwnerTitle => 'Approve changes (optional)';

  @override
  String get fbConnectOwnerBody =>
      'Reviewing proposals and changing sharing are signed with this phone\'s own key. Give the node the public key below, then enter the owner device id it returns.';

  @override
  String get fbConnectOwnerKey => 'This phone\'s public key';

  @override
  String get fbConnectOwnerId => 'Owner device id';

  @override
  String get fbConnectButton => 'Connect';

  @override
  String get fbConnectNeeds => 'Enter the address and the code first.';

  @override
  String get fbConnectFailedTitle => 'Could not connect';

  @override
  String fbDisconnect(String name) {
    return 'Disconnect $name';
  }

  @override
  String get fbDisconnectDetail =>
      'Forget the pairing, token and approval key on this phone';

  @override
  String fbDisconnectTitle(String name) {
    return 'Disconnect $name?';
  }

  @override
  String get fbDisconnectBody =>
      'This phone forgets the node\'s pairing, token and approval key. The node\'s notes stay where they are, and notes waiting on this phone stay until you discard them.';

  @override
  String get fbErrorAddress =>
      'That address is not on a private network. Use this phone, your home network or your tailnet.';

  @override
  String get fbErrorBadResponse =>
      'Memory answered in a way this app cannot read. Update the app, or update the memory program.';

  @override
  String get fbErrorForbidden => 'This phone is not allowed to do that.';

  @override
  String get fbErrorNotEnabled => 'That is turned off in this memory.';

  @override
  String get fbErrorOwner =>
      'This phone cannot approve changes yet. Set up approval first.';

  @override
  String get fbErrorPairCode =>
      'That code did not work. Ask the node for a new one.';

  @override
  String get fbErrorSlow => 'Memory is busy. Try again in a moment.';

  @override
  String get fbErrorStale =>
      'That changed since you looked. Look again, then try once more.';

  @override
  String get fbErrorStarting => 'Memory is starting. Try again in a moment.';

  @override
  String get fbErrorUnauthorized =>
      'This phone is not signed in to Memory. Set it up again.';

  @override
  String fbErrorCode(String code) {
    return 'Code $code';
  }

  @override
  String get fbKeyLocked => 'Locked';

  @override
  String get fbKeyUnlocked => 'Unlocked';

  @override
  String get fbKindInvoice => 'Invoice';

  @override
  String get fbKindHealth => 'Health';

  @override
  String get fbModuleStorage => 'Storage';

  @override
  String get fbModuleCloud => 'Cloud backup';

  @override
  String get fbModuleCare => 'Needs you';

  @override
  String get fbNotSavedAtNode => 'Held by Memory';

  @override
  String get fbNotSavedOnPhone => 'On this phone';

  @override
  String get fbNotSavedNodeItem => 'A note Memory is holding';

  @override
  String get fbReasonModuleNotConfigured => 'nothing connected yet';

  @override
  String get fbReasonModuleNotEnabled => 'not turned on yet';

  @override
  String get fbReasonOutboxFull =>
      'The queue on this phone is full. Send or discard a waiting note.';

  @override
  String get fbReasonParserError => 'Memory could not read it. Try again.';

  @override
  String get fbReminderMismatch =>
      'Memory read this differently. Check the date, time and zone, then try again.';

  @override
  String fbReminderPause(String what) {
    return 'Pause “$what”';
  }

  @override
  String fbReminderResume(String what) {
    return 'Resume “$what”';
  }

  @override
  String get fbRemindersTitle => 'Reminders';

  @override
  String get fbRemindersActive => 'Active';

  @override
  String get fbRemindersPaused => 'Paused';

  @override
  String get fbRemindersDone => 'Done';

  @override
  String get fbRemindersEmptyTitle => 'No reminders yet';

  @override
  String get fbRemindersEmptyBody =>
      'Add a reminder, or turn on “Also remind me about it” when you add a note, and it shows up here.';

  @override
  String get fbSourceSaved => 'something you saved';

  @override
  String get fbStateReady => 'Ready';

  @override
  String get fbStateWaiting => 'Waiting';

  @override
  String get fbStateStale => 'Stale';

  @override
  String get fbStateFailed => 'Failed';

  @override
  String get fbStateOff => 'Off';

  @override
  String get fbStateNotSetUp => 'Not set up';

  @override
  String get fbStatusTitle => 'Status';

  @override
  String get fbStatusSearch => 'Search';

  @override
  String get fbStatusBackup => 'Backup';

  @override
  String get fbStatusBackedUp => 'Backed up';

  @override
  String get fbStatusNoBackup => 'No backup yet';

  @override
  String get fbStatusThisPhone => 'This phone';

  @override
  String get fbStatusApprove => 'Approve changes from this phone';

  @override
  String fbStatusSince(String when) {
    return 'updated $when';
  }

  @override
  String get fbThingWaitingNote =>
      'Facts marked \"waiting for your OK\" are in Needs you.';

  @override
  String fbWhenDaily(String time) {
    return 'Every day at $time';
  }

  @override
  String get fbWhenCustom => 'Custom schedule';

  @override
  String get fbDeveloperFactsBook => 'Memory (preview)';

  @override
  String get fbDeveloperFactsBookDetail =>
      'Shows the Memory section in your conversations. Off by default.';

  @override
  String get fbDeveloperTitle => 'Developer';

  @override
  String get fbErrorBadRequest =>
      'Memory did not accept that. Check what you entered and try again.';

  @override
  String get fbErrorNodeFull =>
      'Memory\'s own queue is full. Keep your text and try again later.';

  @override
  String get fbErrorIndexRebuilding =>
      'Search is getting ready. Try again in a moment.';

  @override
  String get fbErrorAuthUnavailable =>
      'This phone has no fingerprint, face or screen lock set up, so it cannot approve changes. Set one up in the phone\'s settings, then try again.';

  @override
  String get fbErrorAuthCancelled =>
      'Approval was cancelled. Nothing was changed.';

  @override
  String get fbErrorAuthLocked =>
      'Too many wrong tries. Wait a little, then try again.';

  @override
  String get fbErrorAuthFailed =>
      'The phone could not confirm it is you. Nothing was changed.';

  @override
  String fbErrorSlowAfter(int seconds) {
    return 'Memory is busy. Try again in $seconds seconds.';
  }

  @override
  String get fbAuthReason => 'Approve this change in Memory';

  @override
  String fbWhenWeeklyOn(String days, String time) {
    return 'Every week on $days at $time';
  }

  @override
  String fbWhenMonthLast(String time) {
    return 'Every month on the last day at $time';
  }

  @override
  String fbWhenMonthlyOn(int day, String time) {
    return 'Every month on day $day at $time';
  }

  @override
  String fbWhenYearlyOn(String date, String time) {
    return 'Every year on $date at $time';
  }

  @override
  String get fbReminderNeedsTitle => 'Write the reminder first.';

  @override
  String get fbReminderWhatHint => 'Call the dentist';

  @override
  String get fbReminderDayOfMonth => 'Day of the month';

  @override
  String get fbReminderLastDay => 'Last day';

  @override
  String get fbRemindersAdd => 'Add a reminder';

  @override
  String fbThingOriginally(String source, String date) {
    return 'Originally read from $source · $date';
  }

  @override
  String get fbExportTitle => 'Export this note';

  @override
  String get fbExportBody =>
      'This is the note as an encrypted object. It does not unlock the locked folder, and opening it needs the recovery details from when Memory was set up on its own device.';

  @override
  String get fbExportObject => 'Encrypted object';

  @override
  String get fbExportCopy => 'Copy export';

  @override
  String get fbSettingsTitle => 'Memory settings';

  @override
  String get fbSettingsBrief => 'Morning brief';

  @override
  String get fbSettingsBriefCap => 'Items a day';

  @override
  String get fbSettingsBriefCapDetail => 'The most the brief shows in one day.';

  @override
  String get fbSettingsBriefOff => 'None';

  @override
  String fbSettingsBriefItems(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items',
      one: '1 item',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsBriefTime => 'Brief time';

  @override
  String get fbSettingsQuietHours => 'Quiet hours';

  @override
  String get fbSettingsPrivacy => 'Privacy';

  @override
  String get fbSettingsLockTimeout => 'Locked folder stays open for';

  @override
  String fbSettingsMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutes',
      one: '1 minute',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsAreasDetail => 'Who else may read each area';

  @override
  String get fbSettingsThisNode => 'This memory';

  @override
  String get fbSettingsHomeZone => 'Home time zone';

  @override
  String get fbSettingsSpending => 'Spending limit';

  @override
  String get fbSettingsSpendingNone => 'Nothing may be spent';

  @override
  String get fbSettingsFootnote =>
      'The lock time and the home zone are set on the memory server. Changes here need this phone\'s approval.';

  @override
  String get fbSettingsNeedsOwner =>
      'This phone cannot approve changes yet, so settings are shown but not changed. Set up approval to change them.';

  @override
  String get fbModuleReplication => 'Replication';

  @override
  String get fbStatusChecked => 'Checked';

  @override
  String get fbStatusUnknown => 'Not known';

  @override
  String get fbStatusHistory => 'Signed history';

  @override
  String get fbStatusHistoryNotChecked => 'Not checked';

  @override
  String get fbStatusHistoryVerified =>
      'Verified against the key pinned when you paired';

  @override
  String get fbStatusHistoryFailed =>
      'Could not be verified. Do not approve anything until it is.';

  @override
  String get fbStatusHistoryForked =>
      'Two competing histories were found. Approvals are paused.';

  @override
  String get fbStatusNoReplication =>
      'Replication is off, so your notes exist only in this memory, and how fresh a cloud copy is cannot be known.';

  @override
  String get fbStatusNotYet => 'Not turned on';

  @override
  String get fbNotYetCloud => 'Cloud sync and backup';

  @override
  String get fbNotYetUnlock => 'Opening the locked folder';

  @override
  String get fbNotYetAlarms => 'Reminder alarms on this phone';

  @override
  String get fbNotAvailableYet => 'Off';

  @override
  String get fbReminderClockNote =>
      'If a time does not exist that day because the clocks change, it runs at the first time that does.';

  @override
  String get fbCaptureReminderNotMade =>
      'The reminder was not made yet. Add it again once the note is saved.';

  @override
  String get fbNotSavedDiscardBodyNode =>
      'Memory will delete the copy it is holding. It was never saved.';

  @override
  String get fbAreaNameGeneral => 'General';

  @override
  String get fbAreaNameHome => 'Home';

  @override
  String get fbAreaNameTravel => 'Travel';

  @override
  String get fbAreaNameUnsorted => 'Unsorted';

  @override
  String get fbAreaNameId => 'ID';

  @override
  String get fbModuleIndex => 'Search index';

  @override
  String get fbModuleMembership => 'Devices';

  @override
  String get fbTriggersTitle => 'When something new arrives';

  @override
  String get fbTriggersSettingsDetail =>
      'Rules that suggest reminders and conversations from messages you share';

  @override
  String get fbTriggersUnavailableTitle => 'Rules are off here';

  @override
  String get fbTriggersUnavailableBody =>
      'This memory does not offer rules. Update it to use them.';

  @override
  String get fbTriggersEmptyTitle => 'No rules yet';

  @override
  String get fbTriggersEmptyBody =>
      'A rule watches for one kind of message and only suggests. Nothing is saved or sent until you approve.';

  @override
  String get fbTriggersNew => 'New rule';

  @override
  String fbTriggersGroupShare(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules',
      one: '1 rule',
    );
    return 'Text you share · $_temp0';
  }

  @override
  String fbTriggersGroupChat(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules',
      one: '1 rule',
    );
    return 'Conversations you save · $_temp0';
  }

  @override
  String fbTriggersGroupNote(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules',
      one: '1 rule',
    );
    return 'Notes you write · $_temp0';
  }

  @override
  String get fbTriggersRecent => 'Recent messages';

  @override
  String get fbTriggersFootnote =>
      'A rule only suggests. Nothing is saved or sent until you approve. Ignored items are counted, never kept.';

  @override
  String fbRuleFinds(String template, String outcomes) {
    return 'Looks for $template → $outcomes';
  }

  @override
  String get fbTemplateAppointment => 'an appointment';

  @override
  String get fbTemplateDelivery => 'a delivery';

  @override
  String get fbTemplateBooking => 'a booking';

  @override
  String get fbTemplateSubscription => 'a subscription renewal';

  @override
  String get fbTemplateBill => 'a bill due';

  @override
  String get fbOutcomeReminder => 'propose a reminder';

  @override
  String get fbOutcomeFact => 'propose a fact';

  @override
  String get fbOutcomeConversation => 'offer to talk about it';

  @override
  String fbRuleWeek(int proposed, int ignored) {
    return 'This week: $proposed proposed, $ignored ignored';
  }

  @override
  String fbRuleWeekOffers(int proposed, int ignored, int offers) {
    String _temp0 = intl.Intl.pluralLogic(
      offers,
      locale: localeName,
      other: '$offers offers',
      one: '1 offer',
    );
    return 'This week: $proposed proposed, $ignored ignored, $_temp0';
  }

  @override
  String get fbRulePaused => 'Paused';

  @override
  String fbRulePause(String name) {
    return 'Pause “$name”';
  }

  @override
  String fbRuleResume(String name) {
    return 'Resume “$name”';
  }

  @override
  String get fbRulePausedDone => 'Rule paused.';

  @override
  String get fbRuleResumedDone => 'Rule is on again.';

  @override
  String get fbSourceGroupShare => 'Text you share';

  @override
  String get fbSourceGroupChat => 'A conversation you saved';

  @override
  String get fbSourceGroupNote => 'A note you wrote';

  @override
  String fbEventWhen(String source, String when) {
    return '$source · $when';
  }

  @override
  String get fbReasonMatched => 'A rule matched this message.';

  @override
  String get fbReasonNoMatch => 'No rule matched this message.';

  @override
  String get fbReasonUncertain =>
      'Memory could not read this safely, so it kept nothing and suggested nothing.';

  @override
  String get fbReasonSensitive =>
      'This looked private and locked notes are closed, so it was set aside.';

  @override
  String get fbReasonSourceCap =>
      'Too many messages came from this source today, so this one was only counted.';

  @override
  String get fbReasonVaultCap =>
      'Too many messages arrived today, so this one was only counted.';

  @override
  String get fbReasonRuleCap =>
      'This rule reached its daily limit, so this one was only counted.';

  @override
  String get fbReasonStorm =>
      'Many similar messages came at once, so they were folded into one.';

  @override
  String get fbReasonNoRule => 'No rule is on for this source.';

  @override
  String get fbRuleNewTitle => 'New rule';

  @override
  String get fbRuleEditTitle => 'Edit rule';

  @override
  String get fbRuleNameLabel => 'Rule name';

  @override
  String get fbRuleNameHint => 'For example: Library appointments';

  @override
  String get fbRuleWhen => 'When';

  @override
  String get fbRuleWhenLabel => 'Where the message comes from';

  @override
  String get fbSourceKindShare => 'Shared text';

  @override
  String get fbSourceKindChat => 'Saved conversation';

  @override
  String get fbSourceKindNote => 'Note';

  @override
  String get fbRuleSays => 'Says something like';

  @override
  String get fbRuleTemplateLabel => 'Kind of message';

  @override
  String get fbRuleLanguageLabel => 'Message language';

  @override
  String get fbRuleLangEn => 'English';

  @override
  String get fbRuleLangAr => 'Arabic';

  @override
  String get fbRuleTemplateHelp =>
      'Memory reads one line like “Appointment: Library visit | 2026-10-15T09:00”.';

  @override
  String get fbRuleWordsLabel => 'Words it must contain (optional)';

  @override
  String get fbRuleWordsHelp =>
      'Up to 4 words, separated by commas. Plain text, not code.';

  @override
  String get fbRuleThen => 'Then';

  @override
  String get fbRuleOutcomeReminder => 'Propose a reminder';

  @override
  String get fbRuleOutcomeReminderDetail =>
      'You confirm the date and time first.';

  @override
  String get fbRuleOutcomeConversation => 'Offer to talk about it';

  @override
  String get fbRuleOutcomeConversationDetail =>
      'You choose whether a conversation opens.';

  @override
  String get fbRuleOutcomeFactKept =>
      'This rule also proposes a fact. Facts are set up on the memory server, and this stays as it is.';

  @override
  String get fbRuleAutoStart => 'Start these conversations by itself';

  @override
  String get fbRuleAutoStartOff =>
      'Conversations are only offered, never started for you.';

  @override
  String get fbRuleOn => 'This rule is on';

  @override
  String get fbRuleOnDetail =>
      'Turn it off to pause it. Nothing it already suggested is removed.';

  @override
  String get fbRuleLimit => 'At most each day';

  @override
  String fbRuleLimitValue(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count a day',
      one: '1 a day',
    );
    return '$_temp0';
  }

  @override
  String get fbRuleTest => 'Try it on a sample';

  @override
  String get fbRuleSampleLabel => 'Sample message';

  @override
  String get fbRuleSampleExample => 'Use an example';

  @override
  String get fbRuleTestRun => 'Test';

  @override
  String get fbRuleTestAgain => 'Test again';

  @override
  String fbRuleTestMatch(String summary, String when) {
    return 'It matches. Found: $summary, $when.';
  }

  @override
  String get fbRuleTestMatchPlain => 'It matches.';

  @override
  String fbRuleTestWould(String outcomes) {
    return 'Would propose: $outcomes. A test saves nothing.';
  }

  @override
  String get fbRuleTestNoMatch => 'It does not match.';

  @override
  String get fbRuleSave => 'Save rule';

  @override
  String get fbRuleSaved => 'Rule saved.';

  @override
  String get fbRuleDelete => 'Delete rule';

  @override
  String get fbRuleDeleteTitle => 'Delete this rule?';

  @override
  String fbRuleDeleteBody(String name) {
    return '“$name” stops suggesting things. What it already suggested stays for you to decide.';
  }

  @override
  String get fbRuleNeedsOwner =>
      'This phone cannot sign rule changes yet, so rules are shown but not changed. Set up approval to change them.';

  @override
  String get fbRuleStale =>
      'This rule changed in memory. It was reloaded: check it, then save again.';

  @override
  String get fbRuleProblemName => 'Give the rule a name of up to 80 letters.';

  @override
  String get fbRuleProblemWords => 'Use up to 4 words, each up to 40 letters.';

  @override
  String get fbRuleProblemOutcome =>
      'Choose at least one thing for the rule to suggest.';

  @override
  String get fbRuleProblemTemplate =>
      'This memory does not read that kind of message.';

  @override
  String get fbRuleProblemLimit => 'Choose between 1 and 20 a day.';

  @override
  String get fbEventTitle => 'What happened here';

  @override
  String get fbEventFrom => 'Where it came from';

  @override
  String get fbEventSender => 'Sender (a claim)';

  @override
  String get fbEventApp => 'App (a claim)';

  @override
  String get fbEventTrust => 'Trust';

  @override
  String get fbTrustUntrusted => 'From someone else, not verified';

  @override
  String get fbTrustSelected => 'A conversation you chose';

  @override
  String get fbTrustAuthored => 'Written by you';

  @override
  String get fbEventRule => 'Rule that matched';

  @override
  String get fbEventIgnored => 'Other notes';

  @override
  String fbEventCollapsed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count more similar messages were folded into this one.',
      one: '1 more similar message was folded into this one.',
    );
    return '$_temp0';
  }

  @override
  String get fbEventUnverified =>
      'Unverified sender. Memory cannot check who really sent this text, so it will not change a money fact without your OK.';

  @override
  String get fbEventProposed => 'What it suggested';

  @override
  String get fbEventFootnote =>
      'The original text is never shown here and is never followed as an instruction.';

  @override
  String get fbTriggerChip => 'From a trigger';

  @override
  String get fbWhyFired => 'What happened to this message';

  @override
  String fbCareProposalWhen(String what, String when) {
    return '$what, $when';
  }

  @override
  String fbNeedsYouOffers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count offers from triggers',
      one: '1 offer from a trigger',
    );
    return '$_temp0';
  }

  @override
  String get fbOfferTitle => 'Discuss with your agent?';

  @override
  String fbOfferFromRule(String name) {
    return 'Offer · $name';
  }

  @override
  String fbOfferSource(String source, String date) {
    return 'From $source · $date';
  }

  @override
  String get fbOfferTalk => 'Talk about this';

  @override
  String get fbOfferNotNow => 'Not now';

  @override
  String get fbOfferExplain =>
      'A new conversation opens with your agent. It gets a short summary, not the whole message. It can read and suggest, never act. Replying or paying always asks you first.';

  @override
  String get fbOfferChatLead =>
      'Memory noticed something you may want to talk about. This is a short summary, not the original message:';

  @override
  String get fbOfferStale =>
      'This offer changed or ran out, so it was refreshed.';

  @override
  String get fbOfferUnknown =>
      'Memory could not confirm the conversation started, so nothing was opened. It will not try again by itself.';

  @override
  String get fbErrorBusy => 'Memory is busy for a moment. Try again.';

  @override
  String get fbRuleOfferUnavailable =>
      'This memory does not offer conversations from rules.';

  @override
  String get fbNodeTitle => 'Memory on this phone';

  @override
  String get fbNodeRow => 'Use memory on this phone';

  @override
  String get fbNodeRowDetail =>
      'Keep your memory on this phone. Nothing leaves the phone.';

  @override
  String get fbNodeUnsupportedTitle => 'Not available here';

  @override
  String get fbNodeUnsupportedBody =>
      'Memory on this phone needs On this phone, which only Android phones have.';

  @override
  String get fbNodeLinuxTitle => 'Set up phone first';

  @override
  String get fbNodeLinuxBody =>
      'Memory runs in On this phone. Set that up first, then come back.';

  @override
  String get fbNodeLinuxAction => 'Set up this phone';

  @override
  String get fbNodeNoBuildTitle => 'No memory build yet';

  @override
  String get fbNodeNoBuildBody =>
      'This version of the app cannot install Memory on this phone. Update the app to a newer version, or connect to a server of your own below.';

  @override
  String get fbNodeNotInstalledTitle => 'Memory not installed yet';

  @override
  String get fbNodeNotInstalledBody =>
      'The app installs Memory in On this phone, starts it and connects to it by itself. It listens only inside this phone.';

  @override
  String get fbNodeRun => 'Set up memory on this phone';

  @override
  String get fbNodeStepCopying => 'Installing the program';

  @override
  String get fbNodeStepPreparing => 'Creating the memory';

  @override
  String get fbNodeStepStarting => 'Starting';

  @override
  String get fbNodeStepPairing => 'Connecting this app';

  @override
  String get fbNodeRunningTitle => 'Memory is running here';

  @override
  String get fbNodeRunningBody =>
      'Memory runs in On this phone and is connected to this app. It listens only inside this phone.';

  @override
  String get fbNodeStoppedTitle => 'Memory is stopped';

  @override
  String get fbNodeStoppedBody =>
      'Your memory is still on this phone. Start it to use it.';

  @override
  String get fbNodeStart => 'Start memory';

  @override
  String get fbNodeStop => 'Stop memory';

  @override
  String get fbNodeRestart => 'Restart memory';

  @override
  String get fbNodeStopping => 'Stopping';

  @override
  String get fbNodeRemoving => 'Removing';

  @override
  String get fbNodeOpen => 'Open About you';

  @override
  String get fbNodeAddress => 'Address';

  @override
  String get fbNodeAddressValue => 'Only inside this phone';

  @override
  String get fbNodeRemove => 'Remove memory from this phone';

  @override
  String get fbNodeRemoveTitle => 'Remove memory from phone?';

  @override
  String get fbNodeRemoveBody =>
      'This stops Memory and deletes everything it keeps on this phone. It cannot be undone.';

  @override
  String get fbNodeRemoveLoses1 =>
      'Every note, fact and reminder Memory holds here';

  @override
  String get fbNodeRemoveLoses2 => 'The connection and approval key link to it';

  @override
  String get fbNodeRemoved => 'Memory removed from this phone.';

  @override
  String get fbNodeFailInstall =>
      'The program could not be installed here. Try again; Details says why.';

  @override
  String get fbNodeFailStart => 'Memory did not start. Try again.';

  @override
  String get fbNodeFailPair =>
      'Memory is running, but this app could not connect to it. Try again.';

  @override
  String get fbNodeFailStop => 'Memory did not stop. Try again.';

  @override
  String get fbNodeFailRemove =>
      'Memory could not be removed. Some of its data may still be on this phone.';

  @override
  String get fbNodeLog => 'Show the log';

  @override
  String get fbReasonTargetStale =>
      'The thing this rule updates changed, so nothing was proposed.';

  @override
  String get fbReasonTargetGone =>
      'The thing this rule updates is no longer there or is not open to it, so nothing was proposed.';

  @override
  String get fbReasonRejectedEvent => 'Memory did not accept this message.';

  @override
  String get fbRuleProblemSender => 'The sender name must be 1 to 80 letters.';

  @override
  String get fbRuleSenderLabel => 'Sender claim (optional)';

  @override
  String get fbRuleSenderHelp =>
      'Only messages that say they are from this name. Anyone can claim any name, so this narrows a rule; it never proves who sent it.';

  @override
  String get fbRuleAreaLabel => 'Area';

  @override
  String get fbRuleTestDraftNote =>
      'The test uses what you see here, saved or not. It saves nothing.';

  @override
  String get fbCareProposalEdited => 'Changed. Check it, then approve it.';

  @override
  String get fbCaptureRulesLook => 'Let my rules read this';

  @override
  String get fbAuthReasonRules => 'Let your rules read this text';

  @override
  String get fbNodeWhatTitle => 'What is this?';

  @override
  String get fbNodeWhatBody =>
      'Memory keeps your notes, reminders and facts on this phone, private to you. It runs on this phone and never leaves it.';

  @override
  String get fbNodeOwnServer => 'Connect to my own server';

  @override
  String get fbNodeOwnServerBody =>
      'Advanced: connect this app to a Memory server you run yourself, instead of using this phone.';

  @override
  String get fbMemoryStopped => 'Memory is stopped';

  @override
  String get fbMemoryStoppedDetail =>
      'Start it to see your notes and reminders';

  @override
  String get fbMemoryStart => 'Start';

  @override
  String get fbMemoryStarting => 'Starting memory…';

  @override
  String get fbCareTapHint => 'Tap a card to change it or skip it.';

  @override
  String fbCareApproveNamed(String claim) {
    return 'Approve “$claim”';
  }

  @override
  String fbCareRejectNamed(String claim) {
    return 'Reject “$claim”';
  }

  @override
  String fbCareEditNamed(String claim) {
    return 'Change “$claim”';
  }

  @override
  String fbCareSkipNamed(String claim) {
    return 'Skip “$claim”';
  }

  @override
  String get fbCareSkipDetail => 'It stays here for later';

  @override
  String get fbCareSourceRule => 'A rule suggested it';

  @override
  String get fbCareSourceAgent => 'An agent suggested it';

  @override
  String get fbNodeNameThisPhone => 'this phone\'s memory';

  @override
  String get fbNodeNameDefault => 'this memory';

  @override
  String get fbStatusAllWorking => 'Everything is working';

  @override
  String fbStatusNeedsLook(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count things need a look',
      one: '1 thing needs a look',
    );
    return '$_temp0';
  }

  @override
  String fbStatusBackedUpAge(String age) {
    return 'Backed up · copy $age old';
  }

  @override
  String get fbReminderNotSounded => 'Not sounded on this phone';

  @override
  String fbEventOutcomeProposed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'It suggested $count things for you',
      one: 'It suggested 1 thing for you',
    );
    return '$_temp0';
  }

  @override
  String get fbEventWaitingForOk => 'Waiting for your OK';

  @override
  String get fbCaptureAddTo => 'Add to';

  @override
  String get fbCaptureAddToNone => 'Let memory sort it';

  @override
  String get fbCaptureAddToFactNote => 'Saved as one line, for you to approve';

  @override
  String get fbCaptureArea => 'Area';

  @override
  String get fbCaptureAreaAuto => 'Let memory choose';

  @override
  String get fbCaptureRemindToo => 'Also remind me about it';

  @override
  String get fbCaptureRemindTooDetail =>
      'You check the date and time before anything is saved.';

  @override
  String fbCaptureRulesOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rules are on',
      one: '1 rule is on',
      zero: 'No rules are on',
    );
    return '$_temp0';
  }

  @override
  String get fbCaptureSeeRules => 'See your rules';

  @override
  String fbCaptureFactAdded(String thing) {
    return 'Added to $thing.';
  }

  @override
  String get fbCaptureFactProposed =>
      'Sent for your OK. It waits in Needs you.';

  @override
  String get fbCaptureSeeNeedsYou => 'See Needs you';

  @override
  String fbThingAddFact(String thing) {
    return 'Add a fact to $thing';
  }

  @override
  String get fbThingAddFactSave => 'Add fact';

  @override
  String fbThingRemind(String thing) {
    return 'Remind me about $thing';
  }

  @override
  String fbThingReminderMade(String thing) {
    return 'Reminder saved for $thing.';
  }

  @override
  String get fbThingEditFact => 'Change this fact';

  @override
  String get fbThingEditFactSave => 'Send my change';

  @override
  String get fbThingFactChanged => 'Changed.';

  @override
  String get fbThingChangeProposed =>
      'Your change waits for your OK in Needs you.';

  @override
  String get fbThingOneLine =>
      'One line. It waits for your OK before it is kept.';

  @override
  String get fbThingTapHint =>
      'Tap a fact to see where it came from or to change it.';

  @override
  String fbThingExplainTold(String date) {
    return 'You told Memory this on $date.';
  }

  @override
  String fbThingExplainSeen(String source, String date) {
    return 'Memory read this from $source on $date. A source can be wrong, so check it before you rely on it.';
  }

  @override
  String fbThingExplainSeenNoSource(String date) {
    return 'Memory read this on $date. A source can be wrong, so check it before you rely on it.';
  }

  @override
  String fbThingExplainGuessed(String source, String date) {
    return 'Memory guessed this from $source on $date. Nobody has confirmed it.';
  }

  @override
  String fbThingExplainGuessedNoSource(String date) {
    return 'Memory guessed this on $date. Nobody has confirmed it.';
  }

  @override
  String fbThingExplainCalculated(String date) {
    return 'Memory worked this out from other facts on $date.';
  }

  @override
  String get fbThingExplainWaiting => 'It is waiting for your OK in Needs you.';

  @override
  String get settingsV3Servers => 'Servers';

  @override
  String get settingsV3ThisPhone => 'This phone';

  @override
  String get settingsV3Providers => 'Providers';

  @override
  String get settingsV3Shell => 'Shell for commands';

  @override
  String get settingsV3VoiceTyping => 'Voice typing';

  @override
  String get settingsV3Permissions => 'Permissions and automation';

  @override
  String get settingsV3Notifications => 'Notificaciones';

  @override
  String get settingsV3Background => 'Segundo plano';

  @override
  String get settingsV3Language => 'Language';

  @override
  String get settingsV3ByServer => 'Por servidor';

  @override
  String get settingsV3On => 'Activado';

  @override
  String get settingsV3Off => 'Desactivado';

  @override
  String get settingsV3Connected => 'Connected';

  @override
  String get settingsV3NotConnected => 'Sin conexión';

  @override
  String get settingsV3PhoneRunning => 'Running';

  @override
  String get settingsV3PhoneInstalled => 'Installed';

  @override
  String get settingsV3PhoneSetUp => 'Set up';

  @override
  String get settingsV3ServersBlock =>
      'You can change some of these for one server';

  @override
  String get settingsV3ServerGone => 'This server was removed';

  @override
  String get settingsV3SectionConnection => 'Connection';

  @override
  String get settingsV3SectionInApp => 'In this app';

  @override
  String get settingsV3StatusVersion => 'Status and version';

  @override
  String get settingsV3SignIn => 'Sign-in';

  @override
  String get settingsV3SignInPassword => 'Password';

  @override
  String get settingsV3SignInToken => 'Token';

  @override
  String get settingsV3SignInNone => 'Not set';

  @override
  String get settingsV3NameAddress => 'Name and address';

  @override
  String get settingsV3Supports => 'What this server supports';

  @override
  String get settingsV3ModelNew => 'Model for new conversations';

  @override
  String get settingsV3ServerPick => 'The server picks';

  @override
  String get settingsV3DifferentHere => 'Different here';

  @override
  String get settingsV3Watch => 'Watch this server';

  @override
  String get settingsV3ShareRead => 'Share read state';

  @override
  String get settingsV3Updates => 'Updates and service';

  @override
  String get settingsV3UseMyDefault => 'Use my default';

  @override
  String get settingsV3AgentsPhone => 'On this phone';

  @override
  String get settingsV3ModelByServerNote =>
      'Each server keeps its own choice for new conversations. Conversations already open keep their model.';

  @override
  String get settingsV3ProvidersByServerNote =>
      'Each server signs in to providers on its own. Signing in on one never signs in the others.';

  @override
  String get settingsV3ShellByServerNote =>
      'Saved on each server. Other apps using a server get its shell too.';

  @override
  String settingsV3ServerConnected(String name) {
    return '$name · Connected';
  }

  @override
  String settingsV3ServersCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servers',
      one: '1 server',
    );
    return '$_temp0';
  }

  @override
  String settingsV3DifferentOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Different on $count servers',
      one: 'Different on 1 server',
    );
    return '$_temp0';
  }

  @override
  String settingsV3ModelNewOn(String name) {
    return 'Conversaciones nuevas en $name';
  }

  @override
  String settingsV3SectionOn(String name) {
    return 'On $name';
  }

  @override
  String settingsV3SavedOn(String name) {
    return 'Saved on $name. Other apps using $name get this too.';
  }

  @override
  String settingsV3ConnectedVersion(String version) {
    return 'Connected · $version';
  }

  @override
  String settingsV3ConnectTo(String name) {
    return 'Conectar con $name';
  }

  @override
  String settingsV3UseMyDefaultDetail(String name) {
    return 'Notifications, the model for new conversations and read sharing for $name go back to your own choices.';
  }

  @override
  String get settingsV3ServerSettings => 'Ajustes del servidor';

  @override
  String get settingsV3ShellAutomatic => 'Automatic';

  @override
  String get settingsV3ReadStateByServerNote =>
      'Each server is asked separately. Nothing is shared with a server until you switch it on for that server.';

  @override
  String settingsV3SharingCount(int count, int total) {
    return '$count de $total comparten';
  }

  @override
  String get fbSettingsUsePhoneZone => 'Use this phone\'s time zone';

  @override
  String fbSettingsUsePhoneZoneDetail(String zone) {
    return '$zone. Reminders keep their clock times.';
  }

  @override
  String get fbReasonKeyLockedNotYet =>
      'It has a password or account number, and Memory can\'t save those yet. It stays here on this phone.';

  @override
  String get fbListLockedNoteNotYet =>
      'Notes with passwords or account numbers aren\'t saved yet. They wait in Not saved yet.';

  @override
  String get fbReminderNotSoundedYet =>
      'This phone can\'t ring reminders yet. It\'s saved and shows in Reminders.';

  @override
  String settingsV3ReconnectTo(String name) {
    return 'Reconnect to $name';
  }

  @override
  String get projectToolsNeedGitCommit =>
      'Worktrees and cloud environments need a project that git tracks, with at least one commit. Ask the agent to set up git and make a first commit.';

  @override
  String settingsV3UseMyDefaultDone(String name, String what) {
    return 'Reset on $name to your own choices: $what.';
  }

  @override
  String get settingsV3NotificationsAllServers =>
      'Notificaciones de todos los servidores';

  @override
  String get settingsV3AgentsOnPhone => 'Agents on this phone';

  @override
  String get settingsV3AgentsReady => 'Ready';

  @override
  String settingsV3AgentsExternalCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count connected',
      one: '1 connected',
      zero: 'None connected',
    );
    return '$_temp0';
  }

  @override
  String settingsV3NotConnectedVersion(String version) {
    return 'Not connected · last seen $version';
  }

  @override
  String get settingsV3ConnectToChange =>
      'Connect to see and change its providers, tools and permissions.';

  @override
  String quotaInAppStepChecking(String server) {
    return 'Checking $server';
  }

  @override
  String get quotaInAppStepCopying => 'Copying the usage helper in';

  @override
  String get quotaInAppStepSignIns => 'Finding your sign-ins';

  @override
  String get quotaInAppStepStarting => 'Starting the usage helper';

  @override
  String get quotaInAppStepReading => 'Reading what is left';

  @override
  String quotaInAppFailLinux(String server) {
    return '$server is not ready yet. Finish setting up On this phone, then try again.';
  }

  @override
  String quotaInAppFailNoNode(String server) {
    return '$server has no Node yet, which usage needs. Run the On this phone setup again to add it, then try again.';
  }

  @override
  String quotaInAppFailNodeOld(String server) {
    return 'Node on $server is older than version 20. Run the On this phone setup again to update it, then try again.';
  }

  @override
  String quotaInAppFailPrepare(String server) {
    return 'Usage could not be prepared on $server. Try again; if it keeps failing, restart the app.';
  }

  @override
  String quotaInAppFailStart(String server) {
    return 'The usage helper on $server did not start. Try again in a moment.';
  }

  @override
  String quotaInAppFailRemove(String server) {
    return 'Usage could not be turned off on $server. Try again.';
  }

  @override
  String get settingsV3ToolsByServerNote =>
      'Tools are what the agent can use, like reading files or running commands. Each server keeps its own set.';

  @override
  String get launchUiUsageSignInServerGone =>
      'Ese servidor ya no está guardado, así que aquí tienes tu uso.';

  @override
  String get launchUiUsageSignInConnectionFailed =>
      'No se pudo conectar con ese servidor para iniciar sesión. Revísalo en Servidores y vuelve a probar el widget.';

  @override
  String get settingsHubSearchReasoningAliases =>
      'transcript display thinking reasoning';

  @override
  String get usageTabPlans => 'Plans';

  @override
  String get usageTabSpending => 'Spending';

  @override
  String get usagePlansRefresh => 'Refresh plans';

  @override
  String get usageEmptyTitle => 'No subscriptions yet';

  @override
  String get usageEmptyBody =>
      'Add a plan to see how much is left and when it resets.';

  @override
  String get usageAddSection => 'Add a subscription';

  @override
  String usageLeft(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString% left';
  }

  @override
  String usageUsedPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString% used';
  }

  @override
  String get usageWindowFiveHour => '5 hours';

  @override
  String get usageWindowWeek => 'Week';

  @override
  String get usageWindowMonth => 'Month';

  @override
  String get usageWindowTools => 'Tools · month';

  @override
  String get usageWindowOther => 'Limit';

  @override
  String usageOnConnection(String connection) {
    return 'on $connection';
  }

  @override
  String get usageThisPhone => 'This phone';

  @override
  String get usageAlertsTitle => 'Alerts';

  @override
  String get usageAlertsSummaryOff => 'Off';

  @override
  String get usageWidgetRow => 'Home screen widget';

  @override
  String usageApiKeyOnly(String provider, String connection) {
    return '$provider API key on $connection · pay as you go, no plan limits';
  }

  @override
  String get usagePillSignInRanOut => 'Sign-in ran out';

  @override
  String get usagePillBroken => 'Can’t read';

  @override
  String get usagePillCantRead => 'Can’t read yet';

  @override
  String get usagePillOffline => 'Offline';

  @override
  String usageCantReadBody(String connection) {
    return 'Signed in on $connection. The app can’t read its limits from there yet.';
  }

  @override
  String get usageCantReadBodyOne =>
      'Signed in, but the app can’t read its limits yet.';

  @override
  String usageSignInRanOut(String provider, String connection) {
    return '$provider sign-in on $connection ran out';
  }

  @override
  String usageSignInRanOutOne(String provider) {
    return 'Your $provider sign-in ran out.';
  }

  @override
  String get usageNotSignedInAnywhere => 'Not signed in anywhere';

  @override
  String get usageNotSignedInOne => 'Not signed in';

  @override
  String usageReaderBroken(String provider) {
    return 'Can’t read $provider right now';
  }

  @override
  String get usageNoLimits => 'No limits reported for this plan';

  @override
  String usageOfflineStale(String connection, String age) {
    return '$connection is offline · read $age ago';
  }

  @override
  String usageOfflineStaleOne(String age) {
    return 'Offline · read $age ago';
  }

  @override
  String usageDurationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String usageDurationHours(int hours) {
    return '$hours h';
  }

  @override
  String usageDurationHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String usageDurationDays(int days) {
    return '$days d';
  }

  @override
  String usageDurationDaysHours(int days, int hours) {
    return '$days d $hours h';
  }

  @override
  String usageResetsIn(String time, String duration) {
    return 'Resets at $time, in $duration';
  }

  @override
  String get usageResetPassed => 'The reset time has passed. Refresh to check.';

  @override
  String usageResetsAt(String time) {
    return 'Resets $time';
  }

  @override
  String usageFiveHourResets(String time) {
    return '5-hour resets $time';
  }

  @override
  String usageWeekResets(String time) {
    return 'week resets $time';
  }

  @override
  String usageOutUntil(String time) {
    return 'Out until $time';
  }

  @override
  String usageResetSince(String time) {
    return 'Reset at $time since the last reading';
  }

  @override
  String usageReadAgo(String age) {
    return 'Read $age ago';
  }

  @override
  String usageCheckedAgo(String age) {
    return 'Checked $age ago';
  }

  @override
  String usageReadFrom(String connection) {
    return 'Read usage from $connection';
  }

  @override
  String get usageSignIn => 'Iniciar sesión';

  @override
  String get usageSignInAgain => 'Sign in again';

  @override
  String get usageAddKey => 'Add key';

  @override
  String get usageTryAgain => 'Try again';

  @override
  String get usageSignInUnavailable =>
      'There is nowhere to sign in to from here yet.';

  @override
  String usageAddTitle(String provider) {
    return 'Add $provider';
  }

  @override
  String usageAddWhere(String provider) {
    return 'Where should $provider be signed in? The app reads usage from whichever you pick.';
  }

  @override
  String get usageBest => 'Best';

  @override
  String get usageAddInAppNote => 'On this phone · usage works straight away';

  @override
  String get usageAddServerNote => 'A server you saved';

  @override
  String usageAddPhoneNote(String provider) {
    return 'Signs in the $provider agent on this phone';
  }

  @override
  String usageAddNoneTitle(String provider) {
    return 'Can’t add $provider yet';
  }

  @override
  String get usageAddNoneBody =>
      'Save a server, or set up On this phone, and it will appear here.';

  @override
  String get usageReadFromTitleHere => 'Read usage';

  @override
  String get usageReadFromGoneTitle => 'That connection is gone';

  @override
  String get usageReadFromGoneBody => 'Go back to Plans to see what is left.';

  @override
  String usageReadFromSignedIn(String providers, String connection) {
    return '$providers signed in on $connection';
  }

  @override
  String get usageReadFromBodyInApp =>
      'To show their limits, the app starts a small reader in On this phone. It only reads how much is left, never your conversations or keys.';

  @override
  String get usageReadFromSetUpInApp => 'Set up usage';

  @override
  String get usageNotNow => 'Not now';

  @override
  String get usageSubGoneTitle => 'This plan is gone';

  @override
  String get usageSubGoneBody => 'Go back to Plans to see what is left.';

  @override
  String get usageStateSignedIn => 'Signed in';

  @override
  String get usageStateExpired => 'Sign-in ran out';

  @override
  String get usageStateNotSignedIn => 'Not signed in';

  @override
  String get usageConnOffline => 'Offline';

  @override
  String get usageConnReadsHere => 'Numbers come from here';

  @override
  String get usageSameAccount => 'Same account';

  @override
  String get usageSignedInOn => 'Signed in on';

  @override
  String get usageAlertAt => 'Alert me at';

  @override
  String usageAlertAtPlan(String provider, int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'For $provider only. Other plans use $percentString%.';
  }

  @override
  String get usageAlertAtSame => 'Same as your other plans';

  @override
  String usageAlertSameAsOthers(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'Same as other plans ($percentString%)';
  }

  @override
  String get usageNotifyReset => 'Tell me when it resets';

  @override
  String get usageNotifyResetHelp =>
      'A message when a limit that ran out comes back.';

  @override
  String get usageSaveFailed => 'Couldn’t save this. It stays as it was.';

  @override
  String get usageDetails => 'Details';

  @override
  String get usageDetailReader => 'Reader';

  @override
  String get usageDetailRead => 'Read';

  @override
  String get usageDetailChecked => 'Checked';

  @override
  String get usageDetailAccount => 'Account';

  @override
  String get usageDetailWindows => 'Windows';

  @override
  String get usageYes => 'Yes, an account is known';

  @override
  String get usageNo => 'No account known';

  @override
  String get usageAlertsSwitch => 'Usage alerts';

  @override
  String get usageAlertsDifferent => 'Different from default';

  @override
  String get usageAlertOverrideResetOn => 'tells you when it resets';

  @override
  String get usageAlertOverrideResetOff => 'no reset message';

  @override
  String get usageAlertsNothingToWatch =>
      'Nothing to watch yet. Alerts start when a plan can be read.';

  @override
  String get usageWidgetSettingsShow => 'Show these plans';

  @override
  String get usageWidgetSettingsWindows => 'Show 5-hour, weekly or both';

  @override
  String get usageWidgetWindowBoth => 'Both';

  @override
  String get usageWidgetWindowFiveHour => '5 hours';

  @override
  String get usageWidgetWindowWeekly => 'Weekly';

  @override
  String get usageWidgetSettingsSetup => 'Show rows that need setup';

  @override
  String get usageWidgetSettingsSetupHelp => 'Sign in, add key and set up rows';

  @override
  String get usageSources => 'Sources';

  @override
  String get usageNoKey => 'No key added';

  @override
  String get usageStateCouldntCheck => 'Couldn’t check';

  @override
  String usageViaOpenCodeNoPlan(String connection) {
    return 'Anthropic on $connection · no plan limits through OpenCode';
  }

  @override
  String get usageNotReadYet => 'Not read yet';

  @override
  String get usageConnectionsHeader => 'Connections';

  @override
  String get usageReadUsage => 'Read usage';

  @override
  String usageSetupSignedInOn(String connection) {
    return 'Signed in on $connection';
  }

  @override
  String get usageSetupSignedIn => 'Signed in';

  @override
  String usageSetupRanOutOn(String connection) {
    return 'Sign-in ran out on $connection';
  }

  @override
  String get usageSetupRanOut => 'Sign-in ran out';

  @override
  String get usageSetupCouldntRead => 'Couldn’t read';

  @override
  String get usageNeedsSetupHeader => 'Needs setup';

  @override
  String get usageNoNumbersYet => 'No plan numbers yet. Set one up below.';

  @override
  String usageComputerExplain(String server) {
    return 'The app runs a small read-only check on $server through OpenCode each time it refreshes. Nothing is installed and nothing keeps running. It sends back only how much is left, never your keys or conversations.';
  }

  @override
  String get usageComputerWhatRuns => 'What runs';

  @override
  String get usageComputerCommandLabel => 'Command';

  @override
  String get usageComputerReadsLabel => 'Reads';

  @override
  String usageComputerReadsValue(String server) {
    return 'OpenCode’s sign-in file on $server, and the Codex CLI’s for Codex. Read only; nothing is written.';
  }

  @override
  String get usageComputerTurnOn => 'Turn on';

  @override
  String get usageComputerTurnOff => 'Turn off';

  @override
  String get usageComputerReadNow => 'Read now';

  @override
  String get usageComputerDone => 'Done';

  @override
  String usageComputerChecking(String server) {
    return 'Checking $server…';
  }

  @override
  String usageComputerFound(String plans, String server) {
    return 'Found $plans on $server.';
  }

  @override
  String usageComputerFoundNone(String server) {
    return 'The check worked, but no plan is signed in on $server.';
  }

  @override
  String usageComputerOnNote(String server) {
    return 'Reading $server through OpenCode.';
  }

  @override
  String usageComputerNeedsNode(String server) {
    return '$server needs Node.js 20 or later.';
  }

  @override
  String usageComputerServerTooOld(String server) {
    return 'OpenCode on $server is too old for this. Update it and try again.';
  }

  @override
  String usageComputerOffline(String server) {
    return 'Can’t reach $server right now.';
  }

  @override
  String usageComputerNoAnswer(String server) {
    return 'The check on $server didn’t finish. Try again.';
  }

  @override
  String usageComputerRowTitle(String server) {
    return 'Reading $server';
  }

  @override
  String get usageDetailLastCheck => 'Last check';

  @override
  String get usagePhoneNotRunning => 'Not running';

  @override
  String get kitRequestNeedsYouLabel => 'Te necesita';

  @override
  String get kitRequestAlwaysAllowLike =>
      'Permitir siempre solicitudes como esta';

  @override
  String get chatRequestAlwaysAllowCommands =>
      'Permitir siempre comandos como este';

  @override
  String get chatRequestAskCommand => '¿Ejecutar este comando?';

  @override
  String get chatRequestAskEdit => '¿Editar este archivo?';

  @override
  String get chatRequestAskRead => '¿Leer este archivo?';

  @override
  String get chatRequestAskDirectory => '¿Abrir esta carpeta?';

  @override
  String get chatRequestAskFetch => '¿Abrir esta página web?';

  @override
  String usageBackAt(String time) {
    return 'Back at $time';
  }

  @override
  String get usageAlertsOnWord => 'On';

  @override
  String usageWidgetPlans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count plans',
      one: '1 plan',
      zero: 'No plans',
    );
    return '$_temp0';
  }

  @override
  String get usageAlertsForEvery => 'For every plan the app can read';

  @override
  String get usageAlertsTellMe => 'Tell me when';

  @override
  String get usageAlertsQuietGroup => 'Quiet';

  @override
  String get usageAlertPlanDownTo => 'A plan is down to';

  @override
  String get usageAlertUsedUpBack => 'A used-up plan is back';

  @override
  String get usageQuietSameAsOthers => 'Same as your other alerts';

  @override
  String get usageWifiOnlyShort => 'Check only on Wi-Fi';

  @override
  String kitTaskChipCount(int done, int total) {
    return '$done/$total';
  }

  @override
  String kitTaskChipDone(int done, int total) {
    return '✓ $done/$total';
  }

  @override
  String kitTaskChipAdded(int done, int total) {
    return '+ $done/$total';
  }

  @override
  String kitTaskChipSpoken(int done, int total) {
    return 'Tasks, $done of $total done';
  }

  @override
  String kitTaskChipSpokenNow(int done, int total, String task) {
    return 'Tasks, $done of $total done, now $task';
  }

  @override
  String get toolCardUpdatedTasks => 'Updated tasks';

  @override
  String kitDiffFoldAbove(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines above',
      one: '1 line above',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBelow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines below',
      one: '1 line below',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBetween(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines',
      one: '1 line',
    );
    return '… $_temp0';
  }

  @override
  String notifStatusNeedsYou(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count te necesitan',
      one: '1 te necesita',
    );
    return '$_temp0';
  }

  @override
  String notifStatusStillWorking(int count) {
    return '$count siguen en curso';
  }

  @override
  String notifStatusChatsWorking(int count) {
    return '$count conversaciones en curso';
  }

  @override
  String notifStatusMore(int count) {
    return '+$count más';
  }

  @override
  String notifStatusWaitingCount(int count) {
    return '$count en espera';
  }

  @override
  String notifStatusTasksProgress(int done, int total) {
    return '$done de $total tareas';
  }

  @override
  String notifStatusMinutes(int count) {
    return '$count min';
  }

  @override
  String notifStatusHours(int count) {
    return '$count h';
  }

  @override
  String notifStatusWaitingProvider(String provider) {
    return 'En espera · límite de $provider';
  }

  @override
  String get notifStatusWaitingGeneric =>
      'En espera · el proveedor está ocupado';

  @override
  String notifStatusTriesAgainAt(String time) {
    return 'Reintenta a las $time';
  }

  @override
  String get notifStatusTriesAgainSoon => 'Reintentará pronto';

  @override
  String get notifStatusPausedAndroidTitle =>
      'En pausa · límite de segundo plano de Android';

  @override
  String get notifStatusPausedAndroidBody =>
      'Android detiene el trabajo en segundo plano tras 6 horas al día. Abre OpenCode para continuar.';

  @override
  String get notifStatusPausedHotTitle =>
      'En pausa · el teléfono está caliente';

  @override
  String get notifStatusPausedHotBody => 'Se reanuda cuando se enfríe';

  @override
  String get notifStatusPausedIdleTitle => 'En pausa · nada en ejecución';

  @override
  String get notifStatusPausedIdleBody => 'Toca para iniciar OpenCode de nuevo';

  @override
  String notifStatusReconnectingTitle(String server) {
    return 'Reconectando con $server';
  }

  @override
  String get notifStatusReconnectingGeneric => 'Reconectando';

  @override
  String get notifStatusReconnectingBody => 'Esperando la conexión';

  @override
  String get notifStatusReadyTitle => 'Listo · nada en ejecución';

  @override
  String get notifStatusReadyBody => 'Listo cuando quieras';

  @override
  String notifStatusLastReply(String ago) {
    return 'Última respuesta hace $ago';
  }

  @override
  String get notifStatusLastReplyNow => 'Última respuesta hace un momento';

  @override
  String notifStatusSetupTitle(int step, int steps) {
    return 'Configuración · $step de $steps';
  }

  @override
  String notifStatusOnServer(String server) {
    return 'en $server';
  }

  @override
  String get notifStatusOnThisPhone => 'en este teléfono';

  @override
  String notifStatusPublicWorking(int count) {
    return '$count en curso';
  }

  @override
  String get notifStatusPublicWaiting => 'Esperando';

  @override
  String get notifStatusPublicPaused => 'En pausa';

  @override
  String get notifStatusPublicReconnecting => 'Reconectando';

  @override
  String get notifStatusPublicReady => 'Listo';

  @override
  String get notifStatusPublicSetup => 'Configurando';

  @override
  String get integrationsSignInCodeLabel => 'Your code';

  @override
  String get integrationsSignInCodeHint =>
      'Open browser copies the code for you. Paste it on the page that opens.';

  @override
  String get integrationsCodeCopied => 'Code copied';

  @override
  String get integrationsCopyCode => 'Copiar código';

  @override
  String get integrationsOpenBrowserAgain => 'Abrir el navegador de nuevo';

  @override
  String get integrationsSignInWaitingBrowser =>
      'Esperando la aprobación en el navegador';

  @override
  String get integrationsSignInWaitingDetail =>
      'Ingresa el código en tu navegador y aprueba. Esta app está esperando y termina el inicio de sesión sola cuando vuelvas.';

  @override
  String get integrationsSignInEnded => 'El inicio de sesión terminó';

  @override
  String get integrationsSignInEndedDetail =>
      'Este inicio de sesión terminó antes de ser aprobado. Puede que el código haya caducado o que el servidor se haya reiniciado. No se agregó nada.';

  @override
  String get integrationsStartAgain => 'Empezar de nuevo';

  @override
  String get mentionToolTitle => 'Archivo del proyecto';

  @override
  String get mentionToolSupporting =>
      'Indica al agente un archivo de este proyecto';

  @override
  String get mentionSheetTitle => 'Project files';

  @override
  String get mentionSearchLabel => 'Find a file by name';

  @override
  String get mentionSearchHint =>
      'Type part of a file name to search this project.';

  @override
  String get mentionSearchFailedTitle => 'Search did not finish';

  @override
  String get mentionSearchFailedMessage =>
      'Check the connection to the server and try again.';

  @override
  String get mentionNoMatchWhat => 'project files';

  @override
  String get mentionProjectFolder => 'Project folder';

  @override
  String get inAppServerOwnershipRecoveryBody =>
      'El servidor de este teléfono no pudo reiniciarse de forma segura. En los ajustes de la app, toca Forzar detención y vuelve a abrir la app; tus conversaciones se conservan.';

  @override
  String get liveStyleAurora => 'Aurora drift';

  @override
  String get liveStyleMesh => 'Breathing mesh';

  @override
  String get liveStyleDotRipple => 'Dot ripple';

  @override
  String get liveStyleTopo => 'Contour lines';

  @override
  String get liveStyleFlowThreads => 'Flow threads';

  @override
  String get liveStyleGlowSpill => 'Glow spill';

  @override
  String get liveStyleLightRays => 'Light rays';

  @override
  String get liveStyleConstellation => 'Constellation';

  @override
  String get liveStyleHalftone => 'Halftone';

  @override
  String get liveStyleLiquidBlobs => 'Liquid blobs';

  @override
  String get liveStyleMatrixRain => 'Matrix rain';

  @override
  String get liveStyleBlueprint => 'Blueprint';

  @override
  String get liveStyleCircuit => 'Circuit';

  @override
  String get liveStyleOscilloscope => 'Oscilloscope';

  @override
  String get liveStyleRadar => 'Radar';

  @override
  String get liveStyleHoneycomb => 'Honeycomb';

  @override
  String get liveStyleBrushedMetal => 'Brushed metal';

  @override
  String get liveStyleSilk => 'Silk';

  @override
  String get liveStyleCaustics => 'Caustics';

  @override
  String get liveStyleBokeh => 'Bokeh';

  @override
  String get liveIntensitySubtle => 'Sutil';

  @override
  String get liveIntensityNormal => 'Normal';

  @override
  String get liveIntensityBold => 'Intensa';

  @override
  String get effectsLiveBackground => 'Fondo animado';

  @override
  String get continueWithMenu => 'Continue with…';

  @override
  String get continueWithMenuHint =>
      'Carry this work to another agent or model';

  @override
  String get continueWithTitle => 'Continue this work';

  @override
  String continueWithSubtitle(String title, String project) {
    return '$title · $project';
  }

  @override
  String get continueWithModelsHere => 'Models on this server';

  @override
  String get continueWithAgents => 'Agents on this phone';

  @override
  String get continueWithNothingReady =>
      'No other agent or model is ready to take over.';

  @override
  String get continueWithLoadFailed => 'Couldn\'t load the agents. Try again.';

  @override
  String get continueWithTryAgain => 'Try again';

  @override
  String get continueWithSignIn => 'Sign in';

  @override
  String continueWithNeedsSignIn(String agent) {
    return 'Sign in to use $agent.';
  }

  @override
  String continueWithLimitReached(String agent) {
    return '$agent has reached its limit for now.';
  }

  @override
  String continueWithNotReady(String agent) {
    return '$agent isn\'t ready on this phone yet.';
  }

  @override
  String get continueWithNativeNote => 'Your conversation stays here.';

  @override
  String get continueWithUseModel => 'Use this model';

  @override
  String get continueWithModelFailed =>
      'Couldn\'t change the model. Try again.';

  @override
  String get continueWithNewNote =>
      'Start a new conversation with a record of this one.';

  @override
  String get continueWithSourceStays => 'This conversation stays available.';

  @override
  String get continueWithFactAgent => 'Agent';

  @override
  String get continueWithFactModel => 'Model';

  @override
  String get continueWithFactProject => 'Project';

  @override
  String get continueWithPreparing => 'Preparing the conversation record…';

  @override
  String get continueWithSaving => 'Saving the conversation record…';

  @override
  String get continueWithStarting => 'Starting the conversation…';

  @override
  String get continueWithLinking => 'Linking the conversations…';

  @override
  String get continueWithRecordTitle => 'Conversation record';

  @override
  String continueWithRecordSize(int kb) {
    return 'About $kb KB';
  }

  @override
  String get continueWithOlderLeftOut =>
      'Older turns were left out to keep the record small';

  @override
  String continueWithOmittedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count messages left out',
      one: '1 message left out',
    );
    return '$_temp0';
  }

  @override
  String get continueWithRedacted =>
      'Anything that looked like a password or key was hidden.';

  @override
  String get continueWithReadPreview =>
      'Read the preview first. It can\'t catch every private detail.';

  @override
  String get continueWithShowPreview => 'Show preview';

  @override
  String get continueWithHidePreview => 'Hide preview';

  @override
  String continueWithRecordFile(String path) {
    return 'Saved as $path';
  }

  @override
  String get continueWithFirstMessage => 'First message';

  @override
  String get continueWithFirstMessageHelp =>
      'Keep the file name so the agent can read the record.';

  @override
  String get continueWithMessageInvalid =>
      'Write a first message that keeps the record\'s file name.';

  @override
  String get continueWithStart => 'Start and continue';

  @override
  String get continueWithCancel => 'Cancel';

  @override
  String get continueWithPrepareAgain => 'Prepare again';

  @override
  String get continueWithSaveLinkAgain => 'Save link again';

  @override
  String get continueWithOpenConversation => 'Open the conversation';

  @override
  String get continueWithAnother => 'Continuar con otro agente';

  @override
  String get continueWithErrNoWriter =>
      'This agent can\'t save a conversation record here yet.';

  @override
  String get continueWithErrProject =>
      'These conversations aren\'t connected to the same project.';

  @override
  String get continueWithErrTarget =>
      'This model isn\'t available. Choose another one.';

  @override
  String get continueWithErrSource =>
      'This conversation changed. Prepare the record again.';

  @override
  String get continueWithErrScope =>
      'The server or project changed. Start again.';

  @override
  String get continueWithErrRead =>
      'Couldn\'t read the conversation. Try preparing it again.';

  @override
  String get continueWithErrTooLarge =>
      'The latest turn is too large to carry over.';

  @override
  String get continueWithErrFile =>
      'Couldn\'t save the record. No message was sent.';

  @override
  String get continueWithErrUnknown =>
      'The result isn\'t confirmed. Check the target conversations before trying again.';

  @override
  String get continueWithErrLink =>
      'The conversation started, but its link couldn\'t be saved.';

  @override
  String get continueWithErrGeneric =>
      'Couldn\'t prepare the record. Try again.';

  @override
  String continuedIn(String agent) {
    return 'Continued in $agent';
  }

  @override
  String continuedFrom(String title) {
    return 'Continued from $title';
  }

  @override
  String continuedFromShort(String title) {
    return 'from $title';
  }

  @override
  String continuedInShort(String agent) {
    return 'continued in $agent';
  }

  @override
  String get continuedGoneShort => 'other conversation gone';

  @override
  String get continuedGone => 'Esta conversación ya no está disponible.';

  @override
  String get continueWithChange => 'Change';

  @override
  String get workServerBusyPhone => 'OpenCode en este teléfono está ocupado';

  @override
  String get workServerRestartBodyCommand =>
      'El comando en ejecución también se detendrá. Tus conversaciones se conservan.';

  @override
  String get connectionReconnectingQuiet => 'Reconectando…';

  @override
  String get connectionTryNow => 'Intentar ahora';

  @override
  String kitComposerWaitsForAgent(String agent) {
    return 'Se envía cuando $agent responda';
  }

  @override
  String get textSizeTitle => 'Tamaño del texto';

  @override
  String get textSizeSmallest => 'Muy pequeño';

  @override
  String get textSizeSmall => 'Pequeño';

  @override
  String get textSizeDefault => 'Predeterminado';

  @override
  String get textSizeLarge => 'Grande';

  @override
  String get kitComposerAddToTurnNote =>
      'El agente leerá esto mientras trabaja.';

  @override
  String get continuedOpenFromList =>
      'Find it in that server\'s conversations.';

  @override
  String get teamModelsSection => 'Models';

  @override
  String get teamModelsNote =>
      'Each new job starts with the model chosen for its role. A saved choice applies to new jobs, not to ones already running.';

  @override
  String get teamModelServerDefault => 'Server default';

  @override
  String get teamModelServerDefaultHint =>
      'Uses the model this server falls back to when none is chosen. It may differ from the model your conversations use.';

  @override
  String get teamModelPlannerSaved =>
      'Planner model saved. Ask again to plan with it.';

  @override
  String get teamProjectEditorTypePath => 'Type a path';

  @override
  String get teamProjectEditorChooseFolder => 'Choose a folder';

  @override
  String get teamProjectEditorFolderNone => 'No folder chosen yet';

  @override
  String get teamProjectEditorFirstCommit => 'Make the first commit for me';

  @override
  String get teamProjectEditorFirstCommitManual =>
      'Open a terminal in that folder and run: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorFirstCommitFailed =>
      'The first commit could not be made. Open a terminal in that folder and run: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorStarting =>
      'Checking the folder and starting the planner…';

  @override
  String get teamProjectEditorDraftRestored => 'Your unsent draft is back.';

  @override
  String get teamProjectEditorStartFresh => 'Start fresh';

  @override
  String get teamProjectEditorStarterNote =>
      'A first milestone was drafted from your task. Edit it before you approve.';

  @override
  String get teamRefusalRepoPathInvalid =>
      'The team can\'t find that folder on this phone.';

  @override
  String get teamRefusalRepoPathInvalidNext =>
      'Choose the folder from the list instead of typing it.';

  @override
  String effectsLookPreviewLabel(String name) {
    return '$name preview. Opens the looks.';
  }

  @override
  String get effectsLookPreviewOffLabel =>
      'Live background is off. Opens the looks.';

  @override
  String get effectsLookPreviewOff => 'Off';

  @override
  String get teamProjectEditorFolderEmpty =>
      'This folder has no commits yet, and the team needs one to start from.';

  @override
  String get teamProjectEditorChooseModeBudget =>
      'Choose Single lane or Parallel agents, and set a budget or choose No limit.';

  @override
  String get teamProjectEditorFallbackSection => 'Optional';

  @override
  String get teamProjectEditorFallbackNote =>
      'Saved with the role, but the team does not switch to it automatically yet.';

  @override
  String get teamUiRowChecking => 'Checking…';

  @override
  String get teamRefusalCheckerUnreadable =>
      'The check finished, but its answer could not be read.';

  @override
  String get teamRefusalCheckerUnreadableNext =>
      'Resume reads that answer again without asking the model. If the work passes, it merges into dev, and tasks waiting behind it can start.';

  @override
  String get teamProjectInterruptedResumeNote =>
      'Resume reads the finished check again without asking the model. If the work passes, it merges into dev, and tasks waiting behind it can start.';

  @override
  String get teamProjectInterruptedResume => 'Resume';

  @override
  String get widgetV2ConfigureTitle => 'Estilo del widget';

  @override
  String get widgetProjectTitle => 'Show conversations from';

  @override
  String get widgetProjectUnavailable =>
      'Couldn’t save this choice. Try again.';

  @override
  String get widgetV2BackgroundLabel => 'Fondo';

  @override
  String get widgetV2BackgroundSolid => 'Sólido';

  @override
  String get widgetV2BackgroundTinted => 'Teñido';

  @override
  String get widgetV2BackgroundTransparent => 'Transparente';

  @override
  String get widgetV2AccentLabel => 'Color de énfasis';

  @override
  String get widgetV2AccentWallpaper => 'Fondo de pantalla';

  @override
  String get widgetV2AccentApp => 'Tema de la app';

  @override
  String get widgetV2AccentGreen => 'Verde';

  @override
  String get widgetV2AccentTeal => 'Turquesa';

  @override
  String get widgetV2AccentBlue => 'Azul';

  @override
  String get widgetV2AccentAmber => 'Ámbar';

  @override
  String get widgetV2SaveStyle => 'Guardar estilo';

  @override
  String get widgetV2SteerTitle => 'Agregar a este turno';

  @override
  String get widgetV2AfterReplyTitle => 'Enviar tras esta respuesta';

  @override
  String get widgetV2VoiceTitle => 'Mensaje nuevo';

  @override
  String get widgetV2MessageHint => 'Escribe un mensaje';

  @override
  String get widgetV2Dictate => 'Dictar un mensaje';

  @override
  String get widgetV2Send => 'Enviar';

  @override
  String get widgetV2Sending => 'Enviando…';

  @override
  String get widgetV2Accepted => 'Enviado';

  @override
  String get widgetV2StaleRequest =>
      'La conversación cambió. Tu mensaje sigue aquí.';

  @override
  String get widgetV2Unavailable =>
      'La conexión en segundo plano no está disponible. Tu mensaje sigue aquí.';

  @override
  String get widgetV2Unsupported => 'Esta conexión no puede hacer eso.';

  @override
  String get widgetV2Uncertain =>
      'No pudimos confirmar si se envió. Revisa la conversación antes de enviar de nuevo.';

  @override
  String get widgetV2Busy => 'Espera a que termine la acción actual.';

  @override
  String get widgetV2InvalidRequest => 'Escribe un mensaje antes de enviar.';

  @override
  String get widgetV2UnknownBehavior =>
      'Esperando los detalles de la conversación…';

  @override
  String get widgetV2Cancel => 'Cancelar';

  @override
  String get widgetV2PreviewLabel => 'Vista previa del widget';

  @override
  String get widgetV2PreviewTitle => 'Arreglar la página de inicio de sesión';

  @override
  String get widgetV2PreviewSecond => 'Escribir las notas de la versión';

  @override
  String get widgetV2PreviewStep => 'Editando archivos';

  @override
  String get widgetV2PreviewWorking => 'Trabajando';

  @override
  String get widgetV2PreviewStop => 'Detener';

  @override
  String get widgetV2PreviewSteer => 'Guiar';

  @override
  String get widgetV2PreviewAllow => 'Permitir';

  @override
  String get widgetV2SizeLabel => 'Tamaño de la vista previa';

  @override
  String get widgetV2SizeCompact => 'Pequeño';

  @override
  String get widgetV2SizeCard => 'Mediano';

  @override
  String get widgetV2SizeList => 'Ancho';

  @override
  String get widgetV2StyleStale =>
      'Este widget cambió. Ciérralo e inténtalo de nuevo.';

  @override
  String get widgetV2StyleUnavailable =>
      'No se pudo guardar el estilo. Inténtalo de nuevo.';

  @override
  String get widgetV2DictationUnavailable =>
      'El dictado no está disponible ahora. Puedes escribir.';

  @override
  String get widgetV2RestartUnavailable =>
      'Esta conversación solo se puede cambiar desde la app.';

  @override
  String get phoneSetupErrorPackageRepair =>
      'Algunos paquetes de Linux necesitan reparación. Continúa la configuración para intentarlo de nuevo. Los detalles muestran qué lo detuvo.';

  @override
  String get agentsFailNeedsSignIn =>
      'Claude Code necesita que inicies sesión en este teléfono.';

  @override
  String get agentsFailHelperVersion =>
      'Paseo necesita una actualización antes de que este agente pueda conectarse. Actualízalo e inténtalo de nuevo.';

  @override
  String get agentsFailTimeout =>
      'El teléfono tardó demasiado en terminar la comprobación. Reintenta cuando esté menos ocupado.';

  @override
  String get agentsCheckRetry => 'Intentar de nuevo';

  @override
  String get agentsCheckUpdateHelper => 'Actualiza Paseo e inténtalo de nuevo';

  @override
  String get agentsCheckPrevious => 'Usar la versión anterior';

  @override
  String agentsCheckPreviousNote(String version) {
    return 'La versión $version sigue en este teléfono. Se comprobará antes de que la uses. Tu sesión y tus conversaciones se conservan.';
  }

  @override
  String get agentsCheckAgentChanged =>
      'Cambió la versión requerida del agente. Comprueba este teléfono de nuevo.';

  @override
  String get agentsCheckHelperChanged =>
      'Cambió la versión requerida de Paseo. Comprueba la conexión de nuevo.';

  @override
  String get agentsCheckInstallChanged =>
      'La configuración del teléfono cambió. Compruébala de nuevo.';

  @override
  String agentsCheckDownload(
    String component,
    String downloaded,
    String total,
  ) {
    return 'Descargando $component · $downloaded de $total';
  }

  @override
  String agentsCheckDownloadUnknown(String component, String downloaded) {
    return 'Descargando $component · $downloaded';
  }

  @override
  String agentsCheckDownloading(String component) {
    return 'Descargando $component';
  }

  @override
  String agentsCheckPreparing(String component) {
    return 'Preparando $component';
  }

  @override
  String get agentsCheckPhoneTools => 'Herramientas del teléfono';

  @override
  String get phoneSetupErrorInterrupted =>
      'La configuración se detuvo antes de terminar. Continúa para intentarlo de nuevo. Los pasos completados se conservan.';

  @override
  String get agentErrorProviderAuth =>
      'El proveedor del modelo necesita un inicio de sesión válido.';

  @override
  String get agentErrorProviderAuthHint =>
      'Abre los ajustes del proveedor para iniciar sesión o reemplazar la clave de API, y vuelve a intentarlo.';

  @override
  String get agentErrorUnknown => 'La solicitud no pudo terminar.';

  @override
  String get agentErrorUnknownHint =>
      'Revisa la conversación antes de enviar de nuevo. Los detalles pueden ayudar a explicar qué pasó.';

  @override
  String get serverTrustHttpTitle => '¿Usar HTTP sin cifrar?';

  @override
  String get serverTrustHttpPublicBody =>
      'En esta dirección HTTP pública, tu contraseña y código viajan sin cifrar y otros en la red pueden leerlos o modificarlos. Es arriesgado en Wi-Fi público; prefiere HTTPS o una conexión privada de Tailscale.';

  @override
  String get serverTrustHttpAccept => 'Permitir HTTP para este servidor';

  @override
  String get serverTrustCertificateTitle => '¿Confiar en este certificado?';

  @override
  String get serverTrustCertificateBody =>
      'Esta conexión está cifrada, pero este dispositivo no puede verificar el emisor del certificado ni el nombre del servidor. Compara esta huella SHA-256 con la de tu servidor por otro canal de confianza y confía solo si coinciden; las próximas conexiones exigirán este mismo certificado.';

  @override
  String get serverTrustCertificateChangedTitle =>
      'El certificado del servidor cambió';

  @override
  String get serverTrustCertificateChangedBody =>
      'El certificado ya no coincide con el que aceptaste, así que la app bloqueó la conexión antes de enviar tu contraseña. Puede ser una renovación o una conexión interceptada; vuelve a comprobar la huella con el dueño del servidor antes de confiar en uno nuevo.';

  @override
  String get serverTrustCertificateInspect => 'Comprobar certificado';

  @override
  String get serverTrustCertificateRecheck =>
      'Volver a comprobar el certificado';

  @override
  String get serverTrustCertificateAccept => 'Confiar en esta huella';

  @override
  String get serverTrustCertificateReplace => 'Confiar en el certificado nuevo';

  @override
  String get serverTrustCertificatePinned =>
      'Este servidor usa la huella del certificado en la que confiaste. Un certificado distinto bloqueará la conexión.';

  @override
  String get serverTrustCertificateFailed =>
      'No se pudo comprobar el certificado. Revisa la dirección y la red e inténtalo de nuevo. No se confió en nada.';

  @override
  String get serverTrustFingerprint => 'Huella SHA-256';

  @override
  String get serverTrustInvalidPort =>
      'Escribe un puerto de servidor entre 1 y 65535.';

  @override
  String get serverTrustHttpUnconfirmed =>
      'Este servidor usa HTTP sin cifrar y aún no lo has confirmado. Edita el servidor para revisar y aprobar la advertencia de HTTP, o usa HTTPS.';

  @override
  String get serverTrustProbeFailed =>
      'No se pudo comprobar el servidor. Revisa la dirección e inténtalo de nuevo.';

  @override
  String get serverTrustPasswordRejected =>
      'Contraseña rechazada. Revisa la contraseña del servidor e inténtalo de nuevo.';

  @override
  String serverTrustCertificateValidity(String from, String to) {
    return 'Válido del $from al $to';
  }

  @override
  String get serverTrustCertificateValidityError =>
      'El certificado del servidor ha caducado o aún no es válido. Pide al propietario del servidor que lo renueve o revisa el reloj del dispositivo antes de reconectar.';

  @override
  String get devPreviewTitle => 'Vista previa';

  @override
  String get devPreviewReload => 'Actualizar página';

  @override
  String get devPreviewOpenInBrowser => 'Abrir en el navegador';

  @override
  String get devPreviewCopyAddress => 'Copiar dirección';

  @override
  String get devPreviewLoading => 'Cargando la vista previa';

  @override
  String get devPreviewFailedTitle => 'Aún no responde nada';

  @override
  String devPreviewFailedBody(String port) {
    return 'El puerto $port no responde. Puede que la app aún esté arrancando.';
  }

  @override
  String get devPreviewRetry => 'Intentar de nuevo';

  @override
  String devPreviewRemoteNote(String port, String host) {
    return 'Mostrando el puerto $port en $host. El servidor de desarrollo debe escuchar en 0.0.0.0 (todas las direcciones) para que este teléfono pueda alcanzarlo.';
  }

  @override
  String get devPreviewNoServerTitle => 'Conecta un servidor primero';

  @override
  String get devPreviewNoServerBody =>
      'Un enlace a esta máquina solo tiene sentido en la máquina que ejecuta la app. Conecta un servidor o abre el enlace en un navegador.';

  @override
  String get devPreviewNoHostTitle => 'No se encuentra ese servidor';

  @override
  String get devPreviewNoHostBody =>
      'La dirección de este servidor está incompleta, así que el enlace no puede apuntar a él. Ábrelo en un navegador.';

  @override
  String get devPreviewUnsupportedTitle =>
      'La vista previa no está disponible aquí';

  @override
  String get devPreviewUnsupportedBody =>
      'Este dispositivo no puede mostrar la página dentro de la app. Ábrela en un navegador.';

  @override
  String get devPreviewMenu => 'Vista previa de una app en ejecución';

  @override
  String get devPreviewMenuHint =>
      'Open a dev server this conversation mentioned';

  @override
  String get devPreviewSheetSubtitle =>
      'Servidores que mencionó esta conversación';

  @override
  String devPreviewPortTitle(String port) {
    return 'Puerto $port';
  }

  @override
  String get devPreviewOnPhone => 'En este teléfono';

  @override
  String devPreviewOnServer(String host) {
    return 'En $host';
  }

  @override
  String get openCode1UnsupportedTitle => 'Actualiza este servidor';

  @override
  String get openCode1Unsupported =>
      'Este servidor ejecuta OpenCode 1, que esta app ya no admite. Actualízalo a OpenCode 2.';

  @override
  String get openCode2UpgradeTitle => 'Actualizando a OpenCode 2';

  @override
  String get openCode2UpgradeFailed =>
      'La actualización no pudo terminar de forma segura. Tus conversaciones y archivos de OpenCode 1 siguen en este teléfono, intactos. Inténtalo de nuevo cuando el servidor esté inactivo y haya suficiente espacio libre.';

  @override
  String get openCode2HostInstall => 'Install and start OpenCode 2';

  @override
  String get openCode2HostInstallDetail =>
      'Requires Node.js and npm. Starts on this computer only and prints its password. Keep this terminal open.';

  @override
  String get schedulePhoneBusy =>
      'A scheduled prompt is using the phone server. Try again when it finishes.';

  @override
  String get schedulePhoneCheckFailed =>
      'The phone server could not be checked. Try again.';

  @override
  String get scheduleTitle => 'Mensajes programados';

  @override
  String get scheduleCreate => 'Programar un mensaje';

  @override
  String get scheduleEdit => 'Editar programación';

  @override
  String get schedulePrompt => 'Mensaje';

  @override
  String get scheduleTarget => 'Conversación';

  @override
  String get scheduleThisConversation => 'Esta conversación';

  @override
  String get scheduleNewConversation => 'Conversación nueva en cada ejecución';

  @override
  String get scheduleOnce => 'Una vez';

  @override
  String get scheduleRepeat => 'Cuándo';

  @override
  String get scheduleTime => 'Hora';

  @override
  String get scheduleDelete => 'Eliminar programación';

  @override
  String get schedulePaused => 'En pausa';

  @override
  String get scheduleFinished => 'Sin más ejecuciones';

  @override
  String get scheduleDigest => 'Resumen matutino';

  @override
  String get scheduleEmpty => 'Aún no hay mensajes programados';

  @override
  String get scheduleUnsupported =>
      'Los mensajes programados no están disponibles para esta conexión.';

  @override
  String get scheduleSaveFailed =>
      'No se pudo guardar este cambio. Inténtalo de nuevo.';

  @override
  String get scheduleNeedsYou =>
      'Abre la conversación para responder a una solicitud.';

  @override
  String get scheduleBusy => 'La conversación ya estaba trabajando.';

  @override
  String get scheduleUnavailable =>
      'No se pudo contactar con el servidor. Comprueba que esté en marcha y que este teléfono pueda llegar a él.';

  @override
  String get scheduleSignInAgain =>
      'El servidor necesita de nuevo su contraseña. Abre el servidor e inicia sesión antes de la próxima ejecución.';

  @override
  String get scheduleAllowHttp =>
      'Este servidor usa HTTP sin cifrar y aún no está permitido. Abre el servidor y permite HTTP antes de la próxima ejecución.';

  @override
  String get scheduleAgentFailed =>
      'El agente se detuvo con un error. Abre la conversación para ver qué pasó.';

  @override
  String get scheduleCertificateChanged =>
      'El certificado del servidor cambió. Abre el servidor para comprobarlo antes de la próxima ejecución.';

  @override
  String get scheduleCertificateInvalid =>
      'El certificado del servidor está caducado o aún no es válido. Abre el servidor para comprobarlo antes de la próxima ejecución.';

  @override
  String get scheduleTargetChanged =>
      'La conexión o el destino guardados cambiaron. Edita esta programación.';

  @override
  String get scheduleInterrupted =>
      'La ejecución se interrumpió. No se volvió a enviar.';

  @override
  String get scheduleTimeout =>
      'La ejecución alcanzó su límite de tiempo. Revisa la conversación.';

  @override
  String get scheduleUncertain =>
      'No se pudo confirmar la entrega. Revisa la conversación antes de enviar de nuevo.';

  @override
  String get scheduleMissed =>
      'Omitida según tu elección para ejecuciones perdidas.';

  @override
  String get scheduleDeleteBody =>
      'Se quitarán las ejecuciones futuras y el historial de esta programación. Los mensajes ya enviados permanecen en sus conversaciones.';

  @override
  String scheduleToday(String time) {
    return 'Hoy, $time';
  }

  @override
  String scheduleTomorrow(String time) {
    return 'Mañana, $time';
  }

  @override
  String scheduleInMinutes(String minutes) {
    return 'en $minutes min';
  }

  @override
  String scheduleDeleteNamedTitle(String name) {
    return '¿Eliminar “$name”?';
  }

  @override
  String get openCode2MoveProgress =>
      'Moving your conversations to OpenCode 2…';

  @override
  String openCode2MoveRetrying(int retry, int total) {
    return 'The phone is still getting ready. Trying again shortly (attempt $retry of $total).';
  }

  @override
  String get openCode2MoveFailedNotReady =>
      'The server on this phone was not ready. Your OpenCode 1 conversations and files are still on this phone, untouched. Try again in a minute.';

  @override
  String get openCode2MoveFailedSeparateHistory =>
      'OpenCode 2 on this phone already has its own conversations, so the move stopped to protect both. Nothing was changed.';

  @override
  String get openCode2MoveFailedStopped =>
      'The move stopped before it finished, often because the phone is low on free space or the server is busy. Your OpenCode 1 conversations and files are still on this phone, untouched.';

  @override
  String get openCode2MoveFailedNotConfirmed =>
      'OpenCode 2 did not confirm that your conversations arrived. Your OpenCode 1 conversations and files are still on this phone, untouched.';

  @override
  String get terminalLinkTitle => 'Link in the terminal';

  @override
  String get terminalLinkOpen => 'Open link';

  @override
  String get terminalLinkCopy => 'Copy link';

  @override
  String get terminalCopiedToClipboard => 'Copied to clipboard';

  @override
  String get chatRequestAskOutsideWrite => 'Write outside this project?';

  @override
  String get chatRequestAskOutsideRead => 'Read outside this project?';

  @override
  String get chatRequestAskOutsideRun => 'Run a command outside this project?';

  @override
  String get phoneSetupStageRetrying => 'Interrupted. Trying again';

  @override
  String get linuxMgrRow => 'Linux';

  @override
  String get linuxMgrRowDetail =>
      'What runs, ports, storage and packages of the Ubuntu inside this app';

  @override
  String get linuxMgrTitle => 'Linux on this phone';

  @override
  String get linuxMgrRunning => 'Running';

  @override
  String get linuxMgrRunningDetail => 'What runs inside Linux, and stopping it';

  @override
  String get linuxMgrPorts => 'Ports';

  @override
  String get linuxMgrPortsDetail => 'What is listening, and previewing it';

  @override
  String get linuxMgrStorage => 'Storage';

  @override
  String get linuxMgrStorageDetail =>
      'Where the space goes, and clearing caches';

  @override
  String get linuxMgrPackages => 'Packages';

  @override
  String get linuxMgrPackagesDetail =>
      'Installed software: install, remove and update';

  @override
  String get linuxMgrRepair => 'Repair Linux';

  @override
  String get linuxMgrRepairDetail =>
      'Checks Linux, OpenCode and your tools and fixes what is broken. Your projects are not touched.';

  @override
  String get linuxMgrRepairTitle => 'Repair Linux?';

  @override
  String get linuxMgrRepairBody =>
      'Checks Linux, OpenCode and the tools you added, and installs again anything that is missing or broken. It needs a connection and can take several minutes.';

  @override
  String get linuxMgrRepairKept => 'Your projects and sign-ins are not touched';

  @override
  String get linuxMgrReset => 'Reset Linux';

  @override
  String get linuxMgrResetDetail =>
      'Start again from a fresh Ubuntu. Your projects stay.';

  @override
  String get linuxMgrResetTitle => 'Reset Linux?';

  @override
  String get linuxMgrResetBody =>
      'Linux is erased and set up again from scratch. This cannot be undone.';

  @override
  String get linuxMgrResetLostTools =>
      'Installed packages and tools: OpenCode, Python, AI Team, voice typing';

  @override
  String get linuxMgrResetLostSignIns => 'Agent sign-ins and their settings';

  @override
  String get linuxMgrResetLostChats =>
      'Conversations kept by the server on this phone';

  @override
  String get linuxMgrResetKept => 'Your projects stay on the phone';

  @override
  String get linuxMgrResetWord => 'Reset';

  @override
  String get linuxMgrProblemBusy =>
      'Another task is using Linux right now. Wait a moment and try again.';

  @override
  String get linuxMgrProblemSetup =>
      'Setup is running. Wait until it finishes.';

  @override
  String get linuxMgrProblemJob =>
      'A package task is running. Wait until it finishes.';

  @override
  String get linuxMgrProblemUnavailable =>
      'Linux is not set up or is not answering. Open This phone to set it up.';

  @override
  String get linuxMgrProblemFailed => 'That did not finish. Try again.';

  @override
  String get linuxMgrLoadFailedTitle => 'Could not read Linux';

  @override
  String get linuxMgrProcTitle => 'Running in Linux';

  @override
  String linuxMgrProcSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count programs',
      one: '1 program',
    );
    return '$_temp0 · $memory';
  }

  @override
  String get linuxMgrKindServer => 'OpenCode server';

  @override
  String get linuxMgrKindPaseo => 'Paseo';

  @override
  String get linuxMgrKindAgent => 'Agent';

  @override
  String get linuxMgrKindDev => 'Dev server';

  @override
  String get linuxMgrKindOther => 'Other';

  @override
  String get linuxMgrAboutServer =>
      'The OpenCode server this app talks to. Stop it from This phone, not from here.';

  @override
  String get linuxMgrAboutPaseo =>
      'Paseo runs coding agents on this phone. Stopping it ends the agents it started.';

  @override
  String get linuxMgrAboutAgent =>
      'A coding agent or part of AI Team. Stopping it ends its work in progress.';

  @override
  String get linuxMgrAboutDev =>
      'A program that serves your project while you build it. Start it again from the project when you need it.';

  @override
  String get linuxMgrAboutOther =>
      'A program running inside Linux. Stopping it ends it; nothing restarts it.';

  @override
  String get linuxMgrProtected => 'Managed from This phone';

  @override
  String linuxMgrStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get linuxMgrStopBody =>
      'It ends now and nothing restarts it. Unsaved work in it is lost.';

  @override
  String linuxMgrStopHelpers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count programs it started stop with it',
      one: '1 program it started stops with it',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopLabel(String name) {
    return 'Stop $name';
  }

  @override
  String get linuxMgrStopping => 'Stopping…';

  @override
  String linuxMgrStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stopped $count programs',
      one: 'Stopped 1 program',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopForced(int count) {
    return '$count had to be forced';
  }

  @override
  String linuxMgrStopRemaining(int count) {
    return '$count would not stop';
  }

  @override
  String linuxMgrStopChanged(int count) {
    return '$count had already changed and was left alone';
  }

  @override
  String get linuxMgrNotStoppedTitle => 'Not everything stopped';

  @override
  String get linuxMgrProcEmpty => 'Nothing is running in Linux';

  @override
  String get linuxMgrProcEmptyBody =>
      'OpenCode and your tools start when you use them.';

  @override
  String get linuxMgrCopyCommand => 'Copy command';

  @override
  String get linuxMgrPortsTitle => 'Ports in Linux';

  @override
  String linuxMgrPortRow(int port) {
    return 'Port $port';
  }

  @override
  String linuxMgrPortUsedBy(String name) {
    return 'Used by $name';
  }

  @override
  String get linuxMgrPortUnknown => 'Program not known';

  @override
  String get linuxMgrPortLocal => 'Only on this phone';

  @override
  String get linuxMgrPortAll => 'Open to other devices on your network';

  @override
  String get linuxMgrPortNotPage => 'Not a web page';

  @override
  String get linuxMgrPortPreview => 'Open preview';

  @override
  String linuxMgrPortPreviewNamed(int port) {
    return 'Open preview of port $port';
  }

  @override
  String get linuxMgrPortCopy => 'Copy address';

  @override
  String get linuxMgrPortsNote => 'Only ports this phone can open are listed.';

  @override
  String get linuxMgrPortsEmpty => 'Nothing is listening';

  @override
  String get linuxMgrPortsEmptyBody =>
      'When a dev server starts, its port shows up here.';

  @override
  String get linuxMgrStorageTitle => 'Linux storage';

  @override
  String linuxMgrStorageTotal(String size) {
    return '$size used inside Linux';
  }

  @override
  String get linuxMgrCatBase => 'Linux itself';

  @override
  String get linuxMgrCatBaseDetail => 'Ubuntu and its system files';

  @override
  String get linuxMgrCatPackages => 'Package downloads';

  @override
  String get linuxMgrCatPackagesDetail =>
      'Downloaded packages and package lists';

  @override
  String get linuxMgrCatTools => 'Node and caches';

  @override
  String get linuxMgrCatToolsDetail => 'Node, npm and setup downloads';

  @override
  String get linuxMgrCatAgents => 'Agents and OpenCode';

  @override
  String get linuxMgrCatAgentsDetail =>
      'OpenCode, Paseo, Claude Code, AI Team and their saved settings';

  @override
  String get linuxMgrCatProjects => 'Projects';

  @override
  String get linuxMgrCatProjectsDetail => 'Your work. Never cleared from here.';

  @override
  String get linuxMgrCatTemp => 'Temporary files and logs';

  @override
  String get linuxMgrCatTempDetail => 'Scratch files and program logs';

  @override
  String get linuxMgrFreeUp => 'Free up space';

  @override
  String get linuxMgrCleanApt => 'Clear package downloads';

  @override
  String get linuxMgrCleanAptDetail =>
      'Linux downloads them again if it needs them';

  @override
  String get linuxMgrCleanTools => 'Clear Node and download caches';

  @override
  String get linuxMgrCleanToolsDetail =>
      'npm and setup downloads, fetched again when needed';

  @override
  String get linuxMgrCleanTemp => 'Clear old temporary files and logs';

  @override
  String get linuxMgrCleanTempDetail =>
      'Files untouched for a day and logs older than a week';

  @override
  String get linuxMgrCleanAgents => 'Remove old agent versions';

  @override
  String get linuxMgrCleanAgentsDetail =>
      'Paseo versions that are no longer in use';

  @override
  String linuxMgrCleanBody(String size) {
    return 'About $size comes back. This cannot be undone, and nothing you made is touched.';
  }

  @override
  String get linuxMgrCleanKept => 'Projects, sign-ins and settings stay';

  @override
  String linuxMgrCleanDone(String size) {
    return 'Freed $size';
  }

  @override
  String get linuxMgrCleanNothing => 'There was nothing to clear';

  @override
  String get linuxMgrCleanNone => 'Nothing to clear right now';

  @override
  String get linuxMgrMeasuring => 'Measuring…';

  @override
  String get linuxMgrPkgTitle => 'Packages';

  @override
  String linuxMgrPkgCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count packages installed',
      one: '1 package installed',
    );
    return '$_temp0';
  }

  @override
  String get linuxMgrPkgSearch => 'Search installed packages';

  @override
  String get linuxMgrPkgNoMatch => 'No installed package matches that';

  @override
  String linuxMgrPkgShowAll(int count) {
    return 'Show all $count packages';
  }

  @override
  String get linuxMgrPkgUpdate => 'Update system';

  @override
  String get linuxMgrPkgUpdateDetail =>
      'Refreshes the package lists and installs available updates';

  @override
  String get linuxMgrPkgUpdateTitle => 'Update Linux packages?';

  @override
  String get linuxMgrPkgUpdateBody =>
      'Downloads the latest updates and installs them. It uses your connection, can take several minutes, and stops by itself after 45 minutes.';

  @override
  String get linuxMgrPkgInstall => 'Install a package';

  @override
  String get linuxMgrPkgInstallDetail => 'By name, for example htop';

  @override
  String get linuxMgrPkgName => 'Package name';

  @override
  String get linuxMgrPkgNameHelper =>
      'Ubuntu package names, separated by spaces';

  @override
  String get linuxMgrPkgNameInvalid =>
      'Use only lower case letters, digits and + - . in each name';

  @override
  String get linuxMgrPkgCheck => 'Check';

  @override
  String linuxMgrPkgAlready(String name) {
    return '$name is already installed';
  }

  @override
  String linuxMgrPkgUnknown(String name) {
    return 'No package named $name. Update system refreshes the list of packages.';
  }

  @override
  String linuxMgrPkgInstallTitle(String names) {
    return 'Install $names?';
  }

  @override
  String get linuxMgrPkgInstallBody =>
      'Downloads from Ubuntu and installs. It uses your connection.';

  @override
  String linuxMgrPkgInstallLabel(String names) {
    return 'Install $names';
  }

  @override
  String linuxMgrPkgRemove(String name) {
    return 'Remove $name';
  }

  @override
  String linuxMgrPkgRemoveTitle(String name) {
    return 'Remove $name?';
  }

  @override
  String get linuxMgrPkgRemoveBody =>
      'It is removed from Linux. Your projects are not touched.';

  @override
  String linuxMgrPkgAlsoRemoved(String list) {
    return 'Also removed: $list';
  }

  @override
  String get linuxMgrPkgLocked =>
      'Linux or your tools need it. Tools are removed from This phone.';

  @override
  String linuxMgrPkgBlockedPlan(String list) {
    return 'Removing it would also remove $list, which Linux needs.';
  }

  @override
  String get linuxMgrPkgChecking => 'Checking what it would remove…';

  @override
  String linuxMgrJobInstalling(String names) {
    return 'Installing $names';
  }

  @override
  String linuxMgrJobRemoving(String names) {
    return 'Removing $names';
  }

  @override
  String get linuxMgrJobUpdating => 'Updating Linux packages';

  @override
  String get linuxMgrJobKeepsGoing => 'It keeps going if you leave this page.';

  @override
  String linuxMgrJobStopInstall(String names) {
    return 'Stop installing $names';
  }

  @override
  String linuxMgrJobStopRemove(String names) {
    return 'Stop removing $names';
  }

  @override
  String get linuxMgrJobStopUpdate => 'Stop updating';

  @override
  String get linuxMgrJobStopBody =>
      'It stops now. A half-finished package is repaired the next time you run a package task.';

  @override
  String linuxMgrJobDoneInstall(String names) {
    return 'Installed $names';
  }

  @override
  String linuxMgrJobDoneRemove(String names) {
    return 'Removed $names';
  }

  @override
  String get linuxMgrJobDoneUpdate => 'Linux packages are up to date';

  @override
  String get linuxMgrJobFailed =>
      'That did not finish. Nothing you made was touched. Try again; it carries on where it stopped.';

  @override
  String get linuxMgrJobTimedOut =>
      'It took longer than 45 minutes and stopped itself. Run it again to continue.';

  @override
  String get linuxMgrJobInterrupted =>
      'It stopped before it finished, for example when the app was closed. Run it again to continue.';

  @override
  String get linuxMgrJobNoticeInstall => 'Installing Linux packages';

  @override
  String get linuxMgrJobNoticeRemove => 'Removing Linux packages';

  @override
  String get linuxMgrJobNoticeUpdate => 'Updating Linux packages';

  @override
  String get linuxMgrJobDismiss => 'Done';

  @override
  String linuxMgrJobStopInstallTitle(String names) {
    return 'Stop installing $names?';
  }

  @override
  String linuxMgrJobStopRemoveTitle(String names) {
    return 'Stop removing $names?';
  }

  @override
  String get linuxMgrJobStopUpdateTitle => 'Stop updating Linux packages?';

  @override
  String get linuxMgrCleanAptAsk => 'Clear package downloads?';

  @override
  String get linuxMgrCleanToolsAsk => 'Clear Node and download caches?';

  @override
  String get linuxMgrCleanTempAsk => 'Clear old temporary files and logs?';

  @override
  String get linuxMgrCleanAgentsAsk => 'Remove old agent versions?';

  @override
  String get kitLogSearch => 'Search this output';

  @override
  String get kitLogPause => 'Pause following';

  @override
  String get kitLogFollow => 'Follow new output';

  @override
  String get kitLogPreviousMatch => 'Previous match';

  @override
  String get kitLogNextMatch => 'Next match';

  @override
  String get servicesAlsoRunning => 'Also running';

  @override
  String get servicesAlsoPhoneHint =>
      'Found by the ports they listen on, however they were started.';

  @override
  String get servicesAlsoServerHint =>
      'This server lists the commands it is running. A server started in a terminal on it is not found here.';

  @override
  String servicesPorts(int count, String ports) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Ports $ports',
      one: 'Port $ports',
    );
    return '$_temp0';
  }

  @override
  String get servicesNoPortYet => 'No port found yet';

  @override
  String get servicesOpenPreview => 'Open preview';

  @override
  String servicesOpenPreviewNamed(String name) {
    return 'Preview $name';
  }

  @override
  String servicesPreviewPort(String port) {
    return 'Preview port $port';
  }

  @override
  String get servicesSaveCommand => 'Save as command';

  @override
  String servicesStopFoundTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get servicesStopFoundHint =>
      'This ends the program on the machine it runs on. Anything it has not saved is lost.';

  @override
  String get servicesLogsUnavailable =>
      'This program was not started from this app, so its output is not kept where the app can read it. Choose Save as command on its row, stop this one, then start the saved command here to see its logs.';

  @override
  String devPreviewRunningNow(String name) {
    return 'Running now: $name';
  }

  @override
  String get devPreviewNoneFound => 'No running apps found';

  @override
  String get linuxMgrResetNotErasedTitle => 'Linux was not reset';

  @override
  String get linuxMgrResetNotErasedBody =>
      'Nothing was erased. Close the terminals and stop what runs in Linux, then try again.';

  @override
  String get linuxMgrResetDetailsLabel => 'Error code';

  @override
  String get linuxMgrResetCleanupTitle => 'Setting Linux up again';

  @override
  String linuxMgrResetForgetFailed(String name) {
    return '“$name” from before the reset could not be forgotten and is still saved.';
  }

  @override
  String linuxMgrResetLeft(String name, String what) {
    return 'From before the reset, “$name” still has saved: $what.';
  }

  @override
  String get linuxMgrResetLeftConnection => 'its saved connection';

  @override
  String get servicesAlsoPhoneFailed =>
      'Could not check what else runs in Linux right now. It is checked again in a few seconds.';

  @override
  String linuxMgrProcGroupCount(int count) {
    return '$count processes';
  }

  @override
  String get gitRepairFailureWords => 'Git history can\'t be read.';

  @override
  String get gitRepairAction => 'Repair';

  @override
  String get gitRepairTitle => 'Repair Git history';

  @override
  String gitRepairWorking(String project) {
    return 'Repairing $project…';
  }

  @override
  String get gitRepairLooking => 'Looking for missing Git files…';

  @override
  String gitRepairRestoring(int done, int total) {
    return 'Putting back $done of $total Git files…';
  }

  @override
  String gitRepairAskingTermux(int done, int total) {
    return 'Copying $done of $total Git files from Termux…';
  }

  @override
  String get gitRepairChecking => 'Checking the project with Git…';

  @override
  String gitRepairDoneTitle(String project) {
    return '$project is repaired';
  }

  @override
  String gitRepairDoneBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count Git files are back, and Git can read the project again.',
      one: '1 Git file is back, and Git can read the project again.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairHealthyTitle => 'Nothing was missing';

  @override
  String gitRepairHealthyBody(String project) {
    return 'Git can read $project now.';
  }

  @override
  String get gitRepairStart => 'Start the conversation';

  @override
  String get gitRepairTermuxNotAllowedTitle => 'Files are in Termux';

  @override
  String get gitRepairTermuxNotAllowedBody =>
      'Allow the app to run commands in Termux, and it copies them back.';

  @override
  String get gitRepairAllow => 'Allow and repair';

  @override
  String get gitRepairTermuxNoAnswerTitle => 'Termux didn\'t answer';

  @override
  String get gitRepairTermuxNoAnswerBody =>
      'Open Termux once so it starts, come back here, and repair again.';

  @override
  String get gitRepairAgain => 'Repair again';

  @override
  String get gitRepairOpenTermux => 'Open Termux';

  @override
  String gitRepairCannotTitle(String project) {
    return 'Can\'t repair $project';
  }

  @override
  String gitRepairMissingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count Git files aren\'t on this phone any more. Clone the project again from where it\'s hosted, or restore a copy.',
      one:
          '1 Git file isn\'t on this phone any more. Clone the project again from where it\'s hosted, or restore a copy.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairStillBrokenBody =>
      'Git still can\'t read it. Clone the project again from where it\'s hosted, or restore a copy.';

  @override
  String get gitRepairNotRepositoryTitle => 'Can\'t repair this folder';

  @override
  String get gitRepairNotRepositoryBody =>
      'Only Git projects in On this phone\'s projects folder can be repaired here.';

  @override
  String get gitRepairClose => 'Close';

  @override
  String get gitProblemNotARepository => 'This folder isn’t a Git repository.';

  @override
  String get gitProblemGitMissing =>
      'Git isn’t installed where this project lives. Install it there, then try again.';

  @override
  String get gitProblemFolderMissing =>
      'The project folder wasn’t found. It may have been moved or deleted.';

  @override
  String get gitProblemEnvironmentUnavailable =>
      'Couldn’t reach the place this project lives. Check that it’s running, then try again.';

  @override
  String get gitProblemServerCannotRunCommands =>
      'This server can’t run Git commands for the app.';

  @override
  String get gitProblemOutputTooLarge =>
      'There are too many changes to read here. Commit or clean up some files, then try again.';

  @override
  String get gitProblemTimedOut => 'Git took too long to answer. Try again.';

  @override
  String get gitProblemUnsafeRepository =>
      'Git won’t work in this folder because another user owns it. Fix the folder’s owner on that device.';

  @override
  String get gitProblemRepositoryLocked =>
      'Another Git command is using this repository. Wait for it to finish, then try again.';

  @override
  String get gitProblemNoUpstream =>
      'This branch isn’t linked to a branch on the remote yet. Push it first.';

  @override
  String get gitProblemNoRemote =>
      'This repository has no remote to push to. Add one on that device first.';

  @override
  String get gitProblemDetachedHead =>
      'You’re not on a branch. Switch to a branch first.';

  @override
  String get gitProblemCannotFastForward =>
      'The remote and this branch both have new commits, so they can’t be combined automatically. Merge or rebase on that device.';

  @override
  String get gitProblemPushRejected =>
      'The remote has commits this branch doesn’t have. Pull first, then push again.';

  @override
  String get gitProblemLocalChangesBlock =>
      'Your uncommitted changes would be overwritten. Commit or discard them first.';

  @override
  String get gitProblemAuthRequired =>
      'The remote needs you to sign in. Set up Git sign-in where this project lives, then try again.';

  @override
  String get gitProblemAuthRequiredSignIn =>
      'The remote needs you to sign in. Sign in to GitHub, then try again.';

  @override
  String get gitProblemGitHubTokenRejected =>
      'GitHub didn’t accept your sign-in. Sign in to GitHub again.';

  @override
  String get gitProblemNoPermission =>
      'Your account can’t push to this repository. Ask its owner for access.';

  @override
  String get gitProblemNetwork =>
      'Couldn’t reach the remote. Check the internet connection, then try again.';

  @override
  String get gitProblemRemoteNotFound =>
      'The remote repository wasn’t found, or your account can’t see it.';

  @override
  String get gitProblemNothingSelected => 'Tick at least one file.';

  @override
  String get gitProblemEmptyMessage => 'Describe what changed first.';

  @override
  String get gitProblemNothingToCommit =>
      'There’s nothing to commit in the files you chose.';

  @override
  String get gitProblemIdentityMissing =>
      'Git needs your name and email to make a commit. Add them, then try again.';

  @override
  String get gitProblemSigningFailed =>
      'Git couldn’t sign the commit. Check commit signing on that device.';

  @override
  String get gitProblemHookRejected =>
      'A check in this repository stopped the action. Details shows what it said.';

  @override
  String get gitProblemInvalidBranchName =>
      'That isn’t a valid branch name. Avoid spaces and characters like ~ ^ : ? * [';

  @override
  String get gitProblemBranchExists =>
      'A branch with that name already exists.';

  @override
  String get gitProblemBranchNotFound => 'That branch wasn’t found.';

  @override
  String get gitProblemConflicts =>
      'Some files have conflicts. Resolve them on that device first.';

  @override
  String get gitProblemOperationInProgress =>
      'A merge or rebase isn’t finished. Finish or cancel it on that device first.';

  @override
  String get gitProblemCannotDiscardConflict =>
      'Files with conflicts can’t be discarded here.';

  @override
  String get gitProblemUnknown =>
      'Git couldn’t finish. Details shows what it said.';

  @override
  String get gitUnavailableNotConnected =>
      'Connect to a server to see its Git changes.';

  @override
  String get gitUnavailableNoProject =>
      'Open a project to see its Git changes.';

  @override
  String get gitUnavailableServerCannotRun =>
      'This server can’t run Git for the app. Use Git on that computer instead.';

  @override
  String get gitNotRepositoryTitle => 'Not a Git repository';

  @override
  String get gitNotRepositoryBody => 'This project folder doesn’t use Git yet.';

  @override
  String get gitCleanBody => 'Everything is committed.';

  @override
  String get gitDetachedHead => 'Not on a branch';

  @override
  String get gitNoCommitsYet => 'No commits yet';

  @override
  String gitAhead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count commits to push',
      one: '1 commit to push',
    );
    return '$_temp0';
  }

  @override
  String get gitOperationMerge => 'Merge not finished';

  @override
  String get gitOperationRebase => 'Rebase not finished';

  @override
  String get gitOperationCherryPick => 'Cherry-pick not finished';

  @override
  String get gitOperationRevert => 'Revert not finished';

  @override
  String get gitUsesOwnSignIn =>
      'Sending and getting latest use the Git sign-in already set up where this project lives.';

  @override
  String get gitChangeAdded => 'Added';

  @override
  String get gitChangeModified => 'Modified';

  @override
  String get gitChangeDeleted => 'Deleted';

  @override
  String get gitChangeRenamed => 'Renamed';

  @override
  String get gitChangeCopied => 'Copied';

  @override
  String get gitChangeTypeChanged => 'Type changed';

  @override
  String get gitChangeUntracked => 'New';

  @override
  String get gitChangeConflicted => 'Conflict';

  @override
  String get gitActionPull => 'Pull';

  @override
  String get gitActionPush => 'Push';

  @override
  String get gitActionCommit => 'Commit';

  @override
  String get gitActionSwitchBranch => 'Switch branch';

  @override
  String get gitActionCreateBranch => 'New branch';

  @override
  String get gitActionDiscard => 'Discard changes';

  @override
  String get gitActionRefresh => 'Refresh';

  @override
  String get gitActionSaveIdentity => 'Save name and email';

  @override
  String get gitBusyPull => 'Getting the latest…';

  @override
  String get gitBusyPush => 'Sending…';

  @override
  String get gitBusyCommit => 'Saving…';

  @override
  String get gitBusySwitchBranch => 'Switching branch…';

  @override
  String get gitBusyCreateBranch => 'Creating the branch…';

  @override
  String get gitBusyDiscard => 'Discarding changes…';

  @override
  String get gitBusyRefresh => 'Checking for changes…';

  @override
  String get gitBusySaveIdentity => 'Saving…';

  @override
  String get gitDoneUpToDate => 'Already up to date.';

  @override
  String get gitDonePulled => 'Got the latest changes.';

  @override
  String get gitDonePushed => 'Sent.';

  @override
  String get gitDoneSwitched => 'Switched branch.';

  @override
  String get gitDoneCreated => 'Created the branch and switched to it.';

  @override
  String get gitDoneDiscarded => 'Changes discarded.';

  @override
  String get gitDoneIdentitySaved =>
      'Saved your name and email for this repository.';

  @override
  String get gitCommitMessageHint => 'Describe what changed';

  @override
  String get gitWriteItForMe => 'Write it for me';

  @override
  String get gitAskAgentFixConflicts => 'Ask the agent to fix conflicts';

  @override
  String gitConflictCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files have conflicts',
      one: '1 file has conflicts',
    );
    return '$_temp0';
  }

  @override
  String gitAskAgentFixConflictsPrompt(String files) {
    return 'Please resolve the merge conflicts in $files. Keep what both sides meant, then tell me what you chose.';
  }

  @override
  String get gitBranchNameHint => 'Branch name';

  @override
  String get gitIdentityName => 'Name';

  @override
  String get gitIdentityEmail => 'Email';

  @override
  String get gitHubSignIn => 'Sign in to GitHub';

  @override
  String get gitHubSignOut => 'Sign out of GitHub';

  @override
  String get gitHubSignOutTitle => 'Sign out of GitHub?';

  @override
  String gitHubSignedInAs(String login) {
    return 'Signed in to GitHub as $login';
  }

  @override
  String get gitHubRequestingCode => 'Getting a sign-in code…';

  @override
  String get gitHubEnterCode =>
      'Enter this code on GitHub to finish signing in.';

  @override
  String get gitHubOpenGitHub => 'Open GitHub';

  @override
  String get gitHubCopyCode => 'Copy code';

  @override
  String get gitHubWaiting => 'Waiting for you to finish on GitHub…';

  @override
  String get gitHubFinishing => 'Finishing sign-in…';

  @override
  String get gitHubCancel => 'Cancel';

  @override
  String get gitHubSignOutBody =>
      'The app forgets your GitHub sign-in on this device. To remove its access completely, revoke it on GitHub too.';

  @override
  String get gitHubManageAccess => 'Manage access on GitHub';

  @override
  String get gitHubPushOverHttps =>
      'Pushes to GitHub use this sign-in over HTTPS. Your SSH settings don’t change.';

  @override
  String get gitHubProblemExpired =>
      'The code expired before it was entered. Sign in again to get a new one.';

  @override
  String get gitHubProblemDenied => 'Sign-in was cancelled on GitHub.';

  @override
  String get gitHubProblemDisabled =>
      'GitHub sign-in isn’t switched on for this app yet.';

  @override
  String get gitHubProblemNetwork =>
      'Couldn’t reach GitHub. Check the internet connection, then try again.';

  @override
  String get gitHubProblemTokenRejected =>
      'Your GitHub sign-in ended. Sign in again.';

  @override
  String get gitHubProblemUnexpected =>
      'GitHub sent an answer the app didn’t understand. Try again.';

  @override
  String get gitProblemInvalidRemoteName =>
      'That isn’t a valid remote name. Use letters, numbers, dots, dashes or underscores.';

  @override
  String get gitProblemInvalidRemoteUrl =>
      'That address doesn’t look right. Use one like https://github.com/owner/name.';

  @override
  String get gitProblemRemoteAddressHasPassword =>
      'Take the password or token out of the address. Sign in to GitHub instead, so no secret is saved in the repository.';

  @override
  String get gitProblemRemoteExists =>
      'A remote with that name is already set up. Choose another name.';

  @override
  String get gitProblemInvalidFolderName =>
      'That isn’t a usable folder name. Use letters, numbers, dots, dashes or underscores.';

  @override
  String get gitProblemFolderExists =>
      'A folder with that name is already there. Choose another name.';

  @override
  String get gitActionAddRemote => 'Add remote';

  @override
  String get gitActionClone => 'Clone';

  @override
  String get gitBusyAddRemote => 'Adding the remote…';

  @override
  String get gitBusyClone => 'Cloning…';

  @override
  String get gitDoneRemoteAdded => 'Remote added.';

  @override
  String gitDoneCloned(String path) {
    return 'Cloned into $path. Open it from your projects.';
  }

  @override
  String get gitSheetTitle => 'Git';

  @override
  String gitSheetChipSemantics(String branch) {
    return 'Git, branch $branch. Opens the Git actions.';
  }

  @override
  String get gitSheetChipSemanticsPlain => 'Git. Opens the Git actions.';

  @override
  String get gitGetLatest => 'Get latest';

  @override
  String get gitSend => 'Send';

  @override
  String get gitSaveChanges => 'Save changes';

  @override
  String gitSaveChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count changed files',
      one: '1 changed file',
      zero: 'No changed files',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveFilesLabel => 'Files to include';

  @override
  String gitSaveButton(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Save $count files',
      one: 'Save 1 file',
    );
    return '$_temp0';
  }

  @override
  String gitSaveOnBranch(String branch) {
    return 'On $branch';
  }

  @override
  String get gitSwitchRow => 'Switch branch';

  @override
  String gitSwitchRowOn(String branch) {
    return 'On $branch';
  }

  @override
  String gitBranchRemoteOnly(String remote) {
    return 'On $remote, not on this device yet';
  }

  @override
  String get gitBranchCurrent => 'Current branch';

  @override
  String gitNewBranchFrom(String branch) {
    return 'New branch from $branch';
  }

  @override
  String get gitCloneRow => 'Clone a repository';

  @override
  String get gitCloneRowHint => 'Copy a repository next to this project';

  @override
  String get gitCloneAddressLabel => 'Repository address';

  @override
  String get gitCloneFolderLabel => 'Folder name';

  @override
  String gitCloneInto(String path) {
    return 'Clones into $path';
  }

  @override
  String get gitCloneButton => 'Clone';

  @override
  String get gitRemotesRow => 'Remotes';

  @override
  String get gitRemotesRowNone => 'None yet';

  @override
  String get gitRemotesTitle => 'Remotes';

  @override
  String get gitRemotesEmpty =>
      'This project has no remote yet. Add one to send your work somewhere.';

  @override
  String get gitRemoteAddTitle => 'Add a remote';

  @override
  String get gitRemoteNameLabel => 'Name';

  @override
  String get gitRemoteAddressLabel => 'Address';

  @override
  String get gitIdentityTitle => 'Your name on saves';

  @override
  String get gitHubSignedOutHint =>
      'Sign in once with a code. There is no token to paste.';

  @override
  String get gitHubSignInTitle => 'GitHub sign-in';

  @override
  String get gitHubSignInWhy =>
      'Sending to GitHub needs your sign-in. Get a code here, enter it on GitHub, and the app stores the sign-in on this device only.';

  @override
  String get gitHubSignOutConfirm => 'Sign out';

  @override
  String gitUsesOwnSignInOn(String host) {
    return 'Sending and getting latest use the Git sign-in set up on $host.';
  }

  @override
  String gitDoneSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count files',
      one: '1 file',
    );
    return 'Saved $_temp0.';
  }

  @override
  String get gitDoneSavedPlain => 'Saved.';

  @override
  String get gitChecking => 'Checking…';

  @override
  String get gitCheckFailed => 'Couldn’t check, tap to try';

  @override
  String gitNewCount(int count) {
    return '$count new';
  }

  @override
  String get gitIdentityWhy =>
      'Saves in this project carry this name and email, so your work is yours when you send it.';

  @override
  String get kitStepSliderLess => 'A';

  @override
  String get kitStepSliderMore => 'A';

  @override
  String get appearanceIntensityHelp =>
      'Your conversation stays on top and easy to read.';

  @override
  String get appearanceTextHelp =>
      'Applies to the whole app, on top of your phone\'s text size.';

  @override
  String kitCarouselPlace(int page, int count) {
    return '$page of $count';
  }

  @override
  String get gitPullNotOnRemote => 'Not on the remote yet. Send it first.';

  @override
  String get gitPullNoRemote => 'Add a remote below first.';

  @override
  String get gitSendNoRemote => 'No remote yet. Add one below.';

  @override
  String get chatSteerHint => 'Steer while it works…';

  @override
  String chatSteerReadsAfterCommand(String agent) {
    return '$agent reads this when the command finishes';
  }

  @override
  String chatSteerReadsAfterStep(String agent) {
    return '$agent reads this when this step finishes';
  }

  @override
  String get chatSteerAgentFallback => 'The agent';

  @override
  String get chatSteerStopCommand => 'Stop the command';

  @override
  String get chatSteerStopStep => 'Stop this step';

  @override
  String get chatSendLaterOrRepeat => 'Send later or repeat';

  @override
  String get chatSendLaterSaved => 'Scheduled. It is in Scheduled prompts.';

  @override
  String get composerSettingModel => 'Model';

  @override
  String get composerSettingPlan => 'Plan';

  @override
  String get composerSettingApprovalDetail =>
      'Allow what it asks for without asking you';

  @override
  String get chatAutoApproveOn => 'Auto-approve is on';

  @override
  String get chatAutoApproveTurnOff => 'Turn off';

  @override
  String get chatNoModelSignedIn => 'No model signed in';

  @override
  String get chatNoModelSignIn => 'Sign in';

  @override
  String gitSendTo(String place, String branch) {
    return 'Send to $place · $branch';
  }

  @override
  String get gitPlaceGitHub => 'GitHub';

  @override
  String get gitStateUpToDate => 'Up to date';

  @override
  String get gitStateNothingToSend => 'Nothing to send';

  @override
  String get gitStateFirstSend => 'First time, links this branch';

  @override
  String get gitDetailsBranch => 'Branch';

  @override
  String get gitDetailsGetsFrom => 'Get latest from';

  @override
  String get gitDetailsSendsTo => 'Send to';

  @override
  String get gitDetailsAddress => 'Remote address';

  @override
  String gitDoneGotN(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count new changes',
      one: '1 new change',
    );
    return 'Got $_temp0.';
  }

  @override
  String reviewSummary(int files, int added, int removed) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files files changed',
      one: '1 file changed',
    );
    return '$_temp0 · +$added −$removed';
  }

  @override
  String get reviewAlreadyApplied =>
      'The agent already made these changes in your project.';

  @override
  String get reviewLooksGood => 'Looks good';

  @override
  String get reviewUndoAll => 'Undo all';

  @override
  String get reviewSendBackWithNote => 'Send back with a note';

  @override
  String reviewUndoTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Undo $count changed files?',
      one: 'Undo 1 changed file?',
    );
    return '$_temp0';
  }

  @override
  String get reviewUndoBody =>
      'Your project goes back to how it was before the agent started. The conversation stays.';

  @override
  String get chatsNewBuildPrompt => 'What would you like to build?';

  @override
  String get chatsNewProjectRow => 'Project';

  @override
  String get chatsNewModelRow => 'Model';

  @override
  String get chatsNewSeparateCopyDetail =>
      'Works on its own copy, so your files stay as they are.';

  @override
  String get modelPickerSheetTitle => 'Model';

  @override
  String modelPickerAllModels(int count) {
    return 'All models ($count)';
  }

  @override
  String get modelPickerReasonDefault => 'Default';

  @override
  String get modelPickerReasonRecent => 'Recently used';

  @override
  String get modelPickerReasonCheapest => 'Cheapest';

  @override
  String get modelPickerFree => 'Free';

  @override
  String get modelPickerScopeNewSessions => 'For new conversations';

  @override
  String get modelPickerScopeSession => 'For this conversation';

  @override
  String get searchRecentTitle => 'Recent';

  @override
  String chatsHomeUnreachableShort(String servers) {
    return '$servers isn\'t answering';
  }

  @override
  String chatProjectChipFilesSemantics(String name) {
    return 'Project $name. Opens its files and tools.';
  }

  @override
  String chatProjectServerChipFilesSemantics(String name, String server) {
    return 'Project $name on $server. Opens its files and tools.';
  }

  @override
  String get chatTurnAllFiles => 'All files';

  @override
  String chatTurnAllFilesMore(int count) {
    return 'All files · $count more changed';
  }

  @override
  String chatTurnFileLinesAdded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines added',
      one: '1 line added',
    );
    return '$_temp0';
  }

  @override
  String chatTurnFileLinesRemoved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count lines removed',
      one: '1 line removed',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubSearch => 'Search settings';

  @override
  String get settingsHubServer => 'Server';

  @override
  String get settingsHubHelp => 'Help';

  @override
  String get settingsHubServerOnPhone => 'On this phone';

  @override
  String settingsHubServerLine(String where, String state) {
    return '$where · $state';
  }

  @override
  String get settingsHubServerNone => 'Not set up yet';

  @override
  String gitSaveAndSendTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Save $count files and send to $place · $branch',
      one: 'Save 1 file and send to $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSendSavesTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Send $count saves to $place · $branch',
      one: 'Send 1 save to $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveAndSendTitle => 'Save and send';

  @override
  String get gitSaveOnly => 'Save without sending';

  @override
  String gitMessageAddOne(String file) {
    return 'Add $file';
  }

  @override
  String gitMessageUpdateOne(String file) {
    return 'Update $file';
  }

  @override
  String gitMessageUpdateTwo(String first, String second) {
    return 'Update $first and $second';
  }

  @override
  String gitMessageUpdateMany(String first, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Update $first and $count more files',
      one: 'Update $first and 1 more file',
    );
    return '$_temp0';
  }

  @override
  String gitDoneSavedAndSent(String place, String branch) {
    return 'Saved and sent to $place · $branch.';
  }

  @override
  String gitSavedNotSent(String reason) {
    return 'Saved here, not sent yet. $reason';
  }

  @override
  String get gitMoreLabel => 'Remotes, clone and details';

  @override
  String gitBusySendingTo(String place, String branch) {
    return 'Sending to $place · $branch…';
  }

  @override
  String gitGetNewFrom(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Get $count new changes from $place · $branch',
      one: 'Get 1 new change from $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSavedSignInFirst(String place) {
    return 'Saved here, not sent yet. $place needs you to sign in first.';
  }

  @override
  String get thisPhoneAnswerRunning => 'Everything is running';

  @override
  String thisPhoneAnswerMemory(String size) {
    return 'Uses about $size of memory';
  }

  @override
  String thisPhoneAnswerStorage(String size) {
    return 'Takes $size of storage';
  }

  @override
  String get thisPhoneAnswerConnect => 'Use this phone\'s server';

  @override
  String get thisPhoneAnswerHeatPaused => 'Paused, your phone is warm';

  @override
  String get thisPhoneAnswerHeatStopped => 'Stopped, your phone is hot';

  @override
  String thisPhoneAnswerHeatResumeIn(String time) {
    return 'The AI Team resumes by itself in $time';
  }

  @override
  String get thisPhoneAnswerHeatResumeCool =>
      'The AI Team resumes by itself once your phone cools';

  @override
  String get thisPhoneAnswerHeatStays =>
      'The AI Team stays paused until you resume it';

  @override
  String get thisPhoneAnswerOff => 'The server is off';

  @override
  String get thisPhoneAnswerOffLine => 'It stays off until you start it.';

  @override
  String get thisPhoneAnswerAndroidClosed => 'Android closed the server';

  @override
  String get thisPhoneAnswerStoppedItself => 'The server stopped by itself';

  @override
  String thisPhoneAnswerRestartsIn(String time) {
    return 'It restarts by itself in $time';
  }

  @override
  String get thisPhoneAnswerRestartsSoon => 'It restarts by itself in a moment';

  @override
  String get thisPhoneAnswerRestarting => 'Restarting by itself';

  @override
  String get thisPhoneAnswerGaveUp =>
      'It kept stopping, so it won\'t restart by itself.';

  @override
  String get thisPhoneAnswerRestartNow => 'Restart now';

  @override
  String get thisPhoneAnswerStartFailed => 'The server didn\'t start';

  @override
  String get thisPhoneAnswerInstallFailed => 'Setup didn\'t finish';

  @override
  String get thisPhoneAnswerCheckFailed => 'Couldn\'t check the server';

  @override
  String get thisPhoneAnswerStopFailed => 'The server didn\'t stop';

  @override
  String get thisPhoneAnswerSwitchStopped => 'The switch didn\'t finish';

  @override
  String get thisPhoneAnswerTermux => 'Can\'t reach Termux';

  @override
  String get thisPhoneAnswerRecovery => 'The server can\'t restart safely';

  @override
  String get thisPhoneAnswerNotSetUp => 'OpenCode isn\'t on this phone yet';

  @override
  String get thisPhoneAnswerSettingUp => 'Setting up the server';

  @override
  String get thisPhoneAnswerStarting => 'Starting the server';

  @override
  String get thisPhoneAnswerStopping => 'Stopping the server';

  @override
  String get thisPhoneAnswerChecking => 'Checking the server';

  @override
  String get thisPhoneAnswerRemoving => 'Removing the server';

  @override
  String get thisPhoneAnswerConnecting => 'Connecting to the server';

  @override
  String get thisPhoneRestartRow => 'Restart OpenCode on this phone';

  @override
  String get thisPhoneRestartTitle => 'Restart the server?';

  @override
  String get thisPhoneRestartBusy =>
      'A reply is being written. Restarting stops it; your conversations are kept.';

  @override
  String thisPhoneAboutSeconds(int seconds) {
    return 'about $seconds s';
  }

  @override
  String thisPhoneAboutMinutes(int minutes) {
    return 'about $minutes min';
  }

  @override
  String get thisPhoneAnswerStartFailedLine =>
      'Start it again; Details below says what went wrong.';

  @override
  String get serverSwitcherAddServer => 'Add a server';

  @override
  String get serverSwitcherNotAnswering => 'Isn\'t answering';

  @override
  String serverSwitcherChecksAgain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'checks again in $count min',
      one: 'checks again in 1 min',
    );
    return '$_temp0';
  }

  @override
  String serverSwitcherTryAgainLabel(String server) {
    return 'Try $server again';
  }

  @override
  String get serverSwitcherOpenRequest => 'Open';

  @override
  String serverSwitcherOpenRequestLabel(String target) {
    return 'Open $target';
  }

  @override
  String serverSwitcherAllowLabel(String command, String server) {
    return 'Allow $command once on $server';
  }

  @override
  String serverSwitcherUndoLabel(String server) {
    return 'Undo the answer on $server';
  }

  @override
  String kitRequestMoreTargets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '+$count more',
      one: '+1 more',
    );
    return '$_temp0';
  }

  @override
  String kitRequestChangeSize(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String chatRequestAskRunIn(String command, String project) {
    return 'Run $command in $project?';
  }

  @override
  String chatRequestAskRunNamed(String command) {
    return 'Run $command?';
  }

  @override
  String chatRequestAskRunAnyIn(String project) {
    return 'Run a command in $project?';
  }

  @override
  String chatRequestAskEditNamed(String file) {
    return 'Edit $file?';
  }

  @override
  String chatRequestAskEditFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Edit $count files?',
      one: 'Edit 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskCreateNamed(String file) {
    return 'Create $file?';
  }

  @override
  String chatRequestAskCreateFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Create $count files?',
      one: 'Create 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskDeleteNamed(String file) {
    return 'Delete $file?';
  }

  @override
  String chatRequestAskDeleteFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Delete $count files?',
      one: 'Delete 1 file?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskReadNamed(String file) {
    return 'Read $file?';
  }

  @override
  String chatRequestAlwaysAllowNamed(String what) {
    return 'Always allow $what';
  }

  @override
  String get chatRequestAlwaysAllowEdits => 'Always allow edits';

  @override
  String get chatRequestAlwaysAllowAnyCommand => 'Always allow every command';

  @override
  String get chatRequestAlwaysEditsWhat => 'edits';

  @override
  String get chatRequestAlwaysAnyCommandWhat => 'every command';

  @override
  String get filesAskAboutFile => 'Ask about this file';

  @override
  String filesMenuChanges(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Changes · $count files',
      one: 'Changes · 1 file',
      zero: 'Changes',
    );
    return '$_temp0';
  }

  @override
  String filesProjectSwitchLabel(String name) {
    return 'Project $name. Switch project';
  }

  @override
  String get scheduleNew => 'New scheduled prompt';

  @override
  String get scheduleAsk => 'What should it do?';

  @override
  String get scheduleAskHint => 'Run the tests and sum up what failed';

  @override
  String get scheduleEveryDay => 'Every day';

  @override
  String get scheduleWeekdaysShort => 'Weekdays';

  @override
  String get scheduleCustom => 'Custom';

  @override
  String scheduleRuleDaily(String time) {
    return 'Every day at $time';
  }

  @override
  String scheduleRuleWeekdays(String time) {
    return 'Every weekday at $time';
  }

  @override
  String scheduleRuleHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Every $hoursString hours',
      one: 'Every hour',
    );
    return '$_temp0';
  }

  @override
  String scheduleForTime(String time) {
    return 'Schedule for $time';
  }

  @override
  String scheduleForWhen(String when) {
    return 'Schedule for $when';
  }

  @override
  String scheduleForHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Schedule every $hoursString hours',
      one: 'Schedule every hour',
    );
    return '$_temp0';
  }

  @override
  String scheduleForToday(String time) {
    return 'Schedule for today, $time';
  }

  @override
  String scheduleForTomorrow(String time) {
    return 'Schedule for tomorrow, $time';
  }

  @override
  String scheduleDayAt(String day, String time) {
    return '$day, $time';
  }

  @override
  String scheduleSent(String when) {
    return 'Sent $when';
  }

  @override
  String scheduleNotSent(String when) {
    return 'Didn\'t go out $when';
  }

  @override
  String scheduleSkippedWhen(String when) {
    return 'Skipped $when';
  }

  @override
  String get scheduleSendingNow => 'Sending now';

  @override
  String get scheduleDayToday => 'today';

  @override
  String get scheduleDayYesterday => 'yesterday';

  @override
  String get scheduleWheelToday => 'Today';

  @override
  String get scheduleWheelTomorrow => 'Tomorrow';

  @override
  String get scheduleWheelDay => 'Day';

  @override
  String get scheduleWheelHowOften => 'How often';

  @override
  String get scheduleClosedNote =>
      'Runs even with the app closed, sometimes a little late.';

  @override
  String get scheduleNeedPrompt => 'Write what it should do first.';

  @override
  String get scheduleNeedFuture => 'Pick a time later than now.';

  @override
  String get scheduleInThisConversation => 'In this conversation';

  @override
  String get scheduleOpenRun => 'Open the conversation';

  @override
  String get scheduleDigestShort => 'A note each morning about what ran';

  @override
  String get scheduleExactNote => 'Runs may start a little late.';

  @override
  String get scheduleExactAllow => 'Allow exact timing';

  @override
  String get scheduleNotifyOff =>
      'Notifications are off, so a run won\'t tell you it went out.';

  @override
  String get inboxTitle => 'Inbox';

  @override
  String get inboxEmptyTitle => 'Nothing waits for you';

  @override
  String get inboxFinishedLabel => 'Finished, not opened yet';

  @override
  String inboxOpenLabel(String conversation) {
    return 'Open $conversation';
  }

  @override
  String inboxOpenOnServer(String server) {
    return 'Open on $server';
  }

  @override
  String get inboxWaitingForAnswer => 'Waiting for your answer';

  @override
  String get inboxAsksPermission => 'Asks for your permission';

  @override
  String get inboxAsksQuestion => 'Asks you a question';

  @override
  String inboxUnreachable(String servers) {
    return '$servers isn\'t answering. What waits there isn\'t shown.';
  }

  @override
  String get kitRequestOpen => 'Open';

  @override
  String goToNeedsYouHint(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count waiting',
      one: '1 waiting',
      zero: 'Nothing waits',
    );
    return '$_temp0';
  }

  @override
  String inboxEmptyPartialLine(String servers) {
    return '$servers isn\'t answering, so it may have more';
  }

  @override
  String get kitFieldOptional => 'Optional';

  @override
  String get reportProblemAttachedLead =>
      'The error details and app information are attached.';

  @override
  String get reportProblemJobLead =>
      'The job\'s log, the error details and app information are attached.';

  @override
  String get reportProblemJobNoLogLead =>
      'No log was kept for this job. The error details and app information are attached.';

  @override
  String reportProblemKeptLead(String when) {
    return 'Noticed $when. Its details and the app information are attached.';
  }

  @override
  String get reportProblemNothingTitle => 'No error noticed';

  @override
  String get reportProblemNothingLead =>
      'Say what went wrong. Your words and the app information go with the report.';

  @override
  String get reportProblemPrivacy =>
      'Passwords, keys and server addresses are taken out.';

  @override
  String get reportProblemAddLabel => 'Anything to add?';

  @override
  String get reportProblemAddHint => 'What were you doing when it happened?';

  @override
  String get reportProblemSendGitHub => 'Send as a GitHub issue';

  @override
  String get reportProblemSendHint =>
      'Review it on GitHub before posting. It will be public.';

  @override
  String get reportProblemCopyShort => 'Copy';

  @override
  String get reportProblemKeptLink => 'Everything this phone kept';

  @override
  String welcomePhoneCost(String time, String size) {
    return '$time · $size de descarga';
  }

  @override
  String get welcomePhoneSetUp => 'Configurar en este teléfono';

  @override
  String welcomePhoneSettingUp(int percent) {
    return 'Configurando · $percent %';
  }

  @override
  String welcomePhoneStopped(int percent) {
    return 'Configuración al $percent %';
  }

  @override
  String get welcomePhoneContinue => 'Seguir configurando';

  @override
  String get welcomePhoneReady => 'Instalado y listo';

  @override
  String get welcomePhoneOpen => 'Empezar en este teléfono';

  @override
  String get welcomeComputerLine =>
      'Vincula con un código o escribe su dirección';

  @override
  String get welcomeComputerConnect => 'Conectar con mi computadora';

  @override
  String get welcomeMoreWays => 'Otras formas de configurar';

  @override
  String get welcomeDemoLine => 'Una conversación simulada';

  @override
  String kitProgressRingPercent(String percent) {
    return '$percent%';
  }

  @override
  String get kitProgressRingPercentSign => '%';

  @override
  String kitProgressRingSteps(String done, String total) {
    return '$done of $total';
  }

  @override
  String get kitProgressRingFinished => 'Finished';

  @override
  String phoneSetupRingDownloading(String name) {
    return 'Downloading $name';
  }

  @override
  String phoneSetupRingUnpacking(String name) {
    return 'Unpacking $name';
  }

  @override
  String phoneSetupRingInstalling(String name) {
    return 'Installing $name';
  }

  @override
  String get phoneSetupRingChecking => 'Checking this phone';

  @override
  String get phoneSetupRingPausedTitle => 'Setup is paused';

  @override
  String get phoneSetupRingInterruptedTitle => 'Setup was interrupted';

  @override
  String get phoneSetupRingStoppedNote =>
      'Finished steps stay, and downloads pick up where they left off.';

  @override
  String phoneSetupRingMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'About $minutes min left',
      one: 'About 1 min left',
    );
    return '$_temp0';
  }

  @override
  String get phoneSetupRingUnderMinute => 'Less than a minute left';

  @override
  String get phoneSetupRingWifi => 'Wi-Fi';

  @override
  String get phoneSetupRingMobileData => 'Mobile data';

  @override
  String get phoneSetupRingSafeToLeave =>
      'Safe to leave. We\'ll let you know when it\'s ready.';

  @override
  String get phoneSetupRingSafeToLeaveQuiet =>
      'Safe to leave. Setup keeps going in the background.';

  @override
  String get phoneSetupRingPause => 'Pause setup';

  @override
  String get phoneSetupRingDetailsTitle => 'Setup details';

  @override
  String kitProgressRingOfTotal(String total) {
    return 'of $total';
  }

  @override
  String get kitProgressRingStepsDone => 'steps done';

  @override
  String get devPreviewDetails => 'Preview details';

  @override
  String get devPreviewShowDetails => 'Show details';

  @override
  String get devPreviewAddress => 'Address';

  @override
  String get devPreviewAskHint => 'Ask for a change…';

  @override
  String get devPreviewAskSend => 'Send to the conversation';

  @override
  String get devPreviewAskStopAndSend => 'Stop the agent and send';

  @override
  String get devPreviewAskVoice => 'Speak your change';

  @override
  String get devPreviewAskListening => 'Listening…';

  @override
  String get devPreviewAskDoneSpeaking => 'Done speaking';

  @override
  String get devPreviewAskSent => 'Sent to the conversation';

  @override
  String get devPreviewAskSentJoins => 'Added to what the agent is doing';

  @override
  String get devPreviewAskSentAfter => 'Sent. It runs after the current reply';

  @override
  String get devPreviewAskSentRestart =>
      'Sent. The agent started again with it';

  @override
  String get devPreviewAskNotSent => 'Didn\'t send. Your words are still here';

  @override
  String get devPreviewAskNotHeard =>
      'Didn\'t catch that. Try again, or type it';

  @override
  String teamMergeGateQuestion(int count, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Merge $count changes into $branch?',
      one: 'Merge 1 change into $branch?',
      zero: 'Merge the changes into $branch?',
    );
    return '$_temp0';
  }

  @override
  String teamMergeGateChecksPassed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'all $count checks passed',
      one: 'the check passed',
    );
    return '$_temp0';
  }

  @override
  String get teamMergeGateApprove => 'Approve the merge';

  @override
  String get teamMergeGateIfIgnored =>
      'The changes wait on their branch. Nothing is merged until you say so.';

  @override
  String teamMergeGateAnnouncement(String question) {
    return 'Ready to merge. $question';
  }

  @override
  String get teamMergeGateStale => 'Merge is off until the team answers again.';

  @override
  String teamTaskNowOn(String who, String step) {
    return 'Now: $who on $step';
  }

  @override
  String teamTaskNowWorker(String who) {
    return 'Now: $who is working';
  }

  @override
  String get teamTaskNowWorking => 'Now: working on your task';

  @override
  String teamTaskNowStarting(String who) {
    return 'Now: $who is starting';
  }

  @override
  String get teamTaskNowStartingWorker => 'Now: starting a worker';

  @override
  String get teamTaskNowWaitingWorker => 'Now: waiting for a worker';

  @override
  String get teamTaskNowPlanning => 'Now: planning the steps';

  @override
  String get teamTaskNowReviewing => 'Now: reviewing the changes';

  @override
  String get teamTaskNextReview => 'Next: review';

  @override
  String get teamTaskNextMerge => 'Next: merge';

  @override
  String get teamTaskNextWork => 'Next: the worker begins';

  @override
  String get teamTaskStepsDone => 'All steps done';

  @override
  String teamTaskFinishedAgo(String age) {
    return 'Finished $age ago';
  }

  @override
  String get kitComposerTrayClose => 'Close';

  @override
  String get composerTrayPhotos => 'Photos';

  @override
  String get composerTrayCamera => 'Camera';

  @override
  String get composerTrayFile => 'File';

  @override
  String get composerTrayMore => 'More';

  @override
  String chatLiveReading(String name) {
    return 'Reading $name';
  }

  @override
  String get chatLiveReadingAny => 'Reading a file';

  @override
  String chatLiveEditing(String name) {
    return 'Editing $name';
  }

  @override
  String get chatLiveEditingAny => 'Editing files';

  @override
  String chatLiveWriting(String name) {
    return 'Writing $name';
  }

  @override
  String get chatLiveWritingAny => 'Writing a file';

  @override
  String chatLiveRunning(String name) {
    return 'Running $name';
  }

  @override
  String get chatLiveRunningAny => 'Running a command';

  @override
  String chatLiveSearching(String name) {
    return 'Searching for $name';
  }

  @override
  String get chatLiveSearchingAny => 'Searching the project';

  @override
  String get chatLiveSearchingWeb => 'Searching the web';

  @override
  String get chatLivePlanning => 'Planning the steps';

  @override
  String get terminalSessionsLabel => 'Open terminals';

  @override
  String get terminalSessionEnded => 'ended';

  @override
  String localTerminalStopNamed(String name) {
    return 'Stop $name';
  }

  @override
  String localTerminalCloseNamed(String name) {
    return 'Close $name';
  }

  @override
  String get terminalDevServerRunning => 'Dev server running';

  @override
  String get terminalOpenPreview => 'Open preview';

  @override
  String terminalPlaceLabel(String place) {
    return 'Runs on $place';
  }

  @override
  String terminalPlaceSwitchLabel(String place) {
    return 'Runs on $place, change where it runs';
  }

  @override
  String get terminalScreenDetailState => 'State';

  @override
  String get terminalStateRunning => 'Running';

  @override
  String get terminalStateEnded => 'Ended';

  @override
  String connectionTryingAgainIn(int seconds) {
    return 'trying again in $seconds s';
  }

  @override
  String get connectionTryingAgainNow => 'trying again now…';

  @override
  String get chatsHomeNothingRunning => 'Nothing running';

  @override
  String get chatsHomeNothingRunningBody =>
      'Start a conversation, or open one to pick up where you left off.';

  @override
  String get chatsHomeShowAll => 'Show all conversations';

  @override
  String get chatsHomeEmptyCalmBody =>
      'Say what you want to build, and an agent gets to work.';

  @override
  String get chatsHomeConnectComputer => 'Connect a computer';

  @override
  String get chatsHomeProjectEmptyBody =>
      'Ask for something in this project, and it shows up here.';

  @override
  String get scheduleEmptyBody =>
      'Pick a time, and this phone sends the prompt for you.';

  @override
  String get projectsLoadFailedTitle => 'Projects didn\'t load';

  @override
  String get chatStopAfterStep => 'Stop after this step';

  @override
  String get chatStopAfterStepPrompt =>
      'Please stop after the step you are on now and wait for me before doing anything else.';

  @override
  String get appearanceModeAuto => 'Auto';

  @override
  String get appearanceModeSaver => 'Saver';

  @override
  String get appearanceSaverNote =>
      'True black, no motion. Saves battery on OLED screens.';

  @override
  String get appearanceAccentTitle => 'Accent';

  @override
  String get appearanceAccentMint => 'Mint';

  @override
  String get appearanceAccentTeal => 'Teal';

  @override
  String get appearanceAccentBlue => 'Blue';

  @override
  String get appearanceAccentViolet => 'Violet';

  @override
  String get appearanceAccentPink => 'Pink';

  @override
  String get appearanceAccentMono => 'Mono';

  @override
  String get appearanceAccentSilver => 'Silver';

  @override
  String get appearanceSurfaceSolid => 'Solid';

  @override
  String get appearanceSurfaceSoft => 'Soft';

  @override
  String get appearanceSurfaceBrutal => 'Brutal';

  @override
  String get appearanceSurfacePaper => 'Paper';

  @override
  String get signalStyleName => 'Signal';

  @override
  String get signalShow => 'Show';

  @override
  String get signalHide => 'Hide';

  @override
  String signalRowRunningCommand(String command) {
    return 'Running $command';
  }

  @override
  String get appearanceFineTuneCorners => 'Corners';

  @override
  String get appearanceFineTuneCornersRound => 'Round';

  @override
  String get appearanceFineTuneCornersSoft => 'Soft';

  @override
  String get appearanceFineTuneCornersSharp => 'Sharp';

  @override
  String get appearanceFineTuneDepth => 'Depth';

  @override
  String get appearanceFineTuneEdges => 'Edges';

  @override
  String appearanceFineTuneReset(String style) {
    return 'Reset to $style defaults';
  }

  @override
  String get kitTourNext => 'Next';

  @override
  String get kitTourSkip => 'Skip';

  @override
  String get kitTourDone => 'Done';

  @override
  String kitTourStep(int current, int total) {
    return 'Step $current of $total';
  }

  @override
  String get tipStopAfterStep => 'Hold to stop after this step';

  @override
  String get tipStopAfterStepTitle => 'Stop after this step';

  @override
  String get tipSendLater => 'Hold Send to send later or repeat';

  @override
  String get tipSendLaterTitle => 'Send later or repeat';

  @override
  String get tourAppearanceStart => 'Show me how · 30 s';

  @override
  String get tourNotNow => 'Not now';

  @override
  String get tipsTitle => 'Tips';

  @override
  String get tipsHubSubtitle => 'Hidden gestures and short tours';

  @override
  String get tipsSearchAliases =>
      'tips tutorial tour hints gestures hold long-press how to learn';

  @override
  String get tipsIntro =>
      'Each tip shows at its moment, at most twice. Replay any of them here.';

  @override
  String get tipsShowMe => 'Show me';

  @override
  String get tipsTipsLabel => 'Tips';

  @override
  String get tipsToursLabel => 'Mini tutorials';

  @override
  String get chatStopAfterStepPending => 'Stopping after this step';

  @override
  String get chatStopAfterStepUndo => 'Undo';

  @override
  String get chatStopAfterStepUndone => 'It will carry on.';

  @override
  String get chatStopAfterStepTooLate =>
      'Too late: the agent already has the note.';

  @override
  String get chatLiveShowSteps => 'Show steps';

  @override
  String get chatLiveHideSteps => 'Hide steps';

  @override
  String get kitComposerStopPending => 'stopping after this step';

  @override
  String get phraseChipsRetryStarter => 'Try again';

  @override
  String get tourMakeItYoursTitle => 'Make it yours';

  @override
  String get tourMakeItYoursOffer =>
      'Make it yours: light or dark, colours and style, in four steps';

  @override
  String get tourMakeItYoursColours =>
      'Start here: light or dark, a theme and an accent. The preview above shows every change at once.';

  @override
  String get tourMakeItYoursStyle =>
      'Pick how cards and bars feel: Solid, Soft, Brutal or Paper.';

  @override
  String get kitGuideChanges => 'Changes';

  @override
  String get kitGuideAll => 'All';

  @override
  String get kitGuideBack => 'Back to all chips';

  @override
  String get appearanceGroupColours => 'Colours';

  @override
  String get appearanceGroupStyle => 'Style';

  @override
  String get appearanceGroupBackground => 'Background';

  @override
  String get appearanceGroupComfort => 'Text & motion';

  @override
  String appearanceGroupPair(String first, String second) {
    return '$first, $second';
  }

  @override
  String get appearanceChipMode => 'Mode';

  @override
  String get appearanceChipTheme => 'Theme';

  @override
  String get appearanceChipAccent => 'Accent';

  @override
  String get appearanceChipStyle => 'Style';

  @override
  String get appearanceChipShape => 'Shape';

  @override
  String get appearanceChipLook => 'Look';

  @override
  String get appearanceChipStrength => 'Strength';

  @override
  String get appearanceChipText => 'Text size';

  @override
  String get appearanceChipMotion => 'Motion';

  @override
  String get appearanceChipGlow => 'Reply glow';

  @override
  String get appearanceAskMode => 'Light or dark?';

  @override
  String get appearanceAskTheme => 'Which colours?';

  @override
  String get appearanceAskAccent => 'Which accent colour?';

  @override
  String get appearanceAskStyle => 'How should surfaces feel?';

  @override
  String get appearanceAskShape => 'Corners and spacing';

  @override
  String get appearanceAskLook => 'What moves behind your pages?';

  @override
  String get appearanceAskStrength => 'How strong?';

  @override
  String get appearanceAskText => 'How big should text be?';

  @override
  String get appearanceAskMotion => 'How much should move?';

  @override
  String get appearanceAskGlow => 'Glow while a reply is written?';

  @override
  String get appearanceChangesMode => 'Every page, light or dark';

  @override
  String get appearanceChangesTheme => 'The colours of every page';

  @override
  String get appearanceChangesAccent =>
      'Buttons, links, the send key and the glow';

  @override
  String get appearanceChangesStyle => 'Every card, bar and message bubble';

  @override
  String get appearanceChangesShape => 'Corners and spacing of this style';

  @override
  String get appearanceChangesLook => 'The background of every page';

  @override
  String get appearanceChangesStrength =>
      'How much of the background shows through';

  @override
  String get appearanceChangesText =>
      'Text everywhere, on top of your phone\'s size';

  @override
  String get appearanceChangesMotion => 'Animations across the app';

  @override
  String get appearanceChangesGlow => 'The border of the message box';

  @override
  String get appearanceOffInSaver => 'Off in Saver';

  @override
  String get appearanceLookOff => 'Off';

  @override
  String get appearanceStrengthNoLook => 'No look';

  @override
  String appearanceShapeValue(String corners, String spacing) {
    return '$corners, $spacing';
  }

  @override
  String get appearanceShapeSpacing => 'Spacing';

  @override
  String get appearanceSpacingRoomy => 'Roomy';

  @override
  String get appearanceSpacingCompact => 'Compact';

  @override
  String appearanceShapeNote(String style, String corners, String spacing) {
    return '$style starts at $corners and $spacing. Picking another style starts these over.';
  }

  @override
  String get appearanceMotionPhraseFull => 'full motion';

  @override
  String get appearanceMotionPhraseCalm => 'calm motion';

  @override
  String get appearanceMotionPhraseOff => 'no motion';

  @override
  String get appearanceGlowOff => 'Off';

  @override
  String get appearanceGlowOffHint => 'A plain border';

  @override
  String get appearanceGlowClassicHint => 'A light that circles the box';

  @override
  String get appearanceGlowSoftHint => 'A ring that slowly breathes';

  @override
  String get appearanceModeAutoNote =>
      'Follows your phone: light by day, dark at night.';

  @override
  String appearanceSeeAllThemes(int count) {
    return 'See all $count themes';
  }

  @override
  String appearanceSeeAllLooks(int count) {
    return 'See all $count looks';
  }

  @override
  String appearanceAccentMatchNote(String theme) {
    return '“Match” uses $theme\'s own accent.';
  }

  @override
  String get appearanceStrengthNeedsLook =>
      'The background is off, so there is nothing to strengthen.';

  @override
  String get appearanceChooseLookFirst => 'Choose a look first';

  @override
  String get appearanceSaverHoldsStyle =>
      'Saver keeps cards solid and the background still, to save battery on this screen. Your style comes back when Saver is off.';

  @override
  String get appearanceSaverHoldsBackground =>
      'Saver keeps cards solid and the background still, to save battery on this screen. Your background comes back when Saver is off.';

  @override
  String get appearanceTurnOffSaver => 'Turn off Saver';

  @override
  String get appearanceSaverMotion => 'Saver keeps motion off.';

  @override
  String get appearanceGuideStart =>
      'Start with Colours, the biggest change. Every choice saves as you go, and you can tap any row at any time.';

  @override
  String get appearanceGuideSkip => 'Skip the guide';

  @override
  String get appearanceGuideResume =>
      'Pick up where you left off, or tap any chip.';

  @override
  String get appearanceGuideAllSetTitle => 'All set.';

  @override
  String get appearanceGuideAllSet =>
      'Your choices are saved. Tap any chip to change one.';

  @override
  String get appearanceGuideAgain => 'Guide me again';

  @override
  String get appearanceGuideReset => 'Reset to defaults';

  @override
  String get appearanceGuideResetConfirm =>
      'Put every Appearance choice back to the defaults? Light or dark, theme, accent, style, background, text size, motion and the reply glow all reset.';

  @override
  String get appearanceGuideResetDo => 'Reset';

  @override
  String get appearanceGuideResetKeep => 'Keep my choices';

  @override
  String appearanceGuideCountIntro(int count) {
    return '$count quick questions, all optional';
  }

  @override
  String appearanceGuideCountSeen(int seen, int total) {
    return '$seen of $total seen';
  }

  @override
  String appearanceGuideCountAt(int at, int total) {
    return '$at of $total';
  }

  @override
  String get appearanceGuideSavesAsYouGo => 'Everything saves as you go';

  @override
  String get appearanceGuideStartButton => 'Start with Colours';

  @override
  String appearanceGuideContinue(String group) {
    return 'Continue: $group';
  }

  @override
  String appearanceGuideNext(String name) {
    return 'Next: $name';
  }

  @override
  String get appearanceGuideFinish => 'Finish';

  @override
  String appearanceGuidePlace(
    String label,
    String value,
    int at,
    int count,
    String group,
  ) {
    return '$label, $value, question $at of $count in $group';
  }

  @override
  String get tourMakeItYoursBackground =>
      'Pick what moves behind your pages, or turn it off.';

  @override
  String get tourMakeItYoursComfort =>
      'Make text bigger, calm the motion and choose the glow while a reply is written.';

  @override
  String get appearanceAccentMatchShort => 'Match';

  @override
  String get chatTurnStoppedElsewhere =>
      'This reply was stopped from another app or device.';

  @override
  String get chatTurnPausedInactive =>
      'The server paused this reply after an hour with no new work.';

  @override
  String get chatTurnStoppedServerRestart =>
      'The server restarted, so this reply stopped.';

  @override
  String get chatTurnSuperseded => 'A newer run took this reply\'s place.';

  @override
  String get chatTurnStoppedEarly =>
      'This reply was stopped before it finished.';

  @override
  String get settingsV3ModelNewOnPhone => 'New conversations on this phone';

  @override
  String chatLoadAgentMissing(String agent) {
    return '$agent on this phone doesn\'t have this conversation. Go back and open it from your conversations.';
  }

  @override
  String chatLoadAgentUnreadable(String agent) {
    return '$agent on this phone answered, but this app couldn\'t read the reply. Try again, or report a problem.';
  }

  @override
  String get productErrorUnreadableReply =>
      'It answered, but this app couldn\'t read the reply. Try again, or report a problem.';

  @override
  String get settingsV3SavedOnPhone =>
      'Saved on this phone. Other apps using this phone\'s server get this too.';

  @override
  String settingsV3UseMyDefaultDonePhone(String what) {
    return 'Reset on this phone to your own choices: $what.';
  }

  @override
  String get chatStallSubagent =>
      'This sub-agent has been quiet for a while. It stops with its main conversation: stop the reply there.';

  @override
  String get serverPageThisPhonesServer => 'this phone\'s server';

  @override
  String kitBackgroundWorkOne(String task, String time) {
    return 'Still running in the background · $task · $time';
  }

  @override
  String kitBackgroundWorkMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks still running in the background',
      one: '1 task still running in the background',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkTask(String task, String time) {
    return '$task · $time';
  }

  @override
  String kitBackgroundWorkMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes min',
      zero: 'under a minute',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String get kitBackgroundWorkGoToStep => 'Shows the step that started it';

  @override
  String kitBackgroundWorkOneAfterStop(String task, String time) {
    return 'Still running after the turn stopped · $task · $time';
  }

  @override
  String kitBackgroundWorkManyAfterStop(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tasks still running after the turn stopped',
      one: '1 task still running after the turn stopped',
    );
    return '$_temp0';
  }

  @override
  String chatAgentTurnLostConnectionTo(String agent, String company) {
    return '$agent lost its connection to $company, so the turn stopped.';
  }

  @override
  String chatAgentTurnLostConnection(String agent) {
    return '$agent lost its connection to its model provider, so the turn stopped.';
  }

  @override
  String chatSendFailureLostConnectionTo(String agent, String company) {
    return '$agent lost its connection to $company';
  }

  @override
  String chatSendFailureLostConnection(String agent) {
    return '$agent lost its connection to its model provider';
  }

  @override
  String chatBackgroundWorkWaitingFor(String program) {
    return 'Waiting for $program';
  }

  @override
  String chatSendFailureSignIn(String agent) {
    return '$agent needs you to sign in';
  }

  @override
  String chatSendFailureNoCredit(String agent) {
    return '$agent has no credits for this model';
  }

  @override
  String get chatSendFailureModelUnavailable => 'This model isn\'t available';

  @override
  String chatSendFailureBusy(String agent) {
    return '$agent is busy';
  }

  @override
  String get chatSendFailureAgentFallback => 'The agent';

  @override
  String get chatSendFailureSignInAction => 'Sign in';

  @override
  String get chatSendFailureAccountAction => 'Open account';

  @override
  String get chatSendFailureDetailsAgent => 'Agent';

  @override
  String get chatSendFailureDetailsModel => 'Model';

  @override
  String get chatSendFailureDetailsCode => 'Error code';

  @override
  String get chatSendFailureNewConversation =>
      'This agent can\'t continue this conversation';

  @override
  String chatSendFailureNeedsAccount(String agent) {
    return 'This model needs a $agent account';
  }

  @override
  String chatSendFailureSignInToAgent(String agent) {
    return 'Sign in to $agent';
  }

  @override
  String get chatComposerNoteDismiss => 'Dismiss note';

  @override
  String get chatAnnounceReplyFinished => 'Reply finished';

  @override
  String get chatAnnounceNeedsYou => 'The agent needs you';

  @override
  String get runningPhoneTitle => 'Running on this phone';

  @override
  String runningPhoneSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running · $memory',
      one: '1 running · $memory',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneAutoRefresh =>
      'Checks again every 5 seconds while open';

  @override
  String get runningPhoneLimitTitle => 'Servers may be stopped';

  @override
  String runningPhoneLimitBody(int count, int limit) {
    return '$count of $limit background processes: above $limit, Android stops this app\'s oldest processes first, and those are the servers. Stop what was left behind or isn\'t needed, or lift the limit with Developer options › Disable child process restrictions.';
  }

  @override
  String runningPhoneGroup(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind': 'Left behind',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Agent helper',
      'aiTeam': 'AI Team',
      'terminal': 'Terminal',
      'setup': 'Setup and checks',
      'helpers': 'Helpers',
      'app': 'This app',
      'other': 'Other',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneGroupChat(String title) {
    return 'Claude Code · $title';
  }

  @override
  String runningPhoneName(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'flutterTest': 'Flutter test',
      'command': 'Command',
      'linux': 'Linux',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Agent helper',
      'signIn': 'Agent sign-in',
      'team': 'AI Team engine',
      'node': 'Node.js',
      'python': 'Python',
      'git': 'Git',
      'npm': 'npm',
      'dart': 'Dart',
      'flutter': 'Flutter',
      'gradle': 'Gradle',
      'java': 'Java',
      'search': 'Search',
      'app': 'This app',
      'other': 'Program',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneCpu(String level, String percent) {
    String _temp0 = intl.Intl.selectLogic(level, {
      'idle': 'Idle',
      'light': 'Light',
      'busy': 'Busy',
      'other': 'Very busy',
    });
    return '$_temp0 · $percent%';
  }

  @override
  String get runningPhoneMarkHeavy => 'Heavy';

  @override
  String get runningPhoneMarkStuck => 'May be stuck';

  @override
  String get runningPhoneMarkLeftBehind => 'Left behind';

  @override
  String runningPhoneFromChat(String title) {
    return 'Started by Claude Code · $title';
  }

  @override
  String get runningPhoneFromClaude => 'Started by Claude Code';

  @override
  String get runningPhoneStop => 'Stop';

  @override
  String runningPhoneStopNamed(String name) {
    return 'Stop $name';
  }

  @override
  String runningPhoneStopTitle(String name) {
    return 'Stop $name?';
  }

  @override
  String get runningPhoneStopBody =>
      'It gets a polite stop first, then a forced one if it keeps running.';

  @override
  String runningPhoneStopAllLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stop all $count left behind',
      one: 'Stop the 1 left behind',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneStopAllDetail =>
      'Nothing else stops them, and stopping them is safe.';

  @override
  String get runningPhoneStopAllTitle => 'Stop everything left behind?';

  @override
  String runningPhoneStopAllBody(String names) {
    return '$names. Each gets a polite stop first, then a forced one if it keeps running.';
  }

  @override
  String runningPhoneStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Stopped $count',
      one: 'Stopped 1',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneAlreadyEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count had already ended',
      one: '1 had already ended',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneNotStoppedTitle => 'Not everything stopped';

  @override
  String runningPhoneRefusedApp(String name) {
    return '$name: this app itself can\'t be stopped here.';
  }

  @override
  String runningPhoneRefusedServer(String name) {
    return '$name: it runs a server. Stop the server from This phone.';
  }

  @override
  String runningPhoneRefusedStillRunning(String name) {
    return '$name: it kept running after a forced stop. Try again, or restart the phone.';
  }

  @override
  String get runningPhoneStopFailed => 'Couldn\'t stop it. Try again.';

  @override
  String get runningPhoneLoadFailedTitle => 'Couldn\'t read what\'s running';

  @override
  String get runningPhoneLoadFailedBody =>
      'The phone didn\'t answer. Try again.';

  @override
  String get runningPhoneRefreshFailed =>
      'Couldn\'t check again, so this shows the last reading.';

  @override
  String get runningPhoneEmpty => 'Nothing else is running';

  @override
  String get runningPhoneEmptyBody =>
      'When OpenCode, Claude Code or a terminal runs here, it shows up in this list.';

  @override
  String runningPhoneAbout(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind':
          'Left running after its Linux ended. Nothing is waiting on it, so stopping it is safe.',
      'openCode': 'Part of the OpenCode server on this phone.',
      'claudeCode': 'Part of a Claude Code conversation.',
      'agentHelper': 'Runs Claude Code and other agents on this phone.',
      'aiTeam': 'Part of the AI Team.',
      'terminal': 'Runs in a terminal on this phone.',
      'setup': 'A setup step or a check.',
      'helpers': 'A helper service of this app.',
      'app': 'This app itself.',
      'other': 'Started by this app.',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneHeavyFor(String elapsed) {
    return 'It has used more than 80% of a processor core for $elapsed.';
  }

  @override
  String runningPhoneStuckWhy(String elapsed) {
    return 'It has run near 100% for $elapsed with no conversation waiting on it.';
  }

  @override
  String get runningPhoneServerRoot =>
      'It runs a server. Stop the server from This phone.';

  @override
  String get runningPhoneConversationId => 'Claude Code conversation ID';

  @override
  String get runningPhoneOwner => 'Belongs to';

  @override
  String runningPhoneRowNormal(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count running · $memory',
      one: '1 running · $memory',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count left behind, still using the phone',
      one: '1 left behind, still using the phone',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowHeavy(int count, String name, String percent) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count heavy, $name the most · $percent%',
      one: '$name is heavy · $percent%',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowNearLimit(int count, int limit) {
    return '$count of $limit: Android may stop the servers';
  }

  @override
  String get runningPhoneRowIdle => 'See what runs and what heats the phone';

  @override
  String chatStuckCommand(String time) {
    return '$time · may be stuck';
  }

  @override
  String chatStuckMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String chatStuckHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String chatStuckUnnamed(String time) {
    return 'Still running · $time · may be stuck';
  }

  @override
  String chatStuckWaitsNote(String command) {
    return 'Waits for $command to finish. Stop it to get through now.';
  }

  @override
  String get chatStuckNeverEnds =>
      'This reply may never end. Stop and send gets your message through now.';

  @override
  String get chatStuckThisStep => 'this step';

  @override
  String get kitComposerAddsToReply => 'Adds to this reply';

  @override
  String kitComposerCantTakeMidReply(String agent) {
    return '$agent can\'t take a message mid-reply.';
  }

  @override
  String get kitTurnLiveStopShort => 'Stop';

  @override
  String get frostGlassQuestion => 'Glass';

  @override
  String get frostGlassOff => 'Off';

  @override
  String get frostGlassWeak => 'Weak';

  @override
  String get frostGlassBalanced => 'Balanced';

  @override
  String get frostGlassStrong => 'Strong';

  @override
  String get frostGlassHint =>
      'When the conversation scrolls under the message box or the header, they softly blur it. Off keeps them solid.';

  @override
  String get frostGlassHeldSaver =>
      'Saver is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldMotion =>
      'Reduced motion is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldContrast =>
      'High contrast is on, so the message box and header stay solid.';

  @override
  String get frostGlassHeldStyle =>
      'This style keeps its own look, so the message box and header stay solid.';

  @override
  String stopRowSemantics(String agent) {
    return 'Stop $agent';
  }
}

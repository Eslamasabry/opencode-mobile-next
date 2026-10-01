import 'dart:async';
import 'dart:collection';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:clock/clock.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show compute, listEquals;
import 'package:flutter/scheduler.dart' show SchedulerPhase;
import 'package:flutter/rendering.dart' show ScrollDirection;
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../api/server_probe.dart' show ServerFlavor;
import '../../api/sse.dart';
import '../../domain/prompt_attachment.dart';
import '../../domain/background_work.dart';
import '../../domain/background_agent_result.dart';
import '../../domain/background_shell_result.dart';
import '../../domain/session_handoff.dart';
import '../../domain/run_result.dart';
import '../../domain/session_history.dart';
import '../../domain/transcript_search.dart';
import 'running_work_sheet.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/offline_queue.dart';
import '../../state/connection.dart';
import '../../state/first_reply_notify_offer.dart';
import '../../state/free_model_notice.dart'
    show FreeModelNoteDismissals, freeModelNoteDue;
import '../../state/session_tail_cache.dart' show SessionTailPreview;
import '../../state/profiles.dart' show ServerBackend, ServerProfile;
import '../../state/conversation_nudges.dart';
import '../../state/nudges.dart';
import '../../state/review_handoff.dart';
import '../../state/interaction_defaults.dart' show DefaultKind, DefaultReason;
import '../../state/migration_runner.dart' show DraftMigrationBlocker;
import '../../state/prompt_shelf.dart';
import '../../state/session_drafts.dart';
import '../../state/session_auto_approval.dart';
import '../../state/draft_attachments.dart';
import '../../state/prompt_photos.dart';
import '../../voice/audio.dart' show VoicePermissionDenied;
import '../../voice/controller.dart';
import '../../voice/device.dart' show voiceDevicePlatform;
import '../../voice/presentation.dart' show voiceErrorText;
import '../../voice/voice_ui.dart';
import '../../voice/read_aloud.dart';
import '../navigation/chat_route.dart';
import '../../domain/agent_error_text.dart';
import '../../domain/office_text.dart';
import '../agent_error_words.dart';
import '../app_theme.dart';
import '../desktop/desktop_interaction.dart';
import '../desktop/file_drop.dart';
import '../desktop/shortcuts.dart';
import '../widgets/always_allow_invitation.dart';
import '../widgets/command_sheet.dart';
import '../widgets/session_menu.dart';
import '../widgets/safety_confirms.dart';
import '../widgets/default_notices.dart';
import '../widgets/file_preview.dart';
import '../widgets/markdown.dart';
import '../widgets/phone_server_card.dart' show serverDisplayName;
import '../widgets/pickers.dart';
import '../widgets/model_shortcuts.dart';
import '../widgets/product_states.dart';
import '../widgets/prompt_history_navigation.dart';
import '../widgets/last_known_sessions.dart' show LastKnownSessions;
import '../widgets/queued_prompt_move_sheet.dart'
    show showQueuedPromptMoveSheet;
import '../widgets/transcript_highlight.dart';
import '../widgets/question_options.dart';
import '../widgets/session_title.dart';
import '../widgets/session_read_state.dart';
import '../widgets/session_handoff_sheets.dart';
import '../widgets/running_agents_strip.dart';
import '../widgets/tool_card.dart';
import '../../api2/models.dart' show Api2Delivery, Api2FormInfo, Api2InboxItem;
import '../../feedback/bug_report.dart' show openBugReport;
import '../kit/kit.dart';
import '../../domain/orchestration_gateway.dart';
import '../../domain/team_agent_sessions.dart';
import '../../state/orchestration.dart';
import '../../state/team_glance.dart';
import '../../builtin/builtin_server.dart';
import '../../builtin/team/builtin_team.dart';
import '../../state/team_dispatch.dart';
import '../../state/team_conversation.dart';
import '../../state/team_roles.dart';
import '../../state/team_worker_start.dart';
import '../../state/team_planning.dart'
    show
        teamPlanningRequests,
        teamPlanningRunMatches,
        teamPlannerAgent,
        teamPlannerIsOff;
import '../widgets/team_controls.dart' show teamControlReceipt;
import '../widgets/team_now.dart' show teamCheckInterval, teamUnstickAction;
import '../widgets/team_now_line_view.dart';
import '../widgets/team_receipt.dart' show teamReceiptLine;
import '../widgets/team_role_copy.dart';
import '../widgets/team_vocabulary.dart';
import 'team/agent_screen.dart' show AgentScreen;
import 'team/gate_sheet.dart' show showGateSheet;
import 'team/merge_section.dart' show TeamMergeSection;
import 'team/task_details_sheet.dart' show showTeamTaskDetails;
import 'team/team_home_screen.dart' show TeamHomeScreen;
import 'team/team_page.dart' show openTeamPage;
import 'team/work_sheet.dart' show showWorkSheet;
import '../widgets/team_moments.dart' show TeamMergedCelebration;
import 'team/team_needs_you.dart'
    show TeamNeedsYouCard, teamGateWho, teamOpenGates;
import 'team_conversation/team_conversation.dart' show TeamConversation;
import '../kit/scenes/states_scenes.dart';
import '../widgets/grace_timer.dart';
import '../permission_presentation.dart';
import 'activity_screen.dart' show showQuestionSheet;
import 'app_diagnostics_screen.dart';
import '../../diagnostics/perf_trace.dart';
import 'chat/form_flow.dart';
import 'chat/permission_sheet.dart';
import 'files_screen.dart';
import 'global_sessions_screen.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'project_health_screen.dart';
import 'review_workspace.dart';
import 'session_context_screen.dart';
import 'session_note_screen.dart';
import 'session_export_screen.dart';
import 'staged_revert_screen.dart';
import 'session_destination_sheet.dart';
import 'session_relations_screen.dart';
import 'settings_screen.dart';
import 'capabilities_screen.dart';
import 'terminal_screen.dart';
import 'web_sources_screen.dart';
import '../early_l10n.dart';

// The chat screen is one library. This file holds the widget and its
// State's core: shared fields, lifecycle, the build entry and dispose. Each
// concern lives in a part file under chat/: its fields in a
// `mixin _ChatXFields` the State mixes in, its code in an
// `extension _ChatX on _ChatScreenState` (which calls [_setChatState], as
// setState is protected). Methods handed out as callbacks whose identity
// matters stay on the State itself: an extension method's tear-off is a new
// closure every time, so removeListener would miss it and
// MarkdownFileLinks and the tool rows would see a new callback every build.
part 'chat/timeline_sheet.dart';
part 'chat/transcript_find.dart';
part 'chat/command_launcher.dart';
part 'chat/prompt_editor.dart';
part 'chat/prompt_history.dart';
part 'chat/prompt_stash.dart';
part 'chat/composer.dart';
part 'chat/message_view.dart';
part 'chat/session_sheets.dart';
part 'chat/attention_card.dart';
part 'chat/approvals_sheet.dart';
part 'chat/read_aloud.dart';
part 'chat/voice_conversation.dart';
part 'chat/nudge_slot.dart';
part 'chat/empty_chat.dart';
part 'chat/chat_states.dart';
part 'chat/watching.dart';
part 'chat/team_conversation_view.dart';
part 'chat/team_watch_live.dart';
part 'chat/chat_drafts.dart';
part 'chat/chat_running_work.dart';
part 'chat/chat_stream.dart';
part 'chat/chat_history.dart';
part 'chat/chat_queue.dart';
part 'chat/chat_send.dart';
part 'chat/chat_voice.dart';
part 'chat/chat_attachments.dart';
part 'chat/chat_files.dart';
part 'chat/chat_session_actions.dart';
part 'chat/chat_scroll.dart';
part 'chat/chat_start.dart';
part 'chat/chat_message_actions.dart';
part 'chat/chat_notices.dart';
part 'chat/chat_requests.dart';
part 'chat/chat_commands.dart';
part 'chat/chat_command_actions.dart';
part 'chat/chat_session_menu.dart';
part 'chat/chat_top_bar.dart';
part 'chat/chat_transcript.dart';
part 'chat/chat_composer_region.dart';
part 'chat/chat_page.dart';
part 'chat/chat_status_line.dart';
part 'chat/chat_body.dart';
part 'chat/transcript_turns.dart';
part 'chat/transcript_rows.dart';
part 'chat/pending_sends_strip.dart';
part 'chat/team_conversation_parts.dart';
part 'chat/team_agent_conversation.dart';
part 'chat/team_conversation_actions.dart';
part 'chat/composer_tools.dart';

AppLocalizations _chatL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

// =====================================================================
// Chat screen
// =====================================================================

class ChatScreen extends StatefulWidget {
  final String sessionID;
  final VoiceComposerController? voiceController;
  final String initialText;
  final List<PromptAttachment> initialAttachments;
  final bool discardIfUntouched;

  /// Opens with the keyboard up. Only the conversation first run lands in
  /// asks for this; everywhere else the person chooses when to type.
  final bool focusComposer;

  /// An enclosing experience can provide its own navigation and task guidance.
  /// Defaults preserve the ordinary standalone chat presentation.
  final bool showAppBar;
  final Widget? emptyState;

  /// The page embedding this chat without its bar (the demo) has the
  /// keyboard up. That page's frame takes the keyboard's inset, so the chat
  /// cannot see it: the host says so, and the chat's own header action
  /// gives its room to the conversation and what waits on the person.
  final bool hostKeyboardUp;

  /// Overrides the app-wide review handoff store; tests inject their own so
  /// staged references do not leak between cases.
  final ReviewHandoffStore? handoffStore;

  /// Watching mode: the page shows a session someone else drives (an AI
  /// Team worker's) read-only, with [ChatWatch.onMessage] in place of the
  /// composer. Null is the ordinary chat.
  final ChatWatch? watch;

  /// P4.2a: the request (permission, question or form) an Inbox row or a
  /// notification opened this chat for. Its card leads the requests above
  /// the composer and is washed once when it appears (KitArrival, under a
  /// [KitArrivalScope] named by `chatRequestArrivalId`).
  final String? landOnRequestID;

  /// P4.2a: opened for a failed run: once the history is in, the transcript
  /// scrolls to the newest failed turn and marks it.
  final bool landOnFailure;

  /// P10.2: a Work row's conversation-menu pick ("Changes", "Fork", …) that
  /// needs the open conversation; run once, after the first history.
  final SessionMenuAction? menuAction;

  const ChatScreen({
    super.key,
    required this.sessionID,
    this.voiceController,
    this.initialText = '',
    this.initialAttachments = const [],
    this.discardIfUntouched = false,
    this.focusComposer = false,
    this.showAppBar = true,
    this.emptyState,
    this.hostKeyboardUp = false,
    this.handoffStore,
    this.watch,
    this.landOnRequestID,
    this.landOnFailure = false,
    this.menuAction,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen>
    with
        WidgetsBindingObserver,
        AppShortcutSurface,
        _ChatDraftFields,
        _ChatPromptHistoryFields,
        _ChatPromptStashFields,
        _ChatRunningWorkFields,
        _ChatStreamFields,
        _ChatHistoryFields,
        _ChatSendFields,
        _ChatVoiceFields,
        _ChatReadAloudFields,
        _ChatNudgeFields,
        _ChatWatchingFields,
        _ChatAttachmentFields,
        _ChatFileFields,
        _ChatSessionActionFields,
        _ChatFindFields,
        _ChatScrollFields,
        _ChatStartFields,
        _ChatNoticeFields,
        _ChatRequestFields,
        _ChatCommandFields,
        _ChatSessionMenuFields,
        _ChatTranscriptFields {
  late final ConnectionController _conn;
  late final StreamSubscription<EventEnvelope> _sub;
  List<MessageWithParts> _messages = [];
  bool _loading = true;
  Object? _error;
  final _composer = TextEditingController();
  final _focus = FocusNode();
  final _messageScroll = ItemScrollController();
  final _messagePositions = ItemPositionsListener.create();
  final _historyChanges = ValueNotifier<int>(0);
  // Performance report: when this chat opened, and whether its first
  // transcript frame and first fresh history merge have been timed.
  int _openedMicros = 0;
  bool _firstTranscriptRecorded = false;

  /// Session-scoped expansion state for tool cards, tool groups, and
  /// reasoning blocks, so list recycling does not collapse them.
  late final _ExpansionStore _transcriptExpansion = _ExpansionStore(
    onOpenChanged: () {
      // Rebuild the top bar's "Collapse all steps" when the first step opens
      // or the last one closes (never during a build).
      scheduleMicrotask(() {
        if (mounted) _setChatState(() {});
      });
    },
  );

  /// A context under the composer layer (set as the conversation builds):
  /// the clearance an Undo bar reads there includes the composer.
  BuildContext? _undoBodyContext;

  /// Where this page's Undo bars are shown from: under the composer layer
  /// when it is built, so the bar floats above the composer (phone and
  /// wide), else the page itself.
  BuildContext get _undoHost {
    final body = _undoBodyContext;
    return body != null && body.mounted ? body : context;
  }

  late final List<PromptAttachment> _attachments = DraftAttachmentList(
    _scheduleDraftSave,
  );
  void _updateSpeech(VoidCallback change) => setState(change);

  /// setState for the library's extensions (a protected member).
  void _setChatState(VoidCallback change) => setState(change);
  void _nudgesChanged() {
    if (mounted) setState(() {});
  }

  // UX-103 review handoff (start) — Files, Changes, and Review stage
  // structured references here; the composer renders them as chips and
  // `_applyStagedReferences` folds them into the prompt text on send.
  late final ReviewHandoffSession _handoff = ReviewHandoffSession(
    store: _conn.isIsolated
        ? ReviewHandoffStore()
        : widget.handoffStore ?? ReviewHandoffStore.instance,
    sessionID: widget.sessionID,
  );
  // UX-103 review handoff (end).

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _openedMicros = PerfTrace.nowMicros;
    _composer.text = widget.initialText;
    _conn = _readConn();
    if (!_conn.isIsolated) {
      _attachments.addAll(widget.initialAttachments);
      _conn.promptPhotos.addListener(_onPhotosChanged);
    }
    _draftProfileID = _conn.profile?.id ?? _conn.store.activeId ?? '';
    _draftLocation = _conn.locationRevision;
    _draftDirectory = _conn.directory;
    _draftWorkspace = _conn.workspace;
    _offlineFlushRevision = _conn.offlineFlushRevision;
    // A watched session is someone else's: no draft of the person's.
    if (!_conn.isIsolated && !_watching && widget.initialText.isEmpty) {
      final draft = _conn.sessionDraft(widget.sessionID);
      if (draft != null) {
        _composer.text = draft;
        _composer.selection = TextSelection.collapsed(offset: draft.length);
      }
    }
    _lastDraftText = _composer.text;
    _lastDraftAttachments = List.of(_attachments);
    _draftTrackingEnabled = !_conn.isIsolated && !_watching;
    if (!_conn.isIsolated &&
        !_watching &&
        widget.initialText.isEmpty &&
        widget.initialAttachments.isEmpty &&
        (_conn.savedSessionDraft(widget.sessionID)?.attachments.isNotEmpty ??
            false)) {
      _draftRecoveryFuture = _recoverDraftAttachments();
    }
    _composer.addListener(_scheduleDraftSave);
    _focus.onKeyEvent = (_, event) => _navigatePromptHistory(event);
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _conn.addListener(_onConnectionChanged);
    _conn.profileDataChanges.addListener(_readAloudScopeChanged);
    if (!_conn.sessionsById.containsKey(widget.sessionID)) {
      unawaited(_conn.ensureSession(widget.sessionID));
    }
    _syncRetryTicker();
    if (!_conn.isIsolated) {
      _handoff.store.addListener(_onHandoffChanged); // UX-103 review handoff
    }
    if (widget.focusComposer) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _focus.requestFocus();
      });
    }
    _load();
    // Watching reads the transcript only: nothing to send, run or offer.
    if (_conn.capabilities.serverCatalog && !_watching) {
      unawaited(_loadServerCommands());
    }
    if (!_watching) {
      unawaited(_loadBackgroundSupport());
      unawaited(_loadRunningShells());
    }
    _sub = _conn.events.listen(_onEvent);
    _notifyOffer = FirstReplyNotifyOffer(_conn)
      ..addListener(_notifyOfferChanged);
    _wasBusy = _conn.busySessions.contains(widget.sessionID);
    if (_watching) {
      _startWatchPolling();
    } else {
      _startNudges();
    }
    final injectedVoice = widget.voiceController;
    if (!_conn.isIsolated && injectedVoice != null) {
      _voice = injectedVoice;
      _voiceFuture = Future.value(injectedVoice);
    }
    if (!_conn.isIsolated &&
        (widget.initialText.isNotEmpty ||
            widget.initialAttachments.isNotEmpty)) {
      _draftSaveTimer = Timer(const Duration(milliseconds: 600), _persistDraft);
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      if (_voiceSettingsOpened) {
        // Off to Android settings for the microphone; nothing is recording.
      } else if (_voiceDictating) {
        // A shade or dialog over the app (inactive) keeps dictating; leaving
        // the app stops the microphone at once and writes the rest down.
        if (state != AppLifecycleState.inactive) _pauseDictation();
      } else {
        _interruptVoiceConversation();
        unawaited(_voice?.handleLifecyclePause());
      }
      unawaited(_stopReading());
      _persistDraft();
    } else if (_voiceSettingsOpened) {
      _voiceSettingsOpened = false;
      _retryVoiceAfterSettings();
    } else if (_watching && !_loading && !_loadingOlder) {
      // Back in front: catch up at once instead of on the next tick.
      _scheduleRecentHistoryRefresh();
    }
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final route = ModalRoute.of(context);
    if ((_readAloud?.speaking == true ||
            (_voiceConversation && !_voiceOpening && !_speechSheetOpen)) &&
        !(route?.isCurrent ?? true)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !(route?.isCurrent ?? true)) {
          unawaited(_stopReading());
          if (!_voiceOpening) _interruptVoiceConversation();
        }
      });
    }
    // Another page over the chat: the microphone never records behind it.
    if (_voiceDictating && !_voiceOpening && !(route?.isCurrent ?? true)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !_voiceOpening && !(route?.isCurrent ?? true)) {
          _pauseDictation();
        }
      });
    }
  }

  @override
  void didUpdateWidget(covariant ChatScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.sessionID != widget.sessionID) _readAloudScopeChanged();
  }

  // Listeners and identity-sensitive callbacks (see the note above the part
  // list): these stay on the State.

  // Save pauses in typing too: Android may kill a process without a final
  // lifecycle callback. Selection changes alone must not trigger a write.
  void _scheduleDraftSave() {
    if (!_draftTrackingEnabled) return;
    if (_composer.text == _lastDraftText &&
        listEquals(_attachments, _lastDraftAttachments)) {
      return;
    }
    _promptContentRevision++;
    _lastDraftText = _composer.text;
    _lastDraftAttachments = List.of(_attachments);
    _draftSaveTimer?.cancel();
    _draftSaveTimer = Timer(const Duration(milliseconds: 600), _persistDraft);
  }

  void _onPhotosChanged() {
    if (mounted) setState(() {});
  }

  static String _messageText(MessageWithParts message) => message.parts
      .where((part) => part.type == 'text' && !part.synthetic)
      .map((part) => part.text)
      .where((value) => value.trim().isNotEmpty)
      .join('\n\n');

  void _notifyOfferChanged() {
    if (mounted) setState(() {});
  }

  void _onConnectionChanged() {
    if (_findOpen && _findLocation != _conn.locationRevision) _closeFind();
    if (!mounted) return;
    _readAloudScopeChanged();
    _announceCompletedFlush();
    _syncRetryTicker();
    final shouldRehydrate =
        _dataRefreshRevision != _conn.dataRefreshRevision && _conn.api != null;
    if (shouldRehydrate && _voiceReplyWatch != null) {
      _voiceReplyWatch = null;
      _voiceReplyState = _VoiceReplyState.reviewNeeded;
    }
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _noteRunFinished();
    _checkVoiceReply();
    setState(() {});
    final scopeChanged =
        _requestedHistoryScope != null &&
        _requestedHistoryScope != _historyScope;
    if (shouldRehydrate || scopeChanged) unawaited(_load(resetHistory: true));
    if (shouldRehydrate || scopeChanged) {
      unawaited(_conn.ensureSession(widget.sessionID));
    }
    if (shouldRehydrate ||
        _backgroundRepository != _conn.repository ||
        _backgroundLocationRevision != _conn.locationRevision) {
      unawaited(_loadBackgroundSupport());
      _runningShells = [];
      unawaited(_loadRunningShells());
    }
  }

  /// Ctrl+K in a session opens the session's own command launcher rather than
  /// the shell one: slash commands and subagents are the commands that matter
  /// here. Everything else falls through to the shell.
  @override
  bool onAppShortcut(Intent intent) {
    if (_conn.isIsolated) return true;
    if (intent is! OpenCommandPaletteIntent) return false;
    unawaited(_openCommandLauncher());
    return true;
  }

  // UX-103 review handoff (start).
  void _onHandoffChanged() {
    _promptContentRevision++;
    if (mounted) setState(() {});
  }

  /// One listing validates every path in the same directory, and both maps
  /// memoize futures so transcript rebuilds never re-hit the server. A
  /// confirmed file stays confirmed, but a miss only holds for
  /// [_pathLinkNegativeTtl]: agents routinely mention a path moments before
  /// creating the file, so later rebuilds must re-check.
  static const _pathLinkNegativeTtl = Duration(seconds: 20);

  Future<bool> _validatePathLink(String path) {
    if (_conn.isIsolated || !_conn.capabilities.fileBrowsing) {
      return Future.value(false);
    }
    final missedAt = _pathLinkMissAt[path];
    if (missedAt != null &&
        DateTime.now().difference(missedAt) > _pathLinkNegativeTtl) {
      _pathLinkMissAt.remove(path);
      _pathLinkChecks.remove(path);
    }
    return _pathLinkChecks.putIfAbsent(path, () => _checkPathLink(path));
  }

  Future<void> _openPathLink(String raw) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated || !_conn.capabilities.fileBrowsing) return;
    final path = stripPathLineSuffix(raw);
    final name = path.substring(path.lastIndexOf('/') + 1);
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw ProductException(strings.chatUiNotConnectedToTheServerRightNow);
      }
      final content = await api.fileContent(path);
      final binary = content.isBinary || content.encoding == 'base64';
      final bytes = binary ? content.bytes() : null;
      final data = FilePreviewData(
        name: name,
        mimeType: content.mimeType,
        bytes: bytes,
        text: binary ? null : content.content,
      );
      if (!mounted) return;
      await showFilePreviewSheet(
        context,
        data,
        onAttach: () => _attachProjectFile(path, data),
      );
    } catch (error) {
      if (!mounted) return;
      showProductError(context, error);
    }
  }

  Future<FilePreviewData> _loadToolOutputFile(ToolOutputFile file) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: strings.chatUiFilesAreUnavailableInThisPreview,
      );
    }
    final path = file.path;
    final api = await _conn.prepareActionTransport();
    if (path == null || path.isEmpty || api == null) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: strings.chatUiTheGeneratedFileIsNotAvailableFrom,
      );
    }
    final content = await api.fileContent(path);
    final binary = content.isBinary || content.encoding == 'base64';
    final bytes = binary ? content.bytes() : null;
    return FilePreviewData(
      name: file.displayName,
      mimeType: file.mimeType ?? content.mimeType,
      bytes: bytes,
      text: binary ? null : content.content,
      error: binary && bytes!.isEmpty
          ? strings.chatUiTheServerReturnedEmptyImageData
          : null,
    );
  }

  @override
  Widget build(BuildContext context) {
    return _buildPage(context);
  }

  @override
  void dispose() {
    _voiceEpoch.value++;
    _voiceEpoch.dispose();
    _conn.profileDataChanges.removeListener(_readAloudScopeChanged);
    _readAloud?.removeListener(_readAloudChanged);
    _readAloud?.dispose();
    if (!_conn.isIsolated) _conn.promptPhotos.removeListener(_onPhotosChanged);
    _stopWatchPolling();
    _draftTrackingEnabled = false;
    _persistDraft();
    _composer.removeListener(_scheduleDraftSave);
    WidgetsBinding.instance.removeObserver(this);
    _conn.removeListener(_onConnectionChanged);
    _stopNudges();
    _notifyOffer
      ..removeListener(_notifyOfferChanged)
      ..dispose();
    if (_conn.isIsolated) {
      _handoff.store.dispose();
    } else {
      _handoff.store.removeListener(_onHandoffChanged);
    } // UX-103 review handoff
    _sub.cancel();
    _streamFlushTimer?.cancel();
    _highlightTimer?.cancel();
    _startFactsFallback?.cancel();
    _startFacts.dispose();
    _findDebounce?.cancel();
    _findController.dispose();
    _findFocus.dispose();
    _findNavigationFocus.dispose();
    _composerNoteTimer?.cancel();
    _retryTicker?.cancel();
    _voiceListened?.removeListener(_onVoiceChanged);
    unawaited(_voice?.cancel());
    if (widget.voiceController == null) _voice?.dispose();
    _voiceLevel.dispose();
    _composer.dispose();
    _focus.dispose();
    _historyRefreshTimer?.cancel();
    _historyChanges.dispose();
    _backgroundSupportState.dispose();
    super.dispose();
  }
}

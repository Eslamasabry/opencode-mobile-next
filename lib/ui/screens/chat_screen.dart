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

const _maxAttachmentCount = 5;
const _maxAttachmentBytes = 10 * 1024 * 1024;
const _maxAggregateAttachmentBytes = 20 * 1024 * 1024;

AppLocalizations _chatL10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

@visibleForTesting
Future<Uint8List?> readAttachmentBytesWithinLimit(
  PlatformFile file, {
  required int maxBytes,
}) async {
  if (maxBytes < 0) {
    throw ArgumentError.value(maxBytes, 'maxBytes', 'must not be negative');
  }

  final stream = file.readAsByteStream();

  // Retain at most the allowed payload plus one byte. The extra byte detects a
  // file that grew after the picker reported its metadata without allowing an
  // unbounded read or allocation.
  final bytes = BytesBuilder();
  var byteCount = 0;
  final iterator = StreamIterator<List<int>>(stream);
  try {
    while (byteCount <= maxBytes && await iterator.moveNext()) {
      final chunk = iterator.current;
      if (chunk.isEmpty) continue;
      final remaining = maxBytes + 1 - byteCount;
      final acceptedLength = chunk.length < remaining
          ? chunk.length
          : remaining;
      if (acceptedLength == chunk.length) {
        bytes.add(chunk);
      } else {
        final acceptedBytes = Uint8List(acceptedLength)
          ..setRange(0, acceptedLength, chunk);
        bytes.add(acceptedBytes);
      }
      byteCount += acceptedLength;
      if (byteCount > maxBytes) return null;
    }
    return bytes.takeBytes();
  } finally {
    await iterator.cancel();
  }
}

/// Counts coalesced streaming-rebuild flushes. Tests use it to assert that a
/// burst of N part deltas produces a bounded number of transcript rebuilds.
@visibleForTesting
int debugChatStreamFlushes = 0;

String _fmtSessionTime(int ms, BuildContext context) {
  final date = DateTime.fromMillisecondsSinceEpoch(ms);
  final now = DateTime.now();
  final material = MaterialLocalizations.of(context);
  final time = material.formatTimeOfDay(
    TimeOfDay.fromDateTime(date),
    alwaysUse24HourFormat: MediaQuery.alwaysUse24HourFormatOf(context),
  );
  return DateUtils.isSameDay(date, now)
      ? time
      : '${material.formatShortDate(date)}, $time';
}

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

class _PendingSend {
  /// Authored before dispatch, never inferred from matching message contents.
  final String? dispatchedMessageID;
  final String localID;
  final String text;
  final List<PromptAttachment> attachments;
  final int createdAt;
  String? canonicalID;
  bool requestComplete = false;

  _PendingSend({
    this.dispatchedMessageID,
    required this.localID,
    required this.text,
    required this.attachments,
    required this.createdAt,
  });
}

bool _mentionBoundaryBefore(String value) =>
    RegExp(r'''[\s\(\[\{"']''').hasMatch(value);

bool _mentionBoundaryAfter(String value) =>
    RegExp(r'''[\s\.,!\?;:\)\}\]"']''').hasMatch(value);

({int start, int end, String query})? _activeAgentQuery(
  TextEditingValue value,
) {
  final selection = value.selection;
  if (!selection.isValid || !selection.isCollapsed) return null;
  final cursor = selection.baseOffset;
  if (cursor < 1 || cursor > value.text.length) return null;
  final at = value.text.lastIndexOf('@', cursor - 1);
  if (at < 0) return null;
  if (at > 0 && !_mentionBoundaryBefore(value.text.substring(at - 1, at))) {
    return null;
  }
  final query = value.text.substring(at + 1, cursor);
  if (query.contains(RegExp(r'\s'))) return null;
  return (start: at, end: cursor, query: query);
}

List<PromptAgentMention> _promptAgentMentions(
  String text,
  Iterable<CatalogAgent> agents,
) {
  final visible =
      agents
          .where((agent) => !agent.hidden && agent.mode == 'subagent')
          .map((agent) => agent.id)
          .where((name) => name.isNotEmpty)
          .toSet()
          .toList()
        ..sort((a, b) => b.length.compareTo(a.length));
  final mentions = <PromptAgentMention>[];
  for (final name in visible) {
    final value = '@$name';
    var offset = 0;
    while (offset < text.length) {
      final start = text.indexOf(value, offset);
      if (start < 0) break;
      final end = start + value.length;
      final validBefore =
          start == 0 ||
          _mentionBoundaryBefore(text.substring(start - 1, start));
      final validAfter =
          end == text.length ||
          _mentionBoundaryAfter(text.substring(end, end + 1));
      if (validBefore && validAfter) {
        mentions.add(
          PromptAgentMention(name: name, value: value, start: start, end: end),
        );
      }
      offset = end;
    }
  }
  mentions.sort((a, b) => a.start.compareTo(b.start));
  return mentions;
}

typedef _HistoryScope = ({
  ServerGateway? api,
  int location,
  String? profile,
  String session,
});

class _ChatScreenState extends State<ChatScreen>
    with WidgetsBindingObserver, AppShortcutSurface {
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
  bool _freshMergeRecorded = false;
  bool _awayFromLatest = false;

  /// What the earlier-messages pill last said, kept while it fades out.
  int _earlierPillCount = 0;

  /// What Send does while a turn is running, on servers that support the
  /// inbox. Steer matches the server default; the visible delivery control
  /// in the composer both shows and sets this, and the Send long-press
  /// shortcut updates it too so the label never lies (UX-P0-04).
  PromptDelivery _delivery = PromptDelivery.queue;

  /// While the reader is scrolled away from the latest message, the rendered
  /// message count is pinned so a completing turn cannot shift the visible
  /// content by one item (reversed-list index anchoring). Pending messages
  /// materialize when the reader returns to the live end.
  int? _pinnedMessageCount;

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
  final _promptHistory = PromptHistoryNavigation();
  bool _promptShelfOperationBusy = false;
  ReadAloudController? _readAloud;
  Object? _speechOwnerScope;

  /// Consent to hand reply prose to the phone's speech engine: asked once
  /// and remembered on this phone (the engine is the phone's, not a
  /// server's), not asked again in every conversation or after a restart.
  bool get _readAloudConsented =>
      _conn.store.prefs.getBool(_readAloudConsentKey) ?? false;
  bool _readAloudRequestBusy = false;
  int _readAloudRequest = 0;
  String? _readAloudVoiceID;
  ReadAloudFailure? _lastReadAloudFailure;
  void _updateSpeech(VoidCallback change) => setState(change);
  // One-time nudges (UX plan 5.8): the rules live in the watcher, the screen
  // only reports facts and renders the slot. See chat/nudge_slot.dart.
  ConversationNudgeWatcher? _nudgeWatcher;
  bool _nudgeObserveQueued = false;

  /// P6.6a: the model the app picked by itself, said once per server
  /// where it is used (the composer); null once dismissed or not to say.
  String? _modelDefaultSaid;
  bool _modelDefaultClaimed = false;

  /// setState for the library's extensions (a protected member).
  void _setChatState(VoidCallback change) => setState(change);
  void _nudgesChanged() {
    if (mounted) setState(() {});
  }

  int _promptContentRevision = 0;
  bool get _promptShelfBusy =>
      _photoBusy ||
      _promptShelfOperationBusy ||
      _restoringDraftAttachments ||
      _draftRecoveryBlocked;
  bool _draftTrackingEnabled = false;
  bool _restoringDraftAttachments = false;
  bool _draftRecoveryBlocked = false;
  bool _photoBusy = false;
  Future<void>? _draftRecoveryFuture;
  List<PromptAttachment> _lastDraftAttachments = const [];
  late final int _draftLocation;
  late final String? _draftDirectory;
  late final String? _draftWorkspace;

  // UX-103 review handoff (start) — Files, Changes, and Review stage
  // structured references here; the composer renders them as chips and
  // `_applyStagedReferences` folds them into the prompt text on send.
  late final ReviewHandoffSession _handoff = ReviewHandoffSession(
    store: _conn.isIsolated
        ? ReviewHandoffStore()
        : widget.handoffStore ?? ReviewHandoffStore.instance,
    sessionID: widget.sessionID,
  );

  List<ReviewReference> get _stagedReferences => _handoff.references;
  // UX-103 review handoff (end).

  final List<_PendingSend> _pendingSends = [];
  final Map<String, int> _messageVersions = {};
  final Map<String, int> _partVersions = {};
  final Map<String, Map<String, Part>> _deferredParts = {};
  final Map<String, MessageInfo> _deferredMessages = {};
  Timer? _historyRefreshTimer;
  bool _historyRefreshPending = false;
  final Map<String, List<({String field, String delta})>> _deferredPartDeltas =
      {};
  int _eventVersion = 0;
  int _loadGeneration = 0;
  String? _olderCursor;
  Object? _olderError;
  bool _loadingOlder = false;
  bool _resetHistoryOnLoad = true;
  bool _olderNeedsReload = false;
  final Set<String> _usedOlderCursors = {};
  _HistoryScope? _loadedHistoryScope;
  _HistoryScope? _requestedHistoryScope;
  int _dataRefreshRevision = 0;
  int _offlineFlushRevision = 0;
  bool _sending = false;
  bool _aborting = false;
  String? _activePermissionID;
  bool _questionReplying = false;
  String? _activeFormID;

  /// Whether the last connection snapshot had this session running; the
  /// busy→idle edge is the "run finished" moment.
  bool _wasBusy = false;

  /// Temporary feedback kept beside the composer without replacing its editor.
  String? _composerNote;
  Key? _composerNoteKey;
  Timer? _composerNoteTimer;
  Timer? _draftSaveTimer;
  String _lastDraftText = '';
  late final String _draftProfileID;
  int _draftWriteGeneration = 0;
  SessionDraftFailure? _draftSaveFailure;
  final _backgroundSupportState = ValueNotifier<BackgroundWorkSupport>(
    BackgroundWorkSupport.unavailable,
  );
  BackgroundWorkSupport get _backgroundSupport => _backgroundSupportState.value;
  ServerOperationsGateway? _backgroundRepository;
  int _backgroundSupportRevision = 0;
  int _backgroundLocationRevision = -1;
  bool _backgrounding = false;
  final Set<String> _backgroundRequestedParts = {};
  List<ManagedShell> _runningShells = [];
  bool _readingShells = false;
  ServerOperationsGateway? _shellReadRepository;
  int _shellReadLocation = -1;
  int _shellReadRevision = 0;

  /// Ticks once a second while this session sits in a provider-retry
  /// backoff so the banner's countdown stays live; null otherwise.
  Timer? _retryTicker;

  /// The local attachment recovery note shows once per session, not on
  /// every attachment. [_attachmentNoteActive] keeps it up while the first
  /// batch is staged; the static set remembers sessions that have seen it.
  bool _attachmentNoteActive = false;
  static final Set<String> _attachmentNoteShownSessions = {};
  Future<VoiceComposerController>? _voiceFuture;
  VoiceComposerController? _voice;
  bool _voiceOpening = false;
  bool _voiceConversation = false;
  Object? _voiceOwnerScope;
  final ValueNotifier<int> _voiceEpoch = ValueNotifier(0);

  /// The "Speak replies" opt-in of the current voice conversation. Never
  /// persisted: it is granted per conversation, after consent and voice
  /// choice, and Exit, a scope change or a lifecycle pause revoke it.
  bool _voiceSpeakReplies = false;
  bool _voiceReplyPlayback = false;
  bool _speechSheetOpen = false;

  /// Dictation (P10.3): the mic turned the composer into voice mode and
  /// what is said lands in the draft, chunk by chunk.
  bool _voiceDictating = false;

  /// The composer as dictation found it; the transcript is merged in at
  /// its selection each time a chunk is written down.
  TextEditingValue? _dictationBase;

  /// The controller whose changes drive voice mode (listened to once).
  VoiceComposerController? _voiceListened;
  VoiceComposerState? _voiceShownState;
  DateTime? _voiceListeningSince;
  final ValueNotifier<double> _voiceLevel = ValueNotifier(0);

  /// "Allow microphone in Android settings" was tapped: coming back to the
  /// app tries the microphone again instead of leaving voice mode.
  bool _voiceSettingsOpened = false;

  /// The turn sent from this conversation whose reply is still owed, or
  /// null. Only this turn's reply is ever spoken automatically.
  _VoiceReplyWatch? _voiceReplyWatch;

  /// What the conversation strip says about the last automatic reading.
  _VoiceReplyState _voiceReplyState = _VoiceReplyState.idle;
  bool _allowRoutePop = false;

  /// Re-reads a watched transcript ([ChatWatch.pollInterval]).
  Timer? _watchPoll;
  bool _leavingProvisionalSession = false;
  String? _localShareUrl;
  String? _promptError;

  /// The newest turn when it got no answer, as (its prompt's index, the
  /// index of the step that ended it, or null when no step came, and
  /// whether it ended silently: no words, no steps and no error). Worked out
  /// once per build.
  (int, int?, bool)? _unanswered;

  /// When the newest turn started running as far as this phone knows: set
  /// the moment a send is accepted here, before the server says the
  /// conversation is busy (that can take a while on a slow server), and
  /// cleared when the server says it is idle again, the send fails, or
  /// the person stops it. The server's own busy state takes over as soon as
  /// it arrives; this only covers the time before it.
  DateTime? _localTurnSince;

  /// The prompt whose reply the person stopped: its turn ended on purpose,
  /// so it never reads as "No reply came back".
  String? _stoppedPromptID;

  /// The last send failed before the server took it; its text is back in
  /// the composer. Shown on the status line until dismissed or sent again.
  Object? _sendError;
  List<CommandInfo>? _serverCommands;
  Object? _serverCommandsError;
  bool _serverCommandsLoading = false;
  Future<void>? _serverCommandsRequest;
  String? _highlightedMessageID;

  /// [ChatScreen.landOnFailure] happens once, after the first history.
  bool _landedOnFailure = false;
  Timer? _highlightTimer;
  final _findController = TextEditingController();
  final _findFocus = FocusNode();
  final _findNavigationFocus = FocusNode(skipTraversal: true);
  BuildContext? _findExcerptContext;
  final _findIndex = TranscriptSearchIndex();
  Timer? _findDebounce;
  bool _findOpen = false;
  String _findQuery = '';
  List<TranscriptMatch> _findHits = [];
  int _findCursor = 0;
  String? _findKey;
  int? _findLocation;
  bool _findAllLoading = false;

  String? get _shareUrl =>
      _conn.sessionsById[widget.sessionID]?.shareUrl ?? _localShareUrl;

  List<CatalogAgent> get _subagents {
    final agents = (_conn.catalog?.agents ?? const <CatalogAgent>[])
        .where((agent) => !agent.hidden && agent.mode == 'subagent')
        .toList();
    agents.sort((a, b) => a.id.compareTo(b.id));
    return agents;
  }

  bool get _supportsPromptAttachments => _conn.capabilities.promptAttachments;
  bool get _supportsPromptAgentMentions =>
      _conn.capabilities.promptAgentMentions;
  bool get _supportsOfflinePromptQueue => _conn.capabilities.offlinePromptQueue;
  bool get _supportsSessionCompact => _conn.capabilities.sessionCompact;

  bool _chatCommandSupported(_ChatCommand command) => switch (command.action) {
    _ChatCommandAction.shell => _conn.capabilities.terminal,
    _ChatCommandAction.note => _conn.supportsSessionNotes,
    _ChatCommandAction.sessions => _conn.capabilities.globalSessionSearch,
    _ChatCommandAction.workspaces ||
    _ChatCommandAction.move ||
    _ChatCommandAction.warp ||
    _ChatCommandAction.projectHealth => _conn.capabilities.projectManagement,
    _ChatCommandAction.files => _conn.capabilities.fileBrowsing,
    _ChatCommandAction.terminal => _conn.capabilities.terminal,
    _ChatCommandAction.diff => _conn.capabilities.sessionDiff,
    _ChatCommandAction.fork => _conn.capabilities.sessionFork,
    _ChatCommandAction.compact => _supportsSessionCompact,
    _ChatCommandAction.undo ||
    _ChatCommandAction.redo => _conn.capabilities.sessionRevert,
    _ChatCommandAction.references => _conn.capabilities.fileBrowsing,
    _ChatCommandAction.integrations ||
    _ChatCommandAction.mcpServers ||
    _ChatCommandAction.skills => _conn.capabilities.serverCatalog,
    _ChatCommandAction.model => true,
    _ => true,
  };

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

  Future<VoiceComposerController> _getVoice() {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) {
      return Future.error(StateError(strings.chatUiVoiceInputIsUnavailable));
    }
    return _voiceFuture ??= VoiceComposerController.create().then((voice) {
      if (!mounted) {
        voice.dispose();
        throw StateError(strings.chatUiVoiceInputIsUnavailable);
      }
      _voice = voice;
      return voice;
    });
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

  /// Saves the composer text as this session's draft (or clears the draft
  /// when the composer is empty). Runs on navigation away, app pause, and
  /// after sends so the persisted draft always mirrors the composer.
  Future<bool> _persistDraft() async {
    _draftSaveTimer?.cancel();
    // Conversation review is transient. Only an explicit Send publishes text;
    // lifecycle, debounce and route persistence must not save an utterance.
    if (_conn.isIsolated || _voiceConversation || _watching) return true;
    final sessionID = widget.sessionID;
    final generation = ++_draftWriteGeneration;
    final text = _promptHistory.original?.text ?? _composer.text;
    final attachments = List<PromptAttachment>.of(_attachments);
    final wasRecovering = _draftRecoveryBlocked;
    try {
      await _draftRecoveryFuture;
      if (_draftRecoveryBlocked) return false;
      if (widget.sessionID != sessionID) return false;
      await _conn.saveSessionDraft(
        sessionID,
        text,
        profileID: _draftProfileID,
        attachments: wasRecovering ? _attachments : attachments,
        attachmentDirectory: _draftDirectory,
        attachmentWorkspace: _draftWorkspace,
      );
      if (mounted &&
          generation == _draftWriteGeneration &&
          _draftSaveFailure != null) {
        setState(() => _draftSaveFailure = null);
      }
      return true;
    } catch (error) {
      if (mounted && generation == _draftWriteGeneration) {
        setState(
          () => _draftSaveFailure = error is SessionDraftWriteException
              ? error.failure
              : SessionDraftFailure.storage,
        );
      }
      return false;
    }
  }

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

  Future<void> _recoverDraftAttachments() async {
    setState(() {
      _restoringDraftAttachments = true;
      _draftRecoveryBlocked = true;
    });
    try {
      final recovered = await _conn.restoreDraftAttachments(
        widget.sessionID,
        profileID: _draftProfileID,
        directory: _draftDirectory,
        workspace: _draftWorkspace,
      );
      if (!mounted || _draftLocation != _conn.locationRevision) return;
      setState(() => _restoringDraftAttachments = false);
      if (recovered.unavailable.isNotEmpty) {
        final accept = await showKitConfirm(
          context,
          icon: AppIconography.attach,
          title: _chatL10n(context).draftAttachmentRecoveryTitle,
          body: _chatL10n(
            context,
          ).draftAttachmentRecoveryDetail(recovered.unavailable.join(', ')),
          confirmLabel: _chatL10n(context).draftUseAvailableAttachments,
          cancelLabel: _chatL10n(context).draftKeepSavedAttachments,
        );
        if (!mounted || !accept || _draftLocation != _conn.locationRevision) {
          return;
        }
      }
      setState(() {
        _attachments
          ..clear()
          ..addAll(recovered.attachments);
        _draftRecoveryBlocked = false;
        _draftSaveFailure = null;
      });
      _draftSaveTimer?.cancel();
      _draftSaveTimer = Timer(const Duration(milliseconds: 600), _persistDraft);
    } catch (_) {
      // Keep the stored snapshot intact until the user explicitly recovers it.
    } finally {
      if (mounted) {
        setState(() {
          _restoringDraftAttachments = false;
          if (_draftRecoveryBlocked) {
            _draftSaveFailure = SessionDraftFailure.attachments;
          }
        });
      }
    }
  }

  /// True once the draft is saved.
  Future<bool> _retryDraftPersistence() async {
    if (_draftRecoveryBlocked) {
      _draftRecoveryFuture = _recoverDraftAttachments();
    }
    return _persistDraft();
  }

  String _draftFailureText(SessionDraftFailure failure) => switch (failure) {
    SessionDraftFailure.attachments => _chatL10n(
      context,
    ).draftAttachmentsFailed,
    SessionDraftFailure.storage =>
      _composer.text.trim().isEmpty
          ? _chatL10n(context).draftClearFailed
          : _chatL10n(context).draftSaveFailed,
    SessionDraftFailure.full => _chatL10n(context).draftStorageFull,
    SessionDraftFailure.profileRemoved => _chatL10n(
      context,
    ).draftProfileRemoved,
  };

  List<String> get _recentPrompts => {
    for (final message in _visibleHistory.toList().reversed)
      if (message.info.role == 'user' && !message.info.id.startsWith('local-'))
        if (_messageText(message).trim() case final text when text.isNotEmpty)
          text,
    ..._savedPromptHistory,
  }.take(50).toList();

  List<String> get _savedPromptHistory {
    try {
      return _conn.sentPromptHistory;
    } catch (_) {
      return const [];
    }
  }

  Set<String> get _shellIDs => {
    for (final message in _messages)
      for (final part in message.parts)
        if (part.type == 'tool')
          if (part.toolState.metadata?['shellID'] case final String id) id,
  };

  Future<void> _loadRunningShells() async {
    if (_conn.isIsolated || !_conn.capabilities.terminal) return;
    if (_conn.status != StreamStatus.connected) return;
    final repo = _conn.repository;
    if (repo == null) return;
    final location = _conn.locationRevision;
    if (_readingShells &&
        repo == _shellReadRepository &&
        location == _shellReadLocation) {
      return;
    }
    final revision = ++_shellReadRevision;
    _shellReadRepository = repo;
    _shellReadLocation = location;
    _readingShells = true;
    try {
      final result = await repo.loadRunningShells();
      if (!mounted ||
          repo != _conn.repository ||
          location != _conn.locationRevision) {
        return;
      }
      setState(() => _runningShells = result.supported ? result.shells : []);
    } catch (_) {
      // Discovery must succeed before a shell-only entry is advertised.
    } finally {
      if (revision == _shellReadRevision) _readingShells = false;
    }
  }

  Future<void> _openRunningWork() async {
    if (!_conn.capabilities.projectManagement) return;
    final sessionID = widget.sessionID;
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    final targetID = await showRunningWorkSheet(
      context,
      controller: _conn,
      sessionID: widget.sessionID,
      shellIDs: _shellIDs,
      backgroundSupport: _backgroundSupport,
      readBackgroundSupport: () => _backgroundSupport,
      canBackground: () =>
          mounted &&
          widget.sessionID == sessionID &&
          location == _conn.locationRevision &&
          profileID == _conn.profile?.id &&
          _canBackgroundWork,
      availabilityChanges: Listenable.merge([
        _historyChanges,
        _backgroundSupportState,
      ]),
      onBackground: () async {
        if (widget.sessionID != sessionID ||
            location != _conn.locationRevision ||
            profileID != _conn.profile?.id) {
          return null;
        }
        return _backgroundRunningWork();
      },
    );
    if (!mounted ||
        widget.sessionID != sessionID ||
        location != _conn.locationRevision ||
        profileID != _conn.profile?.id) {
      return;
    }
    if (targetID != null) await _openSubagentSession(targetID);
    if (mounted) unawaited(_loadRunningShells());
  }

  Future<void> _reusePrompt() async {
    final l10n = _chatL10n(context);
    final text = await showKitSheet<String>(
      context,
      sheetKey: const Key('prompt-history-sheet'),
      title: l10n.composerReuseTitle,
      subtitle: l10n.promptHistoryIntro,
      icon: AppIconography.history,
      body: (_) => _PromptHistorySheet(prompts: _recentPrompts),
    );
    if (!mounted || text == null) return;
    final draft = _composer.text.trimRight();
    final next = draft.isEmpty ? text : '$draft\n\n$text';
    _composer.value = TextEditingValue(
      text: next,
      selection: TextSelection.collapsed(offset: next.length),
    );
    _focus.requestFocus();
  }

  KeyEventResult _navigatePromptHistory(KeyEvent event) {
    if (_conn.isIsolated) return KeyEventResult.ignored;
    final value = _composer.value;
    if (_sending ||
        _promptShelfBusy ||
        !isPromptHistoryKey(
          event,
          value,
          suggestionsOpen:
              value.text.trimLeft().startsWith('/') ||
              _activeAgentQuery(value) != null,
        )) {
      return KeyEventResult.ignored;
    }
    final next = _promptHistory.move(
      event.logicalKey == LogicalKeyboardKey.arrowUp,
      value,
      _recentPrompts,
    );
    if (next == null) return KeyEventResult.ignored;
    setState(() => _composer.value = next);
    return KeyEventResult.handled;
  }

  void _restoreHistoryDraft() {
    final original = _promptHistory.restore();
    if (original == null || !mounted) return;
    setState(() => _composer.value = original);
    _persistDraft();
    _focus.requestFocus();
  }

  Future<void> _rememberSentPrompt(String profile, String text) async {
    if (_conn.isIsolated) return;
    try {
      await _conn.rememberSentPrompt(profile, text);
    } catch (_) {
      if (mounted && _conn.promptShelfProfileID == profile) {
        _showComposerNote(_chatL10n(context).promptHistorySaveFailed);
      }
    }
  }

  StashedPrompt _snapshotPrompt() => StashedPrompt(
    id: DateTime.now().microsecondsSinceEpoch.toString(),
    text: _composer.text,
    createdAt: DateTime.now().millisecondsSinceEpoch,
    directory: _conn.directory,
    workspace: _conn.workspace,
    attachments: List.of(_attachments),
    references: List.of(_stagedReferences),
  );

  bool _promptUnchanged(StashedPrompt snapshot, int location) =>
      mounted &&
      location == _conn.locationRevision &&
      snapshot.text == _composer.text &&
      listEquals(snapshot.attachments, _attachments) &&
      listEquals(snapshot.references, _stagedReferences);

  Future<void> _stashCurrentPrompt() async {
    if (_sending || _promptShelfBusy || !_conn.canUsePromptShelf) return;
    final snapshot = _snapshotPrompt();
    if (snapshot.isEmpty) return;
    final location = _conn.locationRevision;
    final session = widget.sessionID;
    final profile = _conn.promptShelfProfileID;
    final revision = _promptContentRevision;
    final route = ModalRoute.of(context);
    setState(() => _promptShelfOperationBusy = true);
    try {
      if (_conn.promptStash.length >= PromptShelfStore.capacity) {
        _showComposerNote(_chatL10n(context).promptStashFull);
        return;
      }
      await _conn.savePromptStash(snapshot, locationRevision: location);
      if (!_promptUnchanged(snapshot, location) ||
          widget.sessionID != session ||
          profile != _conn.promptShelfProfileID ||
          !_conn.canUsePromptShelf ||
          revision != _promptContentRevision ||
          !(route?.isCurrent ?? true)) {
        return;
      }
      setState(() {
        _composer.clear();
        _attachments.clear();
        _handoff.store.clear(widget.sessionID);
      });
      _restoreHistoryDraft();
      final clearedRevision = _promptContentRevision;
      final persisted = await _persistDraft();
      if (mounted &&
          widget.sessionID == session &&
          profile == _conn.promptShelfProfileID &&
          location == _conn.locationRevision &&
          _conn.canUsePromptShelf &&
          clearedRevision == _promptContentRevision &&
          (route?.isCurrent ?? true)) {
        _showComposerNote(
          persisted
              ? _chatL10n(context).promptStashed
              : _chatL10n(context).promptStashedDraftPending,
        );
      }
    } catch (_) {
      if (mounted) _showActionError(_chatL10n(context).promptStashSaveFailed);
    } finally {
      if (mounted) setState(() => _promptShelfOperationBusy = false);
    }
  }

  Future<void> _openPromptStash() async {
    if (_sending || _promptShelfBusy || !_conn.canUsePromptShelf) return;
    final location = _conn.locationRevision;
    final profile = _conn.promptShelfProfileID;
    final session = widget.sessionID;
    final route = ModalRoute.of(context);
    var invalidated = false;
    void checkScope() {
      if (!_conn.canUsePromptShelf ||
          profile != _conn.promptShelfProfileID ||
          location != _conn.locationRevision) {
        invalidated = true;
      }
    }

    bool currentScope() =>
        mounted &&
        !invalidated &&
        widget.sessionID == session &&
        _conn.canUsePromptShelf &&
        profile == _conn.promptShelfProfileID &&
        location == _conn.locationRevision &&
        (route?.isCurrent ?? true);
    final current = _snapshotPrompt();
    final revision = _promptContentRevision;
    bool unchanged() =>
        currentScope() &&
        revision == _promptContentRevision &&
        _promptUnchanged(current, location);
    _conn.addListener(checkScope);
    _conn.profileDataChanges.addListener(checkScope);
    setState(() => _promptShelfOperationBusy = true);
    // Not disposed here: the sheet's body can still report to it while the
    // sheet slides away; nothing holds it after that.
    final loading = ValueNotifier<bool>(true);
    try {
      final l10n = _chatL10n(context);
      final selected = await showKitSheet<StashedPrompt>(
        context,
        sheetKey: const Key('prompt-stash-sheet'),
        title: l10n.promptStashTitle,
        subtitle: l10n.promptStashIntro,
        icon: AppIconography.bookmarks,
        loading: loading,
        body: (_) => _PromptStashSheet(
          controller: _conn,
          location: location,
          profile: profile,
          loading: loading,
        ),
      );
      if (!mounted || selected == null || !unchanged()) {
        return;
      }
      if (selected.locationBound &&
          (selected.directory != _conn.directory ||
              selected.workspace != _conn.workspace)) {
        _showActionError(
          _chatL10n(context).promptStashLocation(
            selected.directory ?? _chatL10n(context).promptDefaultLocation,
          ),
        );
        return;
      }
      final recovered = await _conn.restorePromptStashAttachments(
        selected.id,
        locationRevision: location,
      );
      if (!mounted || !unchanged()) return;
      // Restoring acts at once, with Undo (P3.2): no question first. The
      // draft it replaces is what Undo brings back; it is also kept in Saved
      // prompts until then, so closing the app mid-way loses nothing. While
      // arrow keys browse sent prompts, the draft is the one put aside.
      final historyOriginal = _promptHistory.original;
      final previousValue = historyOriginal ?? _composer.value;
      final previousAttachments = List<PromptAttachment>.of(_attachments);
      final previousReferences = List<ReviewReference>.of(_stagedReferences);
      final previous = StashedPrompt(
        id: current.id,
        text: previousValue.text,
        createdAt: current.createdAt,
        directory: current.directory,
        workspace: current.workspace,
        attachments: previousAttachments,
        references: previousReferences,
      );
      String? keptID;
      if (!previous.isEmpty) {
        if (_conn.promptStash.length >= PromptShelfStore.capacity) {
          _showComposerNote(_chatL10n(context).promptStashFull);
          return;
        }
        await _conn.savePromptStash(previous, locationRevision: location);
        keptID = previous.id;
      }
      if (!mounted || !unchanged()) return;
      if (historyOriginal != null) _promptHistory.restore();
      void stageAll(Iterable<ReviewReference> references, String prefix) {
        for (final reference in references) {
          _handoff.stage(
            ReviewReference(
              id: _handoff.nextID(prefix),
              kind: reference.kind,
              path: reference.path,
              scope: reference.scope,
              lineLabel: reference.lineLabel,
              snippet: reference.snippet,
              comment: reference.comment,
              added: reference.added,
              removed: reference.removed,
              status: reference.status,
            ),
          );
        }
      }

      setState(() {
        _composer.value = TextEditingValue(
          text: selected.text,
          selection: TextSelection.collapsed(offset: selected.text.length),
        );
        _attachments.clear();
        _attachments.addAll(recovered.attachments);
        _handoff.store.clear(session);
        stageAll(selected.references, 'stash-${selected.id}');
      });
      final restored = _snapshotPrompt();
      final restoredRevision = _promptContentRevision;
      await _persistDraft();
      if (!mounted ||
          !currentScope() ||
          restoredRevision != _promptContentRevision ||
          !_promptUnchanged(restored, location)) {
        return;
      }
      _focus.requestFocus();
      final unavailable = recovered.unavailable;
      showKitUndo(
        _undoHost,
        key: const Key('prompt-restored-undo'),
        message: unavailable.isEmpty
            ? l10n.promptRestored
            : l10n.promptRestoredWithout(unavailable.join(', ')),
        onUndo: () async {
          // Only over the restored prompt itself: newer typing wins.
          if (!currentScope() || !_promptUnchanged(restored, location)) {
            throw StateError('The draft changed after the restore');
          }
          setState(() {
            _composer.value = previousValue;
            _attachments
              ..clear()
              ..addAll(previousAttachments);
            _handoff.store.clear(session);
            stageAll(previousReferences, 'draft');
          });
          if (!await _persistDraft()) {
            throw StateError('The draft could not be saved');
          }
          // The draft is back in the composer; its safety copy may go. If
          // that fails, a spare copy in Saved prompts is harmless.
          if (keptID != null) {
            try {
              await _conn.removePromptStash(keptID, locationRevision: location);
            } catch (_) {}
          }
        },
      );
    } catch (_) {
      if (mounted && currentScope()) {
        _showActionError(_chatL10n(context).promptStashRestoreFailed);
      }
    } finally {
      _conn.removeListener(checkScope);
      _conn.profileDataChanges.removeListener(checkScope);
      if (mounted) setState(() => _promptShelfOperationBusy = false);
    }
  }

  void _clearDraftText() {
    if (_promptShelfBusy) return;
    final previous = _composer.value;
    _composer.clear();
    _persistDraft();
    showKitUndo(
      _undoHost,
      message: _chatL10n(context).composerDraftCleared,
      key: const Key('composer-cleared-undo'),
      onUndo: () {
        if (!mounted) return;
        // Never overwrite text entered since Clear. Keep both drafts.
        final current = _composer.text;
        _composer.value = current.isEmpty
            ? previous
            : TextEditingValue(
                text: '${previous.text}\n\n$current',
                selection: TextSelection.collapsed(
                  offset: previous.text.length + 2 + current.length,
                ),
              );
        _persistDraft();
        _focus.requestFocus();
      },
    );
    _focus.requestFocus();
  }

  Future<void> _loadBackgroundSupport() async {
    if (_conn.isIsolated || !_conn.capabilities.projectManagement) return;
    final repository = _conn.repository;
    _backgroundRepository = repository;
    _backgroundLocationRevision = _conn.locationRevision;
    final location = _conn.locationRevision;
    final revision = ++_backgroundSupportRevision;
    _backgroundSupportState.value = BackgroundWorkSupport.unavailable;
    _backgroundRequestedParts.clear();
    if (repository == null) return;
    try {
      final support = await repository.loadBackgroundWorkSupport().timeout(
        const Duration(seconds: 8),
      );
      if (!mounted ||
          revision != _backgroundSupportRevision ||
          location != _conn.locationRevision ||
          repository != _conn.repository) {
        return;
      }
      setState(() => _backgroundSupportState.value = support);
    } catch (_) {
      // Unknown/older v1 servers must not advertise an experimental action.
      // Retry capability discovery after the next reconnect, not every event.
    }
  }

  String _backgroundPartKey(Part part) =>
      '${part.messageID}/${part.id ?? part.callID}';

  bool get _canBackgroundWork =>
      !_conn.isIsolated &&
      !_backgrounding &&
      _backgroundLocationRevision == _conn.locationRevision &&
      _conn.status == StreamStatus.connected &&
      _conn.busySessions.contains(widget.sessionID) &&
      foregroundBackgroundableParts(_messages, _backgroundSupport).any(
        (part) => !_backgroundRequestedParts.contains(_backgroundPartKey(part)),
      );

  Future<BackgroundWorkResult?> _backgroundRunningWork() async {
    if (!_canBackgroundWork) return null;
    final repository = _conn.repository;
    if (repository == null) return null;
    final connection = _conn.connectionRevision;
    final location = _conn.locationRevision;
    final parts = foregroundBackgroundableParts(
      _messages,
      _backgroundSupport,
    ).map(_backgroundPartKey).toSet();
    setState(() => _backgrounding = true);
    try {
      final result = await repository.backgroundSession(widget.sessionID);
      if (!mounted ||
          repository != _conn.repository ||
          connection != _conn.connectionRevision ||
          location != _conn.locationRevision) {
        return null;
      }
      if (result != BackgroundWorkResult.unchanged) {
        _backgroundRequestedParts.addAll(parts);
      }
      // Acknowledgement is not a job record. Reconcile the transcript and
      // family status, including the v2 204/idle race, before reporting state.
      await Future.wait([
        _load(),
        _conn.refreshSessions(),
        _loadRunningShells(),
      ]);
      if (!mounted) return null;
      if (result == BackgroundWorkResult.unchanged) {
        _showComposerNote(_chatL10n(context).backgroundWorkNoop);
      } else if (result == BackgroundWorkResult.promoted) {
        _showComposerNote(_chatL10n(context).backgroundWorkPromoted);
      } else {
        _showComposerNote(_chatL10n(context).workBackgroundRequested);
      }
      return result;
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _backgrounding = false);
    }
    return null;
  }

  ConnectionController _readConn() {
    final ctx = context;
    final container = ProviderScope.containerOf(ctx, listen: false);
    return container.read(connProvider);
  }

  void _onEvent(EventEnvelope env) {
    _observeVoiceReplyStatus(env);
    if (!mounted) return;
    // Inbox delivery creates a canonical server message without a message event.
    // Refresh every delivery; the pending inbox item may already be removed.
    if ((env.type == 'session.skill.changed' ||
            env.type == 'session.inbox.delivered') &&
        env.properties['sessionID'] == widget.sessionID) {
      _scheduleRecentHistoryRefresh();
    }
    if ((env.type == 'session.compacted' ||
            env.type == 'session.history.reset') &&
        env.properties['sessionID'] == widget.sessionID) {
      if (env.type == 'session.history.reset' &&
          _conn.sessionsById[widget.sessionID]?.stagedRevert == null) {
        final removedFrom = env.properties['removedFrom'] as String?;
        // A cleared boundary may mean commit, clear, or an ambiguous response.
        // Never reveal the cached staged tail before authoritative hydration.
        _messages.removeWhere(
          (message) =>
              removedFrom == null ||
              message.info.id.compareTo(removedFrom) >= 0,
        );
        _deferredMessages.clear();
        _deferredParts.clear();
        _deferredPartDeltas.clear();
      }
      unawaited(_load(resetHistory: true));
    }
    if (env.type.startsWith('shell.')) {
      unawaited(_loadRunningShells());
    }
    if (env.type == 'session.shell.changed' &&
        env.properties['sessionID'] == widget.sessionID) {
      unawaited(_load());
      unawaited(_loadRunningShells());
    }
    switch (env.type) {
      case 'message.part.updated':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final partJson = env.properties['part'];
        if (partJson is Map<String, dynamic>) {
          final p = Part.fromJson(partJson);
          final mid = p.messageID;
          if (mid != null) {
            setState(() {
              _partVersions[_partKey(mid, p.id ?? p.callID ?? '')] =
                  ++_eventVersion;
              _upsertPart(mid, p);
            });
          }
        }
        break;
      case 'message.part.delta':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        final partID = env.properties['partID']?.toString();
        final field = env.properties['field']?.toString();
        final delta = env.properties['delta']?.toString();
        if (messageID != null &&
            messageID.isNotEmpty &&
            partID != null &&
            partID.isNotEmpty &&
            field != null &&
            delta != null &&
            _isSupportedDeltaField(field)) {
          // Deltas mutate the model synchronously (versioning and deferred
          // bookkeeping must stay ordered against hydration), but the
          // rebuild is coalesced: one setState per burst, then at most one
          // per ~50ms while the stream keeps flowing.
          _partVersions[_partKey(messageID, partID)] = ++_eventVersion;
          if (!_applyPartDelta(messageID, partID, field, delta)) {
            _deferredPartDeltas
                .putIfAbsent(_partKey(messageID, partID), () => [])
                .add((field: field, delta: delta));
          }
          _scheduleStreamFlush();
        }
        break;
      case 'message.part.removed':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        final partID = env.properties['partID']?.toString();
        if (messageID != null &&
            messageID.isNotEmpty &&
            partID != null &&
            partID.isNotEmpty) {
          setState(() {
            final key = _partKey(messageID, partID);
            _partVersions[key] = ++_eventVersion;
            _deferredPartDeltas.remove(key);
            _deferredParts[messageID]?.remove(partID);
            final message = _messageByID(messageID);
            message?.parts.removeWhere(
              (part) => part.id == partID || part.callID == partID,
            );
          });
        }
        break;
      case 'message.removed':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final messageID = env.properties['messageID']?.toString();
        if (messageID != null && messageID.isNotEmpty) {
          setState(() {
            _messageVersions[messageID] = ++_eventVersion;
            _resetHistoryOnLoad = true;
            _olderNeedsReload = true;
            _messages.removeWhere((message) => message.info.id == messageID);
            _deferredParts.remove(messageID);
            _deferredMessages.remove(messageID);
            _deferredPartDeltas.removeWhere(
              (key, _) => key.startsWith('$messageID\u0000'),
            );
            _pendingSends.removeWhere(
              (pending) =>
                  pending.localID == messageID ||
                  pending.canonicalID == messageID,
            );
          });
        }
        break;
      case 'message.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          if (msg.sessionID != widget.sessionID) break;
          final watch = _voiceReplyWatch;
          if (watch != null &&
              msg.role == 'user' &&
              !watch.existingMessageIDs.contains(msg.id)) {
            watch.liveUserIDs.add(msg.id);
          }
          setState(() {
            if (msg.role == 'assistant' && msg.errorText != null) {
              _promptError = msg.errorText;
              _recoverFromPromptError(msg.errorText);
            } else if (msg.role == 'assistant' &&
                _promptError != null &&
                !_messages.any((known) => known.info.id == msg.id)) {
              // A new step after the error: the turn moved on (the server
              // retried, or the agent carried on). A banner still saying
              // something is wrong would now be false.
              _promptError = null;
            }
            _messageVersions[msg.id] = ++_eventVersion;
            if (!_reconcilePendingMessage(
              msg,
              canonicalParts: _deferredParts[msg.id]?.values.toList(),
            )) {
              final idx = _messages.indexWhere((m) => m.info.id == msg.id);
              if (idx >= 0) {
                _messages[idx] = MessageWithParts(
                  info: msg,
                  parts: _messages[idx].parts,
                );
              } else {
                final knownTimes = _messages
                    .where((m) => !m.info.id.startsWith('local-'))
                    .map((m) => m.info.time?.created)
                    .whereType<int>();
                final created = msg.time?.created;
                if (_olderCursor != null &&
                    (created == null ||
                        knownTimes.isEmpty ||
                        created <= knownTimes.last)) {
                  _deferredMessages[msg.id] = msg;
                  // A late edit/completion is not a new row. If it might be in
                  // the recent window, ask the server to establish its order.
                  if (created == null ||
                      knownTimes.isEmpty ||
                      created >= knownTimes.first) {
                    _scheduleRecentHistoryRefresh();
                  }
                  return;
                }
                _messages.add(MessageWithParts(info: msg));
              }
            }
            final deferred = _deferredParts.remove(msg.id);
            for (final part in deferred?.values ?? const <Part>[]) {
              _upsertPart(msg.id, part);
            }
          });
        }
        break;
      case 'session.idle':
      case 'session.status':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        final raw = env.properties['status'];
        final status = env.type == 'session.idle'
            ? 'idle'
            : raw is Map
            ? raw['type']?.toString()
            : raw?.toString();
        // Idle without a busy first (a missed event, or a turn that ended
        // at once): the turn this phone started is over all the same.
        if (status == 'idle' && !_sending && _localTurnSince != null) {
          setState(() => _localTurnSince = null);
        }
        break;
      case 'session.error':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        setState(() {
          if (!_sending) _localTurnSince = null;
          _promptError = _eventErrorMessage(env.properties['error']);
        });
        _recoverFromPromptError(_promptError);
        break;
      case 'session.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic> &&
            info['id']?.toString() == widget.sessionID) {
          if (mounted) setState(() {});
        }
        break;
    }
    _historyChanges.value++;
    _checkVoiceReply();
  }

  String _partKey(String messageID, String partID) => '$messageID\u0000$partID';

  // ----- streaming delta batching (C1) -----
  //
  // Leading edge: the first delta of an idle stream flushes on the next
  // microtask, so one synchronous SSE burst costs one setState. Trailing
  // edge: each flush opens a ~50ms window; deltas landing inside it only
  // mutate the model and are flushed together when the window closes.
  static const _streamFlushInterval = Duration(milliseconds: 50);
  bool _streamFlushScheduled = false;
  bool _streamDirty = false;
  Timer? _streamFlushTimer;

  void _scheduleStreamFlush() {
    if (_streamFlushTimer != null) {
      _streamDirty = true;
      return;
    }
    if (_streamFlushScheduled) return;
    _streamFlushScheduled = true;
    scheduleMicrotask(() {
      _streamFlushScheduled = false;
      if (mounted) _flushStreamDeltas();
    });
  }

  void _flushStreamDeltas() {
    debugChatStreamFlushes++;
    _streamDirty = false;
    setState(() {});
    _historyChanges.value++;
    _streamFlushTimer?.cancel();
    _streamFlushTimer = Timer(_streamFlushInterval, () {
      _streamFlushTimer = null;
      if (_streamDirty && mounted) _flushStreamDeltas();
    });
  }

  /// The index of the message the server is working on while busy on
  /// OpenCode 1: the assistant's current message, or, before it has been
  /// created, the first user prompt. Every user message after it is queued.
  static int _queuedAfterIndex(List<MessageWithParts> messages) {
    final assistant = messages.lastIndexWhere(
      (m) => m.info.role == 'assistant',
    );
    if (assistant >= 0) {
      // The newest reply already ended: the prompt after it is the one now
      // starting (its reply is not written yet), not one waiting its turn.
      final info = messages[assistant].info;
      final ended =
          info.errorText != null ||
          (info.time?.isDone == true && info.finish != 'tool-calls');
      return ended ? -1 : assistant;
    }
    return messages.indexWhere((m) => m.info.role == 'user');
  }

  /// A "Model not found" error means the server's model list moved under
  /// the selection (typically a provider it only just loaded). Re-read the
  /// catalog so the picker and the selected model reflect what it can serve.
  void _recoverFromPromptError(String? text) {
    if (text == null) return;
    final kind = MessageErrorKind.refineFromText(
      MessageErrorKind.unknown,
      text,
    );
    if (kind != MessageErrorKind.modelNotFound) return;
    unawaited(_readConn().refreshCatalog());
  }

  String _eventErrorMessage(Object? raw) {
    if (raw is Map) {
      final data = raw['data'];
      final nested = data is Map ? data['message'] : null;
      return (raw['message'] ?? nested ?? raw['name'])?.toString() ??
          _chatL10n(context).chatUiOpenCodeCouldNotCompleteThisPrompt;
    }
    final text = raw?.toString().trim();
    return text?.isNotEmpty == true
        ? text!
        : _chatL10n(context).chatUiOpenCodeCouldNotCompleteThisPrompt;
  }

  MessageWithParts? _messageByID(String messageID) {
    for (final message in _messages) {
      if (message.info.id == messageID) return message;
    }
    return null;
  }

  bool _reconcilePendingMessage(
    MessageInfo info, {
    List<Part>? canonicalParts,
    _PendingSend? pendingSend,
  }) {
    if (info.role != 'user') return false;
    _PendingSend? pending = pendingSend;
    for (final candidate in _pendingSends) {
      if (candidate.dispatchedMessageID == info.id ||
          candidate.canonicalID == info.id) {
        pending = candidate;
        break;
      }
    }
    if (pending == null) {
      final parts = canonicalParts ?? _messageByID(info.id)?.parts ?? const [];
      final matches = _pendingSends
          .where(
            (candidate) =>
                candidate.canonicalID == null &&
                candidate.dispatchedMessageID == null &&
                _matchesPendingPrompt(parts, candidate),
          )
          .toList();
      if (matches.isNotEmpty) {
        final created = info.time?.created;
        if (created != null) {
          matches.sort(
            (a, b) => (a.createdAt - created).abs().compareTo(
              (b.createdAt - created).abs(),
            ),
          );
        }
        pending = matches.first;
      }
    }
    if (pending == null) return false;
    if (pending.dispatchedMessageID != null &&
        pending.dispatchedMessageID != info.id) {
      return false;
    }

    final localIndex = _messages.indexWhere(
      (message) => message.info.id == pending!.localID,
    );
    final canonicalIndex = _messages.indexWhere(
      (message) => message.info.id == info.id,
    );
    final parts =
        canonicalParts ??
        (canonicalIndex >= 0 && _messages[canonicalIndex].parts.isNotEmpty
            ? _messages[canonicalIndex].parts
            : localIndex >= 0
            ? _messages[localIndex].parts
            : <Part>[]);
    final replacement = MessageWithParts(info: info, parts: parts);
    if (localIndex >= 0) {
      _messages[localIndex] = replacement;
      if (canonicalIndex >= 0 && canonicalIndex != localIndex) {
        _messages.removeAt(canonicalIndex);
      }
    } else if (canonicalIndex >= 0) {
      _messages[canonicalIndex] = replacement;
    } else {
      _messages.add(replacement);
    }
    pending.canonicalID = info.id;
    _messageVersions.remove(pending.localID);
    if (pending.requestComplete) _pendingSends.remove(pending);
    return true;
  }

  void _upsertPart(String messageID, Part part) {
    final bundle = _messageByID(messageID);
    if (bundle == null) {
      // An edit or tool update may belong to unloaded history. A part alone
      // cannot establish the message's role or its place in the transcript.
      final partID = part.id ?? part.callID ?? '';
      var deferred = part;
      for (final delta
          in _deferredPartDeltas.remove(_partKey(messageID, partID)) ??
              const <({String field, String delta})>[]) {
        deferred =
            _partWithDelta(deferred, delta.field, delta.delta) ?? deferred;
      }
      _deferredParts.putIfAbsent(messageID, () => {})[partID] = deferred;
      return;
    }
    final idx = bundle.parts.indexWhere(
      (p) =>
          (part.callID != null && p.callID == part.callID) ||
          (part.id != null && p.id == part.id && p.type == part.type),
    );
    if (idx >= 0) {
      bundle.parts[idx] = part;
    } else {
      final optimisticIndex = bundle.info.role == 'user'
          ? bundle.parts.indexWhere(
              (candidate) =>
                  candidate.id == null &&
                  candidate.type == part.type &&
                  (part.type != 'file' || candidate.filename == part.filename),
            )
          : -1;
      if (optimisticIndex >= 0) {
        bundle.parts[optimisticIndex] = part;
      } else {
        bundle.parts.add(part);
      }
    }
    final key = _partKey(messageID, part.id ?? part.callID ?? '');
    final deferred = _deferredPartDeltas.remove(key);
    if (deferred != null) {
      for (final delta in deferred) {
        _applyPartDelta(
          messageID,
          part.id ?? part.callID ?? '',
          delta.field,
          delta.delta,
        );
      }
    }
    if (bundle.info.role == 'user') {
      _reconcilePendingMessage(bundle.info, canonicalParts: bundle.parts);
    }
  }

  bool _isSupportedDeltaField(String field) =>
      field == 'text' ||
      field == 'input' ||
      field == 'raw' ||
      field == 'state.input' ||
      field == 'state.raw';

  bool _applyPartDelta(
    String messageID,
    String partID,
    String field,
    String delta,
  ) {
    final bundle = _messageByID(messageID);
    if (bundle == null) {
      final part = _deferredParts[messageID]?[partID];
      if (part == null) return false;
      final updated = _partWithDelta(part, field, delta);
      if (updated == null) return false;
      _deferredParts[messageID]![partID] = updated;
      return true;
    }
    final index = bundle.parts.indexWhere(
      (part) => part.id == partID || part.callID == partID,
    );
    if (index < 0) return false;
    final part = bundle.parts[index];
    final updated = _partWithDelta(part, field, delta);
    if (updated == null) return false;
    bundle.parts[index] = updated;
    return true;
  }

  Part? _partWithDelta(Part part, String field, String delta) {
    if (field == 'text' && (part.type == 'text' || part.type == 'reasoning')) {
      return _copyPart(part, text: '${part.text}$delta');
    }
    if (part.type == 'tool' &&
        (field == 'input' ||
            field == 'raw' ||
            field == 'state.input' ||
            field == 'state.raw')) {
      final state = part.toolState;
      return _copyPart(
        part,
        toolState: ToolState(
          status: state.status,
          title: state.title,
          inputJson: '${state.inputJson ?? ''}$delta',
          output: state.output,
          metadata: state.metadata,
          outputFiles: state.outputFiles,
        ),
      );
    }
    return null;
  }

  Part _copyPart(Part part, {String? text, ToolState? toolState}) => Part(
    id: part.id,
    type: part.type,
    text: text ?? part.text,
    messageID: part.messageID,
    callID: part.callID,
    toolName: part.toolName,
    toolState: toolState ?? part.toolState,
    mime: part.mime,
    filename: part.filename,
    url: part.url,
    synthetic: part.synthetic,
  );

  _HistoryScope get _historyScope => (
    api: _conn.api,
    location: _conn.locationRevision,
    profile: _conn.profile?.id,
    session: widget.sessionID,
  );

  void _scheduleRecentHistoryRefresh() {
    _historyRefreshPending = true;
    if (_historyRefreshTimer != null) return;
    _historyRefreshTimer = Timer(const Duration(milliseconds: 150), () {
      _historyRefreshTimer = null;
      if (!mounted || _loading || _loadingOlder) return;
      _historyRefreshPending = false;
      unawaited(_load());
    });
  }

  /// Whether two error texts are the same problem: the same words, or the
  /// same recognised cause (a banner from a session event and the reply's
  /// own error often differ by a prefix or a trailing sentence).
  static bool _sameError(String? a, String b) {
    if (a == null) return false;
    if (a.trim() == b.trim()) return true;
    final cause = classifyAgentError(a);
    return cause != null && cause == classifyAgentError(b);
  }

  bool _currentHistory(int generation, _HistoryScope scope) =>
      mounted && generation == _loadGeneration && scope == _historyScope;

  ({String id, int index, double alignment})? _historyAnchor() {
    final count = _renderedMessageCount;
    final positions =
        _messagePositions.itemPositions.value
            .where(
              (position) =>
                  position.index < count &&
                  position.itemTrailingEdge > 0 &&
                  position.itemLeadingEdge < 1,
            )
            .toList()
          ..sort((a, b) => a.itemLeadingEdge.compareTo(b.itemLeadingEdge));
    if (positions.isEmpty) return null;
    final position = positions.firstWhere(
      (item) => item.itemLeadingEdge >= 0,
      orElse: () => positions.first,
    );
    return (
      id: _messages[count - 1 - position.index].info.id,
      index: position.index,
      alignment: position.itemLeadingEdge.clamp(0.0, 1.0),
    );
  }

  void _restoreHistoryAnchor(
    ({String id, int index, double alignment})? anchor,
    int generation,
  ) {
    if (anchor == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted ||
          generation != _loadGeneration ||
          !_messageScroll.isAttached) {
        return;
      }
      final chronological = _messages.indexWhere(
        (message) => message.info.id == anchor.id,
      );
      if (chronological < 0) {
        if (_renderedMessageCount > 0) _messageScroll.jumpTo(index: 0);
        return;
      }
      final index = _renderedMessageCount - 1 - chronological;
      if (index >= 0 && index != anchor.index) {
        _messageScroll.jumpTo(index: index, alignment: anchor.alignment);
      }
    });
  }

  void _retainPinnedEnd(String? lastVisibleID) {
    if (!_awayFromLatest || lastVisibleID == null) return;
    final index = _messages.indexWhere(
      (message) => message.info.id == lastVisibleID,
    );
    _pinnedMessageCount = index < 0 ? _messages.length : index + 1;
  }

  Future<void> _load({bool resetHistory = false}) async {
    final generation = ++_loadGeneration;
    final versionAtStart = _eventVersion;
    final scope = _historyScope;
    _requestedHistoryScope = scope;
    if (resetHistory || _loadedHistoryScope != scope) {
      _resetHistoryOnLoad = true;
    }
    setState(() {
      _loading = true;
      _loadingOlder = false;
      _error = null;
    });
    _historyChanges.value++;
    // Inbox events are volatile: reconcile this session's pending sends
    // from REST whenever the transcript (re)hydrates. No-op on v1.
    unawaited(_conn.refreshInbox(widget.sessionID));
    try {
      final api = scope.api;
      if (api == null) {
        // Reached synchronously from initState on an offline open.
        throw ProductException(
          earlyAppLocalizations(context).chatUiOpenCodeIsReconnecting,
        );
      }
      // The controller's newest-page read is shared with a prefetch fired
      // on the tap that opened this chat (one HTTP call for both) and saves
      // the opening excerpt for next time. A host without a saved,
      // connected server (the demo, tests) reads the gateway directly, and
      // so does watching: its poll must not rewrite the saved excerpt.
      final page =
          !_conn.isIsolated &&
              !_watching &&
              identical(api, _conn.api) &&
              _conn.canLoadSessionTail(scope.session)
          ? await _conn.loadSessionTail(scope.session)
          : await readHistoryAtStagedBoundary(
              api,
              scope.session,
              boundary:
                  _conn.sessionsById[scope.session]?.stagedRevert?.messageID,
              isCurrent: () => _currentHistory(generation, scope),
            );
      if (!_currentHistory(generation, scope)) return;
      final anchor = _historyAnchor();
      final pinnedEnd = _renderedMessageCount == 0
          ? null
          : _messages[_renderedMessageCount - 1].info.id;
      final incomingIDs = page.items.map((message) => message.info.id).toSet();
      final overlap = _messages.indexWhere(
        (message) => incomingIDs.contains(message.info.id),
      );
      final retainPrefix = !_resetHistoryOnLoad && page.hasMore && overlap >= 0;
      final prefix = retainPrefix
          ? _messages.take(overlap).toList()
          : <MessageWithParts>[];
      setState(() {
        _messages = _mergeHydratedMessages(
          page.items,
          versionAtStart,
          prefix: prefix,
        );
        if (!retainPrefix) {
          _olderCursor = page.hasMore ? page.nextCursor : null;
          _usedOlderCursors.clear();
        }
        _loadedHistoryScope = scope;
        _resetHistoryOnLoad = false;
        _olderNeedsReload = false;
        _olderError = null;
        _retainPinnedEnd(pinnedEnd);
        if (anchor != null &&
            !_messages.any((message) => message.info.id == anchor.id)) {
          _awayFromLatest = false;
          _pinnedMessageCount = null;
          _composerNote = _chatL10n(context).historyRefreshed;
        }
      });
      if (!_freshMergeRecorded) {
        _freshMergeRecorded = true;
        PerfTrace.recordSince('chat.open_to_fresh', _openedMicros);
      }
      _restoreHistoryAnchor(anchor, generation);
      _landOnFailedTurn();
      _runRouteMenuAction();
    } catch (e) {
      if (!_currentHistory(generation, scope)) return;
      setState(() => _error = e);
      if (_messages.isNotEmpty) _showComposerNote(productErrorText(e));
    } finally {
      if (mounted && generation == _loadGeneration) {
        setState(() => _loading = false);
        _historyChanges.value++;
        if (_historyRefreshPending) _scheduleRecentHistoryRefresh();
      }
    }
  }

  Future<void> _loadOlder() async {
    final cursor = _olderCursor;
    if (_loading || _loadingOlder || cursor == null) return;
    if (_olderNeedsReload || _resetHistoryOnLoad) {
      await _load(resetHistory: true);
      if (mounted) {
        setState(() => _olderError = _error);
        _historyChanges.value++;
      }
      return;
    }
    final generation = ++_loadGeneration;
    final scope = _historyScope;
    final versionAtStart = _eventVersion;
    setState(() {
      _loadingOlder = true;
      _olderError = null;
    });
    _historyChanges.value++;
    final expiredMessage = _chatL10n(context).historyCursorExpired;
    try {
      final api = scope.api;
      if (api == null) {
        throw ProductException(_chatL10n(context).chatUiOpenCodeIsReconnecting);
      }
      final page = await api.messagePage(scope.session, cursor: cursor);
      if (!_currentHistory(generation, scope)) return;
      final next = page.hasMore ? page.nextCursor : null;
      if (next != null &&
          (next == cursor || _usedOlderCursors.contains(next))) {
        _olderNeedsReload = true;
        throw ProductException(expiredMessage);
      }
      final anchor = _historyAnchor();
      final pinnedEnd = _renderedMessageCount == 0
          ? null
          : _messages[_renderedMessageCount - 1].info.id;
      setState(() {
        _messages = _mergeHydratedMessages(
          page.items,
          versionAtStart,
          preserveUnseen: true,
          reconcilePending: false,
        );
        _usedOlderCursors.add(cursor);
        _olderCursor = next;
        _retainPinnedEnd(pinnedEnd);
      });
      _restoreHistoryAnchor(anchor, generation);
    } catch (error) {
      if (!_currentHistory(generation, scope)) return;
      setState(() {
        _olderError = error;
        if (error is ApiException &&
            (error.statusCode == 400 || error.statusCode == 410)) {
          _olderNeedsReload = true;
        }
      });
    } finally {
      if (mounted && generation == _loadGeneration) {
        setState(() => _loadingOlder = false);
        _historyChanges.value++;
        if (_historyRefreshPending) _scheduleRecentHistoryRefresh();
      }
    }
  }

  Widget _olderHistoryRow() {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final error = _olderError;
    return Padding(
      key: const ValueKey('chat-older-history'),
      padding: EdgeInsets.all(tokens.space4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        spacing: tokens.space2,
        children: [
          if (error != null)
            KitText(
              productErrorText(error),
              tone: KitTextTone.danger,
              textAlign: TextAlign.center,
            ),
          KitButton.secondary(
            key: const ValueKey('chat-load-older'),
            expand: false,
            working: _loadingOlder,
            onPressed: _loading || _loadingOlder ? null : _loadOlder,
            label: _olderNeedsReload
                ? l10n.historyReload
                : error != null
                ? l10n.refreshRetry
                : l10n.historyLoadOlder,
          ),
        ],
      ),
    );
  }

  List<MessageWithParts> _mergeHydratedMessages(
    List<MessageWithParts> hydrated,
    int versionAtStart, {
    List<MessageWithParts> prefix = const [],
    bool preserveUnseen = false,
    bool reconcilePending = true,
  }) {
    for (final message in hydrated) {
      if (!reconcilePending) break;
      if (message.info.role != 'user') continue;
      for (final pending in List<_PendingSend>.from(_pendingSends)) {
        if (pending.canonicalID != null) continue;
        if (_matchesPendingPrompt(message.parts, pending)) {
          _reconcilePendingMessage(
            message.info,
            canonicalParts: message.parts,
            pendingSend: pending,
          );
          break;
        }
      }
    }

    final currentByID = {
      for (final entry in _deferredMessages.entries)
        entry.key: MessageWithParts(info: entry.value),
      for (final message in _messages) message.info.id: message,
    };
    final merged = <MessageWithParts>[];
    final hydratedIDs = <String>{};
    for (final snapshot in hydrated) {
      final messageID = snapshot.info.id;
      if (!hydratedIDs.add(messageID)) continue;
      final current = currentByID[messageID];
      _deferredMessages.remove(messageID);
      final messageChanged =
          (_messageVersions[messageID] ?? 0) > versionAtStart;
      if (messageChanged && current == null) continue;

      final currentParts = {
        ...?_deferredParts.remove(messageID),
        for (final part in current?.parts ?? const <Part>[])
          if ((part.id ?? part.callID)?.isNotEmpty == true)
            (part.id ?? part.callID)!: part,
      };
      final parts = <Part>[];
      final includedPartIDs = <String>{};
      for (final snapshotPart in snapshot.parts) {
        final partID = snapshotPart.id ?? snapshotPart.callID;
        if (partID == null || partID.isEmpty) {
          parts.add(snapshotPart);
          continue;
        }
        includedPartIDs.add(partID);
        if ((_partVersions[_partKey(messageID, partID)] ?? 0) >
            versionAtStart) {
          final newerPart = currentParts[partID];
          if (newerPart != null) {
            parts.add(newerPart);
          } else {
            Part? deferredPart = snapshotPart;
            final key = _partKey(messageID, partID);
            final deferred = _deferredPartDeltas[key];
            for (final delta
                in deferred ?? const <({String field, String delta})>[]) {
              deferredPart = _partWithDelta(
                deferredPart!,
                delta.field,
                delta.delta,
              );
              if (deferredPart == null) break;
            }
            if (deferred != null) {
              parts.add(deferredPart ?? snapshotPart);
              _deferredPartDeltas.remove(key);
            }
          }
        } else {
          parts.add(snapshotPart);
        }
      }
      for (final entry in currentParts.entries) {
        if (!includedPartIDs.contains(entry.key) &&
            (_partVersions[_partKey(messageID, entry.key)] ?? 0) >
                versionAtStart) {
          parts.add(entry.value);
        }
      }
      merged.add(
        MessageWithParts(
          info: messageChanged ? current!.info : snapshot.info,
          parts: parts,
        ),
      );
      _deferredPartDeltas.removeWhere(
        (key, _) =>
            key.startsWith('$messageID\u0000') &&
            (_partVersions[key] ?? 0) <= versionAtStart,
      );
    }

    final prefixIDs = prefix.map((message) => message.info.id).toSet();
    for (final current in _messages) {
      if (hydratedIDs.contains(current.info.id)) continue;
      final isPending = _pendingSends.any(
        (pending) =>
            pending.localID == current.info.id ||
            pending.canonicalID == current.info.id,
      );
      final hasNewMessage =
          (_messageVersions[current.info.id] ?? 0) > versionAtStart;
      final hasNewPart = current.parts.any((part) {
        final partID = part.id ?? part.callID;
        return partID != null &&
            (_partVersions[_partKey(current.info.id, partID)] ?? 0) >
                versionAtStart;
      });
      if (preserveUnseen || isPending || hasNewMessage || hasNewPart) {
        if (!prefixIDs.contains(current.info.id)) merged.add(current);
      }
    }
    return [
      ...prefix.where((message) => !hydratedIDs.contains(message.info.id)),
      ...merged,
    ];
  }

  bool _matchesPendingPrompt(List<Part> parts, _PendingSend pending) {
    final text = parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .join('\n')
        .trim();
    if (text != pending.text.trim()) return false;

    final files = parts.where((part) => part.type == 'file').toList();
    if (files.length != pending.attachments.length) return false;
    for (var i = 0; i < files.length; i++) {
      final part = files[i];
      final attachment = pending.attachments[i];
      // The name is what identifies an attachment across the round trip. A
      // server that stores none cannot contradict the one that was sent.
      final name = part.filename ?? '';
      if (name.isNotEmpty && name != attachment.filename) return false;
      // The URL and the type are deliberately not compared. The app sends a
      // `data:` URI (or a path); a server keeps the file itself and answers
      // with its own location, and may name the type differently
      // (`image/jpg`). Requiring them to match left the optimistic bubble
      // unreconciled, so the same prompt appeared again after every turn
      // whenever photos were attached.
    }
    return true;
  }

  /// Queues a drafted prompt for delivery when the server returns. Returns
  /// false (with the limits message shown) when the entry cannot be queued.
  Future<bool> _queueDraft(
    String text,
    List<PromptAttachment> attachments,
    List<PromptAgentMention> mentions, {
    SessionSelection? selection,
    String? profileID,
  }) async {
    selection ??= _conn.selectionForSession(widget.sessionID);
    profileID ??= _conn.profile?.id;
    if (profileID == null) return false;
    final now = DateTime.now();
    final bool queued;
    try {
      queued = await _conn.queuePrompt(
        QueuedPrompt(
          id: 'queued-${now.microsecondsSinceEpoch}',
          profileID: profileID,
          sessionID: widget.sessionID,
          text: text,
          attachments: attachments,
          mentions: mentions,
          modelProviderID: selection.model?.providerID,
          modelID: selection.model?.modelID,
          agent: selection.agent,
          variant: selection.variant,
          createdAt: now.millisecondsSinceEpoch,
        ),
      );
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
      return false;
    }
    if (!mounted) return queued;
    if (queued) {
      // The queue evicts on age and size. Whatever it dropped to make room
      // is said here, in the same breath as the confirmation, rather than
      // leaving the user to notice a missing draft later.
      final evicted = _conn.takeQueueEvictionNotice();
      _showComposerNote(
        evicted == null
            ? _chatL10n(context).chatUiQueuedWillSendWhenReconnected
            : _chatL10n(context).chatUiQueuedWithEviction(evicted),
        key: const Key('queued-draft-notice'),
      );
    } else {
      _showActionError(_chatL10n(context).chatUiThisDraftIsTooLargeToQueue);
    }
    return queued;
  }

  /// Removes a queued draft. False when storage refused or when the
  /// controller declined because a flush is dispatching the entry — the
  /// bubble already shows that state, so the refusal needs no notice.
  Future<bool> _removeQueuedDraft(String id) async {
    try {
      return await _conn.removeQueuedPrompt(id);
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueRemoveFailed);
      return false;
    }
  }

  /// The entry as the controller holds it now, not as the tap saw it. A
  /// reconnect can mark and dispatch a draft while a sheet is open.
  QueuedPrompt? _liveQueuedPrompt(String id) {
    for (final entry in _conn.queuedPromptsFor(widget.sessionID)) {
      if (entry.id == id) return entry;
    }
    return null;
  }

  /// Edit takes the draft out of the queue and back into the composer,
  /// ahead of what was typed since, with "Returned to your draft · Undo"
  /// (P4.3). Undo takes it back out and queues it again. Nothing is sent
  /// until the person presses Send. A draft whose send was never confirmed
  /// also says that sending it again may duplicate it.
  Future<void> _editQueuedPrompt(QueuedPrompt entry) async {
    final live = _liveQueuedPrompt(entry.id);
    if (live == null) return;
    if (!await _removeQueuedDraft(live.id)) return;
    if (!mounted) return;
    final added = [
      for (final attachment in live.attachments)
        if (!_attachments.contains(attachment)) attachment,
    ];
    setState(() => _attachments.addAll(added));
    returnWithdrawnToDraft(
      _undoHost,
      composer: _composer,
      text: live.text,
      focus: _focus,
      onUndo: () async {
        if (mounted) {
          setState(() => _attachments.removeWhere(added.contains));
        }
        try {
          await _conn.queuePrompt(live);
        } on OfflineQueueWriteException {
          if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
        }
      },
    );
    if (live.dispatched) {
      _showComposerNote(
        _chatL10n(context).queuedResendMessage,
        key: const Key('queued-edit-unconfirmed-note'),
      );
    }
  }

  Future<void> _discardQueuedPrompt(QueuedPrompt entry) async {
    if (!await _confirmDiscardQueuedPrompt(entry)) return;
    if (!mounted) return;
    var live = _liveQueuedPrompt(entry.id);
    if (live == null) return;
    // The sheet promised "not sent" but a flush dispatched the draft
    // meanwhile: ask once more with the copy that matches its real state.
    // A marker never comes off without the user's own resend, so a second
    // premise change is impossible and one re-ask is enough.
    if (live.dispatched && !entry.dispatched) {
      if (!await _confirmDiscardQueuedPrompt(live)) return;
      if (!mounted) return;
      live = _liveQueuedPrompt(entry.id);
      if (live == null) return;
    }
    await _removeQueuedDraft(live.id);
  }

  /// The discard sheet, worded for the entry's state at the moment it opens.
  Future<bool> _confirmDiscardQueuedPrompt(QueuedPrompt asked) {
    final l10n = _chatL10n(context);
    return showKitConfirm(
      context,
      kind: KitConfirmKind.discard,
      icon: AppIconography.clearAll,
      title: l10n.chatUiDiscardQueuedDraft,
      body: asked.dispatched
          ? l10n.queuedDiscardUnconfirmedMessage
          : l10n.chatUiThisDraftHasNotBeenSentTo,
      confirmLabel: l10n.chatUiDiscardDraft,
      cancelLabel: asked.dispatched
          ? l10n.queuedKeepForReview
          : l10n.chatUiKeepItQueued,
    );
  }

  /// The explicit resend for a draft whose send was never confirmed. Only
  /// the user's confirmation clears the dispatch marker; a duplicate is the
  /// risk they accept here, so the sheet names it. The controller declines
  /// silently when the entry is no longer in review by the time they
  /// confirm; the bubble shows why.
  Future<void> _resendQueuedPrompt(QueuedPrompt entry) async {
    final l10n = _chatL10n(context);
    final confirmed = await showKitConfirm(
      context,
      icon: AppIconography.send,
      title: l10n.queuedResendTitle,
      body: l10n.queuedResendMessage,
      confirmLabel: l10n.queuedResendConfirm,
      cancelLabel: l10n.queuedKeepForReview,
    );
    if (!confirmed) return;
    try {
      await _conn.resendQueuedPrompt(entry.id);
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueSaveFailed);
    }
  }

  /// Retry on a draft the server refused: nothing was delivered, so it is
  /// sent again at once, with no question.
  Future<void> _retryQueuedPrompt(QueuedPrompt entry) async {
    try {
      await _conn.retryQueuedPrompt(entry.id);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Takes a pending server send back: its text returns to the draft with
  /// "Returned to your draft · Undo", and Undo sends it again the same way.
  /// Only a send that carries files asks first, since Undo brings back the
  /// words, not the files.
  Future<void> _cancelInboxSend(Api2InboxItem item) async {
    final files = item.payload['files'];
    if (files is List && files.isNotEmpty) {
      final l10n = _chatL10n(context);
      final confirmed = await showKitConfirm(
        context,
        kind: KitConfirmKind.discard,
        icon: AppIconography.clearAll,
        title: l10n.chatUiCancelThisPendingMessage,
        body: l10n.chatUiItsTextReturnsToTheComposerAs,
        confirmLabel: l10n.chatUiCancelMessage,
        cancelLabel: l10n.chatUiKeepItPending,
      );
      if (!confirmed || !mounted) return;
    }
    String? text;
    try {
      text = await _conn.cancelInboxItem(widget.sessionID, item.id);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409) {
        _showComposerNote(_chatL10n(context).chatUiAlreadyDelivered);
        return;
      }
      _showActionError(error);
      return;
    } catch (error) {
      if (mounted) _showActionError(error);
      return;
    }
    if (!mounted || text == null || text.isEmpty) return;
    final withdrawn = text;
    final delivery = switch (item.delivery) {
      Api2Delivery.steer => PromptDelivery.steer,
      Api2Delivery.queue => PromptDelivery.queue,
      _ => null,
    };
    returnWithdrawnToDraft(
      _undoHost,
      composer: _composer,
      text: withdrawn,
      focus: _focus,
      onUndo: () => _sendWithdrawnAgain(withdrawn, delivery),
    );
  }

  /// Undo for a cancelled server send: the same words go back to the
  /// server with the same delivery. A failure reaches the Undo bar, which
  /// says so and offers Try again.
  Future<void> _sendWithdrawnAgain(
    String text,
    PromptDelivery? delivery,
  ) async {
    final reconnecting = _chatL10n(
      context,
    ).chatUiOpenCodeIsReconnectingTryAgainWhenThe;
    final api = await _conn.prepareActionTransport();
    if (api == null) throw StateError(_conn.connectionError ?? reconnecting);
    final selection = _conn.selectionForSession(widget.sessionID);
    await api.promptAsync(
      widget.sessionID,
      text: text,
      model: selection.model,
      agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
      variant: selection.variant.isEmpty ? null : selection.variant,
      delivery: _conn.busySessions.contains(widget.sessionID) ? delivery : null,
    );
  }

  /// Withdraws this conversation's steering messages that the agent has not
  /// picked up yet and returns their text, oldest first, so the message being
  /// sent can carry them. Only plain-text steers are taken: one with files
  /// stays as it is, as does anything queued for after the run (that was a
  /// deliberate choice of timing). An item the agent took in the meantime
  /// (409) is simply not ours to merge any more.
  Future<List<String>> _takeBackWaitingSteers() async {
    if (_conn.isIsolated || !_conn.supportsInbox) return const [];
    if (!_conn.busySessions.contains(widget.sessionID)) return const [];
    final waiting =
        _conn
            .inboxItemsFor(widget.sessionID)
            .where(
              (item) =>
                  item.type == 'user' &&
                  item.delivery == Api2Delivery.steer &&
                  (item.promptText ?? '').trim().isNotEmpty &&
                  (item.payload['files'] is! List ||
                      (item.payload['files'] as List).isEmpty),
            )
            .toList()
          ..sort((a, b) => (a.timeCreated ?? 0).compareTo(b.timeCreated ?? 0));
    final texts = <String>[];
    for (final item in waiting) {
      try {
        final text = await _conn.cancelInboxItem(widget.sessionID, item.id);
        if (text != null && text.trim().isNotEmpty) texts.add(text.trim());
      } catch (_) {
        // Delivered already, or the server said no: it goes out on its own.
      }
    }
    return texts;
  }

  /// Flips a pending server send between steer and queue delivery.
  Future<void> _flipInboxDelivery(Api2InboxItem item) async {
    final next = item.delivery == Api2Delivery.steer
        ? Api2Delivery.queue
        : Api2Delivery.steer;
    try {
      await _conn.setInboxDelivery(widget.sessionID, item.id, delivery: next);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409) {
        _showComposerNote(_chatL10n(context).chatUiAlreadyDelivered);
        return;
      }
      _showActionError(error);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// The delivery mode that rides on an OpenCode 2 send made while a turn
  /// runs. Off a running turn — and on v1, which has no inbox — nothing is
  /// sent, so the server default applies. While a turn runs the composer's
  /// visible delivery control decides; "Send after this reply" is the
  /// default and adding to the running turn is the choice (P6.6).
  PromptDelivery? get _activeDelivery =>
      _conn.supportsInbox && _conn.busySessions.contains(widget.sessionID)
      ? _delivery
      : null;

  /// Clears the composer and shows [pending] as the person's own bubble with
  /// the turn's live line, before the server says it is busy.
  void _showOptimisticBubble(_PendingSend pending) {
    _composer.clear();
    // On a phone the keyboard would keep three quarters of the screen from
    // the reply the person just asked for; tapping the field brings it back.
    // A desktop keeps focus for the next line.
    if (desktopInteractions) {
      _focus.requestFocus();
    } else {
      _focus.unfocus();
    }
    setState(() {
      _localTurnSince = DateTime.fromMillisecondsSinceEpoch(pending.createdAt);
      _stoppedPromptID = null;
      _promptError = null;
      _sendError = null;
      _pendingSends.add(pending);
      _messages.add(
        MessageWithParts(
          info: MessageInfo(
            id: pending.localID,
            sessionID: widget.sessionID,
            role: 'user',
            time: MsgTime(created: pending.createdAt),
          ),
          parts: [
            if (pending.text.isNotEmpty) Part(type: 'text', text: pending.text),
            for (final attachment in pending.attachments)
              Part(
                type: 'file',
                mime: attachment.mime,
                filename: attachment.filename,
                url: attachment.url,
              ),
          ],
        ),
      );
      _attachments.clear();
    });
  }

  /// Takes a bubble shown before the transport was ready back out and gives
  /// the person their text and attachments again.
  void _rollbackOptimisticBubble(_PendingSend pending) {
    setState(() {
      _localTurnSince = null;
      _pendingSends.remove(pending);
      _messages.removeWhere((message) => message.info.id == pending.localID);
      _attachments.insertAll(0, pending.attachments);
    });
    final currentText = _composer.text;
    if (pending.text.isNotEmpty && currentText.trim() != pending.text) {
      _composer.text = currentText.isEmpty
          ? pending.text
          : '${pending.text}\n$currentText';
      _composer.selection = TextSelection.collapsed(
        offset: _composer.text.length,
      );
    }
    _persistDraft();
  }

  /// [delivery] rides only on OpenCode 2 sends made while a turn runs. When
  /// it is omitted the composer's current delivery choice applies; the
  /// long-press shortcut passes an explicit steer or queue.
  Future<void> _send({PromptDelivery? delivery}) async {
    final tapMicros = PerfTrace.nowMicros;
    final strings = _chatL10n(context);
    final conversationSend = _voiceConversation;
    final voiceEpoch = _voiceEpoch.value;
    final voiceScope = _speechScopeNow;
    bool voiceSendCurrent() =>
        !conversationSend ||
        (mounted &&
            _voiceConversation &&
            voiceEpoch == _voiceEpoch.value &&
            voiceScope == _speechScopeNow &&
            (ModalRoute.of(context)?.isCurrent ?? true));
    if (_voiceConversation && !_conversationCanSend) {
      _showComposerNote(_conversationPauseCopy);
      return;
    }
    if (_voiceConversation && _composer.text.trimLeft().startsWith('/')) {
      _showComposerNote(strings.voiceConversationCommandsOnly);
      return;
    }
    delivery ??= _activeDelivery;
    await _voice?.cancel();
    if (!mounted || !voiceSendCurrent()) return;
    if (conversationSend && !_conversationCanSend) return;
    // UX-103 review handoff: the command grammar is matched against the text
    // the *user* typed, before any staged reference is folded in. Folding
    // first appended a multi-line reference block that `_typedChatCommand`
    // could never match, so a composer holding `/new` plus a staged reference
    // silently sent the command as a chat message.
    final hasStagedReferences = _handoff.references.isNotEmpty;
    if (_sending ||
        _promptShelfBusy ||
        (_composer.text.trim().isEmpty &&
            _attachments.isEmpty &&
            !hasStagedReferences)) {
      return;
    }
    // The send tick belongs to the composer's Send (KitComposer), which
    // already gave it for this tap: one send, one haptic.
    if (!_conn.isIsolated &&
        _attachments.isEmpty &&
        _composer.text.trimLeft().startsWith('/') &&
        _serverCommands == null) {
      await _loadServerCommands();
      if (!mounted) return;
    }
    final typedCommand = _conn.isIsolated
        ? null
        : _typedChatCommand(_composer.text.trim());
    if (_attachments.isEmpty && typedCommand != null) {
      // A command is not a prompt: a server command's arguments feed its own
      // template and a mobile command takes none, so references cannot ride
      // along. They stay staged for the next prompt rather than being
      // rewritten into arguments the command never asked for — and the user
      // is told, so nothing looks lost.
      if (hasStagedReferences) _noteReferencesKeptForNextPrompt();
      await _submitTypedCommand(typedCommand);
      return;
    }
    if (!_conn.isIsolated && _attachments.isEmpty) {
      final typed = _composer.text.trim();
      // "!command" runs in this conversation's shell; its output joins the
      // transcript as the shell step's card (P10.1).
      if (_shellLine.firstMatch(typed) case final shell?) {
        if (hasStagedReferences) _noteReferencesKeptForNextPrompt();
        await _submitShellLine(shell.group(1)!.trim());
        return;
      }
      // An agent that does not share its commands never gets "/compact" as
      // a plain message pretending to be a command: say so, send nothing.
      final slash = _conn.capabilities.slashCommands
          ? null
          : _slashWord.firstMatch(typed);
      if (slash != null) {
        _showComposerNote(
          strings.commandSheetAgentCommandNotSent(
            '/${slash.group(1)}',
            _agentWord(strings),
          ),
        );
        return;
      }
    }
    if (_conn.supportsStagedRevert &&
        (_conn.sessionsById[widget.sessionID]?.reverted == true ||
            _conn.sessionRevertSaving(widget.sessionID))) {
      _showComposerNote(strings.revertResolveBeforeSending);
      return;
    }
    _applyStagedReferences(); // UX-103 review handoff
    if (_composer.text.trim().isEmpty && _attachments.isEmpty) return;
    if (!_supportsPromptAttachments && _attachments.isNotEmpty) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    if (_conn.status != StreamStatus.connected) {
      // Offline compose: the draft queues instead of failing, and flushes
      // through the same send path when the connection returns.
      if (!_supportsOfflinePromptQueue) {
        final persisted = await _persistDraft();
        if (mounted) {
          _showComposerNote(
            persisted && _draftSaveFailure == null
                ? strings.codexOfflineDraftSaved
                : strings.codexReconnectBeforeSending,
          );
        }
        return;
      }
      final draftText = _composer.text.trim();
      final draftAttachments = List<PromptAttachment>.from(_attachments);
      final draftMentions = _supportsPromptAgentMentions
          ? _promptAgentMentions(draftText, _subagents)
          : const <PromptAgentMention>[];
      if (await _queueDraft(draftText, draftAttachments, draftMentions)) {
        if (!mounted) return;
        setState(() => _attachments.clear());
        _composer.clear();
        _persistDraft();
        _focus.requestFocus();
      }
      return;
    }
    setState(() => _sending = true);
    // The bubble is on screen before the transport is awaited: after a phone
    // wake `prepareActionTransport` can wait out the whole connect timeout,
    // and the person must not stare at a composer that looks unsent. Steering
    // (takes back earlier items first) and voice conversation (needs the
    // gateway for the exact message id) keep the transport-first order.
    final bubbleFirst = delivery != PromptDelivery.steer && !conversationSend;
    _PendingSend? early;
    if (bubbleFirst) {
      final now = DateTime.now();
      early = _PendingSend(
        localID:
            'local-${now.millisecondsSinceEpoch}-${now.microsecondsSinceEpoch}',
        text: _composer.text.trim(),
        attachments: List<PromptAttachment>.from(_attachments),
        createdAt: now.millisecondsSinceEpoch,
      );
      _showOptimisticBubble(early);
      PerfTrace.recordSince(
        'chat.send_to_bubble',
        tapMicros,
        attrs: const {'order': 'bubble_first'},
      );
    }
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (!voiceSendCurrent() || (conversationSend && !_conversationCanSend)) {
      setState(() => _sending = false);
      return;
    }
    if (actionApi == null) {
      if (early != null) _rollbackOptimisticBubble(early);
      setState(() => _sending = false);
      final detail = _conn.connectionError;
      _showActionError(
        detail == null || detail.isEmpty
            ? strings.chatUiOpenCodeIsReconnectingTryAgainWhenThe
            : detail,
      );
      return;
    }
    // Several steering messages in a row are one thought, typed in pieces.
    // Left as separate inbox items they reach the agent as separate
    // interruptions; taken back and sent together they are one.
    final earlier = delivery == PromptDelivery.steer
        ? await _takeBackWaitingSteers()
        : const <String>[];
    if (!mounted) return;
    final text =
        early?.text ??
        [
          ...earlier,
          _composer.text.trim(),
        ].where((piece) => piece.isNotEmpty).join('\n\n');
    if (text.isEmpty && _attachments.isEmpty && early == null) {
      setState(() => _sending = false);
      return;
    }
    final attachments =
        early?.attachments ?? List<PromptAttachment>.from(_attachments);
    final agentMentions = _supportsPromptAgentMentions
        ? _promptAgentMentions(text, _subagents)
        : const <PromptAgentMention>[];
    var selection = _conn.selectionForSession(widget.sessionID);
    final selectionProfileID = _conn.profile?.id;
    var promptStarted = false;
    final pending =
        early ??
        _PendingSend(
          dispatchedMessageID:
              conversationSend &&
                  _voiceSpeakReplies &&
                  actionApi.capabilities.clientPromptMessageID &&
                  actionApi is CorrelatedPromptGateway
              ? (actionApi as CorrelatedPromptGateway).createPromptMessageID()
              : null,
          localID:
              'local-${DateTime.now().millisecondsSinceEpoch}-${DateTime.now().microsecondsSinceEpoch}',
          text: text,
          attachments: attachments,
          createdAt: DateTime.now().millisecondsSinceEpoch,
        );
    if (early == null) {
      _showOptimisticBubble(pending);
      PerfTrace.recordSince(
        'chat.send_to_bubble',
        tapMicros,
        attrs: const {'order': 'transport_first'},
      );
    }
    _persistDraft();
    try {
      await _conn.waitForSessionSelection(
        widget.sessionID,
        expectedApi: actionApi,
      );
      if (!voiceSendCurrent() || (conversationSend && !_conversationCanSend)) {
        throw StateError(strings.chatUiVoiceConversationWasInterrupted);
      }
      selection = _conn.selectionForSession(widget.sessionID);
      promptStarted = true;
      if (conversationSend && voiceSendCurrent()) {
        setState(() => _watchVoiceReply(pending));
      }
      _conn.noteLocalTurn(widget.sessionID);
      final exactMessageID = pending.dispatchedMessageID;
      if (exactMessageID != null && actionApi is CorrelatedPromptGateway) {
        await (actionApi as CorrelatedPromptGateway).promptWithMessageID(
          widget.sessionID,
          messageID: exactMessageID,
          text: text,
          model: selection.model,
          agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
          variant: selection.variant.isEmpty ? null : selection.variant,
          attachments: attachments,
          agentMentions: agentMentions,
          delivery: delivery,
        );
      } else {
        await actionApi.promptAsync(
          widget.sessionID,
          text: text,
          model: selection.model,
          agent: selection.agent?.isNotEmpty == true ? selection.agent : null,
          variant: selection.variant.isEmpty ? null : selection.variant,
          attachments: attachments,
          agentMentions: agentMentions,
          delivery: delivery,
        );
      }
      if (!conversationSend) {
        unawaited(_rememberSentPrompt(selectionProfileID ?? '', text));
      }
      PerfTrace.recordSince('chat.send_to_ack', tapMicros);
      if (!mounted) return;
      setState(() {
        _sending = false;
        pending.requestComplete = true;
        if (pending.canonicalID != null) _pendingSends.remove(pending);
      });
      _checkVoiceReply();
      if (!conversationSend && _composer.text.isEmpty) _restoreHistoryDraft();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _sending = false;
        _localTurnSince = null;
        if (identical(_voiceReplyWatch?.pending, pending)) {
          _voiceReplyWatch = null;
          _voiceReplyState = _VoiceReplyState.reviewNeeded;
        }
        _pendingSends.remove(pending);
        _messages.removeWhere(
          (message) =>
              message.info.id == pending.localID ||
              message.info.id == pending.canonicalID,
        );
      });
      // A transport-level failure (no HTTP response) means the server became
      // unreachable mid-send: queue the draft rather than erroring.
      if (!voiceSendCurrent()) return;
      if (!conversationSend &&
          promptStarted &&
          _supportsOfflinePromptQueue &&
          e is ApiException &&
          e.statusCode == null) {
        if (await _queueDraft(
          text,
          attachments,
          agentMentions,
          selection: selection,
          profileID: selectionProfileID,
        )) {
          return;
        }
      }
      if (!mounted) return;
      if (e is ApiException && e.errorTag == 'SessionRevertPending') {
        unawaited(_conn.ensureSession(widget.sessionID));
      }
      setState(() => _attachments.insertAll(0, attachments));
      final currentText = _composer.text;
      if (text.isNotEmpty && currentText.trim() != text) {
        _composer.text = currentText.isEmpty ? text : '$text\n$currentText';
        _composer.selection = TextSelection.collapsed(
          offset: _composer.text.length,
        );
      }
      // Said on the status line, where it stays until dismissed or sent
      // again, not in a snackbar that leaves while the person reads it.
      setState(() => _sendError = e);
    }
  }

  void _insertAgentMention(CatalogAgent agent) {
    if (!_supportsPromptAgentMentions) return;
    final current = _composer.value;
    final query = _activeAgentQuery(current);
    final selection = current.selection;
    final fallback = selection.isValid
        ? selection.start.clamp(0, current.text.length)
        : current.text.length;
    final start = query?.start ?? fallback;
    final end =
        query?.end ??
        (selection.isValid
            ? selection.end.clamp(start, current.text.length)
            : start);
    final needsLeadingSpace =
        query == null &&
        start > 0 &&
        !RegExp(r'\s').hasMatch(current.text.substring(start - 1, start));
    final needsTrailingSpace =
        end == current.text.length ||
        !RegExp(r'\s').hasMatch(current.text.substring(end, end + 1));
    final replacement =
        '${needsLeadingSpace ? ' ' : ''}@${agent.id}${needsTrailingSpace ? ' ' : ''}';
    final nextText = current.text.replaceRange(start, end, replacement);
    _composer.value = TextEditingValue(
      text: nextText,
      selection: TextSelection.collapsed(offset: start + replacement.length),
    );
    _focus.requestFocus();
  }

  ({_ChatCommand command, String arguments})? _typedChatCommand(String text) {
    final match = RegExp(r'^/(\S+)(?:\s+(.*))?$').firstMatch(text);
    if (match == null) return null;
    final name = match.group(1)!.toLowerCase();
    for (final command in _chatCommands) {
      if (command.matches(name)) {
        return (command: command, arguments: match.group(2)?.trim() ?? '');
      }
    }
    return null;
  }

  static final _shellLine = RegExp(r'^!([^\s!][\s\S]*)$');
  static final _slashWord = RegExp(r'^/(\S+)');

  /// The agent's name for copy: "Codex", "Claude Code", else the server's.
  String _agentWord(AppLocalizations strings) =>
      commandSheetAgentName(_conn) ??
      _conn.profile?.name ??
      strings.commandSheetAgentFallback;

  /// A composer line that starts with "!": the rest runs as a shell
  /// command in this conversation, the same call as Run shell command. A
  /// server with no shell for the conversation says so and sends nothing.
  Future<void> _submitShellLine(String command) async {
    final strings = _chatL10n(context);
    if (!_conn.capabilities.terminal) {
      _showComposerNote(
        strings.commandSheetShellNotSent('!$command', _agentWord(strings)),
      );
      return;
    }
    final original = _composer.text;
    setState(() => _sending = true);
    try {
      await _runShellCommand(command);
      if (!mounted) return;
      _composer.clear();
      _focus.requestFocus();
    } catch (error) {
      if (!mounted) return;
      _composer.text = original;
      _composer.selection = TextSelection.collapsed(offset: original.length);
      _showActionError(error);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _submitTypedCommand(
    ({_ChatCommand command, String arguments}) typed,
  ) async {
    final command = typed.command;
    if (!command.enabled) {
      _showActionError(
        _chatL10n(context).chatUiCommandUnavailable(command.slash),
      );
      return;
    }
    if (command.serverCommand == null) {
      _composer.clear();
      await _runMobileCommand(command.action! as _ChatCommandAction);
      return;
    }
    if (_conn.supportsStagedRevert &&
        (_conn.sessionsById[widget.sessionID]?.reverted == true ||
            _conn.sessionRevertSaving(widget.sessionID))) {
      _showComposerNote(_chatL10n(context).revertResolveBeforeSending);
      return;
    }
    setState(() => _sending = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (actionApi == null) {
      setState(() => _sending = false);
      _showActionError(
        _conn.connectionError ??
            _chatL10n(context).chatUiOpenCodeIsReconnectingTryAgainShortly,
      );
      return;
    }
    final original = _composer.text;
    try {
      await _conn.waitForSessionSelection(
        widget.sessionID,
        expectedApi: actionApi,
      );
      await actionApi.slashCommand(
        widget.sessionID,
        command.serverCommand!.name,
        typed.arguments,
        model: _conn.modelForSession(widget.sessionID),
        variant: _conn.variantForSession(widget.sessionID).isEmpty
            ? null
            : _conn.variantForSession(widget.sessionID),
      );
      if (!mounted) return;
      _composer.clear();
      _focus.requestFocus();
      setState(() => _sending = false);
    } catch (error) {
      if (!mounted) return;
      if (error is ApiException && error.errorTag == 'SessionRevertPending') {
        unawaited(_conn.ensureSession(widget.sessionID));
      }
      _composer.text = original;
      _composer.selection = TextSelection.collapsed(offset: original.length);
      setState(() => _sending = false);
      _showActionError(error);
    }
  }

  /// The mic (P10.3): turns the composer into voice mode. Dictation puts
  /// what is said into the draft; in a voice conversation it is sent. The
  /// first time, P10.4's automatic setup picks and fetches the speech model
  /// and hands over a recording already listening.
  Future<void> _openVoice() async {
    if (_conn.isIsolated) return;
    // The tools sheet hides the entry point off Android; this keeps a
    // programmatic call (a shortcut, a restored intent) from starting a model
    // download for a recognizer that can never be fed.
    if (!platformCapabilities.supportsVoice) return;
    if (_voiceOpening || _sending) return;
    if (_voiceConversation && !_conversationCanSend) {
      _showComposerNote(_conversationPauseCopy);
      return;
    }
    final scope = _speechScopeNow;
    final epoch = _voiceEpoch.value;
    _voiceOwnerScope = scope;
    bool current() =>
        mounted &&
        epoch == _voiceEpoch.value &&
        scope == _speechScopeNow &&
        _conn.isProfileReadable(_conn.promptShelfProfileID) &&
        (ModalRoute.of(context)?.isCurrent ?? true);
    setState(() => _voiceOpening = true);
    try {
      await _stopReading();
      if (!current()) return;
      final voice = await _getVoice();
      if (!mounted || !current()) return;
      _listenToVoice(voice);
      if (!voice.models.isReady) {
        final ready = await showVoiceAutomaticSetupSheet(context, voice);
        if (!ready || !current()) {
          // A recording the setup started belongs to no mode now.
          if (voice.state == VoiceComposerState.listening) {
            unawaited(voice.cancel());
          }
          if (mounted && _voiceConversation && !ready) {
            _interruptVoiceConversation();
          }
          return;
        }
      }
      if (!_voiceConversation && !_voiceDictating) {
        _dictationBase = _composer.value;
        _updateSpeech(() => _voiceDictating = true);
      }
      await voice.startListening();
    } catch (error) {
      if (mounted && current()) {
        if (_voiceDictating) _leaveDictation();
        _showActionError(_chatL10n(context).voiceInputUnavailable);
      }
    } finally {
      if (mounted) setState(() => _voiceOpening = false);
    }
  }

  Future<void> _addWebSources() async {
    if (_conn.isIsolated || !_conn.capabilities.webSearch) return;
    if (_sending || _promptShelfBusy || _voiceConversation) return;
    final source = _speechScopeNow;
    final snapshot = _snapshotPrompt();
    final location = _conn.locationRevision;
    final revision = _promptContentRevision;
    final route = ModalRoute.of(context);
    final selections = await Navigator.of(context)
        .push<List<WebSourceSelection>>(
          KitPageRoute(builder: (_) => WebSourcesScreen(controller: _conn)),
        );
    if (!mounted || selections == null || selections.isEmpty) return;
    if (source != _speechScopeNow ||
        revision != _promptContentRevision ||
        !_promptUnchanged(snapshot, location) ||
        !(route?.isCurrent ?? true)) {
      _showComposerNote(_chatL10n(context).webSourcesDraftChanged);
      return;
    }
    final appendix = jsonEncode([
      for (final selection in selections)
        {
          'title': selection.title,
          'url': selection.url,
          'excerpt': selection.excerpt,
        },
    ]);
    final addition = '${_chatL10n(context).webSourcesDraftLabel}\n$appendix';
    final text = snapshot.text.isEmpty
        ? addition
        : '${snapshot.text}\n\n$addition';
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    await _persistDraft();
    if (mounted && source == _speechScopeNow) _focus.requestFocus();
  }

  Future<void> _pickAttachment() async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_promptShelfBusy) return;
    final location = _conn.locationRevision;
    setState(() => _photoBusy = true);
    try {
      final attachment = await _chooseAttachment(_attachments);
      if (attachment != null && mounted && location == _conn.locationRevision) {
        setState(() => _attachments.add(attachment));
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _photoBusy = false);
    }
  }

  void _onPhotosChanged() {
    if (mounted) setState(() {});
  }

  bool _photoMatches(PendingPromptPhoto photo) =>
      photo.profileID == _draftProfileID &&
      photo.sessionID == widget.sessionID &&
      photo.directory == _conn.directory &&
      photo.workspace == _conn.workspace &&
      _draftLocation == _conn.locationRevision;

  String _photoError(Object error) {
    final l10n = _chatL10n(context);
    if (error is PromptPhotoException) {
      return switch (error.failure) {
        PromptPhotoFailure.tooLarge => l10n.photoTooLarge,
        PromptPhotoFailure.unsupported => l10n.chatAttachmentUnsupported,
        PromptPhotoFailure.storage => l10n.photoStorageFailed,
        PromptPhotoFailure.pending => l10n.photoPendingOther,
        PromptPhotoFailure.unavailable => l10n.photoUnavailable,
      };
    }
    if (error is PlatformException &&
        error.code.toLowerCase().contains('denied')) {
      return l10n.photoPermissionDenied;
    }
    return l10n.photoUnavailable;
  }

  Future<void> _pickPhoto(ImageSource source) async {
    if (_conn.isIsolated || !_supportsPromptAttachments) {
      if (!_conn.isIsolated && !_supportsPromptAttachments) {
        _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      }
      return;
    }
    if (_promptShelfBusy || !platformCapabilities.supportsPromptPhotos) return;
    if (_attachments.length >= _maxAttachmentCount ||
        _attachments.fold<int>(
              0,
              (total, a) => total + _attachmentByteLength(a),
            ) >=
            _maxAggregateAttachmentBytes) {
      _showActionError(_chatL10n(context).photoDraftFull);
      return;
    }
    // A photo still waiting from an earlier pick joins its own
    // conversation's draft first (P3.2): no question about it. This
    // conversation's photo goes into this composer; another one's into that
    // conversation's saved draft. One that cannot move yet keeps waiting.
    if (_conn.promptPhotos.pending case final pending?) {
      if (pending.profileID == _draftProfileID &&
          pending.sessionID == widget.sessionID) {
        await _applyPendingPhoto(pending);
      } else {
        await _conn.recoverPendingPhoto();
      }
      if (!mounted) return;
      if (_conn.promptPhotos.pending != null) {
        _showActionError(_chatL10n(context).photoPendingOther);
        return;
      }
    }
    if (!mounted || !await _persistDraft() || !mounted) return;
    if (_draftLocation != _conn.locationRevision ||
        _conn.profile?.id != _draftProfileID) {
      return;
    }
    setState(() => _photoBusy = true);
    try {
      if (source == ImageSource.gallery) {
        await _pickGalleryPhotos();
        return;
      }
      final photo = await _conn.promptPhotos.pick(
        profileID: _draftProfileID,
        sessionID: widget.sessionID,
        directory: _draftDirectory,
        workspace: _draftWorkspace,
        source: source,
      );
      if (photo != null && mounted && _photoMatches(photo)) {
        await _applyPendingPhoto(photo, fromPicker: true);
      }
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) setState(() => _photoBusy = false);
    }
  }

  /// Standing facts about this conversation's run, as one line of labelled
  /// chips above the composer: that approvals are automatic, and that the
  /// running work can be sent to the background. They used to be a bar and a
  /// link of their own, repeated above the composer on every running turn.
  Widget _composerStatusStrip() {
    final approval = _conn.isIsolated
        ? null
        : _conn.autoApprovalFor(widget.sessionID);
    // A request waiting for a person has its own card, which also says when
    // an automatic reply failed; the chip steps aside until it is answered.
    final showApproval =
        approval != null &&
        approval.automatic &&
        _conn.permissionsForSession(widget.sessionID).isEmpty;
    // While the move is in flight the chip steps aside (a chip that cannot
    // act is not shown); the composer note then says how it went.
    final showBackground = _canBackgroundWork;
    // Context the server will hand the agent at its next step (a finished
    // background command, changed instructions). Nothing to do about it, so
    // it is a label, not a bubble of its own above the composer.
    final pendingContext = _conn.isIsolated
        ? 0
        : _conn
              .inboxItemsFor(widget.sessionID)
              .where((item) => item.type != 'user')
              .length;
    if (!showApproval && !showBackground && pendingContext == 0) {
      return const SizedBox.shrink();
    }
    final strings = _chatL10n(context);
    return KitComposerStatusStrip(
      stripKey: const Key('composer-status-strip'),
      chips: [
        if (pendingContext > 0)
          KitChip(
            key: const Key('pending-context-chip'),
            icon: AppIconography.sparkle,
            label: pendingContext > 1
                ? '${strings.chatStripContextPending} · $pendingContext'
                : strings.chatStripContextPending,
          ),
        if (showApproval)
          _AutoApprovalIndicator(
            key: const ValueKey('auto-approval-indicator-slot'),
            effective: approval,
            connected: _conn.isConnected,
            approved: _conn.autoApprovedFor(widget.sessionID),
            onOpen: () => unawaited(
              showSessionApprovalsSheet(
                context,
                controller: _conn,
                sessionID: widget.sessionID,
              ),
            ),
          ),
        if (showBackground)
          Semantics(
            hint: strings.backgroundWorkShortcut,
            child: KitChip.action(
              key: const Key('background-running-work'),
              icon: AppIconography.lowPriority,
              label: strings.chatStripBackground,
              onPressed: () => unawaited(_backgroundRunningWork()),
            ),
          ),
      ],
    );
  }

  /// Gallery: as many photos as the draft still has room for, in one visit.
  Future<void> _pickGalleryPhotos() async {
    final picked = await _conn.promptPhotos.pickMany(
      profileID: _draftProfileID,
      sessionID: widget.sessionID,
      directory: _draftDirectory,
      workspace: _draftWorkspace,
      limit: _maxAttachmentCount - _attachments.length,
    );
    if (!mounted || picked.isEmpty) return;
    if (_draftLocation != _conn.locationRevision ||
        _conn.profile?.id != _draftProfileID) {
      _showActionError(_chatL10n(context).photoOtherLocation);
      return;
    }
    var bytes = _attachments.fold<int>(
      0,
      (total, a) => total + _attachmentByteLength(a),
    );
    final added = <PromptAttachment>[];
    var full = false;
    for (final attachment in picked) {
      if (_attachments.any((a) => a.url == attachment.url) ||
          added.any((a) => a.url == attachment.url)) {
        continue;
      }
      final size = _attachmentByteLength(attachment);
      if (_attachments.length + added.length >= _maxAttachmentCount ||
          bytes + size > _maxAggregateAttachmentBytes) {
        full = true;
        break;
      }
      bytes += size;
      added.add(attachment);
    }
    if (added.isNotEmpty) {
      setState(() => _attachments.addAll(added));
      await _persistDraft();
    }
    if (full && mounted) _showActionError(_chatL10n(context).photoDraftFull);
  }

  Future<void> _applyPendingPhoto(
    PendingPromptPhoto photo, {
    bool fromPicker = false,
  }) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_promptShelfBusy && !fromPicker) return;
    if (!_photoMatches(photo)) {
      _showActionError(_chatL10n(context).photoOtherLocation);
      return;
    }
    setState(() => _photoBusy = true);
    try {
      final attachment = await _conn.promptPhotos.readPending(photo.id);
      if (!mounted || !_photoMatches(photo)) return;
      if (!_attachments.any((a) => a.url == attachment.url)) {
        if (_attachments.length >= _maxAttachmentCount ||
            _attachments.fold<int>(
                      0,
                      (total, a) => total + _attachmentByteLength(a),
                    ) +
                    _attachmentByteLength(attachment) >
                _maxAggregateAttachmentBytes) {
          _showActionError(_chatL10n(context).photoDraftFull);
          return;
        }
        setState(() => _attachments.add(attachment));
      }
      if (await _persistDraft()) await _conn.promptPhotos.discard(photo.id);
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) setState(() => _photoBusy = false);
    }
  }

  Future<void> _discardPendingPhoto(PendingPromptPhoto photo) async {
    try {
      await _conn.promptPhotos.discard(photo.id);
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    }
  }

  Future<void> _reviewPendingPhoto(PendingPromptPhoto photo) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    if (_photoBusy) return;
    setState(() => _photoBusy = true);
    try {
      final attachment = await _conn.promptPhotos.readPending(photo.id);
      if (!mounted) return;
      setState(() => _photoBusy = false);
      await showFilePreviewSheet(
        context,
        FilePreviewData.fromDataUrl(
          name: attachment.filename,
          mimeType: attachment.mime,
          url: attachment.url,
        ),
      );
    } catch (error) {
      if (mounted) _showActionError(_photoError(error));
    } finally {
      if (mounted) setState(() => _photoBusy = false);
    }
  }

  /// Attaches an image committed into the composer by the IME — keyboard
  /// GIF/sticker insertions and Android's clipboard-image paste chip both
  /// arrive here via InputConnection.commitContent.
  ///
  /// This is the only zero-dependency image-paste path on Android: the
  /// framework's [Clipboard] service API reads `text/plain` exclusively, so
  /// a manual "Paste image" menu action cannot read image bytes without a
  /// platform plugin. Content without inline bytes (a URI-only commit) is
  /// ignored rather than half-attached.
  Future<void> _handleInsertedContent(KeyboardInsertedContent content) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final bytes = content.data;
    if (bytes == null || bytes.isEmpty) return;
    final mime = content.mimeType.isEmpty ? 'image/png' : content.mimeType;
    final extension = switch (mime.toLowerCase()) {
      'image/jpeg' || 'image/jpg' => 'jpg',
      'image/gif' => 'gif',
      'image/webp' => 'webp',
      'image/bmp' => 'bmp',
      _ => 'png',
    };
    final name =
        'pasted-image-${DateTime.now().millisecondsSinceEpoch}.$extension';
    try {
      await _addPreviewAttachment(
        filename: name,
        mimeType: mime,
        data: FilePreviewData(name: name, mimeType: mime, bytes: bytes),
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<PromptAttachment?> _chooseAttachment(
    List<PromptAttachment> current,
  ) async {
    final strings = _chatL10n(context);
    if (!_supportsPromptAttachments) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return null;
    }
    final unsupportedAttachment = strings.chatAttachmentUnsupported;
    if (current.length >= _maxAttachmentCount) {
      throw ProductException(
        strings.chatUiAttachmentCountLimit(_maxAttachmentCount),
      );
    }
    final currentBytes = current.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes >= _maxAggregateAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    final file = await FilePicker.pickFile(
      dialogTitle: strings.chatUiAttachToPrompt,
    );
    if (file == null) return null;
    final size = await file.length();
    if (size > _maxAttachmentBytes) {
      throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
    }
    if (size > 0 && currentBytes + size > _maxAggregateAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    final remainingAggregateBytes = _maxAggregateAttachmentBytes - currentBytes;
    final readLimit = remainingAggregateBytes < _maxAttachmentBytes
        ? remainingAggregateBytes
        : _maxAttachmentBytes;
    final bytes = await readAttachmentBytesWithinLimit(
      file,
      maxBytes: readLimit,
    );
    if (bytes == null && readLimit < _maxAttachmentBytes) {
      throw ProductException(strings.chatUiAttachmentsMustTotalNoMoreThan20);
    }
    if (bytes == null) {
      throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
    }
    if (isOfficeDocument(file.name)) {
      return _officeAttachment(file.name, bytes);
    }
    final mime = promptAttachmentMime(filename: file.name, bytes: bytes);
    if (mime == null) {
      throw ProductException(unsupportedAttachment);
    }
    final attachment = PromptAttachment(
      mime: mime,
      filename: file.name,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    return attachment;
  }

  /// A workbook or Word document, attached as the text it contains. No model
  /// takes these files as they are; read on the phone, a sheet is CSV and a
  /// document is its paragraphs, which an agent can work with.
  Future<PromptAttachment> _officeAttachment(
    String filename,
    Uint8List bytes,
  ) async {
    final strings = _chatL10n(context);
    final OfficeText converted;
    try {
      // Off the UI thread: a large sheet is a lot of XML.
      converted = await compute(
        (({String name, Uint8List bytes}) input) =>
            officeDocumentAsText(input.name, input.bytes),
        (name: filename, bytes: bytes),
      );
    } on FormatException {
      throw ProductException(strings.chatAttachmentOfficeUnreadable(filename));
    }
    if (converted.text.isEmpty) {
      throw ProductException(strings.chatAttachmentOfficeEmpty(filename));
    }
    final spreadsheet = !filename.toLowerCase().endsWith('.docx');
    final text = [
      strings.chatAttachmentOfficeHeader(filename),
      if (converted.truncated) strings.chatAttachmentOfficeTruncated,
      '',
      converted.text,
    ].join('\n');
    if (mounted) {
      _showComposerNote(
        spreadsheet
            ? strings.chatAttachmentSheetAttached(
                filename,
                converted.sections,
                converted.lines,
              )
            : strings.chatAttachmentDocumentAttached(filename),
      );
    }
    return PromptAttachment(
      mime: 'text/plain',
      filename: '$filename.${spreadsheet ? 'csv' : 'txt'}',
      url: 'data:text/plain;base64,${base64Encode(utf8.encode(text))}',
    );
  }

  Future<void> _openPromptEditor() async {
    if (_conn.isIsolated) return;
    if (_promptShelfBusy) return;
    final result = await Navigator.of(context).push<_PromptEditorResult>(
      KitPageRoute<_PromptEditorResult>(
        fullscreenDialog: true,
        builder: (_) => _PromptEditorScreen(
          initialValue: _composer.value,
          initialAttachments: _attachments,
          chooseAttachment: _supportsPromptAttachments
              ? _chooseAttachment
              : null,
        ),
      ),
    );
    if (!mounted || result == null) return;
    _composer.value = result.value;
    setState(() {
      _attachments
        ..clear()
        ..addAll(result.attachments);
    });
    _focus.requestFocus();
  }

  int _attachmentByteLength(PromptAttachment attachment) {
    final comma = attachment.url.indexOf(',');
    if (comma < 0) return 0;
    final header = attachment.url.substring(0, comma);
    final payload = attachment.url.substring(comma + 1);
    if (!header.endsWith(';base64')) return utf8.encode(payload).length;
    final padding = payload.endsWith('==')
        ? 2
        : payload.endsWith('=')
        ? 1
        : 0;
    return (payload.length * 3 ~/ 4) - padding;
  }

  String _mimeForFilename(String filename) {
    final extension = filename.contains('.')
        ? filename.split('.').last.toLowerCase()
        : '';
    return switch (extension) {
      'png' => 'image/png',
      'jpg' || 'jpeg' => 'image/jpeg',
      'gif' => 'image/gif',
      'webp' => 'image/webp',
      'svg' => 'image/svg+xml',
      'pdf' => 'application/pdf',
      'json' => 'application/json',
      'md' || 'txt' || 'log' => 'text/plain',
      'dart' ||
      'js' ||
      'ts' ||
      'tsx' ||
      'jsx' ||
      'py' ||
      'go' ||
      'rs' => 'text/plain',
      _ => 'application/octet-stream',
    };
  }

  Future<void> _abort() async {
    if (_aborting) return;
    if (!_conn.isIsolated) KitHaptics.commit(context);
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    setState(() => _aborting = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (location != _conn.locationRevision || profileID != _conn.profile?.id) {
      setState(() => _aborting = false);
      _showActionError(_chatL10n(context).workContextChanged);
      return;
    }
    if (actionApi == null) {
      setState(() => _aborting = false);
      _showActionError(
        _conn.connectionError ??
            _chatL10n(context).chatUiOpenCodeIsReconnectingTryAgainShortly,
      );
      return;
    }
    try {
      await actionApi.abort(widget.sessionID);
      if (mounted) {
        final prompt = _messages.lastIndexWhere(_isPrompt);
        setState(() {
          _localTurnSince = null;
          if (prompt >= 0) _stoppedPromptID = _messages[prompt].info.id;
        });
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _aborting = false);
    }
  }

  Future<void> _share() async {
    final strings = _chatL10n(context);
    final confirmed = await showKitConfirm(
      context,
      icon: AppIconography.globe,
      title: strings.chatUiShareThisSession,
      body: strings.chatUiAnyoneWithTheLinkCanViewThis,
      confirmLabel: strings.chatUiShareSession,
    );
    if (!confirmed) return;
    try {
      final repository = await _requireActionRepository();
      final url = await repository.shareSession(widget.sessionID);
      if (url == null) {
        throw ProductException(strings.chatUiNoShareLinkWasReturned);
      }
      if (mounted) {
        setState(() => _localShareUrl = url);
        await _conn.refreshSessions();
        if (!mounted) return;
        // The share banner shows the link; the copy is confirmed by the
        // kit's tick and announcement, and only a failed copy says more.
        await _copyShareLink(url);
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Copies the public link. A clipboard that refuses it points at the
  /// share banner, where the link stays visible.
  Future<void> _copyShareLink(String url) async {
    try {
      // The kit's redaction leaves a plain share address as it is and masks
      // a credential a server might put in it (G12).
      await KitCopy.copy(
        context,
        url,
        announcement: _chatL10n(context).chatUiShareLinkCopied,
      );
    } catch (_) {
      if (!mounted) return;
      _showComposerNote(
        _chatL10n(context).chatUiSessionSharedCopyTheVisibleLinkManually,
      );
    }
  }

  /// Every entry point (banner, session menu, /unshare) lands here, so the
  /// confirmation cannot be skipped by choosing a different one.
  Future<void> _stopSharing() async {
    if (!await confirmStopSharing(context) || !mounted) return;
    try {
      final repository = await _requireActionRepository();
      await repository.unshareSession(widget.sessionID);
      if (!mounted) return;
      // The share banner leaves with the link: that is the confirmation.
      setState(() => _localShareUrl = null);
      await _conn.refreshSessions();
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _fork() async {
    if (!_conn.capabilities.sessionFork) return;
    try {
      final repository = await _requireActionRepository();
      final id = await repository.forkSession(widget.sessionID);
      await _conn.refreshSessions();
      if (mounted) await _landInFork(id);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Every fork lands in one place (P10.2): the copy opens in place of
  /// this conversation, from the menu's Fork, "/fork", a prompt's own Fork
  /// and the timeline alike. A fork from a prompt brings that prompt back
  /// into the copy's composer.
  Future<void> _landInFork(
    String id, {
    String initialText = '',
    List<PromptAttachment> initialAttachments = const [],
  }) => Navigator.of(context).pushReplacement(
    KitPageRoute<void>(
      builder: (_) => ChatScreen(
        sessionID: id,
        initialText: initialText,
        initialAttachments: initialAttachments,
      ),
    ),
  );

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final strings = _chatL10n(context);
    final repository = await _conn.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(
      _conn.connectionError ??
          strings.chatUiOpenCodeIsReconnectingTryAgainShortly,
    );
  }

  Future<void> _openTimeline() async {
    if (_messages.isEmpty) return;
    // A prompt's own Fork in the timeline lands like every fork (P10.2).
    final selection = await showKitFramedSheet<_TimelineSelection>(
      context,
      useSafeArea: true,
      maxWidth: 720,
      builder: (context) => ListenableBuilder(
        listenable: _historyChanges,
        builder: (context, _) => _TimelineSheet(
          messages: List.of(_visibleHistory),
          forkAvailable: _conn.capabilities.sessionFork,
          hasOlder: _olderCursor != null,
          loadingOlder: _loading || _loadingOlder,
          olderError: _olderError,
          olderNeedsReload: _olderNeedsReload || _resetHistoryOnLoad,
          loadOlder: _loadOlder,
        ),
      ),
    );
    if (!mounted || selection == null) return;
    if (selection.fork) {
      await _forkFromMessage(selection.message);
      return;
    }
    if (selection.query.isNotEmpty) {
      _openFind(query: selection.query, messageID: selection.message.info.id);
    } else {
      _jumpToMessage(selection.message.info.id);
    }
  }

  void _syncFind() {
    _findHits = _findOpen ? _findIndex.search(_visibleHistory, _findQuery) : [];
    final retained = _findHits.indexWhere((match) => match.key == _findKey);
    _findCursor = retained >= 0
        ? retained
        : _findCursor.clamp(0, math.max(0, _findHits.length - 1));
    _findKey = _findHits.isEmpty ? null : _findHits[_findCursor].key;
  }

  void _openFind({String? query, String? messageID}) {
    setState(() {
      _findOpen = true;
      _findLocation = _conn.locationRevision;
      if (query != null) {
        _findController.text = query;
        _findQuery = query.trim();
        _findKey = null;
        _findCursor = 0;
      }
      _syncFind();
      if (messageID != null) {
        final index = _findHits.indexWhere(
          (match) => match.messageID == messageID,
        );
        if (index >= 0) {
          _findCursor = index;
          _findKey = _findHits[index].key;
        }
      }
    });
    if (messageID != null) {
      _jumpToMessage(messageID, alignment: .85);
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted || !_findOpen) return;
        _findFocus.requestFocus();
        _findController.selection = TextSelection(
          baseOffset: 0,
          extentOffset: _findController.text.length,
        );
      });
    }
  }

  void _changeFind(String query) {
    _findDebounce?.cancel();
    _findDebounce = Timer(const Duration(milliseconds: 220), () {
      if (!mounted || !_findOpen) return;
      setState(() {
        _findQuery = query.trim();
        _findKey = null;
        _findCursor = 0;
        _syncFind();
      });
      if (_findHits.isNotEmpty) {
        _jumpToMessage(_findHits.first.messageID, alignment: .85);
      }
    });
  }

  void _navigateFind(int step) {
    _findDebounce?.cancel();
    if (_findQuery != _findController.text.trim()) {
      setState(() {
        _findQuery = _findController.text.trim();
        _findCursor = 0;
        _findKey = null;
        _syncFind();
      });
      step = 0;
    }
    if (_findHits.isEmpty) return;
    _findFocus.unfocus();
    _findNavigationFocus.requestFocus();
    _findExcerptContext = null;
    setState(() {
      _findCursor = (_findCursor + step) % _findHits.length;
      _findKey = _findHits[_findCursor].key;
    });
    _jumpToMessage(_findHits[_findCursor].messageID, alignment: .85);
  }

  void _closeFind() {
    _findAllLoading = false;
    _findDebounce?.cancel();
    _findFocus.unfocus();
    _findNavigationFocus.unfocus();
    _findExcerptContext = null;
    setState(() {
      _findOpen = false;
      _findQuery = '';
      _findController.clear();
      _findHits = [];
      _findKey = null;
    });
  }

  Future<void> _searchAllHistory() async {
    if (_findAllLoading || _loading || _loadingOlder) return;
    _findNavigationFocus.requestFocus();
    final location = _conn.locationRevision;
    setState(() => _findAllLoading = true);
    try {
      while (mounted &&
          _findOpen &&
          _findAllLoading &&
          location == _conn.locationRevision &&
          _olderCursor != null) {
        final before = _olderCursor;
        await _loadOlder();
        if (_olderError != null || _olderCursor == before) break;
      }
    } finally {
      if (mounted) setState(() => _findAllLoading = false);
    }
  }

  /// The slash commands' display toggles: the transcript changes at once,
  /// and the bar says which way and offers the way back.
  Future<void> _toggleReasoningDisplay() async {
    final expanded = await _setReasoningDisplay(
      !_conn.transcriptReasoningExpanded,
    );
    if (!mounted || expanded == null) return;
    showKitUndo(
      _undoHost,
      message: expanded
          ? _chatL10n(context).chatUiReasoningExpandedInTheTranscript
          : _chatL10n(context).chatUiLongReasoningCollapsedInTheTranscript,
      onUndo: () => _setReasoningDisplay(!expanded),
    );
  }

  /// Folds every step, reasoning line and work fold that is open.
  void _collapseAllSteps() {
    setState(_transcriptExpansion.collapseAll);
  }

  Future<bool?> _setReasoningDisplay(bool expanded) async {
    if (!mounted) return null;
    // The transcript-wide choice is the new default for every reasoning
    // block, so per-part overrides are dropped instead of being silently
    // rewritten with the toggle's value. Rewriting them meant one flip
    // erased the session's per-part choices, and an off-screen block kept
    // resisting the toggle because its stale override outlived it.
    setState(
      () => _transcriptExpansion.removeWhere(
        (key, _) => key.startsWith('reasoning:'),
      ),
    );
    await _conn.setTranscriptReasoningExpanded(expanded);
    return mounted ? expanded : null;
  }

  Future<void> _toggleTimestampDisplay() async {
    final visible = !_conn.transcriptTimestampsVisible;
    await _conn.setTranscriptTimestampsVisible(visible);
    if (!mounted) return;
    showKitUndo(
      _undoHost,
      message: visible
          ? _chatL10n(context).chatUiMessageTimestampsShown
          : _chatL10n(context).chatUiMessageTimestampsHidden,
      onUndo: () => _conn.setTranscriptTimestampsVisible(!visible),
    );
  }

  /// Fraction of the model's context window consumed, from the newest
  /// assistant message that reported token usage and the catalog's limit for
  /// its exact model. Null when either side is unknown.
  double? _contextWindowUsage() {
    final catalog = _conn.catalog;
    if (catalog == null) return null;
    for (var index = _visibleHistory.length - 1; index >= 0; index -= 1) {
      final info = _messages[index].info;
      if (info.role != 'assistant' || info.tokens.total <= 0) continue;
      for (final model in catalog.models) {
        if (model.providerID == info.providerID && model.id == info.modelID) {
          return model.contextLimit > 0
              ? info.tokens.total / model.contextLimit
              : null;
        }
      }
      return null;
    }
    return null;
  }

  bool _onTranscriptScroll(ScrollNotification notification) {
    // Code/table readers own nested scrollables. Their gestures must not
    // change whether the transcript follows the latest message.
    if (notification.depth != 0 || notification.metrics.axis != Axis.vertical) {
      return false;
    }
    // The list is reversed, so pixel offset measures distance scrolled away
    // from the newest message.
    final away = notification.metrics.pixels > 480;
    if (away != _awayFromLatest) {
      void apply() => setState(() {
        _awayFromLatest = away;
        _pinnedMessageCount = away ? _messages.length : null;
      });
      // A scroll position can settle while the list lays out (new content
      // ends a fling): rebuild after that frame, never during it.
      if (WidgetsBinding.instance.schedulerPhase ==
          SchedulerPhase.persistentCallbacks) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && away != _awayFromLatest) apply();
        });
      } else {
        apply();
      }
    }
    return false;
  }

  /// How many messages the transcript currently renders — the full list, or
  /// the pinned count while the reader is scrolled away from the live end.
  int get _renderedMessageCount {
    final pinned = _pinnedMessageCount;
    final count = _visibleHistory.length;
    if (!_awayFromLatest || pinned == null) return count;
    return pinned < count ? pinned : count;
  }

  /// Servers return undone rows until the undo is final (v2 until commit,
  /// v1 until the next prompt). Keep them in the hydration cache, but apply
  /// the server's boundary to every chat view on every server.
  Iterable<MessageWithParts> get _visibleHistory {
    final boundary =
        _conn.sessionsById[widget.sessionID]?.stagedRevert?.messageID;
    return boundary == null
        ? _messages
        : _messages.takeWhile(
            (message) => message.info.id.compareTo(boundary) < 0,
          );
  }

  /// Messages sitting entirely above the viewport: the transcript's list
  /// index grows toward older messages, so everything past the largest
  /// visible index is "earlier".
  int _earlierMessageCount(Iterable<ItemPosition> positions) {
    var oldestVisible = -1;
    for (final position in positions) {
      if (position.index >= _renderedMessageCount) continue;
      if (position.itemTrailingEdge <= 0 || position.itemLeadingEdge >= 1) {
        continue;
      }
      if (position.index > oldestVisible) oldestVisible = position.index;
    }
    if (oldestVisible < 0) return 0;
    final earlier = _renderedMessageCount - 1 - oldestVisible;
    return earlier > 0 ? earlier : 0;
  }

  void _jumpToLatest() {
    if (!_messageScroll.isAttached) return;
    setState(() {
      _awayFromLatest = false;
      _pinnedMessageCount = null;
    });
    if (KitMotion.reduced(context)) {
      _messageScroll.jumpTo(index: 0);
    } else {
      _messageScroll.scrollTo(
        index: 0,
        duration: KitMotion.standard,
        curve: KitMotion.enter,
      );
    }
  }

  /// A starter fills the composer and never sends: the person may want to
  /// finish the sentence or change a word first.
  void _insertSuggestion(String text) {
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    if (_focus.hasFocus) {
      // Focus alone does not bring back a keyboard the person dismissed
      // while the field kept focus.
      unawaited(SystemChannels.textInput.invokeMethod<void>('TextInput.show'));
    } else {
      _focus.requestFocus();
    }
  }

  /// The ways to start for the folder as known now; nothing once the
  /// person has typed, so the row never competes with their own words.
  Widget _startersRow() => ListenableBuilder(
    listenable: Listenable.merge([_startFacts, _composer]),
    builder: (context, _) {
      final starters = _composer.text.trim().isNotEmpty
          ? const <ChatStarter>[]
          : chatStartSuggestions(
              _currentStartFacts(),
              l10n: _chatL10n(context),
            );
      if (starters.isEmpty) return const SizedBox.shrink();
      return Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 860),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: _ChatStarters(
              starters: starters,
              onPick: (starter) => _insertSuggestion(starter.text),
            ),
          ),
        ),
      );
    },
  );

  /// Facts about the folder a new conversation runs in; null while none
  /// have been asked for.
  final _startFacts = ValueNotifier<ChatStartFacts?>(null);
  String? _startFactsDirectory;
  bool _startFactsRequested = false;
  int _startFactsGeneration = 0;
  Timer? _startFactsFallback;

  /// The facts the empty state shows now: pending until the folder the
  /// conversation runs in has been looked at.
  ChatStartFacts _currentStartFacts() {
    final facts = _startFacts.value;
    final directory = _conn.directory;
    if (facts != null && facts.directory == directory) return facts;
    return ChatStartFacts.pending(directory);
  }

  /// Asks once per folder, and only when an empty conversation is on screen,
  /// so opening an existing conversation costs nothing.
  void _requestStartFacts() {
    final directory = _conn.directory;
    if (_startFactsRequested && _startFactsDirectory == directory) return;
    _startFactsRequested = true;
    _startFactsDirectory = directory;
    // Built during build; the load starts after the frame so its first
    // answer never marks widgets dirty mid-build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_loadStartFacts(directory));
    });
  }

  Future<void> _loadStartFacts(String? directory) async {
    final generation = ++_startFactsGeneration;
    final bare = ChatStartFacts(directory: directory);
    void publish(ChatStartFacts facts) {
      if (!mounted || generation != _startFactsGeneration) return;
      _startFactsFallback?.cancel();
      _startFacts.value = facts;
    }

    if (_conn.isIsolated || !bare.hasProject) {
      publish(bare);
      return;
    }
    // A slow server must not hold the starters back: after a moment they
    // appear from what is known, and a late answer still sharpens them.
    _startFactsFallback?.cancel();
    _startFactsFallback = Timer(const Duration(seconds: 3), () {
      if (mounted &&
          generation == _startFactsGeneration &&
          _startFacts.value?.directory != directory) {
        _startFacts.value = bare;
      }
    });
    final filesFuture = _conn.capabilities.fileBrowsing
        ? () async {
            final api = await _conn.prepareActionTransport();
            return api?.listFiles('');
          }().then<List<FileNode>?>((files) => files, onError: (_) => null)
        : Future<List<FileNode>?>.value(null);
    final vcsFuture = () async {
      final repository = await _conn.prepareActionRepository();
      return repository?.loadVersionControlHealth();
    }().then<VersionControlHealth?>((health) => health, onError: (_) => null);
    final (files, vcs) = await (filesFuture, vcsFuture).wait;
    final git = switch (vcs?.setupState) {
      VersionControlSetupState.git => true,
      VersionControlSetupState.absent => false,
      _ => (vcs?.branch?.isNotEmpty ?? false) ? true : null,
    };
    final branch = vcs?.branch?.trim() ?? '';
    publish(
      ChatStartFacts(
        directory: directory,
        // The server may list Git's own folder; it is not the project's.
        entries: files?.where((node) => node.name != '.git').length,
        git: git,
        // A branch name needs a commit to point at; an unborn branch reads
        // back as HEAD, or not at all.
        hasHistory: git != false && branch.isNotEmpty && branch != 'HEAD',
        changes: vcs?.changes.length,
      ),
    );
  }

  /// [ChatScreen.landOnFailure]: the newest turn that ended in an error,
  /// scrolled to and marked once. None in the loaded history: the chat
  /// opens at its newest turn as usual.
  void _landOnFailedTurn() {
    if (!widget.landOnFailure || _landedOnFailure) return;
    _landedOnFailure = true;
    MessageWithParts? failed;
    for (final message in _visibleHistory) {
      if (message.info.errorText != null) failed = message;
    }
    if (failed == null) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _jumpToMessage(failed!.info.id, alignment: .3);
    });
  }

  void _jumpToMessage(String messageID, {double alignment = .5}) {
    final chronologicalIndex = _messages.indexWhere(
      (message) => message.info.id == messageID,
    );
    if (chronologicalIndex < 0 ||
        chronologicalIndex >= _visibleHistory.length) {
      _showActionError(_chatL10n(context).chatUiThatMessageIsNoLongerInThis);
      return;
    }
    final listIndex = _visibleHistory.length - 1 - chronologicalIndex;
    _highlightTimer?.cancel();
    setState(() {
      // Materialize any messages deferred while scrolled away so the target
      // index maps onto the rendered list.
      _pinnedMessageCount = null;
      _highlightedMessageID = messageID;
    });
    final findKey = _findOpen ? _findKey : null;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted ||
          !_messageScroll.isAttached ||
          _highlightedMessageID != messageID) {
        return;
      }
      if (findKey != null) {
        // A distant animated scroll builds a temporary list. Its excerpt
        // context can be retired before the final list takes over. Materialize
        // search targets directly, then animate only the precise reveal.
        _messageScroll.jumpTo(index: listIndex, alignment: alignment);
      } else {
        await _messageScroll.scrollTo(
          index: listIndex,
          alignment: alignment,
          duration: KitMotion.reduced(context)
              ? Duration.zero
              : KitMotion.standard,
          curve: KitMotion.enter,
        );
      }
      if (findKey == null) return;
      final layout = WidgetsBinding.instance.endOfFrame;
      // jumpTo can be a no-op for the same list index. Still wait for a
      // scheduled layout before revealing the changed excerpt.
      WidgetsBinding.instance.scheduleFrame();
      await layout;
      final target = _findExcerptContext;
      if (!mounted ||
          !_findOpen ||
          _findKey != findKey ||
          target == null ||
          !target.mounted) {
        return;
      }
      final scrollable = Scrollable.of(target);
      final viewport = scrollable.context.findRenderObject() as RenderBox;
      final excerpt = target.findRenderObject() as RenderBox;
      final top = excerpt.localToGlobal(Offset.zero, ancestor: viewport).dy;
      final desired = math.max(
        0.0,
        (viewport.size.height - excerpt.size.height) * .3,
      );
      final position = scrollable.position;
      // This list centers slivers around an arbitrary item. ensureVisible's
      // sliver reveal offset is unreliable there; use measured viewport pixels.
      final delta = top - desired;
      await position.moveTo(
        position.pixels +
            (position.axisDirection == AxisDirection.up ? -delta : delta),
        clamp: false,
        duration: KitMotion.reduced(context) ? Duration.zero : KitMotion.quick,
        curve: KitMotion.enter,
      );
    });
    _highlightTimer = Timer(const Duration(seconds: 2), () {
      if (mounted && _highlightedMessageID == messageID) {
        setState(() => _highlightedMessageID = null);
      }
    });
  }

  static String _messageText(MessageWithParts message) => message.parts
      .where((part) => part.type == 'text' && !part.synthetic)
      .map((part) => part.text)
      .where((value) => value.trim().isNotEmpty)
      .join('\n\n');

  /// Adjacent assistant messages form the reply already presented by the
  /// transcript. Copy that complete text without changing which individual
  /// message destructive actions target.
  ({String label, String text}) _messageCopy(MessageWithParts message) {
    final index = _messages.indexWhere(
      (item) => item.info.id == message.info.id,
    );
    if (message.info.role != 'assistant' || index < 0) {
      return (
        label: _chatL10n(context).chatUiCopyMessageText,
        text: _messageText(message),
      );
    }
    // The reply is the whole turn; a notice in the middle of it (context
    // added, project moved) is not where it ends.
    final reply = _turnSteps(_messages, index);
    final start = _messages.indexOf(reply.first);
    final end = _messages.indexOf(reply.last);
    final unfinished =
        _conn.busySessions.contains(widget.sessionID) &&
        reply.any((item) => item.info.time?.isDone != true);
    return (
      label: start == end
          ? _chatL10n(context).chatUiCopyMessageText
          : unfinished
          ? _chatL10n(context).chatCopyReplySoFar
          : start == 0 && _olderCursor != null
          ? _chatL10n(context).historyCopyLoadedReply
          : _chatL10n(context).chatCopyCompleteReply,
      text: reply
          .map(_messageText)
          .where((text) => text.trim().isNotEmpty)
          .join('\n\n'),
    );
  }

  /// Copies verbatim (a reply is the person's to paste), confirmed by the
  /// kit's tick and announcement, like the reply footer's Copy.
  Future<void> _copyMessageText(MessageWithParts message) =>
      KitCopy.copy(context, _messageCopy(message).text, redact: false);

  /// A transcript message's actions, one list for every way in: the reply
  /// footer's More, long-press, right-click, Shift+F10 and the screen
  /// reader's custom actions (the footer draws Copy beside More, so its
  /// menu leaves Copy out). Ordered by use; delete last (KIT-28).
  List<KitMenuItem> _messageContextActions(MessageWithParts message) => [
    if (_messageCopy(message).text.isNotEmpty)
      KitMenuItem(
        key: const ValueKey('message-menu-copy'),
        label: _messageCopy(message).label,
        icon: AppIcons.copy,
        onSelected: () => unawaited(_copyMessageText(message)),
      ),
    if (message.info.role == 'user' && _conn.capabilities.sessionFork)
      KitMenuItem(
        key: const ValueKey('message-menu-fork'),
        label: _chatL10n(context).chatUiForkFromThisPrompt,
        icon: AppIconography.fork,
        onSelected: () => unawaited(_forkFromMessage(message)),
      ),
    if (_canReadReply(message))
      KitMenuItem(
        key: const ValueKey('message-menu-read-aloud'),
        label: _chatL10n(context).readAloudAction,
        icon: AppIconography.volume,
        onSelected: () => unawaited(_readReply(message)),
      ),
    if (_canReadReply(message) && _readAloudConsented)
      KitMenuItem(
        key: const ValueKey('message-menu-read-aloud-voice'),
        label: _chatL10n(context).readAloudOtherVoice,
        icon: AppIconography.speakUser,
        onSelected: () => unawaited(_readReply(message, chooseVoice: true)),
      ),
    if (_canUndoFrom(message))
      KitMenuItem(
        key: const ValueKey('message-menu-revert'),
        label: _chatL10n(context).revertFromHere,
        icon: AppIconography.history,
        onSelected: () => unawaited(_undoFrom(message)),
      ),
    if (_conn.capabilities.messageDelete)
      KitMenuItem(
        key: const ValueKey('message-menu-delete'),
        label: _chatL10n(context).chatUiDeleteMessage,
        icon: AppIconography.delete,
        destructive: true,
        onSelected: () => unawaited(_deleteMessage(message)),
      ),
  ];

  Future<void> _deleteMessage(MessageWithParts message) async {
    final confirmed = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      title: _chatL10n(context).chatUiDeleteThisMessage,
      body: _chatL10n(context).chatUiTheMessageAndAllOfItsParts,
      confirmLabel: _chatL10n(context).chatUiDeleteMessage,
    );
    if (!confirmed || !mounted) return;
    try {
      final repository = await _requireActionRepository();
      await repository.deleteMessage(
        sessionID: widget.sessionID,
        messageID: message.info.id,
      );
      if (!mounted) return;
      // The message leaving the transcript is the confirmation.
      setState(() {
        _messages.removeWhere((entry) => entry.info.id == message.info.id);
      });
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _forkFromMessage(MessageWithParts message) async {
    if (!_conn.capabilities.sessionFork || message.info.role != 'user') return;
    final text = message.parts
        .where((part) => part.type == 'text' && !part.synthetic)
        .map((part) => part.text)
        .join();
    final attachments = <PromptAttachment>[];
    for (final part in message.parts.where(
      (part) => part.type == 'file' && !part.synthetic,
    )) {
      final url = part.url;
      if (url == null || url.isEmpty) {
        _showActionError(
          _chatL10n(context).chatUiThisPromptCannotBeRestoredBecauseAn,
        );
        return;
      }
      final filename = part.filename?.trim().isNotEmpty == true
          ? part.filename!
          : 'attachment';
      attachments.add(
        PromptAttachment(
          mime: part.mime?.trim().isNotEmpty == true
              ? part.mime!
              : _mimeForFilename(filename),
          filename: filename,
          url: url,
        ),
      );
    }
    try {
      final repository = await _requireActionRepository();
      final id = await repository.forkSession(
        widget.sessionID,
        messageID: message.info.id,
      );
      await _conn.refreshSessions();
      if (!mounted) return;
      await _landInFork(id, initialText: text, initialAttachments: attachments);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Whether the failed compaction at [index] is still the state of things:
  /// it is the newest compaction notice, nothing is running, and this server
  /// can compact on request.
  bool _canCompactAgain(int index) {
    if (_conn.isIsolated || !_supportsSessionCompact) return false;
    if (_conn.busySessions.contains(widget.sessionID)) return false;
    final part = v2VariantPart(_messages[index]);
    if (part?.type != 'v2:compaction' || part?.toolName != 'failed') {
      return false;
    }
    for (var later = index + 1; later < _messages.length; later += 1) {
      if (v2VariantPart(_messages[later])?.type == 'v2:compaction') {
        return false;
      }
    }
    return true;
  }

  Future<void> _compact() async {
    if (!_supportsSessionCompact) return;
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null && !_conn.serverOwnsSessionSelection) {
      _showActionError(
        _chatL10n(context).chatUiSelectAModelBeforeCompactingThisSession,
      );
      return;
    }
    final strings = _chatL10n(context);
    try {
      // Asked first; the sheet shows progress while the request runs and
      // keeps the question open with Try again if it fails.
      final confirmed = await showKitConfirm(
        context,
        icon: AppIconography.collapse,
        title: strings.chatUiCompactConfirmTitle,
        body: strings.chatUiCompactConfirmBody,
        confirmLabel: strings.chatUiCompactConfirmAction,
        action: () async {
          final repository = await _requireActionRepository();
          await repository.compactSession(
            widget.sessionID,
            providerID: model?.providerID ?? '',
            modelID: model?.modelID ?? '',
          );
        },
      );
      if (!confirmed || !mounted) return;
      _showComposerNote(_chatL10n(context).chatUiCompactionStarted);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// Hidden at once on Dismiss, before the save lands.
  bool _freeModelNoteDismissed = false;

  /// First run's one notification question, asked in the status slot.
  late final FirstReplyNotifyOffer _notifyOffer;

  void _notifyOfferChanged() {
    if (mounted) setState(() {});
  }

  /// True once this conversation holds a finished reply and is idle. A
  /// reply that failed (or a message that was not sent) is not the moment
  /// to offer "get told when it needs you".
  bool get _replyCompleted =>
      !_conn.busySessions.contains(widget.sessionID) &&
      _promptError == null &&
      _sendError == null &&
      _messages.any(
        (message) =>
            message.info.role == 'assistant' && message.info.errorText == null,
      );

  /// The notification question, or why "Notify me" did not work: a line in
  /// the status slot over the transcript's top, so the reply and its actions
  /// at the bottom never move when it comes or goes (F16).
  _ChatStatus? _notifyOfferStatus(BuildContext context) {
    final l10n = _chatL10n(context);
    final offer = _notifyOffer;
    if (offer.failure case final failure?) {
      final error = offer.failureError;
      return _ChatStatus(
        id: 'notify-failed',
        icon: AppIconography.error,
        tone: AppStatusTone.failure,
        message: switch (failure) {
          FirstReplyNotifyFailure.saveFailed => l10n.consentSaveFailed,
          // The app's own sentence from the Android side ("Notification
          // access is required.") stays; exception text is said in words.
          FirstReplyNotifyFailure.notEnabled =>
            error == null ? l10n.e7SettingsUi22 : productErrorText(error),
        },
        onDismiss: offer.dismissFailure,
      );
    }
    if (!offer.showFor(replyCompleted: _replyCompleted)) return null;
    return _ChatStatus(
      id: 'notify-offer',
      icon: AppIconography.inbox,
      message: l10n.firstRunNotifyTitle,
      action: KitAction(
        key: const ValueKey('chat-notify-offer-accept'),
        label: l10n.firstRunNotifyAccept,
        onPressed: offer.working ? null : () => unawaited(offer.accept()),
      ),
      onDismiss: offer.working ? null : () => unawaited(offer.decline()),
      dismissTooltip: l10n.firstRunNotifyDecline,
    );
  }

  /// The free-model note's Dismiss: hidden now, and kept dismissed for this
  /// conversation on this server.
  void _dismissFreeModelNote() {
    setState(() => _freeModelNoteDismissed = true);
    final profileId = _conn.profile?.id;
    if (profileId == null) return;
    unawaited(
      FreeModelNoteDismissals.dismiss(
        _conn.store.prefs,
        profileId,
        widget.sessionID,
      ),
    );
  }

  /// The free-model note's way out: the provider sign-ins of this server.
  Future<void> _signInToProvider() async {
    if (_conn.isIsolated) return;
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) => IntegrationsScreen(
          controller: _conn,
          mode: IntegrationsMode.providers,
        ),
      ),
    );
  }

  /// The providers/integrations screen, reached from a provider-auth error
  /// card; the same destination the `/integrations` command opens.
  Future<void> _openProviders() async {
    if (_conn.isIsolated) return;
    await Navigator.of(context).push(
      KitPageRoute<void>(builder: (_) => IntegrationsScreen(controller: _conn)),
    );
  }

  /// Sends "Continue" through the normal send path after an output-length
  /// cut, keeping any half-typed draft for afterwards.
  /// The words of the newest turn's prompt when that turn got no answer
  /// and the prompt is words alone (a resend from here cannot bring files).
  String? get _unansweredWords {
    final prompt = _unanswered?.$1;
    if (prompt == null || prompt >= _messages.length) return null;
    return _promptWordsOnly(_messages[prompt]);
  }

  /// The model a "model not found" [raw] error suggests, when this server
  /// has it ([_suggestedModel]).
  CatalogModel? _suggestedModelFor(String? raw) {
    final models = _conn.catalog?.models;
    if (raw == null || models == null || models.isEmpty) return null;
    return _suggestedModel(raw, models);
  }

  /// A model by the name the catalog gives it.
  String _modelName(CatalogModel model) => model.name.trim().isNotEmpty
      ? model.name.trim()
      : presentedModelLabel(model.providerID, model.id);

  /// Sends the unanswered prompt again, as it was, after switching this
  /// conversation to [model] when one is given ("Use GPT-5.6 Pro and
  /// resend"). Whatever the message box holds is set aside for the send and
  /// put back after it.
  Future<void> _resendUnanswered({CatalogModel? model}) async {
    final words = _unansweredWords;
    if (words == null || _sending) return;
    if (model != null) {
      try {
        await _conn.selectModelForSession(
          widget.sessionID,
          ModelRef(providerID: model.providerID, modelID: model.id),
        );
      } catch (error) {
        if (mounted) _showActionError(error);
        return;
      }
      if (!mounted) return;
    }
    final draft = _composer.value;
    final draftAttachments = List<PromptAttachment>.of(_attachments);
    setState(() {
      _attachments.clear();
      _promptError = null;
    });
    _composer.text = words;
    await _send();
    if (!mounted) return;
    if (draft.text.isNotEmpty || draftAttachments.isNotEmpty) {
      _composer.value = draft;
      setState(
        () => _attachments
          ..clear()
          ..addAll(draftAttachments),
      );
    }
  }

  Future<void> _continueTruncated() async {
    if (_sending) return;
    final draft = _composer.text;
    _composer.text = _chatL10n(context).returnBriefContinue;
    await _send();
    if (mounted && _composer.text.isEmpty && draft.trim().isNotEmpty) {
      _composer.text = draft;
    }
  }

  /// "Undo last prompt" (conversation menu, /undo): the same "Undo from
  /// here" flow as a prompt's own menu, from the newest prompt.
  Future<void> _revertLast() async {
    if (!_conn.capabilities.sessionRevert) return;
    MessageWithParts? target;
    for (final message in _visibleHistory.toList().reversed) {
      if (_isUndoTarget(message)) {
        target = message;
        break;
      }
    }
    if (target == null) return;
    await _undoFrom(target);
  }

  Future<void> _restore() async {
    if (!_conn.capabilities.sessionRevert) return;
    if (_conn.supportsStagedRevert) {
      await _reviewStagedRevert();
      return;
    }
    try {
      final repository = await _requireActionRepository();
      await repository.restoreSession(widget.sessionID);
      // The boundary clears on the session: read it so the turns come back.
      await _conn.ensureSession(widget.sessionID);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// A prompt the server saved and still holds: undo can start there.
  bool _isUndoTarget(MessageWithParts message) =>
      message.info.role == 'user' &&
      !message.info.id.startsWith('local-') &&
      !_conn
          .inboxItemsFor(widget.sessionID)
          .any((item) => item.id == message.info.id);

  /// Whether a prompt's menu offers "Undo from here". A busy conversation
  /// still offers it: the sheet says why its primary is off.
  bool _canUndoFrom(MessageWithParts message) =>
      _conn.capabilities.sessionRevert && _isUndoTarget(message);

  /// The one "Undo from here" flow (map stage-revert-sheet) behind every
  /// door: a prompt's menu, the conversation menu and /undo. Where the
  /// server stages an undo, the sheet stages it and the review page follows;
  /// otherwise the sheet is the honest one-step confirm and undoes at once.
  Future<void> _undoFrom(MessageWithParts message) async {
    if (!_canUndoFrom(message)) return;
    final review = _conn.reviewSessionRevert(widget.sessionID);
    final prompt = message.parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .join('\n');
    if (_conn.supportsStagedRevert) {
      // The sheet stages itself: its primary shows the work, and a failure
      // stays in the sheet with the choice kept, instead of an alert after
      // it closed.
      final applyFiles = await showStageRevertSheet(
        context,
        controller: _conn,
        review: review,
        prompt: prompt,
        stage: (applyFiles) => _conn.stageSessionRevert(
          review,
          message.info.id,
          applyFiles: applyFiles,
        ),
      );
      if (!mounted || applyFiles == null) return;
      if (review.scope == _conn.reviewSessionRevert(widget.sessionID).scope) {
        await _reviewStagedRevert();
      }
      return;
    }
    final after = _messagesAfter(message);
    final undone = await showStageRevertSheet(
      context,
      controller: _conn,
      review: review,
      prompt: prompt,
      messagesAfter: after?.length,
      editedFiles: after == null ? const [] : RunResult.editedPaths(after),
      undo: () async {
        final repository = await _requireActionRepository();
        await repository.revertSession(widget.sessionID, message.info.id);
      },
    );
    if (!mounted || undone != true) return;
    try {
      // The undo boundary comes from the session; read it now rather than
      // wait for its event, so the undone turns hide at once.
      await _conn.ensureSession(widget.sessionID);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// The server's messages after [message] in the loaded transcript, or null
  /// when [message] is not in it. History loads newest first, so a loaded
  /// prompt has everything after it loaded too.
  List<MessageWithParts>? _messagesAfter(MessageWithParts message) {
    final index = _messages.indexWhere((m) => m.info.id == message.info.id);
    if (index < 0) return null;
    return [
      for (final m in _messages.skip(index + 1))
        if (!m.info.id.startsWith('local-')) m,
    ];
  }

  Future<void> _reviewStagedRevert() => Navigator.of(context).push<void>(
    KitPageRoute<void>(
      builder: (_) =>
          StagedRevertScreen(controller: _conn, sessionID: widget.sessionID),
    ),
  );

  Future<void> _retryLast() async {
    final strings = _chatL10n(context);
    if (_sending) return;
    MessageWithParts? target;
    for (final message in _visibleHistory.toList().reversed) {
      if (message.info.role == 'user' &&
          !message.info.id.startsWith('local-')) {
        target = message;
        break;
      }
    }
    final text =
        target?.parts
            .where((part) => part.type == 'text')
            .map((part) => part.text)
            .join('\n') ??
        '';
    final files =
        target?.parts.where((part) => part.type == 'file').toList() ??
        const <Part>[];
    if (text.trim().isEmpty && files.isEmpty) return;
    if (!_supportsPromptAttachments && files.isNotEmpty) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    final attachments = <PromptAttachment>[];
    for (final file in files) {
      final url = file.url;
      if (url == null || url.isEmpty) {
        _showActionError(strings.chatUiThisPromptCannotBeRetriedBecauseAn);
        return;
      }
      final filename = file.filename?.isNotEmpty == true
          ? file.filename!
          : 'attachment';
      attachments.add(
        PromptAttachment(
          mime: file.mime?.isNotEmpty == true
              ? file.mime!
              : _mimeForFilename(filename),
          filename: filename,
          url: url,
        ),
      );
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw ProductException(strings.chatUiOpenCodeIsReconnecting);
      }
      await _conn.waitForSessionSelection(widget.sessionID, expectedApi: api);
      await api.promptAsync(
        widget.sessionID,
        text: text,
        model: _conn.modelForSession(widget.sessionID),
        agent: _conn.agentForSession(widget.sessionID).isEmpty
            ? null
            : _conn.agentForSession(widget.sessionID),
        variant: _conn.variantForSession(widget.sessionID).isEmpty
            ? null
            : _conn.variantForSession(widget.sessionID),
        attachments: attachments,
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  void _showActionError(Object error) {
    showProductError(context, error);
  }

  // ----- dialogs -----

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

  SessionRetryState? get _retryState => _conn.retryStates[widget.sessionID];

  /// Starts the one-second countdown ticker when the session enters a retry
  /// backoff and cancels it as soon as the backoff clears, so an idle chat
  /// never pays for a periodic rebuild.
  void _syncRetryTicker() {
    final retry = _retryState;
    if (retry == null || retry.next == null) {
      _retryTicker?.cancel();
      _retryTicker = null;
      return;
    }
    _retryTicker ??= Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      if (_retryState == null) {
        _syncRetryTicker();
        return;
      }
      setState(() {});
    });
  }

  /// The finish haptic ([KitHaptics.done]) when this session goes from
  /// busy to idle: the reply the person waited for is in. Keeps the editor
  /// in place; skipped under reduced motion.
  void _noteRunFinished() {
    if (_conn.isIsolated) return;
    final busy = _conn.busySessions.contains(widget.sessionID);
    final finished = _wasBusy && !busy;
    _wasBusy = busy;
    if (!finished) return;
    // The server's own idle ends the turn this phone started.
    _localTurnSince = null;
    KitHaptics.done(context);
  }

  /// Shows a composer-local note above the field for three seconds. Used
  /// for outcomes about the draft itself (queued, staged, already present)
  /// so they never cover the field as a snackbar would.
  void _showComposerNote(String text, {Key? key}) {
    if (!mounted) return;
    _composerNoteTimer?.cancel();
    setState(() {
      _composerNote = text;
      _composerNoteKey = key;
    });
    _composerNoteTimer = Timer(const Duration(seconds: 3), () {
      if (!mounted) return;
      setState(() {
        _composerNote = null;
        _composerNoteKey = null;
      });
    });
  }

  /// Once per session: true while the first staged attachments of this
  /// session are showing, false afterwards.
  bool _attachmentNoteVisible() {
    if (_attachments.isEmpty) {
      if (_attachmentNoteActive) {
        _attachmentNoteActive = false;
        _attachmentNoteShownSessions.add(widget.sessionID);
      }
      return false;
    }
    if (!_supportsPromptAttachments) return true;
    if (_attachmentNoteActive) return true;
    if (_attachmentNoteShownSessions.contains(widget.sessionID)) return false;
    _attachmentNoteActive = true;
    return true;
  }

  /// Opens the form renderer from the inline card. Forms no longer
  /// auto-present: the card above the composer is the entry point, so an
  /// arriving form never steals the keyboard.
  Future<void> _openForm(Api2FormInfo form) async {
    if (_activeFormID != null) return;
    _activeFormID = form.id;
    try {
      await presentConnectionForm(context, _conn, form);
    } finally {
      _activeFormID = null;
    }
  }

  /// Confirms a reconnect flush that delivered queued drafts, closing the
  /// loop the "Queued — will send when reconnected" note opened. Also names
  /// drafts the flush deliberately left for other servers.
  void _announceCompletedFlush() {
    if (_offlineFlushRevision == _conn.offlineFlushRevision) return;
    _offlineFlushRevision = _conn.offlineFlushRevision;
    final sent = _conn.lastFlushedPromptCount;
    if (sent <= 0) return;
    final waiting = _conn.lastFlushSkippedForOtherProfiles;
    final message = StringBuffer(_chatL10n(context).chatUiQueuedSent(sent));
    if (waiting > 0) {
      message.write(_chatL10n(context).chatUiOtherDraftsWaitingSuffix(waiting));
    }
    // About the person's own drafts, so it sits by the composer.
    _showComposerNote(message.toString());
  }

  /// The Review path from the attention card: the full permission sheet,
  /// now dismissible — closing it leaves the card in place.
  Future<void> _showPermissionDialog(PermissionRequest permission) async {
    if (_activePermissionID != null) return;
    _activePermissionID = permission.id;
    final tool = permission.tool;
    try {
      await showPermissionSheet(
        context,
        permission: permission,
        controller: _conn,
        onShowSource: tool == null
            ? null
            : () => _jumpToMessage(tool.messageID),
      );
    } finally {
      _activePermissionID = null;
    }
  }

  /// The inline question card's answer path: the same controller call the
  /// Activity sheet's Send answers makes, so the server sees one contract.
  Future<void> _answerQuestion(
    PendingQuestion question,
    List<List<String>> answers,
  ) async {
    if (_questionReplying) return;
    setState(() => _questionReplying = true);
    try {
      await _conn.answerQuestion(question.id, answers);
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _questionReplying = false);
    }
  }

  /// More / Answer on the question card: the full sheet Activity uses.
  Future<void> _showQuestionSheet(PendingQuestion question) =>
      showQuestionSheet(context, _conn, question);

  /// "Run shell command" (map chat-run-shell-dialog): one command, run by
  /// this conversation's agent in its project. It runs from inside the
  /// dialog, so a failure stays under the field with the command kept.
  /// [initial] prefills it: a shell step's "Run this command again" opens
  /// the dialog with that command, to read or change before it runs.
  Future<void> _runShellDialog({String? initial}) async {
    if (_conn.isIsolated || !_conn.capabilities.terminal) return;
    final strings = _chatL10n(context);
    await showKitInputDialog(
      context,
      title: strings.chatUiRunShellCommand,
      label: strings.chatRunShellLabel,
      hint: strings.chatRunShellHint,
      helper: strings.chatRunShellHelper,
      confirmLabel: strings.commandRun,
      kind: KitFieldKind.mono,
      // A command reads whole: it wraps to up to four lines instead of
      // scrolling its start out of view (review board: run-shell dialog).
      maxLines: 4,
      initial: initial,
      dialogKey: const ValueKey('run-shell-dialog'),
      fieldKey: const ValueKey('run-shell-command'),
      confirmKey: const ValueKey('run-shell-confirm'),
      validate: (value) =>
          value.trim().isEmpty ? strings.chatRunShellEmpty : null,
      onSubmit: (value) async {
        try {
          await _runShellCommand(value.trim());
          return null;
        } catch (error) {
          return productErrorText(error, l10n: strings);
        }
      },
    );
  }

  Future<void> _runShellCommand(String command) async {
    final reconnecting = _chatL10n(context).chatUiOpenCodeIsReconnecting;
    final api = await _conn.prepareActionTransport();
    if (api == null) throw ProductException(reconnecting);
    await _conn.waitForSessionSelection(widget.sessionID, expectedApi: api);
    final agent = _conn.agentForSession(widget.sessionID);
    final variant = _conn.variantForSession(widget.sessionID);
    await api.shell(
      widget.sessionID,
      command: command,
      agent: agent.isNotEmpty ? agent : 'build',
      model: _conn.modelForSession(widget.sessionID),
      variant: variant.isEmpty ? null : variant,
    );
  }

  /// A shell step's "Run this command again"; null where this server has
  /// no shell for the conversation.
  ValueChanged<String>? get _rerunShellCommand =>
      _conn.isIsolated || !_conn.capabilities.terminal
      ? null
      : (command) => unawaited(_runShellDialog(initial: command));

  Future<void> _loadServerCommands() {
    if (_conn.isIsolated || !_conn.capabilities.slashCommands) {
      return Future.value();
    }
    final existing = _serverCommandsRequest;
    if (existing != null) return existing;
    late final Future<void> request;
    request = _performLoadServerCommands().whenComplete(() {
      if (identical(_serverCommandsRequest, request)) {
        _serverCommandsRequest = null;
      }
    });
    _serverCommandsRequest = request;
    return request;
  }

  Future<void> _performLoadServerCommands() async {
    setState(() {
      _serverCommandsLoading = true;
      _serverCommandsError = null;
    });
    try {
      final repository = await _conn.prepareActionRepository();
      if (!mounted) return;
      if (repository == null) {
        // Looked up after the first await: this runs from initState, where
        // inherited localizations are not yet available.
        throw ProductException(
          _chatL10n(context).chatUiOpenCodeCommandsAreUnavailableOffline,
        );
      }
      final commands = [...await repository.listCommands()];
      if (!mounted) return;
      commands.sort((a, b) => a.name.compareTo(b.name));
      setState(() => _serverCommands = commands);
    } catch (error) {
      if (mounted) setState(() => _serverCommandsError = error);
    } finally {
      if (mounted) setState(() => _serverCommandsLoading = false);
    }
  }

  /// The app's actions this server can run, then its own commands (only
  /// where the server lists them: [ServerCapabilities.slashCommands]).
  List<_ChatCommand> get _chatCommands {
    final supported = _builtinChatCommands
        .where(_chatCommandSupported)
        .toList();
    if (!_conn.capabilities.slashCommands) return supported;
    final dynamic = serverCommandEntries(
      _chatL10n(context),
      _serverCommands ?? const <CommandInfo>[],
      serverName: _conn.profile?.name,
    );
    return [...supported, ...dynamic];
  }

  /// The app's actions this server cannot run, each with the capability
  /// that says why (P7.4, explain instead of vanish): the launcher explains
  /// them in place of the rows. Only gates the capability registry can
  /// explain; the others (moving a conversation, forking, sharing) stay out
  /// of the list as before.
  List<(_ChatCommand, String)> get _unavailableChatCommands => [
    for (final command in _builtinChatCommands)
      if (!_chatCommandSupported(command))
        if (_missingCapability(command.action) case final capability?)
          (command, capability),
  ];

  static String? _missingCapability(Object? action) => switch (action) {
    _ChatCommandAction.files ||
    _ChatCommandAction.terminal ||
    _ChatCommandAction.references ||
    _ChatCommandAction.workspaces ||
    _ChatCommandAction.projectHealth => 'flag:fileBrowsing+terminal',
    _ChatCommandAction.diff => 'flag:sessionDiff',
    _ChatCommandAction.integrations ||
    _ChatCommandAction.mcpServers ||
    _ChatCommandAction.skills => 'flag:serverCatalog',
    _ => null,
  };

  List<_ChatCommand> get _builtinChatCommands {
    final session = _conn.sessionsById[widget.sessionID];
    final hasUserMessage = _visibleHistory.any(
      (message) =>
          message.info.role == 'user' && !message.info.id.startsWith('local-'),
    );
    return <_ChatCommand>[
      CommandSheetEntry.app(
        slash: 'new',
        aliases: const ['clear'],
        title: _chatL10n(context).workspaceNewSession,
        description: _chatL10n(context).chatUiStartACleanSessionInThisWorkspace,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.newSession,
      ),
      CommandSheetEntry.app(
        slash: 'sessions',
        aliases: const ['resume', 'continue'],
        title: _chatL10n(context).usageSessions,
        description: _chatL10n(
          context,
        ).chatUiFindSessionsAcrossEveryOpenCodeProject,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.sessions,
      ),
      CommandSheetEntry.app(
        slash: 'workspaces',
        aliases: const ['workspace'],
        title: _chatL10n(context).chatUiProjectsAndWorkspaces,
        description: _chatL10n(context).chatUiSwitchProjectDirectoryOrWorktree,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.workspaces,
      ),
      CommandSheetEntry.app(
        slash: 'move',
        title: _chatL10n(context).chatUiMoveSession,
        description: _chatL10n(
          context,
        ).chatUiMoveThisSessionToAnotherProjectDirectory,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.move,
      ),
      // §7 row 5: warping a session into a managed workspace has no v2
      // equivalent, so the command leaves the palette rather than failing.
      if (_conn.capabilities.workspaceWarp)
        CommandSheetEntry.app(
          slash: 'warp',
          title: _chatL10n(context).chatUiMoveSession,
          description: _chatL10n(
            context,
          ).chatUiChangeThisSessionSExperimentalWorkspace,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.warp,
        ),
      CommandSheetEntry.app(
        slash: 'editor',
        title: _chatL10n(context).chatUiPromptEditor,
        description: _chatL10n(context).chatUiEditTheCurrentPromptInAFocused,
        group: _chatL10n(context).chatUiCompose,
        action: _ChatCommandAction.promptEditor,
      ),
      CommandSheetEntry.app(
        slash: 'files',
        aliases: const ['open'],
        title: _chatL10n(context).chatUiProjectFiles,
        description: _chatL10n(
          context,
        ).chatUiBrowsePreviewDownloadAndAttachProjectFiles,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.files,
      ),
      CommandSheetEntry.app(
        slash: 'health',
        title: _chatL10n(context).chatUiProjectHealth,
        description: _chatL10n(
          context,
        ).chatUiInspectGitLanguageServicesAndFormattersFor,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.projectHealth,
      ),
      CommandSheetEntry.app(
        slash: 'terminal',
        title: _chatL10n(context).libraryTerminalTitle,
        description: _chatL10n(context).chatUiOpenPersistentWorkspaceTerminals,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.terminal,
      ),
      CommandSheetEntry.app(
        slash: 'models',
        aliases: const ['model', 'mo'],
        title: _chatL10n(context).chatUiModel,
        description: _chatL10n(context).chatUiChooseAServerModelByProviderAnd,
        group: _chatL10n(context).chatUiModelAndAgent,
        action: _ChatCommandAction.model,
      ),
      CommandSheetEntry.app(
        slash: 'agents',
        aliases: const ['agent'],
        title: _chatL10n(context).chatUiAgent,
        description: _chatL10n(context).chatUiChooseTheActiveOpenCodeAgent,
        group: _chatL10n(context).chatUiModelAndAgent,
        action: _ChatCommandAction.model,
      ),
      CommandSheetEntry.app(
        slash: 'variants',
        title: _chatL10n(context).modelThinkingMode,
        description: _chatL10n(
          context,
        ).chatUiChooseTheCurrentModelVariantOrReasoning,
        group: _chatL10n(context).chatUiModelAndAgent,
        action: _ChatCommandAction.model,
      ),
      CommandSheetEntry.app(
        slash: 'mcps',
        aliases: const ['mcp'],
        title: _chatL10n(context).chatUiMCPServers,
        description: _chatL10n(
          context,
        ).chatUiInspectMCPStatusAuthenticationAndResources,
        group: 'OpenCode',
        action: _ChatCommandAction.mcpServers,
      ),
      CommandSheetEntry.app(
        slash: 'connect',
        title: _chatL10n(context).chatUiConnectProvider,
        description: _chatL10n(
          context,
        ).chatUiManageProviderAndIntegrationAuthentication,
        group: 'OpenCode',
        action: _ChatCommandAction.integrations,
      ),
      // §7 row 8.
      if (_conn.capabilities.consoleOrganizations)
        CommandSheetEntry.app(
          slash: 'org',
          aliases: const ['orgs', 'switch-org'],
          title: _chatL10n(context).chatUiSwitchOrganization,
          description: _chatL10n(
            context,
          ).chatUiChangeTheActiveOpenCodeConsoleOrganization,
          group: 'OpenCode',
          action: _ChatCommandAction.organization,
        ),
      CommandSheetEntry.app(
        slash: 'skills',
        title: _chatL10n(context).chatUiSkills,
        description: _chatL10n(context).chatUiBrowseProjectAndGlobalSkills,
        group: 'OpenCode',
        action: _ChatCommandAction.skills,
      ),
      // §7 row 20: no tool inventory endpoint, so the destination goes too.
      if (_conn.capabilities.toolInventory)
        CommandSheetEntry.app(
          slash: 'tools',
          title: _chatL10n(context).chatUiToolsAndCapabilities,
          description: _chatL10n(
            context,
          ).chatUiInspectToolsCallableByTheActiveProvider,
          group: 'OpenCode',
          action: _ChatCommandAction.tools,
        ),
      CommandSheetEntry.app(
        slash: 'references',
        aliases: const ['reference', 'refs'],
        title: _chatL10n(context).chatUiProjectReferences,
        description: _chatL10n(
          context,
        ).chatUiAddAnOpenCodeProjectReferenceToThis,
        group: 'OpenCode',
        action: _ChatCommandAction.references,
      ),
      CommandSheetEntry.app(
        slash: 'status',
        title: _chatL10n(context).chatUiServerStatus,
        description: _chatL10n(
          context,
        ).chatUiConnectionHealthServerVersionAndLiveMode,
        group: 'OpenCode',
        action: _ChatCommandAction.status,
      ),
      CommandSheetEntry.app(
        slash: 'debug',
        title: _chatL10n(context).chatUiAppDiagnostics,
        description: _chatL10n(context).chatUiReviewHandledAppErrorsAndSendA,
        group: 'OpenCode',
        action: _ChatCommandAction.diagnostics,
      ),
      CommandSheetEntry.app(
        slash: 'themes',
        aliases: const ['theme'],
        title: _chatL10n(context).chatUiAppearance,
        description: _chatL10n(
          context,
        ).chatUiFollowAndroidOrChooseTheNativeLight,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.appearance,
      ),
      CommandSheetEntry.app(
        slash: 'diff',
        title: _chatL10n(context).chatUiSessionChanges,
        description: _chatL10n(context).chatUiReviewTheActualDiffForThisSession,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.diff,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
      CommandSheetEntry.app(
        slash: 'context',
        aliases: const ['usage'],
        title: _chatL10n(context).chatUiSessionContext,
        description: _chatL10n(
          context,
        ).chatUiInspectCurrentTokensCacheCostAndContext,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.context,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: _messages.any(
          (message) =>
              message.info.role == 'assistant' && message.info.tokens.total > 0,
        ),
      ),
      // §7 rows 10–11.
      if (_conn.capabilities.sessionShare) ...[
        CommandSheetEntry.app(
          slash: 'share',
          title: _shareUrl == null
              ? _chatL10n(context).chatUiShareSession
              : _chatL10n(context).chatUiCopyShareLink,
          description: _chatL10n(context).chatUiCreateOrCopyAPublicSessionLink,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.share,
          // The conversation menu is its one home (P10.2); typing it
          // still works.
          listed: false,
        ),
        CommandSheetEntry.app(
          slash: 'unshare',
          title: _chatL10n(context).chatUiStopSharing,
          description: _chatL10n(
            context,
          ).chatUiDisableTheCurrentPublicSessionLink,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.unshare,
          // The conversation menu is its one home (P10.2); typing it
          // still works.
          listed: false,
          enabled: _shareUrl != null,
        ),
      ],
      CommandSheetEntry.app(
        slash: 'rename',
        title: _chatL10n(context).chatUiRenameSession,
        description: _chatL10n(context).chatUiChangeTheTitleShownInTheSession,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.rename,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
      CommandSheetEntry.app(
        slash: 'timeline',
        aliases: const ['messages'],
        title: _chatL10n(context).chatUiMessageTimeline,
        description: _chatL10n(context).chatUiFindAMessageJumpToItOr,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.timeline,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: _messages.isNotEmpty,
      ),
      CommandSheetEntry.app(
        slash: 'fork',
        title: _chatL10n(context).chatUiForkSession,
        description: _chatL10n(context).sessionMenuForkHint,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.fork,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: hasUserMessage,
      ),
      CommandSheetEntry.app(
        slash: 'compact',
        aliases: const ['summarize'],
        title: _chatL10n(context).chatUiCompactContext,
        description: _chatL10n(
          context,
        ).chatUiSummarizeTheSessionUsingTheSelectedModel,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.compact,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: hasUserMessage,
      ),
      CommandSheetEntry.app(
        slash: 'thinking',
        aliases: const ['toggle-thinking'],
        title: _conn.transcriptReasoningExpanded
            ? _chatL10n(context).chatUiCollapseReasoning
            : _chatL10n(context).chatUiExpandReasoning,
        description: _chatL10n(
          context,
        ).chatUiToggleLongReasoningDetailsAcrossTheTranscript,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.thinking,
      ),
      CommandSheetEntry.app(
        slash: 'timestamps',
        aliases: const ['toggle-timestamps'],
        title: _conn.transcriptTimestampsVisible
            ? _chatL10n(context).chatUiHideTimestamps
            : _chatL10n(context).chatUiShowTimestamps,
        description: _chatL10n(
          context,
        ).chatUiToggleCreationTimesBesideTranscriptEntries,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.timestamps,
      ),
      CommandSheetEntry.app(
        slash: 'undo',
        title: _chatL10n(context).chatUiRevertLastPrompt,
        description: _chatL10n(context).revertUndoDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.undo,
        enabled:
            hasUserMessage &&
            session?.reverted != true &&
            !_conn.sessionRevertSaving(widget.sessionID) &&
            !_conn.busySessions.contains(widget.sessionID),
      ),
      CommandSheetEntry.app(
        slash: 'redo',
        title: _conn.supportsStagedRevert
            ? _chatL10n(context).revertClearAction
            : _chatL10n(context).chatUiRestoreRevertedPrompt,
        description: _conn.supportsStagedRevert
            ? _chatL10n(context).revertClearShortDescription
            : _chatL10n(context).chatUiRestoreTheCurrentlyRevertedSessionState,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.redo,
        enabled: session?.reverted == true,
      ),
      CommandSheetEntry.app(
        slash: 'copy',
        title: _chatL10n(context).chatUiCopyTranscript,
        description: _chatL10n(
          context,
        ).chatUiCopyTheRenderedConversationAsMarkdown,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.copy,
      ),
      CommandSheetEntry.app(
        slash: 'export',
        title: _chatL10n(context).chatUiExportTranscript,
        description: _chatL10n(
          context,
        ).chatUiSaveTheConversationAsAMarkdownFile,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.export,
      ),
      CommandSheetEntry.app(
        slash: 'shell',
        aliases: const ['!'],
        title: _chatL10n(context).chatUiRunShellCommand,
        description: _chatL10n(context).commandSheetShellDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.shell,
      ),
      CommandSheetEntry.app(
        slash: 'retry',
        title: _chatL10n(context).chatUiRetryLastPrompt,
        description: _chatL10n(context).commandSheetRetryDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.retry,
        enabled: hasUserMessage && !_sending,
      ),
      CommandSheetEntry.app(
        slash: 'note',
        title: _chatL10n(context).sessionNoteTitle,
        description: _chatL10n(context).commandSheetNoteDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.note,
      ),
      CommandSheetEntry.app(
        slash: 'approvals',
        title: _chatL10n(context).approvalsUiMenu,
        description: _chatL10n(context).commandSheetApprovalsDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.approvals,
      ),
      CommandSheetEntry.app(
        slash: 'plan',
        aliases: const ['todos'],
        title: _chatL10n(context).chatUiTodos,
        description: _chatL10n(context).commandSheetPlanDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.plan,
        enabled: _latestPlan != null,
      ),
      CommandSheetEntry.app(
        slash: 'reload',
        title: _chatL10n(context).chatUiReloadMessages,
        description: _chatL10n(context).commandSheetReloadDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.reload,
      ),
      CommandSheetEntry.app(
        slash: 'help',
        title: _chatL10n(context).chatUiCommandMap,
        description: _chatL10n(
          context,
        ).chatUiSearchMobileActionsAndServerProvidedCommands,
        group: 'OpenCode',
        action: _ChatCommandAction.help,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
    ];
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

  Future<void> _cycleModel({
    bool reverse = false,
    bool favoritesOnly = false,
  }) async {
    if (_conn.isIsolated) return;
    final revision = _conn.connectionRevision;
    try {
      final next = await _conn.cycleModelForSession(
        widget.sessionID,
        reverse: reverse,
        favoritesOnly: favoritesOnly,
      );
      if (!mounted || revision != _conn.connectionRevision) return;
      // By the composer, whose model chip changes with it; a newer cycle
      // replaces the note.
      _showComposerNote(
        next == null
            ? _chatL10n(context).chatUiChooseAnotherModelInThePickerTo
            : _chatL10n(
                context,
              ).chatUiNextTurnsModel(_presentedModelLabel ?? ''),
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Widget? _modelCycleButton() {
    if (_conn.isIsolated) return null;
    final library = _conn.modelLibrary;
    final current = _conn.modelForSession(widget.sessionID);
    final hasRecent =
        library.next(current, available: _conn.modelAvailable) != null;
    final hasFavorites =
        library.next(
          current,
          favoritesOnly: true,
          available: _conn.modelAvailable,
        ) !=
        null;
    if (!hasRecent && !hasFavorites) return null;
    return ModelCycleButton(
      onCycle: _cycleModel,
      hasRecent: hasRecent,
      hasFavorites: hasFavorites,
    );
  }

  /// The command sheet (slice-P10.1), the same one Settings › Tools ›
  /// Commands shows: this server's commands and the app's, and the
  /// subagents a prompt can be handed to. A pick runs in this conversation.
  Future<void> _openCommandLauncher({
    CommandSheetTab initialTab = CommandSheetTab.commands,
  }) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAgentMentions && initialTab == CommandSheetTab.agents) {
      return;
    }
    FocusManager.instance.primaryFocus?.unfocus();
    if (_conn.capabilities.serverCatalog) {
      unawaited(_conn.refreshCatalog());
    }
    await showKitFramedSheet<void>(
      context,
      useSafeArea: true,
      maxWidth: 720,
      builder: (sheetContext) => CommandSheet(
        controller: _conn,
        initialTab: initialTab,
        commands: () => _chatCommands,
        unavailable: () => _unavailableChatCommands,
        agents: () => _subagents,
        loading: () => _serverCommandsLoading,
        loaded: () => _serverCommands != null,
        error: () => _serverCommandsError,
        onRefresh: _loadServerCommands,
        onSelected: (command) {
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _selectChatCommand(command);
        },
        onAgentSelected: (agent) {
          if (!_supportsPromptAgentMentions) return;
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _insertAgentMention(agent);
        },
      ),
    );
  }

  void _selectChatCommand(_ChatCommand command) {
    if (!command.enabled || !_chatCommandSupported(command)) return;
    if (command.serverCommand case final serverCommand?) {
      _composer.value = TextEditingValue(
        text: '/${serverCommand.name} ',
        selection: TextSelection.collapsed(
          offset: serverCommand.name.length + 2,
        ),
      );
      _focus.requestFocus();
      return;
    }
    if (_composer.text.trimLeft().startsWith('/')) _composer.clear();
    unawaited(_runMobileCommand(command.action! as _ChatCommandAction));
  }

  Future<void> _runMobileCommand(_ChatCommandAction action) async {
    try {
      await _executeMobileCommand(action);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _executeMobileCommand(_ChatCommandAction action) async {
    final strings = _chatL10n(context);
    switch (action) {
      case _ChatCommandAction.newSession:
        if (!await _persistDraft() || !mounted) return;
        final session = await _conn.createSession();
        if (mounted) {
          await _discardUntouchedMobileSession();
          if (!mounted) return;
          await _conn.refreshSessions();
          if (mounted) {
            Navigator.of(context).pushReplacementNamed(
              '/chat/${session.id}',
              arguments: const ChatRouteArguments.newlyCreated(),
            );
          }
        }
        return;
      case _ChatCommandAction.sessions:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => GlobalSessionsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.workspaces:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(builder: (_) => const HomeScreen(initialTab: 0)),
          );
        }
        return;
      case _ChatCommandAction.files:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => KitScreen(
                topBar: KitTopBar(title: strings.chatUiProjectFiles),
                body: FilesScreen(
                  controller: _conn,
                  onAttachFile: _attachProjectFile,
                  onReviewPrompt: _addReviewPrompt,
                  handoff: _handoff, // UX-103 review handoff
                ),
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.projectHealth:
        final repository = await _conn.prepareActionRepository();
        if (repository == null) {
          throw ProductException(strings.chatUiOpenCodeIsReconnectingTryAgain);
        }
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ProjectHealthScreen(
                repository: repository,
                repositoryResolver: _conn.prepareActionRepository,
                capabilities: _conn.capabilities,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.move:
        if (mounted) {
          await showSessionDestinationSheet(
            context,
            controller: _conn,
            sessionID: widget.sessionID,
            mode: SessionDestinationMode.move,
          );
        }
        return;
      case _ChatCommandAction.warp:
        if (mounted) {
          await showSessionDestinationSheet(
            context,
            controller: _conn,
            sessionID: widget.sessionID,
            mode: SessionDestinationMode.warp,
          );
        }
        return;
      case _ChatCommandAction.promptEditor:
        await _openPromptEditor();
        return;
      case _ChatCommandAction.terminal:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => TerminalScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.model:
        if (mounted) {
          await showModelPicker(
            context,
            applyScope: _modelApplyScope,
            sessionID: widget.sessionID,
          );
        }
        return;
      // "/connect" and "/mcps" land on the same screens as Settings › Agent
      // setup › Providers and › MCP, so each has one home.
      case _ChatCommandAction.integrations:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => IntegrationsScreen(
                controller: _conn,
                mode: IntegrationsMode.providers,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.mcpServers:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => IntegrationsScreen(
                controller: _conn,
                mode: IntegrationsMode.mcp,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.organization:
        if (mounted) {
          await showConsoleOrganizationSheet(context, controller: _conn);
        }
        return;
      case _ChatCommandAction.skills:
        if (mounted) {
          final location = _conn.locationRevision;
          final used = await Navigator.of(context).push<bool>(
            KitPageRoute<bool>(
              builder: (_) => SkillsScreen(
                controller: _conn,
                sessionID: _conn.supportsSessionSkills
                    ? widget.sessionID
                    : null,
              ),
            ),
          );
          if (mounted && used == true && _conn.locationRevision == location) {
            _showComposerNote(strings.skillApplied);
            await _load();
          }
        }
        return;
      case _ChatCommandAction.tools:
        // Same destination as Settings › Agent setup › Commands & tools,
        // opened on its Tools tab (index 1 whenever the inventory exists,
        // which is also the gate for this command).
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) =>
                  CapabilitiesScreen(controller: _conn, initialTab: 1),
            ),
          );
        }
        return;
      case _ChatCommandAction.references:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ReferencesScreen(
                controller: _conn,
                onSelected: _attachReference,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.status:
        // "/status" means this server's health, not all of Settings: open
        // the hub's Connection › This server screen.
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ServerSettingsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.diagnostics:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => AppDiagnosticsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.appearance:
        // Settings › Appearance, the one home of theme and language.
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => AppearanceSettingsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.diff:
        _showDiff();
        return;
      case _ChatCommandAction.context:
        await _showContext();
        return;
      case _ChatCommandAction.share:
        if (_shareUrl case final url?) {
          await _copyShareLink(url);
        } else {
          await _share();
        }
        return;
      case _ChatCommandAction.unshare:
        await _stopSharing();
        return;
      case _ChatCommandAction.rename:
        await _renameCurrentSession();
        return;
      case _ChatCommandAction.timeline:
        await _openTimeline();
        return;
      // Fork lands in one place (P10.2): the copy opens at once, from the
      // menu, "/fork" and a prompt's own Fork alike.
      case _ChatCommandAction.fork:
        await _fork();
        return;
      case _ChatCommandAction.compact:
        await _compact();
        return;
      case _ChatCommandAction.thinking:
        await _toggleReasoningDisplay();
        return;
      case _ChatCommandAction.timestamps:
        await _toggleTimestampDisplay();
        return;
      case _ChatCommandAction.undo:
        await _revertLast();
        return;
      case _ChatCommandAction.redo:
        await _restore();
        return;
      case _ChatCommandAction.copy:
        await _copyTranscript();
        return;
      case _ChatCommandAction.export:
        await _exportTranscript();
        return;
      case _ChatCommandAction.help:
        await _openCommandLauncher();
        return;
      case _ChatCommandAction.shell:
        await _runShellDialog();
        return;
      case _ChatCommandAction.retry:
        await _retryLast();
        return;
      case _ChatCommandAction.note:
        await _openSessionNote();
        return;
      case _ChatCommandAction.approvals:
        await showSessionApprovalsSheet(
          context,
          controller: _conn,
          sessionID: widget.sessionID,
        );
        return;
      case _ChatCommandAction.reload:
        await _load();
        return;
      case _ChatCommandAction.plan:
        _openPlan();
        return;
    }
  }

  void _attachReference(ReferenceInfo reference) {
    if (!_conn.capabilities.fileBrowsing) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final attachment = PromptAttachment.reference(
      name: reference.name,
      path: reference.path,
    );
    if (_attachments.any(
      (candidate) =>
          candidate.isDirectoryReference && candidate.url == attachment.url,
    )) {
      _showComposerNote(
        _chatL10n(context).chatUiReferenceAlreadyAdded(reference.name),
      );
      _focus.requestFocus();
      return;
    }
    final current = _composer.text.trimRight();
    final mention = '@${reference.name}';
    final text = current.isEmpty ? mention : '$current $mention';
    setState(() {
      _attachments.add(attachment);
      _composer.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    });
    _focus.requestFocus();
  }

  /// "Rename conversation" (map chat-rename-session-dialog). The rename
  /// runs from inside the dialog, so a failure stays under the field with
  /// the new title kept.
  Future<void> _renameCurrentSession() async {
    final strings = _chatL10n(context);
    final current = _conn.sessionsById[widget.sessionID]?.title ?? '';
    await showKitInputDialog(
      context,
      title: strings.chatUiRenameSession,
      label: strings.chatUiTitle,
      confirmLabel: strings.chatUiRename,
      initial: current,
      dialogKey: const ValueKey('rename-session-dialog'),
      fieldKey: const ValueKey('rename-session-title'),
      validate: (value) =>
          value.trim().isEmpty ? strings.chatRenameEmpty : null,
      onSubmit: (value) async {
        try {
          await _conn.renameSession(widget.sessionID, value.trim());
          await _conn.refreshSessions();
          return null;
        } catch (error) {
          return productErrorText(error, l10n: strings);
        }
      },
    );
  }

  /// The conversation as Markdown for Copy transcript (SEC-13, G12): the
  /// person's own prompts stay as they typed them; everything else (the
  /// title, replies, reasoning, tool output, attachments' names and error
  /// text) is masked through [KitRedact] first, so a key a tool printed
  /// never reaches the clipboard.
  String _transcriptMarkdown() {
    final l10n = _chatL10n(context);
    final title = _conn.sessionsById[widget.sessionID]?.title;
    final out = StringBuffer();
    void put(String text) => out.write(KitRedact.text(text));
    put(
      '# ${title?.isNotEmpty == true ? title : l10n.chatUiOpenCodeSession}\n',
    );
    if (_olderCursor != null) {
      out.write('\n> ${l10n.historyLoadedOnly}\n');
    }
    for (final message in _visibleHistory) {
      if (message.info.id.startsWith('local-')) continue;
      final own = message.info.role == 'user';
      out.write(
        '\n## ${message.info.role == 'assistant' ? l10n.chatUiAssistant : l10n.chatUiUser}\n\n',
      );
      for (final part in message.parts) {
        if (part.type == 'text' && part.text.trim().isNotEmpty) {
          // The person's own words, verbatim; a reply is masked.
          if (own && !part.synthetic) {
            out.write('${part.text.trim()}\n\n');
          } else {
            put('${part.text.trim()}\n\n');
          }
        } else if (part.type == 'reasoning' && part.text.trim().isNotEmpty) {
          put(
            '<details><summary>${l10n.transcriptFindReasoning}</summary>\n\n${part.text.trim()}\n\n</details>\n\n',
          );
        } else if (part.type == 'file') {
          put(
            '- ${l10n.chatUiAttachment}: ${part.filename ?? part.url ?? l10n.chatUiFile}\n',
          );
        } else if (part.type == 'tool') {
          put(
            '### ${l10n.chatUiTool}: ${part.toolName ?? l10n.chatUiTool}\n\n',
          );
          final output = part.toolState.output?.trim();
          if (output?.isNotEmpty == true) {
            put('```text\n$output\n```\n\n');
          }
        }
      }
      if (message.info.errorText case final error?) {
        put('> ${l10n.chatUiError}: $error\n');
      }
    }
    return out.toString().trimRight();
  }

  /// Already masked where it is not the person's own ([_transcriptMarkdown]);
  /// copied as built so their prompts stay verbatim (SEC-13).
  Future<void> _copyTranscript() => KitCopy.copy(
    context,
    _transcriptMarkdown(),
    redact: false,
    announcement: _chatL10n(context).chatUiTranscriptCopiedAsMarkdown,
  );

  Future<void> _exportTranscript() async {
    final repository = _conn.repository;
    if (repository is SessionExportGateway &&
        (repository as SessionExportGateway).sessionExportSupported) {
      await Navigator.of(context).push(
        KitPageRoute<void>(
          builder: (_) => SessionExportScreen(
            controller: _conn,
            sessionID: widget.sessionID,
            markdown: () =>
                Uint8List.fromList(utf8.encode(_transcriptMarkdown())),
          ),
        ),
      );
      return;
    }
    final path = await FilePicker.saveFile(
      dialogTitle: _chatL10n(context).chatUiExportSessionTranscript,
      fileName:
          'opencode-${widget.sessionID.substring(0, widget.sessionID.length.clamp(0, 8))}.md',
      bytes: Uint8List.fromList(utf8.encode(_transcriptMarkdown())),
    );
    if (mounted && path != null) {
      _showComposerNote(_chatL10n(context).chatUiTranscriptSaved);
    }
  }

  /// The conversation menu (slice-P10.2): "Go to" (Changes, Timeline,
  /// Find, Subagents, Details) and "Do" (Share, Compact, Fork, Rename,
  /// Continue on computer, Open on another phone), one [KitMenuItem] list in
  /// the title bar's overflow. A Work row's menu is built by the same
  /// [sessionMenuItems] and hands its conversation acts to this screen
  /// ([ChatScreen.menuAction]). The app's other actions are commands, in
  /// the command sheet.
  List<KitMenuItem> _sessionMenu({required bool shared}) {
    if (_conn.isIsolated || _watching) return const [];
    final hasPrompt = _visibleHistory.any(
      (message) =>
          message.info.role == 'user' && !message.info.id.startsWith('local-'),
    );
    return sessionMenuItems(
      _chatL10n(context),
      SessionMenuOffer.of(
        _conn.capabilities,
        shared: shared,
        savedServer: _conn.profile != null,
        compact: _supportsSessionCompact,
        timeline: _messages.isNotEmpty,
        hasPrompt: hasPrompt,
      ),
      shortcuts: true,
      onSelected: (action) => unawaited(_runSessionMenuAction(action)),
    );
  }

  Future<void> _runSessionMenuAction(SessionMenuAction action) async {
    if (!mounted || _conn.isIsolated) return;
    switch (action) {
      case SessionMenuAction.changes:
        if (_conn.capabilities.sessionDiff) _showDiff();
      case SessionMenuAction.timeline:
        await _openTimeline();
      case SessionMenuAction.find:
        _openFind();
      case SessionMenuAction.subagents:
        await _openRunningWork();
      case SessionMenuAction.details:
        await _showContext();
      case SessionMenuAction.share:
        if (_conn.capabilities.sessionShare) await _share();
      case SessionMenuAction.unshare:
        if (_conn.capabilities.sessionShare) await _stopSharing();
      case SessionMenuAction.compact:
        if (_supportsSessionCompact) await _compact();
      case SessionMenuAction.fork:
        await _fork();
      case SessionMenuAction.rename:
        await _renameCurrentSession();
      case SessionMenuAction.continueOnComputer:
        if (_conn.capabilities.cliSessionResume) await _continueOnComputer();
      case SessionMenuAction.continueOnPhone:
        if (_conn.profile != null) await _continueOnPhone();
    }
  }

  /// A Work row's "Go to" or "Do" pick for this conversation, run once
  /// after the first history arrives (the transcript, find bar and prompts
  /// it needs are there by then).
  bool _menuActionRun = false;

  void _runRouteMenuAction() {
    final action = widget.menuAction;
    if (action == null || _menuActionRun || !mounted) return;
    _menuActionRun = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) unawaited(_runSessionMenuAction(action));
    });
  }

  /// "Note for the agent" (the command sheet's /note).
  Future<void> _openSessionNote() async {
    final location = _conn.locationRevision;
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) =>
            SessionNoteScreen(controller: _conn, sessionID: widget.sessionID),
      ),
    );
    if (mounted && _conn.locationRevision == location) setState(() {});
  }

  /// F4-S1: the terminal command that resumes this session on the computer
  /// running the server. The CLI name follows the server's product
  /// generation (the only thing the flavor is used for here — copy), the
  /// availability follows [ServerCapabilities.cliSessionResume]. The sheet
  /// only offers a copy; Export lives in the conversation menu. When the
  /// server did not report the folder, the sheet offers a reload: the
  /// conversation and its details are fetched again and the sheet reopens
  /// with what the server says now.
  Future<void> _continueOnComputer() async {
    final session = _conn.sessionsById[widget.sessionID];
    final command = SessionResumeCommand.build(
      cli: _conn.serverFlavor == ServerFlavor.v2
          ? SessionResumeCli.openCode2
          : SessionResumeCli.openCode1,
      sessionID: widget.sessionID,
      directory: session?.directory ?? _conn.directory,
      workspaceID: session?.workspaceID ?? _conn.workspace,
    );
    final action = await showContinueOnComputerSheet(
      context,
      command: command,
      offerReload: true,
    );
    if (!mounted || action != continueOnComputerReload) return;
    await _conn.refreshSessions();
    await _load();
    if (!mounted) return;
    await _continueOnComputer();
  }

  /// F4-S2: the QR / link that opens this exact session in the app on
  /// another phone. Route identifiers only, built from the saved server's
  /// id and the session id; nothing is sent.
  Future<void> _continueOnPhone() async {
    final link = SessionLink.tryCreate(
      profileID: _conn.profile?.id,
      sessionID: widget.sessionID,
    );
    await showContinueOnPhoneSheet(
      context,
      link: link,
      // P3.9: "Include this server's address", offered only where the
      // server and the address coordinator allow it (gated off for now).
      address: SessionAddressOffer.of(
        context,
        connection: _conn,
        sessionID: widget.sessionID,
      ),
    );
  }

  Future<void> _showContext() async {
    await Navigator.of(context).push(
      KitPageRoute<void>(
        builder: (_) => SessionContextScreen(
          controller: _conn,
          sessionID: widget.sessionID,
          initialMessages: List.unmodifiable(_visibleHistory),
          initialHasOlder: _olderCursor != null,
        ),
      ),
    );
  }

  Future<void> _showSubagents() async {
    if (!_conn.capabilities.projectManagement) return;
    final target = await Navigator.of(context).push<Session>(
      KitPageRoute<Session>(
        builder: (_) => SessionRelationsScreen(
          controller: _conn,
          sessionID: widget.sessionID,
        ),
      ),
    );
    if (!mounted || target == null || target.id == widget.sessionID) return;
    await _openRelatedSession(target);
  }

  /// Opens the child session a Task tool card points at (its metadata
  /// carries the subagent's session id), fetching it when the list has not
  /// caught up with a freshly spawned subagent yet.
  Future<void> _openSubagentSession(
    String sessionID, {
    bool requireChild = false,
  }) async {
    if (!_conn.capabilities.projectManagement ||
        sessionID == widget.sessionID) {
      return;
    }
    final origin = widget.sessionID;
    final location = _conn.locationRevision;
    final profileID = _conn.profile?.id;
    final scope = (_conn.profile?.baseUrl, _conn.directory, _conn.workspace);
    bool current() =>
        mounted &&
        origin == widget.sessionID &&
        location == _conn.locationRevision &&
        profileID == _conn.profile?.id &&
        scope == (_conn.profile?.baseUrl, _conn.directory, _conn.workspace);
    try {
      final repository = await _requireActionRepository();
      if (!current()) return;
      final target =
          _conn.sessionsById[sessionID] ??
          await repository.getSessionDetails(sessionID);
      if (!current() || repository != _conn.repository) return;
      if (requireChild && target.parentID != origin) return;
      await _openRelatedSession(target);
    } catch (error) {
      if (current()) _showActionError(error);
    }
  }

  Future<void> _openParentSession() async {
    if (!_conn.capabilities.projectManagement) return;
    final parentID = _conn.sessionsById[widget.sessionID]?.parentID;
    if (parentID != null) await _openSubagentSession(parentID);
  }

  Future<void> _openRelatedSession(Session target) async {
    if (_conn.isIsolated || !_conn.capabilities.projectManagement) return;
    final location = _conn.locationRevision;
    final origin = widget.sessionID;
    final identity = (_conn.profile?.id, _conn.profile?.baseUrl);
    final text = _composer.text;
    final attachments = List.of(_attachments);
    bool currentDraft() =>
        mounted &&
        origin == widget.sessionID &&
        identity == (_conn.profile?.id, _conn.profile?.baseUrl) &&
        text == _composer.text &&
        listEquals(attachments, _attachments);
    if (!await _persistDraft() ||
        !mounted ||
        location != _conn.locationRevision ||
        !currentDraft()) {
      return;
    }
    if (_conn.directory != target.directory ||
        _conn.workspace != target.workspaceID) {
      await _conn.selectLocationForExistingSession(
        directory: target.directory,
        workspace: target.workspaceID,
      );
    }
    // A competing location selection can supersede the awaited operation.
    // Its completion alone does not establish the target scope.
    if (!mounted ||
        !currentDraft() ||
        _conn.directory != target.directory ||
        _conn.workspace != target.workspaceID) {
      return;
    }
    Navigator.of(context).pushReplacementNamed('/chat/${target.id}');
  }

  Future<void> _showDiff() async {
    final strings = _chatL10n(context);
    if (!_conn.capabilities.sessionDiff) return;
    if (_conn.isIsolated) {
      final api = await _conn.prepareActionTransport();
      if (api == null) return;
      final diffs = await api.diff(widget.sessionID);
      if (!mounted) return;
      await Navigator.of(context).push<void>(
        KitPageRoute<void>(
          builder: (_) => DiffPage(diffs: diffs, allowCopy: false),
        ),
      );
      return;
    }
    final prompt = await Navigator.of(context).push<String>(
      KitPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          // P6.6a: the view picked for the person is said once per server.
          profileId: _conn.profile?.id,
          handoff: _handoff, // UX-103 review handoff
          cacheKey:
              '${_conn.profile?.id}|${_conn.directory}|${widget.sessionID}',
          // OpenCode 2 has no per-conversation diff: what it answers for
          // "this chat" is the uncommitted changes, which is its own view
          // already. Two views showing the same thing is one too many.
          loadDiffs: _conn.serverFlavor == ServerFlavor.v2
              ? null
              : () async {
                  final api = await _conn.prepareActionTransport();
                  if (api == null) {
                    throw ProductException(
                      strings.chatUiOpenCodeIsReconnecting,
                    );
                  }
                  return api.diff(widget.sessionID);
                },
          loadWorkingTreeDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw ProductException(strings.chatUiOpenCodeIsReconnecting);
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
          loadBranchDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw ProductException(strings.chatUiOpenCodeIsReconnecting);
            }
            return repository.listVcsDiffs(VcsDiffMode.branch);
          },
        ),
      ),
    );
    if (!mounted || prompt == null || prompt.trim().isEmpty) return;
    _addReviewPrompt(prompt);
  }

  // UX-103 review handoff (start).
  void _onHandoffChanged() {
    _promptContentRevision++;
    if (mounted) setState(() {});
  }

  /// Folds every staged reference into the prompt text just before it is
  /// sent. References are pointers, not attachments: they leave the composer
  /// as structured markdown the agent can read, and the chips clear with
  /// them.
  void _applyStagedReferences() {
    final references = _handoff.references;
    if (references.isEmpty) return;
    final block = ReviewReference.format(references);
    if (block.isEmpty) return;
    final current = _composer.text.trim();
    final text = current.isEmpty ? block : '$current\n\n$block';
    _composer.value = TextEditingValue(
      text: text,
      selection: TextSelection.collapsed(offset: text.length),
    );
    _handoff.store.clear(widget.sessionID);
  }

  /// Says why the chips are still there after a slash command ran, so the
  /// user does not read a surviving reference as a send that failed.
  void _noteReferencesKeptForNextPrompt() {
    if (!mounted) return;
    final count = _handoff.references.length;
    _showComposerNote(
      count == 1
          ? _chatL10n(context).chatUiReferenceKeptForYourNextPromptCommands
          : _chatL10n(context).chatUiReferencesKeptForYourNextPromptCommands,
      key: const Key('references-kept-notice'),
    );
  }

  void _removeStagedReference(ReviewReference reference) =>
      _handoff.store.remove(widget.sessionID, reference.id);
  // UX-103 review handoff (end).

  void _addReviewPrompt(String prompt) {
    if (!mounted || prompt.trim().isEmpty) return;
    final current = _composer.text.trimRight();
    final value = prompt.trim();
    final text = current.isEmpty ? value : '$current\n\n$value';
    setState(() {
      _composer.value = TextEditingValue(
        text: text,
        selection: TextSelection.collapsed(offset: text.length),
      );
    });
    _focus.requestFocus();
    _showComposerNote(_chatL10n(context).chatUiReviewCommentAddedToThePrompt);
  }

  /// One listing validates every path in the same directory, and both maps
  /// memoize futures so transcript rebuilds never re-hit the server. A
  /// confirmed file stays confirmed, but a miss only holds for
  /// [_pathLinkNegativeTtl]: agents routinely mention a path moments before
  /// creating the file, so later rebuilds must re-check.
  static const _pathLinkNegativeTtl = Duration(seconds: 20);
  final Map<String, Future<List<FileNode>>> _pathLinkDirs = {};
  final Map<String, DateTime> _pathLinkDirsAt = {};
  final Map<String, Future<bool>> _pathLinkChecks = {};
  final Map<String, DateTime> _pathLinkMissAt = {};

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

  Future<bool> _checkPathLink(String path) async {
    final slash = path.lastIndexOf('/');
    final name = slash >= 0 ? path.substring(slash + 1) : '';
    if (name.isEmpty) return false;
    final dir = slash == 0 ? '/' : path.substring(0, slash);
    try {
      final listedAt = _pathLinkDirsAt[dir];
      if (listedAt != null &&
          DateTime.now().difference(listedAt) > _pathLinkNegativeTtl) {
        _pathLinkDirs.remove(dir);
      }
      final nodes = await _pathLinkDirs.putIfAbsent(dir, () {
        _pathLinkDirsAt[dir] = DateTime.now();
        return () async {
          final api = await _conn.prepareActionTransport();
          if (api == null) throw StateError('offline');
          return api.listFiles(dir);
        }();
      });
      final found = nodes.any((node) => !node.isDir && node.name == name);
      if (!found) _pathLinkMissAt[path] = DateTime.now();
      return found;
    } catch (_) {
      // A transient failure must not brand the path dead for the whole
      // session; forget both futures so a later rebuild can retry.
      _pathLinkDirs.remove(dir);
      _pathLinkChecks.remove(path);
      return false;
    }
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

  Future<void> _attachToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    await _addPreviewAttachment(
      filename: file.displayName,
      mimeType: data.mimeType ?? file.mimeType,
      data: data,
    );
    if (!mounted) return;
    _focus.requestFocus();
    _showComposerNote(_chatL10n(context).chatUiFileAttached(file.displayName));
  }

  Future<void> _attachProjectFile(String path, FilePreviewData data) =>
      !_supportsPromptAttachments
      ? Future<void>.sync(
          () => _showComposerNote(_chatL10n(context).codexTextOnlyPrompt),
        )
      : _addPreviewAttachment(
          filename: path.split('/').last,
          mimeType: data.mimeType,
          data: data,
        );

  /// Files dropped onto the composer from the desktop file manager.
  ///
  /// Goes through the same `_addPreviewAttachment` pipeline the picker and
  /// the file viewer use, so the count, per-file and aggregate caps apply
  /// identically. The size is checked from the drop's own metadata first, so
  /// an oversized file is refused without ever being read into memory.
  Future<void> _handleDroppedFiles(List<DroppedFile> files) async {
    final strings = _chatL10n(context);
    if (_conn.isIsolated) return;
    if (!_supportsPromptAttachments) {
      _showComposerNote(strings.codexTextOnlyPrompt);
      return;
    }
    for (final file in files) {
      try {
        if (await file.length() > _maxAttachmentBytes) {
          throw ProductException(strings.chatUiEachAttachmentMustBe10MBOr);
        }
        final bytes = await file.readBytes();
        if (!mounted) return;
        await _addPreviewAttachment(
          filename: file.name,
          mimeType: file.mimeType,
          data: FilePreviewData(
            name: file.name,
            mimeType: file.mimeType,
            bytes: bytes,
          ),
        );
      } catch (error) {
        if (!mounted) return;
        showProductError(context, error);
        return;
      }
    }
    if (!mounted) return;
    _focus.requestFocus();
  }

  Future<void> _addPreviewAttachment({
    required String filename,
    required String? mimeType,
    required FilePreviewData data,
  }) async {
    if (!_supportsPromptAttachments) {
      _showComposerNote(_chatL10n(context).codexTextOnlyPrompt);
      return;
    }
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(
        data.error ?? _chatL10n(context).chatUiTheFileHasNoContentToAttach,
      );
    }
    if (_attachments.length >= _maxAttachmentCount) {
      throw ProductException(
        _chatL10n(context).chatUiAttachmentCountLimit(_maxAttachmentCount),
      );
    }
    if (bytes.length > _maxAttachmentBytes) {
      throw ProductException(
        _chatL10n(context).chatUiEachAttachmentMustBe10MBOr,
      );
    }
    final currentBytes = _attachments.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes + bytes.length > _maxAggregateAttachmentBytes) {
      throw ProductException(
        _chatL10n(context).chatUiAttachmentsMustTotalNoMoreThan20,
      );
    }
    final mime = promptAttachmentMime(
      filename: filename,
      bytes: bytes,
      declaredMime: mimeType,
    );
    if (mime == null) {
      throw ProductException(_chatL10n(context).chatAttachmentUnsupported);
    }
    final attachment = PromptAttachment(
      mime: mime,
      filename: filename,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    if (!mounted) return;
    setState(() => _attachments.add(attachment));
  }

  /// Deletes the conversation this screen made when the person leaves it
  /// untouched. Best effort: when OpenCode cannot confirm it is still empty
  /// (or cannot delete it), it stays, as an ordinary empty conversation in
  /// the list, and nothing interrupts the person on their way out.
  Future<void> _discardUntouchedMobileSession() async {
    if (_conn.isIsolated) return;
    if (!widget.discardIfUntouched ||
        _messages.isNotEmpty ||
        _pendingSends.isNotEmpty ||
        _sending ||
        // A typed draft persists per session, so the session must survive
        // to give that draft a home to be restored into.
        _composer.text.trim().isNotEmpty ||
        _attachments.isNotEmpty ||
        _draftRecoveryBlocked ||
        _photoBusy ||
        (_conn.promptPhotos.pending?.sessionID == widget.sessionID &&
            _conn.promptPhotos.pending?.profileID == _draftProfileID) ||
        _conn.busySessions.contains(widget.sessionID)) {
      return;
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) return;
      final currentMessages = await api.messagePage(widget.sessionID, limit: 1);
      if (currentMessages.items.isNotEmpty || currentMessages.hasMore) {
        return;
      }
      await api.deleteSession(widget.sessionID);
      try {
        await _conn.refreshSessions();
      } catch (_) {
        // The exact empty session is already gone. The destination screen will
        // reconcile on its normal refresh even if this optional refresh fails.
      }
    } catch (_) {
      // Kept: an empty conversation is harmless and the list shows it.
    }
  }

  /// The draft could not be saved on the way out. The sheet offers what its
  /// words say: copy the text and leave (the main answer), try saving
  /// again (it leaves once the save works), or leave without saving. Back,
  /// Esc and a swipe keep editing. True when the chat should close.
  Future<bool> _askLeaveUnsavedDraft() async {
    final l10n = _chatL10n(context);
    final navigator = Navigator.of(context);
    final text = _composer.text;
    final hasText = text.trim().isNotEmpty;
    final stillFailing = ValueNotifier<bool>(false);
    final retry = ValueNotifier<KitAction?>(null);
    var retrying = false;
    var open = true;
    void close() {
      if (!open) return;
      open = false;
      navigator.pop(true);
    }

    late final VoidCallback showRetry;
    Future<void> tryAgain() async {
      retrying = true;
      stillFailing.value = false;
      showRetry();
      final saved = await _retryDraftPersistence();
      retrying = false;
      if (!mounted || !open) return;
      if (saved) {
        close();
        return;
      }
      stillFailing.value = true;
      showRetry();
    }

    showRetry = () => retry.value = KitAction(
      key: const ValueKey('leave-draft-retry'),
      label: l10n.draftLeaveRetry,
      working: retrying,
      onPressed: retrying ? null : () => unawaited(tryAgain()),
    );
    showRetry();
    // The two notifiers outlive the sheet on purpose: a save still running
    // when the sheet is dismissed reports into them afterwards.
    final answer = await showKitSheet<bool>(
      context,
      sheetKey: const ValueKey('leave-unsaved-draft'),
      icon: AppIconography.save,
      title: l10n.draftLeaveTitle,
      body: (_) => ValueListenableBuilder<bool>(
        valueListenable: stillFailing,
        builder: (context, failed, _) => Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            KitText(
              hasText ? l10n.draftLeaveMessage : l10n.draftLeaveMessageNoText,
              tone: KitTextTone.secondary,
            ),
            if (failed) ...[
              SizedBox(height: KitTokens.of(context).space3),
              KitNotice(
                key: const ValueKey('leave-draft-still-failing'),
                tone: AppStatusTone.failure,
                message: l10n.draftLeaveStillFailing,
              ),
            ],
          ],
        ),
      ),
      primary: hasText
          ? KitAction(
              key: const ValueKey('leave-draft-copy'),
              label: l10n.draftLeaveCopyAction,
              icon: AppIconography.copy,
              onPressed: () async {
                if (!open) return;
                await KitCopy.copy(context, text, redact: false);
                close();
              },
            )
          : null,
      primaryListenable: hasText ? null : retry,
      secondaryListenable: hasText ? retry : null,
      tertiary: [
        KitAction(
          key: const ValueKey('leave-draft-discard'),
          label: l10n.draftLeaveAction,
          destructive: true,
          onPressed: close,
        ),
      ],
    );
    open = false;
    return answer == true;
  }

  Future<void> _leaveChat() async {
    if (_leavingProvisionalSession) return;
    final textBeforeSave = _composer.text;
    final attachmentsBeforeSave = List.of(_attachments);
    final saved = await _persistDraft();
    if (!mounted) return;
    if (!saved) {
      final leave = await _askLeaveUnsavedDraft();
      if (!mounted || !leave) return;
    }
    if (_composer.text != textBeforeSave ||
        !listEquals(attachmentsBeforeSave, _attachments)) {
      return;
    }
    _leavingProvisionalSession = true;
    await _discardUntouchedMobileSession();
    if (!mounted) return;
    setState(() => _allowRoutePop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.of(context).maybePop();
    });
  }

  Future<void> _downloadToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    if (_conn.isIsolated) return;
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(
        data.error ?? _chatL10n(context).chatUiTheFileHasNoContentToSave,
      );
    }
    final savedPath = await FilePicker.saveFile(
      dialogTitle: _chatL10n(context).chatUiSaveFile(file.displayName),
      fileName: file.displayName,
      bytes: bytes,
    );
    if (!mounted || savedPath == null) return;
    _showComposerNote(_chatL10n(context).chatUiFileSaved(file.displayName));
  }

  /// A picker opened from an open chat applies to this session only. Other
  /// sessions keep the profile default; on OpenCode 2 the server also treats
  /// the model as session state.
  ModelPickerApplyScope get _modelApplyScope => ModelPickerApplyScope.session;

  /// The model as the catalog names it, falling back to the presented
  /// provider/model pair; never a raw wire ID.
  /// The turn footer's model ("opencode/mimo-v2.6-flash-free", or two
  /// joined by an arrow) by the names the catalog gives them, as the
  /// composer shows them.
  String? _catalogModelNames(String? label) {
    if (label == null) return null;
    final models = _conn.catalog?.models ?? const <CatalogModel>[];
    return label
        .split(' → ')
        .map((part) {
          for (final model in models) {
            if (presentedModelLabel(model.providerID, model.id) == part &&
                model.name.trim().isNotEmpty) {
              return model.name.trim();
            }
          }
          return part;
        })
        .join(' → ');
  }

  String? get _presentedModelLabel {
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null) return null;
    for (final candidate in _conn.catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.id == model.modelID &&
          candidate.providerID == model.providerID &&
          candidate.name.trim().isNotEmpty) {
        return candidate.name.trim();
      }
    }
    return presentedModelLabel(model.providerID, model.modelID);
  }

  /// The selected model's catalog entry, when the catalog knows it.
  CatalogModel? get _selectedCatalogModel {
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null) return null;
    for (final candidate in _conn.catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.id == model.modelID &&
          candidate.providerID == model.providerID) {
        return candidate;
      }
    }
    return null;
  }

  /// The agent the server would use unprompted — the first primary agent —
  /// so the composer chip only names an agent when it is a real choice.
  String get _defaultAgentName {
    for (final agent in _conn.agents) {
      if (agent.mode != 'subagent') return agent.name;
    }
    return '';
  }

  /// Whether a pending permission request is for this tool call: its step
  /// then says "Waiting for you", never "Running" (AUTO-15).
  bool _toolWaitsForYou(Part part) {
    final callID = part.callID;
    if (callID == null || callID.isEmpty) return false;
    return _conn
        .permissionsForSession(widget.sessionID)
        .any((request) => request.tool?.callID == callID);
  }

  /// A permission request lands as an inline card above the composer —
  /// oldest first, one at a time — instead of a modal sheet that steals the
  /// keyboard mid-sentence; then a question, then a retry countdown. The
  /// slot unfolds when one arrives and folds away when none is left
  /// ([KitReveal], instant under reduced motion); one card replacing
  /// another changes in place.
  Widget _attentionRegion(
    List<PermissionRequest> pendingPermissions, {
    bool arrive = true,
  }) {
    final permission = pendingPermissions.firstOrNull;
    final question = _conn.questionForSession(widget.sessionID);
    final retry = _retryState;
    // The card an Inbox row or a notification opened this chat for is
    // washed once where it settles (above the composer), never in the
    // loading layout it leaves a moment later.
    Widget landing(String requestID, Widget card) => arrive
        ? KitArrival(id: chatRequestArrivalId(requestID), child: card)
        : card;
    // Automatic approval is never silent, but it is a standing fact, not an
    // event: it lives in the chip strip above the composer
    // ([_composerStatusStrip]), not in this slot.
    return KitReveal(
      child: permission != null
          ? landing(
              permission.id,
              Column(
                key: ValueKey('permission-region-${permission.id}'),
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _PermissionAttentionCard(
                    key: ValueKey('permission-card-${permission.id}'),
                    permission: permission,
                    autoApprovalFailed:
                        _conn.autoApprovalFailure(permission.id) != null,
                    onReview: () =>
                        unawaited(_showPermissionDialog(permission)),
                  ),
                  // P6.7: the third identical ask offers "Always allow"
                  // once, directly under its card.
                  if (!_conn.isIsolated)
                    AlwaysAllowInvitation(
                      key: ValueKey('always-allow-${permission.id}'),
                      controller: _conn,
                      sessionID: permission.sessionID,
                      requestID: permission.id,
                    ),
                ],
              ),
            )
          : question != null
          ? landing(
              question.id,
              _QuestionAttentionCard(
                key: ValueKey('question-card-${question.id}'),
                question: question,
                replying: _questionReplying,
                onAnswer: (answers) =>
                    unawaited(_answerQuestion(question, answers)),
                onMore: () => unawaited(_showQuestionSheet(question)),
              ),
            )
          : retry != null
          ? _RetryAttentionCard(
              key: const ValueKey('retry-banner'),
              retry: retry,
            )
          : null,
    );
  }

  Widget _transcriptSelectionArea({required Widget child}) => _conn.isIsolated
      ? child
      : KitSelectable(mode: KitSelectMode.finePointer, child: child);

  Widget _composerDropTarget({required Widget child}) => _conn.isIsolated
      ? child
      : !_supportsPromptAttachments
      ? child
      : DesktopFileDropTarget(onDrop: _handleDroppedFiles, child: child);

  /// The conversation's bar (kit-v2.md §1.18): the title, the server when
  /// more than one could be meant, and the few actions, most urgent first.
  /// On a phone the first shows and the rest wait in the overflow; on a PC
  /// they carry their words (§8.2).
  String _topBarTitle(Session? session, AppLocalizations l10n) {
    final own = presentedSessionTitle(
      session,
      fallback: l10n.commandDestination,
    );
    final watch = widget.watch;
    if (watch == null) return own;
    watch.sessionTitle?.value = own;
    return watch.title?.call() ?? own;
  }

  KitTopBar _chatTopBar({
    required Session? session,
    required String? serverName,
    required bool shared,
    required int runningWorkCount,
  }) {
    final l10n = _chatL10n(context);
    return KitTopBar(
      titleKey: const Key('chat-title'),
      // Watching a team worker: the task it is on, not its internal title.
      title: _topBarTitle(session, l10n),
      // Which server (and so which agent) this conversation is with, when
      // there is more than one to be with.
      subtitle: serverName,
      actions: [
        if (_readAloudRequestBusy || _readAloud?.speaking == true)
          KitAction(
            icon: AppIconography.stopCircle,
            label: l10n.readAloudStop,
            onPressed: () => unawaited(_stopReading()),
          ),
        if (!_conn.isIsolated &&
            !_watching &&
            _conn.capabilities.projectManagement &&
            runningWorkCount > 0)
          KitAction(
            key: const Key('running-work-indicator'),
            icon: AppIconography.branch,
            label: l10n.workCount(runningWorkCount),
            onPressed: _openRunningWork,
          ),
        if (_conn.isIsolated)
          KitAction(
            icon: AppIconography.review,
            label: l10n.demoReviewChanges,
            onPressed: _showDiff,
          ),
        // Watching: one tap folds every open step and fold on the page.
        if (_watching && _transcriptExpansion.anyOpen)
          KitAction(
            key: const Key('chat-collapse-all'),
            icon: AppIconography.unfoldLess,
            label: l10n.chatCollapseAllSteps,
            onPressed: _collapseAllSteps,
          ),
        // Watching: the worker's own page (its state and controls).
        if (widget.watch case final watch?) ?_watchDetailsAction(watch),
      ],
      // Watching: the conversation is the worker's; nothing in the menu
      // (share, fork, rename) is ours. The demo has none either.
      menu: _sessionMenu(shared: shared),
      menuKey: const ValueKey('session-actions-button'),
      menuLabel: l10n.chatUiSessionMenu,
    );
  }

  /// The transcript: the newest turn at the bottom, clear of the floating
  /// composer (under [KitComposer.layer]), with the two jump pills over it.
  Widget _transcript({
    required int queuedAfterIndex,
    required List<List<Part>> displayParts,
    required Set<String> waitingLocalIDs,
    required Set<int> turnActionOwners,
  }) {
    final Widget list = Builder(
      builder: (context) {
        final tokens = KitTokens.of(context);
        final clearance = KitBottomInset.of(context).bottom;
        return Center(
          child: ConstrainedBox(
            // The conversation's cap (VL §5, LAY-5), its gutters inside.
            constraints: BoxConstraints(
              maxWidth: KitLayout.paneDetailMaxWidth + 2 * tokens.gutter,
            ),
            // Scrolling repaints up to the nearest boundary; without one
            // that is the whole route, so every scroll frame redrew the
            // composer and its glass.
            child: RepaintBoundary(
              key: const ValueKey('transcript-repaint-boundary'),
              child: ScrollablePositionedList.builder(
                reverse: true,
                itemScrollController: _messageScroll,
                itemPositionsListener: _messagePositions,
                padding: EdgeInsets.fromLTRB(
                  tokens.gutter,
                  tokens.space2,
                  tokens.gutter,
                  tokens.space2 + clearance,
                ),
                itemCount:
                    _renderedMessageCount + (_olderCursor == null ? 0 : 1),
                itemBuilder: (context, i) => _transcriptRow(
                  context,
                  i,
                  queuedAfterIndex: queuedAfterIndex,
                  displayParts: displayParts,
                  waitingLocalIDs: waitingLocalIDs,
                  turnActionOwners: turnActionOwners,
                ),
              ),
            ),
          ),
        );
      },
    );
    final latest = KitJumpPillLayer(
      pill: KitJumpPill(
        pillKey: const ValueKey('jump-to-latest'),
        label: KitJumpPill.latestLabel(context),
        onPressed: _jumpToLatest,
        visible: _awayFromLatest,
      ),
      child: list,
    );
    // Only while reading history, and counting only the messages that
    // actually sit above the viewport.
    return ValueListenableBuilder<Iterable<ItemPosition>>(
      valueListenable: _messagePositions.itemPositions,
      child: latest,
      builder: (context, positions, child) {
        final earlier = _awayFromLatest && _messages.length > 30
            ? _earlierMessageCount(positions)
            : 0;
        // A leaving pill keeps its last words while it fades.
        if (earlier > 0) _earlierPillCount = earlier;
        return KitJumpPillLayer(
          pill: KitJumpPill.older(
            pillKey: const ValueKey('earlier-messages-pill'),
            label: _chatL10n(
              context,
            ).chatUiEarlierMessageCount(_earlierPillCount),
            onPressed: () => unawaited(_openTimeline()),
            visible: earlier > 0,
          ),
          child: child!,
        );
      },
    );
  }

  /// The running turn's live line and the row that carries it, worked out
  /// once per build ([_liveTurn]).
  ({KitTurnLive live, int index})? _live;

  /// The newest turn's live line while it runs, and the message row it
  /// sits under: from the moment a send is accepted here (before the server
  /// says the conversation is busy) until the turn ends, so a reply is
  /// never waited for in silence. Null when nothing runs.
  ({KitTurnLive live, int index})? _liveTurn({
    required bool busy,
    required int queuedAfterIndex,
  }) {
    if (_conn.isIsolated || _watching || _messages.isEmpty) return null;
    bool ended(MessageInfo info) =>
        info.errorText != null || info.time?.isDone == true;
    // OpenCode 1 runs a prompt sent mid-turn after the turn: the running
    // reply is then not the newest row, and its turn is the one that runs.
    final queuedBehind =
        queuedAfterIndex >= 0 &&
        queuedAfterIndex < _messages.length &&
        _messages[queuedAfterIndex].info.role == 'assistant' &&
        !ended(_messages[queuedAfterIndex].info) &&
        _messages.skip(queuedAfterIndex + 1).any(_isPrompt);
    final end = queuedBehind ? queuedAfterIndex + 1 : _messages.length;
    // A turn with no prompt of its own (an automated first turn) still
    // runs, and still needs its Stop.
    final prompt = _messages.take(end).toList().lastIndexWhere(_isPrompt);
    if (prompt < 0 && !busy) return null;
    MessageWithParts? step;
    var output = false;
    for (var index = prompt + 1; index < end; index += 1) {
      final message = _messages[index];
      if (message.info.role != 'assistant') continue;
      step = message;
      output =
          output ||
          message.parts.any(
            (part) =>
                (part.type == 'text' && part.text.trim().isNotEmpty) ||
                part.type == 'tool' ||
                part.type == 'reasoning',
          );
    }
    final stepEnded = step != null && ended(step.info);
    // The server's words win over this phone's guess: a finished step that
    // does not go on to run tools, with the conversation idle, is a
    // finished turn.
    if (!busy &&
        !_sending &&
        step != null &&
        stepEnded &&
        (step.info.errorText != null || step.info.finish != 'tool-calls')) {
      _localTurnSince = null;
    }
    final running = busy || _sending || _localTurnSince != null;
    if (!running) return null;
    final session = widget.sessionID;
    final KitTurnActivity activity;
    if (_sending) {
      activity = KitTurnActivity.sending;
    } else if (_conn.permissionsForSession(session).isNotEmpty ||
        _conn.questionForSession(session) != null) {
      activity = KitTurnActivity.waitingForYou;
    } else if (!output) {
      activity = busy || step != null
          ? KitTurnActivity.waitingForModel
          : KitTurnActivity.waitingForServer;
    } else {
      Part? newest;
      for (final part in step?.parts.reversed ?? const <Part>[]) {
        if ((part.type == 'text' && part.text.trim().isNotEmpty) ||
            part.type == 'tool' ||
            part.type == 'reasoning') {
          newest = part;
          break;
        }
      }
      activity = switch (newest) {
        final part? when part.type == 'tool' =>
          part.toolState.status == 'completed' ||
                  part.toolState.status == 'error'
              ? KitTurnActivity.thinking
              : KitTurnActivity.working,
        final part? when part.type == 'text' && !stepEnded =>
          KitTurnActivity.writing,
        _ => KitTurnActivity.thinking,
      };
    }
    final created = prompt < 0 ? null : _messages[prompt].info.time?.created;
    return (
      live: KitTurnLive(
        activity: activity,
        pace: _livePace(prompt, end),
        since:
            _localTurnSince ??
            (created == null
                ? null
                : DateTime.fromMillisecondsSinceEpoch(created)),
        // Not while the prompt is still on its way: there is nothing to
        // stop yet.
        onStop: _sending ? null : () => unawaited(_abort()),
        stopping: _aborting,
        stopKey: const Key('chat-stop-button'),
        teamAlsoWorking: _inAppTeamWorking(),
      ),
      // Under the reply that runs, above any prompt waiting behind it.
      index: queuedBehind ? queuedAfterIndex : _messages.length - 1,
    );
  }

  // How fast the running reply is arriving, for the composer edge's light:
  // characters (and a weight per step) that came in since the last change,
  // per second, eased. Kept across builds; reset when the turn changes.
  String? _paceTurn;
  int _paceMark = 0;
  DateTime? _paceAt;
  double _paceValue = 0;

  /// 0..1: about 90 characters a second and up is full pace.
  double _livePace(int prompt, int end) {
    var mark = 0;
    for (var i = prompt + 1; i < end; i += 1) {
      final message = _messages[i];
      if (message.info.role != 'assistant') continue;
      for (final part in message.parts) {
        if (part.type == 'text' || part.type == 'reasoning') {
          mark += part.text.length;
        } else if (part.type == 'tool') {
          mark += 60;
        }
      }
    }
    final now = DateTime.now();
    final turn = prompt < 0 ? null : _messages[prompt].info.id;
    if (turn != _paceTurn) {
      _paceTurn = turn;
      _paceMark = mark;
      _paceAt = now;
      _paceValue = 0;
      return 0;
    }
    final at = _paceAt;
    if (at != null && mark != _paceMark) {
      final seconds = (now.difference(at).inMilliseconds / 1000).clamp(
        0.05,
        5.0,
      );
      final target = ((mark - _paceMark).abs() / seconds / 90).clamp(0.0, 1.0);
      _paceValue += (target - _paceValue) * (1 - math.exp(-seconds / 0.8));
      _paceMark = mark;
      _paceAt = now;
    }
    return _paceValue;
  }

  /// This reply runs on the phone's own OpenCode while the AI Team on this
  /// phone has work going: the two share the phone, so a slow first word
  /// says why.
  bool _inAppTeamWorking() {
    final profile = _conn.profile;
    final team = _conn.orchestration;
    if (!looksLikeInAppServer(profile) || team == null) return false;
    if (!BuiltinTeam.isBuiltinConfig(profile?.orchestration)) return false;
    return teamGlanceFromSnapshot(team.snapshot).working > 0;
  }

  /// A server notice row (compaction, a sub-agent, a shell step).
  Widget _v2Row(MessageWithParts m, int index, Part tagged) => V2TranscriptRow(
    key: ValueKey('message-${m.info.id}'),
    part: tagged,
    messageId: m.info.id,
    parentSessionID: widget.sessionID,
    knownSessions: _conn.sessionsById,
    onCompactAgain: !_watching && _canCompactAgain(index)
        ? () => unawaited(_compact())
        : null,
    onOpenChild: _watching
        ? _openWatchedChild
        : _conn.capabilities.projectManagement
        ? (id) => _openSubagentSession(id, requireChild: true)
        : null,
  );

  /// Row [i] of the reversed transcript: item 0 is the newest turn. The
  /// running turn's live line ([_liveTurn]) rides on the row it names.
  Widget _transcriptRow(
    BuildContext context,
    int i, {
    required int queuedAfterIndex,
    required List<List<Part>> displayParts,
    required Set<String> waitingLocalIDs,
    required Set<int> turnActionOwners,
  }) {
    if (i == _renderedMessageCount) return _olderHistoryRow();
    final index = _renderedMessageCount - 1 - i;
    final m = _messages[index];
    // The running turn's status lives on the composer's edge
    // ([KitComposer.rail]), so no transcript row draws a live line.
    if (waitingLocalIDs.contains(m.info.id) || _isFoldedNotice(m)) {
      return const SizedBox.shrink();
    }
    if (v2VariantPart(m) case final tagged?) {
      return _v2Row(m, index, tagged);
    }
    final rawMeta = _messageMeta(_messages, index);
    final meta = rawMeta.withModelLabel(_catalogModelNames(rawMeta.modelLabel));
    final parts = displayParts[index];
    if (parts.isEmpty && meta.isEmpty && m.info.errorText == null) {
      return const SizedBox.shrink();
    }
    final hit =
        _findHits.isNotEmpty && _findHits[_findCursor].messageID == m.info.id
        ? _findHits[_findCursor]
        : null;
    final offline = _conn.isIsolated || _watching;
    final unanswered = _unanswered;
    final endsUnanswered = !offline && unanswered?.$2 == index;
    final silent = unanswered?.$3 ?? false;
    final suggestion = endsUnanswered
        ? _suggestedModelFor(m.info.errorText)
        : null;
    return _MessageView(
      key: ValueKey('message-${m.info.id}'),
      statusOnComposer: _live?.index == index,
      unanswered: !silent && unanswered?.$1 == index,
      onSendAgainNoReply: silent && !offline && unanswered?.$1 == index
          ? () => unawaited(
              _unansweredWords != null ? _resendUnanswered() : _retryLast(),
            )
          : null,
      onResendPrompt: endsUnanswered && _unansweredWords != null
          ? () => unawaited(_resendUnanswered())
          : null,
      onSendInterruptedAgain: offline ? null : () => unawaited(_retryLast()),
      suggestedModel: suggestion == null ? null : _modelName(suggestion),
      onUseSuggestedModel: suggestion == null || _unansweredWords == null
          ? null
          : () => unawaited(_resendUnanswered(model: suggestion)),
      queued:
          queuedAfterIndex >= 0 &&
          m.info.role == 'user' &&
          index > queuedAfterIndex,
      m: m,
      meta: meta,
      parts: parts,
      reasoningExpanded: _conn.transcriptReasoningExpanded,
      expansionStore: _transcriptExpansion,
      showTimestamp: _conn.transcriptTimestampsVisible,
      highlighted: hit != null || _highlightedMessageID == m.info.id,
      searchQuery: _findQuery,
      onSearchExcerptContext: (context) {
        if (_findHits.isNotEmpty &&
            _findHits[_findCursor].messageID == m.info.id) {
          _findExcerptContext = context;
        }
      },
      searchMatch: hit,
      // One "more" control per turn: under the message that ends a reply,
      // never under each step of it or under the prompt. An error with
      // more of the turn after it was got over.
      errorRecovered: m.info.errorText != null && !_endsTurn(_messages, index),
      showActions: turnActionOwners.contains(index),
      onCopy: _conn.isIsolated || _messageCopy(m).text.isEmpty
          ? null
          : () => unawaited(_copyMessageText(m)),
      // Watching: copy is the one message action; revert and fork are not
      // the person's.
      contextActions: offline ? null : () => _messageContextActions(m),
      filePreviewLoader: _loadToolOutputFile,
      onAttachFile: _supportsPromptAttachments && !_watching
          ? _attachToolOutputFile
          : null,
      onDownloadFile: _downloadToolOutputFile,
      onCompact: offline || !_supportsSessionCompact ? null : _compact,
      onOpenProviders: offline ? null : _openProviders,
      onContinue: offline ? null : _continueTruncated,
      onChooseModel: offline
          ? null
          : () => showModelPicker(
              context,
              applyScope: _modelApplyScope,
              sessionID: widget.sessionID,
            ),
      onOpenSession: _watching
          ? _openWatchedChild
          : _conn.isIsolated || !_conn.capabilities.projectManagement
          ? null
          : _openSubagentSession,
    );
  }

  /// Whether the request slot over the composer has something in it.
  bool _attentionPending(List<PermissionRequest> pendingPermissions) =>
      pendingPermissions.isNotEmpty ||
      _conn.questionForSession(widget.sessionID) != null ||
      _retryState != null ||
      (!_conn.isIsolated && _conn.autoApprovalFor(widget.sessionID).automatic);

  /// The height the composer leaves free over itself while a request waits,
  /// so a tall composer (large text, keyboard up) never squeezes the
  /// request to nothing: its Details and answers stay one scroll away.
  double _aboveComposerFloor(
    BoxConstraints bodyConstraints,
    List<PermissionRequest> pendingPermissions,
  ) => bodyConstraints.hasBoundedHeight && _attentionPending(pendingPermissions)
      ? bodyConstraints.maxHeight * .3
      : 0;

  /// What sits over the composer, most urgent first: find, then what needs
  /// the person, then what the draft is waiting on. Solid parts on the
  /// ground; only the composer below them is glass.
  Widget _aboveComposer({
    required BoxConstraints bodyConstraints,
    required bool compactComposer,
    required List<PermissionRequest> pendingPermissions,
  }) {
    final l10n = _chatL10n(context);
    final tokens = KitTokens.of(context);
    final short = bodyConstraints.maxHeight < 420;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Find sits by the keyboard it is typed with (owner review), over
        // the conversation it searches.
        if (_findOpen)
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: bodyConstraints.maxHeight * .38,
            ),
            child: _TranscriptFindBar(
              controller: _findController,
              focusNode: _findFocus,
              count: _findHits.length,
              current: _findCursor,
              hasOlder: _olderCursor != null,
              loading: _loading || _loadingOlder || _findAllLoading,
              searchingAll: _findAllLoading,
              onCancelLoading: () => setState(() => _findAllLoading = false),
              error: _olderError,
              needsReload: _olderNeedsReload || _resetHistoryOnLoad,
              onChanged: _changeFind,
              onNext: () => _navigateFind(1),
              onPrevious: () => _navigateFind(-1),
              onClose: _closeFind,
              onLoadOlder: _searchAllHistory,
            ),
          ),
        if (_conn.sessionNoteReceipt(widget.sessionID) case final saved?)
          Padding(
            padding: EdgeInsetsDirectional.symmetric(
              horizontal: tokens.gutter,
              vertical: tokens.space1,
            ),
            child: KitNotice(
              icon: AppIconography.note,
              message: [
                saved ? l10n.sessionNoteSaved : l10n.sessionNoteRemoved,
                l10n.sessionNotePending,
              ].join('. '),
              onDismiss: () =>
                  _conn.dismissSessionNoteReceipt(widget.sessionID),
            ),
          ),
        // The request shares the height left over the composer with the
        // rest (a tip, waiting drafts): it takes what is left and scrolls,
        // its actions reachable, instead of overflowing or pushing Send off
        // screen. The composer keeps a share free for it
        // ([_aboveComposerFloor]).
        if (bodyConstraints.hasBoundedHeight &&
            _attentionPending(pendingPermissions))
          Flexible(
            child: ListView(
              shrinkWrap: true,
              reverse: true,
              padding: EdgeInsets.zero,
              children: [_attentionRegion(pendingPermissions)],
            ),
          )
        else
          _attentionRegion(pendingPermissions),
        // §7 rule 5: v2-only surfaces stay silent on v1. The map is already
        // empty there, but the gate is explicit so a stale entry cannot leak
        // a form card onto a server that cannot answer it.
        if (_conn.formForSession(widget.sessionID) case final pendingForm?
            when _conn.capabilities.forms)
          KitArrival(
            id: chatRequestArrivalId(pendingForm.id),
            child: _FormRequestCard(
              key: ValueKey('form-request-card-${pendingForm.id}'),
              form: pendingForm,
              onAnswer: () => unawaited(_openForm(pendingForm)),
            ),
          ),
        // The one nudge slot: below whatever needs the person. It gives way
        // to a short (keyboard) layout like every quiet strip. At large text
        // the sentence is tall: it takes at most a third of the body and
        // scrolls, ending on its two controls.
        if (!short)
          ConstrainedBox(
            constraints: BoxConstraints(
              maxHeight: bodyConstraints.maxHeight / 3,
            ),
            child: ListView(
              shrinkWrap: true,
              reverse: true,
              padding: EdgeInsets.zero,
              children: [_nudgeSlot(context)],
            ),
          ),
        // The offline-draft half of the strip is v1-safe; only the inbox
        // bubbles are v2-only (§7 rule 5). Always in place, empty when
        // nothing waits, so the first queued message unfolds in and the last
        // one folds away (design standard §10).
        _PendingSendsStrip(
          key: const ValueKey('pending-sends-strip'),
          drafts: _conn.queuedPromptsFor(widget.sessionID),
          inboxItems: _conn.capabilities.inbox
              // What you sent and is waiting. The server's own pending
              // context updates are a standing fact and live in the chip
              // strip.
              ? _conn
                    .inboxItemsFor(widget.sessionID)
                    .where((item) => item.type == 'user')
                    .toList()
              : const <Api2InboxItem>[],
          isSending: (entry) => _conn.queuedPromptSending(entry.id),
          isAcceptedUnrecorded: (entry) =>
              _conn.queuedPromptAcceptedUnrecorded(entry.id),
          onEdit: _editQueuedPrompt,
          onResend: _resendQueuedPrompt,
          onRetry: _conn.status == StreamStatus.connected
              ? (entry) => unawaited(_retryQueuedPrompt(entry))
              : null,
          onDiscard: _discardQueuedPrompt,
          onCancelInbox: _cancelInboxSend,
          onFlipDelivery: _flipInboxDelivery,
        ),
        if (!_conn.isIsolated)
          if (_conn.promptPhotos.pending case final photo?
              when photo.profileID == _draftProfileID &&
                  photo.sessionID == widget.sessionID)
            KitRow(
              key: const ValueKey('pending-photo-recovery'),
              leading: const KitRowIcon(AppIconography.image),
              title: photo.name ?? l10n.photoPendingTitle,
              titleMaxLines: 2,
              onTap: _photoBusy ? null : () => _reviewPendingPhoto(photo),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  KitIconButton(
                    icon: AppIconography.add,
                    tooltip: l10n.photoAddToDraft,
                    onPressed: _promptShelfBusy
                        ? null
                        : () => _applyPendingPhoto(photo),
                  ),
                  KitIconButton(
                    icon: AppIconography.close,
                    tooltip: l10n.photoDiscard,
                    onPressed: _photoBusy
                        ? null
                        : () => _discardPendingPhoto(photo),
                  ),
                ],
              ),
            ),
        if (_draftSaveFailure case final failure?)
          Padding(
            key: const ValueKey('draft-save-error'),
            padding: EdgeInsetsDirectional.fromSTEB(
              tokens.gutter,
              tokens.space2,
              tokens.gutter,
              tokens.space1,
            ),
            child: Row(
              children: [
                Expanded(
                  child: Semantics(
                    container: true,
                    liveRegion: true,
                    label: _draftFailureText(failure),
                    excludeSemantics: true,
                    child: KitText(
                      compactComposer
                          ? l10n.draftUnsaved
                          : _draftFailureText(failure),
                      role: KitTextRole.secondary,
                      tone: KitTextTone.danger,
                    ),
                  ),
                ),
                KitIconButton(
                  icon: AppIconography.copy,
                  tooltip: l10n.chatDraftCopy,
                  // The person's own words, copied as written.
                  onPressed: _composer.text.isEmpty
                      ? null
                      : () => unawaited(
                          KitCopy.copy(context, _composer.text, redact: false),
                        ),
                ),
                KitIconButton(
                  icon: AppIconography.retry,
                  tooltip: l10n.draftRetrySave,
                  onPressed: _restoringDraftAttachments
                      ? null
                      : _retryDraftPersistence,
                ),
              ],
            ),
          ),
        KitReveal(
          child: _composerNote == null
              ? null
              : _ComposerNote(key: _composerNoteKey, text: _composerNote!),
        ),
        _composerStatusStrip(),
      ],
    );
  }

  /// The glass composer, as it floats over the transcript.
  Widget _floatingComposer({
    required BoxConstraints bodyConstraints,
    required bool compactComposer,
    required bool busy,
    required bool showAttachmentNote,
  }) => _composerDropTarget(
    child: _ChatComposer(
      // The prompt goes to the agent this server runs, and says so.
      agentName: switch (_conn.profile?.backend) {
        ServerBackend.paseo => 'Claude Code',
        ServerBackend.codex => 'Codex',
        _ => null,
      },
      isolated: _conn.isIsolated,
      compact: compactComposer,
      // The multiline field scrolls within its budget at large text scales,
      // leaving room for the model context and Send controls.
      maxInputHeight: compactComposer
          ? bodyConstraints.maxHeight * .45
          : double.infinity,
      // Isolated (the demo) has no commands; the composer says so when a
      // `/` is typed instead of offering any.
      allowInlineCommands:
          !_voiceConversation && bodyConstraints.maxHeight >= 300,
      controller: _composer,
      focusNode: _focus,
      commands: _chatCommands,
      agents: _supportsPromptAgentMentions
          ? _subagents
          : const <CatalogAgent>[],
      onSelectCommand: _selectChatCommand,
      onSelectAgent: _insertAgentMention,
      onOpenCommands: _openCommandLauncher,
      onOpenAgents: () =>
          _openCommandLauncher(initialTab: CommandSheetTab.agents),
      onOpenEditor: _openPromptEditor,
      onReusePrompt: _recentPrompts.isEmpty ? null : _reusePrompt,
      onClearText: _clearDraftText,
      onStashPrompt: _conn.canUsePromptShelf && !_sending && !_promptShelfBusy
          ? _stashCurrentPrompt
          : null,
      onOpenStash: _conn.canUsePromptShelf && !_sending && !_promptShelfBusy
          ? _openPromptStash
          : null,
      onRestoreHistoryDraft: _promptHistory.original == null
          ? null
          : _restoreHistoryDraft,
      shelfBusy: _promptShelfBusy,
      shelfLoading:
          _photoBusy || _promptShelfOperationBusy || _restoringDraftAttachments,
      attachments: _attachments,
      promptAttachmentsSupported: _supportsPromptAttachments,
      webSourcesSupported: _conn.capabilities.webSearch,
      busy: busy,
      // The running turn's status, written on the composer's top edge.
      live: _live?.live,
      sending: _sending,
      // OpenCode 1 runs a send made mid-turn after that turn; OpenCode 2
      // steers or queues it. Either way Send stays live.
      canSendWhileBusy: !_voiceConversation,
      canChooseDelivery: _conn.supportsInbox,
      delivery: _delivery,
      onDeliveryChanged: (delivery) => setState(() => _delivery = delivery),
      voiceOpening: _voiceOpening,
      selectedAgent: _conn.agentForSession(widget.sessionID),
      defaultAgent: _defaultAgentName,
      selectedModel: _conn.modelForSession(widget.sessionID),
      modelLabel: _presentedModelLabel,
      selectionFallback: !_conn.serverOwnsSessionSelection
          ? null
          : _conn.selectionForSession(widget.sessionID).modelKnown
          ? _chatL10n(context).modelServerDefault
          : _chatL10n(context).modelSelectionLoading,
      selectedCatalogModel: _selectedCatalogModel,
      selectedVariant: _conn.variantForSession(widget.sessionID),
      showAttachmentNote: showAttachmentNote,
      onAttach: _pickAttachment,
      onPhotoLibrary: () => _pickPhoto(ImageSource.gallery),
      onCamera: () => _pickPhoto(ImageSource.camera),
      onContentInserted: (content) =>
          unawaited(_handleInsertedContent(content)),
      onVoice: _openVoice,
      onConversation: _startVoiceConversation,
      onWebSources: _addWebSources,
      conversationMode: _voiceConversation,
      voice: _composerVoice(),
      onSend: _send,
      onChooseModel: () {
        if (!_conn.isIsolated) {
          showModelPicker(
            context,
            applyScope: _modelApplyScope,
            sessionID: widget.sessionID,
          );
        }
      },
      contextUsage: _contextWindowUsage(),
      modelSwitch: _modelCycleButton(),
      onRemoveAttachment: (attachment) =>
          setState(() => _attachments.remove(attachment)),
      // UX-103 review handoff (start).
      references: _stagedReferences,
      onRemoveReference: _removeStagedReference,
      // UX-103 review handoff (end).
    ),
  );

  @override
  Widget build(BuildContext context) {
    _syncFind();
    _queueNudgeObservation();
    // Read above the page frame: its body sees the keyboard inset removed,
    // having already shrunk to make room for it.
    final keyboardUp = MediaQuery.viewInsetsOf(context).bottom > 0;
    final busy = _conn.busySessions.contains(widget.sessionID);
    // OpenCode 1 runs a prompt sent mid-turn after that turn: every user
    // message past the assistant's current one is waiting, and says so.
    final queuedAfterIndex = busy && !_conn.supportsInbox
        ? _queuedAfterIndex(_messages)
        : -1;
    final displayParts = _timelineDisplayParts(_messages, liveTail: busy);
    _live = _liveTurn(busy: busy, queuedAfterIndex: queuedAfterIndex);
    _unanswered = _conn.isIsolated || _watching
        ? null
        : _unansweredTurn(
            _messages,
            refused: _promptError != null,
            running: busy || _sending || _live != null,
            stoppedPromptID: _stoppedPromptID,
          );
    // A prompt waiting in the server's inbox (steering, or queued behind the
    // run) is shown once, as its waiting bubble above the composer, where
    // it can still be flipped or cancelled. Its optimistic copy in the
    // transcript would say the same thing a second time, and would make the
    // running turn look finished.
    final waiting = !_conn.isIsolated && _conn.capabilities.inbox
        ? [
            for (final item in _conn.inboxItemsFor(widget.sessionID))
              if (item.type == 'user') item,
          ]
        : const <Api2InboxItem>[];
    final waitingIDs = {for (final item in waiting) item.id};
    final waitingTexts = {
      for (final item in waiting) (item.promptText ?? '').trim(),
    };
    // Where the agent's latest words end. A waiting prompt is always after
    // them; an older prompt with the same words is history and stays.
    final lastAssistant = _messages.lastIndexWhere(
      (message) => message.info.role == 'assistant',
    );
    final waitingLocalIDs = <String>{
      if (waiting.isNotEmpty)
        for (var i = 0; i < _messages.length; i++)
          if (_messages[i].info.role == 'user' &&
              v2VariantPart(_messages[i]) == null &&
              // The server admits a prompt under the id it will keep, and
              // the optimistic copy is swapped for that message at once, so
              // the copy in the transcript usually carries the item's id.
              (waitingIDs.contains(_messages[i].info.id) ||
                  (i > lastAssistant &&
                      waitingTexts.contains(
                        _messageText(_messages[i]).trim(),
                      ))))
            _messages[i].info.id,
    };
    final turnActionOwners = _turnActionOwners(
      _messages,
      displayParts,
      ignore: waitingLocalIDs,
      metaAlways: _conn.transcriptTimestampsVisible,
      running: _conn.busySessions.contains(widget.sessionID),
    );
    final showAttachmentNote = _attachmentNoteVisible();
    var pendingPermissions = _conn.permissionsForSession(widget.sessionID);
    // The request this chat was opened for leads (P4.2a).
    if (widget.landOnRequestID case final landing?
        when pendingPermissions.length > 1 &&
            pendingPermissions.first.id != landing &&
            pendingPermissions.any((p) => p.id == landing)) {
      pendingPermissions = [
        ...pendingPermissions.where((p) => p.id == landing),
        ...pendingPermissions.where((p) => p.id != landing),
      ];
    }

    final session = _conn.sessionsById[widget.sessionID];
    final shareUrl = _shareUrl;
    final parentID = session?.parentID;
    final siblings = parentID == null
        ? const <Session>[]
        : (_conn.sessionsById.values
              .where((candidate) => candidate.parentID == parentID)
              .toList()
            ..sort(
              (a, b) => (a.time?.created ?? 0).compareTo(b.time?.created ?? 0),
            ));
    final siblingIndex = siblings.indexWhere(
      (candidate) => candidate.id == widget.sessionID,
    );
    final runningAgents = runningAgentEntries(
      sessionID: widget.sessionID,
      sessions: _conn.sessionsById,
      busy: _conn.busySessions,
      includeIdle: true,
    );
    final relatedSessionIDs = {
      widget.sessionID,
      for (final session in _conn.sessionsById.values)
        if (session.parentID == widget.sessionID) session.id,
    };
    final runningWorkCount =
        runningAgents.where((entry) => entry.busy && !entry.current).length +
        _runningShells
            .where(
              (shell) =>
                  shell.running &&
                  (relatedSessionIDs.contains(shell.sessionID) ||
                      _shellIDs.contains(shell.id)),
            )
            .length;

    // Which server (and so which agent) this conversation is with, when
    // there is more than one to be with: OpenCode and Claude Code can both be
    // running on this phone, and their conversations look alike.
    final serverName = _conn.isIsolated || _conn.store.profiles.length < 2
        ? null
        : serverDisplayName(
            _conn.profile,
            lookupAppLocalizations(Localizations.localeOf(context)),
            // Two in-app profiles (OpenCode 1 and 2) are both "This phone";
            // the line must say which one this conversation is on.
            among: _conn.store.profiles,
          );
    final reconnecting = _conn.connectionStatus.waiting;
    // Speed contract item 2: while the first history read is on its way the
    // chat shows the end it had last time, read-only, instead of
    // placeholder turns. Never part of [_messages].
    final openingExcerpt =
        _loading && _messages.isEmpty && !_watching && !_conn.isIsolated
        ? _conn.cachedSessionTail(widget.sessionID)
        : null;
    final showExcerpt = openingExcerpt?.messages.isNotEmpty ?? false;
    if (!_firstTranscriptRecorded && (_messages.isNotEmpty || showExcerpt)) {
      _firstTranscriptRecorded = true;
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => PerfTrace.recordSince(
          'chat.open_to_first_transcript',
          _openedMicros,
          attrs: {'source': showExcerpt ? 'saved_excerpt' : 'live'},
        ),
      );
    }

    final screen = PopScope(
      canPop: _conn.isIsolated || _allowRoutePop || _watching,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_leaveChat());
      },
      // The page frame (kit-v2.md §8.2): full width on a phone, the
      // conversation centred at its cap from a tablet up, and the detail
      // pane of Work's two panes on a PC (no Back there).
      child: KitScreen(
        topBar: widget.showAppBar
            ? _chatTopBar(
                session: session,
                serverName: serverName,
                shared: shareUrl != null,
                runningWorkCount: runningWorkCount,
              )
            : null,
        // The screen's one loading bar (design standard §4): the
        // conversation's first load, and a reconnect.
        loading: (_loading && _messages.isEmpty) || reconnecting,
        loadingLabel: reconnecting
            ? _chatL10n(context).e7BannerReconnectingServerSemantic(
                _conn.profile?.name ?? 'OpenCode',
              )
            : _chatL10n(context).chatLoadingConversation,
        status: _chatStatus([
          if (widget.watch case final watch?) _watchingStatus(watch),
          if (!_watching) ...[
            if (_sendError case final error?)
              _sendErrorStatus(
                context,
                error: error,
                onDismiss: () => setState(() => _sendError = null),
              ),
            if (_promptError case final promptError?
                when !_messages.any(
                  (message) => _sameError(message.info.errorText, promptError),
                ))
              _promptErrorStatus(
                context,
                message: promptError,
                onDismiss: () => setState(() => _promptError = null),
                onChooseModel: _conn.isIsolated
                    ? null
                    : () => showModelPicker(
                        context,
                        applyScope: _modelApplyScope,
                        sessionID: widget.sessionID,
                      ),
                onResend: _unansweredWords == null
                    ? null
                    : () => unawaited(_resendUnanswered()),
                suggestion: switch (_suggestedModelFor(promptError)) {
                  final model? when _unansweredWords != null => _modelName(
                    model,
                  ),
                  _ => null,
                },
                onUseSuggestion: switch (_suggestedModelFor(promptError)) {
                  final model? when _unansweredWords != null => () => unawaited(
                    _resendUnanswered(model: model),
                  ),
                  _ => null,
                },
              ),
            _queuedDraftsStatus(
              context,
              _conn,
              onMove: (source) => unawaited(
                showQueuedPromptMoveSheet(
                  context,
                  connection: _conn,
                  source: source,
                  onProblem: (message, {details}) => _showComposerNote(message),
                ),
              ),
            ),
            if (!_conn.isIsolated &&
                _conn.supportsStagedRevert &&
                session?.reverted == true)
              _stagedRevertStatus(
                context,
                onReview: () => unawaited(_reviewStagedRevert()),
              )
            else if (!_conn.isIsolated &&
                _conn.capabilities.sessionRevert &&
                session?.reverted == true)
              _undoneStatus(context, onPutBack: () => unawaited(_restore())),
            if (!_conn.isIsolated && parentID != null)
              _subagentStatus(
                context,
                position: siblingIndex < 0 ? null : siblingIndex + 1,
                total: siblings.isEmpty ? null : siblings.length,
                onParent: _openParentSession,
                onAll: _showSubagents,
              ),
            if (!_conn.isIsolated && shareUrl != null)
              _sharedStatus(context, url: shareUrl, onStop: _stopSharing),
            // Below every real status line: first run's one notification
            // question (and what went wrong turning it on).
            _notifyOfferStatus(context),
            // Last, so anything else this chat says outranks it: replies
            // here come from OpenCode's free model because no provider is
            // signed in. A quiet line in the page's status slot, over the
            // transcript's top, so the reply at the bottom never moves;
            // dismissed once per conversation.
            if (!_freeModelNoteDismissed &&
                freeModelNoteDue(_conn, widget.sessionID))
              _ChatStatus(
                id: 'free-model',
                icon: AppIconography.speed,
                message: _chatL10n(context).freeModelNotice,
                action: KitAction(
                  key: const ValueKey('chat-free-model-sign-in'),
                  label: _chatL10n(context).freeModelSignIn,
                  onPressed: () => unawaited(_signInToProvider()),
                ),
                onDismiss: _dismissFreeModelNote,
              ),
          ],
        ]),
        header: [
          // The demo has no bar of its own here; its one extra action sits
          // under the host's bar. While the keyboard is up the room goes to
          // the conversation and what waits on the person; the action is
          // back when the keyboard is down.
          if (!widget.showAppBar &&
              _conn.isIsolated &&
              _messages.isNotEmpty &&
              !keyboardUp &&
              !widget.hostKeyboardUp)
            Align(
              alignment: AlignmentDirectional.centerEnd,
              child: KitButton.tertiary(
                icon: AppIconography.review,
                label: _chatL10n(context).demoReviewChanges,
                onPressed: _showDiff,
              ),
            ),
        ],
        // Rehydrates and refreshes must not flash a skeleton or a
        // full-screen error over an already-visible transcript. A
        // permission card must not wait for the transcript: it is pinned to
        // the bottom of the skeleton and error states too.
        body: _loading && _messages.isEmpty && !showExcerpt
            ? Column(
                children: [
                  const Expanded(child: _ChatLoadingBody()),
                  if (!_watching)
                    _attentionRegion(pendingPermissions, arrive: false),
                ],
              )
            : _error != null && _messages.isEmpty
            ? Column(
                children: [
                  Expanded(
                    child: _ChatLoadError(
                      error: _error!,
                      onRetry: () => unawaited(_load()),
                    ),
                  ),
                  if (!_watching)
                    _attentionRegion(pendingPermissions, arrive: false),
                ],
              )
            : LayoutBuilder(
                builder: (context, bodyConstraints) {
                  // The composer keeps one editor structure. A keyboard or
                  // short window reduces its line budget without reparenting
                  // the focused field or moving its controls.
                  final compactComposer =
                      keyboardUp || bodyConstraints.maxHeight < 420;
                  final startEmpty =
                      !showExcerpt &&
                      _visibleHistory.isEmpty &&
                      _olderCursor == null &&
                      widget.emptyState == null &&
                      !_watching;
                  if (startEmpty) _requestStartFacts();
                  final showStarters = startEmpty && !_voiceConversation;
                  final watch = widget.watch;
                  final Widget conversation = showExcerpt
                      ? _ChatOpeningExcerpt(preview: openingExcerpt!)
                      : _visibleHistory.isEmpty && _olderCursor == null
                      ? Builder(
                          // Clear of the floating composer (watching
                          // floats one too: it writes to the worker).
                          builder: (context) => Padding(
                            padding: EdgeInsetsDirectional.only(
                              bottom: KitBottomInset.of(context).bottom,
                            ),
                            child:
                                widget.emptyState ??
                                (watch != null
                                    ? _WatchingEmpty(words: watch.empty?.call())
                                    : null) ??
                                _ChatStartArea(
                                  header:
                                      ValueListenableBuilder<ChatStartFacts?>(
                                        valueListenable: _startFacts,
                                        builder: (context, _, _) =>
                                            _ChatStartHeader(
                                              facts: _currentStartFacts(),
                                              compact: compactComposer,
                                            ),
                                      ),
                                  starters: showStarters
                                      ? _startersRow()
                                      : null,
                                ),
                          ),
                        )
                      : _transcriptSelectionArea(
                          child: MarkdownFileLinks(
                            validate: _validatePathLink,
                            open: _openPathLink,
                            child: NotificationListener<ScrollNotification>(
                              onNotification: _onTranscriptScroll,
                              child: _transcript(
                                queuedAfterIndex: queuedAfterIndex,
                                displayParts: displayParts,
                                waitingLocalIDs: waitingLocalIDs,
                                turnActionOwners: turnActionOwners,
                              ),
                            ),
                          ),
                        );
                  // Watching: the composer writes to the worker through
                  // the team; nothing above it asks the person to act on
                  // the worker's session.
                  // The Undo bars read their clearance from a context under
                  // the composer layer, so they float above the composer
                  // instead of over it ([_undoHost]).
                  final anchored = Builder(
                    builder: (context) {
                      _undoBodyContext = context;
                      return conversation;
                    },
                  );
                  if (watch != null) {
                    return _watchLayer(watch: watch, body: anchored);
                  }
                  // The floating layer (VL §6): the transcript scrolls under
                  // the glass composer, the only glass on the page.
                  return KitComposer.layer(
                    body: anchored,
                    aboveMinHeight: _aboveComposerFloor(
                      bodyConstraints,
                      pendingPermissions,
                    ),
                    above: _aboveComposer(
                      bodyConstraints: bodyConstraints,
                      compactComposer: compactComposer,
                      pendingPermissions: pendingPermissions,
                    ),
                    composer: _floatingComposer(
                      bodyConstraints: bodyConstraints,
                      compactComposer: compactComposer,
                      busy: busy,
                      showAttachmentNote: showAttachmentNote,
                    ),
                  );
                },
              ),
      ),
    );
    if (_conn.isIsolated) {
      return MarkdownInteractionScope(enabled: false, child: screen);
    }
    return Actions(
      actions: {
        FindInSurfaceIntent: CallbackAction<FindInSurfaceIntent>(
          onInvoke: (_) {
            _openFind();
            return null;
          },
        ),
      },
      child: CallbackShortcuts(
        bindings: {
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.escape): _closeFind,
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.f3): () =>
                _navigateFind(1),
          if (_findOpen)
            const SingleActivator(LogicalKeyboardKey.f3, shift: true): () =>
                _navigateFind(-1),
        },
        child: Focus(
          focusNode: _findNavigationFocus,
          // This node only routes keyboard shortcuts; exposing the whole
          // screen as a focusable semantics node merges unrelated labels.
          includeSemantics: false,
          // Watching changes nothing: no model switch, no background
          // hand-off, no read receipt sent for the worker's session.
          child: _watching
              ? screen
              : ModelShortcuts(
                  onCycle: _cycleModel,
                  onBackground: _canBackgroundWork
                      ? _backgroundRunningWork
                      : null,
                  child: SessionViewObserver(
                    controller: _conn,
                    sessionID: widget.sessionID,
                    ready: !_loading && _error == null && !_awayFromLatest,
                    child: screen,
                  ),
                ),
        ),
      ),
    );
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

/// The transcript's per-block open/closed choices (`work:`, `tool:`,
/// `reasoning:` keys). Tells the page when the first block opens or the last
/// one closes so the top bar can offer "Collapse all steps".
class _ExpansionStore extends MapBase<String, bool> {
  _ExpansionStore({required this.onOpenChanged});

  final VoidCallback onOpenChanged;
  final Map<String, bool> _values = {};
  bool _anyOpen = false;

  bool get anyOpen => _anyOpen;

  void _sync() {
    final now = _values.containsValue(true);
    if (now == _anyOpen) return;
    _anyOpen = now;
    onOpenChanged();
  }

  /// Closes every open block, keeping the choice so a default-open block
  /// stays closed too.
  void collapseAll() {
    for (final key in _values.keys.toList()) {
      _values[key] = false;
    }
    _sync();
  }

  @override
  bool? operator [](Object? key) => _values[key];

  @override
  void operator []=(String key, bool value) {
    _values[key] = value;
    _sync();
  }

  @override
  void clear() {
    _values.clear();
    _sync();
  }

  @override
  Iterable<String> get keys => _values.keys;

  @override
  bool? remove(Object? key) {
    final removed = _values.remove(key);
    _sync();
    return removed;
  }
}

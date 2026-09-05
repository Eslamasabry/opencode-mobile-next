import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../api/sse.dart';
import '../../domain/prompt_attachment.dart';
import '../../domain/background_work.dart';
import 'running_work_sheet.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/offline_queue.dart';
import '../../state/connection.dart';
import '../../state/review_handoff.dart';
import '../../voice/controller.dart';
import '../../voice/voice_ui.dart';
import '../navigation/chat_route.dart';
import '../app_theme.dart';
import '../desktop/context_menu.dart';
import '../desktop/desktop_interaction.dart';
import '../desktop/file_drop.dart';
import '../desktop/shortcuts.dart';
import '../widgets/agent_color.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/connection_status_banner.dart';
import '../widgets/entrance.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/diff_view.dart';
import '../widgets/file_preview.dart';
import '../widgets/info_label.dart';
import '../widgets/markdown.dart';
import '../widgets/pickers.dart';
import '../widgets/model_shortcuts.dart';
import '../widgets/product_states.dart';
import '../widgets/question_options.dart';
import '../widgets/session_title.dart';
import '../widgets/running_agents_strip.dart';
import '../widgets/tool_card.dart';
import '../widgets/transcript_display_toggles.dart';
import '../../api2/models.dart' show Api2Delivery, Api2FormInfo, Api2InboxItem;
import '../permission_presentation.dart';
import 'activity_screen.dart' show showQuestionSheet;
import 'app_diagnostics_screen.dart';
import 'chat/form_flow.dart';
import 'chat/permission_sheet.dart';
import 'files_screen.dart';
import 'global_sessions_screen.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'project_health_screen.dart';
import 'review_workspace.dart';
import 'session_context_screen.dart';
import 'session_destination_sheet.dart';
import 'session_relations_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';
import 'tools_screen.dart';

part 'chat/sessions_tab.dart';
part 'chat/timeline_sheet.dart';
part 'chat/command_launcher.dart';
part 'chat/prompt_editor.dart';
part 'chat/prompt_history.dart';
part 'chat/composer.dart';
part 'chat/message_view.dart';
part 'chat/session_sheets.dart';
part 'chat/attention_card.dart';

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

String _fmtSessionTime(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  final now = DateTime.now();
  const months = [
    'Jan',
    'Feb',
    'Mar',
    'Apr',
    'May',
    'Jun',
    'Jul',
    'Aug',
    'Sep',
    'Oct',
    'Nov',
    'Dec',
  ];
  final hh = d.hour.toString().padLeft(2, '0');
  final mm = d.minute.toString().padLeft(2, '0');
  if (d.year == now.year && d.month == now.month && d.day == now.day) {
    return '$hh:$mm';
  }
  return '${d.day} ${months[d.month - 1]}, $hh:$mm';
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

  /// Overrides the app-wide review handoff store; tests inject their own so
  /// staged references do not leak between cases.
  final ReviewHandoffStore? handoffStore;

  const ChatScreen({
    super.key,
    required this.sessionID,
    this.voiceController,
    this.initialText = '',
    this.initialAttachments = const [],
    this.discardIfUntouched = false,
    this.handoffStore,
  });

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _PendingSend {
  final String localID;
  final String text;
  final List<PromptAttachment> attachments;
  final int createdAt;
  String? canonicalID;
  bool requestComplete = false;

  _PendingSend({
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
  bool _awayFromLatest = false;

  /// What Send does while a turn is running, on servers that support the
  /// inbox. Steer matches the server default; the visible delivery control
  /// in the composer both shows and sets this, and the Send long-press
  /// shortcut updates it too so the label never lies (UX-P0-04).
  PromptDelivery _delivery = PromptDelivery.steer;

  /// While the reader is scrolled away from the latest message, the rendered
  /// message count is pinned so a completing turn cannot shift the visible
  /// content by one item (reversed-list index anchoring). Pending messages
  /// materialize when the reader returns to the live end.
  int? _pinnedMessageCount;

  /// Session-scoped expansion state for tool cards, tool groups, and
  /// reasoning blocks, so list recycling does not collapse them.
  final Map<String, bool> _transcriptExpansion = {};
  final List<PromptAttachment> _attachments = [];

  // UX-103 review handoff (start) — Files, Changes, and Review stage
  // structured references here; the composer renders them as chips and
  // `_applyStagedReferences` folds them into the prompt text on send.
  late final ReviewHandoffSession _handoff = ReviewHandoffSession(
    store: widget.handoffStore ?? ReviewHandoffStore.instance,
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
  bool _permissionDismissScheduled = false;
  String? _activePermissionID;
  Route<void>? _activePermissionRoute;
  bool _permissionReplying = false;
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
  BackgroundWorkSupport _backgroundSupport = BackgroundWorkSupport.unavailable;
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

  /// The "attachments are not saved" note shows once per session, not on
  /// every attachment. [_attachmentNoteActive] keeps it up while the first
  /// batch is staged; the static set remembers sessions that have seen it.
  bool _attachmentNoteActive = false;
  static final Set<String> _attachmentNoteShownSessions = {};
  Future<VoiceComposerController>? _voiceFuture;
  VoiceComposerController? _voice;
  bool _voiceOpening = false;
  bool _allowRoutePop = false;
  bool _leavingProvisionalSession = false;
  String? _localShareUrl;
  String? _promptError;
  List<CommandInfo>? _serverCommands;
  Object? _serverCommandsError;
  bool _serverCommandsLoading = false;
  Future<void>? _serverCommandsRequest;
  String? _highlightedMessageID;
  Timer? _highlightTimer;

  String? get _shareUrl =>
      _conn.sessionsById[widget.sessionID]?.shareUrl ?? _localShareUrl;

  List<CatalogAgent> get _subagents {
    final agents = (_conn.catalog?.agents ?? const <CatalogAgent>[])
        .where((agent) => !agent.hidden && agent.mode == 'subagent')
        .toList();
    agents.sort((a, b) => a.id.compareTo(b.id));
    return agents;
  }

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _composer.text = widget.initialText;
    _attachments.addAll(widget.initialAttachments);
    _conn = _readConn();
    _offlineFlushRevision = _conn.offlineFlushRevision;
    if (widget.initialText.isEmpty) {
      final draft = _conn.sessionDraft(widget.sessionID);
      if (draft != null) {
        _composer.text = draft;
        _composer.selection = TextSelection.collapsed(offset: draft.length);
      }
    }
    _lastDraftText = _composer.text;
    _composer.addListener(_scheduleDraftSave);
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _conn.addListener(_onConnectionChanged);
    if (!_conn.sessionsById.containsKey(widget.sessionID)) {
      unawaited(_conn.ensureSession(widget.sessionID));
    }
    _syncRetryTicker();
    _handoff.store.addListener(_onHandoffChanged); // UX-103 review handoff
    _load();
    unawaited(_loadServerCommands());
    unawaited(_loadBackgroundSupport());
    unawaited(_loadRunningShells());
    _sub = _conn.events.listen(_onEvent);
    _wasBusy = _conn.busySessions.contains(widget.sessionID);
    final injectedVoice = widget.voiceController;
    if (injectedVoice != null) {
      _voice = injectedVoice;
      _voiceFuture = Future.value(injectedVoice);
    }
  }

  Future<VoiceComposerController> _getVoice() {
    return _voiceFuture ??= VoiceComposerController.create().then((voice) {
      _voice = voice;
      return voice;
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) {
      unawaited(_voice?.handleLifecyclePause());
      _persistDraft();
    }
  }

  /// Saves the composer text as this session's draft (or clears the draft
  /// when the composer is empty). Runs on navigation away, app pause, and
  /// after sends so the persisted draft always mirrors the composer.
  void _persistDraft() {
    _draftSaveTimer?.cancel();
    unawaited(_conn.saveSessionDraft(widget.sessionID, _composer.text));
  }

  // Save pauses in typing too: Android may kill a process without a final
  // lifecycle callback. Selection changes alone must not trigger a write.
  void _scheduleDraftSave() {
    if (_composer.text == _lastDraftText) return;
    _lastDraftText = _composer.text;
    _draftSaveTimer?.cancel();
    _draftSaveTimer = Timer(const Duration(milliseconds: 600), _persistDraft);
  }

  List<String> get _recentPrompts => {
    for (final message in _messages.reversed)
      if (message.info.role == 'user' && !message.info.id.startsWith('local-'))
        if (_messageText(message).trim() case final text when text.isNotEmpty)
          text,
  }.take(30).toList();

  Set<String> get _shellIDs => {
    for (final message in _messages)
      for (final part in message.parts)
        if (part.type == 'tool')
          if (part.toolState.metadata?['shellID'] case final String id) id,
  };

  Future<void> _loadRunningShells() async {
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
    final targetID = await showRunningWorkSheet(
      context,
      controller: _conn,
      sessionID: widget.sessionID,
      shellIDs: _shellIDs,
    );
    if (!mounted) return;
    final target = _conn.sessionsById[targetID];
    if (target != null) await _openRelatedSession(target);
    if (mounted) unawaited(_loadRunningShells());
  }

  Future<void> _reusePrompt() async {
    final text = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      builder: (_) => _PromptHistorySheet(prompts: _recentPrompts),
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

  void _clearDraftText() {
    final previous = _composer.value;
    _composer.clear();
    _persistDraft();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(_chatL10n(context).composerDraftCleared),
        action: SnackBarAction(
          label: _chatL10n(context).commonUndo,
          onPressed: () {
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
        ),
      ),
    );
    _focus.requestFocus();
  }

  Future<void> _loadBackgroundSupport() async {
    final repository = _conn.repository;
    _backgroundRepository = repository;
    _backgroundLocationRevision = _conn.locationRevision;
    final location = _conn.locationRevision;
    final revision = ++_backgroundSupportRevision;
    _backgroundSupport = BackgroundWorkSupport.unavailable;
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
      setState(() => _backgroundSupport = support);
    } catch (_) {
      // Unknown/older v1 servers must not advertise an experimental action.
      // Retry capability discovery after the next reconnect, not every event.
    }
  }

  String _backgroundPartKey(Part part) =>
      '${part.messageID}/${part.id ?? part.callID}';

  bool get _canBackgroundWork =>
      !_backgrounding &&
      _backgroundLocationRevision == _conn.locationRevision &&
      _conn.status == StreamStatus.connected &&
      _conn.busySessions.contains(widget.sessionID) &&
      foregroundBackgroundableParts(_messages, _backgroundSupport).any(
        (part) => !_backgroundRequestedParts.contains(_backgroundPartKey(part)),
      );

  Future<void> _backgroundRunningWork() async {
    if (!_canBackgroundWork) return;
    final repository = _conn.repository;
    if (repository == null) return;
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
        return;
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
      if (!mounted) return;
      if (result == BackgroundWorkResult.unchanged) {
        _showComposerNote(_chatL10n(context).backgroundWorkNoop);
      } else if (result == BackgroundWorkResult.promoted) {
        _showComposerNote(_chatL10n(context).backgroundWorkPromoted);
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _backgrounding = false);
    }
  }

  ConnectionController _readConn() {
    final ctx = context;
    final container = ProviderScope.containerOf(ctx, listen: false);
    return container.read(connProvider);
  }

  void _onEvent(EventEnvelope env) {
    if (!mounted) return;
    if (env.type == 'session.compacted' &&
        env.properties['sessionID'] == widget.sessionID) {
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
          setState(() {
            if (msg.role == 'assistant' && msg.errorText != null) {
              _promptError = msg.errorText;
              _recoverFromPromptError(msg.errorText);
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
      case 'session.error':
        if (env.properties['sessionID']?.toString() != widget.sessionID) {
          break;
        }
        setState(() {
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
    if (assistant >= 0) return assistant;
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
          'OpenCode could not complete this prompt.';
    }
    final text = raw?.toString().trim();
    return text?.isNotEmpty == true
        ? text!
        : 'OpenCode could not complete this prompt.';
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
      if (candidate.canonicalID == info.id) {
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
        throw const ProductException('OpenCode is reconnecting.');
      }
      final page = await api.messagePage(scope.session);
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
      _restoreHistoryAnchor(anchor, generation);
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
        throw const ProductException('OpenCode is reconnecting.');
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
    return Padding(
      key: const ValueKey('chat-older-history'),
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_olderError != null) Text(productErrorText(_olderError!)),
          Stack(
            alignment: Alignment.center,
            children: [
              OutlinedButton(
                key: const ValueKey('chat-load-older'),
                onPressed: _loading || _loadingOlder ? null : _loadOlder,
                child: Opacity(
                  opacity: _loadingOlder ? 0 : 1,
                  child: Text(
                    _olderNeedsReload
                        ? l10n.historyReload
                        : _olderError != null
                        ? l10n.refreshRetry
                        : l10n.historyLoadOlder,
                  ),
                ),
              ),
              if (_loadingOlder)
                const SizedBox.square(
                  dimension: 22,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
            ],
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
      if ((part.filename ?? '') != attachment.filename) return false;
      if (part.mime?.isNotEmpty == true && part.mime != attachment.mime) {
        return false;
      }
      if (part.url?.isNotEmpty == true && part.url != attachment.url) {
        return false;
      }
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
            ? 'Queued — will send when reconnected'
            : 'Queued — will send when reconnected. $evicted',
        key: const Key('queued-draft-notice'),
      );
    } else {
      _showActionError(
        'This draft is too large to queue, or the queue is full of newer '
        'drafts. Remove an attachment, or clear queued prompts in Settings.',
      );
    }
    return queued;
  }

  Future<bool> _removeQueuedDraft(String id) async {
    try {
      await _conn.removeQueuedPrompt(id);
      return true;
    } on OfflineQueueWriteException {
      if (mounted) _showActionError(_chatL10n(context).queueRemoveFailed);
      return false;
    }
  }

  Future<void> _editQueuedPrompt(QueuedPrompt entry) async {
    if (!await _removeQueuedDraft(entry.id)) return;
    if (!mounted) return;
    setState(() {
      _attachments
        ..clear()
        ..addAll(entry.attachments);
    });
    final current = _composer.text;
    _composer.text = current.trim().isEmpty
        ? entry.text
        : '${entry.text}\n$current';
    _composer.selection = TextSelection.collapsed(
      offset: _composer.text.length,
    );
    _focus.requestFocus();
  }

  Future<void> _discardQueuedPrompt(QueuedPrompt entry) async {
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.delete_sweep_outlined,
      title: 'Discard queued draft?',
      message: 'This draft has not been sent to OpenCode.',
      confirmLabel: 'Discard draft',
      cancelLabel: 'Keep it queued',
      destructive: true,
    );
    if (confirmed) await _removeQueuedDraft(entry.id);
  }

  /// Cancels a pending server send; its text returns to the composer as a
  /// draft — that is the edit affordance for immutable inbox items.
  Future<void> _cancelInboxSend(Api2InboxItem item) async {
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.delete_sweep_outlined,
      title: 'Cancel this pending message?',
      message: 'Its text returns to the composer as a draft.',
      confirmLabel: 'Cancel message',
      cancelLabel: 'Keep it pending',
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    String? text;
    try {
      text = await _conn.cancelInboxItem(widget.sessionID, item.id);
    } on ApiException catch (error) {
      if (!mounted) return;
      if (error.statusCode == 409) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Already delivered')));
        return;
      }
      _showActionError(error);
      return;
    } catch (error) {
      if (mounted) _showActionError(error);
      return;
    }
    if (!mounted || text == null || text.isEmpty) return;
    final current = _composer.text;
    _composer.text = current.trim().isEmpty ? text : '$text\n$current';
    _composer.selection = TextSelection.collapsed(
      offset: _composer.text.length,
    );
    _focus.requestFocus();
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
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Already delivered')));
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
  /// visible delivery control decides, and Steer stays the default, matching
  /// the server.
  PromptDelivery? get _activeDelivery =>
      _conn.supportsInbox && _conn.busySessions.contains(widget.sessionID)
      ? _delivery
      : null;

  /// [delivery] rides only on OpenCode 2 sends made while a turn runs. When
  /// it is omitted the composer's current delivery choice applies; the
  /// long-press shortcut passes an explicit steer or queue.
  Future<void> _send({PromptDelivery? delivery}) async {
    delivery ??= _activeDelivery;
    await _voice?.cancel();
    // UX-103 review handoff: the command grammar is matched against the text
    // the *user* typed, before any staged reference is folded in. Folding
    // first appended a multi-line reference block that `_typedChatCommand`
    // could never match, so a composer holding `/new` plus a staged reference
    // silently sent the command as a chat message.
    final hasStagedReferences = _handoff.references.isNotEmpty;
    if (_sending ||
        (_composer.text.trim().isEmpty &&
            _attachments.isEmpty &&
            !hasStagedReferences)) {
      return;
    }
    unawaited(HapticFeedback.lightImpact());
    if (_attachments.isEmpty &&
        _composer.text.trimLeft().startsWith('/') &&
        _serverCommands == null) {
      await _loadServerCommands();
      if (!mounted) return;
    }
    final typedCommand = _typedChatCommand(_composer.text.trim());
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
    _applyStagedReferences(); // UX-103 review handoff
    if (_composer.text.trim().isEmpty && _attachments.isEmpty) return;
    if (_conn.status != StreamStatus.connected) {
      // Offline compose: the draft queues instead of failing, and flushes
      // through the same send path when the connection returns.
      final draftText = _composer.text.trim();
      final draftAttachments = List<PromptAttachment>.from(_attachments);
      final draftMentions = _promptAgentMentions(draftText, _subagents);
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
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (actionApi == null) {
      setState(() => _sending = false);
      final detail = _conn.connectionError;
      _showActionError(
        detail == null || detail.isEmpty
            ? 'OpenCode is reconnecting. Try again when the server is online.'
            : detail,
      );
      return;
    }
    final text = _composer.text.trim();
    if (text.isEmpty && _attachments.isEmpty) {
      setState(() => _sending = false);
      return;
    }
    final attachments = List<PromptAttachment>.from(_attachments);
    final agentMentions = _promptAgentMentions(text, _subagents);
    var selection = _conn.selectionForSession(widget.sessionID);
    final selectionProfileID = _conn.profile?.id;
    var promptStarted = false;
    final createdAt = DateTime.now().millisecondsSinceEpoch;
    final localID = 'local-$createdAt-${DateTime.now().microsecondsSinceEpoch}';
    final pending = _PendingSend(
      localID: localID,
      text: text,
      attachments: attachments,
      createdAt: createdAt,
    );
    _composer.clear();
    _persistDraft();
    _focus.requestFocus();

    // Optimistic user bubble.
    setState(() {
      _promptError = null;
      _pendingSends.add(pending);
      _messages.add(
        MessageWithParts(
          info: MessageInfo(
            id: localID,
            sessionID: widget.sessionID,
            role: 'user',
            time: MsgTime(created: createdAt),
          ),
          parts: [
            if (text.isNotEmpty) Part(type: 'text', text: text),
            for (final attachment in attachments)
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
    try {
      await _conn.waitForSessionSelection(
        widget.sessionID,
        expectedApi: actionApi,
      );
      selection = _conn.selectionForSession(widget.sessionID);
      promptStarted = true;
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
      if (!mounted) return;
      setState(() {
        _sending = false;
        pending.requestComplete = true;
        if (pending.canonicalID != null) _pendingSends.remove(pending);
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _sending = false;
        _pendingSends.remove(pending);
        _messages.removeWhere(
          (message) =>
              message.info.id == pending.localID ||
              message.info.id == pending.canonicalID,
        );
      });
      // A transport-level failure (no HTTP response) means the server became
      // unreachable mid-send: queue the draft rather than erroring.
      if (promptStarted && e is ApiException && e.statusCode == null) {
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
      setState(() => _attachments.insertAll(0, attachments));
      final currentText = _composer.text;
      if (text.isNotEmpty && currentText.trim() != text) {
        _composer.text = currentText.isEmpty ? text : '$text\n$currentText';
        _composer.selection = TextSelection.collapsed(
          offset: _composer.text.length,
        );
      }
      showProductError(context, e);
    }
  }

  void _insertAgentMention(CatalogAgent agent) {
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

  Future<void> _submitTypedCommand(
    ({_ChatCommand command, String arguments}) typed,
  ) async {
    final command = typed.command;
    if (!command.enabled) {
      _showActionError('/${command.slash} is not available right now.');
      return;
    }
    if (command.serverCommand == null) {
      _composer.clear();
      await _runMobileCommand(command.action!);
      return;
    }
    setState(() => _sending = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (actionApi == null) {
      setState(() => _sending = false);
      _showActionError(
        _conn.connectionError ?? 'OpenCode is reconnecting. Try again shortly.',
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
      _composer.text = original;
      _composer.selection = TextSelection.collapsed(offset: original.length);
      setState(() => _sending = false);
      _showActionError(error);
    }
  }

  Future<void> _openVoice() async {
    // The tools sheet hides the entry point off Android; this keeps a
    // programmatic call (a shortcut, a restored intent) from starting a model
    // download for a recognizer that can never be fed.
    if (!platformCapabilities.supportsVoice) return;
    if (_voiceOpening || _sending) return;
    setState(() => _voiceOpening = true);
    try {
      final voice = await _getVoice();
      if (!mounted) return;
      if (!voice.models.isReady) {
        final ready = await showVoiceModelSetupSheet(context, voice.models);
        if (!ready || !mounted) return;
      }
      final result = await showVoiceComposerResultSheet(context, voice);
      if (!mounted || result == null || result.text.trim().isEmpty) return;
      final selection = _composer.selection;
      _composer.text = mergeVoiceDraft(_composer.text, selection, result.text);
      _composer.selection = TextSelection.collapsed(
        offset: _composer.text.length,
      );
      _focus.requestFocus();
      setState(() {});
      // "Insert & send" goes through the one send path, so delivery mode,
      // commands and attachments behave exactly as a typed prompt would.
      if (result.send) await _send();
    } catch (error) {
      if (mounted) _showActionError('Voice input failed: $error');
    } finally {
      if (mounted) setState(() => _voiceOpening = false);
    }
  }

  Future<void> _pickAttachment() async {
    try {
      final attachment = await _chooseAttachment(_attachments);
      if (attachment != null && mounted) {
        setState(() => _attachments.add(attachment));
      }
    } catch (error) {
      if (mounted) _showActionError(error);
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
    final unsupportedAttachment = _chatL10n(context).chatAttachmentUnsupported;
    if (current.length >= _maxAttachmentCount) {
      throw ProductException(
        'You can attach up to $_maxAttachmentCount files.',
      );
    }
    final currentBytes = current.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes >= _maxAggregateAttachmentBytes) {
      throw const ProductException(
        'Attachments must total no more than 20 MB.',
      );
    }
    final file = await FilePicker.pickFile(dialogTitle: 'Attach to prompt');
    if (file == null) return null;
    final size = await file.length();
    if (size > _maxAttachmentBytes) {
      throw const ProductException('Each attachment must be 10 MB or smaller.');
    }
    if (size > 0 && currentBytes + size > _maxAggregateAttachmentBytes) {
      throw const ProductException(
        'Attachments must total no more than 20 MB.',
      );
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
      throw const ProductException(
        'Attachments must total no more than 20 MB.',
      );
    }
    if (bytes == null) {
      throw const ProductException('Each attachment must be 10 MB or smaller.');
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

  Future<void> _openPromptEditor() async {
    final result = await Navigator.of(context).push<_PromptEditorResult>(
      MaterialPageRoute<_PromptEditorResult>(
        fullscreenDialog: true,
        builder: (_) => _PromptEditorScreen(
          initialValue: _composer.value,
          initialAttachments: _attachments,
          chooseAttachment: _chooseAttachment,
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
    unawaited(HapticFeedback.mediumImpact());
    setState(() => _aborting = true);
    final actionApi = await _conn.prepareActionTransport();
    if (!mounted) return;
    if (actionApi == null) {
      setState(() => _aborting = false);
      _showActionError(
        _conn.connectionError ?? 'OpenCode is reconnecting. Try again shortly.',
      );
      return;
    }
    try {
      await actionApi.abort(widget.sessionID);
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _aborting = false);
    }
  }

  Future<void> _share() async {
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.public_rounded,
      title: 'Share this session?',
      message:
          'Anyone with the link can view this session’s conversation and shared context. '
          'Do not share sessions containing secrets, credentials, or private files.',
      confirmLabel: 'Share session',
    );
    if (!confirmed) return;
    try {
      final repository = await _requireActionRepository();
      final url = await repository.shareSession(widget.sessionID);
      if (url == null) {
        throw const ProductException('No share link was returned');
      }
      if (mounted) {
        setState(() => _localShareUrl = url);
        await _conn.refreshSessions();
        if (!mounted) return;
        var copied = true;
        try {
          await Clipboard.setData(ClipboardData(text: url));
        } catch (_) {
          copied = false;
        }
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              copied
                  ? 'Share link copied'
                  : 'Session shared. Copy the visible link manually.',
            ),
          ),
        );
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _stopSharing() async {
    try {
      final repository = await _requireActionRepository();
      await repository.unshareSession(widget.sessionID);
      if (!mounted) return;
      setState(() => _localShareUrl = null);
      await _conn.refreshSessions();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Session is no longer shared')),
        );
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _fork() async {
    try {
      final repository = await _requireActionRepository();
      final id = await repository.forkSession(widget.sessionID);
      await _conn.refreshSessions();
      if (mounted) Navigator.of(context).pushReplacementNamed('/chat/$id');
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final repository = await _conn.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(
      _conn.connectionError ?? 'OpenCode is reconnecting. Try again shortly.',
    );
  }

  Future<void> _openTimeline({bool forkMode = false}) async {
    if (_messages.isEmpty) return;
    final selection = await showModalBottomSheet<_TimelineSelection>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      constraints: const BoxConstraints(maxWidth: 720),
      builder: (context) => ListenableBuilder(
        listenable: _historyChanges,
        builder: (context, _) => _TimelineSheet(
          messages: List.of(_messages),
          forkMode: forkMode,
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
    _jumpToMessage(selection.message.info.id);
  }

  Future<void> _toggleReasoningDisplay() async {
    final expanded = !_conn.transcriptReasoningExpanded;
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
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          expanded
              ? 'Reasoning expanded in the transcript'
              : 'Long reasoning collapsed in the transcript',
        ),
      ),
    );
  }

  Future<void> _toggleTimestampDisplay() async {
    final visible = !_conn.transcriptTimestampsVisible;
    await _conn.setTranscriptTimestampsVisible(visible);
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          visible ? 'Message timestamps shown' : 'Message timestamps hidden',
        ),
      ),
    );
  }

  /// Fraction of the model's context window consumed, from the newest
  /// assistant message that reported token usage and the catalog's limit for
  /// its exact model. Null when either side is unknown.
  double? _contextWindowUsage() {
    final catalog = _conn.catalog;
    if (catalog == null) return null;
    for (var index = _messages.length - 1; index >= 0; index -= 1) {
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
    // The list is reversed, so pixel offset measures distance scrolled away
    // from the newest message.
    final away = notification.metrics.pixels > 480;
    if (away != _awayFromLatest) {
      setState(() {
        _awayFromLatest = away;
        _pinnedMessageCount = away ? _messages.length : null;
      });
    }
    return false;
  }

  /// How many messages the transcript currently renders — the full list, or
  /// the pinned count while the reader is scrolled away from the live end.
  int get _renderedMessageCount {
    final pinned = _pinnedMessageCount;
    if (!_awayFromLatest || pinned == null) return _messages.length;
    return pinned < _messages.length ? pinned : _messages.length;
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
    if (MediaQuery.disableAnimationsOf(context)) {
      _messageScroll.jumpTo(index: 0);
    } else {
      _messageScroll.scrollTo(
        index: 0,
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
      );
    }
  }

  void _insertSuggestion(String text) {
    _composer.text = text;
    _composer.selection = TextSelection.collapsed(offset: text.length);
    _focus.requestFocus();
  }

  void _jumpToMessage(String messageID) {
    final chronologicalIndex = _messages.indexWhere(
      (message) => message.info.id == messageID,
    );
    if (chronologicalIndex < 0) {
      _showActionError('That message is no longer in this session.');
      return;
    }
    final listIndex = _messages.length - 1 - chronologicalIndex;
    _highlightTimer?.cancel();
    setState(() {
      // Materialize any messages deferred while scrolled away so the target
      // index maps onto the rendered list.
      _pinnedMessageCount = null;
      _highlightedMessageID = messageID;
    });
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_messageScroll.isAttached) return;
      _messageScroll.scrollTo(
        index: listIndex,
        alignment: .5,
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
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
      return (label: 'Copy message text', text: _messageText(message));
    }
    var start = index;
    var end = index;
    while (start > 0 && _messages[start - 1].info.role == 'assistant') {
      start--;
    }
    while (end + 1 < _messages.length &&
        _messages[end + 1].info.role == 'assistant') {
      end++;
    }
    final reply = _messages.getRange(start, end + 1);
    final unfinished =
        _conn.busySessions.contains(widget.sessionID) &&
        reply.any((item) => item.info.time?.isDone != true);
    return (
      label: start == end
          ? 'Copy message text'
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

  Future<void> _copyMessageText(MessageWithParts message) async {
    await Clipboard.setData(ClipboardData(text: _messageCopy(message).text));
    if (!mounted) return;
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Message text copied')));
  }

  /// The desktop right-click menu for a transcript message. Same three
  /// actions, same gates, same handlers as the long-press sheet below —
  /// mouse users simply reach them with the button they already use.
  List<ContextMenuAction> _messageContextActions(MessageWithParts message) => [
    if (_messageCopy(message).text.isNotEmpty)
      ContextMenuAction(
        menuKey: const ValueKey('message-menu-copy'),
        label: _messageCopy(message).label,
        icon: AppIcons.copy,
        onSelected: () => unawaited(_copyMessageText(message)),
      ),
    if (message.info.role == 'user')
      ContextMenuAction(
        menuKey: const ValueKey('message-menu-fork'),
        label: 'Fork from this prompt',
        icon: Icons.fork_right_rounded,
        onSelected: () => unawaited(_forkFromMessage(message)),
      ),
    if (_conn.capabilities.messageDelete)
      ContextMenuAction(
        menuKey: const ValueKey('message-menu-delete'),
        label: 'Delete message',
        icon: Icons.delete_outline_rounded,
        destructive: true,
        onSelected: () => unawaited(_deleteMessage(message)),
      ),
  ];

  Future<void> _showMessageActions(MessageWithParts message) async {
    final copy = _messageCopy(message);
    final canFork = message.info.role == 'user';
    final theme = Theme.of(context);
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (copy.text.isNotEmpty)
              ListTile(
                key: const ValueKey('message-action-copy'),
                leading: const Icon(AppIcons.copy),
                title: Text(copy.label),
                onTap: () => Navigator.pop(context, 'copy'),
              ),
            if (canFork)
              ListTile(
                key: const ValueKey('message-action-fork'),
                leading: const Icon(Icons.fork_right_rounded),
                title: const Text('Fork from this prompt'),
                subtitle: const Text(
                  'Start a new session with this prompt in the composer',
                ),
                onTap: () => Navigator.pop(context, 'fork'),
              ),
            // §7 row 14: v2 has no message delete, and PATCH edit is not the
            // same promise — do not fake it.
            if (_conn.capabilities.messageDelete)
              ListTile(
                key: const ValueKey('message-action-delete'),
                leading: Icon(
                  Icons.delete_outline_rounded,
                  color: theme.colorScheme.error,
                ),
                title: Text(
                  'Delete message',
                  style: TextStyle(color: theme.colorScheme.error),
                ),
                subtitle: const Text(
                  'Removes it from the conversation permanently',
                ),
                onTap: () => Navigator.pop(context, 'delete'),
              ),
          ],
        ),
      ),
    );
    if (!mounted || action == null) return;
    if (action == 'copy') await _copyMessageText(message);
    if (action == 'fork') await _forkFromMessage(message);
    if (action == 'delete') await _deleteMessage(message);
  }

  Future<void> _deleteMessage(MessageWithParts message) async {
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.delete_outline_rounded,
      title: 'Delete this message?',
      message:
          'The message and all of its parts are permanently removed from the '
          'conversation, so future replies no longer see them. File changes '
          'it made are not reverted.',
      confirmLabel: 'Delete message',
      destructive: true,
    );
    if (!confirmed || !mounted) return;
    try {
      final repository = await _requireActionRepository();
      await repository.deleteMessage(
        sessionID: widget.sessionID,
        messageID: message.info.id,
      );
      if (!mounted) return;
      setState(() {
        _messages.removeWhere((entry) => entry.info.id == message.info.id);
      });
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Message deleted')));
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _forkFromMessage(MessageWithParts message) async {
    if (message.info.role != 'user') return;
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
          'This prompt cannot be restored because an attachment is unavailable.',
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
      await Navigator.of(context).pushReplacement(
        MaterialPageRoute<void>(
          builder: (_) => ChatScreen(
            sessionID: id,
            initialText: text,
            initialAttachments: attachments,
          ),
        ),
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _compact() async {
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null && !_conn.serverOwnsSessionSelection) {
      _showActionError('Select a model before compacting this session.');
      return;
    }
    try {
      final repository = await _requireActionRepository();
      await repository.compactSession(
        widget.sessionID,
        providerID: model?.providerID ?? '',
        modelID: model?.modelID ?? '',
      );
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Compaction started')));
      }
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  /// The providers/integrations screen, reached from a provider-auth error
  /// card; the same destination the `/integrations` command opens.
  Future<void> _openProviders() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => IntegrationsScreen(controller: _conn),
      ),
    );
  }

  /// Sends "Continue" through the normal send path after an output-length
  /// cut, keeping any half-typed draft for afterwards.
  Future<void> _continueTruncated() async {
    if (_sending) return;
    final draft = _composer.text;
    _composer.text = 'Continue';
    await _send();
    if (mounted && _composer.text.isEmpty && draft.trim().isNotEmpty) {
      _composer.text = draft;
    }
  }

  Future<void> _revertLast() async {
    MessageWithParts? target;
    for (final message in _messages.reversed) {
      if (message.info.role == 'user' &&
          !message.info.id.startsWith('local-')) {
        target = message;
        break;
      }
    }
    if (target == null) return;
    final confirmed = await showConfirmSheet(
      context,
      icon: Icons.history_rounded,
      title: 'Revert from this prompt?',
      message:
          'Messages and file changes after the most recent prompt will be rolled back.',
      confirmLabel: 'Revert',
    );
    if (!confirmed) return;
    try {
      final repository = await _requireActionRepository();
      await repository.revertSession(widget.sessionID, target.info.id);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _restore() async {
    try {
      final repository = await _requireActionRepository();
      await repository.restoreSession(widget.sessionID);
      await _load(resetHistory: true);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _retryLast() async {
    if (_sending) return;
    MessageWithParts? target;
    for (final message in _messages.reversed) {
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
    final attachments = <PromptAttachment>[];
    for (final file in files) {
      final url = file.url;
      if (url == null || url.isEmpty) {
        _showActionError(
          'This prompt cannot be retried because an attachment is unavailable.',
        );
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
        throw const ProductException('OpenCode is reconnecting.');
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
    if (!mounted) return;
    _announceCompletedFlush();
    _syncRetryTicker();
    final shouldRehydrate =
        _dataRefreshRevision != _conn.dataRefreshRevision && _conn.api != null;
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _dismissResolvedPermissionDialog();
    _noteRunFinished();
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

  /// A light haptic when this session goes from busy to idle. Keep the
  /// editor in place and respect reduced motion.
  void _noteRunFinished() {
    final busy = _conn.busySessions.contains(widget.sessionID);
    final finished = _wasBusy && !busy;
    _wasBusy = busy;
    if (!finished) return;
    if (MediaQuery.maybeDisableAnimationsOf(context) ?? false) return;
    unawaited(HapticFeedback.lightImpact());
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
  /// loop the "Queued — will send when reconnected" snackbar opened. Also
  /// names drafts the flush deliberately left for other servers.
  void _announceCompletedFlush() {
    if (_offlineFlushRevision == _conn.offlineFlushRevision) return;
    _offlineFlushRevision = _conn.offlineFlushRevision;
    final sent = _conn.lastFlushedPromptCount;
    if (sent <= 0) return;
    final waiting = _conn.lastFlushSkippedForOtherProfiles;
    final message = StringBuffer(
      'Sent $sent queued prompt${sent == 1 ? '' : 's'}',
    );
    if (waiting > 0) {
      message.write(
        ' · $waiting draft${waiting == 1 ? '' : 's'} waiting for other '
        'servers',
      );
    }
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message.toString())));
  }

  void _dismissResolvedPermissionDialog() {
    final activeID = _activePermissionID;
    if (activeID == null ||
        _conn.permissions.containsKey(activeID) ||
        _permissionDismissScheduled) {
      return;
    }
    _permissionDismissScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _permissionDismissScheduled = false;
      if (!mounted ||
          _activePermissionID != activeID ||
          _conn.permissions.containsKey(activeID)) {
        return;
      }
      final route = _activePermissionRoute;
      if (route != null && route.isActive) {
        route.navigator?.removeRoute(route);
        // The request settled without this sheet replying — someone else
        // (another device, the TUI, a notification action) handled it.
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Handled on another device')),
        );
      }
    });
  }

  /// The Review path from the attention card: the full permission sheet,
  /// now dismissible — closing it leaves the card in place.
  Future<void> _showPermissionDialog(PermissionRequest permission) async {
    if (_activePermissionID != null) return;
    _activePermissionID = permission.id;
    final tool = permission.tool;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      isDismissible: true,
      enableDrag: true,
      useSafeArea: true,
      clipBehavior: Clip.antiAlias,
      constraints: const BoxConstraints(maxWidth: 720),
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerLow,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (sheetContext) {
        _activePermissionRoute = ModalRoute.of<void>(sheetContext);
        _dismissResolvedPermissionDialog();
        return Padding(
          padding: EdgeInsets.only(
            bottom: MediaQuery.viewInsetsOf(sheetContext).bottom,
          ),
          child: PermissionSheet(
            permission: permission,
            supportsRejectMessage: _conn.permissionSupportsRejectMessage(
              permission.id,
            ),
            onReply: (reply, {message}) =>
                _conn.answerPermission(permission.id, reply, message: message),
            onShowSource: tool == null
                ? null
                : () => _jumpToMessage(tool.messageID),
          ),
        );
      },
    );
    if (!mounted) return;
    _activePermissionID = null;
    _activePermissionRoute = null;
  }

  /// The card's fast path: the same reply the sheet's Allow once sends.
  Future<void> _allowPermissionOnce(PermissionRequest permission) async {
    if (_permissionReplying) return;
    setState(() => _permissionReplying = true);
    try {
      await _conn.answerPermission(permission.id, 'once');
    } catch (error) {
      if (mounted) _showActionError(error);
    } finally {
      if (mounted) setState(() => _permissionReplying = false);
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

  Future<void> _runShellDialog() async {
    final ctrl = TextEditingController();
    final cmd = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Run shell command'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          decoration: const InputDecoration(
            hintText: 'e.g. npm test',
            prefixText: '\$ ',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
            child: const Text('Run'),
          ),
        ],
      ),
    );
    if (cmd == null || cmd.isEmpty) return;
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      await _conn.waitForSessionSelection(widget.sessionID, expectedApi: api);
      await api.shell(
        widget.sessionID,
        command: cmd,
        agent: _conn.agentForSession(widget.sessionID).isNotEmpty
            ? _conn.agentForSession(widget.sessionID)
            : 'build',
        model: _conn.modelForSession(widget.sessionID),
        variant: _conn.variantForSession(widget.sessionID).isEmpty
            ? null
            : _conn.variantForSession(widget.sessionID),
      );
    } catch (e) {
      if (mounted) showProductError(context, e);
    }
  }

  Future<void> _loadServerCommands() {
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
      if (repository == null) {
        throw const ProductException(
          'OpenCode commands are unavailable offline.',
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

  List<_ChatCommand> get _chatCommands {
    final session = _conn.sessionsById[widget.sessionID];
    final hasUserMessage = _messages.any(
      (message) =>
          message.info.role == 'user' && !message.info.id.startsWith('local-'),
    );
    final builtins = <_ChatCommand>[
      _ChatCommand.mobile(
        slash: 'new',
        aliases: const ['clear'],
        title: 'New session',
        description: 'Start a clean session in this workspace',
        group: 'Navigate',
        action: _ChatCommandAction.newSession,
      ),
      _ChatCommand.mobile(
        slash: 'sessions',
        aliases: const ['resume', 'continue'],
        title: 'Sessions',
        description: 'Find sessions across every OpenCode project',
        group: 'Navigate',
        action: _ChatCommandAction.sessions,
      ),
      _ChatCommand.mobile(
        slash: 'workspaces',
        aliases: const ['workspace'],
        title: 'Projects and workspaces',
        description: 'Switch project, directory, or worktree',
        group: 'Navigate',
        action: _ChatCommandAction.workspaces,
      ),
      _ChatCommand.mobile(
        slash: 'move',
        title: 'Move session',
        description: 'Move this session to another project directory',
        group: 'Current session',
        action: _ChatCommandAction.move,
      ),
      // §7 row 5: warping a session into a managed workspace has no v2
      // equivalent, so the command leaves the palette rather than failing.
      if (_conn.capabilities.workspaceWarp)
        _ChatCommand.mobile(
          slash: 'warp',
          title: 'Move session',
          description: 'Change this session’s experimental workspace',
          group: 'Current session',
          action: _ChatCommandAction.warp,
        ),
      _ChatCommand.mobile(
        slash: 'editor',
        title: 'Prompt editor',
        description: 'Edit the current prompt in a focused full-screen view',
        group: 'Compose',
        action: _ChatCommandAction.promptEditor,
      ),
      _ChatCommand.mobile(
        slash: 'files',
        aliases: const ['open'],
        title: 'Project files',
        description: 'Browse, preview, download, and attach project files',
        group: 'Navigate',
        action: _ChatCommandAction.files,
      ),
      _ChatCommand.mobile(
        slash: 'health',
        title: 'Project health',
        description:
            'Inspect Git, language services, and formatters for this project',
        group: 'Navigate',
        action: _ChatCommandAction.projectHealth,
      ),
      _ChatCommand.mobile(
        slash: 'terminal',
        title: 'Terminal',
        description: 'Open persistent workspace terminals',
        group: 'Navigate',
        action: _ChatCommandAction.terminal,
      ),
      _ChatCommand.mobile(
        slash: 'models',
        aliases: const ['model', 'mo'],
        title: 'Model',
        description: 'Choose a server model by provider and capability',
        group: 'Model and agent',
        action: _ChatCommandAction.model,
      ),
      _ChatCommand.mobile(
        slash: 'agents',
        aliases: const ['agent'],
        title: 'Agent',
        description: 'Choose the active OpenCode agent',
        group: 'Model and agent',
        action: _ChatCommandAction.model,
      ),
      _ChatCommand.mobile(
        slash: 'variants',
        title: 'Thinking mode',
        description: 'Choose the current model variant or reasoning effort',
        group: 'Model and agent',
        action: _ChatCommandAction.model,
      ),
      _ChatCommand.mobile(
        slash: 'mcps',
        aliases: const ['mcp'],
        title: 'MCP servers',
        description: 'Inspect MCP status, authentication, and resources',
        group: 'OpenCode',
        action: _ChatCommandAction.integrations,
      ),
      _ChatCommand.mobile(
        slash: 'connect',
        title: 'Connect provider',
        description: 'Manage provider and integration authentication',
        group: 'OpenCode',
        action: _ChatCommandAction.integrations,
      ),
      // §7 row 8.
      if (_conn.capabilities.consoleOrganizations)
        _ChatCommand.mobile(
          slash: 'org',
          aliases: const ['orgs', 'switch-org'],
          title: 'Switch organization',
          description: 'Change the active OpenCode Console organization',
          group: 'OpenCode',
          action: _ChatCommandAction.organization,
        ),
      _ChatCommand.mobile(
        slash: 'skills',
        title: 'Skills',
        description: 'Browse project and global skills',
        group: 'OpenCode',
        action: _ChatCommandAction.skills,
      ),
      // §7 row 20: no tool inventory endpoint, so the destination goes too.
      if (_conn.capabilities.toolInventory)
        _ChatCommand.mobile(
          slash: 'tools',
          title: 'Tools and capabilities',
          description:
              'Inspect tools callable by the active provider and model',
          group: 'OpenCode',
          action: _ChatCommandAction.tools,
        ),
      _ChatCommand.mobile(
        slash: 'references',
        aliases: const ['reference', 'refs'],
        title: 'Project references',
        description: 'Add an OpenCode project reference to this prompt',
        group: 'OpenCode',
        action: _ChatCommandAction.references,
      ),
      _ChatCommand.mobile(
        slash: 'status',
        title: 'Server status',
        description: 'Connection health, server version, and live mode',
        group: 'OpenCode',
        action: _ChatCommandAction.status,
      ),
      _ChatCommand.mobile(
        slash: 'debug',
        title: 'App diagnostics',
        description: 'Review handled app errors and send a redacted report',
        group: 'OpenCode',
        action: _ChatCommandAction.diagnostics,
      ),
      _ChatCommand.mobile(
        slash: 'themes',
        aliases: const ['theme'],
        title: 'Appearance',
        description: 'Follow Android or choose the native light or dark theme',
        group: 'Transcript display',
        action: _ChatCommandAction.appearance,
      ),
      _ChatCommand.mobile(
        slash: 'diff',
        title: 'Session changes',
        description: 'Review the actual diff for this session',
        group: 'Current session',
        action: _ChatCommandAction.diff,
      ),
      _ChatCommand.mobile(
        slash: 'context',
        aliases: const ['usage'],
        title: 'Session context',
        description: 'Inspect current tokens, cache, cost, and context usage',
        group: 'Current session',
        action: _ChatCommandAction.context,
        enabled: _messages.any(
          (message) =>
              message.info.role == 'assistant' && message.info.tokens.total > 0,
        ),
      ),
      // §7 rows 10–11.
      if (_conn.capabilities.sessionShare) ...[
        _ChatCommand.mobile(
          slash: 'share',
          title: _shareUrl == null ? 'Share session' : 'Copy share link',
          description: 'Create or copy a public session link',
          group: 'Current session',
          action: _ChatCommandAction.share,
        ),
        _ChatCommand.mobile(
          slash: 'unshare',
          title: 'Stop sharing',
          description: 'Disable the current public session link',
          group: 'Current session',
          action: _ChatCommandAction.unshare,
          enabled: _shareUrl != null,
        ),
      ],
      _ChatCommand.mobile(
        slash: 'rename',
        title: 'Rename session',
        description: 'Change the title shown in the session list',
        group: 'Current session',
        action: _ChatCommandAction.rename,
      ),
      _ChatCommand.mobile(
        slash: 'timeline',
        aliases: const ['messages'],
        title: 'Message timeline',
        description: 'Find a message, jump to it, or fork from a prompt',
        group: 'Current session',
        action: _ChatCommandAction.timeline,
        enabled: _messages.isNotEmpty,
      ),
      _ChatCommand.mobile(
        slash: 'fork',
        title: 'Fork from prompt',
        description: 'Choose a prompt and continue it in a new session',
        group: 'Current session',
        action: _ChatCommandAction.fork,
        enabled: hasUserMessage,
      ),
      _ChatCommand.mobile(
        slash: 'compact',
        aliases: const ['summarize'],
        title: 'Compact context',
        description: 'Summarize the session using the selected model',
        group: 'Current session',
        action: _ChatCommandAction.compact,
        enabled: hasUserMessage,
      ),
      _ChatCommand.mobile(
        slash: 'thinking',
        aliases: const ['toggle-thinking'],
        title: _conn.transcriptReasoningExpanded
            ? 'Collapse reasoning'
            : 'Expand reasoning',
        description: 'Toggle long reasoning details across the transcript',
        group: 'Transcript display',
        action: _ChatCommandAction.thinking,
      ),
      _ChatCommand.mobile(
        slash: 'timestamps',
        aliases: const ['toggle-timestamps'],
        title: _conn.transcriptTimestampsVisible
            ? 'Hide timestamps'
            : 'Show timestamps',
        description: 'Toggle creation times beside transcript entries',
        group: 'Transcript display',
        action: _ChatCommandAction.timestamps,
      ),
      _ChatCommand.mobile(
        slash: 'undo',
        title: 'Revert last prompt',
        description: 'Roll back messages and file changes after the prompt',
        group: 'Current session',
        action: _ChatCommandAction.undo,
        enabled: hasUserMessage && session?.reverted != true,
      ),
      _ChatCommand.mobile(
        slash: 'redo',
        title: 'Restore reverted prompt',
        description: 'Restore the currently reverted session state',
        group: 'Current session',
        action: _ChatCommandAction.redo,
        enabled: session?.reverted == true,
      ),
      _ChatCommand.mobile(
        slash: 'copy',
        title: 'Copy transcript',
        description: 'Copy the rendered conversation as Markdown',
        group: 'Current session',
        action: _ChatCommandAction.copy,
      ),
      _ChatCommand.mobile(
        slash: 'export',
        title: 'Export transcript',
        description: 'Save the conversation as a Markdown file',
        group: 'Current session',
        action: _ChatCommandAction.export,
      ),
      _ChatCommand.mobile(
        slash: 'help',
        title: 'Command map',
        description: 'Search mobile actions and server-provided commands',
        group: 'OpenCode',
        action: _ChatCommandAction.help,
      ),
    ];
    final dynamic = [
      for (final command in _serverCommands ?? const <CommandInfo>[])
        _ChatCommand.server(command),
    ];
    return [...builtins, ...dynamic];
  }

  /// Ctrl+K in a session opens the session's own command launcher rather than
  /// the shell one: slash commands and subagents are the commands that matter
  /// here. Everything else falls through to the shell.
  @override
  bool onAppShortcut(Intent intent) {
    if (intent is! OpenCommandPaletteIntent) return false;
    unawaited(_openCommandLauncher());
    return true;
  }

  Future<void> _cycleModel({
    bool reverse = false,
    bool favoritesOnly = false,
  }) async {
    final revision = _conn.connectionRevision;
    try {
      final next = await _conn.cycleModelForSession(
        widget.sessionID,
        reverse: reverse,
        favoritesOnly: favoritesOnly,
      );
      if (!mounted || revision != _conn.connectionRevision) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(
          SnackBar(
            content: Text(
              next == null
                  ? 'Choose another model in the picker to build your recent list.'
                  : 'Next turns in this session use $_presentedModelLabel.',
            ),
          ),
        );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Widget? _modelCycleButton() {
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

  Future<void> _openCommandLauncher({
    _ComposerToolTab initialTab = _ComposerToolTab.commands,
  }) async {
    FocusManager.instance.primaryFocus?.unfocus();
    unawaited(_conn.refreshCatalog());
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      constraints: const BoxConstraints(maxWidth: 720),
      builder: (sheetContext) => _CommandLauncherSheet(
        controller: _conn,
        initialTab: initialTab,
        commands: () => _chatCommands,
        agents: () => _subagents,
        loading: () => _serverCommandsLoading,
        error: () => _serverCommandsError,
        onRefresh: _loadServerCommands,
        onSelected: (command) {
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _selectChatCommand(command);
        },
        onAgentSelected: (agent) {
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _insertAgentMention(agent);
        },
      ),
    );
  }

  void _selectChatCommand(_ChatCommand command) {
    if (!command.enabled) return;
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
    unawaited(_runMobileCommand(command.action!));
  }

  Future<void> _runMobileCommand(_ChatCommandAction action) async {
    try {
      await _executeMobileCommand(action);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _executeMobileCommand(_ChatCommandAction action) async {
    switch (action) {
      case _ChatCommandAction.newSession:
        if (_attachments.isNotEmpty) {
          final discard = await _confirmDiscardDraft();
          if (!mounted || !discard) return;
        }
        _persistDraft();
        final session = await _conn.createSession();
        if (mounted) {
          final cleanupWarning = await _discardUntouchedMobileSession();
          if (!mounted) return;
          await _conn.refreshSessions();
          if (mounted) {
            final messenger = ScaffoldMessenger.maybeOf(context);
            Navigator.of(context).pushReplacementNamed(
              '/chat/${session.id}',
              arguments: const ChatRouteArguments.newlyCreated(),
            );
            if (cleanupWarning != null) {
              messenger?.showSnackBar(SnackBar(content: Text(cleanupWarning)));
            }
          }
        }
        return;
      case _ChatCommandAction.sessions:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => GlobalSessionsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.workspaces:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => const HomeScreen(initialTab: 0),
            ),
          );
        }
        return;
      case _ChatCommandAction.files:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => Scaffold(
                appBar: AppBar(title: const Text('Project files')),
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
          throw const ProductException('OpenCode is reconnecting. Try again.');
        }
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
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
            MaterialPageRoute<void>(
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
      case _ChatCommandAction.integrations:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => IntegrationsScreen(controller: _conn),
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
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => SkillsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.tools:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => ToolsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.references:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => ReferencesScreen(
                controller: _conn,
                onSelected: _attachReference,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.status:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => SettingsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.diagnostics:
        if (mounted) {
          await Navigator.of(context).push(
            MaterialPageRoute<void>(
              builder: (_) => AppDiagnosticsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.appearance:
        if (mounted) {
          await showAppearancePicker(context, controller: _conn);
        }
        return;
      case _ChatCommandAction.diff:
        _showDiff();
        return;
      case _ChatCommandAction.context:
        await _showContext();
        return;
      case _ChatCommandAction.share:
        if (_shareUrl == null) {
          await _share();
        } else {
          await Clipboard.setData(ClipboardData(text: _shareUrl!));
          if (mounted) {
            ScaffoldMessenger.of(
              context,
            ).showSnackBar(const SnackBar(content: Text('Share link copied')));
          }
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
      case _ChatCommandAction.fork:
        await _openTimeline(forkMode: true);
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
    }
  }

  void _attachReference(ReferenceInfo reference) {
    final attachment = PromptAttachment.reference(
      name: reference.name,
      path: reference.path,
    );
    if (_attachments.any(
      (candidate) =>
          candidate.isDirectoryReference && candidate.url == attachment.url,
    )) {
      _showComposerNote('@${reference.name} is already in the prompt');
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

  Future<void> _renameCurrentSession() async {
    final current = _conn.sessionsById[widget.sessionID]?.title ?? '';
    final controller = TextEditingController(text: current);
    final title = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename session'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Title'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Rename'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (title == null || title.isEmpty) return;
    try {
      await _conn.renameSession(widget.sessionID, title);
      await _conn.refreshSessions();
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  String _transcriptMarkdown() {
    final title = _conn.sessionsById[widget.sessionID]?.title;
    final out = StringBuffer(
      '# ${title?.isNotEmpty == true ? title : 'OpenCode session'}\n',
    );
    if (_olderCursor != null) {
      out.write('\n> ${_chatL10n(context).historyLoadedOnly}\n');
    }
    for (final message in _messages) {
      if (message.info.id.startsWith('local-')) continue;
      out.write(
        '\n## ${message.info.role == 'assistant' ? 'Assistant' : 'User'}\n\n',
      );
      for (final part in message.parts) {
        if (part.type == 'text' && part.text.trim().isNotEmpty) {
          out.write('${part.text.trim()}\n\n');
        } else if (part.type == 'reasoning' && part.text.trim().isNotEmpty) {
          out.write(
            '<details><summary>Reasoning</summary>\n\n${part.text.trim()}\n\n</details>\n\n',
          );
        } else if (part.type == 'file') {
          out.write('- Attachment: ${part.filename ?? part.url ?? 'file'}\n');
        } else if (part.type == 'tool') {
          out.write('### Tool: ${part.toolName ?? 'tool'}\n\n');
          final output = part.toolState.output?.trim();
          if (output?.isNotEmpty == true) {
            out.write('```text\n$output\n```\n\n');
          }
        }
      }
      if (message.info.errorText case final error?) {
        out.write('> Error: $error\n');
      }
    }
    return out.toString().trimRight();
  }

  Future<void> _copyTranscript() async {
    await Clipboard.setData(ClipboardData(text: _transcriptMarkdown()));
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Transcript copied as Markdown')),
      );
    }
  }

  Future<void> _exportTranscript() async {
    final path = await FilePicker.saveFile(
      dialogTitle: 'Export session transcript',
      fileName:
          'opencode-${widget.sessionID.substring(0, widget.sessionID.length.clamp(0, 8))}.md',
      bytes: Uint8List.fromList(utf8.encode(_transcriptMarkdown())),
    );
    if (mounted && path != null) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Transcript saved')));
    }
  }

  /// One bottom sheet for every session view destination and the two
  /// transcript display toggles, replacing the old app-bar popup menu.
  /// One bottom sheet behind the app bar's single overflow: the session's
  /// views and transcript toggles, then its mutation and utility actions.
  Future<void> _openSessionMenu({
    required bool reverted,
    required bool shared,
  }) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (context) => LayoutBuilder(
        builder: (context, constraints) => ConstrainedBox(
          constraints: BoxConstraints(maxHeight: constraints.maxHeight * .85),
          child: _SessionMenuSheet(
            reasoningExpanded: _conn.transcriptReasoningExpanded,
            timestampsVisible: _conn.transcriptTimestampsVisible,
            todosAvailable: _conn.capabilities.sessionTodos,
            reverted: reverted,
            shared: shared,
            sharingAvailable: _conn.capabilities.sessionShare,
          ),
        ),
      ),
    );
    if (!mounted || action == null) return;
    switch (action) {
      case 'timeline':
        await _openTimeline();
      case 'context':
        await _showContext();
      case 'changes':
        _showDiff();
      case 'todos':
        _showTodos();
      case 'subagents':
        await _showSubagents();
      case 'thinking':
        await _runMobileCommand(_ChatCommandAction.thinking);
      case 'timestamps':
        await _runMobileCommand(_ChatCommandAction.timestamps);
      case 'retry':
        await _retryLast();
      case 'revert':
        await _revertLast();
      case 'restore':
        await _restore();
      case 'fork':
        await _fork();
      case 'compact':
        await _compact();
      case 'share':
        await _share();
      case 'unshare':
        await _stopSharing();
      case 'shell':
        await _runShellDialog();
      case 'slash':
        await _openCommandLauncher();
      case 'reload':
        await _load();
    }
  }

  void _showTodos() {
    showModalBottomSheet(
      context: context,
      showDragHandle: true,
      builder: (_) => _TodosSheet(conn: _conn, sessionID: widget.sessionID),
    );
  }

  Future<void> _showContext() async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => SessionContextScreen(
          controller: _conn,
          sessionID: widget.sessionID,
          initialMessages: List.unmodifiable(_messages),
          initialHasOlder: _olderCursor != null,
        ),
      ),
    );
  }

  Future<void> _showSubagents() async {
    final target = await Navigator.of(context).push<Session>(
      MaterialPageRoute<Session>(
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
  Future<void> _openSubagentSession(String sessionID) async {
    if (sessionID == widget.sessionID) return;
    try {
      final target =
          _conn.sessionsById[sessionID] ??
          await (await _requireActionRepository()).getSessionDetails(sessionID);
      if (!mounted) return;
      await _openRelatedSession(target);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _openParentSession() async {
    final parentID = _conn.sessionsById[widget.sessionID]?.parentID;
    if (parentID == null) return;
    try {
      final repository = await _requireActionRepository();
      final target =
          _conn.sessionsById[parentID] ??
          await repository.getSessionDetails(parentID);
      if (!mounted) return;
      await _openRelatedSession(target);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _openRelatedSession(Session target) async {
    if (_conn.directory != target.directory ||
        _conn.workspace != target.workspaceID) {
      await _conn.selectLocation(
        directory: target.directory,
        workspace: target.workspaceID,
      );
      if (!mounted) return;
    }
    Navigator.of(context).pushReplacementNamed('/chat/${target.id}');
  }

  Future<void> _showDiff() async {
    final prompt = await Navigator.of(context).push<String>(
      MaterialPageRoute<String>(
        builder: (_) => ReviewWorkspace(
          handoff: _handoff, // UX-103 review handoff
          loadDiffs: () async {
            final api = await _conn.prepareActionTransport();
            if (api == null) {
              throw const ProductException('OpenCode is reconnecting.');
            }
            return api.diff(widget.sessionID);
          },
          loadWorkingTreeDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw const ProductException('OpenCode is reconnecting.');
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
          loadBranchDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw const ProductException('OpenCode is reconnecting.');
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
          ? 'Reference kept for your next prompt — commands do not carry it.'
          : 'References kept for your next prompt — commands do not carry '
                'them.',
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
    _showComposerNote('Review comment added to the prompt');
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
    final path = stripPathLineSuffix(raw);
    final name = path.substring(path.lastIndexOf('/') + 1);
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw const ProductException('Not connected to the server right now.');
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
    final path = file.path;
    final api = await _conn.prepareActionTransport();
    if (path == null || path.isEmpty || api == null) {
      return FilePreviewData(
        name: file.displayName,
        mimeType: file.mimeType,
        error: 'The generated file is not available from this server.',
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
          ? 'The server returned empty image data.'
          : null,
    );
  }

  Future<void> _attachToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    await _addPreviewAttachment(
      filename: file.displayName,
      mimeType: data.mimeType ?? file.mimeType,
      data: data,
    );
    if (!mounted) return;
    _focus.requestFocus();
    _showComposerNote('${file.displayName} attached. Add your comment.');
  }

  Future<void> _attachProjectFile(String path, FilePreviewData data) =>
      _addPreviewAttachment(
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
    for (final file in files) {
      try {
        if (await file.length() > _maxAttachmentBytes) {
          throw const ProductException(
            'Each attachment must be 10 MB or smaller.',
          );
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
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(
        data.error ?? 'The file has no content to attach.',
      );
    }
    if (_attachments.length >= _maxAttachmentCount) {
      throw ProductException(
        'You can attach up to $_maxAttachmentCount files.',
      );
    }
    if (bytes.length > _maxAttachmentBytes) {
      throw const ProductException('Each attachment must be 10 MB or smaller.');
    }
    final currentBytes = _attachments.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes + bytes.length > _maxAggregateAttachmentBytes) {
      throw const ProductException(
        'Attachments must total no more than 20 MB.',
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

  // Composer text needs no leave-time confirmation: it persists as a
  // per-session draft and is restored when the chat reopens. Attachments
  // are not persisted (their bytes are too heavy for the draft store), so
  // losing them still asks first.
  Future<bool> _confirmDiscardDraft() => showConfirmSheet(
    context,
    sheetKey: const ValueKey('discard-chat-draft-dialog'),
    icon: Icons.delete_sweep_outlined,
    title: 'Discard unsent attachments?',
    message:
        'Attachments are not kept with your draft text and have not been '
        'sent to OpenCode.',
    confirmLabel: 'Discard attachments',
    cancelLabel: 'Keep editing',
    destructive: true,
  );

  Future<String?> _discardUntouchedMobileSession() async {
    if (!widget.discardIfUntouched ||
        _messages.isNotEmpty ||
        _pendingSends.isNotEmpty ||
        _sending ||
        // A typed draft persists per session, so the session must survive
        // to give that draft a home to be restored into.
        _composer.text.trim().isNotEmpty ||
        _conn.busySessions.contains(widget.sessionID)) {
      return null;
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) {
        throw const ProductException('OpenCode is reconnecting.');
      }
      final currentMessages = await api.messagePage(widget.sessionID, limit: 1);
      if (currentMessages.items.isNotEmpty || currentMessages.hasMore) {
        return null;
      }
      await api.deleteSession(widget.sessionID);
      try {
        await _conn.refreshSessions();
      } catch (_) {
        // The exact empty session is already gone. The destination screen will
        // reconcile on its normal refresh even if this optional refresh fails.
      }
      return null;
    } catch (_) {
      return 'Empty session was kept because OpenCode could not verify or remove it.';
    }
  }

  Future<void> _leaveChat() async {
    if (_leavingProvisionalSession) return;
    if (_attachments.isNotEmpty) {
      final discard = await _confirmDiscardDraft();
      if (!mounted || !discard) return;
    }
    _persistDraft();
    _leavingProvisionalSession = true;
    final messenger = ScaffoldMessenger.maybeOf(context);
    final warning = await _discardUntouchedMobileSession();
    if (!mounted) return;
    setState(() => _allowRoutePop = true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      Navigator.of(context).maybePop();
      if (warning != null) {
        messenger?.showSnackBar(SnackBar(content: Text(warning)));
      }
    });
  }

  Future<void> _downloadToolOutputFile(
    ToolOutputFile file,
    FilePreviewData data,
  ) async {
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw ProductException(data.error ?? 'The file has no content to save.');
    }
    final savedPath = await FilePicker.saveFile(
      dialogTitle: 'Save ${file.displayName}',
      fileName: file.displayName,
      bytes: bytes,
    );
    if (!mounted || savedPath == null) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('${file.displayName} saved to your device.')),
    );
  }

  /// The offline banner's queue line: drafts the next flush will send,
  /// plus drafts a flush will deliberately skip for other servers.
  String? _queuedNote() {
    final mine = _conn.queuedPromptCount;
    final others = _conn.queuedPromptCountForOtherProfiles;
    final parts = <String>[
      if (mine > 0)
        '$mine draft${mine == 1 ? '' : 's'} queued to send on reconnect.',
      if (others > 0)
        '$others draft${others == 1 ? '' : 's'} waiting for other servers.',
    ];
    return parts.isEmpty ? null : parts.join(' ');
  }

  /// A picker opened from an open chat applies to this session only. Other
  /// sessions keep the profile default; on OpenCode 2 the server also treats
  /// the model as session state.
  ModelPickerApplyScope get _modelApplyScope => ModelPickerApplyScope.session;

  /// The model as the catalog names it, falling back to the presented
  /// provider/model pair; never a raw wire ID.
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

  /// A permission request lands as an inline card above the composer —
  /// oldest first, one at a time — instead of a modal sheet that steals the
  /// keyboard mid-sentence. Animates in and out unless motion is reduced.
  Widget _attentionRegion(
    bool reduceMotion,
    List<PermissionRequest> pendingPermissions,
  ) {
    final permission = pendingPermissions.firstOrNull;
    final question = _conn.questionForSession(widget.sessionID);
    return AnimatedSize(
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
      alignment: Alignment.bottomCenter,
      child: AnimatedSwitcher(
        duration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 180),
        child: permission == null
            ? (question != null
                  ? _QuestionAttentionCard(
                      key: ValueKey('question-card-${question.id}'),
                      question: question,
                      replying: _questionReplying,
                      onAnswer: (answers) =>
                          unawaited(_answerQuestion(question, answers)),
                      onMore: () => unawaited(_showQuestionSheet(question)),
                    )
                  : _retryState == null
                  ? const SizedBox.shrink(key: ValueKey('permission-card-none'))
                  : _RetryAttentionCard(
                      key: const ValueKey('retry-banner'),
                      retry: _retryState!,
                      stopping: _aborting,
                      onStop: _abort,
                    ))
            : _PermissionAttentionCard(
                key: ValueKey('permission-card-${permission.id}'),
                permission: permission,
                replying: _permissionReplying,
                onReview: () => unawaited(_showPermissionDialog(permission)),
                onAllowOnce: () => unawaited(_allowPermissionOnce(permission)),
              ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    final busy = _conn.busySessions.contains(widget.sessionID);
    // OpenCode 1 runs a prompt sent mid-turn after that turn: every user
    // message past the assistant's current one is waiting, and says so.
    final queuedAfterIndex = busy && !_conn.supportsInbox
        ? _queuedAfterIndex(_messages)
        : -1;
    final displayParts = _timelineDisplayParts(_messages);
    final showAttachmentNote = _attachmentNoteVisible();
    final pendingPermissions = _conn.permissionsForSession(widget.sessionID);

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

    final screen = PopScope(
      canPop: _allowRoutePop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_leaveChat());
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            presentedSessionTitle(session, fallback: 'Chat'),
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            if (busy)
              IconButton(
                tooltip: 'Stop',
                icon: Icon(AppIcons.stop, color: theme.colorScheme.error),
                onPressed: _aborting ? null : _abort,
              ),
            IconButton(
              key: const ValueKey('session-actions-button'),
              tooltip: 'Session menu',
              icon: const Icon(Icons.more_vert_rounded),
              onPressed: () => unawaited(
                _openSessionMenu(
                  reverted: session?.reverted == true,
                  shared: shareUrl != null,
                ),
              ),
            ),
          ],
        ),
        body: Column(
          children: [
            if (_conn.status != StreamStatus.connected)
              ConnectionStatusBanner(controller: _conn, note: _queuedNote()),
            // At most one contextual strip below the connection truth, so
            // banners cannot stack three deep over the transcript: a prompt
            // error outranks subagent context, which outranks the share
            // notice (sharing stays visible in Session actions).
            if (_promptError case final promptError?)
              _PromptErrorBanner(
                message: promptError,
                onDismiss: () => setState(() => _promptError = null),
                onChooseModel: () => showModelPicker(
                  context,
                  applyScope: _modelApplyScope,
                  sessionID: widget.sessionID,
                ),
              )
            else if (parentID != null)
              _SubagentContextBanner(
                position: siblingIndex < 0 ? null : siblingIndex + 1,
                total: siblings.isEmpty ? null : siblings.length,
                onParent: _openParentSession,
                onAll: _showSubagents,
              )
            else if (shareUrl != null)
              _SharedSessionBanner(url: shareUrl, onStop: _stopSharing),
            Expanded(
              // Rehydrates and refreshes must not flash a skeleton or a
              // full-screen error over an already-visible transcript.
              // A permission card must not wait for the transcript: it is
              // pinned to the bottom of the skeleton and error states too.
              child: _loading && _messages.isEmpty
                  ? Column(
                      children: [
                        const Expanded(child: LoadingList(rows: 6)),
                        _attentionRegion(reduceMotion, pendingPermissions),
                      ],
                    )
                  : _error != null && _messages.isEmpty
                  ? Column(
                      children: [
                        Expanded(
                          child: ProductErrorState(
                            message: productErrorText(_error!),
                            onRetry: _load,
                          ),
                        ),
                        _attentionRegion(reduceMotion, pendingPermissions),
                      ],
                    )
                  : LayoutBuilder(
                      builder: (context, bodyConstraints) {
                        // The composer keeps one editor structure. A keyboard
                        // or short window reduces its line budget without
                        // reparenting the focused field or moving its controls.
                        final compactComposer =
                            MediaQuery.viewInsetsOf(context).bottom > 0 ||
                            bodyConstraints.maxHeight < 420;
                        return Column(
                          children: [
                            Expanded(
                              child: _messages.isEmpty && _olderCursor == null
                                  ? _EmptyTranscript(
                                      onSuggestion: _insertSuggestion,
                                    )
                                  : DesktopSelectionArea(
                                      child: MarkdownFileLinks(
                                        validate: _validatePathLink,
                                        open: _openPathLink,
                                        child: NotificationListener<ScrollNotification>(
                                          onNotification: _onTranscriptScroll,
                                          child: Stack(
                                            alignment: Alignment.topCenter,
                                            children: [
                                              ConstrainedBox(
                                                constraints:
                                                    const BoxConstraints(
                                                      maxWidth: 860,
                                                    ),
                                                child: ScrollablePositionedList.builder(
                                                  reverse: true,
                                                  itemScrollController:
                                                      _messageScroll,
                                                  itemPositionsListener:
                                                      _messagePositions,
                                                  padding:
                                                      const EdgeInsets.symmetric(
                                                        horizontal: 6,
                                                        vertical: 10,
                                                      ),
                                                  itemCount:
                                                      _renderedMessageCount +
                                                      (_olderCursor == null
                                                          ? 0
                                                          : 1),
                                                  itemBuilder: (context, i) {
                                                    if (i ==
                                                        _renderedMessageCount) {
                                                      return _olderHistoryRow();
                                                    }
                                                    // Reversed list: item 0 is
                                                    // the newest turn. The
                                                    // composer, not a
                                                    // transcript row, says
                                                    // when a run is active.
                                                    final index =
                                                        _renderedMessageCount -
                                                        1 -
                                                        i;
                                                    final m = _messages[index];
                                                    if (v2VariantPart(m)
                                                        case final tagged?) {
                                                      return V2TranscriptRow(
                                                        key: ValueKey(
                                                          'message-${m.info.id}',
                                                        ),
                                                        part: tagged,
                                                        messageId: m.info.id,
                                                      );
                                                    }
                                                    final meta = _messageMeta(
                                                      _messages,
                                                      index,
                                                    );
                                                    final parts =
                                                        displayParts[index];
                                                    if (parts.isEmpty &&
                                                        meta.isEmpty &&
                                                        m.info.errorText ==
                                                            null) {
                                                      return const SizedBox.shrink();
                                                    }
                                                    return _MessageView(
                                                      key: ValueKey(
                                                        'message-${m.info.id}',
                                                      ),
                                                      queued:
                                                          queuedAfterIndex >=
                                                              0 &&
                                                          m.info.role ==
                                                              'user' &&
                                                          index >
                                                              queuedAfterIndex,
                                                      m: m,
                                                      meta: meta,
                                                      parts: parts,
                                                      reasoningExpanded: _conn
                                                          .transcriptReasoningExpanded,
                                                      expansionStore:
                                                          _transcriptExpansion,
                                                      showTimestamp: _conn
                                                          .transcriptTimestampsVisible,
                                                      highlighted:
                                                          _highlightedMessageID ==
                                                          m.info.id,
                                                      onLongPress: () =>
                                                          unawaited(
                                                            _showMessageActions(
                                                              m,
                                                            ),
                                                          ),
                                                      contextActions: () =>
                                                          _messageContextActions(
                                                            m,
                                                          ),
                                                      filePreviewLoader:
                                                          _loadToolOutputFile,
                                                      onAttachFile:
                                                          _attachToolOutputFile,
                                                      onDownloadFile:
                                                          _downloadToolOutputFile,
                                                      onCompact: _compact,
                                                      onOpenProviders:
                                                          _openProviders,
                                                      onContinue:
                                                          _continueTruncated,
                                                      onChooseModel: () =>
                                                          showModelPicker(
                                                            context,
                                                            applyScope:
                                                                _modelApplyScope,
                                                            sessionID: widget
                                                                .sessionID,
                                                          ),
                                                      onOpenSession:
                                                          _openSubagentSession,
                                                    );
                                                  },
                                                ),
                                              ),
                                              if (_awayFromLatest)
                                                Positioned(
                                                  right: 14,
                                                  bottom: 10,
                                                  child: _JumpToLatestButton(
                                                    onTap: _jumpToLatest,
                                                  ),
                                                ),
                                              // Only while reading history, and
                                              // counting only the messages that
                                              // actually sit above the viewport.
                                              if (_awayFromLatest &&
                                                  _messages.length > 30)
                                                Positioned(
                                                  top: 8,
                                                  child: ValueListenableBuilder(
                                                    valueListenable:
                                                        _messagePositions
                                                            .itemPositions,
                                                    builder: (context, positions, _) {
                                                      final earlier =
                                                          _earlierMessageCount(
                                                            positions,
                                                          );
                                                      if (earlier <= 0) {
                                                        return const SizedBox.shrink();
                                                      }
                                                      return _EarlierMessagesPill(
                                                        count: earlier,
                                                        onTap: () => unawaited(
                                                          _openTimeline(),
                                                        ),
                                                      );
                                                    },
                                                  ),
                                                ),
                                            ],
                                          ),
                                        ),
                                      ),
                                    ),
                            ),
                            _attentionRegion(reduceMotion, pendingPermissions),
                            // §7 rule 5: v2-only surfaces stay silent on v1.
                            // The map is already empty there, but the gate is
                            // explicit so a stale entry cannot leak a form
                            // card onto a server that cannot answer it.
                            if (_conn.formForSession(widget.sessionID)
                                case final pendingForm?
                                when _conn.capabilities.forms)
                              _FormRequestCard(
                                key: ValueKey(
                                  'form-request-card-${pendingForm.id}',
                                ),
                                form: pendingForm,
                                onAnswer: () =>
                                    unawaited(_openForm(pendingForm)),
                              ),
                            // The offline-draft half of the strip is v1-safe;
                            // only the inbox bubbles are v2-only (§7 rule 5).
                            if ((
                                  drafts: _conn.queuedPromptsFor(
                                    widget.sessionID,
                                  ),
                                  inbox: _conn.capabilities.inbox
                                      ? _conn.inboxItemsFor(widget.sessionID)
                                      : const <Api2InboxItem>[],
                                )
                                case final pendingSends
                                when pendingSends.drafts.isNotEmpty ||
                                    pendingSends.inbox.isNotEmpty)
                              _PendingSendsStrip(
                                drafts: pendingSends.drafts,
                                inboxItems: pendingSends.inbox,
                                onEdit: _editQueuedPrompt,
                                onDiscard: _discardQueuedPrompt,
                                onCancelInbox: _cancelInboxSend,
                                onFlipDelivery: _flipInboxDelivery,
                              ),
                            AnimatedSize(
                              duration: reduceMotion
                                  ? Duration.zero
                                  : const Duration(milliseconds: 160),
                              curve: Curves.easeOutCubic,
                              child: _composerNote == null
                                  ? const SizedBox.shrink()
                                  : _ComposerNote(
                                      key: _composerNoteKey,
                                      text: _composerNote!,
                                    ),
                            ),
                            if (_canBackgroundWork || _backgrounding)
                              Align(
                                alignment: AlignmentDirectional.centerStart,
                                child: Padding(
                                  padding: const EdgeInsets.symmetric(
                                    horizontal: 12,
                                  ),
                                  child: Tooltip(
                                    message: _chatL10n(
                                      context,
                                    ).backgroundWorkShortcut,
                                    child: TextButton.icon(
                                      key: const Key('background-running-work'),
                                      style: TextButton.styleFrom(
                                        minimumSize: const Size(48, 48),
                                      ),
                                      onPressed: _backgrounding
                                          ? null
                                          : _backgroundRunningWork,
                                      icon: _backgrounding
                                          ? const SizedBox.square(
                                              dimension: 18,
                                              child: CircularProgressIndicator(
                                                strokeWidth: 2,
                                              ),
                                            )
                                          : const Icon(
                                              Icons.low_priority_rounded,
                                              size: 20,
                                            ),
                                      label: Text(
                                        _backgroundSupport ==
                                                BackgroundWorkSupport.subagents
                                            ? _chatL10n(
                                                context,
                                              ).backgroundSubagentsTitle
                                            : _chatL10n(
                                                context,
                                              ).backgroundWorkTitle,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            if (runningWorkCount > 0)
                              Center(
                                child: ConstrainedBox(
                                  constraints: const BoxConstraints(
                                    maxWidth: 860,
                                  ),
                                  child: Align(
                                    alignment: AlignmentDirectional.centerStart,
                                    child: Padding(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 12,
                                      ),
                                      child: TextButton.icon(
                                        key: const Key(
                                          'running-work-indicator',
                                        ),
                                        onPressed: _openRunningWork,
                                        style: TextButton.styleFrom(
                                          minimumSize: const Size(48, 48),
                                        ),
                                        icon: const Icon(
                                          Icons.account_tree_outlined,
                                          size: 20,
                                        ),
                                        label: Text(
                                          _chatL10n(
                                            context,
                                          ).workCount(runningWorkCount),
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            Center(
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 860,
                                ),
                                child: DesktopFileDropTarget(
                                  onDrop: _handleDroppedFiles,
                                  child: _ChatComposer(
                                    compact: compactComposer,
                                    allowInlineCommands:
                                        bodyConstraints.maxHeight >= 300,
                                    controller: _composer,
                                    focusNode: _focus,
                                    commands: _chatCommands,
                                    agents: _subagents,
                                    onSelectCommand: _selectChatCommand,
                                    onSelectAgent: _insertAgentMention,
                                    onOpenCommands: _openCommandLauncher,
                                    onOpenAgents: () => _openCommandLauncher(
                                      initialTab: _ComposerToolTab.agents,
                                    ),
                                    onOpenEditor: _openPromptEditor,
                                    onReusePrompt: _recentPrompts.isEmpty
                                        ? null
                                        : _reusePrompt,
                                    onClearText: _clearDraftText,
                                    attachments: _attachments,
                                    busy: busy,
                                    sending: _sending,
                                    // OpenCode 1 runs a send made mid-turn
                                    // after that turn; OpenCode 2 steers or
                                    // queues it. Either way Send stays live.
                                    canSendWhileBusy: true,
                                    canChooseDelivery: _conn.supportsInbox,
                                    delivery: _delivery,
                                    onDeliveryChanged: (delivery) =>
                                        setState(() => _delivery = delivery),
                                    voiceOpening: _voiceOpening,
                                    selectedAgent: _conn.agentForSession(
                                      widget.sessionID,
                                    ),
                                    defaultAgent: _defaultAgentName,
                                    selectedModel: _conn.modelForSession(
                                      widget.sessionID,
                                    ),
                                    modelLabel: _presentedModelLabel,
                                    selectionFallback:
                                        !_conn.serverOwnsSessionSelection
                                        ? null
                                        : _conn
                                              .selectionForSession(
                                                widget.sessionID,
                                              )
                                              .modelKnown
                                        ? _chatL10n(context).modelServerDefault
                                        : _chatL10n(
                                            context,
                                          ).modelSelectionLoading,
                                    selectedCatalogModel: _selectedCatalogModel,
                                    selectedVariant: _conn.variantForSession(
                                      widget.sessionID,
                                    ),
                                    showAttachmentNote: showAttachmentNote,
                                    onAttach: _pickAttachment,
                                    onContentInserted: (content) => unawaited(
                                      _handleInsertedContent(content),
                                    ),
                                    onVoice: _openVoice,
                                    onSend: _send,
                                    onStop: _abort,
                                    onChooseModel: () => showModelPicker(
                                      context,
                                      applyScope: _modelApplyScope,
                                      sessionID: widget.sessionID,
                                    ),
                                    contextUsage: _contextWindowUsage(),
                                    modelSwitch: _modelCycleButton(),
                                    onRemoveAttachment: (attachment) =>
                                        setState(
                                          () => _attachments.remove(attachment),
                                        ),
                                    // UX-103 review handoff (start).
                                    references: _stagedReferences,
                                    onRemoveReference: _removeStagedReference,
                                    // UX-103 review handoff (end).
                                  ),
                                ),
                              ),
                            ),
                          ],
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
    return ModelShortcuts(
      onCycle: _cycleModel,
      onBackground: _canBackgroundWork ? _backgroundRunningWork : null,
      child: screen,
    );
  }

  @override
  void dispose() {
    _persistDraft();
    _composer.removeListener(_scheduleDraftSave);
    WidgetsBinding.instance.removeObserver(this);
    _conn.removeListener(_onConnectionChanged);
    _handoff.store.removeListener(_onHandoffChanged); // UX-103 review handoff
    _sub.cancel();
    _streamFlushTimer?.cancel();
    _highlightTimer?.cancel();
    _composerNoteTimer?.cancel();
    _retryTicker?.cancel();
    unawaited(_voice?.cancel());
    if (widget.voiceController == null) _voice?.dispose();
    _composer.dispose();
    _focus.dispose();
    _historyRefreshTimer?.cancel();
    _historyChanges.dispose();
    super.dispose();
  }
}

/// Compact attention card for a pending form of the open session (design
/// doc §2): icon, form title, question count, and an Answer button that
/// opens the shared form renderer.
class _FormRequestCard extends StatelessWidget {
  const _FormRequestCard({
    super.key,
    required this.form,
    required this.onAnswer,
  });

  final Api2FormInfo form;
  final VoidCallback onAnswer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final count = form.fields.length;
    return Center(
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 860),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 4, 12, 2),
          child: Material(
            color: theme.colorScheme.surfaceContainerLow,
            borderRadius: BorderRadius.circular(12),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: onAnswer,
              child: Container(
                constraints: const BoxConstraints(minHeight: 72),
                padding: const EdgeInsets.fromLTRB(14, 10, 12, 10),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.fact_check_outlined,
                      color: theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            form.title ?? 'Input requested',
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: theme.textTheme.titleSmall,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '$count question${count == 1 ? '' : 's'}',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 12),
                    FilledButton.tonal(
                      key: ValueKey('form-request-answer-${form.id}'),
                      onPressed: onAnswer,
                      child: const Text('Answer'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

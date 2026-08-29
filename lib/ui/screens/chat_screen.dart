import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../../api/models.dart';
import '../../api/opencode_api.dart' show ApiException;
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../api/sse.dart';
import '../../state/offline_queue.dart';
import '../../state/connection.dart';
import '../../voice/controller.dart';
import '../../voice/voice_ui.dart';
import '../navigation/chat_route.dart';
import '../permission_presentation.dart';
import '../app_theme.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/connection_status_banner.dart';
import '../widgets/entrance.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/file_preview.dart';
import '../widgets/markdown.dart';
import '../widgets/pickers.dart';
import '../widgets/product_states.dart';
import '../widgets/tool_card.dart';
import 'app_diagnostics_screen.dart';
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
part 'chat/composer.dart';
part 'chat/permission_dialog.dart';
part 'chat/message_view.dart';
part 'chat/session_sheets.dart';

const _maxAttachmentCount = 5;
const _maxAttachmentBytes = 10 * 1024 * 1024;
const _maxAggregateAttachmentBytes = 20 * 1024 * 1024;

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

  const ChatScreen({
    super.key,
    required this.sessionID,
    this.voiceController,
    this.initialText = '',
    this.initialAttachments = const [],
    this.discardIfUntouched = false,
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

class _ChatScreenState extends State<ChatScreen> with WidgetsBindingObserver {
  late final ConnectionController _conn;
  late final StreamSubscription<EventEnvelope> _sub;
  List<MessageWithParts> _messages = [];
  bool _loading = true;
  Object? _error;
  final _composer = TextEditingController();
  final _focus = FocusNode();
  final _messageScroll = ItemScrollController();
  bool _awayFromLatest = false;
  final List<PromptAttachment> _attachments = [];
  final List<_PendingSend> _pendingSends = [];
  final Map<String, int> _messageVersions = {};
  final Map<String, int> _partVersions = {};
  final Map<String, List<({String field, String delta})>> _deferredPartDeltas =
      {};
  int _eventVersion = 0;
  int _loadGeneration = 0;
  int _dataRefreshRevision = 0;
  bool _sending = false;
  bool _aborting = false;
  bool _permissionDialogScheduled = false;
  bool _permissionDismissScheduled = false;
  String? _activePermissionID;
  Route<void>? _activePermissionRoute;
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
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _conn.addListener(_onConnectionChanged);
    _load();
    unawaited(_loadServerCommands());
    _sub = _conn.events.listen(_onEvent);
    _schedulePermissionDialog();
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
    }
  }

  ConnectionController _readConn() {
    final ctx = context;
    final container = ProviderScope.containerOf(ctx, listen: false);
    return container.read(connProvider);
  }

  void _onEvent(EventEnvelope env) {
    if (!mounted) return;
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
            delta != null) {
          setState(() {
            if (_isSupportedDeltaField(field)) {
              _partVersions[_partKey(messageID, partID)] = ++_eventVersion;
              if (!_applyPartDelta(messageID, partID, field, delta)) {
                _deferredPartDeltas
                    .putIfAbsent(_partKey(messageID, partID), () => [])
                    .add((field: field, delta: delta));
              }
            }
          });
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
            _messages.removeWhere((message) => message.info.id == messageID);
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
            }
            _messageVersions[msg.id] = ++_eventVersion;
            if (!_reconcilePendingMessage(msg)) {
              final idx = _messages.indexWhere((m) => m.info.id == msg.id);
              if (idx >= 0) {
                _messages[idx] = MessageWithParts(
                  info: msg,
                  parts: _messages[idx].parts,
                );
              } else {
                _messages.add(MessageWithParts(info: msg));
              }
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
        break;
      case 'session.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic> &&
            info['id']?.toString() == widget.sessionID) {
          if (mounted) setState(() {});
        }
        break;
    }
  }

  String _partKey(String messageID, String partID) => '$messageID\u0000$partID';

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
    var bundle = _messages.firstWhere(
      (m) => m.info.id == messageID,
      orElse: () {
        final b = MessageWithParts(
          info: MessageInfo(
            id: messageID,
            sessionID: widget.sessionID,
            role: 'assistant',
          ),
        );
        _messages.add(b);
        return b;
      },
    );
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
    if (bundle == null) return false;
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

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final versionAtStart = _eventVersion;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final api = _conn.api;
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final msgs = await api.messages(widget.sessionID);
      msgs.sort(
        (a, b) =>
            (a.info.time?.created ?? 0).compareTo(b.info.time?.created ?? 0),
      );
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _messages = _mergeHydratedMessages(msgs, versionAtStart));
    } catch (e) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() => _error = e);
    } finally {
      if (mounted && generation == _loadGeneration) {
        setState(() => _loading = false);
      }
    }
  }

  List<MessageWithParts> _mergeHydratedMessages(
    List<MessageWithParts> hydrated,
    int versionAtStart,
  ) {
    for (final message in hydrated) {
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
      for (final message in _messages) message.info.id: message,
    };
    final merged = <MessageWithParts>[];
    final hydratedIDs = <String>{};
    for (final snapshot in hydrated) {
      final messageID = snapshot.info.id;
      hydratedIDs.add(messageID);
      final current = currentByID[messageID];
      final messageChanged =
          (_messageVersions[messageID] ?? 0) > versionAtStart;
      if (messageChanged && current == null) continue;

      final currentParts = {
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
    }

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
      if (isPending || hasNewMessage || hasNewPart) merged.add(current);
    }
    return merged;
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
    List<PromptAgentMention> mentions,
  ) async {
    final profileID = _conn.profile?.id;
    if (profileID == null) return false;
    final now = DateTime.now();
    final queued = await _conn.queuePrompt(
      QueuedPrompt(
        id: 'queued-${now.microsecondsSinceEpoch}',
        profileID: profileID,
        sessionID: widget.sessionID,
        text: text,
        attachments: attachments,
        mentions: mentions,
        modelProviderID: _conn.selectedModel?.providerID,
        modelID: _conn.selectedModel?.modelID,
        agent: _conn.selectedAgent,
        variant: _conn.selectedVariant,
        createdAt: now.millisecondsSinceEpoch,
      ),
    );
    if (!mounted) return queued;
    if (queued) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Queued — will send when reconnected')),
      );
    } else {
      _showActionError(
        'This draft exceeds the attachment size limits and cannot be queued.',
      );
    }
    return queued;
  }

  Future<void> _editQueuedPrompt(QueuedPrompt entry) async {
    await _conn.removeQueuedPrompt(entry.id);
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
    if (confirmed) await _conn.removeQueuedPrompt(entry.id);
  }

  Future<void> _send() async {
    await _voice?.cancel();
    if (_sending || (_composer.text.trim().isEmpty && _attachments.isEmpty)) {
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
      await _submitTypedCommand(typedCommand);
      return;
    }
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
    final createdAt = DateTime.now().millisecondsSinceEpoch;
    final localID = 'local-$createdAt-${DateTime.now().microsecondsSinceEpoch}';
    final pending = _PendingSend(
      localID: localID,
      text: text,
      attachments: attachments,
      createdAt: createdAt,
    );
    _composer.clear();
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
      await actionApi.promptAsync(
        widget.sessionID,
        text: text,
        model: _conn.selectedModel,
        agent: _conn.selectedAgent.isNotEmpty ? _conn.selectedAgent : null,
        variant: _conn.selectedVariant.isEmpty ? null : _conn.selectedVariant,
        attachments: attachments,
        agentMentions: agentMentions,
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
      if (e is ApiException && e.statusCode == null) {
        if (await _queueDraft(text, attachments, agentMentions)) return;
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Send failed: $e')));
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
      await actionApi.slashCommand(
        widget.sessionID,
        command.serverCommand!.name,
        typed.arguments,
        model: _conn.selectedModel,
        variant: _conn.selectedVariant.isEmpty ? null : _conn.selectedVariant,
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
      _showActionError('Command failed: $error');
    }
  }

  Future<void> _openVoice() async {
    if (_voiceOpening || _sending) return;
    setState(() => _voiceOpening = true);
    try {
      final voice = await _getVoice();
      if (!mounted) return;
      if (!voice.models.isReady) {
        final ready = await showVoiceModelSetupSheet(context, voice.models);
        if (!ready || !mounted) return;
      }
      final transcript = await showVoiceComposerSheet(context, voice);
      if (!mounted || transcript == null || transcript.trim().isEmpty) return;
      final selection = _composer.selection;
      _composer.text = mergeVoiceDraft(_composer.text, selection, transcript);
      _composer.selection = TextSelection.collapsed(
        offset: _composer.text.length,
      );
      _focus.requestFocus();
      setState(() {});
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

  Future<PromptAttachment?> _chooseAttachment(
    List<PromptAttachment> current,
  ) async {
    if (current.length >= _maxAttachmentCount) {
      throw StateError('You can attach up to $_maxAttachmentCount files.');
    }
    final currentBytes = current.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes >= _maxAggregateAttachmentBytes) {
      throw StateError('Attachments must total no more than 20 MB.');
    }
    final file = await FilePicker.pickFile(dialogTitle: 'Attach to prompt');
    if (file == null) return null;
    final size = await file.length();
    if (size > _maxAttachmentBytes) {
      throw StateError('Each attachment must be 10 MB or smaller.');
    }
    if (size > 0 && currentBytes + size > _maxAggregateAttachmentBytes) {
      throw StateError('Attachments must total no more than 20 MB.');
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
      throw StateError('Attachments must total no more than 20 MB.');
    }
    if (bytes == null) {
      throw StateError('Each attachment must be 10 MB or smaller.');
    }
    final mime = _mimeForFilename(file.name);
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
      if (mounted) _showActionError('Could not stop generation: $error');
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
      if (url == null) throw StateError('No share link was returned');
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

  Future<ProductRepository> _requireActionRepository() async {
    final repository = await _conn.prepareActionRepository();
    if (repository != null) return repository;
    throw StateError(
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
      builder: (context) =>
          _TimelineSheet(messages: _messages, forkMode: forkMode),
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
      setState(() => _awayFromLatest = away);
    }
    return false;
  }

  void _jumpToLatest() {
    if (!_messageScroll.isAttached) return;
    if (MediaQuery.disableAnimationsOf(context)) {
      _messageScroll.jumpTo(index: 0);
    } else {
      _messageScroll.scrollTo(
        index: 0,
        duration: const Duration(milliseconds: 360),
        curve: Curves.easeOutCubic,
      );
    }
    setState(() => _awayFromLatest = false);
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
    setState(() => _highlightedMessageID = messageID);
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

  Future<void> _showMessageActions(MessageWithParts message) async {
    final text = message.parts
        .where((part) => part.type == 'text' && !part.synthetic)
        .map((part) => part.text)
        .where((value) => value.trim().isNotEmpty)
        .join('\n\n');
    final canFork = message.info.role == 'user';
    final theme = Theme.of(context);
    final action = await showModalBottomSheet<String>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (text.isNotEmpty)
              ListTile(
                key: const ValueKey('message-action-copy'),
                leading: const Icon(Icons.copy_rounded),
                title: const Text('Copy message text'),
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
    if (action == 'copy') {
      await Clipboard.setData(ClipboardData(text: text));
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Message text copied')));
    }
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
      await _load();
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
    final model = _conn.selectedModel;
    if (model == null) {
      _showActionError('Select a model before compacting this session.');
      return;
    }
    try {
      final repository = await _requireActionRepository();
      await repository.compactSession(
        widget.sessionID,
        providerID: model.providerID,
        modelID: model.modelID,
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
      await _load();
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _restore() async {
    try {
      final repository = await _requireActionRepository();
      await repository.restoreSession(widget.sessionID);
      await _load();
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
      if (api == null) throw StateError('OpenCode is reconnecting.');
      await api.promptAsync(
        widget.sessionID,
        text: text,
        model: _conn.selectedModel,
        agent: _conn.selectedAgent.isEmpty ? null : _conn.selectedAgent,
        variant: _conn.selectedVariant.isEmpty ? null : _conn.selectedVariant,
        attachments: attachments,
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  void _showActionError(Object error) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(error.toString())));
  }

  // ----- dialogs -----

  void _onConnectionChanged() {
    if (!mounted) return;
    final shouldRehydrate =
        _dataRefreshRevision != _conn.dataRefreshRevision && _conn.api != null;
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _dismissResolvedPermissionDialog();
    setState(() {});
    if (shouldRehydrate) unawaited(_load());
    _schedulePermissionDialog();
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
      }
    });
  }

  void _schedulePermissionDialog() {
    if (!mounted || _permissionDialogScheduled || _activePermissionID != null) {
      return;
    }
    final pending = _conn.permissionsForSession(widget.sessionID);
    if (pending.isEmpty) return;
    final permission = pending.first;
    _permissionDialogScheduled = true;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _permissionDialogScheduled = false;
      if (!mounted) return;
      final current = _conn.permissions[permission.id];
      if (current == null || current.sessionID != widget.sessionID) {
        _schedulePermissionDialog();
        return;
      }
      unawaited(_showPermissionDialog(current));
    });
  }

  Future<void> _showPermissionDialog(PermissionRequest permission) async {
    _activePermissionID = permission.id;
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) {
        _activePermissionRoute = ModalRoute.of<void>(dialogContext);
        _dismissResolvedPermissionDialog();
        return _PermissionDialog(
          permission: permission,
          onReply: (reply) => _conn.answerPermission(permission.id, reply),
        );
      },
    );
    if (!mounted) return;
    _activePermissionID = null;
    _activePermissionRoute = null;
    _schedulePermissionDialog();
  }

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
      if (api == null) throw StateError('OpenCode is reconnecting.');
      await api.shell(
        widget.sessionID,
        command: cmd,
        agent: _conn.selectedAgent.isNotEmpty ? _conn.selectedAgent : 'build',
        model: _conn.selectedModel,
        variant: _conn.selectedVariant.isEmpty ? null : _conn.selectedVariant,
      );
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Failed: $e')));
      }
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
        throw StateError('OpenCode commands are unavailable offline.');
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
      _ChatCommand.mobile(
        slash: 'warp',
        title: 'Warp session',
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
      _ChatCommand.mobile(
        slash: 'tools',
        title: 'Tools and capabilities',
        description: 'Inspect tools callable by the active provider and model',
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
        if (_hasUnsentDraft) {
          final discard = await _confirmDiscardDraft();
          if (!mounted || !discard) return;
        }
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
        if (mounted) await showModelPicker(context);
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
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('@${reference.name} is already in the prompt')),
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
  Future<void> _openSessionViews() async {
    final action = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (context) => LayoutBuilder(
        builder: (context, constraints) => ConstrainedBox(
          constraints: BoxConstraints(maxHeight: constraints.maxHeight * .85),
          child: _SessionViewsSheet(
            reasoningExpanded: _conn.transcriptReasoningExpanded,
            timestampsVisible: _conn.transcriptTimestampsVisible,
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
    }
  }

  /// One bottom sheet for the session's mutation and utility actions,
  /// replacing the old unlabeled app-bar overflow menu.
  Future<void> _openSessionActions({
    required bool reverted,
    required bool shared,
  }) async {
    final action = await showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (context) => LayoutBuilder(
        builder: (context, constraints) => ConstrainedBox(
          constraints: BoxConstraints(maxHeight: constraints.maxHeight * .85),
          child: _SessionActionsSheet(reverted: reverted, shared: shared),
        ),
      ),
    );
    if (!mounted || action == null) return;
    switch (action) {
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
          loadDiffs: () async {
            final api = await _conn.prepareActionTransport();
            if (api == null) {
              throw StateError('OpenCode is reconnecting.');
            }
            return api.diff(widget.sessionID);
          },
          loadWorkingTreeDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw StateError('OpenCode is reconnecting.');
            }
            return repository.listVcsDiffs(VcsDiffMode.workingTree);
          },
          loadBranchDiffs: () async {
            final repository = await _conn.prepareActionRepository();
            if (repository == null) {
              throw StateError('OpenCode is reconnecting.');
            }
            return repository.listVcsDiffs(VcsDiffMode.branch);
          },
        ),
      ),
    );
    if (!mounted || prompt == null || prompt.trim().isEmpty) return;
    _addReviewPrompt(prompt);
  }

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
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(
        content: Text('Review comment added to the prompt'),
        duration: Duration(seconds: 2),
      ),
    );
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
        throw StateError('Not connected to the server right now.');
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
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not open $name: $error')));
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
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('${file.displayName} attached. Add your comment.'),
        duration: const Duration(seconds: 2),
      ),
    );
  }

  Future<void> _attachProjectFile(String path, FilePreviewData data) =>
      _addPreviewAttachment(
        filename: path.split('/').last,
        mimeType: data.mimeType,
        data: data,
      );

  Future<void> _addPreviewAttachment({
    required String filename,
    required String? mimeType,
    required FilePreviewData data,
  }) async {
    final bytes = data.exportBytes;
    if (data.error != null || bytes == null) {
      throw StateError(data.error ?? 'The file has no content to attach.');
    }
    if (_attachments.length >= _maxAttachmentCount) {
      throw StateError('You can attach up to $_maxAttachmentCount files.');
    }
    if (bytes.length > _maxAttachmentBytes) {
      throw StateError('Each attachment must be 10 MB or smaller.');
    }
    final currentBytes = _attachments.fold<int>(
      0,
      (total, attachment) => total + _attachmentByteLength(attachment),
    );
    if (currentBytes + bytes.length > _maxAggregateAttachmentBytes) {
      throw StateError('Attachments must total no more than 20 MB.');
    }
    final mime = mimeType ?? 'application/octet-stream';
    final attachment = PromptAttachment(
      mime: mime,
      filename: filename,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    if (!mounted) return;
    setState(() => _attachments.add(attachment));
  }

  bool get _hasUnsentDraft =>
      _composer.text.isNotEmpty || _attachments.isNotEmpty;

  Future<bool> _confirmDiscardDraft() => showConfirmSheet(
    context,
    sheetKey: const ValueKey('discard-chat-draft-dialog'),
    icon: Icons.delete_sweep_outlined,
    title: 'Discard unsent draft?',
    message: 'Your text and attachments have not been sent to OpenCode.',
    confirmLabel: 'Discard draft',
    cancelLabel: 'Keep editing',
    destructive: true,
  );

  Future<String?> _discardUntouchedMobileSession() async {
    if (!widget.discardIfUntouched ||
        _messages.isNotEmpty ||
        _pendingSends.isNotEmpty ||
        _sending ||
        _conn.busySessions.contains(widget.sessionID)) {
      return null;
    }
    try {
      final api = await _conn.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final currentMessages = await api.messages(widget.sessionID);
      if (currentMessages.isNotEmpty) return null;
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
    if (_hasUnsentDraft) {
      final discard = await _confirmDiscardDraft();
      if (!mounted || !discard) return;
    }
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
      throw StateError(data.error ?? 'The file has no content to save.');
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final busy = _conn.busySessions.contains(widget.sessionID);
    final displayParts = _timelineDisplayParts(_messages);

    final session = _conn.sessionsById[widget.sessionID];
    final title = session?.title;
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

    return PopScope(
      canPop: _allowRoutePop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_leaveChat());
      },
      child: Scaffold(
        appBar: AppBar(
          title: Text(
            title?.isNotEmpty == true ? title! : 'Chat',
            overflow: TextOverflow.ellipsis,
          ),
          actions: [
            IconButton(
              tooltip: 'Session views',
              icon: const Icon(Icons.view_agenda_outlined),
              onPressed: () => unawaited(_openSessionViews()),
            ),
            if (busy)
              IconButton(
                tooltip: 'Stop',
                icon: Icon(
                  Icons.stop_circle_outlined,
                  color: theme.colorScheme.error,
                ),
                onPressed: _aborting ? null : _abort,
              ),
            IconButton(
              key: const ValueKey('session-actions-button'),
              tooltip: 'Session actions',
              icon: const Icon(Icons.more_vert_rounded),
              onPressed: () => unawaited(
                _openSessionActions(
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
              ConnectionStatusBanner(
                controller: _conn,
                note: _conn.queuedPromptCount > 0
                    ? '${_conn.queuedPromptCount} draft'
                          '${_conn.queuedPromptCount == 1 ? '' : 's'} queued '
                          'to send on reconnect.'
                    : null,
              ),
            // At most one contextual strip below the connection truth, so
            // banners cannot stack three deep over the transcript: a prompt
            // error outranks subagent context, which outranks the share
            // notice (sharing stays visible in Session actions).
            if (_promptError case final promptError?)
              _PromptErrorBanner(
                message: promptError,
                onDismiss: () => setState(() => _promptError = null),
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
              child: _loading
                  ? const LoadingList(rows: 6)
                  : _error != null
                  ? ProductErrorState(message: '$_error', onRetry: _load)
                  : LayoutBuilder(
                      builder: (context, bodyConstraints) {
                        // Keyboard insets reduce [bodyConstraints] but do not
                        // change the device's layout class. Deriving compact
                        // mode from those shrinking constraints replaces the
                        // focused TextField and immediately dismisses Android's
                        // keyboard. Keep one composer structure for the lifetime
                        // of the focus interaction instead.
                        final compactComposer =
                            MediaQuery.sizeOf(context).height < 520;
                        return Column(
                          children: [
                            Expanded(
                              child: _messages.isEmpty
                                  ? _EmptyTranscript(
                                      onSuggestion: _insertSuggestion,
                                    )
                                  : MarkdownFileLinks(
                                      validate: _validatePathLink,
                                      open: _openPathLink,
                                      child: NotificationListener<ScrollNotification>(
                                        onNotification: _onTranscriptScroll,
                                        child: Stack(
                                          alignment: Alignment.topCenter,
                                          children: [
                                            ConstrainedBox(
                                              constraints: const BoxConstraints(
                                                maxWidth: 860,
                                              ),
                                              child: ScrollablePositionedList.builder(
                                                reverse: true,
                                                itemScrollController:
                                                    _messageScroll,
                                                padding:
                                                    const EdgeInsets.symmetric(
                                                      horizontal: 12,
                                                      vertical: 10,
                                                    ),
                                                itemCount:
                                                    _messages.length +
                                                    (busy ? 1 : 0),
                                                itemBuilder: (context, i) {
                                                  final index =
                                                      _messages.length - 1 - i;
                                                  if (index < 0) {
                                                    return _TypingIndicator();
                                                  }
                                                  final m = _messages[index];
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
                                                    m: m,
                                                    meta: meta,
                                                    parts: parts,
                                                    reasoningExpanded: _conn
                                                        .transcriptReasoningExpanded,
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
                                                    filePreviewLoader:
                                                        _loadToolOutputFile,
                                                    onAttachFile:
                                                        _attachToolOutputFile,
                                                    onDownloadFile:
                                                        _downloadToolOutputFile,
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
                                            if (_messages.length > 30)
                                              Positioned(
                                                top: 8,
                                                child: _EarlierMessagesPill(
                                                  count: _messages.length,
                                                  onTap: () => unawaited(
                                                    _openTimeline(),
                                                  ),
                                                ),
                                              ),
                                          ],
                                        ),
                                      ),
                                    ),
                            ),
                            if (_conn.queuedPromptsFor(widget.sessionID)
                                case final queuedDrafts
                                when queuedDrafts.isNotEmpty)
                              _QueuedPromptsStrip(
                                entries: queuedDrafts,
                                onEdit: _editQueuedPrompt,
                                onDiscard: _discardQueuedPrompt,
                              ),
                            Center(
                              child: ConstrainedBox(
                                constraints: const BoxConstraints(
                                  maxWidth: 860,
                                ),
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
                                  attachments: _attachments,
                                  busy: busy,
                                  sending: _sending,
                                  voiceOpening: _voiceOpening,
                                  selectedAgent: _conn.selectedAgent,
                                  selectedModel: _conn.selectedModel,
                                  selectedVariant: _conn.selectedVariant,
                                  onAttach: _pickAttachment,
                                  onVoice: _openVoice,
                                  onSend: _send,
                                  onStop: _abort,
                                  onChooseModel: () => showModelPicker(context),
                                  contextUsage: _contextWindowUsage(),
                                  onRemoveAttachment: (attachment) => setState(
                                    () => _attachments.remove(attachment),
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
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _conn.removeListener(_onConnectionChanged);
    _sub.cancel();
    _highlightTimer?.cancel();
    unawaited(_voice?.cancel());
    if (widget.voiceController == null) _voice?.dispose();
    _composer.dispose();
    _focus.dispose();
    super.dispose();
  }
}

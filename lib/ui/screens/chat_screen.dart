import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:scrollable_positioned_list/scrollable_positioned_list.dart';

import '../../api/models.dart';
import '../../api/provider_presentation.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../../voice/controller.dart';
import '../../voice/voice_ui.dart';
import '../permission_presentation.dart';
import '../widgets/appearance_picker.dart';
import '../widgets/file_preview.dart';
import '../widgets/markdown.dart';
import '../widgets/pickers.dart';
import '../widgets/tool_card.dart';
import 'files_screen.dart';
import 'global_sessions_screen.dart';
import 'home_screen.dart';
import 'library_screen.dart';
import 'review_workspace.dart';
import 'session_destination_sheet.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';

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
// Sessions tab
// =====================================================================

class SessionsTab extends StatelessWidget {
  final ConnectionController controller;
  const SessionsTab({super.key, required this.controller});

  Future<void> _newChat(BuildContext context) async {
    try {
      final session = await controller.createSession();
      if (!context.mounted) return;
      Navigator.of(context).pushNamed('/chat/${session.id}');
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not create chat: $e')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: controller,
      builder: (context, _) {
        final sessions = controller.sortedSessions();
        return Stack(
          children: [
            if (sessions.isEmpty && !controller.isConnected)
              Center(
                child: Text(
                  'Not connected',
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    color: Theme.of(context).hintColor,
                  ),
                ),
              )
            else if (sessions.isEmpty)
              Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.forum_outlined,
                      size: 44,
                      color: Theme.of(context).hintColor,
                    ),
                    const SizedBox(height: 12),
                    const Text('No chats yet'),
                    const SizedBox(height: 12),
                    FilledButton.icon(
                      onPressed: () => _newChat(context),
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('Start one'),
                    ),
                  ],
                ),
              )
            else
              RefreshIndicator(
                onRefresh: controller.refreshSessions,
                child: ListView.builder(
                  itemCount: sessions.length,
                  itemBuilder: (context, i) {
                    final s = sessions[i];
                    final busy = controller.busySessions.contains(s.id);
                    return ListTile(
                      leading: busy
                          ? SizedBox(
                              width: 18,
                              height: 18,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Theme.of(context).colorScheme.primary,
                              ),
                            )
                          : Icon(
                              Icons.chat_bubble_outline_rounded,
                              size: 20,
                              color: Theme.of(context).hintColor,
                            ),
                      title: Text(
                        s.title?.isNotEmpty == true ? s.title! : 'New chat',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        _fmtSessionTime(
                          s.time?.updated ?? s.time?.created ?? 0,
                        ),
                      ),
                      trailing: PopupMenuButton<String>(
                        onSelected: (v) => _sessionAction(context, v, s),
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'rename', child: Text('Rename')),
                          PopupMenuItem(value: 'delete', child: Text('Delete')),
                        ],
                      ),
                      onTap: () =>
                          Navigator.of(context).pushNamed('/chat/${s.id}'),
                    );
                  },
                ),
              ),
            Positioned(
              right: 16,
              bottom: 16,
              child: FloatingActionButton.extended(
                heroTag: 'newChat',
                onPressed: () => _newChat(context),
                icon: const Icon(Icons.add_rounded),
                label: const Text('New chat'),
              ),
            ),
          ],
        );
      },
    );
  }

  Future<void> _rename(BuildContext context, Session s) async {
    var draftTitle = s.title ?? '';
    final title = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Rename chat'),
        content: TextFormField(
          initialValue: draftTitle,
          autofocus: true,
          onChanged: (value) => draftTitle = value,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, draftTitle.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    if (title != null && title.isNotEmpty) {
      await controller.renameSession(s.id, title);
      await controller.refreshSessions();
    }
  }

  Future<bool> _confirmDelete(BuildContext context, Session session) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Delete chat?'),
            content: Text(
              '“${session.title?.isNotEmpty == true ? session.title : 'Untitled chat'}” and its history will be permanently removed.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.error,
                ),
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Delete'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Future<void> _sessionAction(
    BuildContext context,
    String action,
    Session session,
  ) async {
    try {
      if (action == 'rename') {
        await _rename(context, session);
      } else if (action == 'delete') {
        if (!await _confirmDelete(context, session)) return;
        await controller.deleteSession(session.id);
        await controller.refreshSessions();
      }
    } catch (error) {
      if (!context.mounted) return;
      final verb = action == 'delete' ? 'delete' : 'rename';
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text('Could not $verb chat: $error')));
    }
  }
}

// =====================================================================
// Chat screen
// =====================================================================

class ChatScreen extends StatefulWidget {
  final String sessionID;
  final VoiceComposerController? voiceController;
  final String initialText;
  final List<PromptAttachment> initialAttachments;

  const ChatScreen({
    super.key,
    required this.sessionID,
    this.voiceController,
    this.initialText = '',
    this.initialAttachments = const [],
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

class _ChatScreenState extends State<ChatScreen> with WidgetsBindingObserver {
  late final ConnectionController _conn;
  late final StreamSubscription<EventEnvelope> _sub;
  List<MessageWithParts> _messages = [];
  bool _loading = true;
  Object? _error;
  final _composer = TextEditingController();
  final _focus = FocusNode();
  final _messageScroll = ItemScrollController();
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

  Future<void> _send() async {
    await _voice?.cancel();
    if (_sending || (_composer.text.trim().isEmpty && _attachments.isEmpty)) {
      return;
    }
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
        _attachments.insertAll(0, attachments);
      });
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Share this session?'),
        content: const Text(
          'Anyone with the link can view this session’s conversation and shared context. '
          'Do not share sessions containing secrets, credentials, or private files.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Share session'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
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
      showDragHandle: false,
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
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Revert from this prompt?'),
        content: const Text(
          'Messages and file changes after the most recent prompt will be rolled back.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Revert'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
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
        slash: 'references',
        aliases: const ['reference', 'refs'],
        title: 'Project references',
        description: 'Add an OpenCode project reference to this prompt',
        group: 'OpenCode',
        action: _ChatCommandAction.references,
      ),
      _ChatCommand.mobile(
        slash: 'status',
        aliases: const ['debug'],
        title: 'Server status',
        description: 'Connection health, server version, and live mode',
        group: 'OpenCode',
        action: _ChatCommandAction.status,
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

  Future<void> _openCommandLauncher() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: false,
      constraints: const BoxConstraints(maxWidth: 720),
      builder: (sheetContext) => _CommandLauncherSheet(
        commands: () => _chatCommands,
        loading: () => _serverCommandsLoading,
        error: () => _serverCommandsError,
        onRefresh: _loadServerCommands,
        onSelected: (command) {
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _selectChatCommand(command);
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
        final session = await _conn.createSession();
        if (mounted) {
          await _conn.refreshSessions();
          if (mounted) {
            Navigator.of(context).pushReplacementNamed('/chat/${session.id}');
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
      case _ChatCommandAction.appearance:
        if (mounted) {
          await showAppearancePicker(context, controller: _conn);
        }
        return;
      case _ChatCommandAction.diff:
        _showDiff();
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

  void _showTodos() {
    showModalBottomSheet(
      context: context,
      showDragHandle: true,
      builder: (_) => _TodosSheet(conn: _conn, sessionID: widget.sessionID),
    );
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

    return Scaffold(
      appBar: AppBar(
        title: Text(
          title?.isNotEmpty == true ? title! : 'Chat',
          overflow: TextOverflow.ellipsis,
        ),
        actions: [
          PopupMenuButton<String>(
            tooltip: 'Session views',
            icon: const Icon(Icons.view_agenda_outlined),
            onSelected: (value) {
              if (value == 'timeline') unawaited(_openTimeline());
              if (value == 'changes') _showDiff();
              if (value == 'todos') _showTodos();
              if (value == 'thinking') {
                unawaited(_runMobileCommand(_ChatCommandAction.thinking));
              }
              if (value == 'timestamps') {
                unawaited(_runMobileCommand(_ChatCommandAction.timestamps));
              }
            },
            itemBuilder: (_) => [
              const PopupMenuItem(
                value: 'timeline',
                child: _SessionViewMenuItem(
                  icon: Icons.view_timeline_outlined,
                  label: 'Timeline',
                ),
              ),
              const PopupMenuItem(
                value: 'changes',
                child: _SessionViewMenuItem(
                  icon: Icons.difference_outlined,
                  label: 'Changes',
                ),
              ),
              const PopupMenuItem(
                value: 'todos',
                child: _SessionViewMenuItem(
                  icon: Icons.checklist_rounded,
                  label: 'Todos',
                ),
              ),
              const PopupMenuDivider(),
              CheckedPopupMenuItem(
                key: const ValueKey('session-view-thinking'),
                value: 'thinking',
                checked: _conn.transcriptReasoningExpanded,
                child: Text(
                  _conn.transcriptReasoningExpanded
                      ? 'Collapse reasoning'
                      : 'Expand reasoning',
                ),
              ),
              CheckedPopupMenuItem(
                key: const ValueKey('session-view-timestamps'),
                value: 'timestamps',
                checked: _conn.transcriptTimestampsVisible,
                child: Text(
                  _conn.transcriptTimestampsVisible
                      ? 'Hide timestamps'
                      : 'Show timestamps',
                ),
              ),
            ],
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
          PopupMenuButton<String>(
            onSelected: (v) async {
              if (v == 'shell') await _runShellDialog();
              if (v == 'slash') await _openCommandLauncher();
              if (v == 'share') await _share();
              if (v == 'unshare') await _stopSharing();
              if (v == 'fork') await _fork();
              if (v == 'compact') await _compact();
              if (v == 'retry') await _retryLast();
              if (v == 'revert') await _revertLast();
              if (v == 'restore') await _restore();
              if (v == 'reload') await _load();
            },
            itemBuilder: (_) => [
              const PopupMenuItem(
                value: 'retry',
                child: Text('Retry last prompt'),
              ),
              PopupMenuItem(
                value: session?.reverted == true ? 'restore' : 'revert',
                child: Text(
                  session?.reverted == true
                      ? 'Restore messages'
                      : 'Revert last prompt',
                ),
              ),
              const PopupMenuItem(value: 'fork', child: Text('Fork session')),
              const PopupMenuItem(
                value: 'compact',
                child: Text('Compact context'),
              ),
              PopupMenuItem(
                value: shareUrl == null ? 'share' : 'unshare',
                child: Text(
                  shareUrl == null ? 'Share session' : 'Stop sharing',
                ),
              ),
              const PopupMenuDivider(),
              const PopupMenuItem(
                value: 'shell',
                child: Text('Run shell command'),
              ),
              const PopupMenuItem(value: 'slash', child: Text('Commands')),
              const PopupMenuItem(
                value: 'reload',
                child: Text('Reload messages'),
              ),
            ],
          ),
        ],
      ),
      body: Column(
        children: [
          if (shareUrl != null)
            _SharedSessionBanner(url: shareUrl, onStop: _stopSharing),
          if (_promptError case final promptError?)
            _PromptErrorBanner(
              message: promptError,
              onDismiss: () => setState(() => _promptError = null),
            ),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _error != null
                ? Center(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(
                          '$_error',
                          textAlign: TextAlign.center,
                          style: TextStyle(color: theme.colorScheme.error),
                        ),
                        const SizedBox(height: 8),
                        FilledButton.tonal(
                          onPressed: _load,
                          child: const Text('Retry'),
                        ),
                      ],
                    ),
                  )
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
                                ? Center(
                                    child: Text(
                                      'Ask opencode to do something…',
                                      style: TextStyle(color: theme.hintColor),
                                    ),
                                  )
                                : NotificationListener<ScrollNotification>(
                                    onNotification: (_) => false,
                                    child: Align(
                                      alignment: Alignment.topCenter,
                                      child: ScrollablePositionedList.builder(
                                        reverse: true,
                                        itemScrollController: _messageScroll,
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 12,
                                          vertical: 10,
                                        ),
                                        itemCount:
                                            _messages.length + (busy ? 1 : 0),
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
                                          final parts = displayParts[index];
                                          if (parts.isEmpty &&
                                              meta.isEmpty &&
                                              m.info.errorText == null) {
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
                                            filePreviewLoader:
                                                _loadToolOutputFile,
                                            onAttachFile: _attachToolOutputFile,
                                            onDownloadFile:
                                                _downloadToolOutputFile,
                                          );
                                        },
                                      ),
                                    ),
                                  ),
                          ),
                          _ChatComposer(
                            compact: compactComposer,
                            allowInlineCommands:
                                bodyConstraints.maxHeight >= 300,
                            controller: _composer,
                            focusNode: _focus,
                            commands: _chatCommands,
                            onSelectCommand: _selectChatCommand,
                            onOpenCommands: _openCommandLauncher,
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
                            onRemoveAttachment: (attachment) =>
                                setState(() => _attachments.remove(attachment)),
                          ),
                        ],
                      );
                    },
                  ),
          ),
        ],
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

class _TimelineSelection {
  const _TimelineSelection({required this.message, required this.fork});

  final MessageWithParts message;
  final bool fork;
}

class _SessionViewMenuItem extends StatelessWidget {
  const _SessionViewMenuItem({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) => Row(
    children: [
      Icon(icon, size: 20),
      const SizedBox(width: 12),
      Flexible(
        child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
    ],
  );
}

class _TimelineSheet extends StatefulWidget {
  const _TimelineSheet({required this.messages, required this.forkMode});

  final List<MessageWithParts> messages;
  final bool forkMode;

  @override
  State<_TimelineSheet> createState() => _TimelineSheetState();
}

class _TimelineSheetState extends State<_TimelineSheet> {
  final _search = TextEditingController();

  String _preview(MessageWithParts message) {
    final text = message.parts
        .where((part) => part.type == 'text' && !part.synthetic)
        .map((part) => part.text.trim())
        .where((text) => text.isNotEmpty)
        .join(' ')
        .replaceAll(RegExp(r'\s+'), ' ')
        .trim();
    if (text.isNotEmpty) return text;

    final files = message.parts
        .where((part) => part.type == 'file' && !part.synthetic)
        .map((part) => part.filename?.trim())
        .whereType<String>()
        .where((name) => name.isNotEmpty)
        .toList();
    if (files.isNotEmpty) return files.join(', ');

    final tools = message.parts
        .where((part) => part.type == 'tool')
        .map((part) => part.toolName?.trim())
        .whereType<String>()
        .where((name) => name.isNotEmpty)
        .toList();
    if (tools.isNotEmpty) return 'Tools: ${tools.join(', ')}';

    final reasoning = message.parts
        .where((part) => part.type == 'reasoning')
        .map((part) => part.text.trim())
        .firstWhere((text) => text.isNotEmpty, orElse: () => '');
    return reasoning.isNotEmpty ? reasoning : 'Message';
  }

  bool _isForkable(MessageWithParts message) =>
      message.info.role == 'user' &&
      !message.info.id.startsWith('local-') &&
      message.parts.any((part) => part.type == 'text' && !part.synthetic);

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    final query = _search.text.trim().toLowerCase();
    final visible = widget.messages.reversed.where((message) {
      if (widget.forkMode && !_isForkable(message)) return false;
      if (query.isEmpty) return true;
      final role = message.info.role == 'user'
          ? 'you user'
          : 'opencode assistant';
      return '$role ${_preview(message)}'.toLowerCase().contains(query);
    }).toList();

    return DraggableScrollableSheet(
      expand: false,
      minChildSize: .5,
      initialChildSize: largeText ? .96 : .82,
      maxChildSize: .96,
      snap: true,
      snapSizes: const [.82, .96],
      builder: (context, scrollController) => Material(
        color: theme.colorScheme.surfaceContainerLow,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 10, 8),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          widget.forkMode
                              ? 'Fork from prompt'
                              : 'Message timeline',
                          style: theme.textTheme.titleLarge,
                        ),
                        if (!largeText) ...[
                          const SizedBox(height: 2),
                          Text(
                            widget.forkMode
                                ? 'Choose a prompt to restore it in a new session.'
                                : 'Jump anywhere. Fork restores a prompt for editing.',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Close timeline',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 10),
              child: TextField(
                key: const ValueKey('timeline-search'),
                controller: _search,
                onChanged: (_) => setState(() {}),
                textInputAction: TextInputAction.search,
                decoration: const InputDecoration(
                  hintText: 'Search messages',
                  prefixIcon: Icon(Icons.search_rounded),
                  isDense: true,
                ),
              ),
            ),
            Expanded(
              child: visible.isEmpty
                  ? Center(
                      child: Text(
                        'No matching messages',
                        style: TextStyle(color: theme.hintColor),
                      ),
                    )
                  : ListView.separated(
                      controller: scrollController,
                      padding: const EdgeInsets.fromLTRB(8, 0, 8, 20),
                      itemCount: visible.length,
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final message = visible[index];
                        final isUser = message.info.role == 'user';
                        final created = message.info.time?.created;
                        final footer = [
                          isUser ? 'You' : 'OpenCode',
                          if (created != null) _fmtSessionTime(created),
                        ].join('  ·  ');
                        return ListTile(
                          key: ValueKey('timeline-row-${message.info.id}'),
                          minVerticalPadding: 10,
                          leading: Icon(
                            isUser
                                ? Icons.person_outline_rounded
                                : Icons.auto_awesome_outlined,
                            size: 20,
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                          title: Text(
                            _preview(message),
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                          subtitle: Text(footer),
                          trailing: _isForkable(message)
                              ? widget.forkMode
                                    ? const Icon(Icons.call_split_rounded)
                                    : IconButton(
                                        key: ValueKey(
                                          'timeline-fork-${message.info.id}',
                                        ),
                                        tooltip: 'Fork from this prompt',
                                        onPressed: () => Navigator.pop(
                                          context,
                                          _TimelineSelection(
                                            message: message,
                                            fork: true,
                                          ),
                                        ),
                                        icon: const Icon(
                                          Icons.call_split_rounded,
                                        ),
                                      )
                              : null,
                          onTap: () => Navigator.pop(
                            context,
                            _TimelineSelection(
                              message: message,
                              fork: widget.forkMode,
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

enum _ChatCommandAction {
  newSession,
  sessions,
  workspaces,
  move,
  warp,
  files,
  promptEditor,
  terminal,
  model,
  integrations,
  organization,
  skills,
  references,
  status,
  appearance,
  diff,
  share,
  unshare,
  rename,
  timeline,
  fork,
  compact,
  thinking,
  timestamps,
  undo,
  redo,
  copy,
  export,
  help,
}

class _ChatCommand {
  const _ChatCommand._({
    required this.slash,
    required this.aliases,
    required this.title,
    required this.description,
    required this.group,
    required this.enabled,
    this.action,
    this.serverCommand,
  });

  factory _ChatCommand.mobile({
    required String slash,
    List<String> aliases = const [],
    required String title,
    required String description,
    required String group,
    required _ChatCommandAction action,
    bool enabled = true,
  }) => _ChatCommand._(
    slash: slash,
    aliases: aliases,
    title: title,
    description: description,
    group: group,
    enabled: enabled,
    action: action,
  );

  factory _ChatCommand.server(CommandInfo command) => _ChatCommand._(
    slash: command.name,
    aliases: const [],
    title: command.name,
    description:
        command.description ?? command.agent ?? 'OpenCode server command',
    group: 'Server commands',
    enabled: true,
    serverCommand: command,
  );

  final String slash;
  final List<String> aliases;
  final String title;
  final String description;
  final String group;
  final bool enabled;
  final _ChatCommandAction? action;
  final CommandInfo? serverCommand;

  bool matches(String name) =>
      slash.toLowerCase() == name ||
      aliases.any((alias) => alias.toLowerCase() == name);

  bool matchesQuery(String query) {
    final normalized = query.trim().toLowerCase().replaceFirst('/', '');
    if (normalized.isEmpty) return true;
    return slash.toLowerCase().contains(normalized) ||
        aliases.any((alias) => alias.toLowerCase().contains(normalized)) ||
        title.toLowerCase().contains(normalized) ||
        description.toLowerCase().contains(normalized);
  }

  int scoreFor(String query) {
    final normalized = query.trim().toLowerCase().replaceFirst('/', '');
    if (normalized.isEmpty) return 0;
    final command = slash.toLowerCase();
    final normalizedAliases = aliases.map((alias) => alias.toLowerCase());
    if (command == normalized) return 0;
    if (command.startsWith(normalized)) return 1;
    if (normalizedAliases.any((alias) => alias == normalized)) return 2;
    if (normalizedAliases.any((alias) => alias.startsWith(normalized))) {
      return 3;
    }
    if (title.toLowerCase().startsWith(normalized)) return 4;
    if (command.contains(normalized)) return 5;
    if (title.toLowerCase().contains(normalized)) return 6;
    return 7;
  }
}

class _CommandLauncherSheet extends StatefulWidget {
  const _CommandLauncherSheet({
    required this.commands,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onSelected,
  });

  final List<_ChatCommand> Function() commands;
  final bool Function() loading;
  final Object? Function() error;
  final Future<void> Function() onRefresh;
  final ValueChanged<_ChatCommand> onSelected;

  @override
  State<_CommandLauncherSheet> createState() => _CommandLauncherSheetState();
}

class _CommandLauncherSheetState extends State<_CommandLauncherSheet> {
  final _search = TextEditingController();

  @override
  void initState() {
    super.initState();
    if (widget.loading()) _refresh();
  }

  Future<void> _refresh() async {
    await widget.onRefresh();
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final visible = widget
        .commands()
        .where((command) => command.matchesQuery(_search.text))
        .toList();
    if (_search.text.trim().isNotEmpty) {
      visible.sort((a, b) {
        final score = a
            .scoreFor(_search.text)
            .compareTo(b.scoreFor(_search.text));
        return score != 0 ? score : a.slash.compareTo(b.slash);
      });
    }
    final groups = <String, List<_ChatCommand>>{};
    for (final command in visible) {
      groups.putIfAbsent(command.group, () => []).add(command);
    }
    return DraggableScrollableSheet(
      expand: false,
      minChildSize: .58,
      initialChildSize: .86,
      maxChildSize: .96,
      snap: true,
      snapSizes: const [.86, .96],
      builder: (context, scrollController) => Material(
        color: theme.colorScheme.surfaceContainerLow,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 14, 10, 10),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'OpenCode commands',
                          style: theme.textTheme.titleLarge,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Mobile actions and commands from this server',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Close commands',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              child: TextField(
                key: const Key('command-launcher-search'),
                controller: _search,
                autofocus: true,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  hintText: 'Type a command or action',
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: _search.text.isEmpty
                      ? null
                      : IconButton(
                          tooltip: 'Clear search',
                          onPressed: () {
                            _search.clear();
                            setState(() {});
                          },
                          icon: const Icon(Icons.close_rounded),
                        ),
                  border: const OutlineInputBorder(),
                  isDense: true,
                ),
              ),
            ),
            const Divider(height: 1),
            if (widget.loading()) const LinearProgressIndicator(minHeight: 2),
            if (widget.error() != null)
              ListTile(
                dense: true,
                title: const Text('Server commands could not be refreshed'),
                subtitle: Text('${widget.error()}'),
                trailing: IconButton(
                  tooltip: 'Retry server commands',
                  onPressed: _refresh,
                  icon: const Icon(Icons.refresh_rounded),
                ),
              ),
            Expanded(
              child: visible.isEmpty
                  ? Center(
                      child: Text(
                        'No matching commands',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    )
                  : ListView(
                      key: const Key('command-launcher-list'),
                      controller: scrollController,
                      padding: const EdgeInsets.only(bottom: 24),
                      children: [
                        for (final group in groups.entries) ...[
                          Padding(
                            padding: const EdgeInsets.fromLTRB(16, 18, 16, 6),
                            child: Text(
                              group.key,
                              style: theme.textTheme.labelMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                          for (final command in group.value)
                            _CommandRow(
                              command: command,
                              onSelected: widget.onSelected,
                            ),
                        ],
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }
}

class _CommandRow extends StatelessWidget {
  const _CommandRow({required this.command, required this.onSelected});

  final _ChatCommand command;
  final ValueChanged<_ChatCommand> onSelected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return ListTile(
      key: Key(
        'command-${command.serverCommand == null ? 'mobile' : 'server'}-${command.slash}',
      ),
      enabled: command.enabled,
      dense: true,
      minTileHeight: 58,
      title: Row(
        children: [
          Text(
            '/${command.slash}',
            style: theme.textTheme.bodyMedium?.copyWith(
              fontFamily: 'monospace',
              fontWeight: FontWeight.w600,
            ),
          ),
          if (command.aliases.isNotEmpty) ...[
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                command.aliases.map((alias) => '/$alias').join('  '),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.labelSmall?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontFamily: 'monospace',
                ),
              ),
            ),
          ] else
            const Spacer(),
          Text(
            command.serverCommand == null ? 'mobile' : 'server',
            style: theme.textTheme.labelSmall?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
      subtitle: Text(
        command.description,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      onTap: () => onSelected(command),
    );
  }
}

class _InlineCommandSuggestions extends StatelessWidget {
  const _InlineCommandSuggestions({
    required this.commands,
    required this.query,
    required this.compact,
    required this.onSelected,
    required this.onShowAll,
  });

  final List<_ChatCommand> commands;
  final String query;
  final bool compact;
  final ValueChanged<_ChatCommand> onSelected;
  final VoidCallback onShowAll;

  @override
  Widget build(BuildContext context) {
    final matches = commands
        .where((command) => command.enabled && command.matchesQuery(query))
        .toList();
    matches.sort((a, b) {
      final score = a.scoreFor(query).compareTo(b.scoreFor(query));
      return score != 0 ? score : a.slash.compareTo(b.slash);
    });
    final limit = compact ? 1 : 5;
    final visible = matches.take(limit).toList();
    if (visible.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Column(
      key: const Key('inline-command-suggestions'),
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final command in visible)
          InkWell(
            key: Key('inline-command-${command.slash}'),
            onTap: () => onSelected(command),
            child: Padding(
              padding: EdgeInsets.fromLTRB(
                14,
                compact ? 6 : 8,
                12,
                compact ? 6 : 8,
              ),
              child: Row(
                children: [
                  SizedBox(
                    width: compact ? 92 : 112,
                    child: Text(
                      '/${command.slash}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontFamily: 'monospace',
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      compact ? command.title : command.description,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.labelSmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        if (!compact && matches.length > limit)
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: onShowAll,
              child: const Text('Show all commands'),
            ),
          ),
        Divider(
          height: 1,
          color: theme.colorScheme.outlineVariant.withValues(alpha: .55),
        ),
      ],
    );
  }
}

typedef _AttachmentChooser =
    Future<PromptAttachment?> Function(List<PromptAttachment> current);

class _PromptEditorResult {
  const _PromptEditorResult({required this.value, required this.attachments});

  final TextEditingValue value;
  final List<PromptAttachment> attachments;
}

class _PromptEditorScreen extends StatefulWidget {
  const _PromptEditorScreen({
    required this.initialValue,
    required this.initialAttachments,
    required this.chooseAttachment,
  });

  final TextEditingValue initialValue;
  final List<PromptAttachment> initialAttachments;
  final _AttachmentChooser chooseAttachment;

  @override
  State<_PromptEditorScreen> createState() => _PromptEditorScreenState();
}

class _PromptEditorScreenState extends State<_PromptEditorScreen> {
  late final TextEditingController _controller;
  late final List<PromptAttachment> _attachments;
  bool _closing = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController.fromValue(widget.initialValue);
    _attachments = List<PromptAttachment>.from(widget.initialAttachments);
  }

  bool get _dirty =>
      _controller.text != widget.initialValue.text ||
      !_sameAttachments(_attachments, widget.initialAttachments);

  bool _sameAttachments(
    List<PromptAttachment> left,
    List<PromptAttachment> right,
  ) {
    if (left.length != right.length) return false;
    for (var index = 0; index < left.length; index += 1) {
      final a = left[index];
      final b = right[index];
      if (a.mime != b.mime || a.filename != b.filename || a.url != b.url) {
        return false;
      }
    }
    return true;
  }

  Future<void> _addAttachment() async {
    try {
      final attachment = await widget.chooseAttachment(_attachments);
      if (!mounted || attachment == null) return;
      setState(() => _attachments.add(attachment));
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  void _save() {
    Navigator.pop(
      context,
      _PromptEditorResult(
        value: _controller.value,
        attachments: List.unmodifiable(_attachments),
      ),
    );
  }

  Future<void> _cancel() async {
    if (_closing) return;
    if (!_dirty) {
      Navigator.pop(context);
      return;
    }
    _closing = true;
    final discard = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Discard prompt changes?'),
        content: const Text(
          'Your original composer draft and attachments will stay unchanged.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Keep editing'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Discard'),
          ),
        ],
      ),
    );
    _closing = false;
    if (discard == true && mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) unawaited(_cancel());
      },
      child: Scaffold(
        key: const Key('prompt-editor-screen'),
        appBar: AppBar(
          leading: IconButton(
            tooltip: 'Close prompt editor',
            onPressed: _cancel,
            icon: const Icon(Icons.close_rounded),
          ),
          title: const Text('Prompt editor'),
          actions: [
            IconButton(
              key: const Key('prompt-editor-attach'),
              tooltip: _attachments.length >= _maxAttachmentCount
                  ? 'Attachment limit reached'
                  : 'Attach file',
              onPressed: _attachments.length >= _maxAttachmentCount
                  ? null
                  : _addAttachment,
              icon: const Icon(Icons.attach_file_rounded),
            ),
            TextButton(
              key: const Key('prompt-editor-done'),
              onPressed: _save,
              child: const Text('Done'),
            ),
            const SizedBox(width: 4),
          ],
        ),
        body: SafeArea(
          top: false,
          child: Column(
            children: [
              if (_attachments.isNotEmpty)
                SizedBox(
                  height: 64,
                  child: ListView.separated(
                    key: const Key('prompt-editor-attachments'),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 12,
                      vertical: 8,
                    ),
                    scrollDirection: Axis.horizontal,
                    itemCount: _attachments.length,
                    separatorBuilder: (_, _) => const SizedBox(width: 6),
                    itemBuilder: (context, index) {
                      final attachment = _attachments[index];
                      return _PendingAttachmentChip(
                        attachment: attachment,
                        onRemove: () =>
                            setState(() => _attachments.removeAt(index)),
                      );
                    },
                  ),
                ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                  child: TextField(
                    key: const Key('prompt-editor-field'),
                    controller: _controller,
                    autofocus: true,
                    expands: true,
                    minLines: null,
                    maxLines: null,
                    keyboardType: TextInputType.multiline,
                    textAlignVertical: TextAlignVertical.top,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: const InputDecoration(
                      hintText: 'Write your OpenCode prompt…',
                      alignLabelWithHint: true,
                      contentPadding: EdgeInsets.all(16),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

class _ChatComposer extends StatelessWidget {
  const _ChatComposer({
    required this.compact,
    required this.allowInlineCommands,
    required this.controller,
    required this.focusNode,
    required this.commands,
    required this.onSelectCommand,
    required this.onOpenCommands,
    required this.onOpenEditor,
    required this.attachments,
    required this.busy,
    required this.sending,
    required this.voiceOpening,
    required this.selectedAgent,
    required this.selectedModel,
    required this.selectedVariant,
    required this.onAttach,
    required this.onVoice,
    required this.onSend,
    required this.onStop,
    required this.onChooseModel,
    required this.onRemoveAttachment,
  });

  final bool compact;
  final bool allowInlineCommands;
  final TextEditingController controller;
  final FocusNode focusNode;
  final List<_ChatCommand> commands;
  final ValueChanged<_ChatCommand> onSelectCommand;
  final VoidCallback onOpenCommands;
  final VoidCallback onOpenEditor;
  final List<PromptAttachment> attachments;
  final bool busy;
  final bool sending;
  final bool voiceOpening;
  final String selectedAgent;
  final ModelRef? selectedModel;
  final String selectedVariant;
  final VoidCallback onAttach;
  final VoidCallback onVoice;
  final VoidCallback onSend;
  final VoidCallback onStop;
  final VoidCallback onChooseModel;
  final ValueChanged<PromptAttachment> onRemoveAttachment;

  bool get _hasPrompt =>
      controller.text.trim().isNotEmpty || attachments.isNotEmpty;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    final disableAnimations = media.disableAnimations;

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(10, 6, 10, 10),
        child: ListenableBuilder(
          listenable: Listenable.merge([focusNode, controller]),
          builder: (context, _) => AnimatedContainer(
            key: const Key('chat-composer-surface'),
            duration: disableAnimations
                ? Duration.zero
                : const Duration(milliseconds: 180),
            curve: Curves.easeOutCubic,
            decoration: BoxDecoration(
              color: scheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(compact ? 20 : 24),
              border: Border.all(
                color: focusNode.hasFocus
                    ? scheme.primary.withValues(alpha: .8)
                    : scheme.outlineVariant.withValues(alpha: .85),
                width: focusNode.hasFocus ? 1.4 : 1,
              ),
              boxShadow: [
                BoxShadow(
                  color: scheme.surfaceContainerLowest.withValues(alpha: .5),
                  blurRadius: 20,
                  offset: const Offset(0, 10),
                ),
              ],
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (allowInlineCommands && _slashQuery != null)
                  _InlineCommandSuggestions(
                    commands: commands,
                    query: _slashQuery!,
                    compact: compact,
                    onSelected: onSelectCommand,
                    onShowAll: onOpenCommands,
                  ),
                if (attachments.isNotEmpty)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(12, 10, 12, 0),
                    child: SizedBox(
                      height: 56,
                      child: ListView.separated(
                        scrollDirection: Axis.horizontal,
                        itemCount: attachments.length,
                        separatorBuilder: (_, _) => const SizedBox(width: 6),
                        itemBuilder: (context, index) {
                          final attachment = attachments[index];
                          return _PendingAttachmentChip(
                            attachment: attachment,
                            onRemove: () => onRemoveAttachment(attachment),
                          );
                        },
                      ),
                    ),
                  ),
                if (compact)
                  _compactComposer(context)
                else
                  _standardComposer(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _standardComposer(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _ComposerField(
          controller: controller,
          focusNode: focusNode,
          onOpenEditor: onOpenEditor,
          maxLines: 6,
          contentPadding: const EdgeInsets.fromLTRB(16, 14, 16, 10),
        ),
        Divider(
          height: 1,
          indent: 14,
          endIndent: 14,
          color: scheme.outlineVariant.withValues(alpha: .55),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(8, 7, 8, 8),
          child: Row(
            children: [
              _ComposerAction(
                key: const Key('command-launcher-button'),
                tooltip: 'Commands',
                onPressed: onOpenCommands,
                icon: const Icon(Icons.electric_bolt_outlined),
              ),
              const SizedBox(width: 2),
              _ComposerAction(
                tooltip: 'Attach file',
                onPressed: busy || sending ? null : onAttach,
                icon: const Icon(Icons.attach_file_rounded),
              ),
              const SizedBox(width: 2),
              _ComposerAction(
                key: const Key('voice-input-button'),
                tooltip: 'Local voice input',
                onPressed: busy || sending ? null : onVoice,
                icon: voiceOpening
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.mic_none_rounded),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(
                    key: const Key('composer-model-context'),
                    onPressed: onChooseModel,
                    icon: const Icon(Icons.tune_rounded, size: 17),
                    label: Text(
                      _contextLabel,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    style: TextButton.styleFrom(
                      foregroundColor: scheme.onSurfaceVariant,
                      padding: const EdgeInsets.symmetric(horizontal: 10),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 6),
              _ComposerSubmit(
                busy: busy,
                sending: sending,
                enabled: _hasPrompt,
                onSend: onSend,
                onStop: onStop,
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _compactComposer(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          _ComposerAction(
            key: const Key('command-launcher-button'),
            tooltip: 'Commands',
            onPressed: onOpenCommands,
            icon: const Icon(Icons.electric_bolt_outlined),
          ),
          _ComposerAction(
            key: const Key('composer-model-context'),
            tooltip: _contextLabel,
            onPressed: onChooseModel,
            icon: const Icon(Icons.tune_rounded),
          ),
          _ComposerAction(
            tooltip: 'Attach file',
            onPressed: busy || sending ? null : onAttach,
            icon: const Icon(Icons.attach_file_rounded),
          ),
          _ComposerAction(
            key: const Key('voice-input-button'),
            tooltip: 'Local voice input',
            onPressed: busy || sending ? null : onVoice,
            icon: voiceOpening
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.mic_none_rounded),
          ),
          Expanded(
            child: _ComposerField(
              controller: controller,
              focusNode: focusNode,
              onOpenEditor: onOpenEditor,
              maxLines: 3,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 10,
                vertical: 11,
              ),
            ),
          ),
          const SizedBox(width: 4),
          _ComposerSubmit(
            busy: busy,
            sending: sending,
            enabled: _hasPrompt,
            onSend: onSend,
            onStop: onStop,
          ),
        ],
      ),
    );
  }

  String get _contextLabel {
    final parts = <String>[];
    if (selectedAgent.isNotEmpty) parts.add(selectedAgent);
    final model = selectedModel?.modelID;
    if (model != null && model.isNotEmpty) parts.add(model);
    if (selectedVariant.isNotEmpty) parts.add(selectedVariant);
    return parts.isEmpty ? 'Choose model' : parts.join(' · ');
  }

  String? get _slashQuery {
    final match = RegExp(r'^/(\S*)$').firstMatch(controller.text.trimLeft());
    return match?.group(1);
  }
}

class _ComposerField extends StatelessWidget {
  const _ComposerField({
    required this.controller,
    required this.focusNode,
    required this.onOpenEditor,
    required this.maxLines,
    required this.contentPadding,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
  final VoidCallback onOpenEditor;
  final int maxLines;
  final EdgeInsets contentPadding;

  @override
  Widget build(BuildContext context) => TextField(
    key: const Key('chat-composer-field'),
    controller: controller,
    focusNode: focusNode,
    minLines: 1,
    maxLines: maxLines,
    textCapitalization: TextCapitalization.sentences,
    decoration: InputDecoration(
      hintText: 'Ask OpenCode…',
      suffixIcon: IconButton(
        key: const Key('prompt-editor-button'),
        tooltip: 'Open full-screen prompt editor',
        onPressed: onOpenEditor,
        icon: const Icon(Icons.open_in_full_rounded, size: 19),
      ),
      filled: false,
      border: InputBorder.none,
      enabledBorder: InputBorder.none,
      focusedBorder: InputBorder.none,
      contentPadding: contentPadding,
    ),
  );
}

class _ComposerAction extends StatelessWidget {
  const _ComposerAction({
    super.key,
    required this.tooltip,
    required this.onPressed,
    required this.icon,
  });

  final String tooltip;
  final VoidCallback? onPressed;
  final Widget icon;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      style: IconButton.styleFrom(
        backgroundColor: scheme.surfaceContainerHigh,
        foregroundColor: scheme.onSurfaceVariant,
        disabledBackgroundColor: scheme.surfaceContainerHigh.withValues(
          alpha: .45,
        ),
      ),
      icon: icon,
    );
  }
}

class _ComposerSubmit extends StatelessWidget {
  const _ComposerSubmit({
    required this.busy,
    required this.sending,
    required this.enabled,
    required this.onSend,
    required this.onStop,
  });

  final bool busy;
  final bool sending;
  final bool enabled;
  final VoidCallback onSend;
  final VoidCallback onStop;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    if (busy) {
      return IconButton.filledTonal(
        key: const Key('chat-send-button'),
        tooltip: 'Stop',
        onPressed: onStop,
        style: IconButton.styleFrom(
          foregroundColor: scheme.error,
          backgroundColor: scheme.errorContainer.withValues(alpha: .55),
        ),
        icon: const Icon(Icons.stop_rounded),
      );
    }
    return IconButton.filled(
      key: const Key('chat-send-button'),
      tooltip: 'Send',
      onPressed: sending || !enabled ? null : onSend,
      icon: sending
          ? const SizedBox.square(
              dimension: 18,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : const Icon(Icons.arrow_upward_rounded),
    );
  }
}

class _PendingAttachmentChip extends StatelessWidget {
  const _PendingAttachmentChip({
    required this.attachment,
    required this.onRemove,
  });

  final PromptAttachment attachment;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reference = attachment.isDirectoryReference;
    final removeLabel = reference
        ? 'Remove reference @${attachment.filename}'
        : 'Remove attachment ${attachment.filename}';
    void openPreview() => showFilePreviewSheet(
      context,
      FilePreviewData.fromDataUrl(
        name: attachment.filename,
        mimeType: attachment.mime,
        url: attachment.url,
      ),
    );

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      shape: StadiumBorder(
        side: BorderSide(color: theme.colorScheme.outlineVariant),
      ),
      clipBehavior: Clip.antiAlias,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Semantics(
            button: !reference,
            excludeSemantics: true,
            label: reference
                ? 'Reference @${attachment.filename}'
                : 'Preview attachment ${attachment.filename}',
            onTap: reference ? null : openPreview,
            child: Tooltip(
              message: reference
                  ? 'Project reference @${attachment.filename}'
                  : 'Preview ${attachment.filename}',
              child: InkWell(
                onTap: reference ? null : openPreview,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Padding(
                    padding: const EdgeInsets.only(left: 12),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          reference
                              ? Icons.bookmark_outline_rounded
                              : Icons.attach_file_rounded,
                          size: 16,
                        ),
                        const SizedBox(width: 6),
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 180),
                          child: Text(
                            reference
                                ? '@${attachment.filename}'
                                : attachment.filename,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (!reference) ...[
                          const SizedBox(width: 8),
                          const Icon(Icons.visibility_outlined, size: 16),
                        ],
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          Semantics(
            container: true,
            excludeSemantics: true,
            label: removeLabel,
            button: true,
            onTap: onRemove,
            child: IconButton(
              tooltip: removeLabel,
              constraints: const BoxConstraints.tightFor(width: 48, height: 48),
              onPressed: onRemove,
              icon: const Icon(Icons.close_rounded, size: 18),
            ),
          ),
        ],
      ),
    );
  }
}

class _PermissionDialog extends StatefulWidget {
  final PermissionRequest permission;
  final Future<void> Function(String reply) onReply;

  const _PermissionDialog({required this.permission, required this.onReply});

  @override
  State<_PermissionDialog> createState() => _PermissionDialogState();
}

class _PermissionDialogState extends State<_PermissionDialog> {
  bool _replying = false;
  Object? _error;

  Future<void> _reply(String reply) async {
    if (reply == 'always' && !await _confirmAlways()) return;
    setState(() {
      _replying = true;
      _error = null;
    });
    try {
      await widget.onReply(reply);
      if (mounted) Navigator.of(context).pop();
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _replying = false;
        _error = error;
      });
    }
  }

  Future<bool> _confirmAlways() async {
    final permission = widget.permission;
    final broader = permission.always.isNotEmpty
        ? permission.always
        : permission.patterns;
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            scrollable: true,
            icon: const Icon(Icons.warning_amber_rounded),
            title: const Text('Confirm broader access'),
            content: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Context: ${permission.permission} in this chat'),
                const SizedBox(height: 12),
                const Text('Always allow patterns:'),
                const SizedBox(height: 4),
                SelectableText(
                  broader.isEmpty
                      ? '(all matching requests)'
                      : broader.join('\n'),
                  style: const TextStyle(fontFamily: 'monospace'),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Consequence: future matching actions can run without asking again for the lifetime of this OpenCode server. Allow once is safer.',
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Keep asking'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Confirm always allow'),
              ),
            ],
          ),
        ) ??
        false;
  }

  @override
  Widget build(BuildContext context) {
    final permission = widget.permission;
    final theme = Theme.of(context);
    final requestedPatterns = permission.patterns.isEmpty
        ? 'OpenCode wants to use ${permission.permission}.'
        : permission.patterns.join('\n');
    return AlertDialog(
      scrollable: true,
      icon: const Icon(Icons.admin_panel_settings_outlined),
      title: Text(permissionRequestTitle(permission.permission)),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Requested for this action', style: theme.textTheme.labelLarge),
          const SizedBox(height: 4),
          SelectableText(requestedPatterns),
          if (permission.always.isNotEmpty) ...[
            const SizedBox(height: 16),
            Text(
              'Always allow would also cover',
              style: theme.textTheme.labelLarge,
            ),
            const SizedBox(height: 4),
            SelectableText(permission.always.join('\n')),
          ],
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(
              'Reply failed: $_error',
              style: TextStyle(color: theme.colorScheme.error),
            ),
          ],
        ],
      ),
      actions: [
        TextButton(
          onPressed: _replying ? null : () => _reply('reject'),
          child: const Text('Reject'),
        ),
        OutlinedButton(
          onPressed: _replying ? null : () => _reply('always'),
          child: const Text('Always allow'),
        ),
        FilledButton(
          onPressed: _replying ? null : () => _reply('once'),
          child: _replying
              ? const SizedBox.square(
                  dimension: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Allow once'),
        ),
      ],
    );
  }
}

class _TypingIndicator extends StatefulWidget {
  @override
  State<_TypingIndicator> createState() => __TypingIndicatorState();
}

class _PromptErrorBanner extends StatelessWidget {
  final String message;
  final VoidCallback onDismiss;

  const _PromptErrorBanner({required this.message, required this.onDismiss});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Semantics(
      container: true,
      liveRegion: true,
      child: Container(
        key: const ValueKey('prompt-error-banner'),
        margin: const EdgeInsets.fromLTRB(12, 8, 12, 0),
        padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
        decoration: BoxDecoration(
          color: scheme.errorContainer,
          borderRadius: BorderRadius.circular(14),
          border: Border.all(color: scheme.error.withValues(alpha: .35)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(
              Icons.error_outline_rounded,
              size: 20,
              color: scheme.onErrorContainer,
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                message,
                style: TextStyle(color: scheme.onErrorContainer, height: 1.35),
              ),
            ),
            IconButton(
              tooltip: 'Dismiss prompt error',
              visualDensity: VisualDensity.compact,
              onPressed: onDismiss,
              icon: Icon(
                Icons.close_rounded,
                size: 19,
                color: scheme.onErrorContainer,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class __TypingIndicatorState extends State<_TypingIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(seconds: 1),
  );
  bool _animating = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final animate = !MediaQuery.disableAnimationsOf(context);
    if (animate == _animating) return;
    _animating = animate;
    if (animate) {
      _c.repeat(reverse: true);
    } else {
      _c.stop();
      _c.value = 1;
    }
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween(
        begin: .3,
        end: 1.0,
      ).animate(CurvedAnimation(parent: _c, curve: Curves.easeInOut)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Icon(
              Icons.smart_toy_outlined,
              size: 16,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(width: 8),
            Text(
              'thinking…',
              style: Theme.of(context).textTheme.bodySmall!.copyWith(
                color: Theme.of(context).hintColor,
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }
}

const _contextToolNames = {'read', 'list', 'glob', 'grep'};

bool _isToolPart(Part part) => part.type == 'tool';

List<List<Part>> _timelineDisplayParts(List<MessageWithParts> messages) {
  final display = List.generate(messages.length, (_) => <Part>[]);
  final pendingParts = <Part>[];
  String? pendingType;
  int? pendingOwner;

  void flushPending() {
    if (pendingOwner case final owner?) {
      if (pendingType == 'text' || pendingType == 'reasoning') {
        display[owner].add(_mergeTextParts(pendingParts));
      } else {
        display[owner].addAll(pendingParts);
      }
    }
    pendingParts.clear();
    pendingType = null;
    pendingOwner = null;
  }

  void appendPart(int owner, Part part) {
    final mergeable =
        part.type == 'tool' || part.type == 'text' || part.type == 'reasoning';
    if (!mergeable) {
      flushPending();
      display[owner].add(part);
      return;
    }
    if (pendingType != null && pendingType != part.type) flushPending();
    pendingType ??= part.type;
    pendingOwner ??= owner;
    pendingParts.add(part);
  }

  for (var index = 0; index < messages.length; index += 1) {
    final message = messages[index];
    final parts = message.parts.where((part) => part.isRenderable);
    if (message.info.role != 'assistant') {
      flushPending();
      display[index].addAll(parts);
      continue;
    }

    if (message.info.errorText != null && parts.isEmpty) flushPending();
    for (final part in parts) {
      appendPart(index, part);
    }
    if (message.info.errorText != null) flushPending();

    final nextIsAssistant =
        index + 1 < messages.length &&
        messages[index + 1].info.role == 'assistant';
    if (!nextIsAssistant) flushPending();
  }
  flushPending();
  return display;
}

Part _mergeTextParts(List<Part> parts) {
  assert(parts.isNotEmpty);
  if (parts.length == 1) return parts.single;
  final first = parts.first;
  final buffer = StringBuffer();
  for (final part in parts) {
    if (part.text.trim().isEmpty) continue;
    if (buffer.isNotEmpty &&
        !buffer.toString().endsWith('\n') &&
        !part.text.startsWith('\n')) {
      buffer.write('\n\n');
    }
    buffer.write(part.text);
  }
  return Part(
    id: first.id,
    messageID: first.messageID,
    type: first.type,
    text: buffer.toString(),
  );
}

class _AssistantPartRun {
  const _AssistantPartRun(this.parts, {this.grouped = false});

  final List<Part> parts;
  final bool grouped;
}

List<_AssistantPartRun> _groupAssistantParts(List<Part> parts) {
  final runs = <_AssistantPartRun>[];
  var index = 0;
  while (index < parts.length) {
    final current = parts[index];
    if (!_isToolPart(current)) {
      if (current.type != 'text' && current.type != 'reasoning') {
        runs.add(_AssistantPartRun([current]));
        index += 1;
        continue;
      }
      final textParts = <Part>[current];
      var next = index + 1;
      while (next < parts.length && parts[next].type == current.type) {
        textParts.add(parts[next]);
        next += 1;
      }
      runs.add(_AssistantPartRun([_mergeTextParts(textParts)]));
      index = next;
      continue;
    }

    final toolParts = <Part>[current];
    var next = index + 1;
    while (next < parts.length && _isToolPart(parts[next])) {
      toolParts.add(parts[next]);
      next += 1;
    }
    if (toolParts.length == 1) {
      runs.add(_AssistantPartRun(toolParts));
    } else {
      runs.add(_AssistantPartRun(toolParts, grouped: true));
    }
    index = next;
  }
  return runs;
}

String _contextToolSummary(List<Part> parts) {
  final counts = <String, int>{};
  for (final part in parts) {
    final name = part.toolName!.trim().toLowerCase();
    counts[name] = (counts[name] ?? 0) + 1;
  }
  String countLabel(String name, String singular, String plural) {
    final count = counts[name] ?? 0;
    return count == 0 ? '' : '$count ${count == 1 ? singular : plural}';
  }

  final searchCount = (counts['glob'] ?? 0) + (counts['grep'] ?? 0);
  return [
    countLabel('read', 'read', 'reads'),
    if (searchCount > 0)
      '$searchCount ${searchCount == 1 ? 'search' : 'searches'}',
    countLabel('list', 'list', 'lists'),
  ].where((label) => label.isNotEmpty).join(' · ');
}

String _toolRunSummary(List<Part> parts) {
  final allContext = parts.every(
    (part) => _contextToolNames.contains(part.toolName?.trim().toLowerCase()),
  );
  if (allContext) return _contextToolSummary(parts);

  final labels = <String>[];
  for (final part in parts) {
    final name = part.toolName?.trim().toLowerCase() ?? 'tool';
    final label = switch (name) {
      'bash' || 'shell' => 'shell',
      'read' => 'read',
      'list' => 'list',
      'glob' || 'grep' => 'search',
      'edit' => 'edit',
      'write' => 'write',
      'patch' || 'apply_patch' => 'patch',
      'task' => 'agent',
      'todowrite' || 'todo' => 'tasks',
      'webfetch' || 'websearch' => 'web',
      _ => name,
    };
    if (!labels.contains(label)) labels.add(label);
  }
  final kinds = labels.take(3).join(' · ');
  return '${parts.length} calls${kinds.isEmpty ? '' : ' · $kinds'}';
}

class _ToolCallGroup extends StatefulWidget {
  const _ToolCallGroup({
    super.key,
    required this.parts,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  final List<Part> parts;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  State<_ToolCallGroup> createState() => _ToolCallGroupState();
}

class _ToolCallGroupState extends State<_ToolCallGroup> {
  late bool _expanded;

  @override
  void initState() {
    super.initState();
    _expanded = _shouldOpen(widget.parts);
  }

  @override
  void didUpdateWidget(covariant _ToolCallGroup oldWidget) {
    super.didUpdateWidget(oldWidget);
    if ((!_expanded &&
            widget.parts.length > oldWidget.parts.length &&
            _running) ||
        _shouldOpen(widget.parts)) {
      _expanded = true;
    }
  }

  bool _shouldOpen(List<Part> parts) => parts.any(
    (part) =>
        part.toolState.status == 'pending' ||
        part.toolState.status == 'running' ||
        part.toolState.status == 'error' ||
        part.toolState.outputFiles.isNotEmpty,
  );

  bool get _running => widget.parts.any(
    (part) =>
        part.toolState.status == 'pending' ||
        part.toolState.status == 'running',
  );

  bool get _failed =>
      widget.parts.any((part) => part.toolState.status == 'error');

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reduceMotion = MediaQuery.disableAnimationsOf(context);
    final summary = _toolRunSummary(widget.parts);
    final allContext = widget.parts.every(
      (part) => _contextToolNames.contains(part.toolName?.trim().toLowerCase()),
    );
    final title = allContext
        ? (_running ? 'Exploring' : 'Explored')
        : (_running ? 'Running tools' : 'Tools');
    return Container(
      key: const Key('tool-call-group'),
      margin: const EdgeInsets.symmetric(vertical: 3),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .28),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: theme.dividerColor.withValues(alpha: .35)),
      ),
      child: Column(
        children: [
          Semantics(
            button: true,
            expanded: _expanded,
            label: '$title, ${widget.parts.length} tools',
            child: InkWell(
              key: const Key('tool-call-group-header'),
              onTap: () => setState(() => _expanded = !_expanded),
              borderRadius: BorderRadius.circular(8),
              child: ConstrainedBox(
                constraints: const BoxConstraints(minHeight: 48),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 7, 8, 7),
                  child: Row(
                    children: [
                      Icon(
                        Icons.search_rounded,
                        size: 16,
                        color: theme.hintColor,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        title,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(width: 7),
                      Expanded(
                        child: Text(
                          summary,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ),
                      const SizedBox(width: 6),
                      if (_running && !reduceMotion)
                        SizedBox.square(
                          dimension: 12,
                          child: CircularProgressIndicator(
                            strokeWidth: 1.6,
                            color: theme.colorScheme.primary,
                          ),
                        )
                      else
                        Icon(
                          _failed
                              ? Icons.error_outline_rounded
                              : _running
                              ? Icons.hourglass_top_rounded
                              : Icons.check_circle_outline_rounded,
                          size: 14,
                          color: _failed
                              ? theme.colorScheme.error
                              : _running
                              ? theme.colorScheme.primary
                              : Colors.green.shade400,
                        ),
                      const SizedBox(width: 4),
                      AnimatedRotation(
                        turns: _expanded ? .5 : 0,
                        duration: reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 150),
                        child: Icon(
                          Icons.expand_more_rounded,
                          size: 16,
                          color: theme.hintColor,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
          if (_expanded)
            Column(
              children: [
                Divider(
                  height: 1,
                  color: theme.dividerColor.withValues(alpha: .35),
                ),
                for (var index = 0; index < widget.parts.length; index++) ...[
                  if (index > 0)
                    Divider(
                      height: 1,
                      indent: 34,
                      color: theme.dividerColor.withValues(alpha: .28),
                    ),
                  ToolCard(
                    key: ValueKey(
                      widget.parts[index].id ?? widget.parts[index].callID,
                    ),
                    toolName: widget.parts[index].toolName!,
                    state: widget.parts[index].toolState,
                    embedded: true,
                    filePreviewLoader: widget.filePreviewLoader,
                    onAttachFile: widget.onAttachFile,
                    onDownloadFile: widget.onDownloadFile,
                  ),
                ],
              ],
            ),
        ],
      ),
    );
  }
}

class _AssistantMessagePart extends StatelessWidget {
  const _AssistantMessagePart({
    required this.part,
    required this.reasoningExpanded,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  final Part part;
  final bool reasoningExpanded;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  Widget build(BuildContext context) {
    if (part.type == 'text') {
      return Padding(
        key: const Key('assistant-text-block'),
        padding: const EdgeInsets.only(bottom: 4),
        child: MarkdownText(part.text),
      );
    }
    if (part.type == 'reasoning') {
      return _Reasoning(text: part.text, expanded: reasoningExpanded);
    }
    if (part.type == 'tool') {
      return ToolCard(
        key: ValueKey(part.id ?? part.callID),
        toolName: part.toolName ?? 'tool',
        state: part.toolState,
        filePreviewLoader: filePreviewLoader,
        onAttachFile: onAttachFile,
        onDownloadFile: onDownloadFile,
      );
    }
    if (part.type == 'file') {
      return Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Chip(
          avatar: const Icon(Icons.attach_file_rounded, size: 16),
          label: Text(part.filename ?? 'Attachment'),
        ),
      );
    }
    return const SizedBox.shrink();
  }
}

class _MessageView extends StatelessWidget {
  final MessageWithParts m;
  final _MessageMeta meta;
  final List<Part> parts;
  final bool reasoningExpanded;
  final bool showTimestamp;
  final bool highlighted;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;
  const _MessageView({
    super.key,
    required this.m,
    required this.meta,
    required this.parts,
    required this.reasoningExpanded,
    required this.showTimestamp,
    this.highlighted = false,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isUser = m.info.role == 'user';
    final visibleParts = parts.where((p) => p.isRenderable).toList();
    final assistantRuns = isUser
        ? const <_AssistantPartRun>[]
        : _groupAssistantParts(visibleParts);
    final createdAt = m.info.time?.created;

    final metaParts = <String>[
      if (showTimestamp && createdAt != null) _fmtSessionTime(createdAt),
      ?meta.modelLabel,
      if (meta.turnTokens case final tokens?) '${_fmtTokens(tokens)} tok',
      if (meta.turnCost case final cost?) '\$${cost.toStringAsFixed(4)}',
    ];

    return AnimatedContainer(
      key: ValueKey('message-highlight-${m.info.id}-$highlighted'),
      duration: const Duration(milliseconds: 180),
      padding: const EdgeInsets.fromLTRB(6, 4, 6, 10),
      decoration: BoxDecoration(
        color: highlighted
            ? theme.colorScheme.primaryContainer.withValues(alpha: .24)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: isUser
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width * .88,
            ),
            padding: isUser
                ? const EdgeInsets.symmetric(horizontal: 14, vertical: 10)
                : const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            decoration: isUser
                ? BoxDecoration(
                    color: theme.colorScheme.primaryContainer.withValues(
                      alpha: .55,
                    ),
                    borderRadius: BorderRadius.circular(16),
                  )
                : null,
            child: isUser
                ? _UserMessageContent(parts: visibleParts)
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (final run in assistantRuns)
                        if (run.grouped)
                          _ToolCallGroup(
                            key: ValueKey(
                              'tools:${run.parts.first.id ?? run.parts.first.callID}',
                            ),
                            parts: run.parts,
                            filePreviewLoader: filePreviewLoader,
                            onAttachFile: onAttachFile,
                            onDownloadFile: onDownloadFile,
                          )
                        else
                          _AssistantMessagePart(
                            part: run.parts.single,
                            reasoningExpanded: reasoningExpanded,
                            filePreviewLoader: filePreviewLoader,
                            onAttachFile: onAttachFile,
                            onDownloadFile: onDownloadFile,
                          ),
                    ],
                  ),
          ),
          if (metaParts.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 3, left: 6, right: 6),
              child: Text(
                key: ValueKey('message-meta-${m.info.id}'),
                metaParts.join('  ·  '),
                style: theme.textTheme.labelSmall!.copyWith(
                  color: theme.hintColor,
                  fontSize: 10,
                ),
              ),
            ),
          if (m.info.errorText != null)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Text(
                m.info.errorText!,
                style: theme.textTheme.bodySmall!.copyWith(
                  color: theme.colorScheme.error,
                ),
              ),
            ),
        ],
      ),
    );
  }

  static String _fmtTokens(int n) {
    if (n >= 1000000) return '${(n / 1000000).toStringAsFixed(1)}M';
    if (n >= 1000) return '${(n / 1000).toStringAsFixed(1)}k';
    return '$n';
  }
}

class _MessageMeta {
  const _MessageMeta({this.modelLabel, this.turnTokens, this.turnCost});

  final String? modelLabel;
  final int? turnTokens;
  final double? turnCost;

  bool get isEmpty =>
      modelLabel == null && turnTokens == null && turnCost == null;
}

String? _modelLabel(MessageInfo info) {
  final provider = info.providerID?.trim();
  final model = info.modelID?.trim();
  if (model?.isNotEmpty != true) return null;
  return provider?.isNotEmpty == true
      ? presentedModelLabel(provider!, model!)
      : model;
}

_MessageMeta _messageMeta(List<MessageWithParts> messages, int index) {
  final current = messages[index];
  if (current.info.role != 'assistant') return const _MessageMeta();

  final currentModel = _modelLabel(current.info);
  String? previousModel;
  for (var previous = index - 1; previous >= 0; previous -= 1) {
    final info = messages[previous].info;
    if (info.role != 'assistant') continue;
    previousModel = _modelLabel(info);
    break;
  }
  final modelChanged = currentModel != null && currentModel != previousModel;

  final endsAssistantRun =
      index == messages.length - 1 ||
      messages[index + 1].info.role != 'assistant';
  if (!endsAssistantRun) {
    return _MessageMeta(modelLabel: modelChanged ? currentModel : null);
  }

  var turnTokens = 0;
  var turnCost = 0.0;
  for (var runIndex = index; runIndex >= 0; runIndex -= 1) {
    final info = messages[runIndex].info;
    if (info.role != 'assistant') break;
    turnTokens += info.tokens.total;
    turnCost += info.cost;
  }
  return _MessageMeta(
    modelLabel: modelChanged ? currentModel : null,
    turnTokens: turnTokens > 0 ? turnTokens : null,
    turnCost: turnCost > 0 ? turnCost : null,
  );
}

class _UserMessageContent extends StatelessWidget {
  final List<Part> parts;

  const _UserMessageContent({required this.parts});

  @override
  Widget build(BuildContext context) {
    final text = parts
        .where((part) => part.type == 'text')
        .map((part) => part.text)
        .where((value) => value.trim().isNotEmpty)
        .join('\n');
    final files = parts.where((part) => part.type == 'file').toList();
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (text.isNotEmpty) MarkdownText(text),
        if (text.isNotEmpty && files.isNotEmpty) const SizedBox(height: 8),
        for (final file in files) _AttachmentPart(part: file),
      ],
    );
  }
}

class _AttachmentPart extends StatelessWidget {
  final Part part;

  const _AttachmentPart({required this.part});

  String get _filename => part.filename?.trim().isNotEmpty == true
      ? part.filename!.trim()
      : 'Attachment';

  String get _type {
    final dot = _filename.lastIndexOf('.');
    if (dot < 0 || dot == _filename.length - 1) return 'FILE';
    return _filename.substring(dot + 1).toUpperCase();
  }

  bool get _isReference =>
      part.mime == PromptAttachment.directoryReferenceMime &&
      Uri.tryParse(part.url ?? '')?.scheme == 'file';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final reference = _isReference;
    void openPreview() => showFilePreviewSheet(
      context,
      FilePreviewData.fromDataUrl(
        name: _filename,
        mimeType: part.mime,
        url: part.url,
      ),
    );

    return Semantics(
      button: !reference,
      excludeSemantics: true,
      label: reference
          ? 'Reference @$_filename'
          : 'Preview attachment $_filename',
      onTap: reference ? null : openPreview,
      child: Tooltip(
        message: reference
            ? 'Project reference @$_filename'
            : 'Preview attachment',
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: reference ? null : openPreview,
          child: Container(
            margin: const EdgeInsets.only(bottom: 4),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 7),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface.withValues(alpha: .5),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(
                color: theme.colorScheme.outlineVariant.withValues(alpha: .7),
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  reference
                      ? Icons.bookmark_outline_rounded
                      : Icons.attach_file_rounded,
                  size: 17,
                ),
                const SizedBox(width: 7),
                Flexible(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        reference ? '@$_filename' : _filename,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        reference
                            ? 'Project reference'
                            : '$_type · prompt attachment',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                if (!reference) ...[
                  const SizedBox(width: 8),
                  const Icon(Icons.visibility_outlined, size: 15),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _Reasoning extends StatefulWidget {
  final String text;
  final bool expanded;
  const _Reasoning({required this.text, required this.expanded});

  @override
  State<_Reasoning> createState() => _ReasoningState();
}

class _ReasoningState extends State<_Reasoning> {
  late bool _open;

  @override
  void initState() {
    super.initState();
    _open = widget.expanded;
  }

  @override
  void didUpdateWidget(covariant _Reasoning oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.expanded != widget.expanded) {
      _open = widget.expanded;
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final textStyle = theme.textTheme.bodySmall!.copyWith(
      fontStyle: FontStyle.italic,
      color: theme.hintColor,
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final painter = TextPainter(
          text: TextSpan(text: widget.text, style: textStyle),
          textDirection: Directionality.of(context),
        )..layout(maxWidth: constraints.maxWidth - 14);
        final short = painter.computeLineMetrics().length < 2;
        return Container(
          key: const Key('assistant-reasoning-block'),
          margin: const EdgeInsets.only(bottom: 6),
          decoration: BoxDecoration(
            border: Border(
              left: BorderSide(
                color: theme.colorScheme.secondary.withValues(alpha: .5),
                width: 2,
              ),
            ),
          ),
          child: short
              ? KeyedSubtree(
                  key: const Key('reasoning-inline'),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(8, 5, 4, 5),
                    child: Text(widget.text, style: textStyle),
                  ),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Semantics(
                      button: true,
                      expanded: _open,
                      excludeSemantics: true,
                      label: _open
                          ? 'Collapse reasoning details'
                          : 'Expand reasoning details',
                      child: InkWell(
                        key: const Key('reasoning-toggle'),
                        onTap: () => setState(() => _open = !_open),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(minHeight: 48),
                          child: Padding(
                            padding: const EdgeInsets.fromLTRB(8, 4, 4, 4),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.psychology_alt_outlined,
                                  size: 13,
                                  color: theme.hintColor,
                                ),
                                const SizedBox(width: 5),
                                Flexible(
                                  child: Text(
                                    _open
                                        ? 'reasoning'
                                        : 'reasoning (tap to expand)',
                                    style: theme.textTheme.labelSmall!.copyWith(
                                      color: theme.hintColor,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ),
                    if (_open)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(8, 4, 4, 4),
                        child: Text(widget.text, style: textStyle),
                      ),
                  ],
                ),
        );
      },
    );
  }
}

class _SharedSessionBanner extends StatelessWidget {
  final String url;
  final VoidCallback onStop;

  const _SharedSessionBanner({required this.url, required this.onStop});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Theme.of(context).colorScheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
        child: Row(
          children: [
            const Icon(Icons.public_rounded, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Shared: anyone with the link can view'),
                  Semantics(
                    label: 'Shared session link $url',
                    child: SelectableText(
                      url,
                      maxLines: 1,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 11,
                      ),
                    ),
                  ),
                ],
              ),
            ),
            IconButton(
              tooltip: 'Copy share link',
              onPressed: () => Clipboard.setData(ClipboardData(text: url)),
              icon: const Icon(Icons.copy_rounded),
            ),
            TextButton(onPressed: onStop, child: const Text('Stop sharing')),
          ],
        ),
      ),
    );
  }
}

class _TodosSheet extends StatefulWidget {
  final ConnectionController conn;
  final String sessionID;
  const _TodosSheet({required this.conn, required this.sessionID});

  @override
  State<_TodosSheet> createState() => _TodosSheetState();
}

class _TodosSheetState extends State<_TodosSheet> {
  List<Todo>? _todos;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final t = await api.todos(widget.sessionID);
      if (mounted) setState(() => _todos = t);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: _todos == null && _error == null
            ? const Center(heightFactor: 2, child: CircularProgressIndicator())
            : _error != null
            ? Text(_error!, style: TextStyle(color: theme.colorScheme.error))
            : _todos!.isEmpty
            ? Text(
                'No todos in this session.',
                style: TextStyle(color: theme.hintColor),
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'TODO LIST',
                    style: theme.textTheme.labelSmall!.copyWith(
                      color: theme.hintColor,
                      letterSpacing: 1,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Flexible(
                    child: ListView(
                      shrinkWrap: true,
                      children: [
                        for (final t in _todos!)
                          CheckboxListTile(
                            dense: true,
                            value: t.done,
                            controlAffinity: ListTileControlAffinity.leading,
                            title: Text(
                              t.content,
                              style: TextStyle(
                                decoration: t.done
                                    ? TextDecoration.lineThrough
                                    : null,
                                color: t.done ? theme.hintColor : null,
                              ),
                            ),
                            subtitle:
                                t.status == 'pending' && t.priority == null
                                ? null
                                : Text(
                                    [
                                      if (t.status != 'pending')
                                        t.status.replaceAll('_', ' '),
                                      if (t.priority != null)
                                        '${t.priority} priority',
                                    ].join(' · '),
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.hintColor,
                                    ),
                                  ),
                            onChanged: null,
                          ),
                      ],
                    ),
                  ),
                ],
              ),
      ),
    );
  }
}

class _DiffSheet extends StatefulWidget {
  final ConnectionController conn;
  final String sessionID;
  const _DiffSheet({required this.conn, required this.sessionID});

  @override
  State<_DiffSheet> createState() => _DiffSheetState();
}

class _DiffSheetState extends State<_DiffSheet> {
  List<FileDiff>? _diffs;
  String? _error;

  @override
  void initState() {
    super.initState();
    _fetch();
  }

  Future<void> _fetch() async {
    try {
      final api = await widget.conn.prepareActionTransport();
      if (api == null) throw StateError('OpenCode is reconnecting.');
      final d = await api.diff(widget.sessionID);
      if (mounted) setState(() => _diffs = d);
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .75,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: _diffs == null && _error == null
              ? const Center(child: CircularProgressIndicator())
              : _error != null
              ? Text(_error!, style: TextStyle(color: theme.colorScheme.error))
              : _diffs!.isEmpty
              ? Text(
                  'No file changes yet.',
                  style: TextStyle(color: theme.hintColor),
                )
              : Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'CHANGES',
                      style: theme.textTheme.labelSmall!.copyWith(
                        color: theme.hintColor,
                        letterSpacing: 1,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Expanded(
                      child: ListView.builder(
                        itemCount: _diffs!.length,
                        itemBuilder: (context, i) {
                          final d = _diffs![i];
                          final c = d.counts;
                          return Card.filled(
                            margin: const EdgeInsets.symmetric(vertical: 3),
                            child: ListTile(
                              dense: true,
                              leading: const Icon(
                                Icons.description_outlined,
                                size: 20,
                              ),
                              title: Text(
                                d.file.split('/').last,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              subtitle: Text(
                                d.file,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(fontSize: 11),
                              ),
                              trailing: Row(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    '+${c.added}',
                                    style: TextStyle(
                                      color: Colors.green.shade400,
                                      fontFamily: 'monospace',
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Text(
                                    '-${c.removed}',
                                    style: TextStyle(
                                      color: theme.colorScheme.error,
                                      fontFamily: 'monospace',
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  const Icon(Icons.chevron_right_rounded),
                                ],
                              ),
                              onTap: () {
                                showModalBottomSheet(
                                  context: context,
                                  isScrollControlled: true,
                                  builder: (_) => _FileDiffView(diff: d),
                                );
                              },
                            ),
                          );
                        },
                      ),
                    ),
                  ],
                ),
        ),
      ),
    );
  }
}

class _FileDiffView extends StatelessWidget {
  final FileDiff diff;
  const _FileDiffView({required this.diff});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final beforeLines = diff.before?.split('\n') ?? [];
    final afterLines = diff.after?.split('\n') ?? [];

    final patch = diff.patch;
    if (patch != null && patch.isNotEmpty) {
      return _patchView(context, patch);
    }

    // Naive alignment: common prefix/suffix; middle block replaced.
    int p = 0;
    while (p < beforeLines.length &&
        p < afterLines.length &&
        beforeLines[p] == afterLines[p]) {
      p++;
    }
    int s = 0;
    while (s < beforeLines.length - p &&
        s < afterLines.length - p &&
        beforeLines[beforeLines.length - 1 - s] ==
            afterLines[afterLines.length - 1 - s]) {
      s++;
    }

    final displayRows = <Widget>[];
    for (var i = 0; i < p && i < beforeLines.length; i++) {
      displayRows.add(_row(beforeLines[i], null, theme));
    }
    for (var i = p; i < beforeLines.length - s; i++) {
      displayRows.add(_row(beforeLines[i], false, theme));
    }
    for (var i = p; i < afterLines.length - s; i++) {
      displayRows.add(_row(afterLines[i], true, theme));
    }
    for (var i = beforeLines.length - s; i < beforeLines.length; i++) {
      if (i >= 0) displayRows.add(_row(beforeLines[i], null, theme));
    }

    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      diff.file,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontFamily: 'monospace',
                        fontSize: 13,
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: 'Copy updated file',
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    onPressed: () => Clipboard.setData(
                      ClipboardData(text: diff.after ?? ''),
                    ),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: theme.dividerColor),
            Expanded(
              child: SingleChildScrollView(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.all(8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: displayRows.isEmpty
                        ? [
                            const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text('(empty)'),
                            ),
                          ]
                        : displayRows,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _patchView(BuildContext context, String patch) {
    final theme = Theme.of(context);
    final rows = patch.split('\n').map((line) {
      final kind = line.startsWith('@@')
          ? _DiffLineKind.header
          : line.startsWith('+') && !line.startsWith('+++')
          ? _DiffLineKind.added
          : line.startsWith('-') && !line.startsWith('---')
          ? _DiffLineKind.removed
          : _DiffLineKind.context;
      return _patchRow(line, kind, theme);
    }).toList();
    return _diffScaffold(context, rows);
  }

  Widget _diffScaffold(BuildContext context, List<Widget> rows) {
    final theme = Theme.of(context);
    final copyText = diff.after ?? diff.patch ?? '';
    final copyLabel = diff.after != null ? 'Copy updated file' : 'Copy patch';
    return SafeArea(
      child: SizedBox(
        height: MediaQuery.of(context).size.height * .85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          diff.file,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontFamily: 'monospace',
                            fontSize: 13,
                          ),
                        ),
                        if (diff.status != null)
                          Text(
                            diff.status!,
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: copyLabel,
                    icon: const Icon(Icons.copy_rounded, size: 18),
                    onPressed: copyText.isEmpty
                        ? null
                        : () =>
                              Clipboard.setData(ClipboardData(text: copyText)),
                  ),
                ],
              ),
            ),
            Divider(height: 1, color: theme.dividerColor),
            Expanded(
              child: SingleChildScrollView(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.all(8),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: rows.isEmpty
                        ? [
                            const Padding(
                              padding: EdgeInsets.all(20),
                              child: Text('(empty diff)'),
                            ),
                          ]
                        : rows,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _patchRow(String line, _DiffLineKind kind, ThemeData theme) {
    final bg = switch (kind) {
      _DiffLineKind.added => Colors.green.withValues(alpha: .15),
      _DiffLineKind.removed => theme.colorScheme.error.withValues(alpha: .15),
      _DiffLineKind.header => theme.colorScheme.primary.withValues(alpha: .12),
      _DiffLineKind.context => null,
    };
    final foreground = switch (kind) {
      _DiffLineKind.removed => theme.colorScheme.error,
      _DiffLineKind.header => theme.colorScheme.primary,
      _ => null,
    };
    return Container(
      color: bg,
      constraints: const BoxConstraints(minWidth: 400),
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text(
        line,
        style: TextStyle(
          fontFamily: 'monospace',
          fontSize: 12,
          height: 1.4,
          color: foreground,
        ),
      ),
    );
  }

  Widget _row(
    String line,
    bool? addedRemoved /*null=keep,true=add,false=remove*/,
    ThemeData theme,
  ) {
    final bg = addedRemoved == true
        ? Colors.green.withValues(alpha: .15)
        : addedRemoved == false
        ? theme.colorScheme.error.withValues(alpha: .15)
        : null;
    return Container(
      color: bg,
      constraints: const BoxConstraints(minWidth: 400),
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Text(
        line,
        style: TextStyle(
          fontFamily: 'monospace',
          fontSize: 12,
          height: 1.4,
          color: addedRemoved == false ? theme.colorScheme.error : null,
        ),
      ),
    );
  }
}

enum _DiffLineKind { context, added, removed, header }

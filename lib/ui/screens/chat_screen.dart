import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../state/connection.dart';
import '../../voice/controller.dart';
import '../../voice/voice_ui.dart';
import '../widgets/file_preview.dart';
import '../widgets/markdown.dart';
import '../widgets/pickers.dart';
import '../widgets/tool_card.dart';

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

  final inMemoryBytes = file.bytes;
  if (inMemoryBytes != null) {
    return inMemoryBytes.length <= maxBytes ? inMemoryBytes : null;
  }

  final stream =
      file.readStream ??
      (file.path == null ? null : file.xFile.openRead(0, maxBytes + 1));
  if (stream == null) {
    throw StateError('The selected file could not be read.');
  }

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
      final session = await controller.api!.createSession();
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
      await controller.api!.renameSession(s.id, title);
      await controller.refreshSessions();
    }
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
        await controller.api!.deleteSession(session.id);
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
  final List<PromptAttachment> initialAttachments;

  const ChatScreen({
    super.key,
    required this.sessionID,
    this.voiceController,
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
  bool _permissionDialogScheduled = false;
  bool _permissionDismissScheduled = false;
  String? _activePermissionID;
  Route<void>? _activePermissionRoute;
  Future<VoiceComposerController>? _voiceFuture;
  VoiceComposerController? _voice;
  bool _voiceOpening = false;
  String? _localShareUrl;
  String? _promptError;

  String? get _shareUrl =>
      _conn.sessionsById[widget.sessionID]?.shareUrl ?? _localShareUrl;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _attachments.addAll(widget.initialAttachments);
    _conn = _readConn();
    _dataRefreshRevision = _conn.dataRefreshRevision;
    _conn.addListener(_onConnectionChanged);
    _load();
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
      final msgs = await _conn.api!.messages(widget.sessionID);
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
      if (_attachments.length >= _maxAttachmentCount) {
        throw StateError('You can attach up to $_maxAttachmentCount files.');
      }
      final currentBytes = _attachments.fold<int>(
        0,
        (total, attachment) => total + _attachmentByteLength(attachment),
      );
      if (currentBytes >= _maxAggregateAttachmentBytes) {
        throw StateError('Attachments must total no more than 20 MB.');
      }
      final result = await FilePicker.pickFiles(
        dialogTitle: 'Attach to prompt',
        allowMultiple: false,
        withData: false,
        withReadStream: true,
      );
      if (result == null || result.files.isEmpty) return;
      final file = result.files.single;
      final size = file.size;
      if (size > _maxAttachmentBytes) {
        throw StateError('Each attachment must be 10 MB or smaller.');
      }
      if (size > 0 && currentBytes + size > _maxAggregateAttachmentBytes) {
        throw StateError('Attachments must total no more than 20 MB.');
      }
      final remainingAggregateBytes =
          _maxAggregateAttachmentBytes - currentBytes;
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
      if (mounted) setState(() => _attachments.add(attachment));
    } catch (error) {
      if (mounted) _showActionError(error);
    }
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
    try {
      await _conn.api!.abort(widget.sessionID);
    } catch (_) {}
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
      final url = await _conn.repository?.shareSession(widget.sessionID);
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
      await _conn.repository?.unshareSession(widget.sessionID);
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
      final id = await _conn.repository?.forkSession(widget.sessionID);
      if (id == null) throw StateError('The session could not be forked');
      await _conn.refreshSessions();
      if (mounted) Navigator.of(context).pushReplacementNamed('/chat/$id');
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
      await _conn.repository?.compactSession(
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
      await _conn.repository?.revertSession(widget.sessionID, target.info.id);
      await _load();
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _restore() async {
    try {
      await _conn.repository?.restoreSession(widget.sessionID);
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
      await _conn.api!.promptAsync(
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
      await _conn.api!.shell(
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

  Future<void> _slashCommandDialog() async {
    final cmdCtrl = TextEditingController();
    final argCtrl = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Run slash command'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: cmdCtrl,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Command',
                hintText: 'compact / review / init',
              ),
            ),
            TextField(
              controller: argCtrl,
              decoration: const InputDecoration(labelText: 'Arguments'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Run'),
          ),
        ],
      ),
    );
    if (ok != true) return;
    final c = cmdCtrl.text.trim().replaceFirst('/', '');
    if (c.isEmpty) return;
    try {
      await _conn.api!.slashCommand(
        widget.sessionID,
        c,
        argCtrl.text.trim(),
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

  void _showTodos() {
    showModalBottomSheet(
      context: context,
      showDragHandle: true,
      builder: (_) => _TodosSheet(conn: _conn, sessionID: widget.sessionID),
    );
  }

  void _showDiff() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _DiffSheet(conn: _conn, sessionID: widget.sessionID),
    );
  }

  Future<FilePreviewData> _loadToolOutputFile(ToolOutputFile file) async {
    final path = file.path;
    final api = _conn.api;
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
    final mime = data.mimeType ?? file.mimeType ?? 'application/octet-stream';
    final attachment = PromptAttachment(
      mime: mime,
      filename: file.displayName,
      url: 'data:$mime;base64,${base64Encode(bytes)}',
    );
    if (!mounted) return;
    setState(() => _attachments.add(attachment));
    _focus.requestFocus();
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('${file.displayName} attached. Add your comment.'),
        duration: const Duration(seconds: 2),
      ),
    );
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
          IconButton(
            tooltip: 'Changes',
            icon: const Icon(Icons.difference_outlined),
            onPressed: _showDiff,
          ),
          IconButton(
            tooltip: 'Todos',
            icon: const Icon(Icons.checklist_rounded),
            onPressed: _showTodos,
          ),
          if (busy)
            IconButton(
              tooltip: 'Stop',
              icon: Icon(
                Icons.stop_circle_outlined,
                color: theme.colorScheme.error,
              ),
              onPressed: _abort,
            ),
          PopupMenuButton<String>(
            onSelected: (v) async {
              if (v == 'shell') await _runShellDialog();
              if (v == 'slash') await _slashCommandDialog();
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
              const PopupMenuItem(value: 'slash', child: Text('Run command')),
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
                : Column(
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
                                  child: ListView.builder(
                                    reverse: true,
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 12,
                                      vertical: 10,
                                    ),
                                    itemCount:
                                        _messages.length + (busy ? 1 : 0),
                                    itemBuilder: (context, i) {
                                      final index = _messages.length - 1 - i;
                                      if (index < 0) return _TypingIndicator();
                                      final m = _messages[index];
                                      final meta = _messageMeta(
                                        _messages,
                                        index,
                                      );
                                      return _MessageView(
                                        m: m,
                                        meta: meta,
                                        filePreviewLoader: _loadToolOutputFile,
                                        onAttachFile: _attachToolOutputFile,
                                        onDownloadFile: _downloadToolOutputFile,
                                      );
                                    },
                                  ),
                                ),
                              ),
                      ),
                      _ChatComposer(
                        controller: _composer,
                        focusNode: _focus,
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
    unawaited(_voice?.cancel());
    if (widget.voiceController == null) _voice?.dispose();
    _composer.dispose();
    _focus.dispose();
    super.dispose();
  }
}

class _ChatComposer extends StatelessWidget {
  const _ChatComposer({
    required this.controller,
    required this.focusNode,
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

  final TextEditingController controller;
  final FocusNode focusNode;
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
    final compact = media.size.height - media.viewInsets.bottom < 520;
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
}

class _ComposerField extends StatelessWidget {
  const _ComposerField({
    required this.controller,
    required this.focusNode,
    required this.maxLines,
    required this.contentPadding,
  });

  final TextEditingController controller;
  final FocusNode focusNode;
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
            button: true,
            excludeSemantics: true,
            label: 'Preview attachment ${attachment.filename}',
            onTap: openPreview,
            child: Tooltip(
              message: 'Preview ${attachment.filename}',
              child: InkWell(
                onTap: openPreview,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(minHeight: 48),
                  child: Padding(
                    padding: const EdgeInsets.only(left: 12),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.attach_file_rounded, size: 16),
                        const SizedBox(width: 6),
                        ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 180),
                          child: Text(
                            attachment.filename,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        const SizedBox(width: 8),
                        const Icon(Icons.visibility_outlined, size: 16),
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
            label: 'Remove attachment ${attachment.filename}',
            button: true,
            onTap: onRemove,
            child: IconButton(
              tooltip: 'Remove attachment ${attachment.filename}',
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
      title: Text(
        permission.permission.isEmpty
            ? 'Permission needed'
            : permission.permission,
      ),
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
      runs.add(_AssistantPartRun([current]));
      index += 1;
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
            Padding(
              padding: const EdgeInsets.fromLTRB(8, 0, 8, 7),
              child: Column(
                children: [
                  for (final part in widget.parts)
                    ToolCard(
                      key: ValueKey(part.id ?? part.callID),
                      toolName: part.toolName!,
                      state: part.toolState,
                      filePreviewLoader: widget.filePreviewLoader,
                      onAttachFile: widget.onAttachFile,
                      onDownloadFile: widget.onDownloadFile,
                    ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

class _AssistantMessagePart extends StatelessWidget {
  const _AssistantMessagePart({
    required this.part,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  final Part part;
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;

  @override
  Widget build(BuildContext context) {
    if (part.type == 'text') {
      return Padding(
        padding: const EdgeInsets.only(bottom: 4),
        child: MarkdownText(part.text),
      );
    }
    if (part.type == 'reasoning') return _Reasoning(text: part.text);
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
  final ToolOutputFileLoader filePreviewLoader;
  final ToolOutputFileAction onAttachFile;
  final ToolOutputFileAction onDownloadFile;
  const _MessageView({
    required this.m,
    required this.meta,
    required this.filePreviewLoader,
    required this.onAttachFile,
    required this.onDownloadFile,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isUser = m.info.role == 'user';
    final visibleParts = m.parts.where((p) => p.isRenderable).toList();
    final assistantRuns = isUser
        ? const <_AssistantPartRun>[]
        : _groupAssistantParts(visibleParts);

    final metaParts = <String>[
      ?meta.modelLabel,
      if (meta.turnTokens case final tokens?) '${_fmtTokens(tokens)} tok',
      if (meta.turnCost case final cost?) '\$${cost.toStringAsFixed(4)}',
    ];

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
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
}

String? _modelLabel(MessageInfo info) {
  final provider = info.providerID?.trim();
  final model = info.modelID?.trim();
  if (model?.isNotEmpty != true) return null;
  return provider?.isNotEmpty == true ? '$provider/$model' : model;
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    void openPreview() => showFilePreviewSheet(
      context,
      FilePreviewData.fromDataUrl(
        name: _filename,
        mimeType: part.mime,
        url: part.url,
      ),
    );

    return Semantics(
      button: true,
      excludeSemantics: true,
      label: 'Preview attachment $_filename',
      onTap: openPreview,
      child: Tooltip(
        message: 'Preview attachment',
        child: InkWell(
          borderRadius: BorderRadius.circular(10),
          onTap: openPreview,
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
                const Icon(Icons.attach_file_rounded, size: 17),
                const SizedBox(width: 7),
                Flexible(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _filename,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.bodySmall?.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      Text(
                        '$_type · prompt attachment',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(Icons.visibility_outlined, size: 15),
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
  const _Reasoning({required this.text});

  @override
  State<_Reasoning> createState() => _ReasoningState();
}

class _ReasoningState extends State<_Reasoning> {
  bool _open = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      margin: const EdgeInsets.only(bottom: 6),
      decoration: BoxDecoration(
        border: Border(
          left: BorderSide(
            color: theme.colorScheme.secondary.withValues(alpha: .5),
            width: 2,
          ),
        ),
      ),
      child: Column(
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
                          _open ? 'reasoning' : 'reasoning (tap to expand)',
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
              child: Text(
                widget.text,
                style: theme.textTheme.bodySmall!.copyWith(
                  fontStyle: FontStyle.italic,
                  color: theme.hintColor,
                ),
              ),
            ),
        ],
      ),
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
      final t = await widget.conn.api!.todos(widget.sessionID);
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
      final d = await widget.conn.api!.diff(widget.sessionID);
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

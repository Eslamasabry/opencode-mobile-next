import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../state/connection.dart';
import '../widgets/markdown.dart';
import '../widgets/tool_card.dart';

String _fmtSessionTime(int ms) {
  final d = DateTime.fromMillisecondsSinceEpoch(ms);
  final now = DateTime.now();
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
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
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('Could not create chat: $e')));
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
                child: Text('Not connected',
                    style: Theme.of(context).textTheme.bodyMedium!
                        .copyWith(color: Theme.of(context).hintColor)),
              )
            else if (sessions.isEmpty)
              Center(
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Icon(Icons.forum_outlined,
                      size: 44, color: Theme.of(context).hintColor),
                  const SizedBox(height: 12),
                  const Text('No chats yet'),
                  const SizedBox(height: 12),
                  FilledButton.icon(
                      onPressed: () => _newChat(context),
                      icon: const Icon(Icons.add_rounded),
                      label: const Text('Start one')),
                ]),
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
                                  color: Theme.of(context).colorScheme.primary))
                          : Icon(Icons.chat_bubble_outline_rounded,
                              size: 20, color: Theme.of(context).hintColor),
                      title: Text(s.title?.isNotEmpty == true ? s.title! : 'New chat',
                          maxLines: 1, overflow: TextOverflow.ellipsis),
                      subtitle: Text(_fmtSessionTime(
                          s.time?.updated ?? s.time?.created ?? 0)),
                      trailing: PopupMenuButton<String>(
                        onSelected: (v) async {
                          if (v == 'rename') await _rename(context, s);
                          if (v == 'delete') {
                            await controller.api!.deleteSession(s.id);
                            await controller.refreshSessions();
                          }
                        },
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
    final ctrl = TextEditingController(text: s.title ?? '');
    final title = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Rename chat'),
        content: TextField(controller: ctrl, autofocus: true),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
              child: const Text('Save')),
        ],
      ),
    );
    if (title != null && title.isNotEmpty) {
      await controller.api!.renameSession(s.id, title);
      await controller.refreshSessions();
    }
  }
}

// =====================================================================
// Chat screen
// =====================================================================

class ChatScreen extends StatefulWidget {
  final String sessionID;
  const ChatScreen({super.key, required this.sessionID});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  late final ConnectionController _conn;
  late final StreamSubscription<EventEnvelope> _sub;
  List<MessageWithParts> _messages = [];
  bool _loading = true;
  Object? _error;
  final _composer = TextEditingController();
  final _focus = FocusNode();
  final Set<String> _shownPermissions = {};

  @override
  void initState() {
    super.initState();
    _conn = _readConn();
    _load();
    _sub = _conn.events.listen(_onEvent);
  }

  ConnectionController _readConn() {
    final ctx = context;
    final container = ProviderScope.containerOf(ctx);
    return container.read(connProvider);
  }

  void _onEvent(EventEnvelope env) {
    switch (env.type) {
      case 'message.part.updated':
        final partJson = env.properties['part'];
        if (partJson is Map<String, dynamic>) {
          final p = Part.fromJson(partJson);
          final mid = p.messageID;
          if (mid != null) {
            setState(() => _upsertPart(mid, p));
          }
        }
        break;
      case 'message.updated':
        final info = env.properties['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          final idx = _messages.indexWhere((m) => m.info.id == msg.id);
          if (idx >= 0) {
            setState(() => _messages[idx] =
                MessageWithParts(info: msg, parts: _messages[idx].parts));
          } else if (msg.sessionID == widget.sessionID) {
            setState(() => _messages.add(MessageWithParts(info: msg)));
          }
        }
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

  void _upsertPart(String messageID, Part part) {
    var bundle = _messages.firstWhere(
      (m) => m.info.id == messageID,
      orElse: () {
        final b = MessageWithParts(
            info: MessageInfo(
                id: messageID, sessionID: widget.sessionID, role: 'assistant'));
        _messages.add(b);
        return b;
      },
    );
    final idx = bundle.parts.indexWhere((p) =>
        (part.callID != null && p.callID == part.callID) ||
        (part.id != null && p.id == part.id && p.type == part.type));
    if (idx >= 0) {
      bundle.parts[idx] = part;
    } else {
      bundle.parts.add(part);
    }
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final msgs = await _conn.api!.messages(widget.sessionID);
      msgs.sort((a, b) =>
          (a.info.time?.created ?? 0).compareTo(b.info.time?.created ?? 0));
      if (!mounted) return;
      setState(() => _messages = msgs);
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _send() async {
    final text = _composer.text.trim();
    if (text.isEmpty || _conn.api == null) return;
    _composer.clear();
    _focus.requestFocus();

    // Optimistic user bubble.
    setState(() {
      _messages.add(MessageWithParts(
        info: MessageInfo(
          id: 'local-${DateTime.now().microsecondsSinceEpoch}',
          sessionID: widget.sessionID,
          role: 'user',
          time: MsgTime(created: DateTime.now().millisecondsSinceEpoch),
        ),
        parts: [Part(type: 'text', text: text)],
      ));
    });
    try {
      await _conn.api!.promptAsync(
        widget.sessionID,
        text: text,
        model: _conn.selectedModel,
        agent: _conn.selectedAgent.isNotEmpty ? _conn.selectedAgent : null,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Send failed: $e')));
    }
  }

  Future<void> _abort() async {
    try {
      await _conn.api!.abort(widget.sessionID);
    } catch (_) {}
  }

  // ----- dialogs -----

  void _maybeShowPermissionDialog() {
    final perm = _conn.permissions[widget.sessionID];
    if (perm == null || _shownPermissions.contains(perm.key)) return;
    _shownPermissions.add(perm.key);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      showDialog<void>(
        context: context,
        barrierDismissible: false,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.admin_panel_settings_outlined),
          title: Text(perm.type.isEmpty ? 'Permission needed' : perm.type),
          content: Text(perm.title),
          actions: [
            TextButton(
                onPressed: () {
                  _conn.answerPermission(perm.sessionID, 'reject');
                  Navigator.pop(ctx);
                },
                child: const Text('Reject')),
            OutlinedButton(
                onPressed: () {
                  _conn.answerPermission(perm.sessionID, 'always');
                  Navigator.pop(ctx);
                },
                child: const Text('Always allow')),
            FilledButton(
                onPressed: () {
                  _conn.answerPermission(perm.sessionID, 'once');
                  Navigator.pop(ctx);
                },
                child: const Text('Allow once')),
          ],
        ),
      );
    });
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
            decoration:
                const InputDecoration(hintText: 'e.g. npm test',
                    prefixText: '\$ ')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, ctrl.text.trim()),
              child: const Text('Run')),
        ],
      ),
    );
    if (cmd == null || cmd.isEmpty) return;
    try {
      await _conn.api!.shell(widget.sessionID,
          command: cmd,
          agent: _conn.selectedAgent.isNotEmpty ? _conn.selectedAgent : 'build',
          model: _conn.selectedModel);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Failed: $e')));
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
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
              controller: cmdCtrl,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'Command',
                  hintText: 'compact / review / init')),
          TextField(
              controller: argCtrl,
              decoration: const InputDecoration(labelText: 'Arguments')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true), child: const Text('Run')),
        ],
      ),
    );
    if (ok != true) return;
    final c = cmdCtrl.text.trim().replaceFirst('/', '');
    if (c.isEmpty) return;
    try {
      await _conn.api!
          .slashCommand(widget.sessionID, c, argCtrl.text.trim());
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Failed: $e')));
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final busy = _conn.busySessions.contains(widget.sessionID);
    final hasPerm = _conn.permissions.containsKey(widget.sessionID);
    if (hasPerm) _maybeShowPermissionDialog();

    final session = _conn.sessionsById[widget.sessionID];
    final title = session?.title;

    return Scaffold(
      appBar: AppBar(
        title: Text(title?.isNotEmpty == true ? title! : 'Chat',
            overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(tooltip: 'Changes', icon: const Icon(Icons.difference_outlined),
              onPressed: _showDiff),
          IconButton(tooltip: 'Todos', icon: const Icon(Icons.checklist_rounded),
              onPressed: _showTodos),
          if (busy)
            IconButton(tooltip: 'Stop', icon: Icon(Icons.stop_circle_outlined,
                color: theme.colorScheme.error), onPressed: _abort),
          PopupMenuButton<String>(
            onSelected: (v) async {
              if (v == 'shell') await _runShellDialog();
              if (v == 'slash') await _slashCommandDialog();
              if (v == 'reload') await _load();
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'shell', child: Text('Run shell command')),
              PopupMenuItem(value: 'slash', child: Text('Slash command…')),
              PopupMenuItem(value: 'reload', child: Text('Reload messages')),
            ],
          ),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text('$_error', textAlign: TextAlign.center,
                        style: TextStyle(color: theme.colorScheme.error)),
                    const SizedBox(height: 8),
                    FilledButton.tonal(onPressed: _load, child: const Text('Retry')),
                  ]),
                )
              : Column(children: [
                  Expanded(
                    child: _messages.isEmpty
                        ? Center(
                            child: Text('Ask opencode to do something…',
                                style: TextStyle(color: theme.hintColor)))
                        : NotificationListener<ScrollNotification>(
                            onNotification: (_) => false,
                            child: Align(
                              alignment: Alignment.topCenter,
                              child: ListView.builder(
                                reverse: true,
                                padding:
                                    const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                itemCount: _messages.length + (busy ? 1 : 0),
                                itemBuilder: (context, i) {
                                  final index = _messages.length - 1 - i;
                                  if (index < 0) return _TypingIndicator();
                                  final m = _messages[index];
                                  return _MessageView(m: m);
                                },
                              ),
                            ),
                          ),
                  ),
                  SafeArea(
                    top: false,
                    child: Container(
                      padding: const EdgeInsets.fromLTRB(12, 6, 12, 8),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: .35),
                      ),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _composer,
                              focusNode: _focus,
                              minLines: 1,
                              maxLines: 6,
                              textCapitalization: TextCapitalization.sentences,
                              decoration: InputDecoration(
                                hintText: _conn.selectedAgent.isNotEmpty
                                    ? 'Message (${_conn.selectedAgent}${_conn.selectedModel != null ? ' · ${_conn.selectedModel!.modelID}' : ''})'
                                    : 'Message',
                                border: OutlineInputBorder(
                                    borderRadius: BorderRadius.circular(22),
                                    borderSide: BorderSide.none),
                                filled: true,
                                fillColor: theme.colorScheme.surface,
                                contentPadding: const EdgeInsets.symmetric(
                                    horizontal: 16, vertical: 9),
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          busy
                              ? IconButton.filledTonal(
                                  tooltip: 'Stop',
                                  onPressed: _abort,
                                  icon: Icon(Icons.stop_rounded,
                                      color: theme.colorScheme.error))
                              : IconButton.filled(
                                  tooltip: 'Send',
                                  onPressed: _send,
                                  icon: const Icon(Icons.send_rounded)),
                        ],
                      ),
                    ),
                  ),
                ]),
    );
  }

  @override
  void dispose() {
    _sub.cancel();
    _composer.dispose();
    _focus.dispose();
    super.dispose();
  }
}

class _TypingIndicator extends StatefulWidget {
  @override
  State<_TypingIndicator> createState() => __TypingIndicatorState();
}

class __TypingIndicatorState extends State<_TypingIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c =
      AnimationController(vsync: this, duration: const Duration(seconds: 1))..repeat();

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: Tween(begin: .3, end: 1.0).animate(
          CurvedAnimation(parent: _c, curve: Curves.easeInOut)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(children: [
          Icon(Icons.smart_toy_outlined,
              size: 16, color: Theme.of(context).colorScheme.primary),
          const SizedBox(width: 8),
          Text('thinking…',
              style: Theme.of(context).textTheme.bodySmall!
                  .copyWith(color: Theme.of(context).hintColor)),
        ]),
      ),
    );
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }
}

class _MessageView extends StatelessWidget {
  final MessageWithParts m;
  const _MessageView({required this.m});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isUser = m.info.role == 'user';
    final visibleParts = m.parts.where((p) => p.isRenderable).toList();

    final meta = <String>[
      if (!isUser && m.info.modelID != null) '${m.info.providerID}/${m.info.modelID}',
      if (m.info.tokens.total > 0)
        '${_fmtTokens(m.info.tokens.total)} tok',
      if (m.info.cost > 0) '\$${m.info.cost.toStringAsFixed(4)}',
    ];

    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Column(
        crossAxisAlignment:
            isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(
                maxWidth: MediaQuery.of(context).size.width * .88),
            padding: isUser
                ? const EdgeInsets.symmetric(horizontal: 14, vertical: 10)
                : const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            decoration: isUser
                ? BoxDecoration(
                    color: theme.colorScheme.primaryContainer.withValues(alpha: .55),
                    borderRadius: BorderRadius.circular(16))
                : null,
            child: isUser
                ? SelectableText(m.parts.map((p) => p.text).where((t) => t.isNotEmpty).join('\n'))
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (final p in visibleParts)
                        if (p.type == 'text')
                          Padding(
                            padding: const EdgeInsets.only(bottom: 4),
                            child: MarkdownText(p.text),
                          )
                        else if (p.type == 'reasoning')
                          _Reasoning(text: p.text)
                        else if (p.type == 'tool')
                          ToolCard(toolName: p.toolName ?? 'tool', state: p.toolState),
                    ],
                  ),
          ),
          if (meta.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 3, left: 6, right: 6),
              child: Text(meta.join('  ·  '),
                  style: theme.textTheme.labelSmall!
                      .copyWith(color: theme.hintColor, fontSize: 10)),
            ),
          if (m.info.errorText != null)
            Padding(
              padding: const EdgeInsets.only(top: 3),
              child: Text(m.info.errorText!,
                  style: theme.textTheme.bodySmall!
                      .copyWith(color: theme.colorScheme.error)),
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
        border: Border(left: BorderSide(color: theme.colorScheme.secondary.withValues(alpha: .5), width: 2)),
      ),
      child: InkWell(
        onTap: () => setState(() => _open = !_open),
        child: Padding(
          padding: const EdgeInsets.fromLTRB(8, 4, 4, 4),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.psychology_alt_outlined, size: 13, color: theme.hintColor),
              const SizedBox(width: 5),
              Text(_open ? 'reasoning' : 'reasoning (tap to expand)',
                  style: theme.textTheme.labelSmall!.copyWith(color: theme.hintColor)),
            ]),
            if (_open)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(widget.text,
                    style: theme.textTheme.bodySmall!
                        .copyWith(fontStyle: FontStyle.italic, color: theme.hintColor)),
              ),
          ]),
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
                    ? Text('No todos in this session.',
                        style: TextStyle(color: theme.hintColor))
                    : Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment:
                        CrossAxisAlignment.start, children: [
                        Text('TODO LIST', style: theme.textTheme.labelSmall!
                            .copyWith(color: theme.hintColor, letterSpacing: 1)),
                        const SizedBox(height: 8),
                        Flexible(
                          child: ListView(shrinkWrap: true, children: [
                            for (final t in _todos!)
                              CheckboxListTile(
                                dense: true,
                                value: t.done,
                                controlAffinity: ListTileControlAffinity.leading,
                                title: Text(t.content,
                                    style: TextStyle(
                                        decoration: t.done
                                            ? TextDecoration.lineThrough
                                            : null,
                                        color: t.done ? theme.hintColor : null)),
                                onChanged: null,
                              ),
                          ]),
                        ),
                      ]),
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
                      ? Text('No file changes yet.',
                          style: TextStyle(color: theme.hintColor))
                      : Column(crossAxisAlignment: CrossAxisAlignment.start,
                          mainAxisSize: MainAxisSize.min, children: [
                          Text('CHANGES', style: theme.textTheme.labelSmall!
                              .copyWith(color: theme.hintColor, letterSpacing: 1)),
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
                                    leading: const Icon(Icons.description_outlined, size: 20),
                                    title: Text(d.file.split('/').last,
                                        maxLines: 1, overflow: TextOverflow.ellipsis),
                                    subtitle: Text(d.file, maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontSize: 11)),
                                    trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                                      Text('+${c.added}',
                                          style: TextStyle(
                                              color: Colors.green.shade400,
                                              fontFamily: 'monospace')),
                                      const SizedBox(width: 6),
                                      Text('-${c.removed}',
                                          style: TextStyle(
                                              color: theme.colorScheme.error,
                                              fontFamily: 'monospace')),
                                      const SizedBox(width: 6),
                                      const Icon(Icons.chevron_right_rounded),
                                    ]),
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
                        ]),
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
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 8, 4),
            child: Row(children: [
              Expanded(
                child: Text(diff.file,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontFamily: 'monospace', fontSize: 13)),
              ),
              IconButton(
                icon: const Icon(Icons.copy_rounded, size: 18),
                onPressed: () => Clipboard.setData(ClipboardData(
                    text: diff.after ?? '')),
              ),
            ]),
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
                      ? [const Padding(padding: EdgeInsets.all(20), child: Text('(empty)'))]
                      : displayRows,
                ),
              ),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _row(String line, bool? addedRemoved /*null=keep,true=add,false=remove*/, ThemeData theme) {
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

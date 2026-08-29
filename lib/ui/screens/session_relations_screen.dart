import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class SessionRelationsScreen extends StatefulWidget {
  final ConnectionController controller;
  final String sessionID;

  const SessionRelationsScreen({
    super.key,
    required this.controller,
    required this.sessionID,
  });

  @override
  State<SessionRelationsScreen> createState() => _SessionRelationsScreenState();
}

class _SessionRelationsScreenState extends State<SessionRelationsScreen> {
  Session? _parent;
  List<Session>? _children;
  Object? _error;
  int _generation = 0;
  int _dataRefreshRevision = 0;

  @override
  void initState() {
    super.initState();
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_controllerChanged);
    unawaited(_load());
  }

  void _controllerChanged() {
    if (!mounted) return;
    final revision = widget.controller.dataRefreshRevision;
    if (revision == _dataRefreshRevision) return;
    _dataRefreshRevision = revision;
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() => _error = null);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      var current = widget.controller.sessionsById[widget.sessionID];
      current ??= await repository.getSessionDetails(widget.sessionID);
      final parentID = current.parentID ?? current.id;
      var parent = widget.controller.sessionsById[parentID];
      parent ??= parentID == current.id
          ? current
          : await repository.getSessionDetails(parentID);
      final children = await repository.listSessionChildren(parentID);
      if (!mounted || generation != _generation) return;
      setState(() {
        _parent = parent;
        _children = children;
      });
    } catch (error) {
      if (!mounted || generation != _generation) return;
      setState(() => _error = error);
    }
  }

  void _select(Session session) => Navigator.of(context).pop(session);

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Subagent sessions'),
      actions: [
        IconButton(
          tooltip: 'Refresh subagent sessions',
          onPressed: _load,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: _body(),
  );

  Widget _body() {
    final parent = _parent;
    final children = _children;
    if (_error != null && parent == null) {
      return ProductErrorState(message: productErrorText(_error!), onRetry: _load);
    }
    if (parent == null || children == null) return const LoadingList(rows: 5);

    return ListenableBuilder(
      listenable: widget.controller,
      builder: (context, _) => RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          children: [
            _SessionFamilyHeader(parent: parent, childCount: children.length),
            const SectionLabel('Parent session'),
            _SessionRelationTile(
              session: parent,
              current: widget.sessionID == parent.id,
              busy: widget.controller.busySessions.contains(parent.id),
              icon: Icons.chat_bubble_outline_rounded,
              onTap: () => _select(parent),
            ),
            const Divider(height: 1, indent: 64),
            SectionLabel(
              'Subagents',
              trailing: Text(
                '${children.length}',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            if (children.isEmpty)
              const Padding(
                padding: EdgeInsets.fromLTRB(16, 24, 16, 32),
                child: ProductEmptyState(
                  icon: Icons.account_tree_outlined,
                  title: 'No subagent sessions yet',
                  message:
                      'Delegated work will appear here without mixing child sessions into your main chat list.',
                ),
              )
            else
              for (var index = 0; index < children.length; index++) ...[
                _SessionRelationTile(
                  session: children[index],
                  current: widget.sessionID == children[index].id,
                  busy: widget.controller.busySessions.contains(
                    children[index].id,
                  ),
                  icon: Icons.subdirectory_arrow_right_rounded,
                  position: index + 1,
                  total: children.length,
                  onTap: () => _select(children[index]),
                ),
                if (index < children.length - 1)
                  const Divider(height: 1, indent: 64),
              ],
            if (_error != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 18, 16, 24),
                child: Text(
                  'Refresh failed: $_error',
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _generation++;
    widget.controller.removeListener(_controllerChanged);
    super.dispose();
  }
}

class _SessionFamilyHeader extends StatelessWidget {
  final Session parent;
  final int childCount;

  const _SessionFamilyHeader({required this.parent, required this.childCount});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(top: 2),
            child: Icon(
              Icons.account_tree_outlined,
              color: theme.colorScheme.primary,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  parent.title?.trim().isNotEmpty == true
                      ? parent.title!
                      : 'Parent session',
                  style: theme.textTheme.titleMedium,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  childCount == 0
                      ? 'OpenCode has not delegated work from this session.'
                      : '$childCount delegated ${childCount == 1 ? 'session' : 'sessions'} · open any transcript directly.',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SessionRelationTile extends StatelessWidget {
  final Session session;
  final bool current;
  final bool busy;
  final IconData icon;
  final int? position;
  final int? total;
  final VoidCallback onTap;

  const _SessionRelationTile({
    required this.session,
    required this.current,
    required this.busy,
    required this.icon,
    required this.onTap,
    this.position,
    this.total,
  });

  @override
  Widget build(BuildContext context) {
    final created = session.time?.created;
    final details = <String>[
      if (position != null && total != null) '$position of $total',
      if (created != null) _relativeTime(created),
      if (busy) 'Working',
    ];
    return ListTile(
      key: ValueKey('session-relation-${session.id}'),
      selected: current,
      leading: busy
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(icon),
      title: Text(
        session.title?.trim().isNotEmpty == true
            ? session.title!
            : 'Untitled session',
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: details.isEmpty ? null : Text(details.join(' · ')),
      trailing: current
          ? const Icon(Icons.check_circle_rounded)
          : const Icon(Icons.chevron_right_rounded),
      onTap: current ? null : onTap,
    );
  }
}

String _relativeTime(int milliseconds) {
  final age = DateTime.now().difference(
    DateTime.fromMillisecondsSinceEpoch(milliseconds),
  );
  if (age.inMinutes < 1) return 'Now';
  if (age.inHours < 1) return '${age.inMinutes}m ago';
  if (age.inDays < 1) return '${age.inHours}h ago';
  if (age.inDays < 7) return '${age.inDays}d ago';
  return '${(age.inDays / 7).floor()}w ago';
}

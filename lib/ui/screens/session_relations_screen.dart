import 'dart:async';

import 'package:flutter/material.dart';
import '../../l10n/app_localizations.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import '../widgets/session_handoff.dart';

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
  late final SessionNavigationScope _scope;
  bool _selecting = false;

  @override
  void initState() {
    super.initState();
    _scope = SessionNavigationScope(widget.controller);
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_controllerChanged);
    unawaited(_load());
  }

  void _controllerChanged() {
    if (!mounted) return;
    if (!_scope.matches(widget.controller)) {
      _generation++;
      setState(
        () => _error = StateError(
          'Session location changed. Return and reopen related sessions.',
        ),
      );
      return;
    }
    final revision = widget.controller.dataRefreshRevision;
    if (revision == _dataRefreshRevision) return;
    _dataRefreshRevision = revision;
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_generation;
    setState(() => _error = null);
    try {
      _scope.check(widget.controller);
      final repository = await widget.controller.prepareActionRepository();
      _scope.check(widget.controller);
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      final current = await repository.getSessionDetails(widget.sessionID);
      _scope.check(widget.controller);
      final parentID = current.parentID ?? current.id;
      var parent = widget.controller.sessionsById[parentID];
      parent ??= parentID == current.id
          ? current
          : await repository.getSessionDetails(parentID);
      final children = await repository.listSessionChildren(parentID);
      _scope.check(widget.controller);
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

  Future<void> _select(Session session) async {
    if (_selecting) return;
    setState(() => _selecting = true);
    try {
      _scope.check(widget.controller);
      final repository = await widget.controller.prepareActionRepository();
      _scope.check(widget.controller);
      if (repository == null) {
        throw StateError('OpenCode is reconnecting. Try again.');
      }
      final current = await repository.getSessionDetails(session.id);
      _scope.check(widget.controller);
      if (current.id != session.id ||
          current.directory != session.directory ||
          current.workspaceID != session.workspaceID) {
        throw StateError('Session location changed. Return and try again.');
      }
      if (mounted) Navigator.of(context).pop(current);
    } catch (_) {
      if (mounted) {
        setState(
          () => _error = StateError(
            'Session unavailable or location changed. Return or refresh to try again.',
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _selecting = false);
    }
  }

  Future<void> _pin(Session session) async {
    try {
      _scope.check(widget.controller);
      final repository = await widget.controller.prepareActionRepository();
      _scope.check(widget.controller);
      if (repository == null) {
        throw StateError('OpenCode is reconnecting. Try again.');
      }
      final current = await repository.getSessionDetails(session.id);
      _scope.check(widget.controller);
      if (current.id != session.id ||
          current.directory != session.directory ||
          current.workspaceID != session.workspaceID) {
        throw StateError('Session location changed. Return and try again.');
      }
      await widget.controller.setSessionPinned(
        session.id,
        !widget.controller.isSessionPinned(session.id),
        locationRevision: _scope.revision,
      );
    } catch (_) {
      if (mounted) {
        showProductError(
          context,
          'Could not update the pin. Return and try again.',
        );
      }
    }
  }

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
    if (_error != null &&
        (parent == null || !_scope.matches(widget.controller))) {
      return ProductErrorState(
        message: productErrorText(_error!),
        onRetry: _load,
      );
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
              onHandoff: () => showSessionHandoff(
                context,
                controller: widget.controller,
                sessionID: parent.id,
                projectID: parent.projectID,
              ),
              enabled: !_selecting,
              pinned: widget.controller.isSessionPinned(parent.id),
              onPin: widget.controller.canPinSessions
                  ? () => _pin(parent)
                  : null,
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
                  onHandoff: () => showSessionHandoff(
                    context,
                    controller: widget.controller,
                    sessionID: children[index].id,
                    projectID: children[index].projectID,
                  ),
                  enabled: !_selecting,
                  pinned: widget.controller.isSessionPinned(children[index].id),
                  onPin: widget.controller.canPinSessions
                      ? () => _pin(children[index])
                      : null,
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
  final VoidCallback onHandoff;
  final bool enabled;
  final bool pinned;
  final VoidCallback? onPin;

  const _SessionRelationTile({
    required this.session,
    required this.current,
    required this.busy,
    required this.icon,
    required this.onTap,
    required this.onHandoff,
    required this.enabled,
    required this.pinned,
    this.onPin,
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
      trailing: PopupMenuButton<String>(
        tooltip: lookupAppLocalizations(
          Localizations.localeOf(context),
        ).sessionActions,
        enabled: enabled,
        onSelected: (value) {
          if (value == 'handoff') onHandoff();
          if (value == 'pin') onPin?.call();
        },
        itemBuilder: (_) => [
          PopupMenuItem(
            value: 'handoff',
            child: Text(
              lookupAppLocalizations(
                Localizations.localeOf(context),
              ).sessionCopyHandoff,
            ),
          ),
          if (onPin != null)
            PopupMenuItem(
              value: 'pin',
              child: Text(pinned ? 'Unpin session' : 'Pin session'),
            ),
        ],
      ),
      onTap: current || !enabled ? null : onTap,
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

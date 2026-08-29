import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import 'global_sessions_screen.dart';

/// Mission Control: one glanceable surface for everything the agent fleet is
/// doing at the active location — requests waiting on the user, sessions
/// running right now, and where recent work stopped.
///
/// Every row is server truth the controller already holds; nothing here is
/// estimated. Cross-project discovery stays with the all-sessions finder,
/// reachable from the footer.
class MissionControlScreen extends StatefulWidget {
  final ConnectionController controller;

  const MissionControlScreen({super.key, required this.controller});

  @override
  State<MissionControlScreen> createState() => _MissionControlScreenState();
}

class _MissionControlScreenState extends State<MissionControlScreen> {
  bool _refreshing = false;
  String? _refreshError;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_changed);
  }

  @override
  void dispose() {
    widget.controller.removeListener(_changed);
    super.dispose();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  /// Wake-safe manual refresh: reconciliation first, so a retained screen
  /// cannot query through a repository being retired after Android idle.
  Future<void> _refresh() async {
    if (_refreshing) return;
    setState(() {
      _refreshing = true;
      _refreshError = null;
    });
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) {
        throw const ProductException('OpenCode is reconnecting. Try again.');
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) setState(() => _refreshError = error.toString());
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  int _subagentCount(String rootID) {
    var count = 0;
    for (final session in widget.controller.sessionsById.values) {
      if (session.parentID == rootID) count += 1;
    }
    return count;
  }

  void _openChat(String sessionID) {
    Navigator.of(context).pushNamed('/chat/$sessionID');
  }

  static String _relative(int? ms) {
    if (ms == null || ms <= 0) return '';
    final delta = DateTime.now().difference(
      DateTime.fromMillisecondsSinceEpoch(ms),
    );
    if (delta.inMinutes < 1) return 'now';
    if (delta.inHours < 1) return '${delta.inMinutes}m ago';
    if (delta.inDays < 1) return '${delta.inHours}h ago';
    return '${delta.inDays}d ago';
  }

  static String _place(Session session) {
    final directory = session.directory?.trim() ?? '';
    if (directory.isEmpty) return '';
    final parts = directory
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? directory : parts.last;
  }

  static String _sessionTitle(Session? session) =>
      session?.title?.trim().isNotEmpty == true
      ? session!.title!.trim()
      : 'Untitled session';

  @override
  Widget build(BuildContext context) {
    final controller = widget.controller;
    final roots = controller.sortedSessions();
    final running = roots
        .where((session) => controller.busySessions.contains(session.id))
        .toList();
    final recent = roots
        .where((session) => !controller.busySessions.contains(session.id))
        .take(8)
        .toList();
    final attention = <_AttentionItem>[
      for (final permission in controller.permissions.values)
        _AttentionItem(
          sessionID: permission.sessionID,
          icon: Icons.shield_outlined,
          title: 'Permission · ${permission.permission}',
          detail: permission.patterns.isNotEmpty
              ? permission.patterns.first
              : _sessionTitle(controller.sessionsById[permission.sessionID]),
        ),
      for (final question in controller.questions.values)
        _AttentionItem(
          sessionID: question.sessionID,
          icon: Icons.help_outline_rounded,
          title: question.prompts.isNotEmpty
              ? 'Question · ${question.prompts.first.title}'
              : 'Question',
          detail: _sessionTitle(controller.sessionsById[question.sessionID]),
        ),
      // Global (MCP elicitation) forms have no session to open; they live
      // on the Requests screen only.
      for (final form in controller.forms.values)
        if (form.sessionID != 'global')
          _AttentionItem(
            sessionID: form.sessionID,
            icon: Icons.fact_check_outlined,
            title: 'Form · ${form.title ?? 'Input requested'}',
            detail: _sessionTitle(controller.sessionsById[form.sessionID]),
          ),
    ];
    final empty = roots.isEmpty && attention.isEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mission Control'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: _refreshing ? null : _refresh,
            icon: _refreshing
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: empty
            ? ProductEmptyState(
                icon: Icons.space_dashboard_outlined,
                title: 'Nothing in flight',
                message:
                    'Sessions you run appear here with their live state. '
                    'Start one from the Workspace tab, or find one anywhere '
                    'on this server.',
                actionLabel: 'All sessions',
                onAction: _openFinder,
              )
            : ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.only(bottom: 24),
                children: [
                  if (_refreshError case final error?)
                    ProductInlineEmpty(
                      icon: Icons.sync_problem_rounded,
                      title: 'Could not refresh',
                      message: error,
                      actionLabel: 'Try again',
                      onAction: _refresh,
                    ),
                  if (attention.isNotEmpty) ...[
                    const SectionLabel('Needs attention'),
                    for (final item in attention)
                      ListTile(
                        key: ValueKey(
                          'mission-attention-${item.sessionID}-${item.title}',
                        ),
                        leading: Icon(
                          item.icon,
                          color: Theme.of(context).colorScheme.error,
                        ),
                        title: Text(
                          item.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          item.detail,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: const Icon(Icons.chevron_right_rounded),
                        onTap: () => _openChat(item.sessionID),
                      ),
                  ],
                  const SectionLabel('Running'),
                  if (running.isEmpty)
                    const ProductInlineEmpty(
                      icon: Icons.bolt_outlined,
                      title: 'Nothing running',
                      message:
                          'Busy sessions appear here the moment a run starts.',
                    )
                  else
                    for (final session in running)
                      _SessionRow(
                        key: ValueKey('mission-running-${session.id}'),
                        session: session,
                        running: true,
                        subagents: _subagentCount(session.id),
                        detail: _place(session),
                        onTap: () => _openChat(session.id),
                      ),
                  if (recent.isNotEmpty) ...[
                    const SectionLabel('Recent'),
                    for (final session in recent)
                      _SessionRow(
                        key: ValueKey('mission-recent-${session.id}'),
                        session: session,
                        running: false,
                        subagents: _subagentCount(session.id),
                        detail: [
                          _relative(
                            session.time?.updated ?? session.time?.created,
                          ),
                          _place(session),
                        ].where((part) => part.isNotEmpty).join(' · '),
                        onTap: () => _openChat(session.id),
                      ),
                  ],
                  const Divider(height: 24),
                  ListTile(
                    key: const ValueKey('mission-all-sessions'),
                    leading: const Icon(Icons.travel_explore_rounded),
                    title: const Text('All sessions, every project'),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: _openFinder,
                  ),
                ],
              ),
      ),
    );
  }

  void _openFinder() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => GlobalSessionsScreen(controller: widget.controller),
      ),
    );
  }
}

class _AttentionItem {
  final String sessionID;
  final IconData icon;
  final String title;
  final String detail;

  const _AttentionItem({
    required this.sessionID,
    required this.icon,
    required this.title,
    required this.detail,
  });
}

class _SessionRow extends StatelessWidget {
  final Session session;
  final bool running;
  final int subagents;
  final String detail;
  final VoidCallback onTap;

  const _SessionRow({
    super.key,
    required this.session,
    required this.running,
    required this.subagents,
    required this.detail,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final title = session.title?.trim().isNotEmpty == true
        ? session.title!.trim()
        : 'Untitled session';
    return ListTile(
      leading: running
          ? SizedBox.square(
              dimension: 20,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: theme.colorScheme.primary,
              ),
            )
          : Icon(
              Icons.chat_bubble_outline_rounded,
              color: theme.colorScheme.onSurfaceVariant,
            ),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: detail.isEmpty
          ? null
          : Text(detail, maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (subagents > 0)
            Tooltip(
              message: '$subagents subagent${subagents == 1 ? '' : 's'}',
              child: Badge(
                label: Text('$subagents'),
                child: const Icon(Icons.account_tree_outlined, size: 19),
              ),
            ),
          const SizedBox(width: 4),
          const Icon(Icons.chevron_right_rounded),
        ],
      ),
      onTap: onTap,
    );
  }
}

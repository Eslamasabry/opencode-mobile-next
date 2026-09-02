part of '../chat_screen.dart';

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
      Navigator.of(context).pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
    } catch (e) {
      if (!context.mounted) return;
      showProductError(context, e);
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
                    color: AppTheme.mutedOf(Theme.of(context)),
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
                      color: AppTheme.mutedOf(Theme.of(context)),
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
                child: DesktopScrollbarArea(
                  builder: (scrollController) => ListView.builder(
                    controller: scrollController,
                    // The extended FAB floats over the list's tail; this keeps
                    // the last row tappable above it.
                    padding: const EdgeInsets.only(bottom: 88),
                    itemCount: sessions.length,
                    itemBuilder: (context, i) {
                      final s = sessions[i];
                      final busy = controller.busySessions.contains(s.id);
                      final retrying = controller.retryStates.containsKey(s.id);
                      final needsAttention = _sessionNeedsAttention(
                        controller,
                        s.id,
                      );
                      return EntranceReveal(
                        index: i,
                        child: ContextMenuRegion(
                          actions: () => [
                            ContextMenuAction(
                              menuKey: const ValueKey('session-menu-open'),
                              label: 'Open',
                              icon: Icons.open_in_new_rounded,
                              onSelected: () => Navigator.of(
                                context,
                              ).pushNamed('/chat/${s.id}'),
                            ),
                            ContextMenuAction(
                              menuKey: const ValueKey('session-menu-rename'),
                              label: 'Rename',
                              icon: Icons.edit_outlined,
                              onSelected: () => unawaited(
                                _sessionAction(context, 'rename', s),
                              ),
                            ),
                            ContextMenuAction(
                              menuKey: const ValueKey('session-menu-delete'),
                              label: 'Delete',
                              icon: Icons.delete_outline_rounded,
                              destructive: true,
                              onSelected: () => unawaited(
                                _sessionAction(context, 'delete', s),
                              ),
                            ),
                          ],
                          child: Dismissible(
                            key: ValueKey('session-dismiss-${s.id}'),
                            direction: DismissDirection.endToStart,
                            // The existing confirm-and-delete flow runs inside
                            // confirmDismiss and always resolves false: the row is
                            // removed by the refreshed session list, never by the
                            // Dismissible itself, so a failed delete snaps back.
                            confirmDismiss: (_) async {
                              await _sessionAction(context, 'delete', s);
                              return false;
                            },
                            background: const SwipeDeleteBackground(),
                            child: ListTile(
                              leading: needsAttention
                                  ? Icon(
                                      key: Key(
                                        'session-attention-icon-${s.id}',
                                      ),
                                      Icons.notification_important_outlined,
                                      size: 20,
                                      color: AppTheme.statusColor(
                                        Theme.of(context),
                                        AppStatusTone.attention,
                                      ),
                                    )
                                  : retrying
                                  ? Icon(
                                      key: Key('session-retrying-icon-${s.id}'),
                                      AppIcons.retry,
                                      size: 20,
                                      color: AppTheme.statusColor(
                                        Theme.of(context),
                                        AppStatusTone.attention,
                                      ),
                                    )
                                  : busy
                                  ? SizedBox(
                                      width: 18,
                                      height: 18,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2,
                                        color: Theme.of(
                                          context,
                                        ).colorScheme.primary,
                                      ),
                                    )
                                  : Icon(
                                      Icons.chat_bubble_outline_rounded,
                                      size: 20,
                                      color: AppTheme.mutedOf(
                                        Theme.of(context),
                                      ),
                                    ),
                              title: Text(
                                presentedSessionTitle(s, fallback: 'New chat'),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              subtitle: _SessionRowMeta(
                                session: s,
                                retrying: retrying,
                                needsAttention: needsAttention,
                                time: _fmtSessionTime(
                                  s.time?.updated ?? s.time?.created ?? 0,
                                ),
                              ),
                              trailing: PopupMenuButton<String>(
                                onSelected: (v) =>
                                    _sessionAction(context, v, s),
                                itemBuilder: (_) => const [
                                  PopupMenuItem(
                                    value: 'rename',
                                    child: Text('Rename'),
                                  ),
                                  PopupMenuItem(
                                    value: 'delete',
                                    child: Text('Delete'),
                                  ),
                                ],
                              ),
                              onTap: () => Navigator.of(
                                context,
                              ).pushNamed('/chat/${s.id}'),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
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

  Future<bool> _confirmDelete(
    BuildContext context,
    Session session,
  ) => showConfirmSheet(
    context,
    icon: Icons.delete_outline_rounded,
    title: 'Delete chat?',
    message:
        '“${session.title?.isNotEmpty == true ? session.title : 'Untitled chat'}” and its history will be permanently removed.',
    confirmLabel: 'Delete',
    destructive: true,
  );

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
      showProductError(context, error);
    }
  }
}

/// The row's second line: the timestamp, then compact usage chips when the
/// server reported any — a retry/compaction state first, then cost and the
/// aggregate diff. Past the stacked-actions text scale the usage chips hide
/// (the state labels stay as plain text) so a row never grows past three
/// lines.
/// True when the controller holds a permission, question, or form waiting
/// on [sessionID]: the row then says "Needs you" instead of "Working".
bool _sessionNeedsAttention(
  ConnectionController controller,
  String sessionID,
) =>
    controller.permissionsForSession(sessionID).isNotEmpty ||
    controller.questionForSession(sessionID) != null ||
    controller.formForSession(sessionID) != null;

class _SessionRowMeta extends StatelessWidget {
  const _SessionRowMeta({
    required this.session,
    required this.retrying,
    this.needsAttention = false,
    required this.time,
  });

  final Session session;
  final bool retrying;

  /// A permission, question, or form is waiting on this session.
  final bool needsAttention;
  final String time;

  static String costLabel(double cost) => '\$${cost.toStringAsFixed(2)}';

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final stacked = AppTheme.stackedActions(context);
    final compacting = session.compactingSince != null;
    final cost = session.cost;
    final summary = session.summary;
    final hasSummary =
        summary != null &&
        (summary.additions > 0 || summary.deletions > 0 || summary.files > 0);
    final chips = <Widget>[
      if (needsAttention)
        _SessionChip(
          key: Key('session-needs-you-${session.id}'),
          label: 'Needs you',
          color: AppTheme.statusColor(theme, AppStatusTone.attention),
        ),
      if (retrying)
        _SessionChip(
          key: Key('session-retrying-${session.id}'),
          label: 'Retrying',
          color: AppTheme.statusColor(theme, AppStatusTone.attention),
        ),
      if (compacting)
        _SessionChip(
          key: Key('session-compacting-${session.id}'),
          label: 'Compacting…',
          color: AppTheme.statusColor(theme, AppStatusTone.progress),
          leading: SizedBox.square(
            dimension: 10,
            child: CircularProgressIndicator(
              strokeWidth: 1.5,
              color: AppTheme.statusColor(theme, AppStatusTone.progress),
            ),
          ),
        ),
      if (!stacked && cost != null && cost > 0)
        _SessionChip(
          key: Key('session-cost-${session.id}'),
          label: costLabel(cost),
        ),
      if (!stacked && hasSummary)
        _SessionChip(
          key: Key('session-diff-${session.id}'),
          span: TextSpan(
            children: [
              TextSpan(
                text: '+${summary.additions}',
                style: TextStyle(color: AppTheme.successOf(theme)),
              ),
              const TextSpan(text: ' '),
              TextSpan(
                text: '−${summary.deletions}',
                style: TextStyle(color: theme.colorScheme.error),
              ),
              TextSpan(
                text:
                    ' · ${summary.files} ${summary.files == 1 ? 'file' : 'files'}',
              ),
            ],
          ),
        ),
    ];
    if (chips.isEmpty) return Text(time);
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(time),
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Wrap(spacing: 6, runSpacing: 4, children: chips),
        ),
      ],
    );
  }
}

class _SessionChip extends StatelessWidget {
  const _SessionChip({
    super.key,
    this.label,
    this.span,
    this.color,
    this.leading,
  }) : assert(label != null || span != null);

  final String? label;
  final TextSpan? span;
  final Color? color;
  final Widget? leading;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final style = theme.textTheme.labelSmall?.copyWith(
      color: color ?? AppTheme.mutedOf(theme),
      fontFeatures: const [FontFeature.tabularFigures()],
    );
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(6),
        border: Border.all(
          color: color?.withValues(alpha: .5) ?? AppTheme.hairline(theme),
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (leading case final leading?) ...[
            leading,
            const SizedBox(width: 5),
          ],
          span == null
              ? Text(label!, style: style)
              : Text.rich(span!, style: style),
        ],
      ),
    );
  }
}

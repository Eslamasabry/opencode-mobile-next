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
                child: ListView.builder(
                  itemCount: sessions.length,
                  itemBuilder: (context, i) {
                    final s = sessions[i];
                    final busy = controller.busySessions.contains(s.id);
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
                            onSelected: () =>
                                unawaited(_sessionAction(context, 'rename', s)),
                          ),
                          ContextMenuAction(
                            menuKey: const ValueKey('session-menu-delete'),
                            label: 'Delete',
                            icon: Icons.delete_outline_rounded,
                            destructive: true,
                            onSelected: () =>
                                unawaited(_sessionAction(context, 'delete', s)),
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
                            leading: busy
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
                                    color: AppTheme.mutedOf(Theme.of(context)),
                                  ),
                            title: Text(
                              s.title?.isNotEmpty == true
                                  ? s.title!
                                  : 'New chat',
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

part of '../chat_screen.dart';

class _TimelineSelection {
  const _TimelineSelection({required this.message, required this.fork});

  final MessageWithParts message;
  final bool fork;
}

/// Bottom sheet listing the session's view destinations plus the two
/// transcript display toggles. Every row pops with its action value; the
/// caller runs the handler, matching the popup menu this replaces.
class _SessionViewsSheet extends StatelessWidget {
  const _SessionViewsSheet({
    required this.reasoningExpanded,
    required this.timestampsVisible,
    this.todosAvailable = true,
  });

  final bool reasoningExpanded;
  final bool timestampsVisible;

  /// §7 row 13: menus list possible actions only, so the Todos destination
  /// leaves the sheet on a server that cannot serve it.
  final bool todosAvailable;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SectionLabel('Views'),
            _SessionSheetRow(
              icon: Icons.view_timeline_outlined,
              label: 'Timeline',
              value: 'timeline',
            ),
            _SessionSheetRow(
              icon: Icons.donut_large_outlined,
              label: 'Context usage',
              value: 'context',
            ),
            _SessionSheetRow(
              icon: Icons.difference_outlined,
              label: 'Changes',
              value: 'changes',
            ),
            if (todosAvailable)
              _SessionSheetRow(
                icon: Icons.checklist_rounded,
                label: 'Todos',
                value: 'todos',
              ),
            _SessionSheetRow(
              icon: Icons.account_tree_outlined,
              label: 'Subagent sessions',
              value: 'subagents',
            ),
            const SectionLabel('Transcript'),
            SwitchListTile(
              key: const ValueKey('session-view-thinking'),
              secondary: const Icon(Icons.psychology_alt_outlined),
              title: Text(
                reasoningExpanded ? 'Collapse reasoning' : 'Expand reasoning',
              ),
              value: reasoningExpanded,
              onChanged: (_) => Navigator.pop(context, 'thinking'),
            ),
            SwitchListTile(
              key: const ValueKey('session-view-timestamps'),
              secondary: const Icon(Icons.schedule_rounded),
              title: Text(
                timestampsVisible ? 'Hide timestamps' : 'Show timestamps',
              ),
              value: timestampsVisible,
              onChanged: (_) => Navigator.pop(context, 'timestamps'),
            ),
          ],
        ),
      ),
    );
  }
}

/// Bottom sheet grouping the session's mutation and utility actions.
class _SessionActionsSheet extends StatelessWidget {
  const _SessionActionsSheet({
    required this.reverted,
    required this.shared,
    this.sharingAvailable = true,
  });

  final bool reverted;
  final bool shared;

  /// §7 rows 10–11: the whole Sharing group goes when the server has no
  /// share/unshare endpoints.
  final bool sharingAvailable;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SectionLabel('Prompt'),
            _SessionSheetRow(
              icon: Icons.replay_rounded,
              label: 'Retry last prompt',
              value: 'retry',
            ),
            _SessionSheetRow(
              icon: reverted
                  ? Icons.settings_backup_restore_rounded
                  : Icons.history_rounded,
              label: reverted ? 'Restore messages' : 'Revert last prompt',
              value: reverted ? 'restore' : 'revert',
            ),
            _SessionSheetRow(
              icon: Icons.fork_right_rounded,
              label: 'Fork session',
              value: 'fork',
            ),
            const SectionLabel('Context'),
            _SessionSheetRow(
              icon: Icons.compress_rounded,
              label: 'Compact context',
              value: 'compact',
            ),
            if (sharingAvailable) ...[
              const SectionLabel('Sharing'),
              _SessionSheetRow(
                icon: shared ? Icons.public_off_rounded : Icons.public_rounded,
                label: shared ? 'Stop sharing' : 'Share session',
                value: shared ? 'unshare' : 'share',
              ),
            ],
            const SectionLabel('Advanced'),
            _SessionSheetRow(
              icon: Icons.terminal_rounded,
              label: 'Run shell command',
              value: 'shell',
            ),
            _SessionSheetRow(
              icon: AppIcons.run,
              label: 'Commands',
              value: 'slash',
            ),
            _SessionSheetRow(
              icon: Icons.refresh_rounded,
              label: 'Reload messages',
              value: 'reload',
            ),
          ],
        ),
      ),
    );
  }
}

class _SessionSheetRow extends StatelessWidget {
  const _SessionSheetRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => ListTile(
    leading: Icon(icon),
    title: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
    onTap: () => Navigator.pop(context, value),
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
                        style: TextStyle(color: AppTheme.mutedOf(theme)),
                      ),
                    )
                  : ListView.separated(
                      controller: scrollController,
                      // Dragging the results dismisses the search keyboard so
                      // it stops covering the list.
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
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

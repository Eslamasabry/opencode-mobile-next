part of '../chat_screen.dart';

enum _ChatCommandAction {
  newSession,
  sessions,
  workspaces,
  move,
  warp,
  files,
  projectHealth,
  promptEditor,
  terminal,
  model,
  integrations,
  organization,
  skills,
  tools,
  references,
  status,
  diagnostics,
  appearance,
  diff,
  context,
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

enum _ComposerToolTab { commands, agents }

class _CommandLauncherSheet extends StatefulWidget {
  const _CommandLauncherSheet({
    required this.controller,
    required this.initialTab,
    required this.commands,
    required this.agents,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onSelected,
    required this.onAgentSelected,
  });

  final ConnectionController controller;
  final _ComposerToolTab initialTab;
  final List<_ChatCommand> Function() commands;
  final List<CatalogAgent> Function() agents;
  final bool Function() loading;
  final Object? Function() error;
  final Future<void> Function() onRefresh;
  final ValueChanged<_ChatCommand> onSelected;
  final ValueChanged<CatalogAgent> onAgentSelected;

  @override
  State<_CommandLauncherSheet> createState() => _CommandLauncherSheetState();
}

class _CommandLauncherSheetState extends State<_CommandLauncherSheet>
    with SingleTickerProviderStateMixin {
  final _search = TextEditingController();
  late final TabController _tabs;

  @override
  void initState() {
    super.initState();
    _tabs = TabController(
      length: _ComposerToolTab.values.length,
      vsync: this,
      initialIndex: widget.initialTab.index,
    )..addListener(_onTabChanged);
    if (widget.loading()) _refresh();
  }

  void _onTabChanged() {
    if (_tabs.indexIsChanging) return;
    _search.clear();
    setState(() {});
  }

  Future<void> _refresh() async {
    if (_tabs.index == _ComposerToolTab.commands.index) {
      await widget.onRefresh();
    } else {
      await widget.controller.refreshCatalog();
    }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) => _buildSheet(context),
  );

  Widget _buildSheet(BuildContext context) {
    final theme = Theme.of(context);
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    final agentTab = _tabs.index == _ComposerToolTab.agents.index;
    final commands = widget
        .commands()
        .where((command) => command.matchesQuery(_search.text))
        .toList();
    if (_search.text.trim().isNotEmpty) {
      commands.sort((a, b) {
        final score = a
            .scoreFor(_search.text)
            .compareTo(b.scoreFor(_search.text));
        return score != 0 ? score : a.slash.compareTo(b.slash);
      });
    }
    final groups = <String, List<_ChatCommand>>{};
    for (final command in commands) {
      groups.putIfAbsent(command.group, () => []).add(command);
    }
    final query = _search.text.trim().toLowerCase().replaceFirst('@', '');
    final agents = widget.agents().where((agent) {
      return query.isEmpty ||
          agent.id.toLowerCase().contains(query) ||
          (agent.description?.toLowerCase().contains(query) ?? false);
    }).toList()..sort((a, b) => a.id.compareTo(b.id));
    return DraggableScrollableSheet(
      expand: false,
      minChildSize: .58,
      initialChildSize: largeText ? .96 : .86,
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
                          'Composer tools',
                          style: theme.textTheme.titleLarge,
                        ),
                        if (!largeText) ...[
                          const SizedBox(height: 2),
                          Text(
                            agentTab
                                ? 'Delegate this prompt to a server subagent'
                                : 'Mobile actions and commands from this server',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  IconButton(
                    tooltip: 'Close composer tools',
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close_rounded),
                  ),
                ],
              ),
            ),
            TabBar(
              controller: _tabs,
              tabs: largeText
                  ? const [
                      Tab(
                        key: Key('composer-tools-commands-tab'),
                        text: 'Commands',
                      ),
                      Tab(
                        key: Key('composer-tools-agents-tab'),
                        text: 'Delegate',
                      ),
                    ]
                  : const [
                      Tab(
                        key: Key('composer-tools-commands-tab'),
                        icon: Icon(Icons.electric_bolt_outlined),
                        text: 'Commands',
                      ),
                      Tab(
                        key: Key('composer-tools-agents-tab'),
                        icon: Icon(Icons.smart_toy_outlined),
                        text: 'Delegate',
                      ),
                    ],
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              child: TextField(
                key: const Key('command-launcher-search'),
                controller: _search,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  hintText: agentTab
                      ? 'Find a subagent'
                      : 'Find a command or action',
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
            if (agentTab && widget.controller.catalogLoading)
              const LinearProgressIndicator(minHeight: 2),
            if (!agentTab && widget.loading())
              const LinearProgressIndicator(minHeight: 2),
            if (!agentTab && widget.error() != null)
              ListTile(
                dense: true,
                title: const Text(
                  'Server commands could not be refreshed',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
                subtitle: Text(
                  productErrorText(widget.error()!),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: IconButton(
                  tooltip: 'Retry server commands',
                  onPressed: _refresh,
                  icon: const Icon(Icons.refresh_rounded),
                ),
              ),
            Expanded(
              child: agentTab
                  ? _AgentPickerList(
                      agents: agents,
                      loading: widget.controller.catalogLoading,
                      error: widget.controller.catalogError,
                      scrollController: scrollController,
                      onRefresh: _refresh,
                      onSelected: widget.onAgentSelected,
                    )
                  : commands.isEmpty
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
                      // Dragging the results dismisses the search keyboard so
                      // it stops covering the list.
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
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
    _tabs
      ..removeListener(_onTabChanged)
      ..dispose();
    _search.dispose();
    super.dispose();
  }
}

class _AgentPickerList extends StatelessWidget {
  const _AgentPickerList({
    required this.agents,
    required this.loading,
    required this.error,
    required this.scrollController,
    required this.onRefresh,
    required this.onSelected,
  });

  final List<CatalogAgent> agents;
  final bool loading;
  final Object? error;
  final ScrollController scrollController;
  final Future<void> Function() onRefresh;
  final ValueChanged<CatalogAgent> onSelected;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (agents.isEmpty) {
      return RefreshIndicator(
        onRefresh: onRefresh,
        child: ListView(
          controller: scrollController,
          physics: const AlwaysScrollableScrollPhysics(),
          keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
          children: [
            SizedBox(height: MediaQuery.sizeOf(context).height * .12),
            Icon(
              loading ? Icons.sync_rounded : Icons.smart_toy_outlined,
              size: 36,
              color: theme.colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(
              loading
                  ? 'Loading subagents…'
                  : error == null
                  ? 'No subagents available from this server'
                  : 'Subagents could not be loaded',
              textAlign: TextAlign.center,
              style: theme.textTheme.bodyMedium?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            if (!loading)
              Center(
                child: TextButton.icon(
                  onPressed: onRefresh,
                  icon: const Icon(Icons.refresh_rounded),
                  label: const Text('Refresh'),
                ),
              ),
          ],
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView.separated(
        key: const Key('composer-agent-list'),
        controller: scrollController,
        physics: const AlwaysScrollableScrollPhysics(),
        keyboardDismissBehavior: ScrollViewKeyboardDismissBehavior.onDrag,
        padding: const EdgeInsets.only(top: 6, bottom: 24),
        itemCount: agents.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final agent = agents[index];
          return ListTile(
            key: Key('composer-agent-${agent.id}'),
            minTileHeight: 64,
            leading: Icon(
              Icons.smart_toy_outlined,
              color: theme.colorScheme.primary,
            ),
            title: Text(
              '@${agent.id}',
              style: theme.textTheme.bodyLarge?.copyWith(
                fontFamily: 'AppMono',
                fontWeight: FontWeight.w600,
              ),
            ),
            subtitle: Text(
              agent.description ?? 'Delegate this prompt',
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: const Icon(Icons.add_rounded),
            onTap: () => onSelected(agent),
          );
        },
      ),
    );
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
              fontFamily: 'AppMono',
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
                  fontFamily: 'AppMono',
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
            // 44dp floor: these rows sit directly under the thumbs while
            // typing, where ~30dp rows invite mis-taps.
            child: Container(
              constraints: const BoxConstraints(minHeight: 44),
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
                        fontFamily: 'AppMono',
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

class _InlineAgentSuggestions extends StatelessWidget {
  const _InlineAgentSuggestions({
    required this.agents,
    required this.query,
    required this.compact,
    required this.onSelected,
    required this.onShowAll,
  });

  final List<CatalogAgent> agents;
  final String query;
  final bool compact;
  final ValueChanged<CatalogAgent> onSelected;
  final VoidCallback onShowAll;

  @override
  Widget build(BuildContext context) {
    final normalized = query.toLowerCase();
    final matches = agents.where((agent) {
      return normalized.isEmpty ||
          agent.id.toLowerCase().contains(normalized) ||
          (agent.description?.toLowerCase().contains(normalized) ?? false);
    }).toList();
    matches.sort((a, b) {
      final aPrefix = a.id.toLowerCase().startsWith(normalized) ? 0 : 1;
      final bPrefix = b.id.toLowerCase().startsWith(normalized) ? 0 : 1;
      final prefix = aPrefix.compareTo(bPrefix);
      return prefix != 0 ? prefix : a.id.compareTo(b.id);
    });
    final limit = compact ? 1 : 5;
    final visible = matches.take(limit).toList();
    if (visible.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    return Column(
      key: const Key('inline-agent-suggestions'),
      mainAxisSize: MainAxisSize.min,
      children: [
        for (final agent in visible)
          InkWell(
            key: Key('inline-agent-${agent.id}'),
            onTap: () => onSelected(agent),
            // 44dp floor: these rows sit directly under the thumbs while
            // typing, where ~30dp rows invite mis-taps.
            child: Container(
              constraints: const BoxConstraints(minHeight: 44),
              padding: EdgeInsets.fromLTRB(
                14,
                compact ? 6 : 8,
                12,
                compact ? 6 : 8,
              ),
              child: Row(
                children: [
                  Icon(
                    Icons.smart_toy_outlined,
                    size: 18,
                    color: theme.colorScheme.primary,
                  ),
                  const SizedBox(width: 10),
                  SizedBox(
                    width: compact ? 92 : 112,
                    child: Text(
                      '@${agent.id}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: theme.textTheme.bodySmall?.copyWith(
                        fontFamily: 'AppMono',
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Expanded(
                    child: Text(
                      compact
                          ? 'Delegate'
                          : agent.description ?? 'Delegate this prompt',
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
              child: const Text('Show all subagents'),
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

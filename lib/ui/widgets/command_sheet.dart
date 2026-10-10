import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/server_gateway.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../../state/profiles.dart' show ServerBackend;
import '../app_iconography.dart';
import '../app_theme.dart' show AppIcons, AppStatusTone;
import '../kit/kit.dart';
import '../search/search_index.dart';
import 'product_states.dart' show productErrorText;

AppLocalizations _l10n(BuildContext context) =>
    lookupAppLocalizations(Localizations.localeOf(context));

/// One command in the command sheet (slice-P10.1): an app action the
/// conversation can run, or a command the connected server lists. It reads
/// by its name first, what it does under it, and its slash word as the
/// hint for typing it.
@immutable
class CommandSheetEntry {
  const CommandSheetEntry._({
    required this.slash,
    required this.aliases,
    required this.title,
    required this.description,
    required this.group,
    required this.enabled,
    required this.listed,
    this.action,
    this.serverCommand,
  });

  /// One of the app's own actions; [action] is the caller's own value.
  factory CommandSheetEntry.app({
    required String slash,
    List<String> aliases = const [],
    required String title,
    required String description,
    required String group,
    required Object action,
    bool enabled = true,
    bool listed = true,
  }) => CommandSheetEntry._(
    slash: slash,
    aliases: aliases,
    title: title,
    description: description,
    group: group,
    enabled: enabled,
    listed: listed,
    action: action,
  );

  /// A command the server lists. Plain words lead: its description is the
  /// title when it has one, and `/name` follows as the typing hint.
  /// [runsWith] names the agent a command runs with ("Runs with plan").
  factory CommandSheetEntry.server(
    CommandInfo command, {
    required String group,
    required String fallbackDescription,
    String Function(String agent)? runsWith,
  }) {
    final description = command.description?.trim() ?? '';
    final firstLine = description.split('\n').first.trim();
    final agent = command.agent?.trim() ?? '';
    return CommandSheetEntry._(
      slash: command.name,
      aliases: const [],
      title: firstLine.isEmpty ? '/${command.name}' : firstLine,
      description: agent.isNotEmpty
          ? (runsWith?.call(agent) ?? agent)
          : firstLine.isEmpty
          ? fallbackDescription
          : '',
      group: group,
      enabled: true,
      listed: true,
      serverCommand: command,
    );
  }

  final String slash;
  final List<String> aliases;
  final String title;
  final String description;
  final String group;
  final bool enabled;

  /// False: the command still runs when typed ("/fork") but the sheet does
  /// not list it, because the conversation menu is its one home (P10.2).
  final bool listed;
  final Object? action;
  final CommandInfo? serverCommand;

  bool get fromServer => serverCommand != null;

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

/// The name of the agent the connected server runs, for copy only ("Codex
/// commands aren't available here"); null for OpenCode, whose server lists
/// its commands.
String? commandSheetAgentName(ConnectionController controller) =>
    switch (controller.profile?.backend) {
      ServerBackend.paseo => 'Claude Code',
      ServerBackend.codex => 'Codex',
      _ => null,
    };

/// The server's commands as sheet entries, sorted by name, under "Commands
/// from `server`".
List<CommandSheetEntry> serverCommandEntries(
  AppLocalizations l10n,
  List<CommandInfo> commands, {
  String? serverName,
}) {
  final name = serverName?.trim();
  final group = name == null || name.isEmpty
      ? l10n.chatUiServerCommands
      : l10n.commandSheetServerGroup(name);
  final sorted = [...commands]..sort((a, b) => a.name.compareTo(b.name));
  return [
    for (final command in sorted)
      CommandSheetEntry.server(
        command,
        group: group,
        fallbackDescription: l10n.chatUiOpenCodeServerCommand,
        runsWith: l10n.commandsScreenRunsWith,
      ),
  ];
}

enum CommandSheetTab { commands, agents }

/// The command sheet (map `command-launcher-sheet`, slice-P10.1): one
/// place for the current backend's commands, in plain words, grouped and
/// searchable. The chat's "/" (and Ctrl+K) opens it over the conversation
/// and runs the pick there; Settings › Tools › Commands shows the same
/// sheet ([embedded]) and asks which conversation the pick runs in.
///
/// A server whose agent does not share its commands (Claude Code through
/// Paseo, Codex: [ServerCapabilities.slashCommands] false) says so and
/// names what is missing (listing, running, "!" shell); nothing is offered
/// as if it worked. Where the server takes "@agent" mentions a second tab
/// hands the prompt to a subagent.
///
/// States: loading (the server's list is on its way), empty, error (a
/// failed refresh says so with Retry, the last list stays), no match.
class CommandSheet extends StatefulWidget {
  const CommandSheet({
    super.key,
    required this.controller,
    required this.commands,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onSelected,
    this.loaded,
    this.unavailable,
    this.agents,
    this.onAgentSelected,
    this.initialTab = CommandSheetTab.commands,
    this.subtitle,
    this.searchElsewhere = true,
    this.embedded = false,
    this.refreshOnOpen = true,
  });

  final ConnectionController controller;

  /// Every entry; the sheet lists those with [CommandSheetEntry.listed].
  final List<CommandSheetEntry> Function() commands;
  final bool Function() loading;
  final Object? Function() error;
  final Future<void> Function() onRefresh;
  final ValueChanged<CommandSheetEntry> onSelected;

  /// Whether the server's list was read at least once: a failure then is
  /// "Couldn't refresh" over the last list, else "Couldn't load commands".
  final bool Function()? loaded;

  /// The app's actions this server cannot run, each with the capability
  /// that explains why (P7.4).
  final List<(CommandSheetEntry, String)> Function()? unavailable;

  /// The subagents a prompt can be handed to; null: no agent tab.
  final List<CatalogAgent> Function()? agents;
  final ValueChanged<CatalogAgent>? onAgentSelected;
  final CommandSheetTab initialTab;

  /// The line under the title; default "Run an action in this
  /// conversation, or a command from this server".
  final String? subtitle;

  /// Typing also searches the rest of the app (settings, tools, tabs).
  final bool searchElsewhere;

  /// Settings › Tools › Commands: the same sheet inside its tab, without
  /// the modal frame.
  final bool embedded;

  /// A list still [loading] when the sheet opens is asked for once more
  /// (the chat's read coalesces); false where the owner already reads it.
  final bool refreshOnOpen;

  @override
  State<CommandSheet> createState() => _CommandSheetState();
}

class _CommandSheetState extends State<CommandSheet> {
  final _search = TextEditingController();
  late CommandSheetTab _tab = widget.initialTab;

  bool get _agentsOffered =>
      widget.agents != null &&
      widget.controller.capabilities.promptAgentMentions;

  @override
  void initState() {
    super.initState();
    // Filtering follows every keystroke: the list is local.
    _search.addListener(_onQuery);
    // A list still on its way is asked for once more after the first
    // frame (the owner coalesces), never while its page is building.
    if (widget.refreshOnOpen && widget.loading()) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) unawaited(_refresh());
      });
    }
  }

  void _onQuery() {
    if (mounted) setState(() {});
  }

  void _switchTab(CommandSheetTab tab) {
    if (tab == _tab) return;
    _search.clear();
    setState(() => _tab = tab);
  }

  Future<void> _refresh() async {
    if (_tab == CommandSheetTab.commands) {
      await widget.onRefresh();
    } else {
      await widget.controller.refreshCatalog();
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _search
      ..removeListener(_onQuery)
      ..dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) => _buildSheet(context),
  );

  /// Enter in the search runs the best match, so a PC keyboard can pick a
  /// command without leaving the field.
  void _runFirst() {
    if (_tab == CommandSheetTab.agents && _agentsOffered) {
      final agents = _matchingAgents(_search.text);
      if (agents.isNotEmpty) widget.onAgentSelected?.call(agents.first);
      return;
    }
    final commands = _matchingCommands(
      _search.text,
    ).where((entry) => entry.enabled);
    if (commands.isNotEmpty) widget.onSelected(commands.first);
  }

  Widget _buildSheet(BuildContext context) {
    final l10n = _l10n(context);
    final tokens = KitTokens.of(context);
    final agentTab = _agentsOffered && _tab == CommandSheetTab.agents;
    final query = _search.text;
    final Widget list;
    final int resultCount;
    if (agentTab) {
      final agents = _matchingAgents(query);
      resultCount = agents.length;
      list = _AgentPickerList(
        agents: agents,
        query: query,
        loading: widget.controller.catalogLoading,
        error:
            widget.controller.catalogFailure ?? widget.controller.catalogError,
        onRefresh: _refresh,
        onClearSearch: _search.clear,
        onSelected: (agent) => widget.onAgentSelected?.call(agent),
      );
    } else {
      final commands = _matchingCommands(query);
      final scope = SearchScope.of(context, widget.controller);
      final elsewhere = widget.searchElsewhere
          ? searchEntries(l10n, scope, query)
          : const <SearchEntry>[];
      resultCount = commands.length + elsewhere.length;
      list = _CommandList(
        commands: commands,
        unavailable: [
          for (final entry in widget.unavailable?.call() ?? const [])
            if (entry.$1.matchesQuery(query)) entry,
        ],
        serverName: widget.controller.profile?.name,
        elsewhere: elsewhere,
        query: query,
        loading: widget.loading(),
        failed: widget.error() != null,
        onClearSearch: _search.clear,
        onSelected: widget.onSelected,
        onOpenElsewhere: (entry) {
          // The sheet's context dies with the sheet; the result opens from
          // the route below it.
          final navigator = Navigator.of(context);
          final below = navigator.overlay?.context;
          if (!widget.embedded) navigator.pop();
          if (below != null) unawaited(entry.open(below, scope));
        },
      );
    }
    final commandsError = agentTab ? null : widget.error();
    final agentName = commandSheetAgentName(widget.controller);
    final missing = !agentTab && !widget.controller.capabilities.slashCommands;
    final content = <Widget>[
      if (_agentsOffered) ...[
        KitSegmented<CommandSheetTab>(
          semanticsLabel: l10n.composerToolCommandsTitle,
          selected: agentTab
              ? CommandSheetTab.agents
              : CommandSheetTab.commands,
          onChanged: _switchTab,
          segments: [
            KitSegment(
              key: const Key('composer-tools-commands-tab'),
              value: CommandSheetTab.commands,
              label: l10n.runResultsCommandsTitle,
              icon: AppIcons.run,
            ),
            KitSegment(
              key: const Key('composer-tools-agents-tab'),
              value: CommandSheetTab.agents,
              label: l10n.chatUiDelegate,
              icon: AppIconography.agent,
            ),
          ],
        ),
        SizedBox(height: tokens.space3),
      ],
      KitSearchField(
        label: agentTab
            ? l10n.chatUiFindASubagent
            : l10n.chatUiFindACommandOrAction,
        controller: _search,
        onChanged: (_) {},
        onSubmitted: (_) => _runFirst(),
        resultCount: query.trim().isEmpty ? null : resultCount,
        fieldKey: const Key('command-launcher-search'),
        clearKey: const Key('command-launcher-search-clear'),
      ),
      SizedBox(height: tokens.space3),
      if (commandsError != null) ...[
        // Plain words for what failed; the technical text under Details.
        KitNotice.error(
          key: const Key('command-launcher-error'),
          // No list yet: it could not be read; otherwise the last list
          // stays under the notice.
          title: widget.loaded?.call() ?? true
              ? l10n.refreshFailed
              : l10n.commandsScreenLoadFailed,
          message: productErrorText(commandsError, l10n: l10n),
          error: commandsError,
          retry: KitAction(
            key: const Key('command-launcher-retry'),
            label: l10n.refreshRetry,
            onPressed: () => unawaited(_refresh()),
          ),
        ),
        SizedBox(height: tokens.space3),
      ],
      // An agent that does not share its commands (Claude Code through
      // Paseo, Codex) says so and names what is missing; what follows is
      // the app's own.
      if (missing) ...[
        KitNotice(
          key: const Key('command-launcher-agent-commands-unavailable'),
          icon: AppIconography.info,
          title: l10n.commandSheetAgentMissingTitle(
            agentName ?? l10n.commandSheetAgentFallback,
          ),
          message: l10n.commandSheetAgentMissingWhy(
            agentName ?? l10n.commandSheetAgentFallback,
          ),
        ),
        SizedBox(height: tokens.space3),
      ],
      list,
    ];
    if (widget.embedded) {
      return ListView(
        key: const Key('command-sheet-embedded'),
        padding: EdgeInsetsDirectional.fromSTEB(
          tokens.gutter,
          tokens.space3,
          tokens.gutter,
          KitScreen.endPadding(context),
        ),
        children: [
          if (widget.subtitle case final subtitle?) ...[
            KitText(
              subtitle,
              role: KitTextRole.secondary,
              tone: KitTextTone.secondary,
            ),
            SizedBox(height: tokens.space3),
          ],
          ...content,
        ],
      );
    }
    final largeText = MediaQuery.textScalerOf(context).scale(1) >= 1.5;
    final height = (MediaQuery.sizeOf(context).height * (largeText ? .96 : .86))
        .floorToDouble();
    return SizedBox(
      key: const Key('command-launcher-sheet'),
      height: height,
      child: KitSheet(
        title: l10n.composerToolCommandsTitle,
        subtitle: agentTab
            ? l10n.chatUiDelegateThisPromptToAServerSubagent
            : widget.subtitle ??
                  (widget.controller.capabilities.slashCommands
                      ? l10n.commandLauncherSubtitle
                      : l10n.commandSheetSubtitleAppOnly),
        fill: true,
        // The modal route draws the one handle (the theme's drag handle).
        handle: false,
        dismissKeyboardOnDrag: true,
        loading: agentTab ? widget.controller.catalogLoading : widget.loading(),
        onClose: () => Navigator.pop(context),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: content,
        ),
      ),
    );
  }

  List<CommandSheetEntry> _matchingCommands(String query) {
    final commands = widget
        .commands()
        .where((command) => command.listed && command.matchesQuery(query))
        .toList();
    if (query.trim().isNotEmpty) {
      commands.sort((a, b) {
        final score = a.scoreFor(query).compareTo(b.scoreFor(query));
        return score != 0 ? score : a.slash.compareTo(b.slash);
      });
    }
    return commands;
  }

  List<CatalogAgent> _matchingAgents(String query) {
    final normalized = query.trim().toLowerCase().replaceFirst('@', '');
    return (widget.agents?.call() ?? const <CatalogAgent>[]).where((agent) {
      return normalized.isEmpty ||
          agent.id.toLowerCase().contains(normalized) ||
          (agent.description?.toLowerCase().contains(normalized) ?? false);
    }).toList()..sort((a, b) => a.id.compareTo(b.id));
  }
}

/// The commands, in their groups (the server's own first), then why the
/// app's other actions are not here (P7.4), then "Go to" places elsewhere
/// in the app; "Nothing matches" with Clear search when a query finds none.
class _CommandList extends StatelessWidget {
  const _CommandList({
    required this.commands,
    this.unavailable = const [],
    this.serverName,
    required this.elsewhere,
    required this.query,
    required this.loading,
    this.failed = false,
    required this.onClearSearch,
    required this.onSelected,
    required this.onOpenElsewhere,
  });

  final List<CommandSheetEntry> commands;
  final List<(CommandSheetEntry, String)> unavailable;
  final String? serverName;
  final List<SearchEntry> elsewhere;
  final String query;
  final bool loading;

  /// The server's list could not be read: the notice above says so, so no
  /// "No commands" state claims there are none.
  final bool failed;
  final VoidCallback onClearSearch;
  final ValueChanged<CommandSheetEntry> onSelected;
  final ValueChanged<SearchEntry> onOpenElsewhere;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    if (commands.isEmpty && unavailable.isEmpty && elsewhere.isEmpty) {
      if (query.trim().isNotEmpty) {
        return KitSearchNoMatch(
          key: const Key('command-launcher-no-match'),
          query: query.trim(),
          onClear: onClearSearch,
        );
      }
      if (loading) {
        return const KitSkeletonRows(key: Key('command-launcher-loading'));
      }
      if (failed) return const SizedBox.shrink();
      return KitStateView(
        key: const Key('command-launcher-empty'),
        icon: AppIcons.run,
        title: l10n.e7LibraryNoServerCommandsFound,
        body: l10n.e7LibraryCommandsFromYourProjectAndSkillsAppear,
        size: KitStateSize.inline,
      );
    }
    // The server's commands lead: they are the backend's own catalogue.
    final groups = <String, List<CommandSheetEntry>>{};
    for (final command in [
      ...commands.where((command) => command.fromServer),
      ...commands.where((command) => !command.fromServer),
    ]) {
      groups.putIfAbsent(command.group, () => []).add(command);
    }
    final missing = <String, List<CommandSheetEntry>>{};
    for (final (command, capability) in unavailable) {
      missing.putIfAbsent(capability, () => []).add(command);
    }
    return Column(
      key: const Key('command-launcher-list'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final group in groups.entries)
          KitRowGroup(
            label: group.key,
            leadingIcons: false,
            margin: EdgeInsetsDirectional.zero,
            children: [
              for (final command in group.value)
                _CommandRow(command: command, onSelected: onSelected),
            ],
          ),
        if (missing.isNotEmpty)
          KitRowGroup(
            key: const Key('command-launcher-unavailable'),
            margin: EdgeInsetsDirectional.zero,
            children: [
              for (final MapEntry(key: capability, value: actions)
                  in missing.entries)
                KitCapabilityExplainer.row(
                  rowKey: ValueKey('command-unavailable-$capability'),
                  capability: capability,
                  title: actions.length == 1 ? actions.single.title : null,
                  serverName: serverName,
                  source: 'command-launcher-sheet',
                ),
            ],
          ),
        if (elsewhere.isNotEmpty)
          KitRowGroup(
            label: l10n.discoverSearchGoTo,
            margin: EdgeInsetsDirectional.zero,
            children: [
              for (final entry in elsewhere)
                KitRow(
                  key: ValueKey('command-launcher-result-${entry.id}'),
                  leading: KitRowIcon(entry.icon),
                  title: entry.title,
                  supporting: entry.parent == null
                      ? null
                      : TextSpan(text: l10n.discoverSearchIn(entry.parent!)),
                  trailing: const KitChevron(),
                  onTap: () => onOpenElsewhere(entry),
                ),
            ],
          ),
      ],
    );
  }
}

/// One command: plain words first, what it does (or the agent it runs
/// with) under them, and its slash word as the hint for typing it. The
/// app's short titles keep the slash word at the end; a server command's
/// title is its sentence-long description, so its slash word goes under it
/// and the words take the row's full width. A server command copies its
/// slash word from the row's menu.
class _CommandRow extends StatelessWidget {
  const _CommandRow({required this.command, required this.onSelected});

  final CommandSheetEntry command;
  final ValueChanged<CommandSheetEntry> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final server = command.fromServer;
    final slash = '/${command.slash}';
    final hint = command.title != slash;
    final description = command.description;
    // The slash word under a server command's words, not in a column beside
    // them.
    final under = server && hint;
    final supporting = under
        ? [
            KitBidi.ltr(slash),
            if (description.isNotEmpty) description,
          ].join(' · ')
        : description;
    return KitRow(
      key: Key('command-${server ? 'server' : 'mobile'}-${command.slash}'),
      title: command.title,
      titleMaxLines: under ? 3 : 2,
      supporting: supporting.isEmpty ? null : TextSpan(text: supporting),
      supportingMaxLines: 2,
      trailing: hint && !under
          ? KitText(
              slash,
              role: KitTextRole.mono,
              tone: KitTextTone.secondary,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            )
          : null,
      enabled: command.enabled,
      onTap: command.enabled ? () => onSelected(command) : null,
      menuLabel: server ? l10n.commandsScreenMenuLabel : null,
      menu: server
          ? [
              KitMenuItem.copy(
                label: l10n.commandsScreenCopy(KitBidi.ltr(slash)),
                text: () => slash,
              ),
            ]
          : const [],
    );
  }
}

/// The subagents a prompt can be handed to: "@name", what it is for and the
/// model it runs, one tap to put the mention in the prompt.
class _AgentPickerList extends StatelessWidget {
  const _AgentPickerList({
    required this.agents,
    required this.query,
    required this.loading,
    required this.error,
    required this.onRefresh,
    required this.onClearSearch,
    required this.onSelected,
  });

  final List<CatalogAgent> agents;
  final String query;
  final bool loading;
  final Object? error;
  final Future<void> Function() onRefresh;
  final VoidCallback onClearSearch;
  final ValueChanged<CatalogAgent> onSelected;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    if (agents.isEmpty) {
      if (query.trim().isNotEmpty && !loading) {
        return KitSearchNoMatch(
          key: const Key('composer-agent-no-match'),
          query: query.trim(),
          onClear: onClearSearch,
        );
      }
      final refresh = KitAction(
        key: const Key('composer-agent-refresh'),
        label: l10n.globalSessionsRefresh,
        icon: AppIconography.retry,
        onPressed: () => unawaited(onRefresh()),
      );
      if (loading) {
        return KitStateView(
          key: const Key('composer-agent-loading'),
          icon: AppIconography.agent,
          title: l10n.chatUiLoadingSubagents,
          tone: AppStatusTone.progress,
          size: KitStateSize.inline,
        );
      }
      if (error != null) {
        return KitStateView.error(
          key: const Key('composer-agent-error'),
          title: l10n.chatUiSubagentsCouldNotBeLoaded,
          error: error,
          retry: refresh,
          size: KitStateSize.inline,
        );
      }
      return KitStateView(
        key: const Key('composer-agent-empty'),
        icon: AppIconography.agent,
        title: l10n.chatUiNoSubagentsAvailableFromThisServer,
        primary: refresh,
        size: KitStateSize.inline,
      );
    }
    return KitRowGroup(
      key: const Key('composer-agent-list'),
      margin: EdgeInsetsDirectional.zero,
      children: [
        for (final agent in agents)
          KitRow(
            key: Key('composer-agent-${agent.id}'),
            leading: const KitRowIcon(AppIconography.agent),
            title: '@${KitBidi.auto(agent.id)}',
            supporting: TextSpan(
              text: [
                agent.description ?? l10n.chatUiDelegateThisPrompt,
                if (agent.model?.trim() case final model? when model.isNotEmpty)
                  KitBidi.auto(model),
              ].join('\n'),
            ),
            supportingMaxLines: 3,
            onTap: () => onSelected(agent),
          ),
      ],
    );
  }
}

part of '../chat_screen.dart';

// The chat's commands: which are supported, the server's commands, the
// shell dialog, and the built-in command list.

mixin _ChatCommandFields {
  List<CommandInfo>? _serverCommands;
  Object? _serverCommandsError;
  bool _serverCommandsLoading = false;
  Future<void>? _serverCommandsRequest;
}

extension _ChatCommands on _ChatScreenState {
  /// The heading of the server's own actions: the agent's name in a
  /// conversation with an agent on this phone.
  String get _commandServerGroup =>
      _conn.isAgentBackend ? (_conn.profile?.name ?? 'OpenCode') : 'OpenCode';

  /// "Reconnecting" naming this conversation's own backend: an agent on
  /// this phone (Claude Code) is not OpenCode.
  String _reconnectingWords(BuildContext context) => _conn.isAgentBackend
      ? _chatL10n(
          context,
        ).chatUiAgentIsReconnecting(KitBidi.auto(_conn.profile?.name ?? ''))
      : _chatL10n(context).chatUiOpenCodeIsReconnecting;

  String _reconnectingShortlyWords(BuildContext context) => _conn.isAgentBackend
      ? _chatL10n(context).chatUiAgentIsReconnectingTryAgainShortly(
          KitBidi.auto(_conn.profile?.name ?? ''),
        )
      : _chatL10n(context).chatUiOpenCodeIsReconnectingTryAgainShortly;

  List<CatalogAgent> get _subagents {
    final agents = (_conn.catalog?.agents ?? const <CatalogAgent>[])
        .where((agent) => !agent.hidden && agent.mode == 'subagent')
        .toList();
    agents.sort((a, b) => a.id.compareTo(b.id));
    return agents;
  }

  bool get _supportsPromptAgentMentions =>
      _conn.capabilities.promptAgentMentions;
  bool get _supportsOfflinePromptQueue => _conn.capabilities.offlinePromptQueue;
  bool get _supportsSessionCompact => _conn.capabilities.sessionCompact;

  bool _chatCommandSupported(_ChatCommand command) => switch (command.action) {
    _ChatCommandAction.shell => _conn.capabilities.terminal,
    _ChatCommandAction.note => _conn.supportsSessionNotes,
    _ChatCommandAction.sessions => _conn.capabilities.globalSessionSearch,
    _ChatCommandAction.workspaces ||
    _ChatCommandAction.move ||
    _ChatCommandAction.warp ||
    _ChatCommandAction.projectHealth => _conn.capabilities.projectManagement,
    _ChatCommandAction.files => _conn.capabilities.fileBrowsing,
    _ChatCommandAction.terminal => _conn.capabilities.terminal,
    _ChatCommandAction.diff => _conn.capabilities.sessionDiff,
    _ChatCommandAction.fork => _conn.capabilities.sessionFork,
    _ChatCommandAction.compact => _supportsSessionCompact,
    _ChatCommandAction.undo ||
    _ChatCommandAction.redo => _conn.capabilities.sessionRevert,
    _ChatCommandAction.references => _conn.capabilities.fileBrowsing,
    _ChatCommandAction.integrations ||
    _ChatCommandAction.mcpServers ||
    _ChatCommandAction.skills => _conn.capabilities.serverCatalog,
    _ChatCommandAction.model => true,
    _ => true,
  };

  /// "Run shell command" (map chat-run-shell-dialog): one command, run by
  /// this conversation's agent in its project. It runs from inside the
  /// dialog, so a failure stays under the field with the command kept.
  /// [initial] prefills it: a shell step's "Run this command again" opens
  /// the dialog with that command, to read or change before it runs.
  Future<void> _runShellDialog({String? initial}) async {
    if (_conn.isIsolated || !_conn.capabilities.terminal) return;
    final strings = _chatL10n(context);
    await showKitInputDialog(
      context,
      title: strings.chatUiRunShellCommand,
      label: strings.chatRunShellLabel,
      hint: strings.chatRunShellHint,
      helper: strings.chatRunShellHelper,
      confirmLabel: strings.commandRun,
      kind: KitFieldKind.mono,
      // A command reads whole: it wraps to up to four lines instead of
      // scrolling its start out of view (review board: run-shell dialog).
      maxLines: 4,
      initial: initial,
      dialogKey: const ValueKey('run-shell-dialog'),
      fieldKey: const ValueKey('run-shell-command'),
      confirmKey: const ValueKey('run-shell-confirm'),
      validate: (value) =>
          value.trim().isEmpty ? strings.chatRunShellEmpty : null,
      onSubmit: (value) async {
        try {
          await _runShellCommand(value.trim());
          return null;
        } catch (error) {
          return productErrorText(error, l10n: strings);
        }
      },
    );
  }

  Future<void> _runShellCommand(String command) async {
    final reconnecting = _reconnectingWords(context);
    final api = await _conn.prepareActionTransport();
    if (api == null) throw ProductException(reconnecting);
    await _conn.waitForSessionSelection(widget.sessionID, expectedApi: api);
    final agent = _conn.agentForSession(widget.sessionID);
    final variant = _conn.variantForSession(widget.sessionID);
    await api.shell(
      widget.sessionID,
      command: command,
      agent: agent.isNotEmpty ? agent : 'build',
      model: _conn.modelForSession(widget.sessionID),
      variant: variant.isEmpty ? null : variant,
    );
  }

  /// A shell step's "Run this command again"; null where this server has
  /// no shell for the conversation.
  ValueChanged<String>? get _rerunShellCommand =>
      _conn.isIsolated || !_conn.capabilities.terminal
      ? null
      : (command) => unawaited(_runShellDialog(initial: command));

  Future<void> _loadServerCommands() {
    if (_conn.isIsolated || !_conn.capabilities.slashCommands) {
      return Future.value();
    }
    final existing = _serverCommandsRequest;
    if (existing != null) return existing;
    late final Future<void> request;
    request = _performLoadServerCommands().whenComplete(() {
      if (identical(_serverCommandsRequest, request)) {
        _serverCommandsRequest = null;
      }
    });
    _serverCommandsRequest = request;
    return request;
  }

  Future<void> _performLoadServerCommands() async {
    _setChatState(() {
      _serverCommandsLoading = true;
      _serverCommandsError = null;
    });
    try {
      final repository = await _conn.prepareActionRepository();
      if (!mounted) return;
      if (repository == null) {
        // Looked up after the first await: this runs from initState, where
        // inherited localizations are not yet available.
        throw ProductException(
          _chatL10n(context).chatUiOpenCodeCommandsAreUnavailableOffline,
        );
      }
      final commands = [...await repository.listCommands()];
      if (!mounted) return;
      commands.sort((a, b) => a.name.compareTo(b.name));
      _setChatState(() => _serverCommands = commands);
    } catch (error) {
      if (mounted) _setChatState(() => _serverCommandsError = error);
    } finally {
      if (mounted) _setChatState(() => _serverCommandsLoading = false);
    }
  }

  /// The app's actions this server can run, then its own commands (only
  /// where the server lists them: [ServerCapabilities.slashCommands]).
  List<_ChatCommand> get _chatCommands {
    final supported = _builtinChatCommands
        .where(_chatCommandSupported)
        .toList();
    if (!_conn.capabilities.slashCommands) return supported;
    final dynamic = serverCommandEntries(
      _chatL10n(context),
      _serverCommands ?? const <CommandInfo>[],
      serverName: _conn.profile?.name,
    );
    return [...supported, ...dynamic];
  }

  /// The app's actions this server cannot run, each with the capability
  /// that says why (P7.4, explain instead of vanish): the launcher explains
  /// them in place of the rows. Only gates the capability registry can
  /// explain; the others (moving a conversation, forking, sharing) stay out
  /// of the list as before.
  List<(_ChatCommand, String)> get _unavailableChatCommands => [
    for (final command in _builtinChatCommands)
      if (!_chatCommandSupported(command))
        if (_missingCapability(command.action) case final capability?)
          (command, capability),
  ];

  static String? _missingCapability(Object? action) => switch (action) {
    _ChatCommandAction.files ||
    _ChatCommandAction.terminal ||
    _ChatCommandAction.references ||
    _ChatCommandAction.workspaces ||
    _ChatCommandAction.projectHealth => 'flag:fileBrowsing+terminal',
    _ChatCommandAction.diff => 'flag:sessionDiff',
    _ChatCommandAction.integrations ||
    _ChatCommandAction.mcpServers ||
    _ChatCommandAction.skills => 'flag:serverCatalog',
    _ => null,
  };

  List<_ChatCommand> get _builtinChatCommands {
    final session = _conn.sessionsById[widget.sessionID];
    final hasUserMessage = _visibleHistory.any(
      (message) =>
          message.info.role == 'user' && !message.info.id.startsWith('local-'),
    );
    return <_ChatCommand>[
      CommandSheetEntry.app(
        slash: 'new',
        aliases: const ['clear'],
        title: _chatL10n(context).workspaceNewSession,
        description: _chatL10n(context).chatUiStartACleanSessionInThisWorkspace,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.newSession,
      ),
      CommandSheetEntry.app(
        slash: 'sessions',
        aliases: const ['resume', 'continue'],
        title: _chatL10n(context).usageSessions,
        description: _chatL10n(
          context,
        ).chatUiFindSessionsAcrossEveryOpenCodeProject,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.sessions,
      ),
      CommandSheetEntry.app(
        slash: 'workspaces',
        aliases: const ['workspace'],
        title: _chatL10n(context).chatUiProjectsAndWorkspaces,
        description: _chatL10n(context).chatUiSwitchProjectDirectoryOrWorktree,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.workspaces,
      ),
      CommandSheetEntry.app(
        slash: 'move',
        title: _chatL10n(context).chatUiMoveSession,
        description: _chatL10n(
          context,
        ).chatUiMoveThisSessionToAnotherProjectDirectory,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.move,
      ),
      // §7 row 5: warping a session into a managed workspace has no v2
      // equivalent, so the command leaves the palette rather than failing.
      if (_conn.capabilities.workspaceWarp)
        CommandSheetEntry.app(
          slash: 'warp',
          title: _chatL10n(context).chatUiMoveSession,
          description: _chatL10n(
            context,
          ).chatUiChangeThisSessionSExperimentalWorkspace,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.warp,
        ),
      CommandSheetEntry.app(
        slash: 'editor',
        title: _chatL10n(context).chatUiPromptEditor,
        description: _chatL10n(context).chatUiEditTheCurrentPromptInAFocused,
        group: _chatL10n(context).chatUiCompose,
        action: _ChatCommandAction.promptEditor,
      ),
      CommandSheetEntry.app(
        slash: 'files',
        aliases: const ['open'],
        title: _chatL10n(context).chatUiProjectFiles,
        description: _chatL10n(
          context,
        ).chatUiBrowsePreviewDownloadAndAttachProjectFiles,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.files,
      ),
      CommandSheetEntry.app(
        slash: 'health',
        title: _chatL10n(context).chatUiProjectHealth,
        description: _chatL10n(
          context,
        ).chatUiInspectGitLanguageServicesAndFormattersFor,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.projectHealth,
      ),
      CommandSheetEntry.app(
        slash: 'terminal',
        title: _chatL10n(context).libraryTerminalTitle,
        description: _chatL10n(context).chatUiOpenPersistentWorkspaceTerminals,
        group: _chatL10n(context).chatUiNavigate,
        action: _ChatCommandAction.terminal,
      ),
      CommandSheetEntry.app(
        slash: 'models',
        aliases: const ['model', 'mo'],
        title: _chatL10n(context).chatUiModel,
        description: _chatL10n(context).chatUiChooseAServerModelByProviderAnd,
        group: _chatL10n(context).chatUiModelAndAgent,
        action: _ChatCommandAction.model,
      ),
      // OpenCode's agents (build, plan, …), or an agent's modes: offered only
      // where there is more than one to choose.
      if (_conn.agents.length > 1)
        CommandSheetEntry.app(
          slash: 'agents',
          aliases: const ['agent'],
          title: _chatL10n(context).chatUiAgent,
          description: _conn.isAgentBackend
              ? _chatL10n(
                  context,
                ).chatUiChooseAgentMode(KitBidi.auto(_conn.profile?.name ?? ''))
              : _chatL10n(context).chatUiChooseTheActiveOpenCodeAgent,
          group: _chatL10n(context).chatUiModelAndAgent,
          action: _ChatCommandAction.model,
        ),
      // Variants come with the server's model catalog.
      if (_conn.capabilities.serverCatalog)
        CommandSheetEntry.app(
          slash: 'variants',
          title: _chatL10n(context).modelThinkingMode,
          description: _chatL10n(
            context,
          ).chatUiChooseTheCurrentModelVariantOrReasoning,
          group: _chatL10n(context).chatUiModelAndAgent,
          action: _ChatCommandAction.model,
        ),
      CommandSheetEntry.app(
        slash: 'mcps',
        aliases: const ['mcp'],
        title: _chatL10n(context).chatUiMCPServers,
        description: _chatL10n(
          context,
        ).chatUiInspectMCPStatusAuthenticationAndResources,
        group: _commandServerGroup,
        action: _ChatCommandAction.mcpServers,
      ),
      CommandSheetEntry.app(
        slash: 'connect',
        title: _chatL10n(context).chatUiConnectProvider,
        description: _chatL10n(
          context,
        ).chatUiManageProviderAndIntegrationAuthentication,
        group: _commandServerGroup,
        action: _ChatCommandAction.integrations,
      ),
      // §7 row 8.
      if (_conn.capabilities.consoleOrganizations)
        CommandSheetEntry.app(
          slash: 'org',
          aliases: const ['orgs', 'switch-org'],
          title: _chatL10n(context).chatUiSwitchOrganization,
          description: _chatL10n(
            context,
          ).chatUiChangeTheActiveOpenCodeConsoleOrganization,
          group: _commandServerGroup,
          action: _ChatCommandAction.organization,
        ),
      CommandSheetEntry.app(
        slash: 'skills',
        title: _chatL10n(context).chatUiSkills,
        description: _chatL10n(context).chatUiBrowseProjectAndGlobalSkills,
        group: _commandServerGroup,
        action: _ChatCommandAction.skills,
      ),
      // §7 row 20: no tool inventory endpoint, so the destination goes too.
      if (_conn.capabilities.toolInventory)
        CommandSheetEntry.app(
          slash: 'tools',
          title: _chatL10n(context).chatUiToolsAndCapabilities,
          description: _chatL10n(
            context,
          ).chatUiInspectToolsCallableByTheActiveProvider,
          group: _commandServerGroup,
          action: _ChatCommandAction.tools,
        ),
      CommandSheetEntry.app(
        slash: 'references',
        aliases: const ['reference', 'refs'],
        title: _chatL10n(context).chatUiProjectReferences,
        description: _chatL10n(
          context,
        ).chatUiAddAnOpenCodeProjectReferenceToThis,
        group: _commandServerGroup,
        action: _ChatCommandAction.references,
      ),
      CommandSheetEntry.app(
        slash: 'status',
        title: _chatL10n(context).chatUiServerStatus,
        description: _chatL10n(
          context,
        ).chatUiConnectionHealthServerVersionAndLiveMode,
        group: _commandServerGroup,
        action: _ChatCommandAction.status,
      ),
      CommandSheetEntry.app(
        slash: 'debug',
        title: _chatL10n(context).chatUiAppDiagnostics,
        description: _chatL10n(context).chatUiReviewHandledAppErrorsAndSendA,
        group: _commandServerGroup,
        action: _ChatCommandAction.diagnostics,
      ),
      CommandSheetEntry.app(
        slash: 'themes',
        aliases: const ['theme'],
        title: _chatL10n(context).chatUiAppearance,
        description: _chatL10n(
          context,
        ).chatUiFollowAndroidOrChooseTheNativeLight,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.appearance,
      ),
      CommandSheetEntry.app(
        slash: 'diff',
        title: _chatL10n(context).chatUiSessionChanges,
        description: _chatL10n(context).chatUiReviewTheActualDiffForThisSession,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.diff,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
      CommandSheetEntry.app(
        slash: 'context',
        aliases: const ['usage'],
        title: _chatL10n(context).chatUiSessionContext,
        description: _chatL10n(
          context,
        ).chatUiInspectCurrentTokensCacheCostAndContext,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.context,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: _messages.any(
          (message) =>
              message.info.role == 'assistant' && message.info.tokens.total > 0,
        ),
      ),
      // §7 rows 10–11.
      if (_conn.capabilities.sessionShare) ...[
        CommandSheetEntry.app(
          slash: 'share',
          title: _shareUrl == null
              ? _chatL10n(context).chatUiShareSession
              : _chatL10n(context).chatUiCopyShareLink,
          description: _chatL10n(context).chatUiCreateOrCopyAPublicSessionLink,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.share,
          // The conversation menu is its one home (P10.2); typing it
          // still works.
          listed: false,
        ),
        CommandSheetEntry.app(
          slash: 'unshare',
          title: _chatL10n(context).chatUiStopSharing,
          description: _chatL10n(
            context,
          ).chatUiDisableTheCurrentPublicSessionLink,
          group: _chatL10n(context).chatUiCurrentSession,
          action: _ChatCommandAction.unshare,
          // The conversation menu is its one home (P10.2); typing it
          // still works.
          listed: false,
          enabled: _shareUrl != null,
        ),
      ],
      CommandSheetEntry.app(
        slash: 'rename',
        title: _chatL10n(context).chatUiRenameSession,
        description: _chatL10n(context).chatUiChangeTheTitleShownInTheSession,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.rename,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
      CommandSheetEntry.app(
        slash: 'timeline',
        aliases: const ['messages'],
        title: _chatL10n(context).chatUiMessageTimeline,
        description: _chatL10n(context).chatUiFindAMessageJumpToItOr,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.timeline,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: _messages.isNotEmpty,
      ),
      CommandSheetEntry.app(
        slash: 'fork',
        title: _chatL10n(context).chatUiForkSession,
        description: _chatL10n(context).sessionMenuForkHint,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.fork,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: hasUserMessage,
      ),
      CommandSheetEntry.app(
        slash: 'compact',
        aliases: const ['summarize'],
        title: _chatL10n(context).chatUiCompactContext,
        description: _chatL10n(
          context,
        ).chatUiSummarizeTheSessionUsingTheSelectedModel,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.compact,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
        enabled: hasUserMessage,
      ),
      CommandSheetEntry.app(
        slash: 'thinking',
        aliases: const ['toggle-thinking'],
        title: _conn.transcriptReasoningExpanded
            ? _chatL10n(context).chatUiCollapseReasoning
            : _chatL10n(context).chatUiExpandReasoning,
        description: _chatL10n(
          context,
        ).chatUiToggleLongReasoningDetailsAcrossTheTranscript,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.thinking,
      ),
      CommandSheetEntry.app(
        slash: 'timestamps',
        aliases: const ['toggle-timestamps'],
        title: _conn.transcriptTimestampsVisible
            ? _chatL10n(context).chatUiHideTimestamps
            : _chatL10n(context).chatUiShowTimestamps,
        description: _chatL10n(
          context,
        ).chatUiToggleCreationTimesBesideTranscriptEntries,
        group: _chatL10n(context).chatUiTranscriptDisplay,
        action: _ChatCommandAction.timestamps,
      ),
      CommandSheetEntry.app(
        slash: 'undo',
        title: _chatL10n(context).chatUiRevertLastPrompt,
        description: _chatL10n(context).revertUndoDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.undo,
        enabled:
            hasUserMessage &&
            session?.reverted != true &&
            !_conn.sessionRevertSaving(widget.sessionID) &&
            !_conn.busySessions.contains(widget.sessionID),
      ),
      CommandSheetEntry.app(
        slash: 'redo',
        title: _conn.supportsStagedRevert
            ? _chatL10n(context).revertClearAction
            : _chatL10n(context).chatUiRestoreRevertedPrompt,
        description: _conn.supportsStagedRevert
            ? _chatL10n(context).revertClearShortDescription
            : _chatL10n(context).chatUiRestoreTheCurrentlyRevertedSessionState,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.redo,
        enabled: session?.reverted == true,
      ),
      CommandSheetEntry.app(
        slash: 'copy',
        title: _chatL10n(context).chatUiCopyTranscript,
        description: _chatL10n(
          context,
        ).chatUiCopyTheRenderedConversationAsMarkdown,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.copy,
      ),
      CommandSheetEntry.app(
        slash: 'export',
        title: _chatL10n(context).chatUiExportTranscript,
        description: _chatL10n(
          context,
        ).chatUiSaveTheConversationAsAMarkdownFile,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.export,
      ),
      CommandSheetEntry.app(
        slash: 'shell',
        aliases: const ['!'],
        title: _chatL10n(context).chatUiRunShellCommand,
        description: _chatL10n(context).commandSheetShellDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.shell,
      ),
      CommandSheetEntry.app(
        slash: 'retry',
        title: _chatL10n(context).chatUiRetryLastPrompt,
        description: _chatL10n(context).commandSheetRetryDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.retry,
        enabled: hasUserMessage && !_sending,
      ),
      CommandSheetEntry.app(
        slash: 'note',
        title: _chatL10n(context).sessionNoteTitle,
        description: _chatL10n(context).commandSheetNoteDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.note,
      ),
      CommandSheetEntry.app(
        slash: 'approvals',
        title: _chatL10n(context).approvalsUiMenu,
        description: _chatL10n(context).commandSheetApprovalsDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.approvals,
      ),
      CommandSheetEntry.app(
        slash: 'plan',
        aliases: const ['todos'],
        title: _chatL10n(context).chatUiTodos,
        description: _chatL10n(context).commandSheetPlanDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.plan,
        enabled: _latestPlan != null,
      ),
      CommandSheetEntry.app(
        slash: 'reload',
        title: _chatL10n(context).chatUiReloadMessages,
        description: _chatL10n(context).commandSheetReloadDescription,
        group: _chatL10n(context).chatUiCurrentSession,
        action: _ChatCommandAction.reload,
      ),
      CommandSheetEntry.app(
        slash: 'help',
        title: _chatL10n(context).chatUiCommandMap,
        description: _chatL10n(
          context,
        ).chatUiSearchMobileActionsAndServerProvidedCommands,
        group: _commandServerGroup,
        action: _ChatCommandAction.help,
        // The conversation menu is its one home (P10.2); typing it
        // still works.
        listed: false,
      ),
    ];
  }
}

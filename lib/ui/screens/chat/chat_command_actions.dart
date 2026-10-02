part of '../chat_screen.dart';

// Running a command: the launcher, cycling the model, the mobile command
// actions, and the model labels they show.

extension _ChatCommandActions on _ChatScreenState {
  Future<void> _cycleModel({
    bool reverse = false,
    bool favoritesOnly = false,
  }) async {
    if (_conn.isIsolated) return;
    final revision = _conn.connectionRevision;
    try {
      final next = await _conn.cycleModelForSession(
        widget.sessionID,
        reverse: reverse,
        favoritesOnly: favoritesOnly,
      );
      if (!mounted || revision != _conn.connectionRevision) return;
      // By the composer, whose model chip changes with it; a newer cycle
      // replaces the note.
      _showComposerNote(
        next == null
            ? _chatL10n(context).chatUiChooseAnotherModelInThePickerTo
            : _chatL10n(
                context,
              ).chatUiNextTurnsModel(_presentedModelLabel ?? ''),
      );
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Widget? _modelCycleButton() {
    if (_conn.isIsolated) return null;
    final library = _conn.modelLibrary;
    final current = _conn.modelForSession(widget.sessionID);
    final hasRecent =
        library.next(current, available: _conn.modelAvailable) != null;
    final hasFavorites =
        library.next(
          current,
          favoritesOnly: true,
          available: _conn.modelAvailable,
        ) !=
        null;
    if (!hasRecent && !hasFavorites) return null;
    return ModelCycleButton(
      onCycle: _cycleModel,
      hasRecent: hasRecent,
      hasFavorites: hasFavorites,
    );
  }

  /// The command sheet (slice-P10.1), the same one Settings › Tools ›
  /// Commands shows: this server's commands and the app's, and the
  /// subagents a prompt can be handed to. A pick runs in this conversation.
  Future<void> _openCommandLauncher({
    CommandSheetTab initialTab = CommandSheetTab.commands,
  }) async {
    if (_conn.isIsolated) return;
    if (!_supportsPromptAgentMentions && initialTab == CommandSheetTab.agents) {
      return;
    }
    FocusManager.instance.primaryFocus?.unfocus();
    if (_conn.capabilities.serverCatalog) {
      unawaited(_conn.ensureCatalog());
    }
    await showKitFramedSheet<void>(
      context,
      useSafeArea: true,
      maxWidth: 720,
      builder: (sheetContext) => CommandSheet(
        controller: _conn,
        initialTab: initialTab,
        commands: () => _chatCommands,
        unavailable: () => _unavailableChatCommands,
        agents: () => _subagents,
        loading: () => _serverCommandsLoading,
        loaded: () => _serverCommands != null,
        error: () => _serverCommandsError,
        onRefresh: _loadServerCommands,
        onSelected: (command) {
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _selectChatCommand(command);
        },
        onAgentSelected: (agent) {
          if (!_supportsPromptAgentMentions) return;
          FocusManager.instance.primaryFocus?.unfocus();
          Navigator.pop(sheetContext);
          _insertAgentMention(agent);
        },
      ),
    );
  }

  void _selectChatCommand(_ChatCommand command) {
    if (!command.enabled || !_chatCommandSupported(command)) return;
    if (command.serverCommand case final serverCommand?) {
      _composer.value = TextEditingValue(
        text: '/${serverCommand.name} ',
        selection: TextSelection.collapsed(
          offset: serverCommand.name.length + 2,
        ),
      );
      _focus.requestFocus();
      return;
    }
    if (_composer.text.trimLeft().startsWith('/')) _composer.clear();
    unawaited(_runMobileCommand(command.action! as _ChatCommandAction));
  }

  Future<void> _runMobileCommand(_ChatCommandAction action) async {
    try {
      await _executeMobileCommand(action);
    } catch (error) {
      if (mounted) _showActionError(error);
    }
  }

  Future<void> _executeMobileCommand(_ChatCommandAction action) async {
    final strings = _chatL10n(context);
    switch (action) {
      case _ChatCommandAction.newSession:
        if (!await _persistDraft() || !mounted) return;
        final session = await _conn.createSession();
        if (mounted) {
          await _discardUntouchedMobileSession();
          if (!mounted) return;
          await _conn.refreshSessions();
          if (mounted) {
            Navigator.of(context).pushReplacementNamed(
              '/chat/${session.id}',
              arguments: const ChatRouteArguments.newlyCreated(),
            );
          }
        }
        return;
      case _ChatCommandAction.sessions:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => GlobalSessionsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.workspaces:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(builder: (_) => const HomeScreen(initialTab: 0)),
          );
        }
        return;
      case _ChatCommandAction.files:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => KitScreen(
                topBar: KitTopBar(title: strings.chatUiProjectFiles),
                body: FilesScreen(
                  controller: _conn,
                  onAttachFile: _attachProjectFile,
                  onReviewPrompt: _addReviewPrompt,
                  handoff: _handoff, // UX-103 review handoff
                ),
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.projectHealth:
        final repository = await _conn.prepareActionRepository();
        if (repository == null) {
          throw ProductException(strings.chatUiOpenCodeIsReconnectingTryAgain);
        }
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ProjectHealthScreen(
                repository: repository,
                repositoryResolver: _conn.prepareActionRepository,
                capabilities: _conn.capabilities,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.move:
        if (mounted) {
          await showSessionDestinationSheet(
            context,
            controller: _conn,
            sessionID: widget.sessionID,
            mode: SessionDestinationMode.move,
          );
        }
        return;
      case _ChatCommandAction.warp:
        if (mounted) {
          await showSessionDestinationSheet(
            context,
            controller: _conn,
            sessionID: widget.sessionID,
            mode: SessionDestinationMode.warp,
          );
        }
        return;
      case _ChatCommandAction.promptEditor:
        await _openPromptEditor();
        return;
      case _ChatCommandAction.terminal:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => TerminalScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.model:
        if (mounted) {
          await showModelPicker(
            context,
            applyScope: _modelApplyScope,
            sessionID: widget.sessionID,
          );
        }
        return;
      // "/connect" and "/mcps" land on the same screens as Settings › Agent
      // setup › Providers and › MCP, so each has one home.
      case _ChatCommandAction.integrations:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => IntegrationsScreen(
                controller: _conn,
                mode: IntegrationsMode.providers,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.mcpServers:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => IntegrationsScreen(
                controller: _conn,
                mode: IntegrationsMode.mcp,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.organization:
        if (mounted) {
          await showConsoleOrganizationSheet(context, controller: _conn);
        }
        return;
      case _ChatCommandAction.skills:
        if (mounted) {
          final location = _conn.locationRevision;
          final used = await Navigator.of(context).push<bool>(
            KitPageRoute<bool>(
              builder: (_) => SkillsScreen(
                controller: _conn,
                sessionID: _conn.supportsSessionSkills
                    ? widget.sessionID
                    : null,
              ),
            ),
          );
          if (mounted && used == true && _conn.locationRevision == location) {
            _showComposerNote(strings.skillApplied);
            await _load();
          }
        }
        return;
      case _ChatCommandAction.tools:
        // Same destination as Settings › Agent setup › Commands & tools,
        // opened on its Tools tab (index 1 whenever the inventory exists,
        // which is also the gate for this command).
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) =>
                  CapabilitiesScreen(controller: _conn, initialTab: 1),
            ),
          );
        }
        return;
      case _ChatCommandAction.references:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ReferencesScreen(
                controller: _conn,
                onSelected: _attachReference,
              ),
            ),
          );
        }
        return;
      case _ChatCommandAction.status:
        // "/status" means this server's health, not all of Settings: open
        // the hub's Connection › This server screen.
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => ServerSettingsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.diagnostics:
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => AppDiagnosticsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.appearance:
        // Settings › Appearance, the one home of theme and language.
        if (mounted) {
          await Navigator.of(context).push(
            KitPageRoute<void>(
              builder: (_) => AppearanceSettingsScreen(controller: _conn),
            ),
          );
        }
        return;
      case _ChatCommandAction.diff:
        _showDiff();
        return;
      case _ChatCommandAction.context:
        await _showContext();
        return;
      case _ChatCommandAction.share:
        if (_shareUrl case final url?) {
          await _copyShareLink(url);
        } else {
          await _share();
        }
        return;
      case _ChatCommandAction.unshare:
        await _stopSharing();
        return;
      case _ChatCommandAction.rename:
        await _renameCurrentSession();
        return;
      case _ChatCommandAction.timeline:
        await _openTimeline();
        return;
      // Fork lands in one place (P10.2): the copy opens at once, from the
      // menu, "/fork" and a prompt's own Fork alike.
      case _ChatCommandAction.fork:
        await _fork();
        return;
      case _ChatCommandAction.compact:
        await _compact();
        return;
      case _ChatCommandAction.thinking:
        await _toggleReasoningDisplay();
        return;
      case _ChatCommandAction.timestamps:
        await _toggleTimestampDisplay();
        return;
      case _ChatCommandAction.undo:
        await _revertLast();
        return;
      case _ChatCommandAction.redo:
        await _restore();
        return;
      case _ChatCommandAction.copy:
        await _copyTranscript();
        return;
      case _ChatCommandAction.export:
        await _exportTranscript();
        return;
      case _ChatCommandAction.help:
        await _openCommandLauncher();
        return;
      case _ChatCommandAction.shell:
        await _runShellDialog();
        return;
      case _ChatCommandAction.retry:
        await _retryLast();
        return;
      case _ChatCommandAction.note:
        await _openSessionNote();
        return;
      case _ChatCommandAction.approvals:
        await showSessionApprovalsSheet(
          context,
          controller: _conn,
          sessionID: widget.sessionID,
        );
        return;
      case _ChatCommandAction.reload:
        await _load();
        return;
      case _ChatCommandAction.plan:
        _openPlan();
        return;
    }
  }

  /// A picker opened from an open chat applies to this session only. Other
  /// sessions keep the profile default; on OpenCode 2 the server also treats
  /// the model as session state.
  ModelPickerApplyScope get _modelApplyScope => ModelPickerApplyScope.session;

  /// The model as the catalog names it, falling back to the presented
  /// provider/model pair; never a raw wire ID.
  /// The turn footer's model ("opencode/mimo-v2.6-flash-free", or two
  /// joined by an arrow) by the names the catalog gives them, as the
  /// composer shows them.
  String? _catalogModelNames(String? label) {
    if (label == null) return null;
    final models = _conn.catalog?.models ?? const <CatalogModel>[];
    return label
        .split(' → ')
        .map((part) {
          for (final model in models) {
            if (presentedModelLabel(model.providerID, model.id) == part &&
                model.name.trim().isNotEmpty) {
              return model.name.trim();
            }
          }
          return part;
        })
        .join(' → ');
  }

  String? get _presentedModelLabel {
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null) return null;
    for (final candidate in _conn.catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.id == model.modelID &&
          candidate.providerID == model.providerID &&
          candidate.name.trim().isNotEmpty) {
        return candidate.name.trim();
      }
    }
    return presentedModelLabel(model.providerID, model.modelID);
  }

  /// The selected model's catalog entry, when the catalog knows it.
  CatalogModel? get _selectedCatalogModel {
    final model = _conn.modelForSession(widget.sessionID);
    if (model == null) return null;
    for (final candidate in _conn.catalog?.models ?? const <CatalogModel>[]) {
      if (candidate.id == model.modelID &&
          candidate.providerID == model.providerID) {
        return candidate;
      }
    }
    return null;
  }

  /// The agent the server would use unprompted — the first primary agent —
  /// so the composer chip only names an agent when it is a real choice.
  String get _defaultAgentName {
    for (final agent in _conn.agents) {
      if (agent.mode != 'subagent') return agent.name;
    }
    return '';
  }
}

part of '../workspace_screen.dart';

/// Loading projects and workspaces, and creating sessions.
extension _WorkspaceLoad on _WorkspaceScreenState {
  Future<void> _refreshWorkspace() async {
    await _load();
    if (!mounted) return;
    await widget.controller.refreshSessions();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    if (!widget.controller.capabilities.projectManagement) {
      if (mounted) {
        _set(() {
          _projects = const [];
          _workspaces = const [];
          _projectError = null;
          _workspaceError = null;
        });
      }
      return;
    }
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      _set(() => _projectError = _l10n(context).e7WorkspaceDisconnected);
      return;
    }
    _set(() {
      _projectError = null;
      _workspaceError = null;
    });
    try {
      final projects = await repository.listProjects();
      if (!mounted || generation != _loadGeneration) return;
      // A later catalog may confirm the restored folder. Omission leaves
      // the user's choice intact rather than selecting a different project.
      await widget.controller.revalidateRestoredLocation();
      if (!mounted || generation != _loadGeneration) return;
      projects.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      var shouldSelectInitialLocation = false;
      var shouldRestoreSaved = false;
      DefaultChoice<DefaultProject>? projectDefault;
      _set(() {
        _projects = projects;
        if (projects.isEmpty && widget.controller.directory != null) {
          _selectedProjectID = null;
          _selectedDirectory = widget.controller.directory;
          _selectedWorkspaceID = widget.controller.workspace;
          return;
        }
        final controllerDirectory = widget.controller.directory;
        final saved = widget.controller.savedProjectDirectory;
        if (controllerDirectory != null) {
          _restoreGaveUp = false;
          _selectedDirectory = controllerDirectory;
          _selectedWorkspaceID = widget.controller.workspace;
          final matching = WorkspaceScreen.projectForDirectory(
            projects,
            controllerDirectory,
          );
          // An unlisted explicit folder is still the active context. Never
          // label it with a different catalog project's name.
          _selectedProjectID = matching?.id;
        } else if (saved != null && !_restoreGaveUp) {
          // The person already has a project on this server: it is being
          // restored (or is opened now), never replaced by another one.
          _selectedDirectory = saved;
          _selectedWorkspaceID = null;
          _selectedProjectID = WorkspaceScreen.projectForDirectory(
            projects,
            saved,
          )?.id;
          shouldRestoreSaved = true;
        } else {
          // Only a real project folder is opened automatically. The server's
          // catch-all root and any home folder are skipped, so a fresh
          // connection lands on the folder chooser instead of `/root`.
          // Defaults instead of questions (P6.6): the project still picked
          // here, else the only one, else the one worked on most recently
          // (the list is newest first). None: the chooser proposes a new
          // "my-app".
          final usable = projects
              .where(
                (project) =>
                    !isProtectedWorkspaceDirectory(project.directory) &&
                    !isAiTeamDirectory(project.directory),
              )
              .toList();
          final choice = InteractionDefaults.project(
            usable,
            explicitID: _selectedProjectID,
            lastUsedID: usable.firstOrNull?.id,
          );
          final selected = choice.value?.project;
          if (selected == null) {
            _selectedProjectID = null;
            _selectedDirectory = null;
            _selectedWorkspaceID = null;
            return;
          }
          _selectedProjectID = selected.id;
          _selectedDirectory = selected.directory;
          _selectedWorkspaceID = null;
          shouldSelectInitialLocation = true;
          _selectingInitial = true;
          projectDefault = choice;
        }
      });
      final selected = _selectedProject;
      if (shouldSelectInitialLocation && selected != null) {
        try {
          await widget.controller.selectInitialLocation(
            directory: _selectedDirectory ?? selected.directory,
          );
        } finally {
          if (mounted) _set(() => _selectingInitial = false);
        }
        final opened = projectDefault;
        if (opened != null &&
            mounted &&
            widget.controller.directory != null &&
            widget.controller.locationError == null) {
          unawaited(_announceProject(opened));
        }
      }
      if (shouldRestoreSaved) await _restoreSaved();
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        _set(() => _projectError = productErrorText(error));
      }
    }
    if (generation != _loadGeneration) return;
    await _loadWorkspaces();
  }

  /// Says once per server which project this tab opened by itself, with
  /// the way to another one when there is one; nothing when it was the
  /// person's own pick.
  Future<void> _announceProject(DefaultChoice<DefaultProject> choice) async {
    final said = await claimDefaultNotice(
      kind: DefaultKind.project,
      choice: choice,
      profileId: widget.controller.profile?.id,
      prefs: widget.controller.store.prefs,
    );
    if (said == null || !mounted) return;
    final l10n = _l10n(context);
    final name = KitBidi.auto(said);
    _set(() {
      _defaultCanChange = choice.canChange;
      _defaultNotice = choice.reason == DefaultReason.onlyOption
          ? l10n.defaultProjectOnlyNotice(name)
          : l10n.defaultProjectLastUsedNotice(name);
    });
  }

  /// Opens the saved project when the connection came up without it. When
  /// connect is still restoring it there is nothing to do but wait; when the
  /// attempt leaves no folder open, the folder chooser may show.
  Future<void> _restoreSaved() async {
    final controller = widget.controller;
    if (controller.restoringSavedLocation) return;
    try {
      await controller.restoreSavedLocation();
    } catch (_) {
      // Reported through locationError; the chooser below is the way on.
    }
    if (!mounted) return;
    if (controller.directory == null && !controller.restoringSavedLocation) {
      _set(() => _restoreGaveUp = true);
    }
  }

  Future<void> _loadWorkspaces() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) return;
    try {
      final workspaces = await repository.listWorkspaces();
      if (!mounted) return;
      _set(() {
        _workspaces = workspaces
            .where(
              (workspace) =>
                  _selectedProjectID == null ||
                  workspace.projectID == _selectedProjectID,
            )
            .toList();
      });
    } catch (error) {
      if (mounted) _set(() => _workspaceError = productErrorText(error));
    }
  }

  WorkspaceProject? get _selectedProject {
    for (final project in _projects ?? const <WorkspaceProject>[]) {
      if (project.id == _selectedProjectID) return project;
    }
    return null;
  }

  bool get _hasExternalSessionDirectory {
    final directory = _selectedDirectory;
    final project = _selectedProject;
    if (directory == null || project == null) return false;
    return project.directory != directory &&
        !project.worktrees.contains(directory);
  }

  WorkspaceInfo? get _selectedWorkspace {
    for (final workspace in _workspaces) {
      if (workspace.id == _selectedWorkspaceID) return workspace;
    }
    return null;
  }

  /// The folder this screen's conversations run in.
  String? get _contextDirectory =>
      widget.controller.directory ??
      _selectedWorkspace?.directory ??
      _selectedDirectory ??
      _selectedProject?.directory;

  Future<void> _selectWorkspace(WorkspaceInfo? workspace) async {
    await widget.controller.selectLocation(
      directory: workspace?.directory ?? _selectedDirectory,
      workspace: workspace?.id,
    );
    if (!mounted) return;
    _set(() {
      _selectedWorkspaceID = widget.controller.workspace;
      _selectedDirectory = widget.controller.directory;
    });
    // The environment switch failed: say so where the person is.
    final error = widget.controller.locationError;
    if (error != null) _say(error);
  }

  /// What a session is blocked on, or null when nothing is waiting. A run
  /// waiting on a permission, question, or form is not making progress, so
  /// the row names the blocker rather than saying "Working". Permission
  /// outranks question outranks form, the order Activity answers them in.
  String? _blocker(String sessionID, AppLocalizations l10n) {
    final controller = widget.controller;
    if (controller.permissionsForSession(sessionID).isNotEmpty) {
      return l10n.monitorPermission;
    }
    if (controller.questionForSession(sessionID) != null) {
      return l10n.monitorQuestion;
    }
    if (controller.formForSession(sessionID) != null) return l10n.monitorForm;
    return null;
  }

  Future<void> _createSession() async {
    if (_creating) return;
    _set(() => _creating = true);
    try {
      final session = await widget.controller.createSession();
      if (!mounted) return;
      await Navigator.of(context).pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) {
        _say(_l10n(context).e7WorkspaceCreateFailed(productErrorText(error)));
      }
    } finally {
      if (mounted) _set(() => _creating = false);
    }
  }

  /// The project a fresh-worktree task would be created for, or null when
  /// the action must stay hidden: no contract-proven create on this
  /// connection, no project selected, or a managed workspace (not a local
  /// git checkout) is active.
  WorkspaceProject? get _isolatedTaskProject {
    final capabilities = widget.controller.capabilities;
    if (!capabilities.projectManagement || !capabilities.worktreeCreate) {
      return null;
    }
    // Nothing starts before a project folder is open (or while it opens).
    if (widget.controller.workspaceChoiceRequired) return null;
    if (_selectedWorkspaceID != null) return null;
    final project = _selectedProject;
    if (project == null || project.directory.trim().isEmpty) return null;
    return project;
  }

  Future<void> _startIsolatedTask() async {
    final project = _isolatedTaskProject;
    if (_creating || project == null) return;
    final session = await showIsolatedTaskSheet(
      context,
      controller: widget.controller,
      project: project,
    );
    if (!mounted || session == null) return;
    await Navigator.of(context).pushNamed(
      '/chat/${session.id}',
      arguments: const ChatRouteArguments.newlyCreated(),
    );
    if (mounted) await widget.controller.refreshSessions();
  }
}

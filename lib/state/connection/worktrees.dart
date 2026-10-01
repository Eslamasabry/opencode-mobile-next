part of '../connection.dart';

// Isolated tasks, worktrees and moving sessions between folders.

/// [ConnectionController]'s isolated tasks and worktrees.
mixin _ConnectionControllerWorktrees on ChangeNotifier {
  ConnectionController get _self;

  /// Captured when a task sheet opens, before the user can press Start.
  Object get isolatedTaskScope => _self._isolatedTaskScope;

  /// Starts an explicit "new task in a fresh worktree" run for [project] on
  /// the connected profile. The launch subscribes to [events] before the
  /// create call, and [openSessionInWorktree] is the only route into a
  /// session. Requires [ServerCapabilities.worktreeCreate]; the caller owns
  /// the returned launch and disposes it.
  IsolatedTaskLaunch startIsolatedTask({
    required WorkspaceProject project,
    required Object expectedScope,
    String? name,
    Duration readinessTimeout = const Duration(seconds: 45),
  }) => _self._startIsolatedTask(
    project: project,
    expectedScope: expectedScope,
    name: name,
    readinessTimeout: readinessTimeout,
  );

  /// Switches scope to [directory] and creates a blank session there, only
  /// while the same profile is connected and the switched scope still
  /// resolves to [project]. A mismatch throws before any session exists, so
  /// a connection or scope change cannot retarget the task at another
  /// project. Nothing is sent to the new session.
  Future<Session> openSessionInWorktree({
    required WorkspaceProject project,
    required String directory,
    String? profileID,
  }) => _self._openSessionInWorktree(
    project: project,
    directory: directory,
    profileID: profileID,
  );

  Future<void> moveSessionToDirectory(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) => _self._moveSessionToDirectory(
    sessionID,
    directory: directory,
    moveChanges: moveChanges,
  );

  Future<void> warpSessionToWorkspace(
    String sessionID, {
    required String directory,
    required String? workspaceID,
    required bool copyChanges,
  }) => _self._warpSessionToWorkspace(
    sessionID,
    directory: directory,
    workspaceID: workspaceID,
    copyChanges: copyChanges,
  );

  Future<void> switchConsoleOrganization(ConsoleOrganization organization) =>
      _self._switchConsoleOrganization(organization);
}

extension _ConnectionControllerWorktreesImpl on ConnectionController {
  /// The body of [isolatedTaskScope].
  Object get _isolatedTaskScope => (
    this,
    _connectedProfile?.id,
    _connectedProfile?.baseUrl,
    connectionRevision,
    locationRevision,
    directory,
    workspace,
  );

  /// The body of [startIsolatedTask].
  IsolatedTaskLaunch _startIsolatedTask({
    required WorkspaceProject project,
    required Object expectedScope,
    String? name,
    Duration readinessTimeout = const Duration(seconds: 45),
  }) {
    if (expectedScope != isolatedTaskScope) {
      throw const ProductException(
        'The connection or location changed. Reopen the task sheet.',
      );
    }
    if (!capabilities.worktreeCreate) {
      throw const ProductException(
        'Creating worktrees is not available on this connection.',
      );
    }
    final profileID = _connectedProfile?.id;
    bool launchScopeIsCurrent() =>
        profileID != null && expectedScope == isolatedTaskScope;
    final trimmed = name?.trim();
    final requestedName = trimmed?.isNotEmpty == true ? trimmed : null;
    var openingAttempted = false;
    final launch = IsolatedTaskLaunch(
      project: project,
      requestedName: requestedName,
      events: events,
      readinessTimeout: readinessTimeout,
      create: () async {
        if (!launchScopeIsCurrent()) {
          throw const ProductException(
            'The connection changed. Start the task again.',
          );
        }
        final currentRepository = await prepareActionRepository();
        if (currentRepository == null || !launchScopeIsCurrent()) {
          throw const ProductException(
            'OpenCode is reconnecting. Try again shortly.',
          );
        }
        // Revalidate the cached project against this exact server scope before
        // the first mutation. A sheet may originate from an older catalog.
        final projects = await currentRepository.listProjects();
        if (!launchScopeIsCurrent() ||
            !identical(repository, currentRepository)) {
          throw const ProductException(
            'The connection or location changed. Reopen the task sheet.',
          );
        }
        if (!projects.any(
          (candidate) =>
              candidate.id == project.id &&
              ConnectionController.sameDirectoryPath(
                candidate.directory,
                project.directory,
              ),
        )) {
          throw const ProductException(
            'The project could not be confirmed. Reopen it before starting.',
          );
        }
        return currentRepository.createWorktree(
          projectDirectory: project.directory,
          name: requestedName,
        );
      },
      open: (directory) {
        if (!openingAttempted && !launchScopeIsCurrent()) {
          throw const ProductException(
            'The connection or location changed. Start the task again.',
          );
        }
        // A retry is an explicit choice to reopen this same destination;
        // the first attempt may already have switched into the worktree.
        openingAttempted = true;
        return openSessionInWorktree(
          project: project,
          directory: directory,
          profileID: profileID,
        );
      },
    );
    unawaited(launch.start());
    return launch;
  }

  /// The body of [openSessionInWorktree].
  Future<Session> _openSessionInWorktree({
    required WorkspaceProject project,
    required String directory,
    String? profileID,
  }) async {
    final profile = _connectedProfile;
    if (profile == null || (profileID != null && profile.id != profileID)) {
      throw const ProductException(
        'The connection changed. Start the task again.',
      );
    }
    if (!capabilities.worktreeCreate) {
      throw const ProductException(
        'Creating worktrees is not available on this connection.',
      );
    }
    // _selectLocation changes both revisions synchronously before hydration.
    // Pin the intended revisions before invoking it, never adopt a later
    // selection that happens to use the same directory.
    final changesLocation = this.directory != directory || workspace != null;
    final revision = locationRevision + (changesLocation ? 1 : 0);
    final connection = connectionRevision + (changesLocation ? 1 : 0);
    bool scopeIsCurrent() =>
        _connectedProfile?.id == profile.id &&
        _connectedProfile?.baseUrl == profile.baseUrl &&
        locationRevision == revision &&
        connectionRevision == connection &&
        workspace == null &&
        ConnectionController.sameDirectoryPath(this.directory, directory);
    await selectLocation(directory: directory);
    if (!scopeIsCurrent()) {
      throw const ProductException(
        'OpenCode did not switch to the new worktree.',
      );
    }
    final currentRepository = await prepareActionRepository();
    if (currentRepository == null || !scopeIsCurrent()) {
      throw const ProductException(
        'OpenCode is reconnecting. Try again shortly.',
      );
    }
    final current = await currentRepository.loadCurrentProject();
    if (current == null || current.id != project.id) {
      throw const ProductException(
        'The worktree could not be confirmed for this project. '
        'No session was started.',
      );
    }
    if (!scopeIsCurrent() || !identical(repository, currentRepository)) {
      throw const ProductException(
        'The location changed before the session could start.',
      );
    }
    final session = await _createSession(scopeIsCurrent: scopeIsCurrent);
    final sessionProject = session.projectID?.trim() ?? '';
    final sessionDirectory = session.directory;
    if (session.workspaceID != null ||
        (sessionProject.isNotEmpty && sessionProject != project.id) ||
        (sessionDirectory != null &&
            !ConnectionController.sameDirectoryPath(
              sessionDirectory,
              directory,
            ))) {
      throw ProductException(
        'The session opened outside the new worktree, so it was not started '
        'here. Find it in the session list before using it.',
      );
    }
    return session;
  }

  /// The body of [moveSessionToDirectory].
  Future<void> _moveSessionToDirectory(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.moveSession(
      sessionID,
      directory: directory,
      moveChanges: moveChanges,
    );
    await selectLocation(directory: directory);
    try {
      await repository?.addSessionLocationReminder(sessionID, directory);
    } catch (_) {
      // The move itself succeeded. An older server may not support the
      // synthetic no-reply reminder used by newer OpenCode clients.
    }
  }

  /// The body of [warpSessionToWorkspace].
  Future<void> _warpSessionToWorkspace(
    String sessionID, {
    required String directory,
    required String? workspaceID,
    required bool copyChanges,
  }) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.warpSession(
      sessionID,
      workspaceID: workspaceID,
      copyChanges: copyChanges,
    );
    await selectLocation(directory: directory, workspace: workspaceID);
    try {
      await repository?.addSessionLocationReminder(sessionID, directory);
    } catch (_) {
      // Keep a successful warp successful when only the contextual reminder
      // is unavailable on an older server.
    }
  }

  /// The body of [switchConsoleOrganization].
  Future<void> _switchConsoleOrganization(
    ConsoleOrganization organization,
  ) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.switchConsoleOrganization(organization);
    final currentProfile = _connectedProfile;
    if (currentProfile == null) {
      await refreshCatalog();
      return;
    }
    await _resumeLifecycleTransport(
      currentProfile,
      directory: directory,
      workspace: workspace,
    );
  }
}

part of '../product_repository.dart';

mixin _SdkWorkspaceOps on _SdkCore {
  @override
  Future<List<WorkspaceProject>> listProjects() =>
      _guard('Could not load projects', () async {
        final response = await _client.getProjectApi().projectList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const []).map(_mapProject).toList();
      });

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) => _guard('Could not rename project', () async {
    final exactID = projectID.trim();
    final exactDirectory = projectDirectory.trim();
    if (exactID.isEmpty || exactDirectory.isEmpty) {
      throw const ProductException('The project identity is incomplete');
    }
    final response = await _client.getProjectApi().projectUpdate(
      projectID: exactID,
      directory: exactDirectory,
      projectUpdateRequest: sdk.ProjectUpdateRequest(name: name.trim()),
    );
    final project = response.data;
    if (project == null) {
      throw const ProductException('OpenCode returned an invalid project');
    }
    return _mapProject(project);
  });

  @override
  Future<WorkspaceProject?> loadCurrentProject() =>
      _guard('Could not resolve the current project', () async {
        final response = await _client.getProjectApi().projectCurrent(
          directory: _directory,
          workspace: _workspace,
        );
        final project = response.data;
        if (project == null || project.worktree.trim().isEmpty) return null;
        return _mapProject(project);
      });

  static WorkspaceProject _mapProject(sdk.Project project) => WorkspaceProject(
    id: project.id,
    name: project.name?.trim().isNotEmpty == true
        ? project.name!
        : _basename(project.worktree),
    directory: project.worktree,
    worktrees: project.sandboxes,
    updatedAt: project.time.updated,
  );

  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) => _guardWorktree('Could not load worktrees', () async {
    final root = _requiredWorktreeDirectory(projectDirectory, 'project');
    final response = await _client.getExperimentalApi().worktreeList(
      directory: root,
      workspace: _workspace,
    );
    var directories = response.data ?? const <String>[];
    final exactProjectID = projectID?.trim();
    if (directories.isNotEmpty && exactProjectID?.isNotEmpty == true) {
      try {
        final discovered = await _client.getProjectApi().projectDirectories(
          projectID: exactProjectID!,
          directory: root,
          workspace: _workspace,
        );
        final canonicalDirectories = (discovered.data ?? const [])
            .where((item) => item.strategy == 'git_worktree')
            .map((item) => item.directory)
            .toList(growable: false);
        final resolved = <String>[];
        final seen = <String>{};
        for (final directory in directories) {
          final basename = _basename(directory);
          final matching = canonicalDirectories
              .where((candidate) => _basename(candidate) == basename)
              .toList(growable: false);
          final resolvedDirectory = matching.length == 1
              ? matching.single
              : directory;
          if (seen.add(resolvedDirectory)) {
            resolved.add(resolvedDirectory);
          }
        }
        directories = resolved;
      } catch (_) {
        // Older servers expose only worktree.list. Keep that authoritative
        // existence list when project directory discovery is unavailable.
      }
    }
    return directories
        .where((directory) => directory.trim().isNotEmpty)
        .map(
          (directory) =>
              WorktreeInfo(name: _basename(directory), directory: directory),
        )
        .toList(growable: false);
  });

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => _guardWorktree('Could not create worktree', () async {
    final root = _requiredWorktreeDirectory(projectDirectory, 'project');
    final trimmedName = name?.trim();
    final response = await _client.getExperimentalApi().worktreeCreate(
      directory: root,
      workspace: _workspace,
      worktreeCreateInput: sdk.WorktreeCreateInput(
        name: trimmedName?.isNotEmpty == true ? trimmedName : null,
      ),
    );
    final worktree = response.data;
    if (worktree == null || worktree.directory.trim().isEmpty) {
      throw const ProductException('OpenCode returned an invalid worktree');
    }
    return WorktreeInfo(
      name: worktree.name,
      directory: worktree.directory,
      branch: worktree.branch,
    );
  });

  @override
  Future<List<VersionControlFile>> listWorktreeFileStatuses(String directory) =>
      _guard('Could not inspect worktree changes', () async {
        final target = _requiredWorktreeDirectory(directory, 'worktree');
        final response = await _client.getInstanceApi().vcsStatus(
          directory: target,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (file) => VersionControlFile(
                path: file.file,
                status: file.status.value.toString(),
                additions: file.additions.toInt(),
                deletions: file.deletions.toInt(),
              ),
            )
            .toList(growable: false);
      });

  @override
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  }) => _guardWorktree('Could not reset worktree', () async {
    final root = _requiredWorktreeDirectory(projectDirectory, 'project');
    final target = _requiredSandboxDirectory(root, directory);
    final response = await _client.getExperimentalApi().worktreeReset(
      directory: root,
      workspace: _workspace,
      worktreeResetInput: sdk.WorktreeResetInput(directory: target),
    );
    if (response.data != true) {
      throw const ProductException(
        'OpenCode did not confirm the worktree reset',
      );
    }
  });

  @override
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) => _guardWorktree('Could not remove worktree', () async {
    final root = _requiredWorktreeDirectory(projectDirectory, 'project');
    final target = _requiredSandboxDirectory(root, directory);
    final response = await _client.getExperimentalApi().worktreeRemove(
      directory: root,
      workspace: _workspace,
      worktreeRemoveInput: sdk.WorktreeRemoveInput(directory: target),
    );
    if (response.data != true) {
      throw const ProductException(
        'OpenCode did not confirm the worktree removal',
      );
    }
  });

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() => _guard(
    'Could not load workspaces',
    () => _loadWorkspaces(directory: _directory, workspace: _workspace),
  );

  @override
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  }) => _guard(
    'Could not load managed workspaces',
    () => _loadWorkspaces(directory: projectDirectory, workspace: null),
  );

  @override
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  }) => _guard('Could not load workspace adapters', () async {
    final response = await _client
        .getWorkspaceApi()
        .experimentalWorkspaceAdapterList(directory: projectDirectory);
    return (response.data ?? const [])
        .map(
          (adapter) => WorkspaceAdapterInfo(
            type: adapter.type,
            name: adapter.name,
            description: adapter.description,
          ),
        )
        .toList(growable: false);
  });

  @override
  Future<void> syncWorkspaceList({required String projectDirectory}) =>
      _guard('Could not discover workspaces', () async {
        await _client.getWorkspaceApi().experimentalWorkspaceSyncList(
          directory: projectDirectory,
        );
      });

  @override
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  }) => _guard('Could not create workspace', () async {
    final adapterType = type.trim();
    if (adapterType.isEmpty) {
      throw const ProductException('Choose a workspace adapter');
    }
    final normalizedBranch = branch?.trim();
    final response = await _client
        .getWorkspaceApi()
        .experimentalWorkspaceCreate(
          directory: projectDirectory,
          experimentalWorkspaceCreateRequest:
              sdk.ExperimentalWorkspaceCreateRequest(
                type: adapterType,
                branch: normalizedBranch?.isNotEmpty == true
                    ? normalizedBranch
                    : null,
              ),
        );
    final workspace = response.data;
    if (workspace == null) {
      throw const ProductException('OpenCode returned an invalid workspace');
    }
    return WorkspaceInfo(
      id: workspace.id,
      projectID: workspace.projectID,
      name: workspace.name,
      type: workspace.type,
      branch: workspace.branch,
      directory: workspace.directory,
      status: null,
    );
  });

  @override
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) => _guard('Could not remove workspace', () async {
    await _client.getWorkspaceApi().experimentalWorkspaceRemove(
      id: id,
      directory: projectDirectory,
    );
  });

  Future<List<WorkspaceInfo>> _loadWorkspaces({
    required String? directory,
    required String? workspace,
  }) async {
    final response = await _client.getWorkspaceApi().experimentalWorkspaceList(
      directory: directory,
      workspace: workspace,
    );
    List<sdk.WorkspaceEventConnectionStatus> statuses = const [];
    try {
      final statusResponse = await _client
          .getWorkspaceApi()
          .experimentalWorkspaceStatus(
            directory: directory,
            workspace: workspace,
          );
      statuses = statusResponse.data ?? const [];
    } catch (_) {
      // Workspace listing predates the status endpoint. Keep older servers
      // useful and surface an unknown status instead of erasing the list.
    }
    final statusByID = <String, String>{};
    for (final status in statuses) {
      final id = status.workspaceID;
      if (id != null) statusByID[id] = status.status.value.toString();
    }
    return (response.data ?? const [])
        .map(
          (workspace) => WorkspaceInfo(
            id: workspace.id,
            projectID: workspace.projectID,
            name: workspace.name,
            type: workspace.type,
            branch: workspace.branch,
            directory: workspace.directory,
            status: statusByID[workspace.id],
          ),
        )
        .toList(growable: false);
  }

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) => _guard('Could not search sessions', () async {
    final query = search?.trim();
    final legacyCursor = cursor == null ? null : int.tryParse(cursor);
    if (cursor != null && legacyCursor == null) {
      throw const ProductException(
        'Session pagination expired. Refresh the list.',
      );
    }
    // Deliberately omit the repository's selected directory/workspace. This
    // endpoint is the server-wide finder; passing the active directory would
    // silently reduce it to the list the Workspace screen already has.
    final response = await _client.getExperimentalApi().experimentalSessionList(
      roots: sdk.OpencodeSdkRawUnion051(true),
      cursor: legacyCursor,
      search: query?.isNotEmpty == true ? query : null,
      limit: limit,
      archived: sdk.OpencodeSdkRawUnion052(includeArchived),
    );
    final items = (response.data ?? const [])
        .map(
          (item) => GlobalSessionResult(
            session: _sessionFromGlobalSdk(item),
            projectName: item.project?.name,
            projectDirectory: item.project?.worktree,
          ),
        )
        .toList();
    final next = response.headers.value('x-next-cursor');
    return ServerPage(
      items: items,
      nextCursor: next?.isNotEmpty == true ? next : null,
    );
  });

  @override
  Future<Session> getSessionDetails(String id) =>
      _guard('Could not load this session', () async {
        final response = await _client.getSessionApi().sessionGet(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
        );
        final session = response.data;
        if (session == null) {
          throw const ProductException('OpenCode returned an invalid session');
        }
        return _sessionFromSdk(session);
      });

  @override
  Future<List<Session>> listSessionChildren(String id) =>
      _guard('Could not load subagent sessions', () async {
        final response = await _client.getSessionApi().sessionChildren(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
        );
        final children = (response.data ?? const [])
            .map(_sessionFromSdk)
            .where((session) => session.parentID == id)
            .toList(growable: false);
        children.sort(
          (a, b) => (a.time?.created ?? 0).compareTo(b.time?.created ?? 0),
        );
        return children;
      });

  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String projectID) =>
      _guard('Could not load project directories', () async {
        final response = await _client.getProjectApi().projectDirectories(
          projectID: projectID,
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (item) => ProjectDirectoryInfo(
                directory: item.directory,
                strategy: item.strategy,
              ),
            )
            .toList();
      });

  @override
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) => _guard('Could not move the session', () async {
    await _client.getControlPlaneApi().experimentalControlPlaneMoveSession(
      experimentalControlPlaneMoveSessionRequest:
          sdk.ExperimentalControlPlaneMoveSessionRequest(
            sessionID: sessionID,
            destination: sdk.MoveSessionDestination(directory: directory),
            moveChanges: moveChanges,
          ),
    );
  });

  @override
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  }) => _guard('Could not warp the session', () async {
    await _client.getWorkspaceApi().experimentalWorkspaceWarp(
      directory: _directory,
      workspace: _workspace,
      experimentalWorkspaceWarpRequest: sdk.ExperimentalWorkspaceWarpRequest(
        id: workspaceID,
        sessionID: sessionID,
        copyChanges: copyChanges,
      ),
    );
  });

  @override
  Future<bool> startWorkspaceSync() =>
      // The detail-preserving guard: sync failures carry OpenCode's own
      // message when the server declares one.
      _guardWorktree('Could not start workspace sync', () async {
        final response = await _client.getSyncApi().syncStart(
          directory: _directory,
          workspace: _workspace,
        );
        return response.data == true;
      });

  @override
  Future<String> stealSessionIntoWorkspace(String sessionID) => _guardWorktree(
    'Could not steal the session into this workspace',
    () async {
      final response = await _client.getSyncApi().syncSteal(
        directory: _directory,
        workspace: _workspace,
        syncStealRequest: sdk.SyncStealRequest(sessionID: sessionID),
      );
      final stolen = response.data;
      if (stolen == null) {
        throw const ProductException('Server confirmed no stolen session');
      }
      return stolen.sessionID;
    },
  );

  @override
  Future<List<ConsoleOrganization>> listConsoleOrganizations() =>
      _guard('Could not load organizations', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalConsoleListOrgs(
              directory: _directory,
              workspace: _workspace,
            );
        return (response.data?.orgs ?? const [])
            .map(
              (item) => ConsoleOrganization(
                accountID: item.accountID,
                accountEmail: item.accountEmail,
                accountUrl: item.accountUrl,
                orgID: item.orgID,
                orgName: item.orgName,
                active: item.active,
              ),
            )
            .toList();
      });

  @override
  Future<void> switchConsoleOrganization(ConsoleOrganization organization) =>
      _guard('Could not switch organization', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalConsoleSwitchOrg(
              directory: _directory,
              workspace: _workspace,
              experimentalConsoleSwitchOrgRequest:
                  sdk.ExperimentalConsoleSwitchOrgRequest(
                    accountID: organization.accountID,
                    orgID: organization.orgID,
                  ),
            );
        if (response.data != true) {
          throw const ProductException('The server did not confirm the switch');
        }
        await _client.getInstanceApi().instanceDispose(
          directory: _directory,
          workspace: _workspace,
        );
      });

  @override
  Future<void> addSessionLocationReminder(
    String sessionID,
    String directory,
  ) => _guard('Could not update the session location context', () async {
    await _client.getSessionApi().sessionPromptAsync(
      sessionID: sessionID,
      directory: _directory,
      workspace: _workspace,
      sessionPromptAsyncRequest: sdk.SessionPromptAsyncRequest(
        noReply: true,
        parts: [
          sdk.OpencodeSdkRawUnion086({
            'type': 'text',
            'text':
                '<system-reminder>The user has changed the current working directory to "$directory". This is still the same project but at a possibly new location; take this into account when working with any files from now on.</system-reminder>',
            'synthetic': true,
          }),
        ],
      ),
    );
  });
}

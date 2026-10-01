part of '../gateway_operations.dart';

mixin _Api2ProjectOps on _Api2Core {
  // ---------------- Host: projects & worktrees ----------------

  @override
  Future<List<WorkspaceProject>> listProjects() =>
      _guard('Could not load projects', () async {
        final json = await _transport.getJson('/project');
        return [for (final item in _dataMaps(json)) _project(item)];
      });

  WorkspaceProject _project(Map<String, dynamic> json) {
    final directory = (json['directory'] ?? json['canonical'] ?? '').toString();
    final time = json['time'];
    final worktrees = json['worktrees'];
    return WorkspaceProject(
      id: (json['id'] ?? '').toString(),
      name: (json['name'] ?? '').toString().isNotEmpty
          ? json['name'].toString()
          : _basename(directory),
      directory: directory,
      worktrees: worktrees is List
          ? worktrees.map((value) => value.toString()).toList()
          : const [],
      updatedAt: time is Map
          ? ((time['updated'] ?? time['created']) as num?)?.toInt() ?? 0
          : 0,
    );
  }

  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) => _guard('Could not rename the project', () async {
    final json = await _transport.patchJson(
      '/project/$projectID',
      body: {'name': name},
    );
    final data = _dataMap(json);
    if (data.isNotEmpty) return _project(data);
    return WorkspaceProject(
      id: projectID,
      name: name,
      directory: projectDirectory,
      worktrees: const [],
      updatedAt: DateTime.now().millisecondsSinceEpoch,
    );
  });

  @override
  Future<WorkspaceProject?> loadCurrentProject() =>
      _guard('Could not load the current project', () async {
        final data = await _currentProjectJson();
        return data.isEmpty ? null : _project(data);
      });

  /// `GET /api/project/current` returns the bare project object (no `data`
  /// envelope, live-verified on beta-18600).
  Future<Map<String, dynamic>> _currentProjectJson() async {
    final json = await _transport.getJson('/project/current', query: _loc());
    final enveloped = _dataMap(json);
    if (enveloped.isNotEmpty) return enveloped;
    return json is Map<String, dynamic> && json['id'] != null ? json : const {};
  }

  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String projectID) =>
      _guard('Could not load project directories', () async {
        final json = await _transport.getJson('/worktree/$projectID');
        return [
          for (final item in _dataMaps(json))
            if ((item['directory'] ?? '').toString().isNotEmpty)
              ProjectDirectoryInfo(
                directory: item['directory'].toString(),
                strategy: item['strategy']?.toString(),
              ),
        ];
      });

  Future<String> _resolveProjectID(
    String projectDirectory,
    String? projectID,
  ) async {
    if (projectID?.isNotEmpty == true) return projectID!;
    final json = await _transport.getJson(
      '/location',
      query: {'location[directory]': projectDirectory},
    );
    final project = json is Map<String, dynamic> ? json['project'] : null;
    final id = project is Map ? project['id']?.toString() : null;
    if (id == null || id.isEmpty) {
      throw const ProductException(
        'OpenCode could not resolve this directory to a project',
      );
    }
    return id;
  }

  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) => _guard('Could not load worktrees', () async {
    final id = await _resolveProjectID(projectDirectory, projectID);
    final json = await _transport.getJson('/worktree/$id');
    return [
      for (final item in _dataMaps(json))
        if ((item['directory'] ?? '').toString().isNotEmpty)
          WorktreeInfo(
            name: _basename(item['directory'].toString()),
            directory: item['directory'].toString(),
            branch: item['branch']?.toString(),
          ),
    ];
  });

  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => _guard('Could not create the worktree', () async {
    final id = await _resolveProjectID(projectDirectory, null);
    final json = await _transport.postJson(
      '/worktree/$id',
      body: {'name': ?name},
    );
    final data = _dataMap(json);
    final directory = (data['directory'] ?? '').toString();
    if (directory.isEmpty) {
      throw const ProductException('OpenCode returned no worktree directory');
    }
    return WorktreeInfo(
      name: name ?? _basename(directory),
      directory: directory,
      branch: data['branch']?.toString(),
    );
  });

  @override
  Future<List<VersionControlFile>> listWorktreeFileStatuses(String directory) =>
      _guard('Could not load worktree changes', () async {
        final json = await _transport.getJson(
          '/vcs/status',
          query: {'location[directory]': directory},
        );
        return [for (final item in _dataMaps(json)) mapVcsStatusJson(item)];
      });

  @override
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) => _guard('Could not remove the worktree', () async {
    final id = await _resolveProjectID(projectDirectory, null);
    await _transport.deleteJson(
      '/worktree/$id',
      body: {'directory': directory, 'force': false},
    );
  });

  // ---------------- Host: workspaces & global sessions ----------------

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() async =>
      // v2 has no workspace inventory endpoint (capability
      // managedWorkspaces: false); create/destroy exist but nothing lists.
      const [];

  @override
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  }) => _guard('Could not create the workspace', () async {
    final json = await _transport.postJson(
      '/workspace',
      body: {'provider': type},
    );
    final data = json is Map<String, dynamic> ? json['data'] : null;
    final id = data is String ? data : data?.toString() ?? '';
    if (id.isEmpty) {
      throw const ProductException('OpenCode returned no workspace ID');
    }
    return WorkspaceInfo(
      id: id,
      projectID: '',
      name: id,
      type: type,
      branch: branch,
    );
  });

  @override
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) => _guard(
    'Could not remove the workspace',
    () => _transport.deleteJson('/workspace/$id'),
  );

  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) => _guard('Could not search sessions', () async {
    final page = await client.sessions(
      unscoped: true,
      cursor: cursor,
      order: 'desc',
      search: search?.trim().isNotEmpty == true ? search!.trim() : null,
      limit: limit,
      rootsOnly: true,
    );
    return ServerPage(
      items: [
        for (final session in page.data)
          if (includeArchived || !session.archived)
            GlobalSessionResult(
              session: mapApi2Session(session),
              projectDirectory: session.location?.directory,
            ),
      ],
      nextCursor: page.nextCursor?.isNotEmpty == true ? page.nextCursor : null,
    );
  });

  @override
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) => _guard(
    'Could not move the session',
    // Lossy: v2 move has no move-changes toggle; file changes stay where
    // they are.
    () => _transport.postJson(
      '/session/$sessionID/move',
      body: {'directory': directory},
    ),
  );
}

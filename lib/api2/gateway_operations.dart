/// OpenCode 2 implementation of the protocol-neutral
/// [ServerOperationsGateway]: everything the product screens drive beyond
/// the live transport in `gateway.dart`.
///
/// Extends the v1 [ProductRepository] base so every operation with no v2
/// endpoint keeps the exact graceful "unavailable on this server"
/// [ProductException] the UI already renders; only genuinely supported
/// operations are overridden with real v2 calls.
library;

import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart' show Options;

import '../api/mcp_oauth.dart';
import '../api/models.dart';
import '../api/product_repository.dart' show ProductRepository;
import '../domain/server_gateway.dart';
import '../domain/parallel_requests.dart';
import 'client.dart';
import 'gateway_mappers.dart';
import 'models.dart';
import 'transport.dart';

class Api2OperationsGateway extends ProductRepository
    implements StagedRevertGateway {
  final Api2Client client;

  /// v2 OAuth attempt routes are integration-scoped, but the domain contract
  /// only carries the attempt ID (matrix risk 7). Remember the owning
  /// integration for every attempt this gateway started.
  final Map<String, ({String integrationID, Map<String, dynamic> location})>
  _oauthAttempts = {};
  final Map<String, IntegrationAuthStatus> _oauthTerminalStatuses = {};

  Api2OperationsGateway({required this.client});

  @override
  Future<ManagedShellList> loadRunningShells() =>
      _guard('Could not load running commands', () async {
        try {
          final json = await _transport.getJson(
            '/shell',
            query: _loc(),
            receiveTimeout: const Duration(seconds: 8),
          );
          return ManagedShellList(
            supported: true,
            shells: [for (final item in _dataMaps(json)) _managedShell(item)],
          );
        } on Api2Error catch (error) {
          if ([404, 405, 501].contains(error.statusCode)) {
            return const ManagedShellList(supported: false);
          }
          rethrow;
        }
      });

  @override
  Future<String?> managedShellServerIdentity() =>
      _guard('Could not identify the server', () async {
        final health = await _transport.getJson(
          '/health',
          receiveTimeout: const Duration(seconds: 8),
        );
        return health is Map && health['pid'] is num
            ? health['pid'].toString()
            : null;
      });

  @override
  Future<ManagedShell?> getManagedShell(String id) =>
      _guard('Could not load the command', () async {
        try {
          final json = await _transport.getJson(
            '/shell/${Uri.encodeComponent(id)}',
            query: _loc(),
            receiveTimeout: const Duration(seconds: 8),
          );
          return _managedShell(_dataMap(json));
        } on Api2Error catch (error) {
          if (error.isNotFound) return null;
          rethrow;
        }
      });

  @override
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  }) => _guard('Could not read command output', () async {
    final json = _dataMap(
      await _transport.getJson(
        '/shell/${Uri.encodeComponent(id)}/output',
        query: _loc({'cursor': cursor, 'limit': limit.clamp(1, 65536)}),
        receiveTimeout: const Duration(seconds: 8),
      ),
    );
    return ManagedShellOutput(
      text: json['output'] as String? ?? '',
      cursor: (json['cursor'] as num).toInt(),
      size: (json['size'] as num).toInt(),
      truncated: json['truncated'] == true,
    );
  });

  @override
  Future<void> stopManagedShell(String id) =>
      _guard('Could not stop the command', () async {
        await _transport.deleteJson(
          '/shell/${Uri.encodeComponent(id)}',
          query: _loc(),
        );
      });

  @override
  Future<ManagedShell> setManagedShellTimeout(String id, Duration? timeout) =>
      _guard('Could not change the timeout', () async {
        final json = await _transport.patchJson(
          '/shell/${Uri.encodeComponent(id)}/timeout',
          query: _loc(),
          body: {'timeout': timeout?.inMilliseconds ?? 0},
        );
        return _managedShell(_dataMap(json));
      });

  static ManagedShell _managedShell(Map<String, dynamic> json) {
    final time = json['time'] as Map? ?? const {};
    final metadata = json['metadata'] as Map? ?? const {};
    return ManagedShell(
      id: json['id'] as String,
      command: json['command'] as String? ?? '',
      status: ManagedShellStatus.values.firstWhere(
        (status) => status.name == json['status'],
        orElse: () => ManagedShellStatus.unknown,
      ),
      startedAt: DateTime.fromMillisecondsSinceEpoch(
        (time['started'] as num).toInt(),
      ),
      completedAt: time['completed'] is num
          ? DateTime.fromMillisecondsSinceEpoch(
              (time['completed'] as num).toInt(),
            )
          : null,
      exitCode: (json['exit'] as num?)?.toInt(),
      sessionID: metadata['sessionID'] as String?,
    );
  }

  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      BackgroundWorkSupport.subagentsAndShells;

  @override
  Future<BackgroundWorkResult> backgroundSession(String sessionID) =>
      _guard('Could not background running work', () async {
        await _transport.postJson(
          '/session/${Uri.encodeComponent(sessionID)}/background',
          query: _loc(),
        );
        return BackgroundWorkResult.requested;
      });

  Api2Transport get _transport => client.transport;

  String? get _directory => client.directory;

  @override
  void setLocation({String? directory, String? workspace}) =>
      client.setLocation(directory: directory, workspace: workspace);

  Map<String, dynamic> _loc([Map<String, dynamic> extra = const {}]) => {
    if (client.directory != null) 'location[directory]': client.directory,
    if (client.workspace != null) 'location[workspace]': client.workspace,
    ...extra,
  };

  static Future<T> _guard<T>(
    String message,
    Future<T> Function() action,
  ) async {
    try {
      return await action();
    } on ProductException {
      rethrow;
    } on Api2Error catch (error) {
      final detail = error.message.trim();
      throw ProductException(
        detail.isNotEmpty ? detail : message,
        cause: error,
      );
    } catch (error) {
      throw ProductException(message, cause: error);
    }
  }

  static Map<String, dynamic> _dataMap(dynamic json) {
    final data = json is Map<String, dynamic> ? json['data'] : null;
    return data is Map ? Map<String, dynamic>.from(data) : const {};
  }

  static List<Map<String, dynamic>> _dataMaps(dynamic json) {
    final data = json is Map<String, dynamic> ? json['data'] : json;
    if (data is! List) return const [];
    return [
      for (final item in data)
        if (item is Map) Map<String, dynamic>.from(item),
    ];
  }

  static String _basename(String path) {
    final normalized = path.endsWith('/')
        ? path.substring(0, path.length - 1)
        : path;
    final index = normalized.lastIndexOf('/');
    return index < 0 ? normalized : normalized.substring(index + 1);
  }

  // ---------------- Session operations ----------------

  @override
  Future<Session> getSessionDetails(String id) => _guard(
    'Could not load the session',
    () async => mapApi2Session(await client.session(id)),
  );

  @override
  Future<List<Session>> listSessionChildren(String id) =>
      _guard('Could not load subagent sessions', () async {
        final page = await client.sessions(parentID: id, limit: 200);
        return page.data.map(mapApi2Session).toList();
      });

  @override
  Future<String?> shareSession(String id) => Future.error(
    const ProductException('Session sharing is unavailable on this server'),
  );

  @override
  Future<void> unshareSession(String id) => Future.error(
    const ProductException('Session sharing is unavailable on this server'),
  );

  @override
  Future<void> archiveSession(String id) => Future.error(
    const ProductException('Session archiving is unavailable on this server'),
  );

  @override
  Future<String> forkSession(String id, {String? messageID}) =>
      _guard('Could not fork the session', () async {
        // Lossy: v1 forked "at" a message. The v2 boundary union offers
        // {type: "before", messageID} (exclusive) or {type: "through"}
        // (everything); an anchored fork therefore excludes the anchor
        // message itself.
        final json = await _transport.postJson(
          '/session/$id/fork',
          body: {
            'boundary': messageID != null && messageID.isNotEmpty
                ? {'type': 'before', 'messageID': messageID}
                : {'type': 'through'},
          },
        );
        final forked = Api2Session.fromJson(_dataMap(json));
        if (forked == null) {
          throw const ProductException('OpenCode returned no forked session');
        }
        return forked.id;
      });

  @override
  Future<void> revertSession(String id, String messageID) async {
    await stageSessionRevert(id, messageID, applyFiles: true);
  }

  @override
  Future<void> restoreSession(String id) => clearSessionRevert(id);

  @override
  Future<String?> sessionRevertPrompt(String sessionID, String messageID) =>
      _guard('Could not load the prompt', () async {
        final message = await client.message(sessionID, messageID);
        if (message.id != messageID || message is! Api2UserMessage) return null;
        return message.text.isNotEmpty
            ? message.text
            : message.files
                  .map((file) => file.name ?? '')
                  .where((name) => name.isNotEmpty)
                  .join(', ');
      });

  @override
  Future<SessionRevert> stageSessionRevert(
    String sessionID,
    String messageID, {
    required bool applyFiles,
  }) => _guard('Could not stage the revert', () async {
    final json = await _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/stage',
      body: {'messageID': messageID, 'files': applyFiles},
    );
    final revert = Api2SessionRevert.fromJson(_dataMap(json));
    if (revert == null) {
      throw const ProductException('OpenCode returned no staged boundary.');
    }
    return mapApi2Revert(revert);
  });

  @override
  Future<void> clearSessionRevert(String sessionID) => _guard(
    'Could not clear the staged revert',
    () => _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/clear',
    ),
  );

  @override
  Future<void> commitSessionRevert(String sessionID) => _guard(
    'Could not commit the staged revert',
    () => _transport.postJson(
      '/session/${Uri.encodeComponent(sessionID)}/revert/commit',
    ),
  );

  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) => _guard(
    'Could not compact the session',
    // Lossy: v2 compaction has no provider/model pair; the session's own
    // compaction agent handles it.
    () => _transport.postJson('/session/$id/compact', body: const {}),
  );

  @override
  Future<void> addSessionLocationReminder(String sessionID, String directory) =>
      _guard('Could not update the session location context', () async {
        await _transport.postJson(
          '/session/$sessionID/synthetic',
          body: {
            'text':
                '<system-reminder>The user has changed the current working '
                'directory to "$directory". This is still the same project '
                'but at a possibly new location; take this into account when '
                'working with any files from now on.</system-reminder>',
            'resume': false,
          },
        );
      });

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

  // ---------------- VCS & project health ----------------

  Future<dynamic> _optionalVcsJson(String path) async {
    try {
      return await _transport.getJson(path, query: _loc());
    } on Api2Error {
      return null;
    }
  }

  @override
  Future<VersionControlHealth> loadVersionControlHealth() =>
      _guard('Could not load version control status', () async {
        final vcsFuture = _optionalVcsJson('/vcs');
        final statusFuture = _optionalVcsJson('/vcs/status');
        final projectFuture = () async {
          try {
            return await _currentProjectJson();
          } catch (_) {
            // VCS truth is still useful without project metadata.
            return const <String, dynamic>{};
          }
        }();
        final (vcs, status, project) = await waitForRequests(
          vcsFuture,
          statusFuture,
          projectFuture,
        );
        final info = _dataMap(vcs);
        final changes = [
          for (final item in _dataMaps(status)) mapVcsStatusJson(item),
        ];
        final branch = info['branch'];
        final current = branch is Map ? branch['current']?.toString() : null;
        final fallback = branch is Map ? branch['default']?.toString() : null;
        return VersionControlHealth(
          branch: current,
          defaultBranch: fallback,
          changes: changes,
          // `/project/current` omits `vcs` on beta-18600 (only the project
          // list rows carry it), so live branch data is the primary signal.
          setupState:
              project['vcs']?.toString() == 'git' ||
                  current?.isNotEmpty == true ||
                  fallback?.isNotEmpty == true
              ? VersionControlSetupState.git
              : project.containsKey('id')
              ? VersionControlSetupState.absent
              : VersionControlSetupState.unknown,
        );
      });

  @override
  Future<List<VersionControlFile>> listFileStatuses() =>
      _guard('Could not load file statuses', () async {
        final json = await _transport.getJson('/vcs/status', query: _loc());
        return [for (final item in _dataMaps(json)) mapVcsStatusJson(item)];
      });

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) =>
      _guard('Could not load the diff', () async {
        final json = await _transport.getJson(
          '/vcs/diff',
          query: _loc({
            'mode': switch (mode) {
              VcsDiffMode.workingTree => 'working',
              VcsDiffMode.branch => 'branch',
            },
          }),
        );
        return [for (final item in _dataMaps(json)) mapVcsDiffJson(item)];
      });

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() async =>
      // No LSP status endpoint in v2 (capability languageServiceStatus:
      // false).
      const [];

  @override
  Future<List<FormatterHealth>> listFormatters() async =>
      // No formatter status endpoint in v2 (capability formatterStatus:
      // false).
      const [];

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) async =>
      // No symbol search in v2 (capability workspaceSymbols: false).
      const [];

  // ---------------- Terminals (PTY) ----------------

  @override
  Future<List<TerminalProcess>> listTerminals() =>
      _guard('Could not load terminals', () async {
        final json = await _transport.getJson('/pty', query: _loc());
        return [for (final item in _dataMaps(json)) _terminal(item)];
      });

  TerminalProcess _terminal(Map<String, dynamic> json) => TerminalProcess(
    id: (json['id'] ?? '').toString(),
    title: (json['title'] ?? '').toString(),
    command: (json['command'] ?? '').toString(),
    arguments: json['args'] is List
        ? (json['args'] as List).map((value) => value.toString()).toList()
        : const [],
    directory: (json['cwd'] ?? '').toString(),
    running: (json['status'] ?? '').toString() == 'running',
    pid: (json['pid'] as num?)?.toInt() ?? 0,
    exitCode: (json['exitCode'] as num?)?.toInt(),
  );

  @override
  Future<TerminalProcess> createTerminal({String? title}) =>
      _guard('Could not create the terminal', () async {
        final json = await _transport.postJson(
          '/pty',
          query: _loc(),
          body: {'title': ?title},
        );
        final data = _dataMap(json);
        if (data.isEmpty) {
          throw const ProductException('OpenCode returned no terminal');
        }
        return _terminal(data);
      });

  @override
  Future<void> renameTerminal(String id, String title) => _guard(
    'Could not rename the terminal',
    () => _transport.putJson('/pty/$id', body: {'title': title}),
  );

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) => _guard(
    'Could not resize the terminal',
    () => _transport.putJson(
      '/pty/$id',
      body: {
        'size': {'rows': rows, 'cols': cols},
      },
    ),
  );

  @override
  Future<void> removeTerminal(String id) => _guard(
    'Could not close the terminal',
    () => _transport.deleteJson('/pty/$id'),
  );

  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor}) =>
      _guard('Could not connect to the terminal', () async {
        final response = await _transport.dio.post<dynamic>(
          '/pty/$id/connect-token',
          queryParameters: _loc(),
          options: Options(headers: {'x-opencode-ticket': '1'}),
        );
        final ticket = _dataMap(response.data)['ticket']?.toString();
        if (ticket == null || ticket.isEmpty) {
          throw const ProductException(
            'OpenCode returned no terminal connect ticket',
          );
        }
        final base = Uri.parse('${_transport.apiBase}/pty/$id/connect');
        final uri = base.replace(
          scheme: base.scheme == 'https' ? 'wss' : 'ws',
          queryParameters: {
            'ticket': ticket,
            if (cursor != null) 'cursor': '$cursor',
            'location[directory]': ?_directory,
          },
        );
        final socket = await WebSocket.connect(uri.toString());
        return _Api2TerminalChannel(socket, initialCursor: cursor ?? 0);
      });

  // ---------------- Catalog ----------------

  @override
  Future<CatalogSnapshot> loadCatalog() =>
      _guard('Could not load models and agents', () async {
        final (providers, models, agents) = await waitForRequests(
          client.providers(),
          client.models(),
          client.agents(),
        );
        return CatalogSnapshot(
          providers: providers.map(mapApi2CatalogProvider).toList(),
          models: models.map(mapApi2CatalogModel).toList(),
          agents: agents.map(mapApi2CatalogAgent).toList(),
        );
      });

  @override
  Future<ChatDefaults> loadChatDefaults() =>
      _guard('Could not load chat defaults', () async {
        final entries = await client.config();
        ModelRef? model;
        String? agent;
        // Entries are lowest→highest priority; the last hit wins.
        for (final entry in entries) {
          final wireModel = entry.info['model']?.toString().trim();
          if (wireModel != null && wireModel.isNotEmpty) {
            final slash = wireModel.indexOf('/');
            if (slash > 0 && slash < wireModel.length - 1) {
              // Model reference strings parse as provider/model[#variant].
              final rest = wireModel.substring(slash + 1);
              final hash = rest.indexOf('#');
              model = ModelRef(
                providerID: wireModel.substring(0, slash),
                modelID: hash > 0 ? rest.substring(0, hash) : rest,
              );
            }
          }
          final wireAgent = entry.info['default_agent']?.toString().trim();
          if (wireAgent != null && wireAgent.isNotEmpty) agent = wireAgent;
        }
        return ChatDefaults(model: model, agent: agent);
      });

  @override
  Future<List<CommandInfo>> listCommands() =>
      _guard('Could not load commands', () async {
        final commands = await client.commands();
        return [
          for (final command in commands)
            CommandInfo(
              name: command.name,
              description: command.description,
              agent: null,
              subtask: false,
            ),
        ];
      });

  @override
  Future<List<SkillInfo>> listSkills() =>
      _guard('Could not load skills', () async {
        final skills = await client.skills();
        return [
          for (final skill in skills)
            SkillInfo(
              name: skill.name ?? skill.id,
              description: skill.description,
              location: skill.location ?? '',
              content: skill.content ?? '',
              slashCommand: skill.slash,
            ),
        ];
      });

  @override
  Future<List<ReferenceInfo>> listReferences() =>
      _guard('Could not load references', () async {
        final json = await _transport.getJson('/reference', query: _loc());
        return [
          for (final item in _dataMaps(json))
            if (item['hidden'] != true &&
                (item['name'] ?? '').toString().isNotEmpty)
              ReferenceInfo(
                name: item['name'].toString(),
                path: (item['path'] ?? item['location'] ?? '').toString(),
                description: item['description']?.toString(),
              ),
        ];
      });

  // ---------------- MCP ----------------

  @override
  Future<List<McpServerInfo>> listMcpServers() => _guard(
    'Could not load MCP servers',
    () async {
      final json = await _transport.getJson('/mcp', query: _loc());
      return [
        for (final item in _dataMaps(json))
          if ((item['name'] ?? '').toString().isNotEmpty)
            McpServerInfo(
              name: item['name'].toString(),
              status: item['status'] is Map
                  ? ((item['status'] as Map)['status'] ?? 'unknown').toString()
                  : (item['status'] ?? 'unknown').toString(),
              error: item['status'] is Map
                  ? (item['status'] as Map)['error']?.toString()
                  : null,
            ),
      ];
    },
  );

  @override
  Future<List<McpResourceInfo>> listMcpResources() =>
      _guard('Could not load MCP resources', () async {
        final json = await _transport.getJson('/mcp/resource', query: _loc());
        final data = _dataMap(json);
        final resources = data['resources'];
        if (resources is! List) return const [];
        return [
          for (final item in resources)
            if (item is Map && (item['name'] ?? '').toString().isNotEmpty)
              McpResourceInfo(
                name: item['name'].toString(),
                server: (item['server'] ?? '').toString(),
                uri: (item['uri'] ?? '').toString(),
                description: item['description']?.toString(),
                mimeType: item['mimeType']?.toString(),
              ),
        ];
      });

  @override
  Future<void> connectMcp(String name) => _guard(
    'Could not connect the MCP server',
    () => _transport.postJson(
      '/mcp/${Uri.encodeComponent(name)}/connect',
      query: _loc(),
    ),
  );

  @override
  Future<void> disconnectMcp(String name) => _guard(
    'Could not disconnect the MCP server',
    () => _transport.postJson(
      '/mcp/${Uri.encodeComponent(name)}/disconnect',
      query: _loc(),
    ),
  );

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) => Future.error(
    const ProductException('MCP authentication is unavailable on this server'),
  );

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => _guard('Could not add the MCP server', () async {
    // Lossy: v2's runtime MCP write has no project/global scope split; the
    // server persists it in its own configuration layer.
    final config = draft.toConfigJson();
    if (draft.timeoutMs case final timeout?) {
      config['timeout'] = {
        'startup': timeout,
        'catalog': timeout,
        'execution': timeout,
      };
    }
    await _transport.putJson(
      '/mcp/${Uri.encodeComponent(draft.normalizedName)}',
      query: _loc(),
      body: {'config': config},
    );
  });

  // ---------------- Integrations ----------------

  @override
  Future<List<IntegrationInfo>> listIntegrations() =>
      _guard('Could not load integrations', () async {
        final json = await _transport.getJson('/integration', query: _loc());
        return [
          for (final item in _dataMaps(json))
            if ((item['id'] ?? '').toString().isNotEmpty) _integration(item),
        ];
      });

  IntegrationInfo _integration(Map<String, dynamic> json) {
    final methods = <IntegrationMethodInfo>[];
    if (json['methods'] is List) {
      for (final raw in json['methods'] as List) {
        if (raw is! Map) continue;
        final method = Map<String, dynamic>.from(raw);
        final type = (method['type'] ?? '').toString();
        if (type.isEmpty) continue;
        methods.add(
          IntegrationMethodInfo(
            type: type,
            id: method['id']?.toString(),
            label: (method['label'] ?? type).toString(),
            prompts: method['form'] is List
                ? [
                    for (final field in method['form'] as List)
                      if (field is Map) Map<String, dynamic>.from(field),
                  ]
                : const [],
            environmentNames: method['names'] is List
                ? (method['names'] as List)
                      .map((value) => value.toString())
                      .toList()
                : const [],
          ),
        );
      }
    }
    final connections = <IntegrationConnectionInfo>[];
    if (json['connections'] is List) {
      for (final raw in json['connections'] as List) {
        if (raw is! Map) continue;
        final connection = Map<String, dynamic>.from(raw);
        final type = (connection['type'] ?? '').toString();
        connections.add(
          IntegrationConnectionInfo(
            type: type,
            id: connection['id']?.toString(),
            label: (connection['label'] ?? connection['name'] ?? type)
                .toString(),
          ),
        );
      }
    }
    return IntegrationInfo(
      id: json['id'].toString(),
      name: (json['name'] ?? json['id']).toString(),
      methods: methods,
      connections: connections,
      connectionCount: connections.length,
    );
  }

  @override
  Future<void> connectIntegrationKey(String id, String key, {String? label}) =>
      _guard(
        'Could not connect the integration',
        () => _transport.postJson(
          '/integration/${Uri.encodeComponent(id)}/connect/key',
          query: _loc(),
          body: {'key': key, 'label': ?label},
        ),
      );

  @override
  Future<void> disconnectIntegration(IntegrationInfo integration) =>
      _guard('Could not disconnect the integration', () async {
        final credentialIDs = integration.credentialIDs;
        if (credentialIDs.isEmpty) {
          throw ProductException(
            integration.hasEnvironmentConnection
                ? 'This integration is connected through server environment '
                      'variables and cannot be disconnected from the app'
                : 'This integration has no stored credential to remove',
          );
        }
        for (final credentialID in credentialIDs) {
          await _transport.deleteJson(
            '/credential/${Uri.encodeComponent(credentialID)}',
          );
        }
      });

  @override
  Future<void> refreshProviderRuntime() => Future.error(
    const ProductException(
      'Provider runtime refresh is unavailable on this server',
    ),
  );

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) => _guard('Could not start the sign-in', () async {
    final location = _loc();
    final json = await _transport.postJson(
      '/integration/${Uri.encodeComponent(id)}/connect/oauth',
      query: location,
      body: {
        'methodID': methodID,
        if (inputs.isNotEmpty) 'answer': inputs,
        'label': ?label,
      },
    );
    final data = _dataMap(json);
    final attemptID = (data['attemptID'] ?? '').toString();
    if (attemptID.isEmpty) {
      throw const ProductException('OpenCode returned no sign-in attempt');
    }
    _oauthAttempts[attemptID] = (integrationID: id, location: location);
    _oauthTerminalStatuses.remove(attemptID);
    final time = data['time'];
    return IntegrationAuthLaunch(
      attemptID: attemptID,
      url: (data['url'] ?? '').toString(),
      instructions: (data['instructions'] ?? '').toString(),
      mode: data['mode']?.toString() == 'code'
          ? IntegrationAuthMode.code
          : IntegrationAuthMode.auto,
      expiresAt: time is Map ? (time['expires'] as num?)?.toInt() : null,
    );
  });

  ({String integrationID, Map<String, dynamic> location}) _attempt(
    String attemptID,
  ) {
    final attempt = _oauthAttempts[attemptID];
    if (attempt == null) {
      // Interface friction: the domain contract addresses attempts by ID
      // only, but v2 attempt routes are integration-scoped. Attempts started
      // by an earlier app run cannot be resumed.
      throw const ProductException(
        'This sign-in attempt is no longer tracked — start it again',
      );
    }
    return attempt;
  }

  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) =>
      _guard('Could not check the sign-in status', () async {
        if (_oauthTerminalStatuses[attemptID] case final cached?) return cached;
        final attempt = _attempt(attemptID);
        final json = await _transport.getJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}',
          query: attempt.location,
        );
        final data = _dataMap(json);
        final status =
            (data['status'] ??
                    (json is Map<String, dynamic> ? json['status'] : null))
                ?.toString();
        final result = IntegrationAuthStatus(
          state: switch (status) {
            'complete' => IntegrationAuthState.complete,
            'failed' => IntegrationAuthState.failed,
            'expired' => IntegrationAuthState.expired,
            _ => IntegrationAuthState.pending,
          },
          message: data['message']?.toString(),
        );
        if (result.state != IntegrationAuthState.pending) {
          _oauthAttempts.remove(attemptID);
          _oauthTerminalStatuses[attemptID] = result;
          if (_oauthTerminalStatuses.length > 32) {
            _oauthTerminalStatuses.remove(_oauthTerminalStatuses.keys.first);
          }
        }
        return result;
      });

  @override
  Future<void> completeIntegrationOAuth(String attemptID, {String? code}) =>
      _guard('Could not finish the sign-in', () async {
        if (_oauthTerminalStatuses[attemptID]?.state ==
            IntegrationAuthState.complete) {
          return;
        }
        final attempt = _attempt(attemptID);
        await _transport.postJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}/complete',
          query: attempt.location,
          body: {'code': ?code},
        );
      });

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) =>
      _guard('Could not cancel the sign-in', () async {
        if (_oauthTerminalStatuses.remove(attemptID) != null) return;
        final attempt = _attempt(attemptID);
        await _transport.deleteJson(
          '/integration/${Uri.encodeComponent(attempt.integrationID)}'
          '/connect/oauth/${Uri.encodeComponent(attemptID)}',
          query: attempt.location,
        );
        _oauthAttempts.remove(attemptID);
      });

  // ---------------- Requests (questions & saved permissions) ----------------

  @override
  Future<List<PendingQuestion>> listQuestions() async =>
      // Questions were replaced by forms (capability legacyQuestionRequests:
      // false); the forms UI wires in a later slice.
      const [];

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) =>
      Future.error(
        const ProductException(
          'Question dialogs are unavailable on this server',
        ),
      );

  @override
  Future<void> rejectQuestion(String id) => Future.error(
    const ProductException('Question dialogs are unavailable on this server'),
  );

  @override
  Future<List<SavedPermission>> listSavedPermissions() =>
      _guard('Could not load always allowed actions', () async {
        final project = await loadCurrentProject();
        final projectID = project?.id ?? '';
        final json = await _transport.getJson(
          '/permission/saved',
          query: {'projectID': ?(projectID.isEmpty ? null : projectID)},
        );
        return [
          for (final item in _dataMaps(json))
            if ((item['id'] ?? '').toString().isNotEmpty)
              SavedPermission(
                id: item['id'].toString(),
                projectID: (item['projectID'] ?? '').toString(),
                action: (item['action'] ?? '').toString(),
                resource: (item['resource'] ?? '').toString(),
              ),
        ];
      });

  @override
  Future<void> removeSavedPermission(String id) =>
      _guard('Could not revoke the always allowed action', () async {
        if (id.trim().isEmpty) {
          throw const ProductException('Saved permission ID is missing');
        }
        await _transport.deleteJson(
          '/permission/saved/${Uri.encodeComponent(id)}',
        );
      });
}

/// PTY WebSocket channel: server→client binary frames are raw terminal
/// bytes, except a meta frame starting with 0x00 carrying `{"cursor": n}`;
/// client→server frames are raw input text.
class _Api2TerminalChannel implements TerminalChannel {
  final WebSocket _socket;
  int _cursor;
  late final Stream<String> _output = _socket
      .expand(_decodeFrame)
      .asBroadcastStream();

  _Api2TerminalChannel(this._socket, {required int initialCursor})
    : _cursor = initialCursor;

  Iterable<String> _decodeFrame(dynamic data) sync* {
    if (data is List<int> && data.isNotEmpty && data.first == 0) {
      try {
        final metadata = jsonDecode(utf8.decode(data.sublist(1)));
        final next = metadata is Map<String, dynamic>
            ? metadata['cursor']
            : null;
        if (next is int && next >= 0) _cursor = next;
      } catch (_) {
        // Invalid control frames are transport metadata, never terminal text.
      }
      return;
    }
    final text = data is String
        ? data
        : data is List<int>
        ? utf8.decode(data, allowMalformed: true)
        : data.toString();
    if (text.isEmpty) return;
    _cursor += data is List<int> ? data.length : utf8.encode(text).length;
    yield text;
  }

  @override
  Stream<String> get output => _output;

  @override
  int get cursor => _cursor;

  @override
  void write(String value) => _socket.add(value);

  @override
  Future<void> close() => _socket.close();
}

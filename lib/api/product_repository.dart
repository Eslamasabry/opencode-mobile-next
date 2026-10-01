import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:opencode_sdk/opencode_sdk.dart' as sdk;

import '../domain/server_gateway.dart';
import '../ui/kit/kit_redact.dart';
import '../domain/session_command_handoff.dart';
import '../domain/session_title_text.dart';
import '../domain/parallel_requests.dart';
import 'mcp_oauth.dart';
import 'models.dart';

export '../domain/server_gateway.dart' hide LiveEventChannel, StreamStatus;

part 'product_repository/ops_workspaces.dart';
part 'product_repository/ops_dev.dart';
part 'product_repository/ops_catalog.dart';
part 'product_repository/ops_integrations.dart';
part 'product_repository/ops_session.dart';

final RegExp _exactSemanticVersion = RegExp(
  r'^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)'
  r'(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?'
  r'(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$',
);

bool isExactServerVersion(String value) =>
    value.isNotEmpty && _exactSemanticVersion.hasMatch(value);

extension on VcsDiffMode {
  String get wireValue => switch (this) {
    VcsDiffMode.workingTree => 'git',
    VcsDiffMode.branch => 'branch',
  };
}

abstract class ProductRepository implements ServerOperationsGateway {
  @override
  Future<ManagedShell> startManagedShell({
    required String command,
    required String directory,
    required String ownerToken,
  }) => Future.error(const ProductException('Service control is unavailable'));

  @override
  Future<ManagedShellList> loadRunningShells() async =>
      const ManagedShellList(supported: false);

  @override
  Future<ManagedShell?> getManagedShell(String id) async => null;

  @override
  Future<String?> managedShellServerIdentity() async => null;

  @override
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  }) => Future.error(const ProductException('Shell management is unavailable'));

  @override
  Future<void> stopManagedShell(String id) =>
      Future.error(const ProductException('Shell management is unavailable'));

  @override
  Future<ManagedShell> setManagedShellTimeout(String id, Duration? timeout) =>
      Future.error(const ProductException('Shell management is unavailable'));

  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      BackgroundWorkSupport.unavailable;

  @override
  Future<BackgroundWorkResult> backgroundSession(String sessionID) =>
      Future.error(
        const ProductException('Background work is unavailable on this server'),
      );

  @override
  void setLocation({String? directory, String? workspace});
  @override
  Future<String> upgradeServer(String target) => Future.error(
    const ProductException(
      'Remote OpenCode upgrade is unavailable on this server',
    ),
  );
  @override
  Future<void> writeClientLog({
    required String message,
    Map<String, Object?> extra = const {},
  }) => Future.error(
    const ProductException(
      'Sending app diagnostics is unavailable on this server',
    ),
  );
  @override
  Future<List<WorkspaceProject>> listProjects();
  @override
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) => Future.error(
    const ProductException('Project renaming is unavailable on this server'),
  );
  @override
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  @override
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  @override
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  @override
  Future<List<VersionControlFile>> listWorktreeFileStatuses(String directory) =>
      Future.error(
        const ProductException('Worktree status is unavailable on this server'),
      );
  @override
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  @override
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  @override
  Future<List<WorkspaceInfo>> listWorkspaces();
  @override
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  }) => listWorkspaces();
  @override
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  }) => Future.error(
    const ProductException(
      'Workspace management is unavailable on this server',
    ),
  );
  @override
  Future<void> syncWorkspaceList({required String projectDirectory}) =>
      Future.error(
        const ProductException(
          'Workspace discovery is unavailable on this server',
        ),
      );
  @override
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  }) => Future.error(
    const ProductException('Workspace creation is unavailable on this server'),
  );
  @override
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) => Future.error(
    const ProductException('Workspace removal is unavailable on this server'),
  );
  @override
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    String? cursor,
    int limit = 50,
  }) => Future.error(
    const ProductException(
      'All-project session search is unavailable on this server',
    ),
  );
  @override
  Future<Session> getSessionDetails(String id) => Future.error(
    const ProductException('Session navigation is unavailable on this server'),
  );
  @override
  Future<List<Session>> listSessionChildren(String id) => Future.error(
    const ProductException('Subagent sessions are unavailable on this server'),
  );
  @override
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String projectID) =>
      Future.error(
        const ProductException(
          'Project directory discovery is unavailable on this server',
        ),
      );
  @override
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) => Future.error(
    const ProductException('Moving sessions is unavailable on this server'),
  );
  @override
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  }) => Future.error(
    const ProductException('Workspace warp is unavailable on this server'),
  );

  /// Asks the server to start its sync loops for workspaces in the current
  /// project that have active sessions. Returns the server's own boolean.
  @override
  Future<bool> startWorkspaceSync() => Future.error(
    const ProductException('Workspace sync is unavailable on this server'),
  );

  /// Reassigns [sessionID] to the currently selected workspace through the
  /// server's sync event system and returns the server-confirmed session ID.
  @override
  Future<String> stealSessionIntoWorkspace(String sessionID) => Future.error(
    const ProductException('Session steal is unavailable on this server'),
  );
  @override
  Future<List<ConsoleOrganization>> listConsoleOrganizations() => Future.error(
    const ProductException(
      'Organization switching is unavailable on this server',
    ),
  );
  @override
  Future<void> switchConsoleOrganization(ConsoleOrganization organization) =>
      Future.error(
        const ProductException(
          'Organization switching is unavailable on this server',
        ),
      );
  @override
  Future<void> addSessionLocationReminder(String sessionID, String directory) =>
      Future.value();
  @override
  Future<VersionControlHealth> loadVersionControlHealth();
  @override
  Future<void> initializeGitRepository() => Future.error(
    const ProductException('Git initialization is unavailable on this server'),
  );
  @override
  Future<List<VersionControlFile>> listFileStatuses();
  @override
  Future<List<LanguageServiceHealth>> listLanguageServices();
  @override
  Future<List<FormatterHealth>> listFormatters();
  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query);
  @override
  Future<List<TerminalProcess>> listTerminals();
  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() => Future.error(
    const ProductException('Shell settings are unavailable on this server'),
  );
  @override
  Future<void> selectTerminalShell(String value) => Future.error(
    const ProductException('Shell settings are unavailable on this server'),
  );
  @override
  Future<TerminalProcess> createTerminal({String? title});
  @override
  Future<void> renameTerminal(String id, String title);
  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  });
  @override
  Future<void> removeTerminal(String id);
  @override
  Future<TerminalChannel> connectTerminal(String id, {int? cursor});
  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode);
  @override
  Future<CatalogSnapshot> loadCatalog();
  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() =>
      Future.error(
        const ProductException(
          'Experimental capability discovery is unavailable on this server',
        ),
      );
  @override
  Future<List<String>> listCodingToolIDs() => Future.error(
    const ProductException('Tool discovery is unavailable on this server'),
  );
  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) => Future.error(
    const ProductException('Tool discovery is unavailable on this server'),
  );
  @override
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  @override
  Future<List<McpServerInfo>> listMcpServers();
  @override
  Future<List<McpResourceInfo>> listMcpResources();
  @override
  Future<void> connectMcp(String name);
  @override
  Future<void> disconnectMcp(String name);
  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name);
  @override
  Future<McpServerInfo> completeMcpAuthentication(String name, String code) =>
      Future.error(
        const ProductException(
          'Completing MCP authentication is unavailable on this server',
        ),
      );
  @override
  Future<void> cancelMcpAuthentication(String name) => Future.error(
    const ProductException(
      'Cancelling MCP authentication is unavailable on this server',
    ),
  );
  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => Future.error(
    const ProductException(
      'Persistent MCP setup is unavailable on this server',
    ),
  );
  @override
  Future<List<IntegrationInfo>> listIntegrations();
  @override
  Future<void> connectIntegrationKey(String id, String key, {String? label});
  @override
  Future<void> disconnectIntegration(IntegrationInfo integration);
  @override
  Future<void> refreshProviderRuntime();
  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs,
    String? label,
  });
  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID);
  @override
  Future<void> completeIntegrationOAuth(String attemptID, {String? code});
  @override
  Future<void> cancelIntegrationOAuth(String attemptID);
  @override
  Future<List<CommandInfo>> listCommands();
  @override
  Future<List<SkillInfo>> listSkills();
  @override
  Future<List<ReferenceInfo>> listReferences();
  @override
  Future<List<PendingQuestion>> listQuestions();
  @override
  Future<List<SavedPermission>> listSavedPermissions() => Future.error(
    const ProductException(
      'Saved permission management is unavailable on this server',
    ),
  );
  @override
  Future<void> removeSavedPermission(String id) => Future.error(
    const ProductException(
      'Saved permission management is unavailable on this server',
    ),
  );
  @override
  Future<void> answerQuestion(String id, List<List<String>> answers);
  @override
  Future<void> rejectQuestion(String id);
  @override
  Future<String?> shareSession(String id);
  @override
  Future<void> unshareSession(String id);
  @override
  Future<void> archiveSession(String id);
  @override
  Future<String> forkSession(String id, {String? messageID});

  /// Permanently removes one message and all of its parts from the session's
  /// stored conversation, so future replies no longer see it. File changes
  /// that message made are not reverted.
  @override
  Future<void> deleteMessage({
    required String sessionID,
    required String messageID,
  }) => Future.error(
    const ProductException('Message deletion is unavailable on this server'),
  );

  @override
  Future<void> revertSession(String id, String messageID);
  @override
  Future<void> restoreSession(String id);
  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  });
}

class SdkProductRepository extends _SdkCore
    with
        _SdkWorkspaceOps,
        _SdkDevOps,
        _SdkCatalogOps,
        _SdkIntegrationOps,
        _SdkSessionOps
    implements LocationAwareProductRepository, SessionCommandHandoffGateway {
  SdkProductRepository(super.client);
}

const _providerOAuthAttemptPrefix = 'provider-oauth-';

/// Shared state for the operation mixins in `product_repository/`.
abstract class _SdkCore extends ProductRepository
    implements LocationAwareProductRepository, SessionCommandHandoffGateway {
  final sdk.OpencodeSdk _client;
  final Map<String, _LegacyProviderOAuthAttempt> _providerOAuthAttempts = {};
  String? _directory;
  String? _workspace;
  int _locationRevision = 0;
  int _providerOAuthAttemptSerial = 0;

  _SdkCore(this._client);

  @override
  SessionCommandHandoff createSessionCommandHandoff({
    required String sessionID,
    required String? directory,
    required String? workspaceID,
    required String username,
  }) => SessionCommandHandoff.openCode1(
    serverURL: _client.dio.options.baseUrl,
    sessionID: sessionID,
    directory: directory,
    workspaceID: workspaceID ?? _workspace,
    username: username,
  );

  @override
  int get locationRevision => _locationRevision;

  @override
  void setLocation({String? directory, String? workspace}) {
    if (_directory == directory && _workspace == workspace) return;
    _directory = directory;
    _workspace = workspace;
    _locationRevision++;
  }

  @override
  Future<String> upgradeServer(String target) =>
      _guard('Could not upgrade OpenCode', () async {
        final exactTarget = target.trim();
        if (target != exactTarget || !isExactServerVersion(exactTarget)) {
          throw const ProductException(
            'OpenCode supplied an invalid update version',
          );
        }
        final response = await _client.getGlobalApi().globalUpgrade(
          globalUpgradeRequest: sdk.GlobalUpgradeRequest(target: exactTarget),
        );
        final result = response.data?.objectValue;
        if (result == null) {
          throw const ProductException(
            'OpenCode returned an invalid upgrade result',
          );
        }
        if (result['success'] != true) {
          throw ProductException('Could not upgrade OpenCode', cause: result);
        }
        final installed = result['version']?.toString().trim() ?? '';
        if (installed != exactTarget) {
          throw const ProductException(
            'OpenCode did not confirm the requested version',
          );
        }
        return installed;
      });

  @override
  Future<void> writeClientLog({
    required String message,
    Map<String, Object?> extra = const {},
  }) => _guard('Could not send diagnostics to OpenCode', () async {
    final response = await _client.getControlApi().appLog(
      directory: _directory,
      workspace: _workspace,
      appLogRequest: sdk.AppLogRequest(
        service: 'opencode-mobile',
        level: sdk.AppLogRequestLevelEnum.error,
        message: message,
        extra: extra,
      ),
    );
    if (response.data != true) {
      throw const ProductException(
        'OpenCode did not accept the diagnostics report',
      );
    }
  });

  String _symbolPath(String value) {
    var path = value;
    final uri = Uri.tryParse(value);
    if (uri?.scheme == 'file') {
      try {
        // Server URIs use forward slashes, regardless of the client's OS.
        path = uri!.toFilePath(windows: false);
      } on UnsupportedError {
        path = Uri.decodeComponent(uri!.path);
      }
    }
    final directory = _directory;
    if (directory != null) {
      final normalizedDirectory = directory.endsWith('/')
          ? directory
          : '$directory/';
      if (path.startsWith(normalizedDirectory)) {
        path = path.substring(normalizedDirectory.length);
      }
    }
    return path.split('/').where((part) => part.isNotEmpty).join('/');
  }
}

TerminalProcess _terminal(sdk.Pty process) => TerminalProcess(
  id: process.id,
  title: process.title,
  command: process.command,
  arguments: process.args,
  directory: process.cwd,
  running: process.status == sdk.PtyStatusEnum.running,
  pid: process.pid,
  exitCode: process.exitCode,
);

String _basename(String path) {
  final segments = path.split('/').where((part) => part.isNotEmpty).toList();
  return segments.isEmpty ? path : segments.last;
}

Map<String, dynamic> _stringMap(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : const {};

String _methodLabel(String type) => switch (type) {
  'key' => 'API key',
  'oauth' => 'OAuth',
  'env' => 'Server environment',
  _ => type,
};

McpServerInfo _mcpServerInfo(String name, sdk.MCPStatus status) {
  final data = status.objectValue ?? const <String, dynamic>{};
  return McpServerInfo(
    name: name,
    status: (data['status'] ?? 'unknown').toString(),
    error: data['error']?.toString(),
  );
}

String _requiredWorktreeDirectory(String value, String label) {
  final trimmed = value.trim();
  if (trimmed.isEmpty) {
    throw ProductException('Select a valid $label directory');
  }
  return trimmed;
}

String _requiredSandboxDirectory(String root, String value) {
  final target = _requiredWorktreeDirectory(value, 'worktree');
  if (target == root) {
    throw const ProductException(
      'The primary project directory cannot be reset or removed',
    );
  }
  return target;
}

Future<T> _guardWorktree<T>(String message, Future<T> Function() action) async {
  try {
    return await action();
  } on ProductException {
    rethrow;
  } catch (error) {
    // A typed server payload is still untrusted prose. Preserve its full
    // cause for redacted Details, never as the authored product message.
    throw ProductException(message, cause: error);
  }
}

Future<T> _guard<T>(String message, Future<T> Function() action) async {
  try {
    return await action();
  } on ProductException {
    rethrow;
  } catch (error) {
    throw ProductException(message, cause: error);
  }
}

class _LegacyProviderOAuthAttempt {
  final String providerID;
  final int methodIndex;
  final IntegrationAuthMode mode;
  IntegrationAuthStatus status = const IntegrationAuthStatus(
    state: IntegrationAuthState.pending,
  );
  Future<IntegrationAuthStatus>? completion;

  _LegacyProviderOAuthAttempt({
    required this.providerID,
    required this.methodIndex,
    required this.mode,
  });
}

Session _sessionFromSdk(sdk.Session item) => Session(
  id: item.id,
  title: item.title,
  projectID: item.projectID,
  workspaceID: item.workspaceID,
  parentID: item.parentID,
  directory: item.directory,
  path: item.path,
  reverted: item.revert != null,
  shareUrl: item.share?.url,
  time: SessionTime(
    created: item.time.created,
    updated: item.time.updated,
    archived: item.time.archived?.toInt(),
  ),
);

Session _sessionFromGlobalSdk(sdk.GlobalSession item) => Session(
  id: item.id,
  title: item.title,
  projectID: item.projectID,
  workspaceID: item.workspaceID,
  parentID: item.parentID,
  directory: item.directory,
  path: item.path,
  reverted: item.revert != null,
  shareUrl: item.share?.url,
  time: SessionTime(
    created: item.time.created,
    updated: item.time.updated,
    archived: item.time.archived?.toInt(),
  ),
);

class _IoTerminalChannel implements TerminalChannel {
  final WebSocket _socket;
  int _cursor;
  late final Stream<String> _output = _socket
      .expand(_decodeFrame)
      .asBroadcastStream();

  _IoTerminalChannel(this._socket, {required int initialCursor})
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

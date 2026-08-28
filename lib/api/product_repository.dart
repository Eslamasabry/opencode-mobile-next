import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:opencode_sdk/opencode_sdk.dart' as sdk;

import 'mcp_oauth.dart';
import 'models.dart';

enum VcsDiffMode { workingTree, branch }

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

class WorkspaceProject {
  final String id;
  final String name;
  final String directory;
  final List<String> worktrees;
  final int updatedAt;

  const WorkspaceProject({
    required this.id,
    required this.name,
    required this.directory,
    required this.worktrees,
    required this.updatedAt,
  });
}

class WorkspaceInfo {
  final String id;
  final String projectID;
  final String name;
  final String type;
  final String? branch;
  final String? directory;
  final String? status;

  const WorkspaceInfo({
    required this.id,
    required this.projectID,
    required this.name,
    required this.type,
    this.branch,
    this.directory,
    this.status,
  });
}

class WorkspaceAdapterInfo {
  final String type;
  final String name;
  final String description;

  const WorkspaceAdapterInfo({
    required this.type,
    required this.name,
    required this.description,
  });
}

class WorktreeInfo {
  final String name;
  final String directory;
  final String? branch;

  const WorktreeInfo({
    required this.name,
    required this.directory,
    this.branch,
  });
}

class ProjectDirectoryInfo {
  final String directory;
  final String? strategy;

  const ProjectDirectoryInfo({required this.directory, this.strategy});
}

class GlobalSessionResult {
  final Session session;
  final String? projectName;
  final String? projectDirectory;

  const GlobalSessionResult({
    required this.session,
    this.projectName,
    this.projectDirectory,
  });
}

class ConsoleOrganization {
  final String accountID;
  final String accountEmail;
  final String accountUrl;
  final String orgID;
  final String orgName;
  final bool active;

  const ConsoleOrganization({
    required this.accountID,
    required this.accountEmail,
    required this.accountUrl,
    required this.orgID,
    required this.orgName,
    required this.active,
  });
}

enum VersionControlSetupState { git, absent, unknown }

class VersionControlHealth {
  final String? branch;
  final String? defaultBranch;
  final List<VersionControlFile> changes;
  final VersionControlSetupState setupState;

  const VersionControlHealth({
    this.branch,
    this.defaultBranch,
    required this.changes,
    this.setupState = VersionControlSetupState.unknown,
  });

  int get additions => changes.fold(0, (total, file) => total + file.additions);
  int get deletions => changes.fold(0, (total, file) => total + file.deletions);
}

class VersionControlFile {
  final String path;
  final String status;
  final int additions;
  final int deletions;

  const VersionControlFile({
    required this.path,
    required this.status,
    required this.additions,
    required this.deletions,
  });
}

class LanguageServiceHealth {
  final String id;
  final String name;
  final String root;
  final String status;

  const LanguageServiceHealth({
    required this.id,
    required this.name,
    required this.root,
    required this.status,
  });

  bool get connected => status == 'connected';
}

class FormatterHealth {
  final String name;
  final List<String> extensions;
  final bool enabled;

  const FormatterHealth({
    required this.name,
    required this.extensions,
    required this.enabled,
  });
}

class SavedPermission {
  final String id;
  final String projectID;
  final String action;
  final String resource;

  const SavedPermission({
    required this.id,
    required this.projectID,
    required this.action,
    required this.resource,
  });
}

class WorkspaceSymbol {
  final String name;
  final int kind;
  final String path;
  final int line;
  final int column;

  const WorkspaceSymbol({
    required this.name,
    required this.kind,
    required this.path,
    required this.line,
    required this.column,
  });
}

class TerminalProcess {
  final String id;
  final String title;
  final String command;
  final List<String> arguments;
  final String directory;
  final bool running;
  final int pid;
  final int? exitCode;

  const TerminalProcess({
    required this.id,
    required this.title,
    required this.command,
    required this.arguments,
    required this.directory,
    required this.running,
    required this.pid,
    this.exitCode,
  });
}

class TerminalShellOption {
  final String path;
  final String name;
  final bool acceptable;

  const TerminalShellOption({
    required this.path,
    required this.name,
    required this.acceptable,
  });
}

class TerminalShellSettings {
  final String selected;
  final List<TerminalShellOption> options;

  const TerminalShellSettings({required this.selected, required this.options});
}

class CatalogVariant {
  final String id;
  final bool disabled;
  final Map<String, dynamic> options;

  const CatalogVariant({
    required this.id,
    this.disabled = false,
    this.options = const {},
  });

  String? get reasoningEffort {
    final value = options['reasoningEffort'] ?? options['reasoning_effort'];
    return value?.toString();
  }

  bool get isFast {
    final normalizedID = id.toLowerCase();
    final effort = reasoningEffort?.toLowerCase();
    return effort == 'low' ||
        normalizedID == 'fast' ||
        normalizedID.contains('turbo');
  }
}

class CatalogModel {
  final String id;
  final String providerID;
  final String name;
  final String? family;
  final bool enabled;
  final String status;
  final int contextLimit;
  final int outputLimit;
  final bool reasoning;
  final bool attachments;
  final bool tools;
  final List<CatalogVariant> variants;

  const CatalogModel({
    required this.id,
    required this.providerID,
    required this.name,
    this.family,
    required this.enabled,
    required this.status,
    required this.contextLimit,
    required this.outputLimit,
    required this.reasoning,
    required this.attachments,
    required this.tools,
    required this.variants,
  });
}

class CatalogProvider {
  final String id;
  final String name;
  final bool enabled;
  final String? integrationID;

  const CatalogProvider({
    required this.id,
    required this.name,
    required this.enabled,
    this.integrationID,
  });
}

class CatalogAgent {
  final String id;
  final String mode;
  final String? description;
  final bool hidden;
  final int? maxSteps;

  const CatalogAgent({
    required this.id,
    required this.mode,
    this.description,
    required this.hidden,
    this.maxSteps,
  });
}

class CatalogSnapshot {
  final List<CatalogProvider> providers;
  final List<CatalogModel> models;
  final List<CatalogAgent> agents;

  const CatalogSnapshot({
    required this.providers,
    required this.models,
    required this.agents,
  });
}

class ExperimentalServerCapabilities {
  final bool backgroundSubagents;

  const ExperimentalServerCapabilities({required this.backgroundSubagents});
}

class CodingToolInfo {
  final String id;
  final String description;
  final Object? parameters;

  const CodingToolInfo({
    required this.id,
    required this.description,
    required this.parameters,
  });
}

class ChatDefaults {
  final ModelRef? model;
  final String? agent;

  const ChatDefaults({this.model, this.agent});
}

class McpServerInfo {
  final String name;
  final String status;
  final String? error;

  const McpServerInfo({required this.name, required this.status, this.error});
}

enum McpServerKind { remote, local }

enum McpConfigScope { project, global }

class McpServerDraft {
  final String name;
  final McpServerKind kind;
  final String? url;
  final List<String> command;
  final String? cwd;
  final Map<String, String> headers;
  final Map<String, String> environment;
  final bool detectOAuth;
  final int? timeoutMs;

  const McpServerDraft({
    required this.name,
    required this.kind,
    this.url,
    this.command = const [],
    this.cwd,
    this.headers = const {},
    this.environment = const {},
    this.detectOAuth = true,
    this.timeoutMs,
  });

  String get normalizedName => name.trim();

  Map<String, Object?> toConfigJson() {
    final serverName = normalizedName;
    if (serverName.isEmpty || serverName.contains(RegExp(r'[\r\n]'))) {
      throw const ProductException('Enter a valid MCP server name');
    }
    final timeout = timeoutMs;
    if (timeout != null && timeout <= 0) {
      throw const ProductException('MCP timeout must be greater than zero');
    }
    switch (kind) {
      case McpServerKind.remote:
        final value = url?.trim() ?? '';
        final uri = Uri.tryParse(value);
        if (uri == null ||
            !uri.hasScheme ||
            !uri.hasAuthority ||
            (uri.scheme != 'https' && uri.scheme != 'http') ||
            uri.userInfo.isNotEmpty) {
          throw const ProductException(
            'Enter an HTTP or HTTPS MCP server URL without credentials',
          );
        }
        _validatePairs(headers, 'HTTP header');
        return {
          'type': 'remote',
          'url': uri.toString(),
          if (headers.isNotEmpty) 'headers': Map.of(headers),
          if (!detectOAuth) 'oauth': false,
          'timeout': ?timeout,
        };
      case McpServerKind.local:
        final parts = command.map((part) => part.trim()).toList();
        if (parts.isEmpty || parts.any((part) => part.isEmpty)) {
          throw const ProductException(
            'Enter the local command and each argument on its own line',
          );
        }
        _validatePairs(environment, 'environment variable');
        return {
          'type': 'local',
          'command': parts,
          if (cwd?.trim().isNotEmpty == true) 'cwd': cwd!.trim(),
          if (environment.isNotEmpty) 'environment': Map.of(environment),
          'timeout': ?timeout,
        };
    }
  }

  static void _validatePairs(Map<String, String> values, String label) {
    for (final entry in values.entries) {
      if (entry.key.trim().isEmpty ||
          entry.key.contains(RegExp(r'[\r\n=]')) ||
          entry.value.contains(RegExp(r'[\r\n]'))) {
        throw ProductException('Enter a valid $label name and value');
      }
    }
  }
}

class McpResourceInfo {
  final String name;
  final String server;
  final String uri;
  final String? description;
  final String? mimeType;

  const McpResourceInfo({
    required this.name,
    required this.server,
    required this.uri,
    this.description,
    this.mimeType,
  });
}

class IntegrationInfo {
  final String id;
  final String name;
  final List<IntegrationMethodInfo> methods;
  final List<IntegrationConnectionInfo> connections;
  final int connectionCount;

  const IntegrationInfo({
    required this.id,
    required this.name,
    required this.methods,
    this.connections = const [],
    required this.connectionCount,
  });

  List<String> get credentialIDs => connections
      .where((connection) => connection.type == 'credential')
      .map((connection) => connection.id)
      .whereType<String>()
      .where((id) => id.isNotEmpty)
      .toList(growable: false);

  bool get hasEnvironmentConnection =>
      connections.any((connection) => connection.type == 'env');
}

class IntegrationConnectionInfo {
  final String type;
  final String? id;
  final String label;

  const IntegrationConnectionInfo({
    required this.type,
    this.id,
    required this.label,
  });
}

class IntegrationMethodInfo {
  final String type;
  final String? id;
  final String label;
  final List<Map<String, dynamic>> prompts;
  final List<String> environmentNames;

  const IntegrationMethodInfo({
    required this.type,
    this.id,
    required this.label,
    this.prompts = const [],
    this.environmentNames = const [],
  });
}

class IntegrationAuthLaunch {
  final String attemptID;
  final String url;
  final String instructions;
  final IntegrationAuthMode mode;
  final int? expiresAt;

  const IntegrationAuthLaunch({
    required this.attemptID,
    required this.url,
    required this.instructions,
    required this.mode,
    this.expiresAt,
  });
}

enum IntegrationAuthMode { auto, code }

enum IntegrationAuthState { pending, complete, failed, expired }

class IntegrationAuthStatus {
  final IntegrationAuthState state;
  final String? message;
  final int? expiresAt;

  const IntegrationAuthStatus({
    required this.state,
    this.message,
    this.expiresAt,
  });
}

class CommandInfo {
  final String name;
  final String? description;
  final String? agent;
  final bool subtask;

  const CommandInfo({
    required this.name,
    this.description,
    this.agent,
    required this.subtask,
  });
}

class SkillInfo {
  final String name;
  final String? description;
  final String location;
  final String content;
  final bool slashCommand;

  const SkillInfo({
    required this.name,
    this.description,
    required this.location,
    required this.content,
    required this.slashCommand,
  });
}

class ReferenceInfo {
  final String name;
  final String path;
  final String? description;

  const ReferenceInfo({
    required this.name,
    required this.path,
    this.description,
  });
}

class QuestionChoice {
  final String label;
  final String description;

  const QuestionChoice({required this.label, required this.description});
}

class QuestionPrompt {
  final String title;
  final String question;
  final bool multiple;
  final bool custom;
  final List<QuestionChoice> choices;

  const QuestionPrompt({
    required this.title,
    required this.question,
    required this.multiple,
    required this.custom,
    required this.choices,
  });
}

class PendingQuestion {
  final String id;
  final String sessionID;
  final List<QuestionPrompt> prompts;

  const PendingQuestion({
    required this.id,
    required this.sessionID,
    required this.prompts,
  });

  factory PendingQuestion.fromJson(Map<String, dynamic> json) {
    final raw = json['questions'];
    return PendingQuestion(
      id: (json['id'] ?? json['requestID'] ?? '').toString(),
      sessionID: (json['sessionID'] ?? '').toString(),
      prompts: raw is List
          ? raw.whereType<Map>().map((item) {
              final value = Map<String, dynamic>.from(item);
              final options = value['options'];
              return QuestionPrompt(
                title: (value['header'] ?? 'Question').toString(),
                question: (value['question'] ?? '').toString(),
                multiple: value['multiple'] == true,
                custom: value['custom'] != false,
                choices: options is List
                    ? options.whereType<Map>().map((option) {
                        final choice = Map<String, dynamic>.from(option);
                        return QuestionChoice(
                          label: (choice['label'] ?? '').toString(),
                          description: (choice['description'] ?? '').toString(),
                        );
                      }).toList()
                    : const [],
              );
            }).toList()
          : const [],
    );
  }
}

abstract class TerminalChannel {
  Stream<String> get output;
  int? get cursor => null;
  void write(String value);
  Future<void> close();
}

abstract interface class LocationAwareProductRepository {
  int get locationRevision;
}

abstract class ProductRepository {
  void setLocation({String? directory, String? workspace});
  Future<String> upgradeServer(String target) => Future.error(
    const ProductException(
      'Remote OpenCode upgrade is unavailable on this server',
    ),
  );
  Future<void> writeClientLog({
    required String message,
    Map<String, Object?> extra = const {},
  }) => Future.error(
    const ProductException(
      'Sending app diagnostics is unavailable on this server',
    ),
  );
  Future<List<WorkspaceProject>> listProjects();
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  }) => Future.error(
    const ProductException('Project renaming is unavailable on this server'),
  );
  Future<WorkspaceProject?> loadCurrentProject() async => null;
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  Future<List<VersionControlFile>> listWorktreeFileStatuses(String directory) =>
      Future.error(
        const ProductException('Worktree status is unavailable on this server'),
      );
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  }) => Future.error(
    const ProductException('Worktree management is unavailable on this server'),
  );
  Future<List<WorkspaceInfo>> listWorkspaces();
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  }) => listWorkspaces();
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  }) => Future.error(
    const ProductException(
      'Workspace management is unavailable on this server',
    ),
  );
  Future<void> syncWorkspaceList({required String projectDirectory}) =>
      Future.error(
        const ProductException(
          'Workspace discovery is unavailable on this server',
        ),
      );
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  }) => Future.error(
    const ProductException('Workspace creation is unavailable on this server'),
  );
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  }) => Future.error(
    const ProductException('Workspace removal is unavailable on this server'),
  );
  Future<List<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    int? cursor,
    int limit = 50,
  }) => Future.error(
    const ProductException(
      'All-project session search is unavailable on this server',
    ),
  );
  Future<Session> getSessionDetails(String id) => Future.error(
    const ProductException('Session navigation is unavailable on this server'),
  );
  Future<List<Session>> listSessionChildren(String id) => Future.error(
    const ProductException('Subagent sessions are unavailable on this server'),
  );
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String projectID) =>
      Future.error(
        const ProductException(
          'Project directory discovery is unavailable on this server',
        ),
      );
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) => Future.error(
    const ProductException('Moving sessions is unavailable on this server'),
  );
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  }) => Future.error(
    const ProductException('Workspace warp is unavailable on this server'),
  );
  Future<List<ConsoleOrganization>> listConsoleOrganizations() => Future.error(
    const ProductException(
      'Organization switching is unavailable on this server',
    ),
  );
  Future<void> switchConsoleOrganization(ConsoleOrganization organization) =>
      Future.error(
        const ProductException(
          'Organization switching is unavailable on this server',
        ),
      );
  Future<void> addSessionLocationReminder(String sessionID, String directory) =>
      Future.value();
  Future<VersionControlHealth> loadVersionControlHealth();
  Future<void> initializeGitRepository() => Future.error(
    const ProductException('Git initialization is unavailable on this server'),
  );
  Future<List<VersionControlFile>> listFileStatuses();
  Future<List<LanguageServiceHealth>> listLanguageServices();
  Future<List<FormatterHealth>> listFormatters();
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query);
  Future<List<TerminalProcess>> listTerminals();
  Future<TerminalShellSettings> loadTerminalShellSettings() => Future.error(
    const ProductException('Shell settings are unavailable on this server'),
  );
  Future<void> selectTerminalShell(String value) => Future.error(
    const ProductException('Shell settings are unavailable on this server'),
  );
  Future<TerminalProcess> createTerminal({String? title});
  Future<void> renameTerminal(String id, String title);
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  });
  Future<void> removeTerminal(String id);
  Future<TerminalChannel> connectTerminal(String id, {int? cursor});
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode);
  Future<CatalogSnapshot> loadCatalog();
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() =>
      Future.error(
        const ProductException(
          'Experimental capability discovery is unavailable on this server',
        ),
      );
  Future<List<String>> listCodingToolIDs() => Future.error(
    const ProductException('Tool discovery is unavailable on this server'),
  );
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) => Future.error(
    const ProductException('Tool discovery is unavailable on this server'),
  );
  Future<ChatDefaults> loadChatDefaults() async => const ChatDefaults();
  Future<List<McpServerInfo>> listMcpServers();
  Future<List<McpResourceInfo>> listMcpResources();
  Future<void> connectMcp(String name);
  Future<void> disconnectMcp(String name);
  Future<McpAuthLaunch> startMcpAuthentication(String name);
  Future<McpServerInfo> completeMcpAuthentication(String name, String code) =>
      Future.error(
        const ProductException(
          'Completing MCP authentication is unavailable on this server',
        ),
      );
  Future<void> cancelMcpAuthentication(String name) => Future.error(
    const ProductException(
      'Cancelling MCP authentication is unavailable on this server',
    ),
  );
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => Future.error(
    const ProductException(
      'Persistent MCP setup is unavailable on this server',
    ),
  );
  Future<List<IntegrationInfo>> listIntegrations();
  Future<void> connectIntegrationKey(String id, String key, {String? label});
  Future<void> disconnectIntegration(IntegrationInfo integration);
  Future<void> refreshProviderRuntime();
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs,
    String? label,
  });
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID);
  Future<void> completeIntegrationOAuth(String attemptID, {String? code});
  Future<void> cancelIntegrationOAuth(String attemptID);
  Future<List<CommandInfo>> listCommands();
  Future<List<SkillInfo>> listSkills();
  Future<List<ReferenceInfo>> listReferences();
  Future<List<PendingQuestion>> listQuestions();
  Future<List<SavedPermission>> listSavedPermissions() => Future.error(
    const ProductException(
      'Saved permission management is unavailable on this server',
    ),
  );
  Future<void> removeSavedPermission(String id) => Future.error(
    const ProductException(
      'Saved permission management is unavailable on this server',
    ),
  );
  Future<void> answerQuestion(String id, List<List<String>> answers);
  Future<void> rejectQuestion(String id);
  Future<String?> shareSession(String id);
  Future<void> unshareSession(String id);
  Future<void> archiveSession(String id);
  Future<String> forkSession(String id, {String? messageID});
  Future<void> revertSession(String id, String messageID);
  Future<void> restoreSession(String id);
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  });
}

class SdkProductRepository
    implements ProductRepository, LocationAwareProductRepository {
  final sdk.OpencodeSdk _client;
  String? _directory;
  String? _workspace;
  int _locationRevision = 0;

  SdkProductRepository(this._client);

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
  Future<String> upgradeServer(String target) => _guard(
    'Could not upgrade OpenCode',
    () async {
      final exactTarget = target.trim();
      if (target != exactTarget || !isExactServerVersion(exactTarget)) {
        throw const ProductException(
          'OpenCode supplied an invalid update version',
        );
      }
      final response = await () async {
        try {
          return await _client.getGlobalApi().globalUpgrade(
            globalUpgradeRequest: sdk.GlobalUpgradeRequest(target: exactTarget),
          );
        } on sdk.OpenCodeApiException catch (error) {
          final payload = error.rawPayload;
          final detail = payload is Map
              ? payload['error']?.toString().trim()
              : null;
          if (detail?.isNotEmpty == true) throw ProductException(detail!);
          rethrow;
        }
      }();
      final result = response.data?.objectValue;
      if (result == null) {
        throw const ProductException(
          'OpenCode returned an invalid upgrade result',
        );
      }
      if (result['success'] != true) {
        final error = result['error']?.toString().trim();
        throw ProductException(
          error?.isNotEmpty == true ? error! : 'OpenCode could not upgrade',
        );
      }
      final installed = result['version']?.toString().trim() ?? '';
      if (installed != exactTarget) {
        throw const ProductException(
          'OpenCode did not confirm the requested version',
        );
      }
      return installed;
    },
  );

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

  @override
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode) => _guard(
    mode == VcsDiffMode.workingTree
        ? 'Could not load working tree changes'
        : 'Could not load branch changes',
    () async {
      final response = await _client.getInstanceApi().vcsDiff(
        mode: mode.wireValue,
        directory: _directory,
        workspace: _workspace,
        context: 3,
      );
      return (response.data ?? const [])
          .map(
            (diff) => FileDiff(
              file: diff.file,
              patch: diff.patch_,
              additions: diff.additions.toInt(),
              deletions: diff.deletions.toInt(),
              status: diff.status?.value.toString(),
            ),
          )
          .toList();
    },
  );

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
  Future<List<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived = false,
    int? cursor,
    int limit = 50,
  }) => _guard('Could not search sessions', () async {
    final query = search?.trim();
    // Deliberately omit the repository's selected directory/workspace. This
    // endpoint is the server-wide finder; passing the active directory would
    // silently reduce it to the list the Workspace screen already has.
    final response = await _client.getExperimentalApi().experimentalSessionList(
      roots: sdk.OpencodeSdkRawUnion050(true),
      cursor: cursor,
      search: query?.isNotEmpty == true ? query : null,
      limit: limit,
      archived: sdk.OpencodeSdkRawUnion051(includeArchived),
    );
    return (response.data ?? const [])
        .map(
          (item) => GlobalSessionResult(
            session: _sessionFromGlobalSdk(item),
            projectName: item.project?.name,
            projectDirectory: item.project?.worktree,
          ),
        )
        .toList();
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
          sdk.OpencodeSdkRawUnion085({
            'type': 'text',
            'text':
                '<system-reminder>The user has changed the current working directory to "$directory". This is still the same project but at a possibly new location; take this into account when working with any files from now on.</system-reminder>',
            'synthetic': true,
          }),
        ],
      ),
    );
  });

  @override
  Future<VersionControlHealth> loadVersionControlHealth() =>
      _guard('Could not load version control status', () async {
        final projectFuture = () async {
          try {
            return (await _client.getProjectApi().projectCurrent(
              directory: _directory,
              workspace: _workspace,
            )).data;
          } catch (_) {
            // Older servers can still provide useful VCS truth without the
            // current-project metadata needed to offer Git initialization.
            return null;
          }
        }();
        final responses = await Future.wait([
          _client.getInstanceApi().vcsGet(
            directory: _directory,
            workspace: _workspace,
          ),
          _client.getInstanceApi().vcsStatus(
            directory: _directory,
            workspace: _workspace,
          ),
        ]);
        final info = responses[0].data as sdk.VcsInfo?;
        final status = responses[1].data as List<sdk.VcsFileStatus>?;
        final project = await projectFuture;
        return VersionControlHealth(
          branch: info?.branch,
          defaultBranch: info?.defaultBranch,
          setupState: project?.vcs == sdk.ProjectVcs.git
              ? VersionControlSetupState.git
              : project != null && project.vcs == null
              ? VersionControlSetupState.absent
              : VersionControlSetupState.unknown,
          changes: (status ?? const [])
              .map(
                (file) => VersionControlFile(
                  path: file.file,
                  status: file.status.value.toString(),
                  additions: file.additions.toInt(),
                  deletions: file.deletions.toInt(),
                ),
              )
              .toList(),
        );
      });

  @override
  Future<void> initializeGitRepository() =>
      _guard('Could not initialize this Git repository', () async {
        final response = await _client.getProjectApi().projectInitGit(
          directory: _directory,
          workspace: _workspace,
        );
        if (response.data?.vcs != sdk.ProjectVcs.git) {
          throw const ProductException(
            'OpenCode did not confirm Git initialization',
          );
        }
      });

  @override
  Future<List<VersionControlFile>> listFileStatuses() =>
      _guard('Could not load file changes', () async {
        final response = await _client.getInstanceApi().vcsStatus(
          directory: _directory,
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
            .toList();
      });

  @override
  Future<List<LanguageServiceHealth>> listLanguageServices() =>
      _guard('Could not load language server status', () async {
        final response = await _client.getInstanceApi().lspStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (service) => LanguageServiceHealth(
                id: service.id,
                name: service.name,
                root: service.root,
                status: service.status.value.toString(),
              ),
            )
            .toList();
      });

  @override
  Future<List<FormatterHealth>> listFormatters() =>
      _guard('Could not load formatter status', () async {
        final response = await _client.getInstanceApi().formatterStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (formatter) => FormatterHealth(
                name: formatter.name,
                extensions: formatter.extensions,
                enabled: formatter.enabled,
              ),
            )
            .toList();
      });

  @override
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query) =>
      _guard('Could not search workspace symbols', () async {
        final response = await _client.getFileApi().findSymbols(
          query: query,
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (symbol) => WorkspaceSymbol(
                name: symbol.name,
                kind: symbol.kind,
                path: _symbolPath(symbol.location.uri),
                line: symbol.location.range.start.line + 1,
                column: symbol.location.range.start.character + 1,
              ),
            )
            .where((symbol) => symbol.path.isNotEmpty)
            .toList();
      });

  @override
  Future<List<TerminalProcess>> listTerminals() =>
      _guard('Could not load terminal processes', () async {
        final response = await _client.getPtyApi().ptyList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const []).map(_terminal).toList();
      });

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() => _guard(
    'Could not load shell settings',
    () async {
      final config = await _client.getGlobalApi().globalConfigGet();
      final shells = await _client.getPtyApi().ptyShells(
        directory: _directory,
        workspace: _workspace,
      );
      return TerminalShellSettings(
        selected: config.data?.shell?.trim() ?? '',
        options: (shells.data ?? const [])
            .where(
              (shell) =>
                  shell.path.trim().isNotEmpty && shell.name.trim().isNotEmpty,
            )
            .map(
              (shell) => TerminalShellOption(
                path: shell.path.trim(),
                name: shell.name.trim(),
                acceptable: shell.acceptable,
              ),
            )
            .toList(),
      );
    },
  );

  @override
  Future<void> selectTerminalShell(String value) => _guard(
    'Could not update the default shell',
    () async {
      final normalized = value.trim();
      if (normalized.length > 4096 || normalized.contains(RegExp(r'[\r\n]'))) {
        throw const ProductException('OpenCode returned an invalid shell');
      }
      await _client.getGlobalApi().globalConfigUpdate(
        config: sdk.Config(shell: normalized),
      );
      final confirmed = await _client.getGlobalApi().globalConfigGet();
      if ((confirmed.data?.shell?.trim() ?? '') != normalized) {
        throw const ProductException(
          'OpenCode did not retain the selected default shell',
        );
      }
    },
  );

  @override
  Future<TerminalProcess> createTerminal({String? title}) =>
      _guard('Could not start a terminal', () async {
        final response = await _client.getPtyApi().ptyCreate(
          directory: _directory,
          workspace: _workspace,
          ptyCreateRequest: sdk.PtyCreateRequest(title: title),
        );
        final process = response.data;
        if (process == null) {
          throw const ProductException('Server returned no terminal');
        }
        return _terminal(process);
      });

  @override
  Future<void> renameTerminal(String id, String title) =>
      _guard('Could not rename the terminal', () async {
        await _client.getPtyApi().ptyUpdate(
          ptyID: id,
          directory: _directory,
          workspace: _workspace,
          ptyUpdateRequest: sdk.PtyUpdateRequest(title: title),
        );
      });

  @override
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  }) => _guard('Could not resize the terminal', () async {
    await _client.getPtyApi().ptyUpdate(
      ptyID: id,
      directory: _directory,
      workspace: _workspace,
      ptyUpdateRequest: sdk.PtyUpdateRequest(
        size: sdk.PtyUpdateRequestSize(rows: rows, cols: cols),
      ),
    );
  });

  @override
  Future<void> removeTerminal(String id) =>
      _guard('Could not stop the terminal', () async {
        await _client.getPtyApi().ptyRemove(
          ptyID: id,
          directory: _directory,
          workspace: _workspace,
        );
      });

  @override
  Future<TerminalChannel> connectTerminal(
    String id, {
    int? cursor,
  }) => _guard('Could not connect to the terminal', () async {
    // A ticket is scoped to one location. Keep the socket query on that same
    // location even if the user switches workspaces while the request awaits.
    final directory = _directory;
    final workspace = _workspace;
    final token = await _client.getPtyApi().ptyConnectToken(
      ptyID: id,
      directory: directory,
      workspace: workspace,
      // Required by the server's CSRF guard but omitted from its OpenAPI spec.
      headers: const {'x-opencode-ticket': '1'},
    );
    final ticket = token.data?.ticket;
    if (ticket == null) {
      throw const ProductException('Terminal ticket was unavailable');
    }
    final base = Uri.parse(_client.dio.options.baseUrl);
    final query = <String, String>{
      'ticket': ticket,
      if (cursor != null) 'cursor': '$cursor',
    };
    if (directory != null) query['directory'] = directory;
    if (workspace != null) query['workspace'] = workspace;
    final uri = base.replace(
      scheme: base.scheme == 'https' ? 'wss' : 'ws',
      path:
          '${base.path.endsWith('/') ? base.path.substring(0, base.path.length - 1) : base.path}/pty/${Uri.encodeComponent(id)}/connect',
      queryParameters: query,
    );
    return _IoTerminalChannel(
      await WebSocket.connect(uri.toString()),
      initialCursor: cursor ?? 0,
    );
  });

  @override
  Future<ChatDefaults> loadChatDefaults() =>
      _guard('Could not load chat defaults', () async {
        final response = await _client.getConfigApi().configGet(
          directory: _directory,
          workspace: _workspace,
        );
        final config = response.data;
        final wireModel = config?.model?.trim();
        ModelRef? model;
        if (wireModel?.isNotEmpty == true) {
          final slash = wireModel!.indexOf('/');
          if (slash > 0 && slash < wireModel.length - 1) {
            model = ModelRef(
              providerID: wireModel.substring(0, slash),
              modelID: wireModel.substring(slash + 1),
            );
          }
        }
        final agent = config?.defaultAgent?.trim();
        return ChatDefaults(
          model: model,
          agent: agent?.isNotEmpty == true ? agent : null,
        );
      });

  @override
  Future<CatalogSnapshot> loadCatalog() =>
      _guard('Could not load models and agents', () async {
        final providersRequest = _client.getProvidersApi().v2ProviderList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        final modelsRequest = _client.getModelsApi().v2ModelList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        final agentsRequest = _client.getOpencodeHttpApiApi().v2AgentList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        final providerResponse = await providersRequest;
        final modelResponse = await modelsRequest;
        final agentResponse = await agentsRequest;
        final providers = (providerResponse.data?.data ?? const [])
            .map((provider) {
              return CatalogProvider(
                id: provider.id,
                name: provider.name,
                enabled: provider.disabled != true,
                integrationID: provider.integrationID,
              );
            })
            .where((provider) => provider.id.isNotEmpty)
            .toList();
        final models = (modelResponse.data?.data ?? const [])
            .map((model) {
              final variants = model.variants
                  .where((variant) => variant.id.isNotEmpty)
                  .map(
                    (variant) => CatalogVariant(
                      id: variant.id,
                      options: _stringMap(variant.body),
                    ),
                  )
                  .toList();
              return CatalogModel(
                id: model.id,
                providerID: model.providerID,
                name: model.name,
                family: model.family,
                enabled: model.enabled,
                status: model.status.value.toString(),
                contextLimit: model.limit.context,
                outputLimit: model.limit.output,
                reasoning: false,
                attachments: model.capabilities.input.any(
                  (input) => input != 'text',
                ),
                tools: model.capabilities.tools,
                variants: variants,
              );
            })
            .where((model) => model.id.isNotEmpty)
            .toList();
        final agents = (agentResponse.data?.data ?? const [])
            .map((agent) {
              return CatalogAgent(
                id: agent.id,
                mode: agent.mode.value.toString(),
                description: agent.description,
                hidden: agent.hidden,
                maxSteps: agent.steps,
              );
            })
            .where((agent) => agent.id.isNotEmpty)
            .toList();
        return CatalogSnapshot(
          providers: providers,
          models: models,
          agents: agents,
        );
      });

  @override
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities() =>
      _guard('Could not load server capabilities', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalCapabilitiesGet(
              directory: _directory,
              workspace: _workspace,
            );
        final capabilities = response.data;
        if (capabilities == null) {
          throw const ProductException(
            'OpenCode returned no capability information',
          );
        }
        return ExperimentalServerCapabilities(
          backgroundSubagents: capabilities.backgroundSubagents,
        );
      });

  @override
  Future<List<String>> listCodingToolIDs() =>
      _guard('Could not load registered tools', () async {
        final response = await _client.getExperimentalApi().toolIds(
          directory: _directory,
          workspace: _workspace,
        );
        final seen = <String>{};
        return [
          for (final rawID in response.data ?? const <String>[])
            if (rawID.trim().isNotEmpty && seen.add(rawID.trim())) rawID.trim(),
        ];
      });

  @override
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  }) => _guard('Could not load model tools', () async {
    final provider = providerID.trim();
    final model = modelID.trim();
    if (provider.isEmpty || model.isEmpty) {
      throw const ProductException('Choose a valid provider and model');
    }
    final response = await _client.getExperimentalApi().toolList(
      provider: provider,
      model: model,
      directory: _directory,
      workspace: _workspace,
    );
    return [
      for (final tool in response.data ?? const <sdk.ToolListItem>[])
        if (tool.id.trim().isNotEmpty)
          CodingToolInfo(
            id: tool.id.trim(),
            description: tool.description.trim(),
            parameters: tool.parameters,
          ),
    ];
  });

  @override
  Future<List<McpServerInfo>> listMcpServers() =>
      _guard('Could not load MCP servers', () async {
        final response = await _client.getMcpApi().mcpStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const {}).entries
            .map((entry) => _mcpServerInfo(entry.key, entry.value))
            .toList();
      });

  @override
  Future<List<McpResourceInfo>> listMcpResources() =>
      _guard('Could not load MCP resources', () async {
        final response = await _client
            .getExperimentalApi()
            .experimentalResourceList(
              directory: _directory,
              workspace: _workspace,
            );
        return (response.data ?? const {}).values
            .map(
              (resource) => McpResourceInfo(
                name: resource.name,
                server: resource.client,
                uri: resource.uri,
                description: resource.description,
                mimeType: resource.mimeType,
              ),
            )
            .toList();
      });

  @override
  Future<void> connectMcp(String name) => _guard(
    'Could not connect the MCP server',
    () async => _client.getMcpApi().mcpConnect(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> disconnectMcp(String name) => _guard(
    'Could not disconnect the MCP server',
    () async => _client.getMcpApi().mcpDisconnect(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<McpAuthLaunch> startMcpAuthentication(String name) =>
      _guard('Could not start authentication', () async {
        final response = await _client.getMcpApi().mcpAuthStart(
          name: name,
          directory: _directory,
          workspace: _workspace,
        );
        final data = response.data;
        final url = Uri.tryParse(data?.authorizationUrl.trim() ?? '');
        final state = data?.oauthState.trim() ?? '';
        if (url == null || url.toString().isEmpty || state.isEmpty) {
          throw const ProductException('No authorization link was returned');
        }
        return McpAuthLaunch(authorizationUrl: url, oauthState: state);
      });

  @override
  Future<McpServerInfo> completeMcpAuthentication(String name, String code) =>
      _guard('Could not complete authentication', () async {
        final response = await _client.getMcpApi().mcpAuthCallback(
          name: name,
          directory: _directory,
          workspace: _workspace,
          mcpAuthCallbackRequest: sdk.McpAuthCallbackRequest(code: code),
        );
        final status = response.data;
        if (status == null) {
          throw const ProductException(
            'OpenCode did not return the MCP connection status',
          );
        }
        return _mcpServerInfo(name, status);
      });

  @override
  Future<void> cancelMcpAuthentication(String name) => _guard(
    'Could not cancel authentication',
    () => _client.getMcpApi().mcpAuthRemove(
      name: name,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  }) => _guard('Could not save the MCP server', () async {
    final name = draft.normalizedName;
    final config = draft.toConfigJson();
    final patch = sdk.Config(mcp: {name: sdk.OpencodeSdkRawUnion012(config)});

    switch (scope) {
      case McpConfigScope.project:
        if (_directory?.trim().isNotEmpty != true) {
          throw const ProductException(
            'Select a project before adding a project MCP server',
          );
        }
        final current = await _client.getConfigApi().configGet(
          directory: _directory,
          workspace: _workspace,
        );
        _requireUniqueMcpName(current.data, name, 'current project');
        await _client.getConfigApi().configUpdate(
          directory: _directory,
          workspace: _workspace,
          config: patch,
        );
      case McpConfigScope.global:
        final current = await _client.getGlobalApi().globalConfigGet();
        _requireUniqueMcpName(current.data, name, 'global configuration');
        await _client.getGlobalApi().globalConfigUpdate(config: patch);
    }
  });

  static void _requireUniqueMcpName(
    sdk.Config? config,
    String name,
    String scope,
  ) {
    if (config == null) {
      throw ProductException('Could not verify the existing $scope');
    }
    if (config.mcp?.containsKey(name) == true) {
      throw ProductException(
        'An MCP server named "$name" already exists in the $scope',
      );
    }
  }

  @override
  Future<List<IntegrationInfo>> listIntegrations() => _guard(
    'Could not load integrations',
    () async {
      final response = await _client.getIntegrationsApi().v2IntegrationList(
        locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
        locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
      );
      return (response.data?.data ?? const []).map((integration) {
        final connections = integration.connections
            .map((connection) {
              final value = connection.objectValue ?? const <String, dynamic>{};
              final type = (value['type'] ?? 'unknown').toString();
              return IntegrationConnectionInfo(
                type: type,
                id: value['id']?.toString(),
                label: switch (type) {
                  'credential' =>
                    (value['label'] ?? 'Stored credential').toString(),
                  'env' => (value['name'] ?? 'Server environment').toString(),
                  _ => 'Server-managed connection',
                },
              );
            })
            .toList(growable: false);
        return IntegrationInfo(
          id: integration.id,
          name: integration.name,
          methods: integration.methods.map((method) {
            final value = method.objectValue ?? const <String, dynamic>{};
            final type = (value['type'] ?? 'unknown').toString();
            final names = value['names'];
            final prompts = value['prompts'];
            return IntegrationMethodInfo(
              type: type,
              id: value['id']?.toString(),
              label: (value['label'] ?? _methodLabel(type)).toString(),
              prompts: prompts is List
                  ? prompts
                        .whereType<Map>()
                        .map((item) => Map<String, dynamic>.from(item))
                        .toList()
                  : const [],
              environmentNames: names is List
                  ? names.map((name) => name.toString()).toList()
                  : const [],
            );
          }).toList(),
          connections: connections,
          connectionCount: connections.length,
        );
      }).toList();
    },
  );

  @override
  Future<void> connectIntegrationKey(String id, String key, {String? label}) =>
      _guard('Could not connect the provider', () async {
        await _client.getIntegrationsApi().v2IntegrationConnectKey(
          integrationID: id,
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          v2IntegrationConnectKeyRequest: sdk.V2IntegrationConnectKeyRequest(
            key: key,
            label: label,
          ),
        );
        // OpenCode 1.18.x keeps the new integration credential store and the
        // provider runtime's legacy auth store separate. Chat execution still
        // reads the latter, so keep both surfaces synchronized until upstream
        // unifies them. Never log or otherwise expose [key].
        await _client.getControlApi().authSet(
          providerID: id,
          auth: sdk.Auth({'type': 'api', 'key': key}),
        );
        await refreshProviderRuntime();
      });

  @override
  Future<void> disconnectIntegration(IntegrationInfo integration) =>
      _guard('Could not disconnect the provider', () async {
        final credentialIDs = integration.credentialIDs.toSet().toList();
        if (credentialIDs.isEmpty) {
          throw const ProductException(
            'This provider is connected through the server environment and '
            'cannot be disconnected from mobile',
          );
        }

        // OpenCode 1.18.x can retain the same key in its legacy provider auth
        // store and its v2 integration credential store. Remove the legacy
        // copy first: if that write fails, the visible v2 connection remains
        // untouched and the user can safely retry from this row.
        try {
          final response = await _client.getControlApi().authRemove(
            providerID: integration.id,
          );
          if (response.data != true) {
            throw StateError('The server did not confirm auth removal');
          }
        } catch (error) {
          throw ProductException(
            'OpenCode could not remove the provider runtime credential. '
            'Nothing else was removed; try again.',
            cause: error,
          );
        }

        Object? credentialFailure;
        for (final credentialID in credentialIDs) {
          try {
            await _client.getOpencodeHttpApiApi().v2CredentialRemove(
              credentialID: credentialID,
              locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
              locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
            );
          } catch (error) {
            credentialFailure ??= error;
          }
        }

        Object? refreshFailure;
        try {
          await refreshProviderRuntime();
        } catch (error) {
          refreshFailure = error;
        }

        if (credentialFailure != null) {
          throw ProductException(
            'The runtime credential was removed, but OpenCode could not '
            'remove every stored connection. The connection remains visible '
            'so you can retry.',
            cause: credentialFailure,
          );
        }
        if (refreshFailure != null) {
          throw ProductException(
            'The provider credentials were removed, but OpenCode could not '
            'refresh its model runtime. Reconnect or restart the server.',
            cause: refreshFailure,
          );
        }
      });

  @override
  Future<void> refreshProviderRuntime() =>
      _guard('Could not refresh the provider runtime', () async {
        // Provider inventories are cached per server instance. Match
        // OpenCode's own compatibility client: invalidate the selected
        // location and the server-default location so newly authenticated or
        // pre-existing provider credentials are immediately available.
        await _client.getInstanceApi().instanceDispose(
          directory: _directory,
          workspace: _workspace,
        );
        await _client.getInstanceApi().instanceDispose();
      });

  @override
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs = const {},
    String? label,
  }) => _guard('Could not start provider authentication', () async {
    final response = await _client
        .getIntegrationsApi()
        .v2IntegrationConnectOauth(
          integrationID: id,
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          v2IntegrationConnectOauthRequest:
              sdk.V2IntegrationConnectOauthRequest(
                methodID: methodID,
                inputs: inputs,
                label: label,
              ),
        );
    final attempt = response.data?.data;
    if (attempt == null || attempt.url.isEmpty) {
      throw const ProductException('No authorization link was returned');
    }
    return IntegrationAuthLaunch(
      attemptID: attempt.attemptID,
      url: attempt.url,
      instructions: attempt.instructions,
      mode: attempt.mode == sdk.IntegrationAttemptModeEnum.code
          ? IntegrationAuthMode.code
          : IntegrationAuthMode.auto,
      expiresAt: _finiteTimestamp(attempt.time.expires.toJson()),
    );
  });

  @override
  Future<IntegrationAuthStatus> integrationOAuthStatus(String attemptID) =>
      _guard('Could not check provider authentication', () async {
        final response = await _client
            .getIntegrationsApi()
            .v2IntegrationAttemptStatus(
              attemptID: attemptID,
              locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
              locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
            );
        final data = response.data?.data.objectValue;
        final value = data?['status']?.toString();
        final state = switch (value) {
          'pending' => IntegrationAuthState.pending,
          'complete' => IntegrationAuthState.complete,
          'failed' => IntegrationAuthState.failed,
          'expired' => IntegrationAuthState.expired,
          _ => throw const ProductException(
            'Server returned an unknown authentication state',
          ),
        };
        final time = _stringMap(data?['time']);
        return IntegrationAuthStatus(
          state: state,
          message: data?['message']?.toString(),
          expiresAt: _finiteTimestamp(time['expires']),
        );
      });

  @override
  Future<void> completeIntegrationOAuth(String attemptID, {String? code}) =>
      _guard('Could not complete provider authentication', () async {
        await _client.getIntegrationsApi().v2IntegrationAttemptComplete(
          attemptID: attemptID,
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          v2IntegrationAttemptCompleteRequest:
              sdk.V2IntegrationAttemptCompleteRequest(code: code),
        );
      });

  @override
  Future<void> cancelIntegrationOAuth(String attemptID) =>
      _guard('Could not cancel provider authentication', () async {
        await _client.getIntegrationsApi().v2IntegrationAttemptCancel(
          attemptID: attemptID,
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
      });

  @override
  Future<List<CommandInfo>> listCommands() =>
      _guard('Could not load commands', () async {
        final response = await _client.getCommandsApi().v2CommandList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .map(
              (command) => CommandInfo(
                name: command.name,
                description: command.description,
                agent: command.agent,
                subtask: command.subtask == true,
              ),
            )
            .toList();
      });

  @override
  Future<List<SkillInfo>> listSkills() =>
      _guard('Could not load skills', () async {
        final response = await _client.getSkillsApi().v2SkillList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .map(
              (skill) => SkillInfo(
                name: skill.name,
                description: skill.description,
                location: skill.location,
                content: skill.content,
                slashCommand: skill.slash == true,
              ),
            )
            .toList();
      });

  @override
  Future<List<ReferenceInfo>> listReferences() =>
      _guard('Could not load references', () async {
        final response = await _client.getReferenceApi().v2ReferenceList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const [])
            .where((reference) => reference.hidden != true)
            .map(
              (reference) => ReferenceInfo(
                name: reference.name,
                path: reference.path,
                description: reference.description,
              ),
            )
            .toList();
      });

  @override
  Future<List<PendingQuestion>> listQuestions() =>
      _guard('Could not load pending questions', () async {
        final response = await _client.getQuestionApi().questionList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map((question) => PendingQuestion.fromJson(question.toJson()))
            .toList();
      });

  @override
  Future<List<SavedPermission>> listSavedPermissions() =>
      _guard('Could not load always allowed actions', () async {
        final projectResponse = await _client.getProjectApi().projectCurrent(
          directory: _directory,
          workspace: _workspace,
        );
        final projectID = projectResponse.data?.id ?? '';
        if (projectID.trim().isEmpty) {
          throw const ProductException('OpenCode returned no current project');
        }
        final response = await _client
            .getPermissionsApi()
            .v2PermissionSavedList(projectID: projectID);
        return (response.data?.data ?? const [])
            .where((permission) => permission.projectID == projectID)
            .map(
              (permission) => SavedPermission(
                id: permission.id,
                projectID: permission.projectID,
                action: permission.action,
                resource: permission.resource,
              ),
            )
            .toList();
      });

  @override
  Future<void> removeSavedPermission(String id) =>
      _guard('Could not revoke the always allowed action', () async {
        if (id.trim().isEmpty) {
          throw const ProductException('Saved permission ID is missing');
        }
        await _client.getPermissionsApi().v2PermissionSavedRemove(id: id);
      });

  @override
  Future<void> answerQuestion(String id, List<List<String>> answers) => _guard(
    'Could not send the answer',
    () async => _client.getQuestionApi().questionReply(
      requestID: id,
      directory: _directory,
      workspace: _workspace,
      questionReplyRequest: sdk.QuestionReplyRequest(answers: answers),
    ),
  );

  @override
  Future<void> rejectQuestion(String id) => _guard(
    'Could not dismiss the question',
    () async => _client.getQuestionApi().questionReject(
      requestID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<String?> shareSession(String id) =>
      _guard('Could not share the session', () async {
        final response = await _client.getSessionApi().sessionShare(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
        );
        return response.data?.share?.url;
      });

  @override
  Future<void> unshareSession(String id) => _guard(
    'Could not stop sharing the session',
    () async => _client.getSessionApi().sessionUnshare(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> archiveSession(String id) => _guard(
    'Could not archive the session',
    () async => _client.getSessionApi().sessionUpdate(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionUpdateRequest: sdk.SessionUpdateRequest(
        time: sdk.SessionUpdateRequestTime(
          archived: DateTime.now().millisecondsSinceEpoch,
        ),
      ),
    ),
  );

  @override
  Future<String> forkSession(String id, {String? messageID}) =>
      _guard('Could not fork the session', () async {
        final response = await _client.getSessionApi().sessionFork(
          sessionID: id,
          directory: _directory,
          workspace: _workspace,
          sessionForkRequest: sdk.SessionForkRequest(messageID: messageID),
        );
        final fork = response.data;
        if (fork == null) {
          throw const ProductException('Server returned no forked session');
        }
        return fork.id;
      });

  @override
  Future<void> revertSession(String id, String messageID) => _guard(
    'Could not revert the session',
    () async => _client.getSessionApi().sessionRevert(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionRevertRequest: sdk.SessionRevertRequest(messageID: messageID),
    ),
  );

  @override
  Future<void> restoreSession(String id) => _guard(
    'Could not restore the session',
    () async => _client.getSessionApi().sessionUnrevert(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
    ),
  );

  @override
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  }) => _guard(
    'Could not compact the session',
    () async => _client.getSessionApi().sessionSummarize(
      sessionID: id,
      directory: _directory,
      workspace: _workspace,
      sessionSummarizeRequest: sdk.SessionSummarizeRequest(
        providerID: providerID,
        modelID: modelID,
      ),
    ),
  );

  static TerminalProcess _terminal(sdk.Pty process) => TerminalProcess(
    id: process.id,
    title: process.title,
    command: process.command,
    arguments: process.args,
    directory: process.cwd,
    running: process.status == sdk.PtyStatusEnum.running,
    pid: process.pid,
    exitCode: process.exitCode,
  );

  static String _basename(String path) {
    final segments = path.split('/').where((part) => part.isNotEmpty).toList();
    return segments.isEmpty ? path : segments.last;
  }

  static Map<String, dynamic> _stringMap(Object? value) =>
      value is Map ? Map<String, dynamic>.from(value) : const {};

  static int? _finiteTimestamp(Object? value) {
    if (value is num && value.isFinite) return value.toInt();
    return null;
  }

  String _symbolPath(String value) {
    var path = value;
    final uri = Uri.tryParse(value);
    if (uri?.scheme == 'file') {
      try {
        path = uri!.toFilePath();
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

  static String _methodLabel(String type) => switch (type) {
    'key' => 'API key',
    'oauth' => 'OAuth',
    'env' => 'Server environment',
    _ => type,
  };

  static McpServerInfo _mcpServerInfo(String name, sdk.MCPStatus status) {
    final data = status.objectValue ?? const <String, dynamic>{};
    return McpServerInfo(
      name: name,
      status: (data['status'] ?? 'unknown').toString(),
      error: data['error']?.toString(),
    );
  }

  static String _requiredWorktreeDirectory(String value, String label) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw ProductException('Select a valid $label directory');
    }
    return trimmed;
  }

  static String _requiredSandboxDirectory(String root, String value) {
    final target = _requiredWorktreeDirectory(value, 'worktree');
    if (target == root) {
      throw const ProductException(
        'The primary project directory cannot be reset or removed',
      );
    }
    return target;
  }

  static Future<T> _guardWorktree<T>(
    String message,
    Future<T> Function() action,
  ) async {
    try {
      return await action();
    } on ProductException {
      rethrow;
    } on sdk.OpenCodeApiException catch (error) {
      final detail = _deepErrorMessage(error.rawPayload);
      if (detail?.isNotEmpty == true) throw ProductException(detail!);
      throw ProductException(message, cause: error);
    } catch (error) {
      throw ProductException(message, cause: error);
    }
  }

  static String? _deepErrorMessage(Object? value) {
    if (value is Map) {
      final direct = value['message']?.toString().trim();
      if (direct?.isNotEmpty == true) return direct;
      for (final nested in value.values) {
        final found = _deepErrorMessage(nested);
        if (found != null) return found;
      }
    } else if (value is List) {
      for (final nested in value) {
        final found = _deepErrorMessage(nested);
        if (found != null) return found;
      }
    }
    return null;
  }

  static Future<T> _guard<T>(
    String message,
    Future<T> Function() action,
  ) async {
    try {
      return await action();
    } on ProductException {
      rethrow;
    } catch (error) {
      throw ProductException(message, cause: error);
    }
  }
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
    _cursor += text.length;
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

class ProductException implements Exception {
  final String message;
  final Object? cause;

  const ProductException(this.message, {this.cause});

  @override
  String toString() => message;
}

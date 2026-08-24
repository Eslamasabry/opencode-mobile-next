import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:opencode_sdk/opencode_sdk.dart' as sdk;

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

  const WorkspaceInfo({
    required this.id,
    required this.projectID,
    required this.name,
    required this.type,
    this.branch,
    this.directory,
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
  final List<String> variants;

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

class McpServerInfo {
  final String name;
  final String status;
  final String? error;

  const McpServerInfo({required this.name, required this.status, this.error});
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
  final int connectionCount;

  const IntegrationInfo({
    required this.id,
    required this.name,
    required this.methods,
    required this.connectionCount,
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
  final String url;
  final String instructions;

  const IntegrationAuthLaunch({required this.url, required this.instructions});
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
  void write(String value);
  Future<void> close();
}

abstract interface class LocationAwareProductRepository {
  int get locationRevision;
}

abstract class ProductRepository {
  void setLocation({String? directory, String? workspace});
  Future<List<WorkspaceProject>> listProjects();
  Future<List<WorkspaceInfo>> listWorkspaces();
  Future<List<TerminalProcess>> listTerminals();
  Future<TerminalProcess> createTerminal({String? title});
  Future<void> renameTerminal(String id, String title);
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  });
  Future<void> removeTerminal(String id);
  Future<TerminalChannel> connectTerminal(String id);
  Future<CatalogSnapshot> loadCatalog();
  Future<List<McpServerInfo>> listMcpServers();
  Future<List<McpResourceInfo>> listMcpResources();
  Future<void> connectMcp(String name);
  Future<void> disconnectMcp(String name);
  Future<String> startMcpAuthentication(String name);
  Future<List<IntegrationInfo>> listIntegrations();
  Future<void> connectIntegrationKey(String id, String key, {String? label});
  Future<IntegrationAuthLaunch> startIntegrationOAuth(
    String id,
    String methodID, {
    Map<String, String> inputs,
    String? label,
  });
  Future<List<CommandInfo>> listCommands();
  Future<List<SkillInfo>> listSkills();
  Future<List<ReferenceInfo>> listReferences();
  Future<List<PendingQuestion>> listQuestions();
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
  Future<List<WorkspaceProject>> listProjects() =>
      _guard('Could not load projects', () async {
        final response = await _client.getProjectApi().projectList(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const [])
            .map(
              (project) => WorkspaceProject(
                id: project.id,
                name: project.name?.trim().isNotEmpty == true
                    ? project.name!
                    : _basename(project.worktree),
                directory: project.worktree,
                worktrees: project.sandboxes,
                updatedAt: project.time.updated,
              ),
            )
            .toList();
      });

  @override
  Future<List<WorkspaceInfo>> listWorkspaces() =>
      _guard('Could not load workspaces', () async {
        final response = await _client
            .getWorkspaceApi()
            .experimentalWorkspaceList(
              directory: _directory,
              workspace: _workspace,
            );
        return (response.data ?? const [])
            .map(
              (workspace) => WorkspaceInfo(
                id: workspace.id,
                projectID: workspace.projectID,
                name: workspace.name,
                type: workspace.type,
                branch: workspace.branch,
                directory: workspace.directory,
              ),
            )
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
    String id,
  ) => _guard('Could not connect to the terminal', () async {
    // A ticket is scoped to one location. Keep the socket query on that same
    // location even if the user switches workspaces while the request awaits.
    final directory = _directory;
    final workspace = _workspace;
    final token = await _client.getPtyApi().ptyConnectToken(
      ptyID: id,
      directory: directory,
      workspace: workspace,
    );
    final ticket = token.data?.ticket;
    if (ticket == null) {
      throw const ProductException('Terminal ticket was unavailable');
    }
    final base = Uri.parse(_client.dio.options.baseUrl);
    final query = <String, String>{'ticket': ticket};
    if (directory != null) query['directory'] = directory;
    if (workspace != null) query['workspace'] = workspace;
    final uri = base.replace(
      scheme: base.scheme == 'https' ? 'wss' : 'ws',
      path:
          '${base.path.endsWith('/') ? base.path.substring(0, base.path.length - 1) : base.path}/pty/${Uri.encodeComponent(id)}/connect',
      queryParameters: query,
    );
    return _IoTerminalChannel(await WebSocket.connect(uri.toString()));
  });

  @override
  Future<CatalogSnapshot> loadCatalog() =>
      _guard('Could not load models and agents', () async {
        final results = await Future.wait([
          _client.getProvidersApi().v2ProviderList(
            locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
            locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          ),
          _client.getModelsApi().v2ModelList(
            locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
            locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          ),
          _client.getOpencodeHttpApiApi().v2AgentList(
            locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
            locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          ),
        ]);
        final providerResponse = results[0] as dynamic;
        final modelResponse = results[1] as dynamic;
        final agentResponse = results[2] as dynamic;
        final providers = (providerResponse.data?.data as List? ?? const [])
            .cast<sdk.ProviderV2Info>()
            .map(
              (provider) => CatalogProvider(
                id: provider.id,
                name: provider.name,
                enabled: provider.disabled != true,
                integrationID: provider.integrationID,
              ),
            )
            .toList();
        final models = (modelResponse.data?.data as List? ?? const [])
            .cast<sdk.ModelV2Info>()
            .map(
              (model) => CatalogModel(
                id: model.id,
                providerID: model.providerID,
                name: model.name,
                family: model.family,
                enabled: model.enabled,
                status: model.status.value.toString(),
                contextLimit: model.limit.context,
                outputLimit: model.limit.output,
                reasoning: model.capabilities.reasoning,
                attachments: model.capabilities.attachment,
                tools: model.capabilities.toolcall,
                variants: model.variants.map((variant) => variant.id).toList(),
              ),
            )
            .toList();
        final agents = (agentResponse.data?.data as List? ?? const [])
            .cast<sdk.AgentV2Info>()
            .map(
              (agent) => CatalogAgent(
                id: agent.id,
                mode: agent.mode.value.toString(),
                description: agent.description,
                hidden: agent.hidden,
                maxSteps: agent.steps,
              ),
            )
            .toList();
        return CatalogSnapshot(
          providers: providers,
          models: models,
          agents: agents,
        );
      });

  @override
  Future<List<McpServerInfo>> listMcpServers() =>
      _guard('Could not load MCP servers', () async {
        final response = await _client.getMcpApi().mcpStatus(
          directory: _directory,
          workspace: _workspace,
        );
        return (response.data ?? const {}).entries.map((entry) {
          final data = entry.value.objectValue ?? const <String, dynamic>{};
          return McpServerInfo(
            name: entry.key,
            status: (data['status'] ?? 'unknown').toString(),
            error: data['error']?.toString(),
          );
        }).toList();
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
  Future<String> startMcpAuthentication(String name) =>
      _guard('Could not start authentication', () async {
        final response = await _client.getMcpApi().mcpAuthStart(
          name: name,
          directory: _directory,
          workspace: _workspace,
        );
        final url = response.data?.authorizationUrl;
        if (url == null) {
          throw const ProductException('No authorization link was returned');
        }
        return url;
      });

  @override
  Future<List<IntegrationInfo>> listIntegrations() =>
      _guard('Could not load integrations', () async {
        final response = await _client.getIntegrationsApi().v2IntegrationList(
          locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
          locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
        );
        return (response.data?.data ?? const []).map((integration) {
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
            connectionCount: integration.connections.length,
          );
        }).toList();
      });

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
      url: attempt.url,
      instructions: attempt.instructions,
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

  static String _methodLabel(String type) => switch (type) {
    'key' => 'API key',
    'oauth' => 'OAuth',
    'env' => 'Server environment',
    _ => type,
  };

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

class _IoTerminalChannel implements TerminalChannel {
  final WebSocket _socket;
  late final Stream<String> _output = _socket.map((data) {
    if (data is String) return data;
    if (data is List<int>) return utf8.decode(data, allowMalformed: true);
    return data.toString();
  }).asBroadcastStream();

  _IoTerminalChannel(this._socket);

  @override
  Stream<String> get output => _output;

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

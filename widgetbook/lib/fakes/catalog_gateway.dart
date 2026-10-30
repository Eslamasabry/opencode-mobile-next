import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

/// Explicit supported-server fixture; no protocol client or network is created.
const ServerCapabilities catalogServerCapabilities = ServerCapabilities(
  promptSteering: true,
  // Stable 2.0.29/2.1.1 permission contract (unchanged between the two); the
  // native engine independently probes the running version, operations and
  // effective policy before execution.
  phoneTeamExecution: true,
  projectsAreGitRepositories: false,
  commandReceipts: true,
  developmentServices: true,
  managedWorkspaces: false,
  workspaceWarp: false,
  sessionSteal: false,
  consoleOrganizations: false,
  mcpOAuth: false,
  mcpChatConnect: true,
  mcpChatOAuth: false,
  mcpChatToolRefresh: false,
  mcpConfigWrites: false,
  mcpRuntimeAdds: true,
  mcpRuntimeRemovals: true,
  integrationCredentials: true,
  integrationCommandAuth: true,
  pluginInventory: true,
  webSearch: true,
  sessionShare: false,
  sessionArchive: false,
  sessionTodos: false,
  messageDelete: false,
  workspaceSymbols: false,
  textSearch: false,
  languageServiceStatus: false,
  formatterStatus: false,
  toolInventory: false,
  experimentalCapabilities: false,
  shellSettings: true,
  remoteUpgrade: false,
  clientDiagnostics: false,
  gitInit: true,
  providerRuntimeRefresh: false,
  configuredProviderFallback: false,
  globalEventStream: true,
  worktreeReset: false,
  // Stable takes {projectID, name} and answers {directory}; the old
  // {strategy, directory} body is gone. Needs a project with a commit.
  worktreeCreate: true,
  legacyQuestionRequests: false,
  forms: true,
  inbox: true,
  // AI setup inspection; reversible config writes remain unavailable.
  setupConfigRead: true,
  setupMcpInventory: true,
);

class CatalogFakeGateway implements ServerGateway {
  CatalogFakeGateway({this.capabilities = catalogServerCapabilities});

  @override
  final ServerCapabilities capabilities;
  @override
  String? directory;
  @override
  String? workspace;
  @override
  bool isClosed = false;

  @override
  void setLocation({String? directory, String? workspace}) {
    this.directory = directory;
    this.workspace = workspace;
  }

  @override
  void close() => isClosed = true;

  @override
  Future<Health> health() async => Health(healthy: true, version: '2.1.1');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await sessions() : const []);

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => const [];

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      pendingPermissions();

  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: const []);

  @override
  Future<ProvidersResponse> configuredProviders() => providers();

  @override
  Future<List<AgentInfo>> agents() async => const [];

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

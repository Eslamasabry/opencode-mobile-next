import '../api/mcp_oauth.dart';
import '../api/models.dart';
import '../api2/models.dart' show Api2FormInfo, Api2FormState, Api2InboxItem;
import 'managed_shell.dart';

export 'managed_shell.dart';

/// Connection lifecycle surfaced to the UI.
enum StreamStatus { connecting, connected, reconnecting, disconnected }

/// One live server event subscription. Implementations own reconnect and
/// backoff; callers only start and dispose the channel.
abstract class LiveEventChannel {
  void start();
  Future<void> dispose();
}

enum VcsDiffMode { workingTree, branch }

/// A server-owned continuation token. Consumers must not derive or order it.
class ServerPage<T> {
  const ServerPage({required this.items, this.nextCursor});
  final List<T> items;
  final String? nextCursor;
  bool get hasMore => nextCursor != null && nextCursor!.isNotEmpty;
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

/// Model pricing. Every figure is **USD per one million tokens**, which is
/// the unit both servers use natively (v1 `Model.cost` mirrors models.dev's
/// per-million prices; v2 `Model.Cost` is typed `Money.USDPerMillionTokens`),
/// so no conversion happens on the way in.
class ModelCost {
  final double inputPerMillion;
  final double outputPerMillion;
  final double cacheReadPerMillion;
  final double cacheWritePerMillion;

  const ModelCost({
    required this.inputPerMillion,
    required this.outputPerMillion,
    this.cacheReadPerMillion = 0,
    this.cacheWritePerMillion = 0,
  });

  /// Parses the `{input, output, cache: {read, write}}` object both servers
  /// send; null for anything that is not a map.
  static ModelCost? fromJson(dynamic v) {
    if (v is! Map) return null;
    final cache = v['cache'] is Map ? v['cache'] as Map : const {};
    return ModelCost(
      inputPerMillion: _toDouble(v['input']),
      outputPerMillion: _toDouble(v['output']),
      cacheReadPerMillion: _toDouble(cache['read']),
      cacheWritePerMillion: _toDouble(cache['write']),
    );
  }

  /// True when every rate is zero (free / self-hosted models).
  bool get isFree =>
      inputPerMillion == 0 &&
      outputPerMillion == 0 &&
      cacheReadPerMillion == 0 &&
      cacheWritePerMillion == 0;

  static double _toDouble(dynamic v) =>
      v is num ? v.toDouble() : (v is String ? double.tryParse(v) ?? 0 : 0);
}

class CatalogModel {
  final String id;
  final String providerID;
  final String name;
  final String? family;
  final bool enabled;

  /// Lifecycle status as the server reports it: `active`, `beta`, `alpha`,
  /// `deprecated`, or `unknown` when the server sent none.
  final String status;
  final int contextLimit;
  final int outputLimit;
  final bool reasoning;
  final bool attachments;
  final bool tools;
  final List<CatalogVariant> variants;

  /// Base pricing (USD per million tokens); null when the server sent none.
  final ModelCost? cost;

  /// Model release date (v1 `release_date` `YYYY-MM-DD`, v2 `time.released`
  /// epoch millis); null when unknown.
  final DateTime? released;

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
    this.cost,
    this.released,
  });

  /// True for `deprecated` models.
  bool get deprecated => status.toLowerCase() == 'deprecated';

  /// True for `alpha`/`beta` models.
  bool get preview {
    final s = status.toLowerCase();
    return s == 'alpha' || s == 'beta';
  }
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

  /// Raw colour string as the server gives it: `#rrggbb` or a theme token
  /// (`primary`, `accent`, ...). Null when the agent has no colour.
  final String? color;

  /// Configured model as `providerID/modelID`; null when the agent inherits
  /// the session's model.
  final String? model;

  const CatalogAgent({
    required this.id,
    required this.mode,
    this.description,
    required this.hidden,
    this.maxSteps,
    this.color,
    this.model,
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

/// Runtime permission on v1; the v2 session protocol supports both kinds.
enum BackgroundWorkSupport { unavailable, subagents, subagentsAndShells }

/// v2 acknowledges the request with 204, which can also mean an idle no-op.
/// Only v1's true response confirms that something was promoted.
enum BackgroundWorkResult { promoted, unchanged, requested }

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

class ProductException implements Exception {
  final String message;
  final Object? cause;

  const ProductException(this.message, {this.cause});

  @override
  String toString() => message;
}

/// Feature switches for server abilities that depend on the connected
/// protocol generation. The v1 server exposes every listed feature, so its
/// gateway reports [allV1]; a v2 gateway narrows these per endpoint support.
class ServerCapabilities {
  final bool managedWorkspaces;
  final bool workspaceWarp;
  final bool sessionSteal;
  final bool consoleOrganizations;
  final bool mcpOAuth;
  final bool mcpConfigWrites;
  final bool sessionShare;
  final bool sessionArchive;
  final bool sessionTodos;
  final bool messageDelete;
  final bool workspaceSymbols;
  final bool textSearch;
  final bool languageServiceStatus;
  final bool formatterStatus;
  final bool toolInventory;
  final bool experimentalCapabilities;
  final bool shellSettings;
  final bool remoteUpgrade;
  final bool clientDiagnostics;
  final bool gitInit;
  final bool providerRuntimeRefresh;
  final bool configuredProviderFallback;
  final bool globalEventStream;
  final bool worktreeReset;
  final bool legacyQuestionRequests;

  /// OpenCode 2 structured forms (`/api/session/{id}/form`); replaces the
  /// v1 question dialogs. False on v1 — [FormGateway] methods are inert.
  final bool forms;

  /// OpenCode 2 session inbox (`/api/session/{id}/inbox`): pending sends
  /// with steer/queue delivery. False on v1 — [InboxGateway] is inert.
  final bool inbox;

  const ServerCapabilities({
    this.managedWorkspaces = true,
    this.workspaceWarp = true,
    this.sessionSteal = true,
    this.consoleOrganizations = true,
    this.mcpOAuth = true,
    this.mcpConfigWrites = true,
    this.sessionShare = true,
    this.sessionArchive = true,
    this.sessionTodos = true,
    this.messageDelete = true,
    this.workspaceSymbols = true,
    this.textSearch = true,
    this.languageServiceStatus = true,
    this.formatterStatus = true,
    this.toolInventory = true,
    this.experimentalCapabilities = true,
    this.shellSettings = true,
    this.remoteUpgrade = true,
    this.clientDiagnostics = true,
    this.gitInit = true,
    this.providerRuntimeRefresh = true,
    this.configuredProviderFallback = true,
    this.globalEventStream = true,
    this.worktreeReset = true,
    this.legacyQuestionRequests = true,
    this.forms = false,
    this.inbox = false,
  });

  static const allV1 = ServerCapabilities();
}

/// Server health checks.
abstract class HealthGateway {
  Future<Health> health();
}

/// Optional richer status read: gateways whose status endpoint carries retry
/// details (v1 `session.status` `{type: 'retry', attempt, message, next}`)
/// implement this so the controller can hydrate [SessionRetryState]s on a
/// refresh. v2's `/session/active` only reports running sessions, so the v2
/// gateway does not implement it and relies on live events instead.
abstract class SessionRetryGateway {
  Future<Map<String, SessionRetryState>> sessionRetryStates();
}

/// Session inventory and live conversation reads for the selected location.
abstract class SessionGateway {
  Future<List<Session>> sessions();
  Future<Session> createSession();
  Future<void> deleteSession(String id);
  Future<void> renameSession(String id, String title);
  Future<Session> session(String id);
  Future<Map<String, String>> sessionStatuses();
  Future<List<MessageWithParts>> messages(String id);
  Future<List<Todo>> todos(String id);
  Future<List<FileDiff>> diff(String id);
}

/// How a prompt sent while a turn runs reaches the agent (OpenCode 2 only):
/// [steer] interjects at the next step boundary (the v2 default), [queue]
/// waits for the current run to finish. v1 gateways ignore the choice.
enum PromptDelivery { steer, queue }

/// Prompt execution against one session.
abstract class PromptGateway {
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments,
    List<PromptAgentMention> agentMentions,
    PromptDelivery? delivery,
  });
  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  });
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  });
  Future<void> abort(String sessionID);
}

/// Pending permission requests and replies.
///
/// [message] rides only on OpenCode 2 rejections (shown to the model —
/// steering-by-rejection); v1 gateways accept and ignore it so callers need
/// no protocol branch.
abstract class PermissionGateway {
  Future<List<PermissionRequest>> pendingPermissions();
  Future<List<PermissionRequest>> pendingPermissionsV2();
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  });
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  });
}

/// OpenCode 2 structured forms (protocol notes §8). v1 implementations are
/// inert: list methods return empty lists and mutations fail with a typed
/// [ProductException] (capability [ServerCapabilities.forms] is false).
abstract class FormGateway {
  /// Pending forms owned by one session.
  Future<List<Api2FormInfo>> sessionForms(String sessionID);

  /// Pending forms across every session of the pinned location, including
  /// global (MCP elicitation) forms with `sessionID == "global"`.
  Future<List<Api2FormInfo>> pendingForms();

  Future<Api2FormState> formState(String sessionID, String formID);

  /// Replies with the assembled answer payload. 400 invalid-answer and
  /// 409 already-settled surface as [ApiException] with the v2 error tag.
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  );

  Future<void> cancelForm(String sessionID, String formID);
}

/// OpenCode 2 session inbox (protocol notes §6.2): durably admitted,
/// not-yet-delivered work. v1 implementations are inert (empty list, typed
/// unavailable mutations; capability [ServerCapabilities.inbox] is false).
abstract class InboxGateway {
  Future<List<Api2InboxItem>> inboxItems(String sessionID);

  /// Cancels an undelivered item (409 [ApiException] if already delivered).
  Future<void> cancelInboxItem(String sessionID, String inboxID);

  /// Flips a pending item to steer delivery (send at next step boundary).
  Future<void> steerInboxItem(String sessionID, String inboxID);

  /// Flips a pending item to queue delivery (wait for the run to finish).
  Future<void> queueInboxItem(String sessionID, String inboxID);
}

/// Pending question requests routed through the session-scoped endpoints.
abstract class QuestionGateway {
  Future<List<Map<String, dynamic>>> pendingQuestionsV2();
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  );
  Future<void> rejectQuestionV2(String sessionID, String requestID);
}

/// Provider and agent inventories backing the model picker.
abstract class ProviderGateway {
  Future<ProvidersResponse> providers();
  Future<ProvidersResponse> configuredProviders();
  Future<List<AgentInfo>> agents();
}

/// Workspace file browsing and search.
abstract class FileGateway {
  Future<List<FileNode>> listFiles(String path);
  Future<FileContent> fileContent(String path);
  Future<List<String>> findFile(String query);
  Future<List<FindMatch>> findText(String pattern);
}

/// Live server event delivery as parsed [EventEnvelope] objects.
abstract class EventGateway {
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  });
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  });
}

/// The protocol-neutral transport for one connected server location: live
/// reads, prompt execution, request replies, and event delivery.
abstract class ServerGateway
    implements
        HealthGateway,
        SessionGateway,
        PromptGateway,
        PermissionGateway,
        QuestionGateway,
        FormGateway,
        InboxGateway,
        ProviderGateway,
        FileGateway,
        EventGateway {
  ServerCapabilities get capabilities;
  String? get directory;
  String? get workspace;
  bool get isClosed;
  void setLocation({String? directory, String? workspace});
  void close();
}

/// Stored-session lifecycle operations beyond the live transport reads.
abstract class SessionOperationsGateway {
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport();
  Future<BackgroundWorkResult> backgroundSession(String sessionID);
  Future<Session> getSessionDetails(String id);
  Future<List<Session>> listSessionChildren(String id);
  Future<String?> shareSession(String id);
  Future<void> unshareSession(String id);
  Future<void> archiveSession(String id);
  Future<String> forkSession(String id, {String? messageID});
  Future<void> deleteMessage({
    required String sessionID,
    required String messageID,
  });
  Future<void> revertSession(String id, String messageID);
  Future<void> restoreSession(String id);
  Future<void> compactSession(
    String id, {
    required String providerID,
    required String modelID,
  });
  Future<void> addSessionLocationReminder(String sessionID, String directory);
}

/// Host administration: projects, worktrees, workspaces, organizations, and
/// cross-location session movement.
abstract class HostGateway {
  Future<String> upgradeServer(String target);
  Future<void> writeClientLog({
    required String message,
    Map<String, Object?> extra,
  });
  Future<List<WorkspaceProject>> listProjects();
  Future<WorkspaceProject> renameProject({
    required String projectID,
    required String projectDirectory,
    required String name,
  });
  Future<WorkspaceProject?> loadCurrentProject();
  Future<List<ProjectDirectoryInfo>> listProjectDirectories(String projectID);
  Future<List<WorktreeInfo>> listWorktrees({
    required String projectDirectory,
    String? projectID,
  });
  Future<WorktreeInfo> createWorktree({
    required String projectDirectory,
    String? name,
  });
  Future<List<VersionControlFile>> listWorktreeFileStatuses(String directory);
  Future<void> resetWorktree({
    required String projectDirectory,
    required String directory,
  });
  Future<void> removeWorktree({
    required String projectDirectory,
    required String directory,
  });
  Future<List<WorkspaceInfo>> listWorkspaces();
  Future<List<WorkspaceInfo>> listManagedWorkspaces({
    required String projectDirectory,
  });
  Future<List<WorkspaceAdapterInfo>> listWorkspaceAdapters({
    required String projectDirectory,
  });
  Future<void> syncWorkspaceList({required String projectDirectory});
  Future<WorkspaceInfo> createManagedWorkspace({
    required String projectDirectory,
    required String type,
    String? branch,
  });
  Future<void> removeManagedWorkspace({
    required String projectDirectory,
    required String id,
  });
  Future<ServerPage<GlobalSessionResult>> listGlobalSessions({
    String? search,
    bool includeArchived,
    String? cursor,
    int limit,
  });
  Future<void> moveSession(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  });
  Future<void> warpSession(
    String sessionID, {
    required String? workspaceID,
    required bool copyChanges,
  });
  Future<bool> startWorkspaceSync();
  Future<String> stealSessionIntoWorkspace(String sessionID);
  Future<List<ConsoleOrganization>> listConsoleOrganizations();
  Future<void> switchConsoleOrganization(ConsoleOrganization organization);
}

/// Version control, workspace health, and symbol search.
abstract class VcsGateway {
  Future<VersionControlHealth> loadVersionControlHealth();
  Future<void> initializeGitRepository();
  Future<List<VersionControlFile>> listFileStatuses();
  Future<List<LanguageServiceHealth>> listLanguageServices();
  Future<List<FormatterHealth>> listFormatters();
  Future<List<WorkspaceSymbol>> findWorkspaceSymbols(String query);
  Future<List<FileDiff>> listVcsDiffs(VcsDiffMode mode);
}

/// Terminal (PTY) management and interactive connections.
abstract class TerminalGateway {
  Future<List<TerminalProcess>> listTerminals();
  Future<TerminalShellSettings> loadTerminalShellSettings();
  Future<void> selectTerminalShell(String value);
  Future<TerminalProcess> createTerminal({String? title});
  Future<void> renameTerminal(String id, String title);
  Future<void> resizeTerminal(
    String id, {
    required int rows,
    required int cols,
  });
  Future<void> removeTerminal(String id);
  Future<TerminalChannel> connectTerminal(String id, {int? cursor});
}

/// Catalog listings: models, agents, commands, skills, references, and tools.
abstract class CatalogGateway {
  Future<CatalogSnapshot> loadCatalog();
  Future<ChatDefaults> loadChatDefaults();
  Future<ExperimentalServerCapabilities> loadExperimentalCapabilities();
  Future<List<String>> listCodingToolIDs();
  Future<List<CodingToolInfo>> listCodingTools({
    required String providerID,
    required String modelID,
  });
  Future<List<CommandInfo>> listCommands();
  Future<List<SkillInfo>> listSkills();
  Future<List<ReferenceInfo>> listReferences();
}

/// MCP server management.
abstract class McpGateway {
  Future<List<McpServerInfo>> listMcpServers();
  Future<List<McpResourceInfo>> listMcpResources();
  Future<void> connectMcp(String name);
  Future<void> disconnectMcp(String name);
  Future<McpAuthLaunch> startMcpAuthentication(String name);
  Future<McpServerInfo> completeMcpAuthentication(String name, String code);
  Future<void> cancelMcpAuthentication(String name);
  Future<void> addMcpServer(
    McpServerDraft draft, {
    required McpConfigScope scope,
  });
}

/// Provider integrations: keys, OAuth attempts, and runtime refresh.
abstract class IntegrationGateway {
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
}

/// Question dialogs and saved permission management.
abstract class RequestGateway {
  Future<List<PendingQuestion>> listQuestions();
  Future<void> answerQuestion(String id, List<List<String>> answers);
  Future<void> rejectQuestion(String id);
  Future<List<SavedPermission>> listSavedPermissions();
  Future<void> removeSavedPermission(String id);
}

/// The protocol-neutral operations surface paired with a [ServerGateway]:
/// everything the product screens drive beyond the live transport.
abstract class ServerOperationsGateway
    implements
        SessionOperationsGateway,
        HostGateway,
        VcsGateway,
        TerminalGateway,
        ManagedShellGateway,
        CatalogGateway,
        McpGateway,
        IntegrationGateway,
        RequestGateway {
  void setLocation({String? directory, String? workspace});
}

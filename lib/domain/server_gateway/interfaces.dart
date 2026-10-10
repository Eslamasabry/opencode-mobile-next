import '../../api/mcp_oauth.dart';
import '../../api/models.dart';
import '../../api2/models.dart' show Api2FormInfo, Api2FormState, Api2InboxItem;
import '../managed_shell.dart';

import 'capabilities.dart';
import 'value_types_core.dart';
import 'value_types_catalog.dart';

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
  /// Compatibility read; paged servers return their newest page only.
  Future<List<Session>> sessions();
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100});
  Future<Session> createSession();
  Future<void> deleteSession(String id);
  Future<void> renameSession(String id, String title);
  Future<Session> session(String id);
  Future<Map<String, String>> sessionStatuses();

  /// Compatibility read of the latest page only. UI callers must use
  /// [messagePage] to retain older-history continuation and coverage.
  Future<List<MessageWithParts>> messages(String id);

  /// Chronological items; the opaque continuation always requests older rows.
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  });
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

/// Optional exact dispatch correlation, separate from heuristic transcript
/// reconciliation. This is not an idempotency or delivery-retry contract.
/// The optional beforeSend fence runs synchronously after asynchronous preflight
/// and immediately before HTTP dispatch; it may refuse a stale/deleted scope.
abstract interface class CorrelatedPromptGateway {
  String createPromptMessageID();
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments,
    List<PromptAgentMention> agentMentions,
    PromptDelivery? delivery,
    void Function()? beforeSend,
  });
}

/// What a message sent while the agent works does to the running turn, on
/// a gateway that does not run it after that turn.
enum MidTurnPrompt {
  /// Read at the agent's next step: "Add to this turn".
  joinsTurn,

  /// The agent stops its reply and starts again from the message: "Stop
  /// and send".
  restartsTurn,
}

/// A gateway whose agents never hold a message sent while they work until
/// the turn ends: they take it into the turn or start over from it. The
/// composer says which, and the message is never shown as queued.
abstract interface class MidTurnPromptGateway {
  /// What [sessionID]'s agent does with a mid-turn message; null when the
  /// gateway does not know the agent yet.
  MidTurnPrompt? midTurnPrompt(String sessionID);
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

/// Optional v2 read receipts for exact completed-run watermarks.
abstract interface class SessionReadStateGateway {
  /// Acknowledge exactly the idle transition displayed by the client.
  Future<void> viewSession(String sessionID, int idle);
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

/// Optional runtime-only MCP removal for the selected location.
abstract interface class McpRemovalGateway {
  /// Does not delete persistent configuration or credentials. Callers should
  /// refetch MCP inventory after success; missing servers remain typed errors.
  Future<void> removeMcpServer(String name);
}

/// Optional stored-credential management using IDs from integration metadata.
/// Successful mutations do not establish which credential is active.
abstract interface class IntegrationCredentialGateway {
  Future<void> renameCredential(String id, String label);
  Future<void> activateCredential(String id);
  Future<void> removeCredential(String id);
}

/// Optional metadata-only recovery. Restores routing in a replacement gateway;
/// it MUST NOT contact the server or start authentication. The controller must
/// first verify the saved profile/origin and selected original location.
abstract interface class IntegrationAuthRecoveryGateway {
  void restoreIntegrationAuthAttempt({
    required String integrationID,
    required String attemptID,
    required bool command,
    String? directory,
    String? workspace,
  });
}

/// Optional server-run command authentication; never execute or copy a command
/// locally. Pin the original repository/location throughout the attempt. Poll
/// after reconnect and refetch integrations/catalog after completion.
abstract interface class IntegrationCommandGateway {
  Future<IntegrationAuthLaunch> startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
  });
  Future<IntegrationAuthStatus> integrationCommandStatus(
    String integrationID,
    String attemptID,
  );
  Future<void> cancelIntegrationCommand(String integrationID, String attemptID);
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

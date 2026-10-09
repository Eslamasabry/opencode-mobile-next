/// Feature switches for server abilities that depend on the connected
/// protocol generation. The v1 server exposes every listed feature, so its
/// gateway reports [allV1]; a v2 gateway narrows these per endpoint support.
class ServerCapabilities {
  /// Qualified Agent cards on the managed host; never implied by a dialect.
  final bool genUi;

  /// Portable session links remain off until private host, UI consent and
  /// authorized scoped lookup are verified together. No adapter enables this.
  final bool sessionAddressHandoff;

  // AI setup assistant: additive capability section (2026-09-27).
  final bool setupConfigRead;
  final bool setupConfigWrite;
  final bool setupMcpInventory;
  final bool setupAssistantSession;

  /// Prompt dispatch preserves an app-authored message ID in the user echo.
  final bool clientPromptMessageID;

  /// Client-ID prompt admission can be checked later. Does not authorize resend.
  final bool commandReceipts;

  /// Optional official-runtime account panel. Disabled for other gateways.
  final bool agentAccount;

  /// Safe host-agent discovery, including unavailable agents and reasons.
  final bool hostAgentProviders;

  /// Exact, request-scoped host permission actions; null selection denies.
  final bool hostAgentPermissionActions;
  // Core operations differ across supported server backends.
  final bool promptAttachments;

  /// With [promptAttachments]: pictures only (an agent on this phone takes
  /// images through its helper, not other files).
  final bool promptImagesOnly;

  /// The server's copy of a sent prompt keeps its text only (Paseo's
  /// timeline drops the pictures), so the app's own copy is matched to it by
  /// text alone.
  final bool promptEchoTextOnly;

  /// A sub-agent's conversation takes the person's replies (OpenCode's task
  /// sessions do; Claude Code's sub-agents answer only their main
  /// conversation, so their page is read-only).
  final bool subagentReplies;

  /// Sub-agents are conversations of their own the chat opens (Claude
  /// Code's through its helper). OpenCode's open through
  /// [projectManagement], as before.
  final bool subagentSessions;
  final bool promptAgentMentions;
  final bool offlinePromptQueue;
  final bool fileBrowsing;
  final bool terminal;
  final bool projectManagement;
  final bool globalSessionSearch;
  final bool sessionDiff;
  final bool sessionFork;
  final bool sessionCompact;
  final bool persistentPermissionGrants;

  /// The runtime asks before it acts (permission requests the app answers),
  /// so a conversation's approval mode (Asks first / Auto-approve) means
  /// something. False for a runtime that never asks: the composer then shows
  /// no approval chip rather than one that promises a question never coming.
  final bool permissionRequests;

  /// Whether standing grants can be listed and revoked from the app. A
  /// runtime can honour "always allow" for a conversation without keeping a
  /// list the app can read back (Paseo applies the rule inside the agent).
  final bool savedPermissionList;

  /// Whether a completed assistant message ends the current run.
  /// Item-based backends report run completion separately.
  final bool messageCompletionEndsRun;
  final bool sessionRevert;
  final bool sessionImportExport;
  final bool sessionNotes;
  final bool serverCatalog;

  /// A conversation can switch to a model from another provider.
  final bool sessionModelProviderSwitching;

  /// The runtime exposes selectable agents, distinct from permission modes.
  final bool agentSelection;

  /// The server lists its own slash commands for a conversation and runs
  /// one there ([CatalogGateway.listCommands], [PromptGateway.slashCommand]).
  /// True on OpenCode 1 and 2. False on Claude Code through Paseo (daemon
  /// 0.8.0 has no callable command list the app can use) and on Codex (its
  /// app-server exposes native compact/review/shell calls, but no command
  /// catalogue): the command sheet names what is missing instead of
  /// offering commands that would be sent as plain text
  /// (docs/qa/slice-P10.1-2-2026-09-28/README.md).
  final bool slashCommands;
  final bool profileAttentionPolling;

  final bool managedWorkspaces;
  final bool workspaceWarp;
  final bool sessionSteal;
  final bool consoleOrganizations;
  final bool mcpOAuth;

  /// A reviewed connector can be added or connected from its chat card.
  final bool mcpChatConnect;

  /// Chat can complete this runtime's MCP authorization round trip.
  final bool mcpChatOAuth;

  /// Connected MCP tools appear on the next model step in this conversation.
  /// Does not imply that an already-running model request gains new tools.
  /// True only with a confirmed registry barrier; an asynchronous change
  /// notification or connected transport alone is insufficient.
  final bool mcpChatToolRefresh;

  /// Persistent project/global configuration writes, rather than runtime add.
  final bool mcpConfigWrites;
  final bool mcpRuntimeAdds;

  /// Runtime-only removal; does not delete persistent MCP configuration.
  final bool mcpRuntimeRemovals;
  final bool integrationCredentials;
  final bool integrationCommandAuth;
  final bool pluginInventory;
  final bool webSearch;
  final bool sessionShare;
  final bool sessionArchive;

  /// The server can delete a conversation for good (Paseo only archives).
  final bool sessionDelete;
  final bool sessionTodos;
  final bool messageDelete;
  final bool workspaceSymbols;
  final bool textSearch;
  final bool languageServiceStatus;
  final bool formatterStatus;
  final bool toolInventory;
  final bool experimentalCapabilities;
  final bool shellSettings;

  /// Ownership-tagged command creation and exact-ID lifecycle controls.
  final bool developmentServices;
  final bool remoteUpgrade;
  final bool clientDiagnostics;
  final bool gitInit;
  final bool providerRuntimeRefresh;
  final bool configuredProviderFallback;
  final bool globalEventStream;
  final bool worktreeReset;

  /// Creating a fresh git worktree through a contract-proven call. True on v1,
  /// whose generated `worktreeCreate` (`POST /experimental/worktree`,
  /// `WorktreeCreateInput{name?}` → `Worktree{name, directory, branch?}`) is
  /// pinned to upstream commit `f12e14cf`. False on v2: its
  /// `POST /api/worktree/{projectID}` requires `{strategy, directory}` that
  /// the adapter does not send and the snapshot does not enumerate, so the
  /// create action is not presented as usable there. Listing, opening and
  /// inspecting worktrees do not depend on this switch.
  final bool worktreeCreate;
  final bool legacyQuestionRequests;

  /// OpenCode 2 structured forms (`/api/session/{id}/form`); replaces the
  /// v1 question dialogs. False on v1 — [FormGateway] methods are inert.
  final bool forms;

  /// OpenCode 2 session inbox (`/api/session/{id}/inbox`): pending sends
  /// with steer/queue delivery. False on v1 — [InboxGateway] is inert.
  final bool inbox;

  /// The server's product has a terminal CLI that resumes a session by id
  /// inside its project directory (`opencode --session`, `opencode2
  /// --session`), so "Continue on computer" can show a command. False for
  /// backends with no such CLI (Codex).
  final bool cliSessionResume;

  /// The server keeps projects only for git repositories, so "this folder
  /// has a project" means "this folder is git" (OpenCode 1). False on
  /// OpenCode 2, which gives every folder a project and reports no VCS.
  final bool projectsAreGitRepositories;

  const ServerCapabilities({
    this.genUi = false,
    this.sessionAddressHandoff = false,
    this.setupConfigRead = false,
    this.setupConfigWrite = false,
    this.setupMcpInventory = false,
    this.setupAssistantSession = false,
    this.clientPromptMessageID = false,
    this.commandReceipts = false,
    this.agentAccount = false,
    this.hostAgentProviders = false,
    this.hostAgentPermissionActions = false,
    this.promptAttachments = true,
    this.promptImagesOnly = false,
    this.promptEchoTextOnly = false,
    this.subagentReplies = true,
    this.subagentSessions = false,
    this.promptAgentMentions = true,
    this.offlinePromptQueue = true,
    this.fileBrowsing = true,
    this.terminal = true,
    this.projectManagement = true,
    this.globalSessionSearch = true,
    this.sessionDiff = true,
    this.sessionFork = true,
    this.sessionCompact = true,
    this.persistentPermissionGrants = true,
    this.permissionRequests = true,
    this.savedPermissionList = true,
    this.messageCompletionEndsRun = true,
    this.sessionRevert = true,
    this.sessionImportExport = true,
    this.sessionNotes = true,
    this.serverCatalog = true,
    this.sessionModelProviderSwitching = true,
    this.agentSelection = true,
    this.slashCommands = true,
    this.profileAttentionPolling = true,

    this.managedWorkspaces = true,
    this.workspaceWarp = true,
    this.sessionSteal = true,
    this.consoleOrganizations = true,
    this.mcpOAuth = true,
    this.mcpChatConnect = false,
    this.mcpChatOAuth = false,
    this.mcpChatToolRefresh = false,
    this.mcpConfigWrites = true,
    this.mcpRuntimeAdds = false,
    this.mcpRuntimeRemovals = false,
    this.integrationCredentials = false,
    this.integrationCommandAuth = false,
    this.pluginInventory = false,
    this.webSearch = false,
    this.sessionShare = true,
    this.sessionArchive = true,
    this.sessionDelete = true,
    this.sessionTodos = true,
    this.messageDelete = true,
    this.workspaceSymbols = true,
    this.textSearch = true,
    this.languageServiceStatus = true,
    this.formatterStatus = true,
    this.toolInventory = true,
    this.experimentalCapabilities = true,
    this.shellSettings = true,
    this.developmentServices = false,
    this.remoteUpgrade = true,
    this.clientDiagnostics = true,
    this.gitInit = true,
    this.providerRuntimeRefresh = true,
    this.configuredProviderFallback = true,
    this.globalEventStream = true,
    this.worktreeReset = true,
    this.worktreeCreate = true,
    this.legacyQuestionRequests = true,
    this.forms = false,
    this.inbox = false,
    this.cliSessionResume = true,
    this.projectsAreGitRepositories = true,
  });

  /// Preserve every protocol capability while applying managed-host readiness.
  ServerCapabilities withGenUi(bool value) => ServerCapabilities(
    genUi: value,
    sessionAddressHandoff: sessionAddressHandoff,
    setupConfigRead: setupConfigRead,
    setupConfigWrite: setupConfigWrite,
    setupMcpInventory: setupMcpInventory,
    setupAssistantSession: setupAssistantSession,
    clientPromptMessageID: clientPromptMessageID,
    commandReceipts: commandReceipts,
    agentAccount: agentAccount,
    hostAgentProviders: hostAgentProviders,
    hostAgentPermissionActions: hostAgentPermissionActions,
    promptAttachments: promptAttachments,
    promptImagesOnly: promptImagesOnly,
    promptEchoTextOnly: promptEchoTextOnly,
    subagentReplies: subagentReplies,
    subagentSessions: subagentSessions,
    promptAgentMentions: promptAgentMentions,
    offlinePromptQueue: offlinePromptQueue,
    fileBrowsing: fileBrowsing,
    terminal: terminal,
    projectManagement: projectManagement,
    globalSessionSearch: globalSessionSearch,
    sessionDiff: sessionDiff,
    sessionFork: sessionFork,
    sessionCompact: sessionCompact,
    persistentPermissionGrants: persistentPermissionGrants,
    permissionRequests: permissionRequests,
    savedPermissionList: savedPermissionList,
    messageCompletionEndsRun: messageCompletionEndsRun,
    sessionRevert: sessionRevert,
    sessionImportExport: sessionImportExport,
    sessionNotes: sessionNotes,
    serverCatalog: serverCatalog,
    sessionModelProviderSwitching: sessionModelProviderSwitching,
    agentSelection: agentSelection,
    slashCommands: slashCommands,
    profileAttentionPolling: profileAttentionPolling,
    managedWorkspaces: managedWorkspaces,
    workspaceWarp: workspaceWarp,
    sessionSteal: sessionSteal,
    consoleOrganizations: consoleOrganizations,
    mcpOAuth: mcpOAuth,
    mcpChatConnect: mcpChatConnect,
    mcpChatOAuth: mcpChatOAuth,
    mcpChatToolRefresh: mcpChatToolRefresh,
    mcpConfigWrites: mcpConfigWrites,
    mcpRuntimeAdds: mcpRuntimeAdds,
    mcpRuntimeRemovals: mcpRuntimeRemovals,
    integrationCredentials: integrationCredentials,
    integrationCommandAuth: integrationCommandAuth,
    pluginInventory: pluginInventory,
    webSearch: webSearch,
    sessionShare: sessionShare,
    sessionArchive: sessionArchive,
    sessionDelete: sessionDelete,
    sessionTodos: sessionTodos,
    messageDelete: messageDelete,
    workspaceSymbols: workspaceSymbols,
    textSearch: textSearch,
    languageServiceStatus: languageServiceStatus,
    formatterStatus: formatterStatus,
    toolInventory: toolInventory,
    experimentalCapabilities: experimentalCapabilities,
    shellSettings: shellSettings,
    developmentServices: developmentServices,
    remoteUpgrade: remoteUpgrade,
    clientDiagnostics: clientDiagnostics,
    gitInit: gitInit,
    providerRuntimeRefresh: providerRuntimeRefresh,
    configuredProviderFallback: configuredProviderFallback,
    globalEventStream: globalEventStream,
    worktreeReset: worktreeReset,
    worktreeCreate: worktreeCreate,
    legacyQuestionRequests: legacyQuestionRequests,
    forms: forms,
    inbox: inbox,
    cliSessionResume: cliSessionResume,
    projectsAreGitRepositories: projectsAreGitRepositories,
  );

  static const allV1 = ServerCapabilities(
    clientPromptMessageID: true,
    commandReceipts: true,
    setupConfigRead: true,
    setupMcpInventory: true,
    mcpRuntimeAdds: true,
    mcpChatConnect: true,
    mcpChatOAuth: true,
    mcpChatToolRefresh: true,
  );
}

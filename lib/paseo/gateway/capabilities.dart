part of '../gateway.dart';

const paseoServerCapabilities = ServerCapabilities(
  hostAgentProviders: true,
  hostAgentPermissionActions: true,
  // Pictures go to the agent with the message (`images`); other files do not.
  promptAttachments: true,
  promptImagesOnly: true,
  promptEchoTextOnly: true,
  subagentReplies: false,
  subagentSessions: true,
  promptAgentMentions: false,
  offlinePromptQueue: false,
  fileBrowsing: true,
  terminal: false,
  projectManagement: false,
  globalSessionSearch: false,
  sessionDiff: false,
  sessionFork: false,
  sessionCompact: false,
  persistentPermissionGrants: true,
  savedPermissionList: false,
  messageCompletionEndsRun: false,
  sessionRevert: false,
  sessionImportExport: false,
  sessionNotes: false,
  serverCatalog: false,
  sessionModelProviderSwitching: false,
  agentSelection: false,
  // The runtime's own commands, listed by the helper, run as `/name args`.
  slashCommands: true,
  profileAttentionPolling: false,
  managedWorkspaces: false,
  workspaceWarp: false,
  sessionSteal: false,
  consoleOrganizations: false,
  mcpOAuth: false,
  mcpConfigWrites: false,
  mcpRuntimeAdds: false,
  mcpRuntimeRemovals: false,
  integrationCredentials: false,
  integrationCommandAuth: false,
  pluginInventory: false,
  webSearch: false,
  sessionShare: false,
  sessionArchive: true,
  sessionDelete: false,
  sessionTodos: false,
  messageDelete: false,
  workspaceSymbols: false,
  textSearch: false,
  languageServiceStatus: false,
  formatterStatus: false,
  toolInventory: false,
  experimentalCapabilities: false,
  shellSettings: false,
  remoteUpgrade: false,
  clientDiagnostics: false,
  gitInit: false,
  providerRuntimeRefresh: false,
  configuredProviderFallback: false,
  globalEventStream: false,
  worktreeReset: false,
  worktreeCreate: false,
  legacyQuestionRequests: true,
  cliSessionResume: false,
  forms: false,
  inbox: false,
);

/// The runtime a conversation uses when the composer names no model.
const paseoDefaultProvider = 'claude';

/// How long [PaseoGateway] waits for a starting daemon to finish listing its
/// runtimes' models. Overridable so tests do not sleep.
int providerWarmupAttempts = 8;
Duration providerWarmupInterval = const Duration(milliseconds: 1500);

/// Placeholder model id for a provider that reports no model list.
const paseoDefaultModel = 'default';

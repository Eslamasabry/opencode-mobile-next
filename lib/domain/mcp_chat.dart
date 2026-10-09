/// Chat connector progress. Ready means discovery completed for the next model
/// step, never that an already-running request gained tools or ran a tool.
enum McpChatPhase {
  suggested,
  connecting,
  needsAuthentication,
  authorizing,
  checkingTools,
  toolsReady,
  connectedReadinessUnknown,
  failed,
  unavailable,
}

/// Fixed keys for localized copy; never include server errors or credentials.
enum McpChatFailure {
  unavailable,
  invalidSuggestion,
  setupRequired,
  nameConflict,
  sourceChanged,
  connectFailed,
  authenticationFailed,
  oauthUnavailable,
  notConnected,
}

final class McpChatSnapshot {
  const McpChatSnapshot(
    this.phase, {
    this.failure,
    this.authorizationUrl,
    this.manualCodeRequired = false,
  });
  final McpChatPhase phase;
  final McpChatFailure? failure;

  /// The callback port could not be owned. Paste the browser return URL/code.
  final bool manualCodeRequired;

  /// Inert and ephemeral. Open only through openExternalLink. Never persist,
  /// log, or include in diagnostics; OAuth links can contain private state.
  final Uri? authorizationUrl;

  @override
  String toString() => 'McpChatSnapshot(${phase.name}, ${failure?.name})';
}

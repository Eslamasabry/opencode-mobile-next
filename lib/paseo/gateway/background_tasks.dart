part of '../gateway.dart';

/// Work Claude Code left running in the background (a command started with
/// "run in the background"), as the shared [BackgroundTaskBook] records it
/// for every connection to this helper: the conversations list reads it
/// for a row's state, the open chat reads its own messages.
///
/// Paseo 0.9.2 and 0.11.2 forward the start (the shell step's reply "Command
/// running in background with ID: …") and the end (a synthetic
/// `task_notification` step whose metadata names the starting step's call
/// id), but not the agent SDK's task list, so a task the process took down
/// with it has no end: what the app saw of the agent's process (no
/// `activeTurn`, closed, starting) stands in for it.
mixin _PaseoBackgroundTasksApi implements BackgroundTaskGateway {
  String _real(String appID);

  BackgroundTaskBook get _backgroundBook => BackgroundTaskBook.shared;

  @override
  DateTime? agentRuntimeGapAt(String sessionID) =>
      _backgroundBook.gapAt(_real(sessionID));

  @override
  bool backgroundTasksRunning(String sessionID) =>
      _backgroundBook.running(_real(sessionID));

  @override
  bool lastTurnFailed(String sessionID) =>
      _backgroundBook.turnFailed(_real(sessionID));

  @override
  Stream<void> get backgroundTaskChanges => _backgroundBook.changes;

  /// [agent] is a snapshot of [appID]'s agent: one with no process behind
  /// it (a saved record the helper has not loaded, a closed agent, one
  /// starting) ends whatever ran before now.
  void _noteAgentProcess(String appID, Map<String, dynamic> agent) {
    final alive =
        agent.containsKey('activeTurn') &&
        (agent['status'] == 'running' ||
            agent['status'] == 'idle' ||
            agent['status'] == 'error');
    if (!alive) _backgroundBook.runtimeGap(_real(appID), DateTime.now());
  }

  /// Everything read of [appID]'s conversation.
  void _noteTimeline(String appID, List<MessageWithParts> messages) =>
      _backgroundBook.replace(_real(appID), messages);

  /// One streamed item of [appID]'s conversation.
  void _noteStreamed(String appID, MessageWithParts message) =>
      _backgroundBook.observe(_real(appID), message);
}

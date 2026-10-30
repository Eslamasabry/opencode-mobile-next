part of '../connection.dart';

// Continue with… (docs/design/agent-handoff-contract.md): the few facts only
// the connection holds. The sheet's own logic is in continue_with.dart and
// continue_with_connection.dart; nothing here sends or writes.

extension ConnectionAgentHandoff on ConnectionController {
  /// The connection that owns the agents on this phone for this conversation:
  /// the main one, or the parent of an agent's own backend.
  ConnectionController get agentHandoffHub =>
      isAgentBackend ? (_genUiParent ?? this) : this;

  /// "Working", "Waiting for you", "Failed" or "Unknown" in plain words for
  /// the record (never inferred from an idle transport alone).
  String agentHandoffLastState(String sessionID) {
    if (busySessions.contains(sessionID)) return 'Working';
    if (permissionsForSession(sessionID).isNotEmpty ||
        questions.values.any((q) => q.sessionID == sessionID)) {
      return 'Waiting for you';
    }
    if (_failedAttentionSessions.containsKey(sessionID)) return 'Failed';
    return 'Unknown';
  }

  /// The connection to the agent host for [directory] (a project under
  /// `/root/projects`). Throws a ProductException in plain words when the
  /// host cannot be reached; starts the host when Android stopped it.
  Future<ServerGateway> agentHandoffTargetGateway(String directory) async =>
      (await agentHandoffHub._paReachSource(directory)).gateway;

  /// Keeps [directory]'s agent connection open while a "Continue with…"
  /// sheet may write there; without it a folder sync could close the
  /// connection between the preview and "Start and continue". Pair each call
  /// with [releaseAgentHandoffTarget].
  void holdAgentHandoffTarget(String directory) => agentHandoffHub
      ._paHandoffHolds
      .update(directory, (count) => count + 1, ifAbsent: () => 1);

  void releaseAgentHandoffTarget(String directory) {
    final holds = agentHandoffHub._paHandoffHolds;
    final count = holds[directory];
    if (count == null) return;
    if (count <= 1) {
      holds.remove(directory);
    } else {
      holds[directory] = count - 1;
    }
  }

  /// Makes a continued conversation openable and returns the id to open it
  /// by (`/chat/<id>`). [sessionId] is the daemon's id, or this folder's
  /// gateway alias for it. The conversation opens the way the Conversations
  /// list opens it, through its row, so the link and the row land on the
  /// same chat; before the list has its row, it opens on the agent's own
  /// backend by the daemon's id, the one id that backend knows.
  Future<String> agentHandoffAdopt({
    required String directory,
    required String sessionId,
    String? agentId,
  }) async {
    final hub = agentHandoffHub;
    final source = hub._paSources[directory]?.gateway;
    final durable = source?.daemonSessionId(sessionId) ?? sessionId;
    final sourceId = _paseoSourceId(directory);
    for (final id in {sessionId, durable, ?source?.appSessionId(durable)}) {
      final row = hub._paKnownAgentRow(id);
      if (row == null ||
          row.directory != directory ||
          row.sourceId != sourceId) {
        continue;
      }
      return (await hub.openChatFeedItem(row)).sessionID;
    }
    // Without the agent's name (a link back to an agent's conversation that
    // the list no longer shows), the chat finds its backend by itself.
    if (agentId == null) return durable;
    final descriptor = hub._paCatalog.byId(agentId);
    hub._paLive.add(jsonEncode([sourceId, durable, directory]));
    hub._paOwners[durable] = await hub._paBackendFor(
      directory,
      agentName: descriptor?.name,
      agentId: agentId,
    );
    hub.notifyListeners();
    return durable;
  }
}

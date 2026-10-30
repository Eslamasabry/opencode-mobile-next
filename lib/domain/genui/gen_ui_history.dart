import '../../domain/models.dart';

/// Bounded transcript reads for recovery. Items are oldest-first within a page.
abstract interface class GenUiHistoryGateway {
  Future<bool> genUiSessionIdle(String sessionID);

  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  });
}

/// Optional cheap recovery hint for backends whose history reads load agents.
/// A missing snapshot or stamp must never suppress a history read. The stamp
/// identifies the latest user prompt, not general conversation activity.
abstract interface class GenUiRecoverySnapshotGateway {
  ({String? promptStamp, bool idle})? genUiRecoverySnapshot(String sessionID);
}

/// Optional guard for backends where a history read wakes a stored agent
/// (a provider process starts and the agent's time is re-stamped). Background
/// recovery asks first and skips a session that is not already awake; answering
/// a card and opening a chat still read it.
abstract interface class GenUiLoadGuardGateway {
  bool genUiMayLoad(String sessionID);
}

class GenUiHistoryPage {
  final List<MessageWithParts> items;
  final String? olderCursor;
  final bool hasMore;

  const GenUiHistoryPage({
    required this.items,
    this.olderCursor,
    required this.hasMore,
  });
}

/// A local, partial observation, never a server-wide attention total.
///
/// Null counts mean unknown, not zero. No request/session content or credentials
/// are retained. Source freshness is unknown until a public cache provides it.
class AttentionItem {
  const AttentionItem({
    required this.profileID,
    required this.profileName,
    required this.isSelected,
    required this.hasConnectionCache,
    this.pendingRequests,
    this.runningSessions,
    this.unreadSessions,
  });

  final String profileID;
  final String profileName;
  final bool isSelected;
  final bool hasConnectionCache;
  final int? pendingRequests;
  final int? runningSessions;
  final int? unreadSessions;
}

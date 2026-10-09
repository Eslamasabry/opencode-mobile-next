/// Presentation-neutral connection truth shared by every screen.
enum ConnectionStatusPhase {
  hidden,
  connecting,
  reconnecting,
  connected,
  credentialsRequired,

  /// The saved password (or connection token) could not be read back from
  /// the phone's secure storage, so nothing was tried: only entering it
  /// again helps. Ranked with [credentialsRequired], ahead of transport.
  credentialsUnreadable,
  notAnswering,
}

class ConnectionStatusSnapshot {
  const ConnectionStatusSnapshot({
    required this.phase,
    this.profileId,
    this.serverName = '',
    this.since,
    this.usesToken = false,
    this.retrying = false,
    this.quiet = false,
    this.attemptRevision = 0,
  });

  final ConnectionStatusPhase phase;
  final String? profileId;
  final String serverName;
  final DateTime? since;
  final bool usesToken;
  final bool retrying;

  /// An established link is recovering within its bounded presentation grace.
  final bool quiet;
  final int attemptRevision;

  bool get visible =>
      !quiet &&
      phase != ConnectionStatusPhase.hidden &&
      phase != ConnectionStatusPhase.connected;
  bool get waiting =>
      !quiet &&
      (phase == ConnectionStatusPhase.connecting ||
          phase == ConnectionStatusPhase.reconnecting);
  bool get reachable => phase == ConnectionStatusPhase.connected;
}

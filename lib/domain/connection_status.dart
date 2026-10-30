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

  /// The phone's own server is alive but slow to answer (a heavy command is
  /// running): a quiet neutral line, never a failure or a Restart.
  serverBusy,
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
    this.nextAttemptAt,
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

  /// When the link's own retry timer tries again, while one is set and the
  /// app may reconnect by itself (`RetryingEventChannel`); null while a try
  /// is in flight, when nothing will retry, or when the link is up.
  final DateTime? nextAttemptAt;

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

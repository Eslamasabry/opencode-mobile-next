/// Narrow native observations. No process arguments, profile IDs or log text.
class AgentHelperStatus {
  const AgentHelperStatus({
    this.running,
    this.lastExitAt,
    this.lastExitCode,
    this.lastStopRequested,
    this.possibleResourceKill = false,
  });

  factory AgentHelperStatus.fromMap(Map<Object?, Object?> map) {
    final at = map['lastExitAtMs'];
    return AgentHelperStatus(
      running: map['running'] is bool ? map['running'] as bool : null,
      lastExitAt: at is int && at > 0 && at <= 8640000000000000
          ? DateTime.fromMillisecondsSinceEpoch(at, isUtc: true)
          : null,
      lastExitCode: map['lastExitCode'] is int
          ? map['lastExitCode'] as int
          : null,
      lastStopRequested: map['lastStopRequested'] is bool
          ? map['lastStopRequested'] as bool
          : null,
      possibleResourceKill: map['exitReason'] == 'memory_or_phantom_kill',
    );
  }

  final bool? running;
  final DateTime? lastExitAt;
  final int? lastExitCode;
  final bool? lastStopRequested;
  final bool possibleResourceKill;

  bool get unexpectedStop =>
      running == false && lastExitAt != null && lastStopRequested == false;

  /// Contract copy for the frontend to localize; SIGKILL is not proof of cause.
  String? get notice => !unexpectedStop
      ? null
      : possibleResourceKill
      ? 'The agent helper stopped. Android may have stopped it to free memory or limit background processes. Restart it and try again.'
      : 'The agent helper stopped unexpectedly. Restart it and try again.';
}

/// Updating the helper that runs the agents on a person's computer (Paseo's
/// daemon). Protocol-neutral: the UI knows the steps, not the wire.
library;

/// Where an update of the computer's helper is.
enum HostUpdatePhase { starting, downloading, installing, complete }

/// How an update ended. A failed update carries the reason in words for a
/// Details fold only; the screen's own copy never repeats it.
final class HostUpdateResult {
  const HostUpdateResult({
    required this.success,
    this.previousVersion,
    this.newVersion,
    this.reason,
  });

  final bool success;
  final String? previousVersion;
  final String? newVersion;

  /// The computer's own words for why it failed. Technical: Details only.
  final String? reason;

  /// It worked and nothing newer was there to install.
  bool get alreadyUpToDate =>
      success &&
      previousVersion != null &&
      newVersion != null &&
      previousVersion == newVersion;
}

/// A gateway whose computer can update its own agent helper.
abstract interface class HostDaemonUpdateGateway {
  /// The helper that answered can be updated from here.
  bool get hostUpdateSupported;

  /// The helper's version as it last introduced itself; null before it did.
  String? get hostVersion;

  /// Asks the computer to install the newest helper and restart it. Reports
  /// each [HostUpdatePhase] as the computer announces it. The connection
  /// drops while the helper restarts; the result arrives first.
  Future<HostUpdateResult> updateHostDaemon({
    void Function(HostUpdatePhase phase)? onProgress,
  });
}

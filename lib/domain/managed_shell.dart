/// A server-owned, non-interactive command. Host paths and process IDs are
/// deliberately absent from the presentation model.
enum ManagedShellStatus { running, exited, timeout, killed, unknown }

class ManagedShell {
  const ManagedShell({
    required this.id,
    required this.command,
    required this.status,
    required this.startedAt,
    this.completedAt,
    this.exitCode,
    this.sessionID,
  });

  final String id;
  final String command;
  final ManagedShellStatus status;
  final DateTime startedAt;
  final DateTime? completedAt;
  final int? exitCode;
  final String? sessionID;
  bool get running => status == ManagedShellStatus.running;
}

class ManagedShellList {
  const ManagedShellList({required this.supported, this.shells = const []});
  final bool supported;
  final List<ManagedShell> shells;
}

class ManagedShellOutput {
  const ManagedShellOutput({
    required this.text,
    required this.cursor,
    required this.size,
    required this.truncated,
  });
  final String text;

  /// Absolute byte offsets supplied by the server, never Dart string lengths.
  final int cursor;
  final int size;
  final bool truncated;
}

abstract class ManagedShellGateway {
  Future<ManagedShellList> loadRunningShells();
  Future<ManagedShell?> getManagedShell(String id);
  Future<ManagedShellOutput> readManagedShellOutput(
    String id, {
    required int cursor,
    int limit = 65536,
  });
  Future<void> stopManagedShell(String id);
  Future<ManagedShell> setManagedShellTimeout(String id, Duration? timeout);

  /// A reconnect alone does not prove a server restart. The runtime identity
  /// lets a visible viewer distinguish a restart from an ordinary reconnect.
  Future<String?> managedShellServerIdentity();
}

/// A metadata-only snapshot. No prompts, titles, paths, tool arguments, output,
/// or credentials belong in this value. Idle is not evidence of success.
class CompletionDigest {
  const CompletionDigest({
    required this.sessionID,
    required this.idleAt,
    required this.pendingDecisions,
    this.changedFiles,
  });

  final String sessionID;
  final int idleAt;

  /// Session-wide server summary, not a per-run diff or verification result.
  final int? changedFiles;

  /// Null means the pending-request snapshot is loading or unavailable.
  final int? pendingDecisions;
}

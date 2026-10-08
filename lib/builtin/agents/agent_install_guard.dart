import '../../domain/agent_catalog.dart';

/// Explicit QA build admission floor. Normal builds keep the pinned estimate.
/// Raising the existing native requirement exercises its real storage guard;
/// this never changes available space or enables a runtime capability.
final class AgentInstallGuard {
  const AgentInstallGuard({
    this.minimumFreeBytes = const int.fromEnvironment(
      'OC_QA_AGENT_INSTALL_MIN_FREE_BYTES',
    ),
  });

  final int minimumFreeBytes;
  static const agentIds = {'codex', 'gemini', 'qwen', 'goose', 'omp-acp', 'fx'};

  int? downloadBytesFor(AgentDescriptor agent) {
    int? bytes;
    for (final artifact
        in agent.recipe?.artifacts.values ?? const <AgentArtifact>[]) {
      final size = artifact.downloadBytes;
      if (size != null && (bytes == null || size > bytes)) bytes = size;
    }
    if (!agentIds.contains(agent.id) || minimumFreeBytes <= 0) return bytes;
    // Native admission doubles this metadata. Saturate at its signed-long
    // ceiling so a mistyped QA flag cannot overflow into a lower requirement.
    const maximum = 0x7fffffffffffffff;
    final requested = minimumFreeBytes > maximum ? maximum : minimumFreeBytes;
    final estimate = requested ~/ 2 + requested % 2;
    return bytes == null || estimate > bytes ? estimate : bytes;
  }
}

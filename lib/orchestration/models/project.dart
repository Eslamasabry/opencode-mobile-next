/// A project (Gas City "rig") that owns work, runs and agents.
///
/// Plain value; [raw] keeps the provider payload for Technical details.
class OrchestrationProject {
  const OrchestrationProject({
    required this.id,
    required this.name,
    this.directory,
    this.rig,
    this.suspended = false,
    this.raw = const {},
  });

  /// Provider identifier; stable across refreshes.
  final String id;

  /// Human label. Falls back to [id] when the provider has none.
  final String name;

  /// Working directory on the host, when reported.
  final String? directory;

  /// Gas City rig name this project maps to, when distinct from [id].
  final String? rig;

  /// The host reports the project switched off (Gas City rig `suspended`:
  /// explicitly, or because every agent in it is suspended).
  final bool suspended;

  /// Untouched provider payload.
  final Map<String, Object?> raw;

  @override
  String toString() => 'OrchestrationProject($id, $name)';
}

/// An agent's own switches (Claude Code's fast mode and the like), which each
/// agent lists for itself. Protocol-neutral: the UI shows what the agent
/// offers in plain words and never learns the wire.
library;

enum AgentFeatureKind { toggle, choice }

/// One value a choice switch can take.
final class AgentFeatureOption {
  const AgentFeatureOption({required this.id, required this.label});

  final String id;
  final String label;
}

/// One switch the agent offers for this conversation (or, before it starts,
/// for the agent and model about to be used).
final class AgentFeature {
  const AgentFeature({
    required this.id,
    required this.label,
    required this.kind,
    this.description,
    this.on = false,
    this.selected,
    this.options = const [],
  });

  /// The agent's own name for it; the screen maps known ones to its words.
  final String id;

  /// The agent's own label, shown only for a switch the app has no words for.
  final String label;
  final String? description;
  final AgentFeatureKind kind;

  /// A toggle's state.
  final bool on;

  /// A choice's current option id; null: none chosen.
  final String? selected;
  final List<AgentFeatureOption> options;
}

/// A gateway whose agents have their own switches.
abstract interface class AgentFeatureGateway {
  bool get agentFeaturesSupported;

  /// The agent's name for the person ("Claude Code"); null when unknown.
  String? agentFeaturesOwner(String sessionID);

  /// The switches this conversation's agent offers now, read from the
  /// server. Empty when its model offers none.
  Future<List<AgentFeature>> agentFeatures(String sessionID);

  /// Sets one switch and returns the switches as the server now has them.
  /// [value] is a bool for a toggle and an option id for a choice. Throws
  /// when the server refuses, leaving the old value in place.
  Future<List<AgentFeature>> setAgentFeature(
    String sessionID,
    String featureId,
    Object value,
  );
}

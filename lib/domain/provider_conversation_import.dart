/// Conversations started in an agent's own app (Claude Code on the person's
/// computer) that this server can bring into its own list. Protocol-neutral.
library;

import '../api/models.dart' show Session;

/// One conversation that can be imported. Its [handle] is the agent's own
/// reference to it: kept for the import call, never shown.
final class ImportableConversation {
  const ImportableConversation({
    required this.agentId,
    required this.handle,
    required this.directory,
    required this.lastUsed,
    this.title,
    this.firstPrompt,
  });

  final String agentId;
  final String handle;

  /// The project folder it was used in.
  final String directory;
  final DateTime lastUsed;
  final String? title;
  final String? firstPrompt;

  /// What the list calls it: its title, else the first thing that was asked;
  /// null when it has neither (the screen then says "Untitled").
  String? get displayTitle => title ?? firstPrompt;
}

final class ImportableConversations {
  const ImportableConversations({
    required this.items,
    this.alreadyImported = 0,
  });

  /// Newest first.
  final List<ImportableConversation> items;

  /// How many more were left out because they are already in this list.
  final int alreadyImported;
}

abstract interface class ProviderConversationImportGateway {
  bool get providerImportSupported;

  /// The project folder the list is about, as the server names it.
  String get providerImportDirectory;

  /// The agent's own conversations for this project that are not here yet.
  Future<ImportableConversations> importableConversations();

  /// Brings one in and returns it as a conversation of this server.
  Future<Session> importConversation(ImportableConversation conversation);
}

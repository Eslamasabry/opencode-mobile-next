part of '../connection.dart';

// Chats-first Home: every conversation across projects (see domain/chat_feed.dart).

/// The UI's door to the chat feed; the UI never needs more than the contract.
ChatFeedSource chatFeedSourceOf(ConnectionController c) => c;

/// [ConnectionController]'s [ChatFeedSource].
mixin _ConnectionControllerChatFeed on ChangeNotifier
    implements ChatFeedSource {
  @override
  bool get chatFeedAcrossProjects => false;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) =>
      const ChatFeedSnapshot(items: [], acrossProjects: false);

  @override
  List<ProjectSummary> get projectSummaries => const [];

  @override
  Future<void> refreshChatFeed() async {}

  @override
  String? get lastUsedProjectDirectory => null;

  @override
  Future<void> rememberLastUsedProject(String directory) async {}

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) =>
      throw const ProductException('Starting a chat here is not ready yet.');

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);
}

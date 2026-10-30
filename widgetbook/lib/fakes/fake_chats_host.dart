// A fake for the chats list: a feed over fixed rows and a host that does
// nothing when the screen asks it to open or start something. Adapted from
// test/support/chats_fakes.dart (the app never imports test/ from lib/).
import 'package:flutter/widgets.dart';
import 'package:opencode_mobile/builtin/git_link_repair.dart'
    show GitRepairActions;
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProviderConversationImportGateway, WorkspaceProject;
import 'package:opencode_mobile/ui/kit/kit_feed_item.dart' show KitFeedRunning;
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';

/// A [ChatFeedSource] over fixed rows, filtered as the contract says.
class FakeFeed implements ChatFeedSource {
  FakeFeed({
    this.items = const [],
    this.projects = const [],
    this.loading = false,
    this.complete = true,
    this.stillLoading = const [],
    this.unreachableServers = const [],
  });

  final List<ChatFeedItem> items;
  final List<ProjectSummary> projects;
  final bool loading;
  final bool complete;
  final List<String> stillLoading;
  final List<String> unreachableServers;

  @override
  bool get chatFeedAcrossProjects => true;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) =>
      ChatFeedSnapshot(
        items: [
          for (final item in items)
            if (chatFeedMatches(item, filter)) item,
        ],
        loading: loading,
        complete: complete,
        stillLoading: stillLoading,
        unreachableServers: unreachableServers,
      );

  @override
  List<ProjectSummary> get projectSummaries => projects;

  @override
  Future<void> refreshChatFeed() async {}

  @override
  String? get lastUsedProjectDirectory => projects.firstOrNull?.directory;

  @override
  Future<void> rememberLastUsedProject(String directory) async {}

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) async =>
      'ses_started';

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);
}

/// The connections the list can show, as a fixed list.
class FakeSources implements ChatListSources {
  FakeSources(this.sources);
  List<ChatListSource> sources;

  @override
  List<ChatListSource> get chatListSources => sources;

  @override
  Future<void> retryChatListSource(String id) async {}

  @override
  Future<void> setChatListSourceShown(String id, bool shown) async {}
}

/// A [ChatsHost] that answers nothing: opening a row does nothing.
class FakeHost implements ChatsHost {
  FakeHost(this.feed, {this.sources});

  final FakeFeed feed;
  final FakeSources? sources;

  @override
  ChatFeedSource get source => feed;

  @override
  ChatListSources? get listSources => sources;

  @override
  Listenable? get listenable => null;

  @override
  PhoneAgentsSource? get agents => null;

  @override
  String? get startAgentName => null;

  @override
  ProviderConversationImportGateway? get conversationImport => null;

  @override
  bool get hasBuiltInProfile => false;

  @override
  String? get profileId => null;

  @override
  String connectionName(BuildContext context, String id, String fallback) =>
      fallback;

  @override
  Widget? listRequest(BuildContext context, ChatFeedItem item) => null;

  @override
  KitFeedRunning? runningOf(ChatFeedItem item) => null;

  @override
  int get inboxCount => feed
      .chatFeed()
      .items
      .where((item) => item.status == ChatStatus.needsYou)
      .length;

  @override
  Future<void> openInbox(BuildContext context) async {}

  @override
  Widget leftoverNotice(BuildContext context) => const SizedBox.shrink();

  @override
  String modelValue(BuildContext context) => 'Server default';

  @override
  Future<void> chooseModel(BuildContext context) async {}

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async =>
      null;

  @override
  Future<String?> showStartedChat(
    BuildContext context, {
    required String sessionID,
  }) async => null;

  @override
  Future<String?> openProject(BuildContext context) async => null;

  @override
  Future<WorkspaceProject?> separateCopyProject(String directory) async => null;

  @override
  GitRepairActions? projectGitRepair(String directory) => null;

  @override
  Future<String?> startSeparateCopy(
    BuildContext context,
    WorkspaceProject project, {
    String initialTask = '',
  }) async => null;

  @override
  Future<String> startChat(
    String directory, {
    required String agentId,
    String? prompt,
  }) => feed.startChatIn(directory, firstPrompt: prompt);

  @override
  Future<String?> openNewChatReplacing(
    BuildContext context,
    ChatFeedItem old,
  ) async => null;

  @override
  Future<void> openLink(BuildContext context, Uri uri) async {}

  @override
  void closeApp() {}

  @override
  Future<void> switchToBuiltIn(BuildContext context) async {}
}

/// One row of the fake feed.
ChatFeedItem feedRow(
  String id,
  String title, {
  String dir = '/home/dev/shopfront',
  String project = 'shopfront',
  bool git = true,
  ChatStatus status = ChatStatus.idle,
  required Duration ago,
  String preview = '',
  String agentId = defaultChatAgentId,
  String? agentLabel,
  String? sourceId,
  String? sourceLabel,
  bool finishedUnseen = false,
  double? contextUsage,
}) => ChatFeedItem(
  sessionID: id,
  title: title,
  directory: dir,
  projectName: project,
  isGit: git,
  status: status,
  lastActivity: DateTime.now().subtract(ago),
  preview: preview,
  agentId: agentId,
  agentLabel: agentLabel,
  sourceId: sourceId,
  sourceLabel: sourceLabel,
  finishedUnseen: finishedUnseen,
  contextUsage: contextUsage,
);

ProjectSummary feedProject(
  String name, {
  int chats = 1,
  int running = 0,
  int needs = 0,
  bool git = true,
}) => ProjectSummary(
  directory: '/home/dev/$name',
  name: name,
  isGit: git,
  chatCount: chats,
  runningCount: running,
  needsYouCount: needs,
);

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:opencode_mobile/builtin/git_link_repair.dart'
    show GitRepairActions;
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/phone_agents_source.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProviderConversationImportGateway, WorkspaceProject;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_feed_item.dart' show KitFeedRunning;
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;
// AUDIT TOOLING (audit/style-coherence): no-op unless AUDIT_STYLE is set.
import '../../tool/capture/audit/audit_env.dart';
import 'agents_fakes.dart';

/// A [ChatFeedSource] over a fixed list, filtered as the contract says.
class FakeChatFeedSource implements ChatFeedSource {
  FakeChatFeedSource({
    this.items = const [],
    this.projects = const [],
    this.acrossProjects = true,
    this.complete = true,
    this.lastUsed,
  });

  List<ChatFeedItem> items;
  List<ProjectSummary> projects;
  bool acrossProjects;
  bool complete;
  List<String> stillLoading = const [];
  List<String> unreachableServers = const [];
  List<String> incompleteSources = const [];
  String? lastUsed;

  int refreshes = 0;
  final started = <({String directory, String? prompt})>[];
  Object? startError;
  String startedID = 'ses_started';

  @override
  bool get chatFeedAcrossProjects => acrossProjects;

  @override
  ChatFeedSnapshot chatFeed([ChatFeedFilter filter = ChatFeedFilter.all]) {
    final kept = [
      for (final item in items)
        if ((filter.includeSubagents || !item.isSubagent) &&
            (!filter.otherFolders || item.inOtherFolder) &&
            (filter.projectDirectory == null ||
                item.directory == filter.projectDirectory) &&
            (!(filter.needsYou || filter.running) ||
                (filter.needsYou && item.status == ChatStatus.needsYou) ||
                (filter.running && item.status == ChatStatus.running)))
          item,
    ];
    return ChatFeedSnapshot(
      items: kept,
      acrossProjects: acrossProjects,
      complete: complete,
      stillLoading: stillLoading,
      unreachableServers: unreachableServers,
      incompleteSources: incompleteSources,
    );
  }

  @override
  List<ProjectSummary> get projectSummaries => projects;

  @override
  Future<void> refreshChatFeed() async => refreshes++;

  @override
  String? get lastUsedProjectDirectory => lastUsed;

  @override
  Future<void> rememberLastUsedProject(String directory) async =>
      lastUsed = directory;

  @override
  Future<String> startChatIn(String directory, {String? firstPrompt}) async {
    started.add((directory: directory, prompt: firstPrompt));
    final error = startError;
    if (error != null) throw error;
    return startedID;
  }

  @override
  bool isTemporaryProject(String? directory) =>
      isTemporaryProjectDirectory(directory);
}

/// A [ChatsHost] that records what the screens ask for.
class FakeChatsHost implements ChatsHost {
  FakeChatsHost(this.fake);

  final FakeChatFeedSource fake;
  final opened = <String>[];
  final shown = <String>[];
  int projectOpens = 0;

  /// What "Open a project" returns.
  String? openProjectResult;

  /// The leftover-process notice the Conversations tab shows, if a test has
  /// one to show.
  Widget leftover = const SizedBox.shrink();

  /// The project a separate copy is offered for (null: not offered).
  WorkspaceProject? copyProject;
  final copies = <String>[];

  /// What the separate-copy step resolves with (null: closed).
  String? copyResult;

  /// The agent a new conversation starts with, if the server says.
  @override
  String? startAgentName;

  /// What can be imported from the agent's own app; null hides the entry.
  @override
  ProviderConversationImportGateway? conversationImport;

  /// The agents on this phone; null hides every agent surface.
  FakePhoneAgentsSource? phoneAgents;
  final startedAgents = <String>[];
  final replaced = <String>[];
  final links = <Uri>[];
  int closed = 0;

  /// Whether this phone has a saved built-in server to switch to.
  bool builtInProfile = false;

  /// The connected profile's id; null keeps no draft on the device.
  @override
  String? profileId;

  /// The connections the list can show; null: only the one.
  @override
  FakeListSources? listSources;

  @override
  String connectionName(BuildContext context, String id, String fallback) =>
      fallback;

  /// The request card each conversation shows in the list, by session id.
  final requestCards = <String, Widget>{};

  @override
  Widget? listRequest(BuildContext context, ChatFeedItem item) =>
      requestCards[item.sessionID];

  /// What each running conversation does now, by session id.
  final running = <String, KitFeedRunning>{};

  @override
  KitFeedRunning? runningOf(ChatFeedItem item) => running[item.sessionID];
  int switched = 0;

  /// What the "Needs you" chip counts (the Inbox's count): by default every
  /// conversation of the feed that waits on the person, across projects.
  int? inboxCountOverride;

  @override
  int get inboxCount =>
      inboxCountOverride ??
      fake.items.where((item) => item.status == ChatStatus.needsYou).length;

  /// How many times the Inbox was opened.
  int inboxOpens = 0;

  @override
  Future<void> openInbox(BuildContext context) async => inboxOpens++;

  @override
  bool get hasBuiltInProfile => builtInProfile;

  @override
  Future<void> switchToBuiltIn(BuildContext context) async => switched++;

  @override
  ChatFeedSource get source => fake;

  @override
  Listenable? get listenable => phoneAgents;

  @override
  PhoneAgentsSource? get agents => phoneAgents;

  @override
  Future<String> startChat(
    String directory, {
    required String agentId,
    String? prompt,
  }) {
    startedAgents.add(agentId);
    return fake.startChatIn(directory, firstPrompt: prompt);
  }

  @override
  Future<String?> openNewChatReplacing(
    BuildContext context,
    ChatFeedItem old,
  ) async {
    final id = await phoneAgents!.startNewChatReplacing(
      old,
      newChatAcknowledged: true,
    );
    replaced.add(id);
    return null;
  }

  @override
  Future<void> openLink(BuildContext context, Uri uri) async => links.add(uri);

  @override
  void closeApp() => closed++;

  @override
  Widget leftoverNotice(BuildContext context) => leftover;

  @override
  Future<WorkspaceProject?> separateCopyProject(String directory) async =>
      copyProject;

  /// What New conversation's Repair runs; null offers none.
  GitRepairActions? gitRepair;

  @override
  GitRepairActions? projectGitRepair(String directory) => gitRepair;

  final copyTasks = <String>[];

  @override
  Future<String?> startSeparateCopy(
    BuildContext context,
    WorkspaceProject project, {
    String initialTask = '',
  }) async {
    copies.add(project.directory);
    copyTasks.add(initialTask);
    return copyResult;
  }

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async {
    opened.add(item.sessionID);
    return openGate?.future;
  }

  /// When set, opening waits for it (a conversation slow to reopen).
  Completer<String?>? openGate;

  @override
  Future<String?> showStartedChat(
    BuildContext context, {
    required String sessionID,
  }) async {
    shown.add(sessionID);
    Navigator.of(context).pop();
    return null;
  }

  @override
  Future<String?> openProject(BuildContext context) async {
    projectOpens++;
    return openProjectResult;
  }

  var modelPicks = 0;

  @override
  String modelValue(BuildContext context) => 'Server default';

  @override
  Future<void> chooseModel(BuildContext context) async => modelPicks++;
}

/// The app around [home] with the fake host.
Widget chatsApp(
  FakeChatsHost host,
  Widget home, {
  bool light = false,
  double textScale = 1,
  Locale locale = const Locale('en'),
  LocalTerminalSessions? terminal,
  ThemeData? theme,
}) => ProviderScope(
  overrides: [
    chatsHostProvider.overrideWithValue(host),
    if (terminal != null) localTerminalProvider.overrideWithValue(terminal),
  ],
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: theme ?? AppTheme.forLocale(captureTheme(light: light), locale),
    locale: locale,
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: auditWrap(context, child!),
    ),
    // The app's own surface: pages sit on a Material, so text carries no
    // debug underline.
    home: Builder(
      builder: (context) => Material(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: home,
      ),
    ),
  ),
);

ChatFeedItem chat(
  String id,
  String title, {
  String dir = '/root/projects/alpha',
  String project = 'alpha',
  bool git = true,
  ChatStatus status = ChatStatus.idle,
  required DateTime at,
  String preview = '',
  String? parent,
  String agentId = defaultChatAgentId,
  String? agentLabel,
  String? sourceId,
  String? sourceLabel,
  bool finishedUnseen = false,
  bool stoppedByPerson = false,
  double? contextUsage,
}) => ChatFeedItem(
  sessionID: id,
  title: title,
  directory: dir,
  projectName: project,
  isGit: git,
  status: status,
  lastActivity: at,
  preview: preview,
  parentID: parent,
  agentId: agentId,
  agentLabel: agentLabel,
  sourceId: sourceId,
  sourceLabel: sourceLabel,
  finishedUnseen: finishedUnseen,
  stoppedByPerson: stoppedByPerson,
  contextUsage: contextUsage,
);

ProjectSummary project(
  String name, {
  int chats = 1,
  int running = 0,
  int needs = 0,
  bool git = true,
  String? kind,
}) => ProjectSummary(
  directory: '/root/projects/$name',
  name: name,
  isGit: git,
  chatCount: chats,
  runningCount: running,
  needsYouCount: needs,
  kind: kind,
);

/// A [ChatListSources] the test sets and reads back.
class FakeListSources implements ChatListSources {
  FakeListSources(this.sources);
  List<ChatListSource> sources;
  final changes = <(String, bool)>[];

  @override
  List<ChatListSource> get chatListSources => sources;

  final retried = <String>[];

  @override
  Future<void> retryChatListSource(String id) async => retried.add(id);

  @override
  Future<void> setChatListSourceShown(String id, bool shown) async {
    changes.add((id, shown));
    sources = [
      for (final source in sources)
        source.id == id
            ? ChatListSource(
                id: source.id,
                name: source.name,
                kind: source.kind,
                shown: shown,
                onThisPhone: source.onThisPhone,
                main: source.main,
                unreachable: source.unreachable,
              )
            : source,
    ];
  }
}

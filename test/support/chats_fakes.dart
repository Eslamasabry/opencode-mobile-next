import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_mobile/domain/chat_feed.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show WorkspaceProject;
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/chats/chats_host.dart';

import '../../tool/capture/fixtures.dart' show captureTheme;

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

  @override
  ChatFeedSource get source => fake;

  @override
  Listenable? get listenable => null;

  @override
  Widget leftoverNotice(BuildContext context) => leftover;

  @override
  Future<WorkspaceProject?> separateCopyProject(String directory) async =>
      copyProject;

  @override
  Future<String?> startSeparateCopy(
    BuildContext context,
    WorkspaceProject project,
  ) async {
    copies.add(project.directory);
    return copyResult;
  }

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async {
    opened.add(item.sessionID);
    return null;
  }

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

  @override
  Widget modelChip(BuildContext context) => KitComposerChips.model(
    label: 'Server default',
    state: KitModelChipState.serverDefault,
    onPressed: () {},
  );
}

/// The app around [home] with the fake host.
Widget chatsApp(
  FakeChatsHost host,
  Widget home, {
  bool light = false,
  double textScale = 1,
  Locale locale = const Locale('en'),
}) => ProviderScope(
  overrides: [chatsHostProvider.overrideWithValue(host)],
  child: MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: captureTheme(light: light),
    locale: locale,
    supportedLocales: AppLocalizations.supportedLocales,
    localizationsDelegates: AppLocalizations.localizationsDelegates,
    builder: (context, child) => MediaQuery(
      data: MediaQuery.of(
        context,
      ).copyWith(textScaler: TextScaler.linear(textScale)),
      child: child!,
    ),
    home: home,
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

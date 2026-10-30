import 'dart:async';

import 'package:clock/clock.dart';
import 'package:flutter/widgets.dart';

import '../../domain/chat_feed.dart' show ChatFeedItem, ChatStatus;
import '../../domain/relative_age.dart';
import '../../l10n/app_localizations.dart';
import '../../state/connection.dart';
import '../app_iconography.dart';
import '../desktop/shortcuts.dart';
import '../navigation/last_project.dart';
import '../screens/chats/chats_host.dart' show ConnectionChatsHost;
import '../screens/guide_screen.dart';
import '../screens/inbox/inbox_items.dart' show inboxCount;
import '../kit/kit_page_route.dart';
import '../screens/project_folder_actions.dart';
import '../screens/project_hub_screen.dart';
import '../screens/scheduling/schedule_routes.dart';
import '../screens/servers_screen.dart' show ServersRouteRequest;
import '../screens/team/team_page.dart';

/// Search's own empty state (owner, 2026-10-20, mockup 6): before anything
/// is typed, a "Go to" group of every place the bottom bar and other
/// screens gave one tap to, then the last three conversations. Nothing is a
/// new screen level: these are the launcher's rows, found by the same
/// matcher once something is typed (the Recent rows step aside then; the
/// conversations are searched as themselves).
///
/// Rows are gated on [ServerCapabilities] and what the connection has (a
/// team, a saved server), never on the server's flavor. [target] is a
/// context under the navigator that is alive when a row runs; [atShell]
/// hands an intent to the shell root (Ctrl/Cmd+1..3 and the terminal do the
/// same).
List<DesktopCommand> goToCommands(
  AppLocalizations l10n, {
  required ConnectionController controller,
  required BuildContext? Function() target,
  required void Function(Intent intent) atShell,
  String mod = 'Ctrl',
  Future<String?> Function(BuildContext context, ChatFeedItem item)? openChat,
}) {
  final capabilities = controller.capabilities;
  void open(void Function(BuildContext context) run) {
    final context = target();
    if (context != null && context.mounted) run(context);
  }

  final project =
      lastUsedProjectOf(controller) ??
      (controller.directory?.isNotEmpty == true ? controller.directory : null);
  final projectName = project == null ? null : _basename(project);
  final noServers = controller.store.profiles.isEmpty;
  final recent = [
    for (final item in controller.chatFeed().items)
      if (!item.isSubagent) item,
  ]..sort((a, b) => b.lastActivity.compareTo(a.lastActivity));
  final now = clock.now();
  final chat =
      openChat ??
      (context, item) =>
          ConnectionChatsHost(controller).openChat(context, item);

  // Everything that waits on the person, across servers (the Inbox).
  final waiting = inboxCount(controller);

  return [
    DesktopCommand(
      key: const ValueKey('go-to-needs-you'),
      section: DesktopCommandSection.goTo,
      label: l10n.chatsHomeNeedsYou,
      icon: AppIconography.inbox,
      hint: l10n.goToNeedsYouHint(waiting),
      keywords: l10n.inboxTitle,
      onInvoke: () => atShell(const OpenChatsIntent(needsYou: true)),
    ),
    if (ProjectHub.isAvailable(capabilities))
      DesktopCommand(
        key: const ValueKey('go-to-files'),
        section: DesktopCommandSection.goTo,
        label: l10n.shellTabFiles,
        detail: projectName,
        icon: AppIconography.files,
        hint: l10n.e7LocaleUiFilesHint,
        keywords: projectName,
        keys: '$mod + 2',
        onInvoke: () => atShell(const SelectDestinationIntent(1)),
      ),
    if (capabilities.projectManagement)
      DesktopCommand(
        key: const ValueKey('go-to-projects'),
        section: DesktopCommandSection.goTo,
        label: l10n.e7ProjectProjectsTitle,
        icon: AppIconography.folderOpen,
        keywords: l10n.discoverProjectAliases,
        onInvoke: () => open((context) async {
          final directory = await ProjectFolderActions.openFolder(
            context,
            controller,
          );
          if (directory == null || !context.mounted) return;
          rememberLastUsedProject(controller, directory);
          unawaited(
            openProjectFiles(context, controller, directory: directory),
          );
        }),
      ),
    DesktopCommand(
      key: const ValueKey('go-to-settings'),
      section: DesktopCommandSection.goTo,
      label: l10n.librarySettingsTitle,
      icon: AppIconography.settingsGear,
      hint: l10n.e7LocaleUiMoreHint,
      keys: '$mod + 3',
      onInvoke: () => atShell(const SelectDestinationIntent(2)),
    ),
    DesktopCommand(
      key: const ValueKey('go-to-servers'),
      section: DesktopCommandSection.goTo,
      label: noServers ? l10n.kitCapServerAnyEnable : l10n.e7SetupServers,
      icon: AppIconography.database,
      keywords: l10n.settingsHubSearchSavedServersAliases,
      onInvoke: () => open(
        (context) => unawaited(
          Navigator.of(context).pushNamed(
            '/servers',
            arguments: noServers ? const ServersRouteRequest.add() : null,
          ),
        ),
      ),
    ),
    if (capabilities.terminal)
      DesktopCommand(
        key: const ValueKey('go-to-terminal'),
        section: DesktopCommandSection.goTo,
        label: l10n.libraryTerminalTitle,
        icon: AppIconography.terminal,
        keywords: l10n.discoverTerminalAliases,
        keys: '$mod + `',
        onInvoke: () => atShell(const OpenTerminalIntent()),
      ),
    if (canSchedulePrompts(controller))
      DesktopCommand(
        key: const ValueKey('go-to-schedule'),
        section: DesktopCommandSection.goTo,
        label: l10n.scheduleTitle,
        icon: AppIconography.clock,
        onInvoke: () => open(
          (context) => unawaited(openScheduledPrompts(context, controller)),
        ),
      ),
    if (controller.orchestration != null)
      DesktopCommand(
        key: const ValueKey('go-to-team'),
        section: DesktopCommandSection.goTo,
        label: l10n.teamUiHomeTitle,
        icon: AppIconography.agent,
        keywords: l10n.discoverTeamAliases,
        onInvoke: () =>
            open((context) => unawaited(openTeamPage(context, controller))),
      ),
    DesktopCommand(
      key: const ValueKey('go-to-help'),
      section: DesktopCommandSection.goTo,
      label: l10n.settingsHubGroupHelp,
      icon: AppIconography.guide,
      keywords:
          '${l10n.onboardingSetupGuide} ${l10n.settingsHubSearchGuideAliases}',
      onInvoke: () => open(
        (context) =>
            unawaited(pushKitPage<void>(context, (_) => const GuideScreen())),
      ),
    ),
    for (final item in recent.take(3))
      DesktopCommand(
        key: ValueKey('go-to-recent-${item.sessionID}'),
        section: DesktopCommandSection.recent,
        onlyWhenEmpty: true,
        label: item.title,
        icon: AppIconography.chat,
        // What the conversation is waiting on comes first, as on its row.
        hint: [
          if (item.status == ChatStatus.needsYou) l10n.chatsHomeNeedsYou,
          if (item.status == ChatStatus.running) l10n.chatsHomeRowWorking,
          item.projectName,
          relativeAgeLabel(
            now.difference(item.lastActivity),
            at: item.lastActivity,
            l10n: l10n,
          ),
        ].join(' · '),
        onInvoke: () => open((context) => unawaited(chat(context, item))),
      ),
  ];
}

String _basename(String directory) {
  final parts = directory.split('/').where((part) => part.isNotEmpty);
  return parts.isEmpty ? directory : parts.last;
}

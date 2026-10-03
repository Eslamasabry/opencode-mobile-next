import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show SystemNavigator;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_linux.dart' show BuiltinLinux;
import '../../../domain/chat_feed.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../domain/server_gateway.dart' show WorkspaceProject;
import '../../../platform/platform_capabilities.dart';
import '../../../termux/bridge.dart' show TermuxBridge;
import '../../widgets/termux_phone_tools.dart' show TermuxRunawayWatcher;
import '../../widgets/work_status_line.dart' show WorkRunawayNotice;
import '../isolated_task_sheet.dart' show showIsolatedTaskSheet;
import '../project_hub_screen.dart' show projectForDirectory;
import '../termux_processes_screen.dart' show TermuxProcessesScreen;
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/profiles.dart' show ServerProfile;
import '../../kit/kit.dart';
import '../../widgets/external_link.dart' show openExternalLink;
import '../../widgets/pickers.dart' show showModelPicker;
import '../../widgets/product_states.dart' show productErrorText;
import '../project_folder_actions.dart';
import '../library_screen.dart' show defaultModelLabel;
import '../servers_screen.dart' show ServersRouteRequest;

/// What the Chats screens need from the app besides the feed itself. One
/// seam: the screens never touch the connection controller, and a test hands
/// in a fake. [ConnectionChatsHost] is the real one.
abstract interface class ChatsHost {
  /// The feed and start-a-chat contract.
  ChatFeedSource get source;

  /// Fires when [source] or the header's facts change; null when it never
  /// does (a fake).
  Listenable? get listenable;

  /// Opens the conversation in its project. Says why in plain words (by the
  /// returned text) when it cannot; null when it opened.
  Future<String?> openChat(BuildContext context, ChatFeedItem item);

  /// Opens a chat that already exists by id (right after starting one),
  /// replacing the current screen. Null when it opened, else plain words.
  Future<String?> showStartedChat(
    BuildContext context, {
    required String sessionID,
  });

  /// The "Open a project" sheet; the chosen folder, or null when closed.
  Future<String?> openProject(BuildContext context);

  /// The model chip for the start screen's composer.
  Widget modelChip(BuildContext context);

  /// The leftover-process notice (a helper burning CPU on this phone's own
  /// server) as a quiet status with its Stop. Draws nothing until the watcher
  /// reports something, and never on a server that is not on this phone.
  Widget leftoverNotice(BuildContext context);

  /// The project a task can be started in a separate copy of (a worktree of
  /// it) for [directory], or null where the server cannot make one: the
  /// option is then not shown.
  Future<WorkspaceProject?> separateCopyProject(String directory);

  /// Starts a task in a separate copy with the existing step, moving the
  /// connection to [project] first. Resolves with the new conversation's id
  /// once it exists in the copy, or null when the person closed the step.
  Future<String?> startSeparateCopy(
    BuildContext context,
    WorkspaceProject project,
  );

  /// The agents on this phone, or null when this connection has none (the
  /// agent chip and every agent surface stay hidden).
  PhoneAgentsSource? get agents;

  /// Starts a conversation with [agentId] in [directory] and returns its id.
  Future<String> startChat(
    String directory, {
    required String agentId,
    String? prompt,
  });

  /// After the person agreed to Start new conversation: makes the
  /// replacement draft and opens it. Null when it opened, else plain words.
  Future<String?> openNewChatReplacing(BuildContext context, ChatFeedItem old);

  /// Hands [uri] (a page the app did not author) to the safe link opener.
  Future<void> openLink(BuildContext context, Uri uri);

  /// Closes the app so the person can reopen it (the restart offer).
  void closeApp();

  /// True when this phone has a saved built-in server to switch to.
  bool get hasBuiltInProfile;

  /// Connects to the built-in server through the Servers screen's own
  /// connect flow.
  Future<void> switchToBuiltIn(BuildContext context);
}

/// The one place the app's connection becomes a [ChatFeedSource]. Until the
/// controller implements the contract (state agent), reading this fails
/// loudly instead of showing an empty feed.
final chatsHostProvider = Provider<ChatsHost>(
  (ref) => ConnectionChatsHost(ref.watch(connProvider)),
);

/// [ChatsHost] over the live [ConnectionController].
class ConnectionChatsHost implements ChatsHost {
  ConnectionChatsHost(this._conn);

  final ConnectionController _conn;

  @override
  ChatFeedSource get source => chatFeedSourceOf(_conn);

  @override
  Listenable? get listenable => _conn;

  static final _safeID = RegExp(r'^[A-Za-z0-9_-]+$');

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async {
    final l10n = _l10n(context);
    if (!_safeID.hasMatch(item.sessionID)) return l10n.chatsHomeOpenFailed;
    final navigator = Navigator.of(context);
    try {
      // Opening switches the connection to the conversation's project first.
      // The gateway that owns the row is found by its identity.
      final route = await _conn.openChatFeedItem(item);
      if (!navigator.mounted) return null;
      unawaited(_conn.prefetchSessionTail(route.sessionID));
      await navigator.pushNamed('/chat/${route.sessionID}');
      return null;
    } catch (error) {
      return productErrorText(error, l10n: l10n);
    }
  }

  @override
  PhoneAgentsSource? get agents => _conn;

  @override
  Future<String> startChat(
    String directory, {
    required String agentId,
    String? prompt,
  }) {
    final feed = source;
    return feed is AgentChatFeedSource
        ? feed.startAgentChatIn(
            directory,
            agentId: agentId,
            firstPrompt: prompt,
          )
        : feed.startChatIn(directory, firstPrompt: prompt);
  }

  @override
  Future<String?> openNewChatReplacing(
    BuildContext context,
    ChatFeedItem old,
  ) async {
    final l10n = _l10n(context);
    final navigator = Navigator.of(context);
    try {
      final id = await _conn.startNewChatReplacing(
        old,
        newChatAcknowledged: true,
      );
      if (!_safeID.hasMatch(id)) return l10n.chatsHomeOpenFailed;
      await navigator.pushNamed('/chat/$id');
      return null;
    } catch (error) {
      return productErrorText(error, l10n: l10n);
    }
  }

  @override
  Future<void> openLink(BuildContext context, Uri uri) async {
    await openExternalLink(context, uri.toString());
  }

  @override
  void closeApp() => unawaited(SystemNavigator.pop());

  ServerProfile? get _builtInProfile => _conn.store.profiles
      .where((profile) => BuiltinLinux.managesServerUrl(profile.baseUrl))
      .firstOrNull;

  @override
  bool get hasBuiltInProfile => _builtInProfile != null;

  @override
  Future<void> switchToBuiltIn(BuildContext context) async {
    final profile = _builtInProfile;
    if (profile == null) return;
    await Navigator.of(
      context,
    ).pushNamed('/servers', arguments: ServersRouteRequest.connect(profile.id));
  }

  @override
  Future<String?> showStartedChat(
    BuildContext context, {
    required String sessionID,
  }) async {
    final l10n = _l10n(context);
    if (!_safeID.hasMatch(sessionID)) return l10n.chatsHomeOpenFailed;
    unawaited(Navigator.of(context).pushReplacementNamed('/chat/$sessionID'));
    return null;
  }

  @override
  Future<String?> openProject(BuildContext context) =>
      ProjectFolderActions.openFolder(context, _conn);

  @override
  Widget leftoverNotice(BuildContext context) {
    if (!(platformCapabilities.supportsTermux &&
        TermuxBridge.managesServerUrl(_conn.profile?.baseUrl))) {
      return const SizedBox.shrink();
    }
    return TermuxRunawayWatcher(
      builder: (context, notice) => leftoverNoticeLine(context, notice),
    );
  }

  @override
  Future<WorkspaceProject?> separateCopyProject(String directory) async {
    final capabilities = _conn.capabilities;
    if (!capabilities.projectManagement || !capabilities.worktreeCreate) {
      return null;
    }
    try {
      final repository = await _conn.prepareActionRepository();
      if (repository == null) return null;
      final project = projectForDirectory(
        await repository.listProjects(),
        directory,
      );
      if (project == null || project.directory.trim().isEmpty) return null;
      return project;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<String?> startSeparateCopy(
    BuildContext context,
    WorkspaceProject project,
  ) async {
    if (!ConnectionController.sameDirectoryPath(
      _conn.directory ?? '',
      project.directory,
    )) {
      await _conn.selectLocation(directory: project.directory);
      if (_conn.locationError != null || !context.mounted) return null;
    }
    final session = await showIsolatedTaskSheet(
      context,
      controller: _conn,
      project: project,
    );
    return session?.id;
  }

  @override
  Widget modelChip(BuildContext context) => ListenableBuilder(
    listenable: _conn,
    builder: (context, _) {
      final l10n = _l10n(context);
      final model = _conn.selectedModel;
      final picked = model != null && model.modelID.isNotEmpty;
      return KitComposerChips.model(
        label: picked
            ? defaultModelLabel(_conn, l10n)
            : l10n.modelServerDefault,
        state: picked
            ? KitModelChipState.chosen
            : KitModelChipState.serverDefault,
        onPressed: () => unawaited(showModelPicker(context)),
        chipKey: const ValueKey('chats-new-model'),
      );
    },
  );

  AppLocalizations _l10n(BuildContext context) =>
      Localizations.of<AppLocalizations>(context, AppLocalizations) ??
      lookupAppLocalizations(Localizations.localeOf(context));
}

/// The leftover-process notice as a quiet kit status (the same one the Work
/// tab used): wording, Stop and Dismiss, and See what's running under More.
Widget leftoverNoticeLine(BuildContext context, WorkRunawayNotice? notice) {
  final l10n = AppLocalizations.of(context);
  final status = notice?.status(
    l10n,
    onSeeRunning: () => unawaited(
      pushKitPage<void>(context, (_) => const TermuxProcessesScreen()),
    ),
  );
  return KitStatusContribution(
    status: status == null
        ? null
        : KitStatus(
            kind: KitStatusKind.work,
            id: 'work:${status.id}',
            key: ValueKey('work-status-${status.id}'),
            icon: status.icon,
            tone: status.tone,
            message: status.message,
            messageKey: status.messageKey,
            action: status.action,
            more: status.more,
            onDismiss: status.onDismiss,
          ),
    child: const SizedBox.shrink(),
  );
}

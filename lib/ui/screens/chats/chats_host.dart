import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show SystemNavigator;
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_linux.dart' show BuiltinLinux;
import '../../../domain/chat_feed.dart';
import '../../../domain/genui/gen_ui.dart';
import '../../../domain/phone_agents_source.dart';
import '../../../domain/server_gateway.dart'
    show
        AgentFeatureGateway,
        ProviderConversationImportGateway,
        WorkspaceProject;
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
import '../../widgets/agent_card_photos.dart';
import '../../widgets/agent_card_view.dart';
import '../../widgets/external_link.dart' show openAgentSignInPage;
import '../../widgets/phone_server_card.dart' show serverDisplayName;
import '../chat_screen.dart' show questionRequestCard;
import '../chat/form_flow.dart' show capturedFormRequestCard;
import '../chat/permission_sheet.dart'
    show
        PermissionAnswers,
        answerPermissionRequest,
        permissionAlwaysStep,
        permissionRequestCard;
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

  /// The name of the agent a new conversation here starts with (Claude Code,
  /// Pi), for the composer's hint; null when the server does not say.
  String? get startAgentName;

  /// The agent's own conversations this server can bring in (Claude Code's);
  /// null where it cannot, which hides the "Import from Claude Code" entry.
  ProviderConversationImportGateway? get conversationImport;

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

  /// The connections whose conversations the list shows, and the person's
  /// choice of which; null when there is only the one.
  ChatListSources? get listSources;

  /// A saved server's name as the server switcher says it ("This phone ·
  /// Termux"), or [fallback] for anything else.
  String connectionName(BuildContext context, String id, String fallback);

  /// The request [item]'s conversation waits on, as the card that answers
  /// it right here (Allow once, Always allow, Reject, with Undo); null when
  /// there is none or it can only be answered in the conversation.
  Widget? listRequest(BuildContext context, ChatFeedItem item);

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
  ChatListSources? get listSources => _conn;

  @override
  Widget? listRequest(BuildContext context, ChatFeedItem item) {
    // Native feed requests belong to the list's gateway, independently of
    // whether this conversation has an open chat backend.
    final owner = _conn.connectionForRow(item);
    final answers = owner == null ? null : PermissionAnswers.of(owner);
    final feedAnswers = PermissionAnswers.of(_conn);
    return ListenableBuilder(
      listenable: Listenable.merge([
        _conn,
        ?owner,
        ?answers,
        feedAnswers,
        _conn.delayedAnswers,
        ?owner?.delayedAnswers,
      ]),
      builder: (context, _) {
        final feedPermission = _conn.permissionForFeedItem(item);
        final nativeFeed = item.sourceId?.startsWith('paseo:') ?? false;
        final waiting = nativeFeed
            ? [?feedPermission]
            : owner?.permissionsForSession(item.sessionID) ?? [];
        final permissionOwner = nativeFeed ? _conn : owner;
        final permissionAnswers = nativeFeed ? feedAnswers : answers;
        final question = _conn.questionForFeedItem(item);
        final form = _conn.formRequestForFeedItem(item);
        final card = _conn.waitingCardsForFeedItem(item).firstOrNull;
        if (waiting.isEmpty &&
            question == null &&
            form == null &&
            card == null) {
          return const SizedBox.shrink();
        }
        Widget? request;
        if (permissionOwner != null &&
            permissionAnswers != null &&
            waiting.isNotEmpty) {
          final permission = waiting.first;
          void answer(String reply) => unawaited(
            answerPermissionRequest(permissionOwner, permission, reply),
          );
          request = permissionRequestCard(
            context,
            key: ValueKey('chats-request-${permission.id}'),
            inList: true,
            permission: permission,
            who: item.agentLabel ?? 'OpenCode',
            answered: permissionAnswers.answerFor(permission.id),
            // Held for its Undo window: collapsed, with Undo, as in the chat.
            heldLabel: permissionOwner.delayedAnswers.heldLabel(permission.id),
            onUndo: () => permissionOwner.delayedAnswers.undo(permission.id),
            onAllow: () => answer('once'),
            onReject: () => answer('reject'),
            alwaysAllow: permissionAlwaysStep(
              context,
              permission: permission,
              supported: nativeFeed
                  ? permission.always.isNotEmpty
                  : permissionOwner.capabilities.persistentPermissionGrants,
              onConfirmed: () => answer('always'),
            ),
          );
        }
        final questionView = question == null
            ? null
            : questionRequestCard(
                context,
                key: ValueKey('chats-question-${item.identity}|${question.id}'),
                owner: _conn,
                item: item,
                question: question,
                inList: true,
              );
        final formView = form == null
            ? null
            : capturedFormRequestCard(
                context,
                _conn,
                form,
                key: ValueKey('chats-form-${form.identity.key}'),
                who: item.agentLabel ?? 'OpenCode',
                // A list holds many rows: its Answer is secondary, as a
                // list question's is, so no row claims the screen's primary.
                secondary: true,
                inList: true,
              );
        // Questions and forms wait behind a permission request, and a card
        // follows them: the list answers the request first, and the buttons of the
        // others are secondary here.
        final cardView = card == null
            ? null
            : AgentCardView(
                key: ValueKey('chats-card-${card.callID}'),
                controller: _conn,
                parse: GenUiParsed(card),
                agentLabel: item.agentLabel ?? 'OpenCode',
                busy: owner?.busySessions.contains(item.sessionID) ?? false,
                inList: true,
                // Photos ride on the row's own connection; until it exists
                // a photo card says to answer in the conversation.
                photos:
                    owner != null &&
                        owner.capabilities.promptAttachments &&
                        platformCapabilities.supportsPromptPhotos
                    ? StoreAgentCardPhotos(
                        store: owner.promptPhotos,
                        profileID: owner.profile?.id ?? '',
                        sessionID: item.sessionID,
                        directory: item.directory,
                        workspace: card.scope.workspace,
                      )
                    : null,
              );
        final shown = [?request, ?questionView, ?formView, ?cardView];
        if (shown.length == 1) return shown.single;
        final gap = SizedBox(height: KitTokens.of(context).space2);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            for (final (index, view) in shown.indexed) ...[
              if (index > 0) gap,
              view,
            ],
          ],
        );
      },
    );
  }

  @override
  String connectionName(BuildContext context, String id, String fallback) {
    for (final profile in _conn.store.profiles) {
      if (profile.id == id) {
        return serverDisplayName(
          profile,
          AppLocalizations.of(context),
          among: _conn.store.profiles,
        );
      }
    }
    return fallback;
  }

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
      final backend = _conn.backendForConversation(route.sessionID) ?? _conn;
      unawaited(backend.prefetchSessionTail(route.sessionID));
      await navigator.pushNamed('/chat/${route.sessionID}');
      return null;
    } catch (error) {
      return productErrorText(error, l10n: l10n);
    }
  }

  @override
  PhoneAgentsSource? get agents => _conn;

  @override
  String? get startAgentName {
    final api = _conn.api;
    return api is AgentFeatureGateway &&
            (api as AgentFeatureGateway).agentFeaturesSupported
        ? (api as AgentFeatureGateway).agentFeaturesOwner('')
        : null;
  }

  @override
  ProviderConversationImportGateway? get conversationImport {
    final api = _conn.api;
    return api is ProviderConversationImportGateway &&
            (api as ProviderConversationImportGateway).providerImportSupported
        ? api as ProviderConversationImportGateway
        : null;
  }

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
    // Only the agent sign-in step opens links here.
    await openAgentSignInPage(context, uri.toString());
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

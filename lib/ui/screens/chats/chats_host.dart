import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../builtin/builtin_server.dart' show builtinLinuxProvider;
import '../../../domain/chat_feed.dart';
import '../../../domain/connection_status.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../../state/phone_host.dart' show PhoneHostKind;
import '../../app_theme.dart';
import '../../desktop/shortcuts.dart';
import '../../kit/kit.dart';
import '../../widgets/pickers.dart' show showModelPicker;
import '../../widgets/phone_server_card.dart'
    show runPhoneServerAction, serverDisplayName;
import '../../widgets/product_states.dart' show productErrorText;
import '../../widgets/server_switcher_sheet.dart';
import '../project_folder_actions.dart';
import '../library_screen.dart' show defaultModelLabel;
import '../servers_screen.dart' show ServersRouteRequest;
import '../this_phone_screen.dart' show openThisPhone;

/// What the Chats screens need from the app besides the feed itself. One
/// seam: the screens never touch the connection controller, and a test hands
/// in a fake. [ConnectionChatsHost] is the real one.
abstract interface class ChatsHost {
  /// The feed and start-a-chat contract.
  ChatFeedSource get source;

  /// Fires when [source] or the header's facts change; null when it never
  /// does (a fake).
  Listenable? get listenable;

  /// The header's server pill: name, state word and tone.
  ({String name, String status, AppStatusTone tone}) serverPill(
    AppLocalizations l10n,
  );

  /// Opens the server switcher and acts on its choice.
  Future<void> openServerSwitcher(BuildContext context);

  /// The same launcher as Ctrl/Cmd+K, or null when search is not offered.
  VoidCallback? search(BuildContext context);

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
}

/// The one place the app's connection becomes a [ChatFeedSource]. Until the
/// controller implements the contract (state agent), reading this fails
/// loudly instead of showing an empty feed.
final chatsHostProvider = Provider<ChatsHost>(
  (ref) => ConnectionChatsHost(ref.watch(connProvider), ref),
);

/// [ChatsHost] over the live [ConnectionController].
class ConnectionChatsHost implements ChatsHost {
  ConnectionChatsHost(this._conn, this._ref);

  final ConnectionController _conn;
  final Ref _ref;

  @override
  ChatFeedSource get source => chatFeedSourceOf(_conn);

  @override
  Listenable? get listenable => _conn;

  @override
  ({String name, String status, AppStatusTone tone}) serverPill(
    AppLocalizations l10n,
  ) {
    final name = serverDisplayName(
      _conn.profile,
      l10n,
      among: _conn.store.profiles,
    );
    final (status, tone) = switch (_conn.connectionStatus.phase) {
      ConnectionStatusPhase.connected => (
        l10n.e7WorkspaceConnected,
        AppStatusTone.ok,
      ),
      ConnectionStatusPhase.connecting => (
        l10n.e7WorkspaceConnecting,
        AppStatusTone.progress,
      ),
      ConnectionStatusPhase.reconnecting => (
        l10n.mcpReconnecting,
        AppStatusTone.progress,
      ),
      _ => (l10n.e7WorkspaceOffline, AppStatusTone.failure),
    };
    return (name: name, status: status, tone: tone);
  }

  @override
  Future<void> openServerSwitcher(BuildContext context) async {
    final navigator = Navigator.of(context);
    final choice = await showServerSwitcher(context, _conn);
    if (choice == null || !navigator.mounted || !context.mounted) return;
    switch (choice) {
      case ServerSwitcherOpenServers(:final ServersRouteRequest? request):
        unawaited(navigator.pushNamed('/servers', arguments: request));
      case ServerSwitcherOpenPhoneSetup():
        unawaited(openThisPhone(context, kind: PhoneHostKind.termux));
      case ServerSwitcherPhoneAction(
        :final action,
        :final profileID,
        :final bytesUsed,
      ):
        final profile = _conn.store.profiles
            .where((profile) => profile.id == profileID)
            .firstOrNull;
        if (profile == null) return;
        final removed = await runPhoneServerAction(
          context,
          action,
          connection: _conn,
          linux: _ref.read(builtinLinuxProvider),
          profile: profile,
          bytesUsed: bytesUsed,
        );
        if (removed && navigator.mounted && _conn.api == null) {
          unawaited(
            navigator.pushNamedAndRemoveUntil('/servers', (_) => false),
          );
        }
      case ServerSwitcherLeave(:final alreadyDisconnected):
        if (!alreadyDisconnected) await _conn.disconnect();
        if (!navigator.mounted) return;
        unawaited(navigator.pushNamedAndRemoveUntil('/servers', (_) => false));
    }
  }

  @override
  VoidCallback? search(BuildContext context) {
    final perform = AppShortcutScope.performOf(context);
    return perform == null
        ? null
        : () => perform(const OpenCommandPaletteIntent());
  }

  static final _safeID = RegExp(r'^[A-Za-z0-9_-]+$');

  @override
  Future<String?> openChat(BuildContext context, ChatFeedItem item) async {
    final l10n = _l10n(context);
    if (!_safeID.hasMatch(item.sessionID)) return l10n.chatsHomeOpenFailed;
    final navigator = Navigator.of(context);
    try {
      // Opening switches the connection to the conversation's project first.
      await _conn.selectLocationForExistingSession(directory: item.directory);
      if (!navigator.mounted) return null;
      unawaited(_conn.prefetchSessionTail(item.sessionID));
      await navigator.pushNamed('/chat/${item.sessionID}');
      return null;
    } catch (error) {
      return productErrorText(error, l10n: l10n);
    }
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

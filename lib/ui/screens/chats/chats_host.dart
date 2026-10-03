import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../domain/chat_feed.dart';
import '../../../l10n/app_localizations.dart';
import '../../../state/connection.dart';
import '../../kit/kit.dart';
import '../../widgets/pickers.dart' show showModelPicker;
import '../../widgets/product_states.dart' show productErrorText;
import '../project_folder_actions.dart';
import '../library_screen.dart' show defaultModelLabel;

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

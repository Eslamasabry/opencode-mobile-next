import 'package:flutter/foundation.dart';

import '../domain/genui/gen_ui.dart';
import '../domain/mcp_catalog.dart';
import '../domain/mcp_chat.dart';
import 'connection.dart';
import 'mcp_chat_controller.dart';
import 'setup_registry_store.dart';

/// What the suggested-connector card needs from one conversation-bound MCP
/// controller (docs/design/BD3-mcp-chat-contract.md "Frozen state API"). The
/// card reads [snapshot] and calls these; it never sees a gateway, a token or
/// a server error. Tests fake this surface.
abstract interface class ConnectorChat implements Listenable {
  McpChatSnapshot get snapshot;

  /// The MCP server name the card connects (the catalogue's name for it).
  String get serverName;

  Future<void> connect(String serverId);
  Future<void> startOAuth();
  Future<void> completeOAuth(String callbackOrCode);
  Future<void> refresh();

  /// Cancels the pending sign-in. On OpenCode 1 this also clears the saved
  /// sign-in for the connector, so the caller confirms first.
  Future<void> cancelOAuth();
}

/// How the card stands before the person has pressed anything.
sealed class ConnectorResolution {
  const ConnectorResolution({this.item});

  /// The catalogue listing the suggestion names; null when this device's
  /// loaded catalogue has no such listing.
  final McpCatalogItem? item;
}

/// This runtime cannot connect catalogue suggestions from chat (Claude Code,
/// or a server without the feature): the card shows no Connect.
final class ConnectorUnsupported extends ConnectorResolution {
  const ConnectorUnsupported({super.item});
}

/// The loaded catalogue has no such connector, or the suggestion cannot be
/// used in this conversation any more.
final class ConnectorNotListed extends ConnectorResolution {
  const ConnectorNotListed({super.item});
}

/// The listing is real but needs a key, settings or a local package, which the
/// card never collects: it points to Tools.
final class ConnectorNeedsSetup extends ConnectorResolution {
  const ConnectorNeedsSetup({required McpCatalogItem super.item});
}

/// The server already reports this connector as connected.
final class ConnectorAlreadyConnected extends ConnectorResolution {
  const ConnectorAlreadyConnected({required McpCatalogItem super.item});
}

/// The person can press Connect; [chat] carries the progress.
final class ConnectorReady extends ConnectorResolution {
  const ConnectorReady({
    required McpCatalogItem super.item,
    required this.chat,
    required this.canSignIn,
  });

  final ConnectorChat chat;

  /// This runtime's sign-in round trip can finish inside the chat.
  final bool canSignIn;
}

/// Gives a card its connector state. The one place that knows how a catalogue
/// id becomes a listing and a controller.
abstract interface class ConnectorCardHost {
  Future<ConnectorResolution> resolve(GenUiCard card);
}

/// The live host over a [ConnectionController]. Reads the saved catalogue only
/// (no network, no consent change) and asks the connection for the
/// conversation-bound controller.
final class ConnectionConnectorCardHost implements ConnectorCardHost {
  const ConnectionConnectorCardHost(this._connection);

  final ConnectionController _connection;

  @override
  Future<ConnectorResolution> resolve(GenUiCard card) async {
    final suggestion = card.connector;
    final profileId = _connection.profile?.id;
    if (suggestion == null || profileId == null) {
      return const ConnectorNotListed();
    }
    final item = await _catalogItem(profileId, suggestion.catalogId);
    if (_connection.profile?.id != profileId) {
      return ConnectorNotListed(item: item);
    }
    final caps = _connection.capabilities;
    if (!(caps.genUi &&
        caps.mcpChatConnect &&
        caps.serverCatalog &&
        caps.mcpRuntimeAdds)) {
      return ConnectorUnsupported(item: item);
    }
    if (item == null) return const ConnectorNotListed();
    final connectable =
        item.runtime == McpCatalogRuntime.hosted &&
        item.draft != null &&
        !item.needsKey &&
        !item.needsSettings;
    ConnectorChat? chat;
    if (connectable) {
      try {
        chat = _ControllerChat(_connection.createMcpChatController(card, item));
      } catch (_) {
        return ConnectorNotListed(item: item);
      }
      // A connection in progress (or finished) keeps its words when the card
      // is built again, for example after it scrolled out of the list.
      if (chat.snapshot.phase != McpChatPhase.suggested) {
        return ConnectorReady(
          item: item,
          chat: chat,
          canSignIn: caps.mcpChatOAuth && caps.mcpOAuth,
        );
      }
    }
    if (await _alreadyConnected(item, profileId)) {
      return ConnectorAlreadyConnected(item: item);
    }
    if (chat == null) return ConnectorNeedsSetup(item: item);
    return ConnectorReady(
      item: item,
      chat: chat,
      canSignIn: caps.mcpChatOAuth && caps.mcpOAuth,
    );
  }

  Future<McpCatalogItem?> _catalogItem(String profileId, String id) async {
    final registry = SetupRegistryStore(
      _connection.store.prefs,
      profileId: profileId,
    );
    try {
      await registry.load();
      final snapshot = registry.snapshot;
      if (snapshot.cachedAt == null) return null;
      for (final entry in snapshot.entries) {
        if (entry.name == id) return McpCatalogItem.from(entry);
      }
      return null;
    } catch (_) {
      return null;
    } finally {
      await registry.dispose();
    }
  }

  Future<bool> _alreadyConnected(McpCatalogItem item, String profileId) async {
    try {
      final gateway = await _connection.prepareActionRepository();
      if (gateway == null) return false;
      final rows = await gateway.listMcpServers();
      if (_connection.profile?.id != profileId) return false;
      return rows.any(
        (row) => row.name == item.serverName && row.status == 'connected',
      );
    } catch (_) {
      // Unknown is not "connected": the card offers Connect, and the
      // controller itself refuses a name that already exists.
      return false;
    }
  }
}

final class _ControllerChat implements ConnectorChat {
  _ControllerChat(this._controller);

  final McpChatController _controller;

  @override
  McpChatSnapshot get snapshot => _controller.snapshot;
  @override
  String get serverName => _controller.item.serverName;
  @override
  void addListener(VoidCallback listener) => _controller.addListener(listener);
  @override
  void removeListener(VoidCallback listener) =>
      _controller.removeListener(listener);
  @override
  Future<void> connect(String serverId) => _controller.connect(serverId);
  @override
  Future<void> startOAuth() => _controller.startOAuth();
  @override
  Future<void> completeOAuth(String callbackOrCode) =>
      _controller.completeOAuth(callbackOrCode);
  @override
  Future<void> refresh() => _controller.refresh();
  @override
  Future<void> cancelOAuth() => _controller.cancelOAuth();
}

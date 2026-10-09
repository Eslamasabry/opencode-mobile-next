part of '../connection.dart';

mixin _ConnectionControllerMcpChat on ChangeNotifier {
  ConnectionController get _self;
  final _mcpChatControllers = <Object, McpChatController>{};
  final _mcpChatOrigins = <Object, Object>{};
  final _mcpChatChecks = <Object, bool Function()>{};

  /// Create only from an authoritative card for this exact connected source.
  /// The item is resolved from the catalog by the frontend, never from payload
  /// URLs or commands. Multiple renderings share the same pending operation.
  McpChatController createMcpChatController(
    GenUiCard card,
    McpCatalogItem item,
  ) => _self._createMcpChatController(card, item);
}

extension _ConnectionControllerMcpChatImpl on ConnectionController {
  McpChatController _createMcpChatController(
    GenUiCard card,
    McpCatalogItem item,
  ) {
    final observed = _genUiStateForScope(card.scope).observedCard(card);
    final owner = _connectedProfile;
    if (observed == null ||
        owner == null ||
        !capabilities.genUi ||
        _genUiScope != card.scope ||
        genUiStateForCard(card) != GenUiCardState.report ||
        observed.connector?.catalogId != item.entry.name ||
        item.serverName != catalogServerName(item.entry.name)) {
      throw const ProductException(
        'This connector suggestion is not available. Browse connectors in Tools.',
      );
    }
    card = observed;
    _mcpChatChanged();
    final selectedRevision = locationRevision;
    final selectedLocation = (directory, workspace);
    final selectedIdentity = (
      owner.id,
      owner.baseUrl,
      owner.username,
      owner.flavor,
    );
    final deletion = _profileDeletionRevisions[owner.id] ?? 0;
    final key = (selectedIdentity, selectedLocation, item.serverName);
    final existing = _mcpChatControllers[key];
    final origin = (card.identity, card.revision, item.entry.id);
    if (existing != null && !existing.isDisposed) {
      if (_mcpChatOrigins[key] == origin) return existing;
      throw const ProductException(
        'A connector with this name already exists. Check it in Tools.',
      );
    }
    if (_mcpChatControllers.length >= 32) {
      throw const ProductException(
        'This connector suggestion is not available. Browse connectors in Tools.',
      );
    }
    bool current() {
      final active = profile;
      final connected = _connectedProfile;
      return !_disposed &&
          active != null &&
          connected != null &&
          store.activeId == owner.id &&
          isProfileReadable(owner.id) &&
          (active.id, active.baseUrl, active.username, active.flavor) ==
              selectedIdentity &&
          (
                connected.id,
                connected.baseUrl,
                connected.username,
                connected.flavor,
              ) ==
              selectedIdentity &&
          locationRevision == selectedRevision &&
          (directory, workspace) == selectedLocation &&
          (_profileDeletionRevisions[owner.id] ?? 0) == deletion &&
          genUiEnabled &&
          _genUiStateForScope(card.scope).registeredReady(card.scope) &&
          _genUiScope == card.scope &&
          (_genUiStateForScope(card.scope).observedState(card) == null ||
              _genUiStateForScope(card.scope).observedState(card) ==
                  GenUiCardState.report);
    }

    final controller = McpChatController(
      item: item,
      capabilities: () => capabilities,
      current: current,
      gateway: () async {
        if (!current()) {
          throw const ProductException(
            'This conversation\'s connection changed. Reopen the connector card.',
          );
        }
        final gateway = await prepareActionRepository();
        if (!current() || gateway == null) {
          throw const ProductException(
            'Could not confirm the connection. Check its status before trying again.',
          );
        }
        // A browser round trip may replace the transport and discard its view.
        // Recover through the fresh source before authorizing any next request.
        _genUiSync();
        await _genUiStateForScope(
          card.scope,
        ).recover([GenUiTarget(card.scope, card.sessionID)]);
        if (!current() ||
            !capabilities.genUi ||
            genUiStateForCard(card) != GenUiCardState.report) {
          throw const ProductException(
            'This connector suggestion is not available. Browse connectors in Tools.',
          );
        }
        return gateway;
      },
    );
    _mcpChatControllers[key] = controller;
    _mcpChatOrigins[key] = origin;
    _mcpChatChecks[key] = current;
    return controller;
  }

  void _mcpChatChanged() {
    for (final entry in _mcpChatControllers.entries.toList()) {
      if (entry.value.isDisposed) {
        _mcpChatControllers.remove(entry.key);
        _mcpChatChecks.remove(entry.key);
        _mcpChatOrigins.remove(entry.key);
      } else if (_mcpChatChecks[entry.key]?.call() != true) {
        _mcpChatControllers.remove(entry.key);
        _mcpChatChecks.remove(entry.key);
        _mcpChatOrigins.remove(entry.key);
        entry.value.invalidate();
      }
    }
  }

  void _mcpChatDispose() {
    removeListener(_mcpChatChanged);
    for (final controller in _mcpChatControllers.values) {
      controller.dispose();
    }
    _mcpChatControllers.clear();
    _mcpChatChecks.clear();
    _mcpChatOrigins.clear();
  }
}

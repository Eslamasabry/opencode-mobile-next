part of '../connection.dart';

mixin _ConnectionControllerConnectorSearch on ChangeNotifier {
  GenUiSearchPublisher? _genUiSearchPublisher;
  String? _connectorSearchKey;
  int _connectorSearchEpoch = 0;
  DateTime? _connectorSearchRetryAfter;
  Future<void> _connectorSearchTail = Future<void>.value();
  final _connectorSearchBridges = <McpConnectorSearchBridge>[];

  /// Tests only: the registry client the connector search and the in-chat
  /// "Turn on" use, so no run reaches the public registry.
  @visibleForTesting
  SetupRegistryClient? connectorRegistryClientOverride;

  /// Turns the connector catalogue on for the active profile and loads it,
  /// from the "Turn on" in a connector search step. True when the choice is
  /// saved; a failed load still leaves it on, and the next search retries.
  Future<bool> enableConnectorCatalogue() =>
      (this as ConnectionController)._enableConnectorCatalogueImpl();
}

/// A saved catalogue older than this is refreshed before a search.
const _connectorCatalogueMaxAge = Duration(hours: 24);

/// Total time the online parts of a search may take, inside the helper's
/// 5-second request deadline.
const _connectorSearchOnlineBudget = Duration(seconds: 4);

extension _ConnectionControllerConnectorSearchImpl on ConnectionController {
  void _connectorSearchSync() {
    // Shared root helpers have a single phone owner. Temporary chat/side
    // controllers must never replace the owner's bridge descriptors.
    if (_genUiParent != null || isSideBackend || isAgentBackend) return;
    final owner = _connectedProfile;
    final generation = _generation;
    final location = (directory, workspace, locationRevision);
    final agents = genUiStatus.agents
        .where((agent) => agent.cardsQualified)
        .toList();
    final available =
        !_disposed &&
        owner != null &&
        _genUiManaged &&
        genUiEnabled &&
        status == StreamStatus.connected &&
        !_deletingReadProfiles.contains(owner.id) &&
        agents.isNotEmpty;
    final key = available
        ? jsonEncode([
            owner.id,
            owner.baseUrl,
            owner.username,
            generation,
            location.toString(),
            agents.map((agent) => agent.id).toList(),
          ])
        : null;
    if (_connectorSearchKey == key) return;
    if (key != null &&
        _connectorSearchRetryAfter?.isAfter(clock.now()) == true) {
      return;
    }
    _connectorSearchRetryAfter = null;
    _connectorSearchKey = key;
    final epoch = ++_connectorSearchEpoch;
    for (final bridge in _connectorSearchBridges) {
      unawaited(bridge.close());
    }
    _connectorSearchBridges.clear();
    if (!available) return;
    bool current(GenUiAgent agent) =>
        !_disposed &&
        epoch == _connectorSearchEpoch &&
        _connectorSearchKey == key &&
        _generation == generation &&
        (directory, workspace, locationRevision) == location &&
        store.activeId == owner.id &&
        _connectedProfile?.id == owner.id &&
        _connectedProfile?.baseUrl == owner.baseUrl &&
        _connectedProfile?.username == owner.username &&
        genUiEnabled &&
        status == StreamStatus.connected &&
        !_deletingReadProfiles.contains(owner.id) &&
        genUiStatus.agents.contains(agent);
    _connectorSearchTail = _connectorSearchTail
        .then((_) async {
          for (final agent in agents) {
            if (!current(agent)) return;
            McpConnectorSearchBridge? bridge;
            try {
              bridge = await McpConnectorSearchBridge.start(
                isCurrent: () => current(agent),
                search: (request) =>
                    _findCachedConnectors(owner.id, agent, request),
              );
              if (!current(agent)) {
                await bridge.close();
                return;
              }
              final published =
                  await (_genUiSearchPublisher ??=
                          BuiltinGenUiSearchPublisher())
                      .publish(
                        profileId: owner.id,
                        agent: agent,
                        endpoint: bridge.endpoint,
                        bearer: bridge.bearer,
                      );
              if (published && current(agent)) {
                _connectorSearchBridges.add(bridge);
              } else {
                if (current(agent)) {
                  _connectorSearchKey = null;
                  _connectorSearchRetryAfter = clock.now().add(
                    const Duration(seconds: 5),
                  );
                }
                await bridge.close();
              }
            } catch (_) {
              await bridge?.close();
              if (current(agent)) {
                _connectorSearchKey = null;
                _connectorSearchRetryAfter = clock.now().add(
                  const Duration(seconds: 5),
                );
              }
            }
          }
        })
        .catchError((Object _) {});
  }

  Future<Map<String, Object?>> _findCachedConnectors(
    String profileId,
    GenUiAgent agent,
    Map<String, dynamic> request,
  ) async {
    if (request.keys.any((key) => key != 'arguments' && key != 'directory') ||
        request['directory'] is! String) {
      throw const FormatException();
    }
    final folder = request['directory'] as String;
    if (!folder.startsWith('/') ||
        folder.length > 4096 ||
        RegExp(r'[\x00-\x1f\x7f]').hasMatch(folder)) {
      throw const FormatException();
    }
    // Validate before any inventory call. This read never refreshes the cache,
    // changes its consent, or contacts the registry.
    searchMcpConnectors(
      arguments: request['arguments'],
      entries: null,
      connected: null,
    );
    final gateway = api, operations = repository, revision = locationRevision;
    final selectedDirectory = directory, selectedWorkspace = workspace;
    final generation = _generation;
    final identity = (_connectedProfile?.baseUrl, _connectedProfile?.username);
    bool sourceCurrent() =>
        !_disposed &&
        _generation == generation &&
        _connectedProfile?.id == profileId &&
        (_connectedProfile?.baseUrl, _connectedProfile?.username) == identity &&
        status == StreamStatus.connected &&
        store.activeId == profileId &&
        identical(api, gateway) &&
        identical(repository, operations) &&
        locationRevision == revision &&
        directory == selectedDirectory &&
        workspace == selectedWorkspace &&
        genUiEnabled &&
        genUiStatus.agents.contains(agent);
    if (!sourceCurrent()) throw StateError('Source changed');
    final client = connectorRegistryClientOverride;
    final registry = SetupRegistryStore(
      store.prefs,
      profileId: profileId,
      client: client,
    );
    SetupRegistryStore? searcher;
    try {
      await registry.load();
      if (!sourceCurrent()) throw StateError('Source changed');
      if (!registry.snapshot.optedIn) {
        // No consent, no request: the agent is told to ask the person.
        return mcpConnectorCatalogueOff();
      }
      final cachedAt = registry.snapshot.cachedAt;
      final stale =
          cachedAt == null ||
          clock.now().toUtc().difference(cachedAt.toUtc()) >
              _connectorCatalogueMaxAge;
      final query = ((request['arguments'] as Map)['query'] as String).trim();
      // The registry's own answer to the words searched for. Transient: the
      // store shows it and never saves it.
      searcher = SetupRegistryStore(
        store.prefs,
        profileId: profileId,
        client: client,
      );
      await searcher.load();
      final online = <RegistryEntry>[];
      Future<void> refreshSaved() async {
        if (!stale) return;
        await registry.refresh();
      }

      final asked = searcher;
      Future<void> askRegistry() async {
        await asked.refresh(query: query);
        if (asked.snapshot.query == query &&
            asked.snapshot.status == SetupRegistryStatus.ready) {
          online.addAll(asked.snapshot.entries);
        }
      }

      try {
        await Future.wait([
          refreshSaved(),
          askRegistry(),
        ]).timeout(_connectorSearchOnlineBudget);
      } catch (_) {
        // Offline, slow or refused: the saved catalogue answers.
      }
      if (!sourceCurrent()) throw StateError('Source changed');
      final saved = registry.snapshot;
      List<RegistryEntry>? entries =
          saved.cachedAt == null && saved.entries.isEmpty
          ? null
          : saved.entries;
      if (entries == null && online.isNotEmpty) entries = const [];
      if (entries == null) {
        return searchMcpConnectors(
          arguments: request['arguments'],
          entries: null,
          connected: null,
        );
      }
      if (online.isNotEmpty) {
        final seen = <String>{};
        entries = [
          for (final entry in [...online, ...entries])
            if (seen.add(entry.name)) entry,
        ];
      }
      Map<String, bool>? connected;
      // The helper's cwd cannot establish a workspace ID. Ambiguous sources
      // return unknown instead of borrowing another project's inventory.
      final exactSource =
          agent.paseoProvider == null &&
          selectedDirectory == folder &&
          selectedWorkspace == null &&
          gateway != null &&
          gateway.capabilities.serverCatalog &&
          operations != null;
      if (exactSource) {
        try {
          if (!sourceCurrent()) throw StateError('Source changed');
          final inventory = await operations.listMcpServers().timeout(
            const Duration(milliseconds: 900),
          );
          if (!identical(api, gateway) ||
              !identical(repository, operations) ||
              locationRevision != revision ||
              directory != selectedDirectory ||
              workspace != selectedWorkspace) {
            throw const FormatException();
          }
          connected = {
            for (final server in inventory)
              server.name: server.status == 'connected',
          };
        } catch (_) {
          // The HTTP bridge independently rechecks profile and generation.
          // A location replacement while reading also invalidates the reply.
          if (!identical(api, gateway) ||
              locationRevision != revision ||
              directory != selectedDirectory ||
              workspace != selectedWorkspace) {
            throw StateError('Source changed');
          }
        }
      }
      return searchMcpConnectors(
        arguments: request['arguments'],
        entries: entries,
        connected: connected,
      );
    } finally {
      await searcher?.dispose();
      await registry.dispose();
    }
  }

  Future<bool> _enableConnectorCatalogueImpl() async {
    final profileId = store.activeId;
    if (_disposed || profileId == null) return false;
    final registry = SetupRegistryStore(
      store.prefs,
      profileId: profileId,
      client: connectorRegistryClientOverride,
    );
    try {
      await registry.load();
      await registry.setOptIn(true);
      try {
        await registry.refresh().timeout(const Duration(seconds: 30));
      } catch (_) {}
      return registry.snapshot.optedIn;
    } catch (_) {
      return false;
    } finally {
      await registry.dispose();
    }
  }

  void _connectorSearchDispose() {
    removeListener(_connectorSearchSync);
    _connectorSearchKey = null;
    _connectorSearchEpoch++;
    for (final bridge in _connectorSearchBridges) {
      unawaited(bridge.close());
    }
    _connectorSearchBridges.clear();
  }
}

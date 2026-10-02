part of '../connection.dart';

// Model, agent and variant selection, the model library and provider runtime reloads.

String _providerSetKey(Set<String> providers) =>
    (providers.toList()..sort()).join(',');

/// [ConnectionController]'s model, agent and variant selection.
mixin _ConnectionControllerModels on ChangeNotifier {
  ConnectionController get _self;

  final Map<String, String> sessionSelectionErrors = {};

  /// Default model for pickers opened outside a chat and for new sessions.
  ModelRef? selectedModel;
  String selectedAgent = '';
  String selectedVariant = '';

  /// Model choices made from inside a chat, keyed by session ID. A choice
  /// here belongs to that session only; every other session keeps using
  /// [selectedModel]. Restored per profile on connect and dropped with the
  /// session.
  Map<String, SessionModelChoice> sessionModels = {};
  ModelLibrary _modelLibrary = const ModelLibrary();
  ModelLibrary get modelLibrary => _modelLibrary;
  Future<void> _modelLibraryWrite = Future.value();

  bool get serverOwnsSessionSelection =>
      _self.api is SessionSelectionGateway ||
      (_self.api == null && _self.serverFlavor == ServerFlavor.v2);

  SessionSelection selectionForSession(String sessionID) =>
      _self._selectionForSession(sessionID);

  ModelRef? modelForSession(String sessionID) =>
      selectionForSession(sessionID).model;

  /// The variant [sessionID] sends with; see [modelForSession].
  String variantForSession(String sessionID) =>
      selectionForSession(sessionID).variant;

  String agentForSession(String sessionID) =>
      selectionForSession(sessionID).agent ?? '';

  bool sessionSelectionSaving(String sessionID) =>
      _self._selectionMutations.containsKey(sessionID);

  Future<void> waitForSessionSelection(
    String sessionID, {
    ServerGateway? expectedApi,
  }) => _self._waitForSessionSelection(sessionID, expectedApi: expectedApi);

  Future<void> selectAgentForSession(String sessionID, String name) =>
      _self._selectAgentForSession(sessionID, name);

  /// Chooses a model for one session without touching the profile default
  /// or any other session.
  Future<void> selectModelForSession(
    String sessionID,
    ModelRef ref, {
    String? variant,
    bool recordRecent = true,
  }) => _self._selectModelForSession(
    sessionID,
    ref,
    variant: variant,
    recordRecent: recordRecent,
  );

  Future<void> selectModel(ModelRef ref, {String? variant}) =>
      _self._selectModel(ref, variant: variant);

  Future<void> selectVariant(String variant) async {
    final model = selectedModel;
    if (model == null) return;
    await selectModel(model, variant: variant);
  }

  Future<void> selectAgent(String name) => _self._selectAgent(name);

  /// Reloads the catalog now: Reload, a finished sign-in, a model the
  /// server no longer has. Shares a load already running.
  Future<void> refreshCatalog() => _self._loadCatalog();

  /// Loads the catalog only when it is missing or stale (older than
  /// [catalogFreshFor], or invalidated by a change event): for pickers and
  /// sheets that open often. The shown list stays while it refreshes.
  Future<void> ensureCatalog() => _self._ensureCatalog();

  bool modelAvailable(ModelRef ref) => _self._modelAvailable(ref);

  Future<void> toggleModelFavorite(ModelRef model) =>
      _self._toggleModelFavorite(model);

  /// Cycles only this chat's next-turn selection. Recent cycling keeps the
  /// MRU order stable, otherwise repeated taps just bounce between two models.
  Future<ModelRef?> cycleModelForSession(
    String sessionID, {
    bool reverse = false,
    bool favoritesOnly = false,
  }) async {
    final next = _modelLibrary.next(
      modelForSession(sessionID),
      reverse: reverse,
      favoritesOnly: favoritesOnly,
      available: modelAvailable,
    );
    if (next == null) return null;
    await selectModelForSession(sessionID, next, recordRecent: favoritesOnly);
    return next;
  }

  /// Ask the server to rebuild its provider runtime, then reload the catalog.
  ///
  /// The manual counterpart of the one-shot heal in [_loadCatalog], for the
  /// picker's "Reload providers" action when a provider stays unloaded.
  ///
  /// Never stops a running reply: while any runs, the reload waits and
  /// [providerReloadWaitingOn] says for how many; it runs once they finish.
  Future<void> reloadProviderRuntime() => _self._reloadProviderRuntime();
}

extension _ConnectionControllerModelsImpl on ConnectionController {
  // ---------------- Selection persistence ----------------

  /// Whether [variant] is one the catalog offers for [ref]. The empty
  /// variant is always allowed.
  bool _variantAllowed(ModelRef ref, String variant) {
    if (variant.isEmpty) return true;
    final matchingModel = catalog?.models.where(
      (model) => model.providerID == ref.providerID && model.id == ref.modelID,
    );
    return matchingModel != null &&
        matchingModel.isNotEmpty &&
        matchingModel.first.variants.any(
          (item) => item.id == variant && !item.disabled,
        );
  }

  /// The body of [selectionForSession].
  SessionSelection _selectionForSession(String sessionID) =>
      serverOwnsSessionSelection
      ? sessionsById[sessionID]?.selection ??
            const SessionSelection(modelKnown: false, agentKnown: false)
      : SessionSelection(
          model: sessionModels[sessionID]?.model ?? selectedModel,
          variant: sessionModels[sessionID]?.variant ?? selectedVariant,
          agent: selectedAgent,
        );

  /// The body of [waitForSessionSelection].
  Future<void> _waitForSessionSelection(
    String sessionID, {
    ServerGateway? expectedApi,
  }) async {
    await (_selectionMutations[sessionID] ?? Future.value());
    if (expectedApi != null && !identical(api, expectedApi)) {
      throw const ProductException(
        'The connection changed. Reopen the session and try again.',
      );
    }
  }

  Future<void> _mutateSessionSelection(
    String sessionID,
    Future<bool> Function(SessionSelectionGateway gateway) mutate, {
    bool requireConfirmation = true,
  }) {
    final currentApi = api;
    final generation = _generation;
    if (currentApi is! SessionSelectionGateway) {
      return Future.error(const ProductException('OpenCode is reconnecting.'));
    }
    final previous = _selectionMutations[sessionID] ?? Future.value();
    late final Future<void> tracked;
    tracked = previous
        .catchError((Object _) {})
        .then((_) async {
          if (!_isCurrent(generation, currentApi)) {
            throw const ProductException(
              'The connection changed. Reopen the session and try again.',
            );
          }
          try {
            sessionSelectionErrors.remove(sessionID);
            final changed = await mutate(currentApi as SessionSelectionGateway);
            if (!_isCurrent(generation, currentApi)) return;
            if (changed) {
              _markSessionChanged(sessionID, affectsStatus: false);
              await _refreshOneSession(sessionID);
              if (!_isCurrent(generation, currentApi)) return;
              final error = sessionDetailsErrors[sessionID];
              if (requireConfirmation && error != null) {
                throw ProductException(error);
              }
            }
          } catch (error) {
            if (_isCurrent(generation, currentApi)) {
              // A timeout may have applied the mutation. Reconcile before retry.
              _markSessionChanged(sessionID, affectsStatus: false);
              await _refreshOneSession(sessionID);
              if (_isCurrent(generation, currentApi)) {
                sessionSelectionErrors[sessionID] = error.toString();
              }
            }
            rethrow;
          }
        })
        .whenComplete(() {
          if (identical(_selectionMutations[sessionID], tracked)) {
            _selectionMutations.remove(sessionID);
            if (!_disposed) _notifyListeners();
          }
        });
    _selectionMutations[sessionID] = tracked;
    _notifyListeners();
    return tracked;
  }

  /// The body of [selectAgentForSession].
  Future<void> _selectAgentForSession(String sessionID, String name) async {
    if (!serverOwnsSessionSelection) return selectAgent(name);
    if (name.isEmpty) return;
    await _mutateSessionSelection(sessionID, (gateway) async {
      final current = selectionForSession(sessionID);
      if (current.agentKnown && current.agent == name) return false;
      await gateway.setSessionAgent(sessionID, name);
      return true;
    });
  }

  /// The body of [selectModelForSession].
  Future<void> _selectModelForSession(
    String sessionID,
    ModelRef ref, {
    String? variant,
    bool recordRecent = true,
  }) async {
    ref = ref.normalized;
    final nextVariant = variant ?? '';
    if (catalog != null && !modelAvailable(ref)) return;
    if (!_variantAllowed(ref, nextVariant)) return;
    if (serverOwnsSessionSelection) {
      final generation = _generation;
      await _mutateSessionSelection(sessionID, (gateway) async {
        final current = selectionForSession(sessionID);
        if (current.modelKnown &&
            ModelLibrary.sameModel(current.model, ref) &&
            current.variant == nextVariant) {
          return false;
        }
        await gateway.setSessionModel(sessionID, ref, nextVariant);
        return true;
      });
      if (!_disposed && generation == _generation && recordRecent) {
        await _rememberModel(ref);
      }
      return;
    }
    sessionModels[sessionID] = SessionModelChoice(
      model: ref,
      variant: nextVariant,
    );
    final p = profile;
    final generation = _generation;
    if (recordRecent) await _rememberModel(ref);
    if (_disposed || generation != _generation) return;
    if (p != null) await store.setSessionModels(p.id, sessionModels);
    if (_disposed || generation != _generation) return;
    _notifyListeners();
  }

  void _forgetSessionModel(String sessionID) {
    if (sessionModels.remove(sessionID) == null) return;
    final p = profile;
    if (p != null) unawaited(store.setSessionModels(p.id, sessionModels));
  }

  /// The body of [selectModel].
  Future<void> _selectModel(ModelRef ref, {String? variant}) async {
    ref = ref.normalized;
    final nextVariant = variant ?? '';
    if (catalog != null && !modelAvailable(ref)) return;
    if (!_variantAllowed(ref, nextVariant)) return;
    selectedModel = ref;
    selectedVariant = nextVariant;
    final p = profile;
    final generation = _generation;
    await _rememberModel(ref);
    if (_disposed || generation != _generation) return;
    if (p != null) {
      await store.setModel(p.id, ref.providerID, ref.modelID, explicit: true);
      await store.setVariant(p.id, nextVariant);
    }
    if (_disposed || generation != _generation) return;
    _notifyListeners();
  }

  /// The body of [selectAgent].
  Future<void> _selectAgent(String name) async {
    selectedAgent = name;
    final p = profile;
    final generation = _generation;
    if (p != null) await store.setAgent(p.id, name);
    if (_disposed || generation != _generation) return;
    _notifyListeners();
  }

  /// The body of [modelAvailable].
  bool _modelAvailable(ModelRef ref) =>
      catalog?.models.any(
        (model) =>
            model.enabled &&
            ModelLibrary.sameModel(
              ref,
              ModelRef(providerID: model.providerID, modelID: model.id),
            ),
      ) ??
      false;

  Future<void> _persistModelLibrary() {
    final id = _connectedProfile?.id ?? profile?.id;
    if (id == null) return Future.value();
    final snapshot = _modelLibrary;
    final previous = _modelLibraryWrite;
    final write = () async {
      try {
        await previous;
      } catch (_) {}
      // The snapshot belongs to the captured profile, even when the user
      // changes workspace or server while storage is busy. Profile deletion
      // drains this queue before sweeping its keys.
      await store.setModelLibrary(id, snapshot);
    }();
    _modelLibraryWrite = write;
    return write;
  }

  Future<void> _rememberModel(ModelRef model) {
    _modelLibrary = _modelLibrary.remember(model);
    return _persistModelLibrary();
  }

  /// The body of [toggleModelFavorite].
  Future<void> _toggleModelFavorite(ModelRef model) async {
    if (!modelAvailable(model) && !_modelLibrary.isFavorite(model)) return;
    final before = _modelLibrary;
    final next = before.toggleFavorite(model);
    _modelLibrary = next;
    _notifyListeners();
    try {
      await _persistModelLibrary();
    } catch (_) {
      if (!_disposed && identical(_modelLibrary, next)) {
        _modelLibrary = before;
        _notifyListeners();
      }
      rethrow;
    }
  }

  /// The body of [reloadProviderRuntime].
  Future<void> _reloadProviderRuntime() async {
    final currentApi = api;
    final currentRepository = repository;
    if (currentApi != null &&
        currentRepository != null &&
        currentApi.capabilities.providerRuntimeRefresh) {
      try {
        await currentRepository.refreshProviderRuntime();
        providerReloadWaitingOn = 0;
        _providerHealDeferred = false;
        _runtimeJustRefreshed = true;
      } on ProviderRuntimeBusyException catch (busy) {
        _providerHealDeferred = true;
        _runtimeHealKey = null;
        providerReloadWaitingOn = busy.runningReplies;
        _notifyListeners();
        return;
      } catch (_) {
        // The reload below still reports whether the provider came up.
      }
    }
    await _loadCatalog();
  }

  /// Runs the provider reload that waited for replies, once none runs.
  void _resumeDeferredProviderHeal() {
    if (!_providerHealDeferred || busySessions.isNotEmpty) return;
    _providerHealDeferred = false;
    unawaited(_loadCatalog(announce: false));
  }

  Future<void> _refreshPreexistingProviderRuntime({
    required int generation,
    required ServerGateway currentApi,
    required ServerOperationsGateway currentRepository,
    required ServerProfile profile,
  }) => PerfTrace.span(
    'provider_runtime.refresh',
    () => _refreshPreexistingProviderRuntimeUntraced(
      generation: generation,
      currentApi: currentApi,
      currentRepository: currentRepository,
      profile: profile,
    ),
  );

  Future<void> _refreshPreexistingProviderRuntimeUntraced({
    required int generation,
    required ServerGateway currentApi,
    required ServerOperationsGateway currentRepository,
    required ServerProfile profile,
  }) async {
    // §7 row 25: v2 hot-reloads provider config, so there is no runtime to
    // kick — skip the probe entirely instead of failing it once per connect.
    if (!currentApi.capabilities.providerRuntimeRefresh) return;
    if (store.providerRuntimeWasRefreshed(
      profile.id,
      directory: directory,
      workspace: workspace,
    )) {
      return;
    }
    try {
      final integrations = await currentRepository.listIntegrations();
      if (!_isCurrent(generation, currentApi)) return;
      if (integrations.any((integration) => integration.connectionCount > 0)) {
        await currentRepository.refreshProviderRuntime();
        if (!_isCurrent(generation, currentApi)) return;
      }
      await store.markProviderRuntimeRefreshed(
        profile.id,
        directory: directory,
        workspace: workspace,
      );
    } catch (_) {
      // Older or temporarily unavailable servers must remain connectable. A
      // failed migration is deliberately left unmarked so a later connection
      // can retry it.
    }
  }
}

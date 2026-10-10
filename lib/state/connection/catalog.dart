part of '../connection.dart';

// The provider, agent and model catalog load.

Map<String, dynamic> _catalogMap(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : const {};

List<CatalogVariant> _catalogVariants(Object? value) {
  if (value is! Map) return const [];
  return [
    for (final entry in value.entries)
      if (entry.key.toString().isNotEmpty)
        CatalogVariant(
          id: entry.key.toString(),
          disabled: _catalogMap(entry.value)['disabled'] == true,
          options: _catalogMap(_catalogMap(entry.value)['body']).isNotEmpty
              ? _catalogMap(_catalogMap(entry.value)['body'])
              : _catalogMap(entry.value),
        ),
  ];
}

CatalogModel _catalogModelFromProvider(ProviderInfo provider, String modelID) {
  final raw = provider.modelData[modelID] ?? const <String, dynamic>{};
  final capabilities = _catalogMap(raw['capabilities']);
  final limit = _catalogMap(raw['limit']);
  final input = capabilities['input'];
  final attachments =
      capabilities['attachment'] == true ||
      (input is List && input.any((value) => value != 'text')) ||
      (input is Map &&
          input.entries.any(
            (entry) => entry.key != 'text' && entry.value == true,
          ));
  return CatalogModel(
    id: modelID,
    providerID: provider.id,
    name: raw['name']?.toString() ?? modelID,
    family: raw['family']?.toString(),
    enabled: raw['enabled'] != false,
    status: raw['status']?.toString() ?? 'unknown',
    contextLimit: (limit['context'] as num?)?.toInt() ?? 0,
    outputLimit: (limit['output'] as num?)?.toInt() ?? 0,
    reasoning: capabilities['reasoning'] == true,
    attachments: attachments,
    tools: capabilities['toolcall'] == true || capabilities['tools'] == true,
    variants: _catalogVariants(raw['variants']),
    // v1 `Model.cost` is models.dev's USD-per-million-tokens price list.
    cost: ModelCost.fromJson(raw['cost']),
    released: _catalogReleaseDate(raw['release_date']),
  );
}

/// v1 `release_date` is a `YYYY-MM-DD` string; tolerate epoch millis too.
DateTime? _catalogReleaseDate(dynamic raw) {
  if (raw is num && raw > 0) {
    return DateTime.fromMillisecondsSinceEpoch(raw.toInt());
  }
  if (raw is String && raw.trim().isNotEmpty) {
    return DateTime.tryParse(raw.trim());
  }
  return null;
}

CatalogModel _mergeCatalogModel(CatalogModel detailed, CatalogModel base) {
  return CatalogModel(
    id: detailed.id,
    providerID: detailed.providerID,
    name: detailed.name,
    family: detailed.family,
    enabled: detailed.enabled,
    status: detailed.status,
    contextLimit: detailed.contextLimit,
    outputLimit: detailed.outputLimit,
    reasoning: detailed.reasoning || base.reasoning,
    attachments: detailed.attachments || base.attachments,
    tools: detailed.tools || base.tools,
    variants: detailed.variants.isEmpty ? base.variants : detailed.variants,
    cost: detailed.cost ?? base.cost,
    released: detailed.released ?? base.released,
  );
}

/// [ConnectionController]'s provider and model catalog.
mixin _ConnectionControllerCatalog on ChangeNotifier {
  bool catalogLoading = false;
  String? catalogError;

  ProvidersResponse? providers;
  List<AgentInfo> agents = [];
  CatalogSnapshot? catalog;
  bool catalogDetailed = false;

  /// The catalog this conversation can actually use. Provider-bound runtimes
  /// retain their full catalog for new conversations and other provider chats.
  CatalogSnapshot? catalogForSession(String? sessionID) {
    final snapshot = catalog;
    final self = this as ConnectionController;
    if (snapshot == null ||
        sessionID == null ||
        self.capabilities.sessionModelProviderSwitching) {
      return snapshot;
    }
    final gateway = self.api;
    final provider = gateway is PaseoGateway
        ? gateway.providerIdForSession(sessionID)
        : (self.sessionsById[sessionID]?.selection?.model ??
                  self.sessionModels[sessionID]?.model)
              ?.providerID;
    return CatalogSnapshot(
      providers: snapshot.providers.where((p) => p.id == provider).toList(),
      models: snapshot.models.where((m) => m.providerID == provider).toList(),
      agents: snapshot.agents,
    );
  }

  /// Providers the server reports as connected (a credential exists) but has
  /// not loaded into its model runtime. OpenCode 1 caches provider state per
  /// instance, so a sign-in that lands after startup leaves the provider in
  /// this limbo: `/provider` lists it with the full models.dev catalog while
  /// every prompt fails with "Model not found". [_loadCatalog] heals this
  /// by disposing the instance, only while no reply is running (a dispose
  /// aborts them) and once per distinct set: a set that stayed unloaded after
  /// a refresh is remembered per location, so later starts do not dispose
  /// again. Anything still listed needs a manual [reloadProviderRuntime].
  Set<String> unloadedProviderIDs = const {};

  /// True when a provider runtime refresh already ran for exactly
  /// [unloadedProviderIDs] and they stayed unloaded: the server holds sign-ins
  /// it cannot use (an OAuth sign-in with no plugin to load it), not a
  /// runtime that is merely behind.
  bool unloadedProvidersUnusable = false;

  /// Replies running on the server that hold the provider reload back.
  /// Nonzero while a reload waits for them; it runs once they finish.
  int providerReloadWaitingOn = 0;
  bool _providerHealDeferred = false;
  bool _runtimeJustRefreshed = false;
  String? _runtimeHealKey;
  int _runtimeHealGeneration = -1;

  // ---- freshness: one load per server and folder, then only when stale ----
  //
  // OpenCode 1 builds its multi-megabyte `/provider` answer on its only
  // thread (1.2 s p50, 3.6 s p95 on a phone-hosted server), and the catalog
  // used to reload on every wake, picker open and command sheet. It now
  // loads once per server and folder and again only when something says it
  // changed: Reload, a provider/config/agent event, a sign-in, or age.

  /// The load running now; callers that arrive meanwhile share it.
  Future<void>? _catalogInFlight;
  int _catalogInFlightGeneration = -1;
  int _catalogInFlightRefresh = -1;

  /// One reload queued behind [_catalogInFlight] for a change that arrived
  /// after it started, shared by every caller that asks meanwhile.
  Future<void>? _catalogFollowUp;
  Future<void>? _catalogFollowUpAfter;
  bool _catalogFollowUpAnnounced = false;

  /// When the shown catalog was read, for which server and folder, and
  /// which invalidation it already reflects.
  DateTime? _catalogLoadedAt;
  String? _catalogLoadedKey;

  /// What a screen opening may reuse: the last finished load, its server and
  /// folder, and the invalidations it reflected. Unlike [_catalogLoadedKey]
  /// this survives a folder re-select or a wake rebuild, so Settings or a
  /// picker opened moments after connect does not read `/provider` again.
  DateTime? _catalogReuseAt;
  String? _catalogReuseKey;
  int _catalogReuseInvalidations = -1;

  // ---- OpenCode 1 answers "not found" to every `/api/...` (v2) endpoint ----
  //
  // Each probe cost 0.35-0.75 s on a phone-hosted server and was repeated on
  // every refresh. The first not-found for a family (permission, question,
  // catalog) is remembered for this connection; a reconnect or another server
  // asks again. Opening another folder keeps the memory: same server.
  final Set<String> _v2Absent = {};
  String? _v2AbsentScope;
  int _catalogInvalidations = 0;
  int _catalogLoadedInvalidations = 0;
}

/// How long a loaded catalog counts as current without any change signal.
/// Past it the next reader refreshes it behind the list already shown.
const catalogFreshFor = Duration(minutes: 10);

/// A screen that opens (Settings, a picker) reuses a catalog read this
/// recently for the same server and folder, whatever rebuilt the transport
/// in between. Change events and Reload still read it again at once.
const catalogReuseWindow = Duration(seconds: 60);

extension _ConnectionControllerCatalogImpl on ConnectionController {
  String? get _v2Scope {
    final owner = _connectedProfile;
    return owner == null ? null : '${owner.id}\n${owner.baseUrl}';
  }

  /// True while the v2 variant of [family] is worth asking for.
  bool _v2Probe(String family) {
    final scope = _v2Scope;
    if (_v2AbsentScope != scope) {
      _v2Absent.clear();
      _v2AbsentScope = scope;
    }
    return !_v2Absent.contains(family);
  }

  /// Remembers a "not found" (or "method not allowed") from the v2 variant.
  void _v2Failed(String family, Object error) {
    if (_v2NotFound(error)) {
      _v2AbsentScope = _v2Scope;
      _v2Absent.add(family);
    }
  }

  void _v2Reset() {
    _v2Absent.clear();
    _v2AbsentScope = null;
  }

  static bool _v2NotFound(Object error) {
    bool missing(int? status) => status == 404 || status == 405;
    if (error is ProductException) {
      final cause = error.cause;
      return cause != null && _v2NotFound(cause);
    }
    if (error is ApiException) return missing(error.statusCode);
    if (error is sdk.OpenCodeApiException) return missing(error.statusCode);
    try {
      final response = (error as dynamic).response;
      final status = response?.statusCode;
      return status is int && missing(status);
    } catch (_) {
      return false;
    }
  }

  /// True when a screen opening can reuse the shown catalog: it was read in
  /// the last [catalogReuseWindow] for this server and folder and no change
  /// event or manual reload has invalidated it since.
  bool get _catalogRecent {
    final at = _catalogReuseAt;
    return catalog != null &&
        catalogError == null &&
        at != null &&
        _catalogReuseKey != null &&
        _catalogReuseKey == _catalogScopeKey &&
        _catalogReuseInvalidations == _catalogInvalidations &&
        clock.now().difference(at) < catalogReuseWindow;
  }

  /// Which server and folder a catalog answer belongs to.
  String? get _catalogScopeKey {
    final owner = _connectedProfile;
    if (owner == null) return null;
    return '${owner.id}\n${owner.baseUrl}\n${directory ?? ''}'
        '\n${workspace ?? ''}';
  }

  /// True when the shown catalog is this server's and folder's, nothing has
  /// invalidated it since, and it is younger than [catalogFreshFor].
  bool get _catalogFresh {
    final loadedAt = _catalogLoadedAt;
    return catalog != null &&
        catalogError == null &&
        loadedAt != null &&
        _catalogLoadedKey == _catalogScopeKey &&
        _catalogLoadedInvalidations == _catalogInvalidations &&
        clock.now().difference(loadedAt) < catalogFreshFor;
  }

  /// The load running for this transport and folder, if any.
  Future<void>? get _currentCatalogLoad {
    final load = _catalogInFlight;
    if (load == null ||
        _catalogInFlightGeneration != _generation ||
        _catalogInFlightRefresh != _catalogRefreshGeneration) {
      return null;
    }
    return load;
  }

  /// Loads the catalog only when it is missing or stale; otherwise returns
  /// at once. Concurrent callers share one load. Wakes, folder opens and
  /// pickers come through here.
  Future<void> _ensureCatalog() {
    final running = _currentCatalogLoad;
    if (running != null) return running;
    if (_catalogFresh || _catalogRecent) return Future<void>.value();
    return _startCatalogLoad(announce: catalog == null);
  }

  /// Reloads the catalog because something says it changed. A load already
  /// running is not interrupted: one reload follows it, shared by every
  /// caller that asks meanwhile. [announce] shows the loading state (an
  /// explicit Reload); a change event refreshes behind the shown list.
  Future<void> _loadCatalog({bool announce = true}) {
    _catalogInvalidations += 1;
    final running = _currentCatalogLoad;
    if (running == null) {
      return _startCatalogLoad(announce: announce || catalog == null);
    }
    if (announce && !catalogLoading) {
      catalogLoading = true;
      _notifyListeners();
    }
    _catalogFollowUpAnnounced = _catalogFollowUpAnnounced || announce;
    final queued = _catalogFollowUp;
    if (queued != null && identical(_catalogFollowUpAfter, running)) {
      return queued;
    }
    late final Future<void> followUp;
    followUp = running.then((_) {}, onError: (Object _) {}).then((_) {
      final announced = _catalogFollowUpAnnounced;
      // A server or folder switch dropped it: that switch loads its own.
      if (_disposed || !identical(_catalogFollowUp, followUp)) {
        return Future<void>.value();
      }
      _catalogFollowUp = null;
      _catalogFollowUpAfter = null;
      _catalogFollowUpAnnounced = false;
      final next = _currentCatalogLoad;
      if (next != null) return next;
      if (_catalogFresh) return Future<void>.value();
      return _startCatalogLoad(announce: announced || catalog == null);
    });
    _catalogFollowUp = followUp;
    _catalogFollowUpAfter = running;
    return followUp;
  }

  Future<void> _startCatalogLoad({required bool announce}) {
    final currentApi = api;
    if (currentApi == null) return Future<void>.value();
    final refreshGeneration = ++_catalogRefreshGeneration;
    final generation = _generation;
    final invalidations = _catalogInvalidations;
    final scope = _catalogScopeKey;
    late final Future<void> load;
    load =
        PerfTrace.span(
          'catalog.load',
          () => _loadCatalogUntraced(
            currentApi: currentApi,
            generation: generation,
            refreshGeneration: refreshGeneration,
            invalidations: invalidations,
            scope: scope,
            announce: announce,
          ),
          attrs: {'shown': catalog != null},
        ).whenComplete(() {
          if (identical(_catalogInFlight, load)) _catalogInFlight = null;
          // A wake or reconnect retired this load's transport and nothing
          // newer started: the shown catalog stays, so stop saying it loads.
          if (!_disposed &&
              catalogLoading &&
              refreshGeneration == _catalogRefreshGeneration &&
              !_isCurrent(generation, currentApi)) {
            catalogLoading = false;
            _notifyListeners();
          }
        });
    _catalogInFlight = load;
    _catalogInFlightGeneration = generation;
    _catalogInFlightRefresh = refreshGeneration;
    return load;
  }

  Future<void> _loadCatalogUntraced({
    required ServerGateway currentApi,
    required int generation,
    required int refreshGeneration,
    required int invalidations,
    required String? scope,
    required bool announce,
  }) async {
    final currentRepository = repository;
    // Only a load that starts after a manual runtime reload reads the
    // rebuilt runtime; one already running answers from before it.
    final afterRuntimeRefresh = _runtimeJustRefreshed;
    final hadError = catalogError != null;
    catalogError = null;
    if (announce) catalogLoading = true;
    if (announce || hadError) _notifyListeners();
    try {
      Future<CatalogSnapshot?> loadDetailedCatalog() async {
        if (currentRepository == null || !_v2Probe('catalog')) return null;
        try {
          return await currentRepository.loadCatalog();
        } catch (error) {
          _v2Failed('catalog', error);
          return null;
        }
      }

      Future<ProvidersResponse?> loadConfiguredProviders() async {
        try {
          return await currentApi.configuredProviders();
        } catch (_) {
          return null;
        }
      }

      Future<List<IntegrationInfo>> loadIntegrations() async {
        if (currentRepository == null) return const [];
        try {
          return await currentRepository.listIntegrations();
        } catch (_) {
          return const [];
        }
      }

      Future<ChatDefaults?> loadChatDefaults() async {
        if (currentRepository == null) return null;
        try {
          return await currentRepository.loadChatDefaults();
        } catch (_) {
          return null;
        }
      }

      // v1 servers cache their provider runtime per instance; fetch the
      // runtime view alongside the connected list so the two can be compared.
      final comparesRuntime = currentApi.capabilities.providerRuntimeRefresh;
      final results = await Future.wait<Object?>([
        currentApi.providers(),
        currentApi.agents(),
        loadDetailedCatalog(),
        // OpenCode 1 reads connected providers from `/provider` itself; the
        // integration list only matters to the v2 recovery below, and on v1
        // it cost a second `/provider` plus `/provider/auth` every load.
        comparesRuntime
            ? Future<List<IntegrationInfo>>.value(const [])
            : loadIntegrations(),
        loadChatDefaults(),
        comparesRuntime
            ? loadConfiguredProviders()
            : Future<ProvidersResponse?>.value(null),
      ]);
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      var nextProviders = results[0] as ProvidersResponse;
      final nextAgents = results[1] as List<AgentInfo>;
      final detailedCatalog = results[2] as CatalogSnapshot?;
      final integrations = results[3] as List<IntegrationInfo>;
      final chatDefaults = results[4] as ChatDefaults?;
      var configuredProviders = results[5] as ProvidersResponse?;
      var unloaded = comparesRuntime
          ? ConnectionController.unloadedProviders(
              nextProviders,
              configuredProviders,
            )
          : const <String>{};
      final healProfileID = _connectedProfile?.id;
      final healDirectory = directory;
      final healWorkspace = workspace;
      String? triedUnloadable = healProfileID == null
          ? null
          : store.providerRuntimeUnloadable(
              healProfileID,
              directory: healDirectory,
              workspace: healWorkspace,
            );
      if (afterRuntimeRefresh && _runtimeJustRefreshed) {
        // The manual reload just rebuilt the runtime: whatever is still
        // unloaded now is what this server cannot load.
        _runtimeJustRefreshed = false;
        _runtimeHealGeneration = generation;
        _runtimeHealKey = _providerSetKey(unloaded);
        triedUnloadable = _providerSetKey(unloaded);
        if (healProfileID != null) {
          await store.setProviderRuntimeUnloadable(
            healProfileID,
            triedUnloadable,
            directory: healDirectory,
            workspace: healWorkspace,
          );
        }
      }
      if (unloaded.isNotEmpty && currentRepository != null) {
        // A credential the runtime has not picked up yet (OAuth finished in
        // the TUI, or after this app's own sign-in raced the server). Dispose
        // the instance so OpenCode rebuilds its provider state, then re-read.
        // Heal once per distinct set of providers per connection so a server
        // that cannot load a provider does not loop, and never again for a
        // set a refresh already failed to load: on a cold start that dispose
        // would only abort replies still running on the server.
        final healKey = _providerSetKey(unloaded);
        if (healKey != triedUnloadable &&
            (_runtimeHealGeneration != generation ||
                _runtimeHealKey != healKey)) {
          _runtimeHealGeneration = generation;
          _runtimeHealKey = healKey;
          try {
            await currentRepository.refreshProviderRuntime();
            final healed = await Future.wait<Object?>([
              currentApi.providers(),
              loadConfiguredProviders(),
            ]);
            if (!_isCurrentCatalogRefresh(
              generation,
              currentApi,
              refreshGeneration,
            )) {
              return;
            }
            nextProviders = healed[0] as ProvidersResponse;
            configuredProviders = healed[1] as ProvidersResponse?;
            unloaded = ConnectionController.unloadedProviders(
              nextProviders,
              configuredProviders,
            );
            providerReloadWaitingOn = 0;
            triedUnloadable = unloaded.isEmpty
                ? null
                : _providerSetKey(unloaded);
            if (healProfileID != null) {
              await store.setProviderRuntimeUnloadable(
                healProfileID,
                triedUnloadable,
                directory: healDirectory,
                workspace: healWorkspace,
              );
            }
          } on ProviderRuntimeBusyException catch (busy) {
            // Replies are running: a dispose would abort them. Wait until
            // the server goes idle, then try again.
            _runtimeHealKey = null;
            _providerHealDeferred = true;
            providerReloadWaitingOn = busy.runningReplies;
          } catch (_) {
            // Leave the providers flagged; the picker offers a manual reload.
          }
        }
      } else if (unloaded.isEmpty) {
        providerReloadWaitingOn = 0;
        _providerHealDeferred = false;
      }
      final hasConnectedIntegration = integrations.any(
        (integration) => integration.connectionCount > 0,
      );
      if (configuredProviders == null && hasConnectedIntegration) {
        configuredProviders = await loadConfiguredProviders();
      }
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      // V1 chat reads /provider.connected, not the v2 integration credential
      // store. A v2-only OAuth credential must not expose unusable models.
      if (!comparesRuntime && integrations.isNotEmpty) {
        final present = {
          for (final provider in nextProviders.providers) provider.id,
        };
        final recoverableByID = {
          if (configuredProviders != null)
            for (final provider in configuredProviders.availableProviders)
              provider.id: provider,
          for (final provider in nextProviders.availableProviders)
            provider.id: provider,
        };
        final connectedIntegrationIDs = integrations
            .where((integration) => integration.connectionCount > 0)
            .map((integration) => integration.id);
        final recovered = <ProviderInfo>[];
        for (final id in connectedIntegrationIDs) {
          if (present.contains(id)) continue;
          final provider = recoverableByID[id];
          if (provider != null) recovered.add(provider);
        }
        if (recovered.isNotEmpty) {
          nextProviders = ProvidersResponse(
            providers: [...nextProviders.providers, ...recovered],
            availableProviders: nextProviders.availableProviders,
            defaultProviderID: nextProviders.defaultProviderID,
            defaultModelID: nextProviders.defaultModelID,
          );
        }
      }
      final fallbackCatalog = CatalogSnapshot(
        providers: [
          for (final provider in nextProviders.providers)
            CatalogProvider(
              id: provider.id,
              name: provider.name,
              enabled: true,
            ),
        ],
        models: [
          for (final provider in nextProviders.providers)
            for (final modelID in provider.modelIDs)
              _catalogModelFromProvider(provider, modelID),
        ],
        agents: [
          for (final agent in nextAgents)
            CatalogAgent(
              id: agent.name,
              mode: agent.mode ?? 'unknown',
              description: null,
              hidden: false,
              color: agent.color,
              model: agent.model,
            ),
        ],
      );
      final nextCatalog = detailedCatalog == null
          ? fallbackCatalog
          : CatalogSnapshot(
              // provider.list is the OpenCode source of truth for connected
              // providers and their available models. The experimental v2
              // surface only reports providers/models active in the current
              // location, so it may legitimately contain only Zen. Use v2 to
              // enrich matching rows, never to hide connected providers.
              providers: fallbackCatalog.providers.isEmpty
                  ? detailedCatalog.providers
                  : fallbackCatalog.providers,
              models: fallbackCatalog.models.isEmpty
                  ? detailedCatalog.models
                  : [
                      for (final model in fallbackCatalog.models)
                        _mergeCatalogModel(
                          detailedCatalog.models.firstWhere(
                            (detailed) =>
                                detailed.providerID == model.providerID &&
                                detailed.id == model.id,
                            orElse: () => model,
                          ),
                          model,
                        ),
                    ],
              agents: detailedCatalog.agents.isEmpty
                  ? fallbackCatalog.agents
                  : detailedCatalog.agents,
            );
      final profileID = _connectedProfile?.id;
      var nextModel = selectedModel;
      bool validModel(ModelRef? model) =>
          model != null &&
          nextCatalog.models.any(
            (candidate) =>
                candidate.providerID == model.providerID &&
                candidate.id == model.modelID,
          );
      final configuredModel = chatDefaults?.model;
      final modelWasExplicitlySelected =
          profileID != null && store.modelWasExplicitlySelected(profileID);
      if (!validModel(nextModel) ||
          (!modelWasExplicitlySelected && validModel(configuredModel))) {
        final providerDefaultModel = ModelRef(
          providerID: nextProviders.defaultProviderID ?? '',
          modelID: nextProviders.defaultModelID ?? '',
        );
        nextModel = validModel(configuredModel)
            ? configuredModel
            : validModel(providerDefaultModel)
            ? providerDefaultModel
            : null;
        if (nextModel == null) {
          for (final model in nextCatalog.models) {
            nextModel = ModelRef(
              providerID: model.providerID,
              modelID: model.id,
            );
            break;
          }
        }
      }
      var nextVariant = selectedVariant;
      final catalogModel = nextCatalog.models.where(
        (model) =>
            model.providerID == nextModel?.providerID &&
            model.id == nextModel?.modelID,
      );
      final validVariants = catalogModel.isEmpty
          ? const <CatalogVariant>[]
          : catalogModel.first.variants.where((variant) => !variant.disabled);
      if (nextVariant.isNotEmpty &&
          !validVariants.any((variant) => variant.id == nextVariant)) {
        nextVariant = '';
      }
      var nextAgent = selectedAgent;
      if (!nextAgents.any((agent) => agent.name == nextAgent)) {
        final configuredAgent = chatDefaults?.agent;
        final validConfiguredAgent = nextAgents.any(
          (agent) => agent.name == configuredAgent && agent.mode != 'subagent',
        );
        final primaryAgents = nextAgents.where(
          (agent) => agent.mode != 'subagent',
        );
        nextAgent = validConfiguredAgent
            ? configuredAgent!
            : primaryAgents.isEmpty
            ? ''
            : primaryAgents.first.name;
      }
      if (profileID != null) {
        final modelChanged =
            nextModel?.providerID != selectedModel?.providerID ||
            nextModel?.modelID != selectedModel?.modelID;
        if (!validModel(selectedModel) || modelChanged) {
          if (nextModel == null) {
            await store.clearModel(profileID);
          } else {
            await store.setModel(
              profileID,
              nextModel.providerID,
              nextModel.modelID,
            );
          }
        }
        if (nextAgent != selectedAgent) {
          await store.setAgent(profileID, nextAgent);
        }
        if (nextVariant != selectedVariant) {
          await store.setVariant(profileID, nextVariant);
        }
      }
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      providers = nextProviders;
      agents = nextAgents;
      catalog = nextCatalog;
      // A temporarily unloaded provider is not a removed model. Keep its
      // shortcuts until a successful runtime reload can confirm membership.
      final retainedLibrary = _modelLibrary.retainWhere(
        (model) => unloaded.contains(model.providerID) || modelAvailable(model),
      );
      if (!listEquals(retainedLibrary.favorites, _modelLibrary.favorites) ||
          !listEquals(retainedLibrary.recent, _modelLibrary.recent)) {
        _modelLibrary = retainedLibrary;
        await _persistModelLibrary();
      }
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      unloadedProviderIDs = unloaded;
      unloadedProvidersUnusable =
          unloaded.isNotEmpty && triedUnloadable == _providerSetKey(unloaded);
      catalogDetailed =
          detailedCatalog?.models.isNotEmpty == true ||
          nextProviders.providers.any(
            (provider) => provider.modelData.isNotEmpty,
          );
      selectedModel = nextModel;
      selectedAgent = nextAgent;
      selectedVariant = nextVariant;
      catalogLoading = false;
      _catalogLoadedAt = clock.now();
      _catalogLoadedKey = scope;
      _catalogLoadedInvalidations = invalidations;
      _catalogReuseAt = clock.now();
      _catalogReuseKey = scope;
      _catalogReuseInvalidations = invalidations;
      _notifyListeners();
    } catch (error) {
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      catalogLoading = false;
      catalogError = error.toString();
      _recordLocationError(error);
      _notifyListeners();
    }
  }
}

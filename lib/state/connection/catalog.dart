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
}

extension _ConnectionControllerCatalogImpl on ConnectionController {
  Future<void> _loadCatalog() =>
      PerfTrace.span('catalog.load', _loadCatalogUntraced);

  Future<void> _loadCatalogUntraced() async {
    final currentApi = api;
    final currentRepository = repository;
    final generation = _generation;
    if (currentApi == null) return;
    final refreshGeneration = ++_catalogRefreshGeneration;
    catalogLoading = true;
    catalogError = null;
    _notifyListeners();
    try {
      Future<CatalogSnapshot?> loadDetailedCatalog() async {
        if (currentRepository == null) return null;
        try {
          return await currentRepository.loadCatalog();
        } catch (_) {
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
        loadIntegrations(),
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
      if (_runtimeJustRefreshed) {
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
      _recordLocationError(catalogError!);
      _notifyListeners();
    }
  }
}

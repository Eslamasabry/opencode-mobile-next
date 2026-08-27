import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import '../api/opencode_api.dart';
import '../api/product_repository.dart';
import '../api/sse.dart';
import '../background/live_background.dart';
import '../termux/bridge.dart';
import 'profiles.dart';

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
  );
}

CatalogModel _mergeCatalogModel(CatalogModel detailed, CatalogModel base) {
  if (detailed.variants.isNotEmpty || base.variants.isEmpty) return detailed;
  return CatalogModel(
    id: detailed.id,
    providerID: detailed.providerID,
    name: detailed.name,
    family: detailed.family,
    enabled: detailed.enabled,
    status: detailed.status,
    contextLimit: detailed.contextLimit,
    outputLimit: detailed.outputLimit,
    reasoning: detailed.reasoning,
    attachments: detailed.attachments,
    tools: detailed.tools,
    variants: base.variants,
  );
}

/// App-wide singletons that need async init before the UI can render.
class AppBootstrap {
  final ProfileStore store;
  AppBootstrap(this.store);

  static Future<AppBootstrap> create() async {
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    return AppBootstrap(store);
  }
}

/// Created once in main so every screen and the connection controller share
/// the same profile cache and secure-storage view.
final bootstrapProvider = Provider<AppBootstrap>(
  (ref) => throw UnimplementedError('overridden in bootstrap'),
);

/// The live connection controller; overridden with a real instance in main().
final connProvider = Provider<ConnectionController>(
  (ref) => throw UnimplementedError('overridden in bootstrap'),
);

typedef OpenCodeApiFactory = OpenCodeApi Function(ServerProfile profile);
typedef ProductRepositoryFactory = ProductRepository Function(OpenCodeApi api);
typedef LocalWakeLockEnsurer = Future<void> Function();
typedef EventStreamFactory =
    EventStream Function({
      required OpenCodeApi api,
      required void Function(EventEnvelope event) onEvent,
      required void Function(StreamStatus status) onStatus,
      void Function(Object error)? onError,
    });

/// Everything the UI needs about the active server connection.
class ConnectionController extends ChangeNotifier {
  final ProfileStore store;
  final BackgroundLiveController backgroundLive;
  final OpenCodeApiFactory _apiFactory;
  final ProductRepositoryFactory _repositoryFactory;
  final EventStreamFactory _eventStreamFactory;
  final LocalWakeLockEnsurer _localWakeLockEnsurer;

  OpenCodeApi? api;
  ProductRepository? repository;
  EventStream? _events;
  Timer? _poll;
  Future<void>? _busyStatusRefresh;
  ServerProfile? _connectedProfile;
  Future<void> _activeProfileWrite = Future.value();
  int _generation = 0;
  int _sessionsRefreshGeneration = 0;
  int _catalogRefreshGeneration = 0;
  int _questionsRefreshGeneration = 0;
  int _questionRevision = 0;
  final Map<String, int> _questionRevisions = {};
  int _sessionRevision = 0;
  final Map<String, int> _sessionRevisions = {};

  StreamStatus status = StreamStatus.disconnected;
  String? version;
  String? lastError;

  int connectionRevision = 0;

  /// Advances whenever a usable transport is ready and screen-owned data
  /// should be rehydrated. This also advances after an SSE reconnection so
  /// events missed during a network handoff are reconciled from REST.
  int dataRefreshRevision = 0;
  int locationRevision = 0;
  String? directory;
  String? workspace;
  bool locationLoading = false;
  String? locationError;
  bool sessionsLoading = false;
  String? sessionsError;
  bool catalogLoading = false;
  String? catalogError;
  bool permissionsLoading = false;
  String? permissionsError;
  bool questionsLoading = false;
  String? questionsError;

  bool get connectionLoading =>
      status == StreamStatus.connecting || status == StreamStatus.reconnecting;
  String? get connectionError => lastError;
  bool get pollingFallbackEnabled => _poll?.isActive ?? false;
  bool get shouldPoll => api != null && status != StreamStatus.connected;

  ProvidersResponse? providers;
  List<AgentInfo> agents = [];
  CatalogSnapshot? catalog;
  bool catalogDetailed = false;

  ModelRef? selectedModel;
  String selectedAgent = '';
  String selectedVariant = '';

  Map<String, Session> sessionsById = {};
  Set<String> busySessions = {};

  /// Outstanding permission asks keyed by request ID.
  Map<String, PermissionRequest> permissions = {};
  Map<String, PendingQuestion> questions = {};
  int ptyRevision = 0;
  EventEnvelope? lastPtyEvent;
  final Set<String> _resolvedPermissionIDs = {};
  final Map<String, ({String sessionID, String permissionID})>
  _legacyPermissionIdentities = {};
  final Map<String, String> _v2PermissionSessions = {};
  final Map<String, String> _v2QuestionSessions = {};
  final Set<String> _resolvedQuestionIDs = {};
  int _permissionRevision = 0;
  Timer? _permissionHydrationRetry;
  int _permissionHydrationGeneration = 0;
  bool _disposed = false;
  bool _lifecycleSuspended = false;
  bool _lifecycleWasBackgrounded = false;
  Future<void>? _lifecycleResume;

  /// True only while the app intentionally has its transport retired in the
  /// background. UI must not treat this as a user-initiated disconnect.
  bool get lifecycleSuspended => _lifecycleSuspended;

  static const _permissionHydrationRetryDelays = [
    Duration(milliseconds: 250),
    Duration(seconds: 1),
    Duration(seconds: 2),
  ];

  /// Broadcast of every event for screen-scoped listeners (chat streaming).
  final _eventBus = StreamController<EventEnvelope>.broadcast();
  Stream<EventEnvelope> get events => _eventBus.stream;

  ConnectionController(
    this.store, {
    OpenCodeApiFactory? apiFactory,
    ProductRepositoryFactory? repositoryFactory,
    EventStreamFactory? eventStreamFactory,
    BackgroundLiveController? backgroundLive,
    LocalWakeLockEnsurer? localWakeLockEnsurer,
  }) : _apiFactory = apiFactory ?? _createApi,
       _repositoryFactory = repositoryFactory ?? _createRepository,
       _eventStreamFactory = eventStreamFactory ?? _createEventStream,
       _localWakeLockEnsurer =
           localWakeLockEnsurer ?? TermuxBridge.ensureWakeLock,
       backgroundLive =
           backgroundLive ??
           BackgroundLiveController(preferences: store.prefs) {
    this.backgroundLive.addListener(_backgroundLiveChanged);
  }

  bool get keepLiveInBackground => backgroundLive.enabled;

  Future<bool> setKeepLiveInBackground(bool enabled) =>
      backgroundLive.setEnabled(enabled);

  Future<void> restoreBackgroundLiveMode() => backgroundLive.restore();

  void _backgroundLiveChanged() {
    if (keepLiveInBackground) {
      unawaited(_ensureLocalServerWakeLock());
    }
    if (!_disposed) notifyListeners();
  }

  Future<void> _ensureLocalServerWakeLock() async {
    if (_disposed || !keepLiveInBackground) return;
    final profile = _connectedProfile;
    if (profile == null || !_isLoopbackUrl(profile.baseUrl)) return;
    try {
      await _localWakeLockEnsurer();
    } catch (_) {
      // The profile may point at a developer server rather than managed
      // Termux. Transport recovery must continue even when the bridge is not
      // installed or Android has revoked its command permission.
    }
  }

  static bool _isLoopbackUrl(String value) {
    final uri = Uri.tryParse(value);
    if (uri == null) return false;
    final host = uri.host.toLowerCase();
    return host == '127.0.0.1' || host == 'localhost' || host == '::1';
  }

  static OpenCodeApi _createApi(ServerProfile profile) => OpenCodeApi(
    baseUrl: profile.baseUrl,
    username: profile.username,
    password: profile.password,
  );

  static ProductRepository _createRepository(OpenCodeApi api) =>
      SdkProductRepository(api.sdkClient);

  static EventStream _createEventStream({
    required OpenCodeApi api,
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => EventStream(
    api: api,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
  );

  bool get isConnected => status == StreamStatus.connected && api != null;

  ServerProfile? get profile {
    final id = store.activeId;
    if (id == null) return null;
    for (final p in store.profiles) {
      if (p.id == id) return p;
    }
    return null;
  }

  Future<void> connect(ServerProfile profile) async {
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    final validationError = validateServerProfileUrl(
      profile.baseUrl,
      username: profile.username,
      password: profile.password,
    );
    if (validationError != null) {
      _beginGeneration();
      _retireTransport();
      status = StreamStatus.disconnected;
      lastError = validationError;
      notifyListeners();
      return;
    }
    final generation = _beginGeneration();
    _retireTransport();
    final currentApi = _apiFactory(profile)
      ..setLocation(directory: null, workspace: null);
    final currentRepository = _repositoryFactory(currentApi)
      ..setLocation(directory: null, workspace: null);
    api = currentApi;
    repository = currentRepository;
    _connectedProfile = profile;
    directory = null;
    workspace = null;
    locationRevision += 1;
    _clearLocationData();
    status = StreamStatus.connecting;
    lastError = null;
    locationError = null;
    notifyListeners();
    enablePollingFallback();

    try {
      await _writeActiveProfile(generation, profile.id);
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return;
      _failCurrentConnection(
        'Could not save the active server profile: $error',
      );
      return;
    }
    if (!_isCurrent(generation, currentApi)) return;

    try {
      await _ensureLocalServerWakeLock();
      final health = await currentApi.health();
      if (!_isCurrent(generation, currentApi)) return;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      version = health.version ?? '';
    } catch (e) {
      if (!_isCurrent(generation, currentApi)) return;
      _failCurrentConnection(
        e is ApiException ? e.message : 'Cannot reach ${profile.baseUrl}: $e',
      );
      return;
    }

    // Restore per-profile selections.
    final saved = store.modelFor(profile.id);
    selectedModel = (saved.$1 != null && saved.$2 != null)
        ? ModelRef(providerID: saved.$1!, modelID: saved.$2!)
        : null;
    selectedAgent = store.agentFor(profile.id);
    selectedVariant = store.variantFor(profile.id);

    unawaited(_loadCatalog());
    _startEvents(generation, currentApi);
    _markDataRefreshReady(generation, currentApi);
    unawaited(refreshSessions());
    unawaited(refreshPendingPermissions());
    unawaited(refreshPendingQuestions());
    notifyListeners();
  }

  Future<void> _loadCatalog() async {
    final currentApi = api;
    final currentRepository = repository;
    final generation = _generation;
    if (currentApi == null) return;
    final refreshGeneration = ++_catalogRefreshGeneration;
    catalogLoading = true;
    catalogError = null;
    notifyListeners();
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

      final results = await Future.wait<Object?>([
        currentApi.providers(),
        currentApi.agents(),
        loadDetailedCatalog(),
        loadIntegrations(),
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
      final hasConnectedIntegration = integrations.any(
        (integration) => integration.connectionCount > 0,
      );
      final configuredProviders = hasConnectedIntegration
          ? await loadConfiguredProviders()
          : null;
      if (!_isCurrentCatalogRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      if (integrations.isNotEmpty) {
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
      if (!validModel(nextModel)) {
        final defaultModel = ModelRef(
          providerID: nextProviders.defaultProviderID ?? '',
          modelID: nextProviders.defaultModelID ?? '',
        );
        nextModel = validModel(defaultModel) ? defaultModel : null;
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
        nextAgent = nextAgents.isEmpty ? '' : nextAgents.first.name;
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
      catalogDetailed =
          detailedCatalog?.models.isNotEmpty == true ||
          nextProviders.providers.any(
            (provider) => provider.modelData.isNotEmpty,
          );
      selectedModel = nextModel;
      selectedAgent = nextAgent;
      selectedVariant = nextVariant;
      catalogLoading = false;
      notifyListeners();
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
      notifyListeners();
    }
  }

  void _startEvents(int generation, OpenCodeApi currentApi) {
    late final EventStream stream;
    stream = _eventStreamFactory(
      api: currentApi,
      onEvent: (event) {
        if (!_isCurrentStream(generation, currentApi, stream)) return;
        _onEvent(event);
      },
      onStatus: (s) {
        if (!_isCurrentStream(generation, currentApi, stream)) return;
        final previousStatus = status;
        status = s;
        if (s == StreamStatus.connected) {
          lastError = null;
          unawaited(refreshPendingPermissions());
          unawaited(refreshPendingQuestions());
          if (previousStatus == StreamStatus.reconnecting ||
              previousStatus == StreamStatus.disconnected) {
            _markDataRefreshReady(generation, currentApi);
            unawaited(refreshSessions());
          }
        } else {
          _cancelPermissionHydration();
        }
        notifyListeners();
      },
      onError: (e) {
        if (!_isCurrentStream(generation, currentApi, stream)) return;
        lastError = e.toString();
        notifyListeners();
      },
    );
    _events = stream;
    stream.start();
  }

  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    final generation = _beginGeneration();
    _retireTransport();
    _clearLocationData();
    version = null;
    _connectedProfile = null;
    directory = null;
    workspace = null;
    locationRevision += 1;
    locationLoading = false;
    locationError = null;
    status = StreamStatus.disconnected;
    if (!silent) notifyListeners();
    if (!keepActive) {
      try {
        await _writeActiveProfile(generation, null);
      } catch (error) {
        if (_disposed || generation != _generation) return;
        lastError = 'Could not clear the active server profile: $error';
        notifyListeners();
      }
    }
  }

  // ---------------- Event handling ----------------

  void _onEvent(EventEnvelope env) {
    if (_disposed) return;
    final props = env.properties;
    switch (env.type) {
      case 'server.connected':
        final v = props['version']?.toString();
        if (v != null && v.isNotEmpty) {
          version = v;
          notifyListeners();
        }
        break;

      case 'integration.connection.updated':
      case 'catalog.updated':
      case 'agent.updated':
      case 'config.updated':
        // Provider credentials and catalog overlays can change without a
        // reconnect. Refetch the current catalog just like upstream clients.
        unawaited(_loadCatalog());
        break;

      case 'session.created':
      case 'session.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final s = Session.fromJson(info);
          _markSessionChanged(s.id);
          sessionsById[s.id] = s;
          notifyListeners();
        }
        break;

      case 'session.deleted':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final id = info['id']?.toString();
          if (id != null && id.isNotEmpty) {
            _markSessionChanged(id);
            sessionsById.remove(id);
            busySessions.remove(id);
            permissions.removeWhere((_, value) => value.sessionID == id);
            final removedQuestionIDs = questions.entries
                .where((entry) => entry.value.sessionID == id)
                .map((entry) => entry.key)
                .toList();
            for (final questionID in removedQuestionIDs) {
              _markQuestionChanged(questionID);
              questions.remove(questionID);
            }
            _legacyPermissionIdentities.removeWhere(
              (_, identity) => identity.sessionID == id,
            );
            _v2PermissionSessions.removeWhere(
              (_, sessionID) => sessionID == id,
            );
            _v2QuestionSessions.removeWhere((_, sessionID) => sessionID == id);
            notifyListeners();
          }
        }
        break;

      case 'message.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final msg = MessageInfo.fromJson(info);
          if (msg.role == 'assistant') {
            _markSessionChanged(msg.sessionID);
            final working =
                (msg.time == null || !msg.time!.isDone) &&
                msg.errorText == null;
            if (working) {
              busySessions.add(msg.sessionID);
            } else {
              busySessions.remove(msg.sessionID);
            }
            notifyListeners();
          }
        }
        break;

      case 'message.removed':
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          unawaited(_refreshOneSession(sid));
        }
        break;

      case 'permission.asked':
        _handlePermission(props);
        break;

      case 'permission.v2.asked':
        _handlePermissionV2(props);
        break;

      case 'permission.updated':
        _handleLegacyPermission(props);
        break;

      case 'permission.replied':
      case 'permission.v2.replied':
        _handlePermissionReply(props);
        break;

      case 'question.asked':
      case 'question.updated':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions.remove(question.id);
          questions[question.id] = question;
          notifyListeners();
        }
        break;

      case 'question.v2.asked':
        questionsLoading = false;
        final question = PendingQuestion.fromJson(props);
        if (question.id.isNotEmpty && question.sessionID.isNotEmpty) {
          _markQuestionChanged(question.id);
          _resolvedQuestionIDs.remove(question.id);
          _v2QuestionSessions[question.id] = question.sessionID;
          questions[question.id] = question;
          notifyListeners();
        }
        break;

      case 'question.replied':
      case 'question.rejected':
      case 'question.v2.replied':
      case 'question.v2.rejected':
        questionsLoading = false;
        final id = props['requestID']?.toString() ?? props['id']?.toString();
        if (id != null && id.isNotEmpty) {
          _markQuestionChanged(id);
          _resolvedQuestionIDs.add(id);
          _v2QuestionSessions.remove(id);
          if (questions.remove(id) != null) notifyListeners();
        }
        break;

      case 'session.status':
        final sid = props['sessionID']?.toString();
        final rawStatus = props['status'];
        final sessionStatus = rawStatus is Map
            ? rawStatus['type']?.toString()
            : rawStatus?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          switch (sessionStatus) {
            case 'idle':
              busySessions.remove(sid);
              unawaited(_refreshOneSession(sid));
              break;
            case 'busy':
            case 'retry':
              busySessions.add(sid);
              break;
            default:
              break;
          }
          notifyListeners();
        }
        break;

      case 'session.error':
        final sid = props['sessionID']?.toString();
        if (sid != null) {
          _markSessionChanged(sid);
          busySessions.remove(sid);
        }
        final err = props['error'];
        if (err is Map<String, dynamic>) {
          final data = err['data'];
          lastError =
              err['message']?.toString() ??
              (data is Map ? data['message']?.toString() : null) ??
              lastError;
        }
        notifyListeners();
        break;

      case 'session.idle':
        final sid = props['sessionID']?.toString();
        if (sid != null) {
          _markSessionChanged(sid);
          busySessions.remove(sid);
          unawaited(_refreshOneSession(sid));
          notifyListeners();
        }
        break;

      case 'session.compacted':
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          _markSessionChanged(sid);
          unawaited(_refreshOneSession(sid));
        }
        break;

      case 'pty.created':
      case 'pty.updated':
      case 'pty.exited':
      case 'pty.deleted':
        lastPtyEvent = env;
        ptyRevision += 1;
        notifyListeners();
        break;

      default:
        break;
    }
    _eventBus.add(env);
  }

  void _handlePermission(Map<String, dynamic> props) {
    final permission = PermissionRequest.fromJson(props);
    if (permission.sessionID.isEmpty || permission.id.isEmpty) return;
    _resolvedPermissionIDs.remove(permission.id);
    _legacyPermissionIdentities.remove(permission.id);
    _v2PermissionSessions.remove(permission.id);
    permissions[permission.id] = permission;
    _permissionRevision += 1;
    notifyListeners();
  }

  void _handlePermissionV2(Map<String, dynamic> props) {
    final permission = PermissionRequest.fromJson({
      'id': props['id'],
      'sessionID': props['sessionID'],
      'permission': props['action'],
      'patterns': props['resources'],
      'metadata': props['metadata'],
      'always': props['save'],
      if (props['source'] is Map) 'tool': props['source'],
    });
    if (permission.sessionID.isEmpty || permission.id.isEmpty) return;
    _resolvedPermissionIDs.remove(permission.id);
    _legacyPermissionIdentities.remove(permission.id);
    _v2PermissionSessions[permission.id] = permission.sessionID;
    permissions[permission.id] = permission;
    _permissionRevision += 1;
    notifyListeners();
  }

  void _handleLegacyPermission(Map<String, dynamic> props) {
    final id = props['id']?.toString() ?? '';
    final sessionID = props['sessionID']?.toString() ?? '';
    if (id.isEmpty || sessionID.isEmpty) return;
    final rawPattern = props['pattern'];
    final patterns = rawPattern is List
        ? rawPattern.map((item) => item.toString()).toList()
        : rawPattern == null
        ? const <String>[]
        : [rawPattern.toString()];
    final messageID = props['messageID']?.toString() ?? '';
    final callID = props['callID']?.toString() ?? '';
    final permission = PermissionRequest(
      id: id,
      sessionID: sessionID,
      permission: props['type']?.toString() ?? '',
      patterns: patterns,
      metadata: props['metadata'] is Map
          ? Map<String, dynamic>.from(props['metadata'] as Map)
          : const {},
      tool: messageID.isNotEmpty && callID.isNotEmpty
          ? PermissionTool(messageID: messageID, callID: callID)
          : null,
    );
    _resolvedPermissionIDs.remove(id);
    _v2PermissionSessions.remove(id);
    permissions[id] = permission;
    _legacyPermissionIdentities[id] = (sessionID: sessionID, permissionID: id);
    _permissionRevision += 1;
    notifyListeners();
  }

  void _handlePermissionReply(Map<String, dynamic> props) {
    final requestID =
        props['requestID']?.toString() ?? props['permissionID']?.toString();
    if (requestID == null || requestID.isEmpty) return;
    _resolvePermission(requestID);
  }

  void _resolvePermission(String requestID) {
    _resolvedPermissionIDs.add(requestID);
    permissions.remove(requestID);
    _legacyPermissionIdentities.remove(requestID);
    _v2PermissionSessions.remove(requestID);
    _permissionRevision += 1;
    notifyListeners();
  }

  PermissionRequest? permissionForSession(String sessionID) {
    for (final permission in permissions.values) {
      if (permission.sessionID == sessionID) return permission;
    }
    return null;
  }

  List<PermissionRequest> permissionsForSession(String sessionID) => permissions
      .values
      .where((permission) => permission.sessionID == sessionID)
      .toList();

  Future<void> refreshPendingPermissions() async {
    final currentApi = api;
    final connectionGeneration = _generation;
    if (currentApi == null) return;
    _permissionHydrationRetry?.cancel();
    _permissionHydrationRetry = null;
    final generation = ++_permissionHydrationGeneration;
    permissionsLoading = true;
    permissionsError = null;
    notifyListeners();
    await _hydratePendingPermissions(
      currentApi,
      connectionGeneration,
      generation,
      0,
    );
  }

  Future<void> _hydratePendingPermissions(
    OpenCodeApi currentApi,
    int connectionGeneration,
    int generation,
    int attempt,
  ) async {
    final revision = _permissionRevision;
    final permissionsAtStart = Map<String, PermissionRequest>.of(permissions);
    try {
      final results = await _loadPendingPermissions(currentApi);
      if (!_isCurrentPermissionHydration(
        currentApi,
        connectionGeneration,
        generation,
      )) {
        return;
      }
      final unresolved = {
        for (final permission in results.pending)
          if (!_resolvedPermissionIDs.contains(permission.id))
            permission.id: permission,
      };
      if (!results.v2Succeeded) {
        for (final entry in permissions.entries) {
          if (_v2PermissionSessions.containsKey(entry.key) &&
              !_resolvedPermissionIDs.contains(entry.key)) {
            unresolved.putIfAbsent(entry.key, () => entry.value);
          }
        }
      }
      if (revision == _permissionRevision) {
        _resolvedPermissionIDs.addAll(
          permissions.keys.where((id) => !unresolved.containsKey(id)),
        );
        permissions = unresolved;
      } else {
        for (final entry in permissionsAtStart.entries) {
          if (!unresolved.containsKey(entry.key) &&
              identical(permissions[entry.key], entry.value)) {
            permissions.remove(entry.key);
            _resolvedPermissionIDs.add(entry.key);
          }
        }
        permissions.addAll(unresolved);
      }
      _legacyPermissionIdentities.removeWhere(
        (id, _) => !permissions.containsKey(id),
      );
      _v2PermissionSessions.removeWhere(
        (id, _) => !permissions.containsKey(id),
      );
      if (results.v2Succeeded) {
        _v2PermissionSessions.removeWhere(
          (id, _) => !results.v2IDs.contains(id),
        );
        for (final id in results.v2IDs) {
          final permission = permissions[id];
          if (permission != null) {
            _v2PermissionSessions[id] = permission.sessionID;
          }
        }
      }
      permissionsLoading = false;
      permissionsError = null;
      notifyListeners();
    } catch (error) {
      if (!_isCurrentPermissionHydration(
            currentApi,
            connectionGeneration,
            generation,
          ) ||
          attempt >= _permissionHydrationRetryDelays.length) {
        if (_isCurrentPermissionHydration(
          currentApi,
          connectionGeneration,
          generation,
        )) {
          permissionsLoading = false;
          permissionsError = error.toString();
          _recordLocationError(permissionsError!);
          notifyListeners();
        }
        return;
      }
      _permissionHydrationRetry = Timer(
        _permissionHydrationRetryDelays[attempt],
        () => unawaited(
          _hydratePendingPermissions(
            currentApi,
            connectionGeneration,
            generation,
            attempt + 1,
          ),
        ),
      );
    }
  }

  Future<
    ({List<PermissionRequest> pending, Set<String> v2IDs, bool v2Succeeded})
  >
  _loadPendingPermissions(OpenCodeApi currentApi) async {
    List<PermissionRequest>? legacy;
    List<PermissionRequest>? v2;
    Object? legacyError;
    Object? v2Error;
    await Future.wait<void>([
      () async {
        try {
          legacy = await currentApi.pendingPermissions();
        } catch (error) {
          legacyError = error;
        }
      }(),
      () async {
        try {
          v2 = await currentApi.pendingPermissionsV2();
        } catch (error) {
          v2Error = error;
        }
      }(),
    ]);
    if (legacy == null && v2 == null) {
      throw ApiException(
        'Could not hydrate pending permissions: '
        '${legacyError ?? v2Error ?? 'no endpoint available'}',
      );
    }
    final merged = <String, PermissionRequest>{
      for (final permission in legacy ?? const <PermissionRequest>[])
        permission.id: permission,
      // Prefer V2 when both APIs briefly expose the same request so reply
      // routing follows the newer, session-scoped contract.
      for (final permission in v2 ?? const <PermissionRequest>[])
        permission.id: permission,
    };
    return (
      pending: merged.values.toList(),
      v2IDs: {
        for (final permission in v2 ?? const <PermissionRequest>[])
          permission.id,
      },
      v2Succeeded: v2 != null,
    );
  }

  bool _isCurrentPermissionHydration(
    OpenCodeApi currentApi,
    int connectionGeneration,
    int generation,
  ) =>
      _isCurrent(connectionGeneration, currentApi) &&
      generation == _permissionHydrationGeneration;

  void _cancelPermissionHydration() {
    _permissionHydrationGeneration += 1;
    _permissionHydrationRetry?.cancel();
    _permissionHydrationRetry = null;
    permissionsLoading = false;
  }

  Future<void> answerPermission(String requestID, String response) async {
    final permission = permissions[requestID];
    final currentApi = api;
    final generation = _generation;
    if (permission == null) {
      if (_resolvedPermissionIDs.contains(requestID)) return;
      throw StateError('Permission request $requestID is no longer pending');
    }
    if (currentApi == null) {
      throw StateError('Not connected to OpenCode');
    }
    try {
      final legacyIdentity = _legacyPermissionIdentities[requestID];
      final v2SessionID = _v2PermissionSessions[requestID];
      if (v2SessionID != null) {
        await currentApi.respondPermissionV2(
          v2SessionID,
          permission.id,
          response,
        );
      } else {
        await currentApi.respondPermission(
          permission.id,
          response,
          legacySessionID: legacyIdentity?.sessionID,
          legacyPermissionID: legacyIdentity?.permissionID,
        );
      }
      if (!_isCurrent(generation, currentApi)) return;
      _resolvePermission(requestID);
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return;
      if (_resolvedPermissionIDs.contains(requestID)) return;
      if (error is ApiException && error.isPermissionNotFound(permission.id)) {
        _resolvePermission(requestID);
        return;
      }
      lastError = error.toString();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> refreshPendingQuestions() async {
    final current = repository;
    final currentApi = api;
    final generation = _generation;
    if (current == null) return;
    final refreshGeneration = ++_questionsRefreshGeneration;
    final revision = _questionRevision;
    questionsLoading = true;
    questionsError = null;
    notifyListeners();
    try {
      final results = await _loadPendingQuestions(currentApi, current);
      if (!_isCurrentQuestionsRefresh(
        generation,
        currentApi,
        current,
        refreshGeneration,
      )) {
        return;
      }
      final hydrated = {
        for (final question in results.pending)
          if (!_resolvedQuestionIDs.contains(question.id))
            question.id: question,
      };
      if (!results.v2Succeeded) {
        for (final entry in questions.entries) {
          if (_v2QuestionSessions.containsKey(entry.key) &&
              !_resolvedQuestionIDs.contains(entry.key)) {
            hydrated.putIfAbsent(entry.key, () => entry.value);
          }
        }
      }
      final questionIDs = {...questions.keys, ...hydrated.keys};
      for (final id in questionIDs) {
        if ((_questionRevisions[id] ?? 0) > revision) continue;
        final question = hydrated[id];
        if (question == null) {
          questions.remove(id);
          _v2QuestionSessions.remove(id);
        } else {
          questions[id] = question;
          if (results.v2Succeeded) {
            if (results.v2IDs.contains(id)) {
              _v2QuestionSessions[id] = question.sessionID;
            } else {
              _v2QuestionSessions.remove(id);
            }
          }
        }
      }
      questionsLoading = false;
      notifyListeners();
    } catch (error) {
      if (!_isCurrentQuestionsRefresh(
        generation,
        currentApi,
        current,
        refreshGeneration,
      )) {
        return;
      }
      questionsLoading = false;
      questionsError = error.toString();
      _recordLocationError(questionsError!);
      notifyListeners();
    }
  }

  Future<({List<PendingQuestion> pending, Set<String> v2IDs, bool v2Succeeded})>
  _loadPendingQuestions(
    OpenCodeApi? currentApi,
    ProductRepository currentRepository,
  ) async {
    List<PendingQuestion>? legacy;
    List<PendingQuestion>? v2;
    Object? legacyError;
    Object? v2Error;
    await Future.wait<void>([
      () async {
        try {
          legacy = await currentRepository.listQuestions();
        } catch (error) {
          legacyError = error;
        }
      }(),
      () async {
        if (currentApi == null) return;
        try {
          final raw = await currentApi.pendingQuestionsV2();
          v2 = raw
              .map(PendingQuestion.fromJson)
              .where(
                (question) =>
                    question.id.isNotEmpty && question.sessionID.isNotEmpty,
              )
              .toList();
        } catch (error) {
          v2Error = error;
        }
      }(),
    ]);
    if (legacy == null && v2 == null) {
      throw StateError(
        'Could not hydrate pending questions: '
        '${legacyError ?? v2Error ?? 'no endpoint available'}',
      );
    }
    final merged = <String, PendingQuestion>{
      for (final question in legacy ?? const <PendingQuestion>[])
        question.id: question,
      for (final question in v2 ?? const <PendingQuestion>[])
        question.id: question,
    };
    return (
      pending: merged.values.toList(),
      v2IDs: {
        for (final question in v2 ?? const <PendingQuestion>[]) question.id,
      },
      v2Succeeded: v2 != null,
    );
  }

  Future<void> answerQuestion(
    String requestID,
    List<List<String>> answers,
  ) async {
    final current = repository;
    final currentApi = api;
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    final v2SessionID = _v2QuestionSessions[requestID];
    try {
      if (v2SessionID != null) {
        if (currentApi == null) throw StateError('Not connected to OpenCode');
        await currentApi.answerQuestionV2(v2SessionID, requestID, answers);
      } else {
        await current.answerQuestion(requestID, answers);
      }
    } catch (error) {
      if (!_isCurrent(generation, currentApi) || repository != current) return;
      if (_isQuestionNotFound(error, requestID)) {
        _resolveQuestion(requestID);
        return;
      }
      rethrow;
    }
    if (!_isCurrent(generation, currentApi) || repository != current) return;
    _resolveQuestion(requestID);
  }

  Future<void> rejectQuestion(String requestID) async {
    final current = repository;
    final currentApi = api;
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    final v2SessionID = _v2QuestionSessions[requestID];
    try {
      if (v2SessionID != null) {
        if (currentApi == null) throw StateError('Not connected to OpenCode');
        await currentApi.rejectQuestionV2(v2SessionID, requestID);
      } else {
        await current.rejectQuestion(requestID);
      }
    } catch (error) {
      if (!_isCurrent(generation, currentApi) || repository != current) return;
      if (_isQuestionNotFound(error, requestID)) {
        _resolveQuestion(requestID);
        return;
      }
      rethrow;
    }
    if (!_isCurrent(generation, currentApi) || repository != current) return;
    _resolveQuestion(requestID);
  }

  void _resolveQuestion(String requestID) {
    _markQuestionChanged(requestID);
    questionsLoading = false;
    _v2QuestionSessions.remove(requestID);
    _resolvedQuestionIDs.add(requestID);
    questions.remove(requestID);
    notifyListeners();
  }

  bool _isQuestionNotFound(Object error, String requestID) {
    if (error is ApiException) return error.isQuestionNotFound(requestID);
    if (error is ProductException && error.cause != null) {
      return _isQuestionNotFound(error.cause!, requestID);
    }
    if (error is sdk.OpenCodeApiException && error.statusCode == 404) {
      final decoded = error.payloadAs<sdk.QuestionNotFoundError>();
      if (decoded != null) return decoded.requestID == requestID;
      final raw = error.rawPayload;
      return raw is Map &&
          raw['_tag']?.toString() == 'QuestionNotFoundError' &&
          raw['requestID']?.toString() == requestID;
    }
    return false;
  }

  @visibleForTesting
  void handleEventForTesting(EventEnvelope event) => _onEvent(event);

  // ---------------- Sessions ----------------

  Future<void> refreshSessions() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final refreshGeneration = ++_sessionsRefreshGeneration;
    final revision = _sessionRevision;
    sessionsLoading = true;
    sessionsError = null;
    notifyListeners();
    try {
      final list = await currentApi.sessions();
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      Map<String, String>? statuses;
      Object? statusError;
      try {
        statuses = await currentApi.sessionStatuses();
      } catch (error) {
        statusError = error;
      }
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      final hydrated = {for (final session in list) session.id: session};
      final sessionIDs = {...sessionsById.keys, ...hydrated.keys};
      for (final id in sessionIDs) {
        if ((_sessionRevisions[id] ?? 0) > revision) continue;
        final session = hydrated[id];
        if (session == null) {
          sessionsById.remove(id);
        } else {
          sessionsById[id] = session;
        }
      }
      if (statuses != null) {
        final statusIDs = {
          ...sessionsById.keys,
          ...busySessions,
          ...statuses.keys,
        };
        for (final id in statusIDs) {
          if ((_sessionRevisions[id] ?? 0) > revision) continue;
          if (statuses[id] != null && statuses[id] != 'idle') {
            busySessions.add(id);
          } else {
            busySessions.remove(id);
          }
        }
      }
      sessionsLoading = false;
      sessionsError = statusError?.toString();
      if (statusError != null) _recordLocationError(sessionsError!);
      notifyListeners();
    } catch (error) {
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      sessionsLoading = false;
      sessionsError = error.toString();
      _recordLocationError(sessionsError!);
      notifyListeners();
    }
  }

  Future<void> _refreshOneSession(String id) async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final revision = _sessionRevisions[id] ?? 0;
    try {
      final session = await currentApi.session(id);
      if (!_isCurrent(generation, currentApi) ||
          revision != (_sessionRevisions[id] ?? 0)) {
        return;
      }
      sessionsById[id] = session;
      notifyListeners();
    } catch (_) {}
  }

  /// Polling fallback plus terminal-state reconciliation for connected SSE.
  void enablePollingFallback() {
    if (_poll?.isActive ?? false) return;
    _poll = Timer.periodic(const Duration(seconds: 5), (_) {
      if (sessionsLoading) return;
      if (shouldPoll) {
        unawaited(refreshSessions());
      } else if (busySessions.isNotEmpty) {
        // An otherwise healthy SSE connection can still lose one terminal
        // event during a network handoff. Reconcile only active sessions so a
        // missed `idle` cannot leave the chat thinking forever.
        unawaited(_refreshBusySessionStatuses());
      }
    });
  }

  Future<void> _refreshBusySessionStatuses() {
    final inFlight = _busyStatusRefresh;
    if (inFlight != null) return inFlight;
    late final Future<void> tracked;
    tracked = _reconcileBusySessionStatuses().whenComplete(() {
      if (identical(_busyStatusRefresh, tracked)) {
        _busyStatusRefresh = null;
      }
    });
    _busyStatusRefresh = tracked;
    return tracked;
  }

  Future<void> _reconcileBusySessionStatuses() async {
    final currentApi = api;
    final generation = _generation;
    final tracked = {
      for (final id in busySessions) id: _sessionRevisions[id] ?? 0,
    };
    if (currentApi == null || tracked.isEmpty) return;

    Map<String, String> statuses;
    try {
      statuses = await currentApi.sessionStatuses();
    } catch (_) {
      // SSE remains authoritative when this lightweight recovery check fails.
      return;
    }
    if (!_isCurrent(generation, currentApi)) return;

    var changed = false;
    for (final entry in tracked.entries) {
      if ((_sessionRevisions[entry.key] ?? 0) != entry.value) continue;
      final remoteStatus = statuses[entry.key] ?? 'idle';
      if (remoteStatus == 'idle') {
        final removed = busySessions.remove(entry.key);
        changed = removed || changed;
        if (removed) {
          _markSessionChanged(entry.key);
          unawaited(_refreshOneSession(entry.key));
        }
      }
    }
    if (changed) notifyListeners();
  }

  @visibleForTesting
  Future<void> reconcileBusySessionsForTesting() =>
      _refreshBusySessionStatuses();

  /// Stops all network work while the application is backgrounded without
  /// clearing the selected profile, location, or already-rendered data.
  void suspendForLifecycle() {
    if (_disposed) return;
    _lifecycleWasBackgrounded = true;
    if (keepLiveInBackground) return;
    if (_lifecycleSuspended) return;
    _lifecycleSuspended = true;
    // A resume already in flight is invalidated by the generation change
    // below. Detach it so a later resume can create a fresh transport.
    _lifecycleResume = null;
    if (api == null) return;
    _beginGeneration();
    _retireTransport();
    status = StreamStatus.disconnected;
    notifyListeners();
  }

  /// Recreates one transport for the profile/location retained by
  /// [suspendForLifecycle]. Concurrent resume signals share the same future.
  Future<void> resumeFromLifecycle() {
    if (_disposed) return Future.value();
    final inFlight = _lifecycleResume;
    if (inFlight != null) return inFlight;
    if (!_lifecycleSuspended) {
      if (keepLiveInBackground && _lifecycleWasBackgrounded) {
        _lifecycleWasBackgrounded = false;
        return _trackLifecycleResume(_reconcileAfterBackground());
      }
      return Future.value();
    }
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    final profile = _connectedProfile;
    if (profile == null) return Future.value();
    return _trackLifecycleResume(
      _resumeLifecycleTransport(
        profile,
        directory: directory,
        workspace: workspace,
      ),
    );
  }

  Future<void> _trackLifecycleResume(Future<void> operation) {
    late final Future<void> tracked;
    tracked = operation.whenComplete(() {
      if (identical(_lifecycleResume, tracked)) _lifecycleResume = null;
    });
    _lifecycleResume = tracked;
    return tracked;
  }

  /// Waits until wake-time transport and catalog reconciliation completes,
  /// then returns the API instance that foreground actions should use.
  ///
  /// Chat and other retained screens must not capture [api] before this
  /// future completes because a stale background transport may be replaced.
  Future<OpenCodeApi?> prepareActionTransport() async {
    await resumeFromLifecycle();
    if (_disposed || _lifecycleSuspended) return null;
    return api;
  }

  Future<void> _reconcileAfterBackground() async {
    final currentApi = api;
    if (currentApi == null) return;
    final generation = _generation;
    try {
      await _ensureLocalServerWakeLock();
      if (!_isCurrent(generation, currentApi)) return;
      final health = await currentApi.health();
      if (!_isCurrent(generation, currentApi)) return;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      version = health.version ?? version ?? '';
    } catch (_) {
      if (!_isCurrent(generation, currentApi)) return;
      final profile = _connectedProfile;
      if (profile == null) return;
      await _resumeLifecycleTransport(
        profile,
        directory: directory,
        workspace: workspace,
      );
      return;
    }
    _markDataRefreshReady(generation, currentApi);
    notifyListeners();
    await Future.wait([
      refreshSessions(),
      refreshCatalog(),
      refreshPendingPermissions(),
      refreshPendingQuestions(),
    ]);
  }

  Future<void> _resumeLifecycleTransport(
    ServerProfile profile, {
    String? directory,
    String? workspace,
  }) async {
    final generation = _beginGeneration();
    _retireTransport();
    final currentApi = _apiFactory(profile)
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = _repositoryFactory(currentApi)
      ..setLocation(directory: directory, workspace: workspace);
    api = currentApi;
    repository = currentRepository;
    status = StreamStatus.connecting;
    lastError = null;
    notifyListeners();
    enablePollingFallback();
    try {
      await _ensureLocalServerWakeLock();
      if (!_isCurrent(generation, currentApi)) return;
      final health = await currentApi.health();
      if (!_isCurrent(generation, currentApi)) return;
      if (!health.healthy) {
        throw ApiException('Server health check reported unhealthy');
      }
      version = health.version ?? version ?? '';
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return;
      _failCurrentConnection(
        error is ApiException
            ? error.message
            : 'Cannot reach ${profile.baseUrl}: $error',
      );
      return;
    }
    _startEvents(generation, currentApi);
    _markDataRefreshReady(generation, currentApi);
    await Future.wait<void>([
      refreshSessions(),
      _loadCatalog(),
      refreshPendingPermissions(),
      refreshPendingQuestions(),
    ]);
  }

  List<Session> sortedSessions() {
    final list =
        sessionsById.values
            .where((s) => s.parentID == null && !s.archived)
            .toList()
          ..sort((a, b) {
            final au = a.time?.updated ?? a.time?.created ?? 0;
            final bu = b.time?.updated ?? b.time?.created ?? 0;
            return bu.compareTo(au);
          });
    return list;
  }

  List<Session> archivedSessions() {
    final list =
        sessionsById.values.where((session) => session.archived).toList()..sort(
          (a, b) => (b.time?.archived ?? 0).compareTo(a.time?.archived ?? 0),
        );
    return list;
  }

  Future<void> selectLocation({String? directory, String? workspace}) async {
    final profile = _connectedProfile;
    if (profile == null || api == null) return;
    if (this.directory == directory && this.workspace == workspace) return;

    final generation = _beginGeneration();
    final previousVersion = version;
    _retireTransport();
    final currentApi = _apiFactory(profile)
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = _repositoryFactory(currentApi)
      ..setLocation(directory: directory, workspace: workspace);
    api = currentApi;
    repository = currentRepository;
    this.directory = directory;
    this.workspace = workspace;
    version = previousVersion;
    locationRevision += 1;
    locationLoading = true;
    locationError = null;
    lastError = null;
    _clearLocationData();
    status = StreamStatus.connecting;
    notifyListeners();
    enablePollingFallback();
    _startEvents(generation, currentApi);
    _markDataRefreshReady(generation, currentApi);

    await Future.wait<void>([
      refreshSessions(),
      _loadCatalog(),
      refreshPendingPermissions(),
      refreshPendingQuestions(),
    ]);
    if (!_isCurrent(generation, currentApi)) return;
    locationLoading = false;
    notifyListeners();
  }

  // ---------------- Selection persistence ----------------

  Future<void> selectModel(ModelRef ref, {String? variant}) async {
    final nextVariant = variant ?? '';
    final matchingModel = catalog?.models.where(
      (model) => model.providerID == ref.providerID && model.id == ref.modelID,
    );
    if (nextVariant.isNotEmpty &&
        (matchingModel == null ||
            matchingModel.isEmpty ||
            !matchingModel.first.variants.any(
              (item) => item.id == nextVariant && !item.disabled,
            ))) {
      return;
    }
    selectedModel = ref;
    selectedVariant = nextVariant;
    final p = profile;
    final generation = _generation;
    if (p != null) {
      await store.setModel(p.id, ref.providerID, ref.modelID, explicit: true);
      await store.setVariant(p.id, nextVariant);
    }
    if (_disposed || generation != _generation) return;
    notifyListeners();
  }

  Future<void> selectVariant(String variant) async {
    final model = selectedModel;
    if (model == null) return;
    await selectModel(model, variant: variant);
  }

  Future<void> selectAgent(String name) async {
    selectedAgent = name;
    final p = profile;
    final generation = _generation;
    if (p != null) await store.setAgent(p.id, name);
    if (_disposed || generation != _generation) return;
    notifyListeners();
  }

  Future<void> refreshCatalog() => _loadCatalog();

  int _beginGeneration() {
    _generation += 1;
    connectionRevision = _generation;
    return _generation;
  }

  void _markDataRefreshReady(int generation, OpenCodeApi currentApi) {
    if (_isCurrent(generation, currentApi)) dataRefreshRevision += 1;
  }

  @visibleForTesting
  void signalDataRefreshForTesting() {
    dataRefreshRevision += 1;
    notifyListeners();
  }

  bool _isCurrent(int generation, OpenCodeApi? currentApi) =>
      !_disposed && generation == _generation && identical(api, currentApi);

  bool _isCurrentStream(
    int generation,
    OpenCodeApi currentApi,
    EventStream stream,
  ) => _isCurrent(generation, currentApi) && identical(_events, stream);

  bool _isCurrentSessionsRefresh(
    int generation,
    OpenCodeApi currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _sessionsRefreshGeneration;

  bool _isCurrentCatalogRefresh(
    int generation,
    OpenCodeApi currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _catalogRefreshGeneration;

  bool _isCurrentQuestionsRefresh(
    int generation,
    OpenCodeApi? currentApi,
    ProductRepository currentRepository,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      identical(repository, currentRepository) &&
      refreshGeneration == _questionsRefreshGeneration;

  void _markSessionChanged(String id) {
    _sessionRevision += 1;
    _sessionRevisions[id] = _sessionRevision;
  }

  void _markQuestionChanged(String id) {
    _questionRevision += 1;
    _questionRevisions[id] = _questionRevision;
  }

  void _failCurrentConnection(String error) {
    _retireTransport();
    _connectedProfile = null;
    version = null;
    status = StreamStatus.disconnected;
    lastError = error;
    notifyListeners();
  }

  void _retireTransport() {
    _cancelPermissionHydration();
    final oldEvents = _events;
    _events = null;
    unawaited(oldEvents?.dispose());
    _poll?.cancel();
    _poll = null;
    _busyStatusRefresh = null;
    final oldApi = api;
    api = null;
    repository = null;
    oldApi?.close();
  }

  void _clearLocationData() {
    _sessionsRefreshGeneration += 1;
    _catalogRefreshGeneration += 1;
    _questionsRefreshGeneration += 1;
    _questionRevision += 1;
    _questionRevisions.clear();
    _sessionRevision += 1;
    _sessionRevisions.clear();
    sessionsById = {};
    busySessions = {};
    permissions = {};
    questions = {};
    _resolvedPermissionIDs.clear();
    _legacyPermissionIdentities.clear();
    _v2PermissionSessions.clear();
    _v2QuestionSessions.clear();
    _resolvedQuestionIDs.clear();
    _permissionRevision = 0;
    providers = null;
    agents = [];
    catalog = null;
    catalogDetailed = false;
    sessionsLoading = false;
    sessionsError = null;
    catalogLoading = false;
    catalogError = null;
    permissionsLoading = false;
    permissionsError = null;
    questionsLoading = false;
    questionsError = null;
    lastPtyEvent = null;
  }

  void _recordLocationError(String error) {
    if (locationLoading) locationError ??= error;
  }

  Future<void> _writeActiveProfile(int generation, String? id) {
    final previous = _activeProfileWrite;
    final write = () async {
      try {
        await previous;
      } catch (_) {
        // A later generation still needs a chance to persist its selection.
      }
      if (_disposed || generation != _generation) return;
      await store.setActiveId(id);
    }();
    _activeProfileWrite = write;
    return write;
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _generation += 1;
    connectionRevision = _generation;
    _retireTransport();
    backgroundLive.removeListener(_backgroundLiveChanged);
    backgroundLive.dispose();
    unawaited(_eventBus.close());
    super.dispose();
  }
}

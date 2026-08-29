import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import '../api/opencode_api.dart';
import '../api/product_repository.dart';
import '../api/server_probe.dart';
import '../api/sse.dart';
import '../api2/client.dart';
import '../api2/gateway.dart';
import '../api2/gateway_operations.dart';
import '../api2/transport.dart' show Api2AuthRequired;
import '../background/live_background.dart';
import '../background/widget_snapshot.dart';
import '../diagnostics/app_diagnostics.dart';
import '../termux/bridge.dart';
import 'offline_queue.dart';
import 'profiles.dart';
import 'session_drafts.dart';

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

/// Builds the OpenCode 2 gateway pair for a profile whose detected flavor is
/// [ServerFlavor.v2]. Injected by tests; production uses the Api2Transport →
/// Api2Client → Api2Gateway/Api2OperationsGateway stack.
typedef V2GatewayPairFactory =
    ({ServerGateway gateway, ServerOperationsGateway operations}) Function(
      ServerProfile profile,
    );
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
  final WidgetSessionSnapshot _widgetSnapshot;
  final AppDiagnosticsController diagnostics;
  final bool _ownsDiagnostics;
  late final ValueNotifier<AppAppearance> appearance;
  late final ValueNotifier<ThemePackId> themePack;
  final OpenCodeApiFactory _apiFactory;
  final ProductRepositoryFactory _repositoryFactory;
  final V2GatewayPairFactory _v2GatewayFactory;
  final EventStreamFactory _eventStreamFactory;
  final EventStreamFactory? _globalEventStreamFactory;
  final LocalWakeLockEnsurer _localWakeLockEnsurer;

  ServerGateway? api;
  ServerOperationsGateway? repository;
  LiveEventChannel? _events;
  LiveEventChannel? _globalEvents;
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
  String? availableServerVersion;
  String? installedServerVersion;
  String? lastError;

  /// True after the connected v2 server answered 401 mid-session — the serve
  /// password rotated (it changes on every restart unless OPENCODE_PASSWORD
  /// is set). Basic auth cannot self-heal, so retry loops stay off and the
  /// connection banner offers "Update password" instead (never a modal).
  /// Cleared when a new connect starts, on disconnect, and when the stream
  /// recovers.
  bool passwordRejected = false;

  int connectionRevision = 0;

  /// Advances whenever a usable transport is ready and screen-owned data
  /// should be rehydrated. This also advances after an SSE reconnection so
  /// events missed during a network handoff are reconciled from REST.
  int dataRefreshRevision = 0;

  /// Prompts drafted while the server was unreachable, waiting to flush.
  /// Loaded lazily from [OfflineQueueStore] and kept in memory afterward.
  List<QueuedPrompt>? _offlineQueue;
  OfflineQueueStore? _offlineQueueStore;
  bool _flushingOfflineQueue = false;

  /// Composer text typed in a chat but never sent, kept per session so
  /// navigating between sessions loses nothing. Loaded lazily from
  /// [SessionDraftStore] and kept in memory afterward.
  Map<String, SessionDraft>? _sessionDrafts;
  SessionDraftStore? _sessionDraftStore;
  int locationRevision = 0;
  String? directory;
  String? workspace;
  bool locationLoading = false;
  String? locationError;
  String? locationNotice;
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
  bool transcriptReasoningExpanded = false;
  bool transcriptTimestampsVisible = false;

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
  final Set<String> _attentionActiveSessions = {};
  final Map<String, ({CodingAlertKind kind, String requestID})>
  _alertedInputKinds = {};
  final Set<String> _alertedStatusSessions = {};
  CodingAlertOpen? _pendingCodingAlertOpen;
  int _permissionRevision = 0;
  Timer? _permissionHydrationRetry;
  int _permissionHydrationGeneration = 0;
  bool _disposed = false;
  bool _lifecycleSuspended = false;
  bool _lifecycleWasBackgrounded = false;
  Future<void>? _lifecycleResume;
  Future<void>? _manualReconnect;

  /// True only while the app intentionally has its transport retired in the
  /// background. UI must not treat this as a user-initiated disconnect.
  bool get lifecycleSuspended => _lifecycleSuspended;

  /// True while a user-requested reconnect is rebuilding the transport for
  /// the retained server and location.
  bool get manualReconnectInProgress => _manualReconnect != null;

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
    V2GatewayPairFactory? v2GatewayFactory,
    EventStreamFactory? eventStreamFactory,
    EventStreamFactory? globalEventStreamFactory,
    BackgroundLiveController? backgroundLive,
    AppDiagnosticsController? diagnostics,
    LocalWakeLockEnsurer? localWakeLockEnsurer,
  }) : _apiFactory = apiFactory ?? _createApi,
       _repositoryFactory = repositoryFactory ?? _createRepository,
       _v2GatewayFactory = v2GatewayFactory ?? _createV2GatewayPair,
       _eventStreamFactory = eventStreamFactory ?? _createEventStream,
       _globalEventStreamFactory =
           globalEventStreamFactory ??
           (eventStreamFactory == null ? _createGlobalEventStream : null),
       _localWakeLockEnsurer =
           localWakeLockEnsurer ?? TermuxBridge.ensureWakeLock,
       backgroundLive =
           backgroundLive ?? BackgroundLiveController(preferences: store.prefs),
       _widgetSnapshot = WidgetSessionSnapshot(prefs: store.prefs),
       diagnostics = diagnostics ?? AppDiagnosticsController(),
       _ownsDiagnostics = diagnostics == null {
    appearance = ValueNotifier(store.appearance);
    themePack = ValueNotifier(store.themePack);
    transcriptReasoningExpanded = store.transcriptReasoningExpanded;
    transcriptTimestampsVisible = store.transcriptTimestampsVisible;
    this.backgroundLive.addListener(_backgroundLiveChanged);
    this.backgroundLive.bindActionHandler(_handleCodingAlertAction);
  }

  /// Resolves an Android notification action while the app stays
  /// backgrounded. Alerts exist only while live mode keeps the transport
  /// alive, so replies go through that live transport directly; running the
  /// foreground wake path here would count as an app resume and clear every
  /// alert. Returns false so Android re-posts the alert when the reply cannot
  /// be delivered.
  Future<bool> _handleCodingAlertAction(CodingAlertAction action) async {
    if (_disposed || _lifecycleSuspended) return false;
    final currentApi = api;
    final current = repository;
    try {
      switch (action.decision) {
        case 'allow':
        case 'deny':
          // Resolution is bound to the exact request the notification
          // represented; a stale or missing ID refreshes the alert instead
          // of resolving whichever request happens to be pending now.
          final permission = permissions[action.requestID];
          if (permission == null || permission.sessionID != action.sessionID) {
            _syncInputAlerts();
            return true;
          }
          if (currentApi == null) return false;
          await _sendPermissionReply(
            currentApi,
            permission.id,
            action.decision == 'allow' ? 'once' : 'reject',
          );
          return true;
        case 'reply':
          final text = action.reply?.trim() ?? '';
          if (text.isEmpty) return false;
          final question = questions[action.requestID];
          if (question == null ||
              question.sessionID != action.sessionID ||
              !_questionSupportsQuickReply(question)) {
            _syncInputAlerts();
            return true;
          }
          await _sendQuestionAnswer(currentApi, current, question.id, [
            [text],
          ]);
          return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  /// A question qualifies for a notification quick reply only when one typed
  /// answer can truthfully satisfy it: a single prompt that accepts custom
  /// text.
  static bool _questionSupportsQuickReply(PendingQuestion question) =>
      question.prompts.length == 1 && question.prompts.single.custom;

  PendingQuestion? questionForSession(String sessionID) {
    for (final question in questions.values) {
      if (question.sessionID == sessionID) return question;
    }
    return null;
  }

  PendingQuestion? _quickReplyQuestionForSession(String sessionID) {
    for (final question in questions.values) {
      if (question.sessionID == sessionID &&
          _questionSupportsQuickReply(question)) {
        return question;
      }
    }
    return null;
  }

  bool get keepLiveInBackground => backgroundLive.enabled;

  Future<bool> setKeepLiveInBackground(bool enabled) =>
      backgroundLive.setEnabled(enabled);

  CodingAlertOpen? get pendingCodingAlertOpen => _pendingCodingAlertOpen;

  CodingAlertOpen? takePendingCodingAlertOpen() {
    final value = _pendingCodingAlertOpen;
    _pendingCodingAlertOpen = null;
    return value;
  }

  Future<void> restoreBackgroundLiveMode() async {
    await backgroundLive.restore();
    await consumeCodingAlertOpen();
  }

  Future<void> consumeCodingAlertOpen() async {
    final value = await backgroundLive.consumeCodingAlertOpen();
    if (_disposed || value == null) return;
    // Home-screen widget rows outlive profile switches: a tap stamped with
    // another profile's ID opens the app normally rather than silently
    // routing into (or switching to) that profile's chat. Notification taps
    // carry no profile ID and keep routing as before.
    if (value.profileID.isNotEmpty && value.profileID != store.activeId) {
      return;
    }
    _pendingCodingAlertOpen = value;
    notifyListeners();
  }

  Future<void> setTranscriptReasoningExpanded(bool expanded) async {
    await store.setTranscriptReasoningExpanded(expanded);
    if (_disposed) return;
    transcriptReasoningExpanded = expanded;
    notifyListeners();
  }

  Future<void> setTranscriptTimestampsVisible(bool visible) async {
    await store.setTranscriptTimestampsVisible(visible);
    if (_disposed) return;
    transcriptTimestampsVisible = visible;
    notifyListeners();
  }

  Future<void> setThemePack(ThemePackId value) async {
    await store.setThemePack(value);
    themePack.value = value;
  }

  Future<void> setAppearance(AppAppearance value) async {
    await store.setAppearance(value);
    if (_disposed) return;
    appearance.value = value;
  }

  void _backgroundLiveChanged() {
    if (keepLiveInBackground) {
      unawaited(_ensureLocalServerWakeLock());
    } else {
      _dismissAllCodingAlerts(clearActive: true);
    }
    if (!_disposed) notifyListeners();
  }

  static String _inputAlertKey(String sessionID) => 'input:$sessionID';
  static String _statusAlertKey(String sessionID) => 'status:$sessionID';

  bool get _canShowCodingAlert =>
      keepLiveInBackground &&
      _lifecycleWasBackgrounded &&
      backgroundLive.notificationGranted;

  void _markSessionAttentionActive(String sessionID) {
    if (sessionID.isEmpty) return;
    _attentionActiveSessions.add(sessionID);
    if (_alertedStatusSessions.remove(sessionID)) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
  }

  void _settleSessionAttention(String sessionID, CodingAlertKind kind) {
    if (sessionID.isEmpty || !_attentionActiveSessions.remove(sessionID)) {
      return;
    }
    if (!_canShowCodingAlert || sessionsById[sessionID]?.parentID != null) {
      return;
    }
    if (!_alertedStatusSessions.add(sessionID)) return;
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            sessionID: sessionID,
            key: _statusAlertKey(sessionID),
          )
          .then((shown) {
            if (!shown && !_disposed && _lifecycleWasBackgrounded) {
              _alertedStatusSessions.remove(sessionID);
            }
          }),
    );
  }

  void _showInputAlert(String sessionID, CodingAlertKind kind) {
    if (sessionID.isEmpty || !_canShowCodingAlert) return;
    // The alert represents one exact request: the front permission, or the
    // quick-reply-eligible question (falling back to the front question).
    final quickReplyQuestion = kind == CodingAlertKind.question
        ? _quickReplyQuestionForSession(sessionID)
        : null;
    final requestID = kind == CodingAlertKind.permission
        ? permissionForSession(sessionID)?.id
        : (quickReplyQuestion ?? questionForSession(sessionID))?.id;
    if (requestID == null || requestID.isEmpty) return;
    final alerted = (kind: kind, requestID: requestID);
    if (_alertedInputKinds[sessionID] == alerted) return;
    _alertedInputKinds[sessionID] = alerted;
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            sessionID: sessionID,
            key: _inputAlertKey(sessionID),
            quickReply: quickReplyQuestion != null,
            requestID: requestID,
          )
          .then((shown) {
            if (!shown &&
                !_disposed &&
                _lifecycleWasBackgrounded &&
                _alertedInputKinds[sessionID] == alerted) {
              _alertedInputKinds.remove(sessionID);
            }
          }),
    );
  }

  void _syncInputAlerts() {
    final permissionSessions = {
      for (final permission in permissions.values) permission.sessionID,
    }..removeWhere((id) => id.isEmpty);
    final questionSessions = {
      for (final question in questions.values) question.sessionID,
    }..removeWhere((id) => id.isEmpty);
    final pendingSessions = {...permissionSessions, ...questionSessions};

    for (final sessionID in _alertedInputKinds.keys.toList()) {
      if (pendingSessions.contains(sessionID)) continue;
      _alertedInputKinds.remove(sessionID);
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    if (!_canShowCodingAlert) return;
    for (final sessionID in pendingSessions) {
      _showInputAlert(
        sessionID,
        permissionSessions.contains(sessionID)
            ? CodingAlertKind.permission
            : CodingAlertKind.question,
      );
    }
  }

  void _dismissSessionCodingAlerts(String sessionID) {
    _attentionActiveSessions.remove(sessionID);
    if (_alertedInputKinds.remove(sessionID) != null) {
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    if (_alertedStatusSessions.remove(sessionID)) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
  }

  void _dismissAllCodingAlerts({bool clearActive = false}) {
    for (final sessionID in _alertedInputKinds.keys.toList()) {
      unawaited(backgroundLive.dismissCodingAlert(_inputAlertKey(sessionID)));
    }
    for (final sessionID in _alertedStatusSessions.toList()) {
      unawaited(backgroundLive.dismissCodingAlert(_statusAlertKey(sessionID)));
    }
    _alertedInputKinds.clear();
    _alertedStatusSessions.clear();
    if (clearActive) _attentionActiveSessions.clear();
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

  /// Production wiring for an OpenCode 2 profile: one Basic-auth transport
  /// and client shared by both gateway halves. Username stays `opencode` on
  /// the wire (protocol notes §1); the profile's stored password rides every
  /// request.
  static ({ServerGateway gateway, ServerOperationsGateway operations})
  _createV2GatewayPair(ServerProfile profile) {
    final client = Api2Client.connect(
      baseUrl: profile.baseUrl,
      password: profile.password,
    );
    return (
      gateway: Api2Gateway(client: client),
      operations: Api2OperationsGateway(client: client),
    );
  }

  /// Constructs the transport pair for [profile]'s cached flavor. The two
  /// v1 factories stay the injected test seams; v2 goes through
  /// [_v2GatewayFactory].
  ({ServerGateway gateway, ServerOperationsGateway operations})
  _buildTransportPair(ServerProfile profile) {
    if (profile.flavor == ServerFlavor.v2) return _v2GatewayFactory(profile);
    final v1Api = _apiFactory(profile);
    return (gateway: v1Api, operations: _repositoryFactory(v1Api));
  }

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

  static EventStream _createGlobalEventStream({
    required OpenCodeApi api,
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => EventStream(
    api: api,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
    global: true,
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

  void _acceptRunningServerVersion(String? rawVersion) {
    final next = rawVersion?.trim() ?? '';
    if (next.isEmpty) return;
    version = next;
    if (availableServerVersion == next) availableServerVersion = null;
    if (installedServerVersion == next) installedServerVersion = null;
  }

  void recordServerUpgradeInstalled(String rawVersion) {
    final installed = rawVersion.trim();
    if (!isExactServerVersion(installed)) return;
    installedServerVersion = installed;
    if (availableServerVersion == installed) availableServerVersion = null;
    notifyListeners();
  }

  Future<ProfileLocation?> _validatedSavedLocation(
    ServerProfile profile,
    ServerOperationsGateway currentRepository,
    int generation,
    ServerGateway currentApi,
  ) async {
    final saved = store.locationFor(profile.id);
    if (saved == null) return null;
    var workspace = saved.workspace;
    try {
      if (saved.directory != null) {
        currentRepository.setLocation(
          directory: saved.directory,
          workspace: null,
        );
        final project = await currentRepository.loadCurrentProject();
        if (!_isCurrent(generation, currentApi)) return null;
        if (project == null) {
          try {
            await store.clearLocation(profile.id);
          } catch (_) {
            // The current connection can still recover to its server root.
          }
          locationNotice =
              'The last project is no longer available. '
              'OpenCode Mobile returned to the server workspace.';
          return null;
        }
      }
      if (workspace != null) {
        currentRepository.setLocation(
          directory: saved.directory,
          workspace: null,
        );
        final workspaces = await currentRepository.listWorkspaces();
        if (!_isCurrent(generation, currentApi)) return null;
        if (!workspaces.any((candidate) => candidate.id == workspace)) {
          workspace = null;
          locationNotice =
              'The last remote workspace is no longer available. '
              'The project was opened locally.';
        }
      }
      return ProfileLocation(directory: saved.directory, workspace: workspace);
    } catch (_) {
      if (_isCurrent(generation, currentApi)) {
        locationNotice =
            'The last project could not be verified. '
            'OpenCode Mobile opened the server workspace instead.';
      }
      return null;
    } finally {
      currentRepository.setLocation(directory: null, workspace: null);
    }
  }

  /// [redetectOnFailure] lets one failed connect re-probe the address and
  /// correct a stale cached [ServerProfile.flavor] (a server swapped between
  /// `opencode serve` generations) before giving up; the corrected retry runs
  /// with it false so detection can never loop.
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
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
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: null, workspace: null);
    final currentRepository = pair.operations
      ..setLocation(directory: null, workspace: null);
    api = currentApi;
    repository = currentRepository;
    _connectedProfile = profile;
    availableServerVersion = null;
    installedServerVersion = null;
    directory = null;
    workspace = null;
    locationRevision += 1;
    _clearLocationData();
    status = StreamStatus.connecting;
    lastError = null;
    passwordRejected = false;
    locationError = null;
    locationNotice = null;
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
      _acceptRunningServerVersion(health.version);
    } catch (e) {
      if (!_isCurrent(generation, currentApi)) return;
      if (redetectOnFailure && _suggestsWrongFlavor(e)) {
        final corrected = await _redetectFlavor(profile);
        if (!_isCurrent(generation, currentApi)) return;
        if (corrected != null) {
          if (corrected.ok) {
            await connect(profile, redetectOnFailure: false);
          } else {
            _failCurrentConnection(
              corrected.message ?? 'Cannot reach ${profile.baseUrl}: $e',
            );
          }
          return;
        }
      }
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

    final savedLocation = await _validatedSavedLocation(
      profile,
      currentRepository,
      generation,
      currentApi,
    );
    if (!_isCurrent(generation, currentApi)) return;
    if (savedLocation != null) {
      await _selectLocation(
        directory: savedLocation.directory,
        workspace: savedLocation.workspace,
        preserveNotice: true,
      );
      return;
    }

    await _refreshPreexistingProviderRuntime(
      generation: generation,
      currentApi: currentApi,
      currentRepository: currentRepository,
      profile: profile,
    );
    if (!_isCurrent(generation, currentApi)) return;

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

      Future<ChatDefaults?> loadChatDefaults() async {
        if (currentRepository == null) return null;
        try {
          return await currentRepository.loadChatDefaults();
        } catch (_) {
          return null;
        }
      }

      final results = await Future.wait<Object?>([
        currentApi.providers(),
        currentApi.agents(),
        loadDetailedCatalog(),
        loadIntegrations(),
        loadChatDefaults(),
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

  void _startEvents(int generation, ServerGateway currentApi) {
    late final LiveEventChannel stream;
    void handleEvent(EventEnvelope event) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      _onEvent(event);
    }

    void handleStatus(StreamStatus s) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      final previousStatus = status;
      status = s;
      if (s == StreamStatus.connected) {
        lastError = null;
        passwordRejected = false;
        unawaited(refreshPendingPermissions());
        unawaited(refreshPendingQuestions());
        unawaited(flushOfflineQueue());
        if (previousStatus == StreamStatus.reconnecting ||
            previousStatus == StreamStatus.disconnected) {
          _markDataRefreshReady(generation, currentApi);
          unawaited(refreshSessions());
        }
      } else {
        _cancelPermissionHydration();
      }
      notifyListeners();
    }

    void handleError(Object e) {
      if (!_isCurrentStream(generation, currentApi, stream)) return;
      _noteAuthFailure(e);
      lastError = e.toString();
      notifyListeners();
    }

    // The v1 factory stays the injected test seam (typed on OpenCodeApi);
    // every other gateway supplies its own channel through the EventGateway
    // interface — the v2 SSE consumer lives behind that seam.
    stream = currentApi is OpenCodeApi
        ? _eventStreamFactory(
            api: currentApi,
            onEvent: handleEvent,
            onStatus: handleStatus,
            onError: handleError,
          )
        : currentApi.openEventChannel(
            onEvent: handleEvent,
            onStatus: handleStatus,
            onError: handleError,
          );
    _events = stream;
    stream.start();
    _startGlobalEvents(generation, currentApi);
  }

  void _startGlobalEvents(int generation, ServerGateway currentApi) {
    late final LiveEventChannel stream;
    void handleEvent(EventEnvelope event) {
      if (!_isCurrentGlobalStream(generation, currentApi, stream)) return;
      if (event.type == 'installation.update-available' ||
          event.type == 'installation.updated' ||
          event.type == 'worktree.ready' ||
          event.type == 'worktree.failed') {
        _onEvent(event);
      }
    }

    if (currentApi is OpenCodeApi) {
      final factory = _globalEventStreamFactory;
      if (factory == null) return;
      stream = factory(
        api: currentApi,
        onEvent: handleEvent,
        // The location-scoped stream owns visible connection state. A global
        // update-notification retry must never make a healthy chat look
        // offline.
        onStatus: (_) {},
        onError: (_) {},
      );
    } else {
      stream = currentApi.openGlobalEventChannel(
        onEvent: handleEvent,
        onStatus: (_) {},
        onError: (_) {},
      );
    }
    _globalEvents = stream;
    stream.start();
  }

  /// Marks a mid-session Basic-auth rejection from the v2 transport so the
  /// connection banner can offer "Update password" instead of retry loops.
  void _noteAuthFailure(Object error) {
    if (error is Api2AuthRequired ||
        (error is ApiException &&
            error.statusCode == 401 &&
            _connectedProfile?.flavor == ServerFlavor.v2)) {
      passwordRejected = true;
    }
  }

  /// True for connect failures shaped like talking to the wrong server
  /// generation: a 401 (v1 client meeting v2's Basic-auth gate) or a 404/405
  /// (v2 client asking a v1 server for `/api/...`). Unreachable addresses and
  /// unhealthy servers are not flavor problems, so they never trigger a
  /// re-probe.
  static bool _suggestsWrongFlavor(Object error) =>
      error is ApiException &&
      (error.statusCode == 401 ||
          error.statusCode == 404 ||
          error.statusCode == 405);

  /// After a failed connect, asks the probe which protocol generation the
  /// address actually speaks. Returns the probe result when it disagrees with
  /// the profile's cached flavor (persisting the correction), null otherwise.
  Future<ServerProbeResult?> _redetectFlavor(ServerProfile profile) async {
    try {
      final result = await serverProbe(
        baseUrl: profile.baseUrl,
        username: profile.username,
        password: profile.password,
      );
      final detected = result.flavor;
      if (detected == ServerFlavor.unknown || detected == profile.flavor) {
        return null;
      }
      profile.flavor = detected;
      if (result.version != null) profile.serverVersion = result.version;
      try {
        await store.upsert(profile);
      } catch (_) {
        // The corrected flavor still applies to this in-memory connect.
      }
      return result;
    } catch (_) {
      return null;
    }
  }

  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    _manualReconnect = null;
    final generation = _beginGeneration();
    _retireTransport();
    _clearLocationData();
    version = null;
    availableServerVersion = null;
    installedServerVersion = null;
    _connectedProfile = null;
    directory = null;
    workspace = null;
    locationRevision += 1;
    locationLoading = false;
    locationError = null;
    locationNotice = null;
    passwordRejected = false;
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
          _acceptRunningServerVersion(v);
          notifyListeners();
        }
        break;

      case 'installation.update-available':
        final target = props['version']?.toString().trim() ?? '';
        if (isExactServerVersion(target) &&
            target != version &&
            target != installedServerVersion) {
          availableServerVersion = target;
          notifyListeners();
        }
        break;

      case 'installation.updated':
        recordServerUpgradeInstalled(props['version']?.toString() ?? '');
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
            _dismissSessionCodingAlerts(id);
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
            _syncInputAlerts();
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
              _markSessionAttentionActive(msg.sessionID);
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
          _syncInputAlerts();
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
          _syncInputAlerts();
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
          if (questions.remove(id) != null) {
            _syncInputAlerts();
            notifyListeners();
          }
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
              _settleSessionAttention(sid, CodingAlertKind.complete);
              unawaited(_refreshOneSession(sid));
              break;
            case 'busy':
            case 'retry':
              busySessions.add(sid);
              _markSessionAttentionActive(sid);
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
          _settleSessionAttention(sid, CodingAlertKind.error);
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
          _settleSessionAttention(sid, CodingAlertKind.complete);
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
    _syncInputAlerts();
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
    _syncInputAlerts();
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
    _syncInputAlerts();
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
    _syncInputAlerts();
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
    ServerGateway currentApi,
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
      _syncInputAlerts();
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
  _loadPendingPermissions(ServerGateway currentApi) async {
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
    ServerGateway currentApi,
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
    if (permission == null) {
      if (_resolvedPermissionIDs.contains(requestID)) return;
      throw StateError('Permission request $requestID is no longer pending');
    }
    final currentApi = await _requireActionTransport();
    await _sendPermissionReply(currentApi, requestID, response);
  }

  /// Sends one permission reply on an already-resolved transport. Notification
  /// actions use this directly with the live background transport because the
  /// foreground path's wake reconciliation doubles as an app resume, which
  /// would clear every posted alert.
  Future<void> _sendPermissionReply(
    ServerGateway currentApi,
    String requestID,
    String response,
  ) async {
    final permission = permissions[requestID];
    if (permission == null) {
      if (_resolvedPermissionIDs.contains(requestID)) return;
      throw StateError('Permission request $requestID is no longer pending');
    }
    final generation = _generation;
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
      _syncInputAlerts();
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
    ServerGateway? currentApi,
    ServerOperationsGateway currentRepository,
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
    await prepareActionTransport();
    await _sendQuestionAnswer(api, repository, requestID, answers);
  }

  /// Sends one question answer on already-resolved transport objects; the
  /// notification-action path passes the live background transport directly
  /// to avoid resume semantics (see [_sendPermissionReply]).
  Future<void> _sendQuestionAnswer(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    String requestID,
    List<List<String>> answers,
  ) async {
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    if (!questions.containsKey(requestID)) {
      if (_resolvedQuestionIDs.contains(requestID)) return;
      throw StateError('Question request $requestID is no longer pending');
    }
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
    await prepareActionTransport();
    final currentApi = api;
    final current = repository;
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    if (!questions.containsKey(requestID)) {
      if (_resolvedQuestionIDs.contains(requestID)) return;
      throw StateError('Question request $requestID is no longer pending');
    }
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
    _syncInputAlerts();
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
          _dismissSessionCodingAlerts(id);
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
            _markSessionAttentionActive(id);
          } else {
            busySessions.remove(id);
            _settleSessionAttention(id, CodingAlertKind.complete);
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
          _settleSessionAttention(entry.key, CodingAlertKind.complete);
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
    if (keepLiveInBackground) {
      _attentionActiveSessions.addAll(busySessions);
      _syncInputAlerts();
      return;
    }
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
        _dismissAllCodingAlerts(clearActive: true);
        return _trackLifecycleResume(_reconcileAfterBackground());
      }
      return Future.value();
    }
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _dismissAllCodingAlerts(clearActive: true);
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

  /// Reconnects the active profile without discarding the selected location
  /// or already-rendered product data. Repeated taps share one operation.
  Future<void> retryConnection() {
    if (_disposed) return Future.value();
    final inFlight = _manualReconnect ?? _lifecycleResume;
    if (inFlight != null) return inFlight;

    final retainedProfile = _connectedProfile ?? profile;
    if (retainedProfile == null) {
      lastError = 'Choose an OpenCode server before retrying.';
      status = StreamStatus.disconnected;
      notifyListeners();
      return Future.value();
    }

    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _dismissAllCodingAlerts(clearActive: true);

    late final Future<void> tracked;
    tracked =
        _resumeLifecycleTransport(
          retainedProfile,
          directory: directory,
          workspace: workspace,
        ).whenComplete(() {
          if (identical(_manualReconnect, tracked)) {
            _manualReconnect = null;
            if (!_disposed) notifyListeners();
          }
        });
    _manualReconnect = tracked;
    return tracked;
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
  OfflineQueueStore get _queueStore =>
      _offlineQueueStore ??= OfflineQueueStore(prefs: store.prefs);

  List<QueuedPrompt> get _queue => _offlineQueue ??= _queueStore.load();

  /// Queued prompts for one session of the active profile, oldest first.
  List<QueuedPrompt> queuedPromptsFor(String sessionID) {
    final profileID = profile?.id;
    if (profileID == null) return const [];
    return [
      for (final entry in _queue)
        if (entry.profileID == profileID && entry.sessionID == sessionID)
          entry,
    ];
  }

  /// Queued prompts across the active profile, for the connection banner.
  int get queuedPromptCount {
    final profileID = profile?.id;
    if (profileID == null) return 0;
    return _queue.where((entry) => entry.profileID == profileID).length;
  }

  /// Queued prompts that belong to profiles other than the active one. A
  /// flush never sends these; the count lets banners and the flush notice
  /// say "N drafts waiting for other servers" instead of staying silent.
  int get queuedPromptCountForOtherProfiles {
    final profileID = profile?.id;
    return _queue.where((entry) => entry.profileID != profileID).length;
  }

  /// Advances after a flush cycle that delivered at least one queued
  /// prompt; [lastFlushedPromptCount] and [lastFlushSkippedForOtherProfiles]
  /// describe that cycle. Screens compare revisions in their listener to
  /// show a one-shot "Sent N queued prompts" confirmation.
  int offlineFlushRevision = 0;
  int lastFlushedPromptCount = 0;
  int lastFlushSkippedForOtherProfiles = 0;

  /// Adds a drafted prompt to the offline queue. Returns false when the
  /// entry exceeds the composer's aggregate attachment cap and was not
  /// queued; the caller keeps its existing limits messaging.
  Future<bool> queuePrompt(QueuedPrompt prompt) async {
    if (prompt.payloadBytes > OfflineQueueStore.maxEntryBytes) return false;
    _queue.add(prompt);
    await _queueStore.save(_queue);
    notifyListeners();
    return true;
  }

  Future<void> removeQueuedPrompt(String id) async {
    _queue.removeWhere((entry) => entry.id == id);
    await _queueStore.save(_queue);
    notifyListeners();
  }

  SessionDraftStore get _draftStore =>
      _sessionDraftStore ??= SessionDraftStore(prefs: store.prefs);

  Map<String, SessionDraft> get _drafts =>
      _sessionDrafts ??= _draftStore.load();

  /// The unsent composer text remembered for [sessionID], if any.
  String? sessionDraft(String sessionID) => _drafts[sessionID]?.text;

  /// Remembers (or, when [text] is blank, forgets) the composer draft for
  /// one session. No [notifyListeners]: drafts drive nothing outside the
  /// chat screen that saved them.
  Future<void> saveSessionDraft(String sessionID, String text) async {
    if (text.trim().isEmpty) {
      if (_drafts.remove(sessionID) == null) return;
    } else {
      _drafts[sessionID] = SessionDraft(
        sessionID: sessionID,
        text: text,
        updatedAt: DateTime.now().millisecondsSinceEpoch,
      );
      // Mirror the store's cap in memory: oldest drafts fall off first.
      if (_drafts.length > SessionDraftStore.maxDrafts) {
        final oldest = _drafts.values.reduce(
          (a, b) => a.updatedAt <= b.updatedAt ? a : b,
        );
        _drafts.remove(oldest.sessionID);
      }
    }
    await _draftStore.save(_drafts);
  }

  /// Sends queued prompts for the active profile, oldest first, through the
  /// wake-reconciled transport. A connectivity failure stops the flush (the
  /// server is still unreachable); a declared server failure keeps that
  /// entry with its error inline and continues with the next.
  Future<void> flushOfflineQueue() async {
    if (_flushingOfflineQueue || _disposed) return;
    final profileID = profile?.id;
    if (profileID == null) return;
    if (!_queue.any((entry) => entry.profileID == profileID)) return;
    _flushingOfflineQueue = true;
    var mutated = false;
    var sent = 0;
    try {
      for (final entry in List.of(_queue)) {
        if (entry.profileID != profileID) continue;
        final currentApi = await prepareActionTransport();
        if (currentApi == null || status != StreamStatus.connected) break;
        try {
          await currentApi.promptAsync(
            entry.sessionID,
            text: entry.text,
            model: entry.model,
            agent: entry.agent?.isNotEmpty == true ? entry.agent : null,
            variant: entry.variant?.isNotEmpty == true ? entry.variant : null,
            attachments: entry.attachments,
            agentMentions: entry.mentions,
          );
          _queue.removeWhere((queued) => queued.id == entry.id);
          mutated = true;
          sent += 1;
        } on ApiException catch (error) {
          if (error.statusCode == null) break;
          final index = _queue.indexWhere((queued) => queued.id == entry.id);
          if (index >= 0) {
            _queue[index] = entry.withError(error.message);
            mutated = true;
          }
        } catch (_) {
          break;
        }
      }
    } finally {
      _flushingOfflineQueue = false;
      if (sent > 0) {
        lastFlushedPromptCount = sent;
        lastFlushSkippedForOtherProfiles = queuedPromptCountForOtherProfiles;
        offlineFlushRevision += 1;
      }
      if (mutated) {
        await _queueStore.save(_queue);
        notifyListeners();
      }
    }
  }

  Future<ServerGateway?> prepareActionTransport() async {
    await resumeFromLifecycle();
    if (_disposed || _lifecycleSuspended) return null;
    return api;
  }

  /// Returns the product repository paired with the wake-reconciled API.
  ///
  /// Retained screens must resolve this after [prepareActionTransport]
  /// completes because lifecycle recovery can replace both objects together.
  Future<ServerOperationsGateway?> prepareActionRepository() async {
    await prepareActionTransport();
    if (_disposed || _lifecycleSuspended) return null;
    return repository;
  }

  /// Rebuilds the selected location after a configuration patch invalidates
  /// the OpenCode instance that served it.
  Future<void> reloadAfterConfigurationChange() async {
    final currentProfile = _connectedProfile;
    if (currentProfile == null) {
      throw StateError('OpenCode is not connected.');
    }
    await _resumeLifecycleTransport(
      currentProfile,
      directory: directory,
      workspace: workspace,
    );
  }

  Future<ServerGateway> _requireActionTransport() async {
    final actionApi = await prepareActionTransport();
    if (actionApi != null) return actionApi;
    throw ApiException(
      connectionError ?? 'OpenCode is reconnecting. Try again shortly.',
    );
  }

  Future<Session> createSession() async =>
      (await _requireActionTransport()).createSession();

  Future<void> renameSession(String sessionID, String title) async {
    await (await _requireActionTransport()).renameSession(sessionID, title);
  }

  Future<void> deleteSession(String sessionID) async {
    await (await _requireActionTransport()).deleteSession(sessionID);
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
      _acceptRunningServerVersion(health.version ?? version);
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
    _connectedProfile = profile;
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = pair.operations
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
      _acceptRunningServerVersion(health.version ?? version);
    } catch (error) {
      if (!_isCurrent(generation, currentApi)) return;
      _noteAuthFailure(error);
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

  @override
  void notifyListeners() {
    super.notifyListeners();
    // Keep the Android home-screen widget's snapshot in step with session
    // truth; the writer itself skips unchanged payloads.
    if (!_disposed) {
      unawaited(
        _widgetSnapshot.update(
          sessions: sortedSessions(),
          busySessions: busySessions,
          connected: status == StreamStatus.connected,
          profileID: _connectedProfile?.id ?? store.activeId ?? '',
        ),
      );
    }
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

  Future<void> selectLocation({String? directory, String? workspace}) =>
      _selectLocation(directory: directory, workspace: workspace);

  Future<void> selectInitialLocation({String? directory, String? workspace}) =>
      _selectLocation(
        directory: directory,
        workspace: workspace,
        preserveNotice: true,
      );

  Future<void> _selectLocation({
    String? directory,
    String? workspace,
    bool preserveNotice = false,
  }) async {
    final profile = _connectedProfile;
    if (profile == null || api == null) return;
    if (!preserveNotice) locationNotice = null;
    if (this.directory == directory && this.workspace == workspace) {
      try {
        await store.setLocation(
          profile.id,
          directory: directory,
          workspace: workspace,
        );
      } catch (_) {
        locationNotice =
            'This location is active, but it could not be remembered '
            'for the next launch.';
      }
      notifyListeners();
      return;
    }

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
    await _refreshPreexistingProviderRuntime(
      generation: generation,
      currentApi: currentApi,
      currentRepository: currentRepository,
      profile: profile,
    );
    if (!_isCurrent(generation, currentApi)) return;
    _markDataRefreshReady(generation, currentApi);

    await Future.wait<void>([
      refreshSessions(),
      _loadCatalog(),
      refreshPendingPermissions(),
      refreshPendingQuestions(),
    ]);
    if (!_isCurrent(generation, currentApi)) return;
    if (locationError == null) {
      try {
        await store.setLocation(
          profile.id,
          directory: directory,
          workspace: workspace,
        );
      } catch (_) {
        locationNotice =
            'This location is active, but it could not be remembered '
            'for the next launch.';
      }
    }
    if (!_isCurrent(generation, currentApi)) return;
    locationLoading = false;
    notifyListeners();
  }

  Future<void> moveSessionToDirectory(
    String sessionID, {
    required String directory,
    required bool moveChanges,
  }) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.moveSession(
      sessionID,
      directory: directory,
      moveChanges: moveChanges,
    );
    await selectLocation(directory: directory);
    try {
      await repository?.addSessionLocationReminder(sessionID, directory);
    } catch (_) {
      // The move itself succeeded. An older server may not support the
      // synthetic no-reply reminder used by newer OpenCode clients.
    }
  }

  Future<void> warpSessionToWorkspace(
    String sessionID, {
    required String directory,
    required String? workspaceID,
    required bool copyChanges,
  }) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.warpSession(
      sessionID,
      workspaceID: workspaceID,
      copyChanges: copyChanges,
    );
    await selectLocation(directory: directory, workspace: workspaceID);
    try {
      await repository?.addSessionLocationReminder(sessionID, directory);
    } catch (_) {
      // Keep a successful warp successful when only the contextual reminder
      // is unavailable on an older server.
    }
  }

  Future<void> switchConsoleOrganization(
    ConsoleOrganization organization,
  ) async {
    await prepareActionTransport();
    final currentRepository = repository;
    if (currentRepository == null) {
      throw StateError('OpenCode is reconnecting.');
    }
    await currentRepository.switchConsoleOrganization(organization);
    final currentProfile = _connectedProfile;
    if (currentProfile == null) {
      await refreshCatalog();
      return;
    }
    await _resumeLifecycleTransport(
      currentProfile,
      directory: directory,
      workspace: workspace,
    );
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

  Future<void> _refreshPreexistingProviderRuntime({
    required int generation,
    required ServerGateway currentApi,
    required ServerOperationsGateway currentRepository,
    required ServerProfile profile,
  }) async {
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

  int _beginGeneration() {
    _generation += 1;
    connectionRevision = _generation;
    return _generation;
  }

  void _markDataRefreshReady(int generation, ServerGateway currentApi) {
    if (_isCurrent(generation, currentApi)) dataRefreshRevision += 1;
  }

  @visibleForTesting
  void signalDataRefreshForTesting() {
    dataRefreshRevision += 1;
    notifyListeners();
  }

  bool _isCurrent(int generation, ServerGateway? currentApi) =>
      !_disposed && generation == _generation && identical(api, currentApi);

  bool _isCurrentStream(
    int generation,
    ServerGateway currentApi,
    LiveEventChannel stream,
  ) => _isCurrent(generation, currentApi) && identical(_events, stream);

  bool _isCurrentGlobalStream(
    int generation,
    ServerGateway currentApi,
    LiveEventChannel stream,
  ) => _isCurrent(generation, currentApi) && identical(_globalEvents, stream);

  bool _isCurrentSessionsRefresh(
    int generation,
    ServerGateway currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _sessionsRefreshGeneration;

  bool _isCurrentCatalogRefresh(
    int generation,
    ServerGateway currentApi,
    int refreshGeneration,
  ) =>
      _isCurrent(generation, currentApi) &&
      refreshGeneration == _catalogRefreshGeneration;

  bool _isCurrentQuestionsRefresh(
    int generation,
    ServerGateway? currentApi,
    ServerOperationsGateway currentRepository,
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
    final oldGlobalEvents = _globalEvents;
    _globalEvents = null;
    unawaited(oldGlobalEvents?.dispose());
    _poll?.cancel();
    _poll = null;
    _busyStatusRefresh = null;
    final oldApi = api;
    api = null;
    repository = null;
    oldApi?.close();
  }

  void _clearLocationData() {
    _dismissAllCodingAlerts(clearActive: true);
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
    _dismissAllCodingAlerts(clearActive: true);
    _generation += 1;
    connectionRevision = _generation;
    _retireTransport();
    backgroundLive.removeListener(_backgroundLiveChanged);
    backgroundLive.dispose();
    if (_ownsDiagnostics) diagnostics.dispose();
    appearance.dispose();
    themePack.dispose();
    unawaited(_eventBus.close());
    super.dispose();
  }
}

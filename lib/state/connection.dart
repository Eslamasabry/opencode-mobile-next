import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import '../codex/gateway.dart';
import '../codex/transport.dart' show CodexFailure, CodexFailureKind;
import '../api/opencode_api.dart';
import '../api2/models.dart' show Api2Delivery, Api2FormInfo, Api2InboxItem;
import '../api/product_repository.dart';
import '../api/server_probe.dart';
import '../termux/managed_server_recovery.dart';
import 'profile_monitor.dart';
import 'provider_quota_monitor.dart';
import '../quota/provider_quota_client.dart';
import '../api/sse.dart';
import '../api2/client.dart';
import '../api2/gateway.dart';
import '../api2/gateway_operations.dart';
import '../api2/transport.dart' show Api2AuthRequired;
import '../background/live_background.dart';
import '../background/widget_snapshot.dart';
import '../diagnostics/app_diagnostics.dart';
import '../termux/bridge.dart';
import 'isolated_task_launch.dart';
import 'model_library.dart';
import 'offline_queue.dart';
import 'profiles.dart';
import 'pending_auth.dart';
import 'session_drafts.dart';
import 'draft_attachments.dart';
import 'prompt_photos.dart';
import 'session_pins.dart';
import 'prompt_shelf.dart';
import 'session_read_state.dart';
import 'return_brief_state.dart';
import '../domain/return_brief.dart';
import '../domain/workspace_paths.dart';

Map<String, dynamic> _catalogMap(Object? value) =>
    value is Map ? Map<String, dynamic>.from(value) : const {};

/// An in-memory reservation becomes uncertain as soon as start is dispatched.
/// Never retain command text, launch URLs, status messages, or raw errors here.
class _IntegrationCommandAttempt {
  final String methodID;
  final int deletionRevision;
  String? attemptID;
  Future<({IntegrationAuthLaunch launch, void Function() check})>? starting;

  _IntegrationCommandAttempt(this.methodID, this.deletionRevision);
}

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

/// The logical request reviewed by a user, independent of transport recovery.
/// A refresh may recreate equivalent model objects, so compare their contents.
class PendingRequestIdentity {
  PendingRequestIdentity._(
    this._owner,
    this._location,
    this._permission,
    this._id,
    this._contents,
  );

  final ConnectionController _owner;
  final int _location;
  final bool _permission;
  final String _id;
  final String _contents;
  bool _retired = false;
}

Object? _canonicalRequestValue(Object? value) {
  if (value is Map) {
    final keys = value.keys.map((key) => key.toString()).toList()..sort();
    return {for (final key in keys) key: _canonicalRequestValue(value[key])};
  }
  if (value is Iterable) return value.map(_canonicalRequestValue).toList();
  return value;
}

String _permissionContents(PermissionRequest value) => jsonEncode(
  _canonicalRequestValue({
    'session': value.sessionID,
    'permission': value.permission,
    'patterns': value.patterns,
    'always': value.always,
    'metadata': value.metadata,
    'message': value.message,
    'tool': [value.tool?.messageID, value.tool?.callID],
  }),
);

String _questionContents(PendingQuestion value) => jsonEncode([
  value.sessionID,
  for (final prompt in value.prompts)
    [
      prompt.title,
      prompt.question,
      prompt.multiple,
      prompt.custom,
      for (final choice in prompt.choices) [choice.label, choice.description],
    ],
]);
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

/// A review belongs to one connection, session, and observed staged boundary.
/// Reusing it after a remote stage/clear/commit is deliberately rejected.
class SessionRevertReview {
  final String sessionID;
  final Object scope;
  final int revision;
  final SessionRevert? revert;
  const SessionRevertReview(
    this.sessionID,
    this.scope,
    this.revision,
    this.revert,
  );
}

/// Everything the UI needs about the active server connection.
class ConnectionController extends ChangeNotifier {
  final ProfileStore store;
  final BackgroundLiveController backgroundLive;
  ProfileMonitor? _profileMonitor;
  ProviderQuotaMonitor? _quotaMonitor;
  ProviderQuotaMonitor get quotaMonitor =>
      _quotaMonitor ??= ProviderQuotaMonitor(
        store: store,
        createGateway: (profile, provider) =>
            HttpProviderQuotaGateway(profile, provider: provider),
        isReadable: (id) => !isIsolated && isProfileReadable(id),
        networkWifi: backgroundLive.monitorWifiAvailable,
        dismiss: backgroundLive.dismissCodingAlert,
        alert: ({required profileID, required key, required token}) =>
            backgroundLive.showCodingAlert(
              kind: CodingAlertKind.quota,
              sessionID: 'quota',
              profileID: profileID,
              key: key,
              monitorToken: token,
              allowActions: false,
            ),
      )..addListener(_quotaMonitorChanged);
  void _quotaMonitorChanged() {
    if (!_disposed) super.notifyListeners();
  }

  final MonitorGatewayFactory? _monitorGatewayFactory;
  ProfileMonitor get profileMonitor => _profileMonitor ??= ProfileMonitor(
    store: store,
    createGateway: _monitorGatewayFactory ?? _buildTransportPair,
    isReadable: (id) => !isIsolated && isProfileReadable(id),
    networkWifi: backgroundLive.monitorWifiAvailable,
    dismiss: backgroundLive.dismissCodingAlert,
    alertsAllowed: (id) => id != profile?.id,
    alert: (id, request, key, token) => backgroundLive.showCodingAlert(
      kind: switch (request.kind) {
        MonitoredRequestKind.permission => CodingAlertKind.permission,
        MonitoredRequestKind.question ||
        MonitoredRequestKind.form => CodingAlertKind.question,
        MonitoredRequestKind.checkIn => CodingAlertKind.checkIn,
      },
      profileID: id,
      sessionID: request.sessionID,
      key: key,
      allowActions: false,
      monitorToken: token,
    ),
  )..addListener(_monitorChanged);
  void _monitorChanged() {
    if (_disposed) return;
    if (_lifecycleWasBackgrounded && !_canShowCodingAlert) {
      _dismissAllCodingAlerts();
    }
    // Monitor observations do not change the active session/widget snapshot.
    super.notifyListeners();
  }

  void _syncProfileServices() {
    if (isIsolated || _disposed) return;
    ManagedServerRecovery.syncProfiles(
      store.prefs,
      store.profiles
          .where(
            (p) =>
                TermuxBridge.supported &&
                TermuxBridge.managesServerUrl(p.baseUrl),
          )
          .map((p) => p.id),
    );
  }

  /// A monitor row is an observation, never authorization to reuse cached
  /// request content or another profile's active transport.
  Future<bool> prepareMonitoredRequest(MonitoredRoute target) async {
    if (isIsolated ||
        _disposed ||
        !isProfileReadable(target.profileID) ||
        !profileMonitor.rulesFor(target.profileID).enabled ||
        (target.createdAt.isAfter(DateTime.now()) ||
            DateTime.now().difference(target.createdAt) >
                const Duration(days: 1))) {
      return false;
    }
    final targetProfile = store.profiles
        .where((p) => p.id == target.profileID)
        .firstOrNull;
    if (targetProfile == null ||
        targetProfile.baseUrl != target.serverUrl ||
        target.sourceIdentity !=
            ProfileMonitor.routeSourceIdentity(targetProfile) ||
        targetProfile.requiresPasswordReentry ||
        !profileMonitor.supportsProfile(targetProfile)) {
      return false;
    }
    final before = (connectionRevision, locationRevision, profile?.id);
    final address = (
      targetProfile.baseUrl,
      targetProfile.username,
      targetProfile.password,
      targetProfile.flavor,
    );
    final pair = (_monitorGatewayFactory ?? _buildTransportPair)(targetProfile);
    try {
      pair.gateway.setLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      pair.operations.setLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      final found = switch (target.kind) {
        MonitoredRequestKind.permission =>
          (await ProfileMonitor.readPermissions(
            pair.gateway,
            const Duration(seconds: 8),
          )).any(
            (p) => p.id == target.requestID && p.sessionID == target.sessionID,
          ),
        MonitoredRequestKind.question =>
          (await ProfileMonitor.readQuestions(
            pair.gateway,
            pair.operations,
            const Duration(seconds: 8),
          )).any(
            (p) => p.id == target.requestID && p.sessionID == target.sessionID,
          ),
        MonitoredRequestKind.form =>
          pair.gateway.capabilities.forms &&
              (await pair.gateway.pendingForms().timeout(
                const Duration(seconds: 8),
              )).any(
                (p) =>
                    p.id == target.requestID && p.sessionID == target.sessionID,
              ),
        // A check-in has no server-side request to find: the session read
        // below is the whole check, and it opens whether or not the run is
        // still going — the user asked to look at it either way.
        MonitoredRequestKind.checkIn => true,
      };
      if (!found) return false;
      if (target.sessionID != 'global') {
        final session = await pair.gateway
            .session(target.sessionID)
            .timeout(const Duration(seconds: 8));
        if (session.id != target.sessionID ||
            (session.directory != null &&
                session.directory != target.directory) ||
            session.workspaceID != target.workspace) {
          return false;
        }
      }
    } catch (_) {
      return false;
    } finally {
      pair.gateway.close();
    }
    if (_disposed ||
        before != (connectionRevision, locationRevision, profile?.id) ||
        !isProfileReadable(target.profileID) ||
        !store.profiles.any(
          (p) =>
              p.id == target.profileID &&
              (p.baseUrl, p.username, p.password, p.flavor) == address,
        )) {
      return false;
    }
    if (profile?.id != target.profileID || !isConnected) {
      final connecting = connect(targetProfile);
      final expected = connectionRevision;
      await connecting;
      if (connectionRevision != expected) return false;
    }
    if (_disposed ||
        profile?.id != target.profileID ||
        !isConnected ||
        !isProfileReadable(target.profileID)) {
      return false;
    }
    var generation = connectionRevision;
    if (directory != target.directory || workspace != target.workspace) {
      final selecting = selectLocation(
        directory: target.directory,
        workspace: target.workspace,
      );
      generation = connectionRevision;
      await selecting;
    }
    if (_disposed ||
        connectionRevision != generation ||
        profile?.id != target.profileID ||
        directory != target.directory ||
        workspace != target.workspace ||
        locationLoading) {
      return false;
    }
    final location = locationRevision;
    await Future.wait([
      refreshPendingPermissions(),
      refreshPendingQuestions(),
      refreshPendingForms(),
    ]);
    return !_disposed &&
        generation == connectionRevision &&
        location == locationRevision &&
        profile?.id == target.profileID &&
        isProfileReadable(target.profileID);
  }

  int get unknownAttentionProfileCount => store.profiles
      .where(
        (p) =>
            isProfileReadable(p.id) &&
            !(p.id == profile?.id && isConnected) &&
            !profileMonitor.snapshotFor(p.id).isCurrent,
      )
      .length;

  int get unifiedAttentionCount {
    final selected = profile?.id;
    return permissions.length +
        questions.length +
        forms.length +
        store.profiles
            .where((p) => p.id != selected && isProfileReadable(p.id))
            .fold<int>(
              0,
              (sum, p) =>
                  sum + (profileMonitor.snapshotFor(p.id).pendingCount ?? 0),
            );
  }

  final WidgetSessionSnapshot _widgetSnapshot;

  /// The most recent home-screen widget write started by [notifyListeners].
  Future<void>? _pendingWidgetSnapshotWrite;

  /// Set while [deleteProfileAndLocalData] runs, so a notification cannot
  /// republish the sessions of the profile being erased.
  bool _widgetSnapshotSuspended = false;
  final AppDiagnosticsController diagnostics;
  final bool _ownsDiagnostics;
  late final ValueNotifier<AppAppearance> appearance;
  late final ValueNotifier<ThemePackId> themePack;
  final OpenCodeApiFactory _apiFactory;
  final ProductRepositoryFactory _repositoryFactory;
  final V2GatewayPairFactory _v2GatewayFactory;
  final V2GatewayPairFactory _codexGatewayFactory;
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
  final Map<String, int> _sessionStatusRevisions = {};
  final Map<String, Future<void>> _selectionMutations = {};
  final Map<String, Object> _revertMutations = {};
  final Map<String, int> _historyRevisions = {};
  final Map<String, String> sessionRevertErrors = {};
  final Map<String, String> sessionSelectionErrors = {};

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
  Future<void> _queueChanges = Future<void>.value();
  OfflineQueueStore? _offlineQueueStore;
  bool _flushingOfflineQueue = false;

  /// The queued prompt a flush is working on: from the moment its dispatch
  /// marker write is queued until its acceptance or failure has been
  /// persisted. Removal and resend refuse this id inside the queue's
  /// serialization, so a confirmation sheet that was opened before the
  /// flush started cannot act on a prompt that is now on the wire.
  String? _queuedPromptInFlight;

  /// Queued prompts the server accepted but whose removal the store refused.
  /// In memory only: after a restart the marker alone remains, which reads
  /// as an unconfirmed send — the honest statement once this is lost.
  final _queuedPromptsAcceptedUnrecorded = <String>{};

  /// Composer text typed in a chat but never sent, kept per session so
  /// navigating between sessions loses nothing. Loaded lazily from
  /// [SessionDraftStore] and kept in memory afterward.
  Map<String, SessionDraft>? _sessionDrafts;
  SessionDraftStore? _sessionDraftStore;
  Future<void> _draftChanges = Future.value();
  final DraftAttachmentVault _draftAttachmentVault;
  final PromptPhotoStore? _promptPhotoStore;
  late final PromptPhotoStore promptPhotos =
      _promptPhotoStore ?? PromptPhotoStore(store.prefs);
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

  /// Providers the server reports as connected (a credential exists) but has
  /// not loaded into its model runtime. OpenCode 1 caches provider state per
  /// instance, so a sign-in that lands after startup leaves the provider in
  /// this limbo: `/provider` lists it with the full models.dev catalog while
  /// every prompt fails with "Model not found". [_loadCatalog] heals this
  /// once per connection by disposing the instance; anything still listed
  /// here after that needs a manual [reloadProviderRuntime].
  Set<String> unloadedProviderIDs = const {};
  String? _runtimeHealKey;
  int _runtimeHealGeneration = -1;

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
  bool transcriptReasoningExpanded = false;
  bool transcriptTimestampsVisible = false;

  Map<String, Session> sessionsById = {};
  String? _sessionsCursor;
  bool get hasMoreSessions => _sessionsCursor != null;
  bool sessionsLoadingMore = false;
  String? sessionsMoreError;
  bool sessionsNeedReload = false;
  final Set<String> _sessionPageIDs = {};
  final Set<String> _sessionInventoryIDs = {};
  bool _sessionInventoryInitialized = false;
  final Set<String> _usedSessionCursors = {};
  int _sessionSnapshotRevision = 0;
  final Map<(ServerGateway, int, String, int), Future<void>> _sessionReads = {};
  final Map<String, String> sessionDetailsErrors = {};
  final Set<String> _deletedSessionIDs = {};
  Set<String> busySessions = {};

  /// Assistant message ids whose completion (or error) this phone received
  /// as a live `message.updated` event on the current connection. Bound to
  /// the exact message record, never to a session-level idle timestamp, so a
  /// run-results view can say "observed live" only for that step. In-memory,
  /// bounded, and cleared with the rest of the connection state.
  final Set<String> observedCompletedMessageIDs = {};
  static const _maxObservedCompletedMessages = 512;

  void _noteObservedCompletion(String messageID) {
    if (messageID.isEmpty) return;
    if (observedCompletedMessageIDs.length >= _maxObservedCompletedMessages) {
      observedCompletedMessageIDs.remove(observedCompletedMessageIDs.first);
    }
    observedCompletedMessageIDs.add(messageID);
  }

  /// Sessions currently in provider-retry backoff, keyed by session ID.
  /// Populated from `session.status` `{type: 'retry'}` (v1 and v2), the v2
  /// `session.retry.scheduled` event, and the v1 status endpoint on refresh;
  /// an entry is removed as soon as the session reports busy or idle. Retry
  /// sessions remain in [busySessions] as before.
  Map<String, SessionRetryState> retryStates = {};

  /// Outstanding permission asks keyed by request ID.
  Map<String, PermissionRequest> permissions = {};
  Map<String, PendingQuestion> questions = {};

  /// Outstanding OpenCode 2 form requests keyed by form ID. Includes global
  /// (MCP elicitation) forms whose `sessionID` is the `"global"` sentinel.
  /// Always empty on v1 (capability `forms` is false).
  Map<String, Api2FormInfo> forms = {};
  bool formsLoading = false;
  String? formsError;

  /// Pending OpenCode 2 inbox items (admitted, not-yet-delivered sends) per
  /// session, keyed by inbox ID. Feeds the pending-sends strip; empty on v1.
  final Map<String, Map<String, Api2InboxItem>> _inboxBySession = {};

  /// Bumps whenever the inbox slice of any session changes.
  int inboxRevision = 0;
  int ptyRevision = 0;
  EventEnvelope? lastPtyEvent;
  final Set<String> _resolvedPermissionIDs = {};
  final Map<String, ({String sessionID, String permissionID})>
  _legacyPermissionIdentities = {};
  final Map<String, String> _v2PermissionSessions = {};
  final Map<String, String> _v2QuestionSessions = {};
  final Set<String> _resolvedQuestionIDs = {};
  final Set<String> _resolvedFormIDs = {};
  int _formRevision = 0;
  int _formRefreshGeneration = 0;
  final Set<String> _attentionActiveSessions = {};
  final Map<String, ({CodingAlertKind kind, String requestID})>
  _alertedInputKinds = {};
  final Set<String> _alertedStatusSessions = {};

  /// Generic sentence for the tool each busy session is running right now,
  /// gleaned from `message.part.updated` on the way past. Feeds the ongoing
  /// Android notification only; pruned lazily against [busySessions].
  final Map<String, String> _runningToolDetail = {};
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

  /// An in-memory product walkthrough with no native publishing or reconnection.
  /// Its caller supplies a separate in-memory store and gateway pair.
  final bool isIsolated;

  factory ConnectionController.isolated(
    ProfileStore store, {
    required ServerGateway gateway,
    required ServerOperationsGateway operations,
    required ServerProfile profile,
    DraftAttachmentVault? draftAttachmentVault,
    DraftAttachmentVault? stashAttachmentVault,
    PromptPhotoStore? promptPhotoStore,
  }) {
    final controller = ConnectionController(
      store,
      isIsolated: true,
      draftAttachmentVault: draftAttachmentVault,
      stashAttachmentVault: stashAttachmentVault,
      promptPhotoStore: promptPhotoStore,
      backgroundLive: BackgroundLiveController(
        preferences: store.prefs,
        invoke: (method, [arguments]) async => const {},
      ),
    );
    controller._connectedProfile = profile;
    controller.api = gateway;
    controller.repository = operations;
    controller.status = StreamStatus.connected;
    controller._startEvents(controller._generation, gateway);
    return controller;
  }

  ConnectionController(
    this.store, {
    this.isIsolated = false,
    OpenCodeApiFactory? apiFactory,
    MonitorGatewayFactory? monitorGatewayFactory,
    ProductRepositoryFactory? repositoryFactory,
    V2GatewayPairFactory? v2GatewayFactory,
    V2GatewayPairFactory? codexGatewayFactory,
    EventStreamFactory? eventStreamFactory,
    EventStreamFactory? globalEventStreamFactory,
    BackgroundLiveController? backgroundLive,
    AppDiagnosticsController? diagnostics,
    LocalWakeLockEnsurer? localWakeLockEnsurer,
    DraftAttachmentVault? draftAttachmentVault,
    DraftAttachmentVault? stashAttachmentVault,
    PromptPhotoStore? promptPhotoStore,
  }) : _monitorGatewayFactory = monitorGatewayFactory,
       _promptPhotoStore = promptPhotoStore,
       _draftAttachmentVault = draftAttachmentVault ?? DraftAttachmentVault(),
       _promptShelf = PromptShelfStore.withAttachmentFiles(
         store.prefs,
         vault: stashAttachmentVault,
       ),
       _apiFactory = apiFactory ?? _createApi,
       _repositoryFactory = repositoryFactory ?? _createRepository,
       _v2GatewayFactory = v2GatewayFactory ?? _createV2GatewayPair,
       _codexGatewayFactory = codexGatewayFactory ?? _createCodexGatewayPair,
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
    if (!isIsolated) {
      this.backgroundLive.bindActionHandler(_handleCodingAlertAction);
      _syncProfileServices();
      profileMonitor.start();
      quotaMonitor.start();
    }
  }

  /// Resolves an Android notification action while the app stays
  /// backgrounded. Alerts exist only while live mode keeps the transport
  /// alive, so replies go through that live transport directly; running the
  /// foreground wake path here would count as an app resume and clear every
  /// alert. Returns false so Android re-posts the alert when the reply cannot
  /// be delivered.
  Future<bool> _handleCodingAlertAction(CodingAlertAction action) async {
    if (action.profileID != (profile?.id ?? '')) return false;
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
          if (action.kind == CodingAlertKind.permission) {
            // On OpenCode 2 permissions, Reply maps to reject-with-message
            // (the message is shown to the model — steering by rejection).
            // RequestID binding rules stay exactly as for allow/deny: the
            // reply resolves only the exact request this notification
            // represented, otherwise the alert refreshes.
            final permission = permissions[action.requestID];
            if (permission == null ||
                permission.sessionID != action.sessionID ||
                !_v2PermissionSessions.containsKey(action.requestID)) {
              _syncInputAlerts();
              return true;
            }
            if (currentApi == null) return false;
            await _sendPermissionReply(
              currentApi,
              permission.id,
              'reject',
              message: text,
            );
            return true;
          }
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

  bool get keepLiveInBackground => !isIsolated && backgroundLive.enabled;

  Future<bool> setKeepLiveInBackground(bool enabled) =>
      isIsolated ? Future.value(false) : backgroundLive.setEnabled(enabled);

  CodingAlertOpen? get pendingCodingAlertOpen => _pendingCodingAlertOpen;

  CodingAlertOpen? takePendingCodingAlertOpen() {
    final value = _pendingCodingAlertOpen;
    _pendingCodingAlertOpen = null;
    return value;
  }

  Future<void> restoreBackgroundLiveMode() async {
    if (isIsolated) return;
    await backgroundLive.restore();
    await consumeCodingAlertOpen();
  }

  Future<void> consumeCodingAlertOpen() async {
    if (isIsolated) return;
    final value = await backgroundLive.consumeCodingAlertOpen();
    if (_disposed || value == null) return;
    // Home-screen widget rows outlive profile switches: a tap stamped with
    // another profile's ID opens the app normally rather than silently
    // routing into (or switching to) that profile's chat. Notification taps
    // carry no profile ID and keep routing as before.
    if (value.monitorToken.isEmpty &&
        value.profileID.isNotEmpty &&
        value.profileID != store.activeId) {
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
    _quotaMonitor?.setRuntime(
      foreground: !_lifecycleWasBackgrounded,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _profileMonitor?.setRuntime(
      foreground: !_lifecycleWasBackgrounded,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    if (keepLiveInBackground) {
      unawaited(_ensureLocalServerWakeLock());
    } else {
      _dismissAllCodingAlerts(clearActive: true);
    }
    if (!_disposed) notifyListeners();
  }

  String _inputAlertKey(String sessionID) =>
      profile == null ? 'input:$sessionID' : 'input:${profile!.id}:$sessionID';
  String _statusAlertKey(String sessionID) => profile == null
      ? 'status:$sessionID'
      : 'status:${profile!.id}:$sessionID';

  bool get _canShowCodingAlert =>
      keepLiveInBackground &&
      _lifecycleWasBackgrounded &&
      backgroundLive.notificationGranted &&
      profileMonitor.rulesFor(profile?.id ?? '').notifications &&
      !profileMonitor.rulesFor(profile?.id ?? '').quietAt(DateTime.now());

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
    if (kind == CodingAlertKind.complete &&
        profileMonitor.rulesFor(profile?.id ?? '').enabled) {
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
            profileID: profile?.id ?? '',
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
    // Forms deliberately never quick-reply (multi-field forms cannot be
    // answered from a RemoteInput); their alert deep-links into the app.
    final requestID = kind == CodingAlertKind.permission
        ? permissionForSession(sessionID)?.id
        : (quickReplyQuestion ?? questionForSession(sessionID))?.id ??
              formForSession(sessionID)?.id;
    if (requestID == null || requestID.isEmpty) return;
    // v2 permission alerts carry the RemoteInput Reply action: its text
    // maps to reject-with-message (see _handleCodingAlertAction).
    final permissionReply =
        kind == CodingAlertKind.permission &&
        _v2PermissionSessions.containsKey(requestID);
    final alerted = (kind: kind, requestID: requestID);
    if (_alertedInputKinds[sessionID] == alerted) return;
    _alertedInputKinds[sessionID] = alerted;
    unawaited(
      backgroundLive
          .showCodingAlert(
            kind: kind,
            profileID: profile?.id ?? '',
            sessionID: sessionID,
            key: _inputAlertKey(sessionID),
            quickReply: quickReplyQuestion != null || permissionReply,
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
      // Pending forms alert like questions (kind `question`, no quick
      // reply); global forms have no session to alert on.
      for (final form in forms.values)
        if (form.sessionID != 'global') form.sessionID,
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
    if (profile == null ||
        profile.backend == ServerBackend.codex ||
        !_isLoopbackUrl(profile.baseUrl)) {
      return;
    }
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
    // One shared predicate, so this cannot drift from what the URL
    // normalizer and the profile validator consider local.
    return uri != null && isLoopbackHost(uri.host);
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

  static ({ServerGateway gateway, ServerOperationsGateway operations})
  _createCodexGatewayPair(ServerProfile profile) {
    final gateway = CodexGateway.connect(
      baseUrl: profile.baseUrl,
      token: profile.codexToken,
      directory: profile.codexDirectory,
    );
    return (gateway: gateway, operations: gateway);
  }

  /// Constructs the transport pair for [profile]'s cached flavor. The two
  /// v1 factories stay the injected test seams; v2 goes through
  /// [_v2GatewayFactory].
  ({ServerGateway gateway, ServerOperationsGateway operations})
  _buildTransportPair(ServerProfile profile) {
    if (isIsolated) {
      throw StateError(
        'An isolated session cannot create a network transport.',
      );
    }
    if (profile.backend == ServerBackend.codex) {
      return _codexGatewayFactory(profile);
    }
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

  final _profileDataChanges = ChangeNotifier();

  /// Notifies read-only, profile-scoped companions before local deletion or
  /// controller disposal can race their in-flight requests. Unlike the main
  /// notifier this does not publish widget snapshots or connection state.
  Listenable get profileDataChanges => _profileDataChanges;

  bool isProfileReadable(String id) =>
      !_disposed && id.isNotEmpty && _readProfileAvailable(id);

  ServerProfile? get profile {
    final id = store.activeId;
    if (id == null) return null;
    for (final p in store.profiles) {
      if (p.id == id) return p;
    }
    return null;
  }

  /// The protocol the live transport speaks. Reads the profile the connection
  /// actually opened, not the selected one, so a switch mid-connection cannot
  /// make screens describe the wrong server.
  ServerFlavor get serverFlavor =>
      _connectedProfile?.flavor ?? profile?.flavor ?? ServerFlavor.v1;

  /// Feature switches for the live transport. Screens gate on these — never on
  /// [serverFlavor], which is only ever copy ("OpenCode 2 servers"). Before a
  /// connection exists a Codex profile retains its restricted capability set;
  /// attaching the live gateway supplies the authoritative set.
  ServerCapabilities get capabilities =>
      api?.capabilities ??
      ((_connectedProfile ?? profile)?.backend == ServerBackend.codex
          ? codexServerCapabilities
          : ServerCapabilities.allV1);

  bool get usesConnectionToken =>
      (_connectedProfile ?? profile)?.backend == ServerBackend.codex;

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

  /// Trailing separators dropped, case kept, so a location saved as
  /// `/work/acme/` still matches the `/work/acme` the server reports.
  static String normalizeDirectoryPath(String path) {
    var value = path.trim();
    while (value.length > 1 && (value.endsWith('/') || value.endsWith('\\'))) {
      final trimmed = value.substring(0, value.length - 1);
      if (trimmed.endsWith(':')) break; // Keep a Windows drive root intact.
      value = trimmed;
    }
    return value;
  }

  static bool sameDirectoryPath(String? a, String? b) {
    if (a == null || b == null) return a == b;
    return normalizeDirectoryPath(a) == normalizeDirectoryPath(b);
  }

  /// True when [directory] is the project root, one of its worktrees, or a
  /// folder inside either. The server's catch-all `/` project covers nothing.
  static bool projectContainsDirectory(
    WorkspaceProject project,
    String directory,
  ) {
    final target = normalizeDirectoryPath(directory);
    if (target.isEmpty) return false;
    bool covers(String root) {
      final base = normalizeDirectoryPath(root);
      if (base.isEmpty || base == '/') return base == target;
      if (base == target) return true;
      return target.startsWith('$base/') || target.startsWith('$base\\');
    }

    return covers(project.directory) || project.worktrees.any(covers);
  }

  /// The most recently updated real project, or null when the server only
  /// knows its catch-all root.
  static WorkspaceProject? newestProject(Iterable<WorkspaceProject> projects) {
    WorkspaceProject? best;
    for (final project in projects) {
      final directory = normalizeDirectoryPath(project.directory);
      if (isProtectedWorkspaceDirectory(directory)) continue;
      if (best == null || project.updatedAt > best.updatedAt) best = project;
    }
    return best;
  }

  static bool _isCatchAllProject(WorkspaceProject project) {
    final directory = normalizeDirectoryPath(project.directory);
    return isProtectedWorkspaceDirectory(directory) || project.id == 'global';
  }

  /// True while an OpenCode connection has no usable project folder: nothing
  /// was chosen yet, or the choice resolves to a home folder or filesystem
  /// root. Workspace blocks session creation until the user creates or opens
  /// a real project folder; the server's own working directory is never used
  /// as a workspace (see `workspace_paths.dart`).
  bool get workspaceChoiceRequired {
    if (_connectedProfile?.backend == ServerBackend.codex) return false;
    if (!capabilities.projectManagement) return false;
    return isProtectedWorkspaceDirectory(directory);
  }

  static const workspaceChoiceNotice =
      'OpenCode Mobile no longer works in the server\'s home folder. '
      'Create a new folder or open a project folder to continue.';

  /// Set when the saved directory was restored without the project list
  /// confirming it; [revalidateRestoredLocation] clears it once the list
  /// loads.
  bool _pendingLocationRevalidation = false;

  @visibleForTesting
  bool get pendingLocationRevalidation => _pendingLocationRevalidation;

  Future<void> _forgetSavedLocation(ServerProfile profile) async {
    try {
      await store.clearLocation(profile.id);
    } catch (_) {
      // The current connection can still recover to its server root.
    }
  }

  String _replacementNotice(String lost, WorkspaceProject replacement) =>
      'Opened ${replacement.name}. The last project ($lost) is no longer on '
      'the server.';

  /// Clears the one-line location notice once the user has read it.
  void dismissLocationNotice() {
    if (locationNotice == null) return;
    locationNotice = null;
    notifyListeners();
  }

  /// Resolves the location to restore for [profile]. The saved directory is
  /// kept whenever the server confirms it or cannot yet say; it is replaced
  /// by the newest project only when the project list proves it gone, and
  /// dropped only when the server has no projects at all.
  Future<ProfileLocation?> _validatedSavedLocation(
    ServerProfile profile,
    ServerOperationsGateway currentRepository,
    int generation,
    ServerGateway currentApi,
  ) async {
    final saved = store.locationFor(profile.id);
    if (saved == null) return null;
    final savedDirectory = saved.directory;
    var directory = savedDirectory == null
        ? null
        : normalizeDirectoryPath(savedDirectory);
    if (directory != null && directory.isEmpty) directory = null;
    if (directory != null && isProtectedWorkspaceDirectory(directory)) {
      // A location saved by an older build that still allowed the server's
      // home folder. It is forgotten rather than restored, and Workspace asks
      // for a project folder instead.
      await _forgetSavedLocation(profile);
      locationNotice = workspaceChoiceNotice;
      return null;
    }
    var workspace = saved.workspace;
    _pendingLocationRevalidation = false;
    try {
      if (directory != null) {
        currentRepository.setLocation(directory: directory, workspace: null);
        WorkspaceProject? project;
        try {
          project = await currentRepository.loadCurrentProject();
        } catch (_) {
          // Verified against the project list below.
        }
        if (!_isCurrent(generation, currentApi)) return null;
        final confirmed =
            project != null &&
            (projectContainsDirectory(project, directory) ||
                !_isCatchAllProject(project));
        if (!confirmed) {
          currentRepository.setLocation(directory: null, workspace: null);
          List<WorkspaceProject>? projects;
          try {
            projects = await currentRepository.listProjects();
          } catch (_) {
            projects = null;
          }
          if (!_isCurrent(generation, currentApi)) return null;
          if (projects == null) {
            // The list is not available yet: keep the saved directory and
            // re-check it once the list loads.
            _pendingLocationRevalidation = true;
          } else {
            final target = directory;
            final present = projects.any(
              (candidate) => projectContainsDirectory(candidate, target),
            );
            if (!present) {
              final replacement = newestProject(projects);
              if (replacement == null) {
                await _forgetSavedLocation(profile);
                locationNotice =
                    'The last project is no longer available. '
                    'OpenCode Mobile returned to the server workspace.';
                return null;
              }
              locationNotice = _replacementNotice(target, replacement);
              directory = normalizeDirectoryPath(replacement.directory);
              workspace = null;
            }
          }
        }
      }
      if (workspace != null) {
        currentRepository.setLocation(directory: directory, workspace: null);
        List<WorkspaceInfo>? workspaces;
        try {
          workspaces = await currentRepository.listWorkspaces();
        } catch (_) {
          workspaces = null;
        }
        if (!_isCurrent(generation, currentApi)) return null;
        if (workspaces == null) {
          workspace = null;
          locationNotice =
              'The last remote workspace could not be verified. '
              'The project was opened locally.';
        } else if (!workspaces.any((candidate) => candidate.id == workspace)) {
          workspace = null;
          locationNotice =
              'The last remote workspace is no longer available. '
              'The project was opened locally.';
        }
      }
      return ProfileLocation(directory: directory, workspace: workspace);
    } catch (_) {
      if (!_isCurrent(generation, currentApi)) return null;
      if (directory == null) {
        locationNotice =
            'The last project could not be verified. '
            'OpenCode Mobile opened the server workspace instead.';
        return null;
      }
      _pendingLocationRevalidation = true;
      locationNotice =
          'The last project could not be verified. '
          'OpenCode Mobile opened it anyway and will check again once the '
          'project list loads.';
      return ProfileLocation(directory: directory, workspace: null);
    } finally {
      currentRepository.setLocation(directory: null, workspace: null);
    }
  }

  /// Re-checks a directory restored while the project list was unavailable.
  /// When the list now loads without it, the newest project is opened and a
  /// plain-sentence [locationNotice] explains the switch.
  Future<void> revalidateRestoredLocation() async {
    if (!_pendingLocationRevalidation) return;
    final currentRepository = repository;
    final currentApi = api;
    final directory = this.directory;
    if (currentRepository == null || currentApi == null || directory == null) {
      _pendingLocationRevalidation = false;
      return;
    }
    final generation = _generation;
    List<WorkspaceProject> projects;
    try {
      projects = await currentRepository.listProjects();
    } catch (_) {
      return; // Still pending: try again on the next location refresh.
    }
    if (!_isCurrent(generation, currentApi) || this.directory != directory) {
      return;
    }
    _pendingLocationRevalidation = false;
    if (projects.any(
      (candidate) => projectContainsDirectory(candidate, directory),
    )) {
      return;
    }
    final replacement = newestProject(projects);
    if (replacement == null) return;
    locationNotice = _replacementNotice(directory, replacement);
    await _selectLocation(
      directory: normalizeDirectoryPath(replacement.directory),
      workspace: null,
      preserveNotice: true,
    );
  }

  /// [redetectOnFailure] lets one failed connect re-probe the address and
  /// correct a stale cached [ServerProfile.flavor] (a server swapped between
  /// `opencode serve` generations) before giving up; the corrected retry runs
  /// with it false so detection can never loop.
  Future<void> connect(
    ServerProfile profile, {
    bool redetectOnFailure = true,
  }) async {
    if (isIsolated) {
      throw StateError('An isolated session cannot connect to a server.');
    }
    _syncProfileServices();
    _lifecycleSuspended = false;
    _lifecycleWasBackgrounded = false;
    _lifecycleResume = null;
    final isCodex = profile.backend == ServerBackend.codex;
    final validationError = isCodex
        ? (profile.requiresCodexTokenReentry
              ? 'The saved connection token is unavailable. Enter it again.'
              : validateCodexServerUrl(profile.baseUrl) ??
                    validateCodexConnectionToken(profile.codexToken) ??
                    validateCodexProjectDirectory(profile.codexDirectory))
        : validateServerProfileUrl(
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
    // The folder edited in a Codex connection is authoritative on connect.
    // Restoring an older OpenCode-style selection would undo that user edit.
    final initialDirectory = isCodex ? profile.codexDirectory : null;
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: initialDirectory, workspace: null);
    final currentRepository = pair.operations
      ..setLocation(directory: initialDirectory, workspace: null);
    api = currentApi;
    repository = currentRepository;
    _connectedProfile = profile;
    availableServerVersion = null;
    installedServerVersion = null;
    directory = initialDirectory;
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
      _noteAuthFailure(e);
      if (!isCodex && redetectOnFailure && _suggestsWrongFlavor(e)) {
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
        ? ModelRef(providerID: saved.$1!, modelID: saved.$2!).normalized
        : null;
    selectedAgent = store.agentFor(profile.id);
    selectedVariant = store.variantFor(profile.id);
    sessionModels = store.sessionModelsFor(profile.id);
    _modelLibrary = store.modelLibraryFor(profile.id);

    final savedLocation = isCodex
        ? null
        : await _validatedSavedLocation(
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
          ? unloadedProviders(nextProviders, configuredProviders)
          : const <String>{};
      if (unloaded.isNotEmpty && currentRepository != null) {
        // A credential the runtime has not picked up yet (OAuth finished in
        // the TUI, or after this app's own sign-in raced the server). Dispose
        // the instance so OpenCode rebuilds its provider state, then re-read.
        // Heal once per distinct set of providers per connection so a server
        // that cannot load a provider does not loop.
        final healKey = (unloaded.toList()..sort()).join(',');
        if (_runtimeHealGeneration != generation ||
            _runtimeHealKey != healKey) {
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
            unloaded = unloadedProviders(nextProviders, configuredProviders);
          } catch (_) {
            // Leave the providers flagged; the picker offers a manual reload.
          }
        }
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
        // Form events are ephemeral: re-poll the pending list after
        // every (re)connect. No-op on servers without forms.
        unawaited(refreshPendingForms());
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
    if ((error is CodexFailure &&
            error.kind == CodexFailureKind.authentication) ||
        error is Api2AuthRequired ||
        (error is ApiException &&
            error.statusCode == 401 &&
            (_connectedProfile?.flavor == ServerFlavor.v2 ||
                _connectedProfile?.backend == ServerBackend.codex))) {
      passwordRejected = true;
      final rejectedProfile = _connectedProfile;
      if (rejectedProfile?.backend == ServerBackend.codex) {
        rejectedProfile!.requiresCodexTokenReentry = true;
      }
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
    if (profile.backend == ServerBackend.codex) return null;
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
          if (_deletedSessionIDs.contains(s.id) &&
              env.type != 'session.created') {
            break;
          }
          _deletedSessionIDs.remove(s.id);
          _markSessionChanged(s.id);
          sessionsById[s.id] = s;
          _rememberSessionMembership(s);
          notifyListeners();
        }
        break;

      case 'session.metadata.updated':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final id = info['id']?.toString();
          if (id == null || _deletedSessionIDs.contains(id)) break;
          final previous = sessionsById[id];
          var next = previous ?? Session(id: id);
          if (info.containsKey('title')) {
            next = next.copyWith(title: info['title'] as String?);
          }
          if (info.containsKey('directory')) {
            next = next.copyWith(directory: info['directory']);
          }
          if (info.containsKey('workspaceID')) {
            next = next.copyWith(workspaceID: info['workspaceID']);
          }
          if (info.containsKey('projectID')) {
            next = next.copyWith(projectID: info['projectID']);
          }
          if (info.containsKey('path')) {
            next = next.copyWith(path: info['path']);
          }
          _markSessionChanged(id, affectsStatus: false);
          sessionsById[id] = next;
          _rememberSessionMembership(
            next,
            authoritative: info.containsKey('directory'),
          );
          notifyListeners();
          if (previous == null) unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.revert.staged':
      case 'session.revert.cleared':
      case 'session.revert.committed':
        final id = props['sessionID']?.toString();
        if (id == null || id.isEmpty || _deletedSessionIDs.contains(id)) break;
        final staged = SessionRevert.fromJson(props['revert']);
        if (env.type == 'session.revert.staged' && staged == null) break;
        final previous = sessionsById[id];
        _markSessionChanged(id, affectsStatus: false);
        sessionsById[id] = (previous ?? Session(id: id)).copyWith(
          stagedRevert: staged,
        );
        _resetSessionHistory(
          id,
          removedFrom: env.type == 'session.revert.committed'
              ? props['to']?.toString() ?? previous?.stagedRevert?.messageID
              : null,
        );
        if (previous == null || env.type != 'session.revert.staged') {
          unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.instructions.updated':
        final id = props['sessionID']?.toString();
        if (id != null && !_deletedSessionIDs.contains(id)) {
          final key = (locationRevision, id);
          _noteRevisions[key] = (_noteRevisions[key] ?? 0) + 1;
          _noteReceipts.remove(key);
          notifyListeners();
        }
        break;

      case 'session.viewed':
        final id = props['sessionID']?.toString();
        final idle = props['idle'];
        if (id == null ||
            idle is! int ||
            idle < 0 ||
            _deletedSessionIDs.contains(id)) {
          break;
        }
        _applySessionViewed(id, idle);
        break;

      case 'session.model.selected':
      case 'session.agent.selected':
        final id = props['sessionID']?.toString();
        if (id == null || _deletedSessionIDs.contains(id)) break;
        final previous = sessionsById[id] ?? Session(id: id);
        final old =
            previous.selection ??
            const SessionSelection(modelKnown: false, agentKnown: false);
        final parsed = SessionSelection.fromJson(props);
        final next = env.type == 'session.model.selected'
            ? old.withModel(parsed.model, parsed.variant)
            : old.withAgent(parsed.agent);
        _markSessionChanged(id, affectsStatus: false);
        sessionsById[id] = previous.copyWith(
          selection: next,
          model: next.model?.wireName,
          agent: next.agent,
        );
        sessionSelectionErrors.remove(id);
        notifyListeners();
        if (!next.modelKnown || !next.agentKnown) {
          unawaited(_refreshOneSession(id));
        }
        break;

      case 'session.deleted':
        final info = props['info'];
        if (info is Map<String, dynamic>) {
          final id = info['id']?.toString();
          if (id != null && id.isNotEmpty) {
            _removeSession(id);
          }
        }
        break;

      case 'session.usage.updated':
        // v2 live usage: merge into the stored session instead of replacing
        // it, since the event carries only cost + tokens.
        final sid = props['sessionID']?.toString();
        if (sid != null && sid.isNotEmpty) {
          final existing = sessionsById[sid];
          if (existing != null) {
            final cost = props['cost'];
            _markSessionChanged(sid);
            sessionsById[sid] = existing.copyWith(
              cost: cost is num ? cost.toDouble() : null,
              tokens: props['tokens'] is Map
                  ? Tokens.fromJson(props['tokens'])
                  : null,
            );
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
            if (!working) _noteObservedCompletion(msg.id);
            // A Codex turn can contain several completed items and still be
            // running. Its explicit session status owns the busy state.
            if (capabilities.messageCompletionEndsRun) {
              if (working) {
                busySessions.add(msg.sessionID);
                _markSessionAttentionActive(msg.sessionID);
              } else {
                busySessions.remove(msg.sessionID);
              }
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

      // ---- OpenCode 2 interaction envelopes ----
      // Emitted by the v2 event adapter (lib/api2/gateway_events.dart):
      //   form.v2.created                 {form: Form.Info (raw v2 JSON)}
      //   form.v2.replied                 {id, sessionID}
      //   form.v2.cancelled               {id, sessionID}
      //   session.inbox.enqueued          {sessionID, inboxID, item}
      //   session.inbox.delivered         {sessionID, inboxID}
      //   session.inbox.cancelled         {sessionID, inboxID}
      //   session.inbox.delivery.changed  {sessionID, inboxID, delivery}
      case 'form.v2.created':
        if (supportsForms) _handleFormCreated(props);
        break;

      case 'form.v2.replied':
      case 'form.v2.cancelled':
        if (!supportsForms) break;
        final formID = props['id']?.toString() ?? '';
        if (formID.isNotEmpty) _resolveForm(formID);
        break;

      case 'session.inbox.enqueued':
        _handleInboxEnqueued(props);
        break;

      case 'session.inbox.delivered':
      case 'session.inbox.cancelled':
        _handleInboxRemoved(props);
        break;

      case 'session.inbox.delivery.changed':
        _handleInboxDeliveryChanged(props);
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
              retryStates.remove(sid);
              _settleSessionAttention(sid, CodingAlertKind.complete);
              unawaited(_refreshOneSession(sid));
              break;
            case 'busy':
              busySessions.add(sid);
              retryStates.remove(sid);
              _markSessionAttentionActive(sid);
              break;
            case 'retry':
              busySessions.add(sid);
              final retry = SessionRetryState.fromStatusJson(rawStatus);
              if (retry != null) retryStates[sid] = retry;
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
          retryStates.remove(sid);
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
          retryStates.remove(sid);
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

      case 'message.part.updated':
        // Parsed only as far as the ongoing notification needs; the chat
        // screen builds the full part from the event bus below. Deliberately
        // no notifyListeners: every streamed delta lands here.
        final part = props['part'];
        if (part is Map && part['type'] == 'tool') {
          final sid = part['sessionID']?.toString() ?? '';
          final state = part['state'];
          final toolStatus = state is Map ? state['status']?.toString() : null;
          if (sid.isNotEmpty && toolStatus != null) {
            final before = _runningToolDetail[sid];
            if (toolStatus == 'running') {
              _runningToolDetail[sid] = toolSentence(
                part['tool']?.toString() ?? '',
              );
            } else if (toolStatus == 'completed' || toolStatus == 'error') {
              _runningToolDetail.remove(sid);
            }
            if (_runningToolDetail[sid] != before) _publishLiveStatus();
          }
        }
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
      'message': props['message'],
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

  /// [message] rides only on v2 rejections (steering-by-rejection); the v1
  /// reply shape has no field for it and ignores it.
  Future<void> answerPermission(
    String requestID,
    String response, {
    String? message,
    PendingRequestIdentity? expectedRequest,
  }) => _sendPermissionReply(
    api,
    requestID,
    response,
    message: message,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  PendingRequestIdentity permissionIdentity(PermissionRequest request) =>
      PendingRequestIdentity._(
        this,
        locationRevision,
        true,
        request.id,
        _permissionContents(request),
      );

  PendingRequestIdentity questionIdentity(PendingQuestion request) =>
      PendingRequestIdentity._(
        this,
        locationRevision,
        false,
        request.id,
        _questionContents(request),
      );

  bool isRequestPending(PendingRequestIdentity request) {
    if (request._retired ||
        _disposed ||
        request._owner != this ||
        request._location != locationRevision) {
      request._retired = true;
      return false;
    }
    bool pending;
    if (request._permission) {
      final current = permissions[request._id];
      pending =
          current != null && _permissionContents(current) == request._contents;
    } else {
      final current = questions[request._id];
      pending =
          current != null && _questionContents(current) == request._contents;
    }
    if (!pending) request._retired = true;
    return pending;
  }

  final _pendingReplies =
      <
        (int, bool, String, String),
        ({PendingRequestIdentity request, Future<void> future})
      >{};

  /// A notification, sheet and inline card share one slot. Claim it before
  /// waking the transport; a second decision waits for the first result.
  Future<void> _withPendingReply(
    PendingRequestIdentity request,
    Future<void> Function() send,
  ) {
    if (!isRequestPending(request)) return Future.value();
    final key = (
      request._location,
      request._permission,
      request._id,
      request._contents,
    );
    final existing = _pendingReplies[key];
    if (existing != null && isRequestPending(existing.request)) {
      return existing.future;
    }
    final completion = Completer<void>();
    _pendingReplies[key] = (request: request, future: completion.future);
    // Remember a removal even if a server reuses the ID before wake finishes.
    void checkPending() => isRequestPending(request);
    addListener(checkPending);
    () async {
      try {
        await send();
        completion.complete();
      } catch (error, stack) {
        // Resolution or replacement makes the old failure irrelevant.
        if (!isRequestPending(request)) {
          completion.complete();
        } else {
          completion.completeError(error, stack);
        }
      } finally {
        removeListener(checkPending);
        if (identical(_pendingReplies[key]?.future, completion.future)) {
          _pendingReplies.remove(key);
        }
      }
    }();
    return completion.future;
  }

  /// True when [requestID] arrived over the OpenCode 2 permission contract,
  /// whose reject reply accepts an optional message shown to the model. The
  /// permission sheet omits its reject-message field otherwise.
  bool permissionSupportsRejectMessage(String requestID) =>
      _v2PermissionSessions.containsKey(requestID);

  /// Sends one permission reply on an already-resolved transport. Notification
  /// actions use this directly with the live background transport because the
  /// foreground path's wake reconciliation doubles as an app resume, which
  /// would clear every posted alert.
  Future<void> _sendPermissionReply(
    ServerGateway? currentApi,
    String requestID,
    String response, {
    String? message,
    PendingRequestIdentity? expectedRequest,
    bool prepareTransport = false,
  }) async {
    if (expectedRequest != null &&
        (!expectedRequest._permission || expectedRequest._id != requestID)) {
      throw ArgumentError('Permission request identity does not match');
    }
    if (expectedRequest != null && !isRequestPending(expectedRequest)) return;
    final permission = permissions[requestID];
    if (permission == null) {
      if (_resolvedPermissionIDs.contains(requestID)) return;
      throw StateError('Permission request $requestID is no longer pending');
    }
    final request = expectedRequest ?? permissionIdentity(permission);
    return _withPendingReply(request, () async {
      final transport = prepareTransport
          ? await _requireActionTransport()
          : currentApi;
      if (!isRequestPending(request)) return;
      if (transport == null) throw StateError('Not connected to OpenCode');
      await _writePermissionReply(
        transport,
        request,
        response,
        message: message,
      );
    });
  }

  Future<void> _writePermissionReply(
    ServerGateway currentApi,
    PendingRequestIdentity request,
    String response, {
    String? message,
  }) async {
    final requestID = request._id;
    final permission = permissions[requestID]!;
    final generation = _generation;
    try {
      final legacyIdentity = _legacyPermissionIdentities[requestID];
      final v2SessionID = _v2PermissionSessions[requestID];
      if (v2SessionID != null) {
        await currentApi.respondPermissionV2(
          v2SessionID,
          permission.id,
          response,
          message: message,
        );
      } else {
        await currentApi.respondPermission(
          permission.id,
          response,
          legacySessionID: legacyIdentity?.sessionID,
          legacyPermissionID: legacyIdentity?.permissionID,
          message: message,
        );
      }
      if (!_isCurrent(generation, currentApi) || !isRequestPending(request)) {
        return;
      }
      _resolvePermission(requestID);
    } catch (error) {
      if (!_isCurrent(generation, currentApi) || !isRequestPending(request)) {
        return;
      }
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
    List<List<String>> answers, {
    PendingRequestIdentity? expectedRequest,
  }) => _sendQuestionReply(
    api,
    repository,
    requestID,
    answers,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  Future<void> rejectQuestion(
    String requestID, {
    PendingRequestIdentity? expectedRequest,
  }) => _sendQuestionReply(
    api,
    repository,
    requestID,
    null,
    expectedRequest: expectedRequest,
    prepareTransport: true,
  );

  /// Sends one question answer on already-resolved transport objects; the
  /// notification-action path passes the live background transport directly
  /// to avoid resume semantics (see [_sendPermissionReply]).
  Future<void> _sendQuestionAnswer(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    String requestID,
    List<List<String>> answers,
  ) => _sendQuestionReply(currentApi, current, requestID, answers);

  Future<void> _sendQuestionReply(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    String requestID,
    List<List<String>>? answers, {
    PendingRequestIdentity? expectedRequest,
    bool prepareTransport = false,
  }) async {
    if (expectedRequest != null &&
        (expectedRequest._permission || expectedRequest._id != requestID)) {
      throw ArgumentError('Question request identity does not match');
    }
    if (expectedRequest != null && !isRequestPending(expectedRequest)) return;
    final question = questions[requestID];
    if (question == null) {
      if (_resolvedQuestionIDs.contains(requestID)) return;
      throw StateError('Question request $requestID is no longer pending');
    }
    final request = expectedRequest ?? questionIdentity(question);
    final capturedAnswers = answers
        ?.map((values) => List<String>.of(values))
        .toList();
    return _withPendingReply(request, () async {
      if (prepareTransport) {
        await prepareActionTransport();
        currentApi = api;
        current = repository;
      }
      if (!isRequestPending(request)) return;
      await _writeQuestionReply(currentApi, current, request, capturedAnswers);
    });
  }

  Future<void> _writeQuestionReply(
    ServerGateway? currentApi,
    ServerOperationsGateway? current,
    PendingRequestIdentity request,
    List<List<String>>? answers,
  ) async {
    final requestID = request._id;
    final generation = _generation;
    if (current == null) throw StateError('Not connected to OpenCode');
    final v2SessionID = _v2QuestionSessions[requestID];
    try {
      if (v2SessionID != null) {
        if (currentApi == null) throw StateError('Not connected to OpenCode');
        if (answers == null) {
          await currentApi.rejectQuestionV2(v2SessionID, requestID);
        } else {
          await currentApi.answerQuestionV2(v2SessionID, requestID, answers);
        }
      } else if (answers == null) {
        await current.rejectQuestion(requestID);
      } else {
        await current.answerQuestion(requestID, answers);
      }
    } catch (error) {
      if (!_isCurrent(generation, currentApi) ||
          repository != current ||
          !isRequestPending(request)) {
        return;
      }
      if (_isQuestionNotFound(error, requestID)) {
        _resolveQuestion(requestID);
        return;
      }
      rethrow;
    }
    if (!_isCurrent(generation, currentApi) ||
        repository != current ||
        !isRequestPending(request)) {
      return;
    }
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

  // ---------------- Forms (OpenCode 2) ----------------

  /// True when the connected server speaks the v2 forms contract.
  bool get supportsUsageStatistics =>
      repository is UsageStatisticsGateway &&
      (repository as UsageStatisticsGateway).usageStatisticsSupported;

  bool get supportsSessionNotes =>
      repository is SessionNoteGateway &&
      (repository as SessionNoteGateway).sessionNotesSupported;

  bool get supportsSessionSkills =>
      repository is SessionSkillGateway &&
      (repository as SessionSkillGateway).sessionSkillsSupported;
  final _skillWrites = <(int, String)>{};

  Future<void> activateSessionSkill(
    String sessionID,
    String skillID, {
    required bool resume,
    required int expectedLocation,
  }) async {
    bool current() =>
        !_disposed &&
        locationRevision == expectedLocation &&
        !_deletedSessionIDs.contains(sessionID);
    if (!current()) {
      throw const SessionSkillException(SessionSkillFailure.changed);
    }
    final key = (expectedLocation, sessionID);
    if (!_skillWrites.add(key)) {
      throw const SessionSkillException(SessionSkillFailure.busy);
    }
    try {
      final transport = await prepareActionRepository();
      final currentApi = api;
      if (!current()) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      if (transport == null ||
          transport is! SessionSkillGateway ||
          !(transport as SessionSkillGateway).sessionSkillsSupported) {
        throw const SessionSkillException(SessionSkillFailure.unsupported);
      }
      await waitForSessionSelection(sessionID, expectedApi: currentApi);
      if (!current() || !identical(transport, repository)) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      final revision = sessionHistoryRevision(sessionID);
      final fresh = await transport.getSessionDetails(sessionID);
      if (!current() ||
          !identical(transport, repository) ||
          !identical(currentApi, api) ||
          revision != sessionHistoryRevision(sessionID)) {
        throw const SessionSkillException(SessionSkillFailure.changed);
      }
      if (fresh.reverted ||
          fresh.stagedRevert != null ||
          sessionsById[sessionID]?.stagedRevert != null ||
          sessionRevertSaving(sessionID)) {
        throw const SessionSkillException(SessionSkillFailure.staged);
      }
      await (transport as SessionSkillGateway).activateSessionSkill(
        sessionID,
        skillID,
        resume: resume,
      );
      if (current() && identical(transport, repository)) {
        _eventBus.add(
          EventEnvelope(
            type: 'session.skill.changed',
            properties: {'sessionID': sessionID},
          ),
        );
      }
    } finally {
      _skillWrites.remove(key);
    }
  }

  final _noteWrites = <(int, String)>{};
  final _noteRevisions = <(int, String), int>{};
  final _noteReceipts = <(int, String), bool>{};
  bool? sessionNoteReceipt(String id) => _noteReceipts[(locationRevision, id)];
  void dismissSessionNoteReceipt(String id) {
    _noteReceipts.remove((locationRevision, id));
    notifyListeners();
  }

  bool isSessionNoteReviewCurrent(SessionNoteReview review) =>
      !_disposed &&
      review.scope == (this, locationRevision) &&
      !_deletedSessionIDs.contains(review.sessionID) &&
      review.revision ==
          (_noteRevisions[(locationRevision, review.sessionID)] ?? 0);

  Future<SessionNoteReview> loadSessionNote(String id) async {
    final scope = (this, locationRevision);
    final revision = _noteRevisions[(locationRevision, id)] ?? 0;
    final transport = await prepareActionRepository();
    if (scope != (this, locationRevision) ||
        _disposed ||
        _deletedSessionIDs.contains(id)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    if (transport is! SessionNoteGateway ||
        !(transport as SessionNoteGateway).sessionNotesSupported) {
      throw const SessionNoteException(SessionNoteFailure.unsupported);
    }
    final value = await (transport as SessionNoteGateway).loadSessionNote(id);
    final review = SessionNoteReview(
      scope: scope,
      sessionID: id,
      value: value,
      revision: revision,
    );
    if (!isSessionNoteReviewCurrent(review) ||
        !identical(transport, repository)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    return review;
  }

  Future<void> saveSessionNote(SessionNoteReview review, String? value) async {
    if (!isSessionNoteReviewCurrent(review)) {
      throw const SessionNoteException(SessionNoteFailure.changed);
    }
    if (value != null &&
        SessionNoteGateway.encodedBytes(value) > SessionNoteGateway.maxBytes) {
      throw const SessionNoteException(SessionNoteFailure.tooLarge);
    }
    final key = (locationRevision, review.sessionID);
    if (!_noteWrites.add(key)) {
      throw const SessionNoteException(SessionNoteFailure.busy);
    }
    try {
      final transport = await prepareActionRepository();
      if (!isSessionNoteReviewCurrent(review)) {
        throw const SessionNoteException(SessionNoteFailure.changed);
      }
      if (transport is! SessionNoteGateway ||
          !(transport as SessionNoteGateway).sessionNotesSupported) {
        throw const SessionNoteException(SessionNoteFailure.unsupported);
      }
      final notes = transport as SessionNoteGateway;
      final current = await notes.loadSessionNote(review.sessionID);
      if (!isSessionNoteReviewCurrent(review) ||
          !identical(transport, repository) ||
          current != review.value) {
        throw const SessionNoteException(SessionNoteFailure.changed);
      }
      if (value == null) {
        await notes.removeSessionNote(review.sessionID);
      } else {
        await notes.saveSessionNote(review.sessionID, value);
      }
      // A receipt belongs only to the session/location that accepted the write.
      if (review.scope == (this, locationRevision) &&
          !_disposed &&
          !_deletedSessionIDs.contains(review.sessionID)) {
        _noteRevisions[key] = (_noteRevisions[key] ?? 0) + 1;
        _noteReceipts[key] = value != null;
        while (_noteReceipts.length > 128) {
          _noteReceipts.remove(_noteReceipts.keys.first);
        }
        notifyListeners();
      }
    } finally {
      _noteWrites.remove(key);
    }
  }

  bool get supportsSessionReadState => repository is SessionReadStateGateway;
  late final _sessionReadStore = SessionReadStore(store.prefs);
  late final _returnBriefStore = ReturnBriefStore(store.prefs);

  /// Saved scope excludes connection epochs so dismissal survives recovery;
  /// callbacks additionally retain the location revision to reject old UI.
  (String, String, int) get returnBriefScope {
    final owner = _connectedProfile ?? profile;
    return (
      owner?.id ?? '',
      jsonEncode([owner?.baseUrl, directory, workspace]),
      locationRevision,
    );
  }

  ReturnBriefAck get returnBriefAcknowledgement {
    final scope = returnBriefScope;
    return _returnBriefStore.acknowledged(scope.$1, scope.$2);
  }

  Future<void> dismissReturnBrief(
    ReturnBrief shown, {
    required (String, String, int) expectedScope,
  }) async {
    if (returnBriefScope != expectedScope ||
        !isProfileReadable(expectedScope.$1)) {
      throw StateError('The project changed. Review its current brief.');
    }
    await _returnBriefStore.acknowledge(
      expectedScope.$1,
      expectedScope.$2,
      shown,
    );
    if (!_disposed) notifyListeners();
  }

  late bool _shareSessionViews =
      store.prefs.getBool('oc.shareSessionViews') ?? true;
  bool get shareSessionViews => _shareSessionViews;
  bool _savingReadPrivacy = false;
  bool get savingReadPrivacy => _savingReadPrivacy;
  int _readPrivacyRevision = 0;
  int get readPrivacyRevision => _readPrivacyRevision;
  final _viewOperations = <Object, Future<void>>{};
  final _deletingReadProfiles = <String>{};
  final _profileDeletions = <String, Future<DeleteProfileResult>>{};
  Future<void> _profileDeletionChanges = Future.value();
  bool _readProfileAvailable(String id) =>
      !_deletingReadProfiles.contains(id) &&
      (id.isEmpty || store.profiles.any((profile) => profile.id == id));

  Future<void> setShareSessionViews(bool value) async {
    if (_savingReadPrivacy) return;
    final previous = _shareSessionViews;
    if (previous == value) return;
    _shareSessionViews = value;
    _savingReadPrivacy = true;
    final revision = ++_readPrivacyRevision;
    notifyListeners();
    try {
      if (!await store.prefs.setBool('oc.shareSessionViews', value)) {
        throw StateError('Could not save the read-state preference');
      }
    } catch (_) {
      // A failed opt-out stays private in this process. Turning sharing on
      // requires a successful saved preference before observers may send.
      if (_readPrivacyRevision == revision) {
        _shareSessionViews = false;
        _readPrivacyRevision++;
        notifyListeners();
      }
      rethrow;
    } finally {
      _savingReadPrivacy = false;
      _readPrivacyRevision++;
      if (!_disposed) notifyListeners();
    }
  }

  (String, String) _sessionReadKey(Session session) {
    final currentProfile = _connectedProfile ?? profile;
    return (
      currentProfile?.id ?? '',
      jsonEncode([
        currentProfile?.baseUrl,
        session.directory ?? directory,
        session.workspaceID ?? workspace,
        session.id,
      ]),
    );
  }

  bool isSessionUnread(Session session) {
    if (!supportsSessionReadState || busySessions.contains(session.id)) {
      return false;
    }
    final (profileID, key) = _sessionReadKey(session);
    final cached = sessionsById[session.id];
    final matching =
        cached != null && _sessionReadKey(cached) == (profileID, key);
    final idle = _newerWatermark(
      session.time?.idle,
      matching ? cached.time?.idle : null,
    );
    final local = _sessionReadStore.viewed(profileID, key);
    final remote = shareSessionViews
        ? _newerWatermark(
            session.time?.viewed,
            matching ? cached.time?.viewed : null,
          )
        : 0;
    return idle > local && idle > remote;
  }

  int _newerWatermark(int? a, int? b) => (a ?? 0) > (b ?? 0) ? a! : b ?? 0;

  Session _preserveReadState(Session incoming) {
    final previous = sessionsById[incoming.id];
    if (previous == null ||
        !supportsSessionReadState ||
        _sessionReadKey(previous) != _sessionReadKey(incoming)) {
      return incoming;
    }
    return incoming.copyWith(
      time: (incoming.time ?? SessionTime()).withReadState(
        idle: _newerWatermark(previous.time?.idle, incoming.time?.idle),
        viewed: _newerWatermark(previous.time?.viewed, incoming.time?.viewed),
      ),
    );
  }

  void _applySessionViewed(String id, int idle) {
    final previous = sessionsById[id] ?? Session(id: id);
    if ((previous.time?.viewed ?? 0) >= idle) return;
    _markSessionChanged(id, affectsStatus: false);
    sessionsById[id] = previous.copyWith(
      time: (previous.time ?? SessionTime()).withReadState(viewed: idle),
    );
    notifyListeners();
    if (previous.time?.idle == null) unawaited(_refreshOneSession(id));
  }

  /// Called only by a visible, loaded chat. Refresh/polling never invokes it.
  /// Recheck visibility and privacy after wake, then acknowledge the exact
  /// observed completion; a newer idle transition remains unread.
  Future<void> viewSession(
    String id, {
    required bool Function() isForeground,
    int? observedIdle,
    int? expectedLocationRevision,
  }) {
    if (expectedLocationRevision != null &&
        expectedLocationRevision != locationRevision) {
      return Future.value();
    }
    final session = sessionsById[id];
    final idle = observedIdle ?? session?.time?.idle;
    if (!supportsSessionReadState ||
        session == null ||
        idle == null ||
        idle <= 0 ||
        idle > (session.time?.idle ?? 0) ||
        busySessions.contains(id) ||
        !isForeground()) {
      return Future.value();
    }
    final scope = locationRevision;
    final privacy = _readPrivacyRevision;
    final (profileID, localKey) = _sessionReadKey(session);
    if (!_readProfileAvailable(profileID)) return Future.value();
    final operationKey = (scope, privacy, id, idle);
    final existing = _viewOperations[operationKey];
    if (existing != null) {
      // A newly opened chat must recheck its own visibility after an older
      // viewer's wake finishes; that viewer may have been disposed meanwhile.
      return existing.then(
        (_) => viewSession(
          id,
          isForeground: isForeground,
          observedIdle: idle,
          expectedLocationRevision: scope,
        ),
      );
    }
    final completion = Completer<void>();
    _viewOperations[operationKey] = completion.future;
    bool current() =>
        !_disposed &&
        locationRevision == scope &&
        _readPrivacyRevision == privacy &&
        isForeground() &&
        !busySessions.contains(id) &&
        _readProfileAvailable(profileID) &&
        !_deletedSessionIDs.contains(id) &&
        _sessionReadKey(sessionsById[id] ?? session) == (profileID, localKey);
    () async {
      try {
        // This device saw this run, even with sharing disabled or a failed
        // connection. The local cache never queues a server write.
        await _sessionReadStore.record(profileID, localKey, idle);
        if (!current()) {
          completion.complete();
          return;
        }
        notifyListeners();
        if (shareSessionViews &&
            !_savingReadPrivacy &&
            (sessionsById[id]?.time?.viewed ?? 0) < idle) {
          final transport = await prepareActionRepository();
          if (!current() || !shareSessionViews || _savingReadPrivacy) {
            completion.complete();
            return;
          }
          if (transport is! SessionReadStateGateway) {
            completion.complete();
            return;
          }
          await (transport as SessionReadStateGateway).viewSession(id, idle);
          if (current() && identical(repository, transport)) {
            _applySessionViewed(id, idle);
          }
        }
        completion.complete();
      } catch (error, stack) {
        completion.completeError(error, stack);
      } finally {
        if (identical(_viewOperations[operationKey], completion.future)) {
          _viewOperations.remove(operationKey);
        }
      }
    }();
    return completion.future;
  }

  bool get supportsForms => api?.capabilities.forms ?? false;

  /// True when the connected server exposes the v2 session inbox.
  bool get supportsInbox => api?.capabilities.inbox ?? false;

  List<Api2FormInfo> formsForSession(String sessionID) => forms.values
      .where((form) => form.sessionID == sessionID)
      .toList(growable: false);

  Api2FormInfo? formForSession(String sessionID) {
    for (final form in forms.values) {
      if (form.sessionID == sessionID) return form;
    }
    return null;
  }

  void _handleFormCreated(Map<String, dynamic> props) {
    final raw = props['form'];
    if (raw is! Map) return;
    final form = Api2FormInfo.fromJson(Map<String, dynamic>.from(raw));
    if (form == null || form.id.isEmpty) return;
    formsLoading = false;
    _resolvedFormIDs.remove(form.id);
    forms[form.id] = form;
    _formRevision += 1;
    _syncInputAlerts();
    notifyListeners();
  }

  void _resolveForm(String formID) {
    formsLoading = false;
    _resolvedFormIDs.add(formID);
    _formRevision += 1;
    if (forms.remove(formID) != null) {
      _syncInputAlerts();
    }
    notifyListeners();
  }

  /// Re-polls the pending form lists. Form events are ephemeral, so this
  /// runs after every SSE (re)connect; it is a no-op on v1 servers.
  Future<void> refreshPendingForms() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null || !currentApi.capabilities.forms) return;
    final refreshGeneration = ++_formRefreshGeneration;
    final revision = _formRevision;
    formsLoading = true;
    formsError = null;
    notifyListeners();
    try {
      final pending = await currentApi.pendingForms();
      if (!_isCurrent(generation, currentApi) ||
          refreshGeneration != _formRefreshGeneration) {
        return;
      }
      final hydrated = {
        for (final form in pending)
          if (!_resolvedFormIDs.contains(form.id)) form.id: form,
      };
      if (revision != _formRevision) {
        // Events moved the set mid-fetch; they are fresher than the poll.
        hydrated.addAll(forms);
        hydrated.removeWhere((id, _) => _resolvedFormIDs.contains(id));
      }
      forms = hydrated;
      formsLoading = false;
      _syncInputAlerts();
      notifyListeners();
    } catch (error) {
      if (!_isCurrent(generation, currentApi) ||
          refreshGeneration != _formRefreshGeneration) {
        return;
      }
      formsLoading = false;
      formsError = error.toString();
      _recordLocationError(formsError!);
      notifyListeners();
    }
  }

  /// Sends the assembled answer of a pending form. Rethrows transport
  /// failures for the presenter (400 invalid-answer keeps the form open with
  /// a banner); a 409 already-settled also resolves the form locally so the
  /// presenter can toast-and-close.
  Future<void> replyForm(String formID, Map<String, dynamic> answer) async {
    final form = forms[formID];
    if (form == null) {
      if (_resolvedFormIDs.contains(formID)) return;
      throw StateError('Form request $formID is no longer pending');
    }
    final scope = returnBriefScope;
    bool current() =>
        returnBriefScope == scope && identical(forms[formID], form);
    final currentApi = await _requireActionTransport();
    if (!current()) {
      throw StateError(
        'The form or project changed. Reopen the current request.',
      );
    }
    try {
      await currentApi.replyForm(form.sessionID, formID, answer);
    } on ApiException catch (error) {
      if (error.errorTag == 'FormAlreadySettledError' ||
          error.errorTag == 'FormNotFoundError') {
        if (current()) _resolveForm(formID);
      }
      rethrow;
    }
    if (current()) _resolveForm(formID);
  }

  /// Cancels (dismisses) a pending form; the agent continues unanswered.
  Future<void> cancelForm(String formID) async {
    final form = forms[formID];
    if (form == null) {
      if (_resolvedFormIDs.contains(formID)) return;
      throw StateError('Form request $formID is no longer pending');
    }
    final scope = returnBriefScope;
    bool current() =>
        returnBriefScope == scope && identical(forms[formID], form);
    final currentApi = await _requireActionTransport();
    if (!current()) {
      throw StateError(
        'The form or project changed. Reopen the current request.',
      );
    }
    try {
      await currentApi.cancelForm(form.sessionID, formID);
    } on ApiException catch (error) {
      if (error.errorTag == 'FormAlreadySettledError' ||
          error.errorTag == 'FormNotFoundError') {
        if (current()) _resolveForm(formID);
        return;
      }
      rethrow;
    }
    if (current()) _resolveForm(formID);
  }

  // ---------------- Inbox (OpenCode 2) ----------------

  /// Pending (admitted, undelivered) sends of one session, oldest first.
  List<Api2InboxItem> inboxItemsFor(String sessionID) {
    final items = _inboxBySession[sessionID];
    if (items == null || items.isEmpty) return const [];
    final sorted = items.values.toList()
      ..sort((a, b) => (a.timeCreated ?? 0).compareTo(b.timeCreated ?? 0));
    return sorted;
  }

  void _handleInboxEnqueued(Map<String, dynamic> props) {
    final sessionID = props['sessionID']?.toString() ?? '';
    final inboxID = props['inboxID']?.toString() ?? '';
    final rawItem = props['item'];
    if (sessionID.isEmpty || inboxID.isEmpty || rawItem is! Map) return;
    final item = Api2InboxItem.fromJson({
      'id': inboxID,
      'sessionID': sessionID,
      'timeCreated': DateTime.now().millisecondsSinceEpoch,
      ...Map<String, dynamic>.from(rawItem),
    });
    if (item == null) return;
    (_inboxBySession[sessionID] ??= {})[inboxID] = item;
    inboxRevision += 1;
    notifyListeners();
  }

  void _handleInboxRemoved(Map<String, dynamic> props) {
    final sessionID = props['sessionID']?.toString() ?? '';
    final inboxID = props['inboxID']?.toString() ?? '';
    final items = _inboxBySession[sessionID];
    if (items == null || items.remove(inboxID) == null) return;
    if (items.isEmpty) _inboxBySession.remove(sessionID);
    inboxRevision += 1;
    notifyListeners();
  }

  void _handleInboxDeliveryChanged(Map<String, dynamic> props) {
    final sessionID = props['sessionID']?.toString() ?? '';
    final inboxID = props['inboxID']?.toString() ?? '';
    final delivery = Api2Delivery.parse(props['delivery']);
    final item = _inboxBySession[sessionID]?[inboxID];
    if (item == null || delivery == null) return;
    _inboxBySession[sessionID]![inboxID] = Api2InboxItem(
      id: item.id,
      sessionID: item.sessionID,
      timeCreated: item.timeCreated,
      type: item.type,
      payload: item.payload,
      delivery: delivery,
    );
    inboxRevision += 1;
    notifyListeners();
  }

  /// Reconciles one session's pending sends from REST (events are volatile).
  /// No-op on servers without an inbox.
  Future<void> refreshInbox(String sessionID) async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null || !currentApi.capabilities.inbox) return;
    final revisionAtStart = inboxRevision;
    List<Api2InboxItem> items;
    try {
      items = await currentApi.inboxItems(sessionID);
    } catch (_) {
      // The strip is a convenience surface; a failed reconcile keeps the
      // event-projected state rather than erroring the chat.
      return;
    }
    if (!_isCurrent(generation, currentApi) ||
        revisionAtStart != inboxRevision) {
      return;
    }
    final next = {for (final item in items) item.id: item};
    if (next.isEmpty) {
      if (_inboxBySession.remove(sessionID) == null) return;
    } else {
      _inboxBySession[sessionID] = next;
    }
    inboxRevision += 1;
    notifyListeners();
  }

  /// Cancels a pending send. Returns its text so the composer can restore
  /// it as a draft (cancel-back-to-composer is the edit affordance for
  /// immutable server items). A 409 already-delivered rethrows after
  /// dropping the item locally.
  Future<String?> cancelInboxItem(String sessionID, String inboxID) async {
    final text = _inboxBySession[sessionID]?[inboxID]?.promptText;
    final currentApi = await _requireActionTransport();
    try {
      await currentApi.cancelInboxItem(sessionID, inboxID);
    } on ApiException catch (error) {
      if (error.statusCode == 409 || error.statusCode == 404) {
        _handleInboxRemoved({'sessionID': sessionID, 'inboxID': inboxID});
      }
      rethrow;
    }
    _handleInboxRemoved({'sessionID': sessionID, 'inboxID': inboxID});
    return text;
  }

  /// Flips a pending send between steer and queue delivery. A 409
  /// already-delivered drops the local item and rethrows for the toast.
  Future<void> setInboxDelivery(
    String sessionID,
    String inboxID, {
    required Api2Delivery delivery,
  }) async {
    final currentApi = await _requireActionTransport();
    try {
      if (delivery == Api2Delivery.queue) {
        await currentApi.queueInboxItem(sessionID, inboxID);
      } else {
        await currentApi.steerInboxItem(sessionID, inboxID);
      }
    } on ApiException catch (error) {
      if (error.statusCode == 409 || error.statusCode == 404) {
        _handleInboxRemoved({'sessionID': sessionID, 'inboxID': inboxID});
      }
      rethrow;
    }
    _handleInboxDeliveryChanged({
      'sessionID': sessionID,
      'inboxID': inboxID,
      'delivery': delivery.wire,
    });
  }

  @visibleForTesting
  void handleEventForTesting(EventEnvelope event) => _onEvent(event);

  /// Marks [profile] as the connected one without a transport, so tests can
  /// exercise profile-bound guards such as [openSessionInWorktree].
  @visibleForTesting
  void adoptConnectedProfileForTesting(ServerProfile profile) {
    _connectedProfile = profile;
  }

  // ---------------- Sessions ----------------

  Future<void> refreshSessions() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final refreshGeneration = ++_sessionsRefreshGeneration;
    final revision = _sessionRevision;
    sessionsLoading = true;
    sessionsLoadingMore = false;
    sessionsError = null;
    sessionsMoreError = null;
    notifyListeners();
    try {
      final page = await currentApi.sessionPage();
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
      // Retry details ride on the same v1 status payload; fetch them only
      // when a session is actually retrying so the common path stays one
      // request.
      Map<String, SessionRetryState>? retries;
      if (statuses != null &&
          statuses.values.contains('retry') &&
          currentApi is SessionRetryGateway) {
        try {
          retries = await (currentApi as SessionRetryGateway)
              .sessionRetryStates();
        } catch (_) {
          retries = null;
        }
      }
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      _sessionSnapshotRevision = revision;
      _sessionPageIDs.clear();
      _usedSessionCursors.clear();
      _mergeSessionPage(page, revision);
      sessionsMoreError = null;
      sessionsNeedReload = false;
      if (statuses != null) {
        final statusIDs = {
          ...sessionsById.keys,
          ...busySessions,
          ...statuses.keys,
        };
        for (final id in statusIDs) {
          if ((_sessionStatusRevisions[id] ?? 0) > revision) continue;
          if (statuses[id] != null && statuses[id] != 'idle') {
            busySessions.add(id);
            _markSessionAttentionActive(id);
            if (!sessionsById.containsKey(id)) {
              unawaited(_refreshOneSession(id));
            }
          } else {
            busySessions.remove(id);
            _settleSessionAttention(id, CodingAlertKind.complete);
          }
          if (statuses[id] == 'retry') {
            final retry = retries?[id];
            if (retry != null) {
              retryStates[id] = retry;
            } else if (retries != null) {
              retryStates.remove(id);
            }
          } else {
            retryStates.remove(id);
          }
        }
      }
      sessionsLoading = false;
      sessionsError = statusError?.toString();
      if (statusError != null) _recordLocationError(sessionsError!);
      notifyListeners();
      unawaited(_refreshPinnedSessions());
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

  void _removeSession(String id) {
    _markSessionChanged(id);
    _deletedSessionIDs.add(id);
    sessionsById.remove(id);
    sessionDetailsErrors.remove(id);
    _sessionInventoryIDs.remove(id);
    _forgetSessionModel(id);
    busySessions.remove(id);
    retryStates.remove(id);
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
    _v2PermissionSessions.removeWhere((_, sessionID) => sessionID == id);
    _v2QuestionSessions.removeWhere((_, sessionID) => sessionID == id);
    final removedFormIDs = forms.values
        .where((form) => form.sessionID == id)
        .map((form) => form.id)
        .toList();
    if (removedFormIDs.isNotEmpty) _formRevision += 1;
    for (final formID in removedFormIDs) {
      forms.remove(formID);
    }
    if (_inboxBySession.remove(id) != null) inboxRevision += 1;
    _syncInputAlerts();
    notifyListeners();
  }

  void _mergeSessionPage(ServerPage<Session> page, int revision) {
    if (!_sessionInventoryInitialized) {
      for (final session in sessionsById.values) {
        _rememberSessionMembership(session);
      }
      _sessionInventoryInitialized = true;
    }
    for (final session in page.items) {
      _sessionPageIDs.add(session.id);
      if (_deletedSessionIDs.contains(session.id)) continue;
      if ((_sessionRevisions[session.id] ?? 0) <= revision) {
        sessionsById[session.id] = _preserveReadState(session);
        _sessionInventoryIDs.add(session.id);
      }
    }
    _sessionsCursor = page.hasMore ? page.nextCursor : null;
    // Absence is meaningful only after walking the entire inventory. A
    // partial head refresh must not delete older cached chats or their alerts.
    if (!page.hasMore) {
      for (final id in _sessionInventoryIDs.toList()) {
        if (!_sessionPageIDs.contains(id) &&
            (_sessionRevisions[id] ?? 0) <= _sessionSnapshotRevision) {
          _sessionInventoryIDs.remove(id);
        }
      }
    }
  }

  void _rememberSessionMembership(
    Session session, {
    bool authoritative = false,
  }) {
    if ((directory == null || session.directory == directory) &&
        (workspace == null || session.workspaceID == workspace)) {
      _sessionInventoryIDs.add(session.id);
    } else if (authoritative &&
        ((directory != null &&
                session.directory != null &&
                session.directory != directory) ||
            (workspace != null && session.workspaceID != workspace))) {
      _sessionInventoryIDs.remove(session.id);
    }
  }

  Future<void> loadMoreSessions() async {
    if (sessionsLoading || sessionsLoadingMore) return;
    if (sessionsNeedReload) {
      await refreshSessions();
      return;
    }
    final cursor = _sessionsCursor;
    final currentApi = api;
    if (cursor == null || currentApi == null) return;
    final generation = _generation;
    final refreshGeneration = ++_sessionsRefreshGeneration;
    final revision = _sessionRevision;
    sessionsLoadingMore = true;
    sessionsMoreError = null;
    notifyListeners();
    try {
      final page = await currentApi.sessionPage(cursor: cursor);
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      if (page.hasMore &&
          (page.nextCursor == cursor ||
              _usedSessionCursors.contains(page.nextCursor))) {
        sessionsNeedReload = true;
        throw const ProductException(
          'The session list changed. Reload recent sessions to continue.',
        );
      }
      _usedSessionCursors.add(cursor);
      _mergeSessionPage(page, revision);
    } catch (error) {
      if (!_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        return;
      }
      sessionsMoreError = error.toString();
      if (error is ApiException &&
          (error.statusCode == 400 || error.statusCode == 410)) {
        sessionsNeedReload = true;
      }
    } finally {
      if (_isCurrentSessionsRefresh(
        generation,
        currentApi,
        refreshGeneration,
      )) {
        sessionsLoadingMore = false;
        notifyListeners();
      }
    }
  }

  /// Direct routes and active sessions are independent of inventory pages.
  Future<void> ensureSession(String id) => _refreshOneSession(id);

  Future<void> _refreshOneSession(String id) {
    final currentApi = api;
    if (currentApi == null) return Future.value();
    final key = (currentApi, _generation, id, _sessionRevisions[id] ?? 0);
    final pending = _sessionReads[key];
    if (pending != null) return pending;
    late final Future<void> tracked;
    tracked = _readOneSession(id).whenComplete(() {
      if (identical(_sessionReads[key], tracked)) _sessionReads.remove(key);
    });
    _sessionReads[key] = tracked;
    return tracked;
  }

  Future<void> _readOneSession(String id) async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    final revision = _sessionRevisions[id] ?? 0;
    try {
      final session = await currentApi.session(id);
      if (!_isCurrent(generation, currentApi) ||
          _deletedSessionIDs.contains(id) ||
          revision != (_sessionRevisions[id] ?? 0)) {
        return;
      }
      final revertChanged =
          sessionsById[id]?.stagedRevert?.fingerprint !=
          session.stagedRevert?.fingerprint;
      sessionsById[id] = _preserveReadState(session);
      sessionDetailsErrors.remove(id);
      _rememberSessionMembership(session, authoritative: true);
      _markSessionChanged(id, affectsStatus: false);
      if (revertChanged) _resetSessionHistory(id);
      notifyListeners();
    } on ApiException catch (error) {
      if (error.statusCode == 404 &&
          _isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        _removeSession(id);
      } else if (_isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        sessionDetailsErrors[id] = error.toString();
        notifyListeners();
      }
    } catch (error) {
      if (_isCurrent(generation, currentApi) &&
          revision == (_sessionRevisions[id] ?? 0)) {
        sessionDetailsErrors[id] = error.toString();
        notifyListeners();
      }
    }
  }

  /// Polling fallback plus terminal-state reconciliation for connected SSE.
  void enablePollingFallback() {
    if (_poll?.isActive ?? false) return;
    _poll = Timer.periodic(const Duration(seconds: 5), (_) {
      if (sessionsLoading || sessionsLoadingMore) return;
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
      for (final id in busySessions) id: _sessionStatusRevisions[id] ?? 0,
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
      if ((_sessionStatusRevisions[entry.key] ?? 0) != entry.value) continue;
      final remoteStatus = statuses[entry.key] ?? 'idle';
      if (remoteStatus == 'idle') {
        final removed = busySessions.remove(entry.key);
        changed = retryStates.remove(entry.key) != null || changed;
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
    if (_disposed || isIsolated) return;
    _lifecycleWasBackgrounded = true;
    _quotaMonitor?.setRuntime(
      foreground: false,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _profileMonitor?.setRuntime(
      foreground: false,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
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
    if (_disposed || isIsolated) return Future.value();
    _quotaMonitor?.setRuntime(
      foreground: true,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _profileMonitor?.setRuntime(
      foreground: true,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
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
    if (_disposed || isIsolated) return Future.value();
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

  Future<T> _serializeQueueChange<T>(Future<T> Function() change) {
    final operation = _queueChanges.then((_) => change());
    _queueChanges = operation.then<void>((_) {}, onError: (Object _) {});
    return operation;
  }

  /// Set when the queue dropped entries on its own — expiry or a limit —
  /// and cleared once a screen has shown it. A prompt the user asked to send
  /// never disappears silently.
  String? _queueEvictionNotice;

  /// Reads and clears the pending eviction notice.
  String? takeQueueEvictionNotice() {
    final notice = _queueEvictionNotice;
    _queueEvictionNotice = null;
    return notice;
  }

  /// The queue, with its age/count/byte limits already applied.
  ///
  /// A queue written by an older build — or left to sit while the server
  /// stayed away — is trimmed on first read and written back, so the limits
  /// hold for existing installs and not only for new sends.
  List<QueuedPrompt> get _queue {
    final cached = _offlineQueue;
    if (cached != null) return cached;
    final eviction = OfflineQueueStore.enforceLimits(_queueStore.load());
    _offlineQueue = eviction.kept;
    if (eviction.removed > 0) {
      _queueEvictionNotice = eviction.notice;
      unawaited(_queueStore.save(eviction.kept));
    }
    return eviction.kept;
  }

  /// Bytes this device is holding for unsent work, for the settings readout.
  int get queuedPromptBytes => _queueStore.storedBytes();

  bool get queuedPromptStorageReadable => _queueStore.readable;

  int get sessionDraftBytes => _draftStore.storedBytes();

  /// Drops every queued prompt, for every profile. Returns whether the
  /// store accepted the write; a refusal leaves the queue intact rather
  /// than reporting a clear that did not happen.
  Future<bool> clearAllQueuedPrompts() => _serializeQueueChange(() async {
    if (_queue.isEmpty && _queueStore.readable) return true;
    if (!await _queueStore.save(const [])) return false;
    _offlineQueue = [];
    _queuedPromptsAcceptedUnrecorded.clear();
    notifyListeners();
    return true;
  });

  /// Drops every saved composer draft, for every session.
  Future<bool> clearAllSessionDrafts() => _serializeDraftChange(() async {
    if (!await _draftStore.save(const {})) return false;
    _sessionDrafts = {};
    notifyListeners();
    return _collectDraftAttachments();
  });

  /// Queued prompts across every profile, for the settings readout.
  int get totalQueuedPromptCount => _queue.length;

  /// Saved composer drafts across every session, for the settings readout.
  int get totalSessionDraftCount => _drafts.length;

  /// Queued prompts for one session of the active profile, oldest first.
  List<QueuedPrompt> queuedPromptsFor(String sessionID) {
    final profileID = profile?.id;
    if (profileID == null) return const [];
    return [
      for (final entry in _queue)
        if (entry.profileID == profileID && entry.sessionID == sessionID) entry,
    ];
  }

  /// Queued prompts across the active profile, for the connection banner.
  int get queuedPromptCount {
    final profileID = profile?.id;
    if (profileID == null) return 0;
    return _queue.where((entry) => entry.profileID == profileID).length;
  }

  /// Queued prompts of the active profile that left the device without a
  /// confirmed outcome. A flush skips these; only the user's explicit
  /// resend, edit, or discard moves them.
  int get queuedPromptReviewCount {
    final profileID = profile?.id;
    if (profileID == null) return 0;
    return _queue
        .where((entry) => entry.profileID == profileID && entry.dispatched)
        .length;
  }

  /// Whether a flush is dispatching this entry or persisting its outcome.
  bool queuedPromptSending(String id) => _queuedPromptInFlight == id;

  /// Whether the server accepted this entry's send but the device could not
  /// record it. Resending such an entry is a guaranteed duplicate.
  bool queuedPromptAcceptedUnrecorded(String id) =>
      _queuedPromptsAcceptedUnrecorded.contains(id);

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

  /// Queued prompts belonging to [profileID], whether or not it is active —
  /// what a "remove this server" confirmation has to disclose.
  int queuedPromptCountForProfile(String profileID) =>
      _queue.where((entry) => entry.profileID == profileID).length;

  /// Unsent composer drafts that removing [profileID] would delete.
  int draftCountForProfile(String profileID) =>
      _drafts.length -
      SessionDraftStore.withoutProfile(_drafts, profileID).length;

  /// Adds a drafted prompt to the offline queue. Returns false when the
  /// entry exceeds the composer's aggregate attachment cap and was not
  /// queued. A storage failure throws [OfflineQueueWriteException], so the
  /// caller can keep the composer and explain why it was not saved.
  Future<bool> queuePrompt(QueuedPrompt prompt) =>
      _serializeQueueChange(() async {
        final target = store.profiles.where((p) => p.id == prompt.profileID);
        if (target.any((p) => p.backend == ServerBackend.codex)) return false;
        if (prompt.payloadBytes > OfflineQueueStore.maxEntryBytes) return false;
        final eviction = OfflineQueueStore.enforceLimits([..._queue, prompt]);
        // The new entry losing its own eviction pass means the queue could not
        // make room for it; say so rather than reporting a queue that silently
        // dropped what the user just wrote.
        if (!eviction.kept.any((entry) => entry.id == prompt.id)) return false;
        if (!await _queueStore.save(eviction.kept)) {
          throw const OfflineQueueWriteException();
        }
        _offlineQueue = eviction.kept;
        if (eviction.removed > 0) _queueEvictionNotice = eviction.notice;
        notifyListeners();
        return true;
      });

  /// Removes a queued prompt. Returns false, removing nothing, when a flush
  /// is dispatching that entry right now: the decision was made against a
  /// bubble that said "queued", and the entry's real state is on the wire.
  /// The check runs inside the queue's serialization, after any marker
  /// write ahead of it. A storage refusal throws
  /// [OfflineQueueWriteException].
  Future<bool> removeQueuedPrompt(String id) => _serializeQueueChange(() async {
    if (_queuedPromptInFlight == id) return false;
    final kept = _queue.where((entry) => entry.id != id).toList();
    if (kept.length == _queue.length) return true;
    if (!await _queueStore.save(kept)) {
      throw const OfflineQueueWriteException();
    }
    _offlineQueue = kept;
    _queuedPromptsAcceptedUnrecorded.remove(id);
    notifyListeners();
    return true;
  });

  /// The user's explicit answer to an unconfirmed send: clear the dispatch
  /// marker so the next flush delivers the entry again, and start that
  /// flush. Returns false without changing anything when the entry is not
  /// in review any more — it is being dispatched, was removed, or its send
  /// is known to have been accepted. A storage refusal throws
  /// [OfflineQueueWriteException] and leaves the entry in review; nothing
  /// is sent until the marker is persisted as cleared.
  Future<bool> resendQueuedPrompt(String id) async {
    final cleared = await _serializeQueueChange(() async {
      if (_queuedPromptInFlight == id ||
          _queuedPromptsAcceptedUnrecorded.contains(id)) {
        return false;
      }
      final index = _queue.indexWhere((entry) => entry.id == id);
      if (index < 0 || !_queue[index].dispatched) return false;
      final next = [..._queue]..[index] = _queue[index].withDispatchedAt(null);
      if (!await _queueStore.save(next)) {
        throw const OfflineQueueWriteException();
      }
      _offlineQueue = next;
      if (!_disposed) notifyListeners();
      return true;
    });
    if (cleared) unawaited(flushOfflineQueue());
    return cleared;
  }

  /// Persists a replacement for one queue entry. Returns null when the entry
  /// is no longer queued, false when the store refused the write (memory
  /// and storage both stay as they were), true when the change persisted.
  Future<bool?> _replaceQueuedPrompt(
    String id,
    QueuedPrompt Function(QueuedPrompt entry) update,
  ) => _serializeQueueChange(() async {
    final index = _queue.indexWhere((entry) => entry.id == id);
    if (index < 0) return null;
    final next = [..._queue]..[index] = update(_queue[index]);
    if (!await _queueStore.save(next)) return false;
    _offlineQueue = next;
    if (!_disposed) notifyListeners();
    return true;
  });

  /// Persists the queue without [id]. Returns false when the store refused
  /// the write; a stale in-memory removal is never applied over a persisted
  /// entry, because that entry would resend after the next restart.
  Future<bool> _persistQueuedPromptRemoval(String id) =>
      _serializeQueueChange(() async {
        final kept = _queue.where((entry) => entry.id != id).toList();
        if (kept.length == _queue.length) return true;
        if (!await _queueStore.save(kept)) return false;
        _offlineQueue = kept;
        if (!_disposed) notifyListeners();
        return true;
      });

  SessionDraftStore get _draftStore =>
      _sessionDraftStore ??= SessionDraftStore(prefs: store.prefs);

  Map<String, SessionDraft> get _drafts =>
      _sessionDrafts ??= _draftStore.load();

  Future<T> _serializeDraftChange<T>(Future<T> Function() change) {
    final operation = _draftChanges.then((_) => change());
    _draftChanges = operation.then<void>((_) {}, onError: (Object _) {});
    return operation;
  }

  /// The unsent composer text for this server and session. Old unattributed
  /// drafts can be recovered automatically only with an unambiguous server.
  String? sessionDraft(String sessionID) {
    return savedSessionDraft(sessionID)?.text;
  }

  List<SessionDraft> get legacySessionDrafts =>
      _drafts.values.where((draft) => draft.profileID.isEmpty).toList()
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));

  /// Explicit removal after review, restricted to the exact legacy snapshot.
  Future<bool> removeLegacySessionDraft(SessionDraft snapshot) =>
      _serializeDraftChange(() async {
        if (snapshot.profileID.isNotEmpty || !_draftStore.readable) {
          return false;
        }
        final current = _drafts[snapshot.storageKey];
        if (current == null ||
            jsonEncode(current.toJson()) != jsonEncode(snapshot.toJson())) {
          return false;
        }
        final next = Map<String, SessionDraft>.of(_drafts)
          ..remove(snapshot.storageKey);
        if (!await _draftStore.save(next)) return false;
        _sessionDrafts = next;
        notifyListeners();
        return _collectDraftAttachments(owner: '');
      });

  SessionDraft? savedSessionDraft(String sessionID, {String? profileID}) {
    final owner = profileID ?? profile?.id ?? store.activeId ?? '';
    return (_drafts[SessionDraft.keyFor(owner, sessionID)] ??
        (store.profiles.length <= 1 ? _drafts[sessionID] : null));
  }

  Future<bool> _collectDraftAttachments({String? owner}) async {
    if (!_draftStore.readable) return false;
    if (!(store.prefs.getBool('oc.draftAttachmentVault') ?? false)) return true;
    final retained = <String, List<DraftAttachmentRef>>{};
    for (final draft in _drafts.values) {
      retained.putIfAbsent(draft.profileID, () => []).addAll(draft.attachments);
    }
    return _draftAttachmentVault.collect(retained, owner: owner);
  }

  Future<DraftAttachmentRecovery> restoreDraftAttachments(
    String sessionID, {
    required String profileID,
    required String? directory,
    required String? workspace,
  }) => _serializeDraftChange(() async {
    final draft = savedSessionDraft(sessionID, profileID: profileID);
    if (draft == null) return const DraftAttachmentRecovery([], []);
    return _draftAttachmentVault.restore(
      draft.profileID,
      draft.attachments,
      sameLocation:
          draft.directory == directory && draft.workspace == workspace,
    );
  });

  /// Remembers (or, when [text] is blank, forgets) the composer draft for
  /// one session. No [notifyListeners]: drafts drive nothing outside the
  /// chat screen that saved them.
  Future<void> saveSessionDraft(
    String sessionID,
    String text, {
    String? profileID,
    List<PromptAttachment>? attachments,
    String? attachmentDirectory,
    String? attachmentWorkspace,
  }) {
    final owner = profileID ?? profile?.id ?? store.activeId ?? '';
    final snapshot = attachments == null
        ? null
        : List<PromptAttachment>.of(attachments);
    return _serializeDraftChange(() async {
      if (!_draftStore.readable) {
        throw const SessionDraftWriteException(SessionDraftFailure.storage);
      }
      if (_deletingReadProfiles.contains(owner) ||
          (owner.isNotEmpty && !store.profiles.any((p) => p.id == owner))) {
        if (text.trim().isEmpty && (snapshot?.isEmpty ?? true)) return;
        throw const SessionDraftWriteException(
          SessionDraftFailure.profileRemoved,
        );
      }
      final key = SessionDraft.keyFor(owner, sessionID);
      if (snapshot == null &&
          (_drafts[key]?.text == text ||
              (text.trim().isEmpty &&
                  !_drafts.containsKey(key) &&
                  (store.profiles.length > 1 ||
                      !_drafts.containsKey(sessionID))))) {
        return;
      }
      var refs = text.trim().isEmpty
          ? <DraftAttachmentRef>[]
          : (_drafts[key]?.attachments ?? const <DraftAttachmentRef>[]);
      if (snapshot != null) {
        try {
          if (snapshot.any((a) => a.url.startsWith('data:'))) {
            if (!(store.prefs.getBool('oc.draftAttachmentVault') ?? false) &&
                !await store.prefs.setBool('oc.draftAttachmentVault', true)) {
              await store.prefs.reload();
              throw const SessionDraftWriteException(
                SessionDraftFailure.attachments,
              );
            }
            await _collectDraftAttachments();
          }
          refs = await _draftAttachmentVault.store(owner, snapshot);
        } catch (_) {
          await _collectDraftAttachments();
          throw const SessionDraftWriteException(
            SessionDraftFailure.attachments,
          );
        }
      }
      final next = Map<String, SessionDraft>.of(_drafts);
      if (store.profiles.length <= 1) next.remove(sessionID);
      if (text.trim().isEmpty && refs.isEmpty) {
        next.remove(key);
      } else {
        next[key] = SessionDraft(
          sessionID: sessionID,
          profileID: owner,
          text: text,
          updatedAt: DateTime.now().millisecondsSinceEpoch,
          attachments: refs,
          directory: snapshot == null
              ? _drafts[key]?.directory
              : attachmentDirectory,
          workspace: snapshot == null
              ? _drafts[key]?.workspace
              : attachmentWorkspace,
        );
      }
      if (next.length > SessionDraftStore.maxDrafts) {
        await _collectDraftAttachments();
        throw const SessionDraftWriteException(SessionDraftFailure.full);
      }
      if (!await _draftStore.save(next)) {
        await _collectDraftAttachments();
        throw const SessionDraftWriteException(SessionDraftFailure.storage);
      }
      _sessionDrafts = next;
      await _collectDraftAttachments();
    });
  }

  /// Removes [profileId] and every piece of local data keyed to it, as one
  /// operation.
  ///
  /// "Remove server" reads — in the UI and in the privacy policy — as a
  /// promise that the server's data leaves the device. Honouring that means
  /// more than dropping the profile row: the Keystore password, the
  /// profile-scoped preferences (model, agent, variant, location, provider
  /// migration flags), queued prompts with their embedded attachments,
  /// composer drafts, and the home-screen widget's session titles all have to
  /// go, including the copies this controller is holding in memory — a
  /// surviving cache would write deleted prompts straight back on the next
  /// save.
  ///
  /// The order is a transaction, and it runs backwards from the obvious one.
  /// Every dependent blob — queued prompts, drafts, the widget snapshot,
  /// the scoped preferences — is rewritten and *verified* first, while the
  /// profile is still saved and the operation is still abortable. Only once
  /// all of that is confirmed gone does the profile row and its Keystore
  /// password go.
  ///
  /// Deleting the profile first, as this used to, meant a later failed write
  /// left prompts and drafts on disk with no server row to attribute them to
  /// while the user was told the server had been removed. Every store here
  /// answers with a success flag; none of them is ignored, and anything that
  /// refuses is reported through [DeleteProfileResult.failures] rather than
  /// being rounded up to success.
  ///
  /// Server-side and provider-side data is untouched; only this device is
  /// cleared.
  Future<DeleteProfileResult> deleteProfileAndLocalData(String profileId) {
    if (_disposed || profileId.isEmpty) {
      return Future.error(StateError('The server profile is unavailable'));
    }
    final pending = _profileDeletions[profileId];
    if (pending != null) return pending;
    // Close admission synchronously, before any drain can yield. An epoch also
    // rejects old callbacks after a failed deletion makes the profile usable.
    _deletingReadProfiles.add(profileId);
    _profileMonitor?.removeProfile(profileId);
    _quotaMonitor?.removeProfile(profileId);
    final recoveryDisabled = ManagedServerRecovery.disableForProfile(
      store.prefs,
      profileId,
    ).then<Object?>((_) => null, onError: (Object error) => error);
    _pendingAuth.block(profileId);
    _integrationCommandAttempts.removeWhere(
      (key, _) => _authKeyProfile(key) == profileId,
    );
    _oauthStarts.removeWhere((key) => _authKeyProfile(key) == profileId);
    _authRecoveryActions.removeWhere(
      (key) => _authKeyProfile(key) == profileId,
    );
    _promptShelfDeletionRevisions[profileId] =
        (_promptShelfDeletionRevisions[profileId] ?? 0) + 1;
    final operation = _profileDeletionChanges
        .then((_) async {
          final recoveryError = await recoveryDisabled;
          if (recoveryError != null) {
            throw StateError(
              'Could not disable server recovery before deletion',
            );
          }
          await _profileMonitor?.drain(profileId);
          await _quotaMonitor?.drain(profileId);
          return _deleteProfileAndLocalData(profileId);
        })
        .whenComplete(() {
          _deletingReadProfiles.remove(profileId);
          _profileDeletions.remove(profileId);
        });
    _profileDeletions[profileId] = operation;
    _profileDeletionChanges = operation.then<void>(
      (_) {},
      onError: (Object _) {},
    );
    _profileDataChanges.notifyListeners();
    return operation;
  }

  Future<DeleteProfileResult> _deleteProfileAndLocalData(
    String profileId,
  ) async {
    await _draftChanges;
    await _pendingAuth.drain(profileId);
    // Drain shortcut writes before the deletion sweep discovers its keys.
    // A failed write must not prevent the user from removing a profile.
    try {
      await _modelLibraryWrite;
    } catch (_) {}
    try {
      await _sessionReadStore.drain(profileId);
    } catch (_) {}
    try {
      await _returnBriefStore.drain(profileId);
    } catch (_) {}
    try {
      await _sessionPins.drain(profileId);
    } catch (_) {}
    try {
      await _promptShelf.drain(profileId);
    } catch (_) {}
    final scopedKeys = store.profileScopedPreferenceKeys(profileId);
    final failures = <String>[];

    // Snapshot writes stay suspended for the whole transaction: any
    // notification would republish the deleted profile's session titles
    // straight back over the snapshot this method just cleared.
    _widgetSnapshotSuspended = true;
    try {
      // 1. Queued prompts — the largest and most sensitive blob, holding
      //    prompt text and attachment data URLs. Serialized with every other
      //    queue write: a flush may be persisting a dispatch marker for the
      //    active profile at this moment, and a rewrite computed from the
      //    pre-marker list would strip that marker from disk while the
      //    prompt is on the wire.
      var clearedQueued = 0;
      await _serializeQueueChange(() async {
        final keptQueue = [
          for (final entry in _queue)
            if (entry.profileID != profileId) entry,
        ];
        final removedQueued = _queue.length - keptQueue.length;
        if (removedQueued == 0) return;
        if (await _queueStore.save(keptQueue)) {
          _offlineQueue = keptQueue;
          clearedQueued = removedQueued;
        } else {
          failures.add(
            '$removedQueued queued '
            '${removedQueued == 1 ? 'prompt' : 'prompts'}',
          );
        }
      });

      // 2. Composer drafts.
      var clearedDrafts = 0;
      await _serializeDraftChange(() async {
        final keptDrafts = SessionDraftStore.withoutProfile(_drafts, profileId);
        final removedDrafts = _drafts.length - keptDrafts.length;
        if (removedDrafts > 0) {
          if (await _draftStore.save(keptDrafts)) {
            _sessionDrafts = keptDrafts;
            clearedDrafts = removedDrafts;
          } else {
            failures.add(
              '$removedDrafts unsent ${removedDrafts == 1 ? 'draft' : 'drafts'}',
            );
          }
        }
        if (!await _collectDraftAttachments(owner: profileId)) {
          failures.add('draft attachments');
        }
        if (!await _collectDraftAttachments(owner: '')) {
          failures.add('unattributed draft attachments');
        }
      });

      // 3. The home-screen widget's session titles.
      if (!await promptPhotos.clearForProfile(profileId)) {
        failures.add('pending photo');
      }
      await _pendingWidgetSnapshotWrite;
      final widgetOutcome = await _widgetSnapshot.clearForProfile(profileId);
      if (widgetOutcome == WidgetSnapshotClear.failed) {
        failures.add('the home-screen widget’s sessions');
      }

      // 4. Stash metadata and its private files must go before the generic
      // preference sweep. On failure that sweep would erase the durable owner
      // marker and make orphan-file cleanup undiscoverable on the next launch.
      final clearedStash = await _promptShelf.clearForProfile(profileId);
      if (!clearedStash) failures.add('stashed prompts and attachments');
      final unclearedKeys = clearedStash
          ? await store.removeScopedPreferences(profileId)
          : scopedKeys;
      if (clearedStash && unclearedKeys.isNotEmpty) {
        failures.add(
          '${unclearedKeys.length} saved '
          '${unclearedKeys.length == 1 ? 'setting' : 'settings'}',
        );
      }

      // 5. Only now the profile row and the Keystore password. A server
      //    whose data is still on disk keeps its row, so the user is never
      //    told a deletion happened that did not.
      if (failures.isNotEmpty) {
        return DeleteProfileResult(
          removedPreferenceKeys: scopedKeys.difference(unclearedKeys),
          removedQueuedPrompts: clearedQueued,
          removedDrafts: clearedDrafts,
          clearedWidgetSnapshot: widgetOutcome == WidgetSnapshotClear.cleared,
          removedProfile: false,
          failures: List.unmodifiable(failures),
        );
      }
      await store.remove(profileId);

      return DeleteProfileResult(
        removedPreferenceKeys: scopedKeys,
        removedQueuedPrompts: clearedQueued,
        removedDrafts: clearedDrafts,
        clearedWidgetSnapshot: widgetOutcome == WidgetSnapshotClear.cleared,
      );
    } finally {
      // A partial deletion can already have removed stash/history/pin/read
      // metadata. Reconcile optimistic preference caches even when a later
      // step failed; never let a retained in-memory shelf resurrect that data.
      try {
        await _promptShelf.reload();
      } catch (_) {
        // PromptShelfStore independently fails closed on uncertain writes.
      }
      _sessionReadStore.forgetProfile(profileId);
      _returnBriefStore.forgetProfile(profileId);
      _sessionPins.forget(profileId);
      _promptShelf.forget(profileId);
      _pendingAuth.forget(profileId);
      // Notify while republishing is still suspended, then lift it: this
      // controller keeps the deleted profile's sessions in memory until the
      // caller disconnects, and a republish would put their titles straight
      // back onto the home screen.
      _deletingReadProfiles.remove(profileId);
      if (!_disposed) notifyListeners();
      _widgetSnapshotSuspended = false;
    }
  }

  /// Set when a flush is requested while one is running, so the running
  /// flush starts another pass when it finishes. An explicit resend that
  /// lands mid-flush must not wait for the next reconnect.
  bool _flushOfflineQueueAgain = false;

  /// Sends queued prompts for the active profile, oldest first, through the
  /// wake-reconciled transport. A connectivity failure stops the flush (the
  /// server is still unreachable); a declared server failure keeps that
  /// entry with its error inline and continues with the next.
  ///
  /// Every send is bracketed by two persisted writes: the dispatch marker
  /// goes to storage before the request leaves the device, and the entry's
  /// removal goes to storage as soon as the server accepts it. A refusal of
  /// either write stops the batch instead of sending on state the next
  /// launch cannot see. Entries whose marker is set are never sent here;
  /// only the user's explicit resend clears it — see
  /// [QueuedPrompt.dispatchedAt].
  Future<void> flushOfflineQueue() async {
    if (_disposed || !capabilities.offlinePromptQueue) return;
    if (_flushingOfflineQueue) {
      _flushOfflineQueueAgain = true;
      return;
    }
    final profileID = profile?.id;
    if (profileID == null) return;
    final origin = (profileID, profile?.baseUrl, directory, workspace);
    bool eligible(QueuedPrompt entry) =>
        entry.profileID == profileID && !entry.dispatched;
    if (!_queue.any(eligible)) return;
    _flushingOfflineQueue = true;
    var sent = 0;
    var touched = false;
    try {
      for (final entry in List.of(_queue)) {
        if (!eligible(entry)) continue;
        final currentApi = await prepareActionTransport();
        await _queueChanges;
        if (_disposed ||
            currentApi == null ||
            !identical(currentApi, api) ||
            status != StreamStatus.connected ||
            origin != (profile?.id, profile?.baseUrl, directory, workspace)) {
          break;
        }
        if (!_queue.any(
          (queued) => queued.id == entry.id && !queued.dispatched,
        )) {
          continue;
        }
        // Set once the dispatch marker is persisted: from here on every
        // failure is an uncertain outcome that keeps the marker.
        var dispatched = false;
        // Set once the transport reported the prompt accepted.
        var delivered = false;
        // Set once the accepted entry's removal reached storage.
        var recorded = false;
        // Set when the store refused the marker write; nothing was sent.
        var markerRefused = false;
        var stop = false;
        touched = true;
        try {
          if (supportsStagedRevert) {
            final fresh = await currentApi.session(entry.sessionID);
            if (_disposed ||
                !identical(currentApi, api) ||
                origin !=
                    (profile?.id, profile?.baseUrl, directory, workspace)) {
              break;
            }
            final revertChanged =
                sessionsById[entry.sessionID]?.stagedRevert?.fingerprint !=
                fresh.stagedRevert?.fingerprint;
            sessionsById[entry.sessionID] = fresh;
            _markSessionChanged(entry.sessionID, affectsStatus: false);
            if (revertChanged) _resetSessionHistory(entry.sessionID);
            if (fresh.reverted || sessionRevertSaving(entry.sessionID)) {
              throw ApiException(
                'Review the staged revert before sending this queued prompt.',
                statusCode: 409,
                errorTag: 'SessionRevertPending',
              );
            }
          }
          // Persists the dispatch marker, then — only if storage accepted
          // it — puts the prompt on the wire, and records the acceptance
          // before anything else (a selection refresh, a session reload)
          // gets to run. Runs after model/agent prep so those preflight
          // failures stay ordinary retryable errors.
          Future<void> dispatch() async {
            final marked = await _replaceQueuedPrompt(entry.id, (queued) {
              // Selection/preflight can still be cancelled. Close removal
              // only once this serialized dispatch write actually begins,
              // then hold it through the persisted delivery outcome.
              _queuedPromptInFlight = entry.id;
              if (!_disposed) notifyListeners();
              return queued.withDispatchedAt(
                DateTime.now().millisecondsSinceEpoch,
              );
            });
            if (marked == null) return;
            if (!marked) {
              markerRefused = true;
              return;
            }
            dispatched = true;
            await currentApi.promptAsync(
              entry.sessionID,
              text: entry.text,
              model: entry.model,
              agent: entry.agent?.isNotEmpty == true ? entry.agent : null,
              variant: entry.variant?.isNotEmpty == true ? entry.variant : null,
              attachments: entry.attachments,
              agentMentions: entry.mentions,
            );
            delivered = true;
            // Until this write lands the persisted marker keeps the entry
            // out of every future flush, so a refusal or a process death
            // here can only cost a review — never a duplicate send.
            recorded = await _persistQueuedPromptRemoval(entry.id);
          }

          if (currentApi is SessionSelectionGateway) {
            await _mutateSessionSelection(entry.sessionID, (gateway) async {
              await _queueChanges;
              if (!_queue.any((queued) => queued.id == entry.id)) return false;
              // Persisted offline entries carry intentional choices. Online
              // prompt delivery alone never rewrites shared session state.
              final model = entry.model;
              if (model != null) {
                await gateway.setSessionModel(
                  entry.sessionID,
                  model,
                  entry.variant ?? '',
                );
              }
              if (!identical(api, currentApi)) {
                throw const ProductException('The connection changed.');
              }
              await _queueChanges;
              if (!_queue.any((queued) => queued.id == entry.id)) return true;
              if (entry.agent?.isNotEmpty == true) {
                await gateway.setSessionAgent(entry.sessionID, entry.agent!);
              }
              if (!identical(api, currentApi)) {
                throw const ProductException('The connection changed.');
              }
              await _queueChanges;
              if (!_queue.any((queued) => queued.id == entry.id)) return true;
              await dispatch();
              return true;
            }, requireConfirmation: false);
          } else {
            await dispatch();
          }
        } on ApiException catch (error) {
          // Once delivered, whatever failed afterwards (a session refresh, a
          // changed connection) does not undo the acceptance.
          if (!delivered && !dispatched) {
            // Preflight failure: nothing left the device, so the entry stays
            // unmarked and the next flush retries it.
            if (error.statusCode == null) {
              stop = true;
            } else {
              await _replaceQueuedPrompt(
                entry.id,
                (queued) => queued.withError(error.message),
              );
            }
          } else if (!delivered) {
            // After dispatch nothing proves non-delivery: neither prompt
            // endpoint offers an idempotency or receipt contract, and a
            // status code only says who answered, not whether the prompt
            // was enqueued first. The marker stays; only the user's review
            // moves this entry.
            await _replaceQueuedPrompt(
              entry.id,
              (queued) => queued.withError(error.message),
            );
            if (error.statusCode == null) stop = true;
          }
        } catch (error) {
          if (!delivered) {
            if (dispatched) {
              await _replaceQueuedPrompt(
                entry.id,
                (queued) => queued.withError(error.toString()),
              );
            }
            stop = true;
          }
        }
        if (delivered && !recorded) {
          // The server has this prompt; the device could not record that.
          // Say so — a plain "unconfirmed" would invite a resend that is a
          // certain duplicate — and keep the marker whatever the store says.
          _queuedPromptsAcceptedUnrecorded.add(entry.id);
          await _replaceQueuedPrompt(
            entry.id,
            (queued) => queued.withError(
              'OpenCode accepted this prompt, but this device could not '
              'update the queue.',
            ),
          );
        }
        _queuedPromptInFlight = null;
        if (!_disposed) notifyListeners();
        if (markerRefused) break;
        if (delivered) {
          if (!recorded) break;
          sent += 1;
        }
        if (stop) break;
      }
    } finally {
      _queuedPromptInFlight = null;
      _flushingOfflineQueue = false;
      if (sent > 0) {
        lastFlushedPromptCount = sent;
        lastFlushSkippedForOtherProfiles = queuedPromptCountForOtherProfiles;
        offlineFlushRevision += 1;
      }
      if (touched && !_disposed) notifyListeners();
      if (_flushOfflineQueueAgain) {
        _flushOfflineQueueAgain = false;
        if (!_disposed) unawaited(flushOfflineQueue());
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

  final _mcpRemovals = <Object, Future<void>>{};

  final _integrationCredentialMutations = <Object, Future<void>>{};

  final _integrationCommandAttempts = <Object, _IntegrationCommandAttempt>{};

  late final PendingAuthStore _pendingAuth = PendingAuthStore(store.prefs);
  final _oauthStarts = <Object>{};
  final _oauthRequestsInFlight = <Object>{};
  final _authRecoveryActions = <Object>{};
  final _authStartReservations = <String, int>{};

  static String? _authKeyProfile(Object key) => switch (key) {
    (String owner, _, _, _, _) => owner,
    (String owner, _, _, _, _, _, _) => owner,
    _ => null,
  };

  void Function() _reserveAuthStart() {
    final owner = profile!.id;
    _pendingAuth.ensureCapacity(owner);
    final reserved = _authStartReservations[owner] ?? 0;
    if (_pendingAuth.entries(owner).length + reserved >=
        PendingAuthStore.maxCount) {
      throw StateError('Sign-in recovery storage is full.');
    }
    _authStartReservations[owner] = reserved + 1;
    return () {
      final remaining = (_authStartReservations[owner] ?? 1) - 1;
      if (remaining <= 0) {
        _authStartReservations.remove(owner);
      } else {
        _authStartReservations[owner] = remaining;
      }
    };
  }

  /// Dispatches whose response supplied no recoverable attempt ID. These are
  /// local uncertainty markers, never evidence that the server did not start.
  List<({String integrationID, PendingAuthKind kind})>
  get uncertainIntegrationAuth {
    final result = <({String integrationID, PendingAuthKind kind})>[];
    void add(Object key, PendingAuthKind kind) {
      if (key case (_, _, _, _, String integrationID)) {
        try {
          final scope = _integrationCommandScope(
            integrationID,
            locationRevision,
          );
          if (scope.key == key &&
              !pendingIntegrationAuth.any(
                (entry) =>
                    entry.integrationID == integrationID && entry.kind == kind,
              )) {
            result.add((integrationID: integrationID, kind: kind));
          }
        } catch (_) {
          // Inactive/deleted profiles and other locations have no actions here.
        }
      }
    }

    for (final key in _oauthStarts) {
      if (!_oauthRequestsInFlight.contains(key)) {
        add(key, PendingAuthKind.oauth);
      }
    }
    for (final entry in _integrationCommandAttempts.entries) {
      if (entry.value.starting == null && entry.value.attemptID == null) {
        add(entry.key, PendingAuthKind.command);
      }
    }
    return List.unmodifiable(result);
  }

  /// Explicitly dismisses only an unknown local dispatch outcome. The UI must
  /// explain that this neither cancels the server attempt nor starts another.
  void forgetUncertainIntegrationAuth(
    String integrationID,
    PendingAuthKind kind, {
    required int locationRevision,
  }) {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    if (!uncertainIntegrationAuth.any(
      (entry) => entry.integrationID == integrationID && entry.kind == kind,
    )) {
      throw StateError('The uncertain sign-in is no longer available.');
    }
    if (kind == PendingAuthKind.oauth) {
      _oauthStarts.remove(scope.key);
    } else {
      _integrationCommandAttempts.remove(scope.key);
    }
    if (!_disposed) notifyListeners();
  }

  bool get integrationAuthRecoverySupported =>
      repository is IntegrationAuthRecoveryGateway;

  bool get pendingAuthPersistenceUncertain =>
      profile != null && _pendingAuth.uncertain(profile!.id);

  bool get hasPendingAuthAtOtherSource {
    final owner = profile;
    if (owner == null || !isProfileReadable(owner.id)) return false;
    return _pendingAuth
        .entries(owner.id)
        .any(
          (entry) =>
              entry.origin != owner.baseUrl ||
              entry.directory != directory ||
              entry.workspace != workspace,
        );
  }

  Future<void> retryPendingAuthPersistence() async {
    final owner = profile?.id;
    if (owner == null ||
        !isProfileReadable(owner) ||
        !await _pendingAuth.retry(owner)) {
      throw StateError('Could not save pending sign-in recovery.');
    }
    if (!_disposed) notifyListeners();
  }

  Future<void> prunePendingIntegrationAuth() async {
    final owner = profile?.id;
    if (owner == null || !isProfileReadable(owner)) return;
    await _pendingAuth.prune(owner);
    if (!_disposed) notifyListeners();
  }

  /// Local discovery only: opening Providers never checks or restarts an attempt.
  /// Other locations stay on disk until the user returns to that exact source.
  List<PendingAuthAttempt> get pendingIntegrationAuth {
    final owner = profile;
    if (owner == null || !isProfileReadable(owner.id)) return const [];
    return _pendingAuth
        .entries(owner.id)
        .where(
          (entry) =>
              entry.origin == owner.baseUrl &&
              entry.directory == directory &&
              entry.workspace == workspace,
        )
        .toList(growable: false);
  }

  Future<bool> Function(IntegrationAuthLaunch) _authRecorder(
    String integrationID,
    PendingAuthKind kind,
  ) {
    final owner = profile!;
    final origin = owner.baseUrl;
    final originalDirectory = directory;
    final originalWorkspace = workspace;
    final deletion = _promptShelfDeletionRevisions[owner.id] ?? 0;
    final deadline = DateTime.now()
        .add(PendingAuthStore.retention)
        .millisecondsSinceEpoch;
    if (PendingAuthAttempt.parse(
          PendingAuthAttempt(
            attemptID: 'pending',
            integrationID: integrationID,
            kind: kind,
            mode: IntegrationAuthMode.auto,
            profileID: owner.id,
            origin: origin,
            directory: originalDirectory,
            workspace: originalWorkspace,
            expiresAt: deadline,
          ).toJson(),
          owner.id,
        ) ==
        null) {
      throw StateError('This sign-in source cannot be safely retained.');
    }
    return (launch) async {
      // A late response may retain recovery at its ORIGINAL location, never at
      // the currently selected one. Deletion closes admission before any await.
      if (_disposed ||
          !isProfileReadable(owner.id) ||
          (_promptShelfDeletionRevisions[owner.id] ?? 0) != deletion) {
        return false;
      }
      final expiry = launch.expiresAt;
      final saved = await _pendingAuth.save(
        PendingAuthAttempt(
          attemptID: launch.attemptID,
          integrationID: integrationID,
          kind: kind,
          mode: launch.mode,
          profileID: owner.id,
          origin: origin,
          directory: originalDirectory,
          workspace: originalWorkspace,
          expiresAt: expiry != null && expiry > 0 && expiry < deadline
              ? expiry
              : deadline,
        ),
      );
      if (!_disposed) notifyListeners();
      return saved;
    };
  }

  /// Explicit OAuth start only. An uncertain dispatch is never auto-retried.
  Future<IntegrationAuthLaunch> startRecoverableIntegrationOAuth(
    String integrationID,
    String methodID, {
    Map<String, String> inputs = const {},
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    _pendingAuth.ensureCapacity(profile!.id);
    if (_oauthStarts.contains(scope.key) ||
        pendingIntegrationAuth.any(
          (e) =>
              e.integrationID == integrationID &&
              e.kind == PendingAuthKind.oauth,
        )) {
      throw StateError('Resume or cancel the existing sign-in first.');
    }
    final record = _authRecorder(integrationID, PendingAuthKind.oauth);
    final releaseReservation = _reserveAuthStart();
    _oauthStarts.add(scope.key);
    _oauthRequestsInFlight.add(scope.key);
    var dispatched = false;
    try {
      final actionRepository = await prepareActionRepository();
      scope.check();
      if (actionRepository == null ||
          actionRepository is! IntegrationAuthRecoveryGateway) {
        throw StateError('Sign-in recovery is unavailable.');
      }
      final generation = _generation;
      dispatched = true;
      final launch = await actionRepository.startIntegrationOAuth(
        integrationID,
        methodID,
        inputs: inputs,
      );
      final recoverySaved = await record(launch);
      scope.check();
      if (_lifecycleSuspended ||
          _generation != generation ||
          !identical(repository, actionRepository)) {
        throw StateError('The sign-in connection changed.');
      }
      if (!recoverySaved) {
        throw StateError('Sign-in started, but recovery could not be saved.');
      }
      _oauthStarts.remove(scope.key);
      return launch;
    } catch (_) {
      throw StateError(
        'Could not confirm sign-in. Use pending sign-in recovery; do not start again.',
      );
    } finally {
      releaseReservation();
      _oauthRequestsInFlight.remove(scope.key);
      if (!dispatched) _oauthStarts.remove(scope.key);
      if (!_disposed) notifyListeners();
    }
  }

  /// Reacquires the action transport and restores only routing, never consent.
  /// Every network operation and completion is guarded by request scope and the
  /// current transport generation. Session selection is deliberately irrelevant.
  Future<IntegrationAuthStatus> recoverIntegrationAuth(
    PendingAuthAttempt entry, {
    bool cancel = false,
    String? code,
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(
      entry.integrationID,
      locationRevision,
    );
    void check() {
      scope.check();
      if (!pendingIntegrationAuth.any(
        (e) =>
            e.key == entry.key &&
            e.mode == entry.mode &&
            e.expiresAt == entry.expiresAt,
      )) {
        throw StateError('The sign-in source changed.');
      }
    }

    check();
    if (!_authRecoveryActions.add(entry.key)) {
      throw StateError('Sign-in action is already running.');
    }
    try {
      final actionRepository = await prepareActionRepository();
      check();
      if (actionRepository == null ||
          actionRepository is! IntegrationAuthRecoveryGateway) {
        throw StateError('This server does not support sign-in recovery.');
      }
      final generation = _generation;
      final actionApi = api;
      void checkTransport() {
        check();
        if (_lifecycleSuspended ||
            generation != _generation ||
            !identical(repository, actionRepository) ||
            !identical(api, actionApi)) {
          throw StateError('The sign-in connection changed.');
        }
      }

      checkTransport();
      (actionRepository as IntegrationAuthRecoveryGateway)
          .restoreIntegrationAuthAttempt(
            integrationID: entry.integrationID,
            attemptID: entry.attemptID,
            command: entry.kind == PendingAuthKind.command,
            directory: entry.directory,
            workspace: entry.workspace,
          );
      // Expiration is local recovery retention, not evidence of server cancel.
      if (entry.expired && !cancel) {
        return const IntegrationAuthStatus(state: IntegrationAuthState.expired);
      }
      IntegrationAuthStatus result;
      if (entry.kind == PendingAuthKind.command) {
        if (actionRepository is! IntegrationCommandGateway ||
            !capabilities.integrationCommandAuth ||
            code != null) {
          throw StateError('Command recovery is unavailable.');
        }
        final gateway = actionRepository as IntegrationCommandGateway;
        if (cancel) {
          await gateway.cancelIntegrationCommand(
            entry.integrationID,
            entry.attemptID,
          );
          result = const IntegrationAuthStatus(
            state: IntegrationAuthState.expired,
          );
        } else {
          result = await gateway.integrationCommandStatus(
            entry.integrationID,
            entry.attemptID,
          );
        }
      } else {
        if (cancel) {
          await actionRepository.cancelIntegrationOAuth(entry.attemptID);
          result = const IntegrationAuthStatus(
            state: IntegrationAuthState.expired,
          );
        } else {
          if (code != null) {
            if (entry.mode != IntegrationAuthMode.code ||
                code.trim().isEmpty ||
                code.length > 8192) {
              throw StateError('Enter a valid authorization code.');
            }
            await actionRepository.completeIntegrationOAuth(
              entry.attemptID,
              code: code,
            );
            checkTransport();
          }
          result = await actionRepository.integrationOAuthStatus(
            entry.attemptID,
          );
        }
      }
      checkTransport();
      if (cancel || result.state == IntegrationAuthState.complete) {
        if (!await _pendingAuth.remove(entry)) {
          throw StateError('Could not save sign-in recovery.');
        }
        scope.check();
        if (_lifecycleSuspended ||
            generation != _generation ||
            !identical(repository, actionRepository) ||
            !identical(api, actionApi)) {
          throw StateError('The sign-in connection changed.');
        }
        _integrationCommandAttempts.remove(scope.key);
        _oauthStarts.remove(scope.key);
      }
      if (!_disposed) notifyListeners();
      // Never return provider-controlled messages to recovery UI.
      return IntegrationAuthStatus(state: result.state);
    } catch (_) {
      throw StateError(
        'Could not confirm sign-in. Check pending recovery before trying again.',
      );
    } finally {
      _authRecoveryActions.remove(entry.key);
      if (!_disposed) notifyListeners();
    }
  }

  /// Explicit local dismissal does not claim server cancellation or revocation.
  Future<void> forgetIntegrationAuth(
    PendingAuthAttempt entry, {
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(
      entry.integrationID,
      locationRevision,
    );
    if (!pendingIntegrationAuth.any((e) => e.key == entry.key) ||
        _authRecoveryActions.contains(entry.key)) {
      throw StateError('Sign-in is unavailable.');
    }
    if (!await _pendingAuth.remove(entry)) {
      throw StateError('Could not remove sign-in recovery.');
    }
    scope.check();
    _integrationCommandAttempts.remove(scope.key);
    _oauthStarts.remove(scope.key);
    if (!_disposed) notifyListeners();
  }

  /// Captures a request's selected source, not its session. Attempt ownership
  /// survives transport replacement and leaving/revisiting the same location;
  /// each request still pins the current profile objects and location revision.
  ({Object key, int deletion, void Function() check}) _integrationCommandScope(
    String integrationID,
    int expectedLocationRevision,
  ) {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null || connected == null || integrationID.trim().isEmpty) {
      throw StateError('Command sign-in is unavailable.');
    }
    final owner = saved.id;
    final origin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _promptShelfDeletionRevisions[owner] ?? 0;
    final identity = (saved.username, saved.flavor);
    final connectedIdentity = (connected.username, connected.flavor);
    void check() {
      if (_disposed ||
          !isProfileReadable(owner) ||
          store.activeId != owner ||
          !identical(profile, saved) ||
          !identical(_connectedProfile, connected) ||
          connected.id != owner ||
          saved.baseUrl != origin ||
          connected.baseUrl != connectedOrigin ||
          origin != connectedOrigin ||
          (saved.username, saved.flavor) != identity ||
          (connected.username, connected.flavor) != connectedIdentity ||
          identity != connectedIdentity ||
          validateServerProfileUrl(origin) != null ||
          locationRevision != expectedLocationRevision ||
          (directory, workspace) != location ||
          (_promptShelfDeletionRevisions[owner] ?? 0) != deletion) {
        throw StateError('The command sign-in location changed.');
      }
    }

    check();
    return (
      key: (owner, origin, identity, location, integrationID),
      deletion: deletion,
      check: check,
    );
  }

  Future<T> _withIntegrationCommandTransport<T>(
    void Function() checkScope,
    Future<T> Function(
      ServerOperationsGateway repository,
      IntegrationCommandGateway gateway,
      void Function() checkTransport,
    )
    action,
  ) async {
    try {
      checkScope();
      final actionRepository = await prepareActionRepository();
      checkScope();
      final actionApi = api;
      final generation = _generation;
      void checkTransport() {
        checkScope();
        if (_lifecycleSuspended ||
            actionApi == null ||
            actionRepository == null ||
            generation != _generation ||
            !identical(api, actionApi) ||
            !identical(repository, actionRepository) ||
            !capabilities.integrationCommandAuth) {
          throw StateError('The command sign-in connection changed.');
        }
      }

      checkTransport();
      if (actionRepository == null ||
          actionRepository is! IntegrationCommandGateway) {
        throw StateError('Command sign-in is unavailable.');
      }
      final result = await action(
        actionRepository,
        actionRepository as IntegrationCommandGateway,
        checkTransport,
      );
      checkTransport();
      return result;
    } catch (_) {
      // Command output and server errors may contain provider credentials.
      throw StateError(
        'Could not confirm command sign-in. Refresh and try again.',
      );
    }
  }

  /// Starts executable authentication on the selected server. The UI MUST obtain
  /// explicit user confirmation before calling this; discovery is not consent.
  /// Identical concurrent taps share a launch. An uncertain dispatch blocks
  /// retries rather than risking a second server-side process.
  Future<IntegrationAuthLaunch> startIntegrationCommand(
    String integrationID,
    String methodID, {
    String? label,
    required int locationRevision,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    if (methodID.trim().isEmpty ||
        (label != null &&
            (label.trim().isEmpty ||
                label.trim().length > 128 ||
                RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(label)))) {
      throw StateError(
        'Enter a valid command method and optional sign-in label.',
      );
    }
    final existing = _integrationCommandAttempts[scope.key];
    if (existing != null) {
      final starting = existing.starting;
      if (existing.methodID == methodID &&
          existing.deletionRevision == scope.deletion &&
          starting != null) {
        final result = await starting;
        scope.check();
        result.check();
        return result.launch;
      }
      throw StateError(
        'A command sign-in may already be running at this location.',
      );
    }

    if (pendingIntegrationAuth.any(
      (entry) =>
          entry.integrationID == integrationID &&
          entry.kind == PendingAuthKind.command,
    )) {
      throw StateError('Resume or cancel the existing command sign-in first.');
    }
    final record = _authRecorder(integrationID, PendingAuthKind.command);
    final releaseReservation = _reserveAuthStart();
    final pending = _IntegrationCommandAttempt(methodID, scope.deletion);
    _integrationCommandAttempts[scope.key] = pending;
    var dispatched = false;
    final operation =
        _withIntegrationCommandTransport<
          ({IntegrationAuthLaunch launch, void Function() check})
        >(scope.check, (actionRepository, gateway, checkTransport) async {
          final integrations = await actionRepository.listIntegrations();
          checkTransport();
          final valid = integrations.any(
            (integration) =>
                integration.id == integrationID &&
                integration.methods.any(
                  (method) => method.id == methodID && method.type == 'command',
                ),
          );
          if (!valid) {
            throw StateError('The command sign-in method is unavailable.');
          }
          dispatched = true;
          final launch = await gateway.startIntegrationCommand(
            integrationID,
            methodID,
            label: label?.trim(),
          );
          final recoverySaved = await record(launch);
          checkTransport();
          if (launch.attemptID.trim().isEmpty) {
            throw StateError('The command sign-in attempt is unavailable.');
          }
          // A stale completion leaves the original reservation uncertain. It
          // cannot attach an attempt to the newly selected profile/location.
          pending.attemptID = launch.attemptID;
          if (!recoverySaved) {
            throw StateError(
              'Command sign-in started, but recovery could not be saved.',
            );
          }
          return (launch: launch, check: checkTransport);
        });
    pending.starting = operation;
    try {
      final result = await operation;
      scope.check();
      result.check();
      return result.launch;
    } finally {
      releaseReservation();
      pending.starting = null;
      // Before dispatch there is only a reservation, not a possible attempt.
      if (!dispatched &&
          identical(_integrationCommandAttempts[scope.key], pending)) {
        _integrationCommandAttempts.remove(scope.key);
      }
      if (!_disposed) notifyListeners();
    }
  }

  String? pendingIntegrationCommand(
    String integrationID, {
    required int locationRevision,
  }) {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    scope.check();
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.kind == PendingAuthKind.command) {
        return entry.attemptID;
      }
    }
    return _integrationCommandAttempts[scope.key]?.attemptID;
  }

  Future<IntegrationAuthStatus> integrationCommandStatus(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) async {
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.attemptID == attemptID &&
          entry.kind == PendingAuthKind.command) {
        return recoverIntegrationAuth(
          entry,
          locationRevision: locationRevision,
        );
      }
    }
    return _integrationCommandAttemptAction<IntegrationAuthStatus>(
      integrationID,
      attemptID,
      locationRevision: locationRevision,
      action: (gateway) =>
          gateway.integrationCommandStatus(integrationID, attemptID),
      terminal: (status) => status.state != IntegrationAuthState.pending,
    );
  }

  Future<void> cancelIntegrationCommand(
    String integrationID,
    String attemptID, {
    required int locationRevision,
  }) async {
    for (final entry in pendingIntegrationAuth) {
      if (entry.integrationID == integrationID &&
          entry.attemptID == attemptID &&
          entry.kind == PendingAuthKind.command) {
        await recoverIntegrationAuth(
          entry,
          cancel: true,
          locationRevision: locationRevision,
        );
        return;
      }
    }
    await _integrationCommandAttemptAction<bool>(
      integrationID,
      attemptID,
      locationRevision: locationRevision,
      action: (gateway) async {
        await gateway.cancelIntegrationCommand(integrationID, attemptID);
        return true;
      },
      terminal: (_) => true,
    );
  }

  Future<T> _integrationCommandAttemptAction<T>(
    String integrationID,
    String attemptID, {
    required int locationRevision,
    required Future<T> Function(IntegrationCommandGateway) action,
    required bool Function(T) terminal,
  }) async {
    final scope = _integrationCommandScope(integrationID, locationRevision);
    final pending = _integrationCommandAttempts[scope.key];
    void checkOwnership() {
      scope.check();
      if (attemptID.trim().isEmpty ||
          pending == null ||
          pending.attemptID != attemptID ||
          pending.deletionRevision != scope.deletion ||
          !identical(_integrationCommandAttempts[scope.key], pending)) {
        throw StateError(
          'The command sign-in attempt is unavailable at this location.',
        );
      }
    }

    checkOwnership();
    void Function()? checkCompletion;
    final result = await _withIntegrationCommandTransport<T>(checkOwnership, (
      _,
      gateway,
      checkTransport,
    ) async {
      checkCompletion = checkTransport;
      final result = await action(gateway);
      checkTransport();
      return result;
    });
    checkCompletion?.call();
    if (terminal(result)) _integrationCommandAttempts.remove(scope.key);
    return result;
  }

  Future<void> activateIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _mutateIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
    mutate: (gateway) => gateway.activateCredential(credentialID),
  );

  Future<void> renameIntegrationCredential(
    String integrationID,
    String credentialID,
    String label, {
    required int locationRevision,
  }) async {
    final trimmed = label.trim();
    if (trimmed.isEmpty ||
        trimmed.length > 128 ||
        RegExp(r'[\x00-\x1f\x7f-\x9f]').hasMatch(label)) {
      throw StateError(
        'Enter a label of 1–128 characters without control characters.',
      );
    }
    return _mutateIntegrationCredential(
      integrationID,
      credentialID,
      locationRevision: locationRevision,
      mutate: (gateway) => gateway.renameCredential(credentialID, trimmed),
    );
  }

  Future<void> removeIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
  }) => _mutateIntegrationCredential(
    integrationID,
    credentialID,
    locationRevision: locationRevision,
    allowAbsent: true,
    mutate: (gateway) => gateway.removeCredential(credentialID),
  );

  /// Credential metadata is server-owned. Serialize intent, not inferred active
  /// state; neither a successful response nor removal selects a replacement.
  Future<void> _mutateIntegrationCredential(
    String integrationID,
    String credentialID, {
    required int locationRevision,
    bool allowAbsent = false,
    required Future<void> Function(IntegrationCredentialGateway) mutate,
  }) async {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null ||
        connected == null ||
        integrationID.trim().isEmpty ||
        credentialID.trim().isEmpty) {
      throw StateError('Credential management is unavailable.');
    }
    final owner = saved.id;
    final savedOrigin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _promptShelfDeletionRevisions[owner] ?? 0;
    void checkScope() {
      if (!isProfileReadable(owner) ||
          store.activeId != owner ||
          !identical(profile, saved) ||
          !identical(_connectedProfile, connected) ||
          connected.id != owner ||
          saved.baseUrl != savedOrigin ||
          connected.baseUrl != connectedOrigin ||
          savedOrigin != connectedOrigin ||
          this.locationRevision != locationRevision ||
          (directory, workspace) != location ||
          (_promptShelfDeletionRevisions[owner] ?? 0) != deletion) {
        throw StateError(
          'The credential location changed. Refresh and try again.',
        );
      }
    }

    checkScope();
    // Integration is deliberately not part of the key: the same credential
    // cannot be mutated concurrently through two claimed integration owners.
    // Labels are not coalesced; each queued rename keeps its own intent.
    final key = (
      owner,
      saved,
      connected,
      savedOrigin,
      locationRevision,
      location,
      deletion,
      credentialID,
    );
    final previous = _integrationCredentialMutations[key];
    void Function()? checkCompletion;
    final operation = () async {
      try {
        if (previous != null) {
          try {
            await previous;
          } catch (_) {
            // A failed predecessor does not authorize or discard this intent.
          }
          checkScope();
        }
        final actionRepository = await prepareActionRepository();
        checkScope();
        final actionApi = api;
        final generation = _generation;
        void checkTransport() {
          checkScope();
          if (_lifecycleSuspended ||
              actionApi == null ||
              actionRepository == null ||
              generation != _generation ||
              !identical(api, actionApi) ||
              !identical(repository, actionRepository)) {
            throw StateError(
              'The credential connection changed. Refresh and try again.',
            );
          }
        }

        checkCompletion = checkTransport;
        checkTransport();
        if (actionRepository == null ||
            !capabilities.integrationCredentials ||
            actionRepository is! IntegrationCredentialGateway) {
          throw StateError('Credential management is unavailable.');
        }
        final integrations = await actionRepository.listIntegrations();
        checkTransport();
        if (!capabilities.integrationCredentials) {
          throw StateError('Credential management is unavailable.');
        }
        final belongs = integrations.any(
          (integration) =>
              integration.id == integrationID &&
              integration.credentialIDs.contains(credentialID),
        );
        if (!belongs) {
          // Absence is idempotent only when the credential is absent everywhere,
          // not when it is present under a different integration.
          final presentElsewhere = integrations.any(
            (integration) => integration.credentialIDs.contains(credentialID),
          );
          if (allowAbsent && !presentElsewhere) return;
          throw StateError(
            'The credential is unavailable. Refresh and try again.',
          );
        }
        await mutate(actionRepository as IntegrationCredentialGateway);
        checkTransport();
      } catch (_) {
        checkScope();
        throw StateError(
          'Could not confirm the credential change. Refresh and try again.',
        );
      }
    }();
    _integrationCredentialMutations[key] = operation;
    try {
      await operation;
      checkScope();
      checkCompletion?.call();
    } finally {
      if (identical(_integrationCredentialMutations[key], operation)) {
        _integrationCredentialMutations.remove(key);
      }
    }
  }

  /// Removes only a currently listed runtime entry. The caller owns refetching
  /// its inventory; this never changes saved configuration or publishes state.
  Future<void> removeMcpServer(
    String name, {
    required int locationRevision,
  }) async {
    final saved = profile;
    final connected = _connectedProfile;
    if (saved == null || connected == null || name.trim().isEmpty) {
      throw StateError('MCP removal is unavailable.');
    }
    final owner = saved.id;
    final savedOrigin = saved.baseUrl;
    final connectedOrigin = connected.baseUrl;
    final location = (directory, workspace);
    final deletion = _promptShelfDeletionRevisions[owner] ?? 0;
    bool currentScope() =>
        isProfileReadable(owner) &&
        store.activeId == owner &&
        identical(profile, saved) &&
        identical(_connectedProfile, connected) &&
        connected.id == owner &&
        saved.baseUrl == savedOrigin &&
        connected.baseUrl == connectedOrigin &&
        savedOrigin == connectedOrigin &&
        this.locationRevision == locationRevision &&
        (directory, workspace) == location &&
        (_promptShelfDeletionRevisions[owner] ?? 0) == deletion;
    void checkScope() {
      if (!currentScope()) {
        throw StateError('The MCP location changed. Refresh and try again.');
      }
    }

    checkScope();
    // A request is profile/location/name-scoped, not session-scoped. Keep its
    // slot across transport recovery so a second tap cannot dispatch twice.
    final key = (
      owner,
      saved,
      connected,
      savedOrigin,
      locationRevision,
      location,
      deletion,
      name,
    );
    final pending = _mcpRemovals[key];
    if (pending != null) return pending;
    final operation = () async {
      try {
        final actionRepository = await prepareActionRepository();
        checkScope();
        final actionApi = api;
        final generation = _generation;
        void checkTransport() {
          checkScope();
          if (_lifecycleSuspended ||
              actionApi == null ||
              actionRepository == null ||
              generation != _generation ||
              !identical(api, actionApi) ||
              !identical(repository, actionRepository)) {
            throw StateError(
              'The MCP connection changed. Refresh and try again.',
            );
          }
        }

        checkTransport();
        if (actionRepository == null ||
            !capabilities.mcpRuntimeRemovals ||
            actionRepository is! McpRemovalGateway) {
          throw StateError('MCP removal is unavailable.');
        }
        final inventory = await actionRepository.listMcpServers();
        checkTransport();
        if (!capabilities.mcpRuntimeRemovals) {
          throw StateError('MCP removal is unavailable.');
        }
        if (!inventory.any((entry) => entry.name == name)) return;
        await (actionRepository as McpRemovalGateway).removeMcpServer(name);
        checkTransport();
      } catch (_) {
        // Never propagate inventory/configuration contents or raw server errors.
        checkScope();
        throw StateError(
          'Could not confirm MCP removal. Refresh and try again.',
        );
      } finally {
        _mcpRemovals.remove(key);
      }
    }();
    _mcpRemovals[key] = operation;
    return operation;
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

  Future<Session> createSession() => _createSession();

  Future<Session> _createSession({bool Function()? scopeIsCurrent}) async {
    final currentApi = await _requireActionTransport();
    if (scopeIsCurrent != null && !scopeIsCurrent()) {
      throw const ProductException(
        'The connection or location changed. Start the task again.',
      );
    }
    final generation = _generation;
    final revision = _sessionRevision;
    final session = currentApi is SessionSelectionGateway
        ? await (currentApi as SessionSelectionGateway).createSelectedSession(
            SessionSelection(
              model: selectedModel,
              variant: selectedVariant,
              agent: selectedAgent,
            ),
          )
        : await currentApi.createSession();
    if (scopeIsCurrent != null && !scopeIsCurrent()) {
      throw const ProductException(
        'The connection or location changed while the session was created. '
        'Find it in the original worktree before using it.',
      );
    }
    if (_isCurrent(generation, currentApi) &&
        (_sessionRevisions[session.id] ?? 0) <= revision) {
      _markSessionChanged(session.id);
      sessionsById[session.id] = session;
      _sessionInventoryIDs.add(session.id);
      notifyListeners();
    }
    return session;
  }

  Future<void> renameSession(String sessionID, String title) async {
    final currentApi = await _requireActionTransport();
    final generation = _generation;
    await currentApi.renameSession(sessionID, title);
    if (_isCurrent(generation, currentApi)) {
      _markSessionChanged(sessionID);
      await _refreshOneSession(sessionID);
    }
  }

  Future<void> deleteSession(String sessionID) async {
    final currentApi = await _requireActionTransport();
    final generation = _generation;
    await currentApi.deleteSession(sessionID);
    if (_isCurrent(generation, currentApi)) _removeSession(sessionID);
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
    // truth; the writer itself skips unchanged payloads. Profile deletion
    // suspends this: the sessions it would republish belong to the profile
    // being erased.
    if (!_disposed && !_widgetSnapshotSuspended && !isIsolated) {
      // Retained so a caller that must observe the settled snapshot — profile
      // deletion — can wait for this write instead of racing it.
      final write = _widgetSnapshot.update(
        sessions: sortedSessions(),
        busySessions: busySessions,
        connected: status == StreamStatus.connected,
        profileID: _connectedProfile?.id ?? store.activeId ?? '',
      );
      _pendingWidgetSnapshotWrite = write;
      unawaited(write);
      _publishLiveStatus();
    }
  }

  /// The ongoing "OpenCode is connected" notification's content, derived
  /// from the same truth the Activity tab shows: how many sessions run, how
  /// many requests wait, the most recently active busy session's title, and
  /// a generic sentence for the tool it is running.
  @visibleForTesting
  LiveStatus liveStatus() {
    Session? current;
    for (final id in busySessions) {
      final session = sessionsById[id];
      if (session == null) continue;
      if (current == null ||
          (session.time?.updated ?? 0) > (current.time?.updated ?? 0)) {
        current = session;
      }
    }
    final title = current?.title?.trim();
    final detail = current == null ? null : _runningToolDetail[current.id];
    return LiveStatus(
      runningCount: busySessions.length,
      pendingCount: permissions.length + questions.length + forms.length,
      title: title == null || title.isEmpty ? null : title,
      detail: detail,
    );
  }

  void _publishLiveStatus() {
    if (_disposed || !keepLiveInBackground || !backgroundLive.active) return;
    _runningToolDetail.removeWhere((id, _) => !busySessions.contains(id));
    unawaited(backgroundLive.publishLiveStatus(liveStatus()));
  }

  /// Names what a tool is doing without repeating its input: no command,
  /// path, query, or URL reaches the notification shade.
  static String toolSentence(String tool) {
    switch (tool.toLowerCase()) {
      case 'bash':
      case 'shell':
        return 'Running a command…';
      case 'edit':
      case 'write':
      case 'patch':
      case 'multiedit':
      case 'apply_patch':
        return 'Editing files…';
      case 'read':
        return 'Reading files…';
      case 'grep':
      case 'glob':
      case 'list':
      case 'ls':
        return 'Searching files…';
      case 'webfetch':
      case 'websearch':
        return 'Browsing the web…';
      case 'task':
        return 'Running a subagent…';
      case 'todowrite':
      case 'todoread':
        return 'Planning…';
      case '':
        return 'Working…';
      default:
        return 'Running $tool…';
    }
  }

  List<Session> sortedSessions() {
    final pins = pinnedSessionIDs;
    final list =
        sessionsById.values
            .where(
              (s) =>
                  s.parentID == null &&
                  !s.archived &&
                  (!_sessionInventoryInitialized ||
                      _sessionInventoryIDs.contains(s.id)),
            )
            .toList()
          ..sort((a, b) {
            final pinOrder =
                (pins.contains(b.id) ? 1 : 0) - (pins.contains(a.id) ? 1 : 0);
            if (pinOrder != 0) return pinOrder;
            final au = a.time?.updated ?? a.time?.created ?? 0;
            final bu = b.time?.updated ?? b.time?.created ?? 0;
            return bu.compareTo(au);
          });
    return list;
  }

  late final _sessionPins = SessionPinStore(store.prefs);
  final PromptShelfStore _promptShelf;
  final _promptShelfDeletionRevisions = <String, int>{};
  String get promptShelfProfileID => (_connectedProfile ?? profile)?.id ?? '';
  bool get canUsePromptShelf => isProfileReadable(promptShelfProfileID);
  List<StashedPrompt> get promptStash =>
      canUsePromptShelf ? _promptShelf.stashes(promptShelfProfileID) : const [];
  List<String> get sentPromptHistory =>
      canUsePromptShelf ? _promptShelf.history(promptShelfProfileID) : const [];

  /// The shelf belongs to a profile; one operation also belongs to the selected
  /// location and that profile's deletion epoch. It does not belong to a server
  /// request/session or replaceable API/repository generation: these are local
  /// files, usable offline and across same-location transport recovery. A chat
  /// must separately guard its destination session/composer before insertion.
  bool Function() _promptShelfScope(int expectedLocation) {
    if (!canUsePromptShelf) throw StateError('The prompt location changed');
    final owner = promptShelfProfileID;
    final savedProfile = store.profiles.firstWhere(
      (candidate) => candidate.id == owner,
    );
    final savedOrigin = savedProfile.baseUrl;
    final activeID = store.activeId;
    final origin = (_connectedProfile ?? profile)?.baseUrl;
    final location = (directory, workspace);
    final deletion = _promptShelfDeletionRevisions[owner] ?? 0;
    bool current() =>
        canUsePromptShelf &&
        locationRevision == expectedLocation &&
        promptShelfProfileID == owner &&
        store.activeId == activeID &&
        store.profiles.any((candidate) => identical(candidate, savedProfile)) &&
        savedProfile.baseUrl == savedOrigin &&
        (_connectedProfile ?? profile)?.baseUrl == origin &&
        (directory, workspace) == location &&
        (_promptShelfDeletionRevisions[owner] ?? 0) == deletion;
    _checkPromptShelfScope(current);
    return current;
  }

  static void _checkPromptShelfScope(bool Function() current) {
    if (!current()) throw StateError('The prompt location changed');
  }

  /// Lazily migrate without consuming saved prompts. Empty shelves do not
  /// access a vault/root or write preferences, even on the live default path.
  Future<List<String>> preparePromptStash({
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    List<StashedPrompt>? before;
    try {
      before = _promptShelf.stashes(owner);
    } on StateError {
      // A previous refused write may need the serialized metadata reload below.
    }
    final deferred = await _promptShelf.migrateAttachments(
      owner,
      checkCurrent: () => _checkPromptShelfScope(current),
    );
    _checkPromptShelfScope(current);
    if (!listEquals(before, _promptShelf.stashes(owner))) notifyListeners();
    return deferred;
  }

  Future<DraftAttachmentRecovery> restorePromptStashAttachments(
    String id, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    final prompt = _promptShelf.stashes(owner).firstWhere((p) => p.id == id);
    final sameLocation =
        sameDirectoryPath(prompt.directory, directory) &&
        prompt.workspace == workspace;
    if (prompt.locationBound && !sameLocation) {
      throw StateError('The saved prompt belongs to another location');
    }
    void checkCurrent() {
      _checkPromptShelfScope(current);
      // IDs may be removed and reused while this read waits in the file queue.
      if (!_promptShelf.stashes(owner).any((p) => identical(p, prompt))) {
        throw StateError('The saved prompt changed');
      }
    }

    final recovery = await _promptShelf.restoreAttachments(
      owner,
      id,
      sameLocation: sameLocation,
      checkCurrent: checkCurrent,
    );
    checkCurrent();
    return recovery;
  }

  Future<void> savePromptStash(
    StashedPrompt prompt, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    try {
      await _promptShelf.stash(
        owner,
        prompt,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
      _checkPromptShelfScope(current);
    } finally {
      if (current()) notifyListeners();
    }
  }

  Future<void> removePromptStash(
    String id, {
    required int locationRevision,
  }) async {
    final current = _promptShelfScope(locationRevision);
    final owner = promptShelfProfileID;
    try {
      await _promptShelf.remove(
        owner,
        id,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
      _checkPromptShelfScope(current);
    } finally {
      // Removal may have committed metadata before file cleanup failed.
      if (current()) notifyListeners();
    }
  }

  Future<void> rememberSentPrompt(String profileID, String text) async {
    // A network send can finish after its server profile has been deleted.
    // Never recreate the removed profile's local history in that callback.
    final deletion = _promptShelfDeletionRevisions[profileID] ?? 0;
    bool current() =>
        isProfileReadable(profileID) &&
        (_promptShelfDeletionRevisions[profileID] ?? 0) == deletion;
    if (!current()) return;
    try {
      await _promptShelf.recordSent(
        profileID,
        text,
        checkCurrent: () => _checkPromptShelfScope(current),
      );
    } catch (_) {
      if (current()) rethrow;
    }
  }

  String get _pinProfile => (_connectedProfile ?? profile)?.id ?? '';
  String get _pinScope => SessionPinStore.scope(directory, workspace);
  Set<String> get pinnedSessionIDs =>
      _pinProfile.isEmpty ? const {} : _sessionPins.ids(_pinProfile, _pinScope);
  bool isSessionPinned(String id) => pinnedSessionIDs.contains(id);
  bool get canPinSessions =>
      _pinProfile.isNotEmpty && !_deletingReadProfiles.contains(_pinProfile);

  Future<void> setSessionPinned(
    String id,
    bool pinned, {
    required int locationRevision,
  }) async {
    if (!canPinSessions || this.locationRevision != locationRevision) {
      throw StateError('The session location changed');
    }
    final profileID = _pinProfile;
    await _sessionPins.setPinned(profileID, _pinScope, id, pinned);
    if (!_disposed) notifyListeners();
  }

  bool get pinnedSessionsLoadFailed =>
      pinnedSessionIDs.any((id) => sessionDetailsErrors.containsKey(id));

  Future<void> _refreshPinnedSessions() async {
    final currentApi = api;
    final generation = _generation;
    if (currentApi == null) return;
    for (final id in pinnedSessionIDs) {
      if (!_isCurrent(generation, currentApi)) return;
      if (!_sessionInventoryIDs.contains(id) ||
          sessionDetailsErrors.containsKey(id)) {
        await _refreshOneSession(id);
      }
    }
  }

  List<Session> archivedSessions() {
    final list =
        sessionsById.values
            .where(
              (session) =>
                  session.archived &&
                  (!_sessionInventoryInitialized ||
                      _sessionInventoryIDs.contains(session.id)),
            )
            .toList()
          ..sort(
            (a, b) => (b.time?.archived ?? 0).compareTo(a.time?.archived ?? 0),
          );
    return list;
  }

  /// Confirms a typed [directory] exists on the connected server without
  /// touching the active location. Returns a plain-sentence problem, or null
  /// when the folder can be opened. The listing runs on a throwaway
  /// transport so a wrong path never rescopes live requests.
  Future<String?> probeProjectFolder(String directory) async {
    final profile = _connectedProfile;
    if (profile == null || isIsolated) return 'OpenCode is not connected.';
    final problem = workspaceDirectoryProblem(directory);
    if (problem != null) return problem;
    final pair = _buildTransportPair(profile);
    pair.gateway.setLocation(
      directory: normalizeDirectoryPath(directory),
      workspace: null,
    );
    try {
      await pair.gateway.listFiles('.');
      return null;
    } catch (_) {
      return 'That folder was not found on the server. Check the path and '
          'try again.';
    } finally {
      pair.gateway.close();
    }
  }

  Future<void> selectLocation({String? directory, String? workspace}) =>
      _selectLocation(directory: directory, workspace: workspace);

  /// Rescopes onto the folder of a conversation that already exists there,
  /// even when that folder is the server's home. Reading or continuing an
  /// earlier conversation is the one thing still allowed in a home folder;
  /// [workspaceChoiceRequired] stays true, so Workspace keeps asking for a
  /// project folder before any new session starts.
  Future<void> selectLocationForExistingSession({
    String? directory,
    String? workspace,
  }) => _selectLocation(
    directory: directory,
    workspace: workspace,
    allowProtectedDirectory: true,
  );

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
    bool allowProtectedDirectory = false,
  }) async {
    final profile = _connectedProfile;
    if (profile == null || api == null) return;
    if (profile.backend == ServerBackend.codex) {
      directory ??= profile.codexDirectory;
      if (workspace != null ||
          validateCodexProjectDirectory(directory) != null) {
        locationError = 'Choose a valid project folder for this connection.';
        notifyListeners();
        return;
      }
    } else if (directory != null &&
        !allowProtectedDirectory &&
        isProtectedWorkspaceDirectory(directory)) {
      // Never rescope onto a home folder or the filesystem root: the server
      // would watch and scan everything underneath it.
      locationError = workspaceDirectoryProblem(directory);
      notifyListeners();
      return;
    }
    if (!preserveNotice) locationNotice = null;
    if (this.directory == directory && this.workspace == workspace) {
      if (isProtectedWorkspaceDirectory(directory)) {
        notifyListeners();
        return;
      }
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
    // Rebuild through the flavor-aware builder: a v2 profile must not be
    // rescoped onto a v1 transport, whose health check cannot succeed and
    // reads to the user as a rotated password. Guarded by
    // test/connection_transport_factory_guard_test.dart, because this fix
    // has already been lost to a merge once.
    final pair = _buildTransportPair(profile);
    final currentApi = pair.gateway
      ..setLocation(directory: directory, workspace: workspace);
    final currentRepository = pair.operations
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
    final savedLibrary = _modelLibrary;
    final savedSessionModels = sessionModels;
    _clearLocationData();
    _modelLibrary = savedLibrary;
    sessionModels = savedSessionModels;
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
    if (locationError == null && !isProtectedWorkspaceDirectory(directory)) {
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
    if (_pendingLocationRevalidation) unawaited(revalidateRestoredLocation());
  }

  /// Captured when a task sheet opens, before the user can press Start.
  Object get isolatedTaskScope => (
    this,
    _connectedProfile?.id,
    _connectedProfile?.baseUrl,
    connectionRevision,
    locationRevision,
    directory,
    workspace,
  );

  /// Starts an explicit "new task in a fresh worktree" run for [project] on
  /// the connected profile. The launch subscribes to [events] before the
  /// create call, and [openSessionInWorktree] is the only route into a
  /// session. Requires [ServerCapabilities.worktreeCreate]; the caller owns
  /// the returned launch and disposes it.
  IsolatedTaskLaunch startIsolatedTask({
    required WorkspaceProject project,
    required Object expectedScope,
    String? name,
    Duration readinessTimeout = const Duration(seconds: 45),
  }) {
    if (expectedScope != isolatedTaskScope) {
      throw const ProductException(
        'The connection or location changed. Reopen the task sheet.',
      );
    }
    if (!capabilities.worktreeCreate) {
      throw const ProductException(
        'Creating worktrees is not available on this connection.',
      );
    }
    final profileID = _connectedProfile?.id;
    bool launchScopeIsCurrent() =>
        profileID != null && expectedScope == isolatedTaskScope;
    final trimmed = name?.trim();
    final requestedName = trimmed?.isNotEmpty == true ? trimmed : null;
    var openingAttempted = false;
    final launch = IsolatedTaskLaunch(
      project: project,
      requestedName: requestedName,
      events: events,
      readinessTimeout: readinessTimeout,
      create: () async {
        if (!launchScopeIsCurrent()) {
          throw const ProductException(
            'The connection changed. Start the task again.',
          );
        }
        final currentRepository = await prepareActionRepository();
        if (currentRepository == null || !launchScopeIsCurrent()) {
          throw const ProductException(
            'OpenCode is reconnecting. Try again shortly.',
          );
        }
        // Revalidate the cached project against this exact server scope before
        // the first mutation. A sheet may originate from an older catalog.
        final projects = await currentRepository.listProjects();
        if (!launchScopeIsCurrent() ||
            !identical(repository, currentRepository)) {
          throw const ProductException(
            'The connection or location changed. Reopen the task sheet.',
          );
        }
        if (!projects.any(
          (candidate) =>
              candidate.id == project.id &&
              sameDirectoryPath(candidate.directory, project.directory),
        )) {
          throw const ProductException(
            'The project could not be confirmed. Reopen it before starting.',
          );
        }
        return currentRepository.createWorktree(
          projectDirectory: project.directory,
          name: requestedName,
        );
      },
      open: (directory) {
        if (!openingAttempted && !launchScopeIsCurrent()) {
          throw const ProductException(
            'The connection or location changed. Start the task again.',
          );
        }
        // A retry is an explicit choice to reopen this same destination;
        // the first attempt may already have switched into the worktree.
        openingAttempted = true;
        return openSessionInWorktree(
          project: project,
          directory: directory,
          profileID: profileID,
        );
      },
    );
    unawaited(launch.start());
    return launch;
  }

  /// Switches scope to [directory] and creates a blank session there, only
  /// while the same profile is connected and the switched scope still
  /// resolves to [project]. A mismatch throws before any session exists, so
  /// a connection or scope change cannot retarget the task at another
  /// project. Nothing is sent to the new session.
  Future<Session> openSessionInWorktree({
    required WorkspaceProject project,
    required String directory,
    String? profileID,
  }) async {
    final profile = _connectedProfile;
    if (profile == null || (profileID != null && profile.id != profileID)) {
      throw const ProductException(
        'The connection changed. Start the task again.',
      );
    }
    if (!capabilities.worktreeCreate) {
      throw const ProductException(
        'Creating worktrees is not available on this connection.',
      );
    }
    // _selectLocation changes both revisions synchronously before hydration.
    // Pin the intended revisions before invoking it, never adopt a later
    // selection that happens to use the same directory.
    final changesLocation = this.directory != directory || workspace != null;
    final revision = locationRevision + (changesLocation ? 1 : 0);
    final connection = connectionRevision + (changesLocation ? 1 : 0);
    bool scopeIsCurrent() =>
        _connectedProfile?.id == profile.id &&
        _connectedProfile?.baseUrl == profile.baseUrl &&
        locationRevision == revision &&
        connectionRevision == connection &&
        workspace == null &&
        sameDirectoryPath(this.directory, directory);
    await selectLocation(directory: directory);
    if (!scopeIsCurrent()) {
      throw const ProductException(
        'OpenCode did not switch to the new worktree.',
      );
    }
    final currentRepository = await prepareActionRepository();
    if (currentRepository == null || !scopeIsCurrent()) {
      throw const ProductException(
        'OpenCode is reconnecting. Try again shortly.',
      );
    }
    final current = await currentRepository.loadCurrentProject();
    if (current == null || current.id != project.id) {
      throw const ProductException(
        'The worktree could not be confirmed for this project. '
        'No session was started.',
      );
    }
    if (!scopeIsCurrent() || !identical(repository, currentRepository)) {
      throw const ProductException(
        'The location changed before the session could start.',
      );
    }
    final session = await _createSession(scopeIsCurrent: scopeIsCurrent);
    final sessionProject = session.projectID?.trim() ?? '';
    final sessionDirectory = session.directory;
    if (session.workspaceID != null ||
        (sessionProject.isNotEmpty && sessionProject != project.id) ||
        (sessionDirectory != null &&
            !sameDirectoryPath(sessionDirectory, directory))) {
      throw ProductException(
        'The session opened outside the new worktree, so it was not started '
        'here. Find it in the session list before using it.',
      );
    }
    return session;
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

  bool get supportsStagedRevert =>
      repository is StagedRevertGateway ||
      (repository == null && serverFlavor == ServerFlavor.v2);

  int sessionHistoryRevision(String id) => _historyRevisions[id] ?? 0;
  bool sessionRevertSaving(String id) => _revertMutations.containsKey(id);
  Object get _revertScope => (api, repository, _generation, locationRevision);

  SessionRevertReview reviewSessionRevert(String id) => SessionRevertReview(
    id,
    _revertScope,
    sessionHistoryRevision(id),
    sessionsById[id]?.stagedRevert,
  );

  bool isRevertReviewCurrent(SessionRevertReview review) =>
      !_disposed &&
      review.scope == _revertScope &&
      !_deletedSessionIDs.contains(review.sessionID) &&
      review.revision == sessionHistoryRevision(review.sessionID) &&
      review.revert?.fingerprint ==
          sessionsById[review.sessionID]?.stagedRevert?.fingerprint;

  void _resetSessionHistory(String id, {String? removedFrom}) {
    _historyRevisions[id] = sessionHistoryRevision(id) + 1;
    _eventBus.add(
      EventEnvelope(
        type: 'session.history.reset',
        properties: {'sessionID': id, 'removedFrom': ?removedFrom},
      ),
    );
    notifyListeners();
  }

  Future<void> stageSessionRevert(
    SessionRevertReview review,
    String messageID, {
    required bool applyFiles,
  }) => _mutateSessionRevert(
    review,
    messageID: messageID,
    applyFiles: applyFiles,
  );

  Future<void> clearSessionRevert(SessionRevertReview review) =>
      _mutateSessionRevert(review);

  Future<void> commitSessionRevert(SessionRevertReview review) =>
      _mutateSessionRevert(review, commit: true);

  Future<void> _mutateSessionRevert(
    SessionRevertReview review, {
    String? messageID,
    bool applyFiles = false,
    bool commit = false,
  }) async {
    final id = review.sessionID;
    final currentApi = api;
    final operations = repository;
    if (currentApi == null || operations is! StagedRevertGateway) {
      throw const ProductException('OpenCode is reconnecting. Try again.');
    }
    if (!isRevertReviewCurrent(review)) {
      throw const ProductException(
        'The session changed. Review the revert again.',
      );
    }
    if (sessionRevertSaving(id) || busySessions.contains(id)) {
      throw const ProductException(
        'Wait for the current session action to finish.',
      );
    }
    final token = Object();
    _revertMutations[id] = token;
    sessionRevertErrors.remove(id);
    notifyListeners();
    var dispatched = false;
    bool sameScope() => !_disposed && review.scope == _revertScope;
    try {
      final gateway = operations as StagedRevertGateway;
      if (messageID != null &&
          (messageID.startsWith('local-') ||
              inboxItemsFor(id).any((item) => item.id == messageID) ||
              await gateway.sessionRevertPrompt(id, messageID) == null)) {
        throw const ProductException(
          'This prompt is not in the saved conversation.',
        );
      }
      // Always re-read immediately before a mutation. The API has no
      // conditional commit; this prevents known stale reviews, not a server
      // race between this read and the POST.
      final fresh = await currentApi.session(id);
      if (!isRevertReviewCurrent(review)) {
        throw const ProductException(
          'The session changed. Review the revert again.',
        );
      }
      final changed =
          fresh.stagedRevert?.fingerprint != review.revert?.fingerprint;
      sessionsById[id] = fresh;
      _markSessionChanged(id, affectsStatus: false);
      sessionDetailsErrors.remove(id);
      if (changed) _resetSessionHistory(id);
      if (changed || (fresh.reverted && fresh.stagedRevert == null)) {
        throw const ProductException(
          'The staged revert changed. Review it again.',
        );
      }
      if (messageID == null && fresh.stagedRevert == null) {
        throw const ProductException('There is no staged revert to apply.');
      }
      if (busySessions.contains(id) ||
          ((messageID != null || commit) && inboxItemsFor(id).isNotEmpty)) {
        throw const ProductException(
          'Wait for the session and queued prompts to finish.',
        );
      }
      dispatched = true;
      SessionRevert? staged;
      if (messageID != null) {
        staged = await gateway.stageSessionRevert(
          id,
          messageID,
          applyFiles: applyFiles,
        );
      } else if (commit) {
        await gateway.commitSessionRevert(id);
      } else {
        await gateway.clearSessionRevert(id);
      }
      if (!sameScope()) return;
      if (sessionHistoryRevision(id) == review.revision &&
          !_deletedSessionIDs.contains(id)) {
        sessionsById[id] = (sessionsById[id] ?? fresh).copyWith(
          stagedRevert: staged,
        );
        _markSessionChanged(id, affectsStatus: false);
        _resetSessionHistory(
          id,
          removedFrom: commit ? review.revert?.messageID : null,
        );
      }
      // Stage's 200 response is immediately usable. Clear/commit return 204:
      // reconcile metadata/usage and invalidate all transcript continuations.
      if (messageID == null) {
        await _refreshOneSession(id);
        if (sameScope() && sessionDetailsErrors[id] != null) {
          throw ProductException(sessionDetailsErrors[id]!);
        }
      }
    } catch (error) {
      if (sameScope()) {
        if (dispatched) {
          // A timeout can mean the server applied the request. Reconcile
          // before enabling any retry, and reload history even if GET fails.
          _markSessionChanged(id, affectsStatus: false);
          await _refreshOneSession(id);
          if (sameScope()) _resetSessionHistory(id);
        }
        if (sameScope()) sessionRevertErrors[id] = error.toString();
      }
      rethrow;
    } finally {
      if (identical(_revertMutations[id], token)) {
        _revertMutations.remove(id);
        if (!_disposed) notifyListeners();
      }
    }
  }

  bool get serverOwnsSessionSelection =>
      api is SessionSelectionGateway ||
      (api == null && serverFlavor == ServerFlavor.v2);

  SessionSelection selectionForSession(String sessionID) =>
      serverOwnsSessionSelection
      ? sessionsById[sessionID]?.selection ??
            const SessionSelection(modelKnown: false, agentKnown: false)
      : SessionSelection(
          model: sessionModels[sessionID]?.model ?? selectedModel,
          variant: sessionModels[sessionID]?.variant ?? selectedVariant,
          agent: selectedAgent,
        );

  ModelRef? modelForSession(String sessionID) =>
      selectionForSession(sessionID).model;

  /// The variant [sessionID] sends with; see [modelForSession].
  String variantForSession(String sessionID) =>
      selectionForSession(sessionID).variant;

  String agentForSession(String sessionID) =>
      selectionForSession(sessionID).agent ?? '';

  bool sessionSelectionSaving(String sessionID) =>
      _selectionMutations.containsKey(sessionID);

  Future<void> waitForSessionSelection(
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
            if (!_disposed) notifyListeners();
          }
        });
    _selectionMutations[sessionID] = tracked;
    notifyListeners();
    return tracked;
  }

  Future<void> selectAgentForSession(String sessionID, String name) async {
    if (!serverOwnsSessionSelection) return selectAgent(name);
    if (name.isEmpty) return;
    await _mutateSessionSelection(sessionID, (gateway) async {
      final current = selectionForSession(sessionID);
      if (current.agentKnown && current.agent == name) return false;
      await gateway.setSessionAgent(sessionID, name);
      return true;
    });
  }

  /// Chooses a model for one session without touching the profile default
  /// or any other session.
  Future<void> selectModelForSession(
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
    notifyListeners();
  }

  void _forgetSessionModel(String sessionID) {
    if (sessionModels.remove(sessionID) == null) return;
    final p = profile;
    if (p != null) unawaited(store.setSessionModels(p.id, sessionModels));
  }

  Future<void> selectModel(ModelRef ref, {String? variant}) async {
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

  bool modelAvailable(ModelRef ref) =>
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

  Future<void> toggleModelFavorite(ModelRef model) async {
    if (!modelAvailable(model) && !_modelLibrary.isFavorite(model)) return;
    final before = _modelLibrary;
    final next = before.toggleFavorite(model);
    _modelLibrary = next;
    notifyListeners();
    try {
      await _persistModelLibrary();
    } catch (_) {
      if (!_disposed && identical(_modelLibrary, next)) {
        _modelLibrary = before;
        notifyListeners();
      }
      rethrow;
    }
  }

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
  Future<void> reloadProviderRuntime() async {
    final currentApi = api;
    final currentRepository = repository;
    if (currentApi != null &&
        currentRepository != null &&
        currentApi.capabilities.providerRuntimeRefresh) {
      try {
        await currentRepository.refreshProviderRuntime();
      } catch (_) {
        // The reload below still reports whether the provider came up.
      }
    }
    await _loadCatalog();
  }

  /// Providers `/provider` lists as connected that `/config/providers` (the
  /// server's live runtime) does not know. Empty when the runtime view is
  /// unavailable, which also covers servers predating `provider.list` where
  /// both calls answer from the same list.
  @visibleForTesting
  static Set<String> unloadedProviders(
    ProvidersResponse connected,
    ProvidersResponse? runtime,
  ) {
    if (runtime == null) return const {};
    final loaded = {for (final provider in runtime.providers) provider.id};
    return {
      for (final provider in connected.providers)
        if (!loaded.contains(provider.id)) provider.id,
    };
  }

  Future<void> _refreshPreexistingProviderRuntime({
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

  void _markSessionChanged(String id, {bool affectsStatus = true}) {
    _sessionRevision += 1;
    _sessionRevisions[id] = _sessionRevision;
    // Metadata hydration must not invalidate a concurrent status snapshot.
    if (affectsStatus) _sessionStatusRevisions[id] = _sessionRevision;
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
    _noteRevisions.clear();
    _noteReceipts.clear();
    _dismissAllCodingAlerts(clearActive: true);
    _sessionsRefreshGeneration += 1;
    _catalogRefreshGeneration += 1;
    _questionsRefreshGeneration += 1;
    _questionRevision += 1;
    _questionRevisions.clear();
    _sessionRevision += 1;
    _sessionRevisions.clear();
    _sessionStatusRevisions.clear();
    _selectionMutations.clear();
    _revertMutations.clear();
    _historyRevisions.clear();
    sessionRevertErrors.clear();
    sessionSelectionErrors.clear();
    sessionsById = {};
    _sessionsCursor = null;
    sessionsLoadingMore = false;
    sessionsMoreError = null;
    sessionsNeedReload = false;
    _sessionPageIDs.clear();
    _usedSessionCursors.clear();
    _sessionInventoryIDs.clear();
    _sessionInventoryInitialized = false;
    _sessionReads.clear();
    sessionDetailsErrors.clear();
    _deletedSessionIDs.clear();
    sessionModels = {};
    _modelLibrary = const ModelLibrary();
    busySessions = {};
    observedCompletedMessageIDs.clear();
    retryStates = {};
    permissions = {};
    questions = {};
    forms = {};
    _resolvedFormIDs.clear();
    _formRevision = 0;
    _formRefreshGeneration += 1;
    formsLoading = false;
    formsError = null;
    _inboxBySession.clear();
    inboxRevision += 1;
    _resolvedPermissionIDs.clear();
    _legacyPermissionIdentities.clear();
    _v2PermissionSessions.clear();
    _v2QuestionSessions.clear();
    _resolvedQuestionIDs.clear();
    _permissionRevision = 0;
    providers = null;
    agents = [];
    catalog = null;
    unloadedProviderIDs = const {};
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
    _profileDataChanges.notifyListeners();
    _profileDataChanges.dispose();
    _dismissAllCodingAlerts(clearActive: true);
    _generation += 1;
    connectionRevision = _generation;
    _retireTransport();
    _profileMonitor?.removeListener(_monitorChanged);
    _profileMonitor?.dispose();
    _quotaMonitor?.removeListener(_quotaMonitorChanged);
    _quotaMonitor?.dispose();
    if (!isIsolated) ManagedServerRecovery.disposeForPreferences(store.prefs);
    backgroundLive.removeListener(_backgroundLiveChanged);
    backgroundLive.dispose();
    if (_ownsDiagnostics) diagnostics.dispose();
    appearance.dispose();
    themePack.dispose();
    unawaited(_eventBus.close());
    super.dispose();
  }
}

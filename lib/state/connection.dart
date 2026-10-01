import 'dart:async';
import 'dart:convert';
import 'dart:ui' show Locale, PlatformDispatcher;

import 'app_locale.dart';
import 'automation_policy.dart';
import 'builtin_server_owner.dart';
import 'session_link_bindings.dart';
import 'shared_project_roots.dart';
import 'consent_owners.dart';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;
import 'package:shared_preferences/shared_preferences.dart';

import '../api/models.dart';
import '../domain/session_history.dart';
import '../domain/session_stop.dart';
import '../codex/gateway.dart';
import '../codex/transport.dart' show CodexFailure, CodexFailureKind;
import '../paseo/gateway.dart';
import '../paseo/transport.dart' show PaseoFailure, PaseoFailureKind;
import '../api/opencode_api.dart';
import '../api2/models.dart' show Api2Delivery, Api2FormInfo, Api2InboxItem;
import '../api/product_repository.dart';
import '../api/server_probe.dart';
import '../termux/managed_server_recovery.dart';
import 'notification_preferences.dart';
import 'nudges.dart';
import 'profile_monitor.dart';
import 'attention_feed.dart';
import 'monitor_attention_reader.dart';
import '../domain/work_row_status.dart';
import 'provider_quota_monitor.dart';
import '../quota/provider_quota_client.dart';
import '../api/sse.dart';
import '../api2/client.dart';
import '../api2/gateway.dart';
import '../api2/gateway_operations.dart';
import '../api2/transport.dart' show Api2AuthRequired;
import '../background/attention_tile_snapshot.dart';
import '../background/live_background.dart';
import '../background/team_alerts.dart';
import '../background/pinned_session_shortcuts.dart';
import '../background/widget_snapshot.dart';
import '../l10n/app_localizations.dart';
import '../platform/platform_capabilities.dart';
import '../diagnostics/app_diagnostics.dart';
import '../diagnostics/perf_trace.dart';
import '../termux/bridge.dart';
// A plain value type (no widgets): the person's effect choices.
import 'effects.dart' show KitEffects;
import '../builtin/builtin_linux.dart';
import '../builtin/builtin_server_recovery.dart';
import 'isolated_task_launch.dart';
import 'model_library.dart';
import 'offline_queue.dart';
import 'orchestration.dart';
import 'phone_project_engine.dart';
import '../domain/phone_project_engine.dart';
import '../domain/team_glance.dart';
import 'team_glance.dart';
import 'orchestration_store.dart';
import 'elsewhere_attention.dart';
import 'profiles.dart';
import 'termux_host_setup.dart' show ManagedRuntimeFlavor;
import 'pending_auth.dart';
import 'session_drafts.dart';
import 'draft_attachments.dart';
import 'draft_photo_recovery.dart';
import 'migration_runner.dart';
import 'prompt_photos.dart';
import 'saved_prompts_controller.dart';
import 'session_pins.dart';
import 'session_inventory_cache.dart';
import 'session_tail_cache.dart';
import 'session_auto_approval.dart';
import 'prompt_shelf.dart';
import 'queued_prompt_move.dart';
import 'queued_prompt_removal.dart';
import 'session_read_state.dart';
import 'automatic_activity.dart';
import 'return_brief_state.dart';
import '../domain/connection_status.dart';
import '../domain/return_brief.dart';
import '../domain/while_away.dart';
import '../domain/workspace_paths.dart';
import '../domain/session_title_text.dart';
import '../domain/team_directories.dart';

part 'connection/monitors.dart';
part 'connection/attention.dart';
part 'connection/surfaces.dart';
part 'connection/settings.dart';
part 'connection/connect.dart';
part 'connection/lifecycle.dart';
part 'connection/revert.dart';
part 'connection/models.dart';
part 'connection/status.dart';
part 'connection/queue.dart';
part 'connection/drafts.dart';
part 'connection/locations.dart';
part 'connection/sessions.dart';
part 'connection/catalog.dart';
part 'connection/permissions.dart';
part 'connection/questions.dart';
part 'connection/session_cache.dart';
part 'connection/team.dart';
part 'connection/phone_chat.dart';
part 'connection/forms_inbox.dart';
part 'connection/events.dart';
part 'connection/alerts.dart';
part 'connection/session_notes.dart';
part 'connection/read_state.dart';
part 'connection/activity.dart';
part 'connection/deletion.dart';
part 'connection/integration_auth.dart';
part 'connection/integration_commands.dart';
part 'connection/prompt_shelf.dart';
part 'connection/worktrees.dart';

/// App-wide singletons that need async init before the UI can render.
class AppBootstrap {
  final ProfileStore store;
  AppBootstrap(this.store);

  /// Bootstrap recovery intentionally works even when profile loading fails.
  /// The gate must drain outstanding loads before invoking this operation.
  static Future<void> resetSavedSignIns() async {
    final prefs = await SharedPreferences.getInstance();
    await ProfileStore(prefs: prefs).resetSavedSignIns();
  }

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
///
/// The class body keeps the constructor, the fields it initializes and the
/// members that must stay here (overrides, listeners, public statics). Each
/// area lives in a part under `connection/`: its mixin holds the state and
/// the public members, which stay real instance members so subclasses can
/// override them; its private extension holds the bodies and helpers. A
/// public member whose body needs the whole controller forwards to that
/// body through `_self`.
class ConnectionController extends ChangeNotifier
    with
        _ConnectionControllerMonitors,
        _ConnectionControllerAttention,
        _ConnectionControllerSurfaces,
        _ConnectionControllerSettings,
        _ConnectionControllerConnect,
        _ConnectionControllerLifecycle,
        _ConnectionControllerRevert,
        _ConnectionControllerModels,
        _ConnectionControllerStatus,
        _ConnectionControllerQueue,
        _ConnectionControllerDrafts,
        _ConnectionControllerLocations,
        _ConnectionControllerSessions,
        _ConnectionControllerCatalog,
        _ConnectionControllerPermissions,
        _ConnectionControllerQuestions,
        _ConnectionControllerSessionCache,
        _ConnectionControllerTeam,
        _ConnectionControllerPhoneChat,
        _ConnectionControllerFormsInbox,
        _ConnectionControllerEvents,
        _ConnectionControllerAlerts,
        _ConnectionControllerSessionNotes,
        _ConnectionControllerReadState,
        _ConnectionControllerActivity,
        _ConnectionControllerDeletion,
        _ConnectionControllerIntegrationAuth,
        _ConnectionControllerIntegrationCommands,
        _ConnectionControllerPromptShelf,
        _ConnectionControllerWorktrees {
  final ProfileStore store;
  final BackgroundLiveController backgroundLive;

  /// The one home of quiet hours, Wi-Fi only, check-ins and the "what
  /// notifies me" choices. Both monitors and this controller's own alerts
  /// read it; only the Notifications screen writes it.
  late final notificationPreferences = NotificationPreferences(store.prefs);

  /// The one-time tips registry (UX plan 5.8). One instance app-wide, because
  /// it also arbitrates the single nudge slot.
  late final nudges = NudgeRegistry(store.prefs);

  final MonitorGatewayFactory? _monitorGatewayFactory;

  final WidgetSessionSnapshot _widgetSnapshot;

  /// Launcher long-press entries for the connected profile's pinned sessions
  /// and the Quick Settings tile's cached needs-attention count. Both are
  /// derived from the same truth as the widget snapshot and follow its
  /// suspension and deletion rules.
  final PinnedSessionShortcuts _pinnedShortcuts;
  final AttentionTileSnapshot _attentionTile;
  final AppDiagnosticsController diagnostics;
  final bool _ownsDiagnostics;
  final OpenCodeApiFactory _apiFactory;
  final ProductRepositoryFactory _repositoryFactory;
  final V2GatewayPairFactory _v2GatewayFactory;
  final V2GatewayPairFactory _codexGatewayFactory;
  final V2GatewayPairFactory _paseoGatewayFactory;
  final EventStreamFactory _eventStreamFactory;
  final EventStreamFactory? _globalEventStreamFactory;
  final LocalWakeLockEnsurer _localWakeLockEnsurer;
  final DraftAttachmentVault _draftAttachmentVault;
  final PromptPhotoStore? _promptPhotoStore;
  late final PromptPhotoStore promptPhotos =
      _promptPhotoStore ?? PromptPhotoStore(store.prefs);

  late final _sessionInventoryCache = SessionInventoryCache(store.prefs);

  late final _sessionTailCache = SessionTailCache(store.prefs);

  /// Per-session approval choices for the selected profile (device-only).
  late final SessionAutoApprovalStore sessionAutoApproval =
      SessionAutoApprovalStore(store.prefs);

  /// Native phone team lifecycle; callable without an active server profile.
  late final PhoneProjectEngineController phoneProjectEngine =
      PhoneProjectEngineController(
        store: store,
        bridge: _phoneEngineBridge,
        gatewayBuilder: _phoneEngineGatewayBuilder,
        onAttached: _phoneEngineAttached,
        onReady: _phoneEngineReady,
        chatSource: _phoneChatSnapshot,
        chatActive: _phoneChatEligible,
      );
  final PhoneProjectEngineBridge? _phoneEngineBridge;
  final PhoneEngineGatewayBuilder? _phoneEngineGatewayBuilder;

  late final OrchestrationStore _orchestrationStore = OrchestrationStore(
    store.prefs,
    secure: store.secure,
  );

  /// An in-memory product walkthrough with no native publishing or reconnection.
  /// Its caller supplies a separate in-memory store and gateway pair.
  final bool isIsolated;

  static const workspaceChoiceNotice =
      'OpenCode Mobile no longer works in the server\'s home folder. '
      'Create a new folder or open a project folder to continue.';

  static const managedRuntimeMismatchMessage =
      'This phone is running a different OpenCode version. Open This phone '
      'setup to switch versions or connect with its matching profile.';
  late final _sessionReadStore = SessionReadStore(store.prefs);
  late final _returnBriefStore = ReturnBriefStore(store.prefs);

  late bool _shareSessionViews =
      store.prefs.getBool('oc.shareSessionViews') ?? true;

  late final PendingAuthStore _pendingAuth = PendingAuthStore(store.prefs);

  late final _sessionPins = SessionPinStore(store.prefs);
  final PromptShelfStore _promptShelf;

  /// Running and waiting conversations in this server's other projects,
  /// from its server-wide event channel.
  late final elsewhereAttention = ElsewhereAttention()
    ..addListener(_elsewhereChanged);

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
    V2GatewayPairFactory? paseoGatewayFactory,
    EventStreamFactory? eventStreamFactory,
    EventStreamFactory? globalEventStreamFactory,
    BackgroundLiveController? backgroundLive,
    AppDiagnosticsController? diagnostics,
    LocalWakeLockEnsurer? localWakeLockEnsurer,
    DraftAttachmentVault? draftAttachmentVault,
    DraftAttachmentVault? stashAttachmentVault,
    PromptPhotoStore? promptPhotoStore,
    PhoneProjectEngineBridge? phoneEngineBridge,
    PhoneEngineGatewayBuilder? phoneEngineGatewayBuilder,
  }) : _phoneEngineBridge = phoneEngineBridge,
       _phoneEngineGatewayBuilder = phoneEngineGatewayBuilder,
       _monitorGatewayFactory = monitorGatewayFactory,
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
       _paseoGatewayFactory = paseoGatewayFactory ?? _createPaseoGatewayPair,
       _eventStreamFactory = eventStreamFactory ?? _createEventStream,
       _globalEventStreamFactory =
           globalEventStreamFactory ??
           (eventStreamFactory == null ? _createGlobalEventStream : null),
       _localWakeLockEnsurer =
           localWakeLockEnsurer ?? TermuxBridge.ensureWakeLock,
       backgroundLive =
           backgroundLive ?? BackgroundLiveController(preferences: store.prefs),
       _widgetSnapshot = WidgetSessionSnapshot(prefs: store.prefs),
       _pinnedShortcuts = PinnedSessionShortcuts(prefs: store.prefs),
       _attentionTile = AttentionTileSnapshot(prefs: store.prefs),
       diagnostics = diagnostics ?? AppDiagnosticsController(),
       _ownsDiagnostics = diagnostics == null {
    _localeStore = AppLocaleStore(store.prefs);
    appLocale = ValueNotifier(_localeStore.value);
    appearance = ValueNotifier(store.appearance);
    themePack = ValueNotifier(store.themePack);
    effects = ValueNotifier(store.effects);
    transcriptReasoningExpanded = store.transcriptReasoningExpanded;
    transcriptTimestampsVisible = store.transcriptTimestampsVisible;
    this.backgroundLive.addListener(_backgroundLiveChanged);
    _profilesShown = _profilesSignature();
    store.changes.addListener(_profilesSaved);
    if (!isIsolated) {
      this.backgroundLive.bindActionHandler(_handleCodingAlertAction);
      _syncProfileServices();
      profileMonitor.start();
      quotaMonitor.start();
    }
  }
  void _quotaMonitorChanged() {
    if (!_disposed) super.notifyListeners();
  }

  void _monitorChanged() {
    if (_disposed) return;
    if (_lifecycleWasBackgrounded && !_canShowCodingAlert) {
      _dismissAllCodingAlerts();
    }
    // Monitor observations do not change the active session/widget snapshot.
    super.notifyListeners();
  }

  StreamStatus get status => _status;
  set status(StreamStatus value) {
    if (_status != value && value != StreamStatus.connected) {
      _attentionTransportRevision++;
    }
    _status = value;
    if (value != StreamStatus.connected) {
      _invalidatePhoneChatStatus();
    }
    _syncConnectionStatusClock();
    _syncPhoneChatHeartbeat();
  }

  void _orchestrationChanged() {
    if (_disposed) return;
    _syncTeamAlerts();
    notifyListeners();
  }

  /// A saved profile changed its name (or generation, or address): the
  /// app bar, the switcher and every list read those from [profile] and
  /// [store] at build time, so they only need to rebuild. Saves that change
  /// none of it (a version or a password stored while connecting) notify
  /// nobody, so connecting does not rebuild the shell twice.
  void _profilesSaved() {
    if (_disposed) return;
    _syncProfileServices();
    final next = _profilesSignature();
    if (next == _profilesShown) return;
    _profilesShown = next;
    notifyListeners();
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
      _ensureLocalServerWakeLock();
    } else {
      _dismissAllCodingAlerts(clearActive: true);
    }
    if (!_disposed) notifyListeners();
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
      // The AI Team's own folders are never opened for the person.
      if (isAiTeamDirectory(directory)) continue;
      if (best == null || project.updatedAt > best.updatedAt) best = project;
    }
    return best;
  }

  /// True for connect failures shaped like talking to the wrong server
  /// generation: a 401 (v1 client meeting v2's Basic-auth gate) or a 404/405
  /// (v2 client asking a v1 server for `/api/...`). Unreachable addresses and
  /// unhealthy servers are not flavor problems, so they never trigger a
  /// re-probe.
  /// True when a failed health check looks like the other protocol
  /// generation answered: auth/route mismatches, or a 200 whose body is not
  /// the JSON health object (an OpenCode 2 host serving its web UI on the
  /// OpenCode 1 path).
  @visibleForTesting
  static bool suggestsWrongFlavor(Object error) =>
      error is ApiException &&
      (error.statusCode == 401 ||
          error.statusCode == 404 ||
          error.statusCode == 405 ||
          error.errorTag == unexpectedHealthShapeTag);

  void _automaticActivityChanged() {
    if (!_disposed) notifyListeners();
  }

  @override
  void notifyListeners() {
    _syncConnectionStatusClock();
    _syncPhoneChatHeartbeat();
    super.notifyListeners();
    // Keep the Android home-screen widget's snapshot in step with session
    // truth; the writer itself skips unchanged payloads. Profile deletion
    // suspends this: the sessions it would republish belong to the profile
    // being erased.
    if (!_disposed && !_widgetSnapshotSuspended && !isIsolated) {
      // Retained so a caller that must observe the settled snapshot — profile
      // deletion — can wait for this write instead of racing it.
      final sessions = sortedSessions();
      final write = _widgetSnapshot.update(
        sessions: sessions,
        busySessions: busySessions,
        connected: status == StreamStatus.connected,
        profileID: _connectedProfile?.id ?? store.activeId ?? '',
      );
      _pendingWidgetSnapshotWrite = write;
      unawaited(write);
      _publishLiveStatus();
      _publishLaunchSurfaces(sessions);
    }
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
      case 'subagent':
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

  // The Inbox badge and the Work tab read the tally through this controller.
  void _elsewhereChanged() {
    if (!_disposed) notifyListeners();
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

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _resetConnectionStatusClock();
    _savedPrompts?.dispose();
    _savedPrompts = null;
    store.changes.removeListener(_profilesSaved);
    _profileDataChanges.notifyListeners();
    _profileDataChanges.dispose();
    _dismissAllCodingAlerts(clearActive: true);
    _generation += 1;
    connectionRevision = _generation;
    _retireTransport();
    _orchestration?.removeListener(_orchestrationChanged);
    _orchestration?.dispose();
    _orchestration = null;
    unawaited(phoneProjectEngine.close());
    // The histories are shared per profile and outlive this connection.
    for (final history in _watchedActivity) {
      history.removeListener(_automaticActivityChanged);
    }
    _watchedActivity.clear();
    _monitorAttentionReader.dispose();
    _profileMonitor?.removeListener(_monitorChanged);
    _profileMonitor?.dispose();
    _quotaMonitor?.removeListener(_quotaMonitorChanged);
    _quotaMonitor?.dispose();
    if (!isIsolated) ManagedServerRecovery.disposeForPreferences(store.prefs);
    backgroundLive.removeListener(_backgroundLiveChanged);
    backgroundLive.dispose();
    if (_ownsDiagnostics) diagnostics.dispose();
    appLocale.dispose();
    appearance.dispose();
    themePack.dispose();
    effects.dispose();
    unawaited(_eventBus.close());
    super.dispose();
  }

  @override
  ConnectionController get _self => this;

  /// [notifyListeners] for this library's extension parts, which cannot call
  /// a protected member themselves.
  void _notifyListeners() => notifyListeners();

  /// Announces [profileDataChanges] for the deletion sweep in its part.
  void _notifyProfileDataChanged() => _profileDataChanges.notifyListeners();
}

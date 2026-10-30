import '../scheduling/platform.dart';
import 'dart:async';
import 'dart:math' show Random;
import '../background/widget_v2_contract.dart';
import '../background/widget_action_runner.dart'
    show widgetProfileFingerprint, widgetRunMarker;
import '../domain/team_project_gateway.dart';
import '../ui/theme_packs.dart' show effectiveThemePack;
import '../builtin/agents/gen_ui_search_publish.dart';
import '../domain/server_answer.dart';
import '../domain/product_failure.dart';
import '../domain/mcp_connector_search.dart';
import 'mcp_connector_search_bridge.dart';
import 'setup_registry_store.dart';
import '../domain/setup_registry.dart';
import '../domain/genui/gen_ui.dart';
import '../domain/mcp_catalog.dart';
import 'mcp_chat_controller.dart';
import '../builtin/agents/gen_ui_install.dart';
import 'gen_ui_state.dart';
import 'dart:convert';
import 'dart:ui' show Brightness, Locale, PlatformDispatcher;
import 'package:clock/clock.dart';
import 'package:crypto/crypto.dart' as receipt_crypto;
import '../domain/command_receipts.dart';
import '../domain/command_receipt_transport.dart';
import 'pending_command_journal.dart';

import 'app_locale.dart';
import '../domain/settings_scope.dart';
import 'automation_policy.dart';
import 'builtin_server_owner.dart';
import 'session_link_bindings.dart';
import 'agent_handoff_links.dart';
import 'shared_project_roots.dart';
import 'consent_owners.dart';

import 'package:flutter/foundation.dart';
import 'package:intl/intl.dart' show DateFormat;
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models.dart';
import '../domain/session_history.dart';
import '../domain/tool_label.dart';
import '../domain/background_tasks.dart';
import '../domain/session_stop.dart';
import '../codex/gateway.dart';
import '../codex/transport.dart' show CodexFailure, CodexFailureKind;
import '../paseo/gateway.dart';
import '../paseo/tested_versions.dart';
import '../paseo/transport.dart' show PaseoFailure, PaseoFailureKind;
import '../domain/form_request.dart';
import '../api2/models.dart' show Api2Delivery, Api2FormInfo, Api2InboxItem;
import '../domain/product_repository.dart';
import '../domain/server_gateway.dart';
import '../domain/server_probe.dart';
import 'server_trust_probe.dart';
import '../termux/managed_server_recovery.dart';
import 'notification_preferences.dart';
import 'nudges.dart';
import 'tips.dart';
import 'profile_monitor.dart';
import 'attention_feed.dart';
import 'monitor_attention_reader.dart';
import '../domain/work_row_status.dart';
import 'provider_quota_monitor.dart';
import '../quota/provider_quota_client.dart';
import '../api2/client.dart';
import '../api2/gateway.dart';
import '../api2/gateway_mappers.dart' show api2ServerCapabilities;
import '../api2/gateway_operations.dart';
import '../api2/transport.dart' show Api2AuthRequired;
import '../background/attention_tile_snapshot.dart';
import '../background/live_background.dart';
import '../background/notification_status.dart';
import '../background/notification_text.dart';
import '../background/thermal_pause_signal.dart';
import '../domain/provider_presentation.dart';
import '../domain/mobile_tool_view.dart';
import '../background/team_alerts.dart';
import '../background/pinned_session_shortcuts.dart';
import '../background/usage_widget_snapshot.dart';
import '../domain/provider_usage.dart';
import '../paseo/provider_usage_gateway.dart';
import 'paseo_usage_monitor.dart';
import 'usage_subscriptions_controller.dart';
import 'usage_subscription_sources.dart';
import 'computer_usage_source.dart';
import 'usage_subscription_alerts.dart';
import '../domain/usage_subscriptions.dart';
import '../domain/agent_account.dart' show AgentAccountGateway;
import '../builtin/builtin_server.dart' show looksLikeInAppServer;
import '../background/widget_snapshot.dart';
import '../l10n/app_localizations.dart';
import '../platform/platform_capabilities.dart';
import '../diagnostics/app_diagnostics.dart';
import '../diagnostics/perf_trace.dart';
import '../termux/bridge.dart';
// A plain value type (no widgets): the person's effect choices.
import 'effects.dart' show KitEffects, KitTextSize;
import '../builtin/builtin_linux.dart';
import '../builtin/quota_collector_host.dart';
import '../builtin/builtin_server.dart' show startBuiltinServer;
import 'delayed_answers.dart';
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
import 'context_usage_memory.dart';
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
import '../domain/chat_feed.dart';
import '../domain/merged_chat_feed.dart';
import '../domain/phone_agent_host.dart';
import '../domain/phone_agents.dart';
import '../domain/phone_agents_source.dart';
import '../domain/agent_sign_in.dart';
import '../domain/agent_catalog.dart';
import '../domain/agent_tools/agent_certification.dart';
import '../domain/agent_tools/browser_claude_launch.dart';
import '../domain/agent_auth_probe.dart';
import '../domain/turn_stall.dart';
import '../paseo/chat_feed_source.dart';
import '../builtin/agents/phone_agents_host.dart' show BuiltinPhoneAgents;
import '../builtin/agents/agent_sign_in.dart' show ChannelAgentSignInHost;
import 'phone_agent_host_port.dart';
import 'conversation_title_memory.dart';

part 'connection/gen_ui.dart';
part 'connection/connector_search.dart';
part 'connection/mcp_chat.dart';
part 'connection/feed_questions.dart';
part 'connection/feed_permissions.dart';
part 'connection/feed_question_reads.dart';
part 'connection/feed_forms.dart';
part 'connection/monitors.dart';
part 'connection/attention.dart';
part 'connection/surfaces.dart';
part 'connection/widgets.dart';
part 'connection/settings.dart';
part 'connection/connect.dart';
part 'connection/lifecycle.dart';
part 'connection/revert.dart';
part 'connection/models.dart';
part 'connection/status.dart';
part 'connection/quiet_reconnect.dart';
part 'connection/queue.dart';
part 'connection/drafts.dart';
part 'connection/locations.dart';
part 'connection/sessions.dart';
part 'connection/catalog.dart';
part 'connection/permissions.dart';
part 'connection/questions.dart';
part 'connection/pending_reads.dart';
part 'connection/session_cache.dart';
part 'connection/team.dart';
part 'connection/phone_chat.dart';
part 'connection/turn_stall.dart';
part 'connection/forms_inbox.dart';
part 'connection/events.dart';
part 'connection/session_events.dart';
part 'connection/request_events.dart';
part 'connection/alerts.dart';
part 'connection/session_notes.dart';
part 'connection/read_state.dart';
part 'connection/activity.dart';
part 'connection/deletion.dart';
part 'connection/integration_auth.dart';
part 'connection/integration_commands.dart';
part 'connection/prompt_shelf.dart';
part 'connection/worktrees.dart';
part 'connection/chat_feed.dart';
part 'connection/phone_agents.dart';
part 'connection/phone_agents_check.dart';
part 'connection/phone_agents_cache.dart';
part 'connection/phone_agents_backend.dart';
part 'connection/phone_agents_idle.dart';
part 'connection/phone_agents_saved.dart';
part 'connection/agent_handoff.dart';
part 'connection/phone_agents_startup.dart';
part 'connection/phone_agents_routes.dart';
part 'connection/side_connections.dart';

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
/// An agent backend's profile id is its phone profile's id plus this, so the
/// profile deletion sweep (`oc.<what>.<id>.…`) also removes its keys.
const agentBackendProfileSuffix = '.agents';

final connProvider = Provider<ConnectionController>(
  (ref) => throw UnimplementedError('overridden in bootstrap'),
);

typedef ServerGatewayFactory = ServerGateway Function(ServerProfile profile);

typedef ProductRepositoryFactory =
    ServerOperationsGateway Function(ServerGateway api);

/// Builds the OpenCode 2 gateway pair for a profile whose detected flavor is
/// [ServerFlavor.v2]. Injected by tests; production uses the Api2Transport →
/// Api2Client → Api2Gateway/Api2OperationsGateway stack.
typedef V2GatewayPairFactory =
    ({ServerGateway gateway, ServerOperationsGateway operations}) Function(
      ServerProfile profile,
    );

typedef LocalWakeLockEnsurer = Future<void> Function();

typedef EventStreamFactory =
    LiveEventChannel Function({
      required ServerGateway api,
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
        _ConnectionControllerGenUi,
        _ConnectionControllerMcpChat,
        _ConnectionControllerConnectorSearch,
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
        _ConnectionControllerWorktrees,
        _ConnectionControllerChatFeed,
        _ConnectionControllerPhoneAgents,
        _ConnectionControllerSideConnections {
  final ProfileStore store;
  final BackgroundLiveController backgroundLive;

  /// Answers to agents' requests held briefly so they can be undone (see
  /// delayed_answers.dart); shared by the chat and the Conversations list.
  late final DelayedAnswers delayedAnswers = DelayedAnswers();

  /// Conversations whose run this connection watched end and that haven't
  /// been opened since (the list's Done tag).
  final _finishedUnseen = <String>{};

  /// Whether [sessionID]'s run ended since it was last opened here.
  bool finishedUnseen(String sessionID) => _finishedUnseen.contains(sessionID);

  /// Conversations whose latest turn this app stopped (the person pressed
  /// Stop). OpenCode 2 reports a stop as a normal finish, so the list would
  /// say "Done"; this memory lets it say "Stopped". Cleared when a new
  /// prompt is sent, and when the conversation is opened.
  final _stoppedByPerson = <String>{};

  /// Whether this app stopped [sessionID]'s latest turn.
  bool stoppedByPerson(String sessionID) =>
      _stoppedByPerson.contains(sessionID);

  void markStoppedByPerson(String sessionID) {
    if (_stoppedByPerson.add(sessionID)) notifyListeners();
  }

  void clearStoppedByPerson(String sessionID) {
    if (_stoppedByPerson.remove(sessionID)) notifyListeners();
  }

  /// Why the server interrupted each conversation's latest turn, when it
  /// said so (OpenCode 2). Set by the idle event that ends an interrupted
  /// run; cleared when the conversation runs again or ends any other way.
  final _turnInterrupts = <String, TurnInterruptReason>{};

  /// Why the server interrupted [sessionID]'s latest turn; null when it was
  /// not interrupted, or the server does not say.
  TurnInterruptReason? turnInterruptReason(String sessionID) =>
      _turnInterrupts[sessionID];

  /// Conversations whose latest turn this app asked the server to stop (the
  /// chat's Stop, the notification's "Stop the reply"). Unlike
  /// [stoppedByPerson] it stays when the conversation is opened, and goes
  /// when the conversation runs again.
  final _stopsFromThisApp = <String>{};

  /// Whether this app asked the server to stop [sessionID]'s latest turn.
  bool turnStoppedFromThisApp(String sessionID) =>
      _stopsFromThisApp.contains(sessionID);

  /// Notes (or, with [asked] false, takes back) a stop this app asked for,
  /// before the server's interrupt can arrive.
  void noteStopFromThisApp(String sessionID, {bool asked = true}) {
    final changed = asked
        ? _stopsFromThisApp.add(sessionID)
        : _stopsFromThisApp.remove(sessionID);
    if (changed) notifyListeners();
  }

  /// The server's last word on whether a turn that looked cut off still
  /// runs ([checkTurnStillRuns]), with when it was asked.
  final _turnRunChecks = <String, ({TurnRunCheck word, DateTime at})>{};
  final _turnRunChecking = <String>{};

  /// How long the server's answer stands before the next look asks again.
  static const turnRunCheckFresh = Duration(seconds: 20);

  /// What the server last said about [sessionID]'s run, while a check is
  /// asked or its answer still fresh: the chat reads it before it calls a
  /// reply dropped. A check under way keeps the previous answer, so the
  /// turn's line does not flicker while it is asked again.
  TurnRunCheck? turnRunCheck(String sessionID) {
    final last = _turnRunChecks[sessionID];
    if (last == null) {
      return _turnRunChecking.contains(sessionID)
          ? TurnRunCheck.checking
          : null;
    }
    if (_turnRunChecking.contains(sessionID) ||
        DateTime.now().difference(last.at) < turnRunCheckFresh) {
      return last.word;
    }
    return null;
  }

  /// Asks the server whether [sessionID] still runs, unless a check is under
  /// way or its answer is fresh. A run the server says is going is busy
  /// again here: a turn whose server still works never reads as dropped.
  Future<void> checkTurnStillRuns(String sessionID) async {
    final fresh = _turnRunChecks[sessionID];
    if (_turnRunChecking.contains(sessionID) ||
        (fresh != null &&
            DateTime.now().difference(fresh.at) < turnRunCheckFresh)) {
      return;
    }
    final currentApi = api;
    final generation = _generation;
    _turnRunChecking.add(sessionID);
    if (fresh == null) notifyListeners();
    TurnRunCheck word;
    // While the link is down only an OpenCode server's status read reaches
    // the server; Codex and Paseo answer from what the lost link last said.
    final backend = (_connectedProfile ?? profile)?.backend;
    final remembered =
        !isConnected &&
        (isAgentBackend ||
            (backend != null && backend != ServerBackend.openCode));
    try {
      if (currentApi == null || remembered) throw StateError('no live read');
      final statuses = await currentApi.sessionStatuses().timeout(
        const Duration(seconds: 8),
      );
      final status = statuses[sessionID];
      word = status != null && status != 'idle'
          ? TurnRunCheck.running
          : TurnRunCheck.ended;
    } catch (_) {
      word = TurnRunCheck.unreachable;
    }
    _turnRunChecking.remove(sessionID);
    if (_disposed) return;
    // An answer from a transport no longer in use says nothing of this one.
    if (word == TurnRunCheck.running && !_isCurrent(generation, currentApi)) {
      word = TurnRunCheck.unreachable;
    }
    // Every answer is kept, even "unreachable": the chat asks again only
    // once it is stale, never on each rebuild.
    _turnRunChecks[sessionID] = (word: word, at: DateTime.now());
    if (word == TurnRunCheck.running && busySessions.add(sessionID)) {
      _turnInterrupts.remove(sessionID);
      _markSessionAttentionActive(sessionID);
      _publishLiveStatus();
    }
    notifyListeners();
  }

  /// The one home of quiet hours, Wi-Fi only, check-ins and the "what
  /// notifies me" choices. Both monitors and this controller's own alerts
  /// read it; only the Notifications screen writes it.
  late final notificationPreferences = NotificationPreferences(store.prefs);

  /// The one-time tips registry (UX plan 5.8). One instance app-wide, because
  /// it also arbitrates the single nudge slot.
  late final nudges = NudgeRegistry(store.prefs);

  /// The teaching system's tips and mini tutorials (docs/ux-system/
  /// teaching.md): one instance app-wide, because it keeps one tip on
  /// screen at a time.
  late final tips = TipController(store.prefs);

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
  final ServerGatewayFactory? _apiFactory;
  final ProductRepositoryFactory? _repositoryFactory;
  final V2GatewayPairFactory _v2GatewayFactory;
  final V2GatewayPairFactory _codexGatewayFactory;
  final V2GatewayPairFactory _paseoGatewayFactory;
  final BrowserClaudeLaunchRegistry _browserLaunches;
  final bool _ownsBrowserLaunches;

  final PhoneAgentHostPort Function(ServerProfile profile)?
  _phoneAgentHostFactory;
  final AgentSignInHost Function()? _agentSignInHostFactory;
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

  /// The last context share seen per conversation of the selected server
  /// (device-only; feeds the chats list's small rings).
  ContextUsageMemory? get contextUsageMemory {
    final id = profile?.id ?? '';
    return id.isEmpty ? null : ContextUsageMemory.forProfile(store.prefs, id);
  }

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

  /// A conversation backend beside the app's main connection (Claude Code on
  /// this phone): it connects and runs its own conversations, models and
  /// approvals, but owns no profile-wide service — no monitors, background
  /// mode, widgets or active-profile writes. Built by
  /// [ConnectionController.agentBackend].
  final bool isAgentBackend;

  /// Another saved server's conversations beside the app's main connection
  /// (Termux while the app is on the in-app Ubuntu): like an agent backend
  /// it owns no profile-wide service and never becomes the active profile.
  /// Built by [ConnectionController.sideBackend].
  final bool isSideBackend;

  /// A conversation backend beside the main connection.
  bool get _isSecondary => isAgentBackend || isSideBackend;

  /// Conversations whose missing record was asked for, per generation
  /// (see _fetchMissingSelection).
  final _selectionFetches = <String>{};

  /// Only the main connection runs the services shared by every profile.
  bool get _ownsProfileServices => !isIsolated && !_isSecondary;

  /// The saved profile an agent backend belongs to (the phone's), whose
  /// saved prompts it shares; null on the main connection.
  String? get _agentOwnerProfileId {
    final id = _connectedProfile?.id;
    if (!isAgentBackend || id == null) return null;
    return id.endsWith(agentBackendProfileSuffix)
        ? id.substring(0, id.length - agentBackendProfileSuffix.length)
        : null;
  }

  /// Starts an agent backend's helper again and reconnects (the banner's
  /// Restart); set by the main connection that owns the helper.
  Future<void> Function()? _agentBackendRecover;
  Future<void> Function()? get recoverAgentBackend => _agentBackendRecover;

  /// Whose notification rules and alerts these are: an agent backend's
  /// belong to the phone profile it runs under.
  String get _alertProfileId => _agentOwnerProfileId ?? profile?.id ?? '';

  /// A saved profile, or this agent backend's own (never saved) one.
  bool _isKnownProfile(String id) =>
      (isAgentBackend && id == _connectedProfile?.id) ||
      store.profiles.any((profile) => profile.id == id);

  static const workspaceChoiceNotice =
      'OpenCode Mobile no longer works in the server\'s home folder. '
      'Create a new folder or open a project folder to continue.';

  static const managedRuntimeMismatchMessage =
      'This phone needs to move to OpenCode 2 before connecting. '
      'Your existing conversations are still stored safely on this phone.';
  late final _sessionReadStore = SessionReadStore(store.prefs);
  late final _returnBriefStore = ReturnBriefStore(store.prefs);

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

  /// A conversation backend for one agent host (see [isAgentBackend]). Its
  /// transport comes only from [paseoGatewayFactory]; [diagnostics] is the
  /// main connection's and is not disposed here.
  /// [backgroundLive] is the main connection's, shared: background mode and
  /// its notifications stay one, and this backend never disposes it.
  factory ConnectionController.agentBackend(
    ProfileStore store, {
    required V2GatewayPairFactory paseoGatewayFactory,
    required BackgroundLiveController backgroundLive,
    AppDiagnosticsController? diagnostics,
    DraftAttachmentVault? draftAttachmentVault,
    PromptPhotoStore? promptPhotoStore,
  }) => ConnectionController(
    store,
    isAgentBackend: true,
    paseoGatewayFactory: paseoGatewayFactory,
    diagnostics: diagnostics,
    draftAttachmentVault: draftAttachmentVault,
    promptPhotoStore: promptPhotoStore,
    backgroundLive: backgroundLive,
  );

  /// A connection to another saved server for the one Conversations list,
  /// built from [main]'s own transports.
  factory ConnectionController.sideBackend(ConnectionController main) =>
      ConnectionController(
        main.store,
        isSideBackend: true,
        genUiInstaller: main._genUiInstaller,
        browserClaudeLaunchRegistry: main._browserLaunches,
        apiFactory: main._apiFactory,
        repositoryFactory: main._repositoryFactory,
        v2GatewayFactory: main._v2GatewayFactory,
        codexGatewayFactory: main._codexGatewayFactory,
        paseoGatewayFactory: main._paseoGatewayFactory,
        eventStreamFactory: main._eventStreamFactory,
        globalEventStreamFactory: main._globalEventStreamFactory,
        backgroundLive: main.backgroundLive,
        diagnostics: main.diagnostics,
        localWakeLockEnsurer: main._localWakeLockEnsurer,
        draftAttachmentVault: main._draftAttachmentVault,
        promptPhotoStore: main._promptPhotoStore,
      );

  /// Explicit, transient browser choice for the active phone-agent chat.
  Future<void> setAgentBrowserRequestedForSession(
    String sessionID, {
    required bool requested,
  }) async {
    final gateway = api;
    if (gateway is! PaseoGateway) {
      throw const ProductException(
        'Open a Claude Code chat on this phone to use the agent browser.',
      );
    }
    await gateway.setBrowserRequestedForSession(
      sessionID,
      requested: requested,
    );
  }

  ConnectionController(
    this.store, {
    this.isIsolated = false,
    this.isAgentBackend = false,
    this.isSideBackend = false,
    ServerGatewayFactory? apiFactory,
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
    PhoneAgentHostPort Function(ServerProfile profile)? phoneAgentHostFactory,
    AgentSignInHost Function()? agentSignInHostFactory,
    GenUiInstaller? genUiInstaller,
    GenUiSearchPublisher? genUiSearchPublisher,
    BrowserClaudeLaunchRegistry? browserClaudeLaunchRegistry,
  }) : _browserLaunches =
           browserClaudeLaunchRegistry ?? BrowserClaudeLaunchRegistry(),
       _ownsBrowserLaunches = browserClaudeLaunchRegistry == null,
       _phoneAgentHostFactory = phoneAgentHostFactory,
       _agentSignInHostFactory = agentSignInHostFactory,
       _phoneEngineBridge = phoneEngineBridge,
       _phoneEngineGatewayBuilder = phoneEngineGatewayBuilder,
       _monitorGatewayFactory = monitorGatewayFactory,
       _promptPhotoStore = promptPhotoStore,
       _draftAttachmentVault = draftAttachmentVault ?? DraftAttachmentVault(),
       _promptShelf = PromptShelfStore.withAttachmentFiles(
         store.prefs,
         vault: stashAttachmentVault,
       ),
       _apiFactory = apiFactory,
       _repositoryFactory = repositoryFactory,
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
    _genUiInstaller = genUiInstaller;
    _genUiSearchPublisher = genUiSearchPublisher;
    appLocale = ValueNotifier(store.appPreferences.value.locale);
    appearance = ValueNotifier(store.appearance);
    themePack = ValueNotifier(store.themePack);
    appearanceAccent = ValueNotifier(store.appearanceAccent);
    effects = ValueNotifier(store.effects);
    textSize = ValueNotifier(store.textSize);
    transcriptReasoningExpanded = store.transcriptReasoningExpanded;
    transcriptTimestampsVisible = store.transcriptTimestampsVisible;
    store.appPreferences.addListener(_appPreferencesChanged);
    this.backgroundLive.addListener(_backgroundLiveChanged);
    thermalPauseActive.addListener(_publishLiveStatus);
    _profilesShown = _profilesSignature();
    store.changes.addListener(_profilesSaved);
    addListener(_mcpChatChanged);
    addListener(_connectorSearchSync);
    if (_ownsProfileServices) {
      this.backgroundLive.bindActionHandler(_handleCodingAlertAction);
      this.backgroundLive.bindStatusActionHandler(_handleStatusAction);
      this.backgroundLive.bindWidgetActionHandler(_handleWidgetAction);
      _syncProfileServices();
      profileMonitor.start();
      quotaMonitor.start();
      paseoUsageMonitor.start();
      if (_hasUsageReaders) usageSubscriptions.start();
      _paWarmUp();
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
    _observeQuietReconnect(value);
    if (_status != value && value != StreamStatus.connected) {
      _attentionTransportRevision++;
    }
    final changed = _status != value;
    _status = value;
    if (value != StreamStatus.connected) {
      _invalidatePhoneChatStatus();
    }
    // Reconnecting changes what the status notification says.
    if (changed) _publishLiveStatus();
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
    _syncSideConnections();
    final next = _profilesSignature();
    if (next == _profilesShown) return;
    _profilesShown = next;
    notifyListeners();
  }

  void _backgroundLiveChanged() {
    _widgetSnapshot.invalidateNativeCopy();
    _usageSubscriptions?.setRuntime(
      foreground: !_lifecycleWasBackgrounded,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _quotaMonitor?.setRuntime(
      foreground: !_lifecycleWasBackgrounded,
      backgroundAllowed: keepLiveInBackground && backgroundLive.active,
    );
    _paseoUsageMonitor?.setRuntime(
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

  /// A missing health route or rejected auth may be an unsupported legacy
  /// server. Re-probe it for a clear upgrade explanation; never start an old
  /// client or retry a mutation against a different protocol.
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
    if (!_disposed && !_widgetSnapshotSuspended && _ownsProfileServices) {
      // Retained so a caller that must observe the settled snapshot — profile
      // deletion — can wait for this write instead of racing it.
      final sessions = sortedSessions();
      final others = _widgetOtherConversations();
      final write = _widgetSnapshot.update(
        sessions: sessions,
        busySessions: busySessions,
        connected:
            status == StreamStatus.connected &&
            (!_lifecycleWasBackgrounded ||
                keepLiveInBackground && backgroundLive.active),
        profileID: _connectedProfile?.id ?? store.activeId ?? '',
        l10n: _shellStrings(),
        scopeToken: _currentWidgetScope,
        lastProjectID: _widgetProjectID,
        pendingCount: awaitingPermissionCount + questions.length + forms.length,
        details: _widgetSessionDetails(),
        others: others,
        teamRows: _widgetTeamRows(),
        actionResult: _widgetActionResult,
        appTheme: _widgetAppTheme,
        onDemand: _widgetOnDemand,
        preserveForOnDemand: _lifecycleSuspended,
        server: _widgetServerName,
        servers: _widgetServers(),
        projects: _widgetProjects(sessions, others),
      );
      _pendingWidgetSnapshotWrite = write;
      unawaited(write);
      _publishLiveStatus();
      _publishLaunchSurfaces(sessions);
    }
  }

  /// Names what a tool is doing without repeating its input: no command,
  /// path, query, or URL reaches the notification shade.
  ///
  /// [l10n] says it in the person's language (the live notification is
  /// built without a widget tree); English where nobody passes it.
  static String toolSentence(String tool, [AppLocalizations? l10n]) {
    final words = l10n ?? lookupAppLocalizations(const Locale('en'));
    switch (tool.toLowerCase()) {
      case 'bash':
      case 'shell':
        return words.liveToolCommand;
      case 'edit':
      case 'write':
      case 'patch':
      case 'multiedit':
      case 'apply_patch':
        return words.liveToolEdit;
      case 'read':
        return words.liveToolRead;
      case 'grep':
      case 'glob':
      case 'list':
      case 'ls':
        return words.liveToolSearch;
      case 'webfetch':
      case 'websearch':
        return words.liveToolWeb;
      case 'task':
      case 'subagent':
        return words.liveToolSubagent;
      case 'todowrite':
      case 'todoread':
        return words.liveToolPlan;
      case '':
        return words.liveToolWorking;
      case _ when isAgentCardTool(tool):
        return words.liveToolCard;
      case _ when isBackgroundTaskNotice(tool):
        return words.liveToolBackgroundTask;
      default:
        // Never the raw id (`render_mermaid_diagram`): its words.
        final toolWords = toolIdWords(tool);
        return toolWords.isEmpty
            ? words.liveToolWorking
            : words.liveToolOther(toolWords);
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
    _forgetTurnStalls();
    _mcpChatDispose();
    _connectorSearchDispose();
    _genUiDispose();
    _resetConnectionStatusClock();
    _syncLocalLiveness(null);
    _syncAgentDaemonWatch(null);
    _syncAgentCommandHold(null);
    _endResumeQuiet();
    _resetQuietReconnect();
    _feedDispose();
    _paShutdown();
    _sideShutdown();
    // A held answer is sent, not lost.
    delayedAnswers.dispose();
    _savedPrompts?.dispose();
    _savedPrompts = null;
    store.changes.removeListener(_profilesSaved);
    store.appPreferences.removeListener(_appPreferencesChanged);
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
    _paseoUsageMonitor?.removeListener(_quotaMonitorChanged);
    _paseoUsageMonitor?.dispose();
    _usageSubscriptions?.dispose();
    if (_ownsProfileServices) {
      ManagedServerRecovery.disposeForPreferences(store.prefs);
    }
    backgroundLive.removeListener(_backgroundLiveChanged);
    thermalPauseActive.removeListener(_publishLiveStatus);
    // A secondary backend borrows the main connection's.
    if (!_isSecondary) backgroundLive.dispose();
    if (_ownsDiagnostics) diagnostics.dispose();
    appLocale.dispose();
    appearance.dispose();
    themePack.dispose();
    appearanceAccent.dispose();
    effects.dispose();
    textSize.dispose();
    unawaited(_eventBus.close());
    super.dispose();
  }

  @override
  ConnectionController get _self => this;

  /// [notifyListeners] for this library's extension parts, which cannot call
  /// a protected member themselves.
  void _notifyListeners() {
    _syncTurnStallsOnNotify();
    notifyListeners();
  }

  /// Announces [profileDataChanges] for the deletion sweep in its part.
  void _notifyProfileDataChanged() => _profileDataChanges.notifyListeners();
}

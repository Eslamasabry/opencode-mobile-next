/// Optional experimental Paseo gateway (daemon 0.8.0, protocol 1).
///
/// The Paseo daemon runs beside the agent CLIs on the user's computer and
/// drives Claude Code, Pi, Codex and others for this app. One daemon *agent*
/// is one conversation and is shown as a session; the model picker's provider
/// chooses which runtime a new conversation uses, and the daemon's permission
/// modes are offered where OpenCode offers agents.
library;

import 'dart:async';
import 'dart:math';

import '../api/models.dart';
import '../domain/server_gateway.dart';
import 'mappers.dart';
import 'host_agent_providers.dart';
import 'transport.dart';
import '../diagnostics/perf_trace.dart';

const paseoServerCapabilities = ServerCapabilities(
  hostAgentProviders: true,
  hostAgentPermissionActions: true,
  promptAttachments: false,
  promptAgentMentions: false,
  offlinePromptQueue: false,
  fileBrowsing: false,
  terminal: false,
  projectManagement: false,
  globalSessionSearch: false,
  sessionDiff: false,
  sessionFork: false,
  sessionCompact: false,
  persistentPermissionGrants: true,
  savedPermissionList: false,
  messageCompletionEndsRun: false,
  sessionRevert: false,
  sessionImportExport: false,
  sessionNotes: false,
  serverCatalog: false,
  slashCommands: false,
  profileAttentionPolling: false,
  managedWorkspaces: false,
  workspaceWarp: false,
  sessionSteal: false,
  consoleOrganizations: false,
  mcpOAuth: false,
  mcpConfigWrites: false,
  mcpRuntimeAdds: false,
  mcpRuntimeRemovals: false,
  integrationCredentials: false,
  integrationCommandAuth: false,
  pluginInventory: false,
  webSearch: false,
  sessionShare: false,
  sessionArchive: false,
  sessionTodos: false,
  messageDelete: false,
  workspaceSymbols: false,
  textSearch: false,
  languageServiceStatus: false,
  formatterStatus: false,
  toolInventory: false,
  experimentalCapabilities: false,
  shellSettings: false,
  remoteUpgrade: false,
  clientDiagnostics: false,
  gitInit: false,
  providerRuntimeRefresh: false,
  configuredProviderFallback: false,
  globalEventStream: false,
  worktreeReset: false,
  worktreeCreate: false,
  legacyQuestionRequests: false,
  cliSessionResume: false,
  forms: false,
  inbox: false,
);

/// The runtime a conversation uses when the composer names no model.
const paseoDefaultProvider = 'claude';

/// How long [PaseoGateway] waits for a starting daemon to finish listing its
/// runtimes' models. Overridable so tests do not sleep.
int providerWarmupAttempts = 8;
Duration providerWarmupInterval = const Duration(milliseconds: 1500);

/// Placeholder model id for a provider that reports no model list.
const paseoDefaultModel = 'default';

class _PaseoPermission {
  final int epoch;
  final PermissionRequest permission;
  final List<dynamic> suggestions;
  final HostAgentPermissionRequest? hostRequest;
  _PaseoPermission(
    this.epoch,
    this.permission,
    this.suggestions,
    this.hostRequest,
  );
}

/// Live stream bookkeeping for one agent.
class _PaseoLive {
  /// Message ids already announced, with the text streamed so far.
  final announced = <String, StringBuffer>{};

  /// Reasoning and id-less assistant text arrive as runs of deltas with no id
  /// of their own. A run keeps the id of its first delta until another item
  /// kind interrupts it.
  String? runID;
  String? runType;
  int lastSeq = 0;
  String? epoch;
}

class PaseoGateway
    implements
        ServerGateway,
        ServerOperationsGateway,
        HostAgentProviderGateway,
        HostAgentPermissionGateway {
  final PaseoTransport transport;
  String? _directory;
  bool _closed = false;
  final _agents = <String, Map<String, dynamic>>{};
  final _sessions = <String, Session>{};
  final _statuses = <String, String>{};
  final _drafts = <String>{};
  final _uncertain = <String>{};

  /// Sessions whose prompt was accepted but whose turn has not started. The
  /// daemon reports a freshly spawned agent as idle for a moment before the
  /// turn begins; that gap must not read as a finished run.
  final _awaitingTurn = <String>{};

  /// Sessions with a turn the stream says is running. Agent snapshots lag the
  /// stream (an `idle` snapshot can land after `turn_started`), so while a
  /// turn is open the stream owns the busy state.
  final _turnActive = <String>{};
  final _permissions = <String, _PaseoPermission>{};

  /// Requests answered from here. A snapshot taken before the daemon applied
  /// the answer still lists the request; it must not come back as a new card.
  final _answered = <String>{};
  final _live = <String, _PaseoLive>{};

  /// The released daemon (0.8.0) names a new agent itself, but a conversation
  /// opened here already has an id the chat screen is bound to. That id stays
  /// the app-facing one for this gateway's lifetime; these maps translate.
  final _realIDs = <String, String>{};
  final _appIDs = <String, String>{};

  /// Pushes about agents not known yet, held while a create is in flight: the
  /// daemon streams a new agent's first events before it answers the create.
  int _creating = 0;
  final _heldEvents = <PaseoEvent>[];
  final _events = StreamController<EventEnvelope>.broadcast(sync: true);
  final _streamStates = StreamController<StreamStatus>.broadcast(sync: true);
  late final StreamSubscription<PaseoEvent> _daemonEvents;
  late final StreamSubscription<int> _daemonDisconnects;
  Timer? _retry;
  int _retryAttempt = 0;
  int _locationEpoch = 0;
  bool _listening = false;
  bool _recoveryDegraded = false;
  Future<void>? _recovering;
  List<Map<String, dynamic>>? _providerEntries;
  int _providerRevision = 0;

  PaseoGateway({required this.transport, String? directory})
    : _directory = directory {
    _daemonEvents = transport.events.listen(_onEvent);
    _daemonDisconnects = transport.disconnects.listen((_) {
      _providerEntries = null;
      _providerRevision++;
      for (final id in _permissions.keys.toList()) {
        _resolvePermission(id);
      }
      _recoveryDegraded = true;
      _emitState(StreamStatus.reconnecting);
      _scheduleReconnect();
    });
  }

  PaseoGateway.connect({
    required String baseUrl,
    String password = '',
    String? directory,
  }) : this(
         transport: PaseoTransport(endpoint: baseUrl, password: password),
         directory: directory,
       );

  @override
  ServerCapabilities get capabilities => paseoServerCapabilities;
  @override
  String? get directory => _directory;
  @override
  String? get workspace => null;
  @override
  bool get isClosed => _closed;

  String get _scope {
    final value = _directory;
    if (value == null ||
        value.isEmpty ||
        value.length > 4096 ||
        RegExp(r'[\x00-\x1f\x7f]').hasMatch(value)) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
    return value;
  }

  @override
  void setLocation({String? directory, String? workspace}) {
    if (workspace != null && workspace.isNotEmpty) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    if (_directory == directory) return;
    _directory = directory;
    _locationEpoch++;
    _providerRevision++;
    _agents.clear();
    _sessions.clear();
    _statuses.clear();
    _drafts.clear();
    _uncertain.clear();
    _awaitingTurn.clear();
    _turnActive.clear();
    _permissions.clear();
    _live.clear();
    _realIDs.clear();
    _appIDs.clear();
    _heldEvents.clear();
    _providerEntries = null;
    _recoveryDegraded = false;
  }

  String _real(String appID) => _realIDs[appID] ?? appID;
  String _app(String realID) => _appIDs[realID] ?? realID;

  void _checkLocation(String scope, int epoch) {
    if (_closed || scope != _directory || epoch != _locationEpoch) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
  }

  /// Records a daemon agent snapshot. Returns null when it belongs to another
  /// project folder or is archived.
  Session? _remember(Map<String, dynamic> agent) {
    final realID = agent['id'];
    if (realID is String && _appIDs.containsKey(realID)) {
      agent = {...agent, 'id': _appIDs[realID]};
    }
    final session = paseoSession(agent);
    if (session.directory != _directory || agent['archivedAt'] is String) {
      return null;
    }
    _agents[session.id] = agent;
    _sessions.remove(session.id);
    _sessions[session.id] = session;
    final status = agent['status'];
    if (status == 'error' || status == 'closed') {
      _awaitingTurn.remove(session.id);
      _turnActive.remove(session.id);
    }
    _statuses[session.id] =
        _awaitingTurn.contains(session.id) || _turnActive.contains(session.id)
        ? 'busy'
        : paseoSessionStatus(agent);
    _drafts.remove(session.id);
    _syncPermissions(session.id, agent['pendingPermissions']);
    while (_sessions.length > 1024) {
      _forget(_sessions.keys.first);
    }
    return session;
  }

  void _forget(String id) {
    _agents.remove(id);
    _sessions.remove(id);
    _statuses.remove(id);
    _drafts.remove(id);
    _uncertain.remove(id);
    _awaitingTurn.remove(id);
    _turnActive.remove(id);
    _live.remove(id);
    _permissions.removeWhere((_, value) => value.permission.sessionID == id);
    final realID = _realIDs.remove(id);
    if (realID != null) _appIDs.remove(realID);
  }

  @override
  Future<Health> health() async {
    await transport.connect();
    final version = transport.serverVersion;
    return Health(
      healthy: true,
      version: version == null
          ? 'Paseo daemon (experimental)'
          : 'Paseo daemon $version (experimental)',
    );
  }

  // ---- sessions ----------------------------------------------------------

  @override
  Future<List<Session>> sessions() async {
    final result = <Session>[];
    String? cursor;
    for (var page = 0; page < 20; page++) {
      final next = await sessionPage(cursor: cursor, limit: 200);
      result.addAll(next.items);
      cursor = next.nextCursor;
      if (cursor == null) break;
    }
    return result;
  }

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request('fetch_agents_request', {
      'sort': [
        {'key': 'updated_at', 'direction': 'desc'},
      ],
      'page': {
        'limit': limit.clamp(1, 200),
        if (cursor != null) 'cursor': paseoString(cursor, max: 4096),
      },
      // One fixed id keeps repeated list reads on a single subscription.
      'subscribe': {'subscriptionId': 'opencode-mobile'},
    });
    _checkLocation(scope, epoch);
    final items = <Session>[];
    for (final raw in paseoList(result['entries'], max: 200)) {
      final entry = paseoObject(raw);
      final session = _remember(paseoObject(entry['agent']));
      if (session != null) items.add(session);
    }
    // Conversations the user opened here but has not sent to the daemon yet.
    if (cursor == null) {
      items.insertAll(0, _drafts.map((id) => _sessions[id]).nonNulls);
    }
    final pageInfo = result['pageInfo'];
    final next = pageInfo is Map && pageInfo['hasMore'] == true
        ? pageInfo['nextCursor']
        : null;
    return ServerPage(
      items: items,
      nextCursor: next is String && next.isNotEmpty ? next : null,
    );
  }

  static String _uuid() {
    final random = Random.secure();
    final b = List<int>.generate(16, (_) => random.nextInt(256));
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    final h = b.map((v) => v.toRadixString(16).padLeft(2, '0')).join();
    return '${h.substring(0, 8)}-${h.substring(8, 12)}-${h.substring(12, 16)}-'
        '${h.substring(16, 20)}-${h.substring(20)}';
  }

  /// The runtime is only known once the composer names a model, so a new
  /// conversation is local until its first prompt. The daemon accepts a
  /// client-chosen agent id, which keeps the session id stable across that
  /// hand-off.
  @override
  Future<Session> createSession() async {
    final scope = _scope;
    await transport.connect();
    final now = DateTime.now().millisecondsSinceEpoch;
    final session = Session(
      id: _uuid(),
      title: 'New conversation',
      directory: scope,
      time: SessionTime(created: now, updated: now),
    );
    _sessions[session.id] = session;
    _statuses[session.id] = 'idle';
    _drafts.add(session.id);
    return session;
  }

  Future<Map<String, dynamic>> _fetchAgent(String id) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request('fetch_agent_request', {
      'agentId': _real(paseoString(id, max: 256)),
    });
    _checkLocation(scope, epoch);
    final agent = paseoObject(result['agent']);
    if (agent['id'] != _real(id)) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    if (_remember(agent) == null) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
    return agent;
  }

  @override
  Future<Session> session(String id) async {
    if (_drafts.contains(id)) return _sessions[id]!;
    await _fetchAgent(id);
    return _sessions[id]!;
  }

  @override
  Future<Session> getSessionDetails(String id) => session(id);

  /// Archives rather than deletes: the daemon keeps the provider's own
  /// session, so the conversation can be restored from the computer.
  @override
  Future<void> deleteSession(String id) async {
    if (!_drafts.contains(id)) {
      if (!_sessions.containsKey(id)) await _fetchAgent(id);
      await transport.request('archive_agent_request', {
        'agentId': _real(id),
      }, mutation: true);
    }
    _forget(id);
  }

  @override
  Future<void> renameSession(String id, String title) async {
    final name = paseoString(title.trim(), max: 200);
    if (_drafts.contains(id)) {
      final draft = _sessions[id]!;
      _sessions[id] = Session(
        id: id,
        title: name,
        directory: draft.directory,
        time: draft.time,
      );
      return;
    }
    if (!_sessions.containsKey(id)) await _fetchAgent(id);
    await transport.request('update_agent_request', {
      'agentId': _real(id),
      'name': name,
    }, mutation: true);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => Map.of(_statuses);

  // ---- history -----------------------------------------------------------

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    if (_drafts.contains(id) && !_uncertain.contains(id)) return const [];
    final scope = _scope;
    final epoch = _locationEpoch;
    final agent = await _fetchAgent(id);
    final result = await transport.request('fetch_agent_timeline_request', {
      'agentId': _real(id),
      'direction': 'tail',
      'limit': 0,
      'projection': 'projected',
    }, timeout: const Duration(seconds: 45));
    _checkLocation(scope, epoch);
    final busy = paseoSessionStatus(agent) == 'busy';
    final messages = paseoTimelineMessages(id, result, busy: busy);
    // Hydrated items are known to the chat from here on, so later stream
    // events for them are deltas and snapshots, never first announcements.
    final live = _live.putIfAbsent(id, _PaseoLive.new);
    live.announced.clear();
    live.runID = null;
    live.runType = null;
    for (final message in messages) {
      live.announced[message.info.id] = StringBuffer(
        message.parts.isEmpty ? '' : message.parts.first.text,
      );
    }
    final streamEpoch = result['epoch'];
    if (streamEpoch is String) live.epoch = streamEpoch;
    final end = result['endCursor'];
    if (end is Map && end['seq'] is int) live.lastSeq = end['seq'] as int;
    _uncertain.remove(id);
    return messages;
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    if (cursor != null) throw PaseoFailure(PaseoFailureKind.unavailable);
    return ServerPage(items: await messages(id));
  }

  // ---- prompts -----------------------------------------------------------

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    PromptTrace.sent(sessionID);
    if (attachments.isNotEmpty ||
        agentMentions.isNotEmpty ||
        delivery != null) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    if (_uncertain.contains(sessionID)) {
      throw PaseoFailure(PaseoFailureKind.deliveryUnknown);
    }
    final scope = _scope;
    final epoch = _locationEpoch;
    final prompt = paseoString(text, max: 1024 * 1024);
    final messageID = _uuid();
    _awaitingTurn.add(sessionID);
    try {
      if (_drafts.contains(sessionID)) {
        await _createAgent(
          sessionID,
          prompt: prompt,
          messageID: messageID,
          model: model,
          mode: agent,
          variant: variant,
        );
      } else {
        if (!_agents.containsKey(sessionID)) await _fetchAgent(sessionID);
        final runtime = _agents[sessionID]?['provider'];
        // v0.9.2 does not expose negotiated ACP loadSession proof. Never let
        // a restored snapshot or a supplied model bypass resume-only admission.
        if (!isExistingPaseoProvider(runtime)) {
          throw PaseoFailure(PaseoFailureKind.unavailable);
        }
        if (model != null && !isExistingPaseoProvider(model.providerID)) {
          throw PaseoFailure(PaseoFailureKind.unavailable);
        }
        await _checkExistingProvider(runtime as String);
        _checkLocation(scope, epoch);
        await _applySelection(sessionID, model: model, mode: agent);
        await transport.request(
          'send_agent_message_request',
          {'agentId': _real(sessionID), 'text': prompt, 'messageId': messageID},
          mutation: true,
          timeout: const Duration(seconds: 60),
        );
      }
      _checkLocation(scope, epoch);
      _statuses[sessionID] = 'busy';
    } on PaseoFailure catch (error) {
      _awaitingTurn.remove(sessionID);
      if (error.kind == PaseoFailureKind.deliveryUnknown) {
        _uncertain.add(sessionID);
      }
      rethrow;
    }
  }

  Future<void> _createAgent(
    String id, {
    required String prompt,
    required String messageID,
    ModelRef? model,
    String? mode,
    String? variant,
  }) async {
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final provider = model == null || model.providerID.isEmpty
        ? paseoDefaultProvider
        : model.providerID;
    if (!isExistingPaseoProvider(provider)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    await _checkExistingProvider(provider);
    _checkLocation(scope, locationEpoch);
    final modes = _modesFor(provider);
    final draft = _sessions[id];
    final title = draft?.title;
    final Map<String, dynamic> result;
    _creating++;
    try {
      result = await transport.request(
        'create_agent_request',
        {
          'config': {
            'provider': provider,
            'cwd': _scope,
            if (model != null &&
                model.modelID.isNotEmpty &&
                model.modelID != paseoDefaultModel)
              'model': model.modelID,
            if (mode != null && modes.contains(mode)) 'modeId': mode,
            if (variant != null && variant.isNotEmpty)
              'thinkingOptionId': variant,
            if (title != null && title != 'New conversation') 'title': title,
          },
          'initialPrompt': prompt,
          'clientMessageId': messageID,
          'labels': <String, String>{},
        },
        mutation: true,
        timeout: const Duration(seconds: 90),
      );
    } finally {
      _creating--;
      if (_creating == 0 && _closed) _heldEvents.clear();
    }
    final agent = paseoObject(result['agent']);
    final realID = paseoString(agent['id'], max: 256);
    _realIDs[id] = realID;
    _appIDs[realID] = id;
    _remember(agent);
    _drafts.remove(id);
    if (_creating == 0) {
      final held = _heldEvents.toList();
      _heldEvents.clear();
      held.forEach(_onEvent);
    }
  }

  /// Applies a changed mode or model before a follow-up turn. A selection the
  /// agent's runtime does not offer is ignored rather than refused: the
  /// composer's choices are global while modes and models are per runtime.
  Future<void> _applySelection(
    String id, {
    ModelRef? model,
    String? mode,
  }) async {
    final agent = _agents[id];
    if (agent == null) return;
    final available = agent['availableModes'];
    final modeIDs = available is List
        ? available.whereType<Map>().map((m) => m['id']).whereType<String>()
        : const <String>[];
    if (mode != null &&
        mode != agent['currentModeId'] &&
        modeIDs.contains(mode)) {
      await transport.request('set_agent_mode_request', {
        'agentId': _real(id),
        'modeId': mode,
      }, mutation: true);
      agent['currentModeId'] = mode;
    }
    if (model != null &&
        model.providerID == agent['provider'] &&
        model.modelID.isNotEmpty &&
        model.modelID != paseoDefaultModel &&
        model.modelID != agent['model']) {
      await transport.request('set_agent_model_request', {
        'agentId': _real(id),
        'modelId': model.modelID,
      }, mutation: true);
      agent['model'] = model.modelID;
    }
  }

  @override
  Future<void> abort(String sessionID) async {
    if (_drafts.contains(sessionID)) return;
    await transport.request('cancel_agent_request', {
      'agentId': _real(paseoString(sessionID, max: 256)),
    }, mutation: true);
  }

  // ---- permissions -------------------------------------------------------

  @override
  Future<List<PermissionRequest>> pendingPermissions() async =>
      _permissions.values.map((p) => p.permission).toList();
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => const [];

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    final pending = _permissions[requestID];
    if (pending == null ||
        (legacySessionID != null &&
            legacySessionID != pending.permission.sessionID)) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    if (pending.hostRequest != null) {
      if (reply == 'always') throw PaseoFailure(PaseoFailureKind.unavailable);
      if (!{'once', 'reject', 'cancel'}.contains(reply)) {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
      String? action;
      if (reply == 'once') {
        final allow = pending.hostRequest!.choices
            .where(
              (choice) =>
                  choice.behavior == HostAgentPermissionBehavior.allowOnce,
            )
            .toList();
        // Multiple choices require the exact-action card, not a guessed choice.
        if (allow.length != 1) throw PaseoFailure(PaseoFailureKind.unavailable);
        action = allow.single.actionId;
      }
      return respondHostAgentPermission(requestID, selectedActionId: action);
    }
    if (reply == 'always' && pending.suggestions.isEmpty) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final response = switch (reply) {
      'once' => <String, dynamic>{'behavior': 'allow'},
      // The provider's own suggested rule ("accept edits for this session").
      'always' => <String, dynamic>{
        'behavior': 'allow',
        if (pending.suggestions.isNotEmpty)
          'updatedPermissions': pending.suggestions,
      },
      'reject' => <String, dynamic>{
        'behavior': 'deny',
        if (message != null && message.trim().isNotEmpty)
          'message': paseoText(message.trim(), max: 4096),
      },
      'cancel' => <String, dynamic>{'behavior': 'deny', 'interrupt': true},
      _ => throw PaseoFailure(PaseoFailureKind.unavailable),
    };
    _checkPermissionEpoch(pending);
    transport.send('agent_permission_response', {
      'agentId': _real(pending.permission.sessionID),
      'requestId': requestID,
      'response': response,
    }, expectedEpoch: pending.epoch);
    _answered.add(requestID);
    while (_answered.length > 256) {
      _answered.remove(_answered.first);
    }
    _resolvePermission(requestID);
  }

  void _addPermission(String agentID, Map<String, dynamic> request) {
    final hostRequest = paseoHostPermission(
      agentID,
      request,
      provider: _agents[agentID]?['provider'] as String?,
    );
    final permission = hostRequest == null
        ? paseoPermission(agentID, request)
        : paseoPermission(agentID, {
            'id': hostRequest.requestId,
            'name': 'tool',
            'kind': 'tool',
            // Only the known structured task preview is exposed. Raw ACP
            // requests, diagnostic labels and arbitrary inputs stay private.
            'detail': request['detail'],
            'description': 'The agent needs your permission.',
          });
    if (_permissions.containsKey(permission.id) ||
        _answered.contains(permission.id)) {
      return;
    }
    while (_permissions.length >= 64) {
      _permissions.remove(_permissions.keys.first);
    }
    final suggestions = request['suggestions'];
    _permissions[permission.id] = _PaseoPermission(
      transport.epoch,
      permission,
      hostRequest == null && suggestions is List ? suggestions : const [],
      hostRequest,
    );
    _emit('permission.asked', {
      'id': permission.id,
      'sessionID': agentID,
      'permission': permission.permission,
      'patterns': permission.patterns,
      'metadata': permission.metadata,
      'always': permission.always,
      if (permission.tool != null)
        'tool': {
          'messageID': permission.tool!.messageID,
          'callID': permission.tool!.callID,
        },
      if (permission.message != null) 'message': permission.message,
    });
  }

  void _resolvePermission(String requestID) {
    final removed = _permissions.remove(requestID);
    if (removed == null) return;
    _emit('permission.replied', {
      'sessionID': removed.permission.sessionID,
      'requestID': requestID,
    });
  }

  /// An agent snapshot lists everything still waiting, so it both adds
  /// requests this client missed and retires ones answered elsewhere.
  void _syncPermissions(String agentID, Object? pending) {
    if (pending is! List) return;
    final current = <String>{};
    for (final raw in pending.take(64)) {
      if (raw is! Map<String, dynamic>) continue;
      try {
        final id = raw['id'];
        if (id is String) current.add(id);
        _addPermission(agentID, raw);
      } on PaseoFailure {
        // A malformed request cannot become an approval card.
      }
    }
    final resolved = _permissions.entries
        .where(
          (entry) =>
              entry.value.permission.sessionID == agentID &&
              !current.contains(entry.key),
        )
        .map((entry) => entry.key)
        .toList();
    resolved.forEach(_resolvePermission);
  }

  void _checkPermissionEpoch(_PaseoPermission pending) {
    if (!transport.connected || pending.epoch != transport.epoch) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
  }

  @override
  Future<List<HostAgentPermissionRequest>>
  pendingHostAgentPermissions() async => List.unmodifiable(
    _permissions.values
        .map((pending) => pending.hostRequest)
        .whereType<HostAgentPermissionRequest>(),
  );

  @override
  Future<void> respondHostAgentPermission(
    String requestId, {
    String? selectedActionId,
  }) async {
    final pending = _permissions[requestId];
    final request = pending?.hostRequest;
    if (pending == null || request == null) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    _checkPermissionEpoch(pending);
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    final realAgentId = _real(request.sessionId);
    HostAgentPermissionChoice? choice;
    if (selectedActionId != null) {
      for (final offered in request.choices) {
        if (offered.actionId == selectedActionId) choice = offered;
      }
      if (choice == null) throw PaseoFailure(PaseoFailureKind.staleRequest);
    } else {
      for (final offered in request.choices) {
        if (offered.behavior == HostAgentPermissionBehavior.rejectOnce) {
          choice = offered;
          break;
        }
      }
    }
    if (choice == null) {
      // Paseo's implicit deny falls back to reject_always. Cancel the turn
      // instead, which resolves ACP pending requests with outcome=cancelled.
      final result = await transport.request(
        'cancel_agent_request',
        {'agentId': realAgentId},
        mutation: true,
        expectedEpoch: pending.epoch,
      );
      _checkLocation(scope, locationEpoch);
      _checkPermissionEpoch(pending);
      final current = _permissions[requestId];
      if (current != null && !identical(current, pending)) {
        throw PaseoFailure(PaseoFailureKind.staleRequest);
      }
      final agent = result['agent'];
      final stillPending = agent is Map ? agent['pendingPermissions'] : null;
      // An idle agent may acknowledge cancel without interrupting anything.
      // Acknowledgement alone must not retire an unanswered approval card.
      if (identical(_permissions[requestId], pending) &&
          (agent is! Map ||
              agent['id'] != realAgentId ||
              agent['cwd'] != _scope ||
              stillPending is! List ||
              stillPending.any(
                (raw) => raw is Map && raw['id'] == requestId,
              ))) {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
    } else {
      transport.send('agent_permission_response', {
        'agentId': _real(request.sessionId),
        'requestId': requestId,
        'response': {
          'behavior': choice.behavior == HostAgentPermissionBehavior.allowOnce
              ? 'allow'
              : 'deny',
          'selectedActionId': choice.actionId,
        },
      }, expectedEpoch: pending.epoch);
    }
    _answered.add(requestId);
    while (_answered.length > 256) {
      _answered.remove(_answered.first);
    }
    _resolvePermission(requestId);
  }

  @override
  Future<HostAgentProviderCatalog> loadHostAgentProviders({
    bool refresh = false,
  }) async {
    if (refresh) {
      _providerEntries = null;
      _providerRevision++;
      final scope = _scope;
      final location = _locationEpoch;
      await transport.request('refresh_providers_snapshot_request', {
        'cwd': scope,
      });
      _checkLocation(scope, location);
    }
    return paseoHostAgentCatalog(await _providers());
  }

  @override
  Future<HostAgentContinuation> loadHostAgentContinuation(
    String sessionId,
  ) async {
    final scope = _scope;
    final location = _locationEpoch;
    if (!_agents.containsKey(sessionId)) await _fetchAgent(sessionId);
    _checkLocation(scope, location);
    final agent = _agents[sessionId];
    if (agent == null) throw PaseoFailure(PaseoFailureKind.unavailable);
    final provider = paseoString(agent['provider'], max: 128);
    final handle = agent['persistence'];
    final intact =
        handle is Map &&
        handle['provider'] == provider &&
        handle['sessionId'] is String &&
        (handle['sessionId'] as String).isNotEmpty &&
        (handle['sessionId'] as String).length <= 512;
    return HostAgentContinuation(
      sessionId: sessionId,
      providerId: provider,
      state: isExistingPaseoProvider(provider)
          ? HostAgentContinuationState.existingRoute
          : intact
          ? HostAgentContinuationState.resumeUnverified
          : HostAgentContinuationState.missingHandle,
    );
  }

  Future<void> _checkExistingProvider(String id) async {
    for (final entry in await _providers()) {
      if (entry['provider'] == id && entry['source'] == 'custom') {
        throw PaseoFailure(PaseoFailureKind.unavailable);
      }
    }
  }

  // ---- runtimes, models and modes ---------------------------------------

  Future<List<Map<String, dynamic>>> _providers() async {
    final cached = _providerEntries;
    if (cached != null) return cached;
    final scope = _scope;
    final epoch = _locationEpoch;
    final revision = _providerRevision;
    // Just after the daemon starts, every runtime reports `loading` while it
    // asks each CLI for its models. The app reads providers once per
    // connection, so answering with that empty moment left the composer with
    // no default model for the whole session. Wait briefly for a settled
    // snapshot; past the limit, answer with what is ready and cache nothing.
    var entries = const <Map<String, dynamic>>[];
    for (var attempt = 0; attempt < providerWarmupAttempts; attempt++) {
      if (attempt > 0) await Future<void>.delayed(providerWarmupInterval);
      final result = await transport.request('get_providers_snapshot_request', {
        'cwd': scope,
      }, timeout: const Duration(seconds: 45));
      _checkLocation(scope, epoch);
      entries = paseoList(
        result['entries'],
        max: 256,
      ).whereType<Map<String, dynamic>>().toList();
      if (entries.every((entry) => entry['status'] != 'loading')) {
        if (revision == _providerRevision) _providerEntries = entries;
        break;
      }
    }
    return entries;
  }

  List<String> _modesFor(String provider) {
    for (final entry in _providerEntries ?? const <Map<String, dynamic>>[]) {
      if (entry['provider'] != provider) continue;
      final modes = entry['modes'];
      if (modes is! List) return const [];
      return modes
          .whereType<Map>()
          .map((mode) => mode['id'])
          .whereType<String>()
          .toList();
    }
    return const [];
  }

  @override
  Future<ProvidersResponse> providers() async {
    final providers = <ProviderInfo>[];
    String? defaultProvider;
    String? defaultModel;
    for (final entry in await _providers()) {
      final id = entry['provider'];
      if (id is! String ||
          !isExistingPaseoProvider(id) ||
          entry['source'] == 'custom') {
        continue;
      }
      if (entry['enabled'] == false || entry['status'] != 'ready') continue;
      final modelIDs = <String>[];
      final modelData = <String, Map<String, dynamic>>{};
      String? providerDefault;
      final models = entry['models'];
      for (final raw in models is List ? models.take(500) : const []) {
        if (raw is! Map<String, dynamic>) continue;
        final modelID = raw['id'];
        if (modelID is! String || modelID.isEmpty || modelID.length > 256) {
          continue;
        }
        if (raw['isSelectable'] == false || modelIDs.contains(modelID)) {
          continue;
        }
        modelIDs.add(modelID);
        final label = raw['label'];
        modelData[modelID] = {
          'id': modelID,
          'name': label is String && label.isNotEmpty ? label : modelID,
        };
        if (raw['isDefault'] == true) providerDefault ??= modelID;
      }
      if (modelIDs.isEmpty) {
        modelIDs.add(paseoDefaultModel);
        modelData[paseoDefaultModel] = {
          'id': paseoDefaultModel,
          'name': 'Default model',
        };
      }
      providers.add(
        ProviderInfo(
          id: id,
          name: paseoProviderName(id),
          modelIDs: modelIDs,
          modelData: modelData,
        ),
      );
      if (defaultProvider == null || id == paseoDefaultProvider) {
        defaultProvider = id;
        defaultModel = providerDefault ?? modelIDs.first;
      }
    }
    return ProvidersResponse(
      providers: providers,
      defaultProviderID: defaultProvider,
      defaultModelID: defaultModel,
    );
  }

  @override
  Future<ProvidersResponse> configuredProviders() => providers();

  /// The daemon's permission modes stand where OpenCode's agents do: both
  /// pick how much the run may do without asking.
  @override
  Future<List<AgentInfo>> agents() async {
    await _providers();
    // Modes differ per runtime; the composer has one list. It shows the
    // default runtime's, and a mode another runtime lacks is simply ignored.
    final names = _modesFor(paseoDefaultProvider).toList();
    if (names.isEmpty) names.add('default');
    return [for (final name in names) AgentInfo(name: name, mode: 'primary')];
  }

  @override
  Future<ChatDefaults> loadChatDefaults() async {
    final response = await providers();
    final provider = response.defaultProviderID;
    final model = response.defaultModelID;
    final modes = provider == null ? const <String>[] : _modesFor(provider);
    return ChatDefaults(
      model: provider == null || model == null
          ? null
          : ModelRef(providerID: provider, modelID: model),
      agent: modes.contains('default')
          ? 'default'
          : modes.isEmpty
          ? 'default'
          : modes.first,
    );
  }

  @override
  Future<List<PendingQuestion>> listQuestions() async => const [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileDiff>> diff(String id) async => const [];
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
  @override
  Future<List<Session>> listSessionChildren(String id) async => const [];
  @override
  Future<List<CommandInfo>> listCommands() async => const [];
  @override
  Future<List<SkillInfo>> listSkills() async => const [];
  @override
  Future<List<ReferenceInfo>> listReferences() async => const [];
  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      BackgroundWorkSupport.unavailable;
  @override
  Future<List<WorkspaceProject>> listProjects() async => [
    WorkspaceProject(
      id: _scope,
      name: _scope,
      directory: _scope,
      worktrees: const [],
      updatedAt: 0,
    ),
  ];
  @override
  Future<WorkspaceProject?> loadCurrentProject() async =>
      (await listProjects()).single;

  // Unavailable operations throw a typed domain-compatible error instead of
  // pretending that a server mutation succeeded. UI capability gates hide them.
  @override
  dynamic noSuchMethod(Invocation invocation) =>
      throw PaseoFailure(PaseoFailureKind.unavailable);

  // ---- live events -------------------------------------------------------

  void _emit(String type, Map<String, dynamic> properties) {
    if (_closed) return;
    _events.add(
      EventEnvelope(type: type, properties: properties, directory: _directory),
    );
  }

  void _emitState(StreamStatus state) {
    if (!_closed && _listening) _streamStates.add(state);
  }

  void _emitStatus(String id, String status) {
    if (_statuses[id] == status) return;
    _statuses[id] = status;
    _emit('session.status', {
      'sessionID': id,
      'status': {'type': status},
    });
  }

  void _onEvent(PaseoEvent event) {
    if (_closed || event.epoch != transport.epoch) return;
    try {
      final p = event.payload;
      final rawID =
          p['agentId'] ?? (p['agent'] is Map ? p['agent']['id'] : null);
      if (_creating > 0 &&
          rawID is String &&
          !_sessions.containsKey(_app(rawID)) &&
          _heldEvents.length < 2048) {
        _heldEvents.add(event);
        return;
      }
      final agentID = rawID is String ? _app(rawID) : null;
      switch (event.type) {
        case 'agent_update':
          if (p['kind'] == 'remove') {
            _removed(agentID);
          } else if (p['agent'] is Map<String, dynamic>) {
            _upserted(p['agent'] as Map<String, dynamic>);
          }
        case 'agent_stream':
          final inner = p['event'];
          if (agentID != null &&
              _sessions.containsKey(agentID) &&
              inner is Map<String, dynamic>) {
            _onStream(agentID, inner, p);
          }
        case 'agent_permission_request':
          final request = p['request'];
          if (agentID != null &&
              _sessions.containsKey(agentID) &&
              request is Map<String, dynamic>) {
            _addPermission(agentID, request);
          }
        case 'agent_permission_resolved':
          final requestID = p['requestId'];
          if (requestID is String) _resolvePermission(requestID);
        case 'agent_deleted' || 'agent_archived':
          _removed(agentID);
        case 'providers_snapshot_update':
          _providerRevision++;
          _providerEntries = null;
      }
    } catch (_) {
      // A malformed push is dropped. No payload reaches logs.
    }
  }

  void _upserted(Map<String, dynamic> agent) {
    final realID = agent['id'];
    if (realID is! String) return;
    final id = _app(realID);
    final known = _agents.containsKey(id);
    final before = _statuses[id];
    if (agent['archivedAt'] is String) {
      _removed(id);
      return;
    }
    final session = _remember(agent);
    if (session == null) return;
    _emit(known ? 'session.updated' : 'session.created', {
      'info': paseoSessionJson(session),
    });
    final status = _statuses[id]!;
    if (before != status) {
      // _remember already stored the new value; announce the change.
      _statuses[id] = before ?? 'idle';
      _emitStatus(id, status);
      if (status == 'idle') _emit('session.idle', {'sessionID': id});
    }
  }

  void _removed(Object? id) {
    if (id is! String || !_sessions.containsKey(id)) return;
    final session = _sessions[id]!;
    _forget(id);
    _emit('session.deleted', {'info': paseoSessionJson(session)});
  }

  void _onStream(
    String id,
    Map<String, dynamic> event,
    Map<String, dynamic> payload,
  ) {
    final live = _live.putIfAbsent(id, _PaseoLive.new);
    final seq = payload['seq'];
    final epoch = payload['epoch'];
    if (seq is int && epoch is String) {
      if (live.epoch == epoch && seq <= live.lastSeq) return;
      live.epoch = epoch;
      live.lastSeq = seq;
    }
    switch (event['type']) {
      case 'turn_started':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.add(id);
        _emitStatus(id, 'busy');
      case 'turn_completed' || 'turn_canceled':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.remove(id);
        _emitStatus(id, 'idle');
        // A completion prompts authoritative hydration; idle itself is not
        // reported as a successful run.
        _emit('session.idle', {'sessionID': id});
      case 'turn_failed':
        live.runID = null;
        _awaitingTurn.remove(id);
        _turnActive.remove(id);
        _emitStatus(id, 'idle');
        _emit('session.error', {
          'sessionID': id,
          'error': {
            'name': 'PaseoTurnFailed',
            'data': {
              'message':
                  'The agent could not finish this reply. Check it on your computer.',
            },
          },
        });
        _emit('session.idle', {'sessionID': id});
      case 'permission_requested':
        final request = event['request'];
        if (request is Map<String, dynamic>) _addPermission(id, request);
      case 'permission_resolved':
        final requestID = event['requestId'];
        if (requestID is String) _resolvePermission(requestID);
      case 'timeline':
        final item = event['item'];
        if (item is Map<String, dynamic>) {
          _onTimelineItem(
            id,
            live,
            item,
            provider: paseoText(event['provider'], max: 128),
            seq: seq is int ? seq : null,
            at: paseoMillis(payload['timestamp']),
          );
        }
    }
  }

  void _onTimelineItem(
    String id,
    _PaseoLive live,
    Map<String, dynamic> item, {
    required String provider,
    required int? seq,
    required int? at,
  }) {
    final type = item['type'];
    if (type is! String) return;
    final streamed = type == 'assistant_message' || type == 'reasoning';
    String messageID;
    final own = type == 'reasoning' ? null : item['messageId'];
    if (streamed && (own is! String || own.isEmpty)) {
      // A run of id-less deltas shares the id of its first delta.
      if (live.runType != type || live.runID == null) {
        live.runType = type;
        live.runID = paseoItemID(item, fallbackSeq: seq);
      }
      messageID = live.runID!;
    } else {
      live.runID = null;
      live.runType = null;
      messageID = paseoItemID(item, fallbackSeq: seq);
    }
    final text = live.announced[messageID];
    if (streamed && text != null) {
      final delta = paseoText(item['text']);
      text.write(delta);
      _emit('message.part.delta', {
        'sessionID': id,
        'messageID': messageID,
        'partID': '$messageID:0',
        'field': 'text',
        'delta': delta,
      });
      return;
    }
    final message = paseoItemMessage(
      id,
      item,
      id: messageID,
      provider: provider,
      created: at ?? DateTime.now().millisecondsSinceEpoch,
      completed: type == 'user_message' ? at : null,
    );
    if (message == null) return;
    if (text == null) {
      live.announced[messageID] = StringBuffer(
        streamed ? paseoText(item['text']) : '',
      );
      while (live.announced.length > 4096) {
        live.announced.remove(live.announced.keys.first);
      }
      _emit('message.updated', {'info': paseoMessageJson(message.info)});
    }
    for (final part in message.parts) {
      _emit('message.part.updated', {
        'sessionID': id,
        'part': {...paseoPartJson(part), 'sessionID': id},
      });
    }
  }

  // ---- connection lifecycle ---------------------------------------------

  void _scheduleReconnect() {
    if (!_listening || _closed || _retry != null || _recovering != null) return;
    final delay = Duration(seconds: 1 << _retryAttempt.clamp(0, 4));
    _retryAttempt++;
    _retry = Timer(delay, () {
      _retry = null;
      unawaited(_recover());
    });
  }

  Future<void> _recover() => _recovering ??= _recoverNow().whenComplete(() {
    _recovering = null;
    if (!transport.connected || _recoveryDegraded) _scheduleReconnect();
  });

  Future<void> _recoverNow() async {
    try {
      await transport.connect();
      // Turn boundaries may have been missed while away; the snapshots read
      // next are the authority on what is still running.
      _awaitingTurn.clear();
      _turnActive.clear();
      // Listing re-establishes the agent subscription on the new socket and
      // refreshes statuses and pending permissions missed while away.
      if (_directory != null) await sessionPage(limit: 200);
      if (_closed || !_listening) return;
      _recoveryDegraded = false;
      _retryAttempt = 0;
      _emitState(StreamStatus.connected);
    } on PaseoFailure catch (error) {
      _recoveryDegraded = true;
      final terminal =
          error.kind == PaseoFailureKind.authentication ||
          error.kind == PaseoFailureKind.hostRefused;
      _emitState(
        terminal ? StreamStatus.disconnected : StreamStatus.reconnecting,
      );
      if (terminal) {
        if (_events.hasListener) _events.addError(error);
        _listening = false;
      }
    }
  }

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) {
    _listening = true;
    final events = _events.stream.listen(
      onEvent,
      // A channel without an error callback still needs to consume the
      // terminal authentication error emitted during reconnect.
      onError: onError ?? (Object _) {},
    );
    final states = _streamStates.stream.listen(onStatus);
    return _PaseoEventChannel(
      () async {
        await events.cancel();
        await states.cancel();
        _listening = false;
        _retry?.cancel();
        _retry = null;
      },
      onStart: () {
        if (!_closed && _listening) {
          _emitState(StreamStatus.connecting);
          unawaited(_recover());
        }
      },
    );
  }

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) => _PaseoEventChannel(() async {});

  @override
  void close() {
    if (_closed) return;
    _closed = true;
    _listening = false;
    _locationEpoch++;
    _providerRevision++;
    _retry?.cancel();
    _retry = null;
    _agents.clear();
    _sessions.clear();
    _statuses.clear();
    _drafts.clear();
    _uncertain.clear();
    _awaitingTurn.clear();
    _turnActive.clear();
    _permissions.clear();
    _live.clear();
    _realIDs.clear();
    _appIDs.clear();
    _heldEvents.clear();
    unawaited(_daemonEvents.cancel());
    unawaited(_daemonDisconnects.cancel());
    unawaited(transport.close());
    unawaited(_events.close());
    unawaited(_streamStates.close());
  }
}

class _PaseoEventChannel implements LiveEventChannel {
  final Future<void> Function() disposeCallback;
  final void Function()? onStart;
  bool _disposed = false;
  bool _started = false;
  _PaseoEventChannel(this.disposeCallback, {this.onStart});
  @override
  void start() {
    if (_disposed || _started) return;
    _started = true;
    onStart?.call();
  }

  @override
  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await disposeCallback();
  }
}

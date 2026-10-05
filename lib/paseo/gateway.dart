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

part 'gateway/capabilities.dart';
part 'gateway/sessions.dart';
part 'gateway/prompts.dart';
part 'gateway/permissions.dart';
part 'gateway/providers.dart';
part 'gateway/events.dart';
part 'gateway/lifecycle.dart';

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
  final _draftProviders = <String, String>{};
  final _liveAgentSessions = <String>{};
  final _uncertain = <String>{};

  /// Sessions whose prompt was accepted but whose turn has not started. The
  /// daemon reports a freshly spawned agent as idle for a moment before the
  /// turn begins; that gap must not read as a finished run.
  final _awaitingTurn = <String>{};

  /// Sessions with a turn the stream says is running. Agent snapshots lag the
  /// stream (an `idle` snapshot can land after `turn_started`), so while a
  /// turn is open the stream owns the busy state.
  final _turnActive = <String>{};
  final _watching = <String>{};
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

  final Map<String, String> defaultProviderModes;

  PaseoGateway({
    required this.transport,
    String? directory,
    Map<String, String> defaultProviderModes = const {},
  }) : defaultProviderModes = Map.unmodifiable(defaultProviderModes),
       _directory = directory {
    _daemonEvents = transport.events.listen(_onEvent);
    _daemonDisconnects = transport.disconnects.listen((_) {
      _liveAgentSessions.clear();
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
    _draftProviders.clear();
    _liveAgentSessions.clear();
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

  /// True while the daemon holds [appID]'s agent in memory (starting,
  /// running or idle): opening it shows the live conversation. A closed
  /// agent only survives as a record, and reopening it is unverified.
  bool isAgentLoaded(String appID) => switch (_agents[appID]?['status']) {
    'initializing' || 'running' || 'idle' => true,
    _ => false,
  };

  /// True when [appID]'s record carries its runtime's own session handle,
  /// so [resumeHostAgentChat] can try to reopen it.
  bool canResumeAgent(String appID) {
    final agent = _agents[appID];
    final handle = agent?['persistence'];
    return agent != null &&
        handle is Map &&
        handle['sessionId'] is String &&
        (handle['sessionId'] as String).isNotEmpty &&
        handle['provider'] == agent['provider'];
  }

  /// The daemon's own id for [appID] once its agent exists (a draft keeps
  /// its app id until the first prompt creates it). Another gateway on the
  /// same daemon opens the conversation by this id.
  String daemonSessionId(String appID) => _real(appID);
  String _app(String realID) => _appIDs[realID] ?? realID;

  void _checkLocation(String scope, int epoch) {
    if (_closed || scope != _directory || epoch != _locationEpoch) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
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

  /// Runtime identity of a scoped snapshot, including a local empty draft.
  String? providerIdForSession(String sessionID) {
    final provider = _agents[sessionID]?['provider'];
    return provider is String ? provider : _draftProviders[sessionID];
  }

  /// Keeps the agent chip selection through an empty draft's first prompt.
  /// Availability is rechecked against the daemon when that prompt is sent.
  void seedDraftProviderForSession(String sessionID, String providerId) {
    if (!_drafts.contains(sessionID) || !isPaseoProviderId(providerId)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    _draftProviders[sessionID] = providerId;
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

  /// A reply loaded while still running can finish before this gateway hears
  /// its stream (another gateway started it). Without stream events, ask the
  /// daemon every 2 s for up to 3 min; two idle answers in a row end the turn
  /// here too, so the reply isn't later shown as cut off.
  Future<void> _watchUntilIdle(String id) async {
    if (!_watching.add(id)) return;
    try {
      var idle = 0;
      for (var i = 0; i < 90 && !_closed; i++) {
        await Future<void>.delayed(const Duration(seconds: 2));
        if (_closed || _turnActive.contains(id)) return;
        final Map<String, dynamic> agent;
        try {
          agent = await _fetchAgent(id);
        } catch (_) {
          continue;
        }
        if (paseoSessionStatus(agent) == 'busy') {
          idle = 0;
          continue;
        }
        if (++idle < 2) continue;
        _emitStatus(id, 'idle');
        _emit('session.idle', {'sessionID': id});
        return;
      }
    } finally {
      _watching.remove(id);
    }
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
    if (busy) unawaited(_watchUntilIdle(id));
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
        if (runtime is! String) {
          throw PaseoFailure(PaseoFailureKind.unavailable);
        }
        await _requireProviderAvailable(runtime);
        _checkLocation(scope, epoch);
        if (model != null && model.providerID != runtime) {
          throw PaseoFailure(PaseoFailureKind.unavailable);
        }
        // A newly created/live session can continue without restoration proof.
        // Reopened ACP rows need explicit new-chat acknowledgement: the pinned
        // host may otherwise fall back to a fresh native session behind this ID.
        if ((await loadHostAgentContinuation(sessionID)).requiresNewChat) {
          throw PaseoFailure(PaseoFailureKind.newChatRequired);
        }
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
      state: _liveAgentSessions.contains(sessionId)
          ? HostAgentContinuationState.liveSession
          : !intact
          ? HostAgentContinuationState.missingHandle
          : isExistingPaseoProvider(provider)
          ? HostAgentContinuationState.existingRoute
          : HostAgentContinuationState.resumeUnverified,
    );
  }

  /// Reopens a conversation the helper no longer holds (it ran before the
  /// helper last started) through the runtime's own session resume:
  /// `resume_agent_request` with the agent's persistence handle. Returns the
  /// id the conversation opens by. Throws when the daemon or the runtime
  /// refuses, so the caller can offer a new conversation instead.
  Future<String> resumeHostAgentChat(String sessionId) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    if (!_agents.containsKey(sessionId)) await _fetchAgent(sessionId);
    _checkLocation(scope, epoch);
    final agent = _agents[sessionId];
    final handle = agent?['persistence'];
    if (agent == null ||
        handle is! Map ||
        handle['sessionId'] is! String ||
        handle['provider'] != agent['provider']) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final result = await transport.request(
      'resume_agent_request',
      {'handle': handle},
      mutation: true,
      timeout: const Duration(seconds: 90),
    );
    _checkLocation(scope, epoch);
    final resumed = paseoObject(result['agent']);
    final realID = paseoString(resumed['id'], max: 256);
    // The resumed agent may be a new record: the row the person tapped
    // opens it.
    if (realID != _real(sessionId)) {
      _realIDs[sessionId] = realID;
      _appIDs[realID] = sessionId;
    }
    if (_remember(resumed) == null) {
      throw PaseoFailure(PaseoFailureKind.scopeMismatch);
    }
    _liveAgentSessions.add(sessionId);
    return realID;
  }

  @override
  Future<String> startNewHostAgentChat(
    String sessionId, {
    required bool newChatAcknowledged,
  }) async {
    if (!newChatAcknowledged) {
      throw PaseoFailure(PaseoFailureKind.newChatRequired);
    }
    final scope = _scope;
    final epoch = _locationEpoch;
    final continuation = await loadHostAgentContinuation(sessionId);
    await _requireProviderAvailable(continuation.providerId);
    _checkLocation(scope, epoch);
    final draft = await createSession();
    _checkLocation(scope, epoch);
    seedDraftProviderForSession(draft.id, continuation.providerId);
    return draft.id;
  }

  @override
  Future<ProvidersResponse> providers() async {
    final providers = <ProviderInfo>[];
    String? defaultProvider;
    String? defaultModel;
    for (final entry in await _providers()) {
      final id = entry['provider'];
      if (id is! String || !paseoProviderCanStart(entry)) continue;
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
        if (raw['isDefault'] == true) {
          providerDefault ??= modelID;
          modelData[modelID]!['isDefault'] = true;
        }
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
          name: paseoHostAgentName(id),
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

  // ---- connection lifecycle ---------------------------------------------

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
    _draftProviders.clear();
    _liveAgentSessions.clear();
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

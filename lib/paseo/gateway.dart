/// Optional experimental Paseo gateway (daemon 0.8.0, protocol 1).
///
/// The Paseo daemon runs beside the agent CLIs on the user's computer and
/// drives Claude Code, Pi, Codex and others for this app. One daemon *agent*
/// is one conversation and is shown as a session; the model picker's provider
/// chooses which runtime a new conversation uses, and the daemon's permission
/// modes are offered where OpenCode offers agents.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart' as crypto;

import '../api/models.dart';
import '../api/gen_ui_history_http.dart';
import '../domain/agent_tools/agent_tool_adapter.dart';
import '../domain/agent_tools/browser_claude_launch.dart';
import '../domain/genui/gen_ui_history.dart';
import '../domain/server_gateway.dart';
import 'mappers.dart';
import 'host_agent_providers.dart';
import 'transport.dart';
import '../diagnostics/perf_trace.dart';

part 'gateway/capabilities.dart';
part 'gateway/sessions.dart';
part 'gateway/prompts.dart';
part 'gateway/browser_launch.dart';
part 'gateway/commands.dart';
part 'gateway/permissions.dart';
part 'gateway/questions.dart';
part 'gateway/providers.dart';
part 'gateway/events.dart';
part 'gateway/lifecycle.dart';
part 'gateway/subagents.dart';
part 'gateway/gen_ui_history.dart';
part 'gateway/payload_use.dart';
part 'gateway/idle_work.dart';

class PaseoGateway
    implements
        ServerGateway,
        ServerOperationsGateway,
        HostAgentProviderGateway,
        HostAgentPermissionGateway,
        SessionSelectionGateway,
        GenUiHistoryGateway,
        CorrelatedPromptGateway {
  final PaseoTransport transport;
  final Future<void> Function()? _beforePayloadUse;
  final void Function()? _afterPayloadUse;
  BrowserClaudeLaunchRegistry? _browserLaunches;
  String? _browserProfileId, _browserSourceId;
  String Function(String directory)? _browserSourceForDirectory;
  final _browserRequested = <String>{};
  final _browserRevisions = <String, int>{};

  /// Installed by the trusted phone host owner; remote gateways have no scope.
  void configureBrowserClaudeLaunch({
    required BrowserClaudeLaunchRegistry registry,
    required String profileId,
    required String sourceId,
    String Function(String directory)? sourceIdForDirectory,
  }) {
    if (_browserLaunches != null) {
      throw StateError('Browser launch scope is already configured.');
    }
    _browserLaunches = registry;
    _browserProfileId = profileId;
    _browserSourceId = sourceId;
    _browserSourceForDirectory = sourceIdForDirectory;
  }

  /// Explicit transient choice for this conversation. The default is off.
  /// Null enrollment never silently sends a browser-requested message.
  Future<void> setBrowserRequestedForSession(
    String sessionID, {
    required bool requested,
  }) async {
    if (_closed) throw _browserUnavailable;
    sessionID = _app(paseoString(sessionID, max: 256));
    if (requested) {
      _requireBrowserScope();
      if (!_browserLaunches!.setRequested(
        profileId: _browserProfileId!,
        sourceId: _browserSourceId!,
        sessionId: _real(sessionID),
        requested: true,
      )) {
        throw _browserUnavailable;
      }
      _browserRequested.add(sessionID);
    } else {
      _browserRequested.remove(sessionID);
      _browserRequested.remove(_real(sessionID));
      if (_browserProfileId != null && _browserSourceId != null) {
        _browserLaunches?.setRequested(
          profileId: _browserProfileId!,
          sourceId: _browserSourceId!,
          sessionId: _real(sessionID),
          requested: false,
        );
      }
      await _revokeBrowserSession(sessionID);
    }
  }

  /// Awaited by the captured source owner before source/profile deletion.
  Future<void> revokeBrowserClaudeLaunches() => _revokeBrowserSource();

  String? _directory;
  bool _closed = false;
  final _agents = <String, Map<String, dynamic>>{};
  final _sessions = <String, Session>{};
  final _statuses = <String, String>{};

  /// Helper-wide work truth. Unknown never authorizes idle admission.
  bool? get localWorkBusy => _readLocalWorkBusy();

  /// Changes to helper-wide work truth, independent of visible project rows.
  Stream<void> get localWorkChanges => _localWork.changes.stream;
  final _localWork = _PaseoWorkInventory();
  final bool _trackLocalWork;
  int _pendingPayloadWork = 0;
  final _drafts = <String>{};
  final _draftProviders = <String, String>{};

  /// The model a draft starts on (its first prompt creates the agent).
  final _draftModels = <String, ModelRef>{};
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
  final _questions = <String, _PaseoQuestion>{};
  final _nativeQuestionChanges = StreamController<void>.broadcast();
  final _nativePermissionChanges = StreamController<void>.broadcast();

  /// Inventory changes only; no transport, resume or timeline side effects.
  Stream<void> get nativePermissionChanges => _nativePermissionChanges.stream;

  ({PermissionRequest permission, Object revision})? nativePermissionSnapshot(
    String sessionID,
  ) {
    if (_closed || !transport.connected) return null;
    for (final pending in _permissions.values) {
      if (pending.permission.sessionID == sessionID &&
          !pending.genUiApproving &&
          pending.epoch == transport.epoch) {
        return (
          permission: PermissionRequest.fromJson(
            jsonDecode(jsonEncode(_permissionJson(pending.permission)))
                as Map<String, dynamic>,
          ),
          revision: pending,
        );
      }
    }
    return null;
  }

  bool isNativePermissionCurrent(
    String sessionID,
    String requestID,
    Object revision,
  ) {
    final pending = _permissions[requestID];
    return !_closed &&
        transport.connected &&
        pending != null &&
        identical(pending, revision) &&
        !pending.genUiApproving &&
        pending.epoch == transport.epoch &&
        pending.permission.sessionID == sessionID;
  }

  /// Local attention changes; subscribing never changes transport ownership.
  Stream<void> get nativeQuestionChanges => _nativeQuestionChanges.stream;
  bool hasNativeQuestion(String sessionID) => _questions.values.any(
    (pending) => pending.question.sessionID == sessionID,
  );

  /// A detached display snapshot plus an opaque revision for list replies.
  /// Reading this never connects, resumes an agent or subscribes to a timeline.
  ({PendingQuestion question, Object revision})? nativeQuestionSnapshot(
    String sessionID,
  ) {
    if (_closed || !transport.connected) return null;
    for (final pending in _questions.values) {
      if (pending.question.sessionID == sessionID &&
          pending.epoch == transport.epoch) {
        return (
          question: PendingQuestion.fromJson(_questionJson(pending)),
          revision: pending,
        );
      }
    }
    return null;
  }

  bool isNativeQuestionCurrent(
    String sessionID,
    String requestID,
    Object revision,
  ) {
    final pending = _questions[requestID];
    return !_closed &&
        transport.connected &&
        pending != null &&
        identical(pending, revision) &&
        pending.epoch == transport.epoch &&
        pending.question.sessionID == sessionID;
  }

  /// Claude Code's sub-agents, each a read-only child session (see
  /// gateway/subagents.dart), by session id.
  final _subagents = <String, _PaseoSubagent>{};

  /// The sub-agent session each tool call started, by call id.
  final _subagentByCall = <String, String>{};

  /// Tool parts shown so far, by call id (with their session id), so a
  /// sub-agent reported after its card still links to it.
  final _toolParts = <String, Map<String, dynamic>>{};
  final _subagentsLoaded = <String>{};

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

  /// Set only by the owning controller after local Agent cards qualification.
  /// Evaluated for each request so disable and profile changes take effect now.
  bool Function()? _genUiShowAllowed;
  set genUiShowAllowed(bool Function()? allowed) {
    _genUiShowAllowed = allowed;
    if (allowed == null) return;
    for (final pending in _permissions.values.toList()) {
      _tryAllowGenUiShow(pending);
    }
  }

  PaseoGateway({
    required this.transport,
    bool trackLocalWork = false,
    String? directory,
    Map<String, String> defaultProviderModes = const {},
    Future<void> Function()? beforePayloadUse,
    void Function()? afterPayloadUse,
  }) : _trackLocalWork = trackLocalWork,
       defaultProviderModes = Map.unmodifiable(defaultProviderModes),
       _beforePayloadUse = beforePayloadUse,
       _afterPayloadUse = afterPayloadUse,
       _directory = directory {
    _daemonEvents = transport.events.listen(_onEvent);
    _daemonDisconnects = transport.disconnects.listen((_) {
      _localWork.invalidate();
      unawaited(_revokeBrowserSource(clearRequests: false));
      _liveAgentSessions.clear();
      _providerEntries = null;
      _providerRevision++;
      for (final id in _questions.keys.toList()) {
        _resolveQuestion(id);
      }
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
    unawaited(_revokeBrowserSource(clearRequests: false));
    _browserRequested.clear();
    _browserRevisions.clear();
    if (directory != null && _browserSourceForDirectory != null) {
      _browserSourceId = _browserSourceForDirectory!(directory);
    }
    _directory = directory;
    _locationEpoch++;
    _providerRevision++;
    _agents.clear();
    _sessions.clear();
    _statuses.clear();
    _drafts.clear();
    _draftProviders.clear();
    _draftModels.clear();
    _liveAgentSessions.clear();
    _uncertain.clear();
    _awaitingTurn.clear();
    _turnActive.clear();
    _permissions.clear();
    _questions.clear();
    _nativeQuestionChanges.add(null);
    _nativePermissionChanges.add(null);
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

  /// The runtime's own session behind [appID] (Claude's session id): two
  /// records with the same one are one conversation (a resume leaves the
  /// old record behind). Null when the record carries none.
  String? sessionKey(String appID) {
    final handle = _agents[appID]?['persistence'];
    if (handle is! Map) return null;
    final id = handle['sessionId'];
    final provider = handle['provider'];
    return id is String && id.isNotEmpty ? '$provider:$id' : null;
  }

  /// Whether [appID]'s record has a title of its own (not the
  /// "Claude Code conversation" stand-in).
  bool hasOwnTitle(String appID) {
    final title = _agents[appID]?['title'];
    return title is String && title.trim().isNotEmpty;
  }

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
    final workRevision = _localWork.revision;
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
    await _observeWorkPage(result, cursor, workRevision);
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

  @override
  Future<Session> createSelectedSession(SessionSelection defaults) async {
    final draft = await createSession();
    final model = defaults.model;
    if (model != null && isPaseoProviderId(model.providerID)) {
      _draftModels[draft.id] = model;
      _draftProviders[draft.id] = model.providerID;
    }
    final session = draft.copyWith(
      selection: SessionSelection(model: model, agent: defaults.agent),
    );
    _sessions[session.id] = session;
    return session;
  }

  /// Changes the model of a running agent (`set_agent_model_request`), or
  /// of a draft before its first prompt.
  @override
  Future<void> setSessionModel(
    String sessionID,
    ModelRef model,
    String variant,
  ) async {
    if (_drafts.contains(sessionID)) {
      _draftModels[sessionID] = model;
      final draft = _sessions[sessionID];
      if (draft != null) {
        _sessions[sessionID] = draft.copyWith(
          selection: SessionSelection(
            model: model,
            agent: draft.selection?.agent,
          ),
        );
      }
      return;
    }
    if (!_agents.containsKey(sessionID)) await _fetchAgent(sessionID);
    final agent = _agents[sessionID];
    if (agent == null || model.providerID != agent['provider']) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final reservation = await _beforeBrowserLaunch(sessionID);
    await transport.request(
      'set_agent_model_request',
      {'agentId': _real(sessionID), 'modelId': model.modelID},
      mutation: true,
      beforeSend: () => _checkBrowserLaunch(sessionID, reservation),
    );
    agent['model'] = model.modelID;
    _remember(agent);
  }

  /// Changes how a running agent works (its mode: `set_agent_mode_request`).
  @override
  Future<void> setSessionAgent(String sessionID, String agentName) async {
    if (_drafts.contains(sessionID)) return;
    if (!_agents.containsKey(sessionID)) await _fetchAgent(sessionID);
    final agent = _agents[sessionID];
    if (agent == null) throw PaseoFailure(PaseoFailureKind.unavailable);
    final reservation = await _beforeBrowserLaunch(sessionID);
    await transport.request(
      'set_agent_mode_request',
      {'agentId': _real(sessionID), 'modeId': agentName},
      mutation: true,
      beforeSend: () => _checkBrowserLaunch(sessionID, reservation),
    );
    agent['currentModeId'] = agentName;
    _remember(agent);
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
    if (_drafts.contains(id) || _isSubagent(id)) return _sessions[id]!;
    await _fetchAgent(id);
    return _sessions[id]!;
  }

  @override
  Future<Session> getSessionDetails(String id) => session(id);

  /// Archives rather than deletes: the daemon keeps the provider's own
  /// session, so the conversation can be restored from the computer.
  @override
  Future<void> deleteSession(String id) async {
    if (_isSubagent(id)) throw _PaseoSubagents._readOnly;
    await _revokeBrowserSession(id);
    if (!_drafts.contains(id)) {
      if (!_sessions.containsKey(id)) await _fetchAgent(id);
      await transport.request('archive_agent_request', {
        'agentId': _real(id),
      }, mutation: true);
    }
    _forget(id);
  }

  /// The title [appID]'s record lost to a resume (the older record of the
  /// same conversation had it): shown here while the record has none of its
  /// own. Nothing is written to the helper (an update there moves the
  /// conversation to the top as if it had just been used).
  void keepTitle(String appID, String title) {
    if (title.trim().isEmpty) return;
    _titleHints[appID] = title;
    if (hasOwnTitle(appID)) return;
    final agent = _agents[appID];
    if (agent != null) _agents[appID] = {...agent, 'title': title};
    final session = _sessions[appID];
    if (session != null) _sessions[appID] = session.copyWith(title: title);
  }

  final _titleHints = <String, String>{};

  @override
  Future<void> renameSession(String id, String title) async {
    if (_isSubagent(id)) throw _PaseoSubagents._readOnly;
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
  Future<bool> genUiSessionIdle(String sessionID) => _genUiIdle(sessionID);

  @override
  Future<GenUiHistoryPage> genUiHistoryPage(
    String sessionID, {
    String? cursor,
    int limit = 50,
  }) => _boundedGenUiHistory(sessionID, cursor: cursor, limit: limit);

  @override
  Future<List<MessageWithParts>> messages(String id) async {
    if (_drafts.contains(id) && !_uncertain.contains(id)) return const [];
    if (_isSubagent(id)) return _subagentMessages(id);
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
    _localWork.changed();
    final linked = _linkMessages(id, messages);
    // Its sub-agents (an old conversation's too), read beside the history:
    // their cards link to them when the list arrives.
    if (_subagentsLoaded.add(id)) unawaited(_loadSubagents(id));
    return linked;
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
  String createPromptMessageID() => _uuid();

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
  }) => _promptAsync(
    sessionID,
    messageID: _uuid(),
    text: text,
    model: model,
    agent: agent,
    variant: variant,
    attachments: attachments,
    agentMentions: agentMentions,
    delivery: delivery,
  );

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) => _promptAsync(
    sessionID,
    messageID: messageID,
    existingOnly: true,
    beforeSend: beforeSend,
    text: text,
    model: model,
    agent: agent,
    variant: variant,
    attachments: attachments,
    agentMentions: agentMentions,
    delivery: delivery,
  );

  Future<void> _promptAsync(
    String sessionID, {
    required String messageID,
    bool existingOnly = false,
    void Function()? beforeSend,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) => _withPayloadUse(() async {
    // A card observed by another gateway carries the daemon ID; local state
    // stays keyed by the original app ID when this gateway created the chat.
    sessionID = _app(sessionID);
    if (existingOnly && _drafts.contains(sessionID)) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    if (messageID.isEmpty ||
        messageID.length > 256 ||
        RegExp(r'[\x00-\x1f\x7f]').hasMatch(messageID)) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    PromptTrace.sent(sessionID);
    if (_isSubagent(sessionID)) throw _PaseoSubagents._readOnly;
    if (agentMentions.isNotEmpty || delivery != null) {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    final images = paseoImages(attachments);
    if (_uncertain.contains(sessionID)) {
      throw PaseoFailure(PaseoFailureKind.deliveryUnknown);
    }
    final scope = _scope;
    final epoch = _locationEpoch;
    final prompt = paseoString(text, max: 1024 * 1024);
    _awaitingTurn.add(sessionID);
    try {
      if (_drafts.contains(sessionID)) {
        await _createAgent(
          sessionID,
          prompt: prompt,
          messageID: messageID,
          model: model ?? _draftModels[sessionID],
          mode: agent,
          variant: variant,
          images: images,
          beforeSend: beforeSend,
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
        final reservation = await _beforeBrowserLaunch(sessionID);
        _checkLocation(scope, epoch);
        await _applySelection(sessionID, model: model, mode: agent);
        final realID = _real(sessionID);
        await transport.request(
          'send_agent_message_request',
          {
            'agentId': realID,
            'text': prompt,
            'messageId': messageID,
            if (images.isNotEmpty) 'images': images,
          },
          mutation: true,
          timeout: const Duration(seconds: 60),
          beforeSend: () {
            _checkLocation(scope, epoch);
            if (_real(sessionID) != realID) {
              throw PaseoFailure(PaseoFailureKind.staleRequest);
            }
            _checkBrowserLaunch(sessionID, reservation);
            beforeSend?.call();
          },
        );
      }
      _checkLocation(scope, epoch);
      _statuses[sessionID] = 'busy';
    } catch (error) {
      _awaitingTurn.remove(sessionID);
      if (error is PaseoFailure &&
          error.kind == PaseoFailureKind.deliveryUnknown) {
        _uncertain.add(sessionID);
      }
      rethrow;
    }
  });

  @override
  Future<void> abort(String sessionID) async {
    await _revokeBrowserSession(sessionID);
    if (_drafts.contains(sessionID) || _isSubagent(sessionID)) return;
    await transport.request('cancel_agent_request', {
      'agentId': _real(paseoString(sessionID, max: 256)),
    }, mutation: true);
  }

  // ---- permissions -------------------------------------------------------

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => _permissions
      .values
      .where((pending) => !pending.genUiApproving)
      .map((p) => p.permission)
      .toList();
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
  Future<String> resumeHostAgentChat(String sessionId) => _withPayloadUse(
    () async {
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
      final browserRequested = _browserRequestedFor(sessionId);
      await _revokeBrowserSession(sessionId);
      final browserRevision = _browserRevision(sessionId);
      _checkLocation(scope, epoch);
      final result = await transport.request(
        'resume_agent_request',
        {'handle': handle},
        mutation: true,
        timeout: const Duration(seconds: 90),
      );
      _checkLocation(scope, epoch);
      if (browserRequested && _browserRevision(sessionId) != browserRevision) {
        throw _browserUnavailable;
      }
      final resumed = {...paseoObject(result['agent'])};
      // A resumed record may come back without the title it had.
      final title = resumed['title'];
      if ((title is! String || title.trim().isEmpty) &&
          agent['title'] is String) {
        resumed['title'] = agent['title'];
      }
      final realID = paseoString(resumed['id'], max: 256);
      // The resumed agent may be a new record: the row the person tapped
      // opens it.
      if (realID != _real(sessionId)) {
        _moveBrowserRequest(_real(sessionId), realID);
        _realIDs[sessionId] = realID;
        _appIDs[realID] = sessionId;
      }
      if (_remember(resumed) == null) {
        throw PaseoFailure(PaseoFailureKind.scopeMismatch);
      }
      if (browserRequested) {
        if (resumed['provider'] != 'claude') throw _browserUnavailable;
        await _beforeBrowserLaunch(sessionId, requiredBrowser: true);
        _checkLocation(scope, epoch);
      }
      _liveAgentSessions.add(sessionId);
      return realID;
    },
  );

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
    final browserRequested = _browserRequestedFor(sessionId);
    await _revokeBrowserSession(sessionId);
    _checkLocation(scope, epoch);
    final draft = await createSession();
    _checkLocation(scope, epoch);
    seedDraftProviderForSession(draft.id, continuation.providerId);
    if (browserRequested) {
      _moveBrowserRequest(_real(sessionId), draft.id);
      _browserRequested.add(draft.id);
    }
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
    // The runtime's own default mode first: the composer names a mode only
    // when the conversation uses another one.
    final preferred =
        _defaultModeFor(paseoDefaultProvider) ??
        defaultProviderModes[paseoDefaultProvider];
    if (preferred != null && names.remove(preferred)) {
      names.insert(0, preferred);
    }
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
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async =>
      _questions.values.map(_questionJson).toList();
  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) => _answerQuestion(sessionID, requestID, answers);
  @override
  Future<void> rejectQuestionV2(String sessionID, String requestID) =>
      _rejectQuestion(sessionID, requestID);
  @override
  Future<List<Todo>> todos(String id) async => const [];
  @override
  Future<List<FileDiff>> diff(String id) async => const [];
  @override
  Future<List<IntegrationInfo>> listIntegrations() async => const [];
  @override
  Future<List<Session>> listSessionChildren(String id) async => const [];

  /// The runtime's own slash commands and skills (Claude Code's `/compact`,
  /// a project's commands), read as a draft for this folder: listing them
  /// through a stored agent would load that agent's runtime first.
  @override
  Future<List<CommandInfo>> listCommands() => _listCommands();

  /// Discovery for a mapped conversation; the folder-wide draft path is off
  /// for browser requests because it has no daemon identity to reserve.
  Future<List<CommandInfo>> listBrowserCommandsForSession(
    String sessionID,
  ) async => _listBrowserCommands(sessionID);

  /// A runtime command runs as the message `/name arguments`: the runtime
  /// reads its own commands from the prompt.
  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) => promptAsync(
    sessionID,
    text: args.trim().isEmpty ? '/$command' : '/$command ${args.trim()}',
    model: model,
    variant: variant,
  );
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
    final browserCleanup = _revokeBrowserSource(clearRequests: false);
    _browserRequested.clear();
    _browserRevisions.clear();
    _closed = true;
    _localWork.invalidate();
    unawaited(_localWork.changes.close());
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
    _draftModels.clear();
    _liveAgentSessions.clear();
    _uncertain.clear();
    _awaitingTurn.clear();
    _turnActive.clear();
    _permissions.clear();
    _questions.clear();
    _nativeQuestionChanges.add(null);
    _nativePermissionChanges.add(null);
    _live.clear();
    _realIDs.clear();
    _appIDs.clear();
    _heldEvents.clear();
    unawaited(_daemonEvents.cancel());
    unawaited(_daemonDisconnects.cancel());
    unawaited(browserCleanup.then((_) => transport.close()));
    unawaited(_events.close());
    unawaited(_streamStates.close());
    unawaited(_nativeQuestionChanges.close());
    unawaited(_nativePermissionChanges.close());
  }
}

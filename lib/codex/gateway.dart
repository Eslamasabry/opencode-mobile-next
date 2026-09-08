/// Optional experimental Codex gateway, pinned to app-server 0.153.4.
library;

import 'dart:async';

import '../api/models.dart';
import '../domain/server_gateway.dart';
import '../domain/agent_account.dart';
import 'account.dart';
import 'mappers.dart';
import 'transport.dart';

const codexServerCapabilities = ServerCapabilities(
  agentAccount: true,
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
  persistentPermissionGrants: false,
  messageCompletionEndsRun: false,
  sessionRevert: false,
  sessionImportExport: false,
  sessionNotes: false,
  serverCatalog: false,
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
  legacyQuestionRequests: false,
  forms: false,
  inbox: false,
);

class _CodexApproval {
  final CodexRpcEvent request;
  final PermissionRequest permission;
  final String turnID;
  _CodexApproval(this.request, this.permission, this.turnID);
}

class _CodexOperationContext {
  final String scope;
  final int locationEpoch;
  final int transportEpoch;

  const _CodexOperationContext({
    required this.scope,
    required this.locationEpoch,
    required this.transportEpoch,
  });
}

class CodexGateway
    implements ServerGateway, ServerOperationsGateway, AgentAccountGateway {
  final CodexTransport transport;
  String? _directory;
  bool _closed = false;
  final _sessions = <String, Session>{};
  final _statuses = <String, String>{};
  final _turns = <String, String>{};
  final _resumed = <String>{};
  final _approvals = <String, _CodexApproval>{};
  final _uncertain = <String>{};
  final _newEmptyThreads = <String>{};
  final _fileChanges = <(String, String), List<dynamic>>{};
  final _events = StreamController<EventEnvelope>.broadcast(sync: true);
  final _streamStates = StreamController<StreamStatus>.broadcast(sync: true);
  late final StreamSubscription<CodexRpcEvent> _rpcEvents;
  late final StreamSubscription<int> _rpcDisconnects;
  Timer? _retry;
  int _retryAttempt = 0;
  int _locationEpoch = 0;
  bool _listening = false;
  bool _recoveryDegraded = false;
  Future<void>? _recovering;

  CodexGateway({required this.transport, String? directory})
    : _directory = directory {
    _rpcEvents = transport.events.listen(_onRpc);
    _rpcDisconnects = transport.disconnects.listen((_) {
      _approvals.clear();
      _turns.clear();
      _recoveryDegraded = true;
      _emitState(StreamStatus.reconnecting);
      _scheduleReconnect();
    });
  }

  CodexGateway.connect({
    required String baseUrl,
    required String token,
    String? directory,
  }) : this(
         transport: CodexTransport(endpoint: baseUrl, token: token),
         directory: directory,
       );

  @override
  ServerCapabilities get capabilities => codexServerCapabilities;
  @override
  AgentAccountSession openAccountSession() {
    final location = _locationEpoch;
    return CodexAccountSession(
      transport,
      () => !_closed && location == _locationEpoch,
    );
  }

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
      throw CodexFailure(CodexFailureKind.scopeMismatch);
    }
    return value;
  }

  @override
  void setLocation({String? directory, String? workspace}) {
    if (workspace != null && workspace.isNotEmpty) {
      throw CodexFailure(CodexFailureKind.unavailable);
    }
    if (_directory == directory) return;
    _directory = directory;
    _locationEpoch++;
    _sessions.clear();
    _statuses.clear();
    _turns.clear();
    _approvals.clear();
    _resumed.clear();
    _uncertain.clear();
    _newEmptyThreads.clear();
    _fileChanges.clear();
    _recoveryDegraded = false;
  }

  void _checkLocation(String scope, int epoch) {
    if (_closed || scope != _directory || epoch != _locationEpoch) {
      throw CodexFailure(CodexFailureKind.scopeMismatch);
    }
  }

  Future<_CodexOperationContext> _captureOperation() async {
    final scope = _scope;
    final locationEpoch = _locationEpoch;
    await transport.connect();
    _checkLocation(scope, locationEpoch);
    return _CodexOperationContext(
      scope: scope,
      locationEpoch: locationEpoch,
      transportEpoch: transport.epoch,
    );
  }

  void _checkOperation(
    _CodexOperationContext operation, {
    bool mutation = false,
  }) {
    _checkLocation(operation.scope, operation.locationEpoch);
    if (!transport.connected || transport.epoch != operation.transportEpoch) {
      throw CodexFailure(
        mutation
            ? CodexFailureKind.deliveryUnknown
            : CodexFailureKind.disconnected,
      );
    }
  }

  Session _remember(Map<String, dynamic> thread, String scope, int epoch) {
    _checkLocation(scope, epoch);
    final session = codexSession(thread, directory: scope);
    _sessions.remove(session.id);
    _sessions[session.id] = session;
    _statuses[session.id] = codexSessionStatus(thread);
    while (_sessions.length > 512) {
      final id = _sessions.keys.firstWhere((id) => !_resumed.contains(id));
      _sessions.remove(id);
      _statuses.remove(id);
      _turns.remove(id);
    }
    return session;
  }

  Future<Map<String, dynamic>> _readThread(
    String id, {
    bool turns = false,
    _CodexOperationContext? operation,
  }) async {
    codexString(id, max: 256);
    final context = operation ?? await _captureOperation();
    _checkOperation(context);
    final result = await transport.request('thread/read', {
      'threadId': id,
      'includeTurns': turns,
    });
    _checkOperation(context);
    final thread = codexObject(result['thread']);
    if (thread['id'] != id) {
      throw CodexFailure(CodexFailureKind.invalidResponse);
    }
    _remember(thread, context.scope, context.locationEpoch);
    if (turns) {
      _turns.remove(id);
      final history = codexList(thread['turns']);
      if (history.isNotEmpty) _newEmptyThreads.remove(id);
      for (final raw in history) {
        final turn = codexObject(raw);
        if (turn['status'] == 'inProgress') {
          _turns[id] = codexString(turn['id'], max: 256);
          for (final rawItem in codexList(turn['items'])) {
            final item = codexObject(rawItem);
            if (item['type'] == 'fileChange') {
              _fileChanges[(id, codexString(item['id'], max: 256))] = codexList(
                item['changes'],
                max: 1000,
              );
            }
          }
        }
      }
    }
    return thread;
  }

  Future<Map<String, dynamic>> _resume(String id) async {
    final context = await _captureOperation();
    await _readThread(id, turns: true, operation: context);
    _checkOperation(context);
    final result = await transport.request('thread/resume', {'threadId': id});
    _checkOperation(context);
    final thread = codexObject(result['thread']);
    if (thread['id'] != id) {
      throw CodexFailure(CodexFailureKind.invalidResponse);
    }
    _remember(thread, context.scope, context.locationEpoch);
    _resumed.remove(id);
    _resumed.add(id);
    while (_resumed.length > 8) {
      final old = _resumed.first;
      _resumed.remove(old);
      unawaited(
        transport
            .request('thread/unsubscribe', {'threadId': old})
            .catchError((Object _) => <String, dynamic>{}),
      );
    }
    for (final raw in codexList(thread['turns'])) {
      final turn = codexObject(raw);
      if (turn['status'] == 'inProgress') {
        _turns[id] = codexString(turn['id'], max: 256);
      }
    }
    return thread;
  }

  @override
  Future<Health> health() async {
    await transport.connect();
    return Health(healthy: true, version: 'Codex app-server (experimental)');
  }

  @override
  Future<List<Session>> sessions() async => (await sessionPage()).items;
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request('thread/list', {
      'cwd': scope,
      'limit': limit.clamp(1, 100),
      if (cursor != null) 'cursor': codexString(cursor, max: 4096),
      'archived': false,
    });
    final rows = codexList(result['data'], max: 100);
    final items = rows
        .map((row) => _remember(codexObject(row), scope, epoch))
        .toList();
    final next = result['nextCursor'];
    return ServerPage(
      items: items,
      nextCursor: next == null ? null : codexString(next),
    );
  }

  @override
  Future<Session> createSession() async {
    final scope = _scope;
    final epoch = _locationEpoch;
    final result = await transport.request('thread/start', {
      'cwd': scope,
      'approvalPolicy': 'on-request',
      'sandbox': 'read-only',
    }, mutation: true);
    final session = _remember(codexObject(result['thread']), scope, epoch);
    _resumed.add(session.id);
    _newEmptyThreads.add(session.id);
    return session;
  }

  @override
  Future<Session> session(String id) async {
    // A newly created empty thread may not have a persisted rollout yet.
    if (_resumed.contains(id) &&
        _sessions[id] != null &&
        _newEmptyThreads.contains(id)) {
      return _sessions[id]!;
    }
    return codexSession(await _readThread(id), directory: _scope);
  }

  @override
  Future<Session> getSessionDetails(String id) => session(id);
  @override
  Future<void> deleteSession(String id) async {
    final context = await _captureOperation();
    await _readThread(id, operation: context);
    _checkOperation(context, mutation: true);
    await transport.request('thread/delete', {'threadId': id}, mutation: true);
    _checkOperation(context, mutation: true);
    _sessions.remove(id);
    _statuses.remove(id);
    _resumed.remove(id);
    _turns.remove(id);
    _newEmptyThreads.remove(id);
    _approvals.removeWhere((_, value) => value.permission.sessionID == id);
  }

  @override
  Future<void> renameSession(String id, String title) async {
    final context = await _captureOperation();
    await _readThread(id, operation: context);
    _checkOperation(context, mutation: true);
    await transport.request('thread/name/set', {
      'threadId': id,
      'name': codexString(title, max: 4096),
    }, mutation: true);
    _checkOperation(context, mutation: true);
  }

  @override
  Future<Map<String, String>> sessionStatuses() async => Map.of(_statuses);
  @override
  Future<List<MessageWithParts>> messages(String id) async {
    if (_resumed.contains(id) &&
        _newEmptyThreads.contains(id) &&
        _sessions.containsKey(id) &&
        !_uncertain.contains(id)) {
      // thread/start itself does not persist an empty conversation. Once a turn
      // has started, authoritative resume is always used.
      return const [];
    }
    final thread = await _resume(id);
    final result = codexMessages(thread);
    _uncertain.remove(id);
    _newEmptyThreads.remove(id);
    return result;
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    if (cursor != null) throw CodexFailure(CodexFailureKind.unavailable);
    return ServerPage(items: await messages(id));
  }

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
    if (attachments.isNotEmpty ||
        agentMentions.isNotEmpty ||
        delivery != null ||
        (agent != null && agent != 'codex') ||
        _uncertain.contains(sessionID)) {
      throw CodexFailure(
        _uncertain.contains(sessionID)
            ? CodexFailureKind.deliveryUnknown
            : CodexFailureKind.unavailable,
      );
    }
    if (!_resumed.contains(sessionID)) await _resume(sessionID);
    if (!_sessions.containsKey(sessionID)) {
      throw CodexFailure(CodexFailureKind.scopeMismatch);
    }
    if (_statuses[sessionID] == 'busy') {
      throw CodexFailure(CodexFailureKind.unavailable);
    }
    final scope = _scope;
    final epoch = _locationEpoch;
    final prompt = codexString(text, max: 1024 * 1024);
    // Once dispatch begins, even an unacknowledged first turn must recover
    // authoritatively. It is no longer safe to treat this as an empty thread.
    _newEmptyThreads.remove(sessionID);
    try {
      final result = await transport.request('turn/start', {
        'threadId': sessionID,
        'input': [
          {'type': 'text', 'text': prompt, 'text_elements': <Object>[]},
        ],
        if (model != null) 'model': model.modelID,
        if (variant != null && variant.isNotEmpty) 'effort': variant,
      }, mutation: true);
      _checkLocation(scope, epoch);
      final turn = codexObject(result['turn']);
      _turns[sessionID] = codexString(turn['id'], max: 256);
      _newEmptyThreads.remove(sessionID);
      _statuses[sessionID] = 'busy';
    } on CodexFailure catch (error) {
      if (error.kind == CodexFailureKind.deliveryUnknown) {
        _uncertain.add(sessionID);
      }
      rethrow;
    }
  }

  @override
  Future<void> abort(String sessionID) async {
    final context = await _captureOperation();
    final thread = await _readThread(
      sessionID,
      turns: true,
      operation: context,
    );
    final active = codexList(
      thread['turns'],
    ).map(codexObject).where((turn) => turn['status'] == 'inProgress').toList();
    if (active.length != 1) throw CodexFailure(CodexFailureKind.staleRequest);
    _checkOperation(context, mutation: true);
    await transport.request('turn/interrupt', {
      'threadId': sessionID,
      'turnId': codexString(active.single['id'], max: 256),
    }, mutation: true);
    _checkOperation(context, mutation: true);
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async => _approvals
      .values
      .where((a) => a.request.epoch == transport.epoch)
      .map((a) => a.permission)
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
    final approval = _approvals[requestID];
    if (approval == null ||
        approval.request.epoch != transport.epoch ||
        (legacySessionID != null &&
            legacySessionID != approval.permission.sessionID)) {
      throw CodexFailure(CodexFailureKind.staleRequest);
    }
    final decision = switch (reply) {
      'once' => 'accept',
      'reject' => 'decline',
      'cancel' => 'cancel',
      _ => throw CodexFailure(CodexFailureKind.unavailable),
    };
    transport.reply(approval.request, {'decision': decision});
    _approvals.remove(requestID);
    _emit('permission.replied', {
      'sessionID': approval.permission.sessionID,
      'requestID': requestID,
    });
  }

  @override
  Future<ProvidersResponse> providers() async {
    final models = <String>[];
    String? defaultModel;
    String? cursor;
    final seen = <String>{};
    for (var page = 0; page < 10; page++) {
      final result = await transport.request('model/list', {
        'limit': 100,
        'cursor': ?cursor,
      });
      for (final raw in codexList(result['data'], max: 100)) {
        final model = codexObject(raw);
        if (model['hidden'] == true) continue;
        final id = codexString(model['model'], max: 256);
        if (!models.contains(id)) models.add(id);
        if (model['isDefault'] == true) defaultModel = id;
      }
      final next = result['nextCursor'];
      if (next == null) break;
      cursor = codexString(next);
      if (!seen.add(cursor) || page == 9) {
        throw CodexFailure(CodexFailureKind.invalidResponse);
      }
    }
    return ProvidersResponse(
      providers: [
        ProviderInfo(
          id: 'codex',
          name: 'Codex server models',
          modelIDs: models,
        ),
      ],
      defaultProviderID: 'codex',
      defaultModelID: defaultModel,
    );
  }

  @override
  Future<ProvidersResponse> configuredProviders() => providers();
  @override
  Future<List<AgentInfo>> agents() async => [
    AgentInfo(name: 'codex', mode: 'primary'),
  ];
  @override
  Future<ChatDefaults> loadChatDefaults() async =>
      const ChatDefaults(agent: 'codex');
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
      throw CodexFailure(CodexFailureKind.unavailable);

  void _emit(String type, Map<String, dynamic> properties) {
    if (!_closed) {
      _events.add(
        EventEnvelope(
          type: type,
          properties: properties,
          directory: _directory,
        ),
      );
    }
  }

  void _emitState(StreamStatus state) {
    if (!_closed && _listening) _streamStates.add(state);
  }

  void _onRpc(CodexRpcEvent event) {
    if (_closed || event.epoch != transport.epoch) return;
    try {
      final p = event.params;
      if (event.method == 'thread/started') {
        final thread = codexObject(p['thread']);
        if (thread['cwd'] != _directory) return;
        final session = _remember(thread, _scope, _locationEpoch);
        _emit('session.created', {
          'info': {
            'id': session.id,
            'title': session.title,
            'directory': session.directory,
            'time': {
              'created': session.time?.created,
              'updated': session.time?.updated,
            },
          },
        });
        return;
      }
      final id = p['threadId'];
      if (id is! String || !_sessions.containsKey(id)) {
        if (event.requestID != null && id == null) {
          transport.rejectUnsupported(event);
        }
        return;
      }
      if (event.method == 'thread/status/changed') {
        _statuses[id] = codexSessionStatus({'status': p['status']});
        _emit('session.status', {
          'sessionID': id,
          'status': {'type': _statuses[id]},
        });
      } else if (event.method == 'turn/started') {
        final turn = codexObject(p['turn']);
        _turns[id] = codexString(turn['id'], max: 256);
        _newEmptyThreads.remove(id);
        _statuses[id] = 'busy';
        _emit('session.status', {
          'sessionID': id,
          'status': {'type': 'busy'},
        });
      } else if (event.method == 'turn/completed') {
        final turn = codexObject(p['turn']);
        final turnID = codexString(turn['id'], max: 256);
        if (_turns[id] != turnID) return;
        _statuses[id] = 'idle';
        _approvals.removeWhere(
          (_, approval) =>
              approval.permission.sessionID == id && approval.turnID == turnID,
        );
        _emit('session.status', {
          'sessionID': id,
          'status': {'type': 'idle'},
        });
        if (turn['status'] == 'failed') {
          _emit('session.error', {
            'sessionID': id,
            'error': {
              'name': 'CodexTurnFailed',
              'data': {'message': 'The Codex turn failed.'},
            },
          });
        }
        // A completion prompts authoritative hydration; idle itself is not
        // reported as a successful run or a successful test result.
        _emit('session.idle', {'sessionID': id});
      } else if (event.method == 'item/started' ||
          event.method == 'item/completed') {
        if (p['turnId'] != _turns[id]) return;
        final item = codexObject(p['item']);
        if (item['type'] == 'fileChange') {
          final key = (id, codexString(item['id'], max: 256));
          _fileChanges[key] = codexList(item['changes'], max: 1000);
          while (_fileChanges.length > 64) {
            _fileChanges.remove(_fileChanges.keys.first);
          }
        }
        final message = codexItemMessage(
          id,
          item,
          completed: event.method == 'item/completed'
              ? DateTime.now().millisecondsSinceEpoch
              : null,
        );
        _emit('message.updated', {'info': codexMessageJson(message.info)});
        for (final part in message.parts) {
          _emit('message.part.updated', {
            'sessionID': id,
            'part': {...codexPartJson(part), 'sessionID': id},
          });
        }
      } else if (event.method == 'item/agentMessage/delta' ||
          event.method == 'item/reasoning/summaryTextDelta') {
        if (p['turnId'] != _turns[id]) return;
        final itemID = codexString(p['itemId'], max: 256);
        _emit('message.part.delta', {
          'sessionID': id,
          'messageID': itemID,
          'partID': '$itemID:0',
          'field': 'text',
          'delta': codexString(p['delta'], max: 1024 * 1024, empty: true),
        });
      } else if (event.method == 'item/commandExecution/requestApproval' ||
          event.method == 'item/fileChange/requestApproval') {
        _addApproval(event, id);
      } else if (event.method == 'serverRequest/resolved') {
        final resolved = p['requestId'];
        final removed = _approvals.entries
            .where(
              (entry) =>
                  entry.value.request.requestID == resolved &&
                  entry.value.permission.sessionID == id,
            )
            .map((entry) => entry.key)
            .toList();
        for (final requestID in removed) {
          _approvals.remove(requestID);
          _emit('permission.replied', {
            'sessionID': id,
            'requestID': requestID,
          });
        }
      } else if (event.requestID != null) {
        transport.rejectUnsupported(event);
      }
    } catch (_) {
      // Invalid requests cannot become approvals. No payload reaches logs.
      if (event.requestID != null) transport.rejectUnsupported(event);
    }
  }

  void _addApproval(CodexRpcEvent event, String sessionID) {
    final p = event.params;
    final turnID = codexString(p['turnId'], max: 256);
    if (event.requestID == null ||
        _approvals.length >= 64 ||
        _statuses[sessionID] != 'busy' ||
        _turns[sessionID] != turnID) {
      transport.rejectUnsupported(event);
      return;
    }
    final itemID = codexString(p['itemId'], max: 256);
    final rawID = event.requestID;
    if (rawID is String) codexString(rawID, max: 256);
    final requestID = 'codex:${event.epoch}:${rawID is int ? 'n' : 's'}:$rawID';
    final command = p['command'];
    final network = p['networkApprovalContext'];
    final file = event.method == 'item/fileChange/requestApproval';
    String permission;
    List<String> patterns;
    Map<String, dynamic> metadata;
    if (file) {
      final changes = _fileChanges[(sessionID, itemID)];
      if (changes == null || changes.isEmpty) {
        transport.rejectUnsupported(event);
        return;
      }
      permission = 'patch';
      patterns = changes
          .map((change) => codexString(codexObject(change)['path']))
          .toList();
      metadata = {
        if (patterns.length == 1) 'filePath': patterns.single,
        'diff': changes
            .map((raw) {
              final change = codexObject(raw);
              return '${codexString(change['path'])}\n${codexString(change['diff'], max: 1024 * 1024, empty: true)}';
            })
            .join('\n'),
      };
    } else if (network is Map<String, dynamic>) {
      permission = 'network';
      final host = codexString(network['host'], max: 1024);
      final protocol = codexString(network['protocol'], max: 64);
      patterns = [host];
      metadata = {'host': host, 'protocol': protocol};
    } else {
      permission = 'shell';
      final preview = codexString(command, max: 65536);
      patterns = [preview];
      metadata = {'command': preview};
    }
    final reason = p['reason'];
    final request = PermissionRequest(
      id: requestID,
      sessionID: sessionID,
      permission: permission,
      patterns: patterns,
      metadata: metadata,
      message: reason is String
          ? codexString(reason, max: 4096, empty: true)
          : null,
      tool: PermissionTool(messageID: itemID, callID: itemID),
    );
    _approvals[requestID] = _CodexApproval(event, request, turnID);
    _emit('permission.asked', {
      'id': requestID,
      'sessionID': sessionID,
      'permission': permission,
      'patterns': patterns,
      'metadata': metadata,
      'always': const <String>[],
      'tool': {'messageID': itemID, 'callID': itemID},
      if (request.message != null) 'message': request.message,
    });
  }

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
      var failed = false;
      for (final id in _resumed.toList()) {
        if (_closed || !_listening) return;
        if (_newEmptyThreads.contains(id) && !_uncertain.contains(id)) {
          // A never-dispatched thread may have no persisted rollout. Retire
          // it before any automatic thread RPC, without interpreting errors
          // as proof of absence. Keep metadata and local drafts; explicit
          // session/history/prompt access must read the server again.
          _resumed.remove(id);
          continue;
        }
        try {
          await _resume(id);
        } on CodexFailure {
          // Keep the subscription tracked so the next reconnect can retry it.
          // A replacement socket is not a complete recovery if any thread
          // still needs authoritative resumption.
          failed = true;
        }
      }
      if (failed || !transport.connected) {
        _recoveryDegraded = true;
        _emitState(StreamStatus.reconnecting);
        return;
      }
      _recoveryDegraded = false;
      _retryAttempt = 0;
      _emitState(StreamStatus.connected);
    } on CodexFailure catch (error) {
      _recoveryDegraded = true;
      _emitState(
        error.kind == CodexFailureKind.authentication
            ? StreamStatus.disconnected
            : StreamStatus.reconnecting,
      );
      if (error.kind == CodexFailureKind.authentication) {
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
    return _CodexEventChannel(
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
  }) => _CodexEventChannel(() async {});

  @override
  void close() {
    if (_closed) return;
    _closed = true;
    _listening = false;
    _locationEpoch++;
    _retry?.cancel();
    _retry = null;
    _sessions.clear();
    _approvals.clear();
    _resumed.clear();
    _turns.clear();
    _statuses.clear();
    _uncertain.clear();
    _newEmptyThreads.clear();
    _fileChanges.clear();
    unawaited(_rpcEvents.cancel());
    unawaited(_rpcDisconnects.cancel());
    unawaited(transport.close());
    unawaited(_events.close());
    unawaited(_streamStates.close());
  }
}

class _CodexEventChannel implements LiveEventChannel {
  final Future<void> Function() disposeCallback;
  final void Function()? onStart;
  bool _disposed = false;
  bool _started = false;
  _CodexEventChannel(this.disposeCallback, {this.onStart});
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

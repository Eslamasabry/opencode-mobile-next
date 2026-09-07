import 'dart:async';

import '../api/models.dart';
import '../api2/models.dart' show Api2FormInfo, Api2InboxItem;
import '../domain/server_gateway.dart';
import 'demo_copy.dart';

/// One route-owned simulation. Uses the same gateway/event contract as a live
/// server, with no network client, filesystem or native dependency.
class DemoGateway implements ServerGateway, ServerOperationsGateway {
  static const sessionID = 'demo-session';
  static final model = ModelRef(providerID: 'demo', modelID: 'simulated');
  static const catalog = CatalogSnapshot(
    providers: [
      CatalogProvider(id: 'demo', name: 'Offline demo', enabled: true),
    ],
    models: [
      CatalogModel(
        id: 'simulated',
        providerID: 'demo',
        name: 'Simulated reply',
        enabled: true,
        status: 'active',
        contextLimit: 0,
        outputLimit: 0,
        reasoning: false,
        attachments: false,
        tools: true,
        variants: [],
      ),
    ],
    agents: [],
  );
  static const patch =
      '--- welcome.txt\n+++ welcome.txt\n@@ -1 +1 @@\n-Hello\n+Welcome aboard!';
  static final sampleSession = Session(
    id: sessionID,
    title: 'Welcome message',
    projectID: 'demo-project',
  );
  final _events = StreamController<EventEnvelope>.broadcast(sync: true);
  final _messages = <MessageWithParts>[];
  Timer? _timer;
  PermissionRequest? _permission;
  bool _busy = false;
  bool _closed = false;
  int _turn = 0;
  int _sequence = 0;
  bool reducedMotion = false;

  @override
  bool get isClosed => _closed;
  bool get hasPendingTimer => _timer?.isActive ?? false;
  bool get hasFinished => _turn > 0 && !_busy;
  @override
  String? get directory => null;
  @override
  String? get workspace => null;
  @override
  void setLocation({String? directory, String? workspace}) {
    if (directory != null || workspace != null) _unavailable();
  }

  @override
  ServerCapabilities get capabilities => const ServerCapabilities(
    managedWorkspaces: false,
    workspaceWarp: false,
    sessionSteal: false,
    consoleOrganizations: false,
    mcpOAuth: false,
    mcpConfigWrites: false,
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
  );

  Never _unavailable() =>
      throw StateError('This action is unavailable in the offline demo.');
  void _check(String id) {
    if (_closed || id != sessionID) _unavailable();
  }

  void _emit(String type, Map<String, dynamic> properties) {
    if (!_closed) {
      _events.add(EventEnvelope(type: type, properties: properties));
    }
  }

  void _status(bool busy) {
    _busy = busy;
    _emit('session.status', {
      'sessionID': sessionID,
      'status': {'type': busy ? 'busy' : 'idle'},
    });
  }

  MessageWithParts _message(String role, String text, {bool complete = true}) {
    final id = 'demo-message-${++_sequence}';
    final created = DateTime.now().millisecondsSinceEpoch;
    final info = <String, dynamic>{
      'id': id,
      'sessionID': sessionID,
      'role': role,
      if (role == 'assistant') ...{
        'providerID': 'demo',
        'modelID': 'simulated',
      },
      'time': {'created': created, if (complete) 'completed': created},
    };
    final part = <String, dynamic>{
      'id': '$id-text',
      'sessionID': sessionID,
      'messageID': id,
      'type': 'text',
      'text': text,
    };
    final message = MessageWithParts(
      info: MessageInfo.fromJson(info),
      parts: [Part.fromJson(part)],
    );
    _messages.add(message);
    _emit('message.updated', {'info': info});
    _emit('message.part.updated', {'sessionID': sessionID, 'part': part});
    return message;
  }

  void _finishReply(MessageWithParts reply, String text) {
    final index = _messages.indexWhere((item) => item.info.id == reply.info.id);
    if (index < 0 || _closed) return;
    final info = <String, dynamic>{
      'id': reply.info.id,
      'sessionID': sessionID,
      'role': 'assistant',
      'providerID': 'demo',
      'modelID': 'simulated',
      'time': {
        'created': reply.info.time?.created,
        'completed': DateTime.now().millisecondsSinceEpoch,
      },
    };
    final part = <String, dynamic>{
      'id': '${reply.info.id}-text',
      'sessionID': sessionID,
      'messageID': reply.info.id,
      'type': 'text',
      'text': text,
    };
    _messages[index] = MessageWithParts(
      info: MessageInfo.fromJson(info),
      parts: [Part.fromJson(part)],
    );
    _emit('message.part.updated', {'sessionID': sessionID, 'part': part});
    _emit('message.updated', {'info': info});
  }

  @override
  Future<void> promptAsync(
    String id, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
  }) async {
    _check(id);
    if (_busy ||
        text.trim().isEmpty ||
        attachments.isNotEmpty ||
        agentMentions.isNotEmpty) {
      _unavailable();
    }
    _turn++;
    _message('user', text);
    _status(true);
    final reply = _message('assistant', '', complete: false);
    final words = DemoCopy.reply.split(' ');
    var count = 0;
    void advance() {
      if (_closed) return;
      count = reducedMotion ? words.length : count + 1;
      final delta = count == 1 ? words.first : ' ${words[count - 1]}';
      if (!reducedMotion) {
        final index = _messages.indexWhere(
          (item) => item.info.id == reply.info.id,
        );
        _messages[index] = MessageWithParts(
          info: reply.info,
          parts: [
            Part.fromJson({
              'id': '${reply.info.id}-text',
              'messageID': reply.info.id,
              'sessionID': sessionID,
              'type': 'text',
              'text': words.take(count).join(' '),
            }),
          ],
        );
        _emit('message.part.delta', {
          'sessionID': sessionID,
          'messageID': reply.info.id,
          'partID': '${reply.info.id}-text',
          'field': 'text',
          'delta': delta,
        });
      }
      if (count < words.length) return;
      _timer?.cancel();
      _timer = null;
      _finishReply(reply, DemoCopy.reply);
      final data = <String, dynamic>{
        'id': 'demo-permission-$_turn',
        'sessionID': sessionID,
        'permission': 'edit',
        'patterns': ['welcome.txt'],
        'always': <String>[],
        'metadata': {'filepath': 'welcome.txt', 'diff': patch},
      };
      _permission = PermissionRequest.fromJson(data);
      _emit('permission.asked', data);
    }

    if (reducedMotion) {
      advance();
    } else {
      _timer = Timer.periodic(
        const Duration(milliseconds: 100),
        (_) => advance(),
      );
    }
  }

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    _check(sessionID);
    if (_permission?.id != requestID ||
        !{'once', 'always', 'reject'}.contains(reply)) {
      _unavailable();
    }
    _permission = null;
    _emit('permission.replied', {
      'requestID': requestID,
      'sessionID': sessionID,
      'reply': reply,
    });
    _message(
      'assistant',
      reply == 'reject' ? DemoCopy.denied : DemoCopy.allowed,
    );
    _status(false);
  }

  @override
  Future<void> abort(String id) async {
    _check(id);
    _timer?.cancel();
    _timer = null;
    final permission = _permission;
    if (permission != null) {
      _permission = null;
      _emit('permission.replied', {
        'requestID': permission.id,
        'sessionID': sessionID,
        'reply': 'reject',
      });
    }
    _message(
      'assistant',
      'The simulated reply was stopped. No files were changed.',
    );
    _status(false);
  }

  @override
  Future<Health> health() async => Health(healthy: !_closed);
  @override
  Future<Session> session(String id) async {
    _check(id);
    return sampleSession;
  }

  @override
  Future<Session> getSessionDetails(String id) => session(id);
  @override
  Future<List<Session>> sessions() async => [sampleSession];
  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? [sampleSession] : []);
  @override
  Future<Map<String, String>> sessionStatuses() async => {
    sessionID: _busy ? 'busy' : 'idle',
  };
  @override
  Future<List<MessageWithParts>> messages(String id) async {
    _check(id);
    return List.of(_messages);
  }

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async => ServerPage(items: cursor == null ? await messages(id) : []);
  @override
  Future<List<FileDiff>> diff(String id) async {
    _check(id);
    return [
      FileDiff(
        file: 'welcome.txt',
        before: 'Hello\n',
        after: 'Welcome aboard!\n',
        patch: patch,
        additions: 1,
        deletions: 1,
      ),
    ];
  }

  @override
  Future<List<Todo>> todos(String id) async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [?_permission];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => [];
  @override
  Future<List<PendingQuestion>> listQuestions() async => [];
  @override
  Future<List<Api2FormInfo>> pendingForms() async => [];
  @override
  Future<List<Api2FormInfo>> sessionForms(String sessionID) async => [];
  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => [];
  @override
  Future<ProvidersResponse> providers() async =>
      ProvidersResponse(providers: []);
  @override
  Future<ProvidersResponse> configuredProviders() => providers();
  @override
  Future<List<AgentInfo>> agents() async => [];
  @override
  Future<CatalogSnapshot> loadCatalog() async => catalog;
  @override
  Future<ChatDefaults> loadChatDefaults() async => ChatDefaults(model: model);
  @override
  Future<List<CommandInfo>> listCommands() async => [];
  @override
  Future<List<ReferenceInfo>> listReferences() async => [];
  @override
  Future<BackgroundWorkSupport> loadBackgroundWorkSupport() async =>
      BackgroundWorkSupport.unavailable;
  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) => _DemoEvents(_events.stream, onEvent, onStatus);
  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope) onEvent,
    required void Function(StreamStatus) onStatus,
    void Function(Object)? onError,
  }) => _DemoEvents(const Stream.empty(), onEvent, onStatus);
  @override
  void close() {
    if (_closed) return;
    _closed = true;
    _timer?.cancel();
    _timer = null;
    _permission = null;
    _messages.clear();
    unawaited(_events.close());
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => _unavailable();
}

class _DemoEvents implements LiveEventChannel {
  _DemoEvents(this.events, this.onEvent, this.onStatus);
  final Stream<EventEnvelope> events;
  final void Function(EventEnvelope) onEvent;
  final void Function(StreamStatus) onStatus;
  StreamSubscription<EventEnvelope>? _subscription;
  @override
  void start() {
    _subscription ??= events.listen(onEvent);
    onStatus(StreamStatus.connected);
  }

  @override
  Future<void> dispose() async => _subscription?.cancel();
}

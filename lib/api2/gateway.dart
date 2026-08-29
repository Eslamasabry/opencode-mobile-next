/// OpenCode 2 implementation of the protocol-neutral [ServerGateway]: the
/// live transport surface (sessions, messages, prompting, permissions,
/// providers, files, events) built on the typed v2 client in this package.
///
/// Operations beyond the live transport live in
/// `gateway_operations.dart` ([Api2OperationsGateway]).
library;

import 'dart:convert';

import '../api/models.dart';
import '../domain/server_gateway.dart';
import 'client.dart';
import 'gateway_events.dart';
import 'gateway_mappers.dart';
import 'models.dart';
import 'transport.dart';

/// The OpenCode 2 side of the domain gateway.
///
/// Failures surface as the same [ApiException]/[ProductException] types the
/// v1 gateway throws, so existing error handling keeps working. Where the v2
/// server has no equivalent endpoint the method returns an inert empty
/// result or throws a typed [ProductException], and the matching
/// [ServerCapabilities] flag is false (see [api2ServerCapabilities]).
class Api2Gateway implements ServerGateway {
  final Api2Client client;

  Api2Gateway({required this.client});

  Api2Gateway.connect({
    required String baseUrl,
    required String password,
    String? directory,
    String? workspace,
  }) : client = Api2Client.connect(
         baseUrl: baseUrl,
         password: password,
         directory: directory,
         workspace: workspace,
       );

  /// Cursor-walk bounds for satisfying the unpaginated domain contract.
  /// TODO(api2): replace fetch-all with real pagination once the domain
  /// session/message contracts grow cursor support.
  static const int maxPages = 20;
  static const int pageLimit = 200;

  Api2Transport get transport => client.transport;

  @override
  ServerCapabilities get capabilities => api2ServerCapabilities;

  @override
  String? get directory => client.directory;

  @override
  String? get workspace => client.workspace;

  @override
  bool get isClosed => transport.isClosed;

  @override
  void setLocation({String? directory, String? workspace}) =>
      client.setLocation(directory: directory, workspace: workspace);

  @override
  void close() => client.close();

  Future<T> _run<T>(Future<T> Function() body) async {
    try {
      return await body();
    } on Api2Error catch (error) {
      throw mapApi2Error(error);
    }
  }

  Map<String, dynamic> _loc([Map<String, dynamic> extra = const {}]) => {
    if (client.directory != null) 'location[directory]': client.directory,
    if (client.workspace != null) 'location[workspace]': client.workspace,
    ...extra,
  };

  // ---------------- Health ----------------

  @override
  Future<Health> health() => _run(() async => mapApi2Health(await client.health()));

  // ---------------- Sessions ----------------

  @override
  Future<List<Session>> sessions() => _run(() async {
    final all = <Session>[];
    String? cursor;
    for (var page = 0; page < maxPages; page += 1) {
      final result = cursor == null
          ? await client.sessions(limit: pageLimit)
          : await client.sessions(cursor: cursor);
      all.addAll(result.data.map(mapApi2Session));
      cursor = result.nextCursor;
      if (cursor == null) break;
    }
    return all;
  });

  @override
  Future<Session> createSession() =>
      _run(() async => mapApi2Session(await client.createSession()));

  @override
  Future<void> deleteSession(String id) => _run(() => client.deleteSession(id));

  @override
  Future<void> renameSession(String id, String title) =>
      _run(() => client.renameSession(id, title));

  @override
  Future<Session> session(String id) =>
      _run(() async => mapApi2Session(await client.session(id)));

  @override
  Future<Map<String, String>> sessionStatuses() => _run(() async {
    final active = await client.activeSessions();
    // v1 status values were compared against 'idle'; any live execution
    // entry means the session is busy.
    return {
      for (final entry in active.entries)
        entry.key: entry.value == 'idle' ? 'idle' : 'busy',
    };
  });

  @override
  Future<List<MessageWithParts>> messages(String id) => _run(() async {
    final all = <Api2Message>[];
    String? cursor;
    for (var page = 0; page < maxPages; page += 1) {
      final result = cursor == null
          ? await client.messages(id, limit: pageLimit, order: 'asc')
          : await client.messages(id, cursor: cursor);
      all.addAll(result.data);
      cursor = result.nextCursor;
      if (cursor == null) break;
    }
    return mapApi2Messages(id, all);
  });

  @override
  Future<List<Todo>> todos(String id) async =>
      // No todo endpoint in v2 (capability sessionTodos: false).
      const [];

  @override
  Future<List<FileDiff>> diff(String id) => _run(() async {
    // v2 dropped the session-scoped diff; the location-scoped working-tree
    // diff is the sanctioned replacement (matrix: session.diff → vcs.diff).
    final json = await transport.getJson(
      '/vcs/diff',
      query: _loc({'mode': 'working'}),
    );
    return _dataList(json, mapVcsDiffJson);
  });

  // ---------------- Prompting ----------------

  @override
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
  }) => _run(() async {
    await _applySelection(sessionID, model: model, agent: agent, variant: variant);
    await client.prompt(
      sessionID,
      text: text,
      files: [
        for (final attachment in attachments)
          Api2PromptFile(
            uri: attachment.url,
            name: attachment.filename.isNotEmpty ? attachment.filename : null,
          ),
      ],
      agents: [
        for (final mention in agentMentions)
          Api2PromptAgentMention(
            name: mention.name,
            mention: Api2Mention(
              start: mention.start,
              end: mention.end,
              text: mention.value,
            ),
          ),
      ],
    );
  });

  @override
  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  }) => _run(() async {
    // v2 shell takes only the command; agent/model ride as session state.
    await _applySelection(sessionID, model: model, agent: agent, variant: variant);
    await transport.postJson(
      '/session/$sessionID/shell',
      body: {'command': command},
    );
  });

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) => _run(() async {
    await _applySelection(sessionID, model: model, variant: variant);
    final name = command.startsWith('/') ? command.substring(1) : command;
    await transport.postJson(
      '/session/$sessionID/command',
      body: {'command': name, 'text': args},
    );
  });

  @override
  Future<void> abort(String sessionID) =>
      _run(() => client.interrupt(sessionID));

  Future<void> _applySelection(
    String sessionID, {
    ModelRef? model,
    String? agent,
    String? variant,
  }) async {
    if (model != null) {
      await client.switchModel(
        sessionID,
        Api2ModelRef(
          id: model.modelID,
          providerID: model.providerID,
          variant: variant,
        ),
      );
    }
    if (agent != null && agent.isNotEmpty) {
      await client.switchAgent(sessionID, agent);
    }
  }

  // ---------------- Permissions ----------------

  @override
  Future<List<PermissionRequest>> pendingPermissions() => _run(() async {
    final requests = await client.pendingPermissions();
    return requests.map(mapApi2PermissionRequest).toList();
  });

  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() =>
      pendingPermissions();

  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
  }) => _run(() async {
    var sessionID = legacySessionID;
    if (sessionID == null || sessionID.isEmpty) {
      // The v2 reply route is session-scoped; recover the owner from the
      // pending list when the caller only knows the request ID.
      final pending = await client.pendingPermissions();
      for (final request in pending) {
        if (request.id == requestID) {
          sessionID = request.sessionID;
          break;
        }
      }
    }
    if (sessionID == null || sessionID.isEmpty) {
      throw ApiException(
        'Permission request $requestID is no longer pending',
        statusCode: 404,
        errorTag: 'PermissionNotFoundError',
        requestID: requestID,
      );
    }
    await client.replyPermission(
      sessionID,
      requestID,
      mapPermissionReply(reply),
    );
  });

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply,
  ) => _run(
    () => client.replyPermission(
      sessionID,
      requestID,
      mapPermissionReply(reply),
    ),
  );

  // ---------------- Questions ----------------
  //
  // v1 questions were replaced by v2 forms, whose typed multi-field answers
  // do not fit the legacy string[][] reply contract. These stay inert
  // (capability legacyQuestionRequests: false); the forms UI arrives in a
  // later slice.

  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async => const [];

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) => Future.error(
    const ProductException('Question dialogs are unavailable on this server'),
  );

  @override
  Future<void> rejectQuestionV2(String sessionID, String requestID) =>
      Future.error(
        const ProductException(
          'Question dialogs are unavailable on this server',
        ),
      );

  // ---------------- Providers / agents ----------------

  @override
  Future<ProvidersResponse> providers() => _run(() async {
    final providersFuture = client.providers();
    final modelsFuture = client.models();
    final defaultFuture = client.defaultModel();
    return mapApi2Providers(
      providers: await providersFuture,
      models: await modelsFuture,
      defaultModel: await defaultFuture,
    );
  });

  @override
  Future<ProvidersResponse> configuredProviders() => providers();

  @override
  Future<List<AgentInfo>> agents() => _run(() async {
    final agents = await client.agents();
    return [
      for (final agent in agents)
        if (agent.selectable) mapApi2Agent(agent),
    ];
  });

  // ---------------- Files ----------------

  @override
  Future<List<FileNode>> listFiles(String path) => _run(() async {
    final entries = await client.fsList(path.isEmpty ? '.' : path);
    return entries.map(mapApi2FsEntry).toList();
  });

  @override
  Future<FileContent> fileContent(String path) => _run(() async {
    final bytes = await client.fsReadBytes(path);
    final looksBinary = bytes.take(8192).contains(0);
    if (looksBinary) {
      return FileContent(
        base64Encode(bytes),
        type: 'binary',
        encoding: 'base64',
        mimeType: _mimeForPath(path),
      );
    }
    return FileContent(utf8.decode(bytes, allowMalformed: true));
  });

  @override
  Future<List<String>> findFile(String query) => _run(() async {
    final entries = await client.fsFind(query, type: 'file');
    return [for (final entry in entries) entry.path];
  });

  @override
  Future<List<FindMatch>> findText(String pattern) async =>
      // No workspace text search in v2 (capability textSearch: false); the
      // one v1 declaration had no consumer either.
      const [];

  // ---------------- Events ----------------

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => Api2LiveEventChannel(
    transport: transport,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
    directoryFilter: () => client.directory,
  );

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => Api2LiveEventChannel(
    transport: transport,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
    global: true,
  );

  // ---------------- helpers ----------------

  static List<T> _dataList<T>(
    dynamic json,
    T Function(Map<String, dynamic>) parse,
  ) {
    final data = json is Map<String, dynamic> ? json['data'] : null;
    if (data is! List) return const [];
    final out = <T>[];
    for (final item in data) {
      if (item is Map) {
        try {
          out.add(parse(Map<String, dynamic>.from(item)));
        } catch (_) {}
      }
    }
    return out;
  }

  static String? _mimeForPath(String path) {
    final lower = path.toLowerCase();
    if (lower.endsWith('.png')) return 'image/png';
    if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
    if (lower.endsWith('.gif')) return 'image/gif';
    if (lower.endsWith('.webp')) return 'image/webp';
    if (lower.endsWith('.bmp')) return 'image/bmp';
    if (lower.endsWith('.pdf')) return 'application/pdf';
    return null;
  }
}

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
import '../domain/parallel_requests.dart';
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
class Api2Gateway implements ServerGateway, SessionSelectionGateway {
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
  Future<Health> health() =>
      _run(() async => mapApi2Health(await client.health()));

  // ---------------- Sessions ----------------

  @override
  Future<List<Session>> sessions() async => (await sessionPage()).items;

  @override
  Future<ServerPage<Session>> sessionPage({String? cursor, int limit = 100}) =>
      _run(() async {
        final page = await client.sessions(
          limit: limit,
          order: 'desc',
          cursor: cursor,
        );
        return ServerPage(
          items: page.data.map(mapApi2Session).toList(),
          nextCursor: page.nextCursor?.isNotEmpty == true
              ? page.nextCursor
              : null,
        );
      });

  @override
  Future<Session> createSession() =>
      _run(() async => mapApi2Session(await client.createSession()));

  @override
  Future<Session> createSelectedSession(SessionSelection defaults) => _run(
    () async => mapApi2Session(
      await client.createSession(
        model: defaults.model == null
            ? null
            : Api2ModelRef(
                id: defaults.model!.normalized.modelID,
                providerID: defaults.model!.providerID,
                variant: defaults.variant.isEmpty ? null : defaults.variant,
              ),
        agent: defaults.agent?.isNotEmpty == true ? defaults.agent : null,
      ),
    ),
  );

  @override
  Future<void> setSessionModel(
    String sessionID,
    ModelRef model,
    String variant,
  ) => _run(
    () => client.switchModel(
      sessionID,
      Api2ModelRef(
        id: model.normalized.modelID,
        providerID: model.providerID,
        variant: variant.isEmpty ? null : variant,
      ),
    ),
  );

  @override
  Future<void> setSessionAgent(String sessionID, String agent) =>
      _run(() => client.switchAgent(sessionID, agent));

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
  Future<List<MessageWithParts>> messages(String id) async =>
      (await messagePage(id)).items;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) => _run(() async {
    final page = await client.messages(
      id,
      limit: limit,
      order: 'desc',
      cursor: cursor,
    );
    return ServerPage(
      items: mapApi2Messages(id, page.data.reversed.toList()),
      nextCursor: page.nextCursor?.isNotEmpty == true ? page.nextCursor : null,
    );
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

  Future<void> _requireResolvedRevert(String sessionID) async {
    // The pinned server implicitly commits a stage before admitting a
    // prompt, even with resume:false. Never let Send or offline replay
    // silently perform that permanent operation.
    if ((await client.session(sessionID)).reverted) {
      throw ApiException(
        'Review the staged revert, then clear it or make it permanent before sending.',
        statusCode: 409,
        errorTag: 'SessionRevertPending',
      );
    }
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
  }) => _run(() async {
    await _requireResolvedRevert(sessionID);
    await client.prompt(
      sessionID,
      text: text,
      delivery: switch (delivery) {
        null => null,
        PromptDelivery.steer => Api2Delivery.steer,
        PromptDelivery.queue => Api2Delivery.queue,
      },
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
    // v2 uses its existing session selection for ordinary sends.
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
    await _requireResolvedRevert(sessionID);
    final name = command.startsWith('/') ? command.substring(1) : command;
    await transport.postJson(
      '/session/$sessionID/command',
      body: {'command': name, 'text': args},
    );
  });

  @override
  Future<void> abort(String sessionID) =>
      _run(() => client.interrupt(sessionID));

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
    String? message,
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
      message: message,
    );
  });

  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) => _run(
    () => client.replyPermission(
      sessionID,
      requestID,
      mapPermissionReply(reply),
      message: message,
    ),
  );

  // ---------------- Forms ----------------

  @override
  Future<List<Api2FormInfo>> sessionForms(String sessionID) =>
      _run(() => client.sessionForms(sessionID));

  @override
  Future<List<Api2FormInfo>> pendingForms() =>
      _run(() => client.pendingForms());

  @override
  Future<Api2FormState> formState(String sessionID, String formID) =>
      _run(() => client.formState(sessionID, formID));

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) => _run(() => client.replyForm(sessionID, formID, answer));

  @override
  Future<void> cancelForm(String sessionID, String formID) =>
      _run(() => client.cancelForm(sessionID, formID));

  // ---------------- Inbox ----------------

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) =>
      _run(() => client.inbox(sessionID));

  @override
  Future<void> cancelInboxItem(String sessionID, String inboxID) =>
      _run(() => client.cancelInboxItem(sessionID, inboxID));

  @override
  Future<void> steerInboxItem(String sessionID, String inboxID) =>
      _run(() => client.steerInboxItem(sessionID, inboxID));

  @override
  Future<void> queueInboxItem(String sessionID, String inboxID) =>
      _run(() => client.queueInboxItem(sessionID, inboxID));

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
    final (providers, models, defaultModel) = await waitForRequests(
      client.providers(),
      client.models(),
      client.defaultModel(),
    );
    return mapApi2Providers(
      providers: providers,
      models: models,
      defaultModel: defaultModel,
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

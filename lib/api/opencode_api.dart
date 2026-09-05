import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;

import '../api2/models.dart' show Api2FormInfo, Api2FormState, Api2InboxItem;
import '../domain/server_gateway.dart';
import 'models.dart';
import 'sse.dart';

export 'models.dart' show ApiException;

/// HTTP client for a single opencode server (`opencode serve`).
class OpenCodeApi
    implements ServerGateway, SessionRetryGateway, EventStreamTransport {
  final String baseUrl;
  final String? username;
  final String? password;
  late final Dio _dio;
  late final sdk.OpencodeSdk sdkClient;
  String? _directory;
  String? _workspace;
  bool _closed = false;

  Dio get dio => _dio;
  @override
  String? get directory => _directory;
  @override
  String? get workspace => _workspace;
  @override
  bool get isClosed => _closed;

  @override
  ServerCapabilities get capabilities => ServerCapabilities.allV1;

  @override
  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
  }

  /// Cancels in-flight requests and closes the shared handwritten/SDK client.
  @override
  void close() {
    if (_closed) return;
    _closed = true;
    _dio.close(force: true);
  }

  Map<String, dynamic> _query([Map<String, dynamic> values = const {}]) => {
    if (_directory != null) 'directory': _directory,
    if (_workspace != null) 'workspace': _workspace,
    ...values,
  };

  static const connectTimeout = Duration(seconds: 8);
  static const requestTimeout = Duration(
    minutes: 10,
  ); // sync endpoints can run long

  OpenCodeApi({required this.baseUrl, this.username, this.password}) {
    final options = BaseOptions(
      baseUrl: baseUrl.endsWith('/')
          ? baseUrl.substring(0, baseUrl.length - 1)
          : baseUrl,
      connectTimeout: connectTimeout,
      receiveTimeout: requestTimeout,
      responseType: ResponseType.json,
      validateStatus: (s) => s != null && s >= 200 && s < 300,
    );
    _dio = Dio(options);
    if (password != null && password!.isNotEmpty) {
      final user = (username == null || username!.isEmpty)
          ? 'opencode'
          : username!;
      final token = base64Encode(utf8.encode('$user:$password'));
      _dio.options.headers['Authorization'] = 'Basic $token';
    }
    // Generated APIs and the handwritten compatibility facade intentionally
    // share transport, timeouts, base URL, and authentication.
    sdkClient = sdk.OpencodeSdk(dio: _dio, interceptors: const []);
  }

  Never _fail(DioException e, String what) {
    final code = e.response?.statusCode;
    final responseData = e.response?.data;
    final detail = errorBodyDetail(responseData);
    final error = responseData is Map
        ? Map<String, dynamic>.from(responseData)
        : null;
    // Without a status code the message used to end at "failed", which told
    // the user nothing. Keep the transport cause so the UI can say whether
    // nothing answered, the connection timed out, or the certificate was
    // rejected, and act on it.
    final cause = code == null ? transportCause(e) : null;
    throw ApiException(
      '$what failed'
      '${code != null ? ' (HTTP $code)' : ''}'
      '${detail != null && code != null ? ': $detail' : ''}'
      '${cause != null ? ': $cause' : ''}',
      statusCode: code,
      errorTag: error?['_tag']?.toString(),
      requestID: error?['requestID']?.toString(),
    );
  }

  /// A short, human cause for a request that never produced a response.
  static String transportCause(DioException e) {
    switch (e.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        return 'timed out';
      case DioExceptionType.badCertificate:
        return 'certificate not trusted';
      case DioExceptionType.cancel:
        return 'cancelled';
      case DioExceptionType.connectionError:
      case DioExceptionType.badResponse:
      case DioExceptionType.unknown:
        final inner = e.error;
        final text = (inner ?? e.message ?? '').toString();
        final lower = text.toLowerCase();
        if (lower.contains('refused')) return 'connection refused';
        if (lower.contains('unreachable')) return 'network unreachable';
        if (lower.contains('no route')) return 'no route to host';
        if (lower.contains('handshake') || lower.contains('certificate')) {
          return 'certificate not trusted';
        }
        if (lower.contains('failed host lookup') ||
            lower.contains('name or service not known')) {
          return 'host name not found';
        }
        if (lower.contains('timed out')) return 'timed out';
        if (lower.contains('reset by peer') || lower.contains('broken pipe')) {
          return 'connection dropped';
        }
        return text.isEmpty ? 'no response' : text;
    }
  }

  Never _failGenerated(sdk.OpenCodeApiException e, String what) {
    final detail = errorBodyDetail(e.rawPayload);
    final error = e.rawPayload is Map
        ? Map<String, dynamic>.from(e.rawPayload! as Map)
        : null;
    throw ApiException(
      '$what failed (HTTP ${e.statusCode})${detail != null ? ': $detail' : ''}',
      statusCode: e.statusCode,
      errorTag: error?['_tag']?.toString(),
      requestID: error?['requestID']?.toString(),
    );
  }

  static const _maxErrorDetailChars = 120;

  /// Distills an HTTP error body into a short human-readable detail: the
  /// `message` field of a JSON error envelope (top-level, `data.message`, or
  /// `error.message`), else the raw body collapsed and truncated to
  /// [_maxErrorDetailChars] characters so developer JSON and HTML dumps never
  /// reach product surfaces verbatim.
  static String? errorBodyDetail(Object? body) {
    if (body == null) return null;
    Object? decoded = body;
    if (decoded is String) {
      final text = decoded.trim();
      if (text.isEmpty) return null;
      try {
        decoded = jsonDecode(text);
      } on FormatException {
        decoded = text;
      }
    }
    if (decoded is Map) {
      final data = decoded['data'];
      final error = decoded['error'];
      for (final candidate in [
        decoded['message'],
        if (data is Map) data['message'],
        if (error is Map) error['message'],
        if (error is String) error,
      ]) {
        if (candidate is String && candidate.trim().isNotEmpty) {
          return _clampErrorDetail(candidate);
        }
      }
    }
    return _clampErrorDetail(decoded.toString());
  }

  static String? _clampErrorDetail(String text) {
    final collapsed = text.replaceAll(RegExp(r'\s+'), ' ').trim();
    if (collapsed.isEmpty) return null;
    if (collapsed.length <= _maxErrorDetailChars) return collapsed;
    return '${collapsed.substring(0, _maxErrorDetailChars - 1)}…';
  }

  bool _wasSuccessfulResponse(DioException error) {
    final status = error.response?.statusCode;
    return status != null && status >= 200 && status < 300;
  }

  @override
  LiveEventChannel openEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => EventStream(
    api: this,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
  );

  @override
  LiveEventChannel openGlobalEventChannel({
    required void Function(EventEnvelope event) onEvent,
    required void Function(StreamStatus status) onStatus,
    void Function(Object error)? onError,
  }) => EventStream(
    api: this,
    onEvent: onEvent,
    onStatus: onStatus,
    onError: onError,
    global: true,
  );

  /// Opens the long-lived `/event` SSE stream.
  @override
  Future<Response<ResponseBody>> openEventStream({CancelToken? cancelToken}) {
    return _dio.get<ResponseBody>(
      '/event',
      queryParameters: _query(),
      options: Options(
        responseType: ResponseType.stream,
        headers: {'Accept': 'text/event-stream'},
        receiveTimeout: null,
      ),
      cancelToken: cancelToken,
    );
  }

  /// Opens the server-wide `/global/event` stream. Its wire envelope wraps the
  /// actual event in `payload`, unlike the location-scoped `/event` stream.
  @override
  Future<Response<ResponseBody>> openGlobalEventStream({
    CancelToken? cancelToken,
  }) {
    return _dio.get<ResponseBody>(
      '/global/event',
      options: Options(
        responseType: ResponseType.stream,
        headers: {'Accept': 'text/event-stream'},
        receiveTimeout: null,
      ),
      cancelToken: cancelToken,
    );
  }

  @override
  Future<Health> health() async {
    try {
      final r = await _dio.get('/global/health');
      return Health.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Health check');
    }
  }

  // ----- Sessions -----

  @override
  Future<List<Session>> sessions() async {
    try {
      final response = await sdkClient.getSessionApi().sessionList(
        directory: _directory,
        workspace: _workspace,
      );
      return (response.data ?? const [])
          .map((session) => Session.fromJson(session.toJson()))
          .toList();
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'List sessions');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is List) {
        try {
          return raw
              .whereType<Map>()
              .map(
                (session) =>
                    Session.fromJson(Map<String, dynamic>.from(session)),
              )
              .toList();
        } catch (_) {
          // Fall through to the existing product-facing transport error.
        }
      }
      _fail(e, 'List sessions');
    }
  }

  @override
  Future<Session> createSession() async {
    try {
      final response = await sdkClient.getSessionApi().sessionCreate(
        directory: _directory,
        workspace: _workspace,
        sessionCreateRequest: sdk.SessionCreateRequest(),
      );
      final session = response.data;
      if (session == null) {
        throw ApiException('Create session failed: server returned no session');
      }
      return Session.fromJson(session.toJson());
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Create session');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is Map) {
        try {
          return Session.fromJson(Map<String, dynamic>.from(raw));
        } catch (_) {
          // Fall through to the existing product-facing transport error.
        }
      }
      _fail(e, 'Create session');
    }
  }

  @override
  Future<void> deleteSession(String id) async {
    try {
      await sdkClient.getSessionApi().sessionDelete(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Delete session');
    } on DioException catch (e) {
      if (_wasSuccessfulResponse(e)) return;
      _fail(e, 'Delete session');
    }
  }

  @override
  Future<void> renameSession(String id, String title) async {
    try {
      await sdkClient.getSessionApi().sessionUpdate(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
        sessionUpdateRequest: sdk.SessionUpdateRequest(title: title),
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Rename session');
    } on DioException catch (e) {
      if (_wasSuccessfulResponse(e)) return;
      _fail(e, 'Rename session');
    }
  }

  @override
  Future<Session> session(String id) async {
    try {
      final response = await sdkClient.getSessionApi().sessionGet(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
      );
      final session = response.data;
      if (session == null) {
        throw ApiException('Get session failed: server returned no session');
      }
      return Session.fromJson(session.toJson());
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is Map) {
        try {
          return Session.fromJson(Map<String, dynamic>.from(raw));
        } catch (_) {
          // Fall through to the existing product-facing transport error.
        }
      }
      _fail(e, 'Get session');
    }
  }

  /// Returns {sessionID: "idle"|"busy"|"retry"}.
  @override
  Future<Map<String, String>> sessionStatuses() async {
    try {
      final response = await sdkClient.getSessionApi().sessionStatus(
        directory: _directory,
        workspace: _workspace,
      );
      return _sessionStatusesFromJson({
        for (final entry in (response.data ?? const {}).entries)
          entry.key: entry.value.toJson(),
      });
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session status');
    } on DioException catch (e) {
      if (_wasSuccessfulResponse(e) && e.response?.data is Map) {
        return _sessionStatusesFromJson(e.response?.data);
      }
      _fail(e, 'Get session status');
    }
  }

  Map<String, String> _sessionStatusesFromJson(Object? data) {
    final out = <String, String>{};
    if (data is Map) {
      data.forEach((k, v) {
        out[k.toString()] = v is Map
            ? ((v['type'] ?? 'idle')).toString()
            : 'idle';
      });
    }
    return out;
  }

  /// Sessions currently in provider-retry backoff, with the attempt counter,
  /// message and next-attempt time the v1 status endpoint carries.
  @override
  Future<Map<String, SessionRetryState>> sessionRetryStates() async {
    Object? raw;
    try {
      final response = await sdkClient.getSessionApi().sessionStatus(
        directory: _directory,
        workspace: _workspace,
      );
      raw = {
        for (final entry in (response.data ?? const {}).entries)
          entry.key: entry.value.toJson(),
      };
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session status');
    } on DioException catch (e) {
      if (_wasSuccessfulResponse(e) && e.response?.data is Map) {
        raw = e.response?.data;
      } else {
        _fail(e, 'Get session status');
      }
    }
    return sessionRetryStatesFromJson(raw);
  }

  /// Pure parser behind [sessionRetryStates]: keeps only `retry` entries.
  static Map<String, SessionRetryState> sessionRetryStatesFromJson(
    Object? data,
  ) {
    final out = <String, SessionRetryState>{};
    if (data is Map) {
      data.forEach((k, v) {
        final retry = SessionRetryState.fromStatusJson(v);
        if (retry != null) out[k.toString()] = retry;
      });
    }
    return out;
  }

  @override
  Future<List<MessageWithParts>> messages(String id) async =>
      (await messagePage(id)).items;

  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    try {
      final response = await sdkClient.getSessionApi().sessionMessages(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
        limit: limit,
        before: cursor,
      );
      final items = (response.data ?? const [])
          .map(
            (bundle) => _bundleFromJson({
              'info': bundle.info.toJson(),
              'parts': bundle.parts.map((part) => part.toJson()).toList(),
            }),
          )
          .toList();
      return ServerPage(
        items: items,
        nextCursor: _messageCursor(response.headers),
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session messages');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is List) {
        try {
          return ServerPage(
            items: raw.map(_bundleFromJson).toList(),
            nextCursor: _messageCursor(e.response!.headers),
          );
        } catch (_) {
          // Fall through to the existing product-facing transport error.
        }
      }
      _fail(e, 'Get session messages');
    }
  }

  String? _messageCursor(Headers headers) {
    final cursor = headers.value('x-next-cursor');
    if (cursor != null && cursor.isNotEmpty) return cursor;
    // Read only the opaque token from Link, never follow a returned URL.
    for (final link in (headers.value('link') ?? '').split(',')) {
      if (!RegExp(r'rel="?next"?').hasMatch(link)) continue;
      final match = RegExp(r'<([^>]+)>').firstMatch(link);
      final next = Uri.tryParse(
        match?.group(1) ?? '',
      )?.queryParameters['before'];
      if (next != null && next.isNotEmpty) return next;
    }
    return null;
  }

  MessageWithParts _bundleFromJson(dynamic raw) {
    final j = Map<String, dynamic>.from(raw as Map);
    return MessageWithParts(
      info: MessageInfo.fromJson(Map<String, dynamic>.from(j['info'] as Map)),
      parts: ((j['parts'] as List?) ?? const [])
          .whereType<Map<String, dynamic>>()
          .map((p) => Part.fromJson(p))
          .toList(),
    );
  }

  /// Fire-and-forget prompt; responses arrive via the SSE event stream.
  /// [delivery] is an OpenCode 2 concept and is ignored on v1.
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
    try {
      await sdkClient.getSessionApi().sessionPromptAsync(
        sessionID: sessionID,
        directory: _directory,
        workspace: _workspace,
        sessionPromptAsyncRequest: sdk.SessionPromptAsyncRequest(
          model: model == null
              ? null
              : sdk.SessionPromptAsyncRequestModel(
                  providerID: model.providerID,
                  modelID: model.modelID,
                ),
          agent: agent,
          variant: variant,
          parts: [
            sdk.OpencodeSdkRawUnion086({'type': 'text', 'text': text}),
            ...agentMentions.map(
              (mention) => sdk.OpencodeSdkRawUnion086(
                sdk.AgentPartInput(
                  type: sdk.AgentPartInputTypeEnum.agent,
                  name: mention.name,
                  source_: sdk.AgentPartSource(
                    value: mention.value,
                    start: mention.start,
                    end: mention.end,
                  ),
                ).toJson(),
              ),
            ),
            ...attachments.map(
              (attachment) => sdk.OpencodeSdkRawUnion086(attachment.toJson()),
            ),
          ],
        ),
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Send prompt');
    } on DioException catch (e) {
      _fail(e, 'Send prompt');
    }
  }

  @override
  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  }) async {
    try {
      // The generated SessionShellRequest currently omits OpenCode's thinking
      // variant. Keep this compatibility request until that wire field exists
      // in the generated contract; silently dropping it changes execution.
      await _dio.post(
        '/session/$sessionID/shell',
        data: shellRequestBody(
          command,
          agent: agent,
          model: model,
          variant: variant,
        ),
        queryParameters: _query(),
      );
    } on DioException catch (e) {
      _fail(e, 'Run command');
    }
  }

  @override
  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    try {
      await sdkClient.getSessionApi().sessionCommand(
        sessionID: sessionID,
        directory: _directory,
        workspace: _workspace,
        sessionCommandRequest: sdk.SessionCommandRequest(
          arguments: args,
          command: command,
          model: model?.wireName,
          variant: variant,
        ),
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Run /command');
    } on DioException catch (e) {
      _fail(e, 'Run /command');
    }
  }

  @override
  Future<void> abort(String sessionID) async {
    try {
      await sdkClient.getSessionApi().sessionAbort(
        sessionID: sessionID,
        directory: _directory,
        workspace: _workspace,
      );
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Stop session');
    } on DioException catch (e) {
      _fail(e, 'Stop session');
    }
  }

  @override
  Future<List<Todo>> todos(String id) async {
    try {
      final response = await sdkClient.getSessionApi().sessionTodo(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
      );
      return (response.data ?? const [])
          .map(
            (todo) => Todo(
              content: todo.content,
              status: todo.status,
              priority: todo.priority,
            ),
          )
          .toList();
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session todos');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is List) {
        try {
          return raw
              .whereType<Map>()
              .map((todo) => Todo.fromJson(Map<String, dynamic>.from(todo)))
              .toList();
        } catch (_) {
          // Preserve the existing product error if even the tolerant parser
          // cannot understand a successful response from an older server.
        }
      }
      _fail(e, 'Get session todos');
    }
  }

  @override
  Future<List<FileDiff>> diff(String id) async {
    try {
      final response = await sdkClient.getSessionApi().sessionDiff(
        sessionID: id,
        directory: _directory,
        workspace: _workspace,
      );
      return (response.data ?? const [])
          .map(
            (diff) => FileDiff(
              file: diff.file ?? '',
              patch: diff.patch_,
              additions: diff.additions.toInt(),
              deletions: diff.deletions.toInt(),
              status:
                  diff.status ==
                      sdk.SnapshotFileDiffStatusEnum.unknownDefaultOpenApi
                  ? null
                  : diff.status?.value.toString(),
            ),
          )
          .toList();
    } on sdk.OpenCodeApiException catch (e) {
      _failGenerated(e, 'Get session diff');
    } on DioException catch (e) {
      final raw = e.response?.data;
      if (_wasSuccessfulResponse(e) && raw is List) {
        try {
          return raw
              .whereType<Map>()
              .map(
                (value) => FileDiff.fromJson(Map<String, dynamic>.from(value)),
              )
              .toList();
        } catch (_) {
          // Preserve the existing product error if even the tolerant parser
          // cannot understand a successful response from an older server.
        }
      }
      _fail(e, 'Get session diff');
    }
  }

  @override
  Future<List<PermissionRequest>> pendingPermissions() async {
    try {
      final response = await sdkClient.getPermissionApi().permissionList(
        directory: _directory,
        workspace: _workspace,
      );
      return (response.data ?? const [])
          .map((item) => PermissionRequest.fromJson(item.toJson()))
          .toList();
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'List pending permissions');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error) && raw is List) {
        try {
          return raw
              .whereType<Map>()
              .map(
                (item) =>
                    PermissionRequest.fromJson(Map<String, dynamic>.from(item)),
              )
              .toList();
        } catch (_) {
          // Older servers can return successful permission objects that omit
          // fields required by the generated contract.
        }
      }
      _fail(error, 'List pending permissions');
    }
  }

  /// Lists requests exposed by the V2 compatibility API.
  ///
  /// Unlike the location-scoped legacy endpoint, this endpoint wraps its
  /// result in `{location, data}` and uses the V2 permission field names.
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async {
    try {
      final response = await sdkClient
          .getPermissionsApi()
          .v2PermissionRequestList(
            locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
            locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          );
      final payload = response.data;
      if (payload == null) {
        throw ApiException('Malformed V2 permission request envelope');
      }
      _validateV2Location(payload.location, 'permission');
      return payload.data
          .map(
            (item) => PermissionRequest(
              id: item.id,
              sessionID: item.sessionID,
              permission: item.action,
              patterns: item.resources,
              metadata: item.metadata is Map
                  ? Map<String, dynamic>.from(item.metadata! as Map)
                  : const {},
              always: item.save ?? const [],
              tool: item.source_ == null
                  ? null
                  : PermissionTool(
                      messageID: item.source_!.messageID,
                      callID: item.source_!.callID,
                    ),
            ),
          )
          .where(
            (permission) =>
                permission.id.isNotEmpty && permission.sessionID.isNotEmpty,
          )
          .toList();
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'List V2 pending permissions');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error)) {
        return _permissionsFromLooseV2Envelope(raw);
      }
      _fail(error, 'List V2 pending permissions');
    }
  }

  List<PermissionRequest> _permissionsFromLooseV2Envelope(dynamic raw) {
    return _v2EnvelopeData(raw, 'permission')
        .map(
          (item) => PermissionRequest.fromJson({
            'id': item['id'],
            'sessionID': item['sessionID'],
            'permission': item['action'],
            'patterns': item['resources'],
            'metadata': item['metadata'],
            'always': item['save'],
            if (item['source'] is Map) 'tool': item['source'],
          }),
        )
        .where(
          (permission) =>
              permission.id.isNotEmpty && permission.sessionID.isNotEmpty,
        )
        .toList();
  }

  void _validateV2Location(sdk.LocationInfo location, String kind) {
    final locationMatches =
        (_directory == null || location.directory == _directory) &&
        (_workspace == null || location.workspaceID == _workspace);
    if (!locationMatches) {
      throw ApiException('Mismatched V2 $kind request location');
    }
  }

  /// Returns raw V2 question objects from the global `{location, data}`
  /// envelope. The app's repository model owns question parsing.
  @override
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async {
    try {
      final response = await sdkClient
          .getSessionQuestionsApi()
          .v2QuestionRequestList(
            locationLeftSquareBracketDirectoryRightSquareBracket: _directory,
            locationLeftSquareBracketWorkspaceRightSquareBracket: _workspace,
          );
      final payload = response.data;
      if (payload == null) {
        throw ApiException('Malformed V2 question request envelope');
      }
      _validateV2Location(payload.location, 'question');
      return payload.data.map((question) => question.toJson()).toList();
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'List V2 pending questions');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error)) {
        return _v2EnvelopeData(raw, 'question');
      }
      _fail(error, 'List V2 pending questions');
    }
  }

  List<Map<String, dynamic>> _v2EnvelopeData(dynamic raw, String kind) {
    if (raw is! Map) {
      throw ApiException('Malformed V2 $kind request envelope');
    }
    final location = raw['location'];
    if (location is! Map) {
      throw ApiException('Malformed V2 $kind request location');
    }
    final responseLocation = Map<String, dynamic>.from(location);
    final responseWorkspace =
        responseLocation['workspaceID'] ?? responseLocation['workspace'];
    final locationMatches =
        (_directory == null ||
            responseLocation['directory']?.toString() == _directory) &&
        (_workspace == null || responseWorkspace?.toString() == _workspace);
    if (!locationMatches) {
      throw ApiException('Mismatched V2 $kind request location');
    }
    final data = raw['data'];
    if (data is! List || data.any((item) => item is! Map)) {
      throw ApiException('Malformed V2 $kind request data');
    }
    return data
        .cast<Map>()
        .map((item) => Map<String, dynamic>.from(item))
        .toList();
  }

  /// [message] (reject steering text) is an OpenCode 2 concept: the v1 reply
  /// shape has no field for it, so it is accepted and ignored here.
  @override
  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
    String? message,
  }) async {
    final replyValue = switch (reply) {
      'once' => sdk.PermissionReplyRequestReplyEnum.once,
      'always' => sdk.PermissionReplyRequestReplyEnum.always,
      'reject' => sdk.PermissionReplyRequestReplyEnum.reject,
      _ => throw ArgumentError.value(
        reply,
        'reply',
        'must match the API contract',
      ),
    };
    try {
      await sdkClient.getPermissionApi().permissionReply(
        requestID: requestID,
        directory: _directory,
        workspace: _workspace,
        permissionReplyRequest: sdk.PermissionReplyRequest(reply: replyValue),
      );
    } on sdk.OpenCodeApiException catch (error) {
      if (!_canUseLegacyPermissionReply(
        status: error.statusCode,
        payload: error.rawPayload,
        legacySessionID: legacySessionID,
        legacyPermissionID: legacyPermissionID,
      )) {
        _failGenerated(error, 'Reply to permission');
      }
      await _respondLegacyPermission(
        legacySessionID!,
        legacyPermissionID!,
        reply,
      );
    } on DioException catch (error) {
      if (!_canUseLegacyPermissionReply(
        status: error.response?.statusCode,
        payload: error.response?.data,
        legacySessionID: legacySessionID,
        legacyPermissionID: legacyPermissionID,
      )) {
        _fail(error, 'Reply to permission');
      }
      await _respondLegacyPermission(
        legacySessionID!,
        legacyPermissionID!,
        reply,
      );
    }
  }

  bool _canUseLegacyPermissionReply({
    required int? status,
    required Object? payload,
    required String? legacySessionID,
    required String? legacyPermissionID,
  }) {
    final body = payload is Map ? Map<String, dynamic>.from(payload) : null;
    final permissionNotFoundError =
        status == 404 && body?['_tag']?.toString() == 'PermissionNotFoundError';
    return !permissionNotFoundError &&
        (status == 404 || status == 405) &&
        legacySessionID?.isNotEmpty == true &&
        legacyPermissionID?.isNotEmpty == true;
  }

  Future<void> _respondLegacyPermission(
    String sessionID,
    String permissionID,
    String reply,
  ) async {
    final responseValue = switch (reply) {
      'once' => sdk.PermissionRespondRequestResponseEnum.once,
      'always' => sdk.PermissionRespondRequestResponseEnum.always,
      'reject' => sdk.PermissionRespondRequestResponseEnum.reject,
      _ => throw StateError('Validated permission reply became invalid'),
    };
    try {
      // ignore: deprecated_member_use
      await sdkClient.getSessionApi().permissionRespond(
        sessionID: sessionID,
        permissionID: permissionID,
        directory: _directory,
        workspace: _workspace,
        permissionRespondRequest: sdk.PermissionRespondRequest(
          response: responseValue,
        ),
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Reply to permission');
    } on DioException catch (error) {
      _fail(error, 'Reply to permission');
    }
  }

  /// [message] is ignored on v1 (see [respondPermission]).
  @override
  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply, {
    String? message,
  }) async {
    final replyValue = switch (reply) {
      'once' => sdk.PermissionV2Reply.once,
      'always' => sdk.PermissionV2Reply.always,
      'reject' => sdk.PermissionV2Reply.reject,
      _ => throw ArgumentError.value(
        reply,
        'reply',
        'must match the API contract',
      ),
    };
    try {
      await sdkClient.getPermissionsApi().v2SessionPermissionReply(
        sessionID: sessionID,
        requestID: requestID,
        v2SessionPermissionReplyRequest: sdk.V2SessionPermissionReplyRequest(
          reply: replyValue,
        ),
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Reply to permission');
    } on DioException catch (error) {
      _fail(error, 'Reply to permission');
    }
  }

  @override
  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    try {
      await sdkClient.getSessionQuestionsApi().v2SessionQuestionReply(
        sessionID: sessionID,
        requestID: requestID,
        questionV2Reply: sdk.QuestionV2Reply(answers: answers),
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Reply to question');
    } on DioException catch (error) {
      _fail(error, 'Reply to question');
    }
  }

  @override
  Future<void> rejectQuestionV2(String sessionID, String requestID) async {
    try {
      await sdkClient.getSessionQuestionsApi().v2SessionQuestionReject(
        sessionID: sessionID,
        requestID: requestID,
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Reject question');
    } on DioException catch (error) {
      _fail(error, 'Reject question');
    }
  }

  // ----- Forms / inbox (OpenCode 2 only; inert on v1) -----
  //
  // v1 servers have neither structured forms nor a session inbox
  // (capabilities `forms`/`inbox` are false). Lists come back empty so
  // pending-count sums need no protocol branch; mutations are a typed
  // failure because reaching them implies a UI gating bug.

  static const _formsUnavailable = ProductException(
    'Forms are unavailable on this server',
  );
  static const _inboxUnavailable = ProductException(
    'The send queue is unavailable on this server',
  );

  @override
  Future<List<Api2FormInfo>> sessionForms(String sessionID) async => const [];

  @override
  Future<List<Api2FormInfo>> pendingForms() async => const [];

  @override
  Future<Api2FormState> formState(String sessionID, String formID) =>
      Future.error(_formsUnavailable);

  @override
  Future<void> replyForm(
    String sessionID,
    String formID,
    Map<String, dynamic> answer,
  ) => Future.error(_formsUnavailable);

  @override
  Future<void> cancelForm(String sessionID, String formID) =>
      Future.error(_formsUnavailable);

  @override
  Future<List<Api2InboxItem>> inboxItems(String sessionID) async => const [];

  @override
  Future<void> cancelInboxItem(String sessionID, String inboxID) =>
      Future.error(_inboxUnavailable);

  @override
  Future<void> steerInboxItem(String sessionID, String inboxID) =>
      Future.error(_inboxUnavailable);

  @override
  Future<void> queueInboxItem(String sessionID, String inboxID) =>
      Future.error(_inboxUnavailable);

  // ----- Providers / agents -----

  @override
  Future<ProvidersResponse> providers() async {
    try {
      final r = await _dio.get('/provider', queryParameters: _query());
      return ProvidersResponse.fromJson(
        Map<String, dynamic>.from(r.data as Map),
      );
    } on DioException {
      // OpenCode versions predating provider.list expose the configured
      // catalog through this endpoint instead.
      return configuredProviders();
    }
  }

  @override
  Future<ProvidersResponse> configuredProviders() async {
    final r = await _dio.get('/config/providers', queryParameters: _query());
    return ProvidersResponse.fromJson(Map<String, dynamic>.from(r.data as Map));
  }

  @override
  Future<List<AgentInfo>> agents() async {
    try {
      final r = await _dio.get('/agent', queryParameters: _query());
      final list = (r.data as List)
          .whereType<Map<String, dynamic>>()
          .map(AgentInfo.fromJson)
          .toList();
      // Primary agents first; subagents are not useful as chat targets.
      list.sort((a, b) {
        int rank(AgentInfo x) =>
            x.mode == null ? 0 : (x.mode == 'subagent' ? 2 : 1);
        return rank(a).compareTo(rank(b));
      });
      return list;
    } on DioException {
      return [];
    }
  }

  // ----- Files -----

  @override
  Future<List<FileNode>> listFiles(String path) async {
    try {
      final response = await sdkClient.getFileApi().fileList(
        path: path,
        directory: _directory,
        workspace: _workspace,
      );
      return _sortFileNodes(
        (response.data ?? const [])
            .map(
              (node) => FileNode(
                name: node.name,
                path: node.path,
                isDir: node.type == sdk.FileNodeTypeEnum.directory,
              ),
            )
            .toList(),
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'List files');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error) && raw is List) {
        try {
          return _sortFileNodes(
            raw
                .whereType<Map>()
                .map(
                  (node) => FileNode.fromJson(Map<String, dynamic>.from(node)),
                )
                .toList(),
          );
        } catch (_) {
          // Preserve the existing product error when a successful old-server
          // response is not understandable even by the tolerant parser.
        }
      }
      _fail(error, 'List files');
    }
  }

  List<FileNode> _sortFileNodes(List<FileNode> nodes) {
    nodes.sort((a, b) {
      if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return nodes;
  }

  @override
  Future<FileContent> fileContent(String path) async {
    try {
      final response = await sdkClient.getFileApi().fileRead(
        path: path,
        directory: _directory,
        workspace: _workspace,
      );
      final content = response.data;
      if (content == null) {
        throw ApiException('Read file failed: server returned no content');
      }
      return FileContent(
        content.content,
        type: content.type == sdk.FileContentTypeEnum.binary
            ? 'binary'
            : 'text',
        encoding: content.encoding == sdk.FileContentEncodingEnum.base64
            ? 'base64'
            : null,
        mimeType: content.mimeType,
      );
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Read file');
    } on DioException catch (error) {
      if (_wasSuccessfulResponse(error)) {
        return _fileContentFromLooseResponse(error.response?.data);
      }
      _fail(error, 'Read file');
    }
  }

  FileContent _fileContentFromLooseResponse(Object? raw) {
    if (raw is Map) {
      return FileContent.fromJson(Map<String, dynamic>.from(raw));
    }
    final text = raw?.toString() ?? '';
    try {
      final parsed = jsonDecode(text);
      if (parsed is Map &&
          (parsed['content'] is String || parsed['content'] is List)) {
        return FileContent.fromJson(Map<String, dynamic>.from(parsed));
      }
    } catch (_) {
      // A plain string is the legacy raw-file response, not malformed JSON.
    }
    return FileContent(text);
  }

  @override
  Future<List<String>> findFile(String query) async {
    try {
      final response = await sdkClient.getFileApi().findFiles(
        query: query,
        directory: _directory,
        workspace: _workspace,
        limit: 50,
      );
      return response.data ?? const [];
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Find files');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error) && raw is List) {
        return raw.map((value) => value.toString()).toList();
      }
      _fail(error, 'Find files');
    }
  }

  @override
  Future<List<FindMatch>> findText(String pattern) async {
    try {
      final response = await sdkClient.getFileApi().findText(
        pattern: pattern,
        directory: _directory,
        workspace: _workspace,
      );
      return (response.data ?? const [])
          .map(
            (match) => FindMatch(
              path: match.path.text,
              lineNumber: match.lineNumber,
              snippet: match.lines.text.trimRight(),
            ),
          )
          .toList();
    } on sdk.OpenCodeApiException catch (error) {
      _failGenerated(error, 'Find text');
    } on DioException catch (error) {
      final raw = error.response?.data;
      if (_wasSuccessfulResponse(error) && raw is List) {
        try {
          return raw
              .whereType<Map>()
              .map(
                (match) => FindMatch.fromJson(Map<String, dynamic>.from(match)),
              )
              .toList();
        } catch (_) {
          // Fall through to the product-facing transport error.
        }
      }
      _fail(error, 'Find text');
    }
  }
}

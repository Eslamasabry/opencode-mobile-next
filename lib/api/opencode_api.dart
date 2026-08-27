import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/opencode_sdk.dart' as sdk;

import 'models.dart';

/// HTTP client for a single opencode server (`opencode serve`).
class OpenCodeApi {
  final String baseUrl;
  final String? username;
  final String? password;
  late final Dio _dio;
  late final sdk.OpencodeSdk sdkClient;
  String? _directory;
  String? _workspace;
  bool _closed = false;

  Dio get dio => _dio;
  String? get directory => _directory;
  String? get workspace => _workspace;
  bool get isClosed => _closed;

  void setLocation({String? directory, String? workspace}) {
    _directory = directory;
    _workspace = workspace;
  }

  /// Cancels in-flight requests and closes the shared handwritten/SDK client.
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
    final body = responseData?.toString();
    final error = responseData is Map
        ? Map<String, dynamic>.from(responseData)
        : null;
    throw ApiException(
      '$what failed${code != null ? ' (HTTP $code)' : ''}${body != null && code != null ? ': $body' : ''}',
      statusCode: code,
      errorTag: error?['_tag']?.toString(),
      requestID: error?['requestID']?.toString(),
    );
  }

  /// Opens the long-lived `/event` SSE stream.
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

  Future<Health> health() async {
    try {
      final r = await _dio.get('/global/health');
      return Health.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Health check');
    }
  }

  // ----- Sessions -----

  Future<List<Session>> sessions() async {
    try {
      final r = await _dio.get('/session', queryParameters: _query());
      return (r.data as List)
          .whereType<Map<String, dynamic>>()
          .map(Session.fromJson)
          .toList();
    } on DioException catch (e) {
      _fail(e, 'List sessions');
    }
  }

  Future<Session> createSession() async {
    try {
      final r = await _dio.post(
        '/session',
        data: {},
        queryParameters: _query(),
      );
      return Session.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Create session');
    }
  }

  Future<void> deleteSession(String id) =>
      _dio.delete('/session/$id', queryParameters: _query());

  Future<void> renameSession(String id, String title) => _dio.patch(
    '/session/$id',
    data: {'title': title},
    queryParameters: _query(),
  );

  Future<Session> session(String id) async {
    try {
      final r = await _dio.get('/session/$id', queryParameters: _query());
      return Session.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Get session');
    }
  }

  /// Returns {sessionID: "idle"|"busy"|"retry"}.
  Future<Map<String, String>> sessionStatuses() async {
    final r = await _dio.get('/session/status', queryParameters: _query());
    final out = <String, String>{};
    final data = r.data;
    if (data is Map) {
      data.forEach((k, v) {
        out[k.toString()] = v is Map
            ? ((v['type'] ?? 'idle')).toString()
            : 'idle';
      });
    }
    return out;
  }

  Future<List<MessageWithParts>> messages(String id) async {
    final r = await _dio.get('/session/$id/message', queryParameters: _query());
    return (r.data as List).map(_bundleFromJson).toList();
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
  Future<void> promptAsync(
    String sessionID, {
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
  }) async {
    try {
      await _dio.post(
        '/session/$sessionID/prompt_async',
        data: promptRequestBody(
          text: text,
          model: model,
          agent: agent,
          variant: variant,
          attachments: attachments,
        ),
        queryParameters: _query(),
      );
    } on DioException catch (e) {
      _fail(e, 'Send prompt');
    }
  }

  Future<void> shell(
    String sessionID, {
    required String command,
    required String agent,
    ModelRef? model,
    String? variant,
  }) async {
    try {
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

  Future<void> slashCommand(
    String sessionID,
    String command,
    String args, {
    ModelRef? model,
    String? variant,
  }) async {
    try {
      await _dio.post(
        '/session/$sessionID/command',
        data: commandRequestBody(command, args, model: model, variant: variant),
        queryParameters: _query(),
      );
    } on DioException catch (e) {
      _fail(e, 'Run /command');
    }
  }

  Future<void> abort(String sessionID) =>
      _dio.post('/session/$sessionID/abort', queryParameters: _query());

  Future<void> initProject(
    String sessionID, {
    required String messageID,
    required ModelRef model,
  }) => _dio.post(
    '/session/$sessionID/init',
    data: {
      'messageID': messageID,
      'providerID': model.providerID,
      'modelID': model.modelID,
    },
    queryParameters: _query(),
  );

  Future<List<Todo>> todos(String id) async {
    final r = await _dio.get('/session/$id/todo', queryParameters: _query());
    return (r.data as List)
        .whereType<Map<String, dynamic>>()
        .map(Todo.fromJson)
        .toList();
  }

  Future<List<FileDiff>> diff(String id) async {
    final r = await _dio.get('/session/$id/diff', queryParameters: _query());
    return (r.data as List)
        .whereType<Map>()
        .map((value) => FileDiff.fromJson(Map<String, dynamic>.from(value)))
        .toList();
  }

  Future<List<PermissionRequest>> pendingPermissions() async {
    try {
      final response = await _dio.get('/permission', queryParameters: _query());
      return (response.data as List? ?? const [])
          .whereType<Map>()
          .map(
            (item) =>
                PermissionRequest.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList();
    } on DioException catch (error) {
      _fail(error, 'List pending permissions');
    }
  }

  /// Lists requests exposed by the V2 compatibility API.
  ///
  /// Unlike the location-scoped legacy endpoint, this endpoint wraps its
  /// result in `{location, data}` and uses the V2 permission field names.
  Future<List<PermissionRequest>> pendingPermissionsV2() async {
    try {
      final response = await _dio.get(
        '/api/permission/request',
        queryParameters: _v2LocationQuery(),
      );
      return _v2EnvelopeData(response.data, 'permission')
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
    } on DioException catch (error) {
      _fail(error, 'List V2 pending permissions');
    }
  }

  /// Returns raw V2 question objects from the global `{location, data}`
  /// envelope. The app's repository model owns question parsing.
  Future<List<Map<String, dynamic>>> pendingQuestionsV2() async {
    try {
      final response = await _dio.get(
        '/api/question/request',
        queryParameters: _v2LocationQuery(),
      );
      return _v2EnvelopeData(response.data, 'question');
    } on DioException catch (error) {
      _fail(error, 'List V2 pending questions');
    }
  }

  Map<String, dynamic> _v2LocationQuery() => {
    if (_directory != null) 'location[directory]': _directory,
    if (_workspace != null) 'location[workspace]': _workspace,
  };

  List<Map<String, dynamic>> _v2EnvelopeData(dynamic raw, String kind) {
    if (raw is! Map) {
      throw ApiException('Malformed V2 $kind request envelope');
    }
    final location = raw['location'];
    if (location is! Map) {
      throw ApiException('Malformed V2 $kind request location');
    }
    final responseLocation = Map<String, dynamic>.from(location);
    final locationMatches =
        (_directory == null ||
            responseLocation['directory']?.toString() == _directory) &&
        (_workspace == null ||
            responseLocation['workspace']?.toString() == _workspace);
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

  Future<void> respondPermission(
    String requestID,
    String reply, {
    String? legacySessionID,
    String? legacyPermissionID,
  }) async {
    if (reply != 'once' && reply != 'always' && reply != 'reject') {
      throw ArgumentError.value(reply, 'reply', 'must match the API contract');
    }
    final encodedID = Uri.encodeComponent(requestID);
    try {
      await _dio.post(
        '/permission/$encodedID/reply',
        data: {'reply': reply},
        queryParameters: _query(),
      );
    } on DioException catch (error) {
      final status = error.response?.statusCode;
      final responseData = error.response?.data;
      final errorBody = responseData is Map
          ? Map<String, dynamic>.from(responseData)
          : null;
      final permissionNotFoundError =
          status == 404 &&
          errorBody?['_tag']?.toString() == 'PermissionNotFoundError';
      final canUseLegacy =
          !permissionNotFoundError &&
          (status == 404 || status == 405) &&
          legacySessionID != null &&
          legacySessionID.isNotEmpty &&
          legacyPermissionID != null &&
          legacyPermissionID.isNotEmpty;
      if (!canUseLegacy) _fail(error, 'Reply to permission');

      final encodedSessionID = Uri.encodeComponent(legacySessionID);
      final encodedPermissionID = Uri.encodeComponent(legacyPermissionID);
      try {
        await _dio.post(
          '/session/$encodedSessionID/permissions/$encodedPermissionID',
          data: {'response': reply},
          queryParameters: _query(),
        );
      } on DioException catch (legacyError) {
        _fail(legacyError, 'Reply to permission');
      }
    }
  }

  Future<void> respondPermissionV2(
    String sessionID,
    String requestID,
    String reply,
  ) async {
    if (reply != 'once' && reply != 'always' && reply != 'reject') {
      throw ArgumentError.value(reply, 'reply', 'must match the API contract');
    }
    final encodedSessionID = Uri.encodeComponent(sessionID);
    final encodedRequestID = Uri.encodeComponent(requestID);
    try {
      await _dio.post(
        '/api/session/$encodedSessionID/permission/$encodedRequestID/reply',
        data: {'reply': reply},
      );
    } on DioException catch (error) {
      _fail(error, 'Reply to permission');
    }
  }

  Future<void> answerQuestionV2(
    String sessionID,
    String requestID,
    List<List<String>> answers,
  ) async {
    final encodedSessionID = Uri.encodeComponent(sessionID);
    final encodedRequestID = Uri.encodeComponent(requestID);
    try {
      await _dio.post(
        '/api/session/$encodedSessionID/question/$encodedRequestID/reply',
        data: {'answers': answers},
      );
    } on DioException catch (error) {
      _fail(error, 'Reply to question');
    }
  }

  Future<void> rejectQuestionV2(String sessionID, String requestID) async {
    final encodedSessionID = Uri.encodeComponent(sessionID);
    final encodedRequestID = Uri.encodeComponent(requestID);
    try {
      await _dio.post(
        '/api/session/$encodedSessionID/question/$encodedRequestID/reject',
      );
    } on DioException catch (error) {
      _fail(error, 'Reject question');
    }
  }

  // ----- Providers / agents -----

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

  Future<ProvidersResponse> configuredProviders() async {
    final r = await _dio.get('/config/providers', queryParameters: _query());
    return ProvidersResponse.fromJson(Map<String, dynamic>.from(r.data as Map));
  }

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

  Future<List<FileNode>> listFiles(String path) async {
    try {
      final r = await _dio.get(
        '/file',
        queryParameters: _query({'path': path}),
      );
      final nodes = (r.data as List)
          .whereType<Map<String, dynamic>>()
          .map(FileNode.fromJson)
          .toList();
      nodes.sort((a, b) {
        if (a.isDir != b.isDir) return a.isDir ? -1 : 1;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
      return nodes;
    } on DioException catch (e) {
      _fail(e, 'List files');
    }
  }

  Future<FileContent> fileContent(String path) async {
    try {
      final r = await _dio.get(
        '/file/content',
        queryParameters: _query({'path': path}),
        options: Options(responseType: ResponseType.plain),
      );
      final text = r.data?.toString() ?? '';
      Map<String, dynamic>? parsed;
      try {
        parsed = jsonDecode(text) as Map<String, dynamic>;
      } catch (_) {
        parsed = null;
      }
      if (parsed != null &&
          (parsed['content'] is String || parsed['content'] is List)) {
        return FileContent.fromJson(parsed);
      }
      // Server returned the raw file body
      return FileContent(text);
    } on DioException catch (e) {
      _fail(e, 'Read file');
    }
  }

  Future<List<String>> findFile(String query) async {
    final r = await _dio.get(
      '/find/file',
      queryParameters: _query({'query': query, 'limit': 50}),
    );
    return (r.data as List? ?? const []).map((e) => e.toString()).toList();
  }

  Future<List<FindMatch>> findText(String pattern) async {
    final r = await _dio.get(
      '/find',
      queryParameters: _query({'pattern': pattern}),
    );
    return (r.data as List)
        .whereType<Map<String, dynamic>>()
        .map(FindMatch.fromJson)
        .toList();
  }
}

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  final String? errorTag;
  final String? requestID;

  ApiException(this.message, {this.statusCode, this.errorTag, this.requestID});

  bool get unauthorized => statusCode == 401 || statusCode == 403;

  bool isPermissionNotFound(String id) =>
      statusCode == 404 &&
      errorTag == 'PermissionNotFoundError' &&
      requestID == id;

  bool isQuestionNotFound(String id) =>
      statusCode == 404 &&
      errorTag == 'QuestionNotFoundError' &&
      requestID == id;

  @override
  String toString() => message;
}

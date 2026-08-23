import 'dart:convert';

import 'package:dio/dio.dart';

import 'models.dart';

/// HTTP client for a single opencode server (`opencode serve`).
class OpenCodeApi {
  final String baseUrl;
  final String? username;
  final String? password;
  late final Dio _dio;

  static const connectTimeout = Duration(seconds: 8);
  static const requestTimeout = Duration(minutes: 10); // sync endpoints can run long

  OpenCodeApi({required this.baseUrl, this.username, this.password}) {
    final options = BaseOptions(
      baseUrl: baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl,
      connectTimeout: connectTimeout,
      receiveTimeout: requestTimeout,
      responseType: ResponseType.json,
      validateStatus: (s) => s != null && s >= 200 && s < 300,
    );
    _dio = Dio(options);
    if (password != null && password!.isNotEmpty) {
      final user = (username == null || username!.isEmpty) ? 'opencode' : username!;
      final token = base64Encode(utf8.encode('$user:$password'));
      _dio.options.headers['Authorization'] = 'Basic $token';
    }
  }

  Never _fail(DioException e, String what) {
    final code = e.response?.statusCode;
    final body = e.response?.data?.toString();
    throw ApiException(
        '$what failed${code != null ? ' (HTTP $code)' : ''}${body != null && code != null ? ': $body' : ''}',
        statusCode: code);
  }

  /// Opens the long-lived `/event` SSE stream.
  Future<Response<ResponseBody>> openEventStream({CancelToken? cancelToken}) {
    return _dio.get<ResponseBody>(
      '/event',
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
      final r = await _dio.get('/session');
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
      final r = await _dio.post('/session', data: {});
      return Session.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Create session');
    }
  }

  Future<void> deleteSession(String id) => _dio.delete('/session/$id');

  Future<void> renameSession(String id, String title) =>
      _dio.patch('/session/$id', data: {'title': title});

  Future<Session> session(String id) async {
    try {
      final r = await _dio.get('/session/$id');
      return Session.fromJson(Map<String, dynamic>.from(r.data as Map));
    } on DioException catch (e) {
      _fail(e, 'Get session');
    }
  }

  /// Returns {sessionID: "idle"|"busy"|"retry"}.
  Future<Map<String, String>> sessionStatuses() async {
    final r = await _dio.get('/session/status');
    final out = <String, String>{};
    final data = r.data;
    if (data is Map) {
      data.forEach((k, v) {
        out[k.toString()] =
            v is Map ? ((v['type'] ?? 'idle')).toString() : 'idle';
      });
    }
    return out;
  }

  Future<List<MessageWithParts>> messages(String id) async {
    final r = await _dio.get('/session/$id/message');
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
  }) async {
    try {
      await _dio.post('/session/$sessionID/prompt_async',
          data: promptRequestBody(text: text, model: model, agent: agent));
    } on DioException catch (e) {
      _fail(e, 'Send prompt');
    }
  }

  Future<void> shell(String sessionID,
      {required String command, required String agent, ModelRef? model}) async {
    try {
      await _dio.post('/session/$sessionID/shell',
          data: shellRequestBody(command, agent: agent, model: model));
    } on DioException catch (e) {
      _fail(e, 'Run command');
    }
  }

  Future<void> slashCommand(String sessionID, String command, String args,
      {ModelRef? model}) async {
    try {
      await _dio.post('/session/$sessionID/command',
          data: commandRequestBody(command, args, model: model));
    } on DioException catch (e) {
      _fail(e, 'Run /command');
    }
  }

  Future<void> abort(String sessionID) => _dio.post('/session/$sessionID/abort');

  Future<void> initProject(String sessionID,
      {required String messageID, required ModelRef model}) =>
      _dio.post('/session/$sessionID/init', data: {
        'messageID': messageID,
        'providerID': model.providerID,
        'modelID': model.modelID,
      });

  Future<List<Todo>> todos(String id) async {
    final r = await _dio.get('/session/$id/todo');
    return (r.data as List)
        .whereType<Map<String, dynamic>>()
        .map(Todo.fromJson)
        .toList();
  }

  Future<List<FileDiff>> diff(String id) async {
    final r = await _dio.get('/session/$id/diff');
    return (r.data as List)
        .whereType<Map<String, dynamic>>()
        .map(FileDiff.fromJson)
        .toList();
  }

  Future<void> respondPermission(String sessionID, String permissionID, String response) =>
      _dio.post('/session/$sessionID/permissions/$permissionID', data: {'response': response});

  // ----- Providers / agents -----

  Future<ProvidersResponse> providers() async {
    final r = await _dio.get('/config/providers');
    return ProvidersResponse.fromJson(Map<String, dynamic>.from(r.data as Map));
  }

  Future<List<AgentInfo>> agents() async {
    try {
      final r = await _dio.get('/agent');
      final list = (r.data as List)
          .whereType<Map<String, dynamic>>()
          .map(AgentInfo.fromJson)
          .toList();
      // Primary agents first; subagents are not useful as chat targets.
      list.sort((a, b) {
        int rank(AgentInfo x) => x.mode == null ? 0 : (x.mode == 'subagent' ? 2 : 1);
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
      final r = await _dio.get('/file', queryParameters: {'path': path});
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
      final r = await _dio.get('/file/content', queryParameters: {'path': path},
          options: Options(responseType: ResponseType.plain));
      final text = r.data?.toString() ?? '';
      Map<String, dynamic>? parsed;
      try {
        parsed = jsonDecode(text) as Map<String, dynamic>;
      } catch (_) {
        parsed = null;
      }
      if (parsed != null && (parsed['content'] is String || parsed['content'] is List)) {
        return FileContent.fromJson(parsed);
      }
      // Server returned the raw file body
      return FileContent(text);
    } on DioException catch (e) {
      _fail(e, 'Read file');
    }
  }

  Future<List<String>> findFile(String query) async {
    final r = await _dio.get('/find/file',
        queryParameters: {'query': query, 'limit': 50});
    return (r.data as List? ?? const []).map((e) => e.toString()).toList();
  }

  Future<List<FindMatch>> findText(String pattern) async {
    final r = await _dio.get('/find', queryParameters: {'pattern': pattern});
    return (r.data as List)
        .whereType<Map<String, dynamic>>()
        .map(FindMatch.fromJson)
        .toList();
  }
}

class ApiException implements Exception {
  final String message;
  final int? statusCode;
  ApiException(this.message, {this.statusCode});

  bool get unauthorized => statusCode == 401 || statusCode == 403;

  @override
  String toString() => message;
}

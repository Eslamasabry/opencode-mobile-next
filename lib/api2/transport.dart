import 'dart:convert';

import 'package:dio/dio.dart';

/// Transport layer for the OpenCode 2 server API (`/api/...`).
///
/// Owns base-URL normalization, HTTP Basic auth (username is always
/// `opencode`), the `?auth_token=` fallback for WebSocket/EventSource
/// contexts, timeouts, and typed mapping of the `_tag`-discriminated
/// error envelope.
class Api2Transport {
  /// Server root without a trailing slash or `/api` suffix.
  final String serverRoot;
  final String username;
  final String password;
  late final Dio _dio;
  bool _closed = false;

  static const connectTimeout = Duration(seconds: 8);
  static const requestTimeout = Duration(minutes: 2);
  static const longPollTimeout = Duration(minutes: 10);

  Dio get dio => _dio;
  bool get isClosed => _closed;
  String get apiBase => '$serverRoot/api';

  Api2Transport({
    required String baseUrl,
    required this.password,
    this.username = 'opencode',
  }) : serverRoot = normalizeServerRoot(baseUrl) {
    _dio = Dio(
      BaseOptions(
        baseUrl: '${normalizeServerRoot(baseUrl)}/api',
        connectTimeout: connectTimeout,
        receiveTimeout: requestTimeout,
        responseType: ResponseType.json,
        validateStatus: (s) => s != null && s >= 200 && s < 300,
      ),
    );
    if (password.isNotEmpty) {
      _dio.options.headers['Authorization'] = 'Basic $basicToken';
    }
  }

  /// Strips trailing slashes and a trailing `/api` path segment so both
  /// `http://host:4097` and `http://host:4097/api/` resolve identically.
  static String normalizeServerRoot(String url) {
    var root = url.trim();
    while (root.endsWith('/')) {
      root = root.substring(0, root.length - 1);
    }
    if (root.endsWith('/api')) {
      root = root.substring(0, root.length - 4);
    }
    return root;
  }

  /// `base64("opencode:<password>")` — the value of both the Basic header
  /// and the `?auth_token=` query fallback.
  String get basicToken => base64Encode(utf8.encode('$username:$password'));

  String get authorizationHeader => 'Basic $basicToken';

  /// Adds `?auth_token=` for contexts where headers are impossible
  /// (WebSocket upgrades, EventSource). Preserves existing query params.
  Uri withAuthToken(Uri uri) {
    final params = Map<String, dynamic>.from(uri.queryParametersAll);
    params['auth_token'] = basicToken;
    return uri.replace(queryParameters: params);
  }

  /// Absolute URI for an `/api`-relative path, with the auth token attached.
  Uri authTokenUri(String path, {Map<String, dynamic>? query}) {
    final base = Uri.parse('$apiBase$path');
    final params = <String, dynamic>{};
    query?.forEach((key, value) {
      if (value == null) return;
      params[key] = value is Iterable
          ? value.map((e) => e.toString()).toList()
          : value.toString();
    });
    return withAuthToken(params.isEmpty ? base : base.replace(queryParameters: params));
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _dio.close(force: true);
  }

  Future<dynamic> getJson(
    String path, {
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
    Duration? receiveTimeout,
  }) => _send(
    path,
    method: 'GET',
    query: query,
    cancelToken: cancelToken,
    receiveTimeout: receiveTimeout,
  );

  Future<dynamic> postJson(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
    Duration? receiveTimeout,
  }) => _send(
    path,
    method: 'POST',
    body: body,
    query: query,
    cancelToken: cancelToken,
    receiveTimeout: receiveTimeout,
  );

  Future<dynamic> putJson(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
  }) => _send(path, method: 'PUT', body: body, query: query);

  Future<dynamic> patchJson(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
  }) => _send(path, method: 'PATCH', body: body, query: query);

  Future<dynamic> deleteJson(
    String path, {
    Object? body,
    Map<String, dynamic>? query,
  }) => _send(path, method: 'DELETE', body: body, query: query);

  Future<dynamic> _send(
    String path, {
    required String method,
    Object? body,
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
    Duration? receiveTimeout,
  }) async {
    try {
      final response = await _dio.request<dynamic>(
        path,
        data: body,
        queryParameters: _cleanQuery(query),
        options: Options(
          method: method,
          receiveTimeout: receiveTimeout ?? requestTimeout,
        ),
        cancelToken: cancelToken,
      );
      return response.data;
    } on DioException catch (e) {
      throw mapError(e, '$method $path');
    }
  }

  /// Raw bytes (used by `/api/fs/read/...` which has no JSON envelope).
  Future<List<int>> getBytes(
    String path, {
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
  }) async {
    try {
      final response = await _dio.get<List<int>>(
        path,
        queryParameters: _cleanQuery(query),
        options: Options(responseType: ResponseType.bytes),
        cancelToken: cancelToken,
      );
      return response.data ?? const [];
    } on DioException catch (e) {
      throw mapError(e, 'GET $path');
    }
  }

  /// Opens a long-lived SSE response with no receive timeout.
  Future<Response<ResponseBody>> openStream(
    String path, {
    Map<String, dynamic>? query,
    CancelToken? cancelToken,
  }) async {
    try {
      return await _dio.get<ResponseBody>(
        path,
        queryParameters: _cleanQuery(query),
        options: Options(
          responseType: ResponseType.stream,
          headers: {'Accept': 'text/event-stream'},
          receiveTimeout: null,
        ),
        cancelToken: cancelToken,
      );
    } on DioException catch (e) {
      throw mapError(e, 'GET $path');
    }
  }

  /// Reads `/api/health`. 503 while the app layer boots surfaces as
  /// [Api2Unavailable]; a missing/denied password as [Api2AuthRequired].
  Future<Api2Health> health({CancelToken? cancelToken}) async {
    final json = await getJson('/health', cancelToken: cancelToken);
    return Api2Health.fromJson(json is Map<String, dynamic> ? json : const {});
  }

  /// Health probe that also reports whether the server speaks the v2 API
  /// this client targets (any `0.0.0-beta-*`/newer server that serves
  /// `/api/health` qualifies; the version string is surfaced for pinning).
  Future<Api2Health> checkServer({CancelToken? cancelToken}) async {
    final health = await this.health(cancelToken: cancelToken);
    if (!health.healthy) {
      throw Api2Unavailable(
        'Server at $serverRoot is not healthy'
        '${health.version != null ? ' (version ${health.version})' : ''}',
      );
    }
    return health;
  }

  Map<String, dynamic>? _cleanQuery(Map<String, dynamic>? query) {
    if (query == null) return null;
    final cleaned = <String, dynamic>{};
    query.forEach((key, value) {
      if (value != null) cleaned[key] = value;
    });
    return cleaned.isEmpty ? null : cleaned;
  }

  /// Maps a transport failure onto the typed [Api2Error] hierarchy using the
  /// `_tag`-discriminated error envelope. 401 is always [Api2AuthRequired]
  /// even when the body is empty (the Basic-auth gate sends none).
  static Api2Error mapError(DioException e, String what) {
    final status = e.response?.statusCode;
    final raw = e.response?.data;
    Map<String, dynamic>? body;
    if (raw is Map) {
      body = Map<String, dynamic>.from(raw);
    } else if (raw is String && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw);
        if (decoded is Map) body = Map<String, dynamic>.from(decoded);
      } catch (_) {}
    }
    final tag = body?['_tag']?.toString() ?? body?['code']?.toString();
    final message =
        body?['message']?.toString() ??
        (status != null ? 'HTTP $status' : e.message ?? 'network error');
    if (status == 401 || tag == 'UnauthorizedError') {
      return Api2AuthRequired('$what: authentication required', body: body);
    }
    if (status == null) {
      final timedOut =
          e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout ||
          e.type == DioExceptionType.sendTimeout;
      return Api2NetworkError(
        '$what failed: $message',
        timedOut: timedOut,
        cancelled: e.type == DioExceptionType.cancel,
      );
    }
    if (status == 503 ||
        tag == 'ServiceUnavailableError' ||
        tag == 'service_starting' ||
        tag == 'service_stopping' ||
        tag == 'service_failed') {
      return Api2Unavailable(
        '$what: $message',
        statusCode: status,
        tag: tag,
        body: body,
      );
    }
    return Api2RequestError(
      '$what failed (HTTP $status): $message',
      statusCode: status,
      tag: tag,
      body: body,
    );
  }
}

/// `GET /api/health` payload.
class Api2Health {
  final bool healthy;
  final String? version;
  final int? pid;
  Api2Health({required this.healthy, this.version, this.pid});

  factory Api2Health.fromJson(Map<String, dynamic> j) => Api2Health(
    healthy: j['healthy'] == true,
    version: j['version']?.toString(),
    pid: j['pid'] is num ? (j['pid'] as num).toInt() : null,
  );
}

/// Base class for every failure surfaced by [Api2Transport].
sealed class Api2Error implements Exception {
  final String message;
  final int? statusCode;
  final String? tag;
  final Map<String, dynamic>? body;
  const Api2Error(this.message, {this.statusCode, this.tag, this.body});

  bool get isNotFound => statusCode == 404;
  bool get isConflict => statusCode == 409;

  /// Extra field carried by the tagged error (e.g. `sessionID`, `field`).
  String? detail(String key) => body?[key]?.toString();

  @override
  String toString() => message;
}

/// 401 — bad or missing password. Distinct so callers can prompt for
/// credentials instead of retrying.
class Api2AuthRequired extends Api2Error {
  const Api2AuthRequired(super.message, {super.body}) : super(statusCode: 401, tag: 'UnauthorizedError');
}

/// Could not reach the server at all (DNS, refused, timeout, cancelled).
class Api2NetworkError extends Api2Error {
  final bool timedOut;
  final bool cancelled;
  const Api2NetworkError(super.message, {this.timedOut = false, this.cancelled = false});
}

/// 503 / boot-time `service_starting|stopping|failed` bodies.
class Api2Unavailable extends Api2Error {
  const Api2Unavailable(super.message, {super.statusCode, super.tag, super.body});
}

/// Any other non-2xx with an optional `_tag`-typed body.
class Api2RequestError extends Api2Error {
  const Api2RequestError(super.message, {super.statusCode, super.tag, super.body});
}

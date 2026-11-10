/// HTTP transport for orchestration hosts: Dio, problem+json, request ids.
///
/// Reads never carry a credential. The client strips `Authorization`,
/// `Cookie` and `Proxy-Authorization` from every request it sends so no
/// caller, interceptor or default can add one by accident; the only header
/// a write adds is `X-GC-Request`, the identity header the host front
/// requires on mutations (04-plugin-architecture §6).
library;

import 'dart:convert';

import 'package:dio/dio.dart';

import '../adapters/gascity/dto/json_read.dart';
import '../adapters/gascity/dto/problem.dart';
import '../../diagnostics/perf_trace.dart';

/// A non-2xx answer decoded as an RFC 9457 problem document. Non-problem
/// bodies (HTML, plain text) are wrapped in a synthetic [GcProblem] whose
/// `title` is the HTTP reason so callers always get one shape.
class OrchestrationHttpException implements Exception {
  const OrchestrationHttpException({
    required this.problem,
    required this.statusCode,
    required this.method,
    required this.path,
    this.requestId,
  });

  final GcProblem problem;
  final int? statusCode;
  final String method;
  final String path;

  /// `X-GC-Request-Id` of the failing response, for bug reports.
  final String? requestId;

  bool get isNotFound => statusCode == 404 || problem.isNotFound;

  /// Error slug (`city-not-found`, `forbidden`), when the host sent one.
  String? get code => problem.slug;

  @override
  String toString() =>
      'OrchestrationHttpException($method $path -> $statusCode '
      '${problem.slug ?? ''}: ${problem.message})';
}

/// The host could not be reached or did not answer in time: DNS, refused
/// connection, TLS failure, connect or receive timeout, cancelled request.
class OrchestrationTransportException implements Exception {
  const OrchestrationTransportException({
    required this.message,
    required this.method,
    required this.path,
    this.cause,
    this.timedOut = false,
  });

  final String message;
  final String method;
  final String path;
  final Object? cause;
  final bool timedOut;

  @override
  String toString() =>
      'OrchestrationTransportException($method $path: $message)';
}

/// Header names that must never leave the app on an orchestration read.
const credentialHeaders = {'authorization', 'cookie', 'proxy-authorization'};

/// Response header carrying the host's request id.
const requestIdHeader = 'X-GC-Request-Id';

/// Request header the host front requires on mutations.
const mutationRequestHeader = 'X-GC-Request';

/// Request header carrying the client idempotency key on mutations sent
/// through the host front (tool/host/cp_front).
const idempotencyKeyHeader = 'Idempotency-Key';

/// Response header the front sets when it answered a mutation from its
/// receipt store instead of calling the supervisor again.
const idempotentReplayedHeader = 'Idempotent-Replayed';

/// One HTTP answer with its status kept, for callers that read a body on
/// every status (the front answers a mutation with the supervisor's status
/// and a receipt body either way).
class OrchestrationHttpResponse {
  const OrchestrationHttpResponse({
    required this.statusCode,
    required this.body,
    this.requestId,
    this.replayed = false,
  });

  final int statusCode;

  /// The JSON object the host sent; a non-object or non-JSON body arrives
  /// under `items` / `value` / `text` so the shape is always a map.
  final Map<String, Object?> body;

  /// `X-GC-Request-Id`, when sent.
  final String? requestId;

  /// `Idempotent-Replayed: true` was present.
  final bool replayed;

  bool get isSuccess => statusCode >= 200 && statusCode < 300;
}

/// Dio-backed JSON client for one orchestration host and city.
///
/// Paths are host-relative (`/health`, `/v0/cities`); [cityPath] prefixes
/// a city-scoped tail (`/beads`) with `/v0/city/<city>`. Every request
/// carries [defaultQuery] (the fixture selects a scenario that way) and
/// the last `X-GC-Request-Id` seen is kept in [lastRequestId].
class OrchestrationHttpClient {
  OrchestrationHttpClient({
    required String baseUrl,
    required this.city,
    this.connectTimeout = const Duration(seconds: 8),
    this.receiveTimeout = const Duration(seconds: 30),
    Map<String, String> defaultQuery = const {},
    void Function(RequestOptions options)? onRequest,
    Dio? dio,
  }) : baseUrl = normalizeBaseUrl(baseUrl),
       defaultQuery = Map.unmodifiable(defaultQuery),
       _onRequest = onRequest {
    _dio =
        dio ??
        Dio(
          BaseOptions(
            baseUrl: this.baseUrl,
            connectTimeout: connectTimeout,
            receiveTimeout: receiveTimeout,
            responseType: ResponseType.json,
            headers: const {'Accept': 'application/json'},
            // Every status is delivered to us so problem+json bodies can be
            // decoded instead of thrown away by Dio.
            validateStatus: (_) => true,
          ),
        );
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          options.headers.removeWhere(
            (key, _) => credentialHeaders.contains(key.toLowerCase()),
          );
          _onRequest?.call(options);
          handler.next(options);
        },
      ),
    );
    PerfTraceInterceptor.attach(_dio, 'gascity');
  }

  /// Host root without a trailing slash (`http://127.0.0.1:8372`).
  final String baseUrl;

  /// City name every city-scoped read is addressed to.
  final String city;
  final Duration connectTimeout;
  final Duration receiveTimeout;

  /// Query parameters added to every request.
  final Map<String, String> defaultQuery;
  final void Function(RequestOptions options)? _onRequest;
  late final Dio _dio;
  bool _closed = false;
  String? _lastRequestId;

  /// `X-GC-Request-Id` of the most recent response, when the host sent one.
  String? get lastRequestId => _lastRequestId;
  bool get isClosed => _closed;

  /// `/v0/city/<city>` with the name percent-encoded.
  String get cityPath => '/v0/city/${Uri.encodeComponent(city)}';

  /// The underlying Dio, for the SSE client.
  Dio get dio => _dio;

  /// Strips whitespace and trailing slashes.
  static String normalizeBaseUrl(String url) {
    var root = url.trim();
    while (root.endsWith('/')) {
      root = root.substring(0, root.length - 1);
    }
    return root;
  }

  void close() {
    if (_closed) return;
    _closed = true;
    _dio.close(force: true);
  }

  /// `GET path` decoded as a JSON object. A JSON array or scalar body
  /// comes back under the key `items` / `value` so callers always receive
  /// a map; a non-2xx answer throws [OrchestrationHttpException].
  Future<Map<String, Object?>> getJson(
    String path, {
    Map<String, Object?>? query,
    CancelToken? cancelToken,
  }) async {
    final response = await _send<Object?>(
      'GET',
      path,
      query: query,
      cancelToken: cancelToken,
    );
    return _decodeBody('GET', path, response);
  }

  /// `GET <cityPath><tail>`: the common case for every city read.
  Future<Map<String, Object?>> getCity(
    String tail, {
    Map<String, Object?>? query,
    CancelToken? cancelToken,
  }) => getJson('$cityPath$tail', query: query, cancelToken: cancelToken);

  /// `POST path` with a JSON body and the `X-GC-Request` identity header
  /// set to [requestId]. Write path only; reads never use it.
  Future<Map<String, Object?>> postJson(
    String path,
    Map<String, Object?> body, {
    required String requestId,
    Map<String, Object?>? query,
    CancelToken? cancelToken,
  }) async {
    final response = await _send<Object?>(
      'POST',
      path,
      query: query,
      body: body,
      headers: {mutationRequestHeader: requestId},
      cancelToken: cancelToken,
    );
    return _decodeBody('POST', path, response);
  }

  /// `POST path` with a JSON [body] (or none) and the mutation headers:
  /// `X-GC-Request` set to [requestId] and, when given, `Idempotency-Key`
  /// set to [idempotencyKey]. Unlike [postJson] every status is returned
  /// rather than thrown so the caller can read the front's receipt on a
  /// rejected write; only transport failures throw.
  Future<OrchestrationHttpResponse> postForReceipt(
    String path, {
    String method = 'POST',
    required String requestId,
    String? idempotencyKey,
    Map<String, Object?>? body,
    Map<String, Object?>? query,
    CancelToken? cancelToken,
  }) async {
    final response = await _send<Object?>(
      method,
      path,
      query: query,
      body: body,
      headers: {
        mutationRequestHeader: requestId,
        idempotencyKeyHeader: ?idempotencyKey,
      },
      cancelToken: cancelToken,
    );
    final data = response.data;
    Map<String, Object?> decoded;
    if (data is Map) {
      decoded = readMap(data);
    } else if (data is List) {
      decoded = {'items': data};
    } else if (data is String && data.trim().isNotEmpty) {
      final json = _tryJson(data);
      decoded = json is Map
          ? readMap(json)
          : json is List
          ? {'items': json}
          : {'text': data};
    } else {
      decoded = const {};
    }
    final replayed = response.headers
        .value(idempotentReplayedHeader.toLowerCase())
        ?.toLowerCase();
    return OrchestrationHttpResponse(
      statusCode: response.statusCode ?? 0,
      body: decoded,
      requestId: _requestIdOf(response),
      replayed: replayed == 'true' || replayed == '1',
    );
  }

  /// Opens `GET path` as a byte stream (`Accept: text/event-stream`).
  /// [headers] carries `Last-Event-ID` on resume; [receiveTimeout] bounds
  /// the silence between chunks (null: no limit). A non-2xx answer is read
  /// fully and thrown as [OrchestrationHttpException].
  Future<Response<ResponseBody>> openStream(
    String path, {
    Map<String, Object?>? query,
    Map<String, String> headers = const {},
    Duration? receiveTimeout,
    CancelToken? cancelToken,
  }) async {
    final response = await _send<ResponseBody>(
      'GET',
      path,
      query: query,
      headers: {'Accept': 'text/event-stream', ...headers},
      responseType: ResponseType.stream,
      receiveTimeout: receiveTimeout ?? Duration.zero,
      cancelToken: cancelToken,
    );
    final status = response.statusCode ?? 0;
    if (status >= 200 && status < 300) return response;
    final body = response.data;
    var text = '';
    if (body != null) {
      final bytes = await body.stream.fold<List<int>>(
        <int>[],
        (acc, chunk) => acc..addAll(chunk),
      );
      text = utf8.decode(bytes, allowMalformed: true);
    }
    throw OrchestrationHttpException(
      problem: _problemFromText(text, status, response.statusMessage),
      statusCode: status,
      method: 'GET',
      path: path,
      requestId: _requestIdOf(response),
    );
  }

  Future<Response<T>> _send<T>(
    String method,
    String path, {
    Map<String, Object?>? query,
    Object? body,
    Map<String, String> headers = const {},
    ResponseType? responseType,
    Duration? receiveTimeout,
    CancelToken? cancelToken,
  }) async {
    if (_closed) {
      throw OrchestrationTransportException(
        message: 'client closed',
        method: method,
        path: path,
      );
    }
    final merged = <String, Object?>{
      ...defaultQuery,
      if (query != null)
        for (final entry in query.entries)
          if (entry.value != null) entry.key: entry.value,
    };
    try {
      final response = await _dio.request<T>(
        path,
        data: body,
        queryParameters: merged.isEmpty ? null : merged,
        cancelToken: cancelToken,
        options: Options(
          method: method,
          headers: headers.isEmpty ? null : headers,
          responseType: responseType,
          receiveTimeout: receiveTimeout,
          contentType: body == null ? null : Headers.jsonContentType,
        ),
      );
      final id = _requestIdOf(response);
      if (id != null) _lastRequestId = id;
      return response;
    } on DioException catch (e) {
      final response = e.response;
      if (response != null && e.type == DioExceptionType.badResponse) {
        throw OrchestrationHttpException(
          problem: _problemFromBody(
            response.data,
            response.statusCode,
            response.statusMessage,
          ),
          statusCode: response.statusCode,
          method: method,
          path: path,
          requestId: _requestIdOf(response),
        );
      }
      final timedOut =
          e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout ||
          e.type == DioExceptionType.sendTimeout;
      throw OrchestrationTransportException(
        message: e.message ?? e.type.name,
        method: method,
        path: path,
        cause: e.error ?? e,
        timedOut: timedOut,
      );
    }
  }

  Map<String, Object?> _decodeBody(
    String method,
    String path,
    Response<Object?> response,
  ) {
    final status = response.statusCode ?? 0;
    final data = response.data;
    if (status >= 200 && status < 300) {
      if (data is Map) return readMap(data);
      if (data is List) return {'items': data};
      if (data is String && data.trim().isNotEmpty) {
        final decoded = _tryJson(data);
        if (decoded is Map) return readMap(decoded);
        if (decoded is List) return {'items': decoded};
      }
      return {'value': data};
    }
    throw OrchestrationHttpException(
      problem: _problemFromBody(data, status, response.statusMessage),
      statusCode: status,
      method: method,
      path: path,
      requestId: _requestIdOf(response),
    );
  }

  static String? _requestIdOf(Response<Object?> response) =>
      response.headers.value(requestIdHeader.toLowerCase());

  static Object? _tryJson(String text) {
    try {
      return jsonDecode(text);
    } on FormatException {
      return null;
    }
  }

  static GcProblem _problemFromBody(Object? data, int? status, String? reason) {
    if (data is Map) {
      final map = readMap(data);
      if (GcProblem.looksLikeProblem(map)) return GcProblem.fromJson(map);
      return GcProblem(status: status, title: reason, raw: map);
    }
    if (data is String) return _problemFromText(data, status, reason);
    return GcProblem(status: status, title: reason ?? 'HTTP $status');
  }

  static GcProblem _problemFromText(String text, int? status, String? reason) {
    final decoded = _tryJson(text);
    if (decoded is Map) {
      final map = readMap(decoded);
      if (GcProblem.looksLikeProblem(map)) return GcProblem.fromJson(map);
      return GcProblem(status: status, title: reason, raw: map);
    }
    final trimmed = text.trim();
    return GcProblem(
      status: status,
      title: reason ?? 'HTTP $status',
      detail: trimmed.isEmpty || trimmed.startsWith('<')
          ? null
          : trimmed.length > 200
          ? trimmed.substring(0, 200)
          : trimmed,
    );
  }
}

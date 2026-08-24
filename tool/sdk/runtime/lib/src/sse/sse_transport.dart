import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:opencode_sdk/src/http/errors.dart';

/// One dispatched server-sent event.
class ServerSentEvent<T> {
  const ServerSentEvent({required this.data, this.id, this.event, this.retry});

  final T data;
  final String? id;
  final String? event;
  final Duration? retry;
}

/// Reconnection policy matching the upstream JavaScript SDK defaults.
class SseReconnectOptions {
  const SseReconnectOptions({
    this.maxReconnectAttempts,
    this.initialBackoff = const Duration(seconds: 3),
    this.maxBackoff = const Duration(seconds: 30),
  }) : assert(maxReconnectAttempts == null || maxReconnectAttempts >= 0);

  /// Maximum reconnects after the initial request, or `null` for unlimited.
  final int? maxReconnectAttempts;
  final Duration initialBackoff;
  final Duration maxBackoff;
}

typedef SseDataDecoder<T> = T Function(ServerSentEvent<String> event);

/// Low-level Dio transport used by the operation-specific SSE extensions.
class SseTransport {
  SseTransport(this._dio);

  final Dio _dio;

  Stream<ServerSentEvent<T>> connect<T>({
    required String path,
    required String operationId,
    required SseDataDecoder<T> decodeData,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    CancelToken? cancelToken,
    String? lastEventId,
    SseReconnectOptions reconnect = const SseReconnectOptions(),
  }) {
    if ((reconnect.maxReconnectAttempts ?? 0) < 0 ||
        reconnect.initialBackoff.isNegative ||
        reconnect.maxBackoff.isNegative) {
      throw ArgumentError.value(
        reconnect,
        'reconnect',
        'backoff durations must not be negative',
      );
    }
    late StreamController<ServerSentEvent<T>> controller;
    final requestCancellation = CancelToken();

    if (cancelToken != null) {
      if (cancelToken.isCancelled) {
        requestCancellation.cancel(cancelToken.cancelError?.error);
      } else {
        unawaited(
          cancelToken.whenCancel.then(
            (error) => requestCancellation.cancel(error.error),
          ),
        );
      }
    }

    Future<void> run() async {
      try {
        await _run(
          controller: controller,
          path: path,
          operationId: operationId,
          decodeData: decodeData,
          queryParameters: queryParameters,
          headers: headers,
          extra: extra,
          cancelToken: requestCancellation,
          lastEventId: lastEventId,
          reconnect: reconnect,
        );
      } catch (error, stackTrace) {
        if (!requestCancellation.isCancelled && !controller.isClosed) {
          controller.addError(error, stackTrace);
        }
      } finally {
        if (!controller.isClosed) await controller.close();
      }
    }

    controller = StreamController<ServerSentEvent<T>>(
      onListen: () => unawaited(run()),
      onCancel: () {
        if (!requestCancellation.isCancelled) {
          requestCancellation.cancel('SSE stream subscription cancelled');
        }
      },
    );
    return controller.stream;
  }

  Future<void> _run<T>({
    required StreamController<ServerSentEvent<T>> controller,
    required String path,
    required String operationId,
    required SseDataDecoder<T> decodeData,
    required CancelToken cancelToken,
    required SseReconnectOptions reconnect,
    Map<String, dynamic>? queryParameters,
    Map<String, dynamic>? headers,
    Map<String, dynamic>? extra,
    String? lastEventId,
  }) async {
    var reconnectAttempt = 0;
    var currentLastEventId = lastEventId;
    Duration? serverRetry;

    while (!cancelToken.isCancelled) {
      Object? connectionError;
      StackTrace? connectionStackTrace;
      var cleanEnd = false;

      try {
        final requestHeaders = <String, dynamic>{
          ...?headers,
          Headers.acceptHeader: 'text/event-stream',
          if (currentLastEventId != null) 'Last-Event-ID': currentLastEventId,
        };
        late Response<ResponseBody> response;
        try {
          response = await _dio.request<ResponseBody>(
            path,
            queryParameters: queryParameters,
            options: Options(
              method: 'GET',
              headers: requestHeaders,
              extra: <String, dynamic>{
                'secure': <Map<String, String>>[],
                ...?extra,
                'operationId': operationId,
              },
              responseType: ResponseType.stream,
              receiveTimeout: Duration.zero,
              validateStatus: (_) => true,
            ),
            cancelToken: cancelToken,
          );
        } on DioException catch (error) {
          rethrowOpenCodeApiException(error, operationId: operationId);
        }
        await _throwIfSseError(response, operationId: operationId);
        final body = response.data;
        if (body == null) {
          throw StateError('SSE response did not contain a response stream.');
        }

        await for (final frame in _parseSse(
          body.stream,
          initialLastEventId: currentLastEventId,
        )) {
          if (cancelToken.isCancelled) return;
          if (frame.id != null) currentLastEventId = frame.id;
          if (frame.retry != null) {
            serverRetry = frame.retry;
          }
          if (!frame.hasData) continue;

          T data;
          try {
            data = decodeData(
              ServerSentEvent<String>(
                data: frame.data,
                id: frame.id,
                event: frame.event,
                retry: frame.retry,
              ),
            );
          } catch (error, stackTrace) {
            Error.throwWithStackTrace(
              _SseDecodeError(error, stackTrace),
              stackTrace,
            );
          }
          controller.add(
            ServerSentEvent<T>(
              data: data,
              id: frame.id,
              event: frame.event,
              retry: frame.retry,
            ),
          );
        }
        cleanEnd = true;
      } on _SseDecodeError catch (error) {
        Error.throwWithStackTrace(error.error, error.stackTrace);
      } catch (error, stackTrace) {
        if (cancelToken.isCancelled) return;
        if (!_isRetryable(error)) rethrow;
        connectionError = error;
        connectionStackTrace = stackTrace;
      }

      if (cancelToken.isCancelled) return;
      if (cleanEnd) return;
      final maximum = reconnect.maxReconnectAttempts;
      if (maximum != null && reconnectAttempt >= maximum) {
        Error.throwWithStackTrace(connectionError!, connectionStackTrace!);
      }

      final delay = _backoff(
        reconnect,
        reconnectAttempt,
        initialBackoff: serverRetry,
      );
      reconnectAttempt++;
      if (delay > Duration.zero) {
        await _cancellableDelay(delay, cancelToken);
      }
    }
  }
}

Future<void> _throwIfSseError(
  Response<ResponseBody> response, {
  required String operationId,
}) async {
  final status = response.statusCode;
  if (status == null || (status >= 200 && status < 300)) return;
  final body = response.data;
  Object? payload;
  if (body != null) {
    final bytes = await body.stream.expand((chunk) => chunk).toList();
    final text = utf8.decode(bytes);
    final mediaType = response.headers
        .value(Headers.contentTypeHeader)
        ?.split(';')
        .first
        .trim()
        .toLowerCase();
    if (mediaType == Headers.jsonContentType ||
        mediaType?.endsWith('+json') == true) {
      try {
        payload = jsonDecode(text);
      } on FormatException {
        payload = text;
      }
    } else {
      payload = text;
    }
  }
  throwIfOpenCodeApiError(
    Response<Object?>(
      data: payload,
      headers: response.headers,
      isRedirect: response.isRedirect,
      requestOptions: response.requestOptions,
      redirects: response.redirects,
      statusCode: response.statusCode,
      statusMessage: response.statusMessage,
      extra: response.extra,
    ),
    operationId: operationId,
  );
}

Future<void> _cancellableDelay(Duration delay, CancelToken cancelToken) {
  final completer = Completer<void>();
  final timer = Timer(delay, completer.complete);
  unawaited(
    cancelToken.whenCancel.then((_) {
      timer.cancel();
      if (!completer.isCompleted) completer.complete();
    }),
  );
  return completer.future;
}

bool _isRetryable(Object error) {
  if (error is DioException && CancelToken.isCancel(error)) return false;
  return true;
}

Duration _backoff(
  SseReconnectOptions options,
  int attempt, {
  Duration? initialBackoff,
}) {
  final multiplier = 1 << attempt.clamp(0, 30);
  final microseconds =
      (initialBackoff ?? options.initialBackoff).inMicroseconds * multiplier;
  return Duration(
    microseconds: microseconds.clamp(0, options.maxBackoff.inMicroseconds),
  );
}

Stream<_SseFrame> _parseSse(
  Stream<List<int>> bytes, {
  String? initialLastEventId,
}) async* {
  var lastEventId = initialLastEventId;
  var event = '';
  var data = <String>[];
  Duration? retry;
  var hasFields = false;
  var firstLine = true;

  await for (var line
      in bytes
          .map<List<int>>((chunk) => chunk)
          .transform(utf8.decoder)
          .transform(const LineSplitter())) {
    if (firstLine) {
      firstLine = false;
      if (line.startsWith('\uFEFF')) line = line.substring(1);
    }
    if (line.isEmpty) {
      if (hasFields) {
        yield _SseFrame(
          data: data.join('\n'),
          hasData: data.isNotEmpty,
          id: lastEventId,
          event: event.isEmpty ? null : event,
          retry: retry,
        );
      }
      event = '';
      data = <String>[];
      retry = null;
      hasFields = false;
      continue;
    }
    if (line.startsWith(':')) continue;

    final separator = line.indexOf(':');
    final field = separator == -1 ? line : line.substring(0, separator);
    var value = separator == -1 ? '' : line.substring(separator + 1);
    if (value.startsWith(' ')) value = value.substring(1);

    switch (field) {
      case 'data':
        data.add(value);
        hasFields = true;
        break;
      case 'event':
        event = value;
        hasFields = true;
        break;
      case 'id':
        if (!value.contains('\u0000')) lastEventId = value;
        hasFields = true;
        break;
      case 'retry':
        if (RegExp(r'^\d+$').hasMatch(value)) {
          final milliseconds = int.tryParse(value);
          if (milliseconds != null) {
            retry = Duration(milliseconds: milliseconds);
            hasFields = true;
          }
        }
        break;
    }
  }
}

class _SseFrame {
  const _SseFrame({
    required this.data,
    required this.hasData,
    this.id,
    this.event,
    this.retry,
  });

  final String data;
  final bool hasData;
  final String? id;
  final String? event;
  final Duration? retry;
}

class _SseDecodeError implements Exception {
  const _SseDecodeError(this.error, this.stackTrace);

  final Object error;
  final StackTrace stackTrace;
}

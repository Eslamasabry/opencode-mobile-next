/// Browser side of `platform_dio.dart`: a Fetch + `AbortController` adapter
/// for streaming requests, layered over the default XMLHttpRequest adapter
/// for everything else.
///
/// Semantics kept from the native adapter:
/// - every `RequestOptions.headers` entry (including `Authorization`) is
///   sent as a request header; credentials never move into the URL;
/// - `connectTimeout` bounds the wait for response headers (the browser
///   does not expose the socket connect phase separately) and surfaces as
///   [DioExceptionType.connectionTimeout]; when it is unset,
///   `receiveTimeout` bounds the header wait as
///   [DioExceptionType.receiveTimeout], the way `IOHttpClientAdapter` bounds
///   `request.close()`;
/// - the per-chunk `receiveTimeout` is enforced by Dio itself on the returned
///   stream (`handleResponseStream`), exactly as on native;
/// - the cancel token, the consumer cancelling its subscription, Dio closing
///   the response and `close(force: true)` all abort the fetch, which also
///   releases the body reader;
/// - body chunks are delivered as they arrive, so the first SSE frame is
///   observable long before the server closes the response.
///
/// Browser failures carry no cause (CORS rejection, mixed content, DNS and
/// refused connections all surface as `TypeError: Failed to fetch`), so the
/// error reason states that honestly instead of guessing.
library;

import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'package:dio/browser.dart';
import 'package:dio/dio.dart';

import 'browser_bindings.dart' as js;
import 'platform_dio.dart' show platformDioStreamsRequest;
import 'transport_failure.dart' show browserNetworkFailureMessage;

const bool usesBrowserTransport = true;

void configurePlatformDio(Dio dio) {
  final current = dio.httpClientAdapter;
  if (current is BrowserStreamingHttpClientAdapter) return;
  dio.httpClientAdapter = BrowserStreamingHttpClientAdapter(fallback: current);
}

/// Why a fetch was aborted from the Dart side; decides the error mapping.
enum _AbortCause { none, cancelToken, consumer, closed, timeout }

/// Routes streaming requests through `fetch()` and everything else through
/// [fallback] (the adapter Dio created, normally [BrowserHttpClientAdapter]).
class BrowserStreamingHttpClientAdapter implements HttpClientAdapter {
  BrowserStreamingHttpClientAdapter({HttpClientAdapter? fallback})
    : _fallback =
          fallback ?? BrowserHttpClientAdapter(enableCORSWarning: false);

  final HttpClientAdapter _fallback;
  final _active = <_ActiveFetch>{};

  /// Adapter used for non-streaming requests.
  HttpClientAdapter get fallback => _fallback;

  /// Streaming fetches currently open (for tests and diagnostics).
  int get activeStreamCount => _active.length;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) {
    if (!platformDioStreamsRequest(options)) {
      return _fallback.fetch(options, requestStream, cancelFuture);
    }
    return _fetchStreaming(options, requestStream, cancelFuture);
  }

  Future<ResponseBody> _fetchStreaming(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final active = _ActiveFetch(options);
    _active.add(active);

    cancelFuture?.then((_) => active.abort(_AbortCause.cancelToken));

    JSAny? body;
    if (requestStream != null) {
      final builder = BytesBuilder(copy: false);
      await for (final chunk in requestStream) {
        builder.add(chunk);
      }
      body = builder.takeBytes().toJS;
    }

    final init = js.FetchRequestInit(
      method: options.method,
      headers: _requestHeaders(options),
      credentials: options.extra['withCredentials'] == true
          ? 'include'
          : 'same-origin',
      cache: 'no-store',
      redirect: 'follow',
      signal: active.controller.signal,
    );
    if (body != null) init.body = body;

    final connectTimeout = options.connectTimeout ?? Duration.zero;
    final receiveTimeout = options.receiveTimeout ?? Duration.zero;

    Future<js.FetchResponse> responseFuture;
    try {
      responseFuture = js.fetch(options.uri.toString().toJS, init).toDart;
    } catch (error) {
      _active.remove(active);
      throw DioException.connectionError(
        requestOptions: options,
        reason: browserNetworkFailureMessage(),
        error: error,
      );
    }
    if (connectTimeout > Duration.zero) {
      responseFuture = responseFuture.timeout(
        connectTimeout,
        onTimeout: () {
          active.abort(_AbortCause.timeout);
          throw DioException.connectionTimeout(
            requestOptions: options,
            timeout: connectTimeout,
          );
        },
      );
    } else if (receiveTimeout > Duration.zero) {
      responseFuture = responseFuture.timeout(
        receiveTimeout,
        onTimeout: () {
          active.abort(_AbortCause.timeout);
          throw DioException.receiveTimeout(
            requestOptions: options,
            timeout: receiveTimeout,
          );
        },
      );
    }

    final js.FetchResponse response;
    try {
      response = await responseFuture;
    } on DioException {
      _active.remove(active);
      rethrow;
    } catch (error) {
      _active.remove(active);
      throw _mapFailure(active, error);
    }

    final headers = _responseHeaders(response);
    final source = response.body;
    final reader = source?.getReader();
    late final StreamController<Uint8List> out;

    Future<void> pump() async {
      if (reader == null) {
        if (!out.isClosed) await out.close();
        _active.remove(active);
        return;
      }
      try {
        while (true) {
          final result = await reader.read().toDart;
          if (result.done) break;
          final value = result.value;
          if (value != null && value.isA<JSUint8Array>()) {
            if (out.isClosed || active.consumerCancelled) break;
            out.add(Uint8List.fromList((value as JSUint8Array).toDart));
          }
        }
        if (!out.isClosed) await out.close();
      } catch (error, stackTrace) {
        if (!out.isClosed) {
          if (!active.consumerCancelled) {
            out.addError(_mapFailure(active, error), stackTrace);
          }
          await out.close();
        }
      } finally {
        _active.remove(active);
      }
    }

    void release() {
      active.abort(_AbortCause.consumer);
      _cancelReader(reader);
      _active.remove(active);
    }

    out = StreamController<Uint8List>(
      onListen: () => unawaited(pump()),
      onCancel: release,
    );

    return ResponseBody(
      out.stream,
      response.status,
      statusMessage: response.statusText,
      isRedirect: response.redirected,
      headers: headers,
      onClose: release,
    );
  }

  static void _cancelReader(js.ReadableStreamDefaultReader? reader) {
    if (reader == null) return;
    try {
      unawaited(reader.cancel().toDart.then((_) {}, onError: (_) {}));
    } catch (_) {
      // Already released or aborted.
    }
  }

  static js.FetchHeaders _requestHeaders(RequestOptions options) {
    final headers = js.FetchHeaders();
    options.headers.forEach((key, value) {
      if (value == null) return;
      // The browser computes the body length itself.
      if (key.toLowerCase() == Headers.contentLengthHeader) return;
      final text = value is Iterable ? value.join(', ') : value.toString();
      headers.append(key, text);
    });
    return headers;
  }

  static Map<String, List<String>> _responseHeaders(js.FetchResponse response) {
    final headers = <String, List<String>>{};
    void collect(JSString value, JSString name) {
      // Fetch joins repeated headers with ", " already; keep one entry so
      // `Headers.value` sees the same shape the XHR adapter produced.
      (headers[name.toDart.toLowerCase()] ??= <String>[]).add(value.toDart);
    }

    response.headers.forEach(collect.toJS);
    return headers;
  }

  static DioException _mapFailure(_ActiveFetch active, Object error) {
    final options = active.options;
    switch (active.cause) {
      case _AbortCause.cancelToken:
        return DioException.requestCancelled(
          requestOptions: options,
          reason: 'The request was cancelled.',
        );
      case _AbortCause.consumer:
        return DioException.requestCancelled(
          requestOptions: options,
          reason: 'The response stream was closed by the app.',
        );
      case _AbortCause.closed:
        return DioException.connectionError(
          requestOptions: options,
          reason: 'The HTTP client was closed.',
        );
      case _AbortCause.timeout:
        final timeout = options.connectTimeout ?? Duration.zero;
        return timeout > Duration.zero
            ? DioException.connectionTimeout(
                requestOptions: options,
                timeout: timeout,
              )
            : DioException.receiveTimeout(
                requestOptions: options,
                timeout: options.receiveTimeout ?? Duration.zero,
              );
      case _AbortCause.none:
        return DioException.connectionError(
          requestOptions: options,
          reason: browserNetworkFailureMessage(),
          error: error,
        );
    }
  }

  @override
  void close({bool force = false}) {
    if (force) {
      for (final active in _active.toList()) {
        active.abort(_AbortCause.closed);
      }
    }
    _active.clear();
    _fallback.close(force: force);
  }
}

class _ActiveFetch {
  _ActiveFetch(this.options);

  final RequestOptions options;
  final controller = js.AbortController();
  _AbortCause cause = _AbortCause.none;

  bool get consumerCancelled => cause == _AbortCause.consumer;

  void abort(_AbortCause why) {
    if (cause != _AbortCause.none) return;
    cause = why;
    try {
      controller.abort();
    } catch (_) {
      // Aborting twice or after completion is harmless.
    }
  }
}

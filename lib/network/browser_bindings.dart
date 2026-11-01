/// Minimal `dart:js_interop` bindings for the browser APIs the web
/// transports use: `fetch` with `AbortController`, the response body
/// `ReadableStream`, and `WebSocket`.
///
/// Only the members the adapters call are bound. They mirror the shapes
/// `package:web` 1.1.1 declares (checked against the resolved copy in the
/// pub cache) so the app does not need a direct dependency on that package.
/// This library is imported only from the `*_web.dart` implementations,
/// which are compiled solely for browser targets.
@JS()
library;

import 'dart:js_interop';

/// `globalThis.fetch(input, init)`.
@JS('fetch')
external JSPromise<FetchResponse> fetch(JSString input, FetchRequestInit init);

/// The `init` dictionary of `fetch()`. An object literal.
extension type FetchRequestInit._(JSObject _) implements JSObject {
  external factory FetchRequestInit({
    String method,
    FetchHeaders headers,
    String credentials,
    String cache,
    String redirect,
    AbortSignal signal,
  });

  external set body(JSAny? value);
}

/// DOM `Headers`.
@JS('Headers')
extension type FetchHeaders._(JSObject _) implements JSObject {
  external factory FetchHeaders();

  external void append(String name, String value);

  /// Calls [callback] as `(value, name)` for every header.
  external void forEach(JSFunction callback);
}

/// DOM `Response` (only the parts the streaming adapter reads).
extension type FetchResponse._(JSObject _) implements JSObject {
  external int get status;
  external String get statusText;
  external bool get redirected;
  external FetchHeaders get headers;
  external ReadableStream? get body;
}

/// DOM `ReadableStream`.
extension type ReadableStream._(JSObject _) implements JSObject {
  /// Default-mode reader; the stream stays locked to it until released.
  external ReadableStreamDefaultReader getReader();
}

/// DOM `ReadableStreamDefaultReader`.
extension type ReadableStreamDefaultReader._(JSObject _) implements JSObject {
  external JSPromise<ReadableStreamReadResult> read();
  external JSPromise<JSAny?> cancel();
}

/// `{ done, value }` produced by `read()`.
extension type ReadableStreamReadResult._(JSObject _) implements JSObject {
  external bool get done;
  external JSAny? get value;
}

/// DOM `AbortController`.
@JS('AbortController')
extension type AbortController._(JSObject _) implements JSObject {
  external factory AbortController();
  external AbortSignal get signal;
  external void abort();
}

/// DOM `AbortSignal`; opaque here.
extension type AbortSignal._(JSObject _) implements JSObject {}

/// `name`/`message` of a JS `Error` or `DOMException`.
extension type JsErrorLike._(JSObject _) implements JSObject {
  external JSAny? get name;
  external JSAny? get message;
}

/// DOM `WebSocket`.
@JS('WebSocket')
extension type BrowserWebSocket._(JSObject _) implements JSObject {
  external factory BrowserWebSocket(String url);

  static const int connecting = 0;
  static const int open = 1;
  static const int closing = 2;
  static const int closed = 3;

  external set binaryType(String value);
  external int get readyState;
  external void send(JSAny data);
  external void close([int code, String reason]);
  external set onopen(JSFunction? handler);
  external set onmessage(JSFunction? handler);
  external set onclose(JSFunction? handler);
  external set onerror(JSFunction? handler);
}

/// DOM `Event`; opaque here.
extension type DomEvent._(JSObject _) implements JSObject {}

/// DOM `MessageEvent`.
extension type DomMessageEvent._(JSObject _) implements JSObject {
  external JSAny? get data;
}

/// DOM `CloseEvent`.
extension type DomCloseEvent._(JSObject _) implements JSObject {
  external int get code;
  external String get reason;
  external bool get wasClean;
}

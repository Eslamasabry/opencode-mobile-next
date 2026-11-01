/// Platform-conditional Dio configuration.
///
/// Native builds keep Dio's default `dart:io` adapter untouched. Browser
/// builds replace the default XMLHttpRequest adapter for streaming requests
/// only: the pinned `dio_web_adapter` reads every response as one
/// `arraybuffer` and completes on `onLoad`, so a `ResponseType.stream`
/// request (our SSE event streams) would deliver nothing until the server
/// closed the connection. Streaming requests go through the Fetch API with
/// an `AbortController` instead, which yields body chunks as they arrive
/// and honours Dio's cancel token, connect timeout and request headers.
///
/// Call [configurePlatformDio] once on every Dio a transport constructs.
/// Test adapters assigned afterwards still win, because the call replaces
/// the adapter only at the moment it runs.
library;

import 'package:dio/dio.dart';

import 'platform_dio_stub.dart'
    if (dart.library.io) 'platform_dio_io.dart'
    if (dart.library.js_interop) 'platform_dio_web.dart'
    as impl;

/// Installs the platform transport on [dio]. A no-op on native platforms;
/// wraps the browser adapter with a Fetch-based streaming path on the web.
void configurePlatformDio(Dio dio) => impl.configurePlatformDio(dio);

/// True when requests leave through a browser networking stack, where the
/// browser (not this app) enforces CORS and mixed-content policy and hides
/// the concrete network failure behind a generic error.
bool get platformDioUsesBrowserTransport => impl.usesBrowserTransport;

/// Whether a request needs the incremental (Fetch) path on the web: only
/// requests that consume the body as a stream. Everything else keeps the
/// default adapter so upload/download progress and existing behaviour stay
/// as they were. Pure, so the routing rule is testable off-browser.
bool platformDioStreamsRequest(RequestOptions options) =>
    options.responseType == ResponseType.stream;

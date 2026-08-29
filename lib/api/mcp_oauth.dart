import 'dart:async';
import 'dart:convert';
import 'dart:io';

class McpAuthLaunch {
  final Uri authorizationUrl;
  final String oauthState;

  const McpAuthLaunch({
    required this.authorizationUrl,
    required this.oauthState,
  });
}

class McpOAuthCallbackException implements Exception {
  final String message;

  const McpOAuthCallbackException(this.message);

  @override
  String toString() => message;
}

/// Returns the loopback redirect requested by the MCP authorization URL.
///
/// OpenCode's MCP OAuth flow normally asks the provider to return to an HTTP
/// loopback callback. When the browser runs on Android, that address belongs to
/// the phone rather than the remote OpenCode host, so mobile owns the listener
/// and forwards the validated code to OpenCode's generated callback endpoint.
Uri? mcpLoopbackRedirect(Uri authorizationUrl) {
  final value =
      authorizationUrl.queryParameters['redirect_uri'] ??
      authorizationUrl.queryParameters['redirectUri'];
  if (value == null || value.trim().isEmpty) return null;
  final redirect = Uri.tryParse(value.trim());
  if (redirect == null ||
      redirect.scheme.toLowerCase() != 'http' ||
      !_isLoopbackHost(redirect.host) ||
      redirect.userInfo.isNotEmpty ||
      !redirect.hasPort ||
      redirect.port <= 0 ||
      redirect.port > 65535) {
    return null;
  }
  return redirect;
}

String parseMcpAuthorizationCode(
  String value, {
  required String expectedState,
}) {
  final input = value.trim();
  if (input.isEmpty ||
      input.length > 8192 ||
      input.contains(RegExp(r'[\r\n]'))) {
    throw const McpOAuthCallbackException(
      'Paste the authorization code or the complete callback URL.',
    );
  }

  final uri = Uri.tryParse(input);
  final code = uri?.queryParameters['code'];
  if (uri != null && code != null) {
    final state = uri.queryParameters['state'];
    if (state == null || state != expectedState) {
      throw const McpOAuthCallbackException(
        'The callback state does not match this authorization request.',
      );
    }
    if (code.trim().isEmpty) {
      throw const McpOAuthCallbackException(
        'The callback URL does not contain an authorization code.',
      );
    }
    return code;
  }

  if (uri != null && uri.hasQuery) {
    final error =
        uri.queryParameters['error_description'] ??
        uri.queryParameters['error'];
    if (error?.trim().isNotEmpty == true) {
      throw McpOAuthCallbackException('Authorization failed: ${error!.trim()}');
    }
    throw const McpOAuthCallbackException(
      'The callback URL does not contain an authorization code.',
    );
  }
  return input;
}

class McpOAuthLoopbackListener {
  McpOAuthLoopbackListener._({
    required HttpServer server,
    required this.callbackUri,
    required this.expectedState,
    required Duration timeout,
  }) : _server = server {
    unawaited(
      _code.future.then<void>((_) {}, onError: (Object _, StackTrace _) {}),
    );
    _subscription = _server.listen(
      (request) => unawaited(_handle(request)),
      onError: (Object error, StackTrace stackTrace) {
        _completeError(
          McpOAuthCallbackException(
            'The local callback listener failed: $error',
          ),
          stackTrace,
        );
      },
      cancelOnError: false,
    );
    _timer = Timer(timeout, () {
      _completeError(
        const McpOAuthCallbackException(
          'Authorization timed out. Start the MCP sign-in again.',
        ),
      );
    });
  }

  final HttpServer _server;
  final Uri callbackUri;
  final String expectedState;
  final Completer<String> _code = Completer<String>();
  late final StreamSubscription<HttpRequest> _subscription;
  late final Timer _timer;
  bool _closed = false;

  Future<String> get code => _code.future;

  static Future<McpOAuthLoopbackListener> bind({
    required Uri redirect,
    required String expectedState,
    Duration timeout = const Duration(minutes: 10),
  }) async {
    if (redirect.scheme.toLowerCase() != 'http' ||
        !_isLoopbackHost(redirect.host) ||
        redirect.userInfo.isNotEmpty ||
        redirect.port < 0 ||
        redirect.port > 65535 ||
        expectedState.isEmpty) {
      throw const McpOAuthCallbackException(
        'OpenCode returned an unsupported callback address.',
      );
    }
    final normalized = redirect.path.isEmpty
        ? redirect.replace(path: '/')
        : redirect;
    final server = await HttpServer.bind(normalized.host, normalized.port);
    return McpOAuthLoopbackListener._(
      server: server,
      callbackUri: normalized.replace(port: server.port),
      expectedState: expectedState,
      timeout: timeout,
    );
  }

  Future<void> _handle(HttpRequest request) async {
    if (_closed) {
      await _respond(
        request,
        HttpStatus.gone,
        'Authorization is no longer active.',
      );
      return;
    }
    if (request.method != 'GET' || request.uri.path != callbackUri.path) {
      await _respond(request, HttpStatus.notFound, 'Unknown callback.');
      return;
    }

    final state = request.uri.queryParameters['state'];
    if (state == null || state != expectedState) {
      await _respond(
        request,
        HttpStatus.badRequest,
        'This callback does not match the active authorization request.',
      );
      return;
    }

    final error =
        request.uri.queryParameters['error_description'] ??
        request.uri.queryParameters['error'];
    if (error?.trim().isNotEmpty == true) {
      await _respond(
        request,
        HttpStatus.badRequest,
        'Authorization was not completed. Return to OpenCode Mobile.',
      );
      _completeError(
        McpOAuthCallbackException('Authorization failed: ${error!.trim()}'),
      );
      return;
    }

    final code = request.uri.queryParameters['code'];
    if (code == null || code.trim().isEmpty) {
      await _respond(
        request,
        HttpStatus.badRequest,
        'The callback did not include an authorization code.',
      );
      return;
    }

    await _respond(
      request,
      HttpStatus.ok,
      'Authorization received. You can return to OpenCode Mobile.',
    );
    if (!_code.isCompleted) _code.complete(code);
    await _shutdown();
  }

  Future<void> _respond(HttpRequest request, int status, String message) async {
    request.response
      ..statusCode = status
      ..headers.contentType = ContentType.html
      ..write(
        '<!doctype html><html><head><meta name="viewport" '
        'content="width=device-width,initial-scale=1"></head><body>'
        '<main><h1>OpenCode Mobile</h1><p>${htmlEscape.convert(message)}</p>'
        '</main></body></html>',
      );
    await request.response.close();
  }

  void _completeError(Object error, [StackTrace? stackTrace]) {
    if (!_code.isCompleted) {
      _code.completeError(error, stackTrace ?? StackTrace.current);
    }
    unawaited(_shutdown());
  }

  Future<void> close() async {
    if (!_code.isCompleted) {
      _code.completeError(
        const McpOAuthCallbackException('Authorization was cancelled.'),
      );
    }
    await _shutdown();
  }

  Future<void> _shutdown() async {
    if (_closed) return;
    _closed = true;
    _timer.cancel();
    await _subscription.cancel();
    await _server.close(force: true);
  }
}

bool _isLoopbackHost(String host) {
  final normalized = host.toLowerCase();
  return normalized == 'localhost' ||
      normalized == '127.0.0.1' ||
      normalized == '::1' ||
      normalized == '[::1]';
}

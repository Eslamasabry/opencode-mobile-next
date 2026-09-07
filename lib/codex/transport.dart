/// Experimental Codex 0.153.4 app-server transport; one RPC per text frame.
library;

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../api/models.dart';
import '../state/profiles.dart' show validateServerProfileUrl;

enum CodexFailureKind {
  invalidEndpoint,
  authentication,
  disconnected,
  invalidResponse,
  unavailable,
  overloaded,
  deliveryUnknown,
  staleRequest,
  scopeMismatch,
}

/// Safe fixed copy: remote error bodies and transport credentials never escape.
class CodexFailure extends ApiException {
  final CodexFailureKind kind;

  CodexFailure(this.kind)
    : super(
        switch (kind) {
          CodexFailureKind.invalidEndpoint => 'Use a secure Codex server address; plain WebSocket is limited to this device.',
          CodexFailureKind.authentication =>
            'Codex rejected the server connection token.',
          CodexFailureKind.disconnected => 'The Codex server disconnected.',
          CodexFailureKind.invalidResponse =>
            'The Codex server returned an unsupported response.',
          CodexFailureKind.unavailable =>
            'This action is unavailable for this Codex connection.',
          CodexFailureKind.overloaded =>
            'The Codex server is busy. Try again later.',
          CodexFailureKind.deliveryUnknown => 'Delivery is uncertain. Refresh the conversation before sending again.',
          CodexFailureKind.staleRequest =>
            'This request has changed. Refresh before replying.',
          CodexFailureKind.scopeMismatch =>
            'This conversation belongs to another project.',
        },
        statusCode: kind == CodexFailureKind.authentication ? 401 : 409,
        errorTag: 'Codex${kind.name}',
      );
}

Uri codexEndpoint(String raw) {
  final uri = Uri.tryParse(raw.trim());
  if (uri == null || !{'ws', 'wss'}.contains(uri.scheme)) {
    throw CodexFailure(CodexFailureKind.invalidEndpoint);
  }
  final httpUri = uri.replace(scheme: uri.scheme == 'wss' ? 'https' : 'http');
  if (validateServerProfileUrl(httpUri.toString()) != null) {
    throw CodexFailure(CodexFailureKind.invalidEndpoint);
  }
  return uri;
}

abstract interface class CodexSocket {
  Stream<Object?> get messages;
  void send(String message);
  Future<void> close();
}

typedef CodexSocketFactory = Future<CodexSocket> Function(
  Uri endpoint,
  String token,
);

class _IoCodexSocket implements CodexSocket {
  final WebSocket socket;
  final HttpClient client;
  _IoCodexSocket(this.socket, this.client);
  @override
  Stream<Object?> get messages => socket;
  @override
  void send(String message) => socket.add(message);
  @override
  Future<void> close() async {
    client.close(force: true);
    try {
      await socket.close().timeout(const Duration(seconds: 2));
    } catch (_) {
      // Closing cannot expose the server's close reason or another exception.
    }
  }
}

/// Manual upgrade disables redirects before the bearer header is attached.
/// Dart's WebSocket.connect currently leaves HttpClient redirects enabled.
Future<CodexSocket> connectCodexSocket(Uri endpoint, String token) async {
  codexEndpoint(endpoint.toString());
  if (token.isEmpty ||
      token.length > 16384 ||
      RegExp(r'[\x00-\x20\x7f]').hasMatch(token)) {
    throw CodexFailure(CodexFailureKind.authentication);
  }
  final client = HttpClient()..connectionTimeout = const Duration(seconds: 8);
  try {
    final http = endpoint.replace(
      scheme: endpoint.scheme == 'wss' ? 'https' : 'http',
    );
    final request = await client.getUrl(http);
    request.followRedirects = false;
    final random = Random.secure();
    final nonce = base64Encode(List.generate(16, (_) => random.nextInt(256)));
    request.headers
      ..set(HttpHeaders.authorizationHeader, 'Bearer $token')
      ..set(HttpHeaders.connectionHeader, 'Upgrade')
      ..set(HttpHeaders.upgradeHeader, 'websocket')
      ..set('Sec-WebSocket-Key', nonce)
      ..set('Sec-WebSocket-Version', '13');
    final response = await request.close().timeout(const Duration(seconds: 8));
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw CodexFailure(CodexFailureKind.authentication);
    }
    final accept = base64Encode(
      sha1
          .convert(
            utf8.encode(
              '$nonce'
              '258EAFA5-E914-47DA-95CA-C5AB0DC85B11',
            ),
          )
          .bytes,
    );
    if (response.statusCode != 101 ||
        response.headers.value('Sec-WebSocket-Accept') != accept ||
        response.headers.value(HttpHeaders.upgradeHeader)?.toLowerCase() !=
            'websocket' ||
        !(response.headers[HttpHeaders.connectionHeader] ?? const <String>[])
            .expand((value) => value.toLowerCase().split(','))
            .any((value) => value.trim() == 'upgrade') ||
        response.headers.value('Sec-WebSocket-Extensions') != null ||
        response.headers.value('Sec-WebSocket-Protocol') != null) {
      throw CodexFailure(CodexFailureKind.invalidResponse);
    }
    final socket = WebSocket.fromUpgradedSocket(
      await response.detachSocket(),
      serverSide: false,
      compression: CompressionOptions.compressionOff,
      maxPayloadLength: CodexTransport.maxFrameBytes,
    );
    socket.pingInterval = const Duration(seconds: 20);
    return _IoCodexSocket(socket, client);
  } catch (error) {
    client.close(force: true);
    if (error is CodexFailure) rethrow;
    throw CodexFailure(CodexFailureKind.disconnected);
  }
}

class CodexRpcEvent {
  final int epoch;
  final String method;
  final Map<String, dynamic> params;
  final Object? requestID;
  const CodexRpcEvent(this.epoch, this.method, this.params, this.requestID);
}

class _PendingRpc {
  final bool mutation;
  final Completer<Map<String, dynamic>> result = Completer();
  Timer? timer;
  _PendingRpc(this.mutation);
}

class CodexTransport {
  static const maxFrameBytes = 4 * 1024 * 1024;
  static const maxPending = 64;
  final Uri endpoint;
  String _token;
  final CodexSocketFactory socketFactory;
  final Duration requestTimeout;
  final _events = StreamController<CodexRpcEvent>.broadcast(sync: true);
  final _disconnects = StreamController<int>.broadcast(sync: true);
  final Map<int, _PendingRpc> _pending = {};
  CodexSocket? _socket;
  StreamSubscription<Object?>? _subscription;
  Future<int>? _connecting;
  int _epoch = 0;
  int _nextID = 0;
  bool _closed = false;
  bool _initialized = false;

  CodexTransport({
    required String endpoint,
    required String token,
    this.socketFactory = connectCodexSocket,
    this.requestTimeout = const Duration(seconds: 15),
  }) : endpoint = codexEndpoint(endpoint),
       _token = token;

  Stream<CodexRpcEvent> get events => _events.stream;
  Stream<int> get disconnects => _disconnects.stream;
  int get epoch => _epoch;
  bool get isClosed => _closed;
  bool get connected => _socket != null && _initialized;

  Future<void> connect() {
    return _connectReady().then<void>((_) {});
  }

  Future<int> _connectReady() {
    if (_closed) {
      return Future.error(CodexFailure(CodexFailureKind.disconnected));
    }
    if (connected) return Future.value(_epoch);
    return _connecting ??= _connect().whenComplete(() => _connecting = null);
  }

  Future<int> _connect() async {
    final epoch = ++_epoch;
    final socket = await socketFactory(endpoint, _token);
    if (_closed || epoch != _epoch) {
      await socket.close();
      throw CodexFailure(CodexFailureKind.disconnected);
    }
    _socket = socket;
    _subscription = socket.messages.listen(
      (frame) => _receive(epoch, frame),
      onError: (Object _) => _lost(epoch),
      onDone: () => _lost(epoch),
      cancelOnError: true,
    );
    try {
      final initialized = await _request(
        'initialize',
        {
          'clientInfo': {'name': 'opencode_mobile', 'version': '1.0.0'},
          'capabilities': {'experimentalApi': false},
        },
        mutation: false,
        epoch: epoch,
        requireInitialized: false,
      );
      final agent = initialized['userAgent'];
      accountApiSupported =
          agent is String && RegExp(r'(^|/)0\.153\.4(?:\s|$)').hasMatch(agent);
      _send({
        'method': 'initialized',
        'params': <String, dynamic>{},
      }, expectedEpoch: epoch);
      _initialized = true;
      return epoch;
    } catch (_) {
      _lost(epoch);
      rethrow;
    }
  }

  Future<Map<String, dynamic>> request(
    String method,
    Map<String, dynamic> params, {
    bool mutation = false,
  }) async {
    final epoch = await _connectReady();
    return _request(
      method,
      params,
      mutation: mutation,
      epoch: epoch,
      requireInitialized: true,
    );
  }

  /// A scope-owned mutation must not trigger or wait for a new connection.
  Future<Map<String, dynamic>> requestInEpoch(
    String method,
    Map<String, dynamic> params, {
    required int epoch,
    bool mutation = false,
  }) => _request(
    method,
    params,
    mutation: mutation,
    epoch: epoch,
    requireInitialized: true,
  );

  /// Account methods are enabled only for the schema verified by this client.
  bool accountApiSupported = false;

  Future<Map<String, dynamic>> _request(
    String method,
    Map<String, dynamic> params, {
    required bool mutation,
    required int epoch,
    required bool requireInitialized,
  }) {
    // A request that waited for an earlier connection must never be sent on a
    // replacement socket after that connection has been invalidated.
    if (_closed ||
        epoch != _epoch ||
        _socket == null ||
        (requireInitialized && !_initialized)) {
      return Future.error(
        CodexFailure(
          mutation
              ? CodexFailureKind.deliveryUnknown
              : CodexFailureKind.disconnected,
        ),
      );
    }
    if (_pending.length >= maxPending) {
      return Future.error(CodexFailure(CodexFailureKind.overloaded));
    }
    final id = ++_nextID;
    final pending = _PendingRpc(mutation);
    _pending[id] = pending;
    pending.timer = Timer(requestTimeout, () {
      if (_pending.remove(id) == null) return;
      pending.result.completeError(
        CodexFailure(
          mutation
              ? CodexFailureKind.deliveryUnknown
              : CodexFailureKind.disconnected,
        ),
      );
    });
    try {
      _send({
        'id': id,
        'method': method,
        'params': params,
      }, expectedEpoch: epoch);
    } on CodexFailure catch (error) {
      _pending.remove(id);
      pending.timer?.cancel();
      if (!pending.result.isCompleted) {
        pending.result.completeError(
          CodexFailure(
            mutation
                ? CodexFailureKind.deliveryUnknown
                : CodexFailureKind.disconnected,
          ),
        );
      }
      // Some socket implementations report a send failure without delivering
      // onDone. Treat that as a loss so the socket and every other pending
      // frame are invalidated immediately.
      if (error.kind != CodexFailureKind.invalidResponse) _lost(epoch);
    } catch (_) {
      _pending.remove(id);
      pending.timer?.cancel();
      if (!pending.result.isCompleted) {
        pending.result.completeError(
          CodexFailure(
            mutation
                ? CodexFailureKind.deliveryUnknown
                : CodexFailureKind.disconnected,
          ),
        );
      }
      _lost(epoch);
    }
    return pending.result.future;
  }

  void reply(CodexRpcEvent request, Map<String, dynamic> result) {
    if (!connected || request.epoch != _epoch || request.requestID == null) {
      throw CodexFailure(CodexFailureKind.staleRequest);
    }
    _send({
      'id': request.requestID,
      'result': result,
    }, expectedEpoch: request.epoch);
  }

  void rejectUnsupported(CodexRpcEvent request) {
    if (!connected || request.epoch != _epoch || request.requestID == null) {
      return;
    }
    _send({
      'id': request.requestID,
      'error': {'code': -32601, 'message': 'Unsupported client request'},
    });
  }

  void _send(Map<String, dynamic> value, {int? expectedEpoch}) {
    final socket = _socket;
    if (_closed ||
        socket == null ||
        (expectedEpoch != null && expectedEpoch != _epoch)) {
      throw CodexFailure(CodexFailureKind.disconnected);
    }
    final json = jsonEncode(value);
    if (utf8.encode(json).length > maxFrameBytes) {
      throw CodexFailure(CodexFailureKind.invalidResponse);
    }
    try {
      socket.send(json);
    } catch (_) {
      _lost(expectedEpoch ?? _epoch);
      rethrow;
    }
  }

  void _receive(int epoch, Object? frame) {
    if (epoch != _epoch || _closed) return;
    try {
      if (frame is! String ||
          frame.length > maxFrameBytes ||
          utf8.encode(frame).length > maxFrameBytes) {
        throw const FormatException();
      }
      final json = jsonDecode(frame);
      if (json is! Map<String, dynamic>) throw const FormatException();
      final id = json['id'];
      if (id != null && id is! String && id is! int) {
        throw const FormatException();
      }
      if (json['method'] case final String method) {
        final params = json['params'];
        if (method.isEmpty ||
            method.length > 200 ||
            params is! Map<String, dynamic>) {
          throw const FormatException();
        }
        _events.add(CodexRpcEvent(epoch, method, params, id));
        return;
      }
      if (id is! int) return;
      final pending = _pending.remove(id);
      if (pending == null) return;
      pending.timer?.cancel();
      final result = json['result'];
      final error = json['error'];
      if (error is Map) {
        pending.result.completeError(
          CodexFailure(
            error['code'] == -32001
                ? CodexFailureKind.overloaded
                : CodexFailureKind.unavailable,
          ),
        );
      } else if (result is Map<String, dynamic>) {
        pending.result.complete(result);
      } else {
        pending.result.completeError(
          CodexFailure(CodexFailureKind.invalidResponse),
        );
      }
    } catch (_) {
      _lost(epoch);
    }
  }

  void _lost(int epoch) {
    if (epoch != _epoch || _socket == null) return;
    final disconnectedEpoch = _epoch;
    // Invalidate callbacks and frames from the old socket before allowing a
    // future connect to allocate a replacement epoch.
    _epoch++;
    final socket = _socket!;
    _socket = null;
    _initialized = false;
    unawaited(_subscription?.cancel());
    _subscription = null;
    unawaited(socket.close());
    for (final pending in _pending.values) {
      pending.timer?.cancel();
      pending.result.completeError(
        CodexFailure(
          pending.mutation
              ? CodexFailureKind.deliveryUnknown
              : CodexFailureKind.disconnected,
        ),
      );
    }
    _pending.clear();
    if (!_closed) _disconnects.add(disconnectedEpoch);
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _token = '';
    _lost(_epoch);
    await _events.close();
    await _disconnects.close();
  }
}

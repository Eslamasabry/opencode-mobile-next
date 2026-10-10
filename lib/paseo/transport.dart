/// Experimental Paseo daemon transport (protocol 1, verified against 0.8.0).
///
/// One WebSocket at `/ws`, one JSON object per text frame. The client opens
/// with `hello`; the daemon answers with a `server_info` status. Every later
/// message travels inside `{"type":"session","message":{...}}`. Requests carry
/// a `requestId` and the matching response repeats it in its payload.
///
/// The daemon's relay and QR pairing are never used: the app reaches a daemon
/// on this device or over the user's own private network only.
library;

import 'dart:async';

import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:crypto/crypto.dart';

import '../api/models.dart';
import '../domain/loopback_host.dart';
import '../orchestration/adapters/gascity/gascity_probe.dart'
    show isTailnetHost;

enum PaseoFailureKind {
  invalidEndpoint,
  authentication,
  hostRefused,
  disconnected,
  invalidResponse,
  unavailable,
  newChatRequired,
  overloaded,
  deliveryUnknown,
  staleRequest,
  scopeMismatch,
}

/// Safe fixed copy: daemon error bodies and the password never escape.
class PaseoFailure extends ApiException {
  final PaseoFailureKind kind;

  PaseoFailure(this.kind)
    : super(
        switch (kind) {
          PaseoFailureKind.invalidEndpoint =>
            'Use this device, a Tailscale address, or your SSH tunnel.',
          PaseoFailureKind.authentication =>
            'The Paseo daemon rejected the password.',
          PaseoFailureKind.hostRefused =>
            'The Paseo daemon does not accept this host name. Add it to the '
                'daemon\'s hostnames, or connect by IP address.',
          PaseoFailureKind.disconnected => 'The Paseo daemon disconnected.',
          PaseoFailureKind.invalidResponse =>
            'The Paseo daemon returned an unsupported response.',
          PaseoFailureKind.newChatRequired =>
            "Can't reopen old chats. Starts a new chat.",
          PaseoFailureKind.unavailable =>
            'The Paseo daemon could not complete this action.',
          PaseoFailureKind.overloaded =>
            'The Paseo daemon is busy. Try again later.',
          PaseoFailureKind.deliveryUnknown =>
            'Delivery is uncertain. Refresh the conversation before sending again.',
          PaseoFailureKind.staleRequest =>
            'This request has changed. Refresh before replying.',
          PaseoFailureKind.scopeMismatch =>
            'This conversation belongs to another project.',
        },
        // Only a real clash reads as a conflict ("changed meanwhile"); a
        // daemon that cannot be reached or did not do it has no status, and
        // the app's failure words say so (ProductFailureCategory.computer).
        statusCode: switch (kind) {
          PaseoFailureKind.authentication => 401,
          PaseoFailureKind.overloaded => 429,
          PaseoFailureKind.staleRequest ||
          PaseoFailureKind.scopeMismatch ||
          PaseoFailureKind.deliveryUnknown ||
          PaseoFailureKind.newChatRequired => 409,
          _ => null,
        },
        errorTag: 'Paseo${kind.name}',
      );
}

/// Connections are accepted only where the network itself is private: this
/// device, or a Tailscale address (WireGuard already encrypts that hop).
bool isPaseoCleartextHost(String host) =>
    isLoopbackHost(host) || isTailnetHost(host);

Uri paseoEndpoint(String raw) {
  final uri = Uri.tryParse(raw.trim());
  if (uri == null ||
      !{'ws', 'wss'}.contains(uri.scheme) ||
      uri.host.isEmpty ||
      uri.userInfo.isNotEmpty ||
      uri.hasQuery ||
      uri.hasFragment ||
      (uri.path.isNotEmpty && uri.path != '/' && uri.path != '/ws')) {
    throw PaseoFailure(PaseoFailureKind.invalidEndpoint);
  }
  if (!isPaseoCleartextHost(uri.host)) {
    throw PaseoFailure(PaseoFailureKind.invalidEndpoint);
  }
  return uri.replace(path: '/ws');
}

abstract interface class PaseoSocket {
  Stream<Object?> get messages;

  /// The WebSocket close code once the daemon has closed, else null.
  int? get closeCode;
  void send(String message);
  Future<void> close();
}

typedef PaseoSocketFactory =
    Future<PaseoSocket> Function(Uri endpoint, String password);

class _IoPaseoSocket implements PaseoSocket {
  final WebSocket socket;
  final HttpClient client;
  _IoPaseoSocket(this.socket, this.client);
  @override
  Stream<Object?> get messages => socket;
  @override
  int? get closeCode => socket.closeCode;
  @override
  void send(String message) => socket.add(message);
  @override
  Future<void> close() async {
    client.close(force: true);
    try {
      await socket.close().timeout(const Duration(seconds: 2));
    } catch (_) {
      // Closing cannot expose the daemon's close reason or another exception.
    }
  }
}

/// Manual upgrade so redirects are off before the password is attached.
///
/// The daemon reads its optional shared secret from the
/// `Sec-WebSocket-Protocol: paseo.bearer.<password>` subprotocol, because a
/// browser WebSocket cannot set headers.
Future<PaseoSocket> connectPaseoSocket(Uri endpoint, String password) async {
  paseoEndpoint(endpoint.toString());
  if (password.length > 4096 ||
      RegExp(r'[\x00-\x20\x7f,]').hasMatch(password)) {
    throw PaseoFailure(PaseoFailureKind.authentication);
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
    final protocol = password.isEmpty ? null : 'paseo.bearer.$password';
    request.headers
      ..set(HttpHeaders.connectionHeader, 'Upgrade')
      ..set(HttpHeaders.upgradeHeader, 'websocket')
      ..set('Sec-WebSocket-Key', nonce)
      ..set('Sec-WebSocket-Version', '13');
    if (protocol != null) {
      request.headers
        ..set('Sec-WebSocket-Protocol', protocol)
        ..set(HttpHeaders.authorizationHeader, 'Bearer $password');
    }
    final response = await request.close().timeout(const Duration(seconds: 8));
    if (response.statusCode == 401) {
      throw PaseoFailure(PaseoFailureKind.authentication);
    }
    if (response.statusCode == 403) {
      // The daemon answers 403 both for a host outside its allowlist and, on
      // some paths, for a rejected secret. A password that was sent and
      // refused is the likelier cause; with none sent it is the host.
      throw PaseoFailure(
        password.isEmpty
            ? PaseoFailureKind.hostRefused
            : PaseoFailureKind.authentication,
      );
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
    final echoed = response.headers.value('Sec-WebSocket-Protocol');
    if (response.statusCode != 101 ||
        response.headers.value('Sec-WebSocket-Accept') != accept ||
        response.headers.value(HttpHeaders.upgradeHeader)?.toLowerCase() !=
            'websocket' ||
        !(response.headers[HttpHeaders.connectionHeader] ?? const <String>[])
            .expand((value) => value.toLowerCase().split(','))
            .any((value) => value.trim() == 'upgrade') ||
        response.headers.value('Sec-WebSocket-Extensions') != null ||
        (echoed != null && echoed != protocol)) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    final socket = WebSocket.fromUpgradedSocket(
      await response.detachSocket(),
      serverSide: false,
      compression: CompressionOptions.compressionOff,
      maxPayloadLength: PaseoTransport.maxFrameBytes,
    );
    socket.pingInterval = const Duration(seconds: 20);
    return _IoPaseoSocket(socket, client);
  } catch (error) {
    client.close(force: true);
    if (error is PaseoFailure) rethrow;
    throw PaseoFailure(PaseoFailureKind.disconnected);
  }
}

/// A message the daemon pushed without being asked.
class PaseoEvent {
  final int epoch;
  final String type;
  final Map<String, dynamic> payload;
  const PaseoEvent(this.epoch, this.type, this.payload);
}

/// One binary frame of a terminal stream: `[opcode, slot, ...payload]`. Only
/// terminal output is ever subscribed to, so nothing else arrives this way.
class PaseoBinaryFrame {
  final int epoch;
  final int opcode;
  final int slot;
  final List<int> payload;
  const PaseoBinaryFrame(this.epoch, this.opcode, this.slot, this.payload);
}

class _PendingRequest {
  final bool mutation;

  /// Progress messages the daemon sends for this request before its answer.
  final void Function(String type, Map<String, dynamic> payload)? onProgress;

  /// The answer is a report to read even when it carries an `error`.
  final bool answerErrors;
  final Completer<Map<String, dynamic>> result = Completer();
  Timer? timer;
  _PendingRequest(this.mutation, {this.onProgress, this.answerErrors = false});
}

class PaseoTransport {
  /// The daemon accepts the upgrade, then closes with this code when the
  /// password is missing or wrong.
  static const closeAuthFailed = 4401;
  static const maxFrameBytes = 8 * 1024 * 1024;
  static const maxPending = 64;

  /// Sent in `hello`. Below 0.1.45 the daemon hides every provider except
  /// claude, codex and opencode.
  static const clientAppVersion = '0.8.0';

  final Uri endpoint;
  String _password;
  final PaseoSocketFactory socketFactory;
  final Duration requestTimeout;
  final String clientId;
  final _events = StreamController<PaseoEvent>.broadcast(sync: true);
  final _disconnects = StreamController<int>.broadcast(sync: true);
  final _binary = StreamController<PaseoBinaryFrame>.broadcast(sync: true);
  final Map<String, _PendingRequest> _pending = {};
  PaseoSocket? _socket;
  StreamSubscription<Object?>? _subscription;
  Future<int>? _connecting;
  Completer<void>? _ready;
  Timer? _keepAlive;
  int _epoch = 0;
  int _nextID = 0;
  bool _closed = false;
  bool _initialized = false;
  int? _boundedFrameBytes;

  /// Daemon version from `server_info`, null before the first handshake.
  String? serverVersion;

  /// The daemon's own wording for the last refused request, clipped. Kept for
  /// diagnostics and tests only; user-facing copy stays the fixed
  /// [PaseoFailure] text.
  String? lastDaemonError;

  PaseoTransport({
    required String endpoint,
    String password = '',
    this.socketFactory = connectPaseoSocket,
    this.requestTimeout = const Duration(seconds: 20),
    String? clientId,
  }) : endpoint = paseoEndpoint(endpoint),
       _password = password,
       clientId = clientId ?? _newClientId();

  static String _newClientId() {
    final random = Random.secure();
    final hex = List.generate(
      16,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
    return 'cid_$hex';
  }

  Stream<PaseoEvent> get events => _events.stream;
  Stream<int> get disconnects => _disconnects.stream;

  /// Terminal stream frames, tagged with the connection they came on.
  Stream<PaseoBinaryFrame> get binaryFrames => _binary.stream;
  int get epoch => _epoch;
  bool get isClosed => _closed;
  bool get connected => _socket != null && _initialized;

  Future<void> connect() => _connectReady().then<void>((_) {});

  Future<int> _connectReady() {
    if (_closed) {
      return Future.error(PaseoFailure(PaseoFailureKind.disconnected));
    }
    if (connected) return Future.value(_epoch);
    return _connecting ??= _connect().whenComplete(() => _connecting = null);
  }

  Future<int> _connect() async {
    serverVersion = null;
    final epoch = ++_epoch;
    final socket = await socketFactory(endpoint, _password);
    if (_closed || epoch != _epoch) {
      await socket.close();
      throw PaseoFailure(PaseoFailureKind.disconnected);
    }
    _socket = socket;
    final ready = _ready = Completer<void>();
    _subscription = socket.messages.listen(
      (frame) => _receive(epoch, frame),
      onError: (Object _) => _lost(epoch),
      onDone: () => _lost(epoch),
      cancelOnError: true,
    );
    try {
      _sendRaw({
        'type': 'hello',
        'clientId': clientId,
        'clientType': 'mobile',
        'protocolVersion': 1,
        'appVersion': clientAppVersion,
        // Only what this client implements. Unclaimed capabilities keep the
        // daemon on its plain message shapes and on implicit delivery of
        // permission and timeline events, so no subscription bookkeeping is
        // needed here. Claude's sub-agents arrive the same implicit way
        // (`agent.provider_subagents.update`) with their projected
        // timelines.
        'capabilities': {
          'all_providers': true,
          'provider_subagents': true,
          'projected_subagent_timeline': true,
        },
      }, expectedEpoch: epoch);
      await ready.future.timeout(const Duration(seconds: 8));
      if (_closed || epoch != _epoch) {
        throw PaseoFailure(PaseoFailureKind.disconnected);
      }
      _initialized = true;
      _keepAlive = Timer.periodic(const Duration(seconds: 20), (_) {
        try {
          _sendRaw({'type': 'ping'}, expectedEpoch: epoch);
        } catch (_) {
          // _sendRaw already invalidated the socket.
        }
      });
      return epoch;
    } catch (error) {
      _lost(epoch);
      if (error is PaseoFailure) rethrow;
      throw PaseoFailure(PaseoFailureKind.disconnected);
    }
  }

  /// A read-only recovery request on a private connection. Its strict frame
  /// budget cannot discard unrelated responses from the live connection.
  Future<Map<String, dynamic>> requestBoundedHistory(
    Map<String, dynamic> body, {
    bool agentSnapshot = false,
  }) async {
    if (_closed) throw PaseoFailure(PaseoFailureKind.disconnected);
    final isolated = PaseoTransport(
      endpoint: endpoint.toString(),
      password: _password,
      socketFactory: socketFactory,
    ).._boundedFrameBytes = 1024 * 1024;
    try {
      return await (() async {
        await isolated.connect();
        if (!isolated.connected || isolated.serverVersion != '0.9.2') {
          throw PaseoFailure(PaseoFailureKind.unavailable);
        }
        return isolated.request(
          agentSnapshot
              ? 'fetch_agent_request'
              : 'fetch_agent_timeline_request',
          body,
          timeout: const Duration(seconds: 10),
          expectedEpoch: isolated.epoch,
        );
      })().timeout(const Duration(seconds: 10));
    } finally {
      await isolated.close();
    }
  }

  /// Sends a session request and completes with the response payload.
  Future<Map<String, dynamic>> request(
    String type,
    Map<String, dynamic> body, {
    bool mutation = false,
    Duration? timeout,
    int? expectedEpoch,
    void Function()? beforeSend,
    void Function(String type, Map<String, dynamic> payload)? onProgress,
    bool answerErrors = false,
  }) async {
    if (expectedEpoch != null && (!connected || expectedEpoch != _epoch)) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    final epoch = await _connectReady();
    if (expectedEpoch != null && epoch != expectedEpoch) {
      throw PaseoFailure(PaseoFailureKind.staleRequest);
    }
    if (_closed || epoch != _epoch || _socket == null || !_initialized) {
      throw PaseoFailure(
        mutation
            ? PaseoFailureKind.deliveryUnknown
            : PaseoFailureKind.disconnected,
      );
    }
    if (_boundedFrameBytes != null && serverVersion != '0.9.2') {
      throw PaseoFailure(PaseoFailureKind.unavailable);
    }
    if (_pending.length >= maxPending) {
      throw PaseoFailure(PaseoFailureKind.overloaded);
    }
    beforeSend?.call();
    final id = 'm${epoch}_${++_nextID}';
    final pending = _PendingRequest(
      mutation,
      onProgress: onProgress,
      answerErrors: answerErrors,
    );
    _pending[id] = pending;
    pending.timer = Timer(timeout ?? requestTimeout, () {
      if (_pending.remove(id) == null) return;
      pending.result.completeError(
        PaseoFailure(
          mutation
              ? PaseoFailureKind.deliveryUnknown
              : PaseoFailureKind.disconnected,
        ),
      );
    });
    try {
      _sendRaw({
        'type': 'session',
        'message': {...body, 'type': type, 'requestId': id},
      }, expectedEpoch: epoch);
    } catch (_) {
      if (_pending.remove(id) != null) {
        pending.timer?.cancel();
        pending.result.completeError(
          PaseoFailure(
            mutation
                ? PaseoFailureKind.deliveryUnknown
                : PaseoFailureKind.disconnected,
          ),
        );
      }
    }
    return pending.result.future;
  }

  /// Sends a session message that has no response (a permission answer).
  void send(String type, Map<String, dynamic> body, {int? expectedEpoch}) {
    if (!connected) throw PaseoFailure(PaseoFailureKind.staleRequest);
    _sendRaw({
      'type': 'session',
      'message': {...body, 'type': type},
    }, expectedEpoch: expectedEpoch ?? _epoch);
  }

  void _sendRaw(Map<String, dynamic> value, {required int expectedEpoch}) {
    final socket = _socket;
    if (_closed || socket == null || expectedEpoch != _epoch) {
      throw PaseoFailure(PaseoFailureKind.disconnected);
    }
    final json = jsonEncode(value);
    if (utf8.encode(json).length > maxFrameBytes) {
      throw PaseoFailure(PaseoFailureKind.invalidResponse);
    }
    try {
      socket.send(json);
    } catch (_) {
      _lost(expectedEpoch);
      throw PaseoFailure(PaseoFailureKind.disconnected);
    }
  }

  static bool _isResponseType(String type) =>
      type.endsWith('_response') || type.endsWith('.response');

  void _receive(int epoch, Object? frame) {
    if (epoch != _epoch || _closed) return;
    if (frame is! String) {
      // Binary frames carry terminal data, and only a terminal subscription
      // asks for any.
      if (frame is List<int> &&
          frame.length >= 2 &&
          frame.length <= maxFrameBytes) {
        _binary.add(
          PaseoBinaryFrame(epoch, frame[0], frame[1], frame.sublist(2)),
        );
      }
      return;
    }
    try {
      if (frame.length > maxFrameBytes) throw const FormatException();
      final cap = _boundedFrameBytes;
      if (cap != null &&
          (frame.length > cap || utf8.encode(frame).length > cap)) {
        throw const FormatException();
      }
      final json = jsonDecode(frame);
      if (json is! Map<String, dynamic>) throw const FormatException();
      final outer = json['type'];
      if (outer == 'pong' || outer == 'ping') return;
      if (outer != 'session') return;
      final message = json['message'];
      if (message is! Map<String, dynamic>) throw const FormatException();
      final type = message['type'];
      if (type is! String || type.isEmpty || type.length > 200) {
        throw const FormatException();
      }
      final rawPayload = message['payload'];
      final payload = rawPayload is Map<String, dynamic>
          ? rawPayload
          : const <String, dynamic>{};
      if (type == 'status' && payload['status'] == 'server_info') {
        final version = payload['version'];
        serverVersion =
            version is String &&
                version.length <= 64 &&
                RegExp(
                  r'^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$',
                ).hasMatch(version)
            ? version
            : null;
        final ready = _ready;
        if (ready != null && !ready.isCompleted) ready.complete();
        return;
      }
      final requestId = payload['requestId'];
      if (type == 'rpc_error') {
        final pending = requestId is String ? _pending.remove(requestId) : null;
        pending?.timer?.cancel();
        final error = payload['error'];
        if (error is String) {
          lastDaemonError = 'The agent request could not be completed.';
        }
        pending?.result.completeError(
          PaseoFailure(PaseoFailureKind.unavailable),
        );
        return;
      }
      // Most answers are `*_response` / `*.response`, but some actions answer
      // with the push they cause (`agent_archived`, `agent_deleted`). Request
      // ids are minted here and unique, so any payload repeating one settles
      // it; a push is still delivered as an event afterwards.
      // Progress is not the answer: it repeats the request id but leaves the
      // request waiting.
      if (type.endsWith('.progress') &&
          requestId is String &&
          _pending.containsKey(requestId)) {
        _pending[requestId]?.onProgress?.call(type, payload);
        return;
      }
      final pending = requestId is String ? _pending.remove(requestId) : null;
      if (pending != null) {
        pending.timer?.cancel();
        final error = payload['error'];
        if (!pending.answerErrors && error is String && error.isNotEmpty) {
          lastDaemonError = 'The agent request could not be completed.';
          pending.result.completeError(
            PaseoFailure(PaseoFailureKind.unavailable),
          );
        } else {
          pending.result.complete(payload);
        }
      }
      if (_isResponseType(type)) return;
      _events.add(PaseoEvent(epoch, type, payload));
    } catch (_) {
      _lost(epoch);
    }
  }

  void _lost(int epoch) {
    if (epoch != _epoch) return;
    serverVersion = null;
    if (_socket == null) return;
    final disconnectedEpoch = _epoch;
    // Invalidate callbacks and frames from the old socket before allowing a
    // future connect to allocate a replacement epoch.
    _epoch++;
    final socket = _socket!;
    _socket = null;
    _initialized = false;
    _keepAlive?.cancel();
    _keepAlive = null;
    final ready = _ready;
    _ready = null;
    if (ready != null && !ready.isCompleted) {
      ready.completeError(
        PaseoFailure(
          socket.closeCode == closeAuthFailed
              ? PaseoFailureKind.authentication
              : PaseoFailureKind.disconnected,
        ),
      );
    }
    unawaited(_subscription?.cancel());
    _subscription = null;
    unawaited(socket.close());
    final pending = _pending.values.toList();
    _pending.clear();
    for (final request in pending) {
      request.timer?.cancel();
      request.result.completeError(
        PaseoFailure(
          request.mutation
              ? PaseoFailureKind.deliveryUnknown
              : PaseoFailureKind.disconnected,
        ),
      );
    }
    if (!_closed) _disconnects.add(disconnectedEpoch);
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _password = '';
    _lost(_epoch);
    await _events.close();
    await _disconnects.close();
    await _binary.close();
  }
}

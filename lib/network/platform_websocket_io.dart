/// Native (`dart:io`) side of `platform_websocket.dart`.
library;

import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'platform_websocket_contract.dart';

Future<PlatformWebSocket> connect(Uri uri, {required Duration timeout}) async {
  final WebSocket socket;
  try {
    socket = await WebSocket.connect(uri.toString()).timeout(timeout);
  } on TimeoutException {
    throw PlatformWebSocketException(
      'Connecting to the terminal socket at ${describeSocketEndpoint(uri)} '
      'timed out after ${timeout.inSeconds}s.',
    );
  } on Object catch (error) {
    throw PlatformWebSocketException(
      'Could not open the terminal socket at ${describeSocketEndpoint(uri)}: '
      'the connection failed.',
      cause: error,
    );
  }
  return _IoPlatformWebSocket(socket);
}

class _IoPlatformWebSocket implements PlatformWebSocket {
  _IoPlatformWebSocket(this._socket) {
    _messages = StreamController<Object>(
      onListen: _subscribe,
      onPause: () => _subscription?.pause(),
      onResume: () => _subscription?.resume(),
    );
  }

  final WebSocket _socket;
  final _done = Completer<void>();
  late final StreamController<Object> _messages;
  StreamSubscription<dynamic>? _subscription;
  bool _closing = false;

  void _subscribe() {
    if (_subscription != null) return;
    _subscription = _socket.listen(
      (dynamic data) {
        if (_messages.isClosed) return;
        if (data is String) {
          _messages.add(data);
        } else if (data is Uint8List) {
          _messages.add(data);
        } else if (data is List<int>) {
          _messages.add(Uint8List.fromList(data));
        }
      },
      onError: (Object error, StackTrace stackTrace) {
        if (!_messages.isClosed) _messages.addError(error, stackTrace);
      },
      onDone: _finish,
      cancelOnError: false,
    );
  }

  void _finish() {
    if (!_done.isCompleted) _done.complete();
    if (!_messages.isClosed) unawaited(_messages.close());
  }

  @override
  Stream<Object> get messages => _messages.stream;

  @override
  bool get isOpen =>
      !_closing && !_done.isCompleted && _socket.readyState == WebSocket.open;

  @override
  void send(Object data) {
    if (data is String || data is List<int>) {
      _socket.add(data);
      return;
    }
    throw ArgumentError.value(data, 'data', 'Expected a String or List<int>');
  }

  @override
  Future<void> close({int? code, String? reason}) async {
    _closing = true;
    if (_subscription == null) _subscribe();
    if (_socket.readyState != WebSocket.closed) {
      try {
        // dart:io already force-closes the socket a few seconds after the
        // close frame when the peer stays silent; the outer bound makes
        // that guarantee explicit for callers.
        await _socket
            .close(code, reason)
            .timeout(platformWebSocketCloseBound, onTimeout: () {});
      } catch (_) {
        // Already closed by the peer.
      }
    }
    await done.timeout(platformWebSocketCloseBound, onTimeout: () {});
  }

  @override
  Future<void> get done => _done.future;

  @override
  int? get closeCode => _socket.closeCode;

  @override
  String? get closeReason => _socket.closeReason;
}

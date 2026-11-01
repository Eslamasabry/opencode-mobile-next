/// Browser side of `platform_websocket.dart`, over the DOM `WebSocket`.
///
/// The browser gives no reason when a socket fails to open (mixed content,
/// a rejected origin, an unreachable host and a bad ticket all end in a
/// close with code 1006), so failures say that instead of guessing.
library;

import 'dart:async';
import 'dart:js_interop';
import 'dart:typed_data';

import 'browser_bindings.dart' as js;
import 'platform_websocket_contract.dart';

Future<PlatformWebSocket> connect(Uri uri, {required Duration timeout}) {
  final endpoint = describeSocketEndpoint(uri);
  final js.BrowserWebSocket socket;
  try {
    socket = js.BrowserWebSocket(uri.toString());
  } catch (error) {
    // SyntaxError for a malformed URL; SecurityError when an https page
    // asks for ws://. Both are thrown synchronously by the constructor.
    return Future.error(
      PlatformWebSocketException(
        'The browser refused to open the terminal socket at $endpoint. '
        'A page loaded over https can only use wss:// servers.',
        cause: error,
      ),
    );
  }
  socket.binaryType = 'arraybuffer';
  final channel = _WebPlatformWebSocket(socket);
  final opened = Completer<PlatformWebSocket>();
  Timer? timer;

  void fail(PlatformWebSocketException error) {
    timer?.cancel();
    if (!opened.isCompleted) opened.completeError(error);
  }

  channel.onOpen = () {
    timer?.cancel();
    if (!opened.isCompleted) opened.complete(channel);
  };
  channel.onClosedBeforeOpen = (int code, String reason) {
    fail(
      PlatformWebSocketException(
        'The browser could not open the terminal socket at $endpoint '
        '(closed with code $code). '
        "The server may be unreachable, may not allow this page's origin, "
        'or the ticket may have expired.',
      ),
    );
  };
  timer = Timer(timeout, () {
    fail(
      PlatformWebSocketException(
        'Connecting to the terminal socket at $endpoint timed out after '
        '${timeout.inSeconds}s.',
      ),
    );
    unawaited(channel.close());
  });
  return opened.future;
}

class _WebPlatformWebSocket implements PlatformWebSocket {
  _WebPlatformWebSocket(this._socket) {
    _socket.onopen = _handleOpen.toJS;
    _socket.onmessage = _handleMessage.toJS;
    _socket.onclose = _handleClose.toJS;
    _socket.onerror = _handleError.toJS;
  }

  final js.BrowserWebSocket _socket;
  final _done = Completer<void>();
  final _messages = StreamController<Object>();
  bool _opened = false;
  int? _closeCode;
  String? _closeReason;

  void Function()? onOpen;
  void Function(int code, String reason)? onClosedBeforeOpen;

  void _handleOpen(js.DomEvent _) {
    _opened = true;
    onOpen?.call();
  }

  void _handleMessage(js.DomMessageEvent event) {
    if (_messages.isClosed) return;
    final data = event.data;
    if (data == null) return;
    if (data.isA<JSString>()) {
      _messages.add((data as JSString).toDart);
    } else if (data.isA<JSArrayBuffer>()) {
      final buffer = (data as JSArrayBuffer).toDart;
      _messages.add(Uint8List.fromList(buffer.asUint8List()));
    }
    // A Blob cannot arrive with binaryType 'arraybuffer'; anything else is
    // not a frame kind the PTY protocol defines.
  }

  void _handleError(js.DomEvent _) {
    // The DOM error event carries no detail; the close event that follows
    // reports the code. Nothing to surface here on its own.
  }

  void _handleClose(js.DomCloseEvent event) {
    _closeCode = event.code;
    _closeReason = event.reason;
    if (!_opened) onClosedBeforeOpen?.call(event.code, event.reason);
    if (_opened && !event.wasClean && !_messages.isClosed) {
      _messages.addError(
        PlatformWebSocketException(
          'The terminal socket closed unexpectedly (code ${event.code}).',
        ),
      );
    }
    _finish();
  }

  void _finish() {
    if (!_done.isCompleted) _done.complete();
    if (!_messages.isClosed) unawaited(_messages.close());
    _socket.onopen = null;
    _socket.onmessage = null;
    _socket.onclose = null;
    _socket.onerror = null;
  }

  @override
  Stream<Object> get messages => _messages.stream;

  @override
  bool get isOpen => _socket.readyState == js.BrowserWebSocket.open;

  @override
  void send(Object data) {
    if (data is String) {
      _socket.send(data.toJS);
      return;
    }
    if (data is List<int>) {
      final bytes = data is Uint8List ? data : Uint8List.fromList(data);
      _socket.send(bytes.toJS);
      return;
    }
    throw ArgumentError.value(data, 'data', 'Expected a String or List<int>');
  }

  @override
  Future<void> close({int? code, String? reason}) async {
    final state = _socket.readyState;
    if (state != js.BrowserWebSocket.closed &&
        state != js.BrowserWebSocket.closing) {
      try {
        // The DOM accepts only 1000 or 3000–4999 as an application code.
        if (code != null && (code == 1000 || (code >= 3000 && code <= 4999))) {
          _socket.close(code, reason ?? '');
        } else {
          _socket.close();
        }
      } catch (_) {
        // Already closing.
      }
    }
    await done.timeout(
      platformWebSocketCloseBound,
      onTimeout: () {
        // The close event never fired; release the listeners anyway so the
        // channel cannot leak or deliver late frames.
        _finish();
      },
    );
  }

  @override
  Future<void> get done => _done.future;

  @override
  int? get closeCode => _closeCode;

  @override
  String? get closeReason => _closeReason;
}

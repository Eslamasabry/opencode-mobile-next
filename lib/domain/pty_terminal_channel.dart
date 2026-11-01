import 'dart:convert';

import 'server_gateway.dart' show TerminalChannel;
import '../network/platform_websocket.dart';

/// PTY WebSocket channel the OpenCode 2 gateway uses over a platform socket
/// (the browser's, where `dart:io` sockets do not exist).
///
/// Server→client binary frames are raw terminal bytes, except a meta frame
/// starting with 0x00 carrying `{"cursor": n}`; text frames are terminal
/// text as well. Client→server frames are raw input text. [cursor] advances
/// by the byte length of every delivered chunk so a reconnect can resume
/// where the previous socket stopped.
class PtyTerminalChannel implements TerminalChannel {
  PtyTerminalChannel(this._socket, {required int initialCursor})
    : _cursor = initialCursor;

  final PlatformWebSocket _socket;
  int _cursor;
  late final Stream<String> _output = _socket.messages
      .expand(_decodeFrame)
      .asBroadcastStream();

  Iterable<String> _decodeFrame(Object data) sync* {
    if (data is List<int> && data.isNotEmpty && data.first == 0) {
      try {
        final metadata = jsonDecode(utf8.decode(data.sublist(1)));
        final next = metadata is Map<String, dynamic>
            ? metadata['cursor']
            : null;
        if (next is int && next >= 0) _cursor = next;
      } catch (_) {
        // Invalid control frames are transport metadata, never terminal text.
      }
      return;
    }
    final text = data is String
        ? data
        : data is List<int>
        ? utf8.decode(data, allowMalformed: true)
        : data.toString();
    if (text.isEmpty) return;
    _cursor += data is List<int> ? data.length : utf8.encode(text).length;
    yield text;
  }

  @override
  Stream<String> get output => _output;

  @override
  int get cursor => _cursor;

  @override
  void write(String value) => _socket.send(value);

  @override
  Future<void> close() => _socket.close();
}

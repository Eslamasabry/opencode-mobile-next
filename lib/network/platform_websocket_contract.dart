/// Platform-neutral WebSocket contract used by the PTY terminal channels.
///
/// The native implementation wraps `dart:io`'s `WebSocket`; the browser one
/// wraps the DOM `WebSocket`. Both deliver text frames as [String] and
/// binary frames as [Uint8List], accept the same two frame kinds on send,
/// and close within a bounded time even when the peer never acknowledges.
library;

import 'dart:typed_data';

/// One open WebSocket connection.
abstract class PlatformWebSocket {
  /// Incoming frames: a [String] for text frames, a [Uint8List] for binary
  /// frames. Single-subscription; ends when the connection closes. A
  /// connection that drops with an error reports it here before ending.
  Stream<Object> get messages;

  /// Sends a text frame for a [String] or a binary frame for a `List<int>`.
  /// Any other type is a programming error.
  void send(Object data);

  /// True while frames can still be sent.
  bool get isOpen;

  /// Closes the connection. Completes once the peer confirmed the close or
  /// after a bounded wait, whichever comes first. Safe to call repeatedly.
  Future<void> close({int? code, String? reason});

  /// Completes when the connection has closed for any reason.
  Future<void> get done;

  /// Close code reported by the peer or the platform, once closed.
  int? get closeCode;

  /// Close reason reported by the peer, once closed.
  String? get closeReason;
}

/// The connection could not be opened, or a platform refused the address.
/// The message never carries the URL: PTY socket URLs hold a short-lived
/// ticket that must not reach logs or screens.
class PlatformWebSocketException implements Exception {
  const PlatformWebSocketException(this.message, {this.cause});

  final String message;
  final Object? cause;

  @override
  String toString() => message;
}

/// Opens a WebSocket to [uri] (`ws://` or `wss://`), failing with a
/// [PlatformWebSocketException] once [timeout] elapses without the handshake
/// completing.
typedef PlatformWebSocketConnector =
    Future<PlatformWebSocket> Function(Uri uri, {Duration timeout});

/// Upper bound on the wait for a close handshake before
/// [PlatformWebSocket.close] gives up and completes anyway.
const platformWebSocketCloseBound = Duration(seconds: 5);

/// Default handshake bound for [PlatformWebSocketConnector].
const platformWebSocketConnectTimeout = Duration(seconds: 15);

/// `host:port` for messages, with the query (and any ticket in it) dropped.
String describeSocketEndpoint(Uri uri) =>
    uri.hasPort ? '${uri.host}:${uri.port}' : uri.host;

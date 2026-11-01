/// Platform-conditional WebSocket connector.
///
/// PTY terminals upgrade to a WebSocket carrying a single-use, 60-second
/// ticket in the query string. Native builds open it with `dart:io`;
/// browser builds with the DOM `WebSocket`, which has no header access and
/// so relies on that same ticket. The connection contract both sides honour
/// is [PlatformWebSocket].
library;

import 'platform_websocket_contract.dart';
import 'platform_websocket_stub.dart'
    if (dart.library.io) 'platform_websocket_io.dart'
    if (dart.library.js_interop) 'platform_websocket_web.dart'
    as impl;

export 'platform_websocket_contract.dart';

/// Opens a WebSocket with the platform's networking stack.
Future<PlatformWebSocket> connectPlatformWebSocket(
  Uri uri, {
  Duration timeout = platformWebSocketConnectTimeout,
}) => impl.connect(uri, timeout: timeout);

/// The connector the terminal gateways use. Production leaves this at
/// [connectPlatformWebSocket]; tests replace it with an in-memory socket
/// and restore it in tearDown, mirroring `serverProbe`.
PlatformWebSocketConnector platformWebSocketConnector =
    connectPlatformWebSocket;

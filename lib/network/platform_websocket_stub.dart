/// Fallback for platforms with neither `dart:io` nor a DOM: no WebSocket.
library;

import 'platform_websocket_contract.dart';

Future<PlatformWebSocket> connect(Uri uri, {required Duration timeout}) =>
    Future.error(
      const PlatformWebSocketException(
        'WebSocket connections are not supported on this platform.',
      ),
    );

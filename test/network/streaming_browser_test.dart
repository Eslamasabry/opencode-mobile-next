@TestOn('browser')
library;

import 'package:flutter_test/flutter_test.dart';

import 'support/streaming_contract.dart';

/// Runs the streaming contract in a browser against a server started
/// outside the page. Proves the Fetch adapter delivers the first SSE frame
/// before the server closes, that cancel/abort releases the connection,
/// that reconnect and `Last-Event-ID` resume work, and that the DOM
/// WebSocket carries both frame kinds. Start the server first:
///
/// ```bash
/// dart run test/network/support/streaming_test_server.dart
/// flutter test --platform chrome \
///   --dart-define=OC_STREAM_TEST_SERVER=http://127.0.0.1:<port> \
///   test/network/streaming_browser_test.dart
/// ```
const _server = String.fromEnvironment('OC_STREAM_TEST_SERVER');

void main() {
  if (_server.isEmpty) {
    test(
      'streaming contract in the browser',
      () {},
      skip:
          'Start `dart run test/network/support/streaming_test_server.dart` '
          'and rerun with --dart-define=OC_STREAM_TEST_SERVER=<url>.',
    );
    return;
  }
  final base = Uri.parse(_server);
  registerStreamingContractTests(() => base);
}

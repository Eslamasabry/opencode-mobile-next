@TestOn('vm')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'support/streaming_contract.dart';
import 'support/streaming_test_server.dart';

class _RealHttpOverrides extends HttpOverrides {}

/// Runs the streaming contract on the native `dart:io` transport against an
/// in-process server. Each case runs inside an `HttpOverrides` zone with a
/// real client so it stays correct even if a test binding has installed
/// flutter_test's request-failing mock.
void main() {
  late StreamingTestServer server;

  setUpAll(() async {
    server = await StreamingTestServer.start();
  });

  tearDownAll(() => server.close());

  registerStreamingContractTests(
    () => server.baseUri,
    run: (body) => HttpOverrides.runZoned(
      body,
      createHttpClient: (_) => _RealHttpOverrides().createHttpClient(null),
    ),
  );
}

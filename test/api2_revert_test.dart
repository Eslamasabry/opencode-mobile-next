import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';

/// Records what the gateway actually puts on the wire.
class _RecordingAdapter implements HttpClientAdapter {
  final requests = <({String path, Map<String, dynamic>? body})>[];

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final raw = options.data;
    requests.add((
      path: options.path,
      body: raw is Map<String, dynamic>
          ? raw
          : raw is String
          ? jsonDecode(raw) as Map<String, dynamic>
          : null,
    ));
    return ResponseBody.fromString('{}', 200, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late _RecordingAdapter adapter;
  late Api2OperationsGateway gateway;

  setUp(() {
    adapter = _RecordingAdapter();
    final client = Api2Client.connect(
      baseUrl: 'http://127.0.0.1:4097',
      password: 'test-password',
    );
    client.transport.dio.httpClientAdapter = adapter;
    gateway = Api2OperationsGateway(client: client);
  });

  test('reverting a session asks the server to restore the files', () async {
    await gateway.revertSession('ses_abc', 'msg_def');

    expect(adapter.requests, hasLength(1));
    final request = adapter.requests.single;
    expect(request.path, contains('/session/ses_abc/revert/stage'));
    expect(request.body?['messageID'], 'msg_def');

    // Without `files: true` the server only moves the session boundary: the
    // call succeeds, the app reports the revert worked, and every file the
    // agent changed is still changed. That shipped once.
    expect(
      request.body?['files'],
      isTrue,
      reason:
          'revert/stage must apply file changes, otherwise the user is told '
          'their code was restored while nothing was reverted',
    );
  });

  test('reverting does not commit, so it stays undoable', () async {
    await gateway.revertSession('ses_abc', 'msg_def');

    // /revert/commit makes a revert permanent. v1's contract keeps a revert
    // undoable through restoreSession, so committing here would quietly
    // remove the only way back.
    expect(
      adapter.requests.any((r) => r.path.contains('revert/commit')),
      isFalse,
    );
  });

  test('restoring a session clears the staged revert', () async {
    await gateway.restoreSession('ses_abc');

    expect(adapter.requests.single.path, contains('/session/ses_abc/revert/clear'));
  });
}

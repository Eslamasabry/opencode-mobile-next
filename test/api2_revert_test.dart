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
    final stage = options.path.endsWith('/stage');
    return ResponseBody.fromString(
      stage
          ? jsonEncode({
              'data': {
                'messageID': 'msg_def',
                'snapshot': 'snap_original',
                'files': [
                  {
                    'file': 'main.dart',
                    'before': 'new',
                    'after': 'old',
                    'patch': '@@ -1 +1 @@\n-new\n+old',
                  },
                ],
              },
            })
          : '',
      stage ? 200 : 204,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
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

    // Send an explicit preference. The pinned server applies files unless
    // this field is false; omission is not a history-only operation.
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

    expect(
      adapter.requests.single.path,
      contains('/session/ses_abc/revert/clear'),
    );
  });

  test(
    'stage returns its fixed preview and encodes history-only preference',
    () async {
      final staged = await gateway.stageSessionRevert(
        'ses_abc',
        'msg_def',
        applyFiles: false,
      );
      expect(adapter.requests.single.body?['files'], isFalse);
      expect(staged.messageID, 'msg_def');
      expect(staged.snapshot, 'snap_original');
      expect(staged.files!.single.file, 'main.dart');
      expect(staged.files!.single.patch, contains('-new'));
    },
  );

  test('commit and clear use distinct bodyless endpoints', () async {
    await gateway.commitSessionRevert('ses_abc');
    await gateway.clearSessionRevert('ses_abc');
    expect(adapter.requests.map((r) => r.path).toList(), [
      '/session/ses_abc/revert/commit',
      '/session/ses_abc/revert/clear',
    ]);
    expect(adapter.requests.every((r) => r.body == null), isTrue);
  });
}

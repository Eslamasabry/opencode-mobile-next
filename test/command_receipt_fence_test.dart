import 'dart:async';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';

class _Http implements HttpClientAdapter {
  int posts = 0;
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? body,
    Future<void>? cancel,
  ) async {
    posts++;
    return ResponseBody.fromString('', 204);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test(
    'v1 receipt fence runs after awaited preflight and settles refused dispatch',
    () async {
      final api = OpenCodeApi(baseUrl: 'http://127.0.0.1:4096');
      addTearDown(api.close);
      final http = _Http();
      api.dio.httpClientAdapter = http;
      final entered = Completer<void>();
      final release = Completer<void>();
      var admitted = true;
      var settled = 0;
      api.beforeSessionDispatch = (_) {
        entered.complete();
        return release.future;
      };
      api.sessionDispatchSettled = (_) {
        settled++;
      };
      final pending = api.promptWithMessageID(
        'ses_1',
        messageID: 'msg_1',
        text: 'Ask',
        beforeSend: () {
          if (!admitted) throw const CommandReceiptException();
        },
      );
      final result = expectLater(
        pending,
        throwsA(isA<CommandReceiptException>()),
      );
      await entered.future;
      admitted = false;
      release.complete();
      await result;
      expect(http.posts, 0);
      expect(settled, 1);
    },
  );
}

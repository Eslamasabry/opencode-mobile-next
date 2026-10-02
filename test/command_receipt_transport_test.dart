import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/command_receipt_transport.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/domain/command_receipts.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/offline_queue.dart';

const _receipt = CommandReceipt(
  commandID: 'command_1',
  receiptID: 'msg_1',
  sessionID: 'ses_1',
  tabID: 'tab_1',
  scope: 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
  createdAt: 1,
  state: CommandReceiptState.uncertain,
);
const _prompt = QueuedPrompt(
  id: 'command_1',
  profileID: 'profile_1',
  sessionID: 'ses_1',
  text: 'Ask',
  createdAt: 1,
);

MessageWithParts _message({
  String id = 'msg_1',
  String session = 'ses_1',
  String role = 'user',
  String text = 'Ask',
}) => MessageWithParts(
  info: MessageInfo(
    id: id,
    sessionID: session,
    role: role,
    time: MsgTime(created: 1),
  ),
  parts: [Part(id: 'part_1', messageID: id, type: 'text', text: text)],
);

class _V1 extends OpenCodeApi {
  _V1({this.enabled = true}) : super(baseUrl: 'http://127.0.0.1:4096');
  final bool enabled;
  bool closed = false;
  final cursors = <String?>[];
  final sends = <({String session, String id, String text})>[];
  ServerPage<MessageWithParts> Function(String? cursor) page = (_) =>
      const ServerPage(items: []);
  @override
  ServerCapabilities get capabilities =>
      ServerCapabilities(commandReceipts: enabled);
  @override
  bool get isClosed => closed;
  @override
  Future<ServerPage<MessageWithParts>> messagePage(
    String id, {
    String? cursor,
    int limit = 100,
  }) async {
    expect(id, 'ses_1');
    cursors.add(cursor);
    return page(cursor);
  }

  @override
  Future<void> promptWithMessageID(
    String sessionID, {
    required String messageID,
    required String text,
    ModelRef? model,
    String? agent,
    String? variant,
    List<PromptAttachment> attachments = const [],
    List<PromptAgentMention> agentMentions = const [],
    PromptDelivery? delivery,
    void Function()? beforeSend,
  }) async {
    beforeSend?.call();
    sends.add((session: sessionID, id: messageID, text: text));
  }
}

class _Http implements HttpClientAdapter {
  final requests =
      <({String method, String path, Map<String, dynamic>? body})>[];
  Map<String, dynamic> session = {'id': 'ses_1'};
  Future<void> Function()? preflight;
  Map<String, dynamic> createdSession = {'id': 'ses_client'};
  List<Map<String, dynamic>> inbox = [];
  Map<String, dynamic> message = {'id': 'msg_1', 'type': 'user', 'text': 'Ask'};
  Map<String, dynamic> promptReceipt = {
    'id': 'msg_1',
    'sessionID': 'ses_1',
    'type': 'prompt',
  };
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    final raw = options.data;
    requests.add((
      method: options.method,
      path: options.path,
      body: raw is String
          ? jsonDecode(raw) as Map<String, dynamic>
          : raw is Map<String, dynamic>
          ? raw
          : null,
    ));
    if (options.path == '/session/ses_1') await preflight?.call();
    final data = switch (options.path) {
      '/session' => createdSession,
      '/session/ses_1' => session,
      '/session/ses_1/prompt' => promptReceipt,
      '/session/ses_1/inbox' => inbox,
      '/session/ses_1/message/msg_1' => message,
      _ => throw StateError('Unexpected path: ${options.path}'),
    };
    return ResponseBody.fromString(
      jsonEncode({'data': data}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  group('v1 receipt lookup', () {
    late _V1 gateway;
    late CommandReceiptTransport transport;
    setUp(() {
      gateway = _V1();
      transport = CommandReceiptTransport(gateway);
      addTearDown(gateway.close);
    });

    test(
      'dispatch supplies the caller ID to the existing prompt transport',
      () async {
        await transport.dispatch(_prompt, 'msg_1');
        expect(gateway.sends, [(session: 'ses_1', id: 'msg_1', text: 'Ask')]);
      },
    );

    test('finds the exact user ID on an older page', () async {
      gateway.page = (cursor) => cursor == null
          ? ServerPage(
              items: [_message(id: 'another')],
              nextCursor: 'older',
            )
          : ServerPage(items: [_message(text: 'server-normalized text')]);
      expect(await transport.lookup(_receipt), isTrue);
      expect(gateway.cursors, [null, 'older']);
      expect(gateway.sends, isEmpty);
    });

    for (final mismatch in ['role', 'session', 'id']) {
      test('does not confirm matching text with wrong $mismatch', () async {
        gateway.page = (_) => ServerPage(
          items: [
            _message(
              role: mismatch == 'role' ? 'assistant' : 'user',
              session: mismatch == 'session' ? 'ses_other' : 'ses_1',
              id: mismatch == 'id' ? 'msg_other' : 'msg_1',
            ),
          ],
        );
        expect(await transport.lookup(_receipt), isFalse);
        expect(gateway.sends, isEmpty);
      });
    }

    test('repeated continuation stops without claiming admission', () async {
      gateway.page = (_) => const ServerPage(items: [], nextCursor: 'same');
      expect(await transport.lookup(_receipt), isFalse);
      expect(gateway.cursors, [null, 'same']);
    });

    test('ever-changing continuations are bounded', () async {
      gateway.page = (_) =>
          ServerPage(items: [], nextCursor: 'page_${gateway.cursors.length}');
      expect(await transport.lookup(_receipt), isFalse);
      expect(gateway.cursors, hasLength(100));
    });

    test('capability false refuses dispatch and performs no lookup', () async {
      final unsupported = _V1(enabled: false);
      addTearDown(unsupported.close);
      final transport = CommandReceiptTransport(unsupported);
      expect(transport.supported, isFalse);
      await expectLater(
        transport.dispatch(_prompt, 'msg_1'),
        throwsA(isA<CommandReceiptException>()),
      );
      expect(await transport.lookup(_receipt), isFalse);
      expect(unsupported.sends, isEmpty);
      expect(unsupported.cursors, isEmpty);
    });

    test('closed connection cannot confirm receipt', () async {
      gateway.closed = true;
      expect(await transport.lookup(_receipt), isFalse);
      expect(gateway.cursors, isEmpty);
    });
  });

  group('v2 receipt transport', () {
    late _Http http;
    late Api2Gateway gateway;
    late CommandReceiptTransport transport;
    setUp(() {
      http = _Http();
      final client = Api2Client.connect(
        baseUrl: 'http://127.0.0.1:4097',
        password: 'fake-test-password',
      );
      client.transport.dio.httpClientAdapter = http;
      addTearDown(client.close);
      gateway = Api2Gateway(client: client);
      transport = CommandReceiptTransport(gateway);
    });

    test(
      'prompt posts the client ID and validates its admission receipt',
      () async {
        expect(transport.supported, isTrue);
        await transport.dispatch(_prompt, 'msg_1');
        expect(http.requests.map((r) => r.path), [
          '/session/ses_1',
          '/session/ses_1/prompt',
        ]);
        expect(http.requests.last.body?['id'], 'msg_1');
        expect(http.requests.last.body?['text'], 'Ask');
      },
    );

    test(
      'pending inbox admission confirms without reading a message',
      () async {
        http.inbox = [
          {'id': 'msg_1', 'sessionID': 'ses_1', 'type': 'prompt'},
        ];
        expect(await transport.lookup(_receipt), isTrue);
        expect(http.requests.map((r) => r.path), ['/session/ses_1/inbox']);
        expect(http.requests.every((r) => r.method == 'GET'), isTrue);
      },
    );

    test('promoted user message confirms after leaving inbox', () async {
      expect(await transport.lookup(_receipt), isTrue);
      expect(http.requests.map((r) => r.path), [
        '/session/ses_1/inbox',
        '/session/ses_1/message/msg_1',
      ]);
      expect(http.requests.every((r) => r.method == 'GET'), isTrue);
    });

    test('wrong inbox session and assistant message do not confirm', () async {
      http.inbox = [
        {'id': 'msg_1', 'sessionID': 'ses_other', 'type': 'prompt'},
      ];
      http.message = {'id': 'msg_1', 'type': 'assistant'};
      expect(await transport.lookup(_receipt), isFalse);
    });

    test('wrong inbox type and message ID do not confirm', () async {
      http.inbox = [
        {'id': 'msg_1', 'sessionID': 'ses_1', 'type': 'shell'},
      ];
      http.message = {'id': 'msg_other', 'type': 'user', 'text': 'Ask'};
      expect(await transport.lookup(_receipt), isFalse);
    });

    test('mismatched POST receipt remains unconfirmed', () async {
      http.promptReceipt = {
        'id': 'msg_other',
        'sessionID': 'ses_1',
        'type': 'prompt',
      };
      await expectLater(
        transport.dispatch(_prompt, 'msg_1'),
        throwsA(isA<CommandReceiptException>()),
      );
      expect(http.requests.where((r) => r.method == 'POST'), hasLength(1));
    });

    test('staged revert refuses prompting without committing revert', () async {
      http.session = {
        'id': 'ses_1',
        'revert': {'messageID': 'msg_old'},
      };
      await expectLater(
        transport.dispatch(_prompt, 'msg_1'),
        throwsA(isA<CommandReceiptException>()),
      );
      expect(http.requests.map((r) => r.path), ['/session/ses_1']);
      expect(http.requests.every((r) => r.method == 'GET'), isTrue);
    });

    test('location change during preflight prevents the POST', () async {
      final entered = Completer<void>();
      final release = Completer<void>();
      http.preflight = () async {
        entered.complete();
        await release.future;
      };
      final assertion = expectLater(
        transport.dispatch(_prompt, 'msg_1'),
        throwsA(isA<CommandReceiptException>()),
      );
      await entered.future;
      gateway.setLocation(directory: '/another-project');
      release.complete();
      await assertion;
      expect(http.requests.map((r) => r.path), ['/session/ses_1']);
      expect(http.requests.every((r) => r.method == 'GET'), isTrue);
    });

    test('policy fence after preflight prevents the POST', () async {
      var checks = 0;
      await expectLater(
        transport.dispatch(
          _prompt,
          'msg_1',
          beforeSend: () {
            checks++;
            expect(http.requests.map((r) => r.path), ['/session/ses_1']);
            throw const CommandReceiptException();
          },
        ),
        throwsA(isA<CommandReceiptException>()),
      );
      expect(checks, 1);
      expect(http.requests.map((r) => r.path), ['/session/ses_1']);
      expect(http.requests.every((r) => r.method == 'GET'), isTrue);
    });

    test('session creation posts the supplied client ID on the wire', () async {
      await transport.createSession('ses_client');
      expect(http.requests, hasLength(1));
      expect(http.requests.single.method, 'POST');
      expect(http.requests.single.path, '/session');
      expect(http.requests.single.body?['id'], 'ses_client');
    });
  });
}

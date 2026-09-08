import 'dart:async';
import 'dart:convert';

import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/agent_account.dart';

const fixtureSignedOut = AgentAccount(requiresSignIn: true);
const fixtureSignedIn = AgentAccount(
  type: 'chatgpt',
  email: 'person@example.com',
  plan: 'plus',
  requiresSignIn: true,
);
const fixtureCode = AccountDeviceCode(
  'https://auth.openai.com/codex/device',
  'TEST-1234',
);
const fixtureLogin = {
  'type': 'chatgptDeviceCode',
  'loginId': 'owned-fixture-login',
  'verificationUrl': 'https://auth.openai.com/codex/device',
  'userCode': 'TEST-1234',
};

class AccountSocket implements CodexSocket {
  final input = StreamController<Object?>.broadcast(sync: true);
  final frames = <Map<String, dynamic>>[];
  void Function(Map<String, dynamic>)? onSend;
  bool closed = false;
  @override
  Stream<Object?> get messages => input.stream;
  @override
  void send(String data) {
    final frame = jsonDecode(data) as Map<String, dynamic>;
    frames.add(frame);
    onSend?.call(frame);
  }

  void result(dynamic id, Map<String, dynamic> data) =>
      input.add(jsonEncode({'id': id, 'result': data}));
  void error(dynamic id, int code) => input.add(
    jsonEncode({
      'id': id,
      'error': {
        'code': code,
        'message': 'Synthetic remote detail must not escape',
      },
    }),
  );
  void event(String method, Map<String, dynamic> params) =>
      input.add(jsonEncode({'method': method, 'params': params}));
  List<Map<String, dynamic>> calls(String method) =>
      frames.where((f) => f['method'] == method).toList();
  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    await input.close();
  }
}

class FakeAccountSession extends AgentAccountSession {
  @override
  bool active = true;
  @override
  bool supported = true;
  final notifications = StreamController<AccountEvent>.broadcast(sync: true);
  AgentAccount account = fixtureSignedOut;
  List<AccountRateBucket> buckets = [];
  AccountTokenUsage tokens = const AccountTokenUsage();
  Future<AgentAccount> Function()? onRead;
  Future<List<AccountRateBucket>> Function()? onLimits;
  Future<AccountTokenUsage> Function()? onUsage;
  Future<AccountDeviceCode> Function()? onStart;
  Future<bool> Function()? onCancel;
  int starts = 0, cancels = 0, reads = 0, limitReads = 0, usageReads = 0;
  bool closed = false;
  @override
  Stream<AccountEvent> get events => notifications.stream;
  @override
  Future<AgentAccount> read() async {
    reads++;
    return await (onRead?.call() ?? Future.value(account));
  }

  @override
  Future<List<AccountRateBucket>> rateLimits() async {
    limitReads++;
    return await (onLimits?.call() ?? Future.value(buckets));
  }

  @override
  Future<AccountTokenUsage> usage() async {
    usageReads++;
    return await (onUsage?.call() ?? Future.value(tokens));
  }

  @override
  Future<AccountDeviceCode> startDeviceLogin() async {
    starts++;
    return await (onStart?.call() ?? Future.value(fixtureCode));
  }

  @override
  Future<bool> cancelLogin() async {
    cancels++;
    return await (onCancel?.call() ?? Future.value(true));
  }

  @override
  Future<void> close() async {
    if (closed) return;
    closed = true;
    active = false;
    await notifications.close();
  }
}

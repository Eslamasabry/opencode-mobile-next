import 'dart:async';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/phone_agents_host.dart';
import 'package:opencode_mobile/domain/phone_agent_host.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'paseo_acp_pilot_test.dart' show FakePaseoSocket;

const _native = MethodChannel(
  'io.github.eslamasabry.opencode_mobile/builtin_linux',
);
const _storage = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
const _testSecret =
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late BuiltinPhoneAgents host;
  late Map<String, Object?> status;
  final methods = <String>[];
  Map<Object?, Object?>? startArgs;
  String? secret;
  var writes = 0;
  var readFails = false;
  var current = true;
  var wrongVersion = false;
  var changeTokenAtHello = false;
  Completer<void>? readGate;
  Completer<void>? readEntered;
  final sockets = <FakePaseoSocket>[];
  setUp(() async {
    status = {
      'serverIdlePolicySupported': true,
      'serverIdleStopped': false,
      'serverIdleHelperStopped': true,
      'serverIdleGeneration': 7,
      'serverRestartWanted': true,
      'serverRunning': true,
    };
    secret = _testSecret;
    writes = 0;
    readFails = false;
    current = true;
    wrongVersion = false;
    changeTokenAtHello = false;
    readGate = null;
    readEntered = null;
    methods.clear();
    sockets.clear();
    startArgs = null;
    SharedPreferences.setMockInitialValues({});
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(_native, (call) async {
      methods.add(call.method);
      if (call.method == 'status') return status;
      if (call.method == 'startAgentHost') {
        startArgs = Map<Object?, Object?>.from(call.arguments as Map);
        return {'running': true};
      }
      return {};
    });
    messenger.setMockMethodCallHandler(_storage, (call) async {
      if (call.method == 'read') {
        readEntered?.complete();
        await readGate?.future;
        if (readFails) throw PlatformException(code: 'test_storage_failure');
        return secret;
      }
      if (call.method == 'write') writes++;
      return null;
    });
    host = BuiltinPhoneAgents(
      profileId: 'idle-owner',
      prefs: await SharedPreferences.getInstance(),
      socketFactory: (_, _) async {
        final socket = wrongVersion ? _WrongVersionSocket() : FakePaseoSocket();
        if (changeTokenAtHello) status['serverIdleGeneration'] = 8;
        sockets.add(socket);
        return socket;
      },
    );
  });
  tearDown(() async {
    await host.dispose();
  });
  Future<void> resume() => host.resumeAfterIdle(
    expectedIdleGeneration: 7,
    stillCurrent: () => current,
  );
  test('idle host projects strict native receipt for a cold process', () async {
    final state = await host.idleState();
    expect(state.supported, true);
    expect(state.generation, 7);
    expect(state.helperStopped, true);
    expect(state.serverRunning, true);
    expect(state.automaticStartAllowed, false);
  });
  test(
    'idle host restores with original secret guarded wire and owned hello',
    () async {
      await resume();
      expect(startArgs?['profileId'], 'idle-owner');
      expect(startArgs?['idleResume'], true);
      expect(startArgs?['expectedIdleGeneration'], 7);
      expect(startArgs?['port'], 4099);
      expect(startArgs?['password'] == _testSecret, true);
      expect(startArgs?['config'], host.configuration());
      expect(writes, 0);
      expect(sockets.single.closeCode, 1000);
      expect(methods.where((v) => v == 'startAgentHost').length, 1);
    },
  );
  test('idle host never creates a missing secret', () async {
    secret = null;
    await expectLater(
      resume(),
      throwsA(
        isA<AgentHostException>().having(
          (v) => v.reason,
          'reason',
          AgentHostFailure.storage,
        ),
      ),
    );
    expect(writes, 0);
    expect(startArgs, null);
  });
  test(
    'idle host rejects corrupt or unreadable secrets without writes',
    () async {
      for (final value in ['', 'invalid', null]) {
        secret = value;
        await expectLater(
          resume(),
          throwsA(
            isA<AgentHostException>().having(
              (v) => v.reason,
              'reason',
              AgentHostFailure.storage,
            ),
          ),
        );
      }
      secret = _testSecret;
      readFails = true;
      await expectLater(
        resume(),
        throwsA(
          isA<AgentHostException>().having(
            (v) => v.reason,
            'reason',
            AgentHostFailure.storage,
          ),
        ),
      );
      expect(writes, 0);
      expect(startArgs, null);
    },
  );
  test(
    'idle host malformed and old receipts cannot authorize restoration',
    () async {
      for (final changes in <Map<String, Object?>>[
        {'serverIdlePolicySupported': false},
        {'serverIdlePolicySupported': 'true'},
        {'serverIdleGeneration': 7.5},
        {'serverIdleGeneration': -1},
        {'serverIdleHelperStopped': 'true'},
        {'serverRestartWanted': false},
        {'serverRunning': false},
      ]) {
        final old = Map<String, Object?>.from(status);
        status.addAll(changes);
        await expectLater(resume(), throwsA(isA<AgentHostException>()));
        status = old;
      }
      status.clear();
      await expectLater(resume(), throwsA(isA<AgentHostException>()));
      expect(startArgs, null);
      expect(writes, 0);
    },
  );
  test('idle host previously stopped helper stays stopped', () async {
    status['serverIdleHelperStopped'] = false;
    await resume();
    expect(startArgs, null);
    expect(writes, 0);
    expect(sockets, isEmpty);
  });
  test('idle host owner changes during secret read prevent dispatch', () async {
    readEntered = Completer<void>();
    readGate = Completer<void>();
    final pending = resume();
    await readEntered!.future;
    current = false;
    readGate!.complete();
    await expectLater(pending, throwsA(isA<AgentHostException>()));
    expect(startArgs, null);
  });
  test(
    'idle host coalesces same token and refuses a different token',
    () async {
      readEntered = Completer<void>();
      readGate = Completer<void>();
      final first = resume();
      await readEntered!.future;
      final second = resume();
      expect(identical(first, second), true);
      await expectLater(
        host.resumeAfterIdle(
          expectedIdleGeneration: 8,
          stillCurrent: () => true,
        ),
        throwsA(isA<AgentHostException>()),
      );
      readGate!.complete();
      await Future.wait([first, second]);
      expect(methods.where((v) => v == 'startAgentHost').length, 1);
    },
  );
  test(
    'idle host native token changed during secure read cannot dispatch',
    () async {
      readEntered = Completer<void>();
      readGate = Completer<void>();
      final pending = resume();
      await readEntered!.future;
      status['serverIdleGeneration'] = 8;
      readGate!.complete();
      await expectLater(pending, throwsA(isA<AgentHostException>()));
      expect(startArgs, null);
    },
  );
  test(
    'idle host native token changed during hello refuses readiness',
    () async {
      changeTokenAtHello = true;
      await expectLater(resume(), throwsA(isA<AgentHostException>()));
      expect(sockets.single.closeCode, 1000);
    },
  );
  test(
    'ordinary host start foreground loss during secret read prevents dispatch',
    () async {
      readEntered = Completer<void>();
      readGate = Completer<void>();
      final pending = host.start(stillCurrent: () => current);
      await readEntered!.future;
      current = false;
      readGate!.complete();
      await expectLater(
        pending,
        throwsA(
          isA<AgentHostException>().having(
            (v) => v.reason,
            'reason',
            AgentHostFailure.stale,
          ),
        ),
      );
      expect(startArgs, null);
    },
  );
  test('idle host stop fences a pending secure read', () async {
    readEntered = Completer<void>();
    readGate = Completer<void>();
    final pending = resume();
    final failure = expectLater(pending, throwsA(isA<AgentHostException>()));
    await readEntered!.future;
    await host.stop();
    await failure;
    readGate!.complete();
    await Future<void>.delayed(Duration.zero);
    expect(startArgs, null);
    expect(writes, 0);
  });
  test('idle host dispose fences a pending secure read', () async {
    readEntered = Completer<void>();
    readGate = Completer<void>();
    final pending = resume();
    final failure = expectLater(pending, throwsA(isA<AgentHostException>()));
    await readEntered!.future;
    await host.dispose();
    await failure;
    readGate!.complete();
    await Future<void>.delayed(Duration.zero);
    expect(startArgs, null);
    expect(writes, 0);
  });
  test(
    'idle host requires actual pinned hello and closes rejected probe',
    () async {
      wrongVersion = true;
      await expectLater(
        resume(),
        throwsA(
          isA<AgentHostException>().having(
            (v) => v.reason,
            'reason',
            AgentHostFailure.hello,
          ),
        ),
      );
      expect(sockets.single.closeCode, 1000);
    },
  );
  test(
    'idle host reconnect cannot create a secret after guarded resume',
    () async {
      await resume();
      secret = null;
      await expectLater(
        host.openGateway('/root/projects/example'),
        throwsA(
          isA<AgentHostException>().having(
            (v) => v.reason,
            'reason',
            AgentHostFailure.storage,
          ),
        ),
      );
      expect(writes, 0);
    },
  );
  test(
    'idle host automatic start allows only complete foreground legacy or idle facts',
    () async {
      status = {'serverRunning': true, 'serverRestartWanted': true};
      expect((await host.idleState()).automaticStartAllowed, true);
      status['serverIdlePolicySupported'] = 'false';
      expect((await host.idleState()).automaticStartAllowed, false);
      status['serverIdlePolicySupported'] = false;
      expect((await host.idleState()).automaticStartAllowed, true);
      status = {
        'serverIdlePolicySupported': true,
        'serverIdleStopped': false,
        'serverIdleHelperStopped': false,
        'serverIdleGeneration': 0,
        'serverRestartWanted': true,
        'serverRunning': true,
      };
      expect((await host.idleState()).automaticStartAllowed, true);
      status['serverIdleGeneration'] = 0;
      expect((await host.idleState()).automaticStartAllowed, true);
      status['serverIdleGeneration'] = 7;
      expect((await host.idleState()).automaticStartAllowed, false);
      status['serverIdleStopped'] = true;
      expect((await host.idleState()).automaticStartAllowed, false);
    },
  );
}

final class _WrongVersionSocket extends FakePaseoSocket {
  @override
  void send(String message) {
    final raw = jsonDecode(message) as Map;
    if (raw['type'] == 'hello') {
      push('status', {'status': 'server_info', 'version': 'unsupported-test'});
    } else {
      super.send(message);
    }
  }
}

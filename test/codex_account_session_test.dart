import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/codex/account.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/agent_account.dart';
import 'support/account_fakes.dart';

void main() {
  late AccountSocket socket;
  late CodexTransport transport;
  late CodexAccountSession session;
  late List<AccountEvent> events;
  bool scope = true;
  String version = '0.153.4';
  void defaultReply(Map<String, dynamic> frame) {
    switch (frame['method']) {
      case 'initialize':
        socket.result(frame['id'], {
          'userAgent': 'codex_cli_rs/$version (fixture)',
        });
      case 'account/read':
        socket.result(frame['id'], {
          'account': null,
          'requiresOpenaiAuth': true,
        });
      case 'account/login/start':
        socket.result(frame['id'], fixtureLogin);
      case 'account/login/cancel':
        socket.result(frame['id'], {'status': 'canceled'});
    }
  }

  setUp(() async {
    scope = true;
    version = '0.153.4';
    events = [];
    socket = AccountSocket()..onSend = defaultReply;
    transport = CodexTransport(
      endpoint: 'wss://fixture.invalid',
      token: 'fixture',
      socketFactory: (_, _) async => socket,
      requestTimeout: const Duration(milliseconds: 150),
    );
    await transport.connect();
    session = CodexAccountSession(transport, () => scope);
    session.events.listen(events.add);
  });
  tearDown(() async {
    await session.close();
    await transport.close();
  });
  test(
    'pinned account read never refreshes provider tokens; start/cancel owns only returned handle',
    () async {
      expect(session.supported, isTrue);
      expect((await session.read()).signedIn, isFalse);
      expect(socket.calls('account/read').last['params'], {
        'refreshToken': false,
      });
      final code = await session.startDeviceLogin();
      expect(code.userCode, 'TEST-1234');
      expect(code.toString(), isNot(contains(code.userCode)));
      expect(socket.calls('account/login/start').single['params'], {
        'type': 'chatgptDeviceCode',
      });
      expect(await session.cancelLogin(), isTrue);
      expect(socket.calls('account/login/cancel').single['params'], {
        'loginId': 'owned-fixture-login',
      });
    },
  );
  test('unsupported runtime does not dispatch account operations', () async {
    transport.accountApiSupported = false;
    await expectLater(
      session.startDeviceLogin(),
      throwsA(isA<AgentAccountException>()),
    );
    expect(socket.calls('account/read'), isEmpty);
    expect(socket.calls('account/login/start'), isEmpty);
  });
  test('second start cannot replace outstanding login', () async {
    await session.startDeviceLogin();
    await expectLater(
      session.startDeviceLogin(),
      throwsA(isA<AgentAccountException>()),
    );
    expect(socket.calls('account/login/start').length, 1);
  });
  test(
    'malformed account cannot be treated as a signed-out preflight',
    () async {
      socket.onSend = (frame) {
        if (frame['method'] == 'account/read') {
          socket.result(frame['id'], {'requiresOpenaiAuth': true});
        } else {
          defaultReply(frame);
        }
      };
      await expectLater(
        session.startDeviceLogin(),
        throwsA(
          isA<AgentAccountException>().having(
            (e) => e.kind,
            'kind',
            AgentAccountFailure.invalidResponse,
          ),
        ),
      );
      expect(socket.calls('account/login/start'), isEmpty);
    },
  );
  test(
    'missing-method error stays fixed and never exposes remote detail',
    () async {
      socket.onSend = (frame) {
        if (frame['method'] == 'account/usage/read') {
          socket.error(frame['id'], -32601);
        } else {
          defaultReply(frame);
        }
      };
      await expectLater(
        session.usage(),
        throwsA(
          isA<AgentAccountException>()
              .having((e) => e.kind, 'kind', AgentAccountFailure.unavailable)
              .having(
                (e) => e.toString(),
                'safe copy',
                isNot(contains('Synthetic remote detail')),
              ),
        ),
      );
    },
  );
  test(
    'wrong version handshake disables account API without affecting connection',
    () async {
      final otherSocket = AccountSocket();
      otherSocket.onSend = (frame) {
        if (frame['method'] == 'initialize') {
          otherSocket.result(frame['id'], {
            'userAgent': 'codex_cli_rs/0.154.0 (fixture)',
          });
        }
      };
      final other = CodexTransport(
        endpoint: 'wss://fixture.invalid',
        token: 'fixture',
        socketFactory: (_, _) async => otherSocket,
      );
      await other.connect();
      final account = CodexAccountSession(other, () => true);
      expect(other.connected, isTrue);
      expect(account.supported, isFalse);
      await expectLater(account.read(), throwsA(isA<AgentAccountException>()));
      expect(otherSocket.calls('account/read'), isEmpty);
      await account.close();
      await other.close();
    },
  );
  test(
    'old epoch cancel cannot reopen transport or mutate replacement',
    () async {
      await session.startDeviceLogin();
      await transport.close();
      expect(await session.cancelLogin(), isFalse);
      expect(socket.calls('account/login/cancel'), isEmpty);
      expect(socket.calls('initialize').length, 1);
    },
  );
  test(
    'foreign completion ignored; matching completion accepted once',
    () async {
      await session.startDeviceLogin();
      socket.event('account/login/completed', {
        'loginId': 'foreign',
        'success': true,
      });
      expect(events, isEmpty);
      socket.event('account/login/completed', {
        'loginId': 'owned-fixture-login',
        'success': true,
      });
      socket.event('account/login/completed', {
        'loginId': 'owned-fixture-login',
        'success': true,
      });
      expect(
        events.where((e) => e.kind == AccountEventKind.loginCompleted).length,
        1,
      );
    },
  );
  test(
    'early owned completion settles flow before late start response can show code',
    () async {
      socket.onSend = (frame) {
        if (frame['method'] == 'account/login/start') {
          socket.event('account/login/completed', {
            'loginId': 'foreign',
            'success': false,
          });
          socket.event('account/login/completed', {
            'loginId': 'owned-fixture-login',
            'success': true,
          });
          socket.result(frame['id'], fixtureLogin);
        } else {
          defaultReply(frame);
        }
      };
      await expectLater(
        session.startDeviceLogin(),
        throwsA(isA<AgentAccountException>()),
      );
      expect(events.single.kind, AccountEventKind.loginCompleted);
      expect(events.single.success, isTrue);
      expect(socket.calls('account/login/cancel'), isEmpty);
    },
  );
  for (final close in [false, true]) {
    test(
      'late start after ${close ? 'close' : 'cancel'} cancels only on original scope',
      () async {
        dynamic held;
        socket.onSend = (frame) {
          if (frame['method'] == 'account/login/start') {
            held = frame['id'];
          } else {
            defaultReply(frame);
          }
        };
        final start = expectLater(
          session.startDeviceLogin(),
          throwsA(isA<AgentAccountException>()),
        );
        await pumpEventQueue();
        expect(held, isNotNull);
        if (close) {
          await session.close();
        } else {
          expect(await session.cancelLogin(), isFalse);
        }
        socket.result(held, fixtureLogin);
        await start;
        expect(socket.calls('account/login/cancel').length, 1);
        if (!close) {
          expect(events.last.kind, AccountEventKind.loginCancelled);
          expect(events.last.success, isTrue);
        }
      },
    );
  }
  test(
    'scope loss during held start cannot cancel a different scope',
    () async {
      dynamic held;
      socket.onSend = (frame) {
        if (frame['method'] == 'account/login/start') {
          held = frame['id'];
        } else {
          defaultReply(frame);
        }
      };
      final start = expectLater(
        session.startDeviceLogin(),
        throwsA(isA<AgentAccountException>()),
      );
      await pumpEventQueue();
      scope = false;
      socket.result(held, fixtureLogin);
      await start;
      expect(socket.calls('account/login/cancel'), isEmpty);
    },
  );
  test('account update during preflight stops login dispatch', () async {
    socket.onSend = (frame) {
      if (frame['method'] == 'account/read') {
        socket.event('account/updated', {'authMode': 'chatgpt'});
      }
      defaultReply(frame);
    };
    await expectLater(
      session.startDeviceLogin(),
      throwsA(isA<AgentAccountException>()),
    );
    expect(socket.calls('account/login/start'), isEmpty);
  });
  test(
    'unconfirmed cancellation retains ownership for explicit retry',
    () async {
      await session.startDeviceLogin();
      socket.onSend = (frame) {
        if (frame['method'] == 'account/login/cancel') {
          socket.result(frame['id'], {'status': 'notFound'});
        } else {
          defaultReply(frame);
        }
      };
      expect(await session.cancelLogin(), isFalse);
      await expectLater(
        session.startDeviceLogin(),
        throwsA(isA<AgentAccountException>()),
      );
      socket.onSend = defaultReply;
      expect(await session.cancelLogin(), isTrue);
      expect(socket.calls('account/login/cancel').length, 2);
    },
  );
  test(
    'lost start receipt cannot become confirmed cancellation or replay',
    () async {
      socket.onSend = (frame) {
        if (frame['method'] != 'account/login/start') defaultReply(frame);
      };
      await expectLater(
        session.startDeviceLogin(),
        throwsA(
          isA<AgentAccountException>().having(
            (e) => e.kind,
            'kind',
            AgentAccountFailure.uncertain,
          ),
        ),
      );
      expect(await session.cancelLogin(), isFalse);
      await expectLater(
        session.startDeviceLogin(),
        throwsA(isA<AgentAccountException>()),
      );
      expect(socket.calls('account/login/start').length, 1);
    },
  );
  test('lookalike URL is never exposed and owned login is canceled', () async {
    socket.onSend = (frame) {
      if (frame['method'] == 'account/login/start') {
        socket.result(frame['id'], {
          ...fixtureLogin,
          'verificationUrl':
              'https://auth.openai.com.evil.example/codex/device',
        });
      } else {
        defaultReply(frame);
      }
    };
    await expectLater(
      session.startDeviceLogin(),
      throwsA(isA<AgentAccountException>()),
    );
    expect(socket.calls('account/login/cancel').length, 1);
  });
  test(
    'reported null stays unavailable and reported zero stays zero',
    () async {
      socket.onSend = (frame) {
        if (frame['method'] == 'account/usage/read') {
          socket.result(frame['id'], {
            'summary': {'lifetimeTokens': 0, 'peakDailyTokens': null},
          });
        } else if (frame['method'] == 'account/rateLimits/read') {
          socket.result(frame['id'], {
            'rateLimits': {
              'limitId': 'codex',
              'primary': {
                'usedPercent': 0,
                'windowDurationMins': null,
                'resetsAt': null,
              },
              'secondary': null,
            },
          });
        } else {
          defaultReply(frame);
        }
      };
      expect((await session.usage()).lifetimeTokens, 0);
      expect((await session.usage()).peakDailyTokens, isNull);
      final bucket = (await session.rateLimits()).single;
      expect(bucket.primary!.usedPercent, 0);
      expect(bucket.primary!.resetsAt, isNull);
      expect(bucket.secondary, isNull);
    },
  );
}

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/agent_sign_in.dart';
import 'package:opencode_mobile/domain/agent_catalog.dart';
import 'package:opencode_mobile/domain/agent_sign_in.dart';

const _url =
    'https://claude.com/cai/oauth/authorize?state=synthetic-challenge-state';
const _code = 'synthetic-code#synthetic-state';

class _Host implements AgentSignInHost {
  AgentSignInRun? run;
  int starts = 0;
  int cancellations = 0;
  int submissions = 0;
  String? lastStatusRunId;
  bool rejectCancellation = false;
  Completer<AgentSignInHostUpdate>? delayedStart;
  AgentSignInPhase statusPhase = AgentSignInPhase.signedIn;
  DateTime? resetAt;

  AgentSignInHostUpdate update(AgentSignInRun run, AgentSignInPhase phase) =>
      AgentSignInHostUpdate(
        runId: run.runId,
        phase: phase,
        authorizationUrl: phase == AgentSignInPhase.urlReady
            ? AgentAuthorizationUrl.validate('claude', _url)
            : null,
        resetAt: phase == AgentSignInPhase.limitReached ? resetAt : null,
      );

  @override
  Future<AgentSignInHostUpdate> start(AgentSignInRun run) async {
    this.run = run;
    starts++;
    final delayed = delayedStart;
    if (delayed != null) return delayed.future;
    return update(run, AgentSignInPhase.urlReady);
  }

  @override
  Future<AgentSignInHostUpdate> readChallenge(AgentSignInRun run) async =>
      update(run, AgentSignInPhase.awaitingCode);

  @override
  Future<AgentSignInHostUpdate> submitCode(
    AgentSignInRun run,
    AgentSignInCode code,
  ) async {
    submissions++;
    code.consume();
    return update(run, AgentSignInPhase.awaitingCode);
  }

  @override
  Future<AgentSignInHostUpdate> status(AgentSignInRun run) async {
    lastStatusRunId = run.runId;
    return update(run, statusPhase);
  }

  @override
  Future<void> cancelAndDrain(AgentSignInRun run) async {
    cancellations++;
    if (rejectCancellation) throw Exception('synthetic-private-host-output');
  }
}

AgentSignInSession _session(
  AgentSignInHost host, {
  AgentSignInMethod method = AgentSignInMethod.browserOAuthHost,
}) => AgentSignInSession(
  host: host,
  profileId: 'profile-one',
  agentId: 'claude',
  method: method,
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'browser code flow awaits host status and redacts ephemeral values',
    () async {
      final host = _Host();
      final session = _session(host);
      await session.start();
      expect(session.state.phase, AgentSignInPhase.urlReady);
      expect(session.state.authorizationUrl!.uri.toString(), _url);
      expect(session.state.toString(), isNot(contains('synthetic')));
      expect(session.state.authorizationUrl.toString(), isNot(contains(_url)));
      // The host waits for Claude's prompt itself: a code sent while the page
      // is still open (urlReady) reaches the host instead of being refused.
      expect(session.state.acceptsCode, isTrue);
      final code = AgentSignInCode(_code);
      expect(code.toString(), isNot(contains(_code)));
      await session.submitCode(code);
      expect(code.consumed, isTrue);
      expect(host.submissions, 1);
      // The host answered; Claude keeps a refused login open, so the next
      // paste goes to the same sign-in.
      expect(session.state.acceptsCode, isTrue);
      final retry = AgentSignInCode('another-code#same-state');
      await session.submitCode(retry);
      expect(retry.consumed, isTrue);
      expect(host.submissions, 2);
      await session.refreshStatus();
      expect(session.state.phase, AgentSignInPhase.signedIn);
      expect(session.state.authorizationUrl, isNull);
      await session.close();
    },
  );

  test(
    'status inspection creates no login and explicit start reuses its run',
    () async {
      final host = _Host()..statusPhase = AgentSignInPhase.signedOut;
      final session = _session(host);
      expect(session.state.inspected, isFalse);
      await session.inspectStatus();
      expect(session.state.inspected, isTrue);
      expect(host.starts, 0);
      final inspectedRun = host.lastStatusRunId;
      await session.start();
      expect(host.starts, 1);
      expect(host.run!.runId, inspectedRun);
      await session.close();
    },
  );

  test(
    'codes are single use and reject keys, terminal escapes and malformed input',
    () {
      final code = AgentSignInCode(_code);
      expect(code.consume(), _code);
      expect(code.consume, throwsA(isA<AgentSignInException>()));
      for (final value in [
        'sk-ant-synthetic',
        'sk-key#state',
        'abc',
        '#abc',
        'a#',
        'a#b#c',
        'a#b\n',
      ]) {
        expect(
          () => AgentSignInCode(value),
          throwsA(isA<AgentSignInException>()),
        );
      }
    },
  );

  test(
    'authorization challenges accept only the pinned vendor authorization route',
    () {
      for (final url in [
        'http://claude.com/cai/oauth/authorize',
        'https://claude.com.evil.test/cai/oauth/authorize',
        'https://claude.com@evil.test/cai/oauth/authorize',
        'https://claude.com:8443/cai/oauth/authorize',
        'https://claude.com/cai/oauth/authorize#private',
        'https://claude.com/unrelated',
        'https://claude.ai/oauth/authorize',
      ]) {
        expect(
          () => AgentAuthorizationUrl.validate('claude', url),
          throwsA(isA<AgentSignInException>()),
        );
      }
      expect(
        () => AgentAuthorizationUrl.validate('unknown-agent', _url),
        throwsA(isA<AgentSignInException>()),
      );
    },
  );

  test('API key instructions never dispatch a credential operation', () async {
    final host = _Host();
    final session = _session(host, method: AgentSignInMethod.apiKeyHost);
    await session.start();
    expect(session.state.phase, AgentSignInPhase.signedOut);
    expect(session.state.hostOnlyApiKey, isTrue);
    expect(host.starts, 0);
    expect(session.state.authorizationUrl, isNull);
    await session.close();
  });

  test(
    'cancellation suppresses late challenges and drains the exact old run',
    () async {
      final host = _Host()..delayedStart = Completer();
      final session = _session(host);
      final starting = session.start();
      final oldRun = host.run!;
      final cancelling = session.cancelAndDrain();
      host.delayedStart!.complete(
        host.update(oldRun, AgentSignInPhase.urlReady),
      );
      await starting;
      await cancelling;
      expect(session.state.phase, AgentSignInPhase.signedOut);
      expect(session.state.authorizationUrl, isNull);
      expect(host.cancellations, 2);
      host.delayedStart = null;
      await session.start();
      expect(host.run!.runId, isNot(oldRun.runId));
      await session.close();
    },
  );

  test(
    'unconfirmed cancellation blocks replacement until successful drain',
    () async {
      final host = _Host();
      final session = _session(host);
      await session.start();
      host.rejectCancellation = true;
      await expectLater(
        session.cancelAndDrain(),
        throwsA(isA<AgentSignInException>()),
      );
      expect(session.state.failure, AgentSignInFailure.cancellationUnconfirmed);
      await expectLater(session.start(), throwsA(isA<AgentSignInException>()));
      host.rejectCancellation = false;
      await session.cancelAndDrain();
      await session.start();
      expect(host.starts, 2);
      await session.close();
    },
  );

  test('rate limits retain only host supplied reset time', () async {
    final host = _Host()..statusPhase = AgentSignInPhase.limitReached;
    final session = _session(host);
    await session.start();
    await session.refreshStatus();
    expect(session.state.phase, AgentSignInPhase.limitReached);
    expect(session.state.resetAt, isNull);
    final reset = DateTime.utc(2026, 10, 3, 12);
    host.resetAt = reset;
    await session.refreshStatus();
    expect(session.state.resetAt, reset);
    await session.close();
  });

  group('private native auth channel', () {
    const channel = MethodChannel('test/private_agent_auth');
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

    tearDown(() => messenger.setMockMethodCallHandler(channel, null));

    AgentSignInRun run() => AgentSignInRun(
      profileId: 'profile-one',
      agentId: 'claude',
      runId: 'run-one',
      method: AgentSignInMethod.browserOAuthHost,
    );

    test('only declared fields and same run become a challenge', () async {
      final host = ChannelAgentSignInHost(channel: channel);
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => {'runId': 'run-one', 'phase': 'urlReady', 'url': _url},
      );
      expect((await host.start(run())).phase, AgentSignInPhase.urlReady);
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => {'runId': 'different-run', 'phase': 'signedIn'},
      );
      await expectLater(
        host.status(run()),
        throwsA(isA<AgentSignInException>()),
      );
      messenger.setMockMethodCallHandler(
        channel,
        (call) async => {
          'runId': 'run-one',
          'phase': 'signedIn',
          'transcript': 'synthetic-private-output',
        },
      );
      await expectLater(
        host.status(run()),
        throwsA(isA<AgentSignInException>()),
      );
    });

    test(
      'code uses the dedicated transient sink and cancel needs confirmed drain',
      () async {
        final methods = <String>[];
        messenger.setMockMethodCallHandler(channel, (call) async {
          methods.add(call.method);
          if (call.method == 'submitAgentSignInCode') {
            expect((call.arguments as Map)['code'], _code);
            return {'runId': 'run-one', 'phase': 'awaitingCode'};
          }
          return {'runId': 'run-one', 'drained': false};
        });
        final host = ChannelAgentSignInHost(channel: channel);
        final code = AgentSignInCode(_code);
        await host.submitCode(run(), code);
        expect(code.consumed, isTrue);
        await expectLater(
          host.cancelAndDrain(run()),
          throwsA(isA<AgentSignInException>()),
        );
        expect(methods, ['submitAgentSignInCode', 'cancelAgentSignIn']);
      },
    );

    test(
      'platform failures redact raw diagnostics and reset text is rejected',
      () async {
        final host = ChannelAgentSignInHost(channel: channel);
        messenger.setMockMethodCallHandler(channel, (call) async {
          throw PlatformException(
            code: 'unknown',
            message: _url,
            details: _code,
          );
        });
        await expectLater(
          host.start(run()),
          throwsA(
            isA<AgentSignInException>().having(
              (error) => error.toString(),
              'safe error',
              isNot(contains('synthetic')),
            ),
          ),
        );
        messenger.setMockMethodCallHandler(
          channel,
          (call) async => {
            'runId': 'run-one',
            'phase': 'limitReached',
            'resetAt': 'probably tomorrow',
          },
        );
        await expectLater(
          host.status(run()),
          throwsA(isA<AgentSignInException>()),
        );
      },
    );
  });
}

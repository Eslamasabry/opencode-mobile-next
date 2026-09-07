import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/codex/gateway.dart';
import 'package:opencode_mobile/codex/transport.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/codex_connection_probe.dart';

class _ProbeGateway extends CodexGateway {
  _ProbeGateway({this.healthFailure, this.sessionsFailure, this.healthy = true})
    : super(
        transport: CodexTransport(
          endpoint: 'ws://127.0.0.1:1',
          token: 'test-token',
        ),
        directory: '/work/project',
      );

  final bool healthy;
  final Object? healthFailure;
  final Object? sessionsFailure;
  var healthCalls = 0;
  var sessionPageCalls = 0;
  var closed = false;

  @override
  Future<Health> health() async {
    healthCalls++;
    final failure = healthFailure;
    if (failure != null) throw failure;
    return Health(healthy: healthy, version: '0.153.4');
  }

  @override
  Future<ServerPage<Session>> sessionPage({
    String? cursor,
    int limit = 100,
  }) async {
    sessionPageCalls++;
    final failure = sessionsFailure;
    if (failure != null) throw failure;
    expect(limit, 1);
    return const ServerPage<Session>(items: [], nextCursor: null);
  }

  @override
  void close() {
    closed = true;
  }
}

void main() {
  test(
    'successful probe checks health and scope then closes the gateway',
    () async {
      late _ProbeGateway gateway;
      final result = await probeCodexConnection(
        baseUrl: 'wss://codex.example.test',
        token: 'test-token',
        directory: '/work/project',
        gatewayFactory:
            ({required baseUrl, required token, required directory}) {
              expect(baseUrl, 'wss://codex.example.test');
              expect(token, 'test-token');
              expect(directory, '/work/project');
              return gateway = _ProbeGateway();
            },
      );

      expect(result.ok, isTrue);
      expect(result.message, 'Codex connection verified.');
      expect(result.version, '0.153.4');
      expect(gateway.healthCalls, 1);
      expect(gateway.sessionPageCalls, 1);
      expect(gateway.closed, isTrue);
    },
  );

  test('a failed scoped read is not reported as verified', () async {
    final gateway = _ProbeGateway(
      sessionsFailure: CodexFailure(CodexFailureKind.scopeMismatch),
    );
    final result = await probeCodexConnection(
      baseUrl: 'wss://codex.example.test',
      token: 'test-token',
      directory: '/work/project',
      gatewayFactory:
          ({required baseUrl, required token, required directory}) => gateway,
    );
    expect(result.ok, isFalse);
    expect(gateway.healthCalls, 1);
    expect(gateway.sessionPageCalls, 1);
    expect(gateway.closed, isTrue);
  });

  test('unhealthy probe is not reported as verified', () async {
    final gateway = _ProbeGateway(healthy: false);
    final result = await probeCodexConnection(
      baseUrl: 'wss://codex.example.test',
      token: 'test-token',
      directory: '/work/project',
      gatewayFactory:
          ({required baseUrl, required token, required directory}) => gateway,
    );
    expect(result.ok, isFalse);
    expect(gateway.sessionPageCalls, 0);
    expect(gateway.closed, isTrue);
  });

  test('failed gateway probe still closes the gateway', () async {
    late _ProbeGateway gateway;
    final result = await probeCodexConnection(
      baseUrl: 'wss://codex.example.test',
      token: 'test-token',
      directory: '/work/project',
      gatewayFactory: ({required baseUrl, required token, required directory}) {
        return gateway = _ProbeGateway(
          healthFailure: CodexFailure(CodexFailureKind.authentication),
        );
      },
    );

    expect(result.ok, isFalse);
    expect(result.message, 'Codex rejected the server connection token.');
    expect(result.version, isNull);
    expect(gateway.healthCalls, 1);
    expect(gateway.sessionPageCalls, 0);
    expect(gateway.closed, isTrue);
  });

  test('invalid input returns before calling the gateway factory', () async {
    var called = false;
    final result = await probeCodexConnection(
      baseUrl: 'https://codex.example.test',
      token: 'test-token',
      directory: '/work/project',
      gatewayFactory: ({required baseUrl, required token, required directory}) {
        called = true;
        throw StateError('must not be called');
      },
    );

    expect(result.ok, isFalse);
    expect(called, isFalse);
    expect(result.message, isNot(contains('test-token')));
  });

  test('unexpected failures use fixed sanitized copy', () async {
    late _ProbeGateway gateway;
    final result = await probeCodexConnection(
      baseUrl: 'wss://codex.example.test',
      token: 'super-secret-token',
      directory: '/work/project',
      gatewayFactory: ({required baseUrl, required token, required directory}) {
        return gateway = _ProbeGateway(
          healthFailure: StateError('token=super-secret-token'),
        );
      },
    );

    expect(result.ok, isFalse);
    expect(result.message, 'Could not verify the Codex connection.');
    expect(result.message, isNot(contains('super-secret-token')));
    expect(gateway.closed, isTrue);
  });
}

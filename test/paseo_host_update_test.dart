import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/paseo/gateway.dart';
import 'package:opencode_mobile/paseo/transport.dart';

import 'paseo_gateway_test.dart' show FakeDaemon, agentJson;

const _dir = '/work/app';

/// The computer's helper as the app sees it for the "Update agents" row.
void main() {
  late FakeDaemon daemon;
  late PaseoGateway gateway;

  String requestId() =>
      daemon.of('daemon.update.request').single['requestId'] as String;

  setUp(() {
    daemon = FakeDaemon();
    gateway = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async => daemon,
      ),
      directory: _dir,
    );
    addTearDown(gateway.close);
  });

  test('progress phases arrive in order and do not end the request', () async {
    final phases = <HostUpdatePhase>[];
    final result = gateway.updateHostDaemon(onProgress: phases.add);
    await pumpEventQueue();
    var done = false;
    unawaited(result.whenComplete(() => done = true));
    for (final phase in ['starting', 'downloading', 'installing']) {
      daemon.push('daemon.update.progress', {
        'requestId': requestId(),
        'phase': phase,
      });
    }
    await pumpEventQueue();
    expect(phases, [
      HostUpdatePhase.starting,
      HostUpdatePhase.downloading,
      HostUpdatePhase.installing,
    ]);
    expect(done, isFalse, reason: 'progress is not the answer');
    daemon.push('daemon.update.progress', {
      'requestId': requestId(),
      'phase': 'complete',
    });
    daemon.push('daemon.update.response', {
      'requestId': requestId(),
      'success': true,
      'error': null,
      'previousVersion': '0.9.1',
      'newVersion': '0.9.2',
    });
    final finished = await result;
    expect(phases.last, HostUpdatePhase.complete);
    expect(finished.success, isTrue);
    expect(finished.previousVersion, '0.9.1');
    expect(finished.newVersion, '0.9.2');
    expect(finished.alreadyUpToDate, isFalse);
  });

  test('the same version before and after is "already up to date"', () async {
    final result = gateway.updateHostDaemon();
    await pumpEventQueue();
    daemon.push('daemon.update.response', {
      'requestId': requestId(),
      'success': true,
      'error': null,
      'previousVersion': '0.9.2',
      'newVersion': '0.9.2',
    });
    expect((await result).alreadyUpToDate, isTrue);
  });

  test('a failed update is a result with the reason, not a throw', () async {
    final result = gateway.updateHostDaemon();
    await pumpEventQueue();
    daemon.push('daemon.update.response', {
      'requestId': requestId(),
      'success': false,
      'error': 'npm ERR! EACCES: permission denied',
      'previousVersion': '0.9.1',
      'newVersion': null,
    });
    final finished = await result;
    expect(finished.success, isFalse);
    expect(finished.reason, contains('EACCES'));
    expect(finished.newVersion, isNull);
  });

  test('the connection returns by itself after the helper restarts', () async {
    final statuses = <StreamStatus>[];
    final replacement = FakeDaemon()
      ..handlers['fetch_agents_request'] = (_) => (
        'fetch_agents_response',
        {
          'entries': [
            {'agent': agentJson('a1')},
          ],
          'pageInfo': {'hasMore': false},
        },
      );
    var first = true;
    final initial = FakeDaemon()
      ..handlers['fetch_agents_request'] = (_) => (
        'fetch_agents_response',
        {
          'entries': <Object>[],
          'pageInfo': {'hasMore': false},
        },
      );
    final restarting = PaseoGateway(
      transport: PaseoTransport(
        endpoint: 'ws://127.0.0.1:6767',
        socketFactory: (_, _) async {
          if (first) {
            first = false;
            return initial;
          }
          return replacement;
        },
      ),
      directory: _dir,
    );
    addTearDown(restarting.close);
    restarting
        .openEventChannel(onEvent: (_) {}, onStatus: statuses.add)
        .start();
    await pumpEventQueue();
    final update = restarting.updateHostDaemon();
    await pumpEventQueue();
    initial.push('daemon.update.response', {
      'requestId': initial.of('daemon.update.request').single['requestId'],
      'success': true,
      'error': null,
      'previousVersion': '0.9.1',
      'newVersion': '0.9.2',
    });
    expect((await update).newVersion, '0.9.2');
    // The daemon restarts itself right after it answers.
    await initial.close();
    await Future<void>.delayed(const Duration(milliseconds: 1500));
    expect(
      statuses,
      containsAllInOrder([
        StreamStatus.connected,
        StreamStatus.reconnecting,
        StreamStatus.connected,
      ]),
    );
    expect(replacement.of('fetch_agents_request'), isNotEmpty);
  });
}

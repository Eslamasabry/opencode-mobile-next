import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/agent_sign_in_foreground.dart';

class _Lease implements AgentSignInForegroundLease {
  _Lease(this.log);
  final List<String> log;
  final events = StreamController<void>.broadcast(sync: true);
  @override
  bool active = true;
  @override
  Future<void> get ready => Future<void>.value();
  @override
  Stream<void> get lost => events.stream;
  @override
  Future<void> release() async {
    if (!active) return;
    active = false;
    log.add('release');
    await events.close();
  }
}

class _Port implements AgentSignInForegroundPort {
  final log = <String>[];
  @override
  AgentSignInForegroundLease reserveAgentSignInForeground() {
    log.add('reserve');
    return _Lease(log);
  }
}

void main() {
  test('successive replacement cannot skip the oldest pending drain', () async {
    final first = AgentSignInForegroundRegistry.bind('foreground-chain', _Port());
    final drain = Completer<void>();
    first.addCleanup(() => drain.future);
    AgentSignInForegroundRegistry.bind('foreground-chain', _Port());
    final lastPort = _Port();
    final last = AgentSignInForegroundRegistry.bind('foreground-chain', lastPort);
    final lease = last.reserve();
    await pumpEventQueue();
    final beforeDrain = List<String>.of(lastPort.log);
    drain.complete();
    await lease.ready;
    await AgentSignInForegroundRegistry.unbind(last);
    expect(beforeDrain, isEmpty);
  });
  test(
    'replacement waits for old terminal drain and foreground release',
    () async {
      final oldPort = _Port();
      final old = AgentSignInForegroundRegistry.bind(
        'foreground-replacement',
        oldPort,
      );
      final oldLease = old.reserve();
      await oldLease.ready;
      final drain = Completer<void>();
      old.addCleanup(() async {
        oldPort.log.add('drain');
        await drain.future;
      });
      final nextPort = _Port();
      final next = AgentSignInForegroundRegistry.bind(
        'foreground-replacement',
        nextPort,
      );
      final nextLease = next.reserve();
      await pumpEventQueue();
      expect(oldPort.log, ['reserve', 'drain']);
      expect(nextPort.log, isEmpty);
      drain.complete();
      await nextLease.ready;
      expect(oldPort.log, ['reserve', 'drain', 'release']);
      expect(nextLease.active, true);
      await AgentSignInForegroundRegistry.unbind(next);
    },
  );

  test('old unbind cannot clear a replacement binding', () async {
    final old = AgentSignInForegroundRegistry.bind('foreground-stale', _Port());
    final next = AgentSignInForegroundRegistry.bind(
      'foreground-stale',
      _Port(),
    );
    await AgentSignInForegroundRegistry.unbind(old);
    expect(
      AgentSignInForegroundRegistry.bindingFor('foreground-stale'),
      same(next),
    );
    expect(next.current, true);
    await AgentSignInForegroundRegistry.unbind(next);
  });

  test(
    'cancelling a reservation before the drain gate never starts a service',
    () async {
      final drain = Completer<void>();
      final old = AgentSignInForegroundRegistry.bind(
        'foreground-cancel',
        _Port(),
      );
      old.addCleanup(() => drain.future);
      final port = _Port();
      final next = AgentSignInForegroundRegistry.bind(
        'foreground-cancel',
        port,
      );
      final lease = next.reserve();
      final failed = expectLater(
        lease.ready,
        throwsA(isA<AgentSignInForegroundException>()),
      );
      await lease.release();
      await failed;
      drain.complete();
      await pumpEventQueue();
      expect(port.log, isEmpty);
      await AgentSignInForegroundRegistry.unbind(next);
    },
  );

  test('failed cleanup blocks admission and remains retryable', () async {
    var fail = true;
    final old = AgentSignInForegroundRegistry.bind('foreground-retry', _Port());
    old.addCleanup(() async {
      if (fail) throw StateError('private native text');
    });
    await expectLater(
      AgentSignInForegroundRegistry.unbind(old),
      throwsA(isA<AgentSignInForegroundException>()),
    );
    fail = false;
    await AgentSignInForegroundRegistry.unbind(old);
    final next = AgentSignInForegroundRegistry.bind(
      'foreground-retry',
      _Port(),
    );
    final lease = next.reserve();
    await lease.ready;
    expect(lease.active, true);
    await AgentSignInForegroundRegistry.unbind(next);
  });
}

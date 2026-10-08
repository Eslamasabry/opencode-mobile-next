import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/phone_server_idle.dart';

class _Linux extends BuiltinLinux {
  _Linux({bool helper = true}) : helperOriginallyLive = helper {
    wire['serverIdleHelperStopped'] = helper;
  }
  final bool helperOriginallyLive;
  final events = <String>[];
  final wire = <String, Object?>{
    'installed': true,
    'phase': 'ready',
    'serverRunning': false,
    'serverRestartWanted': true,
    'serverIdlePolicySupported': true,
    'serverIdleEnabled': true,
    'serverIdleMinutes': 5,
    'serverIdleStopped': true,
    'serverIdleHelperStopped': true,
    'serverIdleGeneration': 37,
  };
  final statusReplies = <Future<BuiltinLinuxStatus>>[];
  Future<BuiltinLinuxStatus>? resumeReply;
  Future<BuiltinLinuxStatus>? completeReply;
  int manualCalls = 0;
  int retryBudget = 3;
  BuiltinLinuxStatus get snapshot => BuiltinLinuxStatus.fromMap(wire);

  @override
  Future<BuiltinLinuxStatus> status() {
    events.add('status');
    return statusReplies.isEmpty
        ? Future.value(snapshot)
        : statusReplies.removeAt(0);
  }

  @override
  Future<BuiltinLinuxStatus> resumeIdleStoppedPhoneServer({
    required String profileId,
    required int expectedIdleGeneration,
  }) {
    events.add('resume:$profileId:$expectedIdleGeneration');
    wire['serverRunning'] = true;
    wire['serverIdleStopped'] = false;
    return resumeReply ?? Future.value(snapshot);
  }

  @override
  Future<BuiltinLinuxStatus> completePhoneServerIdleResume({
    required String profileId,
    required int expectedIdleGeneration,
  }) {
    events.add('complete:$profileId:$expectedIdleGeneration');
    wire['serverIdleHelperStopped'] = false;
    wire['serverIdleGeneration'] = helperOriginallyLive
        ? 0
        : expectedIdleGeneration;
    return completeReply ?? Future.value(snapshot);
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    manualCalls++;
    retryBudget = 0;
  }

  @override
  Future<void> restartServer(
    String script, {
    int port = 4097,
    required int expectedGeneration,
  }) async {
    manualCalls++;
  }

  @override
  Future<Map<Object?, Object?>> confirmManualServerStart(
    String profileId,
  ) async {
    manualCalls++;
    retryBudget = 0;
    return {};
  }
}

class _Fixture {
  _Fixture({bool helper = true}) : linux = _Linux(helper: helper) {
    idle = PhoneServerIdle(
      linux: linux,
      isForeground: () => foreground,
      ownerId: () => owner,
      isReadable: (_) => readable,
      changed: () => notifications++,
      resumeAgents:
          ({required profileId, required expectedIdleGeneration}) async {
            linux.events.add('agents:$profileId:$expectedIdleGeneration');
            await agentReply;
            linux.wire['serverIdleHelperStopped'] = false;
          },
    );
  }
  final _Linux linux;
  late final PhoneServerIdle idle;
  bool foreground = true, readable = true;
  String? owner = 'alias';
  Future<void>? agentReply;
  int notifications = 0;
}

void main() {
  void idleTest(String name, Future<void> Function(WidgetTester) body) {
    testWidgets(name, body);
  }

  idleTest(
    'guarded server then helper completion preserves the exhausted retry budget',
    (tester) async {
      final f = _Fixture();
      try {
        await f.idle.check();
        expect(f.linux.events, [
          'status',
          'resume:alias:37',
          'agents:alias:37',
          'status',
          'complete:alias:37',
        ]);
        expect(f.idle.failure, isNull);
        expect(f.idle.running, isFalse);
        expect(f.linux.retryBudget, 3);
        expect(f.linux.manualCalls, 0);
        expect(f.linux.snapshot.serverIdleGeneration, 0);
      } finally {
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest(
    'prior stopped helper is never resumed and completion retains its positive generation',
    (tester) async {
      final f = _Fixture(helper: false);
      try {
        await f.idle.check();
        expect(f.linux.events, [
          'status',
          'resume:alias:37',
          'status',
          'complete:alias:37',
        ]);
        expect(f.linux.snapshot.serverIdleGeneration, 37);
        expect(f.linux.manualCalls, 0);
        expect(f.idle.failure, isNull);
      } finally {
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest('already restored server only restores its owed helper', (
    tester,
  ) async {
    final f = _Fixture();
    f.linux.wire['serverRunning'] = true;
    f.linux.wire['serverIdleStopped'] = false;
    try {
      await f.idle.check();
      expect(f.linux.events, [
        'status',
        'agents:alias:37',
        'status',
        'complete:alias:37',
      ]);
      expect(f.idle.failure, isNull);
    } finally {
      f.idle.dispose();
      await tester.pump();
    }
  });

  idleTest(
    'unsupported and normal running receipts never authorize restoration',
    (tester) async {
      for (final unsupported in [true, false]) {
        final f = _Fixture();
        if (unsupported) {
          f.linux.wire.remove('serverIdlePolicySupported');
        } else {
          f.linux.wire['serverIdleStopped'] = false;
          f.linux.wire['serverIdleHelperStopped'] = false;
          f.linux.wire['serverIdleGeneration'] = 0;
        }
        try {
          await f.idle.check();
          expect(f.linux.events, ['status']);
          expect(f.idle.failure, isNull);
        } finally {
          f.idle.dispose();
          await tester.pump();
        }
      }
    },
  );

  idleTest('supported incomplete or malformed idle receipts fail closed', (
    tester,
  ) async {
    for (final bad in <Map<String, Object?>>[
      {'serverIdleGeneration': null},
      {'serverIdleGeneration': -1},
      {'serverIdleGeneration': 1.5},
      {'serverIdleGeneration': '37'},
      {'serverIdleGeneration': 0},
      {'serverIdleStopped': 'true'},
      {'serverIdleHelperStopped': null},
      {'serverIdleEnabled': null},
      {'serverIdleMinutes': 61},
    ]) {
      final f = _Fixture();
      f.linux.wire.addAll(bad);
      try {
        await f.idle.check();
        expect(f.linux.events, ['status']);
        expect(f.idle.failure, 'idle_resume_unavailable');
        expect(f.linux.manualCalls, 0);
      } finally {
        f.idle.dispose();
        await tester.pump();
      }
    }
  });

  idleTest('initial Stop foreground and owner guards prevent every launch', (
    tester,
  ) async {
    for (final guard in ['stop', 'background', 'missing-owner', 'unreadable']) {
      final f = _Fixture();
      if (guard == 'stop') f.linux.wire['serverRestartWanted'] = false;
      if (guard == 'background') f.foreground = false;
      if (guard == 'missing-owner') f.owner = null;
      if (guard == 'unreadable') f.readable = false;
      try {
        await f.idle.check();
        expect(f.linux.events, guard == 'stop' ? ['status'] : isEmpty);
        expect(f.linux.manualCalls, 0);
      } finally {
        f.idle.dispose();
        await tester.pump();
      }
    }
  });

  idleTest('same owner check coalesces while status is pending', (
    tester,
  ) async {
    final f = _Fixture();
    final gate = Completer<BuiltinLinuxStatus>();
    f.linux.statusReplies.add(gate.future);
    try {
      final first = f.idle.check();
      final second = f.idle.check();
      expect(identical(first, second), isTrue);
      expect(f.idle.running, isTrue);
      expect(f.linux.events, ['status']);
      gate.complete(f.linux.snapshot);
      await first;
      expect(
        f.linux.events.where((e) => e.startsWith('complete:')),
        hasLength(1),
      );
      expect(f.idle.running, isFalse);
    } finally {
      f.idle.dispose();
      await tester.pump();
    }
  });

  idleTest(
    'owner or foreground change after the first read cancels continuation',
    (tester) async {
      for (final guard in ['owner', 'background', 'unreadable', 'dispose']) {
        final f = _Fixture();
        final gate = Completer<BuiltinLinuxStatus>();
        f.linux.statusReplies.add(gate.future);
        final pending = f.idle.check();
        if (guard == 'owner') f.owner = 'replacement';
        if (guard == 'background') f.foreground = false;
        if (guard == 'unreadable') f.readable = false;
        if (guard == 'dispose') f.idle.dispose();
        final notifications = f.notifications;
        gate.complete(f.linux.snapshot);
        await pending;
        expect(f.linux.events, ['status']);
        if (guard == 'dispose') expect(f.notifications, notifications);
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest(
    'owner change during native server restore cannot start its helper',
    (tester) async {
      final f = _Fixture();
      final gate = Completer<BuiltinLinuxStatus>();
      f.linux.resumeReply = gate.future;
      final pending = f.idle.check();
      await tester.pump();
      f.owner = 'replacement';
      gate.complete(f.linux.snapshot);
      await pending;
      expect(f.linux.events, ['status', 'resume:alias:37']);
      f.idle.dispose();
      await tester.pump();
    },
  );

  idleTest(
    'background or disposal during helper readiness prevents completion',
    (tester) async {
      for (final dispose in [false, true]) {
        final f = _Fixture();
        final gate = Completer<void>();
        f.agentReply = gate.future;
        final pending = f.idle.check();
        await tester.pump();
        if (dispose) {
          f.idle.dispose();
        } else {
          f.foreground = false;
        }
        gate.complete();
        await pending;
        expect(f.linux.events, [
          'status',
          'resume:alias:37',
          'agents:alias:37',
        ]);
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest(
    'Stop or changed generation during helper readiness prevents native completion',
    (tester) async {
      for (final stop in [false, true]) {
        final f = _Fixture();
        final gate = Completer<void>();
        f.agentReply = gate.future;
        final pending = f.idle.check();
        await tester.pump();
        f.linux.wire[stop ? 'serverRestartWanted' : 'serverIdleGeneration'] =
            stop ? false : 38;
        gate.complete();
        await pending;
        expect(f.linux.events, [
          'status',
          'resume:alias:37',
          'agents:alias:37',
          'status',
        ]);
        expect(f.idle.failure, 'idle_resume_unavailable');
        expect(f.linux.manualCalls, 0);
        expect(f.linux.retryBudget, 3);
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest(
    'native or helper failure exposes only a fixed code without fallback',
    (tester) async {
      for (final native in [true, false]) {
        final f = _Fixture();
        final nativeGate = Completer<BuiltinLinuxStatus>();
        final agentGate = Completer<void>();
        if (native) {
          f.linux.resumeReply = nativeGate.future;
        } else {
          f.agentReply = agentGate.future;
        }
        final pending = f.idle.check();
        await tester.pump();
        if (native) {
          nativeGate.completeError(StateError('synthetic private details'));
        } else {
          agentGate.completeError(StateError('synthetic private details'));
        }
        await pending;
        expect(f.idle.failure, 'idle_resume_unavailable');
        expect(f.linux.events.any((e) => e.startsWith('complete:')), isFalse);
        expect(f.linux.manualCalls, 0);
        expect(f.linux.retryBudget, 3);
        f.idle.dispose();
        await tester.pump();
      }
    },
  );

  idleTest(
    'foreground loss during the final fresh receipt prevents completion',
    (tester) async {
      final f = _Fixture();
      final gate = Completer<BuiltinLinuxStatus>();
      f.linux.statusReplies.addAll([
        Future.value(f.linux.snapshot),
        gate.future,
      ]);
      final pending = f.idle.check();
      await tester.pump();
      expect(f.linux.events.last, 'status');
      f.foreground = false;
      gate.complete(f.linux.snapshot);
      await pending;
      expect(f.linux.events.any((e) => e.startsWith('complete:')), isFalse);
      expect(f.idle.failure, isNull);
      f.idle.dispose();
      await tester.pump();
    },
  );

  idleTest(
    'late native completion after disposal cannot publish a failure or change',
    (tester) async {
      final f = _Fixture();
      final gate = Completer<BuiltinLinuxStatus>();
      f.linux.completeReply = gate.future;
      final pending = f.idle.check();
      await tester.pump();
      expect(f.linux.events.last, 'complete:alias:37');
      f.idle.dispose();
      final notifications = f.notifications;
      gate.complete(const BuiltinLinuxStatus.absent());
      await pending;
      expect(f.notifications, notifications);
      expect(f.idle.running, isFalse);
      expect(f.idle.failure, isNull);
      await tester.pump();
    },
  );

  idleTest(
    'invalidated stale check cannot finish or clear a newer pending check',
    (tester) async {
      final f = _Fixture();
      final old = Completer<BuiltinLinuxStatus>(),
          next = Completer<BuiltinLinuxStatus>();
      f.linux.statusReplies.addAll([old.future, next.future]);
      final first = f.idle.check();
      f.idle.invalidate();
      final second = f.idle.check();
      old.complete(f.linux.snapshot);
      await first;
      expect(f.idle.running, isTrue);
      expect(f.linux.events, ['status', 'status']);
      next.complete(f.linux.snapshot);
      await second;
      expect(f.idle.running, isFalse);
      expect(f.idle.failure, isNull);
      f.idle.dispose();
      await tester.pump();
    },
  );
}

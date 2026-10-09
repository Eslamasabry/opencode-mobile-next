import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/phone_agent_work_watch.dart';

typedef _Call = (String, String, bool, Duration);

class _LeaseFake {
  final calls = <_Call>[];
  final active = <String>{'setup', 'sign-in', 'terminal'};
  final logical = <String>{};
  bool get logicalWorkBusy => logical.isNotEmpty;
  Iterable<_Call> get acquisitions =>
      calls.where((call) => call.$3 && call.$4 > Duration.zero);
  Future<BuiltinWorkLeaseStatus> Function(_Call)? answer;
  Future<BuiltinWorkLeaseStatus> hold(
    String profile,
    String id,
    bool on,
    Duration ttl,
  ) async {
    final call = (profile, id, on, ttl);
    calls.add(call);
    if (on && ttl > Duration.zero) logical.add(id);
    final result = answer == null
        ? BuiltinWorkLeaseStatus(held: on && ttl > Duration.zero)
        : await answer!(call);
    if (!on) logical.remove(id);
    if (!on || ttl <= Duration.zero || result.capped) active.remove(id);
    if (on && ttl > Duration.zero && result.held) active.add(id);
    if (on && ttl <= Duration.zero) return const BuiltinWorkLeaseStatus();
    return result;
  }
}

void main() {
  late DateTime now;
  late _LeaseFake leases;
  late PhoneAgentWorkWatch watch;
  void start() {
    now = DateTime.utc(2026, 10, 8);
    leases = _LeaseFake();
    watch = PhoneAgentWorkWatch(hold: leases.hold, now: () => now);
  }

  void workTest(String name, Future<void> Function(WidgetTester) body) {
    testWidgets(name, (tester) async {
      // Both the queue's initial Future and its timer belong to fakeAsync.
      start();
      try {
        await body(tester);
      } finally {
        watch.dispose();
        await tester.pump();
      }
    });
  }

  workTest('unknown never creates a hold; known busy renews the same ID', (
    tester,
  ) async {
    watch.observe(profileId: 'owner', busy: null);
    await tester.pump();
    expect(leases.calls, isEmpty);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    final id = leases.calls.single.$2;
    expect(RegExp(r'^[A-Za-z0-9_.-]{1,80}$').hasMatch(id), isTrue);
    expect(leases.calls.single.$4, const Duration(minutes: 15));
    watch.observe(profileId: 'owner', busy: true);
    watch.observe(profileId: 'owner', busy: null);
    now = now.add(const Duration(minutes: 5));
    await tester.pump(const Duration(minutes: 5));
    expect(leases.calls.map((c) => c.$3), [true, true]);
    expect(leases.calls.map((c) => c.$2).toSet(), {id});
    watch.observe(profileId: 'owner', busy: false);
    await tester.pump();
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
  });

  workTest('idle before queued on skips obsolete acquire but still sends off', (
    tester,
  ) async {
    watch.observe(profileId: 'owner', busy: true);
    watch.observe(profileId: 'owner', busy: false);
    await tester.pump();
    expect(leases.calls.map((c) => c.$3), [false]);
  });

  workTest('late acquire after idle is drained before the next busy run', (
    tester,
  ) async {
    final pending = Completer<BuiltinWorkLeaseStatus>();
    leases.answer = (c) => c.$3 && leases.calls.length == 1
        ? pending.future
        : Future.value(BuiltinWorkLeaseStatus(held: c.$3));
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    final old = leases.calls.first.$2;
    watch.observe(profileId: 'owner', busy: false);
    watch.observe(profileId: 'owner', busy: true);
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(leases.active, isNot(contains(old)));
    expect(leases.calls.last.$3, isTrue);
    expect(leases.calls.last.$2, isNot(old));
    expect(leases.calls.where((c) => c.$2 == old && !c.$3), isNotEmpty);
  });

  workTest('late acquire after dispose releases only its own ID', (
    tester,
  ) async {
    final pending = Completer<BuiltinWorkLeaseStatus>();
    leases.answer = (c) =>
        c.$3 ? pending.future : Future.value(const BuiltinWorkLeaseStatus());
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    watch.dispose();
    watch.observe(profileId: 'owner', busy: true);
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(leases.acquisitions, hasLength(1));
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
    expect(tester.takeException(), isNull);
  });

  workTest('owner switch drains delayed old hold and uses a new opaque ID', (
    tester,
  ) async {
    final pending = Completer<BuiltinWorkLeaseStatus>();
    leases.answer = (c) => c.$1 == 'old' && c.$3
        ? pending.future
        : Future.value(BuiltinWorkLeaseStatus(held: c.$3));
    watch.observe(profileId: 'old', busy: true);
    await tester.pump();
    final old = leases.calls.first.$2;
    watch.observe(profileId: 'new', busy: true);
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(leases.calls.last.$1, 'new');
    expect(leases.calls.last.$2, isNot(old));
    expect(leases.active, isNot(contains(old)));
    watch.observe(profileId: null, busy: true);
    await tester.pump();
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
  });

  workTest('rapid idle busy retains mandatory old off before new on', (
    tester,
  ) async {
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    watch.observe(profileId: 'owner', busy: false);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.calls.map((c) => c.$3), [true, false, true]);
    expect(leases.calls.first.$2, isNot(leases.calls.last.$2));
  });

  workTest('native cap never reopens on true or unknown until confirmed idle', (
    tester,
  ) async {
    leases.answer = (c) async => BuiltinWorkLeaseStatus(capped: c.$3);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    watch.observe(profileId: 'owner', busy: null);
    watch.observe(profileId: 'owner', busy: true);
    now = now.add(const Duration(hours: 7));
    await tester.pump(const Duration(hours: 7));
    expect(leases.calls.map((c) => c.$3), [true]);
    expect(leases.logicalWorkBusy, isTrue);
    watch.observe(profileId: 'owner', busy: false);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.calls.map((c) => c.$3), [true, false, true]);
    expect(leases.calls.last.$2, isNot(leases.calls.first.$2));
  });

  workTest('six-hour cap shortens TTL and releases without busy reopening', (
    tester,
  ) async {
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    now = now.add(const Duration(hours: 5, minutes: 55));
    await tester.pump(const Duration(hours: 5, minutes: 55));
    expect(leases.calls.last.$4, const Duration(minutes: 5));
    now = now.add(const Duration(minutes: 5));
    await tester.pump(const Duration(minutes: 5));
    expect(leases.calls.last.$3, isTrue);
    expect(leases.calls.last.$4, Duration.zero);
    expect(leases.logicalWorkBusy, isTrue);
    final count = leases.calls.length;
    watch.observe(profileId: 'owner', busy: null);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump(const Duration(minutes: 5));
    expect(leases.calls, hasLength(count));
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
  });

  workTest('backward and negative clock fail closed until known idle', (
    tester,
  ) async {
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    now = now.subtract(const Duration(seconds: 1));
    watch.observe(profileId: 'owner', busy: null);
    await tester.pump();
    expect(leases.calls.last.$3, isTrue);
    expect(leases.calls.last.$4, Duration.zero);
    expect(leases.logicalWorkBusy, isTrue);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.acquisitions, hasLength(1));
    watch.observe(profileId: 'owner', busy: false);
    now = DateTime.fromMillisecondsSinceEpoch(-1, isUtc: true);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.acquisitions, hasLength(1));
    expect(leases.logicalWorkBusy, isFalse);
  });

  workTest('six-hour limit drains an acquisition still waiting for native', (
    tester,
  ) async {
    final pending = Completer<BuiltinWorkLeaseStatus>();
    leases.answer = (c) =>
        c.$3 ? pending.future : Future.value(const BuiltinWorkLeaseStatus());
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    now = now.add(const Duration(hours: 6));
    await tester.pump(const Duration(hours: 6));
    pending.complete(const BuiltinWorkLeaseStatus(held: true));
    await tester.pump();
    expect(leases.acquisitions, hasLength(1));
    expect(leases.calls.last.$3, isTrue);
    expect(leases.calls.last.$4, Duration.zero);
    expect(leases.logicalWorkBusy, isTrue);
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.acquisitions, hasLength(1));
  });

  workTest('refused renewal releases the old token and stops retries', (
    tester,
  ) async {
    var onCalls = 0;
    leases.answer = (c) async =>
        BuiltinWorkLeaseStatus(held: c.$3 && ++onCalls == 1);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    now = now.add(const Duration(minutes: 5));
    await tester.pump(const Duration(minutes: 5));
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
    expect(leases.logicalWorkBusy, isTrue);
    expect(leases.calls.last.$4, Duration.zero);
    final count = leases.calls.length;
    watch.observe(profileId: 'owner', busy: true);
    watch.observe(profileId: 'owner', busy: null);
    await tester.pump(const Duration(minutes: 10));
    expect(leases.calls, hasLength(count));
  });

  workTest(
    'combined busy survives CPU closure until every run is actually idle',
    (tester) async {
      watch.observe(profileId: 'owner', busy: true);
      await tester.pump();
      now = now.add(const Duration(hours: 6));
      await tester.pump(const Duration(hours: 6));
      expect(leases.active, {'setup', 'sign-in', 'terminal'});
      expect(leases.logicalWorkBusy, isTrue);
      final other = PhoneAgentWorkWatch(hold: leases.hold, now: () => now);
      try {
        other.observe(profileId: 'owner', busy: true);
        await tester.pump();
        watch.observe(profileId: 'owner', busy: null);
        watch.observe(profileId: 'owner', busy: true);
        await tester.pump();
        expect(leases.logical, hasLength(2));
        watch.observe(profileId: 'owner', busy: false);
        await tester.pump();
        expect(leases.logicalWorkBusy, isTrue);
        other.observe(profileId: 'owner', busy: false);
        await tester.pump();
        expect(leases.logicalWorkBusy, isFalse);
        expect(leases.active, {'setup', 'sign-in', 'terminal'});
      } finally {
        other.dispose();
        await tester.pump();
      }
    },
  );

  workTest('late capped result after clock closure retains its busy key', (
    tester,
  ) async {
    final pending = Completer<BuiltinWorkLeaseStatus>();
    leases.answer = (c) => c.$3 && c.$4 > Duration.zero
        ? pending.future
        : Future.value(const BuiltinWorkLeaseStatus());
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    final id = leases.calls.single.$2;
    now = now.subtract(const Duration(seconds: 1));
    watch.observe(profileId: 'owner', busy: null);
    pending.complete(const BuiltinWorkLeaseStatus(capped: true));
    await tester.pump();
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
    expect(leases.logical, {id});
    expect(
      leases.calls.skip(1).every((c) => c.$3 && c.$4 == Duration.zero),
      isTrue,
    );
    watch.observe(profileId: 'owner', busy: false);
    await tester.pump();
    expect(leases.calls.last.$3, isFalse);
    expect(leases.logicalWorkBusy, isFalse);
  });

  workTest(
    'late result for obsolete closed owner sends off rather than zero hold',
    (tester) async {
      final pending = Completer<BuiltinWorkLeaseStatus>();
      leases.answer = (c) => c.$1 == 'old' && c.$3 && c.$4 > Duration.zero
          ? pending.future
          : Future.value(BuiltinWorkLeaseStatus(held: c.$3));
      watch.observe(profileId: 'old', busy: true);
      await tester.pump();
      final oldId = leases.calls.single.$2;
      now = now.add(const Duration(hours: 6));
      await tester.pump(const Duration(hours: 6));
      watch.observe(profileId: 'new', busy: true);
      pending.complete(const BuiltinWorkLeaseStatus(held: true));
      await tester.pump();
      expect(leases.logical, isNot(contains(oldId)));
      expect(
        leases.calls.where((c) => c.$2 == oldId).skip(1).every((c) => !c.$3),
        isTrue,
      );
      expect(leases.calls.last.$1, 'new');
      expect(leases.acquisitions, hasLength(2));
    },
  );

  workTest('independent watchers never release each other', (tester) async {
    final other = PhoneAgentWorkWatch(hold: leases.hold, now: () => now);
    try {
      watch.observe(profileId: 'owner', busy: true);
      other.observe(profileId: 'owner', busy: true);
      await tester.pump();
      expect(leases.calls.map((c) => c.$2).toSet(), hasLength(2));
      watch.dispose();
      await tester.pump();
      expect(leases.active, hasLength(4));
      other.dispose();
      await tester.pump();
      expect(leases.active, {'setup', 'sign-in', 'terminal'});
    } finally {
      other.dispose();
      await tester.pump();
    }
  });

  workTest('partial acquire and release exceptions cannot poison later work', (
    tester,
  ) async {
    var firstOn = true, firstOff = true;
    leases.answer = (c) async {
      if (c.$3 && firstOn) {
        firstOn = false;
        leases.active.add(c.$2);
        throw StateError('synthetic failure');
      }
      if (!c.$3 && firstOff) {
        firstOff = false;
        throw StateError('synthetic failure');
      }
      return BuiltinWorkLeaseStatus(held: c.$3);
    };
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    watch.observe(profileId: 'owner', busy: false);
    watch.observe(profileId: 'owner', busy: true);
    await tester.pump();
    expect(leases.calls.last.$3, isTrue);
    watch.dispose();
    await tester.pump();
    expect(leases.active, {'setup', 'sign-in', 'terminal'});
    expect(tester.takeException(), isNull);
  });
}

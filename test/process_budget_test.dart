import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/process_budget.dart';
import 'package:opencode_mobile/platform/thermal.dart';

void main() {
  group('the rules', () {
    BudgetWait? decide(
      int running, {
      bool lifted = false,
      HeavyJob job = HeavyJob.agentSession,
      ThermalReading thermal = ThermalReading.unknown,
      BatteryReading battery = BatteryReading.unknown,
    }) => ProcessBudget.decide(
      job: job,
      running: running,
      limitLifted: lifted,
      thermal: thermal,
      battery: battery,
    );

    test('a new agent starts only with room under 32, less a reserve', () {
      // 32 - 4 reserve - 24 running = 4: an agent session (4) fits.
      expect(decide(24), isNull);
      expect(decide(25), BudgetWait.room);
      expect(decide(14, job: HeavyJob.aiTeam), isNull);
      expect(decide(23, job: HeavyJob.aiTeam), BudgetWait.room);
    });

    test('with Android\'s limit off the app still has its own ceiling', () {
      expect(decide(25, lifted: true), isNull);
      expect(decide(40, lifted: true), isNull);
      // Never unlimited: 48 - 4 - 41 = 3 < 4.
      expect(decide(41, lifted: true), BudgetWait.room);
      expect(decide(200, lifted: true), BudgetWait.room);
    });

    test('a hot phone holds new work even with room to spare', () {
      const severe = ThermalReading(status: ThermalStatus.severe);
      const forecast = ThermalReading(
        status: ThermalStatus.moderate,
        headroom: 0.97,
      );
      expect(decide(3, thermal: severe), BudgetWait.warm);
      expect(decide(3, thermal: forecast), BudgetWait.warm);
      expect(decide(3, lifted: true, thermal: severe), BudgetWait.warm);
      expect(
        decide(3, thermal: const ThermalReading(status: ThermalStatus.light)),
        isNull,
      );
    });

    test('a low battery that is not charging holds new work', () {
      expect(
        decide(3, battery: const BatteryReading(level: 15, charging: false)),
        BudgetWait.battery,
      );
      expect(
        decide(3, battery: const BatteryReading(level: 20, charging: false)),
        BudgetWait.battery,
      );
      expect(
        decide(3, battery: const BatteryReading(level: 15, charging: true)),
        isNull,
      );
      expect(
        decide(3, battery: const BatteryReading(level: 21, charging: false)),
        isNull,
      );
      // Unknown never blocks.
      expect(decide(3, battery: const BatteryReading(level: 5)), isNull);
    });

    test('battery readings from the kernel files', () {
      expect(
        BatteryReading.fromSysfs('17\n', 'Discharging\n'),
        const BatteryReading(level: 17, charging: false),
      );
      expect(
        BatteryReading.fromSysfs('100', 'Full'),
        const BatteryReading(level: 100, charging: true),
      );
      expect(BatteryReading.fromSysfs(null, null), BatteryReading.unknown);
    });
  });

  group('Android\'s own stops', () {
    final now = DateTime.utc(2026, 11, 1, 3);
    BuiltinServiceDiagnostics exit(
      Duration ago, {
      bool requested = false,
      BuiltinServiceExitReason reason =
          BuiltinServiceExitReason.memoryOrPhantomKill,
    }) => BuiltinServiceDiagnostics(
      lastExitAtMs: now.subtract(ago).millisecondsSinceEpoch,
      lastStopRequested: requested,
      exitReason: reason,
    );

    test('a recent unrequested SIGKILL counts; others do not', () {
      expect(
        lastAndroidKill(
          BuiltinPerformance(
            services: {
              'agent-host.p': exit(const Duration(minutes: 3)),
              'quota': exit(const Duration(minutes: 1), requested: true),
              'aiteam': exit(
                const Duration(seconds: 30),
                reason: BuiltinServiceExitReason.exited,
              ),
            },
          ),
          now: now,
        ),
        now.subtract(const Duration(minutes: 3)),
      );
      expect(
        lastAndroidKill(
          BuiltinPerformance(
            services: {'agent-host.p': exit(const Duration(hours: 25))},
          ),
          now: now,
        ),
        isNull,
      );
      expect(lastAndroidKill(const BuiltinPerformance(), now: now), isNull);
    });
  });

  group('the guard', () {
    late int? count;
    late ThermalReading thermal;
    late BatteryReading battery;
    late bool lifted;

    ProcessBudgetGuard guard() => ProcessBudgetGuard(
      count: () async => count,
      thermal: () async => thermal,
      battery: () async => battery,
      limitLifted: () async => lifted,
      agents: () async => 2,
      enabled: true,
      pollEvery: const Duration(hours: 1),
    );

    setUp(() {
      count = 10;
      thermal = ThermalReading.unknown;
      battery = BatteryReading.unknown;
      lifted = false;
    });

    test('queues work near the limit and starts it when room frees', () async {
      final g = guard();
      count = 30;
      var started = false;
      final admitted = g
          .admit(HeavyJob.agentSession)
          .then((_) => started = true);
      await pumpEventQueue();
      expect(started, isFalse);
      expect(g.wait, BudgetWait.room);
      expect(g.waiting.single.job, HeavyJob.agentSession);
      expect(g.lastCount, 30);
      expect(g.runningAgents, 2);
      await g.check();
      expect(started, isFalse, reason: 'still no room');
      count = 12;
      await g.check();
      await admitted;
      expect(started, isTrue);
      expect(g.wait, isNull);
      g.dispose();
    });

    test('heat queues work below the cap, and it starts once cool', () async {
      final g = guard();
      thermal = const ThermalReading(status: ThermalStatus.severe);
      var started = false;
      final admitted = g.admit(HeavyJob.aiTeam).then((_) => started = true);
      await pumpEventQueue();
      expect(g.wait, BudgetWait.warm);
      thermal = const ThermalReading(status: ThermalStatus.light);
      await g.check();
      await admitted;
      expect(started, isTrue);
      g.dispose();
    });

    test('low battery queues work, charging lets it start', () async {
      final g = guard();
      battery = const BatteryReading(level: 9, charging: false);
      final admitted = g.admit(HeavyJob.setupJob);
      await pumpEventQueue();
      expect(g.wait, BudgetWait.battery);
      battery = const BatteryReading(level: 9, charging: true);
      await g.check();
      await admitted;
      expect(g.wait, isNull);
      g.dispose();
    });

    test('the cap still applies with Android\'s limit turned off', () async {
      final g = guard();
      lifted = true;
      count = 30;
      await g.admit(HeavyJob.agentSession); // 30 + 4 fits under 48 - 4
      expect(g.lastCeiling, 48);
      count = 42;
      final admitted = g.admit(HeavyJob.agentSession);
      await pumpEventQueue();
      expect(g.wait, BudgetWait.room);
      count = 20;
      await g.check();
      await admitted;
      g.dispose();
    });

    test('work admitted moments ago counts before it shows', () async {
      final g = guard();
      count = 17;
      await g.admit(HeavyJob.agentSession); // 17 + 4 = 21
      await g.admit(HeavyJob.agentSession); // 21 + 4 = 25
      final third = g.admit(HeavyJob.agentSession);
      await pumpEventQueue();
      expect(g.wait, BudgetWait.room, reason: '25 + 4 > 28');
      g.dispose();
      await expectLater(third, throwsA(isA<BudgetCancelled>()));
    });

    test('a cancelled wait ends with BudgetCancelled', () async {
      final g = guard();
      count = 31;
      final cancel = Completer<void>();
      final admitted = g.admit(HeavyJob.agentSession, cancelled: cancel.future);
      await pumpEventQueue();
      expect(g.waiting, hasLength(1));
      cancel.complete();
      await expectLater(admitted, throwsA(isA<BudgetCancelled>()));
      expect(g.waiting, isEmpty);
      g.dispose();
    });

    test('a phone it cannot read never blocks work', () async {
      final g = guard();
      count = null;
      await g.admit(HeavyJob.agentSession);
      expect(g.waiting, isEmpty);
      g.dispose();
    });

    test('waiting for room twice in an hour reads as often', () async {
      final g = guard();
      count = 31;
      final first = g.admit(HeavyJob.agentSession);
      await pumpEventQueue();
      expect(g.queuesOften, isFalse);
      final second = g.admit(HeavyJob.agentSession);
      await pumpEventQueue();
      expect(g.queuesOften, isTrue);
      count = 0;
      await g.check();
      await Future.wait([first, second]);
      g.dispose();
    });
  });
}

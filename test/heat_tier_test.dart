import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/heat_tier.dart';
import 'package:opencode_mobile/platform/thermal.dart';

ThermalReading r(ThermalStatus status, [double? headroom]) =>
    ThermalReading(status: status, headroom: headroom);

void main() {
  group('HeatTierPolicy', () {
    test('a cool phone with a good battery runs at full speed', () {
      expect(
        HeatTierPolicy.decide(
          thermal: r(ThermalStatus.none, 0.3),
          batteryLevel: 80,
          charging: false,
        ),
        HeatTier.normal,
      );
    });

    test('a rising forecast slows the agents before any pause', () {
      final tier = HeatTierPolicy.decide(thermal: r(ThermalStatus.none, 0.75));
      expect(tier, HeatTier.slow);
      expect(tier.slowsAgents, isTrue);
      expect(tier.holdsNewWork, isFalse);
    });

    test('a near-severe forecast or moderate status also holds new work', () {
      expect(
        HeatTierPolicy.decide(thermal: r(ThermalStatus.none, 0.92)),
        HeatTier.hold,
      );
      expect(
        HeatTierPolicy.decide(thermal: r(ThermalStatus.moderate)),
        HeatTier.hold,
      );
    });

    test('low battery counts only when unplugged', () {
      expect(
        HeatTierPolicy.decide(
          thermal: r(ThermalStatus.none),
          batteryLevel: 18,
          charging: false,
        ),
        HeatTier.slow,
      );
      expect(
        HeatTierPolicy.decide(
          thermal: r(ThermalStatus.none),
          batteryLevel: 18,
          charging: true,
        ),
        HeatTier.normal,
      );
      expect(
        HeatTierPolicy.decide(
          thermal: r(ThermalStatus.none),
          batteryLevel: 8,
          charging: false,
        ),
        HeatTier.hold,
      );
      expect(
        HeatTierPolicy.decide(thermal: r(ThermalStatus.none), batteryLevel: 8),
        HeatTier.normal,
        reason: 'an unknown charging state is not guessed',
      );
    });

    test('Android battery saver slows the tools', () {
      expect(
        HeatTierPolicy.decide(thermal: r(ThermalStatus.none), powerSave: true),
        HeatTier.slow,
      );
    });

    test('out of sight slows the tools', () {
      expect(
        HeatTierPolicy.decide(thermal: r(ThermalStatus.none), hidden: true),
        HeatTier.slow,
      );
    });

    test('Always full speed turns every step off', () {
      expect(
        HeatTierPolicy.decide(
          thermal: r(ThermalStatus.moderate, 0.95),
          batteryLevel: 5,
          charging: false,
          hidden: true,
          fullSpeed: true,
        ),
        HeatTier.normal,
      );
    });
  });

  group('AgentThrottle', () {
    late ValueNotifier<bool> hidden;
    late DateTime now;
    late List<int> niceCalls;
    late BatterySnapshot battery;
    var full = false;
    var saver = false;
    int? answer;

    AgentThrottle make() => AgentThrottle(
      fullSpeed: () => full,
      run: (nice) async {
        niceCalls.add(nice);
        return answer;
      },
      battery: () async => battery,
      powerSave: () async => saver,
      hidden: hidden,
      clock: () => now,
      mark: (name, {attrs = const {}}) {},
    );

    setUp(() {
      hidden = ValueNotifier(false);
      now = DateTime(2026, 11, 1, 12);
      niceCalls = [];
      battery = const BatterySnapshot(level: 80, charging: true);
      full = false;
      saver = false;
      answer = 4;
    });

    test('lowers priority on a rising forecast, restores after calm', () async {
      final t = make();
      await t.observe(r(ThermalStatus.none, 0.8));
      expect(niceCalls, [AgentThrottle.slowNice]);
      expect(t.tier, HeatTier.slow);

      // A cooler reading alone is not enough: calm has to last.
      now = now.add(const Duration(seconds: 30));
      await t.observe(r(ThermalStatus.none, 0.3));
      expect(t.tier, HeatTier.slow);
      expect(niceCalls, [AgentThrottle.slowNice]);

      // Still too early, a warm reading restarts the wait.
      now = now.add(const Duration(seconds: 60));
      await t.observe(r(ThermalStatus.none, 0.8));
      now = now.add(const Duration(seconds: 90));
      await t.observe(r(ThermalStatus.none, 0.3));
      expect(t.tier, HeatTier.slow, reason: 'no flapping');

      now = now.add(HeatTierPolicy.calmFor + const Duration(seconds: 1));
      await t.observe(r(ThermalStatus.none, 0.3));
      expect(t.tier, HeatTier.normal);
      expect(niceCalls.last, 0);
      t.dispose();
    });

    test('battery saver slows the agents', () async {
      final t = make();
      saver = true;
      await t.observe(r(ThermalStatus.none, 0.1));
      expect(t.tier, HeatTier.slow);
      t.dispose();
    });

    test('goes up at once, to hold', () async {
      final t = make();
      await t.observe(r(ThermalStatus.none, 0.8));
      await t.observe(r(ThermalStatus.moderate, 0.95));
      expect(t.tier, HeatTier.hold);
      expect(niceCalls, [AgentThrottle.slowNice, AgentThrottle.holdNice]);
      t.dispose();
    });

    test('going out of sight slows, coming back restores after calm', () async {
      final t = make();
      await t.observe(r(ThermalStatus.none, 0.2));
      expect(niceCalls, isEmpty);
      hidden.value = true;
      await t.evaluate();
      expect(t.tier, HeatTier.slow);
      hidden.value = false;
      await t.evaluate(); // calm starts now
      now = now.add(HeatTierPolicy.calmFor + const Duration(seconds: 1));
      await t.evaluate();
      expect(t.tier, HeatTier.normal);
      expect(niceCalls, [AgentThrottle.slowNice, 0]);
      t.dispose();
    });

    test('Always full speed restores at once and stays normal', () async {
      final t = make();
      await t.observe(r(ThermalStatus.none, 0.8));
      full = true;
      await t.evaluate();
      expect(t.tier, HeatTier.normal);
      expect(niceCalls.last, 0);
      t.dispose();
    });

    test(
      'a failed run is tried again; a helper started later is caught',
      () async {
        final t = make();
        answer = null;
        await t.observe(r(ThermalStatus.none, 0.8));
        answer = 0; // no helper was running yet
        await t.observe(r(ThermalStatus.none, 0.8));
        expect(niceCalls.length, 2);
        // Applied, but set again after five minutes for helpers started since.
        now = now.add(AgentThrottle.reapplyEvery + const Duration(seconds: 1));
        await t.observe(r(ThermalStatus.none, 0.8));
        expect(niceCalls.length, 3);
        await t.observe(r(ThermalStatus.none, 0.8));
        expect(niceCalls.length, 3, reason: 'not on every reading');
        t.dispose();
      },
    );
  });

  group('agentNiceScript on a real /proc', () {
    final renice = Process.runSync('which', ['renice']);
    final linux = Platform.isLinux && renice.exitCode == 0;

    Future<int> niceOf(int pid) async {
      final out = await Process.run('ps', ['-o', 'ni=', '-p', '$pid']);
      return int.parse((out.stdout as String).trim());
    }

    test('lowers the helper, its children and nothing else', () async {
      // A stand-in helper whose command line reads like Paseo's, with a
      // child, and a bystander that must stay as it is.
      final helper = await Process.start('bash', [
        '-c',
        'exec -a "paseo daemon run --home /home/oc/.oc-profiles/t1" '
            'bash -c "sleep 30 & wait"',
      ]);
      final bystander = await Process.start('sleep', ['30']);
      try {
        await Future<void>.delayed(const Duration(milliseconds: 600));
        final children = await Process.run('pgrep', ['-P', '${helper.pid}']);
        final child = int.parse(
          (children.stdout as String).trim().split('\n').first,
        );
        final result = await Process.run('sh', ['-c', agentNiceScript(7)]);
        expect(result.stdout, contains('set '));
        expect(await niceOf(helper.pid), 7);
        expect(await niceOf(child), 7);
        expect(await niceOf(bystander.pid), 0);
        final grandchildren = await Process.run('pgrep', ['-P', '$child']);
        for (final line
            in (grandchildren.stdout as String)
                .split('\n')
                .where((l) => l.trim().isNotEmpty)) {
          expect(await niceOf(int.parse(line)), 7);
        }
      } finally {
        helper.kill();
        bystander.kill();
      }
    }, skip: !linux);

    test('says none when no helper runs', () async {
      final result = await Process.run('sh', ['-c', agentNiceScript(7)]);
      expect(result.stdout.toString().trim(), 'none');
    }, skip: !linux);
  });
}

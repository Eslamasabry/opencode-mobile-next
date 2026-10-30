import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/phone_process_watch.dart';
import 'package:opencode_mobile/builtin/phone_processes.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/widgets/running_on_phone_row.dart';
import 'package:opencode_mobile/ui/widgets/running_on_phone_text.dart';

import 'support/running_on_phone_fakes.dart';

void main() {
  final l10n = lookupAppLocalizations(const Locale('en'));
  const step = Duration(seconds: 5);

  PhoneProcessSnapshot snap(List<PhoneProcess> processes) =>
      PhoneProcessSnapshot(processes: processes);

  group('groups', () {
    test('each process sits under what started it, as a tree', () {
      final groups = groupPhoneProcesses(hotProcesses());
      expect(groups.map((g) => g.id), [
        'leftBehind',
        'openCode',
        'claudeCode:$chatSession',
        'agentHelper',
        'terminal',
        'app',
      ]);
      final chat = groups[2];
      expect(chat.session, chatSession);
      // The conversation's tools carry no session; they follow claude's.
      expect(chat.nodes.map((n) => (n.pid, n.depth)), [
        (10220, 0),
        (10230, 1),
        (10240, 2),
      ]);
      final helper = groups[3];
      expect(helper.nodes.map((n) => (n.pid, n.depth)), [
        (10200, 0),
        (10210, 1),
      ]);
      final left = groups.first;
      expect(left.nodes.map((n) => (n.pid, n.depth)), [
        (15001, 0),
        (15002, 1),
        (15003, 2),
        (15004, 2),
        (15005, 2),
        (15006, 2),
      ]);
    });

    test('server roots and the app are never stoppable; the rest are', () {
      final nodes = {
        for (final g in groupPhoneProcesses(normalProcesses()))
          for (final n in g.nodes) n.pid: n,
      };
      for (final root in [10100, 10200, 10300]) {
        expect(nodes[root]!.root, isTrue, reason: '$root');
        expect(nodes[root]!.stoppable, isFalse, reason: '$root');
      }
      expect(nodes[9000]!.stoppable, isFalse);
      for (final child in [10110, 10121, 10220, 10240, 10310]) {
        expect(nodes[child]!.stoppable, isTrue, reason: '$child');
      }
    });

    test('a parent loop in a bad reading still shows every process', () {
      final groups = groupPhoneProcesses([
        proc(1, 2, 'a', owner: PhoneProcessOwner.other),
        proc(2, 1, 'b', owner: PhoneProcessOwner.other),
      ]);
      expect(groups.single.nodes.map((n) => n.pid), unorderedEquals([1, 2]));
    });
  });

  group('heavy', () {
    test('one busy reading is never heavy, however long it ran', () {
      final clock = FakeClock();
      final watch = PhoneProcessWatch(clock: () => clock.now);
      final reading = watch.record(
        snap([proc(1, 0, 'dart', cpu: 99, elapsed: const Duration(hours: 3))]),
        clock.now,
      );
      expect(reading.heavy, isEmpty);
    });

    test('heavy after two minutes over 80% across readings', () {
      final clock = FakeClock();
      final watch = PhoneProcessWatch(clock: () => clock.now);
      final busy = snap([proc(1, 0, 'dart', cpu: 95)]);
      PhoneProcessReading? reading;
      for (var i = 0; i < 24; i++) {
        reading = watch.record(busy, clock.now);
        expect(reading.heavy, isEmpty, reason: 'reading $i');
        clock.advance(step);
      }
      reading = watch.record(busy, clock.now);
      expect(reading.heavy.single.pid, 1);
      expect(reading.heavy.single.heavyFor, const Duration(minutes: 2));
    });

    test('a quiet reading or a gap between readings starts over', () {
      final clock = FakeClock();
      final watch = PhoneProcessWatch(clock: () => clock.now);
      final busy = snap([proc(1, 0, 'dart', cpu: 95)]);
      watch.record(busy, clock.now);
      clock.advance(const Duration(minutes: 1));
      // More than 30 s between readings proves nothing about the middle.
      watch.record(busy, clock.now);
      clock.advance(const Duration(seconds: 20));
      watch.record(busy, clock.now);
      clock.advance(const Duration(seconds: 20));
      watch.record(snap([proc(1, 0, 'dart', cpu: 10)]), clock.now);
      for (var i = 0; i < 20; i++) {
        clock.advance(step);
        expect(watch.record(busy, clock.now).heavy, isEmpty);
      }
    });

    test('a reused process id is a new process', () {
      final clock = FakeClock();
      final watch = PhoneProcessWatch(clock: () => clock.now);
      for (var i = 0; i < 30; i++) {
        watch.record(snap([proc(1, 0, 'dart', cpu: 95)]), clock.now);
        clock.advance(step);
      }
      final reading = watch.record(
        snap([
          proc(1, 0, 'node', cpu: 95, elapsed: const Duration(seconds: 3)),
        ]),
        clock.now,
      );
      expect(reading.heavy, isEmpty);
    });
  });

  group('stuck', () {
    test('left behind is stuck at once', () {
      final reading = PhoneProcessWatch().record(
        snap(leftBehindProcesses()),
        DateTime.utc(2026),
      );
      expect(reading.leftBehind.map((n) => n.pid), [
        15001,
        15002,
        15003,
        15004,
        15005,
        15006,
      ]);
      expect(reading.nodes.every((n) => n.stuck), isTrue);
    });

    test('about 100% for over 10 minutes with no conversation above it', () {
      final clock = FakeClock();
      final watch = PhoneProcessWatch(clock: () => clock.now);
      List<PhoneProcess> tree({
        Duration elapsed = const Duration(minutes: 11),
      }) => [
        proc(100, 1, 'proot', owner: PhoneProcessOwner.task),
        proc(
          101,
          100,
          'make',
          owner: PhoneProcessOwner.task,
          cpu: 99,
          elapsed: elapsed,
        ),
        // Under a conversation: its own work, never "stuck".
        ...normalProcesses(testerCpu: 99).map(
          (p) => p.pid == 10240
              ? proc(
                  10240,
                  10230,
                  'flutter_tester',
                  owner: PhoneProcessOwner.paseo,
                  cpu: 99,
                  elapsed: const Duration(minutes: 20),
                )
              : p,
        ),
      ];
      final first = watch.record(snap(tree()), clock.now);
      expect(first.nodes.where((n) => n.stuck), isEmpty, reason: 'one reading');
      clock.advance(step);
      final second = watch.record(snap(tree()), clock.now);
      expect(second.nodes.where((n) => n.stuck).map((n) => n.pid), [101]);
      clock.advance(step);
      final young = PhoneProcessWatch(clock: () => clock.now);
      young.record(snap(tree(elapsed: const Duration(minutes: 9))), clock.now);
      clock.advance(step);
      expect(
        young
            .record(snap(tree(elapsed: const Duration(minutes: 9))), clock.now)
            .nodes
            .where((n) => n.stuck),
        isEmpty,
      );
    });
  });

  test('Android counts all but the app; the warning starts at 28', () {
    final watch = PhoneProcessWatch();
    final near = watch.record(snap(nearLimitProcesses()), DateTime.utc(2026));
    expect(near.childCount, 29);
    expect(near.nearLimit, isTrue);
    final below = watch.record(
      snap(nearLimitProcesses(workers: 16)),
      DateTime.utc(2026),
    );
    expect(below.childCount, 27);
    expect(below.nearLimit, isFalse);
  });

  group('words', () {
    test('a plain name, never the command line', () {
      String name(String kernel, {String command = '', PhoneProcessOwner? o}) =>
          runningProcessName(
            l10n,
            proc(
              1,
              0,
              kernel,
              command: command,
              owner: o ?? PhoneProcessOwner.opencode,
            ),
          );
      expect(name('flutter_tester'), 'Flutter test');
      expect(name('bash', command: 'bash -c "rm -rf build"'), 'Command');
      expect(name('proot'), 'Linux');
      expect(name('claude'), 'Claude Code');
      expect(name('node', command: 'node /x/opencode serve'), 'OpenCode');
      expect(
        name('node', command: 'node /root/.paseo/daemon.js'),
        'Agent helper',
      );
      expect(
        name('java', command: 'java org.gradle.launcher.GradleDaemon'),
        'Gradle',
      );
      expect(name('python3.12'), 'Python');
      expect(name('anything', o: PhoneProcessOwner.app), 'This app');
      // A program with no plain name keeps the kernel's short name.
      expect(name('make', command: 'make -j8 all'), 'make');
    });

    test('processor use in a word and a %', () {
      expect(runningCpuText(l10n, 0.4), 'Idle · 0.4%');
      expect(runningCpuText(l10n, 12), 'Light · 12%');
      expect(runningCpuText(l10n, 45), 'Busy · 45%');
      expect(runningCpuText(l10n, 99.6), 'Very busy · 100%');
    });

    test("This phone's line: left behind, then heavy, then the limit", () {
      final clock = FakeClock();
      final hot = watchWithHistory(
        FakePhoneProcesses(snap(hotProcesses())),
        clock,
        history: const Duration(minutes: 3),
      ).record(snap(hotProcesses()), clock.now);
      expect(
        runningRowStatus(l10n, hot),
        '6 left behind, still using the phone',
      );
      final heavyOnly = watchWithHistory(
        FakePhoneProcesses(snap(normalProcesses(testerCpu: 96))),
        clock,
        history: const Duration(minutes: 3),
      ).record(snap(normalProcesses(testerCpu: 96)), clock.now);
      expect(runningRowStatus(l10n, heavyOnly), 'Flutter test is heavy · 96%');
      final near = PhoneProcessWatch().record(
        snap(nearLimitProcesses()),
        clock.now,
      );
      expect(
        runningRowStatus(l10n, near),
        '29 of 32: Android may stop the servers',
      );
      final calm = PhoneProcessWatch().record(
        snap(normalProcesses()),
        clock.now,
      );
      expect(runningRowStatus(l10n, calm), startsWith('12 running · '));
      expect(
        runningRowStatus(
          l10n,
          PhoneProcessWatch().record(snap(const []), clock.now),
        ),
        isNull,
      );
    });
  });
}

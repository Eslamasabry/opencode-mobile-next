// Linux services, Dart side (lib/builtin/linux_services): the definition
// format and its rules, the supervisor's state file, how a list is ordered
// and how log lines are marked; the real API over a temporary rootfs and a
// fake native side; the heat hold. The supervisor itself runs in
// linux_services_script_test.dart.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_service_files.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services_heat.dart';
import 'package:opencode_mobile/builtin/linux_services/oc_svd_script.dart';
import 'package:opencode_mobile/builtin/linux_services/rootfs_linux_services.dart';
import 'package:opencode_mobile/platform/thermal.dart';
import 'package:opencode_mobile/ui/kit/kit_log_panel.dart';

import 'support/fake_linux_services.dart';

class _Linux extends BuiltinLinux {
  final running = <String>{};
  final starts = <(String, String)>[];
  final stops = <String>[];
  final keepAtStart = <bool>[];
  bool Function()? keepExists;
  Object? startError;

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: true,
    phase: BuiltinLinuxPhase.ready,
    services: running.toList(),
  );

  @override
  Future<void> startService(
    String name,
    String script, {
    int? port,
    String? notice,
  }) async {
    if (startError case final error?) throw error;
    starts.add((name, script));
    keepAtStart.add(keepExists?.call() ?? false);
    running.add(name);
  }

  @override
  Future<void> stopService(String name) async {
    stops.add(name);
    running.remove(name);
  }
}

LinuxServiceDef _def(String name, {int? port, String command = 'sleep 1'}) =>
    LinuxServiceDef(name: name, command: command, port: port);

String _stateText(String phase, {int? pid, String held = '0'}) =>
    'phase=$phase\npid=${pid ?? ''}\nsince=1793469336\nrestarts=2\n'
    'last_exit=3\nlast_exit_at=1793469330\nnext_try=1793469340\nfails=1\n'
    'held=$held\n';

void main() {
  group('definition rules', () {
    void expectProblem(
      LinuxServiceDef def,
      LinuxServiceProblem problem, {
      List<LinuxServiceDef> others = const [],
      bool isNew = true,
    }) {
      expect(
        () => validateServiceDef(def, others: others, isNew: isNew),
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            problem,
          ),
        ),
      );
    }

    test('names', () {
      for (final bad in [
        'a',
        'Bot',
        '1bot',
        'my_bot',
        'server',
        'svc',
        'linux-job',
        'phone-engine',
        'agent-host',
        'aiteam',
        'oc-x',
        'a' * 25,
      ]) {
        expectProblem(_def(bad), LinuxServiceProblem.invalidName);
      }
      validateServiceDef(_def('my-bot'), others: const [], isNew: true);
      expectProblem(
        _def('my-bot'),
        LinuxServiceProblem.nameTaken,
        others: [_def('my-bot')],
      );
    });

    test('commands, folders, timeouts and environment', () {
      expectProblem(
        _def('bot', command: '  '),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        _def('bot', command: 'a\nb'),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        LinuxServiceDef(name: 'bot', command: 'x', dir: 'relative'),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        LinuxServiceDef(name: 'bot', command: 'x', dir: '/root/../etc'),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        LinuxServiceDef(
          name: 'bot',
          command: 'x',
          stopTimeout: const Duration(seconds: 500),
        ),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        LinuxServiceDef(name: 'bot', command: 'x', env: const {'1A': 'x'}),
        LinuxServiceProblem.invalidCommand,
      );
      expectProblem(
        LinuxServiceDef(name: 'bot', command: 'x', env: const {'A': 'x\ny'}),
        LinuxServiceProblem.invalidCommand,
      );
    });

    test('ports', () {
      for (final port in [80, 1023, 65536, 4096, 4097, 6767, 8372, 8472]) {
        expectProblem(
          _def('bot', port: port),
          LinuxServiceProblem.reservedPort,
        );
      }
      expectProblem(
        _def('bot', port: 8384),
        LinuxServiceProblem.portTaken,
        others: [_def('sync', port: 8384)],
      );
      validateServiceDef(
        _def('bot', port: 8022),
        others: const [],
        isNew: true,
      );
    });

    test('at most 16 services; an edit does not count', () {
      final sixteen = [for (var i = 0; i < 16; i++) _def('s$i-x')];
      expectProblem(_def('bot'), LinuxServiceProblem.tooMany, others: sixteen);
      validateServiceDef(
        _def('s0-x', command: 'new'),
        others: sixteen.skip(1),
        isNew: false,
      );
    });
  });

  test('a definition round-trips through its file', () {
    const def = LinuxServiceDef(
      name: 'syncthing',
      title: 'Syncthing',
      command: 'syncthing serve --gui-address=127.0.0.1:8384 --x=a=b',
      dir: '/root',
      port: 8384,
      restart: LinuxRestart.always,
      stopTimeout: Duration(seconds: 15),
      catalogId: 'syncthing',
      env: {'STNODEFAULTFOLDER': '1', 'X': 'a = b'},
    );
    final text = encodeServiceDef(def);
    expect(text, startsWith('version=1\nname=syncthing\n'));
    expect(text, contains('restart=always\n'));
    expect(text, contains('env.X=a = b\n'));
    expect(decodeServiceDef('syncthing', text), def);
    // The file must name the service it is stored as.
    expect(decodeServiceDef('other', text), isNull);
    expect(decodeServiceDef('x', 'name=x\n'), isNull);
    final minimal = decodeServiceDef('x', 'name=x\ncommand=run\n')!;
    expect(minimal.title, 'x');
    expect(minimal.restart, LinuxRestart.onFailure);
    expect(minimal.stopTimeout, const Duration(seconds: 10));
  });

  test('state files: whole ones parse, half-written ones do not', () {
    final s = LinuxServiceStateFile.parse(_stateText('port_busy', pid: 9))!;
    expect(s.phase, LinuxServicePhase.portBusy);
    expect(s.pid, 9);
    expect(s.restarts, 2);
    expect(s.lastExit, 3);
    expect(s.fails, 1);
    expect(s.held, isFalse);
    expect(s.since, DateTime.fromMillisecondsSinceEpoch(1793469336000));
    final cut = _stateText('running');
    expect(
      LinuxServiceStateFile.parse(cut.substring(0, cut.length - 7)),
      isNull,
    );
    expect(LinuxServiceStateFile.parse(''), isNull);
    expect(LinuxServiceStateFile.parse(_stateText('bogus')), isNull);
    expect(
      LinuxServiceStateFile.parse(_stateText('off', held: '1'))!.held,
      isTrue,
    );
  });

  test('combining: wanted, supervisor down, and stale fields', () {
    final def = _def('bot');
    final running = LinuxServiceStateFile.parse(_stateText('running', pid: 5))!;
    final down = combineServiceState(
      def: def,
      want: true,
      state: running,
      supervisorRunning: false,
    );
    // Not "paused for heat" (on but off): the app is starting it.
    expect(down.phase, LinuxServicePhase.starting);
    expect(down.on, isTrue);
    expect(down.pid, isNull);
    final up = combineServiceState(
      def: def,
      want: true,
      state: running,
      supervisorRunning: true,
    );
    expect(up.phase, LinuxServicePhase.running);
    expect(up.pid, 5);
    expect(up.nextTryAt, isNull);
    final waiting = combineServiceState(
      def: def,
      want: true,
      state: LinuxServiceStateFile.parse(_stateText('off'))!,
      supervisorRunning: true,
    );
    expect(waiting.phase, LinuxServicePhase.starting);
    final held = combineServiceState(
      def: def,
      want: true,
      state: LinuxServiceStateFile.parse(_stateText('off', held: '1'))!,
      supervisorRunning: true,
    );
    // On but held off: the pages say "Paused: the phone is too hot".
    expect(held.phase, LinuxServicePhase.off);
    expect(held.on, isTrue);
    final backoff = combineServiceState(
      def: def,
      want: true,
      state: LinuxServiceStateFile.parse(_stateText('backoff'))!,
      supervisorRunning: true,
    );
    expect(
      backoff.nextTryAt,
      DateTime.fromMillisecondsSinceEpoch(1793469340000),
    );
  });

  test('one list, most urgent first, then by title', () {
    LinuxServiceState s(String name, LinuxServicePhase phase) =>
        LinuxServiceState(def: _def(name), on: true, phase: phase);
    final sorted = sortServices([
      s('off-b', LinuxServicePhase.off),
      s('run-b', LinuxServicePhase.running),
      s('run-a', LinuxServicePhase.running),
      s('fail', LinuxServicePhase.failed),
      s('busy', LinuxServicePhase.portBusy),
      s('retry', LinuxServicePhase.backoff),
    ]);
    expect(sorted.map((s) => s.name), [
      'fail',
      'busy',
      'retry',
      'run-a',
      'run-b',
      'off-b',
    ]);
  });

  test('log lines carry the marker levels', () {
    final lines = serviceLogLines(
      'tail of a cut line\n::svc start 1 0\nhello\n::svc exit 1 3\n'
      '::svc backoff 2\n::svc exit 0 40\n::svc gave-up\n::svc port-busy 80\n',
      cutStart: true,
    );
    expect(lines.first.text, '::svc start 1 0');
    expect(lines.map((l) => l.level), [
      KitLogLevel.normal,
      KitLogLevel.normal,
      KitLogLevel.error,
      KitLogLevel.warning,
      KitLogLevel.normal,
      KitLogLevel.error,
      KitLogLevel.warning,
    ]);
    final exit = LinuxServiceMarker.parse('::svc exit 137 12')!;
    expect(exit.kind, 'exit');
    expect(exit.values, [137, 12]);
    expect(LinuxServiceMarker.parse('svc exit'), isNull);
  });

  group('RootfsLinuxServices', () {
    late Directory rootfs;
    late _Linux linux;
    late RootfsLinuxServices services;
    String home() => '${rootfs.path}/root/.oc-builtin/svc';
    String read(String path) => File('${home()}/$path').readAsStringSync();
    bool exists(String path) => File('${home()}/$path').existsSync();
    void writeState(String name, String text) {
      Directory('${home()}/state').createSync(recursive: true);
      File('${home()}/state/$name').writeAsStringSync(text);
    }

    setUp(() async {
      rootfs = await Directory.systemTemp.createTemp('oc-rootfs-');
      Directory('${rootfs.path}/root').createSync();
      Directory('${rootfs.path}/var/log').createSync(recursive: true);
      linux = _Linux();
      services = RootfsLinuxServices(
        linux: linux,
        locateRootfs: () async => rootfs,
        pollEvery: const Duration(milliseconds: 20),
      );
    });

    tearDown(() => rootfs.delete(recursive: true));

    test('without Linux every act says so and ensure does nothing', () async {
      final none = RootfsLinuxServices(
        linux: linux,
        locateRootfs: () async => null,
      );
      expect(
        none.read,
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            LinuxServiceProblem.linuxMissing,
          ),
        ),
      );
      await none.ensureSupervisor();
      expect(linux.starts, isEmpty);
    });

    test('save writes the definition, off, and does not start it', () async {
      await services.save(_def('bot', port: 8090));
      expect(read('defs/bot.svc'), encodeServiceDef(_def('bot', port: 8090)));
      expect(read('want/bot'), 'off\n');
      expect(linux.starts, isEmpty);
      final list = await services.read();
      expect(list.single.phase, LinuxServicePhase.off);
      expect(list.single.on, isFalse);
      await expectLater(
        services.save(_def('web', port: 8090)),
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            LinuxServiceProblem.portTaken,
          ),
        ),
      );
      // Editing keeps the person's choice.
      File('${home()}/want/bot').writeAsStringSync('on\n');
      await services.save(_def('bot', command: 'sleep 2'));
      expect(read('want/bot'), 'on\n');
    });

    test('start: on, a kick, the supervisor installed and started', () async {
      await services.save(_def('bot'));
      await services.start('bot');
      expect(read('want/bot'), 'on\n');
      expect(exists('kick/bot'), isTrue);
      expect(read('oc-svd'), ocSvdScript());
      expect(linux.starts, [('svc', ocSvdLaunchScript())]);
      // A second start of a running service neither kicks nor restarts.
      writeState('bot', _stateText('running', pid: 7));
      final kick = read('kick/bot');
      await services.start('bot');
      expect(read('kick/bot'), kick);
      expect(linux.starts, hasLength(1));
      final list = await services.read();
      expect(list.single.phase, LinuxServicePhase.running);
      expect(list.single.pid, 7);
    });

    test('start after giving up kicks again', () async {
      await services.save(_def('bot'));
      await services.start('bot');
      writeState('bot', _stateText('failed'));
      final kick = read('kick/bot');
      await Future<void>.delayed(const Duration(milliseconds: 2));
      await services.start('bot');
      expect(read('kick/bot'), isNot(kick));
    });

    test('an unknown service is refused', () async {
      await expectLater(
        services.start('nope'),
        throwsA(isA<LinuxServiceException>()),
      );
      await expectLater(
        services.start('../etc'),
        throwsA(isA<LinuxServiceException>()),
      );
    });

    test('stopping the last service retires the supervisor', () async {
      await services.save(_def('bot'));
      await services.start('bot');
      writeState('bot', _stateText('running', pid: 7));
      await services.stop('bot');
      expect(read('want/bot'), 'off\n');
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(linux.stops, isEmpty, reason: 'still running: wait');
      writeState('bot', _stateText('off'));
      final end = DateTime.now().add(const Duration(seconds: 3));
      while (linux.stops.isEmpty && DateTime.now().isBefore(end)) {
        await Future<void>.delayed(const Duration(milliseconds: 20));
      }
      expect(linux.stops, ['svc']);
    });

    test('stopping one of two keeps the supervisor', () async {
      await services.save(_def('bot'));
      await services.save(_def('web'));
      await services.start('bot');
      await services.start('web');
      await services.stop('bot');
      await Future<void>.delayed(const Duration(milliseconds: 200));
      expect(linux.stops, isEmpty);
    });

    test('remove waits for off, then deletes every file', () async {
      await services.save(_def('bot'));
      await services.start('bot');
      writeState('bot', _stateText('running', pid: 7));
      Directory('${rootfs.path}/var/log/oc-svc').createSync(recursive: true);
      File('${rootfs.path}/var/log/oc-svc/bot.log').writeAsStringSync('x\n');
      File('${rootfs.path}/var/log/oc-svc/bot.log.1').writeAsStringSync('y\n');
      final removing = services.remove('bot');
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(exists('defs/bot.svc'), isTrue);
      writeState('bot', _stateText('off'));
      await removing;
      for (final path in [
        'defs/bot.svc',
        'want/bot',
        'kick/bot',
        'state/bot',
      ]) {
        expect(exists(path), isFalse, reason: path);
      }
      expect(
        File('${rootfs.path}/var/log/oc-svc/bot.log').existsSync(),
        isFalse,
      );
      expect(await services.read(), isEmpty);
    });

    test(
      'ensure: nothing on, nothing started; a newer script restarts it',
      () async {
        await services.save(_def('bot'));
        await services.ensureSupervisor();
        expect(linux.starts, isEmpty);
        File('${home()}/want/bot').writeAsStringSync('on\n');
        await services.ensureSupervisor();
        expect(linux.starts, hasLength(1));
        await services.ensureSupervisor();
        expect(linux.starts, hasLength(1), reason: 'running, same script');
        File('${home()}/oc-svd').writeAsStringSync('# oc-svd v0\n');
        await services.ensureSupervisor();
        expect(linux.starts, hasLength(2));
        expect(read('oc-svd'), ocSvdScript());
      },
    );

    test(
      'a restart for a newer script marks itself so choices are kept',
      () async {
        linux.keepExists = () => exists('keep');
        await services.save(_def('bot'));
        File('${home()}/want/bot').writeAsStringSync('on\n');
        await services.ensureSupervisor();
        File('${home()}/oc-svd').writeAsStringSync('# oc-svd v1\n');
        await services.ensureSupervisor();
        // First start: nothing to restart, no marker; the restart: marked.
        expect(linux.keepAtStart, [false, true]);
        linux.startError = const BuiltinLinuxException('refused');
        File('${home()}/oc-svd').writeAsStringSync('# oc-svd v1\n');
        await expectLater(
          services.ensureSupervisor(),
          throwsA(isA<LinuxServiceException>()),
        );
        expect(exists('keep'), isFalse, reason: 'a failed restart leaves none');
      },
    );

    test('two ensures at once start the supervisor once', () async {
      await services.save(_def('bot'));
      File('${home()}/want/bot').writeAsStringSync('on\n');
      await Future.wait([
        services.ensureSupervisor(),
        services.ensureSupervisor(),
      ]);
      expect(linux.starts, hasLength(1));
    });

    test('a refused start is supervisorDown', () async {
      linux.startError = const BuiltinLinuxException('refused');
      await services.save(_def('bot'));
      await expectLater(
        services.start('bot'),
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            LinuxServiceProblem.supervisorDown,
          ),
        ),
      );
    });

    test('a bad definition file is left out of the list', () async {
      await services.save(_def('bot'));
      File('${home()}/defs/junk.svc').writeAsStringSync('nothing here');
      File('${home()}/defs/Bad.svc').writeAsStringSync('name=Bad\ncommand=x');
      expect((await services.read()).map((s) => s.name), ['bot']);
    });

    test('links inside the rootfs are never followed', () async {
      final outside = await Directory.systemTemp.createTemp('oc-outside-');
      addTearDown(() => outside.delete(recursive: true));
      Directory('${rootfs.path}/root/.oc-builtin').createSync();
      Link(home()).createSync(outside.path);
      await expectLater(
        services.save(_def('bot')),
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            LinuxServiceProblem.writeFailed,
          ),
        ),
      );
      expect(outside.listSync(), isEmpty);
    });

    test('a linked folder on the way is refused, not read through', () async {
      await services.save(_def('bot'));
      final outside = await Directory.systemTemp.createTemp('oc-outside-');
      addTearDown(() => outside.delete(recursive: true));
      File(
        '${outside.path}/bot',
      ).writeAsStringSync(_stateText('running', pid: 666));
      Link('${home()}/state').createSync(outside.path);
      linux.running.add('svc');
      await expectLater(
        services.read(),
        throwsA(
          isA<LinuxServiceException>().having(
            (e) => e.problem,
            'problem',
            LinuxServiceProblem.writeFailed,
          ),
        ),
      );
    });

    test('logTail joins the older part and drops a cut first line', () async {
      final logs = Directory('${rootfs.path}/var/log/oc-svc')
        ..createSync(recursive: true);
      File('${logs.path}/bot.log.1').writeAsStringSync('old 1\nold 2\n');
      File('${logs.path}/bot.log').writeAsStringSync('::svc exit 1 2\nnew\n');
      await services.save(_def('bot'));
      expect((await services.logTail('bot')).map((l) => l.text), [
        'old 1',
        'old 2',
        '::svc exit 1 2',
        'new',
      ]);
      final short = await services.logTail('bot', bytes: 6);
      expect(short.map((l) => l.text), ['new']);
      expect(await services.logTail('../x'), isEmpty);
    });

    test('the heat hold is a file the supervisor reads', () async {
      expect(await services.heatHeld(), isFalse);
      await services.setHeatHold(true);
      expect(exists('hold'), isTrue);
      expect(await services.heatHeld(), isTrue);
      await services.setHeatHold(false);
      expect(exists('hold'), isFalse);
    });

    test('watch reads while listened and stops after', () async {
      await services.save(_def('bot'));
      final seen = <int>[];
      final sub = services
          .watch(every: const Duration(milliseconds: 30))
          .listen((list) => seen.add(list.length));
      // Under load a read can take longer than the tick: wait for two.
      final end = DateTime.now().add(const Duration(seconds: 5));
      while (seen.length < 2 && DateTime.now().isBefore(end)) {
        await Future<void>.delayed(const Duration(milliseconds: 20));
      }
      await sub.cancel();
      final count = seen.length;
      expect(count, greaterThanOrEqualTo(2));
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(seen.length, count);
    });
  });

  group('heat', () {
    const critical = ThermalReading(status: ThermalStatus.critical);
    const severe = ThermalReading(status: ThermalStatus.severe);
    const cool = ThermalReading(status: ThermalStatus.light);

    test('critical holds; cool for the wait gives back', () async {
      var now = DateTime(2026, 10, 31, 22);
      final holds = <bool>[];
      var enabled = true;
      final heat = LinuxServicesHeat(
        readHold: () async => false,
        setHold: (held) async => holds.add(held),
        enabled: () => enabled,
        clock: () => now,
      );
      await heat.observe(severe);
      expect(holds, isEmpty, reason: 'severe pauses the team, not services');
      await heat.observe(critical);
      expect(holds, [true]);
      await heat.observe(cool);
      now = now.add(const Duration(minutes: 1));
      await heat.observe(severe); // hot again: the wait starts over
      now = now.add(const Duration(minutes: 1));
      await heat.observe(cool);
      now = now.add(const Duration(minutes: 1, seconds: 59));
      await heat.observe(cool);
      expect(holds, [true]);
      now = now.add(const Duration(seconds: 1));
      await heat.observe(cool);
      expect(holds, [true, false]);
      enabled = false;
      await heat.observe(critical);
      expect(holds, [true, false], reason: 'off: nothing new is held');
    });

    test('a hold from an earlier process is given back once cool', () async {
      var now = DateTime(2026, 10, 31, 22);
      final holds = <bool>[];
      final heat = LinuxServicesHeat(
        readHold: () async => true,
        setHold: (held) async => holds.add(held),
        enabled: () => false,
        clock: () => now,
      );
      await heat.observe(cool);
      now = now.add(const Duration(minutes: 2));
      await heat.observe(cool);
      expect(holds, [false]);
    });
  });

  test('the fake follows the same rules', () async {
    final fake = FakeLinuxServices();
    await fake.save(_def('bot'));
    expect(fake['bot']!.phase, LinuxServicePhase.off);
    await fake.start('bot');
    expect(fake['bot']!.phase, LinuxServicePhase.running);
    expect(fake.supervisorRunning, isTrue);
    await expectLater(
      fake.save(_def('svc')),
      throwsA(isA<LinuxServiceException>()),
    );
    fake.failNext = const LinuxServiceException(
      LinuxServiceProblem.supervisorDown,
    );
    await expectLater(fake.stop('bot'), throwsA(isA<LinuxServiceException>()));
    await fake.stop('bot');
    expect(fake['bot']!.on, isFalse);
    await fake.remove('bot');
    expect(await fake.read(), isEmpty);
    expect(fake.calls, [
      'save bot',
      'start bot',
      'save svc',
      'stop bot',
      'stop bot',
      'remove bot',
    ]);
    await fake.dispose();
  });
}

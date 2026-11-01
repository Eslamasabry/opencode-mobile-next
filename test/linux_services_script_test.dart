// The service supervisor `oc-svd` (lib/builtin/linux_services/oc_svd_script.dart)
// run for real under bash, against `sleep`, `false`-like commands and a
// port listener, in a temporary folder: start, crash restart with backoff,
// giving up, a kick, stop (the whole process group), a busy port, the heat
// hold, log rotation, a removed definition and SIGTERM.
//
// Timings are shrunk through the script's test-only parameters (a 0.2 s
// tick, 2 s "stable", backoff 1 s doubling to 4 s, give up after 3).

@TestOn('linux || mac-os')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_service_files.dart';
import 'package:opencode_mobile/builtin/linux_services/linux_services.dart';
import 'package:opencode_mobile/builtin/linux_services/oc_svd_script.dart';

class _Svd {
  _Svd(this.dir);

  final Directory dir;
  Process? process;

  String get home => '${dir.path}/svc';
  String get logs => '${dir.path}/log';

  Future<void> define(
    String name,
    String command, {
    int? port,
    LinuxRestart restart = LinuxRestart.onFailure,
    int stopTimeout = 2,
    Map<String, String> env = const {},
    String dir = '/tmp',
  }) async {
    await Directory('$home/defs').create(recursive: true);
    await File('$home/defs/$name.svc').writeAsString(
      encodeServiceDef(
        LinuxServiceDef(
          name: name,
          command: command,
          dir: dir,
          port: port,
          restart: restart,
          stopTimeout: Duration(seconds: stopTimeout),
          env: env,
        ),
      ),
    );
  }

  Future<void> want(String name, bool on) async {
    await Directory('$home/want').create(recursive: true);
    await File('$home/want/$name').writeAsString(on ? 'on\n' : 'off\n');
  }

  Future<void> kick(String name, String token) async {
    await Directory('$home/kick').create(recursive: true);
    await File('$home/kick/$name').writeAsString('$token\n');
  }

  Future<void> start() async {
    final script = File('${dir.path}/oc-svd');
    await script.writeAsString(
      ocSvdScript(
        home: home,
        logs: logs,
        tickSeconds: 0.2,
        stableSeconds: 2,
        maxDelaySeconds: 4,
        giveUpAfter: 3,
        logCapBytes: 4096,
        sizeEveryTicks: 1,
        startGapSeconds: 0,
      ),
    );
    process = await Process.start('bash', [script.path]);
  }

  LinuxServiceStateFile? state(String name) {
    final file = File('$home/state/$name');
    if (!file.existsSync()) return null;
    return LinuxServiceStateFile.parse(file.readAsStringSync());
  }

  String log(String name) {
    final file = File('$logs/$name.log');
    return file.existsSync() ? file.readAsStringSync() : '';
  }

  Future<LinuxServiceStateFile> until(
    String name,
    bool Function(LinuxServiceStateFile s) test, {
    Duration timeout = const Duration(seconds: 15),
  }) async {
    final end = DateTime.now().add(timeout);
    while (DateTime.now().isBefore(end)) {
      final s = state(name);
      if (s != null && test(s)) return s;
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    fail(
      'timed out: $name is ${File('$home/state/$name').existsSync() ? File('$home/state/$name').readAsStringSync() : 'no state'}\n'
      'log: ${log(name)}\nsupervisor: ${log('_svd')}',
    );
  }

  /// SIGTERM and wait for the supervisor to end; its exit code.
  Future<int> terminate() async {
    final p = process;
    if (p == null) return 0;
    p.kill(ProcessSignal.sigterm);
    return p.exitCode.timeout(
      const Duration(seconds: 10),
      onTimeout: () {
        p.kill(ProcessSignal.sigkill);
        return -1;
      },
    );
  }
}

bool _alive(int pid) => Process.runSync('kill', ['-0', '$pid']).exitCode == 0;

/// Pids whose process group is [pgid] (Linux `ps`).
List<int> _group(int pgid) {
  final out = Process.runSync('ps', ['-eo', 'pid=,pgid=']).stdout as String;
  return [
    for (final line in out.split('\n'))
      if (line.trim().split(RegExp(r'\s+')) case [
        final pid,
        final group,
      ] when int.tryParse(group) == pgid)
        int.parse(pid),
  ];
}

Future<int> _freePort() async {
  final socket = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
  final port = socket.port;
  await socket.close();
  return port;
}

void main() {
  late _Svd svd;
  final leftovers = <int>{};

  setUp(() async {
    svd = _Svd(await Directory.systemTemp.createTemp('oc-svd-'));
  });

  tearDown(() async {
    await svd.terminate();
    for (final pid in leftovers) {
      Process.killPid(pid, ProcessSignal.sigkill);
    }
    leftovers.clear();
    await svd.dir.delete(recursive: true);
  });

  test('the script is valid bash and names its version', () async {
    final file = File('${svd.dir.path}/check');
    await file.writeAsString(ocSvdScript());
    expect((await Process.run('bash', ['-n', file.path])).exitCode, 0);
    expect(ocSvdScript(), contains('# oc-svd v$ocSvdVersion'));
    expect(
      ocSvdLaunchScript(),
      "exec /bin/bash '/root/.oc-builtin/svc/oc-svd'\n",
    );
  });

  test('a wanted service runs, with its folder and environment', () async {
    await svd.define(
      'demo',
      r'echo "in $PWD with $FOO"; exec sleep 1000',
      env: {'FOO': 'bar baz'},
      dir: svd.dir.path,
    );
    await svd.want('demo', true);
    await svd.start();
    final s = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(s.pid!);
    expect(_alive(s.pid!), isTrue);
    expect(s.held, isFalse);
    await Future<void>.delayed(const Duration(milliseconds: 300));
    expect(svd.log('demo'), contains('::svc start '));
    expect(svd.log('demo'), contains('in ${svd.dir.path} with bar baz'));
  });

  test('a killed service comes back after its backoff', () async {
    await svd.define('demo', 'exec sleep 1000');
    await svd.want('demo', true);
    await svd.start();
    final first = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running,
    );
    Process.killPid(first.pid!, ProcessSignal.sigkill);
    final second = await svd.until(
      'demo',
      (s) =>
          s.phase == LinuxServicePhase.running &&
          s.pid != null &&
          s.pid != first.pid,
    );
    leftovers.add(second.pid!);
    expect(second.restarts, 1);
    expect(second.lastExit, 137);
    expect(svd.log('demo'), contains('::svc exit 137'));
    expect(svd.log('demo'), contains('::svc backoff 1'));
  });

  test('quick crashes give up, and a kick starts it again', () async {
    await svd.define('crash', 'echo boom; exit 3');
    await svd.want('crash', true);
    await svd.start();
    final failed = await svd.until(
      'crash',
      (s) => s.phase == LinuxServicePhase.failed,
    );
    expect(failed.fails, 3);
    expect(failed.lastExit, 3);
    expect(failed.restarts, 2);
    final log = svd.log('crash');
    expect(log, contains('::svc backoff 1'));
    expect(log, contains('::svc backoff 2'));
    expect(log, contains('::svc gave-up'));
    // It stays down: no start after giving up.
    final starts = '::svc start'.allMatches(log).length;
    await Future<void>.delayed(const Duration(seconds: 2));
    expect('::svc start'.allMatches(svd.log('crash')).length, starts);
    await svd.kick('crash', 'k1');
    await svd.until('crash', (s) => s.phase != LinuxServicePhase.failed);
    expect(
      '::svc start'.allMatches(svd.log('crash')).length,
      greaterThan(starts),
    );
  });

  test('an exit 0 with restart on-failure stays exited', () async {
    await svd.define('once', 'echo done', restart: LinuxRestart.onFailure);
    await svd.want('once', true);
    await svd.start();
    final s = await svd.until(
      'once',
      (s) => s.phase == LinuxServicePhase.exited,
    );
    expect(s.lastExit, 0);
    await Future<void>.delayed(const Duration(seconds: 1));
    expect('::svc start'.allMatches(svd.log('once')).length, 1);
  });

  test('stop ends the whole process group and it stays stopped', () async {
    await svd.define('pair', 'sleep 1000 & sleep 1001; wait');
    await svd.want('pair', true);
    await svd.start();
    final s = await svd.until(
      'pair',
      (s) => s.phase == LinuxServicePhase.running,
    );
    await Future<void>.delayed(const Duration(milliseconds: 300));
    final members = _group(s.pid!);
    expect(members.length, greaterThanOrEqualTo(3));
    leftovers.addAll(members);
    await svd.want('pair', false);
    await svd.until('pair', (s) => s.phase == LinuxServicePhase.off);
    expect(members.where(_alive), isEmpty);
    await Future<void>.delayed(const Duration(seconds: 1));
    expect(svd.state('pair')!.phase, LinuxServicePhase.off);
    expect(svd.log('pair'), contains('::svc stop'));
  });

  test('a service that ignores SIGTERM is killed after its timeout', () async {
    await svd.define('stubborn', 'trap "" TERM; sleep 1000', stopTimeout: 1);
    await svd.want('stubborn', true);
    await svd.start();
    final s = await svd.until(
      'stubborn',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(s.pid!);
    await svd.want('stubborn', false);
    await svd.until(
      'stubborn',
      (s) => s.phase == LinuxServicePhase.off,
      timeout: const Duration(seconds: 6),
    );
    expect(_alive(s.pid!), isFalse);
  });

  test('a kick restarts a running service', () async {
    await svd.define('demo', 'exec sleep 1000');
    await svd.want('demo', true);
    await svd.start();
    final first = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running,
    );
    await svd.kick('demo', 'k1');
    final second = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running && s.pid != first.pid,
    );
    leftovers.add(second.pid!);
    expect(_alive(first.pid!), isFalse);
  });

  test('a busy port waits, then the service starts', () async {
    final port = await _freePort();
    final holder = await ServerSocket.bind(InternetAddress.loopbackIPv4, port);
    holder.listen((socket) => socket.destroy());
    await svd.define(
      'web',
      'exec python3 -m http.server $port --bind 127.0.0.1',
      port: port,
    );
    await svd.want('web', true);
    await svd.start();
    await svd.until('web', (s) => s.phase == LinuxServicePhase.portBusy);
    expect(svd.log('web'), contains('::svc port-busy $port'));
    await holder.close();
    final s = await svd.until(
      'web',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(s.pid!);
  });

  test('the heat hold stops services and gives them back', () async {
    await svd.define('demo', 'exec sleep 1000');
    await svd.want('demo', true);
    await svd.start();
    final s = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running,
    );
    await File('${svd.home}/hold').writeAsString('critical\n');
    final held = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.off && s.held,
    );
    expect(held.held, isTrue);
    expect(_alive(s.pid!), isFalse);
    // The person's choice is untouched.
    expect(File('${svd.home}/want/demo').readAsStringSync().trim(), 'on');
    await File('${svd.home}/hold').delete();
    final back = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running && !s.held,
    );
    leftovers.add(back.pid!);
  });

  test('logs are rotated at the cap', () async {
    await svd.define(
      'noisy',
      r'while :; do echo 0123456789012345678901234567890123456789; sleep 0.01; done',
      restart: LinuxRestart.always,
    );
    await svd.want('noisy', true);
    await svd.start();
    final s = await svd.until(
      'noisy',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(s.pid!);
    final older = File('${svd.logs}/noisy.log.1');
    final end = DateTime.now().add(const Duration(seconds: 10));
    while (!older.existsSync() && DateTime.now().isBefore(end)) {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    expect(older.existsSync(), isTrue);
    expect(older.lengthSync(), greaterThan(4096));
    // The live file was emptied when it was copied: it is far below twice
    // the cap even though the program writes all the time.
    expect(File('${svd.logs}/noisy.log').lengthSync(), lessThan(3 * 4096));
  });

  test('a removed definition is stopped and forgotten', () async {
    await svd.define('demo', 'exec sleep 1000');
    await svd.want('demo', true);
    await svd.start();
    final s = await svd.until(
      'demo',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(s.pid!);
    await File('${svd.home}/defs/demo.svc').delete();
    final end = DateTime.now().add(const Duration(seconds: 5));
    while (_alive(s.pid!) && DateTime.now().isBefore(end)) {
      await Future<void>.delayed(const Duration(milliseconds: 100));
    }
    expect(_alive(s.pid!), isFalse);
  });

  test('SIGTERM stops every service and the supervisor', () async {
    await svd.define('alpha', 'exec sleep 1000');
    await svd.define('beta', 'trap "" TERM; sleep 1000');
    await svd.want('alpha', true);
    await svd.want('beta', true);
    await svd.start();
    final a = await svd.until(
      'alpha',
      (s) => s.phase == LinuxServicePhase.running,
    );
    final b = await svd.until(
      'beta',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.addAll([a.pid!, b.pid!]);
    expect(await svd.terminate(), 0);
    svd.process = null;
    expect(_alive(a.pid!), isFalse);
    expect(_alive(b.pid!), isFalse);
    expect(svd.state('alpha')!.phase, LinuxServicePhase.off);
    expect(svd.state('beta')!.phase, LinuxServicePhase.off);
  });

  // Stop is Stop (owner, 2026-10-31): the notification's Stop reaches the
  // supervisor as SIGTERM and must be saved as each service's own Stop, so
  // nothing comes back when the app opens again.
  test('SIGTERM (the notification\'s Stop) turns every service off', () async {
    await svd.define('alpha', 'exec sleep 1000');
    await svd.define('crash', 'exit 3');
    await svd.define('idle', 'exec sleep 1000');
    await svd.want('alpha', true);
    await svd.want('crash', true);
    await svd.want('idle', false);
    await svd.start();
    final a = await svd.until(
      'alpha',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(a.pid!);
    expect(await svd.terminate(), 0);
    svd.process = null;
    String want(String name) =>
        File('${svd.home}/want/$name').readAsStringSync().trim();
    expect(want('alpha'), 'off');
    expect(want('crash'), 'off', reason: 'retrying counts as on');
    expect(want('idle'), 'off');
    // The next supervisor (the app opened again) starts nothing.
    await svd.start();
    await Future<void>.delayed(const Duration(seconds: 2));
    expect(svd.state('alpha')!.phase, LinuxServicePhase.off);
    expect('::svc start'.allMatches(svd.log('alpha')).length, 1);
  });

  test('SIGTERM after the app\'s keep marker keeps the choices', () async {
    await svd.define('alpha', 'exec sleep 1000');
    await svd.want('alpha', true);
    await svd.start();
    final a = await svd.until(
      'alpha',
      (s) => s.phase == LinuxServicePhase.running,
    );
    leftovers.add(a.pid!);
    await File('${svd.home}/keep').writeAsString('restart\n');
    expect(await svd.terminate(), 0);
    expect(File('${svd.home}/want/alpha').readAsStringSync().trim(), 'on');
    expect(File('${svd.home}/keep').existsSync(), isFalse);
    await svd.start();
    final back = await svd.until(
      'alpha',
      (s) => s.phase == LinuxServicePhase.running && s.pid != a.pid,
    );
    leftovers.add(back.pid!);
  });
}

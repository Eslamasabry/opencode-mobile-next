// The in-app Linux manager's parsers and scripts (lib/builtin/linux_manager):
// processes, ports, storage and cleanup safety, packages and the job log.
// The scan and stop scripts also run for real against `sleep` processes.

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_manager.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_packages.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_ports.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_processes.dart';
import 'package:opencode_mobile/builtin/linux_manager/linux_storage.dart';

String _stat(
  int pid,
  String comm,
  int ppid, {
  int utime = 0,
  int stime = 0,
  int start = 1000,
  int rssPages = 2500,
  String state = 'S',
}) =>
    '$pid ($comm) $state $ppid 1 1 0 -1 4194560 100 0 0 0 $utime $stime 0 0 '
    '20 0 1 0 $start 1000000 $rssPages 18446744073709551615';

String _p(
  int pid,
  String comm,
  int ppid,
  String cmd, {
  int? start,
  int? utime,
}) =>
    '::p $pid\t${_stat(pid, comm, ppid, start: start ?? 1000, utime: utime ?? 0)}\t$cmd';

final _fixture = [
  '::self 900',
  '::uptime 5000.00',
  _p(1, 'init', 0, '/init'),
  _p(10, 'proot', 1, '/data/app/x/lib/arm64/libproot.so --kill-on-exit'),
  _p(
    11,
    'io.github.eslamasabry.opencode_mobile',
    1,
    'io.github.eslamasabry.opencode_mobile',
  ),
  _p(
    20,
    'node',
    10,
    '/opt/opencode2/bin/opencode2 serve --port 4097',
    start: 2000,
  ),
  _p(
    30,
    'node',
    10,
    '/home/oc/.local/node/bin/node /home/oc/.local/share/oc-paseo/1.0/node_modules/.bin/paseo daemon start',
    start: 2100,
  ),
  _p(40, 'claude', 10, '/usr/local/bin/claude --resume abc', start: 2200),
  _p(50, 'sh', 10, 'sh -c npm run dev -- --port 5173', start: 3000),
  _p(
    51,
    'node',
    50,
    'node /root/projects/app/node_modules/.bin/vite --port 5173',
    start: 3010,
    utime: 600,
  ),
  _p(60, 'bash', 10, '-bash', start: 4000),
  _p(900, 'sh', 10, "sh -c # oc-linux-scan", start: 4900),
  _p(901, 'tr', 900, 'tr \\000 '),
  '::p 70\t${_stat(70, 'defunct', 10, state: 'Z')}\t',
  'warning: something on stderr',
].join('\n');

void main() {
  group('processes', () {
    final scan = LinuxProcessScan.parse(_fixture);
    final byPid = {for (final p in scan.processes) p.pid: p};

    test('leaves out Android, proot, the scan itself and zombies', () {
      expect(byPid.keys.toSet(), {20, 30, 40, 50, 51, 60});
    });

    test('sorts what a process is by its command', () {
      expect(byPid[20]!.kind, LinuxProcessKind.openCodeServer);
      expect(byPid[20]!.protected, isTrue);
      expect(byPid[30]!.kind, LinuxProcessKind.paseo);
      expect(byPid[40]!.kind, LinuxProcessKind.agent);
      expect(byPid[50]!.kind, LinuxProcessKind.devServer);
      expect(byPid[51]!.kind, LinuxProcessKind.devServer);
      expect(byPid[60]!.kind, LinuxProcessKind.other);
    });

    test('recognises Antigravity launcher and installed payload as agents', () {
      final agents = LinuxProcessScan.parse(
        [
          _p(40, 'agy', 10, '/home/oc/.local/bin/agy --acp'),
          _p(
            41,
            'antigravity',
            10,
            '/home/oc/.local/share/oc-agents/antigravity/1.5.0/payload/antigravity --acp',
          ),
        ].join('\n'),
      ).processes;
      expect(agents, hasLength(2));
      for (final agent in agents) {
        expect(agent.kind, LinuxProcessKind.agent);
        expect(agent.protected, isFalse);
      }
    });

    test('names the program a person would recognise', () {
      expect(byPid[51]!.programName, 'vite');
      expect(byPid[40]!.programName, 'claude');
      expect(byPid[50]!.programName, 'npm');
      expect(byPid[60]!.programName, 'bash');
    });

    test('reads memory, age and the identity from the stat line', () {
      final vite = byPid[51]!;
      expect(vite.name, 'node');
      expect(vite.memoryMb, 10);
      // Age = the scan's start (tick 4900) minus the process's (3010).
      expect(vite.elapsedSeconds, 19);
      expect(vite.identity, '51:3010');
      expect(vite.cpuSeconds, 6);
    });

    test('age ignores /proc/uptime, which can run on another clock', () {
      // uptime says 90 000 s (an emulator that slept, a proot clock) while
      // the stat start times are only minutes apart: still minutes.
      final skewed = LinuxProcessScan.parse(
        _fixture.replaceFirst('::uptime 5000.00', '::uptime 90000.00'),
      );
      final vite = skewed.processes.firstWhere((p) => p.pid == 51);
      expect(vite.elapsedSeconds, 19);
    });

    test('a process older than the scan by 72 minutes reads 1 h 12 min', () {
      final scan = LinuxProcessScan.parse(
        [
          '::self 900',
          '::uptime 100.00',
          _p(
            30,
            'node',
            10,
            'node /opt/oc-paseo/bin/paseo daemon',
            start: 1000,
          ),
          _p(900, 'sh', 10, 'sh -c # oc-linux-scan', start: 1000 + 72 * 6000),
        ].join('\n'),
      );
      expect(scan.processes.single.elapsedSeconds, 72 * 60);
    });

    test('without the scan\'s own row, age falls back to uptime', () {
      final scan = LinuxProcessScan.parse(
        [
          '::self 900',
          '::uptime 5000.00',
          _p(
            30,
            'node',
            10,
            'node /opt/oc-paseo/bin/paseo daemon',
            start: 3000,
          ),
        ].join('\n'),
      );
      expect(scan.processes.single.elapsedSeconds, 4970);
    });

    group('groups', () {
      List<LinuxProcessGroup> groupsOf(List<String> rows) =>
          LinuxProcessScan.parse(
            ['::self 900', '::uptime 5000.00', ...rows].join('\n'),
          ).groups();

      test('Paseo supervisor, daemon and their node children are one row', () {
        final groups = groupsOf([
          _p(30, 'node', 10, 'node /home/oc/oc-paseo/bin/paseo supervisor'),
          _p(31, 'node', 10, 'node /home/oc/oc-paseo/bin/paseo daemon start'),
          _p(32, 'node', 31, 'node /home/oc/oc-paseo/lib/worker.js'),
          _p(33, 'node', 32, 'node /tmp/helper.js'),
          _p(60, 'bash', 10, '-bash'),
        ]);
        expect(groups, hasLength(2));
        final paseo = groups.firstWhere(
          (g) => g.leader.kind == LinuxProcessKind.paseo,
        );
        expect({for (final m in paseo.members) m.pid}, {30, 31, 32, 33});
        expect(paseo.count, 4);
        expect({for (final r in paseo.roots) r.pid}, {30, 31});
        expect(paseo.memoryMb, 40);
      });

      test(
        'a dev server and its shell are one row; a bash that started it is not',
        () {
          final groups = groupsOf([
            _p(60, 'bash', 10, '-bash'),
            _p(50, 'sh', 60, 'sh -c npm run dev -- --port 5173'),
            _p(51, 'node', 50, 'node /app/node_modules/.bin/vite --port 5173'),
          ]);
          expect(groups, hasLength(2));
          final dev = groups.firstWhere((g) => g.leader.pid == 50);
          expect({for (final m in dev.members) m.pid}, {50, 51});
          expect(groups.firstWhere((g) => g.leader.pid == 60).count, 1);
        },
      );
    });

    test('measures processor use between two readings', () {
      final later = LinuxProcessScan.parse(
        _fixture
            .replaceFirst('::uptime 5000.00', '::uptime 5010.00')
            .replaceFirst(
              _p(
                51,
                'node',
                50,
                'node /root/projects/app/node_modules/.bin/vite --port 5173',
                start: 3010,
                utime: 600,
              ),
              _p(
                51,
                'node',
                50,
                'node /root/projects/app/node_modules/.bin/vite --port 5173',
                start: 3010,
                utime: 1100,
              ),
            ),
      );
      final vite = later.processes.firstWhere((p) => p.pid == 51);
      expect(later.cpuPercent(vite, scan), closeTo(50, 0.01));
      expect(scan.cpuPercent(byPid[51]!, null), isNull);
    });

    test('a stop includes what the process started, children first', () {
      final all = scan.withDescendants([byPid[50]!]);
      expect([for (final p in all) p.pid], [51, 50]);
    });

    test('a stop never includes the OpenCode server', () {
      expect(scan.withDescendants([byPid[20]!]), isEmpty);
    });

    test('the stop script accepts only process identities', () {
      expect(linuxStopScript(['51:3010']), contains('51:3010'));
      expect(() => linuxStopScript([]), throwsArgumentError);
      expect(() => linuxStopScript(['51;rm -rf /']), throwsArgumentError);
      expect(() => linuxStopScript(['1:5']), throwsArgumentError);
      expect(() => linuxStopScript([r'$(id):1']), throwsArgumentError);
    });

    test('parses what a stop did', () {
      final result = LinuxStopResult.parse(
        '::stop stopped 51:3010\n::stop forced 50:3000\n'
        '::stop remaining 9:1\n::stop changed 8:2\nnoise\n',
      );
      expect(result.ended, 2);
      expect(result.forced, 1);
      expect(result.remaining, 1);
      expect(result.changed, 1);
    });

    test('scan and stop scripts work on a real process', () async {
      if (!Platform.isLinux) return;
      final sleeper = await Process.start('sleep', ['120']);
      addTearDown(sleeper.kill);
      final scanRun = await Process.run('sh', ['-c', linuxProcessScanScript]);
      final live = LinuxProcessScan.parse(scanRun.stdout as String);
      final mine = live.processes.where((p) => p.pid == sleeper.pid);
      expect(mine, hasLength(1));
      expect(mine.single.cmd, 'sleep 120');
      expect(mine.single.name, 'sleep');
      expect(
        live.processes.where((p) => p.cmd.contains('oc-linux-scan')),
        isEmpty,
      );

      final stopRun = await Process.run('sh', [
        '-c',
        linuxStopScript([mine.single.identity]),
      ]);
      final result = LinuxStopResult.parse(stopRun.stdout as String);
      expect(result.outcomes[mine.single.identity], isNotNull);
      expect(result.ended, 1);
      await sleeper.exitCode.timeout(const Duration(seconds: 5));
    });

    test('a changed start time leaves the process alone', () async {
      if (!Platform.isLinux) return;
      final sleeper = await Process.start('sleep', ['120']);
      addTearDown(sleeper.kill);
      final stopRun = await Process.run('sh', [
        '-c',
        linuxStopScript(['${sleeper.pid}:1']),
      ]);
      final result = LinuxStopResult.parse(stopRun.stdout as String);
      expect(result.changed, 1);
      expect(result.ended, 0);
      // Still alive: killing it ourselves is what ends it.
      expect(sleeper.kill(), isTrue);
    });
  });

  group('ports', () {
    final scan = LinuxProcessScan.parse(_fixture);

    test('finds the ports a command names', () {
      expect(portsNamedIn('vite --port 5173'), {5173});
      expect(portsNamedIn('serve -p 3000'), {3000});
      expect(portsNamedIn('PORT=8081 node x.js'), {8081});
      // A bare number as its own word is a port too (N1-4: http.server).
      expect(portsNamedIn('python -m http.server 8765 --bind 0.0.0.0'), {8765});
      expect(portsNamedIn('sleep 30'), isEmpty);
      expect(portsNamedIn('curl http://127.0.0.1:9229/json'), {9229});
      expect(portsNamedIn('x --port 80'), isEmpty);
    });

    test('lists a port only when a connection to it worked', () {
      final ports = parseLinuxPorts('::open 5173\n::open 4097\nnoise\n', scan);
      expect([for (final p in ports) p.port], [4097, 5173]);
    });

    test('matches an owner from the commands, and the server by its port', () {
      final ports = parseLinuxPorts(
        '::open 5173\n::open 4097\n::open 3000',
        scan,
      );
      expect([for (final p in ports) p.port], [3000, 4097, 5173]);
      expect(ports[0].owner, isNull);
      expect(ports[0].kind, isNull);
      expect(ports[0].canPreview, isTrue);
      expect(ports[1].kind, LinuxProcessKind.openCodeServer);
      expect(ports[1].canPreview, isFalse);
      expect(ports[2].owner!.pid, 51);
      expect(ports[2].canPreview, isTrue);
    });

    test('reads interface reach from the kernel table when it is readable', () {
      final ports = parseLinuxPorts(
        '::listen 00000000 1435\n::listen 0100007F 0FA1\n::open 5173\n::open 4001',
        scan,
      );
      expect(ports.firstWhere((p) => p.port == 5173).allInterfaces, isTrue);
      expect(ports.firstWhere((p) => p.port == 4001).allInterfaces, isFalse);
      expect(parseLinuxPorts('::open 5173', scan).single.allInterfaces, isNull);
    });

    test(
      'a server on a port no list holds is found by what its command names',
      () async {
        final probe = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
        final port = probe.port;
        await probe.close();
        final start = await Process.run('sh', [
          '-c',
          '(python3 -m http.server $port --bind 127.0.0.1 >/dev/null 2>&1 '
              '& echo \$!)',
        ]);
        final pid = int.parse((start.stdout as String).trim());
        try {
          for (var i = 0; i < 50; i++) {
            try {
              (await Socket.connect('127.0.0.1', port)).destroy();
              break;
            } on SocketException {
              await Future<void>.delayed(const Duration(milliseconds: 100));
            }
          }
          final r = await Process.run('sh', ['-c', linuxPortScript(const [])]);
          final ports = parseLinuxPorts(r.stdout as String, scan);
          expect(
            ports.map((p) => p.port),
            contains(port),
            reason: '${r.stdout}\n${r.stderr}',
          );
        } finally {
          Process.killPid(pid);
        }
      },
      skip:
          Platform.isLinux &&
              Process.runSync('sh', ['-c', 'command -v python3']).exitCode == 0
          ? false
          : 'needs python3 on Linux',
    );

    test('the probe script holds only numbers it was given', () {
      final script = linuxPortScript([5173, 70000, 80, 5173]);
      expect(script, contains('5173'));
      expect(script, isNot(contains('70000')));
      expect(script, isNot(contains(' 80 ')));
    });
  });

  group('storage and cleanup', () {
    test('parses du lines and adds categories', () {
      final report = LinuxStorageReport.parse(
        '::du 1000 /usr\n::du 100 /etc\n::du 5 /var/lib/dpkg\n'
        '::du 40 /var/cache/apt\n::du 60 /var/lib/apt\n'
        '::du 700 /root/projects\n::du 8 /tmp\nbroken\n::du x /var/tmp\n',
      );
      expect(report.of(LinuxStorageCategory.base), 1105 * 1024);
      expect(report.of(LinuxStorageCategory.packages), 100 * 1024);
      expect(report.of(LinuxStorageCategory.projects), 700 * 1024);
      expect(report.of(LinuxStorageCategory.temp), 8 * 1024);
      expect(report.of(LinuxStorageCategory.agents), 0);
    });

    test('freed space is the drop between two readings, never negative', () {
      expect(
        linuxFreedBytes({'/a': 100, '/b': 50}, {'/a': 10, '/b': 70}),
        90 * 1024,
      );
      expect(linuxFreedBytes({'/a': 5}, {}), 5 * 1024);
      expect(linuxFreedBytes({}, {'/a': 5}), 0);
    });

    test('safe paths: caches yes, projects and credentials never', () {
      for (final ok in [
        '/var/cache/apt',
        '/root/.npm/_cacache',
        '/root/.cache',
        '/tmp',
        '/home/oc/.cache',
        '/home/oc/.local/share/oc-paseo',
        '/tmp/build-1',
      ]) {
        expect(linuxCleanupPathIsSafe(ok), isTrue, reason: ok);
      }
      for (final bad in [
        '/',
        '/root',
        '/root/projects',
        '/root/projects/app',
        '/root/.npm',
        '/root/.claude',
        '/root/.config/x',
        '/root/.ssh',
        '/home/oc',
        '/home/oc/.oc-profiles',
        '/home/oc/.oc-profiles/abc/claude',
        '/home/oc/.local',
        '/home/oc/.local/share',
        '/home/oc/.local/bin',
        '/opt/opencode2',
        '/usr/bin',
        '/etc',
        '/var',
        '/var/lib/dpkg',
        '/tmp/../root/projects',
        '/tmp/a b',
        '/tmp/\$(id)',
        '/tmp/',
        'tmp',
        '',
      ]) {
        expect(linuxCleanupPathIsSafe(bad), isFalse, reason: bad);
      }
    });

    test('every cleanup names only safe paths and its script stays inside', () {
      final token = RegExp(r'/[A-Za-z0-9_./-]*[A-Za-z0-9_]');
      for (final action in LinuxCleanupAction.all) {
        expect(action.paths, isNotEmpty);
        for (final path in action.paths) {
          expect(linuxCleanupPathIsSafe(path), isTrue, reason: path);
        }
        for (final script in [action.script, action.measureScript]) {
          for (final protected in linuxProtectedPaths) {
            if (protected == '/etc' ||
                protected == '/usr' ||
                protected == '/opt') {
              continue;
            }
            expect(script, isNot(contains(protected)), reason: '${action.id}');
          }
          expect(script, isNot(contains('/root/projects')));
          expect(script, isNot(contains('oc-profiles')));
          for (final m in token.allMatches(script)) {
            final t = m.group(0)!;
            if (t == '/dev/null') continue;
            final named = [...action.paths, ...action.reads];
            final inside = named.any((p) => t == p || t.startsWith('$p/'));
            expect(inside, isTrue, reason: '${action.id}: $t');
          }
        }
        expect(action.script, isNot(contains('rm -rf /')));
        expect(action.script, isNot(contains(' -rf /\n')));
      }
    });

    test('scripts parse as shell', () async {
      if (!Platform.isLinux) return;
      for (final action in LinuxCleanupAction.all) {
        for (final script in [action.script, action.measureScript]) {
          final r = await Process.run('sh', ['-n', '-c', script]);
          expect(r.exitCode, 0, reason: '${action.id}: ${r.stderr}');
        }
      }
      for (final script in [
        linuxStorageScript,
        linuxProcessScanScript,
        linuxPortScript({5173}),
        linuxPackageListScript,
        linuxPackageCheckScript(['git']),
        linuxRemovePlanScript(['git']),
        linuxStopScript(['55:66']),
        for (final kind in LinuxJobKind.values) linuxJobScript(kind, ['htop']),
      ]) {
        final r = await Process.run('sh', ['-n', '-c', script]);
        expect(r.exitCode, 0, reason: '${r.stderr}\n$script');
      }
    });

    test('the temp cleanup keeps a file in use today', () async {
      if (!Platform.isLinux) return;
      // find's age rule is the only guard; check the rule is a day or more.
      expect(LinuxCleanupAction.tempFiles.script, contains('-mtime +1'));
      expect(LinuxCleanupAction.tempFiles.script, contains('-mtime +7'));
    });

    test('old agent versions are only removed when the current one is known', () {
      final script = LinuxCleanupAction.oldAgentVersions.script;
      expect(script, contains('[ -n "\$cur" ]'));
      expect(
        script,
        contains(
          r'case "$cur" in /home/oc/.local/share/oc-paseo/*) ;; *) exit 0 ;; esac',
        ),
      );
      expect(script, contains(r'case "$cur" in "$d"/*) continue ;; esac'));
    });

    test(
      'old agent versions: keeps the linked one, removes the rest',
      () async {
        if (!Platform.isLinux) return;
        final root = Directory.systemTemp.createTempSync('oc-old-agents-');
        addTearDown(() => root.deleteSync(recursive: true));
        final base = '${root.path}/home/oc/.local';
        for (final v in ['1.0', '1.1', '1.2']) {
          Directory(
            '$base/share/oc-paseo/$v/node_modules/.bin',
          ).createSync(recursive: true);
          File(
            '$base/share/oc-paseo/$v/node_modules/.bin/paseo',
          ).writeAsStringSync('x');
        }
        Directory('$base/bin').createSync(recursive: true);
        Link(
          '$base/bin/paseo',
        ).createSync('$base/share/oc-paseo/1.1/node_modules/.bin/paseo');
        final script = LinuxCleanupAction.oldAgentVersions.script.replaceAll(
          '/home/oc/.local',
          base,
        );
        final r = await Process.run('sh', ['-c', script]);
        expect(r.exitCode, 0);
        final left = Directory(
          '$base/share/oc-paseo',
        ).listSync().map((e) => e.path.split('/').last).toList();
        expect(left, ['1.1']);
      },
    );
  });

  group('packages', () {
    test('validates names', () {
      for (final ok in ['git', 'g++', 'libssl3', 'python3.12', 'a-b']) {
        expect(isValidLinuxPackageName(ok), isTrue, reason: ok);
      }
      for (final bad in [
        '',
        'a',
        'Git',
        'a b',
        'a;b',
        r'$(x)',
        '-rf',
        '../x',
        'a/b',
      ]) {
        expect(isValidLinuxPackageName(bad), isFalse, reason: bad);
      }
      expect(parseTypedPackageNames('git, htop  tmux'), [
        'git',
        'htop',
        'tmux',
      ]);
      expect(parseTypedPackageNames('git; rm -rf'), isNull);
      expect(parseTypedPackageNames('  '), isNull);
    });

    test('lists installed packages and marks the ones Linux needs', () {
      final list = parseLinuxPackages(
        '::pkg bash\t5.2\t1400\tyes\trequired\tii \n'
        '::pkg htop\t3.3\t300\t\toptional\tii \n'
        '::pkg curl\t8\t500\t\toptional\tii \n'
        '::pkg broken\t1\t1\t\toptional\tiU \n'
        '::pkg gone\t1\t1\t\toptional\trc \n'
        'garbage\n',
      );
      expect([for (final p in list) p.name], ['bash', 'curl', 'htop']);
      expect(list[0].locked, isTrue);
      expect(list[1].locked, isTrue, reason: 'setup relies on curl');
      expect(list[2].locked, isFalse);
      expect(list[2].sizeKb, 300);
    });

    test('reads candidates and removal plans', () {
      final c = parseLinuxPackageCandidates(
        '::cand htop\t3.3.0\t\n::cand nope\t\t\n::cand git\t2.43\tii \n',
      );
      expect(c[0].available, isTrue);
      expect(c[0].installed, isFalse);
      expect(c[1].available, isFalse);
      expect(c[2].installed, isTrue);
      expect(parseLinuxRemovePlan('::remv htop\n::remv libx\nx\n::remv ;bad'), [
        'htop',
        'libx',
      ]);
    });

    test('scripts refuse text that is not a package name', () {
      expect(() => linuxPackageCheckScript(['a;b']), throwsArgumentError);
      expect(() => linuxRemovePlanScript([]), throwsArgumentError);
      expect(
        () => linuxJobScript(LinuxJobKind.install, [r'$(id)']),
        throwsArgumentError,
      );
      expect(linuxJobScript(LinuxJobKind.update), contains('upgrade'));
      expect(
        linuxJobScript(LinuxJobKind.install, ['htop']),
        contains('oc_apt_install htop'),
      );
    });

    test('a job stops itself within its time budget', () {
      final script = linuxJobScript(LinuxJobKind.update);
      expect(script, contains('-lt ${linuxJobBudget.inSeconds ~/ 5}'));
      expect(linuxJobBudget, lessThan(const Duration(hours: 1)));
      expect(script, contains('::oc job-exit 124'));
    });
  });

  group('job script', () {
    Future<String> runJob(
      LinuxJobKind kind,
      List<String> names,
      int aptExit,
    ) async {
      final dir = Directory.systemTemp.createTempSync('oc-job-');
      addTearDown(() => dir.deleteSync(recursive: true));
      Directory('${dir.path}/bin').createSync();
      for (final tool in ['apt-get', 'dpkg']) {
        File('${dir.path}/bin/$tool').writeAsStringSync(
          '#!/bin/sh\nif [ "\$1" = "-o" ]; then echo "pmstatus:x:50:Working" >&3; fi\n'
          'exit ${tool == 'apt-get' ? aptExit : 0}\n',
        );
        Process.runSync('chmod', ['+x', '${dir.path}/bin/$tool']);
      }
      // The lock and stamp files live in the sandbox, not in /root or /var.
      final script = linuxJobScript(kind, names)
          .replaceAll('/root/.oc-builtin', '${dir.path}/lock')
          .replaceAll('/var/lib/oc-setup', '${dir.path}/stamp');
      final r = await Process.run(
        'sh',
        ['-c', 'mkdir -p ${dir.path}/lock; $script'],
        environment: {'PATH': '${dir.path}/bin:/usr/bin:/bin'},
      );
      return '${r.stdout}';
    }

    test('reports what it is and how it ended', () async {
      if (!Platform.isLinux) return;
      final ok = await runJob(LinuxJobKind.update, const [], 0);
      expect(ok, contains('::oc job update'));
      final state = LinuxJobState.fromLog(ok, running: false);
      expect(state.status, LinuxJobStatus.done);
    });

    test('a failing apt ends as failed with its code', () async {
      if (!Platform.isLinux) return;
      final out = await runJob(LinuxJobKind.install, ['htop'], 100);
      final state = LinuxJobState.fromLog(out, running: false);
      expect(state.status, LinuxJobStatus.failed);
      expect(state.names, ['htop']);
    });
  });

  group('job log', () {
    test('running with progress', () {
      final s = LinuxJobState.fromLog(
        '::oc job install\n::oc names htop tmux\n::oc stage Unpacking htop\n::oc percent 42.4\n',
        running: true,
      );
      expect(s.status, LinuxJobStatus.running);
      expect(s.kind, LinuxJobKind.install);
      expect(s.names, ['htop', 'tmux']);
      expect(s.percent, 42);
      expect(s.stage, 'Unpacking htop');
    });

    test('done, failed, timed out, busy and interrupted', () {
      LinuxJobState read(String tail, {bool running = false}) =>
          LinuxJobState.fromLog('::oc job update\n$tail', running: running);
      expect(read('::oc job-exit 0\n').status, LinuxJobStatus.done);
      expect(read('::oc job-exit 100\n').status, LinuxJobStatus.failed);
      expect(read('::oc job-exit 124\n').status, LinuxJobStatus.timedOut);
      expect(read('::oc job-exit 75\n').wasBusy, isTrue);
      expect(read('::oc percent 10\n').status, LinuxJobStatus.interrupted);
      expect(
        LinuxJobState.fromLog('', running: false).status,
        LinuxJobStatus.none,
      );
    });
  });
}

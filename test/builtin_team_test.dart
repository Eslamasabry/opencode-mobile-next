// The in-app AI Team runtime (lib/builtin/team/builtin_team.dart): its
// scripts parse in dash and bash, the status output is read right, the
// plugin config is the phone's loopback one on the team's own port, and
// turn-on runs store → project → supervisor → health in order against a
// fake Linux.
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/aiteam_scripts.dart';
import 'package:opencode_mobile/builtin/team/builtin_team.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/profiles.dart';

/// Answers the channel like BuiltinLinux.kt, recording every call.
class _FakeLinux {
  _FakeLinux(this.channel);

  final MethodChannel channel;
  final calls = <MethodCall>[];
  final services = <String>{};
  int Function(String script) exitCodeFor = (_) => 0;
  String Function(String script) outputFor = (_) => '';

  void install() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(channel, (call) async {
          calls.add(call);
          final args = (call.arguments as Map?) ?? const {};
          switch (call.method) {
            case 'status':
              return {
                'installed': true,
                'phase': 'ready',
                'services': services.toList(),
              };
            case 'run':
              final script = args['script'] as String;
              return {
                'exitCode': exitCodeFor(script),
                'output': outputFor(script),
              };
            case 'startService':
              services.add(args['name'] as String);
              return null;
            case 'stopService':
              services.remove(args['name'] as String);
              return null;
            case 'serviceLog':
              return 'supervisor log tail';
          }
          return null;
        });
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(
    () => debugPlatformCapabilities = const PlatformCapabilities(
      platform: TargetPlatform.android,
      isWeb: false,
    ),
  );
  tearDown(() => debugPlatformCapabilities = null);

  group('scripts', () {
    final shells = [
      for (final shell in ['dash', 'bash'])
        if (Process.runSync('sh', ['-c', 'command -v $shell']).exitCode == 0)
          shell,
    ];

    test('every team script parses', () async {
      final scripts = {
        'city': BuiltinTeam.cityScript,
        'rig': BuiltinTeam.rigScript('/root/projects/my app', 'my-app'),
        'service': BuiltinTeam.serviceScript,
        'register': BuiltinTeam.registerScript,
        'status': BuiltinTeam.statusScript,
      };
      for (final entry in scripts.entries) {
        for (final shell in shells) {
          final result = await Process.run(shell, ['-n', '-c', entry.value]);
          expect(
            result.exitCode,
            0,
            reason: '${entry.key} in $shell: ${result.stderr}',
          );
        }
      }
    }, skip: shells.isEmpty ? 'no dash or bash here' : false);

    test('the supervisor listens on loopback only, on its own port', () {
      expect(BuiltinTeam.supervisorConfig, contains('bind = "127.0.0.1"'));
      expect(BuiltinTeam.supervisorConfig, contains('port = 8472'));
      expect(
        BuiltinTeam.supervisorConfig,
        contains('allowed_hosts = ["127.0.0.1", "localhost"]'),
      );
      // Rewritten on every start, not only when the store is made.
      expect(BuiltinTeam.serviceScript, contains('bind = "127.0.0.1"'));
      expect(BuiltinTeam.serviceScript, contains('exec gc supervisor run'));
    });

    test('a project is added once, with the lean team', () {
      final script = BuiltinTeam.rigScript('/root/projects/demo', 'demo');
      expect(script, contains('gc rig add "\$project" --name "\$rig"'));
      expect(script, contains('max_active_sessions = 1'));
      expect(script, contains('name = "gastown.mayor"'));
      expect(script, contains('/root/aiteam/origins/\$rig.git'));
      expect(
        () => BuiltinTeam.rigScript('relative', 'demo'),
        throwsArgumentError,
      );
      expect(
        () => BuiltinTeam.rigScript('/root/projects/x', 'x; rm -rf /'),
        throwsArgumentError,
      );
    });

    // Issue #112: on an OpenCode 2 phone the only `opencode` is the agents'
    // wrapper, and gc init refuses to make the store without it on PATH
    // (three tries, then "the team store could not be made"). It also
    // needs Dolt's author identity.
    group('the store script in a real shell', () {
      late Directory dir;
      late String script;
      late String bin;
      late String agentBin;

      void exe(String path, String body) {
        File(path).writeAsStringSync(body);
        Process.runSync('chmod', ['755', path]);
      }

      setUp(() {
        dir = Directory.systemTemp.createTempSync('oc-team-city');
        bin = '${dir.path}/bin';
        agentBin = '${dir.path}/agent-bin';
        Directory(bin).createSync();
        Directory(agentBin).createSync();
        Directory('${dir.path}/root').createSync();
        // gc 1.4.1's own checks, in its own words.
        exe('$bin/gc', '''#!/bin/sh
echo "gc \$*" >> "\$HOME/calls.log"
[ "\$1" = init ] || exit 0
command -v opencode >/dev/null || { echo 'gc init: provider "opencode": provider not found in PATH'; exit 1; }
dolt config --global --get user.email >/dev/null || { echo 'gc init: city created, but startup is blocked by Dolt author identity'; exit 1; }
mkdir -p city/.gc
''');
        exe('$bin/dolt', '''#!/bin/sh
f="\$HOME/dolt-config"
touch "\$f"
case "\$3" in
  --get) grep -q "^\$4=" "\$f" ;;
  --add) echo "\$4=\$5" >> "\$f"; echo "dolt add \$4" >> "\$HOME/calls.log" ;;
esac
''');
        // cityScript stops leftover Dolt by pattern: never on this machine.
        exe('$bin/pkill', '#!/bin/sh\nexit 0\n');
        exe('$bin/sleep', '#!/bin/sh\nexit 0\n');
        script = BuiltinTeam.cityScript
            .replaceAll(AiTeamScripts.agentBin, agentBin)
            .replaceAll('/root', '${dir.path}/root');
      });
      tearDown(() => dir.deleteSync(recursive: true));

      Future<ProcessResult> run() => Process.run(
        'sh',
        ['-c', script],
        environment: {'PATH': '$bin:/usr/bin:/bin'},
        includeParentEnvironment: false,
      );
      String calls() {
        final log = File('${dir.path}/root/calls.log');
        return log.existsSync() ? log.readAsStringSync() : '';
      }

      test('finds the agents\' opencode and sets the Dolt identity', () async {
        exe('$agentBin/opencode', '#!/bin/sh\nexit 0\n');
        final result = await run();
        expect(result.exitCode, 0, reason: '${result.stdout}${result.stderr}');
        expect(result.stdout, contains('city-ready'));
        expect(calls(), contains('dolt add user.name'));
        expect(calls(), contains('dolt add user.email'));
        expect(calls(), contains('gc init'));
        // An identity the person set is kept.
        File('${dir.path}/root/aiteam/city.ready').deleteSync();
        File('${dir.path}/root/calls.log').deleteSync();
        expect((await run()).exitCode, 0);
        expect(calls(), isNot(contains('dolt add')));
      });

      test('without the wrapper it says so and does not try gc init', () async {
        final result = await run();
        expect(result.exitCode, 70);
        expect(
          result.stderr,
          contains("[oc] The team's opencode is missing ($agentBin/opencode)"),
        );
        expect(calls(), isNot(contains('gc init')));
      });
    });

    test('installed means the component check passes, every line', () async {
      final dir = await Directory.systemTemp.createTemp('oc-team-bin');
      addTearDown(() => dir.delete(recursive: true));
      final bin = Directory('${dir.path}/bin')..createSync();
      final agentBin = Directory('${dir.path}/agent-bin')..createSync();
      void fake(String name, String output) {
        final file = File('${bin.path}/$name')
          ..writeAsStringSync('#!/bin/sh\necho "$output"\n');
        Process.runSync('chmod', ['755', file.path]);
      }

      fake('gc', AiTeamPins.gascity);
      fake('bd', 'bd version ${AiTeamPins.beads} (abc)');
      fake('dolt', 'dolt version ${AiTeamPins.dolt}');
      for (final tool in ['tmux', 'jq', 'lsof', 'git']) {
        fake(tool, tool);
      }
      final script = BuiltinTeam.statusScript.replaceAll(
        AiTeamScripts.agentBin,
        agentBin.path,
      );
      Future<String> run() async =>
          (await Process.run(
                'sh',
                ['-c', script],
                environment: {'PATH': '${bin.path}:/usr/bin:/bin'},
              )).stdout
              as String;

      // Everything answers, but the agents' opencode is missing: a check
      // whose `set -e` were ignored would still say installed.
      expect(await run(), isNot(contains('installed')));
      File('${agentBin.path}/opencode').writeAsStringSync('#!/bin/sh\n');
      Process.runSync('chmod', ['755', '${agentBin.path}/opencode']);
      expect(await run(), contains('installed'));
      // An older Gas City is not the pinned one.
      fake('gc', '1.4.0');
      expect(await run(), isNot(contains('installed')));
    });

    test('the status script reads the store in a real shell', () async {
      final dir = await Directory.systemTemp.createTemp('oc-team');
      addTearDown(() => dir.delete(recursive: true));
      final city = File('${dir.path}/city.toml');
      await city.writeAsString('''
[workspace]
provider = "opencode"

[[rigs]]
name = "demo"
default_branch = "master"

[[rigs]]
name = "my-app"

[[patches.agent]]
name = "gastown.polecat"
dir = "demo"
''');
      final script = BuiltinTeam.statusScript.replaceAll(
        BuiltinTeam.cityDir,
        dir.path,
      );
      final result = await Process.run('sh', ['-c', script]);
      final state = BuiltinTeam.parseStatus(result.stdout as String);
      expect(state.rigs, ['demo', 'my-app']);
      expect(state.hasProject('/root/projects/demo'), isTrue);
      expect(state.hasProject('/root/projects/other'), isFalse);
    });
  });

  group('phone tuning', () {
    // The store Run 2 made (Android 15 emulator, 2026-09-24), as gc wrote it.
    const run2City = '''
[workspace]
provider = "opencode"
install_agent_hooks = ["opencode"]

[providers]
[providers.opencode]
base = "builtin:opencode"
ready_delay_ms = 0

[defaults]
[defaults.rig]
[defaults.rig.imports]
[defaults.rig.imports.gastown]
source = "https://github.com/gastownhall/gascity-packs/tree/main/gastown"
version = "sha:33d3a430a67d1782ad364556cb566bdb01d0afe3"

[daemon]
patrol_interval = "60s"
max_restarts = 5
restart_window = "1h"
shutdown_timeout = "5s"
probe_concurrency = 1
max_wakes_per_tick = 1
nudge_dispatcher = "supervisor"

[orders]
skip = ["digest-generate", "mol-dog-stale-db", "mol-dog-backup", "mol-dog-compactor", "mol-dog-phantom-db", "mol-dog-doctor", "spawn-storm-detect", "cross-rig-deps", "jsonl-export", "dolt-remotes-patrol", "prune-branches", "wisp-compact"]

[[orders.overrides]]
name = "dolt-health"
interval = "2m"

[[orders.overrides]]
name = "beads-health"
interval = "3m"

[[rigs]]
name = "my-app"
default_branch = "master"
[rigs.imports]
[rigs.imports.gastown]
source = "https://github.com/gastownhall/gascity-packs/tree/main/gastown"
version = "sha:33d3a430a67d1782ad364556cb566bdb01d0afe3"

[[patches.agent]]
name = "gastown.mayor"
suspended = true

[[patches.agent]]
name = "gastown.polecat"
dir = "my-app"
max_active_sessions = 1
''';

    test('the orders that made the peaks are off, the rest run in turn', () {
      final tuning = BuiltinTeam.phoneTuning;
      for (final order in [
        'dolt-health',
        'nudge-on-route',
        'cascade-nudge-on-blocker-close',
        'nudge-mail-sweep',
      ]) {
        expect(tuning, contains('"$order"'));
      }
      for (final order in [
        'beads-health',
        'order-tracking-sweep',
        'gate-sweep',
        'orphan-sweep',
        'reaper',
      ]) {
        expect(
          tuning,
          contains('name = "$order"\ntrigger = "manual"'),
          reason: '$order runs only inside phone-upkeep',
        );
        expect(BuiltinTeam.upkeepScript, contains('gc order run $order'));
      }
      expect(
        BuiltinTeam.cityScript,
        contains('base = "builtin:opencode"\nacp_command = "exec opencode"\n'),
      );
      expect(BuiltinTeam.upkeepOrder, contains('trigger = "cooldown"'));
      expect(BuiltinTeam.cityScript, contains(tuning));
      expect(BuiltinTeam.serviceScript, contains('phone-upkeep.toml'));
      // The tuning comes before the supervisor starts.
      expect(
        BuiltinTeam.serviceScript.indexOf('phone-upkeep'),
        lessThan(BuiltinTeam.serviceScript.indexOf('exec gc supervisor run')),
      );
    });

    test(
      'phone upkeep runs its sweeps one after another, one run at a time',
      () async {
        final dir = await Directory.systemTemp.createTemp('oc-upkeep');
        addTearDown(() => dir.delete(recursive: true));
        final bin = Directory('${dir.path}/bin')..createSync();
        final calls = File('${dir.path}/calls.log');
        // A `gc` that takes a while, like a sweep under proot.
        final gc = File('${bin.path}/gc')
          ..writeAsStringSync(
            '#!/bin/sh\necho "start \$*" >> ${calls.path}\n'
            'sleep 1\necho "end \$*" >> ${calls.path}\n',
          );
        Process.runSync('chmod', ['755', gc.path]);
        final city = Directory('${dir.path}/city')..createSync();
        final script = File('${dir.path}/upkeep.sh')
          ..writeAsStringSync(
            BuiltinTeam.upkeepScript.replaceAll(BuiltinTeam.cityDir, city.path),
          );
        final env = {'PATH': '${bin.path}:/usr/bin:/bin'};
        // Gas City starts the next run while the first is still going.
        final first = await Process.start('sh', [
          script.path,
        ], environment: env);
        while (!calls.existsSync()) {
          await Future<void>.delayed(const Duration(milliseconds: 50));
        }
        final watch = Stopwatch()..start();
        final second = Process.runSync('sh', [script.path], environment: env);
        expect(second.exitCode, 0);
        expect(watch.elapsed, lessThan(const Duration(seconds: 1)));
        expect(await first.exitCode, 0);
        // One run's sweeps, strictly one after another.
        expect(calls.readAsLinesSync(), [
          'start order run beads-health',
          'end order run beads-health',
          'start order run order-tracking-sweep',
          'end order run order-tracking-sweep',
          'start order run gate-sweep',
          'end order run gate-sweep',
        ]);
      },
      skip: Process.runSync('sh', ['-c', 'command -v flock']).exitCode == 0
          ? false
          : 'no flock here',
    );

    test('an older team gets the tuning once, the rest of it kept', () async {
      final dir = await Directory.systemTemp.createTemp('oc-tune');
      addTearDown(() => dir.delete(recursive: true));
      final city = File('${dir.path}/city.toml')..writeAsStringSync(run2City);
      final script = BuiltinTeam.tuneScript.replaceAll(
        BuiltinTeam.cityDir,
        dir.path,
      );
      final first = Process.runSync('sh', ['-c', 'set -eu\n$script']);
      expect(first.exitCode, 0, reason: '${first.stderr}');
      final tuned = city.readAsStringSync();
      expect('\n'.allMatches(tuned).length, greaterThan(20));
      expect(
        RegExp(r'^\[daemon\]$', multiLine: true).allMatches(tuned),
        hasLength(1),
      );
      expect(
        RegExp(r'^\[orders\]$', multiLine: true).allMatches(tuned),
        hasLength(1),
      );
      expect(tuned, contains('"nudge-on-route"'));
      expect(tuned, isNot(contains('name = "dolt-health"')));
      // The agents' command sits in the provider's own table, once.
      expect(
        tuned,
        contains('[providers.opencode]\nacp_command = "exec opencode"\n'),
      );
      expect('acp_command'.allMatches(tuned), hasLength(1));
      expect(tuned, isNot(contains('[[patches.provider]]')));
      // Everything else is kept.
      for (final kept in [
        '[workspace]',
        'base = "builtin:opencode"',
        '[[rigs]]',
        'name = "my-app"',
        '[rigs.imports.gastown]',
        'name = "gastown.mayor"',
        'max_active_sessions = 1',
      ]) {
        expect(tuned, contains(kept));
      }
      expect(
        File('${dir.path}/orders/phone-upkeep.toml').readAsStringSync(),
        BuiltinTeam.upkeepOrder.replaceAll(BuiltinTeam.cityDir, dir.path),
      );
      expect(
        File('${dir.path}/assets/phone-upkeep.sh').readAsStringSync(),
        BuiltinTeam.upkeepScript.replaceAll(BuiltinTeam.cityDir, dir.path),
      );
      // A second start changes nothing.
      final second = Process.runSync('sh', ['-c', 'set -eu\n$script']);
      expect(second.exitCode, 0, reason: '${second.stderr}');
      expect(city.readAsStringSync(), tuned);
      // And the result is still TOML (checked where Python can say).
      final toml = Process.runSync('python3', [
        '-c',
        'import sys, tomllib; tomllib.load(open(sys.argv[1], "rb"))',
        city.path,
      ]);
      if (toml.exitCode != 127 && !'${toml.stderr}'.contains('No module')) {
        expect(toml.exitCode, 0, reason: '${toml.stderr}');
      }
    });
  });

  test('project names become safe team names', () {
    expect(BuiltinTeam.rigName('/root/projects/my app'), 'my-app');
    expect(BuiltinTeam.rigName('/root/projects/demo/'), 'demo');
    expect(BuiltinTeam.rigName('/root/projects/..'), 'project');
  });

  test('the plugin config is the phone team on loopback', () {
    final config = BuiltinTeam.config(now: DateTime.utc(2026, 9, 24));
    expect(config.url, 'http://127.0.0.1:8472');
    expect(config.city, 'phone');
    expect(config.hostMode, OrchestrationHostMode.phone);
    expect(config.front, isFalse);
    expect(BuiltinTeam.isBuiltinConfig(config), isTrue);
    // Termux's team on 8372 is not this one.
    expect(
      BuiltinTeam.isBuiltinConfig(
        config.copyWith(url: 'http://127.0.0.1:8372'),
      ),
      isFalse,
    );
    expect(BuiltinTeam.isBuiltinConfig(null), isFalse);
  });

  group('turn on', () {
    late _FakeLinux fake;
    late BuiltinTeam team;
    var supervisorUp = false;
    var cityUp = false;

    setUp(() {
      fake = _FakeLinux(const MethodChannel(BuiltinLinux.channelName))
        ..install();
      supervisorUp = false;
      cityUp = false;
      team = BuiltinTeam(
        linux: BuiltinLinux(),
        pollInterval: Duration.zero,
        httpGet: (uri) async {
          if (uri.path == '/health') {
            return supervisorUp ? '{"status":"ok"}' : null;
          }
          return cityUp ? '{"status":"ok"}' : '{"status":"starting"}';
        },
      );
    });

    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(fake.channel, null);
    });

    test('store, project, supervisor as a service, register, health', () async {
      fake.outputFor = (script) {
        if (script.contains('gc register')) {
          supervisorUp = true;
          cityUp = true;
        }
        return '';
      };
      final stages = <BuiltinTeamStage>[];
      // The supervisor answers once the service runs.
      supervisorUp = true;
      await team.turnOn(
        '/root/projects/demo',
        notice: 'OpenCode and AI Team are running',
        onStage: stages.add,
      );
      expect(stages, [
        BuiltinTeamStage.preparing,
        BuiltinTeamStage.addingProject,
        BuiltinTeamStage.starting,
        BuiltinTeamStage.waiting,
      ]);
      final methods = fake.calls.map((call) => call.method).toList();
      final runs = [
        for (final call in fake.calls)
          if (call.method == 'run') (call.arguments as Map)['script'] as String,
      ];
      expect(runs.first, contains('gc init'));
      expect(runs[1], contains('gc rig add'));
      expect(runs.last, contains('gc register'));
      final start = fake.calls.firstWhere((c) => c.method == 'startService');
      expect((start.arguments as Map)['name'], 'aiteam');
      expect((start.arguments as Map)['port'], 8472);
      expect(
        (start.arguments as Map)['notice'],
        'OpenCode and AI Team are running',
      );
      expect(
        methods.indexOf('startService'),
        lessThan(methods.lastIndexOf('run')),
      );
      expect(fake.services, contains('aiteam'));
    });

    test('a failing step says which, with its output', () async {
      fake.exitCodeFor = (script) => script.contains('gc rig add') ? 65 : 0;
      fake.outputFor = (script) => script.contains('gc rig add')
          ? '[oc] /root/projects/demo is not a git project'
          : '';
      await expectLater(
        team.turnOn('/root/projects/demo', notice: 'n'),
        throwsA(
          isA<BuiltinTeamException>()
              .having((e) => e.stage, 'stage', BuiltinTeamStage.addingProject)
              .having((e) => e.detail, 'detail', contains('not a git project')),
        ),
      );
      expect(fake.calls.where((c) => c.method == 'startService'), isEmpty);
    });

    test('a supervisor that dies while starting is reported', () async {
      fake.install();
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(fake.channel, (call) async {
            fake.calls.add(call);
            switch (call.method) {
              case 'status':
                // It never stays up.
                return {'installed': true, 'phase': 'ready', 'services': []};
              case 'run':
                return {'exitCode': 0, 'output': ''};
              case 'serviceLog':
                return 'panic: something';
            }
            return null;
          });
      await expectLater(
        team.start(notice: 'n'),
        throwsA(
          isA<BuiltinTeamException>()
              .having((e) => e.exited, 'exited', isTrue)
              .having((e) => e.detail, 'detail', 'panic: something'),
        ),
      );
    });

    test('ensureRunning starts only a stopped team', () async {
      await team.ensureRunning(notice: 'n');
      expect(fake.services, {'aiteam'});
      fake.calls.clear();
      await team.ensureRunning(notice: 'n');
      expect(fake.calls.where((c) => c.method == 'startService'), isEmpty);
    });
  });
}

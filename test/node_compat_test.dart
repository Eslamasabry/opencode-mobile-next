// Issue #117: Node.js in a safe mode on phones whose system crashes it
// inside the app's Linux. The decision rules in Dart, the preference, the
// server's environment, and the Node step's shell functions run for real in
// dash and bash against a fake Node that crashes the way the reporter's
// phone does (SIGSEGV) unless started a certain way.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/paseo_scripts.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/setup/node_compat.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/builtin/setup/setup_engine.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:flutter/widgets.dart' show Locale;
import 'package:shared_preferences/shared_preferences.dart';

bool _has(String tool) =>
    Process.runSync('sh', ['-c', 'command -v $tool']).exitCode == 0;

void main() {
  group('chooseNodeCompat', () {
    test('takes the first variant whose own probe passed, in order', () {
      expect(
        chooseNodeCompat({'armcap': 0, 'threads': 0, 'both': 0}),
        NodeCompatVariant.armcap,
      );
      expect(
        chooseNodeCompat({'armcap': 139, 'threads': 0, 'both': 0}),
        NodeCompatVariant.threads,
      );
      expect(
        chooseNodeCompat({'armcap': 139, 'threads': 139, 'both': 0}),
        NodeCompatVariant.both,
      );
    });

    test('diagnostic probes alone never choose a variant', () {
      expect(
        chooseNodeCompat({
          'version': 0,
          'plain': 0,
          'single': 0,
          'armcapOnly': 0,
          'uv': 0,
          'armcap': 139,
          'threads': 124,
          'both': 132,
        }),
        isNull,
      );
      expect(chooseNodeCompat(const {}), isNull);
    });
  });

  group('chooseEarlyNodeCompat', () {
    test('everything crashing gives up before the download', () {
      final early = chooseEarlyNodeCompat({
        'sort': 139,
        'openssl': 139,
        'opensslArmcap': 135,
      });
      expect(early.giveUp, isTrue);
      expect(early.variant, isNull);
    });

    test('OpenSSL crashing only without armcap sets that up front', () {
      final early = chooseEarlyNodeCompat({
        'sort': 0,
        'openssl': 139,
        'opensslArmcap': 0,
      });
      expect(early.giveUp, isFalse);
      expect(early.variant, NodeCompatVariant.armcap);
    });

    test('a healthy phone, or a missing openssl, changes nothing', () {
      for (final results in [
        {'sort': 0, 'openssl': 0, 'opensslArmcap': 0},
        {'sort': 0, 'openssl': 127, 'opensslArmcap': 127},
        {'sort': 139, 'openssl': 127, 'opensslArmcap': 127},
      ]) {
        final early = chooseEarlyNodeCompat(results);
        expect(early.giveUp, isFalse, reason: '$results');
        expect(early.variant, isNull, reason: '$results');
      }
    });
  });

  test('the probe table is read back from the log, the latest run winning', () {
    const log = '''
[oc] Node.js crashed (signal 11). Checking what this phone can run
[oc] probe version 139 # node --version
[oc] probe armcap 139 # OPENSSL_armcap=0 node -e <work>
==> Trying again in compatibility mode
[oc] probe version 0 # node --version
[oc] probe armcap 0 # OPENSSL_armcap=0 node -e <work>
''';
    expect(parseNodeProbeTable(log), {'version': 0, 'armcap': 0});
    expect(
      chooseNodeCompat(parseNodeProbeTable(log)),
      NodeCompatVariant.armcap,
    );
  });

  test('the probe list carries every check of the issue, bounded', () {
    final labels = NodeCompat.nodeProbes.map((p) => p.label).toList();
    expect(
      labels,
      containsAll([
        'node --version',
        'node -e 1',
        'node --single-threaded -e 1',
        'OPENSSL_armcap=0 node -e 1',
        'UV_THREADPOOL_SIZE=1 node -e 1',
        'seq 1 400000 | sort --parallel=4 -S 64M -rn',
        'openssl sha256 /etc/hostname',
        'OPENSSL_armcap=0 openssl sha256 /etc/hostname',
      ]),
    );
    // One probe per variant, under the variant's own name.
    for (final variant in NodeCompatVariant.values) {
      expect(NodeCompat.nodeProbes.map((p) => p.id), contains(variant.name));
    }
    expect(NodeCompat.prelude, contains('timeout "\$oc_nc_secs"'));
  });

  test('the give-up line becomes the plain message, the table stays in the '
      'log for Details and the report', () {
    final l10n = lookupAppLocalizations(const Locale('en'));
    const log =
        '[oc] probe version 139 # node --version\n'
        '[oc] probe armcap 139 # OPENSSL_armcap=0 node -e <work>\n'
        '${NodeCompatFailure.unsupported}\n';
    final record = SetupJobRecord(
      jobId: 'j',
      state: 'failed',
      current: 'node',
      logTail: log,
      error: NodeCompatFailure.unsupported,
      components: const [
        SetupJobComponent(id: 'linux', state: 'done'),
        SetupJobComponent(
          id: 'node',
          state: 'failed',
          error: NodeCompatFailure.unsupported,
        ),
      ],
    );
    final progress = progressFromRecord(
      record,
      const [],
      l10n,
      now: DateTime(2026, 11, 7),
    );
    expect(progress.phoneUnsupported, isTrue);
    expect(progress.error, l10n.phoneSetupErrorNodeUnsupported);
    expect(progress.components.last.error, l10n.phoneSetupErrorNodeUnsupported);
    expect(progress.logTail, contains('[oc] probe version 139'));
    // Another failure of the same step is not this one.
    final other = progressFromRecord(
      SetupJobRecord(
        jobId: 'j',
        state: 'failed',
        components: const [
          SetupJobComponent(id: 'node', state: 'failed', error: 'exit 1'),
        ],
      ),
      const [],
      l10n,
      now: DateTime(2026, 11, 7),
    );
    expect(other.phoneUnsupported, isFalse);
    expect(SetupState.failed, other.state);
  });

  group('NodeCompatStore', () {
    setUp(() => SharedPreferences.setMockInitialValues({}));

    test('keeps the choice per device and clears it', () async {
      final prefs = await SharedPreferences.getInstance();
      expect(NodeCompatStore.read(prefs), isNull);
      await NodeCompatStore.write(prefs, (
        variant: NodeCompatVariant.threads,
        prootCompat: true,
      ));
      expect(NodeCompatStore.key, 'oc.builtin.nodeCompat.device');
      expect(prefs.getString(NodeCompatStore.key), 'threads proot-compat');
      expect(NodeCompatStore.read(prefs), (
        variant: NodeCompatVariant.threads,
        prootCompat: true,
      ));
      expect(await NodeCompatStore.load(), isNotNull);
      await NodeCompatStore.write(prefs, null);
      expect(prefs.containsKey(NodeCompatStore.key), isFalse);
    });

    test('an unknown value reads as none', () async {
      SharedPreferences.setMockInitialValues({NodeCompatStore.key: 'turbo'});
      expect(
        NodeCompatStore.read(await SharedPreferences.getInstance()),
        isNull,
      );
    });

    test('Linux\'s own file is read the same way', () {
      expect(NodeCompat.parseState('armcap\n'), (
        variant: NodeCompatVariant.armcap,
        prootCompat: false,
      ));
      expect(NodeCompat.parseState('both\nproot-compat\n'), (
        variant: NodeCompatVariant.both,
        prootCompat: true,
      ));
      expect(NodeCompat.parseState(''), isNull);
      expect(NodeCompat.parseState(null), isNull);
    });
  });

  group('the server environment', () {
    test('a known choice is exported, never NODE_OPTIONS', () {
      final script = BuiltinLinux.serverScript(
        nodeCompat: (variant: NodeCompatVariant.both, prootCompat: true),
      );
      expect(script, contains('export OPENSSL_armcap=0 UV_THREADPOOL_SIZE=1'));
      expect(script, contains('export GLIBC_TUNABLES=glibc.pthread.rseq=0'));
      expect(script, isNot(contains('NODE_OPTIONS')));
      // Before the server program starts.
      expect(
        script.lastIndexOf('export OPENSSL_armcap=0'),
        lessThan(script.indexOf('exec opencode2 serve')),
      );
    });

    test(
      'the agents\' Node check asks for the wrapper while a mode is on file',
      () {
        expect(
          PaseoPhoneScripts.nodeCheck,
          contains(
            '[ ! -s ${NodeCompat.envFile} ] || '
            '[ -e /home/oc/.local/node/bin/node.real ]',
          ),
        );
        expect(
          PaseoPhoneScripts.nodeInstall,
          contains('oc_nc_wrap "\$node_dir.new/bin/node"'),
        );
        expect(
          PaseoPhoneScripts.nodeInstall,
          contains('oc_nc_wrap "\$node_dir/bin/node"'),
        );
      },
    );

    test('without one, Linux\'s own file still decides', () {
      final script = BuiltinLinux.serverScript();
      expect(script, contains(NodeCompat.stateFile));
      expect(script, isNot(contains('\nexport OPENSSL_armcap=0\n')));
    }, skip: false);

    final shells = [
      for (final shell in ['dash', 'bash'])
        if (_has(shell)) shell,
    ];
    for (final shell in shells) {
      test('$shell: the exports reach the server\'s environment', () async {
        final dir = await Directory.systemTemp.createTemp('oc-nc-env-');
        addTearDown(() => dir.delete(recursive: true));
        final state = File('${dir.path}/node-compat')
          ..writeAsStringSync('threads\nproot-compat\n');
        String envOf(NodeCompatState? known) {
          final exports = NodeCompat.serverExports(
            known,
          ).replaceAll(NodeCompat.stateFile, state.path);
          final r = Process.runSync(
            shell,
            ['-c', 'set -eu\n$exports\nenv'],
            environment: {'PATH': Platform.environment['PATH']!},
          );
          expect(r.exitCode, 0, reason: '${r.stderr}');
          return r.stdout as String;
        }

        final fromFile = envOf(null);
        expect(fromFile, contains('UV_THREADPOOL_SIZE=1'));
        expect(fromFile, contains('GLIBC_TUNABLES=glibc.pthread.rseq=0'));
        expect(fromFile, isNot(contains('OPENSSL_armcap')));
        expect(fromFile, isNot(contains('NODE_OPTIONS')));
        final both = envOf((
          variant: NodeCompatVariant.armcap,
          prootCompat: false,
        ));
        expect(both, contains('OPENSSL_armcap=0'));
        state.deleteSync();
        final none = envOf(null);
        expect(none, isNot(contains('UV_THREADPOOL_SIZE')));
        expect(none, isNot(contains('OPENSSL_armcap')));
      });
    }
  });

  group('the Node step in a real shell', () {
    final missing = [
      for (final tool in ['dash', 'bash', 'timeout'])
        if (!_has(tool)) tool,
    ];
    final skip = missing.isEmpty ? null : 'needs ${missing.join(', ')}';

    late Directory dir;
    late String bin;
    late Map<String, String> env;

    setUp(() async {
      dir = await Directory.systemTemp.createTemp('oc-nc-');
      bin = '${dir.path}/bin';
      Directory(bin).createSync();
      Directory('${dir.path}/opt/bin').createSync(recursive: true);
      Directory('${dir.path}/etc').createSync();
      env = {
        'PATH': '$bin:${Platform.environment['PATH']}',
        'OC_NODE_COMPAT_DIR': '${dir.path}/etc',
        'OC_NODE_BIN': '${dir.path}/opt/bin/node',
        'OC_NODE_LINK': '$bin/node',
        'OC_NPM_BIN': '$bin/npm',
        'OC_NODE_PROBE_SECONDS': '10',
      };
      // npm is Node: it starts `node` from the PATH, as the real one does.
      _script('$bin/npm', 'exec node -e 1');
    });
    tearDown(() => dir.delete(recursive: true));

    /// A Node that crashes unless [runs] (shell test over "$*" and the
    /// environment) holds.
    void fakeNode(String runs) {
      _script('${dir.path}/opt/bin/node', '''
if $runs; then
  case "\$*" in --version) echo v24.21.0 ;; esac
  exit 0
fi
kill -SEGV \$\$
''');
      final link = Link('$bin/node');
      if (link.existsSync() || File(link.path).existsSync()) {
        File(link.path).deleteSync();
      }
      link.createSync('${dir.path}/opt/bin/node');
    }

    /// openssl and sort that crash unless [opensslRuns] / [sortRuns].
    void fakeTools({String opensslRuns = 'true', String sortRuns = 'true'}) {
      _script(
        '$bin/openssl',
        'if $opensslRuns; then exit 0; fi\nkill -SEGV \$\$',
      );
      _script(
        '$bin/sort',
        'cat >/dev/null\nif $sortRuns; then exit 0; fi\n'
            'kill -SEGV \$\$',
      );
    }

    Future<ProcessResult> run(
      String call, {
      String shell = 'dash',
      Map<String, String> extra = const {},
    }) => Process.run(
      shell,
      ['-c', 'set -eu\n${withSetupPrelude(NodeCompat.prelude)}\n$call'],
      environment: {...env, ...extra},
    );

    String stateOf() {
      final file = File('${dir.path}/etc/node-compat');
      return file.existsSync() ? file.readAsStringSync() : '';
    }

    for (final shell in ['dash', 'bash']) {
      test('$shell: a Node that runs is left exactly as it was', () async {
        fakeNode('true');
        fakeTools();
        final r = await run('oc_node_phone_check; oc_node_adapt', shell: shell);
        expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
        expect(stateOf(), isEmpty);
        expect(FileSystemEntity.isLinkSync('$bin/node'), isTrue);
        expect(File('${dir.path}/opt/bin/node.real').existsSync(), isFalse);
        expect(r.stdout, isNot(contains('Node.js crashed')));
        expect(r.stderr, isNot(contains('Segmentation')));
      }, skip: skip);

      test('$shell: a Node that needs OPENSSL_armcap=0 gets it, and so does '
          'npm', () async {
        fakeNode(r'[ "${OPENSSL_armcap:-}" = 0 ]');
        fakeTools();
        final r = await run(
          'oc_node_adapt; node --version; npm --version',
          shell: shell,
        );
        expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
        expect(stateOf(), 'armcap\n');
        final out = r.stdout as String;
        expect(out, contains('[oc] probe version 139 # node --version'));
        expect(out, contains('[oc] probe armcap 0 #'));
        expect(
          out,
          contains(
            'Node.js runs in a safe mode on this phone: '
            'armcap',
          ),
        );
        expect(out, contains('v24.21.0'));
        // The program itself is wrapped: its full path, an empty
        // environment and an agent's own launcher all get the mode.
        expect(File('${dir.path}/opt/bin/node.real').existsSync(), isTrue);
        final direct = await Process.run(shell, [
          '-c',
          'env -i PATH=/usr/bin:/bin "\$1" --version',
          'sh',
          '${dir.path}/opt/bin/node',
        ]);
        expect(direct.exitCode, 0, reason: '${direct.stderr}');
        expect(
          chooseNodeCompat(parseNodeProbeTable(out)),
          NodeCompatVariant.armcap,
        );
        // The crash was the probe's, never reported as the step's.
        expect(r.stderr, isNot(contains('Segmentation')));
        expect(out, isNot(contains('Segmentation')));
      }, skip: skip);

      test(
        '$shell: a Node that needs fewer threads runs single-threaded',
        () async {
          fakeNode(
            r'case "$*" in *--single-threaded*) '
            r'[ "${UV_THREADPOOL_SIZE:-}" = 1 ] ;; *) false ;; esac',
          );
          fakeTools();
          final r = await run('oc_node_adapt; node -e 1', shell: shell);
          expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
          expect(stateOf(), 'threads\n');
          final out = r.stdout as String;
          expect(out, contains('[oc] probe armcap 139'));
          expect(out, contains('[oc] probe threads 0'));
          expect(
            File('${dir.path}/etc/node-compat.env').readAsStringSync(),
            contains('--v8-pool-size=1'),
          );
          // Added to the caller's own NODE_OPTIONS, once.
          _script(
            '${dir.path}/opt/bin/node.real',
            r'printf "%s\n" "$NODE_OPTIONS"',
          );
          final options = await run(
            'node -e 1',
            shell: shell,
            extra: {'NODE_OPTIONS': '--dns-result-order=ipv4first'},
          );
          expect(
            (options.stdout as String).trim(),
            '--dns-result-order=ipv4first --v8-pool-size=1',
          );
          final nested = await run(
            'node -e 1',
            shell: shell,
            extra: {'NODE_OPTIONS': '--v8-pool-size=1'},
          );
          expect((nested.stdout as String).trim(), '--v8-pool-size=1');
        },
        skip: skip,
      );

      test('$shell: found only in compatibility mode, it says so', () async {
        fakeNode(
          r'[ "${OPENSSL_armcap:-}" = 0 ] && '
          r'[ "${GLIBC_TUNABLES:-}" = glibc.pthread.rseq=0 ]',
        );
        fakeTools();
        final r = await run(
          'oc_node_adapt',
          shell: shell,
          extra: {'GLIBC_TUNABLES': 'glibc.pthread.rseq=0'},
        );
        expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
        expect(stateOf(), 'armcap\nproot-compat\n');
        expect(
          File('${dir.path}/etc/node-compat.env').readAsStringSync(),
          contains('export GLIBC_TUNABLES=glibc.pthread.rseq=0'),
        );
        // The wrapper carries it to a Node started without it.
        final again = await run('node --version', shell: shell);
        expect(again.exitCode, 0, reason: '${again.stderr}');
      }, skip: skip);

      test('$shell: nothing works: 139, the plain link back, and the reason '
          'last', () async {
        fakeNode('false');
        fakeTools();
        final r = await run('oc_node_adapt', shell: shell);
        expect(r.exitCode, 139);
        final lines = (r.stdout as String).trimRight().split('\n');
        expect(lines.last, NodeCompatFailure.unsupported);
        expect(stateOf(), isEmpty);
        expect(FileSystemEntity.isLinkSync('$bin/node'), isTrue);
        expect(File('${dir.path}/opt/bin/node.real').existsSync(), isFalse);
        expect(
          File('${dir.path}/opt/bin/node').readAsStringSync(),
          isNot(contains('safe mode')),
        );
        expect(
          parseNodeProbeTable(r.stdout as String).keys,
          containsAll(NodeCompat.nodeProbes.map((p) => p.id)),
        );
      }, skip: skip);

      test('$shell: before the download, OpenSSL crashing only without '
          'armcap sets that mode', () async {
        fakeTools(opensslRuns: r'[ "${OPENSSL_armcap:-}" = 0 ]');
        final r = await run('oc_node_phone_check', shell: shell);
        expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
        expect(stateOf(), 'armcap\n');
        expect(r.stdout, contains('[oc] probe openssl 139'));
        expect(r.stdout, contains('[oc] probe opensslArmcap 0'));
      }, skip: skip);

      test(
        '$shell: before the download, everything crashing stops the step',
        () async {
          fakeTools(opensslRuns: 'false', sortRuns: 'false');
          final r = await run(
            'oc_node_phone_check; echo NOW-DOWNLOADING',
            shell: shell,
          );
          expect(r.exitCode, 139);
          expect(r.stdout, isNot(contains('NOW-DOWNLOADING')));
          expect(
            (r.stdout as String).trimRight().split('\n').last,
            NodeCompatFailure.unsupported,
          );
        },
        skip: skip,
      );

      test('$shell: the agents\' Node is wrapped where it is unpacked and '
          'again where it ends up', () async {
        File(
          '${dir.path}/etc/node-compat.env',
        ).writeAsStringSync(NodeCompat.envFileText(NodeCompatVariant.armcap));
        final unpacked = Directory('${dir.path}/agent/node.new/bin')
          ..createSync(recursive: true);
        _script(
          '${unpacked.path}/node',
          r'[ "${OPENSSL_armcap:-}" = 0 ] && echo v24.21.0 || kill -SEGV $$',
        );
        final r = await Process.run(shell, [
          '-c',
          'set -eu\n${NodeCompat.wrapPrelude}\n'
              'oc_nc_wrap "\$1/node.new/bin/node"\n'
              '"\$1/node.new/bin/node" --version\n'
              'mv "\$1/node.new" "\$1/node"\n'
              'oc_nc_wrap "\$1/node/bin/node"\n'
              'env -i "\$1/node/bin/node" --version\n',
          'sh',
          '${dir.path}/agent',
        ], environment: env);
        expect(r.exitCode, 0, reason: '${r.stdout}${r.stderr}');
        expect(r.stdout, 'v24.21.0\nv24.21.0\n');
        expect(
          File('${dir.path}/agent/node/bin/node').readAsStringSync(),
          contains("exec '${dir.path}/agent/node/bin/node.real'"),
        );
      }, skip: skip);

      test(
        '$shell: nothing on file, the agents\' Node stays as it is',
        () async {
          Directory('${dir.path}/agent/bin').createSync(recursive: true);
          _script('${dir.path}/agent/bin/node', 'echo real');
          final r = await Process.run(shell, [
            '-c',
            'set -eu\n${NodeCompat.wrapPrelude}\noc_nc_wrap "\$1"',
            'sh',
            '${dir.path}/agent/bin/node',
          ], environment: env);
          expect(r.exitCode, 0, reason: '${r.stderr}');
          expect(File('${dir.path}/agent/bin/node.real').existsSync(), isFalse);
        },
        skip: skip,
      );

      test('$shell: the QA file makes every probe crash', () async {
        fakeNode('true');
        fakeTools();
        File('${dir.path}/etc/node-compat.test-crash').writeAsStringSync('');
        final r = await run('oc_node_phone_check', shell: shell);
        expect(r.exitCode, 139);
      }, skip: skip);
    }
  });
}

void _script(String path, String body) {
  File(path).writeAsStringSync('#!/bin/sh\n$body\n');
  Process.runSync('chmod', ['755', path]);
}

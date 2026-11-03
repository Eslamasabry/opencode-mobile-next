import 'dart:convert';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/termux/bridge.dart';

/// The local server password must never be a process argument: every process
/// of the same Android user (the agents the server runs among them) can read
/// any `/proc/<pid>/cmdline`. It reaches the server from its file, on stdin.
const _password = 'pw-argv-canary-7f3a9c1e5b';

String _runnerScript() {
  final manager = TermuxBridge.managerScriptForTesting();
  const open = "<<'OC_SERVER_RUNNER'\n";
  final start = manager.indexOf(open) + open.length;
  final end = manager.indexOf('\nOC_SERVER_RUNNER\n', start);
  expect(start, greaterThan(open.length));
  expect(end, greaterThan(start));
  return '${manager.substring(start, end)}\n';
}

/// Every readable process command line on this machine that holds [secret].
List<String> _cmdlinesHolding(String secret) {
  final needle = utf8.encode(secret);
  final hits = <String>[];
  for (final entry in Directory('/proc').listSync(followLinks: false)) {
    final name = entry.path.split('/').last;
    if (int.tryParse(name) == null) continue;
    List<int> bytes;
    try {
      bytes = File('${entry.path}/cmdline').readAsBytesSync();
    } catch (_) {
      continue;
    }
    if (_contains(bytes, needle)) {
      hits.add(
        '$name: ${utf8.decode(bytes, allowMalformed: true)}'.replaceAll(
          secret,
          '<password>',
        ),
      );
    }
  }
  return hits;
}

bool _contains(List<int> haystack, List<int> needle) {
  outer:
  for (var i = 0; i + needle.length <= haystack.length; i++) {
    for (var j = 0; j < needle.length; j++) {
      if (haystack[i + j] != needle[j]) continue outer;
    }
    return true;
  }
  return false;
}

class _Sandbox {
  _Sandbox() : root = Directory.systemTemp.createTempSync('oc-pw-argv-') {
    final bin = Directory('${root.path}/bin')..createSync();
    out = Directory('${root.path}/out')..createSync();
    Directory('${root.path}/home/.oc').createSync(recursive: true);
    // proot-distro stays the parent of the command it runs, as proot does, so
    // its own argument list lives as long as the server.
    _exe('${bin.path}/proot-distro', r'''#!/usr/bin/env bash
[ "$1" = login ] || exit 64
shift
while [ $# -gt 0 ]; do
  case "$1" in
    --work-dir|--user) shift 2 ;;
    --) shift; break ;;
    *) shift ;;
  esac
done
case "${1:-}" in
  sh) cat >/dev/null 2>&1 || true; exit 0 ;;
  mkdir) exit 0 ;;
esac
"$@"
''');
    _exe('${bin.path}/opencode2', r'''#!/usr/bin/env bash
printf '%s' "${OPENCODE_SERVER_PASSWORD-unset}" > "$OC_TEST_OUT/server_password"
printf '%s' "${OPENCODE_PASSWORD-unset}" > "$OC_TEST_OUT/password"
printf '%s\n' "$@" > "$OC_TEST_OUT/args"
: > "$OC_TEST_OUT/ready"
sleep 20 &
wait
''');
    manager = _exe('${root.path}/manager', r'''#!/usr/bin/env bash
case "$1" in write-log) cat >> "$OC_TEST_OUT/log" ;; *) : ;; esac
''');
    runner = _exe('${root.path}/home/.oc/server-runner.sh', _runnerScript());
    passwordFile = File('${root.path}/home/.oc/server.password');
    environment = {
      ...Platform.environment,
      'HOME': '${root.path}/home',
      'PATH': '${bin.path}:${Platform.environment['PATH']}',
      'OC_TEST_OUT': out.path,
    };
  }

  final Directory root;
  late final Directory out;
  late final File manager;
  late final File runner;
  late final File passwordFile;
  late final Map<String, String> environment;

  static File _exe(String path, String body) {
    final file = File(path)..writeAsStringSync(body);
    expect(Process.runSync('chmod', ['700', path]).exitCode, 0);
    return file;
  }

  Future<Process> start() async {
    final process = await Process.start('setsid', [
      'bash',
      runner.path,
      '4123',
      passwordFile.path,
      manager.path,
      'opencode2',
    ], environment: environment);
    // The app starts the runner with stdin on /dev/null.
    await process.stdin.close();
    process.stdout.drain<void>();
    process.stderr.drain<void>();
    return process;
  }

  File output(String name) => File('${out.path}/$name');

  void dispose() => root.deleteSync(recursive: true);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Termux server runner', () {
    test('reads the password inside the container, never on the host', () {
      final runner = _runnerScript();
      expect(runner, isNot(contains(r'$(cat "$password_file")')));
      expect(runner, isNot(contains('OPENCODE_SERVER_PASSWORD="\$(')));
      expect(runner, contains(r'< "$password_file"'));
      expect(runner, contains('IFS= read -r oc_pw'));
      // The manager's readiness check reads the same file the same way.
      final manager = TermuxBridge.managerScriptForTesting();
      expect(manager, isNot(contains(r'OC_PASSWORD="$password"')));
      expect(manager, isNot(contains(r'-u "opencode:$OC_PASSWORD"')));
      expect(
        manager,
        contains(
          r"' oc-auth-check < "
          '"\$PASSWORD_FILE")',
        ),
      );
    });

    test(
      'the password is in no process command line while the server runs',
      () async {
        final box = _Sandbox();
        addTearDown(box.dispose);
        box.passwordFile.writeAsStringSync(_password);
        final process = await box.start();
        addTearDown(() async {
          Process.runSync('kill', ['-KILL', '--', '-${process.pid}']);
          await process.exitCode;
        });
        final ready = box.output('ready');
        for (var i = 0; i < 100 && !ready.existsSync(); i++) {
          await Future<void>.delayed(const Duration(milliseconds: 50));
        }
        expect(
          ready.existsSync(),
          isTrue,
          reason: box.output('log').existsSync()
              ? box.output('log').readAsStringSync()
              : 'no runner log',
        );
        expect(_cmdlinesHolding(_password), isEmpty);
        // The server still gets its credential, from the environment.
        expect(box.output('server_password').readAsStringSync(), _password);
        expect(box.output('password').readAsStringSync(), _password);
        expect(
          box.output('args').readAsStringSync(),
          'serve\n--hostname\n127.0.0.1\n--port\n4123\n',
        );
      },
      skip: !Platform.isLinux,
    );

    for (final (name, contents) in [
      ('missing', null),
      ('empty', ''),
      ('only a blank line', '\n'),
    ]) {
      test(
        'a $name password file stops the start; no server runs without it',
        () async {
          final box = _Sandbox();
          addTearDown(box.dispose);
          if (contents != null) box.passwordFile.writeAsStringSync(contents);
          final process = await box.start();
          final code = await process.exitCode.timeout(
            const Duration(seconds: 10),
          );
          expect(code, 78);
          expect(box.output('ready').existsSync(), isFalse);
          expect(box.output('args').existsSync(), isFalse);
          expect(
            box.output('log').readAsStringSync(),
            contains('the server was not started'),
          );
        },
        skip: !Platform.isLinux,
      );
    }
  });

  group('built-in Linux password write', () {
    test('the script carries no password; it reads stdin', () {
      final script = BuiltinLinux.writePasswordScript;
      expect(script, isNot(contains(_password)));
      expect(script, contains('cat > ${BuiltinLinux.passwordFile}.tmp'));
      expect(BuiltinLinux.scriptLabel(script), 'write-password');
    });

    test(
      'writes stdin to a 0600 file in a 0700 folder; empty stdin keeps the old file',
      () async {
        final root = Directory.systemTemp.createTempSync('oc-pw-write-');
        addTearDown(() => root.deleteSync(recursive: true));
        final script = BuiltinLinux.writePasswordScript.replaceAll(
          '/root/.oc-builtin',
          '${root.path}/oc-builtin',
        );
        Future<int> write(String input) async {
          final shell = await Process.start('sh', ['-c', script]);
          shell.stdin.write(input);
          await shell.stdin.close();
          return shell.exitCode;
        }

        expect(await write(_password), 0);
        final file = File('${root.path}/oc-builtin/server.password');
        expect(file.readAsStringSync(), _password);
        expect(file.statSync().mode & 0x1ff, 0x180);
        expect(
          Directory('${root.path}/oc-builtin').statSync().mode & 0x1ff,
          0x1c0,
        );
        expect(await write(''), 78);
        expect(file.readAsStringSync(), _password);
        expect(File('${file.path}.tmp').existsSync(), isFalse);
      },
      skip: !Platform.isLinux,
    );

    test(
      'writes the password where the protected sandbox denies chmod',
      () async {
        // The protected Linux denies chmod for every process; a write that
        // needs chmod fails the start with "could not set up sign-in".
        final script = BuiltinLinux.writePasswordScript;
        expect(script, isNot(contains('chmod')));
        final root = Directory.systemTemp.createTempSync('oc-pw-nochmod-');
        addTearDown(() => root.deleteSync(recursive: true));
        final bin = Directory('${root.path}/bin')..createSync();
        final fake = File('${bin.path}/chmod')
          ..writeAsStringSync('#!/bin/sh\nexit 1\n');
        await Process.run('/bin/chmod', ['755', fake.path]);
        final shell = await Process.start(
          'sh',
          ['-c', script.replaceAll('/root/.oc-builtin', '${root.path}/oc')],
          environment: {'PATH': '${bin.path}:${Platform.environment['PATH']}'},
        );
        shell.stdin.write(_password);
        await shell.stdin.close();
        expect(await shell.exitCode, 0);
        final file = File('${root.path}/oc/server.password');
        expect(file.readAsStringSync(), _password);
        expect(file.statSync().mode & 0x1ff, 0x180);
      },
      skip: !Platform.isLinux,
    );

    test('writeServerPassword sends the password as stdin only', () async {
      const channel = MethodChannel('test.secpw.builtin');
      final calls = <MethodCall>[];
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
            calls.add(call);
            return {'exitCode': 0, 'output': ''};
          });
      addTearDown(
        () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null),
      );
      final result = await BuiltinLinux(
        channel: channel,
      ).writeServerPassword(_password);
      expect(result.ok, isTrue);
      expect(calls, hasLength(1));
      final args = calls.single.arguments as Map;
      expect(args['script'], BuiltinLinux.writePasswordScript);
      expect(args['script'], isNot(contains(_password)));
      expect(args['stdin'], _password);
      expect(
        () => BuiltinLinux(channel: channel).writeServerPassword(''),
        throwsArgumentError,
      );
    });
  });
}

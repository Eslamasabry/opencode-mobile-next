import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';

/// The local server password must never be a process argument: every process
/// of the same Android user (the agents the server runs among them) can read
/// any `/proc/<pid>/cmdline`. It reaches the server from its file, on stdin.
const _password = 'pw-argv-canary-7f3a9c1e5b';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

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

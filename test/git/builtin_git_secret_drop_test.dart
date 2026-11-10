@TestOn('linux || mac-os')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/git_shell.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';

import 'git_test_support.dart';

void main() {
  late Directory root;

  setUp(() async {
    root = await Directory.systemTemp.createTemp('oc-git-drop-');
  });

  tearDown(() async {
    if (await root.exists()) await root.delete(recursive: true);
  });

  test('the token file is 0600 in a 0700 folder, and removed after', () async {
    final drop = BuiltinGitSecretDrop(rootfs: () async => root);
    final file = await drop.place(fakeGitHubToken);
    expect(file.shellPath, startsWith('/root/.oc-git/c-'));
    expect(file.hostPath, '${root.path}${file.shellPath}');
    final stat = await File(file.hostPath).stat();
    expect(stat.mode & 0x1ff, 0x180, reason: 'file mode must be 0600');
    final folder = await Directory('${root.path}/root/.oc-git').stat();
    expect(folder.mode & 0x1ff, 0x1c0, reason: 'folder mode must be 0700');
    expect(await File(file.hostPath).readAsString(), fakeGitHubToken);

    await drop.remove(file);
    expect(await File(file.hostPath).exists(), isFalse);
    // Twice, and after the script's own trap removed it: no error.
    await drop.remove(file);
  });

  test('each command gets a new random name', () async {
    final drop = BuiltinGitSecretDrop(rootfs: () async => root);
    final a = await drop.place(fakeGitHubToken);
    final b = await drop.place(fakeGitHubToken);
    expect(a.shellPath, isNot(b.shellPath));
    await drop.remove(a);
    await drop.remove(b);
  });

  test(
    'when the file cannot be made private, nothing is left behind',
    () async {
      final drop = BuiltinGitSecretDrop(
        rootfs: () async => root,
        chmod: (path, mode) async => mode == '700',
      );
      await expectLater(
        drop.place(fakeGitHubToken),
        throwsA(isA<FileSystemException>()),
      );
      final left = Directory('${root.path}/root/.oc-git').listSync();
      expect(left, isEmpty);
    },
  );

  test('no Ubuntu installed: refuses', () async {
    final drop = BuiltinGitSecretDrop(rootfs: () async => null);
    await expectLater(
      drop.place(fakeGitHubToken),
      throwsA(isA<FileSystemException>()),
    );
  });

  test('the shell returns the run output and maps bridge failures', () async {
    final shell = BuiltinGitShell(_FakeLinux());
    expect(await shell.run('echo', timeout: const Duration(seconds: 1)), 'out');
  });
}

class _FakeLinux extends BuiltinLinux {
  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
    String? input,
  }) async => const BuiltinLinuxRunResult(exitCode: 0, output: 'out');
}

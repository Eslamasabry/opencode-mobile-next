import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/domain/workspace_paths.dart';
import 'package:opencode_mobile/state/connection.dart';

/// A fake bridge that answers scripts and the all-files-access check.
class _FakeLinux extends BuiltinLinux {
  _FakeLinux({this.accessGranted = true});

  bool accessGranted;
  final scripts = <String>[];
  BuiltinLinuxRunResult Function(String script)? runAnswer;

  @override
  Future<AllFilesAccess> checkAllFilesAccess() async =>
      AllFilesAccess(granted: accessGranted);

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    scripts.add(script);
    return runAnswer?.call(script) ??
        const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }
}

void main() {
  group('canonicalizeAndroidSharedStoragePath', () {
    test('maps /sdcard to the canonical path', () {
      expect(
        canonicalizeAndroidSharedStoragePath('/sdcard/CodeAnything'),
        '/storage/emulated/0/CodeAnything',
      );
      expect(
        canonicalizeAndroidSharedStoragePath('/sdcard'),
        '/storage/emulated/0',
      );
    });

    test('maps /storage/self/primary to the canonical path', () {
      expect(
        canonicalizeAndroidSharedStoragePath(
          '/storage/self/primary/CodeAnything',
        ),
        '/storage/emulated/0/CodeAnything',
      );
      expect(
        canonicalizeAndroidSharedStoragePath('/storage/self/primary'),
        '/storage/emulated/0',
      );
    });

    test('leaves the canonical path and other paths alone', () {
      expect(
        canonicalizeAndroidSharedStoragePath(
          '/storage/emulated/0/CodeAnything',
        ),
        '/storage/emulated/0/CodeAnything',
      );
      expect(
        canonicalizeAndroidSharedStoragePath('/root/projects/app'),
        '/root/projects/app',
      );
      expect(
        canonicalizeAndroidSharedStoragePath('/data/local/tmp'),
        '/data/local/tmp',
      );
    });
  });

  group('isAndroidSharedStoragePath', () {
    test('detects all three spellings', () {
      expect(isAndroidSharedStoragePath('/sdcard/CodeAnything'), isTrue);
      expect(
        isAndroidSharedStoragePath('/storage/self/primary/CodeAnything'),
        isTrue,
      );
      expect(
        isAndroidSharedStoragePath('/storage/emulated/0/CodeAnything'),
        isTrue,
      );
    });

    test('rejects internal paths', () {
      expect(isAndroidSharedStoragePath('/root/projects/app'), isFalse);
      expect(isAndroidSharedStoragePath('/root'), isFalse);
      expect(isAndroidSharedStoragePath('/sdcardboard/app'), isFalse);
    });
  });

  group('normalizeDirectoryPath', () {
    test('canonicalizes shared storage before anything else', () {
      expect(
        ConnectionController.normalizeDirectoryPath('/sdcard/CodeAnything/'),
        '/storage/emulated/0/CodeAnything',
      );
      expect(
        ConnectionController.normalizeDirectoryPath(
          '/storage/self/primary/CodeAnything',
        ),
        '/storage/emulated/0/CodeAnything',
      );
    });

    test('leaves /root/projects behavior unchanged', () {
      expect(
        ConnectionController.normalizeDirectoryPath('/root/projects/app/'),
        '/root/projects/app',
      );
      expect(
        ConnectionController.sameDirectoryPath(
          '/root/projects/app/',
          '/root/projects/app',
        ),
        isTrue,
      );
    });
  });

  group('workspaceDirectoryProblem', () {
    test('accepts a real shared-storage project', () {
      expect(workspaceDirectoryProblem('/sdcard/CodeAnything'), isNull);
      expect(
        workspaceDirectoryProblem('/storage/emulated/0/CodeAnything'),
        isNull,
      );
    });

    test('still rejects protected folders', () {
      expect(workspaceDirectoryProblem('/root'), isNotNull);
      expect(workspaceDirectoryProblem('/'), isNotNull);
    });
  });

  group('probeSharedStorageScript', () {
    test('enumerates instead of test -d, and never creates', () {
      final script = BuiltinLinux.probeSharedStorageScript(
        '/storage/emulated/0/CodeAnything',
      );
      expect(script, contains('ls -A'));
      expect(script, isNot(contains('mkdir')));
      expect(script, isNot(contains('git init')));
      expect(script, isNot(contains('test -d')));
    });

    test('rejects unsafe paths', () {
      expect(
        () => BuiltinLinux.probeSharedStorageScript('relative/path'),
        throwsArgumentError,
      );
      expect(
        () => BuiltinLinux.probeSharedStorageScript('/a\nb'),
        throwsArgumentError,
      );
    });

    test('parses all four states, garbage reads as unreachable', () {
      expect(
        parseSharedStorageProbe('status=with-files\n'),
        SharedStorageProbe.withFiles,
      );
      expect(
        parseSharedStorageProbe('status=empty\n'),
        SharedStorageProbe.empty,
      );
      expect(
        parseSharedStorageProbe('status=missing\n'),
        SharedStorageProbe.missing,
      );
      expect(
        parseSharedStorageProbe('status=unreadable\n'),
        SharedStorageProbe.unreadable,
      );
      expect(
        parseSharedStorageProbe('something unexpected'),
        SharedStorageProbe.unreadable,
      );
    });
  });

  group('BuiltinProjectFolders.create', () {
    test(
      'refuses shared storage without all-files access and runs no mkdir',
      () async {
        final linux = _FakeLinux(accessGranted: false);
        final folders = BuiltinProjectFolders(linux);
        await expectLater(
          folders.create('/storage/emulated/0/CodeAnything'),
          throwsA(
            isA<BuiltinLinuxException>().having(
              (e) => e.code,
              'code',
              'all_files_access_missing',
            ),
          ),
        );
        // No script at all: neither probe nor mkdir may run.
        expect(linux.scripts, isEmpty);
      },
    );

    test(
      'refuses an unreachable shared-storage folder and runs no mkdir',
      () async {
        final linux = _FakeLinux(accessGranted: true);
        linux.runAnswer = (script) {
          if (script.contains('status=')) {
            return const BuiltinLinuxRunResult(
              exitCode: 0,
              output: 'status=unreadable\n',
            );
          }
          return const BuiltinLinuxRunResult(exitCode: 0, output: '');
        };
        final folders = BuiltinProjectFolders(linux);
        await expectLater(
          folders.create('/storage/emulated/0/CodeAnything'),
          throwsA(
            isA<BuiltinLinuxException>().having(
              (e) => e.code,
              'code',
              'shared_storage_unreachable',
            ),
          ),
        );
        expect(
          linux.scripts.any((s) => s.contains('mkdir')),
          isFalse,
          reason: 'no mkdir may run for an unreachable folder',
        );
      },
    );

    test(
      'creates a genuinely missing shared-storage folder on real storage',
      () async {
        final linux = _FakeLinux(accessGranted: true);
        linux.runAnswer = (script) {
          if (script.contains('status=')) {
            return const BuiltinLinuxRunResult(
              exitCode: 0,
              output: 'status=missing\n',
            );
          }
          return const BuiltinLinuxRunResult(
            exitCode: 0,
            output: 'created /storage/emulated/0/CodeAnything\n',
          );
        };
        final folders = BuiltinProjectFolders(linux);
        final made = await folders.create(
          '/storage/emulated/0/CodeAnything',
        );
        expect(made.created, isTrue);
        expect(linux.scripts.last, contains('mkdir'));
      },
    );

    test('internal /root/projects flow is unchanged', () async {
      final linux = _FakeLinux(accessGranted: false);
      linux.runAnswer = (_) => const BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'created /root/projects/hello\n',
      );
      final folders = BuiltinProjectFolders(linux);
      // No access check may block internal projects, even when the
      // (irrelevant) shared-storage grant is missing.
      final made = await folders.create('/root/projects/hello');
      expect(made, (path: '/root/projects/hello', created: true));
      expect(linux.scripts, hasLength(1));
      expect(
        linux.scripts.single,
        BuiltinLinux.createFolderScript('/root/projects/hello'),
      );
    });
  });

  group('BuiltinProjectFolders.probeSharedStorage', () {
    test('reports the enumerated state without creating', () async {
      final linux = _FakeLinux();
      linux.runAnswer = (_) => const BuiltinLinuxRunResult(
        exitCode: 0,
        output: 'status=with-files\n',
      );
      final folders = BuiltinProjectFolders(linux);
      expect(
        await folders.probeSharedStorage('/storage/emulated/0/CodeAnything'),
        SharedStorageProbe.withFiles,
      );
      expect(linux.scripts.single, contains('ls -A'));
      expect(linux.scripts.single, isNot(contains('mkdir')));
    });

    test('a bridge failure throws instead of reading as missing', () async {
      final linux = _FakeLinux();
      linux.runAnswer = (_) =>
          const BuiltinLinuxRunResult(exitCode: -1, output: 'timed out');
      final folders = BuiltinProjectFolders(linux);
      await expectLater(
        folders.probeSharedStorage('/storage/emulated/0/CodeAnything'),
        throwsA(isA<BuiltinLinuxException>()),
      );
    });
  });
}

import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/migration/termux_migration_transport.dart';
import 'package:opencode_mobile/domain/termux_migration.dart';
import 'package:opencode_mobile/termux/bridge.dart';

const _job = '0123456789abcdef0123456789abcdef';

TermuxCommandResult _result(String stdout) => TermuxCommandResult(
  stdout: stdout,
  stderr: '',
  exitCode: 0,
  errorCode: -1,
  errorMessage: '',
);

Matcher _failure(TermuxMigrationFailure code) =>
    isA<TermuxMigrationException>().having((error) => error.code, 'code', code);

void main() {
  test(
    'inventory reports only bounded metadata and unknown versions',
    () async {
      final transport = BridgeTermuxMigrationTransport(
        run: (script, {required timeout}) async {
          expect(timeout, const Duration(seconds: 110));
          expect(script, contains('timeout -k 2 90'));
          return _result(
            'free|9999999\nitem|projects|4096|3\nitem|config|0|0\nisolated|1',
          );
        },
      );
      final source = await transport.inspect();
      expect(source.freeBytes, 9999999);
      expect(source.items.first.bytes, 4096);
      expect(source.oc2Isolated, isTrue);
      expect(source.oc1Version, isNull);
    },
  );

  test('unsupported unselected categories do not hide valid projects', () async {
    final transport = BridgeTermuxMigrationTransport(
      run: (script, {required timeout}) async {
        expect(script, contains('-name .git -type f'));
        return _result(
          'free|5000\nitem|projects|4096|3\nproblem|sessions|unsupportedEntry',
        );
      },
    );
    final source = await transport.inspect();
    expect(source.items.first.problem, isNull);
    expect(source.items.last.problem, TermuxMigrationFailure.unsupportedEntry);
    expect(source.items.last.bytes, 0);
  });

  test('raw bridge credentials are discarded on failure', () async {
    final transport = BridgeTermuxMigrationTransport(
      run: (_, {required timeout}) async {
        throw const TermuxBridgeException(
          'test-provider-credential-must-not-escape',
        );
      },
    );
    try {
      await transport.inspect();
      fail('Expected failure');
    } catch (error) {
      expect(error, _failure(TermuxMigrationFailure.unavailable));
      expect('$error', isNot(contains('test-provider-credential')));
    }
  });

  test('source rejects links even after prior metadata output', () async {
    final transport = BridgeTermuxMigrationTransport(
      run: (_, {required timeout}) async =>
          _result('free|5000\nfailure|unsupportedEntry'),
    );
    await expectLater(
      transport.inspect(),
      throwsA(_failure(TermuxMigrationFailure.unsupportedEntry)),
    );
  });

  test(
    'pack has strict IDs, USTAR, own lock, bounded size and digest',
    () async {
      final digest = 'a' * 64;
      var calls = 0;
      final transport = BridgeTermuxMigrationTransport(
        run: (script, {required timeout}) async {
          calls++;
          expect(
            script,
            contains(
              'tar --format=ustar --hard-dereference --no-recursion --null',
            ),
          );
          expect(script, contains('flock -n 9'));
          expect(script, contains('ulimit -f'));
          expect(script, isNot(contains('rm -rf')));
          return _result('archive|10240|$digest');
        },
      );
      await expectLater(
        transport.pack(
          '../escape',
          TermuxMigrationItem.projects,
          maxBytes: 10240,
        ),
        throwsA(_failure(TermuxMigrationFailure.invalidSelection)),
      );
      expect(calls, 0);
      final archive = await transport.pack(
        _job,
        TermuxMigrationItem.projects,
        maxBytes: 10240,
      );
      expect(archive.sha256, digest);
    },
  );

  test(
    'authenticated stream resumes flushed chunks after bridge loss',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'migration-transport-test-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final destination = File(
        '${directory.path}/migrations/$_job/archive.tar',
      );
      final content = List<int>.generate(
        BridgeTermuxMigrationTransport.chunkBytes + 4096,
        (i) => i % 251,
      );
      final archive = TermuxMigrationArchive(
        bytes: content.length,
        sha256: sha256.convert(content).toString(),
      );
      final offsets = <int>[];
      var loseSecond = true;
      Future<TermuxCommandResult> run(
        String script, {
        required Duration timeout,
      }) async {
        final offset = int.parse(
          RegExp(r"--header 'x-offset: (\d+)'").firstMatch(script)![1]!,
        );
        offsets.add(offset);
        if (offset > 0 && loseSecond) {
          loseSecond = false;
          throw const TermuxBridgeException('connection lost');
        }
        // The bearer reaches curl as a private config file, never argv.
        expect(script, isNot(contains("--header 'Authorization")));
        expect(script, contains('-K "\$oc_curl_cfg"'));
        final token = RegExp(
          "Authorization: Bearer %s\"\\\\n' '([A-Za-z0-9_=-]+)'",
        ).firstMatch(script)![1]!;
        final url = RegExp(
          r"'(http://127\.0\.0\.1:\d+/archive)'",
        ).firstMatch(script)![1]!;
        final client = HttpClient();
        try {
          // A second local app cannot write without the fresh bearer.
          final denied = await client.putUrl(Uri.parse(url));
          denied.headers.set('x-offset', '$offset');
          final deniedResponse = await denied.close();
          expect(deniedResponse.statusCode, HttpStatus.forbidden);
          await deniedResponse.drain<void>();
          final request = await client.putUrl(Uri.parse(url));
          request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
          request.headers.set('x-offset', '$offset');
          final end = (offset + BridgeTermuxMigrationTransport.chunkBytes)
              .clamp(0, content.length);
          request.add(content.sublist(offset, end));
          final response = await request.close();
          expect(response.statusCode, HttpStatus.noContent);
          await response.drain<void>();
        } finally {
          client.close(force: true);
        }
        return _result('copied');
      }

      final first = BridgeTermuxMigrationTransport(run: run);
      await expectLater(
        first.copy(
          _job,
          TermuxMigrationItem.projects,
          archive,
          destination,
          cancelled: () => false,
        ),
        throwsA(_failure(TermuxMigrationFailure.unavailable)),
      );
      final resumed = BridgeTermuxMigrationTransport(run: run);
      await resumed.copy(
        _job,
        TermuxMigrationItem.projects,
        archive,
        destination,
        cancelled: () => false,
      );
      expect(await destination.readAsBytes(), content);
      expect(offsets, [
        0,
        BridgeTermuxMigrationTransport.chunkBytes,
        BridgeTermuxMigrationTransport.chunkBytes,
      ]);
      await resumed.copy(
        _job,
        TermuxMigrationItem.projects,
        archive,
        destination,
        cancelled: () => false,
      );
      expect(offsets.length, 3, reason: 'Verified rerun does not retransfer');
    },
  );

  test(
    'real host shell packs managed projects and transfers verified USTAR',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'migration shell test-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final prefix = '${directory.path}/usr';
      final home = '${directory.path}/home';
      final project = Directory(
        '$prefix/var/lib/proot-distro/containers/opencode-ubuntu/rootfs/root/projects/example',
      );
      await project.create(recursive: true);
      await Directory(home).create();
      await File(
        '${project.path}/README.md',
      ).writeAsString('fixture project\n');
      await Directory('${project.path}/.git').create();
      Future<TermuxCommandResult> run(
        String script, {
        required Duration timeout,
      }) async {
        final process = await Process.start(
          'bash',
          ['-s'],
          environment: {'HOME': home, 'PREFIX': prefix},
        );
        final output = process.stdout.transform(utf8.decoder).join();
        final errors = process.stderr.drain<void>();
        process.stdin.write(script);
        await process.stdin.close();
        final exit = await process.exitCode.timeout(timeout);
        await errors;
        return TermuxCommandResult(
          stdout: await output,
          stderr: '',
          exitCode: exit,
          errorCode: -1,
          errorMessage: '',
        );
      }

      final transport = BridgeTermuxMigrationTransport(run: run);
      final inventory = await transport.inspect();
      final projects = inventory.items.singleWhere(
        (item) => item.item == TermuxMigrationItem.projects,
      );
      expect(projects.problem, isNull);
      expect(projects.bytes, 16);
      final archive = await transport.pack(
        _job,
        TermuxMigrationItem.projects,
        maxBytes: 1024 * 1024,
      );
      final packed = File('$home/.oc/migration/$_job/projects.tar');
      expect(await packed.length(), archive.bytes);
      expect(
        sha256.convert(await packed.readAsBytes()).toString(),
        archive.sha256,
      );
      final bytes = await packed.readAsBytes();
      expect(ascii.decode(bytes.sublist(257, 262)), 'ustar');
      final destination = File(
        '${directory.path}/migrations/$_job/received.tar',
      );
      await transport.copy(
        _job,
        TermuxMigrationItem.projects,
        archive,
        destination,
        cancelled: () => false,
      );
      expect(await destination.readAsBytes(), bytes);
      expect((await destination.parent.stat()).mode & 0x1ff, 0x1c0);
      expect((await destination.parent.parent.stat()).mode & 0x1ff, 0x1c0);
      expect((await destination.stat()).mode & 0x1ff, 0x180);
      await transport.cancel(_job);
      final resumed = await transport.pack(
        _job,
        TermuxMigrationItem.projects,
        maxBytes: 1024 * 1024,
      );
      expect(resumed.sha256, archive.sha256);
      expect(await File('$home/.oc/migration/$_job/cancel').exists(), isFalse);
    },
  );

  group('link2symlink links', () {
    late Directory directory;
    late String prefix;
    late String home;
    late Directory project;
    late Directory store;

    Future<TermuxCommandResult> run(
      String script, {
      required Duration timeout,
    }) async {
      final process = await Process.start(
        'bash',
        ['-s'],
        environment: {'HOME': home, 'PREFIX': prefix},
      );
      final output = process.stdout.transform(utf8.decoder).join();
      final errors = process.stderr.drain<void>();
      process.stdin.write(script);
      await process.stdin.close();
      final exit = await process.exitCode.timeout(timeout);
      await errors;
      return TermuxCommandResult(
        stdout: await output,
        stderr: '',
        exitCode: exit,
        errorCode: -1,
        errorMessage: '',
      );
    }

    setUp(() async {
      directory = await Directory.systemTemp.createTemp('migration l2s-');
      addTearDown(() => directory.delete(recursive: true));
      prefix = '${directory.path}/usr';
      home = '${directory.path}/home';
      final rootfs =
          '$prefix/var/lib/proot-distro/containers/opencode-ubuntu/rootfs';
      project = Directory('$rootfs/root/projects/example');
      store = Directory('$rootfs/.l2s');
      await project.create(recursive: true);
      await store.create(recursive: true);
      await Directory(home).create();
      await File('${project.path}/README.md').writeAsString('readme\n');
      await Directory(
        '${project.path}/.git/objects/ab',
      ).create(recursive: true);
      await File('${store.path}/.l2s.tmp_obj_00010001').writeAsString('OBJECT');
      await Link(
        '${project.path}/.git/objects/ab/cdef',
      ).create('${store.path}/.l2s.tmp_obj_00010001');
    });

    Future<Map<String, (String, String)>> packedEntries() async {
      final transport = BridgeTermuxMigrationTransport(run: run);
      await transport.pack(
        _job,
        TermuxMigrationItem.projects,
        maxBytes: 1024 * 1024,
      );
      final listing = await Process.run('tar', [
        '-tvf',
        '$home/.oc/migration/$_job/projects.tar',
      ]);
      final entries = <String, (String, String)>{};
      for (final line in (listing.stdout as String).trim().split('\n')) {
        final name = line.split(RegExp(r'\s+')).last;
        entries[name] = (line.substring(0, 1), line);
      }
      return entries;
    }

    test('are archived as their target contents, not as links', () async {
      final entries = await packedEntries();
      expect(entries['./example/.git/objects/ab/cdef']?.$1, '-');
      expect(entries['./example/README.md']?.$1, '-');
      final out = await Directory('${directory.path}/out').create();
      await Process.run('tar', [
        '-xf',
        '$home/.oc/migration/$_job/projects.tar',
        '-C',
        out.path,
      ]);
      final restored = File('${out.path}/example/.git/objects/ab/cdef');
      expect(FileSystemEntity.isLinkSync(restored.path), isFalse);
      expect(await restored.readAsString(), 'OBJECT');
    });

    test('inspect counts the target bytes and accepts the project', () async {
      final inventory = await BridgeTermuxMigrationTransport(
        run: run,
      ).inspect();
      final projects = inventory.items.singleWhere(
        (item) => item.item == TermuxMigrationItem.projects,
      );
      expect(projects.problem, isNull);
      expect(projects.bytes, 'readme\n'.length + 'OBJECT'.length);
    });

    test('an unreadable target is named, not migrated half-broken', () async {
      await File('${store.path}/.l2s.tmp_obj_00010001').delete();
      final inventory = await BridgeTermuxMigrationTransport(
        run: run,
      ).inspect();
      final projects = inventory.items.singleWhere(
        (item) => item.item == TermuxMigrationItem.projects,
      );
      expect(projects.problem, TermuxMigrationFailure.linkedFileUnreadable);
      await expectLater(
        BridgeTermuxMigrationTransport(
          run: run,
        ).pack(_job, TermuxMigrationItem.projects, maxBytes: 1024 * 1024),
        throwsA(_failure(TermuxMigrationFailure.linkedFileUnreadable)),
      );
    });

    for (final absolute in [false, true]) {
      test(
        'an ordinary ${absolute ? 'absolute' : 'relative'} symlink is still refused',
        () async {
          await Link(
            '${project.path}/link',
          ).create(absolute ? '/usr/bin/env' : 'README.md');
          final inventory = await BridgeTermuxMigrationTransport(
            run: run,
          ).inspect();
          final projects = inventory.items.singleWhere(
            (item) => item.item == TermuxMigrationItem.projects,
          );
          expect(projects.problem, TermuxMigrationFailure.unsupportedEntry);
        },
      );
    }
  });

  for (final suffix in ['', '.offset', '.offset.tmp']) {
    test(
      'copy refuses a staging link $suffix without changing its target',
      () async {
        final directory = await Directory.systemTemp.createTemp(
          'migration-link-test-',
        );
        addTearDown(() => directory.delete(recursive: true));
        final destination = File(
          '${directory.path}/migrations/$_job/archive.tar',
        );
        await destination.parent.create(recursive: true);
        final original = File('${directory.path}/original');
        await original.writeAsString('preserve');
        await Link('${destination.path}$suffix').create(original.path);
        final transport = BridgeTermuxMigrationTransport(
          run: (_, {required timeout}) async {
            fail('No bridge dispatch for unsafe storage');
          },
        );
        await expectLater(
          transport.copy(
            _job,
            TermuxMigrationItem.projects,
            TermuxMigrationArchive(bytes: 1024, sha256: 'a' * 64),
            destination,
            cancelled: () => false,
          ),
          throwsA(_failure(TermuxMigrationFailure.destinationConflict)),
        );
        expect(await original.readAsString(), 'preserve');
      },
    );
  }

  test('cancel before transfer dispatches nothing', () async {
    final transport = BridgeTermuxMigrationTransport(
      run: (_, {required timeout}) async {
        fail('No bridge call expected');
      },
    );
    await expectLater(
      transport.copy(
        _job,
        TermuxMigrationItem.projects,
        TermuxMigrationArchive(bytes: 1024, sha256: 'a' * 64),
        File('/unused'),
        cancelled: () => true,
      ),
      throwsA(_failure(TermuxMigrationFailure.cancelled)),
    );
  });
}

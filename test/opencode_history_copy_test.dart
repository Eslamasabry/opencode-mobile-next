import 'dart:convert';
import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/dialect.dart';
import 'package:opencode_mobile/api2/gateway_operations.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/domain/history_migration.dart';
import 'package:opencode_mobile/domain/opencode_history_copy.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';

class _Real extends HttpOverrides {}

void main() {
  group('the switch carries OpenCode 1 chats into OpenCode 2', () {
    test('the in-app OpenCode 2 start copies before the first start', () {
      final two = BuiltinLinux.serverScript(runtime: OpenCodeRuntime.openCode2);
      expect(two, contains(openCode1HistoryCopyScript));
      expect(
        two.indexOf(openCode1HistoryCopyScript),
        lessThan(two.indexOf('exec opencode2 serve')),
      );
      // Only after the isolated folder exists.
      expect(
        two.indexOf('mkdir -p /root/.oc-opencode2/data/opencode'),
        lessThan(two.indexOf(openCode1HistoryCopyScript)),
      );
      expect(
        () => BuiltinLinux.serverScript(runtime: OpenCodeRuntime.openCode1),
        throwsA(isA<BuiltinLinuxException>()),
        reason: 'The legacy runtime can never be started',
      );
    });

    test('copy failures cannot be swallowed by the launcher', () {
      expect(
        BuiltinLinux.serverScript(runtime: OpenCodeRuntime.openCode2),
        contains('(\n$openCode1HistoryCopyScript) || exit 74'),
      );
    });

    test('the guard never overwrites and copies all three files', () {
      expect(
        openCode1HistoryCopyScript,
        contains('[ ! -e "\$oc2_dir/opencode.db" ]'),
      );
      for (final name in [
        'opencode.db',
        'opencode.db-wal',
        'opencode.db-shm',
      ]) {
        expect(openCode1HistoryCopyScript, contains('cp "\$oc1_dir/$name"'));
      }
      expect(openCode1HistoryCopyScript, isNot(contains('mv "\$oc1_dir')));
      expect(openCode1HistoryCopyScript, isNot(contains('rm -f "\$oc1_dir')));
    });

    group('the snippet itself, run in a scratch home', () {
      late Directory home;
      late Directory oc1;
      late Directory oc2;

      setUp(() {
        home = Directory.systemTemp.createTempSync('oc1copy');
        oc1 = Directory('${home.path}/.local/share/opencode')
          ..createSync(recursive: true);
        oc2 = Directory('${home.path}/oc2/data/opencode');
      });
      tearDown(() => home.deleteSync(recursive: true));

      ProcessResult run({String source = openCode1HistoryCopyScript}) {
        final script = source.replaceAll(
          '/root/.oc-opencode2/data/opencode',
          oc2.path,
        );
        return Process.runSync(
          'sh',
          ['-c', script],
          environment: {'HOME': home.path},
        );
      }

      test('copies db, wal and shm; OpenCode 1 stays whole', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('db');
        File('${oc1.path}/opencode.db-wal').writeAsStringSync('wal');
        File('${oc1.path}/opencode.db-shm').writeAsStringSync('shm');
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'db');
        expect(File('${oc2.path}/opencode.db-wal').readAsStringSync(), 'wal');
        expect(File('${oc2.path}/opencode.db-shm').readAsStringSync(), 'shm');
        expect(File('${oc1.path}/opencode.db').readAsStringSync(), 'db');
        expect(File('${oc1.path}/opencode.db-wal').existsSync(), isTrue);
        expect(File('${oc1.path}/opencode.db-shm').existsSync(), isTrue);
        expect(oc2.listSync().map((e) => e.path.split('/').last).toSet(), {
          'opencode.db',
          'opencode.db-wal',
          'opencode.db-shm',
          '.oc1-import-source.sha256',
        }, reason: 'no scratch folder left behind');
      });

      test('a database alone is enough (no wal or shm yet)', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('db');
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'db');
        expect(File('${oc2.path}/opencode.db-wal').existsSync(), isFalse);
      });

      test('never overwrites an OpenCode 2 database that exists', () {
        oc2.createSync(recursive: true);
        File('${oc2.path}/opencode.db').writeAsStringSync('mine');
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        File('${oc1.path}/opencode.db-wal').writeAsStringSync('oldwal');
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'mine');
        expect(File('${oc2.path}/opencode.db-wal').existsSync(), isFalse);
      });

      test('explicit move refuses two histories without changing either', () {
        oc2.createSync(recursive: true);
        for (final suffix in ['', '-wal', '-shm']) {
          File(
            '${oc1.path}/opencode.db$suffix',
          ).writeAsStringSync('old$suffix');
          File(
            '${oc2.path}/opencode.db$suffix',
          ).writeAsStringSync('new$suffix');
        }
        final result = run(source: openCode1HistorySwitchCheckScript);
        expect(result.exitCode, 74);
        expect(result.stderr, contains(openCode1HistorySwitchBlockedMessage));
        for (final suffix in ['', '-wal', '-shm']) {
          expect(
            File('${oc1.path}/opencode.db$suffix').readAsStringSync(),
            'old$suffix',
          );
          expect(
            File('${oc2.path}/opencode.db$suffix').readAsStringSync(),
            'new$suffix',
          );
        }
      });

      test('completed copy can resume even after OC2 converted its target', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('original');
        expect(run().exitCode, 0);
        File('${oc2.path}/opencode.db').writeAsStringSync('converted');
        expect(run(source: openCode1HistorySwitchCheckScript).exitCode, 0);
        expect(File('${oc1.path}/opencode.db').readAsStringSync(), 'original');
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'converted');
      });

      test('changed original cannot reuse a prior copy receipt', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('original');
        expect(run().exitCode, 0);
        File('${oc1.path}/opencode.db-wal').writeAsStringSync('later chat');
        expect(run(source: openCode1HistorySwitchCheckScript).exitCode, 74);
        expect(
          File('${oc1.path}/opencode.db-wal').readAsStringSync(),
          'later chat',
        );
      });

      test('interrupted verified sidecar placement safely resumes', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('original');
        File('${oc1.path}/opencode.db-wal').writeAsStringSync('wal');
        expect(run().exitCode, 0);
        File('${oc2.path}/opencode.db').deleteSync();
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'original');
        expect(File('${oc1.path}/opencode.db-wal').readAsStringSync(), 'wal');
      });

      test('explicit move permits a fresh target without modifying source', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        expect(run(source: openCode1HistorySwitchCheckScript).exitCode, 0);
        expect(File('${oc1.path}/opencode.db').readAsStringSync(), 'old');
        expect(oc2.existsSync(), isFalse);
      });

      test('explicit move refuses a symlinked source database', () {
        final outside = File('${home.path}/elsewhere')
          ..writeAsStringSync('old');
        Link('${oc1.path}/opencode.db').createSync(outside.path);
        expect(run(source: openCode1HistorySwitchCheckScript).exitCode, 74);
        expect(outside.readAsStringSync(), 'old');
        expect(oc2.existsSync(), isFalse);
      });

      test('ordinary OC2 start stays available with both histories', () {
        oc2.createSync(recursive: true);
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        File('${oc2.path}/opencode.db').writeAsStringSync('new');
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').readAsStringSync(), 'new');
      });

      test('copy failure stops startup and preserves the source', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        oc2.parent.createSync(recursive: true);
        File(oc2.path).writeAsStringSync('not a directory');
        final result = run();
        expect(result.exitCode, 74);
        expect(
          result.stderr,
          contains('Your earlier conversations could not be copied'),
        );
        expect(File('${oc1.path}/opencode.db').readAsStringSync(), 'old');
        expect(File(oc2.path).readAsStringSync(), 'not a directory');
      });

      test('failed WAL placement never publishes the database or starts', () {
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        File('${oc1.path}/opencode.db-wal').writeAsStringSync('wal');
        final result = run(
          source:
              'mv() { return 1; }\n'
              '$openCode1HistoryCopyScript\n'
              'echo server-started',
        );
        expect(result.exitCode, 74);
        expect(result.stdout, isNot(contains('server-started')));
        expect(File('${oc2.path}/opencode.db').existsSync(), isFalse);
        expect(File('${oc1.path}/opencode.db').readAsStringSync(), 'old');
        expect(File('${oc1.path}/opencode.db-wal').readAsStringSync(), 'wal');
      });

      test('orphaned target WAL refuses a fresh copy', () {
        oc2.createSync(recursive: true);
        File('${oc1.path}/opencode.db').writeAsStringSync('old');
        File('${oc2.path}/opencode.db-wal').writeAsStringSync('unresolved');
        expect(run().exitCode, 74);
        expect(File('${oc2.path}/opencode.db').existsSync(), isFalse);
        expect(
          File('${oc2.path}/opencode.db-wal').readAsStringSync(),
          'unresolved',
        );
      });

      test('no OpenCode 1 database is a quiet no-op', () {
        expect(run().exitCode, 0);
        expect(File('${oc2.path}/opencode.db').existsSync(), isFalse);
      });
    });
  });

  group('migration status', () {
    test('maps the four answers', () {
      HistoryMigrationStatus? read(Object json) =>
          HistoryMigrationStatus.fromJson(json);
      expect(
        read({'status': 'required'})!.phase,
        HistoryMigrationPhase.required,
      );
      expect(read({'status': 'completed'})!.pending, isFalse);
      final running = read({
        'status': 'running',
        'progress': {
          'label': 'Migrating sessions',
          'numerator': 3,
          'denominator': 12,
        },
      })!;
      expect(running.phase, HistoryMigrationPhase.running);
      expect(running.label, 'Migrating sessions');
      expect((running.done, running.total), (3, 12));
      expect(running.pending, isTrue);
      final failed = read({'status': 'error', 'error': 'disk full'})!;
      expect(failed.phase, HistoryMigrationPhase.error);
      expect(failed.error, 'disk full');
      expect(failed.pending, isFalse);
    });

    test('accepts the data envelope and rejects unknown shapes', () {
      expect(
        HistoryMigrationStatus.fromJson({
          'data': {'status': 'completed'},
        })!.phase,
        HistoryMigrationPhase.completed,
      );
      expect(HistoryMigrationStatus.fromJson({'status': 'weird'}), isNull);
      expect(HistoryMigrationStatus.fromJson('x'), isNull);
      expect(HistoryMigrationStatus.fromJson(null), isNull);
    });

    test('the page line is plain words; the server text stays out of it', () {
      final l10n = lookupAppLocalizations(const Locale('en'));
      expect(historyLine(l10n, null), isNull);
      expect(
        historyLine(
          l10n,
          const HistoryMigrationStatus(HistoryMigrationPhase.completed),
        ),
        isNull,
      );
      expect(
        historyLine(
          l10n,
          const HistoryMigrationStatus(HistoryMigrationPhase.required),
        ),
        'Bringing your earlier conversations across…',
      );
      expect(
        historyLine(
          l10n,
          const HistoryMigrationStatus(
            HistoryMigrationPhase.running,
            done: 3,
            total: 12,
          ),
        ),
        'Bringing your earlier conversations across (3 of 12)…',
      );
      final error = historyLine(
        l10n,
        const HistoryMigrationStatus(
          HistoryMigrationPhase.error,
          error: 'SQLITE_FULL',
        ),
      )!;
      expect(
        error,
        contains('original data is still on this phone, untouched'),
      );
      expect(error, isNot(contains('SQLITE')));
    });

    test('the gateway reads GET /experimental/migration/v1', () async {
      await HttpOverrides.runZoned(() async {
        final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
        final paths = <String>[];
        server.listen((request) async {
          paths.add(request.uri.path);
          request.response.headers.contentType = ContentType.json;
          request.response.write(
            jsonEncode({
              'status': 'running',
              'progress': {
                'label': 'Migrating sessions',
                'numerator': 1,
                'denominator': 2,
              },
            }),
          );
          await request.response.close();
        });
        final client = Api2Client.connect(
          baseUrl: 'http://${server.address.host}:${server.port}',
          password: 'pw',
        );
        client.transport.settleDialect(Api2Dialect.stable);
        try {
          final status = await Api2OperationsGateway(
            client: client,
          ).historyMigrationStatus();
          expect(paths, ['/api/experimental/migration/v1']);
          expect(status!.done, 1);
        } finally {
          client.close();
          await server.close(force: true);
        }
      }, createHttpClient: (_) => _Real().createHttpClient(null));
    });
  });
}

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/setup_scripts.dart';

void main() {
  late Directory temporary;
  late Map<String, String> environment;
  setUp(() {
    temporary = Directory.systemTemp.createTempSync('oc-apt-repair-');
    final bin = Directory('${temporary.path}/bin')..createSync();
    final lists = Directory('${temporary.path}/lists')..createSync();
    File('${lists.path}/test_Packages').writeAsStringSync('');
    File('${temporary.path}/stamp').writeAsStringSync('');
    File('${bin.path}/dpkg').writeAsStringSync(r'''#!/bin/sh
printf 'dpkg %s\n' "$*" >> "$TEST_LOG"
case "$1" in
  --configure)
    [ "$TEST_MODE" != repair ] || [ -f "$TEST_LOG.repaired" ] || exit 1
    [ "$TEST_MODE" != broken ] || exit 1 ;;
  --audit)
    if [ "$TEST_MODE" = broken ] || [ "$TEST_MODE" = audit ]; then
      echo 'The following packages are only half configured: test-package'
    fi ;;
  -s)
    [ "$TEST_MODE" != missing ] || exit 1
    if [ "$TEST_MODE" = half ]; then
      echo 'Status: install ok half-configured'
    else
      echo 'Status: install ok installed'
    fi ;;
esac
''');
    File('${bin.path}/apt-get').writeAsStringSync(r'''#!/bin/sh
printf 'apt %s\n' "$*" >> "$TEST_LOG"
case " $* " in
  *" -f "*)
    [ "$TEST_MODE" != broken ] || exit 100
    touch "$TEST_LOG.repaired" ;;
esac
''');
    Process.runSync('chmod', [
      '755',
      '${bin.path}/dpkg',
      '${bin.path}/apt-get',
    ]);
    environment = {
      'PATH': '${bin.path}:/usr/bin:/bin',
      'OC_APT_STAMP': '${temporary.path}/stamp',
      'OC_APT_LISTS': lists.path,
      'TEST_LOG': '${temporary.path}/calls',
    };
  });
  tearDown(() => temporary.deleteSync(recursive: true));

  Future<ProcessResult> install(String mode, String shell) => Process.run(
    shell,
    [
      '-c',
      'set -eu\n${withSetupPrelude("oc_apt_install test-package\necho FINISHED")}',
    ],
    environment: {...environment, 'TEST_MODE': mode},
  );

  for (final shell in ['dash', 'bash']) {
    test('$shell repairs dependency interruption before installing', () async {
      final result = await install('repair', shell);
      expect(result.exitCode, 0, reason: '${result.stderr}');
      final calls = File(environment['TEST_LOG']!).readAsLinesSync();
      final repair = calls.indexWhere(
        (line) => line.startsWith('apt ') && line.contains(' -f '),
      );
      expect(repair, greaterThan(0));
      expect(calls[repair + 1], 'dpkg --configure -a');
      expect(calls.last, 'dpkg -s test-package');
      expect(result.stdout, contains('FINISHED'));
    });

    test('$shell refuses failed repair and gives dpkg audit details', () async {
      final result = await install('broken', shell);
      expect(result.exitCode, isNot(0));
      expect('${result.stdout}\n${result.stderr}', contains('dpkg --audit'));
      expect(
        '${result.stdout}\n${result.stderr}',
        contains('half configured: test-package'),
      );
      expect(result.stdout, isNot(contains('FINISHED')));
    });

    test('$shell catches audit findings even when audit exits zero', () async {
      final result = await install('audit', shell);
      expect(result.exitCode, isNot(0));
      expect('${result.stdout}\n${result.stderr}', contains('dpkg --audit'));
      final calls = File(environment['TEST_LOG']!).readAsStringSync();
      expect(calls, isNot(contains('apt ')));
    });

    for (final mode in ['missing', 'half']) {
      test(
        '$shell verifies dpkg status after apt claims success: $mode',
        () async {
          final result = await install(mode, shell);
          expect(result.exitCode, isNot(0));
          expect(result.stdout, isNot(contains('FINISHED')));
          expect(
            File(environment['TEST_LOG']!).readAsStringSync(),
            contains('dpkg -s test-package'),
          );
        },
      );
    }
  }
}

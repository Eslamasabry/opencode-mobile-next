import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

String? _kotlinc() {
  final configured = Platform.environment['KOTLINC'];
  if (configured != null && configured.isNotEmpty) return configured;
  for (final dir in (Platform.environment['PATH'] ?? '').split(':')) {
    if (dir.isNotEmpty && File('$dir/kotlinc').existsSync()) return 'kotlinc';
  }
  final sdkman =
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  return File(sdkman).existsSync() ? sdkman : null;
}

// Top-level class members have four spaces; nested functions have eight.
String _member(String source, String signature) {
  final begin = source.indexOf('    $signature');
  expect(begin, greaterThanOrEqualTo(0), reason: signature);
  final next = RegExp(
    r'\n    (?:(?:private|internal|public) )?(?:fun |val |var |/\*\*|@)',
  ).firstMatch(source.substring(begin + signature.length + 4));
  expect(next, isNotNull, reason: 'next member after $signature');
  return source.substring(begin, begin + signature.length + 4 + next!.start);
}

void main() {
  final compiler = _kotlinc();
  final skip = compiler == null
      ? 'kotlinc not found (set KOTLINC); admission fixture not run'
      : null;
  late Directory temporary;
  late String jar;

  setUpAll(() async {
    if (compiler == null) return;
    temporary = await Directory.systemTemp.createTemp(
      'oc-installer-admission-',
    );
    jar = '${temporary.path}/admission.jar';
    const sources =
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile';
    final linux = await File('$sources/BuiltinLinux.kt').readAsString();
    final reclaim = _member(linux, 'private fun reclaimDeadInstaller(');
    final checkAdmission = _member(linux, 'private fun admitCheckProcess(');
    final qualified = _member(linux, 'private fun startQualifiedInstaller(');
    final launch = qualified.indexOf('        val nonce =');
    expect(launch, greaterThan(0));
    // Keep every real pre-launch admission guard. Replace only the external
    // process launch after those guards, which this host fixture cannot run.
    final installerAdmission =
        '${qualified.substring(0, launch)}        return recordAdmission(targets, operation)\n    }\n';
    final constants = RegExp(
      r'private const val (?:RUN_ADMISSION_WINDOW_MS|PROCESS_POLL_MS) = [^\n]+',
    ).allMatches(linux).map((match) => match.group(0)!).join('\n');
    expect(constants.split('\n'), hasLength(2));
    final template = await File(
      'test/native/installer_admission_harness.kt',
    ).readAsString();
    final fixture = File('${temporary.path}/ProductionAdmission.kt');
    await fixture.writeAsString(
      template
          .replaceFirst('/* PRODUCTION_RECLAIM */', reclaim)
          .replaceFirst('/* PRODUCTION_CHECK_ADMISSION */', checkAdmission)
          .replaceFirst(
            '/* PRODUCTION_INSTALLER_ADMISSION */',
            installerAdmission,
          )
          .replaceFirst('/* PRODUCTION_CONSTANTS */', constants),
    );
    final clock = File('${temporary.path}/SystemClock.kt');
    await clock.writeAsString('''
package android.os
object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1000000L
}
''');
    final compiled = await Process.run(compiler, [
      fixture.path,
      clock.path,
      '$sources/NativeInstallerAdmission.kt',
      '$sources/NativeInstallerOwnership.kt',
      '$sources/NativeInstallerVisibility.kt',
      '$sources/NativeRuntimeOwnership.kt',
      '-include-runtime',
      '-d',
      jar,
    ]);
    expect(compiled.exitCode, 0, reason: '${compiled.stderr}');
  });

  tearDownAll(() async {
    if (compiler == null) return;
    await temporary.delete(recursive: true);
  });

  for (final scenario in [
    'check-cached-dead',
    'check-durable-dead',
    'install-cached-dead',
    'install-durable-dead',
    'check-living-owner',
    'install-living-owner',
    'check-orphan',
    'install-orphan',
    'check-unknown',
    'install-unknown',
    'check-uncertain-absence',
    'install-uncertain-absence',
    'install-prepared-dead',
    'install-clear-failed',
    'install-cached-clear-failed',
    'cold-durable-dead',
    'cold-install-durable-dead',
    'normal-check',
    'normal-install',
  ]) {
    test('native installer admission: $scenario', skip: skip, () async {
      final result = await Process.run(Platform.environment['JAVA'] ?? 'java', [
        '-jar',
        jar,
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, 'PASS $scenario\n');
    });
  }
}

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

String? _executable(String name, {String? configured, String? fallback}) {
  if (configured != null && configured.isNotEmpty) return configured;
  for (final directory in (Platform.environment['PATH'] ?? '').split(':')) {
    if (directory.isNotEmpty && File('$directory/$name').existsSync()) {
      return '$directory/$name';
    }
  }
  return fallback != null && File(fallback).existsSync() ? fallback : null;
}

void main() {
  final native = File(
    'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/BuiltinLinux.kt',
  ).readAsStringSync();

  test('native Stop wins an admitted wake request racing shutdown', () async {
    final home = Platform.environment['HOME'];
    final compiler = _executable(
      'kotlinc',
      configured: Platform.environment['KOTLINC'],
      fallback: '$home/.sdkman/candidates/kotlin/current/bin/kotlinc',
    );
    final java = _executable('java', configured: Platform.environment['JAVA']);
    if (compiler == null || java == null) {
      markTestSkipped('Kotlin/Java unavailable; native race not executed');
      return;
    }
    final method = RegExp(
      r'    fun setChatWorkLease\([\s\S]*?\n    }',
    ).firstMatch(native)?.group(0);
    expect(method, isNotNull);
    final stopTree = RegExp(
      r'        fun stopTree\([\s\S]*?\n        }',
    ).firstMatch(native)?.group(0);
    expect(stopTree, isNotNull);
    final permissions = File(
      'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/TermuxActivityPermissions.kt',
    ).readAsStringSync();
    final answer = RegExp(
      r'when \{\s+granted -> "granted"[\s\S]*?else -> "denied"\s+}',
    ).firstMatch(permissions)?.group(0);
    expect(answer, isNotNull);
    final temporary = await Directory.systemTemp.createTemp('reply-work-race-');
    addTearDown(() => temporary.delete(recursive: true));
    final source = File('${temporary.path}/race.kt');
    source.writeAsStringSync(
      File('test/native/reply_work_hold_harness.kt')
          .readAsStringSync()
          .replaceFirst('    // PRODUCTION_HOLD_METHOD', method!)
          .replaceFirst('// PRODUCTION_STOP_TREE', stopTree!)
          .replaceFirst('// PRODUCTION_PERMISSION_ANSWER', answer!),
    );
    final jar = '${temporary.path}/race.jar';
    final compiled = await Process.run(compiler, [
      source.path,
      '-include-runtime',
      '-d',
      jar,
    ]);
    expect(compiled.exitCode, 0, reason: '${compiled.stderr}');
    final ran = await Process.run(java, ['-jar', jar]);
    expect(ran.exitCode, 0, reason: '${ran.stderr}');
    expect(ran.stdout, contains('PASS reply work admission versus Stop'));
  });
}

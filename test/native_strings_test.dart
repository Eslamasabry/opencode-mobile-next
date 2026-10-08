import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final compiler =
      Platform.environment['KOTLINC'] ??
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final skip = File(compiler).existsSync() ? null : 'kotlinc unavailable';
  late String jar;
  late Directory generated;
  setUpAll(() async {
    if (skip != null) return;
    generated = Directory.systemTemp.createTempSync('bd10-native-resources-');
    final xml = File(
      'android/app/src/main/res/values/strings.xml',
    ).readAsStringSync();
    final resources = RegExp(
      r'<(string|plurals) name="([^"]+)"',
    ).allMatches(xml).toList();
    final ids = <String, int>{
      for (var i = 0; i < resources.length; i++) resources[i][2]!: i + 1,
    };
    final stub = File('${generated.path}/resource_ids.kt');
    stub.writeAsStringSync(
      'package io.github.eslamasabry.opencode_mobile\n'
      'object R {\n'
      'object string {\n'
      '${resources.where((m) => m[1] == 'string').map((m) => 'const val ${m[2]} = ${ids[m[2]]}').join('\n')}\n'
      '}\nobject plurals {\n'
      '${resources.where((m) => m[1] == 'plurals').map((m) => 'const val ${m[2]} = ${ids[m[2]]}').join('\n')}\n'
      '}\n}\n'
      'object ResourceNames {\nval names = mapOf(\n'
      '${ids.entries.map((e) => '${e.value} to "${e.key}"').join(',\n')}\n)\n'
      'val numericStringIds = setOf<Int>(\n'
      '${resources.where((m) => m[1] == 'string' && RegExp('<string name="${m[2]}">[^<]*%[0-9]+\\\$d').hasMatch(xml)).map((m) => ids[m[2]]).join(',')}\n)\n'
      'val pluralIds = setOf<Int>(\n'
      '${resources.where((m) => m[1] == 'plurals').map((m) => ids[m[2]]).join(',')}\n)\n}\n',
    );
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeStrings.kt',
        'test/native/native_strings_harness.kt',
        'test/native/native_strings_stubs/context.kt',
        'test/native/native_strings_stubs/resources.kt',
        stub.path,
      ],
    );
  });
  tearDownAll(() {
    if (skip == null) generated.deleteSync(recursive: true);
  });
  for (final scenario in [
    'arabic-override',
    'english-override',
    'system-ar',
    'system-list',
    'untranslated-app',
    'unsupported-system',
    'invalid-choice',
    'read-refused',
    'choice-changes',
    'resource-formatting',
  ]) {
    test('native resource locale: $scenario', skip: skip, () async {
      final result = await Process.run('java', [
        '-cp',
        jar,
        'io.github.eslamasabry.opencode_mobile.Native_strings_harnessKt',
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, contains('PASS $scenario'));
    });
  }
}

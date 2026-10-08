import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xml/xml.dart';

import 'native/kotlin_jar_cache.dart';

void main() {
  final compiler =
      Platform.environment['KOTLINC'] ??
      '${Platform.environment['HOME']}/.sdkman/candidates/kotlin/current/bin/kotlinc';
  final skip = File(compiler).existsSync() ? null : 'kotlinc unavailable';
  late String jar;
  Directory? generated;
  setUpAll(() async {
    if (skip != null) return;
    generated = Directory.systemTemp.createTempSync('bd10-locale-refresh-');
    final xml = XmlDocument.parse(
      File('android/app/src/main/res/values/strings.xml').readAsStringSync(),
    );
    final resources = xml.rootElement.childElements
        .where((entry) => ['string', 'plurals'].contains(entry.name.local))
        .toList();
    final ids = <String, int>{
      for (var index = 0; index < resources.length; index++)
        resources[index].getAttribute('name')!: index + 1,
    };
    final stub = File('${generated!.path}/resource_ids.kt');
    stub.writeAsStringSync(
      'package io.github.eslamasabry.opencode_mobile\n'
      'object R {\n'
      '${['string', 'plurals'].map((kind) => 'object $kind {\n${resources.where((entry) => entry.name.local == kind).map((entry) => 'const val ${entry.getAttribute('name')} = ${ids[entry.getAttribute('name')]}').join('\n')}\n}').join('\n')}\n}\n'
      'object ResourceNames {\nval names = mapOf(\n'
      '${ids.entries.map((entry) => '${entry.value} to "${entry.key}"').join(',\n')}\n)\n'
      'val pluralIds = setOf<Int>(\n'
      '${resources.where((entry) => entry.name.local == 'plurals').map((entry) => ids[entry.getAttribute('name')]).join(',')}\n)\n}\n',
    );
    jar = await cachedKotlinJar(
      compiler: compiler,
      sources: [
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeNotificationLocale.kt',
        'android/app/src/main/kotlin/io/github/eslamasabry/opencode_mobile/NativeStrings.kt',
        'test/native/native_locale_refresh_harness.kt',
        'test/native/native_strings_stubs/resources.kt',
        ...Directory(
          'test/native/native_locale_refresh_stubs',
        ).listSync().whereType<File>().map((file) => file.path),
        stub.path,
      ],
    );
  });
  tearDownAll(() => generated?.deleteSync(recursive: true));
  for (final scenario in [
    'phone-fallback',
    'supplied-title',
    'channel-policy',
    'empty-surfaces',
  ]) {
    test('native locale refresh: $scenario', skip: skip, () async {
      final result = await Process.run('java', [
        '-cp',
        jar,
        'io.github.eslamasabry.opencode_mobile.Native_locale_refresh_harnessKt',
        scenario,
      ]);
      expect(result.exitCode, 0, reason: '${result.stdout}\n${result.stderr}');
      expect(result.stdout, contains('PASS $scenario'));
    });
  }
}

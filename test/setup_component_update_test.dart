import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/setup/component_updates.dart';

void main() {
  late Directory temp;
  late String target;
  setUp(() {
    temp = Directory.systemTemp.createTempSync('component-journal-');
    target = '${temp.path}/active';
  });
  tearDown(() => temp.deleteSync(recursive: true));

  Future<ProcessResult> run(String script) => Process.run('/bin/sh', [
    '-c',
    'set -eu\n$componentUpdatePrelude\n$script',
  ]);

  test(
    'fresh process restores uncommitted activation and replay is safe',
    () async {
      File(target).writeAsStringSync('good');
      File('$target.new').writeAsStringSync('bad');
      final activated = await run('oc_update_activate "$target" "$target.new"');
      expect(activated.exitCode, 0, reason: '${activated.stderr}');
      expect(File(target).readAsStringSync(), 'bad');
      for (var i = 0; i < 2; i++) {
        final recovered = await run('oc_update_recover "$target"');
        expect(recovered.exitCode, 0, reason: '${recovered.stderr}');
        expect(File(target).readAsStringSync(), 'good');
        expect(File('$target.oc-pending').existsSync(), isFalse);
      }
    },
  );

  test(
    'commit retains only the immediately preceding good generation',
    () async {
      File(target).writeAsStringSync('first');
      for (final version in ['second', 'third']) {
        File('$target.new').writeAsStringSync(version);
        final committed = await run('''
oc_update_activate "$target" "$target.new"
oc_update_commit "$target"
''');
        expect(committed.exitCode, 0, reason: '${committed.stderr}');
      }
      expect(File(target).readAsStringSync(), 'third');
      expect(File('$target.oc-good').readAsStringSync(), 'second');
      expect(File('$target.oc-pending').existsSync(), isFalse);
    },
  );

  test('first-install recovery removes incomplete activation', () async {
    File('$target.new').writeAsStringSync('bad');
    expect(
      (await run('oc_update_activate "$target" "$target.new"')).exitCode,
      0,
    );
    expect((await run('oc_update_recover "$target"')).exitCode, 0);
    expect(File(target).existsSync(), isFalse);
  });

  test(
    'recovery restores launch links without following their targets',
    () async {
      final good = File('${temp.path}/good')..writeAsStringSync('good');
      final other = File('${temp.path}/other')..writeAsStringSync('other');
      Link(target).createSync(good.path);
      Link('$target.new').createSync(other.path);
      expect(
        (await run('oc_update_activate "$target" "$target.new"')).exitCode,
        0,
      );
      expect((await run('oc_update_recover "$target"')).exitCode, 0);
      expect(Link(target).targetSync(), good.path);
      expect(other.readAsStringSync(), 'other');
    },
  );

  test(
    'corrupt receipt fails closed and exposes only fixed guidance',
    () async {
      File(target).writeAsStringSync('active');
      File('$target.oc-good').writeAsStringSync('good');
      File('$target.oc-pending').writeAsStringSync('invalid-private-content');
      final result = await run('oc_update_recover "$target"');
      expect(result.exitCode, isNot(0));
      expect(
        result.stderr,
        contains('A component update could not be restored. Run setup again.'),
      );
      expect(result.stderr, isNot(contains('invalid-private-content')));
      expect(File(target).readAsStringSync(), 'active');
      expect(File('$target.oc-good').readAsStringSync(), 'good');
      expect(File('$target.oc-pending').existsSync(), isTrue);
    },
  );

  test('interruption before old move or after restore is replayable', () async {
    File(target).writeAsStringSync('good');
    File('$target.oc-pending').writeAsStringSync('existing\n');
    expect((await run('oc_update_recover "$target"')).exitCode, 0);
    expect(File(target).readAsStringSync(), 'good');
    File('$target.oc-pending').writeAsStringSync('existing\n');
    File(target).deleteSync();
    expect((await run('oc_update_recover "$target"')).exitCode, isNot(0));
    expect(File('$target.oc-pending').existsSync(), isTrue);
  });
}

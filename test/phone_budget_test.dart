import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/agents/phone_budget.dart';

void main() {
  late Directory files;
  late Directory claude;

  setUp(() {
    files = Directory.systemTemp.createTempSync('phone_budget_');
    // The profile's home exists once an agent was set up.
    Directory(
      '${files.path}/linux/ubuntu/home/oc/.oc-profiles/p1',
    ).createSync(recursive: true);
    claude = PhoneBudgetFiles.claudeFolder(files, 'p1');
  });
  tearDown(() => files.deleteSync(recursive: true));

  bool apply({bool fullSpeed = false}) => PhoneBudgetFiles.apply(
    filesDir: files,
    profileId: 'p1',
    fullSpeed: fullSpeed,
  );

  Map<String, dynamic> settings() =>
      jsonDecode(File('${claude.path}/settings.json').readAsStringSync())
          as Map<String, dynamic>;

  test('writes the phone note and the thread caps', () {
    expect(apply(), isTrue);
    final note = File('${claude.path}/CLAUDE.md').readAsStringSync();
    expect(note, contains('This is a phone'));
    expect(note, contains('flutter test --concurrency=1'));
    expect(
      (settings()['env'] as Map)['MAKEFLAGS'],
      '-j2',
      reason: 'tools start two threads, not one per core',
    );
  });

  test('keeps the person\'s own text and settings, and is repeatable', () {
    claude.createSync();
    File('${claude.path}/CLAUDE.md').writeAsStringSync('# Mine\nUse tabs.\n');
    File('${claude.path}/settings.json').writeAsStringSync(
      jsonEncode({
        'permissions': {
          'allow': ['Bash(ls)'],
        },
        'env': {'MAKEFLAGS': '-j8', 'KEEP': '1'},
      }),
    );
    apply();
    apply();
    final note = File('${claude.path}/CLAUDE.md').readAsStringSync();
    expect(note, startsWith('# Mine\nUse tabs.\n'));
    expect(
      PhoneBudgetFiles.begin.allMatches(note).length,
      1,
      reason: 'a second start does not stack a second block',
    );
    final json = settings();
    expect(json['permissions'], isNotNull);
    final env = json['env'] as Map;
    expect(env['MAKEFLAGS'], '-j8', reason: 'the person\'s value wins');
    expect(env['KEEP'], '1');
    expect(env['GOMAXPROCS'], '2');
  });

  test('Always full speed takes back only what the app added', () {
    claude.createSync();
    File('${claude.path}/CLAUDE.md').writeAsStringSync('# Mine\n');
    File('${claude.path}/settings.json').writeAsStringSync(
      jsonEncode({
        'env': {'MAKEFLAGS': '-j8'},
      }),
    );
    apply();
    apply(fullSpeed: true);
    expect(File('${claude.path}/CLAUDE.md').readAsStringSync(), '# Mine\n');
    final env = settings()['env'] as Map;
    expect(env, {'MAKEFLAGS': '-j8'});
  });

  test('full speed on a fresh profile leaves no files', () {
    apply();
    apply(fullSpeed: true);
    expect(File('${claude.path}/CLAUDE.md').existsSync(), isFalse);
    expect(File('${claude.path}/settings.json').existsSync(), isFalse);
  });

  test('does nothing for a profile without a home, or with a link', () {
    expect(
      PhoneBudgetFiles.apply(
        filesDir: files,
        profileId: 'other',
        fullSpeed: false,
      ),
      isFalse,
    );
    claude.createSync();
    final secret = File('${files.path}/secret')..writeAsStringSync('keep');
    Link('${claude.path}/CLAUDE.md').createSync(secret.path);
    apply();
    expect(
      secret.readAsStringSync(),
      'keep',
      reason: 'a link is never followed',
    );
  });

  test('settings it cannot read are left alone', () {
    claude.createSync();
    final file = File('${claude.path}/settings.json')
      ..writeAsStringSync('{ not json');
    expect(apply(), isFalse);
    expect(file.readAsStringSync(), '{ not json');
  });
}

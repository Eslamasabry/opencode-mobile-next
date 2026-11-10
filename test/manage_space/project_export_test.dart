// The exporter's rules (slice clear-storage-guard): what "Clear storage"
// would delete is measured from the files, and a plain export never carries
// a credential; a private export adds them and OpenCode's own data.
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/project_export.dart';

void main() {
  late Directory files;

  void write(String relative, String text) {
    final file = File('${files.path}/$relative');
    file.parent.createSync(recursive: true);
    file.writeAsStringSync(text);
  }

  setUp(() {
    files = Directory.systemTemp.createTempSync('oc-export-');
    write('projects/app/lib/main.dart', 'void main() {}\n'); // 15
    write('projects/app/.env', 'TOKEN=secret\n'); // 13
    write('projects/app/.env.example', 'TOKEN=\n'); // 7
    write(
      'projects/app/.git/config',
      '[remote]\nurl = https://me:tok@x.dev/r\n',
    );
    write('projects/app/deploy/server.pem', 'key');
    write('projects/web/index.html', '<p>hi</p>'); // 9
    write('projects/web/.git/config', '[remote]\nurl = https://x.dev/r\n');
    write('linux/ubuntu.ready', '');
    write('linux/ubuntu/usr/bin/tool', '0123456789'); // 10
    write('linux/ubuntu/root/.local/share/opencode/auth.json', '{"k":"v"}');
    write('linux/ubuntu/root/.local/share/opencode/storage/s1.json', '{}');
    write('linux/ubuntu/root/.local/share/opencode/log/today.log', 'log');
    write('linux/ubuntu/root/.config/opencode/opencode.json', '{}');
    // A link out of the project must never be followed or copied.
    Link('${files.path}/projects/web/outside').createSync('/etc');
    Link('${files.path}/projects/linked').createSync(files.path);
  });

  tearDown(() => files.deleteSync(recursive: true));

  test('a plain export leaves every credential out', () {
    final plan = ProjectStorageScanner.plan(files.path);
    final names = plan.map((e) => e.name).toSet();
    expect(names, contains('projects/app/lib/main.dart'));
    expect(names, contains('projects/app/.env.example'));
    expect(names, contains('projects/web/index.html'));
    // A git remote without a password is an ordinary file.
    expect(names, contains('projects/web/.git/config'));
    expect(names, isNot(contains('projects/app/.env')));
    expect(names, isNot(contains('projects/app/.git/config')));
    expect(names, isNot(contains('projects/app/deploy/server.pem')));
    expect(names.any((n) => n.contains('auth.json')), isFalse);
    expect(names.any((n) => n.startsWith('opencode/')), isFalse);
    expect(plan.any((e) => e.private), isFalse);
    // Links are neither followed nor listed.
    expect(names.any((n) => n.contains('outside')), isFalse);
    expect(names.any((n) => n.contains('linked')), isFalse);
  });

  test('a private export adds credentials and OpenCode data, not logs', () {
    final plan = ProjectStorageScanner.plan(files.path, includePrivate: true);
    final names = plan.map((e) => e.name).toSet();
    expect(names, contains('projects/app/.env'));
    expect(names, contains('projects/app/.git/config'));
    expect(names, contains('opencode/.local/share/opencode/auth.json'));
    expect(names, contains('opencode/.local/share/opencode/storage/s1.json'));
    expect(names, contains('opencode/.config/opencode/opencode.json'));
    expect(names.any((n) => n.contains('/log/')), isFalse);
    expect(
      plan.where((e) => e.private).map((e) => e.name),
      containsAll([
        'projects/app/.env',
        'opencode/.local/share/opencode/auth.json',
      ]),
    );
  });

  test('measures each project, the server and saved private counts', () {
    final facts = ProjectStorageScanner.scan(files.path);
    expect(facts.projects.map((p) => p.name), ['app', 'web']);
    final app = facts.projects.first;
    final gitConfig = File('${files.path}/projects/app/.git/config');
    expect(app.bytes, 15 + 13 + 7 + gitConfig.lengthSync() + 3);
    expect(app.files, 5);
    expect(app.privateFiles, 3); // .env, .git/config with a token, .pem
    expect(facts.projects.last.privateFiles, 0);
    expect(facts.serverInstalled, isTrue);
    expect(facts.serverBytes, greaterThanOrEqualTo(10));
    expect(facts.privateDataBytes, 9 + 2 + 2);
    expect(facts.privateFiles, 3);
  });

  test('nothing installed measures as nothing, never an error', () {
    final empty = Directory.systemTemp.createTempSync('oc-export-empty-');
    addTearDown(() => empty.deleteSync(recursive: true));
    final facts = ProjectStorageScanner.scan(empty.path);
    expect(facts.projects, isEmpty);
    expect(facts.serverInstalled, isFalse);
    expect(facts.serverBytes, 0);
    expect(ProjectStorageScanner.plan(empty.path), isEmpty);
  });

  test('projects not yet moved out of Ubuntu are exported too', () {
    write('linux/ubuntu/root/projects/old/readme.md', 'old');
    final names = ProjectStorageScanner.plan(files.path).map((e) => e.name);
    expect(names, contains('projects/old/readme.md'));
    final facts = ProjectStorageScanner.scan(files.path);
    expect(facts.projects.map((p) => p.name), ['app', 'old', 'web']);
  });

  // Ubuntu's proot (--link2symlink) stores every Git object as a link to a
  // `.l2s.` file; links holding the absolute path they were made at.
  void linkedObject(String objects, String name, String bytes, {String? at}) {
    final folder = '${files.path}/$objects';
    final made = at == null ? folder : '${files.path}/$at';
    Directory(folder).createSync(recursive: true);
    File('$folder/.l2s.tmp_obj_$name.0001.0001').writeAsStringSync(bytes);
    Link(
      '$folder/.l2s.tmp_obj_$name.0001',
    ).createSync('$made/.l2s.tmp_obj_$name.0001.0001');
    Link('$folder/$name').createSync('$made/.l2s.tmp_obj_$name.0001');
  }

  test('a repository made in Ubuntu exports with its Git objects', () {
    linkedObject('projects/app/.git/objects/ab', 'cdef', 'object one');
    // Made before the folder was renamed: the links name the old folder.
    linkedObject(
      'projects/web/.git/objects/12',
      '3456',
      'object two',
      at: 'projects/web-old/.git/objects/12',
    );
    // A link2symlink-looking link out of the app's files stays out.
    Link(
      '${files.path}/projects/app/.git/objects/ab/out',
    ).createSync('/etc/.l2s.passwd');

    final plan = ProjectStorageScanner.plan(files.path);
    final byName = {for (final e in plan) e.name: e};

    final one = byName['projects/app/.git/objects/ab/cdef'];
    final two = byName['projects/web/.git/objects/12/3456'];
    expect(one, isNotNull);
    expect(File(one!.source).readAsStringSync(), 'object one');
    expect(one.bytes, 'object one'.length);
    expect(two, isNotNull);
    expect(File(two!.source).readAsStringSync(), 'object two');
    // proot's own files are not project files.
    expect(byName.keys.where((n) => n.contains('.l2s.')), isEmpty);
    expect(byName.keys.where((n) => n.endsWith('/out')), isEmpty);
    final app = ProjectStorageScanner.scan(
      files.path,
    ).projects.firstWhere((p) => p.name == 'app');
    final gitConfig = File('${files.path}/projects/app/.git/config');
    expect(
      app.bytes,
      15 + 13 + 7 + gitConfig.lengthSync() + 3 + 'object one'.length,
    );
  });

  test(
    'the plan file frames one entry per line and skips unframeable',
    () async {
      final plan = await writeExportPlan(files, const [
        ProjectExportEntry(name: 'projects/a/x', source: '/f/a/x', bytes: 1),
        ProjectExportEntry(name: 'projects/a/b\tc', source: '/f/b', bytes: 1),
      ]);
      expect(plan.readAsLinesSync(), ['projects/a/x\t/f/a/x']);
    },
  );

  test('the suggested file name says when it is private', () {
    final day = DateTime(2026, 9, 8);
    expect(
      suggestedExportName(day, private: false),
      'opencode-projects-2026-09-08.zip',
    );
    expect(
      suggestedExportName(day, private: true),
      'opencode-projects-private-2026-09-08.zip',
    );
  });

  test('the policy knows credential names wherever they are', () {
    for (final path in [
      'auth.json',
      'a/.git-credentials',
      '.ssh/config',
      'keys/id_ed25519',
      'x/.env.local',
      'store.jks',
    ]) {
      expect(ProjectExportPolicy.isPrivate(path), isTrue, reason: path);
    }
    for (final path in [
      'README.md',
      '.env.example',
      'src/env.dart',
      'key.dart',
    ]) {
      expect(ProjectExportPolicy.isPrivate(path), isFalse, reason: path);
    }
  });
}

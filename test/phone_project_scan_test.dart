// The "Find projects" scanner over a temp tree standing in for the phone's
// storage (lib/platform/phone_project_scan.dart).
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/platform/phone_project_scan.dart';

void main() {
  late Directory root;

  void file(String path) {
    File('${root.path}/$path')
      ..createSync(recursive: true)
      ..writeAsStringSync('x');
  }

  void dir(String path) =>
      Directory('${root.path}/$path').createSync(recursive: true);

  Future<(List<PhoneProject>, PhoneScanEnd)> scan({
    PhoneProjectScanner? scanner,
    Duration limit = const Duration(seconds: 10),
  }) async {
    final run = (scanner ?? PhoneProjectScanner(root: root.path)).start(
      timeLimit: limit,
    );
    final found = await run.projects.toList();
    return (found, await run.end);
  }

  setUp(() => root = Directory.systemTemp.createTempSync('phone_scan_'));
  tearDown(() => root.deleteSync(recursive: true));

  test('finds a folder by its marker and says what kind it is', () async {
    file('a/pubspec.yaml');
    file('b/package.json');
    file('c/Cargo.toml');
    file('d/go.mod');
    file('e/pyproject.toml');
    file('f/App.sln');
    dir('g/.git');
    file('h/notes.txt');
    final (found, end) = await scan();
    expect(end, PhoneScanEnd.completed);
    final kinds = {for (final p in found) p.name: p.kind};
    expect(kinds, {
      'a': PhoneProjectKind.dart,
      'b': PhoneProjectKind.node,
      'c': PhoneProjectKind.rust,
      'd': PhoneProjectKind.go,
      'e': PhoneProjectKind.python,
      'f': PhoneProjectKind.dotnet,
      'g': PhoneProjectKind.git,
    });
    expect(found.firstWhere((p) => p.name == 'g').hasGit, isTrue);
    expect(found.firstWhere((p) => p.name == 'a').hasGit, isFalse);
  });

  test('every listed marker makes a project', () async {
    const names = [
      '.git',
      'package.json',
      'pubspec.yaml',
      'pyproject.toml',
      'requirements.txt',
      'Cargo.toml',
      'go.mod',
      'pom.xml',
      'build.gradle',
      'build.gradle.kts',
      'Gemfile',
      'composer.json',
      'CMakeLists.txt',
      'Makefile',
      'x.sln',
      'x.csproj',
    ];
    for (final (i, name) in names.indexed) {
      name == '.git' ? dir('p$i/.git') : file('p$i/$name');
    }
    final (found, _) = await scan();
    expect(found.length, names.length);
  });

  test('a repository with a code marker keeps its kind and has git', () async {
    dir('app/.git');
    file('app/package.json');
    final (found, _) = await scan();
    expect(found.single.kind, PhoneProjectKind.node);
    expect(found.single.hasGit, isTrue);
  });

  test('never goes into a project', () async {
    file('app/package.json');
    file('app/inner/pubspec.yaml');
    final (found, _) = await scan();
    expect(found.map((p) => p.name), ['app']);
  });

  test('skips Android, node_modules, hidden and build folders', () async {
    for (final skipped in [
      'Android',
      'node_modules',
      '.hidden',
      'build',
      '.dart_tool',
      'venv',
      '.venv',
      '__pycache__',
      'target',
      'dist',
    ]) {
      file('$skipped/p/package.json');
    }
    file('work/p/package.json');
    final (found, _) = await scan();
    expect(found.map((p) => p.path), ['${root.path}/work/p']);
  });

  test('does not follow links', () async {
    file('real/p/package.json');
    dir('links');
    Link('${root.path}/links/again').createSync('${root.path}/real');
    final (found, _) = await scan();
    expect(found.map((p) => p.path), ['${root.path}/real/p']);
  });

  test('looks five levels down and no further', () async {
    file('1/2/3/4/five/package.json');
    file('1/2/3/4/5/six/package.json');
    final (found, end) = await scan();
    expect(found.map((p) => p.name), ['five']);
    expect(end, PhoneScanEnd.completed);
  });

  test('the root itself is not a project', () async {
    file('package.json');
    final (found, _) = await scan();
    expect(found, isEmpty);
  });

  test('the time cap stops it and says so', () async {
    file('a/package.json');
    final (found, end) = await scan(limit: Duration.zero);
    expect(end, PhoneScanEnd.timedOut);
    expect(found, isEmpty);
  });

  test('the visit cap stops it as limited', () async {
    for (var i = 0; i < 10; i++) {
      dir('d$i/x');
    }
    final (_, end) = await scan(
      scanner: PhoneProjectScanner(root: root.path, maxVisited: 3),
    );
    expect(end, PhoneScanEnd.limited);
  });

  test('the result cap keeps the first results', () async {
    for (var i = 0; i < 6; i++) {
      file('p$i/package.json');
    }
    final (found, end) = await scan(
      scanner: PhoneProjectScanner(root: root.path, maxResults: 2),
    );
    expect(found.length, 2);
    expect(end, PhoneScanEnd.limited);
  });

  test('cancel stops it', () async {
    for (var i = 0; i < 30; i++) {
      dir('d$i/e/f');
    }
    final run = PhoneProjectScanner(root: root.path).start();
    run.cancel();
    await run.projects.toList();
    expect(await run.end, PhoneScanEnd.cancelled);
  });

  test('an unreadable root ends quietly', () async {
    final run = PhoneProjectScanner(root: '${root.path}/missing').start();
    expect(await run.projects.toList(), isEmpty);
    expect(await run.end, PhoneScanEnd.completed);
  });

  group('project space', () {
    test(
      'every folder straight in it is a project, with or without markers',
      () async {
        file('proj/a/package.json');
        dir('proj/b/.git');
        dir('proj/c/notes');
        final run = PhoneProjectScanner(
          root: '${root.path}/proj',
          reportAs: '/root/projects',
          childrenAreProjects: true,
        ).start();
        final found = await run.projects.toList();
        final byName = {for (final p in found) p.name: p};
        expect(byName.keys.toSet(), {'a', 'b', 'c'});
        // Paths read the way the browser shows them.
        expect(byName['a']!.path, '/root/projects/a');
        expect(byName['a']!.kind, PhoneProjectKind.node);
        expect(byName['b']!.hasGit, isTrue);
        expect(byName['c']!.hasGit, isFalse);
      },
    );

    test('markers still find projects deeper down', () async {
      file('proj/group/deep/pubspec.yaml');
      final run = PhoneProjectScanner(
        root: '${root.path}/proj',
        reportAs: '/root/projects',
        childrenAreProjects: true,
      ).start();
      final found = await run.projects.toList();
      expect(found.map((p) => p.path), ['/root/projects/group']);
    });

    test('in sequence: the first scan\'s results come first', () async {
      file('proj/a/package.json');
      file('phone/b/package.json');
      final scan = PhoneProjectScan.sequence([
        (limit) => PhoneProjectScanner(
          root: '${root.path}/proj',
          reportAs: '/root/projects',
          childrenAreProjects: true,
        ).start(timeLimit: limit),
        (limit) => PhoneProjectScanner(
          root: '${root.path}/phone',
        ).start(timeLimit: limit),
      ], const Duration(seconds: 10));
      final found = await scan.projects.toList();
      expect(found.map((p) => p.name), ['a', 'b']);
      expect(await scan.end, PhoneScanEnd.completed);
      expect(scan.visited, greaterThan(2));
    });

    test('a list becomes a scan', () async {
      final scan = PhoneProjectScan.fromFuture(
        Future.value([
          const PhoneProject(
            name: 'x',
            path: '/root/projects/x',
            kind: PhoneProjectKind.git,
            hasGit: true,
          ),
        ]),
      );
      expect((await scan.projects.toList()).single.name, 'x');
      expect(await scan.end, PhoneScanEnd.completed);
    });

    test('cancel stops the whole sequence', () async {
      for (var i = 0; i < 20; i++) {
        dir('phone/d$i/e/f');
      }
      final scan = PhoneProjectScan.sequence([
        (limit) => PhoneProjectScanner(root: root.path).start(timeLimit: limit),
      ], const Duration(seconds: 10));
      scan.cancel();
      await scan.projects.toList();
      expect(await scan.end, PhoneScanEnd.cancelled);
    });
  });
}

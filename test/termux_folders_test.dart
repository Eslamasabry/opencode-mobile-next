// Folders of the OpenCode server this app runs in Termux
// (lib/termux/termux_folders.dart). The generated scripts are run for real
// here with bash, as Termux runs them (`bash -s`), against a temporary
// folder tree; a stand-in `proot-distro` runs the command after its `--`,
// so the inner script sees the host's paths.
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/builtin/builtin_folders.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/termux/termux_folders.dart';

void main() {
  late Directory temp;
  late String bin;

  setUp(() {
    temp = Directory.systemTemp.createTempSync('termux-folders');
    bin = '${temp.path}/bin';
    Directory(bin).createSync();
    // proot-distro login <distro> -- <command...>
    File('$bin/proot-distro')
      ..writeAsStringSync('#!/bin/sh\nshift 3\nexec "\$@"\n')
      ..setLastModifiedSync(DateTime.now());
    Process.runSync('chmod', ['+x', '$bin/proot-distro']);
  });

  tearDown(() => temp.deleteSync(recursive: true));

  /// Runs [script] like Termux does, in [cwd], and returns its stdout.
  Future<TermuxCommandResult> bash(String script, {String? cwd}) async {
    final process = await Process.start(
      'bash',
      ['-s'],
      workingDirectory: cwd ?? temp.path,
      environment: {'PATH': '$bin:${Platform.environment['PATH']}'},
    );
    process.stdin.write(script);
    await process.stdin.close();
    final out = await process.stdout.transform(utf8.decoder).join();
    final err = await process.stderr.transform(utf8.decoder).join();
    final code = await process.exitCode;
    return TermuxCommandResult(
      stdout: out,
      stderr: err,
      exitCode: code,
      errorCode: -1,
      errorMessage: '',
    );
  }

  TermuxFolders folders({String? cwd}) =>
      TermuxFolders(run: (script, _) => bash(script, cwd: cwd));

  /// A folder tree whose own path and children carry every awkward name.
  Directory tree() {
    final root = Directory(
      '${temp.path}/it\'s "a" \$(touch PWNED) `touch PWNED` dir',
    )..createSync();
    for (final name in [
      'plain',
      'with space',
      'quote\'s "double"',
      r'$(touch PWNED)',
      '`touch PWNED`',
      'semi;colon & amp',
      '.hidden',
      'new\nline',
      'تطبيق',
    ]) {
      Directory('${root.path}/$name').createSync();
    }
    Directory('${root.path}/repo/.git').createSync(recursive: true);
    Directory('${root.path}/worktree').createSync();
    File('${root.path}/worktree/.git').writeAsStringSync('gitdir: x');
    File('${root.path}/a-file').writeAsStringSync('x');
    Link('${root.path}/link-out').createSync('/etc');
    return root;
  }

  test(
    'lists child folders with git marks; names are data, never code',
    () async {
      final root = tree();
      final entries = await folders(cwd: root.path).list(root.path);
      expect(
        [for (final e in entries) (e.name, e.isGit)],
        unorderedEquals([
          ('plain', false),
          ('with space', false),
          ('quote\'s "double"', false),
          (r'$(touch PWNED)', false),
          ('`touch PWNED`', false),
          ('semi;colon & amp', false),
          ('تطبيق', false),
          ('repo', true),
          ('worktree', true),
        ]),
        reason: 'hidden, the newline name, files and links are left out',
      );
      expect(
        entries.firstWhere((e) => e.name == 'with space').path,
        '${root.path}/with space',
      );
      // Nothing ran: neither in the working folder nor next to the tree.
      expect(File('${root.path}/PWNED').existsSync(), isFalse);
      expect(File('${temp.path}/PWNED').existsSync(), isFalse);
    },
  );

  test('listing a folder with an awkward name goes into it exactly', () async {
    final root = tree();
    final inner = '${root.path}/quote\'s "double"';
    Directory('$inner/child').createSync();
    final entries = await folders(cwd: root.path).list(inner);
    expect([for (final e in entries) e.path], ['$inner/child']);
    expect(File('${root.path}/PWNED').existsSync(), isFalse);
  });

  test('a path with a newline or .. is refused before anything runs', () async {
    var ran = 0;
    final termux = TermuxFolders(
      run: (script, _) async {
        ran++;
        return bash(script);
      },
    );
    for (final path in ['/root/new\nline', '/root/../etc', 'relative']) {
      await expectLater(
        termux.list(path),
        throwsA(
          isA<FolderListException>().having(
            (e) => e.problem,
            'problem',
            FolderListProblem.invalid,
          ),
        ),
        reason: path,
      );
    }
    expect(ran, 0);
  });

  test(
    'the script itself refuses a newline path, whatever it is sent',
    () async {
      // Even called directly with an encoded newline, it lists nothing.
      final encoded = base64.encode(utf8.encode('${temp.path}/x\n/etc'));
      final script =
          'set -u\nsh -c \'${TermuxFolders.listInnerScript}\' -- \'$encoded\'\n';
      final result = await bash(script);
      expect(result.stdout.trim(), 'oc-folders-invalid');
    },
  );

  test('a link, a missing folder and / answer as the sheet expects', () async {
    final root = tree();
    await expectLater(
      folders().list('${root.path}/link-out'),
      throwsA(
        isA<FolderListException>().having(
          (e) => e.problem,
          'problem',
          FolderListProblem.linked,
        ),
      ),
    );
    await expectLater(
      folders().list('${root.path}/gone'),
      throwsA(
        isA<FolderListException>().having(
          (e) => e.problem,
          'problem',
          FolderListProblem.missing,
        ),
      ),
    );
    final top = await folders().list('/');
    final names = [for (final e in top) e.name];
    expect(names, isNot(contains('proc')));
    expect(names, isNot(contains('sys')));
    expect(names, isNot(contains('dev')));
    expect(names, contains('tmp'));
  });

  test(
    'makes a folder with an awkward name, and finds it the second time',
    () async {
      final root = tree();
      final path = '${root.path}/new \$(touch PWNED) "project"';
      final termux = folders(cwd: root.path);
      expect(await termux.create(path), (path: path, created: true));
      expect(Directory(path).existsSync(), isTrue);
      expect(await termux.create(path), (path: path, created: false));
      expect(File('${root.path}/PWNED').existsSync(), isFalse);
    },
  );

  // Found with issue #112: AI Team refuses a folder that is not a git
  // repository, so a project made while turning it on failed at once.
  test('a new folder is a git project with one empty first commit', () async {
    final path = '${temp.path}/team-app';
    expect(await folders().create(path), (path: path, created: true));
    final log = Process.runSync('git', [
      '-C',
      path,
      'log',
      '--format=%s',
    ], workingDirectory: temp.path);
    expect(log.exitCode, 0, reason: '${log.stderr}');
    expect((log.stdout as String).trim(), 'First commit');
    // An existing folder is never made a repository.
    final old = Directory('${temp.path}/old')..createSync();
    expect(await folders().create(old.path), (path: old.path, created: false));
    expect(Directory('${old.path}/.git').existsSync(), isFalse);
  });

  test('parsing ignores noise and refuses unsafe names', () {
    const out =
        'proot warning: can\'t sanitize binding "/proc/self/fd/1"\n'
        'oc-dir\tg\tbefore-ok\n'
        'oc-folders-ok\n'
        'oc-dir\tg\tapp\n'
        'oc-dir\t-\tnotes\n'
        'oc-dir\tx\tbad-flag\n'
        'oc-dir\t-\t.hidden\n'
        'oc-dir\t-\t..\n'
        'oc-dir\t-\ta/b\n'
        'oc-dir\t-\tbell\x07\n'
        'oc-dir\t-\ttoo\tmany\n'
        'garbage line\n';
    final entries = TermuxFolders.parseListing('/root/projects', out);
    expect(
      [for (final e in entries) (e.path, e.isGit)],
      [('/root/projects/app', true), ('/root/projects/notes', false)],
    );
    expect(
      [
        for (final e in TermuxFolders.parseListing(
          '/',
          'oc-folders-ok\n'
              'oc-dir\t-\tproc\noc-dir\t-\topt\n',
        ))
          e.name,
      ],
      ['opt'],
    );
  });

  test(
    'no answer, a timeout inside Termux, and a missing projects folder',
    () async {
      expect(
        () => TermuxFolders.parseListing('/root/x', 'proot: something\n'),
        throwsA(
          isA<FolderListException>().having(
            (e) => e.problem,
            'problem',
            FolderListProblem.failed,
          ),
        ),
      );
      expect(
        () => TermuxFolders.parseListing('/root/x', 'oc-folders-timeout\n'),
        throwsA(
          isA<FolderListException>().having(
            (e) => e.problem,
            'problem',
            FolderListProblem.timedOut,
          ),
        ),
      );
      expect(
        TermuxFolders.parseListing('/root/projects', 'oc-folders-missing\n'),
        isEmpty,
      );
    },
  );

  test('the outer script embeds only base64, never the path', () {
    final script = TermuxFolders.listScript('/root/projects/a b\'c');
    expect(script, isNot(contains("a b'c")));
    expect(
      script,
      contains(base64.encode(utf8.encode('/root/projects/a b\'c'))),
    );
    expect(TermuxFolders.listInnerScript, isNot(contains("'")));
    expect(TermuxFolders.createInnerScript, isNot(contains("'")));
    expect(
      script,
      contains('timeout -k 2s 15s proot-distro login opencode-ubuntu'),
    );
  });
}

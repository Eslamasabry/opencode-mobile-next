// Add a remote, clone a repository and the remote address a remotes list
// shows: validation, the exact scripts (no token in them) and, where real
// git exists, the whole flow against a local bare repository.
@TestOn('linux || mac-os')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/git/git_parsers.dart';
import 'package:opencode_mobile/domain/git/git_problem.dart';
import 'package:opencode_mobile/domain/git/git_repository.dart';
import 'package:opencode_mobile/domain/git/git_shell.dart';

import 'git_test_support.dart';

final bool _hasGit = () {
  try {
    return Process.runSync('sh', [
          '-c',
          'command -v git && command -v base64',
        ]).exitCode ==
        0;
  } catch (_) {
    return false;
  }
}();

class _ShShell implements GitShell {
  _ShShell(this.env);
  final Map<String, String> env;
  @override
  Future<String> run(String script, {required Duration timeout}) async {
    final r = await Process.run('sh', [
      '-c',
      script,
    ], environment: env).timeout(timeout);
    return '${r.stdout}${r.stderr}';
  }
}

void main() {
  group('remote addresses', () {
    test('the list keeps each address without user info', () {
      final remotes = parseGitRemotes(
        'remote.origin.url https://github.com/o/r.git\n'
        'remote.fork.url git@github.com:me/r.git\n'
        'remote.tok.url https://user:ghp_SECRET@example.com/o/r.git\n',
      );
      expect(remotes[0].address, 'https://github.com/o/r.git');
      expect(remotes[1].address, 'git@github.com:me/r.git');
      expect(remotes[2].address, 'https://example.com/o/r.git');
      expect(remotes.map((r) => r.address).join(), isNot(contains('SECRET')));
    });
  });

  group('names and addresses are checked before any shell runs', () {
    test('remote names', () {
      expect(isValidGitRemoteName('origin'), isTrue);
      expect(isValidGitRemoteName('my-fork.2'), isTrue);
      expect(isValidGitRemoteName(''), isFalse);
      expect(isValidGitRemoteName('-x'), isFalse);
      expect(isValidGitRemoteName('a b'), isFalse);
      expect(isValidGitRemoteName('a/b'), isFalse);
    });

    test('addresses', () {
      GitProblem? p(String u) => gitRemoteAddressProblem(u);
      expect(p('https://github.com/o/r.git'), isNull);
      expect(p('http://example.com/r'), isNull);
      expect(p('ssh://git@host/o/r.git'), isNull);
      expect(p('git@github.com:o/r.git'), isNull);
      expect(p('/srv/git/r.git'), isNull);
      expect(p('file:///srv/git/r.git'), isNull);
      expect(p(''), GitProblem.invalidRemoteUrl);
      expect(p('-oProxyCommand=x'), GitProblem.invalidRemoteUrl);
      expect(p('ext::sh -c touch% /tmp/x'), GitProblem.invalidRemoteUrl);
      expect(
        p('https://github.com/o/r with space'),
        GitProblem.invalidRemoteUrl,
      );
      expect(p('ftp://example.com/r'), GitProblem.invalidRemoteUrl);
      expect(
        p('https://user:ghp_x@github.com/o/r.git'),
        GitProblem.remoteAddressHasPassword,
      );
    });

    test('clone folder names come from the address', () {
      expect(
        suggestGitCloneFolder('https://github.com/o/my-app.git'),
        'my-app',
      );
      expect(suggestGitCloneFolder('git@github.com:o/x.git'), 'x');
      expect(suggestGitCloneFolder('https://h/o/r/'), 'r');
      expect(suggestGitCloneFolder('/srv/git/proj.git'), 'proj');
      expect(isValidGitFolderName('my app'), isFalse);
      expect(isValidGitFolderName('..'), isFalse);
      expect(isValidGitFolderName('.hidden'), isFalse);
      expect(isValidGitFolderName('a/b'), isFalse);
      expect(isValidGitFolderName('ok_1.2-x'), isTrue);
    });

    test('a bad address never reaches the shell', () async {
      final shell = FakeGitShell([]);
      final repo = GitRepository(shell: shell, directory: '/w/p');
      expect(
        (await repo.addRemote(name: 'origin', url: 'ext::x')).problem,
        GitProblem.invalidRemoteUrl,
      );
      expect(
        (await repo.addRemote(name: 'a b', url: 'https://h/r')).problem,
        GitProblem.invalidRemoteName,
      );
      expect(
        (await repo.clone(url: 'https://u:p@h/r', folderName: 'r')).problem,
        GitProblem.remoteAddressHasPassword,
      );
      expect(
        (await repo.clone(url: 'https://h/r', folderName: '..')).problem,
        GitProblem.invalidFolderName,
      );
      expect(shell.scripts, isEmpty);
    });
  });

  group('classification', () {
    test('existing remote and existing folder', () {
      expect(
        classifyGitError('error: remote origin already exists.'),
        GitProblem.remoteExists,
      );
      expect(
        classifyGitError(
          "fatal: destination path 'r' already exists and is not an empty directory.",
        ),
        GitProblem.folderExists,
      );
    });
  });

  group('with real git', () {
    late Directory tmp;
    late Map<String, String> env;
    late String bare;
    late String repo;

    setUp(() async {
      if (!_hasGit) return;
      tmp = await Directory.systemTemp.createTemp('oc-git-clone-');
      final home = Directory('${tmp.path}/home')..createSync();
      env = {
        ...Platform.environment,
        'HOME': home.path,
        'GIT_CONFIG_NOSYSTEM': '1',
      };
      Future<void> git(String dir, List<String> a) async {
        final r = await Process.run(
          'git',
          a,
          workingDirectory: dir,
          environment: env,
        );
        if (r.exitCode != 0) throw StateError('${a.first}: ${r.stderr}');
      }

      bare = '${tmp.path}/up.git';
      repo = '${tmp.path}/work/proj';
      Directory(repo).createSync(recursive: true);
      await git(tmp.path, ['init', '-q', '--bare', '-b', 'main', bare]);
      await git(repo, ['init', '-q', '-b', 'main']);
      await git(repo, ['config', 'user.name', 'T']);
      await git(repo, ['config', 'user.email', 't@e.x']);
      File('$repo/a.txt').writeAsStringSync('a\n');
      await git(repo, ['add', '.']);
      await git(repo, ['commit', '-q', '-m', 'first']);
      await git(repo, ['remote', 'add', 'origin', bare]);
      await git(repo, ['push', '-q', '-u', 'origin', 'main']);
    });

    tearDown(() async {
      if (_hasGit) await tmp.delete(recursive: true);
    });

    GitRepository open() =>
        GitRepository(shell: _ShShell(env), directory: repo);

    test('add a remote, see it with its address, refuse a duplicate', () async {
      final added = await open().addRemote(name: 'backup', url: bare);
      expect(added.note, GitActionNote.remoteAdded, reason: '${added.failure}');
      final snap = ((await open().snapshot()) as GitSnapshotReady).snapshot;
      expect(
        snap.remotes.map((r) => r.name),
        containsAll(['origin', 'backup']),
      );
      expect(snap.remotes.firstWhere((r) => r.name == 'backup').address, bare);
      final again = await open().addRemote(name: 'backup', url: bare);
      expect(again.problem, GitProblem.remoteExists);
    }, skip: !_hasGit);

    test('clone lands beside the project; a taken folder is refused', () async {
      final done = await open().clone(url: bare, folderName: 'copy');
      expect(done.note, GitActionNote.cloned, reason: '${done.failure}');
      expect(done.clonedTo, '${tmp.path}/work/copy');
      expect(File('${tmp.path}/work/copy/a.txt').existsSync(), isTrue);
      final again = await open().clone(url: bare, folderName: 'copy');
      expect(again.problem, GitProblem.folderExists);
      final missing = await open().clone(
        url: '${tmp.path}/nowhere.git',
        folderName: 'x',
      );
      expect(missing.ok, isFalse);
      expect(Directory('${tmp.path}/work/x').existsSync(), isFalse);
    }, skip: !_hasGit);
  });
}

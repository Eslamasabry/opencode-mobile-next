// The Git page's scripts run by a real `sh` against real `git` in throwaway
// repositories (a local bare repository stands in for the remote). Proves
// the quoting, the envelope and the token file's lifecycle end to end on the
// machine running the tests; the phone's proot Ubuntu is not covered here.
@TestOn('linux || mac-os')
library;

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/git/git_credentials.dart';
import 'package:opencode_mobile/domain/git/git_models.dart';
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

late Directory _tmp;
late Map<String, String> _env;

/// `sh -c` with a clean HOME, so the tester's own Git config stays out.
class _ShShell implements GitShell {
  final scripts = <String>[];

  @override
  Future<String> run(String script, {required Duration timeout}) async {
    scripts.add(script);
    final result = await Process.run(
      'sh',
      ['-c', script],
      environment: _env,
      includeParentEnvironment: true,
    ).timeout(timeout);
    return '${result.stdout}${result.stderr}';
  }
}

/// A token file in the test's temp folder; the shell sees the same path.
class _TempDrop implements GitCredentialDrop {
  final placed = <String>[];
  var removedByApp = 0;

  @override
  Future<GitSecretFile> place(String secret) async {
    final file = File('${_tmp.path}/secret-${placed.length}');
    await file.writeAsString(secret);
    await Process.run('chmod', ['600', file.path]);
    placed.add(file.path);
    return GitSecretFile(shellPath: file.path, hostPath: file.path);
  }

  @override
  Future<void> remove(GitSecretFile file) async {
    removedByApp++;
    final f = File(file.hostPath);
    if (await f.exists()) await f.delete();
  }
}

Future<String> _git(String dir, List<String> args) async {
  final result = await Process.run(
    'git',
    args,
    workingDirectory: dir,
    environment: _env,
  );
  if (result.exitCode != 0) {
    throw StateError('git ${args.first} failed: ${result.stderr}');
  }
  return result.stdout as String;
}

void main() {
  late String remote;
  late String repo;

  setUp(() async {
    if (!_hasGit) return;
    _tmp = await Directory.systemTemp.createTemp('oc-git-page-');
    final home = Directory('${_tmp.path}/home')..createSync();
    _env = {
      'HOME': home.path,
      'GIT_CONFIG_NOSYSTEM': '1',
      'GIT_AUTHOR_DATE': '2026-10-19T00:00:00Z',
      'GIT_COMMITTER_DATE': '2026-10-19T00:00:00Z',
    };
    remote = '${_tmp.path}/remote.git';
    repo = "${_tmp.path}/my 'app' ✓";
    await _git(_tmp.path, ['init', '-q', '--bare', '-b', 'main', remote]);
    await _git(_tmp.path, ['init', '-q', '-b', 'main', repo]);
    for (final kv in [
      ['user.name', 'Test User'],
      ['user.email', 't@example.com'],
      ['commit.gpgsign', 'false'],
    ]) {
      await _git(repo, ['config', ...kv]);
    }
    File('$repo/old name.txt').writeAsStringSync('a\nb\n');
    File('$repo/keep.txt').writeAsStringSync('x\n');
    await _git(repo, ['add', '.']);
    await _git(repo, ['commit', '-q', '-m', 'first']);
    await _git(repo, ['remote', 'add', 'origin', remote]);
  });

  tearDown(() async {
    if (!_hasGit) return;
    await _tmp.delete(recursive: true);
  });

  GitRepository open({GitCredentialDrop? drop, String? dir}) => GitRepository(
    shell: _ShShell(),
    directory: dir ?? repo,
    credentials: drop,
  );

  test(
    'snapshot: rename to unicode with spaces, untracked, no upstream',
    () async {
      await _git(repo, ['mv', 'old name.txt', 'new näme ✓.txt']);
      File('$repo/untracked é.md').writeAsStringSync('hi\n');
      File('$repo/keep.txt').writeAsStringSync('x\ny\n');
      final result = await open().snapshot();
      final snapshot = (result as GitSnapshotReady).snapshot;
      final files = {for (final f in snapshot.status.files) f.path: f};
      expect(files['new näme ✓.txt']!.staged, GitChangeKind.renamed);
      expect(files['new näme ✓.txt']!.originalPath, 'old name.txt');
      expect(files['untracked é.md']!.isUntracked, isTrue);
      expect(files['keep.txt']!.addedLines, 1);
      expect(snapshot.status.branch.head, 'main');
      expect(snapshot.status.branch.hasUpstream, isFalse);
      expect(snapshot.commits.single.subject, 'first');
      expect(snapshot.branches.current!.name, 'main');
      expect(snapshot.remotes.single.name, 'origin');
    },
    skip: !_hasGit,
  );

  test('not a repository is a state', () async {
    final plain = Directory('${_tmp.path}/plain')..createSync();
    expect(
      await open(dir: plain.path).snapshot(),
      isA<GitSnapshotNotRepository>(),
    );
    expect(
      ((await open(dir: '${_tmp.path}/missing').snapshot())
              as GitSnapshotFailed)
          .failure
          .problem,
      GitProblem.folderMissing,
    );
  }, skip: !_hasGit);

  test('commit only the chosen paths with a hostile message', () async {
    File('$repo/keep.txt').writeAsStringSync('x\nchanged\n');
    File("$repo/new 'file' \$(x).txt").writeAsStringSync('n\n');
    File('$repo/other.txt').writeAsStringSync('o\n');
    await _git(repo, ['add', 'other.txt']);
    const message = 'it\'s "fine" \$(touch pwned) `touch pwned2`\n\nbody';
    final result = await open().commit(
      paths: ['keep.txt', "new 'file' \$(x).txt"],
      message: message,
    );
    expect(result.ok, isTrue, reason: result.failure?.details);
    expect(result.commit, isNotEmpty);
    expect(
      (await _git(repo, ['log', '-1', '--format=%B'])).trim(),
      message.trim(),
    );
    final committed = await _git(repo, ['show', '--name-only', '--format=']);
    expect(committed, contains('keep.txt'));
    expect(committed, contains("new 'file' \$(x).txt"));
    expect(committed, isNot(contains('other.txt')));
    // other.txt stays staged; nothing ran from the message.
    expect(
      await _git(repo, ['diff', '--cached', '--name-only']),
      'other.txt\n',
    );
    expect(File('$repo/pwned').existsSync(), isFalse);
    expect(File('$repo/pwned2').existsSync(), isFalse);
  }, skip: !_hasGit);

  test('push, then pull up to date, branches and discard', () async {
    final repoApi = open();
    final first = await repoApi.push();
    expect(
      first.note,
      GitActionNote.pushedAndTracking,
      reason: first.failure?.details,
    );
    expect((await repoApi.push()).note, GitActionNote.upToDate);
    expect((await repoApi.pull()).note, GitActionNote.upToDate);

    expect((await repoApi.createBranch('feature/näme')).ok, isTrue);
    expect(
      (await _git(repo, ['branch', '--show-current'])).trim(),
      'feature/näme',
    );
    expect((await repoApi.switchBranch('main')).ok, isTrue);
    expect(
      (await repoApi.createBranch('main')).problem,
      GitProblem.branchExists,
    );

    File('$repo/keep.txt').writeAsStringSync('ruined\n');
    final discarded = await repoApi.discard(
      const GitFileChange(path: 'keep.txt', unstaged: GitChangeKind.modified),
    );
    expect(discarded.ok, isTrue, reason: discarded.failure?.details);
    expect(File('$repo/keep.txt').readAsStringSync(), 'x\n');

    File('$repo/junk é.txt').writeAsStringSync('j');
    await repoApi.discard(
      const GitFileChange(
        path: 'junk é.txt',
        unstaged: GitChangeKind.untracked,
      ),
    );
    expect(File('$repo/junk é.txt').existsSync(), isFalse);
  }, skip: !_hasGit);

  test('pull that cannot fast-forward, push that is rejected', () async {
    final repoApi = open();
    await repoApi.push();
    final other = '${_tmp.path}/other';
    await _git(_tmp.path, ['clone', '-q', remote, other]);
    await _git(other, ['config', 'user.name', 'O']);
    await _git(other, ['config', 'user.email', 'o@e.com']);
    File('$other/r.txt').writeAsStringSync('r\n');
    await _git(other, ['add', 'r.txt']);
    await _git(other, ['commit', '-q', '-m', 'remote']);
    await _git(other, ['push', '-q']);
    File('$repo/l.txt').writeAsStringSync('l\n');
    await repoApi.commit(paths: ['l.txt'], message: 'local');

    expect((await repoApi.push()).problem, GitProblem.pushRejected);
    expect((await repoApi.pull()).problem, GitProblem.cannotFastForward);
  }, skip: !_hasGit);

  test('token push: file removed by the script and the app', () async {
    await open().push();
    File('$repo/t.txt').writeAsStringSync('t\n');
    final drop = _TempDrop();
    final shell = _ShShell();
    final repoApi = GitRepository(
      shell: shell,
      directory: repo,
      credentials: drop,
    );
    await repoApi.commit(paths: ['t.txt'], message: 't');
    final result = await repoApi.push(gitHubToken: fakeGitHubToken);
    expect(result.note, GitActionNote.pushed, reason: result.failure?.details);
    expect(drop.placed, hasLength(1));
    expect(File(drop.placed.single).existsSync(), isFalse);
    expect(drop.removedByApp, 1);
    for (final script in shell.scripts) {
      expect(script, isNot(contains(fakeGitHubToken)));
    }
  }, skip: !_hasGit);

  test('the credential helper answers GitHub only, from the file', () async {
    final tokenFile = File('${_tmp.path}/tok')
      ..writeAsStringSync(fakeGitHubToken);
    Future<ProcessResult> fill(String host) => Process.run(
      'sh',
      [
        '-c',
        "printf 'protocol=https\\nhost=$host\\n\\n' | git "
            '${shCommand(gitHubCredentialOptions())} credential fill',
      ],
      // Not the checkout: a stray or broken .git there must not matter.
      workingDirectory: _tmp.path,
      environment: {
        ..._env,
        gitTokenFileVariable: tokenFile.path,
        'GIT_TERMINAL_PROMPT': '0',
      },
    );
    final github = await fill('github.com');
    expect(github.exitCode, 0, reason: '${github.stderr}');
    expect(github.stdout, contains('username=x-access-token'));
    expect(
      (github.stdout as String).contains('password=$fakeGitHubToken'),
      isTrue,
    );
    final other = await fill('example.com');
    expect((other.stdout as String).contains(fakeGitHubToken), isFalse);
    expect(other.exitCode, isNot(0));
  }, skip: !_hasGit);
}

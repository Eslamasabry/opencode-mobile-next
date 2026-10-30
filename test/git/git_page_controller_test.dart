import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:opencode_mobile/api2/client.dart';
import 'package:opencode_mobile/api2/gateway.dart';
import 'package:opencode_mobile/demo/demo_gateway.dart';
import 'package:opencode_mobile/domain/git/git_problem.dart';
import 'package:opencode_mobile/domain/git/git_repository.dart';
import 'package:opencode_mobile/domain/git/github_device_flow.dart';
import 'package:opencode_mobile/state/git_page_controller.dart';
import 'package:opencode_mobile/state/git_workspace.dart';
import 'package:opencode_mobile/state/github_account.dart';
import 'package:opencode_mobile/state/profiles.dart';

import 'git_fixtures.dart' as fx;
import 'git_test_support.dart';

String _snapshot({
  String status = fx.statusRename,
  String operation = '',
  String remotes = 'remote.origin.url https://github.com/o/r.git\n',
}) => envelope({
  'status': ok(status),
  'unstaged': ok(fx.numstatWork),
  'staged': ok(fx.numstatIndex),
  'log': ok(fx.log),
  'branches': ok(fx.branches),
  'operation': ok(operation),
  'remotes': ok(remotes),
});

String _pushPlan() => envelope({
  'head': ok('main\n'),
  'upstream': ok('origin/main\n'),
  'remotes': ok('origin\n'),
});

GitPageController _controller(
  FakeGitShell shell, {
  GitHost host = GitHost.phoneLinux,
  FakeCredentialDrop? drop,
  GitHubAccountController? account,
}) => GitPageController(
  resolution: GitWorkspaceResolution.available(
    GitWorkspace(
      host: host,
      directory: '/root/projects/app',
      shell: shell,
      credentials: drop,
    ),
  ),
  account: account,
);

/// A signed-in account whose token never touches storage.
class _Account extends GitHubAccountController {
  _Account()
    : super(
        profileId: 'builtin',
        store: GitHubTokenStore(secure: const FlutterSecureStorage()),
        flow: GitHubDeviceFlow(),
      );

  var rejected = 0;

  @override
  Future<String?> tokenForGit() async => fakeGitHubToken;

  @override
  Future<void> tokenRejected() async => rejected++;
}

void main() {
  test('first read: loading, then ready; busy is visible meanwhile', () async {
    final shell = FakeGitShell([_snapshot()]);
    final page = _controller(shell);
    expect(page.view, isA<GitPageLoading>());
    final busy = <GitAction?>[];
    page.addListener(() => busy.add(page.busy));
    await page.refresh();
    expect(busy, [GitAction.refresh, null]);
    final ready = page.view as GitPageReady;
    expect(ready.snapshot.status.files, hasLength(4));
    expect(page.abilities.canPull, isTrue);
    expect(page.abilities.canPush, isTrue);
    expect(page.abilities.canCommit, isTrue);
  });

  test('not a repository and failures are views, not exceptions', () async {
    final notRepo = _controller(
      FakeGitShell([
        envelope({'status': failed(fx.notRepositoryError, exit: 128)}),
      ]),
    );
    await notRepo.refresh();
    expect(notRepo.view, isA<GitPageNotRepository>());

    final broken = _controller(FakeGitShell(['@@OCG begin\n@@OCG nogit\n']));
    await broken.refresh();
    expect(
      (broken.view as GitPageFailed).failure.problem,
      GitProblem.gitMissing,
    );
  });

  test('a failed refresh keeps the last snapshot and reports it', () async {
    final page = _controller(FakeGitShell([_snapshot(), 'garbage']));
    await page.refresh();
    await page.refresh();
    expect(page.view, isA<GitPageReady>());
    expect(page.lastResult!.problem, GitProblem.outputTooLarge);
  });

  test('unavailable workspaces never run anything', () async {
    final page = GitPageController(
      resolution: const GitWorkspaceResolution.unavailable(
        GitUnavailableReason.serverCannotRunCommands,
      ),
    );
    expect(
      (page.view as GitPageUnavailable).reason,
      GitUnavailableReason.serverCannotRunCommands,
    );
    expect(await page.push(), isNull);
  });

  test('a write records its result, then reads again', () async {
    final shell = FakeGitShell([
      _snapshot(),
      envelope({'add': ok(), 'commit': ok(), 'id': ok('abc1234\n')}),
      _snapshot(status: fx.statusNoUpstream),
    ]);
    final page = _controller(shell);
    await page.refresh();
    final result = await page.commit(['keep.txt'], 'message');
    expect(result!.note, GitActionNote.committed);
    expect(page.lastResult, same(result));
    expect(page.busy, isNull);
    expect(shell.scripts, hasLength(3));
    expect(page.snapshot!.status.branch.head, 'lonely');
  });

  group('save and send in one tap', () {
    test('saves, then sends, then reads once; busy names each half', () async {
      final drop = FakeCredentialDrop();
      final shell = FakeGitShell([
        _snapshot(),
        envelope({'add': ok(), 'commit': ok(), 'id': ok('abc1234\n')}),
        _pushPlan(),
        envelope({'push': ok('To github.com:o/r.git\n')}),
        _snapshot(status: fx.statusNoUpstream),
      ]);
      final page = _controller(shell, drop: drop, account: _Account());
      await page.refresh();
      final busy = <GitAction?>[];
      page.addListener(() => busy.add(page.busy));
      final result = await page.commitAndPush(['keep.txt'], 'Update keep');
      expect(result!.action, GitAction.push);
      expect(result.ok, isTrue);
      expect(page.savedBeforeSend, isTrue);
      expect(busy, [GitAction.commit, GitAction.push, null]);
      expect(shell.scripts[1], contains("'commit' '-m' 'Update keep'"));
      expect(shell.scripts[3], contains("'push'"));
      expect(shell.scripts, hasLength(5));
      // The token went through the drop only, never into a script.
      expect(drop.secrets, [fakeGitHubToken]);
      expect(shell.scripts.join(), isNot(contains(fakeGitHubToken)));
      page.clearLastResult();
      expect(page.savedBeforeSend, isFalse);
    });

    test('a failed save stops there: nothing is sent', () async {
      final shell = FakeGitShell([
        _snapshot(),
        envelope({'add': ok(), 'commit': failed('pre-commit hook failed\n')}),
        _snapshot(),
      ]);
      final page = _controller(shell);
      await page.refresh();
      final result = await page.commitAndPush(['keep.txt'], 'm');
      expect(result!.ok, isFalse);
      expect(result.action, GitAction.commit);
      expect(page.savedBeforeSend, isFalse);
      expect(shell.scripts.any((s) => s.contains("'push'")), isFalse);
    });

    test('a failed send keeps the save and says so', () async {
      final shell = FakeGitShell([
        _snapshot(),
        envelope({'add': ok(), 'commit': ok(), 'id': ok('abc1234\n')}),
        _pushPlan(),
        envelope({
          'push': failed(
            "fatal: could not read Username for 'https://github.com'\n",
          ),
        }),
        _snapshot(),
      ]);
      final page = _controller(shell);
      await page.refresh();
      final result = await page.commitAndPush(['keep.txt'], 'm');
      expect(result!.ok, isFalse);
      expect(result.problem, GitProblem.authRequired);
      expect(page.savedBeforeSend, isTrue);
      expect(page.lastResult, same(result));
      expect(page.busy, isNull);
    });
  });

  test('one action at a time', () async {
    final shell = FakeGitShell([
      _snapshot(),
      envelope({'add': ok(), 'commit': ok(), 'id': ok('a\n')}),
      _snapshot(),
    ]);
    final page = _controller(shell);
    await page.refresh();
    final first = page.commit(['a'], 'm');
    expect(page.busy, GitAction.commit);
    expect(await page.push(), isNull);
    await first;
  });

  test('phone Linux: push hands the signed-in token to the drop', () async {
    final drop = FakeCredentialDrop();
    final account = _Account();
    final shell = FakeGitShell([
      _snapshot(),
      _pushPlan(),
      envelope({'push': ok()}),
      _snapshot(),
    ]);
    final page = _controller(shell, drop: drop, account: account);
    await page.refresh();
    expect(page.abilities.offersGitHubSignIn, isTrue);
    final result = await page.push();
    expect(result!.note, GitActionNote.pushed);
    expect(drop.secrets, [fakeGitHubToken]);
    expect(drop.allRemoved, isTrue);
    expect(shell.scripts.join(), isNot(contains(fakeGitHubToken)));
  });

  test('a rejected token signs the account out', () async {
    final account = _Account();
    final page = _controller(
      FakeGitShell([
        _snapshot(),
        _pushPlan(),
        envelope({'push': failed(fx.httpsAuthError, exit: 128)}),
        _snapshot(),
      ]),
      drop: FakeCredentialDrop(),
      account: account,
    );
    await page.refresh();
    final result = await page.push();
    expect(result!.problem, GitProblem.gitHubTokenRejected);
    expect(account.rejected, 1);
  });

  test(
    'Termux and servers: no sign-in offered, the token stays home',
    () async {
      final account = _Account();
      final shell = FakeGitShell([
        _snapshot(),
        _pushPlan(),
        envelope({'push': failed(fx.noPromptError, exit: 128)}),
        _snapshot(),
      ]);
      final page = _controller(shell, host: GitHost.termux, account: account);
      await page.refresh();
      expect(page.abilities.offersGitHubSignIn, isFalse);
      expect(page.abilities.usesOwnGitSignIn, isTrue);
      final result = await page.push();
      expect(result!.problem, GitProblem.authRequired);
      expect(account.rejected, 0);
      expect(shell.scripts.join(), isNot(contains('credential.helper')));
    },
  );

  test('abilities follow the snapshot', () async {
    final detached = _controller(
      FakeGitShell([_snapshot(status: fx.statusDetached)]),
    );
    await detached.refresh();
    expect(detached.abilities.canPull, isFalse);
    expect(detached.abilities.canPush, isFalse);

    final merging = _controller(
      FakeGitShell([
        _snapshot(status: fx.statusConflict, operation: 'MERGE_HEAD\n'),
      ]),
    );
    await merging.refresh();
    expect(merging.abilities.canCommit, isFalse);
    expect(merging.abilities.canPull, isFalse);
    expect(merging.abilities.canSwitchBranch, isFalse);

    final noRemote = _controller(
      FakeGitShell([_snapshot(status: fx.statusNoUpstream, remotes: '')]),
    );
    await noRemote.refresh();
    expect(noRemote.abilities.canPush, isFalse);
    expect(noRemote.abilities.canPull, isFalse);
  });

  group('workspace resolution', () {
    ServerProfile profile(String url) => ServerProfile(
      id: 'p',
      name: 'Server',
      baseUrl: url,
      username: 'opencode',
      password: 'fake-test-password',
    );

    test('a server with remoteShell gets a server shell, no token', () {
      final gateway = Api2Gateway(
        client: Api2Client.connect(
          baseUrl: 'https://example.test',
          password: 'fake-test-password',
          directory: '/srv/app',
        ),
      );
      final resolution = resolveGitWorkspace(
        profile: profile('https://example.test'),
        gateway: gateway,
        directory: '/srv/app',
        termuxSupported: false,
      );
      expect(resolution.workspace!.host, GitHost.server);
      expect(resolution.workspace!.shell, isA<ServerGitShell>());
      expect(resolution.workspace!.offersGitHubSignIn, isFalse);
    });

    test('a server without it is unavailable, whatever its flavor', () {
      final resolution = resolveGitWorkspace(
        profile: profile('https://example.test'),
        gateway: DemoGateway(),
        directory: '/srv/app',
        termuxSupported: false,
      );
      expect(resolution.workspace, isNull);
      expect(resolution.reason, GitUnavailableReason.serverCannotRunCommands);
    });

    test('no connection or no project', () {
      expect(
        resolveGitWorkspace(
          profile: null,
          gateway: null,
          directory: '/x',
        ).reason,
        GitUnavailableReason.notConnected,
      );
      expect(
        resolveGitWorkspace(
          profile: profile('https://example.test'),
          gateway: DemoGateway(),
          directory: ' ',
        ).reason,
        GitUnavailableReason.noProject,
      );
    });
  });
}

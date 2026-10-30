// The Git sheet behind a chat's branch chip: the actions as rows that name
// their target, Save changes with a list to tick, Switch branch, Clone,
// Remotes, GitHub sign-in, and the chip itself. Driven by the real
// controller over a fake shell, so what the sheet asks Git to do is read from
// the scripts it sent.
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/git/git_shell.dart';
import 'package:opencode_mobile/domain/git/github_device_flow.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/chat_git_holder.dart';
import 'package:opencode_mobile/state/git_page_controller.dart';
import 'package:opencode_mobile/state/git_workspace.dart';
import 'package:opencode_mobile/state/github_account.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/git/git_sheet.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';

import '../kit/kit_harness.dart';
import 'git_fixtures.dart' as fx;
import 'git_test_support.dart';

const _branchesTwo =
    'refs/heads/main\x1f97f3f68\x1forigin/main\x1f\x1f*\x1f1792357278\x1fsecond\n'
    'refs/heads/feature\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1ffirst\n'
    'refs/remotes/origin/main\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1ffirst\n'
    'refs/remotes/origin/release\x1fb196b9f\x1f\x1f\x1f \x1f1792357277\x1ffirst\n';

String _snapshot({
  String status = fx.statusRename,
  String branches = fx.branches,
  String remotes = 'remote.origin.url https://github.com/o/r.git\n',
}) => envelope({
  'status': ok(status),
  'unstaged': ok(fx.numstatWork),
  'staged': ok(fx.numstatIndex),
  'log': ok(fx.log),
  'branches': ok(branches),
  'operation': ok(''),
  'remotes': ok(remotes),
});

class _Account extends GitHubAccountController {
  _Account(this._state) : super(profileId: 'p');
  GitHubProfile? profile;
  @override
  Future<GitHubProfile?> commitProfile() async => profile;
  GitHubSignInState _state;
  var started = 0;
  var cancelled = 0;
  @override
  GitHubSignInState get state => _state;
  @override
  Future<String?> tokenForGit() async => null;
  @override
  Future<void> startSignIn() async => started++;
  @override
  void cancelSignIn() => cancelled++;
  void become(GitHubSignInState next) {
    _state = next;
    notifyListeners();
  }
}

GitPageController _page(
  GitShell shell, {
  GitHost host = GitHost.phoneLinux,
  GitHubAccountController? account,
}) => GitPageController(
  resolution: GitWorkspaceResolution.available(
    GitWorkspace(
      host: host,
      directory: '/root/projects/app',
      shell: shell,
      credentials: host == GitHost.phoneLinux ? FakeCredentialDrop() : null,
    ),
  ),
  account: account,
);

Future<GitPageController> _open(
  WidgetTester tester,
  FakeGitShell shell, {
  GitHost host = GitHost.phoneLinux,
  GitHubAccountController? account,
  ValueChanged<String>? onAskAgent,
  String? summary,
}) async {
  final context = await pumpKitHost(tester);
  // Opening the sheet reads once, then looks at the remote and reads again.
  if (shell.answers.length >= 2) {
    shell.answers.insertAll(2, [_fetch(), shell.answers[1]]);
  }
  final page = _page(shell, host: host, account: account);
  await page.refresh();
  showGitSheet(
    context,
    page: page,
    account: account,
    onAskAgent: onAskAgent,
    summary: summary,
  );
  await tester.pumpAndSettle();
  return page;
}

/// Nothing changed, [ahead] saves not sent yet.
String _clean({int ahead = 1}) => _snapshot(
  status:
      '# branch.oid b196b9f452aac7110d9a5277edd4d698e72d9872\x00'
      '# branch.head main\x00# branch.upstream origin/main\x00'
      '# branch.ab +$ahead -0\x00',
);

/// Opens the fold that holds Remotes, Clone, the account and the names.
Future<void> _openMore(WidgetTester tester) async {
  final fold = find.text('Remotes, clone and details');
  await tester.ensureVisible(fold);
  await tester.tap(fold);
  await tester.pumpAndSettle();
}

String _fetch() => envelope({'upstream': ok('origin/main\n'), 'fetch': ok()});

/// A line of text as the person reads it: the direction marks the kit puts
/// around names and paths are not part of the words.
Finder _text(String s) => find.byWidgetPredicate(
  (w) =>
      w is RichText &&
      w.text.toPlainText().replaceAll(RegExp('[\u2066-\u2069]'), '') == s,
  description: 'text "$s"',
);

void main() {
  testWidgets('changed files make one named main action: save and send', (
    tester,
  ) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    expect(_text('Git'), findsWidgets);
    expect(_text('Save 4 files and send to GitHub · main'), findsOneWidget);
    expect(_text('Get latest'), findsOneWidget);
    expect(_text('Up to date'), findsOneWidget);
    expect(_text('Switch branch'), findsOneWidget);
    // Nothing to send on its own, and the save is the main action: no rows
    // repeat them.
    expect(find.byKey(const Key('git-row-push')), findsNothing);
    expect(find.byKey(const Key('git-row-save')), findsNothing);
    // GitHub sign-in is offered where the app can hand it over.
    expect(find.byKey(const Key('git-row-github')), findsOneWidget);
    // Remotes and Clone are one tap down, not on the surface.
    expect(find.byKey(const Key('git-row-remotes')), findsNothing);
    await _openMore(tester);
    expect(find.byKey(const Key('git-row-remotes')), findsOneWidget);
    expect(_text('Clone a repository'), findsOneWidget);
  });

  testWidgets('with nothing to save the main action sends what is saved', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _clean(ahead: 2),
      _clean(ahead: 2),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      }),
      envelope({'push': ok('To origin\n')}),
      _clean(ahead: 0),
    ]);
    await _open(tester, shell);
    expect(_text('Send 2 saves to GitHub · main'), findsOneWidget);
    expect(find.byKey(const Key('git-row-save')), findsNothing);
    // Nothing is sent until the tap.
    expect(shell.scripts.any((s) => s.contains("'push'")), isFalse);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(shell.scripts.any((s) => s.contains("'push'")), isTrue);
    expect(_text('Sent.'), findsOneWidget);
    // Nothing left waiting: no main action.
    expect(find.byKey(const Key('git-primary')), findsNothing);
  });

  testWidgets('conflicts offer to ask the agent; one tap closes the sheet '
      'and sends the files to resolve', (tester) async {
    final shell = FakeGitShell([
      _snapshot(status: fx.statusConflict),
      _snapshot(status: fx.statusConflict),
    ]);
    final asked = <String>[];
    await _open(tester, shell, onAskAgent: asked.add);
    final row = find.byKey(const Key('git-row-ask-conflicts'));
    expect(row, findsOneWidget);
    expect(_text('Ask the agent to fix conflicts'), findsOneWidget);
    await tester.tap(row);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-sheet')), findsNothing);
    expect(asked, hasLength(1));
    expect(asked.single, startsWith('Please resolve the merge conflicts in '));
  });

  testWidgets('no agent to ask, or nothing in conflict: no such row', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(status: fx.statusConflict),
      _snapshot(status: fx.statusConflict),
    ]);
    await _open(tester, shell);
    expect(find.byKey(const Key('git-row-ask-conflicts')), findsNothing);
  });

  testWidgets('Write it for me fills the message with the agent\'s summary', (
    tester,
  ) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    await _open(
      tester,
      shell,
      summary: 'Added a combo counter and screen shake.',
      onAskAgent: (_) {},
    );
    expect(find.byKey(const Key('git-row-ask-conflicts')), findsNothing);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    final write = find.byKey(const Key('git-save-write-for-me'));
    await tester.ensureVisible(write);
    await tester.tap(write);
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TextFormField>(find.byKey(const Key('git-save-message')))
          .controller!
          .text,
      'Added a combo counter and screen shake.',
    );
  });

  testWidgets('with no summary there is no Write it for me', (tester) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    await _open(tester, shell);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-save-write-for-me')), findsNothing);
  });

  testWidgets('conflicts keep the save row, saying why it cannot run', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(status: fx.statusConflict),
      _snapshot(status: fx.statusConflict),
    ]);
    await _open(tester, shell);
    expect(find.byKey(const Key('git-row-save')), findsOneWidget);
    expect(find.textContaining('Save 2 files and send'), findsNothing);
  });

  testWidgets('Send names a non-GitHub remote by its host, not origin/main', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(remotes: 'remote.origin.url https://gitlab.com/o/r.git\n'),
      _snapshot(remotes: 'remote.origin.url https://gitlab.com/o/r.git\n'),
    ]);
    await _open(tester, shell);
    expect(_text('Save 4 files and send to gitlab.com · main'), findsOneWidget);
    expect(find.textContaining('origin/main'), findsNothing);
  });

  testWidgets('the exact branch and remote sit under Details, folded', (
    tester,
  ) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    // Nothing on the surface says origin/main.
    expect(find.textContaining('origin/main'), findsNothing);
    expect(find.byKey(const Key('git-details')), findsOneWidget);
    await _openMore(tester);
    expect(find.textContaining('origin/main'), findsWidgets);
    expect(find.textContaining('https://github.com/o/r.git'), findsWidgets);
  });

  testWidgets('where the app cannot hand a sign-in over it says so', (
    tester,
  ) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    await _open(tester, shell, host: GitHost.termux);
    expect(find.byKey(const Key('git-row-github')), findsNothing);
    await _openMore(tester);
    expect(
      find.textContaining('use the Git sign-in already set up'),
      findsOneWidget,
    );
  });

  testWidgets('a server that cannot run Git shows the reason, no actions', (
    tester,
  ) async {
    final context = await pumpKitHost(tester);
    final page = GitPageController(
      resolution: const GitWorkspaceResolution.unavailable(
        GitUnavailableReason.serverCannotRunCommands,
      ),
    );
    showGitSheet(context, page: page);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-unavailable')), findsOneWidget);
    expect(
      _text(
        'This server can’t run Git for the app. Use Git on that computer instead.',
      ),
      findsOneWidget,
    );
    expect(find.byKey(const Key('git-row-push')), findsNothing);
  });

  testWidgets('Get latest and Send run, and the result is said in words', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(),
      _snapshot(),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'pull': ok('Already up to date.\n'),
      }),
      _snapshot(),
    ]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    await tester.tap(find.byKey(const Key('git-row-pull')));
    await tester.pumpAndSettle();
    expect(shell.scripts.any((s) => s.contains("'pull' '--ff-only'")), isTrue);
    expect(
      find.descendant(
        of: find.byKey(const Key('git-result')),
        matching: _text('Already up to date.'),
      ),
      findsOneWidget,
    );
  });

  testWidgets('review: tick files, the message follows, save without sending', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(),
      _snapshot(),
      envelope({
        'add': ok(),
        'commit': ok(),
        'id': ok('abc1234\n'),
        'files': ok('keep.txt\nnew näme ✓.txt\nuntracked é.md\n'),
      }),
      _snapshot(),
    ]);
    await _open(tester, shell);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-save')), findsOneWidget);
    expect(_text('Save and send'), findsOneWidget);
    expect(_text('keep.txt'), findsOneWidget);
    expect(_text('img.bin'), findsOneWidget);
    String message() => tester
        .widget<TextFormField>(find.byKey(const Key('git-save-message')))
        .controller!
        .text;
    // An honest default from the ticked files, the person can change it.
    expect(message(), 'Update img.bin and 3 more files');
    expect(_text('Save 4 files and send to GitHub · main'), findsOneWidget);
    // Untick one file: the count and the default follow.
    await tester.tap(find.byKey(const Key('git-file-img.bin')));
    await tester.pumpAndSettle();
    expect(_text('Save 3 files and send to GitHub · main'), findsOneWidget);
    expect(message(), 'Update keep.txt and 2 more files');
    // An empty message: the main button is unavailable and says why.
    await tester.enterText(find.byKey(const Key('git-save-message')), '');
    await tester.pumpAndSettle();
    expect(_text('Describe what changed first.'), findsOneWidget);
    await tester.enterText(
      find.byKey(const Key('git-save-message')),
      'Rename and tweak',
    );
    await tester.pumpAndSettle();
    // A typed message stays when the ticks change.
    await tester.tap(find.byKey(const Key('git-file-img.bin')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('git-file-img.bin')));
    await tester.pumpAndSettle();
    expect(message(), 'Rename and tweak');
    await tester.ensureVisible(find.byKey(const Key('git-save-only')));
    await tester.tap(find.byKey(const Key('git-save-only')));
    await tester.pumpAndSettle();
    final commit = shell.scripts.firstWhere((s) => s.contains("'commit'"));
    expect(commit, contains("'keep.txt'"));
    expect(commit, contains("'old name.txt'")); // a rename saves both halves
    expect(commit, isNot(contains("'img.bin'")));
    expect(commit, contains("'Rename and tweak'"));
    // Saved only: nothing was sent.
    expect(shell.scripts.any((s) => s.contains("'push'")), isFalse);
    // Back on the first step, with the result.
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    expect(_text('Saved 3 files.'), findsOneWidget);
  });

  testWidgets('one tap saves the ticked files and sends them', (tester) async {
    final shell = FakeGitShell([
      _snapshot(),
      _snapshot(),
      envelope({'add': ok(), 'commit': ok(), 'id': ok('abc1234\n')}),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      }),
      envelope({'push': ok('To origin\n')}),
      _clean(ahead: 0),
    ]);
    await _open(tester, shell, account: _Account(const GitHubSignedIn('o')));
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    // Opening the review sends nothing.
    expect(shell.scripts.any((s) => s.contains("'commit'")), isFalse);
    await tester.tap(find.byKey(const Key('git-save-button')));
    await tester.pumpAndSettle();
    final commit = shell.scripts.indexWhere((s) => s.contains("'commit'"));
    final push = shell.scripts.indexWhere((s) => s.contains("'push'"));
    expect(commit, greaterThan(0));
    expect(push, greaterThan(commit));
    expect(
      shell.scripts[commit],
      contains("'Update img.bin and 3 more files'"),
    );
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    expect(_text('Saved and sent to GitHub · main.'), findsOneWidget);
  });

  testWidgets('a send that needs GitHub after the save keeps the save', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(),
      _snapshot(),
      envelope({'add': ok(), 'commit': ok(), 'id': ok('abc1234\n')}),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      }),
      envelope({
        'push': failed(
          "fatal: could not read Username for 'https://github.com': terminal prompts disabled\n",
        ),
      }),
      _clean(ahead: 1),
    ]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('git-save-button')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    expect(
      _text('Saved here, not sent yet. GitHub needs you to sign in first.'),
      findsOneWidget,
    );
    // Signing in is the one next step, offered once, as the main button.
    expect(_text('Sign in to GitHub'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-way-sign-in')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-signin')), findsOneWidget);
  });

  testWidgets('Switch branch lists local and remote-only branches', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(branches: _branchesTwo),
      _snapshot(branches: _branchesTwo),
      envelope({'run': ok()}),
      _snapshot(branches: _branchesTwo),
    ]);
    await _open(tester, shell);
    await tester.tap(find.byKey(const Key('git-row-switch')));
    await tester.pumpAndSettle();
    expect(_text('main'), findsWidgets);
    expect(_text('Current branch'), findsOneWidget);
    expect(_text('release'), findsOneWidget);
    expect(_text('On origin, not on this device yet'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-branch-feature')));
    await tester.pumpAndSettle();
    expect(shell.scripts.any((s) => s.contains("'switch' 'feature'")), isTrue);
    expect(_text('Switched branch.'), findsOneWidget);
  });

  testWidgets('Remotes shows each address; Add a remote refuses a password', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(
        remotes: 'remote.origin.url https://u:tok@github.com/o/r.git\n',
      ),
      _snapshot(
        remotes: 'remote.origin.url https://u:tok@github.com/o/r.git\n',
      ),
      envelope({'run': ok()}),
      _snapshot(
        remotes:
            'remote.origin.url https://github.com/o/r.git\n'
            'remote.backup.url git@example.com:me/r.git\n',
      ),
    ]);
    await _open(tester, shell);
    await _openMore(tester);
    await tester.tap(find.byKey(const Key('git-row-remotes')));
    await tester.pumpAndSettle();
    expect(_text('origin'), findsOneWidget);
    expect(_text('https://github.com/o/r.git'), findsOneWidget);
    await tester.enterText(find.byKey(const Key('git-remote-name')), 'backup');
    await tester.enterText(
      find.byKey(const Key('git-remote-url')),
      'https://me:secret@example.com/r.git',
    );
    await tester.pumpAndSettle();
    expect(
      find.textContaining('Take the password or token out of the address'),
      findsOneWidget,
    );
    await tester.enterText(
      find.byKey(const Key('git-remote-url')),
      'git@example.com:me/r.git',
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const Key('git-add-remote')));
    await tester.pumpAndSettle();
    expect(
      shell.scripts.any(
        (s) => s.contains("'remote' 'add' 'backup' 'git@example.com:me/r.git'"),
      ),
      isTrue,
    );
    expect(_text('backup'), findsOneWidget);
    expect(_text('git@example.com:me/r.git'), findsOneWidget);
  });

  testWidgets('Clone fills the folder from the address and clones beside', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _snapshot(),
      _snapshot(),
      envelope({'clone': ok()}),
      _snapshot(),
    ]);
    await _open(tester, shell);
    await _openMore(tester);
    await tester.tap(find.byKey(const Key('git-row-clone')));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('git-clone-url')),
      'https://github.com/o/my-app.git',
    );
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<TextFormField>(find.byKey(const Key('git-clone-folder')))
          .controller!
          .text,
      'my-app',
    );
    expect(_text('Clones into /root/projects/my-app'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-clone-button')));
    await tester.pumpAndSettle();
    final script = shell.scripts.firstWhere((s) => s.contains("'clone'"));
    expect(script, contains("cd -- '/root/projects'"));
    expect(
      script,
      contains("'clone' '--' 'https://github.com/o/my-app.git' 'my-app'"),
    );
    expect(
      _text('Cloned into /root/projects/my-app. Open it from your projects.'),
      findsOneWidget,
    );
  });

  testWidgets('GitHub sign-in shows the code to enter, no token to paste', (
    tester,
  ) async {
    final shell = FakeGitShell([_snapshot(), _snapshot()]);
    final account = _Account(const GitHubSignedOut());
    await _open(tester, shell, account: account);
    await tester.tap(find.byKey(const Key('git-row-github')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-signin-why')), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-signin-start')));
    await tester.pumpAndSettle();
    expect(account.started, 1);
    account.become(
      GitHubAwaitingUser(
        userCode: 'VPFI-KC2ZK',
        verificationUri: Uri.parse('https://github.com/login/device'),
        expiresAt: DateTime.utc(2030),
      ),
    );
    await tester.pumpAndSettle();
    expect(_text('VPFI-KC2ZK'), findsOneWidget);
    expect(find.byKey(const Key('git-signin-open')), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-signin-cancel')));
    expect(account.cancelled, 1);
    // Finishing returns to the actions.
    account.become(const GitHubSignedIn('octo'));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-home')), findsOneWidget);
    // Signed in, the account sits with Remotes and Clone.
    expect(find.byKey(const Key('git-row-github')), findsNothing);
    await _openMore(tester);
    expect(_text('Signed in to GitHub as octo'), findsOneWidget);
  });

  testWidgets('a push that needs GitHub offers the sign-in right there', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _clean(),
      _clean(),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      }),
      envelope({
        'push': failed(
          'fatal: could not read Username for \'https://github.com\': terminal prompts disabled\n',
        ),
      }),
      _clean(),
    ]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-result')), findsOneWidget);
    // Signing in becomes the main button, offered once.
    expect(_text('Sign in to GitHub'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-way-sign-in')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-signin')), findsOneWidget);
  });

  testWidgets(
    'Get latest looks at the remote quietly, then says what it found',
    (tester) async {
      final behind3 = _snapshot(
        status: fx.statusRename.replaceFirst('+0 -0', '+0 -3'),
      );
      final slow = _SlowFetchShell(first: _snapshot(), after: behind3);
      final context = await pumpKitHost(tester);
      final page = _page(slow);
      await page.refresh();
      showGitSheet(context, page: page);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      // While the fetch runs the row says it is looking, never "nothing new".
      expect(_text('Checking…'), findsOneWidget);
      expect(page.check, GitCheck.checking);
      slow.release();
      await tester.pumpAndSettle();
      expect(_text('3 new'), findsOneWidget);
      expect(page.check, GitCheck.done);
    },
  );

  testWidgets('a failed check is a quiet line, not an error', (tester) async {
    final shell = _SlowFetchShell(
      first: _snapshot(),
      after: _snapshot(),
      failFetch: true,
    )..release();
    final context = await pumpKitHost(tester);
    final page = _page(shell);
    await page.refresh();
    showGitSheet(context, page: page);
    await tester.pumpAndSettle();
    expect(page.check, GitCheck.failed);
    expect(_text('Couldn’t check, tap to try'), findsOneWidget);
    expect(find.byKey(const Key('git-result')), findsNothing);
    expect(page.lastResult, isNull);
  });

  testWidgets('results read in plain words; ids stay under Details', (
    tester,
  ) async {
    final shell = FakeGitShell([
      _clean(),
      _clean(),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'pull': ok('Updating a..b\n'),
        'count': ok('1\n'),
      }),
      _clean(),
      envelope({
        'head': ok('main\n'),
        'upstream': ok('origin/main\n'),
        'remotes': ok('origin\n'),
      }),
      envelope({'push': ok('To origin\n')}),
      _clean(),
    ]);
    await _open(tester, shell, account: _Account(const GitHubSignedOut()));
    await tester.tap(find.byKey(const Key('git-row-pull')));
    await tester.pumpAndSettle();
    expect(_text('Got 1 new change.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(_text('Sent.'), findsOneWidget);
  });

  testWidgets('the first save asks who you are when git has a placeholder', (
    tester,
  ) async {
    String placeholder() => envelope({
      'status': ok(fx.statusRename),
      'unstaged': ok(fx.numstatWork),
      'staged': ok(fx.numstatIndex),
      'log': ok(fx.log),
      'branches': ok(fx.branches),
      'operation': ok(''),
      'remotes': ok('remote.origin.url https://github.com/o/r.git\n'),
      'identity': ok('AI Team\naiteam@localhost\n'),
    });
    final shell = FakeGitShell([
      placeholder(),
      placeholder(),
      envelope({'name': ok(), 'email': ok()}),
      placeholder(),
    ]);
    final account = _Account(const GitHubSignedIn('octocat'))
      ..profile = const GitHubProfile(
        login: 'octocat',
        id: 583231,
        name: 'The Octocat',
      );
    await _open(tester, shell, account: account);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    // Not the save list yet: the one-time question, prefilled from GitHub.
    expect(find.byKey(const Key('git-identity')), findsOneWidget);
    expect(_text('Your name on saves'), findsOneWidget);
    String field(String key) =>
        tester.widget<TextFormField>(find.byKey(Key(key))).controller!.text;
    expect(field('git-identity-name'), 'The Octocat');
    expect(
      field('git-identity-email'),
      '583231+octocat@users.noreply.github.com',
    );
    await tester.tap(find.byKey(const Key('git-identity-save')));
    await tester.pumpAndSettle();
    final script = shell.scripts.firstWhere((s) => s.contains("'user.name'"));
    // For this repository only: never the device's global settings.
    expect(script, contains("'config' 'user.name' 'The Octocat'"));
    expect(
      script,
      contains("'user.email' '583231+octocat@users.noreply.github.com'"),
    );
    expect(script, isNot(contains('--global')));
    // Then the save list.
    expect(find.byKey(const Key('git-save')), findsOneWidget);
  });

  testWidgets('a real identity is not asked about again', (tester) async {
    String named() => envelope({
      'status': ok(fx.statusRename),
      'unstaged': ok(fx.numstatWork),
      'staged': ok(fx.numstatIndex),
      'log': ok(fx.log),
      'branches': ok(fx.branches),
      'operation': ok(''),
      'remotes': ok('remote.origin.url https://github.com/o/r.git\n'),
      'identity': ok('Ada\nada@example.com\n'),
    });
    final shell = FakeGitShell([named(), named()]);
    await _open(tester, shell);
    await tester.tap(find.byKey(const Key('git-primary')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-save')), findsOneWidget);
  });

  testWidgets('with no remote, Get latest and Send say so and open Remotes', (
    tester,
  ) async {
    String noRemote() => envelope({
      'status': ok(fx.statusNoUpstream),
      'unstaged': ok(''),
      'staged': ok(''),
      'log': ok(fx.log),
      'branches': ok(''),
      'operation': ok(''),
      'remotes': ok(''),
    });
    final shell = FakeGitShell([noRemote(), noRemote()]);
    await _open(tester, shell);
    expect(_text('Add a remote below first.'), findsOneWidget);
    expect(_text('No remote yet. Add one below.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('git-row-push')));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('git-remotes')), findsOneWidget);
    expect(find.byKey(const Key('git-remote-url')), findsOneWidget);
  });

  testWidgets('a branch not on the remote yet says to send it first', (
    tester,
  ) async {
    String local() => envelope({
      'status': ok(fx.statusNoUpstream),
      'unstaged': ok(''),
      'staged': ok(''),
      'log': ok(fx.log),
      'branches': ok(''),
      'operation': ok(''),
      'remotes': ok('remote.origin.url https://github.com/o/r.git\n'),
    });
    final shell = FakeGitShell([local(), local()]);
    await _open(tester, shell);
    expect(_text('Not on the remote yet. Send it first.'), findsOneWidget);
  });

  group('the header chip', () {
    testWidgets('it names the branch beside the project and opens on a tap', (
      tester,
    ) async {
      var taps = 0;
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.light(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: KitTopBar(
              title: 'Chat',
              exit: KitTopBarExit.none,
              scope: KitTopBarScope(
                label: 'app',
                semanticsLabel: 'Project app',
                items: [KitMenuItem(label: 'Files', onSelected: () {})],
                branch: KitTopBarBranch(
                  chipKey: const Key('chat-git-chip'),
                  label: 'feature/x',
                  semanticsLabel:
                      'Git, branch feature/x. Opens the Git actions.',
                  onTap: () => taps++,
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.text('app'), findsOneWidget);
      await tester.tap(find.byKey(const Key('chat-git-chip')));
      expect(taps, 1);
    });

    test(
      'the holder reads the branch once and rebinds on a new project',
      () async {
        var resolved = 0;
        final shells = <FakeGitShell>[];
        final holder = ChatGitHolder(
          resolve: (profile, gateway, directory) {
            resolved++;
            final shell = FakeGitShell([_snapshot()]);
            shells.add(shell);
            return GitWorkspaceResolution.available(
              GitWorkspace(
                host: GitHost.termux,
                directory: directory!,
                shell: shell,
              ),
            );
          },
        );
        addTearDown(holder.dispose);
        expect(holder.branchName, isNull);
        holder.bind(profile: null, gateway: null, directory: '/a');
        holder.bind(profile: null, gateway: null, directory: '/a');
        expect(resolved, 1);
        await Future<void>.delayed(const Duration(milliseconds: 50));
        expect(holder.branchName, 'main');
        holder.bind(profile: null, gateway: null, directory: '/b');
        expect(resolved, 2);
        expect(holder.branchName, isNull);
      },
    );

    // Jitter J8 (2026-10-28): every open of a chat said "Git" until its read
    // landed, then "master". A project read this run names its branch from
    // the chat's first frame.
    test('a chat opened again names the branch from its first frame', () async {
      ChatGitHolder make() => ChatGitHolder(
        resolve: (profile, gateway, directory) =>
            GitWorkspaceResolution.available(
              GitWorkspace(
                host: GitHost.termux,
                directory: directory!,
                shell: FakeGitShell([_snapshot()]),
              ),
            ),
      );
      final first = make();
      first.bind(profile: null, gateway: null, directory: '/j8');
      await Future<void>.delayed(const Duration(milliseconds: 50));
      expect(first.branchName, 'main');
      first.dispose();
      final again = make();
      addTearDown(again.dispose);
      again.bind(profile: null, gateway: null, directory: '/j8');
      expect(again.page?.snapshot, isNull);
      expect(again.branchName, 'main');
    });
  });
}

/// A shell whose fetch waits until [release], so the "Checking…" line can be
/// seen; every read answers [first] until the fetch is done, then [after].
class _SlowFetchShell implements GitShell {
  _SlowFetchShell({
    required this.first,
    required this.after,
    this.failFetch = false,
  });

  final bool failFetch;

  final String first;
  final String after;
  final _gate = Completer<void>();
  var _fetched = false;

  void release() => _gate.complete();

  @override
  Future<String> run(String script, {required Duration timeout}) async {
    if (script.contains("'fetch' '--quiet'")) {
      await _gate.future;
      _fetched = true;
      return envelope({
        'upstream': ok('origin/main\n'),
        'fetch': failFetch
            ? failed('fatal: unable to access: Could not resolve host\n')
            : ok(),
      });
    }
    return _fetched ? after : first;
  }
}

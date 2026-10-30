// The Git sheet a chat's branch chip opens: one named main action (save the
// changed files and send them in one tap, else Send, else Get latest), Get
// latest and Switch branch as rows, and Remotes, Clone and the exact names
// folded under one line, with the GitHub sign-in in the same sheet. It only arranges kit parts and reads two controllers
// (lib/state/git_page_controller.dart, lib/state/github_account.dart); every
// word comes from lib/ui/git_words.dart and the ARB files, and Git's own
// text goes under Details.
import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/git/git_models.dart';
import '../../domain/git/git_problem.dart';
import '../../domain/git/git_repository.dart';
import '../../domain/git/github_device_flow.dart';
import '../../l10n/app_localizations.dart';
import '../../state/git_page_controller.dart';
import '../../state/github_account.dart';
import '../app_iconography.dart';
import '../app_theme.dart' show AppStatusTone;
import '../git_words.dart';
import '../kit/kit.dart';
import '../widgets/external_link.dart';

part 'git_sheet_home.dart';
part 'git_sheet_steps.dart';
part 'git_sheet_signin.dart';

/// Opens the Git sheet over [context] for the project [page] is bound to.
/// [account] is the GitHub sign-in; null where this connection pushes with
/// its own sign-in.
Future<void> showGitSheet(
  BuildContext context, {
  required GitPageController page,
  GitHubAccountController? account,
  String? hostName,
  ValueChanged<String>? onAskAgent,
  String? summary,
}) {
  // A fresh read (the files may have changed since the chat opened), then a
  // quiet look at the remote so "new" and "up to date" are true.
  unawaited(page.refresh().then((_) => page.checkForUpdates()));
  return showKitFramedSheet<void>(
    context,
    sheetKey: const Key('git-sheet'),
    builder: (_) => GitSheet(
      page: page,
      account: account,
      hostName: hostName,
      onAskAgent: onAskAgent,
      summary: summary,
    ),
  );
}

enum _GitStep { home, save, branches, clone, remotes, signIn, identity }

class GitSheet extends StatefulWidget {
  const GitSheet({
    super.key,
    required this.page,
    this.account,
    this.hostName,
    this.onAskAgent,
    this.summary,
  });

  /// Sends [prompt] to the conversation's agent (the sheet closes first):
  /// "Ask the agent to fix conflicts". Null: not offered.
  final ValueChanged<String>? onAskAgent;

  /// The agent's own one-line summary of its last turn: "Write it for me"
  /// fills the commit message with it. Null or empty: not offered.
  final String? summary;

  /// Where pushes use their own sign-in: this phone's or the server's name.
  final String? hostName;

  final GitPageController page;
  final GitHubAccountController? account;

  @override
  State<GitSheet> createState() => _GitSheetState();
}

class _GitSheetState extends State<GitSheet> {
  _GitStep _step = _GitStep.home;

  // Save changes
  final _message = TextEditingController();
  Set<String>? _picked;

  /// The review sends after the save (the one-step "Save and send").
  bool _sendAfterSave = true;

  /// The person typed in the message: ticking files no longer rewrites it.
  bool _messageEdited = false;

  // Switch branch
  final _branchName = TextEditingController();

  // Clone
  final _cloneUrl = TextEditingController();
  final _cloneFolder = TextEditingController();
  bool _folderTouched = false;

  // Remotes
  final _remoteName = TextEditingController();
  final _remoteUrl = TextEditingController();

  // Identity
  final _identityName = TextEditingController();
  final _identityEmail = TextEditingController();

  GitHubSignInState? _lastSignIn;

  GitPageController get page => widget.page;
  GitHubAccountController? get account => widget.account;
  String? get hostName => widget.hostName;

  @override
  void initState() {
    super.initState();
    _lastSignIn = account?.state;
    account?.addListener(_accountChanged);
  }

  @override
  void dispose() {
    account?.removeListener(_accountChanged);
    for (final c in [
      _message,
      _branchName,
      _cloneUrl,
      _cloneFolder,
      _remoteName,
      _remoteUrl,
      _identityName,
      _identityEmail,
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  /// Finishing the sign-in returns to the actions, where it was needed.
  void _accountChanged() {
    final now = account?.state;
    final was = _lastSignIn;
    _lastSignIn = now;
    if (now is GitHubSignedIn &&
        was is! GitHubSignedIn &&
        was is! GitHubSignInLoading &&
        _step == _GitStep.signIn &&
        mounted) {
      setState(() => _step = _GitStep.home);
    }
  }

  void _go(_GitStep step) {
    page.clearLastResult();
    setState(() => _step = step);
  }

  void _close() => KitSheet.close<void>(context);

  /// For the step files, which are extensions and cannot call setState.
  void _update(VoidCallback change) => setState(change);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context);
    return ListenableBuilder(
      listenable: Listenable.merge([page, ?account]),
      builder: (context, _) => switch (_step) {
        _GitStep.home => _homeStep(l10n),
        _GitStep.save => _saveStep(l10n),
        _GitStep.branches => _branchesStep(l10n),
        _GitStep.clone => _cloneStep(l10n),
        _GitStep.remotes => _remotesStep(l10n),
        _GitStep.signIn => _signInStep(l10n),
        _GitStep.identity => _identityStep(l10n),
      },
    );
  }

  // ------------------------------------------------------------- shared

  bool get _busy => page.busy != null;

  /// The back arrow of every step but the first.
  KitAction _back(AppLocalizations l10n) => KitAction(
    key: const Key('git-back'),
    label: l10n.kitTopBarBack,
    onPressed: () => _go(_GitStep.home),
  );

  /// A running action's line and nothing else while it runs.
  Widget? _busyNotice(AppLocalizations l10n) {
    final busy = page.busy;
    if (busy == null || busy == GitAction.refresh) return null;
    return KitNotice(
      key: const Key('git-busy'),
      tone: AppStatusTone.progress,
      message: gitBusyText(busy, l10n),
    );
  }

  /// What the last action said: done, or what is wrong and the way forward.
  /// Git's own text stays under Details.
  ///
  /// On the first step a needed sign-in is the main button instead, so the
  /// notice does not offer it a second time ([signInIsMain]).
  Widget? _resultNotice(AppLocalizations l10n, {bool signInIsMain = false}) {
    final result = page.lastResult;
    if (result == null) return null;
    final offers = page.abilities.offersGitHubSignIn;
    var text = gitResultText(result, l10n, offersGitHubSignIn: offers);
    // The one-step save and send: say both halves, and that a save whose
    // send failed is kept.
    if (page.savedBeforeSend) {
      final target = _target(page.snapshot);
      text = result.ok
          ? target == null
                ? l10n.gitDonePushed
                : l10n.gitDoneSavedAndSent(target.place, target.branch)
          : (result.problem == GitProblem.authRequired ||
                    result.problem == GitProblem.gitHubTokenRejected) &&
                offers &&
                target != null
          ? l10n.gitSavedSignInFirst(target.place)
          : l10n.gitSavedNotSent(text);
    }
    if (result.ok) {
      // The commit's id is for people who want it: under Details only.
      final id = result.commit;
      return Column(
        key: const Key('git-result'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          KitNotice(
            tone: AppStatusTone.ok,
            message: text,
            onDismiss: page.clearLastResult,
          ),
          if (id != null && id.isNotEmpty) KitDetailsFold(text: id),
        ],
      );
    }
    final problem = result.problem!;
    final details = result.failure?.details ?? '';
    final way = signInIsMain
        ? null
        : _wayForward(result, problem, offers, l10n);
    // A sign-in the main button offers is fully explained: no Git text.
    final fold = details.trim().isEmpty || signInIsMain
        ? null
        : KitDetailsFold(text: details);
    if (way == null && !signInIsMain) {
      return Column(
        key: const Key('git-result'),
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [KitNotice.error(message: text, details: details)],
      );
    }
    return Column(
      key: const Key('git-result'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        KitNotice(tone: AppStatusTone.neutral, message: text, actions: [?way]),
        ?fold,
      ],
    );
  }

  /// The one step that fixes [problem], where the sheet can do it.
  KitAction? _wayForward(
    GitActionResult result,
    GitProblem problem,
    bool offersSignIn,
    AppLocalizations l10n,
  ) => switch (problem) {
    GitProblem.authRequired ||
    GitProblem.gitHubTokenRejected when offersSignIn => KitAction(
      key: const Key('git-way-sign-in'),
      label: l10n.gitHubSignIn,
      onPressed: () => _go(_GitStep.signIn),
    ),
    GitProblem.pushRejected => KitAction(
      key: const Key('git-way-pull'),
      label: l10n.gitGetLatest,
      onPressed: () => unawaited(page.pull()),
    ),
    GitProblem.identityMissing => KitAction(
      key: const Key('git-way-identity'),
      label: l10n.gitIdentityTitle,
      onPressed: () => _go(_GitStep.identity),
    ),
    _ => null,
  };

  /// The last send needs a GitHub sign-in this sheet can start.
  bool get _needsSignIn {
    final problem = page.lastResult?.problem;
    return page.abilities.offersGitHubSignIn &&
        account != null &&
        account!.state is! GitHubSignedIn &&
        (problem == GitProblem.authRequired ||
            problem == GitProblem.gitHubTokenRejected);
  }

  /// Where Send goes in plain words ("GitHub", "main"); null without a
  /// branch or a remote to send to.
  ({String place, String branch})? _target(GitSnapshot? snapshot) {
    final remote = snapshot?.pushRemote;
    final head = snapshot?.status.branch.head;
    if (snapshot == null || remote == null || head == null) return null;
    return (
      place: KitBidi.auto(gitPlaceName(remote, AppLocalizations.of(context))),
      branch: KitBidi.ltr(_middleName(head)),
    );
  }

  /// A very long branch name keeps its start and its end.
  static String _middleName(String name, {int max = 34}) => name.length <= max
      ? name
      : '${name.substring(0, (max - 1) ~/ 2)}\u2026'
            '${name.substring(name.length - (max - 1) ~/ 2)}';

  /// A short field error in the app's words, or null while it is fine.
  String? _problemText(GitProblem? problem, AppLocalizations l10n) =>
      problem == null ? null : gitProblemText(problem, l10n);
}

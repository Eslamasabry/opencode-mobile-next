part of 'git_sheet.dart';

// GitHub sign-in, in the same sheet: a code to enter on GitHub, no token to
// paste. The token never reaches this file; it only sees the state.

extension _GitSheetSignIn on _GitSheetState {
  Widget _signInStep(AppLocalizations l10n) {
    final account = this.account;
    final state = account?.state ?? const GitHubSignInLoading();
    final tokens = KitTokens.of(context);
    KitAction? primary;
    KitAction? secondary;
    final body = <Widget>[];
    switch (state) {
      case GitHubSignInLoading():
        break;
      case GitHubSignedOut(:final problem, :final details):
        body.add(
          KitText(
            l10n.gitHubSignInWhy,
            key: const Key('git-signin-why'),
            role: KitTextRole.body,
          ),
        );
        if (problem != null) {
          body
            ..add(SizedBox(height: tokens.space3))
            ..add(
              KitNotice(
                key: const Key('git-signin-problem'),
                tone: AppStatusTone.failure,
                message: gitHubSignInProblemText(problem, l10n),
              ),
            );
          if (details.trim().isNotEmpty) {
            body.add(KitDetailsFold(text: details));
          }
        }
        primary = KitAction(
          key: const Key('git-signin-start'),
          label: l10n.gitHubSignIn,
          icon: AppIconography.globe,
          onPressed: () => unawaited(account?.startSignIn()),
        );
      case GitHubRequestingCode():
        body.add(
          KitNotice(
            tone: AppStatusTone.progress,
            message: l10n.gitHubRequestingCode,
          ),
        );
        secondary = _cancelSignIn(l10n);
      case GitHubAwaitingUser(:final userCode, :final verificationUri):
        body
          ..add(KitText(l10n.gitHubEnterCode, role: KitTextRole.body))
          ..add(SizedBox(height: tokens.space3))
          ..add(
            KitOneTimeCode(
              key: const Key('git-signin-code'),
              code: userCode,
              copyLabel: l10n.gitHubCopyCode,
              copyKey: const Key('git-signin-copy'),
            ),
          )
          ..add(SizedBox(height: tokens.space3))
          ..add(KitText(l10n.gitHubWaiting, role: KitTextRole.secondary));
        primary = KitAction(
          key: const Key('git-signin-open'),
          label: l10n.gitHubOpenGitHub,
          icon: AppIconography.globe,
          // GitHub's address comes from a network answer: only the gate
          // may open it.
          onPressed: () =>
              unawaited(openExternalLink(context, verificationUri.toString())),
        );
        secondary = _cancelSignIn(l10n);
      case GitHubFinishing():
        body.add(
          KitNotice(
            tone: AppStatusTone.progress,
            message: l10n.gitHubFinishing,
          ),
        );
      case GitHubSignedIn(:final login):
        body.add(
          KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            children: [
              KitRow(
                key: const Key('git-signin-account'),
                leading: KitRow.icon(context, AppIconography.globe),
                title: l10n.gitHubSignedInAs(KitBidi.auto(login)),
                titleMaxLines: 2,
              ),
              KitRow(
                key: const Key('git-signin-manage'),
                leading: KitRow.icon(context, AppIconography.link),
                title: l10n.gitHubManageAccess,
                onTap: () => unawaited(
                  openExternalLink(context, gitHubAuthorizedAppsUrl),
                ),
              ),
            ],
          ),
        );
        secondary = KitAction(
          key: const Key('git-signin-out'),
          label: l10n.gitHubSignOut,
          neutral: true,
          onPressed: () => unawaited(_confirmSignOut(l10n)),
        );
    }
    return KitSheet(
      key: const Key('git-signin'),
      step: 'signin',
      title: l10n.gitHubSignInTitle,
      handle: false,
      leading: _back(l10n),
      onClose: _close,
      primary: primary,
      secondary: secondary,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: body,
      ),
    );
  }

  KitAction _cancelSignIn(AppLocalizations l10n) => KitAction(
    key: const Key('git-signin-cancel'),
    label: l10n.gitHubCancel,
    neutral: true,
    onPressed: () => account?.cancelSignIn(),
  );

  Future<void> _confirmSignOut(AppLocalizations l10n) async {
    final sure = await showKitConfirm(
      context,
      title: l10n.gitHubSignOutTitle,
      body: l10n.gitHubSignOutBody,
      confirmLabel: l10n.gitHubSignOutConfirm,
      cancelLabel: l10n.gitHubCancel,
    );
    if (sure) await account?.signOut();
  }
}

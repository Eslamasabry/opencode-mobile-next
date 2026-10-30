part of 'git_sheet.dart';

// The first step: one named main action pinned at the bottom (save the
// changed files and send them, else send what is saved, else get what is
// new), Get latest and Switch branch as rows, and Remotes, Clone, the
// sign-in and the exact names folded under one line. Every action names what
// it acts on ("Send to GitHub · main"); a row that cannot work says why on
// its second line.

/// What the one main action of the first step does.
enum _GitMain {
  /// Changed files and somewhere to send them: review, then one tap.
  saveAndSend,

  /// Changed files, nowhere to send yet: review, then save.
  save,

  /// Nothing to save, saves not sent yet: one tap sends.
  send,

  /// Nothing to save or send, and the check found new changes.
  getLatest,

  /// Nothing waiting: the rows alone.
  none,
}

extension _GitSheetHome on _GitSheetState {
  Widget _homeStep(AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final loading = page.view is GitPageLoading || page.busy != null;
    final snapshot = page.snapshot;
    return KitSheet(
      key: const Key('git-home'),
      step: 'home',
      title: l10n.gitSheetTitle,
      handle: false,
      loading: loading,
      onClose: _close,
      primary: snapshot == null ? null : _mainButton(snapshot, l10n),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?_busyNotice(l10n),
          ?_resultNotice(l10n, signInIsMain: _needsSignIn),
          if (_busyNotice(l10n) != null || page.lastResult != null)
            SizedBox(height: tokens.space3),
          ..._homeBody(l10n, tokens),
        ],
      ),
    );
  }

  /// The one main action, from what the snapshot says is waiting.
  _GitMain _main(GitSnapshot snapshot) {
    final abilities = page.abilities;
    final branch = snapshot.status.branch;
    if (abilities.canCommit && _saveable(snapshot).isNotEmpty) {
      return abilities.canPush && _target(snapshot) != null
          ? _GitMain.saveAndSend
          : _GitMain.save;
    }
    if (abilities.canPush &&
        _target(snapshot) != null &&
        (branch.ahead > 0 || !branch.hasUpstream)) {
      return _GitMain.send;
    }
    if (abilities.canPull && page.check == GitCheck.done && branch.behind > 0) {
      return _GitMain.getLatest;
    }
    return _GitMain.none;
  }

  /// The changed files a save can take (a conflicted one cannot).
  List<GitFileChange> _saveable(GitSnapshot snapshot) => [
    for (final file in snapshot.status.files)
      if (!file.isConflicted) file,
  ];

  KitAction? _mainButton(GitSnapshot snapshot, AppLocalizations l10n) {
    final target = _target(snapshot);
    final branch = snapshot.status.branch;
    final busy = page.busy;
    // While any action runs the button stays, quietly unavailable, so the
    // sheet does not jump; it spins only for its own action.
    final idle = busy == null || busy == GitAction.refresh;
    // A send that needs a sign-in: signing in is the one next step.
    if (_needsSignIn) {
      return KitAction(
        key: const Key('git-way-sign-in'),
        label: l10n.gitHubSignIn,
        icon: AppIconography.globe,
        onPressed: idle ? () => _go(_GitStep.signIn) : null,
      );
    }
    switch (_main(snapshot)) {
      case _GitMain.saveAndSend:
        final count = _saveable(snapshot).length;
        return KitAction(
          key: const Key('git-primary'),
          label: l10n.gitSaveAndSendTo(count, target!.place, target.branch),
          icon: AppIconography.upload,
          onPressed: idle ? () => _openSave(send: true) : null,
        );
      case _GitMain.save:
        return KitAction(
          key: const Key('git-primary'),
          label: l10n.gitSaveButton(_saveable(snapshot).length),
          icon: AppIconography.check,
          onPressed: idle ? () => _openSave(send: false) : null,
        );
      case _GitMain.send:
        return KitAction(
          key: const Key('git-primary'),
          label: branch.hasUpstream
              ? l10n.gitSendSavesTo(branch.ahead, target!.place, target.branch)
              : l10n.gitSendTo(target!.place, target.branch),
          icon: AppIconography.upload,
          working: busy == GitAction.push,
          onPressed: idle ? () => unawaited(page.push()) : null,
        );
      case _GitMain.getLatest:
        return KitAction(
          key: const Key('git-primary'),
          label: target == null
              ? l10n.gitGetLatest
              : l10n.gitGetNewFrom(branch.behind, target.place, target.branch),
          icon: AppIconography.download,
          working: busy == GitAction.pull,
          onPressed: idle ? () => unawaited(page.pull()) : null,
        );
      case _GitMain.none:
        return null;
    }
  }

  List<Widget> _homeBody(AppLocalizations l10n, KitTokens tokens) {
    switch (page.view) {
      case GitPageLoading():
        return [
          KitStateView(
            icon: AppIconography.branch,
            title: l10n.gitBusyRefresh,
            size: KitStateSize.inline,
          ),
        ];
      case GitPageUnavailable(:final reason):
        return [
          KitStateView(
            key: const Key('git-unavailable'),
            icon: AppIconography.cloudOff,
            title: l10n.gitSheetTitle,
            body: gitUnavailableText(reason, l10n),
            size: KitStateSize.inline,
          ),
        ];
      case GitPageFailed(:final failure):
        return [
          KitNotice.error(
            key: const Key('git-failed'),
            message: gitProblemText(failure.problem, l10n),
            details: failure.details,
            retry: KitAction(
              key: const Key('git-retry'),
              label: l10n.gitActionRefresh,
              onPressed: () => unawaited(page.refresh()),
            ),
          ),
        ];
      case GitPageNotRepository():
        return [
          KitStateView(
            key: const Key('git-not-repository'),
            icon: AppIconography.branch,
            title: l10n.gitNotRepositoryTitle,
            body: l10n.gitNotRepositoryBody,
            size: KitStateSize.inline,
          ),
          _group([_cloneRow(l10n)]),
          ?_signInGroup(l10n),
          ?_ownSignInLine(l10n, tokens),
        ];
      case GitPageReady(:final snapshot):
        final operation = gitOperationText(snapshot.operation, l10n);
        final main = _main(snapshot);
        final branch = snapshot.status.branch;
        final noRemote =
            !snapshot.hasRemote && !branch.detached && !branch.unborn;
        // Send has its own row only where it is not the main action and
        // there is something to send (or no remote yet, which it explains).
        final sendRow =
            noRemote ||
            (main != _GitMain.send &&
                page.abilities.canPush &&
                (branch.ahead > 0 || !branch.hasUpstream));
        // Save has a row only where the main action cannot take the files
        // (conflicts): it says why.
        final saveRow =
            snapshot.status.files.isNotEmpty &&
            main != _GitMain.saveAndSend &&
            main != _GitMain.save;
        return [
          if (operation != null) ...[
            KitNotice(
              key: const Key('git-operation'),
              tone: AppStatusTone.neutral,
              message: operation,
            ),
            SizedBox(height: tokens.space3),
          ],
          _group([
            if (main != _GitMain.getLatest) _pullRow(snapshot, l10n),
            if (sendRow) _pushRow(snapshot, l10n),
            if (saveRow) _saveRow(snapshot, l10n),
            ?_askConflictsRow(snapshot, l10n),
            _switchRow(snapshot, l10n),
          ]),
          ?_signInGroup(l10n),
          ..._more(snapshot, l10n, tokens),
        ];
    }
  }

  /// Remotes, Clone, the GitHub account once signed in, where pushes sign in
  /// elsewhere, and the exact branch, remote and address: folded and last.
  List<Widget> _more(
    GitSnapshot snapshot,
    AppLocalizations l10n,
    KitTokens tokens,
  ) {
    final branch = snapshot.status.branch;
    final remote = snapshot.pushRemote;
    final sendsTo =
        branch.upstream ??
        (remote != null && branch.head != null
            ? '${remote.name}/${branch.head}'
            : null);
    final values = [
      if (branch.head != null)
        KitTechnicalValue(l10n.gitDetailsBranch, branch.head!),
      if (branch.upstream != null)
        KitTechnicalValue(l10n.gitDetailsGetsFrom, branch.upstream!),
      if (sendsTo != null) KitTechnicalValue(l10n.gitDetailsSendsTo, sendsTo),
      if (remote?.address != null)
        KitTechnicalValue(l10n.gitDetailsAddress, remote!.address!),
    ];
    final ownSignIn = _ownSignInText(l10n);
    return [
      Padding(
        padding: EdgeInsetsDirectional.only(top: tokens.space3),
        child: KitDetailsFold(
          foldKey: const Key('git-details'),
          label: l10n.gitMoreLabel,
          notes: [?ownSignIn],
          values: values,
          child: KitRowGroup(
            margin: EdgeInsetsDirectional.zero,
            children: [
              _remotesRow(snapshot, l10n),
              _cloneRow(l10n),
              ?_signedInRow(l10n),
            ],
          ),
        ),
      ),
    ];
  }

  Widget _group(List<Widget> rows) => KitRowGroup(
    margin: EdgeInsetsDirectional.zero,
    gapBefore: KitTokens.of(context).space3,
    children: rows,
  );

  // ---------------------------------------------------------------- rows

  Widget _pullRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final branch = snapshot.status.branch;
    final can = page.abilities.canPull && !_busy;
    final upstream = branch.upstream;
    final noRemote =
        !_busy &&
        !can &&
        !snapshot.hasRemote &&
        snapshot.operation == GitOperation.none &&
        !branch.detached;
    return KitRow(
      key: const Key('git-row-pull'),
      leading: KitRow.icon(context, AppIconography.download),
      title: l10n.gitGetLatest,
      supporting: noRemote
          ? TextSpan(text: l10n.gitPullNoRemote)
          : (can || _busy) && upstream != null
          ? TextSpan(text: _pullState(branch, l10n))
          : null,
      supportingMaxLines: 2,
      enabled: can || noRemote,
      disabledReason: can || _busy || noRemote
          ? null
          : _pullReason(snapshot, l10n),
      // With no remote at all the answer is on the Remotes page.
      trailing: noRemote ? const KitChevron() : null,
      onTap: can
          ? () => unawaited(page.pull())
          : noRemote
          ? () => _go(_GitStep.remotes)
          : null,
    );
  }

  /// What the quiet look at the remote found, in plain words.
  String _pullState(GitBranchState branch, AppLocalizations l10n) =>
      switch (page.check) {
        GitCheck.failed => l10n.gitCheckFailed,
        GitCheck.done =>
          branch.behind > 0
              ? l10n.gitNewCount(branch.behind)
              : l10n.gitStateUpToDate,
        GitCheck.idle || GitCheck.checking => l10n.gitChecking,
      };

  String _pullReason(GitSnapshot snapshot, AppLocalizations l10n) {
    final branch = snapshot.status.branch;
    if (snapshot.operation != GitOperation.none) {
      return l10n.gitProblemOperationInProgress;
    }
    return branch.detached
        ? l10n.gitProblemDetachedHead
        : l10n.gitPullNotOnRemote;
  }

  Widget _pushRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final branch = snapshot.status.branch;
    final can = page.abilities.canPush && !_busy;
    // No remote at all: the row says so and opens Remotes, where one is added.
    final noRemote =
        !_busy && !snapshot.hasRemote && !branch.detached && !branch.unborn;
    final target = _target(snapshot);
    return KitRow(
      key: const Key('git-row-push'),
      leading: KitRow.icon(context, AppIconography.upload),
      // "Send to GitHub · main": where, in plain words. The exact
      // origin/main is under Details at the bottom.
      title: target != null
          ? l10n.gitSendTo(target.place, target.branch)
          : l10n.gitSend,
      titleMaxLines: 2,
      supporting: noRemote
          ? TextSpan(text: l10n.gitSendNoRemote)
          : (can || _busy) && target != null
          ? TextSpan(
              text: !branch.hasUpstream
                  ? l10n.gitStateFirstSend
                  : branch.ahead > 0
                  ? l10n.gitAhead(branch.ahead)
                  : l10n.gitStateNothingToSend,
            )
          : null,
      supportingMaxLines: 2,
      enabled: can || noRemote,
      disabledReason: can || _busy || noRemote
          ? null
          : _pushReason(snapshot, l10n),
      trailing: noRemote ? const KitChevron() : null,
      onTap: can
          ? () => unawaited(page.push())
          : noRemote
          ? () => _go(_GitStep.remotes)
          : null,
    );
  }

  String _pushReason(GitSnapshot snapshot, AppLocalizations l10n) {
    final branch = snapshot.status.branch;
    if (branch.detached) return l10n.gitProblemDetachedHead;
    if (branch.unborn) return l10n.gitNoCommitsYet;
    return l10n.gitProblemNoRemote;
  }

  /// Only where the main action cannot take the files: it says why.
  Widget _saveRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final can = page.abilities.canCommit && !_busy;
    final count = snapshot.status.files.length;
    return KitRow(
      key: const Key('git-row-save'),
      leading: KitRow.icon(context, AppIconography.check),
      title: l10n.gitSaveChanges,
      supporting: TextSpan(
        text: count == 0 ? l10n.gitCleanBody : l10n.gitSaveChangesCount(count),
      ),
      enabled: can,
      disabledReason: can || _busy
          ? null
          : snapshot.status.hasConflicts
          ? l10n.gitProblemConflicts
          : l10n.gitCleanBody,
      trailing: can ? const KitChevron() : null,
      onTap: can ? () => _openSave(send: false) : null,
    );
  }

  /// Conflicted files and an agent to ask: one tap sends the agent the
  /// files to resolve, from the conversation the sheet was opened in.
  Widget? _askConflictsRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final ask = widget.onAskAgent;
    if (ask == null || !snapshot.status.hasConflicts) return null;
    final files = [for (final file in snapshot.status.conflicts) file.path];
    return KitRow(
      key: const Key('git-row-ask-conflicts'),
      leading: KitRow.icon(context, AppIconography.sparkle),
      title: l10n.gitAskAgentFixConflicts,
      supporting: TextSpan(text: l10n.gitConflictCount(files.length)),
      trailing: const KitChevron(),
      onTap: () {
        _close();
        ask(l10n.gitAskAgentFixConflictsPrompt(files.join(', ')));
      },
    );
  }

  Widget _switchRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final can = page.abilities.canSwitchBranch && !_busy;
    final head = snapshot.status.branch.head;
    return KitRow(
      key: const Key('git-row-switch'),
      leading: KitRow.icon(context, AppIconography.branch),
      title: l10n.gitSwitchRow,
      supporting: TextSpan(
        text: head == null
            ? l10n.gitDetachedHead
            : l10n.gitSwitchRowOn(KitBidi.ltr(head)),
      ),
      enabled: can,
      disabledReason: can || _busy ? null : l10n.gitProblemOperationInProgress,
      trailing: can ? const KitChevron() : null,
      onTap: can ? () => _go(_GitStep.branches) : null,
    );
  }

  Widget _remotesRow(GitSnapshot snapshot, AppLocalizations l10n) {
    final names = snapshot.remotes.map((r) => r.name).join(', ');
    return KitRow(
      key: const Key('git-row-remotes'),
      leading: KitRow.icon(context, AppIconography.link),
      title: l10n.gitRemotesRow,
      supporting: TextSpan(
        text: snapshot.remotes.isEmpty
            ? l10n.gitRemotesRowNone
            : KitBidi.ltr(names),
      ),
      trailing: const KitChevron(),
      onTap: _busy ? null : () => _go(_GitStep.remotes),
      enabled: !_busy,
    );
  }

  Widget _cloneRow(AppLocalizations l10n) => KitRow(
    key: const Key('git-row-clone'),
    leading: KitRow.icon(context, AppIconography.cloud),
    title: l10n.gitCloneRow,
    supporting: TextSpan(text: l10n.gitCloneRowHint),
    supportingMaxLines: 2,
    trailing: const KitChevron(),
    enabled: !_busy,
    onTap: _busy ? null : () => _go(_GitStep.clone),
  );

  /// GitHub sign-in on the surface while signed out, where the app can hand
  /// a sign-in over: sending needs it first.
  Widget? _signInGroup(AppLocalizations l10n) {
    final account = this.account;
    if (!page.abilities.offersGitHubSignIn || account == null) return null;
    if (account.state is GitHubSignedIn) return null;
    // The main button offers it after a send that needed it.
    if (_needsSignIn) return null;
    return _group([
      KitRow(
        key: const Key('git-row-github'),
        leading: KitRow.icon(context, AppIconography.globe),
        title: l10n.gitHubSignIn,
        titleMaxLines: 2,
        supporting: TextSpan(text: l10n.gitHubSignedOutHint),
        supportingMaxLines: 2,
        trailing: const KitChevron(),
        onTap: () => _go(_GitStep.signIn),
      ),
    ]);
  }

  /// Signed in: the account, to sign out, folded with Remotes and Clone.
  Widget? _signedInRow(AppLocalizations l10n) {
    final account = this.account;
    if (!page.abilities.offersGitHubSignIn || account == null) return null;
    final state = account.state;
    if (state is! GitHubSignedIn) return null;
    return KitRow(
      key: const Key('git-row-github'),
      leading: KitRow.icon(context, AppIconography.globe),
      title: l10n.gitHubSignedInAs(KitBidi.auto(state.login)),
      titleMaxLines: 2,
      trailing: const KitChevron(),
      onTap: () => _go(_GitStep.signIn),
    );
  }

  /// Where pushes use the sign-in already set up where the project lives.
  String? _ownSignInText(AppLocalizations l10n) {
    if (!page.abilities.usesOwnGitSignIn) return null;
    return hostName == null
        ? l10n.gitUsesOwnSignIn
        : l10n.gitUsesOwnSignInOn(KitBidi.auto(hostName!));
  }

  /// The same line on its own, where there is no fold (not a repository).
  Widget? _ownSignInLine(AppLocalizations l10n, KitTokens tokens) {
    final text = _ownSignInText(l10n);
    if (text == null) return null;
    return Padding(
      padding: EdgeInsetsDirectional.only(top: tokens.space3),
      child: KitText(
        text,
        key: const Key('git-own-sign-in'),
        role: KitTextRole.secondary,
      ),
    );
  }
}

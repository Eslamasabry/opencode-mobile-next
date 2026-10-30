part of 'git_sheet.dart';

// The steps behind the first one: Save and send (a list of changed files to
// tick, a prefilled message, one tap that saves and sends), Switch branch, Clone, Remotes and the commit
// name. Each is the same sheet with its content swapped in place.

extension _GitSheetSteps on _GitSheetState {
  // ------------------------------------------------------- Save changes

  /// Opens the review with every file a save can take ticked and a plain
  /// message prefilled from their names. [send] makes it the one-step
  /// "Save and send".
  void _openSave({required bool send}) {
    final snapshot = page.snapshot;
    if (snapshot == null) return;
    _sendAfterSave = send;
    _picked = {
      for (final file in snapshot.status.files)
        if (!file.isConflicted) file.path,
    };
    _messageEdited = false;
    _message.text = _defaultMessage(snapshot.status.files, _picked!);
    // The first save asks once who the saves are by: a missing name, or a
    // placeholder left on the device (aiteam@localhost), would show a
    // stranger as the author once this is sent.
    if (snapshot.identity?.needsSetup ?? false) {
      _go(_GitStep.identity);
      unawaited(_prefillIdentity());
      return;
    }
    _go(_GitStep.save);
  }

  /// "Update basket.ts and checkout_form.dart": the ticked files by name, the
  /// honest default the person can change. Raw text, it goes into Git as is.
  String _defaultMessage(List<GitFileChange> files, Set<String> picked) {
    final l10n = AppLocalizations.of(context);
    final chosen = [
      for (final file in files)
        if (picked.contains(file.path)) file,
    ];
    String name(GitFileChange file) => file.path.split('/').last;
    switch (chosen.length) {
      case 0:
        return '';
      case 1:
        final file = chosen.single;
        final added =
            file.kind == GitChangeKind.untracked ||
            file.kind == GitChangeKind.added;
        return added
            ? l10n.gitMessageAddOne(name(file))
            : l10n.gitMessageUpdateOne(name(file));
      case 2:
        return l10n.gitMessageUpdateTwo(name(chosen[0]), name(chosen[1]));
      default:
        return l10n.gitMessageUpdateMany(name(chosen.first), chosen.length - 1);
    }
  }

  /// With GitHub signed in, the name and the private noreply address from the
  /// account fill the two fields (the person can change them).
  Future<void> _prefillIdentity() async {
    final profile = await account?.commitProfile();
    if (!mounted || profile == null) return;
    if (_identityName.text.isEmpty) _identityName.text = profile.commitName;
    final email = profile.commitEmail;
    if (_identityEmail.text.isEmpty && email != null) {
      _identityEmail.text = email;
    }
    _update(() {});
  }

  Widget _saveStep(AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final snapshot = page.snapshot;
    final files = snapshot?.status.files ?? const <GitFileChange>[];
    final known = {for (final f in files) f.path};
    final picked = (_picked ?? <String>{}).where(known.contains).toSet();
    final byPath = {for (final f in files) f.path: f};
    final head = snapshot?.status.branch.head;
    final target = _target(snapshot);
    final send = _sendAfterSave && page.abilities.canPush && target != null;
    final empty = _message.text.trim().isEmpty;
    final count = picked.length;
    final reason = count == 0
        ? l10n.gitProblemNothingSelected
        : empty
        ? l10n.gitProblemEmptyMessage
        : null;
    final ready = reason == null && !_busy;
    final working =
        page.busy == GitAction.commit || page.busy == GitAction.push;
    return KitSheet(
      key: const Key('git-save'),
      step: 'save',
      title: send ? l10n.gitSaveAndSendTitle : l10n.gitSaveChanges,
      // Save and send: the button names the count and the target, so no
      // line under the title repeats them.
      subtitle: send
          ? null
          : head == null
          ? null
          : l10n.gitSaveOnBranch(KitBidi.ltr(head)),
      handle: false,
      loading: _busy,
      leading: _back(l10n),
      onClose: _close,
      primary: KitAction(
        key: const Key('git-save-button'),
        // While it runs the button says which half it is on.
        label: page.busy == GitAction.push && target != null
            ? l10n.gitBusySendingTo(target.place, target.branch)
            : page.busy == GitAction.commit
            ? l10n.gitBusyCommit
            : send
            ? l10n.gitSaveAndSendTo(count, target.place, target.branch)
            : l10n.gitSaveButton(count),
        icon: send ? AppIconography.upload : AppIconography.check,
        working: working,
        disabledReason: reason,
        onPressed: ready
            ? () => unawaited(_commit(byPath, picked, send: send))
            : null,
      ),
      // Saving without sending: the full-width second button under the
      // main one, as on the Changes screen.
      secondary: send
          ? KitAction(
              key: const Key('git-save-only'),
              label: l10n.gitSaveOnly,
              onPressed: ready
                  ? () => unawaited(_commit(byPath, picked, send: false))
                  : null,
            )
          : null,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // The button says what runs; only a result needs a line here.
          if (_resultNotice(l10n) case final notice?) ...[
            notice,
            SizedBox(height: tokens.space3),
          ],
          KitChoiceList<String>.multi(
            key: const Key('git-save-files'),
            semanticsLabel: l10n.gitSaveFilesLabel,
            // The main button says "Save 2 files": no count line above.
            showCount: false,
            choices: [
              for (final file in files)
                KitChoice<String>(
                  key: Key('git-file-${file.path}'),
                  value: file.path,
                  title: KitBidi.auto(file.path),
                  supporting: _fileLine(file, l10n),
                  enabled: !file.isConflicted,
                  disabledReason: l10n.gitProblemConflicts,
                ),
            ],
            selected: picked,
            // The ticks hold still while the save runs.
            onChanged: (next) {
              if (_busy) return;
              _update(() {
                _picked = next;
                // The prefilled message follows the ticks until the person
                // writes their own.
                if (!_messageEdited) {
                  _message.text = _defaultMessage(files, next);
                }
              });
            },
          ),
          SizedBox(height: tokens.space4),
          KitField(
            fieldKey: const Key('git-save-message'),
            label: l10n.gitCommitMessageHint,
            controller: _message,
            minLines: 1,
            maxLines: 4,
            onChanged: (_) => _update(() => _messageEdited = true),
          ),
          // The agent already said what it did: one tap makes that the
          // message (it can still be edited).
          if (widget.summary case final summary? when summary.trim().isNotEmpty)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: KitButton.tertiary(
                key: const Key('git-save-write-for-me'),
                icon: AppIconography.sparkle,
                label: l10n.gitWriteItForMe,
                onPressed: _busy
                    ? null
                    : () => _update(() {
                        _message.text = summary.trim();
                        _messageEdited = true;
                      }),
              ),
            ),
        ],
      ),
    );
  }

  /// "Modified · +3 −1": the kind in words, the counts as numbers.
  String _fileLine(GitFileChange file, AppLocalizations l10n) {
    final added = file.totalAdded;
    final deleted = file.totalDeleted;
    final counts = file.binary || (added == null && deleted == null)
        ? ''
        : ' · ${KitBidi.ltr('+${added ?? 0} −${deleted ?? 0}')}';
    return '${gitChangeLabel(file, l10n)}$counts';
  }

  Future<void> _commit(
    Map<String, GitFileChange> byPath,
    Set<String> picked, {
    required bool send,
  }) async {
    // A rename also names the old path, so both halves are saved together.
    final paths = <String>[
      for (final path in picked) ...[path, ?byPath[path]?.originalPath],
    ];
    final result = send
        ? await page.commitAndPush(paths, _message.text)
        : await page.commit(paths, _message.text);
    if (!mounted || result == null) return;
    // A failed save stays here with its reason; once saved (even if the send
    // did not go) the first step says what happened.
    if (!result.ok && !page.savedBeforeSend) return;
    _message.clear();
    _picked = null;
    _update(() => _step = _GitStep.home);
  }

  // ------------------------------------------------------ Switch branch

  Widget _branchesStep(AppLocalizations l10n) {
    final snapshot = page.snapshot;
    final branches = snapshot?.branches ?? const GitBranches();
    final head = snapshot?.status.branch.head;
    final name = _branchName.text.trim();
    final invalid = name.isNotEmpty && !isValidGitBranchName(name);
    final choices = <KitChoice<String>>[
      for (final branch in branches.local)
        KitChoice<String>(
          key: Key('git-branch-${branch.name}'),
          value: branch.name,
          title: KitBidi.ltr(branch.name),
          supporting: branch.current
              ? l10n.gitBranchCurrent
              : branch.upstream == null
              ? null
              : KitBidi.ltr(branch.upstream!),
        ),
      for (final branch in branches.remoteOnly)
        KitChoice<String>(
          key: Key('git-branch-${branch.name}'),
          value: branch.localName,
          title: KitBidi.ltr(branch.localName),
          supporting: l10n.gitBranchRemoteOnly(
            KitBidi.ltr(branch.remoteName ?? ''),
          ),
        ),
    ];
    final createReason = name.isEmpty
        ? l10n.gitBranchNameHint
        : invalid
        ? l10n.gitProblemInvalidBranchName
        : null;
    return KitSheet(
      key: const Key('git-branches'),
      step: 'branches',
      title: l10n.gitSwitchRow,
      subtitle: head == null ? null : l10n.gitSwitchRowOn(KitBidi.ltr(head)),
      handle: false,
      loading: _busy,
      leading: _back(l10n),
      onClose: _close,
      primary: KitAction(
        key: const Key('git-create-branch'),
        label: head == null
            ? l10n.gitActionCreateBranch
            : l10n.gitNewBranchFrom(KitBidi.ltr(head)),
        icon: AppIconography.add,
        working: page.busy == GitAction.createBranch,
        disabledReason: createReason,
        onPressed: createReason == null && !_busy
            ? () => unawaited(_createBranch(name))
            : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?_busyNotice(l10n),
          ?_resultNotice(l10n),
          KitChoiceList<String>.single(
            key: const Key('git-branch-list'),
            semanticsLabel: l10n.gitSwitchRow,
            choices: choices,
            selected: head,
            onSelected: (value) {
              if (value == head || _busy) return;
              unawaited(_switchTo(value));
            },
          ),
          SizedBox(height: KitTokens.of(context).space3),
          KitField(
            fieldKey: const Key('git-new-branch-name'),
            label: l10n.gitBranchNameHint,
            controller: _branchName,
            error: invalid ? l10n.gitProblemInvalidBranchName : null,
            onChanged: (_) => _update(() {}),
          ),
        ],
      ),
    );
  }

  Future<void> _switchTo(String name) async {
    final result = await page.switchBranch(name);
    if (!mounted || result == null || !result.ok) return;
    _update(() => _step = _GitStep.home);
  }

  Future<void> _createBranch(String name) async {
    final result = await page.createBranch(name);
    if (!mounted || result == null || !result.ok) return;
    _branchName.clear();
    _update(() => _step = _GitStep.home);
  }

  // -------------------------------------------------------------- Clone

  Widget _cloneStep(AppLocalizations l10n) {
    final url = _cloneUrl.text.trim();
    final folder = _cloneFolder.text.trim();
    final urlProblem = url.isEmpty ? null : gitRemoteAddressProblem(url);
    final folderProblem = folder.isEmpty || isValidGitFolderName(folder)
        ? null
        : GitProblem.invalidFolderName;
    final ready =
        url.isNotEmpty &&
        urlProblem == null &&
        folder.isNotEmpty &&
        folderProblem == null;
    final parent = gitParentFolder(page.workspace?.directory ?? '/');
    final into = parent.endsWith('/') ? '$parent$folder' : '$parent/$folder';
    return KitSheet(
      key: const Key('git-clone'),
      step: 'clone',
      title: l10n.gitCloneRow,
      handle: false,
      loading: _busy,
      leading: _back(l10n),
      onClose: _close,
      primary: KitAction(
        key: const Key('git-clone-button'),
        label: l10n.gitCloneButton,
        icon: AppIconography.cloud,
        working: page.busy == GitAction.clone,
        disabledReason: ready ? null : l10n.gitCloneAddressLabel,
        onPressed: ready && !_busy
            ? () => unawaited(_clone(url, folder))
            : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?_busyNotice(l10n),
          ?_resultNotice(l10n),
          KitField(
            fieldKey: const Key('git-clone-url'),
            label: l10n.gitCloneAddressLabel,
            hint: 'https://github.com/owner/name',
            controller: _cloneUrl,
            kind: KitFieldKind.url,
            error: _problemText(urlProblem, l10n),
            autofocus: true,
            onChanged: (value) => _update(() {
              if (!_folderTouched) {
                _cloneFolder.text = suggestGitCloneFolder(value);
              }
            }),
          ),
          SizedBox(height: KitTokens.of(context).space3),
          KitField(
            fieldKey: const Key('git-clone-folder'),
            label: l10n.gitCloneFolderLabel,
            controller: _cloneFolder,
            error: _problemText(folderProblem, l10n),
            helper: folder.isEmpty
                ? null
                : l10n.gitCloneInto(KitBidi.ltr(into)),
            onChanged: (_) => _update(() => _folderTouched = true),
          ),
        ],
      ),
    );
  }

  Future<void> _clone(String url, String folder) async {
    final result = await page.clone(url, folder);
    if (!mounted || result == null || !result.ok) return;
    _cloneUrl.clear();
    _cloneFolder.clear();
    _folderTouched = false;
    // The success line ("Cloned into …") is on the first step.
    _update(() => _step = _GitStep.home);
  }

  // ------------------------------------------------------------ Remotes

  Widget _remotesStep(AppLocalizations l10n) {
    final tokens = KitTokens.of(context);
    final snapshot = page.snapshot;
    final remotes = snapshot?.remotes ?? const <GitRemote>[];
    final name = _remoteName.text.trim();
    final url = _remoteUrl.text.trim();
    final nameProblem = name.isEmpty || isValidGitRemoteName(name)
        ? null
        : GitProblem.invalidRemoteName;
    final urlProblem = url.isEmpty ? null : gitRemoteAddressProblem(url);
    final ready =
        name.isNotEmpty &&
        url.isNotEmpty &&
        nameProblem == null &&
        urlProblem == null;
    final sshOverHttps =
        page.abilities.offersGitHubSignIn &&
        remotes.any((r) => r.kind == GitRemoteKind.gitHubSsh);
    return KitSheet(
      key: const Key('git-remotes'),
      step: 'remotes',
      title: l10n.gitRemotesTitle,
      handle: false,
      loading: _busy,
      leading: _back(l10n),
      onClose: _close,
      primary: KitAction(
        key: const Key('git-add-remote'),
        label: l10n.gitActionAddRemote,
        icon: AppIconography.add,
        working: page.busy == GitAction.addRemote,
        disabledReason: ready ? null : l10n.gitRemoteAddressLabel,
        onPressed: ready && !_busy
            ? () => unawaited(_addRemote(name, url))
            : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?_busyNotice(l10n),
          ?_resultNotice(l10n),
          if (remotes.isEmpty)
            KitText(
              l10n.gitRemotesEmpty,
              key: const Key('git-remotes-empty'),
              role: KitTextRole.secondary,
            )
          else
            KitRowGroup(
              margin: EdgeInsetsDirectional.zero,
              leadingIcons: false,
              children: [
                for (final remote in remotes)
                  KitRow(
                    key: Key('git-remote-${remote.name}'),
                    title: KitBidi.ltr(remote.name),
                    supporting: TextSpan(
                      text: KitBidi.ltr(remote.address ?? ''),
                    ),
                    supportingMaxLines: 3,
                  ),
              ],
            ),
          if (sshOverHttps)
            Padding(
              padding: EdgeInsetsDirectional.only(top: tokens.space2),
              child: KitText(
                l10n.gitHubPushOverHttps,
                role: KitTextRole.secondary,
              ),
            ),
          SizedBox(height: tokens.space4),
          KitSectionLabel.inline(l10n.gitRemoteAddTitle),
          KitField(
            fieldKey: const Key('git-remote-name'),
            label: l10n.gitRemoteNameLabel,
            hint: remotes.isEmpty ? 'origin' : null,
            controller: _remoteName,
            error: _problemText(nameProblem, l10n),
            onChanged: (_) => _update(() {}),
          ),
          SizedBox(height: tokens.space3),
          KitField(
            fieldKey: const Key('git-remote-url'),
            label: l10n.gitRemoteAddressLabel,
            hint: 'https://github.com/owner/name',
            controller: _remoteUrl,
            kind: KitFieldKind.url,
            error: _problemText(urlProblem, l10n),
            onChanged: (_) => _update(() {}),
          ),
        ],
      ),
    );
  }

  Future<void> _addRemote(String name, String url) async {
    final result = await page.addRemote(name, url);
    if (!mounted || result == null || !result.ok) return;
    _remoteName.clear();
    _remoteUrl.clear();
    // Stay here: the new remote appears in the list above.
    _update(() {});
  }

  // ----------------------------------------------------------- Identity

  Widget _identityStep(AppLocalizations l10n) {
    final name = _identityName.text.trim();
    final email = _identityEmail.text.trim();
    final ready = name.isNotEmpty && email.contains('@');
    return KitSheet(
      key: const Key('git-identity'),
      step: 'identity',
      title: l10n.gitIdentityTitle,
      handle: false,
      loading: _busy,
      leading: _back(l10n),
      onClose: _close,
      primary: KitAction(
        key: const Key('git-identity-save'),
        label: l10n.gitActionSaveIdentity,
        working: page.busy == GitAction.setIdentity,
        disabledReason: ready ? null : l10n.gitIdentityEmail,
        onPressed: ready && !_busy ? () => unawaited(_saveIdentity()) : null,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ?_resultNotice(l10n),
          KitText(
            l10n.gitIdentityWhy,
            key: const Key('git-identity-why'),
            role: KitTextRole.secondary,
          ),
          SizedBox(height: KitTokens.of(context).space3),
          KitField(
            fieldKey: const Key('git-identity-name'),
            label: l10n.gitIdentityName,
            controller: _identityName,
            autofocus: true,
            onChanged: (_) => _update(() {}),
          ),
          SizedBox(height: KitTokens.of(context).space3),
          KitField(
            fieldKey: const Key('git-identity-email'),
            label: l10n.gitIdentityEmail,
            controller: _identityEmail,
            onChanged: (_) => _update(() {}),
          ),
        ],
      ),
    );
  }

  Future<void> _saveIdentity() async {
    final result = await page.setIdentity(
      _identityName.text,
      _identityEmail.text,
    );
    if (!mounted || result == null || !result.ok) return;
    // Back to Save changes, where the commit was stopped.
    _update(() => _step = _GitStep.save);
  }
}

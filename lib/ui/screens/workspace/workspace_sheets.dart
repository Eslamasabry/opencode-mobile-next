part of '../workspace_screen.dart';

/// The context sheet and the per-session actions.
extension _WorkspaceSheets on _WorkspaceScreenState {
  /// One project switchboard (audit UX-P0-02; map `workspace-context-sheet`,
  /// proposal "fix"): titled with the project and the server it is on, it
  /// switches project, starts a new one, chooses where it runs ("Runs on",
  /// the current one marked in words), and keeps the folder path under
  /// Details, last and collapsed. The project's tools live on the Project
  /// tab (manage-project merged into project-hub, slice-P3.11a).
  ///
  /// Opened from a folder row ([folder] set: a conversation running outside
  /// the project root, or a server that cannot manage projects), Details
  /// shows that folder, open, instead of a separate folder dialog (map
  /// `workspace-directory-details-dialog`, merged here).
  Future<void> _openContextSheet({String? folder}) async {
    final controller = widget.controller;
    final l10n = _l10n(context);
    final manages = controller.capabilities.projectManagement;
    final title = !manages && folder != null
        ? _WorkspaceScreenState._basename(folder)
        : _selectedProject?.name ??
              (_selectedDirectory == null
                  ? l10n.e7WorkspaceNoProjectSelected
                  : _WorkspaceScreenState._basename(_selectedDirectory!));
    final server = _serverName(controller);
    final workspace = _selectedWorkspace;
    final subtitle = [
      if (server != null && server.isNotEmpty)
        l10n.workspaceContextOn(KitBidi.auto(server)),
      if (workspace != null)
        KitBidi.auto(_WorkspaceScreenState._workspaceName(workspace)),
    ].join(' · ');
    final directory = folder ?? _contextDirectory;
    final canCreate = manages && ProjectFolderActions.canCreate(controller);
    final choice = await showKitSheet<_ContextChoice>(
      context,
      title: title,
      subtitle: subtitle.isEmpty ? null : subtitle,
      icon: AppIconography.files,
      sheetKey: const ValueKey('workspace-context-sheet'),
      body: (sheetContext) {
        void pick(_ContextChoice choice) =>
            Navigator.of(sheetContext).pop(choice);
        Widget runsOn({
          required Key key,
          required IconData icon,
          required String name,
          required bool current,
          String? path,
          required _ContextChoice choice,
        }) => KitRow(
          key: key,
          leading: KitRowIcon(icon, current: current),
          title: name,
          selected: current,
          supporting: current || path != null
              ? TextSpan(
                  children: [
                    if (current)
                      kitCurrentSpan(
                        sheetContext,
                        l10n.workspaceContextCurrent,
                      ),
                    if (path != null && path.isNotEmpty)
                      TextSpan(text: KitBidi.ltr(path)),
                  ],
                )
              : null,
          onTap: () => pick(choice),
        );
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (!controller.supportsSessionReadState)
              KitNotice(
                message: l10n.returnBriefStatusUnknown,
                icon: AppIconography.info,
                liveRegion: false,
              ),
            if (manages)
              KitRowGroup(
                children: [
                  KitRow(
                    key: const ValueKey('context-switch-project'),
                    leading: KitRow.icon(sheetContext, AppIconography.swap),
                    title: l10n.e7WorkspaceSwitchProject,
                    supporting: TextSpan(
                      text: l10n.e7WorkspaceOpenProjectCount(
                        _projects?.length ?? 0,
                      ),
                    ),
                    trailing: const KitChevron(),
                    onTap: () => pick(const _ContextChoice.switchProject()),
                  ),
                  if (canCreate)
                    KitRow(
                      key: const ValueKey('context-new-project'),
                      leading: KitRow.icon(
                        sheetContext,
                        AppIconography.folderAdd,
                      ),
                      title: l10n.workspaceContextNewProject,
                      trailing: const KitChevron(),
                      onTap: () => pick(const _ContextChoice.newProject()),
                    ),
                ],
              ),
            if (_workspaces.isNotEmpty)
              KitRowGroup(
                label: l10n.workspaceContextRunsOn,
                children: [
                  runsOn(
                    key: const ValueKey('workspace-option-local'),
                    icon: AppIconography.computer,
                    name: l10n.e7WorkspaceThisComputer,
                    current: _selectedWorkspaceID == null,
                    choice: const _ContextChoice.workspace(null),
                  ),
                  for (final workspace in _workspaces)
                    runsOn(
                      key: ValueKey('workspace-option-${workspace.id}'),
                      icon: AppIconography.cloud,
                      name: _WorkspaceScreenState._workspaceName(workspace),
                      current: workspace.id == _selectedWorkspaceID,
                      path: workspace.directory,
                      choice: _ContextChoice.workspace(workspace),
                    ),
                ],
              ),
            KitDetailsFold(
              foldKey: const ValueKey('workspace-context-details'),
              initiallyExpanded: folder != null,
              values: [
                KitTechnicalValue(
                  l10n.workspaceContextFolder,
                  directory?.isNotEmpty == true
                      ? directory!
                      : l10n.e7WorkspaceNoFolder,
                  copyable: directory?.isNotEmpty == true,
                ),
              ],
            ),
          ],
        );
      },
    );
    if (!mounted || choice == null) return;
    if (choice.newProject) {
      await _createProjectFolder();
      return;
    }
    if (choice.switchProject) {
      await _openProjects();
      return;
    }
    await _selectWorkspace(choice.workspace);
  }

  String _titleOf(Session session) {
    final l10n = _l10n(context);
    return presentedSessionTitle(
      session,
      fallback: l10n.globalSessionsUntitled,
      l10n: l10n,
    );
  }

  /// Says a failed act once, above the list (§4.8), until dismissed.
  void _say(Object error) {
    if (!mounted) return;
    _set(() => _notice = productErrorText(error));
  }

  /// A conversation's facts, from its row's menu: Conversation context,
  /// where the folder and shared link sit under Details beside how full
  /// the conversation is (map `workspace-session-details-sheet`, merged
  /// into session-context in slice-P3.11a).
  Future<void> _openSessionContext(Session session) => pushKitPage<void>(
    context,
    (_) => SessionContextScreen(
      controller: widget.controller,
      sessionID: session.id,
    ),
  );

  /// The finished run [session] stands for, if it is still unreviewed.
  ReturnBriefRun? _unreviewedRun(Session session) {
    final controller = widget.controller;
    return ReturnBrief.unreviewedRun(
      session,
      readStateKnown: controller.supportsSessionReadState,
      isUnread: controller.isSessionUnread,
      isBusy: controller.busySessions.contains,
      ack: controller.returnBriefAcknowledgement,
    );
  }

  /// Review results: the run's result screen, retired if the project or
  /// server changes under it. Looking is not acknowledging; the mark stays
  /// until the conversation is opened or marked reviewed.
  Future<void> _review(Session session) async {
    final controller = widget.controller;
    final scope = controller.returnBriefScope;
    bool current() =>
        mounted &&
        controller.returnBriefScope == scope &&
        controller.isProfileReadable(scope.$1);
    final routes = RequestRoutes(changes: controller, isPending: current);
    try {
      await pushKitPage<void>(context, (context) {
        routes.own(ModalRoute.of(context));
        return RunResultScreen(controller: controller, sessionID: session.id);
      });
    } finally {
      routes.close();
    }
  }

  /// Mark as reviewed: this device stops flagging the run. The conversation
  /// stays unread on the server and nothing is answered, exactly what the
  /// old card's "Dismiss shown items" did for one row.
  Future<void> _markReviewed(Session session) async {
    final controller = widget.controller;
    final run = _unreviewedRun(session);
    if (run == null) return;
    final failed = _l10n(context).workMarkReviewedFailed;
    try {
      await controller.dismissReturnBrief(
        ReturnBrief.single(run),
        expectedScope: controller.returnBriefScope,
      );
    } catch (_) {
      _say(failed);
    }
  }

  Future<void> _togglePin(Session session) async {
    final controller = widget.controller;
    final failed = _l10n(context).sessionPinFailed;
    try {
      await controller.setSessionPinned(
        session.id,
        !controller.isSessionPinned(session.id),
        locationRevision: controller.locationRevision,
      );
    } catch (_) {
      _say(failed);
    }
  }

  /// Archive, from the swipe and from the menu alike (one path, P3.12):
  /// the row goes at once, Undo stands for the window, and only then is
  /// the server told. Archiving has no server-side reverse, so the undo
  /// window *is* the safety net (DATA-11 a, b).
  KitSwipeAction _archiveSwipe(Session session) {
    final l10n = _l10n(context);
    final title = _titleOf(session);
    return KitSwipeAction(
      id: ValueKey('session-dismiss-${session.id}'),
      label: l10n.e7WorkspaceArchive,
      icon: AppIconography.archive,
      undoMessage: l10n.e7WorkspaceArchivedToast(title),
      onAct: () async {
        if (!mounted || _pendingArchive.contains(session.id)) return false;
        _set(() {
          _pendingArchive.add(session.id);
          if (_selectedSessionID == session.id) _selectedSessionID = null;
        });
        return true;
      },
      onUndo: () {
        if (mounted) _set(() => _pendingArchive.remove(session.id));
      },
      // Worded now: the commit may run after Work has gone (the window
      // closes, a new Undo arrives, the route pops or the app pauses).
      onCommit: () =>
          _commitArchive(session, l10n.workspaceArchiveFailed(title)),
    );
  }

  Future<void> _commitArchive(Session session, String failed) async {
    final controller = widget.controller;
    try {
      final repository = await controller.prepareActionRepository();
      if (repository == null) throw ProductException(failed);
      await repository.archiveSession(session.id);
      await controller.refreshSessions();
    } catch (_) {
      _say(failed);
    } finally {
      if (mounted) _set(() => _pendingArchive.remove(session.id));
    }
  }

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final failureMessage = _l10n(context).e7WorkspaceReconnectingShortly;
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw ProductException(failureMessage);
  }

  /// Rename: one field; a failure stays in the dialog under the field.
  Future<void> _rename(Session session) async {
    final l10n = _l10n(context);
    final controller = widget.controller;
    final renamed = await showKitInputDialog(
      context,
      title: l10n.e7WorkspaceRenameSession,
      label: l10n.e7WorkspaceTitle,
      confirmLabel: l10n.fileSave,
      initial: session.title ?? '',
      dialogKey: const ValueKey('workspace-rename-dialog'),
      onSubmit: (value) async {
        final title = value.trim();
        if (title.isEmpty) return null;
        try {
          await controller.renameSession(session.id, title);
          return null;
        } catch (error) {
          return productErrorText(error);
        }
      },
    );
    if (renamed != null && mounted) await controller.refreshSessions();
  }

  /// Share: says who can see it; the link is copied once the server has
  /// made it. A failure stays in the confirmation (DATA-14).
  Future<void> _share(Session session) async {
    final l10n = _l10n(context);
    String? link;
    final shared = await showKitConfirm(
      context,
      icon: AppIconography.globe,
      title: l10n.e7WorkspaceShareConfirm,
      body: l10n.e7WorkspaceShareDetail(_titleOf(session)),
      consequences: [l10n.workspaceShareCopiesLink],
      confirmLabel: l10n.e7WorkspaceShareSession,
      sheetKey: const ValueKey('workspace-share-confirm'),
      confirmKey: const ValueKey('workspace-share-confirm-button'),
      action: () async {
        final repository = await _requireActionRepository();
        final url = await repository.shareSession(session.id);
        if (url == null || url.isEmpty) {
          throw ProductException(l10n.e7WorkspaceNoShareLink);
        }
        link = url;
      },
    );
    final url = link;
    if (!shared || url == null || !mounted) return;
    await KitCopy.copy(
      context,
      url,
      announcement: l10n.e7WorkspaceShareCopied,
      redact: false,
    );
    await widget.controller.refreshSessions();
  }

  Future<void> _unshare(Session session) async {
    if (!await confirmStopSharing(context)) return;
    try {
      final repository = await _requireActionRepository();
      await repository.unshareSession(session.id);
      await widget.controller.refreshSessions();
    } catch (error) {
      _say(error);
    }
  }

  /// Delete: nobody can bring it back, so it is confirmed (DATA-11); a
  /// shared conversation also says its link stops working. A failure stays
  /// in the confirmation (DATA-14).
  Future<void> _delete(Session session) async {
    final l10n = _l10n(context);
    final controller = widget.controller;
    final deleted = await showKitConfirm(
      context,
      kind: KitConfirmKind.destructive,
      icon: AppIconography.delete,
      title: l10n.e7WorkspaceDeleteConfirm,
      body: l10n.e7WorkspaceDeleteDetail(_titleOf(session)),
      consequences: [
        if (session.shareUrl != null) l10n.workspaceDeleteSharedLink,
      ],
      confirmLabel: l10n.promptStashDelete,
      sheetKey: const ValueKey('workspace-delete-confirm'),
      confirmKey: const ValueKey('workspace-delete-confirm-button'),
      action: () => controller.deleteSession(session.id),
    );
    if (!deleted || !mounted) return;
    if (_selectedSessionID == session.id) {
      _set(() => _selectedSessionID = null);
    }
    await controller.refreshSessions();
  }
}

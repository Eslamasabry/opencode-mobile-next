part of '../workspace_screen.dart';

/// The Work tab's build method and its first-load and status pieces.
extension _WorkspaceBuild on _WorkspaceScreenState {
  Widget _buildScreen(BuildContext context) {
    // The Work tab, top to bottom (work-tab cleanup, 2026-09-24): the
    // project header from the first frame, one loading bar, at most one
    // status line, this project's conversations in one list ordered by
    // urgency, the other projects once each, and New conversation docked
    // below the list.
    // Project discovery and session inventory are independent: a pending
    // or failed catalog never hides conversations the server can list.
    // Rows archived by swipe or menu vanish at once and come back on Undo;
    // the server call only happens once the Undo window has closed.
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final tokens = KitTokens.of(context);
    final controller = widget.controller;
    final sessions = controller
        .sortedSessions()
        .where((session) => !_pendingArchive.contains(session.id))
        .toList();
    // One list, one order (UX plan 5.7; owner decision 2026-09-27, no state
    // sections): what needs me, what is running, what I pinned, what I did
    // recently. A conversation appears once, in the first group that fits: a
    // blocked or running pin returns among the pins as soon as it is
    // answered or finishes, because every group is cut from the same sorted
    // list.
    final blockers = <String, String>{
      for (final session in sessions) session.id: ?_blocker(session.id, l10n),
    };
    final attention = sessions
        .where((session) => blockers.containsKey(session.id))
        .toList();
    final active = sessions
        .where(
          (session) =>
              !blockers.containsKey(session.id) &&
              controller.busySessions.contains(session.id),
        )
        .toList();
    final pinned = sessions
        .where(
          (session) =>
              !blockers.containsKey(session.id) &&
              !controller.busySessions.contains(session.id) &&
              controller.isSessionPinned(session.id),
        )
        .toList();
    final recent = sessions
        .where(
          (session) =>
              !blockers.containsKey(session.id) &&
              !controller.busySessions.contains(session.id) &&
              !controller.isSessionPinned(session.id),
        )
        .toList();
    // A finished result nobody has looked at carries its mark in its own
    // row; the separate "Unreviewed work" card is gone (item 5).
    final acknowledged = controller.returnBriefAcknowledgement;
    ReturnBriefRun? unreviewed(Session session) =>
        blockers.containsKey(session.id)
        ? null
        : ReturnBrief.unreviewedRun(
            session,
            readStateKnown: controller.supportsSessionReadState,
            isUnread: controller.isSessionUnread,
            isBusy: controller.busySessions.contains,
            ack: acknowledged,
          );
    final archived = controller.archivedSessions();
    // The team is one strip at the top (docs/design/team-conversation-
    // 2026-09-26.md): counts, and its most urgent tasks. Its tasks are not
    // repeated in the list below.
    final team = controller.orchestration;
    final teamStrip = team == null ? null : _teamStrip(team, l10n);
    final capabilities = controller.capabilities;
    final pinNudge = controller.nudges.activeFor(NudgeRegistry.workScope);
    _queuePinNudge(
      visible:
          TickerMode.valuesOf(context).enabled &&
          (ModalRoute.of(context)?.isCurrent ?? true),
      // Only with something to pin, a way to pin it, and no pin here yet:
      // someone who already pins has nothing to learn from the tip.
      canOffer:
          controller.canPinSessions &&
          controller.pinnedSessionIDs.isEmpty &&
          recent.isNotEmpty,
    );

    // A project is being restored (or picked for a fresh connection): the
    // header names it and the chooser waits. The chooser only appears once
    // that has finished and there really is no project (item 1).
    final restoring =
        capabilities.projectManagement &&
        controller.workspaceChoiceRequired &&
        _pendingDirectory != null;

    // No project folder yet (or an older build saved the server's home
    // folder): sessions cannot start until the user creates or opens one.
    // The chooser waits for the project list so an auto-opened project does
    // not flash it first, and it keeps the server-wide session finder so
    // earlier conversations stay reachable.
    if (capabilities.projectManagement &&
        controller.workspaceChoiceRequired &&
        !restoring &&
        (_projects != null || _projectError != null)) {
      // The chooser is a state of its own; a server that stops answering is
      // still said above it, in the same one status line.
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          WorkStatusLine(
            controller: controller,
            serverOnThisPhone: widget.serverOnThisPhone,
            onRestartServer: widget.onRestartServer,
          ),
          Expanded(child: _chooser(capabilities)),
        ],
      );
    }

    final partial =
        controller.hasMoreSessions ||
        controller.sessionsLoading ||
        controller.sessionsError != null;
    // Loading for the first time: nothing listed yet and something is on
    // its way. Skeleton rows stand in, and no empty or "load more" text
    // contradicts them (item 4).
    final firstLoad =
        sessions.isEmpty &&
        _pendingArchive.isEmpty &&
        controller.sessionsError == null &&
        (restoring ||
            controller.sessionsLoading ||
            controller.locationLoading ||
            controller.connectionLoading);
    // One bar for everything loading the first time (item 3).
    final loading =
        firstLoad ||
        restoring ||
        (capabilities.projectManagement &&
            _projects == null &&
            _projectError == null) ||
        controller.locationLoading ||
        controller.connectionLoading;
    // Teach only when the list is known to be empty. A partial or failed
    // load cannot claim "no conversations yet", and a row waiting out its
    // Undo window is hidden, not gone.
    final nothingYet =
        !partial &&
        !firstLoad &&
        sessions.isEmpty &&
        archived.isEmpty &&
        _pendingArchive.isEmpty;
    final headerDirectory = _headerDirectory;
    // Asked here so the chooser knows by the time New conversation is
    // tapped.
    _teamPossibleNow();
    final wide = KitScreen.showsDetail(context);
    // The conversation in the detail pane; gone once it is archived,
    // deleted or no longer listed.
    final selectedID = wide ? _selectedSessionID : null;
    final detailID =
        selectedID != null &&
            controller.sessionsById.containsKey(selectedID) &&
            !_pendingArchive.contains(selectedID)
        ? selectedID
        : null;

    final rowNow = DateTime.now();
    Widget row(Session session, {bool busy = false, String? blocker}) =>
        _SessionRow(
          controller: controller,
          session: session,
          busy: busy,
          status: _rowStatus.statusFor(session.id),
          now: rowNow,
          blocker: blocker,
          unreviewed: busy ? null : unreviewed(session),
          selected: session.id == detailID,
          onOpen: _openSession,
          onMenuAction: _sessionMenuAction,
          onReview: _review,
          onMarkReviewed: _markReviewed,
          onPin: _togglePin,
          onDelete: _delete,
          archive: capabilities.sessionArchive ? _archiveSwipe(session) : null,
        );

    // The top of the one list, most urgent first: needs you, running, pins.
    final head = <Widget>[
      for (final session in attention)
        KeyedSubtree(
          key: ValueKey('work-needs-you-${session.id}'),
          child: row(
            session,
            busy: controller.busySessions.contains(session.id),
            blocker: blockers[session.id],
          ),
        ),
      for (final session in active)
        KeyedSubtree(
          key: ValueKey('work-running-${session.id}'),
          child: row(session, busy: true),
        ),
      for (final session in pinned)
        KeyedSubtree(
          key: ValueKey('work-pinned-${session.id}'),
          child: row(
            session,
            busy: controller.busySessions.contains(session.id),
          ),
        ),
    ];
    // The empty state only when the whole list is empty.
    final showEmpty =
        head.isEmpty &&
        recent.isEmpty &&
        teamStrip == null &&
        !partial &&
        !firstLoad;

    final notice = _notice;
    // No project on this server yet: the empty state says so and holds
    // the search itself.
    final noProjects =
        capabilities.projectManagement &&
        _projects?.isEmpty == true &&
        headerDirectory == null;
    final header = <Widget>[
      // Which project this is stays put while the list scrolls (UX plan
      // 5.7), and it is there from the first frame: the folder is known
      // before the project list is (item 2). A single chevron opens the
      // project sheet with the full folder path, switching and managing.
      if (capabilities.projectManagement && headerDirectory != null)
        _ProjectHeader(
          key: const ValueKey('current-project-entry'),
          // The phone server's folder of projects is where it starts, not
          // a project: say a project is still to be chosen.
          name: _WorkspaceScreenState._isPhoneProjectsRoot(headerDirectory)
              ? l10n.e7WorkspaceChooseProject
              : _projectName(headerDirectory),
          // With no project yet there is nothing to describe or manage:
          // go straight to the list, where creating one comes first.
          onTap: _WorkspaceScreenState._isPhoneProjectsRoot(headerDirectory)
              ? _openProjects
              : _openContextSheet,
        ),
      // Still context, not management: the conversation is running
      // somewhere other than the project root.
      if (capabilities.projectManagement &&
          _hasExternalSessionDirectory &&
          controller.busySessions.isNotEmpty)
        KitRow(
          key: const ValueKey('active-session-directory'),
          leading: KitRow.icon(context, AppIconography.nested),
          title: _WorkspaceScreenState._basename(_selectedDirectory!),
          supporting: TextSpan(
            text: l10n.e7WorkspaceActiveDirectory(
              KitBidi.ltr(_selectedDirectory!),
            ),
          ),
          onTap: () => _openContextSheet(folder: _selectedDirectory),
        ),
      if (!capabilities.projectManagement &&
          controller.directory?.isNotEmpty == true)
        KitRow(
          key: const ValueKey('restricted-directory-context'),
          leading: KitRow.icon(context, AppIconography.files),
          title: _WorkspaceScreenState._basename(controller.directory!),
          // A path reads left to right in any interface: isolate it.
          supporting: TextSpan(text: KitBidi.ltr(controller.directory!)),
          onTap: () => _openContextSheet(folder: controller.directory),
        ),
    ];

    final pinHeader = wide || MediaQuery.textScalerOf(context).scale(16) < 28;
    final list = KitRefresh(
      onRefresh: _refreshWorkspace,
      child: KitScrollArea(
        builder: (scrollController) => CustomScrollView(
          controller: scrollController,
          key: const PageStorageKey('workspace-scroll'),
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            // At very large text the project header scrolls with the list:
            // pinned, it and the New conversation block leave the list no
            // room on a small window.
            if (!pinHeader)
              for (final row in header) SliverToBoxAdapter(child: row),
            // 1. At most one status line, and only with something to
            // do (item 3, 6, 7).
            SliverToBoxAdapter(child: _statusLine(context, l10n)),
            // A failed act, said where the list is, until dismissed.
            if (notice != null)
              SliverPadding(
                padding: EdgeInsetsDirectional.fromSTEB(
                  tokens.gutter,
                  tokens.space2,
                  tokens.gutter,
                  tokens.space2,
                ),
                sliver: SliverToBoxAdapter(
                  child: KitNotice(
                    key: const ValueKey('work-notice'),
                    message: notice,
                    tone: AppStatusTone.failure,
                    icon: AppIconography.warning,
                    onDismiss: () => _set(() => _notice = null),
                    dismissLabel: l10n.workspaceDismissNotice,
                  ),
                ),
              ),
            if (noProjects)
              SliverToBoxAdapter(
                child: KitStateView(
                  size: KitStateSize.inline,
                  liveRegion: false,
                  icon: AppIconography.folders,
                  illustration: const StatesFolderScene(),
                  title: l10n.e7WorkspaceNoProjects,
                  body: capabilities.globalSessionSearch
                      ? l10n.e7WorkspaceNoProjectsSearch
                      : l10n.e7WorkspaceServerNoProjects,
                  tertiary: [
                    if (capabilities.globalSessionSearch)
                      KitAction(
                        label: l10n.workspaceSearchAllSessions,
                        onPressed: _openAllSessions,
                      ),
                  ],
                ),
              ),
            // A project this tab opened by itself, said once (P6.6).
            if (_defaultNotice case final said?)
              SliverPadding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                  vertical: tokens.space2,
                ),
                sliver: SliverToBoxAdapter(
                  // The only project has no other to offer; the header
                  // still opens the project sheet.
                  child: _defaultCanChange
                      ? KitNotice.offer(
                          key: const ValueKey('work-default-project'),
                          icon: AppIconography.folders,
                          message: said,
                          action: KitAction(
                            label: l10n.defaultProjectChange,
                            onPressed: () {
                              _set(() => _defaultNotice = null);
                              unawaited(_openProjects());
                            },
                          ),
                          dismissLabel: l10n.workspaceDismissNotice,
                          onDismiss: () => _set(() => _defaultNotice = null),
                        )
                      : KitNotice(
                          key: const ValueKey('work-default-project'),
                          icon: AppIconography.folders,
                          message: said,
                          dismissLabel: l10n.workspaceDismissNotice,
                          onDismiss: () => _set(() => _defaultNotice = null),
                        ),
                ),
              ),
            // The pin tip sits just above the list it is about.
            if (pinNudge != null)
              SliverPadding(
                padding: EdgeInsetsDirectional.symmetric(
                  horizontal: tokens.gutter,
                  vertical: tokens.space2,
                ),
                sliver: SliverToBoxAdapter(
                  child: KitNotice.offer(
                    key: ValueKey('nudge-${pinNudge.id.name}'),
                    icon: AppIconography.pin,
                    message: l10n.nudgePin,
                    action: KitAction(
                      label: l10n.e7GlossaryGotIt,
                      onPressed: () =>
                          unawaited(controller.nudges.dismiss(pinNudge.id)),
                    ),
                    dismissLabel: l10n.nudgeDismiss,
                    onDismiss: () =>
                        unawaited(controller.nudges.dismiss(pinNudge.id)),
                  ),
                ),
              ),
            // Ordered by urgency: what waits on the person (amber mark and
            // the words "Needs you"), then running work (spinner and
            // "Working"), then pins, then the rest newest first. The row's
            // mark and worded state carry the meaning, not a heading. The
            // urgent head moves gently as rows come and go (design
            // standard §10); the rest may be long, so it stays lazy.
            if (firstLoad)
              SliverToBoxAdapter(child: _firstLoadRows(l10n))
            else if (showEmpty)
              SliverToBoxAdapter(
                child: KitStateView(
                  key: ValueKey(
                    nothingYet ? 'work-empty-teaching' : 'work-empty-recent',
                  ),
                  size: KitStateSize.inline,
                  liveRegion: false,
                  icon: AppIconography.chat,
                  illustration: const StatesSheetScene(),
                  title: nothingYet
                      ? l10n.emptyTeachWorkTitle
                      : l10n.e7WorkspaceNoRecent,
                  body: nothingYet
                      ? l10n.emptyTeachWorkMessage
                      : controller.directory == null
                      ? l10n.e7WorkspaceChooseFolderToStart
                      : l10n.e7WorkspaceStartInWorkspace,
                  // No button here: the pinned New conversation is the
                  // one action that fills this list, and an empty Work
                  // tab keeps exactly one of them (workspace_hierarchy_test
                  // pins that).
                ),
              )
            else ...[
              // The first read failed with nothing listed yet: the titles
              // from last time stay, read-only, beside the failure the
              // pager says (codex-speed contract item 1).
              if (head.isEmpty &&
                  recent.isEmpty &&
                  controller.sessionsError != null)
                if (_lastKnown case final lastKnown?)
                  SliverToBoxAdapter(
                    child: LastKnownSessions(
                      preview: lastKnown,
                      refreshing: false,
                    ),
                  ),
              SliverToBoxAdapter(
                child: PhoneTeamSetupStrip(connection: controller),
              ),
              if (teamStrip != null) SliverToBoxAdapter(child: teamStrip),
              if (head.isNotEmpty)
                SliverToBoxAdapter(child: KitAnimatedRows(children: head)),
              SliverList.builder(
                itemCount: recent.length,
                itemBuilder: (context, index) => row(recent[index]),
              ),
            ],
            // Older pages: the list pages itself as its end comes near
            // (target-ia §1.4), with skeletons while a page loads and a
            // failed page said in place. Built lazily, so a long list asks
            // for the next page only once it is scrolled to.
            if (OlderSessionsPager.showsFor(controller))
              SliverList.builder(
                itemCount: 1,
                itemBuilder: (context, _) =>
                    OlderSessionsPager(controller: controller),
              ),
            // One way to every other conversation (R3, R4): All
            // conversations spans every project on the server and holds the
            // Archived filter, so no separate archived row or menu repeats
            // it. The AI Team keeps its own door in Settings.
            if (capabilities.globalSessionSearch && !noProjects)
              SliverToBoxAdapter(
                child: KitRow(
                  key: const ValueKey('search-all-sessions'),
                  leading: KitRow.icon(context, AppIconography.searchList),
                  title: l10n.workspaceSearchAllSessions,
                  supporting: TextSpan(text: l10n.workspaceSearchAllDetail),
                  trailing: const KitChevron(),
                  onTap: _openAllSessions,
                ),
              ),
            // 5. Everything else going on: the other projects on this
            // server once each, then other servers with something
            // running or waiting (items 6 and 8).
            // The same gap as between the sections above (SectionLabel's
            // top): the panel's own label carries none.
            SliverPadding(
              padding: EdgeInsetsDirectional.only(top: tokens.sectionGap),
              sliver: SliverToBoxAdapter(
                child: OtherProjectsPanel(
                  controller: controller,
                  currentDirectory: headerDirectory,
                  onAllProjects: _openProjects,
                ),
              ),
            ),
            SliverToBoxAdapter(
              child: OtherServersPanel(controller: controller),
            ),
            SliverToBoxAdapter(
              child: SizedBox(
                key: const ValueKey('workspace-scroll-end'),
                height: tokens.space4,
              ),
            ),
          ],
        ),
      ),
    );

    // 6. New conversation, pinned below the list rather than over it, so
    // no row or header ever sits under it or under the tab bar: the list
    // ends where the pinned block begins (item 9, standard §1).
    final bottom = _QuickAskPill(
      creating: _creating,
      onTap: _creating || controller.workspaceChoiceRequired
          ? null
          : _newConversation,
    );

    if (!wide) {
      return KitScreen(
        header: pinHeader ? header : const [],
        // One bar for everything loading the first time (item 3).
        loading: loading,
        loadingLabel: l10n.workLoadingLabel,
        // Pull to refresh draws the brand's portal (design standard §10).
        body: list,
        bottom: bottom,
      );
    }
    // Expanded and wider (C37): the list at the start, the selected
    // conversation beside it instead of a pushed page.
    return KitScreen.twoPane(
      listPaneKey: const ValueKey('work-list-pane'),
      detailPaneKey: const ValueKey('work-detail-pane'),
      loading: loading,
      loadingLabel: l10n.workLoadingLabel,
      list: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ...header,
          Expanded(child: list),
        ],
      ),
      detail: detailID == null
          ? null
          : KeyedSubtree(
              // A new landing (a waiting request, a menu pick) opens the
              // conversation afresh; a plain reopen keeps it.
              key: ValueKey(
                'work-detail-$detailID'
                '${_detailAction != null || _detailRequestID != null ? '-$_detailOpen' : ''}',
              ),
              child: chatLandingPage(
                sessionID: detailID,
                landOnRequestID: _detailRequestID,
                menuAction: _detailAction,
              ),
            ),
      emptyDetail: KitStateView(
        key: const ValueKey('work-detail-empty'),
        icon: AppIconography.chat,
        liveRegion: false,
        title: l10n.workspaceDetailEmptyTitle,
        body: l10n.workspaceDetailEmptyBody,
      ),
      bottom: bottom,
    );
  }

  String? _serverName(ConnectionController controller) =>
      controller.profile == null
      ? null
      : serverDisplayName(
          controller.profile,
          _l10n(context),
          among: controller.store.profiles,
        );

  Widget _chooser(ServerCapabilities capabilities) {
    final controller = widget.controller;
    return _WorkspaceFolderChooser(
      notice: controller.locationNotice,
      // The name the pill and Servers show ("This phone · Termux"), not
      // the one setup saved the profile under.
      server: _serverName(controller),
      projectError: _projectError,
      canCreate: ProjectFolderActions.canCreate(controller),
      onCreate: _createProjectFolder,
      onOpen: _openProjectFolder,
      onBrowse: _openProjects,
      onSearchAll: capabilities.globalSessionSearch ? _openAllSessions : null,
      onRetry: _load,
    );
  }

  /// The project's name for [directory]: the catalog's when it lists the
  /// project, else the folder's own name.
  String _projectName(String directory) {
    final projects = _projects;
    final project = projects == null
        ? null
        : WorkspaceScreen.projectForDirectory(projects, directory);
    return project?.name ?? _WorkspaceScreenState._basename(directory);
  }

  /// Placeholder rows while the list loads the first time. Once the server
  /// has not answered for [notAnsweringGrace] (the status line above says
  /// so, with its way out), they give way to the unplugged drawing: rows
  /// that never fill in would say "loading" forever.
  Widget _firstLoadRows(AppLocalizations l10n) {
    final controller = widget.controller;
    final lost = WorkStatusLine.serverLost(controller);
    return GraceTimer(
      waiting: lost,
      restartKey: controller.connectionAttemptRevision,
      builder: (context, overdue) {
        final failed =
            controller.status == StreamStatus.disconnected &&
            controller.connectionError != null &&
            !controller.manualReconnectInProgress;
        // What this list held last time stands in for the skeletons, so
        // the tab opens with the person's own titles (codex-speed contract
        // item 1); a different project or server has no such rows.
        final lastKnown = _lastKnown;
        if (!lost || !(overdue || failed)) {
          return lastKnown == null
              ? const KitSkeletonRows()
              : LastKnownSessions(preview: lastKnown);
        }
        final notAnswering = KitStateView(
          key: const ValueKey('work-not-answering-list'),
          size: KitStateSize.inline,
          liveRegion: false,
          icon: AppIconography.cloudOff,
          illustration: const StatesUnpluggedScene(),
          title: l10n.workNotAnsweringListTitle,
          body: l10n.workNotAnsweringListBody,
        );
        if (lastKnown == null) return notAnswering;
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            notAnswering,
            LastKnownSessions(preview: lastKnown, refreshing: !failed),
          ],
        );
      },
    );
  }

  /// The titles this list showed last time for this exact server and
  /// project, or null (none saved, or nothing in them).
  SessionInventoryPreview? get _lastKnown {
    final cached = widget.controller.cachedSessionInventory;
    return cached != null && cached.sessions.isNotEmpty ? cached : null;
  }
}

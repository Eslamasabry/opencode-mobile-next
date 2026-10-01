part of '../workspace_screen.dart';

/// What the context sheet was dismissed with: switch project, start a new
/// one, or move to a workspace (`null` meaning the project's own local
/// checkout).
class _ContextChoice {
  const _ContextChoice.switchProject()
    : workspace = null,
      switchProject = true,
      newProject = false;
  const _ContextChoice.newProject()
    : workspace = null,
      switchProject = false,
      newProject = true;
  const _ContextChoice.workspace(this.workspace)
    : switchProject = false,
      newProject = false;

  final WorkspaceInfo? workspace;
  final bool switchProject;
  final bool newProject;
}

/// A section's count, in figures that line up.
class _SessionRow extends StatelessWidget {
  final ConnectionController controller;
  final Session session;
  final bool busy;

  /// What the conversation is doing, from the one row-status source
  /// (slice-P5.5); null before the first live observation. A status that is
  /// not fresh (the connection dropped) is the last seen state: it rests
  /// still and says when.
  final WorkRowStatus? status;

  /// The clock a fresh status line is read against.
  final DateTime now;

  /// What the run is blocked on (permission, question, form), already
  /// worded for the row; null when nothing is waiting.
  final String? blocker;

  /// The finished, not yet reviewed run this row stands for: it carries the
  /// Unreviewed mark, and its menu offers Review results and Mark as
  /// reviewed.
  final ReturnBriefRun? unreviewed;

  /// The row open in the detail pane (expanded and wider).
  final bool selected;

  final ValueChanged<Session> onOpen;

  /// The conversation menu's picks ([sessionMenuItems], the same menu as
  /// the chat's title bar, P10.2).
  final void Function(Session, SessionMenuAction) onMenuAction;
  final ValueChanged<Session> onReview;
  final ValueChanged<Session> onMarkReviewed;
  final ValueChanged<Session> onPin;
  final ValueChanged<Session> onDelete;

  /// The row's one swipe, and its twin in the menu; null where this server
  /// cannot archive. Delete is never on a swipe (KIT-29): it confirms.
  final KitSwipeAction? archive;

  const _SessionRow({
    required this.controller,
    required this.session,
    required this.busy,
    required this.now,
    this.status,
    this.blocker,
    this.unreviewed,
    this.selected = false,
    required this.onOpen,
    required this.onMenuAction,
    required this.onReview,
    required this.onMarkReviewed,
    required this.onPin,
    required this.onDelete,
    this.archive,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final updated = session.time?.updated ?? session.time?.created;
    final l10n = _l10n(context);
    final pinned = controller.isSessionPinned(session.id);
    final needsAttention = blocker != null;
    final isUnreviewed = !needsAttention && !busy && unreviewed != null;
    final rowStatus = this.status;
    final phase = rowStatus?.facts.phase;
    // Last seen working: the connection dropped while it ran. It rests
    // still and says as of when — never a live mark while disconnected.
    final stale =
        (rowStatus != null && !rowStatus.isFresh) || !controller.isConnected;
    final lastSeen =
        !needsAttention && stale && (busy || phase == WorkRowPhase.working);
    final failed =
        !needsAttention && !busy && !lastSeen && phase == WorkRowPhase.failed;
    // The facts line wraps instead of cutting: one ellipsized line lost the
    // time and diff at 390dp and even "Working" at 320dp/2.5x. The status
    // comes first, so whatever is cut is the least essential.
    final largeText = MediaQuery.textScalerOf(context).scale(1) > 1.25;
    final shared = session.shareUrl != null;
    // A leading mark of one width for every state, so titles line up.
    Widget lead(Widget child) => SizedBox.square(
      dimension: tokens.iconTileSize,
      child: Center(child: child),
    );
    final leading = needsAttention
        ? lead(
            KitNeedsYou.mark(
              key: ValueKey('session-attention-icon-${session.id}'),
            ),
          )
        : lastSeen
        ? lead(
            KitTaskMark(
              key: ValueKey('session-last-seen-mark-${session.id}'),
              state: KitTaskState.waiting,
            ),
          )
        : busy
        ? lead(
            const KitTaskMark(
              key: ValueKey('session-busy-dot'),
              state: KitTaskState.working,
            ),
          )
        : failed
        ? lead(
            KitTaskMark(
              key: ValueKey('session-failed-mark-${session.id}'),
              state: KitTaskState.failed,
            ),
          )
        : KitRow.icon(
            context,
            pinned ? AppIconography.pin : AppIconography.chat,
          );
    // "as of" in the person's own clock (KitTime, F15).
    String moment(DateTime at) => KitTime.moment(context, at, now: now);
    // The blocker outranks "Working": a run waiting on an answer is not
    // making progress.
    final status = needsAttention
        ? null
        : session.compactingSince != null && !lastSeen
        ? l10n.e7WorkspaceCompacting
        : lastSeen
        ? (rowStatus != null &&
                  !rowStatus.isFresh &&
                  phase == WorkRowPhase.working
              ? rowStatus.line(l10n, now: now, moment: moment)
              : l10n.activityLastSeenRunning)
        : busy
        ? (rowStatus?.facts.phase == WorkRowPhase.working
              ? rowStatus!.line(l10n, now: now, moment: moment)
              : l10n.globalSessionsWorking)
        : failed
        ? rowStatus!.line(l10n, now: now, moment: moment)
        : isUnreviewed
        ? l10n.workUnreviewed
        : null;
    final rest = [
      ?blocker,
      ?status,
      if (updated != null) relativeTimeLabel(updated, l10n: l10n),
      // The folder only earns its place when it differs from the open
      // project, e.g. a worktree; otherwise every row would repeat the
      // header.
      if (session.directory?.isNotEmpty == true &&
          !ConnectionController.sameDirectoryPath(
            session.directory,
            controller.directory,
          ))
        _basename(session.directory!),
    ];
    // The state word leads (STATE-9): "Needs you · Permission needed",
    // "Working", "Unreviewed" at label weight, then the muted facts.
    final supporting = TextSpan(
      children: [
        if (needsAttention) KitNeedsYou.span(context),
        if (rest.isNotEmpty && (needsAttention || status != null))
          TextSpan(
            text: rest.first,
            style: KitText.styleOf(
              context,
              KitTextRole.label,
              tone: KitTextTone.primary,
            ),
          ),
        if (rest.isNotEmpty)
          TextSpan(
            text: (needsAttention || status != null)
                ? rest.skip(1).map((fact) => ' · $fact').join()
                : rest.join(' · '),
          ),
      ],
    );

    return KitRow(
      // A kit row (design standard §6) whose title and facts may wrap: a
      // conversation title is the person's own words.
      key: ValueKey('session-row-${session.id}'),
      titleMaxLines: 2,
      supportingMaxLines: largeText ? 3 : 2,
      supportingKey: ValueKey('session-subtitle-${session.id}'),
      leading: leading,
      title: presentedSessionTitle(
        session,
        fallback: l10n.globalSessionsUntitled,
        l10n: l10n,
      ),
      supporting: rest.isEmpty && !needsAttention ? null : supporting,
      selected: selected,
      onTap: () => onOpen(session),
      swipe: archive,
      menuLabel: l10n.globalSessionsActions,
      // Every rare act on long-press, right-click, Shift+F10 and as a
      // semantic action (KIT-28); Archive joins from the swipe, Delete is
      // last and confirms.
      menu: [
        if (isUnreviewed) ...[
          KitMenuItem(
            key: ValueKey('session-review-${session.id}'),
            label: l10n.returnBriefReview,
            icon: AppIconography.guide,
            group: 'review',
            onSelected: () => onReview(session),
          ),
          KitMenuItem(
            key: ValueKey('session-mark-reviewed-${session.id}'),
            label: l10n.workMarkReviewed,
            icon: AppIconography.check,
            group: 'review',
            onSelected: () => onMarkReviewed(session),
          ),
        ],
        KitMenuItem(
          key: const ValueKey('session-menu-open'),
          label: l10n.globalSessionsOpen,
          icon: AppIconography.externalLink,
          onSelected: () => onOpen(session),
        ),
        // The conversation menu, the same as the chat's title bar (P10.2):
        // "Go to" and "Do".
        ...sessionMenuItems(
          l10n,
          SessionMenuOffer.of(
            controller.capabilities,
            shared: shared,
            savedServer: controller.profile != null,
          ),
          explain: false,
          onSelected: (action) => onMenuAction(session, action),
        ),
        if (controller.canPinSessions)
          KitMenuItem(
            key: const ValueKey('session-menu-pin'),
            label: pinned ? l10n.sessionUnpin : l10n.sessionPin,
            icon: AppIconography.pin,
            group: 'row',
            onSelected: () => onPin(session),
          ),
        KitMenuItem(
          key: const ValueKey('session-menu-delete'),
          label: l10n.promptStashDelete,
          icon: AppIconography.delete,
          destructive: true,
          onSelected: () => onDelete(session),
        ),
      ],
    );
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }
}

/// One project entry opens the folder, workspace and management details.
class _ProjectHeader extends StatelessWidget {
  const _ProjectHeader({super.key, required this.name, required this.onTap});
  final String name;
  final VoidCallback onTap;

  /// The roles the name may use, largest first: a long single word steps
  /// down instead of breaking mid-word.
  static const _nameRungs = [
    KitTextRole.largeTitle,
    KitTextRole.title,
    KitTextRole.headline,
  ];

  /// Segments a line breaker will not split: runs of non-space, non-hyphen
  /// characters, keeping a trailing hyphen with the run it ends.
  static final _segment = RegExp(r'[^\s\-]+-?');

  static KitTextRole nameRole(
    BuildContext context,
    String name, {
    required double maxWidth,
  }) {
    final scaler = MediaQuery.textScalerOf(context);
    final direction = Directionality.of(context);
    final segments = _segment
        .allMatches(name)
        .map((match) => match.group(0)!)
        .toList();
    final painter = TextPainter(textDirection: direction, textScaler: scaler);
    try {
      for (final role in _nameRungs) {
        final style = KitText.styleOf(context, role);
        var widest = 0.0;
        for (final segment in segments) {
          painter
            ..text = TextSpan(text: segment, style: style)
            ..layout();
          if (painter.width > widest) widest = painter.width;
        }
        if (widest <= maxWidth) return role;
      }
    } finally {
      painter.dispose();
    }
    return _nameRungs.last;
  }

  @override
  Widget build(BuildContext context) {
    final tokens = KitTokens.of(context);
    final glyph = KitIconSize.large.logical;
    return LayoutBuilder(
      builder: (context, constraints) => KitTappable(
        tappableKey: const ValueKey('current-project-context'),
        tooltip: _l10n(context).workspaceManageProjectHint,
        surface: KitSurfaceLevel.ground,
        shape: KitShape.square,
        onTap: onTap,
        child: Padding(
          padding: EdgeInsetsDirectional.all(tokens.gutter),
          child: Row(
            children: [
              Flexible(
                child: KitText(
                  name,
                  key: const ValueKey('current-project-name'),
                  role: nameRole(
                    context,
                    name,
                    maxWidth:
                        constraints.maxWidth -
                        tokens.gutter * 2 -
                        tokens.space2 -
                        glyph,
                  ),
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              SizedBox(width: tokens.space2),
              const KitIcon(
                AppIconography.chevronDown,
                tone: KitTextTone.secondary,
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// New conversation, docked to the workspace: the one primary. How to
/// start (Solo · Team · a separate copy · a cloud machine) is asked by its
/// chooser, never by controls around the button.
class _QuickAskPill extends StatelessWidget {
  const _QuickAskPill({required this.creating, required this.onTap});

  final bool creating;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    // Two lines before an ellipsis: the primary action's name is never the
    // thing cut.
    // Clear space above the dock and below the last row: a full step
    // rather than the block's own one.
    return KeyedSubtree(
      key: const ValueKey('workspace-quick-ask'),
      child: Padding(
        padding: EdgeInsetsDirectional.symmetric(
          vertical: KitTokens.of(context).space2,
        ),
        child: KitButton.primary(
          key: const ValueKey('workspace-new'),
          onPressed: onTap,
          working: creating,
          icon: AppIconography.add,
          label: l10n.workspaceNewSession,
        ),
      ),
    );
  }
}

/// Compact usage labels for a session row: cost to the cent, and the diff
/// summary as "+120 −34 · 6 files". Empty when the server sent neither.
List<String> sessionUsageLabels(Session session, {AppLocalizations? l10n}) {
  final labels = <String>[];
  final cost = session.cost;
  if (cost != null && cost >= 0.005) labels.add('\$${cost.toStringAsFixed(2)}');
  final summary = session.summary;
  if (summary != null && (summary.additions > 0 || summary.deletions > 0)) {
    labels.add('+${summary.additions} −${summary.deletions}');
    if (summary.files > 0) {
      labels.add(
        l10n?.e7WorkspaceFileCount(summary.files) ??
            '${summary.files} ${summary.files == 1 ? 'file' : 'files'}',
      );
    }
  }
  return labels;
}

/// Blocking state shown while the connection has no usable project folder
/// (map `workspace-folder-chooser`, proposal "fix"). It replaces the session
/// list and the quick-ask pill: nothing can run in the server's home
/// folder, so the only ways forward are creating a folder (managed server)
/// or opening an existing one. A project list that failed to load says so
/// in its title and offers Try again first.
class _WorkspaceFolderChooser extends StatelessWidget {
  const _WorkspaceFolderChooser({
    required this.notice,
    required this.server,
    required this.projectError,
    required this.canCreate,
    required this.onCreate,
    required this.onOpen,
    required this.onBrowse,
    required this.onSearchAll,
    required this.onRetry,
  });

  final String? notice;

  /// The saved server's name, for the one line under the title.
  final String? server;
  final String? projectError;
  final bool canCreate;
  final VoidCallback onCreate;
  final VoidCallback onOpen;
  final VoidCallback onBrowse;
  final VoidCallback? onSearchAll;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final l10n = _l10n(context);
    final create = KitAction(
      key: const ValueKey('workspace-create-folder'),
      icon: AppIconography.folderAdd,
      label: l10n.projectFolderCreate,
      onPressed: onCreate,
    );
    final open = KitAction(
      key: const ValueKey('workspace-open-folder'),
      icon: AppIconography.folderOpen,
      label: l10n.workspaceChooserEnterPath,
      onPressed: onOpen,
    );
    final browse = KitAction(
      key: const ValueKey('workspace-browse-projects'),
      label: l10n.workspaceChooserRecentProjects,
      onPressed: onBrowse,
    );
    final searchAll = onSearchAll == null
        ? null
        : KitAction(
            key: const ValueKey('workspace-chooser-search-all'),
            label: l10n.workspaceSearchAllSessions,
            onPressed: onSearchAll!,
          );
    final error = projectError;
    if (error != null) {
      return KitStateView(
        key: const ValueKey('workspace-folder-chooser'),
        icon: AppIconography.warning,
        tone: AppStatusTone.failure,
        // The project list that could not load is the unplugged drawing,
        // like every other load failure.
        illustration: const StatesUnpluggedScene(),
        titleKey: const ValueKey('workspace-chooser-error-title'),
        title: l10n.workspaceChooserLoadFailedTitle,
        body: notice ?? l10n.workspaceChooserLoadFailedBody,
        bodyKey: notice == null
            ? null
            : const ValueKey('location-recovery-notice'),
        primary: KitAction(
          key: const ValueKey('workspace-chooser-retry'),
          label: l10n.workspaceRetryProjects,
          onPressed: onRetry,
        ),
        secondary: canCreate ? create : open,
        tertiary: [if (canCreate) open, browse, ?searchAll],
        detailNotes: [if (!canCreate) l10n.projectFolderNoCreateHint],
        details: error,
      );
    }
    return KitStateView(
      key: const ValueKey('workspace-folder-chooser'),
      icon: AppIconography.folders,
      // A place with nothing in it yet.
      illustration: const StatesFolderScene(),
      title: l10n.projectFolderChooserTitle,
      // Why a folder, not the title again (slice-P3.11a).
      body:
          notice ??
          (server?.trim().isNotEmpty == true
              ? l10n.workspaceChooserBody(KitBidi.auto(server!.trim()))
              : l10n.e7WorkspaceChooseFolderToStart),
      bodyKey: notice == null
          ? null
          : const ValueKey('location-recovery-notice'),
      primary: canCreate ? create : open,
      secondary: canCreate ? open : null,
      tertiary: [browse, ?searchAll],
      // Why there is no Create here is the technical part: under Details,
      // below the actions.
      detailNotes: [if (!canCreate) l10n.projectFolderNoCreateHint],
    );
  }
}

AppLocalizations _l10n(BuildContext context) =>
    Localizations.of<AppLocalizations>(context, AppLocalizations) ??
    lookupAppLocalizations(Localizations.localeOf(context));

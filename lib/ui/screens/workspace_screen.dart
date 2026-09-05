import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../l10n/app_localizations.dart';
import '../../platform/platform_capabilities.dart';
import '../../state/connection.dart';
import '../desktop/context_menu.dart';
import '../desktop/desktop_interaction.dart';
import '../navigation/chat_route.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/entrance.dart';
import '../widgets/product_states.dart';
import '../widgets/session_title.dart';
import '../widgets/session_inventory_footer.dart';
import 'global_sessions_screen.dart';
import 'manage_project_screen.dart';
import 'projects_screen.dart';
import 'settings_screen.dart';
import 'terminal_screen.dart';
import '../app_theme.dart';

class WorkspaceScreen extends StatefulWidget {
  final ConnectionController controller;
  const WorkspaceScreen({super.key, required this.controller});

  @override
  State<WorkspaceScreen> createState() => _WorkspaceScreenState();
}

class _WorkspaceScreenState extends State<WorkspaceScreen> {
  List<WorkspaceProject>? _projects;
  List<WorkspaceInfo> _workspaces = const [];
  String? _projectError;
  String? _workspaceError;
  String? _selectedProjectID;
  String? _selectedWorkspaceID;
  String? _selectedDirectory;
  bool _creating = false;
  int _loadGeneration = 0;
  int _dataRefreshRevision = 0;
  final Set<String> _pendingArchive = {};

  @override
  void initState() {
    super.initState();
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    widget.controller.addListener(_changed);
    _load();
  }

  void _changed() {
    if (!mounted) return;
    final shouldReload =
        _dataRefreshRevision != widget.controller.dataRefreshRevision &&
        widget.controller.repository != null;
    _dataRefreshRevision = widget.controller.dataRefreshRevision;
    setState(() {});
    if (shouldReload) unawaited(_load());
  }

  Future<void> _refreshWorkspace() async {
    await _load();
    if (!mounted) return;
    await widget.controller.refreshSessions();
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() => _projectError = 'The server is not connected.');
      return;
    }
    setState(() {
      _projectError = null;
      _workspaceError = null;
    });
    try {
      final projects = await repository.listProjects();
      if (!mounted || generation != _loadGeneration) return;
      // A location restored before the project list was available is only
      // provisional; confirm it against the real list now rather than on the
      // next location change.
      await widget.controller.revalidateRestoredLocation();
      if (!mounted || generation != _loadGeneration) return;
      projects.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      var shouldSelectInitialLocation = false;
      setState(() {
        _projects = projects;
        if (projects.isEmpty) {
          _selectedProjectID = null;
          _selectedDirectory = null;
          _selectedWorkspaceID = null;
          return;
        }
        final controllerDirectory = widget.controller.directory;
        if (controllerDirectory != null) {
          _selectedDirectory = controllerDirectory;
          _selectedWorkspaceID = widget.controller.workspace;
          final matching = _projectForDirectory(projects, controllerDirectory);
          if (matching != null) {
            _selectedProjectID = matching.id;
          } else if (!projects.any(
            (project) => project.id == _selectedProjectID,
          )) {
            _selectedProjectID = projects.first.id;
          }
        } else {
          final retained = projects.where(
            (project) => project.id == _selectedProjectID,
          );
          final selected = retained.isEmpty ? projects.first : retained.first;
          _selectedProjectID = selected.id;
          _selectedDirectory = selected.directory;
          _selectedWorkspaceID = null;
          shouldSelectInitialLocation = true;
        }
      });
      final selected = _selectedProject;
      if (shouldSelectInitialLocation && selected != null) {
        await widget.controller.selectInitialLocation(
          directory: _selectedDirectory ?? selected.directory,
        );
      }
    } catch (error) {
      if (mounted && generation == _loadGeneration) {
        setState(() => _projectError = productErrorText(error));
      }
    }
    if (generation != _loadGeneration) return;
    await _loadWorkspaces();
  }

  Future<void> _loadWorkspaces() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) return;
    try {
      final workspaces = await repository.listWorkspaces();
      if (!mounted) return;
      setState(() {
        _workspaces = workspaces
            .where(
              (workspace) =>
                  _selectedProjectID == null ||
                  workspace.projectID == _selectedProjectID,
            )
            .toList();
      });
    } catch (error) {
      if (mounted) setState(() => _workspaceError = productErrorText(error));
    }
  }

  WorkspaceProject? get _selectedProject {
    for (final project in _projects ?? const <WorkspaceProject>[]) {
      if (project.id == _selectedProjectID) return project;
    }
    return null;
  }

  static WorkspaceProject? _projectForDirectory(
    List<WorkspaceProject> projects,
    String directory,
  ) {
    for (final project in projects) {
      if (project.directory == directory ||
          project.worktrees.contains(directory)) {
        return project;
      }
    }
    return null;
  }

  bool get _hasExternalSessionDirectory {
    final directory = _selectedDirectory;
    final project = _selectedProject;
    if (directory == null || project == null) return false;
    return project.directory != directory &&
        !project.worktrees.contains(directory);
  }

  WorkspaceInfo? get _selectedWorkspace {
    for (final workspace in _workspaces) {
      if (workspace.id == _selectedWorkspaceID) return workspace;
    }
    return null;
  }

  /// The compact context line under the project name: which workspace and
  /// which directory this screen's sessions will run in.
  String get _contextSubtitle {
    final parts = <String>[];
    final workspace = _selectedWorkspace;
    if (workspace != null) {
      parts.add(
        workspace.branch?.isNotEmpty == true
            ? workspace.branch!
            : workspace.name,
      );
    }
    final directory =
        widget.controller.directory ??
        _selectedWorkspace?.directory ??
        _selectedDirectory ??
        _selectedProject?.directory;
    parts.add(
      directory?.isNotEmpty == true ? directory! : 'Server’s default directory',
    );
    return parts.join(' · ');
  }

  Future<void> _selectWorkspace(WorkspaceInfo? workspace) async {
    await widget.controller.selectLocation(
      directory: workspace?.directory ?? _selectedDirectory,
      workspace: workspace?.id,
    );
    if (!mounted) return;
    setState(() {
      _selectedWorkspaceID = widget.controller.workspace;
      _selectedDirectory = widget.controller.directory;
    });
    final error = widget.controller.locationError;
    if (error != null) _showError(error);
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  /// A permission, question, or form is waiting on the session: the row
  /// must say so rather than "Working", since nothing moves until the user
  /// answers.
  bool _needsAttention(String sessionID) =>
      widget.controller.permissionsForSession(sessionID).isNotEmpty ||
      widget.controller.questionForSession(sessionID) != null ||
      widget.controller.formForSession(sessionID) != null;

  Future<void> _createSession() async {
    if (_creating) return;
    setState(() => _creating = true);
    try {
      final session = await widget.controller.createSession();
      if (!mounted) return;
      await Navigator.of(context).pushNamed(
        '/chat/${session.id}',
        arguments: const ChatRouteArguments.newlyCreated(),
      );
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _showError('Could not create a session: $error');
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_projects == null && _projectError == null) {
      return const LoadingList(rows: 6);
    }
    if (_projectError != null && _projects == null) {
      return ProductErrorState(
        message: _projectError!,
        onRetry: _refreshWorkspace,
      );
    }
    if (_projects?.isEmpty == true) {
      // A fresh server has no projects yet, but it can still host a session:
      // with no directory selected the session-create call omits the
      // directory parameter and the server scopes the session to its own
      // default directory. Keep the quick-ask pill so the first prompt is
      // never a dead end.
      return Stack(
        children: [
          ProductEmptyState(
            icon: Icons.folder_off_outlined,
            title: 'No projects opened',
            message:
                'Open a project on this OpenCode server, then refresh here — '
                'or ask below to start a session in the server’s default '
                'directory.',
            actionLabel: 'Refresh',
            onAction: _refreshWorkspace,
          ),
          Positioned(
            left: 12,
            right: 12,
            bottom: 12,
            child: _QuickAskPill(
              creating: _creating,
              onTap: _creating ? null : _createSession,
            ),
          ),
        ],
      );
    }

    // Rows swiped to Archive vanish immediately and come back on Undo; the
    // server call only happens once the snackbar has gone.
    final sessions = widget.controller
        .sortedSessions()
        .where((session) => !_pendingArchive.contains(session.id))
        .toList();
    final active = sessions
        .where((session) => widget.controller.busySessions.contains(session.id))
        .toList();
    final recent = sessions
        .where(
          (session) => !widget.controller.busySessions.contains(session.id),
        )
        .toList();
    final archived = widget.controller.archivedSessions();
    final l10n = lookupAppLocalizations(Localizations.localeOf(context));
    final partial =
        widget.controller.hasMoreSessions || widget.controller.sessionsLoading;

    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: _refreshWorkspace,
          child: DesktopScrollbarArea(
            builder: (scrollController) => CustomScrollView(
              controller: scrollController,
              key: const PageStorageKey('workspace-scroll'),
              physics: const AlwaysScrollableScrollPhysics(),
              slivers: [
                // 1. Current project/workspace context — one compact header.
                SliverToBoxAdapter(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (widget.controller.locationNotice != null)
                        ListTile(
                          key: const ValueKey('location-recovery-notice'),
                          leading: const Icon(Icons.info_outline_rounded),
                          title: Text(widget.controller.locationNotice!),
                        ),
                      ListTile(
                        key: const ValueKey('current-project-entry'),
                        leading: const Icon(Icons.folder_rounded),
                        title: Text(
                          _selectedProject?.name ?? 'Choose a project',
                        ),
                        subtitle: Text(
                          _contextSubtitle,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        trailing: const Icon(Icons.unfold_more_rounded),
                        onTap: _openContextSheet,
                      ),
                      // Still context, not management: the session is running
                      // somewhere other than the project root.
                      if (_hasExternalSessionDirectory)
                        ListTile(
                          key: const ValueKey('active-session-directory'),
                          leading: const Icon(Icons.subdirectory_arrow_right),
                          title: Text(_basename(_selectedDirectory!)),
                          subtitle: Text(
                            'Active session directory · $_selectedDirectory',
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      if (_workspaceError != null)
                        Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                          child: Text(
                            _workspaceError!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                      // 2. Continue active sessions, with their live state.
                      if (active.isNotEmpty)
                        SectionLabel(
                          'Active sessions',
                          trailing: Text('${active.length}'),
                        ),
                    ],
                  ),
                ),
                if (active.isNotEmpty)
                  SliverList.builder(
                    itemCount: active.length,
                    itemBuilder: (context, index) => _SessionRow(
                      session: active[index],
                      busy: true,
                      needsAttention: _needsAttention(active[index].id),
                      onOpen: _openSession,
                      onAction: _sessionAction,
                      sharingAvailable:
                          widget.controller.capabilities.sessionShare,
                      archiveAvailable:
                          widget.controller.capabilities.sessionArchive,
                    ),
                  ),
                // 3. Recent sessions.
                SliverToBoxAdapter(
                  child: SectionLabel(
                    'Recent sessions',
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          key: const ValueKey('search-all-sessions'),
                          tooltip: 'Search all sessions',
                          onPressed: _openAllSessions,
                          icon: const Icon(
                            Icons.manage_search_rounded,
                            size: 21,
                          ),
                        ),
                        IconButton(
                          tooltip: 'Refresh sessions',
                          onPressed: widget.controller.sessionsLoading
                              ? null
                              : widget.controller.refreshSessions,
                          icon: const Icon(Icons.refresh_rounded, size: 19),
                        ),
                        // Terminal gave its navigation slot to Activity; this
                        // keeps it one tap from the workspace it runs in.
                        IconButton(
                          key: const ValueKey('workspace-terminal'),
                          tooltip: 'Terminal',
                          onPressed: _openTerminal,
                          icon: const Icon(Icons.terminal_outlined, size: 20),
                        ),
                        // Whether runs keep updating after the app closes
                        // was only discoverable two levels into Settings;
                        // say it where the runs are.
                        if (platformCapabilities.supportsBackgroundService)
                          IconButton(
                            key: const ValueKey('workspace-background-toggle'),
                            tooltip: widget.controller.keepLiveInBackground
                                ? 'Stays connected in the background'
                                : 'Background updates off',
                            onPressed: _openBackgroundSettings,
                            isSelected: widget.controller.keepLiveInBackground,
                            icon: Icon(
                              widget.controller.keepLiveInBackground
                                  ? Icons.cloud_sync_outlined
                                  : Icons.cloud_off_outlined,
                              size: 20,
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                if (recent.isEmpty)
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 26),
                      child: ProductInlineEmpty(
                        icon: Icons.chat_bubble_outline_rounded,
                        title: partial
                            ? l10n.sessionsNoLoadedRecent
                            : 'No recent sessions',
                        message: partial
                            ? l10n.sessionsLoadedOnly
                            : 'Start a session in the selected workspace.',
                        actionLabel: 'New session',
                        onAction: _createSession,
                      ),
                    ),
                  )
                else
                  SliverList.builder(
                    itemCount: recent.length,
                    itemBuilder: (context, index) => EntranceReveal(
                      index: index,
                      child: _SessionRow(
                        session: recent[index],
                        busy: false,
                        needsAttention: _needsAttention(recent[index].id),
                        onOpen: _openSession,
                        onAction: _sessionAction,
                        sharingAvailable:
                            widget.controller.capabilities.sessionShare,
                        archiveAvailable:
                            widget.controller.capabilities.sessionArchive,
                      ),
                    ),
                  ),
                SliverToBoxAdapter(
                  child: SessionInventoryFooter(controller: widget.controller),
                ),
                if (archived.isNotEmpty || partial)
                  SliverToBoxAdapter(
                    child: ListTile(
                      leading: const Icon(Icons.archive_outlined),
                      title: const Text('Archived sessions'),
                      subtitle: Text(
                        partial
                            ? l10n.sessionsLoadedCount(archived.length)
                            : '${archived.length} hidden from recents',
                      ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _showArchived,
                    ),
                  ),
                // 5. Everything management sits below the sessions, behind one
                // labelled route: worktrees, managed workspaces, project
                // health, and project switching.
                if (ManageProjectScreen.isAvailable(
                  widget.controller.capabilities,
                ))
                  SliverToBoxAdapter(
                    child: ListTile(
                      key: const ValueKey('manage-project-entry'),
                      leading: const Icon(Icons.tune_rounded),
                      title: const Text('Manage project'),
                      subtitle: const Text(
                        'Switch project, worktrees, and project health',
                      ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _openManageProject,
                    ),
                  ),
                const SliverToBoxAdapter(child: SizedBox(height: 96)),
              ],
            ),
          ),
        ),
        // 4. Start a prompt: a docked quick-ask pill opens a fresh session in
        // the active project without scrolling, replacing the New-session FAB.
        Positioned(
          left: 12,
          right: 12,
          bottom: 12,
          child: _QuickAskPill(
            creating: _creating,
            onTap: _creating ? null : _createSession,
          ),
        ),
      ],
    );
  }

  void _openSession(Session session) {
    Navigator.of(context).pushNamed('/chat/${session.id}');
  }

  void _openBackgroundSettings() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BackgroundSettingsScreen(controller: widget.controller),
      ),
    );
  }

  Future<void> _openTerminal() => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => TerminalPage(controller: widget.controller),
    ),
  );

  Future<void> _openAllSessions() => Navigator.of(context).push(
    MaterialPageRoute<void>(
      builder: (_) => GlobalSessionsScreen(controller: widget.controller),
    ),
  );

  Future<void> _openProjects() async {
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => ProjectsScreen(
          controller: widget.controller,
          selectedProjectID: _selectedProjectID,
        ),
      ),
    );
    if (mounted) await _load();
  }

  /// One coherent context sheet (audit UX-P0-02): project switching and
  /// workspace selection live together instead of a project row plus a
  /// separate strip of horizontal workspace chips.
  Future<void> _openContextSheet() async {
    final choice = await showModalBottomSheet<_ContextChoice>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SingleChildScrollView(
          key: const ValueKey('workspace-context-sheet'),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ListTile(
                leading: const Icon(Icons.folder_rounded),
                title: Text(_selectedProject?.name ?? 'No project selected'),
                subtitle: Text(
                  _contextSubtitle,
                  maxLines: 3,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const Divider(height: 1),
              ListTile(
                key: const ValueKey('context-switch-project'),
                leading: const Icon(Icons.swap_horiz_rounded),
                title: const Text('Switch project'),
                subtitle: Text('${_projects?.length ?? 0} open on this server'),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: () => Navigator.of(
                  sheetContext,
                ).pop(const _ContextChoice.switchProject()),
              ),
              if (_workspaces.isNotEmpty) ...[
                const SectionLabel('Workspace'),
                ListTile(
                  key: const ValueKey('workspace-option-local'),
                  leading: const Icon(Icons.computer_rounded),
                  title: const Text('This computer'),
                  trailing: _selectedWorkspaceID == null
                      ? const Icon(Icons.check_rounded)
                      : null,
                  selected: _selectedWorkspaceID == null,
                  onTap: () => Navigator.of(
                    sheetContext,
                  ).pop(const _ContextChoice.workspace(null)),
                ),
                for (final workspace in _workspaces)
                  ListTile(
                    key: ValueKey('workspace-option-${workspace.id}'),
                    leading: const Icon(Icons.cloud_outlined),
                    title: Text(
                      workspace.branch?.isNotEmpty == true
                          ? workspace.branch!
                          : workspace.name,
                    ),
                    subtitle: Text(
                      workspace.directory ?? '',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: workspace.id == _selectedWorkspaceID
                        ? const Icon(Icons.check_rounded)
                        : null,
                    selected: workspace.id == _selectedWorkspaceID,
                    onTap: () => Navigator.of(
                      sheetContext,
                    ).pop(_ContextChoice.workspace(workspace)),
                  ),
              ],
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
    if (!mounted || choice == null) return;
    if (choice.switchProject) {
      await _openProjects();
      return;
    }
    await _selectWorkspace(choice.workspace);
  }

  Future<void> _openManageProject() async {
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => ManageProjectScreen(
          controller: widget.controller,
          project: _selectedProject,
        ),
      ),
    );
    if (mounted) await _load();
  }

  Future<void> _sessionAction(String action, Session session) async {
    try {
      switch (action) {
        case 'rename':
          await _rename(session);
          break;
        case 'share':
          if (!await _confirmShare(session)) return;
          final shareRepository = await _requireActionRepository();
          final url = await shareRepository.shareSession(session.id);
          if (url == null || url.isEmpty) {
            throw const ProductException('No share link was returned.');
          }
          await Clipboard.setData(ClipboardData(text: url));
          if (mounted) _showMessage('Share link copied');
          break;
        case 'unshare':
          final unshareRepository = await _requireActionRepository();
          await unshareRepository.unshareSession(session.id);
          if (mounted) _showMessage('Session is no longer shared');
          break;
        case 'archive':
          if (!await _confirmArchive(session)) return;
          final archiveRepository = await _requireActionRepository();
          await archiveRepository.archiveSession(session.id);
          break;
        case 'swipe-archive':
          await _archiveWithUndo(session);
          return;
        case 'delete':
          if (!await _confirmDelete(session)) return;
          await widget.controller.deleteSession(session.id);
          break;
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _showError(error);
    }
  }

  /// Swipe-to-archive: hide the row at once, offer Undo for the snackbar's
  /// lifetime, and only then tell the server. Archiving has no server-side
  /// reverse, so the undo window *is* the safety net.
  Future<void> _archiveWithUndo(Session session) async {
    if (_pendingArchive.contains(session.id)) return;
    setState(() => _pendingArchive.add(session.id));
    final title = session.title?.isNotEmpty == true
        ? session.title!
        : 'Untitled session';
    final messenger = ScaffoldMessenger.of(context);
    messenger.hideCurrentSnackBar();
    final snackBar = messenger.showSnackBar(
      SnackBar(
        key: ValueKey('archive-undo-${session.id}'),
        content: Text('Archived “$title”'),
        duration: const Duration(seconds: 5),
        // Material keeps a snackbar with an action open until it is acted
        // on; here the timeout *is* the commit, so it must run out.
        persist: false,
        action: SnackBarAction(
          label: 'Undo',
          onPressed: () => messenger.hideCurrentSnackBar(
            reason: SnackBarClosedReason.action,
          ),
        ),
      ),
    );
    final reason = await snackBar.closed;
    if (!mounted) return;
    if (reason == SnackBarClosedReason.action) {
      setState(() => _pendingArchive.remove(session.id));
      return;
    }
    try {
      final archiveRepository = await _requireActionRepository();
      await archiveRepository.archiveSession(session.id);
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _showError(error);
    } finally {
      if (mounted) setState(() => _pendingArchive.remove(session.id));
    }
  }

  Future<ServerOperationsGateway> _requireActionRepository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw const ProductException(
      'OpenCode is reconnecting. Try again shortly.',
    );
  }

  Future<void> _rename(Session session) async {
    final controller = TextEditingController(text: session.title ?? '');
    final title = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Rename session'),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: const InputDecoration(labelText: 'Title'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, controller.text.trim()),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    controller.dispose();
    if (title?.isNotEmpty == true) {
      await widget.controller.renameSession(session.id, title!);
    }
  }

  Future<bool> _confirmArchive(Session session) => showConfirmSheet(
    context,
    icon: Icons.archive_outlined,
    title: 'Archive session?',
    message:
        '“${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}” will be hidden from recent sessions.',
    confirmLabel: 'Archive',
  );

  Future<bool> _confirmShare(Session session) => showConfirmSheet(
    context,
    icon: Icons.public_rounded,
    title: 'Share this session?',
    message:
        'Anyone with the link can view “${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}”, '
        'including its conversation and shared context. Do not share secrets, credentials, or private files.',
    confirmLabel: 'Share session',
  );

  Future<bool> _confirmDelete(Session session) => showConfirmSheet(
    context,
    icon: Icons.delete_outline_rounded,
    title: 'Delete session?',
    message:
        '“${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}” and its history will be permanently removed.',
    confirmLabel: 'Delete',
    destructive: true,
  );

  /// The archived list. The server gateway only exposes `archiveSession`
  /// (it stamps `time.archived`; the SDK drops a null timestamp), so there is
  /// no unarchive to offer. Rows still lead somewhere: open, or delete for
  /// good, through the same confirm flow as the recent list.
  void _showArchived() {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => ListenableBuilder(
        listenable: widget.controller,
        builder: (sheetContext, _) {
          final sessions = widget.controller.archivedSessions();
          final theme = Theme.of(sheetContext);
          return SafeArea(
            child: ListView.builder(
              shrinkWrap: true,
              itemCount: sessions.length + 1,
              itemBuilder: (context, index) {
                if (index == sessions.length) {
                  return Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (sessions.isEmpty)
                        Padding(
                          padding: const EdgeInsets.all(16),
                          child: Text(
                            lookupAppLocalizations(
                              Localizations.localeOf(context),
                            ).sessionsNoLoadedArchived,
                          ),
                        ),
                      SessionInventoryFooter(controller: widget.controller),
                    ],
                  );
                }
                final session = sessions[index];
                final title = session.title?.isNotEmpty == true
                    ? session.title!
                    : 'Untitled session';
                void run(String action) {
                  Navigator.pop(sheetContext);
                  unawaited(_sessionAction(action, session));
                }

                return ContextMenuRegion(
                  actions: () => [
                    ContextMenuAction(
                      label: 'Open',
                      icon: Icons.open_in_new_rounded,
                      onSelected: () {
                        Navigator.pop(sheetContext);
                        _openSession(session);
                      },
                    ),
                    ContextMenuAction(
                      label: 'Delete',
                      icon: Icons.delete_outline_rounded,
                      destructive: true,
                      onSelected: () => run('delete'),
                    ),
                  ],
                  child: ListTile(
                    key: ValueKey('archived-session-${session.id}'),
                    leading: const Icon(Icons.inventory_2_outlined, size: 21),
                    title: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    subtitle: Text(
                      session.directory ?? '',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: PopupMenuButton<String>(
                      key: ValueKey('archived-session-actions-${session.id}'),
                      tooltip: 'Archived session actions',
                      onSelected: run,
                      itemBuilder: (_) => [
                        const PopupMenuItem(
                          value: 'rename',
                          child: Text('Rename'),
                        ),
                        PopupMenuItem(
                          value: 'delete',
                          child: Text(
                            'Delete',
                            style: TextStyle(color: theme.colorScheme.error),
                          ),
                        ),
                      ],
                    ),
                    onTap: () {
                      Navigator.pop(sheetContext);
                      _openSession(session);
                    },
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _showError(Object error) => showProductError(context, error);

  @override
  void dispose() {
    _loadGeneration++;
    widget.controller.removeListener(_changed);
    super.dispose();
  }
}

/// What the context sheet was dismissed with: switch project, or move to a
/// workspace (`null` meaning the project's own local checkout).
class _ContextChoice {
  const _ContextChoice.switchProject() : workspace = null, switchProject = true;
  const _ContextChoice.workspace(this.workspace) : switchProject = false;

  final WorkspaceInfo? workspace;
  final bool switchProject;
}

class _SessionRow extends StatelessWidget {
  final Session session;
  final bool busy;

  /// The run is blocked on a permission, question, or form.
  final bool needsAttention;
  final ValueChanged<Session> onOpen;
  final Future<void> Function(String, Session) onAction;

  /// §7 rows 10–12: menus list possible actions only.
  final bool sharingAvailable;
  final bool archiveAvailable;

  const _SessionRow({
    required this.session,
    required this.busy,
    this.needsAttention = false,
    required this.onOpen,
    required this.onAction,
    this.sharingAvailable = true,
    this.archiveAvailable = true,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final updated = session.time?.updated ?? session.time?.created;
    final row = Dismissible(
      key: ValueKey('session-dismiss-${session.id}'),
      direction: DismissDirection.endToStart,
      // Swipe archives (with Undo) wherever the server can archive; delete
      // stays behind the menu's confirm. Either way this resolves false: the
      // parent removes or refreshes the row, so a cancel simply snaps back.
      confirmDismiss: (_) async {
        await onAction(archiveAvailable ? 'swipe-archive' : 'delete', session);
        return false;
      },
      background: archiveAvailable
          ? const _SwipeArchiveBackground()
          : const SwipeDeleteBackground(),
      child: ListTile(
        minTileHeight: 64,
        leading: needsAttention
            ? Icon(
                key: ValueKey('session-attention-icon-${session.id}'),
                Icons.notification_important_outlined,
                size: 21,
                color: AppTheme.statusColor(theme, AppStatusTone.attention),
              )
            : busy
            ? const _BreathingDot()
            : const Icon(Icons.chat_bubble_outline_rounded, size: 21),
        title: Text(
          presentedSessionTitle(session, fallback: 'Untitled session'),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        subtitle: _SessionRowSubtitle(
          // "Needs you" outranks "Working": a run waiting on an answer is
          // not making progress, and the colour says so.
          status: needsAttention
              ? 'Needs you'
              : session.compactingSince != null
              ? 'Compacting…'
              : busy
              ? 'Working'
              : null,
          statusColor: needsAttention
              ? AppTheme.statusColor(theme, AppStatusTone.attention)
              : null,
          rest: [
            if (session.shareUrl != null) 'Shared: ${session.shareUrl}',
            if (updated != null) _relativeTime(updated),
            if (session.directory?.isNotEmpty == true)
              _basename(session.directory!),
            // Server-reported usage, when the server sends it: what the run
            // cost and how much it touched, so a row answers "was that
            // worth it?" without opening the session.
            ...sessionUsageLabels(session),
          ],
        ),
        trailing: PopupMenuButton<String>(
          tooltip: 'Session actions',
          onSelected: (value) => onAction(value, session),
          itemBuilder: (context) => [
            const PopupMenuItem(value: 'rename', child: Text('Rename')),
            if (sharingAvailable)
              PopupMenuItem(
                value: session.shareUrl == null ? 'share' : 'unshare',
                child: Text(
                  session.shareUrl == null ? 'Share' : 'Stop sharing',
                ),
              ),
            if (archiveAvailable)
              const PopupMenuItem(value: 'archive', child: Text('Archive')),
            PopupMenuItem(
              value: 'delete',
              child: Text(
                'Delete',
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ),
          ],
        ),
        onTap: () => onOpen(session),
      ),
    );
    // The same entries the trailing overflow menu lists, on the button a
    // mouse user actually reaches for. A pass-through off desktop.
    return ContextMenuRegion(
      actions: () => [
        ContextMenuAction(
          menuKey: const ValueKey('session-menu-open'),
          label: 'Open',
          icon: Icons.open_in_new_rounded,
          onSelected: () => onOpen(session),
        ),
        ContextMenuAction(
          menuKey: const ValueKey('session-menu-rename'),
          label: 'Rename',
          icon: Icons.edit_outlined,
          onSelected: () => unawaited(onAction('rename', session)),
        ),
        if (sharingAvailable)
          ContextMenuAction(
            menuKey: const ValueKey('session-menu-share'),
            label: session.shareUrl == null ? 'Share' : 'Stop sharing',
            icon: Icons.public_rounded,
            onSelected: () => unawaited(
              onAction(session.shareUrl == null ? 'share' : 'unshare', session),
            ),
          ),
        if (archiveAvailable)
          ContextMenuAction(
            menuKey: const ValueKey('session-menu-archive'),
            label: 'Archive',
            icon: Icons.archive_outlined,
            onSelected: () => unawaited(onAction('archive', session)),
          ),
        ContextMenuAction(
          menuKey: const ValueKey('session-menu-delete'),
          label: 'Delete',
          icon: Icons.delete_outline_rounded,
          destructive: true,
          onSelected: () => unawaited(onAction('delete', session)),
        ),
      ],
      child: row,
    );
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

  static String _relativeTime(int milliseconds) {
    final difference = DateTime.now().difference(
      DateTime.fromMillisecondsSinceEpoch(milliseconds),
    );
    if (difference.inMinutes < 1) return 'Just now';
    if (difference.inHours < 1) return '${difference.inMinutes}m ago';
    if (difference.inDays < 1) return '${difference.inHours}h ago';
    if (difference.inDays < 7) return '${difference.inDays}d ago';
    final date = DateTime.fromMillisecondsSinceEpoch(milliseconds);
    return '${date.year}-${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';
  }
}

/// End-swipe reveal for the archive gesture: a calm container, not the
/// destructive field the delete swipe uses elsewhere.
/// The one-line row subtitle: an optional status word first (tinted when it
/// asks for attention), then the usual dot-separated facts.
class _SessionRowSubtitle extends StatelessWidget {
  const _SessionRowSubtitle({
    required this.status,
    required this.statusColor,
    required this.rest,
  });

  final String? status;
  final Color? statusColor;
  final List<String> rest;

  @override
  Widget build(BuildContext context) {
    // No explicit style: the ListTile's subtitle DefaultTextStyle applies,
    // so the row keeps the exact typography it had as a plain Text.
    final tail = rest.join(' · ');
    return Text.rich(
      TextSpan(
        children: [
          if (status case final status?)
            TextSpan(
              text: status,
              style: statusColor == null
                  ? null
                  : TextStyle(color: statusColor, fontWeight: FontWeight.w600),
            ),
          if (status != null && tail.isNotEmpty) const TextSpan(text: ' · '),
          if (tail.isNotEmpty) TextSpan(text: tail),
        ],
      ),
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
  }
}

class _SwipeArchiveBackground extends StatelessWidget {
  const _SwipeArchiveBackground();

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      color: scheme.secondaryContainer,
      alignment: AlignmentDirectional.centerEnd,
      padding: const EdgeInsetsDirectional.only(end: 24),
      child: Icon(Icons.archive_outlined, color: scheme.onSecondaryContainer),
    );
  }
}

/// The busy marker on a session row: a primary dot that breathes slowly
/// instead of a spinner, because "working" is a state, not a wait. Holds
/// still when the platform asks for reduced motion.
class _BreathingDot extends StatefulWidget {
  const _BreathingDot();

  static const period = Duration(milliseconds: 1600);

  @override
  State<_BreathingDot> createState() => _BreathingDotState();
}

class _BreathingDotState extends State<_BreathingDot>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: _BreathingDot.period,
  );
  late final Animation<double> _opacity = Tween<double>(
    begin: .4,
    end: 1,
  ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (MediaQuery.disableAnimationsOf(context)) {
      _controller
        ..stop()
        ..value = 1;
    } else if (!_controller.isAnimating) {
      _controller.repeat(reverse: true);
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return Semantics(
      label: 'Working',
      child: SizedBox.square(
        dimension: 22,
        child: Center(
          child: RepaintBoundary(
            child: FadeTransition(
              opacity: _opacity,
              child: Container(
                key: const ValueKey('session-busy-dot'),
                width: 10,
                height: 10,
                decoration: BoxDecoration(color: color, shape: BoxShape.circle),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A composer-shaped invitation docked to the workspace: it looks like the
/// chat input and opens a fresh session in the active project ready to type.
class _QuickAskPill extends StatelessWidget {
  const _QuickAskPill({required this.creating, required this.onTap});

  final bool creating;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final scheme = theme.colorScheme;
    return Semantics(
      button: true,
      label: 'New session',
      child: Material(
        key: const ValueKey('workspace-quick-ask'),
        color: scheme.surfaceContainerLow,
        elevation: 6,
        shadowColor: scheme.surfaceContainerLowest,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(24),
          side: BorderSide(color: scheme.outlineVariant.withValues(alpha: .85)),
        ),
        child: InkWell(
          borderRadius: BorderRadius.circular(24),
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(18, 14, 12, 14),
            child: Row(
              children: [
                Text(
                  '❯',
                  style: theme.textTheme.titleMedium!.copyWith(
                    color: scheme.primary,
                    fontFamily: AppTheme.monoFamily,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Ask OpenCode…',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: AppTheme.mutedOf(theme),
                    ),
                  ),
                ),
                if (creating)
                  const Padding(
                    padding: EdgeInsets.all(8),
                    child: SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  )
                else
                  Container(
                    width: 36,
                    height: 36,
                    decoration: BoxDecoration(
                      color: scheme.primaryContainer,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      Icons.add_rounded,
                      size: 21,
                      color: scheme.onPrimaryContainer,
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Compact usage labels for a session row: cost to the cent, and the diff
/// summary as "+120 −34 · 6 files". Empty when the server sent neither.
List<String> sessionUsageLabels(Session session) {
  final labels = <String>[];
  final cost = session.cost;
  if (cost != null && cost >= 0.005) labels.add('\$${cost.toStringAsFixed(2)}');
  final summary = session.summary;
  if (summary != null && (summary.additions > 0 || summary.deletions > 0)) {
    labels.add('+${summary.additions} −${summary.deletions}');
    if (summary.files > 0) {
      labels.add('${summary.files} ${summary.files == 1 ? 'file' : 'files'}');
    }
  }
  return labels;
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../navigation/chat_route.dart';
import '../widgets/confirm_sheet.dart';
import '../widgets/entrance.dart';
import '../widgets/product_states.dart';
import 'global_sessions_screen.dart';
import 'managed_workspaces_screen.dart';
import 'project_health_screen.dart';
import 'projects_screen.dart';
import 'worktrees_screen.dart';

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
        setState(() => _projectError = error.toString());
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
      if (mounted) setState(() => _workspaceError = error.toString());
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

  Future<void> _selectWorkspace(WorkspaceInfo? workspace) async {
    setState(() => _selectedWorkspaceID = workspace?.id);
    await widget.controller.selectLocation(
      directory: workspace?.directory ?? _selectedDirectory,
      workspace: workspace?.id,
    );
  }

  static String _basename(String path) {
    final parts = path.split('/').where((part) => part.isNotEmpty).toList();
    return parts.isEmpty ? path : parts.last;
  }

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
      return ProductErrorState(message: _projectError!, onRetry: _load);
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
            onAction: _load,
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

    final sessions = widget.controller.sortedSessions();
    final active = sessions
        .where((session) => widget.controller.busySessions.contains(session.id))
        .toList();
    final recent = sessions
        .where(
          (session) => !widget.controller.busySessions.contains(session.id),
        )
        .toList();
    final archived = widget.controller.archivedSessions();

    return Stack(
      children: [
        RefreshIndicator(
          onRefresh: _load,
          child: CustomScrollView(
            key: const PageStorageKey('workspace-scroll'),
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
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
                    SectionLabel(
                      'Project',
                      trailing: Text('${_projects!.length} open'),
                    ),
                    ListTile(
                      key: const ValueKey('current-project-entry'),
                      leading: const Icon(Icons.folder_rounded),
                      title: Text(_selectedProject?.name ?? 'Choose a project'),
                      subtitle: _selectedProject == null
                          ? null
                          : Text(
                              _selectedProject!.directory,
                              maxLines: 2,
                              overflow: TextOverflow.ellipsis,
                            ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _openProjects,
                    ),
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
                    if (_workspaces.isNotEmpty) ...[
                      const SectionLabel('Workspace'),
                      SizedBox(
                        height: 52,
                        child: ListView(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          scrollDirection: Axis.horizontal,
                          children: [
                            ChoiceChip(
                              label: const Text('Local'),
                              selected: _selectedWorkspaceID == null,
                              onSelected: (_) => _selectWorkspace(null),
                            ),
                            const SizedBox(width: 8),
                            for (final workspace in _workspaces) ...[
                              ChoiceChip(
                                label: Text(
                                  workspace.branch?.isNotEmpty == true
                                      ? workspace.branch!
                                      : workspace.name,
                                ),
                                selected: workspace.id == _selectedWorkspaceID,
                                onSelected: (_) => _selectWorkspace(workspace),
                              ),
                              const SizedBox(width: 8),
                            ],
                          ],
                        ),
                      ),
                    ],
                    if (_workspaceError != null)
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                        child: Text(
                          _workspaceError!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.error,
                          ),
                        ),
                      ),
                    const SectionLabel('Coding'),
                    ListTile(
                      key: const ValueKey('worktrees-entry'),
                      leading: const Icon(Icons.call_split_rounded),
                      title: const Text('Worktrees'),
                      subtitle: const Text(
                        'Create and manage isolated Git branches',
                      ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _selectedProject == null ? null : _openWorktrees,
                    ),
                    ListTile(
                      key: const ValueKey('managed-workspaces-entry'),
                      leading: const Icon(Icons.cloud_outlined),
                      title: const Text('Managed workspaces'),
                      subtitle: const Text(
                        'Create, discover, open, and remove adapter-backed environments',
                      ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _selectedProject == null
                          ? null
                          : _openManagedWorkspaces,
                    ),
                    ListTile(
                      key: const ValueKey('project-health-entry'),
                      leading: const Icon(Icons.monitor_heart_outlined),
                      title: const Text('Project health'),
                      subtitle: const Text(
                        'Branch, changed files, language services, and formatters',
                      ),
                      trailing: const Icon(Icons.chevron_right_rounded),
                      onTap: _openProjectHealth,
                    ),
                    if (active.isNotEmpty)
                      SectionLabel(
                        'Active',
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
                    onOpen: _openSession,
                    onAction: _sessionAction,
                  ),
                ),
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
                        icon: const Icon(Icons.manage_search_rounded, size: 21),
                      ),
                      IconButton(
                        tooltip: 'Refresh sessions',
                        onPressed: widget.controller.refreshSessions,
                        icon: const Icon(Icons.refresh_rounded, size: 19),
                      ),
                    ],
                  ),
                ),
              ),
              if (recent.isEmpty)
                SliverToBoxAdapter(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 26),
                    child: ProductEmptyState(
                      icon: Icons.chat_bubble_outline_rounded,
                      title: 'No recent sessions',
                      message: 'Start a session in the selected workspace.',
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
                      onOpen: _openSession,
                      onAction: _sessionAction,
                    ),
                  ),
                ),
              if (archived.isNotEmpty)
                SliverToBoxAdapter(
                  child: ListTile(
                    leading: const Icon(Icons.archive_outlined),
                    title: const Text('Archived sessions'),
                    subtitle: Text('${archived.length} hidden from recents'),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: () => _showArchived(archived),
                  ),
                ),
              const SliverToBoxAdapter(child: SizedBox(height: 96)),
            ],
          ),
        ),
        // Composer-first home: a docked quick-ask pill opens a fresh session
        // in the active project, replacing the New-session FAB.
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

  Future<void> _openProjectHealth() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ProjectHealthScreen(
          repository: repository,
          repositoryResolver: widget.controller.prepareActionRepository,
        ),
      ),
    );
  }

  Future<void> _openWorktrees() async {
    final project = _selectedProject;
    if (project == null) return;
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) =>
            WorktreesScreen(controller: widget.controller, project: project),
      ),
    );
    if (mounted) await _load();
  }

  Future<void> _openManagedWorkspaces() async {
    final project = _selectedProject;
    if (project == null) return;
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => ManagedWorkspacesScreen(
          controller: widget.controller,
          project: project,
        ),
      ),
    );
    if (changed == true && mounted) await _loadWorkspaces();
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
            throw StateError('No share link was returned.');
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
        case 'delete':
          if (!await _confirmDelete(session)) return;
          await widget.controller.deleteSession(session.id);
          break;
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _showError(error.toString());
    }
  }

  Future<ProductRepository> _requireActionRepository() async {
    final repository = await widget.controller.prepareActionRepository();
    if (repository != null) return repository;
    throw StateError('OpenCode is reconnecting. Try again shortly.');
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

  void _showArchived(List<Session> sessions) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (context) => SafeArea(
        child: ListView.builder(
          shrinkWrap: true,
          itemCount: sessions.length,
          itemBuilder: (context, index) {
            final session = sessions[index];
            return ListTile(
              title: Text(session.title ?? 'Untitled session'),
              subtitle: Text(session.directory ?? ''),
              onTap: () {
                Navigator.pop(context);
                _openSession(session);
              },
            );
          },
        ),
      ),
    );
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Theme.of(context).colorScheme.error,
      ),
    );
  }

  @override
  void dispose() {
    _loadGeneration++;
    widget.controller.removeListener(_changed);
    super.dispose();
  }
}

class _SessionRow extends StatelessWidget {
  final Session session;
  final bool busy;
  final ValueChanged<Session> onOpen;
  final Future<void> Function(String, Session) onAction;

  const _SessionRow({
    required this.session,
    required this.busy,
    required this.onOpen,
    required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final updated = session.time?.updated ?? session.time?.created;
    return ListTile(
      minTileHeight: 64,
      leading: busy
          ? SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(
                strokeWidth: 2,
                color: theme.colorScheme.primary,
              ),
            )
          : const Icon(Icons.chat_bubble_outline_rounded, size: 21),
      title: Text(
        session.title?.isNotEmpty == true ? session.title! : 'Untitled session',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        [
          if (session.shareUrl != null) 'Shared: ${session.shareUrl}',
          if (busy) 'Working',
          if (updated != null) _relativeTime(updated),
          if (session.directory?.isNotEmpty == true)
            _basename(session.directory!),
        ].join(' · '),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: PopupMenuButton<String>(
        tooltip: 'Session actions',
        onSelected: (value) => onAction(value, session),
        itemBuilder: (context) => [
          const PopupMenuItem(value: 'rename', child: Text('Rename')),
          PopupMenuItem(
            value: session.shareUrl == null ? 'share' : 'unshare',
            child: Text(session.shareUrl == null ? 'Share' : 'Stop sharing'),
          ),
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
                    fontFamily: 'AppMono',
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Ask OpenCode…',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.bodyLarge?.copyWith(
                      color: theme.hintColor,
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

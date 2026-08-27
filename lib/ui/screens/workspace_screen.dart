import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import 'project_health_screen.dart';

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

  ProductRepository? get _repository => widget.controller.repository;

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
    final repository = _repository;
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
      setState(() {
        _projects = projects;
        if (_selectedProjectID == null && projects.isNotEmpty) {
          _selectedProjectID = projects.first.id;
          _selectedDirectory = projects.first.directory;
        }
      });
      final selected = _selectedProject;
      if (selected != null) {
        await widget.controller.selectLocation(
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
    final repository = _repository;
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

  Future<void> _selectProject(WorkspaceProject project) async {
    setState(() {
      _selectedProjectID = project.id;
      _selectedWorkspaceID = null;
      _selectedDirectory = project.directory;
      _workspaces = const [];
    });
    await widget.controller.selectLocation(directory: project.directory);
    await _loadWorkspaces();
  }

  Future<void> _selectWorktree(String directory) async {
    setState(() {
      _selectedDirectory = directory;
      _selectedWorkspaceID = null;
      _workspaces = const [];
    });
    await widget.controller.selectLocation(directory: directory);
    await _loadWorkspaces();
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
    final api = widget.controller.api;
    if (api == null || _creating) return;
    setState(() => _creating = true);
    try {
      final session = await api.createSession();
      if (!mounted) return;
      await Navigator.of(context).pushNamed('/chat/${session.id}');
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
      return ProductEmptyState(
        icon: Icons.folder_off_outlined,
        title: 'No projects opened',
        message: 'Open a project on this OpenCode server, then refresh here.',
        actionLabel: 'Refresh',
        onAction: _load,
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
                    const SectionLabel('Project'),
                    SizedBox(
                      height: 52,
                      child: ListView.separated(
                        padding: const EdgeInsets.symmetric(horizontal: 12),
                        scrollDirection: Axis.horizontal,
                        itemCount: _projects!.length,
                        separatorBuilder: (_, _) => const SizedBox(width: 8),
                        itemBuilder: (context, index) {
                          final project = _projects![index];
                          return ChoiceChip(
                            avatar: const Icon(Icons.folder_outlined, size: 17),
                            label: Text(project.name),
                            selected: project.id == _selectedProjectID,
                            onSelected: (_) => _selectProject(project),
                          );
                        },
                      ),
                    ),
                    if ((_selectedProject?.worktrees.isNotEmpty ?? false)) ...[
                      const SectionLabel('Worktree'),
                      SizedBox(
                        height: 52,
                        child: ListView.separated(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          scrollDirection: Axis.horizontal,
                          itemCount: _selectedProject!.worktrees.length + 1,
                          separatorBuilder: (_, _) => const SizedBox(width: 8),
                          itemBuilder: (context, index) {
                            final directory = index == 0
                                ? _selectedProject!.directory
                                : _selectedProject!.worktrees[index - 1];
                            return ChoiceChip(
                              label: Text(_basename(directory)),
                              selected: directory == _selectedDirectory,
                              onSelected: (_) => _selectWorktree(directory),
                            );
                          },
                        ),
                      ),
                    ],
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
                  trailing: IconButton(
                    tooltip: 'Refresh sessions',
                    onPressed: widget.controller.refreshSessions,
                    icon: const Icon(Icons.refresh_rounded, size: 19),
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
                  itemBuilder: (context, index) => _SessionRow(
                    session: recent[index],
                    busy: false,
                    onOpen: _openSession,
                    onAction: _sessionAction,
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
        Positioned(
          right: 16,
          bottom: 16,
          child: FloatingActionButton.extended(
            heroTag: 'workspace-new-session',
            onPressed: _creating ? null : _createSession,
            icon: _creating
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.add_rounded),
            label: const Text('New session'),
          ),
        ),
      ],
    );
  }

  void _openSession(Session session) {
    Navigator.of(context).pushNamed('/chat/${session.id}');
  }

  void _openProjectHealth() {
    final repository = _repository;
    if (repository == null) return;
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => ProjectHealthScreen(repository: repository),
      ),
    );
  }

  Future<void> _sessionAction(String action, Session session) async {
    try {
      switch (action) {
        case 'rename':
          await _rename(session);
          break;
        case 'share':
          if (!await _confirmShare(session)) return;
          final url = await _repository?.shareSession(session.id);
          if (url != null) {
            await Clipboard.setData(ClipboardData(text: url));
            if (mounted) _showMessage('Share link copied');
          }
          break;
        case 'unshare':
          await _repository?.unshareSession(session.id);
          if (mounted) _showMessage('Session is no longer shared');
          break;
        case 'archive':
          if (!await _confirmArchive(session)) return;
          await _repository?.archiveSession(session.id);
          break;
        case 'delete':
          if (!await _confirmDelete(session)) return;
          await widget.controller.api?.deleteSession(session.id);
          break;
      }
      await widget.controller.refreshSessions();
    } catch (error) {
      if (mounted) _showError(error.toString());
    }
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
      await widget.controller.api?.renameSession(session.id, title!);
    }
  }

  Future<bool> _confirmArchive(Session session) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Archive session?'),
            content: Text(
              '“${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}” will be hidden from recent sessions.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Archive'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Future<bool> _confirmShare(Session session) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Share this session?'),
            content: Text(
              'Anyone with the link can view “${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}”, '
              'including its conversation and shared context. Do not share secrets, credentials, or private files.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Share session'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Future<bool> _confirmDelete(Session session) async {
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Delete session?'),
            content: Text(
              '“${session.title?.isNotEmpty == true ? session.title : 'Untitled session'}” and its history will be permanently removed.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context, false),
                child: const Text('Cancel'),
              ),
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: Theme.of(context).colorScheme.error,
                ),
                onPressed: () => Navigator.pop(context, true),
                child: const Text('Delete'),
              ),
            ],
          ),
        ) ??
        false;
  }

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

import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

class ProjectsScreen extends StatefulWidget {
  final ConnectionController controller;
  final String? selectedProjectID;

  const ProjectsScreen({
    super.key,
    required this.controller,
    required this.selectedProjectID,
  });

  @override
  State<ProjectsScreen> createState() => _ProjectsScreenState();
}

class _ProjectsScreenState extends State<ProjectsScreen> {
  final _search = TextEditingController();
  List<WorkspaceProject>? _projects;
  String? _error;
  String? _busyProjectID;
  bool _loading = false;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _search.addListener(_searchChanged);
    unawaited(_load());
  }

  void _searchChanged() => setState(() {});

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    setState(() => _loading = true);
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() {
        _loading = false;
        _error = 'OpenCode is reconnecting. Try again shortly.';
      });
      return;
    }
    try {
      final projects = await repository.listProjects();
      projects.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _projects = projects;
        _error = null;
      });
    } catch (error) {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _error = productErrorText(error);
      });
    }
  }

  List<WorkspaceProject> get _visibleProjects {
    final projects = _projects ?? const <WorkspaceProject>[];
    final query = _search.text.trim().toLowerCase();
    if (query.isEmpty) return projects;
    return projects
        .where(
          (project) =>
              project.name.toLowerCase().contains(query) ||
              project.directory.toLowerCase().contains(query),
        )
        .toList(growable: false);
  }

  Future<void> _select(WorkspaceProject project) async {
    if (_busyProjectID != null) return;
    if (project.id == widget.selectedProjectID &&
        widget.controller.directory == project.directory &&
        widget.controller.workspace == null) {
      Navigator.of(context).pop(false);
      return;
    }
    setState(() => _busyProjectID = project.id);
    await widget.controller.selectLocation(directory: project.directory);
    if (!mounted) return;
    setState(() => _busyProjectID = null);
    final error = widget.controller.locationError;
    if (error != null) {
      _showMessage(error);
      return;
    }
    Navigator.of(context).pop(true);
  }

  Future<void> _rename(WorkspaceProject project) async {
    if (_busyProjectID != null) return;
    final next = await showDialog<String>(
      context: context,
      builder: (_) => _RenameProjectDialog(project: project),
    );
    if (next == null || !mounted) return;
    final folderName = _basename(project.directory);
    final normalized = next.trim();
    final serverName = normalized.isEmpty || normalized == folderName
        ? ''
        : normalized;
    if (normalized == project.name && serverName.isNotEmpty) return;

    setState(() => _busyProjectID = project.id);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) throw const ProductException('OpenCode is reconnecting.');
      final updated = await repository.renameProject(
        projectID: project.id,
        projectDirectory: project.directory,
        name: serverName,
      );
      if (!mounted) return;
      setState(() {
        _projects = [
          for (final item in _projects ?? const <WorkspaceProject>[])
            if (item.id == updated.id) updated else item,
        ]..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      });
      _showMessage('Project renamed to ${updated.name}');
    } catch (error) {
      if (mounted) _showMessage('Could not rename project: $error');
    } finally {
      if (mounted) setState(() => _busyProjectID = null);
    }
  }

  static String _basename(String path) {
    final normalized = path.replaceAll('\\', '/');
    final parts = normalized
        .split('/')
        .where((part) => part.isNotEmpty)
        .toList();
    return parts.isEmpty ? path : parts.last;
  }

  void _showMessage(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));

  @override
  Widget build(BuildContext context) {
    final projects = _projects;
    final visible = _visibleProjects;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Projects'),
        actions: [
          IconButton(
            tooltip: 'Refresh projects',
            onPressed: _loading || _busyProjectID != null ? null : _load,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('projects-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 32),
          children: [
            const ListTile(
              leading: Icon(Icons.folder_copy_outlined),
              title: Text('Projects opened by this server'),
              subtitle: Text(
                'Choose a project for sessions, files, terminals, and coding tools.',
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              child: TextField(
                key: const ValueKey('project-search'),
                controller: _search,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: 'Search projects or paths',
                  prefixIcon: const Icon(Icons.search_rounded),
                  suffixIcon: _search.text.isEmpty
                      ? null
                      : IconButton(
                          tooltip: 'Clear project search',
                          onPressed: _search.clear,
                          icon: const Icon(Icons.close_rounded),
                        ),
                ),
              ),
            ),
            SectionLabel(
              'Open projects',
              trailing: Text('${visible.length} of ${projects?.length ?? 0}'),
            ),
            if (_loading && projects == null)
              const LinearProgressIndicator(minHeight: 2),
            if (_error != null && projects == null)
              ProductErrorState(message: _error!, onRetry: _load)
            else if (projects?.isEmpty == true)
              // Coherent with the Workspace empty state: a fresh server can
              // still host a first session in its own default directory.
              const ProductEmptyState(
                icon: Icons.folder_off_outlined,
                title: 'No projects opened',
                message:
                    'Open a project on this OpenCode server, then refresh '
                    'here — or ask from Workspace to start a session in the '
                    'server’s default directory.',
              )
            else if (visible.isEmpty)
              const ProductEmptyState(
                icon: Icons.search_off_rounded,
                title: 'No matching projects',
                message: 'Try a project name or a directory from the server.',
              )
            else
              for (final project in visible)
                _ProjectTile(
                  project: project,
                  active: project.id == widget.selectedProjectID,
                  busy: _busyProjectID == project.id,
                  onOpen: () => _select(project),
                  onRename: () => _rename(project),
                ),
            if (_error != null && projects != null)
              ListTile(
                key: const ValueKey('project-refresh-error'),
                leading: const Icon(Icons.error_outline_rounded),
                title: const Text('Project refresh failed'),
                subtitle: Text(_error!),
                trailing: IconButton(
                  tooltip: 'Retry projects',
                  onPressed: _load,
                  icon: const Icon(Icons.refresh_rounded),
                ),
              ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _search
      ..removeListener(_searchChanged)
      ..dispose();
    super.dispose();
  }
}

class _ProjectTile extends StatelessWidget {
  final WorkspaceProject project;
  final bool active;
  final bool busy;
  final VoidCallback onOpen;
  final VoidCallback onRename;

  const _ProjectTile({
    required this.project,
    required this.active,
    required this.busy,
    required this.onOpen,
    required this.onRename,
  });

  @override
  Widget build(BuildContext context) {
    final worktreeCount = project.worktrees.length;
    return ListTile(
      key: ValueKey('project-${project.id}'),
      selected: active,
      enabled: !busy,
      leading: busy
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(active ? Icons.folder_rounded : Icons.folder_outlined),
      title: Text(project.name),
      subtitle: Text(
        worktreeCount == 0
            ? project.directory
            : '${project.directory}\n$worktreeCount ${worktreeCount == 1 ? 'worktree' : 'worktrees'}',
        maxLines: 3,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            key: ValueKey('rename-project-${project.id}'),
            tooltip: 'Rename ${project.name}',
            onPressed: busy ? null : onRename,
            icon: const Icon(Icons.edit_outlined),
          ),
          Icon(
            active ? Icons.check_circle_rounded : Icons.chevron_right_rounded,
          ),
        ],
      ),
      onTap: onOpen,
    );
  }
}

class _RenameProjectDialog extends StatefulWidget {
  final WorkspaceProject project;

  const _RenameProjectDialog({required this.project});

  @override
  State<_RenameProjectDialog> createState() => _RenameProjectDialogState();
}

class _RenameProjectDialogState extends State<_RenameProjectDialog> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.project.name,
  );

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('Rename project'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          TextField(
            key: const ValueKey('project-name-input'),
            controller: _controller,
            autofocus: true,
            textInputAction: TextInputAction.done,
            decoration: const InputDecoration(labelText: 'Project name'),
            onSubmitted: (_) => _submit(),
          ),
          const SizedBox(height: 12),
          Text(
            widget.project.directory,
            maxLines: 3,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodySmall,
          ),
          const SizedBox(height: 8),
          const Text('Clear the name to use the project folder name.'),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        key: const ValueKey('confirm-rename-project'),
        onPressed: _submit,
        child: const Text('Save'),
      ),
    ],
  );

  void _submit() => Navigator.pop(context, _controller.text);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../domain/server_gateway.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';
import 'managed_workspaces_screen.dart';
import 'project_health_screen.dart';
import 'projects_screen.dart';
import 'worktrees_screen.dart';

/// Audit UX-P0-02 / UX-101: every low-frequency project management surface —
/// project switching, worktrees, managed workspaces, and project health —
/// lives behind this one route so Workspace itself can stay session-first.
class ManageProjectScreen extends StatefulWidget {
  final ConnectionController controller;
  final WorkspaceProject? project;

  const ManageProjectScreen({
    super.key,
    required this.controller,
    required this.project,
  });

  /// §7 rule 1: a destination whose whole contents are gated away is noise.
  /// Switching projects and project health have a backend on every protocol
  /// generation, so the route is never an empty dead end — but the entry row
  /// asks first rather than assuming it.
  static bool isAvailable(ServerCapabilities capabilities) => true;

  @override
  State<ManageProjectScreen> createState() => _ManageProjectScreenState();
}

class _ManageProjectScreenState extends State<ManageProjectScreen> {
  bool _changed = false;

  @override
  Widget build(BuildContext context) {
    final project = widget.project;
    final capabilities = widget.controller.capabilities;
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: (didPop, _) {
        if (didPop) return;
        Navigator.of(context).pop(_changed);
      },
      child: Scaffold(
        appBar: AppBar(title: const Text('Manage project')),
        body: ListView(
          key: const ValueKey('manage-project-list'),
          padding: const EdgeInsets.only(bottom: 32),
          children: [
            ListTile(
              key: const ValueKey('manage-project-context'),
              leading: const Icon(Icons.folder_rounded),
              title: Text(project?.name ?? 'No project selected'),
              subtitle: Text(
                project?.directory ??
                    'Sessions run in the server’s default directory.',
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const Divider(height: 1),
            const SectionLabel('Project'),
            ListTile(
              key: const ValueKey('switch-project-entry'),
              leading: const Icon(Icons.swap_horiz_rounded),
              title: const Text('Switch project'),
              subtitle: const Text(
                'Choose another project opened by this server',
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: _switchProject,
            ),
            const SectionLabel('Coding'),
            ListTile(
              key: const ValueKey('worktrees-entry'),
              leading: const Icon(Icons.call_split_rounded),
              title: const Text('Worktrees'),
              subtitle: Text(
                project == null
                    ? 'Choose a project first'
                    : 'Create and manage isolated Git branches',
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: project == null ? null : _openWorktrees,
            ),
            // §7 rows 1–4: no workspace inventory, adapter discovery or sync
            // on v2, so the whole destination goes.
            if (capabilities.managedWorkspaces)
              ListTile(
                key: const ValueKey('managed-workspaces-entry'),
                leading: const Icon(Icons.cloud_outlined),
                title: const Text('Managed workspaces'),
                subtitle: Text(
                  project == null
                      ? 'Choose a project first'
                      : 'Create, discover, open, and remove adapter-backed environments',
                ),
                trailing: const Icon(Icons.chevron_right_rounded),
                onTap: project == null ? null : _openManagedWorkspaces,
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
          ],
        ),
      ),
    );
  }

  Future<void> _switchProject() async {
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => ProjectsScreen(
          controller: widget.controller,
          selectedProjectID: widget.project?.id,
        ),
      ),
    );
    if (!mounted) return;
    if (changed == true) {
      // The context this screen manages just moved: hand the user back to
      // their sessions in the newly selected project.
      Navigator.of(context).pop(true);
      return;
    }
    setState(() => _changed = true);
  }

  Future<void> _openWorktrees() async {
    final project = widget.project;
    if (project == null) return;
    await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) =>
            WorktreesScreen(controller: widget.controller, project: project),
      ),
    );
    if (mounted) setState(() => _changed = true);
  }

  Future<void> _openManagedWorkspaces() async {
    final project = widget.project;
    if (project == null) return;
    final changed = await Navigator.of(context).push<bool>(
      MaterialPageRoute<bool>(
        builder: (_) => ManagedWorkspacesScreen(
          controller: widget.controller,
          project: project,
        ),
      ),
    );
    if (changed == true && mounted) setState(() => _changed = true);
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
          capabilities: widget.controller.capabilities,
        ),
      ),
    );
  }
}

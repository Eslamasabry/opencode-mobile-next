import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../desktop/context_menu.dart';
import '../widgets/product_states.dart';
import '../app_theme.dart';

class ManagedWorkspacesScreen extends StatefulWidget {
  final ConnectionController controller;
  final WorkspaceProject project;

  const ManagedWorkspacesScreen({
    super.key,
    required this.controller,
    required this.project,
  });

  @override
  State<ManagedWorkspacesScreen> createState() =>
      _ManagedWorkspacesScreenState();
}

class _ManagedWorkspacesScreenState extends State<ManagedWorkspacesScreen> {
  List<WorkspaceInfo>? _workspaces;
  List<WorkspaceAdapterInfo>? _adapters;
  String? _workspaceError;
  String? _adapterError;
  String? _busyWorkspaceID;
  bool _loading = false;
  bool _syncing = false;
  bool _creating = false;
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final generation = ++_loadGeneration;
    if (mounted) setState(() => _loading = true);
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted || generation != _loadGeneration) return;
    if (repository == null) {
      setState(() {
        _loading = false;
        _workspaceError = 'OpenCode is reconnecting. Try again shortly.';
        _adapterError = _workspaceError;
      });
      return;
    }

    List<WorkspaceInfo>? workspaces;
    List<WorkspaceAdapterInfo>? adapters;
    String? workspaceError;
    String? adapterError;
    try {
      workspaces = await repository.listManagedWorkspaces(
        projectDirectory: widget.project.directory,
      );
      workspaces.sort((a, b) => a.name.compareTo(b.name));
    } catch (error) {
      workspaceError = productErrorText(error);
    }
    if (!mounted || generation != _loadGeneration) return;
    try {
      adapters = await repository.listWorkspaceAdapters(
        projectDirectory: widget.project.directory,
      );
      adapters.sort((a, b) => a.name.compareTo(b.name));
    } catch (error) {
      adapterError = productErrorText(error);
    }
    if (!mounted || generation != _loadGeneration) return;
    setState(() {
      _loading = false;
      if (workspaces != null) _workspaces = workspaces;
      if (adapters != null) _adapters = adapters;
      _workspaceError = workspaceError;
      _adapterError = adapterError;
    });
  }

  Future<void> _sync() async {
    if (_syncing || _creating || _busyWorkspaceID != null) return;
    setState(() => _syncing = true);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) throw const ProductException('OpenCode is reconnecting.');
      await repository.syncWorkspaceList(
        projectDirectory: widget.project.directory,
      );
      await _load();
      if (mounted) _showMessage('Workspace discovery finished');
    } catch (error) {
      if (mounted) _showMessage('Could not discover workspaces: $error');
    } finally {
      if (mounted) setState(() => _syncing = false);
    }
  }

  Future<void> _create() async {
    final adapters = _adapters ?? const <WorkspaceAdapterInfo>[];
    if (_creating || adapters.isEmpty) return;
    final draft = await showDialog<_WorkspaceDraft>(
      context: context,
      builder: (_) => _CreateWorkspaceDialog(adapters: adapters),
    );
    if (draft == null || !mounted) return;
    setState(() => _creating = true);
    try {
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) throw const ProductException('OpenCode is reconnecting.');
      final workspace = await repository.createManagedWorkspace(
        projectDirectory: widget.project.directory,
        type: draft.type,
        branch: draft.branch,
      );
      await widget.controller.selectLocation(
        directory: workspace.directory ?? widget.project.directory,
        workspace: workspace.id,
      );
      if (!mounted) return;
      final locationError = widget.controller.locationError;
      if (locationError != null) throw ProductException(locationError);
      Navigator.of(context).pop(true);
    } catch (error) {
      if (mounted) _showMessage('Could not create workspace: $error');
    } finally {
      if (mounted) setState(() => _creating = false);
    }
  }

  Future<void> _open(WorkspaceInfo workspace) async {
    if (_busyWorkspaceID != null || _creating || _syncing) return;
    setState(() => _busyWorkspaceID = workspace.id);
    await widget.controller.selectLocation(
      directory: workspace.directory ?? widget.project.directory,
      workspace: workspace.id,
    );
    if (!mounted) return;
    setState(() => _busyWorkspaceID = null);
    if (widget.controller.locationError != null) {
      _showMessage(widget.controller.locationError!);
      return;
    }
    Navigator.of(context).pop(true);
  }

  Future<void> _remove(WorkspaceInfo workspace) async {
    if (_busyWorkspaceID != null || _creating || _syncing) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => _RemoveWorkspaceDialog(workspace: workspace),
    );
    if (confirmed != true || !mounted) return;
    setState(() => _busyWorkspaceID = workspace.id);
    try {
      if (widget.controller.workspace == workspace.id) {
        await widget.controller.selectLocation(
          directory: widget.project.directory,
        );
        final locationError = widget.controller.locationError;
        if (locationError != null) throw ProductException(locationError);
      }
      final repository = await widget.controller.prepareActionRepository();
      if (repository == null) throw const ProductException('OpenCode is reconnecting.');
      await repository.removeManagedWorkspace(
        projectDirectory: widget.project.directory,
        id: workspace.id,
      );
      await _load();
      if (mounted) _showMessage('${workspace.name} was removed');
    } catch (error) {
      if (mounted) _showMessage('Could not remove workspace: $error');
    } finally {
      if (mounted) setState(() => _busyWorkspaceID = null);
    }
  }

  void _showMessage(String message) => ScaffoldMessenger.of(
    context,
  ).showSnackBar(SnackBar(content: Text(message)));

  @override
  Widget build(BuildContext context) {
    final workspaces = _workspaces;
    final adapters = _adapters;
    final busy = _syncing || _creating || _busyWorkspaceID != null;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cloud environments'),
        actions: [
          IconButton(
            key: const ValueKey('sync-managed-workspaces'),
            tooltip: 'Discover existing environments',
            onPressed: busy ? null : _sync,
            icon: _syncing
                ? const SizedBox.square(
                    dimension: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.sync_rounded),
          ),
          IconButton(
            tooltip: 'Refresh cloud environments',
            onPressed: busy || _loading ? null : _load,
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      floatingActionButton: adapters?.isNotEmpty == true
          ? FloatingActionButton.extended(
              key: const ValueKey('create-managed-workspace'),
              onPressed: busy ? null : _create,
              icon: _creating
                  ? const SizedBox.square(
                      dimension: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.add_rounded),
              label: const Text('New environment'),
            )
          : null,
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          key: const ValueKey('managed-workspaces-list'),
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 104),
          children: [
            if (_loading && workspaces == null)
              const LinearProgressIndicator(minHeight: 2),
            if (_workspaceError != null && workspaces == null)
              ProductErrorState(message: _workspaceError!, onRetry: _load)
            else ...[
              SectionLabel(
                'Environments',
                trailing: Text('${workspaces?.length ?? 0}'),
              ),
              if (workspaces?.isEmpty == true)
                ProductEmptyState(
                  icon: Icons.cloud_queue_rounded,
                  title: 'No cloud environments',
                  message:
                      'Adapter-backed environments for ${widget.project.name} '
                      'appear here. Create one from a server adapter, or use '
                      'Discover to register environments the adapter already '
                      'knows.',
                )
              else
                for (final workspace in workspaces ?? const <WorkspaceInfo>[])
                  _WorkspaceTile(
                    workspace: workspace,
                    active: widget.controller.workspace == workspace.id,
                    busy: _busyWorkspaceID == workspace.id,
                    onOpen: () => _open(workspace),
                    onRemove: () => _remove(workspace),
                  ),
              if (_workspaceError != null && workspaces != null)
                ListTile(
                  leading: const Icon(Icons.error_outline_rounded),
                  title: const Text('Environment refresh failed'),
                  subtitle: Text(_workspaceError!),
                  trailing: IconButton(
                    tooltip: 'Retry cloud environments',
                    onPressed: _load,
                    icon: const Icon(Icons.refresh_rounded),
                  ),
                ),
            ],
            const SectionLabel('Adapters'),
            if (_adapterError != null && adapters == null)
              ListTile(
                key: const ValueKey('workspace-adapter-error'),
                leading: const Icon(Icons.error_outline_rounded),
                title: const Text('Adapters unavailable'),
                subtitle: Text(_adapterError!),
                trailing: IconButton(
                  tooltip: 'Retry workspace adapters',
                  onPressed: _load,
                  icon: const Icon(Icons.refresh_rounded),
                ),
              )
            else if (adapters?.isEmpty == true)
              const ProductInlineEmpty(
                key: ValueKey('workspace-adapters-empty'),
                icon: Icons.extension_off_outlined,
                title: 'No workspace adapters',
                message:
                    'This OpenCode project does not expose managed workspace creation.',
              )
            else
              for (final adapter in adapters ?? const <WorkspaceAdapterInfo>[])
                ListTile(
                  leading: const Icon(Icons.extension_outlined),
                  title: Text(adapter.name),
                  subtitle: Text(
                    '${adapter.description}\n${adapter.type}',
                    maxLines: 3,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
            if (_adapterError != null && adapters != null)
              ListTile(
                leading: const Icon(Icons.error_outline_rounded),
                title: const Text('Adapter refresh failed'),
                subtitle: Text(_adapterError!),
                trailing: IconButton(
                  tooltip: 'Retry workspace adapters',
                  onPressed: _load,
                  icon: const Icon(Icons.refresh_rounded),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _WorkspaceTile extends StatelessWidget {
  final WorkspaceInfo workspace;
  final bool active;
  final bool busy;
  final VoidCallback onOpen;
  final VoidCallback onRemove;

  const _WorkspaceTile({
    required this.workspace,
    required this.active,
    required this.busy,
    required this.onOpen,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    final status = workspace.status?.toLowerCase();
    final theme = Theme.of(context);
    final (icon, tone, label) = switch (status) {
      'connected' => (Icons.cloud_done_outlined, AppStatusTone.ok, 'Connected'),
      'connecting' => (
        Icons.cloud_sync_outlined,
        AppStatusTone.progress,
        'Connecting',
      ),
      'error' => (Icons.cloud_off_outlined, AppStatusTone.failure, 'Error'),
      'disconnected' => (
        Icons.cloud_off_outlined,
        AppStatusTone.neutral,
        'Disconnected',
      ),
      _ => (
        Icons.cloud_queue_outlined,
        AppStatusTone.neutral,
        'Status unknown',
      ),
    };
    final color = AppTheme.statusColor(theme, tone);
    final detail = [
      if (workspace.branch?.isNotEmpty == true) workspace.branch!,
      workspace.type,
      label,
      if (workspace.directory?.isNotEmpty == true) workspace.directory!,
    ].join(' · ');
    final tile = ListTile(
      key: ValueKey('managed-workspace-${workspace.id}'),
      enabled: !busy,
      leading: busy
          ? const SizedBox.square(
              dimension: 22,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(icon, color: color),
      title: Text(workspace.name),
      subtitle: Text(detail, maxLines: 3, overflow: TextOverflow.ellipsis),
      trailing: PopupMenuButton<String>(
        tooltip: 'Environment actions',
        enabled: !busy,
        onSelected: (value) {
          if (value == 'open') onOpen();
          if (value == 'remove') onRemove();
        },
        itemBuilder: (_) => [
          PopupMenuItem(
            value: 'open',
            child: Text(active ? 'Open again' : 'Open'),
          ),
          const PopupMenuItem(value: 'remove', child: Text('Remove')),
        ],
      ),
      selected: active,
      onTap: onOpen,
    );
    // The overflow menu's entries, on a right click. A pass-through off
    // desktop.
    return ContextMenuRegion(
      actions: () => busy
          ? const []
          : [
              ContextMenuAction(
                menuKey: const ValueKey('environment-menu-open'),
                label: active ? 'Open again' : 'Open',
                icon: Icons.open_in_new_rounded,
                onSelected: onOpen,
              ),
              ContextMenuAction(
                menuKey: const ValueKey('environment-menu-remove'),
                label: 'Remove',
                icon: Icons.delete_outline_rounded,
                destructive: true,
                onSelected: onRemove,
              ),
            ],
      child: tile,
    );
  }
}

class _WorkspaceDraft {
  final String type;
  final String? branch;

  const _WorkspaceDraft({required this.type, this.branch});
}

class _CreateWorkspaceDialog extends StatefulWidget {
  final List<WorkspaceAdapterInfo> adapters;

  const _CreateWorkspaceDialog({required this.adapters});

  @override
  State<_CreateWorkspaceDialog> createState() => _CreateWorkspaceDialogState();
}

class _CreateWorkspaceDialogState extends State<_CreateWorkspaceDialog> {
  late String _type = widget.adapters.first.type;
  final _branch = TextEditingController();

  WorkspaceAdapterInfo get _adapter =>
      widget.adapters.firstWhere((adapter) => adapter.type == _type);

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: const Text('New managed workspace'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<String>(
            key: const ValueKey('workspace-adapter-picker'),
            initialValue: _type,
            isExpanded: true,
            decoration: const InputDecoration(labelText: 'Adapter'),
            items: [
              for (final adapter in widget.adapters)
                DropdownMenuItem(
                  value: adapter.type,
                  child: Text(adapter.name, overflow: TextOverflow.ellipsis),
                ),
            ],
            onChanged: (value) {
              if (value != null) setState(() => _type = value);
            },
          ),
          const SizedBox(height: 10),
          Text(_adapter.description),
          const SizedBox(height: 16),
          TextField(
            key: const ValueKey('workspace-branch-input'),
            controller: _branch,
            decoration: const InputDecoration(
              labelText: 'Branch (optional)',
              hintText: 'Use the adapter default',
            ),
          ),
          const SizedBox(height: 12),
          const Text(
            'OpenCode configures adapter-specific details on the server. The new workspace opens here after it is ready.',
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context),
        child: const Text('Cancel'),
      ),
      FilledButton(
        key: const ValueKey('confirm-create-managed-workspace'),
        onPressed: () {
          final branch = _branch.text.trim();
          Navigator.pop(
            context,
            _WorkspaceDraft(
              type: _type,
              branch: branch.isEmpty ? null : branch,
            ),
          );
        },
        child: const Text('Create and open'),
      ),
    ],
  );

  @override
  void dispose() {
    _branch.dispose();
    super.dispose();
  }
}

class _RemoveWorkspaceDialog extends StatefulWidget {
  final WorkspaceInfo workspace;

  const _RemoveWorkspaceDialog({required this.workspace});

  @override
  State<_RemoveWorkspaceDialog> createState() => _RemoveWorkspaceDialogState();
}

class _RemoveWorkspaceDialogState extends State<_RemoveWorkspaceDialog> {
  final _controller = TextEditingController();

  @override
  Widget build(BuildContext context) => AlertDialog(
    title: Text('Remove ${widget.workspace.name}?'),
    content: SingleChildScrollView(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'The server adapter may permanently delete the remote environment or worktree. Existing chat history remains, but its workspace may no longer be reachable.',
          ),
          const SizedBox(height: 16),
          TextField(
            key: const ValueKey('remove-managed-workspace-confirmation'),
            controller: _controller,
            decoration: InputDecoration(
              labelText: 'Type ${widget.workspace.name} to confirm',
            ),
            onChanged: (_) => setState(() {}),
          ),
        ],
      ),
    ),
    actions: [
      TextButton(
        onPressed: () => Navigator.pop(context, false),
        child: const Text('Cancel'),
      ),
      FilledButton(
        key: const ValueKey('confirm-remove-managed-workspace'),
        style: FilledButton.styleFrom(
          backgroundColor: Theme.of(context).colorScheme.error,
        ),
        onPressed: _controller.text == widget.workspace.name
            ? () => Navigator.pop(context, true)
            : null,
        child: const Text('Remove permanently'),
      ),
    ],
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}

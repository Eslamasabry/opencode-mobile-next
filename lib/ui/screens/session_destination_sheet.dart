import 'dart:async';

import 'package:flutter/material.dart';

import '../../api/models.dart';
import '../../api/product_repository.dart';
import '../../state/connection.dart';
import '../widgets/product_states.dart';

enum SessionDestinationMode { move, warp }

Future<void> showSessionDestinationSheet(
  BuildContext context, {
  required ConnectionController controller,
  required String sessionID,
  required SessionDestinationMode mode,
}) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: false,
  constraints: const BoxConstraints(maxWidth: 720),
  builder: (_) => _SessionDestinationSheet(
    rootContext: context,
    controller: controller,
    sessionID: sessionID,
    mode: mode,
  ),
);

Future<void> showConsoleOrganizationSheet(
  BuildContext context, {
  required ConnectionController controller,
}) => showModalBottomSheet<void>(
  context: context,
  isScrollControlled: true,
  useSafeArea: true,
  showDragHandle: false,
  constraints: const BoxConstraints(maxWidth: 720),
  builder: (_) =>
      _ConsoleOrganizationSheet(rootContext: context, controller: controller),
);

class _SessionDestination {
  const _SessionDestination({
    required this.title,
    required this.directory,
    this.workspaceID,
    this.workspaceType,
    this.status,
    this.current = false,
  });

  final String title;
  final String directory;
  final String? workspaceID;
  final String? workspaceType;
  final String? status;
  final bool current;
}

class _SessionDestinationSheet extends StatefulWidget {
  const _SessionDestinationSheet({
    required this.rootContext,
    required this.controller,
    required this.sessionID,
    required this.mode,
  });

  final BuildContext rootContext;
  final ConnectionController controller;
  final String sessionID;
  final SessionDestinationMode mode;

  @override
  State<_SessionDestinationSheet> createState() =>
      _SessionDestinationSheetState();
}

class _SessionDestinationSheetState extends State<_SessionDestinationSheet> {
  List<_SessionDestination>? _destinations;
  List<VersionControlFile> _changes = const [];
  Object? _error;
  Object? _changesError;
  bool _working = false;
  String _query = '';

  bool get _moving => widget.mode == SessionDestinationMode.move;
  /// Both modes read as "Move" to the user; only the transport differs.
  String get _verb => 'Move';

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      setState(() => _error = const ProductException('OpenCode is reconnecting.'));
      return;
    }
    setState(() {
      _destinations = null;
      _changes = const [];
      _error = null;
      _changesError = null;
    });
    try {
      final session = widget.controller.sessionsById[widget.sessionID];
      final currentDirectory =
          session?.directory ?? widget.controller.directory ?? '';
      final projects = await repository.listProjects();
      if (!mounted) return;
      final project = _projectForSession(projects, session, currentDirectory);
      if (project == null) {
        throw const ProductException(
          'The session project is not available on this server.',
        );
      }

      final destinations = _moving
          ? await _loadMoveDestinations(
              repository,
              project,
              session,
              currentDirectory,
            )
          : await _loadWarpDestinations(repository, project, session);
      List<VersionControlFile> changes = const [];
      Object? changesError;
      try {
        final health = await repository.loadVersionControlHealth();
        changes = health.changes;
      } catch (error) {
        changesError = error;
      }
      if (!mounted) return;
      setState(() {
        _destinations = destinations;
        _changes = changes;
        _changesError = changesError;
      });
    } catch (error) {
      if (mounted) setState(() => _error = error);
    }
  }

  WorkspaceProject? _projectForSession(
    List<WorkspaceProject> projects,
    Session? session,
    String currentDirectory,
  ) {
    for (final project in projects) {
      if (project.id == session?.projectID) return project;
    }
    final matches =
        projects.where((project) {
            final roots = [project.directory, ...project.worktrees];
            return roots.any((root) => _containsPath(root, currentDirectory));
          }).toList()
          ..sort((a, b) => b.directory.length.compareTo(a.directory.length));
    return matches.isEmpty ? null : matches.first;
  }

  Future<List<_SessionDestination>> _loadMoveDestinations(
    ServerOperationsGateway repository,
    WorkspaceProject project,
    Session? session,
    String currentDirectory,
  ) async {
    final listed = await repository.listProjectDirectories(project.id);
    final directories = <String>{
      if (currentDirectory.isNotEmpty) currentDirectory,
      ...listed.map((item) => item.directory),
      for (final item in widget.controller.sessionsById.values)
        if (item.projectID == project.id && item.directory?.isNotEmpty == true)
          item.directory!,
    }.toList();
    directories.sort((a, b) {
      if (a == currentDirectory) return -1;
      if (b == currentDirectory) return 1;
      return a.toLowerCase().compareTo(b.toLowerCase());
    });
    return [
      for (final directory in directories)
        _SessionDestination(
          title: _basename(directory),
          directory: directory,
          current: directory == currentDirectory,
        ),
    ];
  }

  Future<List<_SessionDestination>> _loadWarpDestinations(
    ServerOperationsGateway repository,
    WorkspaceProject project,
    Session? session,
  ) async {
    final currentWorkspaceID =
        session?.workspaceID ?? widget.controller.workspace;
    final workspaces = await repository.listWorkspaces();
    final projectWorkspaces =
        workspaces
            .where((workspace) => workspace.projectID == project.id)
            .toList()
          ..sort((a, b) {
            if (a.id == currentWorkspaceID) return -1;
            if (b.id == currentWorkspaceID) return 1;
            return a.name.toLowerCase().compareTo(b.name.toLowerCase());
          });
    return [
      _SessionDestination(
        title: 'Local project',
        directory: project.directory,
        current: currentWorkspaceID == null,
      ),
      for (final workspace in projectWorkspaces)
        _SessionDestination(
          title: workspace.name,
          directory: workspace.directory ?? project.directory,
          workspaceID: workspace.id,
          workspaceType: workspace.type,
          status: workspace.status,
          current: workspace.id == currentWorkspaceID,
        ),
    ];
  }

  Future<void> _select(_SessionDestination destination) async {
    if (_working || destination.current) return;
    final status = destination.status;
    if (!_moving && status != null && status != 'connected') return;
    final messenger = ScaffoldMessenger.of(widget.rootContext);
    final transfer = await _confirmTransfer(destination);
    if (transfer == null || !mounted) return;
    setState(() => _working = true);
    try {
      if (_moving) {
        await widget.controller.moveSessionToDirectory(
          widget.sessionID,
          directory: destination.directory,
          moveChanges: transfer,
        );
      } else {
        await widget.controller.warpSessionToWorkspace(
          widget.sessionID,
          directory: destination.directory,
          workspaceID: destination.workspaceID,
          copyChanges: transfer,
        );
      }
      if (!mounted) return;
      Navigator.of(context).pop();
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Moved to ${destination.title}',
          ),
        ),
      );
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  Future<bool?> _confirmTransfer(_SessionDestination destination) {
    final hasChanges = _changes.isNotEmpty;
    final unknownChanges = _changesError != null;
    return showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('$_verb session?'),
        content: Text(
          hasChanges
              ? '${_changes.length} changed ${_changes.length == 1 ? 'file is' : 'files are'} present. Choose whether those working changes should ${_moving ? 'move' : 'be copied'} with the session.'
              : unknownChanges
              ? 'The app could not inspect working changes. For safety, this continues without transferring changes.'
              : 'Continue to ${destination.title}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: const Text('Cancel'),
          ),
          if (hasChanges)
            TextButton(
              key: const Key('session-destination-without-changes'),
              onPressed: () => Navigator.pop(dialogContext, false),
              child: Text('$_verb only'),
            ),
          FilledButton(
            key: const Key('session-destination-confirm'),
            onPressed: () =>
                Navigator.pop(dialogContext, hasChanges && !unknownChanges),
            child: Text(
              hasChanges
                  ? _moving
                        ? 'Move with changes'
                        : 'Copy changes and move'
                  : _verb,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final destinations = _destinations;
    final query = _query.trim().toLowerCase();
    final visible = destinations
        ?.where(
          (item) =>
              query.isEmpty ||
              item.title.toLowerCase().contains(query) ||
              item.directory.toLowerCase().contains(query),
        )
        .toList();
    return FractionallySizedBox(
      heightFactor: .9,
      child: Scaffold(
        key: Key(_moving ? 'move-session-sheet' : 'warp-session-sheet'),
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: const Text('Move session'),
          actions: [
            IconButton(
              tooltip: 'Close',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
              onPressed: _working ? null : () => Navigator.pop(context),
              icon: const Icon(Icons.close_rounded),
            ),
          ],
          bottom: _working
              ? const PreferredSize(
                  preferredSize: Size.fromHeight(3),
                  child: LinearProgressIndicator(minHeight: 3),
                )
              : null,
        ),
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: Text(
                _moving
                    ? 'Choose another directory in this project.'
                    : 'Choose a connected workspace, or return to the local project.',
                style: theme.textTheme.bodyMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ),
            if ((destinations?.length ?? 0) > 6)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
                child: TextField(
                  key: const Key('session-destination-search'),
                  onChanged: (value) => setState(() => _query = value),
                  decoration: const InputDecoration(
                    labelText: 'Filter destinations',
                    prefixIcon: Icon(Icons.search_rounded),
                  ),
                ),
              ),
            Expanded(child: _buildBody(visible)),
          ],
        ),
      ),
    );
  }

  Widget _buildBody(List<_SessionDestination>? visible) {
    if (_error != null && _destinations == null) {
      return ProductErrorState(
        message: productErrorText(_error!),
        onRetry: _load,
      );
    }
    if (visible == null) {
      return const LoadingList(rows: 6);
    }
    if (visible.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            _query.isEmpty
                ? 'No other destinations are available.'
                : 'No destinations match this filter.',
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: visible.length + (_error == null ? 0 : 1),
      itemBuilder: (context, index) {
        if (index == 0 && _error != null) {
          return _InlineErrorBanner(error: _error!);
        }
        final offset = _error == null ? index : index - 1;
        final item = visible[offset];
        final unavailable =
            !_moving && item.status != null && item.status != 'connected';
        final label = item.workspaceType == null
            ? item.directory
            : '${item.workspaceType} · ${item.status ?? 'unknown'}\n${item.directory}';
        return ListTile(
          key: ValueKey(
            '${_moving ? 'move' : 'warp'}-destination-${item.workspaceID ?? item.directory}',
          ),
          minTileHeight: 64,
          enabled: !_working && !item.current && !unavailable,
          leading: Icon(
            item.current
                ? Icons.radio_button_checked_rounded
                : Icons.radio_button_unchecked_rounded,
          ),
          title: Text(item.title),
          subtitle: Text(label, maxLines: 3, overflow: TextOverflow.ellipsis),
          trailing: item.current
              ? const Text('Current')
              : unavailable
              ? Text(item.status ?? 'Unavailable')
              : const Icon(Icons.chevron_right_rounded),
          onTap: () => _select(item),
        );
      },
    );
  }
}

class _ConsoleOrganizationSheet extends StatefulWidget {
  const _ConsoleOrganizationSheet({
    required this.rootContext,
    required this.controller,
  });

  final BuildContext rootContext;
  final ConnectionController controller;

  @override
  State<_ConsoleOrganizationSheet> createState() =>
      _ConsoleOrganizationSheetState();
}

class _ConsoleOrganizationSheetState extends State<_ConsoleOrganizationSheet> {
  List<ConsoleOrganization>? _organizations;
  Object? _error;
  bool _working = false;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<void> _load() async {
    final repository = await widget.controller.prepareActionRepository();
    if (!mounted) return;
    if (repository == null) {
      setState(() => _error = const ProductException('OpenCode is reconnecting.'));
      return;
    }
    setState(() {
      _organizations = null;
      _error = null;
    });
    try {
      final organizations = [...await repository.listConsoleOrganizations()];
      organizations.sort((a, b) {
        if (a.active != b.active) return a.active ? -1 : 1;
        final account = _accountLabel(a).compareTo(_accountLabel(b));
        return account != 0 ? account : a.orgName.compareTo(b.orgName);
      });
      if (mounted) setState(() => _organizations = organizations);
    } catch (error) {
      if (mounted) setState(() => _error = error);
    }
  }

  Future<void> _switch(ConsoleOrganization organization) async {
    if (_working || organization.active) return;
    final messenger = ScaffoldMessenger.of(widget.rootContext);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Switch organization?'),
        content: Text(
          'Models and providers will reload using ${organization.orgName}.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            key: const Key('console-org-confirm'),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Switch'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _working = true;
      _error = null;
    });
    try {
      await widget.controller.switchConsoleOrganization(organization);
      if (!mounted) return;
      Navigator.pop(context);
      messenger.showSnackBar(
        SnackBar(content: Text('Switched to ${organization.orgName}')),
      );
    } catch (error) {
      if (mounted) setState(() => _error = error);
    } finally {
      if (mounted) setState(() => _working = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final organizations = _organizations;
    return FractionallySizedBox(
      heightFactor: .82,
      child: Scaffold(
        key: const Key('console-organization-sheet'),
        appBar: AppBar(
          automaticallyImplyLeading: false,
          title: const Text('Switch organization'),
          actions: [
            IconButton(
              tooltip: 'Close',
              constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
              onPressed: _working ? null : () => Navigator.pop(context),
              icon: const Icon(Icons.close_rounded),
            ),
          ],
          bottom: _working
              ? const PreferredSize(
                  preferredSize: Size.fromHeight(3),
                  child: LinearProgressIndicator(minHeight: 3),
                )
              : null,
        ),
        body: organizations == null
            ? _error == null
                  ? const LoadingList(rows: 5)
                  : ProductErrorState(
                      message: productErrorText(_error!),
                      onRetry: _load,
                    )
            : organizations.isEmpty
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    'No switchable OpenCode Console organizations were returned.',
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            : ListView(
                padding: const EdgeInsets.only(bottom: 24),
                children: [
                  if (_error != null) _InlineErrorBanner(error: _error!),
                  for (
                    var index = 0;
                    index < organizations.length;
                    index++
                  ) ...[
                    if (index == 0 ||
                        _accountLabel(organizations[index - 1]) !=
                            _accountLabel(organizations[index]))
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 18, 16, 6),
                        child: Text(
                          _accountLabel(organizations[index]),
                          style: Theme.of(context).textTheme.labelLarge,
                        ),
                      ),
                    ListTile(
                      key: ValueKey(
                        'console-org-${organizations[index].accountID}-${organizations[index].orgID}',
                      ),
                      minTileHeight: 64,
                      enabled: !_working && !organizations[index].active,
                      leading: Icon(
                        organizations[index].active
                            ? Icons.radio_button_checked_rounded
                            : Icons.radio_button_unchecked_rounded,
                      ),
                      title: Text(organizations[index].orgName),
                      subtitle: Text(organizations[index].orgID),
                      trailing: organizations[index].active
                          ? const Text('Current')
                          : const Icon(Icons.chevron_right_rounded),
                      onTap: () => _switch(organizations[index]),
                    ),
                  ],
                ],
              ),
      ),
    );
  }
}

class _InlineErrorBanner extends StatelessWidget {
  const _InlineErrorBanner({required this.error});

  final Object error;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
    child: Text(
      productErrorText(error),
      style: TextStyle(color: Theme.of(context).colorScheme.error),
    ),
  );
}

String _accountLabel(ConsoleOrganization organization) {
  final uri = Uri.tryParse(organization.accountUrl);
  final host = uri?.host.isNotEmpty == true
      ? uri!.host
      : organization.accountUrl;
  return '${organization.accountEmail} · $host';
}

String _basename(String path) {
  final parts = path
      .replaceAll('\\', '/')
      .split('/')
      .where((part) => part.isNotEmpty)
      .toList();
  return parts.isEmpty ? path : parts.last;
}

bool _containsPath(String root, String path) {
  final normalizedRoot = root
      .replaceAll('\\', '/')
      .replaceFirst(RegExp(r'/+$'), '');
  final normalizedPath = path.replaceAll('\\', '/');
  return normalizedPath == normalizedRoot ||
      normalizedPath.startsWith('$normalizedRoot/');
}
